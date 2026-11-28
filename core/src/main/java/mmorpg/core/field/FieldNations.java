package mmorpg.core.field;

import java.util.ArrayList;
import java.util.List;

/**
 * 나라 섬의 건물 (2026-11-28 — 사용자 "불의 나라나 땅의 나라 번개의 나라 같은거 추가 하고 싶으면 추가해도 됨 거기 가서 퀘스트 받게 하게 해도 됨").
 * <ul>
 * <li>번개 나라 섬: 이나에 마을 (일본풍 민가 다섯 · 곳간 · 볏단 걸이 · 벚나무 · 석등 · 가게 둘 · 큰 도리이 · 이동 지점) · 벼락받이 사당 (산꼭대기 자갈 마당 —
 * 구리 큰 기둥에 볏짚 금줄과 흰 시데, 꼭대기 피뢰침 셋 · 본전 (붉은 기둥 · 흰 벽 · 푸른 구리 지붕) · 석등 · 큰 도리이) · 사당으로 오르는 길의 도리이 길
 * (사당 쪽 90칸은 두 칸마다 — 붉은 굴, 그 아래는 여덟 칸마다, 마을 앞까지)</li>
 * <li>바위 나라 섬: 큰 저울 (받침점 탑 — 아래 현자 회의장 · 꼭대기 받침과 둘레 마루 · 금 바늘과 눈금판 · 좌우 80칸 저울대 · 끝마다 걸개와 사슬 넷) ·
 * 두 접시 (땅 위 6칸에 매달린 사암 접시 · 금 테 · 청금석 무늬 · 계단 — 서쪽 = 세베린 · 이동 지점, 동쪽 = 리브라 · 작은 저울 · 가게) ·
 * 탑 아래 ↔ 꼭대기 오르내림 발판 (자리표 lift_scale_a · lift_scale_b — 게임 쪽 FieldLifts)</li>
 * </ul>
 * 사당 기둥 꼭대기의 자리표 bolt = 밤마다 벼락이 떨어지는 곳 (게임 쪽 FieldShrines). 설계 방향은 모두 앞 (문) 이 남쪽.
 */
final class FieldNations {
    private FieldNations() {}

    static final String RED = "minecraft:red_concrete";
    static final String BLACK = "minecraft:polished_blackstone";
    static final String COPPER = "minecraft:waxed_weathered_cut_copper|minecraft:waxed_oxidized_cut_copper|minecraft:waxed_weathered_copper|minecraft:waxed_oxidized_cut_copper";
    static final String COPPER_ROOF = "minecraft:waxed_oxidized_cut_copper_stairs";
    static final String STONE = "minecraft:stone_bricks|minecraft:polished_andesite|minecraft:stone_bricks|minecraft:mossy_stone_bricks";
    static final String SAND = "minecraft:smooth_sandstone|minecraft:smooth_sandstone|minecraft:cut_sandstone";
    static final String TILE = "minecraft:light_blue_glazed_terracotta|minecraft:cyan_glazed_terracotta";
    static final String GOLD = "minecraft:gold_block";
    static final String LAPIS = "minecraft:lapis_block";

    /** 흰 회벽 (2026-11-28 CI 사진 — 흰 테라코타는 분홍빛이 돌아 방해석으로) */
    static final String PLASTER = "minecraft:calcite";

    /** 일본풍 민가 (흰 회벽 · 짙은 나무 뼈대 · 검은 기와) */
    static final FieldTown.Style JAPAN = new FieldTown.Style("minecraft:stripped_dark_oak_log", PLASTER, "minecraft:spruce_stairs",
            "minecraft:deepslate_tile_stairs", "minecraft:deepslate_tiles", "minecraft:polished_andesite|minecraft:andesite|minecraft:polished_andesite",
            "minecraft:spruce_planks", "minecraft:spruce_door", "minecraft:dark_oak_fence");

    // ------------------------------------------------------------------ 작은 설계 (번개 나라)

    /** 민가: 긴 집 (일본풍 차림) + 앞 툇마루 (반 블록) */
    static void minka(Blueprint b, int W, int D, int H, String npc) {
        FieldTown.longhouse(b, W, D, H, JAPAN, npc);
        b.fill(-1, 1, D, W, 1, D, "minecraft:spruce_slab[type=bottom]");
    }

    /**
     * 도리이: 가운데 (0, 0) · 기둥 x = ±half (설계 x 로 걸침 — 길이 남북이면 앞 = 남쪽) · 갓 (kasagi) 높이 H.
     * 기둥은 저마다의 땅 (baseL · baseR — 설계 높이) 위에 섬 (밑 한 칸 검은 테). 관 (nuki) H−3 · 이음 (shimaki) H−1 · 검은 갓 H (양 끝이 들림).
     * big = 큰 도리이 (받침돌 · 검은 현판).
     */
    static void torii(Blueprint b, int half, int H, int baseL, int baseR, boolean big) {
        for (int s : new int[] {-1, 1}) {
            int x = s * half;
            int base = s < 0 ? baseL : baseR;
            if (big) b.set(x, base, 0, "minecraft:polished_andesite");
            b.set(x, base + 1, 0, BLACK);
            b.fill(x, base + 2, 0, x, H - 1, 0, RED);
        }
        b.fill(-half - 1, H - 3, 0, half + 1, H - 3, 0, RED);
        if (big) b.set(0, H - 2, 0, "minecraft:black_concrete");
        b.fill(-half - 1, H - 1, 0, half + 1, H - 1, 0, RED);
        b.fill(-half - 1, H, 0, half + 1, H, 0, BLACK);
        for (int s : new int[] {-1, 1}) {
            b.set(s * (half + 2), H, 0, "minecraft:polished_blackstone_slab[type=top]");
            b.set(s * (half + 2), H + 1, 0, "minecraft:polished_blackstone_slab[type=bottom]");
        }
    }

    /** 석등 (1 × 1): 받침 · 기둥 · 등불 · 지붕 */
    static void stoneLantern(Blueprint b) {
        b.air(0, 1, 0, 0, 4, 0);
        b.set(0, 1, 0, "minecraft:polished_andesite");
        b.set(0, 2, 0, "minecraft:andesite_wall");
        b.set(0, 3, 0, "minecraft:lantern[hanging=false]");
        b.set(0, 4, 0, "minecraft:polished_andesite_slab[type=bottom]");
    }

