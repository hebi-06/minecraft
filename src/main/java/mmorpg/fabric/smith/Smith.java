package mmorpg.fabric.smith;

import net.minecraft.server.level.ServerPlayer;

/** 대장장이 (smith.js · enhance.js): 영웅 무기 레벨 올리기 ↔ 무기 강화 ↔ 보스 무기 제작 — 4단계 (채우는 중) */
public final class Smith {
    private Smith() {}

    public static void init() {}

    /** 대장장이 창 (흐름 안에서). start: "smith" · "enhance" · "craft", 끝나면 done (null 이면 없음) */
    public static void smithLoop(ServerPlayer sp, String start, Runnable done) {
        if (done != null) done.run();
    }
}
