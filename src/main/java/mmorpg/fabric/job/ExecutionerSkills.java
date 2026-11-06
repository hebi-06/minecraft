package mmorpg.fabric.job;

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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 사신 반전 엑시큐셔너 (재설계안 2판 — 2026-11-06 사용자 "다음 작업", jobs.json advance executioner — 해 질 녘 금빛 · 짙은 보라, 특성 「처형」 — JobTraits12):
 * 1차의 많이 거두고 흡혈로 버티는 사신을 뒤집어 하나를 끝내는 처형인 — 컨뎀 · 섀도 스토크 · 길로틴 · 원 라스트 데이 · 비포 선셋. 몸 쓰는 직업이라 제 몸 · 제 낫 동작만
 * (망령 단두대 날 · 처형 표식 · 해 질 녘 빛은 그림). 단타는 크게 감았다가 큰 타격. 직업 스킬 (궁극기 빼고) 로 쓰러뜨리면 그 스킬의 재사용 대기 초기화
 * (JobTraits12.killedBy), 궁극기는 쓰러뜨리면 다시 쓰기 (JobRecast).
 */
final class ExecutionerSkills {
    private ExecutionerSkills() {}

    static final double[] DUSK = JobTraits12.DUSK;
    static final double[] DUSK_CORE = JobTraits12.DUSK_CORE;
    static final double[] VIOLET = JobTraits12.VIOLET;
    static final double[] VIOLET_CORE = {0.96, 0.78, 1.0};

