package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.List;
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
 * 0.51.0 2차 직업 — 전사 반전 울프헤딘(라이칸) (jobs.json advance.lycan — 달빛 은회 · 붉은 눈빛, 특성 「야성」 — 사냥감을 이어 칠수록 빨라짐): 송곳니 베기 · 사냥감 덮치기 ·
 * 달밤의 포효 · 늑대의 밤 · 달 삼키기 (전사와 같은 도끼 · 같은 칸 역할의 새 스킬 다섯 — 늑대 가죽을 쓴 전사). 몸 쓰는 직업 기준 (제 몸 · 제 도끼 동작만 — 늑대 · 달은 기운
 * 그림). 피해 = 위력 P (직업 맞춤 — 전사의 초당 피해 × 1.2) × 배율 (JobKit.strike · strikeSplit — 같은 적은 10틱 넘게 띄움, 빠른 X자 · 발톱은 한 번에 넣고 숫자만 여러 줄).
 * 그림은 전사의 도끼 궤적을 달빛으로 (JobFx — 라이칸은 겉빛 = 달빛 은회) + 붉은 늑대 발톱 네 줄 (rfx_jclaw4) · 달 (rfx_jmoon) · 늑대 기운 (rfx_jwolf).
 */
final class LycanSkills {
    private LycanSkills() {}

    /** 잔상의 그림자 몸 (짙은 은회) */
    private static final double[] SHADE = {0.32, 0.34, 0.42};
    private static final double[] DUST = {0.6, 0.58, 0.55};
    private static final double[] WHITE = RuneKit.WHITE;
    /** 붉은 달 (월식의 테) */
    private static final double[] BLOOD_MOON = {0.95, 0.2, 0.16};
    private static final double[] BLOOD_MOON_CORE = {1.0, 0.62, 0.42};

    static void register() {
        JobSystem.register("lycan_fang", LycanSkills::fang);
        JobSystem.register("lycan_pounce", LycanSkills::pounce);
        JobSystem.register("lycan_howl", LycanSkills::howl);
        JobSystem.register("lycan_night", LycanSkills::night);
        JobSystem.register("lycan_ult", LycanSkills::ult);
    }

    /** 붉은 발톱 (라이칸 색 — 겉 붉은 눈빛 · 속 달빛 흰빛) */
    static void claws(ServerLevel level, Vec3 at, double size, double spin, double life, Opt o, Jobs.Job j) {
        JobFx.claw4(level, at, size, spin, life, o, JobKit.core(j), JobFx.inner(j));
    }

    /** 늑대 기운 (rfx_jwolf — 달빛 겉 · 붉은 속: 눈 · 이빨이 붉게) */
    static void wolf(ServerLevel level, Vec3 head, Vec3 dir, double size, double life, Jobs.Job j, Opt o) {
        JobFx.loop(level, "jwolf", JobFx.WOLF, V.add(head, dir, -size * JobFx.WOLF_HEAD), life, 12, o.with("dir", dir, "size", size), JobKit.glow(j), JobKit.core(j));
    }

    // ------------------------------------------------------------ 송곳니 베기

    /** 송곳니 베기 세 박자의 눈앞 베기 기울기 (X자 둘 · 발톱) */
    private static final double[][] FANG_TILT = {{35, 145}, {145, 35}};

