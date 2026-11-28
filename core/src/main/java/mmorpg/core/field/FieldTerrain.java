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
 * 땅 높이는 섬 지형 종류마다 (마을 · 농장 · 사막 · 바다 · 작은 섬 · 북쪽 · 공허 · 무스펠 · 눈꽃성 · 곶 · 방패 성벽) → 자리 (마을 · 거점 · 보스 신전 ·
 * 다리 끝) 를 평평하게 → 강 · 호수 (field.json rivers · lakes). 바다 밑은 해안에서 멀수록 깊음.
 * <p>
 * 2026-11-28 다리 없음 · 섬을 멀리 (사용자 "텔포로 이동시킬거여서 다리 없애고 그냥 섬을 멀리 둬도 됨"): 옮긴 섬은 잡음을 shapeAt 자리에서 읽어 (칸 + dx · dz)
 * 모양 · 높이 · 겉흙 · 강 굽이가 옮기기 전과 같음. 붙은 두 섬 (북쪽 섬 · 균열 곶) 이 겹친 곳은 두 섬 높이를 섞어 (합친 땅의 해안 거리로) 안쪽 해안이 없음.
 */
public final class FieldTerrain {
    /** 겉흙 (게임은 블록으로, 배치도는 색으로) */
    public enum Surface {
        GRASS, SAND, SANDSTONE, RED_SAND, TERRACOTTA, GRAVEL, STONE, SNOW, PACKED_ICE, BLUE_ICE, PODZOL, MUD, COARSE, CLAY,
        VOID, VOID_DARK, SCULK, OBSIDIAN, SOUL, BONE, BASALT, BLACKSTONE, MAGMA, PATH, DIRT, DEEPSLATE, CALCITE
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
        /** 계단식 논의 물 높이 (번개 나라 섬 — 이 칸이 논이면 땅 = 이것 − 1, 물 = 이것) · 없으면 {@link Integer#MIN_VALUE} */
        public int paddyTop = Integer.MIN_VALUE;
        /** 계단식 논 칸 종류: 0 아님 · 1 둑 (단 아래쪽 가장자리 — 벼랑 면이 돌담) · 2 물 논 · 3 마른 단 */
        public int terrace;

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
    static final java.util.Set<String> CLEARED = java.util.Set.of("camp", "hamlet", "boss", "landmark");
    /** 바이옴 → 같은 지역의 빈터 바이옴 (tools/field_biomes.py CLEAR — 하늘 · 안개 · 색은 같고 나무 · 큰 장식만 없음) */
    static final java.util.Map<String, String> CLEARING = java.util.Map.ofEntries(
            java.util.Map.entry("mmorpg:midgard", "mmorpg:midgard_town"), java.util.Map.entry("mmorpg:midgard_grove", "mmorpg:midgard_town"),
            java.util.Map.entry("mmorpg:harvest_fields", "mmorpg:harvest_clearing"), java.util.Map.entry("mmorpg:harvest_woods", "mmorpg:harvest_clearing"),
            java.util.Map.entry("mmorpg:dune_sea", "mmorpg:dune_clearing"), java.util.Map.entry("mmorpg:red_mesa", "mmorpg:dune_clearing"),
            java.util.Map.entry("mmorpg:oasis", "mmorpg:oasis_clearing"), java.util.Map.entry("mmorpg:fjord_coast", "mmorpg:fjord_clearing"),
            java.util.Map.entry("mmorpg:mire", "mmorpg:mire_clearing"), java.util.Map.entry("mmorpg:frost_taiga", "mmorpg:frost_clearing"),
            java.util.Map.entry("mmorpg:glacier", "mmorpg:glacier_clearing"), java.util.Map.entry("mmorpg:muspelheim", "mmorpg:muspel_clearing"),
            java.util.Map.entry("mmorpg:snowcrown", "mmorpg:snowcrown_clearing"), java.util.Map.entry("mmorpg:rift_cape", "mmorpg:rift_cape_clearing"),
            java.util.Map.entry("mmorpg:shieldwall", "mmorpg:shieldwall_clearing"), java.util.Map.entry("mmorpg:black_fissures", "mmorpg:shieldwall_clearing"),
            java.util.Map.entry("mmorpg:inae_fields", "mmorpg:inae_clearing"), java.util.Map.entry("mmorpg:inae_grove", "mmorpg:inae_clearing"),
            java.util.Map.entry("mmorpg:sage_plateau", "mmorpg:sage_clearing"));

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

    /** 호수와 물 높이 (둘레 땅 − 1) · 그 섬의 무늬 차이 (dx · dz) */
    private record LakeAt(FieldPlan.Lake lake, int level, int ox, int oz) {}

    /** 화산 (무스펠) */
    private record Volcano(double x, double z, double r, double h) {}

    private final FieldPlan plan;
    private final FieldNoise noise;
    private final FieldNoise warp;
    private final int sea;
    private final List<Plot> plots = new ArrayList<>();
    private final List<Span> spans = new ArrayList<>();
    /** 상징 건물로 가는 길 (거점 → 건물 — 토막 차례) */
    private final java.util.Map<String, List<Segment>> roadTo = new java.util.HashMap<>();
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
                case "town", "camp", "hamlet", "landmark" -> flat = s.r();
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
            FieldPlan.Island ri = plan.island(r.island());
            int rox = ri == null ? 0 : ri.dx();
            int roz = ri == null ? 0 : ri.dz();
            for (int i = 1; i < pts.size(); i++) curve(pts.get(i - 1), pts.get(i), r.width(), 0.16, rox, roz, segs);
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
            FieldPlan.Island li = plan.island(l.island());
            lakes.add(new LakeAt(l, Math.max(sea, (int) Math.floor(low) - 1), li == null ? 0 : li.dx(), li == null ? 0 : li.dz()));
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

