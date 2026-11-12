package mmorpg.fabric.vfx;

import java.util.concurrent.ThreadLocalRandom;
import mmorpg.fabric.vfx.PDefs.Def;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * 2026-11-12 애드온 파티클 알갱이 하나의 계산 (서버 — client AddonParticle 과 같은 식): 처음에 발생기 모양 · 방향 · 수명 · 속도 · 회전, 틱마다 움직임
 * (dynamic: 가속 · 끌림 — 한 틱 안은 정확한 지수 감쇠 / parametric: 처음 자리 + 식), 모습 (크기 · 색 · UV 칸 · 바라보는 쪽) 은 {@link #state} 에서.
 */
final class PSim {
    private static final double DT = 0.05;

    final Def d;
    final double[] v;
    final double life;
    private final double emitterAge0;
    private final Vec3 spawn;
    double x;
    double y;
    double z;
    private double vx;
    private double vy;
    private double vz;
    /** 바라보는 방향 (속도를 따르는 빌보드 — 처음은 내뿜는 방향) */
    Vector3f face = new Vector3f(0, 0, 1);
    final double spin0;
    final double spinRate;
    /** 틱 나이 */
    int age;
    final int lifetime;

    PSim(Vec3 origin, Def d, double[] emitterVars, double emitterAge) {
        this.d = d;
        this.v = emitterVars.clone();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int k = 0; k < 4; k++) v[PDefs.PR1 + k] = r.nextDouble();
        v[PDefs.AGE] = 0;
        v[PDefs.EAGE] = emitterAge;
        this.emitterAge0 = emitterAge;
        double ox = 0;
        double oy = 0;
        double oz = 0;
        if (d.offset != null) {
            ox = d.offset[0].eval(v);
            oy = d.offset[1].eval(v);
            oz = d.offset[2].eval(v);
        }
        double[] s = sample(d, v, r);
        double px = origin.x + ox + s[0];
        double py = origin.y + oy + s[1];
        double pz = origin.z + oz + s[2];
        double dx;
        double dy;
        double dz;
        if (d.dirMode == PDefs.DIR_VEC) {
            dx = d.dirVec[0].eval(v);
            dy = d.dirVec[1].eval(v);
            dz = d.dirVec[2].eval(v);
        } else {
            double sign = d.dirMode == PDefs.DIR_IN ? -1 : 1;
            double l = Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]);
            if (l < 1e-9) {
                double[] u = randomUnit(r);
                dx = u[0];
                dy = u[1];
                dz = u[2];
            } else {
                dx = s[0] / l * sign;
                dy = s[1] / l * sign;
                dz = s[2] / l * sign;
            }
        }
        double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dl > 1e-9) {
            dx /= dl;
            dy /= dl;
            dz /= dl;
            face = new Vector3f((float) dx, (float) dy, (float) dz);
        }
        this.life = Math.max(0.01, d.life.eval(v));
        v[PDefs.LIFETIME] = life;
        double speed = d.speed.eval(v);
        this.vx = dx * speed;
        this.vy = dy * speed;
        this.vz = dz * speed;
        this.spin0 = d.spin0 != null ? d.spin0.eval(v) : 0;
        this.spinRate = d.spinRate != null ? d.spinRate.eval(v) : 0;
        this.spawn = new Vec3(px, py, pz);
        this.lifetime = (int) Math.ceil(life * 20) + 1;
        if (d.motion == PDefs.MOTION_PARAMETRIC) {
            px += d.rel[0].eval(v);
            py += d.rel[1].eval(v);
            pz += d.rel[2].eval(v);
        }
        x = px;
        y = py;
        z = pz;
    }

    private static double[] sample(Def d, double[] v, ThreadLocalRandom r) {
        switch (d.shape) {
            case PDefs.SHAPE_SPHERE -> {
                double rad = d.radius.eval(v);
                double[] u = randomUnit(r);
                double k = d.surface ? rad : rad * Math.cbrt(r.nextDouble());
                return new double[] {u[0] * k, u[1] * k, u[2] * k};
            }
            case PDefs.SHAPE_BOX -> {
                double hx = d.half[0].eval(v);
                double hy = d.half[1].eval(v);
                double hz = d.half[2].eval(v);
                double[] p = {(r.nextDouble() * 2 - 1) * hx, (r.nextDouble() * 2 - 1) * hy, (r.nextDouble() * 2 - 1) * hz};
                if (d.surface) {
                    int axis = r.nextInt(3);
                    double[] h = {hx, hy, hz};
                    p[axis] = r.nextBoolean() ? h[axis] : -h[axis];
                }
                return p;
            }
            case PDefs.SHAPE_DISC -> {
                double rad = d.radius.eval(v);
                Vector3f n = new Vector3f((float) d.normal[0].eval(v), (float) d.normal[1].eval(v), (float) d.normal[2].eval(v));
                if (n.lengthSquared() < 1e-12) n.set(0, 1, 0);
                n.normalize();
                Vector3f a = Math.abs(n.y) < 0.9 ? new Vector3f(0, 1, 0).cross(n).normalize() : new Vector3f(1, 0, 0).cross(n).normalize();
                Vector3f b = new Vector3f(n).cross(a);
                double ang = r.nextDouble() * Math.PI * 2;
                double k = d.surface ? rad : rad * Math.sqrt(r.nextDouble());
                double ca = Math.cos(ang) * k;
                double sa = Math.sin(ang) * k;
                return new double[] {a.x * ca + b.x * sa, a.y * ca + b.y * sa, a.z * ca + b.z * sa};
            }
            default -> {
                return new double[] {0, 0, 0};
            }
        }
    }

    private static double[] randomUnit(ThreadLocalRandom r) {
        double zz = r.nextDouble() * 2 - 1;
        double t = r.nextDouble() * Math.PI * 2;
        double s = Math.sqrt(1 - zz * zz);
        return new double[] {s * Math.cos(t), s * Math.sin(t), zz};
    }

    /** 한 틱 (false = 다 삶) — client AddonParticle.tick 과 같음 */
    boolean tick() {
        if (age++ >= lifetime) return false;
        double t = age / 20.0;
        if (t >= life) return false;
        v[PDefs.AGE] = t;
        v[PDefs.EAGE] = emitterAge0 + t;
        if (d.motion == PDefs.MOTION_PARAMETRIC) {
            x = spawn.x + d.rel[0].eval(v);
            y = spawn.y + d.rel[1].eval(v);
            z = spawn.z + d.rel[2].eval(v);
        } else if (d.motion == PDefs.MOTION_DYNAMIC) {
            double ax = d.accel[0].eval(v);
            double ay = d.accel[1].eval(v);
            double az = d.accel[2].eval(v);
            double drag = d.drag.eval(v);
            double mx;
            double my;
            double mz;
            if (drag > 1e-6) {
                double k = Math.exp(-drag * DT);
                double f = (1 - k) / drag;
                double tx = ax / drag;
                double ty = ay / drag;
                double tz = az / drag;
                mx = tx * DT + (vx - tx) * f;
                my = ty * DT + (vy - ty) * f;
                mz = tz * DT + (vz - tz) * f;
                vx = tx + (vx - tx) * k;
                vy = ty + (vy - ty) * k;
                vz = tz + (vz - tz) * k;
            } else {
                mx = vx * DT + 0.5 * ax * DT * DT;
                my = vy * DT + 0.5 * ay * DT * DT;
                mz = vz * DT + 0.5 * az * DT * DT;
                vx += ax * DT;
                vy += ay * DT;
                vz += az * DT;
            }
            x += mx;
            y += my;
            z += mz;
            double sp = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (sp > d.minSpeed) face = new Vector3f((float) (vx / sp), (float) (vy / sp), (float) (vz / sp));
        }
        return true;
    }

    /** 지금 모습 (그릴 것이 없으면 false — 크기 0 · 투명 · 수명 끝) */
    boolean state(State s) {
        double t = age / 20.0;
        if (t > life) return false;
        v[PDefs.AGE] = t;
        v[PDefs.EAGE] = emitterAge0 + t;
        double w = d.sizeX.eval(v);
        double h = d.sizeY.eval(v);
        if (!(w > 0) || !(h > 0)) return false;
        s.w = w;
        s.h = h;
        s.r = clamp01(d.color[0].eval(v));
        s.g = clamp01(d.color[1].eval(v));
        s.b = clamp01(d.color[2].eval(v));
        s.a = clamp01(d.color[3].eval(v));
        if (s.a <= 0.003) return false;
        double u;
        double vv;
        if (d.flipbook) {
            double maxF = Math.max(1, Math.floor(d.maxFrame.eval(v)));
            double frame = d.stretch ? Math.floor(t / life * maxF) : Math.floor(t * d.fps.eval(v));
            frame = d.loop ? frame % maxF : Math.min(frame, maxF - 1);
            if (frame < 0) frame = 0;
            u = d.fbBase[0].eval(v) + d.fbStep[0] * frame;
            vv = d.fbBase[1].eval(v) + d.fbStep[1] * frame;
        } else {
            u = d.uv[0].eval(v);
            vv = d.uv[1].eval(v);
        }
        s.glyph = d.sheet != null ? d.sheet.glyph(u, vv) : -1;
        Vector3f dir = face;
        if (d.faceDir != null) {
            Vector3f fd = new Vector3f((float) d.faceDir[0].eval(v), (float) d.faceDir[1].eval(v), (float) d.faceDir[2].eval(v));
            if (fd.lengthSquared() > 1e-12) dir = fd.normalize();
        }
        s.dir = dir;
        s.spin = spin0 + spinRate * t;
        return true;
    }

    private static double clamp01(double x) {
        return x < 0 ? 0 : x > 1 ? 1 : x;
    }

    /** 한 순간의 모습 */
    static final class State {
        double w;
        double h;
        double r;
        double g;
        double b;
        double a;
        int glyph;
        Vector3f dir;
        double spin;
    }
}
