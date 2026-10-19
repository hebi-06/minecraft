package mmorpg.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.DoubleSupplier;

/**
 * 룬 뽑기 시험 (0.14.0 — JUnit 없이 main, check-core.sh): gacha.json 기본값, 초월 · 영웅 확률 곡선 (74회부터 오름 · 90 · 10 확정),
 * 천장 (확정 자리 · 초월이 영웅 확정을 밀어냄), 반천장 · 확천장 (놓치면 다음은 픽업 · 놓친 결과는 픽업이 아님), 고급 · 한 계열 뽑기,
 * 통합 확률 (모의 20만 회 — 초월 약 2.2% · 영웅 약 13.8% · 픽업 몫 2/3), 한정 뽑기 설정 (무게 · 검사 · 저장 왕복 · 기간). 하나라도 틀리면 종료 코드 1.
 */
public final class GachaTest {
    private GachaTest() {}

    private static final List<String> BAD = new ArrayList<>();
    private static int ok;

    private static void check(boolean cond, String what) {
        if (cond) ok++;
        else BAD.add(what);
    }

    private static void near(double a, double b, double tol, String what) {
        check(Math.abs(a - b) <= tol, what + " (" + a + " vs " + b + ")");
    }

    /** 정해 둔 값을 차례로 (모자라면 마지막 값을 되풀이) */
    private static DoubleSupplier script(double... values) {
        Deque<Double> q = new ArrayDeque<>();
        for (double v : values) q.add(v);
        double[] last = {values.length > 0 ? values[values.length - 1] : 0.5};
        return () -> {
            if (q.isEmpty()) return last[0];
            return q.poll();
        };
    }

