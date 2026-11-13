package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import mmorpg.core.Jobs;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneMob;
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
 * 2026-11-06 수호기사 반전 다크 나이트 (다시 — 사용자 "레인보우 컨셉이 개짜치고 반전도 이렇다할 멋이 없음 … 나는 멋이 제일 중요함" → 고른 것: 타락한 흑기사, jobs.json advance
 * darkknight — 검붉은 핏빛 · 심연, 특성 「탐식」 — JobTraits15): 1차의 막고 지키는 방패가 굶주린 저주의 아가리가 되어 적을 물어뜯고 통째로 삼키는 근접 딜러 — 크런치 · 헝거
 * 런지 · 피스트 · 언홀리 프렌지 · 어비설 메이. 물기가 들어갈 때마다 굶주림 (JobTraits15.bitSkill) · 가득이면 그 물기가 적을 통째로 삼킴 (JobTraits15.devourTake — 고정
 * 피해 JobKit.strikeFlat). 몸 쓰는 직업이라 제 몸 · 방패 동작만 (방패의 아가리 · 검은 혀 · 땅의 아가리는 그림). 화면은 흔들지 않음.
 */
final class DarkknightSkills {
    private DarkknightSkills() {}

    static final double[] VOID = JobTraits15.VOID;
    static final double[] BLOOD = JobTraits15.BLOOD;
    static final double[] CRIMSON = JobTraits15.CRIMSON;
    static final double[] CRIMSON_CORE = JobTraits15.CRIMSON_CORE;
    /** 헝거 런지 앞의 아가리 · 피스트 · 메이의 검은 혀 · 메이 (solo — 주인마다 하나씩 다시 그림) */
    static final double MAW_SOLO = 100.0;
    static final double TONGUE_SOLO = 101.0;
    static final double ABYSS_SOLO = 110.0;

    static void register() {
        JobSystem.register("darkknight_crunch", DarkknightSkills::crunch);
        JobSystem.register("darkknight_lunge", DarkknightSkills::lunge);
        JobSystem.register("darkknight_feast", DarkknightSkills::feast);
        JobSystem.register("darkknight_frenzy", DarkknightSkills::frenzy);
        JobSystem.register("darkknight_ult", DarkknightSkills::ult);
    }

    /** 방패 앞 (가슴 높이 · 앞 ahead 칸) */
    private static Vec3 shieldFront(ServerPlayer sp, double ahead) {
        return V.up(V.add(sp.position(), Kit.facingOf(sp), ahead), 1.1);
    }

    /** 1인칭 눈앞 아래 (내민 방패의 아가리 — 화면 아래 가운데) */
    private static Vec3 eyeLow(ServerPlayer sp, double ahead, double down) {
        Vec3 look = sp.getViewVector(1F);
        return V.add(V.add(sp.getEyePosition(), look, ahead), JobFx.screenUp(look), -down);
    }

