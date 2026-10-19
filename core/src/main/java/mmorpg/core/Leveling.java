package mmorpg.core;

/**
 * 레벨업 속도 (9단계 — 자바 판에만, rules_more.json LEVELING): 플레이어 레벨 = 바닐라 경험치 레벨.
 * <ul>
 * <li>한 레벨에 필요한 경험치 = 바닐라 (0~14: 2L+7 · 15~29: 5L-38 · 30~: 9L-158) × needMult (3 — 사용자 요청 "약 3배") × ramp (0.24.1)
 *   — 몬스터 · 보스 · 반복 의뢰의 경험치 (포인트) 는 그대로라 레벨업에 그만큼 더 걸림</li>
 * <li>ramp (0.24.1 — 사용자 "갈수록 레벨업에 필요한 경험치량 증강" → "40렙에서 3배"): rampFrom (10) 레벨까지 1, 그 뒤 한 레벨마다 같은 만큼 커져
 *   rampTo (40) 에서 rampMult (3), 그 뒤로도 같은 기울기. 바닐라 × 3 만으로는 몬스터 경험치도 레벨에 맞춰 올라 한 레벨에 드는 처치 수가
 *   1 ~ 39렙 모두 약 11 ~ 15마리로 거의 같았음 → 10렙 뒤로 점점 늘어 39렙 약 44마리 (그 레벨의 가장 좋은 몬스터 + 반복 의뢰)</li>
 * <li>퀘스트의 "레벨" 보상 (메인 이야기 · 직접 만든 퀘스트): 받는 사람의 지금 레벨부터 그 레벨 수만큼의 바닐라 경험치 (포인트) 로 바꿔 줌
 *   — 필요 경험치가 needMult × ramp 배라 실제로 오르는 레벨도 1/(needMult × ramp)</li>
 * </ul>
 * 마인크래프트 클래스를 쓰지 않음 (자바 모드의 PlayerMixin · QuestRewards 가 부름).
 */
public final class Leveling {
    private Leveling() {}

    private static Obj leveling() {
        return Rules.get().root().objOrNull("LEVELING");
    }

    /** 필요 경험치 배율 (LEVELING.needMult, 없으면 1 — 대조 시험의 순수 규칙) */
    public static double needMult() {
        Obj l = leveling();
        return l == null ? 1 : Math.max(0.01, l.numOr("needMult", 1));
    }

    /**
     * 레벨이 오를수록 더 붙는 배율 (0.24.1, LEVELING.rampFrom · rampTo · rampMult — 없으면 1):
     * level ≤ rampFrom 이면 1, 그 뒤 1 + (rampMult - 1) × (level - rampFrom) ÷ (rampTo - rampFrom) (rampTo 를 넘어도 같은 기울기)
     */
    public static double ramp(int level) {
        Obj l = leveling();
        if (l == null) return 1;
        double from = l.numOr("rampFrom", 0);
        double to = l.numOr("rampTo", 0);
        double k = l.numOr("rampMult", 1);
        if (!(to > from) || level <= from) return 1;
        return Math.max(0.01, 1 + (k - 1) * (level - from) / (to - from));
    }

    /** 최고 레벨 (0.24.2 — LEVELING.maxLevel, 사용자 "최고 레벨 60렙으로 고정 후 그 이후부터는 경험치가 안 올라가게"): 없으면 끝없음 */
    public static int maxLevel() {
        Obj l = leveling();
        double m = l == null ? 0 : l.numOr("maxLevel", 0);
        return m >= 1 ? (int) m : Integer.MAX_VALUE;
    }

    /** 최고 레벨에 이르렀는지 (경험치를 더 받지 않음) */
    public static boolean atMax(int level) {
        return level >= maxLevel();
    }

    /** 바닐라의 다음 레벨까지 경험치 (Player.getXpNeededForNextLevel 원래 식) */
    public static int baseNeed(int level) {
        int l = Math.max(0, level);
        if (l >= 30) return 112 + (l - 30) * 9;
        if (l >= 15) return 37 + (l - 15) * 5;
        return 7 + l * 2;
    }

    /** 이 모드의 다음 레벨까지 경험치 (바닐라 × needMult × ramp, 반올림 · 최소 1) */
    public static int need(int level) {
        double v = baseNeed(level) * needMult() * ramp(level);
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, Math.round(v)));
    }

    /** 레벨 0 에서 level 까지 모은 경험치 (이 모드의 곡선) */
    public static long total(int level) {
        long s = 0;
        for (int l = 0; l < level; l++) s += need(l);
        return s;
    }

    /** 퀘스트 레벨 보상 → 경험치: 지금 레벨부터 levels 레벨만큼의 바닐라 경험치 합 (0 이하면 0) */
    public static int pointsForLevels(int level, int levels) {
        long s = 0;
        for (int i = 0; i < levels; i++) s += baseNeed(level + i);
        return (int) Math.min(Integer.MAX_VALUE, s);
    }
}
