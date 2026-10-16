package mmorpg.fabric.rune;

/** 계열 17개의 룬 효과 등록 (RUNE_FN — 계열마다 한 파일, 0.12.6 사신 계열은 자바 전용) */
final class RuneFamilies {
    private RuneFamilies() {}

    static void registerAll() {
        RuneStr.register();
        RuneDex.register();
        RuneVit.register();
        RuneInt.register();
        RuneAxe.register();
        RuneBow.register();
        RuneHoly.register();
        RuneStorm.register();
        RuneAssassin.register();
        RuneBlade.register();
        RuneSpellblade.register();
        RuneElement.register();
        RuneSpirit.register();
        RuneSpiritKing.register(); // 12단계: 정령사 초월 룬 정령왕 강림
        RuneGravity.register();
        RuneReaper.register(); // 0.12.6: 사신 (낫 전용 — 피의 초승달)
        RuneBody.register();
        RuneStrike.register();
    }

    /** 등록한 룬 효과 수 (함수 이름 — 정령사는 다섯 등급이 하나) */
    static int count() {
        return RuneCast.count();
    }
}