    /** 가장 가까운 적 (range 칸 — 없으면 null) */
    private static LivingEntity nearest(ServerPlayer sp, double range) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : Kit.around(sp, sp.position(), range, 4)) {
            if (!e.isAlive() || e.isRemoved()) continue;
            double d = e.distanceToSqr(sp);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** 앞 reach 칸 · arc° 의 가장 가까운 적 (없으면 null) */
    private static LivingEntity nearestFront(ServerPlayer sp, double reach, double arc) {
        List<LivingEntity> cone = RuneKit.coneTargets(sp, reach, arc, 4);
        return cone.isEmpty() ? null : cone.get(0);
    }

    /** 물기 한 번 (그 적 e — mult 배율): 굶주림이 가득이면 통째로 삼킴 (고정 피해) · 들어가면 굶주림 + then */
    static void bite(ServerPlayer sp, LivingEntity e, Jobs.Job j, double P, double mult, boolean big, String life, int delay, Runnable then) {
        double flat = JobTraits15.devourTake(sp, j, e, P);
        JobKit.strikeFlat(sp, e, j, P, mult, flat, false, big || flat > 0, life, delay, d -> {
            if (flat > 0) JobTraits15.devoured(sp, j, e, flat);
            JobTraits15.bitSkill(sp, j);
            if (then != null) then.run();
        });
    }

    /** 문 자리의 핏빛 (그 적 — 물려 찢긴 핏방울 · 붉은 터짐) */
    private static void bloodFx(LivingEntity e, double scale) {
        if (!(e.level() instanceof ServerLevel el)) return;
        Vec3 b = Kit.bodyPoint(e);
        double pad = Kit.bodyPad(e);
        JobFx.burst(el, b, (0.9 + pad * 0.4) * scale, CRIMSON, CRIMSON_CORE, 0.28, Opt.of("vel", RuneKit.entVel(e)));
        SkillFx.sparks(el, b, BLOOD, (int) (8 * scale), 3.5 * scale);
    }

    /** 적을 to 쪽으로 끌어당김 (밀 수 있는 적만 · 한 번에 많아야 step 칸 · 막히면 그대로) — 다 오면 true */
    static boolean dragToward(LivingEntity e, Vec3 to, double step) {
        if (!JobKit.pushable(e) || e.isRemoved() || !e.isAlive()) return true;
        Vec3 p = e.position();
        Vec3 d = new Vec3(to.x - p.x, 0, to.z - p.z);
        double l = V.len(d);
        if (l < 0.2) return true;
        Kit.moveTo(e, V.add(p, V.scale(d, 1 / l), Math.min(step, l)));
        e.resetFallDistance();
        return l <= step;
    }

    // ------------------------------------------------------------ 크런치

    /**
     * 크런치 (롤 렝가 Q · 초가스 R): 방패를 앞으로 내밀면 방패의 아가리가 벌어져 biteAt 틱에 앞 reach 칸 (arc°) 의 적 하나를 물어뜯음 (bite) → window 틱 안에 다시 누르면
     * (gap 틱 뒤부터 — JobRecast) 한 걸음 내디디며 더 크게 벌려 tearAt 틱에 물어 찢고 내 쪽으로 끌어옴 (tear · pull 칸)
     */
    static boolean crunch(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int stage = Math.min(1, JobRecast.stage(sp, k.id()));
        if (stage == 0) JobRecast.open(sp, k, 1, (int) k.num("window", 50), (int) k.num("gap", 11), null);
        else {
            JobRecast.close(sp, k.id());
            JobRecast.motion(sp, k.id() + ":2");
        }
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        double reach = k.num("reach", 3.5) + (stage == 1 ? 0.8 : 0);
        double arc = k.num("arc", 70);
        int at = Math.max(2, (int) (stage == 0 ? k.num("biteAt", 4) : k.num("tearAt", 3)));
        double size = stage == 0 ? 0.85 : 1.2; // 2026-11-06 영상 검토: 0.7 · 1.0 은 몸에 가려 작게 보였음
        // 벌어짐: 내민 방패의 아가리 (3인칭 방패 앞 · 1인칭 화면 아래)
        JobFx15.dmaw(level0, shieldFront(sp, 1.0), size, 0, 3, at / 20.0 + 0.02, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "solo", MAW_SOLO), VOID,
                BLOOD);
        JobFx15.dmaw(level0, eyeLow(sp, 1.5, 0.3), size * 0.6, 0, 3, at / 20.0 + 0.02, false, JobFx.only1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.9), VOID,
                BLOOD);
        if (stage == 1) JobMove.run(sp, JobMove.to(0, at, JobMove.ahead(sp, 0.8, 0), JobMove.OUT));
        SkillFx.play(level0, "entity.wolf.growl", sp.position(), stage == 0 ? 0.8 : 0.6, 0.8);
        RuneKit.later(at, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            Vec3 f = Kit.facingOf(sp);
            LivingEntity e = nearestFront(sp, reach, arc);
            // 콱 다묾: 방패 앞 (적이 있으면 그 적) 의 아가리가 다물며 번쩍 → 찢음은 핏빛이 튐
            Vec3 snapAt = e != null ? V.add(Kit.bodyPoint(e), V.flat(V.sub(c, e.position())), 0.3) : shieldFront(sp, 1.3);
            JobFx15.dmaw(lv, snapAt, size + (e != null ? Kit.bodyPad(e) * 0.3 : 0), 3, stage == 0 ? 1 : 2, stage == 0 ? 0.16 : 0.3, false, Opt.of("solo", MAW_SOLO,
                    "owner", (double) sp.getId(), "vel", e != null ? RuneKit.entVel(e) : V.ZERO, "alpha", 1.0), VOID, BLOOD);
            JobFx.slash(sp, j, 1.5, 0.15, stage == 0 ? 1.6 : 2.2, stage == 0 ? 10.0 : 190.0, 0.2);
            if (stage == 1) JobFx15.fury(lv, V.up(V.add(c, f, 0.4), 0.06), f, reach, 0, 5, 0.45, Opt.of("alpha", 0.95), VOID, BLOOD);
            SkillFx.play(lv, "entity.ravager.attack", c, stage == 0 ? 1.2 : 0.9, 0.8);
            SkillFx.play(lv, "entity.player.attack.knockback", c, 0.8, 0.6);
            if (e == null) return;
            if (stage == 0) {
                bite(sp, e, j, P, k.num("bite", 1.6), false, life, 0, () -> {
                    JobImpact.hit(sp, e, j, 1.3);
                    bloodFx(e, 1.0);
                });
            } else {
                bite(sp, e, j, P, k.num("tear", 2.4), false, life, 0, () -> {
                    JobImpact.hit(sp, e, j, 1.7);
                    bloodFx(e, 1.5);
                    if (JobKit.pushable(e)) RuneKit.knock(e, V.scale(f, -1), k.num("pull", 0.9), 0.15);
                });
            }
        });
        if (stage == 0) JobSystem.note(sp, "§4다시 누르면 물어 찢음 §7(" + Math.round(k.num("window", 50) / 20.0 * 10) / 10.0 + "초)");
        return true;
    }

    // ------------------------------------------------------------ 헝거 런지

    /**
     * 헝거 런지 (롤 워윅 R): 아가리를 앞세워 겨눈 적 (range 칸 — 없으면 가장 가까운 적 · 앞 fallback 칸) 에게 뛰어들어 biteAt 틱에 물고 (bite · stun 틱 기절) drag 칸을
     * dragTicks 틱 동안 끌고 감. 묶였거나 타고 있으면 쓰지 않음
     */
    static boolean lunge(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int biteAt = Math.max(4, (int) k.num("biteAt", 8));
        double range = k.num("range", 10);
        LivingEntity e = Kit.pickTarget(sp, range, 20);
        if (e == null) e = nearest(sp, k.num("fallback", 6) + 2);
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 land;
        Vec3 dir;
        if (e != null) {
            Vec3 back = V.flat(V.sub(c0, e.position()));
            Vec3 u = V.len(back) > 0.1 ? back : V.scale(f, -1);
            dir = V.scale(V.unit(u), -1);
            Vec3 s = JobTraits12.beyond(sp, e.position(), u, 0.9 + Kit.bodyPad(e));
            land = s != null ? s : c0;
        } else {
            dir = f;
            double can = RuneKit.dashDistance(sp, f, k.num("fallback", 6), 0.3, 1.5);
            land = V.add(c0, f, can);
        }
        LivingEntity target = e;
        Vec3 to = land;
        RpgCombat.setSteadfast(sp, biteAt + 3);
        boolean moves = V.hdist(to, c0) > 0.4;
        if (moves) JobMove.run(sp, JobMove.hold(0, 1), JobMove.to(1, biteAt, JobMove.at(to), JobMove.IN));
        if (target != null) JobFx.target(level0, RuneKit.overHead(target, 0.6), 0.45 + Kit.bodyPad(target) * 0.2, j, V.scale(dir, -1), 0, biteAt / 20.0 + 0.1, Opt.of(
                "vel", RuneKit.entVel(target)));
        JobFx.smoke(level0, V.up(c0, 0.6), 1.0, VOID, BLOOD, 0.6, Opt.of("alpha", 0.6, "dir", V.UP, "speed", 0.5));
        SkillFx.play(level0, "entity.wolf.growl", c0, 0.6, 1.0);
        SkillFx.play(level0, "entity.ravager.step", c0, 0.7, 0.8);
        // 뛰어듦: 앞세운 아가리 (매 틱 다시 그림 — 벌어짐) · 지나간 자리의 검붉은 줄 · 연기 (2틱마다). 몸을 따라감: JobMove 는 자리를 옮겨 놓아 몸의 속도가 0 — 뛰어드는
        // 길 (1 → biteAt 틱 · 빨라짐 IN) 에서 다음 틱까지 옮길 만큼을 속도로, 벌어짐은 이어지게 (phase)
        Vec3[] last = {c0};
        int span = Math.max(1, biteAt - 1);
        for (int t = 1; t < biteAt; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 p = sp.position();
                Vec3 vel = moves ? V.scale(V.sub(to, c0), 20 * (JobMove.ease(tt / (double) span, JobMove.IN) - JobMove.ease((tt - 1) / (double) span, JobMove.IN)))
                        : V.ZERO;
                JobFx15.dmaw(lv, shieldFront(sp, 1.1), 0.9, 1, 2, 0.12, true, Opt.of("solo", MAW_SOLO, "owner", (double) sp.getId(), "vel", vel, "fade", 0.0, "fps",
                        12.0, "phase", (tt * 12 / 20.0) % 2, "view", 1.0), VOID, BLOOD);
                if (tt % 2 == 0) return;
                if (V.hdist(p, last[0]) > 0.3) {
                    JobFx.line(lv, V.up(last[0], 0.9), V.up(p, 0.9), CRIMSON, CRIMSON_CORE, 0.3, Opt.of("alpha", 0.9));
                    JobFx.smoke(lv, V.up(last[0], 0.8), 0.7, VOID, BLOOD, 0.5, Opt.of("alpha", 0.5));
                }
                last[0] = p;
            });
        }
        JobFx.speed(sp, j, biteAt / 20.0, 0.6);
        RuneKit.later(biteAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            LivingEntity x = target != null && target.isAlive() && !target.isRemoved() && target.distanceTo(sp) <= 3.2 + Kit.bodyPad(target) ? target
                    : nearestFront(sp, 2.6, 90);
            Vec3 snapAt = x != null ? V.add(Kit.bodyPoint(x), V.flat(V.sub(c, x.position())), 0.3) : shieldFront(sp, 1.3);
            // 콱 묾: 그 적을 덮친 아가리 · 눈앞 큰 베기 · 핏빛
            JobFx15.dmaw(lv, snapAt, 1.0 + (x != null ? Kit.bodyPad(x) * 0.3 : 0), 3, 2, 0.3, false, Opt.of("solo", MAW_SOLO, "owner", (double) sp.getId(), "vel",
                    x != null ? RuneKit.entVel(x) : V.ZERO, "alpha", 1.0), VOID, BLOOD);
            JobFx.slash(sp, j, 1.5, 0.2, 2.0, 200.0, 0.22);
            SkillFx.play(lv, "entity.ravager.attack", c, 1.0, 0.9);
            SkillFx.play(lv, "entity.wolf.growl", c, 0.7, 0.7);
            if (x == null) return;
            Vec3 d = V.flat(dir);
            bite(sp, x, j, P, k.num("bite", 2.4), false, life, 0, () -> {
                JobImpact.hit(sp, x, j, 1.6);
                bloodFx(x, 1.3);
                JobTraits4.stun(x, j, (int) k.num("stun", 10));
            });
            // 끌고 감: 문 채 drag 칸 (나도 따라감 — 밀 수 있는 적만)
            if (!JobKit.pushable(x)) return;
            int n = Math.max(1, (int) k.num("dragTicks", 6));
            double step = k.num("drag", 2.5) / n;
            Vec3 me = V.add(c, d, k.num("drag", 2.5) * 0.9);
            JobMove.run(sp, JobMove.to(0, n, JobMove.at(me), JobMove.OUT));
            for (int i = 1; i <= n; i++) {
                RuneKit.later(i, () -> {
                    if (!RuneKit.sameLife(sp, life) || x.isRemoved() || !x.isAlive()) return;
                    Kit.moveTo(x, V.add(x.position(), d, step));
                    x.resetFallDistance();
                    if (x.level() instanceof ServerLevel el) SkillFx.sparks(el, V.up(x.position(), 0.2), BLOOD, 2, 1.5);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 피스트

    /** 2026-11-13 3D 심연의 아가리 (tools/job_mobs_art.py job_maw — 저주받은 방패의 얼굴 · 뿔 · 붉은 눈 · 송곳니 · 빛나는 목구멍) · 땅에서 솟는 아가리 크기 (core 4칸) */
    private static final String MAW = "rpg:job_maw";
    private static final double PIT_SCALE = 3.4;

    /**
     * 피스트 (롤 탐 켄치 · 블리츠크랭크 Q): 방패를 앞에 박으면 pullAt 틱에 아가리에서 검은 혀 (많아야 count) 가 둘레 radius 칸 적에게 뻗어 감아 (pull) 방패 앞으로 끌어옴 →
     * biteAt 틱 거대하게 벌어진 아가리가 둘레 biteRadius 칸을 한꺼번에 물어뜯음 (bite · 문 적마다 굶주림)
     */
    static boolean feast(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int pullAt = Math.max(2, (int) k.num("pullAt", 6));
        int biteAt = Math.max(pullAt + 10, (int) k.num("biteAt", 16));
        double r = k.num("radius", 6);
        RpgCombat.setSteadfast(sp, biteAt + 3);
        JobMove.run(sp, JobMove.hold(0, biteAt + 2));
        Vec3 c0 = sp.position();
        Vec3 f0 = Kit.facingOf(sp);
        Vec3 mouth = V.up(V.add(c0, f0, 1.4), 1.0);
        Vec3 spot = V.add(c0, f0, 1.9);
        // 박음: 방패의 아가리가 벌어짐 · 발밑 검붉은 금
        // (수명은 물어뜯는 아가리가 나온 뒤까지 — solo 가 바꿔 그림. 2026-11-06 영상 검토: biteAt − 2 까지면 그 사이 한두 틱 아가리가 비었음)
        // 2026-11-13 3D 방패 아가리 (직업 도감 메모 "방패 아가리 3d로" — 2D 아가리 그림 대신): 방패 앞에서 커지며 나타나 벌어짐 → 혀가 끌어오는 동안 벌린 채 →
        // biteAt − 4 에 앞으로 나가며 거대해져 크게 벌림 → biteAt 에 콱 다묾 · 짓씹다 흩어짐
        RuneMob maw = RuneMob.spawn(MAW, level0, V.up(mouth, -1.15), f0, biteAt + 30, 0.9, true);
        if (maw != null) {
            maw.hold("summon");
            RuneKit.later(4, () -> {
                if (maw.alive()) maw.hold("open");
            });
        } else JobFx15.dmaw(level0, mouth, 1.1, 0, 3, (biteAt + 2) / 20.0, false, Opt.of("solo", MAW_SOLO, "owner", (double) sp.getId(), "fade", 0.0), VOID, BLOOD);
        RuneKit.later(biteAt - 4, () -> {
            if (maw == null || !maw.alive() || !RuneKit.sameLife(sp, life)) return;
            Vec3 f = Kit.facingOf(sp);
            maw.hold("wide");
            maw.size(1.75);
            maw.moveTo(V.up(V.add(sp.position(), f, 1.9), -0.15), 4, 0, RuneMob.OUT, false);
        });
        RuneKit.glowCrack(level0, V.up(V.add(c0, f0, 1.2), 0.04), 1.8, CRIMSON, CRIMSON_CORE, Opt.of("life", biteAt / 20.0 + 0.3));
        SkillFx.play(level0, "entity.warden.sniff", c0, 0.8, 1.0);
        SkillFx.play(level0, "item.shield.block", c0, 0.5, 0.8);
        List<LivingEntity> caught = new ArrayList<>();
        RuneKit.later(pullAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            List<LivingEntity> all = new ArrayList<>();
            for (LivingEntity e : Kit.around(sp, c, r, 4)) if (e.isAlive() && !e.isRemoved()) all.add(e);
            all.sort(Comparator.comparingDouble(e -> e.distanceToSqr(sp)));
            int n = Math.min(all.size(), (int) k.num("count", 6));
            for (int i = 0; i < n; i++) caught.add(all.get(i));
            SkillFx.play(lv, "entity.warden.tendril_clicks", c, 0.7, 1.0);
            SkillFx.play(lv, "item.trident.throw", c, 0.5, 1.0);
            for (LivingEntity e : caught) {
                JobKit.strike(sp, e, j, P, k.num("pull", 2.4), false, false, life, 0, d -> {
                    JobImpact.hit(sp, e, j, 1.1);
                    bloodFx(e, 0.7);
                });
            }
            if (caught.isEmpty()) {
                // 빈 혀: 앞으로 뻗었다가 돌아옴
                for (int i = -1; i <= 1; i++) {
                    Vec3 d = V.unit(V.add(f0, V.scale(V.rightOf(f0), i * 0.45)));
                    JobFx15.tendril(lv, mouth, V.add(mouth, d, r * 0.8), 0, 4, 0.35, true, Opt.of("alpha", 0.95), VOID, BLOOD);
                }
            }
        });
        // 감아 끌어옴: 2틱마다 혀를 다시 그림 (방패 → 그 적) · 적을 방패 앞으로
        for (int t = pullAt; t < biteAt; t += 2) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                for (int i = 0; i < caught.size(); i++) {
                    LivingEntity e = caught.get(i);
                    if (e.isRemoved() || !e.isAlive() || e.level() != lv) continue;
                    dragToward(e, spot, 1.1);
                    JobFx15.tendril(lv, mouth, Kit.bodyPoint(e), 0, 4, 0.14, true, Opt.of("solo", TONGUE_SOLO + i, "owner", (double) sp.getId(), "fade", 0.0, "fps",
                            14.0, "alpha", 0.95), VOID, BLOOD);
                }
            });
        }
        JobKit.motionLater(sp, "darkknight_feast:bite", biteAt - 3, life);
        RuneKit.later(biteAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            Vec3 f = Kit.facingOf(sp);
            Vec3 center = V.add(c, f, 1.9);
            // 한꺼번에 물어뜯음: 거대하게 벌어졌다 다무는 아가리 (3D — 콱 다묾 · 짓씹음 · 흩어짐) → 삼킴 · 검붉은 부채 · 핏빛
            if (maw != null && maw.alive()) {
                maw.hold("snap");
                RuneKit.later(3, () -> {
                    if (maw.alive()) maw.anim("chew");
                });
                RuneKit.later(12, () -> maw.leave("vanish", 7));
            } else JobFx15.dmaw(lv, V.up(center, 1.2), 2.1, 2, 4, 0.5, false, Opt.of("solo", MAW_SOLO, "owner", (double) sp.getId(), "alpha", 1.0), VOID, BLOOD);
            JobFx15.fury(lv, V.up(V.add(c, f, 0.4), 0.06), f, 3.4, 0, 6, 0.5, Opt.of("alpha", 0.95), VOID, BLOOD);
            JobFx.burst(lv, V.up(center, 1.0), 2.2, CRIMSON, CRIMSON_CORE, 0.35, Opt.empty());
            SkillFx.sparks(lv, V.up(center, 1.0), BLOOD, 20, 6);
            JobFx.slash(sp, j, 1.6, 0.0, 2.6, 90.0, 0.24);
            SkillFx.play(lv, "entity.ravager.attack", center, 0.7, 1.0);
            SkillFx.play(lv, "entity.warden.attack_impact", center, 0.8, 1.0);
            SkillFx.play(lv, "entity.generic.eat", center, 0.6, 1.0);
            for (LivingEntity e : Kit.around(sp, center, k.num("biteRadius", 3), 3)) {
                if (!e.isAlive() || e.isRemoved()) continue;
                bite(sp, e, j, P, k.num("bite", 4.8), false, life, 0, () -> {
                    JobImpact.hit(sp, e, j, 1.6);
                    bloodFx(e, 1.3);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 언홀리 프렌지

    /**
     * 언홀리 프렌지 (롤 워윅 Q · 렝가): dur 틱 태세 (강화 지속) — 방패의 눈이 붉게 뜨고 아가리가 열린 채 (JobTraits15.stanceFx): 방패 평타가 물어뜯기 (평타 + 위력 × bite
     * · 굶주림 × hunger) · 공격 속도 + speed (JobTraits15)
     */
    static boolean frenzy(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) JobUpgrades.num(sp, k, "dur", 160);
        JobTraits.startStance(sp, k, dur);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 g = ThunderlordSkills.ground(level, c);
        RuneKit.Hand h = RuneKit.handOf(sp);
        // 깨어남: 방패의 아가리가 포효 (고리) · 발밑에서 솟는 검붉은 불 · 붉은 금 · 연기
        JobFx15.dmaw(level, V.up(V.add(h.hand(), h.f(), 0.5), 0.25), 0.55, 6, 1, 0.45, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "solo", 93.0), VOID,
                BLOOD);
        JobFx15.cinder(level, g, 3.0, 0, 3, 0.5, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), VOID, BLOOD);
        RuneKit.glowCrack(level, V.up(g, 0.04), 2.2, CRIMSON, CRIMSON_CORE, Opt.of("life", 1.0));
        RuneKit.novaRing(level, c, 0.6, 3.0, CRIMSON, CRIMSON_CORE, Opt.of("life", 0.4));
        JobFx.smoke(level, V.up(c, 1.2), 1.3, VOID, BLOOD, 0.9, JobFx.hide1p(sp).with("dir", V.UP, "speed", 0.7, "alpha", 0.55));
        JobFx.aura(sp, j, 2.8, 1.0, 0.9);
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.45);
        SkillFx.play(level, "entity.ravager.roar", c, 0.8, 0.9);
        SkillFx.play(level, "entity.wolf.growl", c, 0.6, 1.0);
        JobTraits.sendGauge(sp, false);
        Notify.notify(sp, "§4§l" + k.name() + " §7— 방패 평타가 물어뜯기 (+위력 × " + k.num("bite", 0.5) + ") · 공격 속도 +" + Math.round(k.num("speed", 0.2) * 100)
                + "% · 굶주림 × " + Math.round(k.num("hunger", 2)) + " · " + dur / 20 + "초", 40);
        return true;
    }

    // ------------------------------------------------------------ 어비설 메이

    /**
     * 어비설 메이 (롤 탐 켄치 R · 초가스 R): 심연의 하늘 (rpg:ult_abyss) 아래 방패를 땅에 박으면 (plantAt 틱) 가운데 (겨눈 적 — range 칸, 없으면 앞 ahead 칸) 둘레 radius
     * 칸 땅이 검붉게 갈라지고 검은 혀가 적을 가운데로 끌어당김 → swallowAt 틱 땅에서 거대한 아가리가 솟아 둘레 core 칸을 통째로 삼킴 (swallow · stun 틱 기절) → 짓씹다가
     * crushAt 틱 물어 터뜨림 (crush · 큰 숫자 · 밀쳐냄)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int plantAt = Math.max(4, (int) k.num("plantAt", 10));
        int swallowAt = Math.max(plantAt + 8, (int) k.num("swallowAt", 26));
        int crushAt = Math.max(swallowAt + 10, (int) k.num("crushAt", 44));
        double r = k.num("radius", 6);
        double core = k.num("core", 4);
        Vec3 c0 = sp.position();
        Vec3 f0 = Kit.facingOf(sp);
        Ult.sky(level0, c0, "rpg:ult_abyss", crushAt + 30);
        RpgCombat.setSteadfast(sp, crushAt + 4);
        JobMove.run(sp, JobMove.hold(0, crushAt + 4));
        LivingEntity aimed = Kit.pickTarget(sp, k.num("range", 10), 22);
        Vec3 center0 = aimed != null ? aimed.position() : V.add(c0, f0, RuneKit.dashDistance(sp, f0, k.num("ahead", 3.5), 0.3, 1.5));
        Vec3 center = ThunderlordSkills.ground(level0, center0);
        // 치켜듦: 방패로 빨려 드는 검붉은 연기 · 가운데 땅의 붉은 과녁
        JobFx.charge(sp, j, new Vec3(0.0, 2.6, 0.2), plantAt, 0.9);
        JobFx.smoke(level0, V.up(c0, 1.6), 1.4, VOID, BLOOD, plantAt / 20.0 + 0.3, JobFx.hide1p(sp).with("dir", V.UP, "speed", 0.6, "alpha", 0.6));
        JobFx15.halo(level0, V.up(center, 0.06), core, plantAt / 20.0 + 0.2, Opt.of("alpha", 0.8, "fade", 0.1), VOID, BLOOD);
        SkillFx.play(level0, "entity.warden.roar", c0, 0.5, 0.8);
        SkillFx.play(level0, "entity.wither.ambient", c0, 0.5, 0.6);
        List<LivingEntity> held = new ArrayList<>();
        RuneKit.later(plantAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            // 박음: 방패가 땅을 침 → 가운데 둘레 땅이 검붉게 갈라짐 · 둘레의 검붉은 불기둥 · 깨어나는 아가리 (금)
            JobFx.smash(lv, V.up(V.add(c, Kit.facingOf(sp), 0.8), 0.05), 1.6, j, 0.45, Opt.empty());
            RuneKit.glowCrack(lv, V.up(center, 0.04), r, CRIMSON, CRIMSON_CORE, Opt.of("life", (crushAt - plantAt) / 20.0 + 0.5));
            JobFx15.halo(lv, V.up(center, 0.06), r, (crushAt - plantAt) / 20.0 + 0.3, Opt.of("alpha", 0.9, "fade", 0.1), VOID, BLOOD);
            JobFx15.abyss(lv, center, core, 0, 1, (swallowAt - 6 - plantAt) / 20.0, false, Opt.of("solo", ABYSS_SOLO, "owner", (double) sp.getId(), "fade", 0.0), VOID,
                    BLOOD);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4 + Math.PI / 8;
                Vec3 p = ThunderlordSkills.ground(lv, V.add(center, new Vec3(Math.cos(a), 0, Math.sin(a)), r * 0.92));
                JobFx15.cinder(lv, p, 2.6, 0, 2, 0.12, false, Opt.of("alpha", 0.95), VOID, BLOOD);
                RuneKit.later(2, () -> JobFx15.cinder(lv, p, 2.6, 2, 4, (crushAt - plantAt - 2) / 20.0, true, Opt.of("alpha", 0.9, "fade", 0.05), VOID, BLOOD));
            }
            SkillFx.play(lv, "item.mace.smash_ground_heavy", c, 0.8, 0.7);
            SkillFx.play(lv, "entity.warden.emerge", center, 0.9, 1.0);
            for (LivingEntity e : Kit.around(sp, center, r, 4)) if (e.isAlive() && !e.isRemoved()) held.add(e);
        });
        // 끌어당김: 가운데에서 둘레 적에게 검은 혀 (2틱마다 다시 그림) · 적을 가운데로
        for (int t = plantAt + 1; t < swallowAt; t += 2) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 root = V.up(center, 0.3);
                for (int i = 0; i < held.size() && i < 8; i++) {
                    LivingEntity e = held.get(i);
                    if (e.isRemoved() || !e.isAlive() || e.level() != lv) continue;
                    dragToward(e, center, 0.45);
                    if (V.hdist(e.position(), center) > 0.8) JobFx15.tendril(lv, root, Kit.bodyPoint(e), 0, 4, 0.14, true, Opt.of("solo", TONGUE_SOLO + i, "owner",
                            (double) sp.getId(), "fade", 0.0, "fps", 14.0, "alpha", 0.95), VOID, BLOOD);
                }
            });
        }
        // 2026-11-13 3D 심연의 아가리 (직업 도감 메모 "아가리 3d로" — 2D 아가리 그림 대신): swallowAt − 8 에 가운데 땅속에서 하늘을 보며 솟아 크게 벌림 (8틱) →
        // swallowAt 콱 다묾 → 짓씹음 → crushAt 에 물어 터뜨리며 가라앉음
        RuneMob[] pit = {null};
        RuneKit.later(swallowAt - 8, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 face = V.flat(V.sub(sp.position(), center));
            pit[0] = RuneMob.spawn(MAW, sp.level(), center, face, crushAt - swallowAt + 40, PIT_SCALE * Math.max(0.6, core / 4.0), true);
            if (pit[0] != null) pit[0].hold("pit_rise");
        });
        // 턱 끝이 땅을 뚫음 (솟기 직전)
        RuneKit.later(swallowAt - 6, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            if (pit[0] == null || !pit[0].alive()) JobFx15.abyss(lv, center, core, 1, 2, 0.3, false, Opt.of("solo", ABYSS_SOLO, "owner", (double) sp.getId(), "fade", 0.0),
                    VOID, BLOOD);
            SkillFx.play(lv, "block.sculk_shrieker.shriek", center, 0.6, 0.7);
            SkillFx.sparks(lv, V.up(center, 0.3), BLOOD, 16, 6);
        });
        RuneKit.later(swallowAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            // 삼킴: 콱 다무는 아가리 (번쩍) → 짓씹음 (이어 돌림 — 3D 아가리)
            if (pit[0] != null && pit[0].alive()) {
                pit[0].hold("pit_snap");
                RuneKit.later(3, () -> {
                    if (pit[0].alive()) pit[0].anim("pit_chew");
                });
            } else {
                JobFx15.abyss(lv, center, core, 3, 1, 0.15, false, Opt.of("solo", ABYSS_SOLO, "owner", (double) sp.getId(), "fade", 0.0, "alpha", 1.0), VOID, BLOOD);
                RuneKit.later(3, () -> JobFx15.abyss(lv, center, core, 4, 2, (crushAt - swallowAt - 3) / 20.0, true, Opt.of("solo", ABYSS_SOLO, "owner",
                        (double) sp.getId(), "fade", 0.0, "fps", 6.0), VOID, BLOOD));
            }
            JobFx.burst(lv, V.up(center, 2.0), 3.0, CRIMSON, CRIMSON_CORE, 0.35, Opt.empty());
            SkillFx.sparks(lv, V.up(center, 1.5), BLOOD, 24, 7);
            Ult.flash(lv, center, CRIMSON, Opt.of("radius", 6.0, "time", new double[] {0.02, 0.05, 0.3}), sp);
            SkillFx.play(lv, "entity.ravager.attack", center, 0.5, 1.0);
            SkillFx.play(lv, "entity.warden.attack_impact", center, 0.6, 1.0);
            for (LivingEntity e : Kit.around(sp, center, core, 4)) {
                if (!e.isAlive() || e.isRemoved()) continue;
                bite(sp, e, j, P, k.num("swallow", 8), false, life, 0, () -> {
                    JobImpact.hit(sp, e, j, 1.6);
                    JobTraits4.stun(e, j, (int) k.num("stun", 20));
                    bloodFx(e, 1.2);
                });
            }
        });
        // 짓씹음: 씹는 소리 · 이음매의 핏빛
        for (int t = swallowAt + 5; t < crushAt; t += 6) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                SkillFx.play(lv, "entity.generic.eat", center, 0.4, 0.9);
                SkillFx.sparks(lv, V.up(center, 2.0), BLOOD, 8, 3);
                for (LivingEntity e : Kit.around(sp, center, core, 4)) if (e.isAlive() && !e.isRemoved()) dragToward(e, center, 0.35);
            });
        }
        JobKit.motionLater(sp, "darkknight_ult:crush", crushAt - 4, life);
        RuneKit.later(crushAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            // 물어 터뜨림: 아가리가 터지며 빛살 · 조각 → 연기 속에 가라앉음 (3D 아가리가 부풀어 가라앉음) · 둘레 기둥이 무너짐 · 충격 고리
            if (pit[0] != null && pit[0].alive()) pit[0].leave("pit_sink", 10);
            JobFx15.abyss(lv, center, core, 6, 1, 0.3, false, Opt.of("solo", ABYSS_SOLO, "owner", (double) sp.getId(), "fade", 0.0, "alpha", 1.0), VOID, BLOOD);
            RuneKit.later(6, () -> JobFx15.abyss(lv, center, core, 7, 1, 0.7, false, Opt.of("solo", ABYSS_SOLO, "owner", (double) sp.getId(), "fade", 0.4), VOID,
                    BLOOD));
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4 + Math.PI / 8;
                Vec3 p = ThunderlordSkills.ground(lv, V.add(center, new Vec3(Math.cos(a), 0, Math.sin(a)), r * 0.92));
                JobFx15.cinder(lv, p, 3.0, 6, 2, 0.5, false, Opt.of("alpha", 0.95), VOID, BLOOD);
            }
            RuneKit.novaRing(lv, center, 1.0, r * 1.5 - 1, CRIMSON, CRIMSON_CORE, Opt.of("life", 0.5));
            RuneKit.later(2, () -> RuneKit.novaRing(lv, center, 0.8, r * 1.2 / 0.8 - 1, VOID, BLOOD, Opt.of("life", 0.45)));
            JobFx.burst(lv, V.up(center, 1.6), 3.6, CRIMSON, CRIMSON_CORE, 0.45, Opt.empty());
            JobFx.smoke(lv, V.up(center, 1.0), r * 0.7, VOID, BLOOD, 1.3, Opt.of("alpha", 0.6, "dir", V.UP, "speed", 0.7));
            JobFx.radialWaves(sp, j, center, 8, 1.0, r + 1.5, 0.9, 1.5, 0.3, 15);
            SkillFx.sparks(lv, V.up(center, 1.0), BLOOD, 30, 9);
            Ult.flash(lv, center, CRIMSON, Opt.of("radius", 8.0, "time", new double[] {0.02, 0.07, 0.45}), sp);
            SkillFx.play(lv, "entity.generic.explode", center, 0.7, 1.0);
            SkillFx.play(lv, "entity.ravager.roar", center, 0.6, 1.0);
            SkillFx.play(lv, "entity.warden.sonic_boom", center, 1.4, 0.4);
            for (LivingEntity e : Kit.around(sp, center, core + 0.5, 4)) {
                if (!e.isAlive() || e.isRemoved()) continue;
                bite(sp, e, j, P, k.num("crush", 24), true, life, 0, () -> {
                    JobImpact.hit(sp, e, j, 2.6);
                    bloodFx(e, 1.8);
                    if (JobKit.pushable(e)) RuneKit.knock(e, V.flat(V.sub(e.position(), center)), 1.0, 0.45);
                });
            }
        });
        return true;
    }
}
