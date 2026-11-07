package mmorpg.fabric.field;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import mmorpg.core.field.FieldBuild;
import mmorpg.core.field.FieldTerrain;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.tree.TreeBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * 섬 맵에 짓는 것 놓기 (2026-11-07): 청크를 장식한 뒤 (나무 · 풀 다음) core FieldBuild 의 조각 가운데 이 청크에 걸친 칸만 놓음.
 * 블록 글자 ("minecraft:이름[상태=값]") 는 처음 한 번 블록 상태로 바꿔 둠 (모르는 블록이면 공기 — 기록에 남김).
 */
public final class FieldPieces {
    private FieldPieces() {}

    private static volatile List<FieldBuild.Piece> pieces;
    private static final Map<String, BlockState> STATES = new ConcurrentHashMap<>();

    static void init() {
        // 조각은 처음 쓸 때 만듦 (field.json → 지형 → 조각)
    }

    static List<FieldBuild.Piece> pieces() {
        List<FieldBuild.Piece> p = pieces;
        if (p == null) {
            synchronized (FieldPieces.class) {
                p = pieces;
                if (p == null) pieces = p = List.copyOf(FieldBuild.pieces(FieldWorld.terrain()));
            }
        }
        return p;
    }

    /** "minecraft:spruce_stairs[facing=north,half=top]" → 블록 상태 */
    static BlockState state(String text) {
        return STATES.computeIfAbsent(text, t -> {
            int i = t.indexOf('[');
            String name = i < 0 ? t : t.substring(0, i);
            Map<String, String> props = new LinkedHashMap<>();
            if (i >= 0 && t.endsWith("]")) {
                for (String kv : t.substring(i + 1, t.length() - 1).split(",")) {
                    int e = kv.indexOf('=');
                    if (e > 0) props.put(kv.substring(0, e).trim(), kv.substring(e + 1).trim());
                }
            }
            BlockState s = TreeBlocks.stateOf(name, props);
            if (s == null) {
                Mmorpg.LOG.warn("[field] unknown block {}", t);
                return FieldBlocks.AIR;
            }
            return s;
        });
    }

    /** 이 청크에 걸친 조각을 놓음 (청크 밖 칸은 건드리지 않음) */
    static void place(WorldGenLevel level, ChunkAccess chunk) {
        ChunkPos cp = chunk.getPos();
        int bx = FieldWorld.minBlockX(cp);
        int bz = FieldWorld.minBlockZ(cp);
        FieldTerrain t = FieldWorld.terrain();
        FieldTerrain.Column col = new FieldTerrain.Column();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int minY = chunk.getMinY();
        int maxY = minY + chunk.getHeight() - 1;
        for (FieldBuild.Piece p : pieces()) {
            if (p.maxX() < bx || p.minX() > bx + 15 || p.maxZ() < bz || p.minZ() > bz + 15) continue;
            int x0 = Math.max(bx, p.minX());
            int x1 = Math.min(bx + 15, p.maxX());
            int z0 = Math.max(bz, p.minZ());
            int z1 = Math.min(bz + 15, p.maxZ());
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    t.sample(x, z, col);
                    final int fx = x;
                    final int fz = z;
                    p.column(x, z, col, (y, block) -> {
                        if (y < minY || y > maxY) return;
                        chunk.setBlockState(pos.set(fx, y, fz), state(block), 0);
                    });
                }
            }
        }
    }
}
