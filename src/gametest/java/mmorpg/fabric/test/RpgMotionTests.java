package mmorpg.fabric.test;

import java.lang.reflect.Method;
import java.util.List;
import mmorpg.core.Motions;
import mmorpg.core.Runes;
import mmorpg.core.SwingLooks;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.Slash;
import mmorpg.fabric.SwingArea;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.job.JobTraits;
import mmorpg.fabric.motion.MotionNet;
import mmorpg.fabric.motion.MotionSystem;
import mmorpg.fabric.motion.SwingLook;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.rune.RuneStrike;
import mmorpg.fabric.skill.Transcend;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 0.38.0 동작 게임 시험 (서버): 평타 연타 단계 (칼 가로 → 세로 → 회전 → 다시 가로, comboWindow 를 넘기면 처음, 클라이언트가 보낸 단계를 씀 · 범위 밖 단계는 무시,
 * 원거리 무기는 없음), 구르기 (재사용 대기 · 날기 중 막힘 · 둘레에 알림), 스킬 동작 (무기 스킬 · 룬 → MOTION.skills 의 동작을 알림).
 * 0.39.0 참격 범위 피해 (가로 베기 = 앞 부채꼴 — 옆 · 뒤는 안 맞음, 회전 베기 = 둘레, 낫은 120°, 허공을 베어도 범위 안 적은 100%, 조준한 적은 한 번만),
 * 평타 사이 (네트워크 입력 — 8틱 안 두 번째는 받지 않음, 같은 누름의 공격 패킷 하나는 받음), 공격 대기 없음 (충전과 상관없이 · 바로 다시 쳐도 온전한 피해).
 * 0.40.0 참격 궤적: 범위 거리 = 무기 궤적이 닿는 곳 (칼 희귀 2.3칸 · 낫 고귀 3.3칸 — MOTION.area.reach), 가로 베기 160°.
 * 0.42.0 창 연타: 찌르기 (앞 줄 4칸) → 가로 휘두르기 (앞 160° · 궤적이 닿는 곳).
 * 0.41.0 평타 강화 모습: 평타 강화 룬 · 직업 평타 태세가 범위를 넓힘 (날 세우기 × 1.15 · 사신 현신 5칸), '평타 N번' 은 휘두를 때마다 1번 (마지막 번의 범위 적도),
 * 가르기 부채꼴은 같은 휘두르기에 맞은 적을 다시 베지 않음, 평타 알림에 모습, 활 · 완드 색은 평타 강화 룬만.
 */