    /** 거점에서 이 상징 건물 (landmark 자리) 로 가는 길 토막 (없으면 빈 목록) */
    public List<Segment> roadTo(String siteId) {
        return roadTo.getOrDefault(siteId, List.of());
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

    /** 섬의 e (가운데 0 · 해안 1) — 잡음은 무늬 자리 (shapeAt: 칸 + dx · dz) 에서 읽음 */
    double edgeOf(FieldPlan.Island is, double x, double z) {
        double R0 = is.radius();
        double u0 = x - is.x();
        double v0 = z - is.z();
        double r0 = Math.hypot(u0, v0);
        boolean cape = is.terrain().equals("cape");
        if (r0 > R0 * 2.2 && !(cape && r0 < R0 * 3.6)) return r0 / R0;
        double X = x + is.dx();
        double Z = z + is.dz();
        double k = is.index() * 101.0 + 13;
        double ws = R0 * 0.55;
        // 큰 물결로 자리를 비틀어 곶 · 만
        double wx = X + R0 * 0.14 * warp.fbm((X + k) / ws, (Z - k) / ws, 3);
        double wz = Z + R0 * 0.14 * warp.fbm((X - k * 1.7) / ws, (Z + k * 0.6) / ws, 3);
        double u = wx - is.sx();
        double v = wz - is.sz();
        double r = Math.hypot(u, v);
        double ca = r < 1e-6 ? 1 : u / r;
        double sa = r < 1e-6 ? 0 : v / r;
        double n = 0.55 * noise.noise(ca * 1.3 + k, sa * 1.3 - k) + 0.3 * noise.noise(ca * 2.7 - k, sa * 2.7 + k * 0.5)
                + 0.15 * noise.noise(ca * 5.6 + k * 0.3, sa * 5.6 - k * 0.2);
        // 섬마다 한쪽으로 조금 길쭉하게 (방향은 섬 번호로 고정)
        double phi = FieldNoise.hash(is.index(), 7, 3) * Math.PI;
        double stretch = 0.16 * Math.cos(2 * (Math.atan2(sa, ca) - phi));
        // 곶: 남북으로 긴 땅 (동서 폭 0.62) — 늘림은 빼고 잡음만
        if (cape) {
            stretch = 0;
            r = Math.hypot(u / CAPE_WIDTH, v);
        }
        double R = R0 * (1 + is.coast() * 1.6 * n + stretch);
        double e = r / R + 0.035 * noise.fbm((X + k) / 60, (Z - k) / 60, 3);
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
                if (e > 0.97 && e < 1.3 && noise.noise(X / 52.0 + k, Z / 52.0 - k) > 0.42 + (e - 1) * 0.9) e = 0.965;
            }
            case "snowcrown" -> {
                // 남쪽 항구 물목: 남쪽 해안에서 안으로 좁게 들어온 바다 (배가 닿는 곳 — 둘레는 고드름 절벽)
                double a = Math.atan2(sa, ca) - Math.PI / 2;
                double cove = Math.exp(-(a / 0.16) * (a / 0.16));
                e += 0.32 * cove * smoothstep(0.72, 0.93, e);
            }
            default -> {
            }
        }
        return e;
    }

    /** 곶의 동서 폭 (남북 길이에 대한 몫) */
    static final double CAPE_WIDTH = 0.62;
    /** 붙은 두 섬이 섞이는 e 차이 (이보다 차이가 크면 한 섬만) */
    static final double BLEND = 0.16;

    // ------------------------------------------------------------------ 높이

    /** 가장 작은 e 의 섬 (eOut[0] = e) · 둘째 섬 (next[0] · eOut[1]) */
    private FieldPlan.Island owner(double x, double z, double[] eOut, FieldPlan.Island[] next) {
        FieldPlan.Island best = null;
        FieldPlan.Island second = null;
        double be = 9;
        double ne = 9;
        for (FieldPlan.Island is : plan.islands) {
            double e = edgeOf(is, x, z);
            if (e < be) {
                second = best;
                ne = be;
                be = e;
                best = is;
            } else if (e < ne) {
                ne = e;
                second = is;
            }
        }
        eOut[0] = be;
        eOut[1] = ne;
        next[0] = second;
        return best;
    }

    /** 붙은 두 섬 (with) 인지 */
    static boolean joined(FieldPlan.Island a, FieldPlan.Island b) {
        return a != null && b != null && a != b && a.group().equals(b.group());
    }

    /** 땅이 겹치는 붙은 두 섬 (가운데 사이 &lt; 두 반지름 합 — 곶 · 북쪽 섬). 작은 섬처럼 떨어진 것은 섞지 않음 */
    private boolean merged(FieldPlan.Island a, FieldPlan.Island b) {
        return joined(a, b) && Math.hypot(a.x() - b.x(), a.z() - b.z()) < a.radius() + b.radius();
    }

    /** 땅 높이: 붙은 두 섬이 겹친 곳 (e 차이 BLEND 안) 은 둘째 섬 높이도 섞음 — 둘 다 합친 땅의 해안 거리 (가장 작은 e) 로 재서 안쪽 해안이 없음 */
    private double blendHeight(FieldPlan.Island best, double be, FieldPlan.Island next, double ne, double x, double z, Column out) {
        double h = landHeight(best, x, z, (1 - be) * best.radius(), out);
        if (ne - be >= BLEND || !merged(best, next)) return h;
        double h2 = landHeight(next, x, z, (1 - be) * next.radius(), null);
        return lerp(h, h2, 0.5 * (1 - smooth((ne - be) / BLEND)));
    }

