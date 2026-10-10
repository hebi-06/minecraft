package mmorpg.fabric.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mmorpg.core.Obj;
import mmorpg.core.Rules;

/**
 * 세계수 설계 (worldtree.js treePlanGen · fingerprintGen · planFingerprint · rootReachOf · seedOf · rng · hash3 · vnoise) — 마인크래프트 클래스 없이.
 * 밑동 가운데 칸 (땅 바로 위) 기준 상대 칸들을 자라는 차례대로 단계에 나눔: 뿌리 → 줄기 (6칸씩 아래부터) → 가지 → 잎 (셋) → 빛 (열매 · 덩굴 · 포자 꽃) → 이끼.
 * <p>
 * 같은 높이 · 씨앗이면 애드온과 칸마다 같은 설계 (같은 칸 · 같은 블록 · 같은 놓는 법 · 같은 차례 → 같은 지문 fp):
 * 블록은 애드온의 베드락 이름 · 상태 그대로 (자바 블록 상태로는 {@link TreeBlocks#state}), 씨앗 난수 (mulberry32) · 칸 잡음 (hash3) 은 32비트 정수 연산 그대로,
 * Math.sin · cos · atan2 · pow · hypot 은 StrictMath (fdlibm — QuickJS + 모바일 libm 과 같은 계열, 세 값 hypot 은 QuickJS 처럼 hypot(hypot(x, y), z)),
 * Math.round 는 JS 와 같은 반올림 (반은 +∞ 쪽), 정렬은 JS Array.sort 처럼 안정 정렬, 칸 표는 넣은 차례를 지키는 LinkedHashMap (JS Map).
 * <p>
 * 자바에서 달라진 점: 애드온은 발생기 (yield) 로 여러 틱에 나눠 계산 (system.runJob) — 자바는 이 계산 전체를 서버 스레드 밖의 일꾼 스레드에서
 * ({@link WorldTree} 의 planAsync). 이 클래스는 공유 상태가 없어 어느 스레드에서 불러도 됨.
 */
public final class TreePlan {
    // ------------------------------------------------------------ 블록 (worldtree.js TREE_BLOCKS — 베드락 이름, 지문에 들어감)
    public static final String WOOD = "minecraft:pale_oak_wood";
    public static final String RESIN = "minecraft:resin_block";
    public static final String MOSS = "minecraft:moss_block";
    public static final String LEAF = "minecraft:azalea_leaves";
    public static final String BLOOM = "minecraft:azalea_leaves_flowered";
    public static final String FRUIT = "minecraft:shroomlight";
    public static final String VINE = "minecraft:cave_vines_body_with_berries";
    public static final String VINE_TIP = "minecraft:cave_vines_head_with_berries";
    public static final String BLOSSOM = "minecraft:spore_blossom";
    public static final String CARPET = "minecraft:moss_carpet";
    public static final String FIREFLY = "minecraft:firefly_bush";
    public static final String AZALEA = "minecraft:flowering_azalea";

    /** 잎은 떨어지지 않게 (통나무가 멀어도) — 애드온 LEAF_STATES */
    public static final Map<String, Object> LEAF_STATES = states("persistent_bit", true, "update_bit", false);
    /** 덩굴 마디 · 끝 (끝은 다 자란 나이 25 → 더 자라지 않음) */
    public static final Map<String, Object> VINE_STATES = states("growing_plant_age", 0);
    public static final Map<String, Object> VINE_TIP_STATES = states("growing_plant_age", 25);

    /** 놓는 법 (worldtree.js MODES — 차례가 지문에 들어감) */
    public enum Mode {
        /** 줄기 · 뿌리 · 가지: 공기 · 자연 지형 · 들의 식물 · 다른 나무의 통나무만 바꿈 */
        WOOD,
        /** 잎 · 열매 · 덩굴을 매다는 이끼: 빈 곳 · 풀 · 꽃 · 다른 나무의 잎에만 */
        LEAF,
        /** 덩굴 · 포자 꽃: 빈 칸이고 위가 단단한 블록일 때만 */
        HANG,
        /** 이끼 깔개: 그 자리 둘레 (위 2 ~ 아래 4칸) 의 자연 땅 · 뿌리 위 */
        CARPET,
        /** 반딧불이 덤불 · 꽃 핀 진달래: 깔개와 같되 흙 종류 위에만 */
        BUSH;

        /** 애드온의 이름 (wood · leaf · hang · carpet · bush) */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** 점 (밑동 기준, 칸 단위) */
    public record Pt(double x, double y, double z) {}

    /** 놓을 칸 하나 (밑동 기준 dx · dy · dz, 베드락 블록 이름 · 상태 (없으면 null), 놓는 법) — worldtree.js TreeOp */
    public record Op(int dx, int dy, int dz, String block, Map<String, Object> states, Mode mode) {}

