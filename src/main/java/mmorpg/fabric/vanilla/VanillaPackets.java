package mmorpg.fabric.vanilla;

import mmorpg.fabric.mixin.LivingEntityAccessor;
import mmorpg.fabric.mixin.UpdateAttributesInvoker;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 모드 없는 클라이언트 한 사람에게 가는 패킷을 그 사람 화면에 맞게 바꿈 (ServerCommonPacketListenerImpl.send — VanillaSendMixin):
 * <ul>
 * <li>체력: RPG 최대 체력 (수백) 그대로면 바닐라는 하트를 여러 줄로 그림 → 자기 체력 · 최대 체력을 하트 10칸 (20) 비율로 (체력 패킷 · 자기 엔티티의 체력 데이터 ·
 * 최대 체력 속성). 서버의 체력은 그대로 — 보이는 것만</li>
 * <li>스킬 모드 핫바 (VanillaInput): 핫바 1 ~ 8 칸으로 가는 아이템을 그 모드의 스킬 그림으로 (칸 하나 · 가방 전체 — 전체는 보낸 뒤 그림을 다시 덮음)</li>
 * </ul>
 * 다른 사람에게 가는 같은 패킷 객체는 고치지 않음 (새 패킷을 만듦).
 */
public final class VanillaPackets {
    private VanillaPackets() {}

    /** 바닐라 하트 줄 하나 (10칸) 의 체력 */
    static final float HEARTS = 20F;
    /** 인벤토리 메뉴에서 핫바 첫 칸의 번호 */
    private static final int MENU_HOTBAR = 36;

    /** 보내기 전 (HEAD): 바꿀 것이면 새 패킷, 아니면 그대로 */
    public static Packet<?> rewrite(ServerPlayer sp, Packet<?> packet) {
        if (sp == null || packet == null) return packet;
        if (packet instanceof ClientboundSetHealthPacket p) {
            if (!VanillaClients.isVanilla(sp)) return packet;
            return new ClientboundSetHealthPacket(scale(sp, p.getHealth()), p.getFood(), p.getSaturation());
        }
        if (packet instanceof ClientboundSetEntityDataPacket p) {
            if (p.id() != sp.getId() || !VanillaClients.isVanilla(sp)) return packet;
            int healthId = LivingEntityAccessor.mmorpg$healthId().id();
            List<SynchedEntityData.DataValue<?>> list = p.packedItems();
            for (int i = 0; i < list.size(); i++) {
                SynchedEntityData.DataValue<?> v = list.get(i);
                if (v.id() == healthId && v.value() instanceof Float f) {
                    List<SynchedEntityData.DataValue<?>> out = new ArrayList<>(list);
                    out.set(i, new SynchedEntityData.DataValue<>(healthId, EntityDataSerializers.FLOAT, scale(sp, f)));
                    return new ClientboundSetEntityDataPacket(p.id(), out);
                }
            }
            return packet;
        }
        if (packet instanceof ClientboundUpdateAttributesPacket p) {
            if (p.getEntityId() != sp.getId() || !VanillaClients.isVanilla(sp)) return packet;
            List<ClientboundUpdateAttributesPacket.AttributeSnapshot> list = p.getValues();
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).attribute().value() != Attributes.MAX_HEALTH.value()) continue;
                List<ClientboundUpdateAttributesPacket.AttributeSnapshot> out = new ArrayList<>(list);
                out.set(i, new ClientboundUpdateAttributesPacket.AttributeSnapshot(Attributes.MAX_HEALTH, HEARTS, List.of()));
                return UpdateAttributesInvoker.mmorpg$create(p.getEntityId(), out);
            }
            return packet;
        }
        if (packet instanceof ClientboundContainerSetSlotPacket p) {
            int i = p.getSlot() - MENU_HOTBAR;
            if (p.getContainerId() != 0 || i < 0 || i >= VanillaInput.HOTBAR) return packet;
            ItemStack fake = VanillaInput.fake(sp, i);
            return fake == null ? packet : new ClientboundContainerSetSlotPacket(p.getContainerId(), p.getStateId(), p.getSlot(), fake);
        }
        if (packet instanceof ClientboundSetPlayerInventoryPacket p) {
            if (p.slot() < 0 || p.slot() >= VanillaInput.HOTBAR) return packet;
            ItemStack fake = VanillaInput.fake(sp, p.slot());
            return fake == null ? packet : new ClientboundSetPlayerInventoryPacket(p.slot(), fake);
        }
        return packet;
    }

    /** 보낸 뒤 (TAIL): 가방 전체 내용이 갔으면 스킬 모드 핫바 그림을 다시 덮음 (인벤토리 메뉴일 때만 — 상자 등 창이 열리면 VanillaInput 이 일반으로) */
    public static void after(ServerPlayer sp, Packet<?> packet) {
        if (!(packet instanceof ClientboundContainerSetContentPacket) || sp == null) return;
        if (VanillaInput.mode(sp) == VanillaInput.Mode.NORMAL || sp.containerMenu != sp.inventoryMenu) return;
        VanillaInput.sendHotbar(sp);
    }

    /** 체력 → 하트 10칸 비율 (살아 있으면 반 칸 이상) */
    static float scale(ServerPlayer sp, float health) {
        if (health <= 0) return health;
        float max = sp.getMaxHealth();
        if (max <= 0) return health;
        return Math.max(0.5F, Math.min(HEARTS, health / max * HEARTS));
    }
}
