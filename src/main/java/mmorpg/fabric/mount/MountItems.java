package mmorpg.fabric.mount;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.gear.GearItems;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * 탈것 아이템 등록 (mounts.json 의 item — rpg:mount_&lt;키&gt;, 크리에이티브 RPG 장비 탭). 그림은 assets/rpg/textures/item/mount/&lt;키&gt;.png
 * (tools/mount_items.py 가 탈것 초상에서 만듦), 모델 · 아이템 정의는 assets/rpg/models/item · items 의 mount_&lt;키&gt;.json.
 */
public final class MountItems {
    private MountItems() {}

    private static final Map<String, MountItem> ITEMS = new LinkedHashMap<>();

    /** Mmorpg.onInitialize (레지스트리가 얼기 전) */
    public static void init() {
        for (MountDefs.Def d : MountDefs.all().values()) {
            if (d.item().isEmpty()) continue;
            Identifier id = Identifier.tryParse(d.item());
            if (id == null || !id.getNamespace().equals(Mmorpg.NS) || BuiltInRegistries.ITEM.containsKey(id)) continue;
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
            MountItem item = Registry.register(BuiltInRegistries.ITEM, key, new MountItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).setId(key), d.key()));
            ITEMS.put(d.key(), item);
        }
        CreativeModeTabEvents.modifyOutputEvent(GearItems.TAB).register(output -> ITEMS.values().forEach(output::accept));
    }

    /** 탈것 키 → 아이템 (없으면 null) */
    public static MountItem of(String key) {
        return ITEMS.get(key);
    }

    public static Map<String, MountItem> all() {
        return Collections.unmodifiableMap(ITEMS);
    }
}
