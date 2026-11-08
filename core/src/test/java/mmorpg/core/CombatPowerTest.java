package mmorpg.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 2026-11-08 총 전투력 (CombatPower — 보여주기 · 순위용, JUnit 없이 main — check-core.sh):
 * <ul>
 * <li>값이 오르면 전투력도 오름 (공격력 · 치명타 확률 · 치명타 배율 · 룬 피해 · 재사용 대기 · 최대 체력 · 받는 피해 감소 · 방어 · 이동 속도 하나씩)</li>
 * <li>공격 · 생존이 둘 다 두 배면 전투력도 두 배 (곱의 제곱근), 무기가 없어도 0 이 아님, 감소가 1 이어도 끝없이 커지지 않음</li>
 * <li>60렙 · 스탯 180 · 40렙 초월 무기: 무기 11종의 대표 투자 (한 스탯 · 두 스탯 2:1) 의 전투력이 가장 낮은 것의 1.6배 안</li>
 * <li>크기: 처음 (5렙 일반 칼 · 힘 15) 은 수백 ~ 3천, 끝 (100렙 · 스탯 300 · +10 초월 · 스탯 트리 상한) 은 3만 ~ 30만</li>
 * </ul>
 * 하나라도 틀리면 종료 코드 1.
 */
public final class CombatPowerTest {
    private CombatPowerTest() {}

    private static final List<String> BAD = new ArrayList<>();
    private static int ok;
    private static final Obj SHIELD = Rules.get().obj("SHIELD");

    private static void check(boolean cond, String what) {
        if (cond) ok++;
        else BAD.add(what);
    }

    private static Map<String, Double> stats(double str, double dex, double intel, double vit) {
        Map<String, Double> s = new LinkedHashMap<>();
        s.put("str", str);
        s.put("dex", dex);
        s.put("int", intel);
        s.put("vit", vit);
        return s;
    }

    /** 레벨 lv · 스탯 s 로 무기 w (강화 enh) 를 든 사람 — 스탯 트리 · 갑옷 없음 (방패는 상시 감소) */
    private static CombatPower.Spec spec(Weapon w, Map<String, Double> s, double lv, double enh) {
        double attack = w.damage() * Combat.powerMultiplier(s, w, enh);
        double vit = Stats.get(s, "vit");
        double red = w.type().equals("shield") ? Math.min(SHIELD.num("passiveCap"), w.passive() + vit * SHIELD.num("passivePerVit")) : 0;
        return new CombatPower.Spec(attack, w.critChance(), w.critMult(), 0, 0, Hp.maxHpOf(lv, vit), red, 0, 0);
    }