    /** 벚나무: 밑동 (0, 0) · 줄기 h · 넓고 납작한 꽃 지붕 · 늘어진 꽃 · 바닥 꽃잎 */
    static void cherry(Blueprint b, int h, int seed) {
        String leaf = "minecraft:cherry_leaves[persistent=true]";
        b.air(-4, 1, -4, 4, h + 3, 4);
        b.set(0, 0, 0, "minecraft:dirt");
        b.fill(0, 1, 0, 0, h, 0, "minecraft:cherry_log[axis=y]");
        for (int y = h - 1; y <= h + 2; y++) {
            double dy = (y - h - 0.6) / 1.7;
            double r2 = 12.5 * (1 - dy * dy);
            if (r2 <= 0) continue;
            for (int z = -4; z <= 4; z++) {
                int xr = (int) Math.floor(Math.sqrt(Math.max(0, r2 - z * z)));
                if (z * z > r2) continue;
                b.fill(-xr, y, z, xr, y, z, leaf);
            }
        }
        b.fill(0, h - 1, 0, 0, h, 0, "minecraft:cherry_log[axis=y]");
        int d = seed % 2 == 0 ? 1 : -1;
        b.set(d, h, 0, "minecraft:cherry_log[axis=x]");
        b.set(0, h, -d, "minecraft:cherry_log[axis=z]");
        int[][] drop = {{3, 0}, {-3, 1}, {0, 3}, {1, -3}, {2, 2}, {-2, -2}, {-2, 2}, {2, -2}};
        for (int i = 0; i < drop.length; i++) {
            if (FieldNoise.hash(drop[i][0] + seed * 7, drop[i][1], 5) < 0.55) b.fill(drop[i][0], h - 2, drop[i][1], drop[i][0], h - 1, drop[i][1], leaf);
        }
        int[][] petals = {{2, 0}, {-1, 2}, {0, -2}, {-2, -1}, {1, 1}, {3, -1}};
        for (int i = 0; i < petals.length; i++) {
            b.set(petals[i][0], 1, petals[i][1], "minecraft:pink_petals[flower_amount=" + (2 + (i + seed) % 3) + ",facing=north]");
        }
    }

    /** 곳간 (높은 마루 7 × 7): 기둥 위 마루 · 판자 벽 · 문 · 사다리 · 볏짚 지붕 · 안의 볏섬 */
    static void granary(Blueprint b) {
        b.air(-1, 1, -1, 7, 12, 8);
        for (int[] p : new int[][] {{0, 0}, {6, 0}, {0, 6}, {6, 6}, {3, 0}, {3, 6}, {0, 3}, {6, 3}}) b.fill(p[0], 1, p[1], p[0], 2, p[1], "minecraft:dark_oak_log[axis=y]");
        b.fill(0, 3, 0, 6, 3, 6, "minecraft:spruce_planks");
        b.walls(0, 4, 0, 6, 6, 6, "minecraft:spruce_planks");
        for (int[] p : new int[][] {{0, 0}, {6, 0}, {0, 6}, {6, 6}}) b.fill(p[0], 4, p[1], p[0], 6, p[1], "minecraft:dark_oak_log[axis=y]");
        b.fill(0, 6, 0, 6, 6, 0, "minecraft:dark_oak_log[axis=x]");
        b.fill(0, 6, 6, 6, 6, 6, "minecraft:dark_oak_log[axis=x]");
        b.set(3, 4, 6, "minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false]");
        b.set(3, 5, 6, "minecraft:spruce_door[facing=north,half=upper,hinge=left,open=false]");
        for (int y = 1; y <= 3; y++) b.set(3, y, 7, "minecraft:ladder[facing=south]");
        b.fill(1, 4, 1, 2, 5, 2, "minecraft:hay_block[axis=y]");
        b.fill(4, 4, 1, 5, 4, 3, "minecraft:hay_block[axis=x]");
        for (int k = 0; k <= 4; k++) b.fill(-1 + k, 7 + k, -1 + k, 7 - k, 7 + k, 7 - k, "minecraft:hay_block[axis=y]");
        b.set(3, 12, 3, "minecraft:dark_oak_log[axis=y]");
    }

    /** 볏단 걸이 (길이 L, 설계 x 로): 4칸마다 기둥 · 위 가로대 · 걸린 볏단 */
    static void riceRack(Blueprint b, int L) {
        b.air(0, 1, 0, L - 1, 3, 0);
        for (int x = 0; x < L; x++) {
            boolean post = x % 4 == 0 || x == L - 1;
            b.set(x, 3, 0, "minecraft:spruce_fence");
            if (post) b.fill(x, 1, 0, x, 2, 0, "minecraft:spruce_fence");
            else b.set(x, 2, 0, "minecraft:hay_block[axis=x]");
        }
    }

    // ------------------------------------------------------------------ 이나에 마을

