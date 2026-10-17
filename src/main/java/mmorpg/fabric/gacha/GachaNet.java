package mmorpg.fabric.gacha;

import mmorpg.fabric.Mmorpg;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 룬 뽑기 패킷 (0.14.0): 서버 → 클라이언트 뽑기 상태 (뽑기 종류 · 천장 · 한정 뽑기 · 창 열기) · 뽑은 결과 (연출),
 * 클라이언트 → 서버 뽑기 창의 일 (뽑기 · 연출 끝 · 기록 · 관리자 설정). 모드가 없는 클라이언트에는 보내지 않음 (canSend — 뽑기권 우클릭은 예전처럼 눈앞 마법진).
 */
public final class GachaNet {
    private GachaNet() {}

    /** 서버 → 클라이언트: 상태 JSON (GachaSystem.stateJson — banners · counters · limited · admin · open · note) */
    public record StatePayload(String json) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StatePayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("gacha_state"));
        public static final StreamCodec<FriendlyByteBuf, StatePayload> CODEC = CustomPacketPayload.codec(StatePayload::write, StatePayload::read);

        private static StatePayload read(FriendlyByteBuf buf) {
            return new StatePayload(buf.readUtf(262144));
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeUtf(json, 262144);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 서버 → 클라이언트: 뽑은 결과 JSON {"id": 번호, "banner": 키, "pulls": [{"rune", "grade", "family", "featured", "lost", "at", "new"}…], "counter": …} */
    public record ResultPayload(String json) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ResultPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("gacha_result"));
        public static final StreamCodec<FriendlyByteBuf, ResultPayload> CODEC = CustomPacketPayload.codec(ResultPayload::write, ResultPayload::read);

        private static ResultPayload read(FriendlyByteBuf buf) {
            return new ResultPayload(buf.readUtf(65536));
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeUtf(json, 65536);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 클라이언트 → 서버: 뽑기 창의 일. action = open (a = 뽑기 키 — 상태를 다시) · pull (a = 뽑기 키, b = 횟수 1 · 10) · revealed (a = 결과 번호 — 연출에서 결과가 보임) ·
     * history · limited_save (a = 한정 뽑기 설정 JSON — 관리자) · limited_close (관리자)
     */
    public record ActionPayload(String action, String a, String b) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ActionPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("gacha_action"));
        public static final StreamCodec<FriendlyByteBuf, ActionPayload> CODEC = CustomPacketPayload.codec(ActionPayload::write, ActionPayload::read);

        private static ActionPayload read(FriendlyByteBuf buf) {
            return new ActionPayload(buf.readUtf(24), buf.readUtf(4096), buf.readUtf(32));
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeUtf(action, 24);
            buf.writeUtf(a, 4096);
            buf.writeUtf(b, 32);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** GachaSystem.init */
    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(StatePayload.TYPE, StatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ResultPayload.TYPE, ResultPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE, (p, context) -> GachaSystem.action(context.player(), p.action(), p.a(), p.b()));
    }

    /** 모드가 있는 클라이언트인지 (뽑기 창을 열 수 있는지) */
    public static boolean canSend(ServerPlayer sp) {
        return sp != null && ServerPlayNetworking.canSend(sp, StatePayload.TYPE);
    }

    static void state(ServerPlayer sp, String json) {
        if (canSend(sp)) ServerPlayNetworking.send(sp, new StatePayload(json));
    }

    static void result(ServerPlayer sp, String json) {
        if (sp != null && ServerPlayNetworking.canSend(sp, ResultPayload.TYPE)) ServerPlayNetworking.send(sp, new ResultPayload(json));
    }
}
