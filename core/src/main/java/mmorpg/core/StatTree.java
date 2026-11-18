package mmorpg.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

/**
 * 스탯 트리 (7단계 — 애드온에는 없는 자바 판 기능, 2026-11-18 축복의 길): 스탯마다 나무 하나, 그 스탯에 찍은 포인트 (장비 · 룬 버프 제외) 가 칸의
 * {@code at} 이상이면 칸이 저절로 열림. 갈래 칸 (pick) 은 둘 중 하나를 골라야 보너스 · 공용 스킬이 붙음. 갈래 묶음 (split) 은 칸 여럿이 한 갈래 —
 * 한 번 고르면 묶음의 모든 칸이 같은 쪽 (기록도 묶음의 모든 칸에). 데이터는 {@code /mmorpg/stat_tree.json}.
 *
 * <p>이 클래스는 순수 계산 (서버 · 클라이언트 · 코어 시험이 같이 씀): 열린 칸 수, 보너스 합, 연 공용 스킬, 받을 보상, 초기화 비용, 글자.
 * 플레이어 기록은 {@link State} (고른 갈래 · 나무마다 보상을 받은 칸 수).
 */
public final class StatTree {
    private StatTree() {}

    public static final String RESOURCE = "/mmorpg/stat_tree.json";

    /** 보너스 종류 (kinds): 이름 · 단위 ("%", "%p", "") · 줄이는 쪽인지 (받는 피해 · 재사용 대기) · 그림 · 풀이 */
    public record Kind(String key, String name, String unit, boolean neg, String icon, String desc) {}

    /** 갈래 하나: 이름 · 보너스 · 공용 스킬 id (없으면 null — 2026-11-18 축복의 길) */
    public record Option(String name, Map<String, Double> bonus, String skill) {}

    /**
     * 칸 하나 (index = 0부터, 화면의 "n단계" = index + 1): at = 열리는 스탯 값, bonus = 늘 붙는 보너스, reward = 처음 한 번 받는 것
     * (rune · adv · stat → 장 수), pick = 갈래 (없으면 빈 목록), split = 갈래 묶음 번호 (0 = 묶음 없음 — 칸 하나가 갈래 하나),
     * first = 그 묶음의 첫 칸 index (묶음이 없으면 index)
     */
    public record Node(int index, int at, String name, Map<String, Double> bonus, Map<String, Integer> reward, List<Option> pick, int split, int first) {
        public boolean fork() {
            return !pick.isEmpty();
        }

        /** 갈래를 처음 고르는 칸 (묶음의 첫 칸 — 묶음이 없으면 갈래 칸 모두) */
        public boolean firstOfSplit() {
            return first == index;
        }
    }

    /**
     * 나무 하나: 스탯 · 이름 · 색 (0 ~ 1) · 칸 (at 순서) · 신 · 공용 스킬의 원소 (fire · thunder · ice · rock — 없으면 "") ·
     * 갈래 묶음마다 두 길의 이름
     */
    public record Tree(String stat, String title, double[] color, List<Node> nodes, String god, String element, Map<Integer, List<String>> branches) {
        /** 칸 index 가 든 갈래 묶음의 칸 (차례대로 — 묶음이 없으면 그 칸 하나) */
        public List<Integer> splitOf(int index) {
            Node n = nodes.get(index);
            if (n.split() <= 0) return List.of(index);
            List<Integer> out = new ArrayList<>();
            for (Node m : nodes) if (m.split() == n.split()) out.add(m.index());
            return out;
        }

        /** 칸 n 의 갈래 option 쪽 길 이름 (묶음의 이름 — 없으면 그 칸 갈래의 이름) */
        public String branchName(Node n, int option) {
            List<String> b = branches.get(n.split());
            if (b != null && option >= 0 && option < b.size()) return b.get(option);
            return option >= 0 && option < n.pick().size() ? n.pick().get(option).name() : "";
        }
    }

    /**
     * 공용 스킬 (2026-11-18 축복의 길 — 다섯 신의 축복): id · 이름 · 나무 스탯 · 종류 (move 이동기 · attack 작은 공격) · 원소 (없으면 "") ·
     * 재사용 (초) · 피해 합 (그 나무 스탯의 룬 위력 P 의 배 — 이동기는 0) · 풀이
     */
    public record Skill(String id, String name, String stat, String kind, String element, double cd, double mult, String text) {
        public boolean attack() {
            return "attack".equals(kind);
        }
    }

