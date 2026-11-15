package mmorpg.fabric.job;

import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneSwords;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 2026-11-15 시범 — 팬텀 버스트 (사용자 "너가 추천한거 다 넣어서 시범으로 만들어봐 나는 압도적인 퀄을 중요하게 여김"): 미리 렌더링한 칠한 2D 프레임 (tools/vfx_pilot_art.py —
 * 글자 표시는 명암 계산이 없어 칠한 색 그대로 · 큰 그림은 2 × 2 조각 512 픽셀) · 날아가는 불씨 · 칼 조각 · 발광 외곽선 (리소스 팩 후처리
 * post_effect/entity_outline 이 부드러운 빛 번짐으로 바꿈 — 2판: 자체 가우스 흐림 shaders/post/vfx_blur.fsh). 1판의 칼 둘레 2D 빛 무리 (Aura) 는 밝은 3D 칼에
 * 가려 안 보여 뺐음.
 */
final class PhantomFx {
    private PhantomFx() {}

    /** 칼 · 폭발의 발광 빛 (외곽선 → 빛 번짐 후처리) 색 */
    static final int GLOW_CRIMSON = 0xFF3A3A;
    static final int GLOW_CYAN = 0x7FD8FF;

    /** 땅에 눕힌 그림 (그림 앞면이 위 — 그림 위쪽이 yaw 쪽) */
    static Quaternionf flat(double yawDeg) {
        return Vfx.flat(yawDeg);
    }

    /**
     * 큰 그림 하나 (조각 k × k — 그림판 base_rc): 가운데 at · 한 변 size 칸 · facing (null 이면 카메라를 봄 · 아니면 그 방향에 고정) · 화면 돌림 roll 도 · from 틱에
     * 나타나 every 틱마다 칸을 넘기고 마지막 칸 뒤 사라짐
     */
    static Vfx.Fx tiled(ServerLevel level, String base, int k, Vec3 at, double size, Quaternionf facing, double roll, int from, int every) {
        Vfx.Sheet s0 = Vfx.sheet(base + "_00");
        int frames = s0.frames();
        int life = from + frames * every + 4;
        Vfx.Fx fx = Vfx.at(level, at, life);
        double t = size / k;
        fx.on(Math.max(0, from), () -> {
            for (int r = 0; r < k; r++) {
                for (int c = 0; c < k; c++) {
                    Vfx.Sprite sp = fx.sprite(base + "_" + r + c, at);
                    if (facing != null) sp.fixed(facing);
                    if (roll != 0) sp.roll(roll, 0);
                    sp.size(t);
                    sp.offset((c - (k - 1) / 2.0) * t, ((k - 1) / 2.0 - r) * t);
                    sp.play(fx.age(), 0, frames, every, false);
                }
            }
        });
        return fx;
    }

    /** 한 장 그림 (그림판 name — 칸이 여럿이면 every 틱마다 넘김): 가운데 at · 크기 size 칸 · from 틱에 나타남 · loop 면 life 틱 동안 되풀이 */
    static Vfx.Sprite single(Vfx.Fx fx, String name, Vec3 at, double size, double roll, int every, boolean loop) {
        Vfx.Sheet s = Vfx.sheet(name);
        Vfx.Sprite sp = fx.sprite(name, at);
        sp.size(size);
        if (roll != 0) sp.roll(roll, 0);
        if (s.frames() > 1) sp.play(fx.age(), 0, s.frames(), every, loop);
        return sp;
    }

    /** 섬광 (pb_flash — 푸른 흰 빛살 · 진홍 빛살 · 퍼지는 고리): 가운데 at · 크기 size 칸 */
    static void flash(ServerLevel level, Vec3 at, double size) {
        Vfx.Fx fx = Vfx.at(level, at, 12);
        single(fx, "pb_flash", at, size, Kit.rand() * 40 - 20, 1, false);
    }

    /**
     * 불씨 n 개 (pb_ember — 2판: 둥근 흰 금빛 심 · 주황 · 진홍 겉빛, 네 칸 깜박임. 1판 길쭉한 불티는 카메라를 보는 그림이라 늘 세로로 서서 촛불처럼 보였음):
     * 가운데 c 에서 사방 (위로 쏠림) 으로 dist 칸 솟았다가 떨어지며 작아지고 옅어짐 (life 틱)
     */
    static void embers(ServerLevel level, Vec3 c, int n, double dist, double size, int life) {
        fly(level, c, n, dist, size, life, "pb_ember", 1.0);
    }

    /**
     * 칼 조각 n 개 (pb_shard — 2판: 납작한 강철 조각이 구르는 여덟 칸 · 깨진 가장자리가 진홍으로 달아오름. 1판 3D 상자 조각은 1인칭에서 큰 회색 막대로 보였음):
     * 불씨보다 낮고 멀리 · 빨리 떨어짐
     */
    static void shards(ServerLevel level, Vec3 c, int n, double dist, double size, int life) {
        fly(level, c, n, dist, size, life, "pb_shard", 0.55);
    }

    /** 날아가는 작은 그림 (불씨 · 조각): 솟음 → 둥글게 넘어감 → 떨어짐 (세 토막 보간) · 칸은 되풀이 (불씨는 깜박임 · 조각은 구름) */
    private static void fly(ServerLevel level, Vec3 c, int n, double dist, double size, int life, String sheet, double lift) {
        int L = Math.max(8, life);
        Vfx.Fx fx = Vfx.at(level, c, L + 4);
        Vfx.Sheet sh = Vfx.sheet(sheet);
        for (int i = 0; i < n; i++) {
            double a = Kit.rand() * Math.PI * 2;
            double up = (0.2 + Kit.rand() * 0.9) * lift;
            Vec3 d = V.unit(new Vec3(Math.cos(a), up, Math.sin(a)));
            double dd = dist * (0.5 + Kit.rand() * 0.65);
            double sz = size * (0.6 + Kit.rand() * 0.7);
            Vec3 p1 = V.add(c, d, dd * 0.55);
            Vec3 p2 = V.up(V.add(c, V.flat(d), dd * 0.9), dd * 0.3 * up - 0.15);
            Vec3 p3 = V.up(V.add(c, V.flat(d), dd * 1.12), -dd * 0.4 - 0.4);
            Vfx.Sprite s = fx.sprite(sheet, c);
            s.size(sz * 0.4);
            if (sh.frames() > 1) s.play(0, (int) (Kit.rand() * sh.frames()), sh.frames(), 1 + (int) (Kit.rand() * 2), true);
            int t1 = 2 + (int) (Kit.rand() * 2);
            int t2 = Math.max(3, L / 3);
            fx.on(1, () -> s.to(p1, sz, t1));
            fx.on(1 + t1, () -> s.to(p2, sz * 0.8, t2));
            fx.on(1 + t1 + t2, () -> s.to(p3, sz * 0.35, Math.max(2, L - t1 - t2 - 1)));
            s.fade(L - 6, 6);
        }
    }

    /** 땅 자리 (발밑 블록 위 0.04칸) */
    static Vec3 ground(ServerLevel level, Vec3 p) {
        return new Vec3(p.x, RuneKit.groundAt(level, p, p.y) + 0.04, p.z);
    }
}
