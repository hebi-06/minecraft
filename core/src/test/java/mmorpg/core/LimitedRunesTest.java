package mmorpg.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 0.16.0 시험 (JUnit 없이 main — check-core.sh): 한정 초월 룬 셋 (만상귀일 · 칠성 강림 · 천공의 눈 — 자바 전용 덧붙임).
 * <ul>
 * <li>계열: RUNE_LIMITED_ORDER = arcana · dipper · skyeye · endless (0.17.0 무간참역) · terra (0.18.0 대지 가르기) · yggdrasil (0.19.0 세계수의 가호 — 버프),
 * 뽑기 계열 (FAMILY_ORDER 17) 에는 없음, 모든 계열 23 (뒤에 여섯), 초월 하나씩</li>
 * <li>0.19.0 세계수의 가호: 피해 없음, 가호 = graceUp (받는 피해 graceCut) + min(gracePer × 네 스탯의 합, graceCap), 돌파 계수가 값 넷 모두에
 * (40렙 125포인트 0돌 20% · 3돌 26% · 6돌 32%, 스탯 합 150 에서 최대), 단계는 지속 12 → 14 → 16초 · 반지름 8 → 10 → 12칸, 6돌 연출 (세계수 개화)</li>
 * <li>적용 스탯 = 네 스탯의 합 × 1.0 (스탯을 어떻게 찍어도 같은 위력 — 40렙 125포인트 = 한 스탯만 찍은 자기 계열 룬과 같은 P 42), 알림 글자 "총 스탯 N"</li>
 * <li>배율 합 34 (다른 초월 32 보다 6.25% 위 — 사용자 요청 '5% ~ 12% 더 쎄게'), 재사용 대기 36초, 설명 줄 (한정 룬 · 네 스탯의 합 · 흐름 · 총 피해)</li>
 * <li>뽑기: 룬 뽑기권 · 고급 · 스탯 뽑기권에는 나오지 않음, 한정 뽑기의 초월 픽업으로만 (영웅 픽업 · 놓친 결과 · 다른 등급에는 없음)</li>
 * <li>돌파 3 · 6돌 (배율 합 42 · 55 — 0 · 3 · 6돌 모두 처음 설계 (32 · 38 ~ 39.2 · 49.4 ~ 50 — 다른 초월과 같은 줄) 보다 5 ~ 12% 위),
 * 6돌 전용 연출 (finale — 피해 없음: 근원의 프리즘 · 별똥별 비 · 심판의 눈)</li>
 * </ul>
 * 하나라도 틀리면 종료 코드 1.
 */
public final class LimitedRunesTest {
    private LimitedRunesTest() {}

    private static final List<String> BAD = new ArrayList<>();
    private static int ok;

    private static void check(boolean cond, String what) {
        if (cond) ok++;
        else BAD.add(what);
    }

    private static void near(double a, double b, String what) {
        check(Math.abs(a - b) < 1e-9, what + " (" + a + " vs " + b + ")");
    }

    private static Map<String, Double> stats(double str, double dex, double vit, double intl) {
        Map<String, Double> s = new HashMap<>();
        s.put("str", str);
        s.put("dex", dex);
        s.put("vit", vit);
        s.put("int", intl);
        s.put("lvmax", 40.0);
        return s;
    }

    private static String plain(List<String> lines) {
        return String.join("\n", lines).replaceAll("§.", "");
    }

