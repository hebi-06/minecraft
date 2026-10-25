package mmorpg.fabric.test;

import java.lang.reflect.Method;
import java.util.List;
import mmorpg.core.Motions;
import mmorpg.core.Runes;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.Slash;
import mmorpg.fabric.SwingArea;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.motion.MotionSystem;
import mmorpg.fabric.rune.RuneCast;
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
        h.runAfterDelay(2, () -> {
            MotionSystem.testRequest(sp, "basic", 1, 0);
            sp.attack(a);
            main[0] = TestDummies.dealt(a);
            h.assertTrue(near(main[0], full(sp, w)), "slash_h aimed hit full " + main[0] + " vs " + full(sp, w));
            h.assertTrue(TestDummies.dealt(e) == 0, "area lands one tick later (after the aimed hit)");
        });
        h.runAfterDelay(4, () -> {
            h.assertTrue(near(TestDummies.dealt(e), main[0]), "slash_h arc: front-left 100% " + TestDummies.dealt(e) + " vs " + main[0]);
            h.assertTrue(near(TestDummies.dealt(a), main[0]), "aimed target hit once " + TestDummies.dealt(a));
            h.assertTrue(TestDummies.dealt(b) == 0 && TestDummies.dealt(c) == 0 && TestDummies.dealt(d) == 0,
                    "slash_h: behind · side · far not hit " + TestDummies.dealt(b) + " " + TestDummies.dealt(c) + " " + TestDummies.dealt(d));
            h.assertTrue(SwingArea.lastCount(sp) == 1, "one area victim " + SwingArea.lastCount(sp));
        });
        h.runAfterDelay(16, () -> {
            MotionSystem.testRequest(sp, "basic", 3, 0);
            sp.attack(a);
            main[1] = TestDummies.dealt(a) - main[0];
            h.assertTrue(near(main[1], main[0]), "spin aimed hit full " + main[1]);
        });
        h.runAfterDelay(18, () -> {
            h.assertTrue(near(TestDummies.dealt(b), main[1]), "spin ring: behind 100% " + TestDummies.dealt(b) + " vs " + main[1]);
            h.assertTrue(near(TestDummies.dealt(c), main[1]), "spin ring: side 100% " + TestDummies.dealt(c) + " vs " + main[1]);
            h.assertTrue(near(TestDummies.dealt(e), main[0] + main[1]), "spin ring: front-left again " + TestDummies.dealt(e));
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
        Husk b = dummy(h, 4.2, 2, 3.0); // 앞 왼쪽 약 61° · 3.1칸 — 칼 부채꼴 (±50°) 밖, 낫 (±60° · 3.6칸 + 몸 폭) 안
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
            h.assertTrue(near(TestDummies.dealt(b), main[0]), "scythe arc 120°: wide side 100% " + TestDummies.dealt(b) + " vs " + main[0]);
            h.assertTrue(TestDummies.dealt(c) == 0, "scythe: behind not hit");
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
            h.assertTrue(near(main, full(sp, w)), "aimed (after the punch) hit once, full " + main + " vs " + full(sp, w));
            h.assertTrue(near(TestDummies.dealt(e), main), "air swing area: front-left 100% " + TestDummies.dealt(e));
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
        h.runAfterDelay(2, () -> {
            ItemStack held = sp.getMainHandItem();
            h.assertTrue(held.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
                    .anyMatch(m -> m.attribute().equals(Attributes.ATTACK_SPEED) && m.modifier().amount() >= 90), "weapon attack speed +96 (no vanilla cooldown)");
            sp.resetAttackStrengthTicker(); // 바로 앞에 친 것처럼 (예전에는 충전 0 → 20%)
            sp.attack(a);
            h.assertTrue(near(TestDummies.dealt(a), full(sp, w)), "full damage right after a reset " + TestDummies.dealt(a) + " vs " + full(sp, w));
        });
        h.runAfterDelay(3, () -> {
            // 1틱 뒤 다시 (몹의 연속 피해 막기 시간 안) — 예전에는 막히거나 차이만
            sp.attack(a);
            h.assertTrue(near(TestDummies.dealt(a), 2 * full(sp, w)), "second hit 1 tick later also full " + TestDummies.dealt(a));
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
            done(h, sp);
        });
    }
}
