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
        mmorpg.fabric.rune.Breakthrough.initComponent(); // 룬 돌파 단계 (8단계 — 아이템 구성 요소)
        RpgItems.init();
        RpgNet.init();
        mmorpg.fabric.ui.Forms.init(); // 서버 창 (상자 창 · 대화창 · 버튼 목록 · 입력창)
        mmorpg.fabric.ui.EnderWindow.init(); // 스탯 창 아랫줄 엔더 상자 (어디서나 내 엔더 상자)
        Tasks.init();
        Notify.init();
        RpgStats.init();
        RpgHealth.init();
        RpgCombat.init();
        DamageNumbers.init();
        FxDisplays.init(); // 0.18.0 연출용 표시 엔티티 (대지 가르기의 대검 · 들리는 땅)
        Transcend.init();
        RuneSystem.init();
        mmorpg.fabric.gear.ArmorSystem.init();
        mmorpg.fabric.shop.ShopCommands.init();
        mmorpg.fabric.npc.Npcs.init();
        mmorpg.fabric.npc.NpcEditor.init(); // NPC · 퀘스트 설정 창 (Npcs 훅 · /npc)
        mmorpg.fabric.gear.NpcToolItem.onUse = mmorpg.fabric.npc.Npcs::onToolUse;
        mmorpg.fabric.npc.Dummy.init();
        mmorpg.fabric.mob.RpgEntities.init(); // 몬스터 6 · 보스 10 · 소품 10 · 탈것 (애드온 엔티티 그대로)
        mmorpg.fabric.mount.MountItems.init(); // 탈것 아이템 (mounts.json — 쓰면 내 탈것 목록에)
        mmorpg.fabric.mount.Mounts.init(); // 탈것 부르기 · 고르기 · 탄 동안 막기 · /mount
        mmorpg.fabric.duel.DuelSystem.init(); // 결투 (/pvp · /결투 — 신청 · 수락 · 피해 감소 · 쓰러지지 않음 · 연출)
        mmorpg.fabric.boss.Arena.init(); // 보스 제단 목록 · 필드
        mmorpg.fabric.boss.Ticking.init(); // 붙잡아 둘 땅 (큰 필드 · 세계수)
        mmorpg.fabric.mob.Monsters.init(); // 일반 몬스터 6종 (싸움 · 이름표 · 회복 · 경험치 · 전리품) · 사냥터 · /mob · /mobzone
        mmorpg.fabric.tree.WorldTree.init(); // 세계수 (/worldtree — 심기 · 자라기 · 되돌리기)
        mmorpg.fabric.boss.ArenaGuard.init(); // 제단 블록 9종 · 보스 필드 짓기 · 필드 보호
        mmorpg.fabric.boss.Bosses.init(); // 보스 엔진 (제단 누르기 · 소환 · 싸움 · 보상) · 보스 모듈 · /boss
        mmorpg.fabric.smith.Smith.init();
        mmorpg.fabric.rune.RuneSmith.init();
        mmorpg.fabric.quest.QuestSystem.init();
        mmorpg.fabric.nav.Nav.init(); // 길 안내 (퀘스트의 다음 갈 곳 — 화면 화살표 · 바닥 빛)
        mmorpg.fabric.stattree.StatTrees.init(); // 스탯 트리 (7단계 — 스탯을 찍으면 자람 · 갈래 · 보너스 · 뽑기권 · /스탯트리)
        mmorpg.fabric.cosmetic.Cosmetics.init(); // 장식 (0.13.0 — 무기 · 갑옷 스킨 · 머리 · 등 장식 · 오라 · 칭호, 갑옷 숨기기 · /장식)
        mmorpg.fabric.gacha.GachaSystem.init(); // 룬 뽑기 (0.14.0 — 뽑기 창 · 1회 · 10회 · 원신식 천장 · 관리자 한정 뽑기 · /뽑기)
        mmorpg.fabric.economy.EconomySystem.init(); // 0.21.0 화폐 (골드 · 실버 지갑) · 유저 장터 · 쓰레기통 · 버린 아이템은 가방으로
        mmorpg.fabric.storage.StorageSystem.init(); // 0.22.0 엔더 창고 (큰 상자 54칸 × 상자 5개 — 2번 실버 · 3번부터 골드 · /창고)
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
