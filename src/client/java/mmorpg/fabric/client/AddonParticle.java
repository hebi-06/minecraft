package mmorpg.fabric.client;

import java.util.concurrent.ThreadLocalRandom;
import mmorpg.fabric.client.AddonParticles.Def;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 애드온 파티클 알갱이 하나 (베드락 파티클과 같은 계산): 처음에 발생기 모양 · 방향 · 수명 · 속도 · 회전을 식으로 정하고,
 * 틱마다 움직임 (dynamic: 가속 · 끌림 — 한 틱 안은 정확한 지수 감쇠 / parametric: 처음 자리 + 식), 그릴 때마다 크기 · 색 · UV · 방향을 식으로.
 * 자바 파티클 사각형은 정사각형뿐이라 직사각형 (가로 ≠ 세로) 은 정사각형 여러 장으로 나눠 그림 (유클리드 나눔 — 그림 조각을 그대로 이어 붙임).
 * 밝기 최대 (애드온 파티클은 빛을 받지 않음).
 */
public final class AddonParticle extends SingleQuadParticle {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final double DT = 0.05;

    private final Def d;
    private final double[] v;
    private final double life;
    private final double emitterAge0;
    private final Vec3 spawn;
    private double vx;
    private double vy;
    private double vz;
    /** 바라보는 방향 (속도를 따르는 빌보드 — 처음은 내뿜는 방향) */
    private Vector3f face = new Vector3f(0, 0, 1);
    private final double spin0;
    private final double spinRate;
    private final TextureAtlasSprite tex;
    /** 0.27.0 보는 사람 걸러내기: 0 = 모두 · 1 = 주인이 1인칭이면 숨김 · 2 = 주인의 1인칭에만 (주인 = owner 엔티티 번호) */
    private final int view;
    private final int owner;
    /**
     * 0.29.0 solo: (주인 · 번호) 마다 가장 새 그림 하나만 — 앞 그림은 그리지 않고 다음 틱에 사라짐. 오래 서 있는 큰 그림 (사신 현신 그림자) 을 다시 그릴 때
     * 겹친 두 장 (움직이면 자리가 조금 다름) 이 깊이 싸움으로 줄무늬가 지던 것을 막음. -1 = 아님
     */
    private final long soloKey;
    private static final java.util.Map<Long, AddonParticle> SOLO = new java.util.HashMap<>();

    AddonParticle(ClientLevel level, Vec3 origin, Def d, double[] emitterVars, double emitterAge, TextureAtlasSprite sprite) {
        super(level, origin.x, origin.y, origin.z, sprite);
        this.d = d;
        this.tex = sprite;
        this.v = emitterVars.clone();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int k = 0; k < 4; k++) v[AddonParticles.PR1 + k] = r.nextDouble();
        v[AddonParticles.AGE] = 0;
        v[AddonParticles.EAGE] = emitterAge;
        this.emitterAge0 = emitterAge;
        this.view = AddonParticles.VIEW >= 0 ? (int) Math.round(v[AddonParticles.VIEW]) : 0;
        int solo = AddonParticles.SOLO >= 0 ? (int) Math.round(v[AddonParticles.SOLO]) : 0;
        this.owner = (view == 0 && solo <= 0) || AddonParticles.OWNER_HI < 0 || AddonParticles.OWNER_LO < 0 ? -1
                : ((int) Math.round(v[AddonParticles.OWNER_HI]) << 16) | ((int) Math.round(v[AddonParticles.OWNER_LO]) & 0xFFFF);
        this.soloKey = solo > 0 && owner >= 0 ? ((long) owner << 20) | (solo & 0xFFFFF) : -1;
        if (soloKey >= 0) SOLO.put(soloKey, this);
        // 자리 · 방향 (발생기 모양)
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
        if (d.dirMode == AddonParticles.DIR_VEC) {
            dx = d.dirVec[0].eval(v);
            dy = d.dirVec[1].eval(v);
            dz = d.dirVec[2].eval(v);
        } else {
            double sign = d.dirMode == AddonParticles.DIR_IN ? -1 : 1;
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
        v[AddonParticles.LIFETIME] = life;
        double speed = d.speed.eval(v);
        this.vx = dx * speed;
        this.vy = dy * speed;
        this.vz = dz * speed;
        this.spin0 = d.spin0 != null ? d.spin0.eval(v) : 0;
        this.spinRate = d.spinRate != null ? d.spinRate.eval(v) : 0;
        this.spawn = new Vec3(px, py, pz);
        this.lifetime = (int) Math.ceil(life * 20) + 1;
        this.hasPhysics = false;
        this.gravity = 0;
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        if (d.motion == AddonParticles.MOTION_PARAMETRIC) {
            px += d.rel[0].eval(v);
            py += d.rel[1].eval(v);
            pz += d.rel[2].eval(v);
        }
        this.setPos(px, py, pz);
        this.xo = px;
        this.yo = py;
        this.zo = pz;
        float reach = (float) Math.max(Math.abs(d.sizeX.eval(v)), Math.abs(d.sizeY.eval(v))) * 4 + 1;
        this.setSize(reach, reach);
    }

