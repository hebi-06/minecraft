package mmorpg.fabric.smith;

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
 * 0.44.0 대장장이 전용 창 패킷 (모드가 없는 클라이언트에는 보내지 않음 — 예전 상자 창). 0.45.0 부터 룬술사 · 상인 창도 같은 패킷
 * ({@link Workshop} — 창의 kind 로 가게를 가름).
 * <ul>
 *   <li>서버 → 클라이언트 {@link ViewPayload}: 탭 하나의 목록 · 칸마다 상세 (변화 · 성공 확률 · 재료 가진 수/필요 · 실패 · 단추) · 재료 줄 · 알림 ·
 *       마지막 결과 (open = 창을 엶)</li>
 *   <li>서버 → 클라이언트 {@link WorkPayload}: 일 시작 (NPC · 패널에 올린 것 · 틱 · 닿는 틱 · 동작 · 연출 종류 — 창 패널과 세상의 NPC 동작)</li>
 *   <li>클라이언트 → 서버 {@link ActionPayload}: tab (key = 탭) · page (a = 제작 쪽) · do (key = 칸, a = 고른 것 — 공허의 갑옷 부위, b = 1 이면 수호 주문서) ·
 *       sync · close</li>
 * </ul>
 */
public final class SmithNet {
    private SmithNet() {}

    public static final int MAX_ENTRIES = 80;
    static final int MAX_LINES = 12;
    static final int MAX_COSTS = 12;
    static final int MAX_VARIANTS = 8;

    /** 재료 한 줄: 그림 · 이름 · 가진 수 · 필요 수 */
    public record Cost(ItemStack icon, String name, long have, long need) {
        public boolean enough() {
            return have >= need;
        }

        static Cost read(RegistryFriendlyByteBuf buf) {
            return new Cost(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readUtf(64), buf.readVarLong(), buf.readVarLong());
        }

