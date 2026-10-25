package mmorpg.core;

import java.util.List;

/** Weapon enhancement +1 … +10: config.js {@code enhanceBonus}, {@code enhanceCost} and weapons.js {@code enhanceOf}. */
public final class Enhance {
    private Enhance() {}

    private static final Obj ENHANCE = Rules.get().obj("ENHANCE");
    private static final List<Double> BONUS = ENHANCE.numbersOrNull("bonus");

    /** Highest enhancement level (ENHANCE.max). */
    public static final double MAX = ENHANCE.num("max");

    /**
     * Cost of one attempt: success {@code rate}, materials after the grade multiplier (rounded up), and
     * whether a failure drops one level ({@code down}, unless a guard scroll is used).
     */
    public record Cost(double rate, double stone, double bright, boolean down) {}

    /** config.js enhanceBonus: weapon damage increase of level n (0 … 0.52), n floored and clamped to 0 … max. */
    public static double enhanceBonus(double n) {
        double i = Math.max(0, Math.min(MAX, Math.floor(Double.isNaN(n) ? 0 : n)));
        return i == Math.rint(i) && i < BONUS.size() ? BONUS.get((int) i) : 0;
    }

    /** config.js enhanceCost: the cost to go from n to n + 1, or null when there is no such step. */
    public static Cost enhanceCost(double n, String gradeId) {
        Obj s = ENHANCE.obj("steps").objOrNull(Js.str(n + 1));
        if (s == null) return null;
        double k = ENHANCE.obj("gradeCost").numOr(gradeId, 1);
        return new Cost(s.num("rate"), Math.ceil(s.num("stone") * k - 1e-9), Math.ceil(s.num("bright") * k - 1e-9), n + 1 >= ENHANCE.num("downFrom"));
    }

    // ------------------------------------------------------------------ 0.43.0 장신구 · 아티팩트 강화 (rules_more ENHANCE_GEAR — 자바 전용)

    private static final Obj GEAR = Rules.get().root().objOrNull("ENHANCE_GEAR");
    private static final List<Double> GEAR_BONUS = GEAR == null ? List.of() : GEAR.numbersOrNull("bonus");

    /** 장신구 · 아티팩트 강화를 쓰는지 (순수 규칙에는 없음) */
    public static boolean gearEnabled() {
        return GEAR != null && !GEAR_BONUS.isEmpty();
    }

    /** 장신구 옵션 · 아티팩트 스탯 줄의 강화 배율 몫 (단계 n — 0 ~ max, 없으면 0): 값 × (1 + gearBonus) */
    public static double gearBonus(double n) {
        if (GEAR_BONUS.isEmpty()) return 0;
        int i = (int) Math.max(0, Math.min(Math.min(MAX, GEAR_BONUS.size() - 1), Math.floor(Double.isNaN(n) ? 0 : n)));
        return GEAR_BONUS.get(i);
    }

    /** 장신구 · 아티팩트 강화 한 번 (n → n + 1): 무기와 같은 확률 · 하락, 재료 × ENHANCE_GEAR.costScale (올림). 없는 단계면 null */
    public static Cost gearCost(double n, String gradeId) {
        Cost c = enhanceCost(n, gradeId);
        if (c == null) return null;
        double k = GEAR == null ? 1 : GEAR.numOr("costScale", 1);
        if (k == 1) return c;
        return new Cost(c.rate(), Math.ceil(c.stone() * k - 1e-9), Math.ceil(c.bright() * k - 1e-9), c.down());
    }

    /**
     * weapons.js enhanceOf, given the item's stored "rpg:enhance" value: a positive number floored and
     * capped at 10, anything else 0.
     */
    public static double enhanceOf(Object stored) {
        if (stored instanceof Number num && num.doubleValue() > 0) return Math.min(10, Math.floor(num.doubleValue()));
        return 0;
    }
}
