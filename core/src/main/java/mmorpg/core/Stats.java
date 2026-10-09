package mmorpg.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stats: config.js {@code STAT_KEYS}, {@code STAT_INFO}, {@code damagePerPoint} and the pure math of
 * stats.js. A stat sheet is a {@code Map<String, Double>} such as
 * {@code {str, dex, int, vit, point, lvmax}}; a missing stat reads as 0 ({@code s[k] ?? 0}).
 */
public final class Stats {
    private Stats() {}

    private static final Obj CONFIG = Rules.get().obj("CONFIG");
    private static final Obj STAT_INFO = Rules.get().obj("STAT_INFO");

    /** STAT_KEYS (스탯 창 순서): str, dex, int, vit. */
    public static final List<String> STAT_KEYS = Rules.get().strings("STAT_KEYS");

    /** The stats getStatsCached adds equipment bonus to (written out in stats.js, not taken from STAT_KEYS). */
    private static final List<String> MERGED = List.of("str", "dex", "int", "vit");

    /** {@code s[k] ?? 0}. */
    public static double get(Map<String, Double> s, String key) {
        Double v = s.get(key);
        return v == null ? 0 : v;
    }

    /** STAT_INFO[stat] (name, color, dark, icon, objective, effect, weapons, perPoint); unknown stat throws. */
    public static Obj info(String stat) {
        Obj i = STAT_INFO.objOrNull(stat);
        if (i == null) throw new IllegalArgumentException("unknown stat: " + stat);
        return i;
    }

    /** config.js damagePerPoint: weapon damage per stat point ({@code STAT_INFO[stat]?.perPoint ?? 0}). */
    public static double damagePerPoint(String stat) {
        Obj i = STAT_INFO.objOrNull(stat);
        return i == null ? 0 : i.numOr("perPoint", 0);
    }

    /**
     * stats.js getStatsCached: effective stats = round((spent + equipment bonus) × (1 + rune buff)) for
     * str/dex/int/vit; other keys (point, lvmax) are copied from {@code base}.
     *
     * @param base  spent stats (scoreboard), with point and lvmax
     * @param bonus armor bonus (setStatBonus; missing stats count 0)
     * @param buff  rune buff ratio as given to setStatBuff (≤ 0 means none)
     */
    public static Map<String, Double> effectiveStats(Map<String, Double> base, Map<String, Double> bonus, double buff) {
        double k = 1 + (buff > 0 ? buff : 0);
        Map<String, Double> out = new LinkedHashMap<>(base);
        for (String s : MERGED) {
            Double v = base.get(s);
            out.put(s, Js.round(((v == null ? Double.NaN : v) + get(bonus, s)) * k));
        }
        return out;
    }

    /**
     * stats.js reqLevelOf: the level used by weapon/armor/quest level checks = max(highest recorded XP
     * level, current XP level) — a level reached this tick counts before it is recorded.
     */
    public static double reqLevelOf(double lvmax, double level) {
        return Math.max(lvmax, Double.isNaN(level) ? 0 : level);
    }

    /** stats.js checkLevelUp: stat points for reaching XP level {@code level} when {@code best} was recorded (0 if not higher). */
    public static double checkLevelUp(double level, double best) {
        if (level <= best) return 0;
        return (level - best) * CONFIG.num("POINTS_PER_LEVEL");
    }
}
