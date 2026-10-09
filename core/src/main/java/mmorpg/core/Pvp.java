package mmorpg.core;

/** Duel damage (duel.js {@code duelDamage}). */
public final class Pvp {
    private Pvp() {}

    private static final Obj PVP = Rules.get().obj("PVP");

    /** duel.js duelDamage with the configured PVP.mult and PVP.capRatio. */
    public static double duelDamage(double victimMaxHp, double damage) {
        return duelDamage(PVP.num("mult"), PVP.num("capRatio"), victimMaxHp, damage);
    }

    /**
     * duel.js duelDamage: damage between duel partners = damage × mult, and when capRatio &gt; 0 at most
     * the victim's max health × capRatio (the add-on reads max health from its HP state).
     */
    public static double duelDamage(double mult, double capRatio, double victimMaxHp, double damage) {
        double d = damage * mult;
        if (capRatio > 0) d = Math.min(d, victimMaxHp * capRatio);
        return d;
    }
}
