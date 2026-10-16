package mmorpg.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 0.12.6 시험 (JUnit 없이 main — check-core.sh): 사신 계열 룬 (낫 전용 — 피의 초승달) · 성기사 룬 적용 스탯 (체력 + 힘 × 0.5) (자바 전용 덧붙임).
 * <ul>
 * <li>계열 17개 (중력 다음 사신), 룬 아이템 85 · 사신 다섯 등급 (이름 · 함수 · 배율 합 4 · 7 · 12 · 20 · 32 — 출혈 포함), 뽑기에 같은 확률</li>
 * <li>낫 전용: weaponOnly · usableWith (낫만 · 맨손이나 다른 무기는 안 됨, 다른 계열은 아무 무기나), 설명 줄 (낫 전용 · 출혈 · 흡혈 · 낙인 · 달 추락)</li>
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
        check(order.size() == 17 && order.indexOf("reaper") == order.indexOf("gravity") + 1, "family order " + order);

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
            check(text.contains("낫 전용"), "lore scythe only " + g);
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

        // 낫 전용
        Runes.Rune reaper = Runes.parseRune("rpg:rune_reaper_common");
        Runes.Rune str = Runes.parseRune("rpg:rune_str_common");
        check("scythe".equals(Runes.weaponOnly("reaper")) && Runes.weaponOnly("str") == null, "weaponOnly");
        check(Runes.usableWith(reaper, "scythe"), "reaper with scythe");
        check(!Runes.usableWith(reaper, "sword") && !Runes.usableWith(reaper, null), "reaper without scythe");
        check(Runes.usableWith(str, null) && Runes.usableWith(str, "scythe"), "other families: any weapon");
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

        // 뽑기: 계열은 17개에서 같은 확률 — 사신 자리
        int at = order.indexOf("reaper");
        double[] seq = {0.0, (at + 0.5) / order.size()};
        int[] k = {0};
        Runes.Roll roll = Runes.rollRune(() -> seq[k[0]++], false);
        check(roll.family().equals("reaper") && roll.grade().equals("common"), "roll reaper " + roll);
        check(String.valueOf(Rules.get().obj("RUNE").list("ticketLore")).contains("계열 17개"), "ticket lore 17");
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

        System.out.println("RunesMoreTest: " + ok + " ok, " + BAD.size() + " bad");
        for (String b : BAD) System.out.println("  BAD " + b);
        if (!BAD.isEmpty()) System.exit(1);
    }
}
