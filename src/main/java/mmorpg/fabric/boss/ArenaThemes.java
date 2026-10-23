package mmorpg.fabric.boss;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.DoubleSupplier;
import mmorpg.core.Obj;
import mmorpg.core.Rules;

/**
 * 보스 필드 신전 10종의 설계 (arena.js 136~1772 줄: harvest · desert · sunken · deep · isle · roots · stones · glacier · muspel, 테마 도우미, planOps — 0.33.0 자바 판에만 void).
 * 마인크래프트 클래스 없이 씀 — 블록은 애드온의 베드락 이름 · 상태 그대로 (자바 블록으로는 {@link ArenaBlocks} 가 바꿈), 그래서 애드온 JS 와 칸마다 대조할 수 있음.
 * <p>
 * 같은 제단 자리 (x, y, z) 면 애드온과 같은 칸에 같은 블록: 씨앗 난수 (seeded) · 칸 난수 (hash) · 무늬 (patch) · 뽑는 차례를 그대로.
 * JS 의 Math.sin · cos · atan2 · hypot · pow 는 StrictMath (fdlibm — 모바일 베드락의 libm 과 같은 계열, 세 값 hypot 은 QuickJS 처럼 hypot(hypot(x, y), z)),
 * Math.round 는 JS 와 같은 반올림 (반은 +∞ 쪽).
 * 좌표: dx · dz = 제단 칸 기준 칸, dy = 제단 높이 기준 (바닥 -1 층, 플레이어는 0 층에 섬). r = 칸 가운데까지 거리, deg = 각도 (+x 에서 +z 쪽으로).
 */
public final class ArenaThemes {
    private ArenaThemes() {}

    private static final Obj BOSSES = Rules.get().obj("BOSSES");

    // ------------------------------------------------------------ 자료형 (arena.js 의 Paint · Layer · Put · Stage · Plan)

    /** 칠하기 결과: 블록 이름 + 상태 (상태가 없으면 null). 칠하지 않는 칸은 Paint 대신 null */
    public record Paint(String block, Map<String, Object> states) {}

    /** 층 칠하기: (dx, dz, r, deg) → Paint | null (그대로) */
    @FunctionalInterface
    public interface Painter {
        Paint paint(int dx, int dz, double r, double deg);
    }

    /** 한 층 (제단 높이 기준 dy) */
    public record Layer(int dy, Painter paint) {}

    /** 놓을 블록 하나 (제단 기준 dx · dy · dz) */
    public record Put(int dx, int dy, int dz, String block, Map<String, Object> states) {}

    /** 짓기 단계 (이름 · 층 · 놓을 블록 · 끝날 때 소리) */
    public record Stage(String name, List<Layer> layers, List<Put> puts, String sound) {}

    /** 제단 기준 칸 */
    public record Spot(int dx, int dz) {}

    /** 해일 그림자 기둥 (가로 반폭) */
    public record PillarAt(int dx, int dz, double half) {}

    /** 선돌 (sun = 동쪽 해 선돌) */
    public record StoneAt(int dx, int dz, boolean sun) {}

    /** 얼음 첨탑 · 불꽃 탑 (높이) */
    public record SpireAt(int dx, int dz, int h) {}

    /** 사슬 기둥 · 얼음 결정 (가운데 3×3 반폭, 높이, 블록, 결정 둘레 서리 바닥 — 사슬 기둥은 빈 목록) */
    public record ColumnAt(int dx, int dz, double half, int h, List<Put> cells, List<Put> frost) {}

    /** 설계의 특징 자리 (제단 기준 — 월드 좌표는 Arena.arenaFeatures). tile 은 거대 웜 체크무늬 칸 크기 (없으면 null) */
    public record Feat(List<Spot> totems, List<Spot> decoys, List<Spot> gates, List<Spot> posts, List<PillarAt> pillars, List<Spot> holes, Integer tile,
            List<StoneAt> stones, List<Spot> fires, List<SpireAt> spires, List<ColumnAt> chains, List<ColumnAt> crystals, List<SpireAt> towers) {}

    /** 필드 설계: 단계 + 특징 자리 */
    public record Plan(List<Stage> stages, Feat features) {}

    /** 사슬 기둥 · 얼음 결정 한 칸 (가운데 칸 기준 ox · dy · oz) */
    public record PieceCell(int ox, int dy, int oz, String block, Map<String, Object> states) {}

    /** 놓을 작업 하나: fill (x ~ x2, y ~ y2, z ~ z2 네모) 또는 set (x, y, z 한 칸) — 월드 칸 좌표 */
    public record Op(boolean fill, int x, int y, int z, int x2, int y2, int z2, String block, Map<String, Object> states) {
        /** 바꾸는 블록 수 (fill 은 부피) */
        public int volume() {
            return fill ? (Math.abs(x2 - x) + 1) * (Math.abs(y2 - y) + 1) * (Math.abs(z2 - z) + 1) : 1;
        }
    }

    /** 한 단계의 작업 목록 */
    public record StageOps(String stage, String sound, List<Op> ops) {}

    // ------------------------------------------------------------ 공통 도우미

    /** 설계 하나를 지을 때의 제단 (보스 키 · 자리 · 필드 반지름 R · 높이 H) */
    private record Ctx(String key, int x, int y, int z, int R, int H) {}

    private static final Map<String, Paint> PLAIN = new HashMap<>();

    /** 상태 없는 블록 칠하기 (같은 이름은 같은 것을 씀) */
    private static Paint p(String block) {
        synchronized (PLAIN) {
            return PLAIN.computeIfAbsent(block, b -> new Paint(b, null));
        }
    }

    /** 상태 (JS 객체처럼 넣은 차례 그대로) */
    private static Map<String, Object> st(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return Collections.unmodifiableMap(m);
    }

    private static Paint p(String block, Map<String, Object> states) {
        return new Paint(block, states);
    }

    private static final Map<String, Object> AXIS_Y = st("pillar_axis", "y");

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

    /** JS Math.sign(v) || 1 */
    private static int signOr1(int v) {
        return v > 0 ? 1 : v < 0 ? -1 : 1;
    }

    /** 제단 위치로 정해지는 난수 (arena.js seeded — 같은 자리면 같은 장식) */
    static DoubleSupplier seeded(int x, int y, int z) {
        // JS: ((Math.abs(x * 73856093) ^ …) >>> 0) % 2147483647 || 7 — 곱은 2^53 안이라 정확, ^ 는 낮은 32비트 (ToInt32)
        int hx = (int) Math.abs((long) x * 73856093L);
        int hy = (int) Math.abs((long) y * 19349663L);
        int hz = (int) Math.abs((long) z * 83492791L);
        long s0 = ((hx ^ hy ^ hz) & 0xFFFFFFFFL) % 2147483647L;
        long[] s = {s0 == 0 ? 7 : s0};
        return () -> {
            s[0] = (s[0] * 16807L) % 2147483647L;
            return s[0] / 2147483647.0;
        };
    }

    private static DoubleSupplier seeded(Ctx a) {
        return seeded(a.x, a.y, a.z);
    }

    /** 칸마다 정해진 난수 0~1 (같은 제단 자리면 같은 값 — 테마마다 같은 식) */
    private static double hash(Ctx a, double dx, double dz, double k) {
        double v = StrictMath.sin(dx * 12.9898 + dz * 78.233 + k * 37.719 + (a.x % 1000) * 0.0131 + (a.z % 1000) * 0.0173) * 43758.5453;
        return v - Math.floor(v);
    }

    private static double hash(Ctx a, double dx, double dz) {
        return hash(a, dx, dz, 0);
    }

    /** 가운데를 보게 하는 호박 방향 (dx, dz = 제단 기준) */
    static String facingCenter(int dx, int dz) {
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? "west" : "east";
        return dz > 0 ? "north" : "south";
    }

    /** 각도 (도, +x 에서 +z 쪽으로 0~360) */
    static double DEG(double dx, double dz) {
        return ((StrictMath.atan2(dz, dx) * 180) / Math.PI + 360) % 360;
    }

    /** 반지름 r, 각도 deg (도, +x 에서 +z 쪽으로) 의 칸 (제단 기준 — arena.js polar, 반올림은 JS 와 같게) */
    public static Spot polar(double r, double deg) {
        return new Spot(round(r * StrictMath.cos((deg * Math.PI) / 180)), round(r * StrictMath.sin((deg * Math.PI) / 180)));
    }

    /** 두 각도 (라디안) 사이 */
    private static double angDist(double a1, double a2) {
        return Math.abs(StrictMath.atan2(StrictMath.sin(a1 - a2), StrictMath.cos(a1 - a2)));
    }

    private static long cellKey(int dx, int dz) {
        return ((long) dx << 32) ^ (dz & 0xFFFFFFFFL);
    }

    private static long cellKey(int dx, int dy, int dz) {
        return (((long) (dx + 100000)) << 40) | (((long) (dy + 1000)) << 20) | (dz + 100000);
    }

    @FunctionalInterface
    private interface Gap {
        boolean test(int dx, int dz);
    }

    /** 울타리 고리 (cell = 고리 칸인지, cells = 고리 칸 목록 — 만든 차례) */
    private record Ring(Gap cell, List<Spot> cells) {}

