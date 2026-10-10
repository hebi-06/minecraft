package mmorpg.fabric.test;

import java.util.List;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.AltarBlocks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.boss.BossNidhogg;
import mmorpg.fabric.boss.Bosses;
import mmorpg.fabric.boss.Ticking;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Status;
import mmorpg.fabric.ui.Forms;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 5단계 게임 시험 (서버) — 니드호그 (boss_nidhogg.js): 소환 (하늘 14칸에서 2초 동안 내려앉음 · 체력 25000) · 걷기 (엔진 pose — 대상 쪽으로 걸음, rpg:gait),
 * 비상 (날아오름 → 12칸 위 원을 돎 (pose · rpg:fly) → 독의 비 세 번 → 급강하 → 내려앉음), 뿌리의 올가미 (뿌리 소품 · 속박 → 가라앉아 사라짐),
 * 독룡의 낙인 (겹친 사람 즉사, 혼자면 피해), 처치 보상 (보스 재료) · 날다가 쓰러지면 땅에.
 * 필드는 신전 없이 하늘 (y 150) 의 돌 바닥 + 제단 (특징 자리는 설계에서). 시험마다 멀리 (x -52_000 - 96 × 번호, z 52_000). 통과하면 [nidhogg-test] OK 줄.
 */
public final class RpgNidhoggTests {
    private static final String KEY = "nidhogg";
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-nidhogg-test");
    private static final int R = 22;
    private static final long FAR = Long.MAX_VALUE / 4;
    /** 소환 (60틱) + 등장 (내려앉기 40틱 · 틀 44틱) 이 끝나는 때 */
    private static final int ARRIVED = 110;

    // ------------------------------------------------------------ 도우미

