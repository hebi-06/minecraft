package mmorpg.core.field;

import java.util.ArrayList;
import java.util.List;

/**
 * 마을 섬의 마을 (2026-11-07 섬 맵): 둥근 성벽 (반지름 136 · 성문 넷 · 문 탑 · 모서리 둥근 탑) · 가운데 광장 (분수 · 등불 · 이동 지점) · 큰길 넷 · 둘레 길 ·
 * 건물 (왕녀의 전당 · 하랄의 집 · 훈련장과 무기 스승의 집 다섯 · 마법사 볼바의 집 · 룬술사의 탑 · 대장간 · 여관 · 장터 · 직업 교관의 집 · 집 여럿).
 * 건물마다 NPC 자리표 (Blueprint.Mark — 메인 퀘스트 NPC 번호 npc_1 … · 상인 shop_* · 직업 교관 job · 허수아비 dummy · 이동 지점 waystone_*).
 */
public final class FieldTown {
    private FieldTown() {}

    /** 마을 만들기 결과: 조각 · 자리표 */
    public record Result(List<FieldBuild.Piece> pieces, List<Blueprint.Mark> marks) {}

    /** 나무 · 지붕 차림 */
    record Style(String log, String planks, String stairs, String roof, String ridge, String foundation, String floor, String door, String fence) {}

    static final Style SPRUCE = new Style("minecraft:spruce_log", "minecraft:spruce_planks", "minecraft:spruce_stairs", "minecraft:dark_oak_stairs",
            "minecraft:dark_oak_log", "minecraft:cobblestone|minecraft:mossy_cobblestone|minecraft:cobblestone", "minecraft:spruce_planks", "minecraft:spruce_door",
            "minecraft:spruce_fence");
    static final Style OAK = new Style("minecraft:oak_log", "minecraft:oak_planks", "minecraft:oak_stairs", "minecraft:spruce_stairs", "minecraft:spruce_log",
            "minecraft:cobblestone|minecraft:stone_bricks", "minecraft:oak_planks", "minecraft:oak_door", "minecraft:oak_fence");
    static final Style BIRCH = new Style("minecraft:dark_oak_log", "minecraft:birch_planks", "minecraft:birch_stairs", "minecraft:dark_oak_stairs",
            "minecraft:dark_oak_log", "minecraft:stone_bricks|minecraft:cobblestone", "minecraft:birch_planks", "minecraft:birch_door", "minecraft:birch_fence");
    static final Style DARK = new Style("minecraft:spruce_log", "minecraft:dark_oak_planks", "minecraft:dark_oak_stairs", "minecraft:deepslate_tile_stairs",
            "minecraft:polished_deepslate", "minecraft:cobbled_deepslate|minecraft:deepslate_bricks", "minecraft:dark_oak_planks", "minecraft:dark_oak_door",
            "minecraft:dark_oak_fence");
    static final Style ROYAL = new Style("minecraft:dark_oak_log", "minecraft:stripped_spruce_log[axis=y]", "minecraft:spruce_stairs", "minecraft:deepslate_tile_stairs",
            "minecraft:polished_deepslate", "minecraft:stone_bricks|minecraft:mossy_stone_bricks|minecraft:stone_bricks", "minecraft:polished_andesite",
            "minecraft:dark_oak_door", "minecraft:dark_oak_fence");

    static final Style[] HOUSES = {SPRUCE, OAK, BIRCH, DARK, SPRUCE, OAK};

    // ------------------------------------------------------------------ 긴 집 (바이킹 롱하우스)