public final class RpgMotionTests {
    static final String ENV = "mmorpg:motion";

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        try {
            Method m = ServerGamePacketListenerImpl.class.getDeclaredMethod("markClientLoaded");
            m.setAccessible(true);
            m.invoke(sp.connection);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot mark mock player loaded", e);
        }
        sp.setGameMode(GameType.SURVIVAL);
        Vec3 at = h.absoluteVec(new Vec3(1.5, 2, 1.5));
        sp.snapTo(at.x, at.y, at.z, 0F, 0F);
        sp.setYHeadRot(0F);
        RpgPlayers.onJoin(sp);
        MotionSystem.forget(sp.getUUID());
        return sp;
    }

    private static void done(GameTestHelper h, ServerPlayer sp) {
        RpgCombat.forceCrit = null;
        MotionSystem.forget(sp.getUUID());
        h.getLevel().getServer().getPlayerList().remove(sp);
        h.succeed();
    }

    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }

    private static Husk dummy(GameTestHelper h, double x, double y, double z) {
        Husk m = TestDummies.make(h.spawnWithNoFreeWill(EntityTypes.HUSK, (float) x, (float) y, (float) z));
        m.setYRot(180F);
        m.setYHeadRot(180F);
        m.setYBodyRot(180F);
        m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60, 0, false, false));
        return m;
    }

    private static void hold(ServerPlayer sp, Weapon w) {
        if (sp.experienceLevel < w.level()) {
            sp.setExperienceLevels(w.level());
            RpgPlayers.checkLevelUp(sp);
        }
        for (String s : List.of("str", "dex", "int", "vit")) RpgPlayers.setStat(sp, s, 150);
        sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RpgItems.byId(w.id())));
    }

    // ------------------------------------------------------------ 연타 단계

    @GameTest(environment = ENV, maxTicks = 80)
    public void comboSteps(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon sword = Weapons.weaponFrom("sword", 20, "rare");
        Weapon bow = Weapons.weaponFrom("bow", 20, "rare");
        hold(sp, sword);
        String[] want = {"slash_h", "slash_v", "spin", "slash_h"};
        for (int i = 0; i < want.length; i++) {
            int n = i;
            h.runAfterDelay(2 + i * 3, () -> {
                MotionSystem.Swing s = MotionSystem.swung(sp, sword);
                h.assertTrue(s != null && s.motion().equals(want[n]) && s.step() == n % 3 + 1, "swing " + n + " " + s);
                h.assertTrue(want[n].equals(MotionSystem.lastSent(sp)), "sent " + MotionSystem.lastSent(sp));
                h.assertTrue(MotionSystem.spinNow(sp) == want[n].equals("spin"), "spinNow " + n);
            });
        }
        // 2단 뒤 comboWindow 를 넘기면 처음
        h.runAfterDelay(15, () -> h.assertTrue(MotionSystem.swung(sp, sword).step() == 2, "step 2 again"));
        h.runAfterDelay(15 + Motions.comboWindow() + 1, () -> {
            h.assertTrue(MotionSystem.swung(sp, sword).step() == 1, "window passed → 1");
            h.assertFalse(MotionSystem.spinNow(sp), "no spin");
        });
        // 클라이언트가 보낸 단계 (공격보다 먼저 옴) — 범위 밖은 무시
        h.runAfterDelay(45, () -> {
            MotionSystem.testRequest(sp, "basic", 3, 0);
            MotionSystem.Swing s = MotionSystem.swung(sp, sword);
            h.assertTrue(s.motion().equals("spin") && s.step() == 3, "client step 3 → spin " + s);
            MotionSystem.testRequest(sp, "basic", 7, 0);
            h.assertTrue(MotionSystem.swung(sp, sword).step() == 1, "bad client step ignored (3 → 1)");
        });
        h.runAfterDelay(50, () -> {
            h.assertTrue(MotionSystem.swung(sp, bow) == null, "bow: no combo");
            h.assertTrue(MotionSystem.swung(sp, null) == null, "bare hand: no combo");
            done(h, sp);
        });
    }

    // ------------------------------------------------------------ 0.39.0 참격 범위 피해

    /** 평타 한 번의 온전한 피해 (치명타 없이 · 직업 없음) */
    private static double full(ServerPlayer sp, Weapon w) {
        return w.damage() * RpgCombat.powerOf(sp, w);
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) <= 1e-3 + Math.abs(b) * 1e-4;
    }

    /** 평타 한 번 (치명타면 × 치명타 배율 — 함께 도는 다른 시험이 RpgCombat.forceCrit 를 풀 수 있어 둘 다 맞는 것으로) */
    private static boolean oneHit(double v, ServerPlayer sp, Weapon w) {
        double f = full(sp, w);
        return near(v, f) || near(v, f * w.critMult());
    }

    @GameTest(environment = ENV, maxTicks = 60)
    public void slashAreaFrontArcAndSpinRing(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = Weapons.weaponFrom("sword", 20, "rare");
        hold(sp, w);
        Husk a = dummy(h, 1.5, 2, 3.5); // 앞 2칸 (조준한 적)
        Husk e = dummy(h, 3.0, 2, 3.5); // 앞 왼쪽 비스듬히 (약 37° · 2.5칸) — 가로 베기 부채꼴 안
        Husk b = dummy(h, 1.5, 2, 0.6); // 뒤 0.9칸
        Husk c = dummy(h, 3.6, 2, 1.5); // 옆 2.1칸 (90°)
        Husk d = dummy(h, 6.4, 2, 5.6); // 6.4칸 — 밖
        RpgCombat.forceCrit = false;
        double[] main = new double[2];
        double[] eHit = new double[1];
        h.runAfterDelay(2, () -> {
            MotionSystem.testRequest(sp, "basic", 1, 0);
            sp.attack(a);
            main[0] = TestDummies.dealt(a);
            h.assertTrue(oneHit(main[0], sp, w), "slash_h aimed hit full " + main[0] + " vs " + full(sp, w));
            h.assertTrue(TestDummies.dealt(e) == 0, "area lands one tick later (after the aimed hit)");
        });
        h.runAfterDelay(4, () -> {
            eHit[0] = TestDummies.dealt(e);
            h.assertTrue(oneHit(eHit[0], sp, w), "slash_h arc: front-left 100% " + eHit[0] + " vs " + full(sp, w));
            h.assertTrue(near(TestDummies.dealt(a), main[0]), "aimed target hit once " + TestDummies.dealt(a));
            h.assertTrue(TestDummies.dealt(b) == 0 && TestDummies.dealt(c) == 0 && TestDummies.dealt(d) == 0,
                    "slash_h: behind · side · far not hit " + TestDummies.dealt(b) + " " + TestDummies.dealt(c) + " " + TestDummies.dealt(d));
            h.assertTrue(SwingArea.lastCount(sp) == 1, "one area victim " + SwingArea.lastCount(sp));
        });
        h.runAfterDelay(16, () -> {
            MotionSystem.testRequest(sp, "basic", 3, 0);
            sp.attack(a);
            main[1] = TestDummies.dealt(a) - main[0];
            h.assertTrue(oneHit(main[1], sp, w), "spin aimed hit full " + main[1] + " vs " + full(sp, w));
        });
        h.runAfterDelay(18, () -> {
            h.assertTrue(oneHit(TestDummies.dealt(b), sp, w), "spin ring: behind 100% " + TestDummies.dealt(b) + " vs " + full(sp, w));
            h.assertTrue(oneHit(TestDummies.dealt(c), sp, w), "spin ring: side 100% " + TestDummies.dealt(c) + " vs " + full(sp, w));
            h.assertTrue(oneHit(TestDummies.dealt(e) - eHit[0], sp, w), "spin ring: front-left again " + (TestDummies.dealt(e) - eHit[0]));
            h.assertTrue(TestDummies.dealt(d) == 0, "far not hit " + TestDummies.dealt(d));
            done(h, sp);
        });
    }

    @GameTest(environment = ENV, maxTicks = 60)
    public void scytheAreaIsWide(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = Weapons.weaponFrom("scythe", 20, "noble");
        hold(sp, w);
        Husk a = dummy(h, 1.5, 2, 3.5);
        Husk b = dummy(h, 4.2, 2, 3.0); // 앞 왼쪽 약 61° · 3.1칸 — 칼 궤적 (희귀 2.3칸 + 몸 폭) 밖, 낫 궤적 (고귀 3.3칸) 안
        Husk c = dummy(h, 1.5, 2, 0.6); // 뒤
        RpgCombat.forceCrit = false;
        double[] main = new double[1];
        h.runAfterDelay(2, () -> {
            MotionSystem.testRequest(sp, "basic", 1, 0);
            sp.attack(a);
            main[0] = TestDummies.dealt(a);
            h.assertTrue(main[0] > 0, "scythe aimed hit " + main[0]);
        });
        h.runAfterDelay(4, () -> {
            h.assertTrue(oneHit(TestDummies.dealt(b), sp, w), "scythe reach 3.3: far side 100% " + TestDummies.dealt(b) + " vs " + full(sp, w));
            h.assertTrue(TestDummies.dealt(c) == 0, "scythe: behind not hit");
            // 같은 자리의 적은 칼 (희귀 — 궤적 2.3칸) 로는 범위 밖
            hold(sp, Weapons.weaponFrom("sword", 20, "rare"));
            h.assertTrue(SwingArea.testTargets(sp, "slash_h").stream().noneMatch(x -> x == b), "sword (2.3) does not reach it");
            h.assertTrue(SwingArea.testTargets(sp, "slash_h").stream().anyMatch(x -> x == a), "sword reaches the front one");
            done(h, sp);
        });
    }

    @GameTest(environment = ENV, maxTicks = 60)
    public void airSwingHitsAreaAndGate(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = Weapons.weaponFrom("sword", 20, "rare");
        hold(sp, w);
        Husk a = dummy(h, 1.5, 2, 3.5); // 앞 2칸
        Husk e = dummy(h, 3.0, 2, 3.5); // 앞 왼쪽
        Husk b = dummy(h, 1.5, 2, 0.6); // 뒤
        RpgCombat.forceCrit = false;
        long[] t0 = new long[1];
        h.runAfterDelay(2, () -> {
            h.assertTrue(Slash.admit(sp, false), "first swing admitted");
            Slash.onPunch(sp); // 허공 휘두르기 (조준한 몹 없음)
            t0[0] = Tasks.now();
            MotionSystem.Swing s = MotionSystem.last(sp);
            h.assertTrue(s != null && s.tick() == t0[0] && s.motion().equals("slash_h"), "air swing registered " + s);
            // 같은 틱 (같은 누름) 의 공격 패킷 하나는 받음 → 그 적은 범위에서 빠지고 바닐라 공격으로 한 번
            h.assertTrue(Slash.admit(sp, true), "same press: one attack packet admitted");
            sp.attack(a);
            h.assertFalse(Slash.admit(sp, true), "second attack packet in the same press refused");
            h.assertFalse(Slash.admit(sp, false), "punch again in the same tick ignored");
        });
        h.runAfterDelay(4, () -> {
            double main = TestDummies.dealt(a);
            h.assertTrue(oneHit(main, sp, w), "aimed (after the punch) hit once, full " + main + " vs " + full(sp, w));
            h.assertTrue(oneHit(TestDummies.dealt(e), sp, w), "air swing area: front-left 100% " + TestDummies.dealt(e));
            h.assertTrue(TestDummies.dealt(b) == 0, "behind not hit");
            h.assertFalse(Slash.admit(sp, true) || Slash.admit(sp, false), "2 ticks later: too early");
        });
        h.runAfterDelay(2 + Motions.serverInterval() - 1, () -> h.assertFalse(Slash.admit(sp, true), "interval − 1: too early"));
        h.runAfterDelay(2 + Motions.serverInterval(), () -> {
            h.assertTrue(Slash.admit(sp, true) && Slash.admit(sp, false), "after the interval: admitted");
            done(h, sp);
        });
    }

    @GameTest(environment = ENV, maxTicks = 40)
    public void noAttackCooldownFullDamage(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = Weapons.weaponFrom("sword", 20, "rare");
        hold(sp, w);
        Husk a = dummy(h, 1.5, 2, 3.5);
        RpgCombat.forceCrit = false;
        double[] first = new double[1];
        h.runAfterDelay(2, () -> {
            ItemStack held = sp.getMainHandItem();
            h.assertTrue(held.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
                    .anyMatch(m -> m.attribute().equals(Attributes.ATTACK_SPEED) && m.modifier().amount() >= 90), "weapon attack speed +96 (no vanilla cooldown)");
            sp.resetAttackStrengthTicker(); // 바로 앞에 친 것처럼 (예전에는 충전 0 → 20%)
            sp.attack(a);
            first[0] = TestDummies.dealt(a);
            h.assertTrue(oneHit(first[0], sp, w), "full damage right after a reset " + first[0] + " vs " + full(sp, w));
        });
        h.runAfterDelay(3, () -> {
            // 1틱 뒤 다시 (몹의 연속 피해 막기 시간 안) — 예전에는 막히거나 차이만
            sp.attack(a);
            h.assertTrue(oneHit(TestDummies.dealt(a) - first[0], sp, w), "second hit 1 tick later also full " + (TestDummies.dealt(a) - first[0]));
            done(h, sp);
        });
    }

    // ------------------------------------------------------------ 0.42.0 창: 찌르기 → 가로 휘두르기

    /**
     * 창 연타 = 찌르기 → 가로 휘두르기 (2타 번갈아). 찌르기는 앞 줄 4칸 (옆 적은 못 맞힘 · 4.1칸 앞 적은 맞음), 가로 휘두르기는 앞 160° · 궤적이 닿는 3.55칸
     * (희귀 — 옆 3칸 적은 맞음 · 4.1칸 앞 적은 못 맞힘)
     */
    @GameTest(environment = ENV, maxTicks = 60)
    public void lanceThrustThenSweep(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = Weapons.weaponFrom("lance", 20, "rare");
        hold(sp, w);
        Husk a = dummy(h, 1.5, 2, 3.5); // 앞 2칸 (조준)
        Husk side = dummy(h, 4.5, 2, 2.5); // 앞 1칸 · 왼쪽 3칸 — 휘두르기만
        Husk far = dummy(h, 1.5, 2, 5.6); // 앞 4.1칸 — 찌르기만
        RpgCombat.forceCrit = false;
        double[] seen = new double[2];
        h.runAfterDelay(2, () -> {
            h.assertTrue(Motions.combo("lance").equals(List.of("thrust", "lance_sweep")), "lance combo " + Motions.combo("lance"));
            List<net.minecraft.world.entity.LivingEntity> th = SwingArea.testTargets(sp, "thrust");
            List<net.minecraft.world.entity.LivingEntity> sw = SwingArea.testTargets(sp, "lance_sweep");
            h.assertTrue(th.contains(far) && !th.contains(side), "thrust: the line ahead (far yes · side no)");
            h.assertTrue(sw.contains(side) && !sw.contains(far), "sweep: the arc (side yes · far no)");
            MotionSystem.testRequest(sp, "basic", 1, 0);
            sp.attack(a);
            h.assertTrue("thrust".equals(MotionSystem.lastSent(sp)), "1st = thrust " + MotionSystem.lastSent(sp));
        });
        h.runAfterDelay(4, () -> {
            h.assertTrue(oneHit(TestDummies.dealt(far), sp, w) && TestDummies.dealt(side) == 0, "thrust: far 100% · side 0 — " + TestDummies.dealt(far) + " / "
                    + TestDummies.dealt(side));
            seen[0] = TestDummies.dealt(far);
            seen[1] = TestDummies.dealt(side);
            // 다음 평타 (연타 안) = 가로 휘두르기 (서버가 정함 — 클라이언트 단계 없이)
            MotionSystem.Swing s2 = MotionSystem.swung(sp, w);
            h.assertTrue(s2 != null && s2.motion().equals("lance_sweep") && s2.step() == 2, "2nd = sweep " + s2);
            MotionSystem.Swing s3 = MotionSystem.swung(sp, w);
            h.assertTrue(s3 != null && s3.motion().equals("thrust") && s3.step() == 1, "3rd = thrust again " + s3);
            MotionSystem.testRequest(sp, "basic", 2, 0);
            sp.attack(a);
            h.assertTrue("lance_sweep".equals(MotionSystem.lastSent(sp)), "sweep sent " + MotionSystem.lastSent(sp));
        });
        h.runAfterDelay(6, () -> {
            h.assertTrue(oneHit(TestDummies.dealt(side) - seen[1], sp, w), "sweep: side 100% " + (TestDummies.dealt(side) - seen[1]));
            h.assertTrue(TestDummies.dealt(far) == seen[0], "sweep: far untouched " + (TestDummies.dealt(far) - seen[0]));
            done(h, sp);
        });
    }

    // ------------------------------------------------------------ 0.41.0 평타 강화 모습 (범위 · 휘두를 때마다 1번 · 둘레에 알림)

    /** 평타 한 번 + 더한 피해 add (치명타면 평타만 × 치명타 배율 — 더한 피해는 치명타 뒤에 더함) */
    private static boolean oneHitPlus(double v, ServerPlayer sp, Weapon w, double add) {
        double f = full(sp, w);
        return near(v, f + add) || near(v, f * w.critMult() + add);
    }

    /**
     * 날 세우기 (평타 강화 — 범위 × 1.15 · 평타 3번): 걸기 전에는 2.8칸 적이 범위 밖 → 걸면 안, 한 번 휘둘러 셋을 맞혀도 1번만 씀 (더한 피해는 셋 모두),
     * 마지막 번의 휘두르기는 범위 적 (1틱 뒤) 도 더한 피해, 다 쓰면 모습 · 범위가 돌아옴. 평타 동작 알림에 모습 (빛 색 · 배율) 이 실림
     */
    @GameTest(environment = ENV, maxTicks = 100)
    public void strikeLookWidensAndCountsPerSwing(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = Weapons.weaponFrom("sword", 20, "rare");
        hold(sp, w);
        Husk a = dummy(h, 1.5, 2, 3.5); // 앞 2칸 (조준)
        Husk e = dummy(h, 3.0, 2, 3.5); // 앞 왼쪽 2.5칸 — 보통 범위 안
        Husk g = dummy(h, 1.5, 2, 4.3); // 앞 2.8칸 — 보통 (2.3 + 몸 반폭 0.3) 밖, × 1.15 (2.645 + 0.3) 안
        List<Husk> all = List.of(a, e, g);
        RpgCombat.forceCrit = false;
        Runes.Rune r = Runes.parseRune("rpg:rune_strike_common");
        double[] bonus = new double[1];
        double[] seen = new double[3];
        Runnable mark = () -> {
            for (int i = 0; i < 3; i++) seen[i] = TestDummies.dealt(all.get(i));
        };
        java.util.function.IntToDoubleFunction got = i -> TestDummies.dealt(all.get(i)) - seen[i];
        h.runAfterDelay(2, () -> {
            h.assertTrue(SwingArea.testTargets(sp, "slash_h").stream().noneMatch(x -> x == g), "no buff: 2.8 out of reach");
            h.assertTrue(SwingLook.testKey(sp) == null, "no look yet");
            h.assertTrue(RuneCast.castRune(sp, r, false), "cast 날 세우기");
            bonus[0] = RuneCast.power(sp, "strike") * r.def().num("bonus");
            h.assertTrue("edgeHone".equals(SwingLook.testKey(sp)), "look " + SwingLook.testKey(sp));
            h.assertTrue(SwingArea.testTargets(sp, "slash_h").stream().anyMatch(x -> x == g), "buff: 2.8 within x1.15");
            h.assertTrue(RuneStrike.testLeft(sp, "common") == 3, "3 left " + RuneStrike.testLeft(sp, "common"));
            MotionSystem.testRequest(sp, "basic", 1, 0);
            sp.attack(a);
            MotionNet.Look sent = MotionSystem.lastSentLook(sp);
            h.assertTrue(sent.glow() == SwingLooks.pack(SwingLooks.of("edgeHone").glow()) && Math.abs(sent.scale() - 1.15F) < 1e-6,
                    "look sent with the swing " + sent);
        });
        h.runAfterDelay(4, () -> {
            for (int i = 0; i < 3; i++) {
                h.assertTrue(oneHitPlus(got.applyAsDouble(i), sp, w, bonus[0]), "swing 1: basic + strike on each (" + i + ") " + got.applyAsDouble(i) + " vs "
                        + (full(sp, w) + bonus[0]));
            }
            h.assertTrue(SwingArea.lastCount(sp) == 2, "two area victims " + SwingArea.lastCount(sp));
            h.assertTrue(RuneStrike.testLeft(sp, "common") == 2, "one swing (3 enemies) = one use → 2 left: " + RuneStrike.testLeft(sp, "common"));
            mark.run();
            MotionSystem.testRequest(sp, "basic", 1, 0);
            sp.attack(a);
        });
        h.runAfterDelay(6, () -> {
            h.assertTrue(RuneStrike.testLeft(sp, "common") == 1, "swing 2 → 1 left: " + RuneStrike.testLeft(sp, "common"));
            h.assertTrue(oneHitPlus(got.applyAsDouble(2), sp, w, bonus[0]), "swing 2: far one still in reach " + got.applyAsDouble(2));
            mark.run();
        });
        h.runAfterDelay(14, () -> {
            MotionSystem.testRequest(sp, "basic", 1, 0);
            sp.attack(a); // 마지막 번
            h.assertTrue(RuneStrike.testLeft(sp, "common") == 0, "last use: 0 (kept until the swing's area lands) " + RuneStrike.testLeft(sp, "common"));
        });
        h.runAfterDelay(16, () -> {
            for (int i = 0; i < 3; i++) {
                h.assertTrue(oneHitPlus(got.applyAsDouble(i), sp, w, bonus[0]), "last swing: area victims get the strike too (" + i + ") " + got.applyAsDouble(i));
            }
            mark.run();
        });
        h.runAfterDelay(20, () -> {
            h.assertTrue(RuneStrike.testLeft(sp, "common") == -1 && SwingLook.testKey(sp) == null, "used up → gone " + RuneStrike.testLeft(sp, "common"));
            h.assertTrue(SwingArea.testTargets(sp, "slash_h").stream().noneMatch(x -> x == g), "reach back to normal");
            MotionSystem.testRequest(sp, "basic", 1, 0);
            sp.attack(a);
            h.assertTrue(MotionSystem.lastSentLook(sp).none(), "no look on a plain swing " + MotionSystem.lastSentLook(sp));
        });
        h.runAfterDelay(22, () -> {
            h.assertTrue(oneHit(got.applyAsDouble(0), sp, w) && oneHit(got.applyAsDouble(1), sp, w) && got.applyAsDouble(2) == 0,
                    "plain swing: no strike, far one untouched " + got.applyAsDouble(0) + " " + got.applyAsDouble(1) + " " + got.applyAsDouble(2));
            done(h, sp);
        });
    }

    /** 가르기 (범위 × 1.4 · 부채꼴 반지름 4): 같은 휘두르기에 맞은 적 (조준 · 범위) 은 부채꼴이 다시 베지 않음, 범위 밖 부채꼴 안의 적만 부채꼴 피해 */
    @GameTest(environment = ENV, maxTicks = 60)
    public void cleaveConeSkipsSwingVictims(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = Weapons.weaponFrom("sword", 20, "rare");
        hold(sp, w);
        Husk a = dummy(h, 1.5, 2, 3.5); // 조준
        Husk e = dummy(h, 3.0, 2, 3.5); // 범위
        Husk f = dummy(h, 1.5, 2, 5.3); // 앞 3.8칸 — 범위 (2.3 × 1.4 = 3.22 + 0.3) 밖, 가르기 부채꼴 (반지름 4) 안
        RpgCombat.forceCrit = false;
        Runes.Rune r = Runes.parseRune("rpg:rune_strike_noble");
        double[] bonus = new double[1];
        h.runAfterDelay(2, () -> {
            h.assertTrue(RuneCast.castRune(sp, r, false), "cast 가르기");
            bonus[0] = RuneCast.power(sp, "strike") * r.def().num("bonus");
            h.assertTrue(SwingArea.testTargets(sp, "slash_h").stream().noneMatch(x -> x == f), "3.8 beyond the widened reach");
            MotionSystem.testRequest(sp, "basic", 1, 0);
            sp.attack(a);
        });
        h.runAfterDelay(6, () -> {
            h.assertTrue(oneHitPlus(TestDummies.dealt(a), sp, w, bonus[0]), "aimed: one hit + strike (no cone) " + TestDummies.dealt(a) + " vs " + (full(sp, w) + bonus[0]));
            h.assertTrue(oneHitPlus(TestDummies.dealt(e), sp, w, bonus[0]), "area: one hit + strike (no cone) " + TestDummies.dealt(e));
            h.assertTrue(TestDummies.dealt(f) > 0 && !oneHitPlus(TestDummies.dealt(f), sp, w, bonus[0]), "cone only beyond the area " + TestDummies.dealt(f));
            h.assertTrue(RuneStrike.testLeft(sp, "noble") == r.def().num("hits") - 1, "one swing = one use " + RuneStrike.testLeft(sp, "noble"));
            RuneStrike.testClear(sp);
            done(h, sp);
        });
    }

    /** 직업 평타 태세: 사신 현신 (낫 — 범위 5칸), 명경지수 (칼만), 활 · 완드 색은 평타 강화 룬만 */
    @GameTest(environment = ENV, maxTicks = 40)
    public void jobStanceLooks(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon scythe = Weapons.weaponFrom("scythe", 20, "noble");
        hold(sp, scythe);
        Husk far = dummy(h, 1.5, 2, 6.0); // 앞 4.5칸 — 낫 고귀 3.3 밖, 사신 현신 5칸 안
        h.runAfterDelay(2, () -> {
            h.assertTrue(SwingArea.testTargets(sp, "slash_h").stream().noneMatch(x -> x == far), "scythe 3.3: 4.5 out of reach");
            h.assertTrue(JobTraits.testStance(sp, "reaper", "reaper_avatar", 100), "reaper avatar on");
            h.assertTrue("reaper_avatar".equals(SwingLook.testKey(sp)), "look " + SwingLook.testKey(sp));
            h.assertTrue(SwingArea.testTargets(sp, "slash_h").stream().anyMatch(x -> x == far), "reaper avatar: 5 blocks");
            h.assertTrue(SwingLook.shotColors(sp, scythe) == null, "job looks: melee only");
            hold(sp, Weapons.weaponFrom("sword", 20, "rare"));
            h.assertTrue(SwingLook.testKey(sp) == null, "reaper look: scythe only");
            h.assertTrue(JobTraits.testStance(sp, "swordsman", "swordsman_mirror", 100) && "swordsman_mirror".equals(SwingLook.testKey(sp)),
                    "mirror look " + SwingLook.testKey(sp));
            Weapon bow = Weapons.weaponFrom("bow", 20, "rare");
            hold(sp, bow);
            h.assertTrue(SwingLook.testKey(sp) == null && SwingLook.shotColors(sp, bow) == null, "mirror: not on a bow");
            h.assertTrue(RuneCast.castRune(sp, Runes.parseRune("rpg:rune_strike_noble"), false), "cast 가르기 (bow)");
            double[][] cs = SwingLook.shotColors(sp, bow);
            h.assertTrue(cs != null && SwingLooks.pack(cs[0]) == SwingLooks.pack(SwingLooks.of("cleave").glow()), "strike rune colors the bow shot");
            JobTraits.testEndStance(sp);
            RuneStrike.testClear(sp);
            h.assertTrue(SwingLook.testKey(sp) == null, "cleared");
            done(h, sp);
        });
    }

    // ------------------------------------------------------------ 구르기

    @GameTest(environment = ENV, maxTicks = 60)
    public void rollRules(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        long[] t0 = new long[1];
        h.runAfterDelay(2, () -> {
            h.assertTrue(MotionSystem.canRoll(sp), "can roll");
            MotionSystem.testRequest(sp, "roll", 0, 90F);
            t0[0] = MotionSystem.lastRoll(sp);
            h.assertTrue(t0[0] == Tasks.now() && "roll".equals(MotionSystem.lastSent(sp)), "rolled " + t0[0]);
            h.assertFalse(MotionSystem.canRoll(sp), "cooldown");
        });
        h.runAfterDelay(8, () -> {
            MotionSystem.testRequest(sp, "roll", 0, 90F);
            h.assertTrue(MotionSystem.lastRoll(sp) == t0[0], "second roll in cooldown ignored");
        });
        h.runAfterDelay(2 + Motions.rollCooldown(), () -> {
            sp.getAbilities().flying = true;
            h.assertFalse(MotionSystem.canRoll(sp), "flying: no roll");
            sp.getAbilities().flying = false;
            h.assertTrue(MotionSystem.canRoll(sp), "cooldown over");
            MotionSystem.testRequest(sp, "roll", 0, -90F);
            h.assertTrue(MotionSystem.lastRoll(sp) > t0[0], "rolled again");
            done(h, sp);
        });
    }

    // ------------------------------------------------------------ 스킬 동작

    @GameTest(environment = ENV, maxTicks = 40)
    public void skillMotionsSent(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = Weapons.weaponFrom("sword", 40, "hero"); // 용살 검기 — skill_slash
        hold(sp, w);
        dummy(h, 1.5, 2, 6.5);
        h.runAfterDelay(2, () -> {
            String id = w.skills().get(0).str("id");
            h.assertTrue(Transcend.castSkill(sp, w, 0), "cast " + id);
            h.assertTrue(Motions.skillMotion(id).equals(MotionSystem.lastSent(sp)), "weapon skill motion " + MotionSystem.lastSent(sp) + " (" + id + ")");
        });
        h.runAfterDelay(6, () -> {
            Runes.Rune r = Runes.parseRune("rpg:rune_str_common"); // 대지 강타 — slam
            h.assertTrue(r != null, "rune");
            h.assertTrue(RuneCast.castRune(sp, r, false, 0), "rune cast");
            h.assertTrue(Motions.skillMotion("rune:" + r.def().str("fn")).equals(MotionSystem.lastSent(sp)), "rune motion " + MotionSystem.lastSent(sp));
            h.assertTrue("rn_ground_slam".equals(MotionSystem.lastSent(sp)), "0.46.0 rune's own motion " + MotionSystem.lastSent(sp));
            done(h, sp);
        });
    }

    /**
     * 0.46.0 룬마다 그 룬만의 동작 (사용자 "룬 쓸때 권왕 같은 애들은 실제로 펀치 모션 … 연막탄이면 실제로 연막 날리는거 같은 모션으로 … 나머지 룬 전부"):
     * 주먹 (연환권) · 소품 (연막 · 사슬 갈고리) · 등급마다 다른 정령 (물의 정령) · 두 손 내려찍기 (마검 해방) — 룬을 쓰면 그 동작이 나감
     */
    @GameTest(environment = ENV, maxTicks = 80)
    public void runeMotionsPerRune(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        hold(sp, Weapons.weaponFrom("sword", 40, "hero"));
        dummy(h, 1.5, 2, 4.5);
        String[][] cases = {{"rpg:rune_monk_common", "rn_chain_fist"}, {"rpg:rune_assassin_noble", "rn_smoke_bomb"}, {"rpg:rune_spirit_rare", "rn_spirit_water"},
            {"rpg:rune_spellblade_transcend", "rn_spellblade_release"}, {"rpg:rune_gladiator_common", "rn_chain_hook"}, {"rpg:rune_int_common", "rn_fireball"}};
        for (int i = 0; i < cases.length; i++) {
            String[] c = cases[i];
            h.runAfterDelay(2 + i * 8, () -> {
                Runes.Rune r = Runes.parseRune(c[0]);
                h.assertTrue(r != null, "rune " + c[0]);
                h.assertTrue(c[1].equals(Motions.runeSkillMotion(r.def().str("fn"), r.grade())), c[0] + " table " + Motions.runeSkillMotion(r.def().str("fn"), r.grade()));
                h.assertTrue(RuneCast.castRune(sp, r, false, 0), "cast " + c[0]);
                h.assertTrue(c[1].equals(MotionSystem.lastSent(sp)), c[0] + " motion " + MotionSystem.lastSent(sp));
            });
        }
        h.runAfterDelay(4 + cases.length * 8, () -> done(h, sp));
    }

    /**
     * 0.47.0 직업 스킬마다 그 스킬만의 동작 (jb_ — 사용자 "직업 스킬부터"): 파쇄격을 쓰면 jb_warrior_crush, 돌격은 jb_warrior_charge 로 달리다가 들이받은 뒤
     * 내려찍는 틱에 서버가 단계 동작 (warrior_charge:slam → jb_warrior_charge_slam) 을 다시 보냄
     */
    @GameTest(environment = ENV, maxTicks = 80)
    public void jobMotionsPerSkill(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        sp.setExperienceLevels(40);
        RpgPlayers.update(sp, mmorpg.fabric.RpgData.of(sp).withLvmax(40));
        RpgPlayers.setStat(sp, "str", 50);
        hold(sp, Weapons.weaponFrom("axe", 0, "common"));
        mmorpg.fabric.job.JobSystem.setJob(sp, "warrior");
        h.runAfterDelay(2, () -> {
            h.assertTrue("jb_warrior_crush".equals(Motions.skillMotion("warrior_crush")), "table " + Motions.skillMotion("warrior_crush"));
            h.assertTrue(mmorpg.fabric.job.JobSkills.press(sp, 0), "crush cast");
            h.assertTrue("jb_warrior_crush".equals(MotionSystem.lastSent(sp)), "crush motion " + MotionSystem.lastSent(sp));
        });
        h.runAfterDelay(24, () -> {
            Vec3 at = h.absoluteVec(new Vec3(1.5, 2, 1.5));
            sp.snapTo(at.x, at.y, at.z, 0F, 0F);
            sp.setYHeadRot(0F);
            dummy(h, 1.5, 2, 5.0);
        });
        h.runAfterDelay(26, () -> {
            mmorpg.fabric.job.JobKit.PHASES.clear();
            h.assertTrue(mmorpg.fabric.job.JobSkills.press(sp, 1), "charge cast");
            h.assertTrue("jb_warrior_charge".equals(MotionSystem.lastSent(sp)), "charge motion " + MotionSystem.lastSent(sp));
        });
        h.runAfterDelay(46, () -> {
            h.assertTrue(mmorpg.fabric.job.JobKit.PHASES.contains("warrior_charge:slam"), "charge slam phase " + mmorpg.fabric.job.JobKit.PHASES);
            h.assertTrue("jb_warrior_charge_slam".equals(MotionSystem.lastSent(sp)), "charge slam motion " + MotionSystem.lastSent(sp));
            mmorpg.fabric.job.JobSystem.setJob(sp, null);
            done(h, sp);
        });
    }
}