    /**
     * 송곳니 베기: gap 틱마다 세 박자 (beats — 1.6 · 1.6 · 3.3, splits — X자는 숫자 두 줄 · 발톱은 네 줄): 한 걸음씩 내딛으며 도끼로 X자 (오른쪽 위 → 왼쪽 아래 ·
     * 왼쪽 위 → 오른쪽 아래, 3틱 사이) 두 번 → 왼손으로 크게 할큄 (발톱 네 줄 — 눈앞 · 몸 앞 큰 붉은 발톱). 앞 reach 칸 · arc 도 부채꼴의 적
     */
    static boolean fang(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        List<Double> beats = SwordmasterSkills.numbers(k, "beats", List.of(1.6, 1.6, 3.3));
        List<Double> splits = SwordmasterSkills.numbers(k, "splits", List.of(2.0, 2.0, 4.0));
        int gap = Math.max(10, (int) k.num("gap", 10));
        double reach = k.num("reach", 3.4);
        double arc = k.num("arc", 120);
        double step = k.num("step", 0.5);
        String life = RuneKit.lifeToken(sp);
        int n = beats.size();
        SkillFx.play(sp.level(), "entity.wolf.growl", sp.position(), 1.2, 0.5);
        SkillFx.every(n, gap, i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 f = V.flat(sp.getViewVector(1F));
            boolean last = i == n - 1;
            int split = (int) Math.max(1, Math.round(splits.get(Math.min(i, splits.size() - 1))));
            if (step > 0 && sp.onGround() && !Status.isRooted(sp)) Kit.launch(sp, f.x * step * 0.5, sp.getDeltaMovement().y, f.z * step * 0.5);
            if (last) {
                // 발톱 네 줄: 눈앞 큰 붉은 발톱 (1인칭) · 몸 앞 (3인칭 · 다른 사람) · 찢는 소리
                Vec3 look = sp.getViewVector(1F);
                claws(level, V.add(V.add(sp.getEyePosition(), look, 2.2), JobFx.screenUp(look), -0.15), 1.6, 25.0, 0.3, JobFx.only1p(sp).with("vel", RuneKit.bodyVel(sp)), j);
                claws(level, V.up(V.add(sp.position(), f, 1.5), 1.2), 1.4, 25.0, 0.32, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), j);
                SkillFx.play(level, "entity.player.attack.sweep", sp.getEyePosition(), 1.3, 0.9);
                SkillFx.play(level, "entity.wolf.growl", sp.position(), 1.0, 0.6);
            } else {
                double[] tilts = FANG_TILT[i % FANG_TILT.length];
                for (int s = 0; s < tilts.length; s++) {
                    double tilt = tilts[s];
                    RuneKit.later(s * 3, () -> {
                        if (!RuneKit.sameLife(sp, life)) return;
                        JobFx.slash(sp, j, 2.2, 0.3, 1.9, tilt, 0.24);
                        SkillFx.play(sp.level(), "item.spear.attack_miss", sp.getEyePosition(), 1.2 + Kit.rand() * 0.2, 0.85);
                    });
                }
            }
            int[] cnt = {0};
            for (LivingEntity e : RuneKit.coneTargets(sp, reach, arc, 10)) {
                JobKit.strikeSplit(sp, e, j, P, beats.get(i), split, 3, false, false, life, 0, d -> {
                    if (cnt[0]++ < 8) JobFx.hit(e, j, last ? 1.35 : 1.05);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 사냥감 덮치기

    /**
     * 사냥감 덮치기: 앞 (arc 도) range 칸 안의 가장 가까운 보이는 적 하나에게 leapTicks 틱 포물선으로 덮침 (그 적 앞 1.2칸에 내려앉음 · 높이 height) → 넘어뜨림 (mult ·
     * 기절 stun 틱 — 보스 제외) · 그 적이 사냥감 (야성 그대로). 적이 없으면 앞으로 miss 칸 뜀 (피해 없음). 뛰는 동안 몸 앞 늑대 기운 (3인칭) · 붉은 눈빛
     */
    static boolean pounce(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        int leapTicks = Math.max(6, (int) k.num("leapTicks", 8));
        List<LivingEntity> cone = Kit.visibleFrom(sp, sp.getEyePosition(), RuneKit.coneTargets(sp, k.num("range", 8) + 1.0, k.num("arc", 90), 6));
        LivingEntity target = cone.isEmpty() ? null : cone.get(0);
        Vec3 c0 = sp.position();
        Vec3 land;
        if (target != null) {
            Vec3 f = V.flat(V.sub(target.position(), c0));
            double dist = Math.hypot(target.getX() - c0.x, target.getZ() - c0.z);
            double want = Math.max(0, dist - 1.2 - Kit.bodyPad(target));
            double free = want < 0.2 ? 0 : Kit.reachFlat(level0, c0, f, want);
            Vec3 p = V.add(c0, f, free);
            Vec3 stand = Kit.standSpot(level0, new Vec3(p.x, Math.max(p.y, c0.y) + 0.5, p.z), 3.0);
            land = stand != null ? stand : c0;
            RuneKit.moveTo(sp, c0, V.up(target.position(), target.getBbHeight() * 0.6));
            JobTraits6.setPrey(sp, target);
        } else {
            land = JobMove.landAhead(sp, k.num("miss", 6), sp.getY()).apply(c0);
        }
        String life = RuneKit.lifeToken(sp);
        RpgCombat.setSteadfast(sp, leapTicks + 6);
        JobMove.run(sp, JobMove.hop(0, leapTicks, JobMove.at(land), k.num("height", 1.8)));
        RuneKit.shadeClone(sp, level0, c0, V.flat(V.sub(land, c0)), 1, SHADE, JobKit.glow(j), Opt.of("life", 0.45, "fade", 0.6, "alpha", 0.8));
        SkillFx.sparks(level0, V.up(c0, 0.2), DUST, 8, 3);
        SkillFx.play(level0, "entity.wolf.growl", c0, 1.1, 0.8);
        SkillFx.play(level0, "mob.breeze.jump", c0, 1.1, 0.8);
        JobTraits6.eyeGlint(sp, j, 0.5);
        Vec3[] prev = {c0};
        for (int t = 1; t < leapTicks; t++) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 p = sp.position();
                Vec3 v = V.sub(p, prev[0]);
                prev[0] = p;
                Vec3 hv = V.flat(v);
                if (V.len(hv) > 0.04) {
                    Vec3 u = V.unit(hv);
                    wolf(sp.level(), V.add(V.up(p, 1.1), u, 0.85), u, 1.4, 0.12, j, JobFx.hide1p(sp).with("solo", 44.0, "vel", V.scale(v, 20), "alpha", 0.9));
                }
                JobFx.speed(sp, j, 0.1, 0.7);
            });
        }
        RuneKit.later(leapTicks, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            Vec3 f = Kit.facingOf(sp);
            Vec3 look = sp.getViewVector(1F);
            JobFx.slash(sp, j, 2.2, 0.2, 2.0, 95.0, 0.24);
            claws(level, V.add(V.add(sp.getEyePosition(), look, 2.2), JobFx.screenUp(look), -0.2), 1.5, -20.0, 0.3, JobFx.only1p(sp), j);
            SkillFx.sparks(level, V.up(V.add(c, f, 1.0), 0.15), DUST, 8, 3);
            JobKit.shake(sp, 8, 0.25, 0.2);
            SkillFx.play(level, "entity.player.attack.knockback", c, 0.8, 0.9);
            if (target == null || target.isRemoved() || !target.isAlive() || target.distanceToSqr(sp) > 16) return;
            JobKit.strike(sp, target, j, P, k.num("mult", 3.9), false, false, life, 0, d -> {
                JobTraits4.stun(target, j, (int) k.num("stun", 10));
                if (target.level() instanceof ServerLevel lv) {
                    claws(lv, Kit.bodyPoint(target), 1.0 + Kit.bodyPad(target) * 0.6, -20.0, 0.34, Opt.empty(), j);
                    JobFx.burst(lv, Kit.bodyPoint(target), 0.8 + Kit.bodyPad(target) * 0.4, j, 0.3, Opt.empty());
                    JobFx.smash(lv, V.up(target.position(), 0.05), 1.6, j, 0.4, Opt.of("alpha", 0.8));
                }
                JobFx.hit(target, j, 1.3);
            });
        });
        return true;
    }

