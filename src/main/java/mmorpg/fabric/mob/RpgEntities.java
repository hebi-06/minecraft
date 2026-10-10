package mmorpg.fabric.mob;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.mixin.RangedAttributeAccessor;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

/**
 * 애드온 엔티티 26종 등록 (MobData 의 행동 팩 수치 그대로): 몬스터 6 (RpgMonster) · 보스 10 + 분신 (RpgBoss) · 소품 9 (RpgProp).
 * 크기 = 충돌 상자 (minecraft:collision_box) × 크기 속성 (minecraft:scale → 자바 SCALE 속성 — 그리기와 충돌 상자가 함께 커짐).
 * 최대 체력 상한을 1024 에서 1,000,000 으로 (보스 체력 90000 — 베드락은 상한이 없음).
 * 몬스터 소환 알 (rpg:<키>_spawn_egg — 이름 "<이름> 소환 (Lv.N)", 크리에이티브 RPG 장비 탭 · 소환 알 탭).
 */
public final class RpgEntities {
    private RpgEntities() {}

    /** 자바 최대 체력 상한 (베드락은 상한 없음) */
    public static final double MAX_HEALTH_CAP = 1_000_000;

    private static final Map<String, EntityType<? extends BedrockMob>> TYPES = new LinkedHashMap<>();
    private static final Map<String, Item> EGGS = new LinkedHashMap<>();

    /** Mmorpg.onInitialize (레지스트리가 얼기 전) */
    public static void init() {
        ((RangedAttributeAccessor) Attributes.MAX_HEALTH.value()).mmorpg$setMaxValue(MAX_HEALTH_CAP);
        for (MobData.Def d : MobData.all().values()) register(d);
        Obj monsters = Rules.get().obj("MONSTERS");
        for (String key : Rules.get().strings("MONSTER_ORDER")) {
            EntityType<? extends BedrockMob> type = TYPES.get(Mmorpg.NS + ":" + key);
            Obj m = monsters.objOrNull(key);
            if (type == null || m == null) continue;
            String name = m.str("name") + " 소환 (Lv." + (int) m.num("level") + ")";
            ResourceKey<Item> eggKey = ResourceKey.create(Registries.ITEM, Mmorpg.rpg(key + "_spawn_egg"));
            Item egg = Registry.register(BuiltInRegistries.ITEM, eggKey, new EggItem(new Item.Properties().spawnEgg(type).setId(eggKey), name));
            EGGS.put(key, egg);
        }
        CreativeModeTabEvents.modifyOutputEvent(GearItems.TAB).register(output -> EGGS.values().forEach(output::accept));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> EGGS.values().forEach(output::accept));
    }

    private static void register(MobData.Def d) {
        Identifier id = Identifier.parse(d.id());
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id);
        EntityType.Builder<? extends BedrockMob> b;
        if (d.monster()) b = EntityType.Builder.<RpgMonster>of(RpgMonster::new, MobCategory.MONSTER);
        else if (d.family("rpg_boss") || d.id().contains("decoy_")) b = EntityType.Builder.<RpgBoss>of(RpgBoss::new, MobCategory.MONSTER);
        else b = EntityType.Builder.<RpgProp>of(RpgProp::new, MobCategory.MISC);
        b.sized(Math.max(0.01F, d.width()), Math.max(0.01F, d.height())).noLootTable();
        if (d.monster()) b.clientTrackingRange(8);
        else b.clientTrackingRange(10).updateInterval(1); // 보스 · 소품은 스크립트가 틱마다 옮기기도 함 (헤엄 · 날기 · 촉수)
        if (d.fireImmune()) b.fireImmune();
        EntityType<? extends BedrockMob> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, b.build(key));
        FabricDefaultAttributeRegistry.register(type, attributes(d));
        TYPES.put(d.id(), type);
    }

    /** 기본 속성 (최대 체력 · 이동 · 따라오는 거리 · 밀치기 저항 · 크기) */
    static AttributeSupplier.Builder attributes(MobData.Def d) {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, Math.min(MAX_HEALTH_CAP, Math.max(1, d.health())))
                .add(Attributes.MOVEMENT_SPEED, d.movement())
                .add(Attributes.FOLLOW_RANGE, d.follow())
                .add(Attributes.KNOCKBACK_RESISTANCE, d.knockback())
                .add(Attributes.ATTACK_DAMAGE, 1)
                .add(Attributes.SCALE, Math.max(0.0625, Math.min(16, d.scale())));
    }

    /** ID (rpg:troll) → 엔티티 종류 (없으면 null) */
    public static EntityType<? extends BedrockMob> type(String id) {
        return TYPES.get(id.indexOf(':') < 0 ? Mmorpg.NS + ":" + id : id);
    }

    /** 모든 종류 (ID → 종류) */
    public static Map<String, EntityType<? extends BedrockMob>> types() {
        return Collections.unmodifiableMap(TYPES);
    }

    /** 몬스터 소환 알 (키 → 아이템) */
    public static Map<String, Item> eggs() {
        return Collections.unmodifiableMap(EGGS);
    }

    /** 소환 알 (이름은 애드온 그대로) */
    static final class EggItem extends SpawnEggItem {
        private final String name;

        EggItem(Item.Properties properties, String name) {
            super(properties);
            this.name = name;
        }

        @Override
        public Component getName(ItemStack stack) {
            return Component.literal(name);
        }
    }
}
