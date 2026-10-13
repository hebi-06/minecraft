package mmorpg.fabric.rune;

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
 * 스킬 칸 (11단계) 패킷: 서버 → 클라이언트 칸들 · 룬 재사용 대기, 클라이언트 → 서버 키 · 스킬 칸 창의 일.
 * 모드가 없는 클라이언트에는 보내지 않음 (canSend).
 */
public final class SkillBarNet {
    private SkillBarNet() {}

    /** 서버 → 클라이언트: 스킬 칸 (늘 SkillBar.SIZE 개 — 빈 칸은 EMPTY) */
    public record BarPayload(List<ItemStack> slots) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<BarPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("skill_bar"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BarPayload> CODEC = CustomPacketPayload.codec(BarPayload::write, BarPayload::read);

        private static BarPayload read(RegistryFriendlyByteBuf buf) {
            int n = Math.min(64, buf.readVarInt());
            List<ItemStack> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
            return new BarPayload(List.copyOf(list));
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(slots.size());
            for (ItemStack s : slots) ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 서버 → 클라이언트: 룬 재사용 대기 (rune = 룬 ID · "*" = 모두 지움, left = 남은 틱 (0 = 끝남), total = 전체 틱) */
    public record CooldownPayload(String rune, int left, int total) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<CooldownPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("skill_cd"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CooldownPayload> CODEC = CustomPacketPayload.codec(CooldownPayload::write,
                CooldownPayload::read);

        private static CooldownPayload read(RegistryFriendlyByteBuf buf) {
            return new CooldownPayload(buf.readUtf(128), buf.readVarInt(), buf.readVarInt());
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(rune, 128);
            buf.writeVarInt(left);
            buf.writeVarInt(total);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 클라이언트 → 서버: action = cast (a = 칸) · equip (a = 가방 칸, b = 스킬 칸) · unequip (a = 칸) · move (a → b) · sync */
    public record ActionPayload(String action, int a, int b) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ActionPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("skill_bar_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC = CustomPacketPayload.codec(ActionPayload::write, ActionPayload::read);

        private static ActionPayload read(RegistryFriendlyByteBuf buf) {
            return new ActionPayload(buf.readUtf(16), buf.readVarInt(), buf.readVarInt());
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(action, 16);
            buf.writeVarInt(a);
            buf.writeVarInt(b);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** SkillBar.init */
    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(BarPayload.TYPE, BarPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CooldownPayload.TYPE, CooldownPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE, (payload, context) -> SkillBar.action(context.player(), payload.action(), payload.a(), payload.b()));
    }

    /** 스킬 칸을 클라이언트에 (모드가 있는 클라이언트만) */
    public static void sendBar(ServerPlayer sp) {
        if (sp == null || sp.isRemoved() || !ServerPlayNetworking.canSend(sp, BarPayload.TYPE)) return;
        List<ItemStack> list = new ArrayList<>(SkillBar.SIZE);
        for (ItemStack s : SkillBar.slots(sp)) list.add(s.copy());
        ServerPlayNetworking.send(sp, new BarPayload(list));
    }

    /** 룬 재사용 대기를 클라이언트에 (모드가 있는 클라이언트만) */
    public static void cooldown(ServerPlayer sp, String rune, int left, int total) {
        if (sp == null || sp.isRemoved() || !ServerPlayNetworking.canSend(sp, CooldownPayload.TYPE)) return;
        ServerPlayNetworking.send(sp, new CooldownPayload(rune, Math.max(0, left), Math.max(0, total)));
    }
}
