package mmorpg.core.field;

/**
 * 번화가 줄집 (2026-11-07 — 사용자 "중앙 마을이 너무 허전하 보임 건물들이 촘촘히 있는 변화한 곳"): 길을 따라 벽을 맞대고 늘어선 2 ~ 3층 집 · 가게.
 * 설계 방향은 앞 (문) 이 남쪽 (z = D − 1), 폭 W (홀수 5 · 7 · 9) · 길이 D (7 ~ 11). 층마다 4칸 (벽 3 · 들보 1).
 * 아래층은 돌 (가게면 큰 창 · 차양 · 깃발), 위층은 나무 뼈대 + 회벽 · 창 · 덧창이고 길 쪽으로 한 칸 나옴 (받침 계단).
 * 지붕은 용마루가 길과 나란하거나 박공이 길을 봄 (frontGable) — 길 쪽 처마는 내민 층과 같은 줄까지만 (5칸 길 양쪽 집 사이로 하늘 3칸).
 * 자기 땅 밖 옆으로는 아무것도 놓지 않음 (옆집과 벽을 맞댐 — 지붕도 옆으로 안 나옴).
 */
final class FieldRows {
    private FieldRows() {}

    /** 차림: 아래층 돌 · 기둥 (세로 통나무) · 들보 (가로 통나무) · 회벽 · 지붕 계단 · 용마루 · 문 · 덧창 · 받침 계단 */
    record Facade(String base, String post, String beam, String infill, String roof, String ridge, String door, String trap, String stair) {}

    /** 차림 여덟 — 흰 회벽 · 벽돌 · 진흙 벽돌 · 사암, 지붕은 짙은 기와 · 짙은 참나무 · 벽돌 · 가문비 (무지개처럼 섞이지 않게 차분한 색만) */
    static final Facade[] FACADES = {
        new Facade("minecraft:stone_bricks|minecraft:cobblestone|minecraft:stone_bricks", "minecraft:dark_oak_log", "minecraft:stripped_dark_oak_log",
                "minecraft:white_terracotta", "minecraft:deepslate_tile_stairs", "minecraft:deepslate_tiles", "minecraft:dark_oak_door",
                "minecraft:dark_oak_trapdoor", "minecraft:dark_oak_stairs"),
        new Facade("minecraft:cobblestone|minecraft:mossy_cobblestone|minecraft:cobblestone", "minecraft:spruce_log", "minecraft:stripped_spruce_log",
                "minecraft:calcite", "minecraft:dark_oak_stairs", "minecraft:dark_oak_planks", "minecraft:spruce_door", "minecraft:spruce_trapdoor",
                "minecraft:spruce_stairs"),
        new Facade("minecraft:stone_bricks|minecraft:andesite", "minecraft:dark_oak_log", "minecraft:stripped_dark_oak_log", "minecraft:smooth_sandstone",
                "minecraft:brick_stairs", "minecraft:bricks", "minecraft:oak_door", "minecraft:spruce_trapdoor", "minecraft:dark_oak_stairs"),
        new Facade("minecraft:andesite|minecraft:stone|minecraft:andesite", "minecraft:spruce_log", "minecraft:stripped_spruce_log", "minecraft:bricks",
                "minecraft:deepslate_tile_stairs", "minecraft:deepslate_tiles", "minecraft:spruce_door", "minecraft:dark_oak_trapdoor", "minecraft:spruce_stairs"),
        new Facade("minecraft:tuff_bricks", "minecraft:stripped_dark_oak_log", "minecraft:dark_oak_log", "minecraft:calcite", "minecraft:spruce_stairs",
                "minecraft:spruce_planks", "minecraft:dark_oak_door", "minecraft:spruce_trapdoor", "minecraft:dark_oak_stairs"),
        new Facade("minecraft:polished_andesite", "minecraft:dark_oak_log", "minecraft:stripped_dark_oak_log", "minecraft:mud_bricks",
                "minecraft:deepslate_tile_stairs", "minecraft:deepslate_tiles", "minecraft:dark_oak_door", "minecraft:dark_oak_trapdoor",
                "minecraft:dark_oak_stairs"),
        new Facade("minecraft:cobblestone|minecraft:stone_bricks", "minecraft:oak_log", "minecraft:stripped_oak_log", "minecraft:white_terracotta",
                "minecraft:dark_oak_stairs", "minecraft:dark_oak_planks", "minecraft:oak_door", "minecraft:oak_trapdoor", "minecraft:oak_stairs"),
        new Facade("minecraft:stone_bricks|minecraft:mossy_stone_bricks|minecraft:stone_bricks", "minecraft:spruce_log", "minecraft:stripped_spruce_log",
                "minecraft:pale_oak_planks", "minecraft:brick_stairs", "minecraft:bricks", "minecraft:spruce_door", "minecraft:spruce_trapdoor",
                "minecraft:spruce_stairs")
    };