    // ------------------------------------------------------------ 달밤의 포효

    /**
     * 달밤의 포효: 고개를 젖혀 달을 보며 포효 (하늘에 달 · 머리 위로 울부짖는 늑대 기운) — howlAt 틱에 둘레 howlRadius 칸 (howl) · 겁먹음 fearFor 틱 (가하는 피해 −weaken
     * — 강화) → spinDelay 틱 뒤 도끼로 크게 한 바퀴 (spin — 둘레 spinRadius 칸). 그림: 퍼지는 은빛 고리 둘 · 큰 둥근 베기 · 붉은 눈빛
     */
    static boolean howl(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int howlAt = Math.max(1, (int) k.num("howlAt", 4));
        int spinAt = howlAt + Math.max(10, (int) k.num("spinDelay", 12));
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 f0 = Kit.facingOf(sp);
        // 하늘의 보름달 (앞 위 — 1 · 3인칭 모두): 은빛 원판 · 빛무리
        Vec3 moonAt = V.up(V.add(sp.getEyePosition(), f0, 7.0), 5.0);
        JobFx.moon(level0, moonAt, 1.8, 0, 1, 1.4, Opt.of("alpha", 0.95), JobKit.glow(j), WHITE);
        wolf(level0, V.up(V.add(c0, f0, -0.3), 2.7), V.UP, 1.25, 0.9, j, JobFx.hide1p(sp).with("solo", 45.0, "alpha", 0.85));
        JobTraits6.eyeGlint(sp, j, 0.8);
        SkillFx.play(level0, "item.goat_horn.sound.0", c0, 1.35, 0.9);
        SkillFx.play(level0, "entity.wolf.growl", c0, 0.8, 0.8);
        RuneKit.later(howlAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            double hr = k.num("howlRadius", 6);
            JobFx.ring(level, V.up(c, 0.08), hr * 0.6, j, false, 0.4, Opt.of("alpha", 0.9));
            RuneKit.later(3, () -> JobFx.ring(level, V.up(c, 0.1), hr, j, true, 0.45, Opt.of("alpha", 0.8)));
            JobFx.eyeRing(sp, j, 2.4, 1.2, 0.5);
            SkillFx.shock(level, V.up(c, 0.1), JobFx.inner(j), hr, 0.5);
            JobKit.shake(sp, 10, 0.2, 0.3);
            double weaken = JobUpgrades.num(sp, k, "weaken", k.num("weaken", 0.15));
            int fearFor = (int) k.num("fearFor", 100);
            int[] cnt = {0};
            for (LivingEntity e : Kit.around(sp, c, hr, 3.0)) {
                JobTraits6.fear(e, weaken, fearFor);
                JobKit.strike(sp, e, j, P, k.num("howl", 3.7), false, false, life, 0, d -> {
                    if (cnt[0]++ < 8) JobFx.hit(e, j, 1.0);
                });
            }
        });
        RuneKit.later(spinAt - 2, () -> {
            if (RuneKit.sameLife(sp, life)) SkillFx.play(sp.level(), "entity.player.attack.sweep", sp.position(), 0.7, 0.8);
        });
        RuneKit.later(spinAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            double sr = k.num("spinRadius", 4.5);
            JobFx.ring(level, V.up(c, 1.0), sr, j, false, 0.32, Opt.of("vel", RuneKit.bodyVel(sp)));
            JobFx.ring(level, V.up(c, 0.06), sr * 0.92, j, false, 0.42, Opt.of("alpha", 0.6, "coreAlpha", 0.5));
            JobFx.slash(sp, j, 2.5, 0.5, 2.4, 0.0, 0.28);
            SkillFx.sparks(level, V.up(c, 0.2), DUST, 8, 3);
            SkillFx.play(level, "entity.player.attack.sweep", c, 0.75, 1.0);
            int[] cnt = {0};
            for (LivingEntity e : Kit.around(sp, c, sr, 3.0)) {
                JobKit.strike(sp, e, j, P, k.num("spin", 8.0), false, false, life, 0, d -> {
                    if (cnt[0]++ < 8) JobFx.hit(e, j, 1.25);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 늑대의 밤

    /**
     * 늑대의 밤: dur 틱 태세 — 도끼 평타 두 번째 베기 (echo · 발톱 — JobTraits6), 이동 속도 + speed, 야성 유지 (JobTraits6). 그림: 머리 위로 울부짖는 큰 늑대 기운 ·
     * 작은 달 · 몸의 은빛 기운 · 퍼지는 고리 · 1인칭 눈앞 고리 · 붉은 눈빛 · 늑대 울음
     */
    static boolean night(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = JobUpgrades.ticks(sp, k, "dur", (int) k.num("dur", 160));
        JobTraits.startStance(sp, k, dur);
        JobTraits6.nightStart(sp, k);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        wolf(level, V.up(V.add(c, f, -0.35), 2.8), V.UP, 1.5, 0.9, j, JobFx.hide1p(sp).with("solo", 43.0, "alpha", 0.9));
        JobFx.moon(level, V.up(V.add(sp.getEyePosition(), f, 6.0), 4.2), 1.2, 0, 1, 1.0, Opt.of("alpha", 0.85), JobKit.glow(j), WHITE);
        JobFx.aura(sp, j, 3.0, 1.0, 0.9);
        JobFx.ring(level, V.up(c, 0.06), 2.6, j, false, 0.5, Opt.empty());
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.5);
        JobTraits6.eyeGlint(sp, j, 0.8);
        SkillFx.sparks(level, V.up(c, 1.0), JobKit.core(j), 12, 4);
        SkillFx.play(level, "item.goat_horn.sound.0", c, 1.55, 0.7);
        SkillFx.play(level, "entity.wolf.growl", c, 0.9, 0.7);
        return true;
    }

    // ------------------------------------------------------------ 달 삼키기

    /**
     * 달 삼키기: 밤하늘 · 앞 하늘의 큰 달이 검은 그림자에 차례로 삼켜짐 → first 틱부터 every 틱마다 둘레 range 칸 안의 적 사이를 여섯 번 덮침 (pounces — 6틱 포물선 ·
     * 적마다 돌아가며, 같은 적이면 둘레를 돌며) · 내려앉으며 찢음 (그 적 하나 — 붉은 발톱) → 마지막 덮침 뒤 rise 틱에 riseHeight 칸 솟아 (달이 붉은 테만 남음)
     * finalDelay 틱 (마지막 덮침부터) 에 내려찍음 — 둘레 finalRadius 칸 (final — 큰 숫자) · 발톱 네 갈래 자국 · 붉은 달빛 기둥
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        List<Double> pounces = SwordmasterSkills.numbers(k, "pounces", List.of(4.5, 4.5, 4.5, 4.5, 4.5, 4.5));
        int n = pounces.size();
        int first = Math.max(4, (int) k.num("first", 6));
        int every = Math.max(10, (int) k.num("every", 10));
        int lastLand = first + (n - 1) * every;
        int finalDelay = Math.max(12, (int) k.num("finalDelay", 18));
        int finalAt = lastLand + finalDelay;
        int rise = Math.max(4, (int) k.num("rise", 10));
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 f0 = Kit.facingOf(sp);
        Ult.sky(level0, c0, "rpg:ult_night", finalAt + 25);
        RpgCombat.setSteadfast(sp, finalAt + 10);
        Kit.noFall(sp, finalAt + 40);
        // 하늘의 달 (앞 위 — 쓴 자리에 머묾): 보름달 → 여섯 덮침 동안 그림자가 삼킴 (0 ~ 5) → 붉은 테 (6 ~ 7 · 마지막)
        Vec3 moonAt = V.up(V.add(c0, f0, 12.0), 9.0);
        JobFx.moon(level0, moonAt, 3.2, 0, 6, (lastLand + 4) / 20.0, Opt.of("alpha", 0.95), JobKit.glow(j), WHITE);
        RuneKit.later(lastLand + 4, () -> JobFx.moon(level0, moonAt, 3.4, 6, 2, (finalDelay + 18) / 20.0, Opt.of("alpha", 0.95), BLOOD_MOON, BLOOD_MOON_CORE));
        JobFx.aura(sp, j, 3.0, 0.8, 1.0);
        JobFx.eyeRing(sp, j, 2.4, 1.1, 0.5);
        JobTraits6.eyeGlint(sp, j, 1.0);
        SkillFx.play(level0, "item.goat_horn.sound.0", c0, 1.2, 1.0);
        SkillFx.play(level0, "entity.wolf.growl", c0, 0.75, 1.0);
        LivingEntity[] lastPrey = {null};
        for (int i = 0; i < n; i++) {
            int ii = i;
            double mult = pounces.get(i);
            RuneKit.later(first - 6 + i * every, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                if (ii > 0) JobKit.motion(sp, "lycan_ult:pounce");
                ultPounce(sp, j, k, P, ii, mult, lastPrey, life);
            });
        }
        // 마지막 덮침 뒤: 솟아오름 → 버팀 → 내려찍으며 내려앉음 (finalAt 에 땅)
        RuneKit.later(lastLand, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            double ground = sp.getY();
            int hold = Math.max(rise + 1, finalDelay - 4);
            JobMove.run(sp, JobMove.to(0, rise, JobMove.up(sp, k.num("riseHeight", 5.0)), JobMove.OUT), JobMove.hold(rise, hold), JobMove.to(hold, finalDelay,
                    JobMove.down(sp, ground), JobMove.IN));
            JobKit.motion(sp, "lycan_ult:final");
            SkillFx.play(sp.level(), "mob.breeze.jump", sp.position(), 0.8, 1.0);
            SkillFx.sparks(sp.level(), V.up(sp.position(), 0.2), DUST, 8, 3);
        });
        RuneKit.later(lastLand + rise, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            wolf(d, V.up(sp.position(), 2.6), V.UP, 1.6, 0.8, j, JobFx.hide1p(sp).with("solo", 45.0, "alpha", 0.9));
            SkillFx.play(d, "item.goat_horn.sound.0", sp.position(), 1.05, 1.0);
        });
        RuneKit.later(finalAt, () -> {
            if (RuneKit.sameLife(sp, life)) moonFall(sp, j, k, P, life);
        });
        return true;
    }

    /** 덮칠 곳: range 칸 안의 보이는 적 중 앞 덮침에서 노리지 않은 가장 가까운 적 (없으면 같은 적의 둘레를 돌며 · 적이 없으면 앞으로 4칸) — 그 적 1.4칸 앞 */
    private static Vec3[] pounceSpot(ServerPlayer sp, Jobs.Skill k, int i, LivingEntity[] lastPrey) {
        ServerLevel level = sp.level();
        Vec3 from = sp.position();
        List<LivingEntity> list = Kit.visibleFrom(sp, sp.getEyePosition(), JobKit.near(sp, k.num("range", 10), 16));
        LivingEntity best = null;
        for (LivingEntity e : list) if (e != lastPrey[0]) {
            best = e;
            break;
        }
        if (best == null && !list.isEmpty()) best = list.get(0);
        if (best == null) {
            Vec3 p = V.add(from, Kit.facingOf(sp), 4);
            Vec3 stand = Kit.standSpot(level, new Vec3(p.x, p.y + 0.5, p.z), 3.0);
            return new Vec3[] {stand != null ? stand : from, V.up(V.add(from, Kit.facingOf(sp), 8), 1.0), null};
        }
        lastPrey[0] = best;
        boolean onTop = Math.hypot(from.x - best.getX(), from.z - best.getZ()) < 0.1;
        Vec3 away = onTop ? V.scale(Kit.facingOf(sp), -1) : V.flat(V.sub(from, best.position()));
        Vec3 dir = V.turnY(away, i % 2 == 0 ? 75 : -75);
        Vec3 want = V.add(best.position(), dir, 1.4 + Kit.bodyPad(best));
        Vec3 spot = Kit.standSpot(level, new Vec3(want.x, Math.max(want.y, from.y) + 0.5, want.z), 3.0);
        if (spot == null) spot = Kit.standSpot(level, new Vec3(best.getX(), best.getY() + 0.5, best.getZ()), 3.0);
        return new Vec3[] {spot != null ? spot : from, V.up(best.position(), best.getBbHeight() * 0.55), null};
    }

    /** 덮침 하나 (6틱 포물선 — 노린 적을 보며, 몸 앞 늑대 기운) → 내려앉으며 찢음: 노린 적 하나 (mult) · 붉은 발톱 · 터짐 */
    private static void ultPounce(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, int i, double mult, LivingEntity[] lastPrey, String life) {
        Vec3[] spot = pounceSpot(sp, k, i, lastPrey);
        LivingEntity target = lastPrey[0];
        Vec3 from = sp.position();
        Vec3 to = spot[0];
        Vec3 focus = spot[1];
        int ticks = 6;
        double height = k.num("height", 1.6);
        SkillFx.play(sp.level(), "mob.breeze.jump", from, 1.15, 0.7);
        RuneKit.shadeClone(sp, sp.level(), from, V.flat(V.sub(to, from)), 1, SHADE, JobKit.glow(j), Opt.of("life", 0.35, "fade", 0.6, "alpha", 0.75));
        for (int s = 1; s <= ticks; s++) {
            int ss = s;
            RuneKit.later(s, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double t = (double) ss / ticks;
                Vec3 p = V.up(V.lerp(from, to, t), height * 4 * t * (1 - t));
                Vec3 prev = sp.position();
                if (!RuneKit.moveTo(sp, p, focus)) Kit.moveTo(sp, p);
                ServerLevel d = sp.level();
                Vec3 v = V.sub(p, prev);
                Vec3 hv = V.flat(v);
                if (ss < ticks && V.len(hv) > 0.04) {
                    Vec3 u = V.unit(hv);
                    wolf(d, V.add(V.up(p, 1.1), u, 0.85), u, 1.3, 0.12, j, JobFx.hide1p(sp).with("solo", 44.0, "vel", V.scale(v, 20), "alpha", 0.85));
                }
                JobFx.speed(sp, j, 0.08, 0.7);
                if (ss == ticks) {
                    Vec3 look = sp.getViewVector(1F);
                    claws(d, V.add(V.add(sp.getEyePosition(), look, 2.2), JobFx.screenUp(look), -0.2), 1.4, i % 2 == 0 ? 25.0 : -25.0, 0.28, JobFx.only1p(sp), j);
                    SkillFx.sparks(d, V.up(p, 0.15), DUST, 5, 2.6);
                    SkillFx.play(d, "entity.player.attack.sweep", p, 1.2 + i * 0.05, 0.8);
                    if (target == null || target.isRemoved() || !target.isAlive() || target.distanceToSqr(sp) > 12) return;
                    JobKit.strike(sp, target, j, P, mult, false, false, life, 0, x -> {
                        if (!(target.level() instanceof ServerLevel lv)) return;
                        claws(lv, Kit.bodyPoint(target), 1.0 + Kit.bodyPad(target) * 0.6, i % 2 == 0 ? 25.0 : -25.0, 0.32, Opt.empty(), j);
                        JobFx.hit(target, j, 1.2);
                    });
                }
            });
        }
    }

    /**
     * 달 삼키기 마지막 (내려앉는 틱): 붉은 달빛 · 땅의 갈라짐 · 고리 둘 · 발톱 네 갈래 자국 (앞으로 뻗음) · 눈앞 · 몸 앞 큰 붉은 발톱 · 빛기둥 · 번쩍임 — 둘레 finalRadius 칸
     * (final — 큰 숫자 · 밀쳐냄)
     */
    private static void moonFall(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 at = new Vec3(c.x, RuneKit.groundAt(level, c, c.y), c.z);
        double r = k.num("finalRadius", 6);
        double[] glow = JobKit.glow(j);
        double[] red = JobKit.core(j);
        JobFx.smash(level, V.up(at, 0.05), r, j, 0.9, Opt.empty());
        JobFx.ring(level, V.up(at, 0.08), r, j, false, 0.5, Opt.empty());
        RuneKit.later(3, () -> JobFx.ring(level, V.up(at, 0.1), r * 0.6, j, true, 0.45, Opt.empty()));
        Vec3 look = sp.getViewVector(1F);
        claws(level, V.add(V.add(sp.getEyePosition(), look, 2.6), JobFx.screenUp(look), -0.3), 2.2, 0.0, 0.42, JobFx.only1p(sp), j);
        claws(level, V.up(V.add(at, f, 1.8), 1.3), 2.6, 0.0, 0.42, JobFx.hide1p(sp), j);
        Vec3 rr = V.rightOf(f);
        for (int s = 0; s < 4; s++) {
            Vec3 d = V.unit(V.add(f, rr, (s - 1.5) * 0.16));
            Vec3 g = V.add(V.add(at, f, 0.8), rr, (s - 1.5) * 0.55);
            double len = Math.max(2, RuneKit.rayLength(level, V.up(g, 0.6), d, 8));
            clawRift(level, g, d, len, red);
        }
        RuneKit.godRays(sp, level, at, r * 0.6, 6, glow, red, Opt.of("h", 12.0, "life", 0.9, "alpha", 0.8, "skipNear", true));
        RuneKit.rockBurst(level, V.up(at, 0.3), 12, 5.5, 0.2);
        SkillFx.sparks(level, V.up(at, 0.5), red, 16, 6);
        SkillFx.sparks(level, V.up(at, 0.5), glow, 12, 6);
        Ult.flash(level, at, red, Opt.empty(), sp);
        RuneKit.shakeAround(level, at, 16, 0.45, 0.4);
        SkillFx.play(level, "random.explode", at, 0.8, 0.9);
        SkillFx.play(level, "item.mace.smash_ground_heavy", at, 0.7, 1.1);
        SkillFx.play(level, "entity.wolf.growl", at, 0.7, 1.0);
        for (LivingEntity e : Kit.around(sp, at, r, 3.0)) {
            JobKit.strike(sp, e, j, P, k.num("final", 25), false, true, life, 0, x -> {
                if (JobKit.pushable(e)) RuneKit.knock(e, V.sub(e.position(), at), 0.6, 0.45);
                if (RuneKit.fxQuota("jobMoonFall", 6)) JobFx.hit(e, j, 1.6);
            });
        }
    }

    /** 발톱 자국 한 갈래 (땅에 누운 가는 균열 — 붉은 빛 · 흰 금, 앞으로 뻗음) */
    private static void clawRift(ServerLevel level, Vec3 g, Vec3 f, double len, double[] glow) {
        double piece = 1.8;
        double spin = Math.toDegrees(Math.atan2(f.z, -f.x));
        List<Vec3> mids = new ArrayList<>();
        for (double s = 0.3; s < len; s += piece) mids.add(V.add(g, f, s + piece / 2));
        for (int i = 0; i < mids.size(); i++) {
            Vec3 mid = mids.get(i);
            double size = piece / 2 * 1.2 * (1 - 0.5 * i / Math.max(1, mids.size()));
            RuneKit.later(i, () -> {
                Vec3 m = new Vec3(mid.x, RuneKit.groundAt(level, mid, g.y), mid.z);
                Opt base = Opt.of("dir", V.UP, "size", size, "life", 1.4, "fade", 0.35, "spin", spin + (Kit.rand() < 0.5 ? 180 : 0));
                RuneKit.rfx(level, "fissure", V.up(m, 0.04), base.with("cell", 0.0, "color", new double[] {0.08, 0.02, 0.03}, "alpha", 0.9));
                RuneKit.rfx(level, "fissure", V.up(m, 0.06), base.with("cell", 1.0, "color", glow, "alpha", 0.95));
                RuneKit.rfx(level, "fissure", V.up(m, 0.08), base.with("cell", 2.0, "color", WHITE, "alpha", 1.0));
                SkillFx.sparks(level, V.up(m, 0.15), glow, 2, 2.2);
            });
        }
    }
}
