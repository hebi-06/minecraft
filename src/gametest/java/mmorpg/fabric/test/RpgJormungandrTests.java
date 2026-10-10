package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.ArenaThemes;
import mmorpg.fabric.boss.BossJormungandr;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.boss.Bosses;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.skill.Kit;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 5단계 게임 시험 (서버) — 요르문간드 (boss_jormungandr.js): 소환 (체력 22000 · 엔진 평타 끔) · 헤엄 (엔진 pose — 목뿌리가 해자 가운데 원 15.75칸을
 * 한 틱에 0.18칸씩, 엔티티 = 머리 = 목뿌리 앞 3.94칸, 밀어도 다음 틱에 제자리, 속성 rpg:sink · rpg:curve · rpg:tail) ·
 * 독해 범람 (흰 고리 안 = 안 맞음, 밖 = 피해 + 독, 끝나면 모인 곳으로 돌진 예약) · 세계를 감는 고리 (틈으로 나간 사람은 살고, 담장 쪽으로 나간 사람은
 * 조이기 시작할 때 짓눌림, 고리 소품 rpg:radius 가 줄어듦) · 아홉 걸음의 독 (동료가 곁에 서면 독이 빠짐 · 9칸을 걸으면 즉사) · 처치 보상 (보스 재료 없음).
 * 필드는 신전 없이 하늘 (y 150) 의 돌 바닥 + 제단 (해자 치수는 설정). 시험마다 멀리 (x 60_000 + 80 × 번호, z 62_000). 통과하면 [jorm-test] OK 줄.
 */
public final class RpgJormungandrTests {
    private static final String KEY = "jormungandr";
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-jorm-test");
    private static final int R = 20;
    private static final long FAR = Long.MAX_VALUE / 4;

    private static Arena.Altar field(ServerLevel level, int slot, String[] hold) {
        return RpgWormTests.field(level, KEY, new BlockPos(60_000 + slot * 80, 150, 62_000), R, hold);
    }

    /** 패턴 · 원거리 견제를 멈춤 (pause = 틀 "pause" 도 — 그동안 목만 돌림, 헤엄 시험은 틀 없이) */
    private static void quiet(Bosses.Enc enc, int pause) {
        BossJormungandr.M m = BossJormungandr.mem(enc);
        if (m != null) {
            m.next.replaceAll((k, v) -> FAR);
            m.ringDone.addAll(List.of(0.5, 0.2));
            m.bigUntil = FAR;
        }
        enc.meleeAt = FAR;
        enc.farAt = FAR;
        if (pause > 0) Bosses.cast(enc, "pause", pause);
    }

    private static double wrapPi(double a) {
        double t = Math.PI * 2;
        double w = ((a % t) + t) % t;
        return w > Math.PI ? w - t : w;
    }

    /** 몸 상태로 계산한 머리 자리 */
    private static Vec3 headOf(Bosses.Enc enc) {
        BossJormungandr.Swim sw = BossJormungandr.mem(enc).swim;
        double N = enc.def.obj("swim").num("neck");
        double y = sw.byaw + sw.nyaw;
        return new Vec3(sw.px + Math.cos(y) * N, enc.center.y, sw.pz + Math.sin(y) * N);
    }

    // ------------------------------------------------------------ 시험

