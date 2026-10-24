package mmorpg.fabric.artifact;

import java.util.ArrayList;
import java.util.List;
import mmorpg.fabric.Mmorpg;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 0.37.0 아티팩트 패킷: 서버 → 클라이언트 칸 다섯 (open = 아티팩트 창을 엶), 클라이언트 → 서버 아티팩트 창의 일 (equip · unequip · move · sync).
 * 모드가 없는 클라이언트에는 보내지 않음 (canSend).
 */
public final class ArtifactNet {
    private ArtifactNet() {}

    /** 서버 → 클라이언트: 칸들 (늘 ArtifactSystem.SIZE 개 — 빈 칸은 EMPTY) · 창 열기 */
    public record SlotsPayload(List<ItemStack> slots, boolean open) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SlotsPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("artifacts"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotsPayload> CODEC = CustomPacketPayload.codec(SlotsPayload::write, SlotsPayload::read);

        private static SlotsPayload read(RegistryFriendlyByteBuf buf) {
            int n = Math.min(16, buf.readVarInt());
            List<ItemStack> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
            return new SlotsPayload(List.copyOf(list), buf.readBoolean());
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(slots.size());
            for (ItemStack s : slots) ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s);
            buf.writeBoolean(open);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 클라이언트 → 서버: action = equip (a = 가방 칸 0 ~ 35 · 왼손 40, b = 아티팩트 칸 0 ~ 4 · -1 = 맞는 칸 저절로) · unequip (a = 칸) ·
     * move (a ↔ b — 반지 1 ↔ 반지 2) · sync
     */
    public record ActionPayload(String action, int a, int b) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ActionPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("artifact_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC = CustomPacketPayload.codec(ActionPayload::write, ActionPayload::read);

        private static ActionPayload read(RegistryFriendlyByteBuf buf) {
            return new ActionPayload(buf.readUtf(16), buf.readVarInt(), buf.readVarInt());
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(action, 16);
            buf.writeVarInt(a);
            buf.writeVarInt(b);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** ArtifactSystem.init */
    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(SlotsPayload.TYPE, SlotsPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE, (p, context) -> ArtifactSystem.action(context.player(), p.action(), p.a(), p.b()));
    }

    /** 모드 클라이언트인지 (패킷을 받을 수 있는지) */
    public static boolean canSend(ServerPlayer sp) {
        return sp != null && !sp.isRemoved() && ServerPlayNetworking.canSend(sp, SlotsPayload.TYPE);
    }

    /** 칸들을 클라이언트에 (open = 아티팩트 창을 엶) */
    public static void send(ServerPlayer sp, boolean open) {
        if (!canSend(sp)) return;
        List<ItemStack> list = new ArrayList<>(ArtifactSystem.SIZE);
        for (ItemStack s : ArtifactSystem.slots(sp)) list.add(s.copy());
        ServerPlayNetworking.send(sp, new SlotsPayload(list, open));
    }
}
