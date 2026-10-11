package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.AltarBlocks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.boss.BossYmir;
import mmorpg.fabric.boss.Bosses;
import mmorpg.fabric.boss.Ticking;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Status;
import mmorpg.fabric.ui.Forms;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 5단계 게임 시험 (서버) — 이미르 (boss_ymir.js): 소환 · 체력 38000 · 호수 밑에서 솟아오름 (그동안 무적) · 걸음 (rpg:gait),
 * 균열 단계 (rpg:crack 1 · 2 · 3, 몸 가까이에서 때린 피해만 온전히 — 멀리서는 ¼) → 무릎 꿇음 (하던 내려치기를 끊음 · 빈틈 +50% · 일어난 뒤 금이 가지 않음),
 * 몽둥이 내려치기 (대상이 몽둥이 줄 위 · 옆 · 뒤는 무사 · 느림), 얼음 가시 → 빙결 (속박 + 얼음 소품이 생기고 깨지고 사라짐),
 * 니플헤임의 숨결 (뛰어옴 · 무적 → 들이쉬는 동안 보스 안개 (엔진의 syncBossFog — 걷어도 0.5초 안에 다시) · 모닥불 위 글자 → 내쉼: 모닥불 곁은 안전, 밖은 얼어붙어 즉사 · 안개 걷힘),
 * 처치 보상 (에메랄드 · 서리 심장 · 거인 뼈 · 경험치 · 재사용 대기 20분 · 처치 훅) · 꺼진 모닥불은 다시 켜짐.
 * 필드는 신전 없이 하늘 (y 150) 의 돌 바닥 + 제단 (지었다고 표시 — 모닥불 자리는 설계에서, 모닥불 블록은 모듈이 놓음). 시험마다 멀리 (x 61_000 + 100 × 번호).
 * 통과하면 [ymir-test] OK 줄.
 */
public final class RpgYmirTests {
    private static final String KEY = "ymir";
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-ymir-test");
    private static final int R = 24;
    private static final long FAR = Long.MAX_VALUE / 4;
    /** 소환 (60틱) 뒤 모듈이 시작한 다음 */
    private static final int SPAWNED = 62;
    /** 솟아오름 (rise 60 + 포효 24 + 2) 이 끝난 다음 (SPAWNED 부터) */
    private static final int ARRIVED = 90;

    // ------------------------------------------------------------ 도우미

