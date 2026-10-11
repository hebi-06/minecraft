package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.DoubleSupplier;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Props;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.AltarBlocks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.ArenaBlocks;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.boss.BossSurtr;
import mmorpg.fabric.boss.Bosses;
import mmorpg.fabric.boss.Ticking;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.mob.MobData;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.quest.Quests;
import mmorpg.fabric.quest.SideQuests;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.ui.FormNet;
import mmorpg.fabric.ui.Forms;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 5단계 게임 시험 (서버) — 수르트 (boss_surtr.js, 첫 초월 보스): 도전 창 ([초월 보스] · 깨는 법 · 고급 룬 뽑기권 · 초월 장비 재료), 소환 → 화구 밑에서 솟아오름 (무적) →
 * 체력 90000 · 싸우는 동안 필드를 붙잡음 (Ticking) · 사슬 기둥 · 얼음 결정이 서 있음, 레바테인 내려찍기 → 칼끝 쪽 레바테인의 핵 (맞는 소품 5000 · rpg:part · 두 줄 이름표) →
 * 부수면 부위 파괴 (rpg:sword_broken · 불길 꺼짐 · 보상 재료), 무스펠의 업화 → 업화 막대 (보스 막대 소품) 가 피해로 깎임 → 쓰러짐 (빈틈 ×1.5 · 불꽃 왕관의 핵 → rpg:crown_broken),
 * 횡베기의 금빛 창에 스킬 → 반격 (패턴 끊김 · ×1.25), 겹친 보너스는 가장 큰 것 하나, 사슬 기둥이 칼을 붙잡음 (기둥 뒤는 맞지 않음 · ×1.3) → 무너짐 → 싸움이 끝나면 되돌림,
 * 라그나로크 (얼음 결정 뒤 그림자만 삶 · 숨겨 준 결정은 녹음 → 되돌림), 나머지 패턴 (불의 주먹 · 발 구르기 · 불비 · 불의 고리 뛰어넘기 · 찌르기),
 * 처치 → 딜 순위 · 부위 파괴 줄 · 고급 룬 뽑기권 · 초월 재료 (+ 부순 부위) · 처치 훅 · 전설 퀘스트 · 재사용 대기 30분.
 * 필드는 신전 없이 하늘 (y 150) 의 돌 바닥 + 제단 (특징 자리는 설계에서, 사슬 기둥 · 얼음 결정은 싸움이 시작될 때 모듈이 세움 — 애드온 start 의 되돌리기).
 * 시험마다 멀리 (x -61_000 - 220 × 번호, z 63_000). 통과하면 [surtr-test] OK 줄.
 */
public final class RpgSurtrTests {
    private static final String KEY = "surtr";
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-surtr-test");
    private static final int R = 44;
    private static final long FAR = Long.MAX_VALUE / 4;

    // ------------------------------------------------------------ 도우미

    /** 시험 필드: 둘레를 붙잡아 불러오고 돌 바닥 (반지름 R + 6 네모) + 제단 블록, 목록에 넣고 지었다고 표시 */
    private static Arena.Altar field(ServerLevel level, int slot, String[] hold) {
        BlockPos pos = new BlockPos(-61_000 - slot * 220, 150, 63_000);
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
                for (int dy = 0; dy < 18; dy++) {
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
            level.getServer().getPlayerList().remove(sp);
        }
        h.succeed();
    }

    /** 싸울 플레이어: 체력 스탯 1000 (최대 체력 약 1020 — 수르트의 한 방 100~140 을 여러 번 버팀) */
    private static ServerPlayer fighter(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        RpgPlayers.setStat(sp, "vit", 1000);
        sp.setHealth(sp.getMaxHealth());
        return sp;
    }

    /** 플레이어를 필드 가운데에서 (dx, dz) 자리에 (바닥 위) */
    private static void put(ServerPlayer sp, Arena.Altar a, double dx, double dz) {
        Vec3 c = Arena.arenaCenter(a);
        sp.snapTo(c.x + dx, a.y, c.z + dz, 0F, 0F);
        sp.setOnGround(true);
    }

    private static void putAt(ServerPlayer sp, Vec3 at) {
        sp.snapTo(at.x, at.y, at.z, 0F, 0F);
        sp.setOnGround(true);
    }

    /** 보스가 스스로 패턴 · 원거리 견제를 고르지 않고 걷지 않게 (틀 "pause" — 시험이 부르는 패턴은 틀을 바꿈) */
    private static void freeze(Bosses.Enc enc, int ticks) {
        Bosses.cast(enc, "pause", ticks);
        enc.farAt = FAR;
        BossSurtr.M m = BossSurtr.mem(enc);
        if (m == null) return;
        m.next.replaceAll((k, v) -> FAR);
        m.bigUntil = FAR;
    }

    private static boolean hit(ServerPlayer sp, Entity e, float amount) {
        return e.hurtServer((ServerLevel) e.level(), sp.damageSources().playerAttack(sp), amount);
    }

    private static BlockPos posOf(Arena.Cell q) {
        return new BlockPos(q.x(), q.y(), q.z());
    }

    /** 구조물의 가장 높은 칸 (애드온 looksIntact 와 같이 첫 번째) */
    private static Arena.Cell top(BossSurtr.Struct c) {
        Arena.Cell t = c.cells.get(0);
        for (Arena.Cell q : c.cells) if (q.y() > t.y()) t = q;
        return t;
    }

    /** 구조물의 가운데 밑동 */
    private static Arena.Cell base(BossSurtr.Struct c) {
        for (Arena.Cell q : c.cells) {
            if (q.y() == (int) Math.floor(c.at.y) && Math.abs(q.x() + 0.5 - c.at.x) < 0.6 && Math.abs(q.z() + 0.5 - c.at.z) < 0.6) return q;
        }
        return c.cells.get(0);
    }

