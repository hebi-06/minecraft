package mmorpg.fabric.job;

import mmorpg.fabric.rune.RuneProps;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-16 위르드식 섞기 시범 (사용자 "3번은 위르드 식이 아닌데 위르드는 마크 기존 이펙트도 사용하고 도트 2d도 쓰고 3d 블록도 쓰는거임 3d 블록만 쓰는게 아니라"):
 * 위르드 온라인 화면 (사용자 스크린샷 5장) 의 세 겹을 스타 볼텍스에 겹침 — 판정 · 피해 · 끌어당김 · 처형은 그대로 (ArcmageSkills.vortex).
 * <ul>
 *   <li>도트 2D: 칠한 연출 그대로 (소용돌이 · 고리 · 별빛 · 섬광 — 그림판은 tools/jobvfx/pixel.py 의 도트)</li>
 *   <li>바닐라 파티클: 크게 키운 색 먼지 덩이 (파랑 · 남보라 — 위르드의 파란 + 덩이) · 회색 연기 뭉치 (poof · cloud) · 흰 반짝임 (end_rod · firework) ·
 *       빨려 드는 보랏빛 (portal · enchant) · 맞음 (crit · enchanted_hit) · 무너짐 (sonic_boom · explosion · 마법 맞음 반짝임)</li>
 *   <li>떠도는 별 · 결정 조각: 도트 2D 그림 (금빛 별 다섯 · 구르는 결정 조각 여섯 — 돌며 안쪽으로 감김, 무너질 때 별은 사라지고 조각은 밖으로 튕김).
 *       2026-11-16 직접 만든 3D 소품 (wy_star · wy_shard · wy_cube) 은 뺌 — 사용자 "저거 3d 별도 빼라 어줍잔게 넣으니깐 진짜 없어보인다 차라리 그림으로 넣어"</li>
 * </ul>
 * 글꼴 그림을 더 쓰지 않음 (도트 별 · 조각은 비전 묶음에 있던 그림판, 바닐라 파티클은 바닐라 그림). 화면 흔들림 없음.
 */
final class WyrdMix {
    private WyrdMix() {}

    /** 시범 켬 (끄면 도트 2D 만) */
    static boolean VORTEX = true;

    static final int BLUE = 0x3F57D8, VIOLET = 0x6A4CFF, LIGHT = 0x9DB4FF, PALE = 0xD6E0FF;
    /** 도트 2D 그림판 (비전 묶음): 별 (칸 2 ~ 5 = 금빛 별 반짝임) · 구르는 결정 조각 (여덟 칸) */
    static final String STAR = "el_arcane_starnode";
    static final String SHARD = "el_arcane_shard";

    static void p(ServerLevel l, ParticleOptions o, Vec3 at, int count, double dx, double dy, double dz, double speed) {
        l.sendParticles(o, true, true, at.x, at.y, at.z, count, dx, dy, dz, speed);
    }

    /** 한 알을 속도 vel 로 (count 0 — portal · enchant 는 at + vel 에서 at 으로 날아 듦) */
    static void pv(ServerLevel l, ParticleOptions o, Vec3 at, Vec3 vel) {
        l.sendParticles(o, true, true, at.x, at.y, at.z, 0, vel.x, vel.y, vel.z, 1.0);
    }

    static DustParticleOptions dust(int rgb, double scale) {
        return new DustParticleOptions(rgb, (float) Math.max(0.1, Math.min(4.0, scale)));
    }

    static Vec3 ring(Vec3 c, double r, double ang, double h) {
        return new Vec3(c.x + Math.cos(ang) * r, c.y + h, c.z + Math.sin(ang) * r);
    }

    static int pick(int i) {
        return switch (Math.floorMod(i, 3)) {
            case 0 -> BLUE;
            case 1 -> VIOLET;
            default -> LIGHT;
        };
    }

    // ------------------------------------------------------------ 쓸 때

