package mmorpg.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Boss weapon crafting at the smith (config.js {@code BOSS_CRAFT}, {@code craftMaterials}, {@code craftUseLines}). */
public final class Crafting {
    private Crafting() {}

    private static final Rules R = Rules.get();
    private static final Obj WEAPON_NAMES = R.obj("WEAPON_NAMES");

    /** All recipes (BOSS_CRAFT, in order). */
    public static final List<Obj> RECIPES = R.root().objsOrNull("BOSS_CRAFT");

    /** One material of a recipe: {@code kind} "boss" (BOSS_MATERIALS key) or "drop" (MONSTER_DROPS key). */
    public record Material(String key, String kind, double count) {}

    /** config.js craftMaterials: main boss material, other boss materials, then monster drops. */
    public static List<Material> craftMaterials(Obj recipe) {
        List<Material> out = new ArrayList<>();
        out.add(new Material(recipe.strOrNull("mat"), "boss", recipe.num("count")));
        addPairs(out, recipe.listOrNull("also"), "boss");
        addPairs(out, recipe.listOrNull("drops"), "drop");
        return out;
    }

    private static void addPairs(List<Material> out, List<Object> pairs, String kind) {
        if (pairs == null) return;
        for (Object p : pairs) {
            List<?> pair = (List<?>) p;
            out.add(new Material((String) pair.get(0), kind, ((Number) pair.get(1)).doubleValue()));
        }
    }

    /**
     * config.js craftUseLines: the "보스 무기 제작" lore lines of a material — one per recipe using it
     * ("§r§f그림자 단검 §7영웅 8"), in BOSS_CRAFT order.
     */
    public static List<String> craftUseLines(String kind, String key) {
        List<String> out = new ArrayList<>();
        for (Obj r : RECIPES) {
            for (Material m : craftMaterials(r)) {
                if (m.kind().equals(kind) && Objects.equals(m.key(), key)) {
                    String grade = r.str("grade");
                    out.add("§r§f" + WEAPON_NAMES.obj(r.str("type")).text(grade) + " §7" + (grade.equals("hero") ? "영웅" : "초월") + " " + Js.str(m.count()));
                    break;
                }
            }
        }
        return out;
    }
}
