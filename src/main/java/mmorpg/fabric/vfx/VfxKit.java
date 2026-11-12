package mmorpg.fabric.vfx;

import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 2026-11-11 Vfx 로 짠 흔한 2D · 입체 조각 (룬 · 직업 스킬이 같이 씀): 터짐 · 땅의 충격파 · 갈라진 땅 · 휘두른 초승달 · X 베기 · 불꽃 튐 · 피 튐 · 흙먼지 ·
 * 튀는 돌 · 빛 무리. delay = 지금부터 몇 틱 뒤 (0 = 바로) — 효과 하나에 여럿을 이어 붙여 한 장면을 짬.
 * 2026-11-12 (사용자 "오케 이런 느낌으로 룬 다 바꿔보자"): 2D 조각은 모두 픽셀 그림 (vfx.Px — 글꼴 rpg:px) 으로 — 쓰는 곳 (룬) 은 그대로
 */
public final class VfxKit {
    private VfxKit() {}

    public static final double[] WHITE = {1, 1, 1};
    public static final double[] DUST = {0.62, 0.56, 0.48};
    public static final double[] ROCK = {0.42, 0.38, 0.36};

    static double[] coreOf(double[] c) {
        return Vfx.mix(c, WHITE, 0.7);
    }

    /** 터짐 (번쩍임 + 튀는 작은 별): size = 지름 (칸) */
    public static void burst(Vfx.Fx fx, Vec3 at, double size, double[] color, int delay) {
        Px.star(fx, at, size * 0.85, color, coreOf(color), delay);
        Px.sparks(fx, at, color, 3 + (int) Math.round(size * 1.5), size * 0.55, delay);
    }

    /** 땅에 눕힌 충격파 고리 (커지며 가늘어짐): radius = 끝 반지름 */
    public static void ring(Vfx.Fx fx, Vec3 ground, double radius, double[] color, int delay, int ticks) {
        Px.shock(fx, ground, radius, color, coreOf(color), ticks, delay);
    }

    /** 갈라진 땅 (땅에 눕힘, 천천히 옅어짐): size = 지름 */
    public static void crack(Vfx.Fx fx, Vec3 ground, double size, double[] color, int delay, int life) {
        Px.cracks(fx, ground, 1, 0, size, color, 0, life, delay);
    }

    /**
     * 휘두른 초승달: 날이 지나간 면 — 가운데 at, 휘두르는 쪽 swing (그쪽으로 드러남), 면의 앞 normal (보는 쪽으로). size = 지름. 한 면만 보이므로 보는 사람
     * 쪽을 normal 로
     */
    public static void slash(Vfx.Fx fx, Vec3 at, Vec3 swing, Vec3 normal, double size, double[] color, int delay) {
        Px.slash(fx, at, swing, normal, size, color, coreOf(color), delay);
    }

    /** X 베기 자국 (카메라를 봄 — 베인 자국 둘) */
    public static void xcut(Vfx.Fx fx, Vec3 at, double size, double[] color, int delay) {
        double r = Kit.rand() * 30 - 15;
        Px.cut(fx, at, 45 + r, size, color, coreOf(color), delay);
        Px.cut(fx, at, -45 + r, size, color, coreOf(color), delay + 1);
    }

    /** 불꽃 튐: 작은 별 여럿이 둘레로 튐 */
    public static void sparks(Vfx.Fx fx, Vec3 at, double[] color, int n, double spread, int delay) {
        Px.sparks(fx, at, color, n, spread, delay);
    }

    /** 피 튐 */
    public static void blood(Vfx.Fx fx, Vec3 at, double size, int n, double[] color, int delay) {
        Px.blood(fx, at, size, n, color, delay);
    }

    /** 흙먼지 (덩어리가 부풀며 오름): 땅 ground 둘레 radius 안 n 개 */
    public static void dust(Vfx.Fx fx, Vec3 ground, double radius, int n, double[] color, int delay) {
        Px.dust(fx, ground, n, radius, 1.3, color, delay);
    }

    /** 둥근 빛 — life 틱 동안 at 에, 끝에 옅어짐 */
    public static void glow(Vfx.Fx fx, Vec3 at, double size, double[] color, double alpha, int life) {
        Px.glow(fx, at, size, color, alpha, life);
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
