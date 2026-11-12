package mmorpg.fabric.vfx;

import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.V;
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

        Duo(Vfx.Fx fx, String sheet, Vec3 at, double[] glow, double[] core) {
            this.fx = fx;
            Vfx.Sheet sh = Vfx.sheet(name(sheet));
            a = fx.sprite(name(sheet), at);
            if (!sh.colored()) a.color(glow);
            b = sh.b() != null ? fx.sprite(sh.b(), at).color(core != null ? core : WHITE) : null;
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
        return new Duo(fx, sheet, at, glow, core);
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
        Duo[] out = new Duo[1];
        int t0 = at(fx, delay);
        fx.on(t0, () -> {
            int base = leftToRight ? 8 : 0;
            Duo d = duo(fx, "swing", pivot, glow, core).fixed(plane(right, fwd)).size(radius * 112.0 / 80.0).frame(base);
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
     * 날아가는 검기 (wave — 가로 초승달, 볼록한 앞이 dir 쪽): from 에서 dir 로 len 칸을 ticks 틱 동안, 폭 width 칸, 면이 dir 둘레로 tilt 도 기욺.
     * 날아가는 동안 깜빡이고 (0 ~ 2) 닿으면 부서짐 (3 ~ 5)
     */
    public static void wave(Vfx.Fx fx, Vec3 from, Vec3 dir, double len, int ticks, double width, double tilt, double[] glow, double[] core, int delay) {
        fx.on(at(fx, delay), () -> {
            Vec3 f = V.unit(dir);
            Vec3 right = V.rightOf(V.flat(f));
            Vec3 up = V.unit(V.cross(right, f));
            double a = Math.toRadians(tilt);
            Vec3 r2 = V.add(V.scale(right, Math.cos(a)), up, Math.sin(a));
            Duo d = duo(fx, "wave", from, glow, core).fixed(plane(r2, f)).size(width / 2.0).frame(0);
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
     * 빛줄기 (streak — 땅과 나란히 눕힘): from 에서 dir 로 len 칸, 굵기 thick 칸. grow 틱 동안 늘어나고 (지나가는 검기를 따라) life 틱 뒤 끊어지며 사라짐
     */
    public static void streak(Vfx.Fx fx, Vec3 from, Vec3 dir, double len, double thick, double[] glow, double[] core, int grow, int life, int delay) {
        fx.on(at(fx, delay), () -> {
            Vec3 f = V.unit(dir);
            Vec3 y = V.unit(V.cross(V.UP, f));
            if (V.dot(y, y) < 1e-6) y = new Vec3(1, 0, 0);
            Duo d = duo(fx, "streak", from, glow, core).fixed(plane(f, y)).size(thick);
            double full = len / (thick * 8.0);
            int t = fx.age();
            if (grow > 0) {
                d.stretch(full * 0.05, 1, 0);
                d.stretch(full, 1, grow);
            } else d.stretch(full, 1, 0);
            for (int k = 2; k < life - 4; k += 2) {
                final int fr = (k / 2) % 2;
                fx.on(t + k, () -> d.frame(fr));
            }
            d.seq(t + Math.max(grow + 1, life - 4), 2, true, 2, 3, 4);
        });
    }

    /** 흰 번개 조각 (shard — 카메라를 봄): at 둘레로 n 개가 spread 칸 튀며 사라짐 */
    public static void shards(Vfx.Fx fx, Vec3 at, int n, double spread, double size, double[] glow, double[] core, int delay) {
        fx.on(at(fx, delay), () -> {
            for (int i = 0; i < n; i++) {
                Vec3 d = V.unit(new Vec3(Kit.rand() - 0.5, Kit.rand() * 0.9 - 0.3, Kit.rand() - 0.5));
                Duo s = duo(fx, "shard", at, glow, core).size(size * rnd(0.7, 1.2)).frame((int) (Kit.rand() * 4)).roll(Kit.rand() * 360, 0);
                s.to(V.add(at, d, spread * rnd(0.6, 1.2)), -1, 4);
                s.until(fx.age() + 4 + (int) (Kit.rand() * 3));
            }
        });
    }

    /** 맞은 자리의 베인 자국 (cutmark — 카메라를 봄, 화면에서 rollDeg 도 기욺): 길이 len 칸 */
    public static void cut(Vfx.Fx fx, Vec3 at, double rollDeg, double len, double[] glow, double[] core, int delay) {
        fx.on(at(fx, delay), () -> {
            Duo d = duo(fx, "cutmark", at, glow, core).size(len / 4.0).roll(rollDeg, 0).frame(0);
            int t = fx.age();
            d.seq(t + 1, 1, false, 1);
            d.seq(t + 3, 2, true, 2, 3);
        });
    }

    /** 번쩍임 (star — 카메라를 봄): 크기 size 칸, 4틱 동안 작아짐 */
    public static void star(Vfx.Fx fx, Vec3 at, double size, double[] glow, double[] core, int delay) {
        fx.on(at(fx, delay), () -> {
            Duo d = duo(fx, "star", at, glow, core).size(size).roll(Kit.rand() * 20 - 10, 0).frame(0);
            d.seq(fx.age() + 1, 1, true, 1, 2, 3);
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
                Duo e = duo(fx, "ember", p, color, null).size(rnd(0.32, 0.55)).frame(0);
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
                Duo e = duo(fx, "ember", p, color, null).size(rnd(0.3, 0.45)).frame(0);
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
                    Duo d = duo(fx, "crack", at, color, null).fixed(Vfx.flat(Kit.rand() * 360)).frame(ii % 6).size(s * 0.55);
                    d.to(null, s, 2);
                    d.fade(fx.age() + Math.max(4, life - 10), 10);
                });
            }
        });
    }

    /** 땅의 충격파 고리 (shock — 땅에 눕힘): 반지름 radius 까지 ticks 틱 동안 커지며 가늘어짐 */
    public static void shock(Vfx.Fx fx, Vec3 ground, double radius, double[] glow, double[] core, int ticks, int delay) {
        fx.on(at(fx, delay), () -> {
            Duo d = duo(fx, "shock", V.up(ground, 0.06), glow, core).fixed(Vfx.flat(Kit.rand() * 360)).size(radius * 2 * 64 / 62.0).frame(0);
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
            Duo d = duo(fx, sheet, at, color, null).fixed(Vfx.flat(yaw0)).size(radius * k * 0.6);
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
            Duo d = duo(fx, "octagon", V.up(c, 0.05), glow, core).fixed(Vfx.flat(yaw)).size(radius * 192 / 88.0).frame(0);
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
            Duo d = duo(fx, "bolt", ground, glow, core).vertical().size(height).frame(f0);
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
            Duo d = duo(fx, "chain", V.up(bottom, height * 0.8), glow, core).vertical().size(height).frame(0);
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
            Duo d = duo(fx, "pillar", ground, glow, core).vertical().size(height).frame(0);
            double sx = width / (height / 3.0);
            d.stretch(sx * 0.25, 1, 0);
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
                Duo d = duo(fx, "panel", V.up(p, 0.02), glow, core).fixed(plane(tan, V.UP)).size(height).frame(i % 4);
                d.stretch(sx, 0.04, 0);
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
                    Duo d = duo(fx, "firepuff", p, null, null).size(s).roll(Kit.rand() * 360, 0).frame(0);
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
            Duo d = duo(fx, "firepuff", at, null, null).size(size).roll(Kit.rand() * 360, 0).frame(3);
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
            Vfx.Prop rock = fx.prop("minecraft:magma_block", from).size(size * 0.45, 0).bright();
            Duo glow = duo(fx, "firepuff", from, null, null).size(size).frame(1);
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
                Duo d = duo(fx, "dust", p, color, null).size(size * rnd(0.7, 1.2)).roll(Kit.rand() * 360, 0).frame(0);
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
                    Vfx.Prop p = fx.prop(b, sunk).size(s, 0);
                    p.turn(q, 0);
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
