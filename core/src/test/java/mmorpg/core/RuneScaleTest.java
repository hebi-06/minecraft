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

    /**
     * 2026-11-17 딜타임 (사용자 "쿨 바꿔서 직업 스킬하고 딜타임 만들어둘건데 그사이에 꽂아넣을수 있도록" · "검사면 검사랑 어울리는 룬 스킬이 있으면 해당하는 두 스킬에
     * 쿨이 같게") → 등급마다 재사용 범위 ("일반은 6~15초 이내에서 만들고 희귀는 12초~21초 사이 고귀는 15초~25초 사이 영웅은 27초~38초 사이 초월은 32초~50초 사이"):
     * 새 룬 30 은 제 등급 범위 안 (RUNE_SET.cdRange) · RUNE_SET.pairs 의 짝 직업 스킬 (숨기지 않은 직업) 과 쿨이 같음 · 직업 스킬 하나에 룬 하나
     */
    private static void pairs() {
        double[][] want = {{6, 15}, {12, 21}, {15, 25}, {27, 38}, {32, 50}};
        Obj range = Rules.get().obj("RUNE_SET").obj("cdRange");
        int sets = 0;
        for (int gi = 0; gi < Runes.GRADE_ORDER.size(); gi++) {
            String g = Runes.GRADE_ORDER.get(gi);
            List<Object> lh = range.list(g);
            check(lh.size() == 2 && Math.abs(((Number) lh.get(0)).doubleValue() - want[gi][0]) < 1e-9 && Math.abs(((Number) lh.get(1)).doubleValue() - want[gi][1]) < 1e-9,
                    g + " cd range " + lh);
            for (String fam : Runes.familiesOf(g)) {
                if (!Runes.isSet(fam)) continue;
                sets++;
                double cd = rune(fam, g).cd();
                check(cd >= want[gi][0] - 1e-9 && cd <= want[gi][1] + 1e-9, fam + " cd " + cd + " in the " + g + " range " + want[gi][0] + " ~ " + want[gi][1]);
            }
        }
        check(sets == 40, "40 set runes in grade ranges (2026-11-28 근접 딜 · 지원 다섯씩) " + sets);
        Obj p = Rules.get().obj("RUNE_SET").obj("pairs");
        Set<String> skills = new java.util.HashSet<>();
        int n = 0;
        for (String fam : p.keys()) {
            if (fam.startsWith("_")) continue;
            String g = null;
            for (String x : Runes.GRADE_ORDER) if (Runes.hasGrade(fam, x)) g = x;
            check(g != null && Runes.isSet(fam), fam + " pair: a set rune");
            if (g == null) continue;
            double cd = rune(fam, g).cd();
            for (Object o : p.list(fam)) {
                String id = String.valueOf(o);
                Jobs.Job j = Jobs.job(id.substring(0, Math.max(0, id.indexOf('_'))));
                Jobs.Skill k = j == null ? null : j.skill(id);
                check(k != null && !Jobs.hidden(j.id()) && Math.abs(k.cd() - cd) < 1e-9 && skills.add(id), fam + " " + cd + "s ↔ " + id + " " + (k == null ? null : k.cd()));
                n++;
            }
        }
        check(n == 36, "36 rune ↔ job skill pairs (2026-11-28 근접 딜 다섯 · 지원 넷 더함 — 에덴 블룸은 짝 없음) " + n);
        // 2026-11-17 등급 안 밸런스 (사용자 "각 등급끼리만 봤을때 밸런스"): 램파트는 막는 선딜 (선딜 보정 없음) · 루나 엑시큐션은 달빛 폭발이 둘레도 쳐 좁은 범위
        check(Rules.get().obj("RUNE_SET").obj("spread").obj("rampart.common").truthy("guard") && Runes.windupBonus(rune("rampart", "common")) == 0, "rampart guard");
        check("narrow".equals(Runes.areaKind(rune("lunaexec", "transcend"))), "lunaexec narrow (splash)");
        // 한정 룬끼리: 초당 예산 같음 (34 × scale ÷ 재사용 = 34 ÷ 36) — 얼음 여왕의 분노만 34 ÷ 32 (사용자 "신급 기술 … 살짝 더 좋게") · 유틸 값 × 36 ÷ 재사용
        for (String fam : List.of("endless", "terra", "glacier")) {
            Runes.Rune lr = rune(fam, "transcend");
            double perSec = 34 * Runes.budgetScale(lr) / lr.cd();
            double wantSec = fam.equals("glacier") ? 34 / 32.0 : 34 / 36.0;
            check(Math.abs(perSec - wantSec) < 1e-3, fam + " per-second budget " + perSec + " = " + wantSec);
            near(Runes.utilCdFactor(lr), 36 / lr.cd(), fam + " limited util cd factor");
        }
        check(!rune("endless", "transcend").def().truthy("knock") && !rune("endless", "transcend").def().truthy("lift"), "endless finish: no free knock · lift");
        // 2026-11-18 코드 감사 (사용자 "정체성은 값, 덤은 줄임" · "램파트와 같게"): 바스티온의 방벽 밀쳐냄은 값 · 루나 엑시큐션은 막는 선딜 (투명 · 대상 둔화) + 은신 · 둔화 값 ·
        // 얼음 여왕의 분노 붕괴 띄움은 타격 반응 크기 (0.35 — 밀쳐냄 0.4 · 띄움 0.35 이하는 유틸로 보지 않음)
        Runes.Rune bastion = rune("bastion", "hero");
        check(Runes.utils(bastion).equals(List.of("allyGuard", "knock")), "bastion utils " + Runes.utils(bastion));
        near(Runes.utilCost(bastion), 0.25 * 26 / 32.0, "bastion util (아군 받피 감소 + 밀쳐냄) × 26 / 32");
        Runes.Rune luna = rune("lunaexec", "transcend");
        check(Runes.utils(luna).equals(List.of("blink", "hide", "slow")) && Runes.spreadEntry(luna).truthy("guard"), "lunaexec guard · utils " + Runes.utils(luna));
        near(Runes.windupBonus(luna), 0, "lunaexec: no windup bonus (invisible · target slowed)");
        near(Runes.timeMult(luna), 1, "lunaexec time ×1");
        near(Runes.utilCost(luna), 0.25 * 36 / 50.0, "lunaexec util × 36 / 50");
        near(Runes.damageScale(luna) / Runes.budgetScale(luna), 1.1 * 1.08 * (1 - 0.18), "lunaexec scale: 중거리 × 좁은 범위 × (1 − 유틸 18%) — 선딜 보정 없음");
        near(rune("glacier", "transcend").def().num("lift"), 0.35, "glacier crush lift: hit reaction size");
        // 2026-11-28 쿨 32초 < 초월 기준 36초인 지키는 룬 둘 (사용자 "너가 말한대로 하자" — 지속 · 세기 × 쿨 ÷ 기준, tools/rune_set.py fit): 이모탈 오스 맹약 8 → 7초
        // (회복 · 반격 7번 — 한 번의 배율 합은 예산 그대로) · 버서커 받는 피해 −20 → −18% · 흡혈 2 → 1.8% (광폭화 6초 · 난타 6번 그대로)
        Runes.Rune oath = rune("immortaloath", "transcend");
        near(oath.def().num("ticks"), 140, "immortal oath 7s");
        near(Runes.spreadEntry(oath).num("t"), 120, "immortal oath last pulse at 120 ticks");
        double oathBudget = Runes.runeTotalMult(oath.def()) * Runes.budgetScale(oath);
        check(Math.abs(oathBudget - 32 / 36.0 * 32) < 1e-3, "immortal oath budget kept (7 pulses × 2.5 × scale — scale 소수 넷째 자리) " + oathBudget);
        near(Runes.utilValue(oath, "undying"), 0.175, "immortal oath undying 0.2 × 0.875");
        near(Runes.utilValue(oath, "heal"), 0.1313, "immortal oath heal 0.15 × 0.875");
        near(Runes.utilCost(oath), (0.175 + 0.1313) * 36 / 32.0, "immortal oath util 39.4 → 34.5%");
        Runes.Rune bz = rune("berserker", "transcend");
        near(bz.def().num("reduce"), 0.18, "new berserker −18%");
        near(bz.def().num("heal"), 0.018, "new berserker lifesteal 1.8%");
        // 2026-11-28 사용자 메모 (룬 도감 판 15 — "처음에 한번 크게 내려치고 그 이후에는 버프만"): 새 동작 rageSlam — 한 방 (배율 32 = 예산 전부 · 0.65초 선딜 +13%) +
        // 광폭화 6초 (받는 피해 −18% · 모든 공격 흡혈 — 준 피해의 5%, 1초에 최대 체력 1.8% × 회복 배율까지). 난타 (hits · gap · healMax) 는 없음
        check("rageSlam".equals(bz.def().str("fn")) && bz.def().numOrNull("hits") == null && bz.def().numOrNull("gap") == null
                && bz.def().numOrNull("healMax") == null, "new berserker: one slam (no barrage)");
        near(Runes.runeTotalMult(bz.def()), 32, "new berserker one hit × 32");
        near(bz.def().num("ticks"), 120, "new berserker rage 6s kept");
        near(bz.def().num("leech"), 0.05, "new berserker leech 5% of dealt");
        check(Runes.windupTicks(bz) == 13 && Runes.spreadKind(bz) == null && (int) Runes.spreadEntry(bz).num("t") == 13, "new berserker: windup 13 · one hit");
        near(Runes.timeMult(bz), 1.13, "new berserker windup +13%");
        double bzBudget = Runes.runeTotalMult(bz.def()) * Runes.budgetScale(bz);
        check(Math.abs(bzBudget - 32 / 36.0 * 32) < 1e-3, "new berserker budget (32 × scale) " + bzBudget);
        check("berserker".equals(rune("axe", "transcend").def().str("fn")) && rune("axe", "transcend").def().num("hits") == 6, "old berserker (숨김) barrage kept");
        near(Runes.utilValue(bz, "lifesteal"), 0.09, "new berserker lifesteal 0.1 × 0.9");
        near(Runes.utilCost(bz), (0.18 + 0.09) * 36 / 32.0, "new berserker util 33.8 → 30.4%");
        near(Runes.utilCost(rune("axe", "transcend")), 0.2, "old berserker (숨김 — 표의 값 · 재사용 맞춤 없음) util kept");
    }

    /** 배율 합 그대로 두는 룬 (지원 · 버프 — 유틸 값 없음) */
    private static final Set<String> KEEP = Set.of("vit.common", "vit.rare", "vit.noble", "vit.hero", "vit.transcend", "holy.common", "holy.rare", "holy.noble",
            "holy.transcend", "body.common", "body.rare", "body.noble", "body.hero", "body.transcend", "strike.common", "strike.rare", "strike.noble",
            "strike.transcend", "yggdrasil.transcend", "abyss.transcend");
    private static final double[] BUDGET = {4, 7, 12, 20, 32};
    /** 2026-11-28 지키는 유틸 (버팀 · 회복 · 흡혈 · 받는 피해 감소 · 보호막 · 아군 보호 — 쿨이 등급 기준보다 짧으면 지속 · 세기를 쿨 ÷ 기준 만큼) */
    private static final Set<String> GUARD = Set.of("undying", "heal", "lifesteal", "reduce", "shield", "allyGuard");
    /** 등급의 재사용 (초 — 새 룬의 예산 = BUDGET ÷ BASE_CD × 룬의 재사용) */
    private static final double[] BASE_CD = {8, 12, 18, 26, 36};

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
        List<String> capped = new ArrayList<>();
        int leaps = 0;
        int singles = 0;
        int narrows = 0;
        int sets = 0;
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
                // 0.50.0 범위 보정: a = single (+15%) · narrow (+8%) · 없음 (1)
                String a = Runes.areaKind(r);
                check(a == null || a.equals("single") || a.equals("narrow"), id + " area kind " + a);
                double am = a == null ? 1 : a.equals("single") ? 1.15 : 1.08;
                near(Runes.areaMult(r), am, id + " area mult");
                double mods = (1 - Runes.utilCost(r)) * tm * am * Elements.mult(Runes.element(r)) * Runes.rangeMult(r);
                near(Runes.scaleMods(r), mods, id + " mods = util × time × area × element × range");
                // 2026-11-28 등급 상한 (사용자 "다음 등급을 못 넘게 상한"): 새 룬 (초월 밖) 의 보정 곱 ≤ 다음 등급 초당 값 ÷ 이 등급 초당 값
                double cap = Runes.isSet(fam) && gi + 1 < BUDGET.length ? BUDGET[gi + 1] / BASE_CD[gi + 1] / (BUDGET[gi] / BASE_CD[gi]) : Double.POSITIVE_INFINITY;
                check(Double.isInfinite(cap) ? Double.isInfinite(Runes.gradeCap(r)) : Math.abs(Runes.gradeCap(r) - cap) < 1e-9, id + " grade cap " + Runes.gradeCap(r));
                near(Runes.damageScale(r), Math.min(mods, cap) * Runes.budgetScale(r), id + " scale = min(mods, grade cap) × budget");
                if (Runes.isSet(fam) && total > 0 && gi + 1 < BUDGET.length) {
                    double perSec = total * Runes.damageScale(r) / r.cd();
                    check(perSec <= BUDGET[gi + 1] / BASE_CD[gi + 1] + 1e-9, id + " 0돌 피해/초 " + perSec + " ≤ 다음 등급 " + BUDGET[gi + 1] / BASE_CD[gi + 1]);
                    if (mods > cap + 1e-12) {
                        capped.add(id);
                        near(Runes.gradeCapCut(r), 1 - cap / mods, id + " grade cap cut");
                    } else near(Runes.gradeCapCut(r), 0, id + " no grade cap cut");
                }
                // 2026-11-17 룬 재개발 (계열 없는 새 룬 30): 예산 = 등급 초당 값 × 재사용 · 거리 (근 1.2 · 중 1.1 · 원 1.0) · 받는 피해 증가 없음 · 시전 룬은 선딜 보정 없음
                if (Runes.isSet(fam)) {
                    sets++;
                    check(Runes.familiesOf(g).contains(fam) && !Runes.hasGrade(fam, g.equals("common") ? "rare" : "common"), id + " one grade");
                    double rm = Runes.rangeMult(r);
                    check(Math.abs(rm - 1.2) < 1e-9 || Math.abs(rm - 1.1) < 1e-9 || Math.abs(rm - 1.0) < 1e-9, id + " range mult " + rm);
                    check(!Runes.utils(r).contains("vuln5") && !Runes.utils(r).contains("vuln10") && !Runes.utils(r).contains("vuln15") && !d.truthy("vuln"), id + " no vuln (한정 룬만)");
                    if (Runes.castSeconds(r) > 0) near(Runes.windupBonus(r), 0, id + " cast → no windup bonus");
                    // 2026-11-17 등급 안 밸런스: 유틸 값 × 등급 기준 재사용 ÷ 룬의 재사용 (쿨이 길면 유틸이 드물게 켜짐) · 막는 선딜 (guard) 은 선딜 보정 없음
                    double raw = 0;
                    for (String k : Runes.utils(r)) raw += Runes.utilValue(r, k);
                    near(Runes.utilCdFactor(r), BASE_CD[gi] / r.cd(), id + " util cd factor");
                    near(Runes.utilCost(r), Math.min(0.6, raw * BASE_CD[gi] / r.cd()), id + " util cost × base cd ÷ cd");
                    // 2026-11-18 받는 피해 감소 = 세기 × 막는 시간 비례 (사용자 "비례로 다시 매김" — 기준 −30% · 2초 = 0.1)
                    Set<String> own = new java.util.HashSet<>();
                    if (Runes.utils(r).contains("reduce")) {
                        double sec = d.has("ticks") ? d.num("ticks") / 20 : d.num("hits") * d.num("gap") / 20;
                        near(Runes.utilValue(r, "reduce"), 0.1 * d.num("reduce") * sec / 0.6, id + " reduce value = cut × seconds");
                        own.add("reduce");
                    }
                    // 2026-11-28 지키는 유틸 · 쿨 (사용자 "너가 말한대로 하자"): 쿨이 등급 기준보다 짧은 룬은 지속 · 세기를 쿨 ÷ 기준 만큼 줄임 → 받는 피해 감소 밖의
                    // 지키는 유틸은 룬마다 값 (uCost = 표의 값 × 줄인 비율 — 표보다 작고, 재사용 맞춤 뒤 표의 값과 3.5% 안: 켜져 있는 몫 = 등급 기준)
                    if (r.cd() < BASE_CD[gi]) {
                        for (String k : Runes.utils(r)) {
                            if (!GUARD.contains(k) || k.equals("reduce")) continue;
                            double table = u.obj(k).num("cost");
                            double fitted = Runes.utilValue(r, k) * Runes.utilCdFactor(r);
                            check(Runes.utilValue(r, k) < table && Math.abs(fitted - table) <= table * 0.035,
                                    id + " " + k + " fitted to the cd: " + Runes.utilValue(r, k) + " × " + Runes.utilCdFactor(r) + " ≈ " + table);
                            own.add(k);
                        }
                    }
                    Obj uc = Runes.spreadEntry(r).objOrNull("uCost");
                    check(uc == null ? own.isEmpty() : uc.keys().equals(own), id + " own util values " + (uc == null ? "none" : uc.keys()) + " = " + own);
                    if (e.truthy("guard")) near(Runes.windupBonus(r), 0, id + " guard → no windup bonus");
                    if (total > 0) {
                        double want = BUDGET[gi] / BASE_CD[gi] * r.cd();
                        check(Math.abs(total * Runes.budgetScale(r) - want) <= want * 0.01, id + " budget " + total * Runes.budgetScale(r) + " = " + want);
                    }
                    continue;
                }
                if (a != null && a.equals("single")) singles++;
                if (a != null && a.equals("narrow")) narrows++;
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
        System.out.println("  등급 상한에 걸린 룬 (2026-11-28): " + capped);
        check(!capped.isEmpty(), "grade cap bites somewhere (노움 · 블레이드 헤일 …)");
        check(runes == 160, "160 runes (옛 120 — 숨김 · 2026-11-17 새 룬 30 · 2026-11-28 근접 딜 5 · 지원 5) " + runes);
        check(sets == 40, "40 set runes " + sets);
        pairs();
        for (String g : Runes.GRADE_ORDER) check(Runes.familiesOf(g).size() == 8, g + ": 8 runes " + Runes.familiesOf(g));
        check(ruled == 99, "99 runes under the rule " + ruled);
        check(leaps == 6, "6 leap runes " + leaps);
        check(singles == 10 && narrows == 35, "area kinds 10 single · 35 narrow: " + singles + " · " + narrows);
        Obj area = Rules.get().obj("RUNE_SPREAD").obj("area");
        near(area.obj("single").num("bonus"), 0.15, "single +15%");
        near(area.obj("narrow").num("bonus"), 0.08, "narrow +8%");
        check(Runes.areaName("single").equals("단일") && Runes.areaName("narrow").equals("좁은 범위") && Runes.areaName(null) == null, "area names"); // 0.49.0 도약 다섯은 0.48.2 로 되돌림 (피의 회오리 · 방패 돌진 · 용암 강타 · 발도 일섬 · 배후 일격)

        // 하나씩
        Runes.Rune slam = rune("str", "common");
        near(Runes.utilCost(slam), 0.05, "ground slam stun 0.5s");
        check("narrow".equals(Runes.areaKind(slam)), "ground slam narrow (radius 2.8)");
        near(Runes.damageScale(slam), 0.95 * 1.08, "ground slam scale");
        Runes.Rune titan = rune("str", "hero");
        check(Runes.utils(titan).equals(List.of("leap", "stun1")), "titan leap utils " + Runes.utils(titan));
        check(Runes.windupTicks(titan) == 12, "titan leap windup 12 ticks");
        near(Runes.damageScale(titan), 0.8 * 1.12, "titan leap scale");
        Runes.Rune meteor = rune("int", "transcend");
        check(Runes.windupTicks(meteor) == 30 && Runes.spreadKind(meteor) == null && !Runes.timeCapped(meteor), "meteor windup 1.5s, one hit");
        near(Runes.timeMult(meteor), 1.3, "meteor +30%");
        near(Runes.damageScale(meteor), 0.95 * 1.3 * 0.95, "meteor scale (불 원소 −5%)");
        Runes.Rune berserk = rune("axe", "transcend");
        check(Runes.utils(berserk).equals(List.of("lifesteal", "reduce")) && "zoneLong".equals(Runes.spreadKind(berserk)) && Runes.windupTicks(berserk) == 0,
                "berserker: no exception " + Runes.utils(berserk) + " " + Runes.spreadKind(berserk));
        near(Runes.damageScale(berserk), 0.8 * 1.15, "berserker scale");
        Runes.Rune counter = rune("blade", "noble");
        check(Runes.windupTicks(counter) == 0 && "long".equals(Runes.spreadKind(counter)), "counter stance: no windup (reactive), long");
        near(Runes.damageScale(counter), 0.9 * 1.1 * 1.08, "counter stance scale (narrow cone)");
        Runes.Rune glacier = rune("glacier", "transcend");
        check(Runes.timeCapped(glacier) && "zoneLong".equals(Runes.spreadKind(glacier)), "glacier capped");
        // 2026-11-17 한정 룬끼리 밸런스: 빙결 2초 값 × 36/32 (재사용 맞춤) · 예산 × 32/36 (scale — 초당 같게)
        // 예산은 34 그대로 (사용자 "얼음 여왕의 분노는 신급 기술 … 다른거보다 유틸이나 딜적으로 살짝 더 좋게" — 초당 예산이 다른 한정 룬보다 +12.5%)
        near(Runes.damageScale(glacier), (1 - 0.2 * 36 / 32.0) * 1.3 * 0.95, "glacier scale (얼음 원소 −5% · 유틸 재사용 맞춤)");
        Runes.Rune blood = rune("strike", "hero");
        check(Runes.utils(blood).equals(List.of("lifesteal")), "blood blade lifesteal");
        near(Runes.damageScale(blood), 0.9 * 1.15, "blood blade: util · single, no time");
        Runes.Rune snipe = rune("bow", "hero");
        check("single".equals(Runes.areaKind(snipe)) && Runes.areaKind(titan) == null && Runes.areaKind(meteor) == null, "snipe single · titan · meteor wide");
        near(Runes.damageScale(snipe), 0.95 * 1.17 * 1.15, "snipe scale");
        Runes.Rune hone = rune("strike", "common");
        near(Runes.damageScale(hone), 1.15, "edge hone: single only");
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
