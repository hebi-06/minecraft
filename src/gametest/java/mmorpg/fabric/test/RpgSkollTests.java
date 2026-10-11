package mmorpg.fabric.test;

import java.util.List;
import mmorpg.core.Obj;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.boss.BossSkoll;
import mmorpg.fabric.boss.Bosses;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.mob.BedrockMob;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 5단계 게임 시험 (서버) — 스콜과 하티 (boss_skoll.js): 소환 (깨운 사람에게서 가장 먼 선돌 위 5칸 → 가운데로 뛰어내림 · 체력 27000),
 * 낮 → 밤 → 황혼 (속성 rpg:day · rpg:dusk · 텍스처 번호 rpg:phase, 깨어난 머리 · 약점 쪽 · 원거리 견제 모양), 약점 (잠든 머리 쪽 옆구리 ×1.3 · 노란 "약점") ·
 * 지침 (×1.25 · 하늘색 "지침", 둘 다면 곱함), 해와 달의 심판 (북쪽 가장자리로 물러나 무적 → 제 색 반원 밖은 즉사), 뛰다가 쓰러지면 땅에 · 보상 (보스 재료).
 * 필드는 신전 없이 하늘 (y 150) 의 돌 바닥 + 제단 (선돌 자리는 설계에서). 시험마다 멀리 (x -52_000 - 96 × 번호, z 56_000). 통과하면 [skoll-test] OK 줄.
 */
public final class RpgSkollTests {
    private static final String KEY = "skoll_hati";
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-skoll-test");
    private static final int R = 22;
    private static final long FAR = Long.MAX_VALUE / 4;
    /** 소환 (60틱) + 등장 (울부짖음 20 · 뛰기 20 · 틀 56틱) 이 끝나는 때 */
    private static final int ARRIVED = 122;

    private static Arena.Altar field(ServerLevel level, int slot, String[] hold) {
        return RpgNidhoggTests.field(level, KEY, new BlockPos(-52_000 - slot * 96, 150, 56_000), R, hold);
    }

    /** 스스로 패턴 · 낮밤 바뀜 · 원거리 견제를 고르지 않게 (틀 pause — 걷지도 않음) */
    private static void freeze(Bosses.Enc enc) {
        BossSkoll.M m = BossSkoll.mem(enc);
        m.next.replaceAll((k, v) -> FAR);
        m.bigUntil = FAR;
        m.stance.next = FAR;
        m.judgeDone.add(0.7);
        m.judgeDone.add(0.25);
        enc.farAt = FAR;
        Bosses.cast(enc, "pause", 100000);
    }

