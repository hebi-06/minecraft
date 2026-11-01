package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mmorpg.core.Economy;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgData;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.job.JobSkills;
import mmorpg.fabric.job.JobSystem;
import mmorpg.fabric.job.JobTrainer;
import mmorpg.fabric.job.JobTraits;
import mmorpg.fabric.job.JobTraits5;
import mmorpg.fabric.job.JobTraits6;
import mmorpg.fabric.job.JobTraitsMore;
import mmorpg.fabric.job.JobUpgrades;
import mmorpg.fabric.skill.Guards;
import mmorpg.fabric.ui.FormNet;
import mmorpg.fabric.ui.Forms;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 2차 전직 게임 시험 (jobs.json advance — 검사의 각성 시구르드(소드마스터) · 반전 다인슬레이프(블러드 나이트)):
 * <ul>
 * <li>2차 전직 창 (환경 mmorpg:job_adv — Forms.HEADLESS): 49렙 → 창은 열려 카드 미리 보기 · 고르면 50렙 안내, 50렙 → 각성 무료 · 반전으로 바꾸기 600 은화 (모자람 · 됨) ·
 * 이명(직업명) 표시 · 1차 카드는 같은 계열 안내 · 강화 단계는 같은 칸으로 이어짐 · 다른 1차 직업으로 바꾸면 2차 풀림 (확인 창이 알림)</li>
 * <li>소드마스터 (mmorpg:job_sm): 과녁 하나가 받은 피해 = 위력 × 피해 배율 × 배율 합 — 금빛 연격 (투지 3 → 검기, 5) · 그라니의 질주 (지나가며 1.5 · 다음 평타 치명타) ·
 * 만월 강하 (투지 8 → 치명 +30% × 투지 8 개 +64%) · 용의 심장 (두 번 베기 42% · −20% · 맞으면 받아치기) · 신들의 황혼 (돌진 · 4 + 3.5 × 4 + 22 = 40)</li>
 * <li>블러드 나이트 (mmorpg:job_bk): 피의 갈증 (체력 4% → 위력 +10%) · 흡혈 · 상처 (맞힐 때 +1 · 피의 질주 +2 · 상처 터뜨리기 상처 × 1 + 회복 15% · 끝나지 않는 전투
 * 상처 하나당 +5%) · 회그니의 맹세 (체력 15% · 피 칼날 42% · 상처 +1)</li>
 * <li>워로드 (mmorpg:job_wl): 분노 100 → 곰의 화신 (평타 +20% · 범위 · −15% · 분노가 줄어듦) · 투구 깨부수기 (6.5 · 방어 깨짐 +15%) · 곰의 메치기 (1.4 + 2.5) ·
 * 도끼 태풍 (1.8 × 4 + 4.5 · 흡혈) · 곰의 포효 (곰의 화신 · 쓰러지지 않음 · 위기 회복 둘) · 뵈드바르의 앞발 (6 × 4 + 28)</li>
 * <li>라이칸 (mmorpg:job_ly): 야성 (사냥감 +1 · 다른 적이면 1부터 · 공격 · 이동 속도 +2%) · 송곳니 베기 (야성 10 → 할퀴기 +30% · 5 씀) · 사냥감 덮치기 (3.9 · 기절 ·
 * 사냥감) · 달밤의 포효 (겁먹음 −15% · 3.7 + 8) · 늑대의 밤 (평타 두 번 42% · 이동 +20%) · 달 삼키기 (4.5 × 6 + 25)</li>
 * </ul>
 */
public final class RpgJobAdvanceTests {
    static final String ENV_ADV = "mmorpg:job_adv";
    static final String ENV_SM = "mmorpg:job_sm";
    static final String ENV_BK = "mmorpg:job_bk";
    static final String ENV_WL = "mmorpg:job_wl";
    static final String ENV_LY = "mmorpg:job_ly";

