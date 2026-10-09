package mmorpg.fabric;

import java.util.List;
import mmorpg.core.Stats;
import mmorpg.core.Weapon;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * 서버 ↔ 클라이언트 패킷: 찍은 스탯 · 포인트 · 최고 레벨 (스탯 창 · HUD 레벨 칸), 스탯 찍기.
 * 체력 · 최대 체력은 바닐라가 보내므로 따로 없음. 모드가 없는 클라이언트는 채팅 스탯 창 (누르는 [+1]).
 */
public final class RpgNet {
    private RpgNet() {}

    /** 서버 → 클라이언트: 스탯 데이터 (open = 스탯 창 열기) */
    public record SyncPayload(int str, int dex, int intel, int vit, int point, int lvmax, boolean open) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SyncPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("sync"));
        public static final StreamCodec<FriendlyByteBuf, SyncPayload> CODEC = CustomPacketPayload.codec(SyncPayload::write, SyncPayload::read);

        private static SyncPayload read(FriendlyByteBuf buf) {
            return new SyncPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(str);
            buf.writeVarInt(dex);
            buf.writeVarInt(intel);
            buf.writeVarInt(vit);
            buf.writeVarInt(point);
            buf.writeVarInt(lvmax);
            buf.writeBoolean(open);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 클라이언트 → 서버: 스탯 key 에 포인트 count 개 */
    public record SpendPayload(String stat, int count) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SpendPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("spend"));
        public static final StreamCodec<FriendlyByteBuf, SpendPayload> CODEC = CustomPacketPayload.codec(SpendPayload::write, SpendPayload::read);

        private static SpendPayload read(FriendlyByteBuf buf) {
            return new SpendPayload(buf.readUtf(16), buf.readVarInt());
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeUtf(stat, 16);
            buf.writeVarInt(count);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void init() {
        PayloadTypeRegistry.clientboundPlay().register(SyncPayload.TYPE, SyncPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SpendPayload.TYPE, SpendPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SpendPayload.TYPE, (payload, context) -> RpgPlayers.spend(context.player(), payload.stat(), Math.min(100, payload.count())));
    }

    /** 스탯 데이터를 클라이언트에 (모드가 있는 클라이언트만) */
    public static void sync(ServerPlayer sp, boolean open) {
        if (!ServerPlayNetworking.canSend(sp, SyncPayload.TYPE)) return;
        RpgData d = RpgData.of(sp);
        ServerPlayNetworking.send(sp, new SyncPayload(d.str(), d.dex(), d.intel(), d.vit(), d.point(), d.lvmax(), open));
    }

    /** 스탯 창 열기: 모드가 있으면 화면, 없으면 채팅 스탯 창 */
    public static void openStats(ServerPlayer sp) {
        if (ServerPlayNetworking.canSend(sp, SyncPayload.TYPE)) {
            sync(sp, true);
            return;
        }
        StatSheet s = StatSheet.of(RpgData.of(sp), sp.experienceLevel);
        sp.sendSystemMessage(Component.literal(BedrockText.toJava("§6[RPG] §e§l스탯 §r§7· 남은 포인트 §f" + s.point())));
        for (String key : Stats.STAT_KEYS) {
            MutableComponent line = Component.literal(BedrockText.toJava(" " + s.statLine(key, false) + " "));
            if (s.point() > 0) {
                line.append(Component.literal("[+1]").withStyle(st -> st.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent.RunCommand("/rpg spend " + key + " 1"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("1포인트 투자")))));
            }
            sp.sendSystemMessage(line);
        }
        Weapon w = StatSheet.referenceWeapon(sp.getMainHandItem(), sp.getOffhandItem(), sp.getInventory().getNonEquipmentItems(), s::usable);
        List<String> lines = s.weaponLines(w, false);
        for (String l : lines) sp.sendSystemMessage(Component.literal(BedrockText.toJava(" " + l)));
    }
}