    /** 이나에 마을 (번개 나라 섬 남쪽 들 — 거점 자리 가운데 (x, z), 바닥 g): 북쪽 큰 도리이로 사당 길이 시작됨 */
    static void village(List<Blueprint> bps, int x, int g, int z) {
        // 벚나무 · 석등 (먼저 — 집이 덮어씀)
        int[][] trees = {{-6, -16}, {8, -20}, {-24, 8}, {28, -4}, {-24, 18}, {24, 16}};
        for (int i = 0; i < trees.length; i++) {
            Blueprint t = new Blueprint(x + trees[i][0], g, z + trees[i][1], "south");
            cherry(t, 5 + i % 2, i);
            bps.add(t);
        }
        Blueprint sq = new Blueprint(x, g, z, "south");
        sq.fill(-8, 0, -8, 8, 0, 8, "minecraft:dirt_path|minecraft:coarse_dirt|minecraft:dirt_path|minecraft:packed_mud");
        sq.air(-8, 1, -8, 8, 6, 8);
        // 사당으로 가는 길 (북쪽): 돌 깐 길 · 석등
        sq.fill(-1, 0, -33, 1, 0, -9, "minecraft:dirt_path|minecraft:polished_andesite|minecraft:dirt_path");
        sq.mark("npc_17", -10.5, 1, -2.5, "east");
        bps.add(sq);
        for (int zz : new int[] {-13, -20, -27}) {
            for (int s : new int[] {-1, 1}) {
                Blueprint l = new Blueprint(x + s * 4, g, z + zz, "south");
                stoneLantern(l);
                bps.add(l);
            }
        }
        // 집: 촌장 고헤이의 집 (서쪽 — 앞이 광장) · 민가 넷
        Blueprint head = FieldTown.front(x - 13, g, z - 3, "east", 11, 13);
        minka(head, 11, 13, 5, null);
        bps.add(head);
        Object[][] houses = {{13, -12, "west", 9, 11}, {13, 4, "west", 9, 11}, {-14, 18, "north", 9, 9}, {14, 18, "north", 9, 11}};
        for (Object[] h : houses) {
            int W = (Integer) h[3];
            int D = (Integer) h[4];
            Blueprint hb = FieldTown.front(x + (Integer) h[0], g, z + (Integer) h[1], (String) h[2], W, D);
            minka(hb, W, D, 4, null);
            bps.add(hb);
        }
        // 곳간 · 우물 · 볏단 걸이 · 가게 둘
        Blueprint kura = new Blueprint(x - 24, g, z - 22, "south");
        granary(kura);
        bps.add(kura);
        Blueprint w = new Blueprint(x + 9, g, z - 4, "south");
        FieldTown.well(w);
        bps.add(w);
        for (int zz : new int[] {24, 28}) {
            Blueprint r = new Blueprint(x - 6, g, z + zz, "south");
            riceRack(r, 13);
            bps.add(r);
        }
        Blueprint s1 = FieldTown.front(x - 6, g, z + 11, "north", 5, 4);
        FieldTown.stall(s1, "minecraft:red_wool", "minecraft:hay_block[axis=y]", "shop_20");
        bps.add(s1);
        Blueprint s2 = FieldTown.front(x + 6, g, z + 11, "north", 5, 4);
        FieldTown.stall(s2, "minecraft:white_wool", "minecraft:barrel[facing=up]", "shop_30");
        bps.add(s2);
        // 이동 지점 · 북쪽 큰 도리이 (사당 길의 시작)
        Blueprint way = new Blueprint(x, g, z + 4, "south");
        FieldTown.waystone(way, "waystone_thunder");
        bps.add(way);
        Blueprint gate = new Blueprint(x, g, z - 31, "south");
        torii(gate, 5, 12, 0, 0, true);
        bps.add(gate);
    }

    // ------------------------------------------------------------------ 벼락받이 사당

    /**
     * 벼락 기둥: 가운데 (0, 0), 받침 (돌 두 단) 위 구리 기둥 3 × 3 (높이 H) · 볏짚 금줄 둘 (아래 줄에 흰 시데 열둘 · 위 줄에 넷) ·
     * 꼭대기 피뢰침 셋 (가운데 · 모서리) · 받침 모서리의 볏섬. 자리표 bolt = 벼락이 떨어지는 곳.
     */
    static void boltPillar(Blueprint b, int H) {
        b.fill(-3, 0, -3, 3, 1, 3, "minecraft:chiseled_stone_bricks|minecraft:polished_andesite|minecraft:polished_andesite");
        b.fill(-2, 2, -2, 2, 2, 2, "minecraft:polished_andesite");
        b.fill(-1, 3, -1, 1, H, 1, COPPER);
        for (int y : new int[] {9, H - 6}) {
            for (int i = -2; i <= 2; i++) {
                b.set(i, y, -2, "minecraft:hay_block[axis=x]");
                b.set(i, y, 2, "minecraft:hay_block[axis=x]");
                if (Math.abs(i) < 2) {
                    b.set(-2, y, i, "minecraft:hay_block[axis=z]");
                    b.set(2, y, i, "minecraft:hay_block[axis=z]");
                }
            }
            int[] at = y == 9 ? new int[] {-2, 0, 2} : new int[] {0};
            for (int i : at) {
                b.set(i, y, 3, "minecraft:white_wall_banner[facing=south]");
                b.set(i, y, -3, "minecraft:white_wall_banner[facing=north]");
                b.set(3, y, i, "minecraft:white_wall_banner[facing=east]");
                b.set(-3, y, i, "minecraft:white_wall_banner[facing=west]");
            }
        }
        b.fill(0, H + 1, 0, 0, H + 3, 0, "minecraft:lightning_rod[facing=up]");
        for (int[] p : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) b.set(p[0], H + 1, p[1], "minecraft:lightning_rod[facing=up]");
        for (int[] p : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) b.set(p[0], 2, p[1], "minecraft:hay_block[axis=y]");
        b.mark("bolt", 0.5, H + 4, 0.5, "south");
    }

