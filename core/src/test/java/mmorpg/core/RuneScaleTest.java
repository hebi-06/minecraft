package mmorpg.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 0.49.0 룬 재설계 시험 (JUnit 없이 main — check-core.sh): 유틸 값 · 시간 보정 (RUNE_SPREAD — rules_more.json, Runes.damageScale).
 * <ul>
 * <li>규칙 값: 유틸 23종 (기절 1초당 10% — 0.5초 5% · 2.5초 25%), 선딜 6틱 미만 0 · 1틱 +1% · 최대 +30%, 시간 보정 상한 ×1.3, 유틸 합 최대 60%</li>
 * <li>표: 피해가 있는 룬 (평타 강화 빼고) 은 모두 t 가 있음 · 유틸 이름이 utils 에 있음 · 지원 · 버프 룬 (체력 · 성기사 지원 · 신체 강화 · 평타 강화 (피의 칼날 빼고)
 * · 가호 · 낙인) 은 유틸 값 없음</li>
 * <li>예산: 규칙을 따르는 피해 룬은 배율 합 = 등급 예산 (4 · 7 · 12 · 20 · 32 · 한정 34 — 1% 안) → 실효 = 예산 × (1 − 유틸 값) × 시간 보정</li>
 * <li>룬 몇 개: 대지 강타 (기절 0.5초 −5%) · 거인의 도약 (도약 · 기절 1초 −20%, 선딜 0.6초 +12%) · 메테오 (띄움 −5%, 선딜 1.5초 +30% = 상한) ·
 * 광전사의 혼 (흡혈 · 피해 감소 −20%, 장판 4초 넘게 +15% — 예외 없이) · 반격 자세 (피해 감소 −10%, 선딜 없음) · 만년빙하 (빙결 2초 −20%, 상한) ·
 * 피의 칼날 (평타 흡혈 −10%, 시간 보정 없음) · 불굴의 방벽 (지원 — 장판 4초 넘게 +15% 만)</li>
 * </ul>
 * 하나라도 틀리면 종료 코드 1.
 */
public final class RuneScaleTest {
    private RuneScaleTest() {}

    private static final List<String> BAD = new ArrayList<>();
    private static int ok;

    private static void check(boolean cond, String what) {
        if (cond) ok++;
        else BAD.add(what);
    }

    private static void near(double a, double b, String what) {
        check(Math.abs(a - b) < 1e-9, what + " (" + a + " vs " + b + ")");
    }

    private static Runes.Rune rune(String fam, String grade) {
        return Runes.parseRune(Runes.runeId(fam, grade));
    }

    /** 배율 합 그대로 두는 룬 (지원 · 버프 — 유틸 값 없음) */
    private static final Set<String> KEEP = Set.of("vit.common", "vit.rare", "vit.noble", "vit.hero", "vit.transcend", "holy.common", "holy.rare", "holy.noble",
            "holy.transcend", "body.common", "body.rare", "body.noble", "body.hero", "body.transcend", "strike.common", "strike.rare", "strike.noble",
            "strike.transcend", "yggdrasil.transcend", "abyss.transcend");
    private static final double[] BUDGET = {4, 7, 12, 20, 32};

