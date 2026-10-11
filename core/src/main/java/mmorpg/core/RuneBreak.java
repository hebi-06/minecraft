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
import java.util.TreeMap;

/**
 * 룬 돌파 (8단계 — 애드온에는 없는 자바 판 기능): 같은 룬 (계열 · 등급) 둘을 합치면 남는 룬의 돌파 단계 = 남는 룬 + 재료 + 1 ({@link #max()} 까지).
 * 1돌마다 계수 +{@link #perLevel()}: 피해는 위력 P × {@link #coef(int)}, 회복 · 보호막은 룬 정의의 scaleKeys × coef ({@link #def}).
 * 초월 룬은 {@link #tiers()} (3 · 6) 에 이르면 그 단계의 값으로 바뀌고 ({@link #changes}) 연출 단계 ({@link #fxTier}) 도 바뀜 — 0~2돌은 화면 색 연출 없음.
 * 데이터는 {@code /mmorpg/rune_break.json}. 순수 계산 (서버 · 클라이언트 · 코어 시험이 같이 씀).
 */
public final class RuneBreak {
    private RuneBreak() {}

    public static final String RESOURCE = "/mmorpg/rune_break.json";
    /** 돌파 별 (이룬 단계 · 남은 단계) */
    public static final String STAR = "★";
    public static final String STAR_EMPTY = "☆";
    /** 설명 한 줄의 최대 글자 수 (넘으면 " · " 에서 나눔) */
    private static final int LINE_MAX = 30;

    /**
     * 초월 룬 한 단계의 바뀌는 값 하나: 룬 정의의 key 를 to 로. 글자 (text — 없으면 줄 없음) 의 {from} · {to} = 앞 단계 값 · 바뀐 값 ÷ div
     * (틱 → 초는 20, 비율 → % 는 0.01)
     */
    public record Change(String key, double to, double div, String text) {}

    /**
     * 연출 단계 (at 돌부터): 이름 · 풀이, 하늘빛 시간 배율 (skyTicks) · 하늘빛 거리 배율 (skyDist — 작을수록 짙음), 번쩍임 더 머묾 (flashHold 초) ·
     * 걷힘 배율 (flashOut), 화면 테두리 빛 (edge 0 ~ 1), 쓸 때 금빛 각성 연출 (awaken)
     */
    public record Fx(int at, String name, String text, double skyTicks, double skyDist, double flashHold, double flashOut, double edge, boolean awaken) {}

    /**
     * 초월 룬 하나의 단계 효과 (extra — 하나씩): type echo = at 돌부터, 룬이 나가고 after 틱 뒤 그 룬 효과가 한 번 더 나감 (지금 겨눈 곳 · 위력 × mult,
     * 룬 정의에 set 을 덮음 — 예고를 짧게). text 의 {mult} = mult × 100
     */
    public record Extra(int at, String type, double mult, int after, Map<String, Object> set, String text) {}

    private record Data(int max, double perLevel, List<String> scaleKeys, double grindPerLevel, List<Integer> tiers, Map<Integer, Fx> fx,
            Map<String, Map<Integer, List<Change>>> transcend, Map<String, Extra> extras) {}

    private static final Data DATA = load();

    // ------------------------------------------------------------------ 데이터

    /** 가장 높은 돌파 단계 (6) */
    public static int max() {
        return DATA.max();
    }

    /** 1돌마다 계수 (0.1 = +10%) */
    public static double perLevel() {
        return DATA.perLevel();
    }

    /** 초월 룬이 바뀌는 단계 (3 · 6, 오름차순) */
    public static List<Integer> tiers() {
        return DATA.tiers();
    }

    /** 계수를 곱하는 룬 정의 값 (회복 · 보호막) */
    public static List<String> scaleKeys() {
        return DATA.scaleKeys();
    }

    /** 단계를 0 ~ max 로 */
    public static int clamp(int level) {
        return Math.max(0, Math.min(DATA.max(), level));
    }

    /** 계수: 1 + perLevel × 단계 (6돌 = 1.6) */
    public static double coef(int level) {
        return 1 + DATA.perLevel() * clamp(level);
    }

    /** 합칠 수 있는지: 남는 룬 target 돌 + 재료 material 돌 + 1 ≤ max */
    public static boolean canMerge(int target, int material) {
        return target >= 0 && material >= 0 && target + material + 1 <= DATA.max();
    }