    /** 울타리 고리 칸 (반지름 R+0.5 원 바로 바깥, 대각선으로도 안쪽과 닿는 칸 = 끊기지 않는 고리). gap(dx, dz) 인 칸 (성문) 은 뺌 */
    private static Ring ringOf(int R, Gap gap) {
        Gap inner = (dx, dz) -> hypot(dx, dz) <= R + 0.5;
        Gap cell = (dx, dz) -> {
            if (inner.test(dx, dz) || gap.test(dx, dz)) return false;
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    if (inner.test(dx + i, dz + j)) return true;
                }
            }
            return false;
        };
        List<Spot> cells = new ArrayList<>();
        int n = R + 2;
        for (int dx = -n; dx <= n; dx++) {
            for (int dz = -n; dz <= n; dz++) {
                if (cell.test(dx, dz)) cells.add(new Spot(dx, dz));
            }
        }
        return new Ring(cell, cells);
    }

    /** 그 자리에 가장 가까운 고리 칸 (같으면 먼저 만든 칸) */
    private static Spot nearestCell(List<Spot> cells, Spot want) {
        Spot best = cells.get(0);
        for (Spot c : cells) {
            if (hypot(c.dx - want.dx, c.dz - want.dz) < hypot(best.dx - want.dx, best.dz - want.dz)) best = c;
        }
        return best;
    }

    private static final int[][] DIRS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    private static final double[] GATE_DEG = {0, 90, 180, 270};
    private static final double[] DIAG_DEG = {45, 135, 225, 315};

    private static List<Spot> polars(double r, double[] degs) {
        List<Spot> out = new ArrayList<>(degs.length);
        for (double d : degs) out.add(polar(r, d));
        return out;
    }

    /** 성문 양쪽 기둥 (gate = 성문 번호 0~3: +x · +z · -x · -z) */
    private record Flank(int dx, int dz, int gate) {}

    /** 고리 칸 중 성문 길 바로 옆 (길에서 off 칸) */
    private static List<Flank> flanks(int R, List<Spot> ring, int off) {
        List<Flank> out = new ArrayList<>();
        for (int gi = 0; gi < 4; gi++) {
            int gx = DIRS[gi][0];
            int gz = DIRS[gi][1];
            for (int s : new int[] {-off, off}) {
                Spot c = nearestCell(ring, new Spot(gx * (R + 1) + (gz != 0 ? s : 0), gz * (R + 1) + (gx != 0 ? s : 0)));
                out.add(new Flank(c.dx, c.dz, gi));
            }
        }
        return out;
    }

    /** 한 성문의 들보 자리: lo ~ hi (성문 쪽 축), at (다른 축) */
    private record Beam(int lo, int hi, int at, boolean alongZ) {
        int dx(int k) {
            return alongZ ? at : k;
        }

        int dz(int k) {
            return alongZ ? k : at;
        }
    }

    private static Beam beam(List<Flank> flanks, int gi) {
        int gx = DIRS[gi][0];
        List<Flank> two = new ArrayList<>();
        for (Flank f : flanks) if (f.gate == gi) two.add(f);
        boolean alongZ = gx != 0;
        int lo = Integer.MAX_VALUE;
        int hi = Integer.MIN_VALUE;
        for (Flank f : two) {
            int v = alongZ ? f.dz : f.dx;
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        int at = alongZ ? two.get(0).dx : two.get(0).dz;
        return new Beam(lo, hi, at, alongZ);
    }

    private static List<Spot> spotsOf(List<Flank> flanks) {
        List<Spot> out = new ArrayList<>();
        for (Flank f : flanks) out.add(new Spot(f.dx, f.dz));
        return out;
    }

    /** posts 둘레 2.5칸 안인지 */
    private static boolean nearPost(List<Spot> posts, int dx, int dz) {
        for (Spot p : posts) if (hypot(dx - p.dx, dz - p.dz) < 2.5) return true;
        return false;
    }

    /** 땅 고르기: 0 ~ H 층을 EDGE 안 공기로 */
    private static List<Layer> clearLayers(int H, int EDGE) {
        List<Layer> out = new ArrayList<>();
        Paint air = p("minecraft:air");
        for (int i = 0; i < H + 1; i++) out.add(new Layer(i, (dx, dz, r, deg) -> r <= EDGE ? air : null));
        return out;
    }

    /** EDGE 안을 한 블록으로 */
    private static Layer fillLayer(int dy, int EDGE, Paint paint) {
        return new Layer(dy, (dx, dz, r, deg) -> r <= EDGE ? paint : null);
    }

    private static Feat feat(List<Spot> totems, List<Spot> decoys, List<Spot> gates, List<Spot> posts) {
        return new Feat(totems, decoys, gates, posts, List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static Put put(int dx, int dy, int dz, String block) {
        return new Put(dx, dy, dz, block, null);
    }

    private static Put put(int dx, int dy, int dz, String block, Map<String, Object> states) {
        return new Put(dx, dy, dz, block, states);
    }

    private static Ctx ctx(String key, int x, int y, int z) {
        Obj ar = BOSSES.obj(key).obj("arena");
        return new Ctx(key, x, y, z, (int) ar.num("radius"), (int) ar.num("height"));
    }

    // ------------------------------------------------------------ 허수아비 킹: 수확이 끝난 밭의 신전

    /**
     * 허수아비 킹: 수확이 끝난 밭의 신전. 가운데 짙은 참나무 단(제단) + 짚단 고리, 성문 쪽 십자 흙길, 갈아엎은 밭 줄무늬(회백토·거친 흙),
     * 대각선 네 곳 허수아비 토템(짚단 2 + 잭오랜턴 머리 + 울타리 팔, 둘레 짚단 원 = 수확의 낫 안전지대), 가장자리 석재 벽돌 고리,
     * 짙은 참나무 울타리(높이 2, 네 방향 3칸 성문) + 기둥 8개(원목 3 + 잭오랜턴), 울타리 안쪽 짚단·호박 장식
     */
    private static Plan harvest(Ctx a) {
        int R = a.R;
        int H = a.H;
        DoubleSupplier rand = seeded(a);
        List<Spot> totems = polars(9, DIAG_DEG);
        List<Spot> decoys = polars(8, GATE_DEG);
        List<Spot> gates = polars(R + 1, GATE_DEG);
        Gap onPath = (dx, dz) -> Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
        // 울타리 칸: 반지름 R+0.5 원 바로 바깥 칸 중 (대각선 포함) 안쪽 칸과 닿는 칸 → 끊기지 않고 이어지는 고리 (성문 네 곳만 빔)
        Ring ring = ringOf(R, onPath);
        // 기둥: 울타리 고리 위 (그 각도에 가장 가까운 칸)
        List<Spot> posts = new ArrayList<>();
        for (double d : new double[] {22.5, 67.5, 112.5, 157.5, 202.5, 247.5, 292.5, 337.5}) posts.add(nearestCell(ring.cells, polar(R + 1, d)));
        int EDGE = R + 2; // 바닥·땅 고르기가 닿는 반지름 (울타리 고리까지)
        List<Put> struct = new ArrayList<>();
        for (Spot q : posts) {
            for (int dy = 0; dy <= 2; dy++) struct.add(put(q.dx, dy, q.dz, "minecraft:dark_oak_log"));
            struct.add(put(q.dx, 3, q.dz, "minecraft:lit_pumpkin", st("minecraft:cardinal_direction", facingCenter(q.dx, q.dz))));
        }
        for (Spot t : totems) {
            struct.add(put(t.dx, 0, t.dz, "minecraft:hay_block"));
            struct.add(put(t.dx, 1, t.dz, "minecraft:hay_block"));
            struct.add(put(t.dx, 2, t.dz, "minecraft:lit_pumpkin", st("minecraft:cardinal_direction", facingCenter(t.dx, t.dz))));
        }
        List<Put> deco = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        for (int n = 0, tries = 0; n < 18 && tries < 400; tries++) {
            double deg = rand.getAsDouble() * 360;
            double r = R - 1.5 - rand.getAsDouble() * 2;
            Spot q = polar(r, deg);
            long k = cellKey(q.dx, q.dz);
            if (taken.contains(k) || onPath.test(q.dx, q.dz) || nearAny(totems, q.dx, q.dz, 3.5)) continue;
            taken.add(k);
            boolean pumpkin = n % 3 == 2;
            deco.add(pumpkin ? put(q.dx, 0, q.dz, "minecraft:pumpkin", st("minecraft:cardinal_direction", facingCenter(q.dx, q.dz))) : put(q.dx, 0, q.dz, "minecraft:hay_block"));
            n++;
        }
        Painter floor = (dx, dz, r, deg) -> {
            if (r > EDGE) return null;
            if (r > R) return p("minecraft:mossy_stone_bricks");
            if (r > R - 1) return p("minecraft:stone_bricks");
            if (r <= 2.5) return p("minecraft:dark_oak_planks");
            if (r <= 3.5) return p("minecraft:hay_block");
            if (nearAny(totems, dx, dz, 2.4)) return p("minecraft:hay_block");
            if (onPath.test(dx, dz)) return p("minecraft:grass_path");
            return ((dz % 4) + 4) % 4 < 2 ? p("minecraft:podzol") : p("minecraft:coarse_dirt");
        };
        Paint fence = p("minecraft:dark_oak_fence");
        List<Stage> stages = List.of(
                new Stage("땅 고르기", clearLayers(H, EDGE), List.of(), "dig.grass"),
                new Stage("기초", List.of(fillLayer(-3, EDGE, p("minecraft:dirt")), fillLayer(-2, EDGE, p("minecraft:coarse_dirt"))), List.of(), "dig.gravel"),
                new Stage("바닥", List.of(new Layer(-1, floor)), List.of(), "dig.wood"),
                new Stage("울타리·기둥", List.of(new Layer(0, (dx, dz, r, deg) -> ring.cell.test(dx, dz) ? fence : null),
                        new Layer(1, (dx, dz, r, deg) -> ring.cell.test(dx, dz) ? fence : null)), struct, "dig.wood"),
                new Stage("장식", List.of(), deco, "block.lantern.place"));
        return new Plan(stages, feat(totems, decoys, gates, posts));
    }

    /** spots 중 하나가 k 칸 안인지 */
    private static boolean nearAny(List<Spot> spots, int dx, int dz, double k) {
        for (Spot t : spots) if (hypot(dx - t.dx, dz - t.dz) <= k) return true;
        return false;
    }

    // ------------------------------------------------------------ 사막 전갈 + 바위 전갈: 모래와 바위의 신전

    /**
     * 사막 전갈 + 바위 전갈: 모래와 바위가 반씩인 사막 신전. 서쪽(-x) = 사막 전갈의 모래 (모래·사암·붉은 모래),
     * 동쪽(+x) = 바위 전갈의 바위 (돌·안산암·자갈·조약돌), 경계에 가까울수록 서로 섞임.
     * 남북으로 붉은 사암 길 (두 땅의 경계선), 동서로 매끄러운 사암·윤나는 안산암 길. 가운데 단: 두 재료 반반 + 붉은 사암 고리.
     * 가장자리 고리 (서쪽 깎은 사암·동쪽 석재 벽돌), 담장 (서쪽 사암 담장·동쪽 조약돌 담장, 높이 1 = 넘을 수 없음),
     * 네 성문: 양쪽 기둥 + 위 들보 + 매단 랜턴, 대각선 네 곳 오벨리스크 (꼭대기 랜턴). 장식은 가장자리에만 (서쪽 마른 덤불·뼈 화석·해골, 동쪽 이끼 낀 바위)
     */
    private static Plan desert(Ctx a) {
        int R = a.R;
        int H = a.H;
        DoubleSupplier rand = seeded(a);
        int EDGE = R + 2;
        List<Spot> gates = polars(R + 1, GATE_DEG);
        Gap onPath = (dx, dz) -> Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
        Ring ring = ringOf(R, onPath);
        // 성문 양쪽 기둥: 고리 칸 중 성문 길 바로 옆 (길에서 2칸)
        List<Flank> flanks = flanks(R, ring.cells, 2);
        List<Spot> obelisks = new ArrayList<>();
        for (double d : DIAG_DEG) obelisks.add(nearestCell(ring.cells, polar(R + 1, d)));
        List<Spot> posts = new ArrayList<>(spotsOf(flanks));
        posts.addAll(obelisks);
        List<Put> struct = new ArrayList<>();
        for (Flank f : flanks) {
            for (int dy = 0; dy <= 3; dy++) {
                struct.add(put(f.dx, dy, f.dz, dy == 0 || dy == 3 ? desertM(f.dx, "minecraft:chiseled_sandstone", "minecraft:chiseled_stone_bricks")
                        : desertM(f.dx, "minecraft:cut_sandstone", "minecraft:stone_bricks")));
            }
        }
        // 들보: 두 기둥 위를 잇는 5칸 + 가운데 매단 랜턴
        for (int gi = 0; gi < 4; gi++) {
            Beam b = beam(flanks, gi);
            for (int k = b.lo; k <= b.hi; k++) {
                int dx = b.dx(k);
                int dz = b.dz(k);
                struct.add(put(dx, 4, dz, gi % 2 == 1 ? "minecraft:cut_red_sandstone" : desertM(dx, "minecraft:cut_sandstone", "minecraft:stone_bricks")));
            }
            double mid = (b.lo + b.hi) / 2.0;
            struct.add(put(b.alongZ ? b.at : round(mid), 3, b.alongZ ? round(mid) : b.at, "minecraft:lantern", st("hanging", true)));
        }
        for (Spot o : obelisks) {
            boolean s = o.dx < 0;
            String[] body = s ? new String[] {"minecraft:chiseled_sandstone", "minecraft:cut_sandstone", "minecraft:cut_sandstone", "minecraft:smooth_sandstone", "minecraft:chiseled_sandstone"}
                    : new String[] {"minecraft:chiseled_stone_bricks", "minecraft:stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:stone_bricks", "minecraft:chiseled_stone_bricks"};
            for (int dy = 0; dy < body.length; dy++) struct.add(put(o.dx, dy, o.dz, body[dy]));
            struct.add(put(o.dx, body.length, o.dz, "minecraft:lantern", st("hanging", false)));
        }
        // 장식: 가장자리 띠에만 (싸우는 가운데는 비워 둠), 길·성문·기둥 둘레 제외
        List<Put> deco = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        for (int n = 0, tries = 0; n < 26 && tries < 600; tries++) {
            double deg = rand.getAsDouble() * 360;
            double r = R - 1.5 - rand.getAsDouble() * 2.5;
            Spot q = polar(r, deg);
            long k = cellKey(q.dx, q.dz);
            if (taken.contains(k) || onPath.test(q.dx, q.dz) || Math.abs(q.dx) <= 3 || nearPost(posts, q.dx, q.dz) || hypot(q.dx, q.dz) > R - 1) continue;
            taken.add(k);
            double pick = rand.getAsDouble();
            if (q.dx < 0) {
                if (pick < 0.55) deco.add(put(q.dx, 0, q.dz, "minecraft:deadbush"));
                else if (pick < 0.8) {
                    // 뼈 화석: 뼈 블록 누운 것 2칸 (가운데 쪽으로 누움)
                    String axis = Math.abs(q.dx) > Math.abs(q.dz) ? "x" : "z";
                    int sx = axis.equals("x") ? signOr1(q.dx) : 0;
                    int sz = axis.equals("x") ? 0 : signOr1(q.dz);
                    long k2 = cellKey(q.dx + sx, q.dz + sz);
                    if (taken.contains(k2) || onPath.test(q.dx + sx, q.dz + sz) || hypot(q.dx + sx, q.dz + sz) > R - 1) continue;
                    taken.add(k2);
                    deco.add(put(q.dx, 0, q.dz, "minecraft:bone_block", st("pillar_axis", axis)));
                    deco.add(put(q.dx + sx, 0, q.dz + sz, "minecraft:bone_block", st("pillar_axis", axis)));
                } else deco.add(put(q.dx, 0, q.dz, "minecraft:skeleton_skull", st("facing_direction", 1)));
            } else if (pick < 0.6) {
                // 이끼 낀 바위: 한 칸 또는 위로 한 칸 더
                deco.add(put(q.dx, 0, q.dz, "minecraft:mossy_cobblestone"));
                if (rand.getAsDouble() < 0.4) deco.add(put(q.dx, 1, q.dz, "minecraft:cobblestone"));
            } else if (pick < 0.85) deco.add(put(q.dx, 0, q.dz, "minecraft:andesite"));
            else deco.add(put(q.dx, 0, q.dz, "minecraft:deadbush")); // 바위 틈 마른 덤불 (바닥이 모래가 아니면 놓이지 않으므로 아래에서 모래로)
            n++;
        }
        // 마른 덤불 아래는 모래 (바위 쪽이어도): 바닥 칠하기에서 이 칸들을 모래로
        Set<Long> bushSoil = new HashSet<>();
        for (Put d : deco) if (d.block.equals("minecraft:deadbush")) bushSoil.add(cellKey(d.dx, d.dz));
        Painter floor = (dx, dz, r, deg) -> {
            if (r > EDGE) return null;
            boolean s = dx < 0;
            if (r > R) return p(s ? "minecraft:cut_sandstone" : "minecraft:stone_bricks");
            if (r > R - 1) return p(s ? "minecraft:smooth_sandstone" : "minecraft:polished_andesite");
            if (r <= 3.5) {
                if (r > 2.5) return p("minecraft:cut_red_sandstone");
                if (dx == 0) return p("minecraft:chiseled_red_sandstone");
                return p(s ? "minecraft:cut_sandstone" : "minecraft:polished_andesite");
            }
            if (Math.abs(dx) <= 1) return p("minecraft:cut_red_sandstone"); // 두 땅의 경계선
            if (Math.abs(dz) <= 1) return p(s ? "minecraft:smooth_sandstone" : "minecraft:polished_andesite");
            if (bushSoil.contains(cellKey(dx, dz))) return p("minecraft:sand");
            double h = hash(a, dx, dz);
            // 경계 가까이 (2~5칸): 서로의 재료가 섞임
            double mix = Math.max(0, (6 - Math.abs(dx)) / 12.0);
            if (hash(a, dx, dz, 3) < mix) return p(s ? (h < 0.5 ? "minecraft:cobblestone" : "minecraft:gravel") : "minecraft:sand");
            if (s) {
                if (desertPatch(dx, dz, 1.3) > 1.5) return p("minecraft:sandstone");
                if (h < 0.02) return p("minecraft:red_sand");
                return p("minecraft:sand");
            }
            if (desertPatch(dx, dz, 2.7) > 1.2) return p("minecraft:andesite");
            if (desertPatch(dx, dz, 4.1) < -1.6) return p("minecraft:gravel");
            if (h < 0.08) return p("minecraft:cobblestone");
            return p("minecraft:stone");
        };
        List<Layer> base = new ArrayList<>();
        for (int dy : new int[] {-3, -2}) base.add(new Layer(dy, (dx, dz, r, deg) -> r <= EDGE ? p(dx < 0 ? "minecraft:sandstone" : "minecraft:stone") : null));
        List<Stage> stages = List.of(
                new Stage("땅 고르기", clearLayers(H, EDGE), List.of(), "dig.sand"),
                new Stage("기초", base, List.of(), "dig.stone"),
                new Stage("바닥", List.of(new Layer(-1, floor)), List.of(), "dig.sand"),
                new Stage("담장·기둥", List.of(new Layer(0, (dx, dz, r, deg) -> ring.cell.test(dx, dz) ? p(dx < 0 ? "minecraft:sandstone_wall" : "minecraft:cobblestone_wall") : null)),
                        struct, "dig.stone"),
                new Stage("장식", List.of(), deco, "block.lantern.place"));
        return new Plan(stages, feat(List.of(), List.of(), gates, posts));
    }

    /** 재료: 서쪽 모래 / 동쪽 바위 (남북 성문은 경계라 붉은 사암) */
    private static String desertM(int dx, String s, String r) {
        return dx == 0 ? "minecraft:cut_red_sandstone" : dx < 0 ? s : r;
    }

    /** 부드럽게 이어지는 무늬 (-3~3): 사암·안산암·자갈 조각 */
    private static double desertPatch(double dx, double dz, double k) {
        return StrictMath.sin(dx * 0.62 + k) + StrictMath.sin(dz * 0.55 + k * 2.1) + StrictMath.sin((dx - dz) * 0.37 + k * 0.7);
    }

    // ------------------------------------------------------------ 크라켄: 가라앉은 신전

    private static final String[] CORALS = {"tube", "brain", "bubble", "fire", "horn"};

    /** 체비쇼프 거리 */
    private static int cheb(int dx, int dz, Spot c) {
        return Math.max(Math.abs(dx - c.dx), Math.abs(dz - c.dz));
    }

    /**
     * 크라켄 (v2.0.0): 바다에서 떠오른 가라앉은 신전 (프리즈머린). 가운데 짙은 프리즈머린 단 + 바다 랜턴 고리 (크라켄이 앉는 곳),
     * 네 방향 길(프리즈머린 벽돌) 위 12칸에 촉수 물구덩이 3×3 (물 1칸 깊이, 둘레 산호 블록 — 물에 닿아 살아 있음, 모서리 바다 랜턴),
     * 대각선 10칸에 기둥 4개 (3×3, 높이 6, 꼭대기 바다 랜턴 = 해일을 피하는 그림자), 짙은 프리즈머린 고리 무늬(8·16칸), 모래·자갈 퇴적,
     * 가장자리 고리 + 프리즈머린 담장 (네 성문: 양쪽 기둥 + 들보 + 매단 영혼 랜턴), 대각선 담장 기둥 (꼭대기 바다 랜턴),
     * 장식은 가장자리에만 (죽은 산호 블록·부채, 젖은 스펀지, 바다 피클, 무너진 프리즈머린)
     */
    private static Plan sunken(Ctx a) {
        int R = a.R;
        int H = a.H;
        DoubleSupplier rand = seeded(a);
        int EDGE = R + 2;
        List<Spot> gates = polars(R + 1, GATE_DEG);
        Gap onPath = (dx, dz) -> Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
        // 촉수 물구덩이 (길 위, 가운데에서 12칸) · 기둥 (대각선 10칸, 3×3)
        List<Spot> holes = polars(12, GATE_DEG);
        List<Spot> pillarSpots = polars(10, DIAG_DEG);
        List<PillarAt> pillars = new ArrayList<>();
        for (Spot q : pillarSpots) pillars.add(new PillarAt(q.dx, q.dz, 1.5));
        Ring ring = ringOf(R, onPath);
        List<Flank> flanks = flanks(R, ring.cells, 2);
        List<Spot> towers = new ArrayList<>();
        for (double d : DIAG_DEG) towers.add(nearestCell(ring.cells, polar(R + 1, d)));
        List<Spot> posts = new ArrayList<>(spotsOf(flanks));
        posts.addAll(towers);
        List<Put> struct = new ArrayList<>();
        for (Flank f : flanks) {
            for (int dy = 0; dy <= 3; dy++) struct.add(put(f.dx, dy, f.dz, dy == 0 || dy == 3 ? "minecraft:dark_prismarine" : "minecraft:prismarine_bricks"));
        }
        // 들보 (두 기둥 위) + 가운데 매단 영혼 랜턴 (푸른 빛)
        for (int gi = 0; gi < 4; gi++) {
            Beam b = beam(flanks, gi);
            for (int k = b.lo; k <= b.hi; k++) {
                struct.add(put(b.dx(k), 4, b.dz(k), k == b.lo || k == b.hi ? "minecraft:dark_prismarine" : "minecraft:prismarine_bricks"));
            }
            int mid = round((b.lo + b.hi) / 2.0);
            struct.add(put(b.dx(mid), 3, b.dz(mid), "minecraft:soul_lantern", st("hanging", true)));
        }
        for (Spot t : towers) {
            String[] body = {"minecraft:dark_prismarine", "minecraft:prismarine_bricks", "minecraft:prismarine_bricks", "minecraft:dark_prismarine"};
            for (int dy = 0; dy < body.length; dy++) struct.add(put(t.dx, dy, t.dz, body[dy]));
            struct.add(put(t.dx, 4, t.dz, "minecraft:sea_lantern"));
        }
        // 기둥: 밑동·머리 짙은 프리즈머린, 몸통 프리즈머린 벽돌 (모서리는 프리즈머린), 꼭대기 가운데 바다 랜턴
        for (Spot q : pillarSpots) {
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    boolean corner = i != 0 && j != 0;
                    for (int dy = 0; dy <= 5; dy++) {
                        String block = dy == 0 || dy == 5 ? "minecraft:dark_prismarine" : corner ? "minecraft:prismarine" : "minecraft:prismarine_bricks";
                        struct.add(put(q.dx + i, dy, q.dz + j, block));
                    }
                }
            }
            struct.add(put(q.dx, 6, q.dz, "minecraft:sea_lantern"));
        }
        // 장식: 가장자리 띠에만 (싸우는 가운데·길·기둥·물구덩이 둘레 제외)
        List<Put> deco = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        for (int n = 0, tries = 0; n < 30 && tries < 700; tries++) {
            double deg = rand.getAsDouble() * 360;
            double r = R - 1.5 - rand.getAsDouble() * 2.5;
            Spot q = polar(r, deg);
            long k = cellKey(q.dx, q.dz);
            if (taken.contains(k) || onPath.test(q.dx, q.dz) || Math.abs(q.dx) <= 3 || Math.abs(q.dz) <= 3 || nearPost(posts, q.dx, q.dz)
                    || nearCheb(pillarSpots, q.dx, q.dz, 3) || hypot(q.dx, q.dz) > R - 1) continue;
            taken.add(k);
            double pick = rand.getAsDouble();
            String coral = CORALS[(int) Math.floor(rand.getAsDouble() * CORALS.length)];
            if (pick < 0.3) {
                deco.add(put(q.dx, 0, q.dz, "minecraft:dead_" + coral + "_coral_fan", st("coral_fan_direction", rand.getAsDouble() < 0.5 ? 0 : 1)));
            } else if (pick < 0.55) {
                // 죽은 산호 덩어리: 한 칸 또는 위로 한 칸 더
                deco.add(put(q.dx, 0, q.dz, "minecraft:dead_" + coral + "_coral_block"));
                if (rand.getAsDouble() < 0.35) deco.add(put(q.dx, 1, q.dz, "minecraft:dead_" + CORALS[(int) Math.floor(rand.getAsDouble() * CORALS.length)] + "_coral_block"));
            } else if (pick < 0.7) deco.add(put(q.dx, 0, q.dz, "minecraft:wet_sponge"));
            else if (pick < 0.85) {
                deco.add(put(q.dx, 0, q.dz, "minecraft:sea_pickle", st("cluster_count", (int) Math.floor(rand.getAsDouble() * 4), "dead_bit", true)));
            } else deco.add(put(q.dx, 0, q.dz, rand.getAsDouble() < 0.5 ? "minecraft:prismarine" : "minecraft:dark_prismarine")); // 무너진 돌
            n++;
        }
        Painter floor = (dx, dz, r, deg) -> {
            if (r > EDGE) return null;
            if (nearCheb(holes, dx, dz, 1)) return p("minecraft:water");
            if (r > R) return p("minecraft:dark_prismarine");
            if (r > R - 1) return p("minecraft:prismarine_bricks");
            Spot rim = null;
            for (Spot h : holes) {
                if (cheb(dx, dz, h) == 2) {
                    rim = h;
                    break;
                }
            }
            if (rim != null) {
                // 물구덩이 둘레: 모서리 바다 랜턴 (물에 닿지 않음), 나머지는 산호 블록 (옆 칸이 물이라 살아 있음)
                if (Math.abs(dx - rim.dx) == 2 && Math.abs(dz - rim.dz) == 2) return p("minecraft:sea_lantern");
                return p("minecraft:" + CORALS[(int) Math.floor(hash(a, dx, dz, 5) * CORALS.length)] + "_coral_block");
            }
            if (r <= 2.5) return p("minecraft:dark_prismarine");
            if (r <= 3.5) return p("minecraft:prismarine_bricks");
            if (r <= 4.5) {
                // 크라켄 둘레 빛나는 고리: 촉수 사이 여덟 곳 바다 랜턴
                double d = DEG(dx, dz);
                return Math.abs(((d + 22.5) % 45) - 22.5) < 6 ? p("minecraft:sea_lantern") : p("minecraft:dark_prismarine");
            }
            if (onPath.test(dx, dz)) return p("minecraft:prismarine_bricks");
            if ((r > 7.5 && r <= 8.5) || (r > 15.5 && r <= 16.5)) return p("minecraft:dark_prismarine"); // 고리 무늬
            double h = hash(a, dx, dz);
            if (sunkenPatch(dx, dz, 1.7) > 1.45) return p(h < 0.3 ? "minecraft:gravel" : "minecraft:sand"); // 모래 퇴적
            if (h < 0.06) return p("minecraft:dark_prismarine");
            if (h < 0.1) return p("minecraft:prismarine_bricks");
            return p("minecraft:prismarine");
        };
        Paint wall = p("minecraft:prismarine_wall");
        List<Stage> stages = List.of(
                new Stage("땅 고르기", clearLayers(H, EDGE), List.of(), "dig.stone"),
                new Stage("기초", List.of(fillLayer(-3, EDGE, p("minecraft:prismarine")), fillLayer(-2, EDGE, p("minecraft:dark_prismarine"))), List.of(), "dig.stone"),
                new Stage("바닥", List.of(new Layer(-1, floor)), List.of(), "bucket.empty_water"),
                new Stage("담장·기둥", List.of(new Layer(0, (dx, dz, r, deg) -> ring.cell.test(dx, dz) ? wall : null)), struct, "dig.stone"),
                new Stage("장식", List.of(), deco, "dig.coral"));
        Feat f = new Feat(List.of(), List.of(), gates, posts, pillars, holes, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        return new Plan(stages, f);
    }

    /** spots 중 하나의 체비쇼프 거리 k 안인지 */
    private static boolean nearCheb(List<Spot> spots, int dx, int dz, int k) {
        for (Spot s : spots) if (cheb(dx, dz, s) <= k) return true;
        return false;
    }

    private static double sunkenPatch(double dx, double dz, double k) {
        return StrictMath.sin(dx * 0.58 + k) + StrictMath.sin(dz * 0.51 + k * 2.1) + StrictMath.sin((dx + dz) * 0.33 + k * 0.7);
    }

    // ------------------------------------------------------------ 거대 웜: 심층의 신전

    /** 거대 웜 필드의 체크무늬 한 칸 크기 (칸 — 설정 BOSSES.<키>.quake.tile, 없으면 3) */
    public static int deepTileSize(String key) {
        Obj b = BOSSES.objOrNull(key);
        Obj q = b == null ? null : b.objOrNull("quake");
        Double t = q == null ? null : q.numOrNull("tile");
        return t == null ? 3 : t.intValue();
    }

    public static int deepTileSize() {
        return deepTileSize("great_worm");
    }

    /** 체크무늬 칸 번호 (i, j) 와 어두운 칸인지 */
    public record Tile(int i, int j, boolean dark) {}

    /**
     * 체크무늬 칸 (v2.1.0 심층의 신전): 제단 기준 블록 칸 (dx, dz) → 칸 번호 (i, j), 어두운 칸이면 dark.
     * 가운데 칸 = 제단을 가운데로 한 size×size (크기 3이면 dx -1~1). 대지 분출 판정과 바닥 칠하기가 같은 식을 씀
     */
    public static Tile deepTile(int dx, int dz, int size) {
        int h = Math.floorDiv(size, 2);
        int i = Math.floorDiv(dx + h, size);
        int j = Math.floorDiv(dz + h, size);
        return new Tile(i, j, (((i + j) % 2) + 2) % 2 == 0);
    }

    public static Tile deepTile(int dx, int dz) {
        return deepTile(dx, dz, 3);
    }

    /** 거대 웜의 굴 입구 (제단 기준 블록 칸): 가운데 + 대각선 넷 (체크무늬 어두운 칸의 가운데) */
    private static List<Spot> deepHoles(int size) {
        int k = 3 * size;
        return List.of(new Spot(0, 0), new Spot(k, k), new Spot(-k, k), new Spot(-k, -k), new Spot(k, -k));
    }

    /**
     * 거대 웜 (v2.1.0): 심층의 신전. 바닥 = 3×3칸 체크무늬 (어두운 심층암 타일 / 밝은 윤나는 응회암) — 대지 분출이 한쪽 칸씩 솟음 (담장 밖 둘레까지 같은 무늬),
     * 굴 입구 5곳 (가운데 + 대각선): 파헤쳐진 흙 칸 (진흙투성이 맹그로브 뿌리: 어두운 칸 색을 지키면서 굴이 눈에 띔), 가운데는 진흙 (가운데 굴은 제단). 가장자리 심층암 벽돌 담장 (높이 1),
     * 네 성문 = 양쪽 기둥 + 들보 + 매단 영혼 랜턴, 대각선 담장 탑 (꼭대기 영혼 랜턴).
     * 장식은 가장자리에만 (종유석 석순, 자수정 무리, 켜진 양초, 스컬크, 무너진 심층암)
     */
    private static Plan deep(Ctx a) {
        int R = a.R;
        int H = a.H;
        DoubleSupplier rand = seeded(a);
        int EDGE = R + 2;
        int size = deepTileSize(a.key);
        List<Spot> gates = polars(R + 1, GATE_DEG);
        Gap onGate = (dx, dz) -> Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
        List<Spot> holes = deepHoles(size);
        int hh = Math.floorDiv(size, 2);
        Ring ring = ringOf(R, onGate);
        List<Flank> flanks = flanks(R, ring.cells, 2);
        List<Spot> towers = new ArrayList<>();
        for (double d : DIAG_DEG) towers.add(nearestCell(ring.cells, polar(R + 1, d)));
        List<Spot> posts = new ArrayList<>(spotsOf(flanks));
        posts.addAll(towers);
        String[] pillar = {"minecraft:polished_deepslate", "minecraft:deepslate_bricks", "minecraft:deepslate_bricks", "minecraft:chiseled_deepslate"};
        List<Put> struct = new ArrayList<>();
        for (Flank f : flanks) {
            for (int dy = 0; dy < pillar.length; dy++) struct.add(put(f.dx, dy, f.dz, pillar[dy]));
        }
        // 들보 (두 기둥 위) + 가운데 매단 영혼 랜턴
        for (int gi = 0; gi < 4; gi++) {
            Beam b = beam(flanks, gi);
            for (int k = b.lo; k <= b.hi; k++) {
                struct.add(put(b.dx(k), 4, b.dz(k), k == b.lo || k == b.hi ? "minecraft:polished_deepslate" : "minecraft:deepslate_tiles"));
            }
            int mid = round((b.lo + b.hi) / 2.0);
            struct.add(put(b.dx(mid), 3, b.dz(mid), "minecraft:soul_lantern", st("hanging", true)));
        }
        for (Spot t : towers) {
            for (int dy = 0; dy < pillar.length; dy++) struct.add(put(t.dx, dy, t.dz, pillar[dy]));
            struct.add(put(t.dx, 4, t.dz, "minecraft:soul_lantern", st("hanging", false)));
        }
        // 장식: 가장자리 띠에만 (싸우는 가운데·성문 길·굴·담장 기둥 둘레 제외)
        List<Put> deco = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        String[] candles = {"minecraft:gray_candle", "minecraft:black_candle", "minecraft:candle"};
        for (int n = 0, tries = 0; n < 30 && tries < 700; tries++) {
            double deg = rand.getAsDouble() * 360;
            double r = R - 1.5 - rand.getAsDouble() * 2.5;
            Spot q = polar(r, deg);
            long k = cellKey(q.dx, q.dz);
            if (taken.contains(k) || Math.abs(q.dx) <= 2 || Math.abs(q.dz) <= 2 || nearPost(posts, q.dx, q.dz) || holeOf(holes, hh, q.dx, q.dz) != null
                    || hypot(q.dx, q.dz) > R - 1) continue;
            taken.add(k);
            double pick = rand.getAsDouble();
            if (pick < 0.3) {
                // 석순: 점적석 블록 + 뾰족한 점적석 1~2칸 (위로)
                deco.add(put(q.dx, 0, q.dz, "minecraft:dripstone_block"));
                if (rand.getAsDouble() < 0.5) {
                    deco.add(put(q.dx, 1, q.dz, "minecraft:pointed_dripstone", st("dripstone_thickness", "frustum", "hanging", false)));
                    deco.add(put(q.dx, 2, q.dz, "minecraft:pointed_dripstone", st("dripstone_thickness", "tip", "hanging", false)));
                } else deco.add(put(q.dx, 1, q.dz, "minecraft:pointed_dripstone", st("dripstone_thickness", "tip", "hanging", false)));
            } else if (pick < 0.5) {
                // 자수정: 자수정 블록 + 위로 자란 무리 (빛남)
                deco.add(put(q.dx, 0, q.dz, "minecraft:amethyst_block"));
                deco.add(put(q.dx, 1, q.dz, "minecraft:amethyst_cluster", st("minecraft:block_face", "up")));
            } else if (pick < 0.7) {
                String candle = candles[(int) Math.floor(rand.getAsDouble() * candles.length)];
                deco.add(put(q.dx, 0, q.dz, candle, st("candles", (int) Math.floor(rand.getAsDouble() * 4), "lit", true)));
            } else if (pick < 0.85) deco.add(put(q.dx, 0, q.dz, "minecraft:sculk"));
            else deco.add(put(q.dx, 0, q.dz, rand.getAsDouble() < 0.5 ? "minecraft:cobbled_deepslate" : "minecraft:cracked_deepslate_bricks")); // 무너진 돌
            n++;
        }
        Map<String, Object> rootsY = AXIS_Y;
        Painter floor = (dx, dz, r, deg) -> {
            if (r > EDGE) return null;
            Spot o = holeOf(holes, hh, dx, dz);
            if (o != null) return dx == o.dx && dz == o.dz && (dx != 0 || dz != 0) ? p("minecraft:mud") : p("minecraft:muddy_mangrove_roots", rootsY);
            return deepTile(dx, dz, size).dark ? p("minecraft:deepslate_tiles") : p("minecraft:polished_tuff");
        };
        Paint wall = p("minecraft:deepslate_brick_wall");
        Paint deepslate = p("minecraft:deepslate", AXIS_Y);
        List<Stage> stages = List.of(
                new Stage("땅 고르기", clearLayers(H, EDGE), List.of(), "dig.stone"),
                new Stage("기초", List.of(fillLayer(-3, EDGE, deepslate), fillLayer(-2, EDGE, p("minecraft:cobbled_deepslate"))), List.of(), "dig.deepslate"),
                new Stage("바닥", List.of(new Layer(-1, floor)), List.of(), "place.deepslate"),
                new Stage("담장·기둥", List.of(new Layer(0, (dx, dz, r, deg) -> ring.cell.test(dx, dz) ? wall : null)), struct, "dig.deepslate_bricks"),
                new Stage("장식", List.of(), deco, "place.amethyst_block"));
        Feat f = new Feat(List.of(), List.of(), gates, posts, List.of(), holes, size, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        return new Plan(stages, f);
    }

    private static Spot holeOf(List<Spot> holes, int h, int dx, int dz) {
        for (Spot o : holes) if (Math.abs(dx - o.dx) <= h && Math.abs(dz - o.dz) <= h) return o;
        return null;
    }

    // ------------------------------------------------------------ 요르문간드: 뱀의 섬

    /** 뱀의 섬 치수 (섬 반지름 · 해자 바깥 반지름 · 범람 때 안전한 흰 고리 반지름) */
    public record Isle(double island, double moat, double safe) {}

    /** 뱀의 섬 치수 (v2.2.0 요르문간드, 설정 BOSSES.<키>.arena) */
    public static Isle isleSize(String key) {
        Obj ar = arenaOf(key);
        return new Isle(numOr(ar, "island", 13), numOr(ar, "moat", 18.5), numOr(ar, "safe", 7));
    }

    public static Isle isleSize() {
        return isleSize("jormungandr");
    }

    private static Obj arenaOf(String key) {
        Obj b = BOSSES.objOrNull(key);
        return b == null ? null : b.objOrNull("arena");
    }

    private static double numOr(Obj o, String k, double fallback) {
        return o == null ? fallback : o.numOr(k, fallback);
    }

    /**
     * 요르문간드 (v2.2.0): 뱀의 섬. 가운데 섬(윤나는 안산암) = 가운데 단(조각된 석재 벽돌 + 청록 구리 고리, 제단), 석재 벽돌 고리 무늬,
     * 흰 석영 고리(반지름 safe = 독해 범람 때 안전한 곳), 대각선 넷 청록 구리 줄(비늘 무늬), 이끼 낀 석재 벽돌 물가.
     * 섬 둘레 독의 해자 (물 1칸 깊이, 바닥 진흙·점토·자갈 + 초록 개구리불: 독처럼 빛남, 수련 몇 장). 다리 없음.
     * 해자 밖 둑 (이끼 낀 석재 벽돌·금 간 석재 벽돌) + 이끼 낀 석재 벽돌 담장 (높이 1), 네 성문 = 양쪽 기둥 + 들보(청록 구리) + 매단 랜턴,
     * 대각선 담장 탑 (꼭대기 켜진 청록 구리 전구). 장식은 둑의 담장 쪽에만 (이끼 카펫, 뼈 블록, 이끼 낀 조약돌)
     */
    private static Plan isle(Ctx a) {
        int R = a.R;
        int H = a.H;
        DoubleSupplier rand = seeded(a);
        int EDGE = R + 2;
        Isle size = isleSize(a.key);
        double ISL = size.island;
        double MOAT = size.moat;
        double SAFE = size.safe;
        List<Spot> gates = polars(R + 1, GATE_DEG);
        Gap onGate = (dx, dz) -> Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
        Ring ring = ringOf(R, onGate);
        List<Flank> flanks = flanks(R, ring.cells, 2);
        List<Spot> towers = new ArrayList<>();
        for (double d : DIAG_DEG) towers.add(nearestCell(ring.cells, polar(R + 1, d)));
        List<Spot> posts = new ArrayList<>(spotsOf(flanks));
        posts.addAll(towers);
        String[] pillar = {"minecraft:mossy_stone_bricks", "minecraft:stone_bricks", "minecraft:stone_bricks", "minecraft:chiseled_stone_bricks"};
        List<Put> struct = new ArrayList<>();
        for (Flank f : flanks) {
            for (int dy = 0; dy < pillar.length; dy++) struct.add(put(f.dx, dy, f.dz, pillar[dy]));
        }
        // 들보 (두 기둥 위: 끝은 이끼 낀 석재 벽돌, 가운데 청록 구리) + 가운데 매단 랜턴
        for (int gi = 0; gi < 4; gi++) {
            Beam b = beam(flanks, gi);
            for (int k = b.lo; k <= b.hi; k++) {
                struct.add(put(b.dx(k), 4, b.dz(k), k == b.lo || k == b.hi ? "minecraft:mossy_stone_bricks" : "minecraft:waxed_oxidized_cut_copper"));
            }
            int mid = round((b.lo + b.hi) / 2.0);
            struct.add(put(b.dx(mid), 3, b.dz(mid), "minecraft:lantern", st("hanging", true)));
        }
        for (Spot t : towers) {
            for (int dy = 0; dy < pillar.length; dy++) struct.add(put(t.dx, dy, t.dz, pillar[dy]));
            struct.add(put(t.dx, 4, t.dz, "minecraft:waxed_oxidized_copper_bulb", st("lit", true, "powered_bit", false)));
        }
        // 장식: 둑의 담장 쪽 (반지름 R-1 ~ R, 성문 길·담장 기둥 둘레 제외) + 해자에 수련 몇 장
        List<Put> deco = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        for (int n = 0, tries = 0; n < 22 && tries < 700; tries++) {
            double deg = rand.getAsDouble() * 360;
            Spot q = polar(R - rand.getAsDouble() * 0.9, deg);
            double r = hypot(q.dx, q.dz);
            long k = cellKey(q.dx, q.dz);
            if (taken.contains(k) || r <= MOAT + 0.6 || r > R || Math.abs(q.dx) <= 2 || Math.abs(q.dz) <= 2 || nearPost(posts, q.dx, q.dz)) continue;
            taken.add(k);
            double pick = rand.getAsDouble();
            if (pick < 0.55) deco.add(put(q.dx, 0, q.dz, "minecraft:moss_carpet"));
            else if (pick < 0.8) deco.add(put(q.dx, 0, q.dz, "minecraft:bone_block", st("pillar_axis", rand.getAsDouble() < 0.5 ? "x" : "z"))); // 먹힌 것들의 뼈
            else deco.add(put(q.dx, 0, q.dz, "minecraft:mossy_cobblestone")); // 무너진 돌
            n++;
        }
        for (int n = 0, tries = 0; n < 10 && tries < 400; tries++) {
            double rr = ISL + 1 + rand.getAsDouble() * (MOAT - ISL - 1.5);
            Spot q = polar(rr, rand.getAsDouble() * 360);
            long k = cellKey(q.dx, q.dz);
            double r = hypot(q.dx, q.dz);
            if (taken.contains(k) || !(r > ISL && r <= MOAT) || onGate.test(q.dx, q.dz)) continue;
            taken.add(k);
            deco.add(put(q.dx, 0, q.dz, "minecraft:waterlily"));
            n++;
        }
        Paint quartz = p("minecraft:smooth_quartz", AXIS_Y);
        Painter floor = (dx, dz, r, deg) -> {
            if (r > EDGE) return null;
            if (r > R) return p("minecraft:mossy_cobblestone");
            if (r > MOAT) return p(hash(a, dx, dz, 3) < 0.25 ? "minecraft:cracked_stone_bricks" : "minecraft:mossy_stone_bricks"); // 둑
            if (r > ISL && r <= MOAT) return p("minecraft:water");
            if (r > ISL - 1) return p("minecraft:mossy_stone_bricks"); // 물가
            if (r <= 1.5) return p("minecraft:chiseled_stone_bricks"); // 가운데 단 (제단)
            if (r <= 2.5) return p("minecraft:waxed_oxidized_cut_copper");
            if (r > SAFE - 1 && r <= SAFE) return quartz; // 범람 때 안전한 흰 고리 (칸 바깥 가장자리 약 SAFE + 0.7 까지 안전)
            if ((r > 3.5 && r <= 4.5) || (r > 9.5 && r <= 10.5)) return p("minecraft:stone_bricks");
            // 대각선 넷 청록 구리 줄 (폭 약 2칸, 고리 사이)
            if (r > 4.5 && Math.min(Math.abs(dx - dz), Math.abs(dx + dz)) <= 1) return p("minecraft:waxed_oxidized_cut_copper");
            double h = hash(a, dx, dz);
            if (islePatch(dx, dz, 2.1) > 1.5) return p(h < 0.5 ? "minecraft:andesite" : "minecraft:mossy_stone_bricks"); // 이끼·거친 돌 조각
            return p(h < 0.08 ? "minecraft:cracked_stone_bricks" : "minecraft:polished_andesite");
        };
        // 해자 바닥: 진흙·점토·자갈 조각 + 초록 개구리불 (독처럼 빛남)
        Paint froglight = p("minecraft:verdant_froglight", AXIS_Y);
        Painter bed = (dx, dz, r, deg) -> {
            if (r > EDGE) return null;
            if (!(r > ISL && r <= MOAT)) return p(r > MOAT ? "minecraft:mossy_cobblestone" : "minecraft:andesite");
            if (hash(a, dx, dz, 7) < 0.07) return froglight;
            double q = islePatch(dx, dz, 4.2);
            return p(q > 1.1 ? "minecraft:clay" : q < -1.2 ? "minecraft:gravel" : "minecraft:mud");
        };
        Paint wall = p("minecraft:mossy_stone_brick_wall");
        List<Stage> stages = List.of(
                new Stage("땅 고르기", clearLayers(H, EDGE), List.of(), "dig.stone"),
                new Stage("기초", List.of(fillLayer(-3, EDGE, p("minecraft:tuff")), new Layer(-2, bed)), List.of(), "dig.gravel"),
                new Stage("바닥·해자", List.of(new Layer(-1, floor)), List.of(), "bucket.empty_water"),
                new Stage("담장·기둥", List.of(new Layer(0, (dx, dz, r, deg) -> ring.cell.test(dx, dz) ? wall : null)), struct, "dig.stone"),
                new Stage("장식", List.of(), deco, "dig.grass"));
        return new Plan(stages, feat(List.of(), List.of(), gates, posts));
    }

    private static double islePatch(double dx, double dz, double k) {
        return StrictMath.sin(dx * 0.61 + k) + StrictMath.sin(dz * 0.47 + k * 2.3) + StrictMath.sin((dx - dz) * 0.37 + k * 0.9);
    }

    // ------------------------------------------------------------ 니드호그: 세계수의 뿌리

    /** 세계수의 뿌리 치수 (뿌리가 담장 탑에서 나오는 높이 arch · 땅에 닿는 가운데에서의 거리 ground) */
    public record Roots(double arch, double ground) {}

    /** 세계수의 뿌리 치수 (v2.3.0 니드호그, 설정 BOSSES.<키>.arena) */
    public static Roots rootsSize(String key) {
        Obj ar = arenaOf(key);
        return new Roots(numOr(ar, "arch", 8), numOr(ar, "ground", 12));
    }

    public static Roots rootsSize() {
        return rootsSize("nidhogg");
    }

    /** 뿌리 가운데 줄의 한 점: 가운데에서의 거리 r, 각도 deg (도), 아랫면 높이 b, 굵기 (반지름) rho */
    public record RootPoint(double r, double deg, double b, double rho) {}

    /** 뿌리 하나의 가운데 줄 (s = 0 담장 탑 ~ 1 땅에 닿음 ~ 1.25 땅속) */
    public static RootPoint rootAt(double R, double arch, double ground, double deg, double turn, double s) {
        double r = R + 1 - s * (R + 1 - ground);
        double b = s <= 1 ? arch * StrictMath.pow(1 - s, 1.4) : -(s - 1) * 8;
        return new RootPoint(r, deg + turn * s, b, 1.7 - 0.4 * Math.min(1, s));
    }

    /**
     * 니드호그 (v2.3.0): 세계수의 뿌리. 가운데 수액 샘(깎은 송진 벽돌 + 송진 벽돌 + 버섯불 고리 = 제단), 대각선 네 줄로 흐르는 수액(버섯불 줄기),
     * 뿌리 흙·이끼·창백한 이끼·진흙·회백토가 섞인 바닥, 검은 돌 가장자리. 대각선 네 담장 탑에서 창백한 참나무 뿌리가 필드로 휘어 들어와
     * 가운데에서 ground 칸 거리에서 땅속으로 파고듦 (담장 쪽은 아래로 지나갈 수 있는 아치, 뿌리 밑에 늘어진 뿌리·창백한 이끼, 군데군데 빛나는 수액).
     * 윤나는 흑암 벽돌 담장 (높이 1), 네 성문 = 양쪽 기둥 + 들보(가운데 우는 흑요석) + 매단 영혼 랜턴. 장식은 둑의 담장 쪽에만 (뼈 더미·해골: 시체의 해안)
     */
    private static Plan roots(Ctx a) {
        int R = a.R;
        int H = a.H;
        DoubleSupplier rand = seeded(a);
        int EDGE = R + 2;
        Roots size = rootsSize(a.key);
        double ARCH = size.arch;
        double GROUND = size.ground;
        List<Spot> gates = polars(R + 1, GATE_DEG);
        Gap onGate = (dx, dz) -> Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
        Ring ring = ringOf(R, onGate);
        List<Flank> flanks = flanks(R, ring.cells, 2);
        double[] rootDeg = {45, 135, 225, 315};
        List<Spot> towers = new ArrayList<>();
        for (double d : rootDeg) towers.add(nearestCell(ring.cells, polar(R + 1, d)));
        List<Spot> posts = new ArrayList<>(spotsOf(flanks));
        posts.addAll(towers);
        List<Put> struct = new ArrayList<>();
        String[] pillar = {"minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone_bricks", "minecraft:polished_blackstone_bricks", "minecraft:chiseled_polished_blackstone"};
        for (Flank f : flanks) {
            for (int dy = 0; dy < pillar.length; dy++) struct.add(put(f.dx, dy, f.dz, pillar[dy]));
        }
        // 들보 (끝은 윤나는 흑암 벽돌, 가운데 우는 흑요석) + 가운데 매단 영혼 랜턴
        for (int gi = 0; gi < 4; gi++) {
            Beam b = beam(flanks, gi);
            int mid = round((b.lo + b.hi) / 2.0);
            for (int k = b.lo; k <= b.hi; k++) struct.add(put(b.dx(k), 4, b.dz(k), k == mid ? "minecraft:crying_obsidian" : "minecraft:polished_blackstone_bricks"));
            struct.add(put(b.dx(mid), 3, b.dz(mid), "minecraft:soul_lantern", st("hanging", true)));
        }
        // 세계수의 뿌리: 탑 자리에서 곧게 올라온 줄기(arch + 2 칸) → 필드 쪽으로 휘어 내려와 ground 칸 거리에서 땅속으로
        Map<Long, Put> rootCells = new LinkedHashMap<>();
        for (int i = 0; i < rootDeg.length; i++) {
            double deg = rootDeg[i];
            double turn = i % 2 != 0 ? -14 : 14;
            Spot t = towers.get(i);
            for (int dy = -1; dy <= ARCH + 2; dy++) {
                for (int[] o : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    putRoot(rootCells, H, t.dx + o[0], dy, t.dz + o[1], hash(a, t.dx + o[0], t.dz + o[1], dy) < 0.12 ? "minecraft:dark_oak_wood" : "minecraft:pale_oak_wood");
                }
            }
            for (double s = 0; s <= 1.25; s += 0.004) {
                RootPoint q = rootAt(R, ARCH, GROUND, deg, turn, s);
                double cx = q.r * StrictMath.cos((q.deg * Math.PI) / 180);
                double cz = q.r * StrictMath.sin((q.deg * Math.PI) / 180);
                double cy = q.b + q.rho;
                double span = Math.ceil(q.rho);
                for (int dx = (int) Math.floor(cx - span); dx <= Math.ceil(cx + span); dx++) {
                    for (int dz = (int) Math.floor(cz - span); dz <= Math.ceil(cz + span); dz++) {
                        for (int dy = (int) Math.floor(cy - span); dy <= Math.ceil(cy + span); dy++) {
                            if (hypot(dx - cx, dz - cz, dy - cy) > q.rho + 0.15) continue;
                            boolean glow = hash(a, dx, dz, dy + 11) < 0.05 && dy >= 0;
                            putRoot(rootCells, H, dx, dy, dz, glow ? "minecraft:shroomlight" : hash(a, dx, dz, dy) < 0.1 ? "minecraft:dark_oak_wood" : "minecraft:pale_oak_wood");
                        }
                    }
                }
            }
        }
        // 뿌리 밑에 늘어진 뿌리·창백한 이끼 (아래가 비어 있는 뿌리 칸 밑)
        List<Put> hang = new ArrayList<>();
        for (Put c : rootCells.values()) {
            if (c.dy < 3 || rootCells.containsKey(cellKey(c.dx, c.dy - 1, c.dz)) || hypot(c.dx, c.dz) > R) continue;
            double h = hash(a, c.dx, c.dz, c.dy + 5);
            if (h < 0.22) hang.add(put(c.dx, c.dy - 1, c.dz, "minecraft:hanging_roots"));
            else if (h < 0.34) hang.add(put(c.dx, c.dy - 1, c.dz, "minecraft:pale_hanging_moss", st("tip", true)));
        }
        List<Put> rootList = new ArrayList<>(rootCells.values());
        Set<Long> rootAtCell = new HashSet<>();
        for (Put c : rootList) if (c.dy == -1) rootAtCell.add(cellKey(c.dx, c.dz));
        // 장식: 둑의 담장 쪽 (반지름 R-1.5 ~ R, 성문 길·기둥·뿌리 둘레 제외) — 시체의 해안 (뼈 더미, 해골)
        List<Put> deco = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        for (int n = 0, tries = 0; n < 20 && tries < 700; tries++) {
            double deg = rand.getAsDouble() * 360;
            Spot q = polar(R - rand.getAsDouble() * 1.2, deg);
            double r = hypot(q.dx, q.dz);
            long k = cellKey(q.dx, q.dz);
            if (taken.contains(k) || r > R || r < R - 1.6 || Math.abs(q.dx) <= 2 || Math.abs(q.dz) <= 2 || nearPost(posts, q.dx, q.dz) || nearRoot(rootList, q.dx, q.dz)) continue;
            taken.add(k);
            double pick = rand.getAsDouble();
            if (pick < 0.55) deco.add(put(q.dx, 0, q.dz, "minecraft:bone_block", st("pillar_axis", rand.getAsDouble() < 0.5 ? "x" : "z")));
            else if (pick < 0.8) deco.add(put(q.dx, 0, q.dz, "minecraft:skeleton_skull", st("facing_direction", 1)));
            else deco.add(put(q.dx, 0, q.dz, "minecraft:pale_moss_carpet"));
            n++;
        }
        Painter floor = (dx, dz, r, deg) -> {
            if (r > EDGE) return null;
            if (r > R) return p(hash(a, dx, dz, 3) < 0.25 ? "minecraft:cracked_polished_blackstone_bricks" : "minecraft:polished_blackstone_bricks"); // 둑
            if (r > R - 1) return p("minecraft:polished_blackstone"); // 가장자리
            if (r <= 1.5) return p("minecraft:chiseled_resin_bricks"); // 수액 샘 (제단)
            if (r <= 3) return p("minecraft:resin_bricks");
            if (r <= 3.8) return p("minecraft:shroomlight");
            // 수액 줄기: 대각선 넷 (가운데 → 뿌리가 땅에 닿는 곳)
            int d = Math.min(Math.abs(dx - dz), Math.abs(dx + dz));
            if (r < GROUND - 0.5 && (d == 0 || (d == 1 && hash(a, dx, dz, 9) < 0.35))) return p("minecraft:shroomlight");
            double h = hash(a, dx, dz);
            double pa = rootsPatch(dx, dz, 1.3);
            double pb = rootsPatch(dx, dz, 4.1);
            if (pa > 1.4) return p(h < 0.55 ? "minecraft:moss_block" : "minecraft:pale_moss_block");
            if (pb > 1.5) return p("minecraft:mud");
            if (pb < -1.6) return p("minecraft:podzol");
            return p(h < 0.12 ? "minecraft:coarse_dirt" : "minecraft:dirt_with_roots");
        };
        List<Put> wallPuts = new ArrayList<>(struct);
        wallPuts.addAll(rootList);
        List<Put> decoPuts = new ArrayList<>(hang);
        decoPuts.addAll(deco);
        Paint wall = p("minecraft:polished_blackstone_brick_wall");
        List<Stage> stages = List.of(
                new Stage("땅 고르기", clearLayers(H, EDGE), List.of(), "dig.grass"),
                new Stage("기초", List.of(fillLayer(-3, EDGE, p("minecraft:deepslate")), fillLayer(-2, EDGE, p("minecraft:dirt"))), List.of(), "dig.gravel"),
                new Stage("바닥", List.of(new Layer(-1, (dx, dz, r, deg) -> rootAtCell.contains(cellKey(dx, dz)) ? null : floor.paint(dx, dz, r, deg))), List.of(), "dig.grass"),
                new Stage("담장·뿌리", List.of(new Layer(0, (dx, dz, r, deg) -> ring.cell.test(dx, dz) && !rootCells.containsKey(cellKey(dx, 0, dz)) ? wall : null)),
                        wallPuts, "dig.wood"),
                new Stage("장식", List.of(), decoPuts, "block.lantern.place"));
        return new Plan(stages, feat(List.of(), List.of(), gates, posts));
    }

    private static void putRoot(Map<Long, Put> rootCells, int H, int dx, int dy, int dz, String block) {
        if (dy < -1 || dy > H || (dx == 0 && dz == 0)) return;
        rootCells.putIfAbsent(cellKey(dx, dy, dz), put(dx, dy, dz, block));
    }

    private static boolean nearRoot(List<Put> rootList, int dx, int dz) {
        for (Put c : rootList) if (c.dy <= 1 && hypot(dx - c.dx, dz - c.dz) < 1.5) return true;
        return false;
    }

    private static double rootsPatch(double dx, double dz, double k) {
        return StrictMath.sin(dx * 0.53 + k) + StrictMath.sin(dz * 0.49 + k * 2.1) + StrictMath.sin((dx + dz) * 0.31 + k * 0.7);
    }

    // ------------------------------------------------------------ 스콜과 하티: 해와 달의 돌 고리

    /** 해와 달의 돌 고리 치수 (가운데 해·달 원판 반지름 disc · 선돌까지의 거리 stones) */
    public record StonesSize(double disc, double stones) {}

    /** 해와 달의 돌 고리 치수 (v2.4.0 스콜과 하티, 설정 BOSSES.<키>.arena) */
    public static StonesSize stonesSize(String key) {
        Obj ar = arenaOf(key);
        return new StonesSize(numOr(ar, "disc", 7), numOr(ar, "stones", 19.5));
    }

    public static StonesSize stonesSize() {
        return stonesSize("skoll_hati");
    }

    /** 선돌 8개의 각도 (도, 성문 사이) */
    public static final double[] STONE_DEG = {22.5, 67.5, 112.5, 157.5, 202.5, 247.5, 292.5, 337.5};

    /**
     * 원판의 어느 쪽인지 (v2.4.0 해와 달의 심판): 서 있는 칸 (제단 칸 기준 dx, dz) 으로 봄 — 칠해진 그대로.
     * 칸 가운데가 disc 안인 칸 (hypot(dx, dz) ≤ disc) 이 원판: dx ≥ 1 = 해 (금빛, 동쪽 +x), dx ≤ -1 = 달 (푸른, 서쪽 -x), dx = 0 = 경계 (흰 줄), 그 밖 = 없음 (null).
     * at = 서 있는 자리 (월드 x · z), center = 필드 가운데 (월드, 제단 칸 가운데)
     *
     * @return "sun" · "moon" · "line" · null
     */
    public static String discSide(double atX, double atZ, double centerX, double centerZ, double disc) {
        int dx = (int) Math.floor(atX) - (int) Math.floor(centerX);
        int dz = (int) Math.floor(atZ) - (int) Math.floor(centerZ);
        if (hypot(dx, dz) > disc) return null;
        return dx >= 1 ? "sun" : dx <= -1 ? "moon" : "line";
    }

    /**
     * 스콜과 하티 (v2.4.0): 해와 달의 돌 고리. 눈 덮인 들판 (눈·방해석·돌 조각), 가운데 해와 달의 원판 (반지름 disc:
     * 동쪽 반원 = 해 — 노란·주황 콘크리트 햇살 + 금 블록 해 + 버섯불, 서쪽 반원 = 달 — 검정·파랑 콘크리트 밤하늘 + 흰 초승달 + 바다 랜턴 별,
     * 가운데 남북 한 줄 = 석영 경계, 테두리 윤나는 응회암), 성문 네 길(응회암), 가장자리 응회암 벽돌 담장 (높이 1) + 성문 4 (응회암 벽돌 기둥 + 들보 + 매단 랜턴),
     * 성문 사이 선돌 8개 (가운데에서 stones 칸, 폭 2·높이 5, 동쪽 넷은 버섯불 눈 = 해, 서쪽 넷은 진줏빛 개구리불 눈 = 달). 장식은 담장 쪽 띠에만 (눈 더미·뼈·해골)
     */
    private static Plan stones(Ctx a) {
        int R = a.R;
        int H = a.H;
        DoubleSupplier rand = seeded(a);
        int EDGE = R + 2;
        StonesSize size = stonesSize(a.key);
        double DISC = size.disc;
        double SR = size.stones;
        List<Spot> gates = polars(R + 1, GATE_DEG);
        Gap onGate = (dx, dz) -> Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
        Ring ring = ringOf(R, onGate);
        List<Flank> flanks = flanks(R, ring.cells, 2);
        List<Put> struct = new ArrayList<>();
        // 성문: 양쪽 기둥 (응회암 벽돌 3 + 깎은 응회암 벽돌) + 들보 (가운데 깎은 응회암) + 매단 랜턴
        for (Flank f : flanks) {
            for (int dy = 0; dy <= 2; dy++) struct.add(put(f.dx, dy, f.dz, "minecraft:tuff_bricks"));
            struct.add(put(f.dx, 3, f.dz, "minecraft:chiseled_tuff_bricks"));
        }
        for (int gi = 0; gi < 4; gi++) {
            Beam b = beam(flanks, gi);
            int mid = round((b.lo + b.hi) / 2.0);
            for (int k = b.lo; k <= b.hi; k++) struct.add(put(b.dx(k), 4, b.dz(k), k == mid ? "minecraft:chiseled_tuff" : "minecraft:polished_tuff"));
            struct.add(put(b.dx(mid), 3, b.dz(mid), "minecraft:lantern", st("hanging", true)));
        }
        // 선돌 8개: 가운데에서 SR 칸, 둘레 방향으로 폭 2, 높이 5 (응회암 3 + 윤나는 응회암 + 깎은 응회암 벽돌 머리), 가운데 쪽 한 칸에 빛나는 눈 (동쪽 = 해, 서쪽 = 달)
        List<StoneAt> stoneList = new ArrayList<>();
        for (double deg : STONE_DEG) {
            double rad = (deg * Math.PI) / 180;
            Spot b = polar(SR, deg);
            Spot t = new Spot(round(-StrictMath.sin(rad)), round(StrictMath.cos(rad)));
            Spot[] cells = {b, new Spot(b.dx + t.dx, b.dz + t.dz)};
            boolean sun = StrictMath.cos(rad) > 0;
            stoneList.add(new StoneAt(b.dx, b.dz, sun));
            for (int i = 0; i < cells.length; i++) {
                Spot c = cells[i];
                for (int dy = 0; dy <= 2; dy++) {
                    boolean eye = dy == 2 && i == 0;
                    struct.add(put(c.dx, dy, c.dz, eye ? (sun ? "minecraft:shroomlight" : "minecraft:pearlescent_froglight") : "minecraft:tuff"));
                }
                struct.add(put(c.dx, 3, c.dz, "minecraft:polished_tuff"));
                struct.add(put(c.dx, 4, c.dz, "minecraft:chiseled_tuff_bricks"));
            }
        }
        List<Spot> posts = new ArrayList<>(spotsOf(flanks));
        for (StoneAt s : stoneList) posts.add(new Spot(s.dx, s.dz));
        // 장식: 담장 쪽 띠 (반지름 R-1.6 ~ R, 성문 길·기둥·선돌 둘레 제외) — 눈 더미, 뼈, 해골 (늑대의 사냥터)
        List<Put> deco = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        for (int n = 0, tries = 0; n < 22 && tries < 700; tries++) {
            double deg = rand.getAsDouble() * 360;
            Spot q = polar(R - rand.getAsDouble() * 1.2, deg);
            double r = hypot(q.dx, q.dz);
            long k = cellKey(q.dx, q.dz);
            if (taken.contains(k) || r > R || r < R - 1.6 || Math.abs(q.dx) <= 2 || Math.abs(q.dz) <= 2 || nearPost(posts, q.dx, q.dz)) continue;
            taken.add(k);
            double pick = rand.getAsDouble();
            if (pick < 0.5) deco.add(put(q.dx, 0, q.dz, "minecraft:snow_layer", st("height", rand.getAsDouble() < 0.5 ? 1 : 2, "covered_bit", false)));
            else if (pick < 0.78) deco.add(put(q.dx, 0, q.dz, "minecraft:bone_block", st("pillar_axis", rand.getAsDouble() < 0.5 ? "x" : "z")));
            else deco.add(put(q.dx, 0, q.dz, "minecraft:skeleton_skull", st("facing_direction", 1)));
            n++;
        }
        // 원판: 동쪽 반원 = 해 (가운데 해 원반 + 햇살 + 버섯불), 서쪽 반원 = 달 (밤하늘 + 초승달 + 별)
        Painter discBlock = (dx, dz, r, deg) -> {
            if (dx == 0) return p("minecraft:smooth_quartz"); // 경계 (남북 한 줄)
            if (r > DISC - 0.8) return p(dx > 0 ? "minecraft:raw_gold_block" : "minecraft:lapis_block"); // 테두리
            if (dx > 0) {
                double sr = hypot(dx - DISC * 0.5, dz);
                if (sr <= DISC * 0.34) return p(sr <= 1 ? "minecraft:shroomlight" : "minecraft:gold_block");
                double ang = ((StrictMath.atan2(dz, dx - DISC * 0.5) * 180) / Math.PI + 360) % 30;
                if (ang < 9) return p(hash(a, dx, dz, 5) < 0.18 ? "minecraft:shroomlight" : "minecraft:orange_concrete");
                return p("minecraft:yellow_concrete");
            }
            // 달: 초승달 = 큰 원 - 옆으로 비킨 원
            double mx = -DISC * 0.5;
            boolean inA = hypot(dx - mx, dz) <= DISC * 0.36;
            boolean inB = hypot(dx - (mx + DISC * 0.16), dz + DISC * 0.08) <= DISC * 0.3;
            if (inA && !inB) return p("minecraft:white_concrete");
            if (dx <= -2 && hash(a, dx, dz, 7) < 0.07) return p("minecraft:sea_lantern"); // 별 (가운데 흰 줄 곁에는 없음: 줄이 넓어 보이지 않게)
            return p(r > DISC * 0.6 ? "minecraft:blue_concrete" : "minecraft:black_concrete");
        };
        Painter floor = (dx, dz, r, deg) -> {
            if (r > EDGE) return null;
            if (r > R) return p(hash(a, dx, dz, 3) < 0.3 ? "minecraft:polished_tuff" : "minecraft:tuff_bricks"); // 둑
            if (r > R - 1) return p("minecraft:polished_tuff"); // 가장자리
            if (r <= DISC) return discBlock.paint(dx, dz, r, deg);
            if (r <= DISC + 1) return p("minecraft:polished_tuff"); // 원판 테두리
            if (onGate.test(dx, dz)) return p(hash(a, dx, dz, 2) < 0.35 ? "minecraft:polished_tuff" : "minecraft:tuff"); // 성문 길
            double h = hash(a, dx, dz);
            double pa = snowPatch(dx, dz, 1.7);
            double pb = snowPatch(dx, dz, 5.3);
            if (pa > 1.5) return p(h < 0.6 ? "minecraft:calcite" : "minecraft:snow");
            if (pb > 1.7) return p(h < 0.5 ? "minecraft:andesite" : "minecraft:stone");
            return p(h < 0.05 ? "minecraft:tuff" : "minecraft:snow");
        };
        Paint wall = p("minecraft:tuff_brick_wall");
        List<Stage> stages = List.of(
                new Stage("땅 고르기", clearLayers(H, EDGE), List.of(), "dig.grass"),
                new Stage("기초", List.of(fillLayer(-3, EDGE, p("minecraft:stone")), fillLayer(-2, EDGE, p("minecraft:dirt"))), List.of(), "dig.gravel"),
                new Stage("바닥", List.of(new Layer(-1, floor)), List.of(), "dig.snow"),
                new Stage("담장·선돌", List.of(new Layer(0, (dx, dz, r, deg) -> ring.cell.test(dx, dz) ? wall : null)), struct, "dig.stone"),
                new Stage("장식", List.of(), deco, "block.lantern.place"));
        Feat f = new Feat(List.of(), List.of(), gates, posts, List.of(), List.of(), null, stoneList, List.of(), List.of(), List.of(), List.of(), List.of());
        return new Plan(stages, f);
    }

    /** 눈밭 무늬 (스콜과 하티 · 이미르 같은 식) */
    private static double snowPatch(double dx, double dz, double k) {
        return StrictMath.sin(dx * 0.47 + k) + StrictMath.sin(dz * 0.53 + k * 2.3) + StrictMath.sin((dx - dz) * 0.29 + k * 0.9);
    }

    // ------------------------------------------------------------ 이미르: 태초의 빙원

    /** 태초의 빙원 치수 (가운데 얼어붙은 호수 반지름 lake · 모닥불까지의 거리 fires · 모닥불 곁 반지름 warm) */
    public record Glacier(double lake, double fires, double warm) {}

    /** 태초의 빙원 치수 (v2.5.0 이미르, 설정 BOSSES.<키>.arena) */
    public static Glacier glacierSize(String key) {
        Obj ar = arenaOf(key);
        return new Glacier(numOr(ar, "lake", 6), numOr(ar, "fires", 14), numOr(ar, "warm", 3.5));
    }

    public static Glacier glacierSize() {
        return glacierSize("ymir");
    }

    /** 무스펠헤임의 모닥불 넷의 각도 (도, 대각선: 북동·남동·남서·북서 — +x 에서 +z 쪽으로) */
    public static final double[] FIRE_DEG = {45, 135, 225, 315};
    /** 얼음 첨탑 여덟의 각도 (도, 성문과 모닥불 사이) */
    public static final double[] SPIRE_DEG = {22.5, 67.5, 112.5, 157.5, 202.5, 247.5, 292.5, 337.5};

    /** 모닥불 화덕 칸 (모닥불 가운데에서 ddx · ddz) */
    private record Hearth(int ddx, int ddz) {}

    private static Hearth hearthOf(List<Spot> fires, int dx, int dz) {
        for (Spot f : fires) if (Math.abs(dx - f.dx) <= 2 && Math.abs(dz - f.dz) <= 2) return new Hearth(dx - f.dx, dz - f.dz);
        return null;
    }

    /** 얼음 틈 (각 a · 흔들림 w · 위상 ph) */
    private record Rift(double a, double w, double ph) {}

    /**
     * 이미르 (v2.5.0): 태초의 빙원. 눈밭 (눈 + 방해석·섬록암 조각 + 파란 얼음 틈 줄), 가운데 얼어붙은 호수 (반지름 lake: 하늘색 색유리 밑에 푸른 얼음,
     * 밑바닥에 바다 랜턴 — 유리 너머로 빛남, 흰 색유리 금 줄), 호수 둘레 석영 벽돌 테 + 깎은 석영 룬 여덟, 성문 네 길 (심층암 타일 + 윤나는 심층암),
     * 가장자리 단단한 얼음 (높이 2 얼음 벽: 단단한 얼음 + 푸른 얼음, 넘을 수 없음) + 성문 4 (푸른 얼음 기둥 + 들보 + 매단 영혼 랜턴),
     * 성문과 모닥불 사이 벽 위 얼음 첨탑 여덟 (필드 밖으로만, 높이 9~13), 대각선 무스펠헤임의 모닥불 넷 (가운데에서 fires 칸:
     * 5×5 검은 돌 화덕 + 가운데 윤나는 흑암 위 모닥불 + 둘레 흑암 벽돌 담장 — 모닥불에 발을 들이지 못하게). 장식은 담장 쪽 띠에만 (눈 더미·뼈 블록·해골)
     */
    private static Plan glacier(Ctx a) {
        int R = a.R;
        int H = a.H;
        DoubleSupplier rand = seeded(a);
        int EDGE = R + 2;
        Glacier size = glacierSize(a.key);
        double LAKE = size.lake;
        double FR = size.fires;
        List<Spot> gates = polars(R + 1, GATE_DEG);
        Gap onGate = (dx, dz) -> Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
        Ring ring = ringOf(R, onGate);
        List<Flank> flanks = flanks(R, ring.cells, 2);
        // 무스펠헤임의 모닥불 넷 (대각선), 화덕 = 가운데 5×5
        List<Spot> fires = polars(FR, FIRE_DEG);
        List<Put> struct = new ArrayList<>();
        // 성문: 양쪽 기둥 (푸른 얼음 5 + 단단한 얼음 머리) + 들보 (단단한 얼음, 가운데 푸른 얼음) + 매단 영혼 랜턴
        for (Flank f : flanks) {
            for (int dy = 0; dy <= 4; dy++) struct.add(put(f.dx, dy, f.dz, "minecraft:blue_ice"));
            struct.add(put(f.dx, 5, f.dz, "minecraft:packed_ice"));
        }
        for (int gi = 0; gi < 4; gi++) {
            Beam b = beam(flanks, gi);
            int mid = round((b.lo + b.hi) / 2.0);
            for (int k = b.lo + 1; k < b.hi; k++) struct.add(put(b.dx(k), 5, b.dz(k), k == mid ? "minecraft:blue_ice" : "minecraft:packed_ice"));
            struct.add(put(b.dx(mid), 4, b.dz(mid), "minecraft:soul_lantern", st("hanging", true)));
        }
        // 모닥불: 화덕 가운데 윤나는 흑암 위에 불이 켜진 모닥불 (둘레 담장은 층에서)
        for (Spot f : fires) struct.add(put(f.dx, 1, f.dz, "minecraft:campfire", st("extinguished", false, "minecraft:cardinal_direction", "south")));
        // 얼음 첨탑 여덟: 벽 고리 칸에서 밖으로만 (필드 안으로 들어오지 않게, 둑 끝까지), 아래는 넓고 위로 갈수록 좁아짐, 꼭대기 푸른 얼음
        List<SpireAt> spires = new ArrayList<>();
        for (int i = 0; i < SPIRE_DEG.length; i++) {
            Spot c = nearestCell(ring.cells, polar(R + 1, SPIRE_DEG[i]));
            int h = 9 + (i % 3) * 2;
            spires.add(new SpireAt(c.dx, c.dz, h));
            List<double[]> cells = new ArrayList<>();
            for (int ox = -2; ox <= 2; ox++) {
                for (int oz = -2; oz <= 2; oz++) {
                    int x = c.dx + ox;
                    int z = c.dz + oz;
                    double r = hypot(x, z);
                    double d = hypot(ox, oz);
                    if (d > 1.5 || r <= R + 0.5 || r > EDGE) continue;
                    cells.add(new double[] {x, z, d});
                }
            }
            for (double[] q : cells) {
                int qx = (int) q[0];
                int qz = (int) q[1];
                double qd = q[2];
                // 넓은 밑동 (d ≤ 1.5): h 의 40%, 가운데 줄기 (d ≤ 1): h 의 70%, 꼭대기 (가운데 칸): h
                int top = qd < 0.5 ? h : qd <= 1.01 ? round(h * 0.7) : round(h * 0.4);
                for (int dy = 0; dy < top; dy++) {
                    boolean tip = qd < 0.5 && dy >= h - 2;
                    boolean vein = qd < 0.5 ? dy % 3 == 1 : hash(a, qx, qz, dy) < 0.18;
                    struct.add(put(qx, dy, qz, tip || vein ? "minecraft:blue_ice" : "minecraft:packed_ice"));
                }
            }
        }
        List<Spot> posts = new ArrayList<>(spotsOf(flanks));
        posts.addAll(fires);
        for (SpireAt s : spires) posts.add(new Spot(s.dx, s.dz));
        // 장식: 담장 쪽 띠 (반지름 R-1.6 ~ R, 성문 길·기둥·첨탑 둘레 제외) — 눈 더미, 뼈 블록 (산맥이 된 거인의 뼈), 해골
        List<Put> deco = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        for (int n = 0, tries = 0; n < 24 && tries < 800; tries++) {
            double deg = rand.getAsDouble() * 360;
            Spot q = polar(R - rand.getAsDouble() * 1.2, deg);
            double r = hypot(q.dx, q.dz);
            long k = cellKey(q.dx, q.dz);
            if (taken.contains(k) || r > R || r < R - 1.6 || Math.abs(q.dx) <= 2 || Math.abs(q.dz) <= 2 || nearPost(posts, q.dx, q.dz)) continue;
            taken.add(k);
            double pick = rand.getAsDouble();
            if (pick < 0.5) deco.add(put(q.dx, 0, q.dz, "minecraft:snow_layer", st("height", rand.getAsDouble() < 0.5 ? 1 : 2, "covered_bit", false)));
            else if (pick < 0.8) deco.add(put(q.dx, 0, q.dz, "minecraft:bone_block", st("pillar_axis", rand.getAsDouble() < 0.5 ? "x" : "z")));
            else deco.add(put(q.dx, 0, q.dz, "minecraft:skeleton_skull", st("facing_direction", 1)));
            n++;
        }
        // 호수 금 줄 (가운데에서 뻗는 흰 색유리 다섯 줄) · 룬 여덟 (호수 둘레 테) · 눈밭의 파란 얼음 틈 여섯 줄 (성문 길·모닥불 화덕을 피해 구불구불)
        double[] cracks = new double[5];
        for (int i = 0; i < 5; i++) cracks[i] = ((i * 72 + rand.getAsDouble() * 30) * Math.PI) / 180;
        List<Rift> rifts = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            double ra = ((i * 60 + 20 + rand.getAsDouble() * 20) * Math.PI) / 180;
            double rw = 1.2 + rand.getAsDouble() * 1.2;
            double rph = rand.getAsDouble() * 6;
            rifts.add(new Rift(ra, rw, rph));
        }
        Painter floor = (dx, dz, r, deg) -> {
            if (r > EDGE) return null;
            if (r > R) return p(hash(a, dx, dz, 3) < 0.25 ? "minecraft:packed_ice" : "minecraft:snow"); // 둑
            if (r > R - 1) return p("minecraft:packed_ice"); // 가장자리
            Hearth hf = hearthOf(fires, dx, dz);
            if (hf != null) {
                if (Math.abs(hf.ddx) == 2 && Math.abs(hf.ddz) == 2) return p("minecraft:gilded_blackstone");
                return p(Math.abs(hf.ddx) <= 1 && Math.abs(hf.ddz) <= 1 ? "minecraft:polished_blackstone" : "minecraft:polished_blackstone_bricks");
            }
            if (r <= LAKE) {
                boolean crack = false;
                if (r >= 1.5) {
                    for (double c : cracks) {
                        if (angDist(StrictMath.atan2(dz, dx), c) * r <= 0.55) {
                            crack = true;
                            break;
                        }
                    }
                }
                return p(crack ? "minecraft:white_stained_glass" : "minecraft:light_blue_stained_glass");
            }
            if (r <= LAKE + 1.2) {
                boolean rune = false;
                for (int d = 0; d < 360; d += 45) {
                    if (angDist(StrictMath.atan2(dz, dx), (d * Math.PI) / 180) * r <= 0.5) {
                        rune = true;
                        break;
                    }
                }
                return p(rune ? "minecraft:chiseled_quartz_block" : "minecraft:quartz_bricks");
            }
            if (onGate.test(dx, dz)) return p(dx == 0 || dz == 0 ? "minecraft:deepslate_tiles" : "minecraft:polished_deepslate"); // 성문 길
            if (isRift(rifts, LAKE, R, dx, dz, r)) return p("minecraft:light_blue_concrete");
            double h = hash(a, dx, dz);
            double pa = snowPatch(dx, dz, 1.7);
            double pb = snowPatch(dx, dz, 5.3);
            if (pa > 1.5) return p(h < 0.6 ? "minecraft:calcite" : "minecraft:snow");
            if (pb > 1.7) return p(h < 0.5 ? "minecraft:diorite" : "minecraft:polished_diorite");
            return p("minecraft:snow");
        };
        // 호수 밑바닥 (색유리 밑): 푸른 얼음, 두 고리에 바다 랜턴 (유리 너머로 빛남)
        Painter lakeBottom = (dx, dz, r, deg) -> {
            double ang = (StrictMath.atan2(dz, dx) * 180) / Math.PI;
            return onRing(ang, r, 2.8, 4, 45) || onRing(ang, r, 5, 6, 0) ? p("minecraft:sea_lantern") : p("minecraft:blue_ice");
        };
        Paint packed = p("minecraft:packed_ice");
        Paint blue = p("minecraft:blue_ice");
        List<Layer> base = List.of(
                new Layer(-3, (dx, dz, r, deg) -> r <= EDGE ? (r <= LAKE ? packed : p("minecraft:stone")) : null),
                new Layer(-2, (dx, dz, r, deg) -> r <= EDGE ? (r <= LAKE ? lakeBottom.paint(dx, dz, r, deg) : p("minecraft:dirt")) : null));
        List<Layer> walls = List.of(
                new Layer(0, (dx, dz, r, deg) -> {
                    if (ring.cell.test(dx, dz)) return packed;
                    // 화덕 둘레 담장 (모닥불 가운데 칸 둘레 8칸) · 가운데 윤나는 흑암 (위에 모닥불)
                    Hearth hf = hearthOf(fires, dx, dz);
                    if (hf == null || Math.max(Math.abs(hf.ddx), Math.abs(hf.ddz)) > 1) return null;
                    return p(hf.ddx == 0 && hf.ddz == 0 ? "minecraft:polished_blackstone" : "minecraft:polished_blackstone_brick_wall");
                }),
                new Layer(1, (dx, dz, r, deg) -> ring.cell.test(dx, dz) ? (hash(a, dx, dz, 9) < 0.25 ? blue : packed) : null));
        List<Stage> stages = List.of(
                new Stage("땅 고르기", clearLayers(H, EDGE), List.of(), "dig.snow"),
                new Stage("기초", base, List.of(), "dig.gravel"),
                new Stage("바닥", List.of(new Layer(-1, floor)), List.of(), "dig.snow"),
                new Stage("얼음 벽·첨탑·모닥불", walls, struct, "random.glass"),
                new Stage("장식", List.of(), deco, "fire.ignite"));
        Feat f = new Feat(List.of(), List.of(), gates, posts, List.of(), List.of(), null, List.of(), fires, spires, List.of(), List.of(), List.of());
        return new Plan(stages, f);
    }

    private static boolean onRing(double ang, double r, double rr, int n, double off) {
        if (!(Math.abs(r - rr) <= 0.5)) return false;
        for (int i = 0; i < n; i++) {
            if (angDist((ang * Math.PI) / 180, ((i * 360) / (double) n + off) * (Math.PI / 180)) * r <= 0.6) return true;
        }
        return false;
    }

    private static boolean isRift(List<Rift> rifts, double LAKE, int R, int dx, int dz, double r) {
        if (r < LAKE + 2.5 || r > R - 2.5) return false;
        double ang = StrictMath.atan2(dz, dx);
        // 구불구불: 반지름에 따라 각도가 조금씩 흔들림
        for (Rift f : rifts) {
            if (angDist(ang, f.a + (StrictMath.sin(r * 0.45 + f.ph) * f.w) / r) * r <= 0.55) return true;
        }
        return false;
    }

    // ------------------------------------------------------------ 수르트: 무스펠헤임의 신전

    /** 무스펠헤임의 신전 치수 (가운데 화구 반지름 pit · 사슬 기둥까지 chains · 얼음 결정까지 crystals) */
    public record Muspel(double pit, double chains, double crystals) {}

    /** 무스펠헤임의 신전 치수 (v2.12.0 수르트, 설정 BOSSES.<키>.arena) */
    public static Muspel muspelSize(String key) {
        Obj ar = arenaOf(key);
        return new Muspel(numOr(ar, "pit", 7), numOr(ar, "chains", 16), numOr(ar, "crystals", 26));
    }

    public static Muspel muspelSize() {
        return muspelSize("surtr");
    }

    /** 봉인의 사슬 기둥 넷의 각도 (도, 대각선 — +x 에서 +z 쪽으로) */
    public static final double[] CHAIN_DEG = {45, 135, 225, 315};
    /** 니플헤임의 얼음 결정 여덟의 각도 (성문 길과 사슬 기둥 사이) */
    public static final double[] CRYSTAL_DEG = {22.5, 67.5, 112.5, 157.5, 202.5, 247.5, 292.5, 337.5};
    /** 불꽃 탑 넷 (담장 위 대각선, 필드 밖으로만) */
    public static final double[] TOWER_DEG = {45, 135, 225, 315};
    /** 사슬 기둥·얼음 결정의 가운데 3×3 반폭 (칸): 라그나로크 그림자 폭·칼 붙잡기의 기준 */
    public static final double CORE_HALF = 1.5;
    /** 사슬 기둥 높이 (덮개까지, 칸): 칼 줄이 이 높이 아래로 지나가는 자리부터 붙잡힘 */
    public static final int CHAIN_HEIGHT = 16;
    /** 얼음 결정 높이 (칸) */
    public static final int CRYSTAL_HEIGHT = 9;

    /**
     * 봉인의 사슬 기둥 하나 (기둥 가운데 칸 기준 ox, dy, oz): 밑 3×3 우는 흑요석, 3×3 기둥 (윤나는 흑암 벽돌, 4칸마다 깎은 흑암 띠) dy 1~14,
     * 꼭대기 dy 15 덮개 5×5 (모서리 금 박힌 흑암), 덮개 네 모서리 밑으로 쇠사슬 여섯 칸 + 사슬 끝에 매단 랜턴
     */
    public static List<PieceCell> chainPillarCells() {
        List<PieceCell> out = new ArrayList<>();
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                out.add(new PieceCell(ox, 0, oz, "minecraft:crying_obsidian", null));
                for (int dy = 1; dy <= 14; dy++) out.add(new PieceCell(ox, dy, oz, dy % 4 == 2 ? "minecraft:chiseled_polished_blackstone" : "minecraft:polished_blackstone_bricks", null));
            }
        }
        for (int ox = -2; ox <= 2; ox++) {
            for (int oz = -2; oz <= 2; oz++) {
                out.add(new PieceCell(ox, CHAIN_HEIGHT - 1, oz, Math.abs(ox) == 2 && Math.abs(oz) == 2 ? "minecraft:gilded_blackstone" : "minecraft:polished_blackstone", null));
            }
        }
        for (int[] c : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            for (int dy = 9; dy <= 14; dy++) out.add(new PieceCell(c[0], dy, c[1], "minecraft:iron_chain", st("pillar_axis", "y")));
            out.add(new PieceCell(c[0], 8, c[1], "minecraft:lantern", st("hanging", true)));
        }
        return out;
    }

    /**
     * 니플헤임의 얼음 결정 하나 (결정 가운데 칸 기준): 3×3 결정 — 가운데 기둥 9칸 (꼭대기 두 칸 단단한 얼음), 옆 네 칸 6칸 (3칸마다 단단한 얼음 줄),
     * 모서리 네 칸 3칸 (푸른 얼음), 바깥 네 방향 작은 조각 2칸 (단단한 얼음)
     */
    public static List<PieceCell> crystalCells() {
        List<PieceCell> out = new ArrayList<>();
        for (int ox = -1; ox <= 1; ox++) {
            for (int oz = -1; oz <= 1; oz++) {
                int edge = Math.abs(ox) + Math.abs(oz);
                int top = edge == 0 ? CRYSTAL_HEIGHT : edge == 1 ? 6 : 3;
                for (int dy = 0; dy < top; dy++) {
                    boolean packed = (edge == 0 && dy >= top - 2) || (edge == 1 && dy % 3 == 1);
                    out.add(new PieceCell(ox, dy, oz, packed ? "minecraft:packed_ice" : "minecraft:blue_ice", null));
                }
            }
        }
        for (int[] c : new int[][] {{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
            for (int dy = 0; dy < 2; dy++) out.add(new PieceCell(c[0], dy, c[1], "minecraft:packed_ice", null));
        }
        return out;
    }

    /** 사슬 기둥 밑 5×5 칸 안 자리 (기둥 가운데에서 ox · oz) */
    private record ChainCell(int ox, int oz) {}

    /** 용암 줄기 (각 a · 위상 ph) */
    private record Vein(double a, double ph) {}

    /**
     * 수르트 (v2.12.0): 무스펠헤임의 신전 (반지름 44). 흑암·현무암 바닥, 가운데 무스펠의 화구 (반지름 pit: 주황 색유리 밑에 버섯불 — 녹은 용암처럼 빛남,
     * 붉은 색유리 나선 금), 화구 둘레 흑암 벽돌 테 + 금 박힌 흑암 룬 여덟, 다섯 칸 폭 성문 길 넷 (흑암 벽돌, 가운데 줄 깎은 흑암 룬),
     * 사슬 기둥 둘레의 붉은 네더 벽돌 고리 (반지름 chains), 용암 줄기 여덟 (주황 색유리 밑 버섯불, 구불구불), 가장자리 흑암 벽돌,
     * 높이 4 흑암 벽돌 담장 + 흑암 담장 성가퀴, 성문 넷 (현무암 기둥 + 네더 벽돌 들보·우는 흑요석 쐐기돌 + 매단 랜턴), 대각선 불꽃 탑 넷 (필드 밖으로만, 꼭대기 모닥불),
     * 봉인의 사슬 기둥 넷 (반지름 chains, 대각선) · 니플헤임의 얼음 결정 여덟 (반지름 crystals, 둘레 바닥에 서리). 장식은 담장 쪽 띠에만 (위더 해골·금 박힌 흑암·현무암)
     */
    private static Plan muspel(Ctx a) {
        int R = a.R;
        int H = a.H;
        DoubleSupplier rand = seeded(a);
        int EDGE = R + 2;
        Muspel size = muspelSize(a.key);
        double PIT = size.pit;
        double CH = size.chains;
        double CR = size.crystals;
        Gap onGate = (dx, dz) -> Math.abs(dx) <= 2 || Math.abs(dz) <= 2;
        List<Spot> gates = polars(R + 1, GATE_DEG);
        Ring ring = ringOf(R, onGate);
        List<Spot> chainList = polars(CH, CHAIN_DEG);
        List<Spot> crystalList = polars(CR, CRYSTAL_DEG);
        // 용암 줄기 여덟: 사슬 기둥 양옆 (33.75°·56.25° + 90°k), 화구 테 밖에서 담장 3칸 안까지, 반지름에 따라 조금씩 구불구불
        List<Vein> veins = new ArrayList<>();
        for (double d : new double[] {33.75, 56.25, 123.75, 146.25, 213.75, 236.25, 303.75, 326.25}) veins.add(new Vein((d * Math.PI) / 180, rand.getAsDouble() * 6));
        Painter floor = (dx, dz, r, deg) -> {
            if (r > EDGE) return null;
            if (r > R) return hash(a, dx, dz, 3) < 0.3 ? p("minecraft:basalt", AXIS_Y) : p("minecraft:blackstone"); // 둑
            if (r > R - 1) return p("minecraft:polished_blackstone_bricks"); // 가장자리
            ChainCell ch = chainOf(chainList, dx, dz);
            if (ch != null) return p(Math.abs(ch.ox) == 2 && Math.abs(ch.oz) == 2 ? "minecraft:gilded_blackstone" : "minecraft:chiseled_polished_blackstone");
            Double cr = crystalOf(crystalList, dx, dz);
            if (cr != null) return p(cr <= 1.6 ? "minecraft:blue_ice" : hash(a, dx, dz, 5) < 0.5 ? "minecraft:packed_ice" : "minecraft:snow");
            if (r <= PIT) return p(pitCrack(dx, dz, r) ? "minecraft:red_stained_glass" : "minecraft:orange_stained_glass");
            if (r <= PIT + 1.5) {
                boolean rune = false;
                for (int d = 0; d < 360; d += 45) {
                    if (angDist(StrictMath.atan2(dz, dx), (d * Math.PI) / 180) * r <= 0.6) {
                        rune = true;
                        break;
                    }
                }
                return p(rune ? "minecraft:gilded_blackstone" : "minecraft:polished_blackstone_bricks");
            }
            if (onGate.test(dx, dz)) return p((dx == 0 || dz == 0) && round(r) % 4 == 0 ? "minecraft:chiseled_polished_blackstone" : "minecraft:polished_blackstone_bricks"); // 성문 길
            if (Math.abs(r - CH) <= 0.6) return p("minecraft:red_nether_brick");
            if (isVein(veins, PIT, R, dx, dz, r)) return p("minecraft:orange_stained_glass");
            double h = hash(a, dx, dz);
            if (muspelPatch(dx, dz, 1.7) > 1.5) return h < 0.5 ? p("minecraft:basalt", AXIS_Y) : p("minecraft:smooth_basalt");
            if (muspelPatch(dx, dz, 5.3) > 1.6) return p(h < 0.6 ? "minecraft:cracked_polished_blackstone_bricks" : "minecraft:polished_blackstone");
            return p("minecraft:blackstone");
        };
        List<Put> struct = new ArrayList<>();
        // 성문: 양쪽 현무암 기둥 (dy 0~9) + 머리 (흑암 벽돌) + 들보 (네더 벽돌, 가운데 우는 흑요석) + 매단 랜턴 둘
        List<Flank> flanks = flanks(R, ring.cells, 3);
        for (Flank f : flanks) {
            for (int dy = 0; dy <= 9; dy++) struct.add(put(f.dx, dy, f.dz, "minecraft:basalt", st("pillar_axis", "y")));
            struct.add(put(f.dx, 10, f.dz, "minecraft:polished_blackstone_bricks"));
        }
        for (int gi = 0; gi < 4; gi++) {
            Beam b = beam(flanks, gi);
            int mid = round((b.lo + b.hi) / 2.0);
            for (int k = b.lo + 1; k < b.hi; k++) struct.add(put(b.dx(k), 10, b.dz(k), k == mid ? "minecraft:crying_obsidian" : "minecraft:nether_brick"));
            for (int s : new int[] {-1, 1}) struct.add(put(b.dx(mid + s), 9, b.dz(mid + s), "minecraft:lantern", st("hanging", true)));
        }
        // 불꽃 탑 넷: 담장 고리 칸에서 밖으로만 (현무암, 꼭대기 흑암 + 가운데 모닥불)
        List<SpireAt> towers = new ArrayList<>();
        for (double deg : TOWER_DEG) {
            Spot c = nearestCell(ring.cells, polar(R + 1, deg));
            int h = 14;
            towers.add(new SpireAt(c.dx, c.dz, h));
            for (int ox = -2; ox <= 2; ox++) {
                for (int oz = -2; oz <= 2; oz++) {
                    int x = c.dx + ox;
                    int z = c.dz + oz;
                    double r = hypot(x, z);
                    if (hypot(ox, oz) > 1.5 || r <= R + 0.5 || r > EDGE) continue;
                    for (int dy = 0; dy < h; dy++) {
                        struct.add(put(x, dy, z, dy == h - 1 ? "minecraft:polished_blackstone" : dy % 5 == 4 ? "minecraft:polished_basalt" : "minecraft:basalt",
                                dy == h - 1 ? null : st("pillar_axis", "y")));
                    }
                }
            }
            struct.add(put(c.dx, h, c.dz, "minecraft:campfire", st("extinguished", false, "minecraft:cardinal_direction", "south")));
        }
        // 사슬 기둥 넷 · 얼음 결정 여덟 (블록 목록은 기능 정보에도: 싸움 중 부서지고 녹으면 끝날 때 되돌림)
        List<ColumnAt> chains = new ArrayList<>();
        for (Spot c : chainList) chains.add(new ColumnAt(c.dx, c.dz, CORE_HALF, CHAIN_HEIGHT, toPuts(c, chainPillarCells()), List.of()));
        // 결정 둘레 서리 바닥 (녹으면 그을린 현무암, 되돌릴 때 이 블록으로)
        List<ColumnAt> crystals = new ArrayList<>();
        for (Spot c : crystalList) {
            List<Put> frost = new ArrayList<>();
            for (int ox = -4; ox <= 4; ox++) {
                for (int oz = -4; oz <= 4; oz++) {
                    Paint q = floor.paint(c.dx + ox, c.dz + oz, hypot(c.dx + ox, c.dz + oz), DEG(c.dx + ox, c.dz + oz));
                    if (hypot(ox, oz) <= 3.4 && q != null) frost.add(put(c.dx + ox, -1, c.dz + oz, q.block, q.states));
                }
            }
            crystals.add(new ColumnAt(c.dx, c.dz, CORE_HALF, CRYSTAL_HEIGHT, toPuts(c, crystalCells()), frost));
        }
        List<Put> pieces = new ArrayList<>();
        for (ColumnAt c : chains) pieces.addAll(c.cells);
        for (ColumnAt c : crystals) pieces.addAll(c.cells);
        List<Spot> posts = new ArrayList<>(spotsOf(flanks));
        for (SpireAt t : towers) posts.add(new Spot(t.dx, t.dz));
        // 장식: 담장 쪽 띠 (반지름 R-1.6 ~ R, 성문 길·기둥·탑 둘레 제외) — 위더 해골, 금 박힌 흑암, 현무암 기둥 (1~2칸)
        List<Put> deco = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        for (int n = 0, tries = 0; n < 30 && tries < 1000; tries++) {
            double deg = rand.getAsDouble() * 360;
            Spot q = polar(R - rand.getAsDouble() * 1.2, deg);
            double r = hypot(q.dx, q.dz);
            long k = cellKey(q.dx, q.dz);
            if (taken.contains(k) || r > R || r < R - 1.6 || Math.abs(q.dx) <= 3 || Math.abs(q.dz) <= 3 || nearPost(posts, q.dx, q.dz)) continue;
            taken.add(k);
            double pick = rand.getAsDouble();
            if (pick < 0.35) deco.add(put(q.dx, 0, q.dz, "minecraft:wither_skeleton_skull", st("facing_direction", 1)));
            else if (pick < 0.65) deco.add(put(q.dx, 0, q.dz, "minecraft:gilded_blackstone"));
            else {
                int tall = rand.getAsDouble() < 0.5 ? 2 : 1;
                for (int dy = 0; dy < tall; dy++) deco.add(put(q.dx, dy, q.dz, "minecraft:basalt", st("pillar_axis", "y")));
            }
            n++;
        }
        Paint shroom = p("minecraft:shroomlight");
        Paint blackstone = p("minecraft:blackstone");
        List<Layer> base = List.of(
                new Layer(-3, (dx, dz, r, deg) -> r <= EDGE ? (r <= PIT ? p("minecraft:magma") : p("minecraft:stone")) : null),
                // 바닥 밑: 빛나는 유리 (화구·용암 줄기) 밑은 버섯불, 그 밖은 흑암
                new Layer(-2, (dx, dz, r, deg) -> {
                    if (r > EDGE) return null;
                    boolean glows = r <= R - 1 && chainOf(chainList, dx, dz) == null && crystalOf(crystalList, dx, dz) == null
                            && (r <= PIT || (!onGate.test(dx, dz) && Math.abs(r - CH) > 0.6 && r > PIT + 1.5 && isVein(veins, PIT, R, dx, dz, r)));
                    return glows ? shroom : blackstone;
                }));
        List<Layer> walls = new ArrayList<>();
        for (int dy = 0; dy <= 3; dy++) {
            int wdy = dy;
            walls.add(new Layer(dy, (dx, dz, r, deg) -> ring.cell.test(dx, dz)
                    ? p(hash(a, dx, dz, 9 + wdy) < 0.2 ? "minecraft:cracked_polished_blackstone_bricks" : "minecraft:polished_blackstone_bricks") : null));
        }
        Paint battlement = p("minecraft:blackstone_wall");
        walls.add(new Layer(4, (dx, dz, r, deg) -> ring.cell.test(dx, dz) ? battlement : null));
        List<Stage> stages = List.of(
                new Stage("땅 고르기", clearLayers(H, EDGE), List.of(), "dig.stone"),
                new Stage("기초", base, List.of(), "dig.gravel"),
                new Stage("바닥", List.of(new Layer(-1, floor)), List.of(), "dig.basalt"),
                new Stage("담장·성문·불꽃 탑", walls, struct, "fire.ignite"),
                new Stage("사슬 기둥·얼음 결정", List.of(), pieces, "random.glass"),
                new Stage("장식", List.of(), deco, "mob.blaze.shoot"));
        Feat f = new Feat(List.of(), List.of(), gates, posts, List.of(), List.of(), null, List.of(), List.of(), List.of(), chains, crystals, towers);
        return new Plan(stages, f);
    }

    private static ChainCell chainOf(List<Spot> chainList, int dx, int dz) {
        for (Spot c : chainList) if (Math.abs(dx - c.dx) <= 2 && Math.abs(dz - c.dz) <= 2) return new ChainCell(dx - c.dx, dz - c.dz);
        return null;
    }

    /** 얼음 결정 둘레 서리 (가운데에서 3.4칸 안) → 그 결정까지 거리 (아니면 null) */
    private static Double crystalOf(List<Spot> crystalList, int dx, int dz) {
        for (Spot c : crystalList) {
            double d = hypot(dx - c.dx, dz - c.dz);
            if (d <= 3.4) return d;
        }
        return null;
    }

    private static boolean isVein(List<Vein> veins, double PIT, int R, int dx, int dz, double r) {
        if (r < PIT + 1.6 || r > R - 3) return false;
        double ang = StrictMath.atan2(dz, dx);
        for (Vein v : veins) {
            if (angDist(ang, v.a + (StrictMath.sin(r * 0.4 + v.ph) * 0.8) / r) * r <= 0.55) return true;
        }
        return false;
    }

    private static boolean pitCrack(int dx, int dz, double r) {
        if (!(r >= 1.2)) return false;
        for (int k = 0; k < 3; k++) {
            if (angDist(StrictMath.atan2(dz, dx), r * 0.35 + (k * 2 * Math.PI) / 3) * r <= 0.5) return true;
        }
        return false;
    }

    private static double muspelPatch(double dx, double dz, double k) {
        return StrictMath.sin(dx * 0.31 + k) + StrictMath.sin(dz * 0.37 + k * 2.3) + StrictMath.sin((dx + dz) * 0.21 + k * 0.9);
    }

    private static List<Put> toPuts(Spot c, List<PieceCell> cells) {
        List<Put> out = new ArrayList<>(cells.size());
        for (PieceCell q : cells) out.add(put(c.dx + q.ox, q.dy, c.dz + q.oz, q.block, q.states));
        return out;
    }

    // ------------------------------------------------------------ 공허 기사: 공허의 결투장 (0.33.0)

    /** 공허의 결투장 바닥을 가르는 균열 수 */
    public static final int VOID_CRACKS = 6;

    /**
     * 0.33.0 공허 기사: 공허의 결투장. 검은 바닥 (심층암 타일 · 윤나는 흑암 — 가운데에서 바깥으로 동심원 띠, 띠 사이 윤나는 심층암 줄),
     * 바닥을 가르는 공허 균열 여섯 줄 (가운데 결투 원에서 바깥으로 구불구불 — 가운데는 우는 흑요석 (보랏빛), 가장자리는 흑요석),
     * 가운데 결투 원 (깎은 흑암 · 흑암 벽돌 십자 · 자수정 테 — 가운데가 제단), 성문 네 길 (흑암 벽돌), 가장자리 흑요석 둑 (우는 흑요석 점) + 흑암 벽돌 담장 (높이 1),
     * 성문 4 (심층암 벽돌 기둥 + 들보 (가운데 우는 흑요석) + 매단 영혼 랜턴), 담장 위 부서진 오벨리스크 8개 (흑요석 · 우는 흑요석 마디, 높이 4 ~ 7, 꼭대기 자수정 송이),
     * 장식은 담장 쪽 띠에만 (자수정 송이 · 시든 해골 · 켜진 검은 양초 — 먼저 쓰러진 도전자들)
     */
    private static Plan voidHall(Ctx a) {
        int R = a.R;
        int H = a.H;
        DoubleSupplier rand = seeded(a);
        int EDGE = R + 2;
        List<Spot> gates = polars(R + 1, GATE_DEG);
        Gap onGate = (dx, dz) -> Math.abs(dx) <= 1 || Math.abs(dz) <= 1;
        Ring ring = ringOf(R, onGate);
        List<Flank> flanks = flanks(R, ring.cells, 2);
        List<Put> struct = new ArrayList<>();
        // 성문: 양쪽 기둥 (심층암 벽돌 3 + 깎은 심층암) + 들보 (가운데 우는 흑요석) + 매단 영혼 랜턴
        for (Flank f : flanks) {
            for (int dy = 0; dy <= 2; dy++) struct.add(put(f.dx, dy, f.dz, "minecraft:deepslate_bricks"));
            struct.add(put(f.dx, 3, f.dz, "minecraft:chiseled_deepslate"));
        }
        for (int gi = 0; gi < 4; gi++) {
            Beam b = beam(flanks, gi);
            int mid = round((b.lo + b.hi) / 2.0);
            for (int k = b.lo; k <= b.hi; k++) struct.add(put(b.dx(k), 4, b.dz(k), k == mid ? "minecraft:crying_obsidian" : "minecraft:polished_blackstone_bricks"));
            struct.add(put(b.dx(mid), 3, b.dz(mid), "minecraft:soul_lantern", st("hanging", true)));
        }
        // 부서진 오벨리스크 8개: 담장 고리 위 (성문 사이), 흑요석 · 우는 흑요석 마디, 높이 4 ~ 7, 꼭대기 자수정 송이
        List<SpireAt> obelisks = new ArrayList<>();
        for (double deg : STONE_DEG) {
            Spot q = nearestCell(ring.cells, polar(R + 1, deg));
            int h = 4 + (int) Math.floor(rand.getAsDouble() * 4);
            obelisks.add(new SpireAt(q.dx, q.dz, h));
            for (int dy = 0; dy < h; dy++) struct.add(put(q.dx, dy, q.dz, dy == h - 1 || dy % 3 == 1 ? "minecraft:crying_obsidian" : "minecraft:obsidian"));
            struct.add(put(q.dx, h, q.dz, "minecraft:amethyst_cluster"));
        }
        List<Spot> posts = new ArrayList<>(spotsOf(flanks));
        for (SpireAt o : obelisks) posts.add(new Spot(o.dx, o.dz));
        // 균열 여섯 줄: 가운데 결투 원 밖에서 바깥으로 (각도 · 굽이는 제단 자리마다 정해짐)
        double[] crackDeg = new double[VOID_CRACKS];
        double[] crackPh = new double[VOID_CRACKS];
        for (int i = 0; i < VOID_CRACKS; i++) {
            crackDeg[i] = i * (360.0 / VOID_CRACKS) + 12 + rand.getAsDouble() * 30;
            crackPh[i] = rand.getAsDouble() * 6.28;
        }
        Painter floor = (dx, dz, r, deg) -> {
            if (r > EDGE) return null;
            if (r > R) return p(hash(a, dx, dz, 3) < 0.14 ? "minecraft:crying_obsidian" : "minecraft:obsidian"); // 둑
            if (r > R - 1) return p("minecraft:polished_blackstone_bricks"); // 가장자리
            if (r <= 1.5) return p("minecraft:chiseled_polished_blackstone");
            if (r <= 3.5) return p(dx == 0 || dz == 0 ? "minecraft:polished_blackstone_bricks" : "minecraft:polished_blackstone");
            if (r <= 4.5) return p("minecraft:amethyst_block"); // 결투 원의 자수정 테
            for (int i = 0; i < VOID_CRACKS; i++) {
                double wob = StrictMath.sin(r * 0.55 + crackPh[i]) * 9 + StrictMath.sin(r * 1.7 + crackPh[i] * 2) * 3;
                double off = Math.abs(angDist((deg + 0) * Math.PI / 180, (crackDeg[i] + wob) * Math.PI / 180)) * r;
                double w = 0.45 + r * 0.025;
                if (off <= w) return p(off <= w * 0.55 || r < 9 ? "minecraft:crying_obsidian" : "minecraft:obsidian");
            }
            if (onGate.test(dx, dz)) return p(hash(a, dx, dz, 2) < 0.3 ? "minecraft:cracked_polished_blackstone_bricks" : "minecraft:polished_blackstone_bricks"); // 성문 길
            double band = (r - 4.5) / 3.6;
            if (band - Math.floor(band) < 0.22) return p("minecraft:polished_deepslate"); // 띠 사이 줄
            double h = hash(a, dx, dz);
            if (((int) Math.floor(band)) % 2 == 0) return p(h < 0.07 ? "minecraft:cracked_deepslate_tiles" : "minecraft:deepslate_tiles");
            return p(h < 0.07 ? "minecraft:cracked_polished_blackstone_bricks" : "minecraft:polished_blackstone");
        };
        // 장식: 담장 쪽 띠 (반지름 R-1.6 ~ R, 성문 길 · 기둥 · 오벨리스크 둘레 제외) — 자수정 송이 · 시든 해골 · 켜진 검은 양초
        List<Put> deco = new ArrayList<>();
        Set<Long> taken = new HashSet<>();
        for (int n = 0, tries = 0; n < 24 && tries < 800; tries++) {
            double deg = rand.getAsDouble() * 360;
            Spot q = polar(R - rand.getAsDouble() * 1.2, deg);
            double r = hypot(q.dx, q.dz);
            long k = cellKey(q.dx, q.dz);
            if (taken.contains(k) || r > R || r < R - 1.6 || Math.abs(q.dx) <= 2 || Math.abs(q.dz) <= 2 || nearPost(posts, q.dx, q.dz)) continue;
            // 담장 바로 옆 칸은 빼기 (담장이 장식 쪽으로 이어지지 않게)
            if (ring.cell.test(q.dx + 1, q.dz) || ring.cell.test(q.dx - 1, q.dz) || ring.cell.test(q.dx, q.dz + 1) || ring.cell.test(q.dx, q.dz - 1)) continue;
            taken.add(k);
            double pick = rand.getAsDouble();
            if (pick < 0.45) deco.add(put(q.dx, 0, q.dz, "minecraft:amethyst_cluster"));
            else if (pick < 0.65) deco.add(put(q.dx, 0, q.dz, "minecraft:wither_skeleton_skull", st("facing_direction", 1)));
            else deco.add(put(q.dx, 0, q.dz, "minecraft:black_candle", st("candles", (int) Math.floor(rand.getAsDouble() * 3), "lit", true)));
            n++;
        }
        Paint wall = p("minecraft:polished_blackstone_brick_wall");
        List<Stage> stages = List.of(
                new Stage("땅 고르기", clearLayers(H, EDGE), List.of(), "dig.stone"),
                new Stage("기초", List.of(fillLayer(-3, EDGE, p("minecraft:blackstone")), fillLayer(-2, EDGE, p("minecraft:deepslate"))), List.of(), "dig.deepslate"),
                new Stage("바닥", List.of(new Layer(-1, floor)), List.of(), "dig.deepslate"),
                new Stage("담장·오벨리스크", List.of(new Layer(0, (dx, dz, r, deg) -> ring.cell.test(dx, dz) ? wall : null)), struct, "break.amethyst_block"),
                new Stage("장식", List.of(), deco, "block.lantern.place"));
        Feat f = new Feat(List.of(), List.of(), gates, posts, List.of(), List.of(), null, List.of(), List.of(), obelisks, List.of(), List.of(), List.of());
        return new Plan(stages, f);
    }

    // ------------------------------------------------------------ 설계 고르기 · 작업 목록

    /** 테마 이름 (설정 BOSSES.<키>.arena.theme) → 설계 */
    private static Plan theme(String name, Ctx a) {
        return switch (name) {
            case "harvest" -> harvest(a);
            case "desert" -> desert(a);
            case "sunken" -> sunken(a);
            case "deep" -> deep(a);
            case "isle" -> isle(a);
            case "roots" -> roots(a);
            case "stones" -> stones(a);
            case "glacier" -> glacier(a);
            case "muspel" -> muspel(a);
            case "void" -> voidHall(a);
            default -> null;
        };
    }

    /** 제단 (보스 키 · 블록 자리) 의 필드 설계 (테마가 없으면 null) */
    public static Plan arenaPlan(String key, int x, int y, int z) {
        Obj ar = arenaOf(key);
        String t = ar == null ? null : ar.strOrNull("theme");
        if (t == null) return null;
        return theme(t, ctx(key, x, y, z));
    }

    /** 울타리 · 담장 이음 계산에서 가는 것 (이음을 받지 않음) */
    private static final Set<String> THIN = Set.of("minecraft:air", "minecraft:deadbush", "minecraft:skeleton_skull", "minecraft:lantern", "minecraft:soul_lantern",
            "minecraft:sea_pickle", "minecraft:water", "minecraft:pointed_dripstone", "minecraft:amethyst_cluster", "minecraft:moss_carpet", "minecraft:waterlily",
            "minecraft:pale_moss_carpet", "minecraft:hanging_roots", "minecraft:pale_hanging_moss", "minecraft:snow_layer");

    /** 이음 상태를 나중에 넣을 울타리 · 담장 작업 (단계 · 작업 번호 · 제단 기준 칸) */
    private record Joint(int stage, int index, boolean fence, int dx, int dy, int dz) {}

    private static Map<String, Object> statesOrEmpty(Map<String, Object> s) {
        return s == null ? Map.of() : s;
    }

    /**
     * 설계 → 놓을 작업 목록 (arena.js planOps). 층은 x 줄마다 같은 블록이 이어지는 구간을 fill 한 번으로 (제단 자리는 건너뜀).
     * 울타리 · 담장은 이웃 울타리 · 단단한 블록 쪽으로 이어지는 상태 (connection_* · wall_connection_type_* · wall_post_bit) 를 직접 넣음.
     * x · y · z = 제단 블록 자리, radius = 필드 반지름
     */
    public static List<StageOps> planOps(int x, int y, int z, int radius, Plan plan) {
        int span = radius + 2;
        Map<Long, String> grid = new HashMap<>();
        List<StageOps> out = new ArrayList<>();
        List<Joint> joints = new ArrayList<>();
        for (Stage st : plan.stages()) {
            List<Op> ops = new ArrayList<>();
            for (Layer L : st.layers()) {
                for (int dx = -span; dx <= span; dx++) {
                    String runBlock = null;
                    Map<String, Object> runStates = null;
                    int z0 = 0;
                    int z1 = 0;
                    for (int dz = -span; dz <= span; dz++) {
                        Paint q = L.paint().paint(dx, dz, hypot(dx, dz), DEG(dx, dz));
                        if (dx == 0 && dz == 0 && L.dy() == 0) q = null; // 제단
                        String name = q == null ? null : q.block();
                        Map<String, Object> states = q == null ? null : q.states();
                        if (name != null) grid.put(cellKey(dx, L.dy(), dz), name);
                        boolean isFence = name != null && name.endsWith("_fence");
                        boolean isWall = name != null && name.endsWith("_wall");
                        if (name == null || isFence || isWall) {
                            if (runBlock != null) ops.add(new Op(true, x + dx, y + L.dy(), z + z0, x + dx, y + L.dy(), z + z1, runBlock, runStates));
                            runBlock = null;
                            if (isFence || isWall) {
                                joints.add(new Joint(out.size(), ops.size(), isFence, dx, L.dy(), dz));
                                ops.add(new Op(false, x + dx, y + L.dy(), z + dz, x + dx, y + L.dy(), z + dz, name, null));
                            }
                            continue;
                        }
                        if (runBlock != null && runBlock.equals(name) && Objects.equals(statesOrEmpty(runStates), statesOrEmpty(states)) && z1 == dz - 1) {
                            z1 = dz;
                        } else {
                            if (runBlock != null) ops.add(new Op(true, x + dx, y + L.dy(), z + z0, x + dx, y + L.dy(), z + z1, runBlock, runStates));
                            runBlock = name;
                            runStates = states;
                            z0 = dz;
                            z1 = dz;
                        }
                    }
                    if (runBlock != null) ops.add(new Op(true, x + dx, y + L.dy(), z + z0, x + dx, y + L.dy(), z + z1, runBlock, runStates));
                }
            }
            for (Put q : st.puts()) {
                if (q.dx() == 0 && q.dz() == 0 && q.dy() == 0) continue;
                grid.put(cellKey(q.dx(), q.dy(), q.dz()), q.block());
                ops.add(new Op(false, x + q.dx(), y + q.dy(), z + q.dz(), x + q.dx(), y + q.dy(), z + q.dz(), q.block(), q.states()));
            }
            out.add(new StageOps(st.name(), st.sound(), ops));
        }
        // 울타리·담장 이음: 이웃이 울타리·담장이거나 단단한 블록이면 이음 (덤불·해골·랜턴 같은 작은 장식은 빼고)
        for (Joint j : joints) {
            boolean e = solid(grid, j.dx + 1, j.dy, j.dz);
            boolean w = solid(grid, j.dx - 1, j.dy, j.dz);
            boolean s = solid(grid, j.dx, j.dy, j.dz + 1);
            boolean n = solid(grid, j.dx, j.dy, j.dz - 1);
            Map<String, Object> states;
            if (j.fence) {
                states = st("minecraft:connection_east", e, "minecraft:connection_west", w, "minecraft:connection_south", s, "minecraft:connection_north", n);
            } else {
                // 담장: 이음은 낮게(short), 곧은 줄(동서만·남북만)이 아니면 가운데 기둥(wall_post_bit)
                boolean straight = (e && w && !n && !s) || (n && s && !e && !w);
                states = st("wall_connection_type_east", e ? "short" : "none", "wall_connection_type_west", w ? "short" : "none", "wall_connection_type_south",
                        s ? "short" : "none", "wall_connection_type_north", n ? "short" : "none", "wall_post_bit", !straight);
            }
            List<Op> ops = out.get(j.stage).ops();
            Op o = ops.get(j.index);
            ops.set(j.index, new Op(false, o.x(), o.y(), o.z(), o.x2(), o.y2(), o.z2(), o.block(), states));
        }
        return out;
    }

    private static boolean solid(Map<Long, String> grid, int dx, int dy, int dz) {
        String b = grid.get(cellKey(dx, dy, dz));
        return b != null && !THIN.contains(b) && !b.endsWith("_coral_fan") && !b.endsWith("candle");
    }
}
