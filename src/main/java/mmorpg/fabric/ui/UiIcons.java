package mmorpg.fabric.ui;

import java.util.Map;
import mmorpg.fabric.Mmorpg;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 2026-11-16 바닐라 아이템 그림 금지 (사용자 "바닐라 아이템은 다 빼버려 다 빼고 너가 직접 그려서 다른 걸로 넣어"): 창 칸 · 선택지 · 모드 화면에 쓰던 바닐라 아이템 그림을
 * 직접 그린 아이콘 (tools/ui_icons.py — assets/rpg/items/icon/&lt;이름&gt;) 으로 바꿈. 종이 아이템에 item_model rpg:icon/&lt;이름&gt; (이름 · 설명 등 구성 요소는 그대로).
 * {@link Icon#item(ItemStack)} 이 모든 칸을 이리로 보냄 → Slot.of(Items.X …) · Icon.of("textures/items/…") · Icon.item("minecraft:…") 모두.
 */
public final class UiIcons {
    private UiIcons() {}

    /** 바닐라 아이템 이름 → 그린 아이콘 (없으면 unknown) */
    private static final Map<String, String> MAP = Map.ofEntries(
            Map.entry("nether_star", "breakstar"),
            Map.entry("experience_bottle", "level_badge"),
            Map.entry("player_head", "person"),
            Map.entry("clock", "hourglass"),
            Map.entry("hopper", "sort"),
            Map.entry("compass", "compass_rose"),
            Map.entry("book", "guide_book"),
            Map.entry("enchanted_book", "spell_tome"),
            Map.entry("writable_book", "quill_scroll"),
            Map.entry("name_tag", "price_tag"),
            Map.entry("chest", "crate"),
            Map.entry("barrel", "strongbox"),
            Map.entry("ender_chest", "strongbox"),
            Map.entry("barrier", "deny"),
            Map.entry("anvil", "smith_hammer"),
            Map.entry("iron_sword", "sword_emblem"),
            Map.entry("iron_chestplate", "armor_emblem"),
            Map.entry("bundle", "loot_sack"),
            Map.entry("emerald", "gem"),
            Map.entry("iron_ingot", "metal_bar"),
            Map.entry("target", "crosshair"),
            Map.entry("mace", "crit_burst"),
            Map.entry("golden_apple", "life_fruit"),
            Map.entry("shield", "shield_emblem"),
            Map.entry("feather", "wind_feather"),
            Map.entry("oak_sapling", "sprout"),
            Map.entry("lava_bucket", "trash"));

    /** 그린 아이콘 하나 (종이 + item_model rpg:icon/name) */
    public static ItemStack of(String name) {
        ItemStack s = new ItemStack(Items.PAPER);
        s.set(DataComponents.ITEM_MODEL, Mmorpg.rpg("icon/" + name));
        return s;
    }

    /** 이 칸이 그린 아이콘 name 인지 (종이 + item_model rpg:icon/name — 시험 · 칸 구분용) */
    public static boolean is(ItemStack s, String name) {
        return s != null && s.is(Items.PAPER) && Mmorpg.rpg("icon/" + name).equals(s.get(DataComponents.ITEM_MODEL));
    }

    /** 빈 칸 (그림 없음 — 맨 종이 그림 대신) */
    public static ItemStack blank() {
        return of("blank");
    }

    /**
     * 바닐라 아이템이면 그린 아이콘으로 (구성 요소 — 이름 · 설명 · 수 — 는 그대로, 아이템은 종이: 바닐라 아이템의 반짝임 같은 기본값이 따라오지 않게).
     * 우리 아이템 (rpg 등) · item_model 을 단 종이 · 막대 (모델을 나르는 그릇) 는 그대로
     */
    public static ItemStack devanilla(ItemStack s) {
        if (s == null || s.isEmpty()) return s;
        Identifier id = BuiltInRegistries.ITEM.getKey(s.getItem());
        if (id == null || !"minecraft".equals(id.getNamespace())) return s;
        if ((s.is(Items.PAPER) || s.is(Items.STICK)) && s.has(DataComponents.ITEM_MODEL)) return s;
        ItemStack out = s.transmuteCopy(Items.PAPER);
        out.set(DataComponents.ITEM_MODEL, Mmorpg.rpg("icon/" + MAP.getOrDefault(id.getPath(), "unknown")));
        return out;
    }
}
