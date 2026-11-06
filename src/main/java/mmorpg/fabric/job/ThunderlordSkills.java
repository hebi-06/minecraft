package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * 0.51.0 성기사 각성 썬더로드 (재설계안 2판 — 2026-11-06 사용자 "다음 작업", jobs.json advance thunderlord — 금빛 벼락 · 하늘빛, 특성 「심판」 — 성기사 줄기
 * JobTraits4 + 갈라지는 벼락 · 아군 보호막 JobTraits13): 1차와 같은 매커니즘 (근접 · 광역 제어 · 묵직한 단타 · 아군 보호) 의 새 스킬 — 아크 스매시 · 썬더스텝 ·
 * 스톰 스파이어 · 썬더헤드 · 던브레이커. 몸 쓰는 직업이 아니라 벼락 · 먹구름 · 번개 기둥 · 사슬 · 돌은 저절로 내림 (그림). 단타는 크게 감았다가 큰 타격, 화면은 흔들지 않음.
 */
final class ThunderlordSkills {
    private ThunderlordSkills() {}

    static final double[] GOLD = JobTraits13.GOLD;
    static final double[] GOLD_CORE = JobTraits13.GOLD_CORE;
    static final double[] SKY = JobTraits13.SKY;
    static final double[] SKY_CORE = JobTraits13.SKY_CORE;
    static final double[] CLOUD = JobTraits13.CLOUD;
    static final double[] DAWN = JobTraits13.DAWN;
    static final double[] DAWN_CORE = JobTraits13.DAWN_CORE;
    static final double[] STONE = JobTraits13.STONE;
    static final double[] STONE_CORE = JobTraits13.STONE_CORE;

    static void register() {
        JobSystem.register("thunderlord_arc", ThunderlordSkills::arc);
        JobSystem.register("thunderlord_step", ThunderlordSkills::step);
        JobSystem.register("thunderlord_spire", ThunderlordSkills::spire);
        JobSystem.register("thunderlord_head", ThunderlordSkills::head);
        JobSystem.register("thunderlord_ult", ThunderlordSkills::ult);
    }

    /** p 자리의 땅 (턱 하나 — 없으면 p 높이) */
    static Vec3 ground(ServerLevel level, Vec3 p) {
        return new Vec3(p.x, RuneKit.groundAt(level, V.up(p, 0.6), p.y), p.z);
    }

    /** 망치 머리 자리 (오른손 앞 — 그림의 시작점) */
    static Vec3 hammerHead(ServerPlayer sp) {
        RuneKit.Hand h = RuneKit.handOf(sp);
        return V.add(V.up(h.hand(), 0.15), h.f(), 0.45);
    }

    /** at 둘레에 작은 번개 n 줄 (지직임 — 감기 · 묶음 · 지나간 자리) */
    static void crackle(ServerLevel level, Vec3 at, double r, int n, double[] glow, double[] core, double life, Opt o) {
        for (int i = 0; i < n; i++) {
            Vec3 a = V.add(at, new Vec3((Kit.rand() - 0.5) * r, (Kit.rand() - 0.5) * r, (Kit.rand() - 0.5) * r));
            Vec3 b = V.add(at, new Vec3((Kit.rand() - 0.5) * r * 1.6, (Kit.rand() - 0.5) * r * 1.6, (Kit.rand() - 0.5) * r * 1.6));
            JobFx.arc(level, a, b, glow, core, life, o);
        }
    }

    // ------------------------------------------------------------ 아크 스매시