    /** 스탯 룬 뽑기권: 아이템 ID · 이름 · 나오는 룬 계열 */
    public record Ticket(String stat, String item, String name, String family) {}

    private static final Data DATA = load();

    private record Data(Map<String, Tree> trees, Map<String, Kind> kinds, Map<String, Ticket> tickets, Map<String, Double> caps, int perPick,
                        Map<String, Skill> skills) {}

    // ------------------------------------------------------------------ 데이터

    /** 나무 순서 (창의 탭 순서) */
    public static List<String> stats() {
        return List.copyOf(DATA.trees().keySet());
    }

    /** 스탯의 나무 (없으면 null) */
    public static Tree tree(String stat) {
        return DATA.trees().get(stat);
    }

    /** 보너스 종류 (순서 = 데이터 순서) */
    public static Map<String, Kind> kinds() {
        return DATA.kinds();
    }

    public static Kind kind(String key) {
        return DATA.kinds().get(key);
    }

    /** 스탯 룬 뽑기권 (스탯 → 뽑기권) */
    public static Map<String, Ticket> tickets() {
        return DATA.tickets();
    }

    /** 갈래 하나를 지우는 값 (에메랄드) */
    public static int perPick() {
        return DATA.perPick();
    }

    /** 나무 넷을 더한 합의 상한 (없으면 상한 없음) */
    public static Map<String, Double> caps() {
        return DATA.caps();
    }

    /** 공용 스킬 (데이터 차례 — 없으면 빈 것) */
    public static Map<String, Skill> skillDefs() {
        return DATA.skills();
    }

    /** 공용 스킬 하나 (없으면 null) */
    public static Skill skill(String id) {
        return id == null ? null : DATA.skills().get(id);
    }

    // ------------------------------------------------------------------ 계산

    /** 찍은 스탯 값 spent 로 열린 칸 수 (0 ~ 칸 수) */
    public static int tier(String stat, double spent) {
        Tree t = tree(stat);
        if (t == null) return 0;
        int n = 0;
        for (Node node : t.nodes()) {
            if (spent >= node.at()) n++;
            else break;
        }
        return n;
    }

    /** 다음 칸이 열리는 스탯 값 (다 열렸으면 -1) */
    public static int nextAt(String stat, double spent) {
        Tree t = tree(stat);
        int k = tier(stat, spent);
        return t == null || k >= t.nodes().size() ? -1 : t.nodes().get(k).at();
    }

    /** 한 나무의 보너스 합 (열린 칸 · 고른 갈래만 — 상한 없음). picks = 칸 index → 고른 갈래 */
    public static Map<String, Double> treeTotals(String stat, double spent, Map<Integer, Integer> picks) {
        Map<String, Double> out = new LinkedHashMap<>();
        Tree t = tree(stat);
        if (t == null) return out;
        int k = tier(stat, spent);
        for (int i = 0; i < k; i++) add(out, active(t.nodes().get(i), picks));
        return out;
    }

