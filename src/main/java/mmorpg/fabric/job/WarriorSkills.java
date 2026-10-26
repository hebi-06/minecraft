package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Guards;
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
 * 0.28.0 전사 직업 스킬 (jobs.json warrior — 주황 · 진홍 불씨, 특성 「분노」): 파쇄격 · 돌격 · 대회전 · 투혼 · 격노 도약.
 * 피해 = 위력 P × 배율 (JobKit.strike — 같은 적을 다시 칠 때는 몹 무적 시간이 끝난 뒤로 미룸, 같은 적을 여러 번 치는 스킬은 10틱 넘게 띄움).
 * 연출 = 메이플식 2D 그림 (JobFx — 내려찍기 · 방어 깨짐 · 회전 칼날 · 회오리 · 불꽃 꼬리 · 눈앞 큰 베기 · 터짐, 룬 문양 · 룬 글자는 쓰지 않음).
 */
final class WarriorSkills {
    private WarriorSkills() {}

    /** 돌진 · 도약 잔상 (짙은 주황) */
    private static final double[] SHADE = {0.5, 0.18, 0.06};
    /** 흙먼지 (누런 회색) */
    private static final double[] DUST = {0.62, 0.52, 0.4};

    static void register() {
        JobSystem.register("warrior_crush", WarriorSkills::crush);
        JobSystem.register("warrior_charge", WarriorSkills::charge);
        JobSystem.register("warrior_whirl", WarriorSkills::whirl);
        JobSystem.register("warrior_spirit", WarriorSkills::spirit);
        JobSystem.register("warrior_ult", WarriorSkills::ult);
    }

    /** 발 앞 ahead 칸의 땅 (내려찍는 자리) */
    private static Vec3 groundAhead(ServerPlayer sp, Vec3 f, double ahead) {
        Vec3 a = V.add(sp.position(), f, ahead);
        return new Vec3(a.x, RuneKit.groundAt(sp.level(), a, sp.getY()), a.z);
    }

    /** 내려찍은 자리의 연출: 땅의 금 · 충격 고리 · 터짐 · 튀는 돌 · 불티 · 화면 흔들림 · 소리 */
    private static void slamFx(ServerPlayer sp, Jobs.Job j, Vec3 ground, double radius, double scale) {
        ServerLevel level = sp.level();
        JobFx.smash(level, V.up(ground, 0.05), radius, j, 0.5 + 0.1 * scale, Opt.empty());
        JobFx.burst(level, V.up(ground, 0.45), 0.9 * scale, j, 0.32, Opt.empty());
        RuneKit.rockBurst(level, V.up(ground, 0.2), (int) Math.round(6 * scale), 4.5, 0.16 * scale);
        SkillFx.sparks(level, V.up(ground, 0.3), JobKit.glow(j), (int) Math.round(8 * scale), 5);
        SkillFx.sparks(level, V.up(ground, 0.2), DUST, (int) Math.round(6 * scale), 3);
        JobKit.shake(sp, 10, 0.15 + 0.1 * scale, 0.25);
        SkillFx.play(level, "mace.heavy_smash_ground", ground, 0.9, 0.9);
        SkillFx.play(level, "random.anvil_land", ground, 0.55, 0.45);
    }

    // ------------------------------------------------------------ 파쇄격

