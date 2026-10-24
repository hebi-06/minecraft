package mmorpg.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 0.37.0 아티팩트 시험 (JUnit 없이 main — check-core.sh): rules_more.json ARTIFACTS 의 모양 (종류 넷 · 등급 다섯 · 칸 다섯 · 옵션 둘 · 등급마다 오르는 값),
 * 착용 레벨, ID, 칸 · 종류 맞춤, 보너스 합 (렙제 · 칸 종류), 스탯 트리와 합친 상한, 제작 비용, 글자. 하나라도 틀리면 종료 코드 1.
 */
public final class ArtifactsTest {
    private ArtifactsTest() {}

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
        Rules r = Rules.get();
        // 모양
        check(Artifacts.enabled(), "enabled");
        check(Artifacts.types().equals(List.of("ring", "necklace", "earring", "bracelet")), "types " + Artifacts.types());
        check(Artifacts.slots().equals(List.of("ring", "ring", "necklace", "earring", "bracelet")) && Artifacts.size() == 5, "slots " + Artifacts.slots());
        check(Artifacts.slotName(0).equals("반지 1") && Artifacts.slotName(1).equals("반지 2") && Artifacts.slotName(4).equals("팔찌"), "slot names");
        check(Artifacts.slotsFor("ring").equals(List.of(0, 1)) && Artifacts.slotsFor("necklace").equals(List.of(2)) && Artifacts.slotsFor("bracelet").equals(List.of(4)),
                "slotsFor");
        check(Artifacts.all().size() == 20, "20 artifacts " + Artifacts.all().size());
        int[] levels = {10, 20, 30, 40, 50};
        for (Grade g : r.grades()) check(Artifacts.level(g.id()) == levels[g.index()], "level " + g.id() + " " + Artifacts.level(g.id()));
        check(Artifacts.glint("transcend") && !Artifacts.glint("hero"), "glint transcend only");
        for (String t : Artifacts.types()) {
            Artifacts.Type ty = Artifacts.type(t);
            check(ty.bonus().size() == 2, t + " has 2 options " + ty.bonus().keySet());
            for (Map.Entry<String, List<Double>> e : ty.bonus().entrySet()) {
                List<Double> v = e.getValue();
                check(StatTree.kind(e.getKey()) != null, t + " kind " + e.getKey());
                for (int i = 1; i < v.size(); i++) check(v.get(i) > v.get(i - 1), t + "." + e.getKey() + " rises " + v);
            }
            for (Grade g : r.grades()) {
                Artifacts.Art a = Artifacts.art(t, g.id());
                check(a != null && a.id().equals("rpg:artifact_" + t + "_" + g.id()) && Artifacts.byId(a.id()) == a, "id " + t + " " + g.id());
                check(a != null && a.gradeIndex() == g.index() && a.level() == levels[g.index()], "art grade/level " + t + " " + g.id());
                check(a != null && a.name().startsWith(Artifacts.prefix(g.id()) + " ") && a.name().endsWith(ty.name()), "name " + (a == null ? null : a.name()));
            }
        }
        // 서로 다른 옵션 (종류마다 다름 — 여덟 종류 모두 한 번씩)
        List<String> kinds = new ArrayList<>();
        for (String t : Artifacts.types()) kinds.addAll(Artifacts.type(t).bonus().keySet());
        check(kinds.size() == 8 && kinds.stream().distinct().count() == 8, "8 distinct option kinds " + kinds);
        check(kinds.containsAll(List.of("atk", "crit", "hp", "def", "rune", "cdr", "critDmg", "speed")), "kinds " + kinds);
        check(Artifacts.byId("rpg:loot_troll") == null && Artifacts.byId(null) == null && Artifacts.art("ring", "mythic") == null, "not artifacts");

        // 값 (사용자 견본: 반지 공격력 % · 치명타 확률, 목걸이 최대 체력 · 받는 피해 감소, 귀걸이 룬 피해 % · 재사용 대기 감소, 팔찌 치명타 피해 % · 이동 속도)
        Artifacts.Art ringT = Artifacts.art("ring", "transcend");
        near(ringT.bonus().get("atk"), 4, "transcend ring atk");
        near(ringT.bonus().get("crit"), 3, "transcend ring crit");
        near(Artifacts.art("necklace", "common").bonus().get("hp"), 8, "common necklace hp");
        near(Artifacts.art("earring", "noble").bonus().get("rune"), 4.5, "noble earring rune");
        near(Artifacts.art("bracelet", "hero").bonus().get("critDmg"), 9, "hero bracelet critDmg");

        // 칸 · 종류
        check(Artifacts.fits(0, ringT) && Artifacts.fits(1, ringT) && !Artifacts.fits(2, ringT) && !Artifacts.fits(5, ringT) && !Artifacts.fits(-1, ringT), "ring fits 0 · 1");
        check(Artifacts.fits(2, Artifacts.art("necklace", "rare")) && !Artifacts.fits(0, Artifacts.art("necklace", "rare")), "necklace fits 2");
        check(Artifacts.usable(ringT, 50) && !Artifacts.usable(ringT, 49.9) && !Artifacts.usable(null, 99), "usable by level");

