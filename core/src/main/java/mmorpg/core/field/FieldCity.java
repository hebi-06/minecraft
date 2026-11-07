package mmorpg.core.field;

import java.util.ArrayList;
import java.util.List;

/**
 * 마을 섬의 도시 (2026-11-07 다시 지음 — 사용자 "중앙 마을이 너무 허전하 보임 건물들이 촘촘히 있는 변화한 곳과 성당, 마탑, 여신상, 커다란 분수" ·
 * "모험가 길드도 만들고 싶음, 그리고 장터랑 대장장이 무기소도"): 둥근 성벽 (반지름 104 · 문 넷 · 문 탑 · 모서리 탑) 안에
 * 가운데 광장 (반지름 24, 큰 분수 · 의자 · 화단 · 등 · 이동 지점), 북쪽 성당 앞마당 (여신상) 과 성당, 북서쪽 마탑 (마법사 볼바) · 룬 공방 (룬술사) · 왕녀의 전당,
 * 동쪽 모험가 길드 (직업 교관) · 훈련장 (브란 · 허수아비) · 무기 스승의 집 넷 · 엘린의 집, 남동쪽 장터 (가게 열둘 · 상인), 남쪽 큰길의 대장장이 무기소 (군나르) ·
 * 하랄의 집 · 여관 (잉그리드). 나머지 길은 모두 줄집 (2 ~ 3층, 벽을 맞댐 — FieldRows) 으로 채우고, 뒷마당엔 나무 · 꽃밭.
 * 좌표는 마을 가운데 기준 (북쪽 = −z, 동쪽 = +x).
 */
public final class FieldCity {
    private FieldCity() {}

    /** 마을 만들기 결과: 조각 · 자리표 */
    public record Result(List<FieldBuild.Piece> pieces, List<Blueprint.Mark> marks) {}

    static final int WALL = 104;
    static final int PLAZA = 24;
    /** 성벽 밖 길 (다리에서 오는 길과 이어짐) 끝 */
    static final int OUTER = 150;
    /** 집이 놓일 수 있는 반지름 (성벽 안쪽 길 · 탑 자리 빼고) */
    static final int BUILD_R = WALL - 7;

    /**
     * 길: alongX 면 z = at 줄 (x 가 from ~ to), 아니면 x = at 줄. half = 반폭, avenue = 큰길, sides = 집이 길을 보고 설 쪽
     * (1 = 음수 쪽 · 2 = 양수 쪽 · 3 = 둘 다 · 0 = 집 없음).
     */
    record Street(boolean alongX, int at, int from, int to, int half, boolean avenue, int sides) {
        boolean has(int dx, int dz) {
            int across = alongX ? dz : dx;
            int along = alongX ? dx : dz;
            return Math.abs(across - at) <= half && along >= from && along <= to;
        }
    }

    record Rect(int x1, int z1, int x2, int z2) {
        boolean has(int dx, int dz) {
            return dx >= x1 && dx <= x2 && dz >= z1 && dz <= z2;
        }
    }

    /** 성당 앞마당 (여신상) · 장터 */
    static final Rect CATHEDRAL_SQUARE = new Rect(-22, -44, 22, -22);
    static final Rect MARKET = new Rect(28, 12, 64, 44);