    /**
     * 헤엄 (엔진 pose): 목뿌리가 해자 가운데 원 (15.75칸) 위, 20틱에 0.18 × 20 / 15.75 라디안 돎, 엔티티 = 머리 (목뿌리 앞 3.94칸) = home,
     * 엔진 평타는 꺼짐, 밀어도 다음 틱에 머리 자리로, 속성 rpg:sink 1 (해자 위) · rpg:curve (원을 따라 굽음) · rpg:tail 0
     */
    @GameTest(maxTicks = 200)
    public void jormSwimsAroundTheMoat(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 0, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        RpgWormTests.put(p1, a, 0, 8);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.assertTrue(enc != null, "summoned");
        h.runAfterDelay(64, () -> {
            h.assertTrue("fight".equals(enc.state), "fight " + enc.state);
            BedrockMob b = enc.boss;
            h.assertTrue(b.getMaxHealth() == 22000F && b.getHealth() == 22000F, "hp " + b.getHealth());
            BossJormungandr.M m = BossJormungandr.mem(enc);
            h.assertTrue(m != null && enc.meleeAt >= FAR / 2, "module started, engine melee off");
            quiet(enc, 0);
            RpgWormTests.put(p1, a, 0, 0);
            Vec3 c = Arena.arenaCenter(a);
            double radius = enc.def.obj("swim").num("radius");
            BossJormungandr.Swim sw = m.swim;
            h.assertTrue(sw.onCircle && Math.abs(BossKit.hdist(BossJormungandr.pivotOf(enc), c) - radius) < 1e-6, "pivot on the moat circle");
            h.assertTrue(BossKit.hdist(b.position(), headOf(enc)) < 1e-3 && BossKit.hdist(enc.home, b.position()) < 1e-3, "entity = head = home");
            double ang0 = sw.ang;
            int dir = sw.dir;
            h.runAfterDelay(20, () -> {
                double want = ang0 + dir * enc.def.obj("swim").num("speed") * 20 / radius;
                h.assertTrue(enc.cast == null && Math.abs(wrapPi(sw.ang - want)) < 1e-6, "swam 20 ticks along the circle " + sw.ang + " vs " + want);
                h.assertTrue(BossKit.hdist(b.position(), headOf(enc)) < 1e-3, "pose moved the entity to the head " + b.position() + " " + headOf(enc));
                h.assertTrue(Math.abs(Math.hypot(b.getX() - c.x, b.getZ() - c.z) - Math.hypot(radius, enc.def.obj("swim").num("neck"))) < 1e-3, "head ahead of the pivot");
                h.assertTrue(b.prop("rpg:sink") == 1.0, "sunk over the moat " + b.prop("rpg:sink"));
                double curve = BossJormungandr.curveFor(enc, radius, dir);
                h.assertTrue(Math.abs(b.prop("rpg:curve") - Math.round(curve * 100) / 100.0) < 0.011, "curve along the circle " + b.prop("rpg:curve") + " vs " + curve);
                h.assertTrue(Math.abs(b.prop("rpg:tail")) < 0.06, "tail straight behind " + b.prop("rpg:tail"));
                // 밀어도 다음 틱에 머리 자리로 (모듈이 매 틱 pose)
                BossKit.teleport(b, new Vec3(b.getX() + 3, b.getY(), b.getZ() - 2));
                h.runAfterDelay(2, () -> {
                    h.assertTrue(BossKit.hdist(b.position(), headOf(enc)) < 1e-3, "pushed snake is posed back " + b.position());
                    LOG.info("[jorm-test] OK swim: pivot on the 15.75 circle, 0.18/tick, entity = head = home, sink 1 · curve {} · tail 0, pushed → posed back",
                            b.prop("rpg:curve"));
                    RpgWormTests.finish(h, level, a, hold, p1);
                });
            });
        });
    }

