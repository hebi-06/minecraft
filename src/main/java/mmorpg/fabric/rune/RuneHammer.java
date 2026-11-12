package mmorpg.fabric.rune;

import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-12 룬 전용 망치 (사용자 "망치 3d" — tools/vfx_props.py rune_hammer, 빛 = 계열 색): 도약하며 머리 위로 든 망치 → 착지하며 내려찍음 (거인의 일격 · 심판의
 * 도약), 던져 적 사이를 튀는 도는 망치 (심판의 망치). 축: 자루 = 모델 +Y (손잡이 → 머리), 때리는 면 = ±Z, 넓은 옆면 = ±X.
 */
final class RuneHammer {
    private RuneHammer() {}

    /** 손잡이 → 머리 가운데 (모델 픽셀) */
    static final double REACH_PX = RuneProps.HAMMER_HEAD[1] - RuneProps.HAMMER_GRIP[1];

    /** 머리 위로 든 망치 (몸을 따라감) */
    static final class Held {
        final ServerPlayer sp;
        final Vfx.Fx fx;
        final Vfx.Prop p;
        final double s;
        Vec3 grip;
        boolean done;

        Held(ServerPlayer sp, Vfx.Fx fx, Vfx.Prop p, double s, Vec3 grip) {
            this.sp = sp;
            this.fx = fx;
            this.p = p;
            this.s = s;
            this.grip = grip;
        }
    }

    /** 자루 방향: 위에서 앞 (f) 쪽으로 deg 도 (음수 = 뒤로 젖힘) */
    static Vec3 haft(Vec3 f, double deg) {
        double a = Math.toRadians(deg);
        return V.unit(V.add(V.scale(V.UP, Math.cos(a)), f, Math.sin(a)));
    }

    /**
     * 도약하는 air 틱 동안 머리 위로 든 망치 (길이 len 칸): 손잡이가 머리 위 (몸 + 2칸) · 자루가 뒤로 40° 젖혀졌다 10° 까지 세워짐 (틱마다 몸을 따라감)
     */
    static Held overhead(ServerPlayer sp, ServerLevel level, Vec3 f, int air, double len, double[] glow, double[] core, int extra) {
        Vfx.Fx fx = RuneProps.fx(level, sp.position(), air + extra);
        double s = RuneProps.scaleFor(len, RuneProps.HAMMER_LEN);
        Vec3 g0 = V.add(V.up(sp.position(), 2.0), f, -0.1);
        Vfx.Prop p = RuneProps.prop(fx, RuneProps.HAMMER, g0, RuneProps.HAMMER_GRIP, RuneProps.along(haft(f, -40), V.rightOf(f)), s * 0.3, glow, core);
        Held h = new Held(sp, fx, p, s, g0);
        fx.on(1, () -> p.size(s, 3));
        fx.each(1, air, 1, t -> {
            if (h.done || sp.isRemoved()) return;
            double u = Math.min(1, t / (double) Math.max(1, air));
            h.grip = V.add(V.up(sp.position(), 2.0), f, -0.1 + 0.2 * u);
            p.to(h.grip, RuneProps.along(haft(f, -40 + 30 * u), V.rightOf(f)), -1, 1);
        });
        return h;
    }

    /**
     * 내려찍음: 머리 가운데가 c (땅 위 headY 칸) 에 닿게 위에서 앞으로 휘두름 (3틱 — 길이 len 까지 커짐) → hold 틱 박혀 있다가 가라앉으며 작아져 사라짐
     */
    static void smash(Held h, Vec3 c, Vec3 f, double len, double headY, int hold) {
        h.done = true;
        Vfx.Prop p = h.p;
        double s = RuneProps.scaleFor(len, RuneProps.HAMMER_LEN);
        double reach = REACH_PX / 16.0 * s;
        Vec3 dirEnd = haft(f, 128);
        Vec3 pivot = V.sub(V.up(c, headY), V.scale(dirEnd, reach));
        Vec3 side = V.rightOf(f);
        double[] degs = {35, 85, 128};
        h.fx.keep(hold + 16);
        for (int i = 0; i < degs.length; i++) {
            double k = (i + 1.0) / degs.length;
            Vec3 at = V.add(h.grip, V.sub(pivot, h.grip), k);
            final Vec3 dir = haft(f, degs[i]);
            final double sc = h.s + (s - h.s) * k;
            h.fx.on(h.fx.age() + i, () -> p.to(at, RuneProps.along(dir, side), sc, 1));
        }
        int end = h.fx.age() + degs.length + hold;
        h.fx.on(end, () -> p.to(V.up(pivot, -reach * 0.5), null, s * 0.55, 8));
        h.fx.on(end + 8, p::remove);
    }

    /** 던진 망치 (심판의 망치): 한 번 만든 망치가 튈 때마다 다음 적으로 날아감 */
    static final class Thrown {
        final ServerLevel level;
        final double[] glow;
        final double[] core;
        final double s;
        Vfx.Fx fx;
        Vfx.Prop p;
        double spin;

        Thrown(ServerLevel level, double len, double[] glow, double[] core) {
            this.level = level;
            this.glow = glow;
            this.core = core;
            this.s = RuneProps.scaleFor(len, RuneProps.HAMMER_LEN);
        }

        /** from → to 를 ticks 틱에 도는 망치 (머리 가운데가 길을 따라감 · 날아가는 쪽에 수직인 축으로 틱마다 80° — 세로로 돎) */
        void fly(Vec3 from, Vec3 to, int ticks) {
            Vec3 d = V.unit(V.sub(to, from));
            Vec3 axis = V.len(V.cross(d, V.UP)) > 1e-3 ? V.unit(V.cross(d, V.UP)) : new Vec3(1, 0, 0);
            if (fx == null) {
                fx = RuneProps.fx(level, from, ticks + 12);
                p = RuneProps.prop(fx, RuneProps.HAMMER, from, new double[] {8, 14, 8}, RuneProps.along(V.UP, axis), s * 0.4, glow, core);
                fx.on(1, () -> p.size(s, 2));
            }
            fx.keep(ticks + 12);
            int t0 = fx.age();
            for (int t = 1; t <= ticks; t++) {
                double k = t / (double) ticks;
                Vec3 at = V.add(from, V.sub(to, from), k);
                spin += 80;
                Vec3 up = V.add(V.scale(V.UP, Math.cos(Math.toRadians(spin))), V.cross(axis, V.UP), Math.sin(Math.toRadians(spin)));
                final org.joml.Quaternionf q = RuneProps.along(V.unit(up), axis);
                fx.on(t0 + t, () -> p.to(at, q, -1, 1));
            }
        }

        /** 끝: 작아지며 사라짐 */
        void end(int after) {
            if (fx == null) return;
            int t = fx.age() + after;
            fx.keep(after + 6);
            fx.on(t, () -> p.size(s * 0.2, 4));
            fx.on(t + 4, p::remove);
        }
    }
}
