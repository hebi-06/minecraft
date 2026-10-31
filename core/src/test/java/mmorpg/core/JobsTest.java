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

    private static boolean near(double a, double b, double eps) {
        return Math.abs(a - b) < eps;
    }

    private static double sum(Jobs.Skill k, String key) {
        double s = 0;
        for (Object o : k.def().list(key)) s += ((Number) o).doubleValue();
        return s;
    }

    /**
     * 0.31.0 직업 스킬 강화: 5단계 · 단계마다 계수 +10% (5단계 ×1.5 = 룬 5돌), 필요 레벨 44 · 48 · 52 · 56 · 60, 값 (골드 · 필드 드랍 · 4 · 5단계 보스 재료 —
     * 모두 있는 아이템), 스킬마다 up 키 (스킬 정의에 있는 값 · 피해 없는 스킬은 창벽 말고 모두 up 키), 곱한 값 · bonus · 글자, 노가다 어림 (스킬 하나 골드 130 · 필드
     * 처치 880)
     */
    private static void upgrade() {
        check(Jobs.upMax() == 5 && near(Jobs.upPer(), 0.1), "upgrade 5 steps · +10%");
        double[] mult = {1, 1.1, 1.2, 1.3, 1.4, 1.5};
        for (int i = 0; i <= 5; i++) check(near(Jobs.upMult(i), mult[i]), "upMult " + i + " = " + Jobs.upMult(i));
        check(near(Jobs.upMult(-2), 1) && near(Jobs.upMult(9), 1.5) && Jobs.upClamp(7) == 5, "upMult clamps");
        check(near(Jobs.upMult(5), RuneBreak.coef(5)), "5단계 = 룬 5돌 계수 " + RuneBreak.coef(5));
        int[] levels = {44, 48, 52, 56, 60};
        int[] gold = {5, 10, 20, 35, 60};
        int goldSum = 0;
        int dropSum = 0;
        Set<String> drops = new HashSet<>();
        for (Object o : Rules.get().obj("MONSTER_DROPS").keys()) drops.add(Rules.get().obj("MONSTER_DROPS").obj(String.valueOf(o)).str("item"));
        Set<String> boss = new HashSet<>();
        for (Object o : Rules.get().obj("BOSS_MATERIALS").keys()) boss.add(Rules.get().obj("BOSS_MATERIALS").obj(String.valueOf(o)).str("item"));
        Map<String, Integer> bossNeed = new java.util.LinkedHashMap<>();
        for (int to = 1; to <= 5; to++) {
            Jobs.UpStep s = Jobs.upStep(to);
            check(s != null && s.to() == to && s.level() == levels[to - 1] && Jobs.upLevel(to) == levels[to - 1], "step " + to + " level " + (s == null ? -1 : s.level()));
            if (s == null) continue;
            check(s.gold() == gold[to - 1], "step " + to + " gold " + s.gold());
            goldSum += s.gold();
            boolean hasBoss = false;
            for (Jobs.UpItem it : s.items()) {
                check(it.count() > 0 && (drops.contains(it.id()) || boss.contains(it.id())), "step " + to + " item exists " + it.id());
                if (drops.contains(it.id())) dropSum += it.count();
                if (boss.contains(it.id())) {
                    hasBoss = true;
                    bossNeed.merge(it.id(), it.count(), Integer::sum);
                }
            }
            check(hasBoss == (to >= 4), "boss materials only at 4 · 5 (step " + to + ")");
        }
        check(Jobs.upStep(0) == null && Jobs.upStep(6) == null && Jobs.upLevel(6) == Integer.MAX_VALUE, "no step 0 · 6");
        // 노가다 어림 (스킬 하나 0 → 5): 골드 130 · 필드 전리품 440 (50% — 처치 880) · 이미르 재료 4 + 4 · 수르트 재료 3 + 6
        check(goldSum == 130 && dropSum == 440, "grind per skill: gold " + goldSum + " · drops " + dropSum);
        check(bossNeed.equals(Map.of("rpg:ymir_heart", 4, "rpg:ymir_bone", 4, "rpg:muspel_heart", 3, "rpg:laevateinn_shard", 6)), "boss materials " + bossNeed);

        // 스킬마다 up 키: 스킬 정의에 있는 값, 피해 없는 스킬 (total 0) 은 창벽 (찌르기가 위력 P) 말고 모두 up 키가 있음
        int withUp = 0;
        for (Jobs.Job j : Jobs.all()) {
            for (Jobs.Skill k : j.skills()) {
                for (Jobs.UpKey u : Jobs.upKeys(k)) {
                    check(k.def().has(u.key()) && !u.name().isEmpty() && List.of("pct", "pctp", "x", "sec").contains(u.fmt()), k.id() + " up key " + u.key());
                }
                if (!Jobs.upKeys(k).isEmpty()) withUp++;
                if (k.total() <= 0 && Jobs.upKeys(k).isEmpty()) check(k.id().equals("lancer_wall"), k.id() + " has no damage and no up key");
                check(!Jobs.upLines(k, 0, 1).isEmpty(), k.id() + " shows what an upgrade does");
            }
        }
        check(withUp == 18, "18 skills with up keys " + withUp);

        Jobs.Job sw = Jobs.job("swordsman");
        Jobs.Skill mirror = sw.skill("swordsman_mirror");
        check(near(Jobs.upValue(mirror, "echo", 0.35, 5), 0.525) && near(Jobs.upValue(mirror, "echo", 0.35, 0), 0.35), "mirror echo ×1.5");
        check(near(Jobs.upValue(mirror, "guard", 0.15, 5), 0.15), "guard is not an up key");
        Jobs.Skill veil = Jobs.job("assassin").skill("assassin_veil");
        check(near(Jobs.upValue(veil, "first", 2.5, 5), 3.25) && near(Jobs.upValue(veil, "first", 2.5, 1), 2.65), "veil first: bonus only (1 + 1.5 × m)");
        Jobs.Skill heal = Jobs.job("priest").skill("priest_heal");
        check(near(Jobs.upValue(heal, "heal", 0.12, 3), 0.156), "priest heal +30%");
        Jobs.Skill oath = Jobs.job("paladin").skill("paladin_oath");
        check(near(Jobs.upValue(oath, "dur", 200, 5), 300) && near(Jobs.upValue(oath, "aura", 0.15, 5), 0.15), "oath duration ×1.5 · aura unchanged");

        // 글자
        check(Jobs.upLines(sw.skill(0), 2, 3).equals(List.of("위력 ×1.2 → ×1.3")), "flurry lines " + Jobs.upLines(sw.skill(0), 2, 3));
        check(Jobs.upLines(mirror, 0, 1).equals(List.of("두 번째 베기 35% → 38.5%")), "mirror lines " + Jobs.upLines(mirror, 0, 1));
        check(Jobs.upLines(veil, 0, 1).equals(List.of("첫 공격 ×2.5 → ×2.65")), "veil lines " + Jobs.upLines(veil, 0, 1));
        check(Jobs.upLines(oath, 0, 1).equals(List.of("지속 10초 → 11초")), "oath lines " + Jobs.upLines(oath, 0, 1));
        check(Jobs.upLines(Jobs.job("archer").skill("archer_focus"), 0, 1).equals(List.of("치명타 +20%p → +22%p")), "focus lines");
        check(Jobs.upLines(Jobs.job("priest").skill("priest_hymn"), 0, 1).equals(List.of("위력 ×1 → ×1.1", "회복 (0.5초마다) 1.5% → 1.65%")),
                "hymn lines " + Jobs.upLines(Jobs.job("priest").skill("priest_hymn"), 0, 1));
        check(Jobs.upLines(Jobs.job("lancer").skill("lancer_wall"), 0, 1).equals(List.of("위력 ×1 → ×1.1")), "wall lines (power only)");
        check(Jobs.upNow(mirror, 2).equals(List.of("두 번째 베기 42%")) && Jobs.upNow(sw.skill(4), 5).equals(List.of("위력 ×1.5")), "now lines");
        check(Jobs.upStars(3).equals("★★★☆☆") && Jobs.upStars(0).equals("☆☆☆☆☆") && Jobs.upStars(9).equals("★★★★★"), "stars");
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
        Jobs.Job wa = Jobs.job("warrior");
        Jobs.Job mg = Jobs.job("mage");
        Jobs.Job ar = Jobs.job("archer");
        Jobs.Job as = Jobs.job("assassin");
        Jobs.Job la = Jobs.job("lancer");
        Jobs.Job re = Jobs.job("reaper");
        Jobs.Job pa = Jobs.job("paladin");
        Jobs.Job pr = Jobs.job("priest");
        Jobs.Job gu = Jobs.job("guardian");
        check(Jobs.all().stream().allMatch(Jobs.Job::ready) && Jobs.all().size() == 11,
                "ready: all 11 (0.28.0 검사 · 마검사 · 전사 · 매지션 · 궁수 · 0.29.0 암살자 · 창술사 · 사신 · 0.30.0 성기사 · 사제 · 수호기사)");
        Set<String> skillIds = new HashSet<>();
        for (Jobs.Job j : Jobs.all()) {
            for (Jobs.Skill k : j.skills()) check(!k.id().isEmpty() && k.id().startsWith(j.id() + "_") && skillIds.add(k.id()), "unique skill id " + k.id());
        }
        // 부분 합 = 배율 합
        check(near(sum(sw.skill(0), "hits"), 5) && near(sw.skill(0).total(), 5), "연참 1.2 + 1.2 + 2.6");
        check(near(sw.skill(1).num("mult", 0), 3), "섬보 3");
        check(near(sum(sw.skill(2), "hits"), 9), "원월참 4.5 × 2");
        check(near(sw.skill(4).num("swords", 0) * sw.skill(4).num("sword", 0) + sw.skill(4).num("final", 0), 40), "천검난무 1 × 24 + 16");
        check(near(sb.skill(0).num("slash", 0) + sb.skill(0).num("burst", 0), 8.5), "비전 참격 3.8 + 4.7");
        check(near(sb.skill(1).num("path", 0) + sb.skill(1).num("blast", 0), 5.1), "마력 도약 2.55 + 2.55");
        check(near(sw.skill(4).num("swords", 0) / sw.skill(4).num("volleys", 1), 4), "천검난무 4자루씩");
        // 같은 적을 여러 번 치는 스킬은 무적 시간 (10틱) 만큼 띄움
        check(sw.skill(0).num("gap", 0) >= 10 && sw.skill(2).num("gap", 0) >= 10 && sw.skill(4).num("every", 0) >= 10 && sb.skill(0).num("delay", 0) >= 10
                && sb.skill(1).num("delay", 0) >= 10 && sb.skill(2).num("gap", 0) >= 10 && sb.skill(4).num("gap", 0) >= 10 && sb.skill(4).num("after", 0) >= 10, "i-frame spacing");
        // 천검난무: 마지막 꽂힘 (검이 날아가는 데 6틱까지) 과 일섬 사이도 10틱 넘게
        check(sw.skill(4).num("gather", 0) >= 16, "천검난무 모임 16틱 (날아감 6 + 무적 10)");
        check(near(sb.skill(2).num("lap", 0) * sb.skill(2).num("laps", 0), 15.3), "룬 검무 5.1 × 3");
        check(near(sum(sb.skill(4), "slashes") + sb.skill(4).num("thrust", 0), 68), "비전 거검 11 × 3 + 35");
        check(near(sb.skill(3).num("echo", 0) * sb.skill(3).num("dur", 0) / 10 / 30, 0.744), "마검 각성 0.93 × 24 / 30초 = 0.744 P/초 (검사 명경지수 × 1.7)");
        check(sw.skill(1).cooldownTicks() == 240 && sb.skill(4).cooldownTicks() == 1200, "cooldown ticks");

        // 0.28.0 전사 · 매지션 · 궁수: 부분 합 = 배율 합
        check(near(wa.skill(0).num("mult", 0), 6.5) && near(wa.skill(1).num("ram", 0) + wa.skill(1).num("slam", 0), 3.9), "파쇄격 6.5 · 돌격 1.9 + 2.0");
        check(near(wa.skill(2).num("hit", 0) * wa.skill(2).num("hits", 0), 11.7), "대회전 2.34 × 5");
        check(near(wa.skill(4).num("leap", 0) * wa.skill(4).num("leaps", 0) + wa.skill(4).num("final", 0), 52), "격노 도약 7 × 4 + 24");
        check(near(mg.skill(0).num("bolt", 0) * mg.skill(0).num("bolts", 0), 7) && near(mg.skill(1).num("blast", 0), 4.2), "마탄 연사 1.4 × 5 · 위상 전환 4.2");
        check(near(mg.skill(2).num("pulse", 0) * mg.skill(2).num("pulses", 0) + mg.skill(2).num("burst", 0), 12.6), "비전 구체 1.4 × 6 + 4.2");
        check(near(mg.skill(4).num("page", 0) * mg.skill(4).num("pages", 0) + mg.skill(4).num("final", 0), 56)
                && near(mg.skill(4).num("pages", 0) % mg.skill(4).num("waves", 1), 0), "금서 개방 3 × 12 + 20 (물결마다 같은 장 수)");
        check(near(ar.skill(0).num("arrow", 0) * ar.skill(0).num("arrows", 0), 7.5) && ar.skill(0).num("more", 0) == 4, "속사 2.5 × 3 (호흡 3 이상 4발)");
        check(near(ar.skill(2).num("wave", 0) * ar.skill(2).num("waves", 0), 13.5) && ar.skill(2).num("per", 0) == 2, "화살비 2.7 × 5 (두 발씩)");
        check(near(ar.skill(4).num("shot", 0) + ar.skill(4).num("after", 0), 60), "관천일시 40 + 20");
        // 같은 적을 여러 번 치는 것은 10틱 넘게 (빠른 연타는 한 번에 넣고 숫자만 여러 줄 — 마탄 연사 · 속사 · 화살비 한 물결 · 금서의 주문)
        check(wa.skill(2).num("gap", 0) >= 10 && wa.skill(4).num("leapTicks", 0) >= 10 && wa.skill(4).num("finalDelay", 0) >= 10, "warrior i-frame spacing");
        check(mg.skill(2).num("gap", 0) >= 10 && mg.skill(2).num("dur", 0) - mg.skill(2).num("gap", 0) * mg.skill(2).num("pulses", 0) >= 10
                && mg.skill(4).num("gap", 0) >= 10 && mg.skill(4).num("finalDelay", 0) >= 10, "mage i-frame spacing");
        check(ar.skill(2).num("gap", 0) >= 10 && ar.skill(4).num("burstDelay", 0) >= 10, "archer i-frame spacing");
        // 지속 보정: 대회전 t 40 → short · 격노 도약 · 비전 구체 · 관천일시 → long · 화살비 · 금서 개방 (장판) → zone, 나머지 한방
        check(Jobs.spreadKind(wa.skill(0)) == null && Jobs.spreadKind(wa.skill(1)) == null && "short".equals(Jobs.spreadKind(wa.skill(2)))
                && "long".equals(Jobs.spreadKind(wa.skill(4))), "warrior spread");
        check(Jobs.spreadKind(mg.skill(0)) == null && Jobs.spreadKind(mg.skill(1)) == null && "long".equals(Jobs.spreadKind(mg.skill(2)))
                && "zone".equals(Jobs.spreadKind(mg.skill(4))), "mage spread");
        check(Jobs.spreadKind(ar.skill(0)) == null && "zone".equals(Jobs.spreadKind(ar.skill(2))) && "long".equals(Jobs.spreadKind(ar.skill(4))), "archer spread");
        // 0.29.0 암살자 · 창술사 · 사신: 부분 합 = 배율 합 (설계 문서의 소수 한 자리 대신 틀 × 세기 그대로 — 4.75 · 2.85 · 8.55 / 7.25 · 4.35 · 13.05 / 6.25 · 3.75)
        check(near(as.skill(0).num("base", 0) + as.skill(0).num("per", 0) * as.trait().max(), 4.75), "낙화 0.5 + 0.85 × 표식 5");
        check(near(as.skill(1).num("blast", 0), 2.85) && near(as.skill(2).num("out", 0) + as.skill(2).num("back", 0), 8.55), "잔영 2.85 · 귀환 비수 4.275 × 2");
        check(near(as.skill(4).num("slash", 0) * as.skill(4).num("slashes", 0) + as.skill(4).num("final", 0), 38), "흑야 3 × 6 + 20");
        check(near(sum(la.skill(0), "hits"), 7.25) && la.skill(0).def().list("hits").size() == 3, "삼연창 2 + 2 + 3.25");
        check(near(la.skill(1).num("slam", 0), 4.35) && near(la.skill(2).num("spin", 0) * la.skill(2).num("spins", 0), 13.05), "장대 도약 4.35 · 선회창 6.525 × 2");
        check(near(la.skill(4).num("spear", 0) * la.skill(4).num("spears", 0) + la.skill(4).num("final", 0), 58)
                && near(la.skill(4).num("spears", 0) % la.skill(4).num("waves", 1), 0), "천창유성 2 × 20 + 18 (물결마다 같은 수)");
        check(near(la.skill(3).num("thrust", 0) * la.skill(3).num("dur", 0) / la.skill(3).num("every", 1) / 30, 0.5867, 1e-3), "창벽 1.1 × 16 / 30초 ≈ 0.58 P/초 (검사 태세 0.4 × 1.45)");
        check(near(re.skill(0).num("swing", 0) * re.skill(0).num("swings", 0), 6.25) && near(re.skill(1).num("hit", 0), 3.75), "갈퀴 베기 3.125 × 2 · 망령 걸음 3.75");
        check(near(re.skill(2).num("base", 0) * (1 + re.skill(2).num("per", 0) * 8), 11.2) && near(re.skill(2).num("base", 0) * (1 + re.skill(2).num("per", 0) * 10), 12),
                "혼백 폭발 8 × (1 + 5% × 혼) — 혼 8 = 11.2 · 10 = 12");
        check(near(re.skill(4).num("drain", 0) * re.skill(4).num("drains", 0) + re.skill(4).num("final", 0), 50), "황천문 3 × 6 + 32");
        // 같은 적을 여러 번 치는 것은 10틱 넘게 (흑야는 두 번 벨 때마다 한 번에 — every × 2, 비수가 돌아오는 길은 JobKit 이 무적 시간 뒤로 미룸)
        check(as.skill(4).num("every", 0) * 2 >= 10 && as.skill(4).num("finalDelay", 0) >= 10 && as.skill(2).num("hold", 0) + as.skill(2).num("flight", 0) >= 10,
                "assassin i-frame spacing");
        check(la.skill(0).num("gap", 0) * 2 >= 10 && la.skill(2).num("gap", 0) >= 10 && la.skill(3).num("every", 0) >= 10 && la.skill(4).num("gap", 0) >= 10
                && la.skill(4).num("finalDelay", 0) >= 10, "lancer i-frame spacing");
        check(re.skill(0).num("gap", 0) >= 10 && re.skill(4).num("gap", 0) >= 10 && re.skill(4).num("finalDelay", 0) >= 10, "reaper i-frame spacing");
        // 지속 보정: 잔영 (2초 안에 터짐) · 귀환 비수 · 흑야 → short, 천창유성 · 황천문 (장판) → zone, 나머지 한방
        check(Jobs.spreadKind(as.skill(0)) == null && "short".equals(Jobs.spreadKind(as.skill(1))) && "short".equals(Jobs.spreadKind(as.skill(2)))
                && "short".equals(Jobs.spreadKind(as.skill(4))), "assassin spread");
        check(Jobs.spreadKind(la.skill(0)) == null && Jobs.spreadKind(la.skill(1)) == null && Jobs.spreadKind(la.skill(2)) == null
                && "zone".equals(Jobs.spreadKind(la.skill(4))), "lancer spread");
        check(Jobs.spreadKind(re.skill(0)) == null && Jobs.spreadKind(re.skill(1)) == null && Jobs.spreadKind(re.skill(2)) == null
                && "zone".equals(Jobs.spreadKind(re.skill(4))), "reaper spread");
        check(as.trait().max() == 5 && near(as.trait().def().num("back"), 0.25) && as.trait().def().num("backArc") == 120, "표식 5 · 등 뒤 +25%p · 120°");
        check(la.trait().max() == 1 && near(la.trait().def().num("edgeBonus"), 0.15) && near(la.trait().def().num("momentum"), 0.1), "창끝 +15% · 다음 스킬 +10%");
        check(re.trait().max() == 10 && re.trait().def().num("kill") == 3 && near(re.trait().def().num("speedPer"), 0.01) && near(re.trait().def().num("leechPer"), 0.001),
                "혼 10 · 처치 +3 · 이동 1% · 흡혈 0.1%");
        check(near(Jobs.power(as, Map.of("dex", 125.0, "lvmax", 40.0)), 42) && near(Jobs.power(la, Map.of("str", 89.0, "dex", 36.0, "lvmax", 40.0)), 42)
                && near(Jobs.power(re, Map.of("dex", 89.0, "int", 36.0, "lvmax", 40.0)), 42), "assassin · lancer · reaper P 42");
        // 0.30.0 성기사 · 사제 · 수호기사: 부분 합 = 배율 합 (성기사 ×1.1 · 사제 ×0.6 · 수호기사 ×0.8)
        check(near(pa.skill(0).num("wave", 0) + pa.skill(0).num("bolt", 0), 5.5) && near(pa.skill(1).num("ram", 0), 3.3), "뇌격 강타 3 + 2.5 · 신념의 돌진 3.3");
        check(near(pa.skill(2).num("ring", 0) * pa.skill(2).def().list("radii").size(), 9.9), "뇌진 3.3 × 고리 3");
        check(near(sum(pa.skill(4), "hits"), 44) && pa.skill(4).def().list("hits").size() == 3, "천뢰의 대망치 10 + 10 + 24");
        check(pr.skill(0).total() == 0 && pr.skill(1).total() == 0 && pr.skill(3).total() == 0, "치유의 손길 · 빛의 걸음 · 성흔 피해 없음");
        check(near(pr.skill(2).num("pulse", 0) * pr.skill(2).num("pulses", 0), 5.6) && near(pr.skill(2).num("heal", 0) * pr.skill(2).num("pulses", 0), 0.12),
                "성가 0.7 × 8 · 회복 1.5% × 8 = 12%");
        check(near(pr.skill(4).num("ring", 0) * pr.skill(4).num("rings", 0), 24) && near(pr.skill(4).num("heal", 0) * pr.skill(4).num("rings", 0), 0.45),
                "새벽의 종 8 × 3 · 회복 15% × 3");
        check(near(gu.skill(0).num("mult", 0), 4) && near(gu.skill(1).num("hit", 0), 2.4) && near(gu.skill(2).num("hit", 0), 7.2) && near(gu.skill(4).num("final", 0), 32),
                "방패 치기 4 · 수호 돌입 2.4 · 방패 투척 7.2 (적마다 한 번) · 성채 32");
        // 같은 적을 여러 번 치는 것은 10틱 넘게 (벼락은 충격파 10틱 뒤 · 고리 · 대망치 · 성가 · 종은 10틱 넘게)
        check(pa.skill(0).num("delay", 0) >= 10 && pa.skill(2).num("gap", 0) >= 10 && pa.skill(4).num("gap", 0) >= 10, "paladin i-frame spacing");
        check(pr.skill(2).num("gap", 0) >= 10 && pr.skill(4).num("gap", 0) >= 10, "priest i-frame spacing");
        // 수호 돌입 · 방패 치기 · 투척 · 성채는 같은 적을 한 번만
        check(near(pa.skill(4).num("first", 0) + pa.skill(4).num("gap", 0) * 2, pa.skill(4).num("t", 0)) && near(pr.skill(4).num("first", 0) + pr.skill(4).num("gap", 0) * 2,
                pr.skill(4).num("t", 0)), "궁극기 t = 첫 타 + 간격 × 2");
        // 지속 보정: 벼락 강타 (0.50.0 뛰어올라 내려침 — 벼락까지 16틱) · 뇌진 · 방패 투척 → short, 천뢰의 대망치 · 새벽의 종 → long, 성가 → zone, 성채 (8초 뒤 무너짐) → zoneLong, 나머지 한방
        check("short".equals(Jobs.spreadKind(pa.skill(0))) && Jobs.spreadKind(pa.skill(1)) == null && "short".equals(Jobs.spreadKind(pa.skill(2)))
                && "long".equals(Jobs.spreadKind(pa.skill(4))), "paladin spread");
        check("zone".equals(Jobs.spreadKind(pr.skill(2))) && "long".equals(Jobs.spreadKind(pr.skill(4))), "priest spread");
        // 0.50.0 직업 시간 시험: 파수꾼의 방패는 한 적에 한 번에 들어감 (튕겨 가는 것은 다른 적 — 룬과 같이 한 적 기준) → 한방
        check(Jobs.spreadKind(gu.skill(0)) == null && Jobs.spreadKind(gu.skill(1)) == null && Jobs.spreadKind(gu.skill(2)) == null
                && "zoneLong".equals(Jobs.spreadKind(gu.skill(4))) && near(Jobs.spreadMult(gu.skill(4)), 1.15), "guardian spread");
        check(pa.trait().max() == 5 && pa.trait().def().num("judgeFor") == 200 && pa.trait().def().num("stun") == 20 && near(pa.trait().def().num("aura"), 0.05)
                && pa.trait().def().num("auraRadius") == 6 && near(pa.trait().def().num("bossTaken"), 0.08), "심판 5 · 10초 · 기절 1초 · 오라 5% · 6칸 · 보스 +8%");
        check(near(pa.skill(3).num("aura", 0), 0.15) && near(pa.skill(3).num("regen", 0), 0.01) && pa.skill(3).num("dur", 0) == 200, "수호의 맹세 15% · 1%/초 · 10초");
        check(pr.trait().max() == 5 && near(pr.trait().def().num("per"), 0.1) && pr.trait().def().num("overflowRadius") == 4, "신앙 5 · +10% · 넘친 흡혈 4칸");
        check(gu.trait().max() == 100 && near(gu.trait().def().num("rate"), 0.3) && near(gu.trait().def().num("cap"), 0.5), "반격 30% · 최대 체력 50% 까지");
        check(near(gu.skill(1).num("share", 0), 0.4) && near(gu.skill(1).num("cut", 0), 0.3) && near(gu.skill(4).num("guard", 0), 0.35)
                && near(gu.skill(3).num("front", 0), 1) && near(gu.skill(3).num("side", 0), 0.6), "수호 돌입 40% (−30%) · 성채 −35% · 철옹성 100% · 60%");
        check(near(Jobs.power(pa, Map.of("vit", 89.0, "str", 36.0, "lvmax", 40.0)), 42) && near(Jobs.power(pr, Map.of("int", 89.0, "vit", 36.0, "lvmax", 40.0)), 42)
                && near(Jobs.power(gu, Map.of("vit", 125.0, "lvmax", 40.0)), 42), "paladin · priest · guardian P 42");
        // 특성 수치
        check(wa.trait().max() == 100 && near(wa.trait().def().num("halfBonus"), 0.1) && near(wa.trait().def().num("fullBonus"), 0.2), "분노 0 ~ 100 · +10% · +20%");
        check(mg.trait().max() == 3 && near(mg.trait().def().num("perChain"), 0.12) && mg.trait().def().num("window") == 80, "주문 연쇄 3 · 12% · 4초");
        check(ar.trait().max() == 5 && near(ar.trait().def().num("perStack"), 0.04) && ar.trait().def().num("rangePer") == 1, "호흡 5 · 4% · 1칸");
        check(near(Jobs.power(mg, Map.of("int", 125.0, "lvmax", 40.0)), 42) && near(Jobs.power(ar, Map.of("dex", 125.0, "lvmax", 40.0)), 42), "mage · archer P 42");

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
        // 0.50.0 피해 배율 = (1 − 유틸) × 시간 보정 (선딜 · 첫 피해부터의 지속) × 직업 맞춤 (직업의 초당 피해 그대로)
        check(near(Jobs.totalDamage(sw.skill(0), 42), 42 * 5 * Jobs.damageScale(sw.skill(0))) && near(Jobs.totalDamage(sb.skill(4), 42), 42 * 68 * Jobs.damageScale(sb.skill(4))),
                "total damage = P × 배율 × 피해 배율");
        jobScale(sw, sb, pa, pr, gu);

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
        check(Jobs.skillLine(sw.skill(0)).equals("은빛 연격 · 6초 · 배율 5.21") && Jobs.skillLine(sw.skill(3)).equals("에인헤랴르 · 30초"), "skill line " + Jobs.skillLine(sw.skill(0)));
        check(Jobs.hex(new double[] {1, 0.5, 0}).equals("#ff8000") && Jobs.rgb(new double[] {0, 0, 1}) == 0xff, "color text");

        upgrade();

        System.out.println("JobsTest: " + ok + " ok, " + BAD.size() + " failed");
        for (String b : BAD) System.out.println("  FAIL " + b);
        if (!BAD.isEmpty()) System.exit(1);
    }

    /** 0.50.0 직업 스킬 재설계: 유틸 값 · 시간 보정 (룬과 같은 표) · 직업 맞춤 */
    static void jobScale(Jobs.Job sw, Jobs.Job sb, Jobs.Job pa, Jobs.Job pr, Jobs.Job gu) {
        Obj utils = Rules.get().obj("RUNE_SPREAD").obj("utils");
        int names = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Jobs.Job j : Jobs.all()) {
            double was = 0, now = 0;
            for (Jobs.Skill k : j.skills()) {
                check(seen.add(k.name()), "skill name unique " + k.name());
                names++;
                for (String u : Jobs.utils(k)) check(utils.has(u), k.id() + " util " + u + " known");
                check(Jobs.utilCost(k) <= 0.6 + 1e-9, k.id() + " util ≤ 60%");
                double tm = Jobs.timeMult(k);
                check(tm >= 1 - 1e-9 && tm <= 1.3 + 1e-9, k.id() + " time mult in [1, 1.3] " + tm);
                if (k.total() <= 0) {
                    check(near(Jobs.damageScale(k), 1) && Jobs.scaleNote(k) == null, k.id() + " no damage → scale 1");
                    continue;
                }
                check(k.def().has("t"), k.id() + " damage skill has t");
                check(near(Jobs.damageScale(k), (1 - Jobs.utilCost(k)) * tm * Jobs.jobNorm(j)), k.id() + " scale = util × time × norm");
                check(Jobs.jobOf(k) == j, k.id() + " job");
                was += k.total() * Jobs.spreadMult(k) / k.cd();
                now += k.total() * Jobs.damageScale(k) / k.cd();
            }
            check(near(was, now), j.id() + " job damage per second kept " + was + " → " + now);
            double n = Jobs.jobNorm(j);
            check(n > 0.9 && n < 1.6, j.id() + " norm " + n);
        }
        check(names == 55, "55 skill names " + names);
        // 하나씩: 발할라 (선딜 19틱 — 0.50.0 시간 시험: 솟아올라 첫 일제 사격 — +19% · 지속 63틱 +10% → 1.309 → 상한 ×1.3 · 유틸 없음)
        Jobs.Skill valhalla = sw.skill(4);
        check(valhalla.name().equals("발할라") && Jobs.windupTicks(valhalla) == 19 && "long".equals(Jobs.timeKind(valhalla)), "발할라 windup · long");
        check(near(Jobs.timeMult(valhalla), 1.3) && Jobs.timeCapped(valhalla) && near(Jobs.utilCost(valhalla), 0), "발할라 time 1.3 (cap)");
        // 섬광 질주: 돌진 −10% · 다음 평타 치명 (피해 — 값 없음)
        check(Jobs.utils(sw.skill(1)).equals(List.of("blink", "crit")) && near(Jobs.utilCost(sw.skill(1)), 0.10), "섬광 질주 util 10%");
        // 만월 베기: 발키리의 가호 (가하는 피해 +10%) −15%
        check(Jobs.utils(sw.skill(2)).equals(List.of("buff10")) && near(Jobs.utilCost(sw.skill(2)), 0.15), "만월 베기 buff 15%");
        // 천둥 수레: 돌진 · 기절 0.5초 · 보호막 −30% / 토르의 진노: 기절 1.5초 · 아군 회복 −35% · 선딜 1초
        check(near(Jobs.utilCost(pa.skill(1)), 0.30) && near(Jobs.utilCost(pa.skill(4)), 0.35) && Jobs.windupTicks(pa.skill(4)) == 20, "paladin utils");
        // 아스가르드 성벽: 8초 뒤 무너짐 → 선딜 +30% (상한) · 지속 0 → 장판 +10% → 상한 ×1.3
        Jobs.Skill wall = gu.skill(4);
        check(Jobs.windupTicks(wall) == 160 && near(Jobs.windupBonus(wall), 0.3) && "zone".equals(Jobs.timeKind(wall)) && near(Jobs.timeMult(wall), 1.3)
                && Jobs.timeCapped(wall), "성채 windup cap");
        // 직업 맞춤: 사제는 피해 스킬 둘 다 유틸이 무거워 ×1.48
        check(near(Math.round(Jobs.jobNorm(pr) * 100) / 100.0, 1.48), "priest norm 1.48 " + Jobs.jobNorm(pr));
        String note = Jobs.scaleNote(pr.skill(2));
        check(note != null && note.contains("유틸 −25%") && note.contains("시간 +10%") && note.contains("직업 맞춤 ×1.48"), "스칼드의 노래 note " + note);
        check(Jobs.skillLine(sw.skill(0)).startsWith("은빛 연격 · 6초 · 배율 "), "skill line " + Jobs.skillLine(sw.skill(0)));
        check(sw.trait().name().equals("투지") && Jobs.job("reaper").trait().name().equals("영혼"), "trait names 투지 · 영혼");
        check(near(Jobs.utilCost(pr.skill(0)), 0) && near(Jobs.damageScale(pr.skill(0)), 1), "heal (no damage) scale 1");
        check(Jobs.utilName("stun07").equals("기절 0.7초") && near(utils.obj("undying").num("cost"), 0.2), "job utils in the table");
        check(sb.skill(2).name().equals("푸타르크 검무") && near(Jobs.utilCost(sb.skill(2)), 0.10), "푸타르크 검무 룬 결계 10%");
    }
}
