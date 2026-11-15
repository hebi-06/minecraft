package mmorpg.fabric.job;

import mmorpg.core.Jobs;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 2026-11-15 직업 전용 2D 이펙트 그림 (사용자 "직업마다 전용으로 그릴래" — 시범 팬텀 버스트 "헐 나은데? 이렇게 하자"): 미리 렌더링한 칠한 2D 프레임
 * (tools/jobvfx/&lt;직업&gt;.py — 글자 표시는 명암 계산이 없어 칠한 색 그대로 · 큰 그림은 2 × 2 조각 512 픽셀) · 날아가는 불씨 · 칼 조각 · 발광 외곽선 (리소스 팩
 * 후처리 post_effect/entity_outline — 자체 가우스 흐림 shaders/post/vfx_blur.fsh 로 부드러운 빛 번짐).
 * <p>
 * 그림 묶음 {@link Lib} = 그림판 이름 앞 (tools/jobvfx 의 LIB) · 발광 색. 지금은 팬텀 아처 (pa) — 2차 둘은 묶음을 그릴 때까지 예전 BlockFx 길 ({@link #lib} 이
 * null).
 */
final class PhantomFx {
    private PhantomFx() {}

    /** 칼 · 폭발의 발광 빛 (외곽선 → 빛 번짐 후처리) 색 */
    static final int GLOW_CRIMSON = 0xFF3A3A;
    static final int GLOW_CYAN = 0x7FD8FF;

    /** 직업 전용 그림 묶음: 그림판 이름 앞 p (pa_boom …) · 칼 발광 색 glow · 투영 선 발광 색 trace */
    record Lib(String p, int glow, int trace) {
        String n(String name) {
            return p + "_" + name;
        }
    }

    /** 팬텀 아처 (tools/jobvfx/phantom.py — 진홍 불 · 푸른 흰 투영 · 강철) */
    static final Lib PHANTOM = new Lib("pa", GLOW_CRIMSON, GLOW_CYAN);

    /** 이 직업의 그림 묶음 (아직 그리지 않은 직업은 null — 예전 BlockFx 길) */
    static Lib lib(Jobs.Job j) {
        return j != null && j.id().equals("archer") ? PHANTOM : null;
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
        Vfx.Sheet s0 = Vfx.sheet(base + "_00");
        int frames = s0.frames();
        int life = from + frames * every + 4;
        Vfx.Fx fx = Vfx.at(level, at, life);
        double t = size / k;
        fx.on(Math.max(0, from), () -> {
            for (int r = 0; r < k; r++) {
                for (int c = 0; c < k; c++) {
                    Vfx.Sprite sp = fx.sprite(base + "_" + r + c, at);
                    if (facing != null) sp.fixed(facing);
                    if (roll != 0) sp.roll(roll, 0);
                    sp.size(t);
                    sp.offset((c - (k - 1) / 2.0) * t, ((k - 1) / 2.0 - r) * t);
                    sp.play(fx.age(), 0, frames, every, false);
                }
            }
        });
        return fx;
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
        Vfx.Fx fx = Vfx.at(level, at, 12);
        single(fx, L.n("flash"), at, size, Kit.rand() * 40 - 20, 1, false);
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
        fly(level, c, n, dist, size, life, L.n("ember"), 1.0);
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
        tiled(level, L.n("shock"), 2, V.up(gc, 0.02), rad * 3.2, flat(Kit.rand() * 360), 0, 0, 1);
        tiled(level, L.n("crack"), 2, gc, rad * 1.7, flat(Kit.rand() * 360), 0, 2, 7);
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
        tiled(level, L.n("xslash"), 2, c, size, null, roll, 0, 1);
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
}
