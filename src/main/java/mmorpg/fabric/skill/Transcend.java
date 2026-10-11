package mmorpg.fabric.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Obj;
import mmorpg.core.Weapon;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

/**
 * 초월 무기 스킬 쓰기 (skills.js castSkill · 입력 · 재사용 대기 표시). 수치 · 이름은 규칙 코어의 SKILLS.
 * <ul>
 *   <li>근접 (그람 · 클라우 솔라스 · 궁니르 · 아이기스 · 티르빙): 우클릭 = 스킬 1, 웅크리고 우클릭 = 스킬 2</li>
 *   <li>활 · 완드 (아르테미스 · 간반테인): 짧게 우클릭 = 평타, 웅크리고 우클릭 = 스킬 1, 길게 (SKILLS.holdTicks 틱) = 스킬 2
 *       — 누르고 있는 동안 아이템 사용 (RpgWeaponItem 의 사용 시간), 떼면 평타</li>
 * </ul>
 * 요구 조건 (레벨 · 요구 스탯) 은 무기와 같음. 재사용 대기는 스킬마다 따로, 초월 무기를 들고 있으면 화면 하단에 표시 (쓸 수 없으면 까닭) ·
 * 준비되면 작은 소리. 스킬이 대상이 없어 취소하면 (false) 재사용 대기 없음.
 */
public final class Transcend {
    private Transcend() {}

    /** 스킬 하나: 썼으면 true (false = 취소 — 재사용 대기 없음) */
    @FunctionalInterface
    public interface Skill {
        boolean cast(ServerPlayer sp, Weapon w, Obj k);
    }

    public static final int HOLD_TICKS = (int) Kit.SKILLS.num("holdTicks");
    static final int HUD_EVERY = (int) Kit.SKILLS.num("hudEvery");

    private static final Map<String, Skill> FN = new HashMap<>();
    /** "플레이어|스킬 id" → 다시 쓸 수 있는 틱 */
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();
    /** 준비 알림을 이미 한 것 */
    private static final Set<String> READY_ANNOUNCED = new HashSet<>();

    /** 활 · 완드 누르기 (짧게 / 길게) */
    private static final class Press {
        final Weapon w;
        final long tick;
        boolean done;

        Press(Weapon w, long tick) {
            this.w = w;
            this.tick = tick;
        }
    }

    private static final Map<UUID, Press> PRESSES = new HashMap<>();

    /** 시험용: true 면 재사용 대기를 무시 */
    public static boolean ignoreCooldown = false;

    public static void register(String id, Skill fn) {
        FN.put(id, fn);
    }

    /** 자바 판에 들어온 스킬인지 */
    public static boolean has(String id) {
        return FN.containsKey(id);
    }

