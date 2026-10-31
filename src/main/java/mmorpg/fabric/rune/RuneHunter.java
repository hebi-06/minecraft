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

    /**
     * 매의 습격: 앞 range 칸 · cone 도의 가장 가까운 적에게 영혼의 매 (rpg:rfx_beast 매) 를 날림 — gap 틱마다 hits 번 하늘에서 급강하해 대상 둘레 radius 칸의 적을
     * 할큄 (위력 × mult) 하고 다시 날아오름. 대상이 쓰러지면 둘레의 다른 적에게. 대상이 없으면 쓰지 않음
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
        // 손에서 날아오름
        Vec3 hand = V.add(V.up(sp.position(), 1.6), Kit.facingOf(sp), 0.8);
        Vec3 up0 = V.up(V.add(hand, Kit.facingOf(sp), 1.5), 2.5);
        beast(level0, hand, FALCON, Opt.of("dir", V.unit(V.sub(up0, hand)), "speed", V.len(V.sub(up0, hand)) / 0.2, "size", 0.6, "life", 0.2, "fade", 0.2));
        SkillFx.play(level0, "entity.parrot.fly", sp.position(), 0.8, 1.0);
        SkillFx.play(level0, "mob.phantom.flap", sp.position(), 1.5, 0.6);
        for (int i = 0; i < hits; i++) {
            final int ii = i;
            final int at = 4 + i * gap;
            // 급강하 (4틱 전에 하늘에서)
            RuneKit.later(at - 4, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                if (cur[0] == null || cur[0].isRemoved() || !cur[0].isAlive()) {
                    List<LivingEntity> near = RuneKit.nearestTo(Kit.around(sp, last[0], 6, 3), last[0]);
                    cur[0] = near.isEmpty() ? null : near.get(0);
                }
                Vec3 tgt = cur[0] != null ? Kit.bodyPoint(cur[0]) : V.up(last[0], 0.8);
                last[0] = cur[0] != null ? cur[0].position() : last[0];
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
                        // 0.49.0 사냥감 표식: vulnTicks 동안 받는 피해 + vuln (머리 위 금 간 표식)
                        if (ii == hits - 1) RuneMarks.vuln(e, k.numOr("vuln", 0), (int) k.numOr("vulnTicks", 0), WILD, CORE, RuneKit.fxQuota("falconMark", 6)); // 마지막 급강하에
                    });
                }
                // 다시 날아오름
                double a = Kit.rand() * Math.PI * 2;
                Vec3 from = V.up(c, 1.0);
                Vec3 to = new Vec3(c.x + Math.cos(a) * 3.0, c.y + 4.5, c.z + Math.sin(a) * 3.0);
                beast(level, from, FALCON, Opt.of("dir", V.unit(V.sub(to, from)), "speed", V.len(V.sub(to, from)) / 0.3, "size", 0.75, "life", 0.3, "fade", 0.4));
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 고귀: 사냥 그물

    /** 그물 (rpg:rfx_net — dir 쪽을 보는 판, 땅에 덮을 때는 위): at 에 size, vel 로 움직임 */
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
        // 날아가는 그물 (펼쳐지며 커짐 — 포물선)
        for (int t = 0; t < fly; t++) {
            final int tt = t;
            RuneKit.later(t, () -> {
                double u = (tt + 1) / (double) fly;
                Vec3 p = PairKit.arcAt(hand, V.up(land, 0.3), 2.0, u);
                // 날아가는 동안은 시전자 쪽을 보며 펼쳐짐 (눕혀 두면 옆에서 안 보임) → 떨어지며 눕음
                Vec3 face = V.unit(V.lerp(RuneKit.towardEye(sp, p), V.UP, u * u));
                net(level, p, face, radius * (0.25 + 0.7 * u), 0.08, Vec3.ZERO, tt * 25.0, 0.9);
            });
        }
        SkillFx.play(level, "entity.fishing_bobber.throw", sp.position(), 0.6, 1.0);
        SkillFx.play(level, "mob.breeze.whirl", sp.position(), 1.4, 0.6);
        int holdEnd = fly + (hits - 1) * gap + 8;
        // 덮은 그물 (조일수록 작아짐) · 둘레의 추
        for (int t = fly; t < holdEnd; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                int step = Math.min(hits - 1, (tt - fly) / gap);
                double s = radius * (1.0 - 0.1 * step);
                net(level, land, V.UP, s, 0.14, Vec3.ZERO, 0, 0.95);
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
                net(level, V.up(land, 0.02), V.UP, s * 1.05, 0.25, Vec3.ZERO, 0, 1.0);
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

    /**
     * 늑대 무리: 앞 range 칸 · cone 도의 적 (가까운 순 8) 에게 영혼의 늑대 다섯 (rpg:rfx_beast 늑대 머리) 이 내 둘레에서 달려들어 gap 틱마다 hits 번 물어뜯음
     * (위력 × mult — 발톱 자국) → 무리가 울부짖음: 물린 적 · 첫 대상 둘레 radius 칸의 적 (위력 × crush, 밀쳐냄). 대상이 없으면 쓰지 않음
     */
    static boolean wolfPack(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        List<LivingEntity> ts = RuneKit.coneTargets(sp, k.num("range"), k.num("cone"), 8);
        if (ts.isEmpty()) return PairKit.noTarget(sp, k);
        String life = RuneKit.lifeToken(sp);
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        ServerLevel level0 = sp.level();
        Vec3 f0 = Kit.facingOf(sp);
        // 내 둘레에서 늑대 다섯이 나타남
        for (int j = 0; j < 5; j++) {
            Vec3 d = RuneKit.turn(f0, -56 + j * 28);
            Vec3 p = V.up(V.add(sp.position(), d, 2.7), 0.8);
            beast(level0, p, WOLF, Opt.of("size", 0.4, "life", 0.3, "grow", 0.4, "fade", 0.5));
            SkillFx.particle(level0, ParticleTypes.POOF, p, 2, 0.15, 0.02);
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
                    Vec3 side = V.rightOf(V.flat(V.sub(tgt, c)));
                    Vec3 from = V.up(V.add(V.lerp(c, e.position(), 0.35), side, (j - 2) * 0.9), 0.8);
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
        // 울부짖음
        RuneKit.later(hits * gap + 2, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            List<LivingEntity> alive = PairKit.alive(new ArrayList<>(bitten.values()));
            Vec3 c = alive.isEmpty() ? ts.get(0).position() : alive.get(0).position();
            double radius = k.num("radius");
            double sc = RuneKit.nearK(sp, V.up(c, 2.5), 6, 0.5);
            beast(level, V.up(c, 2.6 * sc), WOLF, Opt.of("size", 1.6 * sc, "life", 0.6, "grow", 0.4, "fade", 0.5));
            for (int j = 0; j < 3; j++) {
                final int jj = j;
                RuneKit.later(j * 2, () -> SkillFx.fx(level, "rpg:shock_ring", V.up(c, 0.1 + jj * 0.6), Opt.of("dir", V.ZERO, "size", radius * (0.4 + jj * 0.15),
                        "life", 0.45, "color", jj % 2 == 0 ? WILD : CORE, "alpha", 0.9)));
            }
            RuneKit.zonePulse(level, c, WILD, radius, 0.5);
            RuneKit.shakeAround(level, c, 10, 0.25, 0.35);
            SkillFx.play(level, "entity.ravager.roar", c, 1.3, 0.8);
            SkillFx.play(level, "entity.polar_bear.warning", c, 0.8, 1.0);
            Map<UUID, LivingEntity> got = new LinkedHashMap<>();
            for (LivingEntity e : alive) got.put(e.getUUID(), e);
            for (LivingEntity e : Kit.around(sp, c, radius, 3)) got.putIfAbsent(e.getUUID(), e);
            for (LivingEntity e : got.values()) {
                PairKit.hit(sp, r, e, P, k.num("crush"), DamageNumbers.Style.FERAL, life, dmg -> {
                    claw(level, Kit.bodyPoint(e), 0.9);
                    if (!PairKit.boss(e)) RuneKit.knock(e, V.sub(e.position(), c), 0.45, 0.3);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 초월: 야수왕 강림

    /**
     * 야수왕 강림: 조준한 곳 (range 칸) 에 짙은 숲빛 하늘 (3돌부터) · 연둣빛 영역 문양 · 빛기둥 → 하늘에서 거대한 영혼의 늑대왕 (rpg:rfx_beast 늑대 머리 —
     * 눈부신 연둣빛 무리) 이 delay 틱 동안 내려옴 → gap 틱마다 hits 번 거대한 발톱이 반지름 radius 칸을 할큄 (위력 × mult) → 늑대왕이 땅을 짓밟음
     * (위력 × crush — 거대한 발자국 충격 · 갈라짐, 띄움). 대상이 없어도 씀
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
        // 늑대왕: 하늘 높이에서 내려와 (delay) 할퀴는 동안 떠 있음 · 숨결 (2틱마다 다시 그림)
        // 늑대왕은 조준한 곳 너머 위 (1인칭에서도 보이게 — 눈에서 20° 남짓)
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
                if (tt % 6 == 0) RuneKit.skyFlare(level, V.add(at, f, 0.4), s * 0.7, WILD, CORE, Opt.of("life", 0.3, "alpha", 0.6));
                if (tt % 4 == 0) SkillFx.particle(level, ParticleTypes.GLOW, at, 6, s * 0.4, 0.02);
            });
        }
        RuneKit.later(delay, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            SkillFx.play(level, "entity.ravager.roar", aim, 0.5, 1.0);
            SkillFx.play(level, "entity.polar_bear.warning", aim, 0.6, 1.0);
            RuneKit.shakeAround(level, aim, 18, 0.3, 0.5);
        });
        // 거대한 발톱 (반지름 안을 가로지르는 발톱 자국 · 할퀸 적)
        for (int i = 0; i < hits; i++) {
            final int ii = i;
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
        // 짓밟기: 늑대왕이 내리꽂힘 → 거대한 발자국 충격
        RuneKit.later(crushAt - 4, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 d = V.sub(V.up(aim, 1.5), hover);
            beast(level, hover, WOLF, Opt.of("dir", V.unit(d), "speed", V.len(d) / 0.2, "size", 4.6, "life", 0.2, "grow", 0.2, "fade", 0.1));
            SkillFx.play(level, "mob.enderdragon.flap", hover, 0.5, 1.0);
        });
        RuneKit.later(crushAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            RuneKit.ultFlashFor(sp, level, aim, "beastKing", "flash", Opt.empty());
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
        return true;
    }
}
