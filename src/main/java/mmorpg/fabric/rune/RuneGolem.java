package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.FxDisplays;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 0.25.0 골렘 룬 (golem — 체력 1.0 + 마력 0.5, 두 스탯 계열): 룬을 새긴 돌 · 바위 골렘. 돌 · 골렘은 블록 표시 (FxDisplays — 3D, 충돌 · 피해 없음) 로 짜고
 * 빛나는 비취빛 룬 (초록 개구리불) 을 박음. 마무리 숫자는 굵은 돌빛 ! (RUBBLE).
 * 바위 주먹 (겨눈 적 발밑에서 돌 주먹이 솟아 올려침) · 돌의 수호 (내 둘레 돌기둥 넷이 솟을 때 · 무너질 때 치고 보호막) · 석화 결계 (룬 결계 → 돌로 굳힘 →
 * 깨뜨림) · 골렘 소환 (돌 골렘이 땅에서 솟아 두 주먹으로 세 번 내려치고 무너짐) · 거신 강림 (땅이 갈라지며 거대한 거신이 솟아 주먹을 퍼붓고 두 주먹으로
 * 대지를 내려침 — 이끼 낀 돌빛 하늘).
 */
final class RuneGolem {
    private RuneGolem() {}

    private static final double[][] C = RuneKit.colorOf("golem");
    /** 비취빛 룬 · 흰 심지 · 돌먼지 */
    static final double[] JADE = C[0];
    static final double[] CORE = C[1];
    static final double[] DUST = {0.62, 0.6, 0.55};

    static final BlockState STONE = Blocks.STONE.defaultBlockState();
    static final BlockState BRICKS = Blocks.STONE_BRICKS.defaultBlockState();
    static final BlockState MOSSY = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
    static final BlockState CRACKED = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    static final BlockState CHISELED = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
    static final BlockState COBBLE = Blocks.COBBLESTONE.defaultBlockState();
    static final BlockState MOSSY_COBBLE = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
    static final BlockState DEEP = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    static final BlockState DEEP_TILES = Blocks.DEEPSLATE_TILES.defaultBlockState();
    static final BlockState POLISHED = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    /** 빛나는 비취빛 룬 (초록 개구리불) */
    static final BlockState RUNE = Blocks.VERDANT_FROGLIGHT.defaultBlockState();

    static void register() {
        RuneCast.register("stoneFist", RuneGolem::stoneFist);
        RuneCast.register("stoneWard", RuneGolem::stoneWard);
        RuneCast.register("petrify", RuneGolem::petrify);
        RuneCast.register("golemSummon", RuneGolem::golemSummon);
        RuneCast.register("colossus", RuneGolem::colossus);
    }

    // ------------------------------------------------------------ 공용: 돌 부스러기 · 골렘 모형

