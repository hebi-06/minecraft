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
 * 0.51.0 2차 직업 — 전사 각성 뵈드바르(워로드) (jobs.json advance.warlord — 주황 · 갈색 곰 기운, 특성 「분노」 → 다 차면 곰의 화신): 투구 깨부수기 · 곰의 메치기 ·
 * 도끼 태풍 · 곰의 포효 · 뵈드바르의 앞발 (사용자 "각성도 기존에 컨셉을 유지한채로 스킬을 바꿔도 됨 더 역동적인 모션을 사용한다던가" — 전사의 다섯 스킬과 같은 칸 ·
 * 같은 컨셉을 더 큰 몸 동작으로). 몸 쓰는 직업 기준 (제 몸 · 제 도끼 동작만 — 전사의 환영 도끼 · 늑대 대신 제 도끼의 궤적 · 곰의 혼 그림). 피해 = 위력 P (직업 맞춤 —
 * 전사의 초당 피해 × 1.2) × 배율 (JobKit.strike — 같은 적은 10틱 넘게 띄움). 그림은 전사의 도끼 궤적 · 갈라지는 땅 · 곰 발톱 세 줄을 워로드 색으로 (JobFx.fid) +
 * 곰의 혼 (rfx_jbear) · 곰 앞발 (rfx_jpaw).
 */
final class WarlordSkills {
    private WarlordSkills() {}

    /** 돌진 · 도약 잔상 (짙은 갈색) */
    private static final double[] SHADE = {0.4, 0.2, 0.07};
    /** 흙먼지 (누런 회색) */
    private static final double[] DUST = {0.62, 0.52, 0.4};
    /** 갈라진 땅의 속 (어두운 틈) */
    private static final double[] RIFT = {0.05, 0.03, 0.02};
    private static final double[] WHITE = RuneKit.WHITE;
    /** 갈라짐이 앞으로 뻗는 빠르기 (칸/초) */
    private static final double SPLIT_SPEED = 32;

    static void register() {
        JobSystem.register("warlord_crush", WarlordSkills::crush);
        JobSystem.register("warlord_throw", WarlordSkills::toss);
        JobSystem.register("warlord_storm", WarlordSkills::storm);
        JobSystem.register("warlord_roar", WarlordSkills::roar);
        JobSystem.register("warlord_ult", WarlordSkills::ult);
    }

    /** 발 앞 ahead 칸의 땅 */
    private static Vec3 groundAhead(Vec3 feet, Vec3 f, double ahead, ServerLevel level) {
        Vec3 a = V.add(feet, f, ahead);
        return new Vec3(a.x, RuneKit.groundAt(level, a, feet.y), a.z);
    }

    /** 내려찍은 자리: 갈라지는 땅 · 터짐 · 튀는 돌 · 불티 · 흙먼지 · 화면 흔들림 · 소리 */
    private static void slamFx(ServerPlayer sp, Jobs.Job j, Vec3 ground, double radius, double scale) {
        ServerLevel level = sp.level();
        JobFx.smash(level, V.up(ground, 0.05), radius, j, 0.5 + 0.1 * scale, Opt.empty());
        JobFx.burst(level, V.up(ground, 0.45), 0.9 * scale, j, 0.32, Opt.empty());
        RuneKit.rockBurst(level, V.up(ground, 0.2), (int) Math.round(6 * scale), 4.5, 0.16 * scale);
        SkillFx.sparks(level, V.up(ground, 0.3), JobKit.glow(j), (int) Math.round(8 * scale), 5);
        SkillFx.sparks(level, V.up(ground, 0.2), DUST, (int) Math.round(7 * scale), 3);
        JobKit.shake(sp, 10, 0.15 + 0.1 * scale, 0.25);
        SkillFx.play(level, "mace.heavy_smash_ground", ground, 0.85, 0.9);
        SkillFx.play(level, "random.anvil_land", ground, 0.5, 0.45);
    }

    /** 앞으로 뻗는 균열 한 갈래 (조각마다 그 자리에 닿는 때 — 어두운 틈 · 주황 · 흰 금, 땅에 눕힘) · 튀는 돌 · 흙먼지 */
    private static void rift(ServerLevel level, Vec3 g, Vec3 f, double len, double[] glow, double width) {
        double piece = 2.0;
        double spin = Math.toDegrees(Math.atan2(f.z, -f.x));
        for (double s = 0.4; s < len; s += piece) {
            double a0 = s;
            double a1 = Math.min(len, s + piece * 1.3);
            int at = (int) Math.round(a0 / SPLIT_SPEED * 20);
            RuneKit.later(at, () -> {
                Vec3 mid = V.add(g, f, (a0 + a1) / 2);
                Vec3 m = new Vec3(mid.x, RuneKit.groundAt(level, mid, g.y), mid.z);
                double size = (a1 - a0) / 2 * 1.25 * width;
                Opt base = Opt.of("dir", V.UP, "size", size, "life", 1.6, "fade", 0.35, "spin", spin + (Kit.rand() < 0.5 ? 180 : 0));
                RuneKit.rfx(level, "fissure", V.up(m, 0.035), base.with("cell", 0.0, "color", RIFT, "alpha", 0.92));
                RuneKit.rfx(level, "fissure", V.up(m, 0.055), base.with("cell", 1.0, "color", glow, "alpha", 0.95));
                RuneKit.rfx(level, "fissure", V.up(m, 0.075), base.with("cell", 2.0, "color", WHITE, "alpha", 1.0));
                RuneKit.rockBurst(level, V.up(m, 0.15), 2, 2.8, 0.14);
                SkillFx.sparks(level, V.up(m, 0.15), DUST, 3, 2.4);
            });
        }
    }

