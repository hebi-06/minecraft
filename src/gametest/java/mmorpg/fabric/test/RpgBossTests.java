package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import mmorpg.core.Rules;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.AltarBlocks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.BossCommands;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.boss.BossScarecrow;
import mmorpg.fabric.boss.Bosses;
import mmorpg.fabric.boss.Ticking;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.mob.MobData;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Status;
import mmorpg.fabric.ui.FormNet;
import mmorpg.fabric.ui.Forms;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 5단계 게임 시험 (서버) — 보스 엔진 (bosses.js · bosskit.js) · 허수아비 킹 (boss_scarecrow.js): 제단 누르기 → 도전 창 → 소환 (3초) · 체력 = 설정,
 * 플레이어마다 무적 시간 (둘이 같은 틱에 때리면 둘 다, 한 사람이 0.5초 안에 다시 때리면 막힘 · 더 세면 차이만큼, 평타 뒤 스킬은 따로), 근접 보너스 ×1.1,
 * 필드 밖 · 환경 피해 막기, 쓰러뜨리면 보상 (가방 · 경험치) · 처치 훅 (퀘스트) · 재사용 대기, 싸우는 사람이 없으면 10초 뒤 초기화,
 * 패턴: 짚단 감옥 (속박 → 감옥을 부수면 풀림) · 분신 (가짜 = 침묵, 진짜 = 비틀거림) · 수확의 낫 (토템 밖 즉사) · 원거리 견제 (불), 소리 이름 · /boss.
 * 필드는 신전 없이 하늘 (y 150) 의 돌 바닥 + 제단 (지었다고 표시 — 특징 자리는 설계에서). 시험마다 멀리 (x 44_000 + 80 × 번호). 통과하면 [boss-test] OK 줄.
 */
public final class RpgBossTests {
    private static final String KEY = "scarecrow_king";
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-boss-test");
    private static final int R = 16;

    // ------------------------------------------------------------ 도우미

