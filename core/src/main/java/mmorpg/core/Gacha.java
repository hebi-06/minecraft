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
import java.util.function.DoubleSupplier;

/**
 * 룬 뽑기 (0.14.0 — 자바 전용): 등급 확률 · 원신식 천장 · 픽업 (한정 뽑기). 데이터 {@code /mmorpg/gacha.json}.
 * <ul>
 * <li>등급: 초월 (원신 5성) · 영웅 (4성) · 나머지 (고귀 · 희귀 · 일반 — 무게 비율대로)</li>
 * <li>천장: 마지막 초월 뒤 n 번째 뽑기의 초월 확률 = 기본 확률, softStart 회부터 회차마다 softStep % 씩 더함, hard 회째는 확정.
 * 영웅도 같은 방식 (heroEvery 회째 확정 — 원신처럼 10회마다 영웅 이상). 초월이 영웅 확정 자리에 나오면 영웅 확정은 다음 뽑기로 밀림
 * (초월은 영웅 천장 수를 되돌리지 않음)</li>
 * <li>픽업 (한정 뽑기): 초월 · 영웅이 나오면 share 확률로 픽업 룬 (반천장) — 놓치면 다음 그 등급은 픽업 확정 (확천장), 놓친 결과는 픽업이 아닌 룬</li>
 * <li>계열은 고른 목록 (픽업 · 픽업 아닌 것 · 모두) 에서 고르게</li>
 * </ul>
 * 천장 수 · 확정은 플레이어 기록 ({@link Counter}) 으로 — 한정 뽑기끼리는 이어짐 (배너가 바뀌어도).
 */
public final class Gacha {
    private Gacha() {}

    public static final String RESOURCE = "/mmorpg/gacha.json";
    /** 원신 5성 · 4성에 해당하는 등급 */
    public static final String TOP = "transcend";
    public static final String HIGH = "hero";
    public static final List<String> GRADES = Runes.GRADE_ORDER;
    /** 나머지 등급 (무게 비율대로) */
    private static final List<String> LOWER = List.of("common", "rare", "noble");

    private static final Obj DATA = load();

    /**
     * 천장 수치. hard = 초월 확정 회 (0 = 없음), softStart = 이 회차부터 초월 확률이 회차마다 softStep (%) 씩 오름 (0 = 오르지 않음),
     * heroEvery = 영웅 확정 회 (0 = 없음), heroSoftStart · heroSoftStep = 영웅도 같은 방식
     */
    public record Pity(int hard, int softStart, double softStep, int heroEvery, int heroSoftStart, double heroSoftStep) {
        public static final Pity NONE = new Pity(0, 0, 0, 0, 0, 0);

        public boolean on() {
            return hard > 0 || heroEvery > 0;
        }

        /** 관리자가 바꾸는 셋 (초월 확정 · 오르기 시작 · 영웅 확정) 만 바꾼 것 — 오르는 폭은 그대로, 영웅이 오르기 시작은 확정 한 회 전 */
        public Pity with(int hard, int softStart, int heroEvery) {
            int h = Math.max(0, hard);
            int e = Math.max(0, heroEvery);
            return new Pity(h, Math.max(0, softStart), softStep, e, e > 1 ? e - 1 : 0, heroSoftStep);
        }
    }

    /** 플레이어의 천장 기록 (뽑기 종류마다): n5 · n4 = 마지막 초월 · 영웅 뒤 뽑은 수, g5 · g4 = 다음 초월 · 영웅은 픽업 확정 */
    public record Counter(int n5, int n4, boolean g5, boolean g4) {
        public static final Counter ZERO = new Counter(0, 0, false, false);

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("n5", n5);
            m.put("n4", n4);
            if (g5) m.put("g5", true);
            if (g4) m.put("g4", true);
            return m;
        }

