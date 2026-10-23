package mmorpg.fabric.test;

import java.util.List;
import java.util.function.DoubleSupplier;
import mmorpg.core.Obj;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.boss.BossVoidKnight;
import mmorpg.fabric.boss.Bosses;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.mob.MobData;
import mmorpg.fabric.RpgPlayers;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.33.0 게임 시험 (서버) — 공허 기사 (BossVoidKnight, Lv.55): 등장 (균열에서 — 무적 · 체력 450,000 · 공허 붕괴까지 막대), 움직임 (달려듦 · 둘레 돌기 · 뒤로 뜀),
 * 내려베기 (선딜레이 동안은 안 맞음 → 부채꼴 안만 · 빈틈 ×1.3 "빈틈"), 돌진 찌르기 (줄 위만 · 몸이 줄 끝으로), 도약 내려찍기 (공중 → 원 · 빈틈), 회전 베기 (원 안만),
 * 균열 걸음 (안 보임 · 무적 → 등 뒤 → 베기 + 어둠), 3연격 (세 번 · 걸음 · 몸을 뚫지 않음), 각성 (체력 절반 → 무적 포효 → 빨라짐 · 텍스처 · 공허 검기),
 * 공허 참 (안전 원 안은 무사 · 밖은 피해), 제한 시간 (막대 붉게 → 공허 붕괴 즉사), 처치 (보상 · 보스 재료 · 재사용 20분 · 막대 치움).
 * 필드는 신전 없이 하늘 (y 150) 의 돌 바닥 + 제단. 시험마다 멀리 (x -52_000 - 96 × 번호, z 60_000). 통과하면 [voidknight-test] OK 줄.
 */
public final class RpgVoidKnightTests {
    private static final String KEY = "void_knight";
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-voidknight-test");
    private static final int R = 22;
    private static final long FAR = Long.MAX_VALUE / 4;
    /** 소환 (60틱) + 등장 (ARRIVE 틱) 이 끝나는 때 */
    private static final int ARRIVED = 60 + BossVoidKnight.ARRIVE + 4;
    private static final double SOUTH = Math.PI / 2;

    private static Arena.Altar field(ServerLevel level, int slot, String[] hold) {
        return RpgNidhoggTests.field(level, KEY, new BlockPos(-52_000 - slot * 96, 150, 60_000), R, hold);
    }

    /** 체력 스탯을 크게 (큰 피해도 버팀 — 받은 피해를 잼) · 가득 채움 */
    private static ServerPlayer fighter(GameTestHelper h, int vit) {
        ServerPlayer sp = RpgGearTests.player(h);
        RpgPlayers.setStat(sp, "vit", vit);
        sp.setHealth(sp.getMaxHealth());
        return sp;
    }

    /** 스스로 패턴 · 원거리 견제 · 공허 참을 고르지 않게 (pause = 틀 — 걷지도 않음) */
    private static void freeze(Bosses.Enc enc, boolean pause) {
        BossVoidKnight.M m = BossVoidKnight.mem(enc);
        m.next.replaceAll((k, v) -> FAR);
        m.nextAttack = FAR;
        m.crossAt = FAR;
        enc.farAt = FAR;
        if (pause) Bosses.cast(enc, "pause", 100000);
    }

    /** 틀을 비우고 패턴을 바로 부름 */
    private static void unpause(Bosses.Enc enc) {
        enc.cast = null;
    }

    private static void fresh(ServerPlayer sp) {
        RpgMonsterTests.fresh(sp);
    }

    private static void near(GameTestHelper h, double got, double want, double eps, String what) {
        RpgMonsterTests.near(h, got, want, eps, what);
    }

    /** 보스 쪽에서 오는 공격 (난이도 배율) */
    private static double scaled(GameTestHelper h, double a) {
        return RpgMonsterTests.scaled(h, a);
    }

    private static Obj def() {
        return Arena.BOSSES.obj(KEY);
    }

    // ------------------------------------------------------------ 시험