    /** 가게 차양 (위 반 블록 양털) · 깃발 */
    static final String[] AWNINGS = {"minecraft:red_wool_slab[type=top]", "minecraft:blue_wool_slab[type=top]", "minecraft:green_wool_slab[type=top]"};
    static final String[] BANNERS = {"minecraft:red_wall_banner", "minecraft:blue_wall_banner", "minecraft:green_wall_banner"};

    /** 기둥 자리 (앞 · 뒤 벽) */
    static int[] posts(int W) {
        return W >= 7 ? new int[] {0, W / 2, W - 1} : new int[] {0, W - 1};
    }

    /** 위층 창 자리 */
    static int[] windows(int W) {
        return switch (W) {
            case 5 -> new int[] {2};
            case 7 -> new int[] {1, 5};
            case 9 -> new int[] {2, 6};
            default -> new int[] {W / 4, W - 1 - W / 4};
        };
    }

    /** 들보 (높이 y — 앞 z = D 까지) */
    static void beams(Blueprint b, Facade f, int W, int D, int y) {
        b.fill(0, y, D, W - 1, y, D, f.beam() + "[axis=x]");
        b.fill(0, y, 0, W - 1, y, 0, f.beam() + "[axis=x]");
        b.fill(0, y, 1, 0, y, D - 1, f.beam() + "[axis=z]");
        b.fill(W - 1, y, 1, W - 1, y, D - 1, f.beam() + "[axis=z]");
    }

    /** 지붕 높이 (맨 위 들보 위로) */
    static int roofRise(int W, int D, boolean frontGable) {
        return frontGable ? W / 2 : (D + 1) / 2;
    }

