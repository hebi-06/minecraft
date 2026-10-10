package mmorpg.fabric.skill;

/** 초월 무기 스킬 목록 (skills.js SKILL_FN): 무기마다 두 스킬을 Transcend 에 등록 */
final class Skills {
    private Skills() {}

    static void registerAll() {
        Gram.register();
    }
}
