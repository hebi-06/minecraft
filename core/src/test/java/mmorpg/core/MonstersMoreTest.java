package mmorpg.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 9단계 시험 (JUnit 없이 main — check-core.sh): 자바 전용 규칙 덧붙임 (rules_more.json) 과 레벨업 곡선.
 * <ul>
 * <li>덧붙임: 몬스터 14종 (애드온 6 + 새 8) — 순서는 레벨 순, 몬스터마다 설정 · 전리품 · 반복 의뢰, 애드온 6종은 그대로 (rules.json 과 같음)</li>
 * <li>새 몬스터: 지역 (farm · desert · sea · north) 마다 둘, 특성 값 (hop · charge · burrow · pack · onHit · shock · shell), 전리품 아이템 이름이 겹치지 않음</li>
 * <li>레벨마다 경험치를 주는 몬스터가 있음 (내 레벨 + 2 이하 · xpUntil 이 내 레벨보다 큼 — 0 ~ 44)</li>
 * <li>Leveling: 바닐라 곡선 · ×3 · 0.24.1 갈수록 더 (10렙 뒤로 커져 40렙에서 ×3 더) · 퀘스트 레벨 보상 → 바닐라 경험치</li>
 * <li>Rules.merge: 표는 더하고 목록 · 값은 바꾸고 "_" 는 건너뜀</li>
 * </ul>
 * 하나라도 틀리면 종료 코드 1.
 */
public final class MonstersMoreTest {
    private MonstersMoreTest() {}

    private static final List<String> BAD = new ArrayList<>();
    private static int ok;

    private static void check(boolean cond, String what) {
        if (cond) ok++;
        else BAD.add(what);
    }

    private static final List<String> OLD = List.of("scarecrow", "gulon", "skeljaskrimsli", "nix", "draugr", "troll");
    private static final Map<String, List<String>> NEW = new LinkedHashMap<>();

    static {
        NEW.put("farm", List.of("nisse", "boar"));
        NEW.put("desert", List.of("sand_scorpion", "worm_larva"));
        NEW.put("sea", List.of("crab", "grendel"));
        NEW.put("north", List.of("frost_wolf", "frost_giant"));
    }