    public static void main(String[] argv) {
        // ---------------------------------------------------------- 기본값
        Gacha.Pity p = Gacha.defaultPity();
        check(p.hard() == 90 && p.softStart() == 74 && p.heroEvery() == 10 && p.heroSoftStart() == 9, "default pity " + p);
        near(p.softStep(), 6, 1e-9, "soft step");
        near(p.heroSoftStep(), 51, 1e-9, "hero soft step");
        check(Gacha.normalPity(), "normal banner has pity");
        check(Gacha.multi() == 10, "multi 10");
        check(Gacha.historyMax() >= 10, "history kept");
        check("rpg:limited_rune_ticket".equals(Gacha.ticket().str("item")), "limited ticket item");

        Gacha.Banner std = Gacha.standard();
        near(std.rate("transcend"), 0.015, 1e-12, "standard transcend 1.5%");
        near(std.rate("hero"), 0.065, 1e-12, "standard hero 6.5%");
        near(std.rate("common"), 0.5, 1e-12, "standard common 50%");
        check(std.families().size() == Runes.FAMILY_ORDER.size() && std.families().size() == 23, "standard: all 23 families (0.25.0 두 스탯 계열 여섯 더함) " + std.families().size());
        check(!std.pickup(), "standard has no pickup");

        // ---------------------------------------------------------- 확률 곡선
        near(Gacha.chance5(std, 1), 0.015, 1e-12, "p5(1)");
        near(Gacha.chance5(std, 73), 0.015, 1e-12, "p5(73) still base");
        near(Gacha.chance5(std, 74), 0.075, 1e-12, "p5(74) = base + 6%");
        near(Gacha.chance5(std, 89), 0.975, 1e-12, "p5(89) = base + 96%");
        near(Gacha.chance5(std, 90), 1, 0, "p5(90) = 1");
        near(Gacha.chance5(std, 120), 1, 0, "p5 beyond hard = 1");
        near(Gacha.chance4(std, 8), 0.065, 1e-12, "p4(8)");
        near(Gacha.chance4(std, 9), 0.575, 1e-12, "p4(9) = base + 51%");
        near(Gacha.chance4(std, 10), 1, 0, "p4(10) = 1");

        // ---------------------------------------------------------- 천장: 운이 하나도 없을 때 (rand 0.999)
        Gacha.Result r = Gacha.pull(std, Gacha.Counter.ZERO, 91, () -> 0.999);
        List<Gacha.Pull> pulls = r.pulls();
        check(pulls.size() == 91, "91 pulls");
        int firstTop = -1;
        List<Integer> heroAt = new ArrayList<>();
        for (int i = 0; i < pulls.size(); i++) {
            String g = pulls.get(i).grade();
            if (g.equals("transcend") && firstTop < 0) firstTop = i + 1;
            if (g.equals("hero")) heroAt.add(i + 1);
        }
        check(firstTop == 90, "unlucky: transcend exactly at 90 (" + firstTop + ")");
        check(pulls.get(89).at() == 90, "transcend at = 90");
        // 89회째: 초월 97.5% + 남은 2.5% 가 영웅 (나머지 등급 0%) — 운이 없어도 영웅
        check(heroAt.equals(List.of(10, 20, 30, 40, 50, 60, 70, 80, 89)), "hero every 10 · 89 takes the last 2.5% " + heroAt);
        check(r.counter().n5() == 1 && r.counter().n4() == 2, "counter after 91 " + r.counter());
        // 초월이 영웅 확정 자리에 나오면 영웅 확정은 다음 뽑기로 밀림
        Gacha.Result pushed = Gacha.pull(std, new Gacha.Counter(89, 9, false, false), 2, () -> 0.999);
        check(pushed.pulls().get(0).grade().equals("transcend") && pushed.pulls().get(0).at() == 90 && pushed.pulls().get(1).grade().equals("hero")
                && pushed.pulls().get(1).at() == 11, "transcend at the hero slot pushes the hero to the next pull " + pushed.pulls());
        for (Gacha.Pull x : pulls) {
            if (!x.grade().equals("transcend") && !x.grade().equals("hero") && !x.grade().equals("noble")) {
                check(false, "unlucky lower grade should be noble (last lower weight) " + x);
                break;
            }
        }
        // 이어 세기: 기록에서 이어서
        Gacha.Result r2 = Gacha.pull(std, new Gacha.Counter(78, 3, false, false), 2, () -> 0.999);
        check(r2.pulls().get(0).grade().equals("noble") && r2.pulls().get(1).grade().equals("noble") && r2.counter().n5() == 80 && r2.counter().n4() == 5,
                "counter continues: 79 · 80 " + r2.pulls() + " " + r2.counter());
        // 운이 좋을 때 (rand 0): 첫 뽑기부터 초월
        Gacha.Result lucky = Gacha.pull(std, Gacha.Counter.ZERO, 3, () -> 0.0);
        check(lucky.pulls().stream().allMatch(x -> x.grade().equals("transcend") && x.at() == 1), "lucky: transcend each pull");
        check(lucky.counter().n4() == 3, "transcend does not reset hero counter " + lucky.counter());

        // ---------------------------------------------------------- 반천장 · 확천장 (한정)
        long now = 1_800_000_000_000L;
        Gacha.Spec spec = Gacha.Spec.defaults("lim1", "불꽃 축제", List.of("spirit"), List.of("axe", "bow", "holy"), now);
        check(spec.problems().isEmpty(), "default spec valid " + spec.problems());
        Gacha.Banner lim = spec.banner();
        check(lim.pickup() && lim.featured5().equals(List.of("spirit")), "limited pickup");
        near(lim.share(), 0.5, 1e-12, "share 50%");
        near(lim.rate("transcend"), 0.015, 1e-9, "limited transcend base = standard");
        // 첫 초월: 픽업 놓침 (0.9) → 픽업 아닌 계열, 확정이 생김
        Gacha.Result a = Gacha.pull(lim, Gacha.Counter.ZERO, 1, script(0.0, 0.9, 0.0));
        Gacha.Pull a0 = a.pulls().get(0);
        check(a0.grade().equals("transcend") && !a0.featured() && a0.lost() && !a0.family().equals("spirit"), "lost 50/50 → not featured " + a0);
        check(a.counter().g5(), "guarantee after a loss");
        // 다음 초월: 확정 (픽업 굴림 없이 — 계열 굴림만)
        Gacha.Result b = Gacha.pull(lim, a.counter(), 1, script(0.0, 0.99));
        Gacha.Pull b0 = b.pulls().get(0);
        check(b0.grade().equals("transcend") && b0.featured() && !b0.lost() && b0.family().equals("spirit"), "guaranteed featured " + b0);
        check(!b.counter().g5(), "guarantee used");
        // 반천장 이김 (0.1)
        Gacha.Result c = Gacha.pull(lim, Gacha.Counter.ZERO, 1, script(0.0, 0.1, 0.5));
        check(c.pulls().get(0).featured() && c.pulls().get(0).family().equals("spirit") && !c.counter().g5(), "won 50/50 " + c.pulls());
        // 영웅 픽업 (0.02 → 영웅 자리: p5 0.015 ≤ x < 0.08)
        Gacha.Result d = Gacha.pull(lim, Gacha.Counter.ZERO, 1, script(0.02, 0.2, 0.7));
        Gacha.Pull d0 = d.pulls().get(0);
        check(d0.grade().equals("hero") && d0.featured() && List.of("axe", "bow", "holy").contains(d0.family()), "hero pickup " + d0);
        Gacha.Result e = Gacha.pull(lim, Gacha.Counter.ZERO, 1, script(0.02, 0.8, 0.0));
        Gacha.Pull e0 = e.pulls().get(0);
        check(e0.grade().equals("hero") && !e0.featured() && !List.of("axe", "bow", "holy").contains(e0.family()) && e.counter().g4(), "hero lost → others · guarantee " + e0);
        // 놓친 결과는 픽업 계열이 아님 (모든 계열 굴림 값으로)
        Set<String> lostFamilies = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            double f = i / 40.0;
            Gacha.Pull x = Gacha.pull(lim, Gacha.Counter.ZERO, 1, script(0.0, 0.9, f)).pulls().get(0);
            lostFamilies.add(x.family());
        }
        check(!lostFamilies.contains("spirit") && lostFamilies.size() == 22, "lost pool = 22 others " + lostFamilies.size());