    /** 합친 단계 (target + material + 1, max 까지) */
    public static int merged(int target, int material) {
        return clamp(Math.max(0, target) + Math.max(0, material) + 1);
    }

    /** 돌파한 룬을 갈면 나오는 조각: 룬 하나 값 × (1 + 단계 — 들어간 룬 수만큼) */
    public static double grindValue(Runes.Rune r, int level) {
        return Runes.grindValue(r) * (1 + DATA.grindPerLevel() * clamp(level));
    }

    /** 초월 룬이고 단계별 강화가 있는지 */
    public static boolean tiered(Runes.Rune r) {
        return r != null && "transcend".equals(r.grade()) && DATA.transcend().containsKey(r.def().strOrNull("fn"));
    }

    /** 단계별 강화가 있는 룬 효과 (fn) */
    public static List<String> tieredFns() {
        return List.copyOf(DATA.transcend().keySet());
    }

    /** 룬 효과 fn 의 tier 돌 강화 (없으면 빈 목록) */
    public static List<Change> changes(String fn, int tier) {
        Map<Integer, List<Change>> m = DATA.transcend().get(fn);
        List<Change> l = m == null ? null : m.get(tier);
        return l == null ? List.of() : l;
    }

    /** 룬 효과 fn 의 단계 효과 (없으면 null) */
    public static Extra extra(String fn) {
        return DATA.extras().get(fn);
    }

    /** 이 룬 · 단계에서 켜진 단계 효과 (초월이고 at 돌 이상 — 아니면 null) */
    public static Extra extraFor(Runes.Rune r, int level) {
        if (!tiered(r)) return null;
        Extra e = DATA.extras().get(r.def().str("fn"));
        return e != null && clamp(level) >= e.at() ? e : null;
    }

    /** 단계 효과 글자 ("검이 한 번 더 떨어짐 (… 위력 60%)") */
    public static String extraText(Extra e) {
        return e.text().replace("{mult}", Js.str(Math.round(e.mult() * 100)));
    }