    private static CombatPower.Spec with(CombatPower.Spec s, int field, double v) {
        double[] a = {s.attack(), s.critChance(), s.critMult(), s.rune(), s.cdr(), s.maxHp(), s.reduction(), s.armor(), s.speed()};
        a[field] += v;
        return new CombatPower.Spec(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8]);
    }

    public static void main(String[] argv) {
        // 오름: 값 하나씩
        CombatPower.Spec base = new CombatPower.Spec(80, 0.2, 1.6, 5, 5, 300, 0.1, 10, 5);
        long cp0 = CombatPower.of(base);
        String[] names = {"attack", "critChance", "critMult", "rune", "cdr", "maxHp", "reduction", "armor", "speed"};
        double[] steps = {5, 0.05, 0.1, 5, 5, 20, 0.05, 2, 5};
        for (int i = 0; i < names.length; i++) {
            long cp = CombatPower.of(with(base, i, steps[i]));
            check(cp > cp0, "rises with " + names[i] + " (" + cp0 + " → " + cp + ")");
        }
        // 공격 · 생존 둘 다 두 배 → 두 배 (공격력 · 최대 체력 두 배)
        CombatPower.Spec twice = new CombatPower.Spec(160, 0.2, 1.6, 5, 5, 600, 0.1, 10, 5);
        check(Math.abs(CombatPower.of(twice) - 2.0 * cp0) <= 2, "both doubled = cp doubled (" + cp0 + " → " + CombatPower.of(twice) + ")");
        // 무기 없음 · 감소 끝
        CombatPower.Spec bare = new CombatPower.Spec(0, 0, 1.5, 0, 0, 30, 0, 0, 0);
        check(CombatPower.of(bare) > 0, "no weapon still > 0 (" + CombatPower.of(bare) + ")");
        CombatPower.Spec wall = new CombatPower.Spec(10, 0, 1.5, 0, 0, 30, 1, 0, 0);
        check(Double.isFinite(CombatPower.survival(wall)) && CombatPower.survival(wall) <= 30 / (1 - CombatPower.REDUCTION_MAX) + 1e-9, "reduction capped");
        check(Math.abs(CombatPower.combineReduction(0.4, 0.4) - 0.64) < 1e-9, "combine reduction 1-(0.6*0.6)");

        // 무기 11종의 대표 투자 (60렙 · 스탯 180 · 40렙 초월)
        Map<String, Map<String, Double>> builds = new LinkedHashMap<>();
        builds.put("sword", stats(180, 0, 0, 0));
        builds.put("dagger", stats(0, 180, 0, 0));
        builds.put("bow", stats(0, 180, 0, 0));
        builds.put("wand", stats(0, 0, 180, 0));
        builds.put("axe", stats(120, 0, 0, 60));
        builds.put("greatsword", stats(60, 0, 120, 0));
        builds.put("shield", stats(0, 0, 0, 180));
        builds.put("lance", stats(120, 60, 0, 0));
        builds.put("scythe", stats(0, 120, 60, 0));
        builds.put("hammer", stats(60, 0, 0, 120));
        builds.put("scepter", stats(0, 0, 120, 60));
        long lo = Long.MAX_VALUE, hi = 0;
        StringBuilder table = new StringBuilder();
        for (Map.Entry<String, Map<String, Double>> e : builds.entrySet()) {
            Weapon w = Weapons.weaponFrom(e.getKey(), 40, "transcend");
            check(w != null, "weapon " + e.getKey());
            if (w == null) continue;
            CombatPower.Spec s = spec(w, e.getValue(), 60, 0);
            long cp = CombatPower.of(s);
            lo = Math.min(lo, cp);
            hi = Math.max(hi, cp);
            table.append(String.format(java.util.Locale.ROOT, "  %-10s 공격 %7.1f  생존 %6.1f  전투력 %,d%n", e.getKey(), CombatPower.offense(s), CombatPower.survival(s), cp));
        }
        System.out.print(table);
        check(hi <= lo * 1.6, "builds within 1.6x (" + lo + " ~ " + hi + ")");

        // 크기
        Weapon first = Weapons.weaponFrom("sword", 0, "common");
        long early = CombatPower.of(spec(first, stats(15, 0, 0, 0), 5, 0));
        check(early >= 300 && early <= 3000, "early cp " + early);
        Weapon top = Weapons.weaponFrom("sword", 40, "transcend");
        CombatPower.Spec t = spec(top, stats(240, 0, 0, 60), 100, 10);
        Map<String, Double> caps = StatTree.caps();
        CombatPower.Spec late = new CombatPower.Spec(t.attack() * 1.3, Combat.critChanceOf(top, caps.getOrDefault("crit", 50.0) / 100), t.critMult() + 0.5, 20,
                caps.getOrDefault("cdr", 40.0), t.maxHp() + 100, caps.getOrDefault("def", 40.0) / 100, 20, caps.getOrDefault("speed", 40.0));
        long end = CombatPower.of(late);
        check(end >= 30000 && end <= 300000, "late cp " + end);
        System.out.println("  처음 " + early + " · 끝 " + end);

        System.out.println("CombatPowerTest: " + ok + " ok, " + BAD.size() + " bad");
        for (String b : BAD) System.out.println("  BAD " + b);
        if (!BAD.isEmpty()) System.exit(1);
    }
}