    /**
     * 아크 스매시 (롤 레오나 Q · 케넨 W): 망치를 오른쪽 뒤로 크게 감았다가 (망치에 번개가 감김) swingAt 틱에 앞 reach 칸 (arc°) 을 가로로 크게 휩씀 (sweep · 처음
     * 맞은 적 stun 틱 기절 · 심판 + judge) → forkAt 틱 맞은 적마다 번개가 갈라져 둘레 forkRange 칸의 다른 적 (적마다 하나 — 가까운 차례) 에게 튐 (fork · 심판 + judge) —
     * 다른 적이 없으면 그 적에게 위에서 다시 내리꽂힘. 맞은 적이 없으면 갈래도 없음
     */
    static boolean arc(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int swingAt = Math.max(1, (int) k.num("swingAt", 6));
        int forkAt = Math.max(swingAt + 10, (int) k.num("forkAt", 16));
        double reach = k.num("reach", 3.5);
        double arcDeg = k.num("arc", 120);
        double forkRange = k.num("forkRange", 5);
        int judge = (int) k.num("judge", 1);
        int stun = (int) k.num("stun", 10);
        RpgCombat.setSteadfast(sp, swingAt + 3);
        // 감음: 망치를 오른쪽 뒤로 — 망치에 감기는 번개 · 칼끝으로 모이는 빛 · 발밑 번개 고리 · 지직
        JobFx.charge(sp, j, new Vec3(0.75, 1.15, -0.55), swingAt, 0.85);
        JobFx.volt(level0, V.up(sp.position(), 0.06), 1.5, j, swingAt / 20.0 + 0.1, Opt.of("alpha", 0.75));
        SkillFx.play(level0, "item.trident.thunder", sp.position(), 1.8, 0.35);
        for (int t = 0; t < swingAt; t += 2) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 f = Kit.facingOf(sp);
                Vec3 back = V.up(V.add(V.add(sp.position(), V.rightOf(f), 0.75), f, -0.45), 1.3);
                crackle(sp.level(), back, 0.7, 2, GOLD, GOLD_CORE, 0.12, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
            });
        }
        List<LivingEntity> swept = new ArrayList<>();
        RuneKit.later(swingAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            Vec3 f = Kit.facingOf(sp);
            // 휩씀: 눈앞 가로 큰 베기 · 몸 앞 큰 참격 판 · 앞으로 날아가는 큰 검기 · 앞 땅을 긁는 번개
            JobFx.slash(sp, j, 2.2, 0.1, 2.9, 186.0, 0.26);
            JobFx.slashCard(lv, V.up(V.add(c, f, 1.7), 1.0), f, 180.0, 5.2, 0.3, Vec3.ZERO, j, JobFx.hide1p(sp));
            JobFx.slashWave(sp, j, 186.0, 1.6, reach, 22);
            for (int i = -2; i <= 2; i++) {
                Vec3 a = V.up(V.add(c, V.turnY(f, i * arcDeg / 5), 1.2), 0.3);
                Vec3 b = V.up(V.add(c, V.turnY(f, i * arcDeg / 5), reach), 0.15);
                JobFx.arc(lv, a, b, GOLD, GOLD_CORE, 0.25, Opt.of("flash", 1.0));
            }
            SkillFx.play(lv, "entity.player.attack.sweep", c, 0.6, 1.0);
            SkillFx.play(lv, "entity.lightning_bolt.impact", c, 1.4, 0.5);
            List<LivingEntity> cone = new ArrayList<>(RuneKit.coneTargets(sp, reach, arcDeg, 8));
            cone.removeIf(e -> !e.isAlive() || e.isRemoved());
            cone.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(sp)));
            for (int i = 0; i < cone.size(); i++) {
                LivingEntity e = cone.get(i);
                boolean first = i == 0;
                swept.add(e);
                JobKit.strike(sp, e, j, P, k.num("sweep", 2.5), false, false, life, 0, d -> {
                    JobTraits4.judge(sp, e, j, judge);
                    if (first) JobTraits4.stun(e, j, stun);
                    JobImpact.hit(sp, e, j, first ? 1.4 : 1.1);
                    if (e.level() instanceof ServerLevel el) {
                        Vec3 b = Kit.bodyPoint(e);
                        JobFx.slashCard(el, b, V.unit(V.sub(b, sp.getEyePosition())), 180.0, 2.6 + Kit.bodyPad(e), 0.26, RuneKit.entVel(e), j, Opt.empty());
                        crackle(el, b, 0.9 + Kit.bodyPad(e), 3, GOLD, GOLD_CORE, 0.3, Opt.of("vel", RuneKit.entVel(e)));
                    }
                });
            }
        });
        // 갈래: 맞은 적마다 둘레의 다른 적 (적마다 하나) — 없으면 그 적에게 위에서
        RuneKit.later(forkAt, () -> {
            if (!RuneKit.sameLife(sp, life) || swept.isEmpty()) return;
            ServerLevel lv = sp.level();
            Set<UUID> got = new HashSet<>();
            for (LivingEntity src : swept) {
                if (src.isRemoved()) continue;
                LivingEntity to = null;
                double best = Double.MAX_VALUE;
                for (LivingEntity x : Kit.around(sp, src.position(), forkRange, 3)) {
                    if (x == src || !x.isAlive() || x.isRemoved() || got.contains(x.getUUID())) continue;
                    double d = x.distanceToSqr(src);
                    if (d < best) {
                        best = d;
                        to = x;
                    }
                }
                Vec3 from = Kit.bodyPoint(src);
                if (to == null) {
                    if (got.contains(src.getUUID()) || !src.isAlive()) continue;
                    to = src;
                    JobFx.thunder(lv, src.position(), 8.5 + src.getBbHeight(), j, 0.35, Opt.of("vel", RuneKit.entVel(src)));
                    JobFx.thunder(lv, src.position(), 7.5 + src.getBbHeight(), SKY, SKY_CORE, 0.25, Opt.of("vel", RuneKit.entVel(src), "alpha", 0.7));
                } else {
                    Vec3 tb = Kit.bodyPoint(to);
                    JobFx.arc(lv, from, tb, GOLD, GOLD_CORE, 0.32, Opt.of("flash", 1.0));
                    JobFx.arc(lv, from, tb, SKY, SKY_CORE, 0.24, Opt.of("alpha", 0.75));
                    JobFx.thunder(lv, to.position(), 6.0 + to.getBbHeight(), j, 0.25, Opt.of("vel", RuneKit.entVel(to), "alpha", 0.8));
                }
                got.add(to.getUUID());
                LivingEntity tgt = to;
                JobFx.volt(lv, V.up(tgt.position(), 0.08), 1.0 + Kit.bodyPad(tgt) * 0.5, j, 0.35, Opt.of("vel", RuneKit.entVel(tgt)));
                JobKit.strike(sp, tgt, j, P, k.num("fork", 3.0), true, false, life, 0, d -> {
                    JobTraits4.judge(sp, tgt, j, judge);
                    JobImpact.hit(sp, tgt, j, 1.2);
                    if (tgt.level() instanceof ServerLevel el) {
                        JobFx.burst(el, Kit.bodyPoint(tgt), 1.0 + Kit.bodyPad(tgt) * 0.4, GOLD, GOLD_CORE, 0.28, Opt.of("vel", RuneKit.entVel(tgt)));
                    }
                });
            }
            SkillFx.play(lv, "entity.lightning_bolt.impact", sp.position(), 1.2, 0.7);
            SkillFx.play(lv, "item.trident.thunder", sp.position(), 1.4, 0.45);
        });
        return true;
    }

    // ------------------------------------------------------------ 썬더스텝

    /**
     * 썬더스텝 (롤 갈리오 R · 브라움 W): 몸이 번개가 되어 하늘로 사라졌다가 (vanish 틱 — 받는 피해 없음 · 보이지 않음 · 그 자리에 머묾) 조준한 곳 (겨눈 아군 곁 · 겨눈 적
     * 앞 · 조준한 땅 range 칸 · 없으면 앞 fallback 칸) 에 벼락으로 내리꽂혀 나타남 — 둘레 radius 칸 strike · stun 틱 기절 · 심판 + judge · 둘레 allyRadius 칸 아군
     * (나 포함) shieldFor 틱 보호막 (최대 체력 × shield — 강화). 묶였거나 타고 있으면 쓰지 않음
     */
    static boolean step(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int vanish = Math.max(2, (int) k.num("vanish", 6));
        double range = k.num("range", 10);
        Vec3 start = sp.position();
        Vec3 f0 = Kit.facingOf(sp);
        Vec3 dest = null;
        LivingEntity face = null;
        ServerPlayer ally = JobTraits4.aimAlly(sp, range + 2);
        if (ally != null) {
            Vec3 back = V.flat(V.sub(start, ally.position()));
            Vec3 u = V.len(back) > 0.1 ? V.unit(back) : V.scale(f0, -1);
            dest = JobTraits12.beyond(sp, ally.position(), u, 1.3);
        }
        if (dest == null) {
            LivingEntity foe = Kit.pickTarget(sp, range, 20);
            if (foe != null) {
                Vec3 back = V.flat(V.sub(start, foe.position()));
                Vec3 u = V.len(back) > 0.1 ? V.unit(back) : V.scale(f0, -1);
                dest = JobTraits12.beyond(sp, foe.position(), u, 1.5 + Kit.bodyPad(foe));
                face = foe;
            }
        }
        if (dest == null) {
            Vec3 aim = RuneKit.groundAim(sp, range);
            if (aim != null && V.hdist(aim, start) > 1.5) dest = Kit.standSpot(level0, V.up(aim, 0.6), 2.5);
        }
        if (dest == null) {
            double can = RuneKit.dashDistance(sp, f0, k.num("fallback", 8), 0.3, 1.5);
            if (can >= 1.5) dest = Kit.standSpot(level0, V.up(V.add(start, f0, can), 0.6), 2.5);
        }
        if (dest == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 내려설 자리가 없습니다", 30);
            return false;
        }
        Vec3 land = dest;
        LivingEntity look = face;
        // 사라짐: 받는 피해 없음 · 보이지 않음 · 그 자리에 머묾 — 몸에서 하늘로 솟는 벼락 · 터짐 · 하늘을 가로지르는 번개
        JobTraits13.guardFor(sp, vanish + 2);
        RpgCombat.setSteadfast(sp, vanish + 4);
        JobMove.run(sp, JobMove.hold(0, vanish));
        sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, vanish + 1, 0, false, false, false));
        JobFx.thunder(level0, start, 12, j, 0.35, Opt.empty());
        JobFx.thunder(level0, start, 10, SKY, SKY_CORE, 0.25, Opt.of("alpha", 0.75));
        JobFx.bolt(level0, V.up(start, 1.0), V.up(start, 12), GOLD, GOLD_CORE, 60, 0.7, Opt.empty());
        JobFx.burst(level0, V.up(start, 1.0), 1.4, GOLD, GOLD_CORE, 0.3, JobFx.hide1p(sp));
        JobFx.volt(level0, V.up(start, 0.06), 1.4, j, 0.35, Opt.empty());
        SkillFx.sparks(level0, V.up(start, 1.0), GOLD, 12, 6);
        SkillFx.play(level0, "entity.lightning_bolt.thunder", start, 1.6, 0.4);
        SkillFx.play(level0, "item.trident.riptide_3", start, 1.4, 0.6);
        RuneKit.later(vanish / 2, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.arc(sp.level(), V.up(start, 11), V.up(land, 11), GOLD, GOLD_CORE, 0.25, Opt.of("flash", 1.0));
        });
        JobKit.motionLater(sp, "thunderlord_step:land", vanish, life);
        RuneKit.later(vanish, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            // 내리꽂힘: 그 자리로 옮김 (겨눈 적을 봄) · 다시 보임
            float yRot = sp.getYRot();
            float xRot = sp.getXRot();
            if (look != null && !look.isRemoved()) {
                float[] rot = Kit.lookRotation(V.up(land, 1.6), Kit.bodyPoint(look));
                yRot = rot[0];
                xRot = Math.max(-20F, Math.min(30F, rot[1]));
            }
            Kit.moveTo(sp, land, yRot, xRot);
            MobEffectInstance inv = sp.getEffect(MobEffects.INVISIBILITY);
            if (inv != null && !inv.isInfiniteDuration() && inv.getDuration() <= vanish + 2) sp.removeEffect(MobEffects.INVISIBILITY);
            double r = k.num("radius", 3);
            JobFx.thunder(lv, land, 14, j, 0.45, Opt.empty());
            JobFx.thunder(lv, land, 12, SKY, SKY_CORE, 0.32, Opt.of("alpha", 0.8));
            JobFx.volt(lv, V.up(land, 0.08), r, j, 0.45, Opt.empty());
            JobFx.smash(lv, V.up(land, 0.05), r * 0.85, j, 0.45, Opt.empty());
            JobFx.burst(lv, V.up(land, 1.0), 2.2, GOLD, GOLD_CORE, 0.32, JobFx.hide1p(sp));
            RuneKit.novaRing(lv, land, 0.6, r / 0.6 - 1, GOLD, GOLD_CORE, Opt.of("life", 0.4));
            SkillFx.sparks(lv, V.up(land, 0.4), GOLD, 16, 7);
            SkillFx.play(lv, "entity.lightning_bolt.impact", land, 1.0, 1.0);
            SkillFx.play(lv, "entity.lightning_bolt.thunder", land, 1.2, 0.6);
            for (LivingEntity e : Kit.around(sp, land, r, 3)) {
                if (!e.isAlive() || e.isRemoved()) continue;
                JobKit.strike(sp, e, j, P, k.num("strike", 3.3), true, false, life, 0, d -> {
                    JobTraits4.judge(sp, e, j, (int) k.num("judge", 1));
                    JobTraits4.stun(e, j, (int) k.num("stun", 14));
                    JobImpact.hit(sp, e, j, 1.3);
                });
            }
            JobTraits13.shieldAllies(sp, j, land, k.num("allyRadius", 5), JobUpgrades.num(sp, k, "shield", 0.1), (int) k.num("shieldFor", 60), 0.45);
        });
        return true;
    }

    // ------------------------------------------------------------ 스톰 스파이어

    /**
     * 스톰 스파이어 (롤 볼리베어 E): 조준한 곳 (겨눈 적의 발밑 · 조준한 땅 range 칸 · 없으면 앞 fallback 칸) 하늘에 먹구름이 맺히고 땅의 금빛 고리가 조여 듦 → strikeAt 틱
     * 하늘을 가르는 거대한 번개 기둥 (둘레 radius 칸 pillar · stun 틱 기절 · 심판 + judge · 기둥 안의 아군 (나 포함) shieldFor 틱 보호막) → waveAt 틱 땅을 타고 번개
     * 물결이 둘레 waveRadius 칸으로 퍼짐 (wave · 밀쳐냄)
     */
    static boolean spire(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int strikeAt = Math.max(4, (int) k.num("strikeAt", 16));
        int waveAt = Math.max(strikeAt + 10, (int) k.num("waveAt", 26));
        double radius = k.num("radius", 3.5);
        double waveR = k.num("waveRadius", 6);
        double range = k.num("range", 10);
        Vec3 start = sp.position();
        Vec3 f0 = Kit.facingOf(sp);
        LivingEntity foe = Kit.pickTarget(sp, range, 20);
        Vec3 g;
        if (foe != null) g = ground(level0, foe.position());
        else {
            Vec3 aim = RuneKit.groundAim(sp, range);
            if (aim != null && V.hdist(aim, start) > 1.0) g = ground(level0, aim);
            else g = ground(level0, V.add(start, f0, RuneKit.dashDistance(sp, f0, k.num("fallback", 6), 0.3)));
        }
        RpgCombat.setSteadfast(sp, strikeAt + 4);
        // 뜸: 망치를 하늘로 치켜듦 · 하늘의 먹구름이 모임 · 땅의 금빛 고리가 조여 듦 · 구름 속 번쩍임 · 땅을 기는 작은 번개
        JobFx.charge(sp, j, new Vec3(0.2, 2.7, 0.2), strikeAt - 2, 0.8);
        Vec3 sky = V.up(g, 9.5);
        JobFx.cloud(level0, sky, 3.4, 0, 2, 0.4, false, Opt.empty(), CLOUD, GOLD);
        RuneKit.later(8, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.cloud(sp.level(), sky, 3.6, 2, 4, (strikeAt - 8) / 20.0 + 0.15, true, Opt.empty(), CLOUD, GOLD);
        });
        JobFx.halo(level0, V.up(g, 0.06), radius * 2.0, j, strikeAt / 20.0, Opt.of("grow", -0.5, "alpha", 0.9));
        JobFx.volt(level0, V.up(g, 0.08), radius, j, strikeAt / 20.0, Opt.of("alpha", 0.55));
        SkillFx.play(level0, "entity.lightning_bolt.thunder", g, 0.7, 0.45);
        SkillFx.play(level0, "block.beacon.activate", start, 1.4, 0.5);
        for (int t = 4; t < strikeAt; t += 4) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                JobFx.thunder(lv, V.add(V.up(g, 6.5), new Vec3((Kit.rand() - 0.5) * 4, 0, (Kit.rand() - 0.5) * 4)), 3.0, j, 0.18, Opt.of("alpha", 0.8));
                double a = Kit.rand() * Math.PI * 2;
                Vec3 p = V.up(V.add(g, new Vec3(Math.cos(a), 0, Math.sin(a)), radius * (0.6 + Kit.rand() * 0.6)), 0.15);
                JobFx.arc(lv, p, V.up(V.add(p, new Vec3(Math.cos(a + 1.6), 0, Math.sin(a + 1.6)), 1.2), 0.1), GOLD, GOLD_CORE, 0.2, Opt.empty());
            });
        }
        JobKit.motionLater(sp, "thunderlord_spire:call", strikeAt - 3, life);
        // 기둥
        RuneKit.later(strikeAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            JobFx.pillar(lv, g, 15, 1, 7, 0.7, false, Opt.empty(), GOLD, GOLD_CORE);
            JobFx.pillar(lv, g, 13, 1, 7, 0.6, false, Opt.of("alpha", 0.7), SKY, SKY_CORE);
            for (int i = 0; i < 3; i++) {
                double a = i * Math.PI * 2 / 3 + Kit.rand();
                JobFx.thunder(lv, V.add(g, new Vec3(Math.cos(a), 0, Math.sin(a)), radius * 0.8), 9, j, 0.3, Opt.of("alpha", 0.85));
            }
            JobFx.volt(lv, V.up(g, 0.1), radius, j, 0.5, Opt.empty());
            JobFx.smash(lv, V.up(g, 0.05), radius, j, 0.55, Opt.empty());
            JobFx.burst(lv, V.up(g, 1.2), 3.0, GOLD, GOLD_CORE, 0.35, Opt.empty());
            RuneKit.novaRing(lv, g, 0.8, radius / 0.8 - 1, GOLD, GOLD_CORE, Opt.of("life", 0.45));
            Ult.flash(lv, g, GOLD, Opt.of("radius", 6.0, "time", new double[] {0.02, 0.05, 0.3}), sp);
            SkillFx.sparks(lv, V.up(g, 0.5), GOLD, 18, 8);
            SkillFx.play(lv, "entity.lightning_bolt.thunder", g, 0.8, 1.0);
            SkillFx.play(lv, "entity.lightning_bolt.impact", g, 0.9, 1.0);
            JobFx.cloud(lv, sky, 3.6, 6, 2, 0.5, false, Opt.empty(), CLOUD, GOLD);
            for (LivingEntity e : Kit.around(sp, g, radius, 4)) {
                if (!e.isAlive() || e.isRemoved()) continue;
                JobKit.strike(sp, e, j, P, k.num("pillar", 6.6), true, true, life, 0, d -> {
                    JobTraits4.judge(sp, e, j, (int) k.num("judge", 2));
                    JobTraits4.stun(e, j, (int) k.num("stun", 20));
                    JobImpact.hit(sp, e, j, 1.7);
                });
            }
            JobTraits13.shieldAllies(sp, j, g, radius, k.num("shield", 0.08), (int) k.num("shieldFor", 60), 0.45);
        });
        // 물결: 기둥 자리에서 땅을 타고 둘레로 퍼지는 번개 (3틱 — 고리가 커짐)
        for (int q = 0; q < 3; q++) {
            int qq = q;
            RuneKit.later(waveAt - 2 + q, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double r = waveR * (0.45 + 0.275 * qq);
                JobFx.volt(sp.level(), V.up(g, 0.1), r, j, 0.3, Opt.of("alpha", 0.9 - qq * 0.15));
            });
        }
        RuneKit.later(waveAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            RuneKit.novaRing(lv, g, 1.0, waveR - 1, GOLD, GOLD_CORE, Opt.of("life", 0.4));
            RuneKit.glowCrack(lv, V.up(g, 0.04), waveR * 0.8, GOLD, GOLD_CORE, Opt.of("life", 0.8));
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                Vec3 a0 = V.up(V.add(g, new Vec3(Math.cos(a), 0, Math.sin(a)), radius * 0.8), 0.15);
                Vec3 a1 = V.up(V.add(g, new Vec3(Math.cos(a + 0.2), 0, Math.sin(a + 0.2)), waveR), 0.1);
                JobFx.arc(lv, a0, a1, GOLD, GOLD_CORE, 0.25, Opt.of("flash", 1.0));
            }
            SkillFx.play(lv, "item.trident.thunder", g, 1.0, 0.7);
            for (LivingEntity e : Kit.around(sp, g, waveR, 4)) {
                if (!e.isAlive() || e.isRemoved()) continue;
                JobKit.strike(sp, e, j, P, k.num("wave", 3.3), true, false, life, 0, d -> {
                    JobImpact.hit(sp, e, j, 1.0);
                    if (JobKit.pushable(e)) RuneKit.knock(e, V.sub(e.position(), g), k.num("knock", 0.6), 0.25);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 썬더헤드

    /**
     * 썬더헤드 (롤 브라움 패시브 · 볼리베어 패시브): dur 틱 태세 (강화 지속) — 머리 위에 먹구름이 따라다님, 망치 평타마다 먹구름 벼락 (평타 × bolt · 심판 + judge 더) · every
     * 번째마다 그 적 둘레 번개 고리 (기절) · 맞을 때마다 둘레 아군 (나 포함) 보호막 (shieldGap 틱에 한 번) · 밀려나지 않음 (JobTraits13). 그림: 먹구름이 모여 듦 · 내게
     * 내리치는 벼락 · 번개 고리 · 몸의 빛
     */
    static boolean head(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) JobUpgrades.num(sp, k, "dur", 200);
        JobTraits.startStance(sp, k, dur);
        RpgCombat.setSteadfast(sp, dur);
        JobTraits13.headOn(sp);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 over = V.up(c, sp.getBbHeight() + 2.6);
        JobFx.cloud(level, over, 1.8, 0, 2, 0.4, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), CLOUD, GOLD);
        RuneKit.later(3, () -> {
            if (sp.isRemoved()) return;
            ServerLevel lv = sp.level();
            Vec3 p = sp.position();
            JobFx.thunder(lv, p, sp.getBbHeight() + 3.0, j, 0.3, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
            JobFx.volt(lv, V.up(p, 0.08), 2.4, j, 0.5, Opt.empty());
            RuneKit.novaRing(lv, p, 0.6, 3.0, GOLD, GOLD_CORE, Opt.of("life", 0.4));
            SkillFx.play(lv, "entity.lightning_bolt.impact", p, 1.3, 0.7);
        });
        JobFx.aura(sp, j, 2.6, 0.8, 0.9);
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.5);
        Ult.flash(level, c, GOLD, Opt.of("radius", 4.0, "time", new double[] {0.03, 0.05, 0.3}), sp);
        SkillFx.play(level, "entity.lightning_bolt.thunder", c, 1.1, 0.6);
        SkillFx.play(level, "block.beacon.power_select", c, 1.2, 0.6);
        Notify.notify(sp, "§e§l" + k.name() + " §7— 먹구름 " + dur / 20 + "초", 40);
        return true;
    }

    // ------------------------------------------------------------ 던브레이커

    /**
     * 던브레이커 (롤 레오나 R · 타릭 R): 폭풍의 밤 (rpg:ult_storm) — 망치를 하늘로 치켜들면 chainAt 틱 둘레 radius 칸 적들 (targets 까지 — 가까운 차례) 에게 하늘에서 번개
     * 사슬이 내리꽂혀 묶음 (chains · root 틱 묶음 · 심판 + judge) → dawnAt 틱 망치를 땅에 꽂는 순간 새벽이 밝아 옴 (rpg:ult_dawn · 금빛 빛살 — 둘레 적 dawn) · 빛을
     * 받은 적이 돌로 굳음 (petrify 틱 기절 — 보스는 느려짐) · 둘레 radius 칸 아군 (나 포함) guard 틱 받는 피해 없음 (강화) → shatterAt 틱 돌이 금 가며 깨짐 (shatter · 큰 숫자)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int chainAt = Math.max(2, (int) k.num("chainAt", 10));
        int dawnAt = Math.max(chainAt + 10, (int) k.num("dawnAt", 30));
        int shatterAt = Math.max(dawnAt + 10, (int) k.num("shatterAt", 46));
        double radius = k.num("radius", 10);
        int targets = Math.max(1, (int) k.num("targets", 10));
        Vec3 c0 = sp.position();
        Ult.sky(level0, c0, "rpg:ult_storm", dawnAt + 4);
        RpgCombat.setSteadfast(sp, shatterAt + 4);
        // 폭풍: 망치를 하늘로 — 망치에 모이는 금빛 · 머리 위의 큰 먹구름 · 구름 속 번개 · 발밑 번개 고리
        JobFx.charge(sp, j, new Vec3(0.2, 2.8, 0.2), chainAt, 0.9);
        Vec3 sky = V.up(c0, 10.5);
        JobFx.cloud(level0, sky, 6.0, 0, 2, 0.4, false, Opt.empty(), CLOUD, GOLD);
        RuneKit.later(8, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.cloud(sp.level(), sky, 6.4, 2, 4, (dawnAt - 8) / 20.0, true, Opt.empty(), CLOUD, GOLD);
        });
        JobFx.volt(level0, V.up(c0, 0.08), 3.0, j, chainAt / 20.0 + 0.2, Opt.empty());
        JobFx.aura(sp, j, 3.0, 1.2, 1.0);
        SkillFx.play(level0, "entity.lightning_bolt.thunder", c0, 0.6, 0.8);
        SkillFx.play(level0, "block.beacon.activate", c0, 0.8, 0.8);
        for (int t = 2; t < dawnAt; t += 4) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 p = V.add(V.up(c0, 8.5), new Vec3((Kit.rand() - 0.5) * 9, 0, (Kit.rand() - 0.5) * 9));
                JobFx.thunder(sp.level(), p, 3.5, j, 0.18, Opt.of("alpha", 0.8));
            });
        }
        List<LivingEntity> chained = new ArrayList<>();
        // 사슬: 하늘에서 내리꽂히는 번개 사슬 — 묶음
        RuneKit.later(chainAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            List<LivingEntity> foes = new ArrayList<>(Kit.around(sp, sp.position(), radius, 4));
            foes.removeIf(e -> !e.isAlive() || e.isRemoved());
            foes.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(sp)));
            if (foes.size() > targets) foes = new ArrayList<>(foes.subList(0, targets));
            int root = (int) k.num("root", 22);
            for (LivingEntity e : foes) {
                chained.add(e);
                Vec3 b = Kit.bodyPoint(e);
                Vec3 top = V.up(e.position(), 9.5);
                JobFx.thunder(lv, e.position(), 9.5, j, 0.3, Opt.of("vel", RuneKit.entVel(e)));
                JobFx.arc(lv, top, b, GOLD, GOLD_CORE, 0.3, Opt.of("flash", 1.0));
                JobFx.chain(lv, top, b, j, 1.2, 0.4, Opt.of("alpha", 1.0));
                JobFx.volt(lv, V.up(e.position(), 0.08), 1.0 + Kit.bodyPad(e) * 0.5, j, 0.4, Opt.of("vel", RuneKit.entVel(e)));
                JobKit.strike(sp, e, j, P, k.num("chains", 8), true, false, life, 0, d -> {
                    JobTraits4.judge(sp, e, j, (int) k.num("judge", 1));
                    RuneKit.effect(e, "slowness", root, JobKit.isBoss(e) ? 1 : 10);
                    JobImpact.hit(sp, e, j, 1.2);
                });
            }
            SkillFx.play(lv, "block.chain.place", sp.position(), 0.7, 1.0);
            SkillFx.play(lv, "entity.lightning_bolt.impact", sp.position(), 1.1, 0.8);
        });
        // 묶여 있음: 하늘에서 내려온 사슬 · 발밑으로 박힌 사슬 둘 · 지직
        for (int t = chainAt + 4; t < dawnAt; t += 4) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                for (LivingEntity e : chained) {
                    if (e.isRemoved() || !e.isAlive()) continue;
                    Vec3 b = Kit.bodyPoint(e);
                    Opt o = Opt.of("vel", RuneKit.entVel(e), "alpha", 0.9);
                    JobFx.chain(lv, V.up(e.position(), 9.5), b, j, 1.2, 0.25, o);
                    double a = Kit.rand() * Math.PI;
                    for (int s = -1; s <= 1; s += 2) {
                        Vec3 anchor = V.add(e.position(), new Vec3(Math.cos(a) * s, 0, Math.sin(a) * s), 1.3 + Kit.bodyPad(e));
                        JobFx.chain(lv, b, V.up(anchor, 0.05), j, 0.8, 0.25, o);
                    }
                    crackle(lv, b, 0.8 + Kit.bodyPad(e), 1, GOLD, GOLD_CORE, 0.2, o);
                }
            });
        }
        JobKit.motionLater(sp, "thunderlord_ult:dawn", dawnAt - 4, life);
        List<LivingEntity> stoned = new ArrayList<>();
        // 새벽: 망치를 땅에 꽂음 — 하늘이 밝아 옴 · 금빛 빛살 · 빛을 받은 적이 돌로 굳음 · 아군 빛의 가호
        RuneKit.later(dawnAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            Ult.sky(lv, c, "rpg:ult_dawn", shatterAt - dawnAt + 30);
            Ult.flash(lv, c, DAWN, Opt.of("radius", radius, "time", new double[] {0.03, 0.1, 0.6}), sp);
            RuneKit.godRays(sp, lv, c, radius * 0.7, 10, DAWN, DAWN_CORE, Opt.of("h", 15.0, "life", 1.3, "alpha", 0.8, "skipNear", true));
            JobFx.cloud(lv, sky, 6.4, 6, 2, 0.6, false, Opt.empty(), CLOUD, GOLD);
            JobFx.halo(lv, V.up(c, 0.08), radius, j, 0.8, Opt.of("alpha", 0.9));
            JobFx.smash(lv, V.up(c, 0.05), 3.0, j, 0.5, Opt.empty());
            RuneKit.novaRing(lv, c, 1.0, radius - 1, DAWN, DAWN_CORE, Opt.of("life", 0.6));
            SkillFx.play(lv, "block.beacon.activate", c, 1.3, 1.0);
            SkillFx.play(lv, "block.bell.use", c, 0.7, 1.0);
            SkillFx.play(lv, "entity.lightning_bolt.thunder", c, 1.4, 0.5);
            int petrify = (int) k.num("petrify", 20);
            for (LivingEntity e : Kit.around(sp, c, radius, 4)) {
                if (!e.isAlive() || e.isRemoved()) continue;
                stoned.add(e);
                JobFx.spotlight(lv, e.position(), 8, 0.8, Opt.of("alpha", 0.75, "vel", RuneKit.entVel(e)), DAWN, DAWN_CORE);
                JobKit.strike(sp, e, j, P, k.num("dawn", 12), true, false, life, 0, d -> {
                    if (JobKit.isBoss(e)) RuneKit.effect(e, "slowness", petrify, 1);
                    else RuneKit.stun(e, petrify, DAWN, false);
                    JobImpact.fx(sp, e, j, 1.2);
                });
                JobFx.stone(lv, e.position(), e.getBbHeight() * 1.08 + 0.1, 0, 2, 0.25, false, Opt.of("vel", RuneKit.entVel(e)), STONE, STONE_CORE);
            }
            int guard = (int) JobUpgrades.num(sp, k, "guard", 40);
            for (ServerPlayer o : JobTraits4.allies(sp, c, radius)) {
                JobTraits13.guardFor(o, guard);
                JobFx.aegis(lv, V.up(o.position(), 1.1), 0.5, j, 0, 0.5, (o == sp ? JobFx.hide1p(sp) : Opt.empty()).with("vel", RuneKit.bodyVel(o)));
                JobFx.healCross(lv, V.up(o.position(), 2.3), 0.3, DAWN, DAWN_CORE, 0.6, (o == sp ? JobFx.hide1p(sp) : Opt.empty()).with("vel", V.add(RuneKit.bodyVel(o),
                        new Vec3(0, 0.6, 0))));
            }
        });
        // 굳어 있음 (6틱마다 다시 그림 — 몸을 따라감)
        for (int t = dawnAt + 5; t < shatterAt - 1; t += 6) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                for (LivingEntity e : stoned) {
                    if (e.isRemoved() || !e.isAlive()) continue;
                    JobFx.stone(lv, e.position(), e.getBbHeight() * 1.08 + 0.1, 2, 2, 0.35, true, Opt.of("vel", RuneKit.entVel(e)), STONE, STONE_CORE);
                }
            });
        }
        // 깨짐: 금이 가며 금빛이 새어 나옴 → 조각이 흩어짐 · 큰 숫자
        RuneKit.later(shatterAt - 2, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            for (LivingEntity e : stoned) {
                if (e.isRemoved() || !e.isAlive()) continue;
                JobFx.stone(lv, e.position(), e.getBbHeight() * 1.08 + 0.1, 4, 4, 0.5, false, Opt.of("vel", RuneKit.entVel(e)), STONE, STONE_CORE);
            }
            SkillFx.play(lv, "block.stone.break", sp.position(), 0.7, 1.0);
        });
        RuneKit.later(shatterAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            Ult.flash(lv, c, DAWN, Opt.of("radius", 6.0, "time", new double[] {0.02, 0.05, 0.3}), sp);
            SkillFx.play(lv, "random.glass", c, 0.7, 1.0);
            SkillFx.play(lv, "random.explode", c, 1.1, 0.6);
            List<LivingEntity> hit = new ArrayList<>(stoned);
            for (LivingEntity e : Kit.around(sp, c, radius, 4)) if (!hit.contains(e)) hit.add(e);
            for (LivingEntity e : hit) {
                if (!e.isAlive() || e.isRemoved()) continue;
                Vec3 b = Kit.bodyPoint(e);
                JobFx.burst(lv, b, 1.4 + Kit.bodyPad(e) * 0.5, DAWN, DAWN_CORE, 0.35, Opt.of("vel", RuneKit.entVel(e)));
                SkillFx.sparks(lv, b, STONE, 14, 6);
                JobKit.strike(sp, e, j, P, k.num("shatter", 24), true, true, life, 0, d -> JobImpact.hit(sp, e, j, 2.0));
            }
        });
        return true;
    }
}
