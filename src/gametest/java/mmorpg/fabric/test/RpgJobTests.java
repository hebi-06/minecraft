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
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.job.JobSkills;
import mmorpg.fabric.job.JobSystem;
import mmorpg.fabric.job.JobTrainer;
import mmorpg.fabric.job.JobTraits;
import mmorpg.fabric.job.JobTraits3;
import mmorpg.fabric.job.JobTraits4;
import mmorpg.fabric.job.JobTraitsMore;
import mmorpg.fabric.job.JobUpgrades;
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
    static final String ENV_WA = "mmorpg:job_wa";
    static final String ENV_MG = "mmorpg:job_mg";
    static final String ENV_AR = "mmorpg:job_ar";
    static final String ENV_AS = "mmorpg:job_as";
    static final String ENV_LA = "mmorpg:job_la";
    static final String ENV_RE = "mmorpg:job_re";
    static final String ENV_PA = "mmorpg:job_pa";
    static final String ENV_PR = "mmorpg:job_pr";
    static final String ENV_GU = "mmorpg:job_gu";

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

    /** 차례로 할 일 (delay 틱 뒤 task) */
    record Step(int delay, Runnable task) {}

    static void chain(GameTestHelper h, List<Step> steps, int i, Runnable end) {
        if (i >= steps.size()) {
            end.run();
            return;
        }
        Step s = steps.get(i);
        h.runAfterDelay(Math.max(1, s.delay()), () -> {
            s.task().run();
            chain(h, steps, i + 1, end);
        });
    }

    /** 지금 자리에서 앞 (보는 쪽 수평) ahead 칸에 과녁 */
    static Husk dummyAhead(GameTestHelper h, ServerPlayer sp, double ahead) {
        Vec3 f = new Vec3(sp.getLookAngle().x, 0, sp.getLookAngle().z).normalize();
        Vec3 p = sp.position().add(f.scale(ahead));
        Husk m = EntityTypes.HUSK.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        m.snapTo(p.x, p.y, p.z, 180F, 0F);
        TestDummies.make(m);
        h.getLevel().addFreshEntity(m);
        return m;
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
            // 0.30.0 열한 직업 모두 스킬이 들어감: 칼을 든 성기사는 칸이 보이되 잠김 (전투 망치)
            JobSystem.setJob(sp, "paladin");
            h.assertTrue(JobSystem.job(sp) != null && JobSkills.skill(sp, 0) != null && JobSkills.skill(sp, 0).lock(sp).equals("전투 망치"),
                    "paladin with a sword: slot locked to the hammer — " + (JobSkills.skill(sp, 0) == null ? "null" : JobSkills.skill(sp, 0).lock(sp)));
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
            boolean allReady = true;
            for (Jobs.Job jj : Jobs.all()) allReady &= list.slots().get(JobTrainer.slotOf(jj.id())).kind() != 3;
            h.assertTrue(list.slots().get(sw).name().contains("검사") && allReady, "cards: all 11 jobs ready (0.30.0 — none dim)");
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
            // 사거리는 같은 틱의 앞뒤를 비교 (시험 플레이어는 창작 모드 — 첫 틱에 사거리 +2 가 붙음)
            double r0 = sp.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
            h.assertTrue(JobSkills.press(sp, 3), "마검 각성 cast");
            h.assertTrue(JobTraits.stanceOn(sp, "spellblade_awaken"), "awaken stance");
            double r1 = sp.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
            h.assertTrue(JobTraits.reachBonus(sp) == 1 && Math.abs(r1 - r0 - 1) < 1e-9, "awaken reach +1: bonus " + JobTraits.reachBonus(sp) + ", " + r0 + " -> " + r1);
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
            double during = sp.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
            h.assertTrue(JobTraits.stanceOn(sp, "spellblade_awaken") && JobTraits.reachBonus(sp) == 1, "awaken still on at the end");
            cleanup(sp);
            double after = sp.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
            h.assertTrue(JobTraits.reachBonus(sp) == 0 && Math.abs(during - after - 1) < 1e-9, "reach restored on job change: " + during + " -> " + after);
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }

    // ------------------------------------------------------------ 0.28.0 전사

    /**
     * 전사: 분노 (도끼 평타 +8 · 50 이상이면 평타 +10% · 맞으면 +5) → 파쇄격 (분노 30 → +30% · 방어 깨짐 +10%) · 돌격 (밀고 가며 1.9 + 끝 2.0) · 대회전 (2.34 × 5) ·
     * 투혼 (분노 100 · 받는 피해 −20%) · 격노 도약 (7 × 4 + 24)
     */
    @GameTest(environment = ENV_WA, maxTicks = 420)
    public void warriorSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = jobPlayer(h, "warrior", Map.of("str", 50, "vit", 20), 40);
        Weapon w = Weapons.weaponFrom("axe", 0, "common");
        hold(sp, w);
        Jobs.Job j = Jobs.job("warrior");
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 2)));
        List<Step> steps = new ArrayList<>();
        double[] mark = {0};
        // 분노: 도끼 평타 +8, 50 이상이면 평타 × 1.1
        steps.add(new Step(1, () -> {
            home(h, sp);
            RpgCombat.forceCrit = false;
            JobTraitsMore.setRage(sp, 0);
            double base = basic(sp, w);
            sp.attack(z.get(0));
            h.assertTrue(near(TestDummies.dealt(z.get(0)), base), "axe basic " + TestDummies.dealt(z.get(0)) + " vs " + base);
            h.assertTrue(JobTraitsMore.rage(sp) == 8, "axe hit → rage 8: " + JobTraitsMore.rage(sp));
        }));
        steps.add(new Step(14, () -> {
            JobTraitsMore.setRage(sp, 50);
            double before = TestDummies.dealt(z.get(0));
            double base = basic(sp, w);
            sp.attack(z.get(0));
            h.assertTrue(near(TestDummies.dealt(z.get(0)) - before, base * 1.1), "rage 50 → basic × 1.1: " + (TestDummies.dealt(z.get(0)) - before) + " vs " + base * 1.1);
            RpgCombat.forceCrit = null;
            // 맞으면 +5 (RPG 체력의 받은 피해 훅 — 몹에게 맞음)
            int r0 = JobTraitsMore.rage(sp);
            mmorpg.fabric.RpgHealth.lossHook.lost(sp, sp.damageSources().mobAttack(z.get(0)), 2, 100, 98);
            h.assertTrue(JobTraitsMore.rage(sp) == r0 + 5, "hurt → rage +5: " + r0 + " -> " + JobTraitsMore.rage(sp));
        }));
        // 파쇄격: 분노 40 → 30 을 써서 6.5 × 1.3, 맞은 적 방어 깨짐 (받는 피해 × 1.1)
        steps.add(new Step(12, () -> {
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 2.2));
            JobTraitsMore.setRage(sp, 40);
            h.assertTrue(JobSkills.press(sp, 0), "파쇄격 cast");
            h.assertTrue(JobTraitsMore.rage(sp) == 10, "파쇄격 used 30 rage: " + JobTraitsMore.rage(sp));
        }));
        steps.add(new Step(14, () -> {
            double want = expected(sp, j, j.skill(0), 6.5 * 1.3);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "파쇄격 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(Math.abs(JobTraitsMore.breakMult(z.get(0)) - 1.1) < 1e-9, "armor break × 1.1: " + JobTraitsMore.breakMult(z.get(0)));
            h.assertTrue(gradientNumber(h, z.get(0), j), "job-colored gradient number");
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 6));
            mark[0] = sp.position().z;
            h.assertTrue(JobSkills.press(sp, 1), "돌격 cast");
        }));
        // 돌격: 달려가 처음 닿은 적 앞에서 멈춰 들이받음 (1.9) + 내려찍기 (2.0)
        steps.add(new Step(30, () -> {
            double want = expected(sp, j, j.skill(1), 3.9);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "돌격 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(sp.position().z - mark[0] > 3 && z.get(0).position().z - sp.position().z < 2.6, "charged forward, stopped in front of the target: "
                    + (sp.position().z - mark[0]) + " / gap " + (z.get(0).position().z - sp.position().z));
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 2));
            JobTraitsMore.setRage(sp, 0);
            h.assertTrue(JobSkills.press(sp, 2), "대회전 cast");
        }));
        // 대회전: 2.34 × 5 (지속 보정 short)
        steps.add(new Step(52, () -> {
            double want = expected(sp, j, j.skill(2), 11.7);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "대회전 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobSkills.press(sp, 3), "투혼 cast");
            h.assertTrue(JobTraits.stanceOn(sp, "warrior_spirit") && JobTraitsMore.rage(sp) == 100 && Math.abs(Guards.of(sp).reduce() - 0.2) < 1e-9,
                    "투혼: stance, rage 100, guard 20%");
        }));
        // 격노 도약: 7 × 4 + 24 (과녁 하나 — 그 둘레를 돌아가며 뜀)
        steps.add(new Step(6, () -> {
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 3));
            h.assertTrue(JobSkills.press(sp, 4), "격노 도약 cast");
        }));
        steps.add(new Step(80, () -> {
            double want = expected(sp, j, j.skill(4), 52);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "격노 도약 " + TestDummies.dealt(z.get(0)) + " vs " + want);
        }));
        chain(h, steps, 0, () -> {
            cleanup(sp);
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }

    // ------------------------------------------------------------ 0.28.0 매지션

    /**
     * 매지션: 마탄 연사 (1.4 × 5 — 한 번에 · 숫자 다섯 줄) → 4초 안에 비전 구체 (연쇄 1 — +12%, 1.4 × 6 + 4.2) · 위상 전환 (뒤로 · 원래 자리 폭발 4.2) · 마나 과부하
     * (관통 · 사거리 +4 · 완드 평타가 맞으면 직업 스킬 재사용 대기 −0.3초) · 금서 개방 (3 × 12 + 20)
     */
    @GameTest(environment = ENV_MG, maxTicks = 420)
    public void mageSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = jobPlayer(h, "mage", Map.of("int", 50), 40);
        Weapon w = Weapons.weaponFrom("wand", 0, "common");
        hold(sp, w);
        Jobs.Job j = Jobs.job("mage");
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 4)));
        List<Step> steps = new ArrayList<>();
        long[] t0 = {0, 0};
        steps.add(new Step(1, () -> {
            home(h, sp);
            JobTraitsMore.setChain(sp, 0);
            h.assertTrue(JobSkills.press(sp, 0), "마탄 연사 cast");
        }));
        steps.add(new Step(16, () -> {
            double want = expected(sp, j, j.skill(0), 7);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "마탄 연사 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(gradientNumber(h, z.get(0), j), "job-colored gradient number");
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 4));
            // 4초 안에 다른 스킬 → 연쇄 1 (+12%)
            h.assertTrue(JobSkills.press(sp, 2), "비전 구체 cast");
            h.assertTrue(JobTraitsMore.chain(sp) == 1, "chain 1: " + JobTraitsMore.chain(sp));
        }));
        steps.add(new Step(76, () -> {
            double want = expected(sp, j, j.skill(2), 12.6) * 1.12;
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "비전 구체 (연쇄 1) " + TestDummies.dealt(z.get(0)) + " vs " + want);
            z.get(0).discard();
            home(h, sp);
            // 북쪽을 보고 서서 뒤 (남쪽 — 돌 바닥 위) 로 물러남 (시험 자리 북쪽은 틀 밖이라 막힘)
            sp.snapTo(sp.getX(), sp.getY(), sp.getZ(), 180F, 0F);
            sp.setYHeadRot(180F);
            z.set(0, dummy(h, 1.5));
            JobTraitsMore.setChain(sp, 0);
            h.assertTrue(JobSkills.press(sp, 1), "위상 전환 cast");
            h.assertTrue(sp.position().distanceTo(h.absoluteVec(new Vec3(1.5, 2, 1.5))) > 4, "moved back");
        }));
        steps.add(new Step(14, () -> {
            double want = expected(sp, j, j.skill(1), 4.2);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "위상 전환 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 2));
            z.add(dummy(h, 4.5));
            JobTraitsMore.setChain(sp, 0);
            h.assertTrue(JobSkills.press(sp, 3), "마나 과부하 cast");
            h.assertTrue(JobTraits.stanceOn(sp, "mage_overload") && RpgCombat.rangedMod.range(sp, w) == 4 && RpgCombat.rangedMod.pierce(sp, w) > 0,
                    "overload: range +4, pierce");
            t0[0] = JobSkills.cooldownLeft(sp, "mage_overload");
            t0[1] = mmorpg.fabric.Tasks.now();
            RpgCombat.forceCrit = false;
            RpgCombat.fireRanged(sp, sp.getMainHandItem(), w, null);
        }));
        // 관통 광선: 앞의 과녁 둘 다 · 완드가 맞으면 재사용 대기 −6틱
        steps.add(new Step(16, () -> {
            RpgCombat.forceCrit = null;
            h.assertTrue(TestDummies.dealt(z.get(0)) > 0 && TestDummies.dealt(z.get(1)) > 0, "beam pierces: " + TestDummies.dealt(z.get(0)) + " / "
                    + TestDummies.dealt(z.get(1)));
            long left = JobSkills.cooldownLeft(sp, "mage_overload");
            long el = mmorpg.fabric.Tasks.now() - t0[1];
            h.assertTrue(left <= t0[0] - el - 6 + 1, "wand hit cuts job cooldowns by 6: " + t0[0] + " - " + el + " -> " + left);
            for (Husk x : z) x.discard();
            z.clear();
            home(h, sp);
            z.add(dummy(h, 6));
            JobTraitsMore.setChain(sp, 0);
            h.assertTrue(JobSkills.press(sp, 4), "금서 개방 cast");
        }));
        steps.add(new Step(84, () -> {
            double want = expected(sp, j, j.skill(4), 56);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "금서 개방 " + TestDummies.dealt(z.get(0)) + " vs " + want);
        }));
        chain(h, steps, 0, () -> {
            cleanup(sp);
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }

    // ------------------------------------------------------------ 0.28.0 궁수

    /**
     * 궁수: 호흡 (멈춰 쏘면 +1 · 하나당 활 평타 +4%) → 속사 (2.5 × 3, 호흡 3 이상 4발) · 바람 타기 (다음 활 평타가 두 발) · 화살비 (2.7 × 5) · 정조준 (호흡 5 · 관통 · 치명타
     * +20%p) · 관천일시 (40 + 20)
     */
    @GameTest(environment = ENV_AR, maxTicks = 460)
    public void archerSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = jobPlayer(h, "archer", Map.of("dex", 50), 40);
        Weapon w = Weapons.weaponFrom("bow", 0, "common");
        hold(sp, w);
        Jobs.Job j = Jobs.job("archer");
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 4)));
        List<Step> steps = new ArrayList<>();
        double[] want0 = {0};
        steps.add(new Step(1, () -> home(h, sp)));
        // 멈춰 쏨: 호흡 +1 → 평타 × 1.04
        steps.add(new Step(12, () -> {
            JobTraitsMore.setBreath(sp, 0);
            RpgCombat.forceCrit = false;
            double base = w.damage() * RpgCombat.powerOf(sp, w);
            RpgCombat.fireRanged(sp, sp.getMainHandItem(), w, z.get(0));
            h.assertTrue(JobTraitsMore.breath(sp) == 1, "still shot → breath 1: " + JobTraitsMore.breath(sp));
            want0[0] = base * 1.04;
        }));
        steps.add(new Step(8, () -> {
            RpgCombat.forceCrit = null;
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want0[0]), "breath 1 → bow × 1.04: " + TestDummies.dealt(z.get(0)) + " vs " + want0[0]);
            z.get(0).discard();
            z.set(0, dummy(h, 4));
            JobTraitsMore.setBreath(sp, 0);
            h.assertTrue(JobSkills.press(sp, 0), "속사 cast");
        }));
        steps.add(new Step(16, () -> {
            double want = expected(sp, j, j.skill(0), 7.5);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "속사 3 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            z.get(0).discard();
            z.set(0, dummy(h, 4));
            JobSkills.resetCooldowns(sp);
            JobTraitsMore.setBreath(sp, 3);
            h.assertTrue(JobSkills.press(sp, 0), "속사 cast (breath 3)");
        }));
        steps.add(new Step(18, () -> {
            double want = expected(sp, j, j.skill(0), 10);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "속사 4 (breath 3) " + TestDummies.dealt(z.get(0)) + " vs " + want);
            z.get(0).discard();
            home(h, sp);
            h.assertTrue(JobSkills.press(sp, 1), "바람 타기 cast");
            h.assertTrue(JobTraitsMore.windShots(sp) == 2, "wind shots 2");
        }));
        // 바람 타기 뒤 활 평타: 두 발 (두 번째 = 평타와 같은 피해). 바람 타기가 간 쪽은 움직이던 쪽 · 막힌 쪽에 따라 달라 (뒤가 바닥 끝이면 앞으로) 그 앞 3.5칸
        // 과녁이 시험 자리 (barrier 벽 8칸) 밖에 설 수 있었음 — 제자리로 돌아와 쏨 (0.30.0: 두 번째 화살이 들어가지 않은 시험이 다섯 번에 두 번)
        steps.add(new Step(8, () -> {
            home(h, sp);
            z.set(0, dummyAhead(h, sp, 3.5));
            RpgCombat.forceCrit = false;
            // 호흡 가득 (멈춰 쏘면 +1 이 붙는지는 바람 타기가 끝난 뒤 움직임을 재는 4틱 마디에 따라 달라짐 — 가득이면 그대로)
            JobTraitsMore.setBreath(sp, 5);
            want0[0] = 2 * w.damage() * RpgCombat.powerOf(sp, w) * RpgCombat.basicMult.applyAsDouble(sp, w);
            RpgCombat.fireRanged(sp, sp.getMainHandItem(), w, z.get(0));
            h.assertTrue(JobTraitsMore.windShots(sp) == 1, "one wind shot used");
        }));
        steps.add(new Step(26, () -> {
            RpgCombat.forceCrit = null;
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want0[0]), "wind: two arrows " + TestDummies.dealt(z.get(0)) + " vs " + want0[0]);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 5));
            h.assertTrue(JobSkills.press(sp, 2), "화살비 cast");
        }));
        // 화살비: 2.7 × 5 (장판)
        steps.add(new Step(56, () -> {
            double want = expected(sp, j, j.skill(2), 13.5);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "화살비 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 2.5));
            z.add(dummy(h, 5));
            double c0 = RpgCombat.critChanceOf(sp, w, null);
            h.assertTrue(JobSkills.press(sp, 3), "정조준 cast");
            double c1 = RpgCombat.critChanceOf(sp, w, null);
            h.assertTrue(JobTraits.stanceOn(sp, "archer_focus") && JobTraitsMore.breath(sp) == 5 && Math.abs(c1 - c0 - 0.2) < 1e-9 && RpgCombat.rangedMod.pierce(sp, w) > 0,
                    "focus: breath 5, crit +0.2 (" + c0 + " -> " + c1 + "), pierce");
            RpgCombat.forceCrit = false;
            RpgCombat.fireRanged(sp, sp.getMainHandItem(), w, z.get(0));
        }));
        // 관통: 앞의 과녁과 그 뒤의 과녁
        steps.add(new Step(10, () -> {
            RpgCombat.forceCrit = null;
            h.assertTrue(TestDummies.dealt(z.get(0)) > 0 && TestDummies.dealt(z.get(1)) > 0, "arrow pierces: " + TestDummies.dealt(z.get(0)) + " / "
                    + TestDummies.dealt(z.get(1)));
            for (Husk x : z) x.discard();
            z.clear();
            home(h, sp);
            z.add(dummy(h, 6));
            h.assertTrue(JobSkills.press(sp, 4), "관천일시 cast");
        }));
        steps.add(new Step(70, () -> {
            double want = expected(sp, j, j.skill(4), 60);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "관천일시 " + TestDummies.dealt(z.get(0)) + " vs " + want);
        }));
        chain(h, steps, 0, () -> {
            cleanup(sp);
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }

    // ------------------------------------------------------------ 0.29.0 암살자

    /** 과녁의 몸이 향한 쪽 (yaw — 0 = 남쪽) 을 정함 */
    static void face(Husk z, float yaw) {
        z.setYRot(yaw);
        z.setYHeadRot(yaw);
        z.setYBodyRot(yaw);
    }

    /**
     * 암살자: 표식 (단검 평타 +1 · 등 뒤 치명타 +25%p) → 낙화 (표식 5 → 0.5 + 0.85 × 5) · 잔영 (분신이 닿으면 터짐 2.85) · 귀환 비수 (갈 때 · 올 때 4.275 × 2) · 암영
     * (투명 · 첫 단검 평타 치명타 확정 × 2.5 · 풀리면 이동 +30% / 직업 스킬 첫 타 × 2.5) · 흑야 (3 × 6 + 20)
     */
    @GameTest(environment = ENV_AS, maxTicks = 460)
    public void assassinSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = jobPlayer(h, "assassin", Map.of("dex", 50), 40);
        Weapon w = Weapons.weaponFrom("dagger", 0, "common");
        hold(sp, w);
        Jobs.Job j = Jobs.job("assassin");
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 2)));
        List<Step> steps = new ArrayList<>();
        double[] want0 = {0, 0};
        // 표식: 단검 평타 +1 · 등 뒤 (과녁이 남쪽을 봄 — 나는 북쪽) 면 치명타 +25%p
        steps.add(new Step(1, () -> {
            home(h, sp);
            RpgCombat.forceCrit = false;
            double base = basic(sp, w);
            sp.attack(z.get(0));
            h.assertTrue(near(TestDummies.dealt(z.get(0)), base), "dagger basic " + TestDummies.dealt(z.get(0)) + " vs " + base);
            h.assertTrue(JobTraits3.marks(sp, z.get(0)) == 1, "dagger hit → 1 mark: " + JobTraits3.marks(sp, z.get(0)));
            face(z.get(0), 180F);
            double c0 = RpgCombat.critChanceOf(sp, w, z.get(0));
            face(z.get(0), 0F);
            double c1 = RpgCombat.critChanceOf(sp, w, z.get(0));
            h.assertTrue(Math.abs(c1 - c0 - 0.25) < 1e-9 && JobTraits3.behind(sp, z.get(0), 120), "back crit +0.25: " + c0 + " -> " + c1);
        }));
        // 낙화: 표식 5 → 4.75 (숫자 여섯 줄), 표식이 사라짐
        steps.add(new Step(12, () -> {
            RpgCombat.forceCrit = null;
            z.get(0).discard();
            z.set(0, dummy(h, 2));
            JobTraits3.setMarks(sp, z.get(0), 5);
            h.assertTrue(JobSkills.press(sp, 0), "낙화 cast");
            h.assertTrue(JobTraits3.marks(sp, z.get(0)) == 0, "marks used");
        }));
        steps.add(new Step(14, () -> {
            double want = expected(sp, j, j.skill(0), 4.75);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "낙화 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(gradientNumber(h, z.get(0), j), "job-colored gradient number");
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 1.2));
            want0[0] = sp.position().z;
            h.assertTrue(JobSkills.press(sp, 1), "잔영 cast");
        }));
        // 잔영: 앞으로 옮겨 감 · 원래 자리 분신에 닿은 과녁 → 터짐 2.85 (지속 보정 short)
        steps.add(new Step(10, () -> {
            double want = expected(sp, j, j.skill(1), 2.85);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "잔영 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(sp.position().z - want0[0] > 4, "teleported ahead: " + (sp.position().z - want0[0]));
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 3));
            h.assertTrue(JobSkills.press(sp, 2), "귀환 비수 cast");
        }));
        // 귀환 비수: 갈 때 · 올 때 한 번씩
        steps.add(new Step(30, () -> {
            double want = expected(sp, j, j.skill(2), 8.55);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "귀환 비수 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 2));
            h.assertTrue(JobSkills.press(sp, 3), "암영 cast");
            h.assertTrue(JobTraits3.veilOn(sp) && JobTraits.stanceOn(sp, "assassin_veil") && sp.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY),
                    "veil: stealth stance, invisible");
        }));
        // 암영 중 첫 단검 평타: 치명타 확정 × 2.5 → 풀림 · 이동 +30%
        steps.add(new Step(4, () -> {
            double base = basic(sp, w);
            sp.attack(z.get(0));
            double want = base * 2.5 * RpgCombat.critMultOf(sp, w);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "veil first hit " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(!JobTraits3.veilOn(sp) && !JobTraits.stanceOn(sp, "assassin_veil") && !sp.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY),
                    "attack breaks the veil");
            h.assertTrue(Math.abs(JobTraits3.speedBonus(sp, false) - 0.3) < 1e-9, "speed +30% after veil: " + JobTraits3.speedBonus(sp, false));
        }));
        // 암영 중 직업 스킬: 첫 타 × 2.5 (낙화 — 표식 0 → 0.5)
        steps.add(new Step(4, () -> {
            z.get(0).discard();
            z.set(0, dummy(h, 2));
            JobSkills.resetCooldowns(sp);
            h.assertTrue(JobSkills.press(sp, 3) && JobTraits3.veilOn(sp), "암영 again");
            h.assertTrue(JobSkills.press(sp, 0), "낙화 from stealth");
            h.assertTrue(!JobTraits3.veilOn(sp), "job skill breaks the veil");
        }));
        steps.add(new Step(6, () -> {
            double want = expected(sp, j, j.skill(0), 0.5 * 2.5);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "낙화 from stealth × 2.5 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 3));
            h.assertTrue(JobSkills.press(sp, 4), "흑야 cast");
        }));
        steps.add(new Step(60, () -> {
            double want = expected(sp, j, j.skill(4), 38);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "흑야 " + TestDummies.dealt(z.get(0)) + " vs " + want);
        }));
        chain(h, steps, 0, () -> {
            cleanup(sp);
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }

    // ------------------------------------------------------------ 0.29.0 창술사

    /**
     * 창술사: 창끝 (사거리 끝 1칸 안 평타 +15% · 기세 → 다음 직업 스킬 +10%) → 삼연창 (2 + 2 + 3.25) · 장대 도약 (4.35) · 선회창 (6.525 × 2) · 창벽 (0.5초마다 1.1 ·
     * 정면 −30%) · 천창유성 (2 × 20 + 18 — 장판)
     */
    @GameTest(environment = ENV_LA, maxTicks = 440)
    public void lancerSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = jobPlayer(h, "lancer", Map.of("str", 50, "dex", 20), 40);
        Weapon w = Weapons.weaponFrom("lance", 0, "common");
        hold(sp, w);
        Jobs.Job j = Jobs.job("lancer");
        List<Husk> z = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
        double[] mark = {0};
        // 창끝: 사거리 끝 (몸 상자까지 사거리 − 0.8 — 끝 1칸 안) 은 평타 × 1.15, 가까우면 그대로
        steps.add(new Step(1, () -> {
            home(h, sp);
            RpgCombat.forceCrit = false;
            double r = sp.entityInteractionRange();
            z.add(dummy(h, Math.min(6.2, r - 0.5)));
            h.assertTrue(JobTraits3.edgeHit(sp, z.get(0), 1.0), "edge distance (reach " + r + ")");
            double base = basic(sp, w);
            sp.attack(z.get(0));
            h.assertTrue(near(TestDummies.dealt(z.get(0)), base * 1.15), "edge +15% " + TestDummies.dealt(z.get(0)) + " vs " + base * 1.15);
        }));
        steps.add(new Step(14, () -> {
            z.get(0).discard();
            z.set(0, dummy(h, 2));
            h.assertFalse(JobTraits3.edgeHit(sp, z.get(0), 1.0), "near is not the edge");
            double base = basic(sp, w);
            sp.attack(z.get(0));
            h.assertTrue(near(TestDummies.dealt(z.get(0)), base), "near basic " + TestDummies.dealt(z.get(0)) + " vs " + base);
            RpgCombat.forceCrit = null;
            // 기세 → 삼연창 +10%
            z.get(0).discard();
            z.set(0, dummy(h, 3));
            JobTraits3.setMomentum(sp, true);
            h.assertTrue(JobSkills.press(sp, 0), "삼연창 cast");
            h.assertFalse(JobTraits3.momentum(sp), "momentum used");
        }));
        steps.add(new Step(16, () -> {
            double want = expected(sp, j, j.skill(0), 7.25) * 1.1;
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "삼연창 (기세 +10%) " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(gradientNumber(h, z.get(0), j), "job-colored gradient number");
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 5));
            mark[0] = sp.position().z;
            h.assertTrue(JobSkills.press(sp, 1), "장대 도약 cast");
        }));
        // 장대 도약: 앞으로 뛰어 넘어 내려찍음 4.35
        steps.add(new Step(16, () -> {
            double want = expected(sp, j, j.skill(1), 4.35);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "장대 도약 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(sp.position().z - mark[0] > 4, "vaulted ahead: " + (sp.position().z - mark[0]));
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 2));
            h.assertTrue(JobSkills.press(sp, 2), "선회창 cast");
        }));
        // 선회창: 6.525 × 2 (한방 — t 10)
        steps.add(new Step(16, () -> {
            double want = expected(sp, j, j.skill(2), 13.05);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "선회창 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 2));
            z.add(dummy(h, -1));
            h.assertTrue(JobSkills.press(sp, 3), "창벽 cast");
            h.assertTrue(JobTraits.stanceOn(sp, "lancer_wall") && sp.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS), "wall stance, slowed");
            double front = mmorpg.fabric.RpgHealth.sourceReduce.applyAsDouble(sp, sp.damageSources().mobAttack(z.get(0)));
            double back = mmorpg.fabric.RpgHealth.sourceReduce.applyAsDouble(sp, sp.damageSources().mobAttack(z.get(1)));
            h.assertTrue(Math.abs(front - 0.3) < 1e-9 && back == 0, "front −30%, back none: " + front + " / " + back);
        }));
        // 창벽: 10 · 20틱에 자동 찌르기 (1.1 × 2) — 뒤의 과녁은 맞지 않음
        steps.add(new Step(25, () -> {
            double want = expected(sp, j, j.skill(3), 2.2);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "창벽 2 thrusts " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(TestDummies.dealt(z.get(1)) == 0, "behind not thrust");
            JobSystem.setJob(sp, "lancer"); // 태세 · 재사용 대기 지움 (남은 찌르기는 멈춤)
            for (Husk x : z) x.discard();
            z.clear();
            home(h, sp);
            z.add(dummy(h, 5));
            h.assertTrue(JobSkills.press(sp, 4), "천창유성 cast");
        }));
        steps.add(new Step(80, () -> {
            double want = expected(sp, j, j.skill(4), 58);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "천창유성 " + TestDummies.dealt(z.get(0)) + " vs " + want);
        }));
        chain(h, steps, 0, () -> {
            cleanup(sp);
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }

    // ------------------------------------------------------------ 0.29.0 사신

    /**
     * 사신: 혼 (낫 평타 +1 · 하나당 이동 +1%) → 갈퀴 베기 (3.125 × 2 · 혼 +2) · 망령 걸음 (받는 피해 없음 · 지나가며 3.75) · 혼백 폭발 (혼 8 → 8 × 1.4) · 사신 현신
     * (넓게 베기 5칸 · 60%) · 황천문 (3 × 6 + 32 — 장판)
     */
    @GameTest(environment = ENV_RE, maxTicks = 460)
    public void reaperSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = jobPlayer(h, "reaper", Map.of("dex", 50, "int", 20), 40);
        Weapon w = Weapons.weaponFrom("scythe", 0, "common");
        hold(sp, w);
        Jobs.Job j = Jobs.job("reaper");
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 2)));
        List<Step> steps = new ArrayList<>();
        double[] mark = {0, 0};
        steps.add(new Step(1, () -> {
            home(h, sp);
            RpgCombat.forceCrit = false;
            JobTraits3.setSouls(sp, 0);
            double base = basic(sp, w);
            sp.attack(z.get(0));
            h.assertTrue(near(TestDummies.dealt(z.get(0)), base), "scythe basic " + TestDummies.dealt(z.get(0)) + " vs " + base);
            h.assertTrue(JobTraits3.souls(sp) == 1, "scythe hit → 1 soul: " + JobTraits3.souls(sp));
            JobTraits3.setSouls(sp, 10);
            h.assertTrue(Math.abs(JobTraits3.speedBonus(sp, true) - 0.1) < 1e-9, "10 souls → speed +10%: " + JobTraits3.speedBonus(sp, true));
            RpgCombat.forceCrit = null;
            JobTraits3.setSouls(sp, 0);
            z.get(0).discard();
            z.set(0, dummy(h, 2.5));
            h.assertTrue(JobSkills.press(sp, 0), "갈퀴 베기 cast");
        }));
        // 갈퀴 베기: 3.125 × 2 · 혼 +2
        steps.add(new Step(16, () -> {
            double want = expected(sp, j, j.skill(0), 6.25);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "갈퀴 베기 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits3.souls(sp) == 2, "two hits → 2 souls: " + JobTraits3.souls(sp));
            h.assertTrue(gradientNumber(h, z.get(0), j), "job-colored gradient number");
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 3));
            mark[0] = sp.position().z;
            h.assertTrue(JobSkills.press(sp, 1), "망령 걸음 cast");
            h.assertTrue(JobTraits3.ghost(sp) && mmorpg.fabric.RpgHealth.sourceReduce.applyAsDouble(sp, sp.damageSources().mobAttack(z.get(0))) == 1,
                    "wraith: no damage taken");
        }));
        // 망령 걸음: 지나가며 3.75 · 앞으로
        steps.add(new Step(12, () -> {
            double want = expected(sp, j, j.skill(1), 3.75);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "망령 걸음 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(sp.position().z - mark[0] > 4, "walked through: " + (sp.position().z - mark[0]));
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 2));
            JobTraits3.setSouls(sp, 8);
            h.assertTrue(JobSkills.press(sp, 2), "혼백 폭발 cast");
            h.assertTrue(JobTraits3.souls(sp) == 0, "souls used");
        }));
        // 혼백 폭발: 혼 8 → 8 × 1.4
        steps.add(new Step(8, () -> {
            double want = expected(sp, j, j.skill(2), 8 * 1.4);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "혼백 폭발 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 2));
            z.add(dummy(h, 4.5));
            RpgCombat.forceCrit = false;
            sp.attack(z.get(0));
            h.assertTrue(TestDummies.dealt(z.get(1)) == 0, "no avatar: 4.5 blocks is outside the 3-block sweep");
        }));
        // 사신 현신: 넓게 베기 5칸 · 60%
        steps.add(new Step(14, () -> {
            h.assertTrue(JobSkills.press(sp, 3), "사신 현신 cast");
            h.assertTrue(JobTraits.stanceOn(sp, "reaper_avatar"), "avatar stance");
            mark[1] = TestDummies.dealt(z.get(0));
            sp.attack(z.get(0));
            double main = TestDummies.dealt(z.get(0)) - mark[1];
            h.assertTrue(main > 0 && near(TestDummies.dealt(z.get(1)), main * 0.6), "avatar sweep 60% at 4.5 blocks: " + TestDummies.dealt(z.get(1)) + " vs " + main * 0.6);
            RpgCombat.forceCrit = null;
            JobSystem.setJob(sp, "reaper");
            for (Husk x : z) x.discard();
            z.clear();
            home(h, sp);
            z.add(dummy(h, 5));
            h.assertTrue(JobSkills.press(sp, 4), "황천문 cast");
        }));
        steps.add(new Step(90, () -> {
            double want = expected(sp, j, j.skill(4), 50);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "황천문 " + TestDummies.dealt(z.get(0)) + " vs " + want);
        }));
        chain(h, steps, 0, () -> {
            cleanup(sp);
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }
    // ------------------------------------------------------------ 0.30.0 도구

    /** 시험 플레이어 sp 의 지금 자리에서 (dx, dz) 칸에 선 아군 (시험 플레이어 — 끝나면 bye 로 지움) */
    static ServerPlayer allyAt(GameTestHelper h, ServerPlayer sp, double dx, double dz) {
        ServerPlayer o = RpgGearTests.player(h);
        o.snapTo(sp.getX() + dx, sp.getY(), sp.getZ() + dz, 180F, 0F);
        return o;
    }

    /** 아군을 (sp 기준 dx, dz) 로 옮김 */
    static void moveAlly(ServerPlayer o, ServerPlayer sp, double dx, double dz) {
        o.snapTo(sp.getX() + dx, sp.getY(), sp.getZ() + dz, 180F, 0F);
    }

    static void bye(GameTestHelper h, ServerPlayer o) {
        if (o != null) h.getLevel().getServer().getPlayerList().remove(o);
    }

    /** 시험 자리 기준 (dx, dz) 칸의 과녁 */
    static Husk dummyAt(GameTestHelper h, double dx, double dz) {
        return TestDummies.make(h.spawnWithNoFreeWill(EntityTypes.HUSK, (float) (1.5 + dx), 2F, (float) (1.5 + dz)));
    }

    static boolean close(double a, double b, double eps) {
        return Math.abs(a - b) <= eps;
    }

    // ------------------------------------------------------------ 0.30.0 성기사

    /**
     * 성기사: 심판 (망치 평타 +1 · 다섯이면 벼락 — 기절 뒤 0) · 수호 오라 (망치를 든 성기사 둘레 6칸 −5%) → 뇌격 강타 (3 + 2.5 · 심판 +2) · 신념의 돌진 (3.3 · 기절
     * · 둘레 아군 보호막 8%) · 뇌진 (3.3 × 3 · 심판 +3) · 수호의 맹세 (오라 −15% · 1초마다 1% 회복) · 천뢰의 대망치 (10 + 10 + 24)
     */
    @GameTest(environment = ENV_PA, maxTicks = 420)
    public void paladinSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = jobPlayer(h, "paladin", Map.of("vit", 50, "str", 20), 40);
        Weapon w = Weapons.weaponFrom("hammer", 0, "common");
        hold(sp, w);
        Jobs.Job j = Jobs.job("paladin");
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 2)));
        List<Step> steps = new ArrayList<>();
        ServerPlayer[] ally = {null};
        double[] mark = {0, 0};
        steps.add(new Step(1, () -> {
            home(h, sp);
            RpgCombat.forceCrit = false;
            sp.attack(z.get(0));
            h.assertTrue(JobTraits4.judges(sp, z.get(0)) == 1, "hammer hit → 1 judgement: " + JobTraits4.judges(sp, z.get(0)));
            JobTraits4.setJudges(sp, z.get(0), 4);
        }));
        steps.add(new Step(14, () -> {
            sp.attack(z.get(0));
            h.assertTrue(JobTraits4.judges(sp, z.get(0)) == 0 && z.get(0).hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS),
                    "5th judgement → verdict (stunned), back to 0: " + JobTraits4.judges(sp, z.get(0)));
            RpgCombat.forceCrit = null;
            // 수호 오라: 둘레 6칸 아군 (나 포함) −5%, 11칸 밖 · 망치가 아니면 없음
            ally[0] = allyAt(h, sp, 0, 4);
            h.assertTrue(close(JobTraits4.auraFor(sp), 0.05, 1e-9) && close(JobTraits4.auraFor(ally[0]), 0.05, 1e-9), "aura 5%: " + JobTraits4.auraFor(ally[0]));
            h.assertTrue(close(RpgHealth.sourceReduce.applyAsDouble(ally[0], ally[0].damageSources().mobAttack(z.get(0))), 0.05, 1e-9), "aura reduces damage taken");
            moveAlly(ally[0], sp, 0, 11);
            h.assertTrue(JobTraits4.auraFor(ally[0]) == 0, "11 blocks away: no aura");
            hold(sp, Weapons.weaponFrom("sword", 0, "common"));
            h.assertTrue(JobTraits4.auraFor(sp) == 0, "no hammer: no aura");
            hold(sp, w);
            z.get(0).discard();
            z.set(0, dummy(h, 2.5));
            h.assertTrue(JobSkills.press(sp, 0), "뇌격 강타 cast");
        }));
        // 뇌격 강타: 충격파 3 → 10틱 뒤 벼락 2.5 · 심판 +2
        steps.add(new Step(16, () -> {
            double want = expected(sp, j, j.skill(0), 5.5);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "뇌격 강타 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits4.judges(sp, z.get(0)) == 2, "smite → 2 judgements: " + JobTraits4.judges(sp, z.get(0)));
            h.assertTrue(gradientNumber(h, z.get(0), j), "job-colored gradient number");
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 5));
            moveAlly(ally[0], sp, 2, 3);
            mark[0] = sp.position().z;
            h.assertTrue(JobSkills.press(sp, 1), "신념의 돌진 cast");
        }));
        // 신념의 돌진: 부딪힘 3.3 · 기절 (0.5초 — 끝나기 전에 봄) · 나와 둘레 아군 보호막 (최대 체력 8%)
        steps.add(new Step(7, () -> {
            double want = expected(sp, j, j.skill(1), 3.3);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "신념의 돌진 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(sp.position().z - mark[0] > 1.5, "charged ahead: " + (sp.position().z - mark[0]));
            h.assertTrue(z.get(0).hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS), "ram stuns");
            h.assertTrue(close(RpgHealth.shieldOf(sp), RpgHealth.maxOf(sp) * 0.08, 1e-6) && close(RpgHealth.shieldOf(ally[0]), RpgHealth.maxOf(ally[0]) * 0.08, 1e-6),
                    "shield 8% on me and the ally: " + RpgHealth.shieldOf(sp) + " / " + RpgHealth.shieldOf(ally[0]));
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 2));
            h.assertTrue(JobSkills.press(sp, 2), "뇌진 cast");
        }));
        // 뇌진: 3.3 × 3 (지속 보정 short) · 맞을 때마다 심판 +1
        steps.add(new Step(26, () -> {
            double want = expected(sp, j, j.skill(2), 9.9);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "뇌진 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits4.judges(sp, z.get(0)) == 3, "quake → 3 judgements: " + JobTraits4.judges(sp, z.get(0)));
            sp.setHealth(sp.getMaxHealth() * 0.5F);
            mark[1] = sp.getHealth();
            h.assertTrue(JobSkills.press(sp, 3), "수호의 맹세 cast");
            h.assertTrue(JobTraits.stanceOn(sp, "paladin_oath") && close(JobTraits4.auraFor(sp), 0.15, 1e-9) && RpgCombat.isSteadfast(sp), "oath: aura 15%, steadfast");
        }));
        // 수호의 맹세: 1초마다 최대 체력 1% (자연 회복이 더해질 수 있어 그 이상)
        steps.add(new Step(25, () -> {
            double got = sp.getHealth() - mark[1];
            h.assertTrue(got >= RpgHealth.maxOf(sp) * 0.01 - 1e-3, "oath regen ≥ 1%: " + got);
            JobSystem.setJob(sp, "paladin");
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 3));
            h.assertTrue(JobSkills.press(sp, 4), "천뢰의 대망치 cast");
        }));
        // 천뢰의 대망치: 10 + 10 + 24 (지속 보정 long — 앞 3칸 가운데 둘레 8칸) · 마지막에 기절
        steps.add(new Step(60, () -> {
            double want = expected(sp, j, j.skill(4), 44);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "천뢰의 대망치 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(z.get(0).hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS), "last strike stuns");
        }));
        chain(h, steps, 0, () -> {
            cleanup(sp);
            for (Husk x : z) x.discard();
            bye(h, ally[0]);
            RpgGearTests.done(h, sp);
        });
    }

    // ------------------------------------------------------------ 0.30.0 사제

    /**
     * 사제: 치유의 손길 (겨눈 아군 · 없으면 나 12% · −10% · 신앙 +1) · 빛의 걸음 (지나간 아군 · 나 보호막 10%) · 성가 (0.7 × 8 × 신앙 · 아군 1.5% × 8 · 신앙을
     * 씀) · 성흔 (때린 아군이 준 피해 4% 회복) · 넘친 홀 흡혈 → 다친 아군 · 새벽의 종 (8 × 3 · 15% × 3 · 정화 · 쓰러지지 않음)
     */
    @GameTest(environment = ENV_PR, maxTicks = 460)
    public void priestSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = jobPlayer(h, "priest", Map.of("int", 50, "vit", 20), 40);
        Weapon w = Weapons.weaponFrom("scepter", 0, "common");
        hold(sp, w);
        Jobs.Job j = Jobs.job("priest");
        List<Husk> z = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
        ServerPlayer[] ally = {null};
        double[] mark = {0, 0, 0};
        steps.add(new Step(1, () -> {
            home(h, sp);
            ally[0] = allyAt(h, sp, 0, 4);
            ally[0].setHealth(ally[0].getMaxHealth() * 0.5F);
            mark[0] = ally[0].getHealth();
            h.assertTrue(JobSkills.press(sp, 0), "치유의 손길 cast (aimed ally)");
            double got = ally[0].getHealth() - mark[0];
            h.assertTrue(close(got, RpgHealth.maxOf(ally[0]) * 0.12, 0.01), "ally healed 12%: " + got);
            mmorpg.fabric.skill.Guards.Guard g = mmorpg.fabric.skill.Guards.of(ally[0]);
            h.assertTrue(g != null && close(g.reduce(), 0.1, 1e-9), "ally guard −10%");
            h.assertTrue(JobTraits4.faith(sp) == 1, "faith 1: " + JobTraits4.faith(sp));
            // 아군을 겨누지 않으면 나
            Vec3 at = sp.position();
            sp.snapTo(at.x, at.y, at.z, 180F, 0F);
            sp.setYHeadRot(180F);
            sp.setHealth(sp.getMaxHealth() * 0.5F);
            mark[1] = sp.getHealth();
            JobSkills.resetCooldowns(sp);
            h.assertTrue(JobSkills.press(sp, 0), "치유의 손길 cast (self)");
            h.assertTrue(close(sp.getHealth() - mark[1], RpgHealth.maxOf(sp) * 0.12, 0.01) && JobTraits4.faith(sp) == 2, "self healed 12%, faith 2");
        }));
        // 빛의 걸음: 앞으로 미끄러지며 지나간 아군 · 나 보호막 10% · 신앙 +1
        steps.add(new Step(4, () -> {
            home(h, sp);
            moveAlly(ally[0], sp, 1.5, 3);
            mark[0] = sp.position().z;
            h.assertTrue(JobSkills.press(sp, 1), "빛의 걸음 cast");
        }));
        steps.add(new Step(8, () -> {
            h.assertTrue(sp.position().z - mark[0] > 3, "slid ahead: " + (sp.position().z - mark[0]));
            h.assertTrue(close(RpgHealth.shieldOf(ally[0]), RpgHealth.maxOf(ally[0]) * 0.1, 1e-6) && close(RpgHealth.shieldOf(sp), RpgHealth.maxOf(sp) * 0.1, 1e-6),
                    "shield 10%: " + RpgHealth.shieldOf(ally[0]) + " / " + RpgHealth.shieldOf(sp));
            h.assertTrue(JobTraits4.faith(sp) == 3, "faith 3: " + JobTraits4.faith(sp));
            home(h, sp);
            z.add(dummy(h, 2.5));
            moveAlly(ally[0], sp, 2, 0);
            ally[0].setHealth(ally[0].getMaxHealth() * 0.5F);
            mark[0] = ally[0].getHealth();
            h.assertTrue(JobSkills.press(sp, 2), "성가 cast");
            h.assertTrue(JobTraits4.faith(sp) == 0, "attack skill uses all faith");
        }));
        // 성가: 0.7 × 8 (장판 +10%) × 신앙 3 (+30%) · 아군 1.5% × 8
        steps.add(new Step(80, () -> {
            double want = expected(sp, j, j.skill(2), 5.6) * 1.3;
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "성가 (신앙 3) " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(gradientNumber(h, z.get(0), j), "job-colored gradient number");
            h.assertTrue(ally[0].getHealth() - mark[0] >= RpgHealth.maxOf(ally[0]) * 0.12 - 0.01, "hymn healed the ally ≥ 12%: " + (ally[0].getHealth() - mark[0]));
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 3));
            h.assertTrue(JobSkills.press(sp, 3), "성흔 cast");
            h.assertTrue(JobTraits4.stigmaOn(z.get(0)) && JobTraits4.faith(sp) == 1, "stigma on the target, faith 1");
        }));
        // 성흔 찍힌 적을 홀 평타로: 홀 흡혈 (준 피해 5% · 최대 체력 1% 까지) + 성흔 4%
        steps.add(new Step(4, () -> {
            RpgCombat.forceCrit = false;
            sp.setHealth(sp.getMaxHealth() * 0.5F);
            mark[1] = sp.getHealth();
            mark[2] = TestDummies.dealt(z.get(0));
            sp.attack(z.get(0));
            double dealt = TestDummies.dealt(z.get(0)) - mark[2];
            double want = Math.min(dealt * 0.05, RpgHealth.maxOf(sp) * 0.01) + dealt * 0.04;
            double got = sp.getHealth() - mark[1];
            h.assertTrue(dealt > 0 && close(got, want, 0.02), "stigma + lifesteal heal " + got + " vs " + want + " (dealt " + dealt + ")");
        }));
        // 넘친 흡혈: 내 체력이 가득이면 둘레 4칸에서 체력 비율이 가장 낮은 아군에게
        steps.add(new Step(14, () -> {
            sp.setHealth(sp.getMaxHealth());
            moveAlly(ally[0], sp, 1.5, 0);
            ally[0].setHealth(ally[0].getMaxHealth() * 0.5F);
            mark[0] = ally[0].getHealth();
            mark[2] = TestDummies.dealt(z.get(0));
            sp.attack(z.get(0));
            double dealt = TestDummies.dealt(z.get(0)) - mark[2];
            double want = Math.min(dealt * 0.05, RpgHealth.maxOf(sp) * 0.01);
            double got = ally[0].getHealth() - mark[0];
            h.assertTrue(dealt > 0 && close(got, want, 0.02), "overflow to the hurt ally " + got + " vs " + want);
            RpgCombat.forceCrit = null;
            // 새벽의 종: 신앙 2 (+20%)
            JobTraits4.setFaith(sp, 2);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 3));
            moveAlly(ally[0], sp, 2, 1);
            ally[0].setHealth(ally[0].getMaxHealth() * 0.5F);
            mark[0] = ally[0].getHealth();
            ally[0].addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 200, 0));
            h.assertTrue(JobSkills.press(sp, 4), "새벽의 종 cast");
        }));
        // 첫 울림 (16틱): 15% 회복 · 정화 · 쓰러지지 않음 (큰 피해에도 체력 1)
        steps.add(new Step(20, () -> {
            h.assertTrue(!ally[0].hasEffect(net.minecraft.world.effect.MobEffects.POISON), "first toll cleanses");
            h.assertTrue(JobTraits4.dawnOn(ally[0]) && JobTraits4.dawnOn(sp), "dawn: undying on the ally and me");
            h.assertTrue(ally[0].getHealth() - mark[0] >= RpgHealth.maxOf(ally[0]) * 0.15 - 0.01, "first toll heals 15%: " + (ally[0].getHealth() - mark[0]));
            float hp = ally[0].getHealth();
            float next = RpgHealth.onLoss(ally[0], ally[0].damageSources().magic(), hp, hp - 10000F);
            h.assertTrue(close(next, Math.min(hp, 1F), 1e-6), "undying: a lethal hit leaves 1 HP (" + next + ")");
        }));
        // 새벽의 종: 8 × 3 (지속 보정 long) × 신앙 2 (+20%)
        steps.add(new Step(40, () -> {
            double want = expected(sp, j, j.skill(4), 24) * 1.2;
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "새벽의 종 (신앙 2) " + TestDummies.dealt(z.get(0)) + " vs " + want);
        }));
        chain(h, steps, 0, () -> {
            cleanup(sp);
            for (Husk x : z) x.discard();
            bye(h, ally[0]);
            RpgGearTests.done(h, sp);
        });
    }

    // ------------------------------------------------------------ 0.30.0 수호기사

    /**
     * 수호기사: 반격 (웅크려 막은 양 × 30% · 최대 체력 50% 까지 → 다음 공격 직업 스킬 첫 타에 더함) · 방패 치기 (4 + 반격 · 기절) · 철옹성 (막기 정면 100% · 옆 60%
     * · 이동 −40%) · 수호 돌입 (겨눈 아군에게 · 2.4 · 아군 피해의 40% 를 내가 × 0.7) · 방패 투척 (적마다 7.2) · 성채 (안의 아군 −35% → 32)
     */
    @GameTest(environment = ENV_GU, maxTicks = 460)
    public void guardianSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = jobPlayer(h, "guardian", Map.of("vit", 70), 40);
        Weapon w = Weapons.weaponFrom("shield", 0, "common");
        hold(sp, w);
        Jobs.Job j = Jobs.job("guardian");
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 2)));
        List<Step> steps = new ArrayList<>();
        ServerPlayer[] ally = {null};
        double[] mark = {0, 0};
        steps.add(new Step(1, () -> {
            home(h, sp);
            JobTraits4.setCounter(sp, 0);
            // 받은 피해 (RpgHealth.onLoss — 바닐라의 연속 피해 막기 · 접속 직후 무적 없이): 웅크려 앞에서
            float hp = sp.getHealth();
            sp.setShiftKeyDown(true);
            RpgHealth.onLoss(sp, sp.damageSources().mobAttack(z.get(0)), hp, hp - 10F);
            sp.setShiftKeyDown(false);
            double c = JobTraits4.counter(sp);
            h.assertTrue(c > 0 && c <= JobTraits4.counterCap(sp, j) + 1e-9, "blocked hit → counter " + c);
            JobTraits4.setCounter(sp, 1e9);
            h.assertTrue(close(JobTraits4.counter(sp), RpgHealth.maxOf(sp) * 0.5, 1e-6), "counter cap 50% max HP: " + JobTraits4.counter(sp));
            JobTraits4.setCounter(sp, 20);
            sp.setHealth(sp.getMaxHealth());
            h.assertTrue(JobSkills.press(sp, 0), "방패 치기 cast");
        }));
        // 방패 치기: 4 + 반격 20 (첫 타) · 기절
        steps.add(new Step(6, () -> {
            double want = expected(sp, j, j.skill(0), 4) + 20;
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "방패 치기 + 반격 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits4.counter(sp) == 0, "counter used");
            h.assertTrue(z.get(0).hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS), "bash stuns");
            h.assertTrue(gradientNumber(h, z.get(0), j), "job-colored gradient number");
            // 철옹성: 정면 100% · 옆 60% · 뒤 없음 · 이동 −40% · 밀려나지 않음
            h.assertTrue(JobSkills.press(sp, 3), "철옹성 cast");
            h.assertTrue(JobTraits.stanceOn(sp, "guardian_bulwark") && close(JobTraits4.slowBonus(sp), -0.4, 1e-9) && RpgCombat.isSteadfast(sp),
                    "bulwark stance, −40% speed, steadfast");
            h.assertTrue(close(RpgCombat.blockMod.block(sp, w, 0.9), 1.0, 1e-9) && close(RpgCombat.blockMod.block(sp, w, 0.0), 0.6, 1e-9)
                    && RpgCombat.blockMod.block(sp, w, -0.8) == 0, "bulwark block: front 1.0 · side 0.6 · back 0");
            z.add(dummyAt(h, 2, 0));
            sp.setShiftKeyDown(true);
            double side = RpgCombat.shieldReduction(sp, sp.damageSources().mobAttack(z.get(1)));
            sp.setShiftKeyDown(false);
            h.assertTrue(side >= 0.6 - 1e-9 && side < 1, "side attack blocked 60%: " + side);
        }));
        // 수호 돌입: 겨눈 아군 (앞 5.5칸 — 바닥 8×8 안: 7칸은 시험 자리 둘레 barrier 벽 너머라 시선이 막혀 겨누지 못했음) 앞까지 → 둘레 2.5칸 적 2.4 → 아군이 받을 피해의 40% 를
        // 내가 × 0.7
        steps.add(new Step(4, () -> {
            JobSystem.setJob(sp, "guardian");
            for (Husk x : z) x.discard();
            z.clear();
            home(h, sp);
            ally[0] = allyAt(h, sp, 0, 5.5);
            z.add(dummyAt(h, 1.3, 4.3));
            h.assertTrue(JobSkills.press(sp, 1), "수호 돌입 cast");
        }));
        steps.add(new Step(8, () -> {
            h.assertTrue(sp.distanceTo(ally[0]) < 2.5, "rushed to the ally: " + sp.distanceTo(ally[0]));
            double want = expected(sp, j, j.skill(1), 2.4);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "수호 돌입 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(sp.getUUID().equals(JobTraits4.guardOf(ally[0])), "ally guarded (guard " + JobTraits4.guardOf(ally[0]) + ", me " + sp.position() + ", ally "
                    + ally[0].position() + ")");
            sp.setHealth(sp.getMaxHealth());
            ally[0].setHealth(ally[0].getMaxHealth());
            mark[0] = ally[0].getHealth();
            mark[1] = sp.getHealth();
            float next = RpgHealth.onLoss(ally[0], ally[0].damageSources().mobAttack(z.get(0)), (float) mark[0], (float) mark[0] - 10F);
            double lossA = mark[0] - next;
            double lossG = mark[1] - sp.getHealth();
            h.assertTrue(lossA > 0 && close(lossG, lossA * (0.4 * 0.7 / 0.6), 0.02 + lossA * 0.002), "shared: guardian " + lossG + " vs ally " + lossA);
        }));
        // 아군 없이 앞으로: 길 앞에 처음 닿은 적 (4칸) 앞에서 멈춰 닿음 (지나치지 않음)
        steps.add(new Step(4, () -> {
            JobTraits4.setCounter(sp, 0);
            for (Husk x : z) x.discard();
            z.clear();
            home(h, sp);
            moveAlly(ally[0], sp, -3, -3);
            z.add(dummy(h, 4));
            JobSkills.resetCooldowns(sp);
            mark[0] = sp.position().z;
            h.assertTrue(JobSkills.press(sp, 1), "수호 돌입 cast (no ally)");
        }));
        steps.add(new Step(8, () -> {
            double moved = sp.position().z - mark[0];
            h.assertTrue(moved > 1.0 && moved < 3.2, "stopped before the first enemy: moved " + moved);
            double want = expected(sp, j, j.skill(1), 2.4);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "수호 돌입 (no ally) " + TestDummies.dealt(z.get(0)) + " vs " + want);
        }));
        // 방패 투척: 3 · 6 · 9칸 (6칸 안으로 튕김) 적마다 한 번 7.2 (지속 보정 short)
        steps.add(new Step(4, () -> {
            JobTraits4.setCounter(sp, 0);
            for (Husk x : z) x.discard();
            z.clear();
            home(h, sp);
            moveAlly(ally[0], sp, -3, -3);
            for (double d : new double[] {3, 6, 9}) z.add(dummy(h, d));
            h.assertTrue(JobSkills.press(sp, 2), "방패 투척 cast");
        }));
        steps.add(new Step(30, () -> {
            double want = expected(sp, j, j.skill(2), 7.2);
            for (int i = 0; i < 3; i++) h.assertTrue(near(TestDummies.dealt(z.get(i)), want), "방패 투척 " + i + ": " + TestDummies.dealt(z.get(i)) + " vs " + want);
            for (Husk x : z) x.discard();
            z.clear();
            home(h, sp);
            z.add(dummy(h, 3));
            moveAlly(ally[0], sp, 2, 2);
            mark[0] = mmorpg.fabric.FxDisplays.activeCount();
            h.assertTrue(JobSkills.press(sp, 4), "성채 cast");
        }));
        // 성채: 안의 아군 · 나 받는 피해 −35%, 성벽 (블록 표시)
        steps.add(new Step(12, () -> {
            h.assertTrue(close(JobTraits4.fortFor(sp), 0.35, 1e-9) && close(JobTraits4.fortFor(ally[0]), 0.35, 1e-9), "inside the fortress −35%");
            h.assertTrue(close(RpgHealth.sourceReduce.applyAsDouble(ally[0], ally[0].damageSources().mobAttack(z.get(0))), 0.35, 1e-9), "fortress in sourceReduce");
            h.assertTrue(mmorpg.fabric.FxDisplays.activeCount() - mark[0] >= 30, "walls: " + (mmorpg.fabric.FxDisplays.activeCount() - mark[0]) + " block displays");
        }));
        // 무너짐 (160틱): 둘레 9칸 32 (지속 보정 zoneLong)
        steps.add(new Step(156, () -> {
            double want = expected(sp, j, j.skill(4), 32);
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "성채 무너짐 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits4.fortFor(sp) == 0, "fortress over");
        }));
        chain(h, steps, 0, () -> {
            cleanup(sp);
            for (Husk x : z) x.discard();
            bye(h, ally[0]);
            RpgGearTests.done(h, sp);
        });
    }
    // ------------------------------------------------------------ 0.31.0 직업 스킬 강화

    static final String ENV_UP = "mmorpg:job_up";
    static final String ENV_UP2 = "mmorpg:job_up2";

    /** 아이템 id 의 아이템 (시험 — 없으면 null) */
    static net.minecraft.world.item.Item item(String id) {
        return mmorpg.fabric.ui.Icon.itemById(id);
    }

    /**
     * 0.31.0 강화 흐름 (창 — Forms.HEADLESS): 교관 창의 강화 단추 → 강화 창 (스킬 다섯) → 확인 창 (43렙 — 강화 칸 흐림 · 까닭) → 재료가 모자라면 안 됨 →
     * 다 맞추면 모이는 동안 창 없이 묶임 (단계 그대로) → GATHER 틱에 골드 5 · 드라우그 30 · 늑대 30 을 꼭 그만큼 받고 +1 · 화면 연출 · 칸 배지 →
     * SHOW 틱 뒤 창이 다시 열림 (결과 문구) → +2 는 48렙부터 · 5단계 막힘 · 직업을 바꿔도 남음
     */
    @GameTest(environment = ENV_UP, maxTicks = 160)
    public void upgradeFlow(GameTestHelper h) {
        ServerPlayer sp = jobPlayer(h, "swordsman", Map.of("str", 50), 43);
        Forms.HEADLESS.add(sp.getUUID());
        Jobs.Job j = Jobs.job("swordsman");
        Jobs.Skill flurry = j.skill(0);
        net.minecraft.world.item.Item draugr = item("rpg:loot_draugr");
        net.minecraft.world.item.Item wolf = item("rpg:loot_frost_wolf");
        h.assertTrue(draugr != null && wolf != null, "loot items exist");
        Wallet.set(sp, Economy.Kind.GOLD, 0);
        JobUpgrades.set(sp, flurry.id(), 0);
        // 교관 창: 오른쪽 위 강화 단추
        JobTrainer.open(sp, null);
        FormNet.ChestPayload cards = (FormNet.ChestPayload) Forms.last(sp);
        h.assertTrue(cards.slots().get(8).name().contains("직업 스킬 강화"), "upgrade button on the trainer window: " + cards.slots().get(8).name());
        Forms.answer(sp, 8, null);
        FormNet.ChestPayload list = Forms.last(sp) instanceof FormNet.ChestPayload c ? c : null;
        h.assertTrue(list != null && list.title().contains("직업 스킬 강화") && list.slots().size() == 27, "upgrade window");
        for (int i = 0; i < 5; i++) h.assertTrue(list.slots().get(11 + i).name().contains(j.skill(i).name()), "skill slot " + i);
        // 43렙: 확인 창의 강화 칸 흐림 · 본문에 44렙
        Forms.answer(sp, 11, null);
        FormNet.ChestPayload conf = (FormNet.ChestPayload) Forms.last(sp);
        h.assertTrue(conf.title().contains("스킬 강화 확인") && conf.slots().get(10).kind() == 3, "confirm: OK dim below level 44");
        h.assertTrue(conf.body().contains("44렙"), "body tells the level: " + conf.body());
        Forms.answer(sp, 16, null);
        h.assertTrue(Forms.last(sp) instanceof FormNet.ChestPayload c2 && c2.title().contains("직업 스킬 강화"), "cancel → upgrade window");
        // 44렙 · 골드 7 · 드라우그 30 · 늑대 29 → 늑대가 모자람 (아무것도 쓰지 않음)
        sp.setExperienceLevels(44);
        RpgPlayers.update(sp, RpgData.of(sp).withLvmax(44));
        Wallet.set(sp, Economy.Kind.GOLD, 7);
        sp.getInventory().add(new ItemStack(draugr, 30));
        sp.getInventory().add(new ItemStack(wolf, 29));
        JobUpgrades.Need n = JobUpgrades.need(sp, flurry);
        h.assertTrue(n.levelOk() && n.goldOk() && !n.itemsOk() && JobUpgrades.why(n).contains("서리 늑대 가죽"), "wolf pelts short: " + JobUpgrades.why(n));
        Forms.answer(sp, 11, null);
        Forms.answer(sp, 10, null);
        h.assertTrue(JobUpgrades.level(sp, flurry.id()) == 0 && Wallet.get(sp, Economy.Kind.GOLD) == 7 && mmorpg.fabric.ui.Inv.count(sp, draugr) == 30,
                "short: nothing paid");
        h.assertTrue(Forms.last(sp) instanceof FormNet.ChestPayload c3 && c3.body().contains("서리 늑대 가죽"), "short: reason in the window");
        // 늑대 +3 → 됨: 모이는 동안 창 없이 묶임 · 단계 그대로
        sp.getInventory().add(new ItemStack(wolf, 3));
        h.assertTrue(JobUpgrades.need(sp, flurry).ok(), "all ready");
        Forms.answer(sp, 11, null);
        Forms.answer(sp, 10, null);
        h.assertTrue(Forms.isOpen(sp) && !Forms.pending(sp) && JobUpgrades.level(sp, flurry.id()) == 0, "gathering: busy, no window, still +0");
        h.runAfterDelay(JobUpgrades.GATHER + 3, () -> {
            h.assertTrue(JobUpgrades.level(sp, flurry.id()) == 1, "+1 after gathering: " + JobUpgrades.level(sp, flurry.id()));
            h.assertTrue(Wallet.get(sp, Economy.Kind.GOLD) == 2, "paid 5 gold: " + Wallet.get(sp, Economy.Kind.GOLD));
            h.assertTrue(mmorpg.fabric.ui.Inv.count(sp, draugr) == 0 && mmorpg.fabric.ui.Inv.count(sp, wolf) == 2, "paid 30 draugr bones + 30 wolf pelts: "
                    + mmorpg.fabric.ui.Inv.count(sp, draugr) + " / " + mmorpg.fabric.ui.Inv.count(sp, wolf));
            JobUpgrades.FxPayload fx = JobUpgrades.lastFx(sp);
            h.assertTrue(fx != null && fx.level() == 1 && fx.max() == 5 && fx.skill().equals(flurry.id()) && fx.lines().get(0).equals("위력 ×1 → ×1.1"),
                    "screen effect payload: " + fx);
            h.assertTrue(JobSkills.payload(sp).ups().get(0) == 1, "slot badge +1");
            h.assertTrue(!Forms.pending(sp) && Forms.isOpen(sp), "showing: window not back yet");
        });
        h.runAfterDelay(JobUpgrades.GATHER + JobUpgrades.SHOW + 6, () -> {
            FormNet.ChestPayload back = Forms.last(sp) instanceof FormNet.ChestPayload c4 ? c4 : null;
            h.assertTrue(back != null && back.body().contains("강화: ") && back.slots().get(11).name().contains("+1"), "window back with the result: "
                    + (back == null ? "none" : back.body()));
            // +2 는 48렙부터
            h.assertTrue(JobUpgrades.why(JobUpgrades.need(sp, flurry)).contains("48렙"), "+2 needs 48: " + JobUpgrades.why(JobUpgrades.need(sp, flurry)));
            // 5단계 막힘 · 창에서 누르면 안내
            JobUpgrades.set(sp, flurry.id(), 5);
            h.assertTrue(JobUpgrades.need(sp, flurry).max() && JobUpgrades.why(JobUpgrades.need(sp, flurry)).contains("최대"), "+5 is the max");
            Forms.answer(sp, 11, null);
            h.assertTrue(Forms.last(sp) instanceof FormNet.ChestPayload c5 && c5.body().contains("최대"), "max: no confirm window");
            Forms.answer(sp, -1, null);
            // 직업을 바꿔도 스킬마다 남음
            JobSystem.setJob(sp, "warrior");
            h.assertTrue(JobUpgrades.level(sp, flurry.id()) == 5 && JobSkills.payload(sp).ups().get(0) == 0, "kept after job change (warrior slots +0)");
            JobSystem.setJob(sp, "swordsman");
            h.assertTrue(JobSkills.payload(sp).ups().get(0) == 5, "back to swordsman: +5 badge");
            JobUpgrades.set(sp, flurry.id(), 0);
            cleanup(sp);
            RpgGearTests.done(h, sp);
        });
    }

    /** 0.31.0 강화 피해: 연참 +3 → 위력 ×1.3 (과녁 하나 = P × 지속 보정 × 5 × 1.3), 명경지수 +2 → 두 번째 베기 35% → 42% (평타 × 1.42) */
    @GameTest(environment = ENV_UP2, maxTicks = 160)
    public void upgradeDamage(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = jobPlayer(h, "swordsman", Map.of("str", 50), 44);
        Jobs.Job j = Jobs.job("swordsman");
        Weapon w = Weapons.weaponFrom("sword", 0, "common");
        JobUpgrades.set(sp, "swordsman_flurry", 3);
        JobUpgrades.set(sp, "swordsman_mirror", 2);
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 2.5)));
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(1, () -> {
            home(h, sp);
            JobTraits.setStacks(sp, 0);
            h.assertTrue(JobSkills.press(sp, 0), "연참 +3 cast");
        }));
        steps.add(new Step(32, () -> {
            double want = expected(sp, j, j.skill(0), 5) * 1.3;
            h.assertTrue(near(TestDummies.dealt(z.get(0)), want), "연참 +3 = ×1.3: " + TestDummies.dealt(z.get(0)) + " vs " + want);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 2));
            h.assertTrue(JobSkills.press(sp, 3), "명경지수 +2 cast");
        }));
        steps.add(new Step(12, () -> {
            RpgCombat.forceCrit = false;
            double base = basic(sp, w);
            sp.attack(z.get(0));
            RpgCombat.forceCrit = null;
            h.assertTrue(near(TestDummies.dealt(z.get(0)), base * 1.42), "mirror +2 echo 42%: " + TestDummies.dealt(z.get(0)) + " vs " + base * 1.42);
        }));
        chain(h, steps, 0, () -> {
            JobUpgrades.set(sp, "swordsman_flurry", 0);
            JobUpgrades.set(sp, "swordsman_mirror", 0);
            cleanup(sp);
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }

    /**
     * 0.31.0 강화 회복 · 지속 · 값: 치유의 손길 +5 → 나 18% (12 × 1.5), 마나 과부하 +5 → 태세 300틱 (200 × 1.5), 암영 +5 첫 공격 ×3.25 (1 을 넘는 몫만 ×1.5),
     * 바람 타기 +3 덧쏘기 130%, 강화가 곱하지 않는 값 (맹세 오라) 은 그대로
     */
    @GameTest(environment = ENV_UP2, maxTicks = 60)
    public void upgradeSupport(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = jobPlayer(h, "priest", Map.of("int", 50, "vit", 20), 44);
        hold(sp, Weapons.weaponFrom("scepter", 0, "common"));
        JobUpgrades.set(sp, "priest_heal", 5);
        home(h, sp);
        Vec3 at = sp.position();
        sp.snapTo(at.x, at.y, at.z, 180F, 0F); // 아군 없이 → 나
        sp.setYHeadRot(180F);
        sp.setHealth(sp.getMaxHealth() * 0.5F);
        double before = sp.getHealth();
        h.assertTrue(JobSkills.press(sp, 0), "치유의 손길 +5 cast");
        h.assertTrue(close(sp.getHealth() - before, RpgHealth.maxOf(sp) * 0.18, 0.01), "heal +5 = 18%: " + (sp.getHealth() - before));
        // 매지션: 마나 과부하 +5 → 태세 300틱
        JobSystem.setJob(sp, "mage");
        hold(sp, Weapons.weaponFrom("wand", 0, "common"));
        JobUpgrades.set(sp, "mage_overload", 5);
        h.assertTrue(JobSkills.press(sp, 3), "마나 과부하 +5 cast");
        h.assertTrue(JobTraits.stanceOn(sp, "mage_overload") && JobTraits.stanceTotal(sp) == 300, "overload +5 lasts 300 ticks: " + JobTraits.stanceTotal(sp));
        // 값: 암영 첫 공격 (1 을 넘는 몫) · 바람 타기 덧쏘기 · 맹세 오라 (up 키 아님)
        Jobs.Skill veil = Jobs.job("assassin").skill("assassin_veil");
        Jobs.Skill wind = Jobs.job("archer").skill("archer_wind");
        Jobs.Skill oath = Jobs.job("paladin").skill("paladin_oath");
        JobUpgrades.set(sp, veil.id(), 5);
        JobUpgrades.set(sp, wind.id(), 3);
        JobUpgrades.set(sp, oath.id(), 5);
        h.assertTrue(close(JobUpgrades.num(sp, veil, "first", 2.5), 3.25, 1e-9), "veil +5 first ×3.25");
        h.assertTrue(close(JobUpgrades.num(sp, wind, "extra", 1), 1.3, 1e-9), "wind +3 extra 130%");
        h.assertTrue(JobUpgrades.ticks(sp, oath, "dur", 200) == 300 && close(JobUpgrades.num(sp, oath, "aura", 0.15), 0.15, 1e-9), "oath +5: 300 ticks, aura unchanged");
        for (String id : List.of("priest_heal", "mage_overload", veil.id(), wind.id(), oath.id())) JobUpgrades.set(sp, id, 0);
        cleanup(sp);
        RpgGearTests.done(h, sp);
    }
}