    static final List<Street> STREETS = List.of(
            // 큰길 (반폭 3): 광장 → 성문
            new Street(true, 0, -104, -23, 3, true, 3),
            new Street(true, 0, 23, 104, 3, true, 3),
            new Street(false, 0, 23, 104, 3, true, 3),
            new Street(false, 0, -104, -94, 3, true, 3),
            // 성당 둘레 길 · 뒷길
            new Street(false, -25, -96, -4, 2, false, 3),
            new Street(false, 25, -96, -4, 2, false, 3),
            new Street(true, -96, -27, 27, 2, false, 3),
            // 북서
            new Street(true, -48, -98, -28, 2, false, 3),
            new Street(false, -60, -88, -4, 2, false, 3),
            new Street(true, -80, -60, -28, 2, false, 3),
            // 북동
            new Street(true, -64, 27, 70, 2, false, 3),
            new Street(false, 72, -66, -4, 2, false, 3),
            new Street(true, -80, 28, 60, 2, false, 3),
            // 남동 (장터 둘레)
            new Street(true, 60, 4, 84, 2, false, 3),
            new Street(false, 78, 4, 58, 2, false, 3),
            new Street(false, 46, 62, 92, 2, false, 3),
            new Street(true, 45, 28, 64, 0, false, 2),
            new Street(false, 65, 12, 44, 0, false, 2),
            // 남서
            new Street(false, -25, 4, 92, 2, false, 3),
            new Street(true, 60, -84, -4, 2, false, 3),
            new Street(false, -60, 4, 58, 2, false, 3),
            new Street(false, -46, 62, 92, 2, false, 3),
            new Street(true, 84, -46, 46, 2, false, 3));

    /** 길 종류: 0 없음 · 1 길 · 2 큰길 */
    static int streetAt(int dx, int dz) {
        int k = 0;
        for (Street s : STREETS) {
            if (!s.has(dx, dz)) continue;
            if (s.avenue()) return 2;
            k = 1;
        }
        return k;
    }

    // ------------------------------------------------------------------ 바닥 (광장 · 길 · 앞마당 · 장터 · 성벽 · 문 · 탑)

    static final class Ground implements FieldBuild.Piece {
        final int cx;
        final int cz;
        final int g;

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

        private void clear(FieldBuild.Out out) {
            out.set(g + 1, "minecraft:air");
            out.set(g + 2, "minecraft:air");
            out.set(g + 3, "minecraft:air");
        }

        private void lamp(FieldBuild.Out out) {
            out.set(g + 1, "minecraft:dark_oak_fence");
            out.set(g + 2, "minecraft:dark_oak_fence");
            out.set(g + 3, "minecraft:dark_oak_fence");
            out.set(g + 4, "minecraft:lantern[hanging=false]");
        }

        @Override
        public void column(int x, int z, FieldTerrain.Column ground, FieldBuild.Out out) {
            int dx = x - cx;
            int dz = z - cz;
            double r = Math.hypot(dx + 0.5, dz + 0.5);
            if (r > OUTER) return;
            int ax = Math.abs(dx);
            int az = Math.abs(dz);
            if (r > WALL + 9) {
                // 성벽 밖: 큰길이 흙길로 이어짐 (마을 밖 길과 만남)
                if (ax <= 2 || az <= 2) {
                    out.set(g, pick("minecraft:dirt_path|minecraft:dirt_path|minecraft:coarse_dirt", x, g, z));
                    clear(out);
                }
                return;
            }
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
            if (r >= WALL + 2) {
                // 성벽 바로 밖 (문 앞): 큰길이 이어짐
                if (ax <= 3 || az <= 3) {
                    out.set(g, pick("minecraft:stone_bricks|minecraft:cobblestone|minecraft:dirt_path", x, g, z));
                    clear(out);
                }
                return;
            }
            // 광장
            if (r < PLAZA) {
                plaza(x, z, dx, dz, r, out);
                return;
            }
            // 성당 앞마당
            if (CATHEDRAL_SQUARE.has(dx, dz)) {
                boolean band = Math.floorMod(dx, 6) == 0 || Math.floorMod(dz, 6) == 0;
                out.set(g, band ? "minecraft:polished_andesite" : pick("minecraft:stone_bricks|minecraft:stone_bricks|minecraft:cracked_stone_bricks", x, g, z));
                clear(out);
                if ((ax == 21 && (dz == -43 || dz == -23)) || (ax == 12 && dz == -23)) lamp(out);
                return;
            }
            // 장터
            if (MARKET.has(dx, dz)) {
                out.set(g, pick("minecraft:stone_bricks|minecraft:andesite|minecraft:polished_andesite|minecraft:cobblestone", x, g, z));
                clear(out);
                return;
            }
            int kind = streetAt(dx, dz);
            if (kind == 0 || r >= WALL - 2) return;
            if (kind == 2) {
                int off = ax <= 3 && az > 3 ? ax : az;
                out.set(g, off == 3 ? "minecraft:polished_andesite" : pick("minecraft:stone_bricks|minecraft:cobblestone|minecraft:stone_bricks|minecraft:andesite", x, g, z));
                clear(out);
                // 큰길 등 (가장자리 칸, 14칸마다 양쪽 번갈아)
                int along = ax <= 3 && az > 3 ? az : ax;
                if (off == 3 && along % 14 == 0 && along > PLAZA + 2 && along < WALL - 8) {
                    boolean side = (ax <= 3 && az > 3 ? dx : dz) > 0;
                    if (side == ((along / 14) % 2 == 0)) lamp(out);
                }
                return;
            }
            out.set(g, pick("minecraft:cobblestone|minecraft:andesite|minecraft:cobblestone|minecraft:stone_bricks|minecraft:mossy_cobblestone", x, g, z));
            clear(out);
        }

