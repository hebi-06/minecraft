package mmorpg.fabric.job;

import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneProps;
import mmorpg.fabric.rune.RuneSwords;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.BlockFx;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-15 궁수 (팬텀 아처) 2차 특성 (claude/archer-remake.md — 옛 스나이퍼 · 배틀 아처 대신):
 * <ul>
 * <li>아스널 킹 「황금 문」 — 활 평타를 쏠 때마다 등 뒤 금빛 문에서 보물 하나가 함께 날아감. 맞으면 칼 + 보물 = 둘이 꽂힘 (perShot · 최대 max). 여덟째가 꽂히면 burstDelay 틱
 * 뒤 모두 터짐 (활 평타 × burst · 둘레 radius 칸 — PhantomArcher 의 꽂힌 칼과 같은 셈, 색만 금빛)</li>
 * <li>블레이드 레인저 「쌍검」 — twin 칸 안의 적에게 쏜 활 평타는 투영한 쌍검 베기 (X 베인 자국 · 앞 부채꼴 cleaveRange 칸의 다른 적도 평타 × cleave). near 칸 안
 * 적에게 주는 피해 +nearBonus · far 칸 밖 −farCut (활 평타 · 직업 스킬)</li>
 * </ul>
 */
public final class JobTraits9 {
    private JobTraits9() {}

    static final String AK = "arsenalking";
    static final String BR = "bladeranger";

    static void init() {
        RpgCombat.MeleeMult rm = RpgCombat.rangedMult;
        RpgCombat.rangedMult = (p, w, v) -> rm.mult(p, w, v) * basicRange(p, w, v);
    }

    static Jobs.Job jobIf(ServerPlayer sp, String id) {
        Jobs.Job j = JobSystem.job(sp);
        return j != null && id.equals(j.id()) ? j : null;
    }

    static double trait(Jobs.Job j, String key, double fallback) {
        return j == null || j.trait() == null ? fallback : j.trait().def().numOr(key, fallback);
    }

    static void reset(ServerPlayer sp) {}

    static void forget(java.util.UUID id) {}

    static void lifeEnded(java.util.UUID id) {}

    // ------------------------------------------------------------ 블레이드 레인저 거리 배율

    /** 활 평타 배율 (RpgCombat.rangedMult): 블레이드 레인저 활 — 가까운 적 + · 먼 적 − */
    private static double basicRange(ServerPlayer sp, Weapon w, LivingEntity victim) {
        if (w == null || victim == null || !"bow".equals(w.type())) return 1;
        Jobs.Job j = jobIf(sp, BR);
        return j == null ? 1 : distMult(sp, victim, j);
    }

    /** 직업 스킬 배율 (JobKit.hit): 블레이드 레인저 — 가까운 적 + · 먼 적 − (다른 직업은 1) */
    static double rangeMult(ServerPlayer sp, LivingEntity e, Jobs.Job j) {
        if (j == null || e == null || !BR.equals(j.id())) return 1;
        return distMult(sp, e, j);
    }

    /** 거리 배율: 내 몸에서 적 몸까지 (가로 — 몸 여유를 뺌) near 칸 안 1 + nearBonus · far 칸 밖 1 − farCut · 그 사이 1 */
    static double distMult(ServerPlayer sp, LivingEntity e, Jobs.Job j) {
        double d = Math.max(0, V.hdist(sp.position(), e.position()) - Kit.bodyPad(e));
        if (d <= trait(j, "near", 4)) return 1 + trait(j, "nearBonus", 0.2);
        if (d > trait(j, "far", 10)) return 1 - trait(j, "farCut", 0.3);
        return 1;
    }

    // ------------------------------------------------------------ 활 평타 (JobTraitsMore 훅)

    /** 쏨: 아스널 킹 = 금빛 칼 + 등 뒤 문의 보물 · 블레이드 레인저 = 가까우면 쌍검 베기 (아니면 단조 불빛 칼) */
    static void onFired(ServerPlayer sp, Jobs.Job j, Weapon w, LivingEntity target, Vec3 from, Vec3 dir, double dist) {
        if (AK.equals(j.id())) {
            PhantomArcher.onFired(sp, j, w, target, from, dir, dist);
            if (target != null && RuneKit.fxQuota("kingShotGate", 4)) gateShot(sp, j, target);
            return;
        }
        if (BR.equals(j.id())) {
            if (target != null && Math.max(0, V.hdist(sp.position(), target.position()) - Kit.bodyPad(target)) <= trait(j, "twin", 4)) twinCut(sp, j, w, target);
            else PhantomArcher.onFired(sp, j, w, target, from, dir, dist);
        }
    }

    /** 활 평타가 맞음: 아스널 킹 = 칼 + 보물이 꽂힘 (블레이드 레인저는 꽂지 않음) */
    static void onHit(ServerPlayer sp, Jobs.Job j, LivingEntity victim) {
        if (AK.equals(j.id())) PhantomArcher.onHit(sp, j, victim);
    }

