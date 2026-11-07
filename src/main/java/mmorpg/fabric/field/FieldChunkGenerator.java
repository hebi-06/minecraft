package mmorpg.fabric.field;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import mmorpg.core.field.FieldPlan;
import mmorpg.core.field.FieldTerrain;
import mmorpg.core.field.FieldTerrain.Column;
import mmorpg.core.field.FieldTerrain.Surface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * 섬 맵 지형 생성기 (2026-11-07 — world_preset mmorpg:field 의 지상 세계): 칸마다 core FieldTerrain (field.json) 이 정한 땅 높이 · 겉흙 · 물 · 용암 ·
 * 떠 있는 바위를 그대로 쌓음. 세계 시드와 상관없이 같은 지도. 동굴 · 바닐라 구조물 없음, 나무 · 풀 · 꽃 · 광석은 섬 바이옴의 장식 (바닐라) 이,
 * 마을 · 거점 · 다리 · 신전은 그 뒤에 {@link FieldPieces} 가 놓음 (청크마다 걸친 부분만).
 */
public final class FieldChunkGenerator extends ChunkGenerator {
    public static final MapCodec<FieldChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)).apply(i, i.stable(FieldChunkGenerator::new)));

    public static final int MIN_Y = -64;
    public static final int HEIGHT = 384;

    public FieldChunkGenerator(BiomeSource biomes) {
        super(biomes);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /** 블록을 받는 곳 (청크 칸 · 기둥 배열) */
    private interface Sink {
        void put(int y, BlockState state);
    }

    /** 칸 하나를 아래부터 쌓음 (기반암 → 몸 → 아래 흙 → 겉흙 → 물 · 용암 → 떠 있는 바위) */
    static void column(Column c, int x, int z, int minY, int maxY, Sink out) {
        String terr = c.island == null || !c.land() ? "" : c.island.terrain();
        int g = Math.min(c.ground, maxY);
        boolean gravity = c.top == Surface.SAND || c.top == Surface.RED_SAND || c.top == Surface.GRAVEL;
        int topDepth = gravity ? Math.max(1, c.depth) : 1;
        int underTop = g - topDepth;
        int underBottom = underTop - (gravity ? 1 : c.depth) + 1;
        boolean bands = c.top == Surface.TERRACOTTA || c.under == Surface.TERRACOTTA;
        out.put(minY, FieldBlocks.BEDROCK);
        for (int y = minY + 1; y <= g; y++) {
            BlockState s;
            if (y > underTop) s = FieldBlocks.of(c.top, x, y, z);
            else if (y >= underBottom) s = FieldBlocks.of(gravity && c.under == Surface.DIRT ? Surface.SANDSTONE : c.under, x, y, z);
            else if (bands && y > 60) s = FieldBlocks.of(Surface.TERRACOTTA, x, y, z); // 메사 벼랑: 바다 높이 위는 테라코타 띠
            else s = FieldBlocks.body(terr, y);
            out.put(y, s);
        }
        if (c.hasWater() && c.water > g) {
            BlockState fluid = c.lava ? FieldBlocks.LAVA : FieldBlocks.WATER;
            for (int y = g + 1; y <= Math.min(c.water, maxY); y++) out.put(y, fluid);
        }
        if (c.floatTop > c.floatBottom) {
            for (int y = Math.max(c.floatBottom, g + 2); y <= Math.min(c.floatTop, maxY); y++) out.put(y, FieldBlocks.END_STONE);
        }
    }

    @Override
    public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState random, StructureManager structures, BiomeManager biomes,
            WorldGenRegion region, Set<Holder<Biome>> touched) {
        FieldTerrain t = FieldWorld.terrain();
        ChunkPos cp = chunk.getPos();
        int bx = FieldWorld.minBlockX(cp);
        int bz = FieldWorld.minBlockZ(cp);
        int minY = chunk.getMinY();
        int maxY = minY + chunk.getHeight() - 1;
        LevelChunkSection[] sections = chunk.getSections();
        Column c = new Column();
        for (LevelChunkSection s : sections) s.acquire();
        try {
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    t.sample(bx + lx, bz + lz, c);
                    final int fx = lx;
                    final int fz = lz;
                    column(c, bx + lx, bz + lz, minY, maxY, (y, state) -> {
                        LevelChunkSection s = sections[chunk.getSectionIndex(y)];
                        s.setBlockState(fx, y & 15, fz, state, false);
                    });
                }
            }
        } finally {
            for (LevelChunkSection s : sections) s.release();
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {
        super.applyBiomeDecoration(level, chunk, structures);
        FieldPieces.place(level, chunk);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // 처음 동물은 두지 않음 (바이옴의 동물은 시간이 지나며 생김 — 섬마다 양 · 닭 · 토끼 · 여우 · 개구리)
    }

    @Override
    public int getGenDepth() {
        return HEIGHT;
    }

    @Override
    public int getSeaLevel() {
        return FieldWorld.terrain().plan().sea;
    }

    @Override
    public int getMinY() {
        return MIN_Y;
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor level) {
        FieldPlan p = FieldWorld.terrain().plan();
        return FieldWorld.terrain().surfaceAt(p.spawnX, p.spawnZ) + 1;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        Column c = FieldWorld.terrain().sample(x, z, new Column());
        int top = c.ground;
        if (c.floatTop > c.floatBottom) top = Math.max(top, c.floatTop);
        boolean fluid = type != Heightmap.Types.OCEAN_FLOOR && type != Heightmap.Types.OCEAN_FLOOR_WG;
        if (fluid && c.hasWater()) top = Math.max(top, c.water);
        return Math.min(top, level.getMinY() + level.getHeight() - 1) + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        return new NoiseColumn(level.getMinY(), columnStates(x, z, level.getMinY(), level.getHeight()));
    }

    /** 칸 (x, z) 의 지형 블록 (아래 minY 부터 height 칸 — 장식 · 짓는 것 전) */
    public static BlockState[] columnStates(int x, int z, int minY, int height) {
        BlockState[] states = new BlockState[height];
        java.util.Arrays.fill(states, FieldBlocks.AIR);
        Column c = FieldWorld.terrain().sample(x, z, new Column());
        column(c, x, z, minY, minY + height - 1, (y, state) -> states[y - minY] = state);
        return states;
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos, SamplerContext context) {
        Column c = FieldWorld.terrain().sample(pos.getX(), pos.getZ(), new Column());
        info.add("Field: " + (c.island == null ? "-" : c.island.id() + (c.land() ? "" : " (sea)")) + " " + c.biome + " ground " + c.ground);
    }
}
