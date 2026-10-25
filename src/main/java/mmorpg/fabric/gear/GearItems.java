package mmorpg.fabric.gear;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import mmorpg.core.Armor;
import mmorpg.core.Crafting;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.Mmorpg;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * 갑옷 240 (armor.js — 종류 10 (애드온 5 + 0.12.5 두 스탯 무기용 5 — ARMOR_TYPE_ORDER) × 레벨 · 등급 6 × 부위 4) · 재료 (레이드 재료 4 · 보스 재료 12 · 몬스터 전리품 6 · 룬 조각) · NPC 설정 도구 ·
 * 0.37.0 아티팩트 20 ({@link ArtifactItem}) 등록,
 * 크리에이티브 탭 "RPG 장비 · 재료". 재료 이름 · 반짝임 · 쌓임은 애드온 아이템 정의 (assets/mmorpg/materials.json — tools/armor_items.py).
 */
public final class GearItems {
    private GearItems() {}

    private static final Rules R = Rules.get();
    public static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Mmorpg.id("gear"));
    private static final List<RpgArmorItem> ARMOR = new ArrayList<>();
    private static final Map<String, Item> BY_ID = new LinkedHashMap<>();
    public static Item NPC_TOOL;

    private static ResourceKey<Item> key(String id) {
        return ResourceKey.create(Registries.ITEM, Mmorpg.rpg(id.substring((Mmorpg.NS + ":").length())));
    }

    private static EquipmentSlot slotOf(String slot) {
        return switch (slot) {
            case "Head" -> EquipmentSlot.HEAD;
            case "Chest" -> EquipmentSlot.CHEST;
            case "Legs" -> EquipmentSlot.LEGS;
            default -> EquipmentSlot.FEET;
        };
    }

    private static EquipmentSlotGroup groupOf(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> EquipmentSlotGroup.HEAD;
            case CHEST -> EquipmentSlotGroup.CHEST;
            case LEGS -> EquipmentSlotGroup.LEGS;
            default -> EquipmentSlotGroup.FEET;
        };
    }

    /** 재료 이름 표 (애드온 아이템 정의) */
    private static Map<String, JsonObject> materialTable() {
        try (InputStream in = GearItems.class.getResourceAsStream("/assets/mmorpg/materials.json")) {
            if (in == null) throw new IllegalStateException("materials.json missing");
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, JsonObject> out = new LinkedHashMap<>();
            for (String k : o.keySet()) out.put(k, o.getAsJsonObject(k));
            return out;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** RpgItems.init 이 부름 (레지스트리가 얼기 전) */
    public static void init() {
        for (String type : R.strings("ARMOR_TYPE_ORDER")) {
            Obj gradeLevels = R.obj("ARMOR_GRADE_LEVELS");
            for (String grade : gradeLevels.keys()) {
                for (double lv : gradeLevels.numbersOrNull(grade)) {
                    for (String p : Armor.PIECE_ORDER) {
                        Armor.Piece a = Armor.armorFrom(type, lv, grade, p);
                        if (a == null) continue;
                        ResourceKey<Item> k = key(a.id());
                        EquipmentSlot slot = slotOf(a.slot());
                        ItemAttributeModifiers attrs = ItemAttributeModifiers.EMPTY.withModifierAdded(Attributes.ARMOR,
                                new AttributeModifier(Mmorpg.id("armor_" + p), a.prot(), AttributeModifier.Operation.ADD_VALUE), groupOf(slot));
                        Item.Properties props = new Item.Properties().stacksTo(1).equippable(slot).attributes(attrs)
                                .component(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true)).setId(k);
                        RpgArmorItem item = Registry.register(BuiltInRegistries.ITEM, k, new RpgArmorItem(a, props));
                        ARMOR.add(item);
                        BY_ID.put(a.id(), item);
                    }
                }
            }
        }
        Map<String, JsonObject> names = materialTable();
        Obj raid = R.obj("RAID_MATERIALS");
        for (String m : R.strings("RAID_MATERIAL_ORDER")) material(names, raid.obj(m).str("item"), () -> strings(raid.obj(m).list("lore")));
        Obj boss = R.obj("BOSS_MATERIALS");
        for (String m : boss.keys()) material(names, boss.obj(m).str("item"), () -> bossLore(m));
        Obj drops = R.obj("MONSTER_DROPS");
        for (String m : R.strings("MONSTER_ORDER")) {
            if (drops.objOrNull(m) != null) material(names, drops.obj(m).str("item"), () -> dropLore(m));
        }
        Obj smith = R.obj("RUNE_SMITH");
        material(names, smith.str("shardItem"), () -> strings(smith.list("shardLore")));
        NPC_TOOL = material(names, "rpg:npc_tool", () -> List.of("§r§7NPC 를 누르면 그 NPC 설정 창", "§r§7허공을 누르면 NPC 관리 창 (운영자)"));
        // 0.37.0 아티팩트 20 (반지 · 목걸이 · 귀걸이 · 팔찌 × 일반 ~ 초월 — 그림은 tools/artifact_icons.py)
        for (mmorpg.core.Artifacts.Art art : mmorpg.core.Artifacts.all()) {
            ResourceKey<Item> k = key(art.id());
            Item.Properties props = new Item.Properties().stacksTo(1).setId(k);
            if (mmorpg.core.Artifacts.glint(art.grade())) props.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            BY_ID.put(art.id(), Registry.register(BuiltInRegistries.ITEM, k, new ArtifactItem(art, props)));
        }
        // 0.43.0 아티팩트 50 (신화 유물 열 × 일반 ~ 초월 — 그림은 tools/relic_icons.py)
        for (mmorpg.core.Relics.Relic relic : mmorpg.core.Relics.all()) {
            ResourceKey<Item> k = key(relic.id());
            Item.Properties props = new Item.Properties().stacksTo(1).setId(k);
            if (mmorpg.core.Relics.glint(relic.grade())) props.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            BY_ID.put(relic.id(), Registry.register(BuiltInRegistries.ITEM, k, new RelicItem(relic, props)));
        }
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.mmorpg.gear"))
                .icon(() -> new ItemStack(BY_ID.getOrDefault("rpg:armor_str_l40_transcend_chest", ARMOR.isEmpty() ? NPC_TOOL : ARMOR.get(0))))
                .displayItems((params, output) -> {
                    for (Item it : BY_ID.values()) output.accept(it);
                })
                .build());
    }

    private static List<String> strings(List<Object> list) {
        List<String> out = new ArrayList<>();
        for (Object o : list) out.add(String.valueOf(o));
        return out;
    }

    private static Item material(Map<String, JsonObject> names, String id, Supplier<List<String>> lore) {
        JsonObject def = names.get(id);
        if (def == null) throw new IllegalStateException("no material name for " + id);
        ResourceKey<Item> k = key(id);
        Item.Properties props = new Item.Properties().stacksTo(def.get("stack").getAsInt()).setId(k);
        if (def.get("glint").getAsBoolean()) props.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        String name = def.get("name").getAsString();
        Item item = Registry.register(BuiltInRegistries.ITEM, k, id.equals("rpg:npc_tool") ? new NpcToolItem(name, lore, props) : new MaterialItem(name, lore, props));
        BY_ID.put(id, item);
        return item;
    }

    /** 몬스터 전리품 설명 (loot.js dropLore): 몬스터 · 설명 · 상점 값 · 반복 의뢰 · 보스 무기 재료 수 */
    static List<String> dropLore(String key) {
        Obj d = R.obj("MONSTER_DROPS").obj(key);
        Obj m = R.obj("MONSTERS").obj(key);
        List<String> lines = new ArrayList<>(List.of("§r§7" + m.str("name") + " (Lv." + Js.str(m.num("level")) + ") 전리품", "§r§7" + d.str("desc"),
                "§r§7상점: 1개 " + Js.str(d.num("price")) + " " + R.obj("CONFIG").str("CURRENCY_NAME"), "§r§7잉그리드의 반복 의뢰 재료"));
        Map<String, Integer> uses = new HashMap<>();
        for (Obj r : Crafting.RECIPES) { // BOSS_CRAFT + 12단계 CRAFT_MORE (새 무기 — 고귀 제작은 전리품만)
            List<Object> ds = r.listOrNull("drops");
            if (ds == null) continue;
            for (Object o : ds) {
                if (o instanceof List<?> pair && !pair.isEmpty() && key.equals(pair.get(0))) {
                    uses.merge(r.str("grade"), 1, Integer::sum);
                    break;
                }
            }
        }
        List<String> parts = new ArrayList<>();
        if (uses.getOrDefault("noble", 0) > 0) parts.add("고귀 " + uses.get("noble"));
        if (uses.getOrDefault("hero", 0) > 0) parts.add("영웅 " + uses.get("hero"));
        if (uses.getOrDefault("transcend", 0) > 0) parts.add("초월 " + uses.get("transcend"));
        if (!parts.isEmpty()) lines.add("§r§6" + (uses.getOrDefault("noble", 0) > 0 ? "무기 제작 재료: " : "보스 무기 재료: ") + String.join(" · ", parts));
        // 0.36.0 대장장이: 초월 무기 레벨 올리기 (WEAPON_LEVEL_UP — 올린 뒤 레벨) · 공허의 갑옷 제작 (VOID_ARMOR)
        String item = d.str("item");
        Obj up = R.root().objOrNull("WEAPON_LEVEL_UP");
        if (up != null) {
            List<String> to = new ArrayList<>();
            Obj cost = up.obj("cost");
            for (String lv : cost.keys()) {
                Obj items = lv.startsWith("_") ? null : cost.obj(lv).objOrNull("items");
                if (items != null && items.has(item)) to.add(Js.str(Double.parseDouble(lv) + up.num("step")));
            }
            if (!to.isEmpty()) lines.add("§r§5초월 무기 레벨 올리기: " + String.join(" · ", to) + "렙");
        }
        Obj va = R.root().objOrNull("VOID_ARMOR");
        if (va != null) {
            int n = 0;
            for (String p : va.obj("cost").keys()) {
                Obj items = p.startsWith("_") ? null : va.obj("cost").obj(p).objOrNull("items");
                if (items != null && items.has(item)) n += (int) items.num(item);
            }
            if (n > 0) lines.add("§r§5공허의 갑옷 재료 §7(한 벌 " + n + "개)");
        }
        String art = artifactUse(item);
        if (!art.isEmpty()) lines.add(art);
        return lines;
    }

    /** 0.37.0 아티팩트 제작 재료 줄 ("§d아티팩트 재료: 고귀 10개") — 쓰지 않으면 "" */
    static String artifactUse(String itemId) {
        List<String> parts = new ArrayList<>();
        for (mmorpg.core.Grade g : R.grades()) {
            mmorpg.core.Artifacts.Cost c = mmorpg.core.Artifacts.cost(g.id());
            Integer n = c == null ? null : c.items().get(itemId);
            if (n != null && n > 0) parts.add(g.name() + " " + n + "개");
        }
        return parts.isEmpty() ? "" : "§r§d장신구 재료: " + String.join(" · ", parts) + " §7(하나에 · 대장장이)";
    }

    /**
     * 보스 재료 설명: 애드온 설명 (BOSS_MATERIALS[m].lore) 의 "대장장이 보스 무기 제작:" 줄까지 그대로, 그 아래 무기 줄은 지금 제작 목록
     * (Crafting.craftUseLines — 12단계 새 무기 포함) 으로 다시 씀. 그 줄이 없는 재료 (수르트 — 준비 중) 는 그대로
     */
    static List<String> bossLore(String key) {
        List<String> lore = strings(R.obj("BOSS_MATERIALS").obj(key).list("lore"));
        String art = artifactUse(R.obj("BOSS_MATERIALS").obj(key).str("item")); // 0.37.0 아티팩트 재료 (이미르 뼈 · 수르트 재료)
        int head = -1;
        for (int i = 0; i < lore.size(); i++) if (lore.get(i).contains("대장장이 보스 무기 제작")) head = i;
        if (head < 0) {
            if (art.isEmpty()) return lore;
            List<String> out = new ArrayList<>(lore);
            out.add(art);
            return out;
        }
        List<String> out = new ArrayList<>(lore.subList(0, head + 1));
        out.addAll(Crafting.craftUseLines("boss", key));
        if (!art.isEmpty()) out.add(art);
        return out;
    }

    public static List<RpgArmorItem> armor() {
        return Collections.unmodifiableList(ARMOR);
    }

    /** 갑옷 · 재료 ID (rpg:armor_… · rpg:star_ore …) 의 아이템 (없으면 null) */
    public static Item byId(String id) {
        return BY_ID.get(id);
    }

    /** 이 칸의 아이템이 RPG 갑옷이면 그 부위 (아니면 null — parseArmor(typeId)) */
    public static Armor.Piece armorOf(ItemStack stack) {
        return stack != null && stack.getItem() instanceof RpgArmorItem a ? a.piece() : null;
    }
}
