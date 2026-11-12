package mmorpg.fabric.vfx;

import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.V;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 2026-11-12 픽셀 효과 (사용자 "지금 스킬들 확인했는데 다 너무 저퀄이야 … 이런 느낌으로 만들어" — 예시 사진 8장): tools/vfx_pixel.py 의 글꼴 rpg:px
 * 그림 (1칸 = 16픽셀 · 어두운 외곽선 → 계열 색 → 흰 심지의 띠 색) 으로 짠 조각. 모드 없는 클라이언트도 같은 모습 (서버가 띄우는 글자 표시 엔티티).
 * <ul>
 * <li>{@link Duo} 두 층 그림: A 층 (계열 빛 색) + B 층 (심지 — 속빛 색), 같은 자리 · 같은 모습 (픽셀이 겹치지 않음 → 같은 면이어도 깜빡이지 않음)</li>
 * <li>조각: 휩쓰는 초승달 {@link #sweep} · 날아가는 검기 {@link #wave} · 빛줄기 {@link #streak} · 번개 조각 {@link #shards} · 베인 자국 {@link #cut} ·
 * 번쩍임 {@link #star} · 불티 {@link #embers} · 바닥 균열 {@link #cracks} · 충격파 고리 {@link #shock} · 번개 {@link #bolt} · 빛기둥 {@link #pillar} ·
 * 팔각형 {@link #octagon} · 고리 {@link #ring} · 감옥 벽 {@link #prison} · 불 폭발 {@link #fire} · 흙먼지 {@link #dust} · 솟는 바위 {@link #rocks} ·
 * 등급 터짐 {@link #impact}</li>
 * </ul>
 * delay = 지금 (효과 나이) 부터 몇 틱 뒤. 크기는 블록 (칸).
 */
public final class Px {
    private Px() {}

    public static final double[] WHITE = {1, 1, 1};

    static String name(String sheet) {
        return "px/" + sheet;
    }

    /** 지금부터 delay 틱 뒤 */
    static int at(Vfx.Fx fx, int delay) {
        return fx.age() + Math.max(0, delay);
    }

    // ------------------------------------------------------------ 두 층 그림

    /** 두 층 그림 (A = 빛 색 · B = 심지 색 — 한 층 판이면 b 없음) */
    public static final class Duo {
        final Vfx.Fx fx;
        public final Vfx.Sprite a;
        public final Vfx.Sprite b;

        Duo(Vfx.Fx fx, String sheet, Vec3 at, double[] glow, double[] core, Consumer<Vfx.Sprite> init) {
            this.fx = fx;
            Vfx.Sheet sh = Vfx.sheet(name(sheet));
            a = fx.sprite(name(sheet), at, s -> {
                if (!sh.colored()) s.color(glow);
                if (init != null) init.accept(s);
            });
            b = sh.b() != null ? fx.sprite(sh.b(), at, s -> {
                s.color(core != null ? core : WHITE);
                if (init != null) init.accept(s);
            }) : null;
        }

        public Duo fixed(Quaternionf q) {
            a.fixed(q);
            if (b != null) b.fixed(q);
            return this;
        }

        public Duo vertical() {
            a.vertical();
            if (b != null) b.vertical();
            return this;
        }

        public Duo size(double s) {
            a.size(s);
            if (b != null) b.size(s);
            return this;
        }

        public Duo frame(int f) {
            a.frame(f);
            if (b != null) b.frame(f);
            return this;
        }

        public Duo to(Vec3 at, double size, int ticks) {
            a.to(at, size, ticks);
            if (b != null) b.to(at, size, ticks);
            return this;
        }

        public Duo face(Quaternionf q, int ticks) {
            a.face(q, ticks);
            if (b != null) b.face(q, ticks);
            return this;
        }

        public Duo stretch(double sx, double sy, int ticks) {
            a.stretch(sx, sy, ticks);
            if (b != null) b.stretch(sx, sy, ticks);
            return this;
        }

        public Duo roll(double deg, int ticks) {
            a.roll(deg, ticks);
            if (b != null) b.roll(deg, ticks);
            return this;
        }

        public Duo alpha(double v) {
            a.alpha(v);
            if (b != null) b.alpha(v);
            return this;
        }

        public Duo fade(int from, int ticks) {
            a.fade(from, ticks);
            if (b != null) b.fade(from, ticks);
            return this;
        }

        /** 나이 t 에 지움 */
        public Duo until(int t) {
            fx.on(t, this::remove);
            return this;
        }

        /** 나이 start 부터 칸 차례 frames 를 every 틱마다 (end = 마지막 칸 다음에 지움) */
        public Duo seq(int start, int every, boolean end, int... frames) {
            for (int i = 0; i < frames.length; i++) {
                final int f = frames[i];
                fx.on(start + i * every, () -> frame(f));
            }
            if (end) until(start + frames.length * every);
            return this;
        }

        public boolean removed() {
            return a.removed();
        }

        public void remove() {
            a.remove();
            if (b != null) b.remove();
        }
    }

    public static Duo duo(Vfx.Fx fx, String sheet, Vec3 at, double[] glow, double[] core) {
        return new Duo(fx, sheet, at, glow, core, null);
    }

    /** 처음 모습 (init — 방향 · 크기 · 칸 · 늘림 …) 을 정한 뒤 띄움: 같은 틱에 준 움직임이 처음 모습에서부터 보간됨 */
    public static Duo duo(Vfx.Fx fx, String sheet, Vec3 at, double[] glow, double[] core, Consumer<Vfx.Sprite> init) {
        return new Duo(fx, sheet, at, glow, core, init);
    }

    /** 앞 f 를 위로 deg 도 들어 올린 방향 (눕힌 참격이 뒤 · 위의 카메라 · 1인칭 눈을 보게 — 수평이면 옆에서 선으로만 보임) */
    public static Vec3 lifted(Vec3 f, double deg) {
        double a = Math.toRadians(deg);
        Vec3 h = V.flat(f);
        return V.unit(V.add(V.scale(h, Math.cos(a)), V.UP, Math.sin(a)));
    }

    /** 그림 면의 방향: 그림 오른쪽 = x, 그림 위 = y (앞면 = x × y 쪽) */
    public static Quaternionf plane(Vec3 x, Vec3 y) {
        Vec3 ex = V.unit(x);
        Vec3 ez = V.unit(V.cross(ex, y));
        Vec3 ey = V.cross(ez, ex);
        return Vfx.basis(ex, ey, ez);
    }

    /** 땅에 눕힌 그림 (앞면 위 · 그림 위 = fwd 쪽) */
    public static Quaternionf flat(Vec3 fwd) {
        Vec3 f = V.flat(fwd);
        return plane(V.rightOf(f), f);
    }

    static Vec3 ground(ServerLevel level, Vec3 p) {
        return VfxKit.ground(level, p);
    }

    static double rnd(double a, double b) {
        return a + Kit.rand() * (b - a);
    }

    // ------------------------------------------------------------ 참격

    /**
     * 휩쓰는 초승달 (swing): 축 pivot (휘두르는 사람 — 반원의 가운데) · 그림 오른쪽 right · 그림 위 fwd (앞면 = right × fwd), 반지름 radius.
     * leftToRight = 왼쪽 → 오른쪽 (아니면 오른쪽 → 왼쪽). every = 칸마다 틱 (드러남 0 ~ 3 · 머묾 4 · 부서짐 5 ~ 7)
     */
    public static Duo sweep(Vfx.Fx fx, Vec3 pivot, Vec3 right, Vec3 fwd, double radius, double[] glow, double[] core, boolean leftToRight, int delay,
            int every) {
        return swing(fx, pivot, plane(right, fwd), radius, 1, glow, core, leftToRight, delay, every);
    }

    /**
     * 2026-11-12 앞을 보는 초승달 (사용자 "앞을 보게 세움" — 눕힌 초승달이 위를 보던 것): 몸 앞의 세운 면 (쓴 사람 · 뒤 카메라 쪽을 봄 — 글자는 양면으로 보임)에
     * 반원을 납작하게 (폭 width 칸 = 지름 · 높이 height 칸) — 뒤에서 보면 앞을 가로로 휩쓰는 참격. base = 반원의 축 (가운데 아래), f = 앞,
     * leftToRight = 왼쪽 → 오른쪽 (아니면 오른쪽 → 왼쪽)
     */
    public static Duo sweepFront(Vfx.Fx fx, Vec3 base, Vec3 f, double width, double height, double[] glow, double[] core, boolean leftToRight, int delay,
            int every) {
        double radius = width / 2;
        return swing(fx, base, plane(V.rightOf(V.flat(f)), V.UP), radius, height / radius, glow, core, leftToRight, delay, every);
    }

    private static Duo swing(Vfx.Fx fx, Vec3 pivot, Quaternionf q, double radius, double sy, double[] glow, double[] core, boolean leftToRight, int delay,
            int every) {
        Duo[] out = new Duo[1];
        int t0 = at(fx, delay);
        fx.on(t0, () -> {
            int base = leftToRight ? 8 : 0;
            Duo d = duo(fx, "swing", pivot, glow, core, sp -> sp.fixed(q).size(radius * 112.0 / 80.0).stretch(1, sy, 0).frame(base));
            int e = Math.max(1, every);
            int t = fx.age();
            d.seq(t + e, e, false, base + 1, base + 2, base + 3);
            d.seq(t + 4 * e, e * 2, false, base + 4);
            d.seq(t + 6 * e, e, true, base + 5, base + 6, base + 7);
            out[0] = d;
        });
        return out[0];
    }

    /** 양면 초승달 (세워 쓰는 참격 — 어느 쪽에서 봐도 보임): 뒷면은 거울 그림 (같은 차례로 드러남) */
    public static void sweepBoth(Vfx.Fx fx, Vec3 pivot, Vec3 right, Vec3 fwd, double radius, double[] glow, double[] core, boolean leftToRight, int delay,
            int every) {
        sweep(fx, pivot, right, fwd, radius, glow, core, leftToRight, delay, every);
        sweep(fx, pivot, V.scale(right, -1), fwd, radius, glow, core, !leftToRight, delay, every);
    }

    /**
     * 날아가는 검기 (wave — 가로 초승달, 볼록한 앞이 dir 쪽): from 에서 dir 로 len 칸을 ticks 틱 동안, 폭 width 칸. 면은 dir 둘레로 tilt 도 기울고 앞이
     * lift 도 들림 (앞면이 뒤 · 위를 봄 — 쓴 사람 · 3인칭 카메라에 넓게 보임). 날아가는 동안 깜빡이고 (0 ~ 2) 닿으면 부서짐 (3 ~ 5)
     */
    public static void wave(Vfx.Fx fx, Vec3 from, Vec3 dir, double len, int ticks, double width, double tilt, double lift, double[] glow, double[] core,
            int delay) {
        fx.on(at(fx, delay), () -> {
            Vec3 f = V.unit(dir);
            Vec3 right = V.rightOf(V.flat(f));
            Vec3 up = V.unit(V.cross(right, f));
            double a = Math.toRadians(tilt);
            Vec3 r2 = V.add(V.scale(right, Math.cos(a)), up, Math.sin(a));
            double b = Math.toRadians(lift);
            Vec3 y2 = V.add(V.scale(f, Math.cos(b)), up, Math.sin(b));
            Quaternionf q = plane(r2, y2);
            Duo d = duo(fx, "wave", from, glow, core, sp -> sp.fixed(q).size(width / 2.0).frame(0));
            int t = fx.age();
            int n = Math.max(1, ticks);
            d.to(V.add(from, f, len), -1, n);
            for (int k = 2; k < n; k += 2) {
                final int fr = (k / 2) % 3;
                fx.on(t + k, () -> d.frame(fr));
            }
            d.seq(t + n, 1, true, 3, 4, 5);
        });
    }

    /**
     * 빛줄기 (streak): from 에서 dir 로 len 칸, 굵기 thick 칸 — 땅과 나란한 면 + (cross) 세운 면 (어느 쪽에서 봐도 선이 아니게). grow 틱 동안 늘어나고
     * (지나가는 검기를 따라) life 틱 뒤 끊어지며 사라짐
     */
    public static void streak(Vfx.Fx fx, Vec3 from, Vec3 dir, double len, double thick, double[] glow, double[] core, int grow, int life, boolean cross,
            int delay) {
        fx.on(at(fx, delay), () -> {
            Vec3 f = V.unit(dir);
            Vec3 y = V.unit(V.cross(V.UP, f));
            if (V.dot(y, y) < 1e-6) y = new Vec3(1, 0, 0);
            double full = len / (thick * 8.0);
            int t = fx.age();
            Vec3[] ups = cross ? new Vec3[] {y, V.unit(V.cross(f, y))} : new Vec3[] {y};
            for (Vec3 yy : ups) {
                Quaternionf q = plane(f, yy);
                Duo d = duo(fx, "streak", from, glow, core, sp -> sp.fixed(q).size(thick).stretch(grow > 0 ? full * 0.04 : full, 1, 0));
                if (grow > 0) d.stretch(full, 1, grow);
                for (int k = 2; k < life - 4; k += 2) {
                    final int fr = (k / 2) % 2;
                    fx.on(t + k, () -> d.frame(fr));
                }
                d.seq(t + Math.max(grow + 1, life - 4), 2, true, 2, 3, 4);
            }
        });
    }

    /** 흰 번개 조각 (shard — 카메라를 봄): at 둘레로 n 개가 spread 칸 튀며 사라짐 */
    public static void shards(Vfx.Fx fx, Vec3 at, int n, double spread, double size, double[] glow, double[] core, int delay) {
        fx.on(at(fx, delay), () -> {
            for (int i = 0; i < n; i++) {
                Vec3 d = V.unit(new Vec3(Kit.rand() - 0.5, Kit.rand() * 0.9 - 0.3, Kit.rand() - 0.5));
                double sz = size * rnd(0.7, 1.2);
                int fr = (int) (Kit.rand() * 4);
                double rl = Kit.rand() * 360;
                Duo s = duo(fx, "shard", at, glow, core, sp -> sp.size(sz).frame(fr).roll(rl, 0));
                s.to(V.add(at, d, spread * rnd(0.6, 1.2)), -1, 4);
                s.until(fx.age() + 4 + (int) (Kit.rand() * 3));
            }
        });
    }

    /** 맞은 자리의 베인 자국 (cutmark — 카메라를 봄, 화면에서 rollDeg 도 기욺): 길이 len 칸 */
    public static void cut(Vfx.Fx fx, Vec3 at, double rollDeg, double len, double[] glow, double[] core, int delay) {
        fx.on(at(fx, delay), () -> {
            Duo d = duo(fx, "cutmark", at, glow, core, sp -> sp.size(len / 4.0).roll(rollDeg, 0).frame(0));
            int t = fx.age();
            d.seq(t + 1, 1, false, 1);
            d.seq(t + 3, 2, true, 2, 3);
        });
    }

    /** 번쩍임 (star — 카메라를 봄): 크기 size 칸, 4틱 동안 작아짐 */
    public static void star(Vfx.Fx fx, Vec3 at, double size, double[] glow, double[] core, int delay) {
        fx.on(at(fx, delay), () -> {
            double rl = Kit.rand() * 20 - 10;
            Duo d = duo(fx, "star", at, glow, core, sp -> sp.size(size).roll(rl, 0).frame(0));
            d.seq(fx.age() + 1, 1, true, 1, 2, 3);
        });
    }

    // ------------------------------------------------------------ 마법진 · 사슬 · 번개 줄 · 구슬 (2026-11-12 예전 구성 + 픽셀 그림 — 사용자 "수정 전 상태에 지금 그라데이션만")

    /**
     * 마법진 (sigil — 땅에 눕힘, 0 = 육각별 · 1 = 칼 여덟): 바깥 고리 반지름 radius, 4틱에 커지며 나타나 spin 도/틱으로 돌고 life 틱 뒤 옅어짐
     */
    public static Duo sigil(Vfx.Fx fx, Vec3 at, double radius, int frame, double spin, double[] glow, double[] core, int life, int delay) {
        Duo[] out = new Duo[1];
        fx.on(at(fx, delay), () -> {
            double k = 192 / 90.0;
            double yaw0 = Kit.rand() * 360;
            Duo d = duo(fx, "sigil", at, glow, core, sp -> sp.fixed(Vfx.flat(yaw0)).size(radius * k * 0.7).frame(frame));
            d.to(null, radius * k, 4);
            int t = fx.age();
            for (int s = 4; s < life; s += 8) {
                final double yaw = yaw0 + spin * (s + 8);
                fx.on(t + s, () -> d.face(Vfx.flat(yaw), 8));
            }
            d.fade(t + Math.max(4, life - 6), 6);
            out[0] = d;
        });
        return out[0];
    }

    /**
     * 사슬 줄 (chainh — a 에서 b 까지 눕혀 늘림, 3칸마다 한 마디 판): life 틱 동안, flow 틱마다 고리 차례가 바뀜 (0 = 그대로). 세운 면 (양면으로 보임)
     */
    public static void chainLine(Vfx.Fx fx, Vec3 a, Vec3 b, double[] glow, double[] core, int life, int flow, int delay) {
        fx.on(at(fx, delay), () -> {
            Vec3 d = V.sub(b, a);
            double len = V.len(d);
            if (len < 0.2) return;
            Vec3 dir = V.scale(d, 1 / len);
            Vec3 x = V.sub(V.UP, V.scale(dir, V.dot(V.UP, dir)));
            if (V.len(x) < 1e-3) x = new Vec3(1, 0, 0);
            Quaternionf q = plane(V.unit(x), dir);
            int n = Math.max(1, (int) Math.ceil(len / 3.2));
            double seg = len / n;
            int t = fx.age();
            for (int i = 0; i < n; i++) {
                Vec3 mid = V.add(a, dir, seg * (i + 0.5));
                final int f0 = i % 2;
                Duo c = duo(fx, "chainh", mid, glow, core, sp -> sp.fixed(q).size(seg * 1.02).frame(f0));
                if (flow > 0) {
                    for (int k = flow; k < life; k += flow) {
                        final int fr = (f0 + k / flow) % 2;
                        fx.on(t + k, () -> c.frame(fr));
                    }
                }
                c.until(t + life);
            }
        });
    }

    /** 번개 줄 (bolt — b 에서 a 까지 눕혀 늘림, 세운 면): life 틱 동안 모양이 바뀌며 번쩍 → 사라짐 (테두리 번개 · 가운데에서 갇힌 적에게 튀는 번개) */
    public static void boltLine(Vfx.Fx fx, Vec3 a, Vec3 b, double[] glow, double[] core, int life, int delay) {
        fx.on(at(fx, delay), () -> {
            Vec3 d = V.sub(a, b);
            double len = V.len(d);
            if (len < 0.2) return;
            Vec3 y = V.scale(d, 1 / len);
            Vec3 x = V.sub(V.UP, V.scale(y, V.dot(V.UP, y)));
            if (V.len(x) < 1e-3) x = new Vec3(1, 0, 0);
            Quaternionf q = plane(V.unit(x), y);
            int f0 = (int) (Kit.rand() * 4);
            Duo bolt = duo(fx, "bolt", b, glow, core, sp -> sp.fixed(q).size(len).frame(f0));
            int t = fx.age();
            for (int k = 1; k < life; k++) {
                final int fr = (f0 + k) % 4;
                fx.on(t + k, () -> bolt.frame(fr));
            }
            bolt.until(t + Math.max(2, life));
        });
    }

    /** 번개 구슬 (orb — 카메라를 봄): 크기 s0 에서 s1 까지 grow 틱에 커지며 life 틱 동안 튀는 번개가 바뀜 */
    public static void orb(Vfx.Fx fx, Vec3 at, double s0, double s1, int grow, double[] glow, double[] core, int life, int delay) {
        fx.on(at(fx, delay), () -> {
            Duo o = duo(fx, "orb", at, glow, core, sp -> sp.size(s0).frame(0));
            o.to(null, s1, Math.max(1, grow));
            int t = fx.age();
            for (int k = 1; k < life; k++) {
                final int fr = k % 4;
                fx.on(t + k, () -> o.frame(fr));
            }
            o.until(t + life);
        });
    }

    // ------------------------------------------------------------ 불티 · 균열 · 고리

    /**
     * 불티 (ember — 네모, 카메라를 봄, 계열 색): c 둘레 반지름 radius · 높이 0.2 ~ 1.4 에 n 개 — 처음 4틱에 바깥 · 위로 튀고 (burst 칸) 남은 life 틱 동안
     * rise 칸 떠오르며 작아짐
     */
    public static void embers(Vfx.Fx fx, Vec3 c, int n, double radius, double burst, double rise, double[] color, int life, int delay) {
        fx.on(at(fx, delay), () -> {
            int t = fx.age();
            for (int i = 0; i < n; i++) {
                double a = Kit.rand() * Math.PI * 2;
                double r = radius * Math.sqrt(Kit.rand());
                Vec3 p = new Vec3(c.x + Math.cos(a) * r, c.y + rnd(0.15, 1.3), c.z + Math.sin(a) * r);
                Vec3 out = new Vec3(Math.cos(a), rnd(0.4, 1.2), Math.sin(a));
                double sz = rnd(0.32, 0.55);
                Duo e = duo(fx, "ember", p, color, null, sp -> sp.size(sz).frame(0));
                int l = Math.max(8, life + (int) (Kit.rand() * 8) - 4);
                Vec3 p1 = V.add(p, out, burst * rnd(0.5, 1.0));
                e.to(p1, -1, 4);
                Vec3 p2 = V.add(V.up(p1, rise * rnd(0.6, 1.2)), new Vec3(Kit.rand() - 0.5, 0, Kit.rand() - 0.5), 0.4);
                fx.on(t + 4, () -> e.to(p2, -1, Math.max(1, l - 4)));
                e.seq(t + (int) (l * 0.6), Math.max(1, l / 7), true, 1, 2, 3);
            }
        });
    }

    /** 빨려 드는 불티 (ember — 흡혈 · 모임): from 둘레의 n 개가 위로 휘며 to 로 ticks 틱 동안 날아감 */
    public static void drain(Vfx.Fx fx, Vec3 from, Vec3 to, int n, double[] color, int ticks, int delay) {
        fx.on(at(fx, delay), () -> {
            int t = fx.age();
            int half = Math.max(1, ticks / 2);
            for (int i = 0; i < n; i++) {
                Vec3 p = V.add(from, new Vec3(Kit.rand() - 0.5, Kit.rand() - 0.3, Kit.rand() - 0.5), 0.6);
                Vec3 mid = V.up(V.lerp(p, to, 0.5), rnd(0.5, 1.1));
                double sz = rnd(0.3, 0.45);
                Duo e = duo(fx, "ember", p, color, null, sp -> sp.size(sz).frame(0));
                int t0 = t + (int) (Kit.rand() * 3);
                fx.on(t0, () -> e.to(mid, -1, half));
                fx.on(t0 + half, () -> e.to(to, -1, ticks - half));
                e.seq(t0 + ticks - 2, 1, true, 1, 2);
            }
        });
    }

    /**
     * 바닥 균열 (crack — 땅에 눕힘, 검은 갈래 · 계열 색으로 달아오른 가운데): c 둘레 반지름 radius 안에 n 개, 크기 size 칸 안팎 — 가운데에서 바깥으로
     * spread 틱에 걸쳐 차례로 생기고 life 틱 뒤 옅어짐
     */
    public static void cracks(Vfx.Fx fx, Vec3 c, int n, double radius, double size, double[] color, int spread, int life, int delay) {
        fx.on(at(fx, delay), () -> {
            ServerLevel level = fx.level();
            int t = fx.age();
            for (int i = 0; i < n; i++) {
                double a = Kit.rand() * Math.PI * 2;
                double u = i == 0 ? 0 : Math.sqrt(Kit.rand());
                Vec3 p = ground(level, new Vec3(c.x + Math.cos(a) * radius * u, c.y + 0.5, c.z + Math.sin(a) * radius * u));
                Vec3 at = V.up(p, 0.03 + (i % 7) * 0.004);
                double s = size * (i == 0 ? 1.4 : rnd(0.65, 1.15));
                int t0 = t + (int) Math.round(u * spread);
                final int ii = i;
                fx.on(t0, () -> {
                    Quaternionf q = Vfx.flat(Kit.rand() * 360);
                    Duo d = duo(fx, "crack", at, color, null, sp -> sp.fixed(q).frame(ii % 6).size(s * 0.55));
                    d.to(null, s, 2);
                    d.fade(fx.age() + Math.max(4, life - 10), 10);
                });
            }
        });
    }

    /** 땅의 충격파 고리 (shock — 땅에 눕힘): 반지름 radius 까지 ticks 틱 동안 커지며 가늘어짐 */
    public static void shock(Vfx.Fx fx, Vec3 ground, double radius, double[] glow, double[] core, int ticks, int delay) {
        fx.on(at(fx, delay), () -> {
            Quaternionf q = Vfx.flat(Kit.rand() * 360);
            Duo d = duo(fx, "shock", V.up(ground, 0.06), glow, core, sp -> sp.fixed(q).size(radius * 2 * 64 / 62.0).frame(0));
            int e = Math.max(1, ticks / 6);
            d.seq(fx.age() + e, e, true, 1, 2, 3, 4, 5);
        });
    }

    /**
     * 고리 (one-layer: dotring 반짝이는 점 · darkring 짙은 띠) — at 에 눕힘, 반지름 radius (점 · 띠가 지나는 자리), spin 도/틱으로 돎, life 틱 (끝에 옅어짐)
     */
    public static Duo ring(Vfx.Fx fx, String sheet, Vec3 at, double radius, double spin, double[] color, int life, int delay) {
        Duo[] out = new Duo[1];
        fx.on(at(fx, delay), () -> {
            double k = sheet.equals("darkring") ? 128 / 53.0 : 128 / 58.0;
            double yaw0 = Kit.rand() * 360;
            Duo d = duo(fx, sheet, at, color, null, sp -> sp.fixed(Vfx.flat(yaw0)).size(radius * k * 0.6));
            d.to(null, radius * k, 4);
            int t = fx.age();
            for (int s = 4; s < life; s += 8) {
                final double yaw = yaw0 + spin * (s + 8);
                final int fr = (s / 8) % 2;
                fx.on(t + s, () -> {
                    d.face(Vfx.flat(yaw), 8);
                    d.frame(fr);
                });
            }
            d.fade(t + Math.max(4, life - 6), 6);
            out[0] = d;
        });
        return out[0];
    }

    /**
     * 팔각형 (octagon — 땅에 눕힘): 꼭짓점 반지름 radius, 한 변씩 그려진 뒤 (8틱) life 틱 동안 맥동, 끝에 옅어짐. yaw = 돌린 각 (도)
     */
    public static Duo octagon(Vfx.Fx fx, Vec3 c, double radius, double yaw, double[] glow, double[] core, int life, int delay) {
        Duo[] out = new Duo[1];
        fx.on(at(fx, delay), () -> {
            Duo d = duo(fx, "octagon", V.up(c, 0.05), glow, core, sp -> sp.fixed(Vfx.flat(yaw)).size(radius * 192 / 88.0).frame(0));
            int t = fx.age();
            d.seq(t + 1, 1, false, 1, 2, 3, 4, 5, 6, 7, 8);
            for (int s = 12; s < life - 6; s += 4) {
                final int fr = (s / 4) % 2 == 0 ? 9 : 8;
                fx.on(t + s, () -> d.frame(fr));
            }
            d.fade(t + Math.max(10, life - 6), 6);
            out[0] = d;
        });
        return out[0];
    }

    // ------------------------------------------------------------ 번개 · 빛기둥 · 감옥

    /**
     * 번개 (bolt — 세로축으로 카메라를 봄): 땅 ground 에서 height 칸 위까지, life 틱 동안 모양이 바뀌며 번쩍 → 사라짐. 떨어진 곳: 번쩍임 · 작은 충격파 · 균열 ·
     * 불티 (impact = true)
     */
    public static void bolt(Vfx.Fx fx, Vec3 ground, double height, double[] glow, double[] core, int life, boolean impact, int delay) {
        fx.on(at(fx, delay), () -> {
            int f0 = (int) (Kit.rand() * 4);
            Duo d = duo(fx, "bolt", ground, glow, core, sp -> sp.vertical().size(height).frame(f0));
            int t = fx.age();
            for (int k = 1; k < life; k++) {
                final int fr = (f0 + k) % 4;
                fx.on(t + k, () -> d.frame(fr));
            }
            d.until(t + Math.max(2, life));
            if (impact) {
                star(fx, V.up(ground, 0.4), 2.2, glow, core, 0);
                shock(fx, ground, 1.6, glow, core, 6, 0);
                cracks(fx, ground, 2, 0.5, 2.2, glow, 0, 30, 0);
                embers(fx, ground, 6, 0.4, 1.0, 0.8, glow, 14, 0);
            }
        });
    }

    /**
     * 사슬이 내리침 (chain — 세로축으로 카메라를 봄): 끝이 bottom 에 꽂히는 height 칸 사슬이 위에서 drop 틱에 떨어져 박힘 → life 틱 동안 흐름 → 사라짐
     */
    public static void chain(Vfx.Fx fx, Vec3 bottom, double height, double[] glow, double[] core, int drop, int life, int delay) {
        fx.on(at(fx, delay), () -> {
            Duo d = duo(fx, "chain", V.up(bottom, height * 0.8), glow, core, sp -> sp.vertical().size(height).frame(0));
            int t = fx.age();
            d.to(bottom, -1, Math.max(1, drop));
            for (int k = 1; k < life; k++) {
                final int fr = k % 2;
                fx.on(t + drop + k, () -> d.frame(fr));
            }
            d.until(t + drop + life);
        });
    }

    /**
     * 빛기둥 (pillar — 세로축으로 카메라를 봄): 땅 ground 에서 height 칸, 폭 width 칸 — 가늘게 내리꽂혀 넓어지고 (2틱) life 틱 동안 흐름 (0 ~ 3), 끝에 끊어짐
     */
    public static Duo pillar(Vfx.Fx fx, Vec3 ground, double height, double width, double[] glow, double[] core, int life, int delay) {
        Duo[] out = new Duo[1];
        fx.on(at(fx, delay), () -> {
            double sx = width / (height / 3.0);
            Duo d = duo(fx, "pillar", ground, glow, core, sp -> sp.vertical().size(height).frame(0).stretch(sx * 0.25, 1, 0));
            d.stretch(sx, 1, 2);
            int t = fx.age();
            for (int k = 2; k < life; k += 2) {
                final int fr = (k / 2) % 4;
                fx.on(t + k, () -> d.frame(fr));
            }
            d.seq(t + life, 1, true, 4, 5, 6);
            out[0] = d;
        });
        return out[0];
    }

    /**
     * 감옥 벽 (panel 여럿이 원통 — 바깥을 봄): 가운데 c (땅) · 반지름 radius · 높이 height · 판 n 장, 땅에서 솟고 (3틱) life 틱 동안 번개 무늬가 바뀜 → 가라앉음
     */
    public static void prison(Vfx.Fx fx, Vec3 c, double radius, double height, int n, double[] glow, double[] core, int life, int delay) {
        fx.on(at(fx, delay), () -> {
            int t = fx.age();
            double chord = 2 * radius * Math.sin(Math.PI / n) * 1.04;
            double sx = chord / (height * 0.6);
            for (int i = 0; i < n; i++) {
                double a = (i + 0.5) / n * Math.PI * 2;
                Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                Vec3 tan = new Vec3(Math.sin(a), 0, -Math.cos(a));
                Vec3 p = V.add(c, out, radius * Math.cos(Math.PI / n));
                Quaternionf q = plane(tan, V.UP);
                final int f0 = i % 4;
                Duo d = duo(fx, "panel", V.up(p, 0.02), glow, core, sp -> sp.fixed(q).size(height).frame(f0).stretch(sx, 0.04, 0));
                final int ii = i;
                fx.on(t + (i % 3), () -> d.stretch(sx, 1, 3));
                for (int k = 2; k < life; k += 2) {
                    final int fr = (ii + k / 2) % 4;
                    fx.on(t + k, () -> d.frame(fr));
                }
                fx.on(t + life, () -> d.stretch(sx, 0.04, 4));
                d.until(t + life + 4);
            }
        });
    }

    // ------------------------------------------------------------ 불 · 먼지 · 바위

    /**
     * 불 폭발 덩어리 (firepuff — 미리 칠함, 카메라를 봄): c 둘레 반지름 radius 에 n 개 (크기 size 안팎, 0 ~ 3틱 차례로) — 부풀며 터져 식고 연기로 흩어짐
     * (14틱 안팎), 조금 떠오름
     */
    public static void fire(Vfx.Fx fx, Vec3 c, int n, double radius, double size, int delay) {
        fx.on(at(fx, delay), () -> {
            int t = fx.age();
            for (int i = 0; i < n; i++) {
                double a = Kit.rand() * Math.PI * 2;
                double r = i == 0 ? 0 : radius * Math.sqrt(Kit.rand());
                Vec3 p = new Vec3(c.x + Math.cos(a) * r, c.y + rnd(0.2, 1.1) + (i == 0 ? 0.4 : 0), c.z + Math.sin(a) * r);
                double s = size * (i == 0 ? 1.25 : rnd(0.6, 1.05));
                int t0 = t + (i == 0 ? 0 : (int) (Kit.rand() * 4));
                fx.on(t0, () -> {
                    double rl = Kit.rand() * 360;
                    Duo d = duo(fx, "firepuff", p, null, null, sp -> sp.size(s).roll(rl, 0).frame(0));
                    d.to(V.up(p, rnd(0.4, 0.9)), -1, 14);
                    int u = fx.age();
                    d.seq(u + 1, 1, false, 1, 2);
                    d.seq(u + 3, 2, false, 3, 4);
                    d.seq(u + 7, 2, true, 5, 6, 7);
                });
            }
        });
    }

    /** 연기 꼬리 한 덩이 (firepuff 의 식는 칸 — 날아가는 불덩이 뒤): 크기 size, 8틱 */
    public static void smoke(Vfx.Fx fx, Vec3 at, double size, int delay) {
        fx.on(at(fx, delay), () -> {
            double rl = Kit.rand() * 360;
            Duo d = duo(fx, "firepuff", at, null, null, sp -> sp.size(size).roll(rl, 0).frame(3));
            d.to(V.up(at, 0.3), -1, 8);
            d.seq(fx.age() + 2, 2, true, 5, 6, 7);
        });
    }

    /**
     * 날아가는 불덩이 (포물선): 3D 마그마 덩어리 (블록 — 돌며) + 둘레의 불 (firepuff 1 · 2 깜빡임) + 연기 꼬리 — from 에서 to 로 ticks 틱 (중력 g 칸/초²)
     */
    public static void fireball(Vfx.Fx fx, Vec3 from, Vec3 to, int ticks, double size, double gravity, int delay) {
        fx.on(at(fx, delay), () -> {
            double tt = ticks / 20.0;
            Vec3 v = new Vec3((to.x - from.x) / tt, (to.y - from.y) / tt + gravity * tt / 2, (to.z - from.z) / tt);
            Vfx.Prop rock = fx.prop("minecraft:magma_block", from, pr -> pr.size(size * 0.45, 0).bright());
            Duo glow = duo(fx, "firepuff", from, null, null, sp -> sp.size(size).frame(1));
            int t = fx.age();
            for (int i = 1; i <= ticks; i++) {
                double s = i / 20.0;
                Vec3 p = new Vec3(from.x + v.x * s, from.y + v.y * s - gravity * s * s / 2, from.z + v.z * s);
                Quaternionf q = new Quaternionf().rotateXYZ((float) (s * 9), (float) (s * 7), 0);
                final int ii = i;
                fx.on(t + i - 1, () -> {
                    rock.to(p, q, -1, 1);
                    glow.to(p, -1, 1);
                    glow.frame(1 + ii % 2);
                    if (ii % 2 == 0) smoke(fx, p, size * 0.7, 1);
                });
            }
            fx.on(t + ticks, () -> {
                rock.remove();
                glow.remove();
            });
        });
    }

    /** 흙먼지 (dust — 카메라를 봄, color 로 물들임): 땅 c 둘레 반지름 radius 에 n 개, 부풀며 떠올라 흩어짐 (12틱) */
    public static void dust(Vfx.Fx fx, Vec3 c, int n, double radius, double size, double[] color, int delay) {
        fx.on(at(fx, delay), () -> {
            for (int i = 0; i < n; i++) {
                double a = Kit.rand() * Math.PI * 2;
                double r = radius * Math.sqrt(Kit.rand());
                Vec3 p = new Vec3(c.x + Math.cos(a) * r, c.y + rnd(0.2, 0.6), c.z + Math.sin(a) * r);
                double sz = size * rnd(0.7, 1.2);
                double rl = Kit.rand() * 360;
                Duo d = duo(fx, "dust", p, color, null, sp -> sp.size(sz).roll(rl, 0).frame(0));
                d.to(V.up(V.add(p, new Vec3(Math.cos(a), 0, Math.sin(a)), 0.5), 0.7), -1, 12);
                d.seq(fx.age() + 2, 2, true, 1, 2, 3, 4, 5);
            }
        });
    }

    /**
     * 솟는 바위 (블록 아이템 — 진짜 블록 질감): c 둘레 반지름 radius 안에 n 개 (크기 size 안팎) 가 땅속에서 rise 틱에 솟아 hold 틱 머문 뒤 가라앉음.
     * 가운데의 큰 바위 하나 (big = true)
     */
    public static void rocks(Vfx.Fx fx, Vec3 c, int n, double radius, double size, String[] blocks, int rise, int hold, boolean big, int delay) {
        fx.on(at(fx, delay), () -> {
            ServerLevel level = fx.level();
            int t = fx.age();
            int count = n + (big ? 1 : 0);
            for (int i = 0; i < count; i++) {
                boolean center = big && i == n;
                double a = (i + Kit.rand() * 0.7) / Math.max(1, n) * Math.PI * 2;
                double r = center ? 0 : radius * rnd(0.35, 1.0);
                Vec3 g = ground(level, new Vec3(c.x + Math.cos(a) * r, c.y + 0.5, c.z + Math.sin(a) * r));
                double s = center ? size * 1.7 : size * rnd(0.6, 1.15);
                Vec3 out = center ? V.UP : V.unit(new Vec3(Math.cos(a), 1.6, Math.sin(a)));
                Quaternionf q = new Quaternionf().rotateY((float) (Kit.rand() * Math.PI * 2)).rotateX((float) Math.toRadians(rnd(-28, 28)))
                        .rotateZ((float) Math.toRadians(rnd(-28, 28)));
                Vec3 sunk = V.up(g, -s * 0.9);
                Vec3 up = V.add(V.up(g, s * 0.12), out, s * 0.18);
                String b = blocks[i % blocks.length];
                int t0 = t + (center ? 0 : (int) (r / Math.max(0.5, radius) * 3));
                fx.on(t0, () -> {
                    Vfx.Prop p = fx.prop(b, sunk, pr -> pr.size(s, 0).turn(q, 0).bright());
                    p.to(up, null, -1, Math.max(1, rise));
                    fx.on(fx.age() + rise + hold, () -> p.to(V.up(g, -s * 0.95), null, s * 0.6, 7));
                    fx.on(fx.age() + rise + hold + 7, p::remove);
                });
            }
        });
    }

    // ------------------------------------------------------------ 등급 터짐

    /**
     * 등급 터짐 (RuneBoost.impact 의 픽셀판): 땅 g · 반지름 r — 충격파 고리 · 번쩍임 · 균열 · 불티 (등급이 높을수록 많고 큼, 영웅 이상 두 번째 고리 · 번개 조각)
     */
    public static void impact(Vfx.Fx fx, Vec3 g, double r, double[] glow, double[] core, String grade, int delay) {
        int t = switch (grade) {
            case "common" -> 1;
            case "rare" -> 2;
            case "noble" -> 3;
            case "hero" -> 4;
            default -> 5;
        };
        shock(fx, g, r * (0.55 + 0.09 * t), glow, core, 6 + t, delay);
        star(fx, V.up(g, 0.9), 1.4 + 0.35 * t, glow, core, delay);
        cracks(fx, g, 1 + t, r * 0.55, 2.0, glow, 3, 26 + 4 * t, delay);
        embers(fx, g, 4 + 3 * t, r * 0.35, 1.2, 1.0, glow, 16 + 2 * t, delay);
        if (t >= 3) shards(fx, V.up(g, 0.9), 2 + t, 1.6, 0.9, glow, core, delay + 1);
        if (t >= 4) shock(fx, g, r * 0.95, core, WHITE, 9, delay + 3);
    }
}
