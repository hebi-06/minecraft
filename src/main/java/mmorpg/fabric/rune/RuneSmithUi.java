package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import mmorpg.core.Obj;
import mmorpg.core.RuneBreak;
import mmorpg.core.Rules;
import mmorpg.core.Runes;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.smith.SmithNet;
import mmorpg.fabric.smith.Workshop;
import mmorpg.fabric.ui.Inv;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.phys.Vec3;

/**
 * 0.45.0 룬술사 전용 창 (서버 — 사용자: "어우 UI저무 좋은데 룬술사랑 상인 같은 애들도 이렇게 바꾸자" → 고른 것: 룬술사 + 상인 · 대장장이처럼 동작).
 * 대장장이 창과 같은 창 ({@link Workshop} — 위 탭 · 왼쪽 룬 공방 패널의 룬술사 · 가운데 목록 · 오른쪽 상세 · 큰 단추):
 * <ul>
 *   <li>탭 셋: 룬 갈기 (가방의 룬 · 겹친 룬 모두 갈기) · 돌파 (룬마다 하나 · 모두 돌파) · 뽑기권 (룬 · 고급 · 한정 — 1장 · 5장). 판정 · 비용은 상자 창과 같은
 *       함수 ({@link RuneSmith#grind} · {@link Breakthrough#mergeQuiet} · {@link Breakthrough#mergeAllQuiet} · {@link RuneSmith#buyTicketQuiet} — 눈앞
 *       연출 · 알림 없이: 패널과 세상의 룬술사가 보여 줌)</li>
 *   <li>하나뿐인 룬 · 돌파한 룬 갈기 · 겹친 룬 모두 갈기 · 모두 돌파는 두 번 눌러야 함 (상자 창의 확인 창 대신 — Entry.confirm)</li>
 *   <li>단추를 누르면 룬술사가 룬을 띄워 기운을 세 번 밀어 넣음 (rune_channel — 룬이 금 가다 깨져 조각으로 · 조각이 모여 뽑기권으로) 또는 두 룬을 양손으로
 *       끌어와 합침 (rune_merge — 두 번 부딪혀 번쩍) → 끝난 뒤 판정 (룬 · 조각은 그때 가져감)</li>
 *   <li>세상: 룬술사 NPC 바로 옆 ({@link #ALTAR_RANGE} 칸) 에 마법 부여대가 있으면 그쪽을 보고 그 위에서, 없으면 옆으로 돌아서서 몸 앞에서 — 떠오르는 룬
 *       (표시 엔티티) · 바닥 마법진 · 빛 조각 · 자수정 소리 (일하는 사람은 창에서 듣고 — 둘레 사람만)</li>
 *   <li>모드가 없는 클라이언트 · 시험의 가짜 플레이어는 예전 상자 창 ({@link RuneSmith#runesmithLoop}) — {@link Workshop#FORCE} · {@link #legacy}</li>
 * </ul>
 */
public final class RuneSmithUi {
    private RuneSmithUi() {}

    private static final Obj CONF = Rules.get().root().objOrNull("RUNE_SMITH_UI");

    public static final String GRIND = "grind";
    public static final String BREAK = "break";
    public static final String TICKETS = "tickets";

    /** 사진첩: 예전 상자 창으로 (모드 클라이언트도) */
    public static boolean legacy = false;
    /** 룬술사 가게 (Workshop) */
    public static final Workshop.Kind KIND = new Arcane();

    /** 일 하나의 동작 · 틱 · 닿는 틱 (RUNE_SMITH_UI — poses.json 의 impacts × ticks 와 같아야 함) */
    public record Timing(String motion, int ticks, List<Integer> impacts) {}

    private static Timing timing(String name, String motion, int ticks, List<Integer> impacts) {
        Obj o = CONF != null ? CONF.objOrNull(name) : null;
        if (o == null) return new Timing(motion, ticks, impacts);
        List<Integer> im = new ArrayList<>();
        List<Double> ds = o.numbersOrNull("impacts");
        if (ds != null) for (double d : ds) im.add((int) d);
        return new Timing(o.strOr("motion", motion), (int) o.numOr("ticks", ticks), im.isEmpty() ? impacts : List.copyOf(im));
    }

    /** 룬 갈기: 룬을 띄워 기운을 세 번 밀어 넣음 → 깨짐 */
    public static final Timing GRIND_WORK = timing("grind", "rune_channel", 24, List.of(8, 14, 20));
    /** 돌파: 두 룬을 끌어와 두 번 부딪혀 합침 */
    public static final Timing MERGE_WORK = timing("merge", "rune_merge", 26, List.of(13, 20));
    /** 뽑기권: 조각을 모아 뽑기권으로 */
    public static final Timing TICKET_WORK = timing("ticket", "rune_channel", 24, List.of(8, 14, 20));
    /** 룬술사가 쓰는 마법 부여대까지의 거리 (NPC 와 가운데끼리 — 없으면 옆으로 돌아서서 몸 앞에서) */
    public static final double ALTAR_RANGE = CONF != null ? CONF.numOr("altarRange", 1.75) : 1.75;
    /** 마법 부여대가 없을 때 룬이 떠오르는 자리: 몸 앞 · 높이 (칸) */
    static final double REACH = 1.05;
    static final double FLOAT_Y = 1.25;