    /** 소환: 깨운 사람에게서 가장 먼 선돌 위 (5칸) 에서 울부짖음 → 가운데로 뛰어내림 (포물선) · 체력 27000 · 낮 (rpg:phase 0 · rpg:day 1) */
    @GameTest(environment = "mmorpg:skoll", maxTicks = 300)
    public void skollArrivesFromStone(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 0, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        RpgNidhoggTests.tough(p1, 4);
        RpgNidhoggTests.put(p1, a, 0, 14);
        h.assertTrue(Bosses.module(KEY) instanceof BossSkoll, "skoll module registered");
        Bosses.Enc enc = Bosses.summon(a, p1);
        Vec3 c = Arena.arenaCenter(a);
        List<Arena.Stone> stones = Arena.arenaFeatures(a).stones();
        h.assertTrue(stones.size() == 8, "eight standing stones in the plan " + stones.size());
        Vec3 st = stones.get(0).at();
        for (Arena.Stone s : stones) if (BossKit.hdist(s.at(), p1.position()) > BossKit.hdist(st, p1.position())) st = s.at();
        Vec3 stone = st;
        h.runAfterDelay(63, () -> {
            h.assertTrue("fight".equals(enc.state) && enc.boss != null, "fight " + enc.state);
            BedrockMob b = enc.boss;
            h.assertTrue(b.getMaxHealth() == 27000F && b.getHealth() == 27000F, "hp " + b.getHealth());
            h.assertTrue(BossKit.hdist(b.position(), stone) < 1e-6 && Math.abs(b.getY() - c.y - 5) < 1e-6, "howls on top of the farthest stone "
                    + b.position() + " vs " + stone);
            h.assertTrue(enc.cast != null && "arrive".equals(enc.cast.id), "arrive cast");
            h.assertTrue(b.propInt("rpg:phase") == 0 && b.prop("rpg:day") == 1.0 && b.prop("rpg:dusk") == 0.0, "day look");
            h.assertTrue("sun".equals(BossSkoll.awake(enc)) && BossSkoll.weakSide(enc) == 1, "day: Skoll awake, weak = right (Hati) flank");
            h.assertFalse(enc.mod.farOk(enc, Tasks.now()), "no far shots while arriving");
        });
        h.runAfterDelay(ARRIVED, () -> {
            BedrockMob b = enc.boss;
            h.assertTrue(BossSkoll.leapOf(enc) == null, "landed");
            h.assertTrue(Math.abs(b.getY() - c.y) < 1e-6, "on the floor " + (b.getY() - c.y));
            h.assertTrue(BossKit.hdist(b.position(), c) < 1.5, "jumped down to the center disc " + BossKit.hdist(b.position(), c));
            h.assertTrue(enc.meleeAt == Long.MAX_VALUE, "engine melee off");
            h.assertTrue(BossKit.hdist(enc.home, b.position()) < 1e-6, "engine pose (home = body)");
            LOG.info("[skoll-test] OK summon (hp 27000) on the farthest stone (h 5) → leap to the center, day look");
            RpgNidhoggTests.finish(h, level, a, hold, p1);
        });
    }