    /** 게이지: 아스널 킹 = 마지막으로 맞힌 적에게 꽂힌 보물 수 (블레이드 레인저는 없음) */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        return j != null && AK.equals(j.id()) ? PhantomArcher.gauge(sp) : 0;
    }

    /** 아스널 킹 평타: 어깨 뒤 (좌우 번갈아) 에 작은 금빛 문이 열리고 보물 하나가 칼끝부터 나와 과녁으로 날아감 (그림 — 꽂힘은 onHit) */
    private static void gateShot(ServerPlayer sp, Jobs.Job j, LivingEntity target) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 side = V.rightOf(f);
        double s = Kit.rand() < 0.5 ? -1 : 1;
        Vec3 g = V.add(V.up(V.add(sp.position(), f, -0.9), 2.1 + Kit.rand() * 0.5), side, s * (1.0 + Kit.rand() * 0.5));
        Vec3 to = Kit.bodyPoint(target);
        Vec3 d = V.unit(V.sub(to, g));
        BlockFx.gate(level, g, d, 0.55, 12, JobKit.glow(j), 2, 6);
        PhantomArcher.Pal p = PhantomArcher.pal(j);
        double len = 1.5;
        Vfx.Fx fx = RuneProps.fx(level, g, 24);
        Vec3 face = PhantomArcher.faceOf(d);
        RuneSwords.Blade b = RuneSwords.phantom(fx, PhantomArcher.treasure((int) (Kit.rand() * 3)), V.add(g, d, -len * 0.6), d, face, len, 0.05, p.blade(), p.gem());
        fx.on(1, () -> {
            b.grow(1, 2);
            b.pose(V.add(g, d, -len * 0.2), d, face, 2);
        });
        fx.on(4, () -> {
            Vec3 at = target.isRemoved() ? to : Kit.bodyPoint(target);
            Vec3 dd = V.unit(V.sub(at, b.hilt()));
            int t = Math.max(1, ArrowFx.flyTicks(b.hilt(), at, 70));
            b.pose(V.add(at, dd, -b.reach() * 0.8), dd, PhantomArcher.faceOf(dd), t);
            PhantomArcher.trail2d(level, V.add(b.hilt(), dd, 0.5), at, t / 20.0, 0.3, JobKit.glow(j), BlockFx.hot(JobKit.glow(j))); // 2026-11-15 2D 잔상
            fx.on(4 + t, b::remove);
        });
        SkillFx.play(level, "block.amethyst_block.resonate", g, 1.9, 0.35);
    }

    /**
     * 블레이드 레인저 가까운 평타: 투영한 쌍검 (단조 불빛) 이 과녁 앞에서 X 로 엇갈려 벰 — 호 둘 · 번쩍 별, 앞 부채꼴 cleaveRange 칸의 다른 적도 평타 × cleave (한 발에 한
     * 번 · 터지는 피해는 다시 나누지 않음)
     */
    private static void twinCut(ServerPlayer sp, Jobs.Job j, Weapon w, LivingEntity target) {
        ServerLevel level = sp.level();
        Vec3 c = Kit.bodyPoint(target);
        Vec3 face = V.sub(c, sp.getEyePosition());
        double[] glow = JobKit.glow(j);
        PhantomArcher.cross2d(level, V.add(c, V.unit(face), -0.3), face, 1.3, glow, JobKit.core(j));
        if (RuneKit.fxQuota("twinCutBlades", 6)) twinBlades(level, sp, c, j, 1.25, 5);
        SkillFx.play(level, "entity.player.attack.sweep", c, 1.3 + Kit.rand() * 0.2, 0.7);
        if (RpgCombat.bonusHitting()) return;
        double ratio = trait(j, "cleave", 0.5);
        double range = trait(j, "cleaveRange", 2.5);
        Vec3 f = V.flat(V.unit(face));
        int n = 0;
        for (LivingEntity x : Kit.around(sp, target.position(), range, 2.0)) {
            if (x == target || n >= 4) continue;
            Vec3 to = V.sub(x.position(), sp.position());
            if (V.len(V.flat(to)) > 0.1 && V.dot(V.unit(V.flat(to)), f) < 0.3) continue; // 앞 부채꼴 (≈ 145°)
            RpgCombat.bonusHit(sp, x, w, ratio);
            n++;
        }
    }

    /** 쌍검 그림: 과녁 c 앞에서 칼 둘이 양쪽 위에서 엇갈려 내려 벰 (len 칸 · life 틱) */
    static void twinBlades(ServerLevel level, ServerPlayer sp, Vec3 c, Jobs.Job j, double len, int life) {
        Vec3 f0 = V.flat(V.sub(c, sp.position()));
        Vec3 f = V.len(f0) < 1e-3 ? Kit.facingOf(sp) : V.unit(f0);
        Vec3 right = V.rightOf(f);
        PhantomArcher.Pal p = PhantomArcher.pal(j);
        Vec3 at = V.add(c, f, -0.9);
        Vfx.Fx fx = RuneProps.fx(level, at, life + 6);
        for (int s = 0; s < 2; s++) {
            double side = s == 0 ? -1 : 1;
            Vec3 d0 = V.unit(V.add(V.add(V.UP, right, -side * 0.9), f, 0.4));
            Vec3 d1 = V.unit(V.add(V.add(V.scale(V.UP, -0.6), right, side * 0.9), f, 0.5));
            Vec3 h = V.add(at, right, side * 0.45);
            RuneSwords.Blade b = RuneSwords.phantom(fx, p.sword(), h, d0, V.scale(f, -1), len, 0.4, p.blade(), p.gem());
            fx.on(1, () -> b.grow(1, 1).pose(h, d1, V.scale(f, -1), 2));
            fx.on(life, () -> b.grow(0.1, 4));
            fx.on(life + 5, b::remove);
        }
    }

    // ------------------------------------------------------------ 틱 · 태세 (없음)

    static void tick(ServerPlayer sp, Jobs.Job j, long now) {}

    static boolean stanceFx(ServerPlayer sp, Jobs.Job j, String stance) {
        return false;
    }
}