    private static final double[] PURPLE = {0.72, 0.42, 1.0};
    private static final double[] CYAN = {0.45, 0.92, 1.0};
    private static final double[] GOLD = {1.0, 0.8, 0.3};
    private static final double[] SMOKE = {0.45, 0.45, 0.5};

    /** 새 창을 쓸지 (모드 클라이언트 — 시험은 Workshop.FORCE, 사진첩은 legacy 로 예전 창) */
    public static boolean usable(ServerPlayer sp) {
        return Workshop.usable(sp, KIND);
    }

    /** 룬술사 창을 엶 (npc = 룬술사 NPC — 없으면 null, start: grind · break · tickets, done = 닫으면) */
    public static void open(ServerPlayer sp, Entity npc, String start, Runnable done) {
        Workshop.open(sp, KIND, npc, start, -1, done);
    }

    // ------------------------------------------------------------ 가게

    private static final class Arcane implements Workshop.Kind {
        @Override
        public String id() {
            return "runesmith";
        }

        @Override
        public String role() {
            return "룬술사";
        }

        @Override
        public boolean legacy() {
            return legacy;
        }

        @Override
        public List<String> tabs(ServerPlayer sp, Workshop.Session s) {
            return List.of(GRIND, BREAK, TICKETS);
        }

        @Override
        public String tabName(String tab) {
            return switch (tab) {
                case GRIND -> "룬 갈기";
                case BREAK -> "돌파";
                case TICKETS -> "뽑기권";
                default -> tab;
            };
        }

        @Override
        public void begin(ServerPlayer sp, Workshop.Session s, String start) {
            s.tab = BREAK.equals(start) ? BREAK : TICKETS.equals(start) ? TICKETS : GRIND;
        }

        @Override
        public String listTitle(ServerPlayer sp, Workshop.Session s) {
            return switch (s.tab) {
                case BREAK -> "돌파할 룬";
                case TICKETS -> "뽑기권";
                default -> "갈 룬";
            };
        }

        @Override
        public List<SmithNet.Entry> entries(ServerPlayer sp, Workshop.Session s) {
            return switch (s.tab) {
                case BREAK -> breakEntries(sp);
                case TICKETS -> ticketEntries(sp);
                default -> grindEntries(sp);
            };
        }

        @Override
        public List<SmithNet.Res> res(ServerPlayer sp, Workshop.Session s) {
            List<SmithNet.Res> out = new ArrayList<>();
            out.add(new SmithNet.Res(stack(RuneSmith.shardItem(), 1), RuneSmith.shardName(), count(sp, RuneSmith.shardItem())));
            out.add(new SmithNet.Res(RuneItems.ticket(1, false), RuneKit.RUNE.str("ticketName"), count(sp, RuneItems.TICKET)));
            out.add(new SmithNet.Res(RuneItems.ticket(1, true), RuneKit.RUNE.str("advTicketName"), count(sp, RuneItems.ADV_TICKET)));
            return out;
        }

        @Override
        public String emptyText(ServerPlayer sp, Workshop.Session s) {
            return switch (s.tab) {
                case BREAK -> "§7돌파할 룬이 없습니다\n§8같은 룬이 둘 있어야 합니다";
                case TICKETS -> "§7지금은 파는 뽑기권이 없습니다";
                default -> "§7갈 룬이 없습니다\n§8룬을 가방에 넣고 오세요";
            };
        }

        @Override
        public Workshop.Prepared prepare(ServerPlayer sp, Workshop.Session s, String key, int variant, boolean flag) {
            return RuneSmithUi.prepare(sp, key, variant);
        }

        @Override
        public Workshop.Show show(ServerPlayer sp, Workshop.Session s, Entity npc, Workshop.Prepared p) {
            return RuneSmithUi.show(sp, npc, p);
        }
    }

    private static ItemStack stack(Item it, int n) {
        return it == null ? ItemStack.EMPTY : new ItemStack(it, Math.max(1, Math.min(64, n)));
    }

    private static int count(ServerPlayer sp, Item it) {
        return it == null ? 0 : Inv.count(sp, it);
    }

    private static int gradeIndex(String grade) {
        return Math.max(0, Runes.GRADE_ORDER.indexOf(grade));
    }

    private static String runeName(Runes.Rune r, int level) {
        return r.gradeColor() + r.def().str("name") + Breakthrough.nameTag(level);
    }