    /** 독해 범람: 흰 고리 (안전 7.7칸) 안의 사람은 안 맞고, 밖의 사람은 독 물결 (피해 + 독) → 독이 빠지면 모인 곳으로 돌진 예약 */
    @GameTest(maxTicks = 300)
    public void floodSparesTheWhiteRing(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 1, hold);
        ServerPlayer in = RpgGearTests.player(h);
        ServerPlayer out = RpgGearTests.player(h);
        RpgWormTests.put(in, a, 3, 0);
        RpgWormTests.put(out, a, 10, 0);
        Bosses.Enc enc = Bosses.summon(a, in);
        h.runAfterDelay(64, () -> {
            quiet(enc, 1000);
            RpgWormTests.put(in, a, 3, 0);
            RpgWormTests.put(out, a, 10, 0);
            double safe = ArenaThemes.isleSize(KEY).safe();
            h.assertTrue(BossJormungandr.safeEdge(safe) > 7.5 && BossJormungandr.safeEdge(safe) < 8, "safe edge " + BossJormungandr.safeEdge(safe));
            float hin = in.getHealth();
            float hout = out.getHealth();
            BossJormungandr.flood(enc, Tasks.now());
            BossJormungandr.M m = BossJormungandr.mem(enc);
            h.assertTrue(m.flood != null && enc.cast != null && "flood".equals(enc.cast.id), "flood cast");
            int windup = (int) enc.def.obj("flood").num("windup");
            int floodHold = (int) enc.def.obj("flood").num("hold");
            h.runAfterDelay(windup + 1, () -> {
                h.assertTrue(out.getHealth() < hout && out.hasEffect(MobEffects.POISON), "outside the white ring: wave + poison " + hout + " → " + out.getHealth());
                h.assertTrue(in.getHealth() == hin && !in.hasEffect(MobEffects.POISON), "inside the white ring: untouched " + in.getHealth());
            });
            h.runAfterDelay(windup + floodHold + 2, () -> {
                h.assertTrue(m.flood == null && m.floodDash && m.next.get("dash") <= Tasks.now() + 4, "poison drains → dash across the gathered ring");
                h.assertTrue(in.getHealth() == hin, "inside never hit");
                LOG.info("[jorm-test] OK flood: inside the white ring safe, outside hit + poison, dash queued");
                RpgWormTests.finish(h, level, a, hold, in, out);
            });
        });
    }

    /**
     * 세계를 감는 고리: 머리 앞 틈 (60°) — 예고 동안 틈으로 고리 밖에 나간 사람은 살고, 틈이 아닌 쪽 (담장 쪽) 으로 나간 사람은 조이기 시작할 때 짓눌림 (즉사).
     * 고리 소품 (rpg:prop_jorm_ring) 의 rpg:radius 는 21.5 에서 줄어듦
     */
    @GameTest(maxTicks = 300)
    public void ringSparesOnlyTheGap(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 2, hold);
        ServerPlayer gap = RpgGearTests.player(h);
        ServerPlayer wall = RpgGearTests.player(h);
        RpgWormTests.put(gap, a, 0, 5);
        RpgWormTests.put(wall, a, 0, -5);
        Bosses.Enc enc = Bosses.summon(a, gap);
        h.runAfterDelay(64, () -> {
            quiet(enc, 1000);
            BossJormungandr.M m = BossJormungandr.mem(enc);
            m.swim.dir = 1;
            BossJormungandr.setOnCircle(enc, 0);
            BossJormungandr.ring(enc, Tasks.now(), 0.5);
            BossJormungandr.Ring rg = m.ring;
            h.assertTrue(rg != null && enc.cast != null && "ring".equals(enc.cast.id), "ring cast");
            h.assertTrue(rg.prop != null && Kit.isType(rg.prop, BossJormungandr.RING_ID) && Math.abs(rg.prop.prop("rpg:radius") - 21.5) < 1e-6, "ring prop at 21.5");
            h.assertTrue(rg.roster.containsKey(gap.getUUID()) && rg.roster.containsKey(wall.getUUID()), "both on the roster");
            double g = rg.g;
            // 몸 안쪽 (15칸) 에 세움 → 예고 동안 하나는 틈 쪽으로, 하나는 반대쪽 담장 너머로 나감 (24칸)
            RpgWormTests.put(gap, a, Math.cos(g) * 15, Math.sin(g) * 15);
            RpgWormTests.put(wall, a, Math.cos(g + Math.PI) * 15, Math.sin(g + Math.PI) * 15);
            h.runAfterDelay(5, () -> {
                RpgWormTests.put(gap, a, Math.cos(g) * 24, Math.sin(g) * 24);
                RpgWormTests.put(wall, a, Math.cos(g + Math.PI) * 24, Math.sin(g + Math.PI) * 24);
            });
            h.runAfterDelay(8, () -> {
                h.assertTrue(rg.roster.get(gap.getUUID()).passed, "left through the gap");
                h.assertFalse(rg.roster.get(wall.getUUID()).passed, "left over the wall (not the gap)");
                h.assertTrue(wall.isAlive() && !wall.isDeadOrDying(), "nobody dies during the warning");
            });
            long startAt = rg.startAt - Tasks.now();
            h.runAfterDelay((int) startAt + 3, () -> {
                h.assertTrue(wall.isDeadOrDying() || !wall.isAlive(), "left outside the gap → crushed when the ring tightens");
                h.assertTrue(gap.isAlive() && !gap.isDeadOrDying(), "left through the gap → alive");
                h.assertTrue(rg.prop.prop("rpg:radius") < 21.5, "ring tightening " + rg.prop.prop("rpg:radius"));
                LOG.info("[jorm-test] OK ring: gap exit survives, wall exit crushed at the squeeze, ring radius {}", rg.prop.prop("rpg:radius"));
                RpgWormTests.finish(h, level, a, hold, gap, wall);
            });
        });
    }

    /** 아홉 걸음의 독: 표식이 없는 동료가 곁 (2.2칸) 에 1.5초 서 있으면 독이 빠짐 (도운 사람 조금 다침) · 다시 걸린 뒤 스스로 9칸을 걸으면 즉사 */
    @GameTest(maxTicks = 300)
    public void venomNineSteps(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 3, hold);
        ServerPlayer marked = RpgGearTests.player(h);
        ServerPlayer friend = RpgGearTests.player(h);
        RpgWormTests.put(marked, a, 0, 4);
        RpgWormTests.put(friend, a, 1.5, 4);
        Bosses.Enc enc = Bosses.summon(a, marked);
        h.runAfterDelay(64, () -> {
            quiet(enc, 1000);
            RpgWormTests.put(marked, a, 0, 4);
            RpgWormTests.put(friend, a, 1.5, 4);
            BossJormungandr.M m = BossJormungandr.mem(enc);
            float hf = friend.getHealth();
            h.assertTrue(BossJormungandr.venomMark(enc, Tasks.now(), List.of(marked)), "marked");
            h.assertTrue(m.venom != null && m.venom.marks().size() == 1 && m.venom.marks().get(0).p == marked, "one mark on the target");
            int need = (int) enc.def.obj("venom").num("need");
            h.runAfterDelay(need + 3, () -> {
                h.assertTrue(m.venom == null, "friend drained the poison");
                h.assertTrue(friend.getHealth() < hf, "helper takes a little " + hf + " → " + friend.getHealth());
                h.assertTrue(marked.isAlive(), "marked lives");
                // 다시: 동료는 멀리, 표식이 붙은 사람이 스스로 걸음 (0.5초 뒤부터 셈) → 9칸
                RpgWormTests.put(friend, a, -12, 0);
                h.assertTrue(BossJormungandr.venomMark(enc, Tasks.now(), List.of(marked)), "marked again");
                BossJormungandr.Mark mk = m.venom.marks().get(0);
                for (int i = 1; i <= 5; i++) {
                    int k = i;
                    h.runAfterDelay(12 + k, () -> RpgWormTests.put(marked, a, 2.2 * k, 4));
                }
                h.runAfterDelay(20, () -> {
                    h.assertTrue(mk.done && mk.used >= 9, "nine steps used " + mk.used);
                    h.assertTrue(marked.isDeadOrDying() || !marked.isAlive(), "walked nine steps → instant death");
                    LOG.info("[jorm-test] OK nine steps: friend drains, walking 9 kills (used {})", mk.used);
                    RpgWormTests.finish(h, level, a, hold, marked, friend);
                });
            });
        });
    }

    /** 처치 보상: 에메랄드 22~32 · 경험치 70 · 빛나는 강화석 1~2 · 보스 재료 없음 · 처치 훅 · 재사용 대기 15분 */
    @GameTest(maxTicks = 200)
    public void jormKillRewards(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        String[] hold = new String[1];
        Arena.Altar a = field(level, 4, hold);
        ServerPlayer p1 = RpgGearTests.player(h);
        RpgWormTests.put(p1, a, 0, 6);
        List<String> killed = new ArrayList<>();
        BiConsumer<String, List<ServerPlayer>> hook = (k, list) -> killed.add(k);
        Bosses.addBossKillHook(hook);
        Bosses.Enc enc = Bosses.summon(a, p1);
        h.runAfterDelay(64, () -> {
            try {
                quiet(enc, 1000);
                Entity b = enc.boss;
                RpgWormTests.put(p1, a, (b.getX() - Arena.arenaCenter(a).x) * 0.6, (b.getZ() - Arena.arenaCenter(a).z) * 0.6);
                int xp0 = p1.totalExperience;
                enc.boss.setHealth(50F);
                h.assertTrue(RpgWormTests.hit(p1, enc.boss, 100F), "killing blow");
                h.assertTrue(Bosses.encounterAt(a) == null && "dead".equals(enc.state), "dead");
                int em = RpgGearTests.countIn(p1, Items.EMERALD);
                int bright = RpgGearTests.countIn(p1, GearItems.byId("rpg:bright_stone"));
                h.assertTrue(em >= 22 && em <= 32, "emeralds " + em);
                h.assertTrue(bright >= 1 && bright <= 2, "bright stones " + bright);
                h.assertTrue(RpgGearTests.countIn(p1, GearItems.byId("rpg:worm_fang")) == 0 && RpgGearTests.countIn(p1, GearItems.byId("rpg:kraken_ink")) == 0,
                        "no boss materials");
                h.assertTrue(p1.totalExperience - xp0 == 70, "xp " + (p1.totalExperience - xp0));
                h.assertTrue(killed.contains(KEY), "kill hook");
                h.assertTrue(a.cooldownUntil > System.currentTimeMillis() + 890_000, "cooldown 15 min");
                h.assertTrue(BossJormungandr.mem(enc).ring == null && enc.props.isEmpty(), "cleaned up");
                LOG.info("[jorm-test] OK kill → emerald {} · bright {} · xp 70 · no boss materials · cooldown 15 min", em, bright);
            } finally {
                Bosses.removeBossKillHook(hook);
            }
            RpgWormTests.finish(h, level, a, hold, p1);
        });
    }
}
