package mmorpg.core.field;

import java.util.ArrayList;
import java.util.List;

/**
 * 섬마다의 거점 (2026-11-07 섬 맵): 올라프의 농가 (농장 섬) · 라그나르의 오아시스 (사막 섬) · 비요른의 항구 (바다 섬) · 스카디의 사냥막 (북쪽 섬) ·
 * 균열 전초기지 (공허 섬) · 불꽃 전초기지 (무스펠 섬). 거점마다 지역 NPC 자리표 (npc_13 …) · 상인 (shop_*) · 이동 지점 (waystone_*).
 */
public final class FieldCamps {
    private FieldCamps() {}

    // ------------------------------------------------------------------ 작은 설계

    /** 천막: 5 × 5 (앞 = 남쪽), 양털 계단 지붕 · 안 깔개 */
    static void tent(Blueprint b, String color) {
        String st = "minecraft:" + color + "_wool_stairs";
        b.air(-1, 1, 0, 5, 4, 4);
        b.fill(0, 0, 0, 4, 0, 4, "minecraft:coarse_dirt|minecraft:dirt_path");
        for (int z = 0; z <= 4; z++) {
            b.set(0, 1, z, st + "[facing=east,half=bottom]");
            b.set(1, 2, z, st + "[facing=east,half=bottom]");
            b.set(2, 3, z, "minecraft:" + color + "_wool");
            b.set(3, 2, z, st + "[facing=west,half=bottom]");
            b.set(4, 1, z, st + "[facing=west,half=bottom]");
        }
        b.fill(1, 1, 0, 1, 1, 0, "minecraft:" + color + "_wool");
        b.fill(3, 1, 0, 3, 1, 0, "minecraft:" + color + "_wool");
        b.fill(1, 1, 4, 3, 2, 4, "minecraft:" + color + "_wool");
        b.fill(1, 0, 1, 3, 0, 3, "minecraft:spruce_planks");
        b.set(2, 1, 2, "minecraft:brown_carpet");
        b.set(1, 1, 3, "minecraft:barrel[facing=up]");
    }

