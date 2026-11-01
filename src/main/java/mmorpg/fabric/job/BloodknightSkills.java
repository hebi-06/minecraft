package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 2차 직업 — 검사 반전 다인슬레이프(블러드 나이트) (jobs.json advance.bloodknight — 검붉은 칼빛, 특성 「피의 갈증」 JobTraits5): 핏빛 연격 · 피의 질주 ·
 * 상처 터뜨리기 · 회그니의 맹세 · 끝나지 않는 전투 (사용자 "반전은 새 스킬" — 검사와 같은 칼 · 같은 칸 역할 (연계기 · 이동 · 광역 · 태세 · 궁극기) 의 새 스킬
 * 다섯). 몸 쓰는 직업 기준 (제 몸 · 제 칼 동작만). 피해 = 위력 P × 배율 (JobKit — 맞힐 때마다 흡혈 · 상처 +1 은 JobTraits5.afterJobHit, 스킬마다 체력을 바쳐
 * 위력 +10% 는 JobSystem → JobTraits5.castMult). 그림은 검붉은 초승달 · 맞은 자리 피 튀김 (JobFx.hit → JobTraits5.blood) · 핏방울 (rfx_jdrop).
 */
final class BloodknightSkills {
    private BloodknightSkills() {}

    /** 잔상의 그림자 몸 (검붉은 빛) */
    private static final double[] SHADE = {0.34, 0.02, 0.05};
    private static final double[] WHITE = RuneKit.WHITE;
    /** 흙먼지 · 튀는 흙 */
    private static final double[] DUST = {0.62, 0.56, 0.48};
    /** 피 기둥이 앞으로 솟는 빠르기 (칸/초) */
    private static final double SPLIT_SPEED = 30;

    static void register() {
        JobSystem.register("bloodknight_flurry", BloodknightSkills::flurry);
        JobSystem.register("bloodknight_rush", BloodknightSkills::rush);
        JobSystem.register("bloodknight_burst", BloodknightSkills::burst);
        JobSystem.register("bloodknight_oath", BloodknightSkills::oath);
        JobSystem.register("bloodknight_ult", BloodknightSkills::ult);
    }

    /** 상처 최대 (특성 max) */
    private static int maxWounds(Jobs.Job j) {
        return j.trait() == null ? 5 : Math.max(1, j.trait().max());
    }

    /** 핏방울 n 개가 at 에서 dir 쪽으로 튐 (spread — 흩어짐 0 ~ 1, speed 칸/초) */
    static void spray(ServerLevel level, Vec3 at, Vec3 dir, int n, double spread, double speed, Jobs.Job j, double life, Opt view) {
        for (int i = 0; i < n; i++) {
            Vec3 d = V.unit(new Vec3(dir.x + (Kit.rand() - 0.5) * 2 * spread, dir.y + (Kit.rand() - 0.5) * 2 * spread, dir.z + (Kit.rand() - 0.5) * 2 * spread));
            double s = speed * (0.6 + Kit.rand() * 0.6);
            JobTraits5.drop(level, at, d, 0.1 + Kit.rand() * 0.06, i % 3 == 0 ? JobFx.inner(j) : JobFx.outer(j), life, view.with("speed", s, "fade", 0.35));
        }
    }

    // ------------------------------------------------------------ 핏빛 연격

    /** 핏빛 연격 세 베기의 눈앞 베기 기울기 (찌르듯 비스듬히 · 되돌려 · 머리 위에서 내려) */
    private static final double[] FLURRY_TILT = {-28, 160, 95};

