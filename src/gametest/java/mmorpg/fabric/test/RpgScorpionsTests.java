package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.AltarBlocks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.boss.BossScorpions;
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
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * 5단계 게임 시험 (서버) — 사막 전갈 · 바위 전갈 (boss_scorpions.js) 과 엔진의 여러 마리 싸움 (bosses.js units):
 * 소환 → 두 마리 (체력 5000 · 7000, 제 자리 서쪽 · 동쪽 8칸, 마리마다 보스 막대 · 무적 시간 · 평타 · 틀 · 제 자리 끌어오기),
 * 공생 (7칸 안 → 받는 피해 -60%, 떨어지면 그대로), 한 마리가 쓰러지면 그 마리만 (싸움은 이어짐, 보상 없음) + 남은 마리 짝 잃은 분노
 * (피해 ×1.4 · 재사용 ×0.7 · 다음 패턴 당김 · 빠른 걸음 · rpg:rage) → 둘 다 쓰러뜨려야 보상 · 재사용 대기 15분,
 * 맹독 표식 (곁의 사람과 나눠 받음 · 혼자면 즉사 · 그동안 원거리 견제 쉼 · 큰 패턴 겹치지 않음), 석화의 포효 (바위 전갈 쪽을 보던 사람만 속박 + 침묵),
 * 모래 잠행 (무적 → 쫓다가 튀어나옴), 대지의 파동 (땅에 선 사람만 — 뛰는 사람은 피함).
 * 필드는 신전 없이 하늘 (y 150) 의 돌 바닥 + 제단 (시험마다 멀리 — x 44_000 + 96 × 번호, z 53_140). 통과하면 [scorpions-test] OK 줄.
 */
public final class RpgScorpionsTests {
    private static final String KEY = "scorpions";
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-scorpions-test");
    private static final int R = 18;
    private static final long FAR = Long.MAX_VALUE / 4;

    // ------------------------------------------------------------ 도우미

