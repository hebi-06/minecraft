package mmorpg.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 0.12.6 시험 (JUnit 없이 main — check-core.sh): 사신 계열 룬 (피의 초승달 — 0.12.6 에는 낫 전용, 0.27.0 부터 아무 무기나) · 성기사 룬 적용 스탯 (체력 + 힘 × 0.5) (자바 전용 덧붙임).
 * <ul>
 * <li>계열 17개 (중력 다음 사신), 룬 아이템 85 · 사신 다섯 등급 (이름 · 함수 · 배율 합 4 · 7 · 12 · 20 · 32 — 출혈 포함), 뽑기에 같은 확률</li>
 * <li>무기 조건 없음 (0.27.0 사용자 "사신의 룬 조건인 낫을 없애고"): weaponOnly 없음 · usableWith (낫 · 칼 · 맨손 모두), 설명 줄에 "낫 전용" 없음 (출혈 · 흡혈 · 낙인 · 달 추락)</li>
 * <li>적용 스탯: 사신 = 민첩 + 마력 × 0.5, 성기사 = 체력 + 힘 × 0.5</li>
 * <li>적월 강림 돌파 (3돌: 범위 · 칼날 · 출혈, 6돌: 추락 위력 · 범위 · 칼날 · 흡혈)</li>
 * </ul>
 * 하나라도 틀리면 종료 코드 1.
 */
public final class RunesMoreTest {
    private RunesMoreTest() {}

    private static final List<String> BAD = new ArrayList<>();
    private static int ok;

    private static void check(boolean cond, String what) {
        if (cond) ok++;
        else BAD.add(what);
    }

    private static void near(double a, double b, String what) {
        check(Math.abs(a - b) < 1e-9, what + " (" + a + " vs " + b + ")");
    }

    private static Map<String, Double> stats(double str, double dex, double intl, double vit) {
        Map<String, Double> s = new HashMap<>();
        s.put("str", str);
        s.put("dex", dex);
        s.put("int", intl);
        s.put("vit", vit);
        s.put("lvmax", 40.0);
        return s;
    }

    private static String plain(List<String> lines) {
        return String.join("\n", lines).replaceAll("§.", "");
    }

