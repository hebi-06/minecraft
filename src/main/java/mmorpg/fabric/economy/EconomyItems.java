package mmorpg.fabric.economy;

import mmorpg.core.Economy;
import mmorpg.fabric.Mmorpg;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/** 0.21.0 금화 · 은화 아이템 등록 (RpgItems.init 에서 — 그림 tools/coin_art.py) */
public final class EconomyItems {
    private EconomyItems() {}

    public static CoinItem GOLD_COIN;
    public static CoinItem SILVER_COIN;

    public static void init() {
        GOLD_COIN = register("gold_coin", Economy.Kind.GOLD);
        SILVER_COIN = register("silver_coin", Economy.Kind.SILVER);
    }

    private static CoinItem register(String path, Economy.Kind kind) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Mmorpg.rpg(path));
        return Registry.register(BuiltInRegistries.ITEM, key, new CoinItem(kind, new Item.Properties().stacksTo(64).setId(key)));
    }

    /** 화폐의 동전 아이템 */
    public static CoinItem coin(Economy.Kind kind) {
        return kind == Economy.Kind.GOLD ? GOLD_COIN : SILVER_COIN;
    }
}
