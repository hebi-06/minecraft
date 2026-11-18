package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.StatTree;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.job.CommonSkills;
import mmorpg.fabric.job.ElementSystem;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.skill.Guards;
import mmorpg.fabric.stattree.StatTrees;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.zombie.Husk;

/**
 * 2026-11-18 축복의 길 공용 스킬 게임 시험 (CommonSkills — 스탯 트리 40 · 90 칸에서 고른 길이 여는 스킬, 하나만 끼움):
 * <ul>
 * <li>데이터의 16 모두 구현 · 갈래를 고르기 전엔 열린 것 없음 (쓰면 안 됨) · 고르면 그 길의 스킬이 열리고 끼워짐 · 닫힌 스킬은 못 끼움 · 갈래를 지우면 닫힘</li>
 * <li>작은 공격 (크라이오 펄스 — 마력 · 얼음): 피해 = 룬 위력 (마력) × 2.71 × 공격력 보너스 · 얼음이 묻음 · 구속 · 재사용 대기 (10초 × 대기 배율) 동안 다시 못 씀</li>
 * <li>이동기 덤: 라이트닝 스프린트 = 신속 II 4초 · 스틸 차지 = 끝나고 받는 피해 -20%</li>
 * </ul>
 */
public final class RpgCommonSkillTests {
    static final String ENV = "mmorpg:common";

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        TestSetup.treeOn(sp);
        CommonSkills.resetCooldown(sp);
        return sp;
    }

    private static void done(GameTestHelper h, ServerPlayer sp) {
        CommonSkills.resetCooldown(sp);
        TestSetup.TREE_ON.remove(sp.getUUID());
        h.getLevel().getServer().getPlayerList().remove(sp);
        h.succeed();
    }

    @GameTest(environment = ENV, maxTicks = 20)
    public void openAndEquip(GameTestHelper h) {
        h.assertTrue(CommonSkills.implemented().equals(StatTree.skillDefs().keySet()), "16 모두 구현: " + CommonSkills.implemented());
        ServerPlayer sp = player(h);
        RpgPlayers.setStat(sp, "str", 40);
        h.assertTrue(StatTrees.openSkills(sp).isEmpty() && CommonSkills.equipped(sp) == null, "갈래를 안 고르면 열린 공용 스킬 없음");
        h.assertTrue(!CommonSkills.press(sp), "없으면 못 씀");
        h.assertTrue(StatTrees.pick(sp, "str", 1, 0) == null, "광전사");
        h.assertTrue(StatTrees.openSkills(sp).equals(List.of("blaze_leap")) && "blaze_leap".equals(CommonSkills.equipped(sp)),
                "광전사 길 → 블레이즈 리프 (끼워짐): " + StatTrees.openSkills(sp));
        h.assertTrue(CommonSkills.equip(sp, "steel_charge") != null, "닫힌 스킬은 못 끼움");
        RpgPlayers.setStat(sp, "str", 90);
        h.assertTrue(StatTrees.pick(sp, "str", 6, 1) == null, "둘째 갈래");
        h.assertTrue(StatTrees.openSkills(sp).equals(List.of("blaze_leap", "ember_nova")), "둘째 갈래 → 엠버 노바: " + StatTrees.openSkills(sp));
        h.assertTrue("blaze_leap".equals(CommonSkills.equipped(sp)), "끼운 것 그대로");
        h.assertTrue(CommonSkills.equip(sp, "ember_nova") == null && "ember_nova".equals(CommonSkills.equipped(sp)), "엠버 노바로 바꿔 낌");
        StatTrees.clear(sp);
        h.assertTrue(StatTrees.openSkills(sp).isEmpty() && CommonSkills.equipped(sp) == null, "갈래를 지우면 닫힘");
        TestSetup.treeOff(sp);
        h.assertTrue(StatTrees.openSkills(sp).isEmpty(), "스탯 트리를 끄면 없음");
        done(h, sp);
    }

    @GameTest(environment = ENV, maxTicks = 60)
    public void attackHitsWithElement(GameTestHelper h) {
        RpgJobAdvanceTests.floor(h);
        ServerPlayer sp = player(h);
        RpgJobAdvanceTests.home(h, sp);
        ElementSystem.testClear();
        RpgCombat.forceCrit = false;
        RpgPlayers.setStat(sp, "int", 90);
        h.assertTrue(StatTrees.pick(sp, "int", 1, 0) == null && StatTrees.pick(sp, "int", 6, 0) == null, "마력 갈래 둘");
        h.assertTrue(StatTrees.openSkills(sp).contains("cryo_pulse") && CommonSkills.equip(sp, "cryo_pulse") == null, "크라이오 펄스 끼움");
        Husk z = RpgElementTests.target(h, 0, 2, true);
        Husk far = RpgElementTests.target(h, 0, 6, true); // 3.5칸 밖
        StatTree.Skill k = StatTree.skill("cryo_pulse");
        double want = RuneCast.power(sp, "int") * k.mult() * StatTrees.atkMult(sp) * RpgCombat.dealtMult.applyAsDouble(sp);
        int cd = (int) Math.round(k.cd() * 20 * StatTrees.cdMult(sp));
        List<RpgJobTests.Step> steps = new ArrayList<>();
        steps.add(new RpgJobTests.Step(1, () -> {
            RpgJobAdvanceTests.home(h, sp);
            h.assertTrue(CommonSkills.press(sp), "크라이오 펄스 씀");
            h.assertTrue(CommonSkills.cooldownLeft(sp) == cd, "재사용 대기 " + CommonSkills.cooldownLeft(sp) + " / " + cd);
            h.assertTrue(!CommonSkills.press(sp), "재사용 대기 동안 다시 못 씀");
        }));
        steps.add(new RpgJobTests.Step(8, () -> {
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z), want), "피해 " + TestDummies.dealt(z) + " vs " + want);
            h.assertTrue(TestDummies.dealt(far) == 0, "3.5칸 밖은 안 맞음 " + TestDummies.dealt(far));
            h.assertTrue("ice".equals(ElementSystem.auraOf(z)), "얼음이 묻음: " + ElementSystem.auraOf(z));
            MobEffectInstance slow = z.getEffect(MobEffects.SLOWNESS);
            h.assertTrue(slow != null && slow.getAmplifier() == 0, "구속 I");
            h.assertTrue(RpgJobTests.near(CommonSkills.lastDealt, want) && "cryo_pulse".equals(CommonSkills.lastCast), "마지막 기록 " + CommonSkills.lastDealt);
        }));
        RpgJobTests.chain(h, steps, 0, () -> {
            RpgCombat.forceCrit = null;
            ElementSystem.testClear();
            done(h, sp);
        });
    }

    @GameTest(environment = ENV, maxTicks = 40)
    public void moveBuffs(GameTestHelper h) {
        RpgJobAdvanceTests.floor(h);
        ServerPlayer sp = player(h);
        RpgJobAdvanceTests.home(h, sp);
        List<RpgJobTests.Step> steps = new ArrayList<>();
        steps.add(new RpgJobTests.Step(1, () -> {
            h.assertTrue(CommonSkills.cast(sp, "lightning_sprint", true), "라이트닝 스프린트");
            MobEffectInstance speed = sp.getEffect(MobEffects.SPEED);
            h.assertTrue(speed != null && speed.getAmplifier() == 1 && speed.getDuration() > 70, "신속 II 4초");
            CommonSkills.resetCooldown(sp);
            RpgJobAdvanceTests.home(h, sp);
            h.assertTrue(CommonSkills.cast(sp, "steel_charge", true), "스틸 차지");
        }));
        steps.add(new RpgJobTests.Step(9, () -> {
            Guards.Guard g = Guards.of(sp);
            h.assertTrue(g != null && Math.abs(g.reduce() - 0.20) < 1e-9, "끝나고 받는 피해 -20%: " + (g == null ? null : g.reduce()));
        }));
        RpgJobTests.chain(h, steps, 0, () -> done(h, sp));
    }
}
