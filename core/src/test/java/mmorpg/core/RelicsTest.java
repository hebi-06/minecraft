package mmorpg.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 0.43.0 아티팩트 시험 (JUnit 없이 main — check-core.sh): rules_more.json RELICS 의 모양 (종류 열 · 계열 4 + 6 · 등급 다섯 · 판 3 × 3 · 단계 3 · 5 · 7),
 * 스탯 줄 값 · 강화, 판 평가 (이웃 · 선 · 묶음 · 단계 · 같은 종류 · 착용 레벨 · 합), 패시브 글 · 값, 위력, 드랍 표, 글자, 장신구 강화 합. 하나라도 틀리면 종료 코드 1.
 */
public final class RelicsTest {
    private RelicsTest() {}

    private static final List<String> BAD = new ArrayList<>();
    private static int ok;

    private static void check(boolean cond, String what) {
        if (cond) ok++;
        else BAD.add(what);
    }

    private static void near(double a, double b, String what) {
        check(Math.abs(a - b) < 1e-9, what + " (" + a + " vs " + b + ")");
    }

    private static Relics.Cell c(String type, String grade, String stat, int enh) {
        return new Relics.Cell(type == null ? null : Relics.id(type, grade), stat, enh);
    }

    private static Relics.Cell c(String type) {
        return c(type, "transcend", "atk", 0);
    }

    private static List<Relics.Cell> board(String... types) {
        List<Relics.Cell> out = new ArrayList<>();
        for (String t : types) out.add(t == null ? Relics.Cell.EMPTY : c(t));
        return out;
    }

    private static Relics.Group group(Relics.Board b, String stat, int cell) {
        for (Relics.Group g : b.groups()) if (g.stat().equals(stat) && g.cells().contains(cell)) return g;
        return null;
    }