    // ------------------------------------------------------------ 목록

    /** 갈기 탭: 겹친 룬 모두 갈기 (있으면 맨 위) · 가방의 룬 (겹친 룬 먼저 — RuneSmith.runeSlots) */
    static List<SmithNet.Entry> grindEntries(ServerPlayer sp) {
        List<SmithNet.Entry> out = new ArrayList<>();
        List<RuneSmith.RuneSlot> dups = RuneSmith.duplicateSlots(sp);
        String shard = RuneSmith.shardName();
        if (!dups.isEmpty()) {
            int total = RuneSmith.sumShards(dups);
            List<String> names = new ArrayList<>(new LinkedHashSet<>(dups.stream().map(e -> e.r().gradeColor() + e.r().def().str("name")).toList()));
            List<String> lines = new ArrayList<>();
            lines.add("§7같은 룬은 하나씩 남김 §8(높은 돌파 · 핫바의 것)");
            for (int i = 0; i < names.size() && lines.size() < 5; i += 2) lines.add(String.join(" §8· ", names.subList(i, Math.min(names.size(), i + 2))));
            if (names.size() > 8) lines.add("§8외 " + (names.size() - 8) + "종");
            int breakable = Breakthrough.options(sp).size();
            String warn = breakable > 0 ? "§c돌파할 수 있는 룬 " + breakable + "종도 갈립니다" : "";
            out.add(new SmithNet.Entry("dup", stack(RuneSmith.shardItem(), total), "§a겹친 룬 모두 갈기", "+" + total, 0, "한 번에", dups.size() + "개 §8· §7룬마다 하나는 남김",
                    "+" + total + " 조각", "", List.copyOf(lines), -1, List.of(), warn, -1, "모두 갈기", true, "", List.of())
                    .withConfirm("§e룬 " + dups.size() + "개를 갑니다 — 한 번 더 누르세요"));
        }
        boolean first = true;
        for (RuneSmith.RuneSlot e : RuneSmith.runeSlots(sp)) {
            if (out.size() >= SmithNet.MAX_ENTRIES) break;
            Runes.Rune r = e.r();
            int v = RuneSmith.grindValue(r, e.level());
            String where = e.slot() < 9 ? "핫바 " + (e.slot() + 1) : "가방";
            String sub = r.gradeName() + " §8· §7" + (e.dup() > 1 ? "같은 룬 " + e.dup() + "개" : "하나뿐") + " §8· §7" + where;
            List<String> lines = new ArrayList<>();
            lines.add("§7" + r.famName() + " 룬 · " + r.gradeName());
            lines.add("§7갈면 §d" + shard + " +" + v);
            if (e.level() > 0) lines.add("§e" + e.level() + "돌 룬 §7— 룬 " + (e.level() + 1) + "개 값");
            boolean only = e.dup() <= 1;
            String note = only ? "§e하나뿐인 룬 — 갈면 되돌릴 수 없습니다" : "";
            String confirm = only || e.level() > 0 ? "§c" + (only ? "하나뿐인 룬" : e.level() + "돌 룬") + "입니다 — 한 번 더 누르면 갑니다" : "";
            out.add(new SmithNet.Entry(grindKey(e), Breakthrough.stack(r.family(), r.grade(), e.level()), "§r" + runeName(r, e.level()), "+" + v, 0,
                    first ? "가방의 룬" : "", sub, "+" + v + " 조각", "", List.copyOf(lines), -1, List.of(), "", -1, "갈기", true, note, List.of()).withConfirm(confirm));
            first = false;
        }
        return out;
    }

    private static String grindKey(RuneSmith.RuneSlot e) {
        return "r:" + e.slot() + ":" + e.id() + ":" + e.level();
    }