    /** 야자 (정글 통나무가 한쪽으로 휨 + 잎 별 모양) : 밑동 (0, 0) */
    static void palm(Blueprint b, int h, int lean) {
        int x = 0;
        for (int y = 1; y <= h; y++) {
            if (y > h / 2 && lean != 0 && y % 2 == 0) x += lean;
            b.set(x, y, 0, "minecraft:jungle_log[axis=y]");
        }
        int top = h + 1;
        String leaf = "minecraft:jungle_leaves[persistent=true]";
        b.set(x, top, 0, leaf);
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            b.set(x + d[0], top, d[1], leaf);
            b.set(x + d[0] * 2, top, d[1] * 2, leaf);
            b.set(x + d[0] * 3, top - 1, d[1] * 3, leaf);
        }
    }

    /** 모닥불 둘레 (통나무 의자 넷) */
    static void campfire(Blueprint b, boolean soul) {
        b.air(-3, 1, -3, 3, 2, 3);
        b.fill(-1, 0, -1, 1, 0, 1, "minecraft:cobblestone");
        b.set(0, 1, 0, soul ? "minecraft:soul_campfire[lit=true,signal_fire=false]" : "minecraft:campfire[lit=true,signal_fire=false]");
        b.set(0, 1, -3, "minecraft:spruce_log[axis=x]");
        b.set(0, 1, 3, "minecraft:spruce_log[axis=x]");
        b.set(-3, 1, 0, "minecraft:spruce_log[axis=z]");
        b.set(3, 1, 0, "minecraft:spruce_log[axis=z]");
    }

    /** 망루: 기둥 넷 (4 × 4) · 높이 h 의 발판 · 지붕 · 사다리 */
    static void watchtower(Blueprint b, int h, String wood) {
        b.air(-1, 1, -1, 4, h + 5, 4);
        for (int[] p : new int[][] {{0, 0}, {3, 0}, {0, 3}, {3, 3}}) b.fill(p[0], 0, p[1], p[0], h + 3, p[1], "minecraft:" + wood + "_log[axis=y]");
        b.fill(0, h, 0, 3, h, 3, "minecraft:" + wood + "_planks");
        b.walls(0, h + 1, 0, 3, h + 1, 3, "minecraft:" + wood + "_fence");
        b.fill(-1, h + 4, -1, 4, h + 4, 4, "minecraft:" + wood + "_slab[type=bottom]");
        b.fill(0, h + 4, 0, 3, h + 4, 3, "minecraft:" + wood + "_planks");
        for (int y = 1; y <= h; y++) b.set(1, y, 0, "minecraft:ladder[facing=south]");
        b.set(1, h, 0, "minecraft:" + wood + "_trapdoor[facing=south,half=top,open=false]");
        b.air(1, h + 1, 0, 1, h + 1, 0);
        b.set(2, h + 3, 2, "minecraft:lantern[hanging=true]");
    }

    /** 등대: 둥근 돌 탑 (반지름 3) · 꼭대기 빛 (바다 등불 · 유리) */
    static void lighthouse(Blueprint b, int h) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                double d = Math.hypot(x, z);
                if (d > 3.6) continue;
                b.fill(x, -2, z, x, 0, z, "minecraft:stone_bricks");
                if (d > 2.4) {
                    for (int y = 1; y <= h; y++) b.set(x, y, z, (y / 3) % 2 == 0 ? "minecraft:white_concrete" : "minecraft:red_concrete");
                } else b.air(x, 1, z, x, h, z);
                b.set(x, h + 1, z, "minecraft:stone_bricks");
                if (d > 2.4) b.set(x, h + 2, z, "minecraft:stone_brick_wall");
            }
        }
        for (int y = 1; y <= h; y++) b.set(0, y, -2, "minecraft:ladder[facing=south]");
        b.air(0, 1, 3, 0, 2, 3);
        b.set(0, 1, 3, "minecraft:spruce_door[facing=north,half=lower,hinge=left,open=false]");
        b.set(0, 2, 3, "minecraft:spruce_door[facing=north,half=upper,hinge=left,open=false]");
        b.fill(-1, h + 2, -1, 1, h + 4, 1, "minecraft:glass");
        b.set(0, h + 2, 0, "minecraft:sea_lantern");
        b.set(0, h + 3, 0, "minecraft:glowstone");
        b.set(0, h + 4, 0, "minecraft:sea_lantern");
        b.fill(-2, h + 5, -2, 2, h + 5, 2, "minecraft:dark_oak_slab[type=bottom]");
        b.set(0, h + 5, 0, "minecraft:dark_oak_planks");
        b.set(0, h + 6, 0, "minecraft:lightning_rod[facing=up]");
    }

    /** 풍차: 돌 탑 + 날개 (흰 양털 십자) */
    static void windmill(Blueprint b) {
        FieldTown.tower(b, 3, 11, null, false);
        int hub = 9;
        b.set(0, hub, 4, "minecraft:oak_log[axis=z]");
        for (int k = 1; k <= 6; k++) {
            b.set(0, hub + k, 5, "minecraft:white_wool");
            b.set(0, hub - k, 5, k < 6 ? "minecraft:white_wool" : "minecraft:air");
            b.set(k, hub, 5, "minecraft:white_wool");
            b.set(-k, hub, 5, "minecraft:white_wool");
        }
        b.set(0, hub, 5, "minecraft:oak_log[axis=z]");
    }

    /** 허수아비: 건초 · 울타리 · 호박 머리 */
    static void scarecrow(Blueprint b) {
        b.set(0, 1, 0, "minecraft:oak_fence");
        b.set(0, 2, 0, "minecraft:hay_block[axis=y]");
        b.set(-1, 2, 0, "minecraft:oak_fence");
        b.set(1, 2, 0, "minecraft:oak_fence");
        b.set(0, 3, 0, "minecraft:carved_pumpkin[facing=south]");
    }

    /** 돌 둘레 성벽 (반지름 R · 높이 h) — 남쪽 문 (너비 5) · 성가퀴 */
    static void ringWall(Blueprint b, int R, int h, String block, String top) {
        for (int x = -R - 1; x <= R + 1; x++) {
            for (int z = -R - 1; z <= R + 1; z++) {
                double d = Math.hypot(x, z);
                if (d < R - 0.5 || d > R + 1.0) continue;
                if (z > 0 && Math.abs(x) <= 2) continue;
                b.fill(x, -1, z, x, h, z, block);
                if ((x + z) % 2 == 0) b.set(x, h + 1, z, top);
            }
        }
    }

    /** 수정 기둥 (공허): 흑요석 · 우는 흑요석 · 자수정 · 끝막대 */
    static void pylon(Blueprint b, int h) {
        b.fill(-1, 0, -1, 1, 0, 1, "minecraft:polished_blackstone_bricks");
        b.fill(0, 1, 0, 0, h, 0, "minecraft:obsidian|minecraft:obsidian|minecraft:crying_obsidian");
        b.set(0, h + 1, 0, "minecraft:amethyst_block");
        b.set(0, h + 2, 0, "minecraft:end_rod[facing=up]");
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) b.set(d[0], 1, d[1], "minecraft:amethyst_cluster[facing=up]");
    }

    /** 화로 (무스펠): 검은돌 받침 · 마그마 · 모닥불 */
    static void brazier(Blueprint b) {
        b.fill(-1, 0, -1, 1, 0, 1, "minecraft:polished_blackstone_bricks");
        b.set(0, 1, 0, "minecraft:polished_blackstone_brick_wall");
        b.set(0, 2, 0, "minecraft:magma_block");
        b.set(0, 3, 0, "minecraft:campfire[lit=true,signal_fire=false]");
    }

    /** 잔교: (x1 → x2, z 가운데) 너비 3, 갑판 높이 deck, 4칸마다 바닥까지 기둥 · 등불 */
    static final class Pier implements FieldBuild.Piece {
        final int x1;
        final int x2;
        final int z;
        final int deck;

        Pier(int x1, int x2, int z, int deck) {
            this.x1 = Math.min(x1, x2);
            this.x2 = Math.max(x1, x2);
            this.z = z;
            this.deck = deck;
        }

        @Override
        public int minX() {
            return x1;
        }

        @Override
        public int maxX() {
            return x2;
        }

        @Override
        public int minZ() {
            return z - 1;
        }

        @Override
        public int maxZ() {
            return z + 1;
        }

        @Override
        public void column(int x, int zz, FieldTerrain.Column ground, FieldBuild.Out out) {
            int w = zz - z;
            boolean post = (x - x1) % 4 == 0 && Math.abs(w) == 1;
            if (post) {
                for (int y = ground.ground + 1; y < deck; y++) out.set(y, "minecraft:spruce_log[axis=y]");
                out.set(deck, "minecraft:spruce_log[axis=y]");
                out.set(deck + 1, "minecraft:spruce_fence");
                if ((x - x1) % 8 == 0) out.set(deck + 2, "minecraft:lantern[hanging=false]");
                return;
            }
            for (int y = deck + 1; y <= deck + 3; y++) out.set(y, "minecraft:air");
            out.set(deck, "minecraft:spruce_planks");
            if (Math.abs(w) == 1) out.set(deck + 1, "minecraft:spruce_fence");
        }
    }

    // ------------------------------------------------------------------ 거점

    /** 작은 마을 (hamlet): 집 셋 (마당을 보게) · 우물 · 밭 · 나무 넷 */
    static void hamlet(List<Blueprint> bps, int x, int g, int z, int seed) {
        FieldTown.Style[] styles = {FieldTown.SPRUCE, FieldTown.OAK, FieldTown.BIRCH, FieldTown.DARK};
        Blueprint a = FieldTown.front(x, g, z - 7, "south", 9, 11);
        FieldTown.longhouse(a, 9, 11, 4, styles[seed % 4], null);
        bps.add(a);
        Blueprint b = FieldTown.front(x - 7, g, z + 2, "east", 7, 9);
        FieldTown.longhouse(b, 7, 9, 4, styles[(seed + 1) % 4], null);
        bps.add(b);
        Blueprint c = FieldTown.front(x + 7, g, z + 2, "west", 7, 11);
        FieldTown.longhouse(c, 7, 11, 4, styles[(seed + 2) % 4], null);
        bps.add(c);
        Blueprint sq = new Blueprint(x - 4, g, z - 5, "south");
        sq.fill(0, 0, 0, 8, 0, 10, "minecraft:dirt_path|minecraft:coarse_dirt|minecraft:dirt_path|minecraft:gravel");
        bps.add(0, sq);
        Blueprint w = new Blueprint(x, g, z + 3, "south");
        FieldTown.well(w);
        bps.add(w);
        Blueprint f = new Blueprint(x - 9, g, z + 12, "south");
        String[] crops = {"minecraft:wheat[age=7]", "minecraft:carrots[age=7]", "minecraft:potatoes[age=7]"};
        FieldTown.field(f, 19, 8, crops[seed % 3]);
        bps.add(f);
        int[][] trees = {{-16, -10}, {15, -12}, {-17, 4}, {16, 8}};
        for (int i = 0; i < trees.length; i++) {
            Blueprint t = new Blueprint(x + trees[i][0], g, z + trees[i][1], "south");
            FieldTown.tree(t, (seed + i) % 3, 4 + (i + seed) % 2);
            bps.add(t);
        }
    }

    /** 거점 모두 (field.json camp 자리 · 평평한 높이) · 작은 마을 (hamlet) */
    public static void build(FieldTerrain t, List<FieldBuild.Piece> pieces, List<Blueprint.Mark> marks) {
        int hs = 0;
        for (FieldPlan.Site s : t.plan().sites("hamlet")) {
            int g = t.plan().sea + 3;
            for (FieldTerrain.Plot p : t.plots()) if (p.id().equals(s.id())) g = p.y();
            List<Blueprint> bps = new ArrayList<>();
            hamlet(bps, (int) Math.round(s.x()), g, (int) Math.round(s.z()), hs++);
            for (Blueprint b : bps) pieces.add(b.piece(s.id()));
        }
        for (FieldPlan.Site s : t.plan().sites("camp")) {
            int g = t.plan().sea + 3;
            for (FieldTerrain.Plot p : t.plots()) if (p.id().equals(s.id())) g = p.y();
            int x = (int) Math.round(s.x());
            int z = (int) Math.round(s.z());
            List<Blueprint> bps = new ArrayList<>();
            switch (s.id()) {
                case "farm_camp" -> farm(bps, x, g, z);
                case "desert_camp" -> desert(bps, x, g, z);
                case "sea_camp" -> harbor(bps, pieces, t, x, g, z);
                case "north_camp" -> lodge(bps, x, g, z);
                case "void_camp" -> rift(bps, x, g, z);
                case "muspel_camp" -> ember(bps, x, g, z);
                default -> {
                }
            }
            for (Blueprint b : bps) {
                pieces.add(b.piece(s.id()));
                marks.addAll(b.marks());
            }
        }
    }

    /** 올라프의 농가: 농가 (앞 = 서쪽 — 다리에서 오는 길) · 헛간 · 밭 둘 · 풍차 · 허수아비 · 우물 · 이동 지점 */
    static void farm(List<Blueprint> bps, int x, int g, int z) {
        Blueprint house = FieldTown.front(x - 10, g, z - 4, "west", 9, 13);
        FieldTown.longhouse(house, 9, 13, 4, FieldTown.OAK, "npc_13");
        bps.add(house);
        Blueprint barn = FieldTown.front(x - 10, g, z + 16, "west", 11, 15);
        FieldTown.longhouse(barn, 11, 15, 5, FieldTown.SPRUCE, null);
        barn.fill(2, 1, 2, 3, 2, 6, "minecraft:hay_block[axis=y]");
        barn.fill(7, 1, 8, 8, 1, 11, "minecraft:hay_block[axis=x]");
        bps.add(barn);
        Blueprint f1 = new Blueprint(x + 6, g, z - 26, "south");
        FieldTown.field(f1, 19, 12, "minecraft:wheat[age=7]");
        bps.add(f1);
        Blueprint f2 = new Blueprint(x + 6, g, z - 8, "south");
        FieldTown.field(f2, 19, 12, "minecraft:carrots[age=7]");
        bps.add(f2);
        for (int[] p : new int[][] {{x + 10, z - 20}, {x + 20, z - 3}}) {
            Blueprint sc = new Blueprint(p[0], g, p[1], "south");
            scarecrow(sc);
            bps.add(sc);
        }
        Blueprint mill = new Blueprint(x + 18, g, z + 18, "south");
        windmill(mill);
        bps.add(mill);
        Blueprint w = new Blueprint(x + 2, g, z + 12, "south");
        FieldTown.well(w);
        bps.add(w);
        Blueprint way = new Blueprint(x - 22, g, z + 6, "south");
        FieldTown.waystone(way, "waystone_farm");
        bps.add(way);
    }

    /** 라그나르의 오아시스: 천막 다섯 · 가운데 모닥불 · 가게 둘 · 야자 · 우물 · 이동 지점 (오아시스 호수는 남쪽) */
    static void desert(List<Blueprint> bps, int x, int g, int z) {
        Blueprint fire = new Blueprint(x, g, z, "south");
        campfire(fire, false);
        fire.mark("npc_14", 0.5, 1, 4.5, "north");
        bps.add(fire);
        String[] colors = {"orange", "red", "white", "yellow", "brown"};
        int[][] at = {{-18, -14, 0}, {-6, -18, 0}, {8, -16, 0}, {16, -4, 1}, {-20, 2, 3}};
        for (int i = 0; i < colors.length; i++) {
            String face = at[i][2] == 1 ? "west" : at[i][2] == 3 ? "east" : "south";
            Blueprint tb = new Blueprint(x + at[i][0], g, z + at[i][1], face);
            tent(tb, colors[i]);
            bps.add(tb);
        }
        Blueprint s1 = FieldTown.front(x - 8, g, z + 12, "north", 5, 4);
        FieldTown.stall(s1, "minecraft:orange_wool", "minecraft:hay_block[axis=y]", "shop_15");
        bps.add(s1);
        Blueprint s2 = FieldTown.front(x + 2, g, z + 12, "north", 5, 4);
        FieldTown.stall(s2, "minecraft:red_wool", "minecraft:melon", "shop_20");
        bps.add(s2);
        int[][] palms = {{-26, 20}, {-14, 34}, {14, 30}, {24, 18}, {28, 4}, {-30, -6}};
        for (int i = 0; i < palms.length; i++) {
            Blueprint p = new Blueprint(x + palms[i][0], g, z + palms[i][1], "south");
            palm(p, 6 + i % 3, i % 2 == 0 ? 1 : -1);
            bps.add(p);
        }
        Blueprint w = new Blueprint(x + 14, g, z - 30, "south");
        FieldTown.well(w);
        bps.add(w);
        Blueprint way = new Blueprint(x - 4, g, z - 30, "south");
        FieldTown.waystone(way, "waystone_desert");
        bps.add(way);
    }

    /** 비요른의 항구: 어부의 집 셋 · 배 창고 · 등대 · 잔교 셋 (동쪽 바다로) · 이동 지점 */
    static void harbor(List<Blueprint> bps, List<FieldBuild.Piece> pieces, FieldTerrain t, int x, int g, int z) {
        Blueprint house = FieldTown.front(x + 6, g, z - 14, "east", 9, 11);
        FieldTown.longhouse(house, 9, 11, 4, FieldTown.SPRUCE, "npc_15");
        bps.add(house);
        Blueprint h2 = FieldTown.front(x + 6, g, z + 2, "east", 7, 9);
        FieldTown.longhouse(h2, 7, 9, 4, FieldTown.BIRCH, null);
        bps.add(h2);
        Blueprint h3 = FieldTown.front(x - 10, g, z + 16, "east", 7, 11);
        FieldTown.longhouse(h3, 7, 11, 4, FieldTown.DARK, null);
        bps.add(h3);
        Blueprint way = new Blueprint(x - 14, g, z - 4, "south");
        FieldTown.waystone(way, "waystone_sea");
        bps.add(way);
        Blueprint s1 = FieldTown.front(x - 14, g, z - 16, "east", 5, 4);
        FieldTown.stall(s1, "minecraft:blue_wool", "minecraft:barrel[facing=up]", "shop_20");
        bps.add(s1);
        // 해안까지 (동쪽) 찾아 잔교 · 등대
        FieldTerrain.Column c = new FieldTerrain.Column();
        int coast = x;
        for (int xx = x; xx < x + 120; xx++) {
            t.sample(xx, z, c);
            if (!c.land()) {
                coast = xx;
                break;
            }
        }
        int deck = t.plan().sea + 2;
        for (int dz : new int[] {-12, 0, 12}) pieces.add(new Pier(coast - 10, coast + 26, z + dz, deck));
        Blueprint lh = new Blueprint(coast - 6, g, z + 28, "south");
        lighthouse(lh, 16);
        bps.add(lh);
    }

    /** 스카디의 사냥막: 긴 집 · 오두막 둘 · 망루 · 모닥불 · 가죽 걸이 · 가게 · 이동 지점 */
    static void lodge(List<Blueprint> bps, int x, int g, int z) {
        Blueprint lodge = FieldTown.front(x - 12, g, z + 6, "north", 11, 17);
        FieldTown.longhouse(lodge, 11, 17, 5, FieldTown.DARK, null);
        bps.add(lodge);
        Blueprint c1 = FieldTown.front(x + 14, g, z + 6, "north", 7, 9);
        FieldTown.longhouse(c1, 7, 9, 4, FieldTown.SPRUCE, null);
        bps.add(c1);
        Blueprint c2 = FieldTown.front(x + 24, g, z - 12, "west", 7, 9);
        FieldTown.longhouse(c2, 7, 9, 4, FieldTown.SPRUCE, null);
        bps.add(c2);
        Blueprint fire = new Blueprint(x, g, z - 6, "south");
        campfire(fire, false);
        fire.mark("npc_16", 0.5, 1, -3.5, "south");
        bps.add(fire);
        Blueprint tower = new Blueprint(x - 26, g, z - 20, "south");
        watchtower(tower, 9, "spruce");
        bps.add(tower);
        for (int i = 0; i < 3; i++) {
            Blueprint rack = new Blueprint(x - 6 + i * 5, g, z - 18, "south");
            rack.set(0, 1, 0, "minecraft:spruce_fence");
            rack.set(0, 2, 0, "minecraft:spruce_fence");
            rack.set(2, 1, 0, "minecraft:spruce_fence");
            rack.set(2, 2, 0, "minecraft:spruce_fence");
            rack.fill(0, 3, 0, 2, 3, 0, "minecraft:spruce_slab[type=bottom]");
            rack.set(1, 2, 0, "minecraft:brown_wool");
            bps.add(rack);
        }
        Blueprint s1 = FieldTown.front(x + 8, g, z - 22, "south", 5, 4);
        FieldTown.stall(s1, "minecraft:light_gray_wool", "minecraft:bookshelf", "shop_30");
        bps.add(s1);
        Blueprint way = new Blueprint(x + 2, g, z + 22, "south");
        FieldTown.waystone(way, "waystone_north");
        bps.add(way);
    }

    /** 균열 전초기지: 둘레 성벽 (짙은 슬레이트 벽돌) · 수정 기둥 넷 · 보랏빛 천막 · 가게 · 이동 지점 */
    static void rift(List<Blueprint> bps, int x, int g, int z) {
        Blueprint wall = new Blueprint(x, g, z, "south");
        ringWall(wall, 20, 4, "minecraft:deepslate_bricks|minecraft:cracked_deepslate_bricks|minecraft:deepslate_bricks",
                "minecraft:deepslate_brick_wall");
        bps.add(wall);
        for (int[] p : new int[][] {{-12, -12}, {12, -12}, {-12, 12}, {12, 12}}) {
            Blueprint py = new Blueprint(x + p[0], g, z + p[1], "south");
            pylon(py, 6);
            bps.add(py);
        }
        for (int[] p : new int[][] {{-8, -6}, {3, -8}}) {
            Blueprint tb = new Blueprint(x + p[0], g, z + p[1], "south");
            tent(tb, "purple");
            bps.add(tb);
        }
        Blueprint s1 = FieldTown.front(x + 8, g, z + 4, "west", 5, 4);
        FieldTown.stall(s1, "minecraft:purple_wool", "minecraft:amethyst_block", "shop_40");
        bps.add(s1);
        Blueprint fire = new Blueprint(x - 6, g, z + 6, "south");
        campfire(fire, true);
        bps.add(fire);
        Blueprint way = new Blueprint(x, g, z + 12, "south");
        FieldTown.waystone(way, "waystone_void");
        bps.add(way);
    }

    /** 불꽃 전초기지: 검은돌 성벽 · 화로 넷 · 붉은 천막 · 이동 지점 */
    static void ember(List<Blueprint> bps, int x, int g, int z) {
        Blueprint wall = new Blueprint(x, g, z, "south");
        ringWall(wall, 18, 4, "minecraft:polished_blackstone_bricks|minecraft:cracked_polished_blackstone_bricks|minecraft:polished_blackstone_bricks",
                "minecraft:polished_blackstone_brick_wall");
        bps.add(wall);
        for (int[] p : new int[][] {{-10, -10}, {10, -10}, {-10, 10}, {10, 10}}) {
            Blueprint br = new Blueprint(x + p[0], g, z + p[1], "south");
            brazier(br);
            bps.add(br);
        }
        for (int[] p : new int[][] {{-8, -4}, {4, -6}}) {
            Blueprint tb = new Blueprint(x + p[0], g, z + p[1], "south");
            tent(tb, "red");
            bps.add(tb);
        }
        Blueprint s1 = FieldTown.front(x - 8, g, z + 8, "east", 5, 4);
        FieldTown.stall(s1, "minecraft:black_wool", "minecraft:magma_block", "shop_40");
        bps.add(s1);
        Blueprint way = new Blueprint(x, g, z + 10, "south");
        FieldTown.waystone(way, "waystone_muspel");
        bps.add(way);
    }
}
