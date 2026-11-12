package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 0.25.0 사냥꾼 룬 (hunter — 민첩 1.0 + 체력 0.5, 두 스탯 계열): 덫 · 그물 · 영혼의 짐승. 짐승은 rpg:rfx_beast (매 · 늑대 머리 · 발톱 자국), 그물은 rpg:rfx_net,
 * 덫은 블록 표시 (쇠 턱이 닫힘). 마무리 숫자는 굵은 연둣빛 ! (FERAL).
 * 강철 덫 (던진 덫이 닫히며 물고 묶음) · 매의 습격 (영혼의 매가 겨눈 적에게 네 번 내리꽂힘) · 사냥 그물 (그물을 덮어 묶고 세 번 조임) ·
 * 늑대 무리 (영혼의 늑대들이 달려들어 다섯 번 물고 울부짖음) · 야수왕 강림 (거대한 영혼의 늑대왕이 내려와 할퀴고 짓밟음 — 짙은 숲빛 하늘).
 */
final class RuneHunter {
    private RuneHunter() {}

    private static final double[][] C = RuneKit.colorOf("hunter");
    /** 연둣빛 영혼 · 흰 심지 · 짙은 숲 */
    static final double[] WILD = C[0];
    static final double[] CORE = C[1];
    static final double[] FOREST = {0.18, 0.42, 0.1};
    private static final int[] FALCON = {0, 1};
    private static final int[] WOLF = {2, 3};
    private static final int[] CLAW = {4, 5};

    static void register() {
        RuneCast.register("steelTrap", RuneHunter::steelTrap);
        RuneCast.register("falconStrike", RuneHunter::falconStrike);
        RuneCast.register("huntNet", RuneHunter::huntNet);
        RuneCast.register("wolfPack", RuneHunter::wolfPack);
        RuneCast.register("beastKing", RuneHunter::beastKing);
    }

    // ------------------------------------------------------------ 공용: 짐승 그림

    /** 영혼의 짐승 그림 (rpg:rfx_beast — 보는 사람을 향함): cells = FALCON · WOLF · CLAW */
    static void beast(ServerLevel level, Vec3 at, int[] cells, Opt o) {
        RuneKit.rfx2(level, "beast", at, o, WILD, CORE, cells, null);
    }

    /** 할퀸 자리: 발톱 자국 · 연둣빛 터짐 · 깃털 같은 알갱이 */
    static void claw(ServerLevel level, Vec3 at, double size) {
        beast(level, at, CLAW, Opt.of("size", size, "life", 0.32, "grow", 0.25, "fade", 0.55, "spin", (Kit.rand() - 0.5) * 50));
        SkillFx.burst(level, at, WILD, size * 0.9, 0.22);
        SkillFx.particle(level, ParticleTypes.CRIT, at, 6, 0.25, 0.3);
    }

    // ------------------------------------------------------------ 일반: 강철 덫

    private static final BlockState IRON = Blocks.IRON_BLOCK.defaultBlockState();
    private static final BlockState PLATE = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState TRIGGER = Blocks.VERDANT_FROGLIGHT.defaultBlockState();
    /** 덫 크기 (모형 배 — 1인칭 5칸 앞에서도 턱이 보이게) */
    private static final double TRAP_SCALE = 1.3;

    /** 강철 덫 모형 (닫히는 턱 둘 — 무리 1 은 +z 쪽에 눕고 -90° 로 섬, 무리 2 는 -z 쪽 · +90°) */
    private static PairKit.Rig trap(ServerLevel level, Vec3 base, int life, Vec3 offset, Quaternionf rot) {
        PairKit.Rig t = new PairKit.Rig(level, base, life);
        t.offset = offset;
        t.rot = rot;
        t.scale = TRAP_SCALE;
        t.add(PLATE, new Vec3(0, 0.03, 0), 1.3, 0.06, 1.75, PairKit.LIT);
        t.add(TRIGGER, new Vec3(0, 0.07, 0), 0.45, 0.07, 0.45, PairKit.GLOW);
        int a = t.group(new Vec3(0, 0.06, 0));
        int b = t.group(new Vec3(0, 0.06, 0));
        for (int s = 1; s >= -1; s -= 2) {
            int grp = s > 0 ? a : b;
            t.add(grp, IRON, new Vec3(0, 0.06, 0.8 * s), new Vec3(1.2, 0.08, 0.08), null, PairKit.LIT);
            t.add(grp, IRON, new Vec3(0.6, 0.06, 0.4 * s), new Vec3(0.08, 0.08, 0.8), null, PairKit.LIT);
            t.add(grp, IRON, new Vec3(-0.6, 0.06, 0.4 * s), new Vec3(0.08, 0.08, 0.8), null, PairKit.LIT);
            for (int i = 0; i < 4; i++) t.add(grp, IRON, new Vec3(-0.42 + i * 0.28, 0.17, 0.78 * s), new Vec3(0.07, 0.22, 0.07), null, PairKit.LIT);
        }
        return t;
    }

