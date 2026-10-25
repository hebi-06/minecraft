package mmorpg.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 0.38.0 동작 시험 (JUnit 없이 main — check-core.sh): rules_more.json MOTION 의 연타 차례 (근접 무기 아홉 · 칼 · 마검 · 낫 = 가로 → 세로 → 회전),
 * 연타 단계 규칙 (comboWindow 안이면 다음 · 끝이면 처음 · 넘으면 처음), 회전 베기 · 구르기 값, 구르기 틱마다 빠르기 (합 = 거리 · 줄어듦),
 * 스킬 → 동작 표가 무기 스킬 · 룬 (fn) · 직업 스킬을 모두 덮는지, 동작 이름이 poses.json 과 같은지 (열쇠 t 0 → 1 · 오름차순 · 1인칭 열쇠).
 */
public final class MotionsTest {
    private MotionsTest() {}

    private static final List<String> BAD = new ArrayList<>();
    private static int ok;

    private static void check(boolean cond, String what) {
        if (cond) ok++;
        else BAD.add(what);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] argv) throws Exception {
        Rules r = Rules.get();
        // 연타 차례
        List<String> three = List.of("slash_h", "slash_v", "spin");
        for (String t : List.of("sword", "greatsword", "scythe")) check(Motions.combo(t).equals(three), "combo " + t + " " + Motions.combo(t));
        check(Motions.combo("dagger").equals(List.of("dagger_x")), "dagger");
        check(Motions.combo("axe").equals(List.of("chop")) && Motions.combo("hammer").equals(List.of("chop")), "axe · hammer chop");
        check(Motions.combo("lance").equals(List.of("thrust", "lance_sweep")) && Motions.combo("shield").equals(List.of("bash"))
                && Motions.combo("scepter").equals(List.of("smite")), "lance (0.42.0 찌르기 → 가로 휘두르기) · shield · scepter");
        check(Motions.combo("bow").isEmpty() && Motions.combo("wand").isEmpty() && Motions.combo(null).isEmpty(), "ranged none");
        for (String t : List.of("sword", "greatsword", "scythe", "dagger", "axe", "hammer", "lance", "shield", "scepter")) {
            for (String m : Motions.combo(t)) check(Motions.KNOWN.contains(m), "known " + m);
        }
        // 연타 단계
        int win = Motions.comboWindow();
        check(win == 24, "window " + win);
        check(Motions.nextStep(0, 0, 100, 3) == 1, "first swing 1");
        check(Motions.nextStep(1, 100, 110, 3) == 2, "1 → 2");
        check(Motions.nextStep(2, 100, 100 + win, 3) == 3, "2 → 3 at window edge");
        check(Motions.nextStep(3, 100, 105, 3) == 1, "3 → 1 (wrap)");
        check(Motions.nextStep(2, 100, 100 + win + 1, 3) == 1, "past window → 1");
        check(Motions.nextStep(1, 100, 90, 3) == 1, "time went back → 1");
        check(Motions.nextStep(1, 100, 105, 1) == 1, "single motion stays 1");
        check(Motions.nextStep(1, 100, 105, 0) == 0, "no combo 0");
        // 회전 베기 · 구르기
        check(Math.abs(Motions.spinRatio() - 0.4) < 1e-9 && Math.abs(Motions.spinRange() - 3) < 1e-9, "spin 0.4 · 3");
        check(Motions.rollTicks() == 10 && Math.abs(Motions.rollDistance() - 4) < 1e-9 && Motions.rollCooldown() == 20 && Motions.doubleTap() == 6,
                "roll 10 ticks · 4 blocks · 20 cooldown · 6 tap");
        double[] v = Motions.rollSpeeds();
        double sum = 0;
        for (double x : v) sum += x;
        check(v.length == Motions.rollTicks() && Math.abs(sum - Motions.rollDistance()) < 1e-9, "roll speeds sum " + sum);
        for (int i = 1; i < v.length; i++) check(v[i] < v[i - 1], "roll slows " + i);
        check(v[0] < 1.0, "first tick under 1 block/tick (moved-too-quickly safe) " + v[0]);
        // 0.39.0 평타 사이 · 참격 범위
        check(Motions.attackInterval() == 10 && Motions.serverInterval() == 8, "interval 10 · server 8");
        check(Motions.areaEnabled() && Motions.areaMax() == 6, "area on · max 6");
        for (String t : List.of("sword", "greatsword", "scythe", "dagger", "axe", "hammer", "lance", "shield", "scepter")) {
            for (String m : Motions.combo(t)) {
                Motions.Area a = Motions.area(m, t);
                check(a != null && List.of("arc", "band", "line", "ring").contains(a.shape()) && a.range() >= 1.4 && a.range() <= 5, "area " + t + " " + m + " " + a);
                if (a == null) continue;
                // 바로 앞 1.2칸 (눈높이보다 조금 아래 — 좀비 가운데) 은 늘 맞음, 바로 뒤 1.2칸은 회전만 (0.40.0: 단검 · 도끼 궤적은 1.5 ~ 1.9칸)
                check(Motions.inArea(a, 1.2, 0, -0.6, 0.3), "front hit " + m);
                check(Motions.inArea(a, -1.2, 0, -0.6, 0.3) == a.shape().equals("ring"), "behind only ring " + m);
                check(!Motions.inArea(a, a.range() + 1.0, 0, 0, 0.3), "past range " + m);
            }
        }
        // 0.40.0 가로 베기: 궤적이 덮는 앞쪽 160° · 거리는 칼 궤적이 닿는 곳 (보통 2.3 · 초월 3.45 — reach 표)
        Motions.Area h = Motions.area("slash_h", "sword");
        check(Math.abs(h.angle() - 160) < 1e-9 && Math.abs(h.range() - 2.3) < 1e-9, "sword arc 160° · 2.3 " + h);
        double s45 = Math.sin(Math.toRadians(45)) * 2.0;
        double c45 = Math.cos(Math.toRadians(45)) * 2.0;
        check(Motions.inArea(h, c45, s45, 0, 0), "arc 45° side in");
        double s75 = Math.sin(Math.toRadians(75)) * 2.0;
        double c75 = Math.cos(Math.toRadians(75)) * 2.0;
        check(Motions.inArea(h, c75, s75, 0, 0), "arc 75° side in");
        check(!Motions.inArea(h, 0.17, 1.95, 0, 0), "arc 85° side out");
        check(Motions.inArea(h, 0.17, 1.95, 0, 0.6) == (Math.atan2(1.95, 0.17) <= Math.toRadians(80) + Math.asin(0.6 / Math.hypot(0.17, 1.95))), "arc pad widens");
        check(!Motions.inArea(h, 2.5, 0, 0, 0.1), "sword arc ends at 2.3 (+ body)");
        Motions.Area top = Motions.area("slash_h", "sword", 4);
        check(Math.abs(top.range() - 3.45) < 1e-9 && Motions.inArea(top, 3.0, 0, 0, 0.3), "transcend sword reaches 3.45 " + top);
        check(Math.abs(Motions.area("slash_h", "sword", 9).range() - 3.45) < 1e-9 && Math.abs(Motions.area("slash_h", "sword", -1).range() - 2.3) < 1e-9,
                "grade outside the table → nearest");
        Motions.Area sc = Motions.area("slash_h", "scythe");
        check(Math.abs(sc.angle() - 160) < 1e-9 && Math.abs(sc.range() - 3.05) < 1e-9 && sc.range() > h.range(), "scythe arc 160° · 3.05 (longer than a sword) " + sc);
        Motions.Area dx = Motions.area("dagger_x", "dagger");
        check(Math.abs(dx.angle() - 120) < 1e-9 && Math.abs(dx.range() - 1.5) < 1e-9, "dagger X 120° · 1.5 " + dx);
        // 0.42.0 창: 찌르기는 줄 4칸 그대로 (fixed — reach 표를 쓰지 않음), 가로 휘두르기는 궤적이 닿는 곳 (보통 3.45 · 초월 3.9) · 앞 160°
        for (int g = 0; g <= 4; g++) check(Math.abs(Motions.area("thrust", "lance", g).range() - 4.0) < 1e-9, "lance thrust fixed 4 (grade " + g + ")");
        Motions.Area ls = Motions.area("lance_sweep", "lance");
        check(ls.shape().equals("arc") && Math.abs(ls.angle() - 160) < 1e-9 && Math.abs(ls.range() - 3.45) < 1e-9
                && Math.abs(Motions.area("lance_sweep", "lance", 4).range() - 3.9) < 1e-9, "lance sweep arc 160° · 3.45 · 3.9 " + ls);
        check(Motions.inArea(ls, 1.0, -3.0, 0, 0.3) && Motions.inArea(ls, 1.0, 3.0, 0, 0.3) && !Motions.inArea(Motions.area("thrust", "lance"), 1.0, 3.0, 0, 0.3),
                "lance sweep reaches both sides (thrust line does not)");
        check(!Motions.inArea(ls, -1.2, 0, -0.6, 0.3) && Motions.inArea(Motions.area("thrust", "lance"), 3.9, 0.3, 0, 0.3) && !Motions.inArea(ls, 3.9, 0, 0, 0.3),
                "lance sweep: not behind · thrust reaches further ahead (4 vs 3.45)");
        Motions.Area v2 = Motions.area("slash_v", "sword");
        check(Motions.inArea(v2, 2.5, 0.5, 1.2, 0.3) && !Motions.inArea(v2, 2.5, 1.5, 0, 0.3), "band narrow sideways · tall");
        Motions.Area ln = Motions.area("thrust", "lance");
        check(Motions.inArea(ln, 3.9, 0.3, 0, 0.3) && !Motions.inArea(ln, 2, 1.2, 0, 0.3), "line long · thin");
        Motions.Area rg = Motions.area("spin", "sword");
        check(Motions.inArea(rg, -1.8, 1, 0.5, 0.3) && !Motions.inArea(rg, 0, 0, 4.5, 0.3) && !Motions.inArea(rg, 3.5, 0, 0.5, 0.3), "ring around · not far · not high");
        // 0.40.0 참격 궤적 규칙: 평타 여섯은 궤적이 참격을 대신 (창 찌르기 · 방패 밀치기는 예전 참격)
        check(Motions.trailEnabled(), "trail on");
        for (String m : List.of("slash_h", "slash_v", "spin", "dagger_x", "chop", "smite", "lance_sweep")) check(Motions.trailBasic(m), "trail basic " + m);
        check(!Motions.trailBasic("thrust") && !Motions.trailBasic("bash") && !Motions.trailBasic("cast") && !Motions.trailBasic(null), "no trail for thrust · bash");
        // 그리기 설정 (클라이언트 BladeTrails 가 읽음): 겹 몫 0 ~ 1 · 1인칭 너비 · 가까이 옅어짐 [가까운, 먼]
        Obj trail = Rules.get().root().obj("TRAIL");
        Obj ly = trail.obj("layers");
        check(ly.num("glowIn") >= 0 && ly.num("glowIn") < 1 && ly.num("auraIn") >= 0 && ly.num("auraIn") < 1 && ly.num("auraOut") >= 0 && trail.num("glowTo") >= 1,
                "trail layers");
        Obj fpc = trail.obj("fp");
        check(fpc.num("width") > 0 && fpc.num("maxWidth") > 0 && fpc.num("alpha") > 0 && fpc.num("alpha") <= 1, "trail fp band");
        Obj lk = trail.obj("look"); // 0.41.0 평타 강화 모습이 실린 궤적: 심지 희게 몫 · 1인칭 늘임 몫
        check(lk.num("core") >= 0 && lk.num("core") <= 1 && lk.num("fp") > 0 && lk.num("fp") <= 1, "trail look");
        for (String k : List.of("fp", "world")) {
            List<Object> nr = trail.obj("near").list(k);
            check(nr.size() == 2 && ((Number) nr.get(0)).doubleValue() >= 0 && ((Number) nr.get(1)).doubleValue() > ((Number) nr.get(0)).doubleValue(), "trail near " + k);
        }
        for (String t : List.of("sword", "greatsword", "scythe", "dagger", "axe", "hammer", "scepter", "lance")) {
            Double r0 = Motions.reach(t, 0);
            Double r4 = Motions.reach(t, 4);
            check(r0 != null && r4 != null && r0 >= 1.4 && r4 <= 4.5, "reach row " + t + " " + r0 + " … " + r4);
        }
        check(Motions.area("cast", "wand") == null && Motions.area("nope", "sword") == null, "no area for skills");
        // 0.41.0 평타 강화 모습: 평타 강화 룬 다섯 (fn) · 직업 평타 태세 셋, 룬마다 범위 배율 (사용자 — 룬마다 다르게), 사신 현신 5칸
        check(SwingLooks.enabled(), "swing looks on");
        Obj strike = Rules.get().obj("RUNES").obj("strike");
        for (String g : strike.keys()) {
            String fn = strike.obj(g).str("fn");
            check(SwingLooks.of(fn) != null, "strike rune look " + g + " " + fn);
        }
        Map<String, Double> scales = Map.of("edgeHone", 1.15, "flurry", 1.2, "bloodBlade", 1.3, "cleave", 1.4, "swordGod", 1.5,
                "swordsman_mirror", 1.2, "spellblade_awaken", 1.3, "reaper_avatar", 1.0);
        for (Map.Entry<String, Double> e : scales.entrySet()) {
            SwingLooks.Look l = SwingLooks.of(e.getKey());
            check(l != null && Math.abs(l.scale() - e.getValue()) < 1e-9 && l.glow() != null && l.aura() != null && SwingLooks.pack(l.glow()) != 0,
                    "look " + e.getKey() + " " + l);
        }
        check(SwingLooks.keys().size() == scales.size(), "look keys " + SwingLooks.keys());
        SwingLooks.Look av = SwingLooks.of("reaper_avatar");
        double sb = SwingLooks.baseRange("scythe", 0);
        check(Math.abs(sb - 3.05) < 1e-9 && Math.abs(av.scaleAt(sb) - 5 / 3.05) < 1e-9 && av.fits("scythe") && !av.fits("sword"), "reaper avatar 5 blocks " + sb);
        check(SwingLooks.of("cleave").fits("sword") && SwingLooks.of("cleave").fits("bow"), "rune looks fit any weapon");
        check(!SwingLooks.of("swordsman_mirror").fits("greatsword") && SwingLooks.of("spellblade_awaken").fits("greatsword"), "job look weapons");
        check(Math.abs(SwingLooks.baseRange("sword", 0) - 2.3) < 1e-9 && SwingLooks.baseRange("bow", 0) == 0, "base range (first combo motion)");
        check(SwingLooks.of("nope") == null && SwingLooks.pack(new double[] {0, 0, 0}) == 1 && SwingLooks.pack(new double[] {1, 0.5, 0}) == 0xFF8000, "pack");
        // 스킬 표
        Set<String> want = new HashSet<>();
        Obj root = r.root();
        for (String src : List.of("SKILLS")) {
            Obj s = root.obj(src);
            for (String k : s.keys()) {
                if (s.get(k) instanceof List<?> l) {
                    for (Object o : l) if (o instanceof Map<?, ?> m && m.get("id") instanceof String id) want.add(id);
                }
            }
        }
        Obj hero = root.objOrNull("HERO_SKILLS");
        if (hero != null) {
            for (String k : hero.keys()) if (hero.get(k) instanceof Map<?, ?> m && m.get("id") instanceof String id) want.add(id);
        }
        int weapon = want.size();
        Obj runes = root.obj("RUNES");
        Set<String> fns = new HashSet<>();
        for (String fam : runes.keys()) {
            if (fam.startsWith("_")) continue;
            Object o = runes.get(fam);
            Iterable<?> vals = o instanceof Map<?, ?> m ? m.values() : o instanceof List<?> l ? l : List.of();
            for (Object d : vals) if (d instanceof Map<?, ?> m && m.get("fn") instanceof String fn) fns.add(fn);
        }
        for (String fn : fns) want.add("rune:" + fn);
        int jobs = 0;
        for (Jobs.Job j : Jobs.all()) {
            for (Jobs.Skill k : j.skills()) {
                if (k.id() == null || k.id().isEmpty()) continue;
                want.add(k.id());
                jobs++;
            }
        }
        check(weapon >= 32, "weapon skills " + weapon);
        check(fns.size() >= 120, "rune fns " + fns.size());
        check(jobs >= 55, "job skills " + jobs);
        for (String k : want) check(Motions.skillMotion(k) != null, "skill motion " + k);
        for (String k : Motions.skillKeys()) check(want.contains(k), "no stray key " + k);
        check(Motions.skillMotion("mjolnir_throw").equals("throw_weapon") && Motions.skillMotion("gaebolg_pierce").equals("throw_weapon"), "spear · hammer throws");
        check(Motions.skillMotion("gram_charge").equals("dash") && Motions.skillMotion("warrior_charge").equals("dash"), "dashes");
        check(Motions.skillMotion("nothing") == null, "unknown → null");
        // poses.json 과 같은 이름
        Path poses = Path.of("src/main/resources/assets/mmorpg/motion/poses.json");
        Map<String, Object> pj = Json.parseObject(Files.readString(poses, StandardCharsets.UTF_8));
        Map<String, Object> motions = (Map<String, Object>) pj.get("motions");
        check(motions.keySet().equals(Motions.KNOWN), "poses.json names " + motions.keySet());
        for (Map.Entry<String, Object> e : motions.entrySet()) {
            Map<String, Object> m = (Map<String, Object>) e.getValue();
            // 0.44.0 대장장이 망치질 (smith_hammer — 세 번 침) 만 길게
            int maxTicks = e.getKey().equals("smith_hammer") ? 40 : 20;
            check(((Number) m.get("ticks")).intValue() >= 6 && ((Number) m.get("ticks")).intValue() <= maxTicks, e.getKey() + " ticks");
            for (String part : List.of("keys", "fp")) {
                List<Object> keys = (List<Object>) m.get(part);
                check(keys != null && keys.size() >= 2, e.getKey() + " " + part);
                if (keys == null) continue;
                double last = -1;
                for (Object k : keys) {
                    double t = ((Number) ((Map<String, Object>) k).get("t")).doubleValue();
                    check(t >= last && t >= 0 && t <= 1, e.getKey() + " " + part + " t order " + t);
                    last = t;
                }
                check(((Number) ((Map<String, Object>) keys.get(0)).get("t")).doubleValue() == 0 && last == 1, e.getKey() + " " + part + " 0 → 1");
            }
        }
        System.out.println("[motions-test] checks ok " + ok + ", bad " + BAD.size());
        for (String b : BAD) System.out.println("  BAD " + b);
        if (!BAD.isEmpty()) System.exit(1);
    }
}
