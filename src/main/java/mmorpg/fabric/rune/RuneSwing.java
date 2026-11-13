package mmorpg.fabric.rune;

import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 2026-11-13 일반 · 희귀 · 고귀 룬의 3D 무기 휘두르기 · 바위 (사용자 "이재 다른 룬들도 모션하고 이펙트 추가하자" — 초월 · 영웅 다음): 룬 전용 검 · 도끼 · 낫이
 * 쓴 사람의 몸 둘레를 크게 한 번 휘두름 (몸을 따라감 — 틱마다 다시 놓음), 땅에서 솟아 튀는 바위 · 몸 둘레를 도는 바위.
 * <p>휘두르는 면: 앞 f 에서 w 쪽으로 a 도 (w = 오른쪽을 앞 둘레로 tilt 도 돌린 것 — tilt 0 = 가로 (a + 가 오른쪽), 90 = 세로 (a + 가 위),
 * 45 = 오른쪽 위 ↔ 왼쪽 아래 대각선). a 가 앞 (0) 을 지나는 틱이 피해 틱이 되게 씀.
 */
final class RuneSwing {
    private RuneSwing() {}

    /** 휘두르는 면 위의 방향: 앞 f 에서 w 쪽으로 a 도 */
    static Vec3 planeDir(Vec3 f, double tilt, double a) {
        double t = Math.toRadians(tilt);
        Vec3 w = V.add(V.scale(V.rightOf(f), Math.cos(t)), V.UP, Math.sin(t));
        double r = Math.toRadians(a);
        return V.unit(V.add(V.scale(f, Math.cos(r)), w, Math.sin(r)));
    }

    /** 휘두르는 면의 법선 (검의 넓은 면 — 면이 휘두르는 면에 누움) */
    static Vec3 planeFace(Vec3 f, double tilt) {
        double t = Math.toRadians(tilt);
        Vec3 w = V.add(V.scale(V.rightOf(f), Math.cos(t)), V.UP, Math.sin(t));
        return V.unit(V.cross(f, w));
    }

    /**
     * 룬 전용 검 한 번 (len 칸): t0 틱에 a0 쪽에서 커지며 나타나 ticks 틱 동안 a1 까지 휘두름 (몸 높이 h 의 축에서 칼자루까지 reach 칸 · 빠르기 ease) →
     * hold 틱 머물다 칼끝 쪽으로 밀리며 작아져 사라짐. 쓴 사람이 죽거나 나가면 바로 작아짐
     */
    static RuneSwords.Blade sword(ServerPlayer sp, String life, double h, double reach, double len, double tilt, double a0, double a1, int t0, int ticks, int hold,
            String ease, double[] glow, double[] core) {
        ServerLevel level = sp.level();
        Vfx.Fx fx = RuneProps.fx(level, sp.position(), t0 + ticks + hold + 12);
        RuneSwords.Frame fr = RuneBlade.bodyFrame(sp, h);
        Vec3 f0 = fr.fwd();
        Vec3 d0 = planeDir(f0, tilt, a0);
        RuneSwords.Blade b = RuneSwords.blade(fx, V.add(fr.pivot(), d0, reach), d0, planeFace(f0, tilt), len, t0 > 0 ? 0.02 : 0.35, glow, core);
        fx.on(Math.max(1, t0), () -> b.grow(1, 2));
        RuneSwords.swing(b, t0, ticks, fr, reach, (f, a) -> planeDir(f, tilt, a), f -> planeFace(f, tilt), a0, a1, ease);
        retire(fx, b, sp, life, t0 + ticks + hold);
        return b;
    }

    /** 끝: 칼끝 쪽으로 밀리며 작아져 사라짐 (end 틱 — 그 전에 쓴 사람이 죽으면 그때) */
    static void retire(Vfx.Fx fx, RuneSwords.Blade b, ServerPlayer sp, String life, int end) {
        boolean[] done = {false};
        Runnable go = () -> {
            if (done[0]) return;
            done[0] = true;
            int age = fx.age();
            b.grow(0.1, 5);
            b.prop.move(V.add(b.hilt(), b.dir(), b.reach() * 0.6), 5);
            fx.on(age + 6, b::remove);
        };
        fx.each(1, end, 1, t -> {
            if (!RuneKit.sameLife(sp, life)) go.run();
        });
        fx.on(end, go);
    }

