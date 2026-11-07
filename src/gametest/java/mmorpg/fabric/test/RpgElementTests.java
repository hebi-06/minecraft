package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Elements;
import mmorpg.core.Jobs;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.job.ElementSystem;
import mmorpg.fabric.job.JobSkills;
import mmorpg.fabric.job.JobSystem;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneMarks;
import mmorpg.fabric.stattree.StatTrees;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.AABB;

/**
 * 2026-11-07 원소 반응 게임 시험 (core.Elements · job.ElementSystem — 환경 mmorpg:element):
 * <ul>
 * <li>원소 스킬을 쓰는 동안 들어간 직업 피해가 원소를 묻힘 (피닉스 배시 — 불 · 6초) · 무속성 맥락 · 룬 피해 (맥락의 원소) · 같은 원소면 다시 6초</li>
 * <li>반응: 증발 (즉시 피해 = 공격력 × 2.5 · 숫자 앞 "증발" · 두 원소를 지움) · 같은 적 2초에 한 번 (그동안 묻은 원소는 그대로) · 감전 (기절) · 초전도 (둘레 받는 피해
 * +10%) · 과부하 (둘레 3칸 1.5씩) · 용암 (불타는 땅 0.3 × 6) · 진흙 (묶음) · 자력 (밀 수 있는 적을 끌어모음)</li>
 * <li>시험 과녁은 꼬리표 mmorpg_elements 가 있을 때만 원소가 묻음 (옛 시험의 숫자 그대로)</li>
 * </ul>
 */
public final class RpgElementTests {
    static final String ENV = "mmorpg:element";

    /** 원소 시험 과녁 (밀려나지 않음 · 원소가 묻음) */
    static Husk target(GameTestHelper h, double dx, double dz, boolean anchored) {
        Husk m = RpgJobAdvanceTests.dummy(h, dx, dz);
        if (!anchored) m.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
        m.addTag(ElementSystem.TEST_ELEMENTS);
        return m;
    }

    /** 둘레의 글자 표시 중 text 가 든 것이 있는지 */
    static boolean hasText(GameTestHelper h, Husk z, String text) {
        for (Display.TextDisplay d : h.getLevel().getEntitiesOfClass(Display.TextDisplay.class, new AABB(z.blockPosition()).inflate(4), e -> true)) {
            Component c = d.getText();
            if (c != null && c.getString().contains(text)) return true;
        }
        return false;
    }

    static boolean rooted(Husk z) {
        MobEffectInstance s = z.getEffect(MobEffects.SLOWNESS);
        return s != null && s.getAmplifier() >= 10;
    }