    /** 낮 → 밤 → 황혼: 속성 rpg:day 1 → 0 · rpg:dusk 0 → 1, 텍스처 번호 rpg:phase 0 → 1 → 2, 깨어난 머리 · 약점 쪽 · 원거리 견제 모양 (태양 불씨 / 달 서리 / 번갈아) */
    @GameTest(environment = "mmorpg:skoll", maxTicks = 400)
    public void skollDayNightDusk(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 1, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        RpgNidhoggTests.tough(p1, 4);
        RpgNidhoggTests.put(p1, a, 0, 8);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc);
            Obj day = enc.mod.farStyle(enc);
            h.assertTrue("fire".equals(day.str("look")) && "태양 불씨".equals(day.str("name")) && day.num("burn") == 2, "day far = sun ember (fire) " + day);
            BossSkoll.swapStance(enc, Tasks.now());
            h.assertTrue("night".equals(BossSkoll.mem(enc).stance.mode) && "howl".equals(enc.cast.id), "night howl");
        });
        h.runAfterDelay(ARRIVED + 25, () -> {
            BedrockMob b = enc.boss;
            h.assertTrue(b.prop("rpg:day") == 0.0, "rpg:day 0 " + b.prop("rpg:day"));
            h.assertTrue(b.propInt("rpg:phase") == 1, "night texture (phase 1) " + b.propInt("rpg:phase"));
            h.assertTrue("moon".equals(BossSkoll.awake(enc)) && BossSkoll.weakSide(enc) == -1, "night: Hati awake, weak = left (Skoll) flank");
            Obj night = enc.mod.farStyle(enc);
            h.assertTrue("frost".equals(night.str("look")) && "달 서리".equals(night.str("name")) && night.num("burn") == 0
                    && night.obj("effect").str("id").equals("slowness") && night.obj("effect").num("amp") == 1, "night far = moon frost (slowness II) " + night);
            Vec3 from = enc.mod.farFrom(enc, night);
            h.assertTrue(BossKit.hdist(from, BossSkoll.headAt(enc, "moon")) < 1e-6, "frost from Hati's mouth");
            BossSkoll.startDusk(enc, Tasks.now());
            h.assertTrue(BossSkoll.mem(enc).dusk && Math.abs(enc.cdMult - 0.85) < 1e-9, "dusk (cooldowns x0.85)");
        });
        h.runAfterDelay(ARRIVED + 25 + 45, () -> {
            BedrockMob b = enc.boss;
            h.assertTrue(b.prop("rpg:dusk") == 1.0, "rpg:dusk 1 " + b.prop("rpg:dusk"));
            h.assertTrue(b.propInt("rpg:phase") == 2, "dusk texture (phase 2) " + b.propInt("rpg:phase"));
            h.assertTrue("both".equals(BossSkoll.awake(enc)) && BossSkoll.weakSide(enc) == 0, "dusk: both awake, no weak flank");
            String s1 = enc.mod.farStyle(enc).str("look");
            String s2 = enc.mod.farStyle(enc).str("look");
            h.assertTrue(!s1.equals(s2), "dusk far shots alternate " + s1 + " / " + s2);
            LOG.info("[skoll-test] OK day → night → dusk (rpg:day · rpg:dusk · texture phase 0 → 1 → 2, far shot ember / frost / alternating)");
            RpgNidhoggTests.finish(h, level, a, hold, p1);
        });
    }

    /** 약점: 잠든 머리 쪽 옆구리에서 ×1.3 (노란 "약점"), 지친 동안 ×1.25 (하늘색 "지침"), 둘 다 ×1.625 — 황혼에는 약점 없음 */
    @GameTest(environment = "mmorpg:skoll", maxTicks = 400)
    public void skollWeakFlankAndWinded(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 2, hold);
        ServerPlayer east = RpgGearTests.player(h);
        ServerPlayer west = RpgGearTests.player(h);
        ServerPlayer south = RpgGearTests.player(h);
        for (ServerPlayer p : List.of(east, west, south)) RpgNidhoggTests.tough(p, 4);
        RpgNidhoggTests.put(east, a, 8, 0);
        RpgNidhoggTests.put(west, a, -8, 0);
        RpgNidhoggTests.put(south, a, 0, 8);
        Bosses.Enc enc = Bosses.summon(a, south);
        Vec3 c = Arena.arenaCenter(a);
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc);
            // 몸 = 가운데, 북쪽을 봄 → 오른쪽 (하티) = 동쪽. 낮: 하티가 잠듦 → 동쪽 옆구리가 약점
            BossSkoll.setBody(enc, c.x, c.z, -Math.PI / 2);
            RpgNidhoggTests.put(east, a, 8, 0);
            RpgNidhoggTests.put(west, a, -8, 0);
            RpgNidhoggTests.put(south, a, 0, 8);
            BedrockMob b = enc.boss;
            h.assertTrue(BossKit.hdist(b.position(), c) < 1e-6, "body at the center");
            h.assertTrue(BossSkoll.onFlank(enc, east.position()) && !BossSkoll.onFlank(enc, west.position()) && !BossSkoll.onFlank(enc, south.position()),
                    "east = weak flank, west · south not");
            Bosses.Hurt r = enc.mod.hurt(enc, east, 100, null);
            h.assertTrue(Math.abs(r.amount() - 130) < 1e-9 && BossSkoll.WEAK_MARK.equals(r.mark()), "weak flank x1.3 + mark " + r);
            Bosses.Hurt r2 = enc.mod.hurt(enc, west, 100, null);
            h.assertTrue(Math.abs(r2.amount() - 100) < 1e-9 && r2.mark().isEmpty(), "other side plain " + r2);
            float hp0 = b.getHealth();
            h.assertTrue(RpgNidhoggTests.hit(east, b, 100F), "east hit lands");
            h.assertTrue(Math.abs(hp0 - b.getHealth() - 130) < 1e-3, "weak flank took 130 " + (hp0 - b.getHealth()));
            float hp1 = b.getHealth();
            h.assertTrue(RpgNidhoggTests.hit(west, b, 100F), "west hit lands");
            h.assertTrue(Math.abs(hp1 - b.getHealth() - 100) < 1e-3, "plain side took 100 " + (hp1 - b.getHealth()));
            // 숫자 앞 표시 (숫자 상한에 걸리지 않았으면): 노란 약점
            List<String> nums = level.getEntitiesOfClass(Display.TextDisplay.class, b.getBoundingBox().inflate(6), d -> d.entityTags().contains(DamageNumbers.TAG))
                    .stream().map(d -> d.getText().getString()).toList();
            LOG.info("[skoll-test] damage numbers near the boss: {}", nums);
            h.assertTrue(nums.isEmpty() || nums.stream().anyMatch(t -> t.contains("약점")), "a number with the weak mark " + nums);
            // 지침 (달빛 사냥이 끝난 뒤): ×1.25
            BossSkoll.mem(enc).windedUntil = Tasks.now() + 40;
            float hp2 = b.getHealth();
            h.assertTrue(RpgNidhoggTests.hit(south, b, 100F), "south hit lands");
            h.assertTrue(Math.abs(hp2 - b.getHealth() - 125) < 1e-3, "winded took 125 " + (hp2 - b.getHealth()));
            Bosses.Hurt r3 = enc.mod.hurt(enc, south, 100, null);
            h.assertTrue(BossSkoll.WINDED_MARK.equals(r3.mark()), "winded mark " + r3);
        });
        h.runAfterDelay(ARRIVED + 12, () -> {
            BedrockMob b = enc.boss;
            float hp = b.getHealth();
            h.assertTrue(RpgNidhoggTests.hit(east, b, 100F), "east hit again (after iframes)");
            h.assertTrue(Math.abs(hp - b.getHealth() - 162.5) < 1e-3, "weak x winded = 162.5 " + (hp - b.getHealth()));
            Bosses.Hurt r = enc.mod.hurt(enc, east, 100, null);
            h.assertTrue((BossSkoll.WEAK_MARK + BossSkoll.WINDED_MARK).equals(r.mark()), "both marks " + r);
            BossSkoll.mem(enc).windedUntil = 0;
            BossSkoll.startDusk(enc, Tasks.now());
            Bosses.Hurt r2 = enc.mod.hurt(enc, east, 100, null);
            h.assertTrue(Math.abs(r2.amount() - 100) < 1e-9 && r2.mark().isEmpty(), "dusk: no weak flank " + r2);
            LOG.info("[skoll-test] OK weak flank x1.3 (약점) · winded x1.25 (지침) · both x1.625 · none at dusk");
            RpgNidhoggTests.finish(h, level, a, hold, east, west, south);
        });
    }

    /** 해와 달의 심판: 북쪽 가장자리로 물러나 무적 → 표식 → 7초 뒤 제 색 반원 위는 살고, 반대 반원은 즉사 → 무적 풀림 */
    @GameTest(environment = "mmorpg:skoll", maxTicks = 400)
    public void skollJudgeKillsWrongHalf(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 3, hold);
        ServerPlayer good = RpgGearTests.player(h);
        ServerPlayer bad = RpgGearTests.player(h);
        RpgNidhoggTests.tough(good, 4);
        RpgNidhoggTests.put(good, a, 3, 9);
        RpgNidhoggTests.put(bad, a, -3, 9);
        Bosses.Enc enc = Bosses.summon(a, good);
        Vec3 c = Arena.arenaCenter(a);
        Obj J = enc.def.obj("judge");
        int leap = (int) J.num("leap");
        int windup = (int) J.num("windup");
        double keep = enc.def.obj("drive").num("keep");
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc);
            BossSkoll.judge(enc, Tasks.now(), BossKit.fighters(enc), false);
            h.assertTrue(enc.immune && BossSkoll.judgeOf(enc) != null && "judge".equals(enc.cast.id), "judge: immune");
            h.assertFalse(enc.mod.farOk(enc, Tasks.now()), "no far shots during the judgement");
        });
        h.runAfterDelay(ARRIVED + leap + 8, () -> {
            BedrockMob b = enc.boss;
            BossSkoll.Judge G = BossSkoll.judgeOf(enc);
            h.assertTrue(G != null && "mark".equals(G.stage) && G.marks.size() == 2, "marked");
            h.assertTrue(Math.abs(b.getX() - c.x) < 1e-6 && Math.abs(b.getZ() - (c.z - keep)) < 1e-6 && Math.abs(b.getY() - c.y) < 1e-6, "retreated to the north edge "
                    + b.position());
            // 무적 동안 때리면 "무적"
            float hp = b.getHealth();
            RpgNidhoggTests.hit(good, b, 100F);
            h.assertTrue(b.getHealth() == hp, "immune during the judgement");
            for (BossSkoll.JudgeMark mk : G.marks) {
                boolean sun = mk.side.equals("sun");
                // good = 제 반원 (해 = 동쪽, 달 = 서쪽), bad = 반대
                boolean right = mk.p == good;
                double dx = (sun == right) ? 4 : -4;
                RpgNidhoggTests.put(mk.p, a, dx, 1);
            }
        });
        h.runAfterDelay(ARRIVED + leap + 6 + windup + 3, () -> {
            h.assertTrue(BossSkoll.judgeOf(enc) == null && !enc.immune, "verdict → not immune");
            h.assertTrue(!good.isDeadOrDying(), "on its own half survives");
            h.assertTrue(bad.isDeadOrDying(), "on the other half dies");
            LOG.info("[skoll-test] OK judgement: retreat north (immune) → marks → own half survives, other half dies");
            RpgNidhoggTests.finish(h, level, a, hold, good, bad);
        });
    }

    /** 뛰다가 쓰러지면 땅에 내려놓음 → 보상 (에메랄드 28~40 · 경험치 90 · 스콜의 불꽃 송곳니 · 하티의 달빛 갈기) · 재사용 대기 15분 */
    @GameTest(environment = "mmorpg:skoll", maxTicks = 300)
    public void skollKilledMidLeapRewards(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 4, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        RpgNidhoggTests.tough(p1, 4);
        RpgNidhoggTests.put(p1, a, 0, 15);
        Bosses.Enc enc = Bosses.summon(a, p1);
        Vec3 c = Arena.arenaCenter(a);
        int windup = (int) enc.def.obj("pounce").num("windup");
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc);
            RpgNidhoggTests.put(p1, a, 0, 15);
            h.assertTrue(BossSkoll.pounce(enc, Tasks.now(), BossKit.fighters(enc)), "pounce on the far player");
        });
        h.runAfterDelay(ARRIVED + windup + 6, () -> {
            BedrockMob b = enc.boss;
            h.assertTrue(BossSkoll.leapOf(enc) != null && b.getY() - c.y > 1, "mid-leap " + (b.getY() - c.y));
            int xp0 = p1.totalExperience;
            b.setHealth(10F);
            h.assertTrue(RpgNidhoggTests.hit(p1, b, 50F), "killing blow");
            h.assertTrue(Bosses.encounterAt(a) == null && "dead".equals(enc.state), "encounter over");
            h.assertTrue(Math.abs(b.getY() - c.y) < 1e-6, "put on the ground before dying " + (b.getY() - c.y));
            int em = RpgGearTests.countIn(p1, Items.EMERALD);
            int fang = RpgGearTests.countIn(p1, GearItems.byId("rpg:skoll_fang"));
            int mane = RpgGearTests.countIn(p1, GearItems.byId("rpg:hati_mane"));
            h.assertTrue(em >= 28 && em <= 40, "emeralds " + em);
            h.assertTrue(fang >= 2 && fang <= 4 && mane >= 2 && mane <= 4, "boss materials " + fang + " / " + mane);
            h.assertTrue(p1.totalExperience - xp0 == 90, "xp " + (p1.totalExperience - xp0));
            h.assertTrue(a.cooldownUntil > System.currentTimeMillis() + 890_000, "cooldown 15 min");
            LOG.info("[skoll-test] OK killed mid-leap → grounded, rewards (emerald {} · fang {} · mane {} · xp 90) · cooldown", em, fang, mane);
            RpgNidhoggTests.finish(h, level, a, hold, p1);
        });
    }
}
