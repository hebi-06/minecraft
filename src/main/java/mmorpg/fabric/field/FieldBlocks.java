package mmorpg.fabric.field;

import mmorpg.core.field.FieldNoise;
import mmorpg.core.field.FieldTerrain.Surface;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** 섬 맵 겉흙 → 블록 (2026-11-07). 붉은 메사는 높이마다 테라코타 띠, 공허 섬의 떠 있는 바위는 엔드 돌, 흑요석 땅에는 우는 흑요석이 드문드문. */
public final class FieldBlocks {
    private FieldBlocks() {}

    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    static final BlockState STONE = Blocks.STONE.defaultBlockState();
    static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
    static final BlockState WATER = Blocks.WATER.defaultBlockState();
    static final BlockState LAVA = Blocks.LAVA.defaultBlockState();
    static final BlockState END_STONE = Blocks.END_STONE.defaultBlockState();
    static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();

    private static final BlockState[] BANDS = {
        Blocks.TERRACOTTA.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.YELLOW_TERRACOTTA.defaultBlockState(),
        Blocks.TERRACOTTA.defaultBlockState(), Blocks.RED_TERRACOTTA.defaultBlockState(), Blocks.WHITE_TERRACOTTA.defaultBlockState(),
        Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState(), Blocks.BROWN_TERRACOTTA.defaultBlockState(),
        Blocks.TERRACOTTA.defaultBlockState()
    };

    /** 겉흙 블록 (x · y · z 로 띠 · 반짝임을 고름) */
    public static BlockState of(Surface s, int x, int y, int z) {
        return switch (s) {
            case GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
            case SAND -> Blocks.SAND.defaultBlockState();
            case SANDSTONE -> Blocks.SANDSTONE.defaultBlockState();
            case RED_SAND -> Blocks.RED_SAND.defaultBlockState();
            case TERRACOTTA -> BANDS[Math.floorMod(Math.floorDiv(y, 2), BANDS.length)];
            case GRAVEL -> Blocks.GRAVEL.defaultBlockState();
            case STONE -> STONE;
            case SNOW -> Blocks.SNOW_BLOCK.defaultBlockState();
            case PACKED_ICE -> Blocks.PACKED_ICE.defaultBlockState();
            case BLUE_ICE -> Blocks.BLUE_ICE.defaultBlockState();
            case PODZOL -> Blocks.PODZOL.defaultBlockState();
            case MUD -> Blocks.MUD.defaultBlockState();
            case COARSE -> Blocks.COARSE_DIRT.defaultBlockState();
            case CLAY -> Blocks.CLAY.defaultBlockState();
            case VOID -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
            case VOID_DARK -> Blocks.TUFF.defaultBlockState();
            case SCULK -> Blocks.SCULK.defaultBlockState();
            case OBSIDIAN -> FieldNoise.hash(x, z, y) < 0.18 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState();
            case SOUL -> Blocks.SOUL_SOIL.defaultBlockState();
            case BONE -> Blocks.BONE_BLOCK.defaultBlockState();
            case BASALT -> Blocks.BASALT.defaultBlockState();
            case BLACKSTONE -> BLACKSTONE;
            case MAGMA -> Blocks.MAGMA_BLOCK.defaultBlockState();
            case PATH -> Blocks.DIRT_PATH.defaultBlockState();
            case DIRT -> Blocks.DIRT.defaultBlockState();
            case DEEPSLATE -> DEEPSLATE;
        };
    }

    /** 겉흙 아래 몸 (지형 종류마다) */
    static BlockState body(String terrain, int y) {
        if (y < 0) return DEEPSLATE;
        return switch (terrain) {
            case "void" -> DEEPSLATE;
            case "muspel" -> BLACKSTONE;
            default -> STONE;
        };
    }
}