    /** 등장: 소환 → 가운데 균열에서 비집고 나옴 (무적 · 틀 arrive) · 체력 450,000 · 엔진 평타 없음 · 공허 붕괴까지 막대 (10분) · 보스 막대 이름 */
    @GameTest(environment = "mmorpg:voidknight", maxTicks = 300)
    public void voidKnightArrives(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 0, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        RpgNidhoggTests.tough(p1, 4);
        RpgNidhoggTests.put(p1, a, 0, 10);
        h.assertTrue(Bosses.module(KEY) instanceof BossVoidKnight, "void knight module registered");
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.assertTrue(enc != null, "summoned");
        Vec3 c = Arena.arenaCenter(a);
        h.runAfterDelay(63, () -> {
            h.assertTrue("fight".equals(enc.state) && enc.boss != null, "fight " + enc.state);
            BedrockMob b = enc.boss;
            h.assertTrue(b.getMaxHealth() == 450000F && b.getHealth() == 450000F, "hp " + b.getHealth());
            h.assertTrue(enc.cast != null && "arrive".equals(enc.cast.id) && enc.immune, "arrives from the rift (immune)");
            h.assertTrue(BossKit.hdist(b.position(), c) < 1e-6 && Math.abs(b.getY() - c.y) < 1e-6, "at the center " + b.position());
            h.assertTrue(enc.meleeAt == Long.MAX_VALUE, "engine melee off");
            long left = BossVoidKnight.timeLeft(enc, Tasks.now());
            h.assertTrue(left > 12000 - 10 && left <= 12000, "10 minute limit " + left);
            h.assertTrue(BossVoidKnight.timerOf(enc) != null, "collapse timer bar");
            String label = BossKit.bossLabel(55, "균열을 걷는 자", "공허 기사");
            h.assertTrue(label.equals(MobData.get("rpg:boss_void_knight").bossName()), "boss bar name " + MobData.get("rpg:boss_void_knight").bossName());
        });
        h.runAfterDelay(ARRIVED + 2, () -> {
            h.assertTrue(!enc.immune, "hittable after arriving");
            ServerBossEvent bar = BossVoidKnight.timerOf(enc);
            h.assertTrue(bar.getPlayers().contains(p1), "timer bar shown to the fighter");
            h.assertTrue(bar.getName().getString().contains("공허 붕괴까지") && bar.getColor() == BossEvent.BossBarColor.PURPLE, "timer " + bar.getName().getString());
            LOG.info("[voidknight-test] OK summon (hp 450000) from the rift (immune) → fight, collapse timer bar 10:00");
            RpgNidhoggTests.finish(h, level, a, hold, p1);
        });
    }

