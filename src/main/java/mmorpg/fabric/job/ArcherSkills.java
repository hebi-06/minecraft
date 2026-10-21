package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Jobs;
import mmorpg.fabric.DamageNumbers;
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
 * 0.28.0 궁수 직업 스킬 (jobs.json archer — 연두 · 금빛, 특성 「호흡」): 속사 · 바람 타기 · 화살비 · 정조준 · 관천일시. 화살은 공격 피해.
 * 빠른 연타 (속사 · 화살비의 한 물결) 는 피해를 한 번에 넣고 숫자를 여러 줄로 (메이플식 쌓이는 숫자 — JobKit.strikeSplit).
 * 연출 = 메이플식 2D 그림 (JobFx — 빛의 화살 · 빛의 활 · 과녁 · 바람 고리 · 굵은 광선 · 터짐).
 */
final class ArcherSkills {
    private ArcherSkills() {}

    /** 바람 타기 잔상 (짙은 연두) */
    private static final double[] SHADE = {0.16, 0.36, 0.1};

    static void register() {
        JobSystem.register("archer_rapid", ArcherSkills::rapid);
        JobSystem.register("archer_wind", ArcherSkills::wind);
        JobSystem.register("archer_rain", ArcherSkills::rain);
        JobSystem.register("archer_focus", ArcherSkills::focus);
        JobSystem.register("archer_ult", ArcherSkills::ult);
    }

    /** 빛의 활 자리 (활을 든 손 앞 — 1인칭 오른쪽 아래, 3인칭은 몸 앞) */
    private static Vec3 bowAt(ServerPlayer sp, double ahead) {
        Vec3 look = sp.getViewVector(1F);
        return V.add(RuneKit.bowHand(sp), look, ahead);
    }

    // ------------------------------------------------------------ 속사

