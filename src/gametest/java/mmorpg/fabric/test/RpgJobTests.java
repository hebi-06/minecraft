package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mmorpg.core.Combat;
import mmorpg.core.Economy;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgData;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.job.JobSkills;
import mmorpg.fabric.job.JobSystem;
import mmorpg.fabric.job.JobTrainer;
import mmorpg.fabric.job.JobTraits;
import mmorpg.fabric.skill.Guards;
import mmorpg.fabric.stattree.StatTrees;
import mmorpg.fabric.ui.FormNet;
import mmorpg.fabric.ui.Forms;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 0.27.0 직업 게임 시험 (서버):
 * <ul>
 * <li>틀 (환경 mmorpg:job): 규칙 · 효과가 들어간 스킬, 직업 바꾸기 (자원 · 재사용 대기 지움), 칸 잠김 (해금 레벨 · 직업 무기 · 요구 스탯), 마검사 「마력 검술」 (칼의 요구 힘
 * = 힘 + 마력 · 배율의 힘 = 힘 + 마력 × 0.5 — 티르빙은 그대로), 검세 (칼 평타 +1 · 4초 못 맞히면 하나씩), 전직 창 (Forms.HEADLESS — 10렙 전 대사 · 첫 전직 무료 ·
 * 바꾸기 300 은화 · 모자람 · 준비 중 · 같은 직업)</li>
 * <li>검사 (환경 mmorpg:job_sw) · 마검사 (mmorpg:job_sb): 스킬 다섯을 차례로 (칸의 키와 같은 길 — JobSkills.press) — 과녁 하나가 받은 피해 = 위력 P × 지속 보정 ×
 * 배율 합 × 스탯 트리 공격력 × 가호, 직업 색 그라데이션 숫자, 태세 (명경지수 두 번 베기 · 받는 피해 −15% / 마검 각성 잔상 · 사거리 +1), 섬보 뒤 평타 치명타,
 * 마력 각인 (스킬이 찍음 · 평타가 하나 터뜨림 · 비전 거검이 모두 터뜨림)</li>
 * </ul>
 * 범위 스킬이 다른 시험의 과녁을 맞히지 않게 직업마다 환경을 따로 둠 (같은 환경의 시험만 함께 돎).
 */
public final class RpgJobTests {
    static final String ENV = "mmorpg:job";
    static final String ENV_SW = "mmorpg:job_sw";
    static final String ENV_SB = "mmorpg:job_sb";

    // ------------------------------------------------------------ 도구

    /** level 렙 · 직업 · 스탯 · 칼 (일반 0렙) 을 든 시험 플레이어 */
    static ServerPlayer jobPlayer(GameTestHelper h, String job, Map<String, Integer> stats, int level) {
        ServerPlayer sp = RpgGearTests.player(h);
        sp.setExperienceLevels(level);
        RpgPlayers.update(sp, RpgData.of(sp).withLvmax(level));
        for (var e : stats.entrySet()) RpgPlayers.setStat(sp, e.getKey(), e.getValue());
        hold(sp, Weapons.weaponFrom("sword", 0, "common"));
        JobSystem.setJob(sp, job);
        return sp;
    }

    static void hold(ServerPlayer sp, Weapon w) {
        sp.setItemInHand(InteractionHand.MAIN_HAND, w == null ? ItemStack.EMPTY : new ItemStack(RpgItems.byId(w.id())));
    }

    /** 돌 바닥 (8 × 8 — 섬보 · 마력 도약이 설 땅) */
    static void floor(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }

    /** 시험 자리 (1.5, 2, 1.5) 에 남쪽 (+z) 을 보고 섬 */
    static void home(GameTestHelper h, ServerPlayer sp) {
        Vec3 at = h.absoluteVec(new Vec3(1.5, 2, 1.5));
        sp.snapTo(at.x, at.y, at.z, 0F, 0F);
        sp.setYHeadRot(0F);
    }

    /** 앞 (남쪽) ahead 칸의 과녁 (쓰러지지 않음 — 받은 피해는 TestDummies.dealt) */
    static Husk dummy(GameTestHelper h, double ahead) {
        return TestDummies.make(h.spawnWithNoFreeWill(EntityTypes.HUSK, 1.5F, 2F, (float) (1.5 + ahead)));
    }