    /**
     * 강철 덫: 조준한 곳 (range 칸) 으로 덫 (블록 표시 — 쇠 턱 둘 · 빛나는 방아쇠) 을 던짐 → 떨어진 덫의 턱이 철컥 닫히며 둘레 radius 칸의 적을 묾
     * (위력 × mult) · 묶음 (root 틱 — 보스는 느려짐만)
     */
    static boolean steelTrap(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 g = RuneKit.groundAim(sp, k.num("range"));
        Vec3 f = V.flat(V.sub(g, sp.position()));
        double radius = k.num("radius");
        int root = (int) k.num("root");
        Quaternionf q = PairKit.facing(RuneKit.turn(f, 90));
        Vec3 hand = V.add(V.up(sp.position(), 1.3), f, 0.8);
        // 던짐: 손에서 덫까지 연둣빛 포물선
        for (int i = 0; i < 6; i++) {
            Vec3 a = PairKit.arcAt(hand, V.up(g, 0.4), 1.6, i / 6.0);
            Vec3 b = PairKit.arcAt(hand, V.up(g, 0.4), 1.6, (i + 1) / 6.0);
            RuneKit.streak(level, a, b, WILD, 0.16, 0.3, 0.9, i * 0.02);
        }
        SkillFx.play(level, "entity.fishing_bobber.throw", sp.position(), 0.8, 1.0);
        SkillFx.play(level, "item.armor.equip_chain", sp.position(), 1.2, 0.8);
        Quaternionf open = new Quaternionf();
        PairKit.Rig t = trap(level, g, 8 + root + 6, new Vec3(0, 2.6, 0), new Quaternionf(q).mul(PairKit.about(new Vec3(0, 1, 0), 140)));
        RuneKit.later(2, () -> t.pose(Vec3.ZERO, q, TRAP_SCALE, 3, open, open, open));
        RuneKit.later(5, () -> {
            SkillFx.play(level, "block.chain.place", g, 1.0, 1.0);
            SkillFx.rune(level, g, Opt.of("size", radius, "life", 0.35, "color", WILD, "alpha", 0.8, "rate", 200.0, "progress", 1.0));
        });
        // 철컥 (턱이 닫힘)
        RuneKit.later(6, () -> t.pose(Vec3.ZERO, q, TRAP_SCALE, 2, open, PairKit.about(new Vec3(1, 0, 0), -88), PairKit.about(new Vec3(1, 0, 0), 88)));
        RuneKit.later(8, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            claw(level, V.up(g, 0.6), 0.9);
            SkillFx.particle(level, ParticleTypes.CRIT, V.up(g, 0.5), 16, 0.4, 0.4);
            RuneKit.zonePulse(level, g, WILD, radius, 0.35);
            RuneBoost.impact(level, g, WILD, CORE, radius, r.grade()); // 0.50.0 2D 강화 (아래 등급 터짐): 덫의 턱이 닫히는 자리
            SkillFx.play(level, "block.iron_trapdoor.close", g, 0.8, 1.0);
            SkillFx.play(level, "entity.fox.bite", g, 0.7, 1.0);
            SkillFx.play(level, "block.anvil.land", g, 1.8, 0.3);
            for (LivingEntity e : Kit.around(sp, g, radius, 2)) {
                PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                    claw(level, Kit.bodyPoint(e), 0.7);
                    PairKit.root(e, root);
                    RuneKit.later(1, () -> SkillFx.rune(level, e.position(), Opt.of("size", 0.9 + Kit.bodyPad(e), "life", root / 20.0, "color", WILD, "alpha", 0.75,
                            "rate", 60.0, "progress", 1.0)));
                });
            }
        });
        RuneKit.later(8 + root, () -> t.crumble(5, 0.05, 0.3, 0.2));
        return true;
    }

    // ------------------------------------------------------------ 희귀: 매의 습격

    /** 매 (3D 몹 rpg:rune_hawk): 덮치기 동작 (dive) 을 틀고 발톱이 닿기까지 (0.25초) · 크기 (날개 2칸 × 1.35 — 영상에서 작아 키움) */
    static final int HAWK_STRIKE_AT = 5;
    static final double HAWK_SCALE = 1.35;

    /**
     * 매의 습격: 앞 range 칸 · cone 도의 가장 가까운 적에게 영혼의 매를 날림 — gap 틱마다 hits 번 하늘에서 급강하해 대상 둘레 radius 칸의 적을
     * 할큄 (위력 × mult) 하고 다시 날아오름. 대상이 쓰러지면 둘레의 다른 적에게. 대상이 없으면 쓰지 않음.
     * 2026-11-12 매는 3D 몹 (사용자 "매 3d" — rpg:rune_hawk, tools/rune_mobs_art.py): 손에서 날아올라 대상 둘레 하늘을 돌다 발톱을 내밀고 내리꽂힘 (dive 동작 —
     * 발톱이 닿는 틱 = 피해 틱) → 다음 급강하 자리로 솟음 → 마지막에 날아가며 흩어짐. 몹을 못 세우면 예전 그림 (rpg:rfx_beast 매)
     */
    static boolean falconStrike(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        List<LivingEntity> ts = RuneKit.coneTargets(sp, k.num("range"), k.num("cone"), 1);
        if (ts.isEmpty()) return PairKit.noTarget(sp, k);
        String life = RuneKit.lifeToken(sp);
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        double radius = k.num("radius");
        LivingEntity[] cur = {ts.get(0)};
        Vec3[] last = {cur[0].position()};
        ServerLevel level0 = sp.level();
        Vec3 f0 = Kit.facingOf(sp);
        // 손에서 날아오름
        Vec3 hand = V.add(V.up(sp.position(), 1.4), f0, 0.8);
        RuneMob hawk = RuneMob.spawn(RuneMob.HAWK, level0, hand, f0, 4 + hits * gap + 40, HAWK_SCALE, true);
        if (hawk == null) {
            Vec3 h2 = V.add(V.up(sp.position(), 1.6), f0, 0.8);
            Vec3 up0 = V.up(V.add(h2, f0, 1.5), 2.5);
            beast(level0, h2, FALCON, Opt.of("dir", V.unit(V.sub(up0, h2)), "speed", V.len(V.sub(up0, h2)) / 0.2, "size", 0.6, "life", 0.2, "fade", 0.2));
        } else {
            SkillFx.sparks(level0, hand, WILD, 6, 2.5);
            SkillFx.burst(level0, hand, WILD, 0.6, 0.25);
        }
        SkillFx.play(level0, "entity.parrot.fly", sp.position(), 0.8, 1.0);
        SkillFx.play(level0, "mob.phantom.flap", sp.position(), 1.5, 0.6);
        boolean[] burst = {false}; // 0.50.0 2D 강화 (아래 등급 터짐): 매가 처음 할퀸 적에 한 번
        for (int i = 0; i < hits; i++) {
            final int ii = i;
            final int at = 4 + i * gap;
            // 발톱을 내미는 동작 (닿는 틱 = at)
            RuneKit.later(Math.max(0, at - HAWK_STRIKE_AT), () -> {
                if (hawk != null && hawk.alive() && RuneKit.sameLife(sp, life)) hawk.anim("dive");
            });
            // 급강하 (4틱 전에 하늘에서 — 매는 대상의 몸을 따라감)
            RuneKit.later(at - 4, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                if (cur[0] == null || cur[0].isRemoved() || !cur[0].isAlive()) {
                    List<LivingEntity> near = RuneKit.nearestTo(Kit.around(sp, last[0], 6, 3), last[0]);
                    cur[0] = near.isEmpty() ? null : near.get(0);
                }
                Vec3 tgt = cur[0] != null ? Kit.bodyPoint(cur[0]) : V.up(last[0], 0.8);
                last[0] = cur[0] != null ? cur[0].position() : last[0];
                if (hawk != null && hawk.alive()) {
                    LivingEntity e = cur[0];
                    Vec3 from = V.up(hawk.pos(), 0.5);
                    hawk.moveTo(() -> e != null && !e.isRemoved() ? V.up(Kit.bodyPoint(e), -0.55) : V.up(tgt, -0.55), 4, ii == 0 ? 1.2 : 0, ii == 0 ? RuneMob.SMOOTH
                            : RuneMob.IN, true);
                    RuneKit.streak(level0, from, tgt, WILD, 0.2, 0.25, 0.8, 0.0);
                    SkillFx.play(level0, "mob.phantom.flap", from, 1.3, 0.7);
                    return;
                }
                double a = Kit.rand() * Math.PI * 2;
                Vec3 from = new Vec3(tgt.x + Math.cos(a) * 3.2, tgt.y + 4.5, tgt.z + Math.sin(a) * 3.2);
                Vec3 d = V.sub(tgt, from);
                beast(level0, from, FALCON, Opt.of("dir", V.unit(d), "speed", V.len(d) / 0.2, "size", 0.85, "life", 0.2, "fade", 0.1, "spin", ii % 2 == 0 ? 15.0
                        : -15.0));
                RuneKit.streak(level0, from, tgt, WILD, 0.2, 0.25, 0.8, 0.0);
                SkillFx.play(level0, "mob.phantom.flap", from, 1.3, 0.7);
            });
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 c = cur[0] != null && !cur[0].isRemoved() ? cur[0].position() : last[0];
                claw(level, V.up(c, 1.0), 1.0);
                SkillFx.fx(level, "rpg:shock_ring", V.up(c, 0.08), Opt.of("dir", V.ZERO, "size", radius * 0.6, "life", 0.3, "color", WILD, "alpha", 0.85));
                for (int j = 0; j < 6; j++) {
                    double a = j / 6.0 * Math.PI * 2;
                    SkillFx.fx(level, "rpg:arrow_spark", V.up(c, 1.2), Opt.of("dir", V.unit(new Vec3(Math.cos(a), 0.6, Math.sin(a))), "speed", 3.0, "size", 0.14,
                            "life", 0.4, "color", j % 2 == 0 ? WILD : CORE, "alpha", 1.0));
                }
                SkillFx.play(level, "mob.phantom.bite", c, 1.3, 0.8);
                SkillFx.play(level, "entity.player.attack.sweep", c, 1.4, 0.6);
                for (LivingEntity e : Kit.around(sp, c, radius, 3)) {
                    PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                        if (RuneKit.fxQuota("falcon", 3)) claw(level, Kit.bodyPoint(e), 0.7);
                        if (!burst[0]) {
                            burst[0] = true;
                            RuneBoost.impact(level, e.position(), WILD, CORE, 2.0, r.grade()); // 0.50.0 2D 강화 (아래 등급 터짐): 처음 할퀸 적의 발밑
                        }
                        // 0.49.0 사냥감 표식: vulnTicks 동안 받는 피해 + vuln (머리 위 금 간 표식)
                        if (ii == hits - 1) RuneMarks.vuln(e, k.numOr("vuln", 0), (int) k.numOr("vulnTicks", 0), WILD, CORE, RuneKit.fxQuota("falconMark", 6)); // 마지막 급강하에
                    });
                }
                // 다시 날아오름 (다음 급강하 자리 — 대상 둘레 하늘) · 마지막이면 날아가며 흩어짐
                double a = Kit.rand() * Math.PI * 2;
                Vec3 from = V.up(c, 1.0);
                Vec3 to = new Vec3(c.x + Math.cos(a) * 3.0, c.y + 4.5, c.z + Math.sin(a) * 3.0);
                if (hawk != null && hawk.alive()) {
                    if (ii < hits - 1) hawk.moveTo(to, Math.max(2, gap - HAWK_STRIKE_AT), 0, RuneMob.OUT, true);
                    else {
                        hawk.moveTo(V.up(to, 3.0), 12, 0, RuneMob.OUT, true);
                        RuneKit.later(6, () -> {
                            if (!hawk.alive()) return;
                            SkillFx.sparks(level, V.up(hawk.pos(), 0.5), WILD, 8, 3.0);
                            hawk.leave("vanish", 8);
                        });
                    }
                    return;
                }
                beast(level, from, FALCON, Opt.of("dir", V.unit(V.sub(to, from)), "speed", V.len(V.sub(to, from)) / 0.3, "size", 0.75, "life", 0.3, "fade", 0.4));
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 고귀: 사냥 그물

    /** 그물 (rpg:rfx_net — dir 쪽을 보는 판, 땅에 덮을 때는 위): at 에 size, vel 로 움직임 */
    /** 3D 그물 크기: 반지름 (칸) → 모델 크기 (지름 30 픽셀) */
    static double netScale(double radius) {
        return radius * 2 / (RuneProps.NET_D / 16.0);
    }

    static void net(ServerLevel level, Vec3 at, Vec3 dir, double size, double life, Vec3 vel, double spin, double alpha) {
        RuneKit.rfx2(level, "net", at, Opt.of("dir", dir, "size", size, "life", life, "vel", vel, "spin", spin, "alpha", alpha, "fade", 0.2), WILD, CORE,
                new int[] {0, 1}, null);
    }

    /**
     * 사냥 그물: 조준한 곳 (range 칸) 으로 그물을 펼쳐 던짐 → 반지름 radius 칸을 덮어 안의 적을 묶고 (root 틱 — 보스는 느려짐만) gap 틱마다 hits 번 조여
     * 벰 (위력 × mult — 그물이 오그라들며 빛남 · 가운데로 당기는 줄)
     */
    static boolean huntNet(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 g = RuneKit.groundAim(sp, k.num("range"));
        Vec3 f = V.flat(V.sub(g, sp.position()));
        double radius = k.num("radius");
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int root = (int) k.num("root");
        int fly = 6;
        Vec3 hand = V.add(V.up(sp.position(), 1.4), f, 1.0);
        Vec3 land = V.up(g, 0.12);
        int holdEnd = fly + (hits - 1) * gap + 8;
        // 2026-11-12 3D 그물 (사용자 "그물 3d" — tools/vfx_props.py net: 덮개 모양 그물 · 매듭 · 추, 빛 줄 = 사냥꾼 계열 색): 손에서 포물선으로 날며 펼쳐짐
        // (돌며 앞으로 기욺) → 땅을 덮음 → 조일 때마다 오그라듦 → 끝에 작아지며 사라짐
        mmorpg.fabric.vfx.Vfx.Fx netFx = RuneProps.fx(level, land, holdEnd + 12);
        double ns = netScale(radius);
        double[] base = {8, 0, 8};
        mmorpg.fabric.vfx.Vfx.Prop net3d = RuneProps.prop(netFx, RuneProps.NET, hand, base, RuneProps.along(V.unit(V.add(V.UP, f, -0.6)), V.rightOf(f)), ns * 0.25,
                WILD);
        for (int t = 0; t < fly; t++) {
            final int tt = t;
            netFx.on(t + 1, () -> {
                double u = (tt + 1) / (double) fly;
                Vec3 p = PairKit.arcAt(hand, land, 2.0, u);
                Vec3 up = V.unit(V.add(V.UP, f, -0.6 * (1 - u)));
                net3d.to(p, RuneProps.along(up, RuneKit.turn(V.rightOf(f), tt * 25.0)), ns * (0.25 + 0.75 * u), 1);
            });
        }
        SkillFx.play(level, "entity.fishing_bobber.throw", sp.position(), 0.6, 1.0);
        SkillFx.play(level, "mob.breeze.whirl", sp.position(), 1.4, 0.6);
        netFx.on(holdEnd, () -> net3d.to(V.up(land, -0.3), null, ns * 0.3, 8));
        netFx.on(holdEnd + 8, net3d::remove);
        // 덮은 그물 둘레의 빛 (2D)
        for (int t = fly; t < holdEnd; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                int step = Math.min(hits - 1, (tt - fly) / gap);
                double s = radius * (1.0 - 0.1 * step);
                if (tt % 6 == 0) {
                    for (int j = 0; j < 6; j++) {
                        Vec3 p = PairKit.ring(land, s * 0.95, j / 6.0 * Math.PI * 2);
                        SkillFx.fx(level, "rpg:arrow_spark", V.up(p, 0.15), Opt.of("dir", V.UP, "speed", 0.6, "size", 0.16, "life", 0.3, "color", CORE, "alpha", 1.0));
                    }
                }
            });
        }
        Map<UUID, LivingEntity> caught = new LinkedHashMap<>();
        RuneKit.later(fly, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            SkillFx.play(level, "block.chain.place", g, 0.7, 1.0);
            SkillFx.play(level, "item.armor.equip_chain", g, 0.8, 1.0);
            RuneKit.zonePulse(level, g, WILD, radius, 0.4);
            for (LivingEntity e : Kit.around(sp, g, radius, 2.5)) {
                caught.put(e.getUUID(), e);
                PairKit.root(e, root);
            }
        });
        for (int i = 0; i < hits; i++) {
            final int ii = i;
            RuneKit.later(fly + i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double s = radius * (1.0 - 0.1 * ii);
                // 가운데로 당기는 줄 · 빛나는 그물
                for (int j = 0; j < 8; j++) {
                    Vec3 p = V.up(PairKit.ring(land, s, j / 8.0 * Math.PI * 2 + ii * 0.3), 0.2);
                    SkillFx.fx(level, "rpg:light_cut", V.lerp(p, land, 0.5), Opt.of("dir", V.unit(V.sub(land, p)), "size", s * 0.5, "thick", 0.18, "life", 0.35,
                            "delay", 0.2, "color", WILD, "alpha", 0.9));
                }
                // 그물이 조이며 오그라듦 (3D — 번쩍 커졌다 작아짐)
                net3d.size(netScale(s) * 1.06, 1);
                netFx.on(netFx.age() + 2, () -> net3d.size(netScale(s * 0.98), 3));
                if (ii == hits - 1) RuneBoost.impact(level, g, WILD, CORE, radius, r.grade()); // 0.50.0 2D 강화 (아래 등급 터짐): 마지막 조이기
                SkillFx.play(level, "block.chain.hit", g, 0.8 + ii * 0.1, 1.0);
                SkillFx.play(level, "entity.player.attack.sweep", g, 1.2, 0.5);
                for (LivingEntity e : Kit.around(sp, g, radius, 2.5)) {
                    if (caught.putIfAbsent(e.getUUID(), e) == null) PairKit.root(e, Math.max(10, root - ii * gap));
                }
                for (LivingEntity e : caught.values()) {
                    if (e.isRemoved() || e.position().distanceTo(g) > radius + 3) continue;
                    PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                        if (RuneKit.fxQuota("net", 5)) claw(level, Kit.bodyPoint(e), 0.6);
                    });
                }
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 영웅: 늑대 무리

    /** 늑대 정령 (3D 몹 rpg:spirit_wolf) 의 덮치기 (spirit_wolf.pounce): 뛰어오른 뒤 콱 무는 틱 (0.15초) = 물기 피해 틱 */
    static final int WOLF_POUNCE_AT = 3;

    /**
     * 늑대 무리: 앞 range 칸 · cone 도의 적 (가까운 순 8) 에게 영혼의 늑대 다섯이 내 둘레에서 달려들어 gap 틱마다 hits 번 물어뜯음
     * (위력 × mult — 발톱 자국) → 무리가 울부짖음: 물린 적 · 첫 대상 둘레 radius 칸의 적 (위력 × crush, 밀쳐냄). 대상이 없으면 쓰지 않음.
     * 2026-11-12 늑대는 3D 몹 (사용자 "늑대 3d" — 늑대 정령 rpg:spirit_wolf): 내 앞 둘레에서 깨어나 물 때마다 대상 앞으로 뛰어올라 덮침 (pounce — 콱 = 피해 틱,
     * 같은 대상의 늑대끼리 옆으로 비켜 섬) → 울부짖음 (howl) → 정령빛으로 흩어짐. 몹을 못 세우면 그 늑대는 예전 그림 (rpg:rfx_beast 늑대 머리)
     */
    static boolean wolfPack(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        List<LivingEntity> ts = RuneKit.coneTargets(sp, k.num("range"), k.num("cone"), 8);
        if (ts.isEmpty()) return PairKit.noTarget(sp, k);
        String life = RuneKit.lifeToken(sp);
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        ServerLevel level0 = sp.level();
        Vec3 f0 = Kit.facingOf(sp);
        int howlAt = hits * gap + 2;
        // 내 둘레에서 늑대 다섯이 깨어남 (앞쪽 부채꼴)
        RuneMob[] wolves = new RuneMob[5];
        for (int j = 0; j < 5; j++) {
            Vec3 d = RuneKit.turn(f0, -56 + j * 28);
            Vec3 p = V.add(sp.position(), d, 1.7);
            Vec3 g = new Vec3(p.x, RuneKit.groundAt(level0, p, sp.getY()), p.z);
            wolves[j] = RuneMob.spawn(RuneMob.WOLF, level0, g, f0, howlAt + 60, 1.0, true);
            if (wolves[j] != null) {
                SkillFx.sparks(level0, V.up(g, 0.6), WILD, 6, 2.5);
                SkillFx.fx(level0, "rpg:shock_ring", V.up(g, 0.06), Opt.of("dir", V.ZERO, "size", 0.7, "life", 0.35, "color", WILD, "alpha", 0.85));
            } else beast(level0, V.up(p, 0.8), WOLF, Opt.of("size", 0.4, "life", 0.3, "grow", 0.4, "fade", 0.5));
            SkillFx.particle(level0, ParticleTypes.POOF, V.up(p, 0.6), 2, 0.15, 0.02);
        }
        SkillFx.play(level0, "entity.polar_bear.warning", sp.position(), 1.4, 0.7);
        SkillFx.play(level0, "mob.wolf.shake", sp.position(), 0.8, 1.0);
        Map<UUID, LivingEntity> bitten = new LinkedHashMap<>();
        for (LivingEntity e : ts) bitten.put(e.getUUID(), e);
        for (int i = 0; i < hits; i++) {
            final int ii = i;
            RuneKit.later(i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 c = sp.position();
                List<LivingEntity> alive = PairKit.alive(new ArrayList<>(bitten.values()));
                if (alive.isEmpty()) return;
                // 늑대 다섯이 대상들에게 달려듦 (대상을 돌아가며)
                for (int j = 0; j < 5; j++) {
                    LivingEntity e = alive.get((ii + j) % alive.size());
                    Vec3 tgt = Kit.bodyPoint(e);
                    RuneMob w = wolves[j];
                    if (w != null && w.alive()) {
                        Vec3 from = w.pos();
                        double side = (j - 2) * 0.4;
                        w.moveTo(() -> {
                            Vec3 ep = e.position();
                            Vec3 away = Math.hypot(from.x - ep.x, from.z - ep.z) > 1e-3 ? V.flat(V.sub(from, ep)) : V.scale(f0, -1);
                            return V.add(V.add(ep, away, Kit.bodyPad(e) + 0.45), V.rightOf(away), side);
                        }, WOLF_POUNCE_AT, 0.9, RuneMob.LINEAR, true);
                        w.anim("pounce");
                        RuneKit.streak(level, V.up(from, 0.6), tgt, WILD, 0.14, 0.2, 0.7, 0.0);
                        RuneKit.later(WOLF_POUNCE_AT, () -> {
                            if (w.alive() && !e.isRemoved()) w.face(V.sub(e.position(), w.pos()));
                        });
                        continue;
                    }
                    Vec3 sideV = V.rightOf(V.flat(V.sub(tgt, c)));
                    Vec3 from = V.up(V.add(V.lerp(c, e.position(), 0.35), sideV, (j - 2) * 0.9), 0.8);
                    Vec3 d = V.sub(tgt, from);
                    beast(level, from, WOLF, Opt.of("dir", V.unit(d), "speed", V.len(d) / 0.18, "size", 0.6, "life", 0.18, "fade", 0.15));
                    RuneKit.streak(level, from, tgt, WILD, 0.14, 0.2, 0.7, 0.0);
                }
                SkillFx.play(level, "entity.fox.bite", c, 0.8 + ii * 0.08, 0.9);
                RuneKit.later(3, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    for (LivingEntity e : alive) {
                        if (e.isRemoved()) continue;
                        PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                            if (RuneKit.fxQuota("wolf", 4)) claw(level, Kit.bodyPoint(e), 0.75);
                            // 0.49.0 물어뜯긴 적은 slowTicks 동안 둔화
                            if (k.numOr("slowTicks", 0) > 0) RuneKit.effect(e, "slowness", (int) k.num("slowTicks"), (int) k.numOr("slowAmp", 1));
                        });
                    }
                    SkillFx.play(level, "mob.phantom.bite", c, 0.9, 0.7);
                });
            });
        }
        // 울부짖음 (늑대들이 고개를 듦) → 정령빛으로 흩어짐
        RuneKit.later(howlAt, () -> {
            if (!RuneKit.sameLife(sp, life)) {
                for (RuneMob w : wolves) if (w != null) w.discard();
                return;
            }
            ServerLevel level = sp.level();
            List<LivingEntity> alive = PairKit.alive(new ArrayList<>(bitten.values()));
            Vec3 c = alive.isEmpty() ? ts.get(0).position() : alive.get(0).position();
            double radius = k.num("radius");
            for (RuneMob w : wolves) {
                if (w == null || !w.alive()) continue;
                w.face(V.sub(c, w.pos()));
                w.play(mmorpg.fabric.mob.TyrfingWarrior.WOLF_HOWL);
            }
            double sc = RuneKit.nearK(sp, V.up(c, 2.5), 6, 0.5);
            beast(level, V.up(c, 2.6 * sc), WOLF, Opt.of("size", 1.6 * sc, "life", 0.6, "grow", 0.4, "fade", 0.5));
            for (int j = 0; j < 3; j++) {
                final int jj = j;
                RuneKit.later(j * 2, () -> SkillFx.fx(level, "rpg:shock_ring", V.up(c, 0.1 + jj * 0.6), Opt.of("dir", V.ZERO, "size", radius * (0.4 + jj * 0.15),
                        "life", 0.45, "color", jj % 2 == 0 ? WILD : CORE, "alpha", 0.9)));
            }
            RuneKit.zonePulse(level, c, WILD, radius, 0.5);
            RuneBoost.impact(level, c, WILD, CORE, radius, r.grade()); // 0.50.0 2D 강화 (아래 등급 터짐): 무리의 울부짖음 (첫 대상 둘레)
            RuneKit.shakeAround(level, c, 10, 0.25, 0.35);
            SkillFx.play(level, "entity.ravager.roar", c, 1.3, 0.8);
            SkillFx.play(level, "entity.polar_bear.warning", c, 0.8, 1.0);
            SkillFx.play(level, "item.goat_horn.sound.1", c, 1.6, 0.5);
            Map<UUID, LivingEntity> got = new LinkedHashMap<>();
            for (LivingEntity e : alive) got.put(e.getUUID(), e);
            for (LivingEntity e : Kit.around(sp, c, radius, 3)) got.putIfAbsent(e.getUUID(), e);
            for (LivingEntity e : got.values()) {
                PairKit.hit(sp, r, e, P, k.num("crush"), DamageNumbers.Style.FERAL, life, dmg -> {
                    claw(level, Kit.bodyPoint(e), 0.9);
                    if (!PairKit.boss(e)) RuneKit.knock(e, V.sub(e.position(), c), 0.45, 0.3);
                });
            }
            RuneKit.later(22, () -> {
                for (RuneMob w : wolves) {
                    if (w == null || !w.alive()) continue;
                    Vec3 at = V.up(w.pos(), 0.6);
                    SkillFx.sparks(level, at, WILD, 10, 3.5);
                    SkillFx.sparks(level, at, CORE, 4, 2.5);
                    SkillFx.fx(level, "rpg:arrow_spark", at, Opt.of("dir", V.UP, "speed", 2.0, "size", 0.2, "grow", 0.5, "life", 0.5, "color", WILD, "alpha", 0.9));
                    w.discard();
                }
                SkillFx.play(level, "block.amethyst_block.break", c, 1.6, 0.5);
            });
        });
        return true;
    }

    // ------------------------------------------------------------ 초월: 야수왕 강림

    /**
     * 야수왕 (3D 몹 rpg:rune_beast_king): 크기 (모델 어깨 1.5칸 · 뿔 끝 2.8칸 × 2.1 — 1.5 는 영상에서 드라우그만 해 2.1) · 하늘에서 내려앉는 틱 ·
     * 할퀴기 동작에서 발톱이 닿는 틱 (0.1초) · 짓밟기에서 앞발이 땅에 닿는 틱 (0.45초)
     */
    static final double BEAST_SCALE = 2.1;
    static final int BEAST_LAND = 10;
    static final int BEAST_STRIKE_AT = 2;
    static final int BEAST_STOMP_AT = 9;
    /** 야수왕이 서는 자리: 조준한 곳 너머 (쓴 사람에게서 멀어지는 쪽) 이만큼 — 쓴 사람 쪽을 보고 할큄 (머리 · 앞발이 조준한 곳 위에 오게) */
    static final double BEAST_BACK = 3.9;

    /**
     * 야수왕 강림: 조준한 곳 (range 칸) 에 짙은 숲빛 하늘 (3돌부터) · 연둣빛 영역 문양 · 빛기둥 → 하늘에서 거대한 영혼의 늑대왕이 내려옴 (delay 틱) →
     * gap 틱마다 hits 번 거대한 발톱이 반지름 radius 칸을 할큄 (위력 × mult) → 늑대왕이 땅을 짓밟음 (위력 × crush — 거대한 발자국 충격 · 갈라짐, 띄움).
     * 대상이 없어도 씀.
     * 2026-11-12 야수왕은 3D 몹 (사용자 "야수왕 네크로맨서 몹 처럼 3d몹으로 활퀴는 모샨으로" — rpg:rune_beast_king, 왕관 · 정령빛 뿔 · 갈기): 조준한 곳 너머 하늘에서
     * 떨어져 내려앉고 (BEAST_LAND) 울부짖으며 일어섬 (rear) → 할퀼 때마다 오른발 · 왼발을 번갈아 휘두름 (claw_r · claw_l — 발톱이 닿는 틱 = 피해 틱) →
     * 더 높이 일어섰다 두 앞발로 짓밟음 (stomp — 땅에 닿는 틱 = 짓밟기 피해 틱) → 빛으로 흩어짐. 발톱 자국 · 빛줄기 · 발자국 충격은 2D 그대로.
     * 몹을 못 세우면 예전 그림 (하늘에 떠 있는 늑대왕 rpg:rfx_beast)
     */
    static boolean beastKing(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 aim = RuneKit.groundAim(sp, k.num("range"));
        double radius = k.num("radius");
        int delay = (int) k.num("delay");
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int lastHit = delay + (hits - 1) * gap;
        int crushAt = lastHit + 14;
        int end = crushAt + 12;
        Vec3 f = V.flat(V.sub(aim, sp.position()));
        RuneKit.ultSkyFor(sp, level, aim, "beastKing", end + 30, null);
        RuneKit.ultFlashFor(sp, level, aim, "beastKing", "flash", Opt.empty());
        RuneKit.domainSigil(level, aim, radius, "spirit", WILD, CORE, Opt.of("life", end / 20.0, "spinRate", -26.0, "grow", 0.15, "alpha", 0.8, "fade", 0.15));
        RuneKit.selfCue(sp, WILD, CORE, true);
        RuneKit.godRays(sp, level, aim, radius * 0.95, 6, WILD, CORE, Opt.of("life", delay / 20.0 + 0.4, "h", 14.0, "alpha", 0.65));
        SkillFx.play(level, "entity.ravager.roar", sp.position(), 0.7, 0.8);
        SkillFx.play(level, "mob.wither.spawn", aim, 1.6, 0.4);
        // 야수왕: 조준한 곳 너머 하늘에서 떨어져 내려앉음 → 울부짖으며 일어섬
        Vec3 spot = V.add(aim, f, BEAST_BACK);
        Double gy = RuneKit.solidGround(level, V.up(spot, 4), 10);
        Vec3 base = new Vec3(spot.x, gy != null ? gy : aim.y, spot.z);
        int land = Math.max(4, Math.min(BEAST_LAND, delay - 8));
        RuneMob king = RuneMob.spawn(RuneMob.BEAST_KING, level, V.add(V.up(base, 11), f, 3.0), V.scale(f, -1), end + 40, BEAST_SCALE, true);
        if (king != null) {
            king.anim("summon");
            king.moveTo(base, land, 0, RuneMob.IN, false);
            for (int t = 0; t < land; t += 2) {
                final int tt = t;
                RuneKit.later(t, () -> {
                    if (!king.alive()) return;
                    Vec3 p = V.up(king.pos(), 2.0);
                    SkillFx.particle(level, ParticleTypes.GLOW, p, 6, 1.2, 0.02);
                    if (tt % 4 == 0) RuneKit.streak(level, V.up(p, 3.0), p, WILD, 0.5, 0.25, 0.7, 0.0);
                });
            }
            RuneKit.later(land, () -> {
                if (!king.alive()) return;
                king.hold("rear");
                SkillFx.fx(level, "rpg:shock_ring", V.up(base, 0.08), Opt.of("dir", V.ZERO, "size", 4.2, "life", 0.5, "color", WILD, "alpha", 0.95));
                SkillFx.fx(level, "rpg:dust", V.up(base, 0.6), Opt.of("size", 4.0, "life", 0.7, "color", new double[] {0.55, 0.5, 0.4}, "alpha", 0.7));
                RuneKit.rockBurst(level, V.up(base, 0.3), 8, 5, 0.25);
                SkillFx.particle(level, ParticleTypes.POOF, V.up(base, 0.4), 24, 1.6, 0.08);
                SkillFx.play(level, "mace.heavy_smash_ground", base, 0.6, 1.0);
                SkillFx.play(level, "entity.ravager.roar", base, 0.5, 1.0);
                SkillFx.play(level, "entity.polar_bear.warning", base, 0.6, 1.0);
            });
        } else {
            // 예전 그림: 하늘 높이에서 내려와 (delay) 할퀴는 동안 떠 있음 · 숨결 (2틱마다 다시 그림)
            Vec3 high = V.add(V.up(aim, 9.5), f, 5.0);
            Vec3 hover = V.add(V.up(aim, 4.4), f, 3.6);
            for (int t = 0; t < crushAt; t += 2) {
                final int tt = t;
                RuneKit.later(t, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    double u = Math.min(1, tt / (double) delay);
                    double e = 1 - (1 - u) * (1 - u);
                    Vec3 at = V.add(V.lerp(high, hover, e), V.UP, Math.sin(tt * 0.25) * 0.25);
                    double s = 2.0 + 2.6 * e;
                    beast(level, at, WOLF, Opt.of("size", s, "life", 0.14, "alpha", 0.55 + 0.45 * e, "fade", 0.0));
                });
            }
        }
        RuneKit.later(delay, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            SkillFx.play(level, "entity.ravager.roar", aim, 0.5, 1.0);
        });
        // 거대한 발톱 (반지름 안을 가로지르는 발톱 자국 · 할퀸 적) — 야수왕이 오른발 · 왼발을 번갈아 휘두름
        for (int i = 0; i < hits; i++) {
            final int ii = i;
            if (king != null) {
                RuneKit.later(delay + i * gap - BEAST_STRIKE_AT, () -> {
                    if (king.alive() && RuneKit.sameLife(sp, life)) king.hold(ii % 2 == 0 ? "claw_r" : "claw_l");
                });
            }
            RuneKit.later(delay + i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                List<LivingEntity> ts = Kit.around(sp, aim, radius, 5);
                double ang = (ii % 2 == 0 ? 35 : -35) + (Kit.rand() - 0.5) * 30;
                Vec3 at = V.up(V.add(aim, V.rightOf(f), (ii % 2 == 0 ? -1 : 1) * radius * 0.25), 1.6);
                beast(level, at, CLAW, Opt.of("size", radius * 0.45, "life", 0.4, "grow", 0.3, "fade", 0.5, "spin", ang));
                SkillFx.arc(level, V.up(aim, 1.5), f, Opt.of("shape", "heavy", "flip", ii % 2 == 1, "size", radius * 0.7, "life", 0.26, "speed", 1.0, "spin", ang,
                        "color", WILD, "aura", FOREST));
                for (int j = -1; j <= 1; j++) {
                    Vec3 dj = RuneKit.turn(V.rightOf(f), ang + j * 12);
                    SkillFx.fx(level, "rpg:light_cut", V.up(V.add(aim, f, j * 0.8), 0.08), Opt.of("dir", dj, "size", radius * 0.7, "thick", 0.4, "life", 0.8, "delay",
                            0.5, "color", WILD, "alpha", 0.9));
                }
                RuneKit.zonePulse(level, aim, ii % 2 == 0 ? WILD : CORE, radius, 0.4);
                SkillFx.play(level, "entity.player.attack.sweep", aim, 0.5 + ii * 0.04, 1.0);
                SkillFx.play(level, "mob.phantom.bite", aim, 0.6, 0.8);
                for (LivingEntity e : ts) {
                    PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                        if (RuneKit.fxQuota("beastClaw", 6)) claw(level, Kit.bodyPoint(e), 0.8);
                    });
                }
            });
        }
        // 짓밟기: 야수왕이 더 높이 일어섰다 두 앞발로 내리찍음 → 거대한 발자국 충격
        if (king != null) {
            RuneKit.later(crushAt - BEAST_STOMP_AT, () -> {
                if (king.alive() && RuneKit.sameLife(sp, life)) king.anim("stomp");
            });
        } else {
            Vec3 hover = V.add(V.up(aim, 4.4), f, 3.6);
            RuneKit.later(crushAt - 4, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 d = V.sub(V.up(aim, 1.5), hover);
                beast(level, hover, WOLF, Opt.of("dir", V.unit(d), "speed", V.len(d) / 0.2, "size", 4.6, "life", 0.2, "grow", 0.2, "fade", 0.1));
            });
        }
        RuneKit.later(crushAt - 4, () -> {
            if (RuneKit.sameLife(sp, life)) SkillFx.play(level, "mob.enderdragon.flap", aim, 0.5, 1.0);
        });
        RuneKit.later(crushAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            RuneKit.ultFlashFor(sp, level, aim, "beastKing", "flash", Opt.empty());
            RuneBoost.impact(level, aim, WILD, CORE, radius); // 0.50.0 2D 강화 (초월 나머지): 늑대왕이 짓밟은 터짐
            double sc = RuneKit.nearK(sp, V.up(aim, 2), 8, 0.4);
            // 발자국 (발가락 넷 + 발바닥 — 땅의 충격 고리)
            Vec3 side = V.rightOf(f);
            SkillFx.fx(level, "rpg:shock_ring", V.up(aim, 0.08), Opt.of("dir", V.ZERO, "size", radius * 0.32, "life", 0.6, "color", WILD, "alpha", 0.95));
            for (int j = 0; j < 4; j++) {
                Vec3 toe = V.add(V.add(aim, f, radius * 0.38 + (j == 1 || j == 2 ? radius * 0.08 : 0)), side, (j - 1.5) * radius * 0.2);
                SkillFx.fx(level, "rpg:shock_ring", V.up(toe, 0.1), Opt.of("dir", V.ZERO, "size", radius * 0.1, "life", 0.6, "color", CORE, "alpha", 0.95));
            }
            beast(level, V.up(aim, 2.0 * sc), CLAW, Opt.of("size", radius * 0.5 * sc, "life", 0.4, "grow", 0.4, "fade", 0.6, "spin", 0.0));
            RuneKit.novaRing(level, aim, 1.6, radius * 0.9, WILD, CORE, Opt.of("life", 0.6));
            RuneKit.glowCrack(level, aim, radius, WILD, CORE, Opt.of("life", 1.8, "v", 1.0));
            RuneKit.rockBurst(level, V.up(aim, 0.4), 10, 6, 0.28);
            SkillFx.particle(level, ParticleTypes.POOF, V.up(aim, 0.5), 40, radius * 0.4, 0.15);
            RuneKit.shakeAround(level, aim, 22, 0.55, 0.55);
            SkillFx.play(level, "random.explode", aim, 0.6, 1.0);
            SkillFx.play(level, "mace.heavy_smash_ground", aim, 0.5, 1.0);
            SkillFx.play(level, "entity.ravager.roar", aim, 0.6, 1.0);
            for (LivingEntity e : Kit.around(sp, aim, radius, 5)) {
                PairKit.hit(sp, r, e, P, k.num("crush"), DamageNumbers.Style.FERAL, life, dmg -> {
                    if (!PairKit.boss(e)) RuneKit.knock(e, V.sub(e.position(), aim), 0.4, 0.5);
                    if (RuneKit.fxQuota("beastCrush", 8)) claw(level, Kit.bodyPoint(e), 1.0);
                    // 0.49.0 사냥감 표식: 짓밟힌 적 vulnTicks 동안 받는 피해 + vuln (머리 위 금 간 표식)
                    RuneMarks.vuln(e, k.numOr("vuln", 0), (int) k.numOr("vulnTicks", 0), WILD, CORE, RuneKit.fxQuota("beastMark", 8));
                });
            }
        });
        // 끝: 야수왕이 빛으로 흩어짐
        if (king != null) {
            RuneKit.later(end, () -> {
                if (!king.alive()) return;
                Vec3 p = V.up(king.pos(), 2.2);
                SkillFx.sparks(level, p, WILD, 16, 5.0);
                SkillFx.sparks(level, p, CORE, 8, 4.0);
                SkillFx.burst(level, p, WILD, 2.4, 0.4);
                SkillFx.particle(level, ParticleTypes.GLOW, p, 30, 1.6, 0.1);
                SkillFx.play(level, "block.amethyst_block.break", p, 0.8, 1.0);
                king.leave("vanish", 10);
            });
        }
        return true;
    }
}
