package mmorpg.core.field;

import java.util.ArrayList;
import java.util.List;

/**
 * 섬 맵에 짓는 것 (2026-11-07): 조각 (Piece) 마다 칸 (x, z) 의 블록을 아래에서 위로 알려 줌 — 게임은 청크를 만들 때 그 청크에 걸친 칸만 놓음
 * (fabric field.FieldPieces). 블록은 "minecraft:이름[상태=값,…]" 글자 (게임 쪽에서 블록 상태로).
 * <p>
 * 다리 (돌 아치 다리 — 16칸마다 기둥 · 기둥 사이 아치 · 가문비 나무 갑판 · 통나무 난간 · 8칸마다 기둥과 등불 · 두 끝의 문) · 마을 ({@link FieldTown}).
 */
public final class FieldBuild {
    private FieldBuild() {}

    /** 블록을 받는 곳 */
    public interface Out {
        void set(int y, String block);
    }

    /** 짓는 것 하나: 차지하는 네모 (x · z) 와 칸마다의 블록 */
    public interface Piece {
        int minX();

        int maxX();

        int minZ();

        int maxZ();

        /** 칸 (x, z) 의 블록 — ground 는 그 칸의 지형 (FieldTerrain.sample) */
        void column(int x, int z, FieldTerrain.Column ground, Out out);
    }

    /** 섬 맵에 짓는 것 모두: 조각 · 자리표 (NPC · 이동 지점 · 허수아비) */
    public record Built(List<Piece> pieces, List<Blueprint.Mark> marks) {}

    /** 섬 맵의 조각 모두 (다리 · 마을) */
    public static Built build(FieldTerrain t) {
        List<Piece> out = new ArrayList<>();
        List<Blueprint.Mark> marks = new ArrayList<>();
        for (FieldTerrain.Span s : t.spans()) out.add(new Bridge(s, t.plan().sea));
        FieldPlan.Site town = t.plan().site("town");
        if (town != null) {
            int g = t.plan().sea + 9;
            for (FieldTerrain.Plot p : t.plots()) if (p.id().equals("town")) g = p.y();
            FieldTown.Result r = FieldTown.build((int) Math.round(town.x()), (int) Math.round(town.z()), g);
            out.addAll(r.pieces());
            marks.addAll(r.marks());
        }
        FieldCamps.build(t, out, marks);
        return new Built(List.copyOf(out), List.copyOf(marks));
    }

    /** 섬 맵의 조각 모두 */
    public static List<Piece> pieces(FieldTerrain t) {
        return build(t).pieces();
    }

    // ------------------------------------------------------------------ 다리

    /** 돌 아치 다리 (축을 따라 곧게 — 대각선도 칸마다 계산) */
    public static final class Bridge implements Piece {
        static final int HALF = 2;
        static final int PIER_EVERY = 16;
        static final int POST_EVERY = 8;
        final FieldTerrain.Span span;
        final int sea;
        final double ux;
        final double uz;
        final double len;
        final int minX;
        final int maxX;
        final int minZ;
        final int maxZ;
        final String axis;
        final String across;
        final String railStates;