    /** 스킬 하나의 기대 피해: 위력 P × 지속 보정 × 배율 × 스탯 트리 공격력 × 가호 */
    static double expected(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double mult) {
        return JobSystem.power(sp, j) * Jobs.spreadMult(k) * mult * StatTrees.atkMult(sp) * RpgCombat.dealtMult.applyAsDouble(sp);
    }

    static boolean near(double a, double b) {
        return Math.abs(a - b) <= 1e-3 + Math.abs(b) * 2e-4;
    }

    /** 평타 한 번의 피해 (치명타 없이 — 지금 공격 충전) */
    static double basic(ServerPlayer sp, Weapon w) {
        float s = sp.getAttackStrengthScale(0.5F);
        return w.damage() * RpgCombat.powerOf(sp, w) * (0.2 + s * s * 0.8);
    }

    /** 과녁 둘레의 피해 숫자 중 직업 색 그라데이션 (첫 글자 색이 직업 첫째 빛 · 굵게) 이 있는지 */
    static boolean gradientNumber(GameTestHelper h, Husk z, Jobs.Job j) {
        int a = Jobs.rgb(j.color()[0]);
        for (Display.TextDisplay d : h.getLevel().getEntitiesOfClass(Display.TextDisplay.class, new AABB(z.blockPosition()).inflate(4), e -> true)) {
            Component c = d.getText();
            for (Component part : c.getSiblings()) {
                TextColor col = part.getStyle().getColor();
                if (col == null) continue;
                if (col.getValue() == a && part.getStyle().isBold()) return true;
                break;
            }
        }
        return false;
    }

    /** 차례로 할 일 (wait 틱 뒤 run) */
    record Step(int wait, Runnable run) {}

    static void chain(GameTestHelper h, List<Step> steps, int i, Runnable end) {
        if (i >= steps.size()) {
            end.run();
            return;
        }
        Step s = steps.get(i);
        h.runAfterDelay(Math.max(1, s.wait()), () -> {
            s.run().run();
            chain(h, steps, i + 1, end);
        });
    }

    static void cleanup(ServerPlayer sp) {
        RpgCombat.forceCrit = null;
        JobSkills.resetCooldowns(sp);
        JobSystem.setJob(sp, null);
        Forms.HEADLESS.remove(sp.getUUID());
    }

    // ------------------------------------------------------------ 틀

    /** 규칙 · 효과 · 칸 · 직업 바꾸기 */
    @GameTest(environment = ENV, maxTicks = 20)
    public void rulesAndSlots(GameTestHelper h) {
        ServerPlayer sp = jobPlayer(h, null, Map.of("str", 50), 40);
        try {
            h.assertTrue(Jobs.all().size() == 11, "11 jobs");
            for (Jobs.Job j : Jobs.all()) {
                if (!j.ready()) continue;
                for (Jobs.Skill k : j.skills()) h.assertTrue(JobSystem.implemented(k.id()), "implemented " + k.id());
            }
            h.assertTrue(JobSkills.skill(sp, 0) == null && JobSystem.job(sp) == null, "no job: empty slots");
            JobSystem.setJob(sp, "warrior");
            h.assertTrue(JobSystem.job(sp) != null && JobSkills.skill(sp, 0) == null, "not-ready job: no slots yet");
            JobSystem.setJob(sp, "swordsman");
            JobSkills.Skill s0 = JobSkills.skill(sp, 0);
            h.assertTrue(s0 != null && s0.name().equals("연참") && s0.lock(sp).isEmpty(), "swordsman slot 1 = 연참, usable");
            h.assertTrue(s0.cooldown() == JobSystem.cooldownTicks(sp, Jobs.job("swordsman").skill(0)), "cooldown from rules (× stat tree)");
            h.assertTrue(JobSkills.payload(sp).job().equals("swordsman") && JobSkills.payload(sp).colorA() == Jobs.rgb(Jobs.job("swordsman").color()[0]),
                    "bar payload carries the job and its color");
            // 해금 레벨: 12렙이면 칸 2 (15렙) 부터 잠김
            sp.setExperienceLevels(12);
            RpgPlayers.update(sp, RpgData.of(sp).withLvmax(12));
            h.assertTrue(JobSkills.skill(sp, 0).lock(sp).isEmpty() && JobSkills.skill(sp, 1).lock(sp).equals("15렙") && JobSkills.skill(sp, 4).lock(sp).equals("40렙"),
                    "unlock levels: " + JobSkills.skill(sp, 1).lock(sp) + " / " + JobSkills.skill(sp, 4).lock(sp));
            h.assertFalse(JobSkills.press(sp, 1), "locked slot does not cast");
            h.assertTrue(JobSkills.cooldownLeft(sp, "swordsman_flash") == 0, "no cooldown when locked");
            sp.setExperienceLevels(40);
            RpgPlayers.update(sp, RpgData.of(sp).withLvmax(40));
            // 직업 무기: 맨손 · 도끼는 잠김 (칼)
            hold(sp, null);
            h.assertTrue(JobSkills.skill(sp, 0).lock(sp).equals("칼"), "bare hand locks: " + JobSkills.skill(sp, 0).lock(sp));
            hold(sp, Weapons.weaponFrom("axe", 0, "common"));
            h.assertTrue(JobSkills.skill(sp, 0).lock(sp).equals("칼"), "axe locks swordsman");
            // 요구 스탯: 힘이 모자란 40렙 초월 칼
            hold(sp, Weapons.weaponFrom("sword", 40, "transcend"));
            h.assertTrue(JobSkills.skill(sp, 0).lock(sp).equals("요구 스탯"), "stat lock: " + JobSkills.skill(sp, 0).lock(sp));
            hold(sp, Weapons.weaponFrom("sword", 0, "common"));
            // 직업 바꾸기: 검세 · 재사용 대기가 지워짐
            JobTraits.setStacks(sp, 4);
            h.assertTrue(JobSkills.press(sp, 3) && JobSkills.cooldownLeft(sp, "swordsman_mirror") > 0 && JobTraits.stanceOn(sp, "swordsman_mirror"), "mirror cast");
            JobSystem.setJob(sp, "spellblade");
            h.assertTrue(JobTraits.stacks(sp) == 0 && JobSkills.cooldownLeft(sp, "swordsman_mirror") == 0 && !JobTraits.stanceOn(sp, "swordsman_mirror"),
                    "job change clears stacks, stance, cooldowns");
            h.assertTrue(JobSkills.skill(sp, 0).name().equals("비전 참격"), "spellblade slot 1");
        } finally {
            cleanup(sp);
        }
        RpgGearTests.done(h, sp);
    }

