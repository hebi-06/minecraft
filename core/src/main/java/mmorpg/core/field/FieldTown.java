package mmorpg.core.field;

/**
 * 마을 · 거점의 건물 설계 (2026-11-07 섬 맵): 긴 집 (차림 다섯) · 둥근 탑 · 장터 가게 · 대장간 · 훈련장 · 이동 지점 돌 · 나무 · 꽃밭 · 우물 · 밭 · 왕녀의 전당.
 * 마을 배치는 FieldCity, 큰 건물 (성당 · 마탑 · 여신상 · 큰 분수 · 모험가 길드 · 무기소) 은 FieldLandmarks, 줄집은 FieldRows.
 * 건물마다 NPC 자리표 (Blueprint.Mark — 메인 퀘스트 NPC 번호 npc_1 … · 상인 shop_* · 직업 교관 job · 허수아비 dummy · 이동 지점 waystone_*).
 */
public final class FieldTown {
    private FieldTown() {}

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
            "minecraft:polished_basalt", "minecraft:cobbled_deepslate|minecraft:deepslate_bricks", "minecraft:dark_oak_planks", "minecraft:dark_oak_door",
            "minecraft:dark_oak_fence");
    static final Style ROYAL = new Style("minecraft:dark_oak_log", "minecraft:stripped_spruce_log[axis=y]", "minecraft:spruce_stairs", "minecraft:deepslate_tile_stairs",
            "minecraft:polished_basalt", "minecraft:stone_bricks|minecraft:mossy_stone_bricks|minecraft:stone_bricks", "minecraft:polished_andesite",
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
