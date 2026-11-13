package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Jobs;
import mmorpg.fabric.Notify;
import mmorpg.fabric.rune.BowFx;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneProps;
import mmorpg.fabric.rune.RuneSwords;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 궁수 직업 스킬 — 2026-11-14 폭풍 궁수 (사용자가 고른 컨셉 — 정령 궁수 대신: jobs.json archer — 남빛 번개 · 흰 하늘빛, 특성 「충전」 (정령 교감에서 이름 · 그림만)):
 * 체인 볼트 · 썬더 스텝 · 스톰 레인 · 스톰 아이 (머리 위 먹구름 — StormEye) · 헤븐 보우 (하늘의 거대한 폭풍의 활 · 길을 가르는 번개 화살 · 벼락 스물).
 * 피해 · 재사용 대기 · 틱 · 매커니즘은 정령 궁수 그대로 (그림 · 이름 · 설명만). 화살은 공격 피해. 빠른 연타는 피해를 한 번에 넣고 숫자를 여러 줄로 (JobKit.strikeSplit).
 * 연출 = 모드 없는 클라이언트에도 보이는 것 (ArrowFx — 3D 빛 화살 · 3D 먹구름 · 3D 폭풍의 활 · 3D 번개 창 · 룬 번개 · 번개 구슬). 화면 흔들림 없음.
 */
final class ArcherSkills {
    private ArcherSkills() {}

    /** 먹구름 빛 (짙은 남빛 — 구름 모델에 곱함, 밑의 빛은 직업 빛) */
    static final double[] CLOUD = {0.3, 0.32, 0.52};
    /** 번개가 된 몸의 잔상 (짙은 남빛) */
    private static final double[] SHADE = {0.16, 0.2, 0.46};

    static void register() {
        JobSystem.register("archer_rapid", ArcherSkills::rapid);
        JobSystem.register("archer_wind", ArcherSkills::wind);
        JobSystem.register("archer_rain", ArcherSkills::rain);
        JobSystem.register("archer_focus", ArcherSkills::focus);
        JobSystem.register("archer_ult", ArcherSkills::ult);
    }

    // ------------------------------------------------------------ 체인 볼트

