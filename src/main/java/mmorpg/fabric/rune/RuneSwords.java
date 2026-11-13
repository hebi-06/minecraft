package mmorpg.fabric.rune;

import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-13 룬의 3D 검 (룬 전용 검 2판 rpg:vfx/rune_sword — 사용자 "이재 다른 룬들도 모션하고 이펙트 추가하자" · 초월 · 영웅 먼저):
 * 칼자루 · 칼끝 방향 · 넓은 면이 보는 쪽으로 놓기, 한 점 둘레를 원을 그리며 휘두르기 (틱마다 작게 — 표시 엔티티의 보간은 자리는 곧게 · 회전은 구면이라
 * 한 번에 크게 돌리면 칼이 원에서 벗어남), 몸 둘레를 돌기.
 * 모델 축 = 칼끝 +Y · 넓은 면 ±X · 날 ±Z (RuneProps.along(칼끝 방향, 넓은 면 방향)).
 */
public final class RuneSwords {
    private RuneSwords() {}

    /** 손잡이 가운데 (모델 픽셀) — 휘두를 때 이 점이 칼자루 자리 */
    static final double[] HILT = RuneProps.SWORD_HILT;
    /** 손잡이 가운데 → 칼끝 (모델 픽셀 — 칼끝 y 31 · 손잡이 y −3.5) */
    static final double HILT_TO_TIP = 34.5;

    /** 3D 검 하나 */
    public static final class Blade {
        public final Vfx.Fx fx;
        public final Vfx.Prop prop;
        /** 칸 — 자루 끝에서 칼끝까지 */
        public final double len;
        final double s;
        /** 쥔 곳 → 끝 (모델 픽셀) */
        final double gripToTip;
        final double modelLen;
        Vec3 hilt;
        Vec3 dir;
        Vec3 face;
        double k = 1;

        Blade(Vfx.Fx fx, Vec3 hilt, Vec3 dir, Vec3 face, double len, double k0, double[] glow, double[] core) {
            this(fx, RuneProps.SWORD, HILT, HILT_TO_TIP, RuneProps.SWORD_LEN, hilt, dir, face, len, k0, glow, core);
        }

        /** 다른 긴 물체 (화살 — 쥔 곳 = 오늬): model · 쥔 곳 grip · 쥔 곳에서 끝까지 gripToTip · 모델 길이 modelLen (모두 픽셀) */
        Blade(Vfx.Fx fx, String model, double[] grip, double gripToTip, double modelLen, Vec3 hilt, Vec3 dir, Vec3 face, double len, double k0, double[] glow,
                double[] core) {
            this.fx = fx;
            this.len = len;
            this.modelLen = modelLen;
            this.gripToTip = gripToTip;
            this.s = RuneProps.scaleFor(len, modelLen);
            this.hilt = hilt;
            this.dir = V.unit(dir);
            this.face = face;
            this.k = k0;
            this.prop = RuneProps.prop(fx, model, hilt, grip, RuneProps.along(this.dir, face), s * k0, glow, core);
        }

        /** 칼자루 hilt · 칼끝 방향 dir · 넓은 면이 보는 쪽 face 로 (ticks 틱 보간) */
        public Blade pose(Vec3 hilt, Vec3 dir, Vec3 face, int ticks) {
            this.hilt = hilt;
            this.dir = V.unit(dir);
            this.face = face;
            prop.to(hilt, RuneProps.along(this.dir, face), s * k, ticks);
            return this;
        }

        /** 크기 몫 (1 = len 칸) */
        public Blade grow(double k, int ticks) {
            this.k = k;
            prop.size(s * k, ticks);
            return this;
        }

        /** 지금 칼끝 자리 */
        public Vec3 tip() {
            return V.add(hilt, dir, len * k * (gripToTip / modelLen));
        }

        /** 쥔 곳에서 끝까지 (칸 — 지금 크기) */
        public double reach() {
            return len * k * (gripToTip / modelLen);
        }

        public Vec3 hilt() {
            return hilt;
        }

        public Vec3 dir() {
            return dir;
        }

        public void remove() {
            prop.remove();
        }
    }

    /** 검 하나를 hilt 에 (처음 크기 몫 k0 — 0.2 면 다음 틱부터 grow 로 커짐) */
    public static Blade blade(Vfx.Fx fx, Vec3 hilt, Vec3 dir, Vec3 face, double len, double k0, double[] glow, double[] core) {
        return new Blade(fx, hilt, dir, face, len, k0, glow, core);
    }