    /** 땅 높이 (자리 고르기 전) — 섬 밖이면 바다 밑 */
    double rawHeight(double x, double z) {
        double[] e = new double[2];
        FieldPlan.Island[] nx = new FieldPlan.Island[1];
        FieldPlan.Island best = owner(x, z, e, nx);
        if (best == null || e[0] >= 1) return sea - 6;
        return blendHeight(best, e[0], nx[0], e[1], x, z, null);
    }

    /**
     * 가장 가까운 해안까지 거리 (바다 칸 — 땅이면 0): 섬마다 (e − 1) × 반지름 의 가장 작은 값. 바다 끝 안개 (fogPush · fogBack) 가 이 거리로 밀어냄
     * (가장 작은 e 의 섬 하나로 재면 작은 섬 곁에서 멀게 나와 섬마다 잼).
     */
    public double offshore(double x, double z) {
        double best = Double.MAX_VALUE;
        for (FieldPlan.Island is : plan.islands) {
            double e = edgeOf(is, x, z);
            if (e < 1) return 0;
            best = Math.min(best, (e - 1) * is.radius() * (is.terrain().equals("cape") ? CAPE_WIDTH : 1));
        }
        return best;
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

    /** 지형 종류마다의 땅 높이. out 이 있으면 바이옴 단서도 적음. 잡음은 무늬 자리 (X · Z = 칸 + dx · dz), 자리 (오아시스 · 헬 · 화산) 는 세계 칸으로 */
    private double landHeight(FieldPlan.Island is, double x, double z, double d, Column out) {
        double u = (x - is.x()) / is.radius();
        double v = (z - is.z()) / is.radius();
        double X = x + is.dx();
        double Z = z + is.dz();
        double h;
        switch (is.terrain()) {
            case "village" -> {
                h = sea + 1 + smooth(d / 36) * 7;
                h += 3.5 * noise.fbm(X / 150, Z / 150, 3) * smooth(d / 60) + 1.1 * noise.fbm(X / 34, Z / 34, 2);
                boolean grove = noise.fbm(X / 190 + 3, Z / 190 - 8, 2) > 0.16 && d > 45;
                if (out != null) out.biome = grove ? "mmorpg:midgard_grove" : "mmorpg:midgard";
            }
            case "farm" -> {
                h = sea + 1 + smooth(d / 30) * 5;
                h += 6.5 * noise.fbm(X / 230, Z / 230, 3) * smooth(d / 80) + 2.2 * noise.fbm(X / 55, Z / 55, 2);
                boolean woods = noise.fbm(X / 260 + 11, Z / 260 - 5, 2) > 0.08 && d > 40;
                if (out != null) out.biome = woods ? "mmorpg:harvest_woods" : "mmorpg:harvest_fields";
            }
            case "desert" -> {
                h = sea + 1 + smooth(d / 25) * 4;
                double s = (X * 0.819 + Z * 0.574) / 46;
                double q = (-X * 0.574 + Z * 0.819) / 150;
                double dune = noise.ridged(s, q, 2);
                h += dune * dune * 10 * smooth(d / 50) + 1.4 * noise.fbm(X / 40, Z / 40, 2);
                // 메사: 남쪽에 많은 큰 평평한 바위산, 가장자리는 네 계단
                double m = noise.fbm(X / 330 + 9, Z / 330 - 4, 3) + 0.42 * (v + 0.1) - 0.06;
                double edge = smoothstep(0.1, 0.19, m) * smooth((d - 30) / 50);
                if (edge > 0) {
                    double top = sea + 29 + 4 * noise.fbm(X / 150, Z / 150, 2);
                    double steps = Math.floor(edge * 4) / 4;
                    h = lerp(h, top, lerp(steps, edge, 0.18));
                }
                if (out != null) {
                    out.biome = edge > 0.2 ? "mmorpg:red_mesa" : "mmorpg:dune_sea";
                    if (oasis != null && Math.hypot(x - oasis.x(), z - oasis.z()) < oasis.r() + 45 + 16 * noise.noise(X / 20.0, Z / 20.0)) out.biome = "mmorpg:oasis";
                }
            }
            case "sea" -> {
                h = sea + 1 + smooth(d / 20) * 6;
                h += 7 * noise.fbm(X / 160, Z / 160, 3) * smooth(d / 70) + 2 * noise.fbm(X / 45, Z / 45, 2);
                double west = smoothstep(0.15, 0.6, -u + 0.25 * noise.fbm(X / 200, Z / 200, 2));
                h += west * 15 * smooth(d / 13);
                double mire = smoothstep(0.1, 0.45, v * 0.7 - u * 0.7 - 0.25 + 0.25 * noise.fbm(X / 150 + 3, Z / 150, 2)) * smooth((d - 20) / 40);
                if (mire > 0) h = lerp(h, sea + 0.4 + 1.6 * noise.fbm(X / 22, Z / 22, 2), mire * 0.92);
                if (out != null) out.biome = mire > 0.5 ? "mmorpg:mire" : "mmorpg:fjord_coast";
            }
            case "islet" -> {
                h = sea + 1 + smooth(d / 12) * 5 + 5 * noise.fbm(X / 60, Z / 60, 2) * smooth(d / 30);
                if (out != null) out.biome = "mmorpg:fjord_coast";
            }
            case "north" -> {
                h = sea + 2 + smooth(d / 28) * 6;
                double north = -v;
                double mount = smoothstep(-0.4, 0.3, north + 0.35 * noise.fbm(X / 320, Z / 320, 2)) * smooth(d / 90);
                double ridge = noise.ridged(X / 270, Z / 270, 4);
                h += mount * (10 + 80 * Math.pow(ridge, 1.6)) + 3 * noise.fbm(X / 50, Z / 50, 2);
                double g = smoothstep(0.5, 0.78, north + 0.15 * noise.fbm(X / 200, Z / 200, 2)) * smooth(d / 60);
                if (g > 0) {
                    double ice = sea + 46 + 6 * noise.fbm(X / 120, Z / 120, 2);
                    h = lerp(h, Math.max(ice, h * 0.35 + ice * 0.65), g * 0.9);
                    double c = Math.abs(noise.noise(X / 85 + 4, Z / 85 - 2));
                    if (c < 0.04) h -= 13 * g * smoothstep(0.04, 0.015, c);
                }
                if (out != null) out.biome = g > 0.5 ? "mmorpg:glacier" : h > sea + 52 ? "mmorpg:frost_peaks" : "mmorpg:frost_taiga";
            }
            case "void" -> {
                h = sea + 3 + smooth(d / 16) * 16;
                h += 9 * noise.fbm(X / 120, Z / 120, 3) + 4 * noise.fbm(X / 40, Z / 40, 2);
                h = Math.floor(h / 3) * 3 + 1;
                double c = Math.abs(noise.fbm(X / 230 + 3, Z / 230 - 7, 2));
                if (c < 0.05 && d > 40) h = lerp(h, 14, smoothstep(0.05, 0.022, c));
                if (out != null) {
                    boolean helZone = hel != null && z < hel.z() + 230 + 70 * noise.fbm(X / 160 + 2, Z / 160, 2);
                    out.biome = helZone ? "mmorpg:helheim" : "mmorpg:void_rift";
                }
            }
            case "muspel" -> {
                h = sea + 2 + smooth(d / 22) * 8 + 5 * noise.fbm(X / 90, Z / 90, 3) + 2 * noise.fbm(X / 30, Z / 30, 2);
                for (Volcano vo : volcanoes) {
                    double r = Math.hypot(x - vo.x(), z - vo.z());
                    if (r >= vo.r()) continue;
                    double cone = sea + 4 + vo.h() * Math.pow(1 - r / vo.r(), 1.45) + 3 * noise.fbm(X / 25, Z / 25, 2);
                    double crater = vo.r() * 0.17;
                    if (r < crater) cone = sea + 4 + vo.h() * Math.pow(1 - crater / vo.r(), 1.45) - 9 * smooth(1 - r / crater);
                    h = Math.max(h, cone);
                }
                if (out != null) out.biome = "mmorpg:muspelheim";
            }
            case "snowcrown" -> {
                // 눈꽃성 섬 (2026-11-28 · map-layout 2-5): 눈 덮인 높은 고원 · 해안은 고드름 절벽 (28칸 안에서 솟음) · 북쪽으로 조금 높아짐
                double plateau = sea + 30 - 4 * v + 4 * noise.fbm(X / 170, Z / 170, 3) + 1.5 * noise.fbm(X / 40, Z / 40, 2);
                double rise = 0.85 * smoothstep(1, 12, d) + 0.15 * smoothstep(12, 34, d);
                h = sea + 1 + (plateau - sea - 1) * rise;
                // 언덕 몇: 성이 설 가운데는 비움
                double hill = noise.ridged(X / 140 + 5, Z / 140 - 3, 3);
                h += 10 * Math.pow(hill, 2.2) * smoothstep(0.35, 0.6, Math.hypot(u, v)) * smooth(d / 40);
                if (out != null) out.biome = "mmorpg:snowcrown";
            }
            case "cape" -> {
                // 균열 곶 (2026-11-28 · map-layout 2-7): 남쪽 끝 = 북쪽 섬 빙하와 만나는 산줄기 (가운데 x 0 에 고개),
                // 가운데 = 전초기지 들 (낮음), 북쪽 끝 = 바위 곶 (해안은 깊은 바다 골로 떨어지는 절벽)
                double ux = x - is.x();
                double ridge = sea + 46 + 16 * Math.pow(noise.ridged(X / 60, Z / 60, 3), 1.4) - 15 * Math.exp(-(ux / 34) * (ux / 34));
                double plain = sea + 12 + 3 * noise.fbm(X / 90, Z / 90, 3) + 1.2 * noise.fbm(X / 26, Z / 26, 2);
                double head = sea + 17 + 5 * noise.fbm(X / 60, Z / 60, 2) + 4 * smoothstep(-0.45, -0.85, v);
                double hIn = lerp(plain, ridge, smoothstep(0.42, 0.7, v));
                hIn = lerp(hIn, head, smoothstep(-0.38, -0.6, v));
                double cliff = smooth(d / 16);
                h = sea + 1 + (hIn - sea - 1) * cliff;
                if (out != null) out.biome = "mmorpg:rift_cape";
            }
            case "shieldwall" -> {
                // 불 나라 섬 (2026-11-28 · map-layout 2-9): 붉은 바위 들 · 바위 기둥 · 동서의 검은 틈 들 · 남쪽 해안 절벽 (큰 성벽 자리)
                h = sea + 3 + smooth(d / 24) * 7 + 5 * noise.fbm(X / 140, Z / 140, 3) + 1.6 * noise.fbm(X / 34, Z / 34, 2);
                double butte = noise.ridged(X / 75 + 2, Z / 75 - 9, 2);
                if (butte > 0.86 && d > 40) h += 16 * smoothstep(0.86, 0.93, butte);
                // 남쪽은 바다 쪽으로 천천히 높아져 해안이 절벽 (큰 성벽이 설 높은 곳 — 무스펠을 봄)
                double south = smoothstep(0.15 + 0.12 * noise.fbm(X / 120 + 7, Z / 120, 2), 0.85, v) * smooth(d / 12);
                h = Math.max(h, lerp(h, sea + 22 + 3 * noise.fbm(X / 50, Z / 50, 2), south));
                double side = smoothstep(0.18, 0.4, Math.abs(u)) * smoothstep(0.55, 0.2, v) * smooth((d - 30) / 40);
                double crack = Math.abs(noise.fbm(X / 70 + 4, Z / 70 + 1, 2));
                boolean fissure = side > 0.3 && crack < 0.07;
                if (fissure) h -= 9 * smoothstep(0.07, 0.03, crack) * side;
                if (out != null) out.biome = side > 0.45 ? "mmorpg:black_fissures" : "mmorpg:shieldwall";
            }
            case "thunder" -> {
                // 번개 나라 섬 (2026-11-28 · 사용자 "번개의 나라 … 추가해도 됨"): 가운데 산 꼭대기 = 벼락받이 사당, 산허리 = 계단식 논 (세 칸 단 · 물 한 칸 ·
                // 아래쪽 가장자리 둑), 동서 산허리 = 대숲 · 벚나무, 낮은 들 · 남쪽 마을. 사당으로 가는 길 둘레는 단을 풀어 오르막 (계단 벼랑이 없게)
                double hc = thunderHeight(is, x, z, d);
                double rr = Math.hypot(u, v);
                double grove = smoothstep(0.02, 0.22, noise.fbm(X / 210 + 4, Z / 210 - 6, 2)) * smoothstep(0.18, 0.42, rr);
                double band = smoothstep(sea + 6, sea + 10, hc) * smoothstep(sea + 54, sea + 48, hc) * (1 - grove) * smooth((d - 10) / 40);
                h = hc;
                if (band > 0.5) {
                    double rd = roads.isEmpty() ? 1e9 : roadDistance(x, z);
                    double ramp = smoothstep(9, 5, rd);
                    double rel = hc - sea - 1;
                    double k = Math.floor(rel / TERRACE);
                    double f = rel / TERRACE - k;
                    double ht = sea + 1 + k * TERRACE;
                    h = lerp(ht, hc, ramp);
                    if (out != null && ramp < 0.5) {
                        out.terrace = f < BUND ? 1 : 3;
                        // 논 한 배미: 단을 둘레로 약 24 칸씩 나눔 (단마다 나눈 수가 같음 — 단의 대략 반지름에서), 배미 사이 한 칸은 마른 두렁.
                        // 배미마다 물을 대거나 마른 채 (조각보), 기울기가 가파르면 마른 단 (둑이 좁아 물이 아래 단으로 넘침)
                        double ringR = 0.72 * (1 - Math.pow(clamp01((ht - sea - 6) / 50), 1 / 1.35)) * is.radius();
                        int cells = Math.max(6, (int) Math.round(2 * Math.PI * ringR / 24));
                        double a = (Math.atan2(v, u) + Math.PI) / (2 * Math.PI) * cells;
                        int cell = (int) Math.floor(a);
                        double edge = Math.min(a - cell, cell + 1 - a) * 2 * Math.PI / cells * rr * is.radius();
                        boolean wet = edge >= 0.7 && FieldNoise.hash((int) k, cell, 47) < 0.6;
                        if (wet && band > 0.85 && ramp == 0 && f >= BUND) {
                            double gx = thunderHeight(is, x + 1, z, d) - hc;
                            double gz = thunderHeight(is, x, z + 1, d) - hc;
                            if (Math.hypot(gx, gz) < TERRACE * BUND) {
                                out.paddyTop = (int) Math.floor(ht);
                                out.terrace = 2;
                            }
                        }
                    }
                }
                if (out != null) out.biome = rr < 0.13 ? "mmorpg:inae_peak" : grove > 0.5 ? "mmorpg:inae_grove" : "mmorpg:inae_fields";
            }
            case "sage" -> {
                // 바위 나라 섬 (2026-11-28 · 사용자 "땅의 나라 … 추가해도 됨"): 사암 절벽으로 둘린 높은 고원 · 가운데 언덕 (큰 저울의 받침점) ·
                // 동북의 무게 없는 곳 (땅이 갈라지고 위로 바위가 뜸 — 나라의 징조, 세계관 2-6)
                double rr = Math.hypot(u, v);
                double plateau = sea + 28 + 3 * noise.fbm(X / 160, Z / 160, 3) + 1.2 * noise.fbm(X / 36, Z / 36, 2);
                double rise = smoothstep(1, 16, d);
                h = sea + 1 + (plateau - sea - 1) * rise;
                h += 24 * Math.pow(Math.max(0, 1 - rr / 0.36), 1.25) * rise;
                double w = weightless(is, x, z);
                if (w > 0) {
                    double c = Math.abs(noise.fbm(X / 46 + 2, Z / 46 - 5, 2));
                    if (c < 0.09) h -= 14 * w * smoothstep(0.09, 0.03, c);
                    h -= 3 * w;
                }
                if (out != null) out.biome = w > 0.45 ? "mmorpg:weightless" : "mmorpg:sage_plateau";
            }
            default -> {
                h = sea + 1 + smooth(d / 20) * 4;
                if (out != null) out.biome = "mmorpg:midgard";
            }
        }
        return h;
    }

    /** 계단식 논 한 단의 높이 · 둑 (단의 아래쪽 몫) */
    static final int TERRACE = 3;
    static final double BUND = 0.22;

    /** 번개 나라 섬의 이어진 땅 높이 (단으로 자르기 전) */
    private double thunderHeight(FieldPlan.Island is, double x, double z, double d) {
        double X = x + is.dx();
        double Z = z + is.dz();
        double rr = Math.hypot((x - is.x()) / is.radius(), (z - is.z()) / is.radius());
        double base = sea + 2 + smooth(d / 26) * 5 + 3 * noise.fbm(X / 150, Z / 150, 3) + 1.2 * noise.fbm(X / 40, Z / 40, 2);
        double mt = Math.pow(Math.max(0, 1 - rr / 0.72), 1.35) * 50 * (0.88 + 0.12 * noise.fbm(X / 90, Z / 90, 2));
        double ridge = 9 * Math.pow(noise.ridged(X / 110, Z / 110, 3), 2) * smoothstep(0.75, 0.25, rr);
        return base + (mt + ridge) * smooth(d / 50);
    }

    /** 바위 나라 섬의 무게 없는 곳 (동북 — 0 바깥 · 1 한가운데) */
    public double weightless(FieldPlan.Island is, double x, double z) {
        double R = 0.30 * is.radius();
        double cx = is.x() + 0.42 * is.radius();
        double cz = is.z() - 0.30 * is.radius();
        double q = Math.hypot(x - cx, z - cz) / R + 0.18 * noise.fbm((x + is.dx()) / 90 + 11, (z + is.dz()) / 90 - 3, 2);
        return smoothstep(1.0, 0.55, q);
    }

    // ------------------------------------------------------------------ 길

    private void buildRoads() {
        for (FieldPlan.Island is : plan.islands) {
            List<double[]> nodes = new ArrayList<>();
            List<double[]> landings = new ArrayList<>();
            double[] hub = null;
            // 2026-11-28 상징 건물 (landmark) 은 거점에서 곧장 (사당으로 오르는 길 — 그 길에 도리이를 세움, roadTo)
            List<FieldPlan.Site> marks = new ArrayList<>();
            for (FieldPlan.Site s : plan.sites) if (s.island().equals(is.id()) && s.kind().equals("landmark")) marks.add(s);
            // 거점 = 섬의 첫 거점 · 마을 (2026-11-28 바위 나라 섬은 접시 둘 — 둘째부터는 길로 이을 자리)
            List<double[]> camps = new ArrayList<>();
            for (FieldPlan.Site s : plan.sites) {
                if (!s.island().equals(is.id())) continue;
                if (s.kind().equals("camp") || s.kind().equals("town")) {
                    if (hub == null) hub = new double[] {s.x(), s.z()};
                    else camps.add(new double[] {s.x(), s.z()});
                }
            }
            for (Span sp : spans) {
                if (sp.bridge().from().equals(is.id())) landings.add(new double[] {sp.ax(), sp.az()});
                if (sp.bridge().to().equals(is.id())) landings.add(new double[] {sp.bx(), sp.bz()});
            }
            for (FieldPlan.Site s : plan.sites) {
                if (!s.island().equals(is.id())) continue;
                if (s.kind().equals("zone") || s.kind().equals("boss") || s.kind().equals("tree") || s.kind().equals("hamlet")) nodes.add(new double[] {s.x(), s.z()});
            }
            nodes.addAll(0, camps);
            if (hub == null && !landings.isEmpty()) hub = landings.remove(0);
            if (hub == null && !nodes.isEmpty()) hub = nodes.remove(0);
            if (hub == null) continue;
            // 다리 끝은 거점 (마을) 으로 곧장, 나머지는 가까운 것부터 이어 붙임 (Prim): 이미 이어진 점 가운데 가장 가까운 곳에서 갈라짐
            List<double[]> done = new ArrayList<>();
            done.add(hub);
            for (double[] l : landings) {
                curve(hub, l, 3.0, 0.07, is.dx(), is.dz(), roads);
                done.add(l);
            }
            for (FieldPlan.Site m : marks) {
                double[] l = {m.x(), m.z()};
                int before = roads.size();
                curve(hub, l, 3.0, 0.07, is.dx(), is.dz(), roads);
                roadTo.put(m.id(), List.copyOf(roads.subList(before, roads.size())));
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
                curve(from, to, 3.0, 0.07, is.dx(), is.dz(), roads);
                done.add(to);
            }
        }
    }

    /** 굽은 줄: 토막을 나눠 가운데를 옆으로 밀어 냄 (자리마다 고정 — 잡음은 그 섬의 무늬 자리: 칸 + ox · oz) */
    private void curve(double[] a, double[] b, double width, double bendK, int ox, int oz, List<Segment> out) {
        double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
        int n = Math.max(1, (int) (len / 24));
        double nx = -(b[1] - a[1]) / Math.max(1e-6, len);
        double nz = (b[0] - a[0]) / Math.max(1e-6, len);
        double px = a[0];
        double pz = a[1];
        for (int i = 1; i <= n; i++) {
            double t = (double) i / n;
            double bend = i == n ? 0
                    : Math.sin(Math.PI * t) * len * bendK * noise.noise((a[0] + ox) / 97 + (b[1] + oz) / 131, (a[1] + oz) / 89 + t * 1.7)
                            + Math.sin(Math.PI * t) * 6 * noise.noise((a[0] + ox) / 37 + t * 4.1, (b[0] + ox) / 41 - t * 3.3);
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
        out.paddyTop = Integer.MIN_VALUE;
        out.terrace = 0;
        out.water = Integer.MIN_VALUE;
        out.lava = false;
        out.floatBottom = 0;
        out.floatTop = -1;
        out.depth = 3;
        double[] eo = new double[2];
        FieldPlan.Island[] nx = new FieldPlan.Island[1];
        FieldPlan.Island best = owner(x, z, eo, nx);
        double be = eo[0];
        out.island = best;
        if (best == null || be >= 1) {
            seaFloor(x, z, best, be, out);
            return out;
        }
        double d = (1 - be) * best.radius();
        out.coast = d;
        double h = blendHeight(best, be, nx[0], eo[1], x, z, out);
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
            double wob = l.r() * (1 + 0.18 * noise.noise((x + la.ox()) / 14.0 + (l.x() + la.ox()), (z + la.oz()) / 14.0 - (l.z() + la.oz())));
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
        // 계단식 논 (번개 나라 섬): 자리 · 길 · 강이 아니고 높이가 그대로인 칸만 — 한 칸 파고 물 (둑은 땅 그대로)
        if (out.paddyTop != Integer.MIN_VALUE && out.paddyTop == out.ground && (out.flags & (PLOT | ROAD | ROAD_EDGE | RIVER)) == 0 && !out.hasWater()) {
            out.ground = out.paddyTop - 1;
            out.water = out.paddyTop;
            out.top = Surface.MUD;
            out.under = Surface.DIRT;
            out.depth = 2;
        }
        if (best.terrain().equals("void")) floating(best, x, z, d, out);
        else if (best.terrain().equals("sage")) floatingSage(best, x, z, out);
        return out;
    }

    private void seaFloor(int x, int z, FieldPlan.Island near, double e, Column out) {
        double R = near == null ? 400 : near.radius() * (near.terrain().equals("cape") ? CAPE_WIDTH : 1);
        double dc = Math.max(0, (e - 1) * R);
        out.coast = -dc;
        int X = near == null ? x : x + near.dx();
        int Z = near == null ? z : z + near.dz();
        double floor = sea - 3 - 22 * smooth(dc / 70) - 3 * noise.fbm(X / 80.0, Z / 80.0, 2);
        out.ground = (int) Math.floor(floor);
        out.water = sea;
        String t = near == null ? "" : near.terrain();
        boolean shallow = dc < 36;
        switch (t) {
            case "north", "snowcrown" -> {
                out.biome = shallow ? "mmorpg:frozen_shallows" : "mmorpg:open_sea";
                out.top = Surface.GRAVEL;
            }
            case "void", "cape" -> {
                out.biome = shallow ? "mmorpg:rift_shallows" : "mmorpg:open_sea";
                out.top = Surface.DEEPSLATE;
            }
            case "shieldwall" -> {
                out.biome = shallow ? "mmorpg:shallows" : "mmorpg:open_sea";
                out.top = dc < 26 ? Surface.RED_SAND : noise.noise(X / 40.0, Z / 40.0) > 0.2 ? Surface.TERRACOTTA : Surface.GRAVEL;
            }
            case "muspel" -> {
                out.biome = shallow ? "mmorpg:ember_shallows" : "mmorpg:open_sea";
                out.top = Surface.BASALT;
            }
            default -> {
                out.biome = shallow ? "mmorpg:shallows" : "mmorpg:open_sea";
                out.top = dc < 30 ? Surface.SAND : noise.noise(X / 40.0, Z / 40.0) > 0.3 ? Surface.CLAY : Surface.GRAVEL;
            }
        }
        out.under = out.top == Surface.SAND ? Surface.SANDSTONE : Surface.STONE;
        out.depth = 3;
    }

    private void surface(FieldPlan.Island is, int x, int z, double d, Column out) {
        int g = out.ground;
        int X = x + is.dx();
        int Z = z + is.dz();
        double n = noise.noise(X / 18.0, Z / 18.0);
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
                double rock = sea + 44 + 8 * noise.noise(X / 40.0, Z / 40.0);
                if (g > rock) out.top = Surface.STONE;
                if (g > sea + 66 + 6 * n) out.top = Surface.SNOW;
                if (out.biome.equals("mmorpg:glacier")) out.top = n > 0.3 ? Surface.BLUE_ICE : n > -0.35 ? Surface.PACKED_ICE : Surface.SNOW;
                if (d < 5 && g <= sea + 2) out.top = Surface.GRAVEL;
                if (g < sea) out.top = Surface.GRAVEL;
                out.under = out.top == Surface.GRASS ? Surface.DIRT : out.top == Surface.SNOW ? Surface.STONE : out.top;
            }
            case "void" -> {
                boolean helZone = out.biome.equals("mmorpg:helheim");
                double m = noise.noise(X / 26.0 + 9, Z / 26.0 - 4);
                if (helZone) out.top = m > 0.35 ? Surface.BONE : m > -0.3 ? Surface.SOUL : Surface.BASALT;
                else out.top = m > 0.45 ? Surface.SCULK : m < -0.5 ? Surface.OBSIDIAN : n > 0.2 ? Surface.VOID_DARK : Surface.VOID;
                out.under = Surface.DEEPSLATE;
                out.depth = 2;
            }
            case "muspel" -> {
                double m = noise.noise(X / 22.0 - 3, Z / 22.0 + 5);
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
            case "snowcrown" -> {
                // 고원은 눈 · 얼음 조각, 해안 절벽 (28칸 안) 은 겉이 얼음이라 절벽 면이 얼음 기둥으로 보임 (고드름 절벽)
                out.top = n > 0.48 ? Surface.PACKED_ICE : Surface.SNOW;
                out.under = Surface.STONE;
                if (d < 30) {
                    out.top = n > 0.15 ? Surface.PACKED_ICE : n > -0.3 ? Surface.SNOW : Surface.BLUE_ICE;
                    out.under = n > -0.1 ? Surface.PACKED_ICE : Surface.BLUE_ICE;
                    out.depth = 9;
                }
                if (d < 4 && g <= sea + 2) out.top = Surface.GRAVEL;
                if (g < sea) out.top = Surface.GRAVEL;
            }
            case "cape" -> {
                // 눈 · 자갈 · 돌, 북쪽 끝으로 갈수록 공허에 물든 땅 (심층암 · 검은 공허 · 흑요석 줄)
                double v = (z - is.z()) / is.radius();
                double m = noise.noise(X / 24.0 + 6, Z / 24.0 - 2);
                out.top = g > sea + 40 ? (n > -0.2 ? Surface.SNOW : Surface.STONE) : n > 0.2 ? Surface.SNOW : n > -0.35 ? Surface.GRAVEL : Surface.STONE;
                out.under = Surface.STONE;
                double taint = smoothstep(-0.1, -0.7, v);
                if (m < -1 + taint * 1.5) out.top = m < -0.85 + taint ? Surface.VOID_DARK : Surface.DEEPSLATE;
                if (taint > 0.6 && Math.abs(m) < 0.03) out.top = Surface.OBSIDIAN;
                if (d < 16) {
                    out.under = Surface.DEEPSLATE;
                    out.depth = 6;
                }
                if (g < sea) out.top = Surface.GRAVEL;
            }
            case "shieldwall" -> {
                boolean fissures = out.biome.equals("mmorpg:black_fissures");
                double m = noise.noise(X / 20.0 - 7, Z / 20.0 + 3);
                out.top = n > 0.3 ? Surface.TERRACOTTA : n > -0.45 ? Surface.RED_SAND : Surface.COARSE;
                out.under = Surface.TERRACOTTA;
                out.depth = 4;
                if (fissures && m > 0.25) out.top = m > 0.55 ? Surface.OBSIDIAN : Surface.BLACKSTONE;
                if (fissures && g < sea + 3) out.top = Surface.BLACKSTONE;
                if (g < sea) out.top = Surface.RED_SAND;
            }
            case "thunder" -> {
                out.top = Surface.GRASS;
                // 단의 둑: 벼랑 면 (아래 단에서 보이는 세 칸) 이 돌담
                if (out.terrace == 1) {
                    out.under = Surface.STONE;
                    out.depth = 4;
                }
                if (out.biome.equals("mmorpg:inae_grove") && n > 0.3) out.top = Surface.PODZOL;
                if (out.biome.equals("mmorpg:inae_peak")) out.top = n > 0.35 ? Surface.STONE : n > -0.2 ? Surface.GRASS : Surface.GRAVEL;
                if (d < 5 && g <= sea + 2) {
                    out.top = Surface.SAND;
                    out.under = Surface.SANDSTONE;
                }
                if (g < sea) out.top = d < 12 ? Surface.SAND : Surface.DIRT;
            }
            case "sage" -> {
                // 고원은 풀 · 거친 흙, 해안 절벽 (16칸 안) 은 사암 (절벽 면이 사암 띠), 무게 없는 곳은 방해석 · 돌 · 자갈, 틈 바닥은 심층암 · 흑요석
                double w = weightless(is, x, z);
                out.top = n > 0.62 ? Surface.COARSE : Surface.GRASS;
                if (d < 16) {
                    out.under = Surface.SANDSTONE;
                    out.depth = 8;
                    if (d < 6 && n > 0) out.top = Surface.SANDSTONE;
                }
                if (w > 0.35) {
                    double m = noise.noise(X / 18.0 + 3, Z / 18.0 - 8);
                    out.top = m > 0.25 ? Surface.CALCITE : m > -0.25 ? Surface.STONE : Surface.GRAVEL;
                    out.under = Surface.STONE;
                    if (g < sea + 22) out.top = n > 0 ? Surface.DEEPSLATE : Surface.OBSIDIAN;
                }
                if (g < sea) out.top = Surface.SAND;
            }
            default -> out.top = Surface.GRASS;
        }
        if ((out.flags & (ROAD | LANDING)) != 0) {
            out.top = switch (is.terrain()) {
                case "desert" -> Surface.SANDSTONE;
                case "north", "snowcrown", "cape" -> Surface.GRAVEL;
                case "sage" -> Surface.SANDSTONE;
                case "void" -> Surface.DEEPSLATE;
                case "muspel" -> Surface.BASALT;
                case "shieldwall" -> Surface.COARSE;
                default -> Surface.PATH;
            };
        } else if ((out.flags & ROAD_EDGE) != 0 && (is.terrain().equals("village") || is.terrain().equals("farm") || is.terrain().equals("sea"))) {
            out.top = n > 0 ? Surface.COARSE : Surface.GRAVEL;
        }
    }

    /** 공허 섬의 떠 있는 바위: 48칸 칸마다 하나 (있으면), 아래가 뾰족한 덩이 — 칸은 무늬 자리 (섬을 옮겨도 같은 바위) */
    private void floating(FieldPlan.Island is, int wx, int wz, double d, Column out) {
        if (d < 30) return;
        int x = wx + is.dx();
        int z = wz + is.dz();
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

    /**
     * 바위 나라 섬 무게 없는 곳의 떠 있는 바위 (2026-11-28): 36칸 칸마다 하나 (있으면 — 바위 가운데가 무게 없는 곳 안일 때만), 땅에서 10칸 넘게 위.
     * 블록은 생성기가 방해석 · 사암으로 (FieldBlocks).
     */
    private void floatingSage(FieldPlan.Island is, int wx, int wz, Column out) {
        int x = wx + is.dx();
        int z = wz + is.dz();
        int cell = 36;
        int cx = Math.floorDiv(x, cell);
        int cz = Math.floorDiv(z, cell);
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int gx = cx + ox;
                int gz = cz + oz;
                if (FieldNoise.hash(gx, gz, 91) > 0.6) continue;
                double px = gx * cell + 6 + FieldNoise.hash(gx, gz, 92) * 24;
                double pz = gz * cell + 6 + FieldNoise.hash(gx, gz, 93) * 24;
                double wc = weightless(is, px - is.dx(), pz - is.dz());
                if (wc < 0.4) continue;
                double rad = 3 + FieldNoise.hash(gx, gz, 94) * 7 * (0.6 + 0.4 * wc);
                double r = Math.hypot(x - px, z - pz);
                if (r >= rad) continue;
                double k = 1 - (r / rad) * (r / rad);
                int top = (int) Math.floor(sea + 46 + FieldNoise.hash(gx, gz, 95) * 34 + 2 * k);
                int bottom = (int) Math.floor(top - 1 - rad * 1.1 * Math.pow(k, 1.5));
                if (top > out.ground + 10) {
                    out.floatTop = top;
                    out.floatBottom = bottom;
                    return;
                }
            }
        }
    }
}
