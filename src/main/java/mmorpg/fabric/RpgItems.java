package mmorpg.fabric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mmorpg.core.Grade;
import mmorpg.core.Rules;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 아이템 등록: 무기 (지금 규칙으로 얻을 수 있는 종류 × 등급 × 레벨 — 옛 버전 조합은 뺌) + 스탯 책 (크리에이티브 탭 하나), 룬 (RuneItems — 탭 "룬") */
public final class RpgItems {
    private RpgItems() {}

    /** 등록한 무기 (TYPE_ORDER → 등급 → 레벨 순, 마지막에 제작 전용 TYPE_ONLY — 마검 · 12단계 창 · 낫 · 전투 망치 · 성스러운 홀) */
    public static final List<RpgWeaponItem> WEAPONS = new ArrayList<>();
    /** 무기 ID (rpg:sword_l10_rare) → 아이템 */
    private static final Map<String, RpgWeaponItem> BY_ID = new HashMap<>();
    public static Item STAT_BOOK;
    public static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Mmorpg.id("main"));

    private static ResourceKey<Item> key(String path) {
        return ResourceKey.create(Registries.ITEM, Mmorpg.rpg(path));
    }

    static void init() {
        Rules r = Rules.get();
        List<String> types = Weapons.allTypes(); // TYPE_ORDER 여섯 → 제작 전용 (마검 · 12단계 새 무기 넷)
        List<Double> levels = r.numbers("ALL_LEVELS");
        for (String type : types) {
            for (Grade g : r.grades()) {
                for (double lv : levels) {
                    if (!Weapons.isValidWeapon(lv, g.id(), type)) continue;
                    Weapon w = Weapons.weaponFrom(type, lv, g.id());
                    ResourceKey<Item> key = key(w.id().substring((Mmorpg.NS + ":").length()));
                    RpgWeaponItem item = Registry.register(BuiltInRegistries.ITEM, key, new RpgWeaponItem(w, RpgWeaponItem.properties(w).setId(key)));
                    WEAPONS.add(item);
                    BY_ID.put(w.id(), item);
                }
            }
        }
        ResourceKey<Item> bookKey = key("stat_book");
        STAT_BOOK = Registry.register(BuiltInRegistries.ITEM, bookKey, new StatBookItem(new Item.Properties().stacksTo(1).setId(bookKey)));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.mmorpg.main"))
                .icon(() -> new ItemStack(WEAPONS.isEmpty() ? STAT_BOOK : WEAPONS.get(WEAPONS.size() - 1)))
                .displayItems((params, output) -> {
                    output.accept(STAT_BOOK);
                    for (RpgWeaponItem w : WEAPONS) output.accept(w);
                })
                .build());
        mmorpg.fabric.rune.RuneItems.init(); // 룬 80 · 뽑기권 (탭 "룬")
        mmorpg.fabric.gear.GearItems.init(); // 갑옷 120 · 재료 (탭 "RPG 장비 · 재료")
    }

    public static List<RpgWeaponItem> weapons() {
        return Collections.unmodifiableList(WEAPONS);
    }

    /** 무기 ID 의 아이템 (없는 조합이면 null) */
    public static RpgWeaponItem byId(String id) {
        return BY_ID.get(id);
    }
}