    /** 돌파 탭: 모두 돌파 (있으면 맨 위) · 룬마다 하나 (Breakthrough.options) */
    static List<SmithNet.Entry> breakEntries(ServerPlayer sp) {
        List<SmithNet.Entry> out = new ArrayList<>();
        List<Breakthrough.Merge> all = Breakthrough.planAll(sp);
        if (!all.isEmpty()) {
            Map<String, int[]> ft = new LinkedHashMap<>(); // 룬 이름 → {처음, 끝}
            for (Breakthrough.Merge m : all) {
                Runes.Rune r = m.target().r();
                ft.computeIfAbsent(r.gradeColor() + r.def().str("name"), k -> new int[] {m.target().level(), m.result()})[1] = m.result();
            }
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, int[]> e : ft.entrySet()) parts.add(e.getKey() + " §7" + e.getValue()[0] + "→" + e.getValue()[1] + "돌");
            List<String> lines = new ArrayList<>();
            lines.add("§7룬마다 가장 높은 것 하나를 " + RuneBreak.max() + "돌을 넘지 않게");
            for (int i = 0; i < parts.size() && lines.size() < 6; i += 2) lines.add(String.join(" §8· ", parts.subList(i, Math.min(parts.size(), i + 2))));
            if (parts.size() > 10) lines.add("§8외 " + (parts.size() - 10) + "종");
            out.add(new SmithNet.Entry("ball", new ItemStack(Items.NETHER_STAR), "§a모두 돌파", ft.size() + "종", 0, "한 번에", ft.size() + "종 §8· §7합치기 " + all.size() + "번", "",
                    "", List.copyOf(lines), -1, List.of(), "§7재료 룬은 사라집니다", -1, "모두 돌파", true, "", List.of())
                    .withConfirm("§e합치기 " + all.size() + "번 — 재료 룬 " + all.size() + "개가 사라집니다. 한 번 더 누르세요"));
        }
        boolean first = true;
        for (Breakthrough.Merge m : Breakthrough.options(sp)) {
            if (out.size() >= SmithNet.MAX_ENTRIES) break;
            Runes.Rune r = m.target().r();
            List<String> lines = new ArrayList<>();
            for (String l : RuneSmith.mergeLore(m)) lines.add(l.startsWith("§r") ? l.substring(2) : l);
            out.add(new SmithNet.Entry(mergeKey(m), Breakthrough.stack(r.family(), r.grade(), m.result()), "§r" + r.gradeColor() + r.def().str("name"),
                    m.target().level() + "→" + m.result(), 0, first ? "합칠 수 있는 룬" : "", r.gradeName() + " §8· §7" + r.famName() + " 룬",
                    m.target().level() + "돌", m.result() + "돌", List.copyOf(lines), -1, List.of(), "", -1, "돌파", true, "", List.of()));
            first = false;
        }
        return out;
    }

    private static String mergeKey(Breakthrough.Merge m) {
        return "b:" + m.target().slot() + ":" + m.material().slot() + ":" + m.target().r().id() + ":" + m.target().level() + ":" + m.material().level();
    }

    /** 뽑기권 탭: 룬 뽑기권 · 고급 룬 뽑기권 · 한정 룬 뽑기권 (한정 뽑기가 열려 있을 때) — 고를 것 1장 · 5장 */
    static List<SmithNet.Entry> ticketEntries(ServerPlayer sp) {
        List<SmithNet.Entry> out = new ArrayList<>();
        int have = count(sp, RuneSmith.shardItem());
        ItemStack shardIcon = stack(RuneSmith.shardItem(), 1);
        List<RuneSmith.TicketInfo> ts = new ArrayList<>(List.of(RuneSmith.ticketInfo(false), RuneSmith.ticketInfo(true)));
        RuneSmith.TicketInfo lim = RuneSmith.limitedInfo(sp.level().getServer());
        if (lim != null) ts.add(lim);
        boolean first = true;
        for (RuneSmith.TicketInfo t : ts) {
            ItemStack icon = RuneSmith.ticketStack(t, 1);
            List<SmithNet.Variant> vs = new ArrayList<>();
            boolean any = false;
            for (int n : new int[] {1, 5}) {
                int cost = t.price() * n;
                boolean can = have >= cost;
                any |= can;
                vs.add(new SmithNet.Variant(n + "장", RuneSmith.ticketStack(t, n), List.of(new SmithNet.Cost(shardIcon, RuneSmith.shardName(), have, cost)), can,
                        can ? "" : Workshop.lackNote(List.of(RuneSmith.shardName() + " " + (cost - have))), "§7받음 " + t.color() + t.name() + " §f×" + n));
            }
            List<String> lines = new ArrayList<>();
            List<String> odds = t.odds();
            if (RuneSmith.limited(t)) for (String o : odds) lines.add("§7" + o);
            else {
                lines.add("§7" + String.join(" · ", odds.subList(0, Math.min(3, odds.size()))));
                if (odds.size() > 3) lines.add("§7" + String.join(" · ", odds.subList(3, odds.size())));
                if (t.adv()) lines.add("§8일반 룬이 나오지 않음");
            }
            String key = "t:" + (RuneSmith.limited(t) ? "l" : t.adv() ? "a" : "n");
            out.add(new SmithNet.Entry(key, icon, "§r" + t.color() + t.name(), String.valueOf(t.price()), any ? 0 : 1, first ? "룬 조각으로 사기" : "",
                    RuneSmith.shardName() + " " + t.price() + " §8· §71장", "", "", List.copyOf(lines), -1, List.of(), "", -1, "사기", any, "", List.copyOf(vs)));
            first = false;
        }
        return out;
    }

    // ------------------------------------------------------------ 할 일

    private static Workshop.Prepared work(Workshop.Job job, ItemStack shown, int grade, String fx, Timing t) {
        return new Workshop.Prepared(job, shown, grade, null, fx, t.motion(), t.ticks(), t.impacts());
    }

    private static SmithNet.Result error(int seq, String key, String text) {
        return new SmithNet.Result(seq, "error", key, "§c취소", Workshop.onDark(text), ItemStack.EMPTY);
    }

    /** 열쇠의 할 일 (그 순간의 룬 · 돌파 · 뽑기권을 잡아 둠). 열쇠: dup · r:칸:룬:단계 · ball · b:남는칸:재료칸:룬:단계:단계 · t:n|a|l */
    static Workshop.Prepared prepare(ServerPlayer sp, String key, int variant) {
        String[] k = key == null ? new String[0] : key.split(":");
        String shard = RuneSmith.shardName();
        try {
            switch (k.length > 0 ? k[0] : "") {
                case "dup" -> {
                    List<RuneSmith.RuneSlot> dups = RuneSmith.duplicateSlots(sp);
                    if (dups.isEmpty()) return Workshop.Prepared.fail("§c겹친 룬이 없습니다");
                    List<RuneSmith.Pick> picks = new ArrayList<>();
                    for (RuneSmith.RuneSlot e : dups) picks.add(new RuneSmith.Pick(e.slot(), e.id(), e.level()));
                    RuneSmith.RuneSlot top = dups.stream().max(Comparator.comparingInt(e -> gradeIndex(e.r().grade()))).orElseThrow();
                    Workshop.Job job = seq -> {
                        RuneSmith.Ground g = RuneSmith.grind(sp, picks);
                        if (g == null) return error(seq, key, "§c룬이 옮겨졌습니다 — 다시 골라 주세요");
                        return new SmithNet.Result(seq, "done", key, "§d§l룬 " + g.count() + "개를 갈았습니다", "§d" + shard + " §f+" + g.shards(),
                                stack(RuneSmith.shardItem(), g.shards()));
                    };
                    return work(job, Breakthrough.stack(top.r().family(), top.r().grade(), top.level()), gradeIndex(top.r().grade()), "grind", GRIND_WORK);
                }
                case "r" -> {
                    int slot = Integer.parseInt(k[1]);
                    RuneSmith.RuneSlot e = null;
                    for (RuneSmith.RuneSlot x : RuneSmith.runeSlots(sp)) if (grindKey(x).equals(key)) e = x;
                    if (e == null || slot != e.slot()) return Workshop.Prepared.fail("§c룬이 옮겨졌습니다 — 다시 골라 주세요");
                    RuneSmith.Pick pick = new RuneSmith.Pick(e.slot(), e.id(), e.level());
                    Workshop.Job job = seq -> {
                        RuneSmith.Ground g = RuneSmith.grind(sp, List.of(pick));
                        if (g == null) return error(seq, key, "§c룬이 옮겨졌습니다 — 다시 골라 주세요");
                        return new SmithNet.Result(seq, "done", key, "§d§l룬 갈기", g.firstName() + " §7→ §d" + shard + " §f+" + g.shards(),
                                stack(RuneSmith.shardItem(), g.shards()));
                    };
                    return work(job, Breakthrough.stack(e.r().family(), e.r().grade(), e.level()), gradeIndex(e.r().grade()), "grind", GRIND_WORK);
                }
                case "ball" -> {
                    List<Breakthrough.Merge> plan = Breakthrough.planAll(sp);
                    if (plan.isEmpty()) return Workshop.Prepared.fail("§c돌파할 룬이 없습니다");
                    Breakthrough.Merge show = plan.stream().max(Comparator.<Breakthrough.Merge>comparingInt(m -> RuneBreak.tiered(m.target().r()) ? 1 : 0)
                            .thenComparingInt(Breakthrough.Merge::result)).orElseThrow();
                    Workshop.Job job = seq -> {
                        Breakthrough.MergedAll a = Breakthrough.mergeAllQuiet(sp, plan);
                        if (a == null) return error(seq, key, "§c룬이 옮겨졌습니다 — 다시 골라 주세요");
                        Breakthrough.tierLater(sp, a.top(), a.from(), a.to());
                        List<String> parts = a.parts();
                        String list = String.join(" · ", parts.subList(0, Math.min(3, parts.size()))) + (parts.size() > 3 ? " 외 " + (parts.size() - 3) : "");
                        return new SmithNet.Result(seq, "success", key, "§b§l돌파 " + a.done() + "번", "§f" + list,
                                Breakthrough.stack(a.top().family(), a.top().grade(), a.to()));
                    };
                    Runes.Rune r = show.target().r();
                    return work(job, Breakthrough.stack(r.family(), r.grade(), show.target().level()), gradeIndex(r.grade()), "merge", MERGE_WORK);
                }
                case "b" -> {
                    Breakthrough.Merge m = null;
                    for (Breakthrough.Merge x : Breakthrough.options(sp)) if (mergeKey(x).equals(key)) m = x;
                    if (m == null) return Workshop.Prepared.fail("§c룬이 옮겨졌습니다 — 다시 골라 주세요");
                    Breakthrough.Merge mm = m;
                    Runes.Rune r = m.target().r();
                    Workshop.Job job = seq -> {
                        String err = Breakthrough.mergeQuiet(sp, mm);
                        if (err != null) return error(seq, key, err);
                        return new SmithNet.Result(seq, "success", key, "§b§l돌파! §e" + RuneBreak.stars(mm.result()), runeName(r, mm.result()) + " §7" + mm.target().level()
                                + "돌 → §e" + mm.result() + "돌 §8· §7계수 §f" + RuneBreak.coefText(mm.result()), Breakthrough.stack(r.family(), r.grade(), mm.result()));
                    };
                    return work(job, Breakthrough.stack(r.family(), r.grade(), m.target().level()), gradeIndex(r.grade()), "merge", MERGE_WORK);
                }
                case "t" -> {
                    RuneSmith.TicketInfo t = switch (k[1]) {
                        case "a" -> RuneSmith.ticketInfo(true);
                        case "l" -> RuneSmith.limitedInfo(sp.level().getServer());
                        default -> RuneSmith.ticketInfo(false);
                    };
                    if (t == null) return Workshop.Prepared.fail("§c지금은 팔지 않습니다");
                    int n = variant == 1 ? 5 : 1;
                    Workshop.Job job = seq -> {
                        String err = RuneSmith.buyTicketQuiet(sp, t, n);
                        if (err != null) return error(seq, key, err);
                        return new SmithNet.Result(seq, "done", key, t.color() + "§l" + t.name() + " ×" + n, "§7" + shard + " §f-" + t.price() * n, RuneSmith.ticketStack(t, n));
                    };
                    return work(job, RuneSmith.ticketStack(t, n), t.adv() || RuneSmith.limited(t) ? 3 : 2, "ticket", TICKET_WORK);
                }
                default -> {
                    return Workshop.Prepared.fail("§c알 수 없는 칸");
                }
            }
        } catch (RuntimeException err) {
            return Workshop.Prepared.fail("§c알 수 없는 칸");
        }
    }

    // ------------------------------------------------------------ 세상 연출 (룬술사 NPC)

    /** 세상에서 일할 자리: 룬이 떠오르는 곳 · 바닥 마법진 가운데 · NPC 가 볼 쪽 */
    record Stage(Vec3 at, Vec3 floor, float yaw) {}

    /** 둘레 마법 부여대가 있으면 그 위, 없으면 옆으로 돌아서서 몸 앞 */
    static Stage stage(Entity npc) {
        BlockPos t = Workshop.blockNear(npc, ALTAR_RANGE, st -> st.getBlock() instanceof EnchantingTableBlock);
        if (t != null) {
            Vec3 c = new Vec3(t.getX() + 0.5, t.getY(), t.getZ() + 0.5);
            float yaw = Kit.lookRotation(npc.position(), new Vec3(c.x, npc.getY(), c.z))[0];
            return new Stage(V.up(c, 1.35), V.up(c, 0.76), yaw);
        }
        float yaw = Workshop.sideYaw(npc, REACH);
        Vec3 f = Vec3.directionFromRotation(0, yaw);
        Vec3 c = npc.position().add(f.x * REACH, 0, f.z * REACH);
        return new Stage(V.up(c, FLOAT_Y), V.up(c, 0.02), yaw);
    }

    static Workshop.Show show(ServerPlayer sp, Entity npc, Workshop.Prepared p) {
        if (!(npc.level() instanceof ServerLevel level)) return null;
        Stage st = stage(npc);
        Vec3 side = V.rightOf(Vec3.directionFromRotation(0, st.yaw()));
        String grade = Runes.GRADE_ORDER.get(Math.max(0, Math.min(Runes.GRADE_ORDER.size() - 1, p.grade())));
        double[] col = RuneKit.RUNE_GRADE_FX.getOrDefault(grade, PURPLE);
        int life = p.ticks() + 40;
        double secs = p.ticks() / 20.0;
        // 바닥 마법진 (일하는 동안 돎) · 기운 소리
        SkillFx.rune(level, st.floor(), Opt.of("size", 1.25, "life", secs + 0.4, "rate", 70.0, "progress", 1.0, "color", p.fx().equals("ticket") ? GOLD : col,
                "alpha", 0.85));
        Workshop.playExcept(level, sp, "block.enchantment_table.use", st.at(), 1.1, 0.7);
        return switch (p.fx()) {
            case "merge" -> new Merging(sp, level, st, side, p, col, life);
            case "ticket" -> new Conjuring(sp, level, st, p, life);
            default -> new Grinding(sp, level, st, p, col, life);
        };
    }

    /** 갈기: 룬이 떠올라 기운을 받을 때마다 부풀다 마지막에 깨져 흩어짐 */
    private static final class Grinding implements Workshop.Show {
        final ServerPlayer sp;
        final ServerLevel level;
        final Stage st;
        final double[] col;
        final Display.ItemDisplay rune;

        Grinding(ServerPlayer sp, ServerLevel level, Stage st, Workshop.Prepared p, double[] col, int life) {
            this.sp = sp;
            this.level = level;
            this.st = st;
            this.col = col;
            this.rune = Workshop.floatItem(level, st.at(), p.shown(), 0.5, life);
        }

        @Override
        public float yaw() {
            return st.yaw();
        }

        @Override
        public void impact(int i, boolean last) {
            Vec3 at = st.at();
            if (!last) {
                // 부풀었다 돌아옴 · 빛 조각 · 맑은 소리 (한 번마다 높게)
                Workshop.moveItem(rune, Vec3.ZERO, 0.66, 1);
                Tasks.later(2, () -> Workshop.moveItem(rune, Vec3.ZERO, 0.5, 3));
                SkillFx.sparks(level, at, col, 6, 2.2);
                level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, 8, 0.25, 0.25, 0.25, 0.6);
                Workshop.playExcept(level, sp, "block.amethyst_block.chime", at, 0.9 + i * 0.18, 0.9);
                return;
            }
            // 깨짐: 룬이 사라지며 빛 조각이 사방으로
            Workshop.fade(rune, Vec3.ZERO, 2);
            SkillFx.fx(level, "rpg:magic_circle_flash", at, Opt.of("size", 0.7, "life", 0.4, "spin", Kit.rand() * 360, "progress", 1.0, "color", col, "alpha", 1.0));
            for (int k = 0; k < 12; k++) {
                double a = k / 12.0 * Math.PI * 2;
                Vec3 dir = V.unit(new Vec3(Math.cos(a), 0.25 + Kit.rand() * 0.6, Math.sin(a)));
                SkillFx.fx(level, "rpg:arrow_spark", at, Opt.of("dir", dir, "speed", 3 + Kit.rand() * 2, "size", 0.11, "grow", 0.4, "life", 0.45, "color",
                        k % 2 == 1 ? col : PURPLE, "alpha", 1.0));
            }
            Workshop.playExcept(level, sp, "block.amethyst_cluster.break", at, 1.0, 1.0);
            Workshop.playExcept(level, sp, "block.grindstone.use", at, 1.1, 0.7);
        }

        @Override
        public void end(SmithNet.Result r) {
            Workshop.fade(rune, Vec3.ZERO, 2);
            if (r != null) resultFx(sp, level, st.at(), r.kind());
        }
    }

    /** 돌파: 두 룬이 양쪽에서 다가와 부딪혀 번쩍 → 하나 */
    private static final class Merging implements Workshop.Show {
        final ServerPlayer sp;
        final ServerLevel level;
        final Stage st;
        final Vec3 side;
        final double[] col;
        final Display.ItemDisplay left;
        final Display.ItemDisplay right;

        Merging(ServerPlayer sp, ServerLevel level, Stage st, Vec3 side, Workshop.Prepared p, double[] col, int life) {
            this.sp = sp;
            this.level = level;
            this.st = st;
            this.side = side;
            this.col = col;
            this.left = Workshop.floatItem(level, V.add(st.at(), side, -0.8), p.shown(), 0.42, life);
            this.right = Workshop.floatItem(level, V.add(st.at(), side, 0.8), p.shown(), 0.42, life);
            Tasks.later(4, () -> {
                // 천천히 다가옴 (첫 부딪힘까지)
                Workshop.moveItem(left, V.scale(side, 0.45), 0.42, Math.max(1, p.impacts().isEmpty() ? 8 : p.impacts().get(0) - 5));
                Workshop.moveItem(right, V.scale(side, -0.45), 0.42, Math.max(1, p.impacts().isEmpty() ? 8 : p.impacts().get(0) - 5));
            });
        }

        @Override
        public float yaw() {
            return st.yaw();
        }

        @Override
        public void impact(int i, boolean last) {
            Vec3 at = st.at();
            if (!last) {
                // 첫 부딪힘: 튕겨 조금 물러났다 다시 다가옴
                SkillFx.burst(level, at, CYAN, 0.45, 0.25);
                SkillFx.sparks(level, at, col, 6, 2.5);
                Workshop.moveItem(left, V.scale(side, 0.3), 0.42, 2);
                Workshop.moveItem(right, V.scale(side, -0.3), 0.42, 2);
                Tasks.later(3, () -> {
                    Workshop.moveItem(left, V.scale(side, 0.8), 0.45, 4);
                    Workshop.moveItem(right, V.scale(side, -0.8), 0.45, 4);
                });
                Workshop.playExcept(level, sp, "block.amethyst_block.resonate", at, 1.0, 0.9);
                return;
            }
            // 합침: 한 룬은 사라지고 남은 룬이 가운데서 커짐 · 큰 마법진 · 고리
            Workshop.fade(right, V.scale(side, -0.8), 1);
            Workshop.moveItem(left, V.scale(side, 0.8), 0.62, 2);
            SkillFx.fx(level, "rpg:magic_circle_flash", at, Opt.of("size", 0.85, "life", 0.5, "spin", Kit.rand() * 360, "progress", 1.0, "color", col, "alpha", 1.0));
            SkillFx.burst(level, at, col, 0.9, 0.32);
            SkillFx.ring(level, at, Vec3.directionFromRotation(0, st.yaw()), CYAN, 0.7, 0.4, 2.0);
            SkillFx.sparks(level, at, col, 10, 4);
            Workshop.playExcept(level, sp, "block.amethyst_block.chime", at, 1.3, 1.0);
            Workshop.playExcept(level, sp, "block.anvil.use", at, 1.5, 0.3);
        }

        @Override
        public void end(SmithNet.Result r) {
            Workshop.fade(left, V.scale(side, 0.8), 4);
            Workshop.fade(right, V.scale(side, -0.8), 1);
            if (r != null) resultFx(sp, level, st.at(), r.kind());
        }
    }

    /** 뽑기권: 조각이 모여 들다 번쩍 → 뽑기권이 나타나 사는 사람 쪽으로 */
    private static final class Conjuring implements Workshop.Show {
        final ServerPlayer sp;
        final ServerLevel level;
        final Stage st;
        final Workshop.Prepared p;
        final Display.ItemDisplay shard;
        Display.ItemDisplay ticket;

        Conjuring(ServerPlayer sp, ServerLevel level, Stage st, Workshop.Prepared p, int life) {
            this.sp = sp;
            this.level = level;
            this.st = st;
            this.p = p;
            this.shard = Workshop.floatItem(level, st.at(), stack(RuneSmith.shardItem(), 1), 0.42, life);
        }

        @Override
        public float yaw() {
            return st.yaw();
        }

        @Override
        public void impact(int i, boolean last) {
            Vec3 at = st.at();
            if (!last) {
                Workshop.moveItem(shard, Vec3.ZERO, 0.55, 1);
                Tasks.later(2, () -> Workshop.moveItem(shard, Vec3.ZERO, 0.42, 3));
                SkillFx.sparks(level, at, PURPLE, 6, 1.8);
                level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 0.3, at.z, 10, 0.4, 0.4, 0.4, 0.8);
                Workshop.playExcept(level, sp, "block.amethyst_block.hit", at, 1.0 + i * 0.15, 0.9);
                return;
            }
            Workshop.fade(shard, Vec3.ZERO, 1);
            ticket = Workshop.floatItem(level, at, p.shown(), 0.55, 60);
            SkillFx.fx(level, "rpg:magic_circle_flash", at, Opt.of("size", 0.7, "life", 0.45, "spin", Kit.rand() * 360, "progress", 1.0, "color", GOLD, "alpha", 1.0));
            SkillFx.sparks(level, at, GOLD, 10, 3);
            Workshop.playExcept(level, sp, "block.vault.eject_item", at, 1.2, 0.9);
        }

        @Override
        public void end(SmithNet.Result r) {
            Workshop.fade(shard, Vec3.ZERO, 1);
            if (ticket != null) {
                // 사는 사람 쪽으로 건너가 사라짐
                Vec3 to = V.sub(V.up(sp.position(), 1.1), st.at());
                Workshop.moveItem(ticket, V.scale(to, 0.85), 0.3, 6);
                Display.ItemDisplay t = ticket;
                Tasks.later(7, () -> Workshop.fade(t, V.scale(to, 0.85), 2));
            }
            if (r != null) resultFx(sp, level, st.at(), r.kind());
        }
    }

    /** 결과 연출 (세상): 됨 금빛 · 돌파 하늘빛 · 취소 연기 */
    private static void resultFx(ServerPlayer sp, ServerLevel level, Vec3 at, String kind) {
        switch (kind) {
            case "success" -> {
                SkillFx.burst(level, at, CYAN, 0.8, 0.35);
                SkillFx.sparks(level, at, GOLD, 12, 4);
                SkillFx.vanilla(level, "minecraft:totem_particle", V.up(at, 0.2));
                Workshop.playExcept(level, sp, "random.levelup", at, 1.4, 0.5);
            }
            case "done" -> {
                SkillFx.burst(level, at, PURPLE, 0.7, 0.3);
                level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.03);
                Workshop.playExcept(level, sp, "random.orb", at, 1.2, 0.6);
            }
            default -> {
                SkillFx.burst(level, at, SMOKE, 0.6, 0.35);
                Workshop.playExcept(level, sp, "random.fizz", at, 0.9, 0.5);
            }
        }
    }
}
