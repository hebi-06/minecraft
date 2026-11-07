package mmorpg.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 2026-11-07 원소 반응 (사용자 "원소를 서로 조합해서 쓰면 조합에 맞게 원소 반응이 터져서 물 + 전기면 기절, 불 + 물이면 즉시피해" · "해당 원소피해가 있는 스킬들은
 * 딜이 진짜 살짝 낮음 대신 원소를 부착해줌 그리고 애는 룬하고 직업 스킬에도 들어감" · "무속성 피해 … 원소 부착 스킬보다 좀 더 쎈 대신에 원소 반응을 못 줌"):
 * rules_more.json ELEMENTS — 원소 다섯 (불 · 물 · 전기 · 얼음 · 바위) · 반응 열 (두 원소, 순서 없음). 원소 스킬 (룬 RUNE_SPREAD.runes 의 elem · 직업 스킬 jobs.json
 * 의 elem) 은 피해 × (1 − tax) 대신 맞은 적에 원소를 묻힘 — 다른 원소가 묻은 적을 치면 반응 (서버 ElementSystem). 무속성 (elem 없음) 은 그대로.
 * 대조 시험 (rules.json 만 — ELEMENTS 없음) 이면 원소가 없음 (모두 무속성 · 배율 1)
 */
public final class Elements {
    private Elements() {}

    /** 원소: id · 이름 ("불") · 글자 색 (§c) · 빛 · 심지 (0 ~ 1) · 머리 위 그림 칸 (rfx_jelem) */
    public record Element(String id, String name, String text, double[] glow, double[] core, int cell) {
        /** 색 글자 ("§c불") */
        public String label() {
            return text + name;
        }
    }

    /** 반응: 열쇠 ("fire+water") · id (vaporize) · 이름 (증발) · 종류 (hit · blast · stun · root · vuln · field · pull) · 두 원소 · 정의 (power · radius · ticks …) */
    public record Reaction(String key, String id, String name, String kind, String a, String b, Obj def) {
        public double num(String k, double fallback) {
            return def.numOr(k, fallback);
        }

        /** 한 줄 ("증발 · 즉시 피해") */
        public String desc() {
            return def.strOr("desc", "");
        }
    }

    /** 스킬이 타마다 원소를 정함 (삼원소 연격 · 원소 해방 · 정령왕 강림 · 포비든 그리모어 — 쓰는 동안의 기본 원소는 없음) */
    public static final String MULTI = "multi";

    private static final Obj E = Rules.get().root().objOrNull("ELEMENTS");
    private static final Map<String, Element> LIST = list();
    private static final Map<String, Reaction> REACT = readReactions();

    private static Map<String, Element> list() {
        Map<String, Element> out = new LinkedHashMap<>();
        if (E == null) return Map.of();
        Obj l = E.obj("list");
        for (Object o : E.list("order")) {
            String id = String.valueOf(o);
            Obj d = l.obj(id);
            List<Object> c = d.list("color");
            out.put(id, new Element(id, d.str("name"), d.strOr("text", "§f"), rgb(c.get(0)), rgb(c.get(1)), (int) d.num("cell")));
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    private static Map<String, Reaction> readReactions() {
        Map<String, Reaction> out = new LinkedHashMap<>();
        if (E == null) return Map.of();
        Obj r = E.obj("reactions");
        for (String k : r.keys()) {
            if (k.startsWith("_")) continue;
            String[] ab = k.split("\\+");
            if (ab.length != 2 || !LIST.containsKey(ab[0]) || !LIST.containsKey(ab[1]) || !key(ab[0], ab[1]).equals(k))
                throw new IllegalStateException("ELEMENTS.reactions: bad key " + k);
            Obj d = r.obj(k);
            out.put(k, new Reaction(k, d.str("id"), d.str("name"), d.str("kind"), ab[0], ab[1], d));
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    private static double[] rgb(Object o) {
        List<?> l = (List<?>) o;
        return new double[] {((Number) l.get(0)).doubleValue(), ((Number) l.get(1)).doubleValue(), ((Number) l.get(2)).doubleValue()};
    }

    /** 원소 표가 있는지 (대조 시험이면 없음) */
    public static boolean on() {
        return E != null;
    }

    /** 원소 스킬이 덜 주는 몫 (0.05 — 무속성보다 5% 약함) */
    public static double tax() {
        return E == null ? 0 : E.numOr("tax", 0);
    }

    /** 원소가 묻어 있는 틱 (120 = 6초) */
    public static int auraTicks() {
        return E == null ? 0 : (int) E.numOr("aura", 120);
    }

    /** 같은 적에 반응이 다시 터질 수 있을 때까지 틱 (40 = 2초) */
    public static int icdTicks() {
        return E == null ? 0 : (int) E.numOr("icd", 40);
    }

    /** 원소 id 순서 (불 · 물 · 전기 · 얼음 · 바위) */
    public static List<String> order() {
        return List.copyOf(LIST.keySet());
    }

    /** 원소 (없으면 null) */
    public static Element get(String id) {
        return id == null ? null : LIST.get(id);
    }

    /** 다섯 원소 중 하나인지 */
    public static boolean known(String id) {
        return id != null && LIST.containsKey(id);
    }

    /** 반응 열쇠 (abc 순 — "fire+water") */
    public static String key(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "+" + b : b + "+" + a;
    }

    /** 두 원소의 반응 (같은 원소 · 모르는 원소 · 표가 없으면 null) */
    public static Reaction reaction(String a, String b) {
        if (!known(a) || !known(b) || a.equals(b)) return null;
        return REACT.get(key(a, b));
    }

    /** 반응 열 (표 순서) */
    public static Collection<Reaction> reactions() {
        return REACT.values();
    }

    /** 반응 id 로 찾기 (없으면 null) */
    public static Reaction byId(String id) {
        for (Reaction r : REACT.values()) if (r.id().equals(id)) return r;
        return null;
    }

    /**
     * 스킬 · 룬 정의의 원소 (elem — 다섯 중 하나 · {@link #MULTI}). 없거나 표가 없으면 null (무속성). 모르는 이름은 오류 (오타가 무속성으로 숨지 않게)
     */
    public static String of(Obj def) {
        if (E == null || def == null) return null;
        String el = def.strOrNull("elem");
        if (el == null || el.isEmpty()) return null;
        if (!MULTI.equals(el) && !known(el)) throw new IllegalStateException("unknown element '" + el + "' in " + def);
        return el;
    }

    /** 원소 스킬의 피해 배율 (1 − tax) — 무속성 (null) 이면 1 */
    public static double mult(String el) {
        return el == null || E == null ? 1 : 1 - tax();
    }

    /** 이 원소 스킬이 쓰는 원소 이름들 ("§c불" · multi 면 null) */
    public static String label(String el) {
        Element e = get(el);
        return e == null ? null : e.label();
    }

    /** 툴팁 조각 ("원소 §c불§7 −5%" · multi 면 "원소 여럿 −5%" · 무속성이면 null) — 앞 색은 부르는 쪽 */
    public static String note(String el, String plainColor) {
        if (el == null || E == null) return null;
        String pct = Math.round(tax() * 100) + "%";
        if (MULTI.equals(el)) return "원소 여럿 −" + pct;
        Element e = get(el);
        return "원소 " + e.label() + plainColor + " −" + pct;
    }

    /** 색 없는 툴팁 조각 ("원소 불 −5%" · multi 면 "원소 여럿 −5%" · 무속성이면 null) */
    public static String notePlain(String el) {
        if (el == null || E == null) return null;
        String pct = Math.round(tax() * 100) + "%";
        Element e = get(el);
        return "원소 " + (e == null ? "여럿" : e.name()) + " −" + pct;
    }

    /** 반응 표 줄 (안내 · 도감 — "§c불§7 + §9물§7 = 증발 (즉시 피해)") */
    public static List<String> tableLines(String plainColor) {
        List<String> out = new ArrayList<>();
        for (Reaction r : REACT.values())
            out.add(get(r.a()).label() + plainColor + " + " + get(r.b()).label() + plainColor + " = §f" + r.name() + plainColor + " (" + r.desc() + ")");
        return out;
    }
}
