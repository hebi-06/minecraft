package mmorpg.fabric.vfx;

import mmorpg.fabric.rune.RuneProps;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 2026-11-15 단색 블록 볼륨 이펙트 (사용자 "이런식이면 그냥 위르드처럼 해야 할거 같은데" · "그냥 도트처럼 보이는게 문제임" — claude/archer-remake.md):
 * 무늬 없는 흰 상자 (tools/vfx_blocky.py flat_cube — 물들임 0 · 스스로 빛남) 를 물들이고 축마다 늘여 (Vfx.Prop.stretch) 빛 줄 · 호 · 폭발 · 고리 · 조각 · 불꽃을
 * 만듦. 바닐라가 보간으로 부드럽게 그림 · 면에 픽셀 무늬가 없어 크게 띄워도 도트로 보이지 않음. 2D 글꼴 그림 대신 큰 이펙트의 본체.
 */
public final class BlockFx {
    private BlockFx() {}

    public static final String CUBE = "rpg:vfx/flat_cube";
    public static final double[] WHITE = {1.0, 1.0, 1.0};
    /** 연기 (짙은 붉은 잿빛 — 폭발의 번쩍임 뒤 대비) */
    public static final double[] SMOKE = {0.32, 0.17, 0.17};

    /** 상자 하나: 가운데 c · 회전 rot (모델 y = 긴 축) · 크기 (칸) · 색 — 처음부터 그 모습 */
    public static Vfx.Prop box(Vfx.Fx fx, Vec3 c, Quaternionf rot, double sx, double sy, double sz, double[] color) {
        return fx.prop(CUBE, c, p -> {
            p.grip(8, 8, 8).bright();
            p.tint(color);
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
            Vfx.Prop o = box(fx, mid, rot, th, seg, th, color);
            Vfx.Prop k = box(fx, mid, rot, th * 0.4, seg * 1.01, th * 0.4, WHITE);
            fx.on(1, () -> {
                o.stretch(0.0, seg * 0.96, 0.0, li - 1);
                k.stretch(0.0, seg, 0.0, li - 1);
            });
            fx.on(li + 1, () -> {
                o.remove();
                k.remove();
            });
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
                Vfx.Prop m = box(fx, V.add(p, radial, w * 0.12), rot, w * 0.2, segLen * 0.5, w * 0.2, hot);
                Vfx.Prop k = box(fx, V.add(p, radial, w * 0.22), rot, w * 0.1, segLen * 0.5, w * 0.12, WHITE);
                o.stretch(w, segLen, w * 0.4, 1);
                m.stretch(w * 0.55, segLen * 1.01, w * 0.42, 1);
                k.stretch(w * 0.22, segLen * 1.02, w * 0.44, 1);
                fx.on(fx.age + 2, () -> {
                    o.stretch(0.0, segLen * 0.9, 0.0, li);
                    m.stretch(0.0, segLen * 0.95, 0.0, Math.max(2, li - 1));
                    k.stretch(0.0, segLen, 0.0, Math.max(2, li - 2));
                });
                fx.on(fx.age + li + 3, () -> {
                    o.remove();
                    m.remove();
                    k.remove();
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
        Vfx.Prop w = box(fx, c, wq, 0.05, 0.05, 0.05, core);
        fx.on(1, () -> w.stretch(size * 0.62, size * 0.62, size * 0.62, 2));
        fx.on(4, () -> w.turn(new Quaternionf(wq).rotateY(0.8F), 4).stretch(0.0, 0.0, 0.0, 5));
        fx.on(10, w::remove);
        // 불덩이
        // 2026-11-15 3판: 불덩이 여섯 · 작게 (1.1칸까지 — 큰 폭발에서 큰 상자로 보였음) · 둘은 속 색
        for (int k = 0; k < 6; k++) {
            Quaternionf rq = new Quaternionf().rotateXYZ((float) (Kit.rand() * 3), (float) (Kit.rand() * 3), (float) (Kit.rand() * 3));
            Vec3 off = V.scale(new Vec3(Kit.rand() - 0.5, Kit.rand() * 0.6 - 0.2, Kit.rand() - 0.5), size * 0.45);
            double sz = Math.min(1.1, size * (0.3 + Kit.rand() * 0.25));
            Vfx.Prop f = box(fx, V.add(c, off), rq, 0.05, 0.05, 0.05, k < 2 ? core : color);
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
                Vfx.Prop m = box(fx, V.add(c, off), rq, sz * 0.4, sz * 0.4, sz * 0.4, SMOKE);
                m.stretch(sz, sz, sz, 3).move(V.up(V.add(c, off, 1.4), size * 0.3), 3);
                fx.on(6, () -> m.move(V.up(V.add(c, off, 1.7), size * 0.75), L - 2).stretch(0.0, 0.0, 0.0, L - 2).turn(rq2, L - 2));
                fx.on(L + 5, m::remove);
            });
        }
        // 가시 열둘
        for (int k = 0; k < 12; k++) {
            Vec3 d = V.unit(new Vec3(Math.cos(k * 0.524 + Kit.rand() * 0.3), Kit.rand() - 0.25, Math.sin(k * 0.524 + Kit.rand() * 0.3)));
            double len = size * (0.8 + Kit.rand() * 0.5);
            Vec3 mid0 = V.add(c, d, size * 0.3);
            Quaternionf rot = along(d, null);
            Vfx.Prop o = box(fx, mid0, rot, size * 0.07, 0.05, size * 0.07, color);
            Vfx.Prop kk = box(fx, mid0, rot, size * 0.028, 0.05, size * 0.028, WHITE);
            Vec3 mid1 = V.add(c, d, size * 0.3 + len * 0.5);
            Vec3 tip = V.add(c, d, size * 0.3 + len * 1.05);
            fx.on(1, () -> {
                o.move(mid1, 2).stretch(size * 0.07, len, size * 0.07, 2);
                kk.move(mid1, 2).stretch(size * 0.028, len * 1.02, size * 0.028, 2);
            });
            fx.on(4, () -> {
                o.move(tip, L - 4).stretch(0.0, 0.0, 0.0, L - 4);
                kk.move(tip, L - 5).stretch(0.0, 0.0, 0.0, Math.max(2, L - 5));
            });
            fx.on(L + 2, () -> {
                o.remove();
                kk.remove();
            });
        }
        // 불티 열
        for (int k = 0; k < 10; k++) {
            Vec3 d = V.unit(new Vec3(Kit.rand() - 0.5, 0, Kit.rand() - 0.5 + 1e-3));
            double e = size * (0.08 + Kit.rand() * 0.06);
            Vec3 pk = V.up(V.add(c, d, size * (0.6 + Kit.rand() * 0.8)), size * (0.5 + Kit.rand() * 0.6));
            Vec3 end = V.up(V.add(pk, d, size * 0.4), -size * (0.6 + Kit.rand() * 0.4));
            Vfx.Prop em = box(fx, c, along(new Vec3(Kit.rand(), Kit.rand() + 0.1, Kit.rand()), null), e, e, e, k % 2 == 1 ? gold : hot);
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
        Vfx.Prop k = box(fx, c, q, size * 0.05, size * 0.05, size * 0.05, WHITE);
        fx.on(1, () -> k.stretch(size * 0.2, size * 0.2, size * 0.2, 1));
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
        Vec3 front = V.scale(nrm, -0.03); // 흰 심은 보는 쪽으로 조금 앞
        fx.on(delay, () -> {
            Vfx.Prop o = box(fx, a, rot, 0.03, 0.05, w, color);
            Vfx.Prop k = box(fx, V.add(a, front), rot, 0.035, 0.05, w * 0.35, WHITE);
            o.move(c, 1).stretch(0.03, len, w, 1);
            k.move(V.add(c, front), 1).stretch(0.035, len * 1.02, w * 0.35, 1);
            fx.on(fx.age + 2, () -> {
                o.stretch(0.0, len * 1.12, 0.0, 4);
                k.stretch(0.0, len * 1.14, 0.0, 3);
            });
            fx.on(fx.age + 7, () -> {
                o.remove();
                k.remove();
            });
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
                Vfx.Prop k = box(fx, V.up(V.add(c, radial, r - thick * 0.15), thick * 0.12), rot, 0.01, seg * 0.3, 0.01, WHITE);
                o.stretch(thick, seg, thick * 0.3, 2);
                k.stretch(thick * 0.28, seg * 1.02, thick * 0.12, 2);
                fx.on(G + H, () -> {
                    o.stretch(0.0, seg, 0.0, 4);
                    k.stretch(0.0, seg, 0.0, 4);
                });
                fx.on(G + H + 5, () -> {
                    o.remove();
                    k.remove();
                });
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
     * 불꽃 3판 (발 foot · 높이 h — 2026-11-15 로컬 미리보기: 마름모 상자는 상자로 보였음): 세운 긴 상자 넷 (아래 넓은 겉 색 → 위 좁은 속 색 · 흰 심) 이
     * 흔들리며 솟고 가늘어져 사라짐 (life 틱)
     */
    public static void flame(ServerLevel level, Vec3 foot, double h, double[] outer, double[] inner, int life) {
        int L = Math.max(4, life);
        Vfx.Fx fx = RuneProps.fx(level, foot, L + 4);
        double[] hot = {Math.min(1, outer[0] * 0.5 + inner[0] * 0.5), Math.min(1, outer[1] * 0.5 + inner[1] * 0.5), Math.min(1, outer[2] * 0.5 + inner[2] * 0.5)};
        double[][] cols = {outer, hot, inner, WHITE};
        double[] wk = {0.3, 0.22, 0.14, 0.07}, hk = {0.55, 0.75, 0.9, 0.5}, y0 = {0.0, 0.12, 0.25, 0.15};
        for (int i = 0; i < 4; i++) {
            double w = h * wk[i], hh = h * hk[i];
            double yaw = Kit.rand() * Math.PI;
            Quaternionf r = new Quaternionf().rotateY((float) yaw);
            Quaternionf r2 = new Quaternionf().rotateY((float) (yaw + 0.9));
            Vfx.Prop o = box(fx, V.up(foot, h * y0[i] + hh * 0.5), r, w, hh, w, cols[i]);
            Vec3 sway = V.add(foot, new Vec3((Kit.rand() - 0.5) * h * 0.3, h * (y0[i] + 0.45) + hh * 0.5, (Kit.rand() - 0.5) * h * 0.3));
            fx.on(1, () -> o.move(sway, L - 1).turn(r2, L - 1).stretch(0.0, hh * 1.3, 0.0, L - 1));
            fx.on(L + 1, o::remove);
        }
    }
}