    /** 나무 넷의 보너스 합 (상한 caps 적용). spent = 찍은 스탯 {str, dex, int, vit} */
    public static Map<String, Double> totals(Map<String, Double> spent, State state) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (String stat : stats()) add(out, treeTotals(stat, Stats.get(spent, stat), state.picksOf(stat)));
        for (Map.Entry<String, Double> e : DATA.caps().entrySet()) out.computeIfPresent(e.getKey(), (k, v) -> Math.min(v, e.getValue()));
        return out;
    }

    /** 칸의 지금 보너스 (갈래면 고른 것, 안 골랐으면 없음) */
    public static Map<String, Double> active(Node node, Map<Integer, Integer> picks) {
        if (!node.fork()) return node.bonus();
        Integer p = picks == null ? null : picks.get(node.index());
        return p == null || p < 0 || p >= node.pick().size() ? Map.of() : node.pick().get(p).bonus();
    }

    private static void add(Map<String, Double> into, Map<String, Double> b) {
        for (Map.Entry<String, Double> e : b.entrySet()) into.merge(e.getKey(), e.getValue(), Double::sum);
    }

    /** 열렸는데 아직 고르지 않은 갈래 (index — 묶음은 첫 칸 하나) */
    public static List<Integer> pending(String stat, double spent, Map<Integer, Integer> picks) {
        List<Integer> out = new ArrayList<>();
        Tree t = tree(stat);
        if (t == null) return out;
        int k = tier(stat, spent);
        for (int i = 0; i < k; i++) {
            Node n = t.nodes().get(i);
            if (n.fork() && n.firstOfSplit() && (picks == null || !picks.containsKey(i))) out.add(i);
        }
        return out;
    }

    /** 한 나무에서 연 공용 스킬 (열린 갈래 칸 · 고른 쪽의 skill, 칸 차례) */
    public static List<String> openSkills(String stat, double spent, Map<Integer, Integer> picks) {
        List<String> out = new ArrayList<>();
        Tree t = tree(stat);
        if (t == null || picks == null) return out;
        int k = tier(stat, spent);
        for (int i = 0; i < k; i++) {
            Node n = t.nodes().get(i);
            Integer p = n.fork() ? picks.get(i) : null;
            if (p == null || p < 0 || p >= n.pick().size()) continue;
            String s = n.pick().get(p).skill();
            if (s != null) out.add(s);
        }
        return out;
    }

    /** 나무 넷에서 연 공용 스킬 (나무 차례 · 칸 차례) */
    public static List<String> openSkills(Map<String, Double> spent, State state) {
        List<String> out = new ArrayList<>();
        for (String stat : stats()) out.addAll(openSkills(stat, Stats.get(spent, stat), state.picksOf(stat)));
        return out;
    }

    /** 칸 from 다음부터 to 까지 (index from ~ to-1 — 열린 칸 수로) 의 보상 합: 아이템 ID → 장 수 (데이터 순서) */
    public static Map<String, Integer> rewards(String stat, int fromTier, int toTier) {
        Map<String, Integer> out = new LinkedHashMap<>();
        Tree t = tree(stat);
        if (t == null) return out;
        for (int i = Math.max(0, fromTier); i < Math.min(toTier, t.nodes().size()); i++) {
            for (Map.Entry<String, Integer> e : t.nodes().get(i).reward().entrySet()) {
                String item = rewardItem(stat, e.getKey());
                if (item != null && e.getValue() > 0) out.merge(item, e.getValue(), Integer::sum);
            }
        }
        return out;
    }

    /** 보상 이름 (rune · adv · stat) → 아이템 ID (모르면 null) */
    public static String rewardItem(String stat, String key) {
        Obj rune = Rules.get().obj("RUNE");
        switch (key) {
            case "rune":
                return rune.str("ticketItem");
            case "adv":
                return rune.str("advTicketItem");
            case "stat": {
                Ticket tk = DATA.tickets().get(stat);
                if (tk != null && !Runes.FAMILY_ORDER.contains(tk.family())) return rune.str("ticketItem"); // 2026-11-17 룬 재개발 (계열 없음): 스탯 뽑기권 대신 룬 뽑기권
                return tk == null ? null : tk.item();
            }
            default:
                return null;
        }
    }

    /** 아이템 ID → 보상 이름 ("룬 뽑기권" …, 모르면 ID) */
    public static String itemName(String item) {
        Obj rune = Rules.get().obj("RUNE");
        if (item.equals(rune.str("ticketItem"))) return rune.str("ticketName");
        if (item.equals(rune.str("advTicketItem"))) return rune.str("advTicketName");
        for (Ticket tk : DATA.tickets().values()) if (tk.item().equals(item)) return tk.name();
        return item;
    }

    /** 갈래 초기화 비용 (고른 갈래 수 × perPick) */
    public static int resetCost(int picks) {
        return Math.max(0, picks) * DATA.perPick();
    }

    // ------------------------------------------------------------------ 글자

    /** 숫자 (소수 둘째 자리까지, 끝의 0은 뺌) */
    static String num(double v) {
        return Js.str(Js.round(v * 100) / 100);
    }

    /** "공격력 +2%" · "받는 피해 -4%" · "최대 체력 +10" · "치명타 확률 +1%p" */
    public static String bonusText(String kind, double value) {
        Kind k = kind(kind);
        if (k == null) return kind + " " + num(value);
        return k.name() + " " + (k.neg() ? "-" : "+") + num(value) + k.unit();
    }

    /** 보너스 여러 개 (" · " 로) — 없으면 "" */
    public static String bonusText(Map<String, Double> bonus) {
        StringJoiner j = new StringJoiner(" · ");
        for (Map.Entry<String, Double> e : bonus.entrySet()) if (e.getValue() != 0) j.add(bonusText(e.getKey(), e.getValue()));
        return j.toString();
    }

    /** 보상 글자 ("힘 룬 뽑기권 ×1 · 룬 뽑기권 ×1") — 없으면 "" */
    public static String rewardText(String stat, Map<String, Integer> reward) {
        StringJoiner j = new StringJoiner(" · ");
        for (Map.Entry<String, Integer> e : reward.entrySet()) {
            String item = rewardItem(stat, e.getKey());
            if (item != null) j.add(itemName(item) + " ×" + e.getValue());
        }
        return j.toString();
    }

    /** 칸 이름 (갈래는 "광전사 / 강철 피부") */
    public static String nodeName(Node node) {
        if (!node.fork()) return node.name();
        StringJoiner j = new StringJoiner(" / ");
        for (Option o : node.pick()) j.add(o.name());
        return j.toString();
    }

    // ------------------------------------------------------------------ 플레이어 기록

    /**
     * 플레이어의 스탯 트리 기록: picks = 스탯 → (칸 index → 고른 갈래 — 묶음은 묶음의 모든 칸에 같은 쪽), claimed = 스탯 → 보상을 받은 칸 수
     * (가장 높이 열렸던 칸 — 스탯을 되돌렸다가 다시 찍어도 보상은 다시 주지 않음).
     * JSON (2판 — 2026-11-18 축복의 길): {"v":2,"picks":{"str":{"1":0,"2":0,"3":0,"4":0}},"claimed":{"str":4}}.
     * 1판 (v 없음 — 칸 10 · 갈래 3 · 6 · 9) 은 읽을 때 옮김: 고른 갈래는 지움 (칸 자리가 바뀜 — 한 번 무료로 다시 고름), 받은 칸 수 c 는 1판 문턱 10c 로
     * 새 나무의 칸 수로 (보상 문턱은 같음 — 다시 주지 않음)
     */
    public record State(Map<String, Map<Integer, Integer>> picks, Map<String, Integer> claimed) {
        public static final State EMPTY = new State(Map.of(), Map.of());

        public Map<Integer, Integer> picksOf(String stat) {
            Map<Integer, Integer> m = picks.get(stat);
            return m == null ? Map.of() : m;
        }

        public int claimedOf(String stat) {
            return claimed.getOrDefault(stat, 0);
        }

        /** 고른 갈래 수 (그 나무 — 묶음 하나는 하나로 셈) */
        public int pickCount(String stat) {
            Tree t = tree(stat);
            int n = 0;
            for (Integer idx : picksOf(stat).keySet()) {
                if (t == null || idx < 0 || idx >= t.nodes().size() || t.nodes().get(idx).firstOfSplit()) n++;
            }
            return n;
        }

        /** 갈래를 고름: 칸 index 가 든 묶음의 모든 칸에 같은 쪽 (묶음의 어느 칸에서 골라도) */
        public State withPick(String stat, int index, int option) {
            Map<String, Map<Integer, Integer>> p = copyPicks();
            Map<Integer, Integer> m = p.computeIfAbsent(stat, k -> new TreeMap<>());
            Tree t = tree(stat);
            if (t != null && index >= 0 && index < t.nodes().size()) {
                for (int i : t.splitOf(index)) m.put(i, option);
            } else {
                m.put(index, option);
            }
            return new State(freeze(p), claimed);
        }

        public State withoutPicks(String stat) {
            Map<String, Map<Integer, Integer>> p = copyPicks();
            p.remove(stat);
            return new State(freeze(p), claimed);
        }

        public State withClaimed(String stat, int tier) {
            Map<String, Integer> c = new LinkedHashMap<>(claimed);
            c.put(stat, tier);
            return new State(picks, Collections.unmodifiableMap(c));
        }

        private Map<String, Map<Integer, Integer>> copyPicks() {
            Map<String, Map<Integer, Integer>> p = new LinkedHashMap<>();
            for (Map.Entry<String, Map<Integer, Integer>> e : picks.entrySet()) p.put(e.getKey(), new TreeMap<>(e.getValue()));
            return p;
        }

        private static Map<String, Map<Integer, Integer>> freeze(Map<String, Map<Integer, Integer>> p) {
            Map<String, Map<Integer, Integer>> out = new LinkedHashMap<>();
            for (Map.Entry<String, Map<Integer, Integer>> e : p.entrySet()) {
                if (!e.getValue().isEmpty()) out.put(e.getKey(), Collections.unmodifiableMap(new TreeMap<>(e.getValue())));
            }
            return Collections.unmodifiableMap(out);
        }

        /** JSON 글자 (2판) */
        public String json() {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("v", 2);
            Map<String, Object> p = new LinkedHashMap<>();
            for (Map.Entry<String, Map<Integer, Integer>> e : picks.entrySet()) {
                Map<String, Object> m = new LinkedHashMap<>();
                for (Map.Entry<Integer, Integer> x : e.getValue().entrySet()) m.put(String.valueOf(x.getKey()), x.getValue());
                p.put(e.getKey(), m);
            }
            root.put("picks", p);
            root.put("claimed", new LinkedHashMap<>(claimed));
            return Json.stringify(root);
        }

        /** JSON 글자 → 기록 (없거나 깨졌으면 빈 기록 · 1판은 옮김 — 위 풀이) */
        public static State parse(String json) {
            if (json == null || json.isBlank()) return EMPTY;
            try {
                Obj root = Obj.of(Json.parse(json), "stat_tree_state");
                Double ver = root.numOrNull("v");
                boolean v1 = ver == null || ver < 2;
                Map<String, Map<Integer, Integer>> p = new LinkedHashMap<>();
                Obj picks = v1 ? null : root.objOrNull("picks");
                if (picks != null) {
                    for (String stat : picks.keys()) {
                        Obj m = picks.objOrNull(stat);
                        if (m == null) continue;
                        Map<Integer, Integer> one = new TreeMap<>();
                        for (String idx : m.keys()) {
                            Double v = m.numOrNull(idx);
                            if (v != null) one.put(Integer.parseInt(idx), v.intValue());
                        }
                        p.put(stat, one);
                    }
                }
                Map<String, Integer> c = new LinkedHashMap<>();
                Obj claimed = root.objOrNull("claimed");
                if (claimed != null) {
                    for (String stat : claimed.keys()) {
                        Double v = claimed.numOrNull(stat);
                        if (v == null) continue;
                        // 1판: 받은 칸 수 c = 1판 문턱 10 · c 까지 → 새 나무에서 그 문턱까지 열리는 칸 수
                        c.put(stat, v1 ? tier(stat, v.intValue() * 10.0) : v.intValue());
                    }
                }
                State s = new State(Map.of(), Collections.unmodifiableMap(c));
                // 묶음은 모든 칸에 같은 쪽으로 (손으로 쓴 기록 · 첫 칸만 적힌 기록도)
                for (Map.Entry<String, Map<Integer, Integer>> e : p.entrySet()) {
                    for (Map.Entry<Integer, Integer> x : e.getValue().entrySet()) s = s.withPick(e.getKey(), x.getKey(), x.getValue());
                }
                return s;
            } catch (RuntimeException e) {
                return EMPTY;
            }
        }
    }

    // ------------------------------------------------------------------ 읽기

    private static Data load() {
        Map<String, Object> json;
        try (InputStream in = StatTree.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException(RESOURCE + " is not on the classpath");
            json = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + RESOURCE, e);
        }
        Obj root = new Obj(json, "stat_tree");
        Map<String, Kind> kinds = new LinkedHashMap<>();
        Obj ks = root.obj("kinds");
        for (String k : ks.keys()) {
            Obj o = ks.obj(k);
            kinds.put(k, new Kind(k, o.str("name"), o.strOr("unit", ""), o.truthy("neg"), o.strOr("icon", ""), o.strOr("desc", "")));
        }
        Map<String, Ticket> tickets = new LinkedHashMap<>();
        Obj ts = root.obj("tickets");
        for (String stat : ts.keys()) {
            Obj o = ts.obj(stat);
            tickets.put(stat, new Ticket(stat, o.str("item"), o.str("name"), o.str("family")));
        }
        Map<String, Double> caps = new LinkedHashMap<>();
        Obj cs = root.objOrNull("caps");
        if (cs != null) for (String k : cs.keys()) caps.put(k, cs.num(k));
        // 2026-11-18 공용 스킬 (축복의 길)
        Map<String, Skill> skills = new LinkedHashMap<>();
        Obj sk = root.objOrNull("skills");
        if (sk != null) {
            for (String id : sk.keys()) {
                Obj o = sk.obj(id);
                String kind = o.str("kind");
                if (!kind.equals("move") && !kind.equals("attack")) throw new IllegalStateException("stat_tree.skills." + id + ".kind " + kind);
                skills.put(id, new Skill(id, o.str("name"), o.str("stat"), kind, o.strOr("element", ""), o.num("cd"), o.numOr("mult", 0), o.strOr("text", "")));
            }
        }
        Map<String, Tree> trees = new LinkedHashMap<>();
        Obj tr = root.obj("trees");
        for (String stat : tr.keys()) {
            Obj o = tr.obj(stat);
            List<Double> col = o.numbersOrNull("color");
            double[] color = col == null || col.size() < 3 ? new double[] {1, 1, 1} : new double[] {col.get(0), col.get(1), col.get(2)};
            List<Obj> raw = o.objsOrNull("nodes");
            if (raw == null) throw new IllegalStateException("stat_tree.trees." + stat + ".nodes missing");
            List<Node> nodes = new ArrayList<>();
            Map<Integer, Integer> firstOf = new LinkedHashMap<>();
            int lastAt = Integer.MIN_VALUE;
            int lastSplit = 0;
            for (int i = 0; i < raw.size(); i++) {
                Obj n = raw.get(i);
                String where = "stat_tree.trees." + stat + ".nodes[" + i + "]";
                int at = (int) n.num("at");
                if (at <= lastAt) throw new IllegalStateException(where + ".at must grow");
                lastAt = at;
                List<Option> pick = new ArrayList<>();
                List<Obj> ps = n.objsOrNull("pick");
                if (ps != null) {
                    for (Obj p : ps) {
                        String skill = p.strOrNull("skill");
                        if (skill != null && !skills.containsKey(skill)) throw new IllegalStateException(where + " unknown skill " + skill);
                        pick.add(new Option(p.str("name"), bonusOf(p.objOrNull("bonus"), kinds), skill));
                    }
                }
                int split = (int) n.numOr("split", 0);
                if (split > 0) {
                    if (pick.isEmpty()) throw new IllegalStateException(where + ".split needs pick");
                    // 묶음은 이어진 칸 (사이에 다른 칸이 끼면 다른 묶음 번호여야 함)
                    if (firstOf.containsKey(split) && lastSplit != split) throw new IllegalStateException(where + ".split " + split + " not contiguous");
                    firstOf.putIfAbsent(split, i);
                }
                lastSplit = split;
                Map<String, Integer> reward = new LinkedHashMap<>();
                Obj rw = n.objOrNull("reward");
                if (rw != null) for (String k : rw.keys()) reward.put(k, (int) rw.num(k));
                String name = n.strOr("name", pick.isEmpty() ? (i + 1) + "단계" : "갈래");
                nodes.add(new Node(i, at, name, bonusOf(n.objOrNull("bonus"), kinds), Collections.unmodifiableMap(reward), List.copyOf(pick), split,
                        split > 0 ? firstOf.get(split) : i));
            }
            Map<Integer, List<String>> branches = new LinkedHashMap<>();
            Obj br = o.objOrNull("branches");
            if (br != null) {
                for (String k : br.keys()) {
                    List<String> names = new ArrayList<>();
                    for (Object x : br.list(k)) names.add(String.valueOf(x));
                    branches.put(Integer.parseInt(k), List.copyOf(names));
                }
            }
            trees.put(stat, new Tree(stat, o.strOr("title", stat), color, List.copyOf(nodes), o.strOr("god", ""), o.strOr("element", ""),
                    Collections.unmodifiableMap(branches)));
        }
        Obj reset = root.objOrNull("reset");
        int perPick = reset == null ? 0 : (int) reset.numOr("perPick", 0);
        return new Data(Collections.unmodifiableMap(trees), Collections.unmodifiableMap(kinds), Collections.unmodifiableMap(tickets),
                Collections.unmodifiableMap(caps), perPick, Collections.unmodifiableMap(skills));
    }

    private static Map<String, Double> bonusOf(Obj o, Map<String, Kind> kinds) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (o == null) return out;
        for (String k : o.keys()) {
            if (!kinds.containsKey(k)) throw new IllegalStateException("stat_tree: unknown bonus kind " + k);
            out.put(k, o.num(k));
        }
        return Collections.unmodifiableMap(out);
    }
}