    private static boolean placed(ServerLevel level, Arena.Cell q) {
        return level.getBlockState(posOf(q)).getBlock() == ArenaBlocks.state(q).getBlock();
    }

    /** 보스 둘레에 떠 있는 피해 숫자 가운데 mark 가 든 것 (없으면 null — 숫자 상한이 찼을 수 있음) */
    private static String number(ServerLevel level, Entity boss, String mark) {
        for (Display.TextDisplay td : level.getEntitiesOfClass(Display.TextDisplay.class, boss.getBoundingBox().inflate(6), t -> t.entityTags().contains(DamageNumbers.TAG))) {
            String t = td.getText().getString();
            if (t.contains(mark)) return t;
        }
        return null;
    }

    private static void withRand(double v, Runnable run) {
        DoubleSupplier prev = BossKit.rand;
        BossKit.rand = () -> v;
        try {
            run.run();
        } finally {
            BossKit.rand = prev;
        }
    }

    // ------------------------------------------------------------ 시험

    /** 모듈 등록 · 핵의 맞는 상자 3×3.5 · 업화 막대 보스 막대 · 도전 창 ([초월 보스] · 패턴을 깨는 법 · 권장 · 고급 룬 뽑기권 · 초월 장비 재료 · 30분) */
    @GameTest(maxTicks = 40)
    public void moduleAndChallengeForm(GameTestHelper h) {
        h.assertTrue(Bosses.module(KEY) instanceof BossSurtr, "surtr module registered");
        float[] core = MobData.hitbox(BossSurtr.CORE_ID);
        h.assertTrue(core != null && Math.abs(core[0] - 3.0) < 1e-4 && Math.abs(core[1] - 3.5) < 1e-4, "core hit box 3x3.5");
        MobData.Def gauge = MobData.get(BossSurtr.GAUGE_ID);
        h.assertTrue(gauge != null && gauge.bossName() != null && gauge.bossName().contains("무스펠의 업화") && gauge.noDamage().contains("all"), "gauge = boss bar prop");
        h.assertTrue(MobData.get("rpg:boss_surtr").bossRange() == 72 && MobData.get("rpg:boss_surtr").scale() == 10, "surtr hud range 72 · scale 10");
        ServerLevel level = h.getLevel();
        BlockPos pos = new BlockPos(-61_500, 150, 62_000);
        Arena.Altar a = Arena.addAltar(KEY, NpcData.dimId(level), pos);
        a.built = true;
        a.cooldownUntil = 0;
        ServerPlayer sp = RpgGearTests.player(h);
        Forms.HEADLESS.add(sp.getUUID());
        try {
            Arena.onAltarUse.accept(sp, a);
            h.assertTrue(Forms.last(sp) instanceof FormNet.MenuPayload, "challenge form " + Forms.last(sp));
            FormNet.MenuPayload f = (FormNet.MenuPayload) Forms.last(sp);
            String t = f.text();
            h.assertTrue(f.title().contains("수르트에게 도전") && f.buttons().size() == 2, "title " + f.title());
            for (String want : List.of("[초월 보스]", "무스펠헤임의 불의 거인", "Lv.50", "초월 장비를 갖춘 4명 파티", "패턴을 깨는 법", "반격", "무력화", "구조물", "부위 파괴", "고급 룬 뽑기권",
                    "초월 장비 재료", "무스펠의 불꽃 심장", "레바테인 조각", "무스펠의 불덩이", "30분")) {
                h.assertTrue(t.contains(want), "challenge text has " + want + ": " + t);
            }
            Forms.answer(sp, 1, null);
            h.assertTrue(Bosses.encounterAt(a) == null, "cancel = no fight");
            LOG.info("[surtr-test] OK module · core hit box 3x3.5 · gauge boss bar · challenge form ([초월 보스] · notes · rewards)");
        } finally {
            Forms.HEADLESS.remove(sp.getUUID());
            Arena.removeAltar(a);
        }
        RpgGearTests.done(h, sp);
    }