    /**
     * 긴 집: 설계 방향은 앞 (문) 이 남쪽 (z = D−1), 폭 W (홀수) · 길이 D · 벽 높이 H. 박공 지붕 (계단 — 처마 1칸 · 앞뒤 1칸 나옴) · 용마루 통나무 ·
     * 기둥 (모서리 · 4칸마다) · 창 · 문 · 벽 횃불 · 안 (화덕 · 의자 · 매단 등불 · 탁자 · 침대 · 책장).
     */
    static void longhouse(Blueprint b, int W, int D, int H, Style s, String npc) {
        int c = W / 2;
        int top = H + c + 1;
        b.air(-1, 1, -1, W, top + 1, D);
        b.fill(0, -2, 0, W - 1, -1, D - 1, "minecraft:cobblestone");
        b.fill(0, 0, 0, W - 1, 0, D - 1, s.foundation());
        b.fill(1, 0, 1, W - 2, 0, D - 2, s.floor());
        // 벽 · 아래 통나무 · 위 들보
        b.walls(0, 1, 0, W - 1, H, D - 1, s.planks());
        b.fill(0, 1, 0, 0, 1, D - 1, s.log() + "[axis=z]");
        b.fill(W - 1, 1, 0, W - 1, 1, D - 1, s.log() + "[axis=z]");
        b.fill(0, H, 0, 0, H, D - 1, s.log() + "[axis=z]");
        b.fill(W - 1, H, 0, W - 1, H, D - 1, s.log() + "[axis=z]");
        b.fill(0, 1, 0, W - 1, 1, 0, s.log() + "[axis=x]");
        b.fill(0, 1, D - 1, W - 1, 1, D - 1, s.log() + "[axis=x]");
        // 기둥 · 창
        for (int z = 0; z < D; z += 4) {
            int zz = Math.min(z, D - 1);
            b.fill(0, 1, zz, 0, H, zz, s.log() + "[axis=y]");
            b.fill(W - 1, 1, zz, W - 1, H, zz, s.log() + "[axis=y]");
        }
        b.fill(0, 1, D - 1, 0, H, D - 1, s.log() + "[axis=y]");
        b.fill(W - 1, 1, D - 1, W - 1, H, D - 1, s.log() + "[axis=y]");
        for (int z = 2; z < D - 2; z += 4) {
            if (H >= 4) {
                b.fill(0, 2, z, 0, 3, z, "minecraft:glass_pane");
                b.fill(W - 1, 2, z, W - 1, 3, z, "minecraft:glass_pane");
            }
        }
        if (W >= 7) {
            b.fill(c - 2, 2, 0, c - 2, 3, 0, "minecraft:glass_pane");
            b.fill(c + 2, 2, 0, c + 2, 3, 0, "minecraft:glass_pane");
        }
        // 박공 (앞뒤 세모 벽) · 둥근 창
        for (int k = 1; k <= c; k++) {
            b.fill(k, H + k, 0, W - 1 - k, H + k, 0, s.planks());
            b.fill(k, H + k, D - 1, W - 1 - k, H + k, D - 1, s.planks());
        }
        if (c >= 3) {
            b.set(c, H + 2, 0, "minecraft:glass_pane");
            b.set(c, H + 2, D - 1, "minecraft:glass_pane");
        }
        // 지붕 · 용마루 (앞뒤로 한 칸 더 — 박공 끝 장식)
        for (int k = 0; k <= c; k++) {
            b.fill(-1 + k, H + k, -1, -1 + k, H + k, D, s.roof() + "[facing=east,half=bottom]");
            b.fill(W - k, H + k, -1, W - k, H + k, D, s.roof() + "[facing=west,half=bottom]");
        }
        b.fill(c, top, -2, c, top, D + 1, s.ridge() + "[axis=z]");
        b.set(c, top + 1, -2, s.fence());
        b.set(c, top + 1, D + 1, s.fence());
        // 문 · 문 곁 횃불
        b.set(c, 1, D - 1, s.door() + "[facing=north,half=lower,hinge=left,open=false]");
        b.set(c, 2, D - 1, s.door() + "[facing=north,half=upper,hinge=left,open=false]");
        b.set(c - 1, 3, D, "minecraft:wall_torch[facing=south]");
        b.set(c + 1, 3, D, "minecraft:wall_torch[facing=south]");
        // 안: 화덕 · 의자 · 매단 등불 · 탁자 · 침대 · 책장
        int mid = D / 2;
        b.set(c, 1, mid, "minecraft:campfire[lit=true,signal_fire=false]");
        for (int z = 2; z < D - 2; z++) {
            if (Math.abs(z - mid) <= 1) continue;
            b.set(1, 1, z, s.stairs() + "[facing=west,half=bottom]");
            b.set(W - 2, 1, z, s.stairs() + "[facing=east,half=bottom]");
        }
        for (int z : new int[] {Math.max(2, D / 4), Math.min(D - 3, D - D / 4 - 1)}) {
            for (int y = H + 1; y <= H + c; y++) b.set(c, y, z, "minecraft:iron_chain[axis=y]");
            b.set(c, H, z, "minecraft:lantern[hanging=true]");
        }
        b.set(c, 1, 2, s.fence());
        b.set(c, 2, 2, "minecraft:spruce_pressure_plate");
        b.set(1, 1, 1, "minecraft:bookshelf");
        b.set(1, 2, 1, "minecraft:bookshelf");
        b.set(W - 2, 1, 1, "minecraft:red_bed[facing=north,part=head]");
        b.set(W - 2, 1, 2, "minecraft:red_bed[facing=north,part=foot]");
        b.set(c - 1, 1, 1, "minecraft:barrel[facing=up]");
        b.set(c + 1, 1, 1, "minecraft:crafting_table");
        if (npc != null) b.mark(npc, c + 0.5, 1, D - 3.5, "south");
    }

    // ------------------------------------------------------------------ 둥근 탑 (룬술사)

    /** 둥근 탑: 가운데 (0, 0), 반지름 R · 높이 H, 원뿔 지붕, 문은 남쪽, 안 (마법 부여대 · 책장 둘레 · 자수정) */
    static void tower(Blueprint b, int R, int H, String npc) {
        tower(b, R, H, npc, true);
    }

    /** 둥근 탑 (magic = 룬술사의 안 — 마법 부여대 · 책장 · 자수정 · 독서대) */
    static void tower(Blueprint b, int R, int H, String npc, boolean magic) {
        double ro = R + 0.3;
        for (int x = -R - 1; x <= R + 1; x++) {
            for (int z = -R - 1; z <= R + 1; z++) {
                double d = Math.hypot(x, z);
                if (d > ro + 1.2) continue;
                if (d <= ro) {
                    b.fill(x, -2, z, x, -1, z, "minecraft:cobblestone");
                    b.set(x, 0, z, d > ro - 1.2 ? "minecraft:stone_bricks" : "minecraft:polished_andesite");
                    b.air(x, 1, z, x, H, z);
                    if (d > ro - 1.2) b.fill(x, 1, z, x, H, z, "minecraft:stone_bricks|minecraft:stone_bricks|minecraft:mossy_stone_bricks|minecraft:cracked_stone_bricks");
                    else {
                        b.set(x, 7, z, "minecraft:spruce_planks");
                        b.set(x, 13, z, "minecraft:spruce_planks");
                    }
                }
                // 원뿔 지붕 (짙은 슬레이트 기와)
                double dr = ro + 1.2 - d;
                int ry = H + 1 + (int) Math.round(dr * 1.6);
                if (d <= ro + 1.2) {
                    b.fill(x, H + 1, z, x, ry, z, "minecraft:deepslate_tiles");
                    b.air(x, H + 1, z, x, Math.max(H, ry - 2), z);
                    b.set(x, ry, z, "minecraft:deepslate_tiles");
                }
            }
        }
        int peak = H + 1 + (int) Math.round((ro + 1.2) * 1.6);
        b.set(0, peak + 1, 0, "minecraft:lightning_rod[facing=up]");
        // 문 · 창 · 사다리 · 안
        b.air(0, 1, R, 0, 2, R);
        b.set(0, 1, R, "minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false]");
        b.set(0, 2, R, "minecraft:spruce_door[facing=north,half=upper,hinge=left,open=false]");
        for (int y : new int[] {4, 10, 16}) {
            b.set(R, y, 0, "minecraft:glass_pane");
            b.set(-R, y, 0, "minecraft:glass_pane");
            b.set(0, y, -R, "minecraft:glass_pane");
        }
        // 사다리 (서쪽 벽 안쪽 — 벽에 붙음, 층 바닥을 뚫고)
        for (int y = 1; y <= 13; y++) b.set(-R + 1, y, 0, "minecraft:ladder[facing=east]");
        if (magic) {
            b.set(0, 1, -1, "minecraft:enchanting_table");
            for (int x = -2; x <= 2; x++) {
                if (x == 0) continue;
                b.set(x, 1, -R + 2, "minecraft:bookshelf");
                b.set(x, 2, -R + 2, "minecraft:bookshelf");
            }
            b.set(2, 1, 1, "minecraft:amethyst_block");
            b.set(2, 2, 1, "minecraft:amethyst_cluster[facing=up]");
            b.set(-1, 1, 2, "minecraft:lectern[facing=south]");
        }
        for (int y : new int[] {6, 12}) b.set(0, y, 0, "minecraft:lantern[hanging=true]");
        if (npc != null) b.mark(npc, 0.5, 1, 1.5, "south");
    }