    /**
     * 핏빛 연격: gap 틱마다 세 베기 (hits — 1.2 · 1.2 · 2.6, 앞 reach 칸 · arc 도) — 크게 내딛으며 찌르듯 비스듬히 벰 → 되돌려 벰 → 머리 위에서 내려 벰 (앞 땅에 핏빛
     * 터짐). 맞힐 때마다 상처 +1 · 흡혈 (JobTraits5). 칼끝에서 핏방울이 베는 쪽으로 흩뿌려짐
     */
    static boolean flurry(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        List<Double> hits = SwordsmanSkills.numbers(k, "hits", List.of(1.2, 1.2, 2.6));
        int gap = (int) k.num("gap", 10);
        double reach = k.num("reach", 3.6);
        double arc = k.num("arc", 130);
        double step = k.num("step", 0.8);
        String life = RuneKit.lifeToken(sp);
        int n = hits.size();
        SkillFx.every(n, gap, i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 eye = sp.getEyePosition();
            Vec3 f = V.flat(sp.getViewVector(1F));
            boolean last = i == n - 1;
            double mult = hits.get(i);
            // 내딛음 (첫 베기는 크게 — 찌르듯 · 땅 위에서만)
            double push = i == 0 ? 0.6 : 0.4;
            if (step > 0 && sp.onGround() && !Status.isRooted(sp)) Kit.launch(sp, f.x * step * push, sp.getDeltaMovement().y, f.z * step * push);
            double tilt = FLURRY_TILT[Math.min(i, FLURRY_TILT.length - 1)];
            JobFx.slash(sp, j, 2.2, last ? 0.0 : 0.35, last ? 2.3 : 1.75, tilt, last ? 0.3 : 0.26);
            // 칼끝에서 흩뿌려지는 핏방울 (베는 쪽으로 — 1인칭에서는 숨김)
            RuneKit.Hand h = RuneKit.handOf(sp);
            Vec3 tip = V.up(V.add(h.hand(), h.f(), 0.7), 0.1);
            Vec3 side = V.rightOf(f);
            Vec3 toward = last ? V.add(f, V.UP, -0.6) : V.add(f, side, i == 0 ? 0.8 : -0.8);
            spray(level, tip, toward, last ? 5 : 3, 0.35, 3.0, j, 0.35, JobFx.hide1p(sp));
            SkillFx.play(level, "item.spear.attack_miss", eye, 1.0 + i * 0.1, 0.9);
            if (last) {
                // 내려 벰: 앞 땅에 핏빛 터짐 · 흙먼지
                Vec3 g = V.add(sp.position(), f, 1.8);
                Vec3 gg = new Vec3(g.x, RuneKit.groundAt(level, g, sp.getY()), g.z);
                JobFx.smash(level, V.up(gg, 0.05), 1.6, j, 0.4, Opt.of("alpha", 0.85));
                JobTraits5.blood(level, V.up(gg, 0.4), 0.9, j, 0.4, Opt.empty());
                SkillFx.sparks(level, V.up(gg, 0.2), DUST, 6, 3.0);
                SkillFx.play(level, "entity.player.attack.sweep", eye, 0.9, 0.9);
                SkillFx.play(level, "entity.magma_cube.squish", gg, 0.8, 0.6);
            }
            int[] cnt = {0};
            for (LivingEntity e : RuneKit.coneTargets(sp, reach, arc, 10)) {
                JobKit.strike(sp, e, j, P, mult, false, false, life, 0, d -> {
                    if (cnt[0]++ < 8) JobFx.hit(e, j, last ? 1.3 : 1.05);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 피의 질주

    /**
     * 피의 질주: 보는 쪽 dist 칸 (벽 · 낭떠러지 앞까지) 을 t 틱에 내달림 (한 틱에 한 마디 — 지나온 자리에 핏빛 잔상 · 떨어지는 핏방울), 길 가까이 (폭 width) 의 적을
     * 지날 때 벰 (mult) · 상처 wound 개 더. 쓰면 leechFor 틱 동안 흡혈 두 배 (이 질주의 피해도). 묶였거나 앞이 막혔으면 쓰지 않음
     */
    static boolean rush(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double free = Kit.reachFlat(level, start, f, k.num("dist", 6));
        List<Kit.Spot> spots = free > 0.4 ? Kit.dashSpots(level, start, f, free) : List.of();
        if (spots.isEmpty()) {
            Notify.notify(sp, "§c" + k.name() + " §7— 앞이 막혔습니다", 30);
            return false;
        }
        double len = spots.get(spots.size() - 1).s();
        int steps = Math.max(1, (int) k.num("t", 3));
        List<Vec3> marks = new ArrayList<>();
        for (int s = 1; s <= steps; s++) {
            double want = len * s / steps;
            Kit.Spot best = spots.get(0);
            for (Kit.Spot x : spots) if (Math.abs(x.s() - want) < Math.abs(best.s() - want)) best = x;
            marks.add(best.p());
        }
        double[] glow = JobKit.glow(j);
        String life = RuneKit.lifeToken(sp);
        int wound = (int) k.num("wound", 2);
        JobTraits5.leechFor(sp, (int) k.num("leechFor", 100));
        // 출발: 핏빛 잔상 · 발밑 고리 · 몸에서 튀는 피 · 심장 소리
        RuneKit.shadeClone(sp, level, start, f, 1, SHADE, glow, Opt.of("life", 0.55, "fade", 0.6, "alpha", 0.9));
        JobFx.ring(level, V.up(start, 0.06), 0.9, j, false, 0.3, Opt.of("alpha", 0.85));
        JobTraits5.blood(level, V.up(start, 1.0), 0.7, j, 0.35, JobFx.hide1p(sp));
        SkillFx.play(level, "item.trident.riptide_1", start, 1.3, 0.8);
        SkillFx.play(level, "mob.warden.heartbeat", start, 1.4, 0.9);
        Kit.noFall(sp, 30);
        boolean[] stopped = {false};
        for (int s = 0; s < marks.size(); s++) {
            Vec3 to = marks.get(s);
            int ss = s;
            RuneKit.later(s, () -> {
                if (stopped[0] || !RuneKit.sameLife(sp, life)) return;
                Vec3 prev = sp.position();
                if (!Kit.moveTo(sp, to)) {
                    stopped[0] = true;
                    return;
                }
                ServerLevel d = sp.level();
                if (ss < marks.size() - 1) RuneKit.shadeClone(sp, d, prev, f, 1, SHADE, glow, Opt.of("life", 0.36, "fade", 0.6, "alpha", 0.75));
                JobFx.dash(d, V.up(prev, 0.9), V.up(to, 0.9), j, 0.3, JobFx.hide1p(sp));
                JobFx.speed(sp, j, ss == marks.size() - 1 ? 0.25 : 0.1, 0.85);
                // 지나온 마디에 떨어지는 핏방울
                Vec3 mid = V.add(prev, V.sub(to, prev), 0.5);
                for (int q = 0; q < 2; q++)
                    JobTraits5.drop(d, V.up(mid, 0.9 + Kit.rand() * 0.4), V.DOWN, 0.12, JobFx.outer(j), 0.5, JobFx.hide1p(sp).with("speed", 2.0, "fade", 0.3));
            });
        }
        double half = k.num("width", 1.6) / 2;
        double mult = k.num("mult", 3);
        int[] cnt = {0};
        for (Kit.LineHit h : RuneKit.lineTargets(sp, V.up(start, 1.0), f, len, half)) {
            LivingEntity e = h.e();
            int at = (int) Math.min(steps - 1, Math.floor(h.t() / Math.max(0.1, len) * steps));
            JobKit.strike(sp, e, j, P, mult, false, false, life, at, d -> {
                if (d > 0) JobTraits5.addWound(sp, e, j, wound);
                RuneKit.later(2, () -> {
                    if (e.isRemoved() || cnt[0]++ >= 8 || !(e.level() instanceof ServerLevel lv)) return;
                    Vec3 b = Kit.bodyPoint(e);
                    JobFx.line(lv, V.add(b, f, -1.3), V.add(b, f, 1.3), j, 0.3, Opt.empty());
                    JobFx.hit(e, j, 1.15);
                    spray(lv, b, f, 4, 0.5, 3.0, j, 0.4, Opt.empty());
                    SkillFx.play(lv, "game.player.attack.strong", b, 1.2, 0.7);
                });
            });
        }
        // 다 지나간 뒤: 길 전체 핏빛 일섬 (3인칭 · 다른 사람) · 1인칭 화면을 가로지르는 일섬 · 몸의 핏빛 기운 (흡혈 두 배)
        RuneKit.later(steps + 1, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 end = sp.position();
            JobFx.line(sp.level(), V.up(start, 1.0), V.up(V.add(end, f, 0.6), 1.0), j, 0.34, JobFx.hide1p(sp));
            Vec3 look = sp.getViewVector(1F);
            Vec3 c = V.add(V.add(sp.getEyePosition(), look, 2.6), JobFx.screenUp(look), -0.25);
            Vec3 r = V.rightOf(V.flat(look));
            JobFx.line(sp.level(), V.add(c, r, -2.6), V.add(c, r, 2.6), j, 0.3, JobFx.only1p(sp));
            JobFx.aura(sp, j, 2.4, 0.5, 0.8);
        });
        return true;
    }

    // ------------------------------------------------------------ 상처 터뜨리기

    /**
     * 상처 터뜨리기: 칼을 크게 한 바퀴 (spin — 둘레 radius 칸, 맞으면 상처 +1) → delay 틱 뒤 둘레 burstRadius 칸 적의 상처를 한꺼번에 터뜨림 (상처 하나당 burst ·
     * 최대 trait.max) — 터뜨린 피해의 heal 만큼 회복 (적의 몸에서 피가 터져 시전자에게 흘러듦)
     */
    static boolean burst(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double radius = k.num("radius", 4.5);
        String life = RuneKit.lifeToken(sp);
        // 한 바퀴: 허리 높이 둥근 베기 · 땅의 옅은 고리 · 눈앞 큰 베기 · 둘레로 튀는 피
        JobFx.ring(level, V.up(c, 1.0), radius, j, false, 0.32, Opt.of("vel", RuneKit.bodyVel(sp)));
        JobFx.ring(level, V.up(c, 0.06), radius * 0.92, j, false, 0.42, Opt.of("alpha", 0.55, "coreAlpha", 0.45));
        JobFx.slash(sp, j, 2.5, 0.45, 2.3, 0.0, 0.26);
        for (int q = 0; q < 8; q++) {
            double a = q * Math.PI / 4 + Kit.rand() * 0.3;
            Vec3 d = new Vec3(Math.cos(a), 0.15, Math.sin(a));
            JobTraits5.drop(level, V.add(V.up(c, 1.0), d, 0.8), d, 0.13, q % 2 == 0 ? JobFx.outer(j) : JobFx.inner(j), 0.4, Opt.of("speed", 5.0, "fade", 0.35));
        }
        SkillFx.sparks(level, V.up(c, 0.15), DUST, 8, 3.2);
        SkillFx.play(level, "entity.player.attack.sweep", c, 0.8, 1.0);
        SkillFx.play(level, "item.spear.attack_miss", c, 0.95, 0.85);
        int[] cnt = {0};
        for (LivingEntity e : Kit.around(sp, c, radius, 2.5)) {
            JobKit.strike(sp, e, j, P, k.num("spin", 4), false, false, life, 0, d -> {
                if (cnt[0]++ < 8) JobFx.hit(e, j, 1.15);
            });
        }
        int delay = Math.max(1, (int) k.num("delay", 10));
        // 상처가 끓어오름 (중간): 상처 있는 적의 몸에서 핏방울이 솟음
        RuneKit.later(delay / 2, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            int shown = 0;
            for (LivingEntity e : Kit.around(sp, sp.position(), k.num("burstRadius", 6), 3)) {
                if (shown >= 8 || JobTraits5.woundsOf(sp, e) <= 0 || !(e.level() instanceof ServerLevel lv)) continue;
                shown++;
                spray(lv, Kit.bodyPoint(e), V.UP, 2, 0.3, 1.6, j, 0.4, Opt.empty());
            }
            SkillFx.play(sp.level(), "mob.warden.heartbeat", sp.position(), 1.3, 0.8);
        });
        RuneKit.later(delay, () -> {
            if (RuneKit.sameLife(sp, life)) detonate(sp, j, k, P, life);
        });
        return true;
    }

    /** 상처를 터뜨림: 둘레 burstRadius 칸 — 적마다 상처 수 × burst (큰 숫자는 상처 4 이상), 들어간 피해 × heal 회복 */
    private static void detonate(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double R = k.num("burstRadius", 6);
        double per = k.num("burst", 1);
        double heal = k.num("heal", 0.15);
        int max = maxWounds(j);
        // 시전자: 주먹을 움켜쥠 — 몸의 핏빛 기운 · 가슴의 피 · 심장 소리
        JobFx.aura(sp, j, 2.6, 0.5, 0.85);
        JobTraits5.blood(level, V.up(c, 1.2), 0.8, j, 0.4, JobFx.hide1p(sp));
        SkillFx.play(level, "mob.warden.heartbeat", c, 0.9, 1.0);
        SkillFx.play(level, "entity.warden.attack_impact", c, 1.3, 0.6);
        int burst = 0;
        int[] cnt = {0};
        for (LivingEntity e : Kit.around(sp, c, R, 3)) {
            int w = Math.min(max, JobTraits5.takeWounds(sp, e));
            if (w <= 0) continue;
            burst++;
            JobKit.strike(sp, e, j, P, per * w, false, w >= 4, life, 0, d -> {
                if (d > 0) JobTraits5.leech(sp, j, d * heal);
                if (cnt[0]++ >= 8 || !(e.level() instanceof ServerLevel lv)) return;
                Vec3 b = Kit.bodyPoint(e);
                double s = 0.8 + 0.12 * w + Kit.bodyPad(e) * 0.4;
                JobTraits5.blood(lv, b, s, j, 0.45, Opt.empty());
                JobFx.burst(lv, b, s * 0.9, j, 0.3, Opt.empty());
                spray(lv, b, V.UP, 3 + w, 0.9, 3.5, j, 0.45, Opt.empty());
                // 피가 시전자에게 흘러듦 (흡혈)
                Vec3 to = V.up(sp.position(), 1.1);
                Vec3 dir = V.unit(V.sub(to, b));
                double dist = b.distanceTo(to);
                for (int q = 0; q < 3; q++)
                    JobTraits5.drop(lv, V.add(b, dir, 0.4 + q * 0.5), dir, 0.12, JobFx.inner(j), Math.min(0.6, dist / 9.0 + 0.1), Opt.of("speed", 9.0, "fade", 0.3));
                SkillFx.play(lv, "entity.magma_cube.squish", b, 0.7 + Kit.rand() * 0.2, 0.8);
            });
        }
        if (burst == 0) {
            // 상처 낸 적이 없음: 핏빛 고리만 퍼지고 사그라짐
            JobFx.ring(level, V.up(c, 0.06), R * 0.6, j, true, 0.35, Opt.of("alpha", 0.5));
            return;
        }
        JobFx.smash(level, V.up(c, 0.05), R * 0.7, j, 0.45, Opt.of("alpha", 0.8));
        JobKit.shake(sp, 10, 0.2, 0.2);
    }

    // ------------------------------------------------------------ 회그니의 맹세

    /**
     * 회그니의 맹세 (다인슬레이프를 뽑은 왕 회그니 — 끝나지 않는 싸움의 맹세): 체력 cost 를 바쳐 dur 틱 태세 — 칼 평타에 피 칼날 (echo · JobTraits5) · 흡혈 두 배 ·
     * 상처가 사라지지 않음. 그림: 칼을 얼굴 앞에 세움 — 칼날을 따라 흘러내리는 피 · 몸에서 솟는 핏빛 기운 · 발밑 고리 셋 · 둘레 핏빛 기둥 · 1인칭 눈앞 고리
     */
    static boolean oath(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        String life = RuneKit.lifeToken(sp);
        JobTraits5.payBlood(sp, j, k.num("cost", 0.15));
        int dur = JobUpgrades.ticks(sp, k, "dur", (int) k.num("dur", 160));
        JobTraits.startStance(sp, k, dur);
        for (int q = 0; q < 4; q++) {
            int qq = q;
            RuneKit.later(2 + q * 2, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                RuneKit.Hand h = RuneKit.handOf(sp);
                Vec3 blade = V.up(V.add(h.hand(), h.f(), 0.3 + qq * 0.12), 0.55 - qq * 0.12);
                JobTraits5.drop(sp.level(), blade, V.DOWN, 0.13, JobFx.outer(j), 0.5, Opt.of("speed", 2.2, "fade", 0.3));
            });
        }
        for (int i = 0; i < 3; i++) {
            int ii = i;
            RuneKit.later(3 + i * 5, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                JobFx.ring(sp.level(), V.up(sp.position(), 0.06), 1.5 + ii * 0.9, j, ii % 2 == 1, 0.5, Opt.of("alpha", 0.85 - ii * 0.15));
            });
        }
        JobFx.aura(sp, j, 2.8, 1.0, 0.95);
        RuneKit.godRays(sp, level, c, 1.8, 4, JobKit.glow(j), JobKit.core(j), Opt.of("h", 5.0, "life", 0.8, "alpha", 0.75));
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.5);
        SkillFx.play(level, "mob.warden.heartbeat", c, 1.0, 1.0);
        SkillFx.play(level, "block.respawn_anchor.charge", c, 0.8, 0.8);
        SkillFx.play(level, "block.bell.resonate", c, 0.6, 0.5);
        return true;
    }

