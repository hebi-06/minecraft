package mmorpg.core.field;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Obj;
import mmorpg.core.Rules;

/**
 * 섬 맵 지형 (2026-11-07): 칸 (x, z) 마다 땅 높이 · 물 · 겉흙 · 바이옴. 게임의 지형 생성기 (fabric field.FieldChunkGenerator) 와
 * 배치도 (시험의 FieldPreview) 가 같은 함수를 씀 — 지도 한 벌.
 * <p>
 * 섬 모양: 가운데에서 잰 거리 / 해안 반지름 = e (가운데 0 · 해안 1). 해안 반지름은 방향마다 잡음, 자리는 큰 물결로 비틀어 (domain warp) 곶 · 만이 생김.
 * 바다 섬은 서쪽에 피오르, 공허 섬은 둘레에 부서진 바위섬. 가장 작은 e 의 섬이 그 칸의 섬, e &lt; 1 이면 땅.
 * 땅 높이는 섬 지형 종류마다 (마을 · 농장 · 사막 · 바다 · 작은 섬 · 북쪽 · 공허 · 무스펠) → 자리 (마을 · 거점 · 보스 신전 · 다리 끝) 를 평평하게 →
 * 강 · 호수 (field.json rivers · lakes). 바다 밑은 해안에서 멀수록 깊음.
 */
public final class FieldTerrain {
    /** 겉흙 (게임은 블록으로, 배치도는 색으로) */
    public enum Surface {
        GRASS, SAND, SANDSTONE, RED_SAND, TERRACOTTA, GRAVEL, STONE, SNOW, PACKED_ICE, BLUE_ICE, PODZOL, MUD, COARSE, CLAY,
        VOID, VOID_DARK, SCULK, OBSIDIAN, SOUL, BONE, BASALT, BLACKSTONE, MAGMA, PATH, DIRT, DEEPSLATE
    }

    /** 칸 하나 (다시 써서 할당을 줄임) */
    public static final class Column {
        /** 가장 위 단단한 블록의 y */
        public int ground;
        /** 물 (또는 용암) 윗면 y — 없으면 {@link Integer#MIN_VALUE} */
        public int water;
        public boolean lava;
        public Surface top;
        /** 윗 겉흙 두께 (top 이 이만큼, 그 아래 under) */
        public int depth;
        public Surface under;
        public String biome;
        /** 땅이면 그 섬, 바다면 가장 가까운 섬 */
        public FieldPlan.Island island;
        /** 땅: 해안에서 안쪽으로 떨어진 거리 (블록) · 바다: 해안까지 거리의 음수 */
        public double coast;
        /** 떠 있는 바위 (공허 섬) — floatTop &lt; floatBottom 이면 없음 */
        public int floatBottom;
        public int floatTop;
        /** 길 (ROAD) · 다리 끝 (LANDING) · 자리 (PLOT) · 물가 (SHORE) */
        public int flags;

        public boolean land() {
            return coast >= 0;
        }

        public boolean hasWater() {
            return water != Integer.MIN_VALUE;
        }
    }

    public static final int ROAD = 1;
    public static final int PLOT = 2;
    public static final int LANDING = 4;
    public static final int ROAD_EDGE = 8;
    public static final int RIVER = 16;

    /** 빈터를 두는 자리 종류 */
    static final java.util.Set<String> CLEARED = java.util.Set.of("camp", "hamlet", "boss");
    /** 바이옴 → 같은 지역의 빈터 바이옴 (tools/field_biomes.py CLEAR — 하늘 · 안개 · 색은 같고 나무 · 큰 장식만 없음) */
    static final java.util.Map<String, String> CLEARING = java.util.Map.ofEntries(
            java.util.Map.entry("mmorpg:midgard", "mmorpg:midgard_town"), java.util.Map.entry("mmorpg:midgard_grove", "mmorpg:midgard_town"),
            java.util.Map.entry("mmorpg:harvest_fields", "mmorpg:harvest_clearing"), java.util.Map.entry("mmorpg:harvest_woods", "mmorpg:harvest_clearing"),
            java.util.Map.entry("mmorpg:dune_sea", "mmorpg:dune_clearing"), java.util.Map.entry("mmorpg:red_mesa", "mmorpg:dune_clearing"),
            java.util.Map.entry("mmorpg:oasis", "mmorpg:oasis_clearing"), java.util.Map.entry("mmorpg:fjord_coast", "mmorpg:fjord_clearing"),
            java.util.Map.entry("mmorpg:mire", "mmorpg:mire_clearing"), java.util.Map.entry("mmorpg:frost_taiga", "mmorpg:frost_clearing"),
            java.util.Map.entry("mmorpg:glacier", "mmorpg:glacier_clearing"), java.util.Map.entry("mmorpg:muspelheim", "mmorpg:muspel_clearing"));

    /** 평평하게 고를 자리: 가운데 · 평평한 반지름 · 이어지는 반지름 · 높이 */
    public record Plot(String id, String kind, double x, double z, double flat, double blend, int y) {}

    /** 다리: 해안 (ax, az) → 해안 (bx, bz), 갑판 높이 */
    public record Span(FieldPlan.Bridge bridge, double ax, double az, double bx, double bz, int deck) {
        public double length() {
            return Math.hypot(bx - ax, bz - az);
        }
    }