    /** 넓은 돌 바닥 (그라니의 질주가 비스듬히 내달릴 땅) */
    static void floor(GameTestHelper h) {
        for (int x = -2; x < 12; x++) for (int z = -2; z < 14; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }

    /** 시험 자리 (4.5, 2, 1.5) 에 남쪽 (+z) 을 보고 섬 */
    static void home(GameTestHelper h, ServerPlayer sp) {
        Vec3 at = h.absoluteVec(new Vec3(4.5, 2, 1.5));
        sp.snapTo(at.x, at.y, at.z, 0F, 0F);
        sp.setYHeadRot(0F);
        sp.setDeltaMovement(Vec3.ZERO);
    }

    /** 시험 자리에서 (dx, dz) 의 과녁 (밀려나지 않음) */
    static Husk dummy(GameTestHelper h, double dx, double dz) {
        Husk m = TestDummies.make(h.spawnWithNoFreeWill(EntityTypes.HUSK, (float) (4.5 + dx), 2F, (float) (1.5 + dz)));
        m.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        return m;
    }

    static ServerPlayer player(GameTestHelper h, String job, int level) {
        ServerPlayer sp = RpgJobTests.jobPlayer(h, job, Map.of("str", 50), level);
        return sp;
    }

    static FormNet.ChestPayload chest(ServerPlayer sp) {
        return Forms.last(sp) instanceof FormNet.ChestPayload c ? c : null;
    }

    // ------------------------------------------------------------ 2차 전직 창

    @GameTest(environment = ENV_ADV, maxTicks = 20)
    public void advanceFlow(GameTestHelper h) {
        ServerPlayer sp = player(h, "swordsman", 49);
        Forms.HEADLESS.add(sp.getUUID());
        try {
            Wallet.set(sp, Economy.Kind.SILVER, 0);
            int[] done = {0};
            JobTrainer.open(sp, () -> done[0]++);
            FormNet.ChestPayload list = chest(sp);
            h.assertTrue(list != null && list.slots().get(JobTrainer.ADV_SLOT).name().contains("2차 전직"), "trainer: 2차 전직 button");
            // 49렙: 창은 열림 (미리 보기) · 카드를 고르면 50렙 안내
            Forms.answer(sp, JobTrainer.ADV_SLOT, null);
            FormNet.ChestPayload adv = chest(sp);
            h.assertTrue(adv != null && adv.title().contains("2차 전직") && adv.slots().get(JobTrainer.AWAKEN_SLOT).name().contains("시구르드(소드마스터)")
                    && adv.slots().get(JobTrainer.INVERT_SLOT).name().contains("다인슬레이프(블러드 나이트)"), "advance window: 각성 · 반전 cards");
            Forms.answer(sp, JobTrainer.AWAKEN_SLOT, null);
            h.assertTrue(chest(sp) != null && chest(sp).body().contains("50렙") && "swordsman".equals(JobSystem.jobOf(sp)), "below 50: blocked");
            // 50렙 → 각성 (처음 2차 전직 — 무료)
            sp.setExperienceLevels(50);
            RpgPlayers.update(sp, RpgData.of(sp).withLvmax(50));
            JobUpgrades.set(sp, "swordsman_moon", 2);
            Forms.answer(sp, JobTrainer.AWAKEN_SLOT, null);
            h.assertTrue(chest(sp) != null && chest(sp).title().contains("2차 전직 확인"), "advance confirm");
            Forms.answer(sp, 11, null);
            h.assertTrue("swordmaster".equals(JobSystem.jobOf(sp)) && Wallet.get(sp, Economy.Kind.SILVER) == 0 && done[0] == 1, "awaken free: " + JobSystem.jobOf(sp));
            h.assertTrue(JobTrainer.optionText(sp).contains("시구르드(소드마스터)") && JobSystem.job(sp).display().equals("시구르드(소드마스터)"), "display 이명(직업명)");
            h.assertTrue(JobUpgrades.level(sp, "swordmaster_moon") == 2, "upgrade carries to the same slot: " + JobUpgrades.level(sp, "swordmaster_moon"));
            // 1차 카드 (검사) → 같은 계열 안내
            JobTrainer.open(sp, () -> done[0]++);
            Forms.answer(sp, JobTrainer.slotOf("swordsman"), null);
            h.assertTrue(chest(sp) != null && chest(sp).body().contains("2차 직업"), "same line (first-job card)");
            // 반전으로 바꾸기: 은화 0 → 모자람 · 650 → 600 내고 바꿈
            Forms.answer(sp, JobTrainer.ADV_SLOT, null);
            Forms.answer(sp, JobTrainer.INVERT_SLOT, null);
            h.assertTrue(chest(sp) != null && chest(sp).title().contains("바꾸기"), "switch confirm");
            Forms.answer(sp, 11, null);
            h.assertTrue("swordmaster".equals(JobSystem.jobOf(sp)) && chest(sp) != null && chest(sp).body().contains("부족"), "switch: not enough silver");
            Wallet.set(sp, Economy.Kind.SILVER, 650);
            Forms.answer(sp, JobTrainer.INVERT_SLOT, null);
            Forms.answer(sp, 11, null);
            h.assertTrue("bloodknight".equals(JobSystem.jobOf(sp)) && Wallet.get(sp, Economy.Kind.SILVER) == 50, "switched to invert for 600: " + JobSystem.jobOf(sp) + " · "
                    + Wallet.get(sp, Economy.Kind.SILVER));
            h.assertTrue(JobUpgrades.level(sp, "bloodknight_burst") == 2, "upgrade carries (invert)");
            // 같은 2차 직업 → 안내
            JobTrainer.open(sp, () -> done[0]++);
            Forms.answer(sp, JobTrainer.ADV_SLOT, null);
            Forms.answer(sp, JobTrainer.INVERT_SLOT, null);
            h.assertTrue(chest(sp) != null && chest(sp).body().contains("이미"), "same advanced job");
            // 다른 1차 직업 (전사 — 300) → 2차 풀림 (확인 창이 알림)
            Wallet.set(sp, Economy.Kind.SILVER, 300);
            Forms.answer(sp, JobTrainer.BACK_SLOT, null);
            Forms.answer(sp, JobTrainer.slotOf("warrior"), null);
            h.assertTrue(chest(sp) != null && chest(sp).body().contains("풀립니다"), "change confirm warns the second job is cleared");
            Forms.answer(sp, 11, null);
            h.assertTrue("warrior".equals(JobSystem.jobOf(sp)) && Wallet.get(sp, Economy.Kind.SILVER) == 0, "changed first job → second job cleared: " + JobSystem.jobOf(sp));
            // 0.51.0 전사의 2차 전직 (뵈드바르(워로드) · 울프헤딘(라이칸)) — 1차 직업에서 처음이라 무료
            JobTrainer.open(sp, () -> done[0]++);
            FormNet.ChestPayload wa = chest(sp);
            h.assertTrue(wa != null && wa.slots().get(JobTrainer.ADV_SLOT).kind() != 3, "warrior: 2차 전직 open");
            Forms.answer(sp, JobTrainer.ADV_SLOT, null);
            FormNet.ChestPayload wadv = chest(sp);
            h.assertTrue(wadv != null && wadv.slots().get(JobTrainer.AWAKEN_SLOT).name().contains("뵈드바르(워로드)")
                    && wadv.slots().get(JobTrainer.INVERT_SLOT).name().contains("울프헤딘(라이칸)"), "warrior advance cards");
            Forms.answer(sp, JobTrainer.INVERT_SLOT, null);
            h.assertTrue(chest(sp) != null && chest(sp).title().contains("2차 전직 확인"), "warrior advance confirm");
            Forms.answer(sp, 11, null);
            h.assertTrue("lycan".equals(JobSystem.jobOf(sp)) && Wallet.get(sp, Economy.Kind.SILVER) == 0 && JobSystem.job(sp).display().equals("울프헤딘(라이칸)"),
                    "lycan free from the first job: " + JobSystem.jobOf(sp));
            // 마검사의 2차 전직은 아직 준비 중 (다른 1차 직업 — 300)
            Wallet.set(sp, Economy.Kind.SILVER, 300);
            JobTrainer.open(sp, () -> done[0]++);
            Forms.answer(sp, JobTrainer.slotOf("spellblade"), null);
            Forms.answer(sp, 11, null);
            h.assertTrue("spellblade".equals(JobSystem.jobOf(sp)), "changed to spellblade: " + JobSystem.jobOf(sp));
            JobTrainer.open(sp, () -> done[0]++);
            FormNet.ChestPayload sb = chest(sp);
            h.assertTrue(sb != null && sb.slots().get(JobTrainer.ADV_SLOT).kind() == 3, "spellblade: 2차 전직 dim (준비 중)");
            Forms.answer(sp, -1, null);
        } finally {
            JobUpgrades.set(sp, "swordsman_moon", 0);
            RpgJobTests.cleanup(sp);
        }
        RpgGearTests.done(h, sp);
    }

    // ------------------------------------------------------------ 시구르드(소드마스터)

    @GameTest(environment = ENV_SM, maxTicks = 520)
    public void swordmasterSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h, "swordmaster", 50);
        Jobs.Job j = Jobs.job("swordmaster");
        Weapon w = Weapons.weaponFrom("sword", 0, "common");
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 0, 3)));
        List<RpgJobTests.Step> steps = new ArrayList<>();
        // 금빛 연격: 투지 3 → 마지막 X자가 검기 (투지 셋 씀), 1.0 + 1.0 + 1.2 + 1.8
        steps.add(new RpgJobTests.Step(1, () -> {
            home(h, sp);
            JobTraits.setStacks(sp, 3);
            h.assertTrue(JobSkills.press(sp, 0), "금빛 연격 cast");
            h.assertTrue(JobTraits.stacks(sp) == 0, "검기 used 3 stacks: " + JobTraits.stacks(sp));
        }));
        steps.add(new RpgJobTests.Step(38, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(0), 5);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "금빛 연격 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(RpgJobTests.gradientNumber(h, z.get(0), j), "swordmaster gradient number");
            z.get(0).discard();
            home(h, sp);
            // 그라니의 질주: 첫 질주 (앞 오른쪽 18°) 길 위의 과녁
            double a = Math.toRadians(j.skill(1).num("angle", 18));
            z.set(0, dummy(h, -Math.sin(a) * 2.2, Math.cos(a) * 2.2));
            h.assertTrue(JobSkills.press(sp, 1), "그라니의 질주 cast");
        }));
        steps.add(new RpgJobTests.Step(20, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(1), 1.5);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "그라니의 질주 (one cut) " + TestDummies.dealt(z.get(0)) + " vs " + want);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2));
            double base = RpgJobTests.basic(sp, w);
            sp.attack(z.get(0));
            double crit = base * RpgCombat.critMultOf(sp, w);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), crit), "basic after 그라니 is a crit " + TestDummies.dealt(z.get(0)) + " vs " + crit);
        }));
        // 만월 강하: 투지 8 → 치명 (+30% · 투지 3 씀) · 투지를 모두 써서 하나당 +8% (8 → +64%) · 착지 2.4 → 발키리의 가호
        double[] moonWant = {0, 1};
        steps.add(new RpgJobTests.Step(12, () -> {
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2.5));
            JobTraits.setStacks(sp, 8);
            moonWant[0] = RpgJobTests.expected(sp, j, j.skill(2), 9 * (1 + 0.08 * 8) * (1 + j.trait().def().numOr("crit", 0.3)));
            moonWant[1] = RpgCombat.dealtMult.applyAsDouble(sp);
            h.assertTrue(JobSkills.press(sp, 2), "만월 강하 cast");
            h.assertTrue(JobTraits.stacks(sp) == 0 && JobTraits5.critOn(sp), "crit window on, stacks used: " + JobTraits.stacks(sp));
        }));
        steps.add(new RpgJobTests.Step(46, () -> {
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), moonWant[0]), "만월 강하 (crit · 8 stacks) " + TestDummies.dealt(z.get(0)) + " vs " + moonWant[0]);
            h.assertTrue(mmorpg.fabric.rune.RuneMarks.empowerLeft(sp) > 0 && Math.abs(RpgCombat.dealtMult.applyAsDouble(sp) - moonWant[1] * 1.1) < 1e-6,
                    "발키리의 가호 after landing: " + RpgCombat.dealtMult.applyAsDouble(sp));
            mmorpg.fabric.rune.RuneMarks.testClear();
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2));
            h.assertTrue(JobSkills.press(sp, 3), "용의 심장 cast");
            h.assertTrue(JobTraits.stanceOn(sp, "swordmaster_heart") && Math.abs(Guards.of(sp).reduce() - 0.2) < 1e-9, "stance on, guard 20%");
        }));
        // 용의 심장: 칼 평타 = 평타 × 1.42 · 맞으면 받아치기 (위력 × 1)
        double[] before = {0};
        steps.add(new RpgJobTests.Step(8, () -> {
            RpgCombat.forceCrit = false;
            double base = RpgJobTests.basic(sp, w);
            sp.attack(z.get(0));
            RpgCombat.forceCrit = null;
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), base * 1.42), "heart echo " + TestDummies.dealt(z.get(0)) + " vs " + base * 1.42);
            before[0] = TestDummies.dealt(z.get(0));
            // 맞음: 받는 피해 처리 (RpgHealth.onLoss — 실제 피해와 같은 길 · 끝에 lossHook) — 0.51.0 시험 1: sp.hurtServer 는 시험 플레이어에게 피해가 들어가지 않아 받아치기가 없었음
            mmorpg.fabric.RpgHealth.onLoss(sp, sp.damageSources().mobAttack(z.get(0)), sp.getHealth(), sp.getHealth() - 4F);
        }));
        // 받아치기는 2틱 뒤 — 바로 앞 평타로 과녁이 무적 시간 (10틱) 이라 JobKit.strike 가 그 뒤로 미룸 → 14틱 뒤에 확인 (0.51.0 시험 2: 6틱 뒤는 아직 0)
        steps.add(new RpgJobTests.Step(14, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(3), j.skill(3).num("counter", 1.0));
            double got = TestDummies.dealt(z.get(0)) - before[0];
            h.assertTrue(RpgJobTests.near(got, want), "heart counter " + got + " vs " + want);
        }));
        // 신들의 황혼: 앞 과녁 쪽으로 돌진 → 올려 베기 4 → 공중 3.5 × 4 → 내리꽂음 22 (= 40)
        double[] start = {0, 0};
        steps.add(new RpgJobTests.Step(10, () -> {
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 3));
            start[0] = sp.position().z;
            start[1] = sp.getY();
            h.assertTrue(JobSkills.press(sp, 4), "신들의 황혼 cast");
        }));
        steps.add(new RpgJobTests.Step(7, () -> {
            double moved = sp.position().z - start[0];
            h.assertTrue(moved > 0.5 && moved < 1.8, "신들의 황혼 rushed to the target: " + moved);
        }));
        steps.add(new RpgJobTests.Step(70, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(4), 40);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "신들의 황혼 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            // 내려앉음: 시험 플레이어는 물리가 돌지 않아 onGround 가 바뀌지 않음 (0.51.0 시험 3) → 높이로 봄 (JobMove 가 마지막 틱에 땅 높이로)
            h.assertTrue(Math.abs(sp.getY() - start[1]) < 0.3, "landed back on the ground: y " + sp.getY() + " vs " + start[1]);
        }));
        RpgJobTests.chain(h, steps, 0, () -> {
            RpgJobTests.cleanup(sp);
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }

    // ------------------------------------------------------------ 다인슬레이프(블러드 나이트)

    @GameTest(environment = ENV_BK, maxTicks = 420)
    public void bloodknightSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h, "bloodknight", 50);
        Jobs.Job j = Jobs.job("bloodknight");
        Weapon w = Weapons.weaponFrom("sword", 0, "common");
        double bonus = 1 + j.trait().def().numOr("bonus", 0.1);
        double cost = j.trait().def().numOr("cost", 0.04);
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 0, 2.5)));
        List<RpgJobTests.Step> steps = new ArrayList<>();
        // 핏빛 연격: 체력 4% 를 바쳐 위력 +10% · 맞힐 때마다 상처 +1 (셋) · 흡혈
        steps.add(new RpgJobTests.Step(1, () -> {
            home(h, sp);
            sp.setHealth(sp.getMaxHealth());
            h.assertTrue(JobSkills.press(sp, 0), "핏빛 연격 cast");
            h.assertTrue(Math.abs(sp.getHealth() - sp.getMaxHealth() * (1 - cost)) < sp.getMaxHealth() * 0.01, "paid 4% hp: " + sp.getHealth() + " / " + sp.getMaxHealth());
        }));
        steps.add(new RpgJobTests.Step(26, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(0), 5 * bonus);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "핏빛 연격 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits5.woundsOf(sp, z.get(0)) == 3, "3 wounds: " + JobTraits5.woundsOf(sp, z.get(0)));
            h.assertTrue(sp.getHealth() > sp.getMaxHealth() * (1 - cost) + 1e-3, "lifesteal healed: " + sp.getHealth());
            h.assertTrue(RpgJobTests.gradientNumber(h, z.get(0), j), "bloodknight gradient number");
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2));
            h.assertTrue(JobSkills.press(sp, 1), "피의 질주 cast");
        }));
        // 피의 질주: 지나가며 3 (+10%) · 상처 1 + 2
        steps.add(new RpgJobTests.Step(8, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(1), 3 * bonus);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "피의 질주 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits5.woundsOf(sp, z.get(0)) == 3, "rush wounds 1 + 2: " + JobTraits5.woundsOf(sp, z.get(0)));
            z.get(0).discard();
        }));
        // 상처 터뜨리기: 상처 4 + 한 바퀴 (+1) → 5 를 터뜨림 — (4 + 1 × 5) × 1.1 · 터뜨린 피해의 15% 회복
        double[] hp0 = {0};
        steps.add(new RpgJobTests.Step(4, () -> {
            home(h, sp);
            z.set(0, dummy(h, 0, 2.5));
            JobTraits5.setWounds(sp, z.get(0), 4);
            sp.setHealth(sp.getMaxHealth() * 0.5F);
            h.assertTrue(JobSkills.press(sp, 2), "상처 터뜨리기 cast");
            hp0[0] = sp.getHealth();
        }));
        steps.add(new RpgJobTests.Step(16, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(2), 9 * bonus);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "상처 터뜨리기 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            double burst = RpgJobTests.expected(sp, j, j.skill(2), 5 * bonus);
            double healWant = Math.min(sp.getMaxHealth(), hp0[0] + burst * j.skill(2).num("heal", 0.15));
            h.assertTrue(sp.getHealth() >= healWant - sp.getMaxHealth() * 0.01, "burst healed 15%: " + sp.getHealth() + " vs " + healWant);
            h.assertTrue(JobTraits5.woundsOf(sp, z.get(0)) <= 1, "wounds taken: " + JobTraits5.woundsOf(sp, z.get(0)));
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2));
            sp.setHealth(sp.getMaxHealth());
            h.assertTrue(JobSkills.press(sp, 3), "회그니의 맹세 cast");
            h.assertTrue(JobTraits.stanceOn(sp, "bloodknight_oath") && Math.abs(sp.getHealth() - sp.getMaxHealth() * 0.85) < sp.getMaxHealth() * 0.01,
                    "oath stance on, paid 15%: " + sp.getHealth());
        }));
        // 회그니의 맹세: 칼 평타 = 평타 × 1.42 · 상처 +1
        steps.add(new RpgJobTests.Step(8, () -> {
            RpgCombat.forceCrit = false;
            double base = RpgJobTests.basic(sp, w);
            sp.attack(z.get(0));
            RpgCombat.forceCrit = null;
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), base * 1.42), "oath blood blade " + TestDummies.dealt(z.get(0)) + " vs " + base * 1.42);
            h.assertTrue(JobTraits5.woundsOf(sp, z.get(0)) == 1, "oath wound +1: " + JobTraits5.woundsOf(sp, z.get(0)));
        }));
        // 끝나지 않는 전투: 체력 10% · 상처 4 → 첫 X자 뒤 5 → 내리꽂음 24 × (1 + 5% × 5) — 4 × 4 + 30 = 46 (제 값을 바치므로 위력 +10% 없음)
        double[] start = {0};
        steps.add(new RpgJobTests.Step(4, () -> {
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 3));
            JobTraits5.setWounds(sp, z.get(0), 4);
            sp.setHealth(sp.getMaxHealth());
            start[0] = sp.position().z;
            h.assertTrue(JobSkills.press(sp, 4), "끝나지 않는 전투 cast");
            h.assertTrue(Math.abs(sp.getHealth() - sp.getMaxHealth() * 0.9) < sp.getMaxHealth() * 0.01, "ult paid 10%: " + sp.getHealth());
        }));
        steps.add(new RpgJobTests.Step(7, () -> {
            double moved = sp.position().z - start[0];
            h.assertTrue(moved > 0.5 && moved < 1.8, "끝나지 않는 전투 rushed to the target: " + moved);
        }));
        steps.add(new RpgJobTests.Step(62, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(4), 16 + 24 * (1 + 0.05 * 5));
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "끝나지 않는 전투 " + TestDummies.dealt(z.get(0)) + " vs " + want);
        }));
        RpgJobTests.chain(h, steps, 0, () -> {
            RpgJobTests.cleanup(sp);
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }


    // ------------------------------------------------------------ 뵈드바르(워로드)

    @GameTest(environment = ENV_WL, maxTicks = 560)
    public void warlordSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h, "warlord", 50);
        Weapon w = Weapons.weaponFrom("axe", 0, "common");
        RpgJobTests.hold(sp, w);
        Jobs.Job j = Jobs.job("warlord");
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 0, 2)));
        List<RpgJobTests.Step> steps = new ArrayList<>();
        // 분노 96 에서 도끼 평타 (50 이상 +10%) → +8 → 100 → 곰의 화신 (8초 · 받는 피해 −15% · 곰 발톱 평타 모습)
        steps.add(new RpgJobTests.Step(1, () -> {
            home(h, sp);
            RpgCombat.forceCrit = false;
            JobTraitsMore.setRage(sp, 96);
            double base = RpgJobTests.basic(sp, w);
            sp.attack(z.get(0));
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), base * 1.1), "rage 96 basic × 1.1: " + TestDummies.dealt(z.get(0)) + " vs " + base * 1.1);
            h.assertTrue(JobTraits6.bearOn(sp) && JobTraits6.bearLeft(sp) > 150 && JobTraitsMore.rage(sp) == 100, "bear avatar on at rage 100: " + JobTraits6.bearLeft(sp));
            h.assertTrue(Math.abs(Guards.of(sp).reduce() - 0.15) < 1e-9, "avatar guard 15%: " + Guards.of(sp).reduce());
            h.assertTrue("warlord_bear".equals(mmorpg.fabric.motion.SwingLook.testKey(sp)), "bear swing look: " + mmorpg.fabric.motion.SwingLook.testKey(sp));
        }));
        // 곰의 화신 동안 도끼 평타 × 1.2 (분노가 줄어도) · 분노가 남은 시간만큼 줄어듦 · 쌓이지 않음
        double[] mark = {0};
        steps.add(new RpgJobTests.Step(14, () -> {
            double before = TestDummies.dealt(z.get(0));
            double base = RpgJobTests.basic(sp, w);
            sp.attack(z.get(0));
            RpgCombat.forceCrit = null;
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)) - before, base * 1.2), "avatar basic × 1.2: " + (TestDummies.dealt(z.get(0)) - before) + " vs " + base * 1.2);
            h.assertTrue(JobTraitsMore.rage(sp) < 100 && JobTraitsMore.rage(sp) > 80, "rage drains during the avatar: " + JobTraitsMore.rage(sp));
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2.6));
            h.assertTrue(JobSkills.press(sp, 0), "투구 깨부수기 cast");
        }));
        // 투구 깨부수기: 6.5 · 맞은 적 방어 깨짐 × 1.15
        steps.add(new RpgJobTests.Step(18, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(0), 6.5);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "투구 깨부수기 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(Math.abs(JobTraitsMore.breakMult(z.get(0)) - 1.15) < 1e-9, "armor break × 1.15: " + JobTraitsMore.breakMult(z.get(0)));
            h.assertTrue(RpgJobTests.gradientNumber(h, z.get(0), j), "warlord gradient number");
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 4));
            mark[0] = sp.position().z;
            h.assertTrue(JobSkills.press(sp, 1), "곰의 메치기 cast");
        }));
        // 곰의 메치기: 들이받음 1.4 + 메침 2.5 (허수아비는 들리지 않음)
        steps.add(new RpgJobTests.Step(18, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(1), 3.9);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "곰의 메치기 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(sp.position().z - mark[0] > 1 && z.get(0).position().z - sp.position().z < 2.8, "charged to the target: " + (sp.position().z - mark[0]));
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2));
            sp.setHealth(sp.getMaxHealth() * 0.5F);
            mark[0] = sp.getHealth();
            h.assertTrue(JobSkills.press(sp, 2), "도끼 태풍 cast");
        }));
        // 도끼 태풍: 1.8 × 4 + 4.5 = 11.7 · 흡혈
        steps.add(new RpgJobTests.Step(52, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(2), 11.7);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "도끼 태풍 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(sp.getHealth() > mark[0] + 1e-3, "storm lifesteal: " + mark[0] + " -> " + sp.getHealth());
            sp.setHealth(sp.getMaxHealth());
            h.assertTrue(JobSkills.press(sp, 3), "곰의 포효 cast");
        }));
        // 곰의 포효 (5틱에 포효): 곰의 화신 새로 · 쓰러지지 않음 (2초) · 위기 회복 둘 — 체력 20% 로 떨어지면 20% 회복
        double[] hp = {0};
        steps.add(new RpgJobTests.Step(8, () -> {
            h.assertTrue(JobTraits6.bearLeft(sp) > 150 && JobTraits6.undyingOn(sp) && JobTraits6.roarHealsLeft(sp) == 2, "roar: avatar " + JobTraits6.bearLeft(sp)
                    + " · undying · heals " + JobTraits6.roarHealsLeft(sp));
            float max = sp.getMaxHealth();
            sp.setHealth(max * 0.2F);
            hp[0] = sp.getHealth();
            mmorpg.fabric.RpgHealth.lossHook.lost(sp, sp.damageSources().mobAttack(z.get(0)), 2, hp[0] + 2, hp[0]);
        }));
        steps.add(new RpgJobTests.Step(4, () -> {
            double want = hp[0] + sp.getMaxHealth() * j.skill(3).num("heal", 0.2);
            h.assertTrue(sp.getHealth() >= want - sp.getMaxHealth() * 0.01 && JobTraits6.roarHealsLeft(sp) == 1, "crisis heal 20%: " + sp.getHealth() + " vs " + want);
        }));
        steps.add(new RpgJobTests.Step(40, () -> h.assertTrue(!JobTraits6.undyingOn(sp) && JobTraits6.bearOn(sp), "undying 2s ends, avatar stays")));
        // 뵈드바르의 앞발: 6 × 4 + 28 = 52
        double[] start = {0};
        steps.add(new RpgJobTests.Step(4, () -> {
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 3));
            start[0] = sp.getY();
            h.assertTrue(JobSkills.press(sp, 4), "뵈드바르의 앞발 cast");
        }));
        steps.add(new RpgJobTests.Step(90, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(4), 52);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "뵈드바르의 앞발 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(Math.abs(sp.getY() - start[0]) < 0.3, "landed back on the ground: y " + sp.getY() + " vs " + start[0]);
        }));
        RpgJobTests.chain(h, steps, 0, () -> {
            RpgJobTests.cleanup(sp);
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }

    // ------------------------------------------------------------ 울프헤딘(라이칸)

    @GameTest(environment = ENV_LY, maxTicks = 560)
    public void lycanSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h, "lycan", 50);
        Weapon w = Weapons.weaponFrom("axe", 0, "common");
        RpgJobTests.hold(sp, w);
        Jobs.Job j = Jobs.job("lycan");
        double claw = 1 + j.trait().def().numOr("clawBonus", 0.3);
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 0, 2), dummy(h, 1.6, 2)));
        List<RpgJobTests.Step> steps = new ArrayList<>();
        // 야성: 같은 적을 평타로 이어 치면 +1 · 다른 적을 먼저 치면 그 적이 사냥감 · 1부터
        steps.add(new RpgJobTests.Step(1, () -> {
            home(h, sp);
            JobTraits6.setFerocity(sp, 0);
            sp.attack(z.get(0));
            h.assertTrue(JobTraits6.ferocity(sp) == 1 && z.get(0).getUUID().equals(JobTraits6.preyOf(sp)), "ferocity 1 on the prey: " + JobTraits6.ferocity(sp));
        }));
        steps.add(new RpgJobTests.Step(11, () -> {
            sp.attack(z.get(0));
            h.assertTrue(JobTraits6.ferocity(sp) == 2, "same prey → 2: " + JobTraits6.ferocity(sp));
        }));
        steps.add(new RpgJobTests.Step(11, () -> {
            sp.attack(z.get(1));
            h.assertTrue(JobTraits6.ferocity(sp) == 1 && z.get(1).getUUID().equals(JobTraits6.preyOf(sp)), "other enemy → new prey, 1: " + JobTraits6.ferocity(sp));
            // 하나당 공격 속도 · 이동 속도 +2% (10 → +20% · 평타 사이 8틱 · 서버 6틱)
            JobTraits6.setFerocity(sp, 10);
            h.assertTrue(Math.abs(JobTraits6.speedBonus(sp, false) - 0.2) < 1e-9 && Math.abs(RpgCombat.attackSpeed.applyAsDouble(sp) - 1.2) < 1e-9
                    && mmorpg.core.Motions.serverInterval(RpgCombat.attackSpeed.applyAsDouble(sp)) == 6, "ferocity 10: speed " + JobTraits6.speedBonus(sp, false)
                            + " · attack " + RpgCombat.attackSpeed.applyAsDouble(sp));
            for (Husk x : z) x.discard();
            z.clear();
            home(h, sp);
            z.add(dummy(h, 0, 2.5));
            h.assertTrue(JobSkills.press(sp, 0), "송곳니 베기 cast");
            // 할퀴기는 쓸 때 야성 5 를 먼저 씀 → 첫 X자가 바로 들어가 사냥감 +1 (6)
            h.assertTrue(JobTraits6.clawOn(sp) && JobTraits6.ferocity(sp) == 6, "claws on, 5 used, first hit +1: " + JobTraits6.ferocity(sp));
        }));
        // 송곳니 베기: (1.6 + 1.6 + 3.3) × 1.3 (할퀴기) · 맞힐 때마다 사냥감 야성 +1 (5 → 8)
        steps.add(new RpgJobTests.Step(26, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(0), 6.5 * claw);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "송곳니 베기 (claws) " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits6.ferocity(sp) == 8, "skill hits on the prey 5 → 8: " + JobTraits6.ferocity(sp));
            h.assertTrue(RpgJobTests.gradientNumber(h, z.get(0), j), "lycan gradient number");
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 4));
            JobTraits6.setFerocity(sp, 3);
            h.assertTrue(JobSkills.press(sp, 1), "사냥감 덮치기 cast");
        }));
        // 사냥감 덮치기: 3.9 · 기절 (구속) · 그 적이 사냥감 (야성 3 → 4)
        steps.add(new RpgJobTests.Step(12, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(1), 3.9);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "사냥감 덮치기 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(z.get(0).getUUID().equals(JobTraits6.preyOf(sp)) && JobTraits6.ferocity(sp) == 4, "pounce prey · ferocity 4: " + JobTraits6.ferocity(sp));
            h.assertTrue(z.get(0).hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS), "stunned");
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2));
            JobTraits6.setFerocity(sp, 0);
            h.assertTrue(JobSkills.press(sp, 2), "달밤의 포효 cast");
        }));
        // 달밤의 포효: 4틱 포효 3.7 · 겁먹음 (가하는 피해 −15%) → 16틱 크게 한 바퀴 8
        steps.add(new RpgJobTests.Step(8, () -> {
            h.assertTrue(Math.abs(JobTraits6.fearOf(z.get(0)) - 0.15) < 1e-9 && Math.abs(mmorpg.fabric.RpgHealth.weakenOf.applyAsDouble(z.get(0)) - 0.15) < 1e-6,
                    "feared −15%: " + JobTraits6.fearOf(z.get(0)));
        }));
        steps.add(new RpgJobTests.Step(14, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(2), 11.7);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "달밤의 포효 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobSkills.press(sp, 3), "늑대의 밤 cast");
            h.assertTrue(JobTraits.stanceOn(sp, "lycan_night") && Math.abs(JobTraits6.speedBonus(sp, true) - 0.2) < 1e-9, "night stance · move +20%");
        }));
        // 늑대의 밤: 도끼 평타 × 1.42 (두 번째 베기) · 야성 유지
        steps.add(new RpgJobTests.Step(8, () -> {
            RpgCombat.forceCrit = false;
            double before = TestDummies.dealt(z.get(0));
            double base = RpgJobTests.basic(sp, w);
            sp.attack(z.get(0));
            RpgCombat.forceCrit = null;
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)) - before, base * 1.42), "night echo " + (TestDummies.dealt(z.get(0)) - before) + " vs " + base * 1.42);
        }));
        // 달 삼키기: 4.5 × 6 + 25 = 52
        double[] start = {0};
        steps.add(new RpgJobTests.Step(4, () -> {
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 3));
            JobTraits6.setFerocity(sp, 0);
            start[0] = sp.getY();
            h.assertTrue(JobSkills.press(sp, 4), "달 삼키기 cast");
        }));
        steps.add(new RpgJobTests.Step(96, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(4), 52);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "달 삼키기 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(Math.abs(sp.getY() - start[0]) < 0.3, "landed back on the ground: y " + sp.getY() + " vs " + start[0]);
            h.assertTrue(JobTraits6.speedBonus(sp, true) > 0 || !JobTraits.stanceOn(sp, "lycan_night"), "night speed while the stance lasts");
        }));
        RpgJobTests.chain(h, steps, 0, () -> {
            RpgJobTests.cleanup(sp);
            h.assertTrue(JobTraits6.speedBonus(sp, false) == 0 && JobTraits6.speedBonus(sp, true) == 0, "speed modifiers removed on job change");
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }
}