    public static void main(String[] argv) {
        Rules r = Rules.get();
        // ---------------------------------------------------------------- 모양
        check(Relics.enabled(), "enabled");
        check(Relics.size() == 3 && Relics.cells() == 9, "board 3x3 " + Relics.size());
        check(Relics.tierThresholds().equals(List.of(3, 5, 7)) && Relics.maxTier() == 3, "tiers " + Relics.tierThresholds());
        int[] want = {0, 0, 0, 1, 1, 2, 2, 3, 3, 3};
        for (int n = 0; n <= 9; n++) check(Relics.tierOf(n) == want[n], "tierOf " + n + " = " + Relics.tierOf(n));
        check(Relics.nextNeed(0) == 3 && Relics.nextNeed(3) == 5 && Relics.nextNeed(6) == 7 && Relics.nextNeed(7) == -1, "nextNeed");
        List<String> order = List.of("megingjord", "skidbladnir", "idunn", "mimir", "jarngreipr", "aegishjalmr", "draupnir", "gjallarhorn", "gleipnir", "brisingamen");
        check(Relics.types().equals(order), "order " + Relics.types());
        Map<String, Integer> perStat = new LinkedHashMap<>();
        Set<String> pairs = new HashSet<>();
        int singles = 0;
        for (String k : Relics.types()) {
            Relics.Type t = Relics.type(k);
            check(t != null && !t.name().isEmpty() && !t.title().isEmpty() && !t.desc().isEmpty(), "type text " + k);
            for (String s : t.stats()) perStat.merge(s, 1, Integer::sum);
            if (t.stats().size() == 1) singles++;
            else pairs.add(String.join("+", t.stats().stream().sorted().toList()));
        }
        check(singles == 4 && pairs.size() == 6, "4 single + 6 distinct pairs " + singles + " " + pairs);
        for (String s : Relics.STAT_ORDER) check(perStat.getOrDefault(s, 0) == 4 && Relics.typesWith(s).size() == 4, "4 types per stat " + s + " " + perStat);
        check(Relics.type("draupnir").stats().equals(List.of("str", "int")) && Relics.type("gleipnir").stats().equals(List.of("dex", "int")), "pair stats");
        // 아이템
        check(Relics.all().size() == 50, "50 relics " + Relics.all().size());
        int[] levels = {10, 20, 30, 40, 50};
        for (Grade g : r.grades()) {
            check(Relics.level(g.id()) == levels[g.index()], "level " + g.id());
            for (String k : order) {
                Relics.Relic a = Relics.relic(k, g.id());
                check(a != null && a.id().equals("rpg:relic_" + k + "_" + g.id()) && Relics.byId(a.id()) == a && a.gradeIndex() == g.index()
                        && a.level() == levels[g.index()], "relic " + k + " " + g.id());
                check(a != null && a.name().equals(Relics.type(k).name()) && a.coloredName(3).equals(g.color() + "+3 " + Relics.type(k).name()), "names " + k);
            }
        }
        check(Relics.glint("transcend") && !Relics.glint("hero"), "glint");
        check(Relics.byId("rpg:artifact_ring_rare") == null && Relics.byId(null) == null, "not relic");
        // ---------------------------------------------------------------- 스탯 줄
        check(Relics.lineKeys().equals(List.of("atk", "crit", "critDmg", "rune", "cdr", "hp", "def", "speed")), "line keys " + Relics.lineKeys());
        check(Relics.defaultLine().equals("atk") && Relics.isLine("rune") && !Relics.isLine("str") && !Relics.isLine(null), "default / isLine");
        for (String k : Relics.lineKeys()) {
            check(StatTree.kind(k) != null, "kind " + k);
            for (int g = 1; g < 5; g++) check(Relics.lineValue(k, g, 0) > Relics.lineValue(k, g - 1, 0), "rises " + k + " " + g);
            near(Relics.lineValue(k, 4, 10), Relics.lineValue(k, 4, 0) * 1.5, "+10 ×1.5 " + k);
        }
        near(Relics.lineValue("atk", 4, 0), 1.5, "atk transcend");
        near(Relics.lineValue("critDmg", 4, 0), 8, "critDmg transcend");
        near(Relics.lineValue("rune", 4, 0), 3.2, "rune transcend");
        near(Relics.lineValue("hp", 0, 0), 4, "hp common");
        near(Relics.lineValue("atk", 3, 5), 1.2 * 1.22, "atk hero +5");
        // 강화 (장신구 · 아티팩트)
        check(Enhance.gearEnabled(), "gear enhance on");
        near(Enhance.gearBonus(0), 0, "gear +0");
        near(Enhance.gearBonus(10), 0.5, "gear +10");
        near(Enhance.gearBonus(99), 0.5, "gear clamp");
        for (int n = 1; n <= 10; n++) check(Enhance.gearBonus(n) > Enhance.gearBonus(n - 1), "gear rises " + n);
        for (int n = 0; n < 10; n++) {
            Enhance.Cost a = Enhance.gearCost(n, "hero");
            Enhance.Cost b = Enhance.enhanceCost(n, "hero");
            check(a != null && a.rate() == b.rate() && a.stone() == b.stone() && a.bright() == b.bright() && a.down() == b.down(), "gear cost = weapon " + n);
        }
        check(Enhance.gearCost(10, "hero") == null, "no +11");
        // ---------------------------------------------------------------- 패시브
        for (String k : order) {
            Relics.Passive p = Relics.type(k).passive();
            String all = p.text(0);
            check(!p.name().isEmpty() && !all.contains("{") && !all.contains("}"), "passive text " + k + ": " + all);
            for (int t = 1; t <= 3; t++) {
                String tt = p.text(t);
                check(!tt.contains("{") && !tt.equals(all), "tier text " + k + " " + t + ": " + tt);
                for (String key : p.def().keys()) if (p.tiered(key)) check(tt.contains(Js.str(p.value(key, t))), "tier text has value " + k + "." + key + " " + t + ": " + tt);
            }
            boolean tiered = false;
            for (String key : p.def().keys()) {
                if (!p.tiered(key)) continue;
                tiered = true;
                check(p.value(key, 1) < p.value(key, 2) && p.value(key, 2) < p.value(key, 3), "tier values rise " + k + "." + key);
                check(p.value(key, 0) == 0, "tier 0 = 0 " + k + "." + key);
            }
            check(tiered, "has tiered values " + k);
        }
        Relics.Passive meg = Relics.type("megingjord").passive();
        near(meg.num("every", 0), 5, "megingjord every");
        near(meg.value("mult", 3), 1.5, "megingjord t3");
        check(meg.text(2).equals("평타 5번째마다 그 평타 피해 ×1.35"), "megingjord text t2: " + meg.text(2));
        check(meg.text(0).equals("평타 5번째마다 그 평타 피해 ×1.2 · 1.35 · 1.5"), "megingjord text all: " + meg.text(0));
        near(Relics.type("draupnir").passive().value("mult", 2), 0.25, "draupnir t2");
        near(Relics.type("draupnir").passive().num("rings", 0), 8, "draupnir rings");
        near(Relics.type("gjallarhorn").passive().num("cooldownSec", 0), 40, "horn cooldown");
        near(Relics.type("mimir").passive().value("mult", 2), 1.5, "mimir mult scalar at tier");
        check(Relics.passiveText(Relics.type("idunn"), 3).startsWith("「젊음의 사과」 체력이 35% 아래로"), "idunn text " + Relics.passiveText(Relics.type("idunn"), 3));
        // ---------------------------------------------------------------- 이웃
        check(Relics.neighbors(0).equals(List.of(1, 3)) && Relics.neighbors(4).equals(List.of(1, 3, 5, 7)) && Relics.neighbors(8).equals(List.of(5, 7))
                && Relics.neighbors(5).equals(List.of(2, 4, 8)) && Relics.neighbors(9).isEmpty() && Relics.neighbors(-1).isEmpty(), "neighbors");
        // ---------------------------------------------------------------- 판 평가
        Relics.Board empty = Relics.evaluate(List.of(), 60);
        check(empty.links().isEmpty() && empty.groups().isEmpty() && empty.totals().isEmpty() && empty.active() == 0 && empty.filled() == 0, "empty board");
        // 미리 보기 판 (사용자가 고른 그림): 힘 ━ 힘·마 ━ 마력 / 힘 · 마력 · 민첩 / 민첩 · 마·체 ━ 체력
        Relics.Board pv = Relics.evaluate(board("megingjord", "draupnir", "mimir", "megingjord", "mimir", "skidbladnir", "skidbladnir", "brisingamen", "idunn"), 60);
        Relics.Group str = group(pv, "str", 0);
        Relics.Group in = group(pv, "int", 1);
        Relics.Group vit = group(pv, "vit", 8);
        check(str != null && str.cells().equals(List.of(0, 1, 3)), "preview str group " + str);
        check(in != null && in.cells().equals(List.of(1, 2, 4, 7)), "preview int group " + in);
        check(vit != null && vit.cells().equals(List.of(7, 8)), "preview vit group " + vit);
        check(group(pv, "dex", 5).size() == 1 && group(pv, "dex", 6).size() == 1, "dex singles");
        check(pv.links().size() == 6, "preview links " + pv.links());
        check(pv.links().contains(new Relics.Link(0, 1, "str")) && pv.links().contains(new Relics.Link(0, 3, "str")) && pv.links().contains(new Relics.Link(4, 7, "int"))
                && pv.links().contains(new Relics.Link(7, 8, "vit")), "preview link list");
        check(pv.tier("megingjord") == 1 && pv.tier("draupnir") == 1 && pv.tier("mimir") == 1 && pv.tier("brisingamen") == 1 && pv.tier("idunn") == 0
                && pv.tier("skidbladnir") == 0 && pv.tier("gleipnir") == 0, "preview tiers " + pv.tiers());
        check(pv.active() == 4 && pv.filled() == 9 && pv.tiers().size() == 6, "preview active " + pv.active() + " " + pv.tiers());
        check(pv.cellTier()[8] == 0 && pv.cellTier()[1] == 1 && pv.cellTier()[5] == 0, "cell tiers");
        near(pv.totals().get("atk"), 9 * 1.5, "preview totals atk");
        // 7칸 힘 묶음 + 드라우프니르가 잇는 마력 3칸
        Relics.Board big = Relics.evaluate(board("megingjord", "megingjord", "megingjord", "jarngreipr", "aegishjalmr", "draupnir", "megingjord", "mimir", "gleipnir"), 60);
        check(group(big, "str", 0).size() == 7, "str 7 " + group(big, "str", 0));
        check(group(big, "int", 5) != null && group(big, "int", 5).cells().equals(List.of(5, 7, 8)), "int 3 via draupnir " + group(big, "int", 5));
        check(big.tier("megingjord") == 3 && big.tier("jarngreipr") == 3 && big.tier("aegishjalmr") == 3 && big.tier("draupnir") == 3, "tier 3 " + big.tiers());
        check(big.tier("mimir") == 1 && big.tier("gleipnir") == 1 && big.active() == 6, "tier 1 pair " + big.tiers());
        check(group(big, "dex", 3).size() == 1 && group(big, "dex", 8).size() == 1, "dex not joined (3, 8 apart)");
        // 같은 종류 둘: 높은 단계 하나
        Relics.Board two = Relics.evaluate(board("idunn", "idunn", "idunn", null, null, null, "idunn", null, null), 60);
        check(two.tier("idunn") == 1 && two.tiers().size() == 1 && group(two, "vit", 6).size() == 1, "same type keeps highest " + two.tiers());
        // 착용 레벨: 초월 (50렙) 이 49렙이면 끊김
        List<Relics.Cell> lv = board("megingjord", "megingjord", "megingjord", null, null, null, null, null, null);
        lv.set(1, c("megingjord", "transcend", "rune", 0));
        lv.set(0, c("megingjord", "hero", "rune", 2));
        lv.set(2, c("megingjord", "hero", "crit", 0));
        Relics.Board low = Relics.evaluate(lv, 49);
        check(!low.usable()[1] && low.usable()[0] && low.usable()[2] && low.links().isEmpty() && low.tier("megingjord") == 0, "level gate breaks the chain");
        near(low.totals().getOrDefault("rune", 0.0), 2.4 * 1.08, "gated cell has no line (hero rune +2)");
        near(low.totals().getOrDefault("crit", 0.0), 1.2, "hero crit");
        Relics.Board high = Relics.evaluate(lv, 50);
        check(high.tier("megingjord") == 1 && high.links().size() == 2, "at 50 all three join");
        near(high.totals().get("rune"), 2.4 * 1.08 + 3.2, "rune sum");
        // 맞지 않는 스탯 키는 첫 줄 (공격력)
        Relics.Board bad = Relics.evaluate(List.of(new Relics.Cell(Relics.id("mimir", "rare"), "str", 0)), 60);
        near(bad.totals().get("atk"), 0.6, "bad stat → atk");
        // 판 밖 칸 · 아티팩트가 아닌 ID 는 무시
        Relics.Board junk = Relics.evaluate(List.of(new Relics.Cell("rpg:artifact_ring_rare", "atk", 0)), 60);
        check(junk.filled() == 0 && junk.totals().isEmpty() && !junk.usable()[0], "junk id");
        // ---------------------------------------------------------------- 위력
        Map<String, Double> s = new LinkedHashMap<>();
        s.put("str", 100.0);
        s.put("int", 40.0);
        s.put("lvmax", 58.0);
        near(Relics.applied(s, Relics.type("draupnir")), 120, "applied str+int");
        near(Relics.applied(s, Relics.type("mimir")), 85, "applied off-stat 0.85");
        near(Relics.applied(s, Relics.type("megingjord")), 100, "applied single");
        double perPoint = r.obj("RUNE").num("perPoint");
        near(Relics.power(s, Relics.type("draupnir")), Runes.runeLevelBase(58) * (1 + 120 * perPoint), "power");
        // ---------------------------------------------------------------- 드랍
        check(Relics.monsterRolls(1).isEmpty() && Relics.monsterRolls(3).isEmpty(), "Lv1 scarecrow no drop");
        check(Relics.monsterRolls(4).equals(List.of(new Relics.Roll("common", 0.01))) && Relics.monsterRolls(19).get(0).grade().equals("common"), "Lv4-19 common");
        check(Relics.monsterRolls(27).equals(List.of(new Relics.Roll("rare", 0.008))), "Lv20-39 rare");
        check(Relics.monsterRolls(58).size() == 2 && Relics.monsterRolls(42).get(0).grade().equals("noble") && Relics.monsterRolls(42).get(1).grade().equals("hero"),
                "void noble + hero");
        for (Object b : r.root().list("ALL_BOSS_ORDER")) check(!Relics.bossRolls(String.valueOf(b)).isEmpty() && Relics.bossRolls(String.valueOf(b)).get(0).chance() >= 1, "boss drops " + b);
        check(Relics.bossRolls("hel").equals(List.of(new Relics.Roll("transcend", 1), new Relics.Roll("transcend", 0.3))), "hel drops");
        check(Relics.bossRolls("scarecrow_king").equals(List.of(new Relics.Roll("common", 1))), "scarecrow drops");
        check(Relics.bossRolls("nope").isEmpty(), "unknown boss");
        // 더 강한 보스일수록 등급이 오름 (첫 굴림)
        int prev = -1;
        for (Object b : r.root().list("ALL_BOSS_ORDER")) {
            int g = r.grade(Relics.bossRolls(String.valueOf(b)).get(0).grade()).index();
            check(g >= prev, "grade rises with boss " + b);
            prev = g;
        }
        check(Relics.typeFor(0).equals("megingjord") && Relics.typeFor(0.9999).equals("brisingamen") && Relics.typeFor(0.15).equals("skidbladnir")
                && Relics.typeFor(1.5).equals("brisingamen") && Relics.typeFor(-1).equals("megingjord"), "typeFor");
        // ---------------------------------------------------------------- 글자
        List<String> lore = Relics.lore(Relics.relic("draupnir", "hero"), 3, 30);
        String all = String.join("\n", lore);
        check(lore.get(0).contains("아티팩트") && lore.get(0).contains("불어나는 반지"), "lore head " + lore.get(0));
        check(all.contains("§c착용 40렙") && all.contains("지금 30") && all.contains("강화 +3"), "lore level / enhance " + all);
        check(all.contains("§c힘") && all.contains("§9마력") && all.contains("「아홉째 밤」") && all.contains("금고리 8개") && all.contains("3 · 5 · 7칸"), "lore passive " + all);
        check(all.contains(StatTree.bonusText("atk", 1.2 * 1.12)), "lore line +3 " + all);
        check(Relics.lineName("critDmg").equals("치명타 피해") && Relics.statName("vit").equals("체력") && Relics.statsText(Relics.type("brisingamen")).equals("§d체력 §7· §9마력"),
                "names");
        // ---------------------------------------------------------------- 장신구 강화 합
        List<String> ids = new ArrayList<>(List.of("rpg:artifact_ring_transcend", "rpg:artifact_ring_transcend", "", "", ""));
        Map<String, Double> t0 = Artifacts.totals(ids, 60);
        Map<String, Double> t10 = Artifacts.totals(ids, List.of(10, 0, 0, 0, 0), 60);
        near(t0.get("atk"), 8, "accessory +0");
        near(t10.get("atk"), 4 * 1.5 + 4, "accessory +10 ring");
        near(Artifacts.bonusAt(Artifacts.art("bracelet", "hero"), 5).get("critDmg"), 9 * 1.22, "bonusAt +5");
        check(Artifacts.optionText(Artifacts.art("ring", "transcend"), 10).equals(StatTree.bonusText("atk", 6) + " · " + StatTree.bonusText("crit", 4.5)), "option text +10");
        check(Artifacts.lore(Artifacts.art("ring", "rare"), 2, 30).get(0).startsWith("§r§d장신구"), "accessory lore renamed");

        System.out.println("RelicsTest: " + ok + " ok, " + BAD.size() + " bad");
        for (String b : BAD) System.out.println("  BAD " + b);
        if (!BAD.isEmpty()) System.exit(1);
    }
}