    /** 시험 필드: 둘레를 붙잡아 불러오고 돌 바닥 (반지름 R + 6) + 제단 블록, 목록에 넣고 지었다고 표시 */
    private static Arena.Altar field(ServerLevel level, int slot, String[] hold) {
        BlockPos pos = new BlockPos(44_000 + slot * 80, 150, 44_000);
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
    private static void put(ServerPlayer sp, Arena.Altar a, double dx, double dz) {
        Vec3 c = Arena.arenaCenter(a);
        sp.snapTo(c.x + dx, a.y, c.z + dz, 0F, 0F);
        sp.setOnGround(true);
    }

    /** 플레이어를 보스에게서 (dx, dz) 자리에 */
    private static void near(ServerPlayer sp, Entity boss, Arena.Altar a, double dx, double dz) {
        sp.snapTo(boss.getX() + dx, a.y, boss.getZ() + dz, 0F, 0F);
        sp.setOnGround(true);
    }

    /** 보스를 멈춤 (걷지 않고, 틀 "pause" 동안 패턴 · 평타 없음 — 시험이 부르는 패턴은 틀을 바꿈) */
    private static void freeze(Bosses.Enc enc, int ticks) {
        Bosses.hold(enc, true);
        Bosses.cast(enc, "pause", ticks);
        enc.farAt = Long.MAX_VALUE / 4;
    }

    private static boolean hit(ServerPlayer sp, Entity e, float amount) {
        return e.hurtServer((ServerLevel) e.level(), sp.damageSources().playerAttack(sp), amount);
    }

    private static int count(ServerPlayer sp, net.minecraft.world.item.Item item) {
        return RpgGearTests.countIn(sp, item);
    }

    // ------------------------------------------------------------ 시험

    /** 소리 이름: 보스 9종 · 엔진 · 상태가 쓰는 베드락 소리 모두 자바 소리로 풀림, 맞는 상자 (감옥 1.6×2.6), 모듈 등록, /boss altar · list */
    @GameTest(environment = "mmorpg:boss", maxTicks = 20)
    public void soundsHitboxesAndCommand(GameTestHelper h) {
        List<String> missing = new ArrayList<>();
        for (String s : BossKit.soundNames()) if (BossKit.sound(s) == null) missing.add(s);
        h.assertTrue(missing.isEmpty(), "unmapped boss sounds " + missing);
        float[] cage = MobData.hitbox(BossScarecrow.CAGE);
        h.assertTrue(cage != null && Math.abs(cage[0] - 1.6) < 1e-4 && Math.abs(cage[1] - 2.6) < 1e-4, "cage hitbox");
        h.assertTrue(MobData.hitbox("rpg:prop_label") == null && MobData.hitbox(BossScarecrow.BOMB) == null, "no hitbox for label · bomb");
        h.assertTrue(Bosses.module(KEY) instanceof BossScarecrow, "scarecrow module registered");
        h.assertTrue(BossKit.bossLabel(10, "수확의 왕", "허수아비 킹").equals("§e[Lv.10] §6수확의 왕 §c허수아비 킹"), "label");
        h.assertTrue(MobData.get("rpg:boss_scarecrow_king").bossName().equals(BossKit.bossLabel(10, "수확의 왕", "허수아비 킹")), "label = boss bar name");
        ServerPlayer op = RpgGearTests.player(h);
        BossCommands.boss(op, "altar", "kraken");
        h.assertTrue(count(op, AltarBlocks.block("kraken").asItem()) == 1, "/boss altar kraken");
        h.assertTrue(BossCommands.boss(op, "list", null), "/boss list");
        LOG.info("[boss-test] OK sounds {} resolve, cage hitbox 1.6x2.6, module, /boss altar", BossKit.soundNames().size());
        RpgGearTests.done(h, op);
    }

    /** 제단 누르기 → 도전 창 → 도전 → 소환진 3초 → 보스 (체력 = 설정 7500 · 이름 · 무리 태그) · 싸우는 중 누르면 체력 알림 */
    @GameTest(environment = "mmorpg:boss", maxTicks = 200)
    public void altarChallengeSummons(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 0, hold);
        ServerPlayer sp = RpgGearTests.player(h);
        Forms.HEADLESS.add(sp.getUUID());
        put(sp, a, 0, 8);
        Arena.onAltarUse.accept(sp, a);
        h.assertTrue(Forms.last(sp) instanceof FormNet.MenuPayload m && m.title().contains("허수아비 킹에게 도전") && m.buttons().size() == 2
                && m.text().contains("근접 보너스") && m.text().contains("불붙은 짚단"), "challenge form " + Forms.last(sp));
        Forms.answer(sp, 0, null);
        Bosses.Enc enc = Bosses.encounterAt(a);
        h.assertTrue(enc != null && "summon".equals(enc.state) && enc.starter == sp, "summoning " + enc);
        long t0 = Tasks.now();
        boolean[] told = {false};
        h.succeedWhen(() -> {
            if (!"fight".equals(enc.state)) throw h.assertionException("still " + enc.state);
            BedrockMob b = enc.boss;
            h.assertTrue(Tasks.now() - t0 >= 60, "summon delay " + (Tasks.now() - t0));
            h.assertTrue(b != null && !b.isRemoved() && b.isAlive(), "boss spawned");
            h.assertTrue(b.getMaxHealth() == 7500F && b.getHealth() == 7500F, "hp " + b.getHealth() + "/" + b.getMaxHealth());
            h.assertTrue(Bosses.encounterOf(b) == enc && Bosses.unitOf(b) == enc && enc.units.size() == 1, "registered");
            h.assertTrue(b.getCustomName() != null && b.getCustomName().getString().contains("허수아비 킹"), "name " + b.getCustomName());
            h.assertTrue(b.entityTags().contains("rpg_boss") && Kit.hasFamily(b, "rpg_boss"), "family tag " + b.entityTags());
            h.assertTrue(BossScarecrow.mem(enc) != null, "module started");
            if (!told[0]) {
                told[0] = true;
                Forms.HEADLESS.add(sp.getUUID());
                Arena.onAltarUse.accept(sp, a); // 싸우는 중: 창 없이 체력
                h.assertTrue(Forms.last(sp) == null, "no form while fighting " + Forms.last(sp));
            }
            LOG.info("[boss-test] OK altar → challenge form → summon (60 ticks) → boss hp 7500");
            finish(h, level, a, hold, sp);
        });
    }

