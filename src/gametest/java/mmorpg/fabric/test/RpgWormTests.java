package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.DoubleSupplier;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.AltarBlocks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.ArenaThemes;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.boss.BossWorm;
import mmorpg.fabric.boss.Bosses;
import mmorpg.fabric.boss.Ticking;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.skill.Status;
import mmorpg.fabric.ui.Forms;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * 5단계 게임 시험 (서버) — 거대 웜 (boss_worm.js): 소환 (체력 15000 · 가운데 굴 · 깨운 사람 쪽) · 굴 이동 (엔진 seat — 땅속 무적, 사람이 많은 굴로 옮겨
 * 밀려도 새 자리로 돌아옴, 튀어나오며 둘레 피해) · 삼키기 (삼킨 사람은 대상에서 빠지고 속박 · 침묵 · 실명, 그 사람이 때린 것은 셈하지 않음,
 * 동료가 피해를 채우면 뱉고 구역질 · 이름표 · 보스 막대에 구출 진행) · 대지 분출 (어두운 칸 → 밝은 칸, 칸 판정 = 바닥 칠하기 식) ·
 * 진동 감지 (걸으면 들켜 즉사, 웅크려 걸으면 안 들킴, 땅속 무적) · 처치 보상 (보스 재료).
 * 필드는 신전 없이 하늘 (y 150) 의 돌 바닥 + 제단 (굴 · 칸은 설계에서). 시험마다 멀리 (x 60_000 + 80 × 번호, z 60_000). 통과하면 [worm-test] OK 줄.
 */
public final class RpgWormTests {
    private static final String KEY = "great_worm";
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-worm-test");
    private static final int R = 19;
    private static final long FAR = Long.MAX_VALUE / 4;

    // ------------------------------------------------------------ 도우미