    // ------------------------------------------------------------------ 장터 가게 · 대장간 · 훈련장

    /** 장터 가게: 5 × 4, 기둥 넷 · 줄무늬 천막 · 앞 판매대 · 물건 */
    static void stall(Blueprint b, String cloth, String goods, String npc) {
        b.air(0, 1, 0, 4, 4, 3);
        b.fill(0, 0, 0, 4, 0, 3, "minecraft:spruce_planks");
        for (int[] p : new int[][] {{0, 0}, {4, 0}, {0, 3}, {4, 3}}) b.fill(p[0], 1, p[1], p[0], 3, p[1], "minecraft:spruce_fence");
        for (int x = 0; x <= 4; x++) b.fill(x, 4, 0, x, 4, 3, x % 2 == 0 ? cloth : "minecraft:white_wool");
        b.fill(1, 1, 3, 3, 1, 3, "minecraft:spruce_slab[type=top]");
        b.set(1, 2, 3, goods);
        b.set(3, 2, 3, "minecraft:barrel[facing=up]");
        b.set(0, 1, 1, "minecraft:barrel[facing=up]");
        if (npc != null) b.mark(npc, 2.5, 1, 1.5, "south");
    }

    /** 대장간: 앞이 트인 나무 틀 집 (W 11 × D 9) · 화로 (마그마 · 쇠창살) · 굴뚝 (연기) · 모루 · 숫돌 · 대장장이 작업대 · 용광로 */
    static void smithy(Blueprint b, String npc) {
        int W = 11;
        int D = 9;
        int H = 5;
        b.air(-1, 1, -1, W, H + 7, D);
        b.fill(0, -2, 0, W - 1, -1, D - 1, "minecraft:cobblestone");
        b.fill(0, 0, 0, W - 1, 0, D - 1, "minecraft:stone_bricks|minecraft:cobblestone|minecraft:stone_bricks");
        // 뒤 · 옆 벽 (돌 아래 · 나무 위), 앞은 기둥만
        b.fill(0, 1, 0, W - 1, 2, 0, "minecraft:cobblestone");
        b.fill(0, 1, 0, 0, 2, D - 1, "minecraft:cobblestone");
        b.fill(W - 1, 1, 0, W - 1, 2, D - 1, "minecraft:cobblestone");
        b.fill(0, 3, 0, W - 1, H, 0, "minecraft:spruce_planks");
        b.fill(0, 3, 0, 0, H, D - 1, "minecraft:spruce_planks");
        b.fill(W - 1, 3, 0, W - 1, H, D - 1, "minecraft:spruce_planks");
        b.air(1, 1, D - 1, W - 2, H, D - 1);
        for (int x : new int[] {0, 5, 10}) b.fill(x, 1, D - 1, x, H, D - 1, "minecraft:spruce_log[axis=y]");
        b.fill(0, H, D - 1, W - 1, H, D - 1, "minecraft:spruce_log[axis=x]");
        // 한쪽 지붕 (뒤가 높음)
        for (int z = -1; z <= D; z++) {
            int y = H + 1 + (D - z) / 3;
            b.fill(-1, y, z, W, y, z, "minecraft:spruce_slab[type=bottom]");
            b.fill(-1, H + 1, z, W, y - 1, z, z == -1 || z == D ? "minecraft:air" : "minecraft:spruce_planks");
        }
        // 화로 · 굴뚝
        b.fill(1, 1, 1, 3, 1, 3, "minecraft:bricks");
        b.set(2, 1, 2, "minecraft:magma_block");
        b.fill(1, 2, 1, 3, 2, 3, "minecraft:bricks");
        b.set(2, 2, 2, "minecraft:air");
        b.set(2, 2, 3, "minecraft:iron_bars");
        b.fill(1, 3, 1, 3, H + 6, 3, "minecraft:bricks");
        b.fill(2, 3, 2, 2, H + 5, 2, "minecraft:air");
        b.set(2, H + 5, 2, "minecraft:campfire[lit=true,signal_fire=true]");
        b.set(2, H + 6, 2, "minecraft:air");
        b.set(5, 1, 3, "minecraft:anvil[facing=east]");
        b.set(7, 1, 1, "minecraft:grindstone[face=floor,facing=south]");
        b.set(8, 1, 1, "minecraft:smithing_table");
        b.set(9, 1, 1, "minecraft:blast_furnace[facing=south,lit=true]");
        b.set(9, 1, 3, "minecraft:barrel[facing=up]");
        b.set(9, 2, 3, "minecraft:iron_block");
        b.set(5, H, 4, "minecraft:lantern[hanging=true]");
        if (npc != null) b.mark(npc, 6.5, 1, 4.5, "south");
    }

    /** 훈련장: 울타리 둘레 (W × D) · 바닥 (거친 흙 · 자갈) · 과녁 (화살 과녁 · 건초) · 허수아비 자리표 · 무기 받침 */
    static void trainingGround(Blueprint b, int W, int D, String npc) {
        b.air(0, 1, 0, W - 1, 4, D - 1);
        b.fill(0, 0, 0, W - 1, 0, D - 1, "minecraft:coarse_dirt|minecraft:dirt_path|minecraft:gravel|minecraft:coarse_dirt");
        b.walls(0, 1, 0, W - 1, 1, D - 1, "minecraft:oak_fence");
        b.air(W / 2 - 1, 1, D - 1, W / 2 + 1, 1, D - 1);
        b.air(0, 1, D / 2, 0, 1, D / 2 + 1);
        for (int[] p : new int[][] {{0, 0}, {W - 1, 0}, {0, D - 1}, {W - 1, D - 1}, {W / 2 - 2, D - 1}, {W / 2 + 2, D - 1}}) {
            b.fill(p[0], 1, p[1], p[0], 2, p[1], "minecraft:oak_log[axis=y]");
            b.set(p[0], 3, p[1], "minecraft:lantern[hanging=false]");
        }
        // 활터 (뒤쪽): 건초 위 과녁
        for (int x = 4; x < W - 3; x += 5) {
            b.set(x, 1, 2, "minecraft:hay_block[axis=y]");
            b.set(x, 2, 2, "minecraft:target");
        }
        // 허수아비 자리 (게임이 훈련용 허수아비를 세움)
        int n = 0;
        for (int x = 5; x < W - 4 && n < 6; x += 6) {
            b.mark("dummy", x + 0.5, 1, D / 2 + 1.5, "south");
            n++;
        }
        // 무기 받침
        b.fill(2, 1, D - 3, 2, 2, D - 3, "minecraft:spruce_fence");
        b.set(2, 3, D - 3, "minecraft:spruce_slab[type=bottom]");
        if (npc != null) b.mark(npc, W / 2 + 0.5, 1, D - 4.5, "south");
    }