    /** 시험 필드: 둘레를 붙잡아 불러오고 돌 바닥 (반지름 R + 6) + 제단 블록, 목록에 넣고 지었다고 표시 */
    private static Arena.Altar field(ServerLevel level, int slot, String[] hold) {
        BlockPos pos = new BlockPos(44_000 + slot * 96, 150, 53_140);
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

    /** 끝: 싸움 정리 · 목록에서 빼기 · 붙잡은 땅 놓기 · 가짜 플레이어 빼기 */
    private static void finish(GameTestHelper h, ServerLevel level, Arena.Altar a, String[] hold, ServerPlayer... players) {
        BossScorpions.forceNext = null;
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

    /** 플레이어를 제단 가운데에서 (dx, dz) 자리에 (바닥 위), 바라보는 쪽 yaw (자바 각도 — 0 = 남쪽) */
    private static void put(ServerPlayer sp, Arena.Altar a, double dx, double dz, float yaw) {
        Vec3 c = Arena.arenaCenter(a);
        sp.snapTo(c.x + dx, a.y, c.z + dz, yaw, 0F);
        sp.setYHeadRot(yaw);
        sp.setOnGround(true);
    }

    private static void put(ServerPlayer sp, Arena.Altar a, double dx, double dz) {
        put(sp, a, dx, dz, 0F);
    }

    /** 보스를 제단 가운데에서 (dx, dz) 자리로 (바닥 위) */
    private static void move(Entity boss, Arena.Altar a, double dx, double dz) {
        Vec3 c = Arena.arenaCenter(a);
        BossKit.teleport(boss, new Vec3(c.x + dx, a.y, c.z + dz));
    }

    /** 두 마리가 스스로 패턴 · 평타 · 원거리 견제를 고르지 않게 (시험이 차례로 부름) — 걷지도 않음 */
    private static void quiet(Bosses.Enc enc) {
        BossScorpions.M m = BossScorpions.mem(enc);
        if (m != null) {
            m.next.replaceAll((k, v) -> FAR);
            m.bigUntil = 0;
        }
        for (Bosses.Unit u : enc.units) {
            Bosses.hold(u, true);
            u.meleeAt = FAR;
            u.farAt = FAR;
        }
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

    // ------------------------------------------------------------ 시험

    /**
     * 소환 → 두 마리: 체력 5000 · 7000, 이름 · 보스 막대 (마리마다), 제 자리 (가운데 ∓ 8칸), 엔진 등록 · 모듈 시작.
     * 여러 마리 싸움 엔진: 마리마다 무적 시간 (한 사람이 같은 틱에 둘을 때려도 둘 다), 평타 · 틀 (한 마리만), 제 자리 끌어오기 (밀려난 마리는 제 자리로)
     */
    @GameTest(environment = "mmorpg:scorpions", maxTicks = 220)
    public void twoUnitsEachTheirOwn(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 0, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        put(p1, a, 0, 10);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.assertTrue(enc != null && enc.units.size() == 2, "two units " + (enc == null ? null : enc.units.size()));
        h.runAfterDelay(64, () -> {
            h.assertTrue("fight".equals(enc.state), "fight " + enc.state);
            Bosses.Unit d = BossScorpions.desert(enc);
            Bosses.Unit r = BossScorpions.rock(enc);
            h.assertTrue(d != null && r != null && d != enc && r != enc && enc.boss == null, "units desert · rock (the encounter itself is not a unit)");
            BedrockMob db = d.boss;
            BedrockMob rb = r.boss;
            h.assertTrue(db != null && rb != null && db.isAlive() && rb.isAlive(), "both spawned");
            h.assertTrue(db.getMaxHealth() == 5000F && db.getHealth() == 5000F, "desert hp " + db.getHealth() + "/" + db.getMaxHealth());
            h.assertTrue(rb.getMaxHealth() == 7000F && rb.getHealth() == 7000F, "rock hp " + rb.getHealth() + "/" + rb.getMaxHealth());
            h.assertTrue(Bosses.unitOf(db) == d && Bosses.unitOf(rb) == r && Bosses.encounterOf(db) == enc && Bosses.encounterOf(rb) == enc, "registered per unit");
            h.assertTrue(db.getCustomName() != null && db.getCustomName().getString().contains("거대 사막 전갈") && rb.getCustomName() != null
                    && rb.getCustomName().getString().contains("초거대 바위 전갈"), "names " + db.getCustomName() + " · " + rb.getCustomName());
            ServerBossEvent db0 = db.bossBar();
            ServerBossEvent rb0 = rb.bossBar();
            h.assertTrue(db0 != null && rb0 != null && db0 != rb0 && db0.getName().getString().contains("거대 사막 전갈") && rb0.getName().getString().contains("초거대 바위 전갈"),
                    "a boss bar per unit");
            Vec3 c = Arena.arenaCenter(a);
            h.assertTrue(near(d.home.x, c.x - 8, 1e-6) && near(d.home.z, c.z, 1e-6) && near(r.home.x, c.x + 8, 1e-6) && near(r.home.z, c.z, 1e-6),
                    "homes west · east 8 " + d.home + " " + r.home);
            h.assertTrue(near(d.leash, 9.5, 1e-6) && near(r.leash, 9.5, 1e-6), "home radius 9 (+0.5) " + d.leash);
            BossScorpions.M m = BossScorpions.mem(enc);
            h.assertTrue(m != null && d.mem == m && r.mem == m && m.next.size() == 6, "module started, shared memory");
            quiet(enc);
            tough(p1);
            move(db, a, -8, 0);
            move(rb, a, 8, 0);
            // 마리마다 무적 시간: 한 사람이 같은 틱에 둘을 때림 → 둘 다 (몸 겉면에서 4칸 넘게 — 근접 보너스 없음)
            put(p1, a, 0, 10);
            h.assertTrue(hit(p1, db, 100F) && hit(p1, rb, 100F), "one player hits both in the same tick");
            h.assertTrue(near(db.getHealth(), 4900, 1e-3) && near(rb.getHealth(), 6900, 1e-3), "both took 100: " + db.getHealth() + " · " + rb.getHealth());
            h.assertFalse(hit(p1, db, 100F), "re-hit within this unit's iframes is blocked");
            // 평타 · 틀은 마리마다: 사막 전갈 앞에 서면 사막 전갈만 집게
            put(p1, a, -8 + 3.5, 0);
            d.meleeAt = 0;
            h.assertTrue(Bosses.melee(d, Tasks.now(), BossKit.fighters(enc)), "desert melee starts");
            h.assertTrue(d.cast != null && "melee".equals(d.cast.id) && r.cast == null, "only the desert is in a cast " + (r.cast == null ? null : r.cast.id));
            h.assertTrue(d.meleeAt == Tasks.now() + 36, "desert melee every 36 ticks " + (d.meleeAt - Tasks.now()));
            d.cast = null; // 시험: 내려찍기 판정 · 풀기 단계는 하지 않음 (멈춘 채로)
            d.meleeAt = FAR;
            r.meleeAt = 0;
            h.assertFalse(Bosses.melee(r, Tasks.now(), BossKit.fighters(enc)), "rock (13 blocks away) has nobody in reach");
            r.meleeAt = FAR;
            // 제 자리 끌어오기: 밀려난 사막 전갈은 가운데가 아닌 제 자리 (서쪽) 로
            put(p1, a, 0, 12);
            move(db, a, 6, 12);
            h.runAfterDelay(12, () -> {
                h.assertTrue(db0.getPlayers().contains(p1) && rb0.getPlayers().contains(p1), "both bars shown to the fighter");
                h.assertTrue(BossKit.hdist(db.position(), d.home) < 1.0, "desert pulled back to its own home " + db.position() + " (home " + d.home + ")");
                h.assertTrue(BossKit.hdist(rb.position(), r.home) < 1.5, "rock stays at its home");
                LOG.info("[scorpions-test] OK two units: hp 5000 · 7000, bar · name · home per unit, iframes per unit, melee · cast per unit, leash to own home");
                finish(h, level, a, hold, p1);
            });
        });
    }

    /** 공생: 두 전갈이 7칸 안 → 금빛 끈 (mem.bond) · 받는 피해 -60%, 떨어지면 다시 그대로 */
    @GameTest(environment = "mmorpg:scorpions", maxTicks = 200)
    public void symbiosisCutsDamage(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 1, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        put(p1, a, 0, 12);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.runAfterDelay(64, () -> {
            quiet(enc);
            Bosses.Unit d = BossScorpions.desert(enc);
            Bosses.Unit r = BossScorpions.rock(enc);
            BossScorpions.M m = BossScorpions.mem(enc);
            move(d.boss, a, -3, 0);
            move(r.boss, a, 3, 0);
            h.runAfterDelay(3, () -> {
                h.assertTrue(m.bond && m.told.contains("bond"), "bond formed (6 blocks apart) + rule told");
                float hp = d.boss.getHealth();
                h.assertTrue(hit(p1, d.boss, 100F), "hit lands");
                h.assertTrue(near(hp - d.boss.getHealth(), 40, 1e-3), "symbiosis -60%: took " + (hp - d.boss.getHealth()));
                // 떨어뜨림 → 공생 끝 → 그대로
                move(r.boss, a, 8, 0);
                h.runAfterDelay(3, () -> {
                    h.assertFalse(m.bond, "bond broken (11 blocks apart)");
                    float hr = r.boss.getHealth();
                    h.assertTrue(hit(p1, r.boss, 100F), "hit lands");
                    h.assertTrue(near(hr - r.boss.getHealth(), 100, 1e-3), "apart = full damage: took " + (hr - r.boss.getHealth()));
                    LOG.info("[scorpions-test] OK symbiosis: within 7 blocks -60% (100 → 40), apart = full");
                    finish(h, level, a, hold, p1);
                });
            });
        });
    }

    /**
     * 한 마리가 쓰러짐: 그 마리만 (싸움은 이어짐 · 보상 없음 · 엔진 등록 풀림) → 남은 마리 짝 잃은 분노 (피해 ×1.4 · 재사용 ×0.7 · 잡혀 있던 다음 패턴 당김 ·
     * 빠른 걸음 · rpg:rage) → 남은 마리도 쓰러지면 보상 (필드 안 모두) · 처치 훅 · 재사용 대기 15분
     */
    @GameTest(environment = "mmorpg:scorpions", maxTicks = 200)
    public void oneFallsTheOtherRagesThenRewards(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 2, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        ServerPlayer p2 = RpgGearTests.player(h);
        put(p1, a, 0, 12);
        put(p2, a, 0, -12);
        List<Object[]> killed = new ArrayList<>();
        // 훅은 서버 전체 (같이 도는 다른 시험의 처치도 옴) — 이 시험의 플레이어가 받은 처치만
        BiConsumer<String, List<ServerPlayer>> hook = (k, list) -> {
            if (list.contains(p1)) killed.add(new Object[] {k, List.copyOf(list)});
        };
        Bosses.addBossKillHook(hook);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.runAfterDelay(64, () -> {
            try {
                quiet(enc);
                Bosses.Unit d = BossScorpions.desert(enc);
                Bosses.Unit r = BossScorpions.rock(enc);
                BossScorpions.M m = BossScorpions.mem(enc);
                long now = Tasks.now();
                m.next.put("boulders", now + 100);
                m.next.put("tremor", now + 200);
                Map<String, Long> before = new LinkedHashMap<>(m.next);
                d.boss.setHealth(5F);
                h.assertTrue(hit(p1, d.boss, 50F), "killing blow on the desert scorpion");
                h.assertTrue(!d.alive && r.alive && "fight".equals(enc.state) && Bosses.encounterAt(a) == enc, "only the desert falls, the fight goes on");
                h.assertTrue(Bosses.unitOf(d.boss) == null && !d.boss.isAlive(), "fallen unit unregistered + dying");
                h.assertTrue(RpgGearTests.countIn(p1, Items.EMERALD) == 0 && killed.isEmpty(), "no reward yet");
                h.assertTrue(near(r.dmgMult, 1.4, 1e-9) && near(r.cdMult, 0.7, 1e-9) && r.fast, "rage: damage x1.4 · cooldown x0.7 · fast " + r.dmgMult + " " + r.cdMult);
                h.assertTrue(r.boss.propBool("rpg:rage") && m.enraged == r && !m.bond, "red body (rpg:rage), enraged unit");
                h.assertTrue(m.next.get("boulders") == now + 70 && m.next.get("tremor") == now + 140, "pending patterns pulled in x0.7 " + before + " → " + m.next);
                // 빠른 걸음 (분노): 풀면 rpg:release_fast → 이동 0.25 (0.2 × 1.25)
                Bosses.hold(r, false);
                h.assertTrue(r.boss.groups().contains("rpg:free_fast"), "fast walk group " + r.boss.groups());
                Bosses.hold(r, true);
                // 남은 마리도 쓰러뜨림 → 보상
                r.boss.setHealth(5F);
                h.assertTrue(hit(p2, r.boss, 50F), "killing blow on the rock scorpion");
                h.assertTrue(Bosses.encounterAt(a) == null && "dead".equals(enc.state), "encounter over " + enc.state);
                int em = RpgGearTests.countIn(p1, Items.EMERALD);
                h.assertTrue(em >= 12 && em <= 18, "emeralds 12~18: " + em);
                h.assertTrue(RpgGearTests.countIn(p2, Items.EMERALD) >= 12, "p2 rewarded too");
                int stone = RpgGearTests.countIn(p1, GearItems.byId("rpg:enhance_stone"));
                h.assertTrue(stone >= 5 && stone <= 8, "enhance stones 5~8: " + stone);
                h.assertTrue(killed.size() == 1 && KEY.equals(killed.get(0)[0]) && ((List<?>) killed.get(0)[1]).containsAll(List.of(p1, p2)), "kill hook once (quests)");
                h.assertTrue(a.cooldownUntil > System.currentTimeMillis() + 890_000, "cooldown 15 min " + (a.cooldownUntil - System.currentTimeMillis()));
                LOG.info("[scorpions-test] OK one falls → fight goes on, survivor enraged (x1.4 · x0.7 · pulled · fast · rpg:rage) → both down = rewards (emerald {}) · hook · 15 min", em);
            } finally {
                Bosses.removeBossKillHook(hook);
            }
            finish(h, level, a, hold, p1, p2);
        });
    }

    /**
     * 맹독 표식: 표식을 받은 사람 곁 (3칸) 의 사람과 나눠 받음 (둘 다 다치고 살아남음) — 그동안 원거리 견제 쉼 · 다른 마리의 큰 패턴도 없음 → 혼자 받으면 즉사
     */
    @GameTest(environment = "mmorpg:scorpions", maxTicks = 420)
    public void venomSharedOrDeadly(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 3, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        ServerPlayer p2 = RpgGearTests.player(h);
        ServerPlayer p3 = RpgGearTests.player(h);
        put(p1, a, 2, 12);
        put(p2, a, 3.5, 12);
        put(p3, a, -2, -13);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.runAfterDelay(64, () -> {
            quiet(enc);
            Bosses.Unit d = BossScorpions.desert(enc);
            Bosses.Unit r = BossScorpions.rock(enc);
            BossScorpions.M m = BossScorpions.mem(enc);
            for (ServerPlayer sp : List.of(p1, p2, p3)) tough(sp);
            float h1 = p1.getHealth();
            float h2 = p2.getHealth();
            float h3 = p3.getHealth();
            long t0 = Tasks.now();
            BossScorpions.venom(d, t0, List.of(p1));
            int time = (int) d.def.obj("venom").num("time");
            h.assertTrue(m.venom != null && m.venom.p() == p1 && "venom".equals(d.cast.id), "venom on p1");
            h.assertTrue(m.bigUntil >= t0 + time && !enc.mod.farOk(r, t0), "big-pattern lock · far shots pause");
            // 다른 마리의 큰 패턴 (대지의 파동) 이 때가 되어도 나오지 않음
            m.next.put("tremor", t0);
            h.assertFalse(enc.mod.pick(r, t0, BossKit.fighters(enc)), "rock does not start a big pattern during the venom mark");
            m.next.put("tremor", FAR);
            h.runAfterDelay(time + 2, () -> {
                h.assertTrue(m.venom == null, "venom resolved");
                h.assertTrue(p1.isAlive() && p2.isAlive() && !p1.isDeadOrDying() && !p2.isDeadOrDying(), "shared: both survive");
                if (level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL) {
                    h.assertTrue(p1.getHealth() < h1 && p2.getHealth() < h2, "shared: both took damage " + h1 + "→" + p1.getHealth() + ", " + h2 + "→" + p2.getHealth());
                }
                h.assertTrue(p3.getHealth() == h3, "far player untouched");
                // 혼자 받음 → 즉사
                quiet(enc);
                BossScorpions.venom(d, Tasks.now(), List.of(p3));
                h.runAfterDelay(time + 2, () -> {
                    h.assertTrue(p3.isDeadOrDying() || !p3.isAlive(), "alone = instant death");
                    LOG.info("[scorpions-test] OK venom mark: shared by the pair (both alive, damaged), far shots pause, no other big pattern, alone = death");
                    finish(h, level, a, hold, p1, p2, p3);
                });
            });
        });
    }

    /** 석화의 포효: 울부짖음이 끝날 때 바위 전갈 쪽을 보던 사람만 석화 (속박 + 침묵), 등을 돌린 사람은 그대로 · 석화가 풀릴 때까지 큰 패턴 없음 */
    @GameTest(environment = "mmorpg:scorpions", maxTicks = 200)
    public void roarPetrifiesThoseLooking(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 4, hold);
        ServerPlayer looking = RpgGearTests.player(h);
        ServerPlayer away = RpgGearTests.player(h);
        put(looking, a, 8, 8, 180F);
        put(away, a, 8, -8, 180F);
        Bosses.Enc enc = Bosses.summon(a, looking);
        h.runAfterDelay(64, () -> {
            quiet(enc);
            Bosses.Unit r = BossScorpions.rock(enc);
            BossScorpions.M m = BossScorpions.mem(enc);
            move(r.boss, a, 8, 0);
            // 바위 전갈 (가운데 동쪽 8칸) 의 남쪽 8칸에서 북쪽을 봄 = 바위 전갈 쪽, 북쪽 8칸에서 북쪽을 봄 = 등을 돌림
            put(looking, a, 8, 8, 180F);
            put(away, a, 8, -8, 180F);
            long t0 = Tasks.now();
            BossScorpions.roar(r, t0);
            int windup = (int) r.def.obj("roar").num("windup");
            int holdT = (int) r.def.obj("roar").num("hold");
            h.assertTrue("roar".equals(r.cast.id) && m.bigUntil >= t0 + windup + holdT, "roar cast + big lock until the petrify ends");
            h.runAfterDelay(windup + 2, () -> {
                h.assertTrue(Status.isRooted(looking) && Status.isSilenced(looking), "looking → petrified (rooted + silenced)");
                h.assertTrue(m.stone.containsKey(looking.getUUID()), "stone shards on the petrified");
                h.assertFalse(Status.isRooted(away) || Status.isSilenced(away), "turned away → free");
                LOG.info("[scorpions-test] OK roar: looking = rooted + silenced, turned away = free");
                finish(h, level, a, hold, looking, away);
            });
        });
    }

    /** 모래 잠행: 사라지면 무적 (맞아도 체력 그대로) → 흙먼지가 쫓다가 멈춘 자리에서 튀어나옴 (무적 풀림 · 그 자리 · 원 안 피해) */
    @GameTest(environment = "mmorpg:scorpions", maxTicks = 200)
    public void burrowHidesThenBursts(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 5, hold);
        ServerPlayer target = RpgGearTests.player(h);
        ServerPlayer other = RpgGearTests.player(h);
        put(target, a, -8, 4);
        put(other, a, 0, 13);
        Bosses.Enc enc = Bosses.summon(a, target);
        h.runAfterDelay(64, () -> {
            quiet(enc);
            Bosses.Unit d = BossScorpions.desert(enc);
            BossScorpions.M m = BossScorpions.mem(enc);
            move(d.boss, a, -8, 0);
            put(target, a, -8, 4);
            put(other, a, 0, 13);
            tough(target);
            float ht = target.getHealth();
            long t0 = Tasks.now();
            BossScorpions.burrow(d, t0, target);
            h.assertTrue(m.burrow != null && "burrow".equals(d.cast.id) && m.bigUntil > t0, "burrowing");
            h.runAfterDelay(8, () -> {
                float hp = d.boss.getHealth();
                h.assertTrue(d.immune, "immune underground");
                h.assertTrue(hit(other, d.boss, 100F) && d.boss.getHealth() == hp, "hit while burrowed takes nothing (무적)");
            });
            h.succeedWhen(() -> {
                if (m.burrow != null || d.cast == null || !"emerge".equals(d.cast.id)) throw h.assertionException("waiting for the emergence");
                h.assertFalse(d.immune, "vulnerable again");
                h.assertTrue(BossKit.hdist(d.boss.position(), target.position()) < 1.5, "burst out under the target " + d.boss.position());
                if (level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL) h.assertTrue(target.getHealth() < ht, "target hit by the burst");
                LOG.info("[scorpions-test] OK burrow: immune underground → trail → bursts out under the target");
                finish(h, level, a, hold, target, other);
            });
        });
    }

    /** 대지의 파동: 고리가 발밑을 지날 때 땅에 선 사람은 맞고, 뛰어 있는 (공중) 사람은 피함 · 파동마다 한 번 */
    @GameTest(environment = "mmorpg:scorpions", maxTicks = 220)
    public void tremorHitsGroundedOnly(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 6, hold);
        ServerPlayer ground = RpgGearTests.player(h);
        ServerPlayer jumper = RpgGearTests.player(h);
        put(ground, a, 8, 5);
        put(jumper, a, 8, -5);
        Bosses.Enc enc = Bosses.summon(a, ground);
        h.runAfterDelay(64, () -> {
            quiet(enc);
            Bosses.Unit r = BossScorpions.rock(enc);
            BossScorpions.M m = BossScorpions.mem(enc);
            move(r.boss, a, 8, 0);
            put(ground, a, 8, 5);
            put(jumper, a, 8, -5);
            tough(ground);
            tough(jumper);
            jumper.setOnGround(false);
            h.assertTrue(BossScorpions.airborne(jumper, a.y) && !BossScorpions.airborne(ground, a.y), "airborne check");
            long t0 = Tasks.now();
            BossScorpions.tremor(r, t0);
            int first = (int) r.def.obj("tremor").num("first");
            h.assertTrue("tremor".equals(r.cast.id), "tremor cast");
            h.runAfterDelay(first + 22, () -> {
                h.assertTrue(!m.waves.isEmpty(), "wave spreading");
                h.assertTrue(m.waves.get(0).hit().contains(ground.getUUID()), "grounded player hit by the first ring");
                h.assertFalse(m.waves.get(0).hit().contains(jumper.getUUID()), "jumping player dodged the first ring");
                LOG.info("[scorpions-test] OK tremor: ring hits the grounded, the airborne dodge");
                jumper.setOnGround(true);
                finish(h, level, a, hold, ground, jumper);
            });
        });
    }
}