        void write(RegistryFriendlyByteBuf buf) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, icon == null ? ItemStack.EMPTY : icon);
            buf.writeUtf(name == null ? "" : name, 64);
            buf.writeVarLong(Math.max(0, have));
            buf.writeVarLong(Math.max(0, need));
        }
    }

    /**
     * 고를 것 하나 (공허의 갑옷 부위 · 한 벌 · 0.45.0 뽑기권 1장 · 5장 · 갑옷 부위 · 전리품 개수): 이름 · 그림 · 재료 · 할 수 있는지 · 알림 줄 ·
     * info = 고르면 보이는 한 줄 (0.45.0 — 받는 실버 같은 것, 없으면 "")
     */
    public record Variant(String label, ItemStack icon, List<Cost> costs, boolean can, String note, String info) {
        public Variant(String label, ItemStack icon, List<Cost> costs, boolean can, String note) {
            this(label, icon, costs, can, note, "");
        }

        static Variant read(RegistryFriendlyByteBuf buf) {
            String label = buf.readUtf(32);
            ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            List<Cost> costs = readCosts(buf);
            return new Variant(label, icon, costs, buf.readBoolean(), buf.readUtf(128), buf.readUtf(128));
        }

        void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(label == null ? "" : label, 32);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, icon == null ? ItemStack.EMPTY : icon);
            writeCosts(buf, costs);
            buf.writeBoolean(can);
            buf.writeUtf(note == null ? "" : note, 128);
            buf.writeUtf(info == null ? "" : info, 128);
        }
    }

    /**
     * 목록 칸 하나 (key = 할 일의 열쇠 — 서버가 다시 찾음). state: 0 됨 · 1 재료 부족 · 2 할 수 없음 (최대 강화) · 3 됨 (주의 — 레벨이 모자람).
     * group = 이 칸부터 새 묶음이면 묶음 이름 (아니면 ""). from → to = 큰 변화 글 ("+6" → "+7"), rate = 성공 확률 (없으면 -1), scroll = 수호 주문서
     * 가진 수 (하락 위험이 없으면 -1), action = 단추 글, can = 단추를 누를 수 있는지, note = 알림 줄, variants = 고를 것 (있으면 재료는 고른 것의 것),
     * confirm = 두 번 눌러야 하는 칸의 경고 (0.45.0 — 하나뿐인 룬 갈기 같은 것: 처음 누르면 이 글 · 단추가 "한 번 더", 없으면 "").
     */
    public record Entry(String key, ItemStack icon, String name, String badge, int state, String group, String sub, String from, String to,
            List<String> lines, double rate, List<Cost> costs, String fail, int scroll, String action, boolean can, String note, List<Variant> variants,
            String confirm) {
        public Entry(String key, ItemStack icon, String name, String badge, int state, String group, String sub, String from, String to, List<String> lines,
                double rate, List<Cost> costs, String fail, int scroll, String action, boolean can, String note, List<Variant> variants) {
            this(key, icon, name, badge, state, group, sub, from, to, lines, rate, costs, fail, scroll, action, can, note, variants, "");
        }

        /** 같은 칸에 두 번 누름 경고를 붙임 */
        public Entry withConfirm(String text) {
            return new Entry(key, icon, name, badge, state, group, sub, from, to, lines, rate, costs, fail, scroll, action, can, note, variants, text == null ? "" : text);
        }

        static Entry read(RegistryFriendlyByteBuf buf) {
            String key = buf.readUtf(64);
            ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            String name = buf.readUtf(128);
            String badge = buf.readUtf(32);
            int state = buf.readVarInt();
            String group = buf.readUtf(64);
            String sub = buf.readUtf(128);
            String from = buf.readUtf(32);
            String to = buf.readUtf(32);
            int nl = Math.min(MAX_LINES, buf.readVarInt());
            List<String> lines = new ArrayList<>(nl);
            for (int i = 0; i < nl; i++) lines.add(buf.readUtf(256));
            double rate = buf.readDouble();
            List<Cost> costs = readCosts(buf);
            String fail = buf.readUtf(128);
            int scroll = buf.readVarInt() - 1;
            String action = buf.readUtf(32);
            boolean can = buf.readBoolean();
            String note = buf.readUtf(128);
            int nv = Math.min(MAX_VARIANTS, buf.readVarInt());
            List<Variant> variants = new ArrayList<>(nv);
            for (int i = 0; i < nv; i++) variants.add(Variant.read(buf));
            String confirm = buf.readUtf(128);
            return new Entry(key, icon, name, badge, state, group, sub, from, to, List.copyOf(lines), rate, costs, fail, scroll, action, can, note, List.copyOf(variants),
                    confirm);
        }

        void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(key, 64);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, icon == null ? ItemStack.EMPTY : icon);
            buf.writeUtf(nz(name), 128);
            buf.writeUtf(nz(badge), 32);
            buf.writeVarInt(state);
            buf.writeUtf(nz(group), 64);
            buf.writeUtf(nz(sub), 128);
            buf.writeUtf(nz(from), 32);
            buf.writeUtf(nz(to), 32);
            List<String> ls = lines == null ? List.of() : lines;
            int nl = Math.min(MAX_LINES, ls.size());
            buf.writeVarInt(nl);
            for (int i = 0; i < nl; i++) buf.writeUtf(nz(ls.get(i)), 256);
            buf.writeDouble(rate);
            writeCosts(buf, costs);
            buf.writeUtf(nz(fail), 128);
            buf.writeVarInt(Math.max(-1, scroll) + 1);
            buf.writeUtf(nz(action), 32);
            buf.writeBoolean(can);
            buf.writeUtf(nz(note), 128);
            List<Variant> vs = variants == null ? List.of() : variants;
            int nv = Math.min(MAX_VARIANTS, vs.size());
            buf.writeVarInt(nv);
            for (int i = 0; i < nv; i++) vs.get(i).write(buf);
            buf.writeUtf(nz(confirm), 128);
        }
    }

    /** 재료 줄 하나 (창 위 — 그 탭에서 쓰는 것): 그림 · 이름 · 가진 수 */
    public record Res(ItemStack icon, String name, long count) {}

    /** 망치질 결과 (seq 가 바뀌면 한 번 보여 줌): kind = success · keep · guard · down · done · error, key = 그 칸, title = 큰 글 · text = 설명 */
    public record Result(int seq, String kind, String key, String title, String text, ItemStack item) {
        static final Result NONE = new Result(0, "", "", "", "", ItemStack.EMPTY);
    }

    /**
     * 서버 → 클라이언트: 가게 창 (open = 엶). npc = 그 가게 NPC 엔티티 번호 (-1 = 없음 — 명령어로 연 창), tab · tabs = 지금 탭 · 보이는 탭,
     * page · pages = 지금 탭의 쪽 (없으면 빈 목록), entries = 목록, res = 재료 줄, message = 알림 줄, empty = 목록이 비었을 때 글, result = 마지막 결과,
     * busy = 일하는 중. 0.45.0: kind = 가게 (smith · runesmith · shop — 패널 장면), role = 창 위 이름, tabNames = 탭 글 (tabs 와 같은 차례), listTitle = 목록 머리
     */
    public record ViewPayload(boolean open, int npc, String npcName, String tab, List<String> tabs, int page, List<String> pages, List<Entry> entries, List<Res> res,
            String message, String empty, Result result, boolean busy, String kind, String role, List<String> tabNames, String listTitle) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ViewPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("smith_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ViewPayload> CODEC = CustomPacketPayload.codec(ViewPayload::write, ViewPayload::read);

        private static ViewPayload read(RegistryFriendlyByteBuf buf) {
            boolean open = buf.readBoolean();
            int npc = buf.readVarInt() - 1;
            String npcName = buf.readUtf(64);
            String tab = buf.readUtf(16);
            List<String> tabs = readStrings(buf, 8, 16);
            int page = buf.readVarInt();
            List<String> pages = readStrings(buf, 12, 32);
            int ne = Math.min(MAX_ENTRIES, buf.readVarInt());
            List<Entry> entries = new ArrayList<>(ne);
            for (int i = 0; i < ne; i++) entries.add(Entry.read(buf));
            int nr = Math.min(8, buf.readVarInt());
            List<Res> res = new ArrayList<>(nr);
            for (int i = 0; i < nr; i++) res.add(new Res(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readUtf(32), buf.readVarLong()));
            String message = buf.readUtf(256);
            String empty = buf.readUtf(256);
            Result result = new Result(buf.readVarInt(), buf.readUtf(16), buf.readUtf(64), buf.readUtf(128), buf.readUtf(256), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
            boolean busy = buf.readBoolean();
            String kind = buf.readUtf(16);
            String role = buf.readUtf(32);
            List<String> tabNames = readStrings(buf, 8, 32);
            String listTitle = buf.readUtf(64);
            return new ViewPayload(open, npc, npcName, tab, tabs, page, pages, List.copyOf(entries), List.copyOf(res), message, empty, result, busy, kind, role, tabNames,
                    listTitle);
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeBoolean(open);
            buf.writeVarInt(Math.max(-1, npc) + 1);
            buf.writeUtf(nz(npcName), 64);
            buf.writeUtf(nz(tab), 16);
            writeStrings(buf, tabs, 8, 16);
            buf.writeVarInt(Math.max(0, page));
            writeStrings(buf, pages, 12, 32);
            List<Entry> es = entries == null ? List.of() : entries;
            int ne = Math.min(MAX_ENTRIES, es.size());
            buf.writeVarInt(ne);
            for (int i = 0; i < ne; i++) es.get(i).write(buf);
            List<Res> rs = res == null ? List.of() : res;
            int nr = Math.min(8, rs.size());
            buf.writeVarInt(nr);
            for (int i = 0; i < nr; i++) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, rs.get(i).icon() == null ? ItemStack.EMPTY : rs.get(i).icon());
                buf.writeUtf(nz(rs.get(i).name()), 32);
                buf.writeVarLong(Math.max(0, rs.get(i).count()));
            }
            buf.writeUtf(nz(message), 256);
            buf.writeUtf(nz(empty), 256);
            Result r = result == null ? Result.NONE : result;
            buf.writeVarInt(r.seq());
            buf.writeUtf(nz(r.kind()), 16);
            buf.writeUtf(nz(r.key()), 64);
            buf.writeUtf(nz(r.title()), 128);
            buf.writeUtf(nz(r.text()), 256);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, r.item() == null ? ItemStack.EMPTY : r.item());
            buf.writeBoolean(busy);
            buf.writeUtf(nz(kind), 16);
            buf.writeUtf(nz(role), 32);
            writeStrings(buf, tabNames, 8, 32);
            buf.writeUtf(nz(listTitle), 64);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * 서버 → 클라이언트: 일 시작. npc = 그 가게 NPC (-1 = 없음), key = 그 칸, item = 패널에 올린 것, ticks = 동작 틱, impacts = 닿는 틱,
     * grade = 빛 색 (등급 순번 — 0 일반 ~ 4 초월), yaw = NPC 가 볼 쪽 (NaN = 몸 방향 그대로). 0.45.0: motion = NPC 동작 (poses.json),
     * fx = 패널 · 소리 종류 (forge 망치질 · grind 룬 갈기 · merge 돌파 · ticket 뽑기권 · buy 사기 · sell 팔기)
     */
    public record WorkPayload(int npc, String key, ItemStack item, int ticks, List<Integer> impacts, int grade, float yaw, String motion, String fx)
            implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<WorkPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("smith_work"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WorkPayload> CODEC = CustomPacketPayload.codec(WorkPayload::write, WorkPayload::read);

        private static WorkPayload read(RegistryFriendlyByteBuf buf) {
            int npc = buf.readVarInt() - 1;
            String key = buf.readUtf(64);
            ItemStack item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            int ticks = buf.readVarInt();
            int n = Math.min(16, buf.readVarInt());
            List<Integer> impacts = new ArrayList<>(n);
            for (int i = 0; i < n; i++) impacts.add(buf.readVarInt());
            int grade = buf.readVarInt();
            float yaw = buf.readFloat();
            return new WorkPayload(npc, key, item, ticks, List.copyOf(impacts), grade, yaw, buf.readUtf(32), buf.readUtf(16));
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeVarInt(Math.max(-1, npc) + 1);
            buf.writeUtf(nz(key), 64);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, item == null ? ItemStack.EMPTY : item);
            buf.writeVarInt(Math.max(1, ticks));
            List<Integer> im = impacts == null ? List.of() : impacts;
            int n = Math.min(16, im.size());
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) buf.writeVarInt(Math.max(0, im.get(i)));
            buf.writeVarInt(Math.max(0, grade));
            buf.writeFloat(yaw);
            buf.writeUtf(nz(motion), 32);
            buf.writeUtf(nz(fx), 16);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 클라이언트 → 서버: action = tab (key) · page (a) · do (key, a = 고른 것, b = 1 이면 수호 주문서) · sync · close (열린 가게 창 — {@link Workshop}) */
    public record ActionPayload(String action, String key, int a, int b) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<ActionPayload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("smith_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC = CustomPacketPayload.codec(ActionPayload::write, ActionPayload::read);

        private static ActionPayload read(RegistryFriendlyByteBuf buf) {
            return new ActionPayload(buf.readUtf(16), buf.readUtf(64), buf.readVarInt() - 1, buf.readVarInt());
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(nz(action), 16);
            buf.writeUtf(nz(key), 64);
            buf.writeVarInt(Math.max(-1, a) + 1);
            buf.writeVarInt(Math.max(0, b));
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }

    static List<Cost> readCosts(RegistryFriendlyByteBuf buf) {
        int n = Math.min(MAX_COSTS, buf.readVarInt());
        List<Cost> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(Cost.read(buf));
        return List.copyOf(out);
    }

    static void writeCosts(RegistryFriendlyByteBuf buf, List<Cost> costs) {
        List<Cost> cs = costs == null ? List.of() : costs;
        int n = Math.min(MAX_COSTS, cs.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) cs.get(i).write(buf);
    }

    static List<String> readStrings(RegistryFriendlyByteBuf buf, int max, int len) {
        int n = Math.min(max, buf.readVarInt());
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(buf.readUtf(len));
        return List.copyOf(out);
    }

    static void writeStrings(RegistryFriendlyByteBuf buf, List<String> list, int max, int len) {
        List<String> ls = list == null ? List.of() : list;
        int n = Math.min(max, ls.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) buf.writeUtf(nz(ls.get(i)), len);
    }

    /** Workshop.init */
    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(ViewPayload.TYPE, ViewPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WorkPayload.TYPE, WorkPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ActionPayload.TYPE, ActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ActionPayload.TYPE, (p, context) -> Workshop.action(context.player(), p.action(), p.key(), p.a(), p.b()));
    }

    /** 가게 전용 창을 쓸 수 있는 클라이언트인지 (시험은 {@link Workshop#FORCE}) */
    public static boolean canSend(ServerPlayer sp) {
        return sp != null && !sp.isRemoved() && ServerPlayNetworking.canSend(sp, ViewPayload.TYPE);
    }
}