    /** 플레이어마다 무적 시간 · 평타와 스킬은 따로 · 근접 보너스 · 필드 밖 · 환경 피해 */
    @GameTest(environment = "mmorpg:boss", maxTicks = 200)
    public void perPlayerIframes(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 1, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        ServerPlayer p2 = RpgGearTests.player(h);
        ServerPlayer p3 = RpgGearTests.player(h);
        ServerPlayer out = RpgGearTests.player(h);
        put(p1, a, 0, 10);
        put(p2, a, 0, -10);
        put(p3, a, 10, 0);
        put(out, a, 0, R + 4);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.assertTrue(enc != null, "summoned");
        h.runAfterDelay(64, () -> {
            h.assertTrue("fight".equals(enc.state), "fight " + enc.state);
            freeze(enc, 1000);
            BedrockMob b = enc.boss;
            near(p1, b, a, 0, 8);
            near(p2, b, a, 0, -8);
            near(p3, b, a, 2.4, 0); // 몸 겉면에서 1.1칸 (근접 보너스)
            put(out, a, 0, R + 4); // 필드 + 2.5칸 밖
            float hp0 = b.getHealth();
            // 같은 틱에 둘이 때림 → 둘 다 들어감
            h.assertTrue(hit(p1, b, 100F), "p1 lands");
            h.assertTrue(hit(p2, b, 100F), "p2 lands in the same tick");
            h.assertTrue(Math.abs(b.getHealth() - (hp0 - 200)) < 1e-3, "both counted " + b.getHealth());
            // 한 사람이 0.5초 안에 다시 (같은 세기) → 막힘
            h.assertFalse(hit(p1, b, 100F), "p1 again within iframes is blocked");
            h.assertTrue(Math.abs(b.getHealth() - (hp0 - 200)) < 1e-3, "blocked hit took nothing " + b.getHealth());
            // 더 센 공격 → 차이만큼
            h.assertTrue(hit(p1, b, 150F), "stronger hit lands");
            h.assertTrue(Math.abs(b.getHealth() - (hp0 - 250)) < 1e-3, "difference only " + b.getHealth());
            // 평타 뒤 스킬 → 따로 셈 (들어감)
            h.assertTrue(Kit.applySkillDamage(p1, b, 100, false, Kit.Cause.ATTACK, null), "skill after melee lands");
            h.assertTrue(Math.abs(b.getHealth() - (hp0 - 350)) < 1e-3, "skill counted " + b.getHealth());
            h.assertFalse(Kit.applySkillDamage(p1, b, 100, false, Kit.Cause.ATTACK, null), "second skill within iframes blocked");
            // 근접 보너스 ×1.1
            h.assertTrue(Bosses.isClose(b, enc.def, p3, 3) && hit(p3, b, 100F), "close hit lands");
            h.assertTrue(Math.abs(b.getHealth() - (hp0 - 460)) < 1e-2, "close bonus x1.1 " + b.getHealth());
            // 피해 숫자 자리: 가슴 높이, 몸 (반폭 1.3칸) 안이 아니라 때린 사람 쪽 겉면 (자바 글자 엔티티는 몸에 가려짐) — p3 (동쪽 2.4칸) 쪽 1.6칸
            Vec3 anchor = Bosses.textAnchor(b, p3);
            h.assertTrue(anchor != null && Math.abs(anchor.x - b.getX() - 1.6) < 1e-6 && Math.abs(anchor.z - b.getZ()) < 1e-6, "number anchor toward p3 " + anchor);
            h.assertTrue(Math.abs(anchor.y - b.getY() - Math.min(2.6 * 2.6 * 0.45, 3.2)) < 1e-6, "number anchor at chest height " + (anchor.y - b.getY()));
            Vec3 far1 = Bosses.textAnchor(b, p1);
            h.assertTrue(far1 != null && Math.abs(far1.z - b.getZ() - 1.8) < 1e-6, "number anchor toward p1 (body + 0.5) " + far1);
            // 떠 있는 숫자 (다른 시험이 숫자 상한을 채웠으면 없을 수 있음) 는 모두 몸 밖
            for (Display.TextDisplay num : level.getEntitiesOfClass(Display.TextDisplay.class, b.getBoundingBox().inflate(4), d -> d.entityTags().contains(DamageNumbers.TAG))) {
                h.assertTrue(BossKit.hdist(num.position(), b.position()) >= 1.0, "number outside the body " + BossKit.hdist(num.position(), b.position()));
            }
            // 필드 밖 · 환경 피해 → 막힘
            h.assertFalse(hit(out, b, 100F), "outside the field is blocked");
            h.assertFalse(b.hurtServer(level, level.damageSources().generic(), 100F), "environment damage blocked");
            h.assertTrue(Math.abs(b.getHealth() - (hp0 - 460)) < 1e-2, "nothing from outside " + b.getHealth());
            h.assertTrue(enc.parts.containsKey(p1.getUUID()) && enc.parts.containsKey(p2.getUUID()) && !enc.parts.containsKey(out.getUUID()), "participants");
            float hp1 = b.getHealth();
            h.runAfterDelay(11, () -> {
                // 0.5초 뒤 → 다시 들어감
                h.assertTrue(hit(p1, b, 100F), "p1 lands again after the iframes");
                h.assertTrue(Math.abs(b.getHealth() - (hp1 - 100)) < 1e-2, "after iframes " + b.getHealth());
                LOG.info("[boss-test] OK per-player iframes (same tick both · re-hit blocked · stronger = difference · skill separate · close x1.1 · outside blocked)");
                finish(h, level, a, hold, p1, p2, p3, out);
            });
        });
    }

