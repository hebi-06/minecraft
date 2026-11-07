package mmorpg.fabric.field;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mmorpg.core.Obj;
import mmorpg.core.field.FieldPlan;
import mmorpg.core.field.FieldTerrain;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.boss.AltarBlocks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.ArenaBlocks;
import mmorpg.fabric.boss.ArenaThemes;
import mmorpg.fabric.tree.TreeBlocks;
import mmorpg.fabric.tree.TreePlan;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 섬 맵에 미리 지어 두는 것 (2026-11-07 — 게임 쪽 설계를 쓰는 조각): 마을 섬의 세계수 (TreePlan — /worldtree 와 같은 설계, 높이 72) ·
 * 보스 신전 (ArenaThemes — /boss build 와 같은 설계, 제단 블록까지). 청크를 만들 때 그 청크에 걸친 칸만 놓음 (FieldPieces 다음).
 * 제단 목록 (보스 엔진) 에는 세계를 처음 열 때 FieldSetup 이 "지어짐" 으로 올림.
 */
public final class FieldExtras {
    private FieldExtras() {}

    /** 블록 상태로 놓는 조각 */
    interface StatePiece {
        int minX();

        int maxX();

        int minZ();

        int maxZ();

        void place(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int minY, int maxY);
    }

    /** 보스 제단 자리 (보스 키 · 제단 블록 칸) */
    public record AltarSpot(String key, BlockPos pos) {}

    private static volatile List<StatePiece> pieces;
    private static volatile List<AltarSpot> altars;

    public static final int TREE_HEIGHT = 72;

    static List<StatePiece> pieces() {
        List<StatePiece> p = pieces;
        if (p == null) {
            synchronized (FieldExtras.class) {
                p = pieces;
                if (p == null) {
                    List<StatePiece> out = new ArrayList<>();
                    List<AltarSpot> alt = new ArrayList<>();
                    build(FieldWorld.terrain(), out, alt);
                    altars = List.copyOf(alt);
                    pieces = p = List.copyOf(out);
                }
            }
        }
        return p;
    }

    /** 미리 지은 보스 제단 자리 모두 */
    public static List<AltarSpot> altars() {
        pieces();
        return altars;
    }

    /** 세계수 밑동 (밑동 칸 = 땅 위 첫 칸) */
    public static BlockPos treeBase() {
        FieldTerrain t = FieldWorld.terrain();
        FieldPlan.Site s = t.plan().site("tree");
        if (s == null) return null;
        int y = t.plan().sea + 12;
        for (FieldTerrain.Plot p : t.plots()) if (p.id().equals("tree")) y = p.y();
        return new BlockPos((int) Math.floor(s.x()), y + 1, (int) Math.floor(s.z()));
    }

    private static void build(FieldTerrain t, List<StatePiece> out, List<AltarSpot> alt) {
        BlockPos base = treeBase();
        if (base != null) out.add(new Tree(base));
        Obj bosses = Arena.BOSSES;
        for (FieldPlan.Site s : t.plan().sites("boss")) {
            int y = t.plan().sea + 3;
            for (FieldTerrain.Plot p : t.plots()) if (p.id().equals(s.id())) y = p.y();
            BlockPos at = new BlockPos((int) Math.floor(s.x()), y + 1, (int) Math.floor(s.z()));
            Obj b = bosses.objOrNull(s.boss());
            Obj ar = b == null ? null : b.objOrNull("arena");
            if (ar == null) continue;
            ArenaThemes.Plan plan = ArenaThemes.arenaPlan(s.boss(), at.getX(), at.getY(), at.getZ());
            if (plan == null) continue;
            int radius = (int) ar.numOr("radius", 20);
            List<ArenaThemes.StageOps> stages = ArenaThemes.planOps(at.getX(), at.getY(), at.getZ(), radius, plan);
            out.add(new Temple(s.boss(), at, radius + 3, stages));
            alt.add(new AltarSpot(s.boss(), at));
        }
        Mmorpg.LOG.info("[field] extras: world tree at {}, {} temples", base == null ? "-" : base.toShortString(), alt.size());
    }

    // ------------------------------------------------------------------ 세계수

