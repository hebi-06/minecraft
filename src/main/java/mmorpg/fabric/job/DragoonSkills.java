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
 * 연타 찌르기를 뒤집어 높이 · 광역 · 내리꽂는 단타 — 웜테일 · 하이 점프 · 임페일 · 드래곤 브레스 · 드래곤폴. 내리꽂기는 셋 (작은 뜀 · 곧장 솟아 겨눈 곳으로 · 하늘
 * 낙하) — 모양이 서로 다르게. 단타는 크게 감았다가 큰 타격.
 *
 * <p>2026-11-13 변신 (사용자 "드라군은 드래곤으로 변해서 공격하는게 더 간지날듯 사람 빼고 드레곤으로 변해서 공격하는걸로" — {@link DragonForm}): 스킬마다 사람이
 * 불꽃 속에 사라지고 그 자리의 붉은 용 (3D 몹 rpg:job_dragon) 이 직접 함 — 웜테일 = 꼬리로 한 바퀴 휩쓸고 뛰어올라 짓밟음, 하이 점프 = 날개를 내려치며 솟구쳐
 * 날갯짓 → 머리부터 내리꽂힘, 임페일 = 목을 내뻗어 물고 뒷발로 일어섰다 땅에 내리꽂음, 드래곤 브레스 = 숨을 들이켰다 입에서 불길, 드래곤폴 = 포효 → 하늘로 →
 * 커지며 내리꽂힘. 숫자 · 틱 · 범위 · 움직임은 그대로 (그림만 — 빛의 창 · 칼끝 빛 · 창 참격은 변신하지 못했을 때만).
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
        DragonForm.init();
        JobSystem.register("dragoon_tail", DragoonSkills::tail);
        JobSystem.register("dragoon_jump", DragoonSkills::jump);
        JobSystem.register("dragoon_impale", DragoonSkills::impale);
        JobSystem.register("dragoon_breath", DragoonSkills::breath);
        JobSystem.register("dragoon_ult", DragoonSkills::ult);
    }

    private static Vec3 chest(ServerPlayer sp) {
        return V.up(sp.position(), 1.15);
    }

    // ------------------------------------------------------------ 붉은 용 (3D 몹 rpg:job_dragon — tools/job_mobs_art.py)

    /** 붉은 용 (2026-11-12 3D 몹 — 2026-11-13 부터 사람이 변신한 몸, DragonForm) */
    static final String DRAGON = "rpg:job_dragon";

    // 2026-11-13 변신한 용의 입 자리 (tools/job_mobs_art.py 동작의 뼈를 셈 — 크기 1 칸 · 용 원점에서 앞 F · 위 U, 동작의 틱마다)
    /** 불을 토하는 자세 (breathe 끝) · 숨을 들이켠 자세 (inhale 끝) 의 입 */
    private static final double BREATHE_F = 2.26, BREATHE_U = 0.94;
    private static final double INHALE_F = 2.04, INHALE_U = 3.01;
    /** 물기 (bite — 0.3초) · 들어 올림 (rear — 0.5초) · 내리꽂기 (slam — 0.15초에 땅) 의 입 */
    private static final double[] BITE_F = {2.47, 2.62, 2.57, 2.58, 2.67, 2.68, 2.68};
    private static final double[] BITE_U = {1.80, 1.17, 0.56, 0.21, 0.34, 0.41, 0.47};
    private static final double[] REAR_F = {2.68, 2.73, 2.71, 2.61, 2.45, 2.22, 1.95, 1.63, 1.58, 1.53, 1.49};
    private static final double[] REAR_U = {0.47, 0.96, 1.44, 1.90, 2.34, 2.73, 3.08, 3.37, 3.40, 3.44, 3.47};
    private static final double[] SLAM_F = {1.49, 2.22, 2.53, 2.34};
    private static final double[] SLAM_U = {3.47, 2.56, 1.40, 0.20};
    /** 문 적 (보통 몹 — 3틱에 걸쳐 따라 보임) 을 용의 뼈 (1틱) 보다 이만큼 앞당겨 옮김 */
    private static final int PREY_LEAD = 2;

    /** 2026-11-12 3D 용 (못 세우면) 대신 쓰던 2D 용의 혼 — 변신하지 못했을 때만 */
    private static boolean flat(DragonForm.Use u) {
        return u == null;
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

    /**
     * 내리꽂은 자리의 타격 그림: 땅에 꽂히는 큰 빛의 창 (변신하지 못했을 때만 — 용이면 갈라진 땅에서 솟는 불길) · 땅 고리 · 터짐 · 불꽃 덩이 · 둘레로 퍼지는 큰 검기 ·
     * 돌 · 소리 (scale = 크기)
     */
    private static void plungeFx(ServerPlayer sp, Jobs.Job j, Vec3 g, double radius, double scale, boolean spear) {
        ServerLevel level = sp.level();
        if (spear) JobFx.spear(level, V.up(g, 0.1), V.DOWN, 1.3 * scale, 0, 0.5, j, Opt.of("fade", 0.4));
        else JobFx.flame(level, g, 0.9 * scale, 0.6, Opt.empty(), FIRE_GLOW, FIRE_CORE);
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
     * 웜테일 (롤 야스오 R — 띄운 적을 따라 오름): 감았다가 sweepAt 틱에 둘레 radius 칸을 휩쓸어 띄움 (sweep · lift) → 휩쓴 적들 가운데 (없으면 앞 1.5칸) 로
     * hop 칸 뛰어올라 slamAt 틱에 내리꽂음 (둘레 slamRadius 칸 slam × 높이 덤). 밀려나지 않음. 변신: 용이 몸을 낮춰 반대로 감았다가 꼬리로 한 바퀴 (spin — 뛰어오르는
     * 동안 이어 돎) → 내려서며 앞을 들었다 온몸으로 짓밟음 (stomp — 0.2초에 땅)
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
        DragonForm.Use u = DragonForm.begin(sp, j, slamAt + 30);
        if (u != null) {
            RuneKit.later(Math.max(1, sweepAt - 3), () -> DragonForm.anim(sp, u, "spin"));
            RuneKit.later(Math.max(sweepAt + 2, slamAt - 4), () -> DragonForm.anim(sp, u, "stomp"));
            RuneKit.later(slamAt + 8, () -> DragonForm.end(sp, u, DragonForm.LINGER));
        } else {
            JobFx.charge(sp, j, new Vec3(-0.45, 0.45, -0.7), sweepAt, 0.6); // 감음: 창을 낮게 뒤로 — 자루 끝에 모이는 빛
        }
        SkillFx.sparks(level0, V.up(c0, 0.2), DUST, 6, 2.5);
        SkillFx.play(level0, "entity.ender_dragon.flap", c0, 1.4, 0.6);
        List<LivingEntity> swept = new ArrayList<>();
        RuneKit.later(sweepAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            // 휩쓸기: 허리 높이 도는 칼날 · 바닥 고리 · 용의 꼬리 같은 금빛 호 (큰 검기 셋) · 먼지 (변신: 꼬리 높이 불꽃 고리 — 꼬리 끝이 지나간 자리)
            JobFx.whirl(lv, V.up(c, 0.7), r, j, 0.3, Opt.of("alpha", 0.95));
            JobFx.ring(lv, V.up(c, 0.08), r, j, false, 0.35, Opt.empty());
            JobFx.radialWaves(sp, j, c, 5, 0.8, r, 0.6, 0.85, 0.22, 40);
            if (u != null) {
                for (int q = 0; q < 8; q++) {
                    double a = Math.toRadians(q * 45 + 20);
                    Vec3 p = V.add(V.up(c, 0.9), new Vec3(Math.cos(a), 0, Math.sin(a)), r * 0.75);
                    RuneKit.later(q / 3, () -> JobFx.fire(lv, p, 0.55, 0.35, Opt.of("vel", new Vec3(0, 0.8, 0)), FIRE_GLOW, FIRE_CORE));
                }
                SkillFx.play(lv, "entity.ender_dragon.growl", c, 0.5, 1.4);
            }
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
                Vec3 dir = len > 0.1 ? new Vec3(d.x, 0, d.z).normalize() : Kit.facingOf(sp);
                double can = RuneKit.dashDistance(sp, dir, len, 0.3, 1.5);
                dest = ground(lv, V.add(c, dir, can));
            }
            if (dest == null) dest = ground(lv, V.add(c, Kit.facingOf(sp), RuneKit.dashDistance(sp, Kit.facingOf(sp), 1.5, 0.3, 1.5)));
            peak[0] = JobMove.hopPeak(sp, c, dest, hop);
            JobTraits11.airFor(sp, slamAt - sweepAt + 2);
            JobMove.run(sp, JobMove.hop(0, slamAt - sweepAt - 1, JobMove.at(dest), peak[0]));
            JobFx.wind(lv, V.up(c, 0.1), 1.6, j, 0.4, Opt.empty());
            JobFx.comet(lv, V.up(c, 0.4), V.UP, 1.0, 10, 0.25, DRAGON_GLOW, DRAGON_CORE, JobFx.hide1p(sp));
        });
        // 꼭대기: 창을 거꾸로 — 창끝에 모이는 불빛 (변신하지 못했을 때만)
        if (flat(u)) {
            RuneKit.later((sweepAt + slamAt) / 2, () -> {
                if (RuneKit.sameLife(sp, life)) JobFx.glint(sp.level(), V.up(sp.position(), -0.2), 0.7, DRAGON_GLOW, DRAGON_CORE, 0.25, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
            });
        }
        RuneKit.later(slamAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 g = ground(lv, sp.position());
            double mult = k.num("slam", 4.35) * JobTraits11.heightMult(sp, j, peak[0]);
            plungeFx(sp, j, g, sr, 1.0, flat(u));
            if (flat(u)) JobFx.slash(sp, j, 1.8, 0.4, 2.0, 90.0, 0.22);
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
    /** 하이 점프로 떠 있는 사람의 변신 (내리꽂을 때 이어 씀) */
    private static final Map<UUID, DragonForm.Use> JUMP_FORMS = new HashMap<>();

    /**
     * 하이 점프 (롤 판테온 W): 웅크렸다가 2틱부터 rise 틱까지 곧장 height 칸 위로 (머리 위 여유까지) → hover 틱 떠 있음 (조준한 곳의 표식 — 4틱마다) → gap 틱 뒤부터
     * 다시 누르면 ({@link #dive}) 조준한 곳으로 혜성처럼 내리꽂음, 창이 닫히면 저절로. 떠 있는 동안 비상 (받는 피해 − air). 변신: 용이 웅크렸다 날개를 세게
     * 내려치며 솟구침 (launch) → 떠 있는 동안 날갯짓 (fly — 사람이 보는 쪽을 봄)
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
        UUID id = sp.getUUID();
        JUMPS.put(id, life);
        JobMove.run(sp, JobMove.to(2, rise, JobMove.up(sp, height), JobMove.OUT), JobMove.hold(rise, rise + hover));
        JobTraits11.airFor(sp, rise + hover + 10);
        Kit.noFall(sp, rise + hover + 40);
        DragonForm.Use u = DragonForm.begin(sp, j, rise + hover + 40);
        if (u != null) {
            JUMP_FORMS.put(id, u);
            DragonForm.anim(sp, u, "launch");
            RuneKit.later(6, () -> {
                if (life.equals(JUMPS.get(id))) DragonForm.hold(sp, u, "fly");
            });
        } else {
            JUMP_FORMS.remove(id);
            JobFx.charge(sp, j, new Vec3(0.0, 0.6, 0.3), 2, 0.5);
        }
        // 웅크림 → 뛰어오름: 발밑 터짐 · 바람 고리 · 위로 솟는 금빛 꼬리 · 소리
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
        // 떠 있는 동안 (4틱마다): 조준한 곳의 표식 (고리 · 가운데 불빛) · 날갯짓 소리 (변신하지 못했으면 등 뒤 2D 용의 혼)
        for (int t = rise - 2; t < rise + hover; t += 4) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!life.equals(JUMPS.get(id)) || !RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 f = Kit.facingOf(sp);
                if (flat(u)) JobFx.dragon(lv, V.up(V.add(sp.position(), f, 0.5), 1.2), 1.7, tt == rise - 2 ? 0 : 2, tt == rise - 2 ? 2 : 4, 0.22,
                        tt != rise - 2, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.8, "solo", 70.0), DRAGON_GLOW, DRAGON_CORE);
                Vec3 d = aimSpot(sp, k.num("range", 10), k.num("fallback", 3), 12);
                JobFx.ring(lv, V.up(d, 0.1), k.num("radius", 3.5), j, (tt / 4) % 2 == 0, 0.22, Opt.of("alpha", 0.7));
                JobFx.glint(lv, V.up(d, 0.3), 0.5, DRAGON_GLOW, DRAGON_CORE, 0.22, Opt.empty());
                if ((tt - rise) % 12 == 0) SkillFx.play(lv, "entity.ender_dragon.flap", sp.position(), 1.5, 0.5);
            });
        }
        JobRecast.open(sp, k, 1, rise + hover, (int) k.num("gap", 10), () -> {
            if (RuneKit.sameLife(sp, life) && life.equals(JUMPS.get(id))) dive(sp, j, k, P, true);
        });
        JobSystem.note(sp, "§6다시 누르면 조준한 곳으로 내리꽂음 §7(" + (rise + hover) / 20.0 + "초)");
        return true;
    }

    /**
     * 하이 점프 내리꽂음: diveTicks 틱에 조준한 곳 (range 칸 — 없으면 앞 fallback 칸) 으로 · 불꽃 꼬리 → 둘레 radius 칸 total × 높이 덤. 변신: 용이 머리를 아래로
     * 날개를 접고 (dive — 조준한 곳을 봄) 함께 내리꽂혀 땅에 닿으며 날개를 활짝 · 포효 (crash)
     */
    private static boolean dive(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, boolean auto) {
        JobRecast.close(sp, k.id());
        if (auto) JobKit.motion(sp, k.id() + ":dive");
        else JobRecast.motion(sp, k.id() + ":dive");
        UUID id = sp.getUUID();
        JUMPS.remove(id);
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
        DragonForm.Use prev = JUMP_FORMS.remove(id);
        DragonForm.Use u = prev != null && DragonForm.owns(sp, prev) ? prev : DragonForm.begin(sp, j, ticks + 40);
        if (u != null) {
            DragonForm.face(sp, u, V.sub(dest, from), ticks + 14);
            DragonForm.hold(sp, u, "dive");
            RuneKit.later(ticks, () -> {
                DragonForm.hold(sp, u, "crash");
                if (DragonForm.owns(sp, u)) JobFx.fire(level, V.up(sp.position(), 0.8), 1.2, 0.5, Opt.of("spin", 0.0), FIRE_GLOW, FIRE_CORE);
            });
            RuneKit.later(ticks + 12, () -> DragonForm.end(sp, u, DragonForm.LINGER));
        } else {
            JobFx.dragon(level, V.up(from, 1.2), 1.9, 7, 1, ticks / 20.0 + 0.05, false, JobFx.hide1p(sp).with("vel", V.scale(dir, speed), "alpha", 0.9), DRAGON_GLOW,
                    DRAGON_CORE);
        }
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
            plungeFx(sp, j, g, rad, 1.3, flat(u));
            JobFx.flame(lv, g, 1.1, 0.6, Opt.empty(), FIRE_GLOW, FIRE_CORE);
            if (flat(u)) JobFx.slash(sp, j, 1.8, 0.45, 2.3, 90.0, 0.22);
            else SkillFx.play(lv, "entity.ender_dragon.growl", g, 0.7, 1.1);
            for (LivingEntity e : Kit.around(sp, g, rad, 3.5)) {
                JobKit.strike(sp, e, j, P, mult, false, false, life, 0, d -> JobImpact.hit(sp, e, j, 1.6));
            }
            if (fallen >= 2) JobSystem.note(sp, "§6비상 §7높이 " + (int) Math.round(fallen) + "칸 → 피해 ×" + String.format(java.util.Locale.ROOT, "%.2f", mult / k.total()));
        });
        return true;
    }

    // ------------------------------------------------------------ 임페일

    /** 임페일: 문 적이 보일 자리 (용 동작의 틱 td 에 입 — 물기 끝 · 들어 올림 · 내리꽂기) */
    private static Vec3 preyMouth(ServerPlayer sp, DragonForm.Use u, int td, int rearAt, int slamStart) {
        double fwd;
        double up;
        if (td < rearAt) {
            fwd = BITE_F[BITE_F.length - 1];
            up = BITE_U[BITE_U.length - 1];
        } else if (td < slamStart) {
            int i = Math.min(REAR_F.length - 1, td - rearAt);
            fwd = REAR_F[i];
            up = REAR_U[i];
        } else {
            int i = Math.min(SLAM_F.length - 1, td - slamStart);
            fwd = SLAM_F[i];
            up = SLAM_U[i];
        }
        return DragonForm.at(sp, u, fwd, up);
    }

    /**
     * 임페일 (롤 세트 R): skewerAt 틱에 앞 range 칸의 적 하나 (조준 · arc 도 도움 — 없으면 쓰지 않음) 를 꿰어 (skewer) 머리 위로 높이 (lift 칸) 들어 올려 → slamAt 틱에
     * 앞 땅에 내리꽂음 (둘레 radius 칸 slam — 꿰인 적 포함 · 보스 아닌 적 stun 틱 기절). 보스 · 큰 몹 · 밀 수 없는 적은 들지 않고 그 자리에서 내리꽂음. 밀려나지 않음.
     * 변신: 용이 그 적을 보고 목을 내뻗어 물고 (bite — skewerAt 에 다묾 · 적을 입으로 낚아챔) 뒷발로 일어서며 높이 들어 올렸다가 (rear) 앞 땅에 내리꽂음 (slam — 적이
     * 입을 따라감)
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
        Vec3 toE = V.flat(V.sub(e.position(), c0));
        Vec3 f = Math.hypot(toE.x, toE.z) > 0.3 ? V.unit(toE) : Kit.facingOf(sp);
        RpgCombat.setSteadfast(sp, slamAt + 4);
        int rearAt = skewerAt + 1;
        int slamStart = slamAt - 3;
        DragonForm.Use u = DragonForm.begin(sp, j, slamAt + 40);
        if (u != null) {
            DragonForm.face(sp, u, f, slamAt + 14);
            RuneKit.later(Math.max(1, skewerAt - 4), () -> DragonForm.hold(sp, u, "bite"));
            RuneKit.later(rearAt, () -> DragonForm.hold(sp, u, "rear"));
            RuneKit.later(slamStart, () -> DragonForm.hold(sp, u, "slam"));
            RuneKit.later(slamAt + 12, () -> DragonForm.end(sp, u, DragonForm.LINGER));
        } else {
            JobFx.charge(sp, j, new Vec3(0.35, 1.25, -0.55), skewerAt, 0.8);
        }
        SkillFx.play(level0, "entity.ender_dragon.growl", c0, 1.6, 0.4);
        RuneKit.later(skewerAt, () -> {
            if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
            ServerLevel lv = sp.level();
            Vec3 b = Kit.bodyPoint(e);
            if (flat(u)) {
                JobFx.line(lv, V.add(chest(sp), f, 0.6), b, j, 0.25, Opt.of("alpha", 0.95));
                JobFx.stab(lv, Kit.bodyToward(e, sp.getEyePosition(), sp.getViewVector(1F)), 1.0 + Kit.bodyPad(e) * 0.4, j, 0.3, Opt.of("vel", RuneKit.entVel(e)));
            } else {
                // 물기: 입에서 적에게 뻗는 불빛 줄기 (낚아챔) · 이빨 자국 불꽃 · 깨무는 소리
                Vec3 m = DragonForm.at(sp, u, BITE_F[4], BITE_U[4]);
                RuneKit.streak(lv, m, b, FIRE_GLOW, 0.12, 0.3, 0.9, 0);
                JobFx.fire(lv, b, 0.8 + Kit.bodyPad(e) * 0.3, 0.35, Opt.of("vel", RuneKit.entVel(e)), FIRE_GLOW, FIRE_CORE);
                SkillFx.play(lv, "entity.evoker_fangs.attack", b, 0.9, 0.8);
            }
            JobFx.burst(lv, b, 0.9 + Kit.bodyPad(e) * 0.4, j, 0.28, Opt.of("vel", RuneKit.entVel(e)));
            SkillFx.play(lv, "item.trident.hit", b, 0.8, 1.0);
            SkillFx.play(lv, "entity.player.attack.crit", b, 0.9, 0.8);
            JobKit.strike(sp, e, j, P, k.num("skewer", 2.6), false, false, life, 0, d -> JobImpact.hit(sp, e, j, 1.2));
        });
        double ahead = flat(u) ? k.num("ahead", 2.5) : SLAM_F[SLAM_F.length - 1] * DragonForm.SCALE;
        Vec3 land = ground(level0, V.add(c0, f, RuneKit.dashDistance(sp, f, ahead, 0.3, 1.5)));
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
                    if (DragonForm.owns(sp, u)) {
                        // 용의 입을 따라감 (몸 가운데가 입 — 땅 아래로는 가지 않음) · 처음 2틱은 낚아채듯 입으로 끌려옴
                        Vec3 m = preyMouth(sp, u, tt + PREY_LEAD, rearAt, slamStart);
                        p = new Vec3(m.x, Math.max(land.y, m.y - e.getBbHeight() * 0.5), m.z);
                        if (tt + PREY_LEAD >= slamAt) p = land;
                        else if (tt <= skewerAt + 2) p = V.lerp(start[0], p, (tt - skewerAt) / 2.0);
                    } else if (tt <= upEnd) {
                        double w = JobMove.ease((tt - skewerAt) / (double) Math.max(1, upEnd - skewerAt), JobMove.OUT);
                        Vec3 ctl = V.up(start[0], liftH * 0.9);
                        Vec3 a = V.lerp(start[0], ctl, w);
                        Vec3 b2 = V.lerp(ctl, top, w);
                        p = V.lerp(a, b2, w);
                    } else if (tt <= downEnd) {
                        double w = (tt - upEnd) / (double) Math.max(1, downEnd - upEnd);
                        p = V.lerp(top, land, w * w);
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
            plungeFx(sp, j, g, radius, 1.5, flat(u));
            JobFx.quake(lv, V.up(g, 0.05), radius, j, 0.55, Opt.empty());
            if (flat(u)) JobFx.dragon(lv, V.up(g, 2.6), 2.4, 5, 1, 0.45, false, Opt.of("alpha", 0.85), DRAGON_GLOW, DRAGON_CORE);
            for (int q = 0; q < 6; q++) {
                double a = Math.toRadians(q * 60 + 30);
                Vec3 p = ground(lv, V.add(g, new Vec3(Math.cos(a), 0, Math.sin(a)), radius * 0.6));
                RuneKit.later(q % 3, () -> JobFx.fire(lv, V.up(p, 0.8), 0.8, 0.5, Opt.empty(), FIRE_GLOW, FIRE_CORE));
            }
            if (flat(u)) JobFx.slash(sp, j, 2.0, 0.5, 2.6, 90.0, 0.25);
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
     * 드래곤 브레스 (롤 쉬바나 E — 태세 칸 몫 피해): 포효 — 숨을 들이켬 (입으로 빨려 드는 불티 · 커지는 불빛, 느려짐 · 밀려나지 않음) → breathAt 틱에 앞 부채꼴
     * (range 칸 · cone 도) 로 불길을 토함 (흘러가며 커지는 불꽃 덩이 · 땅의 불 — 맞은 적 breath · 밀쳐냄). 변신: 용이 목을 뒤 위로 당겨 숨을 들이켰다가 (inhale)
     * 목을 앞 아래로 내밀어 입을 활짝 (breathe) — 불길은 용의 입에서 (사람이 보는 쪽)
     */
    static boolean breath(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int breathAt = Math.max(4, (int) k.num("breathAt", 14));
        double range = k.num("range", 9);
        double cone = k.num("cone", 70);
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        DragonForm.Use u = DragonForm.begin(sp, j, breathAt + 40);
        RpgCombat.setSteadfast(sp, breathAt + 8);
        sp.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, breathAt + 6, 3, false, false));
        // 발밑 불꽃 고리 (변신하지 못했으면 창을 땅에 꽂는 소리)
        JobFx.ring(level0, V.up(c0, 0.08), 1.8, j, false, 0.4, Opt.empty());
        if (flat(u)) SkillFx.play(level0, "item.trident.hit_ground", c0, 0.8, 0.9);
        SkillFx.play(level0, "entity.ender_dragon.growl", c0, 0.9, 0.7);
        // 변신하지 못했으면 예전 2D 용의 혼: 생겨남 (0 · 1) → 날갯짓 → breathAt 에 포효
        Vec3 dragonAt = V.up(V.add(c0, f, 0.7), 1.7);
        if (u != null) {
            RuneKit.later(2, () -> DragonForm.hold(sp, u, "inhale"));
            RuneKit.later(breathAt + 14, () -> DragonForm.end(sp, u, DragonForm.LINGER));
        } else {
            JobFx.dragon(level0, dragonAt, 2.1, 0, 2, 0.3, false, JobFx.hide1p(sp).with("alpha", 0.9), DRAGON_GLOW, DRAGON_CORE);
            RuneKit.later(6, () -> {
                if (RuneKit.sameLife(sp, life)) JobFx.dragon(sp.level(), dragonAt, 2.1, 2, 4, (breathAt - 6) / 20.0, true, JobFx.hide1p(sp).with("alpha", 0.9),
                        DRAGON_GLOW, DRAGON_CORE);
            });
        }
        // 숨을 들이켬 (2틱마다): 둘레에서 입으로 빨려 드는 불티 · 커지는 불빛
        for (int t = 2; t < breathAt; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                double w = tt / (double) breathAt;
                Vec3 mouth = u != null ? DragonForm.at(sp, u, INHALE_F, INHALE_U) : V.up(V.add(c0, f, 0.9), 3.3);
                for (int q = 0; q < 3; q++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 from = V.add(mouth, new Vec3(Math.cos(a) * 2.2, (Kit.rand() - 0.3) * 1.6, Math.sin(a) * 2.2), 1.0);
                    RuneKit.streak(lv, from, mouth, FIRE_GLOW, 0.06, 0.18, 0.8, 0);
                }
                JobFx.glint(lv, mouth, 0.3 + 0.6 * w, FIRE_GLOW, FIRE_CORE, 0.14, Opt.empty());
                if (tt % 6 == 0) SkillFx.play(lv, "entity.blaze.ambient", mouth, 0.6 + w * 0.6, 0.5);
            });
        }
        RuneKit.later(breathAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            sp.removeEffect(MobEffects.SLOWNESS);
            ServerLevel lv = sp.level();
            Vec3 ff = Kit.facingOf(sp);
            Vec3 mouth;
            if (DragonForm.owns(sp, u)) {
                DragonForm.face(sp, u, ff, 14);
                DragonForm.hold(sp, u, "breathe");
                mouth = DragonForm.at(sp, u, BREATHE_F, BREATHE_U);
            } else {
                mouth = V.up(V.add(c0, f, 0.9), 3.3);
                if (flat(u)) JobFx.dragon(lv, dragonAt, 2.2, 6, 1, 0.5, false, JobFx.hide1p(sp).with("alpha", 0.95), DRAGON_GLOW, DRAGON_CORE);
            }
            // 입 앞 번쩍 · 첫 불길 (토하는 순간이 보이게)
            JobFx.burst(lv, V.add(mouth, ff, 0.8), 1.3, FIRE_GLOW, FIRE_CORE, 0.3, Opt.empty());
            JobFx.fire(lv, V.add(mouth, ff, 1.2), 1.5, 0.4, Opt.of("vel", V.scale(V.unit(V.add(ff, new Vec3(0, -0.35, 0), 1.0)), 7.0), "grow", 1.6), FIRE_GLOW, FIRE_CORE);
            SkillFx.play(lv, "entity.ender_dragon.shoot", mouth, 0.8, 1.0);
            SkillFx.play(lv, "entity.blaze.shoot", mouth, 0.7, 0.9);
            SkillFx.play(lv, "item.firecharge.use", mouth, 0.8, 0.9);
            // 불길: 입에서 부채꼴로 흘러가며 커지는 불꽃 덩이 (카메라를 봄 — 앞으로 눕는 그림은 뒤 3인칭에서 보이지 않음) · 땅의 불
            double half = Math.toRadians(cone / 2);
            Vec3 base = sp.position();
            for (int q = 0; q < 22; q++) {
                double a = (Kit.rand() * 2 - 1) * half * 0.85;
                double dist = range * (0.55 + 0.45 * Kit.rand());
                Vec3 dirq = V.turnY(ff, Math.toDegrees(a));
                Vec3 end = V.up(V.add(base, dirq, dist), 0.6 + Kit.rand() * 0.8);
                Vec3 vel = V.scale(V.sub(end, mouth), 1 / 0.45);
                int dl = q % 4;
                Vec3 m0 = mouth;
                // 2026-11-13 변신 영상 1: 과녁 뒤에 묻혀 작게 보였음 → 불꽃 덩이 크게
                double sz = (0.95 + 0.5 * Kit.rand()) * (flat(u) ? 1.0 : 1.35);
                RuneKit.later(dl, () -> JobFx.fire(lv, V.add(m0, ff, 0.6), sz, 0.5, Opt.of("vel", vel, "grow", 1.9, "spin", Kit.rand() * 40 - 20), FIRE_GLOW,
                        FIRE_CORE));
            }
            for (int q = 0; q < 9; q++) {
                double a = (q / 8.0 * 2 - 1) * half * 0.8;
                double dist = range * (0.35 + 0.6 * ((q * 37) % 10) / 10.0);
                Vec3 p = ground(lv, V.add(base, V.turnY(ff, Math.toDegrees(a)), dist));
                RuneKit.later(4 + q % 4, () -> JobFx.flame(lv, p, 0.55 + 0.25 * Kit.rand(), 0.8, Opt.empty(), FIRE_GLOW, FIRE_CORE));
            }
            if (flat(u)) JobFx.slash(sp, j, 2.0, -0.2, 2.4, 0.0, 0.25);
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

    /** 드래곤폴의 용 크기: 내리꽂을 때 (날개 끝 사이 약 10칸 — 내리꽂기 앞 4틱에 커짐) */
    static final double ULT_BIG = 1.9;

    /**
     * 드래곤폴 (롤 판테온 R): 용의 하늘 (rpg:ult_wyrm) — 크게 웅크려 soarAt 틱에 하늘 높이 (height 칸 — 머리 위 여유까지) 솟구침 (그때부터 내리꽂을 때까지 받는 피해
     * 없음) → hideAt ~ diveAt 조준한 곳 (range 칸 — 없으면 앞 fallback 칸, 쓴 때 정함) 위로 옮겨 감 · 그곳에 금빛 용의 표식이 번짐 → diveAt 틱부터 내리꽂혀
     * impactAt 틱에 둘레 radius 칸 impact (그 밖 outerRadius 칸까지 × outer) × 높이 덤 → pillarsAt 틱에 갈라진 땅에서 용의 불기둥이 둘레로 터져 오름 (둘레
     * pillarRadius 칸 pillars · 띄움). 변신: 용이 포효 (roar) → 날개를 내려치며 (launch) 하늘로 → 조준한 곳 위로 날아감 (fly — 그곳을 봄) → 내리꽂기 앞 4틱에
     * 거대해져 (1.6) 머리부터 내리꽂힘 (dive) → 땅에 닿아 날개를 활짝 · 포효 (crash)
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
        DragonForm.Use u = DragonForm.begin(sp, j, pillarsAt + 40);
        if (u != null) {
            Vec3 toDest = V.flat(V.sub(dest, c0));
            DragonForm.face(sp, u, Math.hypot(toDest.x, toDest.z) > 1.0 ? toDest : f, impactAt + 24);
            RuneKit.later(1, () -> DragonForm.anim(sp, u, "roar"));
            RuneKit.later(soarAt - 1, () -> DragonForm.anim(sp, u, "launch"));
            RuneKit.later(soarAt + 5, () -> DragonForm.hold(sp, u, "fly"));
            RuneKit.later(diveAt - 4, () -> DragonForm.resize(sp, u, ULT_BIG, 4));
            RuneKit.later(diveAt - 1, () -> DragonForm.hold(sp, u, "dive"));
            RuneKit.later(impactAt, () -> DragonForm.hold(sp, u, "crash"));
            RuneKit.later(pillarsAt + 8, () -> DragonForm.end(sp, u, DragonForm.LINGER));
        } else {
            JobFx.charge(sp, j, new Vec3(0.2, 1.7, 0.3), soarAt, 1.0);
            JobFx.dragon(level0, V.up(V.add(c0, f, 0.9), 2.0), 2.8, 0, 2, 0.3, false, JobFx.hide1p(sp).with("alpha", 0.9), DRAGON_GLOW, DRAGON_CORE);
            RuneKit.later(6, () -> {
                if (RuneKit.sameLife(sp, life)) JobFx.dragon(sp.level(), V.up(V.add(c0, f, 0.9), 2.0), 2.8, 5, 2, (soarAt - 6) / 20.0 + 0.05, false, JobFx.hide1p(sp)
                        .with("alpha", 0.9), DRAGON_GLOW, DRAGON_CORE);
            });
        }
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
                double w = Math.min(1, (tt - hideAt + 2) / (double) Math.max(1, diveAt - hideAt));
                JobFx.ring(lv, V.up(dest, 0.1), outerR * (0.4 + 0.6 * w), j, (tt / 4) % 2 == 0, 0.22, Opt.of("alpha", 0.75));
                JobFx.ring(lv, V.up(dest, 0.12), radius, j, (tt / 4) % 2 != 0, 0.22, Opt.of("alpha", 0.9));
                JobFx.glint(lv, V.up(dest, 0.4), 0.6 + 0.6 * w, DRAGON_GLOW, DRAGON_CORE, 0.22, Opt.empty());
                if ((tt - hideAt) % 8 == 0) JobFx.fire(lv, V.up(dest, 0.6), 0.6 + 0.4 * w, 0.4, Opt.empty(), FIRE_GLOW, FIRE_CORE);
                if ((tt - hideAt) % 12 == 0) SkillFx.play(lv, "entity.ender_dragon.flap", dest, 0.9, 0.8);
                // 2026-11-06 영상 1: 하늘에 있는 동안 (1초) 화면이 빈 하늘 · 먼 땅뿐이었음 → 표식 자리를 위에서 내리비추는 불빛 기둥 (변신하지 못했으면 내 앞 아래에서
                // 날갯짓하는 2D 붉은 용)
                JobFx.spotlight(lv, dest, 4 + 8 * w, 0.24, Opt.of("alpha", 0.55 + 0.3 * w), FIRE_GLOW, FIRE_CORE);
                if (tt >= hideAt && tt < diveAt && flat(u)) {
                    Vec3 ff = Kit.facingOf(sp);
                    JobFx.dragon(lv, V.up(V.add(sp.position(), ff, 2.6), -1.5), 3.0, 2, 4, 0.24, true, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.9,
                            "solo", 70.0), DRAGON_GLOW, DRAGON_CORE);
                }
            });
        }
        // 내리꽂음: 불꽃 꼬리 (변신하지 못했으면 거대한 2D 용)
        RuneKit.later(diveAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 p = sp.position();
            top[0] = Math.max(top[0], p.y);
            double secs = (impactAt - diveAt) / 20.0;
            Vec3 vel = V.scale(V.sub(dest, p), 1 / secs);
            if (flat(u)) JobFx.dragon(lv, V.up(p, 2.5), 5.0, 7, 1, secs + 0.08, false, Opt.of("vel", vel, "alpha", 0.95), DRAGON_GLOW, DRAGON_CORE);
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
            plungeFx(sp, j, dest, outerR, 2.4, flat(u));
            JobFx.quake(lv, V.up(dest, 0.05), outerR, j, 0.6, Opt.empty());
            JobFx.burst(lv, V.up(dest, 1.2), 3.4, j, 0.4, Opt.empty());
            if (flat(u)) JobFx.dragon(lv, V.up(dest, 3.4), 3.6, 6, 1, 0.55, false, Opt.of("alpha", 0.9), DRAGON_GLOW, DRAGON_CORE);
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