    /** 단계 (이름 · 종류 root · trunk · branch · leaf · glow · ground · 끝날 때 소리 (베드락 이름) · 높낮이 · 연출 높이 · 칸) — worldtree.js TreeStage */
    public record Stage(String name, String kind, String sound, double pitch, int y, List<Op> ops) {}

    // ------------------------------------------------------------ 설계 결과 (worldtree.js TreePlan)
    /** 높이 (40 ~ 96 로 맞춘 것) */
    public final int H;
    public final int seed;
    public final List<Stage> stages;
    /** 밑동에서 가장 먼 칸 (가로, 깔개 고리 포함 — 틱킹 영역 · 불러오기 · 보스 필드 거리) */
    public final int reach;
    /** 뿌리 끝까지 (가로) */
    public final int rootReach;
    /** 잎 지붕 반지름 (가로) */
    public final int canopy;
    /** 밑동 반지름 */
    public final double trunkR;
    public final int top;
    public final int bottom;
    /** 칸 수 (깔개 · 덤불 포함) */
    public final int total;
    public final int woodCount;
    /** 블록마다 칸 수 (넣은 차례) */
    public final Map<String, Integer> counts;
    /** 가지 가운데 (단계 연출 자리) */
    public final List<Pt> branches;
    /** 설계 지문 (FNV-1a, 0 ~ 2^32-1): 되돌릴 때 다시 계산한 설계가 심을 때와 같은지 */
    public final long fp;

    private TreePlan(int H, int seed, List<Stage> stages, int reach, int rootReach, int canopy, double trunkR, int top, int bottom, int total, int woodCount,
            Map<String, Integer> counts, List<Pt> branches, long fp) {
        this.H = H;
        this.seed = seed;
        this.stages = stages;
        this.reach = reach;
        this.rootReach = rootReach;
        this.canopy = canopy;
        this.trunkR = trunkR;
        this.top = top;
        this.bottom = bottom;
        this.total = total;
        this.woodCount = woodCount;
        this.counts = counts;
        this.branches = branches;
        this.fp = fp;
    }

    // ------------------------------------------------------------ 설정 (config.js WORLD_TREE)

    private static Obj cfg() {
        return Rules.get().obj("WORLD_TREE");
    }

    // ------------------------------------------------------------ 수학 (JS 와 같은 값)

    static final double TAU = Math.PI * 2;
    static final double DEG = Math.PI / 180;

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static double sin(double v) {
        return StrictMath.sin(v);
    }

    static double cos(double v) {
        return StrictMath.cos(v);
    }

    static double pow(double a, double b) {
        return StrictMath.pow(a, b);
    }

    static double hypot(double x, double y) {
        return StrictMath.hypot(x, y);
    }

    /** JS Math.hypot(x, y, z) (QuickJS: hypot(hypot(x, y), z)) */
    static double hypot(double x, double y, double z) {
        return StrictMath.hypot(StrictMath.hypot(x, y), z);
    }

    /** JS Math.round (반은 +∞ 쪽) */
    static int round(double v) {
        return (int) Math.round(v);
    }

    /** 정해진 씨앗의 난수 0 ~ 1 (worldtree.js rng — mulberry32) */
    static final class Rng {
        private int a;

        Rng(int seed) {
            this.a = seed;
        }

        double next() {
            a += 0x6d2b79f5;
            int t = a;
            t = (t ^ (t >>> 15)) * (t | 1);
            t ^= t + (t ^ (t >>> 7)) * (t | 61);
            return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0;
        }
    }

    /** 칸마다 정해진 잡음 0 ~ 1 (worldtree.js hash3) */
    public static double hash3(int x, int y, int z, int k) {
        int h = (x * 374761393) ^ (y * 668265263) ^ (z * 1274126177) ^ (k * 1911520717);
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFFFFL) / 4294967296.0;
    }

    /** 부드러운 잡음 0 ~ 1 (worldtree.js vnoise — 이끼 얼룩) */
    static double vnoise(double x, double y, double z, int k) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        int z0 = (int) Math.floor(z);
        double fx = x - x0;
        double fy = y - y0;
        double fz = z - z0;
        double u = fx * fx * (3 - 2 * fx);
        double v = fy * fy * (3 - 2 * fy);
        double w = fz * fz * (3 - 2 * fz);
        return lerp(lerp(lerp(hash3(x0, y0, z0, k), hash3(x0 + 1, y0, z0, k), u), lerp(hash3(x0, y0 + 1, z0, k), hash3(x0 + 1, y0 + 1, z0, k), u), v),
                lerp(lerp(hash3(x0, y0, z0 + 1, k), hash3(x0 + 1, y0, z0 + 1, k), u), lerp(hash3(x0, y0 + 1, z0 + 1, k), hash3(x0 + 1, y0 + 1, z0 + 1, k), u), v), w);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** 자리에서 씨앗 (같은 자리 = 같은 나무 — worldtree.js seedOf) */
    public static int seedOf(int x, int y, int z) {
        return (int) Math.floor(hash3(x, y, z, 97) * 2147483646) + 1;
    }