    /** 2026-11-13 룬 빛 화살 하나 (쥔 곳 = 오늬 nock · 촉 방향 dir · 촉 두 판의 넓은 면 face · 길이 len 칸) */
    public static Blade arrow(Vfx.Fx fx, Vec3 nock, Vec3 dir, Vec3 face, double len, double k0, double[] glow, double[] core) {
        return new Blade(fx, RuneProps.ARROW, RuneProps.ARROW_NOCK, 31 - RuneProps.ARROW_NOCK[1], RuneProps.ARROW_LEN, nock, dir, face, len, k0, glow, core);
    }

    /** 2026-11-13 뇌신의 창 하나 (쥔 곳 = 손잡이 hilt · 촉 방향 dir · 번개 꼴 촉의 넓은 면 face · 길이 len 칸) */
    public static Blade spear(Vfx.Fx fx, Vec3 grip, Vec3 dir, Vec3 face, double len, double k0, double[] glow, double[] core) {
        return new Blade(fx, RuneProps.THUNDER_SPEAR, RuneProps.THUNDER_SPEAR_GRIP, 31.6 - RuneProps.THUNDER_SPEAR_GRIP[1], RuneProps.THUNDER_SPEAR_LEN, grip,
                dir, face, len, k0, glow, core);
    }

    /** v 를 axis 둘레로 deg 도 (오른손 — axis 가 위면 위에서 보아 반시계) */
    public static Vec3 rot(Vec3 v, Vec3 axis, double deg) {
        Vec3 a = V.unit(axis);
        double r = Math.toRadians(deg);
        double c = Math.cos(r);
        double sn = Math.sin(r);
        Vec3 cr = V.cross(a, v);
        double d = V.dot(a, v);
        return new Vec3(v.x * c + cr.x * sn + a.x * d * (1 - c), v.y * c + cr.y * sn + a.y * d * (1 - c), v.z * c + cr.z * sn + a.z * d * (1 - c));
    }

    /** 0 ~ 1 → 빠르기 곡선 ("in" 점점 빠르게 · "out" 점점 느리게 · "inout" · 그 밖 곧게) */
    public static double ease(double u, String e) {
        u = Math.max(0, Math.min(1, u));
        return switch (e == null ? "" : e) {
            case "in" -> u * u;
            case "out" -> 1 - (1 - u) * (1 - u);
            case "inout" -> u < 0.5 ? 2 * u * u : 1 - 2 * (1 - u) * (1 - u);
            default -> u;
        };
    }

    /** 어디를 기준으로 휘두르는지 (틱마다 다시 셈 — 쓴 사람을 따라감) */
    public interface Frame {
        /** 축 점 (휘두르는 원의 가운데) */
        Vec3 pivot();

        /** 앞 (수평) */
        Vec3 fwd();
    }

    /**
     * 휘두르기: from 틱 (효과의 나이) 부터 ticks 틱 동안 칼이 frame 의 축 점 둘레를 돎 — 칼자루는 축 점에서 reach 칸 (칼끝 방향으로), 칼끝 방향 =
     * dirAt(앞, a) (a = a0 → a1 도, 빠르기 ease), 넓은 면 = faceAt(앞). 틱마다 한 번 (1틱 보간)
     */
    public static void swing(Blade b, int from, int ticks, Frame frame, double reach, java.util.function.BiFunction<Vec3, Double, Vec3> dirAt,
            java.util.function.Function<Vec3, Vec3> faceAt, double a0, double a1, String ease) {
        int n = Math.max(1, ticks);
        for (int i = 0; i <= n; i++) {
            final int ii = i;
            b.fx.on(from + i, () -> {
                double a = a0 + (a1 - a0) * ease(ii / (double) n, ease);
                Vec3 f = frame.fwd();
                Vec3 d = V.unit(dirAt.apply(f, a));
                b.pose(V.add(frame.pivot(), d, reach), d, faceAt.apply(f), ii == 0 ? 0 : 1);
            });
        }
    }

