package mmorpg.fabric.cosmetic;

import mmorpg.fabric.Mmorpg;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 장식 패킷 (0.13.0): 서버 → 클라이언트 내 장식 (가진 것 · 모습 · 창 열기) · 모두의 모습 (보이는 사람마다 그리기),
 * 클라이언트 → 서버 장식 창의 일 (사기 · 입기 · 벗기 · 숨기기). 모드가 없는 클라이언트에는 보내지 않음 (canSend).
 */
public final class CosmeticNet {
    private CosmeticNet() {}

    /** 서버 → 클라이언트: 내 장식 JSON {"owned": [키…], "look": 모습, "open": 열 탭 ("" = 열지 않음), "note": 알림 줄} */
    public record StatePayload(String json) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StatePayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("cosmetic_state"));
        public static final StreamCodec<FriendlyByteBuf, StatePayload> CODEC = CustomPacketPayload.codec(StatePayload::write, StatePayload::read);

        private static StatePayload read(FriendlyByteBuf buf) {
            return new StatePayload(buf.readUtf(32767));
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeUtf(json, 32767);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 서버 → 클라이언트: 모습들 JSON {"&lt;UUID&gt;": 모습 또는 null (나감)} — full 이면 이것으로 모두 바꿈 (들어올 때) */
    public record LooksPayload(boolean full, String json) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<LooksPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("cosmetic_looks"));
        public static final StreamCodec<FriendlyByteBuf, LooksPayload> CODEC = CustomPacketPayload.codec(LooksPayload::write, LooksPayload::read);

        private static LooksPayload read(FriendlyByteBuf buf) {
            return new LooksPayload(buf.readBoolean(), buf.readUtf(262144));
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeBoolean(full);
            buf.writeUtf(json, 262144);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 클라이언트 → 서버: 장식 창의 일. action = buy (a = 키) · equip (a = 키) · clear (a = 칸, b = 무기 종류 — 무기 칸) · hide (a = 갑옷 칸) ·
     * hide_all · show_all (갑옷 넷) · sync
     */
    public record ActionPayload(String action, String a, String b) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ActionPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("cosmetic_action"));
        public static final StreamCodec<FriendlyByteBuf, ActionPayload> CODEC = CustomPacketPayload.codec(ActionPayload::write, ActionPayload::read);

        private static ActionPayload read(FriendlyByteBuf buf) {
            return new ActionPayload(buf.readUtf(16), buf.readUtf(64), buf.readUtf(32));
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeUtf(action, 16);
            buf.writeUtf(a, 64);
            buf.writeUtf(b, 32);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Cosmetics.init */
    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(LooksPayload.TYPE, LooksPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE, (p, context) -> Cosmetics.action(context.player(), p.action(), p.a(), p.b()));
    }

    public static boolean canSend(ServerPlayer sp) {
        return sp != null && ServerPlayNetworking.canSend(sp, StatePayload.TYPE);
    }

    static void state(ServerPlayer sp, String json) {
        if (canSend(sp)) ServerPlayNetworking.send(sp, new StatePayload(json));
    }

    static void looks(ServerPlayer sp, boolean full, String json) {
        if (sp != null && ServerPlayNetworking.canSend(sp, LooksPayload.TYPE)) ServerPlayNetworking.send(sp, new LooksPayload(full, json));
    }
}