    /** 완드 끝 → 가운데: 흰 반짝임 줄 + 파란 먼지 · 가운데 불꽃놀이 반짝임 */
    static void cast(ServerLevel l, Vec3 tip, Vec3 c) {
        Vec3 to = V.up(c, 0.5);
        Vec3 d = V.sub(to, tip);
        int n = Math.max(2, (int) Math.ceil(V.len(d) / 0.4));
        for (int i = 0; i <= n; i++) {
            Vec3 q = V.add(tip, d, (double) i / n);
            p(l, ParticleTypes.END_ROD, q, 1, 0.03, 0.03, 0.03, 0.01);
            if (i % 2 == 0) p(l, dust(i % 4 == 0 ? LIGHT : BLUE, 1.4), q, 1, 0.05, 0.05, 0.05, 0);
        }
        p(l, ParticleTypes.FIREWORK, to, 14, 0.2, 0.2, 0.2, 0.18);
        p(l, ParticleTypes.END_ROD, to, 8, 0.3, 0.3, 0.3, 0.08);
    }

    // ------------------------------------------------------------ 소용돌이 (formAt 부터 life 틱)

    /**
     * 소용돌이 몸: 판정 둘레에 큰 먼지 덩이 고리 · 회색 연기 → 도트 금빛 별 다섯 (가슴 높이, 소용돌이 쪽으로 돌며 안쪽으로 — 반짝임) · 도트 결정 조각 여섯
     * (낮게, 거꾸로 돌며 구름) · 2틱마다 팔을 따라 먼지 덩이 · 빨려 드는 portal · enchant · 솟는 reverse_portal · 반짝임. 끝 squeeze 틱 동안 별 · 조각이
     * 가운데로 조여 듦 → life 틱 (무너짐) 에 별은 작아지며 사라지고 조각은 밖으로 튕겨 나가며 옅어짐
     */
    static void body(ServerLevel l, Vec3 c, double R, int life, int squeeze, double spin0, double rate) {
        double s0 = Math.toRadians(spin0);
        for (int i = 0; i < 16; i++) {
            double a = s0 + i * Math.PI * 2 / 16;
            Vec3 q = ring(c, R, a, 0.2);
            p(l, dust(i % 2 == 0 ? BLUE : VIOLET, 3.0), q, 1, 0.05, 0.02, 0.05, 0);
            if (i % 2 == 0) pv(l, ParticleTypes.POOF, q, new Vec3(Math.cos(a) * 0.08, 0.02, Math.sin(a) * 0.08));
        }
        Vfx.Fx fx = RuneProps.fx(l, c, life + 16);
        int ns = 5;
        int nk = 6;
        boolean has = Vfx.hasSheet(STAR) && Vfx.hasSheet(SHARD);
        Vfx.Sprite[] stars = new Vfx.Sprite[has ? ns : 0];
        Vfx.Sprite[] shards = new Vfx.Sprite[has ? nk : 0];
        for (int i = 0; i < stars.length; i++) {
            stars[i] = fx.sprite(STAR, starAt(c, R, i, ns, 0, life, squeeze, s0, rate)).size(0.05);
            stars[i].play(0, 2, 4, 3, true);
        }
        for (int i = 0; i < shards.length; i++) {
            shards[i] = fx.sprite(SHARD, shardAt(c, R, i, nk, 0, life, squeeze, s0, rate)).size(0.05);
            shards[i].play(0, (i * 3) % 8, 8, 2, true);
        }
        fx.on(1, () -> {
            for (Vfx.Sprite st : stars) st.to(null, 0.95, 4);
            for (Vfx.Sprite sh : shards) sh.to(null, 0.6, 4);
        });
        fx.each(2, life - 1, 2, age -> {
            for (int i = 0; i < stars.length; i++) stars[i].to(starAt(c, R, i, ns, age + 2, life, squeeze, s0, rate), -1, 2);
            for (int i = 0; i < shards.length; i++) shards[i].to(shardAt(c, R, i, nk, age + 2, life, squeeze, s0, rate), -1, 2);
            // 팔을 따라 먼지 덩이 (팔 둘 — 소용돌이와 같은 쪽으로 감김)
            double spin = s0 + Math.toRadians(rate) * age / 20.0;
            for (int arm = 0; arm < 2; arm++) {
                double f = (age / 2) % 2 == 0 ? 0.85 : 0.55;
                double a = spin + arm * Math.PI + (1 - f) * Math.toRadians(140) + (Kit.rand() - 0.5) * 0.3;
                p(l, dust(pick(age / 2 + arm), 1.6 + Kit.rand() * 0.9), ring(c, R * f, a, 0.25), 1, 0.08, 0.03, 0.08, 0);
            }
            // 빨려 드는 보랏빛 (portal — 바깥 → 가운데) · 글자 (enchant — 가슴 높이에서 가운데로)
            for (int i = 0; i < 3; i++) {
                double a = Kit.rand() * Math.PI * 2;
                double r = R * (0.75 + Kit.rand() * 0.3);
                pv(l, ParticleTypes.PORTAL, V.up(c, 0.2), new Vec3(Math.cos(a) * r, -0.9 + Kit.rand() * 0.3, Math.sin(a) * r));
            }
            for (int i = 0; i < 2; i++) {
                double a = Kit.rand() * Math.PI * 2;
                double r = R * (0.5 + Kit.rand() * 0.4);
                pv(l, ParticleTypes.ENCHANT, V.up(c, 0.9), new Vec3(Math.cos(a) * r, 0.4 + Kit.rand() * 1.0, Math.sin(a) * r));
            }
            double a = Kit.rand() * Math.PI * 2;
            pv(l, ParticleTypes.REVERSE_PORTAL, ring(c, R * Math.sqrt(Kit.rand()) * 0.8, a, 0.2), new Vec3(0, 0.06 + Kit.rand() * 0.05, 0));
            if ((age / 2) % 2 == 0) {
                double b = Kit.rand() * Math.PI * 2;
                p(l, ParticleTypes.END_ROD, ring(c, R * (0.4 + Kit.rand() * 0.5), b, 0.4 + Kit.rand() * 1.3), 1, 0, 0, 0, 0.02);
            }
        });
        // 무너짐: 별은 가운데에서 작아지며 사라짐 · 조각은 밖으로 튕겨 나가며 옅어짐
        fx.on(life, () -> {
            for (Vfx.Sprite st : stars) st.to(null, 0.1, 3);
            for (int i = 0; i < shards.length; i++) {
                double a = s0 + i * Math.PI * 2 / nk + 0.4;
                shards[i].to(ring(c, R * 1.15, a, 0.5 + (i % 2) * 0.6), 0.75, 8);
                shards[i].fade(life + 4, 8);
            }
        });
        fx.on(life + 3, () -> {
            for (Vfx.Sprite st : stars) st.remove();
        });
        fx.on(life + 14, fx::end);
    }