    /** 쓰러뜨리면 보상 (필드 안 모두 — 가방 · 경험치) · 처치 훅 (퀘스트) · 재사용 대기 (누르면 남은 시간, 창 없음) */
    @GameTest(environment = "mmorpg:boss", maxTicks = 200)
    public void killRewardsAndCooldown(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 2, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        ServerPlayer p2 = RpgGearTests.player(h);
        Forms.HEADLESS.add(p1.getUUID());
        put(p1, a, 0, 10);
        put(p2, a, 0, -10);
        List<Object[]> killed = new ArrayList<>();
        // 훅은 서버 전체 (같이 도는 다른 시험의 처치도 옴) — 이 시험의 플레이어가 받은 처치만
        BiConsumer<String, List<ServerPlayer>> hook = (k, list) -> {
            if (list.contains(p1)) killed.add(new Object[] {k, List.copyOf(list)});
        };
        Bosses.addBossKillHook(hook);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.runAfterDelay(64, () -> {
            try {
                freeze(enc, 1000);
                BedrockMob b = enc.boss;
                near(p1, b, a, 0, 8);
                int xp0 = p1.totalExperience;
                b.setHealth(50F);
                h.assertTrue(hit(p1, b, 100F), "killing blow lands");
                h.assertTrue(Bosses.encounterAt(a) == null && "dead".equals(enc.state), "encounter over " + enc.state);
                h.assertTrue(!b.isAlive(), "boss dies");
                int em = RpgGearTests.silver(p1);
                h.assertTrue(em >= 8 && em <= 12, "silver " + em);
                h.assertTrue(RpgGearTests.bossGold(p1, KEY), "0.21.0 boss gold " + RpgGearTests.gold(p1) + " in " + java.util.Arrays.toString(mmorpg.core.Economy.bossGold(KEY)));
                int tickets = count(p1, RuneItems.ticket(1, false).getItem());
                h.assertTrue(tickets >= 1 && tickets <= 2, "rune tickets " + tickets);
                int stone = count(p1, GearItems.byId("rpg:enhance_stone"));
                h.assertTrue(stone >= 3 && stone <= 5, "enhance stones " + stone);
                int ore = count(p1, GearItems.byId("rpg:star_ore"));
                h.assertTrue(ore >= 1 && ore <= 2, "star ore " + ore);
                h.assertTrue(p1.totalExperience - xp0 == 20, "xp " + (p1.totalExperience - xp0));
                h.assertTrue(RpgGearTests.silver(p2) >= 8, "p2 in the field is rewarded too");
                h.assertTrue(killed.size() == 1 && KEY.equals(killed.get(0)[0]) && ((List<?>) killed.get(0)[1]).containsAll(List.of(p1, p2)), "kill hook (quests)");
                h.assertTrue(a.cooldownUntil > System.currentTimeMillis() + 590_000, "cooldown 10 min " + (a.cooldownUntil - System.currentTimeMillis()));
                Arena.onAltarUse.accept(p1, a);
                h.assertTrue(Forms.last(p1) == null && Bosses.encounterAt(a) == null, "no challenge during the cooldown");
                LOG.info("[boss-test] OK kill → rewards (emerald {} · ticket {} · stone {} · ore {} · xp 20) · kill hook · cooldown", em, tickets, stone, ore);
            } finally {
                Bosses.removeBossKillHook(hook);
            }
            finish(h, level, a, hold, p1, p2);
        });
    }

