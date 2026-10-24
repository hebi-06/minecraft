package mmorpg.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 0.37.0 아티팩트 (자바 전용 규칙 덧붙임 — rules_more.json ARTIFACTS, 사용자: "아티팩트 추가 — 반지1 반지2 목걸이 귀걸이 팔찌, 등급 나눠서,
 * 각 아티팩트마다 올려주는게 다름"):
 * <ul>
 * <li>종류 넷 (반지 · 목걸이 · 귀걸이 · 팔찌) × 등급 다섯 (일반 ~ 초월 — GRADES 순서) = 아이템 20, ID {@code rpg:artifact_<종류>_<등급>}.</li>
 * <li>칸 다섯 (slots — 반지 1 · 반지 2 · 목걸이 · 귀걸이 · 팔찌): 칸마다 그 종류만.</li>
 * <li>옵션: 종류마다 둘, 같은 등급 같은 종류는 늘 같은 수치 (types.bonus — 등급 순서의 값). 보너스 종류는 스탯 트리와 같음 ({@link StatTree#kinds}) —
 * 스탯 트리 합에 더한 뒤 같은 상한 ({@link #combine}).</li>
 * <li>착용 레벨: 등급마다 (grades.level — 일반 10 · 희귀 20 · 고귀 30 · 영웅 40 · 초월 50). 레벨이 모자란 아티팩트는 칸에 있어도 보너스 없음.</li>
 * <li>제작 비용: 등급마다 (모든 종류 같음 — cost: 실버 · 골드 · 재료).</li>
 * </ul>
 * 순수 규칙 (rules.json 만 — 대조 시험) 에는 ARTIFACTS 가 없음 → {@link #enabled()} false, 목록은 모두 빔.
 */
public final class Artifacts {
    private Artifacts() {}

    /** 아이템 ID 앞부분 */
    public static final String PREFIX = "rpg:artifact_";

    /** 종류 하나: 키 · 이름 (반지) · 설명 · 옵션 (보너스 종류 → 등급 순서의 값) */
    public record Type(String key, String name, String desc, Map<String, List<Double>> bonus) {}

    /**
     * 아티팩트 하나 (종류 × 등급): id · 종류 · 등급 · 등급 순번 (0 일반 ~ 4 초월) · 착용 레벨 · 이름 ("청옥 반지" — 색 없음) · 옵션 (보너스 종류 → 값, 적힌 순서)
     */
    public record Art(String id, String type, String grade, int gradeIndex, int level, String name, Map<String, Double> bonus) {
        /** 등급 색 + 이름 (아이템 이름 · 창 — 밝은 바탕용 아님) */
        public String coloredName() {
            Grade g = Rules.get().grade(grade);
            return (g == null ? "§f" : g.color()) + name;
        }

        /** 밝은 바탕 (상자 창 본문) 용 색 + 이름 */
        public String darkName() {
            Grade g = Rules.get().grade(grade);
            return (g == null ? "§0" : g.dark()) + name;
        }

        /** 등급 이름 (희귀) */
        public String gradeName() {
            Grade g = Rules.get().grade(grade);
            return g == null ? grade : g.name();
        }

        /** 등급 색 (§9 …) */
        public String gradeColor() {
            Grade g = Rules.get().grade(grade);
            return g == null ? "§f" : g.color();
        }

        /** 종류 이름 (반지) */
        public String typeName() {
            Type t = Artifacts.type(type);
            return t == null ? type : t.name();
        }
    }

    /** 제작 비용 (등급마다): 실버 · 골드 · 재료 (아이템 ID → 수, 적힌 순서) */
    public record Cost(int silver, int gold, Map<String, Integer> items) {}

    private record Data(List<String> slots, List<String> slotNames, Map<String, Type> types, Map<String, Integer> levels, Map<String, String> prefixes,
            Map<String, Boolean> glint, Map<String, Cost> costs, Map<String, Art> arts) {}

    private static final Data DATA = load();

    private static Data load() {
        Rules r = Rules.get();
        Obj root = r.root().objOrNull("ARTIFACTS");
        if (root == null) {
            return new Data(List.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        List<String> slots = new ArrayList<>();
        for (Object o : root.list("slots")) slots.add(String.valueOf(o));
        List<String> slotNames = new ArrayList<>();
        for (Object o : root.list("slotNames")) slotNames.add(String.valueOf(o));
        if (slotNames.size() != slots.size()) throw new IllegalStateException("ARTIFACTS: slotNames " + slotNames.size() + " != slots " + slots.size());
        Map<String, Type> types = new LinkedHashMap<>();
        Obj ts = root.obj("types");
        for (String k : ts.keys()) {
            if (k.startsWith("_")) continue;
            Obj t = ts.obj(k);
            Map<String, List<Double>> bonus = new LinkedHashMap<>();
            Obj b = t.obj("bonus");
            for (String kind : b.keys()) {
                if (kind.startsWith("_")) continue;
                if (StatTree.kind(kind) == null) throw new IllegalStateException("ARTIFACTS: unknown bonus kind " + kind + " (" + k + ")");
                List<Double> vals = b.numbersOrNull(kind);
                if (vals == null || vals.size() != r.grades().size()) throw new IllegalStateException("ARTIFACTS: " + k + "." + kind + " needs " + r.grades().size() + " values");
                bonus.put(kind, List.copyOf(vals));
            }
            types.put(k, new Type(k, t.str("name"), t.strOr("desc", ""), Collections.unmodifiableMap(bonus)));
        }
        for (String s : slots) if (!types.containsKey(s)) throw new IllegalStateException("ARTIFACTS: slot type " + s + " is not in types");
        Map<String, Integer> levels = new LinkedHashMap<>();
        Map<String, String> prefixes = new LinkedHashMap<>();
        Map<String, Boolean> glint = new LinkedHashMap<>();
        Obj gs = root.obj("grades");
        for (Grade g : r.grades()) {
            Obj o = gs.obj(g.id());
            levels.put(g.id(), (int) o.num("level"));
            prefixes.put(g.id(), o.str("prefix"));
            glint.put(g.id(), o.truthy("glint"));
        }
        Map<String, Cost> costs = new LinkedHashMap<>();
        Obj cs = root.obj("cost");
        for (Grade g : r.grades()) {
            Obj c = cs.obj(g.id());
            Map<String, Integer> items = new LinkedHashMap<>();
            Obj it = c.objOrNull("items");
            if (it != null) for (String k : it.keys()) if (!k.startsWith("_")) items.put(k, (int) it.num(k));
            costs.put(g.id(), new Cost((int) c.numOr("silver", 0), (int) c.numOr("gold", 0), Collections.unmodifiableMap(items)));
        }
        Map<String, Art> arts = new LinkedHashMap<>();
        for (Type t : types.values()) {
            for (Grade g : r.grades()) {
                Map<String, Double> bonus = new LinkedHashMap<>();
                for (Map.Entry<String, List<Double>> e : t.bonus().entrySet()) bonus.put(e.getKey(), e.getValue().get(g.index()));
                String id = PREFIX + t.key() + "_" + g.id();
                arts.put(id, new Art(id, t.key(), g.id(), g.index(), levels.get(g.id()), prefixes.get(g.id()) + " " + t.name(), Collections.unmodifiableMap(bonus)));
            }
        }
        return new Data(List.copyOf(slots), List.copyOf(slotNames), Collections.unmodifiableMap(types), Collections.unmodifiableMap(levels),
                Collections.unmodifiableMap(prefixes), Collections.unmodifiableMap(glint), Collections.unmodifiableMap(costs), Collections.unmodifiableMap(arts));
    }

    // ------------------------------------------------------------------ 데이터

    /** 규칙에 아티팩트가 있는지 */
    public static boolean enabled() {
        return !DATA.arts().isEmpty();
    }

    /** 칸 수 (5 — 없으면 0) */
    public static int size() {
        return DATA.slots().size();
    }

    /** 칸 순서의 종류 (ring · ring · necklace · earring · bracelet) */
    public static List<String> slots() {
        return DATA.slots();
    }

    /** 칸 i 의 종류 (없는 칸이면 null) */
    public static String slotType(int i) {
        return i < 0 || i >= DATA.slots().size() ? null : DATA.slots().get(i);
    }

    /** 칸 i 의 이름 ("반지 1") */
    public static String slotName(int i) {
        return i < 0 || i >= DATA.slotNames().size() ? "" : DATA.slotNames().get(i);
    }

    /** 이 종류가 들어가는 칸 번호들 (반지 = [0, 1]) */
    public static List<Integer> slotsFor(String type) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < DATA.slots().size(); i++) if (DATA.slots().get(i).equals(type)) out.add(i);
        return out;
    }

    /** 종류 순서 (ring · necklace · earring · bracelet) */
    public static List<String> types() {
        return List.copyOf(DATA.types().keySet());
    }

    public static Type type(String key) {
        return DATA.types().get(key);
    }

    /** 모든 아티팩트 (종류 순서 × 등급 순서) */
    public static List<Art> all() {
        return List.copyOf(DATA.arts().values());
    }

    /** 아이템 ID */
    public static String id(String type, String grade) {
        return PREFIX + type + "_" + grade;
    }

    /** 종류 × 등급 의 아티팩트 (없으면 null) */
    public static Art art(String type, String grade) {
        return DATA.arts().get(id(type, grade));
    }

    /** 아이템 ID 의 아티팩트 (아티팩트가 아니면 null) */
    public static Art byId(String id) {
        return id == null ? null : DATA.arts().get(id);
    }

    /** 등급의 착용 레벨 (없으면 0) */
    public static int level(String grade) {
        Integer v = DATA.levels().get(grade);
        return v == null ? 0 : v;
    }

    /** 등급의 이름 앞말 (청옥) */
    public static String prefix(String grade) {
        return DATA.prefixes().getOrDefault(grade, "");
    }

    /** 반짝이는 등급인지 (초월) */
    public static boolean glint(String grade) {
        return DATA.glint().getOrDefault(grade, false);
    }

    /** 등급의 제작 비용 (없으면 null) */
    public static Cost cost(String grade) {
        return DATA.costs().get(grade);
    }

    // ------------------------------------------------------------------ 계산

    /** 이 아티팩트를 이 레벨 (렙제 레벨 — Stats.reqLevelOf) 로 쓸 수 있는지 */
    public static boolean usable(Art a, double level) {
        return a != null && level >= a.level();
    }

    /** 칸 i 에 이 아티팩트를 놓을 수 있는지 (종류가 맞음) */
    public static boolean fits(int slot, Art a) {
        return a != null && a.type().equals(slotType(slot));
    }

    /**
     * 칸들 (칸 순서의 아이템 ID — 빈 칸은 null · "") 의 보너스 합: 칸의 종류가 맞고 착용 레벨이 되는 것만 (상한 없음 — {@link #combine} 이 적용)
     */
    public static Map<String, Double> totals(List<String> slotIds, double level) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (slotIds == null) return out;
        for (int i = 0; i < Math.min(slotIds.size(), size()); i++) {
            Art a = byId(slotIds.get(i));
            if (!fits(i, a) || !usable(a, level)) continue;
            for (Map.Entry<String, Double> e : a.bonus().entrySet()) out.merge(e.getKey(), e.getValue(), Double::sum);
        }
        return out;
    }

    /** 스탯 트리 합 + 아티팩트 합 → 상한 (stat_tree.json caps) 적용 (둘 다 없으면 빈 지도) */
    public static Map<String, Double> combine(Map<String, Double> tree, Map<String, Double> art) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (tree != null) for (Map.Entry<String, Double> e : tree.entrySet()) out.merge(e.getKey(), e.getValue(), Double::sum);
        if (art != null) for (Map.Entry<String, Double> e : art.entrySet()) out.merge(e.getKey(), e.getValue(), Double::sum);
        for (Map.Entry<String, Double> e : StatTree.caps().entrySet()) out.computeIfPresent(e.getKey(), (k, v) -> Math.min(v, e.getValue()));
        return out;
    }

    // ------------------------------------------------------------------ 글자

    /** 옵션 줄 ["공격력 +4%", "치명타 확률 +3%p"] */
    public static List<String> optionLines(Art a) {
        List<String> out = new ArrayList<>();
        if (a == null) return out;
        for (Map.Entry<String, Double> e : a.bonus().entrySet()) out.add(StatTree.bonusText(e.getKey(), e.getValue()));
        return out;
    }

    /** 옵션 한 줄 ("공격력 +4% · 치명타 확률 +3%p") */
    public static String optionText(Art a) {
        return String.join(" · ", optionLines(a));
    }

    /**
     * 아이템 설명 (§r 로 시작 — 아이템 · 상자 창 칸 공용): 종류 · 칸, 등급 · 착용 레벨 (level &lt; 0 = 모름 → 레벨 비교 없음), 옵션, 설명
     */
    public static List<String> lore(Art a, double level) {
        List<String> out = new ArrayList<>();
        if (a == null) return out;
        Type t = type(a.type());
        List<Integer> where = slotsFor(a.type());
        out.add("§r§d아티팩트 §7· " + a.typeName() + (where.size() > 1 ? " §8(칸 " + where.size() + "개)" : ""));
        boolean ok = level < 0 || usable(a, level);
        out.add("§r" + a.gradeColor() + a.gradeName() + " §r§7· " + (ok ? "§7" : "§c") + "착용 " + a.level() + "렙"
                + (ok || level < 0 ? "" : " §8(지금 " + Js.str(Math.floor(level)) + ")"));
        for (String line : optionLines(a)) out.add("§r§a" + line);
        if (t != null && !t.desc().isEmpty()) out.add("§r§8" + t.desc());
        return out;
    }
}
