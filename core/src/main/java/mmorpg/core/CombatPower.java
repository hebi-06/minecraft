package mmorpg.core;

/**
 * 2026-11-08 총 전투력 (사용자 "체력이나 공격력, 치명타 확률, 치명타 피해 등과 같은걸 종합적으로 합쳐서 총전투력을 만들어줘 해당 전투력은 그냥
 * 보여주기용임 랭킹에 뜰거임"). 보여주기 · 순위용 숫자 — 피해 · 판정에는 쓰지 않음.
 * <pre>
 * 공격   = 공격력 × (1 + 치명타 확률 × (치명타 배율 − 1)) × (1 + (룬 피해 % + 재사용 대기 감소 %) / 200)      (1 보다 작으면 1)
 * 생존   = 최대 체력 ÷ (1 − 받는 피해 감소) × (1 + 방어 / 50)
 * 전투력 = 100 × √(공격 × 생존) × (1 + 이동 속도 % / 400)
 * </pre>
 * 곱의 제곱근인 까닭: 둘이 싸우면 이기는 쪽은 "버티는 시간 (생존 ÷ 상대 공격)" 이 긴 쪽 = 공격 × 생존 이 큰 쪽. 그래서 같은 만큼 투자한 공격형 ·
 * 탱커형 · 균형형이 비슷한 값이 되고 (CombatPowerTest), 공격 · 생존이 둘 다 두 배면 전투력도 두 배.
 * 공격력 = (무기 공격력 (강화 포함) + 스탯이 더한 공격력) × (1 + 스탯 트리 · 아티팩트 공격력 %). 방어 = 입은 갑옷의 방어 점수 (바닐라 갑옷 값).
 */
public final class CombatPower {
    private CombatPower() {}

    /** 전투력 앞의 배수 (숫자 크기 — 처음 1천대 · 끝 8만대) */
    public static final double SCALE = 100;
    /** 받는 피해 감소의 위 (넘으면 나눗셈이 커지므로 — 스탯 트리 40% · 방패 40% 를 곱해도 64%) */
    static final double REDUCTION_MAX = 0.9;

    /**
     * 전투력을 셀 값.
     *
     * @param attack     공격력 (무기 + 스탯 + 스탯 트리 공격력 % 까지)
     * @param critChance 치명타 확률 (0 ~ 1)
     * @param critMult   치명타 배율 (×1.5 = 1.5)
     * @param rune       룬 피해 % (스탯 트리 · 아티팩트)
     * @param cdr        재사용 대기 감소 %
     * @param maxHp      최대 체력
     * @param reduction  받는 피해 감소 (0 ~ 1 — 스탯 트리 · 방패를 합친 것)
     * @param armor      방어 (갑옷 점수)
     * @param speed      이동 속도 %
     */
    public record Spec(double attack, double critChance, double critMult, double rune, double cdr,
                       double maxHp, double reduction, double armor, double speed) {}

    /** 치명타 기대 배율 (1 + 확률 × (배율 − 1)) */
    public static double critFactor(Spec s) {
        double c = clamp(s.critChance(), 0, 1);
        return 1 + c * Math.max(0, s.critMult() - 1);
    }

    /** 공격 (1 보다 작으면 1 — 무기가 없어도 0 이 되지 않게) */
    public static double offense(Spec s) {
        double v = Math.max(0, s.attack()) * critFactor(s) * (1 + (Math.max(0, s.rune()) + Math.max(0, s.cdr())) / 200);
        return Math.max(1, v);
    }

    /** 생존 (최대 체력 ÷ (1 − 감소) × 방어) */
    public static double survival(Spec s) {
        double r = clamp(s.reduction(), 0, REDUCTION_MAX);
        return Math.max(1, s.maxHp()) / (1 - r) * (1 + Math.max(0, s.armor()) / 50);
    }

    /** 전투력 (정수 — 반올림) */
    public static long of(Spec s) {
        return Math.round(SCALE * Math.sqrt(offense(s) * survival(s)) * (1 + Math.max(0, s.speed()) / 400));
    }

    /** 받는 피해 감소 둘을 합침 (1 − (1 − a)(1 − b)) */
    public static double combineReduction(double a, double b) {
        return 1 - (1 - clamp(a, 0, 1)) * (1 - clamp(b, 0, 1));
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }
}