    /**
     * 줄집 하나. stories = 층 수 (2 · 3), shop = 아래층이 가게 (큰 창 · 차양 · 깃발 · 판매대), seed = 굴뚝 · 연기 · 색 고르기.
     */
    static void rowhouse(Blueprint b, int W, int D, int stories, Facade f, boolean frontGable, boolean shop, int seed) {
        int c = W / 2;
        int top = 4 * stories;
        int rise = roofRise(W, D, frontGable);
        b.air(0, 1, 0, W - 1, top + rise + 4, D - 1);
        b.air(0, 4, D, W - 1, top + rise + 4, D + 1);
        b.fill(0, -3, 0, W - 1, -1, D - 1, "minecraft:cobblestone");
        b.fill(0, 0, 0, W - 1, 0, D - 1, f.base());
        b.fill(1, 0, 1, W - 2, 0, D - 2, "minecraft:spruce_planks");
        // 아래층 (돌)
        b.walls(0, 1, 0, W - 1, 3, D - 1, f.base());
        // 위층: 바닥 (앞으로 한 칸 — 내민 층) · 들보 · 회벽 · 기둥 · 창 · 덧창
        for (int s = 1; s < stories; s++) {
            int y0 = 4 * s;
            b.fill(0, y0, 0, W - 1, y0, D, "minecraft:spruce_planks");
            beams(b, f, W, D, y0);
            b.fill(0, y0 + 1, 0, W - 1, y0 + 3, 0, f.infill());
            b.fill(0, y0 + 1, D, W - 1, y0 + 3, D, f.infill());
            b.fill(0, y0 + 1, 1, 0, y0 + 3, D - 1, f.infill());
            b.fill(W - 1, y0 + 1, 1, W - 1, y0 + 3, D - 1, f.infill());
            for (int x : posts(W)) {
                b.fill(x, y0 + 1, D, x, y0 + 3, D, f.post() + "[axis=y]");
                b.fill(x, y0 + 1, 0, x, y0 + 3, 0, f.post() + "[axis=y]");
            }
            for (int x : windows(W)) {
                b.fill(x, y0 + 2, D, x, y0 + 3, D, "minecraft:glass_pane");
                b.fill(x, y0 + 2, 0, x, y0 + 3, 0, "minecraft:glass_pane");
                if (x - 1 >= 0) b.fill(x - 1, y0 + 2, D + 1, x - 1, y0 + 3, D + 1, f.trap() + "[facing=south,half=bottom,open=true]");
                if (x + 1 <= W - 1) b.fill(x + 1, y0 + 2, D + 1, x + 1, y0 + 3, D + 1, f.trap() + "[facing=south,half=bottom,open=true]");
            }
            // 안: 매단 등 · 통
            b.set(c, y0 + 3, D / 2, "minecraft:lantern[hanging=true]");
            b.set(W - 2, y0 + 1, D - 1, "minecraft:barrel[facing=up]");
            b.set(1, y0, 1, "minecraft:air");
        }
        // 맨 위 들보 · 다락 바닥
        beams(b, f, W, D, top);
        b.fill(1, top, 1, W - 2, top, D - 1, "minecraft:spruce_planks");
        // 내민 층 받침 (아래층 앞 · 기둥 자리)
        for (int x : posts(W)) {
            if (shop && x != 0 && x != W - 1) continue;
            b.set(x, 3, D, f.stair() + "[facing=north,half=top]");
        }
        // 문 · 아래층 창
        b.set(c, 1, D - 1, f.door() + "[facing=north,half=lower,hinge=left,open=false]");
        b.set(c, 2, D - 1, f.door() + "[facing=north,half=upper,hinge=left,open=false]");
        if (shop) {
            for (int x = 1; x <= W - 2; x++) if (x != c) b.fill(x, 2, D - 1, x, 3, D - 1, "minecraft:glass_pane");
            String awning = AWNINGS[Math.floorMod(seed, AWNINGS.length)];
            for (int x = 1; x <= W - 2; x++) b.set(x, 3, D, awning);
            b.set(0, 2, D, BANNERS[Math.floorMod(seed / 3, BANNERS.length)] + "[facing=south]");
            // 판매대 (문 앞을 비움)
            for (int x = 1; x <= W - 2; x++) if (Math.abs(x - c) > 0) b.set(x, 1, D - 3, "minecraft:spruce_slab[type=top]");
            b.set(1, 1, D - 4, "minecraft:barrel[facing=up]");
            if (W >= 7) b.set(W - 2, 1, D - 4, "minecraft:barrel[facing=up]");
        } else {
            for (int x : new int[] {c - 2, c + 2}) if (x >= 1 && x <= W - 2) b.set(x, 2, D - 1, "minecraft:glass_pane");
            if (W == 5) {
                b.set(1, 2, D - 1, "minecraft:glass_pane");
                b.set(3, 2, D - 1, "minecraft:glass_pane");
            }
            b.set(Math.min(W - 1, c + 1), 3, D, "minecraft:lantern[hanging=true]");
        }
        b.set(c, 3, D / 2, "minecraft:lantern[hanging=true]");
        // 사다리 (뒤 벽 안쪽 — 층마다 구멍)
        b.fill(1, 1, 1, 1, top - 1, 1, "minecraft:ladder[facing=south]");
        // 지붕
        if (!frontGable) {
            for (int k = 0; ; k++) {
                int zf = D - k;
                int zb = -1 + k;
                int y = top + k;
                if (zf < zb) break;
                if (zf == zb) {
                    b.fill(0, y, zf, W - 1, y, zf, f.ridge());
                    break;
                }
                b.fill(0, y, zf, W - 1, y, zf, f.roof() + "[facing=north,half=bottom]");
                b.fill(0, y, zb, W - 1, y, zb, f.roof() + "[facing=south,half=bottom]");
                if (k >= 1 && zb + 1 <= zf - 1) {
                    b.fill(0, y, zb + 1, 0, y, zf - 1, f.infill());
                    b.fill(W - 1, y, zb + 1, W - 1, y, zf - 1, f.infill());
                }
            }
        } else {
            for (int k = 0; k <= c; k++) {
                int y = top + k;
                int xl = k;
                int xr = W - 1 - k;
                if (xl >= xr) {
                    b.fill(c, y, -1, c, y, D, f.ridge());
                    break;
                }
                b.fill(xl, y, -1, xl, y, D, f.roof() + "[facing=east,half=bottom]");
                b.fill(xr, y, -1, xr, y, D, f.roof() + "[facing=west,half=bottom]");
                if (k >= 1 && xl + 1 <= xr - 1) {
                    b.fill(xl + 1, y, D, xr - 1, y, D, f.infill());
                    b.fill(xl + 1, y, 0, xr - 1, y, 0, f.infill());
                }
            }
            if (c >= 2) b.fill(c, top + 1, D, c, top + c - 1, D, f.post() + "[axis=y]");
            if (c >= 3) {
                b.set(c - 1, top + 1, D, "minecraft:glass_pane");
                b.set(c + 1, top + 1, D, "minecraft:glass_pane");
            }
        }
        // 굴뚝 (셋 중 하나) · 연기 (그 가운데 넷 중 하나)
        if (Math.floorMod(seed, 3) == 0) {
            int hx = W - 2;
            int ytop = top + rise + 2;
            b.fill(hx, 1, 1, hx, ytop, 1, "minecraft:bricks");
            if (Math.floorMod(seed, 4) == 0) b.set(hx, ytop + 1, 1, "minecraft:campfire[lit=true,signal_fire=false]");
        }
    }
}