    /** 발생기 모양 안의 한 점 (발생기 기준) */
    private static double[] sample(Def d, double[] v, ThreadLocalRandom r) {
        switch (d.shape) {
            case AddonParticles.SHAPE_SPHERE -> {
                double rad = d.radius.eval(v);
                double[] u = randomUnit(r);
                double k = d.surface ? rad : rad * Math.cbrt(r.nextDouble());
                return new double[] {u[0] * k, u[1] * k, u[2] * k};
            }
            case AddonParticles.SHAPE_BOX -> {
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
            case AddonParticles.SHAPE_DISC -> {
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
        double z = r.nextDouble() * 2 - 1;
        double t = r.nextDouble() * Math.PI * 2;
        double s = Math.sqrt(1 - z * z);
        return new double[] {s * Math.cos(t), s * Math.sin(t), z};
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (soloKey >= 0 && SOLO.get(soloKey) != this) {
            this.remove(); // 같은 (주인 · 번호) 의 새 그림이 나옴
            return;
        }
        if (this.age++ >= this.lifetime) {
            this.remove();
            if (soloKey >= 0) SOLO.remove(soloKey, this);
            return;
        }
        double t = this.age / 20.0;
        if (t >= life) {
            this.remove();
            if (soloKey >= 0) SOLO.remove(soloKey, this);
            return;
        }
        v[AddonParticles.AGE] = t;
        v[AddonParticles.EAGE] = emitterAge0 + t;
        if (d.motion == AddonParticles.MOTION_PARAMETRIC) {
            this.setPos(spawn.x + d.rel[0].eval(v), spawn.y + d.rel[1].eval(v), spawn.z + d.rel[2].eval(v));
        } else if (d.motion == AddonParticles.MOTION_DYNAMIC) {
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
            this.setPos(this.x + mx, this.y + my, this.z + mz);
            double sp = Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (sp > d.minSpeed) face = new Vector3f((float) (vx / sp), (float) (vy / sp), (float) (vz / sp));
        }
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return d.layer;
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return FULL_BRIGHT;
    }

    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        double t = (this.age + partialTick) / 20.0;
        if (t > life) return;
        if (soloKey >= 0 && SOLO.get(soloKey) != this) return;
        if (view != 0 && !shown()) return;
        v[AddonParticles.AGE] = t;
        v[AddonParticles.EAGE] = emitterAge0 + t;
        float w = (float) d.sizeX.eval(v);
        float h = (float) d.sizeY.eval(v);
        if (!(w > 0) || !(h > 0)) return;
        float cr = clamp01(d.color[0].eval(v));
        float cg = clamp01(d.color[1].eval(v));
        float cb = clamp01(d.color[2].eval(v));
        float ca = clamp01(d.color[3].eval(v));
        if (ca <= 0.003F) return;
        // UV (애드온 그림 픽셀 → 아틀라스)
        double u;
        double vv;
        double uw;
        double vh;
        if (d.flipbook) {
            double maxF = Math.max(1, Math.floor(d.maxFrame.eval(v)));
            double frame;
            if (d.stretch) {
                frame = Math.floor(t / life * maxF);
            } else {
                frame = Math.floor(t * d.fps.eval(v));
            }
            frame = d.loop ? frame % maxF : Math.min(frame, maxF - 1);
            if (frame < 0) frame = 0;
            u = d.fbBase[0].eval(v) + d.fbStep[0] * frame;
            vv = d.fbBase[1].eval(v) + d.fbStep[1] * frame;
            uw = d.fbSize[0];
            vh = d.fbSize[1];
        } else {
            u = d.uv[0].eval(v);
            vv = d.uv[1].eval(v);
            uw = d.uvSize[0].eval(v);
            vh = d.uvSize[1].eval(v);
        }
        float su0 = tex.getU0();
        float sdu = tex.getU1() - su0;
        float sv0 = tex.getV0();
        float sdv = tex.getV1() - sv0;
        float u0 = su0 + (float) (u / d.tw) * sdu;
        float u1 = su0 + (float) ((u + uw) / d.tw) * sdu;
        float v0 = sv0 + (float) (vv / d.th) * sdv;
        float v1 = sv0 + (float) ((vv + vh) / d.th) * sdv;
        // 자리 (카메라 기준)
        Vec3 cam = camera.position();
        double px = Mth.lerp(partialTick, this.xo, this.x);
        double py = Mth.lerp(partialTick, this.yo, this.y);
        double pz = Mth.lerp(partialTick, this.zo, this.z);
        float rx = (float) (px - cam.x);
        float ry = (float) (py - cam.y);
        float rz = (float) (pz - cam.z);
        // 방향 (X = 그림 가로, Y = 그림 세로, Z = 면)
        Vector3f dir = face;
        if (d.faceDir != null) {
            Vector3f fd = new Vector3f((float) d.faceDir[0].eval(v), (float) d.faceDir[1].eval(v), (float) d.faceDir[2].eval(v));
            if (fd.lengthSquared() > 1e-12) dir = fd.normalize();
        }
        Quaternionf q = orientation(d.facing, camera, rx, ry, rz, dir);
        if (q == null) return;
        double spin = spin0 + spinRate * t;
        if (spin != 0 && d.facing != AddonParticles.LOOKAT_DIRECTION) q.rotateZ((float) Math.toRadians(spin));
        int color = ARGB.colorFromFloat(ca, cr, cg, cb);
        quads(state, q, rx, ry, rz, w, h, u0, u1, v0, v1, color);
    }

    /**
     * 0.27.0 이 그림을 지금 그릴지 (view): 1 = 주인 (스킬을 쓴 사람) 이 1인칭이면 숨김 — 몸 둘레 · 발밑 그림은 1인칭 카메라 바로 옆이라 픽셀이 화면을 덮는
     * 큰 네모가 됨, 2 = 주인의 1인칭에서만 (눈앞에 따로 그리는 그림 — 3인칭 · 다른 사람은 몸 둘레 그림을 봄)
     */
    private boolean shown() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        boolean mine = mc.player != null && mc.player.getId() == owner;
        boolean first = mc.options.getCameraType().isFirstPerson();
        return view == 1 ? !(mine && first) : mine && first;
    }

    private static float clamp01(double x) {
        return (float) (x < 0 ? 0 : x > 1 ? 1 : x);
    }

    /** 빌보드 방향 → 사각형 회전 (열 = X 가로 · Y 세로 · Z 면) */
    private static Quaternionf orientation(int facing, Camera camera, float x, float y, float z, Vector3f dir) {
        switch (facing) {
            case AddonParticles.ROTATE_XYZ -> {
                return new Quaternionf(camera.rotation());
            }
            case AddonParticles.ROTATE_Y, AddonParticles.LOOKAT_Y -> {
                Vector3f up = new Vector3f(0, 1, 0);
                Vector3f zv;
                if (facing == AddonParticles.ROTATE_Y) {
                    Vector3f back = new Vector3f(0, 0, 1).rotate(camera.rotation());
                    zv = new Vector3f(back.x, 0, back.z);
                } else {
                    zv = new Vector3f(-x, 0, -z);
                }
                if (zv.lengthSquared() < 1e-10) return new Quaternionf(camera.rotation());
                zv.normalize();
                Vector3f xv = new Vector3f(up).cross(zv).normalize();
                return basis(xv, up, zv);
            }
            case AddonParticles.LOOKAT_XYZ -> {
                Vector3f zv = new Vector3f(-x, -y, -z);
                if (zv.lengthSquared() < 1e-10) return new Quaternionf(camera.rotation());
                zv.normalize();
                Vector3f camUp = new Vector3f(0, 1, 0).rotate(camera.rotation());
                Vector3f xv = new Vector3f(camUp).cross(zv);
                if (xv.lengthSquared() < 1e-10) return new Quaternionf(camera.rotation());
                xv.normalize();
                Vector3f yv = new Vector3f(zv).cross(xv);
                return basis(xv, yv, zv);
            }
            case AddonParticles.DIRECTION_X, AddonParticles.DIRECTION_Y, AddonParticles.DIRECTION_Z -> {
                if (dir.lengthSquared() < 1e-10) return null;
                Vector3f dv = new Vector3f(dir).normalize();
                Vector3f ref = Math.abs(dv.y) < 0.999F ? new Vector3f(0, 1, 0) : new Vector3f(0, 0, 1);
                if (facing == AddonParticles.DIRECTION_Z) {
                    // 면의 법선 = 방향, 세로는 위쪽
                    Vector3f yv = new Vector3f(ref).sub(new Vector3f(dv).mul(ref.dot(dv))).normalize();
                    Vector3f xv = new Vector3f(yv).cross(dv).normalize();
                    return basis(xv, yv, dv);
                }
                if (facing == AddonParticles.DIRECTION_X) {
                    // 가로 = 방향, 면은 위쪽 기준
                    Vector3f zv = new Vector3f(dv).cross(ref).normalize();
                    Vector3f yv = new Vector3f(zv).cross(dv).normalize();
                    return basis(dv, yv, zv);
                }
                // DIRECTION_Y: 세로 = 방향
                Vector3f zv = new Vector3f(ref).cross(dv);
                if (zv.lengthSquared() < 1e-10) zv = new Vector3f(1, 0, 0).cross(dv);
                zv.normalize();
                Vector3f xv = new Vector3f(dv).cross(zv).normalize();
                return basis(xv, dv, zv);
            }
            case AddonParticles.LOOKAT_DIRECTION -> {
                if (dir.lengthSquared() < 1e-10) return null;
                Vector3f dv = new Vector3f(dir).normalize();
                Vector3f toCam = new Vector3f(-x, -y, -z);
                Vector3f n = new Vector3f(toCam).sub(new Vector3f(dv).mul(toCam.dot(dv)));
                if (n.lengthSquared() < 1e-10) {
                    n.set(Math.abs(dv.y) < 0.9F ? 0 : 1, Math.abs(dv.y) < 0.9F ? 1 : 0, 0);
                    n.sub(new Vector3f(dv).mul(n.dot(dv)));
                }
                n.normalize();
                Vector3f yv = new Vector3f(n).cross(dv).normalize();
                return basis(dv, yv, n);
            }
            case AddonParticles.EMITTER_XY -> {
                return basis(new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1));
            }
            case AddonParticles.EMITTER_YZ -> {
                return basis(new Vector3f(0, 0, -1), new Vector3f(0, 1, 0), new Vector3f(1, 0, 0));
            }
            default -> {
                // EMITTER_XZ: 땅에 눕힘 (면이 위)
                return basis(new Vector3f(1, 0, 0), new Vector3f(0, 0, -1), new Vector3f(0, 1, 0));
            }
        }
    }

