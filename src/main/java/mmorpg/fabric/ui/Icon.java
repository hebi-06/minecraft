package mmorpg.fabric.ui;

import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * 창 칸 · 선택지의 그림: 아이템 (게임이 그리는 아이템 그림 — 무기 · 갑옷 · 룬 · 재료 · 바닐라 아이템) 또는 창 그림 (assets/mmorpg/textures/gui/sprites/ui —
 * 애드온 rpg_ui 의 pane · icon_check · q_new …). 애드온은 그림 경로 ("textures/items/emerald") 를 쓰므로 of(경로) 가 같은 그림으로 바꿔 줌.
 */
public record Icon(ItemStack item, String sprite) {
    public static final Icon NONE = new Icon(ItemStack.EMPTY, "");

    /** 바닐라 그림 이름 → 자바 아이템 이름 (다른 것만) */
    private static final Map<String, String> VANILLA = Map.ofEntries(
            Map.entry("book_enchanted", "enchanted_book"),
            Map.entry("book_normal", "book"),
            Map.entry("book_writable", "writable_book"),
            Map.entry("totem", "totem_of_undying"),
            Map.entry("apple_golden", "golden_apple"),
            Map.entry("clock_item", "clock"),
            Map.entry("compass_item", "compass"),
            Map.entry("map_filled", "filled_map"),
            Map.entry("map_empty", "map"),
            Map.entry("potion_bottle_drinkable", "potion"),
            Map.entry("potion_bottle_empty", "glass_bottle"),
            Map.entry("bed_red", "red_bed"),
            Map.entry("fireworks", "firework_rocket"),
            Map.entry("fireball", "fire_charge"),
            Map.entry("dye_powder_red", "red_dye"),
            Map.entry("bow_standby", "bow"),
            Map.entry("seeds_wheat", "wheat_seeds"),
            Map.entry("ender_eye", "ender_eye"),
            Map.entry("iron_horse_armor", "iron_horse_armor"),
            Map.entry("saddle", "saddle"));
    /** 바닐라 UI 그림 (textures/ui/…) → 창 그림 */
    private static final Map<String, String> UI = Map.of("cancel", "icon_cross", "check", "icon_check", "confirm", "icon_check", "refresh", "icon_next");

    public static Icon sprite(String name) {
        return new Icon(ItemStack.EMPTY, name == null ? "" : name);
    }

    public static Icon item(ItemStack stack) {
        return stack == null || stack.isEmpty() ? NONE : new Icon(stack.copy(), "");
    }

    public static Icon item(ItemLike item) {
        return item == null ? NONE : item(new ItemStack(item));
    }

    /** 아이템 ID ("rpg:star_ore" · "minecraft:emerald" · "emerald") 의 그림 (없으면 모르는 아이템 그림) */
    public static Icon item(String id) {
        Item it = itemById(id);
        return it == null ? sprite("item_unknown") : item(it);
    }

    /** 아이템 ID 의 아이템 (없으면 null) */
    public static Item itemById(String id) {
        if (id == null || id.isEmpty()) return null;
        Identifier rl = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return null;
        return BuiltInRegistries.ITEM.getValue(rl);
    }

    /**
     * 애드온 그림 경로 → 그림: textures/rpg_ui/x → 창 그림 x, textures/items/rpg/…/x → RPG 아이템 (loot/x → rpg:loot_x, armor/x → rpg:armor_x,
     * 그 밖 rpg:x — 무기 · 룬 · 재료 · 뽑기권), textures/items/x · textures/blocks/x → 바닐라 아이템, textures/ui/x → 비슷한 창 그림
     */
    public static Icon of(String path) {
        if (path == null || path.isEmpty()) return NONE;
        String last = path.substring(path.lastIndexOf('/') + 1);
        if (path.startsWith("textures/rpg_ui/")) return sprite(path.substring("textures/rpg_ui/".length()));
        if (path.startsWith("textures/ui/")) return sprite(UI.getOrDefault(last, "item_unknown"));
        if (path.startsWith("textures/items/rpg/")) {
            if (path.contains("/loot/")) return item("rpg:loot_" + last);
            if (path.contains("/armor/")) return item("rpg:armor_" + last);
            return item("rpg:" + last);
        }
        if (path.startsWith("textures/items/") || path.startsWith("textures/blocks/")) return item(VANILLA.getOrDefault(last, last));
        return item(path);
    }

    public boolean isEmpty() {
        return item.isEmpty() && sprite.isEmpty();
    }
}
