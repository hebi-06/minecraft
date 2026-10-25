package mmorpg.fabric.motion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Combat;
import mmorpg.core.Motions;
import mmorpg.core.Obj;
import mmorpg.core.SwingLooks;
import mmorpg.core.Weapon;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 0.41.0 평타 강화 모습 (서버 — rules_more SWING_LOOK, core SwingLooks): 사용자 "평타 강화 스킬은 평타 색이 바뀌고 범위가 더 넓어지는걸로" (고른 것: 룬마다 다르게 ·
 * 휘두를 때마다 1번 · 직업 평타 버프도 포함). 평타 강화 룬 (RuneStrike) · 직업 평타 태세 (JobTraits — 명경지수 · 마검 각성 · 사신 현신) 가 {@link #addSource} 로
 * 걸린 모습을 알려 주면:
 * <ul>
 *   <li>근접 평타의 참격 범위 (MOTION.area) 를 넓힘 — {@link #widen} (SwingArea): 거리 × 배율 (최소 칸), 띠 · 줄의 폭도 같은 배율</li>
 *   <li>둘레 사람들에게 평타 동작과 함께 모습을 알림 (MotionNet.PlayPayload.look — MotionSystem.swung), 내 화면에는 바뀔 때마다 (LookPayload — 내 평타는 내
 *       화면이 먼저 그리므로 미리) → 클라이언트 BladeTrails 가 궤적 색을 바꾸고 무기 끝 너머로 길게 그림 (범위 = 궤적)</li>
 *   <li>서버가 그리는 평타 연출 (창 찌르기 · 방패 밀치기 · 맞힘 · 치명타) 의 색 ({@link #styleFor}), 활 · 완드 평타 (평타 강화 룬만) 의 색 ({@link #shotColors})</li>
 * </ul>
 * 여러 모습이 함께 걸리면 든 무기에서 범위가 가장 넓은 것 하나.
 */
public final class SwingLook {
    private SwingLook() {}

    /** 걸린 모습 하나: 모습 · 끝 틱 · 활 · 완드 평타에도 붙는지 (평타 강화 룬) */
    public record Active(SwingLooks.Look look, long until, boolean ranged) {}

    /** 걸린 모습을 알려 줌 (룬 · 직업) */
    @FunctionalInterface
    public interface Source {
        void collect(ServerPlayer sp, List<Active> out);
    }

    private static final List<Source> SOURCES = new ArrayList<>();
    /** 내 화면에 마지막으로 보낸 모습 (사람 → 열쇠) */
    private static final Map<UUID, String> SENT = new HashMap<>();

    public static void addSource(Source s) {
        SOURCES.add(s);
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(SwingLook::tick);
    }

    public static void forget(UUID id) {
        SENT.remove(id);
    }

    /** 이 사람이 무기 w 를 들었을 때의 평타 모습 (없으면 null): 무기 종류에 맞고 (원거리 무기면 원거리에도 붙는 것만) 범위가 가장 넓은 것 */
    public static Active current(ServerPlayer sp, Weapon w) {
        if (sp == null || w == null || SOURCES.isEmpty() || !SwingLooks.enabled()) return null;
        List<Active> all = new ArrayList<>();
        for (Source s : SOURCES) {
            try {
                s.collect(sp, all);
            } catch (RuntimeException e) {
                Mmorpg.LOG.warn("[mmorpg] swing look source", e);
            }
        }
        if (all.isEmpty()) return null;
        boolean melee = Combat.isMeleeWeapon(w);
        double base = SwingLooks.baseRange(w.type(), w.gradeIndex());
        long now = Tasks.now();
        Active best = null;
        double bestK = 0;
        for (Active a : all) {
            if (a == null || a.look() == null || a.until() <= now || !a.look().fits(w.type()) || (!melee && !a.ranged())) continue;
            double k = a.look().scaleAt(base);
            if (best == null || k > bestK) {
                best = a;
                bestK = k;
            }
        }
        return best;
    }

    /** 근접 평타의 참격 범위 a 를 넓힘 (모습이 없으면 a 그대로): 거리 × 배율 (range 가 있으면 그 칸 아래로 줄지 않음), 띠 · 줄의 폭도 같은 배율 (부채꼴 각도 · 높이는 그대로) */
    public static Motions.Area widen(ServerPlayer sp, Weapon w, Motions.Area a) {
        if (a == null) return null;
        Active act = current(sp, w);
        if (act == null) return a;
        double k = act.look().scaleAt(a.range());
        if (!(k > 1 + 1e-9)) return a;
        boolean wide = a.shape().equals("band") || a.shape().equals("line");
        return new Motions.Area(a.shape(), a.range() * k, a.angle(), wide ? a.width() * k : a.width(), a.height());
    }

    /** 패킷용 모습 (없으면 NONE) */
    public static MotionNet.Look net(Active a) {
        if (a == null) return MotionNet.Look.NONE;
        SwingLooks.Look l = a.look();
        return new MotionNet.Look(SwingLooks.pack(l.glow()), SwingLooks.pack(l.aura()), (float) l.scale(), (float) l.range());
    }

    /** 평타 동작과 함께 알릴 모습 (궤적이 대신하는 평타만 — 스킬 동작 · 구르기는 NONE) */
    static MotionNet.Look forSwing(ServerPlayer sp, String motion) {
        if (motion == null || !Motions.trailBasic(motion)) return MotionNet.Look.NONE;
        Weapon w = RpgCombat.weaponOf(sp.getMainHandItem());
        return w == null ? MotionNet.Look.NONE : net(current(sp, w));
    }

    /**
     * 서버가 그리는 근접 평타 연출의 모양 (Slash — 창 찌르기 · 방패 밀치기 · 맞힘 · 치명타): 모습이 있으면 빛 · 심지 · 치명타 색을 모습 색으로 바꾼 복사본
     * (lookAura = 등급과 상관없는 바깥 기운), 없으면 style 그대로
     */
    public static Obj styleFor(ServerPlayer sp, Weapon w, Obj style) {
        if (style == null || sp == null || w == null) return style;
        Active a = current(sp, w);
        if (a == null || a.look().glow() == null) return style;
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : style.keys()) m.put(k, style.get(k));
        double[] glow = a.look().glow();
        double[] core = whiten(glow, 0.8);
        m.put("glow", list(glow));
        m.put("core", list(core));
        m.put("crit", List.of(list(glow), list(whiten(glow, 0.9))));
        m.put("critColor", list(whiten(glow, 0.3)));
        if (a.look().aura() != null) m.put("lookAura", list(a.look().aura()));
        return new Obj(m, "SLASH.look." + a.look().key());
    }

    /** 원거리 평타 (활 · 완드) 의 색 {빛, 심지} — 평타 강화 룬이 걸렸을 때만 (없으면 null) */
    public static double[][] shotColors(ServerPlayer sp, Weapon w) {
        Active a = sp == null || w == null ? null : current(sp, w);
        if (a == null || !a.ranged() || a.look().glow() == null) return null;
        return new double[][] {a.look().glow(), whiten(a.look().glow(), 0.8)};
    }

    private static double[] whiten(double[] c, double t) {
        return new double[] {c[0] + (1 - c[0]) * t, c[1] + (1 - c[1]) * t, c[2] + (1 - c[2]) * t};
    }

    private static List<Object> list(double[] c) {
        return List.of(c[0], c[1], c[2]);
    }

    /** 매 틱: 내 화면에 보낸 모습이 바뀌었으면 (걸림 · 끝남 · 다시 걺 · 무기를 바꿈) 다시 보냄 */
    private static void tick(MinecraftServer server) {
        if (SOURCES.isEmpty()) return;
        long now = Tasks.now();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (!MotionNet.canSendLook(sp)) continue;
            Weapon w = RpgCombat.weaponOf(sp.getMainHandItem());
            Active a = w == null ? null : current(sp, w);
            String key = a == null ? "" : a.look().key() + "|" + a.until() + "|" + a.look().range();
            String last = SENT.get(sp.getUUID());
            if (key.equals(last == null ? "" : last)) continue;
            SENT.put(sp.getUUID(), key);
            int ticks = a == null ? 0 : (int) Math.max(0, Math.min(Integer.MAX_VALUE, a.until() - now));
            ServerPlayNetworking.send(sp, new MotionNet.LookPayload(net(a), ticks));
        }
    }

    /** 시험: 이 사람이 지금 든 무기의 모습 열쇠 (없으면 null) */
    public static String testKey(ServerPlayer sp) {
        Weapon w = sp == null ? null : RpgCombat.weaponOf(sp.getMainHandItem());
        Active a = w == null ? null : current(sp, w);
        return a == null ? null : a.look().key();
    }
}