    /** 싸우는 사람이 필드에 없으면 10초 (resetAfter) 뒤 초기화 — 보스는 사라지고 재사용 대기 없음 */
    @GameTest(environment = "mmorpg:boss", maxTicks = 420)
    public void emptyFieldResets(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 3, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        put(p1, a, 0, 10);
        Bosses.Enc enc = Bosses.summon(a, p1);
        long[] left = {-1};
        h.runAfterDelay(64, () -> {
            h.assertTrue("fight".equals(enc.state), "fighting");
            put(p1, a, 0, R + 5); // 필드 (+ 2.5칸) 밖으로
            left[0] = Tasks.now();
        });
        int reset = (int) Rules.get().obj("BOSS").num("resetAfter");
        h.succeedWhen(() -> {
            if (left[0] < 0 || Bosses.encounterAt(a) != null) throw h.assertionException("waiting for the reset");
            h.assertTrue(Tasks.now() - left[0] > reset, "reset after " + (Tasks.now() - left[0]) + " ticks (> " + reset + ")");
            h.assertTrue(enc.boss.isRemoved(), "boss gone");
            h.assertTrue(a.cooldownUntil == 0, "no cooldown after a reset");
            LOG.info("[boss-test] OK empty field resets after {} ticks", Tasks.now() - left[0]);
            finish(h, level, a, hold, p1);
        });
    }

    /** 짚단 감옥: 가장 먼 사람 → 1초 뒤 감옥 + 속박 → 감옥을 부수면 (바닐라 피해) 풀림 */
    @GameTest(environment = "mmorpg:boss", maxTicks = 200)
    public void prisonCagesAndFrees(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 4, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        ServerPlayer p2 = RpgGearTests.player(h);
        put(p1, a, 0, 12);
        put(p2, a, 0, 7);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.runAfterDelay(64, () -> {
            freeze(enc, 1000);
            near(p1, enc.boss, a, 0, 12);
            near(p2, enc.boss, a, 0, 7);
            BossScarecrow.prison(enc, Tasks.now(), BossKit.fighters(enc));
            h.assertTrue(enc.cast != null && "prison".equals(enc.cast.id), "prison cast");
            h.runAfterDelay(22, () -> {
                BossScarecrow.M m = BossScarecrow.mem(enc);
                h.assertTrue(m.cage != null && m.cage.p == p1, "farthest player caged " + (m.cage == null ? null : m.cage.p));
                Entity cage = m.cage.e;
                h.assertTrue(Kit.isType(cage, BossScarecrow.CAGE) && !cage.isRemoved(), "cage entity");
                h.assertTrue(Math.abs(cage.getBbWidth() - 1.6) < 1e-3 && Math.abs(cage.getBbHeight() - 2.6) < 1e-3, "cage hit box " + cage.getBbWidth() + "x" + cage.getBbHeight());
                h.assertTrue(Status.isRooted(p1), "caged player is rooted");
                h.assertTrue(hit(p2, cage, 100F), "a friend breaks the cage (vanilla damage)");
                h.runAfterDelay(2, () -> {
                    h.assertTrue(BossScarecrow.mem(enc).cage == null, "cage gone");
                    h.assertFalse(Status.isRooted(p1), "freed");
                    LOG.info("[boss-test] OK straw cage: farthest player rooted, cage 1.6x2.6, breaking it frees");
                    finish(h, level, a, hold, p1, p2);
                });
            });
        });
    }

