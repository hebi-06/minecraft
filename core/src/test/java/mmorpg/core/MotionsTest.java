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
        check(Motions.combo("lance").equals(List.of("thrust")) && Motions.combo("shield").equals(List.of("bash")) && Motions.combo("scepter").equals(List.of("smite")),
                "lance · shield · scepter");
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
            check(((Number) m.get("ticks")).intValue() >= 6 && ((Number) m.get("ticks")).intValue() <= 20, e.getKey() + " ticks");
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
