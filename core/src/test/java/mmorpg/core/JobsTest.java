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
        check(near(sum(sw.skill(4), "beats") + sw.skill(4).num("final", 0), 40) && sw.skill(4).def().list("beats").size() == 4, "라그나로크 5 × 4 + 20");
        check(near(sb.skill(0).num("slash", 0) + sb.skill(0).num("burst", 0), 8.5), "비전 참격 3.8 + 4.7");
        check(near(sb.skill(1).num("path", 0) + sb.skill(1).num("blast", 0), 5.1), "마력 도약 2.55 + 2.55");
        // 같은 적을 여러 번 치는 스킬은 무적 시간 (10틱) 만큼 띄움
        check(sw.skill(0).num("gap", 0) >= 10 && sw.skill(2).num("gap", 0) >= 10 && sw.skill(4).num("every", 0) >= 10 && sb.skill(0).num("delay", 0) >= 10
                && sb.skill(1).num("delay", 0) >= 10 && sb.skill(2).num("gap", 0) >= 10 && sb.skill(4).num("gap", 0) >= 10 && sb.skill(4).num("after", 0) >= 10, "i-frame spacing");
        // 라그나로크: 마지막 베기와 내리꽂음 사이도 10틱 넘게 (뛰어오름은 그 안 — 내리꽂는 틱에 땅)
        check(sw.skill(4).num("finalDelay", 0) >= 10 && sw.skill(4).num("hopTicks", 0) < sw.skill(4).num("finalDelay", 0), "라그나로크 내리꽂음 14틱 뒤");
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
        check(near(as.skill(1).num("blast", 0), 2.85) && near(as.skill(2).num("cut", 0) + as.skill(2).num("venom", 0) * as.skill(2).num("ticks", 0), 8.55),
                "잔영 2.85 · 요르문간드의 독니 4.55 + 1 × 4");
        check(near(as.skill(4).num("slash", 0) * as.skill(4).num("slashes", 0) + as.skill(4).num("final", 0), 38), "흑야 3 × 6 + 20");
        check(near(sum(la.skill(0), "hits"), 7.25) && la.skill(0).def().list("hits").size() == 3, "삼연창 2 + 2 + 3.25");
        check(near(la.skill(1).num("slam", 0), 4.35) && near(la.skill(2).num("spin", 0) * la.skill(2).num("spins", 0), 13.05), "장대 도약 4.35 · 선회창 6.525 × 2");
        check(near(la.skill(4).num("thrust", 0) * la.skill(4).num("thrusts", 0) * la.skill(4).num("waves", 0) + la.skill(4).num("final", 0), 58),
                "흐레스벨그의 발톱 1.75 × 4 × 4 + 30");
        check(near(la.skill(3).num("thrust", 0) * la.skill(3).num("dur", 0) / la.skill(3).num("every", 1) / 30, 0.5867, 1e-3), "창벽 1.1 × 16 / 30초 ≈ 0.58 P/초 (검사 태세 0.4 × 1.45)");
        check(near(re.skill(0).num("swing", 0) * re.skill(0).num("swings", 0), 6.25) && near(re.skill(1).num("hit", 0), 3.75), "갈퀴 베기 3.125 × 2 · 망령 걸음 3.75");
        check(near(re.skill(2).num("base", 0) * (1 + re.skill(2).num("per", 0) * 8), 11.2) && near(re.skill(2).num("base", 0) * (1 + re.skill(2).num("per", 0) * 10), 12),
                "혼백 폭발 8 × (1 + 5% × 혼) — 혼 8 = 11.2 · 10 = 12");
        check(near(re.skill(4).num("drain", 0) * re.skill(4).num("drains", 0) + re.skill(4).num("final", 0), 50), "황천문 3 × 6 + 32");
        // 같은 적을 여러 번 치는 것은 10틱 넘게 (흑야는 두 번 벨 때마다 한 번에 — every × 2, 독니의 독은 벤 뒤 every 틱마다)
        check(as.skill(4).num("every", 0) * 2 >= 10 && as.skill(4).num("finalDelay", 0) >= 10 && as.skill(2).num("every", 0) >= 10, "assassin i-frame spacing");
        check(la.skill(0).num("gap", 0) * 2 >= 10 && la.skill(2).num("gap", 0) >= 10 && la.skill(3).num("every", 0) >= 10 && la.skill(4).num("gap", 0) >= 10
                && la.skill(4).num("finalDelay", 0) >= 10, "lancer i-frame spacing");
        // 흐레스벨그의 발톱: 뒤로 뜀 (4틱) · 돌진 (dashTicks) 이 마지막 물결과 꿰뚫음 사이 (finalDelay) 안 — 꿰뚫는 틱 = t
        check(la.skill(4).num("dashTicks", 0) + 6 <= la.skill(4).num("finalDelay", 0)
                && near(la.skill(4).num("first", 0) + (la.skill(4).num("waves", 0) - 1) * la.skill(4).num("gap", 0) + la.skill(4).num("finalDelay", 0), la.skill(4).num("t", 0))
                && near(la.skill(4).num("first", 0), la.skill(4).num("w", 0)), "흐레스벨그의 발톱 timeline (first = w · last = t)");
        check(re.skill(0).num("gap", 0) >= 10 && re.skill(4).num("gap", 0) >= 10 && re.skill(4).num("finalDelay", 0) >= 10, "reaper i-frame spacing");
        // 지속 보정: 잔영 (2초 안에 터짐) · 흑야 → short, 요르문간드의 독니 (독이 2초) · 흐레스벨그의 발톱 (2.5초) → long, 황천문 (장판) → zone, 나머지 한방
        check(Jobs.spreadKind(as.skill(0)) == null && "short".equals(Jobs.spreadKind(as.skill(1))) && "long".equals(Jobs.spreadKind(as.skill(2)))
                && "short".equals(Jobs.spreadKind(as.skill(4))), "assassin spread");
        check(Jobs.spreadKind(la.skill(0)) == null && Jobs.spreadKind(la.skill(1)) == null && Jobs.spreadKind(la.skill(2)) == null
                && "long".equals(Jobs.spreadKind(la.skill(4))), "lancer spread");
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

        // 지속 보정 (룬과 같은 단계): 연참 t 12 → +5% · 라그나로크 t 50 → +10% · 룬 검무 장판 → +10% · 나머지 한방
        check("short".equals(Jobs.spreadKind(sw.skill(0))) && near(Jobs.spreadMult(sw.skill(0)), 1.05), "연참 short");
        check(Jobs.spreadKind(sw.skill(1)) == null && Jobs.spreadKind(sw.skill(2)) == null && Jobs.spreadKind(sw.skill(3)) == null, "섬보 · 원월참 · 명경지수 instant");
        check("long".equals(Jobs.spreadKind(sw.skill(4))) && near(Jobs.spreadMult(sw.skill(4)), 1.1), "라그나로크 long");
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
        check(Jobs.skillLine(sw.skill(0)).equals("트리니티 엣지 · 6초 · 배율 5.84") && Jobs.skillLine(sw.skill(3)).equals("미러 블레이드 · 30초"), "skill line " + Jobs.skillLine(sw.skill(0)));
        check(Jobs.hex(new double[] {1, 0.5, 0}).equals("#ff8000") && Jobs.rgb(new double[] {0, 0, 1}) == 0xff, "color text");

        upgrade();
        advance();

        System.out.println("JobsTest: " + ok + " ok, " + BAD.size() + " failed");
        for (String b : BAD) System.out.println("  FAIL " + b);
        if (!BAD.isEmpty()) System.exit(1);
    }

    /** 직업 초당 피해 (피해 스킬의 배율 합 × 피해 배율 ÷ 재사용 대기 — 범위 보정은 밖이라 뺌) */
    static double dps(Jobs.Job j) {
        double sum = 0;
        for (Jobs.Skill k : j.skills()) if (k.total() > 0 && k.cd() > 0) sum += k.total() * Jobs.damageScale(k) / Jobs.areaMult(k) / k.cd();
        return sum;
    }

    /**
     * 0.51.0 2차 전직 (jobs.json advance — claude/job-advance.md): 검사 · 전사의 각성 · 반전 — 모양 · 이름 · 칸 · 강화 열쇠 · 세기 (1차 × 1.2) · 무적 시간 띄움 ·
     * 시각 (직업표 차례 — 사용자 "다음 그냥 직업표에 있는 순서대로 하자")
     */
    static void advance() {
        Jobs.Job sw = Jobs.job("swordsman");
        Jobs.Job sm = Jobs.job("swordmaster");
        Jobs.Job bk = Jobs.job("bloodknight");
        Jobs.Job wa = Jobs.job("warrior");
        Jobs.Job wl = Jobs.job("warlord");
        Jobs.Job gl = Jobs.job("gladiator");
        check(sm != null && bk != null && wl != null && gl != null && Jobs.allAdvanced().size() == 12 && Jobs.job("lycan") == null, "advanced jobs " + Jobs.allAdvanced().size());
        if (sm == null || bk == null || wl == null || gl == null) return;
        check(Jobs.advanceLevel() == 50 && near(Jobs.switchSilver(), 600) && near(Jobs.advanceDps(), 1.2), "advance rules (50렙 · 은화 600 · ×1.2)");
        check(Jobs.advancedOf("swordsman").equals(List.of(sm, bk)) && Jobs.advancedOf("warrior").equals(List.of(wl, gl)) && Jobs.advancedOf("spellblade").size() == 2
                && Jobs.advancedOf("mage").size() == 2 && Jobs.advancedOf("archer").size() == 2 && Jobs.advancedOf("assassin").size() == 2
                && Jobs.advancedOf("lancer").isEmpty() && Jobs.advancedOf("nope").isEmpty(), "advancedOf (각성 · 반전 순서 — 창술사는 아직)");
        check(Jobs.advance("warrior", "awaken") == wl && Jobs.advance("warrior", "invert") == gl && Jobs.baseOf(wl) == wa && Jobs.baseOf(gl) == wa, "warrior advance");
        check(wl.display().equals("워로드") && gl.display().equals("글래디에이터") && "awaken".equals(wl.path()) && "invert".equals(gl.path()),
                "warrior display");
        check(Jobs.find("워로드") == wl && Jobs.find("스타르카드") == null && Jobs.find("gladiator") == gl && Jobs.find("글래디에이터") == gl, "find warrior advanced");
        check(Jobs.advance("swordsman", "awaken") == sm && Jobs.advance("swordsman", "invert") == bk && Jobs.advance("swordsman", "nope") == null
                && Jobs.advance(null, "awaken") == null, "advance(base, path)");
        check(Jobs.baseOf(sm) == sw && Jobs.baseOf(bk) == sw && Jobs.baseOf(sw) == sw && Jobs.baseOf(null) == null, "baseOf");
        check(sm.advanced() && bk.advanced() && !sw.advanced() && "awaken".equals(sm.path()) && "invert".equals(bk.path()) && "swordsman".equals(sm.base()), "base · path");
        check(sm.display().equals("소드마스터") && bk.display().equals("블러드 나이트") && sw.display().equals("검사"), "display 이명(직업명)");
        check(Jobs.pathName("awaken").equals("각성") && Jobs.pathName("invert").equals("반전") && Jobs.pathName("x").isEmpty() && Jobs.paths().equals(List.of("awaken", "invert")),
                "path names");
        check(!Jobs.order().contains("swordmaster") && Jobs.all().size() == 11 && !Jobs.all().contains(sm), "advanced jobs are not first jobs");
        check(Jobs.find("소드마스터") == sm && Jobs.find("swordmaster") == sm && Jobs.find("시구르드") == null && Jobs.find("bloodknight") == bk
                && Jobs.find("블러드 나이트") == bk, "find advanced");
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (Jobs.Job j : Jobs.all()) for (Jobs.Skill k : j.skills()) {
            ids.add(k.id());
            names.add(k.name());
        }
        double[] cds = {6, 12, 15, 30, 60};
        for (Jobs.Job a : Jobs.allAdvanced()) {
            Jobs.Job b = Jobs.baseOf(a);
            check(b != null && a.weapons().equals(b.weapons()) && a.stats().equals(b.stats()) && near(a.scale(), b.scale()), a.id() + " inherits weapons · stats · scale");
            check(a.skills().size() == Jobs.SLOTS && a.trait() != null && !a.trait().desc().isEmpty() && a.ready() && a.display().equals(a.name()), a.id()
                    + " five skills · trait · job name only (no epithet)");
            check(a.color().length == 2 && a.color()[0].length == 3, a.id() + " two colors");
            for (int i = 0; i < Jobs.SLOTS; i++) {
                Jobs.Skill k = a.skill(i);
                check(k.slot() == i && k.cd() == cds[i] && k.id().startsWith(a.id() + "_") && !k.desc().isEmpty() && ids.add(k.id()), a.id() + " skill " + i + " " + k.id());
                check(names.add(k.name()), a.id() + " skill name unique " + k.name());
                for (String u : Jobs.utils(k)) check(Rules.get().obj("RUNE_SPREAD").obj("utils").has(u), k.id() + " util " + u + " known");
                if (k.total() > 0) check(k.def().has("t") && (Jobs.areaKind(k) == null || List.of("single", "narrow").contains(Jobs.areaKind(k))), k.id() + " t · area");
                check(Jobs.upId(k.id()).equals(b.skill(i).id()), a.id() + " up id " + k.id() + " → " + Jobs.upId(k.id()));
                check(Jobs.jobOf(k) == a, "jobOf " + k.id());
                check((k.total() > 0) == (b.skill(i).total() > 0), a.id() + " slot " + i + " same role (damage · stance)");
                check(Jobs.upKeys(k).size() == Jobs.upKeys(b.skill(i)).size() || i == 3, a.id() + " slot " + i + " up keys");
            }
            double r = dps(a) / dps(b);
            check(near(r, 1.2, 0.005), a.id() + " dps ×" + r + " (1차 × 1.2)");
        }
        check(Jobs.upId("swordsman_flurry").equals("swordsman_flurry") && Jobs.upId(null) == null, "upId keeps first-job ids");
        // 0.51.0 재설계안 2판 (claude/job-advance-redo.md) — 부분 합 = 배율 합: 칼집 섬광 · 받아넘기기는 한 번에 넣고 숫자만 여러 줄, 불의 울타리 가로지르기 다섯,
        // 다시 벼린 칼 충격 + 검풍 / 핏빛 대검 세 번, 피의 사슬 박음 + 끌어당김, 칼을 뽑다 칼날 + 내려꽂음 + X 베기 넷
        check(near(sm.skill(0).total(), 5) && sm.skill(0).num("lines", 0) >= 2 && near(sm.skill(1).total(), 3) && near(sum(sm.skill(2), "cuts"), 9)
                && near(sm.skill(4).num("burst", 0) + sm.skill(4).num("slash", 0), 40), "swordmaster totals 5 · 3 · 9 · 40");
        check(near(sum(bk.skill(0), "casts"), 5) && near(bk.skill(1).total(), 3) && near(bk.skill(2).num("bind", 0) + bk.skill(2).num("pull", 0), 9)
                && near(bk.skill(4).num("blade", 0) + bk.skill(4).num("slam", 0) + sum(bk.skill(4), "slashes"), 40), "bloodknight totals 5 · 3 · 9 · 40");
        // 같은 적을 여러 번 치는 것은 10틱 넘게 (다시 누르는 단계는 gap — 다시 누를 수 있는 가장 이른 틱 · JobRecast)
        List<Double> cleaveAt = bk.skill(0).def().numbersOrNull("hitAt");
        check(cleaveAt != null && cleaveAt.size() == 3 && bk.skill(0).num("gap", 0) + cleaveAt.get(1) - cleaveAt.get(0) >= 10
                && bk.skill(0).num("gap", 0) + cleaveAt.get(2) - cleaveAt.get(1) >= 10 && bk.skill(0).num("window", 0) > bk.skill(0).num("gap", 0), "핏빛 대검 다시 누름 간격");
        check(sm.skill(4).num("gap", 0) + sm.skill(4).num("slashAt", 0) - sm.skill(4).num("burstAt", 0) >= 10 && sm.skill(4).num("dur", 0) > sm.skill(4).num("gap", 0),
                "다시 벼린 칼 검풍 간격");
        check(sm.skill(2).num("every", 0) >= 10 && bk.skill(2).num("delay", 0) >= 10 && bk.skill(4).num("every", 0) >= 10 && bk.skill(4).num("fly", 0) >= 10,
                "swordmaster · bloodknight i-frame spacing");
        // 마지막 피해 틱 = t (RpgJobTimingTests 와 같은 값 — 다시 누르는 스킬은 12틱마다 다시 누름)
        check(near(sm.skill(0).num("sheath", 0), sm.skill(0).num("t", 0)) && near(sm.skill(1).num("window", 0), sm.skill(1).num("t", 0)), "칼집 섬광 t 6 · 받아넘기기 t 10");
        check(near(sm.skill(2).num("first", 0) + (sm.skill(2).def().list("cuts").size() - 1) * sm.skill(2).num("every", 0), sm.skill(2).num("t", 0)), "불의 울타리 t 46");
        check(near(24 + cleaveAt.get(2), bk.skill(0).num("t", 0)) && near(bk.skill(1).num("leap", 0), bk.skill(1).num("t", 0)), "핏빛 대검 t 31 · 피의 돌진 t 6");
        check(near(bk.skill(2).num("first", 0) + bk.skill(2).num("delay", 0) + 2, bk.skill(2).num("t", 0)), "피의 사슬 t 36");
        check(near(bk.skill(4).num("draw", 0) + 1 + bk.skill(4).num("fly", 0) + bk.skill(4).def().list("slashes").size() * bk.skill(4).num("every", 0),
                bk.skill(4).num("t", 0)), "칼을 뽑다 t 60");
        // 특성
        check(sm.trait().name().equals("투지") && sm.trait().max() == 8 && near(sm.trait().def().numOr("crit", 0), 0.3), "투지 최대 8 · 치명 +30%");
        check(bk.trait().name().equals("피의 갈증") && bk.trait().max() == 5 && near(bk.trait().def().numOr("cost", 0), 0.04) && near(bk.trait().def().numOr("leech", 0), 0.05)
                && near(bk.trait().def().numOr("thirstAt", 0), 0.3) && bk.trait().def().numOr("thirstSpeed", 0) > 0 && bk.trait().def().numOr("leechMax", 0) > 0,
                "피의 갈증 체력 4% · 흡혈 5% · 체력 30% 에서 가장 목마름");
        check(Jobs.skillLine(sm.skill(0)).startsWith("라이트닝 드로우 · 6초 · 배율 ") && Jobs.skillLine(bk.skill(3)).equals("새비지 프렌지 · 30초"), "skill line " + Jobs.skillLine(sm.skill(0)));
        warriorAdvance(wa, wl, gl);
        spellbladeAdvance(Jobs.job("spellblade"), Jobs.job("runeknight"), Jobs.job("swordcaller"));
        mageAdvance(Jobs.job("mage"), Jobs.job("arcmage"), Jobs.job("necromancer"));
        archerAdvance(Jobs.job("archer"), Jobs.job("sniper"), Jobs.job("shadowhunter"));
        assassinAdvance(Jobs.job("assassin"), Jobs.job("trickster"), Jobs.job("bladedancer"));
    }

    /**
     * 0.51.0 암살자 2차 — 트릭스터 · 블레이드 댄서 (사용자 "다음 작업"): 부분 합 = 배율 합 (암살자와 같은 4.75 · 2.85 · 8.55 · 38 — 표식을 터뜨리는
     * 것은 표식 가득 (7) 기준) · 무적 시간 띄움 · t · w · 특성 (표식 7 · 옮겨감 / 춤사위 5 · +6% · 이동 +4%)
     */
    static void assassinAdvance(Jobs.Job as, Jobs.Job tr, Jobs.Job bd) {
        check(tr != null && bd != null, "assassin advanced jobs");
        if (tr == null || bd == null) return;
        check(Jobs.advance("assassin", "awaken") == tr && Jobs.advance("assassin", "invert") == bd && Jobs.baseOf(tr) == as && Jobs.baseOf(bd) == as, "assassin advance");
        check(tr.display().equals("트릭스터") && bd.display().equals("블레이드 댄서") && Jobs.find("트릭스터") == tr && Jobs.find("료스알프") == null
                && Jobs.find("블레이드 댄서") == bd, "assassin display · find");
        for (int i : new int[] {0, 1, 2, 4}) check(near(tr.skill(i).total(), as.skill(i).total()) && near(bd.skill(i).total(), as.skill(i).total()), "assassin totals slot " + i);
        int max = tr.trait().max();
        Jobs.Skill r0 = tr.skill(0);
        Jobs.Skill r1 = tr.skill(1);
        Jobs.Skill r2 = tr.skill(2);
        Jobs.Skill ru = tr.skill(4);
        check(max == 7 && near(r0.num("cut", 0) + r0.num("base", 0) + r0.num("per", 0) * max, 4.75) && near(r1.num("blast", 0) * 2, 2.85)
                && near(r2.num("cut", 0) + r2.num("coil", 0) + r2.num("venom", 0) * r2.num("ticks", 0), 8.55)
                && near(ru.num("rounds", 0) * ru.num("throwers", 0) * ru.num("dagger", 0) + ru.num("base", 0) + ru.num("per", 0) * max, 38), "trickster totals 4.75 · 2.85 · 8.55 · 38 (표식 7)");
        Jobs.Skill b0 = bd.skill(0);
        Jobs.Skill b1 = bd.skill(1);
        Jobs.Skill b2 = bd.skill(2);
        Jobs.Skill bu = bd.skill(4);
        check(near(b0.num("spin", 0) + b0.num("slash", 0), 4.75) && near(b1.num("steps", 0) * b1.num("cut", 0), 2.85) && near(b2.num("rounds", 0) * b2.num("round", 0), 8.55)
                && near(bu.num("slashes", 0) * bu.num("slash", 0) + bu.num("final", 0), 38), "bladedancer totals 4.75 · 2.85 · 8.55 · 38");
        // 같은 적을 여러 번 치는 것은 10틱 넘게 (환영 둘의 터짐 · 비수 넷 · 두 번 베기는 한 번에 넣고 숫자만 여러 줄)
        check(r0.num("burstAt", 0) - r0.num("cutAt", 0) >= 10 && r2.num("coilAt", 0) - r2.num("steps", 0) >= 10 && r2.num("every", 0) >= 10 && ru.num("every", 0) >= 10
                && ru.num("finalAt", 0) - ru.num("first", 0) - (ru.num("rounds", 0) - 1) * ru.num("every", 0) - ru.num("flight", 0) >= 10, "trickster i-frame spacing");
        check(b0.num("slashAt", 0) - 1 >= 10 && b1.num("every", 0) >= 10 && b2.num("every", 0) >= 10 && bu.num("every", 0) * 2 >= 10 && bu.num("finalDelay", 0) >= 10,
                "bladedancer i-frame spacing");
        // 마지막 피해 틱 = t · 첫 피해 = w (RpgJobTimingTests 와 같은 값)
        check(near(r0.num("burstAt", 0), r0.num("t", 0)) && near(r2.num("coilAt", 0) + r2.num("ticks", 0) * r2.num("every", 0), r2.num("t", 0))
                && near(ru.num("finalAt", 0), ru.num("t", 0)) && near(ru.num("first", 0) + ru.num("flight", 0), ru.num("w", 0)) && near(r1.num("life", 0), r1.num("t", 0)),
                "trickster t 12 · 40 · 58 · 52 (w 8)");
        check(near(b0.num("slashAt", 0), b0.num("t", 0)) && near((b1.num("steps", 0) - 1) * b1.num("every", 0), b1.num("t", 0))
                && near(b2.num("first", 0) + (b2.num("rounds", 0) - 1) * b2.num("every", 0), b2.num("t", 0))
                && near((bu.num("slashes", 0) - 1) * bu.num("every", 0) + bu.num("finalDelay", 0), bu.num("t", 0)), "bladedancer t 11 · 20 · 21 · 50");
        // 특성: 트릭스터 = 암살자의 표식 (같은 등 뒤 +25%p · 10초) 최대 7 + 옮겨감 (5 이상 · 6칸), 블레이드 댄서 = 춤사위 (최대 5 · +6% · 이동 +4% · 3초)
        check(tr.trait().name().equals(as.trait().name()) && near(tr.trait().def().numOr("back", 0), as.trait().def().numOr("back", -1))
                && near(tr.trait().def().numOr("markFor", 0), as.trait().def().numOr("markFor", -1)) && near(tr.trait().def().numOr("shift", 0), 5)
                && near(tr.trait().def().numOr("shiftRange", 0), 6), "표식 7 · 옮겨감");
        check(bd.trait().name().equals("춤사위") && bd.trait().max() == 5 && near(bd.trait().def().numOr("perStack", 0), 0.06) && near(bd.trait().def().numOr("speedPer", 0), 0.04)
                && near(bd.trait().def().numOr("keep", 0), 60), "춤사위 5 · +6% · 이동 +4% · 3초");
        // 유틸 · 범위: 환술 = 순간 이동 · 도발 (1차와 같음), 요정의 걸음 = 피해 감소, 검은 장미 덤불 단일 · 나머지 좁은 범위
        check(Jobs.utils(r1).equals(List.of("blink", "taunt")) && Jobs.utils(b1).equals(List.of("reduce")) && near(Jobs.utilCost(b1), 0.1) && "single".equals(Jobs.areaKind(r0))
                && "narrow".equals(Jobs.areaKind(r2)) && "narrow".equals(Jobs.areaKind(b0)) && "narrow".equals(Jobs.areaKind(b2)) && Jobs.areaKind(bu) == null, "assassin advanced utils · area");
        check(Jobs.windupTicks(ru) == 8 && Jobs.windupTicks(r1) == 0 && Jobs.windupTicks(bu) == 0, "windups 8 · 0 · 0");
        check(Jobs.upKey(tr.skill(3), "first") != null && Jobs.upKey(bd.skill(3), "echo") != null, "assassin advanced up keys");
        check(Jobs.skillLine(tr.skill(3)).equals("끝없는 밤 · 30초") && Jobs.skillLine(bd.skill(0)).startsWith("빛살 베기 · 6초 · 배율 "), "assassin advanced skill line");
        check(tr.color()[0][0] > 0.9 && tr.color()[0][2] > 0.7 && tr.color()[1][1] < 0.2 && bd.color()[0][0] > 0.95 && bd.color()[0][1] > 0.8 && bd.color()[1][0] > 0.95
                && bd.color()[1][2] > 0.7, "colors: magenta · violet / white gold · pink");
    }

    /**
     * 0.51.0 궁수 2차 — 스나이퍼 · 섀도 헌터 (사용자 "다음 직업표 시작"): 부분 합 = 배율 합 (궁수와 같은 7.5 · 13.5 · 60 — 둘째 칸은 1차 뇨르드의 순풍처럼
     * 평타 화살이라 배율 합 없음) · 무적 시간 띄움 · t · w · 특성 (호흡 8 급소 사격 / 추격 5 · 저주)
     */
    static void archerAdvance(Jobs.Job ar, Jobs.Job sn, Jobs.Job sh) {
        check(sn != null && sh != null, "archer advanced jobs");
        if (sn == null || sh == null) return;
        check(Jobs.advance("archer", "awaken") == sn && Jobs.advance("archer", "invert") == sh && Jobs.baseOf(sn) == ar && Jobs.baseOf(sh) == ar, "archer advance");
        check(sn.display().equals("스나이퍼") && sh.display().equals("섀도 헌터") && Jobs.find("스나이퍼") == sn && Jobs.find("미르크비드") == null
                && Jobs.find("섀도 헌터") == sh, "archer display · find");
        for (int i : new int[] {0, 1, 2, 4}) check(near(sn.skill(i).total(), ar.skill(i).total()) && near(sh.skill(i).total(), ar.skill(i).total()), "archer totals slot " + i);
        Jobs.Skill p0 = sn.skill(0);
        Jobs.Skill su = sn.skill(4);
        check(near(p0.num("rollShots", 0) * p0.num("rollArrow", 0) + p0.num("kneelShots", 0) * p0.num("kneelArrow", 0) + p0.num("pierce", 0), 7.5)
                && near(sn.skill(2).num("volleys", 0) * sn.skill(2).num("volley", 0), 13.5)
                && near(su.num("airShots", 0) * su.num("air", 0) + su.num("shot", 0) + su.num("wave", 0), 60), "sniper totals 7.5 · 13.5 · 60");
        Jobs.Skill hu = sh.skill(4);
        check(near(sh.skill(0).num("arrows", 0) * sh.skill(0).num("arrow", 0), 7.5) && near(sh.skill(2).num("pierce", 0) + sh.skill(2).num("burst", 0), 13.5)
                && near(hu.num("waves", 0) * hu.num("wave", 0) + hu.num("hunt", 0), 60), "shadowhunter totals 7.5 · 13.5 · 60");
        // 같은 적을 여러 번 치는 것은 10틱 넘게 (구르며 두 발 · 무릎 꿇고 세 발 · 화살비 한 번 · 검은 화살 세 발은 한 번에 넣고 숫자만 여러 줄)
        check(p0.num("kneelAt", 0) - p0.num("rollAt", 0) >= 10 && sn.skill(2).num("gap", 0) >= 10 && su.num("airGap", 0) >= 10
                && su.num("shotAt", 0) - su.num("airAt", 0) - su.num("airGap", 0) >= 10 && su.num("waveDelay", 0) >= 10, "sniper i-frame spacing");
        check(sh.skill(2).num("burstAt", 0) - sh.skill(2).num("shotAt", 0) - sh.skill(2).num("range", 0) / sh.skill(2).num("speed", 1) * 20 >= 10
                && hu.num("first", 0) - hu.num("curseAt", 0) >= 0 && hu.num("gap", 0) >= 10 && hu.num("huntAt", 0) - hu.num("first", 0) - (hu.num("waves", 0) - 1) * hu.num("gap", 0) >= 10,
                "shadowhunter i-frame spacing");
        // 마지막 피해 틱 = t · 첫 피해 = w (RpgJobTimingTests 와 같은 값 — 화살이 날아가는 틱은 2.5칸 앞 과녁 기준)
        check(near(sn.skill(2).num("first", 0) + (sn.skill(2).num("volleys", 0) - 1) * sn.skill(2).num("gap", 0), sn.skill(2).num("t", 0))
                && near(sn.skill(2).num("first", 0), sn.skill(2).num("w", 0)) && near(su.num("shotAt", 0) + su.num("waveDelay", 0), su.num("t", 0))
                && su.num("w", 0) >= su.num("airAt", 0) && su.num("w", 0) <= su.num("airAt", 0) + 3 && p0.num("t", 0) >= p0.num("kneelAt", 0), "sniper t 14 · 30 · 54 (w 16 · 10)");
        check(near(sh.skill(2).num("burstAt", 0), sh.skill(2).num("t", 0)) && sh.skill(2).num("w", 0) >= sh.skill(2).num("shotAt", 0)
                && near(hu.num("huntAt", 0), hu.num("t", 0)) && near(hu.num("first", 0), hu.num("w", 0)), "shadowhunter t 36 · 58 (w 7 · 16)");
        // 특성: 스나이퍼 = 궁수의 호흡 (같은 +4% · 사거리 +1칸) 최대 8 + 급소 사격, 섀도 헌터 = 추격 (최대 5 · 이동 +3%) + 저주 (최대 5 · 받는 피해 +2%)
        check(sn.trait().name().equals(ar.trait().name()) && sn.trait().max() == 8 && near(sn.trait().def().numOr("perStack", 0), ar.trait().def().numOr("perStack", -1))
                && near(sn.trait().def().numOr("rangePer", 0), ar.trait().def().numOr("rangePer", -1)) && near(sn.trait().def().numOr("snipeUse", 0), 3), "호흡 8 · 급소 사격");
        check(sh.trait().name().equals("추격") && sh.trait().max() == 5 && near(sh.trait().def().numOr("speedPer", 0), 0.03) && near(sh.trait().def().numOr("curseMax", 0), 5)
                && near(sh.trait().def().numOr("cursePer", 0), 0.02), "추격 5 · 이동 +3% · 저주 5 · +2%");
        // 유틸 · 범위: 울르의 폭우 묶음, 꿰뚫는 연사 · 저주 번짐은 좁은 범위 (꿰뚫음), 검은 화살은 단일
        check(Jobs.utils(sn.skill(2)).equals(List.of("root")) && near(Jobs.utilCost(sn.skill(2)), 0.1) && "narrow".equals(Jobs.areaKind(p0))
                && "narrow".equals(Jobs.areaKind(sh.skill(2))) && "single".equals(Jobs.areaKind(sh.skill(0))) && Jobs.utils(sh.skill(4)).isEmpty(), "archer advanced utils · area");
        check(Jobs.windupTicks(sn.skill(2)) == 16 && Jobs.windupTicks(su) == 10 && Jobs.windupTicks(hu) == 16, "windups 16 · 10 · 16");
        check(Jobs.upKey(sn.skill(1), "arrow") != null && Jobs.upKey(sh.skill(1), "arrow") != null && Jobs.upKey(sn.skill(3), "dur") != null
                && Jobs.upKey(sh.skill(3), "crit") != null, "archer advanced up keys");
        check(Jobs.skillLine(sn.skill(3)).equals("사과 겨누기 · 30초") && Jobs.skillLine(sh.skill(0)).startsWith("검은 화살 · 6초 · 배율 "), "archer advanced skill line");
        check(sn.color()[0][1] > 0.9 && sn.color()[1][0] > 0.9 && sh.color()[0][2] > 0.9 && sh.color()[1][2] > 0.9, "colors: lime · gold / indigo · silver");
    }

    /**
     * 0.51.0 매지션 2차 — 아크메이지 · 네크로맨서 (사용자 "다음 직업표 작업"): 부분 합 = 배율 합 (매지션과 같은 7 · 4.2 · 12.6 · 56) · 무적 시간
     * 띄움 · t · 특성 (주문 연쇄 5 · 별자리 / 망자의 군대 최대 4 · 20초)
     */
    static void mageAdvance(Jobs.Job mg, Jobs.Job am, Jobs.Job nc) {
        check(am != null && nc != null, "mage advanced jobs");
        if (am == null || nc == null) return;
        check(Jobs.advance("mage", "awaken") == am && Jobs.advance("mage", "invert") == nc && Jobs.baseOf(am) == mg && Jobs.baseOf(nc) == mg, "mage advance");
        check(am.display().equals("아크메이지") && nc.display().equals("네크로맨서") && Jobs.find("아크메이지") == am && Jobs.find("나글파르") == null,
                "mage display · find");
        for (int i : new int[] {0, 1, 2, 4}) check(near(am.skill(i).total(), mg.skill(i).total()) && near(nc.skill(i).total(), mg.skill(i).total()), "mage totals slot " + i);
        check(near(am.skill(0).num("bolts", 0) * am.skill(0).num("bolt", 0), 7) && near(am.skill(1).num("blast", 0) + am.skill(1).num("blast2", 0), 4.2)
                && near(am.skill(2).num("pulses", 0) * am.skill(2).num("pulse", 0) + am.skill(2).num("burst", 0), 12.6)
                && near(am.skill(4).num("pages", 0) * am.skill(4).num("page", 0) + am.skill(4).num("stars", 0) * am.skill(4).num("star", 0) + am.skill(4).num("final", 0), 56),
                "arcmage totals 7 · 4.2 · 12.6 · 56");
        check(near(nc.skill(0).num("spear", 0) + nc.skill(0).num("burst", 0), 7) && near(nc.skill(1).num("wall", 0) + nc.skill(1).num("shatter", 0), 4.2)
                && near(nc.skill(2).num("blast", 0) + nc.skill(2).num("pulses", 0) * nc.skill(2).num("cloud", 0), 12.6)
                && near(nc.skill(4).num("ram", 0) + nc.skill(4).num("crew", 0) * nc.skill(4).num("land", 0) + nc.skill(4).num("final", 0), 56),
                "necromancer totals 7 · 4.2 · 12.6 · 56");
        // 같은 적을 여러 번 치는 것은 10틱 넘게 (마탄 다섯 · 별의 비는 한 번에 넣고 숫자만 여러 줄)
        Jobs.Skill au = am.skill(4);
        check(am.skill(1).num("second", 0) >= 10 && am.skill(2).num("gap", 0) >= 10 && au.num("gap", 0) >= 10
                && au.num("rainAt", 0) - au.num("rise", 0) - (au.num("waves", 0) - 1) * au.num("gap", 0) >= 10 && au.num("finalAt", 0) + 4 - (au.num("rainAt", 0) + 6) >= 10,
                "arcmage i-frame spacing");
        Jobs.Skill nu = nc.skill(4);
        check(nc.skill(0).num("burstAt", 0) - nc.skill(0).num("throwAt", 0) - nc.skill(0).num("range", 0) / nc.skill(0).num("speed", 1) * 20 >= 10
                && nc.skill(1).num("shatterAt", 0) - nc.skill(1).num("wallAt", 0) >= 10 && nc.skill(2).num("cloudAt", 0) - nc.skill(2).num("swell", 0) >= 10
                && nc.skill(2).num("gap", 0) >= 10 && nu.num("landAt", 0) - nu.num("rise", 0) - nu.num("sail", 0) >= 10 && nu.num("finalAt", 0) - nu.num("landAt", 0) >= 10,
                "necromancer i-frame spacing");
        // 마지막 피해 틱 = t (RpgJobTimingTests 와 같은 값)
        check(near(am.skill(1).num("second", 0) + am.skill(1).num("delay", 0), am.skill(1).num("t", 0)) && near(am.skill(1).num("delay", 0), am.skill(1).num("w", 0))
                && near(am.skill(2).num("dur", 0), am.skill(2).num("t", 0)) && near(au.num("finalAt", 0) + 4, au.num("t", 0)) && near(au.num("rise", 0) + 6, au.num("w", 0)),
                "arcmage t 4 · 30 · 70 · 76 (w 20 · 10 · 26)");
        check(near(nc.skill(0).num("burstAt", 0), nc.skill(0).num("t", 0)) && near(nc.skill(1).num("shatterAt", 0), nc.skill(1).num("t", 0))
                && near(nc.skill(2).num("cloudAt", 0) + (nc.skill(2).num("pulses", 0) - 1) * nc.skill(2).num("gap", 0), nc.skill(2).num("t", 0))
                && near(nc.skill(2).num("swell", 0), nc.skill(2).num("w", 0)) && near(nu.num("finalAt", 0), nu.num("t", 0)) && near(nu.num("emergeAt", 0), nu.num("w", 0)),
                "necromancer t 24 · 24 · 40 · 60 (w 8 · 10 · 10)");
        // 특성: 아크메이지 = 매지션의 주문 연쇄 (같은 +12% · 완드 −0.3초) 최대 5 + 별자리, 네크로맨서 = 망자의 군대 (최대 4 · 20초 · 위력 × 0.4 · 1.5초마다)
        check(am.trait().name().equals(mg.trait().name()) && am.trait().max() == 5 && near(am.trait().def().numOr("perChain", 0), mg.trait().def().numOr("perChain", -1))
                && near(am.trait().def().numOr("wandCut", 0), mg.trait().def().numOr("wandCut", -1)) && near(am.trait().def().numOr("star", 0), 2.0), "주문 연쇄 5 · 별자리");
        check(nc.trait().name().equals("망자의 군대") && nc.trait().max() == 4 && near(nc.trait().def().numOr("crewFor", 0), 400) && near(nc.trait().def().numOr("crewHit", 0), 0.4)
                && near(nc.trait().def().numOr("crewEvery", 0), 30), "망자의 군대 4 · 20초 · 위력 × 0.4");
        // 유틸 · 범위: 서리 별 걸음 순간 이동 + 빙결 (15%) · 망자와 자리 바꾸기 순간 이동 + 묶음 (20%), 별무리 마탄 · 뼈 창은 좁은 범위 (튀는 별빛 · 꿰뚫음)
        check(Jobs.utils(am.skill(1)).equals(List.of("blink", "stun05")) && near(Jobs.utilCost(am.skill(1)), 0.15) && Jobs.utils(nc.skill(1)).equals(List.of("blink", "root"))
                && near(Jobs.utilCost(nc.skill(1)), 0.2) && "narrow".equals(Jobs.areaKind(am.skill(0))) && "narrow".equals(Jobs.areaKind(nc.skill(0))), "mage advanced utils · area");
        check(Jobs.windupTicks(au) == 26 && Jobs.windupTicks(nu) == 10 && Jobs.windupTicks(nc.skill(0)) == 8, "windups 26 · 10 · 8");
        check(Jobs.upKeys(am.skill(3)).size() == 1 && Jobs.upKey(nc.skill(3), "boost") != null, "stance up keys");
        check(Jobs.skillLine(am.skill(3)).equals("꿀술의 노래 · 30초") && Jobs.skillLine(nc.skill(0)).startsWith("뼈 창 · 6초 · 배율 "), "mage advanced skill line");
        check(am.color()[0][2] > 0.9 && am.color()[1][0] > 0.9 && nc.color()[0][1] > 0.85 && nc.color()[1][0] > 0.85 && nc.color()[1][2] > 0.6,
                "colors: star blue · gold / poison green · bone");
    }

    /**
     * 0.51.0 마검사 2차 — 재설계안 2판 (claude/job-advance-redo.md · 사용자 "직업 이름 같은거 … 바꾸고 싶으면 바꿔도 됨"): 뵐룬드(룬 스미스 — 룬 나이트에서 이름을
     * 바꿈) 새 스킬 다섯 · 소드 콜러 (헥스 블레이드 대신). 부분 합 = 배율 합 (마검사와 같은 8.5 · 5.1 · 15.3 · 68) · 무적 시간 띄움 · t · w ·
     * 특성 · 마력 검술 (2차는 1차 마검사의 칼 규칙을 이어 받음)
     */
    static void spellbladeAdvance(Jobs.Job sb, Jobs.Job rk, Jobs.Job sc) {
        check(rk != null && sc != null, "spellblade advanced jobs");
        if (rk == null || sc == null) return;
        check(Jobs.advance("spellblade", "awaken") == rk && Jobs.advance("spellblade", "invert") == sc && Jobs.baseOf(rk) == sb && Jobs.baseOf(sc) == sb, "spellblade advance");
        check(rk.display().equals("룬 스미스") && sc.display().equals("소드 콜러") && Jobs.find("룬 스미스") == rk && Jobs.find("스키르니르") == null
                && Jobs.find("소드 콜러") == sc && Jobs.find("swordcaller") == sc && Jobs.job("hexblade") == null, "spellblade display · find");
        List<String> rkIds = new ArrayList<>();
        List<String> scIds = new ArrayList<>();
        for (Jobs.Skill k : rk.skills()) rkIds.add(k.id());
        for (Jobs.Skill k : sc.skills()) scIds.add(k.id());
        check(rkIds.equals(List.of("runeknight_anvil", "runeknight_bellows", "runeknight_furnace", "runeknight_temper", "runeknight_ult")), "rune smith skills " + rkIds);
        check(scIds.equals(List.of("swordcaller_strike", "swordcaller_shift", "swordcaller_orbit", "swordcaller_twin", "swordcaller_ult")), "sword caller skills " + scIds);
        for (int i : new int[] {0, 1, 2, 4}) check(near(rk.skill(i).total(), sb.skill(i).total()) && near(sc.skill(i).total(), sb.skill(i).total()), "spellblade totals slot " + i);
        // 룬 스미스: 룬 모루 갈라짐 + 모루 터짐 · 풀무 숨 셋 · 쇳물 숫양 달려옴 + 되돌림
        check(near(rk.skill(0).num("crack", 0) + rk.skill(0).num("burst", 0), 8.5) && near(sum(rk.skill(1), "breaths"), 5.1) && rk.skill(1).def().list("breaths").size() == 3
                && near(rk.skill(4).num("charge", 0) + rk.skill(4).num("back", 0), 68), "rune smith totals 8.5 · 5.1 · 15.3 · 68");
        // 소드 콜러: 베어라 넷 · 꿰뚫음 + 맴돌기 넷 · 칼날 감옥 여섯 + 큰 한 방
        check(near(sum(sc.skill(0), "cuts"), 8.5) && sc.skill(0).def().list("cuts").size() == 4 && near(sc.skill(2).num("pierce", 0) + sum(sc.skill(2), "spins"), 15.3)
                && near(sum(sc.skill(4), "slashes") + sc.skill(4).num("final", 0), 68) && sc.skill(4).def().list("slashes").size() == 6, "sword caller totals 8.5 · 5.1 · 15.3 · 68");
        // 같은 적을 여러 번 치는 것은 10틱 넘게 (숫자만 여러 줄)
        check(rk.skill(0).num("burstAt", 0) >= 10 && rk.skill(1).num("every", 0) >= 10 && rk.skill(4).num("t", 0) - rk.skill(4).num("w", 0) >= 10, "rune smith i-frame spacing");
        check(sc.skill(0).num("groupGap", 0) >= 10 && sc.skill(2).num("every", 0) >= 10 && sc.skill(4).num("every", 0) >= 10
                && sc.skill(4).num("finalAt", 0) - sc.skill(4).num("surroundAt", 0) - 5 * sc.skill(4).num("every", 0) >= 10, "sword caller i-frame spacing");
        // 마지막 피해 틱 = t · 첫 피해 = w (RpgJobTimingTests 와 같은 값 — 앞 2.5칸 과녁)
        Jobs.Skill an = rk.skill(0);
        check(near(an.num("strikeAt", 0) + Math.ceil((2.5 - an.num("start", 0)) / an.num("speed", 1)) + an.num("burstAt", 0), an.num("t", 0)), "룬 모루 t 17");
        Jobs.Skill be = rk.skill(1);
        check(near(be.num("first", 0) + 2 * be.num("every", 0), be.num("t", 0)) && near(be.num("first", 0) + 2 * be.num("every", 0) + 6, be.num("dur", 0)), "풀무 숨 t 24");
        check(near(rk.skill(2).num("eruptAt", 0), rk.skill(2).num("t", 0)) && near(rk.skill(2).num("eruptAt", 0), rk.skill(2).num("w", 0)), "용광로 t = w 16");
        Jobs.Skill ram = rk.skill(4);
        check(near(ram.num("runAt", 0) + Math.ceil((ram.num("summon", 0) - 3.0) / ram.num("speed", 1)), ram.num("w", 0))
                && near(ram.num("strikeAt", 0) + Math.ceil((2.5 - 1.0) / ram.num("backSpeed", 1)), ram.num("t", 0)) && ram.num("clashAt", 0) < ram.num("strikeAt", 0),
                "쇳물 숫양 w 21 · t 31");
        Jobs.Skill st = sc.skill(0);
        check(near(st.num("launch", 0) + 1 + st.num("groupGap", 0), st.num("t", 0)) && st.def().list("cuts").size() == 2 * st.num("group", 0), "베어라 t 13");
        check(near(sc.skill(1).num("start", 0) + Math.ceil(2.5 / sc.skill(1).num("speed", 1)), sc.skill(1).num("t", 0)), "데려가라 t 3");
        Jobs.Skill ob = sc.skill(2);
        check(near(ob.num("launch", 0) + 1 + ob.def().list("spins").size() * ob.num("every", 0), ob.num("t", 0)) && ob.num("lines", 0) * ob.def().list("spins").size() == 12,
                "꿰뚫고 맴돌아라 t 43 · 칼질 열둘");
        check(near(sc.skill(4).num("finalAt", 0), sc.skill(4).num("t", 0)) && near(sc.skill(4).num("surroundAt", 0), sc.skill(4).num("w", 0))
                && sc.skill(4).num("lines", 0) * sc.skill(4).def().list("slashes").size() == 30, "칼날 감옥 t 70 · w 10 · 칼질 서른");
        // 특성: 룬 스미스 = 마력 각인 최대 5 · 번짐 0.5 · 달궈짐 5초 · 식으며 깨짐 위력 × 0.8, 소드 콜러 = 스스로 싸우는 칼 (최대 5 · 하나당 공격 속도 +20% → ×2)
        check(rk.trait().name().equals("마력 각인") && rk.trait().max() == 5 && near(rk.trait().def().numOr("markBurst", 0), sb.trait().def().numOr("markBurst", -1))
                && near(rk.trait().def().numOr("spread", 0), 0.5) && near(rk.trait().def().numOr("heatFor", 0), 100) && near(rk.trait().def().numOr("quench", 0), 0.8),
                "마력 각인 5 · 번짐 · 달궈짐");
        check(sc.trait().name().equals("스스로 싸우는 칼") && sc.trait().max() == 5 && near(1 + sc.trait().max() * sc.trait().def().numOr("per", 0), 2)
                && near(sc.trait().def().numOr("range", 0), 10), "스스로 싸우는 칼 5 · ×2 · 10칸");
        // 마력 검술: 2차는 특성에 magicSword 가 없어도 1차의 칼 규칙 (요구 힘 = 힘 + 마력 · 피해 힘 = 힘 + 마력 × 0.5)
        Map<String, Double> s0 = new java.util.LinkedHashMap<>();
        s0.put("str", 10.0);
        s0.put("int", 40.0);
        for (Jobs.Job a : List.of(rk, sc)) {
            check(Jobs.magicSword(a, "sword") && !Jobs.magicSword(a, "axe"), a.id() + " magic sword");
            check(near(Jobs.reqStats(a, "sword", s0).get("str"), 50) && near(Jobs.damageStats(a, "sword", s0).get("str"), 30), a.id() + " magic sword stats");
        }
        // 유틸: 룬 모루 · 용광로 띄움 · 풀무 숨 보호막 · 쇳물 숫양 띄움 + 밀쳐냄 / 데려가라 순간 이동 · 칼날 감옥 묶음 3초
        check(Jobs.utils(rk.skill(0)).equals(List.of("lift")) && Jobs.utils(rk.skill(1)).equals(List.of("shield")) && Jobs.utils(rk.skill(2)).equals(List.of("lift"))
                && Jobs.utils(rk.skill(4)).equals(List.of("lift", "knock")) && Jobs.utils(sc.skill(1)).equals(List.of("blink"))
                && Jobs.utils(sc.skill(4)).equals(List.of("root3")) && Jobs.utils(sc.skill(0)).isEmpty() && Jobs.utils(sc.skill(2)).isEmpty(), "spellblade advanced utils");
        check(Jobs.windupTicks(rk.skill(2)) == 16 && Jobs.windupTicks(rk.skill(4)) == 21 && Jobs.windupTicks(sc.skill(4)) == 10, "windups 16 · 21 · 10");
        check("narrow".equals(Jobs.areaKind(rk.skill(0))) && Jobs.areaKind(rk.skill(2)) == null && "single".equals(Jobs.areaKind(sc.skill(2)))
                && "single".equals(Jobs.areaKind(sc.skill(4))) && Jobs.slotRole(sc.skill(2)).equals("주력") && !Jobs.slotRole(rk.skill(2)).equals("주력"), "areas · 주력");
        check(Jobs.upKeys(rk.skill(3)).size() == 1 && Jobs.upKey(rk.skill(3), "shard") != null && Jobs.upKeys(sc.skill(3)).size() == 1
                && Jobs.upKey(sc.skill(3), "echo") != null, "stance up keys");
        check(Jobs.skillLine(rk.skill(3)).equals("담금질 · 30초") && Jobs.skillLine(sc.skill(0)).startsWith("플라잉 블레이드 · 6초 · 배율 "), "spellblade advanced skill line");
        check(rk.color()[1][0] > 0.9 && rk.color()[0][2] > 0.9 && sc.color()[0][0] < 0.3 && sc.color()[0][1] > 0.8 && sc.color()[1][2] > 0.9, "colors: purple · gold / teal · white");
    }

    /**
     * 0.51.0 전사 2차 — 재설계안 2판 (claude/job-advance-redo.md): 워로드 새 스킬 다섯 · 글래디에이터 (라이칸 대신). 부분 합 = 배율 합
     * (전사와 같은 6.5 · 3.9 · 11.7 · 52 — 대지 가르기는 끝까지 모은 값, 마지막 휘두름은 바깥 날 · 안쪽은 절반), 무적 시간 띄움 · 다시 누름 · t · w · 특성 (분노 · 곰의
     * 화신 / 결투 10 · +3% · −40%) · 단일 직업의 3번 칸 이름 (주력)
     */
    static void warriorAdvance(Jobs.Job wa, Jobs.Job wl, Jobs.Job gl) {
        for (int i : new int[] {0, 1, 2, 4}) check(near(wl.skill(i).total(), wa.skill(i).total()) && near(gl.skill(i).total(), wa.skill(i).total()), "warrior totals slot " + i);
        List<String> wlIds = new ArrayList<>();
        List<String> glIds = new ArrayList<>();
        for (Jobs.Skill k : wl.skills()) wlIds.add(k.id());
        for (Jobs.Skill k : gl.skills()) glIds.add(k.id());
        check(wlIds.equals(List.of("warlord_rend", "warlord_slumber", "warlord_hug", "warlord_titan", "warlord_ult")), "warlord skills " + wlIds);
        check(glIds.equals(List.of("gladiator_flurry", "gladiator_hook", "gladiator_eight", "gladiator_fury", "gladiator_ult")), "gladiator skills " + glIds);
        // 워로드: 대지 가르기 (끝까지 모으면 6.5 · 처음은 절반) · 동면 3.9 · 곰의 포옹 11.7 · 마지막 휘두름 바깥 52 / 안쪽 26
        check(near(wl.skill(0).total(), 6.5) && near(wl.skill(0).num("minPower", 0), 0.5) && wl.skill(0).def().numbersOrNull("len").size() == 2
                && near(wl.skill(4).num("outer", 0), 52) && near(wl.skill(4).num("inner", 0) * 2, wl.skill(4).num("outer", 0)), "warlord totals 6.5 · 52 (바깥) · 26 (안쪽)");
        // 글래디에이터: 도끼 난타 여섯 = 6.5 · 여덟 팔 무리 넷 = 11.7 · 홀름강 내려침 + 내려찍기 = 52
        check(near(sum(gl.skill(0), "chops"), 6.5) && gl.skill(0).def().list("chops").size() == 6 && near(sum(gl.skill(2), "volleys"), 11.7)
                && gl.skill(2).def().list("volleys").size() * gl.skill(2).num("lines", 0) == 16 && near(gl.skill(4).num("open", 0) + gl.skill(4).num("slam", 0), 52),
                "gladiator totals 6.5 (도끼질 여섯) · 11.7 (숫자 열여섯 줄) · 52");
        // 같은 적을 여러 번 치는 것은 10틱 넘게 (다시 누르는 단계는 gap — 다시 누를 수 있는 가장 이른 틱)
        check(gl.skill(0).num("groupGap", 0) >= 10 && gl.skill(2).num("every", 0) >= 10 && gl.skill(4).num("dur", 0) - gl.skill(4).num("openAt", 0) >= 10
                && gl.skill(1).num("hitAt", 0) > gl.skill(1).num("pullTicks", 0), "gladiator i-frame spacing");
        check(gl.skill(2).num("lines", 0) * gl.skill(2).num("lineGap", 0) <= gl.skill(2).num("every", 0)
                && gl.skill(0).num("group", 0) * gl.skill(0).num("every", 0) <= gl.skill(0).num("groupGap", 0), "연타 숫자 줄이 다음 무리 전에 끝남");
        check(wl.skill(0).num("gap", 0) >= 6 && wl.skill(0).num("charge", 0) > 12 && wl.skill(2).num("slamAt", 0) > wl.skill(2).num("pullAt", 0) + wl.skill(2).num("pullTicks", 0),
                "warlord timing order");
        // 마지막 피해 틱 = t (RpgJobTimingTests 와 같은 값 — 다시 누르는 스킬은 12틱마다 다시 누름: 대지 가르기는 12틱 모아 내려침 → slamAt 뒤)
        check(near(12 + wl.skill(0).num("slamAt", 0), wl.skill(0).num("t", 0)) && near(wl.skill(0).num("w", 0), wl.skill(0).num("t", 0)), "대지 가르기 t 16 = 12 + 4");
        check(near(wl.skill(1).num("dur", 0), wl.skill(1).num("t", 0)) && near(wl.skill(1).num("w", 0), wl.skill(1).num("t", 0)), "동면 t 40 = 선딜");
        check(near(wl.skill(2).num("slamAt", 0), wl.skill(2).num("t", 0)) && near(wl.skill(2).num("w", 0), wl.skill(2).num("t", 0)), "곰의 포옹 t 12");
        check(near(wl.skill(4).num("windup", 0), wl.skill(4).num("t", 0)) && near(wl.skill(4).num("w", 0), wl.skill(4).num("t", 0)), "마지막 휘두름 t 24 = 선딜");
        check(near(gl.skill(0).num("first", 0) + (gl.skill(0).def().list("chops").size() / (int) gl.skill(0).num("group", 1) - 1) * gl.skill(0).num("groupGap", 0),
                gl.skill(0).num("t", 0)) && !gl.skill(0).def().has("w"), "도끼 난타 t 12 (둘째 무리)");
        check(near(gl.skill(1).num("hitAt", 0), gl.skill(1).num("t", 0)) && near(gl.skill(1).num("w", 0), gl.skill(1).num("t", 0)), "걸어 당기기 t 6");
        check(near(gl.skill(2).num("first", 0) + (gl.skill(2).def().list("volleys").size() - 1) * gl.skill(2).num("every", 0), gl.skill(2).num("t", 0)), "여덟 팔 t 34");
        check(near(gl.skill(4).num("dur", 0), gl.skill(4).num("t", 0)) && near(gl.skill(4).num("openAt", 0), gl.skill(4).num("w", 0)), "홀름강 t 140 · w 10");
        // 특성: 워로드 = 전사의 분노 + 곰의 화신 (분노 100 · 8초 · −15%), 글래디에이터 = 결투 (최대 10 · 1초마다 +3% · 다른 적 −40% · 쓰러지면 절반 · 5초)
        check(wl.trait().name().equals("분노") && wl.trait().max() == 100 && near(wl.trait().def().numOr("bearFor", 0), 160) && near(wl.trait().def().numOr("bearGuard", 0), 0.15)
                && near(wl.trait().def().numOr("onHit", 0), wa.trait().def().numOr("onHit", -1)), "분노 · 곰의 화신");
        check(gl.trait().name().equals("결투") && gl.trait().max() == 10 && near(gl.trait().def().numOr("per", 0), 0.03) && near(gl.trait().def().numOr("every", 0), 20)
                && near(gl.trait().def().numOr("others", 0), 0.4) && near(gl.trait().def().numOr("keep", 0), 0.5) && near(gl.trait().def().numOr("lapse", 0), 100)
                && near(gl.trait().max() * gl.trait().def().numOr("per", 0), 0.3), "결투 10 · +3% (최대 +30%) · −40% · 절반 · 5초");
        // 유틸 · 범위: 대지 가르기 띄움 · 동면 피해 감소 + 밀쳐냄 · 곰의 포옹 끌당 + 기절 1초 · 마지막 휘두름 피해 감소 + 띄움 / 걸어 당기기 끌당 + 기절 0.5초 · 여덟 팔 적 받는 피해
        // +10% · 홀름강 가둠 + 적 받는 피해 +15% — 글래디에이터는 모두 단일 (범위 보정 +15%)
        check(Jobs.utils(wl.skill(0)).equals(List.of("lift")) && "narrow".equals(Jobs.areaKind(wl.skill(0))) && Jobs.windupTicks(wl.skill(0)) == 16, "대지 가르기 util · narrow · w 16");
        check(Jobs.utils(wl.skill(1)).equals(List.of("reduce", "knock")) && near(Jobs.utilCost(wl.skill(1)), 0.15) && near(Jobs.windupBonus(wl.skill(1)), 0.3)
                && near(wl.skill(1).num("guard", 0), 0.6), "동면 reduce · knock · 선딜 +30% (상한)");
        check(Jobs.utils(wl.skill(2)).equals(List.of("pull", "stun1")) && near(wl.skill(2).num("stun", 0), 20) && Jobs.areaKind(wl.skill(2)) == null, "곰의 포옹 pull · stun1");
        check(Jobs.utils(wl.skill(4)).equals(List.of("reduce", "lift")) && near(wl.skill(4).num("guard", 0), 0.5) && Jobs.windupTicks(wl.skill(4)) == 24, "마지막 휘두름 −50% · w 24");
        for (int i : new int[] {0, 1, 2, 4}) check("single".equals(Jobs.areaKind(gl.skill(i))), "gladiator single " + gl.skill(i).id());
        check(Jobs.utils(gl.skill(1)).equals(List.of("pull", "stun05")) && Jobs.utils(gl.skill(2)).equals(List.of("vuln10")) && near(gl.skill(2).num("break", 0), 0.1)
                && Jobs.utils(gl.skill(4)).equals(List.of("taunt", "vuln15")) && near(gl.skill(4).num("vuln", 0), 0.15) && Jobs.utils(gl.skill(0)).isEmpty(), "gladiator utils");
        check(Jobs.timeKind(gl.skill(4)).equals("long") && near(Jobs.timeMult(gl.skill(4)), 1.1 * 1.1) && near(Jobs.timeMult(gl.skill(0)), 1.05), "홀름강 지속 +10% · 선딜 +10%");
        // 칸 이름: 단일 직업의 3번 칸은 주력 (재설계안 2판 기준 8) · 나머지는 그대로
        check(Jobs.slotRole(gl.skill(2)).equals("주력") && Jobs.slotRole(wl.skill(2)).equals("광역") && Jobs.slotRole(gl.skill(0)).equals(Jobs.slotRole(0)),
                "slot role 주력 for single slot 3");
        check(Jobs.upKeys(wl.skill(3)).size() == 2 && Jobs.upKeys(gl.skill(3)).size() == 2 && Jobs.upKey(wl.skill(2), "stun") != null && Jobs.upKey(gl.skill(2), "break") != null,
                "up keys");
        check(Jobs.skillLine(wl.skill(3)).equals("타이탄 포스 · 30초") && Jobs.skillLine(gl.skill(3)).equals("이모탈 레이지 · 30초")
                && Jobs.skillLine(gl.skill(0)).startsWith("데들리 플러리 · 6초 · 배율 "), "warrior advanced skill line");
        check(wl.color()[0][0] > 0.9 && gl.color()[0][0] > 0.9 && gl.color()[0][1] < 0.4 && gl.color()[1][2] > 0.5, "colors: orange · brown / scarlet · sand");
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
                // 0.50.0 범위 보정: a = single (+15%) · narrow (+8%) · 없음 — 직업 맞춤 밖에서 곱함
                String a = Jobs.areaKind(k);
                check(a == null || a.equals("single") || a.equals("narrow"), k.id() + " area kind " + a);
                double am = a == null ? 1 : a.equals("single") ? 1.15 : 1.08;
                check(near(Jobs.areaMult(k), am), k.id() + " area mult " + Jobs.areaMult(k));
                check(near(Jobs.damageScale(k), (1 - Jobs.utilCost(k)) * tm * Jobs.jobNorm(j) * am), k.id() + " scale = util × time × norm × area");
                check(Jobs.jobOf(k) == j, k.id() + " job");
                was += k.total() * Jobs.spreadMult(k) / k.cd();
                now += k.total() * Jobs.damageScale(k) / Jobs.areaMult(k) / k.cd();
            }
            check(near(was, now), j.id() + " job damage per second kept (without the area bonus) " + was + " → " + now);
            double n = Jobs.jobNorm(j);
            check(n > 0.9 && n < 1.6, j.id() + " norm " + n);
        }
        check(names == 55, "55 skill names " + names);
        // 하나씩: 라그나로크 (0.50.0 발할라를 바꿈 — 선딜 6틱: 돌진해 첫 베기 +6% · 지속 44틱 +10% → ×1.166 · 유틸 없음)
        Jobs.Skill ragnarok = sw.skill(4);
        check(ragnarok.name().equals("블레이드 아포칼립스") && Jobs.windupTicks(ragnarok) == 6 && "long".equals(Jobs.timeKind(ragnarok)), "라그나로크 windup · long");
        check(near(Jobs.timeMult(ragnarok), 1.06 * 1.1) && !Jobs.timeCapped(ragnarok) && near(Jobs.utilCost(ragnarok), 0), "라그나로크 time 1.166");
        // 요르문간드의 독니: S 자로 휘감아 지나감 (순간 이동 값 −10%) · 독 2초 (지속 43틱 +10%)
        Jobs.Skill fang = Jobs.job("assassin").skill(2);
        check(fang.name().equals("바이퍼 팽") && Jobs.utils(fang).equals(List.of("blink")) && near(Jobs.utilCost(fang), 0.10) && near(Jobs.timeMult(fang), 1.1),
                "요르문간드의 독니 blink 10% · long");
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
        check(Jobs.skillLine(sw.skill(0)).startsWith("트리니티 엣지 · 6초 · 배율 "), "skill line " + Jobs.skillLine(sw.skill(0)));
        check(sw.trait().name().equals("투지") && Jobs.job("reaper").trait().name().equals("영혼"), "trait names 투지 · 영혼");
        check(near(Jobs.utilCost(pr.skill(0)), 0) && near(Jobs.damageScale(pr.skill(0)), 1), "heal (no damage) scale 1");
        check(Jobs.utilName("stun07").equals("기절 0.7초") && near(utils.obj("undying").num("cost"), 0.2), "job utils in the table");
        check(sb.skill(2).name().equals("블레이드 왈츠") && near(Jobs.utilCost(sb.skill(2)), 0.10), "푸타르크 검무 룬 결계 10%");
        // 0.50.0 범위 보정 (단일 +15% · 좁은 범위 +8% — 직업 맞춤 밖): 단일 셋 (별빛 마탄 · 질풍 연사 · 검은 장미) · 좁은 범위 18 · 나머지 그대로
        Jobs.Job mg = Jobs.job("mage");
        Jobs.Job ar = Jobs.job("archer");
        Jobs.Job as = Jobs.job("assassin");
        Jobs.Job la = Jobs.job("lancer");
        check("single".equals(Jobs.areaKind(mg.skill(0))) && "single".equals(Jobs.areaKind(ar.skill(0))) && "single".equals(Jobs.areaKind(as.skill(0))),
                "단일: 별빛 마탄 · 질풍 연사 · 검은 장미");
        check("narrow".equals(Jobs.areaKind(la.skill(4))) && "narrow".equals(Jobs.areaKind(sw.skill(1))) && Jobs.areaKind(sw.skill(2)) == null
                && Jobs.areaKind(sw.skill(4)) == null && Jobs.areaKind(ar.skill(4)) == null, "좁은 범위: 흐레스벨그의 발톱 · 섬광 질주 / 만월 베기 · 라그나로크 · 솔의 화살 그대로");
        int singles = 0, narrows = 0;
        for (Jobs.Job j : Jobs.all()) {
            for (Jobs.Skill k : j.skills()) {
                if ("single".equals(Jobs.areaKind(k))) singles++;
                if ("narrow".equals(Jobs.areaKind(k))) narrows++;
                if (k.total() <= 0) check(Jobs.areaKind(k) == null && !k.def().has("a"), k.id() + " no damage → no area");
            }
        }
        check(singles == 3 && narrows == 18, "area kinds 3 single · 18 narrow: " + singles + " · " + narrows);
        String bolts = Jobs.scaleNote(mg.skill(0));
        check(bolts != null && bolts.contains("단일 +15%"), "별빛 마탄 note " + bolts);
        String claw = Jobs.scaleNote(la.skill(4));
        check(claw != null && claw.contains("좁은 범위 +8%") && claw.contains("시간 +17%"), "흐레스벨그의 발톱 note " + claw);
    }
}