    private static Map<String, Object> resource(String path) throws IOException {
        try (InputStream in = MonstersMoreTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException(path);
            return Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    public static void main(String[] argv) throws IOException {
        Rules r = Rules.get();
        check(Rules.hasMore(), "overlay present");
        List<String> order = r.strings("MONSTER_ORDER");
        Obj mons = r.obj("MONSTERS");
        Obj drops = r.obj("MONSTER_DROPS");
        Obj rep = r.obj("REPEAT_QUESTS");
        check(order.size() == 14, "14 monsters " + order);
        check(order.get(0).equals("scarecrow"), "scarecrow first (무기 시험 길 안내)");
        Set<String> all = new HashSet<>(order);
        check(all.size() == order.size(), "unique order");
        for (String k : OLD) check(all.contains(k), "old " + k);
        for (List<String> ks : NEW.values()) for (String k : ks) check(all.contains(k), "new " + k);
        // 몬스터마다: 설정 · 전리품 · 반복 의뢰, 레벨 순
        double prev = -1;
        Set<String> items = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (String k : order) {
            Obj m = mons.objOrNull(k);
            check(m != null, "MONSTERS." + k);
            if (m == null) continue;
            double lv = m.num("level");
            check(lv >= prev, "level order " + k + " " + lv + " after " + prev);
            prev = lv;
            check(m.num("xpUntil") > lv, k + " xpUntil > level");
            check(m.num("hp") > 0 && m.num("attack") > 0 && m.num("xp") > 0, k + " hp · attack · xp");
            check(m.list("box").size() == 2 && m.num("scale") > 0, k + " box · scale");
            check(m.list("color").size() == 3, k + " color");
            check(!m.obj("sounds").str("windup").isEmpty() && !m.obj("sounds").str("hit").isEmpty(), k + " sounds");
            check(!m.str("trait").isEmpty(), k + " trait");
            Obj d = drops.objOrNull(k);
            check(d != null && d.str("item").equals("rpg:loot_" + k), "drop item " + k);
            if (d != null) {
                check(items.add(d.str("item")), "drop item unique " + k);
                check(names.add(d.str("name")), "drop name unique " + d.str("name"));
                check(d.num("chance") > 0 && d.num("chance") <= 1 && d.num("price") > 0, k + " drop chance · price");
            }
            Obj q = rep.objOrNull(k);
            check(q != null && q.num("count") > 0 && q.num("emerald") > 0 && q.num("xp") > 0, "repeat quest " + k);
        }
        // 애드온 6종은 rules.json 그대로
        Map<String, Object> base = resource(Rules.RESOURCE);
        @SuppressWarnings("unchecked")
        Map<String, Object> baseMons = (Map<String, Object>) base.get("MONSTERS");
        for (String k : OLD) check(Json.stringify(baseMons.get(k)).equals(Json.stringify(mons.get(k))), "old " + k + " unchanged");
        // 새 몬스터: 지역 · 특성
        for (Map.Entry<String, List<String>> e : NEW.entrySet()) {
            for (String k : e.getValue()) check(e.getKey().equals(mons.obj(k).str("region")), k + " region " + e.getKey());
        }
        Obj nisse = mons.obj("nisse");
        check(nisse.obj("hop").num("back") > 0 && nisse.obj("hop").num("pause") > 0, "nisse hop");
        Obj boar = mons.obj("boar").obj("charge");
        check(boar.num("range") > 6.5 && boar.num("prep") > 0 && boar.num("dash") > 0 && boar.num("knock") > 0 && boar.num("mult") >= 1, "boar charge");
        check("poison".equals(mons.obj("sand_scorpion").obj("onHit").str("effect")), "scorpion poison");
        check(mons.obj("crab").num("shell") > 0 && "slowness".equals(mons.obj("crab").obj("onHit").str("effect")), "crab shell · grab");
        Obj burrow = mons.obj("worm_larva").obj("burrow");
        check(burrow.num("detect") > 0 && burrow.num("rest") > 0 && burrow.num("emerge") > 0, "larva burrow");
        check("wither".equals(mons.obj("grendel").obj("onHit").str("effect")), "grendel wither");
        check(mons.obj("frost_wolf").obj("pack").num("radius") > 0, "wolf pack");
        check(mons.obj("frost_giant").obj("shock").num("radius") > 0 && "ice".equals(mons.obj("frost_giant").obj("shock").str("fx")), "giant ice shock");
        // 레벨마다 경험치를 주는 몬스터 (내 레벨 + 2 이하, xpUntil > 내 레벨)
        for (int lv = 0; lv <= 44; lv++) {
            boolean any = false;
            for (String k : order) {
                Obj m = mons.obj(k);
                if (m.num("level") <= lv + 2 && m.num("xpUntil") > lv) any = true;
            }
            check(any, "xp source at level " + lv);
        }
        // 레벨업 곡선
        check(Leveling.baseNeed(0) == 7 && Leveling.baseNeed(14) == 35 && Leveling.baseNeed(15) == 37 && Leveling.baseNeed(29) == 107
                && Leveling.baseNeed(30) == 112 && Leveling.baseNeed(39) == 193, "vanilla curve");
        check(Math.abs(Leveling.needMult() - 3) < 1e-9, "needMult 3");
        // 0.24.1 갈수록 더 (ramp): 10렙까지 바닐라 × 3 그대로, 그 뒤 한 레벨마다 커져 40렙에서 × 3 (= 바닐라 × 9), 그 뒤로도 같은 기울기
        check(Leveling.ramp(0) == 1 && Leveling.ramp(10) == 1 && Math.abs(Leveling.ramp(25) - 2) < 1e-9 && Math.abs(Leveling.ramp(40) - 3) < 1e-9
                && Math.abs(Leveling.ramp(55) - 4) < 1e-9, "ramp 1 · 1 · 2 · 3 · 4 at 0 · 10 · 25 · 40 · 55");
        check(Leveling.need(0) == 21 && Leveling.need(9) == 75 && Leveling.need(10) == 81, "need up to level 10 = vanilla × 3 " + Leveling.need(0) + " " + Leveling.need(10));
        check(Leveling.need(11) == 93 && Leveling.need(20) == 310 && Leveling.need(30) == 784 && Leveling.need(39) == 1698 && Leveling.need(40) == 1818
                && Leveling.need(45) == 2470, "need with ramp " + Leveling.need(11) + " " + Leveling.need(20) + " " + Leveling.need(30) + " " + Leveling.need(39)
                        + " " + Leveling.need(40) + " " + Leveling.need(45));
        for (int lv = 10; lv < 60; lv++) check(Leveling.need(lv + 1) > Leveling.need(lv), "need grows at " + lv);
        // 0.24.2 최고 레벨 60
        check(Leveling.maxLevel() == 60 && Leveling.atMax(60) && Leveling.atMax(61) && !Leveling.atMax(59), "max level 60 " + Leveling.maxLevel());
        check(Leveling.pointsForLevels(0, 3) == 7 + 9 + 11, "quest 3 levels at 0 = 27 " + Leveling.pointsForLevels(0, 3));
        check(Leveling.pointsForLevels(30, 1) == 112 && Leveling.pointsForLevels(5, 0) == 0, "quest levels → points (vanilla — ramp 없음)");
        check(Leveling.total(1) == 21 && Leveling.total(11) == 561 && Leveling.total(40) == 19317, "total to 11 = 561 (그대로) · to 40 = 19,317 (예전 8,760) "
                + Leveling.total(11) + " " + Leveling.total(40));
        // merge
        Map<String, Object> a = new LinkedHashMap<>();
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("x", 1);
        a.put("T", t);
        a.put("L", new ArrayList<>(List.of(1, 2)));
        a.put("N", 1);
        Map<String, Object> o = new LinkedHashMap<>();
        Map<String, Object> t2 = new LinkedHashMap<>();
        t2.put("y", 2);
        t2.put("_c", "skip");
        o.put("T", t2);
        o.put("L", List.of(3));
        o.put("N", 5);
        o.put("_comment", "skip");
        Rules.merge(a, o);
        check(Json.stringify(a).equals("{\"T\":{\"x\":1,\"y\":2},\"L\":[3],\"N\":5}"), "merge " + Json.stringify(a));

        System.out.println("MonstersMoreTest: " + ok + " ok, " + BAD.size() + " failed");
        for (String b : BAD) System.out.println("  FAIL " + b);
        if (!BAD.isEmpty()) System.exit(1);
    }
}
