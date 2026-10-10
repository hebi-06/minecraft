package mmorpg.fabric;

import mmorpg.core.Rules;
import mmorpg.fabric.skill.Transcend;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MMO RPG (자바 에디션 · Fabric). 베드락 애드온 "MMO RPG 시스템" 의 이식 — 1단계: 스탯 · 레벨 포인트 · RPG 체력 ·
 * 무기 (요구 스탯 · 피해 공식 · 활 · 완드) · 피해 숫자, 2단계: 무기 3D 모델 · 이펙트 그림 (활 · 완드 · 참격),
 * 3단계: 초월 무기 스킬 (mmorpg.fabric.skill).
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
        RpgParticles.init();
        RpgItems.init();
        RpgNet.init();
        Tasks.init();
        Notify.init();
        RpgHealth.init();
        RpgCombat.init();
        DamageNumbers.init();
        Transcend.init();
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