        public static Counter fromMap(Object o) {
            if (!(o instanceof Map<?, ?> m)) return ZERO;
            return new Counter(Math.max(0, intOf(m.get("n5"), 0)), Math.max(0, intOf(m.get("n4"), 0)), Boolean.TRUE.equals(m.get("g5")),
                    Boolean.TRUE.equals(m.get("g4")));
        }
    }

    /**
     * 뽑기 하나. weights = 등급 → 무게 (비율), pity = 천장 ({@link Pity#NONE} = 없음), featured5 · featured4 = 픽업 계열 (빈 목록 = 픽업 없음),
     * share = 초월 · 영웅이 나올 때 픽업일 확률 (0 ~ 1), families = 나오는 계열
     */
    public record Banner(Map<String, Double> weights, Pity pity, List<String> featured5, List<String> featured4, double share, List<String> families) {
        public Banner {
            Map<String, Double> w = new LinkedHashMap<>();
            for (String g : GRADES) w.put(g, Math.max(0, weights.getOrDefault(g, 0.0)));
            weights = Collections.unmodifiableMap(w);
            pity = pity == null ? Pity.NONE : pity;
            featured5 = List.copyOf(featured5);
            featured4 = List.copyOf(featured4);
            families = List.copyOf(families);
            share = Math.max(0, Math.min(1, share));
        }

        public double total() {
            double t = 0;
            for (double v : weights.values()) t += v;
            return t;
        }

        /** 기본 확률 (0 ~ 1) */
        public double rate(String grade) {
            double t = total();
            return t > 0 ? weights.getOrDefault(grade, 0.0) / t : 0;
        }

        public boolean pickup() {
            return !featured5.isEmpty() || !featured4.isEmpty();
        }
    }

    /** 한 번의 결과: 계열 · 등급 · 픽업 · 픽업을 놓침 (반천장에서 짐) · 몇 번째에 나왔는지 (초월이면 초월 천장 수, 영웅이면 영웅 천장 수, 나머지 0) */
    public record Pull(String family, String grade, boolean featured, boolean lost, int at) {
        public String id() {
            return Runes.runeId(family, grade);
        }
    }

    public record Result(List<Pull> pulls, Counter counter) {}

    // ------------------------------------------------------------------ 데이터

    private static Obj load() {
        try (InputStream in = Gacha.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException(RESOURCE + " 이 없음");
            return new Obj(Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8)), "gacha");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 기본 천장 (원신: 90 · 74 · 6 · 10 · 9 · 51) */
    public static Pity defaultPity() {
        Obj p = DATA.obj("pity");
        return new Pity((int) p.num("hard"), (int) p.num("softStart"), p.num("softStep"), (int) p.num("heroEvery"), (int) p.num("heroSoftStart"),
                p.num("heroSoftStep"));
    }

    /** 일반 뽑기 (룬 뽑기권) 에도 천장 */
    public static boolean normalPity() {
        return DATA.truthy("normalPity");
    }

    /** 여러 번 뽑기 수 (10) */
    public static int multi() {
        return (int) DATA.num("multi");
    }

    /** 플레이어마다 남기는 뽑기 기록 수 */
    public static int historyMax() {
        return (int) DATA.num("historyMax");
    }

    /** 한정 뽑기 기본값 (share · price · days · maxFeatured5 · maxFeatured4) */
    public static Obj limitedDefaults() {
        return DATA.obj("limited");
    }

    /** 한정 룬 뽑기권 (item · name · icon) */
    public static Obj ticket() {
        return DATA.obj("ticket");
    }

    // ------------------------------------------------------------------ 뽑기 종류

    /** 일반 뽑기 (룬 뽑기권): 룬 85종 모두, 천장 (normalPity), 픽업 없음 */
    public static Banner standard() {
        return new Banner(Runes.gradeWeights(false), normalPity() ? defaultPity() : Pity.NONE, List.of(), List.of(), 0, Runes.FAMILY_ORDER);
    }

    /** 고급 뽑기 (고급 룬 뽑기권 — 일반 룬 없음), 천장 없음 */
    public static Banner advanced() {
        return new Banner(Runes.gradeWeights(true), Pity.NONE, List.of(), List.of(), 0, Runes.FAMILY_ORDER);
    }

    /** 한 계열만 (스탯 룬 뽑기권 — 등급 확률은 룬 뽑기권과 같음), 천장 없음 */
    public static Banner single(String family) {
        if (!Runes.FAMILY_ORDER.contains(family)) throw new IllegalArgumentException("unknown rune family: " + family);
        return new Banner(Runes.gradeWeights(false), Pity.NONE, List.of(), List.of(), 0, List.of(family));
    }

    // ------------------------------------------------------------------ 확률

    /** 마지막 초월 뒤 n 번째 뽑기의 초월 확률 (0 ~ 1) */
    public static double chance5(Banner b, int n) {
        Pity p = b.pity();
        double base = b.rate(TOP);
        if (p.hard() > 0 && n >= p.hard()) return 1;
        if (p.softStart() > 0 && n >= p.softStart()) return Math.min(1, base + p.softStep() / 100.0 * (n - p.softStart() + 1));
        return base;
    }

    /** 마지막 영웅 뒤 n 번째 뽑기의 영웅 확률 (0 ~ 1 — 초월이 먼저) */
    public static double chance4(Banner b, int n) {
        Pity p = b.pity();
        double base = b.rate(HIGH);
        if (p.heroEvery() > 0 && n >= p.heroEvery()) return 1;
        if (p.heroSoftStart() > 0 && n >= p.heroSoftStart()) return Math.min(1, base + p.heroSoftStep() / 100.0 * (n - p.heroSoftStart() + 1));
        return base;
    }

    /** 초월 하나까지 평균 뽑기 수 (천장을 넣은 기대값 — 천장이 없으면 1 / 확률, 확률 0 이면 무한) */
    public static double expected5(Banner b) {
        return expected(b, true);
    }

    /** 영웅 하나까지 평균 뽑기 수 (초월이 자리를 차지하는 것은 셈하지 않은 어림) */
    public static double expected4(Banner b) {
        return expected(b, false);
    }

    private static double expected(Banner b, boolean top) {
        int hard = top ? b.pity().hard() : b.pity().heroEvery();
        double base = b.rate(top ? TOP : HIGH);
        if (hard <= 0) return base > 0 ? 1 / base : Double.POSITIVE_INFINITY;
        double e = 0;
        double reach = 1;
        for (int n = 1; n <= hard && reach > 1e-12; n++) {
            e += reach;
            reach *= 1 - (top ? chance5(b, n) : chance4(b, n));
        }
        return e;
    }

    // ------------------------------------------------------------------ 뽑기

    /**
     * count 번 뽑기. rand 는 [0, 1) — 한 번에 부르는 차례: 등급 → (나머지 등급이면) 어느 등급 → (픽업이 있는 초월 · 영웅이고 확정이 아니면) 픽업인지 → 계열.
     * 기록 c 에서 이어 세고 새 기록을 돌려줌.
     */
    public static Result pull(Banner b, Counter c, int count, DoubleSupplier rand) {
        List<Pull> out = new ArrayList<>(Math.max(0, count));
        Counter cur = c == null ? Counter.ZERO : c;
        for (int i = 0; i < count; i++) {
            Step s = one(b, cur, rand);
            out.add(s.pull());
            cur = s.counter();
        }
        return new Result(List.copyOf(out), cur);
    }

    private record Step(Pull pull, Counter counter) {}

    private static Step one(Banner b, Counter c, DoubleSupplier rand) {
        int n5 = c.n5() + 1;
        int n4 = c.n4() + 1;
        double p5 = chance5(b, n5);
        double p4 = Math.min(chance4(b, n4), 1 - p5);
        double x = rand.getAsDouble();
        String grade;
        if (p5 > 0 && x < p5) grade = TOP;
        else if (p4 > 0 && x < p5 + p4) grade = HIGH;
        else grade = lower(b, rand.getAsDouble());
        boolean featured = false;
        boolean lost = false;
        List<String> pool = b.families();
        boolean g5 = c.g5();
        boolean g4 = c.g4();
        List<String> feat = grade.equals(TOP) ? b.featured5() : grade.equals(HIGH) ? b.featured4() : List.of();
        if (!feat.isEmpty()) {
            boolean sure = grade.equals(TOP) ? c.g5() : c.g4();
            boolean win = sure || rand.getAsDouble() < b.share();
            List<String> others = new ArrayList<>(b.families());
            others.removeAll(feat);
            if (!win && others.isEmpty()) win = true; // 모든 계열이 픽업이면 놓칠 수 없음
            featured = win;
            lost = !win;
            pool = win ? feat : others;
            if (grade.equals(TOP)) g5 = !win;
            else g4 = !win;
        }
        String family = pool.get(Math.min(pool.size() - 1, (int) Math.floor(rand.getAsDouble() * pool.size())));
        Counter next;
        int at;
        if (grade.equals(TOP)) {
            next = new Counter(0, n4, g5, g4);
            at = n5;
        } else if (grade.equals(HIGH)) {
            next = new Counter(n5, 0, g5, g4);
            at = n4;
        } else {
            next = new Counter(n5, n4, g5, g4);
            at = 0;
        }
        return new Step(new Pull(family, grade, featured, lost, at), next);
    }

    /** 나머지 등급 (일반 · 희귀 · 고귀 — 무게 비율, 무게가 모두 0 이면 나오는 가장 낮은 등급) */
    private static String lower(Banner b, double y) {
        double t = 0;
        for (String g : LOWER) t += b.weights().get(g);
        if (!(t > 0)) {
            for (String g : GRADES) if (b.weights().get(g) > 0) return g;
            return LOWER.get(0);
        }
        double r = y * t;
        for (String g : LOWER) {
            double w = b.weights().get(g);
            if (w <= 0) continue;
            if (r < w) return g;
            r -= w;
        }
        for (int i = LOWER.size() - 1; i >= 0; i--) if (b.weights().get(LOWER.get(i)) > 0) return LOWER.get(i);
        return LOWER.get(0);
    }

    // ------------------------------------------------------------------ 한정 뽑기 설정

    /**
     * 관리자가 여는 한정 뽑기 (세계에 저장). rate5 · rate4 = 초월 · 영웅 기본 확률 (%) — 나머지 등급은 일반 뽑기권의 비율대로 나머지를 나눔,
     * hard · softStart · heroEvery = 천장, share = 픽업 몫 (%), start · end = 기간 (밀리초 — end 0 = 끝없음), price = 룬술사의 한정 뽑기권 값 (룬 조각 — 0 = 팔지 않음)
     */
    public record Spec(String id, String name, List<String> featured5, List<String> featured4, double rate5, double rate4, int hard, int softStart,
            int heroEvery, double share, long start, long end, int price) {
        public Spec {
            name = name == null ? "" : name.strip();
            featured5 = List.copyOf(featured5);
            featured4 = List.copyOf(featured4);
        }

        /** 새 한정 뽑기의 기본값 (확률 · 천장은 일반 뽑기와 같게, 픽업 몫 · 값 · 기간은 gacha.json limited) */
        public static Spec defaults(String id, String name, List<String> featured5, List<String> featured4, long now) {
            Pity p = defaultPity();
            Obj d = limitedDefaults();
            Map<String, Double> w = Runes.gradeWeights(false);
            double t = 0;
            for (double v : w.values()) t += v;
            double days = d.num("days");
            return new Spec(id, name, featured5, featured4, round2(w.get(TOP) / t * 100), round2(w.get(HIGH) / t * 100), p.hard(), p.softStart(), p.heroEvery(),
                    d.num("share"), now, days > 0 ? now + (long) (days * 86_400_000L) : 0, (int) d.num("price"));
        }

        /** 기간 안인지 */
        public boolean activeAt(long now) {
            return now >= start && (end <= 0 || now < end);
        }

        /** 등급 무게 (%): 초월 · 영웅은 rate5 · rate4, 나머지는 일반 뽑기권의 비율대로 */
        public Map<String, Double> weights() {
            Map<String, Double> base = Runes.gradeWeights(false);
            double rest = 0;
            for (String g : LOWER) rest += base.get(g);
            double left = Math.max(0, 100 - rate5 - rate4);
            Map<String, Double> w = new LinkedHashMap<>();
            for (String g : GRADES) {
                if (g.equals(TOP)) w.put(g, rate5);
                else if (g.equals(HIGH)) w.put(g, rate4);
                else w.put(g, rest > 0 ? base.get(g) / rest * left : 0);
            }
            return w;
        }

        public Banner banner() {
            return new Banner(weights(), defaultPity().with(hard, softStart, heroEvery), featured5, featured4, share / 100.0, Runes.FAMILY_ORDER);
        }

        /** 틀린 곳 (없으면 빈 목록) — 관리자 설정 창 · 명령어가 보여 줌 */
        public List<String> problems() {
            List<String> out = new ArrayList<>();
            Obj d = limitedDefaults();
            if (name.isEmpty()) out.add("이름이 비었습니다");
            else if (name.length() > 32) out.add("이름은 32자까지");
            if (featured5.isEmpty()) out.add("픽업 초월 룬을 하나 이상 고르세요");
            if (featured5.size() > (int) d.num("maxFeatured5")) out.add("픽업 초월 룬은 " + (int) d.num("maxFeatured5") + "개까지");
            if (featured4.size() > (int) d.num("maxFeatured4")) out.add("픽업 영웅 룬은 " + (int) d.num("maxFeatured4") + "개까지");
            for (String f : featured5) if (!Runes.FAMILY_ORDER.contains(f)) out.add("모르는 계열: " + f);
            for (String f : featured4) if (!Runes.FAMILY_ORDER.contains(f)) out.add("모르는 계열: " + f);
            if (featured5.stream().distinct().count() != featured5.size() || featured4.stream().distinct().count() != featured4.size()) out.add("같은 계열을 두 번 골랐습니다");
            if (!(rate5 > 0) || rate5 > 100) out.add("초월 확률은 0 보다 크고 100 이하");
            if (rate4 < 0 || rate5 + rate4 > 100) out.add("초월 + 영웅 확률은 100 이하");
            if (hard < 0 || hard > 1000) out.add("초월 천장은 0 ~ 1000 회");
            if (softStart < 0 || (hard > 0 && softStart >= hard)) out.add("확률이 오르기 시작하는 회차는 천장보다 작게 (0 = 오르지 않음)");
            if (heroEvery < 0 || heroEvery > 100) out.add("영웅 확정은 0 ~ 100 회");
            if (share < 0 || share > 100) out.add("픽업 몫은 0 ~ 100 %");
            if (end > 0 && end <= start) out.add("끝나는 때가 시작보다 앞입니다");
            if (price < 0 || price > 640) out.add("룬술사 값은 0 ~ 640 룬 조각");
            return out;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            m.put("featured5", new ArrayList<>(featured5));
            m.put("featured4", new ArrayList<>(featured4));
            m.put("rate5", rate5);
            m.put("rate4", rate4);
            m.put("hard", hard);
            m.put("softStart", softStart);
            m.put("heroEvery", heroEvery);
            m.put("share", share);
            m.put("start", (double) start);
            m.put("end", (double) end);
            m.put("price", price);
            return m;
        }

        /** 저장한 것 · 클라이언트가 보낸 것 (모자란 값은 기본값 — 틀린 값은 {@link #problems} 가 잡음) */
        public static Spec fromMap(Map<?, ?> m, long now) {
            Spec d = defaults("", "", List.of(), List.of(), now);
            return new Spec(strOf(m.get("id"), d.id()), strOf(m.get("name"), d.name()), strings(m.get("featured5")), strings(m.get("featured4")),
                    numOf(m.get("rate5"), d.rate5()), numOf(m.get("rate4"), d.rate4()), intOf(m.get("hard"), d.hard()), intOf(m.get("softStart"), d.softStart()),
                    intOf(m.get("heroEvery"), d.heroEvery()), numOf(m.get("share"), d.share()), (long) numOf(m.get("start"), d.start()),
                    (long) numOf(m.get("end"), d.end()), intOf(m.get("price"), d.price()));
        }
    }

    // ------------------------------------------------------------------ 도움

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    static int intOf(Object o, int def) {
        return o instanceof Number n ? (int) Math.round(n.doubleValue()) : def;
    }

    private static double numOf(Object o, double def) {
        return o instanceof Number n && Double.isFinite(n.doubleValue()) ? n.doubleValue() : def;
    }

    private static String strOf(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    private static List<String> strings(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) for (Object x : l) if (x != null) out.add(String.valueOf(x));
        return out;
    }
}