    /**
     * 속사: 겨눈 적 (range + 호흡 칸 · 없으면 쓰지 않음) 에게 every 틱마다 빛의 화살 arrows 발 (호흡 need 이상이면 more 발) — 손 앞의 빛의 활이 당겼다 놓임 (발마다).
     * 피해는 첫 화살이 닿을 때 한 번 (arrow × 발 수 — 숫자 발 수 줄이 화살이 닿을 때마다 한 줄씩)
     */
    static boolean rapid(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int breath = JobTraitsMore.breath(sp);
        LivingEntity target = Kit.pickTarget(sp, k.num("range", 24) + breath, 10);
        if (target == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다", 30);
            return false;
        }
        int n = breath >= k.num("need", 3) ? (int) k.num("more", 4) : (int) k.num("arrows", 3);
        int every = Math.max(1, (int) k.num("every", 3));
        double mult = k.num("arrow", 2.5) * n;
        String life = RuneKit.lifeToken(sp);
        if (n > (int) k.num("arrows", 3)) JobSystem.note(sp, "§a호흡 " + breath + " §7→ " + n + "발");
        for (int i = 0; i < n; i++) {
            int ii = i;
            RuneKit.later(i * every, () -> {
                if (!RuneKit.sameLife(sp, life) || target.isRemoved() || !target.isAlive()) return;
                ServerLevel level = sp.level();
                Vec3 from = RuneKit.bowHand(sp);
                Vec3 to = V.up(Kit.bodyPoint(target), ((ii % 3) - 1) * 0.2);
                // 빛의 활이 당겼다 놓임 (손 앞 — 카메라를 봄) · 빛의 화살
                JobFx.bow(level, bowAt(sp, 0.5), 0.42, j, 2, 4, every / 20.0 + 0.06, Opt.of("vel", RuneKit.bodyVel(sp), "fade", 0.3));
                int t = JobFx.arrow(level, from, to, j, 55, 0.62, Opt.empty());
                SkillFx.play(level, "random.bow", from, 1.3 + ii * 0.08, 0.7);
                if (ii == 0) JobKit.strikeSplit(sp, target, j, P, mult, n, every, false, false, life, t, d -> {});
                RuneKit.later(t, () -> {
                    if (target.isRemoved() || !(target.level() instanceof ServerLevel lv)) return;
                    JobFx.hit(target, j, 0.8);
                    SkillFx.play(lv, "random.bowhit", Kit.bodyPoint(target), 1.2, 0.5);
                });
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 바람 타기

    /**
     * 바람 타기: 움직이는 쪽 (가만히 서 있으면 뒤 — 활을 겨눈 채 물러남, 그쪽이 막혔으면 다른 쪽) dist 칸 (벽 · 낭떠러지 앞까지 · 적 앞에서 멈춤) 을 steps 틱에 미끄러지듯
     * 지나감 (발밑 바람 고리 · 지나온 자리 연두 잔상 · 1인칭 빠른 줄) → for 틱 안의 다음 활 평타 shots 번이 각각 한 발 더 (JobTraitsMore — 두 번째 화살 = 평타와 같은 피해) ·
     * 그동안 호흡이 줄지 않음. 묶였거나 사방이 막혀 못 가면 쓰지 않음.
     * 0.28.0 사진첩: 보는 쪽으로만 가면 적을 겨눈 궁수가 적 몸속으로 미끄러져 들어갔음 → 움직이는 쪽 · 서 있으면 뒤로
     */
    static boolean wind(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        Vec3 start = sp.position();
        Vec3 look = Kit.facingOf(sp);
        Vec3 move = sp.getKnownMovement();
        Vec3 moveH = new Vec3(move.x, 0, move.z);
        Vec3 first = V.len(moveH) > 0.04 ? V.unit(moveH) : V.scale(look, -1);
        Vec3 f = first;
        double free = Kit.reachFlat(level, start, f, k.num("dist", 5));
        for (Vec3 alt : new Vec3[] {V.scale(first, -1), look, V.scale(look, -1)}) {
            if (free >= 1.2) break;
            double a = Kit.reachFlat(level, start, alt, k.num("dist", 5));
            if (a > free) {
                f = alt;
                free = a;
            }
        }
        // 적 앞에서 멈춤 (적 몸속으로 들어가지 않게)
        for (LivingEntity e : Kit.around(sp, start, k.num("dist", 5) + 1.5, 2.0)) {
            Vec3 rel = V.sub(e.position(), start);
            double along = V.dot(rel, f);
            double side = V.len(V.sub(new Vec3(rel.x, 0, rel.z), V.scale(f, along)));
            if (along > 0 && side < 0.9 + Kit.bodyPad(e)) free = Math.min(free, along - 1.0 - Kit.bodyPad(e));
        }
        List<Kit.Spot> spots = free > 0.4 ? Kit.dashSpots(level, start, f, free) : List.of();
        if (spots.isEmpty()) {
            Notify.notify(sp, "§c" + k.name() + " §7— 갈 곳이 막혔습니다", 30);
            return false;
        }
        double len = spots.get(spots.size() - 1).s();
        int steps = Math.max(1, (int) k.num("steps", 4));
        List<Vec3> marks = new ArrayList<>();
        for (int s = 1; s <= steps; s++) {
            double want = len * s / steps;
            Kit.Spot best = spots.get(0);
            for (Kit.Spot x : spots) if (Math.abs(x.s() - want) < Math.abs(best.s() - want)) best = x;
            marks.add(best.p());
        }
        Vec3 dir = f;
        // 1인칭 빠른 줄은 앞으로 갈 때만 (뒤 · 옆으로 물러날 때 앞으로 달리는 것처럼 보이지 않게)
        boolean ahead = V.dot(dir, look) > 0.35;
        String life = RuneKit.lifeToken(sp);
        JobTraitsMore.windStart(sp, (int) k.num("shots", 2), (int) k.num("for", 100));
        Kit.noFall(sp, 30);
        JobFx.wind(level, V.up(start, 0.08), 1.6, j, 0.45, Opt.empty());
        RuneKit.shadeClone(sp, level, start, look, 1, SHADE, JobKit.glow(j), Opt.of("life", 0.4, "fade", 0.6, "alpha", 0.8));
        SkillFx.play(level, "mob.breeze.charge", start, 1.4, 0.8);
        SkillFx.play(level, "mob.phantom.flap", start, 1.2, 0.8);
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
                JobFx.dash(d, V.up(prev, 0.6), V.up(to, 0.6), j, 0.3, JobFx.hide1p(sp));
                JobFx.wind(d, V.up(to, 0.08), 1.1, j, 0.35, Opt.of("alpha", 0.85));
                if (ahead) JobFx.speed(sp, j, ss == marks.size() - 1 ? 0.25 : 0.1, 0.75);
                SkillFx.fx(d, "rpg:arrow_spark", V.up(to, 0.3), Opt.of("dir", V.unit(new Vec3(-dir.x, 0.5, -dir.z)), "speed", 2.0, "size", 0.12, "grow", 0.6, "life", 0.35,
                        "color", JobKit.glow(j), "alpha", 1.0));
            });
        }
        RuneKit.later(steps, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            DamageNumbers.showFace(sp, "§a§l바람 — 다음 두 발 ×2", 2.4, 0, -0.3);
            JobFx.eyeRing(sp, j, 2.4, 0.8, 0.4);
            SkillFx.play(sp.level(), "block.amethyst_block.chime", sp.position(), 1.6, 0.6);
        });
        return true;
    }

