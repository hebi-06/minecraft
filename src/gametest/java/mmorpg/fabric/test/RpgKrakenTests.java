package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.AltarBlocks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.boss.BossKraken;
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
 * 5단계 게임 시험 (서버) — 크라켄 (boss_kraken.js) 과 엔진의 움직이지 않는 보스 (bosses.js stationary):
 * 가운데 (제단 칸, 발 = 바닥) 에 나타나 깨운 사람 쪽을 봄 · 체력 15000, 바닐라 시선으로 몸을 돌리지 않음, 밀려도 0.5초 안에 제 자리 (보던 쪽 그대로),
 * 몸 돌리기는 한 틱에 16°, 효과 면역 (느림 · 실명 · 나약함 …),
 * 촉수 소품 (물구덩이에서 솟음 · 체력 380 · 맞는 소품 (무적 시간 · 이름표 체력 막대) · rpg_tent 동작 · 크라켄 회복 · 쓰러지면 rpg_gone → 1초 뒤 치움 ·
 * 넷 다 베면 그로기 +40% · 시간이 다 되면 물러남), 해일 (기둥 그림자 안 = 버팀, 밖 = 즉사), 처치 보상 (보스 재료 칠흑 먹물 · 심해의 눈) · 재사용 대기 15분.
 * 필드는 신전 없이 하늘 (y 150) 의 돌 바닥 + 제단 (특징 자리 — 물구덩이 · 기둥 — 는 설계에서; 시험마다 멀리 — x 44_000 + 96 × 번호, z 53_460).
 * 통과하면 [kraken-test] OK 줄.
 */
public final class RpgKrakenTests {
    private static final String KEY = "kraken";
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-kraken-test");
    private static final int R = 20;
    private static final long FAR = Long.MAX_VALUE / 4;

    // ------------------------------------------------------------ 도우미