    /** 마검사 「마력 검술」: 칼의 요구 힘 = 힘 + 마력, 칼 배율의 힘 = 힘 + 마력 × 0.5 — 마검 (티르빙) 은 그대로 · 다른 직업은 그대로 */
    @GameTest(environment = ENV, maxTicks = 20)
    public void magicSword(GameTestHelper h) {
        ServerPlayer sp = jobPlayer(h, null, Map.of("str", 50, "int", 60), 40);
        try {
            Weapon sword = Weapons.weaponFrom("sword", 40, "transcend");
            Weapon tyr = Weapons.weaponFrom("greatsword", 40, "transcend");
            h.assertFalse(RpgCombat.lackOf(sp, sword).isEmpty(), "no job: str 50 < transcend sword requirement");
            List<Weapons.Lack> tyrLack = RpgCombat.lackOf(sp, tyr);
            double tyrPower = RpgCombat.powerOf(sp, tyr);
            JobSystem.setJob(sp, "swordsman");
            h.assertFalse(RpgCombat.lackOf(sp, sword).isEmpty(), "swordsman: still short");
            JobSystem.setJob(sp, "spellblade");
            h.assertTrue(RpgCombat.lackOf(sp, sword).isEmpty(), "spellblade: str + int (110) meets the sword");
            Map<String, Double> eff = RpgStats.effective(sp);
            Map<String, Double> de = Jobs.damageStats(Jobs.job("spellblade"), "sword", eff);
            h.assertTrue(Math.abs(de.get("str") - (eff.get("str") + eff.get("int") * 0.5)) < 1e-9, "damage str = str + int × 0.5");
            double want = Combat.powerMultiplier(de, sword, 0) * StatTrees.atkMult(sp) * RpgCombat.dealtMult.applyAsDouble(sp);
            h.assertTrue(near(RpgCombat.powerOf(sp, sword), want), "sword power with 마력 검술 " + RpgCombat.powerOf(sp, sword) + " vs " + want);
            h.assertTrue(RpgCombat.lackOf(sp, tyr).size() == tyrLack.size() && near(RpgCombat.powerOf(sp, tyr), tyrPower), "Tyrfing unchanged");
            h.assertTrue(mmorpg.fabric.StatSheet.of(sp).usable(sword), "stat sheet sees 마력 검술");
        } finally {
            cleanup(sp);
        }
        RpgGearTests.done(h, sp);
    }

