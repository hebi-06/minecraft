package mmorpg.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Weapon crafting at the smith (config.js {@code BOSS_CRAFT}, {@code craftMaterials}, {@code craftUseLines}) plus the Java-only
 * recipes of rules_more.json {@code CRAFT_MORE} (stage 12 — the four new weapon types: noble from monster drops only, hero · transcend
 * like BOSS_CRAFT). With pure rules (the parity test) there is no CRAFT_MORE, so everything is exactly the add-on's.
 */
public final class Crafting {
    private Crafting() {}

    private static final Rules R = Rules.get();
    private static final Obj WEAPON_NAMES = R.obj("WEAPON_NAMES");

    /** All recipes: BOSS_CRAFT in order, then CRAFT_MORE (Java overlay). */
    public static final List<Obj> RECIPES = recipes();

    private static List<Obj> recipes() {
        List<Obj> out = new ArrayList<>(R.root().objsOrNull("BOSS_CRAFT"));
        List<Obj> more = R.root().objsOrNull("CRAFT_MORE");
        if (more != null) out.addAll(more);
        return List.copyOf(out);
    }

    /** One material of a recipe: {@code kind} "boss" (BOSS_MATERIALS key) or "drop" (MONSTER_DROPS key). */
    public record Material(String key, String kind, double count) {}

    /**
     * config.js craftMaterials: main boss material, other boss materials, then monster drops. A Java-only recipe without
     * {@code mat} (noble — monster drops only) starts with its drops.
     */
    public static List<Material> craftMaterials(Obj recipe) {
        List<Material> out = new ArrayList<>();
        if (recipe.has("mat")) out.add(new Material(recipe.strOrNull("mat"), "boss", recipe.num("count")));
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

    /** The grade word of a recipe line ("영웅" · "초월" — and "고귀" for the Java-only noble recipes). */
    public static String gradeWord(String grade) {
        return switch (grade) {
            case "hero" -> "영웅";
            case "noble" -> "고귀";
            default -> "초월";
        };
    }

    /**
     * config.js craftUseLines: the "보스 무기 제작" lore lines of a material — one per recipe using it
     * ("§r§f그림자 단검 §7영웅 8"), in recipe order (BOSS_CRAFT, then CRAFT_MORE).
     */
    public static List<String> craftUseLines(String kind, String key) {
        List<String> out = new ArrayList<>();
        for (Obj r : RECIPES) {
            for (Material m : craftMaterials(r)) {
                if (m.kind().equals(kind) && Objects.equals(m.key(), key)) {
                    String grade = r.str("grade");
                    out.add("§r§f" + WEAPON_NAMES.obj(r.str("type")).text(grade) + " §7" + gradeWord(grade) + " " + Js.str(m.count()));
                    break;
                }
            }
        }
        return out;
    }
}