    public static void main(String[] argv) {
        check(Rules.hasMore(), "overlay present");
        List<String> lim = Runes.LIMITED_ORDER;
        check(lim.equals(List.of("arcana", "dipper", "skyeye", "endless", "terra", "yggdrasil")), "limited order " + lim);
        check(Runes.FAMILY_ORDER.size() == 17, "draw families stay 17 " + Runes.FAMILY_ORDER.size());
        for (String f : lim) check(!Runes.FAMILY_ORDER.contains(f), f + " not in the draw families");
        check(Runes.ALL_FAMILIES.size() == 23 && Runes.ALL_FAMILIES.subList(17, 23).equals(lim), "all families " + Runes.ALL_FAMILIES);
        check(!Runes.limited("int") && Runes.limited("arcana"), "limited()");

        // 초월 하나씩 · 이름 · 함수 · 배율 합 34 · 재사용 대기 36초
        String[] fams = {"arcana", "dipper", "skyeye", "endless", "terra"};
        String[] fns = {"arcanaUnity", "sevenStars", "heavenEye", "endlessSlash", "earthSplitter"};
        String[] names = {"만상귀일", "칠성 강림", "천공의 눈", "무간참역", "대지 가르기"};
        String[] famNames = {"만상", "칠성", "천공", "무간", "대지"};
        for (int i = 0; i < fams.length; i++) {
            for (String g : Runes.GRADE_ORDER) {
                Runes.Rune r = Runes.parseRune(Runes.runeId(fams[i], g));
                check(g.equals("transcend") == (r != null), fams[i] + " " + g + " exists only as transcend");
            }
            Runes.Rune r = Runes.parseRune(Runes.runeId(fams[i], "transcend"));
            if (r == null) continue;
            check(r.def().str("fn").equals(fns[i]) && r.def().str("name").equals(names[i]), fams[i] + " fn/name " + r.def().str("fn"));
            check(r.famName().equals(famNames[i]) && r.stat().equals("int"), fams[i] + " family name / magic stat");
            near(r.cd(), 36, fams[i] + " cd");
            near(Runes.runeTotalMult(r.def()), 34, fams[i] + " total ×34");
            check(Rules.get().obj("RUNE").obj("color").has(fams[i]), fams[i] + " color");
            String lore = plain(Runes.runeLore(r, false));
            check(lore.contains("한정 룬") && lore.contains("한정 뽑기의 초월 픽업으로만"), fams[i] + " lore limited: " + lore);
            check(lore.contains("네 스탯의 합 × 1.0") && lore.contains("어떻게 찍어도 같음"), fams[i] + " lore sum");
            check(lore.contains("총 피해") && lore.contains("위력×34"), fams[i] + " lore total");
            check(!lore.contains("다른 스탯 0.85"), fams[i] + " no off-stat line");
        }
        check(plain(Runes.runeLore(Runes.parseRune("rpg:rune_arcana_transcend"), false)).contains("(17 + 17)"), "arcana parts 17 + 17");
        check(plain(Runes.runeLore(Runes.parseRune("rpg:rune_dipper_transcend"), false)).contains("(2×7 + 20)"), "dipper parts 2×7 + 20");
        check(plain(Runes.runeLore(Runes.parseRune("rpg:rune_skyeye_transcend"), false)).contains("(3×6 + 16)"), "skyeye parts 3×6 + 16");
        check(plain(Runes.runeLore(Runes.parseRune("rpg:rune_endless_transcend"), false)).contains("(2×10 + 14)"), "endless parts 2×10 + 14");
        check(plain(Runes.runeLore(Runes.parseRune("rpg:rune_terra_transcend"), false)).contains("(3×6 + 16)"), "terra parts 3×6 + 16");
        check(Runes.parseRune("rpg:rune_arcana_hero") == null && Runes.parseRune("rpg:rune_nope_transcend") == null, "no other ids");

        // 적용 스탯 = 네 스탯의 합 (40렙 125포인트면 어떻게 찍어도 P 42 = 한 스탯만 찍은 자기 룬)
        for (double[] st : new double[][] {{125, 0, 0, 0}, {0, 0, 0, 125}, {42, 42, 41, 0}, {10, 5, 20, 90}, {31, 31, 31, 32}}) {
            Map<String, Double> s = stats(st[0], st[1], st[2], st[3]);
            for (String f : fams) {
                Runes.Applied a = Runes.runeStat(s, f);
                near(a.value(), 125, f + " applied " + java.util.Arrays.toString(st));
                near(Runes.runePower(s, f), 12 * (1 + 125 * 0.02), f + " P " + java.util.Arrays.toString(st));
                check(!a.off(), f + " never the off-stat");
                check(Runes.runeStatLabel(a).equals("총 스탯 125"), f + " label " + Runes.runeStatLabel(a));
            }
        }
        near(Runes.runePower(stats(0, 0, 0, 125), "int"), 42, "pure mage meteor P 42 (same)");
        near(Runes.runePower(stats(42, 42, 41, 0), "int"), 12 * (1 + 42 * 0.85 * 0.02), "balanced build meteor P (off 0.85)");
        near(Runes.runeStat(stats(150, 150, 150, 150), "arcana").value(), 600, "600 = all four");
        check(Runes.runeStatLabel(Runes.runeStat(stats(10, 5, 20, 90), "int")).equals("마력 90 × 1"), "other labels unchanged " + Runes.runeStatLabel(Runes.runeStat(stats(10, 5, 20, 90), "int")));

        // 뽑기: 룬 뽑기권 · 고급 — 한정 계열은 나오지 않음 (계열 자리를 고르게 모두 훑음)
        for (boolean adv : new boolean[] {false, true}) {
            for (int fi = 0; fi < 40; fi++) {
                double[] seq = {0.999, (fi + 0.5) / 40.0};
                int[] k = {0};
                Runes.Roll roll = Runes.rollRune(() -> seq[k[0]++], adv);
                check(!Runes.limited(roll.family()), "roll " + roll);
            }
        }
        check(Gacha.standard().families().equals(Runes.FAMILY_ORDER) && Gacha.advanced().families().equals(Runes.FAMILY_ORDER), "standard / advanced pools");
        boolean threw = false;
        try {
            Gacha.single("arcana");
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check(threw, "no single-family ticket for a limited family");

        // 한정 뽑기 설정: 초월 픽업만 (영웅 픽업이면 까닭)
        long now = 1_000_000L;
        Gacha.Spec ok5 = Gacha.Spec.defaults("t", "별의 축제", List.of("dipper"), List.of("storm"), now);
        check(ok5.problems().isEmpty(), "limited family as transcend pickup ok " + ok5.problems());
        Gacha.Spec mixed = Gacha.Spec.defaults("t", "섞음", List.of("arcana", "int"), List.of(), now);
        check(mixed.problems().isEmpty(), "limited + normal pickup ok " + mixed.problems());
        Gacha.Spec bad4 = Gacha.Spec.defaults("t", "영웅", List.of("int"), List.of("skyeye"), now);
        check(bad4.problems().contains("한정 룬은 초월 픽업만: skyeye"), "limited hero pickup refused " + bad4.problems());
        Gacha.Spec unknown = Gacha.Spec.defaults("t", "모름", List.of("nope"), List.of(), now);
        check(unknown.problems().contains("모르는 계열: nope"), "unknown family " + unknown.problems());
        check(ok5.banner().families().equals(Runes.FAMILY_ORDER), "limited banner pool = draw families");

        // 한정 뽑기 모의 (시드 고정): 한정 계열은 초월 · 픽업일 때만, 놓친 결과 · 다른 등급에는 없음
        Gacha.Banner b = Gacha.Spec.defaults("t", "만상", List.of("arcana", "skyeye"), List.of("int", "storm"), now).banner();
        Random rnd = new Random(7);
        Gacha.Counter c = Gacha.Counter.ZERO;
        Map<String, Integer> seen = new LinkedHashMap<>();
        int top = 0;
        int lostTop = 0;
        for (int i = 0; i < 20000; i++) {
            Gacha.Result res = Gacha.pull(b, c, 1, rnd::nextDouble);
            c = res.counter();
            Gacha.Pull p = res.pulls().get(0);
            if (Runes.limited(p.family())) {
                seen.merge(p.family(), 1, Integer::sum);
                check(p.grade().equals("transcend") && p.featured() && !p.lost(), "limited only as a featured transcend " + p);
            }
            if (p.grade().equals("transcend")) {
                top++;
                if (p.lost()) {
                    lostTop++;
                    check(!Runes.limited(p.family()), "lost pull is not limited " + p);
                }
            }
            check(Runes.parseRune(p.id()) != null, "pulled rune exists " + p.id());
        }
        check(seen.getOrDefault("arcana", 0) > 0 && seen.getOrDefault("skyeye", 0) > 0 && !seen.containsKey("dipper"), "only the picked limited families " + seen);
        check(top > 100 && lostTop > 0 && lostTop < top, "transcend " + top + " lost " + lostTop);

        // 돌파 3 · 6돌 (배율 합: 0돌 34 · 3돌 42 · 6돌 55)
        double[][] totals = {{17 + 17, 25 + 17, 29 + 26}, {2 * 7 + 20, 3 * 7 + 21, 3.5 * 8 + 27}, {3 * 6 + 16, 4 * 6 + 18, 5 * 7 + 20},
            {2 * 10 + 14, 2.25 * 12 + 15, 2.5 * 14 + 20}, {3 * 6 + 16, 3 * 7 + 21, 3.5 * 8 + 27}};
        // 처음 설계 (다른 초월과 같은 줄) — 사용자 요청 '5% ~ 12% 더 쎄게': 0 · 3 · 6돌 모두 이 값의 1.05 ~ 1.12 배
        // (0.17.0 무간참역 · 0.18.0 대지 가르기는 처음부터 이 줄로 — 다른 초월의 기준 32 · 39 · 50 보다 5 ~ 12% 위)
        double[][] first = {{32, 38, 50}, {32, 2.6 * 7 + 21, 3.2 * 8 + 24}, {32, 3.6 * 6 + 17, 4.2 * 7 + 20}, {32, 39, 50}, {32, 39, 50}};
        for (int i = 0; i < fams.length; i++) {
            Runes.Rune r = Runes.parseRune(Runes.runeId(fams[i], "transcend"));
            check(RuneBreak.tiered(r), fams[i] + " tiered");
            near(Runes.runeTotalMult(RuneBreak.def(r, 0)), totals[i][0], fams[i] + " 0 total");
            near(Runes.runeTotalMult(RuneBreak.def(r, 3)), totals[i][1], fams[i] + " 3 total");
            near(Runes.runeTotalMult(RuneBreak.def(r, 6)), totals[i][2], fams[i] + " 6 total");
            for (int lv = 0; lv <= 6; lv++) {
                double cur = Runes.runeTotalMult(RuneBreak.def(r, lv));
                double was = first[i][lv < 3 ? 0 : lv < 6 ? 1 : 2];
                check(cur >= was * 1.05 - 1e-9 && cur <= was * 1.12 + 1e-9, fams[i] + " " + lv + "돌 +" + Math.round((cur / was - 1) * 1000) / 10.0 + "% (5 ~ 12%)");
            }
            RuneBreak.Extra fin = RuneBreak.extra(fns[i]);
            check(fin != null && "finale".equals(fin.type()) && fin.at() == 6, fams[i] + " 6 finale (no echo)");
            check(RuneBreak.extraFor(r, 5) == null && RuneBreak.extraFor(r, 6) == fin, fams[i] + " finale from 6");
            near(Runes.runeTotalMult(RuneBreak.echoDef(r, 6)), totals[i][2], fams[i] + " finale adds no damage");
            for (String l : RuneBreak.tierLines(r, 6, false)) check(l.replaceAll("§.", "").length() <= 40, fams[i] + " tier line short: " + l);
        }
        near(RuneBreak.def(Runes.parseRune("rpg:rune_arcana_transcend"), 6).num("stun"), 30, "arcana 6 stun");

        // 0.19.0 세계수의 가호 (한정 버프 룬 — 사용자 요청 "가하는 피해 증가 버프 · 받는 피해 감소")
        for (String g : Runes.GRADE_ORDER) check(g.equals("transcend") == (Runes.parseRune(Runes.runeId("yggdrasil", g)) != null), "yggdrasil only transcend " + g);
        Runes.Rune ygg = Runes.parseRune("rpg:rune_yggdrasil_transcend");
        check(ygg != null, "yggdrasil rune");
        if (ygg != null) {
            check(ygg.def().str("fn").equals("worldTreeGrace") && ygg.def().str("name").equals("세계수의 가호") && ygg.famName().equals("세계수"), "yggdrasil def");
            near(ygg.cd(), 36, "yggdrasil cd");
            near(Runes.runeTotalMult(ygg.def()), 0, "yggdrasil deals no damage");
            check(Runes.isGrace(ygg.def()) && !Runes.isGrace(Runes.parseRune("rpg:rune_terra_transcend").def()) && !Runes.isGrace(Runes.parseRune("rpg:rune_body_transcend").def()),
                    "isGrace only the grace rune");
            check(Rules.get().obj("RUNE").obj("color").has("yggdrasil"), "yggdrasil color");
            double S = Runes.runeStat(stats(31, 31, 31, 32), "yggdrasil").value();
            near(S, 125, "yggdrasil applied = four stats");
            near(Runes.runeStat(stats(125, 0, 0, 0), "yggdrasil").value(), 125, "yggdrasil any build");
            near(Runes.graceUp(ygg.def(), S), 0.2, "0 up 20%");
            near(Runes.graceCut(ygg.def(), S), 0.2, "0 cut 20%");
            near(Runes.graceUp(ygg.def(), 0), 0.1, "base 10% without stats");
            near(Runes.graceUp(ygg.def(), Double.NaN), 0.1, "NaN stats = base");
            near(Runes.graceUp(ygg.def(), 100), 0.18, "100 → 18%");
            near(Runes.graceUp(ygg.def(), 150), 0.22, "150 → 22% (cap)");
            near(Runes.graceCut(ygg.def(), 600), 0.22, "cap 22%");
            near(Runes.graceUp(RuneBreak.def(ygg, 1), S), 0.22, "1 × 1.1");
            near(Runes.graceUp(RuneBreak.def(ygg, 3), S), 0.26, "3 up 26%");
            near(Runes.graceCut(RuneBreak.def(ygg, 3), S), 0.26, "3 cut 26%");
            near(Runes.graceUp(RuneBreak.def(ygg, 6), S), 0.32, "6 up 32%");
            near(Runes.graceCut(RuneBreak.def(ygg, 6), 600), 0.22 * 1.6, "6 cap 35.2%");
            double[][] tier = {{240, 8, 2.8}, {280, 10, 2.8}, {320, 12, 3.4}};
            for (int i = 0; i < 3; i++) {
                Obj d = RuneBreak.def(ygg, i * 3);
                near(d.num("ticks"), tier[i][0], "ticks " + i * 3);
                near(d.num("radius"), tier[i][1], "radius " + i * 3);
                near(d.num("tree"), tier[i][2], "tree " + i * 3);
            }
            RuneBreak.Extra fin = RuneBreak.extra("worldTreeGrace");
            check(fin != null && "finale".equals(fin.type()) && fin.at() == 6, "yggdrasil 6 finale");
            for (String l : RuneBreak.tierLines(ygg, 6, false)) check(l.replaceAll("§.", "").length() <= 40, "yggdrasil tier line short: " + l);
            String lore = plain(Runes.runeLore(ygg, false));
            check(lore.contains("한정 룬") && lore.contains("네 스탯의 합 × 1.0"), "yggdrasil lore limited / sum: " + lore);
            check(lore.contains("가하는 피해 +20%") && lore.contains("받는 피해 -20%") && lore.contains("반지름 8칸") && lore.contains("12초"), "yggdrasil lore grace: " + lore);
            check(lore.contains("더한 피해 · 막은 피해") && !lore.contains("위력") && !lore.contains("총 피해"), "yggdrasil lore no damage: " + lore);
            String lore6 = plain(RuneBreak.lore(ygg, 6, false));
            check(lore6.contains("가하는 피해 +32%") && lore6.contains("반지름 12칸") && lore6.contains("16초"), "yggdrasil 6 lore: " + lore6);
        }
        near(RuneBreak.def(Runes.parseRune("rpg:rune_dipper_transcend"), 6).num("hits"), 8, "dipper 6: 8 stars");
        near(RuneBreak.def(Runes.parseRune("rpg:rune_skyeye_transcend"), 6).num("hits"), 7, "skyeye 6: 7 pulses");
        check(RuneBreak.changeTexts(Runes.parseRune("rpg:rune_dipper_transcend"), 6).contains("별 7 → 8개 (북극성)"), "polaris text");

        System.out.println("LimitedRunesTest: " + ok + " ok, " + BAD.size() + " bad");
        for (String x : BAD) System.out.println("  BAD " + x);
        if (!BAD.isEmpty()) System.exit(1);
    }
}