    /** 시험 필드: 둘레를 붙잡아 불러오고 돌 바닥 (반지름 R + 6) + 제단 블록, 목록에 넣고 지었다고 표시 */
    static Arena.Altar field(ServerLevel level, String key, BlockPos pos, int radius, String[] hold) {
        int half = radius + 8;
        hold[0] = Ticking.holdArea(level, Vec3.atCenterOf(pos), half, "t");
        for (int cx = Math.floorDiv(pos.getX() - half, 16); cx <= Math.floorDiv(pos.getX() + half, 16); cx++) {
            for (int cz = Math.floorDiv(pos.getZ() - half, 16); cz <= Math.floorDiv(pos.getZ() + half, 16); cz++) level.getChunk(cx, cz);
        }
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -(radius + 6); dx <= radius + 6; dx++) {
            for (int dz = -(radius + 6); dz <= radius + 6; dz++) {
                m.set(pos.getX() + dx, pos.getY() - 1, pos.getZ() + dz);
                level.setBlock(m, stone, Block.UPDATE_CLIENTS);
                for (int dy = 0; dy < 10; dy++) {
                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (!level.getBlockState(m).isAir()) level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        level.setBlock(pos, AltarBlocks.block(key).defaultBlockState(), Block.UPDATE_ALL);
        Arena.Altar a = Arena.addAltar(key, NpcData.dimId(level), pos);
        a.built = true;
        a.cooldownUntil = 0;
        return a;
    }

    private static Arena.Altar field(ServerLevel level, int slot, String[] hold) {
        return field(level, KEY, new BlockPos(-52_000 - slot * 96, 150, 52_000), R, hold);
    }

    /** 끝: 싸움 정리 · 목록에서 빼기 · 붙잡은 땅 놓기 · 가짜 플레이어 빼기 */
    static void finish(GameTestHelper h, ServerLevel level, Arena.Altar a, String[] hold, ServerPlayer... players) {
        Bosses.Enc enc = Bosses.encounterAt(a);
        if (enc != null) Bosses.endEncounter(enc, "manual");
        Arena.removeAltar(a);
        level.setBlock(a.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        Ticking.releaseArea(hold[0]);
        for (ServerPlayer sp : players) {
            Forms.HEADLESS.remove(sp.getUUID());
            Status.clear(sp);
            level.getServer().getPlayerList().remove(sp);
        }
        h.succeed();
    }

    /** 플레이어를 제단 가운데에서 (dx, dz) 자리에 (바닥 위) */
    static void put(ServerPlayer sp, Arena.Altar a, double dx, double dz) {
        Vec3 c = Arena.arenaCenter(a);
        sp.snapTo(c.x + dx, a.y, c.z + dz, 0F, 0F);
        sp.setOnGround(true);
    }

    /** 사진을 찍는 동안 쓰러지지 않게 (저항 amp — 4 = 피해 없음) */
    static void tough(ServerPlayer sp, int amp) {
        sp.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 20 * 60, amp, false, false));
    }

    static boolean hit(ServerPlayer sp, Entity e, float amount) {
        return e.hurtServer((ServerLevel) e.level(), sp.damageSources().playerAttack(sp), amount);
    }

    /** 스스로 패턴 · 원거리 견제를 고르지 않게 (틀 pause — 걷지도 않음, 시험이 부르는 패턴은 틀을 바꿈) */
    private static void freeze(Bosses.Enc enc) {
        BossNidhogg.M m = BossNidhogg.mem(enc);
        m.next.replaceAll((k, v) -> FAR);
        m.bigUntil = FAR;
        m.flightDone.add(0.75);
        m.flightDone.add(0.4);
        enc.farAt = FAR;
        Bosses.cast(enc, "pause", 100000);
    }

    // ------------------------------------------------------------ 시험

    /** 모듈 등록 · 소환: 체력 25000 · 하늘 (14칸) 에서 2초 동안 내려앉음 (rpg:fly 1 → 0) · 엔진 평타 없음 → 걷기 (대상 쪽으로, rpg:gait · 몸 방향) */
    @GameTest(maxTicks = 300)
    public void nidhoggArrivesAndWalks(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 0, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        tough(p1, 4);
        put(p1, a, 0, 14); // 남쪽 14칸 — 니드호그가 이쪽을 보며 내려앉고, 내려앉은 뒤 이쪽으로 걸어옴
        h.assertTrue(Bosses.module(KEY) instanceof BossNidhogg, "nidhogg module registered");
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.assertTrue(enc != null, "summoned");
        Vec3 c = Arena.arenaCenter(a);
        h.runAfterDelay(63, () -> {
            h.assertTrue("fight".equals(enc.state) && enc.boss != null, "fight " + enc.state);
            BedrockMob b = enc.boss;
            h.assertTrue(b.getMaxHealth() == 25000F && b.getHealth() == 25000F, "hp " + b.getHealth() + "/" + b.getMaxHealth());
            BossNidhogg.M m = BossNidhogg.mem(enc);
            h.assertTrue(m != null && m.flight != null && "arrive".equals(m.flight.stage), "arriving from the sky");
            h.assertTrue(b.getY() - c.y > 9 && b.getY() - c.y < 14.01, "high in the sky " + (b.getY() - c.y));
            h.assertTrue(b.prop("rpg:fly") == 1.0, "flying pose rpg:fly " + b.prop("rpg:fly"));
            h.assertTrue(enc.meleeAt == Long.MAX_VALUE, "engine melee off");
            h.assertTrue(enc.cast != null && "arrive".equals(enc.cast.id), "no attacks while arriving");
            // 남쪽 (깨운 사람 쪽) 을 봄: 자바 각도 0
            h.assertTrue(Math.abs(Mth.wrapDegrees(b.getYRot())) < 1, "faces the starter " + b.getYRot());
        });
        double[] d0 = {0};
        h.runAfterDelay(ARRIVED, () -> {
            BedrockMob b = enc.boss;
            BossNidhogg.M m = BossNidhogg.mem(enc);
            h.assertTrue(m.flight == null, "landed " + (m.flight == null ? null : m.flight.stage));
            h.assertTrue(Math.abs(b.getY() - c.y) < 1e-6, "on the floor " + (b.getY() - c.y));
            // 등장 틀 (44틱) 이 끝나면 곧바로 걷기 시작 (가운데에서 남쪽으로 조금)
            h.assertTrue(BossKit.hdist(b.position(), c) < 1.0, "near the center " + BossKit.hdist(b.position(), c));
            // 패턴 · 물어뜯기 없이 걷기만
            m.next.replaceAll((k, v) -> FAR);
            enc.farAt = FAR;
            d0[0] = BossKit.hdist(b.position(), p1.position());
        });
        h.runAfterDelay(ARRIVED + 30, () -> {
            BedrockMob b = enc.boss;
            double d1 = BossKit.hdist(b.position(), p1.position());
            // 초당 2.6칸 (틱 0.13) — 30틱에 3.9칸 (5.5칸 안이면 멈춤)
            h.assertTrue(Math.abs((d0[0] - d1) - 3.9) < 0.3 && d1 > 5.5, "walks toward the player " + d0[0] + " → " + d1);
            h.assertTrue(b.prop("rpg:gait") >= 0.9, "stride rpg:gait " + b.prop("rpg:gait"));
            h.assertTrue(Math.abs(b.getY() - c.y) < 1e-6, "walks on the floor");
            h.assertTrue(BossKit.hdist(enc.home, b.position()) < 1e-6, "engine pose (home = body)");
            h.assertTrue(BossKit.hdist(BossNidhogg.bodyOf(enc), b.position()) < 1e-6, "entity = module body");
            LOG.info("[nidhogg-test] OK summon (hp 25000) → lands from 14 blocks (rpg:fly) → walks {} blocks toward the player (rpg:gait, pose)",
                    String.format("%.2f", d0[0] - d1));
            finish(h, level, a, hold, p1);
        });
    }

    /** 비상: 날아오름 (1.5초) → 12칸 위 반지름 12칸 원을 돎 (pose · rpg:fly 1) → 독의 비 세 번 → 검붉은 원 · 급강하 → 내려앉음 (rpg:fly 0, 틀 끝) */
    @GameTest(maxTicks = 500)
    public void nidhoggFlightRisesAndLands(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 1, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        ServerPlayer p2 = RpgGearTests.player(h);
        tough(p1, 4);
        tough(p2, 4);
        put(p1, a, 0, 9);
        put(p2, a, -8, -4);
        Bosses.Enc enc = Bosses.summon(a, p1);
        Vec3 c = Arena.arenaCenter(a);
        long[] t0 = {0};
        int rain = (int) enc.def.obj("flight").obj("rain").num("windup");
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc);
            t0[0] = Tasks.now();
            BossNidhogg.takeoff(enc, t0[0]);
            BossNidhogg.Flight f = BossNidhogg.flightOf(enc);
            h.assertTrue(f != null && "rise".equals(f.stage), "takeoff");
            h.assertTrue(enc.cast != null && "flight".equals(enc.cast.id), "flight cast");
            h.assertFalse(enc.mod.farOk(enc, t0[0]), "no far shots while flying");
        });
        h.runAfterDelay(ARRIVED + 34, () -> {
            BedrockMob b = enc.boss;
            BossNidhogg.Flight f = BossNidhogg.flightOf(enc);
            h.assertTrue(f != null && "circle".equals(f.stage), "circling " + (f == null ? null : f.stage));
            h.assertTrue(Math.abs(b.getY() - c.y - 12) < 1e-6, "12 blocks up " + (b.getY() - c.y));
            h.assertTrue(Math.abs(BossKit.hdist(b.position(), c) - 12) < 0.05, "circle radius 12 " + BossKit.hdist(b.position(), c));
            h.assertTrue(b.prop("rpg:fly") == 1.0, "rpg:fly 1 " + b.prop("rpg:fly"));
            h.assertTrue(BossKit.hdist(enc.home, b.position()) < 1e-6 && Math.abs(enc.home.y - b.getY()) < 1e-6, "pose keeps home = body (no leash pull)");
        });
        Vec3[] prev = {null};
        h.runAfterDelay(ARRIVED + 44, () -> prev[0] = enc.boss.position());
        h.runAfterDelay(ARRIVED + 45, () -> {
            // 초당 10칸으로 돎 (틱 0.5칸)
            double step = BossKit.hdist(prev[0], enc.boss.position());
            h.assertTrue(Math.abs(step - 0.5) < 0.05, "circles at 0.5 block/tick " + step);
            BossNidhogg.Flight f = BossNidhogg.flightOf(enc);
            h.assertTrue(f.volleys >= 1 && !f.rains.isEmpty(), "poison rain volley (" + f.volleys + ", " + f.rains.size() + " drops)");
        });
        h.runAfterDelay(ARRIVED + 40 + 2 * 40 + rain + 4, () -> {
            BossNidhogg.Flight f = BossNidhogg.flightOf(enc);
            h.assertTrue(f != null && f.volleys == 3 && f.target != null, "three volleys → dive target " + (f == null ? null : f.volleys));
            h.assertTrue(f.target.y == c.y && BossKit.hdist(f.target, c) <= 12 + 1e-6, "dive spot on the floor inside keep");
        });
        h.runAfterDelay(ARRIVED + 210, () -> {
            BedrockMob b = enc.boss;
            h.assertTrue(BossNidhogg.flightOf(enc) == null, "flight over " + (BossNidhogg.flightOf(enc) == null ? null : BossNidhogg.flightOf(enc).stage));
            h.assertTrue(Math.abs(b.getY() - c.y) < 1e-6, "back on the floor " + (b.getY() - c.y));
            h.assertTrue(enc.cast == null || !"flight".equals(enc.cast.id), "flight cast ended");
            h.assertTrue(BossNidhogg.mem(enc).bigUntil < FAR, "big pattern rest set");
        });
        h.runAfterDelay(ARRIVED + 240, () -> {
            h.assertTrue(enc.boss.prop("rpg:fly") == 0.0, "rpg:fly back to 0 " + enc.boss.prop("rpg:fly"));
            LOG.info("[nidhogg-test] OK flight: rise → 12 up circle (0.5/tick, rpg:fly 1) → 3 poison rains → dive → land (rpg:fly 0)");
            finish(h, level, a, hold, p1, p2);
        });
    }

    /** 뿌리의 올가미: 멀리 있는 사람부터 발밑 원 (1.4초) → 뿌리 소품 + 피해 + 속박 2.5초 → 가라앉음 (rpg_roots) → 사라짐 */
    @GameTest(maxTicks = 300)
    public void nidhoggSnareRootsAndBinds(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 2, hold);
        ServerPlayer far = RpgGearTests.player(h);
        ServerPlayer near = RpgGearTests.player(h);
        tough(far, 4);
        tough(near, 4);
        put(far, a, 0, 11);
        put(near, a, -4, 0);
        Bosses.Enc enc = Bosses.summon(a, far);
        int windup = (int) enc.def.obj("snare").num("windup");
        int root = (int) enc.def.obj("snare").num("root");
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc);
            put(far, a, 0, 11);
            put(near, a, -4, 0);
            h.assertTrue(BossNidhogg.snare(enc, Tasks.now(), BossKit.fighters(enc)), "snare cast");
            h.assertTrue("snare".equals(enc.cast.id), "snare cast id");
        });
        h.runAfterDelay(ARRIVED + windup + 2, () -> {
            List<Entity> roots = enc.props.stream().filter(e -> Kit.isType(e, BossNidhogg.ROOTS_ID)).toList();
            h.assertTrue(roots.size() == 2, "two root props " + roots.size());
            h.assertTrue(Status.isRooted(far) && Status.isRooted(near), "both rooted (far first, then near — count 2)");
            h.assertTrue(BossNidhogg.mem(enc).roots.size() == 2, "roots remembered");
            for (Entity e : roots) h.assertFalse(e.isPickable(), "roots are not targetable");
        });
        h.runAfterDelay(ARRIVED + windup + root + 8, () -> {
            BossNidhogg.M m = BossNidhogg.mem(enc);
            h.assertTrue(!m.roots.isEmpty() && m.roots.get(0).sunk, "roots sink after the bind");
            Entity e = m.roots.get(0).e;
            boolean sinking = e instanceof BedrockMob bm && bm.syncedAnims().stream().anyMatch(p -> "rpg_roots".equals(p.controller())
                    && p.anim().endsWith("nid_roots.sink") && "0".equals(p.stop()));
            h.assertTrue(sinking, "sink animation held (rpg_roots, stop 0)");
            h.assertFalse(Status.isRooted(far), "bind over");
        });
        h.runAfterDelay(ARRIVED + windup + root + 20, () -> {
            h.assertTrue(BossNidhogg.mem(enc).roots.isEmpty() && enc.props.stream().noneMatch(e -> Kit.isType(e, BossNidhogg.ROOTS_ID)), "roots removed");
            LOG.info("[nidhogg-test] OK snare: 2 root props + root {} ticks → sink (rpg_roots) → removed", root);
            finish(h, level, a, hold, far, near);
        });
    }

    /** 독룡의 낙인: 6초 뒤 터짐 — 서로 원 안에 든 둘은 즉사, 혼자 떨어진 사람은 마법 피해만 */
    @GameTest(maxTicks = 400)
    public void nidhoggBrandKillsOverlaps(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 3, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        ServerPlayer p2 = RpgGearTests.player(h);
        ServerPlayer lone = RpgGearTests.player(h);
        tough(lone, 3); // 마법 피해 24 의 20%
        put(p1, a, 8, 8);
        put(p2, a, 8, 10);
        put(lone, a, -10, -6);
        Bosses.Enc enc = Bosses.summon(a, lone);
        int time = (int) enc.def.obj("brand").num("time");
        float[] hp = {0};
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc);
            put(p1, a, 8, 8);
            put(p2, a, 8, 10);
            put(lone, a, -10, -6);
            hp[0] = lone.getHealth();
            h.assertTrue(BossNidhogg.brand(enc, Tasks.now(), BossKit.fighters(enc)), "brand");
            BossNidhogg.Brand b = BossNidhogg.brandOf(enc);
            h.assertTrue(b != null && b.marks.size() == 3, "three marks");
            h.assertFalse(enc.mod.farOk(enc, Tasks.now()), "no far shots during the brand");
        });
        h.runAfterDelay(ARRIVED + time + 3, () -> {
            h.assertTrue(BossNidhogg.brandOf(enc) == null, "brand exploded");
            h.assertTrue(p1.isDeadOrDying() && p2.isDeadOrDying(), "overlapping brands kill both");
            h.assertTrue(!lone.isDeadOrDying() && lone.getHealth() < hp[0], "lone player only hurt " + hp[0] + " → " + lone.getHealth());
            LOG.info("[nidhogg-test] OK brand: overlapping = instant death, lone = damage ({} → {})", hp[0], lone.getHealth());
            finish(h, level, a, hold, p1, p2, lone);
        });
    }

    /** 날다가 쓰러지면 땅에 내려놓음 (rpg:fly 0) → 보상 (에메랄드 28~40 · 경험치 90 · 세계수의 가지 · 타락한 세계수 뿌리) · 재사용 대기 15분 */
    @GameTest(maxTicks = 300)
    public void nidhoggKilledInFlightRewards(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 4, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        tough(p1, 4);
        put(p1, a, 0, 9);
        Bosses.Enc enc = Bosses.summon(a, p1);
        Vec3 c = Arena.arenaCenter(a);
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc);
            BossNidhogg.takeoff(enc, Tasks.now());
        });
        h.runAfterDelay(ARRIVED + 34, () -> {
            BedrockMob b = enc.boss;
            h.assertTrue(b.getY() - c.y > 11, "flying " + (b.getY() - c.y));
            int xp0 = p1.totalExperience;
            b.setHealth(10F);
            // 날아 있는 동안 근접은 닿지 않지만 활 · 완드는 닿음 (필드 안이면 피해는 들어감)
            h.assertTrue(hit(p1, b, 50F), "killing blow");
            h.assertTrue(Bosses.encounterAt(a) == null && "dead".equals(enc.state), "encounter over");
            h.assertTrue(Math.abs(b.getY() - c.y) < 1e-6, "put on the ground before dying " + (b.getY() - c.y));
            h.assertTrue(b.prop("rpg:fly") == 0.0, "wings folded (rpg:fly 0)");
            int em = RpgGearTests.countIn(p1, Items.EMERALD);
            int branch = RpgGearTests.countIn(p1, GearItems.byId("rpg:ygg_branch"));
            int rootN = RpgGearTests.countIn(p1, GearItems.byId("rpg:ygg_root"));
            h.assertTrue(em >= 28 && em <= 40, "emeralds " + em);
            h.assertTrue(branch >= 2 && branch <= 4 && rootN >= 2 && rootN <= 4, "boss materials " + branch + " / " + rootN);
            h.assertTrue(p1.totalExperience - xp0 == 90, "xp " + (p1.totalExperience - xp0));
            h.assertTrue(a.cooldownUntil > System.currentTimeMillis() + 890_000, "cooldown 15 min");
            LOG.info("[nidhogg-test] OK killed in flight → grounded, rewards (emerald {} · branch {} · root {} · xp 90) · cooldown", em, branch, rootN);
            finish(h, level, a, hold, p1);
        });
    }
}