    /** 높이를 40 ~ 96 (설정) 으로 */
    public static int heightOf(double height) {
        Obj c = cfg();
        return (int) clamp(Math.round(height), c.num("minHeight"), c.num("maxHeight"));
    }

    /**
     * 뿌리가 닿는 가장 먼 거리 (설계 전에 쓰는 어림 — 어떤 씨앗이든 이보다 멀지 않음, worldtree.js rootReachOf).
     * 가장 긴 뿌리 0.345H, 잔뿌리 끝은 가장 멀어야 0.352H → 0.355H + 1
     */
    public static int rootReachOf(double height) {
        return (int) Math.ceil(heightOf(height) * 0.355 + 1);
    }

    private static Map<String, Object> states(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return Collections.unmodifiableMap(m);
    }

    // ------------------------------------------------------------ 지문

    /** 설계의 지문 (FNV-1a — 칸마다 자리 · 놓는 법 · 블록 이름, worldtree.js fingerprintGen · planFingerprint) */
    public static long fingerprint(List<Stage> stages) {
        int h = 0x811c9dc5;
        Map<String, Integer> bh = new HashMap<>();
        for (Stage st : stages) {
            for (Op o : st.ops()) {
                Integer b = bh.get(o.block());
                if (b == null) {
                    int v = 0x811c9dc5;
                    for (int i = 0; i < o.block().length(); i++) v = (v ^ o.block().charAt(i)) * 16777619;
                    b = v;
                    bh.put(o.block(), v);
                }
                h = (h ^ ((o.dx() & 255) | ((o.dy() & 255) << 8) | ((o.dz() & 255) << 16) | (o.mode().ordinal() << 24))) * 16777619;
                h = (h ^ b) * 16777619;
            }
        }
        return h & 0xFFFFFFFFL;
    }

    // ------------------------------------------------------------ 칸 표 (JS Map — 넣은 차례 그대로, 같은 칸은 우선순위가 높을 때만 바꿈)

    private static final class Cell {
        final int x;
        final int y;
        final int z;
        String block;
        Map<String, Object> states;
        String part;
        Mode mode;
        int prio;

        Cell(int x, int y, int z, String block, Map<String, Object> states, String part, Mode mode, int prio) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
            this.states = states;
            this.part = part;
            this.mode = mode;
            this.prio = prio;
        }