    /**
     * 사당 본전: 폭 W (x) · 깊이 D (z) · 벽 높이 H. 돌 기단 (둘레 두 칸 나무 툇마루) · 앞 계단 · 붉은 기둥 · 흰 회벽 · 앞은 트임 (가운데 세 칸 위에 볏짚 금줄과 시데) ·
     * 푸른 구리 지붕 (용마루가 x 로 — 앞뒤 두 칸 · 옆 두 칸 처마) · 용마루 위 가쓰오기 · 처마 밑 방울 · 안 (제단 · 금 · 피뢰침 · 볏섬 · 등불).
     */
    static void shrineHall(Blueprint b, int W, int D, int H, String npc) {
        int c = W / 2;
        int z0 = -2;
        int z1 = D + 1;
        int yb = H + 2;
        int rows = 0;
        while (z0 + rows < z1 - rows) rows++;
        int ridge = yb + rows;
        b.air(-3, 1, -3, W + 2, ridge + 2, D + 3);
        b.fill(-2, -1, -2, W + 1, 0, D + 1, STONE);
        b.fill(-2, 1, -2, W + 1, 1, D + 1, "minecraft:spruce_planks");
        b.fill(c - 2, 1, D + 2, c + 2, 1, D + 2, "minecraft:stone_brick_stairs[facing=north,half=bottom]");
        // 벽 · 기둥
        b.fill(0, 2, 0, W - 1, yb + 1, 0, PLASTER);
        b.fill(0, 2, 0, 0, H + 1, D - 1, PLASTER);
        b.fill(W - 1, 2, 0, W - 1, H + 1, D - 1, PLASTER);
        for (int xx = 0; xx < W; xx += 4) b.fill(xx, 2, 0, xx, H + 1, 0, RED);
        b.fill(W - 1, 2, 0, W - 1, H + 1, 0, RED);
        for (int zz : new int[] {0, D / 2, D - 1}) {
            b.fill(0, 2, zz, 0, H + 1, zz, RED);
            b.fill(W - 1, 2, zz, W - 1, H + 1, zz, RED);
        }
        for (int xx = 0; xx < W; xx++) if (Math.abs(xx - c) > 1 && xx % 2 == 0) b.fill(xx, 2, D - 1, xx, H + 1, D - 1, RED);
        b.fill(0, H + 1, D - 1, W - 1, H + 1, D - 1, RED);
        b.fill(0, yb, D - 1, W - 1, yb + 1, D - 1, PLASTER);
        b.fill(1, 2, 0, W - 2, 2, 0, "minecraft:stripped_dark_oak_log[axis=x]");
        // 옆 창 (격자)
        for (int zz : new int[] {2, D - 3}) {
            b.set(0, 3, zz, "minecraft:spruce_trapdoor[facing=west,half=bottom,open=true]");
            b.set(W - 1, 3, zz, "minecraft:spruce_trapdoor[facing=east,half=bottom,open=true]");
        }
        // 지붕 (앞뒤로 내려감)
        for (int k = 0; k < rows; k++) {
            b.fill(-2, yb + k, z1 - k, W + 1, yb + k, z1 - k, COPPER_ROOF + "[facing=north,half=bottom]");
            b.fill(-2, yb + k, z0 + k, W + 1, yb + k, z0 + k, COPPER_ROOF + "[facing=south,half=bottom]");
            int za = Math.max(0, z0 + k + 1);
            int zb = Math.min(D - 1, z1 - k - 1);
            if (za <= zb) {
                b.fill(0, yb + k, za, 0, yb + k, zb, PLASTER);
                b.fill(W - 1, yb + k, za, W - 1, yb + k, zb, PLASTER);
            }
        }
        b.fill(-2, ridge, z0 + rows, W + 1, ridge, z0 + rows, "minecraft:waxed_oxidized_cut_copper");
        for (int xx = 0; xx < W; xx += 3) b.set(xx, ridge + 1, z0 + rows, "minecraft:dark_oak_log[axis=z]");
        for (int xx : new int[] {-2, W + 1}) b.set(xx, ridge + 1, z0 + rows, "minecraft:dark_oak_fence");
        // 앞: 볏짚 금줄 (가운데 트인 곳 위) · 시데 · 방울 · 기단 앞 등불
        b.fill(c - 1, H, D - 1, c + 1, H, D - 1, "minecraft:hay_block[axis=x]");
        for (int xx = c - 1; xx <= c + 1; xx += 2) b.set(xx, H, D, "minecraft:white_wall_banner[facing=south]");
        b.set(c, H + 1, D + 1, "minecraft:bell[attachment=ceiling,facing=south]");
        for (int s : new int[] {-1, 1}) {
            b.set(c + s * 3, 1, D + 2, "minecraft:dark_oak_fence");
            b.set(c + s * 3, 2, D + 2, "minecraft:lantern[hanging=false]");
        }
        // 안: 제단 (뒤 가운데)
        b.fill(c - 3, 2, 1, c + 3, 2, 2, "minecraft:dark_oak_planks");
        b.set(c, 3, 1, GOLD);
        b.set(c, 4, 1, "minecraft:lightning_rod[facing=up]");
        for (int s : new int[] {-1, 1}) {
            b.set(c + s * 2, 3, 1, "minecraft:lantern[hanging=false]");
            b.set(c + s * 3, 3, 1, "minecraft:hay_block[axis=y]");
            b.set(c + s, 3, 1, "minecraft:white_wall_banner[facing=south]");
        }
        b.fill(c - 1, 1, 3, c + 1, 1, D - 1, "minecraft:red_wool");
        for (int zz = 3; zz < D - 1; zz += 3) {
            int under = yb + Math.min(zz - z0, z1 - zz) - 1;
            for (int xx : new int[] {2, W - 3}) {
                b.set(xx, yb + 1, zz, "minecraft:lantern[hanging=true]");
                if (under >= yb + 2) b.fill(xx, yb + 2, zz, xx, under, zz, "minecraft:iron_chain[axis=y]");
            }
        }
        if (npc != null) b.mark(npc, c + 0.5, 2, 4.5, "south");
    }

