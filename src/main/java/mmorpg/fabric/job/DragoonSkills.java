package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 창술사 반전 드라군 (재설계안 2판 — 2026-11-06 사용자 "다음 작업", jobs.json advance dragoon — 금빛 · 진홍, 특성 「비상」 — JobTraits11): 1차의 거리 · 직선 ·
 * 연타 찌르기를 뒤집어 높이 · 광역 · 내리꽂는 단타 — 웜테일 · 하이 점프 · 임페일 · 드래곤 브레스 · 드래곤폴. 몸 쓰는 직업이라 제 몸 · 제 창 동작만 (용의 혼 ·
 * 불길은 그림 — 워로드의 곰의 혼과 같이). 내리꽂기는 셋 (작은 뜀 · 곧장 솟아 겨눈 곳으로 · 하늘 낙하) — 모양이 서로 다르게. 단타는 크게 감았다가 큰 타격.
 */
final class DragoonSkills {
    private DragoonSkills() {}

    /** 용의 혼 (금빛 · 흰 금빛) · 용의 불 (주홍 · 금빛) · 흙먼지 */
    // 2026-11-06 사용자 "드라군 빨간 레드 드레곤 느낌에 화염 이펙트가 좀 들어간 색이면 좋겠어": 용의 혼 = 붉은 용 (겉 진홍 · 심지 불꽃 주황), 불 = 주홍 · 노란 심지
    static final double[] DRAGON_GLOW = {0.95, 0.14, 0.06};
    static final double[] DRAGON_CORE = {1.0, 0.66, 0.24};
    static final double[] FIRE_GLOW = {1.0, 0.3, 0.05};
    static final double[] FIRE_CORE = {1.0, 0.84, 0.36};
    private static final double[] DUST = {0.62, 0.55, 0.45};

    static void register() {
        JobSystem.register("dragoon_tail", DragoonSkills::tail);
        JobSystem.register("dragoon_jump", DragoonSkills::jump);
        JobSystem.register("dragoon_impale", DragoonSkills::impale);
        JobSystem.register("dragoon_breath", DragoonSkills::breath);
        JobSystem.register("dragoon_ult", DragoonSkills::ult);
    }

    private static Vec3 chest(ServerPlayer sp) {
        return V.up(sp.position(), 1.15);
    }

    /** p 자리의 땅 (턱 하나 — 없으면 p 높이) */
    private static Vec3 ground(ServerLevel level, Vec3 p) {
        return new Vec3(p.x, RuneKit.groundAt(level, V.up(p, 0.6), p.y), p.z);
    }

    /** p 아래 땅 (깊이 maxDown 칸까지 — 공중에서 내리꽂을 자리 · 없으면 턱 하나) */
    private static Vec3 groundBelow(ServerLevel level, Vec3 p, double maxDown) {
        Double gy = RuneKit.solidGround(level, V.up(p, 0.5), maxDown);
        return gy != null ? new Vec3(p.x, gy, p.z) : ground(level, p);
    }