        Bridge(FieldTerrain.Span span, int sea) {
            this.span = span;
            this.sea = sea;
            this.len = span.length();
            this.ux = (span.bx() - span.ax()) / len;
            this.uz = (span.bz() - span.az()) / len;
            int pad = HALF + 4;
            this.minX = (int) Math.floor(Math.min(span.ax(), span.bx())) - pad;
            this.maxX = (int) Math.ceil(Math.max(span.ax(), span.bx())) + pad;
            this.minZ = (int) Math.floor(Math.min(span.az(), span.bz())) - pad;
            this.maxZ = (int) Math.ceil(Math.max(span.az(), span.bz())) + pad;
            boolean alongX = Math.abs(ux) >= Math.abs(uz);
            this.axis = alongX ? "x" : "z";
            this.across = alongX ? "z" : "x";
            this.railStates = alongX ? "east=true,west=true" : "north=true,south=true";
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
        public void column(int x, int z, FieldTerrain.Column ground, Out out) {
            double dx = x + 0.5 - span.ax();
            double dz = z + 0.5 - span.az();
            double s = dx * ux + dz * uz;
            double w = -dx * uz + dz * ux;
            if (s < -0.5 || s > len + 0.5) return;
            int D = span.deck();
            int si = (int) Math.floor(s);
            int wi = (int) Math.round(w);
            int aw = Math.abs(wi);
            int pierPhase = Math.floorMod(si, PIER_EVERY);
            boolean pier = pierPhase <= 1 || pierPhase == PIER_EVERY - 1;
            boolean overWater = !ground.land() || ground.ground < D - 2;
            boolean end = si < 3 || si > (int) len - 3;
            // 기둥 (물 위만 — 땅 위 부분은 땅이 받침): 너비 + 양옆 버팀 1칸
            if (pier && overWater && aw <= HALF + 1) {
                int from = Math.min(ground.ground, D - 2) + 1;
                for (int y = from; y <= D - 1; y++) {
                    String b = y <= sea + 1 && y >= sea - 1 ? "minecraft:mossy_stone_bricks" : "minecraft:stone_bricks";
                    if (aw == HALF + 1 && y == D - 1) b = "minecraft:chiseled_stone_bricks";
                    out.set(y, b);
                }
                if (aw == HALF + 1) {
                    out.set(D, "minecraft:stone_brick_wall");
                    out.set(D + 1, "minecraft:lantern[hanging=false]");
                    return;
                }
            }
            if (aw > HALF) return;
            // 아치: 기둥 곁은 두껍게, 가운데는 얇게 (돌 벽돌) — 물 위만
            if (overWater) {
                double u = (s % PIER_EVERY) / PIER_EVERY;
                int under = D - 2 - (int) Math.round(5 * Math.pow(1 - Math.sin(Math.PI * u), 1.5));
                for (int y = Math.max(under, ground.ground + 1); y <= D - 1; y++) out.set(y, aw == HALF ? "minecraft:stone_bricks" : "minecraft:smooth_stone");
                if (aw == HALF && under > ground.ground + 1) out.set(under - 1, "minecraft:stone_brick_slab[type=top]");
            } else {
                for (int y = ground.ground + 1; y <= D - 1; y++) out.set(y, "minecraft:stone_bricks");
            }
            // 갑판 · 난간
            if (aw == HALF) {
                out.set(D, "minecraft:dark_oak_log[axis=" + axis + "]");
                boolean post = Math.floorMod(si, POST_EVERY) == 0 || end && (si == 0 || si == (int) len);
                if (post) {
                    out.set(D + 1, "minecraft:spruce_log[axis=y]");
                    out.set(D + 2, "minecraft:spruce_log[axis=y]");
                    out.set(D + 3, "minecraft:lantern[hanging=false]");
                } else {
                    out.set(D + 1, "minecraft:spruce_fence[" + railStates + "]");
                }
            } else {
                out.set(D, aw == 0 && Math.floorMod(si, 2) == 0 ? "minecraft:stripped_spruce_log[axis=" + axis + "]" : "minecraft:spruce_planks");
            }
            // 두 끝의 문: 기둥 · 들보 · 매단 등불
            if (si == 1 || si == (int) len - 1) {
                if (aw == HALF) {
                    for (int y = D + 1; y <= D + 5; y++) out.set(y, "minecraft:dark_oak_log[axis=y]");
                }
                out.set(D + 6, "minecraft:dark_oak_log[axis=" + across + "]");
                out.set(D + 7, aw == HALF ? "minecraft:spruce_slab[type=bottom]" : "minecraft:spruce_planks");
                if (aw == 1) out.set(D + 5, "minecraft:lantern[hanging=true]");
                else if (aw == 0) out.set(D + 8, "minecraft:spruce_slab[type=bottom]");
            }
        }
    }
}