    /** 시험 필드: 둘레를 붙잡아 불러오고 돌 바닥 (반지름 R + 6) + 제단 블록, 목록에 넣고 지었다고 표시 */
    static Arena.Altar field(ServerLevel level, String key, BlockPos pos, int r, String[] hold) {
        int half = r + 8;
        hold[0] = Ticking.holdArea(level, Vec3.atCenterOf(pos), half, "t");
        for (int cx = Math.floorDiv(pos.getX() - half, 16); cx <= Math.floorDiv(pos.getX() + half, 16); cx++) {
            for (int cz = Math.floorDiv(pos.getZ() - half, 16); cz <= Math.floorDiv(pos.getZ() + half, 16); cz++) level.getChunk(cx, cz);
        }
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -(r + 6); dx <= r + 6; dx++) {
            for (int dz = -(r + 6); dz <= r + 6; dz++) {
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
        return field(level, KEY, new BlockPos(60_000 + slot * 80, 150, 60_000), R, hold);
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
            sp.setShiftKeyDown(false);
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

    /** 보스가 스스로 패턴 · 평타 · 원거리 견제를 고르지 않게 (틀 "pause" — 시험이 부르는 패턴은 틀을 바꿈) */
    private static void freeze(Bosses.Enc enc, int ticks) {
        BossWorm.M m = BossWorm.mem(enc);
        if (m != null) {
            m.next.replaceAll((k, v) -> FAR);
            m.tremorDone.addAll(List.of(0.7, 0.45, 0.2));
            m.bigUntil = FAR;
        }
        enc.meleeAt = FAR;
        enc.farAt = FAR;
        Bosses.cast(enc, "pause", ticks);
    }

    static boolean hit(ServerPlayer sp, Entity e, float amount) {
        return e.hurtServer((ServerLevel) e.level(), sp.damageSources().playerAttack(sp), amount);
    }

    /** 무작위를 정한 수열로 run 을 부르고 되돌림 */
    static void withRand(double[] seq, Runnable run) {
        DoubleSupplier prev = BossKit.rand;
        int[] i = {0};
        BossKit.rand = () -> seq[i[0]++ % seq.length];
        try {
            run.run();
        } finally {
            BossKit.rand = prev;
        }
    }

    static double angDiff(double a, double b) {
        double d = (a - b) % (Math.PI * 2);
        if (d > Math.PI) d -= Math.PI * 2;
        if (d < -Math.PI) d += Math.PI * 2;
        return d;
    }

    // ------------------------------------------------------------ 시험

    /** 소환: 체력 15000 · 모듈 · 가운데 굴 (home = 가운데, 중력 없음) · 굴 다섯 · 칸 3 · 깨운 사람 쪽을 봄 */
    @GameTest(environment = "mmorpg:worm", maxTicks = 200)
    public void wormSummonsAtTheCenterHole(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 0, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        put(p1, a, 0, 8);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.assertTrue(enc != null, "summoned");
        h.runAfterDelay(64, () -> {
            h.assertTrue("fight".equals(enc.state), "fight " + enc.state);
            BedrockMob b = enc.boss;
            h.assertTrue(b.getMaxHealth() == 15000F && b.getHealth() == 15000F, "hp " + b.getHealth() + "/" + b.getMaxHealth());
            h.assertTrue(Bosses.module(KEY) instanceof BossWorm && BossWorm.mem(enc) != null, "worm module started");
            Vec3 c = Arena.arenaCenter(a);
            h.assertTrue(BossWorm.mem(enc).hole == 0 && BossKit.hdist(enc.home, c) < 1e-6, "seated at the center hole " + enc.home);
            h.assertTrue(BossKit.hdist(b.position(), c) < 0.05 && Math.abs(b.getY() - a.y) < 0.05, "body at the hole " + b.position());
            h.assertTrue(b.isNoGravity(), "no gravity (stationary)");
            h.assertTrue(enc.feat.holes().size() == 5 && enc.feat.tile() == 3, "five holes · tile 3 " + enc.feat.holes().size() + " " + enc.feat.tile());
            h.assertTrue(BossKit.hdist(enc.feat.holes().get(1), new Vec3(c.x + 9, a.y, c.z + 9)) < 1e-6, "diagonal hole (+9, +9) " + enc.feat.holes().get(1));
            // 깨운 사람 (남쪽 +z) 쪽을 봄: 각도 π/2
            h.assertTrue(enc.faceNow != null && Math.abs(angDiff(enc.faceNow, Math.PI / 2)) < 0.05, "faces the starter " + enc.faceNow);
            LOG.info("[worm-test] OK summon: hp 15000, seated at the center hole, 5 holes, faces the starter");
            finish(h, level, a, hold, p1);
        });
    }

    /**
     * 굴 이동 (엔진 seat): 땅속으로 (6틱 뒤 무적 — 때려도 체력 그대로) → 사람이 가장 많은 다른 굴로 옮김 (home = 그 굴) → 밀어도 새 자리로 돌아옴 (leash) →
     * 튀어나옴: 무적이 풀리고 둘레 4.5칸 피해, 가장 가까운 사람 쪽을 봄
     */
    @GameTest(environment = "mmorpg:worm", maxTicks = 300)
    public void tunnelSeatsAtTheCrowdedHole(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 1, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        ServerPlayer p2 = RpgGearTests.player(h);
        ServerPlayer p3 = RpgGearTests.player(h);
        put(p1, a, 0, 6);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.runAfterDelay(64, () -> {
            freeze(enc, 1000);
            Vec3 hole1 = enc.feat.holes().get(1);
            Vec3 hole3 = enc.feat.holes().get(3);
            Vec3 c = Arena.arenaCenter(a);
            // 굴 1 (+9, +9) 둘레에 둘, 굴 3 (-9, -9) 둘레에 하나 → 굴 1
            put(p1, a, hole1.x - c.x + 2, hole1.z - c.z);
            put(p2, a, hole1.x - c.x, hole1.z - c.z + 3);
            put(p3, a, hole3.x - c.x + 2, hole3.z - c.z);
            long t0 = Tasks.now();
            boolean started = BossWorm.tunnel(enc, t0, BossKit.fighters(enc));
            h.assertTrue(started && enc.cast != null && "tunnel".equals(enc.cast.id), "tunnel cast");
            BossWorm.M m = BossWorm.mem(enc);
            h.assertTrue(m.under != null && m.under.dest == 1, "goes to the crowded hole " + (m.under == null ? null : m.under.dest));
            float hp1 = p1.getHealth();
            float hp2 = p2.getHealth();
            h.runAfterDelay(8, () -> {
                h.assertTrue(enc.immune, "underground = immune");
                float hp = enc.boss.getHealth();
                hit(p3, enc.boss, 100F);
                h.assertTrue(enc.boss.getHealth() == hp, "no damage while underground " + hp + " → " + enc.boss.getHealth());
            });
            h.runAfterDelay(22, () -> {
                h.assertTrue(m.under != null && m.under.moved && m.hole == 1, "moved under the new hole");
                h.assertTrue(BossKit.hdist(enc.home, hole1) < 1e-6 && BossKit.hdist(enc.boss.position(), hole1) < 0.05, "home = hole 1 " + enc.home + " " + enc.boss.position());
                // 밀어도 새 자리로 (엔진 leash — 10틱마다)
                BossKit.teleport(enc.boss, new Vec3(hole1.x + 2.5, a.y, hole1.z - 1.5));
            });
            h.runAfterDelay(34, () -> {
                h.assertTrue(BossKit.hdist(enc.boss.position(), hole1) < 0.05, "pushed boss returns to the new seat " + enc.boss.position());
            });
            long riseAt = m.under.riseAt;
            h.runAfterDelay((int) (riseAt - t0) + 2, () -> {
                h.assertTrue(m.under == null && !enc.immune, "emerged, no longer immune");
                h.assertTrue(BossKit.hdist(enc.boss.position(), hole1) < 0.05 && m.hole == 1, "emerged at hole 1");
                h.assertTrue(p1.getHealth() < hp1 && p2.getHealth() < hp2, "players by the hole are hit " + hp1 + "→" + p1.getHealth() + " " + hp2 + "→" + p2.getHealth());
                // 가장 가까운 사람 (p1: 굴 1 에서 동쪽 2칸) 쪽을 봄 → 각도 0
                h.assertTrue(enc.faceNow != null && Math.abs(angDiff(enc.faceNow, 0)) < 0.05, "faces the nearest " + enc.faceNow);
                LOG.info("[worm-test] OK tunnel: immune underground, seat = crowded hole, pushed → back to the new seat, emerge hits");
                finish(h, level, a, hold, p1, p2, p3);
            });
        });
    }

    /**
     * 삼키기: 노란 원 → 붉게 멈춤 → 원 안의 한 명을 삼킴 — 대상에서 빠짐 (targetable · targets) · 속박 · 침묵 · 실명 · 몸 안 (제단 위),
     * 이름표 · 보스 막대에 구출 진행, 삼킨 사람이 때린 것은 셈하지 않음 → 동료가 (사람 수 - 1) × 110 을 채우면 뱉고 구역질 (풀려남)
     */
    @GameTest(environment = "mmorpg:worm", maxTicks = 300)
    public void devourSwallowsAndSpits(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 2, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        ServerPlayer p2 = RpgGearTests.player(h);
        put(p1, a, 0, 5);
        put(p2, a, 13, 0);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.runAfterDelay(64, () -> {
            freeze(enc, 1000);
            put(p1, a, 0, 5);
            put(p2, a, 13, 0); // 닿는 곳 (10칸) 밖 → 대상은 p1
            boolean started = BossWorm.devour(enc, Tasks.now(), BossKit.fighters(enc));
            BossWorm.M m = BossWorm.mem(enc);
            h.assertTrue(started && m.devour != null && "aim".equals(m.devour.phase) && m.devour.target == p1, "aims at p1");
            int windup = (int) enc.def.obj("devour").num("windup");
            h.runAfterDelay(windup + 1, () -> {
                BossWorm.Devour D = m.devour;
                h.assertTrue(D != null && "inside".equals(D.phase) && D.victim == p1, "p1 swallowed " + (D == null ? null : D.phase));
                h.assertFalse(enc.mod.targetable(enc, p1), "swallowed player is not a target");
                h.assertTrue(enc.mod.targetable(enc, p2), "others stay targets");
                h.assertTrue(!BossWorm.targets(enc).contains(p1) && BossWorm.targets(enc).contains(p2), "module targets");
                h.assertTrue(Status.isRooted(p1) && Status.isSilenced(p1) && p1.hasEffect(MobEffects.BLINDNESS), "rooted · silenced · blind");
                Vec3 c = Arena.arenaCenter(a);
                h.assertTrue(BossKit.hdist(p1.position(), c) < 0.7 && Math.abs(p1.getY() - (a.y + 1)) < 0.3, "inside the worm (on the altar) " + p1.position());
                h.assertTrue(D.need == 110, "need = helpers × 110 " + D.need);
                String name = enc.boss.getCustomName() == null ? "" : enc.boss.getCustomName().getString();
                String bar = enc.boss.bossBar() == null ? "" : enc.boss.bossBar().getName().getString();
                h.assertTrue(name.contains("구출") && name.contains("0/110") && bar.contains("구출"), "rescue progress on the name · bar: " + name + " / " + bar);
                // 삼켜진 사람이 때린 것은 셈하지 않음
                hit(p1, enc.boss, 50F);
                h.assertTrue(D.dealt == 0, "victim's own hit not counted " + D.dealt);
                // 동료가 채움 → 뱉음
                h.assertTrue(hit(p2, enc.boss, 120F), "helper hit lands");
                h.assertTrue(D.dealt >= 110, "counted " + D.dealt);
                h.runAfterDelay(2, () -> {
                    h.assertTrue(m.devour == null, "spat out");
                    h.assertFalse(Status.isRooted(p1) || Status.isSilenced(p1) || p1.hasEffect(MobEffects.BLINDNESS), "freed");
                    h.assertTrue(enc.mod.targetable(enc, p1), "target again");
                    h.assertTrue(enc.staggerUntil > Tasks.now(), "worm gags (stagger)");
                    String after = enc.boss.getCustomName() == null ? "" : enc.boss.getCustomName().getString();
                    h.assertFalse(after.contains("구출"), "name restored " + after);
                    LOG.info("[worm-test] OK devour: swallowed = untargetable · rooted · silenced · blind, own hits not counted, rescue → spit + gag");
                    finish(h, level, a, hold, p1, p2);
                });
            });
        });
    }

    /** 대지 분출: 칸 판정 = 바닥 칠하기 식 (deepTile), 어두운 칸 먼저 (rand) → 어두운 칸의 사람만 맞음 → gap 뒤 밝은 칸의 사람 */
    @GameTest(environment = "mmorpg:worm", maxTicks = 300)
    public void quakeHitsOneColorThenTheOther(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 3, hold);
        ServerPlayer dark = RpgGearTests.player(h);
        ServerPlayer light = RpgGearTests.player(h);
        put(dark, a, 6, 0);
        put(light, a, 3, 0);
        Bosses.Enc enc = Bosses.summon(a, dark);
        h.runAfterDelay(64, () -> {
            freeze(enc, 1000);
            put(dark, a, 6, 0); // 칸 (2, 0) — 어두운 칸
            put(light, a, 3, 0); // 칸 (1, 0) — 밝은 칸
            h.assertTrue(BossWorm.onDark(enc, dark) && !BossWorm.onDark(enc, light), "tile colors");
            // 칸 가운데 = 바닥 칠하기와 같은 칸
            List<BossWorm.Tile> tiles = BossWorm.quakeTiles(enc);
            List<String> bad = new ArrayList<>();
            for (BossWorm.Tile t : tiles) {
                boolean want = ArenaThemes.deepTile((int) Math.floor(t.x()) - a.x, (int) Math.floor(t.z()) - a.z, 3).dark();
                if (want != t.dark() && bad.size() < 3) bad.add(t.toString());
            }
            h.assertTrue(tiles.size() > 100 && bad.isEmpty(), "tiles " + tiles.size() + " match the floor painter " + bad);
            float hd = dark.getHealth();
            float hl = light.getHealth();
            withRand(new double[] {0.1}, () -> BossWorm.quake(enc, Tasks.now())); // < 0.5 → 어두운 칸 먼저
            BossWorm.M m = BossWorm.mem(enc);
            h.assertTrue(m.quake != null && m.quake.waves.get(0).dark, "dark first");
            int windup = (int) enc.def.obj("quake").num("windup");
            int gap = (int) enc.def.obj("quake").num("gap");
            h.runAfterDelay(windup + 1, () -> {
                h.assertTrue(dark.getHealth() < hd, "dark tile hit " + hd + " → " + dark.getHealth());
                h.assertTrue(light.getHealth() == hl, "light tile not yet " + light.getHealth());
                put(dark, a, 6, 0);
                float hd2 = dark.getHealth();
                h.runAfterDelay(gap + 1, () -> {
                    h.assertTrue(light.getHealth() < hl, "light tile hit second " + hl + " → " + light.getHealth());
                    h.assertTrue(dark.getHealth() == hd2, "dark tile safe on the second wave");
                    h.assertTrue(m.quake == null, "quake over");
                    LOG.info("[worm-test] OK quake: {} tiles = floor painter, dark first then light", tiles.size());
                    finish(h, level, a, hold, dark, light);
                });
            });
        });
    }

    /** 진동 감지: 땅속 (무적) → 듣는 동안 웅크리지 않고 걸은 사람은 들켜 발밑에서 즉사, 웅크려 걸은 사람은 들키지 않음 */
    @GameTest(environment = "mmorpg:worm", maxTicks = 300)
    public void tremorHearsWalkersNotSneakers(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 4, hold);
        ServerPlayer walker = RpgGearTests.player(h);
        ServerPlayer sneaker = RpgGearTests.player(h);
        put(walker, a, 8, 0);
        put(sneaker, a, -8, 0);
        Bosses.Enc enc = Bosses.summon(a, walker);
        h.runAfterDelay(64, () -> {
            freeze(enc, 1000);
            put(walker, a, 8, 0);
            put(sneaker, a, -8, 0);
            long t0 = Tasks.now();
            BossWorm.tremor(enc, t0);
            BossWorm.M m = BossWorm.mem(enc);
            int warn = (int) enc.def.obj("tremor").num("warn");
            h.runAfterDelay(8, () -> h.assertTrue(enc.immune, "underground = immune"));
            h.runAfterDelay(warn + 4, () -> {
                h.assertTrue(m.tremor != null && m.tremor.listeners != null && m.tremor.listeners.size() == 2, "listening to both");
                sneaker.setShiftKeyDown(true);
                put(sneaker, a, -10.5, 0); // 웅크려 2.5칸
                put(walker, a, 10.5, 0); // 걸어서 2.5칸
            });
            h.runAfterDelay(warn + 6, () -> {
                h.assertTrue(m.tremor.heard.contains(walker.getUUID()), "walker heard");
                h.assertFalse(m.tremor.heard.contains(sneaker.getUUID()), "sneaker not heard");
            });
            h.runAfterDelay(warn + 6 + 14, () -> {
                h.assertTrue(walker.isDeadOrDying() || !walker.isAlive(), "walker swallowed (instant death)");
                h.assertTrue(sneaker.isAlive() && !sneaker.isDeadOrDying(), "sneaker alive");
                LOG.info("[worm-test] OK tremor: walker heard → killed, sneaker safe, immune underground");
                finish(h, level, a, hold, walker, sneaker);
            });
        });
    }

