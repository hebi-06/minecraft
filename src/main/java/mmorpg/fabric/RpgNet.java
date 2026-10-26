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
 * 서버 ↔ 클라이언트 패킷: 찍은 스탯 · 포인트 · 최고 레벨 (스탯 창 · HUD 레벨 칸), 스탯 찍기, 화면 흔들림.
 * 체력 · 최대 체력은 바닐라가 보내므로 따로 없음. 모드가 없는 클라이언트는 채팅 스탯 창 (누르는 [+1]).
 */
public final class RpgNet {
    private RpgNet() {}

    /** 서버 → 클라이언트: 스탯 데이터 (open = 스탯 창 열기) */
    public record SyncPayload(int str, int dex, int intel, int vit, int point, int lvmax, boolean open, int bstr, int bdex, int bint, int bvit, float buff)
            implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SyncPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("sync"));
        public static final StreamCodec<FriendlyByteBuf, SyncPayload> CODEC = CustomPacketPayload.codec(SyncPayload::write, SyncPayload::read);

        private static SyncPayload read(FriendlyByteBuf buf) {
            return new SyncPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readFloat());
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(str);
            buf.writeVarInt(dex);
            buf.writeVarInt(intel);
            buf.writeVarInt(vit);
            buf.writeVarInt(point);
            buf.writeVarInt(lvmax);
            buf.writeBoolean(open);
            buf.writeVarInt(bstr);
            buf.writeVarInt(bdex);
            buf.writeVarInt(bint);
            buf.writeVarInt(bvit);
            buf.writeFloat(buff);
        }

        /** 장비 보너스 {str, dex, int, vit} (0 은 뺌) */
        public java.util.Map<String, Double> bonus() {
            java.util.Map<String, Double> m = new java.util.LinkedHashMap<>();
            if (bstr > 0) m.put("str", (double) bstr);
            if (bdex > 0) m.put("dex", (double) bdex);
            if (bint > 0) m.put("int", (double) bint);
            if (bvit > 0) m.put("vit", (double) bvit);
            return m;
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

    /** 서버 → 클라이언트: 화면 흔들림 (애드온 camerashake add @s 세기 초 rotational) */
    public record ShakePayload(float intensity, float seconds) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ShakePayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("shake"));
        public static final StreamCodec<FriendlyByteBuf, ShakePayload> CODEC = CustomPacketPayload.codec(ShakePayload::write, ShakePayload::read);

        private static ShakePayload read(FriendlyByteBuf buf) {
            return new ShakePayload(buf.readFloat(), buf.readFloat());
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeFloat(intensity);
            buf.writeFloat(seconds);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 초월 룬 하늘빛 (ultfx.js — 베드락 /fog): ticks 틱 동안 안개 start ~ end 칸 · 색 rgb (0 ~ 1) 로 하늘과 먼 곳을 물들임. ticks 0 = 걷음.
     * token: 같은 하늘빛을 일찍 걷을 때 (다른 하늘빛으로 바뀌었으면 걷지 않음). edge: 화면 테두리 빛 (0 ~ 1 — 8단계 6돌 각성 연출, 보스 안개는 0)
     */
    public record SkyPayload(int token, int ticks, float start, float end, float r, float g, float b, float edge) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<SkyPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("sky"));
        public static final StreamCodec<FriendlyByteBuf, SkyPayload> CODEC = CustomPacketPayload.codec(SkyPayload::write, SkyPayload::read);

        /** 테두리 빛 없음 */
        public SkyPayload(int token, int ticks, float start, float end, float r, float g, float b) {
            this(token, ticks, start, end, r, g, b, 0F);
        }

        private static SkyPayload read(FriendlyByteBuf buf) {
            return new SkyPayload(buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat());
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(token);
            buf.writeVarInt(ticks);
            buf.writeFloat(start);
            buf.writeFloat(end);
            buf.writeFloat(r);
            buf.writeFloat(g);
            buf.writeFloat(b);
            buf.writeFloat(edge);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 화면 번쩍임 (ultfx.js — 베드락 camera fade): 색 rgb 로 fadeIn 초 동안 차오르고 hold 초 머문 뒤 fadeOut 초 동안 걷힘 */
    public record FlashPayload(float r, float g, float b, float fadeIn, float hold, float fadeOut) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<FlashPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("flash"));
        public static final StreamCodec<FriendlyByteBuf, FlashPayload> CODEC = CustomPacketPayload.codec(FlashPayload::write, FlashPayload::read);

        private static FlashPayload read(FriendlyByteBuf buf) {
            return new FlashPayload(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeFloat(r);
            buf.writeFloat(g);
            buf.writeFloat(b);
            buf.writeFloat(fadeIn);
            buf.writeFloat(hold);
            buf.writeFloat(fadeOut);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 서버 → 클라이언트: 탈것 상태 (스탯 창 탈것 칸 — 6단계). current = 지금 탈것 키 ("" = 없음), owned = 가진 탈것 키들 (탈것 목록 순서),
     * cooldown = 다시 부를 수 있을 때까지 남은 틱
     */
    public record MountPayload(String current, List<String> owned, int cooldown) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<MountPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("mount"));
        public static final StreamCodec<FriendlyByteBuf, MountPayload> CODEC = CustomPacketPayload.codec(MountPayload::write, MountPayload::read);

        private static MountPayload read(FriendlyByteBuf buf) {
            String cur = buf.readUtf(64);
            int n = Math.min(256, buf.readVarInt());
            List<String> owned = new java.util.ArrayList<>();
            for (int i = 0; i < n; i++) owned.add(buf.readUtf(64));
            return new MountPayload(cur, List.copyOf(owned), buf.readVarInt());
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeUtf(current, 64);
            buf.writeVarInt(owned.size());
            for (String k : owned) buf.writeUtf(k, 64);
            buf.writeVarInt(cooldown);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 클라이언트 → 서버: 스탯 창 아래 단추 (quest · ender · mount · mount_select — 받는 곳은 {@link #onStatAction}) */
    public record StatActionPayload(String action) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StatActionPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("stat_action"));
        public static final StreamCodec<FriendlyByteBuf, StatActionPayload> CODEC = CustomPacketPayload.codec(StatActionPayload::write,
                StatActionPayload::read);

        private static StatActionPayload read(FriendlyByteBuf buf) {
            return new StatActionPayload(buf.readUtf(32));
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeUtf(action, 32);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 서버 → 클라이언트: 스탯 트리 (7단계). JSON {"on": 쓰는지, "state": 기록 (고른 갈래 · 받은 칸), "totals": 보너스 합, "open": 열 탭 ("" = 열지 않음),
     * "money": 가진 실버 (지갑 + 은화 — 0.21.0 전에는 에메랄드)}
     */
    public record StatTreePayload(String json) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StatTreePayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("stat_tree"));
        public static final StreamCodec<FriendlyByteBuf, StatTreePayload> CODEC = CustomPacketPayload.codec(StatTreePayload::write, StatTreePayload::read);

        private static StatTreePayload read(FriendlyByteBuf buf) {
            return new StatTreePayload(buf.readUtf(32767));
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeUtf(json, 32767);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 클라이언트 → 서버: 스탯 트리 창 (action = pick · reset · sync, stat, index = 칸, option = 갈래) */
    public record StatTreeActionPayload(String action, String stat, int index, int option) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StatTreeActionPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("stat_tree_action"));
        public static final StreamCodec<FriendlyByteBuf, StatTreeActionPayload> CODEC = CustomPacketPayload.codec(StatTreeActionPayload::write,
                StatTreeActionPayload::read);

        private static StatTreeActionPayload read(FriendlyByteBuf buf) {
            return new StatTreeActionPayload(buf.readUtf(16), buf.readUtf(16), buf.readVarInt(), buf.readVarInt());
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeUtf(action, 16);
            buf.writeUtf(stat, 16);
            buf.writeVarInt(index);
            buf.writeVarInt(option);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 스탯 트리 보내기 (모드가 있는 클라이언트만) */
    public static void statTree(ServerPlayer sp, StatTreePayload p) {
        if (ServerPlayNetworking.canSend(sp, StatTreePayload.TYPE)) ServerPlayNetworking.send(sp, p);
    }

    public static boolean canSendStatTree(ServerPlayer sp) {
        return ServerPlayNetworking.canSend(sp, StatTreePayload.TYPE);
    }

    /** 스탯 창 단추 → 할 일 (퀘스트 목록 · 엔더 상자 · 탈것 — 각 모듈이 넣음) */
    private static final java.util.Map<String, java.util.function.Consumer<ServerPlayer>> STAT_ACTIONS = new java.util.HashMap<>();

    /** 스탯 창 단추의 할 일을 넣음 (같은 이름이면 바꿈) */
    public static void onStatAction(String action, java.util.function.Consumer<ServerPlayer> run) {
        STAT_ACTIONS.put(action, run);
    }

    /** 스탯 창 단추 처리 (클라이언트가 보낸 것 · 시험) — 모르는 이름이면 false */
    public static boolean statAction(ServerPlayer sp, String action) {
        java.util.function.Consumer<ServerPlayer> run = STAT_ACTIONS.get(action);
        if (run == null || sp.isRemoved()) return false;
        run.accept(sp);
        return true;
    }

    static void init() {
        PayloadTypeRegistry.clientboundPlay().register(SyncPayload.TYPE, SyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ShakePayload.TYPE, ShakePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SkyPayload.TYPE, SkyPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FlashPayload.TYPE, FlashPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MountPayload.TYPE, MountPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(StatTreePayload.TYPE, StatTreePayload.CODEC);
        Fx3d.init(); // 0.47.0 입체 효과 물체
        PayloadTypeRegistry.serverboundPlay().register(SpendPayload.TYPE, SpendPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(StatActionPayload.TYPE, StatActionPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(StatTreeActionPayload.TYPE, StatTreeActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SpendPayload.TYPE, (payload, context) -> RpgPlayers.spend(context.player(), payload.stat(), Math.min(100, payload.count())));
        ServerPlayNetworking.registerGlobalReceiver(StatActionPayload.TYPE, (payload, context) -> statAction(context.player(), payload.action()));
    }

    /** 탈것 상태 보내기 (모드가 있는 클라이언트만) */
    public static void mount(ServerPlayer sp, MountPayload p) {
        if (ServerPlayNetworking.canSend(sp, MountPayload.TYPE)) ServerPlayNetworking.send(sp, p);
    }

    /** 스탯 데이터를 클라이언트에 (모드가 있는 클라이언트만) */
    public static void sync(ServerPlayer sp, boolean open) {
        if (!ServerPlayNetworking.canSend(sp, SyncPayload.TYPE)) return;
        RpgData d = RpgData.of(sp);
        java.util.Map<String, Double> b = RpgStats.bonusOf(sp);
        ServerPlayNetworking.send(sp, new SyncPayload(d.str(), d.dex(), d.intel(), d.vit(), d.point(), d.lvmax(), open, (int) Math.round(b.getOrDefault("str", 0.0)),
                (int) Math.round(b.getOrDefault("dex", 0.0)), (int) Math.round(b.getOrDefault("int", 0.0)), (int) Math.round(b.getOrDefault("vit", 0.0)),
                (float) RpgStats.buffOf(sp)));
    }

    /** 화면 흔들림 (모드가 있는 클라이언트만 — 없으면 아무 일 없음) */
    public static void shake(ServerPlayer sp, double intensity, double seconds) {
        if (!ServerPlayNetworking.canSend(sp, ShakePayload.TYPE)) return;
        ServerPlayNetworking.send(sp, new ShakePayload((float) intensity, (float) seconds));
    }

    /** 하늘빛 보내기 (모드가 있는 클라이언트만) */
    public static void sky(ServerPlayer sp, SkyPayload p) {
        if (ServerPlayNetworking.canSend(sp, SkyPayload.TYPE)) ServerPlayNetworking.send(sp, p);
    }

    /** 화면 번쩍임 보내기 (모드가 있는 클라이언트만) */
    public static void flash(ServerPlayer sp, FlashPayload p) {
        if (ServerPlayNetworking.canSend(sp, FlashPayload.TYPE)) ServerPlayNetworking.send(sp, p);
    }

    /** 스탯 창을 열기 전에 보낼 것 (탈것 상태 등 — 각 모듈이 넣음) */
    private static final List<java.util.function.Consumer<ServerPlayer>> BEFORE_OPEN = new java.util.ArrayList<>();

    public static void beforeStatsOpen(java.util.function.Consumer<ServerPlayer> fn) {
        BEFORE_OPEN.add(fn);
    }

    /** 스탯 창 열기: 모드가 있으면 화면, 없으면 채팅 스탯 창 */
    public static void openStats(ServerPlayer sp) {
        if (sp.isRemoved()) return;
        if (ServerPlayNetworking.canSend(sp, SyncPayload.TYPE)) {
            for (java.util.function.Consumer<ServerPlayer> fn : BEFORE_OPEN) fn.accept(sp);
            sync(sp, true);
            return;
        }
        StatSheet s = StatSheet.of(sp);
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
        List<String> lines = s.weaponLines(w, mmorpg.fabric.gear.WeaponEnhance.reference(sp.getMainHandItem(), sp.getInventory().getNonEquipmentItems(), w), false);
        for (String l : lines) sp.sendSystemMessage(Component.literal(BedrockText.toJava(" " + l)));
    }
}
