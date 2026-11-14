package mmorpg.core;

import java.util.Map;

/**
 * 2026-11-14 민첩 = 공격 속도 (자바 전용 규칙 rules_more ATTACK_SPEED — 대조 시험의 애드온 규칙에는 없어서 그때는 예전 그대로).
 * 사용자 "민첩 스탯은 공속으로 바꾸고 공속이 빠를수록 평타가 빨리 나가게 하고 싶음 일정 이상 올라가면 공격력으로 전환되고" · "기본 평타가 느려야겠지?"
 * → 고른 것: 모든 무기 같은 규칙 · 기본 0.8초 (MOTION.interval 16틱) · 민첩 100 에서 0.4초.
 *
 * <ul>
 *   <li>공속 = 1 + perPoint × min(민첩, cap) — 근접 평타 사이 = MOTION.interval ÷ 공속 ({@link Motions#attackInterval(double)}),
 *       활 · 완드 재사용 대기 = cooldownTicks × rangedSlow ÷ 공속</li>
 *   <li>무기 공격력의 민첩 계수 (단검 · 활 · 낫 주 스탯 · 창 덧 스탯): 그 무기의 요구 민첩만큼은 늘 들어감 (요구를 맞춘 사람은 예전 세기 그대로) +
 *       max(cap, 요구 민첩) 을 넘은 민첩 — 그 사이의 민첩은 공속으로만</li>
 *   <li>민첩 계수가 없는 무기: cap 을 넘은 민첩 1당 공격력 overPer</li>
 *   <li>직업 스킬 · 룬 · 유물 위력의 적용 스탯 (민첩) 은 그대로 (무기 공격력이 아님)</li>
 * </ul>
 */
public final class AttackSpeed {
    private AttackSpeed() {}

    private static Obj cfg() {
        return Rules.get().root().objOrNull("ATTACK_SPEED");
    }

    /** 규칙이 있는지 (애드온 그대로의 대조 시험에서는 없음) */
    public static boolean enabled() {
        return cfg() != null;
    }

    /** 공속 스탯 이름 (민첩) */
    public static String stat() {
        Obj c = cfg();
        return c == null ? "dex" : c.strOr("stat", "dex");
    }

    /** 1 포인트당 공속 (+1%) */
    public static double perPoint() {
        Obj c = cfg();
        return c == null ? 0 : c.numOr("perPoint", 0.01);
    }

    /** 공속이 오르는 민첩 상한 (100 — 넘으면 공격력) */
    public static double cap() {
        Obj c = cfg();
        return c == null ? 0 : c.numOr("cap", 100);
    }

    /** 민첩 계수가 없는 무기: 상한을 넘은 민첩 1당 공격력 */
    public static double overPer() {
        Obj c = cfg();
        return c == null ? 0 : c.numOr("overPer", 0.01);
    }

    /** 활 · 완드 재사용 대기의 바탕 배율 (근접 평타 사이 10 → 16틱 과 같은 몫) */
    public static double rangedSlow() {
        Obj c = cfg();
        return c == null ? 1 : c.numOr("rangedSlow", 1.6);
    }

    /** 전투력의 공격에 공속을 넣는 몫 (평타가 공격의 일부라 반만) */
    public static double cpWeight() {
        Obj c = cfg();
        return c == null ? 0 : c.numOr("cpWeight", 0.5);
    }

    /** 가장 빠른 공속 배율 (1 + perPoint × cap) */
    public static double maxSpeed() {
        return 1 + perPoint() * cap();
    }

    /** 이 스탯의 공속 배율 (규칙이 없으면 1) */
    public static double speed(Map<String, Double> s) {
        return speedOf(Stats.get(s, stat()));
    }

    /** 민첩 dex 의 공속 배율 */
    public static double speedOf(double dex) {
        if (!enabled()) return 1;
        return 1 + perPoint() * Math.max(0, Math.min(dex, cap()));
    }

    /** 무기의 요구 민첩 (없으면 0) */
    public static double reqOf(Weapon w) {
        if (w == null || w.reqs() == null) return 0;
        for (Weapons.Req r : w.reqs()) if (r.stat().equals(stat())) return r.value();
        return 0;
    }

    /** 공속이 아니라 공격력이 되는 민첩 (max(cap, 요구 민첩) 을 넘은 몫) */
    public static double overflow(double dex, Weapon w) {
        return Math.max(0, dex - Math.max(cap(), reqOf(w)));
    }

    /**
     * 민첩이 무기 공격력 배율 (1 + Σ) 에 더하는 몫: 민첩 계수 per 가 있으면 per × (요구 민첩 + 넘은 몫), 없으면 overPer × (cap 을 넘은 몫).
     * 규칙이 없으면 예전 그대로 (민첩 × per)
     */
    public static double damageTerm(Map<String, Double> s, Weapon w) {
        double dex = Stats.get(s, stat());
        double per = 0;
        for (Weapons.Scaling b : w.scaling()) if (b.stat().equals(stat())) per += b.per();
        if (!enabled()) return dex * per;
        if (per > 0) return per * (reqOf(w) + overflow(dex, w));
        return overPer() * Math.max(0, dex - cap());
    }

    /** 무기 공격력 배율의 스탯 몫 Σ (민첩은 {@link #damageTerm}, 나머지는 스탯 × per) */
    public static double statSum(Map<String, Double> s, Weapon w) {
        double sum = 0;
        for (Weapons.Scaling b : w.scaling()) {
            if (b.stat().equals(stat())) continue; // 민첩은 damageTerm 이 셈
            sum += Stats.get(s, b.stat()) * b.per();
        }
        return sum + damageTerm(s, w);
    }
}