    /** 별 자리 (가슴 높이 · 소용돌이 쪽으로 돌며 안쪽으로 — 끝 squeeze 틱은 가운데로 조여 듦) */
    static Vec3 starAt(Vec3 c, double R, int i, int n, int age, int life, int squeeze, double s0, double rate) {
        double t = Math.min(1, age / (double) Math.max(1, life - squeeze));
        double sq = age > life - squeeze ? Math.min(1, (age - (life - squeeze)) / (double) Math.max(1, squeeze)) : 0;
        double r = R * (0.78 - 0.3 * t) * (1 - 0.85 * sq);
        double a = s0 + i * Math.PI * 2 / n + Math.toRadians(rate * 0.8) * age / 20.0 * (1 + 2 * sq);
        double h = 1.6 + 0.25 * Math.sin(age * 0.25 + i * 1.3) - 0.5 * sq;
        return ring(c, r, a, h);
    }

    /** 결정 조각 자리 (낮게 · 거꾸로 돎) */
    static Vec3 shardAt(Vec3 c, double R, int i, int n, int age, int life, int squeeze, double s0, double rate) {
        double t = Math.min(1, age / (double) Math.max(1, life - squeeze));
        double sq = age > life - squeeze ? Math.min(1, (age - (life - squeeze)) / (double) Math.max(1, squeeze)) : 0;
        double r = R * (0.58 - 0.22 * t) * (1 - 0.8 * sq);
        double a = s0 + i * Math.PI * 2 / n - Math.toRadians(rate * 1.1) * age / 20.0;
        double h = 0.7 + 0.18 * Math.sin(age * 0.3 + i * 2.1) + 0.3 * sq;
        return ring(c, r, a, h);
    }