    /** 길 · 강 한 토막 */
    public record Segment(double ax, double az, double bx, double bz, double width) {}

    /** 굽게 나눈 강 (토막들 · 상자) */
    private record RiverPath(FieldPlan.River river, List<Segment> segs, double minX, double maxX, double minZ, double maxZ) {}

    /** 호수와 물 높이 (둘레 땅 − 1) */
    private record LakeAt(FieldPlan.Lake lake, int level) {}

    /** 화산 (무스펠) */
    private record Volcano(double x, double z, double r, double h) {}

    private final FieldPlan plan;
    private final FieldNoise noise;
    private final FieldNoise warp;
    private final int sea;
    private final List<Plot> plots = new ArrayList<>();
    private final List<Span> spans = new ArrayList<>();
    private final List<Segment> roads = new ArrayList<>();
    private final List<RiverPath> rivers = new ArrayList<>();
    private final List<LakeAt> lakes = new ArrayList<>();
    private final List<Volcano> volcanoes = new ArrayList<>();
    private final FieldPlan.Site hel;
    private final FieldPlan.Site oasis;

    public FieldTerrain(FieldPlan plan) {
        this.plan = plan;
        this.noise = new FieldNoise(plan.seed);
        this.warp = new FieldNoise(plan.seed * 31 + 7);
        this.sea = plan.sea;
        this.hel = plan.site("b_hel");
        this.oasis = plan.site("desert_camp");
        FieldPlan.Island mus = plan.island("muspel");
        if (mus != null) {
            volcanoes.add(new Volcano(mus.x() - 250, mus.z() - 70, 130, 74));
            volcanoes.add(new Volcano(mus.x() + 240, mus.z() + 200, 110, 58));
            volcanoes.add(new Volcano(mus.x() - 170, mus.z() + 290, 80, 40));
        }
        // 자리: 마을 · 세계수 · 거점 · 보스 신전 (보스 필드 반지름 + 12)
        Obj bosses = Rules.get().obj("BOSSES");
        for (FieldPlan.Site s : plan.sites) {
            double flat;
            switch (s.kind()) {
                case "town", "camp", "hamlet" -> flat = s.r();
                case "tree" -> flat = Math.max(24, s.r() * 0.5);
                case "boss" -> {
                    Obj b = s.boss() == null ? null : bosses.objOrNull(s.boss());
                    Obj ar = b == null ? null : b.objOrNull("arena");
                    flat = (ar == null ? 20 : ar.numOr("radius", 20)) + 12;
                }
                default -> flat = 0;
            }
            if (flat <= 0) continue;
            int y = (int) Math.round(Math.max(sea + 3, Math.min(sea + 60, rawHeight(s.x(), s.z()))));
            if (s.kind().equals("town")) y = sea + 9;
            if (s.kind().equals("tree")) y = sea + 12;
            plots.add(new Plot(s.id(), s.kind(), s.x(), s.z(), flat, flat + Math.max(30, flat * 0.6), y));
        }
        // 다리: 두 섬 가운데를 잇는 줄에서 해안 → 해안, 다리 끝은 갑판 높이로 고름
        for (FieldPlan.Bridge b : plan.bridges) {
            FieldPlan.Island a = plan.island(b.from());
            FieldPlan.Island c = plan.island(b.to());
            double dx = c.x() - a.x();
            double dz = c.z() - a.z();
            double len = Math.hypot(dx, dz);
            double ux = dx / len;
            double uz = dz / len;
            double ta = -1;
            double tb = -1;
            for (double t = a.radius() * 0.5; t < len; t += 1) {
                if (edgeOf(a, a.x() + ux * t, a.z() + uz * t) >= 1) {
                    ta = t;
                    break;
                }
            }
            for (double t = len - c.radius() * 0.5; t > 0; t -= 1) {
                if (edgeOf(c, a.x() + ux * t, a.z() + uz * t) >= 1) {
                    tb = t;
                    break;
                }
            }
            if (ta < 0 || tb < 0 || tb <= ta) continue;
            ta -= 14; // 땅 위로 조금 들어가 다리 끝을 놓음
            tb += 14;
            Span sp = new Span(b, a.x() + ux * ta, a.z() + uz * ta, a.x() + ux * tb, a.z() + uz * tb, plan.bridgeDeck);
            spans.add(sp);
            plots.add(new Plot("landing_" + b.from() + "_" + b.to() + "_a", "landing", sp.ax(), sp.az(), 7, 34, plan.bridgeDeck - 1));
            plots.add(new Plot("landing_" + b.from() + "_" + b.to() + "_b", "landing", sp.bx(), sp.bz(), 7, 34, plan.bridgeDeck - 1));
        }
        // 강: 점 사이를 나눠 옆으로 굽힘
        for (FieldPlan.River r : plan.rivers) {
            List<Segment> segs = new ArrayList<>();
            List<double[]> pts = new ArrayList<>(r.points());
            if (!r.lava()) {
                // 끝점을 바다까지 늘림 (마지막 방향 그대로 — 강이 바다로 이어지게)
                double[] a = pts.get(pts.size() - 2);
                double[] b = pts.get(pts.size() - 1);
                double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
                double ux = (b[0] - a[0]) / len;
                double uz = (b[1] - a[1]) / len;
                FieldPlan.Island is = plan.island(r.island());
                for (double t = 0; t < 800; t += 2) {
                    if (is != null && edgeOf(is, b[0] + ux * t, b[1] + uz * t) > 1.04) {
                        pts.add(new double[] {b[0] + ux * (t + 6), b[1] + uz * (t + 6)});
                        break;
                    }
                }
            }
            for (int i = 1; i < pts.size(); i++) curve(pts.get(i - 1), pts.get(i), r.width(), 0.16, segs);
            double minX = 1e9;
            double maxX = -1e9;
            double minZ = 1e9;
            double maxZ = -1e9;
            for (Segment s : segs) {
                minX = Math.min(minX, Math.min(s.ax(), s.bx()));
                maxX = Math.max(maxX, Math.max(s.ax(), s.bx()));
                minZ = Math.min(minZ, Math.min(s.az(), s.bz()));
                maxZ = Math.max(maxZ, Math.max(s.az(), s.bz()));
            }
            rivers.add(new RiverPath(r, segs, minX, maxX, minZ, maxZ));
        }
        // 호수 물 높이: 둘레 (반지름 + 4) 의 가장 낮은 땅 − 1 (바다 높이보다 낮지 않게)
        for (FieldPlan.Lake l : plan.lakes) {
            double low = 1e9;
            for (int i = 0; i < 32; i++) {
                double a = Math.PI * 2 * i / 32;
                low = Math.min(low, plotted(l.x() + Math.cos(a) * (l.r() + 4), l.z() + Math.sin(a) * (l.r() + 4)));
            }
            lakes.add(new LakeAt(l, Math.max(sea, (int) Math.floor(low) - 1)));
        }
        buildRoads();
    }