    /** 검세: 칼 평타가 맞으면 +1 (최대 5), 80틱 못 맞히면 하나씩 사라짐 */
    @GameTest(environment = ENV, maxTicks = 140)
    public void swordStacks(GameTestHelper h) {
        ServerPlayer sp = jobPlayer(h, "swordsman", Map.of("str", 50), 40);
        Husk z = dummy(h, 2);
        RpgCombat.forceCrit = false;
        sp.attack(z);
        h.assertTrue(JobTraits.stacks(sp) == 1, "one hit → 1 stack: " + JobTraits.stacks(sp));
        h.runAfterDelay(12, () -> {
            sp.attack(z);
            h.assertTrue(JobTraits.stacks(sp) == 2, "second hit → 2: " + JobTraits.stacks(sp));
            JobTraits.setStacks(sp, 2);
            h.runAfterDelay(92, () -> {
                h.assertTrue(JobTraits.stacks(sp) == 1, "decay one after 4 s: " + JobTraits.stacks(sp));
                cleanup(sp);
                RpgGearTests.done(h, sp);
            });
        });
    }

    /** 전직 창 (Forms.HEADLESS): 10렙 전 대사 → 카드 창 → 확인 → 첫 전직 무료 · 같은 직업 · 준비 중 · 은화 부족 · 바꾸기 300 */
    @GameTest(environment = ENV, maxTicks = 20)
    public void trainerFlow(GameTestHelper h) {
        ServerPlayer sp = jobPlayer(h, null, Map.of("str", 20), 5);
        Forms.HEADLESS.add(sp.getUUID());
        try {
            Wallet.set(sp, Economy.Kind.SILVER, 0);
            int[] done = {0};
            JobTrainer.open(sp, () -> done[0]++);
            h.assertTrue(Forms.last(sp) instanceof FormNet.DialogPayload, "below level 10: dialog");
            Forms.answer(sp, 0, null);
            h.assertTrue(done[0] == 1 && JobSystem.job(sp) == null, "dialog closes the flow, no job");
            sp.setExperienceLevels(10);
            RpgPlayers.update(sp, RpgData.of(sp).withLvmax(10));
            JobTrainer.open(sp, () -> done[0]++);
            FormNet.ChestPayload list = Forms.last(sp) instanceof FormNet.ChestPayload c ? c : null;
            h.assertTrue(list != null && list.slots().size() == 27, "card window");
            int sw = JobTrainer.slotOf("swordsman");
            int war = JobTrainer.slotOf("warrior");
            h.assertTrue(list.slots().get(sw).name().contains("검사") && list.slots().get(war).kind() == 3, "cards: swordsman, warrior dim (not ready)");
            // 준비 중인 직업 → 창 그대로 (안내)
            Forms.answer(sp, war, null);
            h.assertTrue(Forms.last(sp) instanceof FormNet.ChestPayload c2 && c2.body().contains("준비 중"), "not ready: back to the list");
            // 검사 → 확인 → 전직 (무료)
            Forms.answer(sp, sw, null);
            h.assertTrue(Forms.last(sp) instanceof FormNet.ChestPayload c3 && c3.title().contains("전직 확인"), "confirm window");
            Forms.answer(sp, 11, null);
            h.assertTrue("swordsman".equals(JobSystem.jobOf(sp)) && done[0] == 2 && !Forms.pending(sp), "first job free: " + JobSystem.jobOf(sp));
            // 같은 직업 → 안내
            JobTrainer.open(sp, () -> done[0]++);
            Forms.answer(sp, sw, null);
            h.assertTrue(Forms.last(sp) instanceof FormNet.ChestPayload c4 && c4.body().contains("이미"), "same job");
            // 마검사 (은화 0) → 확인 → 모자람 → 창 그대로
            int sb = JobTrainer.slotOf("spellblade");
            Forms.answer(sp, sb, null);
            h.assertTrue(Forms.last(sp) instanceof FormNet.ChestPayload c5 && c5.title().contains("바꾸기"), "change confirm");
            Forms.answer(sp, 11, null);
            h.assertTrue("swordsman".equals(JobSystem.jobOf(sp)) && Forms.last(sp) instanceof FormNet.ChestPayload c6 && c6.body().contains("부족"), "not enough silver");
            // 은화 300 → 바꿈
            Wallet.set(sp, Economy.Kind.SILVER, 350);
            Forms.answer(sp, sb, null);
            Forms.answer(sp, 11, null);
            h.assertTrue("spellblade".equals(JobSystem.jobOf(sp)) && Wallet.get(sp, Economy.Kind.SILVER) == 50, "changed for 300: silver " + Wallet.get(sp,
                    Economy.Kind.SILVER));
            // 닫기 → done
            JobTrainer.open(sp, () -> done[0] += 10);
            Forms.answer(sp, -1, null);
            h.assertTrue(done[0] >= 12 && !Forms.pending(sp), "close ends the flow");
        } finally {
            cleanup(sp);
        }
        RpgGearTests.done(h, sp);
    }