    static void register() {
        JobSystem.register("executioner_condemn", ExecutionerSkills::condemn);
        JobSystem.register("executioner_stalk", ExecutionerSkills::stalk);
        JobSystem.register("executioner_guillotine", ExecutionerSkills::guillotine);
        JobSystem.register("executioner_lastday", ExecutionerSkills::lastday);
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

    // ------------------------------------------------------------ 컨뎀

    /**
     * 컨뎀 (롤 다리우스 W · 카직스 Q): 낫을 크게 뒤로 감았다가 slashAt 틱에 앞 reach 칸 (arc°) 의 적 하나 (겨눈 적 — 없으면 가장 가까운 적) 를 비스듬히 크게 벰 (slash ·
     * 둘레 alone 칸에 다른 적이 없으면 × (1 + isolated) — 큰 숫자) → mark 틱 선고 표식 (머리 위 해골 — 받는 직업 스킬 피해 + markBonus). 적이 없으면 헛벰
     */
    static boolean condemn(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int slashAt = Math.max(1, (int) k.num("slashAt", 6));
        double reach = k.num("reach", 4.5);
        double arc = k.num("arc", 60);
        JobTraits12.beginSkill(sp, k);
        RpgCombat.setSteadfast(sp, slashAt + 3);
        // 감음: 낫을 오른 어깨 뒤로 — 날 끝에 모이는 금빛 · 발밑 보랏빛 고리 · 날 가는 소리
        JobFx.charge(sp, j, new Vec3(0.55, 1.7, -0.55), slashAt, 0.7);
        JobFx.halo(level0, V.up(sp.position(), 0.08), 1.5, j, slashAt / 20.0 + 0.15, Opt.of("alpha", 0.7));
        SkillFx.play(level0, "item.trident.return", sp.position(), 0.6, 0.8);
        RuneKit.later(slashAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            Vec3 f = Kit.facingOf(sp);
            LivingEntity e = front(sp, reach, arc);
            // 벰: 눈앞 비스듬한 큰 베기 · 몸 앞 큰 참격 판 · 큰 검기
            JobFx.slash(sp, j, 2.2, 0.2, 2.6, 225.0, 0.26);
            JobFx.slashCard(lv, V.up(V.add(c, f, 1.8), 1.1), f, 135.0, 4.8, 0.3, Vec3.ZERO, j, JobFx.hide1p(sp));
            JobFx.slashWave(sp, j, 225.0, 1.5, reach, 22);
            SkillFx.play(lv, "entity.player.attack.sweep", c, 0.55, 1.0);
            SkillFx.play(lv, "item.trident.hit", c, 0.7, 0.8);
            if (e == null) return;
            boolean alone = isolated(sp, e, k.num("alone", 3));
            double mult = k.num("slash", 6.25) * (alone ? 1 + k.num("isolated", 0.5) : 1);
            JobKit.strike(sp, e, j, P, mult, false, alone, life, 0, d -> {
                JobTraits12.condemn(sp, e, (int) k.num("mark", 100));
                JobImpact.hit(sp, e, j, alone ? 1.6 : 1.2);
                if (e.level() instanceof ServerLevel el) {
                    Vec3 b = Kit.bodyPoint(e);
                    JobFx.slashCard(el, b, V.unit(V.sub(b, sp.getEyePosition())), 135.0, 3.0 + Kit.bodyPad(e), 0.28, RuneKit.entVel(e), j, Opt.empty());
                    JobFx.skull(el, V.up(e.position(), e.getBbHeight() + 0.75), 0.5, 0, 2, 0.3, false, Opt.of("vel", RuneKit.entVel(e)), j);
                    if (alone) {
                        // 고립: 둘레의 금빛 고리 · 위에서 내리비추는 해 질 녘 빛
                        Vec3 g = ground(el, e.position());
                        JobFx.ring(el, V.up(g, 0.08), k.num("alone", 3), j, false, 0.45, Opt.of("alpha", 0.85));
                        JobFx.spotlight(el, g, 5, 0.45, Opt.of("alpha", 0.7), DUSK, DUSK_CORE);
                        SkillFx.play(el, "block.bell.use", g, 0.6, 0.6);
                        JobSystem.note(sp, "§6고립 §7— 피해 +" + Math.round(k.num("isolated", 0.5) * 100) + "%");
                    }
                }
                after(sp, e, k);
            });
        });
        return true;
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

    /** 고립: e 둘레 r 칸에 다른 적이 없음 */
    static boolean isolated(ServerPlayer sp, LivingEntity e, double r) {
        for (LivingEntity x : Kit.around(sp, e.position(), r, 2.5)) {
            if (x != e && x.isAlive() && !x.isRemoved()) return false;
        }
        return true;
    }

    // ------------------------------------------------------------ 섀도 스토크

    /**
     * 섀도 스토크 (롤 카직스 E): 겨눈 적 (range 칸) 의 건너편 (behind 칸 — 막혔으면 비스듬히) 으로 leapTicks 틱에 그림자처럼 뛰어듦 (마지막 3틱에 그 적 쪽으로 돌아섬) →
     * 내려서며 등을 벰 (cut) · nextFor 틱 안의 다음 직업 스킬 + next (JobTraits12.stalkReady). 겨눈 적이 없거나 움직일 수 없으면 쓰지 않음
     */
    static boolean stalk(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        LivingEntity e = Kit.pickTarget(sp, k.num("range", 10), 25);
        if (e == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int leap = Math.max(4, (int) k.num("leapTicks", 8));
        Vec3 start = sp.position();
        Vec3 d0 = V.flat(V.sub(e.position(), start));
        Vec3 toward = V.len(d0) > 0.1 ? V.unit(d0) : Kit.facingOf(sp);
        double pad = Kit.bodyPad(e);
        Vec3 dest = JobTraits12.beyond(sp, e.position(), toward, k.num("behind", 1.4) + pad);
        if (dest == null) dest = JobTraits12.beyond(sp, e.position(), V.scale(toward, -1), 0.9 + pad);
        if (dest == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 내려설 자리가 없습니다", 30);
            return false;
        }
        Vec3 land = dest;
        double peak = JobMove.hopPeak(sp, start, land, 1.6 + 0.08 * V.hdist(start, land));
        RpgCombat.setSteadfast(sp, leap + 3);
        JobMove.run(sp, JobMove.hop(0, leap, JobMove.at(land), peak));
        turnToward(sp, e, leap - 2, 3, life);
        // 뛰어듦: 몸에서 빠져나오는 보랏빛 그림자 · 연기 · 길을 따라 그림자 (2틱마다)
        RuneKit.shadeClone(sp, level0, start, toward, 1, VIOLET, VIOLET_CORE, Opt.of("life", 0.4, "fade", 0.6, "alpha", 0.8));
        JobFx.smoke(level0, V.up(start, 0.6), 0.9, VIOLET, DUSK_CORE, 0.5, JobFx.hide1p(sp).with("alpha", 0.75));
        JobFx.speed(sp, j, leap / 20.0, 0.6);
        SkillFx.play(level0, "entity.phantom.flap", start, 1.2, 0.8);
        SkillFx.play(level0, "entity.enderman.teleport", start, 1.5, 0.4);
        for (int t = 2; t < leap; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                RuneKit.shadeClone(sp, sp.level(), sp.position(), toward, 1 + tt % 3, VIOLET, VIOLET_CORE, Opt.of("life", 0.25, "fade", 0.7, "alpha", 0.6));
            });
        }
        // 등 위에서: 낫 끝 금빛 (3틱 전)
        RuneKit.later(leap - 3, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.glint(sp.level(), V.up(RuneKit.handOf(sp).hand(), 0.4), 0.6, DUSK, DUSK_CORE, 0.2, JobFx.hide1p(sp).with("vel",
                    RuneKit.bodyVel(sp)));
        });
        RuneKit.later(leap, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            JobFx.slash(sp, j, 2.0, 0.3, 2.3, 300.0, 0.24);
            JobFx.smoke(lv, V.up(c, 0.3), 1.0, VIOLET, DUSK_CORE, 0.45, JobFx.hide1p(sp).with("alpha", 0.7));
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
                }
                JobSystem.note(sp, "§5섀도 스토크 §7— 다음 직업 스킬 +" + Math.round(k.num("next", 0.2) * 100) + "% (" + k.num("nextFor", 60) / 20.0 + "초)");
                after(sp, e, k);
            });
        });
        return true;
    }

    // ------------------------------------------------------------ 길로틴

    /**
     * 길로틴 (롤 가렌 R): 낫을 높이 치켜드는 동안 (dropAt − 4 틱) 겨눈 적 (range 칸) 머리 위 height 칸에 거대한 망령 단두대 날이 생겨나 매달림 (적을 따라감 · 위에서 내리비추는
     * 해 질 녘 빛) → 4틱에 떨어져 dropAt 틱에 내리쳐 벰 (drop × (1 + 잃은 체력 몫 × missing) — 처형 덤과 따로 · 큰 숫자). 겨눈 적이 없으면 쓰지 않음
     */
    static boolean guillotine(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        LivingEntity e = Kit.pickTarget(sp, k.num("range", 10), 25);
        if (e == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int dropAt = Math.max(8, (int) k.num("dropAt", 22));
        int fall = 4;
        int hangFrom = 4;
        double h = k.num("height", 7);
        double size = 1.15 + Kit.bodyPad(e) * 0.8;
        JobTraits12.beginSkill(sp, k);
        RpgCombat.setSteadfast(sp, dropAt + 3);
        Vec3[] last = {e.position()};
        double tall = e.getBbHeight();
        // 치켜듦: 머리 위 낫 끝에 모이는 금빛 · 발밑 보랏빛 고리
        JobFx.charge(sp, j, new Vec3(0.2, 2.6, 0.25), dropAt - fall, 0.8);
        JobFx.halo(level0, V.up(sp.position(), 0.08), 1.6, j, (dropAt - fall) / 20.0, Opt.of("alpha", 0.7));
        SkillFx.play(level0, "block.chain.place", sp.position(), 0.6, 0.8);
        // 생겨남 (높이 h 칸) → 매달림 (4틱마다 — 적을 따라감) · 내리비추는 빛 · 적 발밑 고리
        RuneKit.later(hangFrom - 4 < 0 ? 0 : hangFrom - 4, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            if (e.isAlive() && !e.isRemoved()) last[0] = e.position();
            JobFx.guillotine(sp.level(), V.up(last[0], tall + h), size, 0, 2, hangFrom / 20.0 + 0.05, false, Opt.empty(), DUSK, VIOLET_CORE);
            SkillFx.play(sp.level(), "block.chain.step", last[0], 0.5, 1.0);
        });
        for (int t = hangFrom; t < dropAt - fall; t += 4) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                if (e.isAlive() && !e.isRemoved()) last[0] = e.position();
                Vec3 g = ground(lv, last[0]);
                double u = (tt - hangFrom) / (double) Math.max(1, dropAt - fall - hangFrom);
                Opt vel = Opt.of("vel", e.isRemoved() ? Vec3.ZERO : RuneKit.entVel(e));
                JobFx.guillotine(lv, V.up(last[0], tall + h), size, 2, 2, 0.25, true, vel, DUSK, VIOLET_CORE);
                JobFx.spotlight(lv, g, tall + h, 0.24, vel.with("alpha", 0.4 + 0.35 * u), DUSK, DUSK_CORE);
                JobFx.ring(lv, V.up(g, 0.08), 0.9 + Kit.bodyPad(e) + 0.3 * u, j, tt % 8 == 0, 0.24, vel.with("alpha", 0.8));
                if (tt % 8 == 0) SkillFx.play(lv, "block.chain.step", g, 0.6 + 0.3 * u, 0.5);
            });
        }
        // 떨어짐 (4틱): 위로 끌리는 잔상 · 바람 소리
        RuneKit.later(dropAt - fall, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            if (e.isAlive() && !e.isRemoved()) last[0] = e.position();
            Vec3 top = V.up(last[0], tall + h);
            Vec3 vel = new Vec3(0, -(h - 0.4) / (fall / 20.0), 0);
            JobFx.guillotine(sp.level(), top, size, 4, 1, fall / 20.0, false, Opt.of("vel", vel), DUSK, VIOLET_CORE);
            JobFx.line(sp.level(), top, V.up(last[0], tall), DUSK, DUSK_CORE, fall / 20.0 + 0.1, Opt.of("alpha", 0.6));
            SkillFx.play(sp.level(), "item.trident.riptide_1", last[0], 0.6, 0.8);
        });
        JobKit.motionLater(sp, "executioner_guillotine:drop", dropAt - fall, life);
        RuneKit.later(dropAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            if (e.isAlive() && !e.isRemoved()) last[0] = e.position();
            Vec3 g = ground(lv, last[0]);
            Vec3 neck = V.up(last[0], tall * 0.8);
            // 내리침: 날 끝 섬광 → 흩어짐 · 하늘에서 땅까지 금빛 줄 · 터짐 · 땅 금 · 불티
            JobFx.guillotine(lv, V.up(neck, size * 0.4), size, 5, 3, 0.45, false, Opt.empty(), DUSK, VIOLET_CORE);
            JobFx.line(lv, V.up(g, tall + h), V.up(g, -0.2), DUSK, DUSK_CORE, 0.3, Opt.empty());
            JobFx.burst(lv, neck, 1.4 + Kit.bodyPad(e) * 0.5, DUSK, DUSK_CORE, 0.35, Opt.empty());
            JobFx.burst(lv, neck, 1.0 + Kit.bodyPad(e) * 0.4, VIOLET, VIOLET_CORE, 0.3, Opt.empty());
            JobFx.smash(lv, V.up(g, 0.05), 2.0 + Kit.bodyPad(e), j, 0.45, Opt.empty());
            JobFx.slash(sp, j, 2.2, 0.2, 2.4, 90.0, 0.24);
            SkillFx.sparks(lv, neck, DUSK, 14, 5.5);
            SkillFx.play(lv, "block.anvil.land", g, 0.6, 0.7);
            SkillFx.play(lv, "item.trident.hit", g, 0.5, 1.0);
            SkillFx.play(lv, "entity.wither.break_block", g, 0.8, 0.5);
            if (e.isRemoved() || !e.isAlive()) return;
            double max = e.getMaxHealth();
            double missing = max > 0 ? Math.max(0, Math.min(1, 1 - e.getHealth() / max)) : 0;
            double mult = k.num("drop", 8) * (1 + missing * k.num("missing", 0.5));
            JobKit.strike(sp, e, j, P, mult, false, true, life, 0, d -> {
                JobImpact.hit(sp, e, j, 1.8);
                if (e.level() instanceof ServerLevel el) {
                    JobFx.slashCard(el, Kit.bodyPoint(e), V.unit(V.sub(Kit.bodyPoint(e), sp.getEyePosition())), 90.0, 3.4 + Kit.bodyPad(e), 0.28, RuneKit.entVel(e), j,
                            Opt.empty());
                }
                if (missing >= 0.1) JobSystem.note(sp, "§6길로틴 §7잃은 체력 " + Math.round(missing * 100) + "% → ×" + String.format(java.util.Locale.ROOT, "%.2f", 1
                        + missing * k.num("missing", 0.5)));
                after(sp, e, k);
            });
        });
        return true;
    }

    // ------------------------------------------------------------ 원 라스트 데이

    /**
     * 원 라스트 데이 (롤 다리우스 — 처형 초기화): dur 틱 태세 — 처형 기준 execute (JobTraits12.threshold · 처형할 수 있는 적 머리 위 해골), 처형할 때마다 speedFor 틱 이동
     * 속도 + speed · 다른 직업 스킬 재사용 대기 cdr 틱 줄어듦 (JobTraits12.execute). 그림: 해 질 녘 빛 · 금빛 고리 · 비명 · 큰 해골 · 종소리
     */
    static boolean lastday(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) k.num("dur", 200);
        JobTraits.startStance(sp, k, dur);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        JobFx.halo(level, V.up(c, 0.08), 3.0, j, 0.7, Opt.empty());
        JobFx.ring(level, V.up(c, 0.1), 2.0, j, true, 0.5, Opt.empty());
        JobFx.spotlight(level, c, 7, 0.9, Opt.of("alpha", 0.75), DUSK, DUSK_CORE);
        JobFx.skull(level, V.up(c, 3.0), 0.9, 0, 5, 0.9, false, JobFx.hide1p(sp), j);
        JobFx.wail(level, V.up(c, 1.6), 0.9, j, 0.6, JobFx.hide1p(sp).with("dir", V.UP, "speed", 1.4));
        JobFx.aura(sp, j, 2.2, 0.8, 0.9);
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.5);
        Ult.flash(level, c, DUSK, Opt.of("radius", 4.0, "time", new double[] {0.03, 0.05, 0.35}), sp);
        SkillFx.play(level, "block.bell.use", c, 0.5, 1.0);
        SkillFx.play(level, "entity.wither.ambient", c, 1.4, 0.4);
        Notify.notify(sp, "§6§l" + k.name() + " §7— 처형 기준 " + Math.round(k.num("execute", 0.25) * 100) + "%", 40);
        return true;
    }

    // ------------------------------------------------------------ 비포 선셋

    /**
     * 비포 선셋 (롤 파이크 R · 다리우스 R): 해 질 녘 하늘 (rpg:ult_sunset) — 조준한 곳 (겨눈 적 range 칸 · 없으면 앞 fallback 칸의 땅) 에 X 자 처형 표식이 번지고 (markAt ~
     * blinkAt 틱 — 위에서 내리비추는 해 질 녘 빛) blinkAt 틱에 그곳으로 순간 이동해 first 틱부터 every 틱마다 X 자로 strokes 번 벰 (둘레 radius 칸 stroke — 마지막은 큰
     * 숫자). 벤 적이 쓰러지면 window 틱 안에 다시 쓸 수 있음 (JobRecast — 모두 casts 번까지). 묶였거나 타고 있으면 쓰지 않음
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        int stage = JobRecast.stage(sp, k.id());
        JobRecast.close(sp, k.id());
        JobTraits12.beginSkill(sp, k);
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int blinkAt = Math.max(2, (int) k.num("blinkAt", 10));
        int first = Math.max(blinkAt + 1, (int) k.num("first", 12));
        int every = Math.max(10, (int) k.num("every", 10));
        int strokes = Math.max(1, (int) k.num("strokes", 2));
        int lastAt = first + (strokes - 1) * every;
        double R = k.num("radius", 3);
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        LivingEntity aim = Kit.pickTarget(sp, k.num("range", 12), 20);
        Vec3 mark;
        if (aim != null) mark = ground(level0, aim.position());
        else {
            double can = RuneKit.dashDistance(sp, f, k.num("fallback", 8), 0.3, 1.5);
            mark = ground(level0, V.add(start, f, can));
        }
        Vec3 d0 = V.flat(V.sub(mark, start));
        Vec3 u = V.len(d0) > 0.3 ? V.unit(d0) : f;
        double spin = Math.toDegrees(Math.atan2(u.x, u.z));
        Ult.sky(level0, start, "rpg:ult_sunset", lastAt + 30);
        RpgCombat.setSteadfast(sp, lastAt + 4);
        Kit.noFall(sp, lastAt + 30);
        // 표식: X 자가 번짐 (blinkAt 틱) → 맥동 · 위에서 내리비추는 해 질 녘 빛 · 둘레 금빛 고리 · 종소리
        JobFx.xmark(level0, mark, R, 0, 3, blinkAt / 20.0, false, Opt.of("spin", spin), DUSK, VIOLET_CORE);
        JobFx.spotlight(level0, mark, 9, (lastAt + 6) / 20.0, Opt.of("alpha", 0.6), DUSK, DUSK_CORE);
        JobFx.ring(level0, V.up(mark, 0.1), R, j, false, blinkAt / 20.0 + 0.1, Opt.of("alpha", 0.8));
        SkillFx.play(level0, "block.bell.use", mark, 0.5, 1.0);
        SkillFx.play(level0, "entity.wither.ambient", mark, 0.8, 0.3);
        // 웅크림: 낫 끝 금빛 · 발밑 보랏빛 연기
        JobFx.charge(sp, j, new Vec3(0.45, 1.3, -0.4), blinkAt, 0.8);
        JobFx.smoke(level0, V.up(start, 0.4), 1.0, VIOLET, DUSK_CORE, blinkAt / 20.0 + 0.2, JobFx.hide1p(sp).with("alpha", 0.7));
        RuneKit.later(blinkAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            JobFx.xmark(lv, mark, R, 3, 3, (lastAt - blinkAt) / 20.0 + 0.05, true, Opt.of("spin", spin), DUSK, VIOLET_CORE);
            // 순간 이동: 표식 가운데에서 온 쪽으로 몸 여유만큼 (막혔으면 비스듬히 · 없으면 제자리)
            Vec3 from = sp.position();
            double pad = aim != null && !aim.isRemoved() ? Kit.bodyPad(aim) : 0;
            Vec3 spot = aim != null ? JobTraits12.beyond(sp, mark, V.scale(u, -1), 0.9 + pad) : Kit.standSpot(lv, V.up(mark, 0.6), 2.5);
            if (spot != null && Kit.moveTo(sp, spot)) {
                RuneKit.shadeClone(sp, lv, from, u, 1, VIOLET, VIOLET_CORE, Opt.of("life", 0.45, "fade", 0.6, "alpha", 0.85));
                JobFx.line(lv, V.up(from, 1.0), V.up(sp.position(), 1.0), DUSK, DUSK_CORE, 0.3, Opt.empty());
                JobFx.smoke(lv, V.up(from, 0.8), 1.0, VIOLET, DUSK_CORE, 0.5, Opt.of("alpha", 0.75));
                JobFx.smoke(lv, V.up(sp.position(), 0.8), 0.9, VIOLET, DUSK_CORE, 0.45, JobFx.hide1p(sp).with("alpha", 0.7));
                JobFx.speed(sp, j, 0.25, 0.8);
                SkillFx.play(lv, "entity.enderman.teleport", from, 0.8, 0.8);
            }
        });
        boolean[] opened = {false};
        for (int i = 0; i < strokes; i++) {
            int ii = i;
            boolean big = i == strokes - 1;
            RuneKit.later(first + i * every, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                double tilt = ii % 2 == 0 ? 45.0 : 135.0;
                Vec3 face = V.flat(V.sub(mark, sp.getEyePosition()));
                Vec3 dir = V.len(face) > 0.3 ? V.unit(face) : Kit.facingOf(sp);
                // X 의 한 획: 표식 위 거대한 참격 판 · 땅을 긋는 금빛 줄 · 터짐 · 1인칭 눈앞 베기 · 불티
                JobFx.slashCard(lv, V.up(mark, 1.1), dir, tilt, 5.4, 0.32, Vec3.ZERO, j, JobFx.hide1p(sp));
                Vec3 side = V.turnY(u, ii % 2 == 0 ? 45 : -45);
                JobFx.line(lv, V.up(V.add(mark, side, -R), 0.15), V.up(V.add(mark, side, R), 0.15), DUSK, DUSK_CORE, 0.35, Opt.empty());
                JobFx.burst(lv, V.up(mark, 1.0), big ? 2.4 : 1.8, DUSK, DUSK_CORE, 0.35, Opt.empty());
                JobFx.slash(sp, j, 2.4, 0.2, 2.8, ii % 2 == 0 ? 225.0 : 315.0, 0.28);
                SkillFx.sparks(lv, V.up(mark, 1.0), DUSK, big ? 16 : 10, 5.5);
                SkillFx.play(lv, "entity.player.attack.sweep", mark, 0.5 + 0.1 * ii, 1.0);
                SkillFx.play(lv, "item.trident.hit", mark, 0.6, 1.0);
                if (big) {
                    JobFx.xmark(lv, mark, R, 6, 2, 0.4, false, Opt.of("spin", spin), DUSK, VIOLET_CORE);
                    JobFx.smash(lv, V.up(mark, 0.06), R, j, 0.5, Opt.empty());
                    Ult.flash(lv, mark, DUSK, Opt.empty(), sp);
                    SkillFx.shock(lv, V.up(mark, 0.1), DUSK, R * 1.2, 0.5);
                    SkillFx.play(lv, "block.bell.use", mark, 0.7, 1.0);
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

    /** 비포 선셋으로 적을 쓰러뜨림: window 틱 안에 다시 쓸 수 있음 (한 번 쓸 때 한 번만 — 모두 casts 번까지) */
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