    /** 분신: 넷 사이로 숨음 → 가짜를 때리면 침묵 · 가짜는 흩어짐, 진짜를 때리면 분신이 사라지고 비틀거림 (받는 피해 +50%) */
    @GameTest(environment = "mmorpg:boss", maxTicks = 200)
    public void decoysSilenceAndStagger(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 5, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        put(p1, a, 3, 3);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.runAfterDelay(64, () -> {
            freeze(enc, 1000);
            enc.boss.setHealth(7500 * 0.59F);
            BossScarecrow.decoys(enc, Tasks.now());
            h.assertTrue(BossScarecrow.mem(enc).decoys != null && enc.boss.entityTags().contains("rpg_no_auto"), "hidden");
            h.runAfterDelay(14, () -> {
                BossScarecrow.Decoys dm = BossScarecrow.mem(enc).decoys;
                h.assertTrue(dm != null && dm.shown && dm.props.size() == 3 && enc.props.size() == 3, "three decoys " + (dm == null ? null : dm.props.size()));
                Vec3 real = dm.spots.get(dm.real);
                h.assertTrue(BossKit.hdist(enc.boss.position(), real) < 0.6, "real one moved to its spot");
                Entity fake = dm.props.get(0);
                put(p1, a, fake.getX() - Arena.arenaCenter(a).x + 2, fake.getZ() - Arena.arenaCenter(a).z);
                h.assertTrue(hit(p1, fake, 10F), "hitting a decoy is taken");
                h.assertTrue(Status.isSilenced(p1) && fake.isRemoved() && dm.props.size() == 2, "wrong one: silenced, decoy gone");
                float hp = enc.boss.getHealth();
                near(p1, enc.boss, a, 0, 8);
                h.assertTrue(hit(p1, enc.boss, 100F), "real one hit");
                h.assertTrue(BossScarecrow.mem(enc).decoys == null && enc.props.isEmpty(), "decoys cleared");
                h.assertTrue(enc.staggerUntil > Tasks.now() && BossScarecrow.mem(enc).staggering, "staggered");
                h.assertTrue(Math.abs(enc.boss.getHealth() - (hp - 150)) < 1e-2, "found hit +50% " + (hp - enc.boss.getHealth()));
                h.assertFalse(enc.boss.entityTags().contains("rpg_no_auto"), "visible to spirits again");
                LOG.info("[boss-test] OK decoys: wrong = silence, real = stagger (+50%)");
                finish(h, level, a, hold, p1);
            });
        });
    }

    /** 수확의 낫: 토템 금빛 원 안의 사람은 살고, 밖은 즉사 (Status.kill) */
    @GameTest(environment = "mmorpg:boss", maxTicks = 300)
    public void reapingKillsOutsideTotems(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 6, hold);
        ServerPlayer safe = RpgGearTests.player(h);
        ServerPlayer doomed = RpgGearTests.player(h);
        put(safe, a, 0, 10);
        put(doomed, a, 0, -11);
        Bosses.Enc enc = Bosses.summon(a, safe);
        h.runAfterDelay(64, () -> {
            freeze(enc, 1000);
            Vec3 t = enc.feat.totems().get(0);
            Vec3 c = Arena.arenaCenter(a);
            put(safe, a, t.x - c.x + 1.2, t.z - c.z);
            BossScarecrow.reaping(enc, Tasks.now());
            h.assertTrue("reaping".equals(enc.cast.id), "reaping cast");
            int count = (int) enc.def.obj("reaping").num("count");
            h.runAfterDelay(count + 3, () -> {
                h.assertTrue(safe.isAlive() && !safe.isDeadOrDying(), "player at a totem survives");
                h.assertTrue(doomed.isDeadOrDying() || !doomed.isAlive(), "player outside the gold circles is killed");
                LOG.info("[boss-test] OK reaping: totem circle safe, outside = instant death");
                finish(h, level, a, hold, safe, doomed);
            });
        });
    }

    /** 원거리 견제: 10칸 넘게 떨어진 사람 발밑에 원 → 1.5초 뒤 불붙은 짚단 (피해 + 불) */
    @GameTest(environment = "mmorpg:boss", maxTicks = 200)
    public void farShotLands(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 7, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        put(p1, a, 0, 13);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.runAfterDelay(64, () -> {
            freeze(enc, 1000);
            near(p1, enc.boss, a, 0, 13);
            List<ServerPlayer> far = Bosses.farTargets(enc, BossKit.fighters(enc), p -> true);
            h.assertTrue(far.contains(p1), "far target " + far);
            float hp = p1.getHealth();
            Bosses.farThrow(enc, enc, Tasks.now(), far);
            h.assertTrue(enc.farShots.size() == 1 && enc.farTold, "shot in flight + rule told once");
            int windup = (int) enc.def.obj("far").num("windup");
            h.runAfterDelay(windup + 2, () -> {
                h.assertTrue(enc.farShots.isEmpty(), "landed");
                h.assertTrue(p1.getHealth() < hp, "hit " + hp + " → " + p1.getHealth());
                h.assertTrue(p1.getRemainingFireTicks() > 0, "burning");
                LOG.info("[boss-test] OK far shot (fire): disc → lob → hit + burn");
                finish(h, level, a, hold, p1);
            });
        });
    }
}
