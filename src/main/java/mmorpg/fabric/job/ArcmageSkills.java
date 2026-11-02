package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.Notify;
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
 * 0.51.0 2차 직업 — 매지션 각성 오드레리르(아크메이지) 스킬 (jobs.json advance.arcmage — 파랑 · 금빛 · 밤하늘 자주, 특성 「주문 연쇄」 최대 5 · 별자리 — JobTraits8):
 * 1차 매지션의 컨셉 (마탄 · 서리 잔상 · 떠도는 별 · 갈드르 · 갈드라보크) 그대로 새 동작 — 별무리 마탄 (좌우로 미끄러지며 다섯 · 맞은 적 사이로 튀는 별빛) · 서리 별 걸음
 * (순간 이동 두 번 · 두 잔상이 1초 뒤 얼어 터짐) · 쌍둥이 별 (서로 도는 별 둘 · 잇는 빛줄기도 침) · 꿀술의 노래 (1칸 떠올라 노래 — 스킬마다 별똥) · 별의 노래 (3칸 떠올라
 * 마도서 — 불 · 얼음 · 번개에 이어 넷째 별의 비 · 별자리가 모여 대폭발). 모두 마법 피해. 연출 = 2D 그림 (JobFx — 별 rfx_jstarnode · 별똥 rfx_jfallstar 와 1차의 마탄 ·
 * 거울 · 주문 그림, 마도서는 판 (JobFx3d grimoire → card_tome)).
 */
final class ArcmageSkills {
    private ArcmageSkills() {}

    private static final double[] NIGHT = JobTraits8.NIGHT;
    private static final double[] GOLD = JobTraits8.GOLD;
    /** 서리 잔상 (얼음빛 · 흰 심지) */
    private static final double[] FROST = {0.55, 0.85, 1.0};
    private static final double[] FROST_CORE = {0.93, 0.98, 1.0};
    private static final double[] SHADE = {0.16, 0.24, 0.55};

    static void register() {
        JobSystem.register("arcmage_bolts", ArcmageSkills::bolts);
        JobSystem.register("arcmage_phase", ArcmageSkills::phase);
        JobSystem.register("arcmage_orb", ArcmageSkills::twins);
        JobSystem.register("arcmage_song", ArcmageSkills::song);
        JobSystem.register("arcmage_ult", ArcmageSkills::ult);
    }

    static Vec3 wandTip(ServerPlayer sp) {
        return MageSkills.wandTip(sp);
    }

    // ------------------------------------------------------------ 별무리 마탄