    /**
     * 내려설 자리 (조준한 곳): 겨눈 적 (가로 range 칸 · 6도 도움) 의 내 쪽 앞 → 조준한 땅 (가로 1.5 ~ range 칸 · 발보다 maxDy 칸 아래까지) → (공중이면) 앞쪽 120° 의
     * 가장 가까운 적 앞 (하늘에서 겨눈 줄이 적 위를 지나감) → 앞 fallback 칸 (벽 앞) 의 땅. 공중에서도 (눈에서 겨눈 쪽으로 range + 높이 칸까지 봄)
     */
    static Vec3 aimSpot(ServerPlayer sp, double range, double fallback, double maxDy) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        double air = JobMove.airborne(sp);
        double reach = range + air + 3;
        RpgCombat.Aim a = RpgCombat.aimTarget(sp, reach, 6.0);
        if (a.target() != null && Kit.canHit(sp, a.target()) && V.hdist(a.target().position(), sp.position()) <= range + 1) return before(sp, a.target(), f, maxDy);
        if (air > 0.8) {
            LivingEntity best = null;
            double bd = Double.MAX_VALUE;
            for (LivingEntity e : Kit.around(sp, sp.position(), range, air + 4)) {
                Vec3 d = V.sub(e.position(), sp.position());
                double h = Math.hypot(d.x, d.z);
                if (h > 0.5 && (d.x * f.x + d.z * f.z) / h < 0.5) continue;
                if (h < bd) {
                    bd = h;
                    best = e;
                }
            }
            if (best != null) return before(sp, best, f, maxDy);
        }
        Vec3 g = RuneKit.groundAim(sp, reach);
        double dist = V.hdist(g, sp.position());
        if (dist >= 1.5 && dist <= range + 0.5 && g.y <= sp.getY() + 2 && sp.getY() - g.y <= maxDy + air) return groundBelow(level, g, 4);
        double can = RuneKit.dashDistance(sp, f, fallback, 0.3, 1.5);
        return groundBelow(level, V.add(sp.position(), f, can), maxDy + air + 2);
    }

    /** 적 e 의 내 쪽 앞 (몸 여유만큼) 의 땅 */
    private static Vec3 before(ServerPlayer sp, LivingEntity e, Vec3 f, double maxDy) {
        Vec3 d = V.sub(sp.position(), e.position());
        double len = Math.hypot(d.x, d.z);
        Vec3 u = len > 0.05 ? new Vec3(d.x / len, 0, d.z / len) : V.scale(f, -1);
        return groundBelow(sp.level(), V.add(e.position(), u, Math.min(len, 0.6 + Kit.bodyPad(e))), maxDy + 4);
    }

    /** 내리꽂은 자리의 타격 그림: 땅에 꽂히는 큰 빛의 창 · 땅 고리 · 터짐 · 불꽃 덩이 · 둘레로 퍼지는 큰 검기 · 돌 · 소리 (scale = 크기) */
    private static void plungeFx(ServerPlayer sp, Jobs.Job j, Vec3 g, double radius, double scale) {
        ServerLevel level = sp.level();
        JobFx.spear(level, V.up(g, 0.1), V.DOWN, 1.3 * scale, 0, 0.5, j, Opt.of("fade", 0.4));
        JobFx.smash(level, V.up(g, 0.05), radius, j, 0.45 + 0.1 * scale, Opt.empty());
        JobFx.burst(level, V.up(g, 0.7), 1.1 * scale, j, 0.32, Opt.empty());
        JobFx.fire(level, V.up(g, 0.9 * scale), 1.1 * scale, 0.55, Opt.of("spin", 0.0), FIRE_GLOW, FIRE_CORE);
        JobFx.radialWaves(sp, j, g, 6, 0.6, radius + 0.5, 0.5, 0.9 * scale, 0.3, 0);
        RuneKit.rockBurst(level, V.up(g, 0.2), (int) Math.round(6 * scale), 5.0, 0.16 * scale);
        SkillFx.sparks(level, V.up(g, 0.3), FIRE_GLOW, (int) Math.round(10 * scale), 5.0);
        SkillFx.sparks(level, V.up(g, 0.2), DUST, (int) Math.round(8 * scale), 3.4);
        SkillFx.play(level, "mace.heavy_smash_ground", g, 0.85, 0.95);
        SkillFx.play(level, "item.trident.hit_ground", g, 0.7, 0.8);
    }

    // ------------------------------------------------------------ 웜테일

    /**
     * 웜테일 (롤 야스오 R — 띄운 적을 따라 오름): 창을 낮게 감았다가 sweepAt 틱에 자루로 둘레 radius 칸을 휩쓸어 띄움 (sweep · lift) → 휩쓴 적들 가운데 (없으면 앞
     * 1.5칸) 로 hop 칸 뛰어올라 slamAt 틱에 창을 거꾸로 내리꽂음 (둘레 slamRadius 칸 slam × 높이 덤). 밀려나지 않음
     */
    static boolean tail(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int sweepAt = Math.max(2, (int) k.num("sweepAt", 6));
        int slamAt = Math.max(sweepAt + 10, (int) k.num("slamAt", 18));
        double r = k.num("radius", 4);
        double sr = k.num("slamRadius", 3.2);
        double hop = k.num("hop", 2.5);
        Vec3 c0 = sp.position();
        RpgCombat.setSteadfast(sp, slamAt + 4);
        // 감음: 창을 낮게 뒤로 — 자루 끝에 모이는 빛 · 발밑 먼지 · 낮은 울림
        JobFx.charge(sp, j, new Vec3(-0.45, 0.45, -0.7), sweepAt, 0.6);
        SkillFx.sparks(level0, V.up(c0, 0.2), DUST, 6, 2.5);
        SkillFx.play(level0, "entity.ender_dragon.flap", c0, 1.4, 0.6);
        List<LivingEntity> swept = new ArrayList<>();
        RuneKit.later(sweepAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            // 휩쓸기: 허리 높이 도는 칼날 · 바닥 고리 · 용의 꼬리 같은 금빛 호 (큰 검기 셋) · 먼지
            JobFx.whirl(lv, V.up(c, 0.7), r, j, 0.3, Opt.of("alpha", 0.95));
            JobFx.ring(lv, V.up(c, 0.08), r, j, false, 0.35, Opt.empty());
            JobFx.radialWaves(sp, j, c, 5, 0.8, r, 0.6, 0.85, 0.22, 40);
            SkillFx.sparks(lv, V.up(c, 0.3), DUST, 10, 4);
            SkillFx.play(lv, "entity.player.attack.sweep", c, 0.6, 1.0);
            SkillFx.play(lv, "entity.ender_dragon.flap", c, 1.0, 0.8);
            for (LivingEntity e : Kit.around(sp, c, r, 2.5)) {
                swept.add(e);
                JobKit.strike(sp, e, j, P, k.num("sweep", 2.9), false, false, life, 0, d -> {
                    JobFx.hit(e, j, 1.0);
                    if (JobKit.pushable(e)) Kit.launch(e, 0, k.num("lift", 0.6), 0);
                });
            }
        });
        double[] peak = {hop};
        RuneKit.later(sweepAt + 1, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            Vec3 dest = null;
            Vec3 m = Vec3.ZERO;
            int n = 0;
            for (LivingEntity e : swept) {
                if (!e.isAlive() || e.isRemoved() || V.hdist(e.position(), c) > r + 0.5) continue;
                m = V.add(m, e.position(), 1.0);
                n++;
            }
            if (n > 0) {
                Vec3 avg = V.scale(m, 1.0 / n);
                Vec3 d = V.sub(avg, c);
                double len = Math.min(3.0, Math.hypot(d.x, d.z));
                Vec3 u = len > 0.1 ? new Vec3(d.x, 0, d.z).normalize() : Kit.facingOf(sp);
                double can = RuneKit.dashDistance(sp, u, len, 0.3, 1.5);
                dest = ground(lv, V.add(c, u, can));
            }
            if (dest == null) dest = ground(lv, V.add(c, Kit.facingOf(sp), RuneKit.dashDistance(sp, Kit.facingOf(sp), 1.5, 0.3, 1.5)));
            peak[0] = JobMove.hopPeak(sp, c, dest, hop);
            JobTraits11.airFor(sp, slamAt - sweepAt + 2);
            JobMove.run(sp, JobMove.hop(0, slamAt - sweepAt - 1, JobMove.at(dest), peak[0]));
            JobFx.wind(lv, V.up(c, 0.1), 1.6, j, 0.4, Opt.empty());
            JobFx.comet(lv, V.up(c, 0.4), V.UP, 1.0, 10, 0.25, DRAGON_GLOW, DRAGON_CORE, JobFx.hide1p(sp));
        });
        // 꼭대기: 창을 거꾸로 — 창끝에 모이는 불빛
        RuneKit.later((sweepAt + slamAt) / 2, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.glint(sp.level(), V.up(sp.position(), -0.2), 0.7, DRAGON_GLOW, DRAGON_CORE, 0.25, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
        });
        RuneKit.later(slamAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 g = ground(lv, sp.position());
            double mult = k.num("slam", 4.35) * JobTraits11.heightMult(sp, j, peak[0]);
            plungeFx(sp, j, g, sr, 1.0);
            JobFx.slash(sp, j, 1.8, 0.4, 2.0, 90.0, 0.22);
            // 꽂힌 자리 둘레로 번지는 불길 (붉은 용 — 불꽃 덩이 여섯 · 땅불)
            for (int q = 0; q < 6; q++) {
                double a = Math.toRadians(q * 60 + 15);
                Vec3 p = ground(lv, V.add(g, new Vec3(Math.cos(a), 0, Math.sin(a)), sr * 0.7));
                RuneKit.later(q % 3, () -> {
                    JobFx.flame(lv, p, 0.6, 0.6, Opt.empty(), FIRE_GLOW, FIRE_CORE);
                    JobFx.fire(lv, V.up(p, 0.7), 0.6, 0.45, Opt.of("vel", new Vec3(0, 1.6, 0)), FIRE_GLOW, FIRE_CORE);
                });
            }
            for (LivingEntity e : Kit.around(sp, g, sr, 3.5)) {
                JobKit.strike(sp, e, j, P, mult, false, false, life, 0, d -> JobImpact.hit(sp, e, j, 1.4));
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 하이 점프

    /** 하이 점프로 떠 있는 사람 → 목숨 (내리꽂으면 지움 — 떠 있는 그림 · 표식을 그침) */
    private static final Map<UUID, String> JUMPS = new HashMap<>();

    /**
     * 하이 점프 (롤 판테온 W): 웅크렸다가 2틱부터 rise 틱까지 곧장 height 칸 위로 (머리 위 여유까지) → hover 틱 떠 있음 (등 뒤 용의 혼 날갯짓 · 조준한 곳의 표식 — 4틱마다) →
     * gap 틱 뒤부터 다시 누르면 ({@link #dive}) 조준한 곳으로 혜성처럼 내리꽂음, 창이 닫히면 저절로. 떠 있는 동안 비상 (받는 피해 − air)
     */
    static boolean jump(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (JobRecast.stage(sp, k.id()) >= 1) return dive(sp, j, k, P, false);
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int rise = Math.max(4, (int) k.num("rise", 10));
        int hover = Math.max(10, (int) k.num("hover", 40));
        double height = k.num("height", 7);
        Vec3 c0 = sp.position();
        JUMPS.put(sp.getUUID(), life);
        JobMove.run(sp, JobMove.to(2, rise, JobMove.up(sp, height), JobMove.OUT), JobMove.hold(rise, rise + hover));
        JobTraits11.airFor(sp, rise + hover + 10);
        Kit.noFall(sp, rise + hover + 40);
        // 웅크림 → 뛰어오름: 발밑 터짐 · 바람 고리 · 위로 솟는 금빛 꼬리 · 소리
        JobFx.charge(sp, j, new Vec3(0.0, 0.6, 0.3), 2, 0.5);
        RuneKit.later(2, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            JobFx.smash(lv, V.up(c0, 0.05), 1.8, j, 0.4, Opt.empty());
            JobFx.wind(lv, V.up(c0, 0.1), 2.4, j, 0.5, Opt.empty());
            JobFx.comet(lv, V.up(c0, 1.2), V.UP, 1.6, 22, 0.35, DRAGON_GLOW, DRAGON_CORE, JobFx.hide1p(sp));
            SkillFx.sparks(lv, V.up(c0, 0.2), DUST, 10, 4.0);
            SkillFx.play(lv, "entity.ender_dragon.flap", c0, 1.2, 1.0);
            SkillFx.play(lv, "mob.breeze.jump", c0, 0.8, 0.9);
        });
        // 떠 있는 동안 (4틱마다): 등 뒤 용의 혼 (날갯짓) · 조준한 곳의 표식 (고리 · 가운데 불빛)
        for (int t = rise - 2; t < rise + hover; t += 4) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!life.equals(JUMPS.get(sp.getUUID())) || !RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 f = Kit.facingOf(sp);
                JobFx.dragon(lv, V.up(V.add(sp.position(), f, 0.5), 1.2), 1.7, tt == rise - 2 ? 0 : 2, tt == rise - 2 ? 2 : 4, 0.22, tt != rise - 2,
                        JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.8, "solo", 70.0), DRAGON_GLOW, DRAGON_CORE);
                Vec3 d = aimSpot(sp, k.num("range", 10), k.num("fallback", 3), 12);
                JobFx.ring(lv, V.up(d, 0.1), k.num("radius", 3.5), j, (tt / 4) % 2 == 0, 0.22, Opt.of("alpha", 0.7));
                JobFx.glint(lv, V.up(d, 0.3), 0.5, DRAGON_GLOW, DRAGON_CORE, 0.22, Opt.empty());
                if ((tt - rise) % 12 == 0) SkillFx.play(lv, "entity.ender_dragon.flap", sp.position(), 1.5, 0.5);
            });
        }
        JobRecast.open(sp, k, 1, rise + hover, (int) k.num("gap", 10), () -> {
            if (RuneKit.sameLife(sp, life) && life.equals(JUMPS.get(sp.getUUID()))) dive(sp, j, k, P, true);
        });
        JobSystem.note(sp, "§6다시 누르면 조준한 곳으로 내리꽂음 §7(" + (rise + hover) / 20.0 + "초)");
        return true;
    }

    /** 하이 점프 내리꽂음: diveTicks 틱에 조준한 곳 (range 칸 — 없으면 앞 fallback 칸) 으로 · 용의 혼이 함께 내리꽂힘 · 불꽃 꼬리 → 둘레 radius 칸 total × 높이 덤 */
    private static boolean dive(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, boolean auto) {
        JobRecast.close(sp, k.id());
        if (auto) JobKit.motion(sp, k.id() + ":dive");
        else JobRecast.motion(sp, k.id() + ":dive");
        JUMPS.remove(sp.getUUID());
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        int ticks = Math.max(2, (int) k.num("diveTicks", 5));
        Vec3 from = sp.position();
        Vec3 dest = aimSpot(sp, k.num("range", 10), k.num("fallback", 3), 12);
        double fallen = Math.max(0, from.y - dest.y);
        JobMove.run(sp, JobMove.to(0, ticks, JobMove.at(dest), JobMove.IN));
        JobTraits11.airFor(sp, ticks + 2);
        Kit.noFall(sp, ticks + 30);
        RpgCombat.setSteadfast(sp, ticks + 4);
        Vec3 dir = V.unit(V.sub(dest, from));
        double speed = from.distanceTo(dest) / (ticks / 20.0);
        // 내리꽂음: 용의 혼 (내리꽂는 꼴) 이 함께 · 불꽃 꼬리 · 1인칭 빠른 줄 · 소리
        JobFx.dragon(level, V.up(from, 1.2), 1.9, 7, 1, ticks / 20.0 + 0.05, false, JobFx.hide1p(sp).with("vel", V.scale(dir, speed), "alpha", 0.9), DRAGON_GLOW,
                DRAGON_CORE);
        JobFx.comet(level, V.up(from, 0.6), dir, 1.8, speed, ticks / 20.0 + 0.05, FIRE_GLOW, FIRE_CORE, JobFx.hide1p(sp));
        JobFx.speed(sp, j, 0.3, 0.8);
        SkillFx.play(level, "entity.ender_dragon.flap", from, 0.8, 1.0);
        SkillFx.play(level, "item.trident.riptide_3", from, 0.9, 0.8);
        RuneKit.later(ticks, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 g = V.hdist(sp.position(), dest) < 1.5 ? ground(lv, sp.position()) : dest;
            double mult = k.total() * JobTraits11.heightMult(sp, j, fallen);
            double rad = k.num("radius", 3.5);
            plungeFx(sp, j, g, rad, 1.3);
            JobFx.flame(lv, g, 1.1, 0.6, Opt.empty(), FIRE_GLOW, FIRE_CORE);
            JobFx.slash(sp, j, 1.8, 0.45, 2.3, 90.0, 0.22);
            for (LivingEntity e : Kit.around(sp, g, rad, 3.5)) {
                JobKit.strike(sp, e, j, P, mult, false, false, life, 0, d -> JobImpact.hit(sp, e, j, 1.6));
            }
            if (fallen >= 2) JobSystem.note(sp, "§6비상 §7높이 " + (int) Math.round(fallen) + "칸 → 피해 ×" + String.format(java.util.Locale.ROOT, "%.2f", mult / k.total()));
        });
        return true;
    }

    // ------------------------------------------------------------ 임페일

    /**
     * 임페일 (롤 세트 R): 창을 크게 당겼다가 skewerAt 틱에 앞 range 칸의 적 하나 (조준 · arc 도 도움 — 없으면 쓰지 않음) 를 꿰어 (skewer) 창끝에 꿴 채 머리 위로 높이
     * (lift 칸) 들어 올려 → slamAt 틱에 앞 ahead 칸 땅에 내리꽂음 (둘레 radius 칸 slam — 꿰인 적 포함 · 보스 아닌 적 stun 틱 기절). 보스 · 큰 몹 · 밀 수 없는 적은 들지
     * 않고 그 자리에서 내리꽂음. 밀려나지 않음
     */
    static boolean impale(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        LivingEntity e = Kit.pickTarget(sp, k.num("range", 6), k.num("arc", 50) / 2);
        if (e == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 앞에 적이 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int skewerAt = Math.max(2, (int) k.num("skewerAt", 6));
        int slamAt = Math.max(skewerAt + 10, (int) k.num("slamAt", 20));
        double liftH = k.num("lift", 3.5);
        double radius = k.num("radius", 4.5);
        boolean carry = JobKit.pushable(e) && !JobKit.isBoss(e) && e.getBbHeight() <= 2.6 && e.getBbWidth() <= 1.6;
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        RpgCombat.setSteadfast(sp, slamAt + 4);
        JobFx.charge(sp, j, new Vec3(0.35, 1.25, -0.55), skewerAt, 0.8);
        SkillFx.play(level0, "entity.ender_dragon.growl", c0, 1.6, 0.4);
        RuneKit.later(skewerAt, () -> {
            if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
            ServerLevel lv = sp.level();
            Vec3 b = Kit.bodyPoint(e);
            JobFx.line(lv, V.add(chest(sp), f, 0.6), b, j, 0.25, Opt.of("alpha", 0.95));
            JobFx.stab(lv, Kit.bodyToward(e, sp.getEyePosition(), sp.getViewVector(1F)), 1.0 + Kit.bodyPad(e) * 0.4, j, 0.3, Opt.of("vel", RuneKit.entVel(e)));
            JobFx.burst(lv, b, 0.9 + Kit.bodyPad(e) * 0.4, j, 0.28, Opt.of("vel", RuneKit.entVel(e)));
            SkillFx.play(lv, "item.trident.hit", b, 0.8, 1.0);
            SkillFx.play(lv, "entity.player.attack.crit", b, 0.9, 0.8);
            JobKit.strike(sp, e, j, P, k.num("skewer", 2.6), false, false, life, 0, d -> JobImpact.hit(sp, e, j, 1.2));
        });
        Vec3 land = ground(level0, V.add(c0, f, RuneKit.dashDistance(sp, f, k.num("ahead", 2.5), 0.3, 1.5)));
        Vec3[] at = {null};
        if (carry) {
            Vec3 top = V.up(V.add(c0, f, 1.1), Math.max(1.5, liftH - 0.9));
            int upEnd = slamAt - 6;
            int downEnd = slamAt - 2;
            Vec3[] start = {null};
            for (int t = skewerAt + 1; t <= slamAt; t++) {
                int tt = t;
                RuneKit.later(t, () -> {
                    if (!RuneKit.sameLife(sp, life) || !e.isAlive() || e.isRemoved()) return;
                    if (start[0] == null) start[0] = e.position();
                    Vec3 p;
                    if (tt <= upEnd) {
                        double u = JobMove.ease((tt - skewerAt) / (double) Math.max(1, upEnd - skewerAt), JobMove.OUT);
                        Vec3 ctl = V.up(start[0], liftH * 0.9);
                        Vec3 a = V.lerp(start[0], ctl, u);
                        Vec3 b2 = V.lerp(ctl, top, u);
                        p = V.lerp(a, b2, u);
                    } else if (tt <= downEnd) {
                        double u = (tt - upEnd) / (double) Math.max(1, downEnd - upEnd);
                        p = V.lerp(top, land, u * u);
                    } else {
                        p = land;
                    }
                    if (Kit.moveTo(e, p)) at[0] = p;
                    e.resetFallDistance();
                    if (tt % 2 == 0) {
                        ServerLevel lv = sp.level();
                        JobFx.fire(lv, Kit.bodyPoint(e), 0.45 + Kit.bodyPad(e) * 0.3, 0.3, Opt.of("vel", RuneKit.entVel(e)), FIRE_GLOW, FIRE_CORE);
                        if (tt == upEnd) {
                            JobFx.glint(lv, Kit.bodyPoint(e), 0.9, DRAGON_GLOW, DRAGON_CORE, 0.25, Opt.empty());
                            SkillFx.play(lv, "entity.ender_dragon.growl", sp.position(), 1.3, 0.6);
                        }
                    }
                });
            }
        }
        RuneKit.later(slamAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 g = carry && at[0] != null && e.isAlive() ? ground(lv, e.position()) : ground(lv, e.isRemoved() ? land : e.position());
            if (carry && e.isAlive()) Kit.launch(e, 0, -0.4, 0);
            plungeFx(sp, j, g, radius, 1.5);
            JobFx.quake(lv, V.up(g, 0.05), radius, j, 0.55, Opt.empty());
            JobFx.dragon(lv, V.up(g, 2.6), 2.4, 5, 1, 0.45, false, Opt.of("alpha", 0.85), DRAGON_GLOW, DRAGON_CORE);
            for (int q = 0; q < 6; q++) {
                double a = Math.toRadians(q * 60 + 30);
                Vec3 p = ground(lv, V.add(g, new Vec3(Math.cos(a), 0, Math.sin(a)), radius * 0.6));
                RuneKit.later(q % 3, () -> JobFx.fire(lv, V.up(p, 0.8), 0.8, 0.5, Opt.empty(), FIRE_GLOW, FIRE_CORE));
            }
            JobFx.slash(sp, j, 2.0, 0.5, 2.6, 90.0, 0.25);
            SkillFx.play(lv, "entity.generic.explode", g, 1.0, 0.7);
            SkillFx.play(lv, "entity.ender_dragon.growl", g, 1.0, 0.6);
            for (LivingEntity x : Kit.around(sp, g, radius, 3.5)) {
                JobKit.strike(sp, x, j, P, k.num("slam", 10.45), false, false, life, 0, d -> {
                    JobImpact.hit(sp, x, j, 1.8);
                    if (!JobKit.isBoss(x)) RuneKit.stun(x, (int) k.num("stun", 10), JobFx.outer(j), true);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 드래곤 브레스

    /**
     * 드래곤 브레스 (롤 쉬바나 E — 태세 칸 몫 피해): 창을 땅에 꽂고 포효 — 등 뒤에 용의 혼이 솟아 (생겨남 → 날갯짓) 숨을 들이켬 (머리 앞으로 빨려 드는 불티 · 커지는
     * 불빛, 느려짐 · 밀려나지 않음) → breathAt 틱에 앞 부채꼴 (range 칸 · cone 도) 로 금빛 불길을 토함 (흘러가며 커지는 불꽃 덩이 · 땅의 불 — 맞은 적 breath · 밀쳐냄)
     */
    static boolean breath(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int breathAt = Math.max(4, (int) k.num("breathAt", 14));
        double range = k.num("range", 9);
        double cone = k.num("cone", 70);
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 mouth = V.up(V.add(c0, f, 0.9), 3.3);
        RpgCombat.setSteadfast(sp, breathAt + 8);
        sp.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, breathAt + 6, 3, false, false));
        // 창을 꽂음: 발밑 불꽃 고리 · 땅 고리
        JobFx.ring(level0, V.up(c0, 0.08), 1.8, j, false, 0.4, Opt.empty());
        SkillFx.play(level0, "item.trident.hit_ground", c0, 0.8, 0.9);
        SkillFx.play(level0, "entity.ender_dragon.growl", c0, 0.9, 0.7);
        // 용의 혼: 생겨남 (0 · 1) → 날갯짓 → breathAt 에 포효
        Vec3 dragonAt = V.up(V.add(c0, f, 0.7), 1.7);
        JobFx.dragon(level0, dragonAt, 2.1, 0, 2, 0.3, false, JobFx.hide1p(sp).with("alpha", 0.9), DRAGON_GLOW, DRAGON_CORE);
        RuneKit.later(6, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.dragon(sp.level(), dragonAt, 2.1, 2, 4, (breathAt - 6) / 20.0, true, JobFx.hide1p(sp).with("alpha", 0.9), DRAGON_GLOW,
                    DRAGON_CORE);
        });
        // 숨을 들이켬 (2틱마다): 둘레에서 입으로 빨려 드는 불티 · 커지는 불빛
        for (int t = 2; t < breathAt; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                double u = tt / (double) breathAt;
                for (int q = 0; q < 3; q++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 from = V.add(mouth, new Vec3(Math.cos(a) * 2.2, (Kit.rand() - 0.3) * 1.6, Math.sin(a) * 2.2), 1.0);
                    RuneKit.streak(lv, from, mouth, FIRE_GLOW, 0.06, 0.18, 0.8, 0);
                }
                JobFx.glint(lv, mouth, 0.3 + 0.6 * u, FIRE_GLOW, FIRE_CORE, 0.14, Opt.empty());
                if (tt % 6 == 0) SkillFx.play(lv, "entity.blaze.ambient", mouth, 0.6 + u * 0.6, 0.5);
            });
        }
        RuneKit.later(breathAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            sp.removeEffect(MobEffects.SLOWNESS);
            ServerLevel lv = sp.level();
            Vec3 ff = Kit.facingOf(sp);
            JobFx.dragon(lv, dragonAt, 2.2, 6, 1, 0.5, false, JobFx.hide1p(sp).with("alpha", 0.95), DRAGON_GLOW, DRAGON_CORE);
            // 입 앞 번쩍 · 첫 불길 (토하는 순간이 보이게)
            JobFx.burst(lv, V.add(mouth, ff, 0.8), 1.3, FIRE_GLOW, FIRE_CORE, 0.3, Opt.empty());
            JobFx.fire(lv, V.add(mouth, ff, 1.2), 1.5, 0.4, Opt.of("vel", V.scale(V.unit(V.add(ff, new Vec3(0, -0.35, 0), 1.0)), 7.0), "grow", 1.6), FIRE_GLOW, FIRE_CORE);
            SkillFx.play(lv, "entity.ender_dragon.shoot", mouth, 0.8, 1.0);
            SkillFx.play(lv, "entity.blaze.shoot", mouth, 0.7, 0.9);
            SkillFx.play(lv, "item.firecharge.use", mouth, 0.8, 0.9);
            // 불길: 입에서 부채꼴로 흘러가며 커지는 불꽃 덩이 (카메라를 봄 — 앞으로 눕는 그림은 뒤 3인칭에서 보이지 않음) · 땅의 불
            double half = Math.toRadians(cone / 2);
            for (int q = 0; q < 22; q++) {
                double a = (Kit.rand() * 2 - 1) * half * 0.85;
                double dist = range * (0.55 + 0.45 * Kit.rand());
                Vec3 dirq = V.turnY(ff, Math.toDegrees(a));
                Vec3 end = V.up(V.add(c0, dirq, dist), 0.6 + Kit.rand() * 0.8);
                Vec3 vel = V.scale(V.sub(end, mouth), 1 / 0.45);
                int dl = q % 4;
                RuneKit.later(dl, () -> JobFx.fire(lv, V.add(mouth, ff, 0.6), 0.95 + 0.5 * Kit.rand(), 0.5, Opt.of("vel", vel, "grow", 1.9, "spin", Kit.rand() * 40 - 20), FIRE_GLOW,
                        FIRE_CORE));
            }
            for (int q = 0; q < 9; q++) {
                double a = (q / 8.0 * 2 - 1) * half * 0.8;
                double dist = range * (0.35 + 0.6 * ((q * 37) % 10) / 10.0);
                Vec3 p = ground(lv, V.add(c0, V.turnY(ff, Math.toDegrees(a)), dist));
                RuneKit.later(4 + q % 4, () -> JobFx.flame(lv, p, 0.55 + 0.25 * Kit.rand(), 0.8, Opt.empty(), FIRE_GLOW, FIRE_CORE));
            }
            JobFx.slash(sp, j, 2.0, -0.2, 2.4, 0.0, 0.25);
            double cosHalf = Math.cos(half);
            for (LivingEntity e : Kit.around(sp, sp.position(), range, 4)) {
                Vec3 d = V.sub(e.position(), sp.position());
                double h = Math.hypot(d.x, d.z);
                double ahead = d.x * ff.x + d.z * ff.z;
                // 부채꼴 안 (1.2칸 안은 몸 앞 반쪽 — 등 뒤의 적은 불길이 머리 위로 지나감)
                if (h > 1.2 ? ahead / h < cosHalf : ahead < 0) continue;
                JobKit.strike(sp, e, j, P, k.num("breath", 17.4), false, false, life, (int) Math.round(h / range * 6), x -> {
                    JobImpact.hit(sp, e, j, 1.6);
                    if (e.level() instanceof ServerLevel el) JobFx.fire(el, Kit.bodyPoint(e), 0.9 + Kit.bodyPad(e) * 0.4, 0.5, Opt.of("vel", RuneKit.entVel(e)), FIRE_GLOW,
                            FIRE_CORE);
                    if (JobKit.pushable(e)) RuneKit.knock(e, d, k.num("knock", 0.7), 0.25);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 드래곤폴

    /**
     * 드래곤폴 (롤 판테온 R): 용의 하늘 (rpg:ult_wyrm) — 크게 웅크려 용의 혼을 두르고 (등 뒤에서 솟는 용 · 창끝 빛) soarAt 틱에 하늘 높이 (height 칸 — 머리 위 여유까지)
     * 솟구침 (그때부터 내리꽂을 때까지 받는 피해 없음) → hideAt ~ diveAt 조준한 곳 (range 칸 — 없으면 앞 fallback 칸, 쓴 때 정함) 위로 옮겨 감 · 그곳에 금빛 용의 표식이
     * 번짐 → diveAt 틱부터 거대한 금빛 용과 함께 내리꽂혀 impactAt 틱에 둘레 radius 칸 impact (그 밖 outerRadius 칸까지 × outer) × 높이 덤 → pillarsAt 틱에 갈라진 땅에서
     * 용의 불기둥이 둘레로 터져 오름 (둘레 pillarRadius 칸 pillars · 띄움)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int soarAt = Math.max(4, (int) k.num("soarAt", 12));
        int hideAt = Math.max(soarAt + 2, (int) k.num("hideAt", 18));
        int diveAt = Math.max(hideAt + 6, (int) k.num("diveAt", 40));
        int impactAt = Math.max(diveAt + 2, (int) k.num("impactAt", 46));
        int pillarsAt = Math.max(impactAt + 10, (int) k.num("pillarsAt", 56));
        double radius = k.num("radius", 4);
        double outerR = k.num("outerRadius", 8);
        double pillarR = k.num("pillarRadius", 9);
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 dest = aimSpot(sp, k.num("range", 14), k.num("fallback", 8), 8);
        Ult.sky(level0, c0, "rpg:ult_wyrm", pillarsAt + 30);
        RpgCombat.setSteadfast(sp, impactAt + 4);
        Kit.noFall(sp, impactAt + 40);
        // 웅크림: 등 뒤에서 솟는 용의 혼 · 창끝 빛 · 발밑 불꽃 고리 · 낮은 포효
        JobFx.charge(sp, j, new Vec3(0.2, 1.7, 0.3), soarAt, 1.0);
        JobFx.dragon(level0, V.up(V.add(c0, f, 0.9), 2.0), 2.8, 0, 2, 0.3, false, JobFx.hide1p(sp).with("alpha", 0.9), DRAGON_GLOW, DRAGON_CORE);
        RuneKit.later(6, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.dragon(sp.level(), V.up(V.add(c0, f, 0.9), 2.0), 2.8, 5, 2, (soarAt - 6) / 20.0 + 0.05, false, JobFx.hide1p(sp)
                    .with("alpha", 0.9), DRAGON_GLOW, DRAGON_CORE);
        });
        JobFx.ring(level0, V.up(c0, 0.08), 2.2, j, false, soarAt / 20.0, Opt.empty());
        SkillFx.play(level0, "entity.ender_dragon.growl", c0, 0.8, 1.0);
        // 솟구침 → 조준한 곳 위로 옮겨 감 → 떠 있음 → 내리꽂음
        Vec3[] sky = {null};
        JobMove.run(sp, JobMove.to(soarAt, hideAt, JobMove.up(sp, k.num("height", 14)), JobMove.OUT),
                JobMove.to(hideAt, diveAt - 4, from -> {
                    sky[0] = new Vec3(dest.x, from.y, dest.z);
                    return sky[0];
                }, JobMove.INOUT),
                JobMove.hold(diveAt - 4, diveAt), JobMove.to(diveAt, impactAt, JobMove.at(dest), JobMove.IN));
        double[] top = {c0.y};
        RuneKit.later(soarAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            JobTraits11.immuneFor(sp, impactAt - soarAt + 2);
            JobTraits11.airFor(sp, impactAt - soarAt + 2);
            JobFx.smash(lv, V.up(c0, 0.05), 2.4, j, 0.45, Opt.empty());
            JobFx.wind(lv, V.up(c0, 0.1), 3.0, j, 0.5, Opt.empty());
            JobFx.comet(lv, V.up(c0, 1.5), V.UP, 2.4, 40, 0.4, DRAGON_GLOW, DRAGON_CORE, JobFx.hide1p(sp));
            SkillFx.sparks(lv, V.up(c0, 0.2), DUST, 14, 5.0);
            SkillFx.play(lv, "entity.ender_dragon.flap", c0, 0.7, 1.0);
            SkillFx.play(lv, "item.trident.riptide_3", c0, 0.7, 1.0);
        });
        // 표식 (4틱마다 — 점점 커짐): 땅 고리 둘 · 가운데 불빛 · 둘레 작은 불
        for (int t = hideAt - 2; t < impactAt; t += 4) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                top[0] = Math.max(top[0], sp.getY());
                double u = Math.min(1, (tt - hideAt + 2) / (double) Math.max(1, diveAt - hideAt));
                JobFx.ring(lv, V.up(dest, 0.1), outerR * (0.4 + 0.6 * u), j, (tt / 4) % 2 == 0, 0.22, Opt.of("alpha", 0.75));
                JobFx.ring(lv, V.up(dest, 0.12), radius, j, (tt / 4) % 2 != 0, 0.22, Opt.of("alpha", 0.9));
                JobFx.glint(lv, V.up(dest, 0.4), 0.6 + 0.6 * u, DRAGON_GLOW, DRAGON_CORE, 0.22, Opt.empty());
                if ((tt - hideAt) % 8 == 0) JobFx.fire(lv, V.up(dest, 0.6), 0.6 + 0.4 * u, 0.4, Opt.empty(), FIRE_GLOW, FIRE_CORE);
                if ((tt - hideAt) % 12 == 0) SkillFx.play(lv, "entity.ender_dragon.flap", dest, 0.9, 0.8);
                // 2026-11-06 영상 1: 하늘에 있는 동안 (1초) 화면이 빈 하늘 · 먼 땅뿐이었음 → 표식 자리를 위에서 내리비추는 불빛 기둥 · 내 앞 아래에서 날갯짓하는 붉은 용
                // (영상 2: 발 높이 앞 2.6칸이면 3인칭 카메라 위로 꼬리만 보였음 → 발보다 1.5칸 아래 — 땅의 표식으로 날아가는 꼴)
                JobFx.spotlight(lv, dest, 4 + 8 * u, 0.24, Opt.of("alpha", 0.55 + 0.3 * u), FIRE_GLOW, FIRE_CORE);
                if (tt >= hideAt && tt < diveAt) {
                    Vec3 ff = Kit.facingOf(sp);
                    JobFx.dragon(lv, V.up(V.add(sp.position(), ff, 2.6), -1.5), 3.0, 2, 4, 0.24, true, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.9,
                            "solo", 70.0), DRAGON_GLOW, DRAGON_CORE);
                }
            });
        }
        // 내리꽂음: 거대한 금빛 용 (내리꽂는 꼴) · 불꽃 꼬리
        RuneKit.later(diveAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 p = sp.position();
            top[0] = Math.max(top[0], p.y);
            double secs = (impactAt - diveAt) / 20.0;
            Vec3 vel = V.scale(V.sub(dest, p), 1 / secs);
            JobFx.dragon(lv, V.up(p, 2.5), 5.0, 7, 1, secs + 0.08, false, Opt.of("vel", vel, "alpha", 0.95), DRAGON_GLOW, DRAGON_CORE);
            JobFx.comet(lv, V.up(p, 1.0), V.unit(V.sub(dest, p)), 3.0, V.len(vel), secs + 0.05, FIRE_GLOW, FIRE_CORE, JobFx.hide1p(sp));
            SkillFx.play(lv, "entity.ender_dragon.growl", dest, 0.7, 1.0);
        });
        RuneKit.later(impactAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            // 막혀 하늘에 남았으면 표식 자리로 (내리꽂음이 끝남)
            if (V.hdist(sp.position(), dest) > 1.5 || sp.getY() - dest.y > 1.5) {
                Kit.noFall(sp, 40);
                Kit.moveTo(sp, dest);
            }
            double fallen = Math.max(0, top[0] - dest.y);
            double hm = JobTraits11.heightMult(sp, j, fallen);
            plungeFx(sp, j, dest, outerR, 2.4);
            JobFx.quake(lv, V.up(dest, 0.05), outerR, j, 0.6, Opt.empty());
            JobFx.burst(lv, V.up(dest, 1.2), 3.4, j, 0.4, Opt.empty());
            JobFx.dragon(lv, V.up(dest, 3.4), 3.6, 6, 1, 0.55, false, Opt.of("alpha", 0.9), DRAGON_GLOW, DRAGON_CORE);
            JobFx.radialWaves(sp, j, dest, 10, 1.0, outerR + 0.5, 0.8, 1.5, 0.4, 0);
            SkillFx.shock(lv, V.up(dest, 0.1), FIRE_GLOW, outerR * 1.1, 0.55);
            SkillFx.play(lv, "entity.generic.explode", dest, 0.7, 1.0);
            SkillFx.play(lv, "entity.ender_dragon.growl", dest, 0.6, 1.0);
            for (LivingEntity e : Kit.around(sp, dest, outerR, 4)) {
                boolean inner = V.hdist(e.position(), dest) <= radius + 0.5 + Kit.bodyPad(e);
                double mult = k.num("impact", 40) * (inner ? 1 : k.num("outer", 0.6)) * hm;
                JobKit.strike(sp, e, j, P, mult, false, inner, life, 0, d -> JobImpact.hit(sp, e, j, inner ? 2.4 : 1.6));
            }
            JobSystem.note(sp, "§6비상 §7높이 " + (int) Math.round(fallen) + "칸 → 피해 ×" + String.format(java.util.Locale.ROOT, "%.2f", hm));
        });
        RuneKit.later(pillarsAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            // 갈라진 땅에서 용의 불기둥 (둘레 고리 셋 — 안에서 바깥으로) · 퍼지는 큰 검기 · 충격파
            for (int ring = 0; ring < 3; ring++) {
                double rr = pillarR * (0.35 + 0.3 * ring);
                int n = 5 + ring * 3;
                int dl = ring * 2;
                for (int q = 0; q < n; q++) {
                    double a = Math.toRadians(q * 360.0 / n + ring * 17);
                    Vec3 p = ground(lv, V.add(dest, new Vec3(Math.cos(a), 0, Math.sin(a)), rr));
                    RuneKit.later(dl, () -> {
                        JobFx.flame(lv, p, 0.9, 0.7, Opt.empty(), FIRE_GLOW, FIRE_CORE);
                        JobFx.fire(lv, V.up(p, 1.4), 0.9, 0.55, Opt.of("vel", new Vec3(0, 3.0, 0)), FIRE_GLOW, FIRE_CORE);
                    });
                }
            }
            JobFx.radialWaves(sp, j, dest, 8, 1.5, pillarR + 0.5, 0.4, 1.2, 0.35, 22);
            SkillFx.shock(lv, V.up(dest, 0.1), JobFx.inner(j), pillarR * 1.05, 0.5);
            SkillFx.play(lv, "item.firecharge.use", dest, 0.6, 1.0);
            SkillFx.play(lv, "entity.blaze.shoot", dest, 0.6, 1.0);
            for (LivingEntity e : Kit.around(sp, dest, pillarR, 4)) {
                JobKit.strike(sp, e, j, P, k.num("pillars", 18), false, true, life, 0, d -> {
                    JobImpact.hit(sp, e, j, 1.8);
                    if (JobKit.pushable(e)) Kit.launch(e, 0, k.num("lift", 0.8), 0);
                });
            }
        });
        return true;
    }
}