    private static Quaternionf basis(Vector3f xv, Vector3f yv, Vector3f zv) {
        return new Quaternionf().setFromNormalized(new Matrix3f(xv, yv, zv));
    }

    /**
     * 가로 반 w · 세로 반 h 직사각형을 정사각형으로 나눠 그림 (유클리드: 짧은 변 크기의 정사각형을 긴 쪽으로 늘어놓고 남는 띠를 다시 나눔,
     * 남는 띠가 전체의 2% 아래면 버림 — 애드온 그림 가장자리는 대개 투명). 조각마다 그림의 같은 자리를 잘라 붙임.
     */
    private void quads(QuadParticleRenderState state, Quaternionf q, float x, float y, float z, float w, float h, float u0, float u1, float v0, float v1,
            int color) {
        if (Math.abs(w - h) <= 1e-4F * Math.max(w, h)) {
            state.add(getLayer(), x, y, z, q.x, q.y, q.z, q.w, w, u0, u1, v0, v1, color, FULL_BRIGHT);
            return;
        }
        Vector3f ax = new Vector3f(1, 0, 0).rotate(q);
        Vector3f ay = new Vector3f(0, 1, 0).rotate(q);
        float minSide = Math.max(w, h) * 2 * 0.02F;
        tile(state, q, x, y, z, ax, ay, w, h, u0, u1, v0, v1, color, -w, -h, 2 * w, 2 * h, minSide, 0, new int[] {0});
    }

