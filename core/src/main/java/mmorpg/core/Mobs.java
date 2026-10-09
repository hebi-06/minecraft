package mmorpg.core;

import java.util.ArrayList;
import java.util.List;

/** Monster and boss ids and boss layout helpers (config.js). */
public final class Mobs {
    private Mobs() {}

    private static final Obj BOSSES = Rules.get().obj("BOSSES");

    /** config.js monsterId: entity id of a monster ({@code rpg:<key>}). */
    public static String monsterId(String key) {
        return "rpg:" + key;
    }

    /** config.js bossId: entity id of a boss unit ({@code rpg:boss_<key>}). */
    public static String bossId(String key) {
        return "rpg:boss_" + key;
    }

    /** config.js altarId: altar block id of a boss fight ({@code rpg:altar_<key>}). */
    public static String altarId(String key) {
        return "rpg:altar_" + key;
    }

    /** BOSSES[key].units (multi-boss fights such as the scorpions), or null. */
    public static List<Obj> units(String key) {
        Obj b = BOSSES.objOrNull(key);
        return b == null ? null : b.objsOrNull("units");
    }

    /** config.js bossUnitKeys: the entity keys of a fight ([key] for one boss, the units' keys for several). */
    public static List<String> bossUnitKeys(String key) {
        List<Obj> units = units(key);
        if (units == null) return List.of(key);
        List<String> out = new ArrayList<>(units.size());
        for (Obj u : units) out.add(u.strOrNull("key"));
        return out;
    }

    /**
     * config.js unitHomeRadius: how far a boss unit may roam (minecraft:home): arena radius − 1, minus
     * the unit's spawn distance from the center when it has a spawn point. Unknown boss: throws.
     *
     * @param unit one of {@link #units(String)}, or null for a single boss
     */
    public static double unitHomeRadius(String key, Obj unit) {
        Obj boss = BOSSES.objOrNull(key);
        if (boss == null) throw new IllegalArgumentException("unknown boss: " + key);
        double r = boss.obj("arena").num("radius");
        List<Object> spawn = unit == null ? null : unit.listOrNull("spawn");
        return spawn != null ? r - 1 - Math.hypot(coord(spawn, 0), coord(spawn, 1)) : r - 1;
    }

    /** spawn[i] as a number (NaN when missing, like {@code Math.hypot(undefined)}). */
    private static double coord(List<Object> spawn, int i) {
        return i < spawn.size() && spawn.get(i) instanceof Number n ? n.doubleValue() : Double.NaN;
    }
}
