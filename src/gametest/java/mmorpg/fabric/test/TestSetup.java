package mmorpg.fabric.test;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import mmorpg.fabric.stattree.StatTrees;
import net.fabricmc.api.ModInitializer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 게임 시험 모드의 준비 (main 진입점 — 서버 시험 · 화면 시험 둘 다):
 * 스탯 트리 (7단계) 는 시험 서버에서 기본으로 꺼 둠 — 기존 시험 · 사진첩은 스탯을 찍고 정해진 피해 · 최대 체력 · 치명타 · 가방 칸을 확인하므로
 * (스탯 트리가 켜지면 보너스 · 뽑기권이 더해짐). 스탯 트리 시험 · 사진첩은 {@link #treeOn} 으로 그 플레이어만 켬. 실제 게임은 모두 켜져 있음.
 */
public final class TestSetup implements ModInitializer {
    /** 스탯 트리를 켠 플레이어 */
    static final Set<UUID> TREE_ON = ConcurrentHashMap.newKeySet();

    @Override
    public void onInitialize() {
        StatTrees.active = sp -> TREE_ON.contains(sp.getUUID());
    }

    /** 이 플레이어의 스탯 트리를 켬 (보너스 · 이미 자란 칸의 보상 — 서버 스레드에서) */
    static void treeOn(ServerPlayer sp) {
        TREE_ON.add(sp.getUUID());
        StatTrees.refresh(sp, true);
        mmorpg.fabric.RpgHealth.refreshMax(sp, false);
    }

    static void treeOff(ServerPlayer sp) {
        TREE_ON.remove(sp.getUUID());
        StatTrees.refresh(sp, false);
        mmorpg.fabric.RpgHealth.refreshMax(sp, false);
    }
}
