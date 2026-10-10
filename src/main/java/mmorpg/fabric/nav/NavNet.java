package mmorpg.fabric.nav;

import mmorpg.fabric.Mmorpg;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 길 안내 화면 칸 패킷 (서버 → 클라이언트). 애드온은 제목 채널에 "rpgnav:" + 테두리 1글자 + 그림 3글자 + 글자를 숨겨 보냈고 (hp.js setHudExtra)
 * 화살표 방향 · 거리도 서버가 계산했지만, 자바는 목적지 자리를 보내고 화살표 방향 · 거리 · 높이 글자는 클라이언트 (NavHud) 가 매 프레임 내 위치 ·
 * 보는 방향으로 계산 (서버 왕복 없이 바로 바뀜 — 같은 규칙 NavMath). 서버는 목적지 · 도착 · 보이기 · 글자가 바뀔 때만 보냄.
 */
public final class NavNet {
    private NavNet() {}

    /**
     * 화면 칸 하나. visible = 보임 (false = 숨김: 갈 곳 없음 · 꺼짐 · 보스 필드 근처), frame = 테두리 1 메인 금빛 · 2 일반 하늘빛,
     * mode = NONE (그림 없음 — 위치 모름 · 다른 차원 · NPC 가 없음 · 사냥터 없음) · ARROW (화살표 — 클라이언트가 방향 · 거리를 계산) · HERE (도착 그림),
     * key = 목적지 (바뀌면 화살표 경계 머무름을 새로), dim · x · y · z = 목적지 차원 · 자리, label = 이름 글자 (자바 서식), tail = 뒤에 붙는 글자 (NONE · HERE —
     * ARROW 는 클라이언트가 거리 · 높이)
     */
    public record NavPayload(boolean visible, byte frame, byte mode, String key, String dim, double x, double y, double z, String label, String tail)
            implements CustomPacketPayload {
        public static final byte NONE = 0;
        public static final byte ARROW = 1;
        public static final byte HERE = 2;
        /** 숨김 (애드온 "rpgnav:0non") */
        public static final NavPayload HIDDEN = new NavPayload(false, (byte) 0, NONE, "", "", 0, 0, 0, "", "");

        public static final CustomPacketPayload.Type<NavPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("nav"));
        public static final StreamCodec<FriendlyByteBuf, NavPayload> CODEC = CustomPacketPayload.codec(NavPayload::write, NavPayload::read);

        private static NavPayload read(FriendlyByteBuf buf) {
            return new NavPayload(buf.readBoolean(), buf.readByte(), buf.readByte(), buf.readUtf(), buf.readUtf(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readUtf(), buf.readUtf());
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeBoolean(visible);
            buf.writeByte(frame);
            buf.writeByte(mode);
            buf.writeUtf(key);
            buf.writeUtf(dim);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeUtf(label);
            buf.writeUtf(tail);
        }

        /** 화면에 보이는 글자 (화살표일 때는 거리 · 높이 글자를 뺀 것) */
        public String text() {
            return label + tail;
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(NavPayload.TYPE, NavPayload.CODEC);
    }

    /** 보냄 (모드가 있는 클라이언트만). 보냈으면 true */
    static boolean send(ServerPlayer sp, NavPayload p) {
        if (!ServerPlayNetworking.canSend(sp, NavPayload.TYPE)) return false;
        ServerPlayNetworking.send(sp, p);
        return true;
    }
}
