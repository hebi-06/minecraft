package mmorpg.fabric.smith;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import mmorpg.fabric.FxDisplays;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.motion.MotionNet;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.skill.SkillFx;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 0.45.0 가게 창의 공통 흐름 (서버 — 0.44.0 대장장이 창 {@link SmithUi} 를 나눔: 사용자 "룬술사랑 상인 같은 애들도 이렇게 바꾸자").
 * 가게 ({@link Kind}) 는 대장장이 ({@link SmithUi}) · 룬술사 ({@link RuneSmithUi}) · 상인 ({@link ShopUi}) — 같은 창 (위 탭 · 왼쪽 패널의 NPC ·
 * 가운데 목록 · 오른쪽 상세 · 큰 단추) 과 같은 흐름:
 * <ul>
 *   <li>단추를 누르면 칸을 다시 찾고 (재료 · 돈이 있는지) 할 일을 준비 ({@link Prepared} — 그 순간의 것을 잡아 둠) → 창 패널과 세상의 NPC 가 그 가게의
 *       동작 (망치질 · 룬 다루기 · 물건 건네기) → 동작이 끝난 뒤 판정 · 결과 (재료 · 돈은 그때 받음 — 도중에 나가거나 죽으면 아무것도 쓰지 않음)</li>
 *   <li>결과는 창에 (닫았으면 화면 제목)</li>
 *   <li>세상: NPC 가 있으면 둘레 사람에게 동작 (MotionNet) · 가게의 세상 연출 ({@link Show} — 모루 · 마법진 · 물건이 건너감)</li>
 *   <li>모드가 없는 클라이언트 · 시험의 가짜 플레이어는 예전 상자 창 — {@link #FORCE} · 가게마다 legacy 로 시험이 고름</li>
 * </ul>
 */
public final class Workshop {
    private Workshop() {}

    /** 가게 하나 (대장장이 · 룬술사 · 상인) */
    public interface Kind {
        /** smith · runesmith · shop (클라이언트가 패널 장면을 고름) */
        String id();

        /** 창 위 이름 ("대장장이" · "룬술사" · "상인") */
        String role();

        /** 사진첩: 예전 상자 창으로 (모드 클라이언트도) */
        boolean legacy();

        /** 보이는 탭 */
        List<String> tabs(ServerPlayer sp, Session s);

        /** 탭 이름 (창에 보이는 글) */
        String tabName(String tab);

        /** 처음 탭 · 쪽 (start = 흐름의 시작 이름 — 가게마다) */
        void begin(ServerPlayer sp, Session s, String start);

        /** 탭을 바꾼 뒤 (쪽 고르기 등) */
        default void onTab(ServerPlayer sp, Session s) {}

        /** 지금 탭의 쪽 이름 (없으면 빈 목록 — 쪽 넘김이 없음) */
        default List<String> pages(ServerPlayer sp, Session s) {
            return List.of();
        }

        /** 목록 머리 글 (쪽이 없을 때) */
        String listTitle(ServerPlayer sp, Session s);

        /** 지금 탭의 목록 */
        List<SmithNet.Entry> entries(ServerPlayer sp, Session s);

        /** 재료 줄 (목록 아래 — 그 탭에서 쓰는 것) */
        List<SmithNet.Res> res(ServerPlayer sp, Session s);

        /** 목록이 비었을 때 글 */
        String emptyText(ServerPlayer sp, Session s);

        /** 누른 칸의 할 일 (key · variant = 고른 것 · flag = 수호 주문서 같은 켬) */
        Prepared prepare(ServerPlayer sp, Session s, String key, int variant, boolean flag);

        /** 세상 연출 (NPC 가 있을 때 — null 이면 동작만) */
        default Show show(ServerPlayer sp, Session s, Entity npc, Prepared p) {
            return null;
        }
    }

    /** 할 일 하나 (누른 순간에 고른 것 · 비용을 잡아 둠 — 동작이 끝나면 run: 다시 확인하고 재료를 받고 결과) */
    public interface Job {
        SmithNet.Result run(int seq);
    }

    /**
     * 누른 칸의 할 일 (job · 패널에 올릴 것 · 등급 색) 또는 못 하는 까닭 (error). fx = 패널 · 소리의 종류 (forge · grind · merge · ticket · buy · sell),
     * motion = NPC 동작 (poses.json), ticks = 동작 틱 (이 뒤에 판정), impacts = 닿는 틱
     */
    public record Prepared(Job job, ItemStack shown, int grade, String error, String fx, String motion, int ticks, List<Integer> impacts) {
        public static Prepared fail(String error) {
            return new Prepared(null, ItemStack.EMPTY, 0, error, "", "", 1, List.of());
        }
    }

    /** 세상 연출 하나 (NPC 가 있을 때 — 시작에 만들고, 닿는 틱마다 · 끝에 부름) */
    public interface Show {
        /** NPC 가 볼 쪽 (마인크래프트 각도 — NaN 이면 몸 방향 그대로) */
        float yaw();

        /** i 번째로 닿음 (last = 마지막) */
        default void impact(int i, boolean last) {}

        /** 끝 (r = 결과 — null 이면 취소: 치우기만) */
        default void end(SmithNet.Result r) {}
    }

    /** 한 사람의 창 상태 */
    public static final class Session {
        public final Kind kind;
        public Entity npc;
        public String tab = "";
        public int page = -1;
        /** 상점 레벨 (상인 — NPC 의 상점 레벨 · 명령어) */
        public int level = -1;
        boolean open = true;
        Runnable done;
        boolean busy;
        String message = "";
        SmithNet.Result result = SmithNet.Result.NONE;
        int seq;
        String workKey = "";

        Session(Kind kind) {
            this.kind = kind;
        }
    }

    /** 시험: 이 사람들은 클라이언트 없이도 새 창 흐름 (보내는 것은 {@link #SENT} 에 쌓임 — 함께 도는 다른 시험과 섞이지 않게 사람마다) */
    public static final Set<UUID> FORCE = new HashSet<>();
    public static final Map<UUID, List<CustomPacketPayload>> SENT = new HashMap<>();

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** 시험: NPC (엔티티 번호) 가 마지막으로 일을 시작한 틱 */
    private static final Map<Integer, Long> NPC_WORK = new HashMap<>();
    private static boolean inited;

    /** Smith.init (한 번) */
    static void init() {
        if (inited) return;
        inited = true;
        SmithNet.register();
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SESSIONS.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SESSIONS.clear();
            NPC_WORK.clear();
        });
    }

    /** 새 창을 쓸지 (모드 클라이언트 — 시험은 FORCE, 사진첩은 가게의 legacy 로 예전 창) */
    public static boolean usable(ServerPlayer sp, Kind kind) {
        return !kind.legacy() && (FORCE.contains(sp.getUUID()) || SmithNet.canSend(sp));
    }

    private static Session session(ServerPlayer sp) {
        return SESSIONS.get(sp.getUUID());
    }

    /** 창이 열려 있는지 (시험 — 가게 상관없이) */
    public static boolean isOpen(ServerPlayer sp) {
        Session s = session(sp);
        return s != null && s.open;
    }

    /** 일하는 중인지 (시험) */
    public static boolean busy(ServerPlayer sp) {
        Session s = session(sp);
        return s != null && s.busy;
    }

    /** 시험: 열린 창의 가게 (없으면 null) */
    public static String kindOf(ServerPlayer sp) {
        Session s = session(sp);
        return s == null ? null : s.kind.id();
    }

    /** 시험: 그 NPC 가 마지막으로 일을 시작한 틱 (없으면 -1) */
    public static long npcWorkTick(Entity npc) {
        Long t = npc == null ? null : NPC_WORK.get(npc.getId());
        return t == null ? -1 : t;
    }

    // ------------------------------------------------------------ 열기 · 닫기

    /** 가게 창을 엶. npc = 그 가게 NPC (없으면 null — 명령어), start = 처음 탭 (가게마다), level = 상점 레벨 (상인), done = 창을 닫으면 */
    public static void open(ServerPlayer sp, Kind kind, Entity npc, String start, int level, Runnable done) {
        Session old = session(sp);
        if (old != null && old.busy) {
            if (old.kind != kind) {
                // 다른 가게의 일이 끝나기 전 — 그 창은 그대로 (결과는 그 창 · 화면 제목으로)
                Notify.notify(sp, "§e" + old.kind.role() + "의 일이 끝나면 열 수 있습니다", 40);
                SkillFx.playFor(sp, "note.bass", 0.6, 1);
                if (done != null) done.run();
                return;
            }
            // 일하는 중에 다시 열면 같은 창 (결과는 그 창으로)
            old.open = true;
            if (npc != null) old.npc = npc;
            send(sp, old, true);
            return;
        }
        Session s = new Session(kind);
        s.npc = npc;
        s.done = done;
        s.level = level;
        kind.begin(sp, s, start);
        if (s.tab.isEmpty() || !kind.tabs(sp, s).contains(s.tab)) {
            List<String> tabs = kind.tabs(sp, s);
            s.tab = tabs.isEmpty() ? "" : tabs.get(0);
        }
        SESSIONS.put(sp.getUUID(), s);
        send(sp, s, true);
    }

    /** 창의 일 (클라이언트 → 서버): tab · page · do · sync · close */
    public static void action(ServerPlayer sp, String action, String key, int a, int b) {
        Session s = session(sp);
        if (s == null) return;
        switch (action == null ? "" : action) {
            case "tab" -> {
                if (s.busy || !s.kind.tabs(sp, s).contains(key)) return;
                s.tab = key;
                s.message = "";
                s.kind.onTab(sp, s);
                send(sp, s, false);
            }
            case "page" -> {
                if (s.busy) return;
                int n = s.kind.pages(sp, s).size();
                if (n <= 0) return;
                s.page = Math.max(0, Math.min(n - 1, a));
                s.message = "";
                send(sp, s, false);
            }
            case "do" -> start(sp, s, key, a, b == 1);
            case "sync" -> {
                s.open = true;
                send(sp, s, false);
            }
            case "close" -> {
                s.open = false;
                if (!s.busy) close(sp, s);
            }
            default -> {}
        }
    }

    private static void close(ServerPlayer sp, Session s) {
        if (SESSIONS.get(sp.getUUID()) == s) SESSIONS.remove(sp.getUUID());
        Runnable d = s.done;
        s.done = null;
        if (d != null) d.run();
    }

    /** 살아 있는 그 가게 NPC (같은 세계 · 16칸 안 — 아니면 null) */
    static Entity npcOf(ServerPlayer sp, Session s) {
        Entity n = s.npc;
        if (n == null || n.isRemoved() || !n.isAlive() || n.level() != sp.level() || n.distanceToSqr(sp) > 16 * 16) return null;
        return n;
    }

    // ------------------------------------------------------------ 일

    /** 일 시작: 칸을 다시 찾고 (재료가 있는지) → 창 패널 · 세상 NPC 동작 → ticks 뒤 판정 */
    static void start(ServerPlayer sp, Session s, String key, int variant, boolean flag) {
        if (s.busy) return;
        SmithNet.Entry e = find(s.kind.entries(sp, s), key);
        if (e == null) {
            s.message = "§c목록이 바뀌었습니다 — 다시 골라 주세요";
            send(sp, s, false);
            return;
        }
        boolean can = e.can();
        String note = e.note();
        if (!e.variants().isEmpty()) {
            if (variant < 0 || variant >= e.variants().size()) {
                s.message = "§c고를 것을 골라 주세요";
                send(sp, s, false);
                return;
            }
            can = e.variants().get(variant).can();
            note = e.variants().get(variant).note();
        }
        if (!can) {
            refuse(sp, s, note != null && !note.isEmpty() ? note : "§c재료가 모자랍니다");
            return;
        }
        Prepared p = s.kind.prepare(sp, s, key, variant, flag);
        if (p.error() != null) {
            refuse(sp, s, p.error());
            return;
        }
        s.busy = true;
        s.workKey = key;
        s.message = "";
        Entity npc = npcOf(sp, s);
        Show show = npc != null ? s.kind.show(sp, s, npc, p) : null;
        float yaw = show != null ? show.yaw() : Float.NaN;
        out(sp, new SmithNet.WorkPayload(npc != null ? npc.getId() : -1, key, p.shown().copy(), p.ticks(), p.impacts(), p.grade(), yaw, p.motion(), p.fx()));
        send(sp, s, false);
        if (npc != null) {
            NPC_WORK.put(npc.getId(), Tasks.now());
            if (!p.motion().isEmpty()) {
                MotionNet.PlayPayload mp = new MotionNet.PlayPayload(npc.getId(), p.motion(), yaw);
                for (ServerPlayer o : PlayerLookup.tracking(npc)) if (o != sp) MotionNet.send(o, mp);
            }
            if (show != null) {
                List<Integer> im = p.impacts();
                for (int i = 0; i < im.size(); i++) {
                    int k = i;
                    Tasks.later(im.get(i), () -> {
                        if (!npc.isRemoved()) show.impact(k, k == im.size() - 1);
                    });
                }
            }
        }
        Tasks.later(p.ticks(), () -> finish(sp, s, p, npc, show));
    }

    private static void refuse(ServerPlayer sp, Session s, String message) {
        SkillFx.playFor(sp, "note.bass", 0.6, 1);
        s.message = message;
        send(sp, s, false);
    }

    /** 동작이 끝난 순간: 판정 · 결과 (창 · 세상 연출) */
    private static void finish(ServerPlayer sp, Session s, Prepared p, Entity npc, Show show) {
        s.busy = false;
        if (sp.isRemoved() || sp.isDeadOrDying()) {
            if (show != null) show.end(null);
            if (SESSIONS.get(sp.getUUID()) == s) SESSIONS.remove(sp.getUUID());
            return;
        }
        SmithNet.Result r = p.job().run(++s.seq);
        s.result = r;
        if (show != null) show.end(npc != null && !npc.isRemoved() ? r : null);
        if (s.open && SESSIONS.get(sp.getUUID()) == s) send(sp, s, false);
        else {
            Notify.title(sp, r.title(), r.text());
            close(sp, s);
        }
    }

    // ------------------------------------------------------------ 보기

    /** 창을 클라이언트에 */
    static void send(ServerPlayer sp, Session s, boolean open) {
        Kind k = s.kind;
        Entity npc = npcOf(sp, s);
        String npcName = npc != null ? plain(NpcData.name(npc)) : "";
        List<String> tabs = k.tabs(sp, s);
        List<String> names = new ArrayList<>();
        for (String t : tabs) names.add(k.tabName(t));
        List<String> pages = k.pages(sp, s);
        List<SmithNet.Entry> list = k.entries(sp, s);
        out(sp, new SmithNet.ViewPayload(open, npc != null ? npc.getId() : -1, npcName, s.tab, tabs, Math.max(0, s.page), pages, list, k.res(sp, s), s.message,
                list.isEmpty() ? k.emptyText(sp, s) : "", s.result, s.busy, k.id(), k.role(), names, k.listTitle(sp, s)));
    }

    static void out(ServerPlayer sp, CustomPacketPayload p) {
        if (FORCE.contains(sp.getUUID())) SENT.computeIfAbsent(sp.getUUID(), x -> new ArrayList<>()).add(p);
        else if (SmithNet.canSend(sp)) ServerPlayNetworking.send(sp, p);
    }

    static SmithNet.Entry find(List<SmithNet.Entry> list, String key) {
        if (key == null) return null;
        for (SmithNet.Entry e : list) if (e.key().equals(key)) return e;
        return null;
    }

    /** 시험: 지금 탭의 목록 (서버가 보낼 것과 같음) */
    public static List<SmithNet.Entry> testEntries(ServerPlayer sp) {
        Session s = session(sp);
        return s == null ? List.of() : s.kind.entries(sp, s);
    }

    /** 시험: 지금 탭 */
    public static String testTab(ServerPlayer sp) {
        Session s = session(sp);
        return s == null ? null : s.tab;
    }

    /** 시험: 지금 쪽 */
    public static int testPage(ServerPlayer sp) {
        Session s = session(sp);
        return s == null ? -1 : s.page;
    }

    /** 시험: 마지막 결과 */
    public static SmithNet.Result testResult(ServerPlayer sp) {
        Session s = session(sp);
        return s == null ? SmithNet.Result.NONE : s.result;
    }

    /** 시험: 알림 줄 */
    public static String testMessage(ServerPlayer sp) {
        Session s = session(sp);
        return s == null ? "" : s.message;
    }

    // ------------------------------------------------------------ 세상 연출 도움

    /** NPC 바로 옆 (가운데끼리 range 칸 안 · 발 높이 -1 ~ +1) 의 그 블록 중 가장 가까운 것 (없으면 null — 모루 · 마법 부여대) */
    public static BlockPos blockNear(Entity npc, double range, Predicate<BlockState> match) {
        if (!(npc.level() instanceof ServerLevel level)) return null;
        BlockPos base = npc.blockPosition();
        int r = (int) Math.ceil(range);
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = base.offset(dx, dy, dz);
                    if (!match.test(level.getBlockState(p))) continue;
                    double d = npc.position().distanceToSqr(p.getX() + 0.5, npc.getY(), p.getZ() + 0.5);
                    if (d <= range * range && d < bd) {
                        bd = d;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    /** 일할 자리가 없을 때 돌아설 쪽: 몸의 오른쪽 (yaw + 90), 그 앞 reach 칸이 막혔으면 왼쪽 (둘 다 막혔으면 오른쪽) */
    public static float sideYaw(Entity npc, double reach) {
        for (float turn : new float[] {90F, -90F}) {
            float yaw = Mth.wrapDegrees(npc.getYRot() + turn);
            Vec3 f = Vec3.directionFromRotation(0, yaw);
            BlockPos p = BlockPos.containing(npc.getX() + f.x * reach, npc.getY() + 0.5, npc.getZ() + f.z * reach);
            if (npc.level().getBlockState(p).getCollisionShape(npc.level(), p).isEmpty()
                    && npc.level().getBlockState(p.above()).getCollisionShape(npc.level(), p.above()).isEmpty()) return yaw;
        }
        return Mth.wrapDegrees(npc.getYRot() + 90F);
    }

    /** 그 사람만 빼고 들리는 소리 (일하는 사람은 창에서 듣는 소리와 겹치지 않게) */
    public static void playExcept(ServerLevel level, ServerPlayer except, String bedrock, Vec3 at, double pitch, double volume) {
        SoundEvent s = SkillFx.sound(bedrock);
        if (s != null) level.playSound(except, at.x, at.y, at.z, s, SoundSource.NEUTRAL, (float) volume, (float) pitch);
    }

    /** 표시 엔티티의 변환: 엔티티 자리에서 off 만큼 옮김 · 크기 scale (돌림 없음) */
    public static Transformation at(Vec3 off, double scale) {
        return FxDisplays.of(off, new Quaternionf(), scale);
    }

    /**
     * 떠 있는 아이템 (표시 엔티티 — 보는 사람 쪽을 봄 · 밝게): at 에 처음 크기 0 → 2틱 뒤 scale 로 커짐 (life 틱 뒤 저절로 지움 — 치우기는
     * {@link #fade})
     */
    public static Display.ItemDisplay floatItem(ServerLevel level, Vec3 at, ItemStack stack, double scale, int life) {
        if (stack == null || stack.isEmpty()) return null;
        Display.ItemDisplay d = FxDisplays.item(level, at, stack.copyWithCount(1), at(Vec3.ZERO, 0.01), life, FxDisplays.light(15, 15));
        if (d == null) return null;
        // 가방 그림 (gui) 으로: RPG 무기는 손 · none 에서 3D 모델이라 정면에서 보면 옆모습 (가는 막대) — gui 는 아이콘 그대로 보는 사람 쪽
        d.setItemTransform(ItemDisplayContext.GUI);
        d.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        Tasks.later(2, () -> FxDisplays.move(d, at(Vec3.ZERO, scale), 3));
        return d;
    }

    /** 떠 있는 아이템을 ticks 동안 엔티티 자리에서 off 로 · 크기 scale */
    public static void moveItem(Display d, Vec3 off, double scale, int ticks) {
        FxDisplays.move(d, at(off, scale), ticks);
    }

    /** 떠 있는 아이템을 (엔티티 자리에서 off 인 곳에서) 작아지게 한 뒤 지움 */
    public static void fade(Display d, Vec3 off, int ticks) {
        if (d == null || d.isRemoved()) return;
        FxDisplays.move(d, at(off, 0.01), ticks);
        Tasks.later(ticks + 1, () -> FxDisplays.remove(d));
    }

    // ------------------------------------------------------------ 글

    /** 밝은 상자 창용 짙은 색 (§0 · §8 · §2 · §4 …) → 어두운 창용 */
    public static String onDark(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '§' && i + 1 < s.length()) {
                char c = s.charAt(i + 1);
                char to = switch (c) {
                    case '0' -> 'f';
                    case '8' -> '7';
                    case '2' -> 'a';
                    case '4' -> 'c';
                    case '1' -> '9';
                    case '5' -> 'd';
                    case '3' -> 'b';
                    default -> c;
                };
                b.append('§').append(to);
                i++;
            } else b.append(ch);
        }
        return b.toString();
    }

    /** 색 글자 없이 */
    public static String plain(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

    /** 모자란 것 알림 줄 ("§c실버 120 · 강화석 3 더 필요") */
    public static String lackNote(List<String> lack) {
        return lack.isEmpty() ? "" : "§c" + String.join(" · ", lack) + " 더 필요";
    }
}