    /**
     * 체인 볼트 (archer_rapid): 겨눈 적 (range + 충전 칸 · 없으면 쓰지 않음) 에게 every 틱마다 번개 화살 arrows 발 (충전 need 이상이면 more 발) — 3D 빛 화살 · 날아가는 동안
     * 활과 적 사이를 잇는 번개 · 맞은 자리의 번개 구슬, 맞은 자리끼리 번개가 이어짐 (연쇄) · 마지막 화살에 하늘에서 벼락 (그림). 피해는 첫 화살이 닿을 때 한 번
     * (arrow × 발 수 — 숫자 발 수 줄이 화살이 닿을 때마다 한 줄씩)
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
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        // 0.50.0 동작: 쏘며 뒤로 짧게 뜀 (back 칸) — 화살은 손에서 그대로
        JobMove.run(sp, JobMove.hop(0, Math.max(6, (n - 1) * every + 3), JobMove.ahead(sp, -k.num("back", 1.5), 0), 0.5));
        if (n > (int) k.num("arrows", 3)) JobSystem.note(sp, "§9충전 " + breath + " §7→ " + n + "발");
        RuneKit.plasmaOrb(sp.level(), RuneKit.bowHand(sp), 0.3, glow, Opt.of("life", (n * every) / 20.0 + 0.1, "vel", RuneKit.bodyVel(sp)));
        Vec3[] prev = {null};
        for (int i = 0; i < n; i++) {
            int ii = i;
            RuneKit.later(i * every, () -> {
                if (!RuneKit.sameLife(sp, life) || target.isRemoved() || !target.isAlive()) return;
                ServerLevel level = sp.level();
                Vec3 from = RuneKit.bowHand(sp);
                Vec3 to = V.up(Kit.bodyPoint(target), ((ii % 3) - 1) * 0.2);
                int t = ArrowFx.shot(level, from, to, 55, 1.25, 3, target, glow, core);
                // 활과 적 사이를 잇는 번개 (화살이 날아가는 동안)
                RuneKit.zap(level, V.add(from, sp.getViewVector(1F), 0.4), to, glow, core, 0.09, t / 20.0 + 0.1);
                SkillFx.play(level, "random.bow", from, 1.3 + ii * 0.08, 0.7);
                if (ii == 0) JobKit.strikeSplit(sp, target, j, P, mult, n, every, false, false, life, t, d -> {});
                RuneKit.later(t, () -> {
                    if (target.isRemoved() || !(target.level() instanceof ServerLevel lv)) return;
                    Vec3 b = V.up(Kit.bodyPoint(target), ((ii % 3) - 1) * 0.2);
                    RuneKit.plasmaOrb(lv, b, 0.45 + Kit.bodyPad(target) * 0.3, glow, Opt.of("life", 0.28, "vel", RuneKit.entVel(target)));
                    SkillFx.burst(lv, b, glow, 0.9, 0.2);
                    SkillFx.sparks(lv, b, core, 6, 4);
                    if (prev[0] != null) RuneKit.zap(lv, prev[0], b, glow, core, 0.08, 0.22); // 맞은 자리끼리 이어지는 번개 (연쇄)
                    prev[0] = b;
                    JobFx.hit(target, j, 0.8);
                    SkillFx.play(lv, "random.bowhit", b, 1.2, 0.5);
                    if (ii == n - 1) {
                        // 마지막 화살: 하늘에서 벼락 (그림 — 피해는 위의 줄 그대로)
                        ArrowFx.skyBolt(lv, target.position(), 6.5, glow, core);
                        RuneKit.zonePulse(lv, target.position(), glow, 1.8 + Kit.bodyPad(target), 0.35);
                        SkillFx.play(lv, "ambient.weather.lightning.impact", b, 1.5, 0.35);
                    }
                });
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 썬더 스텝

    /**
     * 썬더 스텝 (archer_wind): 번개가 되어 움직이는 쪽 (가만히 서 있으면 뒤 — 활을 겨눈 채 물러남, 그쪽이 막혔으면 다른 쪽) dist 칸 (벽 · 낭떠러지 앞까지 · 적 앞에서 멈춤) 을
     * steps 틱에 지나감 (떠난 자리에 벼락 · 번개빛 잔상 · 지나온 길을 잇는 번개) → for 틱 안의 다음 활 평타 shots 번이 각각 번개 화살 한 발 더 (JobTraitsMore — 두 번째 화살 =
     * 평타와 같은 피해) · 그동안 충전이 줄지 않음. 묶였거나 사방이 막혀 못 가면 쓰지 않음
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
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        JobTraitsMore.windStart(sp, (int) k.num("shots", 2), (int) k.num("for", 100));
        Kit.noFall(sp, 30);
        // 떠난 자리: 하늘에서 벼락 · 번개빛 잔상 (활을 겨눈 자세) · 몸에서 번개 구슬이 터짐
        ArrowFx.skyBolt(level, start, 7.0, glow, core);
        RuneKit.shadeClone(sp, level, start, look, 1, SHADE, glow, Opt.of("life", 0.45, "fade", 0.6, "alpha", 0.75));
        RuneKit.plasmaOrb(level, V.up(start, 1.0), 0.65, glow, Opt.of("life", 0.3));
        SkillFx.burst(level, V.up(start, 1.0), glow, 1.3, 0.25);
        RuneKit.zonePulse(level, start, glow, 1.8, 0.3);
        SkillFx.play(level, "item.trident.thunder", start, 1.8, 0.35);
        SkillFx.play(level, "mob.breeze.charge", start, 1.4, 0.7);
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
                // 지나온 길을 잇는 번개 (몸 높이 · 발밑) · 발밑 번개 구슬
                RuneKit.zap(d, V.up(prev, 0.95), V.up(to, 0.95), glow, core, 0.16, 0.32);
                RuneKit.zap(d, V.up(prev, 0.2), V.up(to, 0.2), glow, core, 0.08, 0.25);
                RuneKit.plasmaOrb(d, V.up(to, 0.5), 0.4, glow, Opt.of("life", 0.16));
                if (V.dot(dir, look) > 0.35) JobFx.speed(sp, j, ss == marks.size() - 1 ? 0.25 : 0.1, 0.75);
                SkillFx.fx(d, "rpg:arrow_spark", V.up(to, 0.3), Opt.of("dir", V.unit(new Vec3(-dir.x, 0.5, -dir.z)), "speed", 2.0, "size", 0.12, "grow", 0.6, "life", 0.35,
                        "color", core, "alpha", 1.0));
            });
        }
        RuneKit.later(steps, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Vec3 c = sp.position();
            Vec3 lf = Kit.facingOf(sp);
            // 닿은 자리: 번쩍임 · 발밑 고리 · 눈앞 고리 (1인칭) · 활에 번개가 감김 (덧쏘기 준비)
            SkillFx.burst(d, V.up(c, 1.0), core, 1.0, 0.25);
            RuneKit.zonePulse(d, c, glow, 2.2, 0.35);
            SkillFx.ring(d, V.add(V.up(c, 1.5), lf, 0.9), lf, glow, 1.0, 0.3, 2.5);
            RuneKit.plasmaOrb(d, RuneKit.bowHand(sp), 0.28, glow, Opt.of("life", 0.6, "vel", RuneKit.bodyVel(sp)));
            SkillFx.play(d, "block.amethyst_block.chime", c, 1.6, 0.6);
        });
        return true;
    }

    /** 썬더 스텝의 덧쏘기 (JobTraitsMore.onFired — follow 틱 뒤): 활에서 겨눈 적에게 번개 화살 (3D 빛 화살 · 활과 적을 잇는 번개 · 맞은 자리 번개 구슬) */
    static void windArrow(ServerPlayer sp, Jobs.Job j, LivingEntity target) {
        ServerLevel level = sp.level();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        Vec3 hand = RuneKit.bowHand(sp);
        Vec3 to = Kit.bodyPoint(target);
        int t = ArrowFx.shot(level, hand, to, 60, 1.2, 3, target, glow, core);
        RuneKit.zap(level, V.add(hand, sp.getViewVector(1F), 0.4), to, glow, core, 0.08, t / 20.0 + 0.1);
        RuneKit.later(t, () -> {
            if (target.isRemoved() || !(target.level() instanceof ServerLevel lv)) return;
            RuneKit.plasmaOrb(lv, Kit.bodyPoint(target), 0.45, glow, Opt.of("life", 0.25, "vel", RuneKit.entVel(target)));
            SkillFx.sparks(lv, Kit.bodyPoint(target), core, 5, 4);
        });
    }