        // 합 (초월 다섯 · 50렙)
        List<String> full = Arrays.asList(Artifacts.id("ring", "transcend"), Artifacts.id("ring", "transcend"), Artifacts.id("necklace", "transcend"),
                Artifacts.id("earring", "transcend"), Artifacts.id("bracelet", "transcend"));
        Map<String, Double> t50 = Artifacts.totals(full, 50);
        near(get(t50, "atk"), 8, "full atk");
        near(get(t50, "crit"), 6, "full crit");
        near(get(t50, "hp"), 40, "full hp");
        near(get(t50, "def"), 4, "full def");
        near(get(t50, "rune"), 8, "full rune");
        near(get(t50, "cdr"), 4, "full cdr");
        near(get(t50, "critDmg"), 12, "full critDmg");
        near(get(t50, "speed"), 4, "full speed");
        check(Artifacts.totals(full, 49).isEmpty(), "49렙: no bonus " + Artifacts.totals(full, 49));
        // 섞임: 반지 1 영웅 · 반지 2 비움 · 목걸이 칸에 반지 (안 셈) · 귀걸이 일반 · 팔찌 모르는 ID, 45렙
        List<String> mixed = Arrays.asList(Artifacts.id("ring", "hero"), null, Artifacts.id("ring", "common"), Artifacts.id("earring", "common"), "rpg:nothing");
        Map<String, Double> mx = Artifacts.totals(mixed, 45);
        near(get(mx, "atk"), 3, "mixed atk (hero ring only)");
        near(get(mx, "crit"), 2, "mixed crit");
        near(get(mx, "rune"), 2, "mixed rune");
        near(get(mx, "cdr"), 1, "mixed cdr");
        check(!mx.containsKey("hp") && !mx.containsKey("speed"), "mixed: wrong slot · unknown ignored " + mx);
        check(Artifacts.totals(null, 60).isEmpty() && Artifacts.totals(List.of(), 60).isEmpty(), "empty totals");

        // 스탯 트리와 합침 · 상한 (crit 50 · def 40 · speed 40 · cdr 40)
        Map<String, Double> c = Artifacts.combine(Map.of("crit", 48.0, "atk", 10.0, "def", 39.0), t50);
        near(get(c, "crit"), StatTree.caps().get("crit"), "crit capped");
        near(get(c, "def"), 40, "def capped");
        near(get(c, "atk"), 18, "atk adds");
        near(get(c, "hp"), 40, "hp from artifacts only");
        check(Artifacts.combine(null, null).isEmpty() && Artifacts.combine(Map.of("atk", 3.0), null).equals(Map.of("atk", 3.0)), "combine empty");

        // 비용 (등급마다 · 모든 종류 같음)
        Artifacts.Cost common = Artifacts.cost("common");
        check(common.silver() == 20 && common.gold() == 0 && common.items().equals(Map.of("rpg:loot_boar", 6, "rpg:loot_gulon", 6)), "common cost " + common);
        Artifacts.Cost tr = Artifacts.cost("transcend");
        check(tr.silver() == 0 && tr.gold() == 6 && tr.items().get("rpg:muspel_heart") == 1 && tr.items().get("rpg:laevateinn_shard") == 2, "transcend cost " + tr);
        check(Artifacts.cost("hero").items().get("rpg:ymir_bone") == 1 && Artifacts.cost("hero").gold() == 3, "hero cost");
        for (Grade g : r.grades()) {
            Artifacts.Cost k = Artifacts.cost(g.id());
            check(k != null && !k.items().isEmpty() && (k.silver() > 0 || k.gold() > 0), "cost " + g.id());
            for (String id : k.items().keySet()) check(id.startsWith("rpg:"), "cost item id " + id);
        }

        // 글자
        check(Artifacts.optionLines(ringT).equals(List.of("공격력 +4%", "치명타 확률 +3%p")), "option lines " + Artifacts.optionLines(ringT));
        check(Artifacts.optionText(Artifacts.art("necklace", "rare")).equals("최대 체력 +14 · 받는 피해 -1.5%"), "necklace text " + Artifacts.optionText(Artifacts.art("necklace", "rare")));
        check(ringT.name().equals("무스펠의 반지") && ringT.coloredName().startsWith("§c") && ringT.typeName().equals("반지") && ringT.gradeName().equals("초월"),
                "transcend ring name " + ringT.coloredName());
        check(Artifacts.art("ring", "rare").name().equals("청옥 반지"), "rare ring name");
        List<String> lore = Artifacts.lore(ringT, 45);
        check(lore.size() >= 5 && lore.get(0).contains("반지") && lore.get(0).contains("칸 2개") && lore.get(1).contains("§c착용 50렙") && lore.get(1).contains("지금 45")
                && lore.get(2).contains("공격력 +4%"), "lore (level short) " + lore);
        List<String> lore2 = Artifacts.lore(Artifacts.art("bracelet", "common"), -1);
        check(lore2.get(1).contains("§7착용 10렙") && !lore2.get(1).contains("지금"), "lore (level unknown) " + lore2);

        System.out.println("[artifacts-test] checks ok " + ok + ", bad " + BAD.size());
        for (String b : BAD) System.out.println("  BAD " + b);
        if (!BAD.isEmpty()) System.exit(1);
    }
}
