package mmorpg.fabric.vfx;

import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 2026-11-11 Vfx 로 짠 흔한 2D · 입체 조각 (룬 · 직업 스킬이 같이 씀): 터짐 · 땅의 충격파 · 갈라진 땅 · 휘두른 초승달 · X 베기 · 불꽃 튐 · 피 튐 · 흙먼지 ·
 * 튀는 돌 · 빛 무리. delay = 지금부터 몇 틱 뒤 (0 = 바로) — 효과 하나에 여럿을 이어 붙여 한 장면을 짬.
 */
public final class VfxKit {
    private VfxKit() {}

    public static final double[] WHITE = {1, 1, 1};
    public static final double[] DUST = {0.62, 0.56, 0.48};
    public static final double[] ROCK = {0.42, 0.38, 0.36};

    /** 터짐 (burst 0 ~ 6 — 별처럼 터진 뒤 고리로 퍼짐) + 흰 심지 (flare): size = 지름 (칸) */
    public static void burst(Vfx.Fx fx, Vec3 at, double size, double[] color, int delay) {
        fx.on(fx.age() + delay, () -> {
            Vfx.Sprite s = fx.sprite("burst", at).color(color).size(size * 0.6);
            s.to(null, size, 4);
            s.play(fx.age(), 0, 7, 1, false);
            Vfx.Sprite c = fx.sprite("flare", at).color(Vfx.mix(color, WHITE, 0.7)).size(size * 0.7).roll(Kit.rand() * 90, 0);
            c.to(null, size * 0.25, 5);
            c.fade(fx.age() + 1, 4);
        });
    }

    /** 땅에 눕힌 충격파 고리 (shock — 커지며 가늘어짐): radius = 끝 반지름 */
    public static void ring(Vfx.Fx fx, Vec3 ground, double radius, double[] color, int delay, int ticks) {
        fx.on(fx.age() + delay, () -> {
            Vfx.Sprite s = fx.sprite("shock", V.up(ground, 0.06)).color(color).fixed(Vfx.flat(Kit.rand() * 360)).size(radius * 2 / 0.95);
            int every = Math.max(1, ticks / 6);
            s.play(fx.age(), 0, 6, every, false).fade(fx.age() + ticks - 2, 3);
        });
    }

    /** 갈라진 땅 (crack — 땅에 눕힘, 천천히 옅어짐): size = 지름 */
    public static void crack(Vfx.Fx fx, Vec3 ground, double size, double[] color, int delay, int life) {
        fx.on(fx.age() + delay, () -> {
            Vfx.Sprite s = fx.sprite("crack", V.up(ground, 0.04)).color(color).fixed(Vfx.flat(Kit.rand() * 360)).frame((int) (Kit.rand() * 4)).size(size * 0.7);
            s.to(null, size, 3);
            s.fade(fx.age() + Math.max(4, life - 10), 10);
        });
    }

    /**
     * 휘두른 초승달 (slash 0 ~ 7): 날이 지나간 면 — 가운데 at, 휘두르는 쪽 swing (그림 +x 쪽으로 퍼짐), 면의 앞 normal (보는 쪽으로).
     * size = 지름. 한 면만 보이므로 보는 사람 쪽을 normal 로
     */
    public static Vfx.Sprite slash(Vfx.Fx fx, Vec3 at, Vec3 swing, Vec3 normal, double size, double[] color, int delay) {
        Vfx.Sprite[] out = new Vfx.Sprite[1];
        fx.on(fx.age() + delay, () -> {
            // 그림: +x = 휘두르는 쪽, +z (앞면) = normal → y = z × x
            Vec3 z = V.unit(normal);
            Vec3 x = V.unit(V.sub(swing, V.scale(z, V.dot(swing, z))));
            Vec3 y = V.unit(V.cross(z, x));
            Vfx.Sprite s = fx.sprite("slash", at).color(color).fixed(Vfx.basis(x, y, z)).size(size);
            s.play(fx.age(), 0, 8, 1, false);
            out[0] = s;
        });
        return out[0];
    }

    /** X 베기 자국 (xcut 0 ~ 3 — 카메라를 봄) */
    public static void xcut(Vfx.Fx fx, Vec3 at, double size, double[] color, int delay) {
        fx.on(fx.age() + delay, () -> {
            Vfx.Sprite s = fx.sprite("xcut", at).color(color).size(size).roll(Kit.rand() * 40 - 20, 0);
            s.play(fx.age(), 0, 4, 2, false);
        });
    }

