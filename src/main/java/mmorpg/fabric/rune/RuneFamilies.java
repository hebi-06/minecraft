package mmorpg.fabric.rune;

/** 계열 17개의 룬 효과 등록 (RUNE_FN — 계열마다 한 파일, 0.12.6 사신 계열은 자바 전용) + 0.16.0 한정 초월 룬 셋 (RuneLimited — 한정 뽑기 전용) · 0.17.0 무간참역 (RuneEndless) · 0.18.0 대지 가르기 (RuneTerra) · 0.19.0 세계수의 가호 (RuneGrove — 버프) · 0.20.0 심연의 낙인 (RuneAbyss — 적 약화) · 0.24.0 만년빙하 (RuneGlacier — 얼음 필드) · 0.25.0 두 스탯 계열 여섯 (RuneTide … RuneGladiator — 공용은 PairKit) */
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
        RuneReaper.register(); // 0.12.6: 사신 (피의 초승달 — 0.27.0 부터 낫 조건 없음)
        RuneBody.register();
        RuneStrike.register();
        RuneLimited.register(); // 0.16.0: 한정 초월 룬 (만상귀일 · 칠성 강림 · 천공의 눈)
        RuneEndless.register(); // 0.17.0: 한정 초월 룬 무간참역
        RuneTerra.register(); // 0.18.0: 한정 초월 룬 대지 가르기
        RuneGrove.register(); // 0.19.0: 한정 룬 세계수의 가호 (버프)
        RuneAbyss.register(); // 0.20.0: 한정 룬 심연의 낙인 (적 약화)
        RuneGlacier.register(); // 0.24.0: 한정 초월 룬 만년빙하 (얼음 필드 · 빙결)
        // 0.25.0: 두 스탯 계열 여섯 (마력+체력 해류 · 체력+마력 골렘 · 민첩+힘 무투 · 민첩+체력 사냥꾼 · 힘+마력 화산 · 체력+민첩 투사)
        RuneTide.register();
        RuneGolem.register();
        RuneMonk.register();
        RuneHunter.register();
        RuneVolcano.register();
        RuneGladiator.register();
    }

    /** 등록한 룬 효과 수 (함수 이름 — 정령사는 다섯 등급이 하나) */
    static int count() {
        return RuneCast.count();
    }
}
