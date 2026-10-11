package mmorpg.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 룬 돌파 계산 시험 (JUnit 없이 main — check-core.sh): rune_break.json 의 모양 (6돌 · +10% · 초월 16종 3 · 6돌), 계수, 합치기, 갈기 값,
 * 단계별 정의 (초월 강화 · 회복 · 보호막 × 계수, 보호막 상한은 그대로), 글자 (별 · 이름표 · 바뀌는 값 · 설명 줄). 하나라도 틀리면 종료 코드 1.
 */
public final class RuneBreakTest {
    private RuneBreakTest() {}

    private static final List<String> BAD = new ArrayList<>();
    private static int ok;

    private static void check(boolean cond, String what) {
        if (cond) ok++;
        else BAD.add(what);
    }

    private static void near(double a, double b, String what) {
        check(Math.abs(a - b) < 1e-9, what + " (" + a + " vs " + b + ")");
    }

    private static Runes.Rune rune(String family, String grade) {
        return Runes.parseRune(Runes.runeId(family, grade));
    }

    /** 색 코드를 뺀 글자 */
    private static String plain(String s) {
        return s.replaceAll("§.", "");
    }

    public static void main(String[] argv) {
        // 데이터
        check(RuneBreak.max() == 6, "max 6");
        near(RuneBreak.perLevel(), 0.1, "perLevel");
        check(RuneBreak.tiers().equals(List.of(3, 6)), "tiers " + RuneBreak.tiers());
        check(RuneBreak.scaleKeys().equals(List.of("heal", "shield")), "scale keys " + RuneBreak.scaleKeys());

        // 계수
        near(RuneBreak.coef(0), 1, "coef 0");
        near(RuneBreak.coef(3), 1.3, "coef 3");
        near(RuneBreak.coef(6), 1.6, "coef 6");
        near(RuneBreak.coef(9), 1.6, "coef clamps at 6");
        near(RuneBreak.coef(-2), 1, "coef clamps at 0");
        check(RuneBreak.coefText(3).equals("×1.3") && RuneBreak.coefText(0).equals("×1"), "coef text " + RuneBreak.coefText(3));

        // 합치기: 남는 룬 + 재료 + 1 ≤ 6
        check(RuneBreak.canMerge(0, 0) && RuneBreak.merged(0, 0) == 1, "0 + 0 → 1");
        check(RuneBreak.canMerge(3, 2) && RuneBreak.merged(3, 2) == 6, "3 + 2 → 6");
        check(RuneBreak.canMerge(5, 0) && RuneBreak.merged(5, 0) == 6, "5 + 0 → 6");
        check(!RuneBreak.canMerge(3, 3), "3 + 3 refused (7)");
        check(!RuneBreak.canMerge(6, 0), "6 + 0 refused");
        check(!RuneBreak.canMerge(-1, 0), "negative refused");

        // 갈기 값: 룬 하나 값 × (1 + 단계)
        Runes.Rune meteor = rune("int", "transcend");
        Runes.Rune common = rune("str", "common");
        near(RuneBreak.grindValue(meteor, 0), 50, "transcend 0 grind");
        near(RuneBreak.grindValue(meteor, 3), 200, "transcend 3 grind");
        near(RuneBreak.grindValue(common, 6), 7, "common 6 grind");

        // 연출 단계
        check(RuneBreak.fxTier(0) == 0 && RuneBreak.fxTier(2) == 0, "no fx below 3");
        check(RuneBreak.fxTier(3) == 3 && RuneBreak.fxTier(5) == 3, "fx 3 at 3..5");
        check(RuneBreak.fxTier(6) == 6, "fx 6 at 6");
        check(RuneBreak.fx(0) == null, "fx 0 = none");
        RuneBreak.Fx f3 = RuneBreak.fx(3);
        RuneBreak.Fx f6 = RuneBreak.fx(6);
        check(f3 != null && f3.edge() == 0 && !f3.awaken() && f3.skyTicks() == 1 && f3.skyDist() == 1, "fx 3 = the plain sky + flash");
        check(f6 != null && f6.edge() > 0 && f6.awaken() && f6.skyTicks() > 1 && f6.skyDist() < 1 && f6.flashHold() > 0, "fx 6 = deeper, edge glow, awaken");

        // 초월 16종 모두 3 · 6돌 강화 (값은 원래 정의에 있는 것 · 커짐 · 6돌 ≥ 3돌)
        Set<String> fns = new HashSet<>();
        for (String fam : Runes.FAMILY_ORDER) {
            Runes.Rune t = rune(fam, "transcend");
            check(t != null, fam + " transcend");
            if (t == null) continue;
            String fn = t.def().str("fn");
            fns.add(fn);
            check(RuneBreak.tiered(t), fn + " tiered");
            for (int tier : RuneBreak.tiers()) {
                List<RuneBreak.Change> cs = RuneBreak.changes(fn, tier);
                check(!cs.isEmpty(), fn + " " + tier + " has changes");
                check(!RuneBreak.changeTexts(t, tier).isEmpty(), fn + " " + tier + " has text");
                for (RuneBreak.Change c : cs) {
                    Double base = t.def().numOrNull(c.key());
                    check(base != null, fn + " " + tier + " key " + c.key() + " is in the rune");
                    check(base != null && c.to() > base, fn + " " + tier + " " + c.key() + " grows " + base + " → " + c.to());
                    check(c.div() > 0, fn + " div");
                }
                for (String s : RuneBreak.changeTexts(t, tier)) check(!s.contains("{") && !s.contains("?"), fn + " text filled: " + s);
            }
            for (RuneBreak.Change c6 : RuneBreak.changes(fn, 6)) {
                for (RuneBreak.Change c3 : RuneBreak.changes(fn, 3)) {
                    if (c3.key().equals(c6.key())) check(c6.to() > c3.to(), fn + " " + c6.key() + " 6 > 3");
                }
            }
            check(RuneBreak.tierLines(t, 0, false).size() >= 4, fn + " tier lines");
            for (String l : RuneBreak.tierLines(t, 6, false)) check(plain(l).length() <= 40, fn + " line short: " + plain(l));
        }
        check(fns.equals(new HashSet<>(RuneBreak.tieredFns())), "tiered fns = the 16 transcend runes " + RuneBreak.tieredFns());
        check(!RuneBreak.tiered(common) && !RuneBreak.tiered(rune("int", "hero")), "only transcend is tiered");

        // 단계별 정의
        check(RuneBreak.def(meteor, 0) == meteor.def() && RuneBreak.leveled(meteor, 0) == meteor, "0 = original");
        near(RuneBreak.def(meteor, 2).num("radius"), 6, "meteor 2: radius 6");
        near(RuneBreak.def(meteor, 3).num("radius"), 7.5, "meteor 3: radius 7.5");
        near(RuneBreak.def(meteor, 3).num("fire"), 8, "meteor 3: fire 8");
        near(RuneBreak.def(meteor, 5).num("lift"), 0.9, "meteor 5: lift 0.9");
        near(RuneBreak.def(meteor, 6).num("radius"), 9, "meteor 6: radius 9");
        near(RuneBreak.def(meteor, 6).num("fire"), 12, "meteor 6: fire 12");
        near(RuneBreak.def(meteor, 6).num("lift"), 1.3, "meteor 6: lift 1.3");
        near(RuneBreak.def(meteor, 3).num("mult"), 32, "mult stays at 3 (the power P carries the coef)");
        check(RuneBreak.def(meteor, 6).str("fn").equals("meteor") && RuneBreak.def(meteor, 6).str("name").equals("메테오"), "other keys kept");
        Runes.Rune angel = rune("holy", "transcend");
        near(RuneBreak.def(angel, 6).num("heal"), 0.03 * 1.6, "archangel 6 heal × 1.6");
        near(RuneBreak.def(angel, 6).num("shield"), 0.04 * 1.6, "archangel 6 shield × 1.6");
        near(RuneBreak.def(angel, 6).num("cap"), 0.35, "archangel 6 cap (not scaled)");
        near(RuneBreak.def(angel, 3).num("hits"), 10, "archangel 3 hits 10");
        Runes.Rune spring = rune("vit", "rare");
        near(RuneBreak.def(spring, 4).num("heal"), 0.14 * 1.4, "lifeSpring 4 heal × 1.4");
        Runes.Rune slam = rune("str", "rare");
        check(RuneBreak.def(slam, 6).keys().equals(slam.def().keys()), "non-transcend: same keys");
        for (String k : slam.def().keys()) {
            if (slam.def().get(k) instanceof Number n) near(RuneBreak.def(slam, 6).num(k), n.doubleValue(), "non-transcend " + k + " kept");
        }
        Runes.Rune lv = RuneBreak.leveled(meteor, 3);
        check(lv != meteor && lv.id().equals(meteor.id()) && lv.grade().equals("transcend") && lv.cd() == meteor.cd(), "leveled keeps the id · grade · cd");
        near(Runes.runeTotalMult(RuneBreak.def(rune("vit", "transcend"), 3)), 2.5 * 10, "undying 3: 10 pulses");

        // 6돌 더 세게 (위력 계수)
        near(RuneBreak.def(meteor, 6).num("mult"), 40, "meteor 6: mult 40");
        near(RuneBreak.def(meteor, 5).num("mult"), 32, "meteor 5: mult 32");
        near(RuneBreak.def(rune("gravity", "transcend"), 6).num("crush"), 28, "blackHole 6: crush 28");
        near(RuneBreak.def(rune("spellblade", "transcend"), 6).num("mult"), 20, "spellblade 6: mult 20");
        near(Runes.runeTotalMult(RuneBreak.def(meteor, 6)), 40, "meteor 6 lore total 40");

        // 단계 효과 (extra): 천붕 · 뇌신의 창 — 한 번 더 (echo)
        Runes.Rune fall = rune("str", "transcend");
        Runes.Rune spear = rune("storm", "transcend");
        int extras = 0;
        for (String fam : Runes.FAMILY_ORDER) {
            Runes.Rune t = rune(fam, "transcend");
            RuneBreak.Extra ex = RuneBreak.extra(t.def().str("fn"));
            if (ex == null) continue;
            extras++;
            String fn = t.def().str("fn");
            check("echo".equals(ex.type()), fn + " extra type " + ex.type());
            check(RuneBreak.tiers().contains(ex.at()), fn + " extra at a tier " + ex.at());
            check(ex.mult() > 0 && ex.mult() <= 1 && ex.after() > 0, fn + " extra mult / after");
            for (String k : ex.set().keySet()) check(t.def().numOrNull(k) != null, fn + " echo set key " + k + " is in the rune");
            check(!RuneBreak.extraText(ex).contains("{"), fn + " extra text " + RuneBreak.extraText(ex));
            check(RuneBreak.extraFor(t, ex.at() - 1) == null && RuneBreak.extraFor(t, ex.at()) == ex, fn + " extra from " + ex.at());
        }
        check(extras == 2, "two echo runes " + extras);
        check(RuneBreak.extraFor(rune("str", "hero"), 6) == null, "not for hero");
        RuneBreak.Extra fx6 = RuneBreak.extraFor(fall, 6);
        check(fx6 != null && Math.abs(fx6.mult() - 0.6) < 1e-9 && fx6.after() == 34, "heavenFall echo 60% after 34");
        near(RuneBreak.echoDef(fall, 6).num("delay"), 14, "heavenFall echo delay 14");
        near(RuneBreak.echoDef(fall, 6).num("radius"), 9, "heavenFall echo keeps the 6 radius");
        near(RuneBreak.echoDef(spear, 6).num("charge"), 10, "thunderSpear echo charge 10");
        check(RuneBreak.echoDef(meteor, 6) == RuneBreak.def(meteor, 6) || RuneBreak.echoDef(meteor, 6).num("mult") == 40, "no echo: plain def");
        check(RuneBreak.extraText(fx6).equals("검이 한 번 더 떨어짐 (지금 겨눈 곳 · 위력 60%)"), "echo text " + RuneBreak.extraText(fx6));
        List<String> fallLines = RuneBreak.tierLines(fall, 6, false).stream().map(RuneBreakTest::plain).toList();
        check(fallLines.stream().anyMatch(s -> s.trim().equals("◆ 검이 한 번 더 떨어짐 (지금 겨눈 곳 · 위력 60%)")), "extra line in the 6 tier " + fallLines);
        check(RuneBreak.tierLines(fall, 3, false).stream().anyMatch(s -> s.startsWith("§r§8     ◆")), "extra line dark below 6");

        // 글자
        check(RuneBreak.stars(2).equals("★★☆☆☆☆") && RuneBreak.stars(6).equals("★★★★★★"), "stars " + RuneBreak.stars(2));
        check(RuneBreak.tag(0).isEmpty() && RuneBreak.tag(3).equals("[3돌]"), "tag " + RuneBreak.tag(3));
        check(RuneBreak.changeTexts(meteor, 3).equals(List.of("운석 범위 6 → 7.5칸", "불붙음 5 → 8초")), "meteor 3 text " + RuneBreak.changeTexts(meteor, 3));
        check(RuneBreak.changeTexts(meteor, 6).equals(List.of("운석 위력 ×32 → ×40", "운석 범위 7.5 → 9칸", "불붙음 8 → 12초", "더 높이 띄움")),
                "meteor 6 text " + RuneBreak.changeTexts(meteor, 6));
        check(RuneBreak.changeTexts(rune("axe", "transcend"), 3).contains("받는 피해 -25 → -30%"), "berserker % " + RuneBreak.changeTexts(rune("axe", "transcend"), 3));
        check(RuneBreak.changeTexts(rune("dex", "transcend"), 3).contains("멈춤 1.5 → 2초"), "shadowless sec " + RuneBreak.changeTexts(rune("dex", "transcend"), 3));
        check(RuneBreak.changeTexts(rune("spirit", "transcend"), 3).contains("정령마다 2 → 3번"), "spirits per spirit " + RuneBreak.changeTexts(rune("spirit", "transcend"), 3));
        check(RuneBreak.changeTexts(rune("str", "transcend"), 6).contains("사거리 22 → 28칸"), "heavenFall 6 range from base");
        check(plain(RuneBreak.summaryLine(3, false)).equals("돌파 ★★★☆☆☆ 3/6 · 계수 ×1.3"), "summary " + plain(RuneBreak.summaryLine(3, false)));
        check(plain(RuneBreak.summaryLine(0, false)).contains("0/6") && plain(RuneBreak.summaryLine(0, false)).contains("+10%"), "summary 0");

        List<String> lore = RuneBreak.lore(meteor, 3, false);
        List<String> plainLore = lore.stream().map(RuneBreakTest::plain).toList();
        check(plainLore.get(1).startsWith("돌파 ★★★☆☆☆ 3/6"), "summary right after the head: " + plainLore.get(1));
        check(plainLore.stream().anyMatch(s -> s.startsWith("★3돌 운석 범위 6 → 7.5칸")), "3 line " + plainLore);
        check(lore.stream().anyMatch(s -> s.startsWith("§r§8☆6돌")), "6 not reached: dark");
        check(plainLore.stream().anyMatch(s -> s.contains("+ 화면 색 연출")), "fx 3 line");
        check(plainLore.get(plainLore.size() - 1).startsWith("누르면 사용") && plainLore.get(plainLore.size() - 2).startsWith("터치:"), "control lines last");
        check(plainLore.stream().anyMatch(s -> s.contains("룬술사 '돌파'")), "where to merge");
        List<String> six = RuneBreak.lore(meteor, 6, false).stream().map(RuneBreakTest::plain).toList();
        check(six.stream().noneMatch(s -> s.contains("룬술사 '돌파'")), "6: no merge hint");
        check(six.stream().anyMatch(s -> s.startsWith("★6돌")), "6 reached");
        List<String> c0 = RuneBreak.lore(common, 0, false).stream().map(RuneBreakTest::plain).toList();
        check(c0.get(1).startsWith("돌파 ☆☆☆☆☆☆ 0/6") && c0.stream().noneMatch(s -> s.contains("3돌")), "common 0 lore " + c0);
        List<String> undying = RuneBreak.lore(rune("vit", "transcend"), 3, false).stream().map(RuneBreakTest::plain).toList();
        check(undying.stream().anyMatch(s -> s.contains("위력×25")), "undying 3 damage total in lore " + undying);
        List<String> spring4 = RuneBreak.lore(spring, 4, false).stream().map(RuneBreakTest::plain).toList();
        check(spring4.stream().anyMatch(s -> s.contains("회복 최대 체력 20%")), "lifeSpring 4 heal 14% × 1.4 ≈ 20% " + spring4);

        System.out.println("RuneBreakTest: " + ok + " ok, " + BAD.size() + " failed");
        for (String b : BAD) System.out.println("  FAIL " + b);
        if (!BAD.isEmpty()) System.exit(1);
    }
}