    public FieldPlan plan() {
        return plan;
    }

    public List<Plot> plots() {
        return plots;
    }

    public List<Span> spans() {
        return spans;
    }

    public List<Segment> roads() {
        return roads;
    }

    /** 강 토막 모두 (배치도) */
    public List<Segment> riverSegments() {
        List<Segment> out = new ArrayList<>();
        for (RiverPath r : rivers) out.addAll(r.segs());
        return out;
    }

    // ------------------------------------------------------------------ 도움 함수

    static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    static double smooth(double t) {
        t = clamp01(t);
        return t * t * (3 - 2 * t);
    }

    /** a → b 사이에서 0 → 1 (a &gt; b 면 거꾸로) */
    static double smoothstep(double a, double b, double x) {
        return smooth((x - a) / (b - a));
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // ------------------------------------------------------------------ 섬 모양

    /** 섬의 e (가운데 0 · 해안 1) */
    double edgeOf(FieldPlan.Island is, double x, double z) {
        double R0 = is.radius();
        double u0 = x - is.x();
        double v0 = z - is.z();
        double r0 = Math.hypot(u0, v0);
        if (r0 > R0 * 2.2) return r0 / R0;
        double k = is.index() * 101.0 + 13;
        double ws = R0 * 0.55;
        // 큰 물결로 자리를 비틀어 곶 · 만
        double wx = x + R0 * 0.14 * warp.fbm((x + k) / ws, (z - k) / ws, 3);
        double wz = z + R0 * 0.14 * warp.fbm((x - k * 1.7) / ws, (z + k * 0.6) / ws, 3);
        double u = wx - is.x();
        double v = wz - is.z();
        double r = Math.hypot(u, v);
        double ca = r < 1e-6 ? 1 : u / r;
        double sa = r < 1e-6 ? 0 : v / r;
        double n = 0.55 * noise.noise(ca * 1.3 + k, sa * 1.3 - k) + 0.3 * noise.noise(ca * 2.7 - k, sa * 2.7 + k * 0.5)
                + 0.15 * noise.noise(ca * 5.6 + k * 0.3, sa * 5.6 - k * 0.2);
        // 섬마다 한쪽으로 조금 길쭉하게 (방향은 섬 번호로 고정)
        double phi = FieldNoise.hash(is.index(), 7, 3) * Math.PI;
        double stretch = 0.16 * Math.cos(2 * (Math.atan2(sa, ca) - phi));
        double R = R0 * (1 + is.coast() * 1.6 * n + stretch);
        double e = r / R + 0.035 * noise.fbm((x + k) / 60, (z - k) / 60, 3);
        switch (is.terrain()) {
            case "sea" -> {
                // 피오르: 서 · 북 · 남쪽 해안에서 안쪽으로 굽어 들어가는 좁은 만 다섯 남짓
                double g = noise.noise(ca * 0.95 + k, sa * 0.95 - k + r / 240);
                double f = Math.pow(1 - Math.min(1, Math.abs(g) * 4.5), 6);
                double face = smoothstep(0.35, -0.2, ca);
                e += 0.75 * f * face * smoothstep(0.4, 0.97, e);
            }
            case "void" -> {
                // 둘레의 부서진 바위섬
                if (e > 0.97 && e < 1.3 && noise.noise(x / 52.0 + k, z / 52.0 - k) > 0.42 + (e - 1) * 0.9) e = 0.965;
            }
            default -> {
            }
        }
        return e;
    }

    // ------------------------------------------------------------------ 높이

    private FieldPlan.Island owner(double x, double z, double[] eOut) {
        FieldPlan.Island best = null;
        double be = 9;
        for (FieldPlan.Island is : plan.islands) {
            double e = edgeOf(is, x, z);
            if (e < be) {
                be = e;
                best = is;
            }
        }
        eOut[0] = be;
        return best;
    }

    /** 땅 높이 (자리 고르기 전) — 섬 밖이면 바다 밑 */
    double rawHeight(double x, double z) {
        double[] e = new double[1];
        FieldPlan.Island best = owner(x, z, e);
        if (best == null || e[0] >= 1) return sea - 6;
        return landHeight(best, x, z, (1 - e[0]) * best.radius(), null);
    }

    /** 자리까지 고른 땅 높이 (강 · 호수 전) */
    private double plotted(double x, double z) {
        double h = rawHeight(x, z);
        for (Plot p : plots) {
            double r = Math.hypot(x - p.x(), z - p.z());
            if (r < p.blend()) h = lerp(h, p.y(), smoothstep(p.blend(), p.flat(), r));
        }
        return h;
    }

    /** 지형 종류마다의 땅 높이. out 이 있으면 바이옴 단서도 적음 */
    private double landHeight(FieldPlan.Island is, double x, double z, double d, Column out) {
        double u = (x - is.x()) / is.radius();
        double v = (z - is.z()) / is.radius();
        double h;
        switch (is.terrain()) {
            case "village" -> {
                h = sea + 1 + smooth(d / 36) * 7;
                h += 3.5 * noise.fbm(x / 150, z / 150, 3) * smooth(d / 60) + 1.1 * noise.fbm(x / 34, z / 34, 2);
                boolean grove = noise.fbm(x / 190 + 3, z / 190 - 8, 2) > 0.16 && d > 45;
                if (out != null) out.biome = grove ? "mmorpg:midgard_grove" : "mmorpg:midgard";
            }
            case "farm" -> {
                h = sea + 1 + smooth(d / 30) * 5;
                h += 6.5 * noise.fbm(x / 230, z / 230, 3) * smooth(d / 80) + 2.2 * noise.fbm(x / 55, z / 55, 2);
                boolean woods = noise.fbm(x / 260 + 11, z / 260 - 5, 2) > 0.08 && d > 40;
                if (out != null) out.biome = woods ? "mmorpg:harvest_woods" : "mmorpg:harvest_fields";
            }
            case "desert" -> {
                h = sea + 1 + smooth(d / 25) * 4;
                double s = (x * 0.819 + z * 0.574) / 46;
                double q = (-x * 0.574 + z * 0.819) / 150;
                double dune = noise.ridged(s, q, 2);
                h += dune * dune * 10 * smooth(d / 50) + 1.4 * noise.fbm(x / 40, z / 40, 2);
                // 메사: 남쪽에 많은 큰 평평한 바위산, 가장자리는 네 계단
                double m = noise.fbm(x / 330 + 9, z / 330 - 4, 3) + 0.42 * (v + 0.1) - 0.06;
                double edge = smoothstep(0.1, 0.19, m) * smooth((d - 30) / 50);
                if (edge > 0) {
                    double top = sea + 29 + 4 * noise.fbm(x / 150, z / 150, 2);
                    double steps = Math.floor(edge * 4) / 4;
                    h = lerp(h, top, lerp(steps, edge, 0.18));
                }
                if (out != null) {
                    out.biome = edge > 0.2 ? "mmorpg:red_mesa" : "mmorpg:dune_sea";
                    if (oasis != null && Math.hypot(x - oasis.x(), z - oasis.z()) < oasis.r() + 45 + 16 * noise.noise(x / 20.0, z / 20.0)) out.biome = "mmorpg:oasis";
                }
            }
            case "sea" -> {
                h = sea + 1 + smooth(d / 20) * 6;
                h += 7 * noise.fbm(x / 160, z / 160, 3) * smooth(d / 70) + 2 * noise.fbm(x / 45, z / 45, 2);
                double west = smoothstep(0.15, 0.6, -u + 0.25 * noise.fbm(x / 200, z / 200, 2));
                h += west * 15 * smooth(d / 13);
                double mire = smoothstep(0.1, 0.45, v * 0.7 - u * 0.7 - 0.25 + 0.25 * noise.fbm(x / 150 + 3, z / 150, 2)) * smooth((d - 20) / 40);
                if (mire > 0) h = lerp(h, sea + 0.4 + 1.6 * noise.fbm(x / 22, z / 22, 2), mire * 0.92);
                if (out != null) out.biome = mire > 0.5 ? "mmorpg:mire" : "mmorpg:fjord_coast";
            }
            case "islet" -> {
                h = sea + 1 + smooth(d / 12) * 5 + 5 * noise.fbm(x / 60, z / 60, 2) * smooth(d / 30);
                if (out != null) out.biome = "mmorpg:fjord_coast";
            }
            case "north" -> {
                h = sea + 2 + smooth(d / 28) * 6;
                double north = -v;
                double mount = smoothstep(-0.4, 0.3, north + 0.35 * noise.fbm(x / 320, z / 320, 2)) * smooth(d / 90);
                double ridge = noise.ridged(x / 270, z / 270, 4);
                h += mount * (10 + 80 * Math.pow(ridge, 1.6)) + 3 * noise.fbm(x / 50, z / 50, 2);
                double g = smoothstep(0.5, 0.78, north + 0.15 * noise.fbm(x / 200, z / 200, 2)) * smooth(d / 60);
                if (g > 0) {
                    double ice = sea + 46 + 6 * noise.fbm(x / 120, z / 120, 2);
                    h = lerp(h, Math.max(ice, h * 0.35 + ice * 0.65), g * 0.9);
                    double c = Math.abs(noise.noise(x / 85 + 4, z / 85 - 2));
                    if (c < 0.04) h -= 13 * g * smoothstep(0.04, 0.015, c);
                }
                if (out != null) out.biome = g > 0.5 ? "mmorpg:glacier" : h > sea + 52 ? "mmorpg:frost_peaks" : "mmorpg:frost_taiga";
            }
            case "void" -> {
                h = sea + 3 + smooth(d / 16) * 16;
                h += 9 * noise.fbm(x / 120, z / 120, 3) + 4 * noise.fbm(x / 40, z / 40, 2);
                h = Math.floor(h / 3) * 3 + 1;
                double c = Math.abs(noise.fbm(x / 230 + 3, z / 230 - 7, 2));
                if (c < 0.05 && d > 40) h = lerp(h, 14, smoothstep(0.05, 0.022, c));
                if (out != null) {
                    boolean helZone = hel != null && z < hel.z() + 230 + 70 * noise.fbm(x / 160 + 2, z / 160, 2);
                    out.biome = helZone ? "mmorpg:helheim" : "mmorpg:void_rift";
                }
            }
            case "muspel" -> {
                h = sea + 2 + smooth(d / 22) * 8 + 5 * noise.fbm(x / 90, z / 90, 3) + 2 * noise.fbm(x / 30, z / 30, 2);
                for (Volcano vo : volcanoes) {
                    double r = Math.hypot(x - vo.x(), z - vo.z());
                    if (r >= vo.r()) continue;
                    double cone = sea + 4 + vo.h() * Math.pow(1 - r / vo.r(), 1.45) + 3 * noise.fbm(x / 25, z / 25, 2);
                    double crater = vo.r() * 0.17;
                    if (r < crater) cone = sea + 4 + vo.h() * Math.pow(1 - crater / vo.r(), 1.45) - 9 * smooth(1 - r / crater);
                    h = Math.max(h, cone);
                }
                if (out != null) out.biome = "mmorpg:muspelheim";
            }
            default -> {
                h = sea + 1 + smooth(d / 20) * 4;
                if (out != null) out.biome = "mmorpg:midgard";
            }
        }
        return h;
    }

    // ------------------------------------------------------------------ 길

    private void buildRoads() {
        for (FieldPlan.Island is : plan.islands) {
            List<double[]> nodes = new ArrayList<>();
            List<double[]> landings = new ArrayList<>();
            double[] hub = null;
            for (FieldPlan.Site s : plan.sites) {
                if (!s.island().equals(is.id())) continue;
                if (s.kind().equals("camp") || s.kind().equals("town")) hub = new double[] {s.x(), s.z()};
            }
            for (Span sp : spans) {
                if (sp.bridge().from().equals(is.id())) landings.add(new double[] {sp.ax(), sp.az()});
                if (sp.bridge().to().equals(is.id())) landings.add(new double[] {sp.bx(), sp.bz()});
            }
            for (FieldPlan.Site s : plan.sites) {
                if (!s.island().equals(is.id())) continue;
                if (s.kind().equals("zone") || s.kind().equals("boss") || s.kind().equals("tree") || s.kind().equals("hamlet")) nodes.add(new double[] {s.x(), s.z()});
            }
            if (hub == null && !landings.isEmpty()) hub = landings.remove(0);
            if (hub == null && !nodes.isEmpty()) hub = nodes.remove(0);
            if (hub == null) continue;
            // 다리 끝은 거점 (마을) 으로 곧장, 나머지는 가까운 것부터 이어 붙임 (Prim): 이미 이어진 점 가운데 가장 가까운 곳에서 갈라짐
            List<double[]> done = new ArrayList<>();
            done.add(hub);
            for (double[] l : landings) {
                curve(hub, l, 3.0, 0.07, roads);
                done.add(l);
            }
            List<double[]> left = new ArrayList<>(nodes);
            while (!left.isEmpty()) {
                double best = Double.MAX_VALUE;
                int bi = -1;
                double[] from = null;
                for (int i = 0; i < left.size(); i++) {
                    for (double[] p : done) {
                        double dd = Math.hypot(left.get(i)[0] - p[0], left.get(i)[1] - p[1]);
                        if (dd < best) {
                            best = dd;
                            bi = i;
                            from = p;
                        }
                    }
                }
                double[] to = left.remove(bi);
                curve(from, to, 3.0, 0.07, roads);
                done.add(to);
            }
        }
    }

    /** 굽은 줄: 토막을 나눠 가운데를 옆으로 밀어 냄 (자리마다 고정) */
    private void curve(double[] a, double[] b, double width, double bendK, List<Segment> out) {
        double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
        int n = Math.max(1, (int) (len / 24));
        double nx = -(b[1] - a[1]) / Math.max(1e-6, len);
        double nz = (b[0] - a[0]) / Math.max(1e-6, len);
        double px = a[0];
        double pz = a[1];
        for (int i = 1; i <= n; i++) {
            double t = (double) i / n;
            double bend = i == n ? 0
                    : Math.sin(Math.PI * t) * len * bendK * noise.noise(a[0] / 97 + b[1] / 131, a[1] / 89 + t * 1.7)
                            + Math.sin(Math.PI * t) * 6 * noise.noise(a[0] / 37 + t * 4.1, b[0] / 41 - t * 3.3);
            double qx = lerp(a[0], b[0], t) + nx * bend;
            double qz = lerp(a[1], b[1], t) + nz * bend;
            out.add(new Segment(px, pz, qx, qz, width));
            px = qx;
            pz = qz;
        }
    }

    private static double segDist(Segment s, double x, double z) {
        double dx = s.bx() - s.ax();
        double dz = s.bz() - s.az();
        double l2 = dx * dx + dz * dz;
        double t = l2 < 1e-9 ? 0 : clamp01(((x - s.ax()) * dx + (z - s.az()) * dz) / l2);
        return Math.hypot(x - (s.ax() + dx * t), z - (s.az() + dz * t));
    }

    private static double nearest(List<Segment> segs, double x, double z, double pad) {
        double best = 1e9;
        for (Segment s : segs) {
            if (x < Math.min(s.ax(), s.bx()) - pad || x > Math.max(s.ax(), s.bx()) + pad || z < Math.min(s.az(), s.bz()) - pad || z > Math.max(s.az(), s.bz()) + pad) {
                continue;
            }
            best = Math.min(best, segDist(s, x, z));
        }
        return best;
    }

    /** 가장 가까운 길까지 거리 (길이 멀면 큰 수) */
    public double roadDistance(double x, double z) {
        return nearest(roads, x, z, 8);
    }

    // ------------------------------------------------------------------ 칸

    private static final ThreadLocal<Column> LOCAL = ThreadLocal.withInitial(Column::new);

    /** 칸 (x, z) 의 바이옴 (sample 과 같은 답 — 스레드마다 칸 하나를 다시 씀) */
    public String biomeAt(int x, int z) {
        return sample(x, z, LOCAL.get()).biome;
    }

    /** 칸 (x, z) 의 땅 · 물 윗면 중 높은 쪽 (시작 자리 · 높이 어림) */
    public int surfaceAt(int x, int z) {
        Column c = sample(x, z, LOCAL.get());
        return c.hasWater() ? Math.max(c.ground, c.water) : c.ground;
    }

    /** 칸 (x, z) — out 을 채워 돌려줌 */
    public Column sample(int x, int z, Column out) {
        out.flags = 0;
        out.water = Integer.MIN_VALUE;
        out.lava = false;
        out.floatBottom = 0;
        out.floatTop = -1;
        out.depth = 3;
        double[] eo = new double[1];
        FieldPlan.Island best = owner(x, z, eo);
        double be = eo[0];
        out.island = best;
        if (best == null || be >= 1) {
            seaFloor(x, z, best, be, out);
            return out;
        }
        double d = (1 - be) * best.radius();
        out.coast = d;
        double h = landHeight(best, x, z, d, out);
        // 자리 고르기 (마을 · 거점 · 신전 · 다리 끝)
        boolean clearing = false;
        for (Plot p : plots) {
            double r = Math.hypot(x - p.x(), z - p.z());
            if (r >= p.blend()) continue;
            h = lerp(h, p.y(), smoothstep(p.blend(), p.flat(), r));
            if (r < p.flat()) out.flags |= p.kind().equals("landing") ? LANDING : PLOT;
            if (r < p.flat() && p.kind().equals("town")) out.biome = "mmorpg:midgard_town"; // 마을 안: 나무가 저절로 나지 않는 바이옴 (건물 · 길 사이는 설계한 나무)
            else if (r < p.flat() + 6 && CLEARED.contains(p.kind())) clearing = true;
        }
        // 거점 · 작은 마을 · 신전 자리: 같은 지역의 "빈터" 바이옴 (나무 · 선인장 · 현무암 기둥 · 얼음 가시가 나지 않음 — CI 42359496849 사진: 항구 · 사냥막이 숲에 묻힘)
        if (clearing && out != null) out.biome = CLEARING.getOrDefault(out.biome, out.biome);
        if (best.terrain().equals("village")) {
            FieldPlan.Site tree = plan.site("tree");
            if (tree != null) {
                double r = Math.hypot(x - tree.x(), z - tree.z());
                if (r < tree.r() * 1.8) h = Math.max(h, sea + 12 - Math.max(0, r - tree.r() * 0.45) * 0.22);
            }
        }
        // 강 (바다 높이 물) · 용암 강 (땅 − 1)
        int lavaTop = Integer.MIN_VALUE;
        for (RiverPath rp : rivers) {
            if (!rp.river().island().equals(best.id())) continue;
            double w = rp.river().width();
            double bank = rp.river().lava() ? 3 : 12 + Math.max(0, h - sea) * 0.9;
            if (x < rp.minX() - w - bank || x > rp.maxX() + w + bank || z < rp.minZ() - w - bank || z > rp.maxZ() + w + bank) continue;
            double dr = nearest(rp.segs(), x, z, w + bank);
            if (dr >= w + bank) continue;
            if (rp.river().lava()) {
                if (dr < w) {
                    lavaTop = (int) Math.floor(h) - 1;
                    h = h - 3;
                }
            } else if (dr < w) {
                h = Math.min(h, sea - 1 - 2.5 * (1 - (dr / w) * (dr / w)));
                out.flags |= RIVER;
            } else {
                h = Math.min(h, lerp(sea + 0.6, h, smoothstep(w, w + bank, dr)));
            }
        }
        // 호수
        int lakeTop = Integer.MIN_VALUE;
        for (LakeAt la : lakes) {
            FieldPlan.Lake l = la.lake();
            double r = Math.hypot(x - l.x(), z - l.z());
            double wob = l.r() * (1 + 0.18 * noise.noise(x / 14.0 + l.x(), z / 14.0 - l.z()));
            if (r < wob) {
                h = Math.min(h, la.level() - 1 - 4 * (1 - (r / wob) * (r / wob)));
                lakeTop = la.level();
            } else if (r < wob + 5) {
                h = Math.max(h, la.level() + 1);
            }
        }
        int g = (int) Math.floor(h);
        out.ground = g;
        double rd = (out.flags & RIVER) != 0 ? 1e9 : roadDistance(x, z);
        if (rd < 2.6 && (out.flags & PLOT) == 0) out.flags |= ROAD;
        else if (rd < 3.6 && (out.flags & PLOT) == 0) out.flags |= ROAD_EDGE;
        surface(best, x, z, d, out);
        if (lavaTop > out.ground) {
            out.water = lavaTop;
            out.lava = true;
            out.top = Surface.MAGMA;
        } else if (lakeTop > out.ground) {
            out.water = lakeTop;
        } else if (out.ground < sea && !out.hasWater() && !best.terrain().equals("void")) {
            out.water = sea;
        }
        if (best.terrain().equals("void")) floating(x, z, d, out);
        return out;
    }

    private void seaFloor(int x, int z, FieldPlan.Island near, double e, Column out) {
        double R = near == null ? 400 : near.radius();
        double dc = Math.max(0, (e - 1) * R);
        out.coast = -dc;
        double floor = sea - 3 - 22 * smooth(dc / 70) - 3 * noise.fbm(x / 80.0, z / 80.0, 2);
        out.ground = (int) Math.floor(floor);
        out.water = sea;
        String t = near == null ? "" : near.terrain();
        boolean shallow = dc < 36;
        switch (t) {
            case "north" -> {
                out.biome = shallow ? "mmorpg:frozen_shallows" : "mmorpg:open_sea";
                out.top = Surface.GRAVEL;
            }
            case "void" -> {
                out.biome = shallow ? "mmorpg:rift_shallows" : "mmorpg:open_sea";
                out.top = Surface.DEEPSLATE;
            }
            case "muspel" -> {
                out.biome = shallow ? "mmorpg:ember_shallows" : "mmorpg:open_sea";
                out.top = Surface.BASALT;
            }
            default -> {
                out.biome = shallow ? "mmorpg:shallows" : "mmorpg:open_sea";
                out.top = dc < 30 ? Surface.SAND : noise.noise(x / 40.0, z / 40.0) > 0.3 ? Surface.CLAY : Surface.GRAVEL;
            }
        }
        out.under = out.top == Surface.SAND ? Surface.SANDSTONE : Surface.STONE;
        out.depth = 3;
    }

    private void surface(FieldPlan.Island is, int x, int z, double d, Column out) {
        int g = out.ground;
        double n = noise.noise(x / 18.0, z / 18.0);
        out.under = Surface.DIRT;
        out.depth = 3;
        switch (is.terrain()) {
            case "village", "farm", "islet" -> {
                out.top = Surface.GRASS;
                if (d < 6 && g <= sea + 2) {
                    out.top = Surface.SAND;
                    out.under = Surface.SANDSTONE;
                }
                if (g < sea) out.top = d < 12 ? Surface.SAND : Surface.DIRT;
                if (is.terrain().equals("farm") && out.biome.equals("mmorpg:harvest_woods") && n > 0.35) out.top = Surface.PODZOL;
            }
            case "sea" -> {
                out.top = Surface.GRASS;
                if (d < 5 && g <= sea + 3) out.top = n > 0.1 ? Surface.GRAVEL : Surface.STONE;
                if (out.biome.equals("mmorpg:mire")) out.top = n > 0.25 ? Surface.MUD : Surface.GRASS;
                if (g < sea) out.top = out.biome.equals("mmorpg:mire") ? Surface.MUD : Surface.GRAVEL;
            }
            case "desert" -> {
                out.top = Surface.SAND;
                out.under = Surface.SANDSTONE;
                out.depth = 4;
                if (out.biome.equals("mmorpg:red_mesa")) {
                    out.top = g > sea + 24 && n > -0.2 ? Surface.RED_SAND : Surface.TERRACOTTA;
                    out.under = Surface.TERRACOTTA;
                }
                if (out.biome.equals("mmorpg:oasis") && g > sea) out.top = n > -0.3 ? Surface.GRASS : Surface.SAND;
                if (g < sea) out.top = Surface.SAND;
            }
            case "north" -> {
                out.top = Surface.GRASS;
                double rock = sea + 44 + 8 * noise.noise(x / 40.0, z / 40.0);
                if (g > rock) out.top = Surface.STONE;
                if (g > sea + 66 + 6 * n) out.top = Surface.SNOW;
                if (out.biome.equals("mmorpg:glacier")) out.top = n > 0.3 ? Surface.BLUE_ICE : n > -0.35 ? Surface.PACKED_ICE : Surface.SNOW;
                if (d < 5 && g <= sea + 2) out.top = Surface.GRAVEL;
                if (g < sea) out.top = Surface.GRAVEL;
                out.under = out.top == Surface.GRASS ? Surface.DIRT : out.top == Surface.SNOW ? Surface.STONE : out.top;
            }
            case "void" -> {
                boolean helZone = out.biome.equals("mmorpg:helheim");
                double m = noise.noise(x / 26.0 + 9, z / 26.0 - 4);
                if (helZone) out.top = m > 0.35 ? Surface.BONE : m > -0.3 ? Surface.SOUL : Surface.BASALT;
                else out.top = m > 0.45 ? Surface.SCULK : m < -0.5 ? Surface.OBSIDIAN : n > 0.2 ? Surface.VOID_DARK : Surface.VOID;
                out.under = Surface.DEEPSLATE;
                out.depth = 2;
            }
            case "muspel" -> {
                double m = noise.noise(x / 22.0 - 3, z / 22.0 + 5);
                out.top = Math.abs(m) < 0.05 ? Surface.MAGMA : m > 0.3 ? Surface.BASALT : Surface.BLACKSTONE;
                out.under = Surface.BLACKSTONE;
                out.depth = 2;
                for (Volcano vo : volcanoes) {
                    double r = Math.hypot(x - vo.x(), z - vo.z());
                    double crater = vo.r() * 0.17;
                    // 화산 비탈: 현무암 · 꼭대기 쪽 마그마 줄기
                    if (r < vo.r() * 0.95) out.top = Math.abs(m) < 0.09 && r < vo.r() * 0.6 ? Surface.MAGMA : r < vo.r() * 0.75 || n > 0 ? Surface.BASALT : out.top;
                    if (r < crater) {
                        int lavaTop = (int) Math.floor(sea + 4 + vo.h() * Math.pow(1 - 0.17, 1.45) - 4);
                        if (lavaTop > g) {
                            out.water = lavaTop;
                            out.lava = true;
                        }
                    }
                }
            }
            default -> out.top = Surface.GRASS;
        }
        if ((out.flags & (ROAD | LANDING)) != 0) {
            out.top = switch (is.terrain()) {
                case "desert" -> Surface.SANDSTONE;
                case "north" -> Surface.GRAVEL;
                case "void" -> Surface.DEEPSLATE;
                case "muspel" -> Surface.BASALT;
                default -> Surface.PATH;
            };
        } else if ((out.flags & ROAD_EDGE) != 0 && (is.terrain().equals("village") || is.terrain().equals("farm") || is.terrain().equals("sea"))) {
            out.top = n > 0 ? Surface.COARSE : Surface.GRAVEL;
        }
    }

    /** 공허 섬의 떠 있는 바위: 48칸 칸마다 하나 (있으면), 아래가 뾰족한 덩이 */
    private void floating(int x, int z, double d, Column out) {
        if (d < 30) return;
        int cx = Math.floorDiv(x, 48);
        int cz = Math.floorDiv(z, 48);
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int gx = cx + ox;
                int gz = cz + oz;
                if (FieldNoise.hash(gx, gz, 77) > 0.45) continue;
                double px = gx * 48 + 8 + FieldNoise.hash(gx, gz, 78) * 32;
                double pz = gz * 48 + 8 + FieldNoise.hash(gx, gz, 79) * 32;
                double rad = 6 + FieldNoise.hash(gx, gz, 80) * 9;
                double r = Math.hypot(x - px, z - pz);
                if (r >= rad) continue;
                double k = 1 - (r / rad) * (r / rad);
                int top = (int) Math.floor(sea + 44 + FieldNoise.hash(gx, gz, 81) * 26 + 2 * k);
                int bottom = (int) Math.floor(top - 2 - rad * 1.3 * Math.pow(k, 1.6));
                if (top > out.ground + 12) {
                    out.floatTop = top;
                    out.floatBottom = bottom;
                    return;
                }
            }
        }
    }
}