    /** 돌 부스러기 (그 자리 블록 깨짐 — 돌) + 돌먼지 */
    static void debris(ServerLevel level, Vec3 at, int n, double spread, BlockState st) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), at.x, at.y, at.z, n, spread, spread * 0.4, spread, 0.15);
        level.sendParticles(ParticleTypes.DUST_PLUME, at.x, at.y, at.z, Math.max(2, n / 4), spread * 0.6, 0.1, spread * 0.6, 0.02);
    }

    /** 땅을 내려친 자리: 갈라짐 · 바위 가시 · 튀는 돌 · 돌먼지 기둥 · 비취빛 고리 */
    static void slamFx(ServerPlayer sp, ServerLevel level, Vec3 at, double radius, double k) {
        RuneKit.glowCrack(level, at, radius * 0.85, JADE, CORE, Opt.of("life", 1.4, "v", 1.0));
        SkillFx.fx(level, "rpg:ground_crack", V.up(at, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", radius * 1.1, "life", 1.4, "color",
                new double[] {0.22, 0.2, 0.18}, "alpha", 0.9));
        RuneKit.rockRing(sp, level, at, radius * 0.7, 6, 0.55 * k, JADE, Opt.of("life", 0.9, "minDist", 1.8));
        RuneKit.rockBurst(level, V.up(at, 0.4), 6, 5, 0.26 * k);
        RuneKit.zonePulse(level, at, JADE, radius, 0.45);
        level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, STONE), at.x, at.y + 0.1, at.z, 24, radius * 0.35, 0.1, radius * 0.35, 0.3);
        debris(level, V.up(at, 0.3), 30, radius * 0.3, COBBLE);
    }

    /** 골렘 팔 무리 번호 (모형을 만들 때 왼쪽 · 오른쪽 순서로 group) */
    static final int ARM_L = 1;
    static final int ARM_R = 2;

    /**
     * 돌 골렘 모형 (scale 1 = 키 4.4칸, 앞 = +z — 모형 x + 가 골렘의 왼쪽): 다리 둘 · 허리 · 몸통 (가슴에 빛나는 룬) · 머리 (빛나는 눈) · 어깨 바위 ·
     * 팔 둘 (어깨 축으로 도는 무리 — 위팔 · 아래팔 · 주먹 · 주먹의 룬). 처음 자세 = hidden (땅속) · rot · scale
     */
    static PairKit.Rig golem(ServerLevel level, Vec3 base, int life, Vec3 hidden, Quaternionf rot, double scale) {
        PairKit.Rig g = new PairKit.Rig(level, base, life);
        g.offset = hidden;
        g.rot = rot;
        g.scale = scale;
        g.group(new Vec3(1.45, 3.2, 0));
        g.group(new Vec3(-1.45, 3.2, 0));
        g.add(DEEP, new Vec3(0.62, 0.7, 0), 0.95, 1.4, 1.0, PairKit.LIT);
        g.add(DEEP, new Vec3(-0.62, 0.7, 0), 0.95, 1.4, 1.0, PairKit.LIT);
        g.add(BRICKS, new Vec3(0, 1.6, 0), 2.0, 0.5, 1.1, PairKit.LIT);
        g.add(MOSSY, new Vec3(0, 2.65, 0), 2.5, 1.65, 1.45, PairKit.LIT);
        g.add(RUNE, new Vec3(0, 2.75, 0.73), 0.7, 0.7, 0.12, PairKit.GLOW);
        g.add(CHISELED, new Vec3(0, 3.88, 0.15), 1.05, 0.85, 1.0, PairKit.LIT);
        g.add(RUNE, new Vec3(0.25, 3.92, 0.66), 0.24, 0.12, 0.06, PairKit.GLOW);
        g.add(RUNE, new Vec3(-0.25, 3.92, 0.66), 0.24, 0.12, 0.06, PairKit.GLOW);
        g.add(MOSSY_COBBLE, new Vec3(1.45, 3.3, 0), 0.95, 0.85, 0.95, PairKit.LIT);
        g.add(MOSSY_COBBLE, new Vec3(-1.45, 3.3, 0), 0.95, 0.85, 0.95, PairKit.LIT);
        for (int side = 1; side >= -1; side -= 2) {
            int grp = side > 0 ? ARM_L : ARM_R;
            double x = 1.55 * side;
            g.add(grp, BRICKS, new Vec3(x, 2.45, 0), new Vec3(0.72, 1.3, 0.72), null, PairKit.LIT);
            g.add(grp, COBBLE, new Vec3(x * 1.03, 1.35, 0.05), new Vec3(0.85, 1.1, 0.85), null, PairKit.LIT);
            g.add(grp, DEEP_TILES, new Vec3(x * 1.05, 0.45, 0.1), new Vec3(1.15, 0.9, 1.15), null, PairKit.LIT);
            g.add(grp, RUNE, new Vec3(x * 1.05, 0.55, 0.69), new Vec3(0.9, 0.14, 0.06), null, PairKit.GLOW);
        }
        return g;
    }

    /** 골렘 자세: 앞으로 lean 도 기울고, 팔은 어깨 축으로 armL · armR 도 (- = 앞으로 듦, -90 = 앞으로 뻗음, -165 = 머리 위) */
    static void golemPose(PairKit.Rig g, Vec3 off, Quaternionf face, double scale, double lean, double armL, double armR, int ticks) {
        Vec3 x = new Vec3(1, 0, 0);
        g.pose(off, new Quaternionf(face).mul(PairKit.about(x, lean)), scale, ticks, new Quaternionf(), PairKit.about(x, armL), PairKit.about(x, armR));
    }

    // ------------------------------------------------------------ 일반: 바위 주먹

    /**
     * 바위 주먹: 앞 range 칸 · cone 도의 가장 가까운 적 발밑에 비취빛 룬 · 갈라짐 → 땅에서 돌 주먹 (블록 표시 — 아래팔 · 비취빛 룬 띠 · 주먹 · 마디) 이 솟구쳐
     * 둘레 radius 칸의 적을 올려침 (위력 × mult, 띄움 lift — 보스 제외) → 주먹이 부서져 무너짐. 대상이 없으면 쓰지 않음
     */
    static boolean stoneFist(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        List<LivingEntity> ts = RuneKit.coneTargets(sp, k.num("range"), k.num("cone"), 1);
        if (ts.isEmpty()) return PairKit.noTarget(sp, k);
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        LivingEntity t0 = ts.get(0);
        Vec3 g = PairKit.onGround(level, t0.position());
        Vec3 f = V.flat(V.sub(g, sp.position()));
        double radius = k.num("radius");
        SkillFx.rune(level, g, Opt.of("size", radius, "life", 0.5, "color", JADE, "alpha", 0.9, "rate", 220.0, "progress", 1.0));
        RuneKit.glowCrack(level, g, radius * 0.9, JADE, CORE, Opt.of("life", 1.3, "v", 1.0));
        debris(level, V.up(g, 0.1), 16, radius * 0.3, COBBLE);
        SkillFx.play(level, "block.deepslate.break", g, 0.7, 1.0);
        SkillFx.play(level, "mob.evocation_illager.cast_spell", sp.position(), 1.4, 0.5);
        Quaternionf face = PairKit.facing(f);
        PairKit.Rig fist = new PairKit.Rig(level, g, 30);
        fist.offset = new Vec3(0, -3.6, 0);
        fist.rot = face;
        fist.add(BRICKS, new Vec3(0, 1.05, 0), 0.95, 2.1, 0.95, PairKit.LIT);
        fist.add(RUNE, new Vec3(0, 1.85, 0), 1.06, 0.22, 1.06, PairKit.GLOW);
        fist.add(MOSSY, new Vec3(0, 2.6, 0.06), 1.4, 1.05, 1.3, PairKit.LIT);
        fist.add(COBBLE, new Vec3(0.62, 2.4, 0.42), 0.36, 0.62, 0.4, PairKit.LIT);
        for (int i = 0; i < 4; i++) fist.add(i % 2 == 0 ? COBBLE : CRACKED, new Vec3(-0.48 + i * 0.32, 3.15, 0.32), 0.28, 0.24, 0.42, PairKit.LIT);
        RuneKit.later(4, () -> fist.pose(new Vec3(0, -0.3, 0), new Quaternionf(face).mul(PairKit.about(new Vec3(1, 0, 0), 10)), 1.0, 3));
        RuneKit.later(7, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            RuneKit.rockBurst(level, V.up(g, 0.5), 6, 6, 0.24);
            SkillFx.burst(level, V.up(g, 1.6), JADE, 1.6, 0.3);
            RuneKit.zonePulse(level, g, JADE, radius, 0.4);
            level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, STONE), g.x, g.y + 0.1, g.z, 16, 0.5, 0.1, 0.5, 0.3);
            SkillFx.play(level, "entity.iron_golem.attack", g, 0.7, 1.0);
            SkillFx.play(level, "mace.smash_ground", g, 0.9, 0.8);
            for (LivingEntity e : Kit.around(sp, g, radius, 3)) {
                PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                    if (!PairKit.boss(e)) Kit.knockback(e, f.x * 0.15, f.z * 0.15, k.num("lift"));
                    SkillFx.fx(level, "rpg:slash_x", Kit.bodyPoint(e), Opt.of("dir", V.UP, "size", 1.0, "life", 0.22, "color", JADE, "alpha", 1.0));
                });
            }
        });
        RuneKit.later(17, () -> {
            fist.crumble(6, 0.2, 2.2, 0.55);
            debris(level, V.up(g, 1.4), 24, 0.6, COBBLE);
            SkillFx.play(level, "block.stone.break", g, 0.7, 1.0);
        });
        return true;
    }

    // ------------------------------------------------------------ 희귀: 돌의 수호

    /**
     * 돌의 수호: 몸에 보호막 (최대 체력 × shield × 스탯 비례, ticks 틱 — 비취빛 방패 문장 · 둘레 육각 판) + 내 둘레 대각선 넷에 룬 돌기둥 (블록 표시) 이 솟으며
     * 둘레 radius 칸의 적을 침 (위력 × mult) → gap 틱 뒤 돌기둥이 바깥으로 쓰러지며 한 번 더 (위력 × mult) · 무너짐
     */
    static boolean stoneWard(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double radius = k.num("radius");
        int gap = (int) k.num("gap");
        double scale = Runes.supportScale(RpgStats.effective(sp), r.family());
        double amt = RpgHealth.maxOf(sp) * k.num("shield") * scale;
        RpgHealth.addShield(sp, amt, (int) k.num("ticks"), amt);
        RuneKit.shieldEmblem(sp, level, V.add(V.up(sp.getEyePosition(), 0.3), f, 2.2), 0.85, JADE, Opt.of("full", 3.0, "life", 0.7, "grow", 0.3, "core", CORE));
        for (int j = 0; j < 6; j++) {
            Vec3 d = RuneKit.turn(f, j * 60 + 30);
            RuneKit.hexPlate(level, V.up(V.add(c, d, 1.15), 1.0), d, 0.5, JADE, Opt.of("life", 0.7, "grow", 0.25, "core", CORE, "alpha", 0.8));
        }
        SkillFx.play(level, "armor.equip_iron", c, 0.7, 1.0);
        SkillFx.play(level, "block.deepslate.break", c, 0.6, 1.0);
        // 돌기둥 넷 (대각선)
        List<PairKit.Rig> pillars = new ArrayList<>();
        List<Vec3> outs = new ArrayList<>();
        int pillarLife = 4 + gap + 16;
        for (int j = 0; j < 4; j++) {
            Vec3 out = RuneKit.turn(f, 45 + j * 90);
            Vec3 p = PairKit.onGround(level, V.add(c, out, 2.7));
            PairKit.Rig pl = new PairKit.Rig(level, p, pillarLife);
            pl.offset = new Vec3(0, -3.4, 0);
            pl.rot = PairKit.facing(out);
            pl.add(MOSSY_COBBLE, new Vec3(0, 0.2, 0), 1.25, 0.4, 1.25, PairKit.LIT);
            pl.add(DEEP, new Vec3(0, 1.55, 0), 0.95, 2.9, 0.95, PairKit.LIT);
            pl.add(RUNE, new Vec3(0, 2.25, 0), 1.05, 0.22, 1.05, PairKit.GLOW);
            pl.add(RUNE, new Vec3(0, 1.2, 0.48), 0.4, 0.4, 0.06, PairKit.GLOW);
            pl.add(POLISHED, new Vec3(0, 3.1, 0), 1.12, 0.32, 1.12, PairKit.LIT);
            pillars.add(pl);
            outs.add(out);
            RuneKit.glowCrack(level, p, 1.3, JADE, CORE, Opt.of("life", 1.0));
        }
        RuneKit.later(2, () -> {
            for (int j = 0; j < pillars.size(); j++) pillars.get(j).pose(Vec3.ZERO, PairKit.facing(outs.get(j)), 1.0, 2);
        });
        // 솟을 때
        RuneKit.later(4, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 cc = sp.position();
            for (PairKit.Rig pl : pillars) debris(level, V.up(pl.base, 0.3), 14, 0.5, COBBLE);
            RuneKit.zonePulse(level, cc, JADE, radius, 0.45);
            RuneKit.rockRing(sp, level, cc, radius * 0.85, 7, 0.5, JADE, Opt.of("life", 0.8, "minDist", 2.0));
            SkillFx.play(level, "entity.iron_golem.step", cc, 0.6, 1.0);
            SkillFx.play(level, "mace.smash_ground", cc, 0.8, 0.8);
            for (LivingEntity e : Kit.around(sp, cc, radius, 3)) {
                PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                    if (!PairKit.boss(e)) RuneKit.knock(e, V.sub(e.position(), cc), 0.35, 0.25);
                    debris(level, Kit.bodyPoint(e), 8, 0.3, COBBLE);
                });
            }
        });
        // 쓰러짐 (바깥으로)
        RuneKit.later(gap + 1, () -> {
            for (int j = 0; j < pillars.size(); j++) {
                Vec3 out = outs.get(j);
                pillars.get(j).pose(Vec3.ZERO, new Quaternionf(PairKit.about(V.cross(V.UP, out), 82)).mul(PairKit.facing(out)), 1.0, 3);
            }
            SkillFx.play(level, "block.deepslate_bricks.break", c, 0.6, 1.0);
        });
        RuneKit.later(gap + 4, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 cc = sp.position();
            for (int j = 0; j < pillars.size(); j++) {
                Vec3 tip = PairKit.onGround(level, V.add(pillars.get(j).base, outs.get(j), 2.6));
                debris(level, V.up(tip, 0.3), 20, 0.6, CRACKED);
                RuneKit.rockBurst(level, V.up(tip, 0.3), 4, 4, 0.22);
            }
            RuneKit.zonePulse(level, cc, CORE, radius, 0.45);
            RuneKit.shakeAround(level, cc, 8, 0.2, 0.25);
            SkillFx.play(level, "random.explode", cc, 1.2, 0.4);
            SkillFx.play(level, "block.stone.break", cc, 0.6, 1.0);
            for (LivingEntity e : Kit.around(sp, cc, radius, 3)) {
                PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                    if (!PairKit.boss(e)) RuneKit.knock(e, V.sub(e.position(), cc), 0.25, 0.2);
                    debris(level, Kit.bodyPoint(e), 8, 0.3, CRACKED);
                });
            }
        });
        RuneKit.later(gap + 7, () -> {
            for (PairKit.Rig pl : pillars) pl.crumble(6, 0.1, 1.2, 0.5);
        });
        return true;
    }

    // ------------------------------------------------------------ 고귀: 석화 결계

    /** 돌 껍데기 한 면 (표시 · 가운데 · 크기 · 깨질 때 튀는 쪽) */
    private record Wall(Display.BlockDisplay d, Vec3 c, Vec3 size, Vec3 out) {}

    /** 적을 돌 껍데기 (네 벽 + 뚜껑) 에 가둠 — 발밑에서부터 자라 오름 (5틱) */
    private static List<Wall> encase(ServerLevel level, LivingEntity e, int life) {
        List<Wall> out = new ArrayList<>();
        double w = e.getBbWidth() + 0.4;
        double h = e.getBbHeight() + 0.2;
        double th = 0.26;
        Vec3 base = e.position();
        Object[][] walls = {
            {new Vec3(w / 2 - th / 2, h / 2, 0), new Vec3(th, h, w), new Vec3(1, 0, 0)}, {new Vec3(-w / 2 + th / 2, h / 2, 0), new Vec3(th, h, w), new Vec3(-1, 0, 0)},
            {new Vec3(0, h / 2, w / 2 - th / 2), new Vec3(w - th * 2, h, th), new Vec3(0, 0, 1)},
            {new Vec3(0, h / 2, -w / 2 + th / 2), new Vec3(w - th * 2, h, th), new Vec3(0, 0, -1)}, {new Vec3(0, h + th / 2, 0), new Vec3(w, th, w), V.UP}
        };
        for (int i = 0; i < walls.length; i++) {
            Vec3 c = (Vec3) walls[i][0];
            Vec3 s = (Vec3) walls[i][1];
            Vec3 o = (Vec3) walls[i][2];
            BlockState st = i == 4 ? MOSSY_COBBLE : i % 2 == 0 ? STONE : COBBLE;
            Display.BlockDisplay d = FxDisplays.block(level, base, st, PairKit.box(new Vec3(c.x, 0.05, c.z), new Quaternionf(), s.x, 0.1, s.z), life, PairKit.LIT);
            if (d == null) continue;
            out.add(new Wall(d, c, s, o));
            RuneKit.later(2, () -> FxDisplays.move(d, PairKit.box(c, new Quaternionf(), s.x, s.y, s.z), 5));
        }
        return out;
    }

    /**
     * 석화 결계: 조준한 곳 (range 칸) 에 반지름 radius 칸의 룬 결계 (비취빛 영역 문양 · 둘레의 육각 벽) — 펼쳐질 때 안의 적 (위력 × mult) → 돌먼지가 발밑에서
     * 차오르고 delay 틱 뒤 안의 적이 돌 껍데기에 갇혀 굳음 (stun 틱 기절 — 보스는 굳지 않고 느려짐만) → 12틱 뒤 껍데기가 깨지며 한 번 더 (위력 × crush)
     */
    static boolean petrify(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 g = RuneKit.groundAim(sp, k.num("range"));
        double radius = k.num("radius");
        int delay = (int) k.num("delay");
        int stun = (int) k.num("stun");
        int breakAt = delay + 12;
        RuneKit.domainSigil(level, g, radius, "arcane", JADE, CORE, Opt.of("life", (breakAt + 4) / 20.0, "spinRate", 40.0, "grow", 0.1, "alpha", 0.85, "fade", 0.2));
        SkillFx.play(level, "mob.evocation_illager.prepare_attack", g, 0.7, 0.9);
        SkillFx.play(level, "beacon.activate", g, 1.4, 0.6);
        // 결계 벽 (둘레의 육각 판 — 안쪽을 봄)
        for (int t = 0; t < breakAt; t += 8) {
            final int tt = t;
            RuneKit.later(t, () -> {
                for (int j = 0; j < 10; j++) {
                    double a = j / 10.0 * Math.PI * 2 + tt * 0.02;
                    Vec3 p = PairKit.ring(g, radius, a);
                    if (PairKit.nearCam(sp, V.up(p, 1.2), 2.4)) continue;
                    Vec3 in = new Vec3(-Math.cos(a), 0, -Math.sin(a));
                    RuneKit.hexPlate(level, V.up(p, 1.0), in, 0.55, JADE, Opt.of("life", 0.45, "fade", 0.3, "core", CORE, "alpha", 0.75, "coreAlpha", 0.6));
                    if (j % 2 == 0) RuneKit.hexPlate(level, V.up(p, 2.0), in, 0.45, JADE, Opt.of("life", 0.45, "fade", 0.3, "core", CORE, "alpha", 0.6));
                }
            });
        }
        // 펼쳐질 때
        Map<UUID, LivingEntity> inside = new LinkedHashMap<>();
        RuneKit.zonePulse(level, g, JADE, radius, 0.5);
        RuneKit.novaRing(level, g, 0.8, radius * 0.8, JADE, CORE, Opt.of("life", 0.45));
        for (LivingEntity e : Kit.around(sp, g, radius, 3)) {
            inside.put(e.getUUID(), e);
            PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> SkillFx.fx(level, "rpg:slash_x", Kit.bodyPoint(e), Opt.of("dir", V.UP, "size", 0.9, "life",
                    0.22, "color", JADE, "alpha", 1.0)));
        }
        // 돌먼지가 차오름
        for (int t = 2; t < delay; t += 4) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (LivingEntity e : Kit.around(sp, g, radius, 3)) {
                    if (!RuneKit.fxQuota("petrifyDust", 8)) break;
                    Vec3 p = e.position();
                    level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, STONE), p.x, p.y + e.getBbHeight() * (tt / (double) delay), p.z, 6,
                            e.getBbWidth() * 0.4, 0.1, e.getBbWidth() * 0.4, 0);
                    SkillFx.fx(level, "rpg:arrow_spark", V.up(p, 0.2), Opt.of("dir", V.UP, "speed", 1.2, "size", 0.12, "life", 0.5, "color", JADE, "alpha", 0.9));
                }
            });
        }
        // 굳음
        Map<UUID, List<Wall>> shells = new LinkedHashMap<>();
        RuneKit.later(delay, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            RuneKit.domainSigil(level, g, radius * 0.9, "arcane", CORE, JADE, Opt.of("life", 0.5, "grow", 0.2, "alpha", 0.95, "fade", 0.6));
            SkillFx.play(level, "block.deepslate.break", g, 0.5, 1.0);
            SkillFx.play(level, "mob.irongolem.crack", g, 0.6, 0.8);
            for (LivingEntity e : Kit.around(sp, g, radius, 3)) {
                inside.putIfAbsent(e.getUUID(), e);
                if (PairKit.boss(e)) {
                    RuneKit.effect(e, "slowness", stun, 2);
                    continue;
                }
                RuneKit.stun(e, stun, JADE, true);
                if (shells.size() < 8) shells.put(e.getUUID(), encase(level, e, breakAt - delay + 8));
                debris(level, V.up(e.position(), 0.3), 10, 0.4, STONE);
            }
        });
        // 깨뜨림
        RuneKit.later(breakAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            for (List<Wall> ws : shells.values()) {
                for (Wall w : ws) {
                    Vec3 c2 = V.add(V.up(w.c(), -0.3), w.out(), 0.9);
                    FxDisplays.move(w.d(), PairKit.box(c2, PairKit.about(V.unit(new Vec3(w.out().z + 0.3, 0.2, -w.out().x + 0.2)), 40), w.size().x * 0.5,
                            w.size().y * 0.5, w.size().z * 0.5), 4);
                }
            }
            RuneKit.zonePulse(level, g, CORE, radius, 0.45);
            RuneKit.glowCrack(level, g, radius * 0.8, JADE, CORE, Opt.of("life", 1.0, "v", 1.0));
            RuneKit.shakeAround(level, g, 10, 0.25, 0.3);
            SkillFx.play(level, "block.stone.break", g, 0.5, 1.0);
            SkillFx.play(level, "random.glass", g, 0.6, 0.8);
            SkillFx.play(level, "random.explode", g, 1.3, 0.4);
            for (LivingEntity e : Kit.around(sp, g, radius + 1.5, 3)) inside.putIfAbsent(e.getUUID(), e);
            for (LivingEntity e : inside.values()) {
                if (e.isRemoved() || (!shells.containsKey(e.getUUID()) && e.position().distanceTo(g) > radius + 2.5)) continue;
                PairKit.hit(sp, r, e, P, k.num("crush"), DamageNumbers.Style.RUBBLE, life, dmg -> {
                    debris(level, Kit.bodyPoint(e), 18, 0.4, STONE);
                    SkillFx.burst(level, Kit.bodyPoint(e), JADE, 1.2, 0.3);
                    if (!PairKit.boss(e)) RuneKit.knock(e, V.sub(e.position(), g), 0.3, 0.2);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 영웅: 골렘 소환

    /**
     * 골렘 소환: 조준한 곳 (range 칸) 옆에 비취빛 룬 · 갈라짐 → 돌 골렘 (블록 표시 — 키 4.4칸, 가슴 · 눈 · 주먹에 빛나는 룬) 이 delay 틱 동안 땅에서 솟음 →
     * gap 틱마다 hits 번 두 주먹을 들었다 내려쳐 둘레 radius 칸의 적 (위력 × mult) → 골렘이 무너지며 한 번 더 (위력 × crush)
     */
    static boolean golemSummon(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 aim = RuneKit.groundAim(sp, k.num("range"));
        Vec3 f = V.flat(V.sub(aim, sp.position()));
        Vec3 side = V.rightOf(f);
        // 골렘은 조준한 곳 옆 (시전자에게 옆모습) 에서 조준한 곳을 봄
        Vec3 c = PairKit.onGround(level, V.add(V.add(aim, side, 1.7), f, -0.3));
        Vec3 face = V.flat(V.sub(aim, c));
        Quaternionf q = PairKit.facing(face);
        double radius = k.num("radius");
        int delay = (int) k.num("delay");
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int crushAt = delay + (hits - 1) * gap + 14;
        int bodyLife = crushAt + 14;
        SkillFx.rune(level, c, Opt.of("size", 2.4, "life", (delay + 4) / 20.0, "color", JADE, "alpha", 0.9, "rate", 90.0, "progress", 1.0));
        RuneKit.glowCrack(level, c, 2.6, JADE, CORE, Opt.of("life", (delay + 10) / 20.0, "v", 1.0));
        SkillFx.play(level, "entity.warden.dig", c, 0.9, 1.0);
        SkillFx.play(level, "mob.evocation_illager.prepare_summon", sp.position(), 1.0, 0.7);
        PairKit.Rig g = golem(level, c, bodyLife, new Vec3(0, -4.8, 0), q, 1.0);
        RuneKit.later(2, () -> golemPose(g, Vec3.ZERO, q, 1.0, -8, -20, -20, Math.max(3, delay - 10)));
        for (int t = 0; t < delay; t += 3) {
            RuneKit.later(t, () -> {
                debris(level, V.up(c, 0.2), 10, 1.0, COBBLE);
                RuneKit.rockBurst(level, V.up(c, 0.3), 2, 4, 0.2);
            });
        }
        RuneKit.later(delay, () -> {
            SkillFx.play(level, "entity.iron_golem.step", c, 0.5, 1.0);
            SkillFx.play(level, "mob.irongolem.repair", c, 0.6, 0.8);
            RuneKit.shakeAround(level, c, 10, 0.2, 0.3);
        });
        for (int i = 0; i < hits; i++) {
            final int hitAt = delay + i * gap;
            // 두 주먹을 머리 위로 → 내려침
            RuneKit.later(hitAt - 8, () -> golemPose(g, Vec3.ZERO, q, 1.0, -6, -160, -160, 5));
            RuneKit.later(hitAt - 2, () -> golemPose(g, Vec3.ZERO, q, 1.0, 18, -58, -58, 2));
            RuneKit.later(hitAt + 5, () -> golemPose(g, Vec3.ZERO, q, 1.0, 0, -12, -12, 5));
            RuneKit.later(hitAt, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 slam = PairKit.onGround(level, V.add(c, face, 2.3));
                slamFx(sp, level, slam, radius, 1.0);
                RuneKit.shakeAround(level, slam, 12, 0.3, 0.3);
                SkillFx.play(level, "entity.iron_golem.attack", slam, 0.6, 1.0);
                SkillFx.play(level, "mace.heavy_smash_ground", slam, 0.9, 0.9);
                for (LivingEntity e : Kit.around(sp, c, radius, 3)) {
                    PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                        if (!PairKit.boss(e)) Kit.knockback(e, 0, 0, 0.35);
                        debris(level, Kit.bodyPoint(e), 8, 0.3, COBBLE);
                    });
                }
            });
        }
        // 무너짐
        RuneKit.later(crushAt - 4, () -> golemPose(g, Vec3.ZERO, q, 1.0, 12, -40, -40, 3));
        RuneKit.later(crushAt, () -> {
            if (!RuneKit.sameLife(sp, life)) {
                g.remove();
                return;
            }
            g.crumble(7, 0.45, 3.5, 0.6);
            slamFx(sp, level, c, radius, 1.1);
            debris(level, V.up(c, 2.0), 40, 1.2, MOSSY_COBBLE);
            RuneKit.shakeAround(level, c, 12, 0.35, 0.4);
            SkillFx.play(level, "block.deepslate_bricks.break", c, 0.5, 1.0);
            SkillFx.play(level, "mob.irongolem.crack", c, 0.5, 1.0);
            SkillFx.play(level, "random.explode", c, 0.8, 0.6);
            for (LivingEntity e : Kit.around(sp, c, radius, 3)) {
                PairKit.hit(sp, r, e, P, k.num("crush"), DamageNumbers.Style.RUBBLE, life, dmg -> {
                    if (!PairKit.boss(e)) RuneKit.knock(e, V.sub(e.position(), c), 0.4, 0.3);
                    debris(level, Kit.bodyPoint(e), 12, 0.35, CRACKED);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 초월: 거신 강림

    /** 거신 크기 (골렘 모형의 배) */
    private static final double TITAN = 1.8;

    /**
     * 거신 강림: 조준한 곳 (range 칸) 에 이끼 낀 돌빛 하늘 (3돌부터) · 비취빛 영역 문양 · 땅이 크게 갈라짐 → 조준한 곳 너머에서 거대한 룬 거신 (골렘 모형 ×1.8 — 키 8칸)
     * 이 delay 틱 동안 땅을 뚫고 솟아 이쪽을 봄 → gap 틱마다 hits 번 왼 · 오른 주먹을 번갈아 내리꽂아 반지름 radius 칸의 적 (위력 × mult) → 두 주먹을 머리 위로
     * 들어 대지를 내려침 (위력 × crush — 거대한 갈라짐 · 바위 가시 고리 · 돌먼지, 띄움) → 거신이 무너져 가라앉음. 대상이 없어도 씀
     */
    static boolean colossus(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 aim = RuneKit.groundAim(sp, k.num("range"));
        Vec3 f = V.flat(V.sub(aim, sp.position()));
        double radius = k.num("radius");
        int delay = (int) k.num("delay");
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int lastHit = delay + (hits - 1) * gap;
        int crushAt = lastHit + 16;
        int end = crushAt + 16;
        Vec3 c = PairKit.onGround(level, V.add(aim, f, 4.2));
        Vec3 face = V.scale(f, -1);
        Quaternionf q = PairKit.facing(face);
        RuneKit.ultSkyFor(sp, level, aim, "colossus", end + 30, null);
        RuneKit.ultFlashFor(sp, level, aim, "colossus", "flash", Opt.empty());
        RuneKit.domainSigil(level, aim, radius, "arcane", JADE, CORE, Opt.of("life", end / 20.0, "spinRate", 18.0, "grow", 0.15, "alpha", 0.8, "fade", 0.15));
        RuneKit.selfCue(sp, JADE, CORE, true);
        SkillFx.play(level, "mob.warden.emerge", c, 0.6, 1.0);
        SkillFx.play(level, "entity.warden.dig", aim, 0.6, 1.0);
        SkillFx.play(level, "mob.wither.spawn", sp.position(), 1.6, 0.4);
        // 땅이 갈라지고 거신이 솟음
        for (int t = 0; t < delay; t += 3) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                RuneKit.shakeAround(level, c, 18, 0.12 + 0.2 * tt / delay, 0.2);
                debris(level, V.up(c, 0.3), 20, 2.0, COBBLE);
                RuneKit.rockBurst(level, V.up(c, 0.3), 4, 6, 0.3);
                level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, STONE), c.x, c.y + 0.1, c.z, 20, 2.2, 0.1, 2.2, 0.4);
                if (tt % 9 == 0) SkillFx.play(level, "block.deepslate.break", c, 0.5, 1.0);
            });
        }
        RuneKit.glowCrack(level, c, 4.0, JADE, CORE, Opt.of("life", (crushAt + 6) / 20.0, "v", 1.0));
        RuneKit.glowCrack(level, aim, radius * 0.8, JADE, CORE, Opt.of("life", 1.6));
        PairKit.Rig g = golem(level, c, end + 4, new Vec3(0, -4.6 * TITAN - 1, 0), q, TITAN);
        RuneKit.later(2, () -> golemPose(g, Vec3.ZERO, q, TITAN, -10, -30, -30, Math.max(4, delay - 10)));
        RuneKit.later(delay, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            SkillFx.play(level, "entity.warden.roar", c, 0.5, 1.0);
            SkillFx.play(level, "entity.ravager.roar", c, 0.5, 0.8);
            RuneKit.shakeAround(level, c, 20, 0.35, 0.5);
        });
        // 주먹 연타 (왼 · 오른 번갈아)
        for (int i = 0; i < hits; i++) {
            final int ii = i;
            final int hitAt = delay + i * gap;
            boolean left = i % 2 == 0;
            RuneKit.later(hitAt - 6, () -> golemPose(g, Vec3.ZERO, q, TITAN, -4, left ? -170 : -20, left ? -20 : -170, 4));
            RuneKit.later(hitAt - 2, () -> golemPose(g, Vec3.ZERO, q, TITAN, 16, left ? -62 : -25, left ? -25 : -62, 2));
            RuneKit.later(hitAt, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 spot = PairKit.onGround(level, V.add(V.add(c, face, 4.4), V.rightOf(face), (left ? -1 : 1) * 1.5 + (Kit.rand() - 0.5)));
                slamFx(sp, level, spot, radius * 0.5, 1.4);
                RuneKit.zonePulse(level, aim, ii % 2 == 0 ? JADE : CORE, radius, 0.45);
                RuneKit.shakeAround(level, spot, 18, 0.35, 0.3);
                SkillFx.play(level, "mace.heavy_smash_ground", spot, 0.7, 1.0);
                SkillFx.play(level, "entity.iron_golem.attack", spot, 0.5, 1.0);
                for (LivingEntity e : Kit.around(sp, aim, radius, 4)) {
                    PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                        if (RuneKit.fxQuota("colossusHit", 6)) debris(level, Kit.bodyPoint(e), 8, 0.3, COBBLE);
                    });
                }
            });
        }
        // 두 주먹으로 대지를 내려침
        RuneKit.later(crushAt - 10, () -> golemPose(g, Vec3.ZERO, q, TITAN, -10, -172, -172, 7));
        RuneKit.later(crushAt - 2, () -> golemPose(g, Vec3.ZERO, q, TITAN, 22, -60, -60, 2));
        RuneKit.later(crushAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            RuneKit.ultFlashFor(sp, level, aim, "colossus", "flash", Opt.empty());
            slamFx(sp, level, aim, radius, 1.8);
            RuneKit.rockRing(sp, level, aim, radius * 0.95, 10, 0.9, JADE, Opt.of("life", 1.2, "minDist", 2.2));
            RuneKit.novaRing(level, aim, 1.6, radius * 0.85, JADE, CORE, Opt.of("life", 0.6));
            RuneKit.glowCrack(level, aim, radius * 1.1, JADE, CORE, Opt.of("life", 2.0, "v", 1.0));
            debris(level, V.up(aim, 0.5), 60, radius * 0.4, COBBLE);
            RuneKit.shakeAround(level, aim, 24, 0.55, 0.6);
            SkillFx.play(level, "random.explode", aim, 0.5, 1.0);
            SkillFx.play(level, "mace.heavy_smash_ground", aim, 0.5, 1.0);
            SkillFx.play(level, "block.deepslate_bricks.break", aim, 0.4, 1.0);
            for (LivingEntity e : Kit.around(sp, aim, radius, 4)) {
                PairKit.hit(sp, r, e, P, k.num("crush"), DamageNumbers.Style.RUBBLE, life, dmg -> {
                    if (!PairKit.boss(e)) RuneKit.knock(e, V.sub(e.position(), aim), 0.3, 0.6);
                    if (RuneKit.fxQuota("colossusCrush", 8)) debris(level, Kit.bodyPoint(e), 14, 0.4, CRACKED);
                });
            }
        });
        // 무너져 가라앉음
        RuneKit.later(crushAt + 6, () -> {
            g.crumble(10, 0.3, 4.0 * TITAN, 0.55);
            debris(level, V.up(c, 3.0), 50, 2.5, MOSSY_COBBLE);
            SkillFx.play(level, "mob.irongolem.crack", c, 0.4, 1.0);
        });
        return true;
    }
}
