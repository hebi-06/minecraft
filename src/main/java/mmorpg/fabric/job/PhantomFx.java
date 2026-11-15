package mmorpg.fabric.job;

import java.util.function.Predicate;
import mmorpg.core.Jobs;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.PBridge;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 2026-11-15 직업 전용 2D 이펙트 그림 (사용자 "직업마다 전용으로 그릴래" — 시범 팬텀 버스트 "헐 나은데? 이렇게 하자"): 미리 렌더링한 칠한 2D 프레임
 * (tools/jobvfx/&lt;직업&gt;.py — 글자 표시는 명암 계산이 없어 칠한 색 그대로 · 큰 그림은 2 × 2 조각 512 픽셀) · 날아가는 불씨 · 칼 조각 · 발광 외곽선 (리소스 팩
 * 후처리 post_effect/entity_outline — 자체 가우스 흐림 shaders/post/vfx_blur.fsh 로 부드러운 빛 번짐).
 * <p>
 * 그림 묶음 {@link Lib} = 그림판 이름 앞 (tools/jobvfx 의 LIB) · 발광 색: 팬텀 아처 (pa — 진홍 불) · 아스널 킹 (ak — 금빛 문 · 빛) · 블레이드 레인저 (br — 단조
 * 불빛 · 달아오른 베기). 다른 직업은 {@link #lib} 이 null (예전 길).
 */
final class PhantomFx {
    private PhantomFx() {}

    /** 칼 · 폭발의 발광 빛 (외곽선 → 빛 번짐 후처리) 색 */
    static final int GLOW_CRIMSON = 0xFF3A3A;
    static final int GLOW_CYAN = 0x7FD8FF;
    static final int GLOW_GOLD = 0xFFC23A;
    static final int GLOW_FORGE = 0xFF6A1A;

    /** 직업 전용 그림 묶음: 그림판 이름 앞 p (pa_boom …) · 칼 발광 색 glow · 투영 선 발광 색 trace · 날아가는 작은 그림 이름 ember (불씨 · 반짝임 · 불티) */
    record Lib(String p, int glow, int trace, String ember) {
        String n(String name) {
            return p + "_" + name;
        }
    }

    /** 팬텀 아처 (tools/jobvfx/phantom.py — 진홍 불 · 푸른 흰 투영 · 강철) */
    static final Lib PHANTOM = new Lib("pa", GLOW_CRIMSON, GLOW_CYAN, "ember");
    /** 2026-11-15 아스널 킹 (tools/jobvfx/arsenal.py — 금빛 문 · 빛 폭발 · 금가루 · 보석 반짝임) */
    static final Lib GOLD = new Lib("ak", GLOW_GOLD, GLOW_GOLD, "sparkle");
    /** 2026-11-15 블레이드 레인저 (tools/jobvfx/forge.py — 달아오른 베기 · 쇳물 폭발 · 불티 · 푸른 흰 투영) */
    static final Lib FORGE = new Lib("br", GLOW_FORGE, GLOW_CYAN, "spark");

    /** 2026-11-15 원소 공용 묶음 (tools/jobvfx/element.py — 사용자 "원소 공용 + 직업 대표") 의 발광 색 */
    static final int GLOW_STEEL = 0x8CC8FF;
    static final int GLOW_BLOOD = 0xE8283A;
    /** 강철 · 푸른 달빛 (검사 — el_steel: 칼빛 초승달 · 빛 폭발 · 한 바퀴 베기 · 땅 가르기 · 일섬 · 반짝임) */
    static final Lib STEEL = new Lib("el_steel", GLOW_STEEL, GLOW_STEEL, "ember");
    /** 피 (블러드 나이트 — el_blood: 핏빛 초승달 · 피 물보라 · 맞은 자리 피 튐 · 피 기둥 · 핏빛 달 · 핏방울) */
    static final Lib BLOOD = new Lib("el_blood", GLOW_BLOOD, GLOW_BLOOD, "ember");

    /** 이 직업의 그림 묶음 (그리지 않은 직업은 null — 예전 길) */
    static Lib lib(Jobs.Job j) {
        if (j == null) return null;
        return switch (j.id()) {
            case "archer" -> PHANTOM;
            case "arsenalking", "swordmaster" -> GOLD;
            case "bladeranger" -> FORGE;
            case "swordsman" -> STEEL;
            case "bloodknight" -> BLOOD;
            default -> null;
        };
    }

    /** 원소 공용 묶음으로 그리는 직업 (JobFx 공통 함수 · JobImpact 가 이 묶음으로 갈라짐 — 팬텀 아처 줄기는 PhantomArcher 길이라 뺌) */
    static Lib elem(Jobs.Job j) {
        return j == null || PhantomArcher.family(j) ? null : lib(j);
    }

    /** 이 묶음에 그림판이 있는지 (조각 그림은 이름_00) */
    static boolean has(Lib L, String name) {
        return !Vfx.sheetNames(L.n(name)).isEmpty();
    }

    // ------------------------------------------------------------ 보는 사람 (JobFx 의 view · owner 값)

    /** JobFx 보는 사람 값 (view 1 = 주인이 1인칭이면 숨김 · 2 = 주인의 1인칭에만, owner = 주인 id) → 걸러내기 (null = 모두) */
    static Predicate<ServerPlayer> who(Opt o) {
        int v = o == null ? 0 : (int) o.num("view", 0);
        if (v == 0) return null;
        int owner = (int) o.num("owner", 0);
        return v == 1 ? p -> !(p.getId() == owner && !PBridge.thirdPersonOf(p)) : p -> p.getId() == owner && !PBridge.thirdPersonOf(p);
    }

    /** 주인이 1인칭이면 숨김 (몸 앞 판 · 몸 둘레 그림) */
    static Predicate<ServerPlayer> hide1p(ServerPlayer sp) {
        int id = sp.getId();
        return p -> !(p.getId() == id && !PBridge.thirdPersonOf(p));
    }

    /** 주인의 1인칭에만 (눈앞에 따로 그리는 그림) */
    static Predicate<ServerPlayer> only1p(ServerPlayer sp) {
        int id = sp.getId();
        return p -> p.getId() == id && !PBridge.thirdPersonOf(p);
    }

    /**
     * 2026-11-15 눈 바로 앞 그림 거름 (1인칭 영상 2 — 블러드 문 · 새비지 프렌지 · 엑스칼리버): 그림 한 장은 256 픽셀이라 눈에서 가까우면 한 픽셀이 화면에서 수십 픽셀로
     * 늘어나 붉은 · 금빛 네모 덩어리로 화면을 덮었음 → 1인칭으로 보는 사람의 눈이 at 에서 r 칸 안이면 보내지 않음 (3인칭은 카메라가 몸 뒤라 그대로 — 쓴 사람의 눈앞에
     * 보여야 할 그림은 스킬이 1인칭 자리에 따로 둠)
     */
    static Predicate<ServerPlayer> farEye(Predicate<ServerPlayer> who, Vec3 at, double r) {
        if (r <= 0) return who;
        double r2 = r * r;
        Predicate<ServerPlayer> far = p -> PBridge.thirdPersonOf(p) || p.getEyePosition().distanceToSqr(at) >= r2;
        return who == null ? far : who.and(far);
    }

    /**
     * 눈앞 거름 반지름 — 한 장 그림 (256 픽셀) 크기 size 의 몇 배 (1080p · 시야 70도에서 한 픽셀 ≈ 3.45 × size / 거리 픽셀): 날이 선 피 그림 (물보라 가시 · 방울) 은
     * 약 9 픽셀 넘게 늘어나면 (0.38 — 새비지 프렌지 2.5칸 앞 맞은 자리 피 튐 6 픽셀은 그대로) · 부드러운 빛 (강철 · 금 · 불 섬광) 은 아주 가까울 때만 (0.25 — 엑스칼리버
     * 발 앞 큰 섬광은 그대로). 2 × 2 그림은 조각이 반 크기라 이 값의 반
     */
    static double nearR(Lib L, double size) {
        return size * (L == BLOOD ? 0.38 : 0.25);
    }

    /** 땅에 눕힌 그림 (그림 앞면이 위 — 그림 위쪽이 yaw 쪽) */
    static Quaternionf flat(double yawDeg) {
        return Vfx.flat(yawDeg);
    }

    // ------------------------------------------------------------ 바탕

    /**
     * 큰 그림 하나 (조각 k × k — 그림판 base_rc): 가운데 at · 한 변 size 칸 · facing (null 이면 카메라를 봄 · 아니면 그 방향에 고정) · 화면 돌림 roll 도 · from 틱에
     * 나타나 every 틱마다 칸을 넘기고 마지막 칸 뒤 사라짐
     */
    static Vfx.Fx tiled(ServerLevel level, String base, int k, Vec3 at, double size, Quaternionf facing, double roll, int from, int every) {
        return tiled(level, base, k, at, size, facing, roll, from, every, false);
    }

    /**
     * 위와 같고 twoSided 면 고정 그림 (facing) 의 뒷면도: 조각마다 거울 ({@link Vfx.Sprite#mirror}) 한 장 더 — 자리 (r, c) 에 조각 (r, k−1−c) 를 거울로 놓으면 그림 전체의
     * 거울이 됨, 미리 뒤집은 그림판 (base + "m") 이 있으면 그것을 써서 뒷면도 세계에서 같은 그림 (휘두르는 쪽이 칼과 같음)
     */
    static Vfx.Fx tiled(ServerLevel level, String base, int k, Vec3 at, double size, Quaternionf facing, double roll, int from, int every, boolean twoSided) {
        return tiled(level, base, k, at, size, facing, roll, from, every, twoSided, null, null, 1.0);
    }

    /**
     * 2026-11-15 같은 것 — 뒷면 그림판 back (null = base + "m" 이 있으면 그것 · 없으면 base: 앞면이 미리 뒤집은 slashm 이면 뒷면은 slash 라야 세계에서 같은 그림) ·
     * 볼 사람 who (null = 모두 — 붙이기 전에 정함) · 불투명 alpha
     */
    static Vfx.Fx tiled(ServerLevel level, String base, int k, Vec3 at, double size, Quaternionf facing, double roll, int from, int every, boolean twoSided,
            String back, Predicate<ServerPlayer> who, double alpha) {
        Vfx.Sheet s0 = Vfx.sheet(base + "_00");
        int frames = s0.frames();
        int life = from + frames * every + 4;
        Vfx.Fx fx = Vfx.at(level, at, life, who);
        double t = size / k;
        int sides = twoSided && facing != null ? 2 : 1;
        String backBase = back != null ? back : Vfx.sheetNames(base + "m_").isEmpty() ? base : base + "m";
        fx.on(Math.max(0, from), () -> {
            for (int r = 0; r < k; r++) {
                for (int c = 0; c < k; c++) {
                    for (int side = 0; side < sides; side++) {
                        Vfx.Sprite sp = fx.sprite(side == 0 ? base + "_" + r + c : backBase + "_" + r + (k - 1 - c), at);
                        if (facing != null) sp.fixed(facing);
                        if (side == 1) sp.mirror();
                        if (roll != 0) sp.roll(roll, 0);
                        if (alpha < 0.999) sp.alpha(alpha);
                        sp.size(t);
                        sp.offset((c - (k - 1) / 2.0) * t, ((k - 1) / 2.0 - r) * t);
                        sp.play(fx.age(), 0, frames, every, false);
                    }
                }
            }
        });
        return fx;
    }

    /** 그림 위쪽 ey · 앞면 ez 의 고정 방향 (ex = ey × ez) — ey 가 ez 와 나란하면 다른 위 */
    static Quaternionf face(Vec3 ez, Vec3 upHint) {
        Vec3 z = V.unit(ez);
        Vec3 u = V.sub(upHint, V.scale(z, V.dot(upHint, z)));
        if (V.len(u) < 1e-3) {
            Vec3 alt = Math.abs(z.y) > 0.9 ? new Vec3(0, 0, -1) : V.UP;
            u = V.sub(alt, V.scale(z, V.dot(alt, z)));
        }
        Vec3 y = V.unit(u);
        Vec3 x = V.cross(y, z);
        return Vfx.basis(x, y, z);
    }

    /** 한 장 그림 (그림판 name — 칸이 여럿이면 every 틱마다 넘김): 가운데 at · 크기 size 칸 · loop 면 되풀이 */
    static Vfx.Sprite single(Vfx.Fx fx, String name, Vec3 at, double size, double roll, int every, boolean loop) {
        Vfx.Sheet s = Vfx.sheet(name);
        Vfx.Sprite sp = fx.sprite(name, at);
        sp.size(size);
        if (roll != 0) sp.roll(roll, 0);
        if (s.frames() > 1) sp.play(fx.age(), 0, s.frames(), every, loop);
        return sp;
    }

    /** 땅 자리 (발밑 블록 위 0.04칸) */
    static Vec3 ground(ServerLevel level, Vec3 p) {
        return new Vec3(p.x, RuneKit.groundAt(level, p, p.y) + 0.04, p.z);
    }

    // ------------------------------------------------------------ 빛 · 섬광

    /** 섬광 (pb_flash → 묶음 flash — 흰 별 모양 · 굵은 빛살 · 퍼지는 고리): 가운데 at · 크기 size 칸 */
    static void flash(ServerLevel level, Vec3 at, double size) {
        flash(level, PHANTOM, at, size);
    }

    static void flash(ServerLevel level, Lib L, Vec3 at, double size) {
        flash(level, L, at, size, null);
    }

    /** 모으는 빛 (되풀이 — 스킬이 to · remove 로 다룸): fx 안 · 가운데 at · 크기 size */
    static Vfx.Sprite charge(Vfx.Fx fx, Lib L, Vec3 at, double size) {
        return single(fx, L.n("charge"), at, size, 0, 1, true);
    }

    // ------------------------------------------------------------ 날아가는 작은 그림

    /**
     * 불씨 n 개 (묶음 ember — 둥근 흰 금빛 심 · 주황 · 진홍 겉빛, 네 칸 깜박임. 1판 길쭉한 불티는 카메라를 보는 그림이라 늘 세로로 서서 촛불처럼 보였음):
     * 가운데 c 에서 사방 (위로 쏠림) 으로 dist 칸 솟았다가 떨어지며 작아지고 옅어짐 (life 틱)
     */
    static void embers(ServerLevel level, Vec3 c, int n, double dist, double size, int life) {
        embers(level, PHANTOM, c, n, dist, size, life);
    }

    static void embers(ServerLevel level, Lib L, Vec3 c, int n, double dist, double size, int life) {
        fly(level, c, n, dist, size, life, L.n(L.ember()), 1.0);
    }

    /** 칼 조각 n 개 (묶음 shard — 납작한 강철 조각이 구르는 여덟 칸 · 깨진 가장자리가 달아오름): 불씨보다 낮고 멀리 · 빨리 떨어짐 */
    static void shards(ServerLevel level, Vec3 c, int n, double dist, double size, int life) {
        shards(level, PHANTOM, c, n, dist, size, life);
    }

    static void shards(ServerLevel level, Lib L, Vec3 c, int n, double dist, double size, int life) {
        fly(level, c, n, dist, size, life, L.n("shard"), 0.55);
    }

    /** 날아가는 작은 그림 (불씨 · 조각): 솟음 → 둥글게 넘어감 → 떨어짐 (세 토막 보간) · 칸은 되풀이 (불씨는 깜박임 · 조각은 구름) */
    private static void fly(ServerLevel level, Vec3 c, int n, double dist, double size, int life, String sheet, double lift) {
        if (n <= 0) return;
        int L = Math.max(8, life);
        Vfx.Fx fx = Vfx.at(level, c, L + 4);
        Vfx.Sheet sh = Vfx.sheet(sheet);
        for (int i = 0; i < n; i++) {
            double a = Kit.rand() * Math.PI * 2;
            double up = (0.2 + Kit.rand() * 0.9) * lift;
            Vec3 d = V.unit(new Vec3(Math.cos(a), up, Math.sin(a)));
            double dd = dist * (0.5 + Kit.rand() * 0.65);
            double sz = size * (0.6 + Kit.rand() * 0.7);
            Vec3 p1 = V.add(c, d, dd * 0.55);
            Vec3 p2 = V.up(V.add(c, V.flat(d), dd * 0.9), dd * 0.3 * up - 0.15);
            Vec3 p3 = V.up(V.add(c, V.flat(d), dd * 1.12), -dd * 0.4 - 0.4);
            Vfx.Sprite s = fx.sprite(sheet, c);
            s.size(sz * 0.4);
            if (sh.frames() > 1) s.play(0, (int) (Kit.rand() * sh.frames()), sh.frames(), 1 + (int) (Kit.rand() * 2), true);
            int t1 = 2 + (int) (Kit.rand() * 2);
            int t2 = Math.max(3, L / 3);
            fx.on(1, () -> s.to(p1, sz, t1));
            fx.on(1 + t1, () -> s.to(p2, sz * 0.8, t2));
            fx.on(1 + t1 + t2, () -> s.to(p3, sz * 0.35, Math.max(2, L - t1 - t2 - 1)));
            s.fade(L - 6, 6);
        }
    }

    // ------------------------------------------------------------ 맞음 · 폭발 · 땅

    /** 착탄 (예전 impact2d — BlockFx 상자 터짐 · 상자 조각 대신): 섬광 · 불씨 · 칼 조각 몇 · 크기 size 칸 */
    static void impact(ServerLevel level, Lib L, Vec3 at, double size) {
        flash(level, L, at, size * 2.2);
        embers(level, L, at, 5 + (int) (size * 6), size * 1.6, 0.32 + size * 0.12, 14);
        if (size >= 0.9) shards(level, L, at, 2 + (int) (size * 2), size * 1.4, 0.32, 14);
    }

    /**
     * 작은 폭발 (한 장 256 — boom_s · shock_s · crack_s: 꽂힌 칼 특성 · 블레이드 레인 칼마다): 가운데 c (몸 높이) · 크기 size 칸 (불덩이 지름) · 불씨 · 조각
     */
    static void boomSmall(ServerLevel level, Lib L, Vec3 c, double size) {
        Vec3 g = ground(level, c);
        Vfx.Fx fx = Vfx.at(level, c, 20);
        single(fx, L.n("boom_s"), V.up(c, size * 0.15), size, Kit.rand() * 30 - 15, 1, false);
        if (c.y - g.y < 2.5) {
            Vfx.Fx gx = Vfx.at(level, g, 18);
            Vfx.Sprite sh = single(gx, L.n("shock_s"), V.up(g, 0.02), size * 1.6, 0, 1, false);
            sh.fixed(flat(Kit.rand() * 360));
            Vfx.Fx cx = Vfx.at(level, g, 40);
            Vfx.Sprite cr = cx.sprite(L.n("crack_s"), V.up(g, 0.01));
            cr.fixed(flat(Kit.rand() * 360));
            cr.size(size * 0.9);
            cr.play(2, 0, Vfx.sheet(L.n("crack_s")).frames(), 6, false);
        }
        embers(level, L, c, 8 + (int) (size * 3), size * 0.9, 0.42, 18);
        shards(level, L, c, 3 + (int) size, size, 0.4, 16);
    }

    /**
     * 큰 폭발 (2 × 2 — boom · shock · crack: 팬텀 버스트 · 블레이드 레인 끝 · 소드 생추어리 끝): 가운데 c (몸 높이) · 반지름 rad 칸 — 불덩이 rad × 1.9 · 땅 충격파
     * rad × 3.2 · 그을린 땅 rad × 1.7 · 불씨 · 칼 조각
     */
    static void boom(ServerLevel level, Lib L, Vec3 c, double rad) {
        Vec3 gc = ground(level, c);
        tiled(level, L.n("boom"), 2, V.up(c, 0.9), rad * 1.9, null, Kit.rand() * 30 - 15, 0, 1);
        // 2026-11-15 영상 (팬텀 버스트 3인칭 · 1인칭): 지름 3.2 × rad 는 고리 앞 날이 카메라 밑까지 와서 화면 아래를 흰 띠로 가로질렀음 → 2.5 × rad
        tiled(level, L.n("shock"), 2, V.up(gc, 0.02), rad * 2.5, flat(Kit.rand() * 360), 0, 0, 1);
        if (has(L, "crack_00")) tiled(level, L.n("crack"), 2, gc, rad * 1.7, flat(Kit.rand() * 360), 0, 2, 7);
        else crackS(level, L, gc, rad * 1.7); // 원소 공용 묶음은 한 장 땅 자국만
        embers(level, L, V.up(c, 0.4), 26, rad * 1.1, 0.55, 26);
        shards(level, L, V.up(c, 0.3), 12, rad * 1.2, 0.5, 22);
    }

    /** 땅 충격파만 (2 × 2 — 속이 빈 고리가 퍼짐): 땅 g · 지름 size 칸 · every 틱마다 한 칸 */
    static void shock(ServerLevel level, Lib L, Vec3 g, double size, int every) {
        tiled(level, L.n("shock"), 2, V.up(g, 0.02), size, flat(Kit.rand() * 360), 0, 0, Math.max(1, every));
    }

    /** 그을린 땅 (2 × 2 — 달아오른 금이 식음): 땅 g · 지름 size 칸 · from 틱부터 every 틱마다 */
    static void crack(ServerLevel level, Lib L, Vec3 g, double size, int from, int every) {
        tiled(level, L.n("crack"), 2, V.up(g, 0.01), size, flat(Kit.rand() * 360), 0, from, Math.max(1, every));
    }

    /**
     * 땅 고리 (2 × 2 ring — 예고 · 테두리, 반짝임 되풀이): 가운데 c (땅) · 반지름 r 칸 — grow 틱에 작게서 커지고 hold 틱 머문 뒤 4틱에 옅어짐. 반환 = 효과
     */
    static Vfx.Fx ring(ServerLevel level, Lib L, Vec3 c, double r, int grow, int hold) {
        int G = Math.max(1, grow), Hh = Math.max(1, hold);
        Vfx.Fx fx = Vfx.at(level, c, G + Hh + 8);
        double size = r * 2.0 / 0.9; // 그림의 고리는 반지름 0.9 (테 가운데)
        double t = size / 2;
        Quaternionf q = flat(Kit.rand() * 360);
        String base = L.n("ring");
        int frames = Vfx.sheet(base + "_00").frames();
        for (int rr = 0; rr < 2; rr++) {
            for (int cc = 0; cc < 2; cc++) {
                Vfx.Sprite sp = fx.sprite(base + "_" + rr + cc, V.up(c, 0.03));
                sp.fixed(q);
                double ox = (cc - 0.5) * t;
                double oy = (0.5 - rr) * t;
                sp.size(t * 0.15);
                sp.offset(ox * 0.15, oy * 0.15);
                sp.play(0, 0, frames, 3, true);
                fx.on(1, () -> {
                    sp.offset(ox, oy);
                    sp.to(null, t, G);
                });
                sp.fade(G + Hh, 4);
            }
        }
        return fx;
    }

    /** 칼이 땅에 꽂힘 · 솟음 (dust — 흙먼지 · 밑의 진홍 번쩍): 땅 g · 크기 size 칸 (그림의 땅 줄이 아래 1/4 이라 그만큼 올림) */
    static void dust(ServerLevel level, Lib L, Vec3 g, double size) {
        Vfx.Fx fx = Vfx.at(level, g, 14);
        single(fx, L.n("dust"), V.up(g, size * 0.25), size, 0, 1, false);
    }

    /** X 베기 (2 × 2 xslash — 두 줄기가 차례로 그어지고 교차점 섬광): 가운데 c · 크기 size 칸 · 화면 돌림 roll 도 (카메라를 봄) */
    static void xslash(ServerLevel level, Lib L, Vec3 c, double size, double roll) {
        tiled(level, L.n("xslash"), 2, c, size, null, roll, 0, 1, false, null, farEye(null, c, nearR(L, size) * 0.5), 1.0);
    }

    // ------------------------------------------------------------ 2026-11-15 아스널 킹 · 블레이드 레인저

    /**
     * 금빛 문 (ak_gate — 녹은 금 판의 물결 고리 · 금 테 · 보석, 되풀이 8칸 · 2틱마다): 가운데 c · 보물이 나오는 쪽 facing (앞면 — 뒷면도 한 장이라 어느 쪽에서도 보임) ·
     * 테 반지름 r 칸. open 틱에 작게서 열리고 (가운데 섬광) hold 틱 머문 뒤 4틱에 오므라들며 옅어짐. 반환 = 효과
     */
    static Vfx.Fx gate(ServerLevel level, Lib L, Vec3 c, Vec3 facing, double r, int open, int hold) {
        Vec3 f = V.len(facing) < 1e-6 ? new Vec3(0, 0, 1) : V.unit(facing);
        Quaternionf q = face(f, V.UP);
        int G = Math.max(1, open);
        int Hh = Math.max(1, hold);
        Vfx.Fx fx = Vfx.at(level, c, G + Hh + 8);
        String name = L.n("gate");
        int frames = Vfx.sheet(name).frames();
        double side = r * 2.0 / 0.8; // 그림의 테는 반지름 0.8 (그림 한 변 = 2)
        int start = (int) (Kit.rand() * frames);
        for (int b = 0; b < 2; b++) {
            Vfx.Sprite sp = fx.sprite(name, c);
            sp.fixed(q);
            if (b == 1) sp.mirror(); // 뒷면 (문 그림은 거의 좌우 같음)
            sp.size(side * 0.12);
            sp.play(0, start, frames, 2, true);
            fx.on(1, () -> sp.to(null, side, G));
            fx.on(1 + G + Hh, () -> sp.to(null, side * 0.25, 4));
            sp.fade(1 + G + Hh, 4);
        }
        fx.on(1 + G / 2, () -> flash(level, L, V.add(c, f, 0.05), r * 1.5));
        return fx;
    }

    /**
     * 단조 베기 (br_slash 2 × 2 — 달아오른 초승달이 휘둘러지고 꼬리부터 식으며 거둬짐 · 불티): 휘두르는 가운데 c (몸) · 볼록한 쪽 bulge (칼이 지나는 앞) · 휘두르는 쪽
     * sweep (그림 왼쪽 → 오른쪽 = sweep 쪽) · 반지름 r 칸 — 휘두른 면에 고정 · 앞 · 뒤 두 장. 그림의 호는 가운데 (0, −0.4) · 반지름 0.9 (그림 한 변 = 2)
     */
    static void slash(ServerLevel level, Lib L, Vec3 c, Vec3 bulge, Vec3 sweep, double r) {
        Vec3 ey = V.unit(bulge);
        Vec3 ex = V.sub(sweep, V.scale(ey, V.dot(sweep, ey)));
        if (V.len(ex) < 1e-3) ex = V.rightOf(V.flat(ey));
        ex = V.unit(ex);
        Vec3 ez = V.cross(ex, ey);
        Quaternionf q = Vfx.basis(ex, ey, ez);
        double side = r * 2.0 / 0.9;
        tiled(level, L.n("slash"), 2, V.add(c, ey, r * 0.4 / 0.9), side, q, 0, 0, 1, true);
    }

    // ------------------------------------------------------------ 불꽃 (세로로 서는 그림)

    /**
     * 불꽃 혀 하나 (flame — 솟았다 사그라드는 10칸, 세로축 빌보드): 발 자리 foot · 높이 h 칸 · every 틱마다 한 칸 (loop 면 끝까지 되풀이 — 효과 life 틱)
     */
    static Vfx.Sprite flame(Vfx.Fx fx, Lib L, Vec3 foot, double h, int every, boolean loop, int startFrame) {
        String name = L.n("flame");
        Vfx.Sheet s = Vfx.sheet(name);
        Vfx.Sprite sp = fx.sprite(name, V.up(foot, h * 0.5));
        sp.vertical();
        sp.size(h);
        sp.frame(startFrame);
        sp.play(fx.age(), startFrame, s.frames(), Math.max(1, every), loop);
        return sp;
    }

    /** 불꽃 하나 따로 (한 번 솟았다 사그라듦): 발 자리 foot · 높이 h · every 틱마다 */
    static void flame(ServerLevel level, Lib L, Vec3 foot, double h, int every) {
        int frames = Vfx.sheet(L.n("flame")).frames();
        Vfx.Fx fx = Vfx.at(level, foot, frames * Math.max(1, every) + 4);
        flame(fx, L, foot, h, every, false, 0);
    }

    // ------------------------------------------------------------ 2026-11-15 원소 공용 묶음 — JobFx 공통 함수 · 검사 라인 스킬이 부름

    /**
     * 그림 속 칼빛 초승달이 그림 한 변에서 차지하는 너비 (tools/jobvfx/element.py — 호 반지름 0.9 · 그림 반 변 1): 칼빛 너비 w 칸이면 그림 한 변 = w / SLASH_FILL
     */
    static final double SLASH_FILL = 0.9;

    /** 섬광 — 볼 사람을 정함 (1인칭 눈 바로 앞이면 거름 — {@link #farEye}) */
    static void flash(ServerLevel level, Lib L, Vec3 at, double size, Predicate<ServerPlayer> who) {
        Vfx.Fx fx = Vfx.at(level, at, 12, farEye(who, at, nearR(L, size)));
        single(fx, L.n("flash"), at, size, Kit.rand() * 40 - 20, 1, false);
    }

    /**
     * 1인칭 눈앞 큰 베기 (주인의 1인칭에만 — 카메라를 봄): 가운데 at · 칼빛 너비 width 칸 · 기울기 tilt (JobFx.slash 와 같은 뜻 — 0 = 왼쪽에서 아래로 불룩하게 오른쪽으로 ·
     * 180 = 위로 불룩하게 오른쪽에서 왼쪽으로 · 95 = 위에서 아래로 내리침 · 275 = 아래에서 위로): 미리 뒤집은 그림판 (slashm — 위로 불룩 · 오른쪽 → 왼쪽) 을 180 − tilt 돌림
     */
    static void eyeSlash(ServerPlayer sp, Lib L, Vec3 at, double width, double tilt) {
        tiled(sp.level(), L.n("slashm"), 2, at, width / SLASH_FILL, null, 180 - tilt, 0, 1, false, null, only1p(sp), 1.0);
    }

    /**
     * 2026-11-15 카메라를 보는 칼빛 초승달 (JobFx.slash · slashCard — 1인칭 · 3인칭 · 다른 사람 모두): 가운데 at · 칼빛 너비 width 칸 · 기울기 tilt (eyeSlash 와 같은 뜻).
     * 첫 영상 (CI 43693784623) 에서 쓴 사람 뒤를 보게 고정한 판은 옆 · 앞에서 비스듬히 얇게 보였음 → 늘 보는 사람 쪽 (어느 쪽에서도 화면에서 같은 기울기)
     */
    static void slashBill(ServerLevel level, Lib L, Vec3 at, double width, double tilt, Predicate<ServerPlayer> who) {
        tiled(level, L.n("slashm"), 2, at, width / SLASH_FILL, null, 180 - tilt, 0, 1, false, null, who, 1.0);
    }

    /**
     * 맞은 자리 그림을 몸 앞으로 (때린 사람 쪽 — 몸 겉 너머): 몸 가운데의 카메라를 보는 그림은 몸에 가려 거의 안 보였음 (CI 43693784623 — X 베기 · 섬광). 때린 사람을 모르면 가장
     * 가까운 사람 쪽 · 둘 다 없으면 몸 가운데
     */
    static Vec3 front(LivingEntity e, Vec3 b) {
        LivingEntity by = e.getLastHurtByMob();
        Vec3 eye = by != null && by.distanceToSqr(e) < 400 ? by.getEyePosition() : null;
        if (eye == null && e.level() instanceof ServerLevel lv) {
            net.minecraft.world.entity.player.Player p = lv.getNearestPlayer(e, 16);
            if (p != null) eye = p.getEyePosition();
        }
        if (eye == null) return b;
        Vec3 d = V.sub(eye, b);
        double len = V.len(d);
        if (len < 0.6) return b;
        return V.add(b, V.scale(d, 1 / len), Math.min(len * 0.5, Kit.bodyPad(e) + 0.55));
    }

    /**
     * 발키리의 날개 (el_steel_wings 2 × 2 — 카메라를 봄 · 날갯짓 되풀이): 등 뒤 (몸 뒤 0.35 · 높이 1.3) 를 2틱마다 따라감 · 4틱에 커지며 나타나 ticks 틱 뒤 6틱에 옅어짐 ·
     * 주인 1인칭에서는 숨김. 그림판이 없으면 false (예전 길)
     */
    static boolean wings(ServerPlayer sp, Lib L, double size, int ticks, double alpha) {
        if (!has(L, "wings_")) return false;
        String base = L.n("wings");
        String life = RuneKit.lifeToken(sp);
        Vec3 at0 = wingAt(sp);
        int T = Math.max(8, ticks);
        Vfx.Fx fx = Vfx.at(sp.level(), at0, T + 10, hide1p(sp));
        int frames = Vfx.sheet(base + "_00").frames();
        double t = size * 2.4 / 2;
        java.util.List<Vfx.Sprite> tiles = new java.util.ArrayList<>();
        int start = (int) (Kit.rand() * frames);
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) {
                Vfx.Sprite w = fx.sprite(base + "_" + r + c, at0);
                double ox = (c - 0.5) * t;
                double oy = (0.5 - r) * t;
                w.size(t * 0.6);
                w.offset(ox * 0.6, oy * 0.6);
                if (alpha < 0.999) w.alpha(alpha);
                w.play(0, start, frames, 2, true);
                fx.on(1, () -> {
                    w.offset(ox, oy);
                    w.to(null, t, 4);
                });
                tiles.add(w);
            }
        }
        fx.each(2, T, 2, age -> {
            if (!RuneKit.sameLife(sp, life)) {
                fx.end();
                return;
            }
            Vec3 at = wingAt(sp);
            for (Vfx.Sprite w : tiles) w.to(at, -1, 2);
        });
        fx.on(T, () -> fx.fadeAll(6));
        return true;
    }

    private static Vec3 wingAt(ServerPlayer sp) {
        return V.up(V.add(sp.position(), Kit.facingOf(sp), -0.35), 1.3);
    }

    /** 화면 위쪽 축 (dir 에 수직 — dir 이 거의 위 · 아래면 북쪽) · 오른쪽 축 */
    private static Vec3[] screenAxes(Vec3 dir) {
        Vec3 d = V.len(dir) > 1e-6 ? V.unit(dir) : new Vec3(0, 0, 1);
        Vec3 up = V.sub(V.UP, V.scale(d, V.dot(V.UP, d)));
        up = V.len(up) < 1e-3 ? new Vec3(0, 0, -1) : V.unit(up);
        Vec3 right = V.unit(V.cross(d, up));
        return new Vec3[] {d, up, right};
    }

    /**
     * 몸 앞 참격 판 (세상에 고정 · 앞뒤 두 면 — 3인칭 · 다른 사람): 가운데 at · 앞면이 dir 의 반대 (쓴 사람 뒤에서 보면 앞면 — eyeSlash 와 같은 모습이 같은 자리에) ·
     * 기울기 tilt (eyeSlash 와 같은 뜻) · 칼빛 너비 width 칸. 앞면 slashm · 뒷면 slash (세계에서 같은 그림 — 휘두르는 쪽이 칼과 같음)
     */
    static void card(ServerLevel level, Lib L, Vec3 at, Vec3 dir, double tilt, double width, Predicate<ServerPlayer> who) {
        Vec3[] ax = screenAxes(dir);
        Quaternionf q = Vfx.basis(ax[2], ax[1], V.scale(ax[0], -1));
        tiled(level, L.n("slashm"), 2, at, width / SLASH_FILL, q, 180 - tilt, 0, 1, true, L.n("slash"), who, 1.0);
    }

    /** 땅에 눕힌 참격 판 (JobFx.CardFace.UP — 면이 위): 불룩한 날 = dir 의 수평을 위에서 tilt 도 돌린 쪽 · 칼빛 너비 width 칸 */
    static void cardUp(ServerLevel level, Lib L, Vec3 at, Vec3 dir, double tilt, double width, Predicate<ServerPlayer> who) {
        Vec3 h = V.flat(dir);
        h = V.len(h) < 1e-6 ? new Vec3(0, 0, 1) : V.unit(h);
        Vec3 bulge = V.turnY(h, tilt);
        Quaternionf q = Vfx.basis(V.unit(V.cross(bulge, V.UP)), bulge, V.UP);
        tiled(level, L.n("slash"), 2, at, width / SLASH_FILL, q, 0, 0, 1, false, null, who, 1.0);
    }

    /**
     * 날아가는 검기 (JobFx.bigWave · slashWave · groundWave · wave — 카메라를 보는 초승달): at 에서 dir 쪽으로 speed 칸/초 · life 초 · 반폭 half 칸 · 기울기 waveTilt
     * (bigWave 의 뜻 — 0 = 위로 불룩 · 90 = 세로 · 180 = 아래로 불룩: slashm 을 −waveTilt 돌림) — 날아가는 동안 베기 칸이 차례로 (그어짐 → 거둬짐)
     */
    static void wave(ServerLevel level, Lib L, Vec3 at, Vec3 dir, double speed, double half, double waveTilt, double life, Predicate<ServerPlayer> who,
            double alpha) {
        Vec3 d = V.len(dir) > 1e-6 ? V.unit(dir) : V.UP;
        int ticks = Math.max(2, (int) Math.round(life * 20));
        int frames = Vfx.sheet(L.n("slashm_00")).frames();
        int every = Math.max(1, (int) Math.round(ticks / (double) frames));
        Vfx.Fx fx = tiled(level, L.n("slashm"), 2, at, half * 2, null, -waveTilt, 0, every, false, null, who, alpha);
        if (speed > 0) fx.shift(V.scale(d, speed * ticks / 20.0), ticks);
    }

    /** 맞은 자리 (JobFx.hit · JobImpact): X 베기 · 섬광 (피 = 맞은 자리 피 튐) · 날아가는 작은 그림 — 몸 가운데 · power (1 = 보통) */
    static void hit(ServerLevel level, Lib L, LivingEntity e, double power) {
        Vec3 b = front(e, Kit.bodyPoint(e));
        double size = (0.75 + Kit.bodyPad(e) * 0.6) * power;
        xslash(level, L, b, size * 1.7, Kit.rand() * 360);
        flash(level, L, b, size * 1.9);
        embers(level, L, b, 3 + (int) Math.round(power * 2), size * 1.1, 0.3, 12);
    }

    /** 터짐 (JobFx.burst): 작으면 섬광 · 보통이면 작은 폭발 (boom_s) · 크면 큰 폭발 (2 × 2) — 크기 size (예전 빛 반 크기) */
    static void burst(ServerLevel level, Lib L, Vec3 at, double size, Predicate<ServerPlayer> who) {
        if (size < 0.7) {
            flash(level, L, at, size * 2.6, who);
            return;
        }
        if (size < 2.0) {
            Vfx.Fx fx = Vfx.at(level, at, 20, farEye(who, at, nearR(L, size * 1.8)));
            single(fx, L.n("boom_s"), at, size * 1.8, Kit.rand() * 30 - 15, 1, false);
            flash(level, L, at, size * 1.5, who);
            if (who == null) embers(level, L, at, 4 + (int) (size * 3), size, 0.35, 16);
            return;
        }
        tiled(level, L.n("boom"), 2, at, size * 1.6, null, Kit.rand() * 30 - 15, 0, 1, false, null, farEye(who, at, nearR(L, size * 1.6) * 0.5), 1.0);
        if (who == null) {
            embers(level, L, at, 14, size * 0.8, 0.45, 22);
            shards(level, L, at, 6, size * 0.8, 0.4, 18);
        }
    }

    /** 땅 자국 한 장 (crack_s — 눕힘, 2틱부터 6틱마다): 땅 g · 지름 size 칸 */
    static void crackS(ServerLevel level, Lib L, Vec3 g, double size) {
        crackS(level, L, g, size, null);
    }

    static void crackS(ServerLevel level, Lib L, Vec3 g, double size, Predicate<ServerPlayer> who) {
        Vfx.Fx cx = Vfx.at(level, g, 40, who);
        Vfx.Sprite cr = cx.sprite(L.n("crack_s"), V.up(g, 0.012));
        cr.fixed(flat(Kit.rand() * 360));
        cr.size(size);
        cr.play(2, 0, Vfx.sheet(L.n("crack_s")).frames(), 6, false);
    }

    /** 땅 내려찍기 (JobFx.smash — 반지름 radius 칸): 충격파 · 땅 자국 · 흙먼지 · 날아가는 작은 그림 */
    static void smash(ServerLevel level, Lib L, Vec3 at, double radius, Predicate<ServerPlayer> who) {
        Vec3 g = ground(level, at);
        tiled(level, L.n("shock"), 2, V.up(g, 0.03), radius * 2.3, flat(Kit.rand() * 360), 0, 0, 1, false, null, who, 1.0);
        crackS(level, L, g, radius * 1.5, who);
        Vfx.Fx dx = Vfx.at(level, g, 14, who);
        double ds = Math.min(4.5, radius * 1.2);
        single(dx, L.n("dust"), V.up(g, ds * 0.25), ds, 0, 1, false);
        if (who == null) embers(level, L, V.up(g, 0.3), 6 + (int) (radius * 2), radius * 0.8, 0.4, 18);
    }

    /** 칼끝 반짝임 (JobFx.glint): 짧으면 섬광 · 길면 반짝임이 깜박이며 머묾 (vel 칸/초로 같이 움직임) */
    static void glint(ServerLevel level, Lib L, Vec3 at, double size, double life, Vec3 vel, Predicate<ServerPlayer> who) {
        int ticks = Math.max(2, (int) Math.round(life * 20));
        flash(level, L, at, size * 2.0, who);
        if (ticks <= 7) return;
        Vfx.Fx fx = Vfx.at(level, at, ticks + 6, farEye(who, at, nearR(L, size * 1.5)));
        Vfx.Sprite s = single(fx, L.n(L.ember()), at, size * 1.5, Kit.rand() * 40 - 20, 1, true);
        if (vel != null && V.len(vel) > 1e-3) s.to(V.add(at, vel, ticks / 20.0), -1, Math.min(59, ticks));
        s.fade(Math.max(1, ticks - 4), 4);
    }

    /**
     * 한 바퀴 베기 (JobFx.ring — 땅 · 허리 높이에 눕힘): 가운데 c · 반지름 radius 칸 · reverse = 반시계 (미리 뒤집은 spinm) · 불투명 alpha. 묶음에 spin 이 없거나
     * 오래 머무는 고리 (hold 12틱 넘게 — 범위 예고) 면 땅 고리 (ring — 커지며 나타나 hold 틱 머묾)
     */
    static void spin(ServerLevel level, Lib L, Vec3 c, double radius, boolean reverse, double alpha, int hold, Predicate<ServerPlayer> who) {
        if (!has(L, "spin_") || hold > 12) {
            ring(level, L, c, radius, 3, Math.max(2, hold - 3), who, alpha);
            return;
        }
        tiled(level, L.n(reverse ? "spinm" : "spin"), 2, V.up(c, 0.02), radius * 2 / 0.8, flat(Kit.rand() * 360), 0, 0, 1, false, null, who, alpha);
    }

    /** 땅 고리 — 볼 사람 · 불투명을 정함 */
    static Vfx.Fx ring(ServerLevel level, Lib L, Vec3 c, double r, int grow, int hold, Predicate<ServerPlayer> who, double alpha) {
        int G = Math.max(1, grow), Hh = Math.max(1, hold);
        Vfx.Fx fx = Vfx.at(level, c, G + Hh + 8, who);
        double size = r * 2.0 / 0.9;
        double t = size / 2;
        Quaternionf q = flat(Kit.rand() * 360);
        String base = L.n("ring");
        int frames = Vfx.sheet(base + "_00").frames();
        for (int rr = 0; rr < 2; rr++) {
            for (int cc = 0; cc < 2; cc++) {
                Vfx.Sprite sp = fx.sprite(base + "_" + rr + cc, V.up(c, 0.03));
                sp.fixed(q);
                double ox = (cc - 0.5) * t;
                double oy = (0.5 - rr) * t;
                sp.size(t * 0.15);
                sp.offset(ox * 0.15, oy * 0.15);
                if (alpha < 0.999) sp.alpha(alpha);
                sp.play(0, 0, frames, 3, true);
                fx.on(1, () -> {
                    sp.offset(ox, oy);
                    sp.to(null, t, G);
                });
                sp.fade(G + Hh, 4);
            }
        }
        return fx;
    }

    /**
     * 일섬 · 걸린 칼자국 (JobFx.line · 블레이드 에코 — cut: 0 ~ 1 그어짐 → 2 ~ 4 머묾 (되풀이) → 5 ~ 7 터져 벌어짐): from → to 를 품은 두 판 (줄을 축으로 눕힌 판 · 세운 판,
     * 앞뒤 두 면 — 어느 쪽에서 봐도 보임) · hold 틱 머문 뒤 터짐 (burst false 면 머문 뒤 옅어짐). 반환 = 효과
     */
    static Vfx.Fx cut(ServerLevel level, Lib L, Vec3 from, Vec3 to, int hold, boolean burst, Predicate<ServerPlayer> who) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        if (len < 0.2 || !has(L, "cut")) return null;
        Vec3 ex = V.scale(d, 1 / len);
        Vec3 side = V.cross(ex, V.UP);
        side = V.len(side) < 1e-3 ? new Vec3(1, 0, 0) : V.unit(side);
        Vec3 n1 = V.unit(V.cross(ex, side));
        Quaternionf flatQ = Vfx.basis(ex, side, V.unit(V.cross(ex, side)));
        Quaternionf upQ = Vfx.basis(ex, n1, V.unit(V.cross(ex, n1)));
        Vec3 mid = V.lerp(from, to, 0.5);
        int H = Math.max(1, hold);
        int life = 2 + H + (burst ? 4 : 5) + 2;
        Vfx.Fx fx = Vfx.at(level, mid, life, who);
        String name = L.n("cut");
        double size = len / 0.92;
        for (Quaternionf q : new Quaternionf[] {flatQ, upQ}) {
            for (int s = 0; s < 2; s++) {
                Vfx.Sprite sp = fx.sprite(name, mid);
                sp.fixed(q);
                if (s == 1) sp.mirror();
                sp.size(size);
                sp.play(0, 0, 2, 1, false);
                fx.on(2, () -> sp.play(fx.age(), 2, 3, 2, true));
                if (burst) fx.on(2 + H, () -> sp.play(fx.age(), 5, 3, 1, false));
                else sp.fade(2 + H, 5);
            }
        }
        return fx;
    }

    /**
     * 땅 가르기 (검사 라그나로크 · 블러드 나이트 — rift 여섯 칸을 눕혀 이어 붙임): 땅 g 에서 f 쪽 (수평) 으로 len 칸 — piece 칸 토막마다 그 자리에 닿는 틱 (speed 칸/초) 에 열림
     */
    static void rift(ServerLevel level, Lib L, Vec3 g, Vec3 f, double len, double speed, double piece) {
        if (!has(L, "rift")) return;
        Vec3 h = V.flat(f);
        if (V.len(h) < 1e-6) return;
        h = V.unit(h);
        String name = L.n("rift");
        int frames = Vfx.sheet(name).frames();
        for (double s = 0.3; s < len; s += piece * 0.78) {
            int at = (int) Math.round(s / Math.max(1, speed) * 20);
            Vec3 m = ground(level, V.add(g, h, s + piece * 0.4));
            Vec3 ex = Kit.rand() < 0.5 ? h : V.scale(h, -1);
            Quaternionf q = Vfx.basis(ex, V.cross(V.UP, ex), V.UP);
            Vfx.Fx fx = Vfx.at(level, m, at + frames * 7 + 6);
            fx.on(at, () -> {
                Vfx.Sprite sp = fx.sprite(name, V.up(m, 0.015));
                sp.fixed(q);
                sp.size(piece * 1.15);
                sp.play(fx.age(), 0, 1, 2, false);
                fx.on(fx.age() + 2, () -> sp.play(fx.age(), 1, frames - 1, 7, false));
            });
        }
    }

    /** 핏빛 달 (moon 2 × 2 되풀이 — 카메라를 봄): 가운데 at · 지름 size 칸 · life 틱 (grow 틱에 커지며 나타남 · 끝 10틱 옅어짐) */
    static Vfx.Fx moon(ServerLevel level, Lib L, Vec3 at, double size, int grow, int life) {
        Vfx.Fx fx = Vfx.at(level, at, life + 4);
        String base = L.n("moon");
        int frames = Vfx.sheet(base + "_00").frames();
        double t = size / 2;
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 2; c++) {
                Vfx.Sprite sp = fx.sprite(base + "_" + r + c, at);
                double ox = (c - 0.5) * t;
                double oy = (0.5 - r) * t;
                sp.size(t * 0.6);
                sp.offset(ox * 0.6, oy * 0.6);
                sp.play(0, 0, frames, 3, true);
                fx.on(1, () -> {
                    sp.offset(ox, oy);
                    sp.to(null, t, Math.max(1, grow));
                });
                sp.fade(Math.max(2, life - 10), 10);
            }
        }
        return fx;
    }

    /** 세로 그림 하나 (pillar — 빛기둥 · 피 기둥, 세로축 빌보드): 발 자리 foot · 높이 h 칸 · every 틱마다 한 칸 */
    static void pillar(ServerLevel level, Lib L, Vec3 foot, double h, int every) {
        pillar(level, L, foot, h, every, null);
    }

    /** 같은 것 — 볼 사람을 정함 (1인칭 눈 바로 앞 기둥은 거름: 그림 128 × 256 이라 한 픽셀 = h / 256 칸) */
    static void pillar(ServerLevel level, Lib L, Vec3 foot, double h, int every, Predicate<ServerPlayer> who) {
        String name = L.n("pillar");
        if (Vfx.sheetNames(name).isEmpty()) return;
        Vfx.Sheet s = Vfx.sheet(name);
        Vfx.Fx fx = Vfx.at(level, foot, s.frames() * Math.max(1, every) + 4, farEye(who, V.up(foot, h * 0.5), nearR(L, h)));
        Vfx.Sprite sp = fx.sprite(name, V.up(foot, h * 0.5));
        sp.vertical();
        sp.size(h);
        sp.play(0, 0, s.frames(), Math.max(1, every), false);
    }

    /**
     * 빛의 대검 (ak_excal 1 × 4 — 위를 보는 칼, 소드마스터 엑스칼리버): 손 hands 가 손잡이를 쥠 · 칼끝 쪽 dir · 길이 len 칸 — 칼의 면은 쓴 사람의 오른쪽 축 right 를 품음
     * (뒤 3인칭에서 넓게 · 내리치면 위에서 넓게) · 앞뒤 두 면. 반환 = 조각 그림 넷 × 두 면 (excalPose 로 돌림 · 늘림)
     */
    static Vfx.Sprite[] excal(Vfx.Fx fx, Vec3 hands, Vec3 dir, Vec3 right, double len) {
        Vfx.Sprite[] out = new Vfx.Sprite[8];
        String base = GOLD.n("excal");
        int frames = Vfx.sheet(base + "_00").frames();
        for (int r = 0; r < 4; r++) {
            for (int s = 0; s < 2; s++) {
                Vfx.Sprite sp = fx.sprite(base + "_" + r + "0", hands);
                if (s == 1) sp.mirror();
                sp.play(fx.age(), (int) (Kit.rand() * frames), frames, 2, true);
                out[r * 2 + s] = sp;
            }
        }
        excalPose(out, hands, dir, right, len, 0);
        return out;
    }

    /**
     * 땅에서 솟구치는 빛의 검 (ak_excal 1 × 4 — 세로축 빌보드, 엑스칼리버 검격 뒤): 땅 g · 높이 h 칸 — 땅속에서 3틱에 솟아 hold 틱 머문 뒤 5틱에 옅어짐 · 밑동 섬광
     */
    static void risingSword(ServerLevel level, Vec3 g, double h, int hold) {
        String base = GOLD.n("excal");
        if (!has(GOLD, "excal_00")) return;
        int frames = Vfx.sheet(base + "_00").frames();
        Vfx.Fx fx = Vfx.at(level, g, 3 + hold + 10);
        double t = h / 4;
        Vec3 low = V.up(g, -h * 0.6);
        Vec3 high = V.up(g, h * 0.385); // 코등이 (그림 아래에서 0.115) 가 땅에 — 손잡이는 땅속
        for (int r = 0; r < 4; r++) {
            Vfx.Sprite sp = fx.sprite(base + "_" + r + "0", low);
            sp.vertical();
            sp.size(t);
            sp.offset(0, (1.5 - r) * t);
            sp.play(0, (int) (Kit.rand() * frames), frames, 2, true);
            fx.on(1, () -> sp.to(high, t, 3));
            sp.fade(3 + hold, 5);
        }
        flash(level, GOLD, V.up(g, 0.3), h * 0.45);
    }

    /** 불꽃 고리 (br_flame — 단조 불꽃을 땅 고리를 따라 세움, 소드마스터 용의 피): 가운데 땅 c · 반지름 R · 불꽃 높이 h · 한 번 솟았다 사그라듦 */
    static void flameRing(ServerLevel level, Vec3 c, double R, double h) {
        int count = Math.max(10, (int) Math.round(2 * Math.PI * R / 0.9));
        for (int i = 0; i < count; i++) {
            double a = i * 2 * Math.PI / count;
            Vec3 p = ground(level, new Vec3(c.x + Math.cos(a) * R, c.y + 1, c.z + Math.sin(a) * R));
            flame(level, FORGE, p, h * (0.8 + Kit.rand() * 0.4), 1);
        }
    }

    /** 빛의 대검 모습: 손 hands · 칼끝 쪽 dir · 오른쪽 축 right · 길이 len (그림 높이 — 손은 그림 아래에서 0.075 높이) · ticks 틱 보간 */
    static void excalPose(Vfx.Sprite[] tiles, Vec3 hands, Vec3 dir, Vec3 right, double len, int ticks) {
        Vec3 ey = V.unit(dir);
        Vec3 ex = V.sub(right, V.scale(ey, V.dot(right, ey)));
        ex = V.len(ex) < 1e-3 ? V.rightOf(V.flat(ey)) : V.unit(ex);
        Vec3 ez = V.cross(ex, ey);
        Quaternionf q = Vfx.basis(ex, ey, ez);
        double t = len / 4;
        Vec3 center = V.add(hands, ey, len * (0.5 - 0.075));
        for (int r = 0; r < 4; r++) {
            for (int s = 0; s < 2; s++) {
                Vfx.Sprite sp = tiles[r * 2 + s];
                if (sp == null) continue;
                sp.offset(0, (1.5 - r) * t);
                if (ticks <= 0) sp.fixed(q);
                else sp.face(q, ticks);
                sp.to(center, t, ticks);
            }
        }
    }
}
