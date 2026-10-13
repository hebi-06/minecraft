package mmorpg.core;

import java.util.List;

/**
 * Weapon skills: config.js {@code SKILLS} (transcend — two per type), {@code skillTotalMult}, {@code tyrfingHpFactor};
 * Java-only {@code HERO_SKILLS} (stage 10 — one per hero weapon type, rules_more.json; absent with pure rules).
 */
public final class Skills {
    private Skills() {}

    private static final Obj SKILLS = Rules.get().obj("SKILLS");
    /** HERO_SKILLS (Java overlay) or null (pure rules — the parity test) */
    private static final Obj HERO = Rules.get().root().objOrNull("HERO_SKILLS");

    /** SKILLS[type]: the two skills of a weapon type, or null. */
    public static List<Obj> weaponSkills(String type) {
        return SKILLS.get(type) instanceof List ? SKILLS.objsOrNull(type) : null;
    }

    /** HERO_SKILLS[type]: the one skill of a hero weapon type (as a list), or null (no such type · pure rules). */
    public static List<Obj> heroSkills(String type) {
        if (HERO == null || type.startsWith("_")) return null;
        Obj k = HERO.objOrNull(type);
        return k == null ? null : List.of(k);
    }

    /** The weapon types that have a hero skill (HERO_SKILLS order, comments skipped). */
    public static List<String> heroTypes() {
        if (HERO == null) return List.of();
        List<String> out = new java.util.ArrayList<>();
        for (String t : HERO.keys()) if (!t.startsWith("_")) out.add(t);
        return List.copyOf(out);
    }

    /**
     * config.js skillTotalMult: one use's multiplier on a single target, crits excluded:
     * (mult ?? reflect) × (hits ?? shots ?? waves ?? 1) + boltMult + spellMult + afterMult + backMult
     * + pulseMult × pulses + pillarMult + foldMult.
     */
    public static double skillTotalMult(Obj k) {
        Double mult = k.numOrNull("mult");
        double each = mult != null ? mult : k.numOr("reflect", 0);
        Double hits = k.numOrNull("hits");
        Double shots = k.numOrNull("shots");
        double times = hits != null ? hits : shots != null ? shots : k.numOr("waves", 1);
        return each * times + k.numOr("boltMult", 0) + k.numOr("spellMult", 0) + k.numOr("afterMult", 0) + k.numOr("backMult", 0)
                + k.numOr("pulseMult", 0) * k.numOr("pulses", 0) + k.numOr("pillarMult", 0) + k.numOr("foldMult", 0);
    }

    /** config.js tyrfingHpFactor: Tyrfing skill damage × max health ÷ SKILLS.tyrfing.hpBase (1 if either is not positive). */
    public static double tyrfingHpFactor(double maxHp) {
        Obj t = SKILLS.objOrNull("tyrfing");
        double base = t == null ? 100 : t.numOr("hpBase", 100);
        return maxHp > 0 && base > 0 ? maxHp / base : 1;
    }
}
