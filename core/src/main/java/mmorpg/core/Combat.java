package mmorpg.core;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure parts of combat.js: damage and crit multipliers and the "cannot use" warning. Stats are
 * passed in ({@code s} = effective stats, see {@link Stats#effectiveStats}); the event handling
 * that applies them needs Minecraft.
 */
public final class Combat {
    private Combat() {}

    /** combat.js MELEE. */
    private static final Set<String> MELEE = Set.of("sword", "dagger", "axe", "shield", "greatsword");
    /** combat.js critChanceOf default cap (RUNE_STRIKE.critCap). */
    private static final double CRIT_CAP = Rules.get().obj("RUNE_STRIKE").num("critCap");

    /** combat.js isMeleeWeapon. */
    public static boolean isMeleeWeapon(Weapon w) {
        return w != null && MELEE.contains(w.type());
    }

    /** combat.js statMultiplier: 1 + Σ stat × per over the weapon's damage scaling (skills use it too). */
    public static double statMultiplier(Map<String, Double> s, Weapon w) {
        double sum = 0;
        for (Weapons.Scaling b : w.scaling()) sum = sum + Stats.get(s, b.stat()) * b.per();
        return 1 + sum;
    }

    /** combat.js powerMultiplier: stat multiplier × (1 + enhancement bonus of the held weapon's level). */
    public static double powerMultiplier(Map<String, Double> s, Weapon w, double enhance) {
        return statMultiplier(s, w) * (1 + Enhance.enhanceBonus(enhance));
    }

    /** combat.js critChanceOf with the default cap (RUNE_STRIKE.critCap). */
    public static double critChanceOf(Weapon w, double add) {
        return critChanceOf(w, add, CRIT_CAP);
    }

    /**
     * combat.js critChanceOf: weapon crit chance plus {@code add} (sum of the crit hooks, each ≥ 0);
     * with a bonus it is capped at {@code cap}, or at the weapon's own chance if that is higher.
     */
    public static double critChanceOf(Weapon w, double add, double cap) {
        return add > 0 ? Math.min(Math.max(cap, w.critChance()), w.critChance() + add) : w.critChance();
    }

    /** combat.js reqText without a player: all requirements. */
    public static String reqText(Weapon w) {
        return reqTextFor(w, null, Map.of());
    }

    /**
     * combat.js reqText: the warning when a weapon cannot be used (level first, since stats come with
     * levels). {@code reqLevel} = stats.js reqLevelOf, {@code baseStats} = spent stats.
     */
    public static String reqText(Weapon w, double reqLevel, Map<String, Double> baseStats) {
        return reqTextFor(w, reqLevel, baseStats);
    }

    private static String reqTextFor(Weapon w, Double reqLevel, Map<String, Double> baseStats) {
        List<Weapons.Lack> lack = reqLevel == null ? List.of() : Weapons.weaponLack(w, reqLevel, baseStats);
        boolean lvLack = lack.stream().anyMatch(Weapons.Lack::isLevel);
        boolean statLack = reqLevel == null || lack.stream().anyMatch(x -> !x.isLevel());
        String reqs = Weapons.reqsText(w.reqs(), false, "§c · ");
        if (!lvLack) return "§c요구 스탯 부족: " + reqs + "§c 필요 §7(스탯 책)";
        String now = "§7(최고 레벨 " + Js.str(reqLevel) + ")";
        if (!statLack) return "§c레벨 " + w.level() + "부터 쓸 수 있는 무기 " + now;
        return "§c레벨 " + w.level() + " · " + reqs + "§c 필요 " + now;
    }
}