        // ---------------------------------------------------------- 고급 · 한 계열
        Gacha.Banner adv = Gacha.advanced();
        check(!adv.pity().on(), "advanced: no pity");
        Random rng = new Random(7);
        Gacha.Result advR = Gacha.pull(adv, Gacha.Counter.ZERO, 20_000, rng::nextDouble);
        long commons = advR.pulls().stream().filter(x -> x.grade().equals("common")).count();
        long tops = advR.pulls().stream().filter(x -> x.grade().equals("transcend")).count();
        check(commons == 0, "advanced: no commons " + commons);
        near(tops / 20_000.0, 0.05, 0.006, "advanced transcend ≈ 5%");
        Gacha.Banner one = Gacha.single("str");
        check(Gacha.pull(one, Gacha.Counter.ZERO, 500, rng::nextDouble).pulls().stream().allMatch(x -> x.family().equals("str")), "single family only");
        boolean threw = false;
        try {
            Gacha.single("nope");
        } catch (IllegalArgumentException ex) {
            threw = true;
        }
        check(threw, "unknown family refused");

        // ---------------------------------------------------------- 통합 확률 (모의)
        int n = 200_000;
        Random rnd = new Random(42);
        Gacha.Result sim = Gacha.pull(std, Gacha.Counter.ZERO, n, rnd::nextDouble);
        double top = sim.pulls().stream().filter(x -> x.grade().equals("transcend")).count() / (double) n;
        double hero = sim.pulls().stream().filter(x -> x.grade().equals("hero")).count() / (double) n;
        near(top, 1 / Gacha.expected5(std), 0.0015, "consolidated transcend ≈ 1 / expected5");
        near(top, 0.0217, 0.0015, "consolidated transcend ≈ 2.17%");
        near(hero, 0.138, 0.006, "consolidated hero ≈ 13.8%");
        near(Gacha.expected5(std), 46.1, 0.3, "expected pulls per transcend");
        int maxGap = 0;
        int gap = 0;
        int maxHeroGap = 0;
        int heroGap = 0;
        for (Gacha.Pull x : sim.pulls()) {
            gap++;
            heroGap++;
            if (x.grade().equals("transcend")) {
                maxGap = Math.max(maxGap, gap);
                gap = 0;
            }
            if (x.grade().equals("hero") || x.grade().equals("transcend")) {
                maxHeroGap = Math.max(maxHeroGap, heroGap);
                heroGap = 0;
            }
        }
        check(maxGap <= 90, "never more than 90 pulls without transcend " + maxGap);
        check(maxHeroGap <= 10, "hero or better within 10 " + maxHeroGap);
        // 한정: 초월 중 픽업 몫 ≈ 2/3 (놓치면 다음은 확정)
        Gacha.Result limSim = Gacha.pull(lim, Gacha.Counter.ZERO, n, new Random(3)::nextDouble);
        long limTop = limSim.pulls().stream().filter(x -> x.grade().equals("transcend")).count();
        long limFeat = limSim.pulls().stream().filter(x -> x.grade().equals("transcend") && x.featured()).count();
        near(limFeat / (double) limTop, 2.0 / 3.0, 0.04, "featured share among transcend ≈ 2/3");
        int streakLost = 0;
        boolean lostTwice = false;
        for (Gacha.Pull x : limSim.pulls()) {
            if (!x.grade().equals("transcend")) continue;
            if (x.lost()) {
                if (++streakLost >= 2) lostTwice = true;
            } else streakLost = 0;
        }
        check(!lostTwice, "never lose 50/50 twice in a row");

