package mmorpg.fabric.rune;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Runes;
import mmorpg.core.Weapon;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * 룬 쓰기 (runes.js "쓰기" · 재사용 대기 표시 · 바로 쓰기): 룬 효과 표 (RUNE_FN — 계열마다 register), 재사용 대기 (핫바 아이콘의 쓸기 표시도),
 * 콤보 (암살자 계열이 넣음) · 쓴 뒤 할 일 (마검사 계열의 마검 부여), 화면 하단 상태 줄 (룬 이름 · 준비 · 적용 스탯 · 보호막 · 덧붙임).
 * 룬은 요구 스탯 없음. 위력 P = 룬 위력 (runePower — 실제 스탯) × 콤보 배율 × 돌파 계수 (8단계 — 룬 아이템의 돌파 단계, Breakthrough).
 */
public final class RuneCast {
    private RuneCast() {}

    /** 룬 효과 하나 (runes.js 의 groundSlam 등): P = 위력, k = r.def(). false = 대상이 없어 취소 (재사용 대기 없음) */
    @FunctionalInterface
    public interface RuneFn {
        boolean cast(ServerPlayer sp, Runes.Rune r, double P, Obj k);
    }

    /** 콤보 계획 (암살자 계열): 위력 배율 · 나갈 틱 (앞 룬의 마지막 타격 뒤 — 지금보다 뒤면 그때 나감) */
    public record Plan(double mult, long start) {}

    /** 암살자 룬 콤보 (runes.js comboPlan · comboCommit · comboLanded · comboRollback · comboOf) — 암살자 계열이 넣음 */
    public interface Combo {
        /** 이번에 쓸 룬의 콤보 계획 */
        Plan plan(ServerPlayer sp, Runes.Rune r);

        /** 계획을 적음 (콤보 상태 — 아래 셋에 다시 넘김) */
        Object commit(ServerPlayer sp, Runes.Rune r, Plan plan);

        /** 룬이 나감 */
        void landed(ServerPlayer sp, Runes.Rune r, Object state);

        /** 나가지 못함 (대상이 사라짐 · 죽음) — 적은 것을 되돌림 */
        void rollback(ServerPlayer sp, Runes.Rune r, Object state);

        /** 죽거나 차원을 옮김: state 가 지금 콤보면 끝냄 */
        void drop(ServerPlayer sp, Object state);

        /** 상태 줄 덧붙임 (" §8| §d암살자 2/5 §l3 콤보" — 없으면 "") */
        String hud(ServerPlayer sp);
    }

    /** 룬이 나간 뒤 할 일 (계열 → 마검사: 마검 부여) */
    @FunctionalInterface
    public interface AfterCast {
        void run(ServerPlayer sp, Runes.Rune r, double P);
    }