    /** 이동 지점 돌 (광장 · 거점): 3 × 3 받침 · 자석석 · 끝막대 빛 · 영혼 등불 넷 */
    static void waystone(Blueprint b, String id) {
        b.air(-1, 1, -1, 1, 4, 1);
        b.fill(-1, 0, -1, 1, 0, 1, "minecraft:chiseled_stone_bricks");
        b.fill(-1, 1, -1, 1, 1, 1, "minecraft:polished_andesite_slab[type=bottom]");
        b.set(0, 1, 0, "minecraft:polished_andesite");
        b.set(0, 2, 0, "minecraft:lodestone");
        b.set(0, 3, 0, "minecraft:end_rod[facing=up]");
        for (int[] p : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) b.set(p[0], 2, p[1], "minecraft:soul_lantern[hanging=false]");
        b.mark(id, 0.5, 2, 0.5, "south");
    }

    // ------------------------------------------------------------------ 마을 바닥 (광장 · 큰길 · 둘레 길 · 성벽 · 탑)

    /** 마을 바닥 조각: 칸마다 광장 · 길 · 성벽 · 탑을 셈 */
    static final class Ground implements FieldBuild.Piece {
        final int cx;
        final int cz;
        final int g;
        static final int WALL = 104;
        static final int PLAZA = 18;
        static final int RING = 56;
        /** 성벽 밖 길 (다리에서 오는 길과 이어짐) 끝 */
        static final int OUTER = 150;

        Ground(int cx, int cz, int g) {
            this.cx = cx;
            this.cz = cz;
            this.g = g;
        }

        @Override
        public int minX() {
            return cx - OUTER;
        }

        @Override
        public int maxX() {
            return cx + OUTER;
        }

        @Override
        public int minZ() {
            return cz - OUTER;
        }

        @Override
        public int maxZ() {
            return cz + OUTER;
        }

        private static String pick(String palette, int x, int y, int z) {
            String[] p = palette.split("\\|");
            return p[(int) (FieldNoise.hash(x, y * 131L + z, 23) * p.length)];
        }

        @Override
        public void column(int x, int z, FieldTerrain.Column ground, FieldBuild.Out out) {
            int dx = x - cx;
            int dz = z - cz;
            double r = Math.hypot(dx + 0.5, dz + 0.5);
            if (r > OUTER) return;
            if (r > WALL + 9) {
                // 성벽 밖: 큰길이 흙길로 이어짐 (마을 밖 길과 만남)
                if (Math.abs(dx) <= 2 || Math.abs(dz) <= 2) {
                    out.set(g, pick("minecraft:dirt_path|minecraft:dirt_path|minecraft:coarse_dirt", x, g, z));
                    out.set(g + 1, "minecraft:air");
                    out.set(g + 2, "minecraft:air");
                }
                return;
            }
            int ax = Math.abs(dx);
            int az = Math.abs(dz);
            boolean avenue = (ax <= 2 || az <= 2) && r > PLAZA - 1 && r < WALL + 9;
            // 성벽 · 문 · 탑
            double tower = towerDist(dx, dz);
            if (tower < 5.6) {
                towerColumn(x, z, tower, out);
                return;
            }
            boolean gateTower = r >= WALL - 5 && r <= WALL + 5 && (between(ax, 4, 9) && az > 20 || between(az, 4, 9) && ax > 20);
            if (gateTower && (ax <= 9 || az <= 9)) {
                for (int y = g - 1; y <= g + 11; y++) out.set(y, pick("minecraft:stone_bricks|minecraft:stone_bricks|minecraft:mossy_stone_bricks|minecraft:cracked_stone_bricks", x, y, z));
                boolean edge = r >= WALL + 4 || r <= WALL - 4 || ax == 4 || ax == 9 || az == 4 || az == 9;
                if (edge && (x + z) % 2 == 0) out.set(g + 12, "minecraft:stone_bricks");
                else if (edge) out.set(g + 12, "minecraft:stone_brick_wall");
                if (edge && (ax == 9 || az == 9) && Math.abs(r - WALL) < 1) out.set(g + 6, "minecraft:air");
                return;
            }
            if (r >= WALL - 2 && r < WALL + 2) {
                boolean gate = ax <= 3 || az <= 3;
                out.set(g - 1, "minecraft:cobblestone");
                if (gate) {
                    out.set(g, pick("minecraft:stone_bricks|minecraft:cobblestone", x, g, z));
                    for (int y = g + 1; y <= g + 5; y++) out.set(y, "minecraft:air");
                    for (int y = g + 6; y <= g + 8; y++) out.set(y, "minecraft:stone_bricks");
                    if ((ax == 0 || az == 0) && r >= WALL + 1) out.set(g + 5, "minecraft:lantern[hanging=true]");
                    return;
                }
                for (int y = g; y <= g + 1; y++) out.set(y, pick("minecraft:cobblestone|minecraft:mossy_cobblestone|minecraft:cobblestone", x, y, z));
                for (int y = g + 2; y <= g + 7; y++) {
                    out.set(y, pick("minecraft:stone_bricks|minecraft:stone_bricks|minecraft:mossy_stone_bricks|minecraft:cracked_stone_bricks", x, y, z));
                }
                if (r >= WALL + 1) {
                    long step = Math.round(Math.atan2(dz, dx) * WALL / 2.0);
                    if ((step & 1) == 0) out.set(g + 8, "minecraft:stone_bricks");
                    else out.set(g + 8, "minecraft:stone_brick_slab[type=bottom]");
                }
                return;
            }
            // 광장
            if (r < PLAZA) {
                plaza(x, z, dx, dz, r, out);
                return;
            }
            // 큰길 · 가로등
            if (avenue && r < WALL - 2) {
                int off = ax <= 2 ? ax : az;
                out.set(g, off == 2 ? "minecraft:stone_bricks" : pick("minecraft:cobblestone|minecraft:stone_bricks|minecraft:andesite|minecraft:cobblestone", x, g, z));
                out.set(g + 1, "minecraft:air");
                out.set(g + 2, "minecraft:air");
                return;
            }
            int lampOff = ax == 3 && az > PLAZA ? az : az == 3 && ax > PLAZA ? ax : -1;
            if (lampOff > 0 && lampOff % 14 == 0 && r < WALL - 6) {
                boolean side = (ax == 3 ? dx : dz) > 0;
                if (side == ((lampOff / 14) % 2 == 0)) {
                    out.set(g + 1, "minecraft:dark_oak_fence");
                    out.set(g + 2, "minecraft:dark_oak_fence");
                    out.set(g + 3, "minecraft:dark_oak_fence");
                    out.set(g + 4, "minecraft:lantern[hanging=false]");
                    return;
                }
            }
            // 둘레 길
            if (Math.abs(r - RING) < 1.6) {
                out.set(g, pick("minecraft:dirt_path|minecraft:dirt_path|minecraft:gravel", x, g, z));
                out.set(g + 1, "minecraft:air");
                out.set(g + 2, "minecraft:air");
            }
        }

