package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-06 사제 각성 세인트 (재설계안 2판 — 사용자 "다음 작업", jobs.json advance saint — 옅은 금빛 · 새잎 초록 · 꽃잎빛, 특성 「신앙」 — 사제 줄기 JobTraits4 +
 * 넘치는 빛 JobTraits14): 1차와 같은 매커니즘 (힐러 — 아군에게 체력을 줌) 의 새 스킬 — 하트 어코드 · 페탈 베일 · 글리머윙 · 소울본드 · 에버블룸. 몸 쓰는 직업이 아니라
 * 연꽃 · 꽃 · 나비 · 꽃잎은 저절로 피고 날아다님 (그림), 화면은 흔들지 않음. 회복 · 보호막은 받는 사람의 최대 체력 비율 (위력과 무관), 피해 = 위력 P × 배율 (마법).
 */
final class SaintSkills {
    private SaintSkills() {}

    static final double[] GOLD = JobTraits14.GOLD;
    static final double[] GOLD_CORE = JobTraits14.GOLD_CORE;
    static final double[] LEAF = JobTraits14.LEAF;
    static final double[] LEAF_CORE = JobTraits14.LEAF_CORE;
    static final double[] PETAL = JobTraits14.PETAL;
    static final double[] PETAL_CORE = JobTraits14.PETAL_CORE;

    static void register() {
        JobSystem.register("saint_accord", SaintSkills::accord);
        JobSystem.register("saint_veil", SaintSkills::veil);
        JobSystem.register("saint_wing", SaintSkills::wing);
        JobSystem.register("saint_bond", SaintSkills::bond);
        JobSystem.register("saint_ult", SaintSkills::ult);
    }

    /** 홀 끝 (손 앞) */
    static Vec3 tip(ServerPlayer sp) {
        RuneKit.Hand h = RuneKit.handOf(sp);
        return V.up(V.add(h.hand(), h.f(), 0.45), 0.25);
    }

    static Vec3 chest(ServerPlayer p) {
        return JobTraits14.chest(p);
    }

    /** 나비 · 빛이 닿는 자리: 나는 가슴 앞 0.6칸 (1인칭 화면 안 — 눈 속으로 날아들지 않게), 아군 · 적은 몸 가운데 */
    static Vec3 spot(ServerPlayer sp, LivingEntity e) {
        if (e == sp) return V.add(chest(sp), Kit.facingOf(sp), 0.6);
        return e instanceof ServerPlayer o ? chest(o) : Kit.bodyPoint(e);
    }

    // ------------------------------------------------------------ 하트 어코드