    // ------------------------------------------------------------ 투구 깨부수기

    /**
     * 투구 깨부수기: 앞으로 lunge 칸 · 높이 jump 칸 뛰어올라 (windup 틱 — 공중에서 오른쪽으로 한 바퀴 돌며 도끼를 머리 위로) 앞 ahead 칸 땅을 내려찍음 — 그 둘레
     * radius 칸 + 앞 rift 칸 (폭 riftWidth — 갈라짐이 닿는 때) 의 적마다 한 번 (mult), 맞은 적 breakFor 틱 방어 깨짐 (받는 피해 + break · 보스 + breakBoss)
     */
    static boolean crush(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int windup = Math.max(6, (int) k.num("windup", 12));
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 f0 = Kit.facingOf(sp);
        Vec3 land = JobMove.landAhead(sp, k.num("lunge", 2.0), sp.getY()).apply(sp.position());
        JobMove.run(sp, JobMove.hop(0, windup, JobMove.at(land), k.num("jump", 3.0)));
        RpgCombat.setSteadfast(sp, windup + 4);
        // 뛰어오름: 발밑 흙먼지 · 도끼를 감아 올림 (손의 불티) · 포효
        SkillFx.sparks(level0, V.up(sp.position(), 0.2), DUST, 8, 3);
        RuneKit.Hand h = RuneKit.handOf(sp);
        SkillFx.fx(level0, "rpg:arrow_spark", V.up(h.hand(), 0.2), Opt.of("dir", V.UP, "speed", 2.4, "size", 0.14, "grow", 0.8, "life", 0.3, "color", JobKit.glow(j),
                "alpha", 1.0));
        SkillFx.play(level0, "mob.breeze.jump", sp.position(), 0.75, 0.9);
        SkillFx.play(level0, "mob.ravager.roar", sp.position(), 1.5, 0.35);
        // 공중에서 한 바퀴 (3 ~ 8틱): 허리 높이 둥근 도끼 궤적 · 눈앞 돌아가는 베기 · 몸을 따라가는 바람
        for (int t = 3; t <= 8; t += 1) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                if (tt == 4) JobFx.ring(level, V.up(sp.position(), 1.0), 2.2, j, false, 0.28, Opt.of("vel", RuneKit.bodyVel(sp)));
                if (tt == 5) JobFx.slash(sp, j, 2.2, 0.4, 1.8, 0.0, 0.22);
                if (tt == 7) JobFx.slash(sp, j, 2.2, 0.4, 1.8, 180.0, 0.22);
                if (tt % 2 == 0) SkillFx.play(level, "entity.player.attack.sweep", sp.position(), 0.75 + tt * 0.03, 0.55);
            });
        }
        // 도끼를 머리 위로 (9틱): 눈앞 머리 위 반원 · 둔탁한 바람
        RuneKit.later(windup - 3, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.slash(sp, j, 1.9, -0.75, 1.2, 180.0, 0.18);
        });
        RuneKit.later(windup, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 f = Kit.facingOf(sp);
            Vec3 ground = groundAhead(sp.position(), f, k.num("ahead", 0.6), level);
            double radius = k.num("radius", 2.5);
            JobFx.slash(sp, j, 2.3, 0.15, 2.2, 95.0, 0.24);
            JobFx.slashCard(level, V.up(V.add(sp.position(), f, 1.0), 1.3), f, 95.0, 3.2, 0.3, V.ZERO, j, JobFx.hide1p(sp));
            slamFx(sp, j, ground, radius, 1.3);
            double len = Math.max(2, RuneKit.rayLength(level, V.up(ground, 0.6), f, k.num("rift", 6)));
            rift(level, ground, f, len, JobKit.glow(j), 1.0);
            SkillFx.play(level, "entity.player.attack.sweep", ground, 0.65, 0.9);
            Set<UUID> seen = new HashSet<>();
            int breakFor = (int) k.num("breakFor", 100);
            int[] cnt = {0};
            List<LivingEntity> near = Kit.around(sp, ground, radius, 2.5);
            for (LivingEntity e : near) {
                seen.add(e.getUUID());
                JobKit.strike(sp, e, j, P, k.num("mult", 6.5), false, false, life, 0, d -> crushHit(sp, e, j, k, breakFor, cnt));
            }
            for (Kit.LineHit lh : RuneKit.lineTargets(sp, V.up(ground, 0.6), f, len, k.num("riftWidth", 1.6) / 2)) {
                LivingEntity e = lh.e();
                if (!seen.add(e.getUUID())) continue;
                JobKit.strike(sp, e, j, P, k.num("mult", 6.5), false, false, life, (int) Math.round(lh.t() / SPLIT_SPEED * 20), d -> crushHit(sp, e, j, k, breakFor, cnt));
            }
        });
        return true;
    }

    /** 투구 깨부수기에 맞음: 방어 깨짐 · 맞은 자리 · 머리 위 갈라지는 방패 (8명까지) */
    private static void crushHit(ServerPlayer sp, LivingEntity e, Jobs.Job j, Jobs.Skill k, int breakFor, int[] cnt) {
        JobTraitsMore.applyBreak(e, JobKit.isBoss(e) ? k.num("breakBoss", 0.075) : k.num("break", 0.15), breakFor);
        if (cnt[0]++ >= 8) return;
        JobFx.hit(e, j, 1.3);
        JobFx.breakMark(e, j, 0.5, 0.65, 1.0);
        if (cnt[0] == 1 && e.level() instanceof ServerLevel lv) SkillFx.play(lv, "block.chain.break", Kit.bodyPoint(e), 0.75, 0.9);
    }

    // ------------------------------------------------------------ 곰의 메치기

    /**
     * 곰의 메치기: 보는 쪽 dist 칸을 steps 틱에 내달림 (한 틱에 한 마디 · 밀려나지 않음). 길 앞 (폭 width) 에 처음 닿은 적들 앞에서 멈춰 들이받음 (ram) — 가장 가까운
     * 적을 잡음: 밀 수 있는 적이면 앞으로 날려 보내고 drag 칸 따라가 붙잡아 들어 올렸다가 (6틱) slamDelay 틱에 땅에 메침, 보스 · 허수아비는 그 자리에서 메침 피해만.
     * 메친 자리 둘레 slamRadius 칸 (slam — 땅 울림). 적이 없으면 끝까지 가서 slamDelay 틱 뒤 앞 땅을 내려찍음. 묶였거나 앞이 막혀 한 칸도 못 가면 쓰지 않음
     */
    static boolean toss(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
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
        int slamDelay = Math.max(10, (int) k.num("slamDelay", 10));
        RpgCombat.setSteadfast(sp, steps + slamDelay + 12);
        Kit.noFall(sp, steps + slamDelay + 30);
        RuneKit.shadeClone(sp, level, start, f, 1, SHADE, JobKit.glow(j), Opt.of("life", 0.45, "fade", 0.6, "alpha", 0.8));
        SkillFx.sparks(level, V.up(start, 0.2), DUST, 8, 3);
        SkillFx.play(level, "entity.polar_bear.warning", start, 0.9, 0.8);
        SkillFx.play(level, "item.trident.riptide_1", start, 0.75, 0.8);
        boolean[] done = {false};
        for (int s = 0; s < marks.size(); s++) {
            Vec3 to = marks.get(s);
            int ss = s;
            RuneKit.later(s, () -> {
                if (done[0] || !RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                Vec3 prev = sp.position();
                List<LivingEntity> hit = Kit.around(sp, V.add(to, f, 0.6), half + 0.6, 2.0);
                if (!hit.isEmpty()) {
                    done[0] = true;
                    double sPrev = V.dot(V.sub(prev, start), f);
                    double near = 1e9;
                    LivingEntity grab = null;
                    for (LivingEntity e : hit) {
                        double sE = V.dot(V.sub(e.position(), start), f) - 1.5 - Kit.bodyPad(e);
                        if (sE < near) {
                            near = sE;
                            grab = e;
                        }
                    }
                    double want = Math.max(sPrev, Math.min(len, near));
                    Kit.Spot best = null;
                    for (Kit.Spot x : spots) if (x.s() <= want + 0.05 && (best == null || x.s() > best.s())) best = x;
                    Vec3 stop = prev;
                    if (best != null && best.s() > sPrev + 0.05 && Kit.moveTo(sp, best.p())) stop = best.p();
                    JobFx.dash(d, V.up(prev, 0.9), V.up(stop, 0.9), j, 0.3, JobFx.hide1p(sp));
                    ram(sp, j, P, k.num("ram", 1.4), hit, f, life);
                    grab(sp, j, k, P, grab, f, spots, slamDelay, life);
                    return;
                }
                if (!Kit.moveTo(sp, to)) {
                    done[0] = true;
                    RuneKit.later(slamDelay, () -> endSlam(sp, j, k, P, f, null, life));
                    return;
                }
                JobFx.dash(d, V.up(prev, 0.9), V.up(to, 0.9), j, 0.3, JobFx.hide1p(sp));
                SkillFx.sparks(d, V.up(to, 0.15), DUST, 3, 2);
                JobFx.speed(sp, j, ss == marks.size() - 1 ? 0.25 : 0.1, 0.8);
                if (ss % 2 == 0) JobFx.ringFacing(d, V.up(V.add(to, f, 0.9), 1.0), 0.75, j, V.scale(f, -1), 0.18, JobFx.hide1p(sp));
                if (ss == marks.size() - 1) {
                    done[0] = true;
                    RuneKit.later(slamDelay, () -> endSlam(sp, j, k, P, f, null, life));
                }
            });
        }
        return true;
    }

    /** 들이받음: 닿은 적마다 ram · 맞은 자리 충돌 고리 · 어깨 앞 불티 · 소리 */
    private static void ram(ServerPlayer sp, Jobs.Job j, double P, double mult, List<LivingEntity> hit, Vec3 f, String life) {
        ServerLevel d = sp.level();
        int[] cnt = {0};
        for (LivingEntity e : hit) {
            JobKit.strike(sp, e, j, P, mult, false, false, life, 0, x -> {
                if (cnt[0]++ >= 8) return;
                JobFx.hit(e, j, 1.2);
                JobFx.ringFacing(e.level() instanceof ServerLevel lv ? lv : d, Kit.bodyPoint(e), 0.8 + Kit.bodyPad(e) * 0.4, j, V.scale(f, -1), 0.3, Opt.empty());
            });
        }
        SkillFx.sparks(d, V.up(V.add(sp.position(), f, 0.9), 1.1), JobKit.glow(j), 8, 4);
        JobKit.shake(sp, 6, 0.3, 0.2);
        SkillFx.play(d, "entity.goat.ram_impact", sp.position(), 0.75, 1.0);
        SkillFx.play(d, "entity.player.attack.knockback", sp.position(), 0.7, 0.9);
    }

    /**
     * 잡음: 밀 수 있는 적은 앞으로 날려 보내고 (밀쳐냄) 2틱 뒤 drag 칸을 4틱에 따라가 (잔상 · 흙먼지) 6틱에 들어 올림 (위로 띄움) → slamDelay 틱에 땅으로 내리꽂음.
     * 보스 · 허수아비 · 플레이어는 움직이지 않음 (메침 피해만). 곰의 혼이 뒤에서 함께 덮침 (3인칭)
     */
    private static void grab(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, LivingEntity e, Vec3 f, List<Kit.Spot> spots, int slamDelay, String life) {
        boolean push = e != null && JobKit.pushable(e);
        JobKit.motionLater(sp, "warlord_throw:slam", 3, life);
        if (push) {
            RuneKit.knock(e, f, 0.75, 0.22);
            Vec3 from = sp.position();
            double drag = k.num("drag", 2.5);
            double room = Kit.reachFlat(sp.level(), from, f, drag);
            Vec3 to = V.add(from, f, Math.max(0, room - 0.2));
            for (int t = 1; t <= 4; t++) {
                int tt = t;
                RuneKit.later(1 + t, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    Vec3 prev = sp.position();
                    Vec3 p = V.lerp(from, to, tt / 4.0);
                    Vec3 stand = Kit.standSpot(sp.level(), new Vec3(p.x, p.y + 0.5, p.z), 1.5);
                    if (stand != null && Kit.moveTo(sp, stand)) {
                        JobFx.dash(sp.level(), V.up(prev, 0.9), V.up(stand, 0.9), j, 0.25, JobFx.hide1p(sp));
                        SkillFx.sparks(sp.level(), V.up(stand, 0.12), DUST, 2, 1.8);
                    }
                });
            }
            RuneKit.later(6, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved() || !e.isAlive()) return;
                Kit.launch(e, 0, 0.72, 0); // 들어 올림 (띄움)
                SkillFx.play(sp.level(), "entity.polar_bear.warning", sp.position(), 1.1, 0.7);
                SkillFx.sparks(sp.level(), Kit.bodyPoint(e), JobKit.glow(j), 6, 2.5);
            });
        }
        RuneKit.later(4, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            // 곰의 혼이 뒤에서 솟아 함께 덮침 (3인칭 · 다른 사람)
            Vec3 back = V.up(V.add(sp.position(), Kit.facingOf(sp), -0.6), 1.7);
            JobFx.bear(sp.level(), back, 1.9, 0.55, 2, 4, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.85), JobTraits6.bearGlow(j),
                    JobTraits6.bearCore(j));
        });
        RuneKit.later(slamDelay, () -> endSlam(sp, j, k, P, f, push ? e : null, life));
    }

    /** 메침 (곰의 메치기 끝): 잡은 적을 땅에 내리꽂음 (아래로) · 앞 땅 · 둘레 slamRadius 칸 (slam — 잡은 적 포함) · 큰 땅 울림 */
    private static void endSlam(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 f, LivingEntity grabbed, String life) {
        if (!RuneKit.sameLife(sp, life)) return;
        ServerLevel level = sp.level();
        Vec3 at = grabbed != null && !grabbed.isRemoved() && grabbed.distanceToSqr(sp) < 25 ? grabbed.position() : V.add(sp.position(), f, 1.4);
        Vec3 ground = new Vec3(at.x, RuneKit.groundAt(level, at, sp.getY()), at.z);
        if (grabbed != null && !grabbed.isRemoved() && grabbed.isAlive()) Kit.launch(grabbed, 0, -1.4, 0);
        double r = k.num("slamRadius", 3.0);
        JobFx.slash(sp, j, 2.2, 0.15, 2.0, 95.0, 0.22);
        slamFx(sp, j, ground, r, 1.4);
        RuneKit.shakeAround(level, ground, 12, 0.35, 0.35);
        SkillFx.play(level, "item.mace.smash_ground_heavy", ground, 0.8, 1.0);
        int[] cnt = {0};
        for (LivingEntity e : Kit.around(sp, ground, r, 3.0)) {
            JobKit.strike(sp, e, j, P, k.num("slam", 2.5), false, false, life, 0, x -> {
                if (cnt[0]++ >= 8) return;
                JobFx.hit(e, j, 1.35);
                if (e.level() instanceof ServerLevel lv) JobFx.bearClaws(lv, Kit.bodyPoint(e), 0.9 + Kit.bodyPad(e) * 0.5, Kit.rand() * 30 - 15, 0.3, Opt.empty(), j);
            });
        }
    }

    // ------------------------------------------------------------ 도끼 태풍

    /**
     * 도끼 태풍: 도끼를 뻗고 오른쪽으로 돌며 걸음 (움직일 수 있음) — gap 틱마다 spins (둘레 radius 칸), 마지막 바퀴 뒤 뛰어올라 (hop 칸) slamDelay 틱에 내려찍음
     * (slam — 둘레 slamRadius 칸). 맞힌 적마다 최대 체력 × leech 흡혈 (한 번에 leechMax 명까지). 그림: 허리 높이 둥근 도끼 궤적 · 몸에서 솟는 회오리 · 흙먼지 바람 ·
     * 1인칭 눈앞 돌아가는 베기 (제 도끼 궤적만 — 환영 도끼 없음)
     */
    static boolean storm(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        List<Double> spins = SwordmasterSkills.numbers(k, "spins", List.of(1.8, 1.8, 1.8, 1.8));
        int gap = Math.max(10, (int) k.num("gap", 10));
        int n = spins.size();
        int lastSpin = (n - 1) * gap;
        int slamAt = lastSpin + Math.max(10, (int) k.num("slamDelay", 12));
        double r = k.num("radius", 3.6);
        double leech = JobUpgrades.num(sp, k, "leech", 0.01);
        int leechMax = (int) k.num("leechMax", 5);
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        SkillFx.play(sp.level(), "mob.breeze.whirl", sp.position(), 0.7, 1.0);
        SkillFx.play(sp.level(), "entity.polar_bear.warning", sp.position(), 0.8, 0.7);
        // 마지막 바퀴 뒤: 그 자리에서 뛰어올라 (2틱 뒤부터) 내려찍음 — 걷던 자리 그대로
        JobMove.run(sp, JobMove.hop(lastSpin + 2, slamAt, from -> from, k.num("hop", 2.5)));
        for (int t = 0; t <= lastSpin + 2; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 c = sp.position();
                Vec3 vel = RuneKit.bodyVel(sp);
                if (tt % 4 == 0) JobFx.ring(level, V.up(c, 1.0), r * 0.9, j, false, 0.2, Opt.of("vel", vel, "spin", (double) (tt * 37 % 360), "alpha", 0.9));
                if (tt % 8 == 0) JobFx.twister(sp, j, 2.9, 0.48, 0.7);
                if (tt % 6 == 0) JobFx.slash(sp, j, 2.0, 0.45, 1.6, (tt * 60) % 360, 0.2);
                for (int i = 0; i < 2; i++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 p = V.up(V.add(c, new Vec3(Math.cos(a), 0, Math.sin(a)), r * (0.4 + Kit.rand() * 0.6)), 0.2 + Kit.rand() * 0.9);
                    SkillFx.fx(level, "rpg:arrow_spark", p, Opt.of("dir", V.unit(new Vec3(-Math.sin(a), 0.5, Math.cos(a))), "speed", 2.5, "size", 0.13, "grow", 0.6, "life",
                            0.4, "color", glow, "alpha", 1.0));
                }
                if (tt % 4 == 2) SkillFx.sparks(level, V.up(c, 0.15), DUST, 3, 2.6);
                if (tt % 10 == 4) SkillFx.play(level, "entity.player.attack.sweep", c, 0.7 + (tt % 20) * 0.01, 0.6);
            });
        }
        int[] healedTotal = {0};
        SkillFx.every(n, gap, i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 c = sp.position();
            int[] healed = {0};
            int[] cnt = {0};
            double each = RpgHealth.maxOf(sp) * leech;
            for (LivingEntity e : Kit.around(sp, c, r, 2.5)) {
                JobKit.strike(sp, e, j, P, spins.get(i), false, false, life, 0, d -> {
                    if (healed[0] < leechMax && each > 0) {
                        healed[0]++;
                        double got = RpgHealth.healBy(sp, each);
                        if (got > 0 && healed[0] == 1) {
                            RuneKit.later(1, () -> {
                                if (!sp.isRemoved()) DamageNumbers.show(sp, each * healed[0], DamageNumbers.Style.HEAL, "", 0);
                            });
                        }
                        healedTotal[0]++;
                    }
                    if (cnt[0]++ < 6) JobFx.hit(e, j, 1.0);
                });
            }
        });
        // 뛰어오름 (마지막 바퀴 + 2): 도끼를 두 손으로 머리 위로 · 바람 소리
        RuneKit.later(lastSpin + 3, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            SkillFx.play(sp.level(), "mob.breeze.jump", sp.position(), 0.8, 0.9);
            SkillFx.sparks(sp.level(), V.up(sp.position(), 0.2), DUST, 6, 3);
        });
        RuneKit.later(slamAt - 3, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.slash(sp, j, 1.9, -0.75, 1.3, 180.0, 0.16);
        });
        // 내려찍음 (slamAt): 둘레 slamRadius 칸 (slam) · 흡혈
        RuneKit.later(slamAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            Vec3 ground = new Vec3(c.x, RuneKit.groundAt(level, c, c.y), c.z);
            double sr = k.num("slamRadius", 4.2);
            JobFx.slash(sp, j, 2.3, 0.1, 2.4, 95.0, 0.26);
            slamFx(sp, j, ground, sr, 1.5);
            JobFx.ring(level, V.up(ground, 0.08), sr, j, false, 0.45, Opt.empty());
            int[] healed = {0};
            int[] cnt = {0};
            double each = RpgHealth.maxOf(sp) * leech;
            for (LivingEntity e : Kit.around(sp, ground, sr, 3.0)) {
                JobKit.strike(sp, e, j, P, k.num("slam", 4.5), false, false, life, 0, d -> {
                    if (healed[0] < leechMax && each > 0) {
                        healed[0]++;
                        RpgHealth.healBy(sp, each);
                    }
                    if (cnt[0]++ < 8) JobFx.hit(e, j, 1.3);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 곰의 포효

    /**
     * 곰의 포효 (태세 칸 — 태세가 아니라 곰의 화신을 부름): 가슴을 치며 (3틱 — 가슴 앞 터짐) 포효 (6틱) — 분노 가득 · 곰의 화신 바로 (dur — JobTraits6), undying 틱 쓰러지지
     * 않음, dur 동안 위기 회복 heals 번. 그림: 뒤에서 솟는 큰 곰의 혼 (포효 장면) · 발밑에서 퍼지는 고리 셋 · 1인칭 눈앞 고리 · 곰 울음
     */
    static boolean roar(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = JobUpgrades.ticks(sp, k, "dur", (int) k.num("dur", 160));
        String life = RuneKit.lifeToken(sp);
        ServerLevel level = sp.level();
        // 가슴을 침 (3틱): 가슴 앞 작은 터짐 · 둔탁한 소리 (두 번)
        for (int i = 0; i < 2; i++) {
            int ii = i;
            RuneKit.later(1 + i * 2, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 chest = V.add(V.up(sp.position(), 1.2), Kit.facingOf(sp), 0.4);
                JobFx.burst(sp.level(), chest, 0.35 + 0.1 * ii, j, 0.25, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
                SkillFx.play(sp.level(), "entity.player.attack.knockback", chest, 0.6, 0.8);
            });
        }
        RuneKit.later(5, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            JobTraits6.roarStart(sp, j, k, dur);
            Vec3 back = V.up(V.add(sp.position(), Kit.facingOf(sp), -0.6), 1.8);
            JobFx.bear(lv, back, 2.5, 1.1, 0, JobFx.BEAR - 1, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.95), JobTraits6.bearGlow(j),
                    JobTraits6.bearCore(j));
            for (int i = 0; i < 3; i++) {
                int ii = i;
                RuneKit.later(i * 4, () -> {
                    if (RuneKit.sameLife(sp, life)) JobFx.ring(sp.level(), V.up(sp.position(), 0.06), 1.8 + ii * 1.1, j, ii % 2 == 1, 0.45, Opt.of("alpha", 0.9 - ii * 0.15));
                });
            }
            RuneKit.shakeAround(lv, sp.position(), 10, 0.3, 0.4);
            SkillFx.play(lv, "entity.polar_bear.warning", sp.position(), 0.6, 1.5);
        });
        SkillFx.play(level, "entity.polar_bear.ambient", sp.position(), 0.7, 0.8);
        return true;
    }

    // ------------------------------------------------------------ 뵈드바르의 앞발

    /**
     * 뵈드바르의 앞발: 붉은 하늘 → leapTicks 틱마다 (leaps 번) 가장 가까운 적 무리 곁으로 높이 height 의 포물선으로 뛰어 내려찍음 (leap — 둘레 landRadius 칸) → 마지막 착지에서
     * rise 틱에 riseHeight 칸 솟아올라 뒤에서 거대한 곰의 혼이 일어서고, finalDelay 틱 (마지막 착지부터) 에 앞발이 내리쳐 내려앉음 — 둘레 finalRadius 칸 (final — 큰 숫자 ·
     * 밀쳐냄) · 앞으로 뻗는 발톱 세 갈래 균열
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int leaps = Math.max(1, (int) k.num("leaps", 4));
        int leapTicks = Math.max(10, (int) k.num("leapTicks", 12));
        int finalDelay = Math.max(14, (int) k.num("finalDelay", 20));
        int lastLand = leaps * leapTicks;
        int finalAt = lastLand + finalDelay;
        int rise = Math.max(4, (int) k.num("rise", 10));
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        double[] glow = JobKit.glow(j);
        Ult.sky(level0, c0, "rpg:ult_meteor", finalAt + 30);
        JobFx.aura(sp, j, 3.0, 0.8, 1.0);
        JobFx.ring(level0, V.up(c0, 0.06), 3.0, j, false, 0.5, Opt.empty());
        JobFx.eyeRing(sp, j, 2.4, 1.1, 0.5);
        SkillFx.sparks(level0, V.up(c0, 1.0), glow, 12, 4);
        SkillFx.play(level0, "entity.polar_bear.warning", c0, 0.6, 1.2);
        SkillFx.play(level0, "mob.enderdragon.growl", c0, 1.3, 0.45);
        Kit.noFall(sp, finalAt + 40);
        RpgCombat.setSteadfast(sp, finalAt + 10);
        Vec3[] last = {c0};
        for (int i = 0; i < leaps; i++) {
            int ii = i;
            RuneKit.later(i * leapTicks, () -> {
                if (RuneKit.sameLife(sp, life)) leap(sp, j, k, P, ii, leapTicks, last, life);
            });
        }
        // 마지막 착지 뒤: 솟아오름 (rise 틱) → 공중에서 버팀 → 앞발과 함께 내려앉음 (4틱 — finalAt 에 땅)
        RuneKit.later(lastLand, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            double ground = sp.getY();
            int hold = Math.max(rise + 1, finalDelay - 4);
            JobMove.run(sp, JobMove.to(0, rise, JobMove.up(sp, k.num("riseHeight", 6.0)), JobMove.OUT), JobMove.hold(rise, hold), JobMove.to(hold, finalDelay,
                    JobMove.down(sp, ground), JobMove.IN));
            JobKit.motion(sp, "warlord_ult:final");
            SkillFx.play(sp.level(), "mob.breeze.jump", sp.position(), 0.6, 1.0);
            SkillFx.sparks(sp.level(), V.up(sp.position(), 0.2), DUST, 10, 3.5);
        });
        // 곰의 혼이 일어섬 (마지막 착지 + 2): 몸 뒤에서 거대한 곰 (3인칭 · 다른 사람) — 1인칭은 화면 위쪽 빛
        RuneKit.later(lastLand + 2, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Vec3 f = Kit.facingOf(sp);
            Vec3 at = V.up(V.add(last[0], f, -1.6), 4.2);
            JobFx.bear(d, at, 4.4, (finalDelay + 6) / 20.0, 0, JobFx.BEAR - 1, false, JobFx.hide1p(sp).with("alpha", 0.92), JobTraits6.bearGlow(j), JobTraits6.bearCore(j));
            SkillFx.play(d, "entity.polar_bear.warning", sp.position(), 0.5, 1.5);
            SkillFx.play(d, "mob.ravager.roar", sp.position(), 0.6, 1.0);
        });
        // 앞발 (finalAt − 7 → finalAt): 위에서 내리치는 거대한 곰 앞발 — 앞 3칸 땅으로 (1인칭 · 3인칭 모두)
        RuneKit.later(finalAt - 7, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Vec3 f = Kit.facingOf(sp);
            Vec3 hit = V.add(last[0], f, 3.0);
            Vec3 from = V.up(hit, 7.0);
            double speed = 7.0 / (7 / 20.0);
            JobFx.paw(d, from, 2.6, 7 / 20.0 + 0.05, Opt.of("dir", V.DOWN, "speed", speed, "alpha", 0.95), JobTraits6.bearGlow(j), JobTraits6.bearCore(j));
            SkillFx.play(d, "item.trident.riptide_3", sp.position(), 0.6, 0.9);
        });
        RuneKit.later(finalAt, () -> {
            if (RuneKit.sameLife(sp, life)) pawBlast(sp, j, k, P, life);
        });
        return true;
    }

    /** 착지 자리 (spot) 와 그때 바라볼 곳 (focus — 노린 적의 몸, 없으면 앞) */
    private record Landing(Vec3 spot, Vec3 focus) {}

    /** 다음 착지 자리: range 칸 안의 적 무리 곁 (노린 적에서 2.4칸 — 같은 적이면 둘레를 돌아가며) — 적이 없으면 앞으로 5칸 */
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

    /** 뛰어 내려찍기 한 번: leapTicks 틱 동안 포물선 (한 틱에 한 마디 — 노린 적 쪽으로 시선이 돌아감) → 착지 둘레 landRadius 칸 (leap) · 곰 발톱 세 줄 */
    private static void leap(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, int i, int leapTicks, Vec3[] last, String life) {
        if (i > 0) JobKit.motion(sp, "warlord_ult:leap");
        Vec3 from = sp.position();
        Landing landing = landingFor(sp, k, i);
        Vec3 to = landing.spot();
        last[0] = to;
        double height = k.num("height", 4.0);
        float yaw0 = sp.getYRot();
        float pitch0 = sp.getXRot();
        SkillFx.play(sp.level(), "mob.breeze.jump", from, 0.75, 0.9);
        SkillFx.sparks(sp.level(), V.up(from, 0.2), DUST, 6, 3);
        for (int s = 1; s <= leapTicks; s++) {
            int ss = s;
            RuneKit.later(s, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double t = (double) ss / leapTicks;
                Vec3 p = V.up(V.lerp(from, to, t), height * 4 * t * (1 - t));
                Vec3 prev = sp.position();
                float[] want = Kit.lookRotation(V.up(p, sp.getEyeHeight()), landing.focus());
                double q = Math.min(1, t * 1.8);
                q = q * q * (3 - 2 * q);
                float yaw = (float) (yaw0 + wrapDeg(want[0] - yaw0) * q);
                float pitch = (float) (pitch0 + (Math.max(-30F, Math.min(55F, want[1])) - pitch0) * q);
                if (!Kit.moveTo(sp, p, yaw, pitch)) Kit.moveTo(sp, p);
                ServerLevel d = sp.level();
                if (t > 0.45 && ss < leapTicks) {
                    Vec3 v = V.sub(p, prev);
                    if (V.len(v) > 0.05) JobFx.comet(d, V.up(p, 1.0), V.unit(v), 1.3, 0, 0.12, j, JobFx.hide1p(sp));
                    JobFx.speed(sp, j, 0.1, 0.75);
                }
                if (ss == leapTicks) land(sp, j, k, P, to, life);
            });
        }
    }

    /** 착지: 갈라지는 땅 · 터짐 · 앞에 곰 발톱 세 줄 · 둘레 landRadius 칸 (leap) */
    private static void land(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 at, String life) {
        double r = k.num("landRadius", 3.5);
        ServerLevel level = sp.level();
        JobFx.slash(sp, j, 2.2, 0.35, 1.7, 90.0, 0.2);
        slamFx(sp, j, at, r, 1.1);
        JobFx.bearClaws(level, V.up(V.add(at, Kit.facingOf(sp), 1.4), 1.0), 1.3, Kit.rand() * 30 - 15, 0.3, JobFx.hide1p(sp), j);
        for (LivingEntity e : Kit.around(sp, at, r, 2.5)) {
            JobKit.strike(sp, e, j, P, k.num("leap", 6), false, false, life, 0, x -> {
                if (RuneKit.fxQuota("jobLeap", 6)) JobFx.hit(e, j, 1.2);
            });
        }
    }

    /**
     * 앞발이 내리침 (내려앉는 틱): 땅의 큰 갈라짐 · 둥근 베기 둘 · 겹쳐 터지는 기운 · 솟는 빛기둥 · 번쩍임 · 앞으로 뻗는 발톱 세 갈래 균열 · 맞은 자리 큰 곰 발톱 — 둘레
     * finalRadius 칸 (final — 큰 숫자 · 밀쳐냄)
     */
    private static void pawBlast(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 at = new Vec3(c.x, RuneKit.groundAt(level, c, c.y), c.z);
        double r = k.num("finalRadius", 7);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        Vec3 impact = V.add(at, f, 2.5);
        JobFx.smash(level, V.up(impact, 0.05), r, j, 0.9, Opt.empty());
        JobFx.ring(level, V.up(at, 0.08), r, j, false, 0.5, Opt.empty());
        RuneKit.later(3, () -> JobFx.ring(level, V.up(at, 0.1), r * 0.6, j, true, 0.45, Opt.empty()));
        for (int i = 0; i < 3; i++) {
            int ii = i;
            RuneKit.later(i * 2, () -> JobFx.burst(level, V.up(impact, 0.8 + ii * 0.5), 1.8 + ii * 0.6, j, 0.4, JobFx.hide1p(sp)));
        }
        Vec3 look = sp.getViewVector(1F);
        JobFx.bearClaws(level, V.add(V.add(sp.getEyePosition(), look, 2.8), JobFx.screenUp(look), -0.3), 2.2, 10, 0.45, JobFx.only1p(sp), j);
        JobFx.bearClaws(level, V.up(impact, 1.4), 2.6, 10, 0.45, JobFx.hide1p(sp), j);
        Vec3 rr = V.rightOf(f);
        for (int s = -1; s <= 1; s++) {
            Vec3 d = V.unit(V.add(f, rr, s * 0.22));
            Vec3 g = V.add(impact, rr, s * 0.9);
            double len = Math.max(2, RuneKit.rayLength(level, V.up(g, 0.6), d, 10));
            rift(level, g, d, len, glow, 0.9);
        }
        RuneKit.godRays(sp, level, impact, r * 0.6, 6, glow, core, Opt.of("h", 12.0, "life", 0.9, "alpha", 0.8, "skipNear", true));
        RuneKit.rockBurst(level, V.up(impact, 0.3), 14, 6.0, 0.22);
        SkillFx.sparks(level, V.up(impact, 0.5), glow, 20, 7);
        Ult.flash(level, at, glow, Opt.empty(), sp);
        RuneKit.shakeAround(level, at, 16, 0.5, 0.45);
        SkillFx.play(level, "random.explode", at, 0.7, 1.0);
        SkillFx.play(level, "item.mace.smash_ground_heavy", at, 0.6, 1.2);
        SkillFx.play(level, "entity.polar_bear.warning", at, 0.5, 1.4);
        for (LivingEntity e : Kit.around(sp, at, r, 3.0)) {
            JobKit.strike(sp, e, j, P, k.num("final", 28), false, true, life, 0, x -> {
                if (JobKit.pushable(e)) RuneKit.knock(e, V.sub(e.position(), at), k.num("knock", 0.8), 0.55);
                if (RuneKit.fxQuota("jobPawFinal", 6)) JobFx.hit(e, j, 1.6);
            });
        }
    }
}