    // ------------------------------------------------------------ 검사

    /** 검사 스킬 다섯 (차례로 — 과녁 하나씩): 연참 (검세 3 → 검기) · 섬보 (지나가며 · 다음 평타 치명타) · 원월참 (검세 하나 +8%) · 명경지수 (두 번 베기 · −15%) · 천검난무 */
    @GameTest(environment = ENV_SW, maxTicks = 420)
    public void swordsmanSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = jobPlayer(h, "swordsman", Map.of("str", 50), 40);
        Jobs.Job j = Jobs.job("swordsman");
        Weapon w = Weapons.weaponFrom("sword", 0, "common");
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 3)));
        List<Step> steps = new ArrayList<>();
        // 연참: 검세 3 → 마지막 베기가 검기 (검세 셋 씀)
        steps.add(new Step(1, () -> {
            home(h, sp);
            JobTraits.setStacks(sp, 3);
            h.assertTrue(JobSkills.press(sp, 0), "연참 cast");
            h.assertTrue(JobTraits.stacks(sp) == 0, "검기 used 3 stacks: " + JobTraits.stacks(sp));
        }));
        steps.add(new Step(32, () -> {
            double want = expected(sp, j, j.skill(0), 5);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "연참 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 2));
            h.assertTrue(JobSkills.press(sp, 1), "섬보 cast");
        }));
        // 섬보: 지나가며 벰 · 숫자 그라데이션 · 다음 평타 치명타 (검세 +1)
        steps.add(new Step(5, () -> {
            double want = expected(sp, j, j.skill(1), 3);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "섬보 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(gradientNumber(h, z.get(0), j), "job-colored gradient number");
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 2));
            double base = basic(sp, w);
            sp.attack(z.get(0));
            double crit = base * RpgCombat.critMultOf(sp, w);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), crit), "basic after 섬보 is a crit " + TestDummies.dealt(z.get(0)) + " vs " + crit);
            h.assertTrue(JobTraits.stacks(sp) == 1, "sword hit → 1 stack");
        }));
        // 원월참: 검세 1 → +8%
        steps.add(new Step(12, () -> {
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 2.5));
            h.assertTrue(JobSkills.press(sp, 2), "원월참 cast");
            h.assertTrue(JobTraits.stacks(sp) == 0, "원월참 used the stack");
        }));
        steps.add(new Step(16, () -> {
            double want = expected(sp, j, j.skill(2), 9 * (1 + 0.08));
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "원월참 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 2));
            h.assertTrue(JobSkills.press(sp, 3), "명경지수 cast");
            h.assertTrue(JobTraits.stanceOn(sp, "swordsman_mirror") && Math.abs(Guards.of(sp).reduce() - 0.15) < 1e-9, "stance on, guard 15%");
        }));
        // 명경지수: 칼 평타 = 평타 × 1.35
        steps.add(new Step(12, () -> {
            RpgCombat.forceCrit = false;
            double base = basic(sp, w);
            sp.attack(z.get(0));
            RpgCombat.forceCrit = null;
            h.assertTrue(near(TestDummies.dealt(z.get(0)), base * 1.35), "mirror echo " + TestDummies.dealt(z.get(0)) + " vs " + base * 1.35);
        }));
        // 천검난무: 4 × 6 + 16
        steps.add(new Step(12, () -> {
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 3));
            h.assertTrue(JobSkills.press(sp, 4), "천검난무 cast");
        }));
        steps.add(new Step(110, () -> {
            double want = expected(sp, j, j.skill(4), 40);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "천검난무 " + TestDummies.dealt(z.get(0)) + " vs " + want);
        }));
        chain(h, steps, 0, () -> {
            cleanup(sp);
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }

    // ------------------------------------------------------------ 마검사

    /** 마검사 스킬 다섯: 비전 참격 (각인 → 평타가 터뜨림) · 마력 도약 (길 + 폭발) · 룬 검무 · 마검 각성 (잔상 · 사거리 +1) · 비전 거검 (각인을 모두 터뜨림) */
    @GameTest(environment = ENV_SB, maxTicks = 360)
    public void spellbladeSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = jobPlayer(h, "spellblade", Map.of("int", 30, "str", 20), 40);
        Jobs.Job j = Jobs.job("spellblade");
        Weapon w = Weapons.weaponFrom("sword", 0, "common");
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 2.5)));
        double reach0 = sp.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(1, () -> {
            home(h, sp);
            h.assertTrue(JobSkills.press(sp, 0), "비전 참격 cast");
        }));
        // 비전 참격: 3.8 + 4.7, 각인 1 → 평타가 각인을 터뜨림 (위력 × 1.0)
        steps.add(new Step(14, () -> {
            double want = expected(sp, j, j.skill(0), 8.5);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "비전 참격 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits.marks(sp, z.get(0)) == 1, "one mark per cast: " + JobTraits.marks(sp, z.get(0)));
            h.assertTrue(gradientNumber(h, z.get(0), j), "job-colored gradient number");
        }));
        // 폭발의 무적 시간 (10틱) 이 지난 뒤 평타: 각인 하나가 터짐
        steps.add(new Step(10, () -> {
            RpgCombat.forceCrit = false;
            double before = TestDummies.dealt(z.get(0));
            double base = basic(sp, w);
            sp.attack(z.get(0));
            RpgCombat.forceCrit = null;
            double burst = JobSystem.power(sp, j) * 1.0 * StatTrees.atkMult(sp) * RpgCombat.dealtMult.applyAsDouble(sp);
            h.assertTrue(near(TestDummies.dealt(z.get(0)) - before, base + burst), "basic + mark burst " + (TestDummies.dealt(z.get(0)) - before) + " vs " + (base + burst));
            h.assertTrue(JobTraits.marks(sp, z.get(0)) == 0, "mark used");
        }));
        // 마력 도약: 길 2.55 + 닿은 자리 폭발 2.55 (과녁 5칸 앞 · 7칸 이동)
        steps.add(new Step(12, () -> {
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 5));
            h.assertTrue(JobSkills.press(sp, 1), "마력 도약 cast");
            h.assertTrue(sp.position().distanceTo(h.absoluteVec(new Vec3(1.5, 2, 1.5))) > 5, "moved ahead");
        }));
        steps.add(new Step(14, () -> {
            double want = expected(sp, j, j.skill(1), 5.1);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "마력 도약 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 2));
            h.assertTrue(JobSkills.press(sp, 2), "룬 검무 cast");
        }));
        // 룬 검무: 5.1 × 3 (장판 보정)
        steps.add(new Step(36, () -> {
            double want = expected(sp, j, j.skill(2), 15.3);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "룬 검무 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 2));
            h.assertTrue(JobSkills.press(sp, 3), "마검 각성 cast");
            h.assertTrue(JobTraits.stanceOn(sp, "spellblade_awaken") && Math.abs(sp.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - reach0 - 1) < 1e-9,
                    "awaken: stance, reach +1");
        }));
        // 마검 각성: 평타 + 위력 × 0.93 잔상
        steps.add(new Step(12, () -> {
            RpgCombat.forceCrit = false;
            double base = basic(sp, w);
            sp.attack(z.get(0));
            RpgCombat.forceCrit = null;
            double echo = JobSystem.power(sp, j) * 0.93 * StatTrees.atkMult(sp) * RpgCombat.dealtMult.applyAsDouble(sp);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), base + echo), "awaken echo " + TestDummies.dealt(z.get(0)) + " vs " + (base + echo));
        }));
        // 비전 거검: 11 × 3 + 35 + 각인 하나 (거검 첫 베기가 찍은 것) 터짐
        steps.add(new Step(12, () -> {
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 4));
            h.assertTrue(JobSkills.press(sp, 4), "비전 거검 cast");
        }));
        steps.add(new Step(72, () -> {
            double want = expected(sp, j, j.skill(4), 68 + 1.0);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "비전 거검 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits.marks(sp, z.get(0)) == 0, "all marks detonated");
        }));
        chain(h, steps, 0, () -> {
            cleanup(sp);
            h.assertTrue(Math.abs(sp.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - reach0) < 1e-9, "reach restored on job change");
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }
}