        Op op() {
            return new Op(x, y, z, block, states, mode);
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 0x8000) << 32) | ((long) (y + 0x8000) << 16) | (z + 0x8000);
    }

    /** 설계 중의 상태 (worldtree.js treePlanGen 안의 변수들) */
    private static final class Gen {
        final Map<Long, Cell> cells = new LinkedHashMap<>();
        double s;
        int Ht;
        double r0;
        double rTop;
        double flare;
        double flareH;
        double sway;
        double ph1;
        double ph2;
        final double[] rootAngles = new double[8];

        /** 같은 칸이 있으면 우선순위가 더 높을 때만 바꿈 (JS: 새 객체로 바꾸되 Map 의 차례는 그대로) */
        void put(int x, int y, int z, String block, Map<String, Object> states, String part, Mode mode, int prio) {
            long k = key(x, y, z);
            Cell o = cells.get(k);
            if (o != null) {
                if (o.prio >= prio) return;
                o.block = block;
                o.states = states;
                o.part = part;
                o.mode = mode;
                o.prio = prio;
                return;
            }
            cells.put(k, new Cell(x, y, z, block, states, part, mode, prio));
        }

        boolean has(int x, int y, int z) {
            return cells.containsKey(key(x, y, z));
        }

        Cell at(int x, int y, int z) {
            return cells.get(key(x, y, z));
        }

        /** a → b 굵기 r0 → r1 인 원기둥 (둥근 끝) 의 나무 칸 (worldtree.js segment — pick 은 늘 wood(part)) */
        void segment(Pt a, Pt b, double ra, double rb, String part) {
            double rm = Math.max(ra, rb) + 1;
            double dx = b.x() - a.x();
            double dy = b.y() - a.y();
            double dz = b.z() - a.z();
            double L2 = dx * dx + dy * dy + dz * dz;
            int x0 = (int) Math.floor(Math.min(a.x(), b.x()) - rm);
            int x1 = (int) Math.ceil(Math.max(a.x(), b.x()) + rm);
            int y0 = (int) Math.floor(Math.min(a.y(), b.y()) - rm);
            int y1 = (int) Math.ceil(Math.max(a.y(), b.y()) + rm);
            int z0 = (int) Math.floor(Math.min(a.z(), b.z()) - rm);
            int z1 = (int) Math.ceil(Math.max(a.z(), b.z()) + rm);
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        double t = L2 > 0 ? clamp(((x - a.x()) * dx + (y - a.y()) * dy + (z - a.z()) * dz) / L2, 0, 1) : 0;
                        double qx = a.x() + dx * t;
                        double qy = a.y() + dy * t;
                        double qz = a.z() + dz * t;
                        double dist = hypot(x - qx, y - qy, z - qz);
                        double r = ra + (rb - ra) * t;
                        if (dist <= r + 0.3) put(x, y, z, WOOD, null, part, Mode.WOOD, 5);
                    }
                }
            }
        }

        /** 점들을 이은 줄기 · 가지 (굵기는 처음 → 끝 — worldtree.js limb) */
        void limb(List<Pt> pts, double ra, double rb, String part) {
            int n = pts.size() - 1;
            for (int i = 0; i + 1 < pts.size(); i++) segment(pts.get(i), pts.get(i + 1), ra + (rb - ra) * ((double) i / n), ra + (rb - ra) * ((double) (i + 1) / n), part);
        }

        /** 줄기 가운데 (살짝 휨) */
        Pt axis(double y) {
            return new Pt(sway * (sin(y * 0.07 + ph1) - sin(ph1)), y, sway * (sin(y * 0.055 + ph2) - sin(ph2)));
        }

        double rTrunk(double y) {
            double t = clamp(y / Ht, 0, 1);
            double r = rTop + (r0 - rTop) * pow(1 - t, 1.2);
            if (y < flareH) r += 0.9 * s * pow(1 - Math.max(0, y) / flareH, 2);
            return r;
        }

        /** 버팀 뿌리: 뿌리 쪽으로 좁게 (각 뿌리 방향 ±20° 쯤) 뻗음, 위로 갈수록 줄어듦 */
        double buttress(double y, double th) {
            if (y >= flareH || y < 0) return 0;
            double m = 0;
            for (double a : rootAngles) m = Math.max(m, pow(Math.max(0, cos(th - a)), 10));
            return flare * pow(1 - y / flareH, 1.6) * m;
        }
    }

    /** 잎 뭉치 (가운데 · 가로 반지름 · 위 · 아래) */
    private record Blob(Pt c, double rx, double up, double down) {}

    // ------------------------------------------------------------ 설계

    /** 세계수 설계 (worldtree.js treePlan / treePlanGen — 한 번에). height 는 40 ~ 96 으로 맞춤 */
    public static TreePlan plan(double height, int seed) {
        Obj cfg = cfg();
        double fruitRate = cfg.num("fruit");
        double berries = cfg.num("berries");
        int H = heightOf(height);
        Gen g = new Gen();
        double s = H / 64.0;
        g.s = s;
        Rng R = new Rng(seed);

        // ---- 줄기: 위로 가늘어지고 살짝 휨, 밑동은 뿌리 쪽으로 판자처럼 뻗은 버팀 뿌리 (밑동 가운데 = 0, 0)
        int Ht = round(H * 0.7);
        g.Ht = Ht;
        g.r0 = 4.0 * s;
        g.rTop = 1.7 * s;
        g.flare = 3.4 * s;
        g.flareH = 7 * s;
        g.sway = 1.2 * s;
        g.ph1 = R.next() * TAU;
        g.ph2 = R.next() * TAU;
        int nRoots = 8;
        double theta0 = R.next() * TAU;
        for (int i = 0; i < nRoots; i++) g.rootAngles[i] = theta0 + ((double) i / nRoots) * TAU + (R.next() - 0.5) * 0.45;
        double flare = g.flare;
        double flareH = g.flareH;
        for (int y = -3; y < Ht; y++) {
            Pt c = g.axis(y);
            double rm = g.rTrunk(y) + flare + 1;
            int x0 = (int) Math.floor(c.x() - rm);
            int x1 = (int) Math.ceil(c.x() + rm);
            int z0 = (int) Math.floor(c.z() - rm);
            int z1 = (int) Math.ceil(c.z() + rm);
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    double dist = hypot(x - c.x(), z - c.z());
                    double r = g.rTrunk(y) + g.buttress(y, StrictMath.atan2(z - c.z(), x - c.x()));
                    if (dist <= r + 0.35) {
                        // 겉: 이끼 얼룩 (밑동에 많고 위로 갈수록 드묾, 줄기를 타고 오르는 모양)
                        String block = WOOD;
                        if (dist > r - 1.1 && y >= -1 && vnoise(x / 3.2, y / 4.5, z / 3.2, 41) > 0.42 + 0.45 * clamp(y / (Ht * 0.7), 0, 1)) block = MOSS;
                        g.put(x, y, z, block, null, "trunk", Mode.WOOD, 5);
                    }
                }
            }
        }
        double rBase = g.r0 + 0.9 * s;

        // ---- 뿌리: 버팀 뿌리 끝에서 사방으로 구불구불 뻗어 땅에 박힘 (짝수 번째는 아치), 중간에서 잔뿌리
        double rootReach = 0;
        for (int i = 0; i < nRoots; i++) {
            double th = g.rootAngles[i];
            double L = H * 0.3 * (0.85 + R.next() * 0.3);
            double arch = i % 2 == 0 ? (1.0 + R.next() * 1.4) * s : 0.2;
            double rr0 = (1.2 + R.next() * 0.35) * s;
            double ph = R.next() * TAU;
            double d0 = rBase + flare * 0.45;
            java.util.function.DoubleFunction<Pt> pAt = t -> {
                double d = d0 + (L - d0) * t;
                double ang = th + 0.16 * sin(t * 3.2 + ph);
                double y = 2.0 * s * pow(1 - t, 1.3) - 2.0 * t + arch * sin(Math.PI * t) * (1 - t * 0.3);
                return new Pt(cos(ang) * d, y, sin(ang) * d);
            };
            List<Pt> pts = new ArrayList<>();
            for (int k = 0; k <= 10; k++) pts.add(pAt.apply(k / 10.0));
            g.limb(pts, rr0, rr0 * 0.35, "root");
            rootReach = Math.max(rootReach, L + 1);
            // 잔뿌리: 가운데쯤에서 옆으로
            int side = i % 2 == 0 ? 1 : -1;
            Pt from = pAt.apply(0.45);
            double a2 = th + side * (0.45 + R.next() * 0.2);
            double L2 = L * 0.42;
            List<Pt> sub = new ArrayList<>();
            for (int k = 0; k <= 5; k++) {
                double t = k / 5.0;
                sub.add(new Pt(from.x() + cos(a2) * L2 * t, from.y() + (-1.5 - from.y()) * t, from.z() + sin(a2) * L2 * t));
            }
            g.limb(sub, rr0 * 0.6, rr0 * 0.3, "root");
            rootReach = Math.max(rootReach, hypot(sub.get(5).x(), sub.get(5).z()) + 1);
        }

        // ---- 가지: 나선으로 돌며 비스듬히 위로, 끝 · 가운데 · 잔가지 끝에 잎 뭉치 (서로 겹쳐 하나의 너른 잎 지붕)
        List<Blob> blobs = new ArrayList<>();
        List<Pt> branchMids = new ArrayList<>();
        int nb = 7;
        double bth0 = R.next() * TAU;
        for (int k = 0; k < nb; k++) {
            double f = (double) k / (nb - 1);
            double yb = Ht * (0.5 + 0.44 * f);
            double th = bth0 + k * 137.5 * DEG;
            double elev = (20 + 22 * f + (R.next() - 0.5) * 8) * DEG;
            double Lb = H * (0.36 - 0.1 * f) * (0.9 + R.next() * 0.2);
            Pt c = g.axis(yb);
            double dirX = cos(th) * cos(elev);
            double dirY = sin(elev);
            double dirZ = sin(th) * cos(elev);
            Pt start = new Pt(c.x() + dirX * g.rTrunk(yb) * 0.5, yb, c.z() + dirZ * g.rTrunk(yb) * 0.5);
            java.util.function.DoubleFunction<Pt> bAt = t -> new Pt(start.x() + dirX * Lb * t, start.y() + dirY * Lb * t + Lb * 0.1 * t * t, start.z() + dirZ * Lb * t);
            List<Pt> pts = new ArrayList<>();
            for (int i = 0; i <= 6; i++) pts.add(bAt.apply(i / 6.0));
            g.limb(pts, Math.max(1.0, g.rTrunk(yb) * 0.5), 0.55, "branch");
            // 가지 밑 줄기 겉으로 흘러내린 송진
            int sx = round(c.x() + cos(th) * (g.rTrunk(yb) - 0.4));
            int sz = round(c.z() + sin(th) * (g.rTrunk(yb) - 0.4));
            for (int dy = 1, n = 0; dy <= 8 && n < 2 + (k % 3); dy++) {
                Cell cell = g.at(sx, round(yb) - dy, sz);
                if (cell == null || !"trunk".equals(cell.part)) continue;
                cell.block = RESIN;
                n++;
            }
            blobs.add(blob(up(bAt.apply(1), 1.4, s), 7.4, 4.4, 2.6, s));
            blobs.add(blob(up(bAt.apply(0.78), 1.8, s), 6.0, 3.8, 2.2, s));
            blobs.add(blob(up(bAt.apply(0.52), 2.0, s), 4.8, 3.2, 1.8, s));
            branchMids.add(bAt.apply(0.45));
            // 잔가지 둘 (JS 는 배열을 먼저 만듦 — 난수 둘을 먼저 뽑음)
            double[][] twigs = {{0.55, 38 + R.next() * 12}, {0.85, -(38 + R.next() * 12)}};
            for (double[] tw : twigs) {
                Pt o = bAt.apply(tw[0]);
                double a = th + tw[1] * DEG;
                double e2 = elev + 12 * DEG;
                double L2 = Lb * 0.4;
                Pt tip = new Pt(o.x() + cos(a) * cos(e2) * L2, o.y() + sin(e2) * L2, o.z() + sin(a) * cos(e2) * L2);
                g.segment(o, tip, 0.7, 0.5, "branch");
                blobs.add(blob(new Pt(tip.x(), tip.y() + 1 * s, tip.z()), 5.2, 3.4, 2.0, s));
            }
        }
        // 꼭대기 세 갈래 + 가운데 큰 잎 뭉치
        Pt topC = g.axis(Ht - 2);
        for (int j = 0; j < 3; j++) {
            double a = bth0 + (j * 120 + 60) * DEG;
            double e = 58 * DEG;
            double Lc = H * 0.17;
            Pt tip = new Pt(topC.x() + cos(a) * cos(e) * Lc, topC.y() + sin(e) * Lc, topC.z() + sin(a) * cos(e) * Lc);
            g.segment(topC, tip, 1.4 * s, 0.6, "branch");
            blobs.add(blob(new Pt(tip.x(), tip.y() + 1 * s, tip.z()), 7.6, 4.6, 3.0, s));
        }
        Pt crown = g.axis(Ht);
        blobs.add(blob(new Pt(crown.x(), Ht + 4 * s, crown.z()), 8.6, 5.4, 3.6, s));

        // ---- 잎 뭉치: 진달래 잎 (꽃 핀 잎 섞음), 위는 둥글고 밑은 납작 (늘어진 잎 지붕), 가장자리는 들쭉날쭉, 겉에 빛나는 열매
        for (Blob bl : blobs) {
            Pt c = bl.c();
            double rx = bl.rx();
            double up = bl.up();
            double down = bl.down();
            int cx = round(c.x());
            int cy = round(c.y());
            int cz = round(c.z());
            int crx = (int) Math.ceil(rx);
            for (int x = cx - crx - 1; x <= cx + crx + 1; x++) {
                for (int y = cy - (int) Math.ceil(down) - 1; y <= cy + (int) Math.ceil(up) + 1; y++) {
                    for (int z = cz - crx - 1; z <= cz + crx + 1; z++) {
                        double ry = y >= c.y() ? up : down;
                        double ex = (x - c.x()) / rx;
                        double ey = (y - c.y()) / ry;
                        double ez = (z - c.z()) / rx;
                        double e = ex * ex + ey * ey + ez * ez;
                        double n = hash3(x, y, z, 7);
                        if (e > 1 + (n - 0.5) * 0.35) continue;
                        if (e > 0.72 && n < 0.18) continue;
                        if (e > 0.62 && hash3(x, y, z, 13) < fruitRate) {
                            g.put(x, y, z, FRUIT, null, "leaf", Mode.LEAF, 4);
                            continue;
                        }
                        g.put(x, y, z, hash3(x, y, z, 11) < 0.24 ? BLOOM : LEAF, LEAF_STATES, "leaf", Mode.LEAF, 3);
                    }
                }
            }
        }

        // ---- 뿌리 위 이끼 (위가 빈 뿌리 칸)
        for (Cell c : g.cells.values()) {
            if ("root".equals(c.part) && c.y >= 0 && !g.has(c.x, c.y + 1, c.z) && hash3(c.x, c.y, c.z, 17) > 0.72) c.block = MOSS;
        }

        // ---- 잎 밑 발광 열매 덩굴 (2 ~ 5칸), 가지 밑 포자 꽃
        List<Cell> leaves = new ArrayList<>();
        for (Cell c : g.cells.values()) if ("leaf".equals(c.part) && !FRUIT.equals(c.block)) leaves.add(c);
        for (Cell c : leaves) {
            if (c.y < 10 * s || g.has(c.x, c.y - 1, c.z) || hash3(c.x, c.y, c.z, 19) >= berries) continue;
            int len = 2 + (int) Math.floor(hash3(c.x, c.y, c.z, 23) * 4);
            // 밑이 트인 곳에만 (덩굴 끝 밑으로 한 칸 더 빔: 잎 사이에 끼어 보이지 않는 덩굴은 만들지 않음)
            boolean free = true;
            for (int j = 1; j <= len + 1 && free; j++) if (g.has(c.x, c.y - j, c.z)) free = false;
            if (!free) continue;
            // 덩굴을 매다는 칸: 이끼 블록 (잎 밑에는 덩굴이 붙지 못할 수 있음 — 단단한 블록이 받침)
            c.block = MOSS;
            c.states = null;
            for (int j = 1; j <= len; j++) g.put(c.x, c.y - j, c.z, j == len ? VINE_TIP : VINE, j == len ? VINE_TIP_STATES : VINE_STATES, "hang", Mode.HANG, 2);
        }
        for (Pt m : branchMids) {
            int x = round(m.x());
            int z = round(m.z());
            Integer low = null;
            for (int y = round(m.y()) + 3; y >= round(m.y()) - 4; y--) {
                Cell c = g.at(x, y, z);
                if (c != null && "branch".equals(c.part)) low = y;
            }
            if (low != null && !g.has(x, low - 1, z)) g.put(x, low - 1, z, BLOSSOM, null, "hang", Mode.HANG, 2);
        }

        // ---- 밑동 둘레: 이끼 깔개 고리, 뿌리 사이 반딧불이 덤불 · 꽃 핀 진달래 (땅 높이는 놓을 때 찾음, 깔개는 덤불 자리를 비움)
        List<Op> bushes = new ArrayList<>();
        List<Op> ground = new ArrayList<>();
        double[][] tries = {{0, 0}, {1.5, 0}, {-1.5, 0}, {0, 0.15}, {0, -0.15}, {3, 0}, {3, 0.15}, {3, -0.15}};
        for (int i = 0; i < nRoots; i++) {
            // 뿌리 사이 (잔뿌리가 지나가지 않는 칸을 찾아: 거리 · 각도를 조금씩 바꿔 봄)
            double a0 = g.rootAngles[i] + Math.PI / nRoots;
            double d0 = rBase + 4 + hash3(i, 1, 0, 31) * 3;
            for (double[] tr : tries) {
                int x = round(cos(a0 + tr[1]) * (d0 + tr[0]));
                int z = round(sin(a0 + tr[1]) * (d0 + tr[0]));
                boolean free = true;
                for (int y = -1; y <= 3 && free; y++) if (g.has(x, y, z)) free = false;
                if (!free) continue;
                bushes.add(new Op(x, 0, z, i % 2 == 0 ? FIREFLY : AZALEA, null, Mode.BUSH));
                break;
            }
        }
        // 깔개 자리: 평평한 땅이면 놓을 수 있는 곳만 (뿌리가 찾는 칸 (위 2 ~ 땅) 을 다 채운 곳은 뺌 — 뿌리 위 · 아치 밑에는 놓임)
        int rg = (int) Math.ceil(rBase + 10);
        for (int x = -rg; x <= rg; x++) {
            for (int z = -rg; z <= rg; z++) {
                double r = hypot(x, z);
                if (r < rBase + 1.5 || r > rBase + 9 || hash3(x, 0, z, 29) < 0.42) continue;
                boolean taken = false;
                for (Op b : bushes) if (b.dx() == x && b.dz() == z) taken = true;
                if (taken || !fits(g, x, z)) continue;
                ground.add(new Op(x, 0, z, CARPET, null, Mode.CARPET));
            }
        }
        ground.addAll(bushes);

        // ---- 단계: 뿌리 → 줄기 (6칸씩 아래부터) → 가지 → 잎 (셋) → 빛 (열매 · 덩굴 · 포자 꽃) → 이끼
        List<Cell> all = new ArrayList<>(g.cells.values());
        List<Stage> stages = new ArrayList<>();
        List<Cell> roots = new ArrayList<>();
        for (Cell c : all) if ("root".equals(c.part)) roots.add(c);
        roots.sort((a, b) -> {
            int cmp = Double.compare(hypot(a.x, a.z), hypot(b.x, b.z));
            return cmp != 0 ? cmp : Integer.compare(a.y, b.y);
        });
        stages.add(new Stage("뿌리", "root", "block.mangrove_roots.place", 0.8, 0, ops(roots)));
        List<Cell> trunk = new ArrayList<>();
        for (Cell c : all) if ("trunk".equals(c.part)) trunk.add(c);
        trunk.sort(Comparator.comparingInt(c -> c.y));
        int slice = Math.max(4, round(6 * s));
        int ti = 0;
        for (int y0 = -4; y0 < Ht; y0 += slice) {
            List<Cell> part = new ArrayList<>();
            while (ti < trunk.size() && trunk.get(ti).y < y0 + slice) part.add(trunk.get(ti++));
            if (!part.isEmpty()) stages.add(new Stage("줄기", "trunk", "dig.wood", 0.6 + 0.5 * clamp((double) (y0 + slice) / Ht, 0, 1), y0 + slice, ops(part)));
        }
        List<Cell> branch = new ArrayList<>();
        for (Cell c : all) if ("branch".equals(c.part)) branch.add(c);
        branch.sort(Comparator.comparingInt(c -> c.y));
        stages.add(new Stage("가지", "branch", "place.wood", 0.9, round(Ht * 0.75), ops(branch)));
        List<Cell> leafAll = new ArrayList<>();
        for (Cell c : all) if ("leaf".equals(c.part) && !FRUIT.equals(c.block)) leafAll.add(c);
        leafAll.sort(Comparator.comparingInt(c -> c.y));
        for (int i = 0; i < 3; i++) {
            List<Cell> part = leafAll.subList((int) Math.floor((leafAll.size() * (double) i) / 3), (int) Math.floor((leafAll.size() * (double) (i + 1)) / 3));
            if (!part.isEmpty()) stages.add(new Stage("잎", "leaf", "place.azalea_leaves", 0.9 + i * 0.15, part.get(part.size() / 2).y, ops(part)));
        }
        List<Cell> glow = new ArrayList<>();
        for (Cell c : all) if (FRUIT.equals(c.block)) glow.add(c);
        List<Cell> hang = new ArrayList<>();
        for (Cell c : all) if ("hang".equals(c.part)) hang.add(c);
        hang.sort((a, b) -> Integer.compare(b.y, a.y));
        glow.addAll(hang);
        stages.add(new Stage("빛", "glow", "chime.amethyst_block", 1.2, round(Ht * 0.9), ops(glow)));
        stages.add(new Stage("이끼", "ground", "place.moss", 1, 0, List.copyOf(ground)));

        double reach = 0;
        int top = 0;
        int bottom = 0;
        int woodCount = 0;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Cell c : all) {
            reach = Math.max(reach, hypot(c.x, c.z));
            top = Math.max(top, c.y);
            bottom = Math.min(bottom, c.y);
            counts.merge(c.block, 1, Integer::sum);
            if (c.mode == Mode.WOOD) woodCount++;
        }
        for (Op o : ground) counts.merge(o.block(), 1, Integer::sum);
        double canopy = Double.NEGATIVE_INFINITY;
        for (Blob b : blobs) canopy = Math.max(canopy, hypot(b.c().x(), b.c().z()) + b.rx());
        List<Stage> frozen = List.copyOf(stages);
        return new TreePlan(H, seed, frozen, (int) Math.ceil(Math.max(reach, rg)), (int) Math.ceil(rootReach), (int) Math.ceil(canopy), rBase, top, bottom,
                all.size() + ground.size(), woodCount, Collections.unmodifiableMap(counts), List.copyOf(branchMids), fingerprint(frozen));
    }

    /** {...bAt(t), y: bAt(t).y + h * s} */
    private static Pt up(Pt p, double h, double s) {
        return new Pt(p.x(), p.y() + h * s, p.z());
    }

    private static Blob blob(Pt c, double rx, double up, double down, double s) {
        return new Blob(c, rx * s, up * s, down * s);
    }

    /** 깔개가 평평한 땅에서 놓일 수 있는 자리인지 (위 2 · 1 · 0 칸 중 빈 칸이고 밑이 나무 칸이거나 땅) */
    private static boolean fits(Gen g, int x, int z) {
        for (int y : new int[] {2, 1, 0}) if (!g.has(x, y, z) && (y == 0 || g.has(x, y - 1, z))) return true;
        return false;
    }

    private static List<Op> ops(List<Cell> cells) {
        List<Op> out = new ArrayList<>(cells.size());
        for (Cell c : cells) out.add(c.op());
        return Collections.unmodifiableList(out);
    }

    /** 단계마다 앞 단계들의 칸 수 (칸 번호 = starts[단계] + 단계 안 번호 — worldtree.js stageStarts) */
    public int[] stageStarts() {
        int[] out = new int[stages.size()];
        int n = 0;
        for (int i = 0; i < stages.size(); i++) {
            out[i] = n;
            n += stages.get(i).ops().size();
        }
        return out;
    }

    /** 모든 칸 (칸 번호 차례) */
    public List<Op> allOps() {
        List<Op> out = new ArrayList<>(total);
        for (Stage st : stages) out.addAll(st.ops());
        return out;
    }
}