    // ------------------------------------------------------------ 스톰 레인

    /**
     * 스톰 레인 (archer_rain): 조준한 곳 (range 칸) 위 8.5칸에 3D 먹구름 (덩이 다섯 — 밑이 번개빛, 구름 속 번쩍임 · 덩이 사이 번개) · 바닥 마법진 (반지름 radius 칸) →
     * first 틱부터 gap 틱마다 (waves 번) 구름에서 번개 화살이 쏟아짐 — 마법진 안의 적마다 한 물결에 두 발 (wave — 한 번에 넣고 숫자 per 줄: 3D 빛 화살 · 구름에서 몸까지 번개 ·
     * 번개 구슬), 빈 자리에도 화살이 꽂힘 · 물결마다 벼락 하나. 처음 맞은 적은 발밑에 꽂힌 번개 화살에 root 틱 묶임 (보스는 둔화)
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
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        java.util.Set<java.util.UUID> rooted = new java.util.HashSet<>(); // 0.50.0 발 묶기 — 적마다 한 번
        // 0.50.0 동작: 제자리에서 뛰어올라 (hop 칸) 하늘로 쏨
        JobMove.run(sp, JobMove.hop(0, 10, from -> from, k.num("hop", 1.0)));
        // 하늘로 쏘아 올림: 3D 빛 화살 셋 · 활에서 하늘로 번개
        for (int i = 0; i < 3; i++) {
            Vec3 from = RuneKit.bowHand(sp);
            Vec3 up = V.add(V.up(from, 9 + i), V.flat(sp.getViewVector(1F)), 2 + i * 0.6);
            ArrowFx.shot(level0, from, up, 45, 1.1, 0, null, glow, core);
        }
        RuneKit.zap(level0, RuneKit.bowHand(sp), V.up(RuneKit.bowHand(sp), 7), glow, core, 0.14, 0.3);
        SkillFx.play(level0, "random.bow", sp.position(), 0.9, 1.0);
        SkillFx.play(level0, "ambient.weather.thunder", center, 1.4, 0.4);
        // 바닥: 먹구름 그늘의 마법진 (돎) · 퍼지는 고리
        SkillFx.rune(level0, V.up(center, 0.05), Opt.of("size", r, "life", (end + 12) / 20.0, "color", glow, "alpha", 0.75, "rate", 40.0, "grow", 0.0));
        RuneKit.zonePulse(level0, center, glow, r, 0.45);
        // 하늘 (과녁 위 — 쏜 사람 쪽으로 조금, 8.5칸 위): 1인칭에서 떨어지는 화살이 화면에 들어오게 낮게
        boolean onMe = Math.hypot(sp.getX() - center.x, sp.getZ() - center.z) < 0.1;
        Vec3 toMe = onMe ? V.ZERO : V.unit(V.flat(V.sub(sp.position(), center)));
        Vec3 sky = V.up(V.add(center, toMe, 2.5), 8.5);
        // 3D 먹구름 (덩이 다섯 — 가운데 큰 덩이 · 둘레 넷)
        Vec3[] puffs = new Vec3[5];
        double[] halves = {2.7, 2.0, 1.9, 1.9, 1.8};
        puffs[0] = V.up(sky, 0.5);
        for (int i = 1; i < 5; i++) puffs[i] = V.up(V.add(sky, RuneKit.turn(new Vec3(1, 0, 0), i * 90 + 25), r * 0.55), (i % 2) * 0.3);
        RuneProps.clouds(level0, puffs, halves, CLOUD, glow, end + 14);
        // 구름 속 번쩍임 · 덩이 사이 번개 (6틱마다)
        for (int t = 2; t <= end + 6; t += 6) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                int a = (int) (Kit.rand() * 5);
                int b = (a + 1 + (int) (Kit.rand() * 4)) % 5;
                SkillFx.fx(level, "rpg:arrow_spark", V.up(puffs[a], -0.3), Opt.of("dir", V.UP, "size", 1.8, "grow", 1.4, "life", 0.16, "color", glow, "alpha", 0.85));
                RuneKit.zap(level, V.up(puffs[a], -0.6), V.up(puffs[b], -0.6), glow, core, 0.09, 0.16);
            });
        }
        int fall = 6;
        // 쏟아지는 화살 (2틱마다 셋 — 피해와 따로, 빈 자리에도: 룬 빛 화살 그림): 닿으면 불티 · 작은 번개 구슬
        for (int t = Math.max(0, first - fall); t <= end - 2; t += 2) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                for (int i = 0; i < 3; i++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 g = V.add(center, new Vec3(Math.cos(a), 0, Math.sin(a)), r * Math.sqrt(Kit.rand()));
                    Vec3 to = new Vec3(g.x, RuneKit.groundAt(level, g, center.y) + 0.05, g.z);
                    Vec3 from = V.add(sky, new Vec3(Kit.rand() * 2 - 1, Kit.rand() * 0.5 - 0.6, Kit.rand() * 2 - 1), 1.6);
                    RuneKit.flyArrow(level, from, to, V.len(V.sub(to, from)) / (fall / 20.0), glow, core, 0.6, 0.0);
                    RuneKit.later(fall, () -> {
                        SkillFx.sparks(level, V.up(to, 0.1), core, 2, 2);
                        if (RuneKit.fxQuota("stormRainOrb", 4)) RuneKit.plasmaOrb(level, V.up(to, 0.2), 0.3, glow, Opt.of("life", 0.15));
                    });
                }
            });
        }
        for (int w = 0; w < waves; w++) {
            int ww = w;
            // 적마다 per 발 (피해와 같은 틱에 닿게 fall 틱 전에 떨어지기 시작): 3D 빛 화살 하나 + 룬 화살 그림
            RuneKit.later(Math.max(0, first + w * gap - fall), () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                int n3d = 0;
                for (LivingEntity e : Kit.around(sp, center, r, 4)) {
                    Vec3 to = Kit.bodyPoint(e);
                    for (int p = 0; p < per; p++) {
                        Vec3 from = V.add(sky, new Vec3(Kit.rand() * 2 - 1, -0.6, Kit.rand() * 2 - 1), 1.2);
                        Vec3 at = V.up(to, (p - 0.5) * 0.4);
                        if (p == 0 && n3d++ < 6) ArrowFx.arrow3d(level, from, at, fall, 1.3, 4, e, glow, core);
                        else RuneKit.flyArrow(level, from, at, V.len(V.sub(at, from)) / (fall / 20.0), glow, core, 0.7, 0.0);
                    }
                }
                if (ww % 2 == 0) SkillFx.play(level, "random.bowhit", center, 0.9 + ww * 0.08, 0.8);
            });
            RuneKit.later(first + w * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                List<LivingEntity> in = Kit.around(sp, center, r, 4);
                // 물결마다 벼락 하나 (안의 적 하나 · 없으면 마법진 안 아무 곳)
                LivingEntity pick = in.isEmpty() ? null : in.get((int) (Kit.rand() * in.size()));
                Vec3 g0;
                if (pick != null) g0 = pick.position();
                else {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 g = V.add(center, new Vec3(Math.cos(a), 0, Math.sin(a)), r * 0.7 * Math.sqrt(Kit.rand()));
                    g0 = new Vec3(g.x, RuneKit.groundAt(level, g, center.y), g.z);
                }
                ArrowFx.skyBolt(level, g0, 8.0, glow, core);
                SkillFx.play(level, "ambient.weather.lightning.impact", g0, 1.3 + ww * 0.05, 0.3);
                for (LivingEntity e : in) {
                    boolean pin = rooted.add(e.getUUID());
                    JobKit.strikeSplit(sp, e, j, P, k.num("wave", 2.7), per, 2, false, false, life, 0, d -> {
                        if (!(e.level() instanceof ServerLevel lv)) return;
                        if (RuneKit.fxQuota("jobRain", 6)) {
                            JobFx.hit(e, j, 0.9);
                            RuneKit.plasmaOrb(lv, Kit.bodyPoint(e), 0.5 + Kit.bodyPad(e) * 0.3, glow, Opt.of("life", 0.22, "vel", RuneKit.entVel(e)));
                            RuneKit.zap(lv, V.up(sky, -0.8), Kit.bodyPoint(e), glow, core, 0.1, 0.18);
                        }
                        // 0.50.0 발 묶기: 처음 맞은 적은 발밑에 꽂힌 번개 화살에 root 틱 묶임 (보스는 둔화)
                        if (pin && k.num("root", 0) > 0) {
                            boolean boss = mmorpg.fabric.boss.Bosses.isBossEntity(e);
                            int rt = (int) k.num("root", 20);
                            RuneKit.effect(e, "slowness", rt, boss ? 1 : 10);
                            if (RuneKit.fxQuota("jobPin", 6)) {
                                Vec3 tm = V.flat(V.sub(sp.position(), e.position()));
                                Vec3 at = V.len(tm) > 0.1 ? V.add(e.position(), V.unit(tm), Kit.bodyPad(e) + 0.25) : e.position();
                                Vec3 tilt = V.unit(V.add(V.DOWN, V.len(tm) > 0.1 ? V.unit(tm) : V.ZERO, -0.35));
                                ArrowFx.pinStake(lv, V.up(at, 0.05), tilt, rt, 1.4, glow, core);
                                RuneKit.plasmaOrb(lv, V.up(e.position(), 0.25), 0.55 + Kit.bodyPad(e) * 0.4, glow, Opt.of("life", rt / 20.0, "fade", 0.3, "vel",
                                        RuneKit.entVel(e)));
                                SkillFx.ring(lv, V.up(e.position(), 0.1), V.UP, glow, 0.8 + Kit.bodyPad(e), rt / 20.0, 0);
                            }
                        }
                    });
                }
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 스톰 아이

    /**
     * 스톰 아이 (archer_focus — id 는 강화 기록이 이어지게 그대로): dur 틱 태세 — 충전 즉시 가득 · 움직여도 줄지 않음 · 활 평타 관통 (폭 width) · 머리 위를 따라다니는 3D 먹구름
     * (StormEye) — 활 평타가 겨눈 적을 맞힐 때마다 구름에서 그 적에게 벼락 (평타 × bite — JobTraitsMore.onWeaponHit). 그림: 활을 하늘로 들어 부름 (동작) · 활에서 하늘로 솟는
     * 번개 · 발밑 고리 · 둘레에서 솟는 번개빛
     */
    static boolean focus(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) k.num("dur", 200);
        JobTraits.startStance(sp, k, dur);
        JobTraitsMore.focusStart(sp, j);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        StormEye.summon(sp, j, dur);
        Vec3 sky = StormEye.centerOf(sp);
        RuneKit.zap(level, RuneKit.bowHand(sp), V.up(sky, -0.4), glow, core, 0.2, 0.4);
        RuneKit.zap(level, V.up(c, 1.2), V.up(sky, -0.4), glow, core, 0.1, 0.3);
        RuneKit.plasmaOrb(level, RuneKit.bowHand(sp), 0.35, glow, Opt.of("life", 0.5, "vel", RuneKit.bodyVel(sp)));
        RuneKit.zonePulse(level, c, glow, 2.6, 0.45);
        SkillFx.ring(level, V.up(c, 0.1), V.UP, glow, 2.2, 0.5, 0);
        for (int i = 0; i < 10; i++) {
            Vec3 at = V.add(V.up(c, 0.3 + Kit.rand() * 1.6), V.turnY(Kit.facingOf(sp), i * 36), 1.2 + Kit.rand() * 0.8);
            SkillFx.fx(level, "rpg:arrow_spark", at, Opt.of("dir", V.UP, "speed", 1.6, "size", 0.12, "grow", 0.5, "life", 0.6, "color", i % 2 == 0 ? core : glow,
                    "alpha", 0.95));
        }
        SkillFx.play(level, "ambient.weather.thunder", c, 1.3, 0.5);
        SkillFx.play(level, "block.amethyst_block.resonate", c, 1.6, 0.6);
        return true;
    }

    // ------------------------------------------------------------ 헤븐 보우

    /** 0.50.0 쓴 때의 땅 높이 (공중에서 쏴도 번개 화살은 쓴 때의 땅 위를 날아감 — 땅의 적을 그대로 꿰뚫음) */
    private static final java.util.Map<java.util.UUID, Double> ULT_GROUND = new java.util.concurrent.ConcurrentHashMap<>();

    /** 하늘의 폭풍의 활: 너비 · 몸 앞 칸 · 눈 위 칸 (뒤로 뛰어오른 자리에서 — 1인칭 화면 위쪽에 활이 다 들어오게) · 걸린 번개 화살 (3D 번개 창) 길이 */
    private static final double BOW_W = 6.6;
    private static final double BOW_AHEAD = 6.4;
    private static final double BOW_UP = 1.9;
    private static final double SPEAR_LEN = 4.6;
    /** 번개 창 모델의 쥔 곳에서 밑동까지 (칸 — 시위에 밑동을 걸 때 쥔 곳을 이만큼 위로) */
    private static final double SPEAR_BUTT = SPEAR_LEN * (RuneProps.THUNDER_SPEAR_GRIP[1] - (31.6 - RuneProps.THUNDER_SPEAR_LEN)) / RuneProps.THUNDER_SPEAR_LEN;

    /**
     * 헤븐 보우 (archer_ult — 2026-11-14, 스피릿 스탬피드 대신): 폭풍 남빛 하늘 (rpg:ult_tempest) → 뒤로 뛰어올라 draw 틱 동안 시위를 당기면 앞 하늘에 거대한 3D 폭풍의 활
     * (sky_bow — 나를 봄) 이 나타나 3D 번개 화살 (번개 창) 이 하늘을 겨눈 채 당겨지고 둘레에서 번개가 모임 → 놓으면 번개 화살이 하늘로 솟았다가 길 머리로 내리꽂혀
     * 보는 쪽 (수평) 앞 range 칸 (막힌 블록까지) 을 speed 칸/초로 꿰뚫으며 날아감 (폭 width — 지나가는 틱에 shot · 큰 숫자 · 띄움 · 땅이 갈라져 빛남) → 지나간 길을 따라
     * bolts 곳에 boltDelay 틱 뒤 차례로 벼락 (맞은 적마다 after — 그 적을 지난 틱 + boltDelay)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int draw = (int) k.num("draw", 24);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        int total = draw + (int) k.num("boltDelay", 18) + 30;
        Ult.sky(level0, c0, "rpg:ult_tempest", total + 40);
        // 0.50.0 동작 (사용자 "역동적으로"): 뒤로 뛰어올라 (back 칸 · lift 칸) 공중에서 시위를 당기고, 쏘는 반동으로 뒤로 밀리며 내려앉음
        JobMove.run(sp, JobMove.to(0, 8, from -> V.up(JobMove.ahead(sp, -k.num("back", 2.5), 0).apply(from), k.num("lift", 1.5)), JobMove.OUT),
                JobMove.hold(8, draw), JobMove.to(draw, draw + 8, from -> JobMove.landAhead(sp, -0.0, c0.y).apply(JobMove.ahead(sp, -1.5, 0).apply(from)),
                        JobMove.IN));
        ULT_GROUND.put(sp.getUUID(), c0.y);
        // 발밑 폭풍 문양 · 퍼지는 고리 · 소리 (시위 · 우레)
        SkillFx.rune(level0, V.up(c0, 0.05), Opt.of("size", 3.2, "life", draw / 20.0 + 0.4, "color", glow, "alpha", 0.8, "rate", 90.0, "grow", 0.1));
        RuneKit.zonePulse(level0, c0, glow, 3.4, 0.5);
        SkillFx.play(level0, "crossbow.loading.start", c0, 0.7, 1.0);
        SkillFx.play(level0, "ambient.weather.thunder", c0, 1.1, 0.6);
        StormBow bow = new StormBow(sp, draw, life, glow, core);
        RuneKit.later(draw, () -> {
            if (RuneKit.sameLife(sp, life)) release(sp, j, k, P, glow, core, life, bow);
            else bow.vanish();
        });
        return true;
    }

    /**
     * 하늘의 3D 폭풍의 활 (sky_bow · 너비 BOW_W — 앞 하늘에서 나를 봄) · 걸린 3D 번개 화살 (thunder_spear · SPEAR_LEN — 하늘을 겨눔): 커지며 나타나 draw 틱 동안 시위
     * (활 끝 → 오늬 빛줄 둘) 가 당겨지고 둘레에서 번개가 화살촉으로 모임 (2틱마다 몸 · 보는 쪽을 따라감 — 뒤로 뛰어오른 자리 기준). 놓으면 loose
     */
    private static final class StormBow {
        final ServerPlayer sp;
        final Vfx.Fx fx;
        final Vfx.Prop prop;
        final RuneSwords.Blade spear;
        final double sc;
        final int draw;
        final double[] glow;
        final double[] core;
        Vec3 center;
        Vec3 face;

        StormBow(ServerPlayer sp, int draw, String life, double[] glow, double[] core) {
            this.sp = sp;
            this.draw = draw;
            this.glow = glow;
            this.core = core;
            ServerLevel level = sp.level();
            this.center = centerFor(sp, 0);
            this.face = V.scale(Kit.facingOf(sp), -1);
            this.fx = RuneProps.fx(level, center, draw + 40);
            this.sc = RuneProps.scaleFor(BOW_W, RuneProps.SKY_BOW_W);
            this.prop = RuneProps.prop(fx, RuneProps.SKY_BOW, center, new double[] {8, 8, 8}, RuneProps.along(V.UP, face), sc * 0.25, Vfx.mix(glow, core, 0.35),
                    RuneKit.WHITE);
            fx.on(1, () -> prop.size(sc, 6));
            double rest = RuneProps.BOW_TIP[1] * sc / 16.0;
            this.spear = RuneSwords.spear(fx, V.up(center, rest - 0.2 + SPEAR_BUTT), V.UP, face, SPEAR_LEN, 0.2, glow, core);
            fx.on(3, () -> spear.grow(1, 4));
            for (int t = 2; t <= draw; t += 2) {
                final int tt = t;
                fx.on(t, () -> {
                    if (!RuneKit.sameLife(sp, life) || sp.isRemoved()) return;
                    pose(tt);
                });
            }
        }

        /** 활 가운데: 몸 (뒤로 뛰어오르는 중이면 그 자리) 앞 BOW_AHEAD · 눈 위 BOW_UP */
        static Vec3 centerFor(ServerPlayer sp, int t) {
            return V.up(V.add(sp.position(), Kit.facingOf(sp), BOW_AHEAD), sp.getEyeHeight() + BOW_UP);
        }

        /** 당김 t 틱: 활 · 화살을 몸 · 보는 쪽에 맞춤 · 시위 · 모여 드는 번개 */
        void pose(int t) {
            ServerLevel level = sp.level();
            double q = Math.min(1, t / (double) draw);
            center = centerFor(sp, t);
            face = V.scale(Kit.facingOf(sp), -1);
            prop.to(center, RuneProps.along(V.UP, face), sc * (1 + 0.04 * Math.sin(t * 0.9)), 2);
            double px = sc / 16.0;
            double rest = RuneProps.BOW_TIP[1] * px;
            double pull = rest - 0.2 - 2.0 * (1 - (1 - q) * (1 - q));
            Vec3 nock = V.up(center, pull);
            spear.pose(V.up(nock, SPEAR_BUTT), V.UP, face, 2);
            Vec3 side = V.cross(face, V.UP);
            Vec3 tipA = V.up(V.add(center, side, RuneProps.BOW_TIP[0] * px), RuneProps.BOW_TIP[1] * px);
            Vec3 tipB = V.up(V.add(center, side, -RuneProps.BOW_TIP[0] * px), RuneProps.BOW_TIP[1] * px);
            for (Vec3 tip : new Vec3[] {tipA, tipB}) {
                Vec3 dv = V.sub(nock, tip);
                double L = V.len(dv);
                SkillFx.fx(level, "rpg:light_cut", V.add(tip, dv, 0.5), Opt.of("dir", V.unit(dv), "size", L / 2, "thick", 0.09, "life", 0.13, "color", core, "alpha", 1.0));
            }
            // 둘레에서 화살촉으로 모여 드는 번개 (당길수록 가까이) · 촉의 번개 구슬
            Vec3 tip = spear.tip();
            double a = t * 47.0;
            Vec3 o = V.add(tip, V.add(V.scale(side, Math.cos(Math.toRadians(a))), V.UP, 0.6 + Math.sin(Math.toRadians(a)) * 0.5), 3.2 - 1.8 * q);
            RuneKit.zap(level, o, tip, glow, core, 0.08 + 0.06 * q, 0.14);
            RuneKit.plasmaOrb(level, tip, 0.35 + 0.45 * q, glow, Opt.of("life", 0.13, "fade", 0.0));
            if (t % 6 == 0) SkillFx.play(level, "crossbow.loading.middle", sp.position(), 0.8 + q * 0.6, 0.6);
            if (t >= draw) {
                ArrowFx.glint(level, tip, 1.4, 0.3, RuneKit.WHITE, Opt.empty());
                SkillFx.play(level, "block.amethyst_block.chime", sp.position(), 1.6, 0.9);
            }
        }

        /** 놓음: 시위가 튕기고 번개 화살이 하늘로 솟음 (3틱) · 활은 잠시 남았다 작아지며 사라짐 */
        void loose() {
            ServerLevel level = sp.level();
            double px = sc / 16.0;
            Vec3 side = V.cross(face, V.UP);
            Vec3 tipA = V.up(V.add(center, side, RuneProps.BOW_TIP[0] * px), RuneProps.BOW_TIP[1] * px);
            Vec3 tipB = V.up(V.add(center, side, -RuneProps.BOW_TIP[0] * px), RuneProps.BOW_TIP[1] * px);
            Vec3 dv = V.sub(tipB, tipA);
            SkillFx.fx(level, "rpg:light_cut", V.add(tipA, dv, 0.5), Opt.of("dir", V.unit(dv), "size", V.len(dv) / 2, "thick", 0.12, "life", 0.3, "color", core, "alpha", 1.0));
            int t0 = fx.age();
            spear.pose(V.up(center, 24), V.UP, face, 3);
            fx.on(t0 + 3, spear::remove);
            RuneKit.zap(level, V.up(center, 0.5), V.up(center, 14), glow, core, 0.3, 0.35);
            SkillFx.burst(level, center, core, 2.2, 0.3);
            fx.on(t0 + 8, () -> {
                prop.size(sc * 0.1, 7);
                prop.move(V.up(center, 1.4), 7);
            });
            fx.on(t0 + 15, prop::remove);
        }

        void vanish() {
            int t0 = fx.age();
            spear.remove();
            prop.size(sc * 0.1, 5);
            fx.on(t0 + 6, prop::remove);
        }
    }

    /**
     * 놓음: 하늘의 활에서 번개 화살이 솟음 → 길 머리 (몸 앞 0.6칸) 로 내리꽂혀 (2틱) 길을 따라 speed 칸/초로 날아감 (3D 번개 창 7칸 · 뒤를 잇는 번개 · 갈라져 빛나는 땅) — 길 위의 적
     * shot (띄움) → 지나간 자리 bolts 곳에 boltDelay 틱 뒤 벼락 (맞은 적은 after)
     */
    private static void release(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, double[] glow, double[] core, String life, StormBow bow) {
        ServerLevel level = sp.level();
        Double g = ULT_GROUND.remove(sp.getUUID());
        double gy = g == null ? sp.getY() : Math.min(sp.getY(), g);
        Vec3 f = Kit.facingOf(sp);
        Vec3 base = new Vec3(sp.getX(), gy, sp.getZ());
        Vec3 from = V.add(V.up(base, 1.2), f, 0.6);
        double len = Math.max(3, RuneKit.rayLength(level, from, f, k.num("range", 40)));
        double speed = Math.max(10, k.num("speed", 18));
        double half = k.num("width", 3) / 2;
        int n = Math.max(1, (int) k.num("bolts", 20));
        int boltDelay = Math.max(10, (int) k.num("boltDelay", 18)); // 같은 적 — 몹 무적 시간 (10틱) 을 넘게
        int run = Math.max(1, (int) Math.round(len / speed * 20));
        Vec3 ground0 = V.add(base, f, 0.6);
        Vec3 right = V.rightOf(f);
        bow.loose();
        Ult.flash(level, from, glow, Opt.empty(), sp);
        SkillFx.play(level, "item.trident.thunder", from, 1.0, 1.0);
        SkillFx.play(level, "random.bow", from, 0.6, 1.0);
        SkillFx.play(level, "ambient.weather.lightning.impact", from, 0.9, 0.8);
        // 3D 번개 화살 (번개 창 7칸): 하늘 (길 머리 위 9칸 · 조금 뒤) 에서 길 머리로 내리꽂힘 → 길을 따라 (높이 1.3) 틱마다 날아감 → 끝에 박혀 떨다가 흩어짐
        double sl = 7.0;
        double reachS = sl * (31.6 - RuneProps.THUNDER_SPEAR_GRIP[1]) / RuneProps.THUNDER_SPEAR_LEN;
        Vfx.Fx fx = RuneProps.fx(level, from, run + 40);
        Vec3 top = V.up(V.add(from, f, -1.0), 9);
        Vec3 dive = V.unit(V.sub(V.up(from, 0.1), top));
        Vec3 sface = V.unit(V.add(right, V.UP, 0.8));
        RuneSwords.Blade spear = RuneSwords.spear(fx, V.sub(top, V.scale(dive, reachS)), dive, sface, sl, 0.6, glow, core);
        fx.on(1, () -> {
            spear.grow(1, 1);
            spear.pose(V.sub(V.up(from, 0.1), V.scale(dive, reachS)), dive, sface, 1);
        });
        fx.on(2, () -> RuneKit.zap(level, top, V.up(from, 0.2), glow, core, 0.3, 0.3));
        Vec3[] lastTip = {from};
        for (int t = 2; t <= run + 1; t++) {
            final int tt = t;
            double dd = Math.min(len, speed * (t - 1) / 20.0);
            Vec3 tip = V.add(V.up(from, 0.1), f, dd);
            fx.on(t, () -> {
                spear.pose(V.sub(tip, V.scale(f, reachS)), f, sface, 1);
                // 뒤를 잇는 번개 · 촉의 번개 구슬
                RuneKit.zap(level, lastTip[0], tip, glow, core, 0.2, 0.35);
                RuneKit.plasmaOrb(level, tip, 0.9, glow, Opt.of("life", 0.1, "fade", 0.0));
                lastTip[0] = tip;
                if (tt % 4 == 0) SkillFx.play(level, "item.trident.riptide_1", tip, 1.4, 0.4);
            });
        }
        // 끝: 박혀 떨다가 흩어짐 · 큰 번쩍임 (흔들림 없음)
        Vec3 end = V.add(V.up(from, 0.1), f, len);
        fx.on(run + 2, () -> {
            Vec3 gp = new Vec3(end.x, RuneKit.groundAt(level, V.up(end, 1.5), gy), end.z);
            BowFx.bigImpact(level, gp, glow, core, 3.2);
            ArrowFx.skyBolt(level, gp, 9.0, glow, core);
            SkillFx.play(level, "random.explode", gp, 1.2, 0.6);
        });
        fx.on(run + 14, () -> spear.grow(0.1, 6));
        fx.on(run + 21, spear::remove);
        // 지나간 길: 땅이 갈라져 빛남 (지나가는 틱 — 2.4칸마다) · 불티
        for (double dd = 1.5; dd <= len; dd += 2.4) {
            Vec3 at = V.add(ground0, f, dd);
            int delay = (int) Math.round(dd / speed * 20) + 1;
            RuneKit.later(delay, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 gp = new Vec3(at.x, RuneKit.groundAt(sp.level(), V.up(at, 1.5), at.y), at.z);
                RuneKit.glowCrack(sp.level(), V.up(gp, 0.04), 1.3, glow, core, Opt.of("life", boltDelay / 20.0 + 0.4, "spin", Math.toDegrees(Math.atan2(f.z, f.x))));
                SkillFx.sparks(sp.level(), V.up(gp, 0.15), core, 3, 2.0);
            });
        }
        // 벼락 bolts 곳: 길을 n 칸으로 나눈 자리마다 화살이 지나간 틱 + boltDelay (가까운 쪽부터 — 그림, 맞은 적의 피해는 아래)
        double seg = len / n;
        for (int i = 0; i < n; i++) {
            double dd = seg * (i + 0.5);
            double side = (i % 2 == 0 ? -1 : 1) * (0.3 + (i * 37 % 10) / 10.0 * (half - 0.3));
            Vec3 at = V.add(V.add(ground0, f, dd), right, side);
            int born = (int) Math.round(dd / speed * 20);
            RuneKit.later(born + boltDelay, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 gp = new Vec3(at.x, RuneKit.groundAt(lv, V.up(at, 1.5), at.y), at.z);
                ArrowFx.skyBolt(lv, gp, 9.5, glow, core);
                if (RuneKit.fxQuota("heavenBolt", 4)) {
                    RuneKit.zonePulse(lv, gp, glow, 1.6, 0.3);
                    SkillFx.play(lv, "ambient.weather.lightning.impact", gp, 1.1 + Kit.rand() * 0.4, 0.3);
                }
            });
        }
        for (Kit.LineHit h : RuneKit.lineTargets(sp, from, f, len, half)) {
            LivingEntity e = h.e();
            int hitT = Math.max(1, (int) Math.round(h.t() / speed * 20));
            JobKit.strike(sp, e, j, P, k.num("shot", 40), false, true, life, hitT, d -> {
                RuneKit.knock(e, f, 0.5, 0.35);
                if (RuneKit.fxQuota("jobHeavenHit", 6) && e.level() instanceof ServerLevel lv) {
                    JobFx.hit(e, j, 1.6);
                    RuneKit.plasmaOrb(lv, Kit.bodyPoint(e), 0.9 + Kit.bodyPad(e) * 0.4, glow, Opt.of("life", 0.3, "vel", RuneKit.entVel(e)));
                    SkillFx.shock(lv, e.position(), glow, 1.8 + Kit.bodyPad(e), 0.35);
                }
            });
            JobKit.strike(sp, e, j, P, k.num("after", 20), false, false, life, hitT + boltDelay, d -> {
                if (!(e.level() instanceof ServerLevel lv)) return;
                ArrowFx.skyBolt(lv, e.position(), 9.5, glow, core);
                RuneKit.zap(lv, V.up(e.position(), 9), Kit.bodyPoint(e), glow, core, 0.24, 0.25);
                if (RuneKit.fxQuota("jobHeavenBolt", 6)) JobFx.hit(e, j, 1.1);
            });
        }
    }
}