    private static final Obj RUNE = RuneKit.RUNE;
    private static final String COMBO_FAMILY = Rules.get().obj("RUNE_COMBO").str("family");
    private static final Map<String, RuneFn> FN = new HashMap<>();
    private static final Map<String, AfterCast> AFTER_CAST = new HashMap<>();
    /** "플레이어|룬 ID" → 재사용 대기가 끝나는 틱 */
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();
    /** 준비 소리를 낸 재사용 대기 */
    private static final Set<String> READY_ANNOUNCED = new HashSet<>();
    /** 바로 쓰기: 플레이어 → 지난 틱에 고른 칸 */
    private static final Map<UUID, Integer> LAST_SLOT = new HashMap<>();
    public static Combo combo;
    /** 시험용: 재사용 대기를 보지 않음 */
    public static boolean ignoreCooldown;

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            quickTick(server);
            if (Tasks.now() % Math.max(1, (long) RUNE.num("hudEvery")) == 0) hudTick(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            COOLDOWNS.clear();
            READY_ANNOUNCED.clear();
            LAST_SLOT.clear();
        });
    }

    static void forget(UUID id) {
        String p = id + "|";
        COOLDOWNS.keySet().removeIf(k -> k.startsWith(p));
        READY_ANNOUNCED.removeIf(k -> k.startsWith(p));
        LAST_SLOT.remove(id);
    }

    /** 룬 효과 등록 (RUNE_FN[fn]) */
    public static void register(String fn, RuneFn f) {
        FN.put(fn, f);
    }

    public static boolean has(String fn) {
        return FN.containsKey(fn);
    }

    /** 룬 효과 (없으면 null — 돌파 단계 효과의 "한 번 더") */
    static RuneFn fn(String name) {
        return FN.get(name);
    }

    /** 등록한 룬 효과 수 */
    public static int count() {
        return FN.size();
    }

    /** 이 계열의 룬이 나간 뒤 할 일 (계열마다 하나) */
    public static void afterCast(String family, AfterCast fn) {
        AFTER_CAST.put(family, fn);
    }

    private static String key(ServerPlayer sp, Runes.Rune r) {
        return sp.getUUID() + "|" + r.id();
    }

    /** 남은 재사용 대기 (틱, runeCooldownLeft) */
    public static long cooldownLeft(ServerPlayer sp, Runes.Rune r) {
        Long until = COOLDOWNS.get(key(sp, r));
        return until == null ? 0 : Math.max(0, until - Tasks.now());
    }

    /** 시험용: 이 사람의 룬 재사용 대기를 모두 지움 (핫바 쓸기 표시도) */
    public static void resetCooldowns(ServerPlayer sp) {
        String p = sp.getUUID() + "|";
        for (String k : COOLDOWNS.keySet()) if (k.startsWith(p)) sp.getCooldowns().removeCooldown(Identifier.parse(k.substring(p.length())));
        COOLDOWNS.keySet().removeIf(k -> k.startsWith(p));
        READY_ANNOUNCED.removeIf(k -> k.startsWith(p));
    }

    /** 룬 위력 P (runePower — 실제 스탯: 찍은 스탯 + 룬 버프) */
    public static double power(ServerPlayer sp, String family) {
        return Runes.runePower(RpgStats.effective(sp), family);
    }

    /** 적용 스탯 (runeStat) */
    public static Runes.Applied applied(ServerPlayer sp, String family) {
        return Runes.runeStat(RpgStats.effective(sp), family);
    }

    private static String sec(long ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }

    /** 핫바 아이콘의 쓸기 표시 (같은 룬 아이템 모두 — 아이템 재사용 대기, 대기 중에는 우클릭도 막힘) */
    private static void startItemCooldown(ServerPlayer sp, Runes.Rune r) {
        sp.getCooldowns().addCooldown(Identifier.parse(r.id()), (int) cooldownTicks(sp, r));
    }

    /** 재사용 대기 틱 = 룬의 초 × 20 × 스탯 트리 재사용 대기 (7단계) */
    public static long cooldownTicks(ServerPlayer sp, Runes.Rune r) {
        return Math.round(r.cd() * 20 * mmorpg.fabric.stattree.StatTrees.cdMult(sp));
    }

    /** 쓸기 표시를 풂 (룬이 나가지 않았을 때 · 재사용 대기를 돌려줄 때) */
    public static void clearItemCooldown(ServerPlayer sp, Runes.Rune r) {
        sp.getCooldowns().removeCooldown(Identifier.parse(r.id()));
    }

    /** 재사용 대기 시작 (+ 핫바 쓸기 표시, 쓴 룬 이름 · 돌파 단계 · 적용 스탯 알림) */
    public static void startCooldown(ServerPlayer sp, Runes.Rune r) {
        String key = key(sp, r);
        COOLDOWNS.put(key, Tasks.now() + cooldownTicks(sp, r));
        READY_ANNOUNCED.remove(key);
        startItemCooldown(sp, r);
        String fn = r.def().strOrNull("fn");
        String tag = fn == null ? "" : Breakthrough.nameTag(Breakthrough.castLevel(sp, fn));
        Notify.notify(sp, r.gradeColor() + r.def().str("name") + tag + "§r §7(" + Runes.runeStatLabel(applied(sp, r.family())) + ")", 24);
    }

    /** 재사용 대기를 돌려줌 (콤보로 미뤄 둔 룬이 나가지 못함) */
    public static void refundCooldown(ServerPlayer sp, Runes.Rune r) {
        COOLDOWNS.remove(key(sp, r));
        clearItemCooldown(sp, r);
    }

    /** 룬 쓰기 (돌파 0돌 — 시험 · 명령) */
    public static boolean castRune(ServerPlayer sp, Runes.Rune r, boolean quick) {
        return castRune(sp, r, quick, 0);
    }

    /**
     * 룬 쓰기 (castRune). 재사용 대기를 확인하고, 효과가 대상이 없어 취소하면 재사용 대기 없음.
     * 암살자 룬: 콤보 단계만큼 위력 배율, 이어 쓴 룬은 앞 룬의 마지막 타격 뒤에 나감 (그 사이 죽거나 못 나가면 재사용 대기를 돌려줌).
     * quick = 칸을 골라서 쓴 것 (재사용 대기 중이면 소리 없이 알림만).
     * breakLevel = 룬 아이템의 돌파 단계 (8단계): 위력 × 계수, 룬 정의는 그 단계의 것 (초월 3 · 6돌 강화 · 회복 · 보호막 × 계수), 화면 색 연출 단계
     */
    public static boolean castRune(ServerPlayer sp, Runes.Rune base, boolean quick, int breakLevel) {
        if (base == null || sp.isRemoved()) return false;
        int lv = mmorpg.core.RuneBreak.clamp(breakLevel);
        Runes.Rune r = mmorpg.core.RuneBreak.leveled(base, lv);
        if (Status.blockedBySilence(sp)) return false; // 보스 기믹: 침묵
        if (mmorpg.fabric.mount.Riding.blocked(sp, "skill")) return false; // 탈것에 탄 동안 (riding.js)
        long left = cooldownLeft(sp, r);
        if (left > 0 && !ignoreCooldown) {
            String text = "§c" + r.def().str("name") + " §7재사용 대기 §f" + sec(left) + "초";
            if (quick) Notify.notify(sp, text, 20);
            else Notify.warn(sp, text);
            return false;
        }
        RuneFn fn = FN.get(r.def().str("fn"));
        if (fn == null) {
            Notify.warn(sp, "§c" + r.def().str("name") + " §7— 자바 판에는 아직 없는 룬입니다");
            return false;
        }
        Breakthrough.noteCast(sp, r, lv); // 이 룬의 돌파 단계 (화면 색 연출 · 알림)
        Combo cb = r.family().equals(COMBO_FAMILY) ? combo : null;
        Plan plan = cb == null ? null : cb.plan(sp, r);
        double P = power(sp, r.family()) * (plan == null ? 1 : plan.mult()) * mmorpg.core.RuneBreak.coef(lv);
        BooleanSupplier run = () -> {
            Breakthrough.noteCast(sp, r, lv); // 콤보로 늦게 나가도 이 단계
            boolean ok;
            try {
                ok = fn.cast(sp, r, P, r.def());
            } catch (RuntimeException e) {
                Mmorpg.LOG.warn("[mmorpg] rune {}", r.id(), e);
                ok = false;
            }
            if (ok) Breakthrough.onCast(sp, r, lv, P); // 6돌 초월: 금빛 각성 · 단계 효과 (한 번 더)
            return ok;
        };
        long now = Tasks.now();
        if (plan != null && plan.start() > now) {
            startCooldown(sp, r);
            Object st = cb.commit(sp, r, plan);
            String life = RuneKit.lifeToken(sp);
            Tasks.later((int) (plan.start() - now), () -> {
                boolean alive = RuneKit.sameLife(sp, life);
                if (!alive) cb.drop(sp, st); // 죽거나 차원이 바뀌면 콤보도 끝
                if (alive && run.getAsBoolean()) cb.landed(sp, r, st);
                else {
                    cb.rollback(sp, r, st);
                    if (!sp.isRemoved()) refundCooldown(sp, r);
                }
            });
            return true;
        }
        if (!run.getAsBoolean()) return false;
        startCooldown(sp, r);
        if (plan != null) cb.landed(sp, r, cb.commit(sp, r, plan));
        AfterCast after = AFTER_CAST.get(r.family());
        if (after != null) after.run(sp, r, P);
        return true;
    }

    // ------------------------------------------------------------ 입력

    /** 룬 아이템 우클릭 (onRuneItemUse — RuneItem.use, 서버): 그 아이템의 돌파 단계로 */
    public static void onUse(ServerPlayer sp, ItemStack stack, Runes.Rune r) {
        castRune(sp, r, false, Breakthrough.of(stack));
    }

    /** 룬 칸을 고르면 바로 쓰는지 (/runequick on — auto 는 터치일 때만이라 자바에서는 꺼짐) */
    public static boolean quickCastOn(ServerPlayer sp) {
        return "on".equals(RuneData.of(sp).quick());
    }

    /** 칸을 바꾼 틱에 고른 칸이 룬이면 바로 사용 (onHotbarSelect) */
    private static void quickTick(MinecraftServer server) {
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            int slot = sp.getInventory().getSelectedSlot();
            Integer last = LAST_SLOT.put(sp.getUUID(), slot);
            if (last == null || last == slot) continue;
            ItemStack stack = sp.getInventory().getItem(slot);
            Runes.Rune r = RuneItems.runeOf(stack);
            if (r == null || !quickCastOn(sp)) continue;
            if (sp.gameMode() == GameType.SPECTATOR || sp.isDeadOrDying()) continue;
            castRune(sp, r, true, Breakthrough.of(stack));
        }
    }

    // ------------------------------------------------------------ 재사용 대기 표시

    /** 들고 있는 것이 초월 무기인지 (그때는 스킬 줄이 상태 줄을 씀) */
    private static boolean heldSkillWeapon(ServerPlayer sp) {
        Weapon w = RpgCombat.weaponOf(sp.getMainHandItem());
        return w != null && w.skills() != null;
    }

    /** 상태 줄 (RUNE.hudEvery 틱마다): 룬을 들면 룬 줄, 아니면 덧붙임 (마검 부여 · 정령 · 신체 강화 …) 만 */
    static void hudTick(MinecraftServer server) {
        long now = Tasks.now();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            Runes.Rune r = RuneItems.runeOf(sp.getMainHandItem());
            // 준비 알림 (들고 있지 않아도 — 소리는 든 룬만)
            String p = sp.getUUID() + "|";
            for (Map.Entry<String, Long> e : COOLDOWNS.entrySet()) {
                String key = e.getKey();
                if (!key.startsWith(p) || READY_ANNOUNCED.contains(key) || e.getValue() > now) continue;
                READY_ANNOUNCED.add(key);
                if (r != null && key.equals(key(sp, r))) SkillFx.playFor(sp, "random.orb", 1.6, 0.35);
            }
            if (!Notify.actionbarFree(sp)) continue;
            if (r == null) {
                String suffix = Notify.hudSuffix(sp);
                if (suffix.isEmpty() || heldSkillWeapon(sp)) continue;
                Notify.bar(sp, suffix.substring(" §8| ".length()));
                continue;
            }
            long left = cooldownLeft(sp, r);
            String state = left > 0 ? "§c" + sec(left) + "초" : "§a준비";
            double sh = RpgHealth.shieldOf(sp);
            String comboText = combo != null && r.family().equals(COMBO_FAMILY) ? combo.hud(sp) : "";
            Notify.bar(sp, "§7룬 " + r.gradeColor() + r.def().str("name") + Breakthrough.nameTag(Breakthrough.of(sp.getMainHandItem())) + "§r " + state + " §8| §7"
                    + Runes.runeStatLabel(applied(sp, r.family()), "×")
                    + (sh > 0 ? " §8| §6보호막 " + (long) Math.ceil(sh) : "") + comboText + Notify.hudSuffix(sp));
        }
    }
}
