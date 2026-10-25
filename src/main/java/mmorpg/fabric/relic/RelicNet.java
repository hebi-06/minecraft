package mmorpg.fabric.relic;

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
 * 0.43.0 아티팩트 판 패킷: 서버 → 클라이언트 판 (칸마다 아이템 · 고른 스탯 줄, open = 판 창을 엶), 클라이언트 → 서버 판 창의 일
 * (equip · unequip · move · stat · sync). 모드가 없는 클라이언트에는 보내지 않음 (canSend).
 */
public final class RelicNet {
    private RelicNet() {}

    /** 서버 → 클라이언트: 칸들 (늘 RelicSystem.SIZE 개 — 빈 칸은 EMPTY) · 칸마다 고른 스탯 줄 · 창 열기 */
    public record BoardPayload(List<ItemStack> items, List<String> stats, boolean open) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<BoardPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("relics"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BoardPayload> CODEC = CustomPacketPayload.codec(BoardPayload::write, BoardPayload::read);

        private static BoardPayload read(RegistryFriendlyByteBuf buf) {
            int n = Math.min(25, buf.readVarInt());
            List<ItemStack> items = new ArrayList<>(n);
            List<String> stats = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                items.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                stats.add(buf.readUtf(16));
            }
            return new BoardPayload(List.copyOf(items), List.copyOf(stats), buf.readBoolean());
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(items.size());
            for (int i = 0; i < items.size(); i++) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, items.get(i));
                buf.writeUtf(i < stats.size() && stats.get(i) != null ? stats.get(i) : "", 16);
            }
            buf.writeBoolean(open);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 클라이언트 → 서버: action = equip (a = 가방 칸 0 ~ 35 · 왼손 40, b = 판 칸 0 ~ 8 · -1 = 첫 빈 칸) · unequip (a = 칸) · move (a ↔ b) ·
     * stat (a = 칸, s = 스탯 줄 키) · sync
     */
    public record ActionPayload(String action, int a, int b, String s) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ActionPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("relic_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC = CustomPacketPayload.codec(ActionPayload::write, ActionPayload::read);

        private static ActionPayload read(RegistryFriendlyByteBuf buf) {
            return new ActionPayload(buf.readUtf(16), buf.readVarInt(), buf.readVarInt(), buf.readUtf(16));
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(action, 16);
            buf.writeVarInt(a);
            buf.writeVarInt(b);
            buf.writeUtf(s == null ? "" : s, 16);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** RelicSystem.init */
    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(BoardPayload.TYPE, BoardPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE, (p, context) -> RelicSystem.action(context.player(), p.action(), p.a(), p.b(), p.s()));
    }

    /** 모드 클라이언트인지 */
    public static boolean canSend(ServerPlayer sp) {
        return sp != null && !sp.isRemoved() && ServerPlayNetworking.canSend(sp, BoardPayload.TYPE);
    }

    /** 판을 클라이언트에 (open = 판 창을 엶) */
    public static void send(ServerPlayer sp, boolean open) {
        if (!canSend(sp)) return;
        List<ItemStack> items = new ArrayList<>(RelicSystem.SIZE);
        List<String> stats = new ArrayList<>(RelicSystem.SIZE);
        for (RelicSystem.Cell c : RelicSystem.cells(sp)) {
            items.add(c.item().copy());
            stats.add(c.stat());
        }
        ServerPlayNetworking.send(sp, new BoardPayload(items, stats, open));
    }
}
