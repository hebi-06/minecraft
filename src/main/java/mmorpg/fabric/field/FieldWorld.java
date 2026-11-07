package mmorpg.fabric.field;

import mmorpg.core.field.FieldPlan;
import mmorpg.core.field.FieldTerrain;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Props;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;

/**
 * 섬 맵 (2026-11-07 — 사용자 "필드 전체" → "섬형으로 할래 … 섬하나하나를 크게"): 지형 생성기 · 바이옴 고르기를 등록하고,
 * 섬 맵으로 만든 세계를 처음 열 때 세계 경계 (field.json border) · 시작 자리 (마을 광장) 를 정함.
 * 새 세계 만들기의 "월드 유형 → 섬 맵 (MMO RPG)" 또는 서버 server.properties 의 level-type=mmorpg:field.
 */
public final class FieldWorld {
    private FieldWorld() {}

    /** 세계 속성 (Props) — 처음 준비를 마친 판 (판이 오르면 다시 준비) */
    static final String SETUP_KEY = "rpg:field_setup";
    static final String SETUP_VERSION = "1";

    private static volatile FieldTerrain terrain;

    public static void init() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Mmorpg.id("field"), FieldChunkGenerator.CODEC);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, Mmorpg.id("field"), FieldBiomeSource.CODEC);
        ServerLifecycleEvents.SERVER_STARTED.register(FieldWorld::onStarted);
        FieldPieces.init();
        FieldWaystones.init();
    }

    /** 지형 (field.json 한 벌 — 처음 쓸 때 만듦) */
    public static FieldTerrain terrain() {
        FieldTerrain t = terrain;
        if (t == null) {
            synchronized (FieldWorld.class) {
                t = terrain;
                if (t == null) terrain = t = new FieldTerrain(FieldPlan.get());
            }
        }
        return t;
    }

    public static boolean isField(ServerLevel level) {
        return level != null && level.getChunkSource().getGenerator() instanceof FieldChunkGenerator;
    }

    static int minBlockX(ChunkPos p) {
        return p.getMinBlockX();
    }

    static int minBlockZ(ChunkPos p) {
        return p.getMinBlockZ();
    }

    /** 세계 속성 — 마지막으로 맞춘 field.json 의 세계 경계 ("x z 크기") — 섬을 더하며 경계를 넓히면 다음에 열 때 맞춤 */
    static final String BORDER_KEY = "rpg:field_border";

    private static void onStarted(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (!isField(level)) return;
        if (!SETUP_VERSION.equals(Props.world(server, SETUP_KEY))) spawn(server, level);
        border(server, level);
        FieldSetup.run(server, level);
    }

    /** 시작 자리 (마을 광장 남쪽) — 처음 한 번 */
    private static void spawn(MinecraftServer server, ServerLevel level) {
        FieldPlan plan = terrain().plan();
        BlockPos spawn = new BlockPos(plan.spawnX, terrain().surfaceAt(plan.spawnX, plan.spawnZ) + 1, plan.spawnZ);
        level.setRespawnData(LevelData.RespawnData.of(Level.OVERWORLD, spawn, 180F, 0F));
        Props.setWorld(server, SETUP_KEY, SETUP_VERSION);
        Mmorpg.LOG.info("[field] island map ready: spawn {}", spawn.toShortString());
    }

    /** 세계 경계 (field.json border) — 처음, 그리고 field.json 의 경계가 바뀌었을 때만 (관리자가 /worldborder 로 바꾼 것은 그대로 둠) */
    private static void border(MinecraftServer server, ServerLevel level) {
        FieldPlan plan = terrain().plan();
        String sig = plan.borderX + " " + plan.borderZ + " " + plan.borderSize;
        if (sig.equals(Props.world(server, BORDER_KEY))) return;
        level.getWorldBorder().setCenter(plan.borderX, plan.borderZ);
        level.getWorldBorder().setSize(plan.borderSize);
        Props.setWorld(server, BORDER_KEY, sig);
        Mmorpg.LOG.info("[field] world border {} at {} {}", plan.borderSize, plan.borderX, plan.borderZ);
    }
}
