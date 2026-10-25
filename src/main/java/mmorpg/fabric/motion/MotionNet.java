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
 *   <li>서버 → 클라이언트 {@link PlayPayload}: 그 사람 (entity) 이 동작 (motion — assets/mmorpg/motion/poses.json 이름) 을 시작 (yaw = 구르기 쪽,
 *       0.41.0 look = 평타 강화 모습 — 평타일 때만)</li>
 *   <li>서버 → 클라이언트 {@link LookPayload} (0.41.0): 내 평타 강화 모습이 바뀜 (내 평타는 내 화면이 먼저 그리므로 미리 앎) — ticks 동안</li>
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

    /**
     * 0.41.0 평타 강화 모습 (rules_more SWING_LOOK): glow · aura = 궤적 빛 · 바깥 기운 색 0xRRGGBB (0 = 없음), scale = 참격 범위 배율 (1 = 그대로),
     * range = 범위 최소 칸 (0 = 없음 — 사신 현신 5칸)
     */
    public record Look(int glow, int aura, float scale, float range) {
        public static final Look NONE = new Look(0, 0, 1F, 0F);

        public boolean none() {
            return glow == 0 && aura == 0 && !(scale > 1F) && !(range > 0F);
        }

        /** 기본 범위 base 칸에서의 범위 배율: max(scale, range / base) */
        public double scaleAt(double base) {
            return range > 0 && base > 0 ? Math.max(scale, range / base) : scale;
        }

        static Look read(RegistryFriendlyByteBuf buf) {
            return new Look(buf.readInt(), buf.readInt(), buf.readFloat(), buf.readFloat());
        }

        void write(RegistryFriendlyByteBuf buf) {
            buf.writeInt(glow);
            buf.writeInt(aura);
            buf.writeFloat(scale);
            buf.writeFloat(range);
        }
    }

    public record PlayPayload(int entity, String motion, float yaw, Look look) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<PlayPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("motion"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PlayPayload> CODEC = CustomPacketPayload.codec(PlayPayload::write, PlayPayload::read);

        public PlayPayload(int entity, String motion, float yaw) {
            this(entity, motion, yaw, Look.NONE);
        }

        private static PlayPayload read(RegistryFriendlyByteBuf buf) {
            return new PlayPayload(buf.readVarInt(), buf.readUtf(32), buf.readFloat(), Look.read(buf));
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(entity);
            buf.writeUtf(motion, 32);
            buf.writeFloat(yaw);
            (look != null ? look : Look.NONE).write(buf);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 0.41.0 내 평타 강화 모습 (NONE = 끝남) · 남은 틱 */
    public record LookPayload(Look look, int ticks) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<LookPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("swing_look"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LookPayload> CODEC = CustomPacketPayload.codec(LookPayload::write, LookPayload::read);

        private static LookPayload read(RegistryFriendlyByteBuf buf) {
            return new LookPayload(Look.read(buf), buf.readVarInt());
        }

        private void write(RegistryFriendlyByteBuf buf) {
            (look != null ? look : Look.NONE).write(buf);
            buf.writeVarInt(Math.max(0, ticks));
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
        PayloadTypeRegistry.clientboundPlay().register(LookPayload.TYPE, LookPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ReqPayload.TYPE, (p, context) -> MotionSystem.request(context.player(), p.kind(), p.step(), p.yaw()));
    }

    /** 모드 클라이언트인지 (동작을 받을 수 있는지) */
    public static boolean canSend(ServerPlayer sp) {
        return sp != null && !sp.isRemoved() && ServerPlayNetworking.canSend(sp, PlayPayload.TYPE);
    }

    public static void send(ServerPlayer to, PlayPayload p) {
        if (canSend(to)) ServerPlayNetworking.send(to, p);
    }

    /** 0.41.0 내 평타 강화 모습을 받을 수 있는지 */
    static boolean canSendLook(ServerPlayer sp) {
        return sp != null && !sp.isRemoved() && ServerPlayNetworking.canSend(sp, LookPayload.TYPE);
    }
}
