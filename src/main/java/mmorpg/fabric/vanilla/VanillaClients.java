package mmorpg.fabric.vanilla;

import mmorpg.fabric.Mmorpg;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 모드 클라이언트 / 모드 없는 클라이언트 가르기 (2026-11-07 1번 — 모드 없이 접속).
 *
 * <p>모드 클라이언트는 설정 단계 채널 {@code mmorpg:hello} 를 받는다고 알림 (MmorpgClient 가 받는 곳을 등록 — 서버는 보내지 않음).
 * 설정 단계 (CONFIGURE) 에 그 채널이 있는지 보고 연결의 PacketContext 에 적어 둠 → 아이템 · 엔티티 · 블록 · 파티클을 보낼 때
 * (Polymer 가 연결마다 부름) 모드 클라이언트에는 그대로, 모드 없는 클라이언트에는 바닐라 것으로.
 *
 * <p>표시가 없는 연결 (게임 시험의 가짜 플레이어 · 설정을 거치지 않은 연결) 은 모드 클라이언트로 봄 — 지금과 같은 것을 보냄.
 */
public final class VanillaClients {
    private VanillaClients() {}

    public static final PacketContext.Key<Boolean> MODDED = PacketContext.key(Mmorpg.id("modded"));

    /** 모드 클라이언트가 받는다고 알리는 설정 단계 채널 (내용 없음 — 서버는 보내지 않음) */
    public record HelloPayload() implements CustomPacketPayload {
        public static final Type<HelloPayload> TYPE = new Type<>(Mmorpg.id("hello"));
        public static final StreamCodec<FriendlyByteBuf, HelloPayload> CODEC = StreamCodec.unit(new HelloPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void init() {
        PayloadTypeRegistry.clientboundConfiguration().register(HelloPayload.TYPE, HelloPayload.CODEC);
        ServerConfigurationConnectionEvents.CONFIGURE.register((listener, server) -> {
            boolean modded = ServerConfigurationNetworking.canSend(listener, HelloPayload.TYPE);
            listener.getPacketContext().set(MODDED, modded);
            if (!modded) {
                var profile = listener.getPacketContext().get(PacketContext.GAME_PROFILE);
                Mmorpg.LOG.info("[vanilla] {} 는 모드 없는 클라이언트 — 바닐라 것으로 보냄", profile != null ? profile.name() : "?");
            }
        });
    }

    /** 이 연결 (보낼 곳) 이 모드 클라이언트인지 — 표시가 없으면 true (지금과 같은 것을 보냄) */
    public static boolean isModded(PacketContext ctx) {
        if (ctx == null) return true;
        Boolean modded = ctx.get(MODDED);
        return modded == null || modded;
    }

    public static boolean isModded(ServerPlayer player) {
        return player == null || isModded(player.getPacketContext());
    }

    /** 모드 없는 클라이언트로 접속한 플레이어 */
    public static boolean isVanilla(ServerPlayer player) {
        return !isModded(player);
    }
}