    /**
     * 몸 둘레를 도는 검 n 자루 (마검 결계 · 칼바람 폭풍): 길이 len 칸 · 반지름 r + flare(나이) 칸 · 발에서 높이 h · 한 틱에 deg 도 (+ = 위에서 보아 시계 —
     * 오른쪽으로 돎), phase = 처음 각. 칼끝이 도는 쪽 · 넓은 면은 위 (tilt 0) 에서 바깥 (tilt 1) 사이로 기울임. 1 ~ until 틱 동안 틱마다 몸을 따라옴
     * (움직인 만큼 앞당김 — 표시 엔티티는 1틱 늦게 따라오므로), 끝 (until 또는 죽음) = 바깥으로 날아가며 작아져 사라짐
     */
    public static Vfx.Fx orbit(net.minecraft.server.level.ServerPlayer sp, String life, int n, double len, double r, double h, double deg, double phase, int until,
            java.util.function.IntToDoubleFunction flare, double tilt, double[] glow, double[] core) {
        Vfx.Fx fx = RuneProps.fx(sp.level(), sp.position(), until + 10);
        Blade[] bl = new Blade[n];
        double mid = len * 0.42;
        Vec3[] last = {sp.position()};
        boolean[] done = {false};
        java.util.function.BiFunction<Integer, Integer, Vec3[]> place = (t, j) -> {
            double th = Math.toRadians(phase + t * deg + j * 360.0 / n);
            Vec3 out = new Vec3(Math.cos(th), 0, Math.sin(th));
            Vec3 tan = deg >= 0 ? new Vec3(-Math.sin(th), 0, Math.cos(th)) : new Vec3(Math.sin(th), 0, -Math.cos(th));
            Vec3 face = V.unit(V.add(V.scale(V.UP, 1 - tilt), out, tilt));
            return new Vec3[] {out, tan, face};
        };
        for (int j = 0; j < n; j++) {
            Vec3[] o = place.apply(0, j);
            Vec3 p = V.up(V.add(sp.position(), o[0], r * 0.5), h);
            bl[j] = blade(fx, V.add(p, o[1], -mid), o[1], o[2], len, 0.2, glow, core);
        }
        fx.on(1, () -> {
            for (Blade b : bl) b.grow(1, 3);
        });
        Runnable retire = () -> {
            if (done[0]) return;
            done[0] = true;
            int age = fx.age();
            for (int j = 0; j < n; j++) {
                Blade b = bl[j];
                Vec3[] o = place.apply(age, j);
                b.grow(0.12, 5);
                b.prop.move(V.add(V.up(b.hilt(), 0.6), o[0], 3.2), 5);
            }
            fx.on(age + 6, () -> {
                for (Blade b : bl) b.remove();
            });
        };
        fx.each(1, until, 1, t -> {
            if (done[0]) return;
            if (!mmorpg.fabric.rune.RuneKit.sameLife(sp, life)) {
                retire.run();
                return;
            }
            Vec3 c = sp.position();
            Vec3 lead = V.sub(c, last[0]);
            last[0] = c;
            if (V.len(lead) > 2) lead = Vec3.ZERO; // 순간 이동
            Vec3 cc = V.add(c, lead);
            double rr = r + (flare == null ? 0 : flare.applyAsDouble(t));
            for (int j = 0; j < n; j++) {
                Vec3[] o = place.apply(t, j);
                Vec3 p = V.up(V.add(cc, o[0], t < 4 ? r * (0.5 + 0.5 * t / 4.0) : rr), h + 0.12 * Math.sin(t * 0.25 + j * 1.7));
                bl[j].pose(V.add(p, o[1], -mid), o[1], o[2], 1);
            }
        });
        fx.on(until, retire);
        return fx;
    }

    /** 맞을 때 바깥으로 튀는 몫: 맞는 틱 (hit0 + i × gap, i < hits) 부터 2틱에 add 칸까지 → 4틱에 걸쳐 돌아옴 */
    public static java.util.function.IntToDoubleFunction flareAt(int hit0, int gap, int hits, double add) {
        return t -> {
            double best = 0;
            for (int i = 0; i < hits; i++) {
                int d = t - (hit0 + i * gap);
                if (d < 0) continue;
                double v = d <= 2 ? d / 2.0 : d <= 6 ? 1 - (d - 2) / 4.0 : 0;
                best = Math.max(best, v);
            }
            return add * best;
        };
    }

    /** 수평 원 위의 방향: 앞 f 를 오른쪽으로 a 도 (+ = 오른쪽 — V.turnY 와 같음), 아래로 dip 도 기울임 */
    public static Vec3 flatDir(Vec3 f, double a, double dip) {
        Vec3 h = V.turnY(f, a);
        double r = Math.toRadians(dip);
        return V.unit(new Vec3(h.x * Math.cos(r), -Math.sin(r), h.z * Math.cos(r)));
    }

    /** 세로 원 위의 방향: 앞 f 에서 위로 a 도 (90 = 하늘, 180 = 뒤, − = 아래) */
    public static Vec3 upDir(Vec3 f, double a) {
        double r = Math.toRadians(a);
        return V.unit(V.add(V.scale(f, Math.cos(r)), V.UP, Math.sin(r)));
    }
}
