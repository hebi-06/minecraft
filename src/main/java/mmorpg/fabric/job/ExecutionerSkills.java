package mmorpg.fabric.job;

import java.util.List;
import mmorpg.core.Jobs;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 사신 반전 엑시큐셔너 (재설계안 2판 — 2026-11-06 · 3판 2026-11-07 직업 스킬 도감 표시 고침 — 사용자 "컨셉은 좋은데 스킬들이 간지가 안남": 컨셉 · 특성 「처형」 ·
 * 칸 몫 · 숫자는 그대로, 스킬 다섯을 새로) — jobs.json advance executioner, 해 질 녘 금빛 · 짙은 보라 (JobTraits12): 1차의 많이 거두고 흡혈로 버티는 사신을 뒤집어
 * 하나를 끝내는 처형인 — 헤드헌트 · 레이븐 시프트 · 체인 오브 저지먼트 · 데스 넬 · 길로틴 오브 헤븐. 몸 쓰는 직업이라 제 몸 · 제 낫 동작만 (까마귀 · 사슬 · 종 ·
 * 하늘의 단두대는 그림). 단타는 크게 감았다가 큰 타격. 직업 스킬 (궁극기 · 태세 빼고) 로 쓰러뜨리면 그 스킬의 재사용 대기 초기화 (JobTraits12.killedBy), 궁극기는
 * 쓰러뜨리면 다시 쓰기 (JobRecast).
 */
final class ExecutionerSkills {
    private ExecutionerSkills() {}

    static final double[] DUSK = JobTraits12.DUSK;
    static final double[] DUSK_CORE = JobTraits12.DUSK_CORE;
    static final double[] VIOLET = JobTraits12.VIOLET;
    static final double[] VIOLET_CORE = {0.96, 0.78, 1.0};
    /** 하늘의 단두대의 몸 (빛 칸 — 짙은 보라) · 칼날 · 테 (심지 칸 — 금빛) */
    static final double[] DARK = {0.34, 0.1, 0.46};
    static final double[] BLADE = {1.0, 0.78, 0.4};
    /** 까마귀 그림 (rpg:odin_raven — 금빛 테를 두른 검은 까마귀 그림에 곱함): 거의 그대로 (살짝 보랏빛) */
    static final double[] RAVEN = {1.0, 0.9, 1.0};
    /** 하늘의 단두대 solo 번호 (주인마다 한 장 — 다시 쓰기 단계마다 따로: 앞 단두대가 아직 내리치는 중이어도 지우지 않게) */
    static final double GALLOWS_SOLO = 171.0;

    static void register() {
        JobSystem.register("executioner_headhunt", ExecutionerSkills::headhunt);
        JobSystem.register("executioner_raven", ExecutionerSkills::raven);
        JobSystem.register("executioner_chains", ExecutionerSkills::chains);
        JobSystem.register("executioner_knell", ExecutionerSkills::knell);
        JobSystem.register("executioner_ult", ExecutionerSkills::ult);
    }

    /** p 자리의 땅 (턱 하나 — 없으면 p 높이) */
    private static Vec3 ground(ServerLevel level, Vec3 p) {
        return new Vec3(p.x, RuneKit.groundAt(level, V.up(p, 0.6), p.y), p.z);
    }

    /** 맞은 적이 쓰러졌으면 그 스킬의 재사용 대기 초기화 (궁극기 빼고 — JobTraits12.killedBy) */
    private static void after(ServerPlayer sp, LivingEntity e, Jobs.Skill k) {
        if (e.isRemoved() || !e.isAlive()) JobTraits12.killedBy(sp, k);
    }

