package mmorpg.fabric.quest;

import java.util.List;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.npc.Npcs;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 퀘스트 (quest.js · mainquest.js · sidequest.js · loot.js 일부 · npc.js /mainnpc) 연결 — main.js 의 퀘스트 관련 연결과 같음:
 * NPC 훅 (이름표 [!] · 머리 위 표시 · 대화 · 메인 NPC 번호), 처치 기록 (죽음 → onEntityDied, 잡은 사람은 Loot.killCredit — 피해 뒤 noteHit),
 * 무기 시험 (RpgCombat 무기 맞힘 훅 → onWeaponHit), 2초마다 questTick (애드온: 20틱마다 · currentTick % 40 &lt; 20 일 때),
 * 접속 (진행 읽기 — 예전 진행 옮기기) · 나감 (캐시 지움), 명령어 /quest · /mainnpc · /questreset.
 * 보스 처치 (5단계 보스) 는 onBossKilled 를 부름.
 */
public final class QuestSystem {
    private QuestSystem() {}

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(Quests::setServer);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            Quests.clear();
            Loot.clear();
        });
        // NPC 쪽 훅 (npc.Npcs 가 부름)
        Npcs.badgeCount = Quests::badgeCount;
        Npcs.hasQuestFor = Quests::hasQuestFor;
        Npcs.questMark = Quests::questMark;
        Npcs.talkTo = QuestTalk::talkTo;
        Npcs.isMainKey = k -> MainQuests.mainNpcByKey(k) != null;
        // 처치 기록: 플레이어가 때린 기록 (불에 타 죽어도 10초 안에 마지막으로 때린 사람이 잡은 것으로) · 죽음
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (!(entity instanceof Player) && source.getEntity() instanceof ServerPlayer sp) Loot.noteHit(entity, sp);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            try {
                Quests.onEntityDied(entity, source);
            } catch (RuntimeException e) {
                Mmorpg.LOG.warn("[mmorpg] quest kill", e);
            }
        });
        // 무기 시험 (무기 종류마다 한 번씩 맞히기)
        RpgCombat.addWeaponHitHook((attacker, victim, w, bonus) -> Quests.onWeaponHit(attacker, victim, w));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (Tasks.now() % 40 != 0) return;
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                try {
                    Quests.questTick(sp);
                } catch (RuntimeException e) {
                    Mmorpg.LOG.warn("[mmorpg] quest tick", e);
                }
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Quests.stateOf(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Quests.forgetQuestPlayer(handler.player.getUUID());
            Loot.forgetLoot(handler.player.getUUID());
        });
        QuestCommands.init();
    }

    /** 보스를 쓰러뜨림 (5단계 보스가 부름: 보상을 받은 사람 모두) → 보스 처치 퀘스트 · 전설의 시련 (Quests.onBossKilled) */
    public static void onBossKilled(String key, List<ServerPlayer> players) {
        Quests.onBossKilled(key, players);
    }
}
