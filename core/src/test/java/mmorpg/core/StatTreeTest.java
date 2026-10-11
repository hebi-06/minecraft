package mmorpg.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 스탯 트리 계산 시험 (JUnit 없이 main — check-core.sh): stat_tree.json 의 모양 (나무 4 · 칸 10 · 갈래 3 · 6 · 9 · 보상), 열린 칸 수,
 * 보너스 합 (갈래는 고른 것만 · 상한), 받을 보상, 초기화 비용, 글자, 기록 JSON, 스탯 룬 뽑기권 (Runes.rollRuneOf). 하나라도 틀리면 종료 코드 1.
 */
public final class StatTreeTest {
    private StatTreeTest() {}

    private static final List<String> BAD = new ArrayList<>();
    private static int ok;

    private static void check(boolean cond, String what) {
        if (cond) ok++;
        else BAD.add(what);
    }

    private static void near(double a, double b, String what) {
        check(Math.abs(a - b) < 1e-9, what + " (" + a + " vs " + b + ")");
    }

    private static double get(Map<String, Double> m, String k) {
        Double v = m.get(k);
        return v == null ? 0 : v;
    }

    public static void main(String[] argv) {
        // 데이터 모양
        check(StatTree.stats().equals(Stats.STAT_KEYS), "tree order = STAT_KEYS " + StatTree.stats());
        for (String stat : StatTree.stats()) {
            StatTree.Tree t = StatTree.tree(stat);
            check(t.nodes().size() == 10, stat + " has 10 nodes");
            for (StatTree.Node n : t.nodes()) {
                check(n.at() == (n.index() + 1) * 10, stat + " node " + n.index() + " at " + n.at());
                boolean fork = n.index() == 2 || n.index() == 5 || n.index() == 8;
                check(n.fork() == fork, stat + " node " + n.index() + " fork " + n.fork());
                if (n.fork()) {
                    check(n.pick().size() == 2, stat + " fork " + n.index() + " has 2 options");
                    check(n.reward().isEmpty() && n.bonus().isEmpty(), stat + " fork " + n.index() + " has no reward / fixed bonus");
                    for (StatTree.Option o : n.pick()) check(!o.bonus().isEmpty() && !o.name().isBlank(), stat + " option " + o.name());
                } else {
                    check(!n.bonus().isEmpty() && !n.name().isBlank(), stat + " node " + n.index() + " bonus " + n.name());
                }
                for (String k : n.reward().keySet()) check(StatTree.rewardItem(stat, k) != null, stat + " reward key " + k);
            }
            StatTree.Ticket tk = StatTree.tickets().get(stat);
            check(tk != null && Runes.FAMILY_ORDER.contains(tk.family()) && tk.family().equals(stat), stat + " ticket family " + (tk == null ? null : tk.family()));
            check(tk != null && tk.item().equals("rpg:" + stat + "_rune_ticket"), stat + " ticket item");
        }
        for (StatTree.Kind k : StatTree.kinds().values()) check(!k.icon().isBlank() && !k.name().isBlank(), "kind " + k.key());

        // 열린 칸 수 · 다음 칸
        check(StatTree.tier("str", 0) == 0 && StatTree.tier("str", 9) == 0, "tier 0 below 10");
        check(StatTree.tier("str", 10) == 1, "tier 1 at 10");
        check(StatTree.tier("str", 34) == 3, "tier 3 at 34");
        check(StatTree.tier("str", 100) == 10 && StatTree.tier("str", 155) == 10, "tier 10 at 100+");
        check(StatTree.nextAt("str", 34) == 40 && StatTree.nextAt("str", 100) == -1, "next at");
        check(StatTree.tier("luck", 50) == 0, "unknown stat → 0");

        // 보너스 합: 갈래는 고른 것만
        Map<String, Double> s34 = StatTree.treeTotals("str", 34, Map.of());
        near(get(s34, "atk"), 2, "str 34 atk without pick");
        near(get(s34, "critDmg"), 10, "str 34 critDmg");
        near(get(StatTree.treeTotals("str", 34, Map.of(2, 0)), "atk"), 6, "str 34 atk with 광전사");
        near(get(StatTree.treeTotals("str", 34, Map.of(2, 1)), "def"), 4, "str 34 def with 강철 피부");
        near(get(StatTree.treeTotals("str", 29, Map.of(2, 0)), "atk"), 2, "pick below its tier does nothing");
        Map<String, Double> s100 = StatTree.treeTotals("str", 100, Map.of(2, 0, 5, 0, 8, 0));
        near(get(s100, "atk"), 22, "str 100 offense atk");
        near(get(s100, "critDmg"), 50, "str 100 offense critDmg");
        near(get(s100, "hp"), 10, "str 100 offense hp");
        check(StatTree.pending("str", 34, Map.of()).equals(List.of(2)), "pending fork 2");
        check(StatTree.pending("str", 64, Map.of(2, 0)).equals(List.of(5)), "pending fork 5 after picking 2");
        check(StatTree.pending("str", 20, Map.of()).isEmpty(), "no pending below 30");

        // 나무 넷 합 + 상한
        StatTree.State st = StatTree.State.EMPTY.withPick("str", 2, 0).withPick("dex", 2, 0).withPick("vit", 5, 0).withPick("vit", 8, 0);
        Map<String, Double> spent = Map.of("str", 34.0, "dex", 50.0, "int", 10.0, "vit", 100.0);
        Map<String, Double> all = StatTree.totals(spent, st);
        near(get(all, "atk"), 6 + 2, "all atk (str 6 + dex 2)");
        near(get(all, "crit"), 1 + 3 + 2, "all crit (dex)");
        near(get(all, "rune"), 2, "all rune (int 10)");
        near(get(all, "def"), 2 + 3 + 5 + 3 + 6, "all def (vit with 수호자 · 성벽)");
        near(get(all, "hp"), 5 + 8 + 8 + 15, "all hp (vit trunk, no 3rd-tier pick)");
        StatTree.State big = StatTree.State.EMPTY.withPick("dex", 2, 0).withPick("dex", 5, 1).withPick("str", 2, 1).withPick("vit", 5, 0).withPick("vit", 8, 0)
                .withPick("int", 8, 1).withPick("int", 5, 0);
        Map<String, Double> max = StatTree.totals(Map.of("str", 100.0, "dex", 100.0, "int", 100.0, "vit", 100.0), big);
        check(get(max, "def") <= StatTree.caps().get("def") + 1e-9, "def capped at " + StatTree.caps().get("def") + " (" + get(max, "def") + ")");
        check(get(max, "cdr") <= StatTree.caps().get("cdr") + 1e-9, "cdr capped");

        // 보상: 칸 2 · 5 · 8 · 10 = 그 스탯 룬 뽑기권, 4 · 7 = 룬 뽑기권, 10 = 고급
        Map<String, Integer> r = StatTree.rewards("str", 0, 10);
        check(r.getOrDefault("rpg:str_rune_ticket", 0) == 4, "str tickets ×4 " + r);
        check(r.getOrDefault("rpg:rune_ticket", 0) == 2, "rune tickets ×2 " + r);
        check(r.getOrDefault("rpg:high_rune_ticket", 0) == 1, "adv ticket ×1 " + r);
        check(StatTree.rewards("str", 0, 1).isEmpty(), "tier 1 has no reward");
        check(StatTree.rewards("str", 1, 2).equals(Map.of("rpg:str_rune_ticket", 1)), "tier 2 → str ticket");
        check(StatTree.rewards("str", 3, 3).isEmpty() && StatTree.rewards("str", 5, 3).isEmpty(), "no reward when not higher");
        check(StatTree.rewards("vit", 0, 10).getOrDefault("rpg:vit_rune_ticket", 0) == 4, "vit tickets");
        check(StatTree.itemName("rpg:str_rune_ticket").equals("힘 룬 뽑기권") && StatTree.itemName("rpg:rune_ticket").equals("룬 뽑기권"), "item names");

        // 초기화 비용 · 글자
        check(StatTree.resetCost(0) == 0 && StatTree.resetCost(2) == 2 * StatTree.perPick() && StatTree.perPick() == 12, "reset cost");
        check(StatTree.bonusText("atk", 2).equals("공격력 +2%"), StatTree.bonusText("atk", 2));
        check(StatTree.bonusText("def", 4).equals("받는 피해 -4%"), StatTree.bonusText("def", 4));
        check(StatTree.bonusText("hp", 10).equals("최대 체력 +10"), StatTree.bonusText("hp", 10));
        check(StatTree.bonusText("crit", 1).equals("치명타 확률 +1%p"), StatTree.bonusText("crit", 1));
        check(StatTree.bonusText("cdr", 4.5).equals("재사용 대기 -4.5%"), StatTree.bonusText("cdr", 4.5));
        check(StatTree.bonusText(Map.of("critDmg", 10.0)).equals("치명타 피해 +10%"), "critDmg text");
        check(StatTree.nodeName(StatTree.tree("str").nodes().get(2)).equals("광전사 / 강철 피부"), "fork name");
        check(StatTree.rewardText("str", Map.of("stat", 1)).equals("힘 룬 뽑기권 ×1"), "reward text");

        // 기록 JSON
        StatTree.State s = StatTree.State.EMPTY.withPick("str", 2, 1).withPick("str", 5, 0).withClaimed("str", 6).withClaimed("vit", 2);
        StatTree.State back = StatTree.State.parse(s.json());
        check(back.picksOf("str").equals(Map.of(2, 1, 5, 0)) && back.claimedOf("str") == 6 && back.claimedOf("vit") == 2, "state json " + s.json());
        check(back.withoutPicks("str").pickCount("str") == 0 && back.withoutPicks("str").claimedOf("str") == 6, "reset keeps claimed");
        check(StatTree.State.parse("{broken").equals(StatTree.State.EMPTY) && StatTree.State.parse(null).claimedOf("str") == 0, "broken json → empty");

        // 스탯 룬 뽑기권: 계열 고정, 등급은 룬 뽑기권과 같은 무게
        double[] r0 = {0.0};
        Runes.Roll a = Runes.rollRuneOf(() -> r0[0], false, "str");
        check(a.family().equals("str") && a.grade().equals("common"), "roll 0 → common str");
        r0[0] = 0.999999;
        check(Runes.rollRuneOf(() -> r0[0], false, "dex").grade().equals("transcend"), "roll ~1 → transcend");
        r0[0] = 0.0;
        check(Runes.rollRuneOf(() -> r0[0], true, "int").grade().equals("rare"), "adv roll 0 → rare (no common)");
        // 등급 경계: 50 / 28 / 14 / 6.5 / 1.5 (합 100)
        r0[0] = 0.50;
        check(Runes.rollRuneOf(() -> r0[0], false, "vit").grade().equals("rare"), "roll 0.50 → rare");
        r0[0] = 0.78;
        check(Runes.rollRuneOf(() -> r0[0], false, "vit").grade().equals("noble"), "roll 0.78 → noble");
        r0[0] = 0.92;
        check(Runes.rollRuneOf(() -> r0[0], false, "vit").grade().equals("hero"), "roll 0.92 → hero");
        r0[0] = 0.985;
        check(Runes.rollRuneOf(() -> r0[0], false, "vit").grade().equals("transcend"), "roll 0.985 → transcend");
        boolean threw = false;
        try {
            Runes.rollRuneOf(() -> 0.1, false, "nope");
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check(threw, "unknown family throws");

        System.out.println("StatTreeTest: " + ok + " ok, " + BAD.size() + " failed");
        for (String b : BAD) System.out.println("  FAIL " + b);
        if (!BAD.isEmpty()) System.exit(1);
    }
}