        // ---------------------------------------------------------- 한정 뽑기 설정
        Gacha.Spec custom = new Gacha.Spec("lim2", "별빛", List.of("int"), List.of("dex"), 1.0, 7.0, 80, 66, 10, 75, now, now + 86_400_000L, 20);
        Map<String, Double> w = custom.weights();
        double total = 0;
        for (double v : w.values()) total += v;
        near(total, 100, 1e-9, "weights sum 100");
        near(w.get("transcend"), 1.0, 1e-12, "rate5 kept");
        near(w.get("common") / w.get("rare"), 50.0 / 28.0, 1e-9, "lower ratio kept");
        Gacha.Banner cb = custom.banner();
        check(cb.pity().hard() == 80 && cb.pity().softStart() == 66 && cb.pity().heroEvery() == 10 && cb.pity().heroSoftStart() == 9, "custom pity " + cb.pity());
        near(cb.share(), 0.75, 1e-12, "custom share");
        check(custom.activeAt(now) && custom.activeAt(now + 1000) && !custom.activeAt(now - 1) && !custom.activeAt(now + 86_400_000L), "active window");
        check(new Gacha.Spec("x", "끝없음", List.of("int"), List.of(), 1, 6, 90, 74, 10, 50, now, 0, 0).activeAt(now + 999_999_999L), "no end");
        // 저장 왕복
        Gacha.Spec back = Gacha.Spec.fromMap(Json.parseObject(Json.stringify(custom.toMap())), now);
        check(back.equals(custom), "round trip " + back);
        // 검사
        check(!problems("", List.of("int"), List.of(), 1.5, 6.5, 90, 74, now, now + 1, 15).isEmpty(), "empty name refused");
        check(!problems("a", List.of(), List.of(), 1.5, 6.5, 90, 74, now, now + 1, 15).isEmpty(), "no featured transcend refused");
        check(!problems("a", List.of("nope"), List.of(), 1.5, 6.5, 90, 74, now, now + 1, 15).isEmpty(), "unknown family refused");
        check(!problems("a", List.of("int", "int"), List.of(), 1.5, 6.5, 90, 74, now, now + 1, 15).isEmpty(), "duplicate refused");
        check(!problems("a", List.of("int"), List.of(), 0, 6.5, 90, 74, now, now + 1, 15).isEmpty(), "rate5 0 refused");
        check(!problems("a", List.of("int"), List.of(), 60, 50, 90, 74, now, now + 1, 15).isEmpty(), "rates over 100 refused");
        check(!problems("a", List.of("int"), List.of(), 1.5, 6.5, 70, 74, now, now + 1, 15).isEmpty(), "soft start after hard refused");
        check(!problems("a", List.of("int"), List.of(), 1.5, 6.5, 90, 74, now, now - 1, 15).isEmpty(), "end before start refused");
        check(!problems("a", List.of("int"), List.of(), 1.5, 6.5, 90, 74, now, now + 1, -1).isEmpty(), "negative price refused");
        check(problems("a", List.of("int", "dex", "vit"), List.of("axe", "bow", "holy"), 1.5, 6.5, 90, 74, now, 0, 0).isEmpty(), "valid spec");
        check(!problems("a", List.of("int", "dex", "vit", "str"), List.of(), 1.5, 6.5, 90, 74, now, 0, 0).isEmpty(), "too many featured transcend");
        // 기본 기간 (gacha.json days 14)
        near((spec.end() - spec.start()) / 86_400_000.0, 14, 1e-9, "default 14 days");
        check(spec.price() == 15, "default price 15");
        // 기록 지도
        Gacha.Counter cnt = new Gacha.Counter(12, 3, true, false);
        check(Gacha.Counter.fromMap(Json.parseObject(Json.stringify(cnt.toMap()))).equals(cnt), "counter round trip");
        check(Gacha.Counter.fromMap(null).equals(Gacha.Counter.ZERO), "missing counter = zero");

        System.out.println("GachaTest: " + ok + " ok, " + BAD.size() + " bad");
        for (String s : BAD) System.out.println("  BAD " + s);
        if (!BAD.isEmpty()) System.exit(1);
    }

    private static List<String> problems(String name, List<String> f5, List<String> f4, double r5, double r4, int hard, int soft, long start, long end,
            int price) {
        return new Gacha.Spec("t", name, f5, f4, r5, r4, hard, soft, 10, 50, start, end, price).problems();
    }
}
