package mmorpg.core;

/**
 * 레벨업 속도 (9단계 — 자바 판에만, rules_more.json LEVELING): 플레이어 레벨 = 바닐라 경험치 레벨.
 * <ul>
 * <li>한 레벨에 필요한 경험치 = 바닐라 (0~14: 2L+7 · 15~29: 5L-38 · 30~: 9L-158) × needMult (지금 3 — 사용자 요청 "약 3배")
 *   — 몬스터 · 보스 · 반복 의뢰의 경험치 (포인트) 는 그대로라 레벨업에 그만큼 더 걸림</li>
 * <li>퀘스트의 "레벨" 보상 (메인 이야기 · 직접 만든 퀘스트): 받는 사람의 지금 레벨부터 그 레벨 수만큼의 바닐라 경험치 (포인트) 로 바꿔 줌
 *   — 필요 경험치가 needMult 배라 실제로 오르는 레벨도 1/needMult</li>
 * </ul>
 * 마인크래프트 클래스를 쓰지 않음 (자바 모드의 PlayerMixin · QuestRewards 가 부름).
 */
public final class Leveling {
    private Leveling() {}

    /** 필요 경험치 배율 (LEVELING.needMult, 없으면 1 — 대조 시험의 순수 규칙) */
    public static double needMult() {
        Obj l = Rules.get().root().objOrNull("LEVELING");
        return l == null ? 1 : Math.max(0.01, l.numOr("needMult", 1));
    }

    /** 바닐라의 다음 레벨까지 경험치 (Player.getXpNeededForNextLevel 원래 식) */
    public static int baseNeed(int level) {
        int l = Math.max(0, level);
        if (l >= 30) return 112 + (l - 30) * 9;
        if (l >= 15) return 37 + (l - 15) * 5;
        return 7 + l * 2;
    }

    /** 이 모드의 다음 레벨까지 경험치 (바닐라 × needMult, 반올림 · 최소 1) */
    public static int need(int level) {
        return (int) Math.max(1, Math.round(baseNeed(level) * needMult()));
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
