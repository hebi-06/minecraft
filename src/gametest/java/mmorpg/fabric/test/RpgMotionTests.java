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
import mmorpg.fabric.Tasks;
import mmorpg.fabric.motion.MotionSystem;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.skill.Transcend;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 0.38.0 동작 게임 시험 (서버): 평타 연타 단계 (칼 가로 → 세로 → 회전 → 다시 가로, comboWindow 를 넘기면 처음, 클라이언트가 보낸 단계를 씀 · 범위 밖 단계는 무시,
 * 원거리 무기는 없음), 회전 베기 (3단) 만 둘레 (뒤 · 옆) 적에게 맞힌 피해 × 0.4 — 1단은 안 맞음, 낫은 넓게 베기 대신 (앞 비스듬한 적은 한 번만),
 * 구르기 (재사용 대기 · 날기 중 막힘 · 둘레에 알림), 스킬 동작 (무기 스킬 · 룬 → MOTION.skills 의 동작을 알림).
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

    // ------------------------------------------------------------ 회전 베기 둘레 판정

    @GameTest(environment = ENV, maxTicks = 60)
    public void spinHitsAround(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = Weapons.weaponFrom("sword", 20, "rare");
        hold(sp, w);
        Husk a = dummy(h, 1.5, 2, 3.5); // 앞 2칸 (맞힌 적)
        Husk b = dummy(h, 1.5, 2, 0.6); // 뒤 0.9칸
        Husk c = dummy(h, 3.6, 2, 1.5); // 옆 2.1칸
        Husk d = dummy(h, 6.4, 2, 5.6); // 6.4칸 — 둘레 밖
        RpgCombat.forceCrit = false;
        double[] main = new double[1];
        h.runAfterDelay(2, () -> {
            MotionSystem.testRequest(sp, "basic", 1, 0);
            sp.attack(a);
            main[0] = TestDummies.dealt(a);
            h.assertTrue(main[0] > 0, "slash_h hit " + main[0]);
            h.assertTrue(TestDummies.dealt(b) == 0 && TestDummies.dealt(c) == 0, "slash_h: behind · side not hit " + TestDummies.dealt(b) + " " + TestDummies.dealt(c));
        });
        h.runAfterDelay(16, () -> {
            MotionSystem.testRequest(sp, "basic", 3, 0);
            sp.attack(a);
            double hit = TestDummies.dealt(a) - main[0];
            h.assertTrue(hit > 0, "spin main hit " + hit);
            double r = Motions.spinRatio();
            h.assertTrue(Math.abs(TestDummies.dealt(b) - hit * r) < 1e-3, "spin behind " + TestDummies.dealt(b) + " vs " + hit * r);
            h.assertTrue(Math.abs(TestDummies.dealt(c) - hit * r) < 1e-3, "spin side " + TestDummies.dealt(c) + " vs " + hit * r);
            h.assertTrue(TestDummies.dealt(d) == 0, "far not hit " + TestDummies.dealt(d));
            done(h, sp);
        });
    }

    @GameTest(environment = ENV, maxTicks = 60)
    public void scytheSpinReplacesSweep(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = Weapons.weaponFrom("scythe", 20, "noble");
        hold(sp, w);
        Husk a = dummy(h, 1.5, 2, 3.5);
        Husk b = dummy(h, 3.0, 2, 3.5); // 앞 비스듬히 — 넓게 베기
        Husk c = dummy(h, 1.5, 2, 0.6); // 뒤
        RpgCombat.forceCrit = false;
        double[] m1 = new double[3];
        h.runAfterDelay(2, () -> {
            MotionSystem.testRequest(sp, "basic", 1, 0);
            sp.attack(a);
            m1[0] = TestDummies.dealt(a);
            m1[1] = TestDummies.dealt(b);
            double ratio = w.typeDef().obj("sweep").num("ratio");
            h.assertTrue(Math.abs(m1[1] - m1[0] * ratio) < 1e-3, "slash_h sweep " + m1[1] + " vs " + m1[0] * ratio);
            h.assertTrue(TestDummies.dealt(c) == 0, "slash_h: behind not hit");
        });
        h.runAfterDelay(16, () -> {
            MotionSystem.testRequest(sp, "basic", 3, 0);
            sp.attack(a);
            double hit = TestDummies.dealt(a) - m1[0];
            double ratio = Math.max(Motions.spinRatio(), w.typeDef().obj("sweep").num("ratio"));
            h.assertTrue(Math.abs(TestDummies.dealt(b) - m1[1] - hit * ratio) < 1e-3, "spin front-side once " + (TestDummies.dealt(b) - m1[1]) + " vs " + hit * ratio);
            h.assertTrue(Math.abs(TestDummies.dealt(c) - hit * ratio) < 1e-3, "spin behind " + TestDummies.dealt(c) + " vs " + hit * ratio);
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