    // ------------------------------------------------------------ 화살비

    /**
     * 화살비: 조준한 곳 (range 칸) 바닥에 과녁 (반지름 radius 칸 — 조여 들어와 맞춰짐) → first 틱부터 gap 틱마다 (waves 번) 하늘에서 빛의 화살이 쏟아짐 — 과녁 안의 적마다
     * 한 물결에 두 발 (wave — 한 번에 넣고 숫자 per 줄), 빈 자리에도 화살이 꽂힘
     */
    static boolean rain(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 aim = Kit.aimPoint(sp, k.num("range", 24) + JobTraitsMore.breath(sp));
        Vec3 center = new Vec3(aim.x, RuneKit.groundAt(level0, aim, sp.getY()), aim.z);
        double r = k.num("radius", 5);
        int waves = (int) k.num("waves", 5);
        int gap = (int) k.num("gap", 10);
        int first = (int) k.num("first", 6);
        int per = Math.max(1, (int) k.num("per", 2));
        int end = first + (waves - 1) * gap;
        String life = RuneKit.lifeToken(sp);
        // 위로 쏘아 올림 (손 앞 활 · 하늘로 빛의 화살 셋) · 바닥 과녁
        JobFx.bow(level0, bowAt(sp, 0.5), 0.5, j, 1, 5, 0.3, Opt.of("vel", RuneKit.bodyVel(sp)));
        for (int i = 0; i < 3; i++) {
            Vec3 from = RuneKit.bowHand(sp);
            Vec3 up = V.add(V.up(from, 9 + i), V.flat(sp.getViewVector(1F)), 2 + i * 0.6);
            JobFx.arrow(level0, from, up, j, 45, 0.6, Opt.empty());
        }
        SkillFx.play(level0, "random.bow", sp.position(), 0.9, 1.0);
        JobFx.target(level0, V.up(center, 0.06), r, j, V.UP, 0, 0.3, Opt.empty());
        RuneKit.later(6, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.target(sp.level(), V.up(center, 0.06), r, j, V.UP, 1, (end - 6) / 20.0 + 0.4, Opt.of("alpha", 0.85));
        });
        // 하늘 (과녁 위 — 쏜 사람 쪽으로 조금, 8.5칸 위): 1인칭에서 떨어지는 화살이 화면에 들어오게 낮게
        boolean onMe = Math.hypot(sp.getX() - center.x, sp.getZ() - center.z) < 0.1;
        Vec3 sky = V.up(V.add(center, onMe ? V.ZERO : V.flat(V.sub(sp.position(), center)), 2.5), 8.5);
        int fall = 6;
        // 쏟아지는 화살 (2틱마다 셋 — 피해와 따로, 빈 자리에도): 닿으면 불티 · 땅에 잠깐 꽂혀 있음
        for (int t = Math.max(0, first - fall); t <= end - 2; t += 2) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                for (int i = 0; i < 3; i++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 g = V.add(center, new Vec3(Math.cos(a), 0, Math.sin(a)), r * Math.sqrt(Kit.rand()));
                    Vec3 to = new Vec3(g.x, RuneKit.groundAt(level, g, center.y) + 0.05, g.z);
                    Vec3 from = V.add(sky, new Vec3(Kit.rand() * 2 - 1, Kit.rand(), Kit.rand() * 2 - 1), 1.6);
                    Vec3 dv = V.unit(V.sub(to, from));
                    JobFx.arrow(level, from, to, j, V.len(V.sub(to, from)) / (fall / 20.0), 0.55, Opt.of("fade", 0.05));
                    RuneKit.later(fall, () -> {
                        SkillFx.sparks(level, V.up(to, 0.1), JobKit.glow(j), 2, 2);
                        JobFx.arrowAt(level, V.up(to, 0.05), dv, 0.42, 0.55, j, Opt.of("alpha", 0.85, "fade", 0.35));
                    });
                }
            });
        }
        for (int w = 0; w < waves; w++) {
            int ww = w;
            // 적마다 per 발 (피해와 같은 틱에 닿게 fall 틱 전에 떨어지기 시작)
            RuneKit.later(Math.max(0, first + w * gap - fall), () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                for (LivingEntity e : Kit.around(sp, center, r, 4)) {
                    Vec3 to = Kit.bodyPoint(e);
                    for (int p = 0; p < per; p++) {
                        Vec3 from = V.add(sky, new Vec3(Kit.rand() * 2 - 1, Kit.rand(), Kit.rand() * 2 - 1), 1.2);
                        Vec3 at = V.up(to, (p - 0.5) * 0.4);
                        JobFx.arrow(level, from, at, j, V.len(V.sub(at, from)) / (fall / 20.0), 0.6, Opt.of("fade", 0.05));
                    }
                }
                if (ww % 2 == 0) SkillFx.play(level, "random.bowhit", center, 0.9 + ww * 0.08, 0.8);
            });
            RuneKit.later(first + w * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (LivingEntity e : Kit.around(sp, center, r, 4)) {
                    JobKit.strikeSplit(sp, e, j, P, k.num("wave", 2.7), per, 2, false, false, life, 0, d -> {
                        if (RuneKit.fxQuota("jobRain", 6)) JobFx.hit(e, j, 0.9);
                    });
                }
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 정조준

    /**
     * 정조준: dur 틱 태세 — 호흡 즉시 가득 · 움직여도 줄지 않음 · 활 평타 관통 (폭 width) · 치명타 + crit (JobTraitsMore). 그림: 몸에서 솟는 빛 · 1인칭 눈앞 과녁이 조여 듦 ·
     * 발밑 둥근 베기 · 글자, 태세 동안 8틱마다 활 앞 금빛 반짝임 (JobTraits) · 쏠 때마다 화살 끝 금빛
     */
    static boolean focus(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) k.num("dur", 200);
        JobTraits.startStance(sp, k, dur);
        JobTraitsMore.focusStart(sp, j);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 look = sp.getViewVector(1F);
        JobFx.aura(sp, j, 2.6, 1.0, 0.9);
        JobFx.ring(level, V.up(c, 0.06), 2.0, j, false, 0.45, Opt.empty());
        JobFx.target(level, V.add(sp.getEyePosition(), look, 3.2), 0.85, j, V.scale(look, -1), 0, 0.35, JobFx.only1p(sp).with("vel", RuneKit.bodyVel(sp)));
        JobFx.target(level, V.add(V.up(c, 1.0), V.flat(look), 1.0), 0.7, j, V.scale(V.flat(look), -1), 0, 0.4, JobFx.hide1p(sp));
        DamageNumbers.showFace(sp, "§a§l정조준", 2.4, 0, 0.2);
        SkillFx.play(level, "crossbow.loading.end", c, 1.2, 0.9);
        SkillFx.play(level, "block.amethyst_block.resonate", c, 1.6, 0.6);
        return true;
    }

    // ------------------------------------------------------------ 관천일시

    /**
     * 관천일시: 새벽빛 하늘 → draw 틱 동안 눈앞에 커지는 빛의 활 (시위를 당김 · 빛이 모여 듦) → 거대한 빛의 화살 한 발 (speed 칸/초) — 앞 range 칸 (막힌 블록까지) 일직선 폭 width
     * 관통 (shot — 큰 숫자, 화살이 닿는 틱에) → 화살이 지나간 자리에 남은 잔상 afterimages 개가 burstDelay 틱 뒤부터 가까운 쪽부터 한 틱에 하나씩 터짐 (맞은 적마다 그 자리의 잔상 하나 —
     * after). 화살과 잔상 사이는 10틱 넘게 떨어짐
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int draw = (int) k.num("draw", 24);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        int total = draw + (int) k.num("burstDelay", 12) + (int) k.num("afterimages", 20) + 10;
        Ult.sky(level0, c0, "rpg:ult_dawn", total + 30);
        JobFx.aura(sp, j, 2.8, draw / 20.0 + 0.3, 0.9);
        JobFx.ring(level0, V.up(c0, 0.06), 3.0, j, false, draw / 20.0 + 0.2, Opt.of("alpha", 0.8));
        SkillFx.play(level0, "crossbow.loading.start", c0, 0.7, 1.0);
        SkillFx.play(level0, "beacon.power", c0, 1.6, 0.8);
        // 시위를 당김: 눈앞의 빛의 활이 커지며 당겨짐 · 둘레에서 빛이 모여 듦
        for (int t = 0; t < draw; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                double q = (tt + 2.0) / draw;
                Vec3 look = sp.getViewVector(1F);
                // 눈앞 2.3칸 — 시위를 당길수록 커져 화면을 채움 (1인칭) · 3인칭에서는 몸 앞
                Vec3 at = V.add(V.add(sp.getEyePosition(), look, 2.3), JobFx.screenUp(look), -0.1);
                JobFx.bow(level, at, 1.0 + 0.4 * q, j, Math.min(3, (int) (q * 4)), 1, 0.13, Opt.of("fade", 0.05, "alpha", 0.8 + 0.2 * q));
                for (int sd : new int[] {-1, 1}) {
                    Vec3 a = V.add(V.add(at, V.rightOf(V.flat(look)), sd * (2.4 - q * 1.6)), V.UP, 1.2 - q);
                    SkillFx.fx(level, "rpg:arrow_spark", a, Opt.of("dir", V.unit(V.sub(at, a)), "speed", 4.0, "size", 0.16, "grow", 0.5, "life", 0.25, "color", core,
                            "alpha", 1.0));
                }
                if (tt % 6 == 0) SkillFx.play(level, "crossbow.loading.middle", sp.position(), 0.8 + q * 0.6, 0.6);
            });
        }
        RuneKit.later(draw, () -> {
            if (RuneKit.sameLife(sp, life)) release(sp, j, k, P, glow, core, life);
        });
        return true;
    }

    /** 놓음: 거대한 빛의 화살 · 빛줄기 · 번쩍임 — 길 위의 적 (shot), 잔상이 차례로 터짐 (after) */
    private static void release(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, double[] glow, double[] core, String life) {
        ServerLevel level = sp.level();
        Vec3 look = sp.getViewVector(1F);
        Vec3 from = V.add(sp.getEyePosition(), look, 0.8);
        double len = Math.max(2, RuneKit.rayLength(level, from, look, k.num("range", 40)));
        Vec3 end = V.add(from, look, len);
        double speed = k.num("speed", 80);
        double half = k.num("width", 3) / 2;
        int n = Math.max(1, (int) k.num("afterimages", 20));
        int burstDelay = (int) k.num("burstDelay", 12);
        // 놓은 활 (번쩍) · 거대한 빛의 화살 · 지나가는 길의 굵은 광선 (1인칭에서는 눈 가까이를 뺌) · 번쩍임 · 흔들림
        Vec3 bowAt = V.add(V.add(sp.getEyePosition(), look, 2.3), JobFx.screenUp(look), -0.1);
        JobFx.bow(level, bowAt, 1.4, j, 4, 2, 0.3, Opt.empty());
        JobFx.arrow(level, V.add(from, look, 1.6), end, j, speed, 2.4, Opt.empty());
        // 광선은 활을 든 손에서 (1인칭: 오른쪽 아래에서 과녁 쪽으로 뻗는 줄로 보임 — 눈에서 곧장 나가면 점으로만 보임)
        Vec3 hand = V.add(RuneKit.bowHand(sp), look, 1.2);
        JobFx.beam(level, hand, end, j, 3.2, len / speed + 0.35, Opt.of("alpha", 0.85));
        // 화살이 지나가는 자리마다 카메라를 보는 작은 터짐 (가까운 것부터 차례로 — 광선 띠는 길이 방향 그림이라 쏜 사람 뒤 · 1인칭에서는 옆면만 보여 보이지 않음)
        for (double dd = 1.6; dd <= len; dd += 1.3) {
            Vec3 at = V.add(from, look, dd);
            int delay = (int) Math.round(dd / speed * 20);
            double sz = 0.32 + Math.min(0.3, dd * 0.008);
            RuneKit.later(delay, () -> JobFx.burst(level, at, sz, j, 0.34, Opt.of("alpha", 0.9)));
        }
        // 화살이 지나가며 남기는 충격 고리 (화살 쪽을 봄 — 1인칭에서는 과녁 둘레로 겹겹이 퍼지는 고리)
        for (double dd : new double[] {3, 6, 10, 15, 21, 28, 36}) {
            if (dd > len) break;
            Vec3 ringAt = V.add(from, look, dd);
            int delay = (int) Math.round(dd / speed * 20);
            double rr = 0.9 + dd * 0.035;
            RuneKit.later(delay, () -> JobFx.ringFacing(level, ringAt, rr, j, V.scale(look, -1), 0.4, Opt.of("alpha", 0.9)));
        }
        SkillFx.beamAway(level, sp.getEyePosition(), from, end, core, Opt.of("half", 0.4, "thick", 0.45, "life", len / speed + 0.4, "alpha", 0.9, "sweep",
                len / speed), 2.4);
        Ult.flash(level, from, glow, Opt.empty(), sp);
        RuneKit.shakeAround(level, from, 12, 0.3, 0.35);
        SkillFx.play(level, "item.trident.thunder", from, 1.6, 0.8);
        SkillFx.play(level, "firework.large_blast", from, 1.3, 0.8);
        SkillFx.play(level, "random.bow", from, 0.6, 1.0);
        // 잔상: 길을 n 칸으로 나눈 자리마다 화살 잔상 (터질 때까지) → burstDelay 틱 뒤부터 한 틱에 하나씩 터짐
        double seg = len / n;
        for (int i = 0; i < n; i++) {
            Vec3 tip = V.add(from, look, seg * (i + 1));
            int at = burstDelay + i;
            if (i % 2 == 0) JobFx.arrowAt(level, tip, look, 0.6, at / 20.0, j, Opt.of("alpha", 0.6, "fade", 0.1));
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                JobFx.burst(level, tip, 1.0, j, 0.3, Opt.empty());
                if (at % 3 == 0) SkillFx.play(level, "firework.blast", tip, 1.3, 0.5);
            });
        }
        RuneKit.later(burstDelay + n / 2, () -> JobFx.ring(level, V.up(new Vec3(from.x, RuneKit.groundAt(level, from, from.y - 1.5), from.z), 0.06), 3.0, j, false, 0.4,
                Opt.of("alpha", 0.7)));
        for (Kit.LineHit h : RuneKit.lineTargets(sp, from, look, len, half)) {
            LivingEntity e = h.e();
            int hitT = (int) Math.round(h.t() / speed * 20);
            JobKit.strike(sp, e, j, P, k.num("shot", 40), false, true, life, hitT, d -> {
                RuneKit.knock(e, look, 0.5, 0.25);
                if (RuneKit.fxQuota("jobSkyArrow", 6)) JobFx.hit(e, j, 1.6);
            });
            int idx = (int) Math.max(0, Math.min(n - 1, Math.floor(h.t() / seg)));
            JobKit.strike(sp, e, j, P, k.num("after", 20), false, false, life, burstDelay + idx, d -> {
                if (RuneKit.fxQuota("jobSkyBurst", 6)) JobFx.hit(e, j, 1.1);
            });
        }
    }
}