        private static boolean between(int v, int lo, int hi) {
            return v >= lo && v <= hi;
        }

        /** 모서리 둥근 탑 넷 (대각선 · 성벽 위) 까지 거리 */
        private double towerDist(int dx, int dz) {
            double best = 99;
            double k = WALL / Math.sqrt(2);
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) best = Math.min(best, Math.hypot(dx + 0.5 - sx * k, dz + 0.5 - sz * k));
            }
            return best;
        }

        private void towerColumn(int x, int z, double d, FieldBuild.Out out) {
            out.set(g - 1, "minecraft:cobblestone");
            out.set(g, "minecraft:stone_bricks");
            if (d > 3.8) {
                for (int y = g + 1; y <= g + 12; y++) {
                    out.set(y, pick("minecraft:stone_bricks|minecraft:stone_bricks|minecraft:mossy_stone_bricks|minecraft:cracked_stone_bricks", x, y, z));
                }
                if (d > 4.6 && (x + z) % 2 == 0) out.set(g + 13, "minecraft:stone_bricks");
            } else {
                for (int y = g + 1; y <= g + 11; y++) out.set(y, "minecraft:air");
                out.set(g + 12, "minecraft:spruce_planks");
                // 지붕 (원뿔) — 바닥 위 깃대
                int ry = g + 14 + (int) Math.round((3.8 - d) * 1.5);
                for (int y = g + 13; y <= ry; y++) out.set(y, y == ry ? "minecraft:deepslate_tiles" : "minecraft:air");
                if (d < 0.8) {
                    out.set(ry + 1, "minecraft:dark_oak_fence");
                    out.set(ry + 2, "minecraft:dark_oak_fence");
                    out.set(ry + 3, "minecraft:red_wool");
                }
            }
        }

        private void plaza(int x, int z, int dx, int dz, double r, FieldBuild.Out out) {
            out.set(g - 1, "minecraft:stone_bricks");
            if (r < 4.6) {
                out.set(g - 2, "minecraft:stone_bricks");
                out.set(g - 1, "minecraft:sea_lantern");
                out.set(g, "minecraft:water");
                out.set(g + 1, "minecraft:air");
                if (r < 1.2) {
                    out.set(g - 1, "minecraft:stone_bricks");
                    out.set(g, "minecraft:chiseled_stone_bricks");
                    out.set(g + 1, "minecraft:stone_brick_wall");
                    out.set(g + 2, "minecraft:stone_brick_wall");
                    out.set(g + 3, "minecraft:chiseled_stone_bricks");
                    out.set(g + 4, "minecraft:sea_lantern");
                }
                return;
            }
            if (r < 5.6) {
                out.set(g, "minecraft:stone_bricks");
                out.set(g + 1, "minecraft:smooth_stone_slab[type=bottom]");
                out.set(g + 2, "minecraft:air");
                return;
            }
            int band = (int) r;
            out.set(g, band % 4 == 0 ? "minecraft:polished_andesite"
                    : pick("minecraft:stone_bricks|minecraft:andesite|minecraft:stone_bricks|minecraft:cracked_stone_bricks", x, g, z));
            out.set(g + 1, "minecraft:air");
            out.set(g + 2, "minecraft:air");
            // 광장 등불 여덟 (큰길 사이)
            double deg = Math.toDegrees(Math.atan2(dz + 0.5, dx + 0.5));
            double rel = ((deg - 22.5) % 45 + 45) % 45;
            if (r > 15.2 && r < 16.4 && (rel < 3 || rel > 42)) {
                out.set(g + 1, "minecraft:dark_oak_fence");
                out.set(g + 2, "minecraft:dark_oak_fence");
                out.set(g + 3, "minecraft:dark_oak_fence");
                out.set(g + 4, "minecraft:lantern[hanging=false]");
            }
        }
    }

    // ------------------------------------------------------------------ 나무 · 꽃밭 · 우물 · 밭

    /** 작은 나무 (참나무 · 자작나무 · 가문비 — 잎은 떨어지지 않게) : 밑동 (0, 0) */
    static void tree(Blueprint b, int kind, int h) {
        String log = kind == 1 ? "minecraft:birch_log[axis=y]" : kind == 2 ? "minecraft:spruce_log[axis=y]" : "minecraft:oak_log[axis=y]";
        String leaf = (kind == 1 ? "minecraft:birch_leaves" : kind == 2 ? "minecraft:spruce_leaves" : "minecraft:oak_leaves") + "[persistent=true]";
        b.set(0, 0, 0, "minecraft:dirt");
        if (kind == 2) {
            for (int y = 2; y <= h + 1; y++) {
                int r = Math.max(0, (h + 1 - y) / 2);
                for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) if (Math.abs(x) + Math.abs(z) <= r + 1) b.set(x, y, z, leaf);
            }
            b.set(0, h + 2, 0, leaf);
        } else {
            for (int y = h - 1; y <= h + 1; y++) {
                int r = y == h + 1 ? 1 : 2;
                for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) if (x * x + z * z <= r * r + 1) b.set(x, y, z, leaf);
            }
            b.set(0, h + 2, 0, leaf);
        }
        b.fill(0, 1, 0, 0, h, 0, log);
    }

    /** 꽃밭 (풀 위 꽃 — 5 × 2) */
    static void flowers(Blueprint b, int seed) {
        String[] f = {"minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:allium", "minecraft:oxeye_daisy", "minecraft:azure_bluet"};
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 2; z++) {
                b.set(x, 0, z, "minecraft:grass_block");
                b.set(x, 1, z, f[(seed + x * 3 + z) % f.length]);
            }
        }
    }

    /** 우물: 3 × 3 돌 둘레 · 물 · 기둥 넷 · 지붕 */
    static void well(Blueprint b) {
        b.air(-2, 1, -2, 2, 5, 2);
        b.fill(-2, -3, -2, 2, 0, 2, "minecraft:stone_bricks");
        b.fill(-1, -2, -1, 1, 0, 1, "minecraft:water");
        b.fill(-2, 1, -2, 2, 1, 2, "minecraft:stone_bricks|minecraft:mossy_stone_bricks");
        b.fill(-1, 1, -1, 1, 1, 1, "minecraft:air");
        for (int[] p : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) b.fill(p[0], 2, p[1], p[0], 3, p[1], "minecraft:spruce_fence");
        b.fill(-2, 4, -2, 2, 4, 2, "minecraft:spruce_slab[type=bottom]");
        b.fill(-1, 4, -1, 1, 4, 1, "minecraft:spruce_planks");
        b.set(0, 3, 0, "minecraft:lantern[hanging=true]");
    }

    /** 밭: W × D, 9칸마다 물길 · 작물 (다 자람) · 둘레 울타리 */
    static void field(Blueprint b, int W, int D, String crop) {
        b.air(0, 1, 0, W - 1, 2, D - 1);
        b.fill(0, -1, 0, W - 1, -1, D - 1, "minecraft:dirt");
        for (int x = 0; x < W; x++) {
            boolean water = x % 9 == 4;
            b.fill(x, 0, 0, x, 0, D - 1, water ? "minecraft:water" : "minecraft:farmland[moisture=7]");
            if (!water) b.fill(x, 1, 0, x, 1, D - 1, crop);
        }
        b.walls(-1, 1, -1, W, 1, D, "minecraft:oak_fence");
        b.air(W / 2, 1, D, W / 2 + 1, 1, D);
        b.fill(-1, 0, -1, W, 0, -1, "minecraft:dirt_path");
        b.fill(-1, 0, D, W, 0, D, "minecraft:dirt_path");
    }

    // ------------------------------------------------------------------ 마을 전체

    /** 차지한 칸 (마을 가운데 기준 ± 152) — 건물 · 길 · 나무가 겹치지 않게 */
    static final class Occ {
        static final int R = 152;
        final boolean[] used = new boolean[(2 * R + 1) * (2 * R + 1)];
        final int cx;
        final int cz;

        Occ(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
        }

        boolean free(int x1, int z1, int x2, int z2) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    int dx = x - cx;
                    int dz = z - cz;
                    if (Math.abs(dx) > R || Math.abs(dz) > R) return false;
                    if (used[(dx + R) * (2 * R + 1) + dz + R]) return false;
                }
            }
            return true;
        }

        void take(int x1, int z1, int x2, int z2) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    int dx = x - cx;
                    int dz = z - cz;
                    if (Math.abs(dx) <= R && Math.abs(dz) <= R) used[(dx + R) * (2 * R + 1) + dz + R] = true;
                }
            }
        }

        /** 설계의 상자 둘레 (+ 여백) 를 무조건 차지 (정해진 건물 — 길 곁이어도) */
        void force(Blueprint b, int margin) {
            int[] r = bounds(b);
            take(r[0] - margin, r[2] - margin, r[1] + margin, r[3] + margin);
        }

        static int[] bounds(Blueprint b) {
            int x1 = Integer.MAX_VALUE;
            int x2 = Integer.MIN_VALUE;
            int z1 = Integer.MAX_VALUE;
            int z2 = Integer.MIN_VALUE;
            for (Blueprint.Box x : b.boxes()) {
                x1 = Math.min(x1, x.x1());
                x2 = Math.max(x2, x.x2());
                z1 = Math.min(z1, x.z1());
                z2 = Math.max(z2, x.z2());
            }
            return new int[] {x1, x2, z1, z2};
        }

        /** 설계의 상자 둘레 (+ 여백) 가 비었으면 차지하고 true */
        boolean place(Blueprint b, int margin) {
            int x1 = Integer.MAX_VALUE;
            int x2 = Integer.MIN_VALUE;
            int z1 = Integer.MAX_VALUE;
            int z2 = Integer.MIN_VALUE;
            for (Blueprint.Box x : b.boxes()) {
                x1 = Math.min(x1, x.x1());
                x2 = Math.max(x2, x.x2());
                z1 = Math.min(z1, x.z1());
                z2 = Math.max(z2, x.z2());
            }
            if (!free(x1 - margin, z1 - margin, x2 + margin, z2 + margin)) return false;
            take(x1 - margin, z1 - margin, x2 + margin, z2 + margin);
            return true;
        }
    }

    /** 마을: 가운데 (cx, cz) · 바닥 높이 g */
    public static Result build(int cx, int cz, int g) {
        List<FieldBuild.Piece> pieces = new ArrayList<>();
        List<Blueprint.Mark> marks = new ArrayList<>();
        pieces.add(new Ground(cx, cz, g));
        List<Blueprint> bps = new ArrayList<>();
        Occ occ = new Occ(cx, cz);
        // 길 · 광장 · 성벽 자리
        for (int dx = -Occ.R; dx <= Occ.R; dx++) {
            for (int dz = -Occ.R; dz <= Occ.R; dz++) {
                double r = Math.hypot(dx + 0.5, dz + 0.5);
                boolean avenue = (Math.abs(dx) <= 3 || Math.abs(dz) <= 3) && r < Ground.WALL + 30;
                if (r < Ground.PLAZA + 2 || avenue || Math.abs(r - Ground.RING) < 3 || Math.abs(r - Ground.WALL) < 7) occ.take(cx + dx, cz + dz, cx + dx, cz + dz);
            }
        }
        // 광장의 이동 지점 (북서)
        Blueprint way = new Blueprint(cx - 10, g, cz - 10, "south");
        waystone(way, "waystone_midgard");
        bps.add(way);
        // 정해진 건물 (먼저 — 자리를 차지)
        Blueprint[] fixed = {
            front(cx - 5, g, cz - 30, "east", 15, 27),   // 왕녀의 전당
            front(cx + 5, g, cz - 26, "west", 9, 13),    // 하랄의 집
            new Blueprint(cx + 30, g, cz - 30, "south"), // 훈련장
            front(cx + 82, g, cz - 20, "west", 9, 11),   // 엘린
            new Blueprint(cx - 36, g, cz + 26, "east"),  // 룬술사의 탑
            front(cx - 30, g, cz - 6, "south", 9, 11),   // 볼바
            front(cx + 5, g, cz + 30, "west", 11, 9),    // 대장간
            front(cx - 5, g, cz + 30, "east", 11, 17),   // 여관
            front(cx - 36, g, cz + 70, "north", 11, 13)  // 직업 교관
        };
        longhouseRoyal(fixed[0], 15, 27, 7, "npc_11");
        longhouse(fixed[1], 9, 13, 4, SPRUCE, "npc_1");
        trainingGround(fixed[2], 46, 24, "npc_2");
        longhouse(fixed[3], 9, 11, 4, BIRCH, "npc_7");
        tower(fixed[4], 6, 18, "npc_12");
        longhouse(fixed[5], 9, 11, 4, DARK, "npc_8");
        fixed[5].set(4, 1, 2, "minecraft:amethyst_block");
        fixed[5].set(4, 2, 2, "minecraft:amethyst_cluster[facing=up]");
        smithy(fixed[6], "npc_9");
        longhouse(fixed[7], 11, 17, 5, OAK, "npc_10");
        longhouse(fixed[8], 11, 13, 5, DARK, "job");
        for (Blueprint b : fixed) {
            occ.force(b, 2);
            bps.add(b);
        }
        // 무기 스승의 집 넷 (훈련장 북쪽 — 앞 = 남쪽)
        String[] mentors = {"npc_3", "npc_4", "npc_5", "npc_6"};
        Style[] mentorStyle = {SPRUCE, DARK, OAK, BIRCH};
        for (int i = 0; i < mentors.length; i++) {
            Blueprint m = front(cx + 36 + i * 12, g, cz - 34, "south", 9, 11);
            longhouse(m, 9, 11, 4, mentorStyle[i], mentors[i]);
            occ.force(m, 1);
            bps.add(m);
        }
        // 장터 (남동): 돌 바닥 마당 · 가운데 우물 · 가게 둘레
        int mx = cx + 34;
        int mz = cz + 30;
        Blueprint square = new Blueprint(mx - 14, g, mz - 12, "south");
        square.fill(0, 0, 0, 28, 0, 24, "minecraft:stone_bricks|minecraft:andesite|minecraft:polished_andesite|minecraft:cobblestone");
        square.air(0, 1, 0, 28, 2, 24);
        occ.force(square, 1);
        bps.add(square);
        Blueprint w = new Blueprint(mx, g, mz, "south");
        well(w);
        bps.add(w);
        String[][] stalls = {{"minecraft:red_wool", "minecraft:melon", "shop_0"}, {"minecraft:blue_wool", "minecraft:pumpkin", "shop_5"},
                {"minecraft:green_wool", "minecraft:hay_block[axis=y]", "shop_10"}, {"minecraft:yellow_wool", "minecraft:bookshelf", null},
                {"minecraft:purple_wool", "minecraft:amethyst_block", null}, {"minecraft:orange_wool", "minecraft:pumpkin", null}};
        int[][] stallAt = {{mx - 10, mz - 9, 0}, {mx - 2, mz - 9, 0}, {mx + 6, mz - 9, 0}, {mx - 10, mz + 9, 2}, {mx - 2, mz + 9, 2}, {mx + 6, mz + 9, 2}};
        for (int i = 0; i < stalls.length; i++) {
            Blueprint st = front(stallAt[i][0] + 2, g, stallAt[i][1], stallAt[i][2] == 0 ? "south" : "north", 5, 4);
            stall(st, stalls[i][0], stalls[i][1], stalls[i][2]);
            bps.add(st);
        }
        // 집: 둘레 길 안쪽 · 바깥쪽 고리 (길을 보게) — 빈 자리만
        int n = 0;
        double[][] rings = {{31, 16}, {44, 11}, {68, 7}, {81, 6}, {94, 5.5}};
        for (double[] ring : rings) {
            for (double deg = 4; deg < 360; deg += ring[1]) {
                double a = Math.toRadians(deg + n * 1.7);
                int hx = cx + (int) Math.round(Math.cos(a) * ring[0]);
                int hz = cz + (int) Math.round(Math.sin(a) * ring[0]);
                double toward = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? Math.cos(a) : Math.sin(a);
                boolean xAxis = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a));
                // 안쪽 고리는 바깥 (둘레 길) 을, 바깥 고리는 안쪽 (둘레 길) 을 봄
                boolean faceOut = ring[0] < Ground.RING;
                String facing = xAxis ? ((toward > 0) == faceOut ? "east" : "west") : ((toward > 0) == faceOut ? "south" : "north");
                int W = n % 3 == 0 ? 7 : 9;
                int D = 9 + (n % 4) * 2;
                Blueprint h = front(hx, g, hz, facing, W, D);
                longhouse(h, W, D, 4, HOUSES[n % HOUSES.length], null);
                if (occ.place(h, 2)) {
                    bps.add(h);
                    n++;
                }
            }
        }
        // 빈 자리 채우기: 12칸 격자 — 자리마다 조금씩 옮겨 가며 (아홉 곳 · 작은 집 먼저 큰 집) 들어가는 곳에, 가까운 길 (큰길 · 둘레 길) 을 봄
        int[][] nudge = {{0, 0}, {4, 0}, {-4, 0}, {0, 4}, {0, -4}, {4, 4}, {-4, -4}, {4, -4}, {-4, 4}};
        for (int gx = -Ground.WALL; gx <= Ground.WALL; gx += 12) {
            for (int gz = -Ground.WALL; gz <= Ground.WALL; gz += 12) {
                boolean done = false;
                for (int[] nd : nudge) {
                    if (done) break;
                    int hx = cx + gx + nd[0];
                    int hz = cz + gz + nd[1];
                    int dx = hx - cx;
                    int dz = hz - cz;
                    double r = Math.hypot(dx, dz);
                    if (r > Ground.WALL - 12 || r < Ground.PLAZA + 6) continue;
                    String facing;
                    double toRing = Math.abs(r - Ground.RING);
                    int toAve = Math.min(Math.abs(dx), Math.abs(dz));
                    if (toAve < toRing) facing = Math.abs(dx) < Math.abs(dz) ? (dx > 0 ? "west" : "east") : (dz > 0 ? "north" : "south");
                    else {
                        boolean inside = r < Ground.RING;
                        boolean xAxis = Math.abs(dx) > Math.abs(dz);
                        int sgn = xAxis ? Integer.signum(dx) : Integer.signum(dz);
                        facing = xAxis ? ((sgn > 0) == inside ? "east" : "west") : ((sgn > 0) == inside ? "south" : "north");
                    }
                    for (int[] size : new int[][] {{9, 11 + (n % 3) * 2}, {7, 9}}) {
                        Blueprint h = front(hx, g, hz, facing, size[0], size[1]);
                        longhouse(h, size[0], size[1], 4, HOUSES[n % HOUSES.length], null);
                        if (occ.place(h, 1)) {
                            bps.add(h);
                            n++;
                            done = true;
                            break;
                        }
                    }
                }
            }
        }
        // 성벽 밖 밭 넷 (대각선) · 목장
        String[] crops = {"minecraft:wheat[age=7]", "minecraft:carrots[age=7]", "minecraft:potatoes[age=7]", "minecraft:beetroots[age=3]"};
        int f = 0;
        for (double deg : new double[] {28, 62, 118, 152, 208, 242, 298, 332}) {
            double a = Math.toRadians(deg);
            int fx = cx + (int) Math.round(Math.cos(a) * 128) - 9;
            int fz = cz + (int) Math.round(Math.sin(a) * 128) - 6;
            Blueprint fb = new Blueprint(fx, g, fz, "south");
            field(fb, 19, 12, crops[f % crops.length]);
            if (occ.place(fb, 1)) {
                bps.add(fb);
                f++;
            }
        }
        // 나무 · 꽃밭 (성벽 안 빈 자리)
        for (int gx = -Ground.WALL; gx <= Ground.WALL; gx += 11) {
            for (int gz = -Ground.WALL; gz <= Ground.WALL; gz += 11) {
                double jx = FieldNoise.hash(gx, gz, 41) * 6 - 3;
                double jz = FieldNoise.hash(gx, gz, 42) * 6 - 3;
                int x = cx + gx + (int) jx;
                int z = cz + gz + (int) jz;
                double r = Math.hypot(x - cx, z - cz);
                if (r > Ground.WALL - 9 || r < Ground.PLAZA + 4) continue;
                double roll = FieldNoise.hash(gx, gz, 43);
                if (roll < 0.42) {
                    Blueprint t = new Blueprint(x, g, z, "south");
                    int kind = (int) (FieldNoise.hash(gx, gz, 44) * 3);
                    tree(t, kind, 4 + (int) (FieldNoise.hash(gx, gz, 45) * 2));
                    if (occ.place(t, 1)) bps.add(t);
                } else if (roll < 0.62) {
                    Blueprint fl = new Blueprint(x, g, z, "south");
                    flowers(fl, (int) (roll * 100));
                    if (occ.place(fl, 1)) bps.add(fl);
                }
            }
        }
        for (Blueprint bp : bps) {
            pieces.add(bp.piece("town"));
            marks.addAll(bp.marks());
        }
        return new Result(pieces, marks);
    }

    /** 앞 가운데 (문 앞 칸) 가 (fx, fz) 가 되게 설계 (폭 W · 길이 D) 를 둠 */
    static Blueprint front(int fx, int y, int fz, String facing, int W, int D) {
        int lx = W / 2;
        int lz = D - 1;
        int ox = fx - switch (facing) {
            case "west" -> -lz;
            case "north" -> -lx;
            case "east" -> lz;
            default -> lx;
        };
        int oz = fz - switch (facing) {
            case "west" -> lx;
            case "north" -> -lz;
            case "east" -> -lx;
            default -> lz;
        };
        return new Blueprint(ox, y, oz, facing);
    }

    /** 왕녀의 전당: 긴 집 (돌 기단 · 높은 벽 · 기와 지붕) + 안 (붉은 길 · 기둥 · 왕좌 · 벽걸이 천) */
    static void longhouseRoyal(Blueprint b, int W, int D, int H, String npc) {
        longhouse(b, W, D, H, ROYAL, null);
        int c = W / 2;
        b.fill(0, 1, 0, W - 1, 1, D - 1, "minecraft:stone_bricks");
        b.air(1, 1, 1, W - 2, 1, D - 2);
        b.set(c, 1, D - 1, "minecraft:dark_oak_door[facing=north,half=lower,hinge=left,open=false]");
        b.set(c, 2, D - 1, "minecraft:dark_oak_door[facing=north,half=upper,hinge=left,open=false]");
        b.air(1, 1, 1, W - 2, 1, D - 2);
        b.fill(c - 1, 0, 1, c + 1, 0, D - 2, "minecraft:red_wool");
        for (int z = 4; z < D - 3; z += 5) {
            b.fill(2, 1, z, 2, H, z, "minecraft:dark_oak_log[axis=y]");
            b.fill(W - 3, 1, z, W - 3, H, z, "minecraft:dark_oak_log[axis=y]");
            b.set(3, H - 1, z, "minecraft:lantern[hanging=false]");
            b.set(W - 4, H - 1, z, "minecraft:lantern[hanging=false]");
            b.set(1, 3, z, "minecraft:red_wool");
            b.set(1, 4, z, "minecraft:red_wool");
            b.set(W - 2, 3, z, "minecraft:red_wool");
            b.set(W - 2, 4, z, "minecraft:red_wool");
        }
        // 왕좌 (뒤쪽 가운데)
        b.fill(c - 2, 1, 1, c + 2, 1, 3, "minecraft:polished_deepslate");
        b.set(c, 2, 2, "minecraft:polished_blackstone_stairs[facing=north,half=bottom]");
        b.set(c, 3, 1, "minecraft:gold_block");
        b.set(c - 1, 2, 2, "minecraft:gold_block");
        b.set(c + 1, 2, 2, "minecraft:gold_block");
        b.set(c - 2, 2, 1, "minecraft:soul_lantern[hanging=false]");
        b.set(c + 2, 2, 1, "minecraft:soul_lantern[hanging=false]");
        if (npc != null) b.mark(npc, c + 0.5, 2, 4.5, "south");
    }
}
