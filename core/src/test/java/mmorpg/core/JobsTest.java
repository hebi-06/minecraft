package mmorpg.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 0.27.0 직업 시험 (JUnit 없이 main — check-core.sh): jobs.json 의 모양 (직업 11 · 순서 · 무기 11종이 하나씩 · 칸 다섯 · 재사용 대기 틀),
 * 세기 (스킬 배율 합 = 검사 틀 × 직업 세기, 부분 합 = 배율 합), 칸 해금 레벨, 직업 위력 P (40렙 의도한 빌드 = 42), 지속 보정 (룬과 같은 단계),
 * 마검사 「마력 검술」 (칼 요구 · 칼 평타 배율 2.76 = 검사의 79%), 마검 힘 계수 1% (티르빙 2.99 = 85%). 하나라도 틀리면 종료 코드 1.
 */
public final class JobsTest {
    private JobsTest() {}

    private static final List<String> BAD = new ArrayList<>();
    private static int ok;

    private static void check(boolean cond, String what) {
        if (cond) ok++;
        else BAD.add(what);
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-6;
    }

    private static double sum(Jobs.Skill k, String key) {
        double s = 0;
        for (Object o : k.def().list(key)) s += ((Number) o).doubleValue();
        return s;
    }

    public static void main(String[] args) {
        // 모양: 직업 11 (설계 문서 순서) · 무기 11종이 하나씩 (마검사는 칼 · 마검)
        List<String> ids = List.of("swordsman", "warrior", "spellblade", "mage", "archer", "assassin", "lancer", "reaper", "paladin", "priest", "guardian");
        List<String> names = List.of("검사", "전사", "마검사", "매지션", "궁수", "암살자", "창술사", "사신", "성기사", "사제", "수호기사");
        check(Jobs.order().equals(ids), "order " + Jobs.order());
        for (int i = 0; i < ids.size(); i++) check(Jobs.job(ids.get(i)).name().equals(names.get(i)), "name " + ids.get(i));
        Set<String> firstWeapons = new HashSet<>();
        for (Jobs.Job j : Jobs.all()) {
            check(!j.weapons().isEmpty(), j.id() + " has weapons");
            for (String w : j.weapons()) check(Weapons.allTypes().contains(w), j.id() + " weapon type " + w);
            firstWeapons.add(j.weapons().get(j.id().equals("spellblade") ? 1 : 0));
            for (String s : j.stats()) check(Stats.STAT_KEYS.contains(s), j.id() + " stat " + s);
            check(j.color().length == 2 && j.color()[0].length == 3 && j.color()[1].length == 3, j.id() + " two colors");
            for (double[] c : j.color()) for (double v : c) check(v >= 0 && v <= 1, j.id() + " color range");
            check(j.trait() != null && !j.trait().name().isEmpty(), j.id() + " trait");
            check(j.skills().size() == Jobs.SLOTS, j.id() + " five skills");
            double[] cds = {6, 12, 15, 30, 60};
            for (int i = 0; i < j.skills().size(); i++) {
                Jobs.Skill k = j.skills().get(i);
                check(k.slot() == i && !k.name().isEmpty() && !k.desc().isEmpty(), j.id() + " skill " + i);
                check(k.cd() == cds[i], j.id() + " skill " + i + " cd " + k.cd());
            }
            if (j.rune() != null) check(Runes.ALL_FAMILIES.contains(j.rune()), j.id() + " rune family " + j.rune());
        }
        check(firstWeapons.size() == 11 && firstWeapons.containsAll(Weapons.allTypes()), "every weapon type has a job " + firstWeapons);
        check(Jobs.find("검사").id().equals("swordsman") && Jobs.find("spellblade").name().equals("마검사") && Jobs.find("없음") == null && Jobs.job("") == null, "find");
        check(Jobs.job("spellblade").usesWeapon("sword") && Jobs.job("spellblade").usesWeapon("greatsword") && !Jobs.job("swordsman").usesWeapon("greatsword"), "weapons");
        check(Jobs.job("spellblade").weaponText().equals("칼 · 마검") && Jobs.job("spellblade").statText().equals("마력 + 힘"), "weapon / stat text");

        // 세기: 배율 합 = 검사 틀 (5 · 3 · 9 · 태세 · 40) × 세기 (설계 문서 — 마검사 ×1.7: 8.5 · 5.1 · 15.3 · 68)
        double[] template = {5, 3, 9, 0, 40};
        for (Jobs.Job j : Jobs.all()) {
            for (int i = 0; i < Jobs.SLOTS; i++) {
                Jobs.Skill k = j.skill(i);
                if (template[i] == 0 || k.total() == 0) continue;
                // 설계 문서의 예외: 사신 혼백 폭발 (기본 8 + 혼) · 사제 성가 (회복 몫과 나눔)
                if (i == 2 && (j.id().equals("reaper") || j.id().equals("priest"))) continue;
                double want = template[i] * j.scale();
                // 스킬이 들어간 직업은 그대로, 나머지는 설계 문서처럼 소수 한 자리 (4.75 → 4.8)
                check(j.ready() ? near(k.total(), want) : Math.abs(k.total() - want) < 0.051, j.id() + " slot " + i + " total " + k.total() + " = " + template[i] + " × " + j.scale());
            }
        }
        Jobs.Job sw = Jobs.job("swordsman");
        Jobs.Job sb = Jobs.job("spellblade");
        check(sw.ready() && sb.ready() && Jobs.all().stream().filter(Jobs.Job::ready).count() == 2, "ready: swordsman · spellblade");
        Set<String> skillIds = new HashSet<>();
        for (Jobs.Job j : List.of(sw, sb)) for (Jobs.Skill k : j.skills()) check(!k.id().isEmpty() && skillIds.add(k.id()), "unique skill id " + k.id());
        // 부분 합 = 배율 합
        check(near(sum(sw.skill(0), "hits"), 5) && near(sw.skill(0).total(), 5), "연참 1.2 + 1.2 + 2.6");
        check(near(sw.skill(1).num("mult", 0), 3), "섬보 3");
        check(near(sum(sw.skill(2), "hits"), 9), "원월참 4.5 × 2");
        check(near(sw.skill(4).num("swords", 0) * sw.skill(4).num("sword", 0) + sw.skill(4).num("final", 0), 40), "천검난무 1 × 24 + 16");
        check(near(sb.skill(0).num("slash", 0) + sb.skill(0).num("burst", 0), 8.5), "비전 참격 3.8 + 4.7");
        check(near(sb.skill(1).num("blast", 0) * 2, 5.1), "마력 도약 2.55 × 2");
        check(near(sb.skill(2).num("lap", 0) * sb.skill(2).num("laps", 0), 15.3), "룬 검무 5.1 × 3");
        check(near(sum(sb.skill(4), "slashes") + sb.skill(4).num("thrust", 0), 68), "비전 거검 11 × 3 + 35");
        check(near(sb.skill(3).num("echo", 0) * sb.skill(3).num("dur", 0) / 10 / 30, 0.744), "마검 각성 0.93 × 24 / 30초 = 0.744 P/초 (검사 명경지수 × 1.7)");
        check(sw.skill(1).cooldownTicks() == 240 && sb.skill(4).cooldownTicks() == 1200, "cooldown ticks");

        // 칸 해금 레벨: 10 · 15 · 20 · 30 · 40
        int[] unlock = {10, 15, 20, 30, 40};
        for (int i = 0; i < Jobs.SLOTS; i++) {
            check(Jobs.unlockLevel(i) == unlock[i], "unlock " + i);
            check(Jobs.unlocked(i, unlock[i]) && !Jobs.unlocked(i, unlock[i] - 1), "unlocked at " + unlock[i]);
        }
        check(Jobs.unlockLevel(5) == Integer.MAX_VALUE && Jobs.slotRole(4).equals("궁극기"), "slot range · role");
        check(Jobs.changeLevel() == 10 && Jobs.changeSilver() == 300, "change level 10 · 300 silver");

        // 위력 P: 40렙 의도한 빌드 = 12 × 3.5 = 42 (모든 직업), 10렙 힘 30 = 6 × 1.6
        check(near(Jobs.power(sw, Map.of("str", 125.0, "lvmax", 40.0)), 42), "swordsman P 42");
        check(near(Jobs.power(sb, Map.of("int", 74.0, "str", 51.0, "lvmax", 40.0)), 42), "spellblade 6:4 P 42");
        check(near(Jobs.power(Jobs.job("warrior"), Map.of("str", 89.0, "vit", 36.0, "lvmax", 40.0)), 42), "warrior P 42");
        check(near(Jobs.power(sw, Map.of("str", 30.0, "dex", 99.0, "lvmax", 10.0)), 9.6), "swordsman lv10 P 9.6 (dex not counted)");
        check(near(Jobs.applied(sb, Map.of("int", 74.0, "str", 51.0, "dex", 10.0)), 125), "applied = int + str");

        // 지속 보정 (룬과 같은 단계): 연참 t 12 → +5% · 천검난무 t 78 → +10% · 룬 검무 장판 → +10% · 나머지 한방
        check("short".equals(Jobs.spreadKind(sw.skill(0))) && near(Jobs.spreadMult(sw.skill(0)), 1.05), "연참 short");
        check(Jobs.spreadKind(sw.skill(1)) == null && Jobs.spreadKind(sw.skill(2)) == null && Jobs.spreadKind(sw.skill(3)) == null, "섬보 · 원월참 · 명경지수 instant");
        check("long".equals(Jobs.spreadKind(sw.skill(4))) && near(Jobs.spreadMult(sw.skill(4)), 1.1), "천검난무 long");
        check(Jobs.spreadKind(sb.skill(0)) == null && Jobs.spreadKind(sb.skill(1)) == null, "비전 참격 · 마력 도약 instant");
        check("zone".equals(Jobs.spreadKind(sb.skill(2))) && near(Jobs.spreadMult(sb.skill(2)), 1.1), "룬 검무 zone");
        check("long".equals(Jobs.spreadKind(sb.skill(4))), "비전 거검 long");
        check(near(Jobs.totalDamage(sw.skill(0), 42), 42 * 5 * 1.05) && near(Jobs.totalDamage(sb.skill(4), 42), 42 * 68 * 1.1), "total damage = P × 배율 × 지속 보정");

        // 마검사 「마력 검술」: 칼의 힘 요구 = 힘 + 마력 (40렙 초월 칼 110 ≤ 125), 칼 평타 배율 = 1 + (힘 + 마력 × 0.5) × 2% = 2.76 (검사 3.5 의 79%)
        Map<String, Double> s64 = Map.of("int", 74.0, "str", 51.0, "lvmax", 40.0);
        Weapon sword40 = Weapons.weaponFrom("sword", 40, "transcend");
        check(sword40.req() == 110, "40렙 초월 칼 요구 힘 110 " + sword40.req());
        check(!Weapons.weaponLack(sword40, 40, s64).isEmpty(), "without the trait the 6:4 build cannot use the 40 sword");
        check(Weapons.weaponLack(sword40, 40, Jobs.reqStats(sb, "sword", s64)).isEmpty(), "magic sword: str + int fills the sword requirement");
        check(Jobs.reqStats(sw, "sword", s64) == s64 && Jobs.reqStats(sb, "greatsword", s64) == s64, "only spellblade · only swords");
        double m = Combat.statMultiplier(Jobs.damageStats(sb, "sword", s64), sword40);
        double base = Combat.statMultiplier(Map.of("str", 125.0), sword40);
        check(near(m, 2.76) && near(base, 3.5), "magic sword multiplier " + m + " (swordsman " + base + ")");
        check(Math.round(m / base * 100) == 79, "79% of swordsman");
        check(near(Combat.statMultiplier(Jobs.damageStats(sw, "sword", s64), sword40), 1 + 51 * 0.02), "swordsman unchanged (2.02 = 58%)");

        // 마검 (티르빙 포함) 힘 계수 2% → 1% (rules_more.json TYPES.greatsword): 6:4 → 1 + 74 × 2% + 51 × 1% = 2.99 (85%)
        Weapon tyr = Weapons.weaponFrom("greatsword", 40, "transcend");
        double mt = Combat.statMultiplier(Jobs.damageStats(sb, "greatsword", s64), tyr);
        check(near(mt, 2.99), "tyrfing multiplier " + mt);
        check(Math.round(mt / base * 100) == 85, "85% of swordsman");
        check(tyr.reqs().size() == 2 && tyr.reqs().get(0).value() == 74 && tyr.reqs().get(1).value() == 36, "tyrfing requirement unchanged " + tyr.reqs());

        // 글자
        check(Jobs.skillLine(sw.skill(0)).equals("연참 · 6초 · 배율 5") && Jobs.skillLine(sw.skill(3)).equals("명경지수 · 30초"), "skill line " + Jobs.skillLine(sw.skill(0)));
        check(Jobs.hex(new double[] {1, 0.5, 0}).equals("#ff8000") && Jobs.rgb(new double[] {0, 0, 1}) == 0xff, "color text");

        System.out.println("JobsTest: " + ok + " ok, " + BAD.size() + " failed");
        for (String b : BAD) System.out.println("  FAIL " + b);
        if (!BAD.isEmpty()) System.exit(1);
    }
}
