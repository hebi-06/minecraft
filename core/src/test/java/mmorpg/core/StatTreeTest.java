package mmorpg.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 스탯 트리 계산 시험 (JUnit 없이 main — check-core.sh). 2026-11-18 축복의 길: stat_tree.json 의 모양 (나무 4 · 칸 11 · 갈래 묶음 둘 ·
 * 공용 스킬 16 · 보상 문턱), 열린 칸 수, 보너스 합 (길 16 = 7단계 갈래 조합 · 상한), 묶음 단위 고르기, 연 공용 스킬, 받을 보상, 초기화 비용, 글자,
 * 기록 JSON (2판 · 1판 옮기기), 스탯 룬 뽑기권 (Runes.rollRuneOf). 하나라도 틀리면 종료 코드 1.
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

    /** 갈래 묶음 둘을 고른 기록 (첫 갈래 o1 · 둘째 갈래 o2 — 묶음의 첫 칸 1 · 6 에 고르면 묶음 전체) */
    private static Map<Integer, Integer> path(String stat, int o1, int o2) {
        return StatTree.State.EMPTY.withPick(stat, 1, o1).withPick(stat, 6, o2).picksOf(stat);
    }

    /** 길 하나의 합이 기대값과 같은지 (나머지 보너스는 0) */
    private static void pathSum(String stat, int o1, int o2, Map<String, Double> want) {
        Map<String, Double> got = StatTree.treeTotals(stat, 100, path(stat, o1, o2));
        for (String k : StatTree.kinds().keySet()) near(get(got, k), want.getOrDefault(k, 0.0), stat + " path " + o1 + o2 + " " + k);
    }

    public static void main(String[] argv) {
        // 데이터 모양: 칸 11 (뿌리 5 → 첫 갈래 10 · 20 · 30 → 이동기 40 → 합류 50 → 둘째 갈래 60 · 70 · 80 → 작은 공격 90 → 정점 100)
        int[] ats = {5, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
        int[] splits = {0, 1, 1, 1, 1, 0, 2, 2, 2, 2, 0};
        int[] firsts = {0, 1, 1, 1, 1, 5, 6, 6, 6, 6, 10};
        Map<Integer, Map<String, Integer>> rewards = Map.of(2, Map.of("stat", 1), 4, Map.of("rune", 1), 5, Map.of("stat", 1), 7, Map.of("rune", 1),
                8, Map.of("stat", 1), 10, Map.of("adv", 1, "stat", 1));
        Map<String, String> gods = Map.of("str", "포르티아", "dex", "라에타", "int", "니바리아", "vit", "그라비스");
        Map<String, String> elems = Map.of("str", "fire", "dex", "thunder", "int", "ice", "vit", "rock");
        check(StatTree.stats().equals(Stats.STAT_KEYS), "tree order = STAT_KEYS " + StatTree.stats());
        for (String stat : StatTree.stats()) {
            StatTree.Tree t = StatTree.tree(stat);
            check(t.nodes().size() == 11, stat + " has 11 nodes");
            check(t.title().endsWith("의 길") && t.god().equals(gods.get(stat)) && t.element().equals(elems.get(stat)), stat + " title · god · element "
                    + t.title() + " " + t.god() + " " + t.element());
            check(t.branches().get(1).size() == 2 && t.branches().get(2).size() == 2, stat + " branches " + t.branches());
            for (StatTree.Node n : t.nodes()) {
                int i = n.index();
                String w = stat + " node " + i;
                check(n.at() == ats[i] && n.split() == splits[i] && n.first() == firsts[i], w + " at " + n.at() + " split " + n.split() + " first " + n.first());
                check(n.fork() == (splits[i] > 0), w + " fork " + n.fork());
                check(n.reward().equals(rewards.getOrDefault(i, Map.of())), w + " reward " + n.reward());
                for (String k : n.reward().keySet()) check(StatTree.rewardItem(stat, k) != null, w + " reward key " + k);
                if (!n.fork()) {
                    check(!n.bonus().isEmpty() && !n.name().isBlank(), w + " bonus " + n.name());
                    continue;
                }
                check(n.pick().size() == 2 && n.bonus().isEmpty(), w + " has 2 options, no fixed bonus");
                boolean skillNode = i == 4 || i == 9;
                for (int o = 0; o < n.pick().size(); o++) {
                    StatTree.Option opt = n.pick().get(o);
                    check(!opt.name().isBlank(), w + " option name");
                    if (!skillNode) {
                        check(!opt.bonus().isEmpty() && opt.skill() == null, w + " option " + opt.name() + " bonus");
                        check(opt.name().startsWith(t.branchName(n, o)), w + " option " + opt.name() + " in branch " + t.branchName(n, o));
                        continue;
                    }
                    StatTree.Skill sk = StatTree.skill(opt.skill());
                    check(sk != null && opt.bonus().isEmpty() && opt.name().equals(sk.name()), w + " skill option " + opt.skill());
                    if (sk == null) continue;
                    check(sk.stat().equals(stat), w + " skill stat " + sk.stat());
                    check(sk.kind().equals(i == 4 ? "move" : "attack"), w + " skill kind " + sk.kind());
                    if (sk.attack()) check(sk.element().equals(t.element()) && sk.mult() > 2 && sk.mult() < 3 && sk.cd() >= 8 && sk.cd() <= 10,
                            w + " attack " + sk.id() + " " + sk.element() + " ×" + sk.mult() + " " + sk.cd() + "s");
                    else check(sk.element().isEmpty() && sk.mult() == 0 && sk.cd() >= 6 && sk.cd() <= 12, w + " move " + sk.id() + " " + sk.cd() + "s");
                    check(!sk.text().isBlank(), w + " skill text");
                }
            }
            StatTree.Ticket tk = StatTree.tickets().get(stat);
            // 2026-11-17 룬 재개발 (계열 없음): 스탯 뽑기권은 남은 아이템만 (쓰면 일반 뽑기) · 보상은 룬 뽑기권
            check(tk != null && Runes.HIDDEN.contains(tk.family()) && tk.family().equals(stat), stat + " ticket family (hidden) " + (tk == null ? null : tk.family()));
            check(StatTree.rewardItem(stat, "stat").equals(Rules.get().obj("RUNE").str("ticketItem")), stat + " stat reward → rune ticket");
            check(tk != null && tk.item().equals("rpg:" + stat + "_rune_ticket"), stat + " ticket item");
        }
        for (StatTree.Kind k : StatTree.kinds().values()) check(!k.icon().isBlank() && !k.name().isBlank(), "kind " + k.key());
        check(StatTree.skillDefs().size() == 16, "16 common skills " + StatTree.skillDefs().size());
        check(StatTree.skill("blaze_leap").name().equals("블레이즈 리프") && StatTree.skill("nope") == null && StatTree.skill(null) == null, "skill lookup");
        // 피해 합 = 0.25 × 재사용 × 거리 × 범위 × 원소 0.95 × (1 − 유틸) (컨셉 문서 — 블레이즈 크로스: 근접 1.2 · 좁은 1.08)
        near(StatTree.skill("blaze_cross").mult(), 2.46, "blaze cross ×2.46");
        near(StatTree.skill("ember_nova").mult(), 2.85, "ember nova ×2.85");

        // 열린 칸 수 · 다음 칸
        check(StatTree.tier("str", 0) == 0 && StatTree.tier("str", 4) == 0, "tier 0 below 5");
        check(StatTree.tier("str", 5) == 1 && StatTree.tier("str", 10) == 2, "tier 1 at 5 · 2 at 10");
        check(StatTree.tier("str", 34) == 4, "tier 4 at 34");
        check(StatTree.tier("str", 100) == 11 && StatTree.tier("str", 155) == 11, "tier 11 at 100+");
        check(StatTree.nextAt("str", 34) == 40 && StatTree.nextAt("str", 3) == 5 && StatTree.nextAt("str", 100) == -1, "next at");
        check(StatTree.tier("luck", 50) == 0, "unknown stat → 0");

        // 묶음 단위 고르기: 어느 칸에서 골라도 묶음 넷이 같은 쪽 · 갈래 수 = 묶음 수
        StatTree.State one = StatTree.State.EMPTY.withPick("str", 1, 0);
        check(one.picksOf("str").equals(Map.of(1, 0, 2, 0, 3, 0, 4, 0)) && one.pickCount("str") == 1, "pick split 1 at its first node " + one.picksOf("str"));
        check(StatTree.State.EMPTY.withPick("str", 3, 1).picksOf("str").equals(Map.of(1, 1, 2, 1, 3, 1, 4, 1)), "pick at a later node of the split");
        StatTree.State two = one.withPick("str", 8, 1);
        check(two.picksOf("str").equals(Map.of(1, 0, 2, 0, 3, 0, 4, 0, 6, 1, 7, 1, 8, 1, 9, 1)) && two.pickCount("str") == 2, "two splits " + two.picksOf("str"));
        check(StatTree.resetCost(two.pickCount("str")) == 2 * StatTree.perPick(), "reset cost counts splits");

        // 보너스 합: 갈래는 고른 것만 (뿌리만 · 길 하나)
        near(get(StatTree.treeTotals("str", 34, Map.of()), "atk"), 2, "str 34 atk without pick (root)");
        near(get(StatTree.treeTotals("str", 34, Map.of()), "critDmg"), 0, "str 34 critDmg without pick (inside the branch)");
        Map<String, Double> s34 = StatTree.treeTotals("str", 34, one.picksOf("str"));
        near(get(s34, "atk"), 6, "str 34 atk with 광전사 (2 + 2 + 2)");
        near(get(s34, "critDmg"), 10, "str 34 critDmg with 광전사 II");
        near(get(StatTree.treeTotals("str", 34, StatTree.State.EMPTY.withPick("str", 1, 1).picksOf("str")), "def"), 4, "str 34 def with 강철 피부");
        near(get(StatTree.treeTotals("str", 9, one.picksOf("str")), "atk"), 2, "pick below its tier does nothing");
        check(StatTree.pending("str", 34, Map.of()).equals(List.of(1)), "pending split 1 (first node only)");
        check(StatTree.pending("str", 64, one.picksOf("str")).equals(List.of(6)), "pending split 2 after picking 1");
        check(StatTree.pending("str", 9, Map.of()).isEmpty(), "no pending below 10");

        // 길 16 = 7단계 갈래 조합 (옮기는 법: 10 → 뿌리 · 20 + 30 갈래 → 첫 갈래 · 40 + 50 → 합류 · 60 갈래 + 70 + 90 갈래 → 둘째 갈래 · 80 + 100 → 정점)
        pathSum("str", 0, 0, Map.of("atk", 17.0, "crit", 5.0, "critDmg", 50.0, "hp", 10.0));
        pathSum("str", 0, 1, Map.of("atk", 22.0, "critDmg", 25.0, "hp", 35.0));
        pathSum("str", 1, 0, Map.of("atk", 13.0, "crit", 5.0, "critDmg", 50.0, "def", 4.0, "hp", 10.0));
        pathSum("str", 1, 1, Map.of("atk", 18.0, "critDmg", 25.0, "def", 4.0, "hp", 35.0));
        pathSum("dex", 0, 0, Map.of("atk", 7.0, "cdr", 4.0, "crit", 11.0, "critDmg", 25.0, "speed", 3.0));
        pathSum("dex", 0, 1, Map.of("atk", 2.0, "cdr", 4.0, "crit", 11.0, "def", 5.0, "speed", 9.0));
        pathSum("dex", 1, 0, Map.of("atk", 7.0, "cdr", 4.0, "crit", 8.0, "critDmg", 25.0, "speed", 8.0));
        pathSum("dex", 1, 1, Map.of("atk", 2.0, "cdr", 4.0, "crit", 8.0, "def", 5.0, "speed", 14.0));
        pathSum("int", 0, 0, Map.of("atk", 4.0, "cdr", 15.0, "def", 3.0, "hp", 10.0, "rune", 13.0));
        pathSum("int", 0, 1, Map.of("atk", 4.0, "cdr", 3.0, "critDmg", 25.0, "def", 3.0, "hp", 10.0, "rune", 19.0));
        pathSum("int", 1, 0, Map.of("atk", 7.0, "cdr", 15.0, "def", 3.0, "hp", 10.0, "rune", 8.0));
        pathSum("int", 1, 1, Map.of("atk", 7.0, "cdr", 3.0, "critDmg", 25.0, "def", 3.0, "hp", 10.0, "rune", 14.0));
        pathSum("vit", 0, 0, Map.of("def", 19.0, "hp", 46.0));
        pathSum("vit", 0, 1, Map.of("def", 8.0, "hp", 91.0));
        pathSum("vit", 1, 0, Map.of("atk", 3.0, "def", 19.0, "hp", 36.0));
        pathSum("vit", 1, 1, Map.of("atk", 3.0, "def", 8.0, "hp", 81.0));

        // 연 공용 스킬: 고른 길의 40 · 90 칸
        check(StatTree.openSkills("str", 100, path("str", 0, 1)).equals(List.of("blaze_leap", "ember_nova")), "str skills 0 1");
        check(StatTree.openSkills("str", 89, path("str", 1, 0)).equals(List.of("steel_charge")), "str 89 → mobility only");
        check(StatTree.openSkills("str", 39, path("str", 0, 0)).isEmpty() && StatTree.openSkills("str", 100, Map.of()).isEmpty(), "no skill below 40 or unpicked");
        StatTree.State sk = StatTree.State.EMPTY.withPick("str", 1, 0).withPick("str", 6, 0).withPick("int", 1, 1);
        check(StatTree.openSkills(Map.of("str", 125.0, "dex", 0.0, "int", 82.0, "vit", 0.0), sk).equals(List.of("blaze_leap", "blaze_cross", "crystal_shift")),
                "skills across trees " + StatTree.openSkills(Map.of("str", 125.0, "dex", 0.0, "int", 82.0, "vit", 0.0), sk));

        // 나무 넷 합 + 상한
        StatTree.State st = StatTree.State.EMPTY.withPick("str", 1, 0).withPick("dex", 1, 0).withPick("vit", 1, 0).withPick("vit", 6, 0);
        Map<String, Double> spent = Map.of("str", 34.0, "dex", 50.0, "int", 10.0, "vit", 100.0);
        Map<String, Double> all = StatTree.totals(spent, st);
        near(get(all, "atk"), 6 + 2, "all atk (str 6 + dex merge 2)");
        near(get(all, "crit"), 1 + 1 + 2 + 2, "all crit (dex root · 급소 I · III · merge)");
        near(get(all, "rune"), 2, "all rune (int 10 — root only, split unpicked)");
        near(get(all, "def"), 19, "all def (vit 거대한 몸 · 수호자)");
        near(get(all, "hp"), 46, "all hp (vit 거대한 몸 · 수호자)");
        StatTree.State big = StatTree.State.EMPTY.withPick("dex", 1, 0).withPick("dex", 6, 1).withPick("str", 1, 1).withPick("vit", 1, 0).withPick("vit", 6, 0)
                .withPick("int", 1, 1).withPick("int", 6, 0);
        Map<String, Double> max = StatTree.totals(Map.of("str", 100.0, "dex", 100.0, "int", 100.0, "vit", 100.0), big);
        check(get(max, "def") <= StatTree.caps().get("def") + 1e-9, "def capped at " + StatTree.caps().get("def") + " (" + get(max, "def") + ")");
        check(get(max, "cdr") <= StatTree.caps().get("cdr") + 1e-9, "cdr capped");

        // 보상: 문턱 20 · 50 · 80 · 100 = (2026-11-17 룬 재개발 — 계열이 없어 스탯 룬 뽑기권 대신) 룬 뽑기권, 40 · 70 = 룬 뽑기권, 100 = 고급 (7단계와 같은 문턱 · 수)
        Map<String, Integer> r = StatTree.rewards("str", 0, 11);
        check(r.getOrDefault("rpg:str_rune_ticket", 0) == 0, "no str tickets " + r);
        check(r.getOrDefault("rpg:rune_ticket", 0) == 6, "rune tickets ×6 (4 + 2) " + r);
        check(r.getOrDefault("rpg:high_rune_ticket", 0) == 1, "adv ticket ×1 " + r);
        check(StatTree.rewards("str", 0, 2).isEmpty(), "root · first branch node have no reward");
        check(StatTree.rewards("str", 2, 3).equals(Map.of("rpg:rune_ticket", 1)), "node at 20 → rune ticket");
        check(StatTree.rewards("str", 3, 3).isEmpty() && StatTree.rewards("str", 5, 3).isEmpty(), "no reward when not higher");
        check(StatTree.rewards("vit", 0, 11).getOrDefault("rpg:rune_ticket", 0) == 6, "vit → rune tickets");
        check(StatTree.itemName("rpg:str_rune_ticket").equals("힘 룬 뽑기권") && StatTree.itemName("rpg:rune_ticket").equals("룬 뽑기권"), "item names");

        // 초기화 비용 · 글자
        check(StatTree.resetCost(0) == 0 && StatTree.resetCost(2) == 2 * StatTree.perPick() && StatTree.perPick() == 12, "reset cost");
        check(StatTree.bonusText("atk", 2).equals("공격력 +2%"), StatTree.bonusText("atk", 2));
        check(StatTree.bonusText("def", 4).equals("받는 피해 -4%"), StatTree.bonusText("def", 4));
        check(StatTree.bonusText("hp", 10).equals("최대 체력 +10"), StatTree.bonusText("hp", 10));
        check(StatTree.bonusText("crit", 1).equals("치명타 확률 +1%p"), StatTree.bonusText("crit", 1));
        check(StatTree.bonusText("cdr", 4.5).equals("재사용 대기 -4.5%"), StatTree.bonusText("cdr", 4.5));
        check(StatTree.bonusText(Map.of("critDmg", 10.0)).equals("치명타 피해 +10%"), "critDmg text");
        StatTree.Tree str = StatTree.tree("str");
        check(StatTree.nodeName(str.nodes().get(1)).equals("광전사 I / 강철 피부 I"), "fork name " + StatTree.nodeName(str.nodes().get(1)));
        check(str.branchName(str.nodes().get(3), 1).equals("강철 피부") && str.branchName(str.nodes().get(7), 0).equals("파괴자"), "branch names");
        check(str.splitOf(3).equals(List.of(1, 2, 3, 4)) && str.splitOf(5).equals(List.of(5)), "split of " + str.splitOf(3));
        check(StatTree.rewardText("str", Map.of("stat", 1)).equals("룬 뽑기권 ×1"), "reward text " + StatTree.rewardText("str", Map.of("stat", 1)));

        // 기록 JSON (2판): 고른 갈래 (묶음 전체) · 받은 칸 수
        StatTree.State s = StatTree.State.EMPTY.withPick("str", 1, 1).withPick("str", 6, 0).withClaimed("str", 7).withClaimed("vit", 2);
        StatTree.State back = StatTree.State.parse(s.json());
        check(s.json().startsWith("{\"v\":2"), "state json v2 " + s.json());
        check(back.picksOf("str").equals(s.picksOf("str")) && back.claimedOf("str") == 7 && back.claimedOf("vit") == 2, "state json " + s.json());
        check(back.withoutPicks("str").pickCount("str") == 0 && back.withoutPicks("str").claimedOf("str") == 7, "reset keeps claimed");
        check(StatTree.State.parse("{broken").equals(StatTree.State.EMPTY) && StatTree.State.parse(null).claimedOf("str") == 0, "broken json → empty");
        // 2판에 첫 칸만 적힌 기록 → 묶음 전체
        check(StatTree.State.parse("{\"v\":2,\"picks\":{\"str\":{\"1\":1}},\"claimed\":{}}").picksOf("str").equals(Map.of(1, 1, 2, 1, 3, 1, 4, 1)), "v2 first-only picks expand");
        // 1판 (v 없음 · 칸 10 · 갈래 3 · 6 · 9): 고른 갈래는 지움 (다시 고름), 받은 칸 수 c → 문턱 10c 까지의 새 칸 수 (보상을 다시 주지 않음)
        StatTree.State old = StatTree.State.parse("{\"picks\":{\"str\":{\"2\":0,\"5\":1}},\"claimed\":{\"str\":3,\"vit\":10,\"dex\":0}}");
        check(old.picksOf("str").isEmpty() && old.pickCount("str") == 0, "v1 picks dropped " + old.picksOf("str"));
        check(old.claimedOf("str") == 4 && old.claimedOf("vit") == 11 && old.claimedOf("dex") == 0, "v1 claimed → new tiers " + old.claimed());
        check(StatTree.rewards("str", old.claimedOf("str"), StatTree.tier("str", 34)).isEmpty(), "v1 claimed 3 (at 30) gives nothing again at 34");

        // 스탯 룬 뽑기권: 계열 고정, 등급은 룬 뽑기권과 같은 무게
        double[] r0 = {0.0};
        Runes.Roll a = Runes.rollRuneOf(() -> r0[0], false, "str");
        check(Runes.isSet(a.family()) && a.grade().equals("common") && Runes.hasGrade(a.family(), "common"), "roll 0 → common (old stat ticket = normal) " + a);
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
