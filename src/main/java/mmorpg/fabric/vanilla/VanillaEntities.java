package mmorpg.fabric.vanilla;

import eu.pb4.polymer.core.api.entity.PolymerEntity;
import eu.pb4.polymer.core.api.entity.PolymerEntityUtils;
import eu.pb4.polymer.core.api.utils.PolymerSyncedObject;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.npc.Dummy;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/**
 * RPG 엔티티 (몬스터 · 보스 · 보스 소품 · 탈것 · 소환물 · 훈련용 허수아비 — 등록한 48종) 의 바닐라 대체.
 *
 * <p>모드 없는 클라이언트: 바닐라 갑옷 거치대 (키를 엔티티에 맞춘 크기) — 몬스터 · 보스 · 소품 · 소환물 · 탈것은 보이지 않게 (이름표는 지금처럼
 * 글자 표시 엔티티), 허수아비는 보이는 갑옷 거치대. 모습은 VanillaModels (뼈마다 아이템 표시 엔티티 — 이 거치대에 탐) · 맞는 칸은 상호작용 엔티티.
 * 모드 클라이언트에는 그대로 (지금 그리기 — BedrockRenderer).
 */
final class VanillaEntities {
    private VanillaEntities() {}

    /** 공유 상태 바이트 (Entity 데이터 0번) 의 보이지 않음 칸 */
    private static final byte INVISIBLE = 0x20;
    /** 살아 있는 것 (LivingEntity) 의 칸 다음 — 몹 (Mob) 깃발부터 (갑옷 거치대에서는 거치대 깃발 · 자세 칸 — 둘 다 LivingEntity 바로 다음 번호) */
    static final int MOB_DATA_FROM = net.minecraft.world.entity.decoration.ArmorStand.DATA_CLIENT_FLAGS.id();

    static void init() {
        int n = 0;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (!VanillaSupport.ours(BuiltInRegistries.ENTITY_TYPE.getKey(type))) continue;
            register(type);
            n++;
        }
        Mmorpg.LOG.info("[vanilla] entity types {}", n);
    }

    private static <T extends Entity> void register(EntityType<T> type) {
        boolean shown = type == Dummy.TYPE;
        PolymerSyncedObject<EntityType<?>> synced = new PolymerSyncedObject<>() {
            @Override
            public EntityType<?> getPolymerReplacement(EntityType<?> obj, PacketContext ctx) {
                return VanillaClients.isModded(ctx) ? obj : EntityTypes.ARMOR_STAND;
            }

            @Override
            public boolean canSyncRawToClient(PacketContext ctx) {
                return VanillaClients.isModded(ctx);
            }
        };
        PolymerEntityUtils.registerOverlay(type, synced, (T entity) -> new StandIn(entity, shown));
    }

    /** 엔티티 하나의 보내는 모습 */
    private record StandIn(Entity entity, boolean shown) implements PolymerEntity {
        @Override
        public EntityType<?> getPolymerEntityType(PacketContext ctx) {
            return VanillaClients.isModded(ctx) ? entity.getType() : EntityTypes.ARMOR_STAND;
        }

        /**
         * 모드 없는 클라이언트: 보이지 않게 (공유 상태 0번의 보이지 않음 칸). 2026-11-11: 몹 (Mob) 이 더한 칸 (15번 — 몹 깃발 · 그 뒤 BedrockMob 의 글자 칸) 은
         * 갑옷 거치대에서는 다른 뜻 (15번 = 거치대 깃발 — 1 작은 거치대 · 4 팔 · 16 표시 전용) 이라 뺌 — AI 를 끈 몹 (몹 깃발 1) 이 작은 거치대가 되어
         * 뼈가 거치대 키의 반에 타서 땅에 묻혀 있었음 (영상의 과녁 드라우그)
         */
        @Override
        public void modifyRawTrackedData(List<SynchedEntityData.DataValue<?>> data, ServerPlayer player, boolean initial) {
            if (shown || VanillaClients.isModded(player)) return;
            data.removeIf(v -> v.id() >= MOB_DATA_FROM);
            for (int i = 0; i < data.size(); i++) {
                SynchedEntityData.DataValue<?> v = data.get(i);
                if (v.id() == 0 && v.value() instanceof Byte b) {
                    data.set(i, new SynchedEntityData.DataValue<>(0, EntityDataSerializers.BYTE, (byte) (b | INVISIBLE)));
                    return;
                }
            }
            if (initial) data.add(new SynchedEntityData.DataValue<>(0, EntityDataSerializers.BYTE, INVISIBLE));
        }

        /**
         * 갑옷 거치대의 크기를 이 엔티티의 키에 맞춤 (거치대 키 1.975 × 크기 = 엔티티 키) — 탄 것 (이름표 글자 · 2단계 모델 뼈) 이 모드 클라이언트와
         * 같은 높이 (엔티티 머리 위) 에 오게. 크기가 바뀔 때 (처음 · 크기 속성이 바뀐 묶음) 만 보냄.
         */
        @Override
        public void modifyRawEntityAttributeData(List<ClientboundUpdateAttributesPacket.AttributeSnapshot> data, ServerPlayer player, boolean initial) {
            if (VanillaClients.isModded(player)) return;
            boolean had = data.removeIf(a -> a.attribute().value() == Attributes.SCALE.value());
            if (initial || had) {
                data.add(new ClientboundUpdateAttributesPacket.AttributeSnapshot(Attributes.SCALE,
                        Math.max(0.0625, entity.getBbHeight() / EntityTypes.ARMOR_STAND.getHeight()), List.of()));
            }
        }
    }

    /** 모드 없는 클라이언트에서 이 엔티티에 탄 것의 자리 (엔티티 발에서 위로 — 거치대 키 × 크기 = 엔티티 키) */
    static double riderHeight(Entity entity) {
        return Math.max(0.0625, entity.getBbHeight() / EntityTypes.ARMOR_STAND.getHeight()) * EntityTypes.ARMOR_STAND.getHeight();
    }
}
