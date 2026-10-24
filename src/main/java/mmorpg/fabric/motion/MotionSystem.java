package mmorpg.fabric.motion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Motions;
import mmorpg.core.Weapon;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Tasks;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * 0.38.0 동작 (서버): 평타 연타 단계 (가로 → 세로 → 회전 — 무기 종류마다 rules_more MOTION.combo), 스킬 동작, 구르기 확인을 정하고
 * 둘레 사람들에게 알림 (MotionNet.PlayPayload — 모습은 클라이언트가 그림).
 * <ul>
 *   <li>평타: 모드 클라이언트는 휘두르기 전에 단계를 보냄 (ReqPayload basic — 화면의 동작과 참격 방향이 같게). 없으면 서버가 같은 규칙으로 셈
 *       (Motions.nextStep). 한 번의 휘두르기 = Slash.once 가 부르는 {@link #swung}</li>
 *   <li>회전 베기 (spin 단계): {@link #spinNow} — WeaponTraits 가 둘레 적에게 맞힌 피해 × Motions.spinRatio</li>
 *   <li>스킬: 무기 스킬 · 룬 · 직업 스킬이 나가면 {@link #skill} (쓴 사람 화면에도)</li>
 *   <li>구르기: 클라이언트가 움직이고 (무적 없음) 서버는 재사용 대기 · 탈것 · 물 · 공중만 보고 둘레에 알림</li>
 * </ul>
 */
public final class MotionSystem {
    private MotionSystem() {}

    /** 평타 한 번: 동작 이름 · 연타 단계 · 틱 */
    public record Swing(String motion, int step, long tick) {}

    private static final Map<UUID, Swing> LAST = new HashMap<>();
    /** 클라이언트가 보낸 다음 평타의 단계 (받은 틱) */
    private static final Map<UUID, long[]> PENDING = new HashMap<>();
    private static final Map<UUID, Long> ROLLED = new HashMap<>();
    /** 시험: 마지막으로 보낸 동작 (사람 → 이름) */
    private static final Map<UUID, String> SENT = new HashMap<>();

    public static void init() {
        MotionNet.register();
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
    }

    public static void forget(UUID id) {
        LAST.remove(id);
        PENDING.remove(id);
        ROLLED.remove(id);
        SENT.remove(id);
    }

    // ------------------------------------------------------------ 클라이언트 요청

    static void request(ServerPlayer sp, String kind, int step, float yaw) {
        if (sp == null || sp.isRemoved()) return;
        switch (kind) {
            case "basic" -> PENDING.put(sp.getUUID(), new long[] {step, Tasks.now()});
            case "roll" -> roll(sp, yaw);
            default -> {}
        }
    }

    // ------------------------------------------------------------ 평타

    /**
     * 휘두르기 한 번 (Slash.once — 같은 틱의 공격 · 휘두르기 패킷은 한 번): 이 평타의 동작 (연타 단계) 을 정하고 둘레에 알림.
     * w = 든 무기 (근접 무기가 아니거나 동작이 없는 종류면 null 을 돌려줌)
     */
    public static Swing swung(ServerPlayer sp, Weapon w) {
        if (sp == null) return null;
        UUID id = sp.getUUID();
        long[] req = PENDING.remove(id);
        List<String> combo = w != null ? Motions.combo(w.type()) : List.of();
        if (combo.isEmpty()) return null;
        long now = Tasks.now();
        Swing last = LAST.get(id);
        boolean fromClient = req != null && now - req[1] <= 2 && req[0] >= 1 && req[0] <= combo.size();
        int step;
        if (fromClient) step = (int) req[0];
        else {
            boolean same = last != null && combo.contains(last.motion());
            step = Motions.nextStep(same ? last.step() : 0, same ? last.tick() : 0, now, combo.size());
        }
        Swing s = new Swing(combo.get(step - 1), step, now);
        LAST.put(id, s);
        broadcast(sp, s.motion(), sp.getYRot(), fromClient);
        return s;
    }

    /** 마지막 평타 (없으면 null) */
    public static Swing last(ServerPlayer sp) {
        return sp == null ? null : LAST.get(sp.getUUID());
    }

    /** 지금 (이번 틱 · 바로 앞 틱) 의 평타가 회전 베기인지 */
    public static boolean spinNow(ServerPlayer sp) {
        Swing s = last(sp);
        return s != null && "spin".equals(s.motion()) && Tasks.now() - s.tick() <= 1;
    }

    // ------------------------------------------------------------ 스킬

    /** 스킬이 나감 (key = 무기 스킬 id · 직업 스킬 id · "rune:" + 룬 fn): 표에 있는 동작을 쓴 사람과 둘레에 */
    public static void skill(ServerPlayer sp, String key) {
        try {
            String m = Motions.skillMotion(key);
            if (m != null && sp != null && !sp.isRemoved()) broadcast(sp, m, sp.getYRot(), false);
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[mmorpg] skill motion {}", key, e);
        }
    }

    // ------------------------------------------------------------ 구르기

    /** 구를 수 있는지 (서버 확인 — 클라이언트도 같은 조건) */
    public static boolean canRoll(ServerPlayer sp) {
        if (sp == null || sp.isRemoved() || !sp.isAlive() || sp.isSpectator()) return false;
        if (sp.isPassenger() || sp.isInWater() || sp.isFallFlying() || sp.isSleeping() || sp.getAbilities().flying) return false;
        Long t = ROLLED.get(sp.getUUID());
        // 클라이언트 틱과 조금 어긋나도 받게 2틱 여유
        return t == null || Tasks.now() - t >= Motions.rollCooldown() - 2;
    }

    static void roll(ServerPlayer sp, float yaw) {
        if (!canRoll(sp)) return;
        ROLLED.put(sp.getUUID(), Tasks.now());
        broadcast(sp, "roll", yaw, true);
    }

    /** 마지막 구르기 틱 (시험 — 없으면 -1) */
    public static long lastRoll(ServerPlayer sp) {
        Long t = sp == null ? null : ROLLED.get(sp.getUUID());
        return t == null ? -1 : t;
    }

    // ------------------------------------------------------------ 알림

    /** 둘레 (그 사람을 보는 사람들) 에 동작을 알림. exceptSelf = 쓴 사람은 이미 그리는 중 (클라이언트가 먼저 시작) */
    static void broadcast(ServerPlayer sp, String motion, float yaw, boolean exceptSelf) {
        SENT.put(sp.getUUID(), motion);
        MotionNet.PlayPayload p = new MotionNet.PlayPayload(sp.getId(), motion, yaw);
        for (ServerPlayer o : PlayerLookup.tracking(sp)) {
            if (o != sp) MotionNet.send(o, p);
        }
        if (!exceptSelf) MotionNet.send(sp, p);
    }

    /** 시험: 이 사람의 마지막 동작 알림 (없으면 null) */
    public static String lastSent(ServerPlayer sp) {
        return sp == null ? null : SENT.get(sp.getUUID());
    }

    /** 시험: 클라이언트 요청처럼 (ReqPayload) */
    public static void testRequest(ServerPlayer sp, String kind, int step, float yaw) {
        request(sp, kind, step, yaw);
    }
}