    /**
     * 벼락받이 사당 (번개 나라 섬 산꼭대기 — 상징 건물 자리 가운데 (x, z), 바닥 g): 반지름 20 자갈 마당 (돌 참배길 · 붉은 울타리 — 남쪽 문) · 큰 도리이 (남쪽) ·
     * 석등 · 벼락 기둥 (가운데 남쪽) · 본전 (북쪽 — 번개 왕 npc_19) · 사당지기 히비키 (npc_18 — 기둥 앞) · 벚나무 넷 · 사당 길의 도리이 ({@link #toriiPath}).
     */
    static void shrine(List<Blueprint> bps, FieldTerrain t, int x, int g, int z) {
        Blueprint p = new Blueprint(x, g, z, "south");
        int R = 20;
        for (int dz = -R - 1; dz <= R + 1; dz++) {
            int xr = (int) Math.floor(Math.sqrt(Math.max(0, (R + 0.4) * (R + 0.4) - dz * dz)));
            if (Math.abs(dz) > R) continue;
            p.fill(-xr, -1, dz, xr, -1, dz, "minecraft:stone_bricks");
            p.fill(-xr, 0, dz, xr, 0, dz, "minecraft:gravel|minecraft:gravel|minecraft:gravel|minecraft:andesite");
            p.air(-xr, 1, dz, xr, 16, dz);
            // 둘레: 돌 테 · 붉은 울타리 (남쪽 문 — 가운데 다섯 칸)
            for (int s : new int[] {-1, 1}) {
                int ex = s * xr;
                p.set(ex, 0, dz, "minecraft:stone_bricks");
                if (!(dz > 0 && Math.abs(ex) <= 3)) p.set(ex, 1, dz, "minecraft:mangrove_fence");
            }
        }
        for (int dx = -R; dx <= R; dx++) {
            int zr = (int) Math.floor(Math.sqrt(Math.max(0, (R + 0.4) * (R + 0.4) - dx * dx)));
            for (int s : new int[] {-1, 1}) {
                p.set(dx, 0, s * zr, "minecraft:stone_bricks");
                if (!(s > 0 && Math.abs(dx) <= 2)) p.set(dx, 1, s * zr, "minecraft:mangrove_fence");
                else p.set(dx, 1, s * zr, "minecraft:air");
            }
        }
        // 참배길 (남쪽 문 → 기둥 앞 · 기둥 뒤 → 본전 계단)
        p.fill(-1, 0, 9, 1, 0, R, "minecraft:polished_andesite|minecraft:andesite|minecraft:polished_andesite");
        p.fill(-1, 0, -3, 1, 0, 1, "minecraft:polished_andesite|minecraft:andesite|minecraft:polished_andesite");
        p.mark("npc_18", 2.5, 1, 10.5, "south");
        bps.add(p);
        // 벚나무 넷 · 석등 여섯
        int[][] trees = {{-14, -3}, {14, -3}, {-11, 12}, {11, 13}};
        for (int i = 0; i < trees.length; i++) {
            Blueprint tb = new Blueprint(x + trees[i][0], g, z + trees[i][1], "south");
            cherry(tb, 5 + i % 2, i + 3);
            bps.add(tb);
        }
        int[][] lamps = {{-3, 18}, {3, 18}, {-3, 11}, {3, 11}, {-10, -6}, {10, -6}};
        for (int[] l : lamps) {
            Blueprint lb = new Blueprint(x + l[0], g, z + l[1], "south");
            stoneLantern(lb);
            bps.add(lb);
        }
        // 벼락 기둥 · 본전 · 큰 도리이
        Blueprint pillar = new Blueprint(x, g, z + 5, "south");
        boltPillar(pillar, 26);
        bps.add(pillar);
        Blueprint hall = FieldTown.front(x, g, z - 6, "south", 13, 9);
        shrineHall(hall, 13, 9, 5, "npc_19");
        bps.add(hall);
        Blueprint gate = new Blueprint(x, g, z + 16, "south");
        torii(gate, 5, 12, 0, 0, true);
        bps.add(gate);
        toriiPath(bps, t, "thunder_shrine", x, z, 24, 90);
    }

