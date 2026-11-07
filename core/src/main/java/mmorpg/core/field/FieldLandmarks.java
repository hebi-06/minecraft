package mmorpg.core.field;

/**
 * 마을의 큰 건물 (2026-11-07 — 사용자 "성당, 마탑, 여신상, 커다란 분수" · "모험가 길드 · 장터 · 대장장이 무기소"):
 * 큰 분수 · 여신상 · 성당 · 마탑 · 모험가 길드 · 대장장이 무기소 · 광장 의자 · 화단. 모두 설계 방향은 앞 (문) 이 남쪽.
 */
final class FieldLandmarks {
    private FieldLandmarks() {}

    static final String QB = "minecraft:quartz_bricks";
    static final String SQ = "minecraft:smooth_quartz";
    static final String GOLD = "minecraft:gold_block";
    static final String STONE = "minecraft:stone_bricks|minecraft:stone_bricks|minecraft:stone_bricks|minecraft:mossy_stone_bricks|minecraft:cracked_stone_bricks";

    // ------------------------------------------------------------------ 큰 분수

    /**
     * 큰 분수: 가운데 (0, 0), 바닥 높이 0. 큰 물그릇 (반지름 10, 석영 테 · 어두운 바닥에 바다 랜턴) · 가운데 그릇 둘 (반지름 4 · 2.5) ·
     * 꼭대기 (금 · 바다 랜턴 · 끝막대) · 그릇마다 넷으로 넘쳐흐르는 물줄기.
     */
    static void grandFountain(Blueprint b) {
        for (int x = -11; x <= 11; x++) {
            for (int z = -11; z <= 11; z++) {
                double r = Math.hypot(x, z);
                if (r > 10.6) continue;
                b.air(x, 1, z, x, 15, z);
                if (r >= 9.5) {
                    b.fill(x, -3, z, x, 0, z, QB);
                    b.set(x, 1, z, "minecraft:smooth_quartz_slab[type=bottom]");
                    continue;
                }
                boolean lamp = r > 5.5 && Math.floorMod(x * 7 + z * 13, 5) == 0;
                b.set(x, -3, z, "minecraft:stone_bricks");
                b.set(x, -2, z, lamp ? "minecraft:sea_lantern" : "minecraft:dark_prismarine");
                b.fill(x, -1, z, x, 0, z, "minecraft:water");
                if (r <= 2.4) b.fill(x, -2, z, x, 2, z, SQ);
                if (r <= 4.4) {
                    b.set(x, 3, z, SQ);
                    b.set(x, 4, z, r >= 3.5 ? QB : "minecraft:water");
                }
                if (r <= 2.6) {
                    b.set(x, 7, z, SQ);
                    b.set(x, 8, z, r >= 1.6 ? QB : "minecraft:water");
                }
                if (r <= 1.0) b.fill(x, 3, z, x, 6, z, "minecraft:quartz_pillar[axis=y]");
            }
        }
        b.set(0, 0, 0, "minecraft:chiseled_quartz_block");
        b.fill(0, 8, 0, 0, 11, 0, "minecraft:quartz_pillar[axis=y]");
        b.set(0, 12, 0, GOLD);
        b.set(0, 13, 0, "minecraft:sea_lantern");
        b.set(0, 14, 0, "minecraft:end_rod[facing=up]");
        // 넘쳐흐르는 물줄기: 위 그릇 → 가운데 그릇, 가운데 그릇 → 큰 물그릇 (테의 홈 · 흐르는 물 · 떨어지는 물)
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            b.set(d[0] * 2, 8, d[1] * 2, "minecraft:water");
            b.set(d[0] * 3, 8, d[1] * 3, "minecraft:water[level=1]");
            b.fill(d[0] * 3, 5, d[1] * 3, d[0] * 3, 7, d[1] * 3, "minecraft:water[level=8]");
            b.set(d[0] * 4, 4, d[1] * 4, "minecraft:water");
            b.set(d[0] * 5, 4, d[1] * 5, "minecraft:water[level=1]");
            b.fill(d[0] * 5, 1, d[1] * 5, d[0] * 5, 3, d[1] * 5, "minecraft:water[level=8]");
        }
    }

    /** 광장 의자: 앞 (앉은 사람이 보는 쪽) 이 북쪽 — 계단 셋 · 팔걸이 */
    static void bench(Blueprint b) {
        b.air(-2, 1, 0, 2, 2, 0);
        b.fill(-1, 1, 0, 1, 1, 0, "minecraft:dark_oak_stairs[facing=south,half=bottom]");
        b.set(-2, 1, 0, "minecraft:dark_oak_trapdoor[facing=west,half=bottom,open=true]");
        b.set(2, 1, 0, "minecraft:dark_oak_trapdoor[facing=east,half=bottom,open=true]");
    }

    /** 화단 나무: 3 × 3 돌 테 (한 칸 높이) · 흙 · 작은 나무 (kind 0 참나무 · 1 자작나무) */
    static void planter(Blueprint b, int kind) {
        String log = kind == 1 ? "minecraft:birch_log[axis=y]" : "minecraft:oak_log[axis=y]";
        String leaf = (kind == 1 ? "minecraft:birch_leaves" : "minecraft:oak_leaves") + "[persistent=true]";
        b.air(-2, 1, -2, 2, 9, 2);
        b.fill(-1, 0, -1, 1, 0, 1, "minecraft:polished_andesite");
        b.fill(-1, 1, -1, 1, 1, 1, "minecraft:stone_brick_wall");
        b.set(0, 1, 0, "minecraft:grass_block");
        for (int y = 5; y <= 7; y++) {
            int r = y == 7 ? 1 : 2;
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) if (x * x + z * z <= r * r + 1) b.set(x, y, z, leaf);
        }
        b.set(0, 8, 0, leaf);
        b.fill(0, 2, 0, 0, 6, 0, log);
    }

    // ------------------------------------------------------------------ 여신상

    /**
     * 여신상: 받침 (11 × 11 → 7 × 7, 높이 7) 위에 날개 달린 여신 (높이 약 26). 오른손은 높이 든 빛 (횃불 — 버섯불 · 끝막대), 왼손은 둥근 방패.
     * 대리석 (매끈한 석영 · 석영 벽돌 · 방해석) · 금 (띠 · 관 · 방패 가운데 · 횃불 자루). 앞 = 남쪽.
     */
    static void goddess(Blueprint b) {
        b.air(-12, 1, -8, 12, 36, 7);
        b.fill(-5, -2, -5, 5, 0, 5, "minecraft:polished_andesite");
        b.fill(-4, 1, -4, 4, 1, 4, "minecraft:stone_bricks");
        b.fill(-3, 2, -3, 3, 6, 3, QB);
        for (int[] p : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) b.fill(p[0], 2, p[1], p[0], 6, p[1], "minecraft:quartz_pillar[axis=y]");
        b.fill(-3, 7, -3, 3, 7, 3, SQ);
        b.fill(-1, 3, 3, 1, 5, 3, "minecraft:chiseled_quartz_block");
        b.set(0, 4, 3, GOLD);
        for (int[] p : new int[][] {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) b.set(p[0], 2, p[1], "minecraft:lantern[hanging=false]");
        int y0 = 8;
        for (int x = -11; x <= 11; x++) {
            for (int y = 0; y <= 27; y++) {
                for (int z = -7; z <= 5; z++) {
                    String m = goddessCell(x, y, z);
                    if (m != null) b.set(x, y0 + y, z, m);
                }
            }
        }
    }

    private static double sq(double v) {
        return v * v;
    }

    /** 선분 (a → b) 까지 거리 */
    private static double seg(double x, double y, double z, double ax, double ay, double az, double bx, double by, double bz) {
        double vx = bx - ax;
        double vy = by - ay;
        double vz = bz - az;
        double t = ((x - ax) * vx + (y - ay) * vy + (z - az) * vz) / (vx * vx + vy * vy + vz * vz);
        t = Math.max(0, Math.min(1, t));
        return Math.sqrt(sq(x - ax - vx * t) + sq(y - ay - vy * t) + sq(z - az - vz * t));
    }

    /** 여신 몸의 칸 (x, y, z — 발밑이 y 0, 앞이 +z) 의 블록 (없으면 null) */
    static String goddessCell(int ix, int iy, int iz) {
        double x = ix;
        double y = iy + 0.5;
        double z = iz;
        // 횃불 (오른손 위): 자루 · 불
        if (Math.abs(x + 4) < 0.5 && Math.abs(z) < 0.5) {
            if (y >= 21.5 && y < 23) return GOLD;
            if (y >= 23 && y < 24) return "minecraft:shroomlight";
            if (y >= 24 && y < 25) return "minecraft:end_rod[facing=up]";
        }
        // 오른팔 (들어 올림)
        if (seg(x, y, z, -2.3, 14.8, 0, -4.0, 21.2, 0) <= 0.75) return SQ;
        // 왼팔 (내림, 방패를 듦)
        if (seg(x, y, z, 2.3, 14.8, 0, 3.2, 10.8, 0.8) <= 0.7) return SQ;
        // 방패 (왼쪽 옆 원판)
        if (x >= 3.5 && x < 4.5) {
            double d = Math.hypot(y - 10.5, z - 0.6);
            if (d <= 2.6) return d < 0.9 ? GOLD : d > 2.0 ? QB : SQ;
        }
        // 머리 · 관 · 머리카락
        double hd = sq(x / 1.5) + sq((y - 18.0) / 1.7) + sq((z - 0.1) / 1.5);
        if (hd <= 1) {
            if (y >= 19.0 && y < 20.0 && hd > 0.45) return GOLD;
            return z < -0.3 || y > 19.4 ? QB : SQ;
        }
        if (y >= 19.6 && y < 20.6 && Math.abs(x) < 0.5 && Math.abs(z - 0.6) < 0.6) return GOLD;
        if (y >= 13 && y < 17.6 && z <= -0.6 && z >= -1.6 && Math.abs(x) <= 1.2) return QB;
        // 목
        if (y >= 16 && y < 16.8 && Math.abs(x) < 0.6 && Math.abs(z) < 0.6) return SQ;
        // 어깨 · 몸통 · 허리띠
        if (y >= 14.5 && y < 16 && Math.abs(x) <= 2.5 && Math.abs(z) <= 1.0) return SQ;
        if (y >= 10 && y < 14.5) {
            double rx = y < 12 ? 1.6 : 2.0;
            if (sq(x / rx) + sq(z / 1.25) <= 1) return y < 11 ? GOLD : SQ;
        }
        // 드레스 (아래가 넓음 · 세로 주름)
        if (y < 10) {
            double rx = 3.4 - 0.17 * y;
            double rz = 2.6 - 0.11 * y;
            if (sq(x / rx) + sq((z - 0.2) / rz) <= 1) {
                if (y < 1) return QB;
                return Math.floorMod(ix, 2) == 0 && z > 0.5 ? "minecraft:quartz_pillar[axis=y]" : SQ;
            }
        }
        // 날개: 어깨 뒤에서 위 · 바깥으로 솟아 끝이 머리 위까지 (앞에서 몸을 가리지 않게 좁게 · 뒤로 젖힘 — CI 42364178888 사진: 넓게 편 날개가 흰 벽처럼 보임)
        double ax = Math.abs(x);
        if (ax >= 1.0 && ax <= 7.5 && y >= 11) {
            double u = (ax - 1.0) / 6.5;
            double zw = -1.5 - 2.2 * u;
            if (Math.abs(z - zw) <= 0.6) {
                double topEdge = 16.5 + 11.0 * Math.pow(u, 0.8);
                double botEdge = 12.0 + 12.5 * Math.pow(u, 1.3);
                if (y >= botEdge && y <= topEdge) {
                    int band = (int) Math.floor((topEdge - y) / 1.8);
                    return Math.floorMod(band, 2) == 0 ? SQ : "minecraft:calcite";
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 성당

    /**
     * 성당: 폭 25 (x 0 ~ 24) · 길이 48 (z 0 ~ 47, 앞 z = 47), 십자 날개 (x −6 ~ 30, z 7 ~ 15), 뒤 반원 (중심 12, 7 · 반지름 6.5).
     * 신랑 (x 6 ~ 18, 벽 20, 박공 지붕 용마루 27) · 옆 복도 (x 0 ~ 5 · 19 ~ 24, 벽 10, 외쪽 지붕) · 앞 쌍탑 (7 × 7, 벽 32 + 뾰족 지붕) ·
     * 교차부 탑 (벽 30 + 첨탑) · 장미창 · 파란 스테인드글라스 뾰족 창 · 버팀벽 · 날개 버팀벽 · 종 · 안 (기둥 줄 · 아치 · 의자 · 붉은 길 · 제단 · 매단 등).
     */
    static void cathedral(Blueprint b) {
        String trim = "minecraft:polished_andesite";
        String tiles = "minecraft:deepslate_tiles";
        String tileS = "minecraft:deepslate_tile_stairs";
        String glassA = "minecraft:blue_stained_glass_pane";
        String glassB = "minecraft:light_blue_stained_glass_pane";
        b.air(-8, 1, -1, 32, 50, 49);
        // 바닥 · 기초 (윤곽 전체)
        b.fill(-6, -3, 1, 30, -1, 47, "minecraft:cobblestone");
        b.fill(0, 0, 16, 24, 0, 47, trim);
        b.fill(-6, 0, 7, 30, 0, 15, trim);
        for (int x = 6; x <= 18; x++) {
            for (int z = 0; z <= 6; z++) if (Math.hypot(x - 12, z - 7) <= 6.5) b.set(x, 0, z, trim);
        }
        for (int x = 7; x <= 17; x++) for (int z = 8; z <= 46; z += 2) if ((x + z) % 4 == 0) b.set(x, 0, z, "minecraft:polished_diorite");
        // 옆 복도 바깥벽 (x 0 · 24, z 16 ~ 40, 높이 10)
        b.fill(0, 1, 16, 0, 10, 40, STONE);
        b.fill(24, 1, 16, 24, 10, 40, STONE);
        // 신랑 벽 (x 6 · 18): 아래는 기둥 줄 (4칸마다) · 아치, 위 (11 ~ 20) 는 벽
        for (int x : new int[] {6, 18}) {
            b.fill(x, 11, 16, x, 20, 46, STONE);
            for (int z = 16; z <= 40; z += 4) {
                b.fill(x, 1, z, x, 10, z, "minecraft:stone_bricks");
                b.set(x, 1, z, "minecraft:chiseled_stone_bricks");
                b.set(x, 8, z, "minecraft:chiseled_stone_bricks");
                if (z + 4 <= 40) {
                    b.set(x, 9, z + 1, "minecraft:stone_brick_stairs[facing=north,half=top]");
                    b.set(x, 9, z + 3, "minecraft:stone_brick_stairs[facing=south,half=top]");
                    b.fill(x, 10, z + 1, x, 10, z + 3, "minecraft:stone_bricks");
                }
            }
            b.fill(x, 1, 41, x, 20, 46, STONE);
        }
        // 십자 날개 (x −6 ~ 30, z 7 ~ 15, 높이 20)
        b.fill(-6, 1, 7, 5, 20, 7, STONE);
        b.fill(-6, 1, 15, 5, 20, 15, STONE);
        b.fill(19, 1, 7, 30, 20, 7, STONE);
        b.fill(19, 1, 15, 30, 20, 15, STONE);
        b.fill(-6, 1, 7, -6, 20, 15, STONE);
        b.fill(30, 1, 7, 30, 20, 15, STONE);
        // 옆 복도 앞뒤 (z 16 · 40) 와 날개 사이 · 탑 사이 벽
        b.fill(0, 1, 16, 5, 10, 16, STONE);
        b.fill(19, 1, 16, 24, 10, 16, STONE);
        // 뒤 반원 (apse): 고리 벽 (높이 17) · 창
        for (int x = 4; x <= 20; x++) {
            for (int z = -1; z <= 6; z++) {
                double d = Math.hypot(x - 12, z - 7);
                if (d > 6.5 || d < 5.4) continue;
                b.fill(x, 1, z, x, 17, z, STONE);
                if (Math.floorMod(x, 3) == 0) b.fill(x, 4, z, x, 12, z, glassA);
            }
        }
        // 앞 쌍탑 (x 0 ~ 6 · 18 ~ 24, z 41 ~ 47, 벽 32): 속 빈 탑 · 종 · 종탑 창 · 난간 · 뾰족 지붕
        for (int tx : new int[] {0, 18}) {
            b.walls(tx, 1, 41, tx + 6, 32, 47, STONE);
            b.fill(tx, 1, 41, tx, 32, 41, "minecraft:stone_bricks");
            for (int[] p : new int[][] {{tx, 47}, {tx + 6, 47}, {tx, 41}, {tx + 6, 41}}) b.fill(p[0], 1, p[1], p[0], 32, p[1], trim);
            // 좁은 창 · 종탑 창 (네 면)
            for (int y : new int[] {8, 16}) {
                b.fill(tx + 3, y, 47, tx + 3, y + 3, 47, glassA);
                b.fill(tx == 0 ? tx : tx + 6, y, 44, tx == 0 ? tx : tx + 6, y + 3, 44, glassA);
            }
            b.fill(tx + 2, 24, 47, tx + 4, 28, 47, "minecraft:air");
            b.fill(tx + 2, 24, 41, tx + 4, 28, 41, "minecraft:air");
            b.fill(tx, 24, 43, tx, 28, 45, "minecraft:air");
            b.fill(tx + 6, 24, 43, tx + 6, 28, 45, "minecraft:air");
            b.set(tx + 3, 29, 47, "minecraft:stone_bricks");
            b.fill(tx + 1, 29, 44, tx + 5, 29, 44, "minecraft:dark_oak_log[axis=x]");
            b.set(tx + 3, 28, 44, "minecraft:bell[attachment=ceiling,facing=south]");
            b.fill(tx + 1, 22, 42, tx + 5, 22, 46, "minecraft:spruce_planks");
            // 난간 (33) · 뾰족 지붕 (34 ~ 45)
            for (int x = tx; x <= tx + 6; x++) {
                for (int z = 41; z <= 47; z++) {
                    boolean edge = x == tx || x == tx + 6 || z == 41 || z == 47;
                    if (edge) b.set(x, 33, z, (x + z) % 2 == 0 ? "minecraft:stone_bricks" : "minecraft:stone_brick_wall");
                }
            }
            spire(b, tx + 3, 44, 34, 3, 12, tiles, tileS);
        }
        // 앞 (정문 · 장미창 · 박공): z = 47, x 7 ~ 17
        b.fill(7, 1, 47, 17, 20, 47, STONE);
        for (int k = 1; k <= 6; k++) b.fill(6 + k, 20 + k, 47, 18 - k, 20 + k, 47, STONE);
        for (int x = 6; x <= 18; x++) {
            for (int y = 9; y <= 21; y++) {
                double d = Math.hypot(x - 12, y - 15);
                if (d >= 5.4) continue;
                if (d >= 4.5) {
                    b.set(x, y, 47, "minecraft:chiseled_stone_bricks");
                    continue;
                }
                double deg = Math.toDegrees(Math.atan2(y - 15, x - 12));
                double rel = Math.floorMod(Math.round(deg + 360), 45);
                String glass;
                if (d < 1.2) glass = "minecraft:yellow_stained_glass";
                else if (d > 1.6 && (rel < 6 || rel > 39)) glass = trim;
                else glass = d > 3.2 ? "minecraft:blue_stained_glass" : "minecraft:light_blue_stained_glass";
                b.set(x, y, 47, glass);
            }
        }
        // 정문: 뾰족 아치 (x 10 ~ 14), 문틀 겹 (z 47 · 48), 덮개
        b.fill(10, 1, 48, 14, 8, 48, "minecraft:stone_bricks");
        b.fill(11, 1, 47, 13, 6, 48, "minecraft:air");
        b.set(12, 7, 47, "minecraft:air");
        b.set(12, 7, 48, "minecraft:air");
        b.set(11, 6, 48, "minecraft:stone_brick_stairs[facing=west,half=top]");
        b.set(13, 6, 48, "minecraft:stone_brick_stairs[facing=east,half=top]");
        b.set(11, 6, 47, "minecraft:stone_brick_stairs[facing=west,half=top]");
        b.set(13, 6, 47, "minecraft:stone_brick_stairs[facing=east,half=top]");
        for (int k = 0; k <= 1; k++) {
            b.set(10 + k, 9 + k, 48, "minecraft:stone_brick_stairs[facing=east,half=bottom]");
            b.set(14 - k, 9 + k, 48, "minecraft:stone_brick_stairs[facing=west,half=bottom]");
        }
        b.set(12, 11, 48, "minecraft:chiseled_stone_bricks");
        b.fill(8, 0, 48, 16, 0, 50, trim);
        b.set(9, 1, 49, "minecraft:lantern[hanging=false]");
        b.set(15, 1, 49, "minecraft:lantern[hanging=false]");
        b.set(10, 4, 49, "minecraft:blue_wall_banner[facing=south]");
        b.set(14, 4, 49, "minecraft:blue_wall_banner[facing=south]");
        // 옆 복도 창 · 버팀벽 · 날개 버팀벽 (z 16 ~ 40, 4칸마다)
        for (int z = 16; z <= 40; z += 4) {
            for (int side = 0; side <= 1; side++) {
                int ox = side == 0 ? -1 : 25;
                int ix = side == 0 ? 0 : 24;
                String face = side == 0 ? "east" : "west";
                b.fill(ox, 1, z, ox, 10, z, "minecraft:stone_bricks");
                b.set(ox, 11, z, "minecraft:stone_brick_wall");
                // 날개 버팀벽: 바깥 기둥 위 (11) → 신랑 벽 (17)
                for (int k = 0; k <= 5; k++) {
                    int x = side == 0 ? ox + k + 1 : ox - k - 1;
                    b.set(x, 12 + k, z, "minecraft:stone_brick_stairs[facing=" + face + ",half=bottom]");
                }
                if (z + 2 <= 40) {
                    b.fill(ix, 3, z + 1, ix, 7, z + 3, glassA);
                    b.set(ix, 8, z + 2, glassB);
                    b.set(ix, 7, z + 1, "minecraft:stone_bricks");
                    b.set(ix, 7, z + 3, "minecraft:stone_bricks");
                    // 높은 창 (신랑 벽 · 옆 복도 지붕 위)
                    int nx = side == 0 ? 6 : 18;
                    b.fill(nx, 17, z + 1, nx, 19, z + 3, glassA);
                    b.set(nx, 19, z + 1, "minecraft:stone_bricks");
                    b.set(nx, 19, z + 3, "minecraft:stone_bricks");
                }
            }
        }
        // 날개 끝 큰 창 (x −6 · 30)
        for (int ex : new int[] {-6, 30}) {
            b.fill(ex, 3, 9, ex, 15, 13, glassA);
            b.fill(ex, 3, 11, ex, 15, 11, "minecraft:stone_bricks");
            b.fill(ex, 9, 9, ex, 9, 13, "minecraft:stone_bricks");
            b.fill(ex, 16, 10, ex, 17, 12, glassB);
            b.fill(ex, 15, 9, ex, 15, 9, "minecraft:stone_bricks");
            b.fill(ex, 15, 13, ex, 15, 13, "minecraft:stone_bricks");
        }
        // 지붕: 옆 복도 (외쪽) · 신랑 (박공) · 날개 (박공, 용마루 x 방향) · 반원 (원뿔 반쪽)
        for (int k = 0; k <= 6; k++) {
            b.fill(-1 + k, 10 + k, 16, -1 + k, 10 + k, 40, "minecraft:dark_oak_stairs[facing=east,half=bottom]");
            b.fill(25 - k, 10 + k, 16, 25 - k, 10 + k, 40, "minecraft:dark_oak_stairs[facing=west,half=bottom]");
        }
        for (int k = 0; k <= 6; k++) {
            int zEnd = k >= 2 ? 47 : 40;
            b.fill(5 + k, 20 + k, 16, 5 + k, 20 + k, zEnd, tileS + "[facing=east,half=bottom]");
            b.fill(19 - k, 20 + k, 16, 19 - k, 20 + k, zEnd, tileS + "[facing=west,half=bottom]");
        }
        b.fill(12, 27, 16, 12, 27, 47, tiles);
        for (int[] arm : new int[][] {{-7, 5}, {19, 31}}) {
            for (int k = 0; k <= 4; k++) {
                b.fill(arm[0], 20 + k, 6 + k, arm[1], 20 + k, 6 + k, tileS + "[facing=south,half=bottom]");
                b.fill(arm[0], 20 + k, 16 - k, arm[1], 20 + k, 16 - k, tileS + "[facing=north,half=bottom]");
            }
            b.fill(arm[0], 25, 11, arm[1], 25, 11, tiles);
        }
        for (int k = 1; k <= 4; k++) {
            b.fill(-6, 20 + k, 7 + k, -6, 20 + k, 15 - k, STONE);
            b.fill(30, 20 + k, 7 + k, 30, 20 + k, 15 - k, STONE);
        }
        for (int x = 4; x <= 20; x++) {
            for (int z = -1; z <= 6; z++) {
                double d = Math.hypot(x - 12, z - 7);
                if (d > 7.2) continue;
                int ry = 17 + (int) Math.round((7.2 - d) * 0.9);
                b.set(x, ry, z, tiles);
            }
        }
        // 교차부 탑 (x 6 ~ 18, z 7 ~ 15, 벽 21 ~ 30) · 창 · 첨탑
        b.walls(6, 21, 7, 18, 30, 15, STONE);
        for (int x = 8; x <= 16; x += 4) {
            b.fill(x, 24, 15, x + 1, 28, 15, glassA);
            b.fill(x, 24, 7, x + 1, 28, 7, glassA);
        }
        b.fill(6, 24, 10, 6, 28, 12, glassA);
        b.fill(18, 24, 10, 18, 28, 12, glassA);
        b.fill(6, 31, 7, 18, 31, 15, tiles);
        for (int x = 6; x <= 18; x++) {
            for (int z = 7; z <= 15; z++) {
                if (x == 6 || x == 18 || z == 7 || z == 15) b.set(x, 32, z, (x + z) % 2 == 0 ? "minecraft:stone_bricks" : "minecraft:stone_brick_wall");
            }
        }
        spire(b, 12, 11, 32, 3, 16, tiles, tileS);
        // 안: 붉은 길 · 의자 · 제단 · 매단 등
        b.fill(11, 1, 16, 13, 1, 46, "minecraft:red_carpet");
        b.fill(11, 1, 9, 13, 1, 15, "minecraft:red_carpet");
        for (int z = 20; z <= 40; z += 2) {
            b.fill(7, 1, z, 10, 1, z, "minecraft:spruce_stairs[facing=south,half=bottom]");
            b.fill(14, 1, z, 17, 1, z, "minecraft:spruce_stairs[facing=south,half=bottom]");
        }
        b.fill(9, 1, 3, 15, 1, 7, "minecraft:polished_andesite_slab[type=bottom]");
        b.fill(10, 1, 4, 14, 1, 5, "minecraft:polished_blackstone_bricks");
        b.set(12, 2, 4, GOLD);
        b.set(12, 3, 4, "minecraft:sea_lantern");
        b.set(10, 2, 4, "minecraft:white_candle[candles=4,lit=true]");
        b.set(14, 2, 4, "minecraft:white_candle[candles=4,lit=true]");
        b.set(11, 2, 5, "minecraft:white_candle[candles=3,lit=true]");
        b.set(13, 2, 5, "minecraft:white_candle[candles=3,lit=true]");
        for (int z : new int[] {22, 30, 38}) {
            b.fill(12, 20, z, 12, 26, z, "minecraft:iron_chain[axis=y]");
            b.set(12, 19, z, "minecraft:lantern[hanging=true]");
        }
        for (int z = 18; z <= 38; z += 4) {
            b.fill(1, 8, z, 1, 11, z, "minecraft:iron_chain[axis=y]");
            b.set(1, 7, z, "minecraft:lantern[hanging=true]");
            b.fill(23, 8, z, 23, 11, z, "minecraft:iron_chain[axis=y]");
            b.set(23, 7, z, "minecraft:lantern[hanging=true]");
        }
        b.fill(12, 21, 11, 12, 30, 11, "minecraft:iron_chain[axis=y]");
        b.set(12, 20, 11, "minecraft:lantern[hanging=true]");
    }

    /** 뾰족 지붕 (네모 — 가운데 cx, cz, 바닥 y, 반폭 h, 높이 n): 층마다 줄어드는 기와 + 가장자리 계단, 꼭대기 금 · 피뢰침 */
    static void spire(Blueprint b, int cx, int cz, int y, int h, int n, String tiles, String stairs) {
        for (int i = 0; i < n; i++) {
            double hh = h + 0.5 - (h + 0.5) * i / (double) n;
            int r = (int) Math.floor(hh);
            if (r < 0) break;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    boolean edge = Math.abs(x) == r || Math.abs(z) == r;
                    String s = tiles;
                    if (edge && r > 0 && Math.abs(x) != Math.abs(z)) {
                        String face = Math.abs(x) == r ? (x > 0 ? "west" : "east") : (z > 0 ? "north" : "south");
                        s = stairs + "[facing=" + face + ",half=bottom]";
                    }
                    b.set(cx + x, y + i, cz + z, s);
                }
            }
        }
        b.set(cx, y + n, cz, GOLD);
        b.set(cx, y + n + 1, cz, "minecraft:lightning_rod[facing=up]");
    }

    // ------------------------------------------------------------------ 마탑

    /**
     * 마탑: 가운데 (0, 0), 반지름 7, 몸통 높이 44, 앞 (문) 남쪽. 짙은 심층암 벽돌 · 자수정 띠 · 세로 갈비 여덟 · 보라 유리 창 · 36층 발코니 ·
     * 꼭대기 방 · 원뿔 첨탑 (높이 20, 자수정 띠 · 금 끝) · 떠 있는 수정 넷. 1층: 마법사 (npc) — 마법 부여대 · 책장 · 양조대 · 가마솥 · 자수정.
     */
    static void mageTower(Blueprint b, String npc) {
        int R = 7;
        int H = 44;
        double ro = R + 0.3;
        String wall = "minecraft:deepslate_bricks|minecraft:deepslate_bricks|minecraft:cracked_deepslate_bricks|minecraft:polished_deepslate";
        for (int x = -R - 4; x <= R + 4; x++) {
            for (int z = -R - 4; z <= R + 4; z++) {
                double d = Math.hypot(x, z);
                if (d > ro + 2.6) continue;
                b.air(x, 1, z, x, H + 24, z);
                double deg = Math.toDegrees(Math.atan2(z, x));
                double rib = Math.abs(((deg % 45) + 45) % 45 - 22.5);
                if (d <= ro + 1.6) {
                    b.fill(x, -3, z, x, -1, z, "minecraft:cobbled_deepslate");
                    b.set(x, 0, z, d > ro ? "minecraft:polished_deepslate" : "minecraft:deepslate_tiles");
                }
                if (d <= ro) {
                    if (d > ro - 1.3) {
                        b.fill(x, 1, z, x, H, z, wall);
                        for (int y = 9; y < H; y += 9) b.set(x, y, z, "minecraft:amethyst_block");
                    } else {
                        for (int y = 9; y <= 36; y += 9) b.set(x, y, z, "minecraft:dark_oak_planks");
                    }
                } else if (d <= ro + 1.1 && rib > 19) {
                    // 세로 갈비 (8방향)
                    b.fill(x, 1, z, x, H - 9, z, "minecraft:polished_deepslate");
                    b.set(x, H - 8, z, "minecraft:polished_deepslate_wall");
                }
                // 발코니 (36): 바닥 · 받침 · 난간
                if (d > ro && d <= ro + 2.4) {
                    b.set(x, 36, z, "minecraft:dark_oak_slab[type=top]");
                    if (d > ro + 1.5) b.set(x, 37, z, "minecraft:dark_oak_fence");
                }
                // 꼭대기 난간 (45)
                if (d <= ro + 0.6 && d > ro - 1.3 && Math.floorMod(x + z, 2) == 0) b.set(x, H + 1, z, "minecraft:deepslate_brick_wall");
            }
        }
        // 창: 층마다 8방향 (갈비 사이) 좁고 긴 보라 유리 · 꼭대기 방은 큰 창
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(k * 45);
            int wx = (int) Math.round(Math.cos(a) * R);
            int wz = (int) Math.round(Math.sin(a) * R);
            if (wx == 0 && Math.abs(wz) == R) continue;
            for (int y0 : new int[] {3, 12, 21, 30}) b.fill(wx, y0, wz, wx, y0 + 3, wz, "minecraft:purple_stained_glass");
            b.fill(wx, 38, wz, wx, 42, wz, "minecraft:purple_stained_glass");
        }
        // 발코니 문 (남쪽 36층)
        b.fill(0, 37, R, 0, 38, R, "minecraft:air");
        // 첨탑 (원뿔, 높이 20) · 자수정 띠 · 금 · 피뢰침 · 끝막대
        int base = H + 1;
        for (int i = 0; i <= 20; i++) {
            double rr = (ro - 0.4) * (1 - i / 20.0);
            for (int x = -R; x <= R; x++) {
                for (int z = -R; z <= R; z++) {
                    double d = Math.hypot(x, z);
                    if (d > rr) continue;
                    if (d > rr - 1.3 || i == 0) b.set(x, base + i, z, i == 7 || i == 8 ? "minecraft:amethyst_block" : "minecraft:deepslate_tiles");
                }
            }
        }
        b.set(0, base + 21, 0, GOLD);
        b.set(0, base + 22, 0, "minecraft:lightning_rod[facing=up]");
        b.set(0, base + 23, 0, "minecraft:end_rod[facing=up]");
        // 떠 있는 수정 넷 (꼭대기 방 둘레)
        for (int k = 0; k < 4; k++) {
            double a = Math.toRadians(45 + k * 90);
            int cx = (int) Math.round(Math.cos(a) * (R + 6));
            int cz = (int) Math.round(Math.sin(a) * (R + 6));
            int y = 39 + (k % 2) * 3;
            b.set(cx, y - 1, cz, "minecraft:amethyst_cluster[facing=down]");
            b.fill(cx, y, cz, cx, y + 1, cz, "minecraft:amethyst_block");
            b.set(cx, y + 2, cz, "minecraft:amethyst_cluster[facing=up]");
        }
        // 문 (남쪽) · 계단 · 등
        b.fill(-1, 1, R - 1, 1, 3, R + 1, "minecraft:air");
        b.set(-1, 3, R, "minecraft:deepslate_brick_stairs[facing=west,half=top]");
        b.set(1, 3, R, "minecraft:deepslate_brick_stairs[facing=east,half=top]");
        b.set(0, 1, R, "minecraft:dark_oak_door[facing=north,half=lower,hinge=left,open=false]");
        b.set(0, 2, R, "minecraft:dark_oak_door[facing=north,half=upper,hinge=left,open=false]");
        b.set(-2, 1, R + 2, "minecraft:soul_lantern[hanging=false]");
        b.set(2, 1, R + 2, "minecraft:soul_lantern[hanging=false]");
        b.fill(-1, 0, R + 1, 1, 0, R + 3, "minecraft:polished_deepslate");
        // 사다리 (북쪽 벽 안쪽) · 바닥 구멍
        b.fill(0, 1, -(R - 1), 0, 43, -(R - 1), "minecraft:ladder[facing=south]");
        // 1층: 마법 부여대 · 책장 둘레 · 양조대 · 가마솥 · 자수정 · 독서대
        b.set(0, 1, 0, "minecraft:enchanting_table");
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                double d = Math.hypot(x, z);
                if (d < 4.6 || d > 5.7) continue;
                if (z >= 4 && Math.abs(x) <= 1) continue;
                if (x == 0 && z <= -4) continue;
                b.fill(x, 1, z, x, 2, z, "minecraft:bookshelf");
            }
        }
        b.set(3, 1, 2, "minecraft:brewing_stand");
        b.set(-3, 1, 2, "minecraft:water_cauldron[level=3]");
        b.set(2, 1, -2, "minecraft:amethyst_block");
        b.set(2, 2, -2, "minecraft:amethyst_cluster[facing=up]");
        b.set(-2, 1, -2, "minecraft:lectern[facing=south]");
        for (int y : new int[] {8, 17, 26, 35}) b.set(0, y, 0, "minecraft:soul_lantern[hanging=true]");
        b.set(3, 10, 3, "minecraft:bookshelf");
        b.set(-3, 10, 3, "minecraft:bookshelf");
        b.set(3, 19, -2, "minecraft:red_bed[facing=north,part=head]");
        b.set(3, 19, -1, "minecraft:red_bed[facing=north,part=foot]");
        b.set(0, 37, 0, "minecraft:enchanting_table");
        b.set(0, 41, 0, "minecraft:amethyst_cluster[facing=down]");
        if (npc != null) b.mark(npc, 0.5, 1, 2.5, "south");
    }

    // ------------------------------------------------------------------ 모험가 길드

    /**
     * 모험가 길드: 폭 17 · 길이 25, 앞 (큰 아치 입구) 남쪽. 아래층 돌 (높은 홀 — 벽 5칸) · 2 · 3층 나무 뼈대와 회벽 · 박공이 앞을 보는 가파른 기와 지붕 (용마루 22) ·
     * 둥근 창 · 2층 발코니 · 파란 깃발. 안: 접수대 (직업 교관 npc) · 의뢰 게시판 (짙은 판에 자작나무 쪽지) · 탁자 · 의자 · 벽난로 · 매단 등 · 2층 침대 · 3층 서고.
     */
    static void guild(Blueprint b, String npc) {
        int W = 17;
        int D = 25;
        int c = W / 2;
        String frame = "minecraft:dark_oak_log";
        String infill = "minecraft:calcite";
        b.air(-1, 1, -1, W, 32, D + 2);
        b.fill(0, -3, 0, W - 1, -1, D - 1, "minecraft:cobblestone");
        b.fill(0, 0, 0, W - 1, 0, D - 1, "minecraft:polished_andesite");
        b.fill(1, 0, 1, W - 2, 0, D - 2, "minecraft:dark_oak_planks");
        // 아래층 (1 ~ 5): 돌 벽 · 기둥 (4칸마다) · 높은 창
        b.walls(0, 1, 0, W - 1, 5, D - 1, "minecraft:stone_bricks|minecraft:stone_bricks|minecraft:mossy_stone_bricks");
        for (int x = 0; x < W; x += 4) {
            b.fill(x, 1, D - 1, x, 5, D - 1, "minecraft:polished_andesite");
            b.fill(x, 1, 0, x, 5, 0, "minecraft:polished_andesite");
        }
        for (int z = 0; z < D; z += 4) {
            b.fill(0, 1, z, 0, 5, z, "minecraft:polished_andesite");
            b.fill(W - 1, 1, z, W - 1, 5, z, "minecraft:polished_andesite");
        }
        for (int z = 2; z < D - 1; z += 4) {
            b.fill(0, 2, z, 0, 4, z, "minecraft:glass_pane");
            b.fill(W - 1, 2, z, W - 1, 4, z, "minecraft:glass_pane");
        }
        b.fill(2, 2, D - 1, 2, 4, D - 1, "minecraft:glass_pane");
        b.fill(14, 2, D - 1, 14, 4, D - 1, "minecraft:glass_pane");
        // 2 · 3층 (6 · 10 바닥, 벽 7 ~ 9 · 11 ~ 13, 맨 위 들보 14)
        for (int y0 : new int[] {6, 10, 14}) {
            b.fill(0, y0, 0, W - 1, y0, D - 1, "minecraft:spruce_planks");
            b.fill(0, y0, 0, W - 1, y0, 0, "minecraft:stripped_dark_oak_log[axis=x]");
            b.fill(0, y0, D - 1, W - 1, y0, D - 1, "minecraft:stripped_dark_oak_log[axis=x]");
            b.fill(0, y0, 1, 0, y0, D - 2, "minecraft:stripped_dark_oak_log[axis=z]");
            b.fill(W - 1, y0, 1, W - 1, y0, D - 2, "minecraft:stripped_dark_oak_log[axis=z]");
        }
        for (int y0 : new int[] {6, 10}) {
            b.walls(0, y0 + 1, 0, W - 1, y0 + 3, D - 1, infill);
            for (int x = 0; x < W; x += 4) {
                b.fill(x, y0 + 1, 0, x, y0 + 3, 0, frame + "[axis=y]");
                b.fill(x, y0 + 1, D - 1, x, y0 + 3, D - 1, frame + "[axis=y]");
            }
            for (int z = 0; z < D; z += 4) {
                b.fill(0, y0 + 1, z, 0, y0 + 3, z, frame + "[axis=y]");
                b.fill(W - 1, y0 + 1, z, W - 1, y0 + 3, z, frame + "[axis=y]");
            }
            for (int x = 2; x < W - 1; x += 4) {
                b.fill(x, y0 + 2, D - 1, x, y0 + 3, D - 1, "minecraft:glass_pane");
                b.fill(x, y0 + 2, 0, x, y0 + 3, 0, "minecraft:glass_pane");
            }
            for (int z = 2; z < D - 1; z += 4) {
                b.fill(0, y0 + 2, z, 0, y0 + 3, z, "minecraft:glass_pane");
                b.fill(W - 1, y0 + 2, z, W - 1, y0 + 3, z, "minecraft:glass_pane");
            }
        }
        // 지붕 (박공이 앞 · 뒤를 봄): 14 + k, 용마루 x = c
        for (int k = 0; k <= c; k++) {
            int y = 14 + k;
            if (k == c) {
                b.fill(c, y, -1, c, y, D, "minecraft:deepslate_tiles");
                break;
            }
            b.fill(k, y, -1, k, y, D, "minecraft:deepslate_tile_stairs[facing=east,half=bottom]");
            b.fill(W - 1 - k, y, -1, W - 1 - k, y, D, "minecraft:deepslate_tile_stairs[facing=west,half=bottom]");
            if (k >= 1) {
                b.fill(k + 1, y, D - 1, W - 2 - k, y, D - 1, infill);
                b.fill(k + 1, y, 0, W - 2 - k, y, 0, infill);
            }
        }
        // 앞 박공: 둥근 창 · 뼈대 · 금 장식
        for (int x = c - 3; x <= c + 3; x++) {
            for (int y = 15; y <= 19; y++) {
                double d = Math.hypot(x - c, y - 17);
                if (d > 2.2) continue;
                b.set(x, y, D - 1, d > 1.5 ? frame + "[axis=y]" : "minecraft:glass_pane");
            }
        }
        b.set(c, 23, D, GOLD);
        // 입구: 큰 아치 (x c−1 ~ c+1, 1 ~ 4) · 덮개 · 깃발 · 등
        b.fill(c - 1, 1, D - 1, c + 1, 4, D - 1, "minecraft:air");
        b.set(c - 1, 4, D - 1, "minecraft:stone_brick_stairs[facing=west,half=top]");
        b.set(c + 1, 4, D - 1, "minecraft:stone_brick_stairs[facing=east,half=top]");
        b.fill(c - 2, 5, D, c + 2, 5, D, "minecraft:dark_oak_slab[type=top]");
        b.set(c - 2, 3, D, "minecraft:blue_wall_banner[facing=south]");
        b.set(c + 2, 3, D, "minecraft:blue_wall_banner[facing=south]");
        b.set(c - 3, 1, D, "minecraft:lantern[hanging=false]");
        b.set(c + 3, 1, D, "minecraft:lantern[hanging=false]");
        b.fill(c - 2, 0, D, c + 2, 0, D + 1, "minecraft:polished_andesite");
        // 2층 발코니 (입구 위)
        b.fill(c - 3, 6, D, c + 3, 6, D + 1, "minecraft:dark_oak_slab[type=top]");
        b.fill(c - 3, 7, D + 1, c + 3, 7, D + 1, "minecraft:dark_oak_fence");
        b.fill(c - 3, 7, D, c - 3, 7, D, "minecraft:dark_oak_fence");
        b.fill(c + 3, 7, D, c + 3, 7, D, "minecraft:dark_oak_fence");
        b.set(c, 7, D - 1, "minecraft:dark_oak_door[facing=north,half=lower,hinge=left,open=false]");
        b.set(c, 8, D - 1, "minecraft:dark_oak_door[facing=north,half=upper,hinge=left,open=false]");
        b.set(c - 2, 9, D, "minecraft:blue_wall_banner[facing=south]");
        b.set(c + 2, 9, D, "minecraft:blue_wall_banner[facing=south]");
        // 안: 접수대 (뒤쪽) · 직업 교관 자리
        b.fill(3, 1, 6, 13, 1, 6, "minecraft:polished_andesite");
        b.fill(3, 2, 6, 13, 2, 6, "minecraft:dark_oak_slab[type=bottom]");
        b.set(c, 1, 6, "minecraft:air");
        b.set(c, 2, 6, "minecraft:air");
        b.fill(2, 1, 1, 14, 3, 1, "minecraft:bookshelf");
        b.set(5, 1, 3, "minecraft:barrel[facing=up]");
        b.set(11, 1, 3, "minecraft:barrel[facing=up]");
        b.set(4, 3, 6, "minecraft:lantern[hanging=false]");
        b.set(12, 3, 6, "minecraft:lantern[hanging=false]");
        // 의뢰 게시판 (서쪽 벽): 짙은 판 · 쪽지 (자작나무 다락문)
        b.fill(1, 2, 9, 1, 4, 15, "minecraft:dark_oak_planks");
        int[][] notes = {{2, 9}, {3, 10}, {2, 11}, {4, 12}, {3, 13}, {2, 14}, {4, 15}, {4, 9}, {3, 15}};
        for (int[] n : notes) b.set(2, n[0], n[1], "minecraft:birch_trapdoor[facing=east,half=bottom,open=true]");
        b.set(2, 5, 12, "minecraft:lantern[hanging=true]");
        // 탁자 · 의자 · 벽난로 (동쪽 벽)
        for (int[] t : new int[][] {{6, 11}, {10, 11}, {6, 17}, {10, 17}}) {
            b.set(t[0], 1, t[1], "minecraft:dark_oak_fence");
            b.set(t[0], 2, t[1], "minecraft:dark_oak_pressure_plate");
            b.set(t[0] - 1, 1, t[1], "minecraft:spruce_stairs[facing=west,half=bottom]");
            b.set(t[0] + 1, 1, t[1], "minecraft:spruce_stairs[facing=east,half=bottom]");
        }
        b.fill(W - 2, 1, 11, W - 2, 4, 13, "minecraft:bricks");
        b.set(W - 2, 1, 12, "minecraft:campfire[lit=true,signal_fire=false]");
        b.fill(W - 1, 5, 12, W - 1, 22, 12, "minecraft:bricks");
        b.set(W - 1, 23, 12, "minecraft:campfire[lit=true,signal_fire=false]");
        for (int z : new int[] {9, 15, 20}) b.set(c, 5, z, "minecraft:lantern[hanging=true]");
        // 사다리 · 2층 침대 · 3층 서고
        b.fill(1, 1, 3, 1, 13, 3, "minecraft:ladder[facing=east]");
        b.set(1, 6, 3, "minecraft:ladder[facing=east]");
        b.set(1, 10, 3, "minecraft:ladder[facing=east]");
        for (int z = 6; z <= 18; z += 4) {
            b.set(W - 2, 7, z, "minecraft:red_bed[facing=east,part=head]");
            b.set(W - 3, 7, z, "minecraft:red_bed[facing=east,part=foot]");
        }
        for (int x = 3; x <= 13; x += 2) b.fill(x, 11, 2, x, 13, 2, "minecraft:bookshelf");
        b.set(c, 9, 12, "minecraft:lantern[hanging=true]");
        b.set(c, 13, 12, "minecraft:lantern[hanging=true]");
        if (npc != null) b.mark(npc, c + 0.5, 1, 4.5, "south");
    }

    // ------------------------------------------------------------------ 대장장이 무기소

    /**
     * 대장장이 무기소: 폭 19 · 길이 21, 앞 (길) 남쪽. 왼쪽 (x 0 ~ 10) 은 무기 가게 (돌 아래층 · 벽돌 위층 · 앞을 보는 박공 · 진열 창 · 벽에 꽂힌 창 · 숫돌 간판),
     * 오른쪽 (x 11 ~ 18) 은 대장간 마당 (큰 화로 · 높은 굴뚝 · 신호 모닥불 연기 · 모루 둘 · 숫돌 · 담금질 물통 · 석탄 · 철 더미 · 무기 걸이 · 차양).
     */
    static void weaponsmith(Blueprint b, String npc) {
        int D = 21;
        b.air(-1, 1, -1, 19, 24, D + 1);
        b.fill(0, -3, 0, 18, -1, D - 1, "minecraft:cobblestone");
        b.fill(0, 0, 0, 18, 0, D - 1, "minecraft:stone_bricks|minecraft:cobblestone|minecraft:andesite");
        // 가게 (x 0 ~ 10, z 6 ~ 20)
        int sx0 = 0;
        int sx1 = 10;
        int sz0 = 6;
        int sz1 = D - 1;
        b.fill(sx0 + 1, 0, sz0 + 1, sx1 - 1, 0, sz1 - 1, "minecraft:spruce_planks");
        b.walls(sx0, 1, sz0, sx1, 4, sz1, "minecraft:cobblestone|minecraft:stone_bricks|minecraft:cobblestone");
        b.fill(sx0, 5, sz0, sx1, 5, sz1, "minecraft:spruce_planks");
        b.walls(sx0, 5, sz0, sx1, 5, sz1, "minecraft:stripped_dark_oak_log[axis=y]");
        b.walls(sx0, 6, sz0, sx1, 8, sz1, "minecraft:bricks");
        for (int x : new int[] {sx0, 5, sx1}) {
            b.fill(x, 6, sz1, x, 8, sz1, "minecraft:dark_oak_log[axis=y]");
            b.fill(x, 6, sz0, x, 8, sz0, "minecraft:dark_oak_log[axis=y]");
        }
        b.fill(2, 7, sz1, 3, 8, sz1, "minecraft:glass_pane");
        b.fill(7, 7, sz1, 8, 8, sz1, "minecraft:glass_pane");
        b.fill(sx0, 7, 12, sx0, 8, 14, "minecraft:glass_pane");
        b.fill(sx0, 9, sz0, sx1, 9, sz1, "minecraft:spruce_planks");
        for (int k = 0; k <= 5; k++) {
            int y = 9 + k;
            if (k == 5) {
                b.fill(5, y, sz0 - 1, 5, y, sz1 + 1, "minecraft:deepslate_tiles");
                break;
            }
            b.fill(k, y, sz0 - 1, k, y, sz1 + 1, "minecraft:deepslate_tile_stairs[facing=east,half=bottom]");
            b.fill(10 - k, y, sz0 - 1, 10 - k, y, sz1 + 1, "minecraft:deepslate_tile_stairs[facing=west,half=bottom]");
            if (k >= 1) {
                b.fill(k + 1, y, sz1, 9 - k, y, sz1, "minecraft:bricks");
                b.fill(k + 1, y, sz0, 9 - k, y, sz0, "minecraft:bricks");
            }
        }
        // 가게 앞: 문 · 진열 창 · 숫돌 간판 · 깃발 · 등
        b.set(5, 1, sz1, "minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false]");
        b.set(5, 2, sz1, "minecraft:spruce_door[facing=north,half=upper,hinge=left,open=false]");
        b.fill(1, 2, sz1, 3, 3, sz1, "minecraft:glass_pane");
        b.fill(7, 2, sz1, 9, 3, sz1, "minecraft:glass_pane");
        b.set(5, 4, sz1 + 1, "minecraft:grindstone[face=wall,facing=south]");
        b.set(0, 3, sz1 + 1, "minecraft:red_wall_banner[facing=south]");
        b.set(10, 3, sz1 + 1, "minecraft:red_wall_banner[facing=south]");
        b.fill(1, 4, sz1 + 1, 9, 4, sz1 + 1, "minecraft:spruce_slab[type=bottom]");
        b.set(5, 4, sz1 + 1, "minecraft:grindstone[face=wall,facing=south]");
        b.set(3, 3, sz1 + 1, "minecraft:lantern[hanging=true]");
        b.set(7, 3, sz1 + 1, "minecraft:lantern[hanging=true]");
        // 가게 안: 진열대 (창 안) · 벽에 꽂힌 창 (피뢰침) · 판매대 · 모루 · 대장장이 자리
        b.fill(1, 1, sz1 - 1, 3, 1, sz1 - 1, "minecraft:spruce_slab[type=top]");
        b.fill(7, 1, sz1 - 1, 9, 1, sz1 - 1, "minecraft:spruce_slab[type=top]");
        b.set(2, 2, sz1 - 1, "minecraft:lightning_rod[facing=up]");
        b.set(8, 2, sz1 - 1, "minecraft:lightning_rod[facing=up]");
        b.set(1, 2, sz1 - 1, "minecraft:iron_bars");
        b.set(9, 2, sz1 - 1, "minecraft:iron_bars");
        for (int z = sz0 + 2; z <= sz1 - 4; z += 2) {
            b.set(1, 2, z, "minecraft:lightning_rod[facing=east]");
            b.set(1, 3, z, "minecraft:lightning_rod[facing=east]");
            b.set(sx1 - 1, 2, z, "minecraft:lightning_rod[facing=west]");
            b.set(sx1 - 1, 3, z, "minecraft:lightning_rod[facing=west]");
        }
        b.fill(2, 1, 12, 8, 1, 12, "minecraft:polished_andesite");
        b.fill(2, 2, 12, 8, 2, 12, "minecraft:spruce_slab[type=bottom]");
        b.set(5, 1, 12, "minecraft:air");
        b.set(5, 2, 12, "minecraft:air");
        b.set(3, 1, 9, "minecraft:anvil[facing=east]");
        b.set(7, 1, 9, "minecraft:smithing_table");
        b.set(8, 1, 8, "minecraft:barrel[facing=up]");
        b.set(2, 1, 8, "minecraft:chest[facing=south,type=single]");
        b.set(5, 4, 15, "minecraft:lantern[hanging=true]");
        b.set(5, 4, 9, "minecraft:lantern[hanging=true]");
        // 대장간 마당 (x 11 ~ 18): 차양 · 큰 화로 · 굴뚝 · 신호 연기 · 모루 · 숫돌 · 물통 · 석탄 · 철 · 무기 걸이 · 울타리
        b.fill(11, 0, 1, 18, 0, D - 1, "minecraft:stone_bricks|minecraft:cobblestone|minecraft:gravel|minecraft:cobblestone");
        for (int[] p : new int[][] {{11, 1}, {18, 1}, {11, 12}, {18, 12}}) b.fill(p[0], 1, p[1], p[0], 5, p[1], "minecraft:dark_oak_log[axis=y]");
        b.fill(11, 6, 0, 18, 6, 13, "minecraft:dark_oak_slab[type=bottom]");
        b.fill(13, 1, 2, 17, 2, 6, "minecraft:bricks");
        b.fill(14, 2, 3, 16, 2, 5, "minecraft:magma_block");
        b.fill(14, 3, 3, 16, 3, 5, "minecraft:bricks");
        b.fill(14, 2, 6, 16, 2, 6, "minecraft:iron_bars");
        b.set(13, 1, 7, "minecraft:blast_furnace[facing=south,lit=true]");
        b.set(17, 1, 7, "minecraft:blast_furnace[facing=south,lit=true]");
        b.fill(14, 4, 3, 16, 17, 5, "minecraft:bricks");
        b.fill(15, 4, 4, 15, 16, 4, "minecraft:air");
        b.set(15, 17, 4, "minecraft:campfire[lit=true,signal_fire=true]");
        b.set(15, 18, 4, "minecraft:air");
        b.set(13, 1, 10, "minecraft:anvil[facing=south]");
        b.set(16, 1, 10, "minecraft:anvil[facing=east]");
        b.set(12, 1, 14, "minecraft:grindstone[face=floor,facing=east]");
        b.set(17, 1, 12, "minecraft:water_cauldron[level=3]");
        b.fill(17, 1, 14, 17, 1, 15, "minecraft:coal_block");
        b.set(17, 2, 14, "minecraft:coal_block");
        b.set(12, 1, 17, "minecraft:raw_iron_block");
        b.set(13, 1, 17, "minecraft:iron_block");
        b.fill(11, 1, 2, 12, 1, 5, "minecraft:spruce_log[axis=z]");
        b.set(11, 2, 3, "minecraft:spruce_log[axis=z]");
        for (int x = 14; x <= 17; x++) {
            b.set(x, 1, 18, "minecraft:spruce_fence");
            b.set(x, 2, 18, "minecraft:lightning_rod[facing=up]");
        }
        b.fill(11, 1, D - 1, 13, 1, D - 1, "minecraft:spruce_fence");
        b.fill(16, 1, D - 1, 18, 1, D - 1, "minecraft:spruce_fence");
        b.fill(18, 1, 13, 18, 1, D - 1, "minecraft:spruce_fence");
        b.set(11, 3, 12, "minecraft:lantern[hanging=false]");
        b.set(15, 5, 9, "minecraft:lantern[hanging=true]");
        b.set(15, 5, 1, "minecraft:lantern[hanging=true]");
        if (npc != null) b.mark(npc, 5.5, 1, 10.5, "south");
    }
}
