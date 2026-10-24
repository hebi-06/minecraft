package mmorpg.fabric.motion;

import mmorpg.fabric.Mmorpg;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 0.38.0 동작 패킷.
 * <ul>
 *   <li>클라이언트 → 서버 {@link ReqPayload}: kind = basic (평타 — step = 연타 단계 1 ~, 공격 패킷보다 먼저 옴) · roll (구르기 — yaw = 구르는 쪽)</li>
 *   <li>서버 → 클라이언트 {@link PlayPayload}: 그 사람 (entity) 이 동작 (motion — assets/mmorpg/motion/poses.json 이름) 을 시작 (yaw = 구르기 쪽)</li>
 * </ul>
 * 모드가 없는 클라이언트에는 보내지 않음 (canSend) — 그 사람의 평타 단계는 서버가 셈.
 */
public final class MotionNet {
    private MotionNet() {}

    public record ReqPayload(String kind, int step, float yaw) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ReqPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("motion_req"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ReqPayload> CODEC = CustomPacketPayload.codec(ReqPayload::write, ReqPayload::read);

        private static ReqPayload read(RegistryFriendlyByteBuf buf) {
            return new ReqPayload(buf.readUtf(16), buf.readVarInt(), buf.readFloat());
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(kind, 16);
            buf.writeVarInt(step);
            buf.writeFloat(yaw);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record PlayPayload(int entity, String motion, float yaw) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<PlayPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("motion"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PlayPayload> CODEC = CustomPacketPayload.codec(PlayPayload::write, PlayPayload::read);

        private static PlayPayload read(RegistryFriendlyByteBuf buf) {
            return new PlayPayload(buf.readVarInt(), buf.readUtf(32), buf.readFloat());
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(entity);
            buf.writeUtf(motion, 32);
            buf.writeFloat(yaw);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** MotionSystem.init */
    static void register() {
        PayloadTypeRegistry.serverboundPlay().register(ReqPayload.TYPE, ReqPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PlayPayload.TYPE, PlayPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ReqPayload.TYPE, (p, context) -> MotionSystem.request(context.player(), p.kind(), p.step(), p.yaw()));
    }

    /** 모드 클라이언트인지 (동작을 받을 수 있는지) */
    public static boolean canSend(ServerPlayer sp) {
        return sp != null && !sp.isRemoved() && ServerPlayNetworking.canSend(sp, PlayPayload.TYPE);
    }

    static void send(ServerPlayer to, PlayPayload p) {
        if (canSend(to)) ServerPlayNetworking.send(to, p);
    }
}