    /**
     * 룬 전용 낫 한 번 (자루 길이 len 칸): 자루가 몸 높이 h 의 축에서 바깥으로 뻗은 채 a0 → a1 로 휩씀 (날 = 휩쓰는 쪽 앞) — t0 · ticks · hold 는 sword 와 같음
     */
    static Vfx.Prop scythe(ServerPlayer sp, String life, double h, double len, double tilt, double a0, double a1, int t0, int ticks, int hold, String ease,
            double[] glow, double[] core) {
        ServerLevel level = sp.level();
        Vfx.Fx fx = RuneProps.fx(level, sp.position(), t0 + ticks + hold + 12);
        RuneSwords.Frame fr = RuneBlade.bodyFrame(sp, h);
        double s = RuneProps.scaleFor(len, RuneProps.SCYTHE_LEN);
        double mid = len * 0.5;
        double sign = Math.signum(a1 - a0) == 0 ? 1 : Math.signum(a1 - a0);
        Vec3 f0 = fr.fwd();
        Vec3 d0 = planeDir(f0, tilt, a0);
        Vfx.Prop p = RuneProps.prop(fx, RuneProps.SCYTHE, V.add(fr.pivot(), d0, mid), RuneProps.SCYTHE_MID, scytheRot(f0, tilt, a0, sign),
                s * (t0 > 0 ? 0.02 : 0.35), glow, core);
        fx.on(Math.max(1, t0), () -> p.size(s, 2));
        int n = Math.max(1, ticks);
        for (int i = 0; i <= n; i++) {
            final int ii = i;
            fx.on(t0 + i, () -> {
                double a = a0 + (a1 - a0) * RuneSwords.ease(ii / (double) n, ease);
                Vec3 f = fr.fwd();
                p.to(V.add(fr.pivot(), planeDir(f, tilt, a), mid), scytheRot(f, tilt, a, sign), -1, ii == 0 ? 0 : 1);
            });
        }
        int end = t0 + ticks + hold;
        boolean[] done = {false};
        Runnable go = () -> {
            if (done[0]) return;
            done[0] = true;
            int age = fx.age();
            p.size(s * 0.1, 5);
            fx.on(age + 6, p::remove);
        };
        fx.each(1, end, 1, t -> {
            if (!RuneKit.sameLife(sp, life)) go.run();
        });
        fx.on(end, go);
        return p;
    }

    /** 낫의 회전: 자루 (+Y) = 면 위의 a 방향 · 날 (−Z) = 휩쓰는 쪽 (sign — a 가 늘면 +) */
    static Quaternionf scytheRot(Vec3 f, double tilt, double a, double sign) {
        Vec3 d = planeDir(f, tilt, a);
        Vec3 t = planeDir(f, tilt, a + 90 * sign);
        return RuneProps.along(d, V.cross(t, d));
    }