    /**
     * 상징 건물로 오르는 길의 도리이 (FieldTerrain.roadTo): 건물 가운데에서 from 칸 밖부터 길 가운데 줄을 따라 내려가며, 길이 dense 칸까지는 두 칸마다 (붉은 굴 ·
     * 네 번째마다 등불), 그 아래는 여덟 칸마다, 거점 (길의 시작) 자리 둘레 40칸 앞까지. 도리이마다 그 칸의 땅 높이에 서고 기둥은 저마다의 땅까지 내려감.
     * 길이 남북이면 도리이가 동서로 걸침 (동서 길이면 남북으로).
     */
    static void toriiPath(List<Blueprint> bps, FieldTerrain t, String siteId, double sx, double sz, double from, double dense) {
        List<FieldTerrain.Segment> segs = t.roadTo(siteId);
        if (segs.isEmpty()) return;
        // 건물 쪽 끝에서 거꾸로 (점 목록)
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[] {segs.get(segs.size() - 1).bx(), segs.get(segs.size() - 1).bz()});
        for (int i = segs.size() - 1; i >= 0; i--) pts.add(new double[] {segs.get(i).ax(), segs.get(i).az()});
        double[] hub = pts.get(pts.size() - 1);
        FieldTerrain.Column c = new FieldTerrain.Column();
        double s = 0;
        double next = 0;
        int n = 0;
        int half = 3;
        for (int i = 0; i + 1 < pts.size(); i++) {
            double ax = pts.get(i)[0];
            double az = pts.get(i)[1];
            double bx = pts.get(i + 1)[0];
            double bz = pts.get(i + 1)[1];
            double len = Math.hypot(bx - ax, bz - az);
            boolean alongZ = Math.abs(bz - az) >= Math.abs(bx - ax);
            while (next <= s + len) {
                double f = (next - s) / Math.max(1e-6, len);
                double px = ax + (bx - ax) * f;
                double pz = az + (bz - az) * f;
                double step = next < dense ? 2 : 8;
                next += step;
                if (Math.hypot(px - sx, pz - sz) < from) continue;
                if (Math.hypot(px - hub[0], pz - hub[1]) < 40) return;
                int cx = (int) Math.floor(px);
                int cz = (int) Math.floor(pz);
                int gc = t.sample(cx, cz, c).ground;
                int ox = alongZ ? half : 0;
                int oz = alongZ ? 0 : half;
                int gl = t.sample(cx - ox, cz - oz, c).ground - gc;
                int gr = t.sample(cx + ox, cz + oz, c).ground - gc;
                Blueprint b = new Blueprint(cx, gc, cz, alongZ ? "south" : "west");
                // 설계 x 가 걸침 — 남북 길은 그대로 (설계 −x = 서쪽), 동서 길은 서쪽 (west) 으로 돌림 (설계 −x = 북쪽): 어느 쪽이든 baseL = 앞 표본 (gl)
                torii(b, half, 8, gl, gr, false);
                if (step == 2 && n % 4 == 0) {
                    b.set(-half - 1, 6, 0, "minecraft:lantern[hanging=true]");
                    b.set(half + 1, 6, 0, "minecraft:lantern[hanging=true]");
                }
                n++;
                bps.add(b);
            }
            s += len;
        }
    }

    // ------------------------------------------------------------------ 큰 저울 (바위 나라)

    /** 받침점 탑 둘레 마루 높이 · 저울대 높이 (탑 바닥에서) · 저울대 반 길이 · 접시가 땅에서 뜬 높이 · 접시 반지름 · 걸개 반 너비 */
    static final int DECK = 52;
    static final int BEAM = 56;
    static final int ARM = 80;
    static final int PAN_UP = 8;
    static final int PAN_R = 15;
    static final int YOKE = 7;

    /** 팔각 (|x| · |z| ≤ r, |x| + |z| ≤ r + r/2) */
    static boolean oct(int x, int z, int r) {
        return Math.abs(x) <= r && Math.abs(z) <= r && Math.abs(x) + Math.abs(z) <= r + r / 2;
    }

    /** 팔각 한 층 (속을 비우려면 inner ≥ 0 — 그 안은 건드리지 않음) */
    static void octLayer(Blueprint b, int r, int inner, int y0, int y1, String block) {
        for (int z = -r; z <= r; z++) {
            int a = Integer.MIN_VALUE;
            for (int x = -r; x <= r + 1; x++) {
                boolean in = x <= r && oct(x, z, r) && !(inner >= 0 && oct(x, z, inner));
                if (in && a == Integer.MIN_VALUE) a = x;
                if (!in && a != Integer.MIN_VALUE) {
                    b.fill(a, y0, z, x - 1, y1, z, block);
                    a = Integer.MIN_VALUE;
                }
            }
        }
    }

    /**
     * 받침점 탑 (가운데 (0, 0), 바닥 0): 현자 회의장 (팔각 반지름 13 · 높이 9 — 동서남북 아치 문 · 타일 띠 · 성가퀴 · 모서리 첨탑 넷 · 안: 여덟 갈래 별 무늬 바닥 ·
     * 둥근 회의 자리 · 의장 오르도 npc_21) · 탑 몸 (팔각 4 — 10칸마다 타일 띠) · 꼭대기 둘레 마루 (팔각 7 · 금 난간) · 받침 (금) · 금 바늘 · 눈금판 (청금석 반원 · 금 눈금) ·
     * 오르내림 발판 둘 (회의장 남쪽 · 마루 남쪽).
     */
    static void fulcrum(Blueprint b) {
        int R = 13;
        int H = 9;
        b.air(-R - 1, 1, -R - 1, R + 1, H + 8, R + 1);
        octLayer(b, R, -1, -2, 0, SAND);
        octLayer(b, R, R - 1, 1, H, SAND);
        octLayer(b, R, R - 1, 7, 8, TILE);
        octLayer(b, R, R - 1, H, H, "minecraft:chiseled_sandstone");
        octLayer(b, R - 1, -1, H, H, "minecraft:smooth_sandstone");
        octLayer(b, R, R - 1, H + 1, H + 1, "minecraft:sandstone_wall");
        // 바닥 무늬: 여덟 갈래 별 (청금석) · 가운데 금 고리
        for (int x = -R + 1; x <= R - 1; x++) {
            for (int z = -R + 1; z <= R - 1; z++) {
                if (!oct(x, z, R - 1)) continue;
                double r = Math.hypot(x, z);
                boolean ray = (x == 0 || z == 0 || Math.abs(x) == Math.abs(z)) && r > 5.5 && r < 11;
                if (r >= 5 && r < 6) b.set(x, 0, z, GOLD);
                else if (ray) b.set(x, 0, z, LAPIS);
            }
        }
        // 문 넷 (너비 5 · 높이 6 — 뾰족 아치) · 문틀 청금석
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            for (int w = -2; w <= 2; w++) {
                int top = Math.abs(w) == 2 ? 4 : Math.abs(w) == 1 ? 5 : 6;
                for (int depth = R - 1; depth <= R; depth++) {
                    int xx = d[0] != 0 ? d[0] * depth : w;
                    int zz = d[1] != 0 ? d[1] * depth : w;
                    b.air(xx, 1, zz, xx, top, zz);
                    b.set(xx, top + 1, zz, LAPIS);
                }
            }
            for (int w : new int[] {-3, 3}) {
                for (int depth = R - 1; depth <= R; depth++) {
                    int xx = d[0] != 0 ? d[0] * depth : w;
                    int zz = d[1] != 0 ? d[1] * depth : w;
                    b.fill(xx, 1, zz, xx, 7, zz, LAPIS);
                }
            }
        }
        // 모서리 첨탑 넷 (팔각의 비스듬한 면 가운데 · 높이 16 · 금 꼭지)
        int m = (R + R / 2) / 2;
        for (int[] q : new int[][] {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
            int mx = q[0] * m;
            int mz = q[1] * m;
            b.fill(mx - 1, 1, mz - 1, mx + 1, 15, mz + 1, "minecraft:cut_sandstone");
            b.fill(mx - 1, 12, mz - 1, mx + 1, 13, mz + 1, TILE);
            b.set(mx, 16, mz, GOLD);
            b.set(mx, 17, mz, "minecraft:end_rod[facing=up]");
        }
        // 둥근 회의 자리 (반지름 9 — 가운데를 봄) · 등불
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            int sx = (int) Math.round(Math.cos(a) * 9);
            int sz = (int) Math.round(Math.sin(a) * 9);
            if (Math.abs(sx) <= 2 || Math.abs(sz) <= 2) continue;
            String face = Math.abs(sx) >= Math.abs(sz) ? (sx > 0 ? "east" : "west") : (sz > 0 ? "south" : "north");
            b.set(sx, 1, sz, "minecraft:sandstone_stairs[facing=" + face + ",half=bottom]");
        }
        for (int[] q : new int[][] {{7, 7}, {-7, 7}, {7, -7}, {-7, -7}}) {
            b.set(q[0], 1, q[1], "minecraft:cut_sandstone");
            b.set(q[0], 2, q[1], "minecraft:lantern[hanging=false]");
        }
        // 탑 몸 (회의장을 지나 마루까지) · 띠
        int r = 4;
        octLayer(b, r, -1, 1, DECK - 1, SAND);
        for (int y = 12; y < DECK - 2; y += 10) octLayer(b, r, -1, y, y + 1, TILE);
        for (int y = 17; y < DECK - 2; y += 10) octLayer(b, r, -1, y, y, LAPIS);
        // 꼭대기 둘레 마루 (팔각 7) · 받침 (금 — 저울대가 얹힘)
        octLayer(b, 7, -1, DECK - 1, DECK, "minecraft:smooth_sandstone");
        octLayer(b, 7, 6, DECK - 2, DECK - 2, "minecraft:cut_sandstone");
        octLayer(b, 7, 6, DECK + 1, DECK + 1, GOLD);
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) if (oct(x, z, 7) && !oct(x, z, 6) && Math.floorMod(x + z, 2) == 1) b.set(x, DECK + 1, z, "minecraft:smooth_sandstone_slab[type=bottom]");
        b.fill(-1, DECK + 1, -1, 1, BEAM - 1, 1, GOLD);
        b.fill(-2, DECK + 1, -1, 2, DECK + 1, 1, LAPIS);
        // 오르내림 발판 (회의장 남쪽 · 마루 남쪽)
        pad(b, 0, 0, 7);
        b.mark("lift_scale_a", 0.5, 1, 7.5, "south");
        pad(b, 0, DECK, 5);
        b.mark("lift_scale_b", 0.5, DECK + 1, 5.5, "south");
        // 의장 오르도 (서쪽 문 안 — 길이 서쪽 접시에서 옴)
        b.mark("npc_21", -8.5, 1, 0.5, "east");
    }

    /** 오르내림 발판: (x, y, z) 에 청금석 3 × 3 · 가운데 금 · 금 누름판 · 둘레 끝막대 넷 */
    static void pad(Blueprint b, int x, int y, int z) {
        b.fill(x - 1, y, z - 1, x + 1, y, z + 1, LAPIS);
        b.set(x, y, z, GOLD);
        b.set(x, y + 1, z, "minecraft:light_weighted_pressure_plate");
        for (int[] q : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) b.set(x + q[0], y + 1, z + q[1], "minecraft:end_rod[facing=up]");
    }

    /** 금 바늘 (저울대 가운데 위로 14칸) · 눈금판 (바늘 뒤 반원 — 청금석 · 15° 마다 금 눈금) */
    static void needle(Blueprint b) {
        b.fill(0, BEAM + 2, 0, 0, BEAM + 14, 0, GOLD);
        b.set(0, BEAM + 15, 0, "minecraft:end_rod[facing=up]");
        int cy = BEAM + 3;
        for (int deg = 20; deg <= 160; deg += 2) {
            double a = Math.toRadians(deg);
            int x = (int) Math.round(Math.cos(a) * 11);
            int y = cy + (int) Math.round(Math.sin(a) * 11);
            b.set(x, y, -1, deg % 15 == 0 || deg == 90 ? GOLD : LAPIS);
        }
    }

    /**
     * 저울대 (가운데 (0, 0) · 탑 바닥 높이 0): x −ARM ~ ARM, 높이 BEAM ~ BEAM+1 · 폭 3 (아래 가운데 줄 청금석 · 위 8칸마다 금 못), 가운데로 갈수록 두꺼움
     * (36칸 안 세 층 · 14칸 안 네 층) · 16칸마다 금 띠 · 끝마다 금 머리 · 걸개 (끝 아래 — 네모 테와 열십자 · 금 모서리).
     */
    static void beam(Blueprint b) {
        b.fill(-ARM, BEAM, -1, ARM, BEAM, 1, "minecraft:cut_sandstone");
        b.fill(-ARM, BEAM, 0, ARM, BEAM, 0, LAPIS);
        b.fill(-ARM, BEAM + 1, -1, ARM, BEAM + 1, 1, "minecraft:smooth_sandstone");
        b.fill(-36, BEAM - 1, -1, 36, BEAM - 1, 1, "minecraft:cut_sandstone");
        b.fill(-36, BEAM - 1, 0, 36, BEAM - 1, 0, LAPIS);
        for (int x = -ARM + 16; x <= ARM - 16; x += 16) b.fill(x, Math.abs(x) <= 36 ? BEAM - 1 : BEAM, -1, x, BEAM + 1, 1, GOLD);
        b.fill(-7, BEAM - 1, -1, 7, BEAM + 2, 1, "minecraft:smooth_sandstone");
        b.fill(-7, BEAM + 2, 0, 7, BEAM + 2, 0, GOLD);
        for (int x = -ARM + 8; x <= ARM - 8; x += 8) if (Math.abs(x) > 8) b.set(x, BEAM + 2, 0, GOLD);
        for (int s : new int[] {-1, 1}) {
            int ex = s * ARM;
            b.fill(ex - 1, BEAM - 1, -1, ex + 1, BEAM + 2, 1, GOLD);
            b.set(ex, BEAM - 2, 0, GOLD);
            int y = BEAM - 3;
            b.fill(ex - YOKE, y, -YOKE, ex + YOKE, y, -YOKE, "minecraft:cut_sandstone");
            b.fill(ex - YOKE, y, YOKE, ex + YOKE, y, YOKE, "minecraft:cut_sandstone");
            b.fill(ex - YOKE, y, -YOKE, ex - YOKE, y, YOKE, "minecraft:cut_sandstone");
            b.fill(ex + YOKE, y, -YOKE, ex + YOKE, y, YOKE, "minecraft:cut_sandstone");
            b.fill(ex - YOKE, y, 0, ex + YOKE, y, 0, "minecraft:smooth_sandstone");
            b.fill(ex, y, -YOKE, ex, y, YOKE, "minecraft:smooth_sandstone");
            for (int[] q : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) b.set(ex + q[0] * YOKE, y, q[1] * YOKE, GOLD);
        }
    }

    /** 걸개 모서리에서 접시까지 사슬 넷 (세계 칸 — 접시 가운데 (px, pz) · 접시 윗면 top · 걸개 높이 yokeY) */
    static void chains(Blueprint b, int px, int pz, int top, int yokeY) {
        for (int[] q : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) b.fill(px + q[0] * YOKE, top + 1, pz + q[1] * YOKE, px + q[0] * YOKE, yokeY - 1, pz + q[1] * YOKE, "minecraft:iron_chain[axis=y]");
    }

    /**
     * 큰 저울 (바위 나라 섬 가운데 언덕 — 상징 건물 자리 (x, z), 바닥 g): 받침점 탑 · 저울대 · 걸개 · 사슬 (두 접시 — sage_west · sage_east 자리 — 까지).
     * 접시는 거점 자리라 {@link #pan} 이 짓고, 여기서는 사슬만 접시 윗면까지 내림.
     */
    static void scale(List<Blueprint> bps, FieldTerrain t, int x, int g, int z) {
        Blueprint tower = new Blueprint(x, g, z, "south");
        fulcrum(tower);
        needle(tower);
        bps.add(tower);
        Blueprint bm = new Blueprint(x, g, z, "south");
        beam(bm);
        bps.add(bm);
        for (String id : new String[] {"sage_west", "sage_east"}) {
            FieldPlan.Site s = t.plan().site(id);
            if (s == null) continue;
            int py = FieldCamps.plotY(t, id, g - 15) + PAN_UP;
            Blueprint ch = new Blueprint(0, 0, 0, "south");
            chains(ch, (int) Math.round(s.x()), (int) Math.round(s.z()), py, g + BEAM - 3);
            bps.add(ch);
        }
    }

    /**
     * 저울 접시 (거점 — 가운데 (x, z), 땅 g): 윗면 = 땅 + PAN_UP, 반지름 PAN_R 사암 접시 (아래가 둥글게 두꺼움 · 금 테 · 청금석 고리 · 여덟 갈래 무늬 ·
     * 낮은 난간), 사슬 닿는 금 고리쇠 넷, 탑 쪽으로 내려가는 계단 (너비 3). west = 서쪽 접시 (이동 지점 · 세베린의 정자 npc_20),
     * 아니면 동쪽 접시 (리브라 npc_22 · 작은 저울 · 가게 shop_40).
     */
    static void pan(List<Blueprint> bps, int x, int g, int z, boolean west) {
        int top = g + PAN_UP;
        // 서쪽 접시는 설계 그대로 (계단이 +x = 동쪽 탑 쪽), 동쪽 접시는 반 바퀴 (계단이 서쪽)
        Blueprint b = new Blueprint(x, top, z, west ? "south" : "north");
        int R = PAN_R;
        b.air(-R - 1, -PAN_UP + 1, -R - 1, R + 7, 12, R + 1);
        for (int dz = -R; dz <= R; dz++) {
            for (int dx = -R; dx <= R; dx++) {
                double r = Math.hypot(dx, dz);
                if (r > R + 0.4) continue;
                int depth = 1 + (int) Math.round(3 * (1 - (r / R) * (r / R)));
                b.fill(dx, -depth, dz, dx, -1, dz, r >= R - 1 ? GOLD : "minecraft:cut_sandstone|minecraft:smooth_sandstone");
                String face;
                if (r >= R - 0.6) face = GOLD;
                else if (r >= R - 2 && r < R - 1) face = LAPIS;
                else if (r >= 6 && r < 7) face = LAPIS;
                else if ((dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz)) && r > 7 && r < R - 2) face = "minecraft:chiseled_sandstone";
                else face = "minecraft:smooth_sandstone";
                b.set(dx, 0, dz, face);
                if (r >= R - 0.6 && !(dx > 0 && Math.abs(dz) <= 1)) b.set(dx, 1, dz, "minecraft:sandstone_wall");
            }
        }
        for (int[] q : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) b.set(q[0] * YOKE, 0, q[1] * YOKE, GOLD);
        // 계단 (+x 쪽 — 땅까지)
        for (int k = 1; k <= PAN_UP - 1; k++) {
            b.fill(R + k, -PAN_UP + 1, -1, R + k, -k - 1, 1, "minecraft:cut_sandstone");
            b.fill(R + k, -k, -1, R + k, -k, 1, "minecraft:sandstone_stairs[facing=west,half=bottom]");
        }
        b.air(R, 1, -1, R + PAN_UP, 4, 1);
        bps.add(b);
        if (west) {
            Blueprint way = new Blueprint(x, top, z, "south");
            FieldTown.waystone(way, "waystone_sage");
            bps.add(way);
            Blueprint kiosk = new Blueprint(x - 9, top, z, "south");
            pavilion(kiosk);
            bps.add(kiosk);
            Blueprint m = new Blueprint(x, top, z, "south");
            m.mark("npc_20", -4.5, 1, 0.5, "east");
            bps.add(m);
        } else {
            Blueprint kiosk = new Blueprint(x + 9, top, z, "south");
            pavilion(kiosk);
            bps.add(kiosk);
            Blueprint ms = new Blueprint(x + 5, top, z - 4, "south");
            miniScale(ms);
            bps.add(ms);
            Blueprint m = new Blueprint(x, top, z, "south");
            m.mark("npc_22", 4.5, 1, -1.5, "west");
            bps.add(m);
            Blueprint st = FieldTown.front(x + 4, top, z + 6, "north", 5, 4);
            FieldTown.stall(st, "minecraft:blue_wool", LAPIS, "shop_40");
            bps.add(st);
        }
    }

    /** 정자 (가운데 (0, 0) · 5 × 5): 기둥 넷 · 타일 띠 · 푸른 타일 둥근 지붕 · 금 꼭지 */
    static void pavilion(Blueprint b) {
        b.air(-3, 1, -3, 3, 10, 3);
        for (int[] q : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) b.fill(q[0], 1, q[1], q[0], 4, q[1], "minecraft:cut_sandstone");
        b.fill(-2, 5, -2, 2, 5, 2, "minecraft:smooth_sandstone");
        b.fill(-1, 5, -1, 1, 5, 1, "minecraft:air");
        for (int y = 0; y <= 3; y++) {
            int rr = y == 0 ? 3 : y == 1 ? 3 : y == 2 ? 2 : 1;
            for (int dz = -rr; dz <= rr; dz++) {
                int xr = (int) Math.floor(Math.sqrt(Math.max(0, rr * rr + 0.5 - dz * dz)));
                b.fill(-xr, 6 + y, dz, xr, 6 + y, dz, TILE);
            }
        }
        b.fill(-1, 6, -1, 1, 7, 1, "minecraft:air");
        b.set(0, 8, 0, "minecraft:lantern[hanging=true]");
        b.set(0, 10, 0, GOLD);
        b.set(0, 11, 0, "minecraft:end_rod[facing=up]");
        b.fill(-1, 0, -1, 1, 0, 1, LAPIS);
        b.set(0, 0, 0, GOLD);
    }

    /** 작은 저울 (리브라의 탁자): 금 받침 · 기둥 · 가로대 · 사슬 · 가마솥 접시 둘 */
    static void miniScale(Blueprint b) {
        b.air(-2, 1, 0, 2, 4, 0);
        b.set(0, 1, 0, GOLD);
        b.set(0, 2, 0, "minecraft:polished_blackstone_wall");
        b.set(0, 3, 0, "minecraft:polished_blackstone_wall");
        b.fill(-2, 4, 0, 2, 4, 0, "minecraft:smooth_sandstone_slab[type=bottom]");
        for (int s : new int[] {-2, 2}) {
            b.set(s, 3, 0, "minecraft:iron_chain[axis=y]");
            b.set(s, 2, 0, "minecraft:cauldron");
        }
    }
}
