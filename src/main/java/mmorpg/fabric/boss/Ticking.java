package mmorpg.fabric.boss;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * 붙잡아 둘 땅 (ticking.js — 베드락 틱킹 영역): 큰 보스 필드 (수르트 반지름 44) 를 짓는 동안 · 싸우는 동안, 세계수가 자라는 동안
 * 사람이 한쪽 끝에 있어도 반대쪽 청크가 멈추거나 내려가지 않게.
 * 자바: 네모에 걸친 청크마다 표 (ticket — 바닐라 포털 표, 300틱이 지나면 저절로 풀림) 를 100틱마다 다시 붙임 → 서버가 꺼지거나 놓으면 저절로 풀려서
 * 애드온의 "남은 틱킹 영역 치우기 (cleanupAreas)" 가 필요 없음. 청크 100개까지 (애드온과 같은 제한).
 */
public final class Ticking {
    private Ticking() {}

    /** 한 영역에 붙잡을 수 있는 청크 수 (베드락 틱킹 영역 제한과 같음) */
    public static final int MAX_CHUNKS = 100;
    /** 표 반경 (2 = 엔티티까지 돌아감) */
    private static final int RADIUS = 2;

    private record Area(ServerLevel level, int cx0, int cz0, int cx1, int cz1) {}

    private static final Map<String, Area> HELD = new LinkedHashMap<>();
    private static int seq;

    /** Mmorpg.onInitialize */
    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (HELD.isEmpty() || server.getTickCount() % 100 != 0) return;
            for (Area a : HELD.values()) touch(a, true);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> HELD.clear());
    }

    /**
     * 가운데 ± half 칸 네모를 붙잡음 → 이름 (청크가 너무 많으면 null — 짓기 · 싸움은 지금 불러온 것만으로 그대로)
     * tag = 종류 (b = 짓기, f = 싸움, t = 세계수 — 이름에만 씀)
     */
    public static String holdArea(ServerLevel level, Vec3 center, double half, String tag) {
        int cx0 = Math.floorDiv((int) Math.floor(center.x - half), 16);
        int cx1 = Math.floorDiv((int) Math.floor(center.x + half), 16);
        int cz0 = Math.floorDiv((int) Math.floor(center.z - half), 16);
        int cz1 = Math.floorDiv((int) Math.floor(center.z + half), 16);
        if ((long) (cx1 - cx0 + 1) * (cz1 - cz0 + 1) > MAX_CHUNKS) return null;
        String name = "rpg_ba_" + (tag == null ? "x" : tag.replaceAll("[^a-z0-9]", "")) + Long.toString(System.currentTimeMillis(), 36) + Integer.toString(++seq, 36);
        Area a = new Area(level, cx0, cz0, cx1, cz1);
        HELD.put(name, a);
        touch(a, true);
        return name;
    }

    /** 놓음 (없으면 그대로) */
    public static void releaseArea(String name) {
        if (name == null) return;
        Area a = HELD.remove(name);
        if (a != null) touch(a, false);
    }

    /** 네모에 걸친 청크가 모두 불러와졌는지 */
    public static boolean areaLoaded(ServerLevel level, Vec3 center, double half) {
        int cx0 = Math.floorDiv((int) Math.floor(center.x - half), 16);
        int cx1 = Math.floorDiv((int) Math.floor(center.x + half), 16);
        int cz0 = Math.floorDiv((int) Math.floor(center.z - half), 16);
        int cz1 = Math.floorDiv((int) Math.floor(center.z + half), 16);
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                if (!level.getChunkSource().hasChunk(cx, cz)) return false;
            }
        }
        return true;
    }

    /** 붙잡은 영역 수 (시험용) */
    public static int held() {
        return HELD.size();
    }

    private static void touch(Area a, boolean on) {
        for (int cx = a.cx0(); cx <= a.cx1(); cx++) {
            for (int cz = a.cz0(); cz <= a.cz1(); cz++) {
                ChunkPos p = new ChunkPos(cx, cz);
                if (on) a.level().getChunkSource().addTicketWithRadius(TicketType.PORTAL, p, RADIUS);
                else a.level().getChunkSource().removeTicketWithRadius(TicketType.PORTAL, p, RADIUS);
            }
        }
    }

    /** 세계를 내릴 때 남은 것 정리 (서버가 꺼지면 표도 사라짐) */
    static void clear() {
        for (Iterator<Area> it = HELD.values().iterator(); it.hasNext();) {
            touch(it.next(), false);
            it.remove();
        }
    }
}