    /**
     * 하트 어코드 (롤에 없는 장치): 홀을 들면 발밑에 빛의 연꽃이 피고 가슴으로 빛이 모임 → flowAt 틱 둘레 radius 칸 아군 (나 포함 — 둘 이상) 의 가슴을 빛줄기로 이어 체력
     * 비율을 고르게 나눔 (많은 아군의 빛이 내 가슴을 거쳐 적은 아군에게 흐름) → healAt 틱 모두 최대 체력 heal 회복 (넘치는 빛). 그림: 연꽃 두 겹 · 빛줄기 · 머리 위 꽃 · 빛
     * 고리
     */
    static boolean accord(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int flowAt = Math.max(2, (int) k.num("flowAt", 6));
        int healAt = Math.max(flowAt + 2, (int) k.num("healAt", 12));
        double r = k.num("radius", 8);
        double heal = JobUpgrades.num(sp, k, "heal", 0.12);
        Vec3 c0 = sp.position();
        JobFx14.lotus(level0, V.up(c0, 0.05), 2.6, 0, 6, (healAt + 8) / 20.0, false, Opt.of("alpha", 0.95), GOLD, GOLD_CORE);
        JobFx14.lotus(level0, V.up(c0, 0.08), 1.6, 0, 6, (healAt + 6) / 20.0, false, Opt.of("alpha", 0.85), LEAF, LEAF_CORE);
        JobFx.charge(sp, j, new Vec3(0, 1.3, 0.35), flowAt, 0.7);
        JobFx.aura(sp, j, 2.4, (healAt + 6) / 20.0, 0.7);
        SkillFx.play(level0, "block.amethyst_block.resonate", c0, 1.3, 0.8);
        SkillFx.play(level0, "block.beacon.power_select", c0, 1.6, 0.5);
        RuneKit.later(flowAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            List<ServerPlayer> allies = JobTraits4.allies(sp, sp.position(), r);
            Vec3 heart = chest(sp);
            for (ServerPlayer o : allies) if (o != sp) JobFx.beam(lv, heart, chest(o), GOLD, GOLD_CORE, 2.0, 0.4, Opt.of("alpha", 0.85));
            equalize(sp, allies, heart, healAt - flowAt);
            JobFx14.bloom(lv, V.up(heart, 0.25), 0.55, 1, 3, 0.35, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), GOLD, GOLD_CORE);
            SkillFx.play(lv, "block.amethyst_block.chime", sp.position(), 1.0, 0.9);
        });
        RuneKit.later(healAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 c = sp.position();
            JobFx.halo(lv, V.up(c, 0.06), r, j, 0.6, Opt.of("alpha", 0.75));
            RuneKit.zonePulse(lv, V.up(c, 0.1), GOLD, r, 0.5);
            for (ServerPlayer o : JobTraits4.allies(sp, c, r)) {
                JobTraits14.saintHeal(sp, o, RpgHealth.maxOf(o) * heal);
                Opt oo = (o == sp ? JobFx.hide1p(sp) : Opt.empty()).with("vel", V.add(RuneKit.bodyVel(o), new Vec3(0, 0.6, 0)));
                JobFx14.bloom(lv, V.up(o.position(), o.getBbHeight() + 0.65), 0.45, 2, 5, 0.65, false, oo, PETAL, PETAL_CORE);
                JobFx.healCross(lv, V.up(o.position(), 1.2), 0.42, j, 0.5, (o == sp ? JobFx.hide1p(sp) : Opt.empty()).with("vel", RuneKit.bodyVel(o)));
            }
            Vec3 look = sp.getViewVector(1F);
            JobFx14.bloom(lv, V.add(V.add(sp.getEyePosition(), look, 2.2), JobFx.screenUp(look), -0.25), 0.3, 2, 5, 0.6, false, JobFx.only1p(sp).with("vel",
                    RuneKit.bodyVel(sp)), PETAL, PETAL_CORE);
            SkillFx.play(lv, "block.bell.use", c, 1.6, 0.5);
            SkillFx.play(lv, "block.amethyst_block.chime", c, 1.4, 0.9);
        });
        return true;
    }

    /**
     * 체력 비율을 고르게 (둘 이상 · RPG 체력): 평균보다 많은 아군은 그만큼 내려가고 (피해가 아님 — 체력 1 아래로는 아님) 빛이 그 가슴에서 내 가슴으로, 적은 아군은 ticks 틱
     * 뒤 내 가슴에서 흘러간 빛이 닿으며 오름 (숫자)
     */
    static void equalize(ServerPlayer sp, List<ServerPlayer> allies, Vec3 heart, int ticks) {
        List<ServerPlayer> live = new ArrayList<>();
        for (ServerPlayer o : allies) if (RpgHealth.isRpgMode(o) && o.isAlive() && !o.isDeadOrDying()) live.add(o);
        if (live.size() < 2) return;
        double sum = 0;
        for (ServerPlayer o : live) sum += o.getHealth() / Math.max(1e-6, o.getMaxHealth());
        double avg = sum / live.size();
        ServerLevel lv = sp.level();
        int half = Math.max(1, ticks / 2);
        for (ServerPlayer o : live) {
            double want = Math.max(1, avg * o.getMaxHealth());
            double diff = want - o.getHealth();
            if (diff < -0.05) {
                o.setHealth((float) want);
                double d = V.len(V.sub(heart, chest(o)));
                for (int i = 0; i < 3; i++) JobFx.soulFly(lv, V.up(chest(o), (i - 1) * 0.2), heart, Math.max(6, d / (half / 20.0)), 0.3, GOLD, GOLD_CORE, Opt.empty());
            } else if (diff > 0.05) {
                RuneKit.later(half, () -> {
                    if (o.isRemoved() || sp.isRemoved()) return;
                    Vec3 from = chest(sp);
                    double d = V.len(V.sub(chest(o), from));
                    for (int i = 0; i < 3; i++) JobFx.soulFly(o.level(), V.up(from, (i - 1) * 0.2), chest(o), Math.max(6, d / (Math.max(1, ticks - half) / 20.0)), 0.3,
                            LEAF, LEAF_CORE, Opt.empty());
                });
                RuneKit.later(ticks, () -> {
                    if (!o.isRemoved() && o.isAlive()) JobTraits4.heal(o, diff);
                });
            }
        }
    }

    // ------------------------------------------------------------ 페탈 베일

    /**
     * 페탈 베일 (롤 잔나 E): 겨눈 아군 (range 칸 — 없으면 나) 에게 홀 끝에서 꽃잎이 흘러가 formAt 틱 감쌈 — shieldFor 틱 보호막 (최대 체력 × shield · 강화) · speedFor 틱
     * 이동 속도 (빠르기 speed), 장막이 깨지거나 끝나면 꽃잎이 터져 둘레 burstRadius 칸 적을 밀쳐냄 (JobTraits14). 그림: 흐르는 꽃잎 · 피는 꽃 · 빛의 방패 · 발밑 연꽃 · 도는 꽃잎
     */
    static boolean veil(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        ServerPlayer aimed = JobTraits4.aimAlly(sp, k.num("range", 16));
        ServerPlayer to = aimed != null ? aimed : sp;
        int formAt = Math.max(1, (int) k.num("formAt", 4));
        double shield = JobUpgrades.num(sp, k, "shield", 0.15);
        int shieldFor = (int) k.num("shieldFor", 80);
        // 꽃잎이 홀 끝에서 그 아군에게 흘러감 (나면 몸을 감아 오름)
        for (int i = 0; i < 12; i++) {
            int delay = i % 3;
            RuneKit.later(delay, () -> {
                if (!RuneKit.sameLife(sp, life) || to.isRemoved()) return;
                ServerLevel lv = sp.level();
                Vec3 a = V.add(tip(sp), new Vec3((Kit.rand() - 0.5) * 0.4, (Kit.rand() - 0.5) * 0.4, (Kit.rand() - 0.5) * 0.4));
                Vec3 b = V.add(chest(to), new Vec3((Kit.rand() - 0.5) * 0.9, (Kit.rand() - 0.5) * 1.0, (Kit.rand() - 0.5) * 0.9));
                if (to == sp) b = V.up(V.add(sp.position(), new Vec3(Math.cos(Kit.rand() * JobTraits14.TAU), 0, Math.sin(Kit.rand() * JobTraits14.TAU)), 0.8), 0.3
                        + Kit.rand() * 1.6);
                Vec3 d = V.sub(b, a);
                double len = V.len(d);
                if (len < 0.1) return;
                double ticks = Math.max(1, formAt - delay);
                JobTraits14.petal(lv, a, V.scale(d, 1 / len), len / (ticks / 20.0), 0.15, ticks / 20.0 + 0.05, Opt.empty());
            });
        }
        JobFx.line(level0, tip(sp), chest(to), PETAL, PETAL_CORE, 0.3, Opt.of("alpha", 0.7));
        SkillFx.play(level0, "block.cherry_leaves.place", sp.position(), 1.3, 1.0);
        SkillFx.play(level0, "block.amethyst_block.chime", sp.position(), 1.5, 0.6);
        RuneKit.later(formAt, () -> {
            if (!RuneKit.sameLife(sp, life) || to.isRemoved() || !to.isAlive()) return;
            ServerLevel lv = to.level();
            double max = RpgHealth.maxOf(to);
            RpgHealth.addShield(to, max * shield, shieldFor, max * shield);
            RuneKit.effect(to, "speed", (int) k.num("speedFor", 60), (int) k.num("speed", 1));
            JobTraits14.veilOn(sp, to, 0, shieldFor, k.num("burstRadius", 3), k.num("knock", 0.6));
            Opt oo = (to == sp ? JobFx.hide1p(sp) : Opt.empty()).with("vel", RuneKit.bodyVel(to));
            JobFx14.bloom(lv, V.up(to.position(), to.getBbHeight() * 0.55), 1.05, 2, 5, 0.6, false, oo.with("alpha", 0.75, "coreAlpha", 0.8), PETAL, PETAL_CORE);
            JobFx.aegis(lv, V.up(to.position(), 1.1), 0.5, j, 0, 0.45, oo);
            JobFx14.lotus(lv, V.up(to.position(), 0.06), 1.4, 0, 6, 0.6, false, Opt.of("alpha", 0.9, "vel", RuneKit.bodyVel(to)), PETAL, PETAL_CORE);
            if (to == sp) {
                Vec3 look = sp.getViewVector(1F);
                JobFx14.bloom(lv, V.add(V.add(sp.getEyePosition(), look, 2.2), JobFx.screenUp(look), -0.3), 0.32, 2, 5, 0.55, false, JobFx.only1p(sp).with("vel",
                        RuneKit.bodyVel(sp)), PETAL, PETAL_CORE);
            }
            SkillFx.play(lv, "item.armor.equip_elytra", to.position(), 1.4, 0.7);
            SkillFx.play(lv, "block.beacon.power_select", to.position(), 1.9, 0.4);
            if (to != sp) Notify.notify(to, "§d" + sp.getName().getString() + " §7님의 §d" + k.name(), 30);
        });
        return true;
    }

    // ------------------------------------------------------------ 글리머윙

    /**
     * 글리머윙 (롤 나미 W): 홀 끝에서 빛나는 나비가 날아올라 첫 대상 (겨눈 적 → 겨눈 아군 → 가장 가까운 적 (hopRange 칸) → 나) 부터 적과 아군 (나 포함) 을 번갈아 hops 번 —
     * every 틱마다 둘레 hopRange 칸의 다음 대상으로 (적은 아직 맞지 않은 적 먼저 · 아군은 체력 비율이 가장 낮은 아군). 적은 hit · hit · last (셋째는 큰 숫자 — 마법), 아군은
     * 최대 체력 heal 회복 (넘치는 빛). 다음 대상이 없거나 다 날면 작은 나비들로 흩어짐
     */
    static boolean wing(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int hops = Math.max(1, (int) k.num("hops", 6));
        int every = Math.max(5, (int) k.num("every", 8));
        double range = k.num("range", 16);
        double hopR = k.num("hopRange", 8);
        double heal = JobUpgrades.num(sp, k, "heal", 0.05);
        LivingEntity first = Kit.pickTarget(sp, range, 12);
        if (first == null) first = JobTraits4.aimAlly(sp, range);
        if (first == null) {
            List<LivingEntity> near = JobKit.near(sp, hopR, 1);
            if (!near.isEmpty()) first = near.get(0);
        }
        if (first == null) first = sp;
        Vec3 from = tip(sp);
        JobFx14.fly(level0, from, 0.6, 0, 6, 0.25, true, Opt.of("alpha", 0.9), GOLD, GOLD_CORE);
        JobFx.burst(level0, from, 0.6, GOLD, GOLD_CORE, 0.25, JobFx.hide1p(sp));
        SkillFx.sparks(level0, from, GOLD_CORE, 8, 3.0);
        SkillFx.play(level0, "block.amethyst_block.resonate", sp.position(), 1.6, 0.8);
        SkillFx.play(level0, "entity.allay.ambient_with_item", sp.position(), 1.4, 0.6);
        hop(sp, j, k, P, life, 0, hops, every, from, first, new int[] {0}, new HashSet<>(), heal, hopR);
        return true;
    }

    /** 나비 한 번: from → to (every 틱) — 닿으면 아군 회복 · 적 피해, 그다음 대상으로 */
    private static void hop(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life, int i, int hops, int every, Vec3 from, LivingEntity to, int[] foes,
            Set<UUID> struck, double heal, double hopR) {
        ServerLevel lv = sp.level();
        Vec3 dest = spot(sp, to);
        JobFx14.flyTo(lv, from, dest, every, 0.62, 0.9, Opt.empty(), GOLD, GOLD_CORE);
        // 날개에서 흩날리는 반짝이
        for (int s = 1; s <= 3; s++) {
            Vec3 p = V.up(V.lerp(from, dest, s / 4.0), Math.sin(s / 4.0 * Math.PI) * 0.9);
            SkillFx.fx(lv, "rpg:arrow_spark", p, Opt.of("dir", V.UP, "speed", 0.5, "size", 0.11, "grow", 0.4, "life", 0.5, "color", s % 2 == 0 ? GOLD_CORE : LEAF,
                    "alpha", 0.9));
        }
        RuneKit.later(every, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel l = sp.level();
            Vec3 at = to.isRemoved() ? dest : spot(sp, to);
            boolean ally = to instanceof ServerPlayer tp && JobTraits4.ally(sp, tp);
            if (ally) {
                ServerPlayer ao = (ServerPlayer) to;
                JobTraits14.saintHeal(sp, ao, RpgHealth.maxOf(ao) * heal);
                Opt oo = (ao == sp ? JobFx.hide1p(sp) : Opt.empty()).with("vel", RuneKit.bodyVel(ao));
                JobFx14.bloom(l, V.up(ao.position(), ao.getBbHeight() + 0.55), 0.42, 2, 5, 0.5, false, oo, PETAL, PETAL_CORE);
                JobFx.healCross(l, V.up(ao.position(), 1.25), 0.36, j, 0.45, oo);
                SkillFx.play(l, "block.amethyst_block.chime", ao.position(), 1.7, 0.6);
            } else if (to.isAlive() && !to.isRemoved()) {
                foes[0]++;
                struck.add(to.getUUID());
                boolean third = foes[0] >= 3;
                JobKit.strike(sp, to, j, P, third ? k.num("last", 2.4) : k.num("hit", 1.6), true, third, life, 0, d -> {
                    JobImpact.fx(sp, to, j, third ? 1.3 : 1.0);
                    if (to.level() instanceof ServerLevel el) {
                        JobFx14.bloom(el, Kit.bodyPoint(to), 0.55 + Kit.bodyPad(to) * 0.3, 5, 3, 0.35, false, Opt.of("vel", RuneKit.entVel(to)), GOLD, GOLD_CORE);
                        JobFx.burst(el, Kit.bodyPoint(to), 0.8 + Kit.bodyPad(to) * 0.4, GOLD, GOLD_CORE, 0.3, Opt.of("vel", RuneKit.entVel(to)));
                    }
                });
                SkillFx.play(l, "block.amethyst_block.break", at, 1.5, 0.8);
            }
            if (i + 1 >= hops) {
                scatter(l, at, 6);
                return;
            }
            LivingEntity next = ally ? nextFoe(sp, at, hopR, struck) : nextAlly(sp, at, hopR);
            if (next == null) {
                scatter(l, at, 6);
                return;
            }
            hop(sp, j, k, P, life, i + 1, hops, every, at, next, foes, struck, heal, hopR);
        });
    }

    /** 다음 적: at 둘레 r 칸 — 아직 맞지 않은 적 먼저 (가까운 차례), 다 맞았으면 가장 가까운 적 */
    private static LivingEntity nextFoe(ServerPlayer sp, Vec3 at, double r, Set<UUID> struck) {
        LivingEntity best = null;
        LivingEntity any = null;
        double bd = 1e9;
        double ad = 1e9;
        for (LivingEntity e : Kit.around(sp, at, r, 4)) {
            if (!e.isAlive() || e.isRemoved()) continue;
            double d = e.position().distanceToSqr(at);
            if (d < ad) {
                ad = d;
                any = e;
            }
            if (!struck.contains(e.getUUID()) && d < bd) {
                bd = d;
                best = e;
            }
        }
        return best != null ? best : any;
    }

    /** 다음 아군 (나 포함): at 둘레 r 칸 — 체력 비율이 가장 낮은 아군 (같으면 가까운 아군) */
    private static ServerPlayer nextAlly(ServerPlayer sp, Vec3 at, double r) {
        ServerPlayer best = null;
        double br = 9;
        for (ServerPlayer o : JobTraits4.allies(sp, at, r)) {
            double ratio = o.getHealth() / Math.max(1e-6, o.getMaxHealth()) + o.position().distanceTo(at) * 1e-4;
            if (ratio < br) {
                br = ratio;
                best = o;
            }
        }
        return best;
    }

    /** 나비가 작은 나비 n 마리로 흩어짐 · 반짝이 */
    static void scatter(ServerLevel level, Vec3 at, int n) {
        for (int i = 0; i < n; i++) {
            double a = i * JobTraits14.TAU / n + Kit.rand() * 0.4;
            Vec3 d = V.unit(new Vec3(Math.cos(a), 0.5 + Kit.rand() * 0.6, Math.sin(a)));
            JobFx14.fly(level, V.add(at, d, 0.2), 0.26, 0, 6, 0.7, true, Opt.of("dir", d, "speed", 2.4 + Kit.rand() * 1.4, "alpha", 0.9), GOLD, GOLD_CORE);
        }
        JobFx14.fly(level, at, 0.62, 6, 2, 0.3, false, Opt.empty(), GOLD, GOLD_CORE);
        SkillFx.sparks(level, at, GOLD_CORE, 10, 3.5);
        SkillFx.play(level, "block.amethyst_cluster.break", at, 1.6, 0.6);
    }

    // ------------------------------------------------------------ 소울본드

    /**
     * 소울본드 (롤 타릭 W): dur 틱 태세 (강화로 늘어남) — 겨눈 아군 (range 칸 — 없으면 가장 가까운 아군) 과 빛의 리본으로 이어짐: 그 아군이 받을 피해의 share 를 내가 대신
     * 받음 · 내 직업 스킬 회복 + healUp (JobTraits14). 이을 아군이 없으면 회복만. 그림: 리본 · 둘의 머리 위 연꽃 · 흐르는 꽃잎 (JobTraits14 가 4틱마다)
     */
    static boolean bond(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) JobUpgrades.num(sp, k, "dur", 200);
        double range = k.num("range", 16);
        ServerPlayer ally = JobTraits4.aimAlly(sp, range);
        if (ally == null) {
            double bd = range * range;
            for (ServerPlayer o : JobTraits4.allies(sp, sp.position(), range)) {
                if (o == sp) continue;
                double d = o.distanceToSqr(sp);
                if (d < bd) {
                    bd = d;
                    ally = o;
                }
            }
        }
        JobTraits.startStance(sp, k, dur);
        JobTraits14.bondStart(sp, ally);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        JobFx14.lotus(level, V.up(c, 0.06), 1.8, 0, 6, 0.7, false, Opt.of("alpha", 0.9), GOLD, GOLD_CORE);
        JobFx14.lotus(level, V.up(c, sp.getBbHeight() + 0.45), 0.45, 0, 6, 0.8, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), GOLD, GOLD_CORE);
        if (ally != null) {
            Vec3 a = chest(sp);
            Vec3 b = chest(ally);
            JobFx.beam(level, a, b, GOLD, GOLD_CORE, 2.0, 0.5, Opt.of("alpha", 0.95));
            JobFx.line(level, a, b, LEAF, LEAF_CORE, 0.4, Opt.of("alpha", 0.8));
            for (int i = 0; i < 8; i++) {
                Vec3 p = V.lerp(a, b, (i + 0.5) / 8);
                JobTraits14.petal(level, p, V.unit(new Vec3(Kit.rand() - 0.5, 0.6, Kit.rand() - 0.5)), 1.5, 0.15, 0.7, Opt.empty());
            }
            JobFx14.lotus(level, V.up(ally.position(), 0.06), 1.4, 0, 6, 0.7, false, Opt.of("alpha", 0.9, "vel", RuneKit.bodyVel(ally)), GOLD, GOLD_CORE);
            JobFx14.lotus(level, V.up(ally.position(), ally.getBbHeight() + 0.45), 0.45, 0, 6, 0.8, false, Opt.of("vel", RuneKit.bodyVel(ally)), GOLD, GOLD_CORE);
            JobFx.aegis(level, V.up(ally.position(), 1.1), 0.5, j, 0, 0.45, Opt.of("vel", RuneKit.bodyVel(ally)));
            Notify.notify(ally, "§a" + sp.getName().getString() + " §7님과 §a" + k.name() + " §7— 받는 피해의 " + Math.round(k.num("share", 0.2) * 100) + "% 를 나눔", 40);
        }
        JobFx.aura(sp, j, 2.6, 0.8, 0.9);
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.5);
        SkillFx.play(level, "block.beacon.activate", c, 1.6, 0.6);
        SkillFx.play(level, "block.amethyst_block.chime", c, 1.2, 0.8);
        Notify.notify(sp, "§a§l" + k.name() + " §7— " + (ally != null ? ally.getName().getString() + " 님과 이어짐 · " : "") + "회복 +" + Math.round(k.num("healUp",
                0.4) * 100) + "% " + dur / 20 + "초", 40);
        return true;
    }

    // ------------------------------------------------------------ 에버블룸

    /**
     * 에버블룸 (롤 킨드레드 R · 질리언 R): 꽃이 피는 봄밤 (rpg:ult_spring) — 발밑 둘레 radius 칸에 거대한 빛의 연꽃이 펼쳐지고 앞 하늘에 꽃봉오리가 떠오름: 안의 아군 (나
     * 포함) 은 undying 틱 쓰러지지 않음 (나쁜 효과 제거 — 폭풍마다 새로 들어온 아군도) · 받은 피해를 적어 둠 (JobTraits14 꽃밭) · first 틱부터 every 틱마다 storms 번 꽃잎 폭풍이
     * 안의 적을 벰 (storm — 마법) → bloomAt 틱 꽃이 활짝 피며 안의 아군마다 그동안 받은 피해를 되돌림 + 최대 체력 heal (넘치는 빛) · 적에게 bloom (큰 숫자) · 나비 떼
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        String life = RuneKit.lifeToken(sp);
        int storms = Math.max(0, (int) k.num("storms", 3));
        int first = Math.max(4, (int) k.num("first", 20));
        int every = Math.max(10, (int) k.num("every", 20));
        int bloomAt = Math.max(first + Math.max(0, storms - 1) * every + 10, (int) k.num("bloomAt", 80));
        double r = k.num("radius", 10);
        double heal = JobUpgrades.num(sp, k, "heal", 0.15);
        int undying = (int) k.num("undying", 84);
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 ground = ThunderlordSkills.ground(level0, c0);
        Ult.sky(level0, c0, "rpg:ult_spring", bloomAt + 30);
        JobTraits14.Field field = JobTraits14.fieldStart(sp, ground, r, bloomAt + 2);
        for (ServerPlayer o : JobTraits4.allies(sp, ground, r)) {
            RuneKit.cleanse(o);
            JobTraits14.bloomFor(o, undying);
        }
        // 땅의 연꽃 (열림 → 활짝 이어 돌림) · 둘레를 도는 꽃잎 · 하늘의 꽃봉오리 (앞 2.5칸 위 3.4칸 — 영상 카메라 안)
        JobFx14.lotus(level0, V.up(ground, 0.06), r, 0, 3, 0.45, false, Opt.of("alpha", 0.95), GOLD, GOLD_CORE);
        JobFx14.lotus(level0, V.up(ground, 0.09), r * 0.6, 0, 3, 0.45, false, Opt.of("alpha", 0.85), LEAF, LEAF_CORE);
        RuneKit.later(9, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            double rest = (bloomAt - 9) / 20.0;
            JobFx14.lotus(lv, V.up(ground, 0.06), r, 3, 3, rest, true, Opt.of("alpha", 0.9), GOLD, GOLD_CORE);
            JobFx14.lotus(lv, V.up(ground, 0.09), r * 0.6, 3, 3, rest, true, Opt.of("alpha", 0.8), LEAF, LEAF_CORE);
        });
        Vec3 budAt = V.up(V.add(ground, f, 2.5), 3.4);
        Vec3 look = sp.getViewVector(1F);
        Vec3 bud1p = V.add(V.add(sp.getEyePosition(), look, 2.0), JobFx.screenUp(look), 0.85);
        JobFx14.bloom(level0, V.up(budAt, -1.0), 1.7, 0, 2, 0.5, false, JobFx.hide1p(sp).with("dir", V.UP, "speed", 2.0), PETAL, PETAL_CORE);
        RuneKit.later(10, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            double rest = (bloomAt - 10) / 20.0;
            JobFx14.bloom(lv, budAt, 1.7, 0, 2, rest, true, JobFx.hide1p(sp).with("fps", 4.0), PETAL, PETAL_CORE);
            JobFx14.bloom(lv, bud1p, 0.36, 0, 2, rest, true, JobFx.only1p(sp).with("fps", 4.0), PETAL, PETAL_CORE);
        });
        RuneKit.godRays(sp, level0, ground, 2.5, 4, GOLD, GOLD_CORE, Opt.of("h", 10.0, "life", 1.0, "alpha", 0.55, "skipNear", true));
        JobFx.aura(sp, j, 3.0, 1.0, 1.0);
        JobFx.eyeRing(sp, j, 2.4, 1.1, 0.5);
        SkillFx.play(level0, "block.beacon.activate", c0, 1.2, 0.8);
        SkillFx.play(level0, "block.amethyst_block.resonate", c0, 0.8, 1.0);
        for (int s = 0; s < storms; s++) {
            int ss = s;
            RuneKit.later(first + s * every, () -> {
                if (RuneKit.sameLife(sp, life)) storm(sp, j, k, P, ground, r, budAt, ss, life, undying - (first + ss * every));
            });
        }
        JobKit.motionLater(sp, "saint_ult:bloom", bloomAt - 4, life);
        RuneKit.later(bloomAt, () -> {
            if (!RuneKit.sameLife(sp, life)) {
                JobTraits14.fieldEnd(field);
                return;
            }
            bloom(sp, j, k, P, ground, r, budAt, bud1p, field, heal, life);
            JobTraits14.fieldEnd(field);
        });
        return true;
    }

    /** 꽃잎 폭풍: 꽃밭 둘레를 휘도는 꽃잎 · 꽃봉오리가 부풂 — 안의 적 storm (마법), 안의 아군은 쓰러지지 않음을 끝까지 (새로 들어온 아군도) */
    private static void storm(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 ground, double r, Vec3 budAt, int s, String life, int undyingLeft) {
        ServerLevel lv = sp.level();
        for (int i = 0; i < 18; i++) {
            double a = i * JobTraits14.TAU / 18 + s * 0.4;
            double rr = r * (0.55 + 0.4 * ((i % 3) / 2.0));
            Vec3 p = V.up(V.add(ground, new Vec3(Math.cos(a), 0, Math.sin(a)), rr), 0.4 + (i % 4) * 0.45);
            Vec3 tan = V.unit(new Vec3(-Math.sin(a), 0.2, Math.cos(a)));
            JobTraits14.petal(lv, p, tan, 7.0, 0.2, 0.45, Opt.empty());
        }
        JobFx14.bloom(lv, budAt, 1.85, 1, 1, 0.3, false, Opt.of("alpha", 0.9), PETAL, PETAL_CORE);
        JobFx.halo(lv, V.up(ground, 0.08), r, j, 0.5, Opt.of("alpha", 0.6));
        SkillFx.play(lv, "block.cherry_leaves.break", ground, 0.9, 1.0);
        SkillFx.play(lv, "entity.player.attack.sweep", ground, 1.5, 0.5);
        for (ServerPlayer o : JobTraits4.allies(sp, ground, r)) JobTraits14.bloomFor(o, Math.max(4, undyingLeft));
        int[] cnt = {0};
        for (LivingEntity e : Kit.around(sp, ground, r, 4)) {
            if (!e.isAlive() || e.isRemoved()) continue;
            JobKit.strike(sp, e, j, P, k.num("storm", 4), true, false, life, 0, d -> {
                if (cnt[0]++ < 6 && e.level() instanceof ServerLevel el) {
                    for (int q = 0; q < 4; q++) {
                        Vec3 d2 = V.unit(new Vec3(Kit.rand() - 0.5, Kit.rand() * 0.6, Kit.rand() - 0.5));
                        JobTraits14.petal(el, Kit.bodyPoint(e), d2, 4.0, 0.17, 0.4, Opt.empty());
                    }
                    JobFx.burst(el, Kit.bodyPoint(e), 0.7 + Kit.bodyPad(e) * 0.4, PETAL, PETAL_CORE, 0.3, Opt.of("vel", RuneKit.entVel(e)));
                }
            });
        }
    }

    /** 꽃이 활짝 핌: 큰 꽃 · 번쩍 · 빛살 · 나비 떼 — 안의 아군마다 받은 피해를 되돌림 + 최대 체력 heal, 안의 적에게 bloom (큰 숫자) */
    private static void bloom(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 ground, double r, Vec3 budAt, Vec3 bud1p, JobTraits14.Field field,
            double heal, String life) {
        ServerLevel lv = sp.level();
        Vec3 flowerAt = V.up(budAt, -0.3);
        JobFx14.bloom(lv, flowerAt, 3.0, 2, 6, 1.2, false, JobFx.hide1p(sp).with("alpha", 1.0), PETAL, PETAL_CORE);
        JobFx14.bloom(lv, flowerAt, 3.6, 3, 4, 1.0, false, JobFx.hide1p(sp).with("alpha", 0.35, "coreAlpha", 0.3), GOLD, GOLD_CORE);
        JobFx14.bloom(lv, bud1p, 0.55, 2, 6, 1.0, false, JobFx.only1p(sp), PETAL, PETAL_CORE);
        JobFx14.lotus(lv, V.up(ground, 0.06), r, 5, 3, 0.7, false, Opt.of("alpha", 0.95), GOLD, GOLD_CORE);
        Ult.flash(lv, ground, GOLD, Opt.empty(), sp);
        RuneKit.godRays(sp, lv, ground, r * 0.5, 6, GOLD, GOLD_CORE, Opt.of("h", 10.0, "life", 1.0, "alpha", 0.7, "skipNear", true));
        RuneKit.novaRing(lv, ground, 0.8, r / 0.8 - 1, PETAL, PETAL_CORE, Opt.of("life", 0.5));
        for (int i = 0; i < 14; i++) {
            Vec3 d = V.unit(new Vec3(Kit.rand() - 0.5, 0.2 + Kit.rand() * 0.8, Kit.rand() - 0.5));
            JobFx14.fly(lv, V.add(flowerAt, d, 0.6), 0.32, 0, 6, 1.0 + Kit.rand() * 0.4, true, Opt.of("dir", d, "speed", 2.5 + Kit.rand() * 2.5, "alpha", 0.95), i % 3
                    == 0 ? LEAF : GOLD, i % 3 == 0 ? LEAF_CORE : GOLD_CORE);
        }
        for (int i = 0; i < 20; i++) {
            Vec3 d = V.unit(new Vec3(Kit.rand() - 0.5, Kit.rand() * 0.7 - 0.2, Kit.rand() - 0.5));
            JobTraits14.petal(lv, V.add(flowerAt, d, 0.8), d, 3.5 + Kit.rand() * 3, 0.2, 1.0, Opt.empty());
        }
        SkillFx.play(lv, "block.amethyst_block.resonate", ground, 0.7, 1.0);
        SkillFx.play(lv, "block.beacon.power_select", ground, 1.4, 0.9);
        SkillFx.play(lv, "entity.player.levelup", ground, 1.5, 0.6);
        for (ServerPlayer o : JobTraits4.allies(sp, ground, r)) {
            double max = RpgHealth.maxOf(o);
            double back = Math.min(JobTraits14.lostIn(field, o), max);
            JobTraits14.saintHeal(sp, o, back + max * heal);
            Opt oo = (o == sp ? JobFx.hide1p(sp) : Opt.empty()).with("vel", V.add(RuneKit.bodyVel(o), new Vec3(0, 0.5, 0)));
            JobFx14.bloom(lv, V.up(o.position(), o.getBbHeight() + 0.7), 0.6, 2, 5, 0.8, false, oo, PETAL, PETAL_CORE);
            JobFx.healCross(lv, V.up(o.position(), 1.3), 0.55, j, 0.7, oo);
        }
        int[] cnt = {0};
        for (LivingEntity e : Kit.around(sp, ground, r, 4)) {
            if (!e.isAlive() || e.isRemoved()) continue;
            JobKit.strike(sp, e, j, P, k.num("bloom", 12), true, true, life, 0, d -> {
                JobImpact.fx(sp, e, j, 1.4);
                if (cnt[0]++ < 6 && e.level() instanceof ServerLevel el) {
                    JobFx14.bloom(el, Kit.bodyPoint(e), 0.9 + Kit.bodyPad(e) * 0.4, 4, 4, 0.5, false, Opt.of("vel", RuneKit.entVel(e)), PETAL, PETAL_CORE);
                    JobFx.burst(el, Kit.bodyPoint(e), 1.1 + Kit.bodyPad(e) * 0.5, GOLD, GOLD_CORE, 0.35, Opt.of("vel", RuneKit.entVel(e)));
                }
            });
        }
    }
}