    // ------------------------------------------------------------ 별빛 (맥박 · 맞음)

    /** 맥박: 가운데에서 땅을 따라 밖으로 달려 나가는 연기 뭉치 (poof · cloud 번갈아) */
    static void beat(ServerLevel l, Vec3 c, double R) {
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12 + Kit.rand() * 0.2;
            Vec3 q = ring(c, R * 0.3, a, 0.15);
            pv(l, i % 2 == 0 ? ParticleTypes.POOF : ParticleTypes.CLOUD, q, new Vec3(Math.cos(a) * 0.32, 0.02, Math.sin(a) * 0.32));
        }
    }

    // 맞은 적의 바닐라 겹은 2026-11-16 WyrdKit.hit (JobFx.hit 이 모든 칠한 직업에 겹침 — 비전 묶음 색) 으로 옮김

    // ------------------------------------------------------------ 무너짐 · 처형

    /**
     * 무너짐: 소닉 붐 고리 · 폭발 연기 · 불꽃놀이 반짝임 · 땅을 따라 퍼지는 연기 뭉치 · 큰 먼지 덩이 · 마법 맞음 반짝임 (2026-11-16 자수정 블록 부스러기는 뺌 — 블록 그림
     * 파티클 = 바닐라 그림, 사용자 "바닐라 아이템은 다 빼버려")
     */
    static void collapse(ServerLevel l, Vec3 c, double R) {
        Vec3 m = V.up(c, 1.0);
        p(l, ParticleTypes.SONIC_BOOM, m, 1, 0, 0, 0, 0);
        p(l, ParticleTypes.EXPLOSION, m, 4, R * 0.22, 0.35, R * 0.22, 0);
        p(l, ParticleTypes.FIREWORK, m, 40, 0.3, 0.3, 0.3, 0.45);
        p(l, ParticleTypes.END_ROD, m, 24, 0.4, 0.4, 0.4, 0.3);
        for (int i = 0; i < 20; i++) {
            double a = i * Math.PI * 2 / 20;
            pv(l, i % 2 == 0 ? ParticleTypes.POOF : ParticleTypes.CLOUD, ring(c, 0.6, a, 0.2), new Vec3(Math.cos(a) * 0.5, 0.04, Math.sin(a) * 0.5));
        }
        for (int i = 0; i < 14; i++) {
            double a = Kit.rand() * Math.PI * 2;
            p(l, dust(i % 3 == 0 ? PALE : pick(i), 2.5 + Kit.rand()), ring(c, R * (0.3 + 0.5 * Kit.rand()), a, 0.3 + Kit.rand() * 1.2), 1, 0, 0, 0, 0);
        }
        p(l, ParticleTypes.ENCHANTED_HIT, m, 40, 0.6, 0.4, 0.6, 0.9);
    }

    /** 처형 (빨려 들어감): 적 → 가운데 보랏빛 줄기 (portal) · 적 둘레 솟는 reverse_portal */
    static void drain(ServerLevel l, LivingEntity e, Vec3 m) {
        Vec3 off = V.sub(Kit.bodyPoint(e), m);
        for (int i = 0; i < 10; i++) {
            double k = 0.8 + Kit.rand() * 0.25;
            pv(l, ParticleTypes.PORTAL, m, new Vec3(off.x * k + (Kit.rand() - 0.5) * 0.3, off.y * k - 0.6, off.z * k + (Kit.rand() - 0.5) * 0.3));
        }
        p(l, ParticleTypes.REVERSE_PORTAL, Kit.bodyPoint(e), 12, 0.3, 0.5, 0.3, 0.05);
    }
}
