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
    /** 2026-11-15 그림자 (암살자 — el_shadow: 짙은 보라 · 자홍 — 그림자 연기 · 엇갈린 베기 · 검은 장미 · 꽃 · 꽃잎 · 도는 비수 · 찌르기 · 일식, tools/jobvfx/shadow.py) */
    static final int GLOW_SHADOW = 0xC45CFF;
    static final Lib SHADOW = new Lib("el_shadow", GLOW_SHADOW, GLOW_SHADOW, "ember");
    /**
     * 2026-11-16 비전 (매지션 · 아크메이지 — el_arcane: 남빛 · 금빛 — 떠도는 별 · 별 마탄 · 광선 · 깨지는 방패 · 금서의 주문 · 눈꽃 · 순간 이동 줄기 · 솟는 별빛 ·
     * 별자리의 별 · 별똥 · 별의 소용돌이 · 빛 고리 · 빛의 방패 · 별빛 맞음, tools/jobvfx/arcane.py)
     */
    static final int GLOW_ARCANE = 0x8FA8FF;
    static final Lib ARCANE = new Lib("el_arcane", GLOW_ARCANE, GLOW_ARCANE, "ember");
    /** 2026-11-16 혼 (네크로맨서 — el_soul: 녹색 혼불 · 뼈빛 — 혼불 · 해골 · 갈라진 땅 · 뼈 창 · 망자의 손 · 망자의 연기 · 혼 고리 · 혼 터짐, tools/jobvfx/soul.py) */
    static final int GLOW_SOUL = 0x5CF08A;
    static final Lib SOUL = new Lib("el_soul", GLOW_SOUL, GLOW_SOUL, "ember");

    /** 이 직업의 그림 묶음 (그리지 않은 직업은 null — 예전 길) */
    static Lib lib(Jobs.Job j) {
        if (j == null) return null;
        return switch (j.id()) {
            case "archer" -> PHANTOM;
            case "arsenalking", "swordmaster" -> GOLD;
            case "bladeranger" -> FORGE;
            case "swordsman" -> STEEL;
            case "bloodknight" -> BLOOD;
            case "assassin" -> SHADOW;
            case "mage", "arcmage" -> ARCANE;
            case "necromancer" -> SOUL;
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
     * 180 = 위로 불룩하게 오른쪽에서 왼쪽으로 · 95 = 위에서 아래로 내리침 · 275 = 아래에서 위로): 미리 뒤집은 그림판 (slashm — 위로 불룩 · 오른쪽 → 왼쪽 = 예전 판 모델
     * slash_job 의 그림) 을 180 + tilt 돌림 (화면에서 반시계 — 예전 rfx_jslash 의 spin · JobFx.slashCard FRONT 의 불룩한 날 = 화면 아래를 tilt 도 반시계로 돌린 쪽과 같음).
     * 2026-11-15 사용자 "이전은 참격이 앞을 잘보고 있는데 왜 새로 만든건 위를 보고 있고 아래를보고 있고 옆을 보고 있고" — 180 − tilt 는 예전의 좌우 거울이었음
     * (기울기 95 내리침이 오른쪽 대신 왼쪽으로 불룩 · 200 이 왼쪽 위 대신 오른쪽 위로)
     */
    static Vfx.Fx eyeSlash(ServerPlayer sp, Lib L, Vec3 at, double width, double tilt) {
        return eyeSlash(sp, L, at, width, tilt, 1.0);
    }

    /** 같은 것 — 불투명 alpha (옅은 잔상 겹) */
    static Vfx.Fx eyeSlash(ServerPlayer sp, Lib L, Vec3 at, double width, double tilt, double alpha) {
        return tiled(sp.level(), L.n("slashm"), 2, at, width / SLASH_FILL, null, 180 + tilt, 0, 1, false, null, only1p(sp), alpha);
    }

    /**
     * 2026-11-15 카메라를 보는 칼빛 초승달 (JobFx.slash · slashCard — 1인칭 · 3인칭 · 다른 사람 모두): 가운데 at · 칼빛 너비 width 칸 · 기울기 tilt (eyeSlash 와 같은 뜻).
     * 첫 영상 (CI 43693784623) 에서 쓴 사람 뒤를 보게 고정한 판은 옆 · 앞에서 비스듬히 얇게 보였음 → 늘 보는 사람 쪽 (어느 쪽에서도 화면에서 같은 기울기)
     */
    static void slashBill(ServerLevel level, Lib L, Vec3 at, double width, double tilt, Predicate<ServerPlayer> who) {
        tiled(level, L.n("slashm"), 2, at, width / SLASH_FILL, null, 180 + tilt, 0, 1, false, null, who, 1.0);
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
     * 기울기 tilt (JobFx.slash 의 뜻) · 칼빛 너비 width 칸. 앞면 slashm · 뒷면 slashm 의 거울 (예전 모드 없는 클라이언트 판과 같은 모습 — 아래 card 참고)
     */
    static void card(ServerLevel level, Lib L, Vec3 at, Vec3 dir, double tilt, double width, Predicate<ServerPlayer> who) {
        card(level, L, at, dir, tilt, width, who, 1.0, 1);
    }

    /**
     * 같은 것 — 불투명 alpha · 칸 넘김 every 틱 (날아가는 판은 수명에 맞춤). 반환 = 효과 (날아가는 판은 shift). 2026-11-15 사용자 "참격이 앞을 바라봐야 하는데 위를
     * 보는데?" — 카메라를 보는 판 (slashBill) 은 높은 3인칭 · 영상 카메라 쪽으로 뒤로 누워 위를 보는 것처럼 보였음 → 3인칭 · 다른 사람은 다시 이 앞을 보는 판
     * (2026-11-14 사용자 메모 "참격이 앞을 안보고 있음" 때 정한 FRONT)
     */
    static Vfx.Fx card(ServerLevel level, Lib L, Vec3 at, Vec3 dir, double tilt, double width, Predicate<ServerPlayer> who, double alpha, int every) {
        Vec3[] ax = screenAxes(dir);
        Quaternionf q = Vfx.basis(ax[2], ax[1], V.scale(ax[0], -1));
        // 돌림 = tilt (2026-11-15 셋째 고침 — 예전 영상 CI 43560411558 (81ca2389) 과 장면마다 맞춤): 예전 모드 없는 클라이언트의 판 (PBridge.Card) 은 판 모델
        // slash_job 을 글자로 구울 때 (tools/vfx_particles.py card_layers) 모델의 180° 돌림을 버리고 rfx_jslash2 그림을 그대로 (아래로 불룩 · 왼쪽 → 오른쪽) 판
        // (X = Y × Z · Y = 불룩한 날 −위 · cos(tilt) + 오른쪽 · sin(tilt) · Z = dir 의 반대) 에 붙였음 → 화면에 보인 불룩한 날은 그 반대 (tilt 0 = 위로 불룩 ∩ ·
        // 172 = 아래로 불룩 ∪). rfx_jslash2 = slashm 을 180° 돌린 그림이라 slashm 을 tilt 만큼 돌리면 예전과 같음. 180 + tilt 는 예전과 위아래가 뒤집혔음
        // (블레이드 아포칼립스 첫 베기 · 블러드 클레이브 큰 판). 뒷면 = 같은 그림판 slashm 의 거울 (예전 판은 한 면 — 보는 사람 쪽으로 판 세로 축 둘레 반 바퀴 돌려
        // 그림이 늘 바로 읽혔음)
        return tiled(level, L.n("slashm"), 2, at, width / SLASH_FILL, q, tilt, 0, Math.max(1, every), true, L.n("slashm"), who, alpha);
    }

    /**
     * 같은 것 — 수명 life 초에 칸을 맞춤 (짧으면 한 틱에 한 칸) · 판이 vel (칸/초) 로 같이 움직임 (예전 판의 vel — 몸과 같이 움직이는 눈앞 판 · 패링 막기 판)
     */
    static Vfx.Fx card(ServerLevel level, Lib L, Vec3 at, Vec3 dir, double tilt, double width, Predicate<ServerPlayer> who, double alpha, double life, Vec3 vel) {
        int frames = Vfx.sheet(L.n("slashm_00")).frames();
        int every = Math.max(1, (int) Math.round(Math.max(1, life * 20) / frames));
        Vfx.Fx fx = card(level, L, at, dir, tilt, width, who, alpha, every);
        follow(fx, vel, frames * every + 1);
        return fx;
    }

    /** 효과의 그림을 모두 vel (칸/초) 로 ticks 틱 동안 옮김 (0 이면 그대로) */
    static void follow(Vfx.Fx fx, Vec3 vel, int ticks) {
        if (vel == null || V.len(vel) < 1e-3) return;
        fx.shift(V.scale(vel, ticks / 20.0), ticks);
    }

    /**
     * 날아가는 앞을 보는 참격 판 (JobFx.wave 의 3인칭 · 다른 사람 — 예전 SWING 판 대신): at 에서 dir 쪽으로 speed 칸/초 · life 초 · 칼빛 너비 width · 기울기 tilt
     * (JobFx.slash 와 같은 뜻 — 0 = 아래로 불룩) — 면이 dir 의 반대 (뒤 · 3인칭에서 넓은 면)
     */
    static void cardFly(ServerLevel level, Lib L, Vec3 at, Vec3 dir, double speed, double width, double tilt, double life, Predicate<ServerPlayer> who,
            double alpha) {
        Vec3 d = V.len(dir) > 1e-6 ? V.unit(dir) : V.UP;
        int ticks = Math.max(2, (int) Math.round(life * 20));
        int frames = Vfx.sheet(L.n("slashm_00")).frames();
        Vfx.Fx fx = card(level, L, at, d, tilt, width, who, alpha, (int) Math.round(ticks / (double) frames));
        if (speed > 0) fx.shift(V.scale(d, speed * ticks / 20.0), ticks);
    }

    /**
     * 2026-11-15 날아가는 검기 판 — 예전 JobFx.wave 의 3인칭 판 (CardFace.SWING + RuneKit.faceCamIfFlat) 그대로 (사용자 "이전은 참격이 앞을 잘보고 있는데"): 불룩한 날 =
     * 날아가는 쪽 dir (그림 위쪽 Y), 면 = 위 (Z) 를 dir 둘레로 −tilt 돌림 — 다만 판이 누운 쪽이면 (cos tilt 의 크기 0.5 이상) 면이 보는 사람의 눈 (영상이면 영상 카메라 —
     * PBridge.eyeOf) 쪽 · 돌림 0 / 180. 그림 = slashm 을 판 안에서 반 바퀴 (예전 모드 없는 클라이언트 판의 rfx_jslash2 그대로 — 불룩한 날이 판의 −Y), 앞뒤 두 면. at 에서 speed 칸/초 · life 초
     */
    static void cardSwing(ServerLevel level, Lib L, ServerPlayer owner, Vec3 at, Vec3 dir, double speed, double width, double tilt, double life,
            Predicate<ServerPlayer> who, double alpha) {
        Vec3 Y = V.len(dir) > 1e-6 ? V.unit(dir) : V.UP;
        // RuneKit.screenSpin(d, UP, −tilt) 와 같은 셈 (주인 눈에서 본 판의 기울기)
        Vec3 r = V.cross(Y, V.UP);
        double spin;
        if (V.len(r) < 1e-4) spin = tilt;
        else {
            r = V.unit(r);
            Vec3 u = V.cross(r, Y);
            spin = -(Math.toDegrees(Math.atan2(V.dot(V.UP, r), V.dot(V.UP, u))) - tilt);
        }
        double c = Math.cos(Math.toRadians(-spin));
        Vec3 Z;
        double roll;
        if (Math.abs(c) >= 0.5) {
            ServerPlayer viewer = owner;
            if (viewer == null && level.getNearestPlayer(at.x, at.y, at.z, 64, false) instanceof ServerPlayer np) viewer = np;
            Vec3 eye = viewer != null ? mmorpg.fabric.vfx.PBridge.eyeOf(viewer) : V.add(at, Y, -4.0);
            Z = V.sub(eye, at);
            roll = c >= 0 ? 0 : 180;
        } else {
            Z = V.UP;
            roll = -tilt;
        }
        Z = V.sub(Z, V.scale(Y, V.dot(Z, Y)));
        if (V.len(Z) < 1e-4) {
            Z = Math.abs(Y.y) < 0.9 ? V.UP : new Vec3(1, 0, 0);
            Z = V.sub(Z, V.scale(Y, V.dot(Z, Y)));
        }
        Z = V.unit(Z);
        Vec3 X = V.unit(V.cross(Y, Z));
        // 판 Y 둘레로 roll 도 (예전 R.rotateY(roll)): X' = X cos − Z sin · Z' = X sin + Z cos
        double a = Math.toRadians(roll);
        Vec3 X2 = V.add(V.scale(X, Math.cos(a)), Z, -Math.sin(a));
        Vec3 Z2 = V.add(V.scale(X, Math.sin(a)), Z, Math.cos(a));
        Quaternionf q = Vfx.basis(V.unit(X2), Y, V.unit(Z2));
        int ticks = Math.max(2, (int) Math.round(life * 20));
        int frames = Vfx.sheet(L.n("slashm_00")).frames();
        // 판 안 돌림 180 (2026-11-15 셋째 고침): 예전 모드 없는 클라이언트 판은 rfx_jslash2 그림을 그대로 붙여 (card 참고) 불룩한 날이 판의 −Y — slashm 을 반 바퀴
        // 돌린 것과 같음. 뒷면 = 같은 그림판의 거울 (예전 한 면 판이 보는 사람 쪽으로 반 바퀴 돌던 것과 같음)
        Vfx.Fx fx = tiled(level, L.n("slashm"), 2, at, width / SLASH_FILL, q, 180, 0, Math.max(1, (int) Math.round(ticks / (double) frames)), true, L.n("slashm"),
                who, alpha);
        if (speed > 0) fx.shift(V.scale(Y, speed * ticks / 20.0), ticks);
    }

    /**
     * 2026-11-15 1인칭 눈앞 칼자국 하나 (주인의 1인칭에만 — 카메라를 봄, 예전 rfx_jcuts 대신 일섬 그림 cut): 가운데 at · 길이 len 칸 · 화면에서 줄의 각 deg (반시계 —
     * 0 = 가로) — 그어짐 → 잠깐 머묾 → 터짐 (8틱)
     */
    static void cutEye(ServerPlayer sp, Lib L, Vec3 at, double len, double deg) {
        if (!has(L, "cut")) return;
        Vfx.Fx fx = Vfx.at(sp.level(), at, 14, only1p(sp));
        single(fx, L.n("cut"), at, len / 0.92, deg, 1, false);
    }

    /** 땅에 눕힌 참격 판 (JobFx.CardFace.UP — 면이 위): 불룩한 날 = dir 의 수평을 위에서 tilt 도 돌린 쪽 · 칼빛 너비 width 칸 */
    static void cardUp(ServerLevel level, Lib L, Vec3 at, Vec3 dir, double tilt, double width, Predicate<ServerPlayer> who) {
        Vec3 h = V.flat(dir);
        h = V.len(h) < 1e-6 ? new Vec3(0, 0, 1) : V.unit(h);
        Vec3 bulge = V.turnY(h, tilt);
        Quaternionf q = Vfx.basis(V.unit(V.cross(bulge, V.UP)), bulge, V.UP);
        // 판 안 돌림 180 (2026-11-15 셋째 고침 — card 참고): 예전 모드 없는 클라이언트 판의 그림은 rfx_jslash2 그대로라 보인 불룩한 날은 bulge 의 반대 (쓴 사람 쪽).
        // 앞뒤 두 면 (뒷면 = 같은 그림판의 거울 — 예전 한 면 판이 보는 사람 쪽으로 반 바퀴 돌던 것과 같음)
        tiled(level, L.n("slashm"), 2, at, width / SLASH_FILL, q, 180, 0, 1, true, L.n("slashm"), who, 1.0);
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
        // +waveTilt (화면에서 반시계 — 예전 rfx_jwave 의 spin: 0 = 위로 불룩 · 90 = 왼쪽으로 불룩). 2026-11-15 −waveTilt 는 좌우 거울이었음
        Vfx.Fx fx = tiled(level, L.n("slashm"), 2, at, half * 2, null, waveTilt, 0, every, false, null, who, alpha);
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
     * 일섬 · 걸린 칼자국 (JobFx.line · 블레이드 에코 — cut: 0 ~ 1 그어짐 → 2 ~ 4 머묾 (되풀이) → 5 ~ 7 터져 벌어짐): from → to 를 품은 한 판이 줄을 축으로 보는 사람의
     * 눈 (가장 가까운 사람 — 영상이면 영상 카메라, PBridge.eyeOf) 쪽을 봄 (앞뒤 두 면) — 예전 rfx_jline2 · rfx_jecho (가는 쪽으로 누워 눈 쪽을 봄) 와 같음. 2026-11-15 사용자
     * "위를 보고 있고 … 옆을 보고 있고" — 전에는 줄을 축으로 눕힌 판 · 세운 판 둘이라 영상에서 위 · 옆을 보는 판으로 보였음. hold 틱 머문 뒤 터짐 (burst false 면 옅어짐)
     */
    static Vfx.Fx cut(ServerLevel level, Lib L, Vec3 from, Vec3 to, int hold, boolean burst, Predicate<ServerPlayer> who) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        if (len < 0.2 || !has(L, "cut")) return null;
        Vec3 ex = V.scale(d, 1 / len);
        Vec3 mid = V.lerp(from, to, 0.5);
        net.minecraft.world.entity.player.Player near = level.getNearestPlayer(mid.x, mid.y, mid.z, 64, false);
        Vec3 eye = near instanceof ServerPlayer np ? mmorpg.fabric.vfx.PBridge.eyeOf(np) : V.up(mid, 2.0);
        Vec3 ez = V.sub(eye, mid);
        ez = V.sub(ez, V.scale(ex, V.dot(ez, ex)));
        if (V.len(ez) < 1e-4) {
            ez = V.cross(ex, V.UP);
            if (V.len(ez) < 1e-4) ez = new Vec3(1, 0, 0);
        }
        ez = V.unit(ez);
        Vec3 ey = V.unit(V.cross(ez, ex));
        Quaternionf faceQ = Vfx.basis(ex, ey, ez);
        int H = Math.max(1, hold);
        int life = 2 + H + (burst ? 4 : 5) + 2;
        Vfx.Fx fx = Vfx.at(level, mid, life, who);
        String name = L.n("cut");
        double size = len / 0.92;
        for (Quaternionf q : new Quaternionf[] {faceQ}) {
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

    // ------------------------------------------------------------ 2026-11-15 예전 rfx 파티클을 칠한 그림판 한 장으로 (암살자 그림자 묶음)

    /** rfx 방향: 카메라를 봄 (rotate_xyz) */
    static final int BILL = 0;
    /** rfx 방향: 면이 dir (direction_z — 위 = 세계 위를 dir 에 수직으로, 뒷면은 거울) */
    static final int FACE_DIR = 1;
    /** rfx 방향: 세로축만 카메라를 봄 (lookat_y) */
    static final int VERT = 2;
    /**
     * 2026-11-16 rfx 방향: 가는 쪽으로 누워 보는 사람의 눈 쪽을 봄 (lookat_direction — 그림 +x = dir · 면 = 눈 쪽을 dir 에 수직으로): 보는 사람마다 한 장
     * (PBridge 는 기준 눈 하나 — 다른 사람에게는 옆 · 뒤가 보였음)
     */
    static final int LOOK_DIR = 3;

    /** 2026-11-16 예전 rfx 이름 → 묶음의 칠한 그림 (조각 k · 방향 · 세로 몫 sy · 크기 몫 — 예전 그림과 같은 틀) */
    record Paint(String art, int k, int facing, double sy, double scale) {}

    /**
     * 2026-11-16 매지션 라인 (사용자 "다음 직업으로 마법사 작업"): 칠한 그림이 있는 예전 rfx — 예전 그림판의 칸 크기 (세로 몫) · 머리 자리 · 고리 반지름에 맞춰 그림.
     * jburst2 (색을 정한 터짐 — 프리즘 레이 갈래 끝) 는 섬광 (PhantomFx.burst 의 작은 터짐과 같은 크기 몫 1.3), jring2 (세운 고리 · 눈앞 고리) 는 한 바퀴 고리
     * (예전 고리 반지름 0.88 · 그림 0.8)
     */
    static final java.util.Map<String, Paint> PAINT = java.util.Map.ofEntries(
            java.util.Map.entry("jstar", new Paint("star", 1, BILL, 1, 1)),
            java.util.Map.entry("jbolt2", new Paint("bolt", 1, LOOK_DIR, 1 / 3.0, 1)),
            java.util.Map.entry("jbeam2", new Paint("beam", 1, LOOK_DIR, 0.25, 1)),
            java.util.Map.entry("jshatter2", new Paint("shatter", 1, BILL, 1, 1)),
            java.util.Map.entry("jfireball", new Paint("fireball", 1, LOOK_DIR, 0.375, 1)),
            java.util.Map.entry("jicelance", new Paint("icelance", 1, LOOK_DIR, 0.25, 1)),
            java.util.Map.entry("jsparkorb", new Paint("sparkorb", 1, LOOK_DIR, 0.5, 1)),
            java.util.Map.entry("jfrost", new Paint("frost", 1, BILL, 1, 1)),
            java.util.Map.entry("jdash2", new Paint("dash", 1, LOOK_DIR, 0.25, 1)),
            java.util.Map.entry("jaura2", new Paint("aura", 1, VERT, 2, 1)),
            java.util.Map.entry("jstarnode", new Paint("starnode", 1, BILL, 1, 1)),
            java.util.Map.entry("jfallstar", new Paint("fallstar", 1, LOOK_DIR, 0.375, 1)),
            java.util.Map.entry("jgalaxy", new Paint("galaxy", 2, FACE_DIR, 1, 1)),
            java.util.Map.entry("jhalo", new Paint("halo", 2, FACE_DIR, 1, 1)),
            java.util.Map.entry("jaegis2", new Paint("aegis", 1, VERT, 1.25, 1)),
            java.util.Map.entry("jsoul2", new Paint("soul", 1, BILL, 1, 1)),
            java.util.Map.entry("jskull", new Paint("skull", 1, BILL, 1, 1)),
            java.util.Map.entry("jrift", new Paint("rift", 1, FACE_DIR, 2.5, 1)),
            java.util.Map.entry("jbonespear", new Paint("bonespear", 1, LOOK_DIR, 0.25, 1)),
            java.util.Map.entry("jhand2", new Paint("hand", 1, VERT, 2, 1)),
            java.util.Map.entry("jsmoke2", new Paint("smoke", 1, BILL, 1, 1)),
            java.util.Map.entry("jburst2", new Paint("flash", 1, BILL, 1, 1.3)),
            java.util.Map.entry("jring2", new Paint("spin", 2, FACE_DIR, 1, 0.88 / 0.8)));

    /** 칠한 직업 (그 직업의 두 빛으로 그린 예전 rfx 를 그 묶음의 칠한 그림으로 — 직업 빛 배열은 jobs.json 을 다시 읽으면 바뀌므로 그때그때 비교) */
    private static final String[] PAINTED_JOBS = {"mage", "arcmage", "necromancer"};
    /** 스킬이 직업 빛 대신 쓰는 빛 묶음 (같은 배열) → 묶음: 아크메이지 밤빛 · 금서의 세 주문 빛 · 프리즘 레이 다섯 빛 (무지개 대신 비전 광선) · 망자의 연기 */
    private static java.util.Map<double[], Lib> palette;

    /** 빛 glow 로 그린 예전 rfx 를 칠할 묶음 (없으면 null — 예전 길) */
    static Lib painter(double[] glow) {
        if (glow == null) return null;
        if (palette == null) {
            java.util.Map<double[], Lib> m = new java.util.IdentityHashMap<>();
            m.put(JobTraits8.NIGHT, ARCANE);
            for (double[][] c : MageSkills.SPELLS) m.put(c[0], ARCANE);
            for (double[][] c : MageSkills.PRISM) m.put(c[0], ARCANE);
            m.put(NecroCrew.DEEP, SOUL); // 네크로맨서 망자의 연기 (짙은 녹색 · 혼불)
            palette = m;
        }
        Lib L = palette.get(glow);
        if (L != null) return L;
        for (String id : PAINTED_JOBS) {
            Jobs.Job j = Jobs.job(id);
            if (j != null && (j.color()[0] == glow || j.color()[1] == glow)) return lib(j);
        }
        return null;
    }

    /**
     * 2026-11-16 예전 rfx 한 벌 (JobFx.two · cells — 빛 glow · 값 b) 을 칠한 그림 한 장으로: glow 가 칠한 직업의 빛 (painter) 이고 그 묶음에 이 rfx 의 그림 (PAINT) 이
     * 있으면 rfx 다리로 같은 자리 · 크기 · 방향 · 움직임에 그리고 true (사용자 "예전 방향 · 자리 · 크기는 그대로, 그림만")
     */
    static boolean painted(ServerLevel level, String rfx, int first, Vec3 at, Opt b, double[] glow) {
        Paint p = PAINT.get(rfx);
        if (p == null) return false;
        Lib L = painter(glow);
        if (L == null) return false;
        String sheet = L.n(p.art());
        String probe = p.k() > 1 ? sheet + "_00" : sheet;
        if (!Vfx.hasSheet(probe)) return false;
        int n = Vfx.sheet(probe).frames();
        int f0 = Math.floorMod(first, n);
        int count = Math.max(1, Math.min((int) b.num("frames", 1), n - f0));
        Opt bb = b.with("frames", (double) count);
        if (p.scale() != 1) bb = bb.with("size", b.num("size", 0) * p.scale());
        rfx(level, sheet, p.k(), f0, at, bb, p.facing(), p.sy());
        return true;
    }

    /**
     * 예전 rfx 파티클 한 벌 (빛 + 심지 두 장 — JobFx.two · cells) 을 칠한 그림판 한 장으로 (사용자 "예전 방향 · 자리 · 크기는 그대로, 그림만"): 모드 없는
     * 클라이언트 다리 (PBridge · PSim) 의 셈 그대로 — 자리 = at + (dir × speed + vel) × 시간 · 그림 높이 = 반 크기 size × 2 × sy (세로 몫) × (1 + grow × 시간 몫) ·
     * 화면 돌림 = spin + spinRate × 시간 (반시계) · 방향 facing (BILL · FACE_DIR · VERT) · 칸 = first + (loop 면 ⌊시간 × fps + phase⌋ mod count, 아니면
     * min(count − 1, ⌊시간 × fps⌋)) · 마지막 fade 몫 동안 옅어짐 · 볼 사람 view · owner. 그림판 sheet (k = 2 면 조각 sheet_rc 넷). b = two · cells 가 만든 값
     */
    static Vfx.Fx rfx(ServerLevel level, String sheet, int k, int first, Vec3 at, Opt b, int facing, double sy) {
        if (facing != LOOK_DIR) return rfx1(level, sheet, k, first, at, b, facing, sy, who(b), null);
        // 가는 쪽으로 누운 그림: 보는 사람마다 그 사람의 눈 쪽을 보는 한 장
        Predicate<ServerPlayer> base = who(b);
        Vfx.Fx last = null;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(at) > 128 * 128 || (base != null && !base.test(p))) continue;
            int id = p.getId();
            Predicate<ServerPlayer> one = q -> q.getId() == id;
            last = rfx1(level, sheet, k, first, at, b, facing, sy, base == null ? one : base.and(one), p);
        }
        return last;
    }

    private static Vfx.Fx rfx1(ServerLevel level, String sheet, int k, int first, Vec3 at, Opt b, int facing, double sy, Predicate<ServerPlayer> viewer,
            ServerPlayer looker) {
        double life = Math.max(0.05, b.num("life", 0.3));
        int ticks = Math.max(1, (int) Math.round(life * 20));
        int count = Math.max(1, (int) b.num("frames", 1));
        double fps = b.num("fps", 0);
        boolean loop = b.truthy("loop");
        double phase = b.num("phase", 0);
        Vec3 dir = b.vec("dir", V.UP);
        double dl = V.len(dir);
        Vec3 d = dl > 1e-6 ? V.scale(dir, 1 / dl) : V.UP;
        Vec3 mv = V.add(V.scale(dir, b.num("speed", 0)), b.vec("vel", V.ZERO));
        double h0 = 2 * b.num("size", 0) * sy;
        double grow = b.num("grow", 0);
        double spin = b.num("spin", 0);
        double spinRate = b.num("spinRate", 0);
        double fade = Math.max(0, Math.min(1, b.num("fade", 0)));
        double alpha = b.num("alpha", 1);
        // 1인칭 눈 바로 앞 거름 (CI 43716691507 1인칭 — 눈앞을 지나는 연기 · 꽃잎이 픽셀 덩어리로 화면을 덮음): 그림 크기에 맞춘 반지름 (nearR — 빛 0.25 × 높이) ·
        // 예전 다리가 가루로 바꾸던 작은 알갱이 (반 크기 0.11 아래) 는 가루처럼 눈에서 1.3칸 (PBridge.DUST_NEAR_EYE) 안이면 그 사람에게 보내지 않음
        double half = b.num("size", 0) * sy;
        Predicate<ServerPlayer> who = farEye(viewer, at, Math.max(half < 0.11 ? 1.3 : 0, h0 * 0.25 / k));
        Vfx.Fx fx = Vfx.at(level, at, ticks + 2, who);
        boolean look = facing == LOOK_DIR;
        Quaternionf q = facing == FACE_DIR ? dirFace(d) : look ? lookDir(d, at, looker) : null;
        int sides = facing == FACE_DIR ? 2 : 1;
        java.util.List<Vfx.Sprite> all = new java.util.ArrayList<>();
        double[] off = new double[k * k * sides * 2];
        int i = 0;
        for (int r = 0; r < k; r++) {
            for (int c = 0; c < k; c++) {
                for (int side = 0; side < sides; side++) {
                    String name = k == 1 ? sheet : sheet + "_" + r + (side == 0 ? c : k - 1 - c);
                    Vfx.Sprite sp = fx.sprite(name, at);
                    if (facing == VERT) sp.vertical();
                    else if (q != null) sp.fixed(q);
                    if (side == 1) sp.mirror();
                    off[i * 2] = c - (k - 1) / 2.0;
                    off[i * 2 + 1] = (k - 1) / 2.0 - r;
                    all.add(sp);
                    i++;
                }
            }
        }
        java.util.function.IntUnaryOperator frameAt = t -> {
            if (count <= 1) return first;
            double u = t / 20.0 * fps;
            return first + (loop ? Math.floorMod((int) Math.floor(u + phase), count) : Math.min(count - 1, (int) Math.floor(u)));
        };
        for (int n = 0; n < all.size(); n++) {
            Vfx.Sprite sp = all.get(n);
            double t0 = h0 / k;
            sp.size(t0);
            if (k > 1) sp.offset(off[n * 2] * t0, off[n * 2 + 1] * t0);
            if (spin != 0 && !look) sp.roll(spin, 0);
            sp.frame(frameAt.applyAsInt(0));
            if (alpha < 0.999) sp.alpha(alpha);
        }
        boolean moves = V.len(mv) > 1e-4;
        boolean turns = Math.abs(spinRate) > 1e-3 && !look;
        boolean grows = Math.abs(grow) > 1e-4;
        int step = turns ? (Math.abs(spinRate) > 400 ? 1 : 2) : look ? 2 : 4;
        int[] shown = {frameAt.applyAsInt(0)};
        fx.each(1, ticks, 1, t -> {
            int f = frameAt.applyAsInt(t);
            if (f != shown[0]) {
                shown[0] = f;
                for (Vfx.Sprite sp : all) sp.frame(f);
            }
            if ((moves || turns || grows || look) && (t - 1) % step == 0) {
                int t1 = Math.min(ticks, t - 1 + step);
                int dur = t1 - (t - 1);
                Vec3 p = V.add(at, mv, t1 / 20.0);
                double h = h0 * (1 + grow * t1 / (double) ticks);
                Quaternionf qq = look ? lookDir(d, p, looker) : null;
                for (int n = 0; n < all.size(); n++) {
                    Vfx.Sprite sp = all.get(n);
                    double tt = h / k;
                    if (k > 1) sp.offset(off[n * 2] * tt, off[n * 2 + 1] * tt);
                    sp.to(moves ? p : null, tt, dur);
                    if (turns) sp.roll(spin + spinRate * t1 / 20.0, dur);
                    if (qq != null) sp.face(qq, dur);
                }
            }
        });
        int from = fade > 0 ? (int) Math.round((1 - fade) * ticks) : ticks;
        for (Vfx.Sprite sp : all) {
            if (fade > 0) sp.fade(Math.max(1, from), Math.max(1, ticks - from));
            else sp.until(ticks + 1);
        }
        return fx;
    }

    /**
     * 2026-11-16 rfx lookat_direction 의 판 방향 (PBridge.fixed 와 같은 셈 — 기준 눈 대신 보는 사람 looker 의 눈 PBridge.eyeOf): 그림 +x = d · 면 = 눈 쪽을 d 에 수직으로 ·
     * 그림 +y = 면 × d
     */
    static Quaternionf lookDir(Vec3 d, Vec3 at, ServerPlayer looker) {
        Vec3 eye = looker != null ? PBridge.eyeOf(looker) : V.up(at, 2.0);
        Vec3 to = V.sub(eye, at);
        Vec3 n = V.sub(to, V.scale(d, V.dot(to, d)));
        if (V.len(n) < 1e-5) {
            Vec3 r = Math.abs(d.y) < 0.9 ? V.UP : new Vec3(1, 0, 0);
            n = V.sub(r, V.scale(d, V.dot(r, d)));
        }
        n = V.unit(n);
        return Vfx.basis(d, V.unit(V.cross(n, d)), n);
    }

    /** rfx direction_z 의 판 방향 (PBridge.fixed — 위 = 세계 위 (dir 이 거의 위 · 아래면 남쪽) 를 dir 에 수직으로 · X = 위 × dir · 앞면 = dir) */
    static Quaternionf dirFace(Vec3 d) {
        Vec3 ref = Math.abs(d.y) < 0.999 ? V.UP : new Vec3(0, 0, 1);
        Vec3 y = V.unit(V.sub(ref, V.scale(d, V.dot(ref, d))));
        Vec3 x = V.unit(V.cross(y, d));
        return Vfx.basis(x, y, d);
    }
}