    public static void main(String[] argv) {
        check(Rules.hasMore(), "overlay present");
        Obj sp = Rules.get().obj("RUNE_SPREAD");
        near(sp.num("cap"), 1.3, "cap ×1.3");
        near(sp.num("utilMax"), 0.6, "util max 60%");
        near(sp.obj("windup").num("perTick"), 0.01, "windup +1%/tick (0.5초 +10%)");
        near(sp.obj("windup").num("max"), 0.3, "windup max +30%");
        near(sp.obj("windup").num("min"), 6, "windup min 6 ticks");
        Obj u = sp.obj("utils");
        near(u.obj("stun05").num("cost"), 0.05, "stun 0.5s 5%");
        near(u.obj("stun1").num("cost"), 0.10, "stun 1s 10%");
        near(u.obj("stun15").num("cost"), 0.15, "stun 1.5s 15%");
        near(u.obj("stun2").num("cost"), 0.20, "stun 2s 20%");
        near(u.obj("stun25").num("cost"), 0.25, "stun 2.5s 25%");
        near(u.obj("vuln10").num("cost"), 0.15, "vuln 15%");
        near(u.obj("buff10").num("cost"), 0.15, "buff 15%");
        near(u.obj("leap").num("cost"), 0.10, "leap 10%");
        near(u.obj("lifesteal").num("cost"), 0.10, "lifesteal 10%");

        int runes = 0;
        int ruled = 0;
        int leaps = 0;
        for (String fam : Runes.ALL_FAMILIES) {
            for (int gi = 0; gi < Runes.GRADE_ORDER.size(); gi++) {
                String g = Runes.GRADE_ORDER.get(gi);
                Runes.Rune r = rune(fam, g);
                if (r == null) continue;
                runes++;
                String id = fam + "." + g;
                Obj d = r.def();
                Obj e = Runes.spreadEntry(r);
                boolean strike = fam.equals(Rules.get().obj("RUNE_STRIKE").str("family"));
                double total = Runes.runeTotalMult(d);
                if (!strike && total > 0) check(e != null && e.has("t"), id + " deals damage → RUNE_SPREAD t");
                for (String k : Runes.utils(r)) check(u.has(k), id + " util " + k + " known");
                check(Runes.utilCost(r) <= 0.6 + 1e-9, id + " util ≤ 60%");
                double tm = Runes.timeMult(r);
                check(tm >= 1 - 1e-9 && tm <= 1.3 + 1e-9, id + " time mult in [1, 1.3] " + tm);
                near(Runes.damageScale(r), (1 - Runes.utilCost(r)) * tm, id + " scale = util × time");
                if (KEEP.contains(id)) {
                    check(Runes.utils(r).isEmpty(), id + " keep: no util cost");
                    continue;
                }
                if (strike) continue; // 피의 칼날: 평타 흡혈 값만 (아래)
                ruled++;
                if (Runes.utils(r).contains("leap")) leaps++;
                double budget = Runes.limited(fam) ? 34 : BUDGET[gi];
                check(Math.abs(total - budget) <= budget * 0.01, id + " total " + total + " = budget " + budget);
            }
        }
        check(runes == 123, "123 runes " + runes);
        check(ruled == 102, "102 runes under the rule " + ruled);
        check(leaps == 11, "11 leap runes " + leaps);

        // 하나씩
        Runes.Rune slam = rune("str", "common");
        near(Runes.utilCost(slam), 0.05, "ground slam stun 0.5s");
        near(Runes.damageScale(slam), 0.95, "ground slam scale");
        Runes.Rune titan = rune("str", "hero");
        check(Runes.utils(titan).equals(List.of("leap", "stun1")), "titan leap utils " + Runes.utils(titan));
        check(Runes.windupTicks(titan) == 12, "titan leap windup 12 ticks");
        near(Runes.damageScale(titan), 0.8 * 1.12, "titan leap scale");
        Runes.Rune meteor = rune("int", "transcend");
        check(Runes.windupTicks(meteor) == 30 && Runes.spreadKind(meteor) == null && !Runes.timeCapped(meteor), "meteor windup 1.5s, one hit");
        near(Runes.timeMult(meteor), 1.3, "meteor +30%");
        near(Runes.damageScale(meteor), 0.95 * 1.3, "meteor scale");
        Runes.Rune berserk = rune("axe", "transcend");
        check(Runes.utils(berserk).equals(List.of("lifesteal", "reduce")) && "zoneLong".equals(Runes.spreadKind(berserk)) && Runes.windupTicks(berserk) == 0,
                "berserker: no exception " + Runes.utils(berserk) + " " + Runes.spreadKind(berserk));
        near(Runes.damageScale(berserk), 0.8 * 1.15, "berserker scale");
        Runes.Rune counter = rune("blade", "noble");
        check(Runes.windupTicks(counter) == 0 && "long".equals(Runes.spreadKind(counter)), "counter stance: no windup (reactive), long");
        near(Runes.damageScale(counter), 0.9 * 1.1, "counter stance scale");
        Runes.Rune glacier = rune("glacier", "transcend");
        check(Runes.timeCapped(glacier) && "zoneLong".equals(Runes.spreadKind(glacier)), "glacier capped");
        near(Runes.damageScale(glacier), 0.8 * 1.3, "glacier scale");
        Runes.Rune blood = rune("strike", "hero");
        check(Runes.utils(blood).equals(List.of("lifesteal")), "blood blade lifesteal");
        near(Runes.damageScale(blood), 0.9, "blood blade: util only, no time");
        Runes.Rune wall = rune("vit", "hero");
        check(Runes.utils(wall).isEmpty() && "zoneLong".equals(Runes.spreadKind(wall)), "bulwark: support (no util), zoneLong");
        near(Runes.damageScale(wall), 1.15, "bulwark scale");
        Runes.Rune net = rune("hunter", "noble");
        check(Runes.windupTicks(net) == 6, "hunt net windup 6 ticks (min)");
        Runes.Rune star = rune("assassin", "rare");
        check(Runes.windupTicks(star) == 0, "poison stars first hit 2 ticks → no windup");

        System.out.println("RuneScaleTest: " + ok + " ok, " + BAD.size() + " bad");
        for (int i = 0; i < Math.min(60, BAD.size()); i++) System.out.println("  BAD " + BAD.get(i));
        if (!BAD.isEmpty()) System.exit(1);
    }
}