    /** 움직임: 멀면 달려듦 (rpg:gait) → 가까우면 둘레를 돎 (rpg:strafe · 거리 유지) → 너무 붙으면 뒤로 뜀 */
    @GameTest(environment = "mmorpg:voidknight", maxTicks = 400)
    public void voidKnightMovesAround(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 1, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        RpgNidhoggTests.tough(p1, 4);
        RpgNidhoggTests.put(p1, a, 0, 14);
        Bosses.Enc enc = Bosses.summon(a, p1);
        Vec3 c = Arena.arenaCenter(a);
        double[] d0 = new double[1];
        double[] ang = new double[1];
        double[] strafe = new double[1];
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc, false);
            BossVoidKnight.setBody(enc, c.x, c.z, SOUTH);
            RpgNidhoggTests.put(p1, a, 0, 14);
            d0[0] = BossKit.hdist(enc.boss.position(), p1.position());
        });
        h.runAfterDelay(ARRIVED + 12, () -> {
            BossVoidKnight.M m = BossVoidKnight.mem(enc);
            double d = BossKit.hdist(enc.boss.position(), p1.position());
            h.assertTrue(d < d0[0] - 3, "dashes toward the far player " + d0[0] + " → " + d);
            m.flipAt = FAR; // 도는 쪽을 바꾸지 않게 (돈 각도를 재려고)
            h.assertTrue(m.dashing && enc.boss.prop("rpg:gait") > 0.5, "dash look (gait " + enc.boss.prop("rpg:gait") + ")");
            Vec3 b = enc.boss.position();
            p1.snapTo(b.x, a.y, b.z + 4.6, 0F, 0F);
        });
        h.runAfterDelay(ARRIVED + 30, () -> {
            Vec3 b = enc.boss.position();
            ang[0] = Math.atan2(b.z - p1.getZ(), b.x - p1.getX());
        });
        for (int t = 31; t <= 70; t++) h.runAfterDelay(ARRIVED + t, () -> strafe[0] = Math.max(strafe[0], Math.abs(enc.boss.prop("rpg:strafe"))));
        h.runAfterDelay(ARRIVED + 71, () -> {
            Vec3 b = enc.boss.position();
            double d = BossKit.hdist(b, p1.position());
            double a1 = Math.atan2(b.z - p1.getZ(), b.x - p1.getX());
            double turned = Math.abs(Math.atan2(Math.sin(a1 - ang[0]), Math.cos(a1 - ang[0])));
            h.assertTrue(d > 2.6 && d < 6.8, "keeps its distance while circling " + d);
            h.assertTrue(turned > Math.toRadians(10), "circles around the player " + Math.toDegrees(turned) + "°");
            h.assertTrue(strafe[0] > 0.3, "strafe look " + strafe[0]);
            BossVoidKnight.mem(enc).backAt = 0;
            p1.snapTo(b.x + 1.2, a.y, b.z, 0F, 0F);
        });
        h.runAfterDelay(ARRIVED + 82, () -> {
            double d = BossKit.hdist(enc.boss.position(), p1.position());
            h.assertTrue(d > 3.2, "hops back when crowded " + d);
            h.assertTrue(BossVoidKnight.mem(enc).backAt > Tasks.now(), "backstep cooldown");
            LOG.info("[voidknight-test] OK dash (far) → circle at ~4.6 (strafe) → backstep (crowded)");
            RpgNidhoggTests.finish(h, level, a, hold, p1);
        });
    }

    /** 내려베기: 선딜레이 동안은 안 맞음 → 앞 부채꼴 안만 260 (뒤는 무사) → 빈틈 (받는 피해 ×1.3 · "빈틈") → 빈틈 끝 */
    @GameTest(environment = "mmorpg:voidknight", maxTicks = 400)
    public void voidKnightCleaveAndOpening(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 2, hold);
        ServerPlayer p1 = fighter(h, 900);
        ServerPlayer p2 = fighter(h, 900);
        RpgNidhoggTests.put(p1, a, 0, 4);
        RpgNidhoggTests.put(p2, a, 0, -5);
        Bosses.Enc enc = Bosses.summon(a, p1);
        Vec3 c = Arena.arenaCenter(a);
        Obj d = def().obj("cleave");
        int wu = (int) d.num("windup");
        int open = (int) d.num("open");
        float[] hp = new float[2];
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc, true);
            BossVoidKnight.setBody(enc, c.x, c.z, SOUTH);
            RpgNidhoggTests.put(p1, a, 0, 4);
            RpgNidhoggTests.put(p2, a, 0, -5);
            p1.setHealth(p1.getMaxHealth());
            p2.setHealth(p2.getMaxHealth());
            fresh(p1);
            fresh(p2);
            hp[0] = p1.getHealth();
            hp[1] = p2.getHealth();
            unpause(enc);
            h.assertTrue(BossVoidKnight.cleave(enc, Tasks.now(), BossKit.fighters(enc)), "cleave starts");
            h.assertTrue(BossVoidKnight.windup(enc, d.num("windup")) == wu, "phase 1 windup " + wu);
        });
        h.runAfterDelay(ARRIVED + wu - 1, () -> h.assertTrue(p1.getHealth() == hp[0], "not hit during the windup"));
        h.runAfterDelay(ARRIVED + wu + 1, () -> {
            near(h, hp[0] - p1.getHealth(), scaled(h, d.num("damage")), 1e-3, "cleave hits the fan");
            h.assertTrue(p2.getHealth() == hp[1], "behind the knight is safe");
            long now = Tasks.now();
            h.assertTrue(BossVoidKnight.opened(enc, now), "opening after the cleave");
            Bosses.Hurt r = enc.mod.hurt(enc, p1, 100, null);
            h.assertTrue(r != null && Math.abs(r.amount() - 130) < 1e-9 && BossVoidKnight.OPEN_MARK.equals(r.mark()), "opening x1.3 + mark " + r);
        });
        h.runAfterDelay(ARRIVED + wu + open + 2, () -> {
            h.assertTrue(!BossVoidKnight.opened(enc, Tasks.now()) && enc.mod.hurt(enc, p1, 100, null) == null, "opening over");
            LOG.info("[voidknight-test] OK cleave: windup safe → fan 260 (behind safe) → opening x1.3 for {} ticks", open);
            RpgNidhoggTests.finish(h, level, a, hold, p1, p2);
        });
    }

    /** 돌진 찌르기 (줄 위만 200 · 몸이 줄 끝으로) → 도약 내려찍기 (공중 → 떨어진 원 230 · 빈틈) */
    @GameTest(environment = "mmorpg:voidknight", maxTicks = 500)
    public void voidKnightThrustAndLeap(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 3, hold);
        ServerPlayer p1 = fighter(h, 900);
        ServerPlayer p2 = fighter(h, 900);
        RpgNidhoggTests.put(p1, a, 0, 9);
        RpgNidhoggTests.put(p2, a, 2.5, -1);
        Bosses.Enc enc = Bosses.summon(a, p1);
        Vec3 c = Arena.arenaCenter(a);
        Obj t = def().obj("thrust");
        Obj l = def().obj("leap");
        int tw = (int) t.num("windup");
        int time = (int) t.num("time");
        int lw = (int) l.num("windup");
        int jump = (int) l.num("jump");
        float[] hp = new float[2];
        int T2 = ARRIVED + tw + time + 30;
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc, true);
            BossVoidKnight.setBody(enc, c.x, c.z, SOUTH);
            fresh(p1);
            fresh(p2);
            hp[0] = p1.getHealth();
            hp[1] = p2.getHealth();
            unpause(enc);
            h.assertTrue(BossVoidKnight.thrust(enc, Tasks.now(), BossKit.fighters(enc)), "thrust starts");
        });
        h.runAfterDelay(ARRIVED + tw + time + 1, () -> {
            near(h, hp[0] - p1.getHealth(), scaled(h, t.num("damage")), 1e-3, "thrust hits the line");
            h.assertTrue(p2.getHealth() == hp[1], "off the line is safe");
            Vec3 end = new Vec3(c.x, c.y, c.z + Math.min(t.num("length"), 9 + 3));
            h.assertTrue(BossKit.hdist(enc.boss.position(), end) < 0.5, "lunged to the end of the line " + enc.boss.position());
        });
        h.runAfterDelay(T2, () -> {
            freeze(enc, true);
            BossVoidKnight.setBody(enc, c.x, c.z, SOUTH);
            RpgNidhoggTests.put(p1, a, 0, 10);
            RpgNidhoggTests.put(p2, a, 2.5, -1);
            p1.setHealth(p1.getMaxHealth());
            fresh(p1);
            hp[0] = p1.getHealth();
            unpause(enc);
            h.assertTrue(BossVoidKnight.leap(enc, Tasks.now(), BossKit.fighters(enc)), "leap starts (only the far player is a target)");
        });
        h.runAfterDelay(T2 + lw - jump + jump / 2, () -> h.assertTrue(BossVoidKnight.heightOf(enc) > 1, "airborne " + BossVoidKnight.heightOf(enc)));
        h.runAfterDelay(T2 + lw + 1, () -> {
            Vec3 spot = new Vec3(c.x, c.y, c.z + 10);
            h.assertTrue(BossKit.hdist(enc.boss.position(), spot) < 0.3 && BossVoidKnight.heightOf(enc) == 0, "landed on the marked spot " + enc.boss.position());
            near(h, hp[0] - p1.getHealth(), scaled(h, l.num("damage")), 1e-3, "slam hits the circle");
            h.assertTrue(BossVoidKnight.opened(enc, Tasks.now()), "opening after the slam");
            LOG.info("[voidknight-test] OK thrust (line only, lunges to the end) · leap (airborne → slam on the circle → opening)");
            RpgNidhoggTests.finish(h, level, a, hold, p1, p2);
        });
    }

    /** 회전 베기 (원 안만 210) → 균열 걸음 (사라짐 — 안 보임 · 무적 → 대상 등 뒤 2.4칸 → 가로 베기 190 + 어둠) */
    @GameTest(environment = "mmorpg:voidknight", maxTicks = 500)
    public void voidKnightSpinAndBlink(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 4, hold);
        ServerPlayer p1 = fighter(h, 900);
        ServerPlayer p2 = fighter(h, 900);
        RpgNidhoggTests.put(p1, a, 0, 3);
        RpgNidhoggTests.put(p2, a, 0, -8.6);
        Bosses.Enc enc = Bosses.summon(a, p1);
        Vec3 c = Arena.arenaCenter(a);
        Obj s = def().obj("spin");
        Obj b = def().obj("blink");
        int sw = (int) s.num("windup");
        int vanish = (int) b.num("vanish");
        int hidden = (int) b.num("hidden");
        int bw = (int) b.num("windup");
        float[] hp = new float[2];
        int T2 = ARRIVED + sw + (int) s.num("open") + 20;
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc, true);
            BossVoidKnight.setBody(enc, c.x, c.z, SOUTH);
            fresh(p1);
            fresh(p2);
            hp[0] = p1.getHealth();
            hp[1] = p2.getHealth();
            unpause(enc);
            h.assertTrue(BossVoidKnight.spin(enc, Tasks.now(), BossKit.fighters(enc)), "spin starts");
        });
        h.runAfterDelay(ARRIVED + sw + 1, () -> {
            near(h, hp[0] - p1.getHealth(), scaled(h, s.num("damage")), 1e-3, "spin hits inside the ring");
            h.assertTrue(p2.getHealth() == hp[1], "outside the ring is safe");
        });
        h.runAfterDelay(T2, () -> {
            freeze(enc, true);
            BossVoidKnight.setBody(enc, c.x, c.z, SOUTH);
            // p1 은 북쪽 (-z) 을 봄 → 등 뒤 = +z 쪽 2.4칸. p2 는 보스 곁 (가장 가까운 사람 — 균열 걸음은 다른 사람을 노림)
            Vec3 cc = Arena.arenaCenter(a);
            p1.snapTo(cc.x, a.y, cc.z + 8, 180F, 0F);
            RpgNidhoggTests.put(p2, a, 2, -2);
            p1.setHealth(p1.getMaxHealth());
            fresh(p1);
            hp[0] = p1.getHealth();
            unpause(enc);
            h.assertTrue(BossVoidKnight.blink(enc, Tasks.now(), BossKit.fighters(enc)), "blink starts");
        });
        h.runAfterDelay(T2 + vanish + 2, () -> {
            h.assertTrue(enc.boss.isInvisible() && enc.immune && BossVoidKnight.mem(enc).vanished, "in the rift: invisible + immune");
            float hp0 = enc.boss.getHealth();
            RpgNidhoggTests.hit(p2, enc.boss, 50F);
            h.assertTrue(enc.boss.getHealth() == hp0, "cannot be hit in the rift");
        });
        h.runAfterDelay(T2 + vanish + hidden + 1, () -> {
            Vec3 behind = new Vec3(c.x, c.y, c.z + 8 + b.num("behind"));
            h.assertTrue(!enc.boss.isInvisible() && !enc.immune, "out of the rift");
            h.assertTrue(BossKit.hdist(enc.boss.position(), behind) < 0.3, "behind the target " + enc.boss.position() + " vs " + behind);
        });
        h.runAfterDelay(T2 + vanish + hidden + bw + 1, () -> {
            near(h, hp[0] - p1.getHealth(), scaled(h, b.num("damage")), 1e-3, "rift slash");
            h.assertTrue(p1.hasEffect(MobEffects.DARKNESS), "darkness");
            LOG.info("[voidknight-test] OK spin (ring only) · rift step (invisible + immune → behind the target → slash + darkness)");
            RpgNidhoggTests.finish(h, level, a, hold, p1, p2);
        });
    }

    /** 3연격: 한 걸음씩 세 번 (120 · 120 · 240) · 대상 1.8칸 앞에서 멈춤 (뚫고 지나가지 않음) · 빈틈 */
    @GameTest(environment = "mmorpg:voidknight", maxTicks = 400)
    public void voidKnightCombo(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 5, hold);
        ServerPlayer p1 = fighter(h, 900);
        RpgNidhoggTests.put(p1, a, 0, 3.6);
        Bosses.Enc enc = Bosses.summon(a, p1);
        Vec3 c = Arena.arenaCenter(a);
        Obj d = def().obj("combo");
        List<Double> wus = d.numbersOrNull("windup");
        List<Double> dmg = d.numbersOrNull("damage");
        int w0 = (int) Math.round(wus.get(0));
        int w1 = (int) Math.round(wus.get(1));
        int w2 = (int) Math.round(wus.get(2));
        float[] hp = new float[1];
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc, true);
            BossVoidKnight.setBody(enc, c.x, c.z, SOUTH);
            RpgNidhoggTests.put(p1, a, 0, 3.6);
            fresh(p1);
            hp[0] = p1.getHealth();
            unpause(enc);
            h.assertTrue(BossVoidKnight.combo(enc, Tasks.now(), BossKit.fighters(enc)), "combo starts");
        });
        h.runAfterDelay(ARRIVED + w0 + 1, () -> {
            near(h, hp[0] - p1.getHealth(), scaled(h, dmg.get(0)), 1e-3, "first slash");
            hp[0] = p1.getHealth();
        });
        h.runAfterDelay(ARRIVED + w0 + 4, () -> fresh(p1));
        h.runAfterDelay(ARRIVED + w0 + w1 + 1, () -> {
            near(h, hp[0] - p1.getHealth(), scaled(h, dmg.get(1)), 1e-3, "second slash");
            hp[0] = p1.getHealth();
        });
        h.runAfterDelay(ARRIVED + w0 + w1 + 4, () -> fresh(p1));
        h.runAfterDelay(ARRIVED + w0 + w1 + w2 + 1, () -> {
            near(h, hp[0] - p1.getHealth(), scaled(h, dmg.get(2)), 1e-3, "third (heavy) slash");
            double gap = BossKit.hdist(enc.boss.position(), p1.position());
            h.assertTrue(gap >= 1.7, "stops in front of the target (no walking through) " + gap);
            h.assertTrue(BossVoidKnight.opened(enc, Tasks.now()), "opening after the third slash");
            LOG.info("[voidknight-test] OK combo 120 · 120 · 240 stepping in (stops {} in front) → opening", String.format(java.util.Locale.ROOT, "%.2f", gap));
            RpgNidhoggTests.finish(h, level, a, hold, p1);
        });
    }

    /** 각성: 체력 절반 아래 → 포효 (무적) → 빨라짐 (선딜레이 18 → 15 · 재사용 ×0.8 · rpg:phase 1) → 내려베기의 공허 검기가 줄을 따라 날아가 먼 사람을 벰 (110) */
    @GameTest(environment = "mmorpg:voidknight", maxTicks = 500)
    public void voidKnightAwakensWithWaves(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 6, hold);
        ServerPlayer p1 = fighter(h, 900);
        ServerPlayer p2 = fighter(h, 900);
        RpgNidhoggTests.put(p1, a, 0, 3);
        RpgNidhoggTests.put(p2, a, 0, 12);
        Bosses.Enc enc = Bosses.summon(a, p1);
        Vec3 c = Arena.arenaCenter(a);
        Obj P = def().obj("phase2");
        Obj W = def().obj("wave");
        int roar = (int) P.num("roar");
        int wu = (int) Math.round(def().obj("cleave").num("windup") * P.num("windup"));
        float[] hp = new float[2];
        int T2 = ARRIVED + roar + 12;
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc, true);
            BedrockMob b = enc.boss;
            b.setHealth(b.getMaxHealth() * 0.5F + 10);
            RpgNidhoggTests.hit(p1, b, 100F);
            h.assertTrue(BossVoidKnight.mem(enc).awakenPending, "half hp → awakening next");
            unpause(enc);
        });
        h.runAfterDelay(ARRIVED + 3, () -> h.assertTrue(enc.cast != null && "awaken".equals(enc.cast.id) && enc.immune, "roars (immune)"));
        h.runAfterDelay(ARRIVED + 2 + roar + 2, () -> {
            BossVoidKnight.M m = BossVoidKnight.mem(enc);
            h.assertTrue(m.phase2 && !enc.immune && Math.abs(enc.cdMult - P.num("cd")) < 1e-9, "awakened (cooldowns x" + enc.cdMult + ")");
            h.assertTrue(enc.boss.propInt("rpg:phase") == 1, "awakened texture");
            h.assertTrue(BossVoidKnight.windup(enc, 18) == wu, "faster windup " + BossVoidKnight.windup(enc, 18));
            h.assertTrue(m.crossAt < FAR && m.crossAt > Tasks.now(), "void cross scheduled");
        });
        h.runAfterDelay(T2, () -> {
            freeze(enc, true);
            BossVoidKnight.setBody(enc, c.x, c.z, SOUTH);
            RpgNidhoggTests.put(p1, a, 0, 3);
            RpgNidhoggTests.put(p2, a, 0, 12);
            p1.setHealth(p1.getMaxHealth());
            p2.setHealth(p2.getMaxHealth());
            fresh(p1);
            fresh(p2);
            hp[0] = p1.getHealth();
            hp[1] = p2.getHealth();
            unpause(enc);
            h.assertTrue(BossVoidKnight.cleave(enc, Tasks.now(), BossKit.fighters(enc)), "cleave (awakened)");
        });
        h.runAfterDelay(T2 + wu + 1, () -> {
            h.assertTrue(BossVoidKnight.waveCount(enc) >= 1, "void wave flies out");
            near(h, hp[0] - p1.getHealth(), scaled(h, def().obj("cleave").num("damage")), 1e-3, "cleave still hits the fan");
            h.assertTrue(p2.getHealth() == hp[1], "the wave has not reached the far player yet");
        });
        double start = def().obj("cleave").num("reach") - 0.5;
        int reach = (int) Math.ceil((12 - start) / W.num("speed")) + 2;
        h.runAfterDelay(T2 + wu + reach, () -> {
            near(h, hp[1] - p2.getHealth(), W.num("damage"), 1e-3, "the wave cuts the far player in line");
            LOG.info("[voidknight-test] OK awaken at half hp (immune roar) → faster (windup {} · cd x{}), void wave reaches 12 blocks", wu, P.num("cd"));
            RpgNidhoggTests.finish(h, level, a, hold, p1, p2);
        });
    }

    /** 공허 참 (각성 뒤): 가운데로 뛰어 칼을 세움 (무적) → 금빛 안전 원 넷 (가운데에서 12.5칸 · 45° 마다) 안은 무사, 밖은 520 → 빈틈 */
    @GameTest(environment = "mmorpg:voidknight", maxTicks = 500)
    public void voidKnightVoidCross(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 7, hold);
        ServerPlayer p1 = fighter(h, 900);
        ServerPlayer p2 = fighter(h, 900);
        Bosses.Enc enc = Bosses.summon(a, p1);
        Vec3 c = Arena.arenaCenter(a);
        Obj C = def().obj("cross");
        int jump = (int) C.num("jump");
        int wu = (int) C.num("windup");
        float[] hp = new float[2];
        DoubleSupplier prev = BossKit.rand;
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc, true);
            BossVoidKnight.mem(enc).phase2 = true;
            BossVoidKnight.setBody(enc, c.x + 5, c.z, SOUTH);
            double k = C.num("dist") / Math.sqrt(2);
            RpgNidhoggTests.put(p1, a, k, k);
            RpgNidhoggTests.put(p2, a, 0, C.num("dist"));
            fresh(p1);
            fresh(p2);
            hp[0] = p1.getHealth();
            hp[1] = p2.getHealth();
            unpause(enc);
            BossKit.rand = () -> 0.0;
            try {
                h.assertTrue(BossVoidKnight.cross(enc, Tasks.now(), BossKit.fighters(enc)), "void cross starts");
            } finally {
                BossKit.rand = prev;
            }
            BossVoidKnight.Cross x = BossVoidKnight.mem(enc).cross;
            h.assertTrue(x != null && x.safe().size() == (int) C.num("count"), "safe circles " + (x == null ? null : x.safe()));
            h.assertTrue(BossKit.hdist(x.safe().get(0), new Vec3(c.x + k, c.y, c.z + k)) < 1e-6, "first safe circle at 45° " + x.safe().get(0));
            h.assertTrue(enc.immune, "immune while channelling");
        });
        h.runAfterDelay(ARRIVED + jump + 2, () -> h.assertTrue(BossKit.hdist(enc.boss.position(), c) < 0.3, "jumped to the center " + enc.boss.position()));
        h.runAfterDelay(ARRIVED + jump + wu + 1, () -> {
            h.assertTrue(p1.getHealth() == hp[0], "inside a safe circle: untouched");
            near(h, hp[1] - p2.getHealth(), C.num("damage"), 1e-3, "outside: cut by the void cross");
            h.assertTrue(!enc.immune && BossVoidKnight.opened(enc, Tasks.now()), "opening after the cross");
            LOG.info("[voidknight-test] OK void cross: jump to the center (immune) → safe circles at 45° → outside takes {}", C.num("damage"));
            RpgNidhoggTests.finish(h, level, a, hold, p1, p2);
        });
    }

    /** 제한 시간: 1분 아래면 막대가 붉게 → 넘기면 공허 붕괴 (무적 · 가운데) → windup 틱 뒤 필드 안 모두 즉사 */
    @GameTest(environment = "mmorpg:voidknight", maxTicks = 400)
    public void voidKnightEnrageKillsAll(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 8, hold);
        ServerPlayer p1 = fighter(h, 900);
        RpgNidhoggTests.put(p1, a, 0, 6);
        Bosses.Enc enc = Bosses.summon(a, p1);
        int wu = (int) def().obj("enrage").num("windup");
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc, true);
            BossVoidKnight.shiftClock(enc, BossVoidKnight.timeLeft(enc, Tasks.now()) - 30);
        });
        h.runAfterDelay(ARRIVED + 10, () -> {
            ServerBossEvent bar = BossVoidKnight.timerOf(enc);
            h.assertTrue(bar.getColor() == BossEvent.BossBarColor.RED, "red timer in the last minute");
            h.assertTrue(!p1.isDeadOrDying(), "alive before the limit");
        });
        h.runAfterDelay(ARRIVED + 32, () -> {
            h.assertTrue(enc.cast != null && "collapse".equals(enc.cast.id) && enc.immune, "void collapse (immune)");
            h.assertTrue(BossVoidKnight.timerOf(enc).getName().getString().contains("공허 붕괴"), "timer says collapse");
        });
        h.runAfterDelay(ARRIVED + 31 + wu + 3, () -> {
            h.assertTrue(p1.isDeadOrDying(), "everyone in the field dies");
            LOG.info("[voidknight-test] OK 10 minute limit: red timer → void collapse → instant death");
            RpgNidhoggTests.finish(h, level, a, hold, p1);
        });
    }

    /** 처치: 보상 (실버 56~72 · 골드 · 경험치 260 · 공허의 심장 · 균열 대검 조각) · 재사용 대기 20분 · 공허 붕괴 막대 치움 */
    @GameTest(environment = "mmorpg:voidknight", maxTicks = 300)
    public void voidKnightKilledRewards(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 9, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        RpgNidhoggTests.tough(p1, 4);
        RpgNidhoggTests.put(p1, a, 0, 6);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.runAfterDelay(ARRIVED, () -> {
            freeze(enc, true);
            BedrockMob b = enc.boss;
            ServerBossEvent bar = BossVoidKnight.timerOf(enc);
            int xp0 = p1.totalExperience;
            b.setHealth(10F);
            h.assertTrue(RpgNidhoggTests.hit(p1, b, 50F), "killing blow");
            h.assertTrue(Bosses.encounterAt(a) == null && "dead".equals(enc.state), "encounter over");
            Obj L = def().obj("loot");
            int em = RpgGearTests.silver(p1);
            int heart = RpgGearTests.countIn(p1, GearItems.byId("rpg:void_heart"));
            int shard = RpgGearTests.countIn(p1, GearItems.byId("rpg:rift_shard"));
            h.assertTrue(em >= L.numbersOrNull("emerald").get(0) && em <= L.numbersOrNull("emerald").get(1), "silver " + em);
            h.assertTrue(RpgGearTests.bossGold(p1, KEY), "boss gold " + RpgGearTests.gold(p1) + " in " + java.util.Arrays.toString(mmorpg.core.Economy.bossGold(KEY)));
            h.assertTrue(heart >= 2 && heart <= 4 && shard >= 2 && shard <= 4, "boss materials " + heart + " / " + shard);
            h.assertTrue(p1.totalExperience - xp0 == (int) L.num("xp"), "xp " + (p1.totalExperience - xp0));
            h.assertTrue(a.cooldownUntil > System.currentTimeMillis() + 1_190_000, "cooldown 20 min");
            h.assertTrue(bar.getPlayers().isEmpty(), "collapse timer bar removed");
            LOG.info("[voidknight-test] OK kill → silver {} · void heart {} · rift shard {} · xp · 20 min cooldown · timer removed", em, heart, shard);
            RpgNidhoggTests.finish(h, level, a, hold, p1);
        });
    }
}