    /** 미끄러지는 첫 쪽 (사람마다 번갈아) */
    private static final Map<UUID, Boolean> SIDE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 별무리 마탄: 겨눈 적 (range 칸 · 없으면 쓰지 않음) 에게 every 틱마다 별빛 마탄 bolts 발 — 처음 반은 한쪽으로 slide 칸, 나머지는 반대쪽으로 두 배 미끄러지며 (지그재그 —
     * 쓸 때마다 첫 쪽이 바뀜). 피해는 첫 마탄이 닿을 때 한 번 (bolt × bolts — 숫자 bolts 줄). 마탄이 닿을 때마다 별빛이 둘레 bounceRange 칸의 다른 적 (가까운 순 bounceFoes
     * 명 — 돌아가며) 에게 튐 (bounce — 같은 적은 한 번에 · 숫자 여러 줄)
     */
    static boolean bolts(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        LivingEntity target = Kit.pickTarget(sp, k.num("range", 20), 12);
        if (target == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다", 30);
            return false;
        }
        int n = Math.max(1, (int) k.num("bolts", 5));
        int every = Math.max(1, (int) k.num("every", 2));
        double mult = k.num("bolt", 1.4) * n;
        String life = RuneKit.lifeToken(sp);
        double speed = 28;
        boolean right = SIDE.merge(sp.getUUID(), true, (a, b) -> !a);
        double slide = k.num("slide", 1.6);
        int half = Math.max(2, n * every / 2);
        JobMove.run(sp, JobMove.to(0, half, JobMove.side(sp, right ? slide : -slide), JobMove.OUT),
                JobMove.to(half, n * every + 2, JobMove.side(sp, right ? -2 * slide : 2 * slide), JobMove.INOUT));
        // 튀는 별빛을 받을 적: 과녁 둘레 bounceRange 칸의 다른 적 (가까운 순 bounceFoes 명)
        List<LivingEntity> others = new ArrayList<>();
        for (LivingEntity e : RuneKit.nearestTo(Kit.around(sp, target.position(), k.num("bounceRange", 5), 3), target.position())) {
            if (e != target && others.size() < (int) k.num("bounceFoes", 2)) others.add(e);
        }
        int t0 = Math.max(1, (int) Math.round(V.len(V.sub(Kit.bodyPoint(target), wandTip(sp))) / speed * 20));
        Map<UUID, Integer> bounces = new LinkedHashMap<>();
        Map<UUID, Integer> firstAt = new LinkedHashMap<>();
        for (int i = 0; i < n && !others.isEmpty(); i++) {
            LivingEntity o = others.get(i % others.size());
            bounces.merge(o.getUUID(), 1, Integer::sum);
            firstAt.putIfAbsent(o.getUUID(), i);
        }
        for (LivingEntity o : others) {
            int c = bounces.getOrDefault(o.getUUID(), 0);
            if (c <= 0) continue;
            JobKit.strikeSplit(sp, o, j, P, k.num("bounce", 0.7) * c, c, every * others.size(), true, false, life, firstAt.get(o.getUUID()) * every + t0 + 1, d -> {
                if (RuneKit.fxQuota("jobStarBounce", 6)) JobFx.hit(o, j, 0.8);
            });
        }
        for (int i = 0; i < n; i++) {
            int ii = i;
            RuneKit.later(i * every, () -> {
                if (!RuneKit.sameLife(sp, life) || target.isRemoved() || !target.isAlive()) return;
                ServerLevel level = sp.level();
                Vec3 from = wandTip(sp);
                Vec3 to = Kit.bodyPoint(target);
                Vec3 side = V.rightOf(V.flat(V.sub(to, from)));
                Vec3 aim = V.add(V.up(to, (ii % 3 - 1) * 0.25), side, ((ii * 37) % 5 - 2) * 0.12);
                int t = JobFx.bolt(level, from, aim, j, speed, 0.42, Opt.empty());
                JobFx.starNode(level, from, 0.16, 0, 2, 0.15, false, Opt.of("vel", RuneKit.bodyVel(sp)), NIGHT, GOLD);
                SkillFx.play(level, "mob.shulker.shoot", from, 1.6 + ii * 0.08, 0.5);
                if (ii == 0) JobKit.strikeSplit(sp, target, j, P, mult, n, every, true, false, life, t, d -> {});
                RuneKit.later(t, () -> {
                    if (target.isRemoved() || !(target.level() instanceof ServerLevel lv)) return;
                    Vec3 b = Kit.bodyPoint(target);
                    JobFx.starNode(lv, b, 0.36 + Kit.bodyPad(target) * 0.25, 6, 2, 0.22, false, Opt.empty(), NIGHT, GOLD);
                    SkillFx.play(lv, "mob.shulker.bullet.hit", b, 1.5, 0.5);
                    // 별빛이 튐: 과녁 → 다른 적 (돌아가며) — 금빛 줄 · 그 적 몸의 작은 별
                    if (!others.isEmpty()) {
                        LivingEntity o = others.get(ii % others.size());
                        if (!o.isRemoved() && o.isAlive()) {
                            Vec3 ob = Kit.bodyPoint(o);
                            RuneKit.streak(lv, b, ob, GOLD, 0.08, 0.25, 0.95, 0);
                            RuneKit.streak(lv, b, ob, NIGHT, 0.22, 0.2, 0.5, 0);
                            RuneKit.later(1, () -> JobFx.starNode(lv, ob, 0.3, 6, 2, 0.2, false, Opt.empty(), NIGHT, GOLD));
                            SkillFx.play(lv, "block.amethyst_block.chime", ob, 1.8, 0.5);
                        }
                    }
                });
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 서리 별 걸음

    /** 뒤 (또는 비스듬히) dist 칸으로 순간 이동 (벽 앞까지 · 땅 높이) — 간 자리 (못 가면 null) */
    private static Vec3 blink(ServerPlayer sp, Vec3 dir, double max) {
        ServerLevel level = sp.level();
        Vec3 start = sp.position();
        double dist = RuneKit.dashDistance(sp, dir, max, 1.1, 1.8);
        if (dist <= 0.4) return null;
        Vec3 b = V.add(start, dir, dist);
        Vec3 at = new Vec3(b.x, RuneKit.groundAt(level, b, start.y), b.z);
        if (Kit.moveTo(sp, at)) return at;
        List<Kit.Spot> spots = Kit.dashSpots(level, start, dir, dist);
        for (int i = spots.size() - 1; i >= 0; i--) if (Kit.moveTo(sp, spots.get(i).p())) return spots.get(i).p();
        return null;
    }

    /** 서리 별 잔상: 얼음빛 그림자 (life 초) · 가슴의 별 · 발밑 서리 고리 */
    private static void afterimage(ServerPlayer sp, ServerLevel level, Vec3 feet, Vec3 face, double radius, int ticks, Jobs.Job j) {
        RuneKit.shadeClone(sp, level, feet, face, 0, SHADE, FROST, Opt.of("life", ticks / 20.0 + 0.05, "fade", 0.15, "alpha", 0.9));
        JobFx.starNode(level, V.up(feet, 1.2), 0.32, 2, 4, ticks / 20.0, true, Opt.of("fade", 0.1), FROST, GOLD);
        JobFx.ring(level, V.up(feet, 0.06), radius * 0.85, j, true, ticks / 20.0 + 0.1, Opt.of("alpha", 0.8));
        SkillFx.play(level, "block.amethyst_block.resonate", feet, 1.5, 0.6);
    }

    /** 잔상이 얼어 터짐: 얼음 조각 · 서리 별 번쩍 · 고리 → 둘레 radius 칸 (mult · freeze 틱 빙결 — 보스는 둔화) */
    private static void frostBlast(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 feet, double radius, double mult, String life) {
        ServerLevel d = sp.level();
        Vec3 c = V.up(feet, 1.0);
        JobFx.shatter(d, c, 1.4, FROST, FROST_CORE, 0.5, Opt.empty());
        JobFx.starNode(d, c, 0.7, 6, 2, 0.3, false, Opt.empty(), FROST, GOLD);
        JobFx.burst(d, c, radius * 0.5, j, 0.34, Opt.empty());
        JobFx.ring(d, V.up(feet, 0.06), radius, j, false, 0.35, Opt.empty());
        SkillFx.sparks(d, c, FROST_CORE, 10, 5);
        SkillFx.play(d, "block.glass.break", c, 0.9, 1.0);
        SkillFx.play(d, "random.explode", c, 1.7, 0.45);
        int freeze = (int) k.num("freeze", 10);
        for (LivingEntity e : Kit.around(sp, feet, radius, 2.5)) {
            JobKit.strike(sp, e, j, P, mult, true, false, life, 0, x -> {
                if (JobKit.isBoss(e)) RuneKit.effect(e, "slowness", freeze * 3, 1);
                else RuneKit.stun(e, freeze, FROST_CORE, RuneKit.fxQuota("jobFreeze", 6));
                if (RuneKit.fxQuota("jobFrost", 6)) JobFx.frost(e, FROST, FROST_CORE, freeze + 8);
            });
        }
    }

    /**
     * 서리 별 걸음: 뒤로 dist 칸 순간 이동 → second 틱에 비스듬히 (뒤 + 옆 — 쓸 때마다 번갈아) dist2 칸 한 번 더 (막히면 그 자리). 떠난 두 자리에 서리 별 잔상 — 각각 delay 틱 뒤
     * 얼어 터짐 (첫 자리 blast · 둘째 자리 blast2 — 둘레 radius 칸 · freeze 틱 빙결). 묶였거나 뒤가 막혀 첫 걸음을 못 가면 쓰지 않음
     */
    static boolean phase(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 back = V.scale(f, -1);
        Vec3 mid = blink(sp, back, k.num("dist", 3.5));
        if (mid == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 뒤가 막혔습니다", 30);
            return false;
        }
        int delay = (int) k.num("delay", 20);
        int second = (int) k.num("second", 10);
        double radius = k.num("radius", 3);
        String life = RuneKit.lifeToken(sp);
        boolean right = SIDE.merge(sp.getUUID(), true, (a, b) -> !a);
        Kit.noFall(sp, second + 30);
        afterimage(sp, level, start, f, radius, delay, j);
        RuneKit.streak(level, V.up(start, 1.0), V.up(mid, 1.0), FROST, 0.18, 0.3, 0.8, 0);
        JobFx.speed(sp, j, 0.3, 0.85);
        JobFx.starNode(level, V.up(mid, 1.0), 0.4, 0, 2, 0.2, false, JobFx.hide1p(sp), FROST, GOLD);
        SkillFx.play(level, "mob.endermen.portal", start, 1.7, 0.8);
        // 둘째 걸음 (second 틱 — 비스듬히 뒤 · 옆): 그 자리에 둘째 잔상 (못 가도 남김)
        RuneKit.later(second, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Vec3 b0 = sp.position();
            Vec3 f2 = Kit.facingOf(sp);
            Vec3 dir = V.unit(V.add(V.scale(f2, -1), V.rightOf(f2), right ? 0.8 : -0.8));
            Vec3 b1 = Status.isRooted(sp) ? null : blink(sp, dir, k.num("dist2", 3));
            afterimage(sp, d, b0, f2, radius, delay, j);
            if (b1 != null) {
                RuneKit.streak(d, V.up(b0, 1.0), V.up(b1, 1.0), FROST, 0.18, 0.3, 0.8, 0);
                JobFx.speed(sp, j, 0.25, 0.8);
                SkillFx.play(d, "mob.endermen.portal", b0, 1.9, 0.7);
            }
            RuneKit.later(delay, () -> {
                if (RuneKit.sameLife(sp, life)) frostBlast(sp, j, k, P, b0, radius, k.num("blast2", 1.4), life);
            });
        });
        RuneKit.later(delay, () -> {
            if (RuneKit.sameLife(sp, life)) frostBlast(sp, j, k, P, start, radius, k.num("blast", 2.8), life);
        });
        return true;
    }

    // ------------------------------------------------------------ 쌍둥이 별

    /**
     * 쌍둥이 별: 완드 끝에서 별 둘이 나와 서로 돌며 (반지름 orbit · spin 바퀴/초 — 가로로) 겨눈 적 (없으면 조준한 곳) 을 천천히 쫓음 (speed 칸/초 · 모두 range 칸까지) — gap 틱마다
     * (pulses 번) 별 둘레 radius 칸 · 두 별을 잇는 빛줄기 (폭 width) 에 닿은 적에게 별빛 (pulse · 둔화) → dur 틱에 두 별이 가운데로 부딪쳐 터짐 (burst — 둘레 burstRadius 칸)
     */
    static boolean twins(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        LivingEntity target = Kit.pickTarget(sp, 20, 12);
        Vec3 aim = target == null ? V.up(Kit.aimPoint(sp, k.num("range", 12)), 1.2) : null;
        int pulses = (int) k.num("pulses", 6);
        int gap = (int) k.num("gap", 10);
        int dur = (int) k.num("dur", 70);
        double radius = k.num("radius", 2.5);
        double orbit = k.num("orbit", 1.6);
        double width = k.num("width", 1.0);
        double speed = k.num("speed", 3) / 20.0;
        double range = k.num("range", 12);
        double turn = k.num("spin", 1.0) * Math.PI * 2 / 20.0;
        Vec3[] pos = {wandTip(sp)};
        double[] travelled = {0};
        double[] ang = {Kit.rand() * Math.PI * 2};
        Vec3[][] pair = {{pos[0], pos[0]}};
        String life = RuneKit.lifeToken(sp);
        SkillFx.play(sp.level(), "beacon.activate", pos[0], 1.9, 0.6);
        SkillFx.play(sp.level(), "mob.evocation_illager.cast_spell", pos[0], 1.4, 0.6);
        JobFx.starNode(sp.level(), pos[0], 0.4, 0, 2, 0.25, false, Opt.of("vel", RuneKit.bodyVel(sp)), NIGHT, GOLD);
        for (int t = 1; t <= dur; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 goal = target != null && !target.isRemoved() && target.isAlive() && target.level() == level ? Kit.bodyPoint(target) : aim != null ? aim : pos[0];
                Vec3 d = V.sub(goal, pos[0]);
                double len = V.len(d);
                double step = Math.min(Math.max(0, len - 0.2), Math.min(speed * (tt < 6 ? 3 : 1), range - travelled[0]));
                if (step > 0) {
                    pos[0] = V.add(pos[0], V.scale(d, 1 / Math.max(1e-6, len)), step);
                    travelled[0] += step;
                }
                // 둘레를 도는 두 별 (처음 6틱은 반지름이 자라남 — 완드 끝에서 갈라져 나옴)
                ang[0] += turn;
                double r = orbit * Math.min(1, tt / 6.0);
                Vec3 u = new Vec3(Math.cos(ang[0]), 0, Math.sin(ang[0]));
                Vec3 a = V.add(pos[0], u, r);
                Vec3 b = V.add(pos[0], u, -r);
                pair[0] = new Vec3[] {a, b};
                // 영상 1 뒤 0.36 → 0.62 · 빛줄기 굵게 (과녁 사이의 작은 점으로만 보였음)
                JobFx.starNode(level, a, 0.62, 2, 4, 0.12, true, Opt.of("fade", 0.4, "phase", (double) (tt % 4)), NIGHT, GOLD);
                JobFx.starNode(level, b, 0.62, 2, 4, 0.12, true, Opt.of("fade", 0.4, "phase", (double) ((tt + 2) % 4)), NIGHT, GOLD);
                if (tt % 2 == 0) {
                    RuneKit.streak(level, a, b, GOLD, 0.11, 0.14, 0.9, 0);
                    RuneKit.streak(level, a, b, NIGHT, 0.32, 0.14, 0.45, 0);
                }
                if (tt % 6 == 0) {
                    SkillFx.fx(level, "rpg:arrow_spark", a, Opt.of("dir", V.unit(new Vec3(Kit.rand() - 0.5, 0.6, Kit.rand() - 0.5)), "speed", 1.5, "size", 0.12, "grow",
                            0.6, "life", 0.35, "color", GOLD, "alpha", 1.0));
                }
            });
        }
        for (int p = 1; p <= pulses; p++) {
            RuneKit.later(p * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 a = pair[0][0];
                Vec3 b = pair[0][1];
                Map<UUID, LivingEntity> hit = new LinkedHashMap<>();
                for (Vec3 s : new Vec3[] {a, b}) for (LivingEntity e : Kit.visibleFrom(sp, s, Kit.around(sp, s, radius, 3))) hit.put(e.getUUID(), e);
                for (LivingEntity e : Kit.around(sp, pos[0], orbit + width, 3)) {
                    if (JobTraits8.segDist(Kit.bodyPoint(e), a, b) <= width + Kit.bodyPad(e)) hit.put(e.getUUID(), e);
                }
                int[] cnt = {0};
                for (LivingEntity e : hit.values()) {
                    Vec3 from = V.len(V.sub(Kit.bodyPoint(e), a)) < V.len(V.sub(Kit.bodyPoint(e), b)) ? a : b;
                    if (cnt[0]++ < 6) RuneKit.zap(level, from, Kit.bodyPoint(e), GOLD, RuneKit.WHITE, 0.12, 0.22);
                    JobKit.strike(sp, e, j, P, k.num("pulse", 1.4), true, false, life, 0, x -> {
                        if (RuneKit.fxQuota("jobTwins", 6)) JobFx.hit(e, j, 0.8);
                    });
                    if (k.num("slow", 0) > 0) RuneKit.effect(e, "slowness", (int) k.num("slow", 30), 1);
                }
                JobFx.starNode(level, a, 0.85, 6, 1, 0.15, false, Opt.empty(), NIGHT, GOLD);
                JobFx.starNode(level, b, 0.85, 6, 1, 0.15, false, Opt.empty(), NIGHT, GOLD);
                SkillFx.play(level, "block.amethyst_block.chime", pos[0], 1.6, 0.7);
            });
        }
        RuneKit.later(dur, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 c = pos[0];
            double br = k.num("burstRadius", 3.5);
            // 두 별이 가운데로 부딪침 (금빛 줄 둘) → 큰 별 번쩍 · 터짐 · 고리
            RuneKit.streak(level, pair[0][0], c, GOLD, 0.12, 0.15, 1.0, 0);
            RuneKit.streak(level, pair[0][1], c, GOLD, 0.12, 0.15, 1.0, 0);
            JobFx.starNode(level, c, 1.2, 6, 2, 0.4, false, Opt.empty(), NIGHT, GOLD);
            JobFx.burst(level, c, br * 0.6, j, 0.42, Opt.empty());
            JobFx.ringFacing(level, c, br * 0.7, j, V.unit(V.sub(sp.getEyePosition(), c)), 0.4, Opt.empty());
            JobFx.ring(level, new Vec3(c.x, RuneKit.groundAt(level, c, c.y - 1.5) + 0.06, c.z), br, j, false, 0.4, Opt.empty());
            SkillFx.sparks(level, c, GOLD, 16, 6);
            SkillFx.play(level, "random.explode", c, 1.3, 0.8);
            SkillFx.play(level, "firework.blast", c, 1.1, 1.0);
            for (LivingEntity e : Kit.around(sp, c, br, 3)) {
                JobKit.strike(sp, e, j, P, k.num("burst", 4.2), true, false, life, 0, x -> {
                    RuneKit.knock(e, V.sub(e.position(), c), 0.35, 0.3);
                    if (RuneKit.fxQuota("jobTwinsBurst", 6)) JobFx.hit(e, j, 1.2);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 꿀술의 노래

    /**
     * 꿀술의 노래: dur 틱 태세 — 그동안 직업 스킬을 쓸 때마다 그 자리에 별똥 (JobTraits8.meteor — fall 틱 뒤 · 위력 × meteor · 둘레 meteorRadius 칸). 쓸 때 lift 칸 떠올라
     * (0.5초) 1초 동안 노래한 뒤 내려앉음 — 꿀술의 금빛 소용돌이 · 머리 위로 떠오르는 음표 · 금빛 고리
     */
    static boolean song(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = JobUpgrades.ticks(sp, k, "dur", 200);
        JobTraits.startStance(sp, k, dur);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double ground0 = sp.getY();
        JobMove.run(sp, JobMove.to(0, 10, JobMove.up(sp, k.num("lift", 1)), JobMove.OUT), JobMove.hold(10, 30), JobMove.to(30, 38, JobMove.down(sp, ground0),
                JobMove.INOUT));
        JobFx.aura(sp, j, 2.6, 1.6, 0.9);
        JobFx.ring(level, V.up(c, 0.06), 2.6, j, false, 0.6, Opt.empty());
        JobFx.eyeRing(sp, j, 2.4, 0.95, 0.5);
        String life = RuneKit.lifeToken(sp);
        // 노래: 1.5초 동안 음표가 몸 둘레를 돌며 떠오름 (3틱마다) · 금빛 반짝임 · 화음
        for (int t = 0; t < 30; t += 3) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                double a = tt * 0.45;
                Vec3 p = V.up(sp.position(), 0.8 + tt * 0.03);
                JobFx.note(d, new Vec3(p.x + Math.cos(a) * 0.9, p.y, p.z + Math.sin(a) * 0.9), tt / 3 % 4, 0.26, j, 0.9, JobFx.hide1p(sp).with("dir", V.UP, "speed", 1.2));
                SkillFx.fx(d, "rpg:arrow_spark", V.up(sp.position(), 1.0 + Kit.rand()), Opt.of("dir", V.UP, "speed", 1.6, "size", 0.12, "grow", 0.6, "life", 0.4, "color",
                        GOLD, "alpha", 1.0));
                if (tt % 6 == 0) SkillFx.play(d, "block.note_block.chime", sp.position(), 0.9 + tt * 0.03, 0.8);
            });
        }
        SkillFx.sparks(level, V.up(c, 1.0), GOLD, 12, 4);
        SkillFx.play(level, "beacon.power", c, 1.6, 0.8);
        SkillFx.play(level, "block.note_block.harp", c, 1.2, 1.0);
        return true;
    }

    // ------------------------------------------------------------ 별의 노래

    /**
     * 별의 노래: 밤하늘 · lift 칸 떠올라 조준한 곳 (range 칸) 위에 마도서를 펼침 (rise 틱) · 책장 pages 장이 둘레를 맴돎 → gap 틱마다 책장 page 장씩 (waves 번) 불 · 얼음 ·
     * 번개 주문이 차례로 떨어짐 (둘레 radius 칸의 적에게 나눠 — 얼음은 freeze 틱 빙결) → rainAt 틱 넷째 노래: 하늘에 별자리가 떠 별 stars 개가 rainEvery 틱마다 떨어짐
     * (적에게 나눠 — 같은 적은 한 번에 · 숫자 여러 줄) → finalAt 틱 별자리가 가운데로 모여 대폭발 (4틱 뒤 — final, 둘레 radius 칸). 모두 마법 피해
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 aim = Kit.aimPoint(sp, k.num("range", 20));
        Vec3 center = new Vec3(aim.x, RuneKit.groundAt(level0, aim, sp.getY()), aim.z);
        int rise = (int) k.num("rise", 20);
        int gap = (int) k.num("gap", 10);
        int waves = Math.max(1, (int) k.num("waves", 3));
        int pages = Math.max(waves, (int) k.num("pages", 9));
        int per = Math.max(1, pages / waves);
        int rainAt = (int) k.num("rainAt", 50);
        int rainEvery = Math.max(1, (int) k.num("rainEvery", 2));
        int stars = Math.max(1, (int) k.num("stars", 10));
        int finalAt = (int) k.num("finalAt", 72);
        double radius = k.num("radius", 8);
        double distH = Math.hypot(center.x - sp.getX(), center.z - sp.getZ());
        Vec3 back = distH > 0.1 ? V.flat(V.sub(center, sp.position())) : Kit.facingOf(sp);
        Vec3 bookAt = V.up(V.add(center, back, Math.max(0, 7 - distH) * 0.6), 3.6 + Math.max(0, Math.min(1.2, (distH - 7) * 0.15)));
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        Ult.sky(level0, sp.position(), "rpg:ult_night", finalAt + 30);
        // 몸: 3칸 떠올라 (14틱) 노래하는 동안 떠 있다가 대폭발 때 내려앉음
        double ground0 = sp.getY();
        JobMove.run(sp, JobMove.to(0, 14, JobMove.up(sp, k.num("lift", 3)), JobMove.OUT), JobMove.hold(14, finalAt), JobMove.to(finalAt, finalAt + 8,
                JobMove.down(sp, ground0), JobMove.INOUT));
        JobFx.aura(sp, j, 3.0, rise / 20.0 + 0.4, 0.9);
        JobFx.eyeRing(sp, j, 2.6, 1.1, 0.5);
        JobFx.ring(level0, V.up(center, 0.06), radius, j, false, rise / 20.0 + 0.3, Opt.of("alpha", 0.7));
        SkillFx.play(level0, "item.book.page_turn", sp.position(), 0.8, 1.0);
        SkillFx.play(level0, "beacon.power", center, 1.3, 0.9);
        SkillFx.play(level0, "block.note_block.chime", sp.position(), 0.7, 1.0);
        // 마도서 (판 — 아래에서 솟아오르며 커짐 · 시전자 쪽으로 조금 눕혀 책장이 넘어감) · 둘레를 맴도는 책장 (불 · 얼음 · 번개 빛)
        Vec3 toMe = V.len(V.sub(sp.position(), bookAt)) > 0.1 ? V.flat(V.sub(sp.position(), bookAt)) : V.scale(Kit.facingOf(sp), -1);
        double tilt = Math.toRadians(35);
        Vec3 bookFace = V.unit(V.add(V.scale(toMe, Math.cos(tilt)), V.UP, Math.sin(tilt)));
        Vec3 bookUp = V.unit(V.add(V.scale(V.UP, Math.cos(tilt)), toMe, -Math.sin(tilt)));
        int bookId = JobFx3d.spawn(level0, "grimoire", V.up(bookAt, -1.6), j, Opt.of("to", bookAt, "ctrl", V.up(bookAt, -0.5), "travel", rise / 20.0, "ease", "out3",
                "dir", bookUp, "face", bookFace, "scale", 2.2, "pop", rise / 20.0, "life", finalAt / 20.0 + 0.55, "anim", "flip", "loop", 1.0, "animSpeed", 0.9,
                "fadeIn", 0.2, "fadeOut", 0.3, "glowAlpha", 0.4, "shine", 0.2, "bob", 0.12, "bobHz", 0.5));
        for (int t = 0; t < finalAt - 4; t += 12) {
            RuneKit.later(t, () -> {
                if (RuneKit.sameLife(sp, life)) SkillFx.play(sp.level(), "item.book.page_turn", bookAt, 1.2, 0.6);
            });
        }
        int[] pageIds = new int[pages];
        Vec3 ring = new Vec3(center.x, bookAt.y - 1.2, center.z);
        for (int i = 0; i < pages; i++) {
            double[][] col = MageSkills.SPELLS[(i / per) % 3];
            double rr = radius * (0.55 + 0.25 * Math.sin(i * 1.7));
            pageIds[i] = JobFx3d.spawn(level0, "page", ring, MageSkills.mix(col[0], glow, 0.35), col[1], Opt.of("orbit", rr, "orbitSpeed", Math.toDegrees(0.14 * 20),
                    "orbitAngle", 360.0 * i / pages, "bob", 0.55, "bobHz", 0.45, "dir", V.UP, "faceCam", 1.0, "scale", 2.2, "life", (rise + (i / per) * gap) / 20.0 + 0.1,
                    "fadeIn", 0.3, "fadeOut", 0.15, "delay", Math.min(rise / 20.0, 0.05 * i), "glowAlpha", 0.5, "shine", 0.25));
        }
        JobFx3d.guard(sp, life, pageIds, finalAt);
        JobFx3d.guard(sp, life, new int[] {bookId}, finalAt);
        for (int w = 0; w < waves; w++) {
            int ww = w;
            RuneKit.later(rise + w * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (int p = 0; p < per; p++) JobFx3d.kill(sp.level(), pageIds[(ww * per + p) % pages], ring, 0.12);
                wave(sp, j, k, P, center, bookAt, radius, per, ww % 3, life);
            });
            JobKit.motionLater(sp, "arcmage_ult:wave", rise + w * gap - 1, life);
        }
        // 넷째 노래 — 별의 비: 하늘에 별자리 (일곱 별 · 금빛 선) → 별이 차례로 떨어짐
        Vec3[] sky = skyStars(center, back, radius);
        RuneKit.later(rainAt - 8, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            for (int i = 0; i < sky.length; i++) {
                JobFx.starNode(d, sky[i], 1.0, 0, 2, 0.3, false, Opt.empty(), NIGHT, GOLD);
                JobFx.starNode(d, sky[i], 0.85, 2, 4, (finalAt - rainAt + 8) / 20.0, true, Opt.of("fade", 0.15), NIGHT, GOLD);
                if (i > 0) {
                    RuneKit.streak(d, sky[i - 1], sky[i], NIGHT, 0.4, (finalAt - rainAt + 8) / 20.0, 0.5, 0.05 * i);
                    RuneKit.streak(d, sky[i - 1], sky[i], GOLD, 0.14, (finalAt - rainAt + 8) / 20.0, 1.0, 0.05 * i);
                }
            }
            SkillFx.play(d, "block.beacon.activate", center, 1.5, 1.2);
            SkillFx.play(d, "block.amethyst_block.resonate", center, 1.0, 1.2);
        });
        RuneKit.later(rainAt, () -> {
            if (RuneKit.sameLife(sp, life)) starRain(sp, j, k, P, center, radius, sky, stars, rainEvery, life);
        });
        JobKit.motionLater(sp, "arcmage_ult:rain", rainAt - 1, life);
        RuneKit.later(finalAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            mmorpg.fabric.Fx3d.anim(sp.level(), bookId, "close", false, bookAt);
            collapse(sp, j, k, P, center, radius, sky, life);
        });
        JobKit.motionLater(sp, "arcmage_ult:final", finalAt - 4, life);
        return true;
    }

    /** 하늘의 별자리 일곱 (과녁 가운데 위 6칸 — 시전자 쪽으로 열린 국자 꼴: 북두칠성. 영상 1 뒤 8 → 6칸 — 3인칭 화면 위 끝에 잘렸음) */
    static Vec3[] skyStars(Vec3 center, Vec3 back, double radius) {
        Vec3 r = V.rightOf(back);
        double s = Math.min(1.0, radius / 8.0);
        double[][] pts = {{-4.2, -1.6, 0.4}, {-2.4, -0.8, 0.9}, {-0.8, -0.2, 0.3}, {0.9, 0.0, 0.0}, {1.6, 1.6, 0.6}, {3.6, 1.2, 0.2}, {3.2, -0.6, 0.8}};
        Vec3[] out = new Vec3[pts.length];
        for (int i = 0; i < pts.length; i++) {
            out[i] = V.up(V.add(V.add(center, r, pts[i][0] * s * 1.3), back, pts[i][1] * s * 1.3), 6.0 + pts[i][2]);
        }
        return out;
    }

    /** 주문 한 물결 (한 가지 — 0 불 · 1 얼음 · 2 번개): 책장 per 장이 둘레 radius 칸의 적 (가까운 순 — 돌아가며) 에게. 같은 적에 여러 장이면 한 번에 · 숫자 여러 줄 */
    private static void wave(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 center, Vec3 bookAt, double radius, int per, int kind, String life) {
        ServerLevel level = sp.level();
        List<LivingEntity> foes = RuneKit.nearestTo(Kit.around(sp, center, radius, 4), center);
        Map<UUID, Integer> count = new LinkedHashMap<>();
        Map<UUID, LivingEntity> byId = new LinkedHashMap<>();
        for (int p = 0; p < per; p++) {
            Vec3 from = V.add(bookAt, new Vec3(Math.cos(p * 2.1 + kind) * 1.4, -0.4, Math.sin(p * 2.1 + kind) * 1.4), 1);
            Vec3 to;
            if (foes.isEmpty()) {
                double a = Kit.rand() * Math.PI * 2;
                Vec3 g = V.add(center, new Vec3(Math.cos(a), 0, Math.sin(a)), radius * Math.sqrt(Kit.rand()));
                to = new Vec3(g.x, RuneKit.groundAt(level, g, center.y) + 0.2, g.z);
            } else {
                LivingEntity e = foes.get(p % foes.size());
                count.merge(e.getUUID(), 1, Integer::sum);
                byId.put(e.getUUID(), e);
                to = Kit.bodyPoint(e);
            }
            MageSkills.spellFx(level, from, to, kind, p * 2);
        }
        int freeze = (int) k.num("freeze", 10);
        for (var en : count.entrySet()) {
            LivingEntity e = byId.get(en.getKey());
            int n = en.getValue();
            JobKit.strikeSplit(sp, e, j, P, k.num("page", 3) * n, n, 2, true, false, life, 6, x -> {
                if (RuneKit.fxQuota("jobStarSong", 6)) JobFx.hit(e, j, 1.1);
                if (kind == 1 && freeze > 0) {
                    if (JobKit.isBoss(e)) RuneKit.effect(e, "slowness", freeze * 3, 1);
                    else RuneKit.stun(e, freeze, MageSkills.SPELLS[1][1], RuneKit.fxQuota("jobFreeze", 6));
                    if (RuneKit.fxQuota("jobFrost", 6)) JobFx.frost(e, MageSkills.SPELLS[1][0], MageSkills.SPELLS[1][1], freeze + 8);
                }
            });
        }
    }

    /** 별의 비: 하늘의 별자리에서 별똥 stars 개가 every 틱마다 떨어짐 (둘레 radius 칸의 적에게 돌아가며 — 같은 적은 첫 별이 닿을 때 한 번에 · 숫자 여러 줄) */
    private static void starRain(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 center, double radius, Vec3[] sky, int stars, int every, String life) {
        ServerLevel level = sp.level();
        List<LivingEntity> foes = RuneKit.nearestTo(Kit.around(sp, center, radius, 4), center);
        Map<UUID, Integer> count = new LinkedHashMap<>();
        Map<UUID, LivingEntity> byId = new LinkedHashMap<>();
        List<LivingEntity> order = new ArrayList<>();
        for (int i = 0; i < stars; i++) {
            LivingEntity e = foes.isEmpty() ? null : foes.get(i % foes.size());
            order.add(e);
            if (e != null) {
                count.merge(e.getUUID(), 1, Integer::sum);
                byId.put(e.getUUID(), e);
            }
        }
        int fall = 6;
        for (int i = 0; i < stars; i++) {
            int ii = i;
            RuneKit.later(i * every, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                LivingEntity e = order.get(ii);
                Vec3 to;
                if (e != null && !e.isRemoved()) to = Kit.bodyPoint(e);
                else {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 g = V.add(center, new Vec3(Math.cos(a), 0, Math.sin(a)), radius * Math.sqrt(Kit.rand()));
                    to = new Vec3(g.x, RuneKit.groundAt(d, g, center.y) + 0.3, g.z);
                }
                Vec3 from = sky[ii % sky.length];
                JobFx.fallStar(d, from, to, V.len(V.sub(to, from)) / (fall / 20.0), 1.6, Opt.empty(), NIGHT, GOLD); // 영상 1 뒤 1.0 → 1.6 (가는 줄로만 보였음)
                SkillFx.play(d, "entity.firework_rocket.launch", from, 1.3, 0.5);
                RuneKit.later(fall, () -> {
                    JobFx.starNode(d, to, 1.0, 6, 2, 0.3, false, Opt.empty(), NIGHT, GOLD);
                    SkillFx.sparks(d, to, GOLD, 8, 5);
                    SkillFx.play(d, "firework.blast", to, 1.2 + 0.03 * ii, 0.6);
                });
            });
        }
        for (var en : count.entrySet()) {
            LivingEntity e = byId.get(en.getKey());
            int n = en.getValue();
            JobKit.strikeSplit(sp, e, j, P, k.num("star", 1.4) * n, n, every, true, false, life, fall, x -> {
                if (RuneKit.fxQuota("jobStarRain", 6)) JobFx.hit(e, j, 1.1);
            });
        }
    }

    /** 별자리가 모여 대폭발: 하늘의 별이 가운데로 금빛 줄을 긋고 (4틱) → 큰 별 번쩍 · 터짐 셋 · 고리 둘 · 빛기둥 → 둘레 radius 칸 (final — 큰 숫자) */
    private static void collapse(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 center, double radius, Vec3[] sky, String life) {
        ServerLevel level = sp.level();
        Vec3 c = V.up(center, 1.2);
        for (Vec3 s : sky) {
            RuneKit.streak(level, s, c, GOLD, 0.16, 0.3, 1.0, 0);
            RuneKit.streak(level, s, c, NIGHT, 0.4, 0.25, 0.5, 0);
        }
        SkillFx.play(level, "block.beacon.power_select", center, 1.6, 1.2);
        RuneKit.later(4, () -> {
            JobFx.starNode(level, c, 3.2, 6, 2, 0.5, false, Opt.empty(), NIGHT, GOLD);
            for (int i = 0; i < 3; i++) {
                int ii = i;
                RuneKit.later(i * 2, () -> JobFx.burst(level, V.up(center, 0.8 + ii * 0.7), 2.2 + ii * 0.7, j, 0.42, Opt.empty()));
            }
            JobFx.ring(level, V.up(center, 0.06), radius, j, false, 0.5, Opt.empty());
            RuneKit.later(3, () -> JobFx.ring(level, V.up(center, 0.1), radius * 0.6, j, true, 0.45, Opt.empty()));
            RuneKit.godRays(sp, level, center, radius * 0.6, 6, NIGHT, GOLD, Opt.of("h", 14.0, "life", 0.9, "alpha", 0.8, "skipNear", true));
            SkillFx.sparks(level, c, GOLD, 22, 7);
            Ult.flash(level, center, GOLD, Opt.empty(), sp);
            RuneKit.shakeAround(level, center, 18, 0.4, 0.4);
            SkillFx.play(level, "random.explode", center, 0.8, 1.0);
            SkillFx.play(level, "firework.large_blast", center, 1.0, 0.9);
            SkillFx.play(level, "item.trident.thunder", center, 1.5, 0.7);
            for (LivingEntity e : Kit.around(sp, center, radius, 4)) {
                JobKit.strike(sp, e, j, P, k.num("final", 15), true, true, life, 0, x -> {
                    RuneKit.knock(e, V.sub(e.position(), center), 0.45, 0.45);
                    if (RuneKit.fxQuota("jobStarFinal", 6)) JobFx.hit(e, j, 1.5);
                });
            }
        });
    }
}
