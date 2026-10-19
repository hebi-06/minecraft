package mmorpg.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 0.16.1 시험 (JUnit 없이 main — check-core.sh): 자바 판 룬 툴팁 (RuneTip — 사용자 요청 "위력×32 대신 총 피해량처럼 구체적으로 · 쓸모없는 줄 · 연출 줄 빼기 ·
 * 돌파 · 주스탯 / 다른 스탯 계수는 남기기").
 * <ul>
 * <li>모든 룬 93 (0.24.0 — 만년빙하까지) × 돌파 0 ~ 6: "위력" 이 남지 않음 (모두 숫자), 뺀 줄 (용도 · 공식 · 연출 · 터치 · 돌파 안내 · 총 피해 글자 · 한정 뽑기) 없음,
 * 계수 줄 (주스탯 계수 · 다른 스탯 0.85 / 적용 = …) · 돌파 줄 있음, 줄 길이 · 줄 수 상한, 마지막 줄 = 조작</li>
 * <li>총 피해 = 위력 P (스탯 트리 배수 포함) × 돌파 계수 × 배율 합 (Runes.runeTotalMult), 초월은 3 · 6돌마다 그 단계의 총 피해</li>
 * <li>회복 = 최대 체력 × 비율 × (1 + 적용 스탯 × 1%), 스탯을 모르면 "위력×N" · "최대 체력 N%+"</li>
 * <li>대조 시험 함수 Runes.runeLore 는 그대로 (애드온 문구 — 위력×)</li>
 * </ul>
 * 하나라도 틀리면 종료 코드 1.
 */
public final class RuneTipTest {
    private RuneTipTest() {}

    private static final List<String> BAD = new ArrayList<>();
    private static int ok;

    private static void check(boolean cond, String what) {
        if (cond) ok++;
        else BAD.add(what);
    }

    private static Map<String, Double> stats(double str, double dex, double vit, double intl) {
        Map<String, Double> b = new LinkedHashMap<>();
        b.put("str", str);
        b.put("dex", dex);
        b.put("vit", vit);
        b.put("int", intl);
        b.put("point", 0.0);
        b.put("lvmax", 40.0);
        return Stats.effectiveStats(b, Map.of(), 0);
    }

    private static List<String> plain(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String l : lines) out.add(RuneTip.plain(l));
        return out;
    }

    private static String joined(List<String> lines) {
        return String.join("\n", plain(lines));
    }

    private static Runes.Rune rune(String family, String grade) {
        return Runes.parseRune(Runes.runeId(family, grade));
    }

    public static void main(String[] argv) {
        // 숫자 글자
        check(RuneTip.fmt(1344).equals("1,344") && RuneTip.fmt(77.76).equals("77.8") && RuneTip.fmt(12).equals("12") && RuneTip.fmt(0).equals("0")
                && RuneTip.fmt(99.96).equals("100") && RuneTip.fmt(100.4).equals("100") && RuneTip.fmt(Double.NaN).equals("0"), "fmt");

        Map<String, Double> even = stats(31, 31, 31, 32);
        double treeMult = 1.1;
        RuneTip.Viewer v = new RuneTip.Viewer(even, treeMult, 131);
        String[] gone = {"위력", "용도", "레벨 공격력", "연출", "터치:", "누르면 사용", "룬술사 '돌파'", "총 피해를 띄움", "한정 뽑기", "근원의 프리즘", "별똥별 비",
            "심판의 눈", "세계수 개화", "심연의 문", "빙하의 왕관", "오로라"};
        int runes = 0;
        int maxLen = 0;
        int maxLines = 0;
        for (String fam : Runes.ALL_FAMILIES) {
            for (String g : Runes.GRADE_ORDER) {
                Runes.Rune r = rune(fam, g);
                if (r == null) continue;
                runes++;
                for (int lv = 0; lv <= RuneBreak.max(); lv++) {
                    List<String> ls = RuneTip.lines(r, lv, v);
                    List<String> p = plain(ls);
                    String all = String.join("\n", p);
                    String id = r.id() + "@" + lv;
                    for (String w : gone) check(!all.contains(w), id + " has no '" + w + "'");
                    for (String l : p) {
                        maxLen = Math.max(maxLen, l.length());
                        check(l.length() <= 36, id + " line short: " + l);
                    }
                    maxLines = Math.max(maxLines, ls.size());
                    check(ls.size() <= 20, id + " lines " + ls.size());
                    check(ls.get(0).contains("재사용") && p.get(0).contains(Js.str(r.cd()) + "초"), id + " head " + p.get(0));
                    check(p.get(p.size() - 1).equals(RuneTip.CONTROL), id + " control last");
                    check(p.contains(RuneTip.BASIS) || p.contains(RuneTip.BASIS_SELF), id + " basis line");
                    // 계수 줄 (그대로)
                    Obj f = Rules.get().obj("RUNE_FAMILIES").obj(fam);
                    String coef = f.truthy("mix") ? "적용 = 마력 + 힘·민첩" : f.truthy("best") ? "적용 = 가장 높은 스탯 × 1.0" : f.truthy("sum") ? "적용 = 네 스탯의 합 × 1.0"
                            : Runes.coefText(fam) + " · 다른 스탯 0.85";
                    check(all.contains(coef), id + " coef line '" + coef + "'");
                    // 돌파 줄
                    check(all.contains("돌파 " + RuneBreak.stars(lv)), id + " break summary");
                    // 총 피해 = P × 계수 × 배율 합
                    Obj d = RuneBreak.def(r, lv);
                    boolean strike = fam.equals(Rules.get().obj("RUNE_STRIKE").str("family"));
                    boolean body = fam.equals(Rules.get().obj("RUNE_BODY").str("family"));
                    double P = Runes.runePower(even, fam) * treeMult;
                    double total = Runes.runeTotalMult(d);
                    if (!strike && !body && total > 0) {
                        String want = "총 피해 " + RuneTip.fmt(P * RuneBreak.coef(lv) * total);
                        check(p.stream().anyMatch(l -> l.startsWith("⚔ " + want)), id + " total '" + want + "' in " + all);
                    }
                    if (strike && d.truthy("bonus")) check(all.contains("평타마다 +" + RuneTip.fmt(P * RuneBreak.coef(lv) * d.num("bonus"))), id + " strike bonus");
                    if (body && total > 0) check(all.contains("쓸 때 충격파 " + RuneTip.fmt(P * RuneBreak.coef(lv) * total)), id + " body shockwave");
                    // 초월: 3 · 6돌 줄 (그 단계의 총 피해)
                    if (RuneBreak.tiered(r)) {
                        check(all.contains("초월 돌파 (3돌 · 6돌)"), id + " tier head");
                        for (int t : RuneBreak.tiers()) {
                            double tt = Runes.runeTotalMult(RuneBreak.def(r, t));
                            String star = (lv >= t ? RuneBreak.STAR : RuneBreak.STAR_EMPTY) + t + "돌 ";
                            check(p.stream().anyMatch(l -> l.startsWith(star)), id + " tier " + t + " line");
                            if (!strike && !body && tt > 0) {
                                String want = star + "총 피해 " + RuneTip.fmt(P * RuneBreak.coef(t) * tt);
                                check(p.stream().anyMatch(l -> l.startsWith(want)), id + " tier total '" + want + "'");
                            }
                        }
                    } else {
                        check(!all.contains("초월 돌파"), id + " no tier lines");
                    }
                }
            }
        }
        check(runes == 123, "123 runes (0.25.0 두 스탯 계열 30 더함) " + runes);

        // 0.24.0 만년빙하: 얼음 3 × 8 + 빙하 붕괴 10 (P 42 → 126 × 8번 + 420 — 나눔이 길어 다음 줄), 필드 · 빙결 줄, 3 · 6돌 (필드 · 얼음 수 · 빙결)
        List<String> gl = plain(RuneTip.lines(rune("glacier", "transcend"), 0, new RuneTip.Viewer(stats(31, 31, 31, 32), 1, 131)));
        String glj = String.join("\n", gl);
        check(gl.contains("⚔ 총 피해 1,428") && gl.contains("  얼음 126 × 8번 + 빙하 붕괴 420") && gl.contains("조준한 곳 반지름 6칸 얼음 필드 · 0.7초마다")
                && gl.contains("필드 안 적마다 발밑에서 거대한 얼음이 솟음") && gl.contains("빙결: 같은 적 3번 → 2초 (보스는 얼지 않음)") && gl.get(0).equals("재사용 36초 · 한정")
                && glj.contains("띄움"), "glacier: " + gl);
        check(glj.contains("☆3돌 총 피해 " + RuneTip.fmt(42 * 1.3 * 42)) && glj.contains("얼음 필드 6 → 7칸") && glj.contains("거대한 얼음 8 → 10번")
                && glj.contains("빙결 2 → 2.5초") && glj.contains("☆6돌 총 피해 " + RuneTip.fmt(42 * 1.6 * 55)) && glj.contains("빙결 2.5 → 3초"), "glacier tiers: " + glj);
        String gl6 = joined(RuneTip.lines(rune("glacier", "transcend"), 6, new RuneTip.Viewer(stats(31, 31, 31, 32), 1, 131)));
        check(gl6.contains("⚔ 총 피해 " + RuneTip.fmt(42 * 1.6 * 55)) && gl6.contains("얼음 235 × 12번 + 빙하 붕괴 874") && gl6.contains("반지름 8칸 얼음 필드") && gl6.contains("같은 적 3번 → 3초"),
                "glacier 6: " + gl6);

        // 0.20.0 심연의 낙인 (적 약화 — 피해 없음): 적이 받는 피해 + · 주는 피해 − (스탯 합 125: 0돌 20% · 3돌 26% · 6돌 32%)
        Runes.Rune aby = rune("abyss", "transcend");
        String a0 = joined(RuneTip.lines(aby, 0, v));
        check(a0.contains("✦ 적이 받는 피해 +20%") && a0.contains("◈ 적이 주는 피해 -20%") && !a0.contains("총 피해") && a0.contains(RuneTip.BASIS_SELF), "brand 0: " + a0);
        check(a0.contains("조준한 곳 반지름 6칸 안 적 · 10초 (보스 포함)") && a0.contains("세기 = 10% + 스탯 합×0.08% (최대 22%)")
                && a0.contains("모든 플레이어의 피해에 · 가호와 곱"), "brand flow: " + a0);
        check(a0.contains("☆3돌 낙인 26% · 지속 10 → 12초") && a0.contains("반지름 6 → 7칸") && a0.contains("☆6돌 낙인 32% · 지속 12 → 14초")
                && a0.contains("사거리 20 → 24칸"), "brand tiers: " + a0);
        String a6 = joined(RuneTip.lines(aby, 6, v));
        check(a6.contains("✦ 적이 받는 피해 +32%") && a6.contains("◈ 적이 주는 피해 -32%") && a6.contains("반지름 8칸") && a6.contains("14초"), "brand 6: " + a6);
        String abn = joined(RuneTip.lines(aby, 0, null));
        check(abn.contains("적이 받는 피해 +10% + 스탯 비례") && abn.contains("☆3돌 낙인 ×1.3"), "brand without stats: " + abn);

        // 0.19.0 세계수의 가호 (버프 — 피해 없음): 가하는 피해 + · 받는 피해 − (스탯 합 125: 0돌 20% · 3돌 26% · 6돌 32%)
        Runes.Rune ygg = rune("yggdrasil", "transcend");
        String y0 = joined(RuneTip.lines(ygg, 0, v));
        check(y0.contains("✦ 가하는 피해 +20%") && y0.contains("◈ 받는 피해 -20%") && !y0.contains("총 피해") && y0.contains(RuneTip.BASIS_SELF), "grace 0: " + y0);
        check(y0.contains("반지름 8칸 안 아군 (나 포함) · 12초") && y0.contains("세기 = 10% + 스탯 합×0.08% (최대 22%)") && y0.contains("겹치지 않음"), "grace flow: " + y0);
        check(y0.contains("☆3돌 가호 26% · 지속 12 → 14초") && y0.contains("반지름 8 → 10칸") && y0.contains("☆6돌 가호 32% · 지속 14 → 16초"), "grace tiers: " + y0);
        String y6 = joined(RuneTip.lines(ygg, 6, v));
        check(y6.contains("✦ 가하는 피해 +32%") && y6.contains("◈ 받는 피해 -32%") && y6.contains("반지름 12칸") && y6.contains("16초")
                && y6.contains("세기 = 16% + 스탯 합×0.128% (최대 35.2%)"), "grace 6: " + y6);
        String yn = joined(RuneTip.lines(ygg, 0, null));
        check(yn.contains("가하는 피해 +10% + 스탯 비례") && yn.contains("☆3돌 가호 ×1.3"), "grace without stats: " + yn);

        // 하나씩: 메테오 (마력 125 — P 42)
        Map<String, Double> mage = stats(0, 0, 0, 125);
        RuneTip.Viewer vm = new RuneTip.Viewer(mage, 1, 250);
        Runes.Rune meteor = rune("int", "transcend");
        String m0 = joined(RuneTip.lines(meteor, 0, vm));
        check(m0.contains("⚔ 총 피해 1,344") && m0.contains("불붙음 5초 · 띄움") && m0.contains("마력 계수 1.0 · 다른 스탯 0.85"), "meteor 0: " + m0);
        check(m0.contains("☆3돌 총 피해 1,747 · 운석 범위 6 → 7.5칸") && m0.contains("떨어진 자리 3초 불바다 (1초마다 109)"), "meteor tier 3 numbers: " + m0);
        check(m0.contains("☆6돌 총 피해 2,688") && !m0.contains("운석 피해") && !m0.contains("→ 2,688"), "meteor tier 6 total (no mult line): " + m0);
        String m6 = joined(RuneTip.lines(meteor, 6, vm));
        check(m6.contains("⚔ 총 피해 2,688") && m6.contains("돌파 ★★★★★★ 6/6 · 계수 ×1.6") && m6.contains("기절 1초"), "meteor 6: " + m6);
        // 천붕 6돌: 한 번 더 (60% — 천붕은 6돌에도 배율 32, 범위 · 사거리만 바뀜)
        String hf = joined(RuneTip.lines(rune("str", "transcend"), 6, new RuneTip.Viewer(stats(125, 0, 0, 0), 1, 250)));
        check(hf.contains("◆ 검이 한 번 더 떨어짐 (지금 겨눈 곳 · " + RuneTip.fmt(42 * 1.6 * 32 * 0.6) + ")"), "heavenFall echo: " + hf);
        // 만상귀일 (네 스탯 31 · 31 · 31 · 32 = 125 → P 42): 구슬 17 + 폭발 17
        String ar = joined(RuneTip.lines(rune("arcana", "transcend"), 0, new RuneTip.Viewer(even, 1, 131)));
        check(ar.contains("⚔ 총 피해 1,428 (구슬 714 + 귀일 폭발 714)") && ar.contains("재사용 36초 · 한정") && ar.contains("적용 = 네 스탯의 합 × 1.0"), "arcana: " + ar);
        // 0.17.0 무간참역: 베기 2 × 10 + 마무리 일섬 14 (P 42 → 84 × 10번 + 588 — 나눔이 길어 다음 줄), 영역 줄, 6돌 기절
        List<String> en = plain(RuneTip.lines(rune("endless", "transcend"), 0, new RuneTip.Viewer(even, 1, 131)));
        check(en.contains("⚔ 총 피해 1,428") && en.contains("  베기 84 × 10번 + 마무리 일섬 588") && en.contains("영역 반지름 8칸 · 0.5초마다 영역 안 모든 적")
                && en.get(0).equals("재사용 36초 · 한정"), "endless: " + en);
        String en6 = joined(RuneTip.lines(rune("endless", "transcend"), 6, new RuneTip.Viewer(even, 1, 131)));
        check(en6.contains("⚔ 총 피해 " + RuneTip.fmt(42 * 1.6 * 55)) && en6.contains("기절 1초") && en6.contains("영역 반지름 12칸") && !en6.contains("하늘 가르기"), "endless 6: " + en6);
        // 0.18.0 대지 가르기: 대검 16 (먼저) + 여진 3 × 6 (P 42 → 672 + 126 × 6번), 균열 줄 (앞으로 14칸 · 폭 5칸), 6돌 20칸 · 폭 7칸 · 기절
        List<String> te = plain(RuneTip.lines(rune("terra", "transcend"), 0, new RuneTip.Viewer(even, 1, 131)));
        String tej = String.join("\n", te);
        check(tej.contains("⚔ 총 피해 1,428") && tej.contains("대검 672 + 여진 126 × 6번") && te.contains("앞으로 14칸 균열 (폭 5칸) 안 모든 적")
                && te.contains("대검이 내려친 뒤 0.5초마다 여진") && te.get(0).equals("재사용 36초 · 한정") && tej.contains("띄움"), "terra: " + te);
        String te6 = joined(RuneTip.lines(rune("terra", "transcend"), 6, new RuneTip.Viewer(even, 1, 131)));
        check(te6.contains("⚔ 총 피해 " + RuneTip.fmt(42 * 1.6 * 55)) && te6.contains("기절 1초") && te6.contains("앞으로 20칸 균열 (폭 7칸)") && !te6.contains("지맥 폭발"),
                "terra 6: " + te6);
        // 적월 강림 6돌: 나눔이 길면 다음 줄 (이어지는 줄은 "+ ")
        List<String> cm = plain(RuneTip.lines(rune("reaper", "transcend"), 6, new RuneTip.Viewer(stats(0, 84, 0, 41), 1, 131)));
        int ti = -1;
        for (int i = 0; i < cm.size(); i++) if (cm.get(i).startsWith("⚔ 총 피해 ")) ti = i;
        check(ti > 0 && !cm.get(ti).contains("(") && cm.get(ti + 1).startsWith("  칼날 ") && cm.get(ti + 2).startsWith("  + 달 추락 "), "crimson moon split breakdown " + cm);
        check(cm.get(0).contains("낫 전용"), "reaper head: " + cm.get(0));
        // 생명의 샘: 회복 = 최대 체력 × 14% × (1 + 체력 31 × 1%)
        Runes.Rune spring = rune("vit", "rare");
        String sp = joined(RuneTip.lines(spring, 0, new RuneTip.Viewer(even, 1, 131)));
        String heal = RuneTip.fmt(131 * 0.14 * Runes.supportScale(even, "vit"));
        check(sp.contains("✚ 회복 " + heal + " (0.5초마다 4번 나눠)") && sp.contains(RuneTip.BASIS_SELF) && !sp.contains("총 피해"), "life spring: " + sp);
        // 2돌: 회복도 계수 (scaleKeys) — 14% × 1.2
        String sp2 = joined(RuneTip.lines(spring, 2, new RuneTip.Viewer(even, 1, 131)));
        check(sp2.contains("✚ 회복 " + RuneTip.fmt(131 * 0.14 * 1.2 * Runes.supportScale(even, "vit"))), "life spring 2: " + sp2);
        // 대천사 강림: 보호막 씩 · 상한 (최대 체력 × cap — 배율 없음)
        String an = joined(RuneTip.lines(rune("holy", "transcend"), 0, new RuneTip.Viewer(even, 1, 131)));
        check(an.contains("◈ 보호막 ") && an.contains("(최대 " + RuneTip.fmt(131 * 0.25) + ")") && an.contains("신성 피해 · 언데드 ×1.5"), "archangel: " + an);
        // 피의 칼날 (평타 강화): 평타마다 + · 합
        String bb = joined(RuneTip.lines(rune("strike", "hero"), 0, new RuneTip.Viewer(stats(125, 0, 0, 0), 1, 250)));
        check(bb.contains("⚔ 평타마다 +70.1 (12번 합 +842)") && bb.contains("적용 = 가장 높은 스탯 × 1.0"), "blood blade: " + bb);
        // 신체 강화 일반: 효과 한 줄 · 피해 없음 → 기준 줄은 내 스탯만
        String fs = joined(RuneTip.lines(rune("body", "common"), 0, new RuneTip.Viewer(even, 1, 131)));
        check(fs.contains("지속 5초 · 이동 속도 II · 받는 피해 -15%") && fs.contains(RuneTip.BASIS_SELF), "fighting spirit: " + fs);
        // 처형: 약한 적 배율 · 마검 해방: 마검 부여 숫자
        String ex = joined(RuneTip.lines(rune("axe", "hero"), 0, v));
        check(ex.contains("체력 35% 이하 적 ×1.5"), "execute: " + ex);
        String sb = joined(RuneTip.lines(rune("spellblade", "transcend"), 0, v));
        check(sb.contains("마검 부여: 평타 3번") && sb.matches("(?s).*평타 \\+[0-9,.]+ · 관통 4칸.*") && sb.contains("마력 2 : 힘·민첩 1 → 최대 +20%"), "spellblade: " + sb);

        // 스탯을 모를 때: 위력×N · 비율
        String n0 = joined(RuneTip.lines(meteor, 0, null));
        check(n0.contains("총 피해 위력×32") && !n0.contains(RuneTip.BASIS), "no viewer meteor: " + n0);
        String ns = joined(RuneTip.lines(spring, 0, null));
        check(ns.contains("회복 최대 체력 14%+"), "no viewer spring: " + ns);

        // 대조 시험 함수는 그대로 (애드온 문구)
        check(String.join("\n", Runes.runeLore(meteor, false)).contains("위력×32"), "runeLore unchanged");

        System.out.println("RuneTipTest: " + ok + " ok, " + BAD.size() + " bad (lines max " + maxLines + " · width max " + maxLen + ")");
        for (int i = 0; i < Math.min(40, BAD.size()); i++) System.out.println("  BAD " + BAD.get(i));
        if (!BAD.isEmpty()) System.exit(1);
    }
}
