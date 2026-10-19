package mmorpg.fabric.storage;

import java.util.ArrayList;
import java.util.List;
import mmorpg.fabric.Mmorpg;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 0.22.0 엔더 창고 패킷: 서버 → 클라이언트 창고 상태 (열린 창 번호 · 지금 상자 · 열린 상자 수 · 상자마다 값 · 든 묶음 수 — 상자 창 왼쪽의 상자 탭),
 * 클라이언트 → 서버 탭 누르기 (page = 그 상자로 · buy = 다음 상자 사기).
 */
public final class StorageNet {
    private StorageNet() {}

    /** 서버 → 클라이언트: 창고 상태. prices 는 상자마다 "" (기본 · 이미 열림 상관없이 값이 없는 상자) 또는 "silver:300" · "gold:30" */
    public record StatePayload(int containerId, int page, int unlocked, int chests, List<String> prices, List<Integer> stacks) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StatePayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("storage_state"));
        public static final StreamCodec<FriendlyByteBuf, StatePayload> CODEC = CustomPacketPayload.codec(StatePayload::write, StatePayload::read);

        private static StatePayload read(FriendlyByteBuf buf) {
            int id = buf.readVarInt();
            int page = buf.readVarInt();
            int unlocked = buf.readVarInt();
            int chests = Math.min(StorageData.MAX_PAGES, buf.readVarInt());
            List<String> prices = new ArrayList<>(chests);
            List<Integer> stacks = new ArrayList<>(chests);
            for (int i = 0; i < chests; i++) {
                prices.add(buf.readUtf(32));
                stacks.add(buf.readVarInt());
            }
            return new StatePayload(id, page, unlocked, chests, List.copyOf(prices), List.copyOf(stacks));
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(containerId);
            buf.writeVarInt(page);
            buf.writeVarInt(unlocked);
            buf.writeVarInt(chests);
            for (int i = 0; i < chests; i++) {
                buf.writeUtf(i < prices.size() ? prices.get(i) : "", 32);
                buf.writeVarInt(i < stacks.size() ? stacks.get(i) : 0);
            }
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 클라이언트 → 서버: action "page" (그 상자로 — page 0부터) · "buy" (다음 상자 사기) */
    public record ActionPayload(String action, int containerId, int page) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ActionPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("storage_action"));
        public static final StreamCodec<FriendlyByteBuf, ActionPayload> CODEC = CustomPacketPayload.codec(ActionPayload::write, ActionPayload::read);

        private static ActionPayload read(FriendlyByteBuf buf) {
            return new ActionPayload(buf.readUtf(16), buf.readVarInt(), buf.readVarInt());
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeUtf(action, 16);
            buf.writeVarInt(containerId);
            buf.writeVarInt(page);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE, (p, ctx) -> StorageWindow.onAction(ctx.player(), p));
    }

    /** 모드가 있는 클라이언트인지 (상자 탭을 그릴 수 있는지) */
    public static boolean canShow(ServerPlayer sp) {
        return ServerPlayNetworking.canSend(sp, StatePayload.TYPE);
    }

    static void send(ServerPlayer sp, StatePayload p) {
        if (canShow(sp)) ServerPlayNetworking.send(sp, p);
    }
}
