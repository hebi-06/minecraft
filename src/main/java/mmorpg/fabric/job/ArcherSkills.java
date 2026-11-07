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
 * 0.28.0 궁수 직업 스킬 (jobs.json archer — 연두 · 금빛, 특성 「정령 교감」 (2026-11-07 재설계안 3판 — 정령 궁수: 호흡에서 이름 · 그림만)): 스위프트 볼리 · 게일 스텝 ·
 * 애로우 템페스트 · 와일드 헌트 (늑대 정령 — SpiritWolves) · 스피릿 스탬피드 (사슴 정령 왕 · 늑대 무리 · 정령 꽃). 화살은 공격 피해.
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
        // 0.50.0 동작: 쏘며 뒤로 짧게 뜀 (back 칸) — 화살은 손에서 그대로
        JobMove.run(sp, JobMove.hop(0, Math.max(6, (n - 1) * every + 3), JobMove.ahead(sp, -k.num("back", 1.5), 0), 0.5));
        if (n > (int) k.num("arrows", 3)) JobSystem.note(sp, "§a교감 " + breath + " §7→ " + n + "발");
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
        java.util.Set<java.util.UUID> rooted = new java.util.HashSet<>(); // 0.50.0 발 묶기 — 적마다 한 번
        // 0.50.0 동작: 제자리에서 뛰어올라 (hop 칸) 하늘로 쏨
        JobMove.run(sp, JobMove.hop(0, 10, from -> from, k.num("hop", 1.0)));
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
                    boolean pin = rooted.add(e.getUUID());
                    JobKit.strikeSplit(sp, e, j, P, k.num("wave", 2.7), per, 2, false, false, life, 0, d -> {
                        if (RuneKit.fxQuota("jobRain", 6)) JobFx.hit(e, j, 0.9);
                        // 0.50.0 발 묶기: 처음 맞은 적은 발밑에 꽂힌 화살에 root 틱 묶임 (보스는 둔화)
                        if (pin && k.num("root", 0) > 0) {
                            boolean boss = mmorpg.fabric.boss.Bosses.isBossEntity(e);
                            RuneKit.effect(e, "slowness", (int) k.num("root", 20), boss ? 1 : 10);
                            // 0.50.0 꽂힌 화살 (재설계표 — rfx_jpinned: 발밑 (쏜 사람 쪽 몸 앞) 에 꽂힌 화살 · 묶는 빛 고리)
                            if (RuneKit.fxQuota("jobPin", 6)) {
                                Vec3 toMe = V.flat(V.sub(sp.position(), e.position()));
                                Vec3 at = V.len(toMe) > 0.1 ? V.add(e.position(), V.unit(toMe), Kit.bodyPad(e) + 0.15) : e.position();
                                JobFx.pinned(sp.level(), V.up(at, 0.02), 0.42 + Kit.bodyPad(e) * 0.25, j, k.num("root", 20) / 20.0, Opt.of("vel", RuneKit.entVel(e)));
                            }
                        }
                    });
                }
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 와일드 헌트 (2026-11-07 재설계안 3판 — 이글 아이 대신, 정령 궁수)

    /**
     * 와일드 헌트 (archer_focus — id 는 강화 기록이 이어지게 그대로): dur 틱 태세 — 교감 즉시 가득 · 움직여도 줄지 않음 · 활 평타 관통 (폭 width) · 늑대 정령 wolves 마리
     * (진짜 몹 — SpiritWolves) 가 곁에서 달리다 활 평타가 겨눈 적을 맞힐 때마다 그 적에게 뛰어올라 물어뜯음 (평타 × bite — JobTraitsMore.onWeaponHit).
     * 그림: 활을 하늘로 들어 부름 (동작) · 발밑 정령빛 고리 · 둘레에서 감겨 드는 금빛 · 늑대가 깨어나며 울부짖음 · 잎이 흩날림
     */
    static boolean focus(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) k.num("dur", 200);
        JobTraits.startStance(sp, k, dur);
        JobTraitsMore.focusStart(sp, j);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        JobFx.aura(sp, j, 2.6, 1.0, 0.9);
        JobFx.ring(level, V.up(c, 0.06), 2.4, j, false, 0.55, Opt.empty());
        JobFx15.halo(level, V.up(c, 0.08), 3.2, 0.6, Opt.of("alpha", 0.8), JobKit.glow(j), JobKit.core(j));
        for (int i = 0; i < 10; i++) {
            Vec3 at = V.add(V.up(c, 0.3 + Kit.rand() * 1.6), V.turnY(Kit.facingOf(sp), i * 36), 1.2 + Kit.rand() * 0.8);
            SkillFx.fx(level, "rpg:arrow_spark", at, Opt.of("dir", V.UP, "speed", 1.4, "size", 0.12, "grow", 0.5, "life", 0.6, "color", i % 2 == 0 ? JobKit.core(j)
                    : JobKit.glow(j), "alpha", 0.95));
        }
        SpiritWolves.summon(sp, j, Math.max(1, (int) k.num("wolves", 2)), dur);
        SkillFx.play(level, "item.goat_horn.sound.1", c, 1.5, 0.45);
        SkillFx.play(level, "block.amethyst_block.resonate", c, 1.6, 0.6);
        return true;
    }

    // ------------------------------------------------------------ 관천일시

    /**
     * 0.47.0 눕힌 빛의 활 자리: 몸 앞 BOW_AHEAD 칸 · 높이 (눈 0.4칸 아래 — 1인칭에서 내려다봄). 사진첩: 눈 0.75칸 아래 · 2.2칸 앞은 1인칭 화면 아래 체력 줄 · 핫바에
     * 활의 반이 가렸고, 3인칭에서는 거의 옆선으로 보였음 → 조금 높이 · 멀리 · 더 크게, 쏘는 쪽을 30° 들어 위에서 활 모양이 보이게. 3인칭 (내 3인칭 · 다른 사람) 에는
     * 더 큰 활 (팔이 몸 양옆으로 나옴 — BOW_3P)
     */
    private static final double BOW_AHEAD = 2.6;
    private static final double BOW_PITCH = 30;
    /** 3인칭 활: 앞 칸 · 눈 아래 칸 · 크기 (1인칭 활은 1.3) */
    private static final double[] BOW_3P = {2.4, 0.3, 1.9};

    private static double bowY(ServerPlayer sp) {
        return sp.getEyeHeight() - 0.4;
    }

    /**
     * 스피릿 스탬피드 (archer_ult — 2026-11-07 재설계안 3판, 헤븐 피어서 대신 · 정령 궁수): 숲의 정령 하늘 (rpg:ult_spirit) → 뒤로 뛰어올라 draw 틱 동안 빛의 활을 당기면
     * 등 뒤에 금빛 뿔의 사슴 정령 왕이 빛을 모아 깨어남 (rfx_jstagf) → 놓으면 화살이 사슴 정령 왕 · 늑대 정령 무리가 되어 보는 쪽 (수평) 앞 range 칸 (막힌 블록까지) 을
     * speed 칸/초로 내달림 (폭 width — 지나가는 틱에 shot · 큰 숫자 · 짓밟아 띄움) → 지나간 자리에 정령 꽃 blooms 송이가 피어 bloomDelay 틱 뒤 차례로 터짐 (맞은 적마다
     * after — 그 적을 지난 틱 + bloomDelay). 1인칭은 멀어지는 사슴 왕 뒷모습 · 양옆 늑대 판 (옆모습 판이 가는 쪽과 나란해 보이지 않으므로)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int draw = (int) k.num("draw", 24);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        int total = draw + (int) k.num("bloomDelay", 18) + 30;
        Ult.sky(level0, c0, "rpg:ult_spirit", total + 30);
        // 0.50.0 동작 (사용자 "역동적으로"): 뒤로 뛰어올라 (back 칸 · lift 칸) 공중에서 시위를 당기고, 쏘는 반동으로 뒤로 밀리며 내려앉음
        JobMove.run(sp, JobMove.to(0, 8, from -> V.up(JobMove.ahead(sp, -k.num("back", 2.5), 0).apply(from), k.num("lift", 1.5)), JobMove.OUT),
                JobMove.hold(8, draw), JobMove.to(draw, draw + 8, from -> JobMove.landAhead(sp, -0.0, c0.y).apply(JobMove.ahead(sp, -1.5, 0).apply(from)),
                        JobMove.IN));
        ULT_GROUND.put(sp.getUUID(), c0.y);
        JobFx.aura(sp, j, 2.8, draw / 20.0 + 0.3, 0.9);
        JobFx.ring(level0, V.up(c0, 0.06), 3.0, j, false, draw / 20.0 + 0.2, Opt.of("alpha", 0.8));
        SkillFx.play(level0, "crossbow.loading.start", c0, 0.7, 1.0);
        SkillFx.play(level0, "beacon.power", c0, 1.6, 0.8);
        // 2026-11-07 등 뒤에 사슴 정령 왕이 깨어남 (빛이 감겨 들며 모임 → 서 있음 — 놓을 때 고개를 들고 번쩍), 3인칭 · 다른 사람 (1인칭은 등 뒤라 보이지 않음)
        Vec3 f0 = Kit.facingOf(sp);
        Vec3 stagAt = new Vec3(c0.x - f0.x * 5.4, c0.y, c0.z - f0.z * 5.4); // 뒤로 뛰어 내려앉는 자리 (약 4칸 뒤) 너머
        double stagH = k.num("stagH", 4.4);
        JobFx16.stagFront(level0, stagAt, stagH, 0, 3, 0.45, false, Opt.of("alpha", 0.95), glow, core);
        RuneKit.later(9, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx16.stagFront(sp.level(), stagAt, stagH, 3, 3, Math.max(0.1, (draw - 9) / 20.0), true, Opt.of("alpha", 0.95, "fade",
                    0.05), glow, core);
        });
        RuneKit.later(draw, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx16.stagFront(sp.level(), stagAt, stagH, 6, 2, 0.3, false, Opt.of("alpha", 1.0), glow, core);
        });
        JobFx15.halo(level0, V.up(stagAt, 0.08), 2.4, draw / 20.0 + 0.2, Opt.of("alpha", 0.7), glow, core);
        // 0.47.0 입체: 가슴 앞 2.6칸에 눕힌 빛의 활 (팔이 좌우 · 쏘는 쪽이 앞으로 30° 들림 — 주인을 따라감) 이 커지며 시위가 반 → 끝까지 당겨지고 빛의 화살이 걸림.
        // 사진첩: 세운 활은 1인칭 · 3인칭 모두 뒤에서 보아 활대 옆선 한 줄로만 보였음 → 눕혀 위에서 내려다보면 휜 활대 · 당긴 시위 · 화살이 다 보임
        double pitch = Math.toRadians(BOW_PITCH);
        Opt bow = Opt.of("follow", (double) sp.getId(), "dir", new Vec3(1, 0, 0), "face", new Vec3(0, Math.sin(pitch), Math.cos(pitch)), "life", draw / 20.0 + 0.4,
                "anim", "draw", "animSpeed", 20.0 / Math.max(4, draw), "pop", 0.25, "fadeOut", 0.3, "glowAlpha", 0.5, "shine", 0.3);
        int[] bowIds = {
            JobFx3d.spawn(level0, "light_bow", new Vec3(0, bowY(sp), BOW_AHEAD), j, bow.with("scale", 1.3, "grow", 0.35, "view", 2.0)), // 내 1인칭
            JobFx3d.spawn(level0, "light_bow", new Vec3(0, sp.getEyeHeight() - BOW_3P[1], BOW_3P[0]), j, bow.with("scale", BOW_3P[2], "grow", 0.3, "view", 1.0)), // 3인칭
        };
        JobFx3d.guard(sp, life, bowIds, draw + 8);
        RuneKit.later(draw, () -> {
            for (int id : bowIds) mmorpg.fabric.Fx3d.anim(sp.level(), id, "loose", false, sp.position()); // 놓음: 시위가 돌아오며 떨림
        });
        // 시위를 당김: 둘레에서 빛이 모여 듦 (활은 입체)
        for (int t = 0; t < draw; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                double q = (tt + 2.0) / draw;
                Vec3 look = sp.getViewVector(1F);
                // 활 자리 (가슴 앞 2.2칸) 로 양옆 위에서 빛이 모여 듦
                Vec3 at = V.up(V.add(sp.position(), Kit.facingOf(sp), BOW_AHEAD), bowY(sp));
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

    /** 0.50.0 쓴 때의 땅 높이 (공중에서 쏴도 정령 무리는 쓴 때의 땅을 달림 — 땅의 적을 그대로 짓밟음) */
    private static final java.util.Map<java.util.UUID, Double> ULT_GROUND = new java.util.concurrent.ConcurrentHashMap<>();

    /** 놓음: 화살이 사슴 정령 왕 · 늑대 정령 무리가 되어 내달림 (길 위의 적 shot) → 지나간 자리의 정령 꽃이 차례로 터짐 (after) */
    private static void release(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, double[] glow, double[] core, String life) {
        ServerLevel level = sp.level();
        Double g = ULT_GROUND.remove(sp.getUUID());
        double gy = g == null ? sp.getY() : Math.min(sp.getY(), g);
        Vec3 f = Kit.facingOf(sp);
        Vec3 base = new Vec3(sp.getX(), gy, sp.getZ());
        Vec3 from = V.add(V.up(base, 1.2), f, 0.6);
        double len = Math.max(3, RuneKit.rayLength(level, from, f, k.num("range", 40)));
        double speed = Math.max(10, k.num("speed", 40));
        double half = k.num("width", 3) / 2;
        int n = Math.max(1, (int) k.num("blooms", 20));
        int bloomDelay = Math.max(10, (int) k.num("bloomDelay", 18)); // 같은 적 — 몹 무적 시간 (10틱) 을 넘게
        double run = len / speed + 0.05;
        Vec3 ground0 = V.add(base, f, 0.6);
        Vec3 right = V.rightOf(f);
        // 사슴 정령 왕 (가운데 — 옆모습 판 · 1인칭은 뒷모습) · 늑대 정령 넷 (양옆 · 조금 뒤)
        JobFx16.stagRun(level, V.add(ground0, f, -0.6), f, k.num("stagH", 4.4) * 0.9, speed, run, JobFx.hide1p(sp), glow, core);
        JobFx16.stagFront(level, V.add(ground0, f, 0.8), 3.2, 8, 4, run, true, JobFx.only1p(sp).with("speed", speed, "dir", f, "fps", 12.0), glow, core);
        double[][] pack = {{-1.3, -0.9}, {1.3, -0.9}, {-2.3, -2.0}, {2.3, -2.0}};
        for (double[] w : pack) {
            Vec3 at = V.add(V.add(ground0, right, w[0]), f, w[1]);
            JobFx16.wolfRun(level, at, f, 1.9, speed, run, JobFx.hide1p(sp), glow, core);
            JobFx16.wolf1p(level, V.up(at, 0.5), f, 1.7, speed, run, w[0] > 0, JobFx.only1p(sp), glow, core);
        }
        // 잔상 (영상 42332661771 뒤 — 내달리는 무리가 빨라 잘 안 보였음): 3 · 6틱 늦게 같은 길을 따르는 옅은 사슴 왕 · 늑대 (3인칭 · 다른 사람)
        for (int echo = 1; echo <= 2; echo++) {
            int ee = echo;
            RuneKit.later(echo * 3, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double a = ee == 1 ? 0.5 : 0.26;
                double left = Math.max(0.1, run - ee * 0.15);
                JobFx16.stagRun(sp.level(), V.add(ground0, f, -0.6), f, k.num("stagH", 4.4) * 0.9, speed, left, JobFx.hide1p(sp).with("alpha", a), glow, core);
                for (double[] w : pack) {
                    Vec3 at = V.add(V.add(ground0, right, w[0]), f, w[1]);
                    JobFx16.wolfRun(sp.level(), at, f, 1.9, speed, left, JobFx.hide1p(sp).with("alpha", a), glow, core);
                }
            });
        }
        // 놓은 활 (번쩍) · 내달리는 길의 정령빛 띠 · 번쩍임 · 소리 (뿔피리 울부짖음 · 우레 같은 발굽)
        Vec3 bowAt = V.up(V.add(sp.position(), f, BOW_AHEAD), bowY(sp));
        JobFx.burst(level, bowAt, 0.9, j, 0.25, Opt.of("alpha", 0.8));
        Vec3 end = V.add(from, f, len);
        JobFx.beam(level, V.add(RuneKit.bowHand(sp), sp.getViewVector(1F), 1.0), V.add(from, f, Math.min(len, 6)), j, 1.6, 0.35, Opt.of("alpha", 0.7));
        Ult.flash(level, from, glow, Opt.empty(), sp);
        SkillFx.play(level, "item.goat_horn.sound.1", from, 1.3, 1.0);
        SkillFx.play(level, "entity.ravager.roar", from, 1.4, 0.6);
        SkillFx.play(level, "random.bow", from, 0.6, 1.0);
        // 발굽 자국 빛 (지나가는 틱마다 — 땅의 금빛 반짝임 · 흙먼지 대신 잎)
        for (double dd = 1.5; dd <= len; dd += 1.6) {
            Vec3 at = V.add(ground0, f, dd);
            int delay = (int) Math.round(dd / speed * 20);
            RuneKit.later(delay, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 gp = new Vec3(at.x, RuneKit.groundAt(sp.level(), V.up(at, 1.5), at.y), at.z);
                SkillFx.sparks(sp.level(), V.up(gp, 0.15), core, 3, 2.0);
                if (RuneKit.fxQuota("jobStampede", 8)) JobFx.ring(sp.level(), V.up(gp, 0.05), 0.9, j, false, 0.3, Opt.of("alpha", 0.6));
                if (((int) (at.x * 7 + at.z * 3)) % 3 == 0) SkillFx.play(sp.level(), "entity.horse.gallop", gp, 1.3, 0.5);
            });
        }
        // 정령 꽃: 길을 n 칸으로 나눈 자리마다 사슴 왕이 지나가는 틱에 피어 bloomDelay 틱 뒤 터짐 (가까운 쪽부터)
        double seg = len / n;
        for (int i = 0; i < n; i++) {
            double dd = seg * (i + 0.5);
            double side = (i % 2 == 0 ? -1 : 1) * (0.4 + (i * 37 % 10) / 10.0 * (half - 0.4));
            Vec3 at = V.add(V.add(ground0, f, dd), right, side);
            int born = (int) Math.round(dd / speed * 20);
            RuneKit.later(born, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 gp = new Vec3(at.x, RuneKit.groundAt(lv, V.up(at, 1.5), at.y), at.z);
                JobFx16.bloom(lv, gp, 1.3, 0, 5, bloomDelay / 20.0, false, Opt.of("alpha", 0.95, "fade", 0.0), glow, core);
                RuneKit.later(bloomDelay, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    JobFx16.bloom(sp.level(), gp, 1.5, 5, 3, 0.35, false, Opt.empty(), glow, core);
                    if (RuneKit.fxQuota("jobBloom", 6)) SkillFx.play(sp.level(), "block.amethyst_block.break", gp, 1.6, 0.6);
                });
            });
        }
        RuneKit.later((int) Math.round(len / speed * 20), () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.burst(sp.level(), end, 1.6, j, 0.4, Opt.empty());
        });
        for (Kit.LineHit h : RuneKit.lineTargets(sp, from, f, len, half)) {
            LivingEntity e = h.e();
            int hitT = Math.max(1, (int) Math.round(h.t() / speed * 20));
            JobKit.strike(sp, e, j, P, k.num("shot", 40), false, true, life, hitT, d -> {
                RuneKit.knock(e, f, 0.5, 0.35);
                if (RuneKit.fxQuota("jobStampedeHit", 6)) JobFx.hit(e, j, 1.6);
            });
            JobKit.strike(sp, e, j, P, k.num("after", 20), false, false, life, hitT + bloomDelay, d -> {
                if (RuneKit.fxQuota("jobBloomHit", 6)) JobFx.hit(e, j, 1.1);
            });
        }
    }
}