    public static void main(String[] argv) {
        check(Rules.hasMore(), "overlay present");
        List<String> order = Runes.FAMILY_ORDER;
        check(order.size() == 23 && order.indexOf("reaper") == order.indexOf("gravity") + 1, "family order " + order);

        // 사신 다섯 등급
        String[] fns = {"bloodCrescent", "bloodHarvest", "bloodWhirl", "bloodBrand", "crimsonMoon"};
        String[] names = {"피의 초승달", "핏빛 수확", "피의 회오리", "피의 낙인", "적월 강림"};
        double[] totals = {4, 7, 12, 20, 32};
        for (int i = 0; i < 5; i++) {
            String g = Runes.GRADE_ORDER.get(i);
            Runes.Rune r = Runes.parseRune(Runes.runeId("reaper", g));
            check(r != null, "reaper " + g);
            if (r == null) continue;
            check(r.famName().equals("사신") && r.stat().equals("dex"), "family " + r.famName());
            check(r.def().str("fn").equals(fns[i]) && r.def().str("name").equals(names[i]), "def " + g + " " + r.def().str("fn"));
            near(Runes.runeTotalMult(r.def()), totals[i], "total " + g);
            // 같은 등급 다른 피해 룬과 같은 배율 합 (힘 계열)
            double strTotal = Runes.runeTotalMult(Runes.parseRune(Runes.runeId("str", g)).def());
            check(Math.abs(Runes.runeTotalMult(r.def()) - strTotal) < 0.1, "same total as str " + g + " (" + strTotal + ")");
            List<String> lore = Runes.runeLore(r, false);
            String text = plain(lore);
            check(!text.contains("전용"), "lore: no weapon-only line (0.27.0) " + g);
            check(!text.contains("중독"), "no poison wording " + g);
            if (r.def().truthy("dot")) check(text.contains("출혈 위력×" + r.def().text("dot")), "lore bleed " + g + ": " + text);
            if (r.def().truthy("healMax")) check(text.contains("흡혈"), "lore lifesteal " + g);
            for (String l : lore) check(Js.utf8Length(l) <= 60, "lore line short " + g + ": " + l);
        }
        String moon = plain(Runes.runeLore(Runes.parseRune("rpg:rune_reaper_transcend"), false));
        check(moon.contains("달 추락 위력×14") && moon.contains("6번"), "crimson moon lore " + moon);
        check(plain(Runes.runeLore(Runes.parseRune("rpg:rune_reaper_hero"), false)).contains("낙인 5명"), "brand lore");
        // 독 표창은 그대로 중독, 다른 계열은 무기 전용이 아님
        Runes.Rune stars = Runes.parseRune("rpg:rune_assassin_rare");
        check(plain(Runes.runeLore(stars, false)).contains("중독") && !plain(Runes.runeLore(stars, false)).contains("전용"), "poison stars lore unchanged");

        // 0.27.0: 사신 룬의 낫 조건을 뺌 (사용자 "사신의 룬 조건인 낫을 없애고") — 무기 전용 계열이 없음
        Runes.Rune reaper = Runes.parseRune("rpg:rune_reaper_common");
        Runes.Rune str = Runes.parseRune("rpg:rune_str_common");
        check(Runes.weaponOnly("reaper") == null && Runes.weaponOnly("str") == null, "weaponOnly: none");
        check(Runes.usableWith(reaper, "scythe") && Runes.usableWith(reaper, "sword") && Runes.usableWith(reaper, null), "reaper with any weapon");
        check(Runes.usableWith(str, null) && Runes.usableWith(str, "scythe"), "other families: any weapon");
        for (String fam : Runes.ALL_FAMILIES) check(Runes.weaponOnly(fam) == null, fam + " no weapon-only");
        check(Runes.weaponName("scythe").equals("낫"), "weapon name");

        // 적용 스탯
        Map<String, Double> s = stats(60, 100, 40, 100);
        near(Runes.runeStat(s, "reaper").value(), 100 + 40 * 0.5, "reaper applied = dex + int × 0.5");
        near(Runes.runeStat(s, "holy").value(), 100 + 60 * 0.5, "holy applied = vit + str × 0.5");
        Map<String, Double> vitOnly = stats(0, 0, 0, 100);
        near(Runes.runeStat(vitOnly, "holy").value(), 100, "holy with vit only");
        Obj holy = Rules.get().obj("RUNE_FAMILIES").obj("holy");
        check(holy.obj("coef").keys().size() == 2 && holy.obj("coef").num("str") == 0.5 && holy.str("stat").equals("vit"), "holy coef");
        check(plain(Runes.runeLore(Runes.parseRune("rpg:rune_holy_common"), false)).contains("체력 1.0 + 힘 0.5"), "holy lore coef");

        // 뽑기: 계열은 23개에서 같은 확률 — 사신 자리
        int at = order.indexOf("reaper");
        double[] seq = {0.0, (at + 0.5) / order.size()};
        int[] k = {0};
        Runes.Roll roll = Runes.rollRune(() -> seq[k[0]++], false);
        check(roll.family().equals("reaper") && roll.grade().equals("common"), "roll reaper " + roll);
        check(String.valueOf(Rules.get().obj("RUNE").list("ticketLore")).contains("계열 23개"), "ticket lore 23");
        check(Rules.get().obj("RUNE").obj("color").has("reaper"), "reaper color");

        // 적월 강림 돌파
        Runes.Rune cm = Runes.parseRune("rpg:rune_reaper_transcend");
        check(RuneBreak.tiered(cm), "crimson moon tiered");
        Obj d3 = RuneBreak.def(cm, 3);
        near(d3.num("radius"), 8.5, "3: radius");
        near(d3.num("hits"), 7, "3: hits");
        near(d3.num("dot"), 1, "3: bleed");
        near(d3.num("dots"), 4, "3: bleed count");
        Obj d6 = RuneBreak.def(cm, 6);
        near(d6.num("crush"), 22, "6: crush");
        near(d6.num("radius"), 10, "6: radius");
        near(d6.num("hits"), 8, "6: hits");
        near(d6.num("heal"), 0.08 * RuneBreak.coef(6), "6: lifesteal × coef");
        near(Runes.runeTotalMult(d6), 3 * 8 + 22 + 1 * 4, "6: total");
        check(cm.def().numOrNull("dot") == null, "no bleed at 0");

        // 0.25.0 두 스탯 계열 여섯 (사용자: "룬에 마력+체력, 체력+힘, 민첩+힘 등 다양한 스탯 연계" → 조합마다 새 계열 · 보조 × 0.5)
        String[][] pairs = {{"tide", "int", "vit", "해류"}, {"golem", "vit", "int", "골렘"}, {"monk", "dex", "str", "무투"}, {"hunter", "dex", "vit", "사냥꾼"},
            {"volcano", "str", "int", "화산"}, {"gladiator", "vit", "dex", "투사"}};
        check(Runes.PAIR_FAMILIES.size() == 6 && order.indexOf("tide") == order.indexOf("reaper") + 1 && order.indexOf("body") == order.indexOf("gladiator") + 1,
                "pair families after reaper " + order);
        java.util.Set<String> fnsSeen = new java.util.HashSet<>();
        java.util.Set<String> pairSet = new java.util.HashSet<>();
        for (String[] p : pairs) {
            String fam = p[0];
            Obj f = Rules.get().obj("RUNE_FAMILIES").obj(fam);
            check(f.str("stat").equals(p[1]) && f.str("name").equals(p[3]) && f.obj("coef").keys().size() == 2 && f.obj("coef").num(p[1]) == 1
                    && f.obj("coef").num(p[2]) == 0.5 && !f.truthy("weapon") && !f.truthy("limited"), fam + " family " + f);
            check(Rules.get().obj("RUNE").obj("color").has(fam), fam + " color");
            check(Runes.weaponOnly(fam) == null && !Runes.limited(fam), fam + " any weapon · not limited");
            pairSet.add(p[1] + "+" + p[2]);
            // 적용 스탯 = 주 + 보조 × 0.5 (보조만 찍으면 다른 스탯 0.85 가 큼)
            Map<String, Double> s2 = stats(0, 0, 0, 0);
            s2.put(p[1], 100.0);
            s2.put(p[2], 60.0);
            near(Runes.runeStat(s2, fam).value(), 100 + 60 * 0.5, fam + " applied = main + sub × 0.5");
            Map<String, Double> subOnly = stats(0, 0, 0, 0);
            subOnly.put(p[2], 100.0);
            near(Runes.runeStat(subOnly, fam).value(), 85, fam + " sub only → off stat 0.85");
            for (int i = 0; i < 5; i++) {
                String g = Runes.GRADE_ORDER.get(i);
                Runes.Rune r = Runes.parseRune(Runes.runeId(fam, g));
                check(r != null, fam + " " + g);
                if (r == null) continue;
                check(r.famName().equals(p[3]) && r.stat().equals(p[1]), fam + " " + g + " family name");
                near(Runes.runeTotalMult(r.def()), totals[i], fam + " " + g + " total");
                String fn = r.def().str("fn");
                check(fnsSeen.add(fn), "unique fn " + fn);
                check(r.cd() == Rules.get().obj("RUNE_GRADES").obj(g).num("cd"), fam + " " + g + " cooldown");
                List<String> flow = Runes.flowLines(r.def());
                check(!flow.isEmpty(), fam + " " + g + " flow");
                for (String l : flow) check(!l.contains("{") && !l.contains("?") && l.length() <= 34, fam + " " + g + " flow line: " + l);
                String text = plain(Runes.runeLore(r, false));
                check(text.contains(p[3] + " 룬") && text.contains(flow.get(0)) && text.contains(Runes.coefText(fam)), fam + " " + g + " lore: " + text);
                if (r.def().truthy("dot")) check(text.contains(Runes.dotName(fam, r.def()) + " 위력×"), fam + " " + g + " dot lore");
            }
            Runes.Rune ult = Runes.parseRune(Runes.runeId(fam, "transcend"));
            check(RuneBreak.tiered(ult), fam + " transcend tiered");
            double t3 = Runes.runeTotalMult(RuneBreak.def(ult, 3));
            double t6 = Runes.runeTotalMult(RuneBreak.def(ult, 6));
            check(t3 >= 38 && t3 <= 39.5 && Math.abs(t6 - 50) < 1e-9, fam + " transcend totals 3 · 6 = " + t3 + " · " + t6);
            check(RuneBreak.def(ult, 6).num("radius") > ult.def().num("radius"), fam + " 6 radius grows");
        }
        check(pairSet.equals(java.util.Set.of("int+vit", "vit+int", "dex+str", "dex+vit", "str+int", "vit+dex")), "six missing pairs " + pairSet);
        check(Runes.dotName("volcano", Runes.parseRune("rpg:rune_volcano_common").def()).equals("화상") && Runes.dotName("reaper", cm.def()).equals("출혈")
                && Runes.dotName("assassin", stars.def()).equals("중독"), "dot names");
        double ge3 = Runes.runeTotalMult(RuneBreak.def(Runes.parseRune("rpg:rune_volcano_transcend"), 3));
        near(ge3, 3 * 7 + 14 + 1 * 4, "grand eruption 3: burn after the blast");

        System.out.println("RunesMoreTest: " + ok + " ok, " + BAD.size() + " bad");
        for (String b : BAD) System.out.println("  BAD " + b);
        if (!BAD.isEmpty()) System.exit(1);
    }
}