    /** 한 번 더 (echo) 나갈 때의 룬 정의: 그 단계의 정의 + set */
    public static Obj echoDef(Runes.Rune r, int level) {
        Obj d = def(r, level);
        Extra e = extraFor(r, level);
        if (e == null || e.set().isEmpty()) return d;
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : d.keys()) m.put(k, d.get(k));
        m.putAll(e.set());
        return new Obj(Collections.unmodifiableMap(m), "RUNES." + r.family() + "." + r.grade() + "@" + clamp(level) + "+echo");
    }

    /** 이 단계의 연출 단계 (이른 tiers 중 가장 높은 것 — 없으면 0) */
    public static int fxTier(int level) {
        int t = 0;
        for (int at : DATA.tiers()) if (level >= at) t = at;
        return t;
    }

    /** 연출 단계 at 의 설정 (0 이나 없는 단계면 null) */
    public static Fx fx(int at) {
        return DATA.fx().get(at);
    }

    // ------------------------------------------------------------------ 단계별 룬

    /**
     * 돌파 단계의 룬 정의: 원래 정의 + (초월) 이른 단계의 강화 값 + 회복 · 보호막 × 계수. 0돌이면 원래 정의 그대로
     */
    public static Obj def(Runes.Rune r, int level) {
        int lv = clamp(level);
        Obj base = r.def();
        if (lv == 0) return base;
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : base.keys()) m.put(k, base.get(k));
        if (tiered(r)) {
            String fn = base.str("fn");
            for (int t : DATA.tiers()) {
                if (lv < t) break;
                for (Change c : changes(fn, t)) m.put(c.key(), c.to());
            }
        }
        double coef = coef(lv);
        for (String k : DATA.scaleKeys()) if (m.get(k) instanceof Number n) m.put(k, n.doubleValue() * coef);
        return new Obj(Collections.unmodifiableMap(m), "RUNES." + r.family() + "." + r.grade() + "@" + lv);
    }

    /** 돌파 단계의 룬 (정의만 {@link #def} — 0돌이면 r 그대로) */
    public static Runes.Rune leveled(Runes.Rune r, int level) {
        if (r == null || clamp(level) == 0) return r;
        return new Runes.Rune(r.id(), r.family(), r.stat(), r.famName(), r.grade(), def(r, level), r.cd(), r.gradeName(), r.gradeColor());
    }

    // ------------------------------------------------------------------ 글자

    /** "★★★☆☆☆" */
    public static String stars(int level) {
        int lv = clamp(level);
        return STAR.repeat(lv) + STAR_EMPTY.repeat(DATA.max() - lv);
    }

    /** 이름 뒤에 붙는 단계 ("[3돌]" — 0돌이면 "") */
    public static String tag(int level) {
        int lv = clamp(level);
        return lv > 0 ? "[" + lv + "돌]" : "";
    }

    /** "×1.3" */
    public static String coefText(int level) {
        return "×" + Js.str(Math.round(coef(level) * 100) / 100.0);
    }

    private static String num(double v, double div) {
        return Js.str(Math.round(v / div * 100) / 100.0);
    }

    /** 초월 룬의 tier 돌 강화 글자 조각 ("운석 범위 6 → 7.5칸" …) — 앞 단계 값에서 바뀐 값으로 */
    public static List<String> changeTexts(Runes.Rune r, int tier) {
        if (!tiered(r)) return List.of();
        String fn = r.def().str("fn");
        Map<String, Double> cur = new LinkedHashMap<>();
        for (int t : DATA.tiers()) {
            if (t >= tier) break;
            for (Change c : changes(fn, t)) cur.put(c.key(), c.to());
        }
        List<String> out = new ArrayList<>();
        for (Change c : changes(fn, tier)) {
            if (c.text() == null || c.text().isEmpty()) continue;
            Double from = cur.containsKey(c.key()) ? cur.get(c.key()) : r.def().numOrNull(c.key());
            out.add(c.text().replace("{from}", from == null ? "?" : num(from, c.div())).replace("{to}", num(c.to(), c.div())));
        }
        return out;
    }

    /** 조각들을 " · " 로 이어 줄로 (줄마다 LINE_MAX 글자까지) */
    private static List<String> wrap(List<String> parts) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String p : parts) {
            if (cur.length() > 0 && cur.length() + 3 + p.length() > LINE_MAX) {
                lines.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append(" · ");
            cur.append(p);
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    /** 돌파 줄: "돌파 ★★★☆☆☆ 3/6 · 계수 ×1.3" (0돌은 어둡게 — 합치는 법) */
    public static String summaryLine(int level, boolean dark) {
        int lv = clamp(level);
        String L = dark ? "§8" : "§7";
        if (lv == 0) return "§r§8돌파 " + stars(0) + " 0/" + DATA.max() + " · 같은 룬을 합치면 +" + Js.str(Math.round(DATA.perLevel() * 100)) + "%";
        return "§r" + (dark ? "§3" : "§b") + "돌파 §e" + stars(lv) + " " + (dark ? "§0" : "§f") + lv + L + "/" + DATA.max() + " · 계수 " + (dark ? "§0" : "§f")
                + coefText(lv);
    }

    /**
     * 초월 룬의 단계 줄: 단계마다 "★3돌 강화 …" + "+ 연출 이름 (풀이)". 이룬 단계는 밝게, 아직이면 어둡게. 초월이 아니면 빈 목록
     */
    public static List<String> tierLines(Runes.Rune r, int level, boolean dark) {
        if (r == null || !"transcend".equals(r.grade())) return List.of();
        int lv = clamp(level);
        List<String> out = new ArrayList<>();
        List<String> ts = new ArrayList<>();
        for (int t : DATA.tiers()) ts.add(t + "돌");
        out.add("§r§6초월 돌파 강화 §8(" + String.join(" · ", ts) + ")");
        for (int t : DATA.tiers()) {
            boolean on = lv >= t;
            String head = on ? "§r§6" + STAR + t + "돌 " + (dark ? "§0" : "§f") : "§r§8" + STAR_EMPTY + t + "돌 ";
            String cont = on ? "§r" + (dark ? "§0" : "§f") + "     " : "§r§8     ";
            List<String> lines = wrap(changeTexts(r, t));
            Extra ex = extra(r.def().str("fn"));
            if (ex != null && ex.at() == t) lines.add("◆ " + extraText(ex)); // 단계 효과 (한 번 더 …)
            for (int i = 0; i < lines.size(); i++) {
                String l = lines.get(i);
                boolean special = l.startsWith("◆");
                out.add((i == 0 ? head : special ? (on ? "§r§e     " : "§r§8     ") : cont) + l);
            }
            Fx f = fx(t);
            if (f != null) {
                String fl = (on ? "§r" + (dark ? "§5" : "§d") : "§r§8") + "     + " + f.name() + " (" + f.text() + ")";
                if (lines.isEmpty()) fl = head + "+ " + f.name() + " (" + f.text() + ")";
                out.add(fl);
            }
        }
        return out;
    }

    /**
     * 돌파한 룬의 설명 (Runes.runeLore 를 단계별 정의로): 머리 줄 다음에 돌파 줄, 조작 줄 앞에 초월 단계 줄 + (6돌 전) 합치는 곳 안내
     */
    public static List<String> lore(Runes.Rune r, int level, boolean dark) {
        int lv = clamp(level);
        List<String> base = Runes.runeLore(leveled(r, lv), dark);
        List<String> out = new ArrayList<>();
        int ctl = base.size();
        for (int i = 0; i < base.size(); i++) {
            if (base.get(i).startsWith("§r§8터치:")) {
                ctl = i;
                break;
            }
        }
        for (int i = 0; i < ctl; i++) {
            out.add(base.get(i));
            if (i == 0) out.add(summaryLine(lv, dark));
        }
        out.addAll(tierLines(r, lv, dark));
        if (lv < DATA.max()) out.add("§r§8같은 룬 둘 → 룬술사 '돌파' (" + DATA.max() + "돌까지)");
        out.addAll(base.subList(ctl, base.size()));
        return out;
    }

    // ------------------------------------------------------------------ 읽기

    private static Data load() {
        Map<String, Object> json;
        try (InputStream in = RuneBreak.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException(RESOURCE + " is not on the classpath");
            json = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + RESOURCE, e);
        }
        Obj root = new Obj(json, "rune_break");
        List<String> scale = new ArrayList<>();
        for (Object o : root.list("scaleKeys")) scale.add((String) o);
        List<Integer> tiers = new ArrayList<>();
        for (double d : root.numbersOrNull("tiers")) tiers.add((int) d);
        Collections.sort(tiers);
        Map<Integer, Fx> fx = new TreeMap<>();
        Obj fo = root.obj("fx");
        for (String k : fo.keys()) {
            Obj o = fo.obj(k);
            int at = Integer.parseInt(k);
            fx.put(at, new Fx(at, o.str("name"), o.str("text"), o.numOr("skyTicks", 1), o.numOr("skyDist", 1), o.numOr("flashHold", 0), o.numOr("flashOut", 1),
                    o.numOr("edge", 0), o.truthy("awaken")));
        }
        Map<String, Map<Integer, List<Change>>> tr = new LinkedHashMap<>();
        Obj to = root.obj("transcend");
        for (String fn : to.keys()) {
            Obj byTier = to.obj(fn);
            Map<Integer, List<Change>> m = new TreeMap<>();
            for (String t : byTier.keys()) {
                if (t.isEmpty() || !t.chars().allMatch(Character::isDigit)) continue; // "extra" 등
                List<Change> list = new ArrayList<>();
                for (Obj c : byTier.objsOrNull(t)) list.add(new Change(c.str("key"), c.num("to"), c.numOr("div", 1), c.strOr("text", "")));
                m.put(Integer.parseInt(t), List.copyOf(list));
            }
            tr.put(fn, Collections.unmodifiableMap(m));
        }
        Map<String, Extra> extras = new LinkedHashMap<>();
        for (String fn : to.keys()) {
            Obj e = to.obj(fn).objOrNull("extra");
            if (e == null) continue;
            Map<String, Object> set = new LinkedHashMap<>();
            Obj so = e.objOrNull("set");
            if (so != null) for (String k : so.keys()) set.put(k, so.get(k));
            extras.put(fn, new Extra((int) e.num("at"), e.str("type"), e.num("mult"), (int) e.num("after"), Collections.unmodifiableMap(set), e.str("text")));
        }
        return new Data((int) root.num("max"), root.num("perLevel"), List.copyOf(scale), root.numOr("grindPerLevel", 1), List.copyOf(tiers),
                Collections.unmodifiableMap(fx), Collections.unmodifiableMap(tr), Collections.unmodifiableMap(extras));
    }
}