    private void tile(QuadParticleRenderState state, Quaternionf q, float x, float y, float z, Vector3f ax, Vector3f ay, float w, float h, float u0,
            float u1, float v0, float v1, int color, float rx, float ry, float rw, float rh, float minSide, int depth, int[] count) {
        if (rw < minSide || rh < minSide || depth > 6 || count[0] > 48) return;
        boolean wide = rw >= rh;
        float side = wide ? rh : rw;
        int n = (int) Math.floor((wide ? rw : rh) / side + 1e-4F);
        n = Math.min(n, 48 - count[0]);
        for (int i = 0; i < n; i++) {
            float cx = wide ? rx + side * (i + 0.5F) : rx + side * 0.5F;
            float cy = wide ? ry + side * 0.5F : ry + side * (i + 0.5F);
            float s = side * 0.5F;
            float qu0 = u0 + (cx - s + w) / (2 * w) * (u1 - u0);
            float qu1 = u0 + (cx + s + w) / (2 * w) * (u1 - u0);
            float qv0 = v0 + (h - (cy + s)) / (2 * h) * (v1 - v0);
            float qv1 = v0 + (h - (cy - s)) / (2 * h) * (v1 - v0);
            state.add(getLayer(), x + ax.x * cx + ay.x * cy, y + ax.y * cx + ay.y * cy, z + ax.z * cx + ay.z * cy, q.x, q.y, q.z, q.w, s, qu0, qu1,
                    qv0, qv1, color, FULL_BRIGHT);
            count[0]++;
        }
        float used = side * n;
        if (wide) tile(state, q, x, y, z, ax, ay, w, h, u0, u1, v0, v1, color, rx + used, ry, rw - used, rh, minSide, depth + 1, count);
        else tile(state, q, x, y, z, ax, ay, w, h, u0, u1, v0, v1, color, rx, ry + used, rw, rh - used, minSide, depth + 1, count);
    }
}
