package mmorpg.fabric.skill;

/** 무기 스킬 목록 (skills.js SKILL_FN): 초월 무기마다 두 스킬 · 영웅 무기마다 한 스킬 (10단계 — HeroKit) 을 Transcend 에 등록 (12단계 새 무기 4종 포함) */
final class Skills {
    private Skills() {}

    static void registerAll() {
        Gram.register();
        Solas.register();
        Pangu.register();
        Aegis.register();
        Artemis.register();
        Odin.register();
        Tyrfing.register();
        // 12단계 새 무기 초월 스킬 (창 · 낫 · 전투 망치 · 성스러운 홀)
        Brionac.register();
        Kronos.register();
        Mjolnir.register();
        Apollo.register();
        HeroKit.registerAll();
    }
}
