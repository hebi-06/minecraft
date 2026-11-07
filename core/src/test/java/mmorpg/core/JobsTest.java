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
        check(withUp == 16, "16 skills with up keys " + withUp);
        // 0.51.0 4번 칸 (태세 · 강화) 을 피해 스킬로 바꾼 직업 (사용자 "직업 중에 구지 강화 시키면 어색한 애들은 빼자"): 배율 합 = 태세 칸 몫 12 × 세기 (검사 기준
        // 초당 0.4 · 30초) · 직업 맞춤 밖 (budget stance) · 강화 열쇠 없음 (위력 P 가 오름)
        int stanceDamage = 0;
        List<Jobs.Job> every = new ArrayList<>(Jobs.all());
        every.addAll(Jobs.allAdvanced());
        for (Jobs.Job j : every) {
            Jobs.Skill k = j.skill(3);
            check(Jobs.stanceBudget(k) == (k.total() > 0), j.id() + " slot 3 damage ↔ budget stance");
            for (int i : new int[] {0, 1, 2, 4}) check(!Jobs.stanceBudget(j.skill(i)), j.id() + " budget stance only in slot 3");
            if (k.total() <= 0) continue;
            stanceDamage++;
            check(near(k.total(), 12 * j.scale()) && k.cd() == 30 && Jobs.upKeys(k).isEmpty() && !k.def().has("dur"), j.id() + " stance budget " + k.total() + " = 12 × "
                    + j.scale());
        }
        check(stanceDamage == 9, "nine slot-3 damage skills (매지션 · 마검사 · 워로드 · 아케인 블레이드 · 아크메이지 · 네크로맨서 · 피에로 · 스톰 랜서 · 드라군 — 2026-11-07 글래디에이터의 액스 그라인더가 요툰 그립 태세로 · 아크메이지의 스펠 서지가 셀레스티얼 오빗으로 · 소울 리퍼의 랜턴 오브 소울즈가 스펙트럴 사이스 태세로) "
                + stanceDamage);

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
        check(Jobs.upLines(Jobs.job("archer").skill("archer_focus"), 0, 1).equals(List.of("늑대 물어뜯기 40% → 44%")), "wild hunt lines: " + Jobs.upLines(Jobs.job("archer").skill("archer_focus"), 0, 1));
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
        // 스펠 가이저 (0.51.0 — 스펠 인챈트 대신): 룬 원 + 마력 기둥 = 태세 칸 몫 12 × 1.7 · 기둥은 12틱 뒤 (무적 시간)
        check(near(sb.skill(3).num("circle", 0) + sb.skill(3).num("pillar", 0), 20.4) && sb.skill(3).num("eruptAt", 0) - sb.skill(3).num("plantAt", 0) >= 10
                && near(sb.skill(3).num("plantAt", 0), sb.skill(3).num("w", 0)) && near(sb.skill(3).num("eruptAt", 0), sb.skill(3).num("t", 0))
                && Jobs.utils(sb.skill(3)).equals(List.of("root", "lift")), "스펠 가이저 6 + 14.4 · w 6 · t 18");
        // 프리즘 레이 (0.51.0 — 마나 오버로드 대신): 광선 넷 (10틱마다) + 다섯 갈래 = 12 × 1.4
        check(near(sum(mg.skill(3), "beam") + mg.skill(3).num("split", 0), 16.8) && mg.skill(3).num("every", 0) >= 10
                && mg.skill(3).num("splitAt", 0) - mg.skill(3).num("first", 0) - (mg.skill(3).def().list("beam").size() - 1) * mg.skill(3).num("every", 0) >= 10
                && near(mg.skill(3).num("first", 0), mg.skill(3).num("w", 0)) && near(mg.skill(3).num("splitAt", 0), mg.skill(3).num("t", 0)), "프리즘 레이 2.4 × 4 + 7.2 · w 8 · t 48");
        check(sw.skill(1).cooldownTicks() == 240 && sb.skill(4).cooldownTicks() == 1200, "cooldown ticks");

        // 0.28.0 전사 · 매지션 · 궁수: 부분 합 = 배율 합
        check(near(wa.skill(0).num("mult", 0), 6.5) && near(wa.skill(1).num("ram", 0) + wa.skill(1).num("slam", 0), 3.9), "파쇄격 6.5 · 돌격 1.9 + 2.0");
        check(near(wa.skill(2).num("hit", 0) * wa.skill(2).num("hits", 0), 11.7), "대회전 2.34 × 5");
        check(near(wa.skill(4).num("leap", 0) * wa.skill(4).num("leaps", 0) + wa.skill(4).num("final", 0), 52), "격노 도약 7 × 4 + 24");
        check(near(mg.skill(0).num("bolt", 0) * mg.skill(0).num("bolts", 0), 7) && near(mg.skill(1).num("blast", 0), 4.2), "마탄 연사 1.4 × 5 · 위상 전환 4.2");
        check(near(mg.skill(2).num("pulse", 0) * mg.skill(2).num("pulses", 0) + mg.skill(2).num("burst", 0), 12.6), "비전 구체 1.4 × 6 + 4.2");
        check(near(mg.skill(4).num("page", 0) * mg.skill(4).num("pages", 0) + mg.skill(4).num("final", 0), 56)
                && near(mg.skill(4).num("pages", 0) % mg.skill(4).num("waves", 1), 0), "금서 개방 3 × 12 + 20 (물결마다 같은 장 수)");
        check(near(ar.skill(0).num("arrow", 0) * ar.skill(0).num("arrows", 0), 7.5) && ar.skill(0).num("more", 0) == 4, "속사 2.5 × 3 (교감 3 이상 4발)");
        check(near(ar.skill(2).num("wave", 0) * ar.skill(2).num("waves", 0), 13.5) && ar.skill(2).num("per", 0) == 2, "화살비 2.7 × 5 (두 발씩)");
        check(near(ar.skill(4).num("shot", 0) + ar.skill(4).num("after", 0), 60) && ar.skill(4).name().equals("스피릿 스탬피드") && ar.skill(3).name().equals("와일드 헌트")
                && near(ar.skill(3).num("bite", 0), 0.4) && ar.skill(3).num("wolves", 0) == 2 && ar.trait().name().equals("정령 교감"), "스피릿 스탬피드 40 + 20 · 와일드 헌트 늑대 둘 40% · 정령 교감 (2026-11-07 3판)");
        // 같은 적을 여러 번 치는 것은 10틱 넘게 (빠른 연타는 한 번에 넣고 숫자만 여러 줄 — 마탄 연사 · 속사 · 화살비 한 물결 · 금서의 주문)
        check(wa.skill(2).num("gap", 0) >= 10 && wa.skill(4).num("leapTicks", 0) >= 10 && wa.skill(4).num("finalDelay", 0) >= 10, "warrior i-frame spacing");
        check(mg.skill(2).num("gap", 0) >= 10 && mg.skill(2).num("dur", 0) - mg.skill(2).num("gap", 0) * mg.skill(2).num("pulses", 0) >= 10
                && mg.skill(4).num("gap", 0) >= 10 && mg.skill(4).num("finalDelay", 0) >= 10, "mage i-frame spacing");
        check(ar.skill(2).num("gap", 0) >= 10 && ar.skill(4).num("bloomDelay", 0) >= 10, "archer i-frame spacing");
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

    /** 직업 초당 피해 (피해 스킬의 배율 합 × 피해 배율 ÷ 재사용 대기 — 범위 보정 · 2026-11-07 원소 배율 (원소 스킬 −5%) 은 직업 맞춤 밖이라 뺌) */
    static double dps(Jobs.Job j) {
        double sum = 0;
        for (Jobs.Skill k : j.skills())
            if (k.total() > 0 && k.cd() > 0 && !Jobs.stanceBudget(k)) sum += k.total() * Jobs.damageScale(k) / Jobs.areaMult(k) / Jobs.elementMult(k) / k.cd();
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
        Jobs.Job fr = Jobs.job("frostraider");
        check(sm != null && bk != null && wl != null && fr != null && Jobs.allAdvanced().size() == 22 && Jobs.job("lycan") == null && Jobs.job("gladiator") == null,
                "advanced jobs " + Jobs.allAdvanced().size());
        if (sm == null || bk == null || wl == null || fr == null) return;
        check(Jobs.advanceLevel() == 50 && near(Jobs.switchSilver(), 600) && near(Jobs.advanceDps(), 1.2), "advance rules (50렙 · 은화 600 · ×1.2)");
        check(Jobs.advancedOf("swordsman").equals(List.of(sm, bk)) && Jobs.advancedOf("warrior").equals(List.of(wl, fr)) && Jobs.advancedOf("spellblade").size() == 2
                && Jobs.advancedOf("mage").size() == 2 && Jobs.advancedOf("archer").size() == 2 && Jobs.advancedOf("assassin").size() == 2
                && Jobs.advancedOf("lancer").size() == 2 && Jobs.advancedOf("reaper").size() == 2 && Jobs.advancedOf("paladin").size() == 2
                && Jobs.advancedOf("priest").size() == 2 && Jobs.advancedOf("guardian").size() == 2 && Jobs.advancedOf("nope").isEmpty(), "advancedOf (각성 · 반전 순서 — 열한 직업 모두)");
        check(Jobs.advance("warrior", "awaken") == wl && Jobs.advance("warrior", "invert") == fr && Jobs.baseOf(wl) == wa && Jobs.baseOf(fr) == wa, "warrior advance");
        check(wl.display().equals("워로드") && fr.display().equals("프로스트 레이더") && "awaken".equals(wl.path()) && "invert".equals(fr.path()),
                "warrior display");
        check(Jobs.find("워로드") == wl && Jobs.find("스타르카드") == null && Jobs.find("frostraider") == fr && Jobs.find("프로스트 레이더") == fr
                && Jobs.find("글래디에이터") == null, "find warrior advanced");
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
                // 4번 칸 (태세) 은 강화 스킬 · 태세 칸 몫 피해 스킬 (budget stance) 이 섞임 — 0.51.0 사용자 "딜링기도 괜찮고 버프기도 괜찮고"
                // 2026-11-06 워록은 역할이 바뀐 반전 (힐러 → 원거리 딜러 — 재설계안 2판 기준 5: 새 역할의 목표) 이라 사제의 회복 · 보호막 칸에도 피해
                check((k.total() > 0) == (b.skill(i).total() > 0) || i == 3 || a.id().equals("warlock"), a.id() + " slot " + i + " same role (damage · stance)");
                check(Jobs.upKeys(k).size() == Jobs.upKeys(b.skill(i)).size() || i == 3, a.id() + " slot " + i + " up keys");
            }
            double r = dps(a) / dps(b);
            check(near(r, Jobs.advanceDps(a), 0.005), a.id() + " dps ×" + r + " (1차 × " + Jobs.advanceDps(a) + " — 피에로 · 엑시큐셔너 1.0)");
        }
        check(Jobs.upId("swordsman_flurry").equals("swordsman_flurry") && Jobs.upId(null) == null, "upId keeps first-job ids");
        // 0.51.0 재설계안 2판 (claude/job-advance-redo.md) — 부분 합 = 배율 합: 칼집 섬광 · 받아넘기기는 한 번에 넣고 숫자만 여러 줄, 불의 울타리 가로지르기 다섯,
        // 스타폴 블레이드 혜성 여섯 + 마지막 낙하 / 핏빛 대검 세 번, 피의 사슬 박음 + 끌어당김, 블러드 문 빨아들임 + 초승달 검기 + 피의 기둥
        // (사용자 "검사 궁 2개 다른걸로 바꾸자 멋이 안난다")
        check(near(sm.skill(0).total(), 5) && sm.skill(0).num("lines", 0) >= 2 && near(sm.skill(1).total(), 3) && near(sm.skill(2).total(), 9)
                && "swordmaster_echo".equals(sm.skill(2).id()) && sm.skill(2).num("lines", 0) >= 2
                && near(sm.skill(4).num("beam", 0) + sm.skill(4).num("after", 0), 40) && sm.skill(4).name().equals("엑스칼리버"),
                "swordmaster totals 5 · 3 · 9 (블레이드 에코 — 한 번에) · 40 (엑스칼리버 28 + 12)");
        check(near(sum(bk.skill(0), "casts"), 5) && near(bk.skill(1).total(), 3) && near(bk.skill(2).num("bind", 0) + bk.skill(2).num("pull", 0), 9)
                && near(bk.skill(4).num("drain", 0) + bk.skill(4).num("crescent", 0) + bk.skill(4).num("pillar", 0), 40), "bloodknight totals 5 · 3 · 9 · 40 (8 + 20 + 12)");
        // 같은 적을 여러 번 치는 것은 10틱 넘게 (다시 누르는 단계는 gap — 다시 누를 수 있는 가장 이른 틱 · JobRecast)
        List<Double> cleaveAt = bk.skill(0).def().numbersOrNull("hitAt");
        check(cleaveAt != null && cleaveAt.size() == 3 && bk.skill(0).num("gap", 0) + cleaveAt.get(1) - cleaveAt.get(0) >= 10
                && bk.skill(0).num("gap", 0) + cleaveAt.get(2) - cleaveAt.get(1) >= 10 && bk.skill(0).num("window", 0) > bk.skill(0).num("gap", 0), "핏빛 대검 다시 누름 간격");
        // 엑스칼리버 (2026-11-07 3판): 검격 → 빛의 검 10틱 넘게 / 블러드 문: 빨아들임 → 검기 → 기둥 10틱 넘게 (기둥은 검기만큼 빨리 번짐 — 간격이 줄지 않음)
        Jobs.Skill sf = sm.skill(4);
        check(sf.num("afterAt", 0) - sf.num("hitAt", 0) >= 10 && sf.num("hitAt", 0) > sf.num("swingAt", 0), "엑스칼리버 간격");
        Jobs.Skill bm = bk.skill(4);
        check(bm.num("slamAt", 0) - bm.num("drainAt", 0) >= 10 && bm.num("pillarAt", 0) - bm.num("slamAt", 0) >= 10 && bm.num("pillarSpeed", 0) * 20 >= bm.num("speed", 0),
                "블러드 문 간격");
        check(bk.skill(2).num("delay", 0) >= 10, "bloodknight i-frame spacing (블레이드 에코는 한 번에)");
        // 마지막 피해 틱 = t (RpgJobTimingTests 와 같은 값 — 다시 누르는 스킬은 12틱마다 다시 누름)
        check(near(sm.skill(0).num("sheath", 0), sm.skill(0).num("t", 0)) && near(sm.skill(1).num("window", 0), sm.skill(1).num("t", 0)), "칼집 섬광 t 6 · 받아넘기기 t 10");
        check(near(sm.skill(2).num("sheath", 0), sm.skill(2).num("t", 0)) && near(sm.skill(2).num("sheath", 0), sm.skill(2).num("w", 0)), "블레이드 에코 w · t 26 (칼집에 넣는 틱)");
        check(near(24 + cleaveAt.get(2), bk.skill(0).num("t", 0)) && near(bk.skill(1).num("leap", 0), bk.skill(1).num("t", 0)), "핏빛 대검 t 31 · 피의 돌진 t 6");
        check(near(bk.skill(2).num("first", 0) + bk.skill(2).num("delay", 0) + 2, bk.skill(2).num("t", 0)), "피의 사슬 t 36");
        check(near(sf.num("afterAt", 0), sf.num("t", 0)) && near(sf.num("hitAt", 0), sf.num("w", 0)), "엑스칼리버 w 26 · t 37 (검격의 무적 시간 10틱이 끝난 다음 틱)");
        // 블러드 문: 앞 2.5칸 과녁 — 1칸 앞에 내려앉아 0.8칸 앞에서 검기 · 기둥 시작 → 과녁까지 0.7칸 = 1틱
        check(near(bm.num("drainAt", 0), bm.num("w", 0)) && near(bm.num("pillarAt", 0) + 1, bm.num("t", 0)), "블러드 문 w 10 · t 42");
        // 특성
        check(sm.trait().name().equals("투지") && sm.trait().max() == 8 && near(sm.trait().def().numOr("crit", 0), 0.3), "투지 최대 8 · 치명 +30%");
        check(bk.trait().name().equals("피의 갈증") && bk.trait().max() == 5 && near(bk.trait().def().numOr("cost", 0), 0.04) && near(bk.trait().def().numOr("leech", 0), 0.05)
                && near(bk.trait().def().numOr("thirstAt", 0), 0.3) && bk.trait().def().numOr("thirstSpeed", 0) > 0 && bk.trait().def().numOr("leechMax", 0) > 0,
                "피의 갈증 체력 4% · 흡혈 5% · 체력 30% 에서 가장 목마름");
        check(Jobs.skillLine(sm.skill(0)).startsWith("라이트닝 드로우 · 6초 · 배율 ") && Jobs.skillLine(bk.skill(3)).equals("새비지 프렌지 · 30초"), "skill line " + Jobs.skillLine(sm.skill(0)));
        warriorAdvance(wa, wl, fr);
        spellbladeAdvance(Jobs.job("spellblade"), Jobs.job("arcaneblade"), Jobs.job("swordcaller"));
        mageAdvance(Jobs.job("mage"), Jobs.job("arcmage"), Jobs.job("necromancer"));
        archerAdvance(Jobs.job("archer"), Jobs.job("sniper"), Jobs.job("battlearcher"));
        assassinAdvance(Jobs.job("assassin"), Jobs.job("trickster"), Jobs.job("pierrot"));
        lancerAdvance(Jobs.job("lancer"), Jobs.job("stormlancer"), Jobs.job("dragoon"));
        reaperAdvance(Jobs.job("reaper"), Jobs.job("soulreaper"), Jobs.job("executioner"));
        paladinAdvance(Jobs.job("paladin"), Jobs.job("thunderlord"), Jobs.job("tyrant"));
        priestAdvance(Jobs.job("priest"), Jobs.job("saint"), Jobs.job("warlock"));
        guardianAdvance(Jobs.job("guardian"), Jobs.job("phoenix"), Jobs.job("darkknight"));
    }

    /**
     * 2026-11-06 수호기사 2차 — 피닉스 가드 · 다크 나이트 (다시 — 사용자 "레인보우 컨셉이 개짜치고 반전도 이렇다할 멋이 없음" → 고른 것: 푸른 불사조 · 타락한 흑기사):
     * 부분 합 = 배율 합 (수호기사와 같은 4 · 2.4 · 7.2 · 32 — 칸 틀 5 · 3 · 9 · 40 × 세기 0.8) · 무적 시간 띄움 · t · w · 특성 (반격 = 1차 + 불사조의 재 20% / 탐식 —
     * 물면 +12 · 평타 +4 · 가득이면 삼킴 최대 체력 8% (위력 × 6 까지) · 회복 6%) · 초당 피해 (피닉스 가드 × 1.2 · 다크 나이트 × 1.3 — 근접 딜러 목표 1.20 ÷ 탱커 0.92 ×
     * 1.2 ≈ 1.565 에서 삼킴 덤 평균 +20% 쯤을 뺌)
     */
    static void guardianAdvance(Jobs.Job gu, Jobs.Job ph, Jobs.Job dk) {
        check(ph != null && dk != null, "guardian advanced jobs");
        if (ph == null || dk == null) return;
        check(Jobs.advance("guardian", "awaken") == ph && Jobs.advance("guardian", "invert") == dk && Jobs.baseOf(ph) == gu && Jobs.baseOf(dk) == gu
                && Jobs.advancedOf("guardian").equals(List.of(ph, dk)), "guardian advance");
        check(ph.display().equals("피닉스 가드") && dk.display().equals("다크 나이트") && Jobs.find("피닉스 가드") == ph && Jobs.find("다크 나이트") == dk
                && Jobs.find("darkknight") == dk && Jobs.find("센티널") == null && Jobs.find("어벤저") == null && Jobs.find("sentinel") == null
                && Jobs.find("avenger") == null, "guardian display · find");
        List<String> phIds = new ArrayList<>();
        List<String> dkIds = new ArrayList<>();
        for (Jobs.Skill k : ph.skills()) phIds.add(k.id());
        for (Jobs.Skill k : dk.skills()) dkIds.add(k.id());
        check(phIds.equals(List.of("phoenix_bash", "phoenix_wing", "phoenix_inferno", "phoenix_ignition", "phoenix_ult"))
                && dkIds.equals(List.of("darkknight_crunch", "darkknight_lunge", "darkknight_feast", "darkknight_frenzy", "darkknight_ult")), "guardian advanced skill ids");
        for (int i : new int[] {0, 1, 2, 4}) check(near(ph.skill(i).total(), gu.skill(i).total()) && near(dk.skill(i).total(), gu.skill(i).total()), "guardian totals slot " + i);
        Jobs.Skill pb = ph.skill(0), pw = ph.skill(1), pi = ph.skill(2), pg = ph.skill(3), pu = ph.skill(4);
        Jobs.Skill dc = dk.skill(0), dl = dk.skill(1), df = dk.skill(2), dz = dk.skill(3), du = dk.skill(4);
        check(near(pb.num("bash", 0), 4) && near(pw.num("slam", 0), 2.4) && near(pi.num("rise", 0) + pi.num("burst", 0), 7.2)
                && near(pu.num("pillar", 0) + pu.num("dive", 0), 32), "phoenix totals 4 · 2.4 · 7.2 (4.8 + 2.4) · 32 (8 + 24)");
        check(near(dc.num("bite", 0) + dc.num("tear", 0), 4) && near(dl.num("bite", 0), 2.4) && near(df.num("pull", 0) + df.num("bite", 0), 7.2)
                && near(du.num("swallow", 0) + du.num("crush", 0), 32), "darkknight totals 4 (1.6 + 2.4) · 2.4 · 7.2 (2.4 + 4.8) · 32 (8 + 24)");
        // 4번 칸: 이그니션 · 언홀리 프렌지 8초 강화 태세 (피해 칸 몫 아님)
        check(!Jobs.stanceBudget(pg) && pg.total() == 0 && near(pg.num("dur", 0), 160) && !Jobs.stanceBudget(dz) && dz.total() == 0 && near(dz.num("dur", 0), 160),
                "이그니션 · 언홀리 프렌지 8초 태세");
        // 초당 피해: 피닉스 가드 = 1차 × 1.2 · 다크 나이트 = 1차 × 1.3 (역할이 바뀜 — 근접 딜러 목표 1.20 ÷ 탱커 0.92 × 1.2 ≈ 1.565 ÷ 삼킴 덤 1.2)
        check(near(Jobs.advanceDps(ph), 1.2) && near(Jobs.advanceDps(dk), 1.3) && near(dps(ph) / dps(gu), 1.2, 0.005) && near(dps(dk) / dps(gu), 1.3, 0.005),
                "phoenix × 1.2 · darkknight × 1.3");
        // 같은 적을 여러 번 치는 것은 10틱 넘게 (블루 인페르노 솟음 → 터짐 · 디센트 불기둥 → 내리꽂힘 · 크런치 물기 → 찢기 (다시 누름 gap) · 피스트 감기 → 물기 · 메이 삼킴 → 터뜨림)
        check(pi.num("burstAt", 0) - pi.num("riseAt", 0) >= 10 && pu.num("diveAt", 0) - pu.num("pillarAt", 0) >= 10
                && dc.num("gap", 0) + dc.num("tearAt", 0) - dc.num("biteAt", 0) >= 10 && df.num("biteAt", 0) - df.num("pullAt", 0) >= 10
                && du.num("crushAt", 0) - du.num("swallowAt", 0) >= 10, "guardian advanced i-frame spacing");
        // 마지막 피해 틱 = t · 첫 피해 = w (RpgJobTimingTests 와 같은 값 — 크런치는 12틱에 다시 누름)
        check(near(pb.num("bashAt", 0), pb.num("t", 0)) && near(pb.num("bashAt", 0), pb.num("w", 0)) && near(pw.num("glide", 0), pw.num("t", 0))
                && near(pw.num("glide", 0), pw.num("w", 0)) && near(pi.num("burstAt", 0), pi.num("t", 0)) && near(pi.num("riseAt", 0), pi.num("w", 0))
                && near(pu.num("diveAt", 0), pu.num("t", 0)) && near(pu.num("pillarAt", 0), pu.num("w", 0)), "phoenix t 6 · 10 · 30 · 40 (w 6 · 10 · 10 · 10)");
        check(near(dc.num("biteAt", 0), dc.num("w", 0)) && near(12 + dc.num("tearAt", 0), dc.num("t", 0)) && dc.num("gap", 0) <= 12
                && near(dl.num("biteAt", 0), dl.num("t", 0)) && near(dl.num("biteAt", 0), dl.num("w", 0)) && near(df.num("biteAt", 0), df.num("t", 0))
                && near(df.num("pullAt", 0), df.num("w", 0)) && near(du.num("crushAt", 0), du.num("t", 0)) && near(du.num("swallowAt", 0), du.num("w", 0))
                && du.num("plantAt", 0) < du.num("swallowAt", 0), "darkknight t 15 · 8 · 16 · 44 (w 4 · 8 · 6 · 26)");
        // 특성: 피닉스 가드 = 수호기사의 반격 (같은 이름 · 쌓임) + 불사조의 재, 다크 나이트 = 탐식
        for (String key : new String[] {"rate", "cap"}) check(near(ph.trait().def().numOr(key, -1), gu.trait().def().numOr(key, -2)), "반격 " + key + " = 1차");
        check(ph.trait().name().equals(gu.trait().name()) && ph.trait().max() == gu.trait().max() && near(ph.trait().def().numOr("ash", 0), 0.2)
                && near(ph.trait().def().numOr("ashRadius", 0), 8) && near(ph.trait().def().numOr("ashCap", 0), 0.15) && near(pg.num("ash", 0), 2)
                && near(pg.num("cut", 0), 0.2), "반격 + 불사조의 재 (막아 준 피해 20% → 가장 위험한 아군 보호막 · 이그니션 두 배 · −20%)");
        check(dk.trait().name().equals("탐식") && dk.trait().max() == 100 && near(dk.trait().def().numOr("bite", 0), 12) && near(dk.trait().def().numOr("basic", 0), 4)
                && near(dk.trait().def().numOr("keep", 0), 160) && near(dk.trait().def().numOr("decay", 0), 10) && near(dk.trait().def().numOr("devour", 0), 0.08)
                && near(dk.trait().def().numOr("devourCap", 0), 6) && near(dk.trait().def().numOr("heal", 0), 0.06) && near(dz.num("bite", 0), 0.5)
                && near(dz.num("speed", 0), 0.2) && near(dz.num("hunger", 0), 2), "탐식 +12 · 평타 +4 · 8초 뒤 10 씩 · 삼킴 8% (× 6) · 회복 6% · 프렌지 × 0.5 · +20% · 두 배");
        // 유틸 · 범위 · 선딜
        check("narrow".equals(Jobs.areaKind(pb)) && "narrow".equals(Jobs.areaKind(pw)) && Jobs.areaKind(pi) == null && Jobs.areaKind(pu) == null
                && Jobs.utils(pb).equals(List.of("taunt", "lift")) && Jobs.utils(pw).equals(List.of("leap", "shield"))
                && Jobs.utils(pi).equals(List.of("taunt", "knock")) && Jobs.utils(pu).equals(List.of("taunt", "shield", "lift")), "phoenix utils · area");
        check("single".equals(Jobs.areaKind(dc)) && "single".equals(Jobs.areaKind(dl)) && Jobs.areaKind(df) == null && Jobs.areaKind(du) == null
                && Jobs.utils(dc).isEmpty() && Jobs.utils(dl).equals(List.of("blink", "stun05")) && Jobs.utils(df).equals(List.of("pull"))
                && Jobs.utils(du).equals(List.of("pull", "stun1")), "darkknight utils · area");
        check(Jobs.windupTicks(pb) == 6 && Jobs.windupTicks(pw) == 10 && Jobs.windupTicks(pi) == 10 && Jobs.windupTicks(pu) == 10 && Jobs.windupTicks(dc) == 0
                && Jobs.windupTicks(dl) == 8 && Jobs.windupTicks(df) == 6 && Jobs.windupTicks(du) == 26, "windups 6 · 10 · 10 · 10 / 0 (4틱은 선딜 아님) · 8 · 6 · 26");
        check(Jobs.skillLine(pb).startsWith("피닉스 배시 · 6초 · 배율 ") && Jobs.skillLine(dz).equals("언홀리 프렌지 · 30초"), "guardian advanced skill line " + Jobs.skillLine(dz));
        check(ph.color()[0][2] > 0.95 && ph.color()[0][0] < 0.4 && ph.color()[1][2] > 0.95 && dk.color()[0][0] > 0.85 && dk.color()[0][1] < 0.2 && dk.color()[1][0] < 0.3,
                "colors: phoenix azure · white / darkknight crimson · black");
    }

    /**
     * 0.51.0 성기사 2차 — 썬더로드 · 타이런트 (재설계안 2판 — 2026-11-06 사용자 "다음 작업": 썬더로드 새 스킬 다섯 · 타이런트는 아군에게 보호를 주는 광역 제어
     * 탱커를 뒤집어 적의 힘을 빼앗아 홀로 커지는 단일 브루저): 부분 합 = 배율 합 (성기사와 같은 5.5 · 3.3 · 9.9 · 44) · 무적 시간 띄움 · t · w · 특성 (심판 —
     * 갈래 벼락 · 아군 보호막 · 오라 −8% / 강탈 — 힘 하나당 +3% · −2% · 크기 +2% · 최대 10) · 초당 피해 (둘 다 × 1.2 — 타이런트는 역할이 바뀌나 강탈 덤을 셈해 그대로)
     */
    static void paladinAdvance(Jobs.Job pa, Jobs.Job tl, Jobs.Job ty) {
        check(tl != null && ty != null, "paladin advanced jobs");
        if (tl == null || ty == null) return;
        check(Jobs.advance("paladin", "awaken") == tl && Jobs.advance("paladin", "invert") == ty && Jobs.baseOf(tl) == pa && Jobs.baseOf(ty) == pa
                && Jobs.advancedOf("paladin").equals(List.of(tl, ty)), "paladin advance");
        check(tl.display().equals("썬더로드") && ty.display().equals("타이런트") && Jobs.find("썬더로드") == tl && Jobs.find("스루드") == null
                && Jobs.find("타이런트") == ty && Jobs.find("트림") == null && Jobs.find("tyrant") == ty, "paladin display · find");
        List<String> tlIds = new ArrayList<>();
        List<String> tyIds = new ArrayList<>();
        for (Jobs.Skill k : tl.skills()) tlIds.add(k.id());
        for (Jobs.Skill k : ty.skills()) tyIds.add(k.id());
        check(tlIds.equals(List.of("thunderlord_arc", "thunderlord_step", "thunderlord_spire", "thunderlord_head", "thunderlord_ult"))
                && tyIds.equals(List.of("tyrant_pillage", "tyrant_shackle", "tyrant_grip", "tyrant_colossus", "tyrant_ult")), "paladin advanced skill ids");
        for (int i : new int[] {0, 1, 2, 4}) check(near(tl.skill(i).total(), pa.skill(i).total()) && near(ty.skill(i).total(), pa.skill(i).total()), "paladin totals slot " + i);
        Jobs.Skill ta = tl.skill(0), ts = tl.skill(1), tp = tl.skill(2), th = tl.skill(3), tu = tl.skill(4);
        Jobs.Skill yp = ty.skill(0), ys = ty.skill(1), yg = ty.skill(2), yc = ty.skill(3), yu = ty.skill(4);
        check(near(ta.num("sweep", 0) + ta.num("fork", 0), 5.5) && near(ts.num("strike", 0), 3.3) && near(tp.num("pillar", 0) + tp.num("wave", 0), 9.9)
                && near(tu.num("chains", 0) + tu.num("dawn", 0) + tu.num("shatter", 0), 44), "thunderlord totals 5.5 (2.5 + 3) · 3.3 · 9.9 (6.6 + 3.3) · 44 (8 + 12 + 24)");
        check(near(yp.num("smash", 0), 5.5) && near(ys.num("slam", 0), 3.3) && near(yg.num("squeeze", 0) * yg.num("squeezes", 0) + yg.num("slam", 0), 9.9)
                && near(yu.num("drain", 0) * yu.num("drains", 0) + yu.num("slam", 0), 44), "tyrant totals 5.5 · 3.3 · 9.9 (1.65 × 3 + 4.95) · 44 (4 × 3 + 32)");
        // 4번 칸: 썬더헤드 · 콜로서스 둘 다 강화 태세 (피해 없음 · 10초 — 성기사 수호의 맹세와 같은 칸)
        check(!Jobs.stanceBudget(th) && th.total() == 0 && near(th.num("dur", 0), 200) && !Jobs.stanceBudget(yc) && yc.total() == 0 && near(yc.num("dur", 0), 200),
                "썬더헤드 · 콜로서스 10초 태세");
        // 초당 피해: 둘 다 1차 × 1.2 (타이런트 — 브루저 목표 1.08 ÷ 탱커 0.92 × 1.2 ≈ 1.41 에서 강탈 덤 평균 +18% 쯤을 뺌)
        check(near(Jobs.advanceDps(tl), 1.2) && near(Jobs.advanceDps(ty), 1.2) && near(dps(tl) / dps(pa), 1.2, 0.005) && near(dps(ty) / dps(pa), 1.2, 0.005),
                "thunderlord · tyrant × 1.2");
        // 같은 적을 여러 번 치는 것은 10틱 넘게
        check(ta.num("forkAt", 0) - ta.num("swingAt", 0) >= 10 && tp.num("waveAt", 0) - tp.num("strikeAt", 0) >= 10 && tu.num("dawnAt", 0) - tu.num("chainAt", 0) >= 10
                && tu.num("shatterAt", 0) - tu.num("dawnAt", 0) >= 10 && yg.num("every", 0) >= 10 && yg.num("slamAt", 0) - yg.num("first", 0)
                - (yg.num("squeezes", 0) - 1) * yg.num("every", 0) >= 10 && yu.num("every", 0) >= 10 && yu.num("slamAt", 0) - yu.num("first", 0)
                - (yu.num("drains", 0) - 1) * yu.num("every", 0) >= 10, "paladin advanced i-frame spacing");
        // 마지막 피해 틱 = t · 첫 피해 = w (RpgJobTimingTests 와 같은 값)
        check(near(ta.num("forkAt", 0), ta.num("t", 0)) && near(ta.num("swingAt", 0), ta.num("w", 0)) && near(ts.num("vanish", 0), ts.num("t", 0))
                && near(ts.num("vanish", 0), ts.num("w", 0)) && near(tp.num("waveAt", 0), tp.num("t", 0)) && near(tp.num("strikeAt", 0), tp.num("w", 0))
                && near(tu.num("shatterAt", 0), tu.num("t", 0)) && near(tu.num("chainAt", 0), tu.num("w", 0)), "thunderlord t 16 · 6 · 26 · 46 (w 6 · 6 · 16 · 10)");
        check(near(yp.num("smashAt", 0), yp.num("t", 0)) && near(yp.num("smashAt", 0), yp.num("w", 0)) && near(ys.num("slamAt", 0), ys.num("t", 0))
                && near(ys.num("slamAt", 0), ys.num("w", 0)) && near(yg.num("slamAt", 0), yg.num("t", 0)) && near(yg.num("first", 0), yg.num("w", 0))
                && near(yu.num("slamAt", 0), yu.num("t", 0)) && near(yu.num("first", 0), yu.num("w", 0)), "tyrant t 8 · 10 · 40 · 44 (w 8 · 10 · 10 · 10)");
        // 특성: 썬더로드 = 성기사의 심판 (같은 이름 · 쌓임) + 갈래 벼락 · 아군 보호막 · 오라 −8%, 타이런트 = 강탈
        for (String key : new String[] {"max", "judgeFor", "stun", "bossTaken", "bossFor", "auraRadius"}) {
            check(near(tl.trait().def().numOr(key, -1), pa.trait().def().numOr(key, -2)) || key.equals("max") && tl.trait().max() == pa.trait().max(), "심판 " + key + " = 1차");
        }
        check(tl.trait().name().equals(pa.trait().name()) && near(tl.trait().def().numOr("aura", 0), 0.08) && near(tl.trait().def().numOr("forks", 0), 2)
                && near(tl.trait().def().numOr("forkHit", 0), 0.8) && near(tl.trait().def().numOr("shield", 0), 0.04), "심판 + 갈래 벼락 둘 × 0.8 · 보호막 4% · 오라 −8%");
        check(ty.trait().name().equals("강탈") && ty.trait().max() == 10 && near(ty.trait().def().numOr("dealt", 0), 0.03) && near(ty.trait().def().numOr("taken", 0), 0.02)
                && near(ty.trait().def().numOr("grow", 0), 0.02) && near(ty.trait().def().numOr("weaken", 0), 0.03), "강탈 10 · +3% · −2% · 크기 +2% · 빼앗긴 적 −3%");
        // 유틸 · 범위 · 선딜
        check(Jobs.areaKind(ta) == null && "narrow".equals(Jobs.areaKind(ts)) && Jobs.areaKind(tp) == null && Jobs.areaKind(tu) == null && Jobs.utils(ta).equals(List.of("stun05"))
                && Jobs.utils(ts).equals(List.of("blink", "stun07", "shield")) && Jobs.utils(tp).equals(List.of("stun1", "knock", "shield"))
                && Jobs.utils(tu).equals(List.of("root", "stun1", "invuln")), "thunderlord utils · area");
        check("single".equals(Jobs.areaKind(yp)) && "single".equals(Jobs.areaKind(ys)) && "single".equals(Jobs.areaKind(yg)) && "narrow".equals(Jobs.areaKind(yu))
                && Jobs.utils(yp).isEmpty() && Jobs.utils(ys).equals(List.of("leap", "stun05")) && Jobs.utils(yg).equals(List.of("root", "lifesteal", "stun05"))
                && Jobs.utils(yu).equals(List.of("leap", "heal")), "tyrant utils · area");
        check(Jobs.windupTicks(ta) == 6 && Jobs.windupTicks(ts) == 6 && Jobs.windupTicks(tp) == 16 && Jobs.windupTicks(tu) == 10 && Jobs.windupTicks(yp) == 8
                && Jobs.windupTicks(ys) == 10 && Jobs.windupTicks(yg) == 10 && Jobs.windupTicks(yu) == 10, "windups 6 · 6 · 16 · 10 / 8 · 10 · 10 · 10");
        check(Jobs.slotRole(yg).equals("주력") && !Jobs.slotRole(tp).equals("주력"), "타이런트 3번 칸 = 주력 (단일)");
        check(Jobs.skillLine(ta).startsWith("아크 스매시 · 6초 · 배율 ") && Jobs.skillLine(yc).equals("콜로서스 · 30초"), "paladin advanced skill line " + Jobs.skillLine(yc));
        check(tl.color()[0][0] > 0.95 && tl.color()[0][1] > 0.75 && tl.color()[1][2] > 0.95 && ty.color()[0][2] > 0.95 && ty.color()[0][0] > ty.color()[0][1]
                && ty.color()[1][0] > 0.95, "colors: storm gold · sky / stolen violet · crimson");
    }

    /**
     * 2026-11-06 사제 2차 — 세인트 · 워록 (재설계안 2판 — 사용자 "다음 작업": 세인트 새 스킬 다섯 · 워록은 아군에게 체력을 주는 힐러를 뒤집어 팀의 체력을 빼앗아 쓰는
     * 원거리 딜러): 세인트 부분 합 = 배율 합 (사제와 같은 0 · 0 · 5.6 · 24 — 회복 · 보호막 칸은 피해 없음) · 워록 = 칸 틀 × 세기 0.6 (3 · 1.8 · 5.6 · 24 — 역할이 바뀌어
     * 회복 · 보호막 칸에도 피해 · 사제와 같은 칸은 그대로) · 무적 시간 띄움 · t · w · 특성 (신앙 = 1차 + 넘치는 빛 / 생명 착취 4% · 혼자 8% · 25% · 몫 하나당 +10% ·
     * 최대 5) · 초당 피해 (세인트 × 1.2 · 워록 × 1.425 — 원거리 딜러 목표 1.14 ÷ 힐러 0.96 × 1.2)
     */
    static void priestAdvance(Jobs.Job pr, Jobs.Job sa, Jobs.Job wk) {
        check(sa != null && wk != null, "priest advanced jobs");
        if (sa == null || wk == null) return;
        check(Jobs.advance("priest", "awaken") == sa && Jobs.advance("priest", "invert") == wk && Jobs.baseOf(sa) == pr && Jobs.baseOf(wk) == pr
                && Jobs.advancedOf("priest").equals(List.of(sa, wk)), "priest advance");
        check(sa.display().equals("세인트") && wk.display().equals("워록") && Jobs.find("세인트") == sa && Jobs.find("난나") == null && Jobs.find("워록") == wk
                && Jobs.find("굴베이그") == null && Jobs.find("warlock") == wk, "priest display · find");
        List<String> saIds = new ArrayList<>();
        List<String> wkIds = new ArrayList<>();
        for (Jobs.Skill k : sa.skills()) saIds.add(k.id());
        for (Jobs.Skill k : wk.skills()) wkIds.add(k.id());
        check(saIds.equals(List.of("saint_accord", "saint_veil", "saint_wing", "saint_bond", "saint_ult"))
                && wkIds.equals(List.of("warlock_lance", "warlock_mire", "warlock_nova", "warlock_ash", "warlock_ult")), "priest advanced skill ids");
        for (int i : new int[] {0, 1, 2, 4}) check(near(sa.skill(i).total(), pr.skill(i).total()), "saint totals slot " + i);
        Jobs.Skill ac = sa.skill(0), sv = sa.skill(1), sw = sa.skill(2), sb = sa.skill(3), su = sa.skill(4);
        Jobs.Skill wl = wk.skill(0), wm = wk.skill(1), wn = wk.skill(2), wa = wk.skill(3), wu = wk.skill(4);
        check(ac.total() == 0 && sv.total() == 0 && near(sw.num("hit", 0) * 2 + sw.num("last", 0), 5.6) && near(su.num("storm", 0) * su.num("storms", 0) + su.num("bloom",
                0), 24), "saint totals 0 · 0 · 5.6 (1.6 × 2 + 2.4) · 24 (4 × 3 + 12)");
        // 워록: 칸 틀 (검사 5 · 3 · 9 · 40) × 세기 — 사제와 같은 칸 (5.6 · 24) 은 그대로
        check(near(wl.num("lance", 0), 5 * pr.scale()) && near(wm.num("pulse", 0) * wm.num("pulses", 0), 3 * pr.scale()) && near(wn.num("drain", 0) * wn.num("drains", 0)
                + wn.num("burst", 0), pr.skill(2).total()) && near(wu.num("beat", 0) * wu.num("beats", 0) + wu.num("burst", 0), pr.skill(4).total()) && near(wl.total(), 3)
                && near(wm.total(), 1.8) && near(wn.total(), 5.6) && near(wu.total(), 24), "warlock totals 3 · 1.8 (0.45 × 4) · 5.6 (0.6 × 3 + 3.8) · 24 (2 × 3 + 18)");
        // 4번 칸: 소울본드 · 애시본 둘 다 강화 태세 (피해 없음 · 10초)
        check(!Jobs.stanceBudget(sb) && sb.total() == 0 && near(sb.num("dur", 0), 200) && !Jobs.stanceBudget(wa) && wa.total() == 0 && near(wa.num("dur", 0), 200),
                "소울본드 · 애시본 10초 태세");
        // 초당 피해: 세인트 = 1차 × 1.2 · 워록 = 1차 × 1.425 (역할이 바뀜 — 원거리 딜러 목표 1.14 ÷ 힐러 0.96 × 1.2 · 팀 체력 몫 +20% 는 생명 착취 덤)
        check(near(Jobs.advanceDps(sa), 1.2) && near(Jobs.advanceDps(wk), 1.425) && near(dps(sa) / dps(pr), 1.2, 0.005) && near(dps(wk) / dps(pr), 1.425, 0.005),
                "saint × 1.2 · warlock × 1.425");
        // 같은 적을 여러 번 치는 것은 10틱 넘게
        check(sw.num("every", 0) * 2 >= 10 && su.num("every", 0) >= 10 && su.num("bloomAt", 0) - su.num("first", 0) - (su.num("storms", 0) - 1) * su.num("every", 0) >= 10
                && wm.num("every", 0) >= 10 && wn.num("every", 0) >= 10 && wn.num("burstAt", 0) - wn.num("first", 0) - (wn.num("drains", 0) - 1) * wn.num("every", 0) >= 10
                && wu.num("every", 0) >= 10 && wu.num("burstAt", 0) - wu.num("first", 0) - (wu.num("beats", 0) - 1) * wu.num("every", 0) >= 10,
                "priest advanced i-frame spacing");
        // 마지막 피해 틱 = t · 첫 피해 = w (RpgJobTimingTests 와 같은 값 — 글리머윙은 적부터: 적 · 나 · 적 · 나 · 적)
        check(near(sw.num("every", 0) * 5, sw.num("t", 0)) && near(sw.num("every", 0), sw.num("w", 0)) && near(su.num("bloomAt", 0), su.num("t", 0))
                && near(su.num("first", 0), su.num("w", 0)), "saint t 40 · 80 (w 8 · 20)");
        check(near(wl.num("hitAt", 0), wl.num("t", 0)) && near(wl.num("hitAt", 0), wl.num("w", 0)) && near(wm.num("first", 0) + (wm.num("pulses", 0) - 1)
                * wm.num("every", 0), wm.num("t", 0)) && near(wm.num("first", 0), wm.num("w", 0)) && near(wn.num("burstAt", 0), wn.num("t", 0))
                && near(wn.num("first", 0), wn.num("w", 0)) && near(wu.num("burstAt", 0), wu.num("t", 0)) && near(wu.num("first", 0), wu.num("w", 0)),
                "warlock t 12 · 40 · 40 · 70 (w 12 · 10 · 10 · 10)");
        // 특성: 세인트 = 사제의 신앙 (같은 이름 · 쌓임) + 넘치는 빛, 워록 = 생명 착취
        for (String key : new String[] {"per", "overflowRadius"}) check(near(sa.trait().def().numOr(key, -1), pr.trait().def().numOr(key, -2)), "신앙 " + key + " = 1차");
        check(sa.trait().name().equals(pr.trait().name()) && sa.trait().max() == pr.trait().max() && near(sa.trait().def().numOr("overShield", 0), 1.0)
                && near(sa.trait().def().numOr("overCap", 0), 0.2) && near(sa.trait().def().numOr("overFor", 0), 80), "신앙 + 넘치는 빛 (넘친 회복 → 4초 보호막 · 최대 20%)");
        check(wk.trait().name().equals("생명 착취") && wk.trait().max() == 5 && near(wk.trait().def().numOr("take", 0), 0.04) && near(wk.trait().def().numOr("solo", 0), 0.08)
                && near(wk.trait().def().numOr("floor", 0), 0.25) && near(wk.trait().def().numOr("bonus", 0), 0.1) && near(wk.trait().def().numOr("radius", 0), 10),
                "생명 착취 4% · 혼자 8% · 25% · 몫 하나당 +10% · 최대 5");
        // 유틸 · 범위 · 선딜
        check(Jobs.areaKind(sw) == null && Jobs.areaKind(su) == null && Jobs.utils(sw).equals(List.of("healAlly")) && Jobs.utils(su).equals(Jobs.utils(pr.skill(4))),
                "saint utils · area");
        check("single".equals(Jobs.areaKind(wl)) && "narrow".equals(Jobs.areaKind(wm)) && Jobs.areaKind(wn) == null && Jobs.areaKind(wu) == null && Jobs.utils(wl).isEmpty()
                && Jobs.utils(wm).equals(List.of("invuln", "slow")) && Jobs.utils(wn).equals(List.of("knock")) && Jobs.utils(wu).equals(List.of("vuln10")),
                "warlock utils · area");
        check(Jobs.windupTicks(sw) == 8 && Jobs.windupTicks(su) == 20 && Jobs.windupTicks(wl) == 12 && Jobs.windupTicks(wm) == 10 && Jobs.windupTicks(wn) == 10
                && Jobs.windupTicks(wu) == 10, "windups 8 · 20 / 12 · 10 · 10 · 10");
        check(Jobs.skillLine(sw).startsWith("글리머윙 · 15초 · 배율 ") && Jobs.skillLine(wa).equals("애시본 · 30초"), "priest advanced skill line " + Jobs.skillLine(wa));
        check(sa.color()[0][0] > 0.95 && sa.color()[0][1] > 0.85 && sa.color()[1][1] > 0.95 && wk.color()[0][0] > 0.8 && wk.color()[0][1] < 0.2 && wk.color()[1][1] > 0.7,
                "colors: saint gold · green / warlock blood · witch gold");
    }

    /**
     * 0.51.0 사신 2차 — 소울 리퍼 · 엑시큐셔너 (재설계안 2판 — 2026-11-06 사용자 "다음 작업": 소울 리퍼 새 스킬 다섯 · 엑시큐셔너는 광역 · 많이 거둠 · 흡혈을 뒤집어
     * 단일 · 처형 단타 · 흡혈 없음): 부분 합 = 배율 합 (사신과 같은 6.25 · 3.75 · 8 · 50) · 무적 시간 띄움 · t · w · 특성 (영혼 15 · 영혼 해방 / 처형 — 잃은 체력
     * 10% 당 +5% · 15% 아래 한 방 · 초기화) · 초당 피해 (소울 리퍼 × 1.2 · 엑시큐셔너 × 1.0 — 처형 덤 평균 +25%). 2026-11-07 3판 (직업 스킬 도감 표시 고침):
     * 소울 리퍼 4번 칸 랜턴 오브 소울즈 (태세 칸 몫 피해) → 스펙트럴 사이스 (강화 태세), 엑시큐셔너 다섯을 새로 (헤드헌트 4 + X 2.25 · 레이븐 시프트 · 체인 오브 저지먼트 ·
     * 데스 넬 · 길로틴 오브 헤븐 — 숫자 같음)
     */
    static void reaperAdvance(Jobs.Job re, Jobs.Job sr, Jobs.Job ex) {
        check(sr != null && ex != null, "reaper advanced jobs");
        if (sr == null || ex == null) return;
        check(Jobs.advance("reaper", "awaken") == sr && Jobs.advance("reaper", "invert") == ex && Jobs.baseOf(sr) == re && Jobs.baseOf(ex) == re
                && Jobs.advancedOf("reaper").equals(List.of(sr, ex)), "reaper advance");
        check(sr.display().equals("소울 리퍼") && ex.display().equals("엑시큐셔너") && Jobs.find("소울 리퍼") == sr && Jobs.find("모드구드") == null
                && Jobs.find("엑시큐셔너") == ex && Jobs.find("발리") == null && Jobs.find("executioner") == ex, "reaper display · find");
        List<String> srIds = new ArrayList<>();
        List<String> exIds = new ArrayList<>();
        for (Jobs.Skill k : sr.skills()) srIds.add(k.id());
        for (Jobs.Skill k : ex.skills()) exIds.add(k.id());
        check(srIds.equals(List.of("soulreaper_crescent", "soulreaper_trespass", "soulreaper_harvest", "soulreaper_scythe", "soulreaper_ult"))
                && exIds.equals(List.of("executioner_headhunt", "executioner_raven", "executioner_chains", "executioner_knell", "executioner_ult")),
                "reaper advanced skill ids");
        for (int i : new int[] {0, 1, 2, 4}) check(near(sr.skill(i).total(), re.skill(i).total()) && near(ex.skill(i).total(), re.skill(i).total()), "reaper totals slot " + i);
        Jobs.Skill sc = sr.skill(0), st = sr.skill(1), sh = sr.skill(2), sl = sr.skill(3), su = sr.skill(4);
        Jobs.Skill ec = ex.skill(0), es = ex.skill(1), eg = ex.skill(2), el = ex.skill(3), eu = ex.skill(4);
        check(near(sc.num("spin", 0) * sc.num("spins", 0), 6.25) && near(st.num("burst", 0), 3.75) && near(sh.num("drain", 0) * sh.num("drains", 0) + sh.num("rip", 0), 8)
                && near(su.num("sweep", 0) * su.num("sweeps", 0) + su.num("final", 0), 50), "soul reaper totals 6.25 (3.125 × 2) · 3.75 · 8 (1 × 4 + 4) · 50 (14 × 2 + 22)");
        check(near(ec.num("slash", 0) + ec.num("x", 0), 6.25) && near(ec.num("slash", 0), 4.0) && near(es.num("cut", 0), 3.75) && near(eg.num("drop", 0), 8)
                && near(eu.num("stroke", 0) * eu.num("strokes", 0), 50), "executioner totals 6.25 (4 + X 2.25) · 3.75 · 8 · 50 (25 × 2)");
        // 2026-11-07 3판: 스펙트럴 사이스 · 데스 넬은 강화 (피해 없음 — 8초 · 10초), 스펙트럴 사이스는 넓게 베기 5칸 · 50% (강화 열쇠 ratio) · 처치 1초씩 (최대 4초)
        check(!Jobs.stanceBudget(sl) && sl.total() == 0 && near(sl.num("dur", 0), 160) && near(sl.num("range", 0), 5) && near(sl.num("ratio", 0), 0.5)
                && near(sl.num("perKill", 0), 20) && near(sl.num("maxExtra", 0), 80) && Jobs.upKeys(sl).size() == 1 && Jobs.upKey(sl, "ratio") != null
                && !Jobs.stanceBudget(el) && el.total() == 0 && near(el.num("dur", 0), 200), "스펙트럴 사이스 8초 (넓게 베기 5칸 · 50%) · 데스 넬 10초");
        // 초당 피해: 소울 리퍼 1차 × 1.2 · 엑시큐셔너 1차 × 1.0 (처형 덤을 따로 셈 — 직업의 dps)
        check(near(Jobs.advanceDps(sr), 1.2) && near(Jobs.advanceDps(ex), 1.0) && near(dps(sr) / dps(re), 1.2, 0.005) && near(dps(ex) / dps(re), 1.0, 0.005),
                "soul reaper × 1.2 · executioner × 1.0");
        // 같은 적을 여러 번 치는 것은 10틱 넘게
        check(sc.num("every", 0) >= 10 && sh.num("every", 0) >= 10 && sh.num("ripAt", 0) - sh.num("first", 0) - (sh.num("drains", 0) - 1) * sh.num("every", 0) >= 10
                && su.num("every", 0) >= 10 && su.num("finalAt", 0) - su.num("sweepAt", 0) - (su.num("sweeps", 0) - 1) * su.num("every", 0) >= 10
                && eu.num("every", 0) >= 10 && ec.num("gap", 0) + ec.num("xAt", 0) - ec.num("slashAt", 0) >= 10, "reaper advanced i-frame spacing (헤드헌트 X 는 11틱 뒤)");
        // 마지막 피해 틱 = t · 첫 피해 = w (RpgJobTimingTests 와 같은 값)
        check(near(sc.num("first", 0) + (sc.num("spins", 0) - 1) * sc.num("every", 0), sc.num("t", 0)) && near(st.num("exitAt", 0), st.num("t", 0))
                && near(st.num("exitAt", 0), st.num("w", 0)) && near(sh.num("ripAt", 0), sh.num("t", 0)) && near(sh.num("first", 0), sh.num("w", 0))
                && near(su.num("finalAt", 0), su.num("t", 0)) && near(su.num("sweepAt", 0), su.num("w", 0)), "soul reaper t 14 · 18 · 50 · 46");
        // 헤드헌트: 첫 벰 slashAt · X = 다시 누를 수 있는 gap 틱에 누름 + xAt (시간 시험은 다시 누를 수 있는 첫 틱에 누름)
        check(near(ec.num("slashAt", 0), ec.num("w", 0)) && near(ec.num("gap", 0) + ec.num("xAt", 0), ec.num("t", 0)) && near(es.num("flyTicks", 0), es.num("t", 0))
                && near(es.num("flyTicks", 0), es.num("w", 0)) && near(eg.num("dropAt", 0), eg.num("t", 0)) && near(eg.num("dropAt", 0), eg.num("w", 0))
                && near(eu.num("first", 0) + (eu.num("strokes", 0) - 1) * eu.num("every", 0), eu.num("t", 0)) && near(eu.num("first", 0), eu.num("w", 0))
                && eu.num("landAt", 0) < eu.num("first", 0) && eg.num("bindAt", 0) < eg.num("dropAt", 0), "executioner t 17 · 8 · 22 · 28 (w 6 · 8 · 22 · 16)");
        // 특성: 소울 리퍼 = 사신의 영혼 (같은 처치 +3 · 이동 · 흡혈) 최대 15 + 영혼 해방, 엑시큐셔너 = 처형
        for (String key : new String[] {"kill", "speedPer", "leechPer"}) check(near(sr.trait().def().numOr(key, -1), re.trait().def().numOr(key, -2)), "영혼 " + key + " = 1차");
        check(sr.trait().name().equals(re.trait().name()) && sr.trait().max() == 15 && near(sr.trait().def().numOr("wisps", 0), 6)
                && near(sr.trait().def().numOr("wispHit", 0), 0.6), "영혼 15 · 망령 여섯 × 0.6");
        check(ex.trait().name().equals("처형") && ex.trait().max() == 10 && near(ex.trait().def().numOr("per", 0), 0.05) && near(ex.trait().def().numOr("maxBonus", 0), 0.5)
                && near(ex.trait().def().numOr("execute", 0), 0.15) && near(el.num("execute", 0), 0.25) && near(el.num("fear", 0), 20) && near(el.num("fearRange", 0), 8),
                "처형 10% 당 +5% · 최대 +50% · 15% (데스 넬 25% · 처형하면 둘레 8칸 공포 1초)");
        // 유틸 · 범위
        check(Jobs.areaKind(sc) == null && "narrow".equals(Jobs.areaKind(st)) && Jobs.areaKind(sh) == null && Jobs.areaKind(sl) == null && Jobs.areaKind(su) == null
                && Jobs.utils(sc).isEmpty() && Jobs.utils(st).equals(List.of("blink", "invuln", "heal")) && Jobs.utils(sh).equals(List.of("lifesteal"))
                && Jobs.utils(sl).isEmpty() && Jobs.utils(su).equals(List.of("lifesteal", "lift")), "soul reaper utils · area");
        check("single".equals(Jobs.areaKind(ec)) && "single".equals(Jobs.areaKind(es)) && "single".equals(Jobs.areaKind(eg)) && "narrow".equals(Jobs.areaKind(eu))
                && Jobs.utils(ec).equals(List.of("vuln10")) && Jobs.utils(es).equals(List.of("leap", "invuln", "buff10")) && Jobs.utils(eg).equals(List.of("root"))
                && Jobs.utils(eu).equals(List.of("root")), "executioner utils · area");
        check(Jobs.windupTicks(sc) == 0 && Jobs.windupTicks(st) == 18 && Jobs.windupTicks(sh) == 10 && Jobs.windupTicks(su) == 20 && Jobs.windupTicks(ec) == 6
                && Jobs.windupTicks(es) == 8 && Jobs.windupTicks(eg) == 22 && Jobs.windupTicks(eu) == 16, "windups 0 · 18 · 10 · 20 / 6 · 8 · 22 · 16");
        check(Jobs.skillLine(sc).startsWith("리핑 크레센트 · 6초 · 배율 ") && Jobs.skillLine(el).equals("데스 넬 · 30초") && Jobs.skillLine(sl).equals("스펙트럴 사이스 · 30초"),
                "reaper advanced skill line " + Jobs.skillLine(el) + " · " + Jobs.skillLine(sl));
        check(sr.color()[0][2] > 0.95 && sr.color()[0][0] < 0.4 && sr.color()[1][2] > sr.color()[1][0] && ex.color()[0][0] > 0.95 && ex.color()[0][1] > 0.6
                && ex.color()[0][2] < 0.4 && ex.color()[1][2] > ex.color()[1][1], "colors: soul blue · deep sea / sunset gold · dusk violet");
    }

    /**
     * 0.51.0 창술사 2차 — 스톰 랜서 · 드라군 (재설계안 2판 — 2026-11-06 사용자 "다음 작업": 스톰 랜서 새 스킬 다섯 · 드라군은 거리 · 직선 · 연타 찌르기를 뒤집어
     * 높이 · 광역 · 내리꽂는 단타): 부분 합 = 배율 합 (창술사와 같은 7.25 · 4.35 · 13.05 · 58 — 렌딩 게일 · 드래곤 브레스는 태세 칸 몫 12 × 1.45) · 무적 시간 띄움 ·
     * t · w · 특성 (창끝 + 바람 4 질풍 창 / 비상 — 공중 −30% · 높이 1칸당 +5% · 최대 6칸)
     */
    static void lancerAdvance(Jobs.Job la, Jobs.Job sl, Jobs.Job dg) {
        check(sl != null && dg != null, "lancer advanced jobs");
        if (sl == null || dg == null) return;
        check(Jobs.advance("lancer", "awaken") == sl && Jobs.advance("lancer", "invert") == dg && Jobs.baseOf(sl) == la && Jobs.baseOf(dg) == la
                && Jobs.advancedOf("lancer").equals(List.of(sl, dg)), "lancer advance");
        check(sl.display().equals("스톰 랜서") && dg.display().equals("드라군") && Jobs.find("스톰 랜서") == sl && Jobs.find("카리") == null && Jobs.find("드라군") == dg
                && Jobs.find("파프니르") == null && Jobs.find("dragoon") == dg, "lancer display · find");
        List<String> slIds = new ArrayList<>();
        List<String> dgIds = new ArrayList<>();
        for (Jobs.Skill k : sl.skills()) slIds.add(k.id());
        for (Jobs.Skill k : dg.skills()) dgIds.add(k.id());
        check(slIds.equals(List.of("stormlancer_cross", "stormlancer_drive", "stormlancer_drill", "stormlancer_rend", "stormlancer_ult"))
                && dgIds.equals(List.of("dragoon_tail", "dragoon_jump", "dragoon_impale", "dragoon_breath", "dragoon_ult")), "lancer advanced skill ids");
        for (int i : new int[] {0, 1, 2, 4}) check(near(sl.skill(i).total(), la.skill(i).total()) && near(dg.skill(i).total(), la.skill(i).total()), "lancer totals slot " + i);
        Jobs.Skill sc = sl.skill(0), sd = sl.skill(1), sr = sl.skill(2), sn = sl.skill(3), su = sl.skill(4);
        Jobs.Skill dt = dg.skill(0), dj = dg.skill(1), di = dg.skill(2), db = dg.skill(3), du = dg.skill(4);
        check(near(sc.num("gust", 0) * 2 + sc.num("cross", 0), 7.25) && near(sd.num("pass", 0) + sd.num("lance", 0), 4.35)
                && near(sr.num("grind", 0) * sr.num("grinds", 0) + sr.num("burst", 0), 13.05) && near(su.num("pulse", 0) * su.num("pulses", 0) + su.num("final", 0), 58),
                "storm lancer totals 7.25 (2 + 2 + 3.25) · 4.35 · 13.05 (3 × 3 + 4.05) · 58 (6 × 5 + 28)");
        check(near(dt.num("sweep", 0) + dt.num("slam", 0), 7.25) && near(dj.total(), 4.35) && near(di.num("skewer", 0) + di.num("slam", 0), 13.05)
                && near(du.num("impact", 0) + du.num("pillars", 0), 58) && near(du.num("outer", 0), 0.6), "dragoon totals 7.25 · 4.35 · 13.05 · 58 (40 + 18)");
        // 렌딩 게일 · 드래곤 브레스 = 태세 칸 몫 (30초 12 × 창술사 세기 1.45) · 직업 맞춤에서 뺌
        check(Jobs.stanceBudget(sn) && Jobs.stanceBudget(db) && near(sn.total(), 12 * la.scale()) && near(db.total(), 12 * la.scale())
                && near(sn.num("spear", 0) * sn.num("spears", 0), sn.total()) && near(db.num("breath", 0), db.total()), "렌딩 게일 · 드래곤 브레스 17.4 (태세 칸 몫)");
        // 초당 피해: 둘 다 1차 × 1.2
        check(near(Jobs.advanceDps(sl), 1.2) && near(Jobs.advanceDps(dg), 1.2) && near(dps(sl) / dps(la), 1.2, 0.005) && near(dps(dg) / dps(la), 1.2, 0.005),
                "storm lancer · dragoon dps = lancer × 1.2");
        // 같은 적을 여러 번 치는 것은 10틱 넘게 (크로스 게일 둘째 바람 창은 서버가 첫째 뒤 10틱으로 미룸 · 터짐은 그 뒤 10틱 — 바람 창이 창끝까지 날아가는 틱 (45칸/초) 을 더해, 게일 드라이브 되찌르기는 뛰어든 뒤
        // — 지나간 벰과 10틱이 안 되면 서버가 미룸, 드릴 · 폭풍의 눈은 10틱마다, 웜테일 · 임페일 · 드래곤폴은 두 번째 피해가 10틱 넘게 뒤)
        check(sc.num("crossAt", 0) - sc.num("thrustA", 0) >= 20 + sc.num("focus", 0) / 45 * 20 && sd.num("gap", 0) + sd.num("stabAt", 0) > sd.num("steps", 0)
                && sr.num("every", 0) >= 10 && sr.num("burstAt", 0) - sr.num("first", 0) - (sr.num("grinds", 0) - 1) * sr.num("every", 0) >= 10
                && su.num("every", 0) >= 10 && su.num("finalAt", 0) - su.num("first", 0) - (su.num("pulses", 0) - 1) * su.num("every", 0) >= 10, "storm lancer i-frame spacing");
        check(dt.num("slamAt", 0) - dt.num("sweepAt", 0) >= 10 && di.num("slamAt", 0) - di.num("skewerAt", 0) >= 10 && du.num("pillarsAt", 0) - du.num("impactAt", 0) >= 10,
                "dragoon i-frame spacing");
        // 마지막 피해 틱 = t · 첫 피해 = w (RpgJobTimingTests 와 같은 값 — 다시 누르는 스킬은 12틱마다 다시 누름)
        check(near(sc.num("crossAt", 0), sc.num("t", 0)) && near(12 + sd.num("stabAt", 0), sd.num("t", 0))
                && near(sr.num("burstAt", 0), sr.num("t", 0)) && near(sr.num("first", 0), sr.num("w", 0)) && near(12 + sn.num("ripAt", 0), sn.num("t", 0))
                && near(sn.num("w", -1), 0) && near(su.num("finalAt", 0), su.num("t", 0)) && near(su.num("first", 0), su.num("w", 0)), "storm lancer t 26 · 14 · 36 · 14 · 70");
        check(near(dt.num("slamAt", 0), dt.num("t", 0)) && near(dt.num("sweepAt", 0), dt.num("w", 0)) && near(12 + dj.num("diveTicks", 0), dj.num("t", 0))
                && near(dj.num("gap", 0) + dj.num("diveTicks", 0), dj.num("w", 0)) && near(di.num("slamAt", 0), di.num("t", 0)) && near(di.num("skewerAt", 0), di.num("w", 0))
                && near(db.num("breathAt", 0), db.num("t", 0)) && near(db.num("breathAt", 0), db.num("w", 0)) && near(du.num("pillarsAt", 0), du.num("t", 0))
                && near(du.num("impactAt", 0), du.num("w", 0)) && du.num("diveAt", 0) > du.num("hideAt", 0) && du.num("hideAt", 0) > du.num("soarAt", 0),
                "dragoon t 18 · 17 · 20 · 14 · 56 (w 6 · 15 · 6 · 14 · 46)");
        // 특성: 스톰 랜서 = 창술사의 창끝 (같은 끝자락 +15% · 밀쳐냄 · 기세) + 바람 4 질풍 창, 드라군 = 비상 (공중 −30% · 높이 1칸당 +5% · 최대 6칸)
        for (String key : new String[] {"edge", "edgeBonus", "push", "momentum", "momentumFor"}) {
            check(near(sl.trait().def().numOr(key, -1), la.trait().def().numOr(key, -2)), "창끝 " + key + " = 1차");
        }
        check(sl.trait().name().equals(la.trait().name()) && sl.trait().max() == 4 && near(sl.trait().def().numOr("gust", 0), 8)
                && near(sl.trait().def().numOr("gustHit", 0), 0.6) && sl.trait().def().numOr("gustDelay", 0) >= 10 && near(sl.trait().def().numOr("windFor", 0), 120),
                "창끝 바람 4 · 질풍 창 8칸 · 위력 × 0.6");
        check(dg.trait().name().equals("비상") && dg.trait().max() == 6 && near(dg.trait().def().numOr("air", 0), 0.3) && near(dg.trait().def().numOr("perBlock", 0), 0.05)
                && near(dg.trait().def().numOr("maxBlocks", 0), 6), "비상 공중 −30% · 1칸당 +5% · 최대 6칸");
        // 유틸 · 범위: 크로스 게일 · 게일 드라이브 · 템페스트 드릴 · 하이 점프 좁은 범위, 나머지 넓음
        check("narrow".equals(Jobs.areaKind(sc)) && "narrow".equals(Jobs.areaKind(sd)) && "narrow".equals(Jobs.areaKind(sr)) && Jobs.areaKind(sn) == null
                && Jobs.areaKind(su) == null && Jobs.utils(sc).isEmpty() && Jobs.utils(sd).equals(List.of("leap", "knock")) && Jobs.utils(sr).equals(List.of("pull", "knock"))
                && Jobs.utils(sn).isEmpty() && Jobs.utils(su).equals(List.of("pull", "lift")), "storm lancer utils · area");
        check(Jobs.areaKind(dt) == null && "narrow".equals(Jobs.areaKind(dj)) && Jobs.areaKind(di) == null && Jobs.areaKind(db) == null && Jobs.areaKind(du) == null
                && Jobs.utils(dt).equals(List.of("lift")) && Jobs.utils(dj).equals(List.of("leap")) && Jobs.utils(di).equals(List.of("stun05"))
                && Jobs.utils(db).equals(List.of("knock")) && Jobs.utils(du).equals(List.of("lift")), "dragoon utils · area");
        check(Jobs.windupTicks(sc) == 0 && Jobs.windupTicks(sd) == 0 && Jobs.windupTicks(sr) == 6 && Jobs.windupTicks(sn) == 0 && Jobs.windupTicks(su) == 14
                && Jobs.windupTicks(dt) == 6 && Jobs.windupTicks(dj) == 15 && Jobs.windupTicks(di) == 6 && Jobs.windupTicks(db) == 14 && Jobs.windupTicks(du) == 46,
                "windups 0 · 0 · 6 · 0 · 14 / 6 · 15 · 6 · 14 · 46");
        check(Jobs.skillLine(sc).startsWith("크로스 게일 · 6초 · 배율 ") && Jobs.skillLine(dg.skill(4)).startsWith("드래곤폴 · 60초 · 배율 "), "lancer advanced skill line");
        check(sl.color()[0][1] > 0.95 && sl.color()[0][0] < 0.5 && sl.color()[1][2] > 0.9 && dg.color()[0][0] > 0.95 && dg.color()[0][1] < 0.3 && dg.color()[0][2] < 0.2
                && dg.color()[1][0] > 0.95 && dg.color()[1][1] > 0.5 && dg.color()[1][2] < 0.3, "colors: jade wind · white / red dragon · flame orange");
    }

    /**
     * 0.51.0 암살자 2차 — 트릭스터 · 피에로 (재설계안 2판 — 2026-11-06 사용자 "다음 작업": 트릭스터 새 스킬 다섯 · 블레이드 댄서 대신 피에로 — 광대 소환물 탱커,
     * 초당 피해 = 1차 암살자 (사용자 "1차 암살자랑 같게" — 직업의 dps 1.0)): 부분 합 = 배율 합 (암살자와 같은 4.75 · 2.85 · 8.55 · 38 — 표식을 터뜨리는 것은 표식
     * 가득 (7) 기준, 할루시네이트는 태세 칸 몫 12 × 0.95) · 무적 시간 띄움 · t · w · 특성 (표식 7 · 옮겨감 / 쇼타임 — 끌어들임 6칸 · 나눠 받음 20% · 색종이 폭죽)
     */
    static void assassinAdvance(Jobs.Job as, Jobs.Job tr, Jobs.Job pr) {
        check(tr != null && pr != null, "assassin advanced jobs");
        if (tr == null || pr == null) return;
        check(Jobs.advance("assassin", "awaken") == tr && Jobs.advance("assassin", "invert") == pr && Jobs.baseOf(tr) == as && Jobs.baseOf(pr) == as, "assassin advance");
        check(tr.display().equals("트릭스터") && pr.display().equals("피에로") && Jobs.find("트릭스터") == tr && Jobs.find("우트가르드") == null && Jobs.find("피에로") == pr
                && Jobs.find("pierrot") == pr && Jobs.job("bladedancer") == null && Jobs.find("블레이드 댄서") == null, "assassin display · find");
        List<String> trIds = new ArrayList<>();
        List<String> prIds = new ArrayList<>();
        for (Jobs.Skill k : tr.skills()) trIds.add(k.id());
        for (Jobs.Skill k : pr.skills()) prIds.add(k.id());
        check(trIds.equals(List.of("trickster_swap", "trickster_dash", "trickster_twin", "trickster_waltz", "trickster_ult"))
                && prIds.equals(List.of("pierrot_box", "pierrot_spring", "pierrot_balloon", "pierrot_doll", "pierrot_ult")), "assassin advanced skill ids (2판 · 트릭스터 3판)");
        for (int i : new int[] {0, 1, 2, 4}) check(near(tr.skill(i).total(), as.skill(i).total()) && near(pr.skill(i).total(), as.skill(i).total()), "assassin totals slot " + i);
        int max = tr.trait().max();
        Jobs.Skill ts = tr.skill(0), td = tr.skill(1), tp = tr.skill(2), tm = tr.skill(3), tu = tr.skill(4);
        Jobs.Skill pb = pr.skill(0), ps = pr.skill(1), pl = pr.skill(2), pd = pr.skill(3), pu = pr.skill(4);
        check(max == 7 && near(ts.num("cut", 0) + ts.num("base", 0) + ts.num("per", 0) * max, 4.75) && near(td.total(), 2.85)
                && near(tp.num("packet", 0) * tp.num("packets", 0) + tp.num("x", 0), 8.55)
                && near(tu.num("slash", 0) + tu.num("flame", 0) * tu.num("flames", 0) + tu.num("base", 0) + tu.num("per", 0) * max, 38), "trickster totals 4.75 · 2.85 · 8.55 · 38 (표식 7)");
        check(near(pb.num("knife", 0) * pb.num("knives", 0), 4.75) && near(ps.total(), 2.85) && near(pl.num("tangle", 0) + pl.num("pop", 0), 8.55)
                && near(pu.num("rocket", 0) * pu.num("volleys", 0) + pu.num("finale", 0), 38), "pierrot totals 4.75 · 2.85 · 8.55 · 38");
        // 할루시네이트 = 태세 칸 몫 (30초 12 × 암살자 세기 0.95) · 직업 맞춤에서 뺌, 팬텀 왈츠 = 강화 (배율 합 없음 — 환영 베기 평타의 50% · 강화 키 reflect)
        check(Jobs.stanceBudget(pd) && near(pd.total(), 12 * as.scale()) && near(pd.num("swing", 0) * pd.num("swings", 0), pd.total()) && tm.total() <= 0 && !Jobs.stanceBudget(tm),
                "할루시네이트 11.4 (태세 칸 몫) · 팬텀 왈츠 강화");
        check(near(tm.num("reflect", 0), 0.5) && Jobs.upKey(tm, "reflect") != null && tm.num("spinAt", 0) >= 2 && near(tm.num("dur", 0), 160), "팬텀 왈츠 50% · 8초");
        check(near(tp.num("passAt", 0) + 6, tp.num("xAt", 0)) && tp.num("passAt", 0) > tp.num("first", 0) + (tp.num("packets", 0) - 1) * tp.num("every", 0)
                && near(tu.num("mirrors", 0), 12) && tu.num("ring", 0) > 3, "트윈 리플렉션 28 엇갈림 → 34 X · 미러 월드 거울 12");
        // 초당 피해: 트릭스터 = 1차 × 1.2, 피에로 = 1차와 같음 (직업의 dps 1.0 — 탱커)
        check(near(Jobs.advanceDps(tr), 1.2) && near(Jobs.advanceDps(pr), 1.0) && near(Jobs.advanceDps(as), 1) && near(Jobs.advanceDps(), 1.2), "advance dps 1.2 · pierrot 1.0");
        check(near(dps(pr) / dps(as), 1.0, 0.005) && near(dps(tr) / dps(as), 1.2, 0.005), "pierrot dps = assassin · trickster ×1.2");
        // 같은 적을 여러 번 치는 것은 10틱 넘게 (다시 누르는 찌르기는 지나간 환영의 벰 뒤 10틱 — 서버가 미룸 · 칼 · 불꽃놀이 · 꿰뚫기는 10틱마다)
        check(ts.num("gap", 0) + ts.num("stabAt", 0) >= ts.num("cutAt", 0) && ts.num("t", 0) - ts.num("cutAt", 0) >= 10 && tp.num("every", 0) >= 10
                && tp.num("xAt", 0) - tp.num("first", 0) - (tp.num("packets", 0) - 1) * tp.num("every", 0) >= 10 && tu.num("first", 0) - tu.num("slashAt", 0) >= 10
                && tu.num("every", 0) >= 10 && tu.num("finalAt", 0) - tu.num("first", 0) - (tu.num("flames", 0) - 1) * tu.num("every", 0) >= 10, "trickster i-frame spacing");
        check(pb.num("every", 0) >= 10 && pl.num("popAt", 0) - pl.num("tangleAt", 0) >= 10 && pd.num("every", 0) >= 10 && pu.num("every", 0) >= 10
                && pu.num("finaleAt", 0) - pu.num("first", 0) - (pu.num("volleys", 0) - 1) * pu.num("every", 0) >= 10, "pierrot i-frame spacing");
        // 마지막 피해 틱 = t · 첫 피해 = w (RpgJobTimingTests 와 같은 값 — 다시 누르는 스킬은 12틱마다 다시 누름)
        check(near(12 + ts.num("stabAt", 0), ts.num("t", 0)) && near(td.num("steps", 0), td.num("t", 0)) && near(tp.num("xAt", 0), tp.num("t", 0))
                && near(tu.num("finalAt", 0), tu.num("t", 0)), "trickster t 14 · 4 · 34 · 62");
        check(near(pb.num("first", 0) + (pb.num("knives", 0) - 1) * pb.num("every", 0), pb.num("t", 0)) && near(pb.num("first", 0), pb.num("w", 0))
                && near(ps.num("land", 0), ps.num("t", 0)) && near(ps.num("land", 0), ps.num("w", 0)) && near(pl.num("popAt", 0), pl.num("t", 0))
                && near(pl.num("tangleAt", 0), pl.num("w", 0)) && near(pd.num("first", 0) + (pd.num("swings", 0) - 1) * pd.num("every", 0), pd.num("t", 0))
                && near(pd.num("first", 0), pd.num("w", 0)) && near(pu.num("finaleAt", 0), pu.num("t", 0)) && near(pu.num("first", 0), pu.num("w", 0)),
                "pierrot t 50 · 8 · 24 · 200 · 90 (w 10 · 8 · 14 · 20 · 10)");
        // 특성: 트릭스터 = 암살자의 표식 (같은 등 뒤 +25%p · 10초) 최대 7 + 옮겨감 (5 이상 · 6칸), 피에로 = 쇼타임 (소환물 넷까지 셈 · 끌어들임 6칸 · 나눠 받음 20% · 12칸 ·
        // 부서지면 색종이 폭죽 둘레 3칸 위력 × 0.8 · 0.5초 기절)
        check(tr.trait().name().equals(as.trait().name()) && near(tr.trait().def().numOr("back", 0), as.trait().def().numOr("back", -1))
                && near(tr.trait().def().numOr("markFor", 0), as.trait().def().numOr("markFor", -1)) && near(tr.trait().def().numOr("shift", 0), 5)
                && near(tr.trait().def().numOr("shiftRange", 0), 6), "표식 7 · 옮겨감");
        check(pr.trait().name().equals("쇼타임") && pr.trait().max() == 4 && near(pr.trait().def().numOr("lure", 0), 6) && near(pr.trait().def().numOr("share", 0), 0.2)
                && near(pr.trait().def().numOr("shareRange", 0), 12) && near(pr.trait().def().numOr("burst", 0), 0.8) && near(pr.trait().def().numOr("burstRadius", 0), 3)
                && near(pr.trait().def().numOr("stun", 0), 10), "쇼타임 6칸 · 20% · 폭죽 0.8");
        // 소환물 체력 (내 최대 체력의 몫): 상자 35% · 인형 80% · 피날레 상자 40%, 상자는 둘까지
        check(near(pb.num("hp", 0), 0.35) && near(pd.num("hp", 0), 0.8) && near(pu.num("hp", 0), 0.4) && near(pb.num("boxes", 0), 2) && near(pu.num("boxes", 0), 4),
                "summon hp 35% · 80% · 40%");
        // 유틸 · 범위: 섀도 스와프 · 트윈 리플렉션 · 미러 월드 단일 · 미라주 대시 좁은 범위 · 순간 이동, 잭 인 더 박스 단일 · 도발, 핸드스프링 좁은 범위 · 도약 · 도발 ·
        // 피해 감소, 벌룬 퍼레이드 넓음 · 둔화 · 도발, 그랜드 피날레 넓음 · 도발 · 밀쳐냄
        check("single".equals(Jobs.areaKind(ts)) && "narrow".equals(Jobs.areaKind(td)) && "single".equals(Jobs.areaKind(tp)) && "single".equals(Jobs.areaKind(tu))
                && Jobs.utils(td).equals(List.of("blink")) && Jobs.utils(tp).equals(List.of("blink")) && Jobs.utils(tu).equals(List.of("blink")) && Jobs.utils(ts).isEmpty(),
                "trickster utils · area");
        check("single".equals(Jobs.areaKind(pb)) && "narrow".equals(Jobs.areaKind(ps)) && Jobs.areaKind(pl) == null && "narrow".equals(Jobs.areaKind(pd)) && Jobs.areaKind(pu) == null
                && Jobs.utils(pb).equals(List.of("taunt")) && Jobs.utils(ps).equals(List.of("leap", "taunt", "reduce")) && near(Jobs.utilCost(ps), 0.3)
                && Jobs.utils(pl).equals(List.of("slow", "taunt")) && Jobs.utils(pu).equals(List.of("taunt", "knock")), "pierrot utils · area");
        check(Jobs.windupTicks(ts) == 0 && Jobs.windupTicks(tu) == 0 && Jobs.windupTicks(pb) == 10 && Jobs.windupTicks(ps) == 8 && Jobs.windupTicks(pl) == 14
                && Jobs.windupTicks(pu) == 10, "windups 0 · 0 · 10 · 8 · 14 · 10");
        check(Jobs.upKey(tm, "reflect") != null && Jobs.upKeys(pd).isEmpty() && Jobs.upKeys(ts).isEmpty(), "assassin advanced up keys");
        check(Jobs.skillLine(tm).equals("팬텀 왈츠 · 30초") && Jobs.skillLine(ts).startsWith("섀도 스와프 · 6초 · 배율 ") && Jobs.skillLine(pb).startsWith("잭 인 더 박스 · 6초 · 배율 "),
                "assassin advanced skill line");
        check(tr.color()[0][0] > 0.9 && tr.color()[0][2] > 0.7 && tr.color()[1][1] < 0.2 && pr.color()[0][0] > 0.9 && pr.color()[0][1] < 0.3 && pr.color()[1][0] > 0.95
                && pr.color()[1][1] > 0.85, "colors: magenta · violet / circus red · gold");
    }

    /**
     * 0.51.0 궁수 2차 — 스나이퍼 · 배틀 아처 (재설계안 2판 — 2026-11-06 사용자 "다음 작업": 스나이퍼 새 스킬 · 섀도 헌터 대신 배틀 아처): 부분 합 = 배율 합 (궁수와 같은
     * 7.5 · 13.5 · 60 — 둘째 칸은 1차 게일 스텝처럼 평타 화살이라 배율 합 없음) · 무적 시간 띄움 · t · w · 특성 (호흡 8 급소 사격 / 난사 — 세 갈래 · 가까이 +20% · 멀리 −30%)
     */
    static void archerAdvance(Jobs.Job ar, Jobs.Job sn, Jobs.Job ba) {
        check(sn != null && ba != null, "archer advanced jobs");
        if (sn == null || ba == null) return;
        check(Jobs.advance("archer", "awaken") == sn && Jobs.advance("archer", "invert") == ba && Jobs.baseOf(sn) == ar && Jobs.baseOf(ba) == ar, "archer advance");
        check(sn.display().equals("스나이퍼") && ba.display().equals("배틀 아처") && Jobs.find("스나이퍼") == sn && Jobs.find("에길") == null && Jobs.find("배틀 아처") == ba
                && Jobs.find("battlearcher") == ba && Jobs.job("shadowhunter") == null && Jobs.find("섀도 헌터") == null, "archer display · find");
        List<String> snIds = new ArrayList<>();
        List<String> baIds = new ArrayList<>();
        for (Jobs.Skill k : sn.skills()) snIds.add(k.id());
        for (Jobs.Skill k : ba.skills()) baIds.add(k.id());
        check(snIds.equals(List.of("sniper_heart", "sniper_snare", "sniper_trick", "sniper_mark", "sniper_ult"))
                && baIds.equals(List.of("battlearcher_swarm", "battlearcher_dive", "battlearcher_splinter", "battlearcher_stride", "battlearcher_ult")), "archer advanced skill ids (2판)");
        for (int i : new int[] {0, 1, 2, 4}) check(near(sn.skill(i).total(), ar.skill(i).total()) && near(ba.skill(i).total(), ar.skill(i).total()), "archer totals slot " + i);
        Jobs.Skill sh = sn.skill(0), ss = sn.skill(1), st = sn.skill(2), sm = sn.skill(3), su = sn.skill(4);
        Jobs.Skill bs = ba.skill(0), bd = ba.skill(1), bp = ba.skill(2), bt = ba.skill(3), bu = ba.skill(4);
        check(near(sh.total(), 7.5) && near(st.num("first", 0) + st.num("bounce", 0) * st.num("bounces", 0), 13.5)
                && near(su.num("shot", 0) + su.num("burst", 0), 60) && su.name().equals("사일런트 아울"), "sniper totals 7.5 · 13.5 · 60 (사일런트 아울 45 + 15)");
        check(near(bs.num("arrows", 0) * bs.num("arrow", 0), 7.5) && near(bp.total(), 13.5) && near(bu.num("pulses", 0) * bu.num("pulse", 0) + bu.num("final", 0), 60),
                "battlearcher totals 7.5 · 13.5 · 60");
        // 같은 적을 여러 번 치는 것은 10틱 넘게 (트릭 샷이 같은 적에게 다시 · 데드아이 세 발 · 퀴버 스톰 물결 — 열두 발 · 조각은 한 번에 넣고 숫자만 여러 줄)
        check(st.num("sameGap", 0) >= 10 && su.num("burstAt", 0) >= 10 && su.num("gap", 0) >= 10 && sh.num("gap", 0) >= 6, "sniper i-frame spacing");
        check(bu.num("every", 0) >= 10 && bu.num("finalAt", 0) - bu.num("first", 0) - (bu.num("pulses", 0) - 1) * bu.num("every", 0) >= 10, "battlearcher i-frame spacing");
        // 마지막 피해 틱 = t · 첫 피해 = w (RpgJobTimingTests 와 같은 값 — 다시 쓰는 스킬은 12틱마다 다시 누름 · 화살이 2.5칸 앞 과녁에 닿는 1틱)
        check(near(sh.num("t", 0), 12 + 1) && near(sh.num("w", 0), sh.num("t", 0)) && near(st.num("shotAt", 0) + 1 + st.num("sameGap", 0) * st.num("bounces", 0), st.num("t", 0))
                && near(su.num("w", 0), su.num("gap", 0) + 2) && near(su.num("t", 0), su.num("w", 0) + su.num("burstAt", 0)), "sniper t 13 · 24 · 32 (w 13 · 22 — 사일런트 아울: 다시 누를 수 있는 20 에 눌러 (CI 42332661771) 2틱 날아감 · 10틱 뒤 터짐)");
        check(near(bs.num("launchAt", 0) + bs.num("flight", 0), bs.num("t", 0)) && near(bp.num("fireAt", 0), bp.num("t", 0)) && near(bp.num("fireAt", 0), bp.num("w", 0))
                && near(bu.num("finalAt", 0), bu.num("t", 0)) && near(bu.num("first", 0), bu.num("w", 0)), "battlearcher t 6 · 6 · 70 (w 6 · 10)");
        // 특성: 스나이퍼 = 궁수의 정령 교감 (2026-11-07 3판 — 호흡에서 이름 · 그림만, 같은 +4% · 사거리 +1칸) 최대 8 + 급소 사격, 배틀 아처 = 난사 (세 갈래 — 평타 50% · 5칸, 3칸 안 +20% · 10칸 밖 −30%)
        check(sn.trait().name().equals(ar.trait().name()) && sn.trait().max() == 8 && near(sn.trait().def().numOr("perStack", 0), ar.trait().def().numOr("perStack", -1))
                && near(sn.trait().def().numOr("rangePer", 0), ar.trait().def().numOr("rangePer", -1)) && near(sn.trait().def().numOr("snipeUse", 0), 3) && sn.trait().name().equals("정령 교감"), "정령 교감 8 · 급소 사격");
        check(ba.trait().name().equals("난사") && ba.trait().max() == 3 && near(ba.trait().def().numOr("side", 0), 0.5) && near(ba.trait().def().numOr("sideRange", 0), 5)
                && near(ba.trait().def().numOr("near", 0), 3) && near(ba.trait().def().numOr("nearBonus", 0), 0.2) && near(ba.trait().def().numOr("far", 0), 10)
                && near(ba.trait().def().numOr("farCut", 0), 0.3), "난사 세 갈래 · +20% · −30%");
        // 유틸 · 범위: 하트시커 단일 · 스플린터 샷 좁은 범위 (60° · 7.5칸) · 밀쳐냄, 트릭 샷 · 호넷 스웜은 넓음
        check("single".equals(Jobs.areaKind(sh)) && "narrow".equals(Jobs.areaKind(bp)) && Jobs.areaKind(st) == null && Jobs.areaKind(bs) == null && Jobs.utils(bp).equals(List.of("knock"))
                && Jobs.utils(bu).equals(List.of("knock")) && Jobs.utils(st).isEmpty() && Jobs.utils(sh).isEmpty(), "archer advanced utils · area");
        check(Jobs.windupTicks(sh) == 13 && Jobs.windupTicks(su) == 22 && Jobs.windupTicks(bu) == 10 && Jobs.windupTicks(bp) == 6, "windups 13 · 22 · 10 · 6");
        check(Jobs.upKey(ss, "arrow") != null && Jobs.upKey(bd, "arrow") != null && Jobs.upKey(sm, "dur") != null && Jobs.upKey(bt, "dur") != null && Jobs.upKeys(sh).isEmpty(),
                "archer advanced up keys");
        check(sm.total() <= 0 && bt.total() <= 0 && !Jobs.stanceBudget(sm) && !Jobs.stanceBudget(bt), "헌터스 마크 · 스톰 스트라이드 강화 (배율 합 없음)");
        check(Jobs.skillLine(sm).equals("헌터스 마크 · 30초") && Jobs.skillLine(bs).startsWith("호넷 스웜 · 6초 · 배율 "), "archer advanced skill line");
        check(sn.color()[0][1] > 0.9 && sn.color()[1][0] > 0.9 && ba.color()[0][2] > 0.9 && ba.color()[1][0] > 0.9 && ba.color()[1][1] > 0.9, "colors: lime · gold / storm blue · lightning");
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
        // 0.51.0 재설계안 2판 (2026-11-06 사용자 "다음 작업" — 매지션 차례): 아크메이지 스타 체인 · 헤일로 배리어 · 스타 볼텍스 · 셀레스티얼 오빗 (태세 칸 몫) · 콘스텔레이션
        // (2026-11-07 3판 — 스펠 서지 · 셀레스티얼 이글 대신), 네크로맨서 그레이브 콜 · 그레이브야드 · 소울 미스트 · 블라이트 (태세 칸 몫 12 × 1.4) · 팬텀 갤리온 — 부분 합 = 배율 합
        List<String> amIds = new ArrayList<>();
        List<String> ncIds = new ArrayList<>();
        for (Jobs.Skill k : am.skills()) amIds.add(k.id());
        for (Jobs.Skill k : nc.skills()) ncIds.add(k.id());
        check(amIds.equals(List.of("arcmage_chain", "arcmage_halo", "arcmage_vortex", "arcmage_orbit", "arcmage_ult"))
                && ncIds.equals(List.of("necromancer_call", "necromancer_yard", "necromancer_mist", "necromancer_blight", "necromancer_ult")), "mage advanced skill ids (2판)");
        Jobs.Skill ac = am.skill(0), ah = am.skill(1), av = am.skill(2), as = am.skill(3), au = am.skill(4);
        Jobs.Skill nk = nc.skill(0), ny = nc.skill(1), nm = nc.skill(2), nb = nc.skill(3), nu = nc.skill(4);
        check(near(ac.num("first", 0) + ac.num("jump", 0) * ac.num("jumps", 0), 7) && near(ah.num("out", 0) + ah.num("back", 0), 4.2)
                && near(av.num("pulse", 0) * av.num("pulses", 0) + av.num("collapse", 0), 12.6) && near(au.num("star", 0) * au.num("stars", 0) + au.num("burst", 0), 56),
                "arcmage totals 7 · 4.2 · 12.6 · 56");
        check(near(nk.num("smash", 0), 7) && near(ny.num("rise", 0) + ny.num("crumble", 0), 4.2) && near(nm.num("burst", 0) + nm.num("mist", 0) * nm.num("pulses", 0), 12.6)
                && near(nu.num("ram", 0) + nu.num("slam", 0) + nu.num("final", 0), 56), "necromancer totals 7 · 4.2 · 12.6 · 56");
        // 블라이트 · 셀레스티얼 오빗 = 태세 칸 몫 (30초 12 × 매지션 세기 1.4) · 직업 맞춤에서 뺌
        check(Jobs.stanceBudget(nb) && near(nb.total(), 16.8) && near(nb.num("tick", 0) * nb.num("ticks", 0), 16.8) && Jobs.stanceBudget(as) && near(as.total(), 16.8)
                && near(as.num("shot", 0) * as.num("shots", 0), 16.8), "블라이트 · 셀레스티얼 오빗 16.8 (태세 칸 몫)");
        // 같은 적을 여러 번 치는 것은 10틱 넘게
        // (콘스텔레이션의 별빛은 적마다 첫 별빛에 한 번 — 숫자만 별빛마다)
        check(av.num("every", 0) >= 10 && av.num("collapseAt", 0) - av.num("first", 0) - (av.num("pulses", 0) - 1) * av.num("every", 0) >= 10 && as.num("every", 0) >= 10
                && au.num("burstAt", 0) - au.num("w", 0) >= 10 && ah.num("t", 0) - ah.num("w", 0) >= 10 && ac.num("perFoe", 0) == 2, "arcmage i-frame spacing");
        check(ny.num("dur", 0) >= 10 && nm.num("first", 0) - nm.num("throwAt", 0) - nm.num("flight", 0) >= 10 && nm.num("every", 0) >= 10 && nb.num("every", 0) >= 10
                && nu.num("dropAt", 0) - nu.num("emergeAt", 0) - nu.num("sail", 0) >= 10 && nu.num("finalAt", 0) - nu.num("dropAt", 0) >= 10, "necromancer i-frame spacing");
        // 마지막 피해 틱 = t · 첫 피해 = w (RpgJobTimingTests 와 같은 값 — 스타 체인은 과녁 하나면 두 번째가 무적 시간 뒤 12)
        check(near(ac.num("hitAt", 0) + 10, ac.num("t", 0)) && near(av.num("collapseAt", 0), av.num("t", 0)) && near(av.num("first", 0), av.num("w", 0))
                && near(as.num("first", 0) + (as.num("shots", 0) - 1) * as.num("every", 0), as.num("t", 0)) && near(as.num("first", 0), as.num("w", 0))
                && near(au.num("burstAt", 0), au.num("t", 0)) && near(au.num("formAt", 0) + au.num("fall", 0), au.num("w", 0)), "arcmage t 12 · 70 · 190 · 56 (w 10 · 10 · 10)");
        check(near(nk.num("smashAt", 0), nk.num("t", 0)) && near(nk.num("smashAt", 0), nk.num("w", 0)) && near(ny.num("riseAt", 0) + ny.num("dur", 0), ny.num("t", 0))
                && near(ny.num("riseAt", 0), ny.num("w", 0)) && near(nm.num("first", 0) + (nm.num("pulses", 0) - 1) * nm.num("every", 0), nm.num("t", 0))
                && near(nm.num("throwAt", 0) + nm.num("flight", 0), nm.num("w", 0)) && near(nb.num("every", 0) * nb.num("ticks", 0), nb.num("t", 0))
                && near(nb.num("every", 0), nb.num("w", 0)) && near(nu.num("finalAt", 0), nu.num("t", 0)) && near(nu.num("emergeAt", 0), nu.num("w", 0)),
                "necromancer t 6 · 66 · 38 · 200 · 56 (w 6 · 6 · 8 · 10 · 10)");
        // 특성: 아크메이지 = 매지션의 주문 연쇄 (같은 +12% · 완드 −0.3초) 최대 5 + 별자리, 네크로맨서 = 망자의 군대 (최대 4 · 20초 · 위력 × 0.4 · 1.5초마다) + 무덤 (10초)
        check(am.trait().name().equals(mg.trait().name()) && am.trait().max() == 5 && near(am.trait().def().numOr("perChain", 0), mg.trait().def().numOr("perChain", -1))
                && near(am.trait().def().numOr("wandCut", 0), mg.trait().def().numOr("wandCut", -1)) && near(am.trait().def().numOr("star", 0), 2.0), "주문 연쇄 5 · 별자리");
        check(nc.trait().name().equals("망자의 군대") && nc.trait().max() == 4 && near(nc.trait().def().numOr("crewFor", 0), 400) && near(nc.trait().def().numOr("crewHit", 0), 0.4)
                && near(nc.trait().def().numOr("crewEvery", 0), 30) && near(nc.trait().def().numOr("graveFor", 0), 200), "망자의 군대 4 · 20초 · 위력 × 0.4 · 무덤 10초");
        // 유틸 · 범위: 헤일로 배리어 보호막 (15%) · 스타 볼텍스 끌당 · 그레이브야드 가둠 (10%) · 소울 미스트 둔화 · 그레이브 콜은 단일
        check(Jobs.utils(ah).equals(List.of("shield")) && near(Jobs.utilCost(ah), 0.15) && Jobs.utils(av).equals(List.of("pull")) && Jobs.utils(ny).equals(List.of("taunt"))
                && near(Jobs.utilCost(ny), 0.1) && Jobs.utils(nm).equals(List.of("slow")) && Jobs.utils(au).equals(List.of("lift")) && Jobs.utils(nu).equals(List.of("knock"))
                && "single".equals(Jobs.areaKind(nk)) && Jobs.areaKind(ac) == null && "narrow".equals(Jobs.areaKind(as)) && Jobs.areaKind(au) == null,
                "mage advanced utils · area");
        check(Jobs.windupTicks(au) == 10 && Jobs.windupTicks(as) == 10 && Jobs.windupTicks(nu) == 10 && Jobs.windupTicks(nk) == 6 && Jobs.windupTicks(nm) == 8,
                "windups 10 · 10 · 10 · 6 · 8");
        check(Jobs.upKeys(as).isEmpty() && Jobs.upKeys(ah).isEmpty() && Jobs.upKeys(nb).isEmpty(), "up keys: none on the stance-budget slot");
        check(Jobs.skillLine(as).startsWith("셀레스티얼 오빗 · 30초 · 배율 ") && Jobs.skillLine(au).startsWith("콘스텔레이션 · 60초 · 배율 ")
                && Jobs.skillLine(nk).startsWith("그레이브 콜 · 6초 · 배율 "), "mage advanced skill line");
        check(am.color()[0][2] > 0.9 && am.color()[1][0] > 0.9 && nc.color()[0][1] > 0.85 && nc.color()[1][0] > 0.85 && nc.color()[1][2] > 0.6,
                "colors: star blue · gold / poison green · bone");
    }

    /**
     * 0.51.0 마검사 2차 — 각성 아케인 블레이드 (2026-11-05 룬 스미스 대신 — 사용자 "컨셉도 이명에 안맞추고 하자" · "커다란 검기가 나오면서 때리면") · 반전 소드 콜러
     * (재설계안 2판 — 헥스 블레이드 대신). 부분 합 = 배율 합 (마검사와 같은 8.5 · 5.1 · 15.3 · 68) · 무적 시간 띄움 · t · w · 특성 · 마력 검술 (2차는 1차 마검사의 칼
     * 규칙을 이어 받음)
     */
    static void spellbladeAdvance(Jobs.Job sb, Jobs.Job ab, Jobs.Job sc) {
        check(ab != null && sc != null, "spellblade advanced jobs");
        if (ab == null || sc == null) return;
        check(Jobs.advance("spellblade", "awaken") == ab && Jobs.advance("spellblade", "invert") == sc && Jobs.baseOf(ab) == sb && Jobs.baseOf(sc) == sb, "spellblade advance");
        check(ab.display().equals("아케인 블레이드") && sc.display().equals("소드 콜러") && Jobs.find("아케인 블레이드") == ab && Jobs.find("arcaneblade") == ab
                && Jobs.find("룬 스미스") == null && Jobs.job("runeknight") == null && Jobs.find("스키르니르") == null && Jobs.find("소드 콜러") == sc
                && Jobs.find("swordcaller") == sc && Jobs.job("hexblade") == null, "spellblade display · find");
        List<String> abIds = new ArrayList<>();
        List<String> scIds = new ArrayList<>();
        for (Jobs.Skill k : ab.skills()) abIds.add(k.id());
        for (Jobs.Skill k : sc.skills()) scIds.add(k.id());
        check(abIds.equals(List.of("arcaneblade_crescent", "arcaneblade_cross", "arcaneblade_tempest", "arcaneblade_rift", "arcaneblade_ult")), "arcane blade skills " + abIds);
        // 디멘션 슬래시 (0.51.0 — 스펠 오버드라이브 대신): 가르기 + 닫힘 = 태세 칸 몫 12 × 1.7 · 16틱 사이 · 끌당
        Jobs.Skill rf = ab.skill(3);
        check(near(rf.num("slash", 0) + rf.num("close", 0), 20.4) && rf.num("closeAt", 0) - rf.num("slashAt", 0) >= 10 && near(rf.num("slashAt", 0), rf.num("w", 0))
                && near(rf.num("closeAt", 0), rf.num("t", 0)) && Jobs.utils(rf).equals(List.of("pull")) && Jobs.areaKind(rf) == null, "디멘션 슬래시 6.4 + 14 · w 6 · t 22");
        check(scIds.equals(List.of("swordcaller_strike", "swordcaller_shift", "swordcaller_orbit", "swordcaller_twin", "swordcaller_ult")), "sword caller skills " + scIds);
        for (int i : new int[] {0, 1, 2, 4}) check(near(ab.skill(i).total(), sb.skill(i).total()) && near(sc.skill(i).total(), sb.skill(i).total()), "spellblade totals slot " + i);
        // 아케인 블레이드: 가로 검기 + 세로 검기 · X 새김 + X 터짐 · 한 바퀴 + 폭풍 · 내려침 + 솟구침
        check(near(ab.skill(0).num("sweep", 0) + ab.skill(0).num("cleave", 0), 8.5) && near(ab.skill(1).num("path", 0) + ab.skill(1).num("cross", 0), 5.1)
                && near(ab.skill(2).num("spin", 0) + ab.skill(2).num("burst", 0), 15.3) && near(ab.skill(4).num("slam", 0) + ab.skill(4).num("rift", 0), 68),
                "arcane blade totals 8.5 · 5.1 · 15.3 · 68");
        // 소드 콜러: 베어라 넷 · 꿰뚫음 + 맴돌기 넷 · 칼날 감옥 여섯 + 큰 한 방
        check(near(sum(sc.skill(0), "cuts"), 8.5) && sc.skill(0).def().list("cuts").size() == 4 && near(sc.skill(2).num("pierce", 0) + sum(sc.skill(2), "spins"), 15.3)
                && near(sum(sc.skill(4), "slashes") + sc.skill(4).num("final", 0), 68) && sc.skill(4).def().list("slashes").size() == 6, "sword caller totals 8.5 · 5.1 · 15.3 · 68");
        // 같은 적을 여러 번 치는 것은 10틱 넘게 (숫자만 여러 줄)
        check(ab.skill(0).num("gap", 0) >= 10 && ab.skill(1).num("crossAt", 0) - ab.skill(1).num("hitAt", 0) >= 10
                && ab.skill(2).num("burstAt", 0) - ab.skill(2).num("spinAt", 0) >= 10 && ab.skill(4).num("t", 0) - ab.skill(4).num("w", 0) >= 10, "arcane blade i-frame spacing");
        check(sc.skill(0).num("groupGap", 0) >= 10 && sc.skill(2).num("every", 0) >= 10 && sc.skill(4).num("every", 0) >= 10
                && sc.skill(4).num("finalAt", 0) - sc.skill(4).num("surroundAt", 0) - 5 * sc.skill(4).num("every", 0) >= 10, "sword caller i-frame spacing");
        // 마지막 피해 틱 = t · 첫 피해 = w (RpgJobTimingTests 와 같은 값 — 앞 2.5칸 과녁)
        Jobs.Skill cr = ab.skill(0);
        double first = cr.num("charge", 0) + Math.ceil((2.5 - cr.num("start", 0)) / cr.num("speed", 1));
        check(near(first, 7) && !cr.def().has("w") && near(first + cr.num("gap", 0), cr.num("t", 0)), "크레센트 엣지 첫 7 · t 17");
        Jobs.Skill cx = ab.skill(1);
        check(near(cx.num("crossAt", 0), cx.num("t", 0)) && cx.num("hitAt", 0) < 8 && !cx.def().has("w"), "팬텀 크로스 t 12 (첫 1)");
        Jobs.Skill te = ab.skill(2);
        check(near(te.num("spinAt", 0), te.num("w", 0)) && near(te.num("burstAt", 0), te.num("t", 0)), "아케인 템페스트 w 8 · t 20");
        Jobs.Skill wc = ab.skill(4);
        check(near(wc.num("slamAt", 0), wc.num("w", 0)) && near(wc.num("riftAt", 0) + Math.ceil((2.5 - wc.num("start", 0)) / wc.num("riftSpeed", 1)), wc.num("t", 0)),
                "월드 클리버 w 21 · t 31");
        Jobs.Skill st = sc.skill(0);
        check(near(st.num("launch", 0) + 1 + st.num("groupGap", 0), st.num("t", 0)) && st.def().list("cuts").size() == 2 * st.num("group", 0), "베어라 t 13");
        check(near(sc.skill(1).num("start", 0) + Math.ceil(2.5 / sc.skill(1).num("speed", 1)), sc.skill(1).num("t", 0)), "데려가라 t 3");
        Jobs.Skill ob = sc.skill(2);
        check(near(ob.num("launch", 0) + 1 + ob.def().list("spins").size() * ob.num("every", 0), ob.num("t", 0)) && ob.num("lines", 0) * ob.def().list("spins").size() == 12,
                "꿰뚫고 맴돌아라 t 43 · 칼질 열둘");
        check(near(sc.skill(4).num("finalAt", 0), sc.skill(4).num("t", 0)) && near(sc.skill(4).num("surroundAt", 0), sc.skill(4).num("w", 0))
                && sc.skill(4).num("lines", 0) * sc.skill(4).def().list("slashes").size() == 30, "칼날 감옥 t 70 · w 10 · 칼질 서른");
        // 특성: 아케인 블레이드 = 마력 각인 최대 5 · 번짐 0.5 · 3칸 (다섯이 차면 한꺼번에 — 거대한 마력 대검), 소드 콜러 = 스스로 싸우는 칼 (최대 5 · 하나당 공격 속도
        // +20% → ×2)
        check(ab.trait().name().equals("마력 각인") && ab.trait().max() == 5 && near(ab.trait().def().numOr("markBurst", 0), sb.trait().def().numOr("markBurst", -1))
                && near(ab.trait().def().numOr("spread", 0), 0.5) && near(ab.trait().def().numOr("spreadRadius", 0), 3) && !ab.trait().def().has("heatFor"),
                "마력 각인 5 · 번짐 0.5 · 3칸");
        check(sc.trait().name().equals("스스로 싸우는 칼") && sc.trait().max() == 5 && near(1 + sc.trait().max() * sc.trait().def().numOr("per", 0), 2)
                && near(sc.trait().def().numOr("range", 0), 10), "스스로 싸우는 칼 5 · ×2 · 10칸");
        // 마력 검술: 2차는 특성에 magicSword 가 없어도 1차의 칼 규칙 (요구 힘 = 힘 + 마력 · 피해 힘 = 힘 + 마력 × 0.5)
        Map<String, Double> s0 = new java.util.LinkedHashMap<>();
        s0.put("str", 10.0);
        s0.put("int", 40.0);
        for (Jobs.Job a : List.of(ab, sc)) {
            check(Jobs.magicSword(a, "sword") && !Jobs.magicSword(a, "axe"), a.id() + " magic sword");
            check(near(Jobs.reqStats(a, "sword", s0).get("str"), 50) && near(Jobs.damageStats(a, "sword", s0).get("str"), 30), a.id() + " magic sword stats");
        }
        // 유틸: 크레센트 엣지 없음 · 팬텀 크로스 순간 이동 · 아케인 템페스트 끌당 + 띄움 · 월드 클리버 띄움 / 데려가라 순간 이동 · 칼날 감옥 묶음 3초
        check(Jobs.utils(ab.skill(0)).isEmpty() && Jobs.utils(ab.skill(1)).equals(List.of("blink")) && Jobs.utils(ab.skill(2)).equals(List.of("pull", "lift"))
                && Jobs.utils(ab.skill(4)).equals(List.of("lift")) && Jobs.utils(sc.skill(1)).equals(List.of("blink"))
                && Jobs.utils(sc.skill(4)).equals(List.of("root3")) && Jobs.utils(sc.skill(0)).isEmpty() && Jobs.utils(sc.skill(2)).isEmpty(), "spellblade advanced utils");
        check(Jobs.windupTicks(ab.skill(0)) == 0 && Jobs.windupTicks(ab.skill(2)) == 8 && Jobs.windupTicks(ab.skill(4)) == 21 && Jobs.windupTicks(sc.skill(4)) == 10,
                "windups 0 · 8 · 21 · 10");
        check("narrow".equals(Jobs.areaKind(ab.skill(0))) && "narrow".equals(Jobs.areaKind(ab.skill(1))) && Jobs.areaKind(ab.skill(2)) == null && Jobs.areaKind(ab.skill(4)) == null
                && "single".equals(Jobs.areaKind(sc.skill(2))) && "single".equals(Jobs.areaKind(sc.skill(4))) && Jobs.slotRole(sc.skill(2)).equals("주력")
                && !Jobs.slotRole(ab.skill(2)).equals("주력"), "areas · 주력");
        check(Jobs.upKeys(ab.skill(3)).isEmpty() && Jobs.upKeys(sc.skill(3)).size() == 1 && Jobs.upKey(sc.skill(3), "echo") != null, "stance up keys");
        check(Jobs.skillLine(ab.skill(3)).startsWith("디멘션 슬래시 · 30초 · 배율 ") && Jobs.skillLine(sc.skill(0)).startsWith("플라잉 블레이드 · 6초 · 배율 "),
                "spellblade advanced skill line");
        check(ab.color()[0][2] > 0.9 && ab.color()[0][0] > 0.6 && ab.color()[1][1] > 0.9 && ab.color()[1][2] > 0.9 && sc.color()[0][0] < 0.3 && sc.color()[0][1] > 0.8
                && sc.color()[1][2] > 0.9, "colors: purple · cyan / teal · white");
    }

    /**
     * 0.51.0 전사 2차 — 재설계안 2판 (claude/job-advance-redo.md): 워로드 새 스킬 다섯 · 2026-11-07 재설계안 3판 프로스트 레이더 (글래디에이터 대신 — 사용자 "컨셉이
     * 마음에 안듬"). 부분 합 = 배율 합 (전사와 같은 6.5 · 3.9 · 11.7 · 52 — 대지 가르기는 끝까지 모은 값, 마지막 휘두름은 바깥 날 · 안쪽은 절반), 무적 시간 띄움 ·
     * 다시 누름 · t · w · 특성 (분노 · 곰의 화신 / 빙결 5 · 얼어붙음 · 깨짐) · 단일 스킬의 3번 칸 이름 (주력)
     */
    static void warriorAdvance(Jobs.Job wa, Jobs.Job wl, Jobs.Job fr) {
        for (int i : new int[] {0, 1, 2, 4}) check(near(wl.skill(i).total(), wa.skill(i).total()) && near(fr.skill(i).total(), wa.skill(i).total()), "warrior totals slot " + i);
        List<String> wlIds = new ArrayList<>();
        List<String> frIds = new ArrayList<>();
        for (Jobs.Skill k : wl.skills()) wlIds.add(k.id());
        for (Jobs.Skill k : fr.skills()) frIds.add(k.id());
        check(wlIds.equals(List.of("warlord_rend", "warlord_slumber", "warlord_hug", "warlord_stomp", "warlord_ult")), "warlord skills " + wlIds);
        check(frIds.equals(List.of("frostraider_throw", "frostraider_leap", "frostraider_shatter", "frostraider_jotunn", "frostraider_ult")), "frostraider skills " + frIds);
        // 0.51.0 4번 칸 (사용자 "직업 중에 구지 강화 시키면 어색한 애들은 빼자"): 베어 스톰프 내려찍기 + 갈라짐 = 12 × 1.3 · 프로스트 레이더는 요툰 그립 태세 (피해 없음)
        Jobs.Skill bs = wl.skill(3);
        Jobs.Skill jg = fr.skill(3);
        check(near(bs.num("stomp", 0) + bs.num("after", 0), 15.6) && bs.num("afterAt", 0) - bs.num("landAt", 0) >= 10 && near(bs.num("landAt", 0), bs.num("w", 0))
                && near(bs.num("afterAt", 0), bs.num("t", 0)) && Jobs.utils(bs).equals(List.of("leap", "lift")), "베어 스톰프 11 + 4.6 · w 16 · t 26");
        check(jg.total() == 0 && near(jg.num("dur", 0), 160) && near(jg.num("echo", 0), 0.6) && near(jg.num("guard", 0), 0.1) && Jobs.upKey(jg, "echo") != null,
                "요툰 그립 8초 태세 · 거인의 일격 60% · −10%");
        // 워로드: 대지 가르기 (끝까지 모으면 6.5 · 처음은 절반) · 동면 3.9 · 곰의 포옹 11.7 · 마지막 휘두름 바깥 52 / 안쪽 26
        check(near(wl.skill(0).total(), 6.5) && near(wl.skill(0).num("minPower", 0), 0.5) && wl.skill(0).def().numbersOrNull("len").size() == 2
                && near(wl.skill(4).num("outer", 0), 52) && near(wl.skill(4).num("inner", 0) * 2, wl.skill(4).num("outer", 0)), "warlord totals 6.5 · 52 (바깥) · 26 (안쪽)");
        // 프로스트 레이더: 던짐 + 되부름 = 6.5 · 내리찍기 3.9 · 내려찍기 + 깨부숨 = 11.7 · 내리꽂힘 + 깨짐 = 52
        Jobs.Skill th = fr.skill(0);
        Jobs.Skill lp = fr.skill(1);
        Jobs.Skill sh = fr.skill(2);
        Jobs.Skill ul = fr.skill(4);
        check(near(th.num("throw", 0) + th.num("recall", 0), 6.5) && near(lp.num("slam", 0), 3.9) && near(sh.num("slam", 0) + sh.num("shatter", 0), 11.7)
                && near(ul.num("drop", 0) + ul.num("shatter", 0), 52), "frostraider totals 2.5 + 4 · 3.9 · 4.2 + 7.5 · 20 + 32");
        // 같은 적을 여러 번 치는 것은 10틱 넘게: 되부름 (다시 누를 수 있는 가장 이른 틱 gap + 뽑힘 pull) · 샤터 · 핌불윈터
        check(12 + th.num("pull", 0) - th.num("hitAt", 0) >= 11 && th.num("gap", 0) >= 10 && sh.num("shatterAt", 0) - sh.num("slamAt", 0) >= 10
                && ul.num("shatterAt", 0) - ul.num("dropAt", 0) >= 10, "frostraider i-frame spacing");
        check(wl.skill(0).num("gap", 0) >= 6 && wl.skill(0).num("charge", 0) > 12 && wl.skill(2).num("slamAt", 0) > wl.skill(2).num("pullAt", 0) + wl.skill(2).num("pullTicks", 0),
                "warlord timing order");
        // 마지막 피해 틱 = t (RpgJobTimingTests 와 같은 값 — 다시 누르는 스킬은 12틱마다 다시 누름: 대지 가르기는 12틱 모아 내려침 → slamAt 뒤 · 되부름은 12틱에 → pull 뒤)
        check(near(12 + wl.skill(0).num("slamAt", 0), wl.skill(0).num("t", 0)) && near(wl.skill(0).num("w", 0), wl.skill(0).num("t", 0)), "대지 가르기 t 16 = 12 + 4");
        check(near(wl.skill(1).num("dur", 0), wl.skill(1).num("t", 0)) && near(wl.skill(1).num("w", 0), wl.skill(1).num("t", 0)), "동면 t 40 = 선딜");
        check(near(wl.skill(2).num("slamAt", 0), wl.skill(2).num("t", 0)) && near(wl.skill(2).num("w", 0), wl.skill(2).num("t", 0)), "곰의 포옹 t 12");
        check(near(wl.skill(4).num("windup", 0), wl.skill(4).num("t", 0)) && near(wl.skill(4).num("w", 0), wl.skill(4).num("t", 0)), "마지막 휘두름 t 24 = 선딜");
        check(near(12 + th.num("pull", 0), th.num("t", 0)) && near(th.num("hitAt", 0), th.num("w", 0)) && th.num("gap", 0) <= 12, "프로스트 바이트 t 17 = 12 + 5 · w 6");
        check(near(lp.num("leap", 0), lp.num("t", 0)) && near(lp.num("leap", 0), lp.num("w", 0)), "아이스 바운드 t 10 = 착지");
        check(near(sh.num("shatterAt", 0), sh.num("t", 0)) && near(sh.num("slamAt", 0), sh.num("w", 0)), "샤터 t 28 · w 8");
        check(near(ul.num("shatterAt", 0), ul.num("t", 0)) && near(ul.num("dropAt", 0), ul.num("w", 0)), "핌불윈터 t 46 · w 26");
        // 특성: 워로드 = 전사의 분노 + 곰의 화신 (분노 100 · 8초 · −15%), 프로스트 레이더 = 빙결 (최대 5 · 6초 · 1.5초 얼어붙음 · 깨짐 +30% · 조각 2칸 × 0.6)
        check(wl.trait().name().equals("분노") && wl.trait().max() == 100 && near(wl.trait().def().numOr("bearFor", 0), 160) && near(wl.trait().def().numOr("bearGuard", 0), 0.15)
                && near(wl.trait().def().numOr("onHit", 0), wa.trait().def().numOr("onHit", -1)), "분노 · 곰의 화신");
        check(fr.trait().name().equals("빙결") && fr.trait().max() == 5 && near(fr.trait().def().numOr("frostFor", 0), 120) && near(fr.trait().def().numOr("freezeFor", 0), 30)
                && near(fr.trait().def().numOr("shatter", 0), 0.3) && near(fr.trait().def().numOr("shardRadius", 0), 2) && near(fr.trait().def().numOr("shard", 0), 0.6)
                && near(fr.trait().def().numOr("bossTaken", 0), 0.1), "빙결 5 · 6초 · 얼어붙음 1.5초 · 깨짐 +30% · 조각 2칸 × 0.6 · 보스 +10%");
        // 유틸 · 범위: 대지 가르기 띄움 · 동면 피해 감소 + 밀쳐냄 · 곰의 포옹 끌당 + 기절 1초 · 마지막 휘두름 피해 감소 + 띄움 / 아이스 바운드 도약 + 보호막 · 샤터 · 핌불윈터
        // 묶음 — 프로스트 바이트 · 샤터는 단일 (범위 보정 +15%) · 아이스 바운드 · 핌불윈터는 좁은 범위
        check(Jobs.utils(wl.skill(0)).equals(List.of("lift")) && "narrow".equals(Jobs.areaKind(wl.skill(0))) && Jobs.windupTicks(wl.skill(0)) == 16, "대지 가르기 util · narrow · w 16");
        check(Jobs.utils(wl.skill(1)).equals(List.of("reduce", "knock")) && near(Jobs.utilCost(wl.skill(1)), 0.15) && near(Jobs.windupBonus(wl.skill(1)), 0.3)
                && near(wl.skill(1).num("guard", 0), 0.6), "동면 reduce · knock · 선딜 +30% (상한)");
        check(Jobs.utils(wl.skill(2)).equals(List.of("pull", "stun1")) && near(wl.skill(2).num("stun", 0), 20) && Jobs.areaKind(wl.skill(2)) == null, "곰의 포옹 pull · stun1");
        check(Jobs.utils(wl.skill(4)).equals(List.of("reduce", "lift")) && near(wl.skill(4).num("guard", 0), 0.5) && Jobs.windupTicks(wl.skill(4)) == 24, "마지막 휘두름 −50% · w 24");
        check("single".equals(Jobs.areaKind(th)) && "single".equals(Jobs.areaKind(sh)) && "narrow".equals(Jobs.areaKind(lp)) && "narrow".equals(Jobs.areaKind(ul)),
                "frostraider area");
        check(Jobs.utils(th).isEmpty() && Jobs.utils(lp).equals(List.of("leap", "shield")) && Jobs.utils(sh).equals(List.of("root", "slow"))
                && Jobs.utils(ul).equals(List.of("root")) && near(sh.num("chill", 0), 60), "frostraider utils");
        // 칸 이름: 단일 스킬의 3번 칸은 주력 (재설계안 2판 기준 8) · 나머지는 그대로
        check(Jobs.slotRole(sh).equals("주력") && Jobs.slotRole(wl.skill(2)).equals("광역") && Jobs.slotRole(th).equals(Jobs.slotRole(0)), "slot role 주력 for single slot 3");
        check(Jobs.upKeys(wl.skill(3)).isEmpty() && Jobs.upKey(wl.skill(2), "stun") != null && Jobs.upKey(sh, "chill") != null, "up keys");
        check(Jobs.skillLine(wl.skill(3)).startsWith("베어 스톰프 · 30초 · 배율 ") && Jobs.skillLine(th).startsWith("프로스트 바이트 · 6초 · 배율 ")
                && Jobs.skillLine(jg).equals("요툰 그립 · 30초"), "warrior advanced skill line");
        check(wl.color()[0][0] > 0.9 && fr.color()[0][2] > 0.9 && fr.color()[0][0] < 0.7 && fr.color()[1][1] > 0.95, "colors: orange · brown / ice · frost white");
        for (Jobs.Skill k : fr.skills()) check(!k.desc().contains("롤:"), "no LoL name in desc " + k.id());
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
                // 2026-11-07 원소 배율 (원소 스킬 × 0.95 · 무속성 1 — 직업 맞춤 밖)
                double em = Jobs.element(k) == null ? 1 : 0.95;
                check(near(Jobs.elementMult(k), em), k.id() + " element mult " + Jobs.elementMult(k));
                check(near(Jobs.damageScale(k), (1 - Jobs.utilCost(k)) * tm * Jobs.jobNorm(j) * am * em), k.id() + " scale = util × time × norm × area × element");
                check(Jobs.jobOf(k) == j, k.id() + " job");
                if (Jobs.stanceBudget(k)) continue; // 0.51.0 태세 칸 몫 (직업 맞춤 밖)
                was += k.total() * Jobs.spreadMult(k) / k.cd();
                now += k.total() * Jobs.damageScale(k) / Jobs.areaMult(k) / Jobs.elementMult(k) / k.cd();
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
