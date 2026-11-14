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

    /** 빛 줄 (from → to · 굵기 thick): 겉 색 + 흰 심지 — life 틱 동안 가늘어지며 사라짐 */
    public static void beam(ServerLevel level, Vec3 from, Vec3 to, double thick, double[] color, int life) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        if (len < 0.05) return;
        Vec3 mid = V.lerp(from, to, 0.5);
        Quaternionf rot = along(d, null);
        int L = Math.max(2, life);
        Vfx.Fx fx = RuneProps.fx(level, mid, L + 3);
        Vfx.Prop outer = box(fx, mid, rot, thick, len, thick, color);
        Vfx.Prop core = box(fx, mid, rot, thick * 0.38, len * 1.01, thick * 0.38, WHITE);
        fx.on(1, () -> {
            outer.stretch(0.0, len * 0.96, 0.0, L - 1);
            core.stretch(0.0, len, 0.0, L - 1);
        });
        fx.on(L + 1, () -> {
            outer.remove();
            core.remove();
        });
    }

    // ------------------------------------------------------------ 호 (베기)

    /**
     * 빛 호: 가운데 c · 평면 축 u · v (단위 · 서로 수직) · 반지름 r · 각 a0 → a1 (라디안, u 에서 v 쪽) 을 마디 segs 개로 — sweep 틱 동안 차례로 나타나
     * (마디마다 굵기 thick · 가운데가 굵고 양끝이 가는 초승달) life 틱 동안 가늘어지며 사라짐
     */
    public static void arc(ServerLevel level, Vec3 c, Vec3 u, Vec3 v, double r, double a0, double a1, int segs, double thick, double[] color, int sweep, int life) {
        int n = Math.max(2, segs);
        Vfx.Fx fx = RuneProps.fx(level, c, sweep + life + 4);
        double da = (a1 - a0) / n;
        Vec3 nrm = V.unit(V.cross(u, v));
        for (int i = 0; i < n; i++) {
            double a = a0 + da * (i + 0.5);
            Vec3 radial = V.add(V.scale(u, Math.cos(a)), v, Math.sin(a));
            Vec3 tan = V.add(V.scale(u, -Math.sin(a)), v, Math.cos(a));
            if (da < 0) tan = V.scale(tan, -1);
            Vec3 p = V.add(c, radial, r);
            double w = thick * (0.3 + 0.7 * Math.sin(Math.PI * (i + 0.5) / n));
            double segLen = Math.abs(da) * r * 1.18;
            Quaternionf rot = Vfx.basis(radial, tan, nrm);
            int at = sweep * i / n;
            int ii = i;
            fx.on(at, () -> {
                Vfx.Prop o = box(fx, p, rot, w * 0.2, segLen * 0.4, w * 0.35, color);
                Vfx.Prop k = box(fx, V.add(p, radial, w * 0.15), rot, w * 0.08, segLen * 0.4, w * 0.15, WHITE);
                o.stretch(w, segLen, w * 0.55, 1);
                k.stretch(w * 0.32, segLen * 1.02, w * 0.3, 1);
                fx.on(fx.age + 2, () -> {
                    o.stretch(0.0, segLen * 0.9, 0.0, life);
                    k.stretch(0.0, segLen, 0.0, life);
                });
                fx.on(fx.age + life + 2, () -> {
                    o.remove();
                    k.remove();
                });
            });
        }
    }

    // ------------------------------------------------------------ 폭발 · 섬광

    /**
     * 폭발 (가운데 c · 크기 size 칸): 흰 심 상자 (45° 돌림) 가 2틱에 커졌다 돌며 꺼짐 · 겉 색 상자 둘 (서로 다르게 돌림) 이 3틱에 커졌다 life 틱 동안 줄어듦 ·
     * 사방으로 빛 줄 여섯
     */
    public static void burst(ServerLevel level, Vec3 c, double size, double[] color, double[] core, int life) {
        int L = Math.max(4, life);
        Vfx.Fx fx = RuneProps.fx(level, c, L + 4);
        Quaternionf r1 = new Quaternionf().rotateXYZ((float) (Kit.rand() * 3), (float) (Kit.rand() * 3), (float) (Kit.rand() * 3));
        Quaternionf r2 = new Quaternionf().rotateXYZ((float) (Kit.rand() * 3), (float) (Kit.rand() * 3), (float) (Kit.rand() * 3));
        Quaternionf r3 = new Quaternionf().rotateXYZ(0.785F, 0.615F, 0F);
        Vfx.Prop a = box(fx, c, r1, 0.05, 0.05, 0.05, color);
        Vfx.Prop b = box(fx, c, r2, 0.05, 0.05, 0.05, color);
        Vfx.Prop w = box(fx, c, r3, 0.05, 0.05, 0.05, core);
        fx.on(1, () -> {
            a.stretch(size * 0.85, size * 0.85, size * 0.85, 3);
            b.stretch(size * 0.7, size * 0.7, size * 0.7, 3);
            w.stretch(size * 0.55, size * 0.55, size * 0.55, 2);
        });
        fx.on(4, () -> {
            a.turn(new Quaternionf(r1).rotateY(1.2F), L - 3).stretch(0.0, 0.0, 0.0, L - 3);
            b.turn(new Quaternionf(r2).rotateX(-1.0F), L - 3).stretch(0.0, 0.0, 0.0, L - 3);
            w.stretch(0.0, 0.0, 0.0, Math.max(2, L / 2));
        });
        fx.on(L + 2, () -> {
            a.remove();
            b.remove();
            w.remove();
        });
        for (int i = 0; i < 6; i++) {
            Vec3 d = V.unit(new Vec3(Kit.rand() - 0.5, Kit.rand() - 0.35, Kit.rand() - 0.5));
            beam(level, V.add(c, d, size * 0.2), V.add(c, d, size * (0.9 + Kit.rand() * 0.5)), size * 0.07, color, Math.max(3, L - 2));
        }
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
            Quaternionf rot = Vfx.basis(radial, tan, V.UP);
            double seg0 = Math.PI * 2 * r0 / n * 1.12, seg1 = Math.PI * 2 * r1 / n * 1.12;
            Vfx.Prop p = box(fx, V.add(c, radial, r0), rot, thick, seg0, thick * 0.35, color);
            fx.on(1, () -> p.move(V.add(c, radial, r1), L - 1).stretch(0.0, seg1, 0.0, L - 1));
            fx.on(L + 1, p::remove);
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

    /** 불꽃 하나 (발 foot · 높이 h): 겉 색 상자 · 속 색 상자가 솟으며 돌고 작아져 사라짐 (life 틱) */
    public static void flame(ServerLevel level, Vec3 foot, double h, double[] outer, double[] inner, int life) {
        int L = Math.max(4, life);
        Vfx.Fx fx = RuneProps.fx(level, foot, L + 4);
        double w = h * 0.38;
        Quaternionf r = new Quaternionf().rotateY((float) (Kit.rand() * Math.PI)).rotateZ(0.785F).rotateX(0.615F);
        Vfx.Prop o = box(fx, V.up(foot, w * 0.5), r, w, w, w, outer);
        Vfx.Prop k = box(fx, V.up(foot, w * 0.45), r, w * 0.55, w * 0.55, w * 0.55, inner);
        Quaternionf r2 = new Quaternionf(r).rotateY(1.4F);
        fx.on(1, () -> {
            o.move(V.up(foot, h), L - 1).turn(r2, L - 1).stretch(0.0, 0.0, 0.0, L - 1);
            k.move(V.up(foot, h * 0.75), L - 1).turn(r2, L - 1).stretch(0.0, 0.0, 0.0, Math.max(2, L - 3));
        });
        fx.on(L + 1, () -> {
            o.remove();
            k.remove();
        });
    }
}