    public static void init() {
        Kit.init();
        Guards.init();
        Status.init();
        Skills.registerAll();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = Tasks.now();
            if (now % HUD_EVERY == 0) hudTick(server);
            if (now % 100 == 0) Kit.prune();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            COOLDOWNS.clear();
            READY_ANNOUNCED.clear();
            PRESSES.clear();
        });
    }

    /** 나감: 이 사람의 기록을 모두 지움 */
    public static void forget(UUID id) {
        PRESSES.remove(id);
        String p = id + "|";
        COOLDOWNS.keySet().removeIf(k -> k.startsWith(p));
        READY_ANNOUNCED.removeIf(k -> k.startsWith(p));
        Kit.forget(id);
        Guards.clear(id);
        Status.forget(id);
        Aegis.forget(id);
    }

    private static String key(ServerPlayer sp, Obj k) {
        return sp.getUUID() + "|" + k.str("id");
    }

    /** 남은 재사용 대기 (틱) */
    public static long cooldownLeft(ServerPlayer sp, Obj k) {
        return Math.max(0, COOLDOWNS.getOrDefault(key(sp, k), 0L) - Tasks.now());
    }

    /** 재사용 대기를 모두 지움 (시험 · 명령) */
    public static void resetCooldowns(ServerPlayer sp) {
        String p = sp.getUUID() + "|";
        COOLDOWNS.keySet().removeIf(k -> k.startsWith(p));
    }

    static String sec(double s) {
        return String.format(Locale.ROOT, "%.1f", s);
    }

    /**
     * 스킬 쓰기 (skills.js castSkill): 침묵 · 요구 조건 · 재사용 대기를 확인하고, 스킬이 취소하면 (대상 없음 등) 재사용 대기 없음.
     * @param index 0 = 스킬 1, 1 = 스킬 2
     */
    public static boolean castSkill(ServerPlayer sp, Weapon w, int index) {
        List<Obj> ks = w.skills();
        if (ks == null || index < 0 || index >= ks.size() || sp.isRemoved()) return false;
        Obj k = ks.get(index);
        if (Status.blockedBySilence(sp)) return false;
        if (mmorpg.fabric.mount.Riding.blocked(sp, "skill")) return false; // 탈것에 탄 동안 (riding.js)
        if (!RpgCombat.lackOf(sp, w).isEmpty()) {
            Notify.warn(sp, RpgCombat.reqText(sp, w));
            return false;
        }
        long left = cooldownLeft(sp, k);
        if (left > 0 && !ignoreCooldown) {
            Notify.warn(sp, "§c" + k.str("name") + " §7재사용 대기 §f" + sec(left / 20.0) + "초");
            return false;
        }
        Skill fn = FN.get(k.str("id"));
        if (fn == null) {
            Notify.warn(sp, "§c" + k.str("name") + " §7— 자바 판에는 아직 없는 스킬입니다");
            return false;
        }
        boolean ok;
        try {
            ok = fn.cast(sp, w, k);
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[mmorpg] skill {}", k.str("id"), e);
            ok = false;
        }
        if (!ok) return false;
        String key = key(sp, k);
        COOLDOWNS.put(key, Tasks.now() + Math.round(k.num("cd") * 20 * mmorpg.fabric.stattree.StatTrees.cdMult(sp))); // 스탯 트리 재사용 대기 (7단계)
        READY_ANNOUNCED.remove(key);
        Notify.notify(sp, "§b§l" + k.str("name"), 20);
        return true;
    }

    // ------------------------------------------------------------ 입력 (RpgWeaponItem 이 부름)

    /**
     * 초월 무기 우클릭 (Item#use — 클라이언트 · 서버 둘 다). 근접은 바로 스킬, 활 · 완드는 웅크리면 스킬 1, 아니면 누르고 있기 시작 (사용)
     */
    public static InteractionResult use(Player p, InteractionHand hand, Weapon w) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        boolean sneak = p.isShiftKeyDown();
        if (!RpgCombat.isRanged(w)) {
            if (p instanceof ServerPlayer sp) castSkill(sp, w, sneak ? 1 : 0);
            return InteractionResult.CONSUME;
        }
        if (sneak) {
            if (p instanceof ServerPlayer sp) {
                PRESSES.remove(sp.getUUID());
                castSkill(sp, w, 0);
            }
            return InteractionResult.CONSUME;
        }
        p.startUsingItem(hand);
        if (p instanceof ServerPlayer sp) PRESSES.put(sp.getUUID(), new Press(w, Tasks.now()));
        return InteractionResult.CONSUME;
    }

    /** 누르고 있는 동안 매 틱 (서버): holdTicks 가 지나면 스킬 2 (그 뒤로는 떼어도 아무 일 없음) */
    public static void useTick(ServerPlayer sp, Weapon w) {
        Press press = PRESSES.get(sp.getUUID());
        if (press == null || press.done) return;
        if (!press.w.id().equals(w.id())) {
            PRESSES.remove(sp.getUUID());
            return;
        }
        if (Tasks.now() - press.tick < HOLD_TICKS) return;
        press.done = true;
        if (Kit.holding(sp, w)) castSkill(sp, w, 1);
    }

    /** 뗐을 때 (서버): 짧게 눌렀으면 평타 (다 누른 뒤의 떼기는 이미 스킬 2) */
    public static void release(ServerPlayer sp, Weapon w) {
        Press press = PRESSES.remove(sp.getUUID());
        if (press == null || press.done) return;
        press.done = true;
        if (!Kit.holding(sp, press.w)) return;
        if (Tasks.now() - press.tick >= HOLD_TICKS - 1) castSkill(sp, press.w, 1);
        else RpgCombat.fireRanged(sp, sp.getMainHandItem(), press.w, null);
    }

    // ------------------------------------------------------------ 재사용 대기 표시 (화면 하단)

    /** 상태 줄 글자: 스킬마다 "조작 이름 상태" (쓸 수 없는 무기면 까닭) */
    public static String hudText(ServerPlayer sp, Weapon w) {
        if (!RpgCombat.lackOf(sp, w).isEmpty()) return RpgCombat.reqText(sp, w);
        boolean ranged = RpgCombat.isRanged(w);
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < w.skills().size(); i++) {
            Obj k = w.skills().get(i);
            long left = cooldownLeft(sp, k);
            String how = ranged ? (i == 0 ? "웅크리고" : "길게") : (i == 0 ? "우클릭" : "웅크리고");
            String state = left > 0 ? "§c" + sec(left / 20.0) + "초" : k.has("cost") && !Kit.canPayCurse(sp, k) ? "§c체력 부족" : "§a준비";
            parts.add("§7" + how + " §f" + k.str("name") + " " + state);
        }
        return String.join(" §8| ", parts);
    }

    static void hudTick(MinecraftServer server) {
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            Weapon w = RpgCombat.weaponOf(sp.getMainHandItem());
            if (w == null || w.skills() == null) continue;
            for (Obj k : w.skills()) {
                String key = key(sp, k);
                if (cooldownLeft(sp, k) == 0 && COOLDOWNS.containsKey(key) && READY_ANNOUNCED.add(key)) SkillFx.playFor(sp, "random.orb", 1.9, 0.35);
            }
            if (!Notify.actionbarFree(sp)) continue;
            Notify.bar(sp, hudText(sp, w) + Notify.hudSuffix(sp));
        }
    }
}
