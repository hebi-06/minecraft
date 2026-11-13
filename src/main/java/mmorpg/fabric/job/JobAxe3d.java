package mmorpg.fabric.job;

import mmorpg.fabric.rune.IceFx;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneProps;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 2026-11-13 직업 스킬의 3D 도끼 · 얼음 (직업 도감 메모 — 전사 액스 사이클론 "도끼 연출을 3d로", 프로스트 레이더 "얼음과 도끼 연출을 3d로"): 도끼 모델
 * (tools/vfx_blocky.py job_axe — 쇠 머리 · 계열 색 빛 날, frost_axe — 얼음 날 · 서리 결정) 을 Vfx 소품으로 띄워 던지고 · 휘두르고 · 박음. 얼음은 룬 빙하의 결정
 * ({@link IceFx}) · 얼음 가시 ({@link RuneProps#iceSpike}) · 날아가는 얼음 조각 (얼음 가시 모델).
 * <p>
 * 모델 축: +Y = 자루 (손잡이 → 도끼머리), −Z = 날 (초승달 날이 향한 쪽), X = 날의 넓은 면에 수직
 */
final class JobAxe3d {
    private JobAxe3d() {}

    static final String JOB_AXE = "rpg:vfx/job_axe";
    static final String FROST_AXE = "rpg:vfx/frost_axe";
    /** 모델 길이 (자루 끝 y −16 ~ 창끝 y 29.5) · 손잡이 · 도끼머리 가운데 · 날 끝 가운데 (픽셀) */
    static final double LEN = 45.5;
    static final double[] GRIP = {8, -7, 8};
    static final double[] HEAD = {8, 18, 2};
    static final double[] EDGE = {8, 17.6, -6.6};
    /** 손잡이 → 도끼머리 가운데 (칸, 크기 1) */
    static final double REACH = (HEAD[1] - GRIP[1]) / 16.0;
    /** 도끼머리 가운데 → 날 끝 (칸, 크기 1) */
    static final double BITE = (HEAD[2] - EDGE[2]) / 16.0;

    /** 모델 +Y 가 haft (손잡이 → 도끼머리), −Z 가 edge (날) 쪽인 회전 (edge 는 haft 에 수직으로 맞춤) */
    static Quaternionf pose(Vec3 haft, Vec3 edge) {
        Vec3 y = V.unit(haft);
        Vec3 e = V.sub(edge, V.scale(y, V.dot(edge, y)));
        if (V.len(e) < 1e-4) e = Math.abs(y.y) < 0.9 ? V.cross(y, V.UP) : V.cross(y, new Vec3(1, 0, 0));
        Vec3 z = V.scale(V.unit(e), -1);
        Vec3 x = V.unit(V.cross(y, z));
        return Vfx.basis(x, y, z);
    }

    /** q 를 세계 축 axis 둘레로 deg 도 더 돌림 */
    static Quaternionf turn(Quaternionf q, Vec3 axis, double deg) {
        Vec3 a = V.unit(axis);
        return new Quaternionf().rotateAxis((float) Math.toRadians(deg), (float) a.x, (float) a.y, (float) a.z).mul(q);
    }

    /** 도끼 소품 하나 (grip = 모델의 이 점이 at): frost 면 빙하 도끼 (빛 띠 = 얼음빛), 아니면 직업 도끼 (빛 날 = glow · 룬 홈 = core) */
    static Vfx.Prop axe(Vfx.Fx fx, boolean frost, Vec3 at, double[] grip, Quaternionf rot, double scale, double[] glow, double[] core) {
        return RuneProps.prop(fx, frost ? FROST_AXE : JOB_AXE, at, grip, rot, scale, glow, core);
    }

    /** 길이 (칸) → 크기 */
    static double scaleFor(double blocks) {
        return RuneProps.scaleFor(blocks, LEN);
    }

    /**
     * 던진 도끼 (제 무게 가운데 — 도끼머리 — 둘레로 앞뒤로 구름): from → to 를 ticks 틱 (곡선 높이 arc) · 틱마다 spin 도 (축 = 날아가는 쪽의 옆) — 끝에 날이 to 쪽을
     * 보게 (마지막 모습 = 날 = 날아가는 쪽 · 자루 = 위). 소품 (끝난 뒤 그대로 — 부른 쪽이 옮기거나 지움)
     */
    static Vfx.Prop thrown(Vfx.Fx fx, boolean frost, Vec3 from, Vec3 to, int ticks, double arc, double spin, double scale, double[] glow, double[] core) {
        Vec3 d = V.sub(to, from);
        Vec3 u = V.len(d) < 1e-3 ? new Vec3(0, 0, 1) : V.unit(d);
        Vec3 side = JobFx3d.sideOf(u);
        Vec3 up0 = V.unit(V.cross(side, u));
        Quaternionf end = pose(up0, u);
        int n = Math.max(1, ticks);
        Quaternionf start = turn(end, side, -spin * n);
        Vfx.Prop p = axe(fx, frost, from, HEAD, start, scale, glow, core);
        int t0 = fx.age();
        fx.each(t0 + 1, t0 + n, 1, age -> {
            int i = age - t0;
            double t = i / (double) n;
            Vec3 at = V.up(V.lerp(from, to, t), 4 * arc * t * (1 - t));
            p.to(at, turn(end, side, -spin * (n - i)), -1, 1);
        });
        return p;
    }

    /** 날아가는 얼음 조각 n 개 (얼음 가시 모델 — 바깥을 보며 튀어 구르다 작아져 사라짐): c 둘레 반지름 r 까지 · 조각 길이 size 칸 */
    static void iceShards(ServerLevel level, Vec3 c, int n, double r, double size) {
        Vfx.Fx fx = RuneProps.fx(level, c, 16);
        double s = RuneProps.scaleFor(size, RuneProps.ICE_SPIKE_H);
        double[] mid = {8, 12, 8};
        for (int i = 0; i < n; i++) {
            double a = (i + Kit.rand() * 0.6) * Math.PI * 2 / n;
            double up = 0.15 + Kit.rand() * 0.7;
            Vec3 dir = V.unit(new Vec3(Math.cos(a), up, Math.sin(a)));
            Vec3 side = V.cross(dir, V.UP);
            Vfx.Prop p = RuneProps.prop(fx, RuneProps.ICE_SPIKE, V.add(c, dir, 0.2), mid, RuneProps.along(dir, side), s * 0.6);
            double rr = r * (0.7 + Kit.rand() * 0.5);
            long seed = (long) (Kit.rand() * 1e9);
            Vec3 far = V.add(c, dir, rr);
            Vec3 drop = V.up(V.add(c, dir, rr * 1.2), -0.6);
            fx.on(1, () -> p.to(far, new Quaternionf(RuneProps.tumble(seed, 6, 30)).mul(RuneProps.along(dir, side)), s, 5));
            fx.on(6, () -> p.to(drop, new Quaternionf(RuneProps.tumble(seed, 12, 30)).mul(RuneProps.along(dir, side)), s * 0.15, 6));
            fx.on(12, p::remove);
        }
    }

    /** 3D 얼음 가시 고리 (RuneProps.iceSpike): c 둘레 반지름 r0 ~ r1 에 n 개 · 높이 h0 ~ h1 칸 · hold 틱 서 있음 · 고리 안쪽부터 바깥으로 1틱씩 늦게 */
    static void spikeRing(ServerLevel level, Vec3 c, int n, double r0, double r1, double h0, double h1, int hold) {
        for (int i = 0; i < n; i++) {
            double a = (i + Kit.rand() * 0.4) * Math.PI * 2 / n;
            double r = r0 + Kit.rand() * (r1 - r0);
            Vec3 p = new Vec3(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r);
            Vec3 g = new Vec3(p.x, RuneKit.groundAt(level, V.up(p, 1.2), c.y), p.z);
            double h = h0 + Kit.rand() * (h1 - h0);
            int delay = (int) Math.round((r - r0) / Math.max(0.3, r1 - r0) * 2);
            RuneKit.later(delay, () -> RuneProps.iceSpike(level, g, h, hold, Kit.rand() * 360));
        }
    }

    /**
     * 솟는 결정 무리 (룬 빙하의 결정): c 둘레 반지름 r0 ~ r1 에 n 개 · 높이 h0 ~ h1 칸 · 굵기 = 높이 × wk (± 12%) · 바깥으로 tilt0 ~ tilt1 도 기움 (in 이면
     * 안쪽으로 — 가둠) · hold 틱 서 있다 brk 틱 동안 부서짐 (솟기는 delay 틱 (2 이상) 뒤 2틱 동안 — 부서지기 시작 = max(2, delay) + 2 + hold)
     */
    static void crystals(ServerLevel level, Vec3 c, int n, double r0, double r1, double h0, double h1, double wk, double tilt0, double tilt1, boolean in, int delay,
            int hold, int brk) {
        for (int i = 0; i < n; i++) {
            double a = (i + Kit.rand() * 0.5) * Math.PI * 2 / n;
            double r = r0 + Kit.rand() * (r1 - r0);
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 p = V.add(c, out, r);
            Vec3 g = new Vec3(p.x, IceFx.groundY(level, p.x, p.z, c.y), p.z);
            double h = h0 + Kit.rand() * (h1 - h0);
            double tilt = tilt0 + Kit.rand() * (tilt1 - tilt0);
            Vec3 axis = IceFx.tilted(in ? V.scale(out, -1) : out, tilt);
            IceFx.crystal(level, g, axis, h, Math.max(0.3, h * wk * (0.88 + Kit.rand() * 0.24)), Kit.rand() * 120, i % 2 == 0, i % 3 == 1, delay, 2, hold, brk);
        }
    }
}
