package mmorpg.fabric.skill;

import java.util.UUID;
import mmorpg.fabric.Tasks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * 직업 스킬 · 룬 · 보스가 함께 쓰는 스킬 도구의 시작 · 나감 (Kit — 대상 · 몸 · 피해 · 낙하 막기, Guards — 보호 버프, Status — 침묵 · 묶임, Taunts — 도발).
 * 2026-11-16 무기 스킬 (Transcend — 초월 · 영웅 무기의 우클릭 스킬) 을 지우며 옮김 (사용자 "무기 스킬도 빼고 대신 무기 디자인을 늘리건데")
 */
public final class SkillCore {
    private SkillCore() {}

    public static void init() {
        Kit.init();
        Guards.init();
        Status.init();
        Taunts.init();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (Tasks.now() % 100 == 0) Kit.prune();
        });
    }

    /** 나감: 이 사람의 기록을 모두 지움 */
    public static void forget(UUID id) {
        Kit.forget(id);
        Guards.clear(id);
        Status.forget(id);
        Taunts.forget(id);
    }
}