    /**
     * 파쇄격: 도끼를 머리 위로 들어 (windup 틱 — 머리 위 반원) 앞 ahead 칸 땅을 내려찍음 — 그 둘레 radius 칸 + 앞 reach 칸 · arc 도 부채꼴 (mult), 맞은 적 breakFor 틱
     * 방어 깨짐 (받는 피해 + break · 보스 + breakBoss — 머리 위 갈라지는 방패). 분노가 rageUse 이상이면 써서 피해 × (1 + rageBonus)
     */
    static boolean crush(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int windup = (int) k.num("windup", 6);
        boolean rage = JobTraitsMore.useRage(sp, (int) k.num("rageUse", 30));
        double mult = k.num("mult", 6.5) * (rage ? 1 + k.num("rageBonus", 0.3) : 1);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        RuneKit.Hand h = RuneKit.handOf(sp);
        // 들어 올림: 머리 위로 크게 그리는 반원 · 손의 불티 (분노를 쓰면 불꽃이 터짐 + 글자)
        JobFx.slash(sp, j, 1.9, -0.75, 1.15, 180.0, windup / 20.0 + 0.08);
        // 0.47.0 입체: 몸 앞의 거대한 환영 도끼가 머리 위에서 앞 땅으로 내리찍힘 (날이 앞 — windup 틱 뒤 땅에 닿아 잠깐 박혀 있음)
        Vec3 f0 = V.flat(sp.getViewVector(1F));
        JobFx3d.swing(level0, "phantom_axe", V.up(V.add(sp.position(), f0, 1.3), 1.15), V.UP, f0, -25, 100, windup / 20.0 + 0.04, 0.4, rage ? 2.8 : 2.5, j,
                Opt.of("shine", rage ? 0.5 : 0.35, "glowAlpha", 0.55));
        SkillFx.fx(level0, "rpg:arrow_spark", V.up(h.hand(), 0.2), Opt.of("dir", V.UP, "speed", 2.4, "size", 0.14, "grow", 0.8, "life", 0.3, "color", JobKit.glow(j),
                "alpha", 1.0));
        if (rage) {
            JobFx.burst(level0, V.up(V.add(h.hand(), h.f(), 0.5), 0.35), 0.42, j, 0.3, Opt.of("vel", RuneKit.bodyVel(sp)));
            JobSystem.note(sp, "§6분노 −" + (int) k.num("rageUse", 30) + " §7(+" + Math.round(k.num("rageBonus", 0.3) * 100) + "%)");
            SkillFx.play(level0, "fire.ignite", sp.position(), 0.8, 0.9);
        }
        SkillFx.play(level0, "mob.ravager.roar", sp.position(), 1.6, 0.35);
        RuneKit.later(windup, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 f = V.flat(sp.getViewVector(1F));
            Vec3 ground = groundAhead(sp, f, k.num("ahead", 2.0));
            double radius = k.num("radius", 2.4);
            // 내려찍음: 눈앞에서 위 → 아래로 긋는 큰 베기 · 땅의 금 · 터짐
            JobFx.slash(sp, j, 2.3, 0.15, 2.0, 95.0, 0.22);
            slamFx(sp, j, ground, radius, rage ? 1.3 : 1.0);
            SkillFx.play(level, "entity.player.attack.sweep", ground, 0.7, 0.9);
            Set<UUID> seen = new HashSet<>();
            List<LivingEntity> list = new ArrayList<>();
            for (LivingEntity e : Kit.around(sp, ground, radius, 2.5)) if (seen.add(e.getUUID())) list.add(e);
            for (LivingEntity e : RuneKit.coneTargets(sp, k.num("reach", 3.4), k.num("arc", 100), 10)) if (seen.add(e.getUUID())) list.add(e);
            int[] cnt = {0};
            int breakFor = (int) k.num("breakFor", 100);
            for (LivingEntity e : list) {
                JobKit.strike(sp, e, j, P, mult, false, false, life, 0, d -> {
                    JobTraitsMore.applyBreak(e, JobKit.isBoss(e) ? k.num("breakBoss", 0.05) : k.num("break", 0.1), breakFor);
                    if (cnt[0]++ >= 8) return;
                    JobFx.hit(e, j, 1.25);
                    JobFx.breakMark(e, j, 0.45, 0.6, 1.0);
                    if (cnt[0] == 1) SkillFx.play(level, "block.chain.break", Kit.bodyPoint(e), 0.8, 0.9);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 돌격

    /**
     * 돌격: 보는 쪽 dist 칸 (벽 · 낭떠러지 앞까지) 을 steps 틱에 내달림 (한 틱에 한 마디 — 그동안 밀려나지 않음). 길 앞 (폭 width) 에 처음 닿은 적들 앞에서 멈춰
     * 어깨로 들이받음 (ram — 밀리는 적은 앞으로 조금 밀려남 · 보스 · 허수아비 · 플레이어는 밀리지 않음) → 멈춘 뒤 (적이 없으면 끝까지 가서) slamDelay 틱 뒤 앞을 짧게
     * 내려찍음 (slam — 둘레 slamRadius 칸 · 밀리는 적은 앞으로 날아감). 묶였거나 앞이 막혀 한 칸도 못 가면 쓰지 않음.
     * 0.28.0 사진첩: 적을 순간 이동으로 몸 앞에 끌고 가면 몹 위치가 클라이언트에서 몇 틱 늦게 따라와 1인칭 눈앞 · 3인칭 카메라 앞을 가렸음 → 닿으면 멈추는 몸통 박치기로
     */
    static boolean charge(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double free = Kit.reachFlat(level, start, f, k.num("dist", 8));
        List<Kit.Spot> spots = free > 0.4 ? Kit.dashSpots(level, start, f, free) : List.of();
        if (spots.isEmpty()) {
            Notify.notify(sp, "§c" + k.name() + " §7— 앞이 막혔습니다", 30);
            return false;
        }
        double len = spots.get(spots.size() - 1).s();
        int steps = Math.max(1, (int) k.num("steps", 6));
        List<Vec3> marks = new ArrayList<>();
        for (int s = 1; s <= steps; s++) {
            double want = len * s / steps;
            Kit.Spot best = spots.get(0);
            for (Kit.Spot x : spots) if (Math.abs(x.s() - want) < Math.abs(best.s() - want)) best = x;
            marks.add(best.p());
        }
        String life = RuneKit.lifeToken(sp);
        double half = k.num("width", 1.8) / 2;
        double ram = k.num("ram", 1.9);
        int slamDelay = (int) k.num("slamDelay", 3);
        RpgCombat.setSteadfast(sp, steps + slamDelay + 10);
        Kit.noFall(sp, steps + 30);
        // 출발: 발밑 흙먼지 · 어깨 앞 충돌 고리 · 소리
        RuneKit.shadeClone(sp, level, start, f, 1, SHADE, JobKit.glow(j), Opt.of("life", 0.45, "fade", 0.6, "alpha", 0.8));
        SkillFx.sparks(level, V.up(start, 0.2), DUST, 8, 3);
        SkillFx.play(level, "mob.ravager.roar", start, 1.3, 0.6);
        SkillFx.play(level, "item.trident.riptide_1", start, 0.8, 0.8);
        boolean[] done = {false};
        for (int s = 0; s < marks.size(); s++) {
            Vec3 to = marks.get(s);
            int ss = s;
            RuneKit.later(s, () -> {
                if (done[0] || !RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                Vec3 prev = sp.position();
                // 이 마디 앞의 적: 처음 닿은 적들 앞에서 멈춰 들이받음
                List<LivingEntity> hit = Kit.around(sp, V.add(to, f, 0.6), half + 0.6, 2.0);
                if (!hit.isEmpty()) {
                    done[0] = true;
                    double sPrev = V.dot(V.sub(prev, start), f);
                    double near = 1e9;
                    // 적 몸 1.5칸 앞에서 멈춤 (1인칭에서 들이받은 적이 화면을 다 가리지 않게 — 내려찍기는 그 앞까지 닿음)
                    for (LivingEntity e : hit) near = Math.min(near, V.dot(V.sub(e.position(), start), f) - 1.5 - Kit.bodyPad(e));
                    double want = Math.max(sPrev, Math.min(len, near));
                    Kit.Spot best = null;
                    for (Kit.Spot x : spots) if (x.s() <= want + 0.05 && (best == null || x.s() > best.s())) best = x;
                    Vec3 stop = prev;
                    if (best != null && best.s() > sPrev + 0.05 && Kit.moveTo(sp, best.p())) stop = best.p();
                    JobFx.dash(d, V.up(prev, 0.9), V.up(stop, 0.9), j, 0.3, JobFx.hide1p(sp));
                    ramHit(sp, j, P, ram, hit, f, life);
                    JobKit.shake(sp, 6, 0.3, 0.2);
                    JobFx.speed(sp, j, 0.25, 0.8);
                    RuneKit.later(slamDelay, () -> chargeSlam(sp, j, k, P, f, life));
                    return;
                }
                if (!Kit.moveTo(sp, to)) {
                    done[0] = true;
                    RuneKit.later(slamDelay, () -> chargeSlam(sp, j, k, P, f, life));
                    return;
                }
                // 지나온 마디: 돌진 줄기 (1인칭에서는 숨김) · 발밑 흙먼지 · 1인칭 빠른 줄 · 어깨 앞 고리
                JobFx.dash(d, V.up(prev, 0.9), V.up(to, 0.9), j, 0.3, JobFx.hide1p(sp));
                SkillFx.sparks(d, V.up(to, 0.15), DUST, 3, 2);
                JobFx.speed(sp, j, ss == marks.size() - 1 ? 0.25 : 0.1, 0.8);
                if (ss % 2 == 0) JobFx.ringFacing(d, V.up(V.add(to, f, 0.9), 1.0), 0.75, j, V.scale(f, -1), 0.18, JobFx.hide1p(sp));
                if (ss == marks.size() - 1) {
                    done[0] = true;
                    RuneKit.later(slamDelay, () -> chargeSlam(sp, j, k, P, f, life));
                }
            });
        }
        return true;
    }

    /** 들이받음: 닿은 적마다 ram · 맞은 자리 충돌 고리 · 밀리는 적은 앞으로 조금 (내려찍기 안에 남게) */
    private static void ramHit(ServerPlayer sp, Jobs.Job j, double P, double ram, List<LivingEntity> hit, Vec3 f, String life) {
        ServerLevel d = sp.level();
        int[] cnt = {0};
        for (LivingEntity e : hit) {
            JobKit.strike(sp, e, j, P, ram, false, false, life, 0, x -> {
                if (JobKit.pushable(e)) RuneKit.knock(e, f, 0.38, 0.22);
                if (cnt[0]++ >= 8) return;
                JobFx.hit(e, j, 1.2);
                JobFx.ringFacing(e.level() instanceof ServerLevel lv ? lv : d, Kit.bodyPoint(e), 0.8 + Kit.bodyPad(e) * 0.4, j, V.scale(f, -1), 0.3, Opt.empty());
            });
        }
        SkillFx.sparks(d, V.up(V.add(sp.position(), f, 0.9), 1.1), JobKit.glow(j), 8, 4);
        SkillFx.play(d, "entity.goat.ram_impact", sp.position(), 0.8, 1.0);
        SkillFx.play(d, "entity.player.attack.knockback", sp.position(), 0.7, 0.9);
    }

    /** 끝: 짧게 내려찍음 — 몸 앞 둘레 slamRadius 칸 (들이받은 적 포함), 밀리는 적은 앞으로 날아감 */
    private static void chargeSlam(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 f, String life) {
        if (!RuneKit.sameLife(sp, life)) return;
        JobKit.motion(sp, "warrior_charge:slam"); // 0.47.0 멈춰 서며 짧게 내려찍는 동작 (들이받은 때에 따라 3 ~ 9틱)
        Vec3 ground = groundAhead(sp, f, 1.2);
        double r = k.num("slamRadius", 2.6);
        JobFx.slash(sp, j, 2.2, 0.2, 1.6, 100.0, 0.2);
        slamFx(sp, j, ground, r, 0.9);
        for (LivingEntity e : Kit.around(sp, ground, r, 2.5)) {
            JobKit.strike(sp, e, j, P, k.num("slam", 2.0), false, false, life, 0, x -> {
                if (JobKit.pushable(e)) RuneKit.knock(e, f, 0.7, 0.35);
                if (RuneKit.fxQuota("jobChargeSlam", 6)) JobFx.hit(e, j, 1.2);
            });
        }
    }

    // ------------------------------------------------------------ 대회전

    /**
     * 대회전: dur 틱 동안 도끼를 휘두르며 회전 (움직일 수 있음) — gap 틱마다 (hits 번) 둘레 radius 칸 (hit), 맞힌 적마다 최대 체력 × leech 흡혈 (한 번에 leechMax 명까지).
     * 그림: 허리 높이에서 도는 회전 칼날 (몸을 따라감) · 몸에서 솟는 붉은 회오리 (3인칭) · 흩날리는 불티 · 1인칭 눈앞에서 돌아가는 베기
     */
    static boolean whirl(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int hits = (int) k.num("hits", 5);
        int gap = (int) k.num("gap", 10);
        int dur = (int) k.num("dur", 50);
        double r = k.num("radius", 3.6);
        double hit = k.num("hit", 2.34);
        double leech = JobUpgrades.num(sp, k, "leech", 0.01); // 0.31.0 강화
        int leechMax = (int) k.num("leechMax", 5);
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        // 0.47.0 입체: 몸 둘레를 도는 환영 도끼 셋 (허리 높이 · 날이 바깥 — 오른쪽으로 도는 몸과 같이)
        int[] axes = JobFx3d.orbitOwner(sp, "phantom_axe", 3, new Vec3(0, 1.0, 0), r * 0.32, -900, true, dur / 20.0 + 0.05, 1.45, j, Opt.of("fadeIn", 0.12,
                "fadeOut", 0.2, "shine", 0.35, "glowAlpha", 0.5));
        JobFx3d.guard(sp, life, axes, dur);
        SkillFx.play(sp.level(), "mob.breeze.whirl", sp.position(), 0.8, 1.0);
        SkillFx.play(sp.level(), "mob.ravager.roar", sp.position(), 1.4, 0.5);
        for (int t = 0; t <= dur; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 c = sp.position();
                Vec3 vel = RuneKit.bodyVel(sp);
                JobFx.whirl(level, V.up(c, 1.0), r * 0.92, j, 0.14, Opt.of("vel", vel, "phase", (double) ((tt / 2) % JobFx.WHIRL), "fade", 0.1, "alpha", 0.95));
                if (tt % 8 == 0) JobFx.twister(sp, j, 2.9, 0.48, 0.75);
                if (tt % 6 == 0) JobFx.slash(sp, j, 2.0, 0.45, 1.5, (tt * 60) % 360, 0.2);
                for (int i = 0; i < 2; i++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 p = V.up(V.add(c, new Vec3(Math.cos(a), 0, Math.sin(a)), r * (0.4 + Kit.rand() * 0.6)), 0.3 + Kit.rand() * 1.4);
                    SkillFx.fx(level, "rpg:arrow_spark", p, Opt.of("dir", V.unit(new Vec3(-Math.sin(a), 0.6, Math.cos(a))), "speed", 2.5, "size", 0.13, "grow", 0.6, "life",
                            0.4, "color", glow, "alpha", 1.0));
                }
                if (tt % 10 == 4) SkillFx.play(level, "entity.player.attack.sweep", c, 0.75 + (tt % 20) * 0.01, 0.6);
            });
        }
        SkillFx.every(hits, gap, i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 c = sp.position();
            int[] healed = {0};
            int[] cnt = {0};
            double each = RpgHealth.maxOf(sp) * leech;
            for (LivingEntity e : Kit.around(sp, c, r, 2.5)) {
                JobKit.strike(sp, e, j, P, hit, false, false, life, 0, d -> {
                    if (healed[0] < leechMax && each > 0) {
                        healed[0]++;
                        double got = RpgHealth.healBy(sp, each);
                        if (got > 0 && healed[0] == 1) {
                            // 흡혈 숫자는 한 번에 (그 틱의 흡혈 합 — 다음 틱에)
                            RuneKit.later(1, () -> {
                                if (!sp.isRemoved()) DamageNumbers.show(sp, each * healed[0], DamageNumbers.Style.HEAL, "", 0);
                            });
                        }
                    }
                    if (cnt[0]++ < 6) JobFx.hit(e, j, 1.0);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 투혼

    /**
     * 투혼: dur 틱 태세 — 분노 즉시 가득 (그동안 줄지 않음 · 써도 그대로), 받는 피해 −guard, 체력이 low 아래로 떨어지면 한 번 최대 체력 heal 회복 (JobTraitsMore).
     * 그림: 몸의 불꽃 (3인칭) · 발밑에서 퍼지는 둥근 베기 둘 · 1인칭 눈앞 고리 · 화면 번쩍임 · 글자, 태세 동안 8틱마다 몸 · 손의 불티 (JobTraits)
     */
    static boolean spirit(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = JobUpgrades.ticks(sp, k, "dur", 200); // 0.31.0 강화: 지속
        JobTraits.startStance(sp, k, dur);
        JobTraitsMore.spiritStart(sp, j);
        Guards.set(sp, dur, k.num("guard", 0.2), null, p -> JobTraits.stanceOn(p, k.id()));
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        JobFx.aura(sp, j, 3.0, 1.2, 1.0);
        JobFx.ring(level, V.up(c, 0.06), 2.6, j, false, 0.5, Opt.empty());
        String life = RuneKit.lifeToken(sp);
        RuneKit.later(4, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.ring(sp.level(), V.up(sp.position(), 0.08), 1.6, j, true, 0.45, Opt.of("alpha", 0.85));
        });
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.5);
        SkillFx.sparks(level, V.up(c, 1.0), JobKit.glow(j), 14, 5);
        Ult.flash(level, c, JobKit.glow(j), Opt.of("radius", 4.0, "time", new double[] {0.03, 0.05, 0.35}), sp);
        DamageNumbers.showFace(sp, "§6§l투혼", 2.4, 0, 0.2);
        SkillFx.play(level, "mob.ravager.roar", c, 1.0, 1.0);
        SkillFx.play(level, "fire.ignite", c, 0.7, 1.0);
        SkillFx.play(level, "beacon.activate", c, 1.2, 0.6);
        return true;
    }

    // ------------------------------------------------------------ 격노 도약

    /**
     * 격노 도약: 붉은 하늘 · 몸의 불꽃 → leapTicks 틱마다 (leaps 번) 가장 가까운 적 무리 (range 칸 안 — 둘레 landRadius 칸에 적이 가장 많은 적, 같으면 가까운 쪽) 곁으로
     * 높이 height 의 포물선으로 뛰어 내려찍음 (leap — 둘레 landRadius 칸, 같은 적이면 그 둘레를 돌아가며 뜀 · 적이 없으면 앞으로) → 마지막 착지에서 finalDelay 틱 뒤
     * 둘레 finalRadius 칸 대폭발 (final — 큰 숫자 · 띄움). 떨어지는 동안 몸에 불꽃 꼬리 (3인칭) · 1인칭 빠른 줄, 착지마다 땅의 금 · 구덩이
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int leaps = Math.max(1, (int) k.num("leaps", 4));
        int leapTicks = Math.max(6, (int) k.num("leapTicks", 12));
        int finalDelay = (int) k.num("finalDelay", 12);
        int finalAt = leaps * leapTicks + finalDelay;
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        Ult.sky(level0, c0, "rpg:ult_meteor", finalAt + 30);
        JobFx.aura(sp, j, 3.0, 0.8, 1.0);
        JobFx.ring(level0, V.up(c0, 0.06), 3.0, j, false, 0.5, Opt.empty());
        JobFx.eyeRing(sp, j, 2.4, 1.1, 0.5);
        SkillFx.sparks(level0, V.up(c0, 1.0), glow, 12, 4);
        SkillFx.play(level0, "mob.ravager.roar", c0, 0.8, 1.0);
        SkillFx.play(level0, "mob.enderdragon.growl", c0, 1.4, 0.5);
        Kit.noFall(sp, finalAt + 40);
        RpgCombat.setSteadfast(sp, finalAt + 10);
        Vec3[] last = {c0};
        for (int i = 0; i < leaps; i++) {
            int ii = i;
            RuneKit.later(i * leapTicks, () -> {
                if (RuneKit.sameLife(sp, life)) leap(sp, j, k, P, ii, leapTicks, last, life);
            });
        }
        // 0.47.0 입체: 대폭발 6틱 전 하늘에서 거대한 환영 도끼가 날부터 떨어져 마지막 착지 자리에 박힘
        RuneKit.later(finalAt - 6, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 at = last[0];
            double sc = 4.2;
            Vec3 stick = V.up(at, 0.75 * sc * 0.45 + 0.1);
            JobFx3d.spawn(sp.level(), "phantom_axe", V.up(stick, 14), j, Opt.of("to", stick, "ctrl", V.up(stick, 7), "travel", 0.3, "ease", "in", "dir", V.DOWN, "face",
                    Kit.facingOf(sp), "scale", sc, "life", 0.85, "fadeIn", 0.05, "fadeOut", 0.35, "glowAlpha", 0.6, "shine", 0.5));
            SkillFx.play(sp.level(), "item.trident.riptide_3", at, 0.6, 0.8);
        });
        RuneKit.later(finalAt, () -> {
            if (RuneKit.sameLife(sp, life)) finalBlast(sp, j, k, P, last[0], glow, core, life);
        });
        JobKit.motionLater(sp, "warrior_ult:final", finalAt - 6, life); // 0.47.0 도끼를 들어 포효했다가 대폭발 때 내려찍는 동작
        return true;
    }

    /** 착지 자리 (spot) 와 그때 바라볼 곳 (focus — 노린 적의 몸, 없으면 앞) */
    private record Landing(Vec3 spot, Vec3 focus) {}

    /**
     * 다음 착지 자리: range 칸 안의 적 무리 곁 (노린 적에서 2.4칸 — 1인칭에서 적이 눈앞을 가리지 않게, 같은 적이면 둘레를 돌아가며) — 적이 없으면 앞으로 5칸
     */
    private static Landing landingFor(ServerPlayer sp, Jobs.Skill k, int i) {
        ServerLevel level = sp.level();
        Vec3 from = sp.position();
        double landR = k.num("landRadius", 3.5);
        List<LivingEntity> list = Kit.visibleFrom(sp, sp.getEyePosition(), JobKit.near(sp, k.num("range", 12), 24));
        LivingEntity best = null;
        int bestN = -1;
        double bestD = 1e9;
        for (LivingEntity e : list) {
            int n = 0;
            for (LivingEntity o : list) if (o != e && o.distanceTo(e) <= landR) n++;
            double d = e.distanceToSqr(sp);
            if (n > bestN || (n == bestN && d < bestD)) {
                best = e;
                bestN = n;
                bestD = d;
            }
        }
        Vec3 want;
        Vec3 focus;
        if (best != null) {
            boolean onTop = Math.hypot(from.x - best.getX(), from.z - best.getZ()) < 0.1;
            Vec3 away = onTop ? V.scale(Kit.facingOf(sp), -1) : V.flat(V.sub(from, best.position()));
            Vec3 dir = V.turnY(away, i % 2 == 0 ? 0 : (i % 4 == 1 ? 110 : -110));
            want = V.add(best.position(), dir, 2.4 + Kit.bodyPad(best));
            focus = V.up(best.position(), best.getBbHeight() * 0.55);
        } else {
            want = V.add(from, Kit.facingOf(sp), 5);
            focus = V.up(V.add(from, Kit.facingOf(sp), 9), 1.0);
        }
        Vec3 spot = Kit.standSpot(level, new Vec3(want.x, Math.max(want.y, from.y) + 0.5, want.z), 3.0);
        if (spot == null && best != null) spot = Kit.standSpot(level, new Vec3(best.getX(), best.getY() + 0.5, best.getZ()), 3.0);
        return new Landing(spot != null ? spot : from, focus);
    }

    private static float wrapDeg(float a) {
        a %= 360F;
        if (a >= 180F) a -= 360F;
        if (a < -180F) a += 360F;
        return a;
    }

    /** 뛰어 내려찍기 한 번: leapTicks 틱 동안 포물선 (한 틱에 한 마디 — 노린 적 쪽으로 시선이 돌아감) → 착지 둘레 landRadius 칸 (leap) */
    private static void leap(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, int i, int leapTicks, Vec3[] last, String life) {
        if (i > 0) JobKit.motion(sp, "warrior_ult:leap"); // 0.47.0 도약마다 웅크림 → 도끼를 들고 뜀 → 내려찍는 동작 (첫 도약은 쓸 때의 동작)
        Vec3 from = sp.position();
        Landing landing = landingFor(sp, k, i);
        Vec3 to = landing.spot();
        last[0] = to;
        double height = k.num("height", 3.2);
        Vec3 face = Math.hypot(landing.focus().x - to.x, landing.focus().z - to.z) >= 0.1 ? V.flat(V.sub(landing.focus(), to)) : V.flat(V.sub(to, from));
        float yaw0 = sp.getYRot();
        float pitch0 = sp.getXRot();
        SkillFx.play(sp.level(), "mob.breeze.jump", from, 0.8, 0.9);
        SkillFx.sparks(sp.level(), V.up(from, 0.2), DUST, 6, 3);
        for (int s = 1; s <= leapTicks; s++) {
            int ss = s;
            RuneKit.later(s, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double t = (double) ss / leapTicks;
                Vec3 p = V.up(V.lerp(from, to, t), height * 4 * t * (1 - t));
                Vec3 prev = sp.position();
                // 시선: 처음 반쯤 동안 노린 적 쪽으로 돌아감 (뛰는 동안 적을 내려다보고, 내려서면 정면으로)
                float[] want = Kit.lookRotation(V.up(p, sp.getEyeHeight()), landing.focus());
                double q = Math.min(1, t * 1.8);
                q = q * q * (3 - 2 * q);
                float yaw = (float) (yaw0 + wrapDeg(want[0] - yaw0) * q);
                float pitch = (float) (pitch0 + (Math.max(-30F, Math.min(55F, want[1])) - pitch0) * q);
                if (!Kit.moveTo(sp, p, yaw, pitch)) Kit.moveTo(sp, p);
                ServerLevel d = sp.level();
                if (t > 0.45 && ss < leapTicks) {
                    // 떨어짐: 몸에 불꽃 꼬리 (머리 = 몸 가운데, 꼬리는 지나온 쪽 — 3인칭) · 1인칭 빠른 줄
                    Vec3 v = V.sub(p, prev);
                    if (V.len(v) > 0.05) JobFx.comet(d, V.up(p, 1.0), V.unit(v), 1.3, 0, 0.12, j, JobFx.hide1p(sp));
                    JobFx.speed(sp, j, 0.1, 0.75);
                }
                if (ss == leapTicks) land(sp, j, k, P, to, face, life);
            });
        }
    }

    /** 착지: 땅의 금 · 구덩이 · 터짐 · 둘레 landRadius 칸 (leap) */
    private static void land(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 at, Vec3 face, String life) {
        double r = k.num("landRadius", 3.5);
        JobFx.slash(sp, j, 2.2, 0.35, 1.7, 90.0, 0.2);
        slamFx(sp, j, at, r, 1.1);
        for (LivingEntity e : Kit.around(sp, at, r, 2.5)) {
            JobKit.strike(sp, e, j, P, k.num("leap", 7), false, false, life, 0, x -> {
                if (RuneKit.fxQuota("jobLeap", 6)) JobFx.hit(e, j, 1.2);
            });
        }
    }

    /** 대폭발: 땅의 큰 금 · 둥근 베기 둘 · 겹쳐 터지는 불꽃 · 솟는 빛기둥 · 번쩍임 — 둘레 finalRadius 칸 (final — 큰 숫자 · 띄움) */
    private static void finalBlast(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 at, double[] glow, double[] core, String life) {
        ServerLevel level = sp.level();
        double r = k.num("finalRadius", 7);
        JobFx.smash(level, V.up(at, 0.05), r, j, 0.9, Opt.empty());
        JobFx.ring(level, V.up(at, 0.08), r, j, false, 0.5, Opt.empty());
        RuneKit.later(3, () -> JobFx.ring(level, V.up(at, 0.1), r * 0.6, j, true, 0.45, Opt.empty()));
        for (int i = 0; i < 3; i++) {
            int ii = i;
            RuneKit.later(i * 2, () -> JobFx.burst(level, V.up(at, 0.8 + ii * 0.5), 1.8 + ii * 0.6, j, 0.4, JobFx.hide1p(sp)));
        }
        Vec3 look = sp.getViewVector(1F);
        JobFx.burst(level, V.add(V.add(sp.getEyePosition(), look, 2.8), JobFx.screenUp(look), -0.4), 1.6, j, 0.45, JobFx.only1p(sp));
        RuneKit.godRays(sp, level, at, r * 0.7, 6, glow, core, Opt.of("h", 12.0, "life", 0.9, "alpha", 0.8, "skipNear", true));
        RuneKit.rockBurst(level, V.up(at, 0.3), 14, 6.0, 0.22);
        SkillFx.sparks(level, V.up(at, 0.5), glow, 20, 7);
        Ult.flash(level, at, glow, Opt.empty(), sp);
        RuneKit.shakeAround(level, at, 16, 0.45, 0.4);
        SkillFx.play(level, "random.explode", at, 0.7, 1.0);
        SkillFx.play(level, "mace.heavy_smash_ground", at, 0.6, 1.0);
        SkillFx.play(level, "firework.large_blast", at, 0.9, 0.8);
        for (LivingEntity e : Kit.around(sp, at, r, 3.0)) {
            JobKit.strike(sp, e, j, P, k.num("final", 24), false, true, life, 0, x -> {
                if (JobKit.pushable(e)) RuneKit.knock(e, V.sub(e.position(), at), 0.5, 0.55);
                if (RuneKit.fxQuota("jobLeapFinal", 6)) JobFx.hit(e, j, 1.6);
            });
        }
    }
}
