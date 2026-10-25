package mmorpg.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 0.43.0 아티팩트 (자바 전용 규칙 덧붙임 — rules_more.json RELICS. 0.37.0 아티팩트 {@link Artifacts} 는 이름만 '장신구'). 사용자: "아티팩트는 본인이 어떻게
 * 설계 할지에 따라서 해당 스탯들이 달라지게, 총 10종류, 꽂기만 하면 스탯을 본인이 고를 수 있음, 잘 연결되면 패시브" — 고른 것: 판 위 이웃 연결 · 보조 스탯 8종 ·
 * 신화 유물 열 · 보스 · 몬스터 드랍.
 * <ul>
 * <li>종류 열 (order) × 등급 다섯 = 아이템 50, ID {@code rpg:relic_<종류>_<등급>}. 종류마다 계열 (stats — 힘 · 민첩 · 체력 · 마력 하나 또는 둘) · 패시브 하나.</li>
 * <li>판: size × size 칸 (3 × 3 = 9). 칸마다 아티팩트 하나 (같은 종류 여러 칸 가능) · 스탯 줄 하나 ({@link #lineKeys} 중 — 칸에 붙음, 언제든 바꿈).
 * 줄 값 = lines[스탯][등급] × (1 + 강화 {@link Enhance#gearBonus}).</li>
 * <li>연결 ({@link #evaluate}): 위아래 · 옆 이웃이 같은 계열이면 선 ({@link Link}). 계열마다 이어진 묶음 ({@link Group}) 의 칸 수가 tiers (3 · 5 · 7) 이상이면
 * 묶음 안 아티팩트의 패시브 단계 1 · 2 · 3 (두 계열이면 큰 쪽, 같은 종류가 여러 칸이면 가장 높은 단계 하나).</li>
 * <li>착용 레벨 (grades.level — 일반 10 ~ 초월 50): 모자란 칸은 스탯 · 연결 · 패시브 모두 없음.</li>
 * <li>패시브 위력 ({@link #power} — 드라우프니르 금고리): 룬과 같은 식 P = 레벨 기본값 × (1 + 적용 스탯 × RUNE.perPoint), 적용 스탯 = 계열 하나 × 1 + 다른 계열 × 0.5
 * 중 큰 쪽, 또는 아무 스탯 × RUNE.offStat (두 스탯 룬과 같음).</li>
 * <li>드랍 ({@link #monsterRolls} · {@link #bossRolls}): [등급, 확률] — 종류는 order 중 같은 확률.</li>
 * </ul>
 * 순수 규칙 (rules.json 만 — 대조 시험) 에는 RELICS 가 없음 → {@link #enabled()} false, 목록은 모두 빔.
 */
public final class Relics {
    private Relics() {}

    /** 아이템 ID 앞부분 */
    public static final String PREFIX = "rpg:relic_";
    /** 계열 (스탯) 순서 */
    public static final List<String> STAT_ORDER = List.of("str", "dex", "vit", "int");

    /** 패시브 하나: 이름 · 글 (자리 표시 {키} — 값이 목록이면 단계마다) · 값들 */
    public record Passive(String name, String text, Obj def) {
        /** 단계 (1 ~ 3) 의 값: 목록이면 [단계 − 1] (단계가 목록보다 크면 마지막), 숫자면 그대로, 없으면 0. 단계 0 이하는 0 */
        public double value(String key, int tier) {
            if (tier <= 0) return 0;
            return raw(key, tier);
        }

        /** 단계와 상관없는 숫자 (없으면 fallback) */
        public double num(String key, double fallback) {
            Object v = def.get(key);
            if (v instanceof Number n) return n.doubleValue();
            if (v instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Number n) return n.doubleValue();
            return fallback;
        }

        private double raw(String key, int tier) {
            Object v = def.get(key);
            if (v instanceof Number n) return n.doubleValue();
            if (v instanceof List<?> l && !l.isEmpty()) {
                Object o = l.get(Math.max(0, Math.min(l.size() - 1, tier - 1)));
                return o instanceof Number n ? n.doubleValue() : 0;
            }
            return 0;
        }

        /** 단계별로 달라지는 값인지 */
        public boolean tiered(String key) {
            return def.get(key) instanceof List<?>;
        }

        /** 글: 단계 1 ~ 3 이면 그 단계 값, 0 이면 단계마다 "a · b · c" */
        public String text(int tier) {
            StringBuilder out = new StringBuilder();
            String t = text;
            int i = 0;
            while (i < t.length()) {
                int a = t.indexOf('{', i);
                if (a < 0) {
                    out.append(t, i, t.length());
                    break;
                }
                int b = t.indexOf('}', a);
                if (b < 0) {
                    out.append(t, i, t.length());
                    break;
                }
                out.append(t, i, a);
                String key = t.substring(a + 1, b);
                if (tier <= 0 && tiered(key)) {
                    StringJoiner j = new StringJoiner(" · ");
                    for (Object o : (List<?>) def.get(key)) j.add(o instanceof Number n ? Js.str(n.doubleValue()) : String.valueOf(o));
                    out.append(j);
                } else {
                    out.append(Js.str(raw(key, Math.max(1, tier))));
                }
                i = b + 1;
            }
            return out.toString();
        }
    }

    /** 종류 하나: 키 · 이름 (메긴교르드) · 별칭 (힘의 허리띠) · 설명 · 계열 (스탯 키 하나 또는 둘) · 패시브 */
    public record Type(String key, String name, String title, String desc, List<String> stats, Passive passive) {
        public boolean has(String stat) {
            return stats.contains(stat);
        }
    }

    /** 아티팩트 하나 (종류 × 등급): id · 종류 · 등급 · 등급 순번 (0 일반 ~ 4 초월) · 착용 레벨 */
    public record Relic(String id, String type, String grade, int gradeIndex, int level) {
        public Type def() {
            return Relics.type(type);
        }

        /** 이름 (메긴교르드 — 색 없음) */
        public String name() {
            Type t = def();
            return t == null ? type : t.name();
        }

        /** 등급 색 + 이름 (강화 n &gt; 0 이면 "+n 이름") */
        public String coloredName(int enhance) {
            return gradeColor() + (enhance > 0 ? "+" + enhance + " " : "") + name();
        }

        public String coloredName() {
            return coloredName(0);
        }

        /** 밝은 바탕 (상자 창 본문) 용 */
        public String darkName(int enhance) {
            Grade g = Rules.get().grade(grade);
            return (g == null ? "§0" : g.dark()) + (enhance > 0 ? "+" + enhance + " " : "") + name();
        }

        public String gradeName() {
            Grade g = Rules.get().grade(grade);
            return g == null ? grade : g.name();
        }

        public String gradeColor() {
            Grade g = Rules.get().grade(grade);
            return g == null ? "§f" : g.color();
        }
    }

    /** 판의 칸 하나 (평가에 넣는 것): 아이템 ID (빈 칸 null) · 고른 스탯 줄 · 강화 단계 */
    public record Cell(String relicId, String stat, int enhance) {
        public static final Cell EMPTY = new Cell(null, null, 0);
    }

    /** 이웃 두 칸 (a &lt; b) 을 잇는 선 — 그 계열 */
    public record Link(int a, int b, String stat) {}

    /** 한 계열로 이어진 묶음 (칸 번호 오름차순) */
    public record Group(String stat, List<Integer> cells) {
        public int size() {
            return cells.size();
        }
    }

    /**
     * 판 평가: 칸마다 쓸 수 있는지 (아티팩트가 있고 착용 레벨이 됨) · 선 · 묶음 · 칸마다 단계 (그 칸이 든 묶음 중 가장 높은) · 종류마다 패시브 단계 (판에 있는 종류 모두 —
     * 0 = 꺼짐) · 스탯 줄 합 (상한 전)
     */
    public record Board(List<Cell> cells, boolean[] usable, List<Link> links, List<Group> groups, int[] cellTier, Map<String, Integer> tiers,
            Map<String, Double> totals) {
        /** 이 종류의 패시브 단계 (없거나 꺼졌으면 0) */
        public int tier(String type) {
            Integer t = tiers.get(type);
            return t == null ? 0 : t;
        }

        /** 켜진 패시브 수 */
        public int active() {
            int n = 0;
            for (int t : tiers.values()) if (t > 0) n++;
            return n;
        }

        /** 꽂은 칸 수 (아티팩트가 있는 칸 — 착용 레벨과 상관없이) */
        public int filled() {
            int n = 0;
            for (Cell c : cells) if (c != null && byId(c.relicId()) != null) n++;
            return n;
        }
    }

    /** 드랍 한 번: 등급 · 확률 (1 이상 = 반드시) */
    public record Roll(String grade, double chance) {}

    private record Band(double min, double max, List<Roll> rolls) {}

    private record Data(int size, List<Integer> tiers, Map<String, Integer> statColors, Map<String, Integer> levels, Map<String, Boolean> glint,
            Map<String, List<Double>> lines, List<String> order, Map<String, Type> types, Map<String, Relic> relics, List<Band> monsterDrops,
            Map<String, List<Roll>> bossDrops) {}

    private static final Data DATA = load();

    private static Data load() {
        Rules r = Rules.get();
        Obj root = r.root().objOrNull("RELICS");
        if (root == null) {
            return new Data(0, List.of(), Map.of(), Map.of(), Map.of(), Map.of(), List.of(), Map.of(), Map.of(), List.of(), Map.of());
        }
        Obj board = root.obj("board");
        int size = (int) board.num("size");
        if (size < 1 || size > 5) throw new IllegalStateException("RELICS.board.size " + size);
        List<Integer> tiers = new ArrayList<>();
        for (Double d : board.numbersOrNull("tiers")) tiers.add((int) Math.round(d));
        for (int i = 1; i < tiers.size(); i++) if (tiers.get(i) <= tiers.get(i - 1)) throw new IllegalStateException("RELICS.board.tiers must rise " + tiers);
        Map<String, Integer> colors = new LinkedHashMap<>();
        Obj sc = root.obj("statColors");
        for (String s : STAT_ORDER) colors.put(s, Integer.parseInt(sc.str(s), 16));
        Map<String, Integer> levels = new LinkedHashMap<>();
        Map<String, Boolean> glint = new LinkedHashMap<>();
        Obj gs = root.obj("grades");
        for (Grade g : r.grades()) {
            Obj o = gs.obj(g.id());
            levels.put(g.id(), (int) o.num("level"));
            glint.put(g.id(), o.truthy("glint"));
        }
        Map<String, List<Double>> lines = new LinkedHashMap<>();
        Obj ls = root.obj("lines");
        for (String k : ls.keys()) {
            if (k.startsWith("_")) continue;
            if (StatTree.kind(k) == null) throw new IllegalStateException("RELICS.lines: unknown kind " + k);
            List<Double> v = ls.numbersOrNull(k);
            if (v == null || v.size() != r.grades().size()) throw new IllegalStateException("RELICS.lines." + k + " needs " + r.grades().size() + " values");
            lines.put(k, List.copyOf(v));
        }
        List<String> order = new ArrayList<>();
        for (Object o : root.list("order")) order.add(String.valueOf(o));
        Map<String, Type> types = new LinkedHashMap<>();
        Obj ts = root.obj("types");
        for (String k : order) {
            Obj t = ts.obj(k);
            List<String> stats = new ArrayList<>();
            for (Object o : t.list("stats")) {
                String s = String.valueOf(o);
                if (!STAT_ORDER.contains(s)) throw new IllegalStateException("RELICS." + k + ": unknown stat " + s);
                stats.add(s);
            }
            if (stats.isEmpty() || stats.size() > 2) throw new IllegalStateException("RELICS." + k + ": stats must be 1 or 2");
            Obj p = t.obj("passive");
            types.put(k, new Type(k, t.str("name"), t.strOr("title", ""), t.strOr("desc", ""), List.copyOf(stats), new Passive(p.str("name"), p.str("text"), p)));
        }
        for (String k : ts.keys()) if (!k.startsWith("_") && !types.containsKey(k)) throw new IllegalStateException("RELICS.types." + k + " is not in order");
        Map<String, Relic> relics = new LinkedHashMap<>();
        for (Type t : types.values()) {
            for (Grade g : r.grades()) {
                String id = PREFIX + t.key() + "_" + g.id();
                relics.put(id, new Relic(id, t.key(), g.id(), g.index(), levels.get(g.id())));
            }
        }
        List<Band> bands = new ArrayList<>();
        Map<String, List<Roll>> bosses = new LinkedHashMap<>();
        Obj drops = root.objOrNull("drops");
        if (drops != null) {
            List<Obj> ms = drops.objsOrNull("monsters");
            if (ms != null) {
                for (Obj b : ms) bands.add(new Band(b.num("minLevel"), b.num("maxLevel"), rolls(b.list("rolls"), r)));
            }
            Obj bs = drops.objOrNull("bosses");
            if (bs != null) for (String k : bs.keys()) if (!k.startsWith("_")) bosses.put(k, rolls(bs.list(k), r));
        }
        return new Data(size, List.copyOf(tiers), Collections.unmodifiableMap(colors), Collections.unmodifiableMap(levels), Collections.unmodifiableMap(glint),
                Collections.unmodifiableMap(lines), List.copyOf(order), Collections.unmodifiableMap(types), Collections.unmodifiableMap(relics), List.copyOf(bands),
                Collections.unmodifiableMap(bosses));
    }

    private static List<Roll> rolls(List<Object> list, Rules r) {
        List<Roll> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof List<?> pair) || pair.size() != 2) throw new IllegalStateException("RELICS.drops: roll must be [grade, chance] — " + o);
            String g = String.valueOf(pair.get(0));
            if (r.grade(g) == null) throw new IllegalStateException("RELICS.drops: unknown grade " + g);
            out.add(new Roll(g, ((Number) pair.get(1)).doubleValue()));
        }
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------ 데이터

    /** 규칙에 아티팩트 (RELICS) 가 있는지 */
    public static boolean enabled() {
        return !DATA.relics().isEmpty();
    }

    /** 판 한 변 (3) */
    public static int size() {
        return DATA.size();
    }

    /** 판 칸 수 (9 — 없으면 0) */
    public static int cells() {
        return DATA.size() * DATA.size();
    }

    /** 단계 문턱 (묶음 칸 수 — 3 · 5 · 7) */
    public static List<Integer> tierThresholds() {
        return DATA.tiers();
    }

    /** 가장 높은 단계 (3) */
    public static int maxTier() {
        return DATA.tiers().size();
    }

    /** 묶음 칸 수 n 의 단계 (0 = 패시브 없음) */
    public static int tierOf(int n) {
        int t = 0;
        for (int need : DATA.tiers()) if (n >= need) t++;
        return t;
    }

    /** 다음 단계까지 필요한 묶음 칸 수 (이미 가장 높으면 -1) */
    public static int nextNeed(int n) {
        for (int need : DATA.tiers()) if (n < need) return need;
        return -1;
    }

    /** 계열 선 색 (0xRRGGBB) */
    public static int statColor(String stat) {
        Integer c = DATA.statColors().get(stat);
        return c == null ? 0xFFFFFF : c;
    }

    /** 계열 이름 (힘) */
    public static String statName(String stat) {
        Obj o = Stats.info(stat);
        return o == null ? stat : o.str("name");
    }

    /** 계열 글자 색 (§c) */
    public static String statCode(String stat) {
        Obj o = Stats.info(stat);
        return o == null ? "§f" : o.str("color");
    }

    /** 계열 글자 ("§c힘 §7· §9마력") */
    public static String statsText(Type t) {
        StringJoiner j = new StringJoiner(" §7· ");
        for (String s : t.stats()) j.add(statCode(s) + statName(s));
        return j.toString();
    }

    /** 스탯 줄 종류 순서 (공격력 · 치명타 확률 · 치명타 피해 · 룬 피해 · 재사용 대기 · 최대 체력 · 받는 피해 · 이동 속도) */
    public static List<String> lineKeys() {
        return List.copyOf(DATA.lines().keySet());
    }

    /** 고른 적이 없거나 맞지 않는 스탯이면 쓰는 줄 (첫째 — 공격력) */
    public static String defaultLine() {
        return DATA.lines().isEmpty() ? "atk" : DATA.lines().keySet().iterator().next();
    }

    /** 스탯 줄 키가 맞는지 */
    public static boolean isLine(String stat) {
        return stat != null && DATA.lines().containsKey(stat);
    }

    /** 스탯 줄 값 (등급 순번 · 강화 단계) */
    public static double lineValue(String stat, int gradeIndex, int enhance) {
        List<Double> v = DATA.lines().get(stat);
        if (v == null || v.isEmpty()) return 0;
        double base = v.get(Math.max(0, Math.min(v.size() - 1, gradeIndex)));
        return base * (1 + Enhance.gearBonus(enhance));
    }

    /** 종류 순서 (order) */
    public static List<String> types() {
        return DATA.order();
    }

    public static Type type(String key) {
        return key == null ? null : DATA.types().get(key);
    }

    /** 이 계열을 가진 종류들 (order 순) */
    public static List<String> typesWith(String stat) {
        List<String> out = new ArrayList<>();
        for (Type t : DATA.types().values()) if (t.has(stat)) out.add(t.key());
        return out;
    }

    /** 모든 아티팩트 (종류 순서 × 등급 순서) */
    public static List<Relic> all() {
        return List.copyOf(DATA.relics().values());
    }

    public static String id(String type, String grade) {
        return PREFIX + type + "_" + grade;
    }

    public static Relic relic(String type, String grade) {
        return DATA.relics().get(id(type, grade));
    }

    /** 아이템 ID 의 아티팩트 (아니면 null) */
    public static Relic byId(String id) {
        return id == null ? null : DATA.relics().get(id);
    }

    /** 등급의 착용 레벨 (없으면 0) */
    public static int level(String grade) {
        Integer v = DATA.levels().get(grade);
        return v == null ? 0 : v;
    }

    public static boolean glint(String grade) {
        return DATA.glint().getOrDefault(grade, false);
    }

    /** 이 레벨 (렙제 레벨) 로 쓸 수 있는지 */
    public static boolean usable(Relic a, double level) {
        return a != null && level >= a.level();
    }

    // ------------------------------------------------------------------ 판

    /** 칸 i 의 위아래 · 옆 이웃 (판 안) */
    public static List<Integer> neighbors(int i) {
        int n = DATA.size();
        List<Integer> out = new ArrayList<>(4);
        if (n <= 0 || i < 0 || i >= n * n) return out;
        int r = i / n;
        int c = i % n;
        if (r > 0) out.add(i - n);
        if (c > 0) out.add(i - 1);
        if (c < n - 1) out.add(i + 1);
        if (r < n - 1) out.add(i + n);
        return out;
    }

    /** 판 평가 (cells — 칸 순서, 모자라면 빈 칸) */
    public static Board evaluate(List<Cell> cells, double level) {
        int n = cells();
        List<Cell> cs = new ArrayList<>(n);
        boolean[] usable = new boolean[n];
        Relic[] rel = new Relic[n];
        for (int i = 0; i < n; i++) {
            Cell c = cells != null && i < cells.size() && cells.get(i) != null ? cells.get(i) : Cell.EMPTY;
            cs.add(c);
            rel[i] = byId(c.relicId());
            usable[i] = usable(rel[i], level);
        }
        List<Link> links = new ArrayList<>();
        List<Group> groups = new ArrayList<>();
        int[] cellTier = new int[n];
        Map<String, Integer> tiers = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) if (usable[i]) tiers.putIfAbsent(rel[i].type(), 0);
        for (String s : STAT_ORDER) {
            boolean[] in = new boolean[n];
            for (int i = 0; i < n; i++) in[i] = usable[i] && rel[i].def() != null && rel[i].def().has(s);
            for (int i = 0; i < n; i++) {
                if (!in[i]) continue;
                for (int j : neighbors(i)) if (j > i && in[j]) links.add(new Link(i, j, s));
            }
            boolean[] seen = new boolean[n];
            for (int i = 0; i < n; i++) {
                if (!in[i] || seen[i]) continue;
                List<Integer> comp = new ArrayList<>();
                Deque<Integer> q = new ArrayDeque<>();
                q.add(i);
                seen[i] = true;
                while (!q.isEmpty()) {
                    int x = q.poll();
                    comp.add(x);
                    for (int y : neighbors(x)) {
                        if (in[y] && !seen[y]) {
                            seen[y] = true;
                            q.add(y);
                        }
                    }
                }
                Collections.sort(comp);
                groups.add(new Group(s, List.copyOf(comp)));
                int t = tierOf(comp.size());
                for (int x : comp) {
                    cellTier[x] = Math.max(cellTier[x], t);
                    tiers.merge(rel[x].type(), t, Math::max);
                }
            }
        }
        Map<String, Double> totals = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            if (!usable[i]) continue;
            Cell c = cs.get(i);
            String stat = isLine(c.stat()) ? c.stat() : defaultLine();
            totals.merge(stat, lineValue(stat, rel[i].gradeIndex(), c.enhance()), Double::sum);
        }
        return new Board(Collections.unmodifiableList(cs), usable, Collections.unmodifiableList(links), Collections.unmodifiableList(groups), cellTier,
                Collections.unmodifiableMap(tiers), Collections.unmodifiableMap(totals));
    }

    // ------------------------------------------------------------------ 패시브 위력

    /** 패시브 적용 스탯: 계열 하나 × 1 + 다른 계열 × 0.5 중 큰 쪽, 또는 아무 스탯 × RUNE.offStat */
    public static double applied(Map<String, Double> stats, Type t) {
        double best = 0;
        if (t != null) {
            for (String a : t.stats()) {
                double v = Stats.get(stats, a);
                for (String b : t.stats()) if (!b.equals(a)) v += Stats.get(stats, b) * 0.5;
                best = Math.max(best, v);
            }
        }
        double off = Rules.get().obj("RUNE").numOr("offStat", 0.85);
        for (String k : STAT_ORDER) best = Math.max(best, Stats.get(stats, k) * off);
        return best;
    }

    /** 패시브 위력 P = 레벨 기본값 (룬과 같음) × (1 + 적용 스탯 × RUNE.perPoint) */
    public static double power(Map<String, Double> stats, Type t) {
        return Runes.runeLevelBase(Stats.get(stats, "lvmax")) * (1 + applied(stats, t) * Rules.get().obj("RUNE").num("perPoint"));
    }

    // ------------------------------------------------------------------ 드랍

    /** 이 레벨 몬스터의 드랍 굴림 (구간 밖이면 빈 목록) */
    public static List<Roll> monsterRolls(double level) {
        for (Band b : DATA.monsterDrops()) if (level >= b.min() && level <= b.max()) return b.rolls();
        return List.of();
    }

    /** 이 보스의 드랍 굴림 (보상을 받는 사람마다 — 없으면 빈 목록) */
    public static List<Roll> bossRolls(String boss) {
        List<Roll> r = boss == null ? null : DATA.bossDrops().get(boss);
        return r == null ? List.of() : r;
    }

    /** 보스 드랍 표가 있는 보스들 */
    public static List<String> dropBosses() {
        return List.copyOf(DATA.bossDrops().keySet());
    }

    /** 난수 (0 이상 1 미만) 로 고른 종류 (order 중 같은 확률) */
    public static String typeFor(double r) {
        List<String> o = DATA.order();
        if (o.isEmpty()) return null;
        int i = (int) Math.floor(Math.max(0, Math.min(0.999999, r)) * o.size());
        return o.get(i);
    }

    // ------------------------------------------------------------------ 글자

    /** 스탯 줄 글자 ("공격력 +1.5%") */
    public static String lineText(String stat, double value) {
        return StatTree.bonusText(stat, value);
    }

    /** 스탯 줄 이름 (공격력) */
    public static String lineName(String stat) {
        StatTree.Kind k = StatTree.kind(stat);
        return k == null ? stat : k.name();
    }

    /** 패시브 한 줄 ("「두 배의 힘」 평타 5번째마다 …") — tier 0 이면 단계마다 값 */
    public static String passiveText(Type t, int tier) {
        if (t == null) return "";
        return "「" + t.passive().name() + "」 " + t.passive().text(tier);
    }

    /** 단계 문턱 글 ("3 · 5 · 7칸") */
    public static String tiersText() {
        StringJoiner j = new StringJoiner(" · ");
        for (int t : DATA.tiers()) j.add(String.valueOf(t));
        return j + "칸";
    }

    /**
     * 아이템 설명 (§r 로 시작): 별칭 · 등급 · 착용 레벨 (level &lt; 0 = 모름), 계열, 패시브 (단계마다), 연결 안내, 이 등급 (강화 포함) 의 스탯 줄 값, 설명
     */
    public static List<String> lore(Relic a, int enhance, double level) {
        List<String> out = new ArrayList<>();
        if (a == null) return out;
        Type t = a.def();
        out.add("§r§6아티팩트 §7· " + (t == null ? "" : t.title()));
        boolean ok = level < 0 || usable(a, level);
        out.add("§r" + a.gradeColor() + a.gradeName() + " §r§7· " + (ok ? "§7" : "§c") + "착용 " + a.level() + "렙"
                + (ok || level < 0 ? "" : " §8(지금 " + Js.str(Math.floor(level)) + ")") + (enhance > 0 ? " §7· §e강화 +" + enhance : ""));
        if (t == null) return out;
        out.add("§r§7계열: " + statsText(t));
        out.add("§r§e패시브 「" + t.passive().name() + "」");
        out.add("§r§7" + t.passive().text(0));
        out.add("§r§8같은 계열이 " + tiersText() + " 이어지면 1 · 2 · 3단계");
        List<String> keys = lineKeys();
        StringJoiner a1 = new StringJoiner(" · ");
        StringJoiner a2 = new StringJoiner(" · ");
        for (int i = 0; i < keys.size(); i++) {
            String s = lineText(keys.get(i), lineValue(keys.get(i), a.gradeIndex(), enhance));
            if (i < (keys.size() + 1) / 2) a1.add(s);
            else a2.add(s);
        }
        out.add("§r§b스탯 줄 (판에서 하나 고름)");
        out.add("§r§7" + a1);
        out.add("§r§7" + a2);
        if (!t.desc().isEmpty()) out.add("§r§8" + t.desc());
        return out;
    }
}