    /** 세계수: TreePlan 칸을 기둥마다 묶어 놓음 (줄기는 늘, 잎 · 덩굴 · 이끼는 빈 곳 · 풀에만) */
    static final class Tree implements StatePiece {
        final BlockPos base;
        final Map<Long, List<TreePlan.Op>> byColumn = new HashMap<>();
        final int minX;
        final int maxX;
        final int minZ;
        final int maxZ;

        Tree(BlockPos base) {
            this.base = base;
            TreePlan plan = TreePlan.plan(TREE_HEIGHT, TreePlan.seedOf(base.getX(), base.getY(), base.getZ()));
            int a = Integer.MAX_VALUE;
            int b = Integer.MIN_VALUE;
            int c = Integer.MAX_VALUE;
            int d = Integer.MIN_VALUE;
            for (TreePlan.Op o : plan.allOps()) {
                int x = base.getX() + o.dx();
                int z = base.getZ() + o.dz();
                byColumn.computeIfAbsent(key(x, z), k -> new ArrayList<>()).add(o);
                a = Math.min(a, x);
                b = Math.max(b, x);
                c = Math.min(c, z);
                d = Math.max(d, z);
            }
            minX = a;
            maxX = b;
            minZ = c;
            maxZ = d;
        }

        static long key(int x, int z) {
            return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        }

        @Override
        public int minX() {
            return minX;
        }

        @Override
        public int maxX() {
            return maxX;
        }

        @Override
        public int minZ() {
            return minZ;
        }

        @Override
        public int maxZ() {
            return maxZ;
        }

        @Override
        public void place(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int minY, int maxY) {
            List<TreePlan.Op> ops = byColumn.get(key(x, z));
            if (ops == null) return;
            for (TreePlan.Op o : ops) {
                int y = base.getY() + o.dy();
                if (y < minY || y > maxY) continue;
                BlockState s = TreeBlocks.state(o.block());
                BlockState now = level.getBlockState(pos.set(x, y, z));
                boolean ok = switch (o.mode()) {
                    case WOOD -> true;
                    case LEAF -> now.isAir() || now.canBeReplaced() || now.is(net.minecraft.tags.BlockTags.LEAVES);
                    default -> now.isAir() || now.canBeReplaced();
                };
                if (ok) level.setBlock(pos.set(x, y, z), s, Block.UPDATE_CLIENTS);
            }
        }
    }

    // ------------------------------------------------------------------ 보스 신전

    /** 보스 신전: ArenaThemes 작업 (x 줄마다 z 구간 채우기 · 한 칸) 을 x 로 묶어 둠, 제단 블록도 */
    static final class Temple implements StatePiece {
        final String key;
        final BlockPos altar;
        final int span;
        final Map<Integer, List<ArenaThemes.Op>> byX = new HashMap<>();

        Temple(String key, BlockPos altar, int span, List<ArenaThemes.StageOps> stages) {
            this.key = key;
            this.altar = altar;
            this.span = span;
            for (ArenaThemes.StageOps st : stages) {
                for (ArenaThemes.Op o : st.ops()) {
                    for (int x = Math.min(o.x(), o.x2()); x <= Math.max(o.x(), o.x2()); x++) byX.computeIfAbsent(x, k -> new ArrayList<>()).add(o);
                }
            }
        }

        @Override
        public int minX() {
            return altar.getX() - span;
        }

        @Override
        public int maxX() {
            return altar.getX() + span;
        }

        @Override
        public int minZ() {
            return altar.getZ() - span;
        }

        @Override
        public int maxZ() {
            return altar.getZ() + span;
        }

        @Override
        public void place(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int minY, int maxY) {
            List<ArenaThemes.Op> ops = byX.get(x);
            if (ops != null) {
                for (ArenaThemes.Op o : ops) {
                    if (z < Math.min(o.z(), o.z2()) || z > Math.max(o.z(), o.z2())) continue;
                    BlockState s = ArenaBlocks.state(o.block(), o.states());
                    for (int y = Math.max(minY, Math.min(o.y(), o.y2())); y <= Math.min(maxY, Math.max(o.y(), o.y2())); y++) {
                        level.setBlock(pos.set(x, y, z), s, Block.UPDATE_CLIENTS);
                    }
                }
            }
            if (x == altar.getX() && z == altar.getZ()) level.setBlock(pos.set(altar), AltarBlocks.block(key).defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