    /** ticks 틱에 걸쳐 e 쪽으로 돌아섬 (start 틱부터 — 남은 각의 1/3 · 1/2 · 나머지, 고개는 −20 ~ 35°): 암살자 베놈 대시의 돌아섬과 같음 */
    private static void turnToward(ServerPlayer sp, LivingEntity e, int start, int ticks, String life) {
        for (int t = 0; t < ticks; t++) {
            int left = ticks - t;
            RuneKit.later(start + t, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
                float[] rot = Kit.lookRotation(sp.getEyePosition(), Kit.bodyPoint(e));
                float dy = Mth.wrapDegrees(rot[0] - sp.getYRot());
                float pitch = Mth.clamp(rot[1], -20F, 35F);
                Kit.moveTo(sp, sp.position(), sp.getYRot() + dy / left, sp.getXRot() + (pitch - sp.getXRot()) / left);
            });
        }
    }

    /** 앞 reach 칸 · arc° 의 적 하나: 겨눈 적 → 가장 가까운 적 (없으면 null) */
    private static LivingEntity front(ServerPlayer sp, double reach, double arc) {
        List<LivingEntity> cone = RuneKit.coneTargets(sp, reach, arc, 10);
        LivingEntity aim = Kit.pickTarget(sp, reach + 0.5, arc / 2);
        if (aim != null && cone.contains(aim)) return aim;
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : cone) {
            double d = e.distanceToSqr(sp);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** 까마귀 한 마리 (rpg:odin_raven — 날갯짓 · 머리가 가는 쪽): at 에서 vel (칸/초) 로 life 초 */
    static void raven(ServerLevel level, Vec3 at, Vec3 vel, double size, double life, double alpha) {
        double speed = V.len(vel);
        Vec3 dir = speed > 1e-6 ? V.scale(vel, 1 / speed) : new Vec3(1, 0, 0);
        SkillFx.fx(level, "rpg:odin_raven", at, Opt.of("dir", dir, "size", size, "life", life, "speed", speed, "spin", Kit.rand() * 8, "color", RAVEN, "alpha",
                alpha, "move", dir));
    }

    // ------------------------------------------------------------ 헤드헌트 (2026-11-07 3판 — 컨뎀 대신)

    /**
     * 헤드헌트 (롤 다리우스 W): 낫을 등 뒤로 크게 젖혔다가 slashAt 틱에 앞 reach 칸 (arc°) 의 적 하나 (겨눈 적 — 없으면 가장 가까운 적) 를 가로로 크게 벰 (slash — 거대한
     * 초승달 검기 · 큰 숫자) → gap 틱 뒤부터 window 틱 안에 다시 누르면 반대로 휘둘러 X (xAt 틱 뒤 x — 그 적에 mark 틱 선고 표식: 받는 직업 스킬 피해 + markBonus · 머리 위
     * 해골). 다시 누르지 않으면 그대로 끝. 적이 없으면 헛벰
     */
    static boolean headhunt(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (JobRecast.stage(sp, k.id()) >= 1) {
            JobRecast.close(sp, k.id());
            JobRecast.motion(sp, k.id() + ":x");
            headhuntX(sp, j, k, P);
            return true;
        }
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int slashAt = Math.max(1, (int) k.num("slashAt", 6));
        double reach = k.num("reach", 4.5);
        double arc = k.num("arc", 70);
        JobTraits12.beginSkill(sp, k);
        RpgCombat.setSteadfast(sp, slashAt + 3);
        JobRecast.open(sp, k, 1, (int) k.num("window", 40), (int) k.num("gap", 14), null);
        // 젖힘: 낫을 등 뒤로 — 날 끝에 모이는 금빛 · 발밑 보랏빛 고리 · 날 가는 소리
        JobFx.charge(sp, j, new Vec3(-0.6, 1.5, -0.7), slashAt, 0.75);
        JobFx.halo(level0, V.up(sp.position(), 0.08), 1.6, j, slashAt / 20.0 + 0.15, Opt.of("alpha", 0.7));
        SkillFx.play(level0, "item.trident.return", sp.position(), 0.55, 0.8);
        RuneKit.later(slashAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            Vec3 f = Kit.facingOf(sp);
            LivingEntity e = front(sp, reach, arc);
            JobTraits12.headhuntTarget(sp, e);
            // 가로로 크게: 눈앞 큰 베기 · 몸 앞 거대한 초승달 검기 (앞 6칸 — 벽 앞까지) · 가로 참격 판
            JobFx.slash(sp, j, 2.3, 0.15, 2.8, 180.0, 0.26);
            JobFx.slashWave(sp, j, 180.0, 2.6, 6, 16);
            JobFx.slashCard(lv, V.up(V.add(c, f, 1.9), 1.15), f, 180.0, 5.2, 0.3, Vec3.ZERO, j, JobFx.hide1p(sp));
            SkillFx.play(lv, "entity.player.attack.sweep", c, 0.5, 1.0);
            SkillFx.play(lv, "item.trident.hit", c, 0.7, 0.8);
            if (e == null) return;
            JobKit.strike(sp, e, j, P, k.num("slash", 4.0), false, true, life, 0, d -> {
                JobImpact.hit(sp, e, j, 1.4);
                if (e.level() instanceof ServerLevel el) {
                    Vec3 b = Kit.bodyPoint(e);
                    JobFx.slashCard(el, b, V.unit(V.sub(b, sp.getEyePosition())), 180.0, 3.2 + Kit.bodyPad(e), 0.3, RuneKit.entVel(e), j, Opt.empty());
                    JobFx.burst(el, b, 1.0 + Kit.bodyPad(e) * 0.4, DUSK, DUSK_CORE, 0.3, Opt.of("vel", RuneKit.entVel(e)));
                    SkillFx.sparks(el, b, DUSK, 10, 5.0);
                }
                if (e.isRemoved() || !e.isAlive()) JobRecast.close(sp, k.id());
                after(sp, e, k);
            });
        });
        return true;
    }

    /** 헤드헌트 다시 누름: 반대로 휘둘러 X (xAt 틱 — 앞 벰의 적 · 없으면 앞의 적) · 선고 표식 */
    private static void headhuntX(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        String life = RuneKit.lifeToken(sp);
        int xAt = Math.max(1, (int) k.num("xAt", 3));
        RpgCombat.setSteadfast(sp, xAt + 3);
        JobFx.charge(sp, j, new Vec3(0.6, 1.6, -0.5), xAt, 0.6);
        SkillFx.play(sp.level(), "item.trident.return", sp.position(), 0.8, 0.7);
        RuneKit.later(xAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            Vec3 f = Kit.facingOf(sp);
            LivingEntity e = JobTraits12.headhuntTarget(sp);
            if (e == null || !e.isAlive() || e.isRemoved() || e.distanceTo(sp) > k.num("reach", 4.5) + 2.5) e = front(sp, k.num("reach", 4.5), k.num("arc", 70));
            // 반대로: 비스듬히 위로 엇갈려 X 를 이룸 — 큰 베기 · 검기 · 비스듬한 참격 판
            JobFx.slash(sp, j, 2.3, 0.15, 2.8, 315.0, 0.26);
            JobFx.slashWave(sp, j, 315.0, 2.4, 6, 16);
            JobFx.slashCard(lv, V.up(V.add(c, f, 1.9), 1.15), f, 315.0, 4.8, 0.3, Vec3.ZERO, j, JobFx.hide1p(sp));
            SkillFx.play(lv, "entity.player.attack.sweep", c, 0.65, 1.0);
            if (e == null) return;
            LivingEntity t = e;
            JobKit.strike(sp, t, j, P, k.num("x", 2.25), false, true, life, 0, d -> {
                JobTraits12.condemn(sp, t, (int) k.num("mark", 100));
                JobImpact.hit(sp, t, j, 1.6);
                if (t.level() instanceof ServerLevel el) {
                    Vec3 b = Kit.bodyPoint(t);
                    JobFx.cross(el, b, 1.2 + Kit.bodyPad(t) * 0.5, DUSK, DUSK_CORE, 0.4, Opt.of("vel", RuneKit.entVel(t), "spin", 0.0));
                    JobFx.slashCard(el, b, V.unit(V.sub(b, sp.getEyePosition())), 315.0, 3.2 + Kit.bodyPad(t), 0.3, RuneKit.entVel(t), j, Opt.empty());
                    JobFx.skull(el, V.up(t.position(), t.getBbHeight() + 0.75), 0.55, 0, 2, 0.35, false, Opt.of("vel", RuneKit.entVel(t)), j);
                    SkillFx.sparks(el, b, DUSK, 14, 5.5);
                    SkillFx.play(el, "block.bell.use", b, 0.55, 0.6);
                }
                JobSystem.note(sp, "§6선고 §7— 받는 직업 스킬 피해 +" + Math.round(k.num("markBonus", 0.1) * 100) + "% (" + k.num("mark", 100) / 20.0 + "초)");
                after(sp, t, k);
            });
        });
    }

    // ------------------------------------------------------------ 레이븐 시프트 (2026-11-07 3판 — 섀도 스토크 대신)

    /**
     * 레이븐 시프트 (롤 스웨인 · 카직스 E): 몸이 까마귀 떼로 흩어져 (flyTicks 틱 — 피해 받지 않음) 겨눈 적 (range 칸) 의 건너편 (behind 칸 — 막혔으면 비스듬히) 으로 날아가
     * 모이며 낫으로 베어 올림 (cut) · nextFor 틱 안의 다음 직업 스킬 + next. 겨눈 적이 없거나 움직일 수 없으면 쓰지 않음
     */
    static boolean raven(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        LivingEntity e = Kit.pickTarget(sp, k.num("range", 10), 25);
        if (e == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다", 30);
            return false;
        }
        String life = RuneKit.lifeToken(sp);
        int fly = Math.max(4, (int) k.num("flyTicks", 8));
        Vec3 start = sp.position();
        Vec3 d0 = V.sub(e.position(), start);
        Vec3 toward = Math.hypot(d0.x, d0.z) > 0.1 ? V.flat(d0) : Kit.facingOf(sp); // V.flat 은 단위 벡터
        double pad = Kit.bodyPad(e);
        Vec3 dest = JobTraits12.beyond(sp, e.position(), toward, k.num("behind", 1.4) + pad);
        if (dest == null) dest = JobTraits12.beyond(sp, e.position(), V.scale(toward, -1), 0.9 + pad);
        if (dest == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 내려설 자리가 없습니다", 30);
            return false;
        }
        Vec3 land = dest;
        ServerLevel level0 = sp.level();
        double peak = JobMove.hopPeak(sp, start, land, 0.9 + 0.05 * V.hdist(start, land));
        RpgCombat.setSteadfast(sp, fly + 3);
        JobTraits12.ravenGuard(sp, fly + 1);
        sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, fly, 0, false, false, false)); // 몸은 까마귀 떼가 됨 (내려서면 다시 보임)
        JobMove.run(sp, JobMove.hop(0, fly, JobMove.at(land), peak));
        turnToward(sp, e, fly - 2, 3, life);
        // 흩어짐: 몸에서 까마귀 열둘이 터져 나와 · 검보라 연기 · 깃털 (금빛 불티) · 날갯짓 소리
        Vec3 side = V.rightOf(toward);
        int half = fly / 2;
        for (int i = 0; i < 12; i++) {
            double s = (i % 2 == 0 ? 1 : -1) * (0.4 + (i / 2) * 0.32);
            double up = 0.6 + (i % 4) * 0.35;
            double size2 = 0.42 + (2 - i % 3) * 0.06;
            Vec3 from = V.add(V.up(start, up), side, s * 0.4);
            Vec3 mid = V.up(V.add(V.lerp(start, land, 0.5), side, s * 1.3), up + 1.0 + (i % 3) * 0.4);
            Vec3 to = V.add(V.up(land, 0.8 + (i % 4) * 0.25), side, s * 0.3);
            raven(level0, from, V.scale(V.sub(mid, from), 20.0 / half), 0.42 + (i % 3) * 0.06, half / 20.0 + 0.02, 0.95);
            RuneKit.later(half, () -> {
                if (RuneKit.sameLife(sp, life)) raven(sp.level(), mid, V.scale(V.sub(to, mid), 20.0 / (fly - half)), size2, (fly - half) / 20.0 + 0.02, 0.95);
            });
        }
        JobFx.smoke(level0, V.up(start, 0.8), 1.2, VIOLET, DUSK_CORE, 0.5, JobFx.hide1p(sp).with("alpha", 0.8));
        SkillFx.sparks(level0, V.up(start, 1.0), DUSK, 10, 4.0);
        SkillFx.play(level0, "entity.phantom.flap", start, 0.9, 1.0);
        SkillFx.play(level0, "entity.parrot.fly", start, 0.7, 0.9);
        SkillFx.play(level0, "entity.enderman.teleport", start, 1.4, 0.4);
        // 날아가는 동안: 몸을 감싸는 까마귀 (2틱마다 — 몸과 같이 감) · 깃털
        for (int t = 2; t < fly; t += 2) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 c = V.up(sp.position(), 1.0);
                Vec3 vel = RuneKit.bodyVel(sp);
                for (int q = 0; q < 3; q++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 p = V.add(c, new Vec3(Math.cos(a) * 0.6, (Kit.rand() - 0.5) * 0.8, Math.sin(a) * 0.6), 1.0);
                    raven(lv, p, V.add(vel, new Vec3(-Math.sin(a), 0.3, Math.cos(a)), 3.0), 0.36, 0.14, 0.9);
                }
                SkillFx.sparks(lv, c, VIOLET_CORE, 2, 1.5);
            });
        }
        // 모여 베어 올림
        RuneKit.later(fly, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3;
                Vec3 from = V.add(V.up(c, 1.0), new Vec3(Math.cos(a), 0.4, Math.sin(a)), 1.6);
                raven(lv, from, V.scale(V.sub(V.up(c, 1.0), from), 20.0 / 3), 0.38, 0.15, 0.9);
            }
            JobFx.slash(sp, j, 2.0, 0.3, 2.4, 300.0, 0.24);
            JobFx.smoke(lv, V.up(c, 0.4), 1.0, VIOLET, DUSK_CORE, 0.45, JobFx.hide1p(sp).with("alpha", 0.7));
            SkillFx.play(lv, "entity.player.attack.sweep", c, 0.8, 1.0);
            SkillFx.play(lv, "item.trident.hit", c, 1.1, 0.7);
            if (e.isRemoved() || !e.isAlive()) return;
            JobKit.strike(sp, e, j, P, k.num("cut", 3.75), false, false, life, 0, d -> {
                JobTraits12.stalkReady(sp, (int) k.num("nextFor", 60));
                JobImpact.hit(sp, e, j, 1.2);
                if (e.level() instanceof ServerLevel el) {
                    Vec3 b = Kit.bodyPoint(e);
                    JobFx.slashCard(el, b, V.unit(V.sub(b, sp.getEyePosition())), 300.0, 2.8 + Kit.bodyPad(e), 0.26, RuneKit.entVel(e), j, Opt.empty());
                    JobFx.burst(el, b, 0.9 + Kit.bodyPad(e) * 0.4, VIOLET, VIOLET_CORE, 0.3, Opt.of("vel", RuneKit.entVel(e)));
                    for (int i = 0; i < 4; i++) {
                        double a = i * Math.PI / 2 + 0.4;
                        raven(el, b, new Vec3(Math.cos(a) * 5, 3.5, Math.sin(a) * 5), 0.4, 0.4, 0.85);
                    }
                }
                JobSystem.note(sp, "§5레이븐 시프트 §7— 다음 직업 스킬 +" + Math.round(k.num("next", 0.2) * 100) + "% (" + k.num("nextFor", 60) / 20.0 + "초)");
                after(sp, e, k);
            });
        });
        return true;
    }

    // ------------------------------------------------------------ 체인 오브 저지먼트 (2026-11-07 3판 — 길로틴 대신)

    /**
     * 체인 오브 저지먼트 (롤 가렌 R · 세트 R): 겨눈 적 (range 칸 — 없으면 쓰지 않음) 둘레 땅 넷 (anchor 칸) 에서 bindAt 틱에 처형 사슬이 솟아 그 적을 묶음 (bind 틱 묶임 —
     * 보스는 느려짐 · 머리 위에서 내리비추는 해 질 녘 빛) · 그동안 낫을 머리 위로 높이 치켜듦 → 땅을 내리찍어 거대한 세로 검기가 땅을 타고 그 적에게 달려가 (waveSpeed 칸/초 —
     * 거리만큼 일찍 내리찍음) dropAt 틱에 목을 벰 (drop × (1 + 잃은 체력 몫 × missing) — 처형 덤과 따로 · 큰 숫자) · 사슬이 끊어져 흩어짐
     */
    static boolean chains(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        LivingEntity e = Kit.pickTarget(sp, k.num("range", 10), 25);
        if (e == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int dropAt = Math.max(12, (int) k.num("dropAt", 22));
        int bindAt = Math.max(1, Math.min(dropAt - 8, (int) k.num("bindAt", 4)));
        int bind = Math.max(10, (int) k.num("bind", 20));
        double speed = Math.max(8, k.num("waveSpeed", 24));
        double anchorR = k.num("anchor", 2.2) + Kit.bodyPad(e);
        JobTraits12.beginSkill(sp, k);
        RpgCombat.setSteadfast(sp, dropAt + 3);
        Vec3 c0 = e.position();
        Vec3 tw = V.sub(c0, sp.position());
        Vec3 u = Math.hypot(tw.x, tw.z) > 0.1 ? V.flat(tw) : Kit.facingOf(sp); // V.flat 은 단위 벡터
        Vec3[] anchors = new Vec3[4];
        for (int i = 0; i < 4; i++) anchors[i] = ground(level0, V.add(c0, V.turnY(u, 45 + i * 90), anchorR));
        // 검기가 달려가는 틱: 거리만큼 (가까우면 3틱 · 10칸이면 8틱 — 몸 앞 0.9칸에서 떠남)
        double run0 = Math.max(0.5, V.hdist(sp.position(), c0) - 0.9 - Kit.bodyPad(e) * 0.5);
        int travel = Math.max(3, Math.min(dropAt - bindAt - 2, (int) Math.ceil(run0 / speed * 20)));
        int launchAt = dropAt - travel;
        // 치켜듦: 머리 위 낫 끝에 모이는 금빛 · 발밑 보랏빛 고리 · 사슬 소리
        JobFx.charge(sp, j, new Vec3(0.2, 2.6, 0.25), launchAt - 1, 0.85);
        JobFx.halo(level0, V.up(sp.position(), 0.08), 1.6, j, launchAt / 20.0, Opt.of("alpha", 0.7));
        SkillFx.play(level0, "block.chain.place", sp.position(), 0.6, 0.8);
        // 사슬이 솟을 자리: 땅에 금빛 고리 · 반짝임
        for (Vec3 a : anchors) {
            JobFx.ring(level0, V.up(a, 0.06), 0.55, j, false, bindAt / 20.0 + 0.2, Opt.of("alpha", 0.9));
            JobFx.glint(level0, V.up(a, 0.2), 0.4, DUSK, DUSK_CORE, bindAt / 20.0 + 0.1, Opt.empty());
        }
        // 솟음 (bindAt − 3 ~ bindAt): 땅에서 그 적의 몸까지 사슬이 뻗음 → 묶음
        for (int t = 0; t <= 3; t++) {
            int tt = t;
            RuneKit.later(bindAt - 3 + tt, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
                ServerLevel lv = sp.level();
                Vec3 b = V.up(e.position(), e.getBbHeight() * 0.55);
                for (Vec3 a : anchors) {
                    Vec3 tip = V.lerp(V.up(a, 0.1), b, (tt + 1) / 4.0);
                    JobFx.chain(lv, V.up(a, 0.1), tip, j, 1.2, 0.08, Opt.of("alpha", 1.0, "phase", chainPhase()));
                    if (tt == 0) JobFx.smoke(lv, V.up(a, 0.3), 0.6, VIOLET, DUSK_CORE, 0.4, Opt.of("alpha", 0.7));
                }
                if (tt == 0) SkillFx.play(lv, "block.chain.break", e.position(), 0.6, 1.0);
            });
        }
        RuneKit.later(bindAt, () -> {
            if (!RuneKit.sameLife(sp, life) || e.isRemoved() || !e.isAlive()) return;
            boolean boss = JobKit.isBoss(e);
            RuneKit.effect(e, "slowness", bind, boss ? 2 : 10);
            if (e.level() instanceof ServerLevel lv) {
                JobFx.burst(lv, Kit.bodyPoint(e), 0.9 + Kit.bodyPad(e) * 0.4, DUSK, DUSK_CORE, 0.25, Opt.empty());
                JobFx.spotlight(lv, ground(lv, e.position()), e.getBbHeight() + 4, (dropAt - bindAt + 6) / 20.0, Opt.of("alpha", 0.55), DUSK, DUSK_CORE);
                SkillFx.play(lv, "block.chain.place", e.position(), 0.5, 1.0);
                SkillFx.play(lv, "entity.iron_golem.attack", e.position(), 0.6, 0.6);
            }
            JobSystem.note(sp, "§6처형 사슬 §7— " + bind / 20.0 + "초 묶임");
        });
        // 묶인 동안: 땅 → 몸 사슬 넷 (4틱마다 다시 그림 — 그 적을 따라감 · 장면은 서버 시간으로 이어서 다시 그려도 튀지 않음)
        for (int t = bindAt + 1; t <= dropAt; t += 4) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved() || !e.isAlive()) return;
                ServerLevel lv = sp.level();
                Vec3 b = V.up(e.position(), e.getBbHeight() * 0.55);
                for (Vec3 a : anchors) JobFx.chain(lv, V.up(a, 0.1), b, j, 1.2, 0.25, Opt.of("alpha", 1.0, "phase", chainPhase(), "fade", 0.05));
            });
        }
        // 내리찍음 (launchAt): 낫을 땅에 내리찍어 거대한 세로 검기가 땅을 타고 그 적에게 달려감 · 눈앞 세로 베기 · 땅 금
        JobKit.motionLater(sp, "executioner_chains:drop", launchAt - 3, life);
        RuneKit.later(launchAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            Vec3 to = e.isRemoved() ? c0 : e.position();
            Vec3 d = V.sub(to, c);
            Vec3 f = Math.hypot(d.x, d.z) > 0.1 ? V.flat(d) : Kit.facingOf(sp);
            double len = Math.max(1.0, V.hdist(c, to) - 0.9);
            JobFx.slash(sp, j, 2.2, 0.2, 2.6, 90.0, 0.26);
            JobFx.groundWave(lv, sp, ground(lv, V.add(c, f, 0.9)), f, 2.0, len, len / (travel / 20.0), j);
            JobFx.smash(lv, V.up(ground(lv, V.add(c, f, 1.0)), 0.05), 1.4, j, 0.4, Opt.empty());
            SkillFx.play(lv, "entity.player.attack.sweep", c, 0.6, 1.0);
            SkillFx.play(lv, "block.anvil.land", c, 0.9, 0.5);
        });
        RuneKit.later(dropAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 at = e.isRemoved() ? c0 : e.position();
            Vec3 g = ground(lv, at);
            double tall = e.getBbHeight();
            Vec3 neck = V.up(at, tall * 0.8);
            Vec3 f = V.sub(at, sp.position());
            f = Math.hypot(f.x, f.z) > 0.1 ? V.flat(f) : Kit.facingOf(sp);
            // 목을 벰: 그 자리에 피는 세로 초승달 · 목 높이 섬광 · 해골 · 땅 금 · 사슬이 끊어져 흩어짐
            JobFx.bigWave(lv, null, V.up(at, tall * 0.55), f, 0, 2.4, 90, 0.4, j, DUSK, DUSK_CORE, Opt.empty());
            JobFx.burst(lv, neck, 1.5 + Kit.bodyPad(e) * 0.5, DUSK, DUSK_CORE, 0.35, Opt.empty());
            JobFx.burst(lv, neck, 1.0 + Kit.bodyPad(e) * 0.4, VIOLET, VIOLET_CORE, 0.3, Opt.empty());
            JobFx.skull(lv, V.up(at, tall + 0.6), 0.7, 4, 2, 0.4, false, Opt.empty(), j);
            JobFx.smash(lv, V.up(g, 0.05), 2.0 + Kit.bodyPad(e), j, 0.45, Opt.empty());
            for (Vec3 a : anchors) {
                JobFx.chain(lv, V.up(a, 0.1), V.lerp(V.up(a, 0.1), neck, 0.5), j, 1.2, 0.15, Opt.of("alpha", 0.8));
                SkillFx.sparks(lv, V.lerp(a, neck, 0.5), DUSK, 6, 4.0);
            }
            SkillFx.sparks(lv, neck, DUSK, 14, 5.5);
            SkillFx.play(lv, "block.chain.break", g, 0.5, 1.0);
            SkillFx.play(lv, "entity.wither.break_block", g, 0.8, 0.5);
            SkillFx.play(lv, "item.trident.hit", g, 0.6, 1.0);
            if (e.isRemoved() || !e.isAlive()) return;
            double max = e.getMaxHealth();
            double missing = max > 0 ? Math.max(0, Math.min(1, 1 - e.getHealth() / max)) : 0;
            double mult = k.num("drop", 8) * (1 + missing * k.num("missing", 0.5));
            JobKit.strike(sp, e, j, P, mult, false, true, life, 0, dmg -> {
                JobImpact.hit(sp, e, j, 1.8);
                if (e.level() instanceof ServerLevel el) {
                    Vec3 b = Kit.bodyPoint(e);
                    JobFx.slashCard(el, b, V.unit(V.sub(b, sp.getEyePosition())), 90.0, 3.4 + Kit.bodyPad(e), 0.28, RuneKit.entVel(e), j, Opt.empty());
                }
                if (missing >= 0.1) JobSystem.note(sp, "§6심판 §7잃은 체력 " + Math.round(missing * 100) + "% → ×" + String.format(java.util.Locale.ROOT, "%.2f", 1
                        + missing * k.num("missing", 0.5)));
                after(sp, e, k);
            });
        });
        return true;
    }

    /** 사슬 그림의 장면 (서버 시간으로 이어서 — 4틱마다 다시 그려도 사슬 고리가 튀지 않음: jchain2 4장 · 10 fps) */
    private static double chainPhase() {
        return (Tasks.now() * 10 / 20.0) % JobFx.CHAIN;
    }

    // ------------------------------------------------------------ 데스 넬 (2026-11-07 3판 — 원 라스트 데이 대신)

    /**
     * 데스 넬 (롤 다리우스 R — 처형 초기화): dur 틱 태세 — 머리 위에 망령 종이 뜸 (JobTraits12.tick), 처형 기준 execute (처형할 수 있는 적 머리 위 해골), 처형할 때마다 종이
     * 울려 둘레 fearRange 칸 적 (보스 제외) 이 fear 틱 공포 (움찔해 물러섬 · 멈춤) · speedFor 틱 이동 속도 + speed · 다른 직업 스킬 재사용 대기 cdr 틱 줄어듦
     * (JobTraits12.execute). 그림: 머리 위에 종이 내려와 한 번 크게 울림 · 퍼지는 금빛 고리 · 해골
     */
    static boolean knell(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) k.num("dur", 200);
        JobTraits.startStance(sp, k, dur);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 top = V.up(c, sp.getBbHeight() + 1.4);
        JobFx.bell(level, top, 0.95, VIOLET, DUSK_CORE, 0.8, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
        JobFx.halo(level, V.up(c, 0.08), 3.0, j, 0.7, Opt.empty());
        SkillFx.shock(level, V.up(c, 0.1), DUSK, 4.0, 0.6);
        JobFx.ring(level, V.up(c, 0.1), 2.0, j, true, 0.5, Opt.empty());
        JobFx.skull(level, V.up(top, 0.9), 0.7, 0, 5, 0.8, false, JobFx.hide1p(sp), j);
        JobFx.aura(sp, j, 2.2, 0.8, 0.9);
        Ult.flash(level, c, DUSK, Opt.of("radius", 4.0, "time", new double[] {0.03, 0.05, 0.35}), sp);
        SkillFx.play(level, "block.bell.use", c, 0.45, 1.0);
        RuneKit.later(6, () -> SkillFx.play(sp.level(), "block.bell.resonate", sp.position(), 0.5, 0.9));
        SkillFx.play(level, "entity.wither.ambient", c, 1.4, 0.35);
        Notify.notify(sp, "§6§l" + k.name() + " §7— 처형 기준 " + Math.round(k.num("execute", 0.25) * 100) + "%", 40);
        return true;
    }

    // ------------------------------------------------------------ 길로틴 오브 헤븐 (2026-11-07 3판 — 비포 선셋 대신 · 숫자 그대로)

    /**
     * 길로틴 오브 헤븐 (롤 파이크 R): 해 질 녘 하늘 (rpg:ult_sunset) — 겨눈 적 (range 칸 — 없으면 앞 fallback 칸의 땅) 위 하늘에서 거대한 단두대 (rfx_jgallows — 높이
     * gallowsH 칸) 가 내려와 landAt 틱에 기둥이 땅에 박히며 둘레 radius 칸 적을 가둠 (trap 틱 묶임 — 보스는 느려짐) → first 틱 칼날이 떨어져 벰 (stroke) → 끌어올려져
     * every 틱 뒤 한 번 더 (strokes 번 — 마지막은 큰 숫자) → 흩어짐. 벤 적이 쓰러지면 window 틱 안에 다시 쓸 수 있음 (JobRecast — 모두 casts 번까지)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int stage = JobRecast.stage(sp, k.id());
        JobRecast.close(sp, k.id());
        JobTraits12.beginSkill(sp, k);
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int landAt = Math.max(4, (int) k.num("landAt", 10));
        int first = Math.max(landAt + 4, (int) k.num("first", 16));
        int every = Math.max(10, (int) k.num("every", 12));
        int strokes = Math.max(1, (int) k.num("strokes", 2));
        int lastAt = first + (strokes - 1) * every;
        int trap = Math.max(10, (int) k.num("trap", 16));
        double R = k.num("radius", 2.5);
        double H = k.num("gallowsH", 7.5);
        double drop = k.num("dropFrom", 9);
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        LivingEntity aim = Kit.pickTarget(sp, k.num("range", 12), 20);
        Vec3 mark;
        if (aim != null) mark = ground(level0, aim.position());
        else {
            double can = RuneKit.dashDistance(sp, f, k.num("fallback", 8), 0.3, 1.5);
            mark = ground(level0, V.add(start, f, can));
        }
        Ult.sky(level0, start, "rpg:ult_sunset", lastAt + 30);
        RpgCombat.setSteadfast(sp, lastAt + 4);
        double solo = GALLOWS_SOLO + stage;
        // 내려옴: 하늘 drop 칸 위에서 단두대가 생겨나 (0 ~ 1 장) landAt 틱에 땅에 닿음 · 땅에 처형 표식 X · 위에서 내리비추는 해 질 녘 빛 · 종소리
        JobFx.gallows(level0, V.up(mark, drop), H, 0, 2, (landAt + 1) / 20.0, false, Opt.of("vel", new Vec3(0, -drop / (landAt / 20.0), 0), "solo", solo, "owner",
                (double) sp.getId(), "fade", 0.0), DARK, BLADE);
        JobFx.xmark(level0, mark, R, 0, 3, landAt / 20.0, false, Opt.empty(), DUSK, DUSK_CORE);
        JobFx.spotlight(level0, mark, H + drop, (lastAt + 6) / 20.0, Opt.of("alpha", 0.5), DUSK, DUSK_CORE);
        JobFx.charge(sp, j, new Vec3(0.2, 2.5, 0.3), landAt, 0.8);
        SkillFx.play(level0, "block.bell.use", mark, 0.45, 1.0);
        SkillFx.play(level0, "entity.wither.ambient", mark, 0.7, 0.35);
        SkillFx.play(level0, "block.chain.place", mark, 0.5, 0.9);
        // 박힘 (landAt): 기둥이 땅에 박히며 흙먼지 · 고리 · 둘레 적을 가둠 (묶음)
        RuneKit.later(landAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            JobFx.smash(lv, V.up(mark, 0.05), R + 0.8, j, 0.5, Opt.empty());
            JobFx.smoke(lv, V.up(mark, 0.4), 2.2, VIOLET, DUSK_CORE, 0.6, Opt.of("alpha", 0.7));
            SkillFx.shock(lv, V.up(mark, 0.1), DUSK, R * 1.4, 0.45);
            JobFx.xmark(lv, mark, R, 3, 3, (lastAt - landAt) / 20.0 + 0.05, true, Opt.empty(), DUSK, DUSK_CORE);
            SkillFx.play(lv, "block.anvil.land", mark, 0.5, 1.0);
            SkillFx.play(lv, "entity.iron_golem.attack", mark, 0.5, 0.8);
            for (LivingEntity e : Kit.around(sp, mark, R, 3)) RuneKit.effect(e, "slowness", trap, JobKit.isBoss(e) ? 2 : 10);
        });
        // 매달림 (landAt ~ first − 3): 칼날이 위에서 번득임 (이어 돌림)
        JobFx.gallowsLater(sp, life, solo, mark, H, landAt, 2, 2, first - 3 - landAt, true, DARK, BLADE);
        boolean[] opened = {false};
        for (int i = 0; i < strokes; i++) {
            int at = first + i * every;
            boolean big = i == strokes - 1;
            // 떨어짐 (at − 3 ~ at) → 내리침 (at — 섬광 장) → 끌어올려짐 (at + 4 ~ 다음 떨어짐) · 끝은 빛 조각으로 흩어짐
            JobFx.gallowsLater(sp, life, solo, mark, H, at - 3, 4, 1, 3, false, DARK, BLADE);
            RuneKit.later(at - 3, () -> {
                if (RuneKit.sameLife(sp, life)) SkillFx.play(sp.level(), "item.trident.riptide_1", mark, 0.6, 0.8);
            });
            JobKit.motionLater(sp, "executioner_ult:drop", at - 4, life);
            JobFx.gallowsLater(sp, life, solo, mark, H, at, 5, 1, 4, false, DARK, BLADE);
            if (i < strokes - 1) JobFx.gallowsLater(sp, life, solo, mark, H, at + 4, 6, 1, every - 7, false, DARK, BLADE);
            else JobFx.gallowsLater(sp, life, solo, mark, H, at + 4, 7, 1, 12, false, DARK, BLADE);
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 neck = V.up(mark, 1.3);
                // 내리침: 목판에서 섬광 · 터짐 · 칼날이 지나간 빛 줄 · 불티 (몸은 낫을 내리긋는 동작만 — 눈앞 베기 없음: 베는 것은 10칸 너머의 단두대)
                JobFx.burst(lv, neck, big ? 2.6 : 2.0, DUSK, DUSK_CORE, 0.35, Opt.empty());
                JobFx.burst(lv, neck, big ? 1.8 : 1.4, VIOLET, VIOLET_CORE, 0.3, Opt.empty());
                JobFx.line(lv, V.up(mark, H), V.up(mark, -0.2), DUSK, DUSK_CORE, 0.3, Opt.of("alpha", 0.8));
                SkillFx.sparks(lv, neck, DUSK, big ? 18 : 12, 5.5);
                SkillFx.play(lv, "block.anvil.land", mark, 0.55, 1.0);
                SkillFx.play(lv, "item.trident.hit", mark, 0.5, 1.0);
                if (big) {
                    JobFx.xmark(lv, mark, R, 6, 2, 0.4, false, Opt.empty(), DUSK, DUSK_CORE);
                    JobFx.smash(lv, V.up(mark, 0.06), R + 0.6, j, 0.5, Opt.empty());
                    Ult.flash(lv, mark, DUSK, Opt.empty(), sp);
                    SkillFx.shock(lv, V.up(mark, 0.1), DUSK, R * 1.6, 0.5);
                    SkillFx.play(lv, "block.bell.use", mark, 0.6, 1.0);
                    SkillFx.play(lv, "entity.wither.break_block", mark, 0.8, 0.5);
                }
                for (LivingEntity e : Kit.around(sp, mark, R, 3)) {
                    JobKit.strike(sp, e, j, P, k.num("stroke", 25), false, big, life, 0, d -> {
                        JobImpact.hit(sp, e, j, big ? 2.2 : 1.6);
                        if (e.isRemoved() || !e.isAlive()) recastOnKill(sp, j, k, stage, opened);
                    });
                }
            });
        }
        return true;
    }

    /** 길로틴 오브 헤븐으로 적을 쓰러뜨림: window 틱 안에 다시 쓸 수 있음 (한 번 쓸 때 한 번만 — 모두 casts 번까지) */
    private static void recastOnKill(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, int stage, boolean[] opened) {
        if (opened[0]) return;
        opened[0] = true;
        int casts = Math.max(1, (int) k.num("casts", 3));
        if (stage + 1 >= casts) return;
        int window = Math.max(20, (int) k.num("window", 200));
        RuneKit.later(1, () -> {
            if (sp.isRemoved() || JobTraits12.jobIf(sp, JobTraits12.EX) == null) return;
            JobRecast.open(sp, k, stage + 1, window, null);
            JobSkills.clearCooldown(sp, k.id());
            JobFx.speed(sp, j, 0.3, 0.7);
            SkillFx.play(sp.level(), "block.amethyst_block.chime", sp.position(), 1.2, 1.0);
            SkillFx.play(sp.level(), "block.bell.use", sp.position(), 1.0, 0.6);
            JobSystem.note(sp, "§6처형 §7— " + k.name() + " 다시 쓸 수 있음 (" + window / 20 + "초 · " + (casts - stage - 1) + "번 남음)");
        });
    }
}
