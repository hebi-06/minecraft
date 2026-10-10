package mmorpg.fabric;

import mmorpg.core.Rules;
import mmorpg.fabric.rune.RuneSystem;
import mmorpg.fabric.skill.Transcend;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MMO RPG (자바 에디션 · Fabric). 베드락 애드온 "MMO RPG 시스템" 의 이식 — 1단계: 스탯 · 레벨 포인트 · RPG 체력 ·
 * 무기 (요구 스탯 · 피해 공식 · 활 · 완드) · 피해 숫자, 2단계: 무기 3D 모델 · 이펙트 그림 (활 · 완드 · 참격),
 * 3단계: 초월 무기 스킬 (mmorpg.fabric.skill) · 룬 80종 (mmorpg.fabric.rune), 4단계: 갑옷 · 재료 (mmorpg.fabric.gear) …
 * 밸런스 수치와 공식은 규칙 코어 (mmorpg.core, rules.json) 를 그대로 씀.
 */
public final class Mmorpg implements ModInitializer {
    /** 모드 id (fabric.mod.json) — 플레이어 데이터 · 패킷 · 화면 이름 */
    public static final String MOD_ID = "mmorpg";
    /** 아이템 · 그림 이름공간: 애드온과 같은 rpg (rpg:sword_l10_rare) */
    public static final String NS = "rpg";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        Rules.get(); // rules.json 을 처음에 읽음 (잘못되었으면 여기서 바로 실패)
        RpgData.init();
        Props.init(); // 베드락 동적 속성 (엔티티 · 플레이어 · 세계)
        RpgParticles.init();
        mmorpg.fabric.gear.WeaponEnhance.init(); // 무기 강화 단계 (아이템 구성 요소 — 아이템보다 먼저)
        RpgItems.init();
        RpgNet.init();
        mmorpg.fabric.ui.Forms.init(); // 서버 창 (상자 창 · 대화창 · 버튼 목록 · 입력창)
        Tasks.init();
        Notify.init();
        RpgStats.init();
        RpgHealth.init();
        RpgCombat.init();
        DamageNumbers.init();
        Transcend.init();
        RuneSystem.init();
        mmorpg.fabric.gear.ArmorSystem.init();
        mmorpg.fabric.shop.ShopCommands.init();
        mmorpg.fabric.npc.Npcs.init();
        mmorpg.fabric.npc.NpcEditor.init(); // NPC · 퀘스트 설정 창 (Npcs 훅 · /npc)
        mmorpg.fabric.gear.NpcToolItem.onUse = mmorpg.fabric.npc.Npcs::onToolUse;
        mmorpg.fabric.npc.Dummy.init();
        mmorpg.fabric.mob.RpgEntities.init(); // 몬스터 6 · 보스 10 · 소품 10 (애드온 엔티티 그대로)
        mmorpg.fabric.boss.Arena.init(); // 보스 제단 목록 · 필드
        mmorpg.fabric.boss.Ticking.init(); // 붙잡아 둘 땅 (큰 필드 · 세계수)
        mmorpg.fabric.boss.ArenaGuard.init(); // 제단 블록 9종 · 보스 필드 짓기 · 필드 보호
        mmorpg.fabric.boss.Bosses.init(); // 보스 엔진 (제단 누르기 · 소환 · 싸움 · 보상) · 보스 모듈 · /boss
        mmorpg.fabric.smith.Smith.init();
        mmorpg.fabric.rune.RuneSmith.init();
        mmorpg.fabric.quest.QuestSystem.init();
        mmorpg.fabric.nav.Nav.init(); // 길 안내 (퀘스트의 다음 갈 곳 — 화면 화살표 · 바닥 빛)
        RpgPlayers.init();
        RpgCommands.init();
        LOG.info("MMO RPG: weapons {}, rules {}", RpgItems.WEAPONS.size(), Rules.RESOURCE);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static Identifier rpg(String path) {
        return Identifier.fromNamespaceAndPath(NS, path);
    }
}
