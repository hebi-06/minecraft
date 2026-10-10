package mmorpg.fabric.rune;

import net.minecraft.server.level.ServerPlayer;

/** 룬술사 (runesmith.js): 룬 갈기 → 룬 조각 → 뽑기권 — 4단계 (채우는 중) */
public final class RuneSmith {
    private RuneSmith() {}

    public static void init() {}

    /** 룬술사 창 (흐름 안에서). start: "grind" · "tickets", 끝나면 done (null 이면 없음) */
    public static void runesmithLoop(ServerPlayer sp, String start, Runnable done) {
        if (done != null) done.run();
    }
}