    /** 시험 필드: 둘레를 붙잡아 불러오고 돌 바닥 (반지름 R + 4) + 제단 블록, 목록에 넣고 지었다고 표시 */
    private static Arena.Altar field(ServerLevel level, int slot, String[] hold) {
        BlockPos pos = new BlockPos(61_000 + slot * 100, 150, 61_000);
        int half = R + 8;
        hold[0] = Ticking.holdArea(level, Vec3.atCenterOf(pos), half, "t");
        for (int cx = Math.floorDiv(pos.getX() - half, 16); cx <= Math.floorDiv(pos.getX() + half, 16); cx++) {
            for (int cz = Math.floorDiv(pos.getZ() - half, 16); cz <= Math.floorDiv(pos.getZ() + half, 16); cz++) level.getChunk(cx, cz);
        }
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -(R + 4); dx <= R + 4; dx++) {
            for (int dz = -(R + 4); dz <= R + 4; dz++) {
                m.set(pos.getX() + dx, pos.getY() - 1, pos.getZ() + dz);
                level.setBlock(m, stone, Block.UPDATE_CLIENTS);
                for (int dy = 0; dy < 12; dy++) {
                    m.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (!level.getBlockState(m).isAir()) level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        level.setBlock(pos, AltarBlocks.block(KEY).defaultBlockState(), Block.UPDATE_ALL);
        Arena.Altar a = Arena.addAltar(KEY, NpcData.dimId(level), pos);
        a.built = true;
        a.cooldownUntil = 0;
        return a;
    }

    /** 끝: 싸움 정리 · 목록에서 빼기 · 모닥불 블록 치우기 · 붙잡은 땅 놓기 · 가짜 플레이어 빼기 */
    private static void finish(GameTestHelper h, ServerLevel level, Arena.Altar a, String[] hold, ServerPlayer... players) {
        BossYmir.forceNext = null;
        Bosses.Enc enc = Bosses.encounterAt(a);
        if (enc != null) Bosses.endEncounter(enc, "manual");
        for (Vec3 f : Arena.arenaFeatures(a).fires()) level.setBlock(BlockPos.containing(f.x, f.y + 1, f.z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
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

    /** 싸울 사람: 체력을 넉넉히 (활력 300 — 이미르의 한 방에 쓰러지지 않게) */
    private static ServerPlayer fighter(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        RpgPlayers.setStat(sp, "vit", 300);
        sp.setHealth(sp.getMaxHealth());
        return sp;
    }

    /** 플레이어를 제단 가운데에서 (dx, dz) 자리에 (바닥 위) */
    private static void put(ServerPlayer sp, Arena.Altar a, double dx, double dz) {
        Vec3 c = Arena.arenaCenter(a);
        sp.snapTo(c.x + dx, a.y, c.z + dz, 0F, 0F);
        sp.setOnGround(true);
    }

    /** 플레이어를 월드 자리 (x, z) 에 (바닥 위) */
    private static void putAt(ServerPlayer sp, Arena.Altar a, double x, double z) {
        sp.snapTo(x, a.y, z, 0F, 0F);
        sp.setOnGround(true);
    }

    /** 보스가 스스로 패턴 · 원거리 견제 · 숨결 · 겨울을 고르지 않게 (시험이 차례로 부름 — 걷기는 그대로) */
    private static void quiet(Bosses.Enc enc) {
        BossYmir.M m = BossYmir.mem(enc);
        m.next.replaceAll((k, v) -> FAR);
        m.bigUntil = FAR;
        m.breathDone.addAll(enc.def.obj("breath").numbersOrNull("at"));
        m.winter = false;
        enc.meleeAt = FAR;
        enc.farAt = FAR;
    }

    /** 몸을 가운데 남쪽을 보게 세우고 틀 "pause" 로 걷지 않게 (시험이 부르는 패턴은 틀을 바꿈) */
    private static void stand(Bosses.Enc enc, double dx, double dz, double yaw) {
        Bosses.cast(enc, "pause", 4000);
        BossYmir.setBody(enc, enc.center.x + dx, enc.center.z + dz, yaw);
    }

    private static boolean hit(ServerPlayer sp, Entity e, float amount) {
        return e.hurtServer((ServerLevel) e.level(), sp.damageSources().playerAttack(sp), amount);
    }

    private static int count(ServerPlayer sp, net.minecraft.world.item.Item item) {
        return RpgGearTests.countIn(sp, item);
    }

    /** 바닐라 난이도 배율 (때린 엔티티가 있는 보스 피해) */
    private static double scaled(GameTestHelper h, double a) {
        Difficulty d = h.getLevel().getDifficulty();
        return switch (d) {
            case PEACEFUL -> 0;
            case EASY -> Math.min(a / 2 + 1, a);
            case HARD -> a * 1.5;
            default -> a;
        };
    }

    private static boolean dead(ServerPlayer sp) {
        return sp.isDeadOrDying() || !sp.isAlive();
    }

    /** 각도 차이 (-π ~ π) */
    private static double wrapPi(double a) {
        double w = ((a % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
        return w > Math.PI ? w - Math.PI * 2 : w;
    }

    // ------------------------------------------------------------ 시험

    /** 소환: 모듈 등록 · 체력 38000 · 호수 밑 10칸에서 깨운 사람 쪽을 보고 솟아오름 (그동안 무적 · 금이 가지 않음) · 모닥불 넷이 켜짐 → 땅 위에 서서 걸음 (rpg:gait) */
    @GameTest(environment = "mmorpg:ymir", maxTicks = 400)
    public void summonRisesFromTheLake(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 0, hold);
        ServerPlayer p1 = fighter(h);
        put(p1, a, 0, 14);
        h.assertTrue(Bosses.module(KEY) instanceof BossYmir, "ymir module registered");
        h.assertTrue(BossKit.FOGS.containsKey(BossYmir.FOG_BREATH) && BossKit.FOGS.containsKey(BossYmir.FOG_WINTER), "blizzard fogs known to the engine");
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.assertTrue(enc != null && "summon".equals(enc.state), "summoning");
        h.runAfterDelay(SPAWNED, () -> {
            h.assertTrue("fight".equals(enc.state), "fight " + enc.state);
            BedrockMob b = enc.boss;
            h.assertTrue(b != null && Kit.isType(b, "rpg:boss_ymir"), "rpg:boss_ymir spawned");
            h.assertTrue(b.getMaxHealth() == 38000F && b.getHealth() == 38000F, "hp " + b.getHealth() + "/" + b.getMaxHealth());
            BossYmir.M m = BossYmir.mem(enc);
            h.assertTrue(m != null && m.body != null, "module started");
            h.assertTrue(enc.immune && enc.cast != null && "arrive".equals(enc.cast.id) && enc.meleeAt == Long.MAX_VALUE, "rising: untouchable, no engine melee");
            h.assertTrue(m.body.h < -8 && b.getY() < a.y - 7, "deep under the frozen lake (h " + m.body.h + ", y " + b.getY() + ")");
            h.assertTrue(Math.abs(m.body.yaw - Math.PI / 2) < 1e-6, "facing whoever woke it (south) " + m.body.yaw);
            List<BossYmir.Fire> fires = BossYmir.firesOf(enc);
            h.assertTrue(fires.size() == 4, "four Muspelheim fires " + fires.size());
            for (BossYmir.Fire f : fires) h.assertTrue(f.lit && BossYmir.fireBlockLit(enc, f), "fire lit (block) at " + f.at);
            quiet(enc);
            float hp = b.getHealth();
            put(p1, a, 0, 4);
            hit(p1, b, 50F);
            h.assertTrue(b.getHealth() == hp && BossYmir.crackOf(enc).g == 0, "a hit while it rises does nothing (no crack either)");
            put(p1, a, 0, 14);
            h.runAfterDelay(ARRIVED, () -> {
                h.assertTrue(m.body.h == 0 && Math.abs(b.getY() - a.y) < 1e-6, "standing on the ice " + b.getY());
                h.assertTrue(!enc.immune && (enc.cast == null || !"arrive".equals(enc.cast.id)), "the fight is on");
                h.assertTrue(b.propInt("rpg:crack") == 0, "legs whole (rpg:crack 0)");
                double z0 = m.body.z;
                h.runAfterDelay(22, () -> {
                    double moved = m.body.z - z0;
                    h.assertTrue(moved > 2.0 && moved < 3.3, "walks toward the nearest player (" + moved + " blocks in 22 ticks)");
                    h.assertTrue(Math.abs(b.getZ() - m.body.z) < 1e-6 && Math.abs(b.getX() - m.body.x) < 1e-6, "the entity follows its body");
                    h.assertTrue(b.prop("rpg:gait") == 1.0, "rpg:gait 1 while walking " + b.prop("rpg:gait"));
                    LOG.info("[ymir-test] OK summon → hp 38000, rises from the lake (untouchable), four fires lit, walks (rpg:gait 1, {} blocks)", String.format("%.2f", moved));
                    finish(h, level, a, hold, p1);
                });
            });
        });
    }

    /** 균열: 가까이에서 때린 피해만 온전히 (멀리서 ¼) · ⅓ · ⅔ 에서 rpg:crack 1 · 2 → 다 차면 하던 내려치기를 끊고 무릎 (rpg:crack 3) · 빈틈 +50% → 일어남 → 금이 가지 않음 */
    @GameTest(environment = "mmorpg:ymir", maxTicks = 600)
    public void crackStagesKneelAndOpening(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 1, hold);
        ServerPlayer near = fighter(h);
        ServerPlayer far = fighter(h);
        put(near, a, 0, 4);
        put(far, a, 0, 9);
        Bosses.Enc enc = Bosses.summon(a, near);
        Obj C = Rules.get().obj("BOSSES").obj(KEY).obj("crack");
        double need = C.num("need");
        h.runAfterDelay(SPAWNED, () -> {
            quiet(enc);
            h.runAfterDelay(ARRIVED, () -> {
                BedrockMob b = enc.boss;
                BossYmir.M m = BossYmir.mem(enc);
                stand(enc, 0, 0, Math.PI / 2);
                double half = BossKit.bossSize(enc.def).half();
                put(near, a, 0, half + 2.5); // 몸 겉면에서 2.5칸 (근접 보너스 거리)
                put(far, a, 0, half + 7); // 멀리
                BossYmir.Crack K = BossYmir.crackOf(enc);
                h.assertTrue(Bosses.isClose(b, enc.def, near, 3) && !Bosses.isClose(b, enc.def, far, 3), "near / far");
                hit(near, b, 100F);
                double g1 = K.g;
                hit(far, b, 100F);
                double close = 100 * (1 + Rules.get().obj("BOSS").obj("close").num("bonus"));
                h.assertTrue(Math.abs(g1 - close) < 1e-6, "a close hit cracks fully (with its close bonus) " + g1);
                h.assertTrue(Math.abs(K.g - g1 - 100 * C.num("far")) < 1e-6, "a far hit cracks only x" + C.num("far") + " " + (K.g - g1));
                K.g = need / 3 - 5;
                h.runAfterDelay(11, () -> {
                    hit(near, b, 10F);
                    h.assertTrue(K.stage == 1, "a third: stage 1 (" + K.stage + ")");
                    K.g = 2 * need / 3 - 5;
                    h.runAfterDelay(11, () -> {
                        h.assertTrue(b.propInt("rpg:crack") == 1, "rpg:crack 1 sent " + b.propInt("rpg:crack"));
                        hit(near, b, 10F);
                        h.assertTrue(K.stage == 2, "two thirds: stage 2");
                        // 내려치기 도중에 금이 가득 참 → 끊고 무릎
                        BossYmir.forceNext = "slam";
                        enc.cast = null;
                        h.runAfterDelay(3, () -> {
                            h.assertTrue(enc.cast != null && "slam".equals(enc.cast.id), "(winding up a slam) " + (enc.cast == null ? null : enc.cast.id));
                            h.assertTrue(b.propInt("rpg:crack") == 2, "rpg:crack 2 sent");
                            K.g = need - 5;
                            h.runAfterDelay(9, () -> {
                                long t = Tasks.now();
                                float hpN = near.getHealth();
                                hit(near, b, 20F);
                                h.assertTrue(enc.cast != null && "kneel".equals(enc.cast.id) && K.stage == 3 && m.kneelUntil == t + (long) C.num("time"),
                                        "the cracks fill up: the slam is cut off and it kneels " + (enc.cast == null ? null : enc.cast.id));
                                h.assertTrue(K.g == 0 && !BossYmir.mem(enc).told.isEmpty(), "cracks reset, the rule told");
                                // 무릎 꿇은 동안: +50% "빈틈" (어디서 때려도), 금은 더 가지 않음, 원거리 견제도 쉼
                                float hp0 = b.getHealth();
                                hit(far, b, 40F);
                                h.assertTrue(Math.abs(hp0 - b.getHealth() - 40 * (1 + C.num("bonus"))) < 1e-3, "kneeling: +50% (" + (hp0 - b.getHealth()) + ")");
                                Bosses.Hurt hu = Bosses.module(KEY).hurt(enc, far, 10, null);
                                h.assertTrue(hu != null && BossYmir.OPEN_MARK.equals(hu.mark()) && Math.abs(hu.amount() - 15) < 1e-9, "the number gets the gold 빈틈 mark");
                                for (Display.TextDisplay td : level.getEntitiesOfClass(Display.TextDisplay.class, b.getBoundingBox().inflate(6),
                                        d -> d.entityTags().contains(DamageNumbers.TAG) && d.getText().getString().contains("60"))) {
                                    h.assertTrue(td.getText().getString().contains("빈틈"), "number " + td.getText().getString());
                                }
                                h.assertTrue(K.g == 0 && !Bosses.module(KEY).farOk(enc, Tasks.now()), "no cracks, no ranged attack while kneeling");
                                h.runAfterDelay(2, () -> h.assertTrue(b.propInt("rpg:crack") == 3, "rpg:crack 3 (kneeling)"));
                                h.runAfterDelay((int) C.num("time") + 16, () -> {
                                    h.assertTrue(near.getHealth() >= hpN, "the cut-off slam never landed");
                                    h.assertTrue(K.stage == 0 && b.propInt("rpg:crack") == 0, "got up: legs whole again");
                                    quiet(enc);
                                    stand(enc, 0, 0, Math.PI / 2);
                                    float hp1 = b.getHealth();
                                    hit(near, b, 50F);
                                    h.assertTrue(Math.abs(hp1 - b.getHealth() - 50 * (1 + Rules.get().obj("BOSS").obj("close").num("bonus"))) < 1e-3,
                                            "normal damage again " + (hp1 - b.getHealth()));
                                    h.assertTrue(K.g == 0, "no cracks for " + C.num("lock") / 20 + " s after it rises");
                                    LOG.info("[ymir-test] OK crack: close full · far x{} · rpg:crack 1·2·3 · kneel cuts the slam · 빈틈 +50% · rise · lock", C.num("far"));
                                    finish(h, level, a, hold, near, far);
                                });
                            });
                        });
                    });
                });
            });
        });
    }

    /** 몽둥이 내려치기: 대상이 오른 어깨의 몽둥이 줄 위에 오게 몸을 돌림 → 직사각형 안은 피해 + 느림, 줄 옆 · 몸 뒤는 무사 */
    @GameTest(environment = "mmorpg:ymir", maxTicks = 400)
    public void clubSlamHitsTheLine(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 2, hold);
        ServerPlayer target = fighter(h);
        ServerPlayer side = fighter(h);
        ServerPlayer behind = fighter(h);
        put(target, a, 2, 8);
        put(side, a, -8, 8);
        put(behind, a, 0, -5);
        Bosses.Enc enc = Bosses.summon(a, target);
        h.runAfterDelay(SPAWNED, () -> {
            quiet(enc);
            h.runAfterDelay(ARRIVED, () -> {
                BossYmir.M m = BossYmir.mem(enc);
                stand(enc, 0, 0, Math.PI / 2);
                Vec3 c = enc.center;
                putAt(target, a, c.x + 2, c.z + 8);
                putAt(behind, a, c.x, c.z - 5);
                double yw = BossYmir.slamYaw(enc, target.position());
                BossYmir.Rect R = BossYmir.slamRect(enc, yw);
                Vec3 perp = BossYmir.rightOf(yw);
                double width = enc.def.obj("melee").num("width");
                putAt(side, a, target.getX() - perp.x * (width / 2 + 1.1) + R.dir().x * 1.5, target.getZ() - perp.z * (width / 2 + 1.1) + R.dir().z * 1.5);
                h.assertTrue(BossYmir.inRect(target.position(), R, width / 2, 0) && !BossYmir.inRect(side.position(), R, width / 2, 0.3), "target on the club's line, side off it");
                float t0 = target.getHealth();
                float s0 = side.getHealth();
                float b0 = behind.getHealth();
                h.assertTrue(BossYmir.slam(enc, Tasks.now(), BossKit.fighters(enc)), "slam starts");
                h.assertTrue(enc.cast != null && "slam".equals(enc.cast.id) && m.body.want != null && Math.abs(m.body.want - yw) < 1e-9, "turning to bring the club over the target");
                int windup = (int) enc.def.obj("melee").num("windup");
                h.runAfterDelay(windup - 2, () -> {
                    h.assertTrue(Math.abs(wrapPi(m.body.yaw - yw)) < 1e-6, "its body has turned (" + m.body.yaw + " → " + yw + ")");
                    h.assertTrue(target.getHealth() == t0, "nothing before the club comes down");
                    h.runAfterDelay(3, () -> {
                        double want = scaled(h, enc.def.obj("melee").num("damage"));
                        if (want > 0) {
                            h.assertTrue(t0 - target.getHealth() > want * 0.15, "slammed (" + (t0 - target.getHealth()) + ")");
                            h.assertTrue(target.hasEffect(MobEffects.SLOWNESS), "…and slowed");
                        }
                        h.assertTrue(side.getHealth() == s0 && behind.getHealth() == b0, "beside the rectangle and behind it: nothing");
                        LOG.info("[ymir-test] OK club slam: the target on the line is hit + slowed, beside / behind safe");
                        finish(h, level, a, hold, target, side, behind);
                    });
                });
            });
        });
    }

    /** 얼음 가시: 줄 위의 사람 → 피해 + 빙결 (속박) + 얼음 소품 (rpg:prop_ymir_ice) → 풀리는 순간 부서짐 (shatter, 틀 rpg_ice) → 0.6초 뒤 사라짐 */
    @GameTest(environment = "mmorpg:ymir", maxTicks = 400)
    public void spikesFreezeInIceThatShatters(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 3, hold);
        ServerPlayer p1 = fighter(h);
        put(p1, a, 0, 12);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.runAfterDelay(SPAWNED, () -> {
            quiet(enc);
            h.runAfterDelay(ARRIVED, () -> {
                stand(enc, 0, 0, Math.PI / 2);
                put(p1, a, 0, 12);
                float hp = p1.getHealth();
                h.assertTrue(BossYmir.spikes(enc, Tasks.now(), BossKit.fighters(enc)), "spikes start");
                h.assertTrue("spikes".equals(enc.cast.id), "spikes cast");
                h.runAfterDelay(45, () -> {
                    h.assertTrue(p1.getHealth() < hp, "a spike hit " + hp + " → " + p1.getHealth());
                    h.assertTrue(Status.isRooted(p1), "frozen solid (rooted)");
                    List<BossYmir.Ice> ice = BossYmir.iceOf(enc);
                    h.assertTrue(ice.size() == 1 && ice.get(0).pid.equals(p1.getUUID()), "one block of ice, around the player " + ice.size());
                    Entity e = ice.get(0).e;
                    h.assertTrue(Kit.isType(e, BossYmir.ICE_ID) && !e.isRemoved() && BossKit.hdist(e.position(), p1.position()) < 1e-6, "rpg:prop_ymir_ice at their feet");
                    h.assertTrue(!ice.get(0).broken && enc.props.contains(e), "still whole, owned by the fight");
                    h.runAfterDelay(20, () -> {
                        h.assertTrue(ice.get(0).broken && !e.isRemoved(), "shattered the moment it thaws");
                        boolean shatter = false;
                        for (BedrockMob.Playing p : ((BedrockMob) e).syncedAnims()) {
                            if (p.anim().equals("animation.rpg.prop.ymir_ice.shatter") && p.controller().equals("rpg_ice") && p.stop().equals("0")) shatter = true;
                        }
                        h.assertTrue(shatter, "shatter animation on rpg_ice (held) " + ((BedrockMob) e).syncedAnims());
                        h.assertFalse(Status.isRooted(p1), "thawed");
                        h.runAfterDelay(12, () -> {
                            h.assertTrue(e.isRemoved() && BossYmir.iceOf(enc).isEmpty() && !enc.props.contains(e), "…and gone a moment later");
                            LOG.info("[ymir-test] OK ice spikes: hit + frozen, ice prop forms → shatters → gone");
                            finish(h, level, a, hold, p1);
                        });
                    });
                });
            });
        });
    }

    /**
     * 니플헤임의 숨결 (체력 65%): 가운데로 뛰어옴 (무적) → 들이쉼 (보스 안개 · 모닥불 위 글자 · 걷어도 0.5초 안에 다시) →
     * 내쉼: 불이 켜진 모닥불 곁 (3.5칸) 은 안전, 눈밭은 얼어붙어 즉사 (얼음 덩어리) → 안개 걷힘
     */
    @GameTest(environment = "mmorpg:ymir", maxTicks = 600)
    public void breathSparesTheFireside(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 4, hold);
        ServerPlayer warm = fighter(h);
        ServerPlayer cold = fighter(h);
        put(warm, a, 6, 6);
        put(cold, a, 0, 8);
        Bosses.Enc enc = Bosses.summon(a, cold);
        Obj B = Rules.get().obj("BOSSES").obj(KEY).obj("breath");
        h.runAfterDelay(SPAWNED, () -> {
            quiet(enc);
            h.runAfterDelay(ARRIVED, () -> {
                BossYmir.M m = BossYmir.mem(enc);
                stand(enc, 4, -3, Math.PI / 2);
                Vec3 c = enc.center;
                BossYmir.Fire f0 = BossYmir.firesOf(enc).get(0);
                Vec3 in = new Vec3(c.x - f0.at.x, 0, c.z - f0.at.z).normalize();
                putAt(warm, a, f0.at.x + in.x * 2.3, f0.at.z + in.z * 2.3);
                put(cold, a, 0, 8);
                h.assertTrue(BossYmir.nearFire(enc, f0, List.of(warm, cold)).equals(List.of(warm)), "(one by the south-east fire, one out in the snow)");
                // 65%: 큰 패턴을 고르게 (진짜 pick)
                enc.boss.setHealth(enc.boss.getMaxHealth() * ((float) B.numbersOrNull("at").get(0).doubleValue() - 0.01F));
                m.bigUntil = 0;
                m.breathDone.clear();
                enc.cast = null;
                h.runAfterDelay(1, () -> {
                    BossYmir.Breath G = BossYmir.breathOf(enc);
                    h.assertTrue(G != null && "leap".equals(G.stage) && enc.immune && "breath".equals(enc.cast.id), "65%: it leaps to the middle (untouchable)");
                    h.assertTrue(G.cap == 1 && G.who.size() == 2, "two fighters: a fire holds one");
                    h.assertTrue(BossYmir.leapOf(enc) != null, "in the air");
                    int leap = (int) B.num("leap");
                    h.runAfterDelay(leap + 8, () -> {
                        h.assertTrue("inhale".equals(G.stage) && !enc.immune, "landed, drawing breath");
                        h.assertTrue(BossKit.hdist(enc.boss.position(), c) < 1e-6 && Math.abs(enc.boss.getY() - a.y) < 1e-6, "in the middle of the lake");
                        h.assertTrue(BossYmir.FOG_BREATH.equals(BossYmir.fogWant(enc)), "blizzard wanted");
                        for (ServerPlayer p : List.of(warm, cold)) {
                            BossKit.Fog f = m.fog == null ? null : m.fog.get(p.getUUID());
                            h.assertTrue(f != null && BossYmir.FOG_BREATH.equals(f.id()) && Bosses.hasBossFog(p), "the blizzard fog is on " + p.getName().getString());
                            h.assertTrue(Ult.skyBlocked.test(p), "…and blocks a transcend rune's sky tint");
                        }
                        h.assertTrue(G.until == G.t0 + (long) B.num("windup"), "inhaling for " + B.num("windup") / 20 + " s");
                        long exhaleAt = G.until;
                        h.assertTrue(G.labels.size() == 4, "a label over each fire " + G.labels.size());
                        for (BossYmir.Label L : G.labels) {
                            String want = L.f == f0 ? "§6불씨 §f1§7/§f1" : "§6불씨 §f0§7/§f1";
                            h.assertTrue(want.equals(BossYmir.labelText(L)) && Kit.isType(L.e, BossYmir.LABEL_ID), "label " + BossYmir.labelText(L));
                        }
                        // 엔진이 안개를 걷음 (다시 들어옴) → 모듈이 0.5초 안에 다시 씌움
                        Bosses.clearBossFog(List.of(warm));
                        h.assertTrue(!m.fog.containsKey(warm.getUUID()) && !Bosses.hasBossFog(warm), "the engine lifted warm's fog");
                        h.runAfterDelay(11, () -> {
                            BossKit.Fog f = m.fog.get(warm.getUUID());
                            h.assertTrue(f != null && BossYmir.FOG_BREATH.equals(f.id()), "…and it is back within half a second");
                            float hpWarm = warm.getHealth();
                            h.assertTrue(BossYmir.breathOf(enc) == G && !dead(cold), "(still inhaling, nobody frozen yet)");
                            // 내쉼은 그 틱의 보스 틱에서 — 시험은 그다음 틱에 봄
                            h.runAfterDelay((int) (exhaleAt + 1 - Tasks.now()), () -> {
                                h.assertTrue(BossYmir.breathOf(enc) == null, "exhaled");
                                h.assertTrue(!dead(warm) && warm.getHealth() >= hpWarm - 1e-3, "by a lit fire: safe");
                                h.assertTrue(dead(cold), "out in the snow: frozen to death");
                                h.assertTrue(G.labels.stream().allMatch(L -> L.e.isRemoved()), "labels cleared");
                                boolean encased = false;
                                for (BossYmir.Ice i : BossYmir.iceOf(enc)) if (i.pid.equals(cold.getUUID())) encased = true;
                                h.assertTrue(encased, "the frozen one is encased in ice");
                                h.assertTrue(BossYmir.firesOf(enc).stream().allMatch(f2 -> f2.lit), "nobody crowded a fire: all still lit");
                                h.runAfterDelay(2, () -> {
                                    h.assertTrue(BossYmir.fogWant(enc) == null && !m.fog.containsKey(warm.getUUID()) && !Bosses.hasBossFog(warm), "the fog lifts");
                                    LOG.info("[ymir-test] OK Niflheim's breath: leap (immune) → inhale (fog · labels · fog comes back after the engine clears it) → fireside safe, snow frozen → fog lifted");
                                    finish(h, level, a, hold, warm, cold);
                                });
                            });
                        });
                    });
                });
            });
        });
    }

    /** 쓰러뜨리면 보상 (에메랄드 34~48 · 서리 심장 · 거인 뼈 · 경험치 120) · 처치 훅 · 재사용 대기 20분 · 꺼진 모닥불은 다시 켜짐 · 얼음 · 안개 정리 */
    @GameTest(environment = "mmorpg:ymir", maxTicks = 400)
    public void killRewardsAndRelightsFires(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 5, hold);
        ServerPlayer p1 = fighter(h);
        ServerPlayer p2 = fighter(h);
        put(p1, a, 0, 6);
        put(p2, a, 0, -9);
        List<Object[]> killed = new ArrayList<>();
        // 훅은 서버 전체 (같이 도는 다른 시험의 처치도 옴) — 이 시험의 플레이어가 받은 처치만
        BiConsumer<String, List<ServerPlayer>> hook = (k, list) -> {
            if (list.contains(p1)) killed.add(new Object[] {k, List.copyOf(list)});
        };
        Bosses.addBossKillHook(hook);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.runAfterDelay(SPAWNED, () -> {
            quiet(enc);
            h.runAfterDelay(ARRIVED, () -> {
                try {
                    stand(enc, 0, 0, Math.PI / 2);
                    BossYmir.M m = BossYmir.mem(enc);
                    BossYmir.Fire f1 = BossYmir.firesOf(enc).get(1);
                    BossYmir.setFire(enc, f1, false);
                    h.assertTrue(!f1.lit && !BossYmir.fireBlockLit(enc, f1), "(a fire put out)");
                    m.winter = true; // 겨울 안개를 씌운 채로 쓰러짐
                    BedrockMob b = enc.boss;
                    h.runAfterDelay(2, () -> {
                        try {
                            h.assertTrue(m.fog != null && m.fog.containsKey(p1.getUUID()), "(winter fog on)");
                            put(p1, a, 0, 6);
                            int xp0 = p1.totalExperience;
                            b.setHealth(50F);
                            h.assertTrue(hit(p1, b, 100F), "killing blow lands");
                            h.assertTrue(Bosses.encounterAt(a) == null && "dead".equals(enc.state), "Ymir falls " + enc.state);
                            Obj L = Rules.get().obj("BOSSES").obj(KEY).obj("loot");
                            int em = count(p1, Items.EMERALD);
                            h.assertTrue(em >= L.numbersOrNull("emerald").get(0) && em <= L.numbersOrNull("emerald").get(1), "emeralds " + em);
                            Obj mats = Rules.get().obj("BOSS_MATERIALS");
                            for (String k : List.of("ymir_heart", "ymir_bone")) {
                                int n = count(p1, GearItems.byId(mats.obj(k).str("item")));
                                h.assertTrue(n >= 2 && n <= 4, k + " " + n);
                                h.assertTrue(count(p2, GearItems.byId(mats.obj(k).str("item"))) >= 2, "p2 gets " + k + " too");
                            }
                            h.assertTrue(p1.totalExperience - xp0 == (int) L.num("xp"), "xp " + (p1.totalExperience - xp0));
                            // 다른 시험 (허수아비 킹) 의 처치도 같은 훅으로 올 수 있어 이미르 것만 셈
                            List<Object[]> ours = killed.stream().filter(k -> KEY.equals(k[0])).toList();
                            h.assertTrue(ours.size() == 1 && ((List<?>) ours.get(0)[1]).containsAll(List.of(p1, p2)), "kill hook (quests) " + ours.size());
                            h.assertTrue(a.cooldownUntil > System.currentTimeMillis() + 1_190_000, "cooldown 20 min " + (a.cooldownUntil - System.currentTimeMillis()));
                            h.assertTrue(f1.lit && BossYmir.fireBlockLit(enc, f1), "every fire burns again after the fight");
                            h.assertTrue(m.fog.isEmpty() && !Bosses.hasBossFog(p1), "the fog is lifted");
                            h.assertTrue(BossYmir.iceOf(enc).isEmpty() && enc.props.isEmpty(), "props cleared");
                            LOG.info("[ymir-test] OK kill → emerald {} · frost heart · giant's bone · xp · kill hook · 20 min cooldown · fires relit · fog lifted", em);
                        } finally {
                            Bosses.removeBossKillHook(hook);
                        }
                        finish(h, level, a, hold, p1, p2);
                    });
                } catch (RuntimeException | AssertionError e) {
                    Bosses.removeBossKillHook(hook);
                    throw e;
                }
            });
        });
    }
}
