package mmorpg.fabric.skill;

/** 무기 스킬 목록 (skills.js SKILL_FN): 초월 무기마다 두 스킬 · 영웅 무기마다 한 스킬 (10단계 — HeroKit) 을 Transcend 에 등록 */
final class Skills {
    private Skills() {}

    static void registerAll() {
        Gram.register();
        Solas.register();
        Gungnir.register();
        Aegis.register();
        Artemis.register();
        Odin.register();
        Tyrfing.register();
        HeroKit.registerAll();
    }
}
