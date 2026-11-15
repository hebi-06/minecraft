package mmorpg.fabric.vfx;

import mmorpg.fabric.rune.RuneProps;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 2026-11-15 블록 볼륨 이펙트 (사용자 "이런식이면 그냥 위르드처럼 해야 할거 같은데" · "그냥 도트처럼 보이는게 문제임" — claude/archer-remake.md): 상자를 물들이고
 * 축마다 늘여 (Vfx.Prop.stretch) 빛 줄 · 호 · 폭발 · 고리 · 조각 · 불꽃을 만듦. 2026-11-15 HD 칠 (사용자 "색칠한게 너무 발로 한거 같음" · "단색이 아니라 색을
 * 좀 예쁘게 다양하게" — tools/vfx_hd.py): 줄 · 보석 · 덩이 · 불꽃 상자는 색조를 옮긴 그늘 (물들임 0) + 뜨거운 심 (물들임 1 = hot), 금빛 문 · 사슬 · 땅 마법진은
 * 구운 색 모델 하나. 참격 · 잔상은 2D (PhantomArcher.slash2d · trail2d — 사용자 "참격이나 잔상 남는 연출도 넣어서 그런거는 2D 연출로")
 */
public final class BlockFx {
    private BlockFx() {}

    /**
     * 2026-11-15 HD 칠 (사용자 "색칠한게 너무 발로 한거 같음" · "AI가 만든 느낌을 줄이고 단색이 아니라 색을 좀 예쁘게 다양하게" — tools/vfx_hd.py): 줄 상자 — 가로 셀 띠
     * (물들임 0 = 색 · 가장자리는 와인 · 보라 쪽으로 옮긴 그늘) + 가운데 뜨거운 심 (물들임 1 = hot(색)). 모든 상자의 바탕
     */
    public static final String CUBE = "rpg:vfx/fx_bar";
    /** 보석 상자 (네 빗면 · 뜨거운 마름모 — 물들임 0 · 1, fx_cube): 반짝 조각 · 불티 · 번쩍 별 심 */
    public static final String GLYPH = "rpg:vfx/fx_cube";
    /** 2026-11-15 둥근 덩이 (빛 받은 공 · 뜨거운 덮개 — fx_orb): 불덩이 · 연기 · 폭발 심 */
    public static final String ORB = "rpg:vfx/fx_orb";
    /** 2026-11-15 불꽃 혀 (휘는 계단 불꽃 · 아래 흰 심 — fx_flame, 쥔 곳 = 밑 (8, 0, 8) · 높이 28.4 픽셀) */
    public static final String FLAME = "rpg:vfx/fx_flame";
    /** 2026-11-15 금빛 문 (모델 하나 — 금빛 물결 판 · 금 테 · 보석 넷 색 · 햇살 가시, gold_gate: 테 반지름 7.25 픽셀 · 얇은 축 Z) */
    public static final String GATE = "rpg:vfx/gold_gate";
    /** 2026-11-15 금 사슬 고리 (gold_link — 긴 축 Y 11.2 픽셀) */
    public static final String LINK = "rpg:vfx/gold_link";
    public static final double[] WHITE = {1.0, 1.0, 1.0};
    /** 연기 (짙은 붉은 잿빛 — 폭발의 번쩍임 뒤 대비) */
    public static final double[] SMOKE = {0.32, 0.17, 0.17};

    /**
     * 2026-11-15 뜨거운 심 색 (물들임 1): 흰빛 쪽으로 — 따뜻한 색 (빨강 ≥ 파랑) 은 크림 · 노랑 쪽, 차가운 색은 하늘빛 쪽 (한 색을 밝기만 바꾸지 않게)
     */
    public static double[] hot(double[] c) {
        boolean warm = c[0] >= c[2];
        double[] w = warm ? new double[] {1.0, 0.95, 0.8} : new double[] {0.9, 0.97, 1.0};
        double k = 0.72;
        return new double[] {c[0] + (w[0] - c[0]) * k, c[1] + (w[1] - c[1]) * k, c[2] + (w[2] - c[2]) * k};
    }

    /** 상자 하나: 가운데 c · 회전 rot (모델 y = 긴 축) · 크기 (칸) · 색 — 처음부터 그 모습 (심 = hot(색)) */
    public static Vfx.Prop box(Vfx.Fx fx, Vec3 c, Quaternionf rot, double sx, double sy, double sz, double[] color) {
        return fx.prop(CUBE, c, p -> {
            p.grip(8, 8, 8).bright();
            p.tint(color, hot(color));
            p.to(c, rot, 1, 0);
            p.stretch(sx, sy, sz, 0);
        });
    }

    /** 2026-11-15 둥근 덩이 하나 (fx_orb — 지름 size 칸 · 색 color · 빛 받는 쪽 덮개 hot) */
    public static Vfx.Prop orb(Vfx.Fx fx, Vec3 c, Quaternionf rot, double size, double[] color, double[] hot) {
        return fx.prop(ORB, c, p -> {
            p.grip(8, 8, 8).bright();
            p.tint(color, hot);
            p.to(c, rot, 1, 0);
            p.stretch(size, size, size, 0);
        });
    }

    /** 2026-11-15 보석 상자 하나 (fx_cube — 한 변 size 칸 · 색 color · 심 hot(색)) */
    public static Vfx.Prop crystal(Vfx.Fx fx, Vec3 c, Quaternionf rot, double size, double[] color) {
        return fx.prop(GLYPH, c, p -> {
            p.grip(8, 8, 8).bright();
            p.tint(color, hot(color));
            p.to(c, rot, 1, 0);
            p.stretch(size, size, size, 0);
        });
    }

    /** 그림 상자 하나 (몸 색 color · 무늬 색 glyph — 흰빛에 가깝게) */
    public static Vfx.Prop boxG(Vfx.Fx fx, Vec3 c, Quaternionf rot, double sx, double sy, double sz, double[] color, double[] glyph) {
        return fx.prop(GLYPH, c, p -> {
            p.grip(8, 8, 8).bright();
            p.tint(color, glyph);
            p.to(c, rot, 1, 0);
            p.stretch(sx, sy, sz, 0);
        });
    }

    /** y 축이 dir 인 회전 (x · z 는 아무 수직 — side 가 있으면 x 를 그쪽으로) */
    public static Quaternionf along(Vec3 dir, Vec3 side) {
        Vec3 y = V.unit(dir);
        Vec3 s = side == null ? (Math.abs(y.y) < 0.9 ? V.UP : new Vec3(1, 0, 0)) : side;
        Vec3 x = V.sub(s, V.scale(y, V.dot(s, y)));
        if (V.len(x) < 1e-4) x = Math.abs(y.y) < 0.9 ? V.cross(y, V.UP) : V.cross(y, new Vec3(1, 0, 0));
        x = V.unit(x);
        Vec3 z = V.unit(V.cross(x, y));
        return Vfx.basis(x, y, z);
    }

    // ------------------------------------------------------------ 빛 줄

    /**
     * 빛 줄 (from → to · 굵기 thick): 세 마디 (머리 쪽 to 가 굵고 꼬리 from 쪽이 가늘게) · 흰 심지 — 꼬리부터 가늘어지며 사라짐 (life 틱 — 2026-11-15 로컬
     * 미리보기 2판)
     */
    public static void beam(ServerLevel level, Vec3 from, Vec3 to, double thick, double[] color, int life) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        if (len < 0.05) return;
        Quaternionf rot = along(d, null);
        int L = Math.max(2, life);
        int n = 3;
        Vfx.Fx fx = RuneProps.fx(level, V.lerp(from, to, 0.5), L + 4);
        for (int i = 0; i < n; i++) {
            Vec3 a = V.lerp(from, to, i / (double) n);
            Vec3 b = V.lerp(from, to, (i + 1) / (double) n);
            Vec3 mid = V.lerp(a, b, 0.5);
            double seg = len / n * 1.02;
            double th = thick * (0.45 + 0.55 * (i + 1) / n);
            int li = Math.max(2, (int) Math.round(L * (0.55 + 0.45 * (i + 1) / n)));
            // 2026-11-15 HD: 흰 심지 상자 대신 줄 상자의 뜨거운 심 겹 (상자 수 반)
            Vfx.Prop o = box(fx, mid, rot, th, seg, th, color);
            fx.on(1, () -> o.stretch(0.0, seg * 0.96, 0.0, li - 1));
            fx.on(li + 1, o::remove);
        }
    }

    // ------------------------------------------------------------ 호 (베기)

    /**
     * 빛 호 2판 (2026-11-15 로컬 미리보기): 가운데 c · 평면 축 u · v · 반지름 r · 각 a0 → a1 을 마디 segs 개로 — sweep 틱 동안 차례로 나타나는 세 겹 초승달 (겉 색
     * 넓게 · 가운데 뜨거운 색 · 흰 심지, 가운데가 굵고 양끝이 가늚) · 그어진 차례대로 꼬리부터 사라짐 (마디마다 life 의 45 ~ 100%)
     */
    public static void arc(ServerLevel level, Vec3 c, Vec3 u, Vec3 v, double r, double a0, double a1, int segs, double thick, double[] color, int sweep, int life) {
        int n = Math.max(4, segs);
        Vfx.Fx fx = RuneProps.fx(level, c, sweep + life + 8);
        double da = (a1 - a0) / n;
        double[] hot = {Math.min(1, color[0] * 0.4 + 0.6), Math.min(1, color[1] * 0.4 + 0.5), Math.min(1, color[2] * 0.4 + 0.3)};
        for (int i = 0; i < n; i++) {
            double a = a0 + da * (i + 0.5);
            Vec3 radial = V.add(V.scale(u, Math.cos(a)), v, Math.sin(a));
            Vec3 tan = V.add(V.scale(u, -Math.sin(a)), v, Math.cos(a));
            if (da < 0) tan = V.scale(tan, -1);
            Quaternionf rot = Vfx.basis(radial, tan, V.unit(V.cross(radial, tan))); // 오른손 (da < 0 이면 tan 이 뒤집힘)
            double w = thick * (0.25 + 0.75 * Math.pow(Math.sin(Math.PI * (i + 0.5) / n), 0.7));
            double segLen = Math.abs(da) * r * 1.12;
            Vec3 p = V.add(c, radial, r);
            int li = Math.max(2, (int) Math.round(life * (0.45 + 0.55 * i / (double) n)));
            int at = sweep * i / n;
            fx.on(at, () -> {
                Vfx.Prop o = box(fx, p, rot, w * 0.3, segLen * 0.5, w * 0.25, color);
                Vfx.Prop m = box(fx, V.add(p, radial, w * 0.16), rot, w * 0.2, segLen * 0.5, w * 0.2, hot);
                o.stretch(w, segLen, w * 0.4, 1);
                m.stretch(w * 0.5, segLen * 1.01, w * 0.42, 1);
                fx.on(fx.age + 2, () -> {
                    o.stretch(0.0, segLen * 0.9, 0.0, li);
                    m.stretch(0.0, segLen * 0.95, 0.0, Math.max(2, li - 1));
                });
                fx.on(fx.age + li + 3, () -> {
                    o.remove();
                    m.remove();
                });
            });
        }
    }

    // ------------------------------------------------------------ 폭발 · 섬광

    /**
     * 폭발 2판 (가운데 c · 크기 size 칸 — 2026-11-15 로컬 미리보기에서 맞춤, 사용자 "너 하고 싶은거 다 해봐"): 번쩍 별 (흰 가는 긴 상자 셋 · 3틱) · 흰 심 (돌며
     * 꺼짐) · 불덩이 상자 넷 (뜨거운 색 둘 · 겉 색 둘 — 커졌다 돌며 떠올라 줄어듦) · 가시 열둘 (겉 색 + 흰 심지 — 2틱에 뻗었다 끝으로 줄어듦) · 불티 열 (솟았다
     * 떨어지며 작아짐)
     */
    public static void burst(ServerLevel level, Vec3 c, double size, double[] color, double[] core, int life) {
        int L = Math.max(5, life);
        Vfx.Fx fx = RuneProps.fx(level, c, L + 10);
        double[] hot = {Math.min(1, color[0] * 0.5 + core[0] * 0.5), Math.min(1, color[1] * 0.5 + core[1] * 0.5), Math.min(1, color[2] * 0.5 + core[2] * 0.5)};
        double[] gold = {1.0, 0.85, 0.4};
        // 번쩍 별
        Vec3[] axes = {V.unit(new Vec3(1, 0.2, 0.1)), V.unit(new Vec3(-0.2, 1, 0.3)), V.unit(new Vec3(0.3, -0.1, 1))};
        for (Vec3 ax : axes) {
            Vfx.Prop st = box(fx, c, along(ax, null), size * 0.05, size * 0.4, size * 0.05, WHITE);
            fx.on(1, () -> st.stretch(size * 0.04, size * 2.2, size * 0.04, 1));
            fx.on(3, () -> st.stretch(0.0, size * 2.8, 0.0, 2));
            fx.on(6, st::remove);
        }
        // 흰 심
        Quaternionf wq = new Quaternionf().rotateXYZ(0.785F, 0.615F, 0F);
        Vfx.Prop w = orb(fx, c, wq, 0.05, core, WHITE); // 2026-11-15 HD: 둥근 덩이 (빛 받은 공)
        fx.on(1, () -> w.stretch(size * 0.62, size * 0.62, size * 0.62, 2));
        fx.on(4, () -> w.turn(new Quaternionf(wq).rotateY(0.8F), 4).stretch(0.0, 0.0, 0.0, 5));
        fx.on(10, w::remove);
        // 불덩이
        // 2026-11-15 3판: 불덩이 여섯 · 작게 (1.1칸까지 — 큰 폭발에서 큰 상자로 보였음) · 둘은 속 색
        for (int k = 0; k < 6; k++) {
            Quaternionf rq = new Quaternionf().rotateXYZ((float) (Kit.rand() * 3), (float) (Kit.rand() * 3), (float) (Kit.rand() * 3));
            Vec3 off = V.scale(new Vec3(Kit.rand() - 0.5, Kit.rand() * 0.6 - 0.2, Kit.rand() - 0.5), size * 0.45);
            double sz = Math.min(1.1, size * (0.3 + Kit.rand() * 0.25));
            double[] fc = k < 2 ? core : color;
            Vfx.Prop f = orb(fx, V.add(c, off), rq, 0.05, fc, hot(fc)); // 2026-11-15 HD: 상자 대신 둥근 불덩이
            Vec3 axis = V.unit(new Vec3(Kit.rand() + 0.01, Kit.rand(), Kit.rand()));
            Quaternionf rq2 = new Quaternionf(rq).rotateAxis(1.4F, (float) axis.x, (float) axis.y, (float) axis.z);
            fx.on(1, () -> f.stretch(sz, sz, sz, 3).move(V.add(c, off, 1.6), 3));
            fx.on(4, () -> f.turn(rq2, L - 3).move(V.up(V.add(c, off, 2.2), size * 0.25), L - 3).stretch(0.0, 0.0, 0.0, L - 3));
            fx.on(L + 2, f::remove);
        }
        // 2026-11-15 연기 넷 (짙은 색 — 번쩍임 뒤 대비, 큰 폭발만): 3틱에 가운데서 솟으며 부풀었다 돌며 줄어듦
        if (size >= 1.5) for (int k = 0; k < 4; k++) {
            Quaternionf rq = new Quaternionf().rotateXYZ((float) (Kit.rand() * 3), (float) (Kit.rand() * 3), (float) (Kit.rand() * 3));
            Quaternionf rq2 = new Quaternionf(rq).rotateY(1.2F);
            Vec3 off = V.scale(new Vec3(Kit.rand() - 0.5, Kit.rand() * 0.4, Kit.rand() - 0.5), size * 0.7);
            double sz = size * (0.26 + Kit.rand() * 0.14);
            fx.on(3, () -> {
                Vfx.Prop m = orb(fx, V.add(c, off), rq, sz * 0.4, SMOKE, lighten(SMOKE, 0.22));
                m.stretch(sz, sz, sz, 3).move(V.up(V.add(c, off, 1.4), size * 0.3), 3);
                // 2026-11-15 게임 영상: 오래 떠 있던 짙은 상자가 흙 덩이처럼 보였음 → 짧게 · 낮게
                int sl = Math.max(4, L / 2);
                fx.on(6, () -> m.move(V.up(V.add(c, off, 1.6), size * 0.45), sl).stretch(0.0, 0.0, 0.0, sl).turn(rq2, sl));
                fx.on(7 + sl, m::remove);
            });
        }
        // 가시 열둘
        for (int k = 0; k < 12; k++) {
            Vec3 d = V.unit(new Vec3(Math.cos(k * 0.524 + Kit.rand() * 0.3), Kit.rand() - 0.25, Math.sin(k * 0.524 + Kit.rand() * 0.3)));
            double len = size * (0.8 + Kit.rand() * 0.5);
            Vec3 mid0 = V.add(c, d, size * 0.3);
            Quaternionf rot = along(d, null);
            Vfx.Prop o = box(fx, mid0, rot, size * 0.08, 0.05, size * 0.08, color);
            Vec3 mid1 = V.add(c, d, size * 0.3 + len * 0.5);
            Vec3 tip = V.add(c, d, size * 0.3 + len * 1.05);
            fx.on(1, () -> o.move(mid1, 2).stretch(size * 0.08, len, size * 0.08, 2));
            fx.on(4, () -> o.move(tip, L - 4).stretch(0.0, 0.0, 0.0, L - 4));
            fx.on(L + 2, o::remove);
        }
        // 불티 열
        for (int k = 0; k < 10; k++) {
            Vec3 d = V.unit(new Vec3(Kit.rand() - 0.5, 0, Kit.rand() - 0.5 + 1e-3));
            double e = size * (0.08 + Kit.rand() * 0.06);
            Vec3 pk = V.up(V.add(c, d, size * (0.6 + Kit.rand() * 0.8)), size * (0.5 + Kit.rand() * 0.6));
            Vec3 end = V.up(V.add(pk, d, size * 0.4), -size * (0.6 + Kit.rand() * 0.4));
            Vfx.Prop em = crystal(fx, c, along(new Vec3(Kit.rand(), Kit.rand() + 0.1, Kit.rand()), null), e, k % 2 == 1 ? gold : hot); // 2026-11-15 보석 불티
            fx.on(1, () -> em.move(pk, 4));
            fx.on(5, () -> em.move(end, L).stretch(0.0, 0.0, 0.0, L));
            fx.on(L + 6, em::remove);
        }
    }

    /**
     * 번쩍 별 2판 (가운데 c · 크기 size — 쏠 때 · 꽂힐 때의 가벼운 번쩍임): 흰 가는 긴 상자 셋 (1틱에 뻗었다 2틱에 사라짐) · 겉 색 가는 긴 상자 둘 · 작은 흰
     * 마름모 (2026-11-15 미리보기: 겉 색 큰 상자는 상자로 보였음)
     */
    public static void star(ServerLevel level, Vec3 c, double size, double[] color) {
        Vfx.Fx fx = RuneProps.fx(level, c, 8);
        Object[][] rays = {{new Vec3(1, 0.25, 0.1), WHITE, 0.035, 2.2}, {new Vec3(-0.25, 1, 0.3), WHITE, 0.035, 2.2}, {new Vec3(0.3, -0.15, 1), WHITE, 0.035, 2.2},
                {new Vec3(1, 1, 0.2), color, 0.05, 1.4}, {new Vec3(-1, 1, -0.3), color, 0.05, 1.4}};
        for (Object[] r : rays) {
            double th = (double) r[2];
            double ln = (double) r[3];
            Vfx.Prop st = box(fx, c, along(V.unit((Vec3) r[0]), null), size * th, size * 0.3, size * th, (double[]) r[1]);
            fx.on(1, () -> st.stretch(size * th, size * ln, size * th, 1));
            fx.on(2, () -> st.stretch(0.0, size * ln * 1.2, 0.0, 2));
            fx.on(5, st::remove);
        }
        Quaternionf q = new Quaternionf().rotateXYZ(0.785F, 0.615F, 0F);
        Vfx.Prop k = crystal(fx, c, q, size * 0.05, color);
        fx.on(1, () -> k.stretch(size * 0.22, size * 0.22, size * 0.22, 1));
        fx.on(2, () -> k.turn(new Quaternionf(q).rotateY(0.9F), 3).stretch(0.0, 0.0, 0.0, 3));
        fx.on(6, k::remove);
    }

    /**
     * 베인 줄 (가운데 c · 방향 dir · 판의 법선 normal = 보는 쪽 · 길이 len): 겉 색 납작한 긴 상자 + 흰 심 — delay 틱에 한쪽 끝에서 1틱 동안 그어짐 → 2틱 뒤 길어지며
     * 가늘어져 사라짐 (2026-11-15 맞은 자리 — 2D 베인 그림 대신)
     */
    public static void slash(ServerLevel level, Vec3 c, Vec3 dir, Vec3 normal, double len, double[] color, int delay) {
        Vfx.Fx fx = RuneProps.fx(level, c, delay + 12);
        Vec3 d = V.unit(dir);
        Vec3 nrm = V.unit(normal);
        Quaternionf rot = along(d, nrm); // x = 법선 (두께) · y = 줄 · z = 보이는 너비
        double w = len * 0.09;
        Vec3 a = V.add(c, d, -len * 0.5);
        fx.on(delay, () -> {
            Vfx.Prop o = box(fx, a, rot, 0.03, 0.05, w, color);
            o.move(c, 1).stretch(0.03, len, w, 1);
            fx.on(fx.age + 2, () -> o.stretch(0.0, len * 1.12, 0.0, 4));
            fx.on(fx.age + 7, o::remove);
        });
    }

    // ------------------------------------------------------------ 고리 (충격파 · 예고)

    /** 땅의 고리 (가운데 c · 반지름 r0 → r1 · 마디 segs · 굵기 thick): 판이 바깥으로 퍼지며 얇아져 사라짐 (life 틱) */
    public static void ring(ServerLevel level, Vec3 c, double r0, double r1, int segs, double thick, double[] color, int life) {
        int n = Math.max(6, segs);
        int L = Math.max(3, life);
        Vfx.Fx fx = RuneProps.fx(level, c, L + 4);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 tan = new Vec3(-Math.sin(a), 0, Math.cos(a));
            Quaternionf rot = Vfx.basis(radial, tan, V.DOWN); // 오른손 (radial × tan = 아래) — 2026-11-15 미리보기: UP 이면 왼손이라 마디가 서 버렸음
            double seg0 = Math.PI * 2 * r0 / n * 1.12, seg1 = Math.PI * 2 * r1 / n * 1.12;
            Vfx.Prop p = box(fx, V.add(c, radial, r0), rot, thick, seg0, thick * 0.35, color);
            fx.on(1, () -> p.move(V.add(c, radial, r1), L - 1).stretch(0.0, seg1, 0.0, L - 1));
            fx.on(L + 1, p::remove);
        }
    }

    /**
     * 머무는 고리 (예고 · 테두리 — 가운데 c · 반지름 r · 마디 segs · 굵기 thick): grow 틱 동안 마디가 돌아가며 솟아 hold 틱 머문 뒤 4틱에 얇아져 사라짐.
     * 흰 심지 줄 (안쪽 · 가늘게) 을 함께
     */
    public static void ringHold(ServerLevel level, Vec3 c, double r, int segs, double thick, double[] color, int grow, int hold) {
        int n = Math.max(8, segs);
        int G = Math.max(1, grow);
        int H = Math.max(1, hold);
        Vfx.Fx fx = RuneProps.fx(level, c, G + H + 8);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 tan = new Vec3(-Math.sin(a), 0, Math.cos(a));
            Quaternionf rot = Vfx.basis(radial, tan, V.DOWN); // 오른손 (radial × tan = 아래) — 2026-11-15 미리보기: UP 이면 왼손이라 마디가 서 버렸음
            double seg = Math.PI * 2 * r / n * 1.1;
            int at = G * i / n;
            fx.on(at, () -> {
                Vfx.Prop o = box(fx, V.add(c, radial, r), rot, 0.02, seg * 0.3, 0.02, color);
                o.stretch(thick, seg, thick * 0.3, 2);
                fx.on(G + H, () -> o.stretch(0.0, seg, 0.0, 4));
                fx.on(G + H + 5, o::remove);
            });
        }
    }

    /**
     * 하늘 고리 (2026-11-15 게임 영상: 눕힌 고리를 아래에서 보면 바닥 면이 어둡게 — 마크 면 밝기 0.5 — 그려져 검붉었음 → 세운 띠): 가운데 c · 반지름 r · 띠 높이
     * height — grow 틱 동안 마디가 돌아가며 솟아 hold 틱 머문 뒤 4틱에 낮아져 사라짐, 안쪽에 흰 심지 띠
     */
    public static void halo(ServerLevel level, Vec3 c, double r, int segs, double height, double[] color, int grow, int hold) {
        int n = Math.max(8, segs);
        int G = Math.max(1, grow);
        int H = Math.max(1, hold);
        Vfx.Fx fx = RuneProps.fx(level, c, G + H + 8);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            Vec3 radial = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 tan = new Vec3(-Math.sin(a), 0, Math.cos(a));
            Quaternionf rot = Vfx.basis(radial, tan, V.DOWN); // x = 바깥 (두께) · y = 둘레 · z = 아래 (띠 높이)
            double seg = Math.PI * 2 * r / n * 1.1;
            int at = G * i / n;
            fx.on(at, () -> {
                Vfx.Prop o = box(fx, V.add(c, radial, r), rot, height * 0.12, seg * 0.3, 0.02, color);
                o.stretch(height * 0.12, seg, height, 2);
                fx.on(G + H, () -> o.stretch(height * 0.12, seg, 0.0, 4));
                fx.on(G + H + 5, o::remove);
            });
        }
    }

    // ------------------------------------------------------------ 2026-11-15 금빛 문 · 사슬 (아스널 킹)

    /** 색을 흰빛과 섞음 (k = 흰빛 몫) */
    public static double[] lighten(double[] c, double k) {
        return new double[] {c[0] + (1 - c[0]) * k, c[1] + (1 - c[1]) * k, c[2] + (1 - c[2]) * k};
    }

    /**
     * 금빛 문 2026-11-15 HD (사용자 "퀄이 너무 낮잖아" — 상자 고리 · 판 마흔 개 대신 모델 하나 gold_gate: 금빛 물결 판 · 금 테 · 보석 넷 색 · 햇살 가시): 가운데 c ·
     * 보물이 나오는 쪽 facing · 테 반지름 r 칸. grow 틱 동안 돌며 (−92° → 0°) 커져 열리고 · 번쩍 별, hold 틱 동안 천천히 돎 (26°) → 4틱에 돌며 오므라들어 사라짐.
     * segs 14 넘는 큰 문은 열릴 때 물결 고리 하나가 1.5r 로 번짐 (색 color — 뜨거운 색). 마크 면 밝기: 판이 아래를 보면 아랫면이 어두움 → 부르는 쪽이 문을
     * 과녁 쪽 · 옆으로 세움 (아스널 킹)
     */
    public static void gate(ServerLevel level, Vec3 c, Vec3 facing, double r, int segs, double[] color, int grow, int hold) {
        Vec3 f = V.len(facing) < 1e-6 ? new Vec3(0, 0, 1) : V.unit(facing);
        Vec3 u = Math.abs(f.y) > 0.95 ? new Vec3(1, 0, 0) : V.unit(V.rightOf(V.flat(f)));
        Vec3 v = V.unit(V.cross(u, f));
        Vec3 back = V.scale(f, -1);
        int n = Math.max(8, segs);
        int G = Math.max(1, grow);
        int H = Math.max(1, hold);
        Vfx.Fx fx = RuneProps.fx(level, c, G + H + 12);
        double s = r / (7.25 / 16.0);
        Vfx.Prop g = fx.prop(GATE, c, p -> {
            p.grip(8, 8, 8).bright();
            p.to(c, roll(u, v, back, -1.6), s * 0.05, 0);
        });
        fx.on(1, () -> g.to(c, roll(u, v, back, 0), s, G));
        fx.on(1 + G, () -> g.turn(roll(u, v, back, 0.45), H));
        fx.on(1 + G + H, () -> g.to(c, roll(u, v, back, 1.4), s * 0.02, 4));
        fx.on(G + H + 6, g::remove);
        double[] hot = hot(color);
        // 물결 고리 (큰 문만)
        if (n > 14) {
            int m = 20;
            double seg = Math.PI * 2 * r / m * 1.12;
            for (int i = 0; i < m; i++) {
                double a = Math.PI * 2 * i / m;
                Vec3 radial = V.add(V.scale(u, Math.cos(a)), v, Math.sin(a));
                Vec3 tan = V.add(V.scale(u, -Math.sin(a)), v, Math.cos(a));
                Quaternionf rot = Vfx.basis(radial, tan, back);
                fx.on(1 + G, () -> {
                    Vfx.Prop w = box(fx, V.add(c, radial, r), rot, r * 0.07, seg, 0.05, hot);
                    w.move(V.add(c, radial, r * 1.55), 7).stretch(0.0, seg * 1.55, 0.0, 7);
                    fx.on(fx.age + 8, w::remove);
                });
            }
        }
        fx.on(1 + G / 2, () -> star(level, c, r * 1.1, color));
    }

    /** 2026-11-15 판의 둘레 돌림 (금빛 문): 모델 x = u · y = v 를 축 back 둘레로 a 라디안 돌린 회전 */
    static Quaternionf roll(Vec3 u, Vec3 v, Vec3 back, double a) {
        Vec3 x = V.add(V.scale(u, Math.cos(a)), v, Math.sin(a));
        Vec3 y = V.add(V.scale(u, -Math.sin(a)), v, Math.cos(a));
        return Vfx.basis(x, y, back);
    }

    /**
     * 사슬 (a → b · 고리 크기 link 칸) 2026-11-15 HD: 금 사슬 고리 모델 (gold_link — 고리마다 90° 엇갈려 22% 겹침) 이 grow 틱 동안 a 에서 b 로 차례로 뻗어 나옴 →
     * hold 틱 → 3틱에 작아져 사라짐 (색 color 는 쓰지 않음 — 금)
     */
    public static void chain(ServerLevel level, Vec3 a, Vec3 b, double link, double[] color, int grow, int hold) {
        Vec3 dv = V.sub(b, a);
        double L = V.len(dv);
        if (L < 0.1) return;
        Vec3 y = V.scale(dv, 1 / L);
        Vec3 side = Math.abs(y.y) > 0.95 ? new Vec3(1, 0, 0) : V.unit(V.rightOf(V.flat(y)));
        Vec3 side2 = V.unit(V.cross(side, y));
        int n = Math.min(44, Math.max(2, (int) Math.ceil(L / (link * 0.78))));
        int G = Math.max(1, grow);
        int H = Math.max(1, hold);
        double k = link / (11.2 / 16.0);
        Vfx.Fx fx = RuneProps.fx(level, V.lerp(a, b, 0.5), G + H + 8);
        for (int i = 0; i < n; i++) {
            Vec3 p = V.add(a, y, (i + 0.5) * L / n);
            Quaternionf rot = along(y, i % 2 == 0 ? side : side2);
            int at = G * i / n;
            fx.on(at, () -> {
                Vfx.Prop o = fx.prop(LINK, p, q -> {
                    q.grip(8, 8, 8).bright();
                    q.to(p, rot, k * 0.3, 0);
                });
                o.size(k, 1);
                fx.on(G + H, () -> o.size(k * 0.3, 3));
                fx.on(G + H + 4, o::remove);
            });
        }
    }

    // ------------------------------------------------------------ 조각

    /** 조각 n 개 (가운데 c 에서 사방으로 dist 칸 · 길이 size): 길쭉한 상자가 돌며 날아가 작아져 사라짐 (life 틱) */
    public static void shards(ServerLevel level, Vec3 c, int n, double dist, double size, double[] color, int life) {
        int L = Math.max(3, life);
        Vfx.Fx fx = RuneProps.fx(level, c, L + 4);
        for (int i = 0; i < n; i++) {
            Vec3 d = V.unit(new Vec3(Math.cos(i * 2.399) * (0.6 + Kit.rand() * 0.4), Kit.rand() * 0.9 - 0.2, Math.sin(i * 2.399) * (0.6 + Kit.rand() * 0.4)));
            Quaternionf rot = along(d, null);
            Vfx.Prop p = box(fx, V.add(c, d, size * 0.3), rot, size * 0.18, size, size * 0.1, color);
            Quaternionf spin = new Quaternionf(rot).rotateX((float) (Kit.rand() * 4 - 2));
            fx.on(1, () -> {
                p.move(V.add(c, d, dist * (0.7 + Kit.rand() * 0.6)), L - 1);
                p.turn(spin, L - 1);
                p.stretch(0.0, size * 0.3, 0.0, L - 1);
            });
            fx.on(L + 1, p::remove);
        }
    }

    // ------------------------------------------------------------ 불꽃

    /**
     * 불꽃 5판 2026-11-15 HD (불꽃 혀 모델 fx_flame — 상자 대신 휘는 계단 불꽃 · 아래 흰 심 · 위로 붉어짐): 발 foot · 높이 h 칸 — 혀 셋 (겉 색 크게 → 섞은 색 →
     * 속 색 작게 · 0 · 1 · 2틱) 이 2틱에 솟아 커졌다 돌며 떠올라 작아짐 (life 틱)
     */
    public static void flame(ServerLevel level, Vec3 foot, double h, double[] outer, double[] inner, int life) {
        int L = Math.max(4, life);
        Vfx.Fx fx = RuneProps.fx(level, foot, L + 6);
        double[] mid = Vfx.mix(outer, inner, 0.5);
        double[][] cols = {outer, mid, inner};
        double[] hk = {1.0, 0.7, 0.48};
        double[][] off = {{0, 0}, {-0.2, 0.12}, {0.18, -0.14}};
        int[] dl = {0, 1, 2};
        double yaw = Kit.rand() * Math.PI * 2;
        for (int i = 0; i < 3; i++) {
            double[] col = cols[i];
            double hh = h * hk[i];
            double k = hh / (28.4 / 16.0);
            int li = Math.max(3, L - dl[i]);
            double c = Math.cos(yaw), sn = Math.sin(yaw);
            Vec3 base = V.add(foot, new Vec3((off[i][0] * c - off[i][1] * sn) * h, 0, (off[i][0] * sn + off[i][1] * c) * h));
            Quaternionf q0 = new Quaternionf().rotateY((float) (yaw + i * 2.1));
            Quaternionf q1 = new Quaternionf(q0).rotateY((float) ((i % 2 == 0 ? 1 : -1) * 0.9));
            fx.on(dl[i], () -> {
                Vfx.Prop o = fx.prop(FLAME, base, p -> {
                    p.grip(8, 0, 8).bright();
                    p.tint(col, hot(col));
                    p.to(base, q0, k * 0.3, 0);
                });
                o.to(base, q0, k, 2);
                fx.on(fx.age + 2, () -> o.to(V.up(base, hh * 0.3), q1, k * 0.12, li - 2));
                fx.on(fx.age + li + 1, o::remove);
            });
        }
    }

    /**
     * 2026-11-15 땅 마법진 (모델 하나 — sigil_crimson · sigil_gold · sigil_forge: 윗면에 그린 빛 고리 · 룬 띠 · 직업 도형 · 문장, 마크 면 밝기 1): 가운데 c (땅 위 0.04칸쯤) ·
     * 반지름 r 칸 — grow 틱 동안 돌며 (−70°) 커지고, hold 틱 동안 spin 도 천천히 돎 → 5틱에 돌며 오므라듦
     */
    public static Vfx.Prop sigil(ServerLevel level, Vec3 c, double r, String model, int grow, int hold, double spin) {
        int G = Math.max(1, grow);
        int H = Math.max(1, hold);
        Vfx.Fx fx = RuneProps.fx(level, c, G + H + 10);
        double s = r * 2.0;
        double yaw = Kit.rand() * Math.PI * 2;
        double sp = Math.toRadians(Math.max(-170, Math.min(170, spin)));
        Vfx.Prop p = fx.prop(model, c, q -> {
            q.grip(8, 8, 8).bright();
            q.to(c, new Quaternionf().rotateY((float) (yaw - 1.2)), s * 0.12, 0);
        });
        fx.on(1, () -> p.to(c, new Quaternionf().rotateY((float) yaw), s, G));
        fx.on(1 + G, () -> p.turn(new Quaternionf().rotateY((float) (yaw + sp)), H));
        fx.on(1 + G + H, () -> p.to(c, new Quaternionf().rotateY((float) (yaw + sp + 0.9)), s * 0.04, 5));
        fx.on(G + H + 7, p::remove);
        return p;
    }

    /** 땅에 그은 줄 (a → b · 굵기 thick · 흰 심): grow 틱 동안 a 에서 b 로 뻗음 → hold 틱 → 4틱에 가늘어져 사라짐 (2026-11-15 소드 생추어리 마법진 육망성) */
    public static void line(ServerLevel level, Vec3 a, Vec3 b, double thick, double[] color, int grow, int hold) {
        Vec3 d = V.sub(b, a);
        double len = V.len(d);
        if (len < 0.05) return;
        Vec3 y = V.scale(d, 1 / len);
        Vec3 x = V.cross(y, V.UP);
        if (V.len(x) < 1e-3) return;
        Quaternionf rot = Vfx.basis(V.unit(x), y, V.UP); // 오른손 (x × y = 위) — 두께가 위아래
        int G = Math.max(1, grow), H = Math.max(1, hold);
        Vec3 mid = V.lerp(a, b, 0.5);
        Vfx.Fx fx = RuneProps.fx(level, mid, G + H + 8);
        Vfx.Prop o = box(fx, a, rot, thick, 0.05, thick * 0.3, color);
        fx.on(1, () -> o.move(mid, G).stretch(thick, len, thick * 0.3, G));
        fx.on(1 + G + H, () -> o.stretch(0.0, len, 0.0, 4));
        fx.on(G + H + 6, o::remove);
    }
}
