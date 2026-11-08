package mmorpg.fabric.vanilla;

import mmorpg.fabric.Mmorpg;
import net.minecraft.resources.Identifier;

/**
 * 2026-11-07 로드맵 1번 — 모드 없이 리소스 팩만으로 접속 (claude/roadmap.md · 사용자 "모델링 모드 없이 그냥 리소스만 다운시켜서 굴릴는법 찾아야 함",
 * 결정 "모드 없으면 모션만 없이 대역 안세움").
 *
 * <p>Polymer (서버 쪽 콘텐츠 라이브러리 — 모드 jar 안에 같이) 로 모드 없는 클라이언트에 보내는 것만 바닐라 것으로 바꿈: 아이템 · 블록 · 엔티티 ·
 * 파티클 · 남은 등록 항목 (Fabric 레지스트리 동기화가 끊지 않게) + 리소스 팩. 모드 클라이언트는 지금 그대로 (VanillaClients 가 가름).
 * 단계: 1 바탕 · 2 몬스터 · 보스 모델 (VanillaModels — 뼈마다 아이템 표시 엔티티) · 3 스킬 입력 · 체력 표시 (VanillaInput · VanillaPackets) ·
 * 4 창 (VanillaForms — 상자 메뉴 · 대화창) · 5 이펙트 · 6 나머지.
 */
public final class VanillaSupport {
    private VanillaSupport() {}

    /** Mmorpg.onInitialize 끝 (모든 등록 뒤 · 레지스트리가 얼기 전) */
    public static void init() {
        VanillaClients.init();
        VanillaItems.init();
        VanillaBlocks.init();
        VanillaEntities.init();
        VanillaModels.init();
        VanillaParticles.init();
        VanillaRegistries.init();
        VanillaPack.init();
        VanillaInput.init();
        VanillaForms.init();
        GachaCinematic.init();
        VanillaHud.init();
        VanillaCosmetics.init();
        VanillaProbe.init();
    }

    /** 우리가 등록한 이름 (rpg: · mmorpg:) */
    static boolean ours(Identifier id) {
        return id != null && (id.getNamespace().equals(Mmorpg.NS) || id.getNamespace().equals(Mmorpg.MOD_ID));
    }
}
