package mmorpg.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 2026-11-28 룬 8칸 교차 사용 계산 (사용자 "검사가 전사나 탱 룬 쓰는것도 밸런스에 고려해야 함 반대로 전사나 탱커가 검사룬 쓰는것도" → 고른 것 "누가 써도 같게 +
 * 계산 시험": 룬은 누가 써도 같고, 직업마다 룬 8칸 최고 조합을 셈해 범위를 벗어나는 룬만 고침).
 *
 * <p>모델 (과녁 하나 · 늘 침 · 0돌 · 40렙 125포인트 — 직업 스탯이 하나면 125, 둘이면 83 : 42 · 40렙 초월 무기 (2차 직업은 1차의 무기 · 스탯) · 강화 없음):
 * <ul>
 *   <li>평타/초 = 무기 피해 × 스탯 배율 (Combat.statMultiplier) × 치명타 기대 × 초당 횟수 (근접 20 ÷ 평타 사이 (MOTION.interval ÷ 공속) · 활 · 완드
 *       20 ÷ (20틱 × rangedSlow ÷ 공속))</li>
 *   <li>직업/초 = Σ 피해 스킬의 배율 합 × 피해 배율 (Jobs.damageScale) ÷ 쿨 × 직업 위력</li>
 *   <li>룬/초 = 배율 합 × 피해 배율 (Runes.damageScale) ÷ 쿨 × 룬 위력 (네 스탯 합 — 모두 같음) − 시전 초 × 평타/초 ÷ 쿨 (시전 동안 평타를 못 침).
 *       원거리 직업 (역할에 "원거리") 은 근거리 룬을 뺌 (붙어야 맞음)</li>
 *   <li>지키는 값/초 = 등급 초당 값 × Σ 지키는 유틸 (버팀 · 회복 · 흡혈 · 받는 피해 감소 · 보호막 · 아군 보호) 의 값 × 쿨 맞춤 — 지킨 몫을 피해 예산으로 셈</li>
 *   <li>피해 조합 = 룬/초가 큰 8 · 버팀 조합 = 지키는 값이 큰 8 (같으면 룬/초)</li>
 * </ul>
 * 보는 것: ① 근접 · 원거리 직업의 피해 조합 룬/초가 비슷함 (거리 보정 근 ×1.2 만큼 근접이 조금 위) ② 버팀 조합은 피해를 내줌 (지키는 유틸은 공짜가 아님) ③ 지키는 유틸이
 * 있는 룬이 피해로도 맨 위가 아님 ④ 탱커 · 힐러 (수호기사 · 사제 · 세인트) 가 피해 조합을 껴도 검사보다 충분히 낮음 (역할 목표 탱커 0.92 · 힐러 0.96 ÷ 근접 딜러
 * 1.20 = 0.77 · 0.80). 직업 자체의 세기 (민첩 공속으로 평타가 두 배인 단검 · 활 직업 — 피에로 포함) 는 표에만 찍음 (룬 몫이 아님).
 */
public final class RuneLoadoutTest {
    private RuneLoadoutTest() {}

    static final Set<String> GUARD = Set.of("undying", "heal", "lifesteal", "reduce", "shield", "allyGuard");
    static final int SLOTS = 8;
    static int ok;
    static final List<String> BAD = new ArrayList<>();

    static void check(boolean c, String what) {
        if (c) ok++;
        else BAD.add(what);
    }

    /** 룬 하나의 값 (P/s · 시전 초 · 지키는 값/초) */
    record RV(Runes.Rune r, String name, String grade, String range, double cast, double dmg, double guard, List<String> utils) {}

    /** 직업 하나의 셈 */
    record JV(String id, String role, double basic, double job, double dmgRunes, double guardRunes, double guard, List<String> picks, boolean far) {
        double total() {
            return basic + job + dmgRunes;
        }
    }

    /** 등급의 초당 값 (예산 ÷ 기준 쿨 — tools/rune_set.py GRADE_BASE) */
    static double gradePerSec(String g) {
        Obj base = Rules.get().obj("RUNE_SET").obj("baseCd");
        double budget = switch (g) {
            case "common" -> 4;
            case "rare" -> 7;
            case "noble" -> 12;
            case "hero" -> 20;
            default -> 32;
        };
        return budget / base.num(g);
    }

    static List<RV> runes() {
        List<RV> out = new ArrayList<>();
        for (String fam : Runes.FAMILY_ORDER) {
            if (!Runes.isSet(fam)) continue;
            for (String g : Runes.GRADE_ORDER) {
                if (!Runes.hasGrade(fam, g)) continue;
                Runes.Rune r = Runes.parseRune(Runes.runeId(fam, g));
                double total = Runes.runeTotalMult(r.def());
                double dmg = total > 0 ? total * Runes.damageScale(r) / r.cd() : 0;
                double guard = 0;
                for (String u : Runes.utils(r)) if (GUARD.contains(u)) guard += Runes.utilValue(r, u) * Runes.utilCdFactor(r);
                Obj e = Runes.spreadEntry(r);
                // 2026-11-28 지원 룬 (피해 없음 — 예산 전부가 회복 · 버프): 지키는 몫 (회복 · 받는 피해 − · 정화 — tools/rune_set.py support_split 의 sup.guard)
                Obj sup = e == null ? null : e.objOrNull("sup");
                if (sup != null) guard += sup.numOr("guard", 0);
                out.add(new RV(r, r.famName(), g, e == null ? "?" : e.strOr("r", "?"), Runes.castSeconds(r), dmg, guard * gradePerSec(g), Runes.utils(r)));
            }
        }
        return out;
    }

    /** 직업의 스탯 (40렙 · 125포인트) */
    static Map<String, Double> statsOf(Jobs.Job b) {
        Map<String, Double> s = new HashMap<>();
        s.put("lvmax", 40.0);
        s.put("level", 40.0);
        List<String> st = b.stats();
        if (st.size() == 1) s.put(st.get(0), 125.0);
        else {
            s.put(st.get(0), 83.0);
            s.put(st.get(1), 42.0);
        }
        return s;
    }

    static JV jobOf(String id, List<RV> runes) {
        Jobs.Job j = Jobs.job(id);
        Jobs.Job b = j.advanced() ? Jobs.job(j.base()) : j;
        Map<String, Double> s = statsOf(b);
        String wt = b.weapons().get(0);
        Weapon w = Weapons.weaponFrom(wt, 40, "transcend");
        double hit = w.damage() * Combat.statMultiplier(s, w) * (1 + w.critChance() * Math.max(0, w.critMult() - 1));
        double speed = AttackSpeed.speed(s);
        double rate = Weapons.isRangedType(wt) ? 20.0 / (20 * AttackSpeed.rangedSlow() / speed) : 20.0 / Motions.attackInterval(speed);
        double basic = hit * rate;
        double P = Jobs.power(j, s);
        double job = 0;
        for (Jobs.Skill k : j.skills()) if (k.total() > 0 && k.cd() > 0) job += k.total() * Jobs.damageScale(k) / k.cd() * P;
        double rp = Runes.runePower(s, runes.get(0).r().family());
        boolean far = j.role().contains("원거리");
        List<RV> usable = new ArrayList<>(runes);
        if (far) usable.removeIf(v -> "melee".equals(v.range()));
        java.util.function.ToDoubleFunction<RV> val = v -> v.dmg() > 0 ? v.dmg() * rp - v.cast() * basic / v.r().cd() : 0;
        List<RV> byDmg = new ArrayList<>(usable);
        byDmg.sort(Comparator.comparingDouble(val).reversed());
        double dr = 0;
        List<String> picks = new ArrayList<>();
        for (int i = 0; i < Math.min(SLOTS, byDmg.size()); i++) {
            dr += val.applyAsDouble(byDmg.get(i));
            picks.add(byDmg.get(i).name());
        }
        List<RV> byGuard = new ArrayList<>(usable);
        byGuard.sort(Comparator.comparingDouble(RV::guard).reversed().thenComparing(Comparator.comparingDouble(val).reversed()));
        double gr = 0, gg = 0;
        for (int i = 0; i < Math.min(SLOTS, byGuard.size()); i++) {
            gr += Math.max(0, val.applyAsDouble(byGuard.get(i)));
            gg += byGuard.get(i).guard();
        }
        return new JV(id, j.role(), basic, job, dr, gr, gg, picks, far);
    }

    public static void main(String[] args) {
        List<RV> runes = runes();
        check(runes.size() >= 30, "set runes " + runes.size());
        System.out.println("== 룬 (P/s = 과녁 하나 초당 배율 · 지킴 = 지키는 유틸의 예산 몫/초)");
        for (RV v : runes) {
            System.out.printf(Locale.ROOT, "  %-9s %-10s %-5s 시전 %.1f  P/s %.3f  지킴 %.3f  %s%n", v.grade(), v.name(), v.range(), v.cast(), v.dmg(), v.guard(), v.utils());
        }
        for (String g : Runes.GRADE_ORDER) {
            List<Double> ds = new ArrayList<>();
            for (RV v : runes) if (v.grade().equals(g) && v.dmg() > 0) ds.add(v.dmg());
            if (ds.isEmpty()) continue;
            ds.sort(null);
            System.out.printf(Locale.ROOT, "  %-9s P/s 최소 %.3f · 가운데 %.3f · 최대 %.3f (예산/초 %.3f)%n", g, ds.get(0), ds.get(ds.size() / 2), ds.get(ds.size() - 1), gradePerSec(g));
        }

        List<String> ids = new ArrayList<>();
        for (String b0 : Jobs.visibleOrder()) {
            ids.add(b0);
            for (Jobs.Job a : Jobs.visibleAdvancedOf(b0)) ids.add(a.id());
        }
        Map<String, JV> jobs = new HashMap<>();
        for (String id : ids) jobs.put(id, jobOf(id, runes));
        double sw = jobs.get("swordsman").total();
        System.out.println("== 직업 (피해/초 — 40렙 125포인트 · 초월 무기 · 과녁 하나 · 0돌)");
        for (String id : ids) {
            JV v = jobs.get(id);
            System.out.printf(Locale.ROOT, "  %-13s 평타 %6.1f  직업 %6.1f  룬 %6.1f  합 %6.1f (검사 ×%.2f · 룬 %2.0f%%)  버팀 조합: 룬 %6.1f (×%.2f) 지킴 %.2f  [%s]%n", id, v.basic(),
                    v.job(), v.dmgRunes(), v.total(), v.total() / sw, v.dmgRunes() / v.total() * 100, v.guardRunes(), v.guardRunes() / v.dmgRunes(), v.guard(),
                    String.join(", ", v.picks()));
        }

        // ① 근접 · 원거리 직업의 피해 조합 (룬 몫) — 룬 위력이 같으므로 거리 · 시전 몫만큼만 다름
        double meleeMax = 0, farMin = Double.MAX_VALUE;
        for (JV v : jobs.values()) {
            if (v.far()) farMin = Math.min(farMin, v.dmgRunes());
            else meleeMax = Math.max(meleeMax, v.dmgRunes());
        }
        double ratio = meleeMax / farMin;
        System.out.printf(Locale.ROOT, "  근접 ÷ 원거리 피해 조합 (룬) ×%.3f%n", ratio);
        check(ratio >= 0.95 && ratio <= 1.12, "melee ÷ ranged rune loadout " + ratio);
        // ② 버팀 조합은 피해를 내줌 (지키는 룬을 쓸 수 있는 직업 — 2026-11-28 지키는 룬 여섯이 모두 근거리라 원거리 직업은 버팀 조합 = 피해 조합이었음 →
        // 2026-11-28 지원 룬 (그로브 블레싱 · 라이프 볼트 · 새크리드 서클 · 발키리 송 · 에덴 블룸 — 중 · 원거리) 로 모든 직업이 지키는 룬을 씀)
        for (JV v : jobs.values()) {
            check(v.guard() > 0, v.id() + " has guard runes (2026-11-28 지원 룬 — 원거리 직업도)");
            if (v.guard() > 0) check(v.guardRunes() <= v.dmgRunes() * 0.93, v.id() + " guard loadout costs damage " + v.guardRunes() / v.dmgRunes());
        }
        // ③ 지키는 유틸이 있는 룬이 P/s 맨 위 셋에 없음
        List<RV> top = new ArrayList<>(runes);
        top.sort(Comparator.comparingDouble(RV::dmg).reversed());
        for (int i = 0; i < 3; i++) check(top.get(i).guard() == 0, "guard rune in the damage top 3: " + top.get(i).name());
        // ④ 탱커 · 힐러가 피해 조합을 껴도 검사보다 충분히 낮음
        for (String id : List.of("guardian", "priest", "saint")) {
            JV v = jobs.get(id);
            if (v != null) check(v.total() / sw <= 0.85, id + " with damage runes ×" + v.total() / sw + " of swordsman");
        }
        System.out.println("RuneLoadoutTest: " + ok + " ok, " + BAD.size() + " failed");
        for (String b : BAD) System.out.println("  FAIL " + b);
        if (!BAD.isEmpty()) System.exit(1);
    }
}