    /** 처치 보상: 실버 · 골드 · 16~24 · 경험치 50 · 보스 재료 (파쇄의 송곳니 · 대지의 심장석 2~3) · 처치 훅 · 재사용 대기 15분 */
    @GameTest(environment = "mmorpg:worm", maxTicks = 200)
    public void wormKillRewardsBossMaterials(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 5, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        put(p1, a, 0, 8);
        List<String> killed = new ArrayList<>();
        // 훅은 서버 전체 (같이 도는 다른 시험의 처치도 옴) — 이 시험의 플레이어가 받은 처치만
        BiConsumer<String, List<ServerPlayer>> hook = (k, list) -> {
            if (list.contains(p1)) killed.add(k);
        };
        Bosses.addBossKillHook(hook);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.runAfterDelay(64, () -> {
            try {
                freeze(enc, 1000);
                put(p1, a, 0, 8);
                int xp0 = p1.totalExperience;
                enc.boss.setHealth(50F);
                h.assertTrue(hit(p1, enc.boss, 100F), "killing blow");
                h.assertTrue(Bosses.encounterAt(a) == null && "dead".equals(enc.state), "dead");
                int em = RpgGearTests.silver(p1);
                int fang = RpgGearTests.countIn(p1, GearItems.byId("rpg:worm_fang"));
                int heart = RpgGearTests.countIn(p1, GearItems.byId("rpg:worm_heart"));
                h.assertTrue(em >= 16 && em <= 24, "silver " + em);
                h.assertTrue(RpgGearTests.bossGold(p1, KEY), "0.21.0 boss gold " + RpgGearTests.gold(p1) + " in " + java.util.Arrays.toString(mmorpg.core.Economy.bossGold(KEY)));
                h.assertTrue(fang >= 2 && fang <= 4 && heart >= 2 && heart <= 4, "boss materials fang " + fang + " heart " + heart);
                h.assertTrue(p1.totalExperience - xp0 == 50, "xp " + (p1.totalExperience - xp0));
                h.assertTrue(killed.contains(KEY), "kill hook");
                h.assertTrue(a.cooldownUntil > System.currentTimeMillis() + 890_000, "cooldown 15 min");
                LOG.info("[worm-test] OK kill → emerald {} · fang {} · heart {} · xp 50 · cooldown 15 min", em, fang, heart);
            } finally {
                Bosses.removeBossKillHook(hook);
            }
            finish(h, level, a, hold, p1);
        });
    }
}
