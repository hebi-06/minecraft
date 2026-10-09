package mmorpg.core;

/**
 * Pure math of hp.js (the add-on's own "RPG" health that sits on top of the 20-point vanilla bar).
 * The live part (events, vanilla mirror, HUD, dying) needs Minecraft and is not here; these are
 * the formulas it applies to one player's {hp, max, dead} state, taken out of hpState.
 */
public final class Hp {
    private Hp() {}

    private static final Obj CONFIG = Rules.get().obj("CONFIG");
    /** hp.js MIRROR_MAX: vanilla health points the RPG health is mirrored onto. */
    private static final double MIRROR_MAX = 20;

    /** New state after paying or healing: {@code hp} = new health, {@code amount} = what was paid / gained. */
    public record Change(double hp, double amount) {}

    /** hp.js maxHpOf: BASE_HP + level bonus + vit × HP_PER_VIT (vit = effective stat, armor and buffs included). */
    public static double maxHpOf(double lvmax, double vit) {
        return CONFIG.num("BASE_HP") + levelHpOf(lvmax) + vit * CONFIG.num("HP_PER_VIT");
    }

    /** hp.js levelHpOf: the part of max health from the highest XP level. */
    public static double levelHpOf(double lvmax) {
        return lvmax * CONFIG.numOr("HP_PER_LEVEL", 0);
    }

    /** hp.js hpNumber: health shown on the HUD, one decimal, at least 0.1 while alive. */
    public static String hpNumber(boolean dead, double hp) {
        if (dead || !(hp > 0)) return "0";
        return Js.str(Math.max(0.1, Js.round(hp * 10) / 10));
    }

    /**
     * hp.js refreshMaxHp: current health after max health changes from {@code max} to {@code newMax}.
     * Stats/levels ({@code keepRatio = false}) add the increase to current health; armor
     * ({@code keepRatio = true}) keeps the ratio so swapping armor never refills health.
     */
    public static double refreshMaxHp(double hp, double max, double newMax, boolean dead, boolean keepRatio) {
        if (newMax == max) return hp;
        double diff = newMax - max;
        double ratio = max > 0 ? hp / max : 1;
        if (dead) return hp;
        return keepRatio
                ? Math.min(newMax, Math.max(Math.min(1, hp), ratio * newMax))
                : Math.min(newMax, Math.max(1, hp + Math.max(0, diff)));
    }

    /**
     * hp.js payHp (Tyrfing's health cost; survival/adventure only): pays {@code amount} only if at
     * least 1 health remains (with a 1e-6 allowance for float error), otherwise pays nothing.
     */
    public static Change payHp(double hp, double amount, boolean dead) {
        if (!(amount > 0) || dead || hp - amount < 1 - 1e-6) return new Change(hp, 0);
        return new Change(Math.max(1, hp - amount), amount);
    }

    /** hp.js healBy: heal up to max health; {@code amount} of the result = health actually gained. */
    public static Change healBy(double hp, double max, double amount, boolean dead) {
        if (dead || !(amount > 0)) return new Change(hp, 0);
        double next = Math.min(max, hp + amount);
        return new Change(next, next - hp);
    }

    /** hp.js onPlayerHealBefore: a vanilla heal of {@code healing} (of 20) heals the same share of max health. */
    public static double vanillaHeal(double hp, double max, double healing, boolean dead) {
        if (dead || !(healing > 0)) return hp;
        return Math.min(max, hp + (healing * max) / MIRROR_MAX);
    }

    /** hp.js revive (totem of undying, stuck death): health after reviving = 10% of max, rounded, at least 1. */
    public static double reviveHp(double max) {
        return Math.max(1, Math.min(max, Js.round(max * 0.1)));
    }

    /** hp.js saveHp: the health ratio stored on the player (0.001 … 1, three decimals; 1 when dead). */
    public static double saveRatio(boolean dead, double hp, double max) {
        return dead || !(max > 0) ? 1 : Math.max(0.001, Math.min(1, Js.round((hp / max) * 1000) / 1000));
    }
}