    @GameTest(environment = ENV, maxTicks = 400)
    public void reactions(GameTestHelper h) {
        RpgJobAdvanceTests.floor(h);
        ServerPlayer sp = RpgJobAdvanceTests.player(h, "phoenix", 50);
        RpgJobTests.hold(sp, Weapons.weaponFrom("shield", 0, "common"));
        Jobs.Job j = Jobs.job("phoenix");
        ElementSystem.testClear();
        RpgCombat.forceCrit = false;
        double P = JobSystem.power(sp, j);
        double base = P * StatTrees.atkMult(sp) * RpgCombat.dealtMult.applyAsDouble(sp);
        Elements.Reaction vap = Elements.byId("vaporize");
        List<Husk> all = new ArrayList<>();
        Husk a = target(h, 0, 2.5, true);
        all.add(a);
        Husk plain = RpgJobAdvanceTests.dummy(h, -2, 2.5); // 꼬리표 없는 시험 과녁
        all.add(plain);
        double[] mark = {0, 0};
        List<RpgJobTests.Step> steps = new ArrayList<>();
        // 피닉스 배시 (불 원소 스킬 — 6 에 내리침 4): 맞은 적에 불이 묻음 (6초) · 피해 = 위력 × 피해 배율 (원소 −5% 든) × 4
        steps.add(new RpgJobTests.Step(1, () -> {
            RpgJobAdvanceTests.home(h, sp);
            h.assertTrue("fire".equals(Jobs.element(j.skill(0))) && Jobs.elementMult(j.skill(0)) < 1, "phoenix bash is a fire skill (−5%)");
            h.assertTrue(JobSkills.press(sp, 0), "피닉스 배시 cast");
        }));
        steps.add(new RpgJobTests.Step(10, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(0), 4);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(a), want), "bash " + TestDummies.dealt(a) + " vs " + want);
            h.assertTrue("fire".equals(ElementSystem.auraOf(a)) && ElementSystem.auraLeft(a) > 100 && ElementSystem.auraLeft(a) <= 120, "fire attached: "
                    + ElementSystem.auraOf(a) + " " + ElementSystem.auraLeft(a));
            h.assertTrue(ElementSystem.auraOf(plain) == null, "plain test dummy: no element");
            // 물 → 증발 (즉시 피해 2.5 · 두 원소를 지움 · 2초 대기)
            mark[0] = TestDummies.dealt(a);
            Elements.Reaction r = ElementSystem.apply(sp, a, "water", P, true, false);
            h.assertTrue(r == vap, "fire + water = vaporize: " + r);
            double got = TestDummies.dealt(a) - mark[0];
            h.assertTrue(RpgJobTests.near(got, base * 2.5), "vaporize " + got + " vs " + base * 2.5);
            h.assertTrue(ElementSystem.auraOf(a) == null && ElementSystem.icdLeft(a) == 40, "both elements gone · icd 2s " + ElementSystem.icdLeft(a));
            h.assertTrue(hasText(h, a, "증발"), "number shows 증발");
            // 대기 중: 불이 다시 묻고 물은 반응하지 않음 (불은 그대로)
            h.assertTrue(ElementSystem.apply(sp, a, "fire", P, true, false) == null && "fire".equals(ElementSystem.auraOf(a)), "fire again");
            mark[0] = TestDummies.dealt(a);
            h.assertTrue(ElementSystem.apply(sp, a, "water", P, true, false) == null && "fire".equals(ElementSystem.auraOf(a)) && TestDummies.dealt(a) == mark[0],
                    "no reaction while waiting (fire stays)");
        }));
        steps.add(new RpgJobTests.Step(30, () -> {
            // 같은 원소: 다시 6초
            long left = ElementSystem.auraLeft(a);
            ElementSystem.apply(sp, a, "fire", P, true, false);
            h.assertTrue(left < 100 && ElementSystem.auraLeft(a) == 120, "same element refreshes: " + left + " → " + ElementSystem.auraLeft(a));
        }));
        steps.add(new RpgJobTests.Step(12, () -> {
            h.assertTrue(ElementSystem.icdLeft(a) == 0, "wait over");
            mark[0] = TestDummies.dealt(a);
            h.assertTrue(ElementSystem.apply(sp, a, "water", P, true, false) == vap && RpgJobTests.near(TestDummies.dealt(a) - mark[0], base * 2.5),
                    "vaporize again after 2s");
            // 감전 (전기 + 물): 기절 1초
            Husk b = target(h, 3, 6, true);
            all.add(b);
            ElementSystem.apply(sp, b, "thunder", P, true, false);
            h.assertTrue(ElementSystem.apply(sp, b, "water", P, true, false) == Elements.byId("shock") && rooted(b) && b.hasEffect(MobEffects.WEAKNESS),
                    "thunder + water = shock (stun)");
            // 초전도 (얼음 + 전기): 둘레 받는 피해 +10% 5초
            Husk c = target(h, 6, 6, true);
            all.add(c);
            ElementSystem.apply(sp, c, "ice", P, true, false);
            h.assertTrue(ElementSystem.apply(sp, c, "thunder", P, true, false) == Elements.byId("superconduct") && RuneMarks.vulnLeft(c) > 90
                    && RpgJobTests.near(RuneMarks.vulnMult(c), 1.1), "ice + thunder = superconduct (+10%) " + RuneMarks.vulnMult(c));
            // 과부하 (불 + 전기): 둘레 3칸 적마다 1.5
            Husk d = target(h, -1, 10, true);
            Husk d2 = target(h, 1, 10, true);
            all.add(d);
            all.add(d2);
            ElementSystem.apply(sp, d, "fire", P, true, false);
            double d0 = TestDummies.dealt(d), e0 = TestDummies.dealt(d2);
            h.assertTrue(ElementSystem.apply(sp, d, "thunder", P, true, false) == Elements.byId("overload"), "fire + thunder = overload");
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(d) - d0, base * 1.5) && RpgJobTests.near(TestDummies.dealt(d2) - e0, base * 1.5), "overload 1.5 each: "
                    + (TestDummies.dealt(d) - d0) + " / " + (TestDummies.dealt(d2) - e0) + " vs " + base * 1.5);
            h.assertTrue(hasText(h, d, "과부하"), "number shows 과부하");
            // 진흙 (바위 + 물): 2초 묶음
            Husk g = target(h, 6, 10, true);
            all.add(g);
            ElementSystem.apply(sp, g, "rock", P, true, false);
            h.assertTrue(ElementSystem.apply(sp, g, "water", P, true, false) == Elements.byId("mud") && rooted(g) && !g.hasEffect(MobEffects.WEAKNESS),
                    "rock + water = mud (root)");
        }));
        // 용암 (불 + 바위): 발밑 2.5칸 불타는 땅 — 0.5초마다 0.3 × 6 (때린 이가 있는 피해만 — 불붙은 피해는 뺌)
        steps.add(new RpgJobTests.Step(41, () -> {
            Husk f = target(h, 7, 2.5, true);
            all.add(f);
            ElementSystem.apply(sp, f, "fire", P, true, false);
            mark[1] = TestDummies.dealtBy(f);
            h.assertTrue(ElementSystem.apply(sp, f, "rock", P, true, false) == Elements.byId("lava"), "fire + rock = lava");
        }));
        steps.add(new RpgJobTests.Step(56, () -> {
            Husk f = all.get(all.size() - 1);
            double got = TestDummies.dealtBy(f) - mark[1];
            h.assertTrue(RpgJobTests.near(got, base * 0.3 * 6), "lava 0.3 × 6: " + got + " vs " + base * 1.8);
            // 자력 (바위 + 전기): 둘레 5칸 밀 수 있는 적을 끌어모음
            Husk m = target(h, 0, 11, true);
            Husk q = target(h, 4, 11, false);
            all.add(m);
            all.add(q);
            ElementSystem.apply(sp, m, "rock", P, true, false);
            h.assertTrue(ElementSystem.apply(sp, m, "thunder", P, true, false) == Elements.byId("magnet"), "rock + thunder = magnet");
        }));
        steps.add(new RpgJobTests.Step(10, () -> {
            Husk m = all.get(all.size() - 2);
            Husk q = all.get(all.size() - 1);
            double dd = Math.hypot(q.getX() - m.getX(), q.getZ() - m.getZ());
            h.assertTrue(dd < 2.0, "magnet pulled the foe in: " + dd);
            // 룬 피해: 맥락의 원소를 묻힘 (얼음 룬 — 룬 위력) · 맥락이 없으면 무속성
            Husk r = target(h, 7, 8, true);
            all.add(r);
            double rp = RuneCast.power(sp, "int");
            RuneKit.runeHit(sp, r, rp, 1.0, "int");
            h.assertTrue(ElementSystem.auraOf(r) == null, "no context → 무속성");
            ElementSystem.with("ice", rp, false, () -> RuneKit.runeHit(sp, r, rp, 1.0, "int"));
            h.assertTrue("ice".equals(ElementSystem.auraOf(r)), "rune hit in an ice context attaches ice: " + ElementSystem.auraOf(r));
            h.assertTrue(ElementSystem.LOG.size() >= 7, "reaction log " + ElementSystem.LOG);
        }));
        RpgJobTests.chain(h, steps, 0, () -> {
            ElementSystem.testClear();
            RpgJobTests.cleanup(sp);
            for (Husk x : all) x.discard();
            RpgGearTests.done(h, sp);
        });
    }
}