    /** 시험 필드: 둘레를 붙잡아 불러오고 돌 바닥 (반지름 R + 6) + 제단 블록, 목록에 넣고 지었다고 표시 */
    private static Arena.Altar field(ServerLevel level, int slot, String[] hold) {
        BlockPos pos = new BlockPos(44_000 + slot * 96, 150, 53_460);
        int half = R + 8;
        hold[0] = Ticking.holdArea(level, Vec3.atCenterOf(pos), half, "t");
        for (int cx = Math.floorDiv(pos.getX() - half, 16); cx <= Math.floorDiv(pos.getX() + half, 16); cx++) {
            for (int cz = Math.floorDiv(pos.getZ() - half, 16); cz <= Math.floorDiv(pos.getZ() + half, 16); cz++) level.getChunk(cx, cz);
        }
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -(R + 6); dx <= R + 6; dx++) {
            for (int dz = -(R + 6); dz <= R + 6; dz++) {
                m.set(pos.getX() + dx, pos.getY() - 1, pos.getZ() + dz);
                level.setBlock(m, stone, Block.UPDATE_CLIENTS);
                for (int dy = 0; dy < 10; dy++) {
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

    private static void finish(GameTestHelper h, ServerLevel level, Arena.Altar a, String[] hold, ServerPlayer... players) {
        BossKraken.forceNext = null;
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

    private static void put(ServerPlayer sp, Arena.Altar a, double dx, double dz) {
        Vec3 c = Arena.arenaCenter(a);
        sp.snapTo(c.x + dx, a.y, c.z + dz, 0F, 0F);
        sp.setOnGround(true);
    }

    /** 크라켄이 스스로 패턴 · 평타 · 원거리 견제를 고르지 않게 (시험이 차례로 부름) */
    private static void quiet(Bosses.Enc enc) {
        BossKraken.M m = BossKraken.mem(enc);
        if (m != null) {
            m.next.replaceAll((k, v) -> FAR);
            m.bigUntil = FAR;
            m.addsDone.addAll(enc.def.obj("tentacles").numbersOrNull("at"));
            m.tideDone.addAll(enc.def.obj("tide").numbersOrNull("at"));
        }
        enc.meleeAt = FAR;
        enc.farAt = FAR;
    }

    /** 저항 IV (받는 피해 -80%): 낮은 레벨의 가짜 플레이어가 패턴 피해에 쓰러지지 않게 (즉사는 저항과 상관없음) */
    private static void tough(ServerPlayer sp) {
        sp.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 2000, 3, false, false));
        sp.setHealth(sp.getMaxHealth());
    }

    private static boolean hit(ServerPlayer sp, Entity e, float amount) {
        return e.hurtServer((ServerLevel) e.level(), sp.damageSources().playerAttack(sp), amount);
    }

    private static boolean near(double a, double b, double eps) {
        return Math.abs(a - b) <= eps;
    }

    /** 이 촉수 (BedrockMob) 의 rpg_tent 틀에 튼 동작 이름 (없으면 "") */
    private static String tentAnim(Entity e) {
        if (!(e instanceof BedrockMob m)) return "";
        for (BedrockMob.Playing p : m.syncedAnims()) if (BossKraken.TENT_CONTROLLER.equals(p.controller())) return p.anim() + "|" + p.stop();
        return "";
    }

    // ------------------------------------------------------------ 시험

    /**
     * 움직이지 않는 보스: 가운데 (발 = 바닥) · 체력 15000 · 깨운 사람 쪽 (남쪽) 을 봄, 사람이 둘레를 돌아도 몸을 돌리지 않음 (바닐라 시선 없음),
     * 밀려도 0.5초 안에 제 자리 (보던 쪽 그대로), 몸 돌리기 16°/틱, 효과 면역
     */
    @GameTest(maxTicks = 260)
    public void krakenHoldsItsPlace(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 0, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        put(p1, a, 0, 10);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.runAfterDelay(64, () -> {
            h.assertTrue("fight".equals(enc.state) && enc.units.size() == 1 && enc.units.get(0) == enc, "one stationary unit");
            BedrockMob b = enc.boss;
            Vec3 c = Arena.arenaCenter(a);
            h.assertTrue(b.getMaxHealth() == 15000F && b.getHealth() == 15000F, "hp " + b.getHealth());
            h.assertTrue(BossKit.hdist(b.position(), c) < 0.05 && near(b.getY(), a.y, 0.05), "at the center, feet on the floor " + b.position());
            h.assertTrue(b.isNoGravity() && b.noPhysics, "no gravity · no collision");
            // 깨운 사람 (남쪽 10칸) 쪽: 자바 각도 0 (남쪽)
            h.assertTrue(Math.abs(Mth.wrapDegrees(b.getYRot())) < 1 && Math.abs(Mth.wrapDegrees(b.yBodyRot)) < 1, "faces the challenger (south) " + b.getYRot());
            quiet(enc);
            tough(p1);
            // 둘레를 돌아도 (동쪽 · 북쪽) 바닐라 시선으로 몸을 돌리지 않음
            put(p1, a, 9, 0);
            float yaw0 = b.getYRot();
            h.runAfterDelay(20, () -> {
                put(p1, a, 0, -9);
                h.runAfterDelay(20, () -> {
                    h.assertTrue(Math.abs(Mth.wrapDegrees(b.getYRot() - yaw0)) < 0.5 && Math.abs(Mth.wrapDegrees(b.yHeadRot - yaw0)) < 0.5, "does not turn by itself "
                            + yaw0 + " → " + b.getYRot() + " head " + b.yHeadRot);
                    // 효과 면역 (느림 · 실명 · 나약함 — 행동 팩 mob_effect_immunity)
                    b.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 2));
                    b.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0));
                    b.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
                    h.assertTrue(!b.hasEffect(MobEffects.SLOWNESS) && !b.hasEffect(MobEffects.BLINDNESS) && !b.hasEffect(MobEffects.WEAKNESS), "effect immune");
                    // 밀림 (속도 + 자리) → 0.5초 안에 제 자리, 보던 쪽 그대로
                    b.setPos(c.x + 2, a.y + 0.6, c.z + 1.5);
                    b.setDeltaMovement(0.8, 0.2, 0.3);
                    long pushed = Tasks.now();
                    h.runAfterDelay(11, () -> {
                        h.assertTrue(BossKit.hdist(b.position(), c) < 0.05 && near(b.getY(), a.y, 0.05), "back in place within 10 ticks " + b.position() + " ("
                                + (Tasks.now() - pushed) + " ticks)");
                        h.assertTrue(Math.abs(Mth.wrapDegrees(b.getYRot() - yaw0)) < 0.5, "same facing after the push");
                        // 몸 돌리기: 동쪽 (+x) 으로 — 90° 를 16°/틱
                        Bosses.face(enc, new Vec3(1, 0, 0));
                        h.assertTrue(Bosses.turnLeft(enc) > 1.5, "turn requested " + Bosses.turnLeft(enc));
                        h.runAfterDelay(3, () -> {
                            double left = Bosses.turnLeft(enc);
                            h.assertTrue(left > 0.1 && left < Math.PI / 2 - 0.3, "turning step by step " + left);
                            h.runAfterDelay(6, () -> {
                                h.assertTrue(Bosses.turnLeft(enc) < 1e-3 && Math.abs(Mth.wrapDegrees(b.getYRot() + 90)) < 0.5, "faces east (-90°) " + b.getYRot());
                                h.assertTrue(BossKit.hdist(b.position(), c) < 0.05, "turned in place");
                                LOG.info("[kraken-test] OK stationary: center · faces challenger · no vanilla turning · back within 10 ticks · 16°/tick turn · effect immune");
                                finish(h, level, a, hold, p1);
                            });
                        });
                    });
                });
            });
        });
    }

    /**
     * 촉수 소환: 물구덩이 넷에서 솟음 (구덩이 1칸 아래 · 가운데를 봄 · rise 동작) · 체력 380 · 맞는 소품 (무적 시간 · 근접 보너스 · 이름표 체력 막대) ·
     * 살아 있는 동안 크라켄 회복 · 원거리 견제 쉼 → 쓰러지면 die 동작 (멈춤 0) · rpg_gone · 1초 뒤 이름표와 함께 치움 → 넷 다 베면 그로기 (+40%)
     */
    @GameTest(maxTicks = 300)
    public void tentaclesRiseAndFall(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 1, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        ServerPlayer p2 = RpgGearTests.player(h);
        put(p1, a, 0, 9);
        put(p2, a, 0, -9);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.runAfterDelay(64, () -> {
            quiet(enc);
            tough(p1);
            tough(p2);
            BossKraken.M m = BossKraken.mem(enc);
            List<Vec3> holes = enc.feat.holes();
            h.assertTrue(holes.size() == 4, "four tentacle pools in the plan " + holes.size());
            h.assertTrue(BossKraken.summonAdds(enc, Tasks.now()), "summon starts");
            h.assertTrue(m.adds != null && "summon".equals(enc.cast.id) && !enc.mod.farOk(enc, Tasks.now()), "summon cast · far shots pause");
            h.runAfterDelay(16, () -> {
                h.assertTrue(m.adds != null && m.adds.list.size() == 4, "four tentacles " + (m.adds == null ? null : m.adds.list.size()));
                Vec3 c = Arena.arenaCenter(a);
                for (BossKraken.Add t : m.adds.list) {
                    Entity e = t.e;
                    h.assertTrue(Kit.isType(e, BossKraken.TENTACLE) && e instanceof BedrockMob bm && bm.getHealth() == 380F && bm.getMaxHealth() == 380F, "tentacle hp 380");
                    h.assertTrue(BossKit.hdist(e.position(), t.hole) < 0.05 && near(e.getY(), t.hole.y - 1, 0.05), "rises from its pool (one block down) " + e.position());
                    h.assertTrue(Math.abs(Mth.wrapDegrees(e.getYRot() - BossKit.yawTo(e.position(), new Vec3(c.x, e.getY(), c.z)))) < 1, "faces the kraken");
                    Bosses.PropInfo pi = Bosses.propInfoOf(e);
                    h.assertTrue(pi != null && pi.max == 380 && pi.textHeight == 4 && enc.props.contains(e), "hit prop (hp 380, numbers at 4)");
                    h.assertTrue(tentAnim(e).startsWith("animation.rpg.prop.kraken_tentacle.rise|"), "rise animation on rpg_tent " + tentAnim(e));
                    Display.TextDisplay label = BossKraken.labelOf(enc, e);
                    h.assertTrue(label != null && label.getText().getString().contains("크라켄의 촉수") && label.getText().getString().contains("380"), "two-line hp label");
                }
                // 회복: 살아 있는 동안 크라켄이 회복 (10틱마다 최대 체력 × 0.0015 × 0.5 × 촉수 수)
                enc.boss.setHealth(10000F);
                h.runAfterDelay(20, () -> {
                    h.assertTrue(enc.boss.getHealth() > 10000F, "kraken heals while tentacles live " + enc.boss.getHealth());
                    BossKraken.Add t0 = m.adds.list.get(0);
                    BedrockMob e0 = (BedrockMob) t0.e;
                    // 촉수 곁 (2칸) 에서 때림: 근접 보너스 ×1.1 → 110
                    p1.snapTo(t0.hole.x + 2, a.y, t0.hole.z, 0F, 0F);
                    h.assertTrue(hit(p1, e0, 100F), "tentacle takes the hit");
                    h.assertTrue(near(e0.getHealth(), 270, 1e-3), "hp 380 → 270 (close bonus) " + e0.getHealth());
                    h.assertTrue(BossKraken.labelOf(enc, e0).getText().getString().contains("270"), "label bar updated");
                    h.assertFalse(hit(p1, e0, 100F), "same player again within the iframes: blocked");
                    // 쓰러뜨림 → die 동작 · rpg_gone · 남은 촉수 3 → 1초 뒤 치움
                    p2.snapTo(t0.hole.x - 2, a.y, t0.hole.z, 0F, 0F);
                    e0.setHealth(10F);
                    h.assertTrue(hit(p2, e0, 100F), "killing blow on a tentacle");
                    h.assertTrue(t0.dead && e0.entityTags().contains("rpg_gone") && Bosses.propInfoOf(e0).dead, "tentacle down (rpg_gone)");
                    h.assertTrue(tentAnim(e0).equals("animation.rpg.prop.kraken_tentacle.die|0"), "die animation held (stop 0) " + tentAnim(e0));
                    h.assertTrue(!e0.isRemoved() && m.corpses.size() == 1, "corpse stays a moment");
                    h.runAfterDelay(22, () -> {
                        h.assertTrue(e0.isRemoved() && BossKraken.labelOf(enc, e0) == null && !enc.props.contains(e0), "removed with its label after 1 s");
                        // 나머지 셋을 베면 그로기
                        for (BossKraken.Add t : m.adds.list) {
                            if (t.dead) continue;
                            ((BedrockMob) t.e).setHealth(1F);
                            p1.snapTo(t.hole.x + 2, a.y, t.hole.z, 0F, 0F);
                            h.assertTrue(hit(p1, t.e, 50F), "killing blow");
                        }
                        h.runAfterDelay(2, () -> {
                            h.assertTrue(m.adds == null && enc.staggerUntil > Tasks.now() && m.staggerUntil > Tasks.now(), "all cut → kraken staggered");
                            h.assertTrue(enc.cast == null || !"summon".equals(enc.cast.id), "staggered: no cast");
                            // 그로기: 받는 피해 +40% (몸 겉면에서 4칸 넘게 — 근접 보너스 없음)
                            put(p2, a, 0, -10);
                            float hp = enc.boss.getHealth();
                            h.assertTrue(hit(p2, enc.boss, 100F), "hit the staggered kraken");
                            h.assertTrue(near(hp - enc.boss.getHealth(), 140, 1e-2), "stagger +40%: took " + (hp - enc.boss.getHealth()));
                            LOG.info("[kraken-test] OK tentacles: 4 from the pools (hp 380 · label · rise), heal, close bonus hit, die → gone → removed, all cut = stagger +40%");
                            finish(h, level, a, hold, p1, p2);
                        });
                    });
                });
            });
        });
    }

    /** 촉수가 시간 안에 베어지지 않으면 물러남 (맞지 않음 · rpg_gone · sink 동작) → 치움, 그로기 없음 */
    @GameTest(maxTicks = 200)
    public void tentaclesRetreatInTime(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 2, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        put(p1, a, 0, 9);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.runAfterDelay(64, () -> {
            quiet(enc);
            BossKraken.M m = BossKraken.mem(enc);
            BossKraken.summonAdds(enc, Tasks.now());
            h.runAfterDelay(16, () -> {
                h.assertTrue(m.adds != null && m.adds.list.size() == 4, "four tentacles");
                List<Entity> list = new ArrayList<>();
                for (BossKraken.Add t : m.adds.list) list.add(t.e);
                m.adds.until = Tasks.now() + 2; // 시험: 시간이 다 됨
                h.runAfterDelay(4, () -> {
                    h.assertTrue(m.adds == null && enc.staggerUntil <= Tasks.now(), "retreated, no stagger");
                    for (Entity e : list) {
                        h.assertTrue(e.entityTags().contains("rpg_gone") && Bosses.propInfoOf(e) != null && Bosses.propInfoOf(e).dead, "retired");
                        h.assertTrue(tentAnim(e).equals("animation.rpg.prop.kraken_tentacle.sink|0"), "sink animation held " + tentAnim(e));
                    }
                    p1.snapTo(m.corpses.get(0).e().getX() + 2, a.y, m.corpses.get(0).e().getZ(), 0F, 0F);
                    h.assertFalse(hit(p1, list.get(0), 100F), "a retreating tentacle takes no hits");
                    h.runAfterDelay(20, () -> {
                        for (Entity e : list) h.assertTrue(e.isRemoved() && BossKraken.labelOf(enc, e) == null, "removed");
                        LOG.info("[kraken-test] OK tentacles retreat when time runs out (retired · gone · sink → removed)");
                        finish(h, level, a, hold, p1);
                    });
                });
            });
        });
    }

    /** 해일: 서쪽에서 오는 물결 (rand 0) → 기둥 뒤 (동쪽) 그림자 안은 버티고, 열린 곳은 즉사 · 그림자 판정 (sheltered) */
    @GameTest(maxTicks = 420)
    public void tideSparesTheSheltered(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 3, hold);
        ServerPlayer safe = RpgGearTests.player(h);
        ServerPlayer doomed = RpgGearTests.player(h);
        put(safe, a, 0, 12);
        put(doomed, a, 0, -12);
        Bosses.Enc enc = Bosses.summon(a, safe);
        h.runAfterDelay(64, () -> {
            quiet(enc);
            tough(safe);
            tough(doomed);
            BossKraken.M m = BossKraken.mem(enc);
            List<Arena.Pillar> pillars = enc.feat.pillars();
            h.assertTrue(pillars.size() == 4 && near(pillars.get(0).half(), 1.5, 1e-9), "four pillars (3x3) " + pillars.size());
            Arena.Pillar pl = pillars.get(0);
            Vec3 east = new Vec3(1, 0, 0);
            // 물결이 동쪽으로 감 (서쪽에서 옴): 기둥 동쪽 3칸 = 그림자, 기둥 서쪽 = 아님, 기둥 동쪽 7칸 (깊이 5 넘음) = 아님
            h.assertTrue(BossKraken.sheltered(pillars, east, new Vec3(pl.at().x + 3, a.y, pl.at().z), 5, 0.35), "behind the pillar = shelter");
            h.assertFalse(BossKraken.sheltered(pillars, east, new Vec3(pl.at().x - 3, a.y, pl.at().z), 5, 0.35), "in front = no shelter");
            h.assertFalse(BossKraken.sheltered(pillars, east, new Vec3(pl.at().x + 1.5 + 5 + 1, a.y, pl.at().z), 5, 0.35), "too far behind = no shelter");
            h.assertTrue(BossKraken.sideName(east).equals("서쪽") && BossKraken.sideName(new Vec3(0, 0, 1)).equals("북쪽"), "side names");
            Vec3 c = Arena.arenaCenter(a);
            safe.snapTo(pl.at().x + 3, a.y, pl.at().z, 0F, 0F);
            put(doomed, a, 0, -12);
            DoubleSupplier prev = BossKit.rand;
            BossKit.rand = () -> 0.0; // 8방향 중 0 = 동쪽으로 가는 물결
            try {
                BossKraken.tide(enc, Tasks.now());
            } finally {
                BossKit.rand = prev;
            }
            h.assertTrue(m.tide != null && near(m.tide.dir.x, 1, 1e-9) && "tide".equals(enc.cast.id), "tide from the west");
            int windup = (int) enc.def.obj("tide").num("windup");
            h.assertTrue(BossKit.hdist(c, enc.boss.position()) < 0.05, "kraken stays at its place while calling");
            h.runAfterDelay(windup + 95, () -> {
                h.assertTrue(m.tide == null, "wave passed");
                h.assertTrue(safe.isAlive() && !safe.isDeadOrDying(), "sheltered behind a pillar survives");
                h.assertTrue(doomed.isDeadOrDying() || !doomed.isAlive(), "in the open = swept away (instant death)");
                LOG.info("[kraken-test] OK tide: pillar shadow survives, open = death, sheltered() math");
                finish(h, level, a, hold, safe, doomed);
            });
        });
    }

    /** 처치: 보상 (에메랄드 16~24 · 보스 재료 칠흑 먹물 · 심해의 눈 2~3) · 재사용 대기 15분, 남은 촉수 · 이름표도 치움 */
    @GameTest(maxTicks = 200)
    public void killRewardsMaterials(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 4, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        put(p1, a, 0, 9);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.runAfterDelay(64, () -> {
            quiet(enc);
            BossKraken.M m = BossKraken.mem(enc);
            BossKraken.summonAdds(enc, Tasks.now());
            h.runAfterDelay(16, () -> {
                List<Entity> tents = new ArrayList<>();
                List<Display.TextDisplay> labels = new ArrayList<>();
                for (BossKraken.Add t : m.adds.list) {
                    tents.add(t.e);
                    labels.add(BossKraken.labelOf(enc, t.e));
                }
                h.assertTrue(tents.size() == 4 && !labels.contains(null), "tentacles with labels");
                enc.boss.setHealth(5F);
                put(p1, a, 0, 9);
                h.assertTrue(hit(p1, enc.boss, 50F), "killing blow");
                h.assertTrue(Bosses.encounterAt(a) == null && "dead".equals(enc.state), "kraken down");
                int em = RpgGearTests.countIn(p1, Items.EMERALD);
                int ink = RpgGearTests.countIn(p1, GearItems.byId("rpg:kraken_ink"));
                int eye = RpgGearTests.countIn(p1, GearItems.byId("rpg:kraken_eye"));
                h.assertTrue(em >= 16 && em <= 24, "emeralds 16~24: " + em);
                h.assertTrue(ink >= 2 && ink <= 4 && eye >= 2 && eye <= 4, "boss materials 2~3 (+25% one more): ink " + ink + " · eye " + eye);
                h.assertTrue(a.cooldownUntil > System.currentTimeMillis() + 890_000, "cooldown 15 min");
                for (Entity e : tents) h.assertTrue(e.isRemoved(), "tentacles cleared");
                for (Display.TextDisplay td : labels) h.assertTrue(td.isRemoved(), "labels cleared");
                LOG.info("[kraken-test] OK kill: emerald {} · ink {} · eye {} · 15 min · tentacles + labels cleared", em, ink, eye);
                finish(h, level, a, hold, p1);
            });
        });
    }
}