    /** 불꽃 튐: 작은 별 (burst2 8 ~ 11) 여럿이 둘레로 튐 */
    public static void sparks(Vfx.Fx fx, Vec3 at, double[] color, int n, double spread, int delay) {
        fx.on(fx.age() + delay, () -> {
            for (int i = 0; i < n; i++) {
                Vec3 d = V.unit(new Vec3(Kit.rand() - 0.5, Kit.rand() * 0.8 - 0.1, Kit.rand() - 0.5));
                Vfx.Sprite s = fx.sprite("burst2", at).color(Vfx.mix(color, WHITE, 0.35)).size(0.28 + Kit.rand() * 0.2).frame(8 + (int) (Kit.rand() * 2));
                s.to(V.add(at, d, spread * (0.6 + Kit.rand() * 0.6)), 0.12, 5);
                s.fade(fx.age() + 2, 4);
            }
        });
    }

    /** 피 튐 (blood — 터지는 장) */
    public static void blood(Vfx.Fx fx, Vec3 at, double size, int n, double[] color, int delay) {
        fx.on(fx.age() + delay, () -> {
            for (int i = 0; i < n; i++) {
                Vec3 p = V.add(at, new Vec3((Kit.rand() - 0.5) * 0.6, (Kit.rand() - 0.3) * 0.6, (Kit.rand() - 0.5) * 0.6), 1);
                Vfx.Sprite s = fx.sprite("blood", p).color(color).size(size * (0.7 + Kit.rand() * 0.6)).roll(Kit.rand() * 360, 0);
                s.play(fx.age(), (i % 2) * 6, 6, 1, false);
                s.to(V.up(p, -0.25), -1, 6);
            }
        });
    }

    /** 흙먼지 (dust — 회색 덩어리가 부풀며 오름): 땅 ground 둘레 radius 안 n 개 */
    public static void dust(Vfx.Fx fx, Vec3 ground, double radius, int n, double[] color, int delay) {
        fx.on(fx.age() + delay, () -> {
            for (int i = 0; i < n; i++) {
                double a = Kit.rand() * Math.PI * 2;
                double r = radius * Math.sqrt(Kit.rand());
                Vec3 p = new Vec3(ground.x + Math.cos(a) * r, ground.y + 0.3, ground.z + Math.sin(a) * r);
                double sz = 0.9 + Kit.rand() * 0.8;
                Vfx.Sprite s = fx.sprite("dust", p).color(color).size(sz).alpha(0.85).roll(Kit.rand() * 360, 0);
                s.to(V.up(V.add(p, new Vec3(Math.cos(a), 0, Math.sin(a)), 0.5), 0.5), sz * 1.6, 12);
                s.play(fx.age(), 0, 6, 2, false);
            }
        });
    }

    /** 둥근 빛 (glow) — life 틱 동안 at 에, 끝에 옅어짐 */
    public static Vfx.Sprite glow(Vfx.Fx fx, Vec3 at, double size, double[] color, double alpha, int life) {
        Vfx.Sprite s = fx.sprite("glow", at).color(color).size(size).alpha(alpha);
        s.fade(fx.age() + Math.max(1, life - 4), 4);
        return s;
    }

    /**
     * 튀는 돌 (블록 아이템 모델 — 깎은 돌 · 심층암 …): c 에서 둘레로 포물선 (2틱마다 다음 자리), 떨어지면 작아지며 사라짐
     */
    public static void rocks(Vfx.Fx fx, Vec3 c, int n, double speed, double size, String[] blocks, int delay) {
        fx.on(fx.age() + delay, () -> {
            for (int i = 0; i < n; i++) {
                double a = (double) i / n * Math.PI * 2 + Kit.rand() * 0.6;
                double vh = speed * (0.6 + Kit.rand() * 0.5);
                double vy = speed * (0.9 + Kit.rand() * 0.5);
                Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
                String b = blocks[i % blocks.length];
                double sz = size * (0.7 + Kit.rand() * 0.6);
                Quaternionf r0 = new Quaternionf().rotateXYZ((float) (Kit.rand() * 6), (float) (Kit.rand() * 6), (float) (Kit.rand() * 6));
                Vfx.Prop p = fx.prop(b, V.up(c, 0.2)).size(sz * 0.5, 0);
                p.turn(r0, 0);
                int t0 = fx.age();
                int flight = 12;
                fx.each(t0 + 1, t0 + flight, 2, t -> {
                    double s = (t - t0) / 20.0;
                    Vec3 pos = new Vec3(c.x + dir.x * vh * s, c.y + 0.2 + vy * s - 14 * s * s, c.z + dir.z * vh * s);
                    Quaternionf rr = new Quaternionf(r0).rotateXYZ((float) (s * 9), (float) (s * 6), 0);
                    p.to(pos, rr, t - t0 >= flight - 2 ? sz * 0.2 : sz, 2);
                });
                fx.on(t0 + flight + 2, p::remove);
            }
        });
    }

    /** 땅 높이 (at 아래 · 위 4칸 안의 맨 위 막힌 칸 위 — 없으면 at.y) */
    public static Vec3 ground(ServerLevel level, Vec3 at) {
        return new Vec3(at.x, mmorpg.fabric.rune.RuneKit.groundAt(level, at, at.y), at.z);
    }
}