    /** 소환 → 화구 밑 36칸에서 5초 동안 솟아오름 (무적, 깨운 사람 쪽을 봄) → 포효 → 싸움 · 체력 90000 · 필드를 붙잡음 · 사슬 기둥 · 얼음 결정이 서 있음 */
    @GameTest(maxTicks = 320)
    public void summonRisesFromThePit(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 0, hold);
        ServerPlayer sp = fighter(h);
        put(sp, a, 0, 20);
        Bosses.Enc enc = Bosses.summon(a, sp);
        h.assertTrue(enc != null && "summon".equals(enc.state), "summoning");
        h.assertTrue(enc.area != null && Ticking.areaLoaded(level, enc.center, R + 4), "big field held while fighting " + enc.area);
        h.runAfterDelay(62, () -> {
            h.assertTrue("fight".equals(enc.state), "fight " + enc.state);
            BedrockMob b = enc.boss;
            h.assertTrue(b.getMaxHealth() == 90000F && b.getHealth() == 90000F, "hp " + b.getHealth() + "/" + b.getMaxHealth());
            h.assertTrue(Math.abs(b.getBbWidth() - 10) < 1e-3 && Math.abs(b.getBbHeight() - 33) < 1e-3, "giant 10x33 " + b.getBbWidth() + "x" + b.getBbHeight());
            BossSurtr.M m = BossSurtr.mem(enc);
            h.assertTrue(m != null && enc.immune && enc.cast != null && "arrive".equals(enc.cast.id), "rising, immune");
            h.assertTrue(b.getY() < a.y - 20, "under the pit " + (b.getY() - a.y));
            h.assertTrue(Math.abs(BossSurtr.mem(enc).body.yaw - Math.PI / 2) < 1e-6, "faces the starter (south) " + m.body.yaw);
            h.assertTrue(enc.meleeAt > Tasks.now() + 1_000_000, "engine melee off");
            // 사슬 기둥 넷 · 얼음 결정 여덟이 서 있음 (돌 바닥이라 싸움이 시작될 때 모듈이 세움)
            h.assertTrue(m.chains.size() == 4 && m.crystals.size() == 8, "4 chains · 8 crystals " + m.chains.size() + " " + m.crystals.size());
            for (BossSurtr.Struct c : m.chains) h.assertTrue(placed(level, top(c)) && placed(level, base(c)) && c.intact, "chain pillar standing at " + c.at);
            for (BossSurtr.Struct c : m.crystals) h.assertTrue(placed(level, top(c)) && placed(level, base(c)) && placed(level, c.frost.get(0)), "crystal standing at " + c.at);
            h.assertTrue(level.getBlockState(posOf(base(m.chains.get(0)))).is(Blocks.CRYING_OBSIDIAN), "pillar base crying obsidian");
            h.assertTrue(level.getBlockState(posOf(base(m.crystals.get(0)))).is(Blocks.BLUE_ICE), "crystal blue ice");
            float hp = b.getHealth();
            put(sp, a, 0, 12);
            h.assertTrue(hit(sp, b, 100F), "a hit during the rise is taken (immune)");
            h.assertTrue(b.getHealth() == hp, "immune while rising");
            h.runAfterDelay(140, () -> {
                h.assertTrue(!enc.immune && (enc.cast == null || !"arrive".equals(enc.cast.id)), "fight began after the roar");
                h.assertTrue(Math.abs(m.body.h) < 1e-9 && Math.abs(b.getY() - a.y) < 0.01, "on the floor " + (b.getY() - a.y));
                h.assertTrue(enc.area != null, "still held");
                Bosses.endEncounter(enc, "manual");
                h.assertTrue(enc.area == null, "released after the fight");
                LOG.info("[surtr-test] OK summon: rises 36 under the pit (immune) → roar → fight, hp 90000, 10x33, field held, chains · crystals standing");
                finish(h, level, a, hold, sp);
            });
        });
    }

    /** 레바테인 내려찍기: 대상이 붉은 줄 위 → 맞음 + 불길, 칼끝 쪽 레바테인의 핵 (5000 · rpg:part 0 · 두 줄 이름표) → 부수면 부위 파괴 (rpg:sword_broken · 불길 꺼짐 · 보상 재료) */
    @GameTest(maxTicks = 200)
    public void slamCoreBreaksTheSword(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 1, hold);
        ServerPlayer sp = fighter(h);
        put(sp, a, 0, 22);
        Bosses.Enc enc = Bosses.summon(a, sp);
        h.runAfterDelay(62, () -> {
            BossSurtr.skipArrive(enc);
            freeze(enc, 1000);
            BossSurtr.M m = BossSurtr.mem(enc);
            BossSurtr.setBody(enc, enc.center.x, enc.center.z, Math.PI / 2);
            put(sp, a, 0, 22);
            float hp0 = sp.getHealth();
            h.assertTrue(BossSurtr.slam(enc, Tasks.now(), BossKit.fighters(enc)), "slam started");
            h.assertTrue("slam".equals(enc.cast.id) && m.body.want != null, "slam cast");
            double yaw = m.body.want;
            BossSurtr.Rect rect = BossSurtr.slamRect(enc, yaw);
            h.assertTrue(BossSurtr.inRect(sp.position(), rect, 3), "the target is on the red line");
            h.assertTrue(BossSurtr.bindPillar(enc, rect) == null, "no pillar on this line");
            int windup = (int) enc.def.obj("melee").num("windup");
            h.runAfterDelay(windup + 2, () -> {
                h.assertTrue(sp.getHealth() < hp0, "hit by the slam " + hp0 + " → " + sp.getHealth());
                h.assertTrue(m.trails.size() == 1, "the line burns (sword intact)");
                BossSurtr.Core core = m.core;
                h.assertTrue(core != null && "sword".equals(core.kind) && Kit.isType(core.e, BossSurtr.CORE_ID), "sword core exposed " + core);
                BedrockMob ce = core.e;
                h.assertTrue(ce.getHealth() == 5000F && Bosses.propInfoOf(ce) != null && Bosses.propInfoOf(ce).max == 5000, "core hp 5000");
                h.assertTrue(ce.propInt("rpg:part") == 0, "rpg:part 0 (sword)");
                Vec3 s = BossSurtr.shoulderAt(enc, yaw);
                Vec3 want = new Vec3(s.x + Math.cos(yaw) * 33, a.y, s.z + Math.sin(yaw) * 33);
                h.assertTrue(BossKit.hdist(ce.position(), want) < 0.6, "core at the blade tip (shoulder + 33) " + BossKit.hdist(ce.position(), want));
                h.assertTrue(core.labelText() != null && core.labelText().contains("레바테인의 핵") && core.labelText().contains("100%") && core.label() != null, "label " + core.labelText());
                Display.TextDisplay label = core.label();
                put(sp, a, ce.getX() - enc.center.x, ce.getZ() - enc.center.z - 6);
                h.assertTrue(hit(sp, ce, 1000F), "core hit");
                h.assertTrue(Math.abs(m.parts.get("sword").hp - 4000) < 1e-6 && core.labelText().contains("80%"), "part hp kept 4000 · label " + core.labelText());
                h.runAfterDelay(11, () -> {
                    h.assertTrue(hit(sp, ce, 6000F), "breaking hit");
                    h.assertTrue(m.parts.get("sword").broken && m.core == null && ce.isRemoved() && label.isRemoved(), "sword broken, core and label gone");
                    h.assertTrue(m.trails.isEmpty(), "flames out");
                    h.assertTrue(m.brokenOrder.equals(List.of("sword")), "broken order");
                    Map<String, Double> bonus = Bosses.module(KEY).bonusLoot(enc);
                    h.assertTrue(bonus.equals(Map.of("laevateinn_shard", 2.0)), "bonus loot " + bonus);
                    h.assertTrue(enc.parts.get(sp.getUUID()).dmg >= 5000, "damage board counts the core " + enc.parts.get(sp.getUUID()).dmg);
                    h.runAfterDelay(2, () -> {
                        h.assertTrue(enc.boss.propBool("rpg:sword_broken") && !enc.boss.propBool("rpg:fist_broken"), "texture: rpg:sword_broken");
                        LOG.info("[surtr-test] OK slam: red line hit + burning line → sword core 5000 (part 0, label) → broken: rpg:sword_broken, flames out, +2 shards");
                        finish(h, level, a, hold, sp);
                    });
                });
            });
        });
    }

    /** 무스펠의 업화: 업화 막대 (1000 = 남은 업화) 가 피해로 깎임 → 다 깎으면 쓰러짐 (빈틈 ×1.5) · 불꽃 왕관의 핵 → 부수면 rpg:crown_broken. 열기 · 주황 안개 */
    @GameTest(maxTicks = 200)
    public void infernoGaugeAndGroggy(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 2, hold);
        ServerPlayer sp = fighter(h);
        put(sp, a, 0, 16);
        Bosses.Enc enc = Bosses.summon(a, sp);
        h.runAfterDelay(62, () -> {
            BossSurtr.skipArrive(enc);
            freeze(enc, 1000);
            BossSurtr.M m = BossSurtr.mem(enc);
            BossSurtr.setBody(enc, enc.center.x, enc.center.z, Math.PI / 2);
            BedrockMob b = enc.boss;
            BossSurtr.inferno(enc, Tasks.now(), BossKit.fighters(enc));
            BossSurtr.Inferno inf = m.inferno;
            h.assertTrue(inf != null && "inferno".equals(enc.cast.id), "inferno cast");
            h.assertTrue(inf.need == 1800, "need = max(2, 1 fighter) x 900 = 1800 " + inf.need);
            BedrockMob gauge = inf.gauge;
            h.assertTrue(gauge != null && Kit.isType(gauge, BossSurtr.GAUGE_ID) && gauge.getHealth() == 1000F && gauge.bossBar() != null, "gauge prop with a boss bar");
            h.runAfterDelay(4, () -> {
                h.assertTrue(b.prop("rpg:heat") >= 0.15 - 1e-6, "heating " + b.prop("rpg:heat"));
                h.assertTrue(BossSurtr.FOG_INFERNO.equals(m.fogWant), "inferno fog " + m.fogWant);
                put(sp, a, 0, 16);
                h.assertTrue(hit(sp, b, 900F), "hit 900");
                h.assertTrue(Math.abs(inf.left - 900) < 1e-6 && gauge.getHealth() == 500F, "gauge 1000 → 500 " + gauge.getHealth());
                h.runAfterDelay(11, () -> {
                    h.assertTrue(hit(sp, b, 1000F), "hit 1000");
                    h.assertTrue(m.inferno == null && gauge.isRemoved() && m.infernoWins == 1, "inferno broken, gauge gone");
                    h.assertTrue(m.groggyUntil > Tasks.now() && "groggy".equals(enc.cast.id), "groggy");
                    h.assertTrue(m.core != null && "crown".equals(m.core.kind) && m.core.e.propInt("rpg:part") == 2, "crown core");
                    BossSurtr.Bonus o = BossSurtr.openBonus(enc, Tasks.now());
                    h.assertTrue(o != null && BossSurtr.MARK_GROGGY.equals(o.mark()) && o.bonus() == 0.5, "groggy bonus " + o);
                    BedrockMob crown = m.core.e;
                    h.runAfterDelay(11, () -> {
                        float hp = b.getHealth();
                        h.assertTrue(hit(sp, b, 100F), "hit while groggy");
                        h.assertTrue(Math.abs((hp - b.getHealth()) - 150) < 1e-2, "groggy x1.5 " + (hp - b.getHealth()));
                        String num = number(level, b, "빈틈");
                        if (num == null) LOG.warn("[surtr-test] no damage number with 빈틈 (number cap?)");
                        put(sp, a, crown.getX() - enc.center.x, crown.getZ() - enc.center.z + 6);
                        h.assertTrue(hit(sp, crown, 5000F), "crown core hit");
                        h.assertTrue(m.parts.get("crown").broken && crown.isRemoved(), "crown broken");
                        h.runAfterDelay(2, () -> {
                            h.assertTrue(b.propBool("rpg:crown_broken"), "rpg:crown_broken (head flames hidden)");
                            LOG.info("[surtr-test] OK inferno: gauge 1000 → 500 by damage → collapse (groggy x1.5, mark {}) → crown core → rpg:crown_broken", num);
                            finish(h, level, a, hold, sp);
                        });
                    });
                });
            });
        });
    }

    /** 반격: 횡베기의 금빛 창에 스킬 → 패턴이 끊김 (맞지 않음) · 비틀거림 · 3초 ×1.25. 겹친 보너스는 가장 큰 것 하나 (빈틈 > 속박 > 반격) */
    @GameTest(maxTicks = 200)
    public void counterAndLargestBonus(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 3, hold);
        ServerPlayer sp = fighter(h);
        put(sp, a, 0, 28);
        Bosses.Enc enc = Bosses.summon(a, sp);
        h.runAfterDelay(62, () -> {
            BossSurtr.skipArrive(enc);
            freeze(enc, 1000);
            BossSurtr.M m = BossSurtr.mem(enc);
            BossSurtr.setBody(enc, enc.center.x, enc.center.z, Math.PI / 2);
            put(sp, a, 0, 28);
            BedrockMob b = enc.boss;
            h.assertTrue(BossSurtr.sweep(enc, Tasks.now(), BossKit.fighters(enc)), "sweep started (target in the 20~38 band)");
            int windup = (int) enc.def.obj("sweep").num("windup");
            int window = (int) enc.def.obj("sweep").num("window");
            float php = sp.getHealth();
            h.runAfterDelay(windup - window + 1, () -> {
                h.assertTrue(m.counter != null && "sweep".equals(m.counter.kind()), "counter window open (gold)");
                float hp = b.getHealth();
                h.assertTrue(Kit.applySkillDamage(sp, b, 100, false, Kit.Cause.ATTACK, null), "skill lands in the window");
                h.assertTrue(m.counter == null && enc.cast == null && m.counters == 1, "pattern cut by the counter");
                h.assertTrue(enc.staggerUntil > Tasks.now() && m.counterUntil > Tasks.now(), "stagger + counter bonus");
                h.assertTrue(Math.abs((hp - b.getHealth()) - 125) < 1e-2, "counter hit x1.25 " + (hp - b.getHealth()));
                h.runAfterDelay(window + 4, () -> {
                    h.assertTrue(sp.getHealth() >= php, "the cut sweep did not land");
                    float hp2 = b.getHealth();
                    h.assertTrue(hit(sp, b, 100F), "melee during the counter bonus");
                    h.assertTrue(Math.abs((hp2 - b.getHealth()) - 125) < 1e-2, "x1.25 " + (hp2 - b.getHealth()));
                    String num = number(level, b, "반격");
                    if (num == null) LOG.warn("[surtr-test] no damage number with 반격 (number cap?)");
                    // 겹치면 가장 큰 것 하나
                    long now = Tasks.now();
                    m.groggyUntil = now + 200;
                    m.bindUntil = now + 200;
                    BossSurtr.Bonus o = BossSurtr.openBonus(enc, now);
                    h.assertTrue(o != null && o.bonus() == 0.5 && BossSurtr.MARK_GROGGY.equals(o.mark()), "groggy wins " + o);
                    h.runAfterDelay(11, () -> {
                        float hp3 = b.getHealth();
                        h.assertTrue(hit(sp, b, 100F), "hit with three bonuses open");
                        h.assertTrue(Math.abs((hp3 - b.getHealth()) - 150) < 1e-2, "only the largest (x1.5) " + (hp3 - b.getHealth()));
                        m.groggyUntil = 0;
                        BossSurtr.Bonus o2 = BossSurtr.openBonus(enc, Tasks.now());
                        h.assertTrue(o2 != null && o2.bonus() == 0.3 && BossSurtr.MARK_BIND.equals(o2.mark()), "bind (0.3) over counter (0.25) " + o2);
                        m.bindUntil = 0;
                        BossSurtr.Bonus o3 = BossSurtr.openBonus(enc, Tasks.now());
                        h.assertTrue(o3 != null && BossSurtr.MARK_COUNTER.equals(o3.mark()), "counter " + o3);
                        m.counterUntil = 0;
                        h.assertTrue(BossSurtr.openBonus(enc, Tasks.now()) == null, "no bonus");
                        LOG.info("[surtr-test] OK counter: skill in the gold window cuts the sweep → x1.25 ({}); overlapping bonuses → largest only", num);
                        finish(h, level, a, hold, sp);
                    });
                });
            });
        });
    }

    /** 봉인의 사슬 기둥: 칼 줄 위의 기둥이 칼을 붙잡음 (기둥 뒤는 맞지 않음) → 속박 6초 (×1.3) · 칼의 핵 → 풀리면 기둥이 무너짐 → 싸움이 끝나면 되돌림 */
    @GameTest(maxTicks = 420)
    public void chainPillarBindsAndIsRestored(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 4, hold);
        ServerPlayer sp = fighter(h);
        put(sp, a, 0, 20);
        Bosses.Enc enc = Bosses.summon(a, sp);
        h.runAfterDelay(62, () -> {
            BossSurtr.skipArrive(enc);
            freeze(enc, 1000);
            BossSurtr.M m = BossSurtr.mem(enc);
            BossSurtr.Struct c0 = m.chains.get(0);
            Vec3 c = enc.center;
            Vec3 v = new Vec3(c0.at.x - c.x, 0, c0.at.z - c.z).normalize();
            putAt(sp, new Vec3(c0.at.x + v.x * 8, a.y, c0.at.z + v.z * 8));
            BossSurtr.setBody(enc, c.x, c.z, Math.atan2(v.z, v.x));
            Arena.Cell top = top(c0);
            Arena.Cell base = base(c0);
            h.assertTrue(placed(level, top) && level.getBlockState(posOf(base)).is(Blocks.CRYING_OBSIDIAN), "pillar standing");
            float php = sp.getHealth();
            h.assertTrue(BossSurtr.slam(enc, Tasks.now(), BossKit.fighters(enc)), "slam at the player behind the pillar");
            BossSurtr.Pin pin = BossSurtr.bindPillar(enc, BossSurtr.slamRect(enc, m.body.want));
            h.assertTrue(pin != null && pin.c() == c0, "the pillar is on the line");
            int windup = (int) enc.def.obj("melee").num("windup");
            int time = (int) enc.def.obj("bind").num("time");
            h.runAfterDelay(windup + 1, () -> {
                h.assertTrue(m.bindUntil > Tasks.now() && m.binds == 1 && !c0.intact && "bound".equals(enc.cast.id), "chains bind the sword");
                h.assertTrue(sp.getHealth() >= php, "behind the pillar: not hit");
                h.assertTrue(m.core != null && "sword".equals(m.core.kind) && BossKit.hdist(m.core.e.position(), c0.at) < 6, "sword core in front of the pillar");
                h.assertTrue(m.trails.isEmpty(), "no burning line when bound");
                BedrockMob b = enc.boss;
                float hp = b.getHealth();
                h.assertTrue(hit(sp, b, 100F), "hit while bound");
                h.assertTrue(Math.abs((hp - b.getHealth()) - 130) < 1e-2, "bound x1.3 " + (hp - b.getHealth()));
                BossSurtr.Bonus o = BossSurtr.openBonus(enc, Tasks.now());
                h.assertTrue(o != null && BossSurtr.MARK_BIND.equals(o.mark()), "bind mark " + o);
                h.runAfterDelay(time + 2, () -> {
                    h.assertTrue(c0.broken && level.getBlockState(posOf(top)).isAir(), "pillar crumbled " + level.getBlockState(posOf(top)));
                    h.assertTrue(level.getBlockState(posOf(base)).is(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS), "cracked stump " + level.getBlockState(posOf(base)));
                    h.assertTrue(m.core == null, "core covered");
                    Bosses.endEncounter(enc, "manual");
                    h.assertTrue(c0.intact && !c0.broken && placed(level, top) && level.getBlockState(posOf(base)).is(Blocks.CRYING_OBSIDIAN), "pillar restored after the fight");
                    int missing = 0;
                    for (Arena.Cell q : c0.cells) if (!placed(level, q)) missing++;
                    h.assertTrue(missing == 0, "every pillar block back (" + missing + " missing)");
                    LOG.info("[surtr-test] OK chain pillar binds the sword (behind = safe, x1.3), crumbles, restored after the fight");
                    finish(h, level, a, hold, sp);
                });
            });
        });
    }

    /** 라그나로크: 가운데 7칸 자리로 뛰어감 (무적) → 9초 칼을 듦 (붉은 안개) → 불길: 얼음 결정 그림자 안은 삶 (결정이 녹음), 밖은 즉사 → 싸움이 끝나면 결정 되돌림 */
    @GameTest(maxTicks = 480)
    public void ragnarokShadowSaves(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 5, hold);
        ServerPlayer safe = fighter(h);
        ServerPlayer doomed = fighter(h);
        put(safe, a, 0, 20);
        put(doomed, a, -20, 0);
        Bosses.Enc enc = Bosses.summon(a, safe);
        h.runAfterDelay(62, () -> {
            BossSurtr.skipArrive(enc);
            freeze(enc, 2000);
            BossSurtr.M m = BossSurtr.mem(enc);
            withRand(0.0, () -> BossSurtr.ragnarok(enc, Tasks.now(), BossKit.fighters(enc), false));
            BossSurtr.Ragnarok rg = m.ragnarok;
            h.assertTrue(rg != null && "leap".equals(rg.stage) && enc.immune && m.leap != null, "leaping (immune)");
            int leap = (int) enc.def.obj("ragnarok").num("leap");
            int windup = (int) enc.def.obj("ragnarok").num("windup");
            h.runAfterDelay(leap + 2, () -> {
                Vec3 body = BossSurtr.bodyOf(enc);
                h.assertTrue(m.leap == null && !enc.immune && Math.abs(body.x - (enc.center.x + 7)) < 1e-6 && Math.abs(body.z - enc.center.z) < 1e-6, "landed 7 east " + body);
                BossSurtr.Struct cr = m.crystals.get(0);
                Vec3 mid = BossSurtr.shadowMid(enc, cr, body);
                putAt(safe, mid);
                put(doomed, a, -20, 0);
                h.assertTrue(BossSurtr.shadowOf(enc, safe.position(), body) == cr, "safe player in the crystal shadow");
                h.assertTrue(BossSurtr.shadowOf(enc, doomed.position(), body) == null, "doomed player in the open");
                h.runAfterDelay(8, () -> {
                    h.assertTrue("raise".equals(rg.stage) && BossSurtr.FOG_RAGNAROK.equals(m.fogWant), "raised · red fog " + rg.stage + " " + m.fogWant);
                    h.assertTrue(enc.boss.prop("rpg:heat") >= 0.7 - 1e-6, "heat 0.7 " + enc.boss.prop("rpg:heat"));
                    h.runAfterDelay(windup + 90, () -> {
                        h.assertTrue(m.ragnarok == null && m.ragDone == 1, "wave passed");
                        h.assertTrue(rg.saved == 1 && rg.killed == 1, "saved " + rg.saved + " killed " + rg.killed);
                        h.assertTrue(safe.isAlive() && !safe.isDeadOrDying(), "player in the shadow survives");
                        h.assertTrue(doomed.isDeadOrDying() || !doomed.isAlive(), "player in the open burns");
                        h.assertTrue(cr.melted && level.getBlockState(posOf(base(cr))).isAir() && level.getBlockState(posOf(cr.frost.get(0))).is(Blocks.SMOOTH_BASALT),
                                "the sheltering crystal melted (ice → air, frost → smooth basalt)");
                        int intact = 0;
                        for (BossSurtr.Struct c : m.crystals) if (c.intact) intact++;
                        h.assertTrue(intact == 7, "7 crystals left " + intact);
                        Bosses.endEncounter(enc, "manual");
                        h.assertTrue(cr.intact && !cr.melted && level.getBlockState(posOf(base(cr))).is(Blocks.BLUE_ICE) && placed(level, cr.frost.get(0)), "crystal restored after the fight");
                        LOG.info("[surtr-test] OK ragnarok: leap (immune) → raise (red fog, heat 0.7) → wave: shadow saves (crystal melts), open = death → restored");
                        finish(h, level, a, hold, safe, doomed);
                    });
                });
            });
        });
    }

    /** 나머지 패턴: 불의 주먹 (원 · 건틀릿의 핵) → 발 구르기 (옆) → 불비 (발밑 원) → 불의 고리 (땅 = 맞음, 뛰면 넘음) → 찌르기 (가장 먼 사람 줄) */
    @GameTest(maxTicks = 520)
    public void fistStompMeteorsRingsThrust(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 6, hold);
        ServerPlayer p1 = fighter(h);
        ServerPlayer p2 = fighter(h);
        put(p1, a, 0, 15);
        put(p2, a, 0, -30);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.runAfterDelay(62, () -> {
            BossSurtr.skipArrive(enc);
            freeze(enc, 2000);
            BossSurtr.M m = BossSurtr.mem(enc);
            Vec3 c = enc.center;
            BossSurtr.setBody(enc, c.x, c.z, Math.PI / 2);
            // 1) 불의 주먹: 주먹 거리 (몸에서 hypot(13.4, 7) = 15.1칸) 에 선 p1
            double[] fa = BossKit.pair(enc.def.obj("fist"), "at");
            put(p1, a, 0, Math.hypot(fa[0], fa[1]));
            put(p2, a, 0, -30);
            float h1 = p1.getHealth();
            h.assertTrue(BossSurtr.fist(enc, Tasks.now(), List.of(p1)), "fist started");
            Vec3 fistAt = BossSurtr.fistAt(enc, m.body.want);
            h.assertTrue(BossKit.hdist(fistAt, p1.position()) < 1e-6, "fist lands on the player " + BossKit.hdist(fistAt, p1.position()));
            int fw = (int) enc.def.obj("fist").num("windup");
            h.runAfterDelay(fw + 2, () -> {
                h.assertTrue(p1.getHealth() < h1, "fist hit");
                h.assertTrue(m.core != null && "fist".equals(m.core.kind) && m.core.e.propInt("rpg:part") == 1, "gauntlet core");
                // 2) 발 구르기: 옆에 붙은 p1 (몸 오른쪽 5칸)
                enc.cast = null;
                BossSurtr.setBody(enc, c.x, c.z, Math.PI / 2);
                put(p1, a, -5, 0);
                float h2 = p1.getHealth();
                h.assertTrue(BossSurtr.stomp(enc, Tasks.now(), List.of(p1)), "stomp started (flank)");
                int sw = (int) enc.def.obj("stomp").num("windup");
                h.runAfterDelay(sw + 2, () -> {
                    h.assertTrue(p1.getHealth() < h2, "stomp hit");
                    // 3) 불비: 모두의 발밑 + 곳곳 4곳
                    enc.cast = null;
                    put(p1, a, 10, 10);
                    put(p2, a, 0, -30);
                    float h3 = p1.getHealth();
                    float h3b = p2.getHealth();
                    h.assertTrue(BossSurtr.meteors(enc, Tasks.now(), BossKit.fighters(enc)), "meteors started");
                    int mw = (int) enc.def.obj("meteors").num("windup");
                    h.runAfterDelay(mw + 2, () -> {
                        h.assertTrue(p1.getHealth() < h3 && p2.getHealth() < h3b && p1.getRemainingFireTicks() > 0, "meteors hit both + burn");
                        // 4) 불의 고리: p1 땅 (맞음), p2 뛰는 중 (넘음)
                        enc.cast = null;
                        p1.clearFire();
                        p2.clearFire();
                        put(p1, a, 10, 0);
                        put(p2, a, -10, 0);
                        float h4 = p1.getHealth();
                        float h4b = p2.getHealth();
                        h.assertTrue(BossSurtr.rings(enc, Tasks.now(), BossKit.fighters(enc)), "rings started");
                        int plunge = (int) enc.def.obj("rings").num("plunge");
                        h.runAfterDelay(plunge + 2, () -> {
                            h.assertTrue(m.rings.size() == 1, "first ring spreading");
                            p2.setOnGround(false);
                            h.runAfterDelay(34, () -> {
                                h.assertTrue(p1.getHealth() < h4, "grounded player hit by the ring");
                                h.assertTrue(p2.getHealth() >= h4b, "jumping player cleared the ring");
                                // 5) 찌르기: 가장 먼 사람 쪽 줄
                                enc.cast = null;
                                m.rings.clear();
                                BossSurtr.setBody(enc, c.x, c.z, Math.PI / 2);
                                put(p1, a, 0, 14);
                                put(p2, a, 0, 34);
                                p2.setOnGround(true);
                                float h5 = p2.getHealth();
                                h.assertTrue(BossSurtr.thrust(enc, Tasks.now(), BossKit.fighters(enc)), "thrust started");
                                int tw = (int) enc.def.obj("thrust").num("windup");
                                h.runAfterDelay(tw + 2, () -> {
                                    h.assertTrue(p2.getHealth() < h5, "thrust hits the farthest player");
                                    LOG.info("[surtr-test] OK fist (gauntlet core) · stomp · meteors (+burn) · rings (ground hit, jump clears) · thrust (farthest)");
                                    finish(h, level, a, hold, p1, p2);
                                });
                            });
                        });
                    });
                });
            });
        });
    }

    /** 처치: 딜 순위 · 부위 파괴 줄 · 보상 (고급 룬 뽑기권 · 초월 재료 + 부순 부위) · 처치 훅 ("surtr") · 전설 퀘스트 (왕녀) · 재사용 대기 30분 */
    @GameTest(maxTicks = 200)
    public void killBoardLootAndLegend(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 7, hold);
        ServerPlayer p1 = fighter(h);
        ServerPlayer p2 = fighter(h);
        put(p1, a, 0, 15);
        put(p2, a, 0, -15);
        String legend = SideQuests.transcendLegendId(KEY);
        Props.set(p1, "rpg:qs", "{\"" + legend + "\":{\"s\":\"a\",\"n\":0,\"c\":0}}");
        Quests.forgetQuestPlayer(p1.getUUID());
        h.assertTrue("active".equals(Quests.statusOf(p1, Quests.getQuest(legend))), "legend quest active " + Quests.statusOf(p1, Quests.getQuest(legend)));
        List<Object[]> killed = new ArrayList<>();
        BiConsumer<String, List<ServerPlayer>> hook = (k, list) -> killed.add(new Object[] {k, List.copyOf(list)});
        Bosses.addBossKillHook(hook);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.runAfterDelay(62, () -> {
            {
                BossSurtr.skipArrive(enc);
                freeze(enc, 1000);
                BossSurtr.M m = BossSurtr.mem(enc);
                m.parts.get("crown").broken = true;
                m.parts.get("crown").by = "p2";
                m.counters = 2;
                m.infernoWins = 1;
                m.infernoFails = 1;
                m.binds = 1;
                BedrockMob b = enc.boss;
                put(p1, a, 0, 15);
                put(p2, a, 0, -15);
                h.assertTrue(hit(p1, b, 1000F) && hit(p2, b, 500F), "both deal damage");
                int xp0 = p1.totalExperience;
                b.setHealth(50F);
                h.runAfterDelay(11, () -> {
                  try {
                    h.assertTrue(hit(p1, b, 100F), "killing blow");
                    h.assertTrue(Bosses.encounterAt(a) == null && "dead".equals(enc.state) && !b.isAlive(), "surtr down");
                    List<String> board = Bosses.damageBoard(enc);
                    h.assertTrue(board.size() == 3 && board.get(0).contains("[딜 순위]") && board.get(0).contains("수르트") && board.get(0).contains("1,600"), "board " + board);
                    h.assertTrue(board.get(1).contains("1. ") && board.get(1).contains("1,100") && board.get(1).contains("(69%)"), "first " + board.get(1));
                    h.assertTrue(board.get(2).contains("2. ") && board.get(2).contains("500") && board.get(2).contains("(31%)"), "second " + board.get(2));
                    List<String> note = Bosses.module(KEY).killNote(enc);
                    h.assertTrue(note.size() == 2 && note.get(0).equals("§6[부위 파괴] §f1/3 §7— 불꽃 왕관 (p2)"), "note " + note);
                    h.assertTrue(note.get(1).equals("§7반격 2번 · 업화 막음 1/2 · 사슬 속박 1번"), "note " + note.get(1));
                    int em = RpgGearTests.countIn(p1, Items.EMERALD);
                    int adv = RpgGearTests.countIn(p1, RuneItems.ADV_TICKET);
                    int tickets = RpgGearTests.countIn(p1, RuneItems.TICKET);
                    int heart = RpgGearTests.countIn(p1, GearItems.byId("rpg:muspel_heart"));
                    int shard = RpgGearTests.countIn(p1, GearItems.byId("rpg:laevateinn_shard"));
                    h.assertTrue(em >= 48 && em <= 64, "emeralds " + em);
                    h.assertTrue(adv >= 1 && adv <= 2, "advanced rune tickets " + adv);
                    h.assertTrue(tickets >= 2 && tickets <= 4, "rune tickets " + tickets);
                    h.assertTrue(heart >= 2 && heart <= 4, "muspel heart (1~2 +30% +1 crown) " + heart);
                    h.assertTrue(shard >= 2 && shard <= 4, "laevateinn shard " + shard);
                    h.assertTrue(p1.totalExperience - xp0 == 200, "xp " + (p1.totalExperience - xp0));
                    h.assertTrue(RpgGearTests.countIn(p2, RuneItems.ADV_TICKET) >= 1, "p2 rewarded too");
                    // 훅은 서버 전체 (같이 도는 다른 보스 시험의 처치도 옴) — 수르트 것만
                    List<Object[]> mine = new ArrayList<>();
                    for (Object[] k : killed) if (KEY.equals(k[0])) mine.add(k);
                    h.assertTrue(mine.size() == 1 && ((List<?>) mine.get(0)[1]).containsAll(List.of(p1, p2)), "kill hook (quests) surtr " + mine.size() + " of " + killed.size());
                    h.assertTrue("ready".equals(Quests.statusOf(p1, Quests.getQuest(legend))), "legend quest ready " + Quests.statusOf(p1, Quests.getQuest(legend)));
                    h.assertTrue(a.cooldownUntil > System.currentTimeMillis() + 1_790_000, "cooldown 30 min " + (a.cooldownUntil - System.currentTimeMillis()));
                    LOG.info("[surtr-test] OK kill: board {} · note {} · emerald {} · adv ticket {} · heart {} · shard {} · hook · legend ready · 30 min", board, note, em, adv, heart, shard);
                  } finally {
                    Bosses.removeBossKillHook(hook);
                  }
                  finish(h, level, a, hold, p1, p2);
                });
            }
        });
    }
}