    // ------------------------------------------------------------ 끝나지 않는 전투

    /** 네 번의 X자 (첫 베기 · 둘째 베기의 눈앞 베기 기울기) */
    private static final double[][] X_TILT = {{35, 145}, {-35, 215}, {55, 125}, {20, 160}};

    /**
     * 끝나지 않는 전투 (회그니 · 헤딘의 싸움 — 밤마다 되살아나 끝나지 않음): 체력 cost 를 바치고 앞 (100°) dash 칸 안의 가장 가까운 보이는 적 1.5칸 앞까지 rush 틱에
     * 돌진 → every 틱마다 X자로 두 번씩 네 번 (beats — 숫자 split 줄, 둘레 radius 칸) → 작게 뛰어 칼을 땅에 꽂음 — 핏빛 기둥이 터짐 (final — 둘레 finalRadius 칸 +
     * 앞 finalRange 칸 · 폭 finalWidth 에 솟는 피 기둥이 닿는 때, 적마다 한 번 · 상처 하나당 + perWound 를 터뜨림). 같은 적은 10틱 넘게 띄움
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        List<Double> beats = SwordsmanSkills.numbers(k, "beats", List.of(4.0, 4.0, 4.0, 4.0));
        int n = beats.size();
        int split = Math.max(1, (int) k.num("split", 2));
        int rush = Math.max(2, (int) k.num("rush", 6));
        int every = Math.max(10, (int) k.num("every", 10));
        int finalDelay = Math.max(10, (int) k.num("finalDelay", 16));
        int lastBeat = rush + (n - 1) * every;
        int finalAt = lastBeat + finalDelay;
        double radius = k.num("radius", 4.5);
        String life = RuneKit.lifeToken(sp);
        Vec3 c0 = sp.position();
        double[] glow = JobKit.glow(j);
        JobTraits5.payBlood(sp, j, k.num("cost", 0.1));
        List<LivingEntity> cone = RuneKit.coneTargets(sp, k.num("dash", 7) + 1.5, 100, 4);
        LivingEntity first = cone.isEmpty() ? null : cone.get(0);
        Vec3 f = Kit.facingOf(sp);
        double want = k.num("step", 3);
        if (first != null) {
            f = V.flat(V.sub(first.position(), c0));
            want = Math.max(0, Math.hypot(first.getX() - c0.x, first.getZ() - c0.z) - 1.5 - Kit.bodyPad(first));
            RuneKit.moveTo(sp, c0, V.up(first.position(), first.getBbHeight() * 0.6));
        }
        boolean held = Status.isRooted(sp) || Kit.ridingAny(sp);
        Double gy0 = RuneKit.solidGround(level0, V.up(c0, 0.1), 6);
        Vec3 base = gy0 != null && c0.y - gy0 > 0.05 ? new Vec3(c0.x, gy0, c0.z) : c0;
        double free = held || want < 0.3 ? 0 : Kit.reachFlat(level0, base, f, want);
        List<Kit.Spot> spots = free > 0.3 ? Kit.dashSpots(level0, base, f, free) : List.of();
        Vec3 stop = spots.isEmpty() ? base : spots.get(spots.size() - 1).p();
        Ult.sky(level0, c0, "rpg:ult_blood", finalAt + 25);
        RpgCombat.setSteadfast(sp, finalAt + 10);
        Kit.noFall(sp, finalAt + 30);
        // 몸: 돌진 (rush 틱) → 그 자리에 버팀 (X자 넷) → 작게 뛰어 칼을 꽂음 (8틱 — 마지막 틱에 땅)
        int hop0 = finalAt - 8;
        if (held) JobMove.run(sp, JobMove.hold(0, hop0), JobMove.hop(hop0, finalAt, from -> from, 0.7));
        else JobMove.run(sp, JobMove.to(0, rush, JobMove.at(stop), JobMove.OUT), JobMove.hold(rush, hop0), JobMove.hop(hop0, finalAt, from -> from, 0.7));
        // 발동: 발을 굴러 땅이 울림 · 몸에서 솟는 핏빛 기운 · 1인칭 눈앞 고리 · 심장 소리
        JobFx.smash(level0, V.up(c0, 0.05), 1.6, j, 0.35, Opt.of("alpha", 0.8));
        SkillFx.sparks(level0, V.up(c0, 0.2), DUST, 10, 3.5);
        JobFx.aura(sp, j, 2.6, 0.45, 0.75);
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.4);
        SkillFx.play(level0, "mob.ravager.roar", c0, 1.1, 0.6);
        SkillFx.play(level0, "mob.warden.heartbeat", c0, 1.2, 1.0);
        SkillFx.play(level0, "item.trident.riptide_1", c0, 1.0, 0.8);
        // 돌진: 핏빛 잔상 · 줄기 · 떨어지는 핏방울 · 흙먼지 · 1인칭 빠른 줄
        Vec3[] last = {c0};
        Vec3 face = f;
        for (int t = 1; t <= rush; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                Vec3 now = sp.position();
                if (now.distanceToSqr(last[0]) > 0.04) {
                    if (tt % 2 == 1) RuneKit.shadeClone(sp, d, last[0], face, 1, SHADE, glow, Opt.of("life", 0.45, "fade", 0.6, "alpha", 0.8));
                    JobFx.dash(d, V.up(last[0], 0.9), V.up(now, 0.9), j, 0.3, JobFx.hide1p(sp));
                    JobTraits5.drop(d, V.up(last[0], 1.0), V.DOWN, 0.12, JobFx.outer(j), 0.5, JobFx.hide1p(sp).with("speed", 2.0, "fade", 0.3));
                    SkillFx.sparks(d, V.up(now, 0.12), DUST, 3, 2.2);
                    JobFx.speed(sp, j, tt == rush ? 0.22 : 0.1, 0.8);
                }
                last[0] = now;
            });
        }
        for (int i = 0; i < n; i++) {
            int ii = i;
            double mult = beats.get(i);
            RuneKit.later(rush + i * every, () -> {
                if (RuneKit.sameLife(sp, life)) xBeat(sp, j, P, mult, split, ii, radius, life);
            });
        }
        // 꽂기 준비: 칼을 거꾸로 쥐고 들어 올림 — 칼에 피가 모임 (둘레에서 칼끝으로 핏방울) · 기운 · 소리
        RuneKit.later(hop0, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Vec3 top = V.up(sp.position(), 2.6);
            for (int q = 0; q < 6; q++) {
                double a = q * Math.PI / 3 + Kit.rand() * 0.4;
                Vec3 from = V.add(top, new Vec3(Math.cos(a), -0.6, Math.sin(a)), 1.6);
                JobTraits5.drop(d, from, V.unit(V.sub(top, from)), 0.12, q % 2 == 0 ? JobFx.outer(j) : JobFx.inner(j), 0.3, Opt.of("speed", 5.0, "fade", 0.3));
            }
            JobFx.aura(sp, j, 2.6, 0.5, 0.7);
            SkillFx.play(d, "block.respawn_anchor.charge", sp.position(), 1.2, 0.7);
            SkillFx.play(d, "item.trident.riptide_2", sp.position(), 1.1, 0.7);
        });
        RuneKit.later(finalAt, () -> {
            if (RuneKit.sameLife(sp, life)) bloodPillars(sp, j, k, P, life);
        });
        return true;
    }

    /** X자 하나 (i 번째 — 두 베기가 3틱 간격, 숫자 split 줄): 눈앞 큰 베기 둘 · 참격 판 둘 · 둘레 고리 · 튀는 피, 둘레 radius 칸 */
    private static void xBeat(ServerPlayer sp, Jobs.Job j, double P, double mult, int split, int i, double radius, String life) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double[] tilts = X_TILT[i % X_TILT.length];
        for (int s = 0; s < 2; s++) {
            double tilt = tilts[s];
            int ss = s;
            RuneKit.later(s * 3, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 cc = sp.position();
                Vec3 ff = Kit.facingOf(sp);
                JobFx.slash(sp, j, 2.4, 0.3, 2.5, tilt, 0.3);
                JobFx.slashCard(lv, V.up(V.add(cc, ff, 1.1), 1.1), ff, tilt, 3.3, 0.3, V.ZERO, j, JobFx.hide1p(sp));
                SkillFx.play(lv, "item.spear.attack_miss", cc, 0.9 + i * 0.1 + ss * 0.15, 0.85);
            });
        }
        JobFx.ring(level, V.up(c, 1.0), radius, j, i % 2 == 1, 0.3, Opt.of("vel", RuneKit.bodyVel(sp), "alpha", 0.75));
        spray(level, V.up(V.add(c, f, 1.0), 1.1), f, 4, 0.6, 3.5, j, 0.4, JobFx.hide1p(sp));
        SkillFx.play(level, "entity.player.attack.sweep", c, 0.8 + i * 0.08, 1.0);
        JobKit.shake(sp, 8, 0.12 + 0.03 * i, 0.15);
        int[] cnt = {0};
        for (LivingEntity e : Kit.around(sp, c, radius, 2.5)) {
            JobKit.strikeSplit(sp, e, j, P, mult, split, 3, false, false, life, 0, d -> {
                if (cnt[0]++ < 8) JobFx.hit(e, j, 1.2);
            });
        }
    }

    /**
     * 끝나지 않는 전투 마지막: 칼을 땅에 꽂음 — 큰 피 기둥 (칼끝) · 둘레 finalRadius 칸 (지금) + 앞 finalRange 칸 피 기둥 (1.8칸마다 SPLIT_SPEED 로 솟음 — 폭
     * finalWidth, 닿는 때) 의 적마다 한 번 final × (1 + perWound × 상처 — 터뜨림). 그림: 눈앞 세로 베기 · 땅 터짐 · 피 튀김 · 충격 고리 · 핏방울 비 · 번쩍임 · 흔들림
     */
    private static void bloodPillars(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        double R = k.num("finalRadius", 5);
        double range = k.num("finalRange", 8);
        double half = k.num("finalWidth", 3) / 2;
        double per = k.num("perWound", 0.05);
        int max = maxWounds(j);
        Vec3 g = new Vec3(c.x, RuneKit.groundAt(level, c, c.y), c.z);
        Vec3 tip = V.add(g, f, 1.2);
        JobFx.slash(sp, j, 2.3, -0.1, 3.0, 95.0, 0.34);
        JobFx.slashCard(level, V.up(V.add(c, f, 1.0), 1.4), f, 95.0, 3.8, 0.32, V.ZERO, j, JobFx.hide1p(sp));
        JobFx.smash(level, V.up(tip, 0.05), R, j, 0.6, Opt.empty());
        // 큰 피 기둥: 칼끝보다 조금 앞 (눈 바로 앞이면 가늘어짐 — 0.51.0 영상 1: 칼끝 1.2칸은 1인칭 기준으로 0.56 굵기라 3인칭에서 가는 줄로 보였음)
        pillar(sp, level, V.add(g, f, 2.2), 6.0, 2.0, j);
        JobTraits5.blood(level, V.up(tip, 0.8), 1.8, j, 0.5, Opt.empty());
        RuneKit.novaRing(level, V.up(g, 0.1), R * 0.4, 1.5, glow, core, Opt.of("life", 0.5));
        spray(level, V.up(tip, 0.5), V.UP, 8, 0.8, 5.0, j, 0.5, Opt.empty());
        // 핏방울 비 (조금 뒤 — 둘레에 떨어짐)
        RuneKit.later(5, () -> {
            for (int q = 0; q < 10; q++) {
                double a = Kit.rand() * Math.PI * 2;
                double rr = 0.8 + Kit.rand() * (R - 0.8);
                Vec3 p = new Vec3(g.x + Math.cos(a) * rr, g.y + 3.0 + Kit.rand() * 2.0, g.z + Math.sin(a) * rr);
                JobTraits5.drop(level, p, V.DOWN, 0.14, q % 3 == 0 ? JobFx.inner(j) : JobFx.outer(j), 0.8, Opt.of("speed", 5.0, "fade", 0.3));
            }
        });
        Ult.flash(level, V.up(c, 1.0), glow, Opt.empty(), sp);
        RuneKit.shakeAround(level, c, 16, 0.45, 0.55);
        SkillFx.play(level, "item.mace.smash_ground_heavy", c, 0.7, 1.2);
        SkillFx.play(level, "entity.warden.attack_impact", c, 0.8, 1.0);
        SkillFx.play(level, "random.explode", c, 0.6, 0.8);
        SkillFx.play(level, "mob.warden.heartbeat", c, 0.7, 1.0);
        // 앞으로 솟는 피 기둥: 1.8칸마다 그 자리에 닿는 때 (가는 기둥 · 피 튀김 · 솟는 핏방울 · 고리 · 흙먼지)
        double len = Math.max(2, RuneKit.rayLength(level, V.up(g, 0.6), f, range));
        for (double s = 3.8; s <= len + 0.01; s += 1.8) { // 큰 기둥 (2.2칸) 다음부터
            double ss = s;
            RuneKit.later((int) Math.round(ss / SPLIT_SPEED * 20), () -> {
                Vec3 p = V.add(g, f, ss);
                Vec3 m = new Vec3(p.x, RuneKit.groundAt(level, p, g.y), p.z);
                pillar(sp, level, m, 3.4, 1.1, j);
                JobTraits5.blood(level, V.up(m, 0.6), 1.0, j, 0.4, Opt.empty());
                spray(level, V.up(m, 0.3), V.UP, 4, 0.5, 4.0, j, 0.45, Opt.empty());
                JobFx.ring(level, V.up(m, 0.06), 1.1, j, false, 0.35, Opt.of("alpha", 0.8));
                SkillFx.sparks(level, V.up(m, 0.15), DUST, 4, 2.5);
            });
        }
        RuneKit.later((int) Math.round(len / SPLIT_SPEED * 20), () -> SkillFx.play(level, "entity.magma_cube.squish", V.add(g, f, len), 0.6, 0.9));
        double mult0 = k.num("final", 24);
        Set<UUID> done = new HashSet<>();
        int[] cnt = {0};
        for (LivingEntity e : Kit.around(sp, c, R, 3)) {
            done.add(e.getUUID());
            int w = Math.min(max, JobTraits5.takeWounds(sp, e));
            JobKit.strike(sp, e, j, P, mult0 * (1 + per * w), false, true, life, 0, d -> finalHitFx(e, j, c, w, cnt));
        }
        for (Kit.LineHit h : RuneKit.lineTargets(sp, V.up(g, 0.6), f, len, half)) {
            LivingEntity e = h.e();
            if (!done.add(e.getUUID())) continue;
            int w = Math.min(max, JobTraits5.takeWounds(sp, e));
            JobKit.strike(sp, e, j, P, mult0 * (1 + per * w), false, true, life, (int) Math.round(h.t() / SPLIT_SPEED * 20), d -> finalHitFx(e, j, c, w, cnt));
        }
    }

    /** 피 기둥 하나 (밑동 = at · 높이 h · 굵기 th — 눈 · 3인칭 카메라 가까이면 가늘게): 짙은 피 · 밝은 피 · 흰 심지 세 겹 */
    private static void pillar(ServerPlayer sp, ServerLevel level, Vec3 at, double h, double th, Jobs.Job j) {
        double kk = RuneKit.nearK(sp, V.up(at, 1.6), th + 1.2, 0.45);
        Vec3 mid = V.up(at, h / 2);
        SkillFx.fx(level, "rpg:arrow_shot", mid, Opt.of("dir", V.UP, "speed", 0.0, "size", h / 2, "thick", th * kk, "life", 0.6, "color", JobFx.outer(j), "alpha", 0.9));
        SkillFx.fx(level, "rpg:arrow_shot", mid, Opt.of("dir", V.UP, "speed", 0.0, "size", h / 2, "thick", th * 0.5 * kk, "life", 0.55, "color", JobFx.inner(j), "alpha",
                0.95));
        SkillFx.fx(level, "rpg:arrow_shot", mid, Opt.of("dir", V.UP, "speed", 0.0, "size", h / 2, "thick", th * 0.18 * kk, "life", 0.5, "color", WHITE, "alpha", 0.9));
    }

    /** 피 기둥에 맞은 적: 타격 그림 · 피 튀김 (상처가 많을수록 큼 — 8명까지) · 밀려남 (가운데에서 바깥으로 — 연출) */
    private static void finalHitFx(LivingEntity e, Jobs.Job j, Vec3 c, int w, int[] cnt) {
        if (JobKit.pushable(e)) RuneKit.knock(e, V.flat(V.sub(e.position(), c)), 0.35, 0.3);
        if (cnt[0]++ >= 8 || !(e.level() instanceof ServerLevel lv)) return;
        JobFx.hit(e, j, 1.7);
        JobTraits5.blood(lv, Kit.bodyPoint(e), 1.0 + 0.15 * w + Kit.bodyPad(e) * 0.5, j, 0.45, Opt.empty());
    }
}
