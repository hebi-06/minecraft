package mmorpg.fabric.economy;

import mmorpg.fabric.Mmorpg;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** 0.21.0 화폐 패킷: 지갑 (골드 · 실버) 을 클라이언트에 — 화면 왼쪽 아래 지갑 · 장식 창 · 스탯 트리 창이 씀 */
public final class EconomyNet {
    private EconomyNet() {}

    /** 서버 → 클라이언트: 지갑의 골드 · 실버 (가방의 동전은 클라이언트가 직접 셈) */
    public record WalletPayload(long gold, long silver) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WalletPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("wallet_sync"));
        public static final StreamCodec<FriendlyByteBuf, WalletPayload> CODEC = CustomPacketPayload.codec(WalletPayload::write, WalletPayload::read);

        private static WalletPayload read(FriendlyByteBuf buf) {
            return new WalletPayload(buf.readVarLong(), buf.readVarLong());
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeVarLong(gold);
            buf.writeVarLong(silver);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void init() {
        PayloadTypeRegistry.clientboundPlay().register(WalletPayload.TYPE, WalletPayload.CODEC);
    }

    /** 지갑 보내기 (모드가 있는 클라이언트만) */
    static void send(ServerPlayer sp, long gold, long silver) {
        if (ServerPlayNetworking.canSend(sp, WalletPayload.TYPE)) ServerPlayNetworking.send(sp, new WalletPayload(gold, silver));
    }
}