    /**
     * 룬 전용 도끼 한 번 (크기 몫 fit — RuneAxe.fitOf): 자루가 몸 높이 h 의 축에서 a0 → a1 로 (면 tilt) 휘둘러짐 — 날이 휘두르는 쪽 앞. 끝 자세로 hold 틱
     * 박혀 있다가 가라앉으며 작아져 사라짐
     */
    static Vfx.Prop axe(ServerPlayer sp, String life, double h, double reach, double fit, double tilt, double a0, double a1, int t0, int ticks, int hold,
            String ease, double[] glow, double[] core) {
        ServerLevel level = sp.level();
        Vfx.Fx fx = RuneProps.fx(level, sp.position(), t0 + ticks + hold + 14);
        RuneSwords.Frame fr = RuneBlade.bodyFrame(sp, h);
        double sign = Math.signum(a1 - a0) == 0 ? 1 : Math.signum(a1 - a0);
        Vec3 f0 = fr.fwd();
        Vec3 d0 = planeDir(f0, tilt, a0);
        double[] grip = RuneAxe.gripOf("rare");
        Vfx.Prop p = RuneProps.prop(fx, RuneAxe.RUNE_AXE, V.add(fr.pivot(), d0, reach), grip, RuneAxe.axeRot(d0, planeDir(f0, tilt, a0 + 90 * sign)),
                fit * (t0 > 0 ? 0.02 : 0.4), glow, core);
        fx.on(Math.max(1, t0), () -> p.size(fit, 2));
        int n = Math.max(1, ticks);
        Vec3[] last = {null};
        for (int i = 0; i <= n; i++) {
            final int ii = i;
            fx.on(t0 + i, () -> {
                double a = a0 + (a1 - a0) * RuneSwords.ease(ii / (double) n, ease);
                Vec3 f = fr.fwd();
                Vec3 d = planeDir(f, tilt, a);
                last[0] = V.add(fr.pivot(), d, reach);
                p.to(last[0], RuneAxe.axeRot(d, planeDir(f, tilt, a + 90 * sign)), -1, ii == 0 ? 0 : 1);
            });
        }
        int end = t0 + ticks + hold;
        boolean[] done = {false};
        Runnable go = () -> {
            if (done[0]) return;
            done[0] = true;
            int age = fx.age();
            if (last[0] != null) p.move(V.up(last[0], -0.5), 8);
            p.size(fit * 0.15, 8);
            fx.on(age + 9, p::remove);
        };
        fx.each(1, end, 1, t -> {
            if (!RuneKit.sameLife(sp, life)) go.run();
        });
        fx.on(end, go);
        return p;
    }

    // ------------------------------------------------------------ 바위

    /**
     * 땅에서 솟아 튀는 바위 (3D boulder — 틈의 빛 = col): c 둘레 (앞 f 기준 ±spread/2 도 · 반지름 r0 ~ r1) 의 땅에서 n 개가 솟아 (2틱 — 0.4 ~ 1칸) 바깥으로
     * 구르며 날아가 (out 칸 · 포물선) 떨어져 가라앉음. size = 지름 칸 (s0 ~ s1)
     */
    static void erupt(ServerLevel level, Vec3 c, Vec3 f, int n, double spread, double r0, double r1, double s0, double s1, double out, double[] col) {
        Vfx.Fx fx = RuneProps.fx(level, c, 40);
        for (int i = 0; i < n; i++) {
            double a = -spread / 2 + (i + 0.5) / n * spread + (Kit.rand() - 0.5) * (spread / n) * 0.6;
            Vec3 d = RuneKit.turn(f, a);
            Vec3 b = V.add(c, d, r0 + Kit.rand() * (r1 - r0));
            final Vec3 ground = new Vec3(b.x, RuneKit.groundAt(level, b, c.y), b.z);
            final double hgt = 0.4 + Kit.rand() * 0.6;
            final double sc = RuneProps.scaleFor(s0 + Kit.rand() * (s1 - s0), RuneProps.BOULDER_D);
            final long seed = (long) (Kit.rand() * 1e9) + i;
            Vfx.Prop p = RuneProps.prop(fx, RuneProps.BOULDER, V.up(ground, -0.4), new double[] {8, 8, 8}, RuneProps.tumble(seed, 0, 0), sc * 0.4, col);
            fx.on(1, () -> p.to(V.up(ground, hgt), RuneProps.tumble(seed, 2, 10), sc, 2));
            final Vec3 from = V.up(ground, hgt);
            final Vec3 land = PairKit.onGround(level, V.add(ground, d, out * (0.6 + Kit.rand() * 0.6)));
            final int fly = 7 + (int) (Kit.rand() * 4);
            final double arc = 0.9 + Kit.rand() * 0.8;
            for (int t = 1; t <= fly; t++) {
                final double u = t / (double) fly;
                final int tt = t;
                fx.on(3 + t, () -> p.to(V.add(V.lerp(from, V.up(land, 0.2), u), V.UP, arc * 4 * u * (1 - u)), RuneProps.tumble(seed, 4 + tt, 28), -1, 1));
            }
            fx.on(3 + fly + 1, () -> SkillFx.particle(level, net.minecraft.core.particles.ParticleTypes.DUST_PLUME, V.up(land, 0.3), 4, 0.3, 0.05));
            fx.on(3 + fly + 5, () -> p.to(V.up(land, -0.5), null, sc * 0.4, 7));
            fx.on(3 + fly + 13, p::remove);
        }
    }
}