        private static boolean between(int v, int lo, int hi) {
            return v >= lo && v <= hi;
        }

        /** 모서리 둥근 탑 넷 (대각선 · 성벽 위) 까지 거리 */
        static double towerDist(int dx, int dz) {
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
                int ry = g + 14 + (int) Math.round((3.8 - d) * 1.5);
                for (int y = g + 13; y <= ry; y++) out.set(y, y == ry ? "minecraft:deepslate_tiles" : "minecraft:air");
                if (d < 0.8) {
                    out.set(ry + 1, "minecraft:dark_oak_fence");
                    out.set(ry + 2, "minecraft:dark_oak_fence");
                    out.set(ry + 3, "minecraft:red_wool");
                }
            }
        }

        /** 광장: 동심 띠 · 방사 줄 (석영 · 안산암) · 등 여덟 (큰길 사이) — 분수 · 의자 · 화단은 따로 */
        private void plaza(int x, int z, int dx, int dz, double r, FieldBuild.Out out) {
            out.set(g - 1, "minecraft:stone_bricks");
            double deg = Math.toDegrees(Math.atan2(dz + 0.5, dx + 0.5));
            double spoke = Math.abs(((deg % 22.5) + 22.5) % 22.5 - 11.25);
            String s;
            if (r > 22.6) s = "minecraft:polished_andesite";
            else if ((int) r % 5 == 0) s = "minecraft:smooth_stone";
            else if (spoke > 10.2 && r > 11) s = "minecraft:polished_andesite";
            else s = pick("minecraft:stone_bricks|minecraft:andesite|minecraft:stone_bricks|minecraft:polished_andesite", x, g, z);
            out.set(g, s);
            clear(out);
            double rel = ((deg - 22.5) % 45 + 45) % 45;
            if (r > 21.4 && r < 22.6 && (rel < 2.5 || rel > 42.5)) lamp(out);
        }
    }

    // ------------------------------------------------------------------ 차지한 칸

    /** 차지한 칸 (마을 가운데 기준 ± R) — 건물 · 길 · 나무가 겹치지 않게 */
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

        /** 설계의 상자 둘레 (+ 여백) 를 무조건 차지 (정해진 건물) */
        void force(Blueprint b, int margin) {
            int[] r = bounds(b);
            take(r[0] - margin, r[2] - margin, r[1] + margin, r[3] + margin);
        }

        /** 설계의 상자 둘레 (+ 여백) 가 비었으면 차지하고 true */
        boolean place(Blueprint b, int margin) {
            int[] r = bounds(b);
            if (!free(r[0] - margin, r[2] - margin, r[1] + margin, r[3] + margin)) return false;
            take(r[0] - margin, r[2] - margin, r[1] + margin, r[3] + margin);
            return true;
        }
    }

    /** 앞 가운데 (문) 가 (fx, fz) 이고 facing 쪽을 보는 W × D 집의 땅 (x1, z1, x2, z2) */
    static int[] footprint(int fx, int fz, String facing, int W, int D) {
        int h = W / 2;
        return switch (facing) {
            case "north" -> new int[] {fx - h, fz, fx + h, fz + D - 1};
            case "east" -> new int[] {fx - (D - 1), fz - h, fx, fz + h};
            case "west" -> new int[] {fx, fz - h, fx + D - 1, fz + h};
            default -> new int[] {fx - h, fz - (D - 1), fx + h, fz};
        };
    }

    // ------------------------------------------------------------------ 마을

    /** 마을: 가운데 (cx, cz) · 바닥 높이 g */
    public static Result build(int cx, int cz, int g) {
        List<FieldBuild.Piece> pieces = new ArrayList<>();
        List<Blueprint.Mark> marks = new ArrayList<>();
        pieces.add(new Ground(cx, cz, g));
        List<Blueprint> bps = new ArrayList<>();
        Occ occ = new Occ(cx, cz);
        // 길 · 광장 · 앞마당 · 장터 · 성벽 자리
        for (int dx = -Occ.R; dx <= Occ.R; dx++) {
            for (int dz = -Occ.R; dz <= Occ.R; dz++) {
                double r = Math.hypot(dx + 0.5, dz + 0.5);
                boolean outerRoad = r > WALL - 3 && (Math.abs(dx) <= 3 || Math.abs(dz) <= 3) && r < OUTER;
                if (r < PLAZA + 1 || r >= BUILD_R || outerRoad || streetAt(dx, dz) != 0 || CATHEDRAL_SQUARE.has(dx, dz) || MARKET.has(dx, dz)
                        || Ground.towerDist(dx, dz) < 9) {
                    if (r < WALL - 2 || outerRoad || Math.abs(r - WALL) < 7) occ.take(cx + dx, cz + dz, cx + dx, cz + dz);
                }
            }
        }
        // 광장: 큰 분수 · 의자 · 화단 · 이동 지점
        Blueprint fountain = new Blueprint(cx, g, cz, "south");
        FieldLandmarks.grandFountain(fountain);
        bps.add(fountain);
        // 의자: 앉은 사람이 분수를 봄 (설계의 북쪽 → 서쪽 의자는 west · 동쪽 의자는 east · 남쪽 의자는 south 로 돌림)
        int[][] benches = {{-14, 5, 0}, {-14, -5, 0}, {14, 5, 1}, {14, -5, 1}, {-5, 14, 2}, {5, 14, 2}};
        String[] benchFace = {"west", "east", "south"};
        for (int[] bc : benches) {
            Blueprint b = new Blueprint(cx + bc[0], g, cz + bc[1], benchFace[bc[2]]);
            FieldLandmarks.bench(b);
            bps.add(b);
        }
        int pk = 0;
        for (int[] p : new int[][] {{-16, -16}, {16, -16}, {-16, 16}, {16, 16}}) {
            Blueprint t = new Blueprint(cx + p[0], g, cz + p[1], "south");
            FieldLandmarks.planter(t, pk++ % 2);
            bps.add(t);
        }
        Blueprint way = new Blueprint(cx - 10, g, cz + 16, "south");
        FieldTown.waystone(way, "waystone_midgard");
        bps.add(way);
        // 여신상 (성당 앞마당) · 성당
        Blueprint statue = new Blueprint(cx, g, cz - 33, "south");
        FieldLandmarks.goddess(statue);
        bps.add(statue);
        Blueprint cathedral = FieldTown.front(cx, g, cz - 45, "south", 25, 48);
        FieldLandmarks.cathedral(cathedral);
        occ.force(cathedral, 1);
        bps.add(cathedral);
        // 북서: 마탑 (볼바) · 룬 공방 (룬술사) · 왕녀의 전당
        Blueprint mage = new Blueprint(cx - 42, g, cz - 28, "east");
        FieldLandmarks.mageTower(mage, "npc_8");
        occ.take(cx - 52, cz - 38, cx - 32, cz - 18);
        bps.add(mage);
        Blueprint rune = new Blueprint(cx - 42, g, cz - 11, "south");
        FieldTown.tower(rune, 5, 14, "npc_12");
        occ.force(rune, 1);
        bps.add(rune);
        Blueprint royal = FieldTown.front(cx - 28, g, cz - 66, "east", 15, 25);
        FieldTown.longhouseRoyal(royal, 15, 25, 7, "npc_11");
        royal.set(14, 4, 25, "minecraft:red_wall_banner[facing=south]");
        royal.set(0, 4, 25, "minecraft:red_wall_banner[facing=south]");
        occ.force(royal, 1);
        bps.add(royal);
        // 동쪽: 모험가 길드 (직업 교관) · 훈련장 (브란) · 무기 스승 넷 · 엘린
        Blueprint guild = FieldTown.front(cx + 28, g, cz - 13, "west", 17, 25);
        FieldLandmarks.guild(guild, "job");
        occ.force(guild, 1);
        bps.add(guild);
        Blueprint training = new Blueprint(cx + 30, g, cz - 47, "south");
        FieldTown.trainingGround(training, 40, 22, "npc_2");
        occ.force(training, 1);
        bps.add(training);
        String[] mentors = {"npc_3", "npc_4", "npc_5", "npc_6"};
        FieldTown.Style[] mentorStyle = {FieldTown.SPRUCE, FieldTown.DARK, FieldTown.OAK, FieldTown.BIRCH};
        for (int i = 0; i < mentors.length; i++) {
            Blueprint m = FieldTown.front(cx + 35 + i * 10, g, cz - 49, "south", 9, 11);
            FieldTown.longhouse(m, 9, 11, 4, mentorStyle[i], mentors[i]);
            occ.force(m, 0);
            bps.add(m);
        }
        Blueprint elin = FieldTown.front(cx + 75, g, cz - 35, "west", 9, 11);
        FieldTown.longhouse(elin, 9, 11, 4, FieldTown.BIRCH, "npc_7");
        occ.force(elin, 0);
        bps.add(elin);
        // 남쪽 큰길: 대장장이 무기소 (군나르) · 하랄의 집 · 여관 (잉그리드)
        Blueprint smith = FieldTown.front(cx + 4, g, cz + 34, "west", 19, 21);
        FieldLandmarks.weaponsmith(smith, "npc_9");
        occ.force(smith, 0);
        bps.add(smith);
        Blueprint haral = FieldTown.front(cx - 4, g, cz + 30, "east", 9, 13);
        FieldTown.longhouse(haral, 9, 13, 4, FieldTown.SPRUCE, "npc_1");
        occ.force(haral, 0);
        bps.add(haral);
        Blueprint inn = FieldTown.front(cx - 4, g, cz + 46, "east", 13, 19);
        FieldTown.longhouse(inn, 13, 19, 6, FieldTown.OAK, "npc_10");
        occ.force(inn, 0);
        bps.add(inn);
        // 장터 (남동): 우물 · 가게 열둘 (줄 셋 — 통로를 보게) · 수레 · 짐
        market(cx, cz, g, bps);
        // 줄집: 길마다 양쪽 (빈 땅만)
        int[] count = {0};
        for (Street s : STREETS) rows(s, cx, cz, g, occ, bps, count);
        // 빈 땅 (길에서 먼 안마당 · 성벽 곁): 문 앞 한 칸이 비어 있는 쪽을 보는 작은 줄집으로 채움
        backfill(cx, cz, g, occ, bps, count);
        // 뒷마당: 나무 · 꽃밭
        for (int gx = -WALL; gx <= WALL; gx += 7) {
            for (int gz = -WALL; gz <= WALL; gz += 7) {
                int x = cx + gx + (int) (FieldNoise.hash(gx, gz, 41) * 4) - 2;
                int z = cz + gz + (int) (FieldNoise.hash(gx, gz, 42) * 4) - 2;
                double r = Math.hypot(x - cx, z - cz);
                if (r > BUILD_R - 2) continue;
                double roll = FieldNoise.hash(gx, gz, 43);
                if (roll < 0.5) {
                    Blueprint t = new Blueprint(x, g, z, "south");
                    FieldTown.tree(t, (int) (FieldNoise.hash(gx, gz, 44) * 3), 4 + (int) (FieldNoise.hash(gx, gz, 45) * 2));
                    if (occ.place(t, 0)) bps.add(t);
                } else if (roll < 0.8) {
                    Blueprint fl = new Blueprint(x, g, z, "south");
                    FieldTown.flowers(fl, (int) (roll * 100));
                    if (occ.place(fl, 0)) bps.add(fl);
                }
            }
        }
        // 성벽 밖 밭 여덟 (대각선)
        String[] crops = {"minecraft:wheat[age=7]", "minecraft:carrots[age=7]", "minecraft:potatoes[age=7]", "minecraft:beetroots[age=3]"};
        int f = 0;
        for (double deg : new double[] {28, 62, 118, 152, 208, 242, 298, 332}) {
            double a = Math.toRadians(deg);
            int fx = cx + (int) Math.round(Math.cos(a) * 128) - 9;
            int fz = cz + (int) Math.round(Math.sin(a) * 128) - 6;
            Blueprint fb = new Blueprint(fx, g, fz, "south");
            FieldTown.field(fb, 19, 12, crops[f % crops.length]);
            if (occ.place(fb, 1)) {
                bps.add(fb);
                f++;
            }
        }
        for (Blueprint bp : bps) {
            pieces.add(bp.piece("town"));
            marks.addAll(bp.marks());
        }
        return new Result(pieces, marks);
    }

    /** 장터: 가운데 우물 · 가게 열둘 (5 × 4 — 줄 셋, 통로를 봄) · 상인 셋 · 수레 둘 · 짐 */
    static void market(int cx, int cz, int g, List<Blueprint> bps) {
        int x0 = cx + MARKET.x1();
        int z0 = cz + MARKET.z1();
        Blueprint w = new Blueprint(x0 + 18, g, z0 + 16, "south");
        FieldTown.well(w);
        bps.add(w);
        String[][] goods = {{"minecraft:red_wool", "minecraft:melon", "shop_0"}, {"minecraft:blue_wool", "minecraft:pumpkin", "shop_5"},
                {"minecraft:green_wool", "minecraft:hay_block[axis=y]", "shop_10"}, {"minecraft:yellow_wool", "minecraft:bookshelf", null},
                {"minecraft:red_wool", "minecraft:barrel[facing=up]", null}, {"minecraft:blue_wool", "minecraft:cake[bites=0]", null},
                {"minecraft:green_wool", "minecraft:melon", null}, {"minecraft:yellow_wool", "minecraft:pumpkin", null},
                {"minecraft:red_wool", "minecraft:hay_block[axis=y]", null}, {"minecraft:blue_wool", "minecraft:decorated_pot[facing=south]", null},
                {"minecraft:green_wool", "minecraft:barrel[facing=up]", null}, {"minecraft:yellow_wool", "minecraft:bookshelf", null}};
        // 줄 셋: 북 (남쪽을 봄) · 가운데 두 줄 (등을 맞대고 북 · 남) — 가게마다 7칸 간격
        int[][] at = {
            {3, 3, 0}, {10, 3, 0}, {24, 3, 0}, {31, 3, 0},
            {3, 25, 2}, {10, 25, 2}, {24, 25, 2}, {31, 25, 2},
            {3, 12, 0}, {31, 12, 0}, {3, 21, 2}, {31, 21, 2}};
        for (int i = 0; i < at.length; i++) {
            String facing = at[i][2] == 0 ? "south" : "north";
            Blueprint st = FieldTown.front(x0 + at[i][0] + 2, g, z0 + at[i][1] + (facing.equals("south") ? 3 : 0), facing, 5, 4);
            FieldTown.stall(st, goods[i][0], goods[i][1], goods[i][2]);
            bps.add(st);
        }
        // 수레 (짐 실은 가문비 판 · 바퀴) · 짐 더미
        for (int[] c : new int[][] {{x0 + 8, z0 + 17}, {x0 + 28, z0 + 15}}) {
            Blueprint cart = new Blueprint(c[0], g, c[1], "south");
            cart.fill(-1, 1, 0, 1, 1, 2, "minecraft:spruce_slab[type=top]");
            cart.set(-2, 1, 0, "minecraft:spruce_trapdoor[facing=west,half=bottom,open=true]");
            cart.set(-2, 1, 2, "minecraft:spruce_trapdoor[facing=west,half=bottom,open=true]");
            cart.set(2, 1, 0, "minecraft:spruce_trapdoor[facing=east,half=bottom,open=true]");
            cart.set(2, 1, 2, "minecraft:spruce_trapdoor[facing=east,half=bottom,open=true]");
            cart.set(0, 2, 1, "minecraft:hay_block[axis=x]");
            cart.set(-1, 2, 0, "minecraft:barrel[facing=up]");
            cart.set(1, 2, 2, "minecraft:pumpkin");
            cart.set(0, 1, 3, "minecraft:spruce_fence");
            bps.add(cart);
        }
        for (int[] c : new int[][] {{x0 + 1, z0 + 9}, {x0 + 35, z0 + 9}, {x0 + 1, z0 + 30}, {x0 + 35, z0 + 30}}) {
            Blueprint pile = new Blueprint(c[0], g, c[1], "south");
            pile.set(0, 1, 0, "minecraft:barrel[facing=up]");
            pile.set(0, 1, 1, "minecraft:hay_block[axis=y]");
            pile.set(0, 2, 0, "minecraft:barrel[facing=up]");
            pile.set(0, 1, -1, "minecraft:composter[level=0]");
            pile.set(0, 3, 0, "minecraft:lantern[hanging=false]");
            bps.add(pile);
        }
    }

    /** 길 하나의 양쪽 (sides) 에 줄집을 빈 땅만큼 이어 붙임 */
    static void rows(Street s, int cx, int cz, int g, Occ occ, List<Blueprint> bps, int[] count) {
        for (int side = -1; side <= 1; side += 2) {
            if (side < 0 && (s.sides() & 1) == 0 || side > 0 && (s.sides() & 2) == 0) continue;
            int frontOff = s.at() + side * (s.half() + 1);
            String facing = s.alongX() ? (side < 0 ? "south" : "north") : (side < 0 ? "east" : "west");
            int t = s.from();
            while (t <= s.to()) {
                double h = FieldNoise.hash(t * 7L + s.at() * 131L, side * 13 + (s.alongX() ? 1 : 2), 61);
                int[] widths = h < 0.3 ? new int[] {5, 7} : h < 0.7 ? new int[] {7, 5} : new int[] {9, 7, 5};
                boolean placed = false;
                for (int W : widths) {
                    int D0 = 8 + (int) (FieldNoise.hash(t, s.at() * 3L + side, 62) * 4);
                    for (int D = D0; D >= 7 && !placed; D--) {
                        int fx = s.alongX() ? cx + t + W / 2 : cx + frontOff;
                        int fz = s.alongX() ? cz + frontOff : cz + t + W / 2;
                        int[] r = footprint(fx, fz, facing, W, D);
                        if (!inside(r, cx, cz) || !occ.free(r[0], r[1], r[2], r[3])) continue;
                        int n = count[0]++;
                        double near = Math.hypot(fx - cx, fz - cz);
                        double h2 = FieldNoise.hash(n, 5, 63);
                        int stories = h2 < (s.avenue() || near < 55 ? 0.6 : 0.3) ? 3 : 2;
                        boolean shop = FieldNoise.hash(n, 6, 64) < (s.avenue() ? 0.7 : 0.25);
                        boolean frontGable = FieldNoise.hash(n, 7, 65) < 0.4;
                        FieldRows.Facade fac = FieldRows.FACADES[(int) (FieldNoise.hash(n, 8, 66) * FieldRows.FACADES.length)];
                        Blueprint bp = FieldTown.front(fx, g, fz, facing, W, D);
                        FieldRows.rowhouse(bp, W, D, stories, fac, frontGable, shop, n);
                        occ.take(r[0], r[1], r[2], r[3]);
                        bps.add(bp);
                        t += W;
                        placed = true;
                    }
                    if (placed) break;
                }
                if (!placed) t++;
            }
        }
    }

    /**
     * 빈 땅 채우기: 3칸 격자를 돌며 (7 × 9 · 5 × 8 · 9 × 9 · 5 × 7) 집을 네 방향 가운데 문 앞 칸이 비었거나 길인 쪽으로 놓음.
     * 문 앞 칸은 차지해 둠 (뒤에 놓는 집이 문을 막지 않게).
     */
    static void backfill(int cx, int cz, int g, Occ occ, List<Blueprint> bps, int[] count) {
        int[][] sizes = {{7, 9}, {5, 8}, {9, 9}, {5, 7}};
        String[] facings = {"south", "east", "north", "west"};
        for (int gx = -BUILD_R; gx <= BUILD_R; gx += 3) {
            for (int gz = -BUILD_R; gz <= BUILD_R; gz += 3) {
                int fx = cx + gx;
                int fz = cz + gz;
                if (Math.hypot(gx, gz) > BUILD_R - 4) continue;
                boolean placed = false;
                for (int[] sz : sizes) {
                    if (placed) break;
                    int start = (int) (FieldNoise.hash(gx, gz, 71) * 4);
                    for (int k = 0; k < 4 && !placed; k++) {
                        String facing = facings[(start + k) % 4];
                        int[] r = footprint(fx, fz, facing, sz[0], sz[1]);
                        if (!inside(r, cx, cz) || !occ.free(r[0], r[1], r[2], r[3])) continue;
                        int[] door = switch (facing) {
                            case "north" -> new int[] {fx, fz - 1};
                            case "east" -> new int[] {fx + 1, fz};
                            case "west" -> new int[] {fx - 1, fz};
                            default -> new int[] {fx, fz + 1};
                        };
                        boolean street = streetAt(door[0] - cx, door[1] - cz) != 0;
                        if (!street && !occ.free(door[0], door[1], door[0], door[1])) continue;
                        int n = count[0]++;
                        FieldRows.Facade fac = FieldRows.FACADES[(int) (FieldNoise.hash(n, 8, 66) * FieldRows.FACADES.length)];
                        Blueprint bp = FieldTown.front(fx, g, fz, facing, sz[0], sz[1]);
                        FieldRows.rowhouse(bp, sz[0], sz[1], FieldNoise.hash(n, 5, 63) < 0.35 ? 3 : 2, fac, FieldNoise.hash(n, 7, 65) < 0.4, false, n);
                        occ.take(r[0], r[1], r[2], r[3]);
                        occ.take(door[0], door[1], door[0], door[1]);
                        bps.add(bp);
                        placed = true;
                    }
                }
            }
        }
    }

    /** 땅 네 모서리가 모두 성벽 안 (집 자리) */
    static boolean inside(int[] r, int cx, int cz) {
        for (int[] p : new int[][] {{r[0], r[1]}, {r[2], r[1]}, {r[0], r[3]}, {r[2], r[3]}}) {
            if (Math.hypot(p[0] - cx + 0.5, p[1] - cz + 0.5) > BUILD_R) return false;
        }
        return true;
    }
}
