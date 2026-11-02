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
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.job.JobSkills;
import mmorpg.fabric.job.JobSystem;
import mmorpg.fabric.job.JobTrainer;
import mmorpg.fabric.job.JobTraits;
import mmorpg.fabric.job.JobTraits5;
import mmorpg.fabric.job.JobTraits6;
import mmorpg.fabric.job.JobTraits7;
import mmorpg.fabric.job.JobTraits8;
import mmorpg.fabric.job.JobTraits9;
import mmorpg.fabric.job.NecroCrew;
import mmorpg.fabric.job.JobTraitsMore;
import mmorpg.fabric.job.JobUpgrades;
import mmorpg.fabric.skill.Guards;
import mmorpg.fabric.stattree.StatTrees;
import mmorpg.fabric.ui.FormNet;
import mmorpg.fabric.ui.Forms;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
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
 * <li>룬 나이트 (mmorpg:job_rk): 룬 새김 베기 (3.6 + 4.9 · 각인 1) → 평타가 각인을 터뜨림 (위력 × 1.0) · 둘레의 다른 적에게 번짐 (× 0.5) · 세이드 섬광 (길 1.2 + 되돌아옴 1.2 +
 * 그 곁 룬 원 1.35) · 푸타르크 대검무 (2.4 × 4 + 5.7 · 떠오름 · 룬 결계 −20%) · 룬 담금질 (평타 + 위력 × 0.37 × 3 · 사거리 +1.5) · 스물네 룬의 대검 (10 × 3 + 14 + 24 + 각인 1)</li>
 * <li>헥스 블레이드 (mmorpg:job_hx): 뒤집힌 룬 (평타 +1) · 가시 룬 베기 (룬 3 → 3.0 × 1.36 · 룬을 터뜨림 → 5.5 · 가시 룬 +2) · 뒤집힌 걸음 (뒤로 6칸 · 덫 5.1 × 1.6 · 다섯이면
 * 묶음) · 우박의 룬 (2.2 → 방어 깨짐 +10% → 2.2 × 3 + 6.5 를 × 1.1) · 저주의 칼 (평타 + 위력 × 0.5 · 룬 +2) · 재앙의 룬 (줄기 획 10 · 셋째 획 10 · 가르기 12 · 터짐 26)</li>
 * <li>아크메이지 (mmorpg:job_am): 별무리 마탄 (1.4 × 5 · 다른 적에게 별빛 0.7 × 5) · 서리 별 걸음 (두 잔상 2.8 · 1.4 — 빙결) · 쌍둥이 별 (1.4 × 6 + 4.2) · 꿀술의 노래
 * (스킬마다 별똥 위력 × 2.4) · 별자리 완성 (연쇄 5 — 별 다섯 × 2 + 선 1 · 연쇄는 처음부터) · 완드 평타 → 재사용 대기 −6틱 · 별의 노래 (3 × 9 + 1.4 × 10 + 15)</li>
 * <li>네크로맨서 (mmorpg:job_nc): 뼈 창 (4.9 + 2.1 · 표식) → 쓰러뜨리면 망자 선원 (rpg:naglfar_crew) 이 일어나 싸움 (위력 × 0.4 — 내 직업 스킬 피해) · 망자 일으키기 (둘 · +30%) ·
 * 망자와 자리 바꾸기 (자리 · 뼈 방벽 2.8 + 1.4) · 시체 폭발 (시체 · 망자를 터뜨림 8.4 + 1.4 × 3) · 나글파르의 출항 (14 + 3 × 6 + 24 · 선원 여섯)</li>
 * <li>스나이퍼 (mmorpg:job_sn): 꿰뚫는 연사 (1.2 × 2 + 1.4 × 2 + 2.3 · 뒤의 과녁 2.3) · 급소 사격 (호흡 8 → 치명타 확정 · 꿰뚫음 · 호흡 3 씀) · 순풍 공중 사격 (뜀 · 활 평타 × 1 세 발 ·
 * 호흡 그대로) · 울르의 폭우 (6.75 × 2 · 묶음) · 사과 겨누기 (호흡 8 · 급소 사격이 호흡을 쓰지 않음) · 세 번째 화살 (8 × 2 + 30 + 14 · 뜸 → 내려앉음)</li>
 * <li>섀도 헌터 (mmorpg:job_sh): 검은 화살 (2.5 × 3 · 저주 +1 · 추격 +1 → 이동 +3%) · 그림자 구르기 (받는 피해 없음 · 활 평타 × 1.5 · 저주 +1) · 저주 번짐 (5.5 → 터짐 8 × 저주 1.02 ·
 * 둘레 4 / 쓰러지면 바로 번짐) · 그믐밤 (치명타 +20%p · 추격이 줄지 않음 · 몹이 나를 놓침) · 어둠숲의 사냥 (저주 +2 → (6 × 4 + 36) × 1.04)</li>
 * </ul>
 */
public final class RpgJobAdvanceTests {
    static final String ENV_ADV = "mmorpg:job_adv";
    static final String ENV_SM = "mmorpg:job_sm";
    static final String ENV_BK = "mmorpg:job_bk";
    static final String ENV_WL = "mmorpg:job_wl";
    static final String ENV_LY = "mmorpg:job_ly";
    static final String ENV_RK = "mmorpg:job_rk";
    static final String ENV_HX = "mmorpg:job_hx";
    static final String ENV_AM = "mmorpg:job_am";
    static final String ENV_NC = "mmorpg:job_nc";
    static final String ENV_SN = "mmorpg:job_sn";
    static final String ENV_SH = "mmorpg:job_sh";

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

    /** 시험 자리 (4.5, 2, z) 에 남쪽 (+z) 을 보고 섬 (뒤로 순간 이동하는 스킬 — 뒤에 바닥이 있게) */
    static void homeAt(GameTestHelper h, ServerPlayer sp, double z) {
        Vec3 at = h.absoluteVec(new Vec3(4.5, 2, z));
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
            // 0.51.0 마검사의 2차 전직 (뵐룬드(룬 나이트) · 투르스(헥스 블레이드)) — 다른 1차 직업 (300) 으로 바꾼 뒤 처음이라 무료
            Wallet.set(sp, Economy.Kind.SILVER, 300);
            JobTrainer.open(sp, () -> done[0]++);
            Forms.answer(sp, JobTrainer.slotOf("spellblade"), null);
            Forms.answer(sp, 11, null);
            h.assertTrue("spellblade".equals(JobSystem.jobOf(sp)), "changed to spellblade: " + JobSystem.jobOf(sp));
            JobTrainer.open(sp, () -> done[0]++);
            FormNet.ChestPayload sb = chest(sp);
            h.assertTrue(sb != null && sb.slots().get(JobTrainer.ADV_SLOT).kind() != 3, "spellblade: 2차 전직 open");
            Forms.answer(sp, JobTrainer.ADV_SLOT, null);
            FormNet.ChestPayload sadv = chest(sp);
            h.assertTrue(sadv != null && sadv.slots().get(JobTrainer.AWAKEN_SLOT).name().contains("뵐룬드(룬 나이트)")
                    && sadv.slots().get(JobTrainer.INVERT_SLOT).name().contains("투르스(헥스 블레이드)"), "spellblade advance cards");
            Forms.answer(sp, JobTrainer.AWAKEN_SLOT, null);
            h.assertTrue(chest(sp) != null && chest(sp).title().contains("2차 전직 확인"), "spellblade advance confirm");
            Forms.answer(sp, 11, null);
            h.assertTrue("runeknight".equals(JobSystem.jobOf(sp)) && Wallet.get(sp, Economy.Kind.SILVER) == 0 && JobSystem.job(sp).display().equals("뵐룬드(룬 나이트)"),
                    "runeknight free from the first job: " + JobSystem.jobOf(sp));
            // 0.51.0 매지션의 2차 전직 (오드레리르(아크메이지) · 나글파르(네크로맨서)) — 다른 1차 직업 (300) 으로 바꾼 뒤 처음이라 무료
            Wallet.set(sp, Economy.Kind.SILVER, 300);
            JobTrainer.open(sp, () -> done[0]++);
            Forms.answer(sp, JobTrainer.slotOf("mage"), null);
            Forms.answer(sp, 11, null);
            h.assertTrue("mage".equals(JobSystem.jobOf(sp)), "changed to mage: " + JobSystem.jobOf(sp));
            JobTrainer.open(sp, () -> done[0]++);
            FormNet.ChestPayload mg = chest(sp);
            h.assertTrue(mg != null && mg.slots().get(JobTrainer.ADV_SLOT).kind() != 3, "mage: 2차 전직 open");
            Forms.answer(sp, JobTrainer.ADV_SLOT, null);
            FormNet.ChestPayload madv = chest(sp);
            h.assertTrue(madv != null && madv.slots().get(JobTrainer.AWAKEN_SLOT).name().contains("오드레리르(아크메이지)")
                    && madv.slots().get(JobTrainer.INVERT_SLOT).name().contains("나글파르(네크로맨서)"), "mage advance cards");
            Forms.answer(sp, JobTrainer.INVERT_SLOT, null);
            h.assertTrue(chest(sp) != null && chest(sp).title().contains("2차 전직 확인"), "mage advance confirm");
            Forms.answer(sp, 11, null);
            h.assertTrue("necromancer".equals(JobSystem.jobOf(sp)) && Wallet.get(sp, Economy.Kind.SILVER) == 0 && JobSystem.job(sp).display().equals("나글파르(네크로맨서)"),
                    "necromancer free from the first job: " + JobSystem.jobOf(sp));
            // 0.51.0 궁수의 2차 전직 (에길(스나이퍼) · 미르크비드(섀도 헌터)) — 다른 1차 직업 (300) 으로 바꾼 뒤 처음이라 무료
            Wallet.set(sp, Economy.Kind.SILVER, 300);
            JobTrainer.open(sp, () -> done[0]++);
            Forms.answer(sp, JobTrainer.slotOf("archer"), null);
            Forms.answer(sp, 11, null);
            h.assertTrue("archer".equals(JobSystem.jobOf(sp)), "changed to archer: " + JobSystem.jobOf(sp));
            JobTrainer.open(sp, () -> done[0]++);
            FormNet.ChestPayload ar = chest(sp);
            h.assertTrue(ar != null && ar.slots().get(JobTrainer.ADV_SLOT).kind() != 3, "archer: 2차 전직 open");
            Forms.answer(sp, JobTrainer.ADV_SLOT, null);
            FormNet.ChestPayload aadv = chest(sp);
            h.assertTrue(aadv != null && aadv.slots().get(JobTrainer.AWAKEN_SLOT).name().contains("에길(스나이퍼)")
                    && aadv.slots().get(JobTrainer.INVERT_SLOT).name().contains("미르크비드(섀도 헌터)"), "archer advance cards");
            Forms.answer(sp, JobTrainer.AWAKEN_SLOT, null);
            h.assertTrue(chest(sp) != null && chest(sp).title().contains("2차 전직 확인"), "archer advance confirm");
            Forms.answer(sp, 11, null);
            h.assertTrue("sniper".equals(JobSystem.jobOf(sp)) && Wallet.get(sp, Economy.Kind.SILVER) == 0 && JobSystem.job(sp).display().equals("에길(스나이퍼)"),
                    "sniper free from the first job: " + JobSystem.jobOf(sp));
            // 암살자의 2차 전직은 아직 준비 중 (다른 1차 직업 — 300 · 2차가 풀림)
            Wallet.set(sp, Economy.Kind.SILVER, 300);
            JobTrainer.open(sp, () -> done[0]++);
            Forms.answer(sp, JobTrainer.slotOf("assassin"), null);
            Forms.answer(sp, 11, null);
            h.assertTrue("assassin".equals(JobSystem.jobOf(sp)), "changed to assassin: " + JobSystem.jobOf(sp));
            JobTrainer.open(sp, () -> done[0]++);
            FormNet.ChestPayload as = chest(sp);
            h.assertTrue(as != null && as.slots().get(JobTrainer.ADV_SLOT).kind() == 3, "assassin: 2차 전직 dim (준비 중)");
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

    // ------------------------------------------------------------ 뵐룬드(룬 나이트)

    @GameTest(environment = ENV_RK, maxTicks = 480)
    public void runeknightSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = RpgJobTests.jobPlayer(h, "runeknight", Map.of("int", 30, "str", 20), 50);
        Jobs.Job j = Jobs.job("runeknight");
        Weapon w = Weapons.weaponFrom("sword", 0, "common");
        double spread = j.trait().def().numOr("markBurst", 1.0) * j.trait().def().numOr("spread", 0.5);
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 0, 3)));
        List<RpgJobTests.Step> steps = new ArrayList<>();
        double[] mark = {0, 0};
        // 룬 새김 베기: 미끄러지며 세 번 (3.6 — 한 번에 넣고 숫자 셋) → 16틱에 찔러 글자 셋이 터짐 (4.9) = 8.5 · 각인 1 (스킬마다 적마다 한 번)
        steps.add(new RpgJobTests.Step(1, () -> {
            home(h, sp);
            mark[0] = sp.position().z;
            h.assertTrue(JobSkills.press(sp, 0), "룬 새김 베기 cast");
        }));
        steps.add(new RpgJobTests.Step(24, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(0), 8.5);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "룬 새김 베기 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits.marks(sp, z.get(0)) == 1, "one mark per cast: " + JobTraits.marks(sp, z.get(0)));
            h.assertTrue(sp.position().z - mark[0] > 1.5, "slid forward: " + (sp.position().z - mark[0]));
            h.assertTrue(RpgJobTests.gradientNumber(h, z.get(0), j), "runeknight gradient number");
            // 과녁 곁 (1.5칸 앞) 에 서고 · 각인 찍힌 과녁 뒤 2.2칸에 다른 과녁 (번짐을 받음 — 평타는 닿지 않는 거리)
            homeAt(h, sp, 3.0);
            z.add(dummy(h, 0, 5.2));
        }));
        // 평타가 각인 하나를 터뜨림 (위력 × 1.0) → 둘레 3칸의 다른 적에게 룬 폭발이 번짐 (위력 × 1.0 × 0.5)
        steps.add(new RpgJobTests.Step(4, () -> {
            RpgCombat.forceCrit = false;
            double before = TestDummies.dealt(z.get(0));
            double base = RpgJobTests.basic(sp, w);
            sp.attack(z.get(0));
            RpgCombat.forceCrit = null;
            double unit = JobSystem.power(sp, j) * StatTrees.atkMult(sp) * RpgCombat.dealtMult.applyAsDouble(sp);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)) - before, base + unit), "basic + mark burst " + (TestDummies.dealt(z.get(0)) - before) + " vs "
                    + (base + unit));
            h.assertTrue(JobTraits.marks(sp, z.get(0)) == 0, "mark used");
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(1)), unit * spread), "rune burst spreads " + TestDummies.dealt(z.get(1)) + " vs " + unit * spread);
        }));
        // 세이드 섬광: 보는 쪽으로 순간 이동 (길 1.2 — 시험 자리는 8칸이라 barrier 벽 앞 약 5.9칸) → 10틱에 되돌아옴 (길 1.2) · 닿은 자리 원 (1.35) → 20틱에 처음 자리
        // 원 (1.35) — 처음 자리 곁 과녁 (2칸) · 닿은 자리 곁 과녁 (4.5칸) 모두 1.2 + 1.2 + 1.35 (두 원은 멀어 한 과녁이 둘 다 맞지 않음)
        steps.add(new RpgJobTests.Step(12, () -> {
            for (Husk x : z) x.discard();
            z.clear();
            home(h, sp);
            z.add(dummy(h, 0, 2));
            z.add(dummy(h, 0, 4.5));
            mark[0] = sp.position().z;
            h.assertTrue(JobSkills.press(sp, 1), "세이드 섬광 cast");
            h.assertTrue(sp.position().z - mark[0] > 5, "blinked ahead: " + (sp.position().z - mark[0]));
        }));
        steps.add(new RpgJobTests.Step(14, () -> h.assertTrue(Math.abs(sp.position().z - mark[0]) < 0.6, "returned to the start: " + (sp.position().z - mark[0]))));
        steps.add(new RpgJobTests.Step(30, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(1), 1.2 + 1.2 + 1.35);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "세이드 섬광 (start circle) " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(1)), want), "세이드 섬광 (landing circle) " + TestDummies.dealt(z.get(1)) + " vs " + want);
            for (Husk x : z) x.discard();
            z.clear();
            home(h, sp);
            z.add(dummy(h, 0, 2.5));
            mark[1] = sp.getY();
            h.assertTrue(JobSkills.press(sp, 2), "푸타르크 대검무 cast");
            h.assertTrue(Math.abs(Guards.of(sp).reduce() - 0.2) < 1e-9, "rune ward 20%: " + Guards.of(sp).reduce());
        }));
        // 푸타르크 대검무: 2칸 떠올라 2.4 × 4 (0 · 15 · 30 · 45) → 60 에 내려앉으며 룬 원 폭발 5.7 = 15.3
        steps.add(new RpgJobTests.Step(20, () -> h.assertTrue(sp.getY() - mark[1] > 1.2, "lifted during the sword dance: " + (sp.getY() - mark[1]))));
        steps.add(new RpgJobTests.Step(56, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(2), 15.3);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "푸타르크 대검무 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(Math.abs(sp.getY() - mark[1]) < 0.3, "landed: y " + sp.getY() + " vs " + mark[1]);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2));
            double r0 = sp.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
            h.assertTrue(JobSkills.press(sp, 3), "룬 담금질 cast");
            double r1 = sp.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
            h.assertTrue(JobTraits.stanceOn(sp, "runeknight_temper") && Math.abs(JobTraits.reachBonus(sp) - 1.5) < 1e-9 && Math.abs(r1 - r0 - 1.5) < 1e-9,
                    "temper stance · reach +1.5: " + JobTraits.reachBonus(sp) + ", " + r0 + " -> " + r1);
            h.assertTrue("runeknight_temper".equals(mmorpg.fabric.motion.SwingLook.testKey(sp)), "rune greatsword swing look: " + mmorpg.fabric.motion.SwingLook.testKey(sp));
        }));
        // 룬 담금질: 칼 평타 = 평타 + 마력 검 셋 (위력 × 0.37 × 3)
        steps.add(new RpgJobTests.Step(12, () -> {
            RpgCombat.forceCrit = false;
            double base = RpgJobTests.basic(sp, w);
            sp.attack(z.get(0));
            RpgCombat.forceCrit = null;
            double echo = JobSystem.power(sp, j) * 0.37 * 3 * StatTrees.atkMult(sp) * RpgCombat.dealtMult.applyAsDouble(sp);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), base + echo), "temper swords " + TestDummies.dealt(z.get(0)) + " vs " + (base + echo));
        }));
        // 스물네 룬의 대검: 6칸 솟아 10 × 3 (첫 베기에 각인 +1) → 검이 앞 6칸에 내리꽂힘 14 → 검과 함께 내리꽂혀 24 + 각인 1 (위력 × 1.0) = 69
        steps.add(new RpgJobTests.Step(12, () -> {
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 4));
            mark[0] = sp.position().z;
            mark[1] = sp.getY();
            h.assertTrue(JobSkills.press(sp, 4), "스물네 룬의 대검 cast");
        }));
        steps.add(new RpgJobTests.Step(30, () -> h.assertTrue(sp.getY() - mark[1] > 1.5, "rose with the giant sword: " + (sp.getY() - mark[1]))));
        steps.add(new RpgJobTests.Step(56, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(4), 68 + 1.0);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "스물네 룬의 대검 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits.marks(sp, z.get(0)) == 0, "marks detonated");
            h.assertTrue(Math.abs(sp.getY() - mark[1]) < 0.3 && sp.position().z - mark[0] > 1.5, "dove forward to the ground: y " + sp.getY() + " vs " + mark[1]
                    + " · moved " + (sp.position().z - mark[0]));
        }));
        RpgJobTests.chain(h, steps, 0, () -> {
            h.assertTrue(JobTraits.stanceOn(sp, "runeknight_temper") && JobTraits.reachBonus(sp) == 1.5, "temper still on at the end");
            RpgJobTests.cleanup(sp);
            h.assertTrue(JobTraits.reachBonus(sp) == 0, "reach restored on job change");
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }

    // ------------------------------------------------------------ 투르스(헥스 블레이드)

    @GameTest(environment = ENV_HX, maxTicks = 480)
    public void hexbladeSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = RpgJobTests.jobPlayer(h, "hexblade", Map.of("int", 30, "str", 20), 50);
        Jobs.Job j = Jobs.job("hexblade");
        Weapon w = Weapons.weaponFrom("sword", 0, "common");
        double per = j.trait().def().numOr("perRune", 0.12);
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 0, 2.5)));
        List<RpgJobTests.Step> steps = new ArrayList<>();
        double[] mark = {0, 0};
        // 뒤집힌 룬: 칼 평타가 맞힌 적에 저주 룬 +1 (평타 피해는 그대로)
        steps.add(new RpgJobTests.Step(1, () -> {
            home(h, sp);
            RpgCombat.forceCrit = false;
            double base = RpgJobTests.basic(sp, w);
            sp.attack(z.get(0));
            RpgCombat.forceCrit = null;
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), base), "basic unchanged " + TestDummies.dealt(z.get(0)) + " vs " + base);
            h.assertTrue(JobTraits7.runesOf(sp, z.get(0)) == 1, "curse rune +1: " + JobTraits7.runesOf(sp, z.get(0)));
        }));
        // 가시 룬 베기: 룬 3 → 올려 벰 3.0 × (1 + 0.12 × 3) (들어가면 룬을 모두 터뜨림) → 내려 벰 5.5 · 가시 룬 +2
        steps.add(new RpgJobTests.Step(12, () -> {
            JobTraits7.setRunes(sp, z.get(0), 3);
            mark[0] = TestDummies.dealt(z.get(0));
            h.assertTrue(JobSkills.press(sp, 0), "가시 룬 베기 cast");
        }));
        steps.add(new RpgJobTests.Step(24, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(0), 3.0 * (1 + per * 3) + 5.5);
            double got = TestDummies.dealt(z.get(0)) - mark[0];
            h.assertTrue(RpgJobTests.near(got, want), "가시 룬 베기 (3 runes) " + got + " vs " + want);
            h.assertTrue(JobTraits7.runesOf(sp, z.get(0)) == 2, "thorn runes +2 after the detonation: " + JobTraits7.runesOf(sp, z.get(0)));
            h.assertTrue(RpgJobTests.gradientNumber(h, z.get(0), j), "hexblade gradient number");
            // 뒤집힌 걸음: 뒤로 물러날 자리가 있게 (4.5 — 시험 자리 8칸, 뒤 barrier 벽 앞까지 약 3.9칸) · 과녁은 앞 2칸 · 룬 5
            z.get(0).discard();
            homeAt(h, sp, 4.5);
            z.set(0, dummy(h, 0, 5.0));
            JobTraits7.setRunes(sp, z.get(0), 5);
            mark[0] = sp.position().z;
            h.assertTrue(JobSkills.press(sp, 1), "뒤집힌 걸음 cast");
            h.assertTrue(mark[0] - sp.position().z > 3, "stepped back: " + (mark[0] - sp.position().z));
        }));
        // 뒤집힌 걸음: 떠난 자리 덫 (10틱) 5.1 × (1 + 0.12 × 5) — 룬 다섯이 터져 묶음
        steps.add(new RpgJobTests.Step(16, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(1), 5.1 * (1 + per * 5));
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "뒤집힌 걸음 (5 runes) " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits7.runesOf(sp, z.get(0)) == 0 && z.get(0).hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS), "runes detonated · rooted");
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2.5));
            h.assertTrue(JobSkills.press(sp, 2), "우박의 룬 cast");
        }));
        // 우박의 룬: 첫 우박 2.2 → 맞은 적 받는 피해 +10% (5초) → 2.2 × 3 · 칼을 뽑아 깨짐 6.5 가 × 1.1
        steps.add(new RpgJobTests.Step(10, () -> h.assertTrue(Math.abs(JobTraitsMore.breakMult(z.get(0)) - 1.1) < 1e-9, "hail vulnerability × 1.1: "
                + JobTraitsMore.breakMult(z.get(0)))));
        steps.add(new RpgJobTests.Step(48, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(2), 2.2 + (2.2 * 3 + 6.5) * 1.1);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "우박의 룬 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2));
            h.assertTrue(JobSkills.press(sp, 3), "저주의 칼 cast");
            h.assertTrue(JobTraits.stanceOn(sp, "hexblade_curse") && "hexblade_curse".equals(mmorpg.fabric.motion.SwingLook.testKey(sp)), "curse stance · cursed blade look: "
                    + mmorpg.fabric.motion.SwingLook.testKey(sp));
        }));
        // 저주의 칼: 칼 평타 + 저주 불꽃 (위력 × 0.5) · 저주 룬 +2
        steps.add(new RpgJobTests.Step(12, () -> {
            RpgCombat.forceCrit = false;
            double base = RpgJobTests.basic(sp, w);
            sp.attack(z.get(0));
            RpgCombat.forceCrit = null;
            double flame = JobSystem.power(sp, j) * 0.5 * StatTrees.atkMult(sp) * RpgCombat.dealtMult.applyAsDouble(sp);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), base + flame), "curse flame " + TestDummies.dealt(z.get(0)) + " vs " + (base + flame));
            h.assertTrue(JobTraits7.runesOf(sp, z.get(0)) == 2, "cursed basic +2 runes: " + JobTraits7.runesOf(sp, z.get(0)));
        }));
        // 재앙의 룬: 앞 5칸 줄기 위 과녁 — 줄기 획 10 (6틱) · 셋째 획 10 (26틱 — 가시 끝이 줄기로 돌아옴, 둘째 획은 오른쪽 가시라 닿지 않음) → 34 ~ 46 뛰어올라 가르기 12 →
        // 56 저주가 터짐 26 = 58
        steps.add(new RpgJobTests.Step(12, () -> {
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 5));
            mark[1] = sp.getY();
            h.assertTrue(JobSkills.press(sp, 4), "재앙의 룬 cast");
        }));
        steps.add(new RpgJobTests.Step(40, () -> h.assertTrue(sp.getY() - mark[1] > 1.5, "leapt to cleave: " + (sp.getY() - mark[1]))));
        steps.add(new RpgJobTests.Step(32, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(4), 10 + 10 + 12 + 26);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "재앙의 룬 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(Math.abs(sp.getY() - mark[1]) < 0.3, "landed: y " + sp.getY() + " vs " + mark[1]);
        }));
        RpgJobTests.chain(h, steps, 0, () -> {
            JobTraits7.setRunes(sp, z.get(0), 3);
            RpgJobTests.cleanup(sp);
            h.assertTrue(JobTraits7.runesOf(sp, z.get(0)) == 0, "runes cleared on job change");
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }

    // ------------------------------------------------------------ 오드레리르(아크메이지)

    /** 직업 스킬이 아닌 특성 피해 (별자리 · 별똥 — 피해 배율 없이 위력 × 배율 × 스탯 트리 공격력 × 가호) */
    static double raw(ServerPlayer sp, Jobs.Job j, double mult) {
        return JobSystem.power(sp, j) * mult * StatTrees.atkMult(sp) * RpgCombat.dealtMult.applyAsDouble(sp);
    }

    @GameTest(environment = ENV_AM, maxTicks = 620)
    public void arcmageSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = RpgJobTests.jobPlayer(h, "arcmage", Map.of("int", 50), 50);
        Weapon w = Weapons.weaponFrom("wand", 0, "common");
        RpgJobTests.hold(sp, w);
        Jobs.Job j = Jobs.job("arcmage");
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 0, 3)));
        List<RpgJobTests.Step> steps = new ArrayList<>();
        double[] mark = {0, 0};
        // 별무리 마탄: 겨눈 과녁 1.4 × 5 (한 번에 · 숫자 다섯 줄) · 둘레 5칸의 다른 과녁에게 별빛 0.7 × 5 (하나뿐이라 다섯 모두)
        steps.add(new RpgJobTests.Step(1, () -> {
            home(h, sp);
            z.add(dummy(h, 2, 4));
            RpgJobTests.clearStrays(h, z.toArray(new net.minecraft.world.entity.Entity[0])); // 튀는 별빛 · 별의 비는 둘레의 적에게 나뉨 — 앞 시험이 남긴 몹이 받지 않게
            JobTraitsMore.setChain(sp, 0);
            h.assertTrue(JobSkills.press(sp, 0), "별무리 마탄 cast");
        }));
        steps.add(new RpgJobTests.Step(24, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(0), 7);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "별무리 마탄 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            double bounce = RpgJobTests.expected(sp, j, j.skill(0), 0.7 * 5);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(1)), bounce), "starlight bounce " + TestDummies.dealt(z.get(1)) + " vs " + bounce);
            h.assertTrue(RpgJobTests.gradientNumber(h, z.get(0), j), "arcmage gradient number");
            // 서리 별 걸음: 북쪽을 보고 서서 뒤 (남쪽) 로 — 첫 자리 곁 과녁 (2.8) · 둘째 자리 곁 과녁 (1.4)
            for (Husk x : z) x.discard();
            z.clear();
            home(h, sp);
            sp.snapTo(sp.getX(), sp.getY(), sp.getZ(), 180F, 0F);
            sp.setYHeadRot(180F);
            z.add(dummy(h, -2, -1.0));
            z.add(dummy(h, 2.5, 5.0));
            mark[0] = sp.position().z;
            JobTraitsMore.setChain(sp, 0);
            h.assertTrue(JobSkills.press(sp, 1), "서리 별 걸음 cast");
            h.assertTrue(sp.position().z - mark[0] > 2.5, "blinked back: " + (sp.position().z - mark[0]));
        }));
        steps.add(new RpgJobTests.Step(36, () -> {
            double a = RpgJobTests.expected(sp, j, j.skill(1), 2.8);
            double b = RpgJobTests.expected(sp, j, j.skill(1), 1.4);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), a), "first afterimage " + TestDummies.dealt(z.get(0)) + " vs " + a);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(1)), b), "second afterimage " + TestDummies.dealt(z.get(1)) + " vs " + b);
            // 쌍둥이 별: 1.4 × 6 + 4.2
            for (Husk x : z) x.discard();
            z.clear();
            home(h, sp);
            z.add(dummy(h, 0, 4));
            JobTraitsMore.setChain(sp, 0);
            h.assertTrue(JobSkills.press(sp, 2), "쌍둥이 별 cast");
        }));
        steps.add(new RpgJobTests.Step(80, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(2), 12.6);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "쌍둥이 별 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            // 꿀술의 노래 → 별무리 마탄 (연쇄 없이): 마탄 7 + 그 자리 별똥 (위력 × 2.4 — 0.6초 뒤)
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 3));
            JobSkills.resetCooldowns(sp);
            h.assertTrue(JobSkills.press(sp, 3), "꿀술의 노래 cast");
            h.assertTrue(JobTraits.stanceOn(sp, "arcmage_song"), "song stance");
        }));
        // 노래 (1칸 떠올라 1초 · 38틱에 내려앉음) 가 끝난 뒤 — 태세 (10초) 는 그대로
        steps.add(new RpgJobTests.Step(40, () -> {
            h.assertTrue(JobTraits.stanceOn(sp, "arcmage_song"), "song stance lasts");
            JobTraitsMore.setChain(sp, 0);
            mark[0] = TestDummies.dealt(z.get(0));
            h.assertTrue(JobSkills.press(sp, 0), "별무리 마탄 (song) cast");
        }));
        steps.add(new RpgJobTests.Step(30, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(0), 7) + raw(sp, j, 2.4);
            double got = TestDummies.dealt(z.get(0)) - mark[0];
            h.assertTrue(RpgJobTests.near(got, want), "bolts + meteor " + got + " vs " + want);
            // 별자리 완성: 연쇄 4 · 별 넷 (과녁 둘레) → 다섯째 (마탄 — 연쇄 5 +60%) → 0.5초 뒤 별 다섯 × 2 + 선 × 1 → 연쇄 · 별은 처음부터
            JobTraits.testEndStance(sp);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 3));
            JobSkills.resetCooldowns(sp);
            JobTraitsMore.setChain(sp, 4);
            JobTraits8.testStars(sp, 4);
            h.assertTrue(JobTraits8.stars(sp) == 4, "four stars: " + JobTraits8.stars(sp));
            h.assertTrue(JobSkills.press(sp, 0), "별무리 마탄 (chain 5) cast");
            h.assertTrue(JobTraitsMore.chain(sp) == 0 && JobTraits8.stars(sp) == 0, "constellation → chain reset: chain " + JobTraitsMore.chain(sp) + " stars "
                    + JobTraits8.stars(sp));
        }));
        steps.add(new RpgJobTests.Step(36, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(0), 7) * 1.6 + raw(sp, j, 2.0 * 5 + 1.0);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "bolts (chain 5) + constellation " + TestDummies.dealt(z.get(0)) + " vs " + want);
            // 완드 평타가 맞으면 직업 스킬 재사용 대기 −6틱 (매지션과 같음 — 마탄의 미끄러짐으로 옆에 섰으니 시험 자리로)
            home(h, sp);
            mark[0] = JobSkills.cooldownLeft(sp, "arcmage_bolts");
            mark[1] = mmorpg.fabric.Tasks.now();
            RpgCombat.forceCrit = false;
            RpgCombat.fireRanged(sp, sp.getMainHandItem(), w, z.get(0));
        }));
        steps.add(new RpgJobTests.Step(16, () -> {
            RpgCombat.forceCrit = null;
            long left = JobSkills.cooldownLeft(sp, "arcmage_bolts");
            long el = mmorpg.fabric.Tasks.now() - (long) mark[1];
            h.assertTrue(left <= (long) mark[0] - el - 6 + 1, "wand hit cuts job cooldowns: " + mark[0] + " - " + el + " -> " + left);
            // 별의 노래: 3 × 9 + 1.4 × 10 + 15 = 56 (3칸 떠오름)
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 5));
            RpgJobTests.clearStrays(h, z.get(0));
            JobSkills.resetCooldowns(sp);
            JobTraitsMore.setChain(sp, 0);
            mark[1] = sp.getY();
            h.assertTrue(JobSkills.press(sp, 4), "별의 노래 cast");
        }));
        steps.add(new RpgJobTests.Step(30, () -> h.assertTrue(sp.getY() - mark[1] > 1.0, "floated: " + (sp.getY() - mark[1]))));
        steps.add(new RpgJobTests.Step(64, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(4), 56);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "별의 노래 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(Math.abs(sp.getY() - mark[1]) < 0.3, "landed: y " + sp.getY() + " vs " + mark[1]);
        }));
        RpgJobTests.chain(h, steps, 0, () -> {
            RpgJobTests.cleanup(sp);
            h.assertTrue(JobTraits8.stars(sp) == 0, "stars cleared on job change");
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }

    // ------------------------------------------------------------ 나글파르(네크로맨서)

    @GameTest(environment = ENV_NC, maxTicks = 760)
    public void necromancerSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = RpgJobTests.jobPlayer(h, "necromancer", Map.of("int", 50), 50);
        Weapon w = Weapons.weaponFrom("wand", 0, "common");
        RpgJobTests.hold(sp, w);
        Jobs.Job j = Jobs.job("necromancer");
        NecroCrew.PASSIVE.add(sp.getUUID());
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 0, 3)));
        RpgJobTests.clearStrays(h, z.get(0)); // 망자가 노리는 적 · 선원이 덮칠 적이 앞 시험이 남긴 몹이 되지 않게
        List<RpgJobTests.Step> steps = new ArrayList<>();
        double[] mark = {0, 0, 0};
        Husk[] victim = {null};
        Vec3[] pos = {null};
        // 뼈 창: 꿰뚫음 4.9 (창끝이 닿는 틱) · 1.2초에 뼈 가시 2.1 · 맞은 적에 표식
        steps.add(new RpgJobTests.Step(1, () -> {
            home(h, sp);
            h.assertTrue(JobSkills.press(sp, 0), "뼈 창 cast");
        }));
        steps.add(new RpgJobTests.Step(32, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(0), 7);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "뼈 창 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits8.marked(sp, z.get(0)), "bone spear death mark");
            h.assertTrue(RpgJobTests.gradientNumber(h, z.get(0), j), "necromancer gradient number");
            // 망자의 군대: 쓰러질 몹 (과녁이 아님 — 체력 1) 을 뼈 창으로 쓰러뜨림 → 그 자리에서 망자 선원이 일어남
            z.get(0).discard();
            Husk v = h.spawnWithNoFreeWill(EntityTypes.HUSK, 4.5F, 2F, 4.5F);
            v.setHealth(1F);
            victim[0] = v;
            JobSkills.resetCooldowns(sp);
            home(h, sp);
            h.assertTrue(JobSkills.press(sp, 0), "뼈 창 (kill) cast");
        }));
        steps.add(new RpgJobTests.Step(30, () -> {
            h.assertTrue(victim[0].isRemoved() || !victim[0].isAlive(), "victim fell");
            h.assertTrue(NecroCrew.count(sp) == 1 && NecroCrew.bodies(sp).size() == 1, "a crew rose: " + NecroCrew.count(sp));
            var x = NecroCrew.bodies(sp).get(0);
            h.assertTrue(x.entityTags().contains(mmorpg.fabric.mob.TyrfingWarrior.TAG) && x.entityTags().contains("naglfar_crew")
                    && !mmorpg.fabric.skill.Kit.canHit(sp, x), "crew: ally tags · not a skill target " + x.entityTags());
            // 망자가 침: 내가 뼈 창으로 친 과녁 (망자가 먼저 노리는 적) 에게 1.5초마다 위력 × 0.4 (내 직업 스킬 피해 — 숫자는 직업 색)
            NecroCrew.PASSIVE.remove(sp.getUUID());
            z.set(0, dummy(h, 1.0, 4.0));
            RpgJobTests.clearStrays(h, z.get(0));
            JobSkills.resetCooldowns(sp);
            sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.get(0).getEyePosition()); // 비스듬한 과녁을 겨눔 (조준 보정 12° 밖)
            h.assertTrue(JobSkills.press(sp, 0), "뼈 창 (focus) cast");
        }));
        steps.add(new RpgJobTests.Step(70, () -> {
            NecroCrew.PASSIVE.add(sp.getUUID());
            double one = raw(sp, j, 0.4);
            double got = TestDummies.dealt(z.get(0)) - RpgJobTests.expected(sp, j, j.skill(0), 7);
            double n = got / one;
            h.assertTrue(got > 1e-6 && Math.abs(n - Math.round(n)) < 1e-3 && Math.round(n) >= 1, "crew hits " + got + " = " + n + " × " + one + " — dummy at "
                    + z.get(0).position() + " · " + NecroCrew.testState(sp));
            // 망자 일으키기: 망자 둘이 바로 (모두 셋) · 10초 동안 망자 공격 +30%
            z.get(0).discard();
            home(h, sp);
            h.assertTrue(JobSkills.press(sp, 3), "망자 일으키기 cast");
            h.assertTrue(JobTraits.stanceOn(sp, "necromancer_raise") && NecroCrew.count(sp) == 3, "raise: stance · crew 3: " + NecroCrew.count(sp));
            h.assertTrue(Math.abs(JobTraits8.crewMultOf(sp) - 0.4 * 1.3) < 1e-9, "crew attack +30%: " + JobTraits8.crewMultOf(sp));
        }));
        // 망자와 자리 바꾸기: 망자 (다 솟은 뒤) 와 자리를 바꿈 → 떠난 자리 곁 과녁 2.8 + 1.4
        steps.add(new RpgJobTests.Step(20, () -> {
            home(h, sp);
            pos[0] = sp.position();
            h.assertTrue(NecroCrew.testRaise(sp, h.absoluteVec(new Vec3(5.0, 2, 6.0))) != null, "a crew ahead to swap with");
            h.assertTrue(JobSkills.press(sp, 1), "망자와 자리 바꾸기 cast");
            z.set(0, dummy(h, -1.5, 0.0));
            h.assertTrue(sp.position().distanceTo(pos[0]) > 1.0, "swapped: moved " + sp.position().distanceTo(pos[0]));
            boolean crewAtStart = false;
            for (var x : NecroCrew.bodies(sp)) if (x.position().distanceTo(pos[0]) < 0.6) crewAtStart = true;
            h.assertTrue(crewAtStart, "a crew stands where I was");
        }));
        steps.add(new RpgJobTests.Step(32, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(1), 4.2);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "bone wall " + TestDummies.dealt(z.get(0)) + " vs " + want);
            // 시체 폭발 (시체): 과녁 곁 시체를 터뜨림 → 8.4 + 1.4 × 3 · 망자 수는 그대로
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 4.0));
            RpgJobTests.clearStrays(h, z.get(0));
            JobTraits8.testCorpse(h.getLevel(), z.get(0).position().add(0.8, 0, 0), 200);
            mark[0] = JobTraits8.corpses();
            mark[1] = NecroCrew.count(sp);
            h.assertTrue(JobSkills.press(sp, 2), "시체 폭발 (corpse) cast");
            h.assertTrue(JobTraits8.corpses() == mark[0] - 1 && NecroCrew.count(sp) == mark[1], "corpse used, crew kept: " + JobTraits8.corpses() + " · " + NecroCrew.count(sp));
        }));
        steps.add(new RpgJobTests.Step(46, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(2), 12.6);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "시체 폭발 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            // 시체 폭발 (망자): 시체가 없으면 과녁 곁 망자를 터뜨림 (하나 줄어듦)
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 4.0));
            NecroCrew.testRaise(sp, z.get(0).position().add(1.2, 0, 0));
            mark[1] = NecroCrew.count(sp);
            JobSkills.resetCooldowns(sp);
            h.assertTrue(JobSkills.press(sp, 2), "시체 폭발 (crew) cast");
            h.assertTrue(NecroCrew.count(sp) == mark[1] - 1, "a crew exploded: " + mark[1] + " -> " + NecroCrew.count(sp));
        }));
        steps.add(new RpgJobTests.Step(46, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(2), 12.6);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "시체 폭발 (crew) " + TestDummies.dealt(z.get(0)) + " vs " + want);
            // 나글파르의 출항: 앞 2.5칸 과녁 — 솟는 배 밑 14 · 선원 여섯이 덮침 3 × 6 · 갈라진 땅 24 = 56, 선원 여섯은 6초 더
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2.5));
            RpgJobTests.clearStrays(h, z.get(0));
            mark[2] = NecroCrew.countAll(sp) - NecroCrew.count(sp);
            JobSkills.resetCooldowns(sp);
            h.assertTrue(JobSkills.press(sp, 4), "나글파르의 출항 cast");
        }));
        steps.add(new RpgJobTests.Step(70, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(4), 56);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "나글파르의 출항 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            int ult = NecroCrew.countAll(sp) - NecroCrew.count(sp);
            h.assertTrue(ult - (int) mark[2] == 6, "six ult crew stay: " + ult);
        }));
        RpgJobTests.chain(h, steps, 0, () -> {
            NecroCrew.PASSIVE.remove(sp.getUUID());
            RpgJobTests.cleanup(sp);
            h.assertTrue(NecroCrew.countAll(sp) == 0, "crew disbanded on job change: " + NecroCrew.countAll(sp));
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }

    // ------------------------------------------------------------ 에길(스나이퍼)

    /** 활 평타 한 번 (치명타 없이 · 지금의 평타 배율 — 호흡) */
    static double bowShot(ServerPlayer sp, Weapon w) {
        return w.damage() * RpgCombat.powerOf(sp, w) * RpgCombat.basicMult.applyAsDouble(sp, w);
    }

    @GameTest(environment = ENV_SN, maxTicks = 640)
    public void sniperSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = RpgJobTests.jobPlayer(h, "sniper", Map.of("dex", 50), 50);
        Weapon w = Weapons.weaponFrom("bow", 0, "common");
        RpgJobTests.hold(sp, w);
        Jobs.Job j = Jobs.job("sniper");
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 0, 4), dummy(h, 0, 7)));
        List<RpgJobTests.Step> steps = new ArrayList<>();
        double[] mark = {0, 0};
        // 꿰뚫는 연사: 뒤로 구르며 1.2 × 2 → 무릎 꿇고 1.4 × 2 + 꿰뚫는 화살 2.3 (겨눈 과녁 = 7.5) · 같은 일직선 뒤의 과녁도 꿰뚫음 (2.3)
        steps.add(new RpgJobTests.Step(1, () -> {
            home(h, sp);
            RpgJobTests.clearStrays(h, z.toArray(new net.minecraft.world.entity.Entity[0]));
            JobTraitsMore.setBreath(sp, 0);
            mark[0] = sp.position().z;
            h.assertTrue(JobSkills.press(sp, 0), "꿰뚫는 연사 cast");
        }));
        steps.add(new RpgJobTests.Step(30, () -> {
            double e = RpgJobTests.expected(sp, j, j.skill(0), 1);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), e * 7.5), "꿰뚫는 연사 " + TestDummies.dealt(z.get(0)) + " vs " + e * 7.5);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(1)), e * 2.3), "piercing arrow (behind) " + TestDummies.dealt(z.get(1)) + " vs " + e * 2.3);
            h.assertTrue(RpgJobTests.gradientNumber(h, z.get(0), j), "sniper gradient number");
            h.assertTrue(mark[0] - sp.position().z > 1.5, "rolled back: " + (mark[0] - sp.position().z));
            // 급소 사격: 호흡 8 에서 쏜 활 평타 → 치명타 확정 · 꿰뚫음 (뒤의 과녁도) · 호흡 3 씀 (8 → 5)
            for (Husk x : z) x.discard();
            z.clear();
            home(h, sp);
            z.add(dummy(h, 0, 3));
            z.add(dummy(h, 0, 6));
            JobTraitsMore.setBreath(sp, 8);
            RpgCombat.forceCrit = null;
            RpgCombat.fireRanged(sp, sp.getMainHandItem(), w, z.get(0));
            h.assertTrue(JobTraitsMore.breath(sp) == 5, "snipe uses 3 breath: " + JobTraitsMore.breath(sp));
            mark[0] = bowShot(sp, w) * RpgCombat.critMultOf(sp, w);
        }));
        steps.add(new RpgJobTests.Step(14, () -> {
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), mark[0]), "snipe: sure crit " + TestDummies.dealt(z.get(0)) + " vs " + mark[0]);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(1)), mark[0]), "snipe pierces (crit) " + TestDummies.dealt(z.get(1)) + " vs " + mark[0]);
            // 순풍 공중 사격: 뒤로 5칸 · 2.5칸 높이 → 공중에서 세 발 (각각 활 평타 × 1 — 치명타 없이) · 그동안 호흡이 줄지 않음
            for (Husk x : z) x.discard();
            z.clear();
            homeAt(h, sp, 7.5);
            z.add(dummy(h, 0, 10.0));
            RpgJobTests.clearStrays(h, z.get(0));
            JobTraitsMore.setBreath(sp, 2);
            RpgCombat.forceCrit = false;
            mark[0] = 3 * bowShot(sp, w);
            mark[1] = sp.getY();
            h.assertTrue(JobSkills.press(sp, 1), "순풍 공중 사격 cast");
        }));
        steps.add(new RpgJobTests.Step(7, () -> h.assertTrue(sp.getY() - mark[1] > 1.0, "gale: rose " + (sp.getY() - mark[1]))));
        steps.add(new RpgJobTests.Step(50, () -> {
            RpgCombat.forceCrit = null;
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), mark[0]), "순풍 공중 사격 (3 bow shots) " + TestDummies.dealt(z.get(0)) + " vs " + mark[0]);
            h.assertTrue(JobTraitsMore.breath(sp) == 2, "breath held: " + JobTraitsMore.breath(sp));
            h.assertTrue(Math.abs(sp.getY() - mark[1]) < 0.3, "gale: landed " + sp.getY() + " vs " + mark[1]);
            // 울르의 폭우: 과녁 자리에 화살비 두 번 6.75 × 2 (= 13.5) · 처음 맞은 과녁 1.5초 묶임 (둔화)
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 6));
            RpgJobTests.clearStrays(h, z.get(0));
            h.assertTrue(JobSkills.press(sp, 2), "울르의 폭우 cast");
        }));
        steps.add(new RpgJobTests.Step(20, () -> h.assertTrue(z.get(0).hasEffect(MobEffects.SLOWNESS), "storm: pinned (slowness)")));
        steps.add(new RpgJobTests.Step(26, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(2), 13.5);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "울르의 폭우 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            // 사과 겨누기: 태세 8초 — 호흡 즉시 8 · 급소 사격이 호흡을 쓰지 않음 (치명타 확정)
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 3));
            JobTraitsMore.setBreath(sp, 0);
            h.assertTrue(JobSkills.press(sp, 3), "사과 겨누기 cast");
            h.assertTrue(JobTraits.stanceOn(sp, "sniper_apple") && JobTraitsMore.breath(sp) == 8, "apple: stance · breath 8 — " + JobTraitsMore.breath(sp));
            RpgCombat.forceCrit = null;
            RpgCombat.fireRanged(sp, sp.getMainHandItem(), w, z.get(0));
            h.assertTrue(JobTraitsMore.breath(sp) == 8, "apple: snipe keeps breath: " + JobTraitsMore.breath(sp));
            mark[0] = bowShot(sp, w) * RpgCombat.critMultOf(sp, w);
        }));
        steps.add(new RpgJobTests.Step(14, () -> {
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), mark[0]), "apple snipe " + TestDummies.dealt(z.get(0)) + " vs " + mark[0]);
            // 세 번째 화살: 뒤로 뛰어 공중 두 발 8 × 2 → 내려앉아 셋째 화살 30 → 길의 바람 14 (= 60)
            JobTraits.testEndStance(sp);
            z.get(0).discard();
            homeAt(h, sp, 6.5);
            z.set(0, dummy(h, 0, 11.0));
            RpgJobTests.clearStrays(h, z.get(0));
            JobSkills.resetCooldowns(sp);
            mark[1] = sp.getY();
            h.assertTrue(JobSkills.press(sp, 4), "세 번째 화살 cast");
        }));
        steps.add(new RpgJobTests.Step(14, () -> h.assertTrue(sp.getY() - mark[1] > 1.5, "ult: in the air " + (sp.getY() - mark[1]))));
        steps.add(new RpgJobTests.Step(18, () -> h.assertTrue(Math.abs(sp.getY() - mark[1]) < 0.3, "ult: landed to draw " + sp.getY() + " vs " + mark[1])));
        steps.add(new RpgJobTests.Step(46, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(4), 60);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "세 번째 화살 " + TestDummies.dealt(z.get(0)) + " vs " + want);
        }));
        RpgJobTests.chain(h, steps, 0, () -> {
            RpgJobTests.cleanup(sp);
            h.assertTrue(JobTraitsMore.breath(sp) == 0, "breath cleared on job change: " + JobTraitsMore.breath(sp));
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }

    // ------------------------------------------------------------ 미르크비드(섀도 헌터)

    @GameTest(environment = ENV_SH, maxTicks = 640)
    public void shadowhunterSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = RpgJobTests.jobPlayer(h, "shadowhunter", Map.of("dex", 50), 50);
        Weapon w = Weapons.weaponFrom("bow", 0, "common");
        RpgJobTests.hold(sp, w);
        Jobs.Job j = Jobs.job("shadowhunter");
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 0, 5)));
        List<RpgJobTests.Step> steps = new ArrayList<>();
        double[] mark = {0, 0};
        Husk[] victim = {null};
        // 검은 화살: 옆으로 3칸 달리며 2.5 × 3 (= 7.5 — 한 번에) · 맞은 과녁 저주 +1 · 달리며 쏘아 추격 +1 (이동 +3%)
        steps.add(new RpgJobTests.Step(1, () -> {
            home(h, sp);
            RpgJobTests.clearStrays(h, z.get(0));
            JobTraits9.setPursuit(sp, 0);
            mark[0] = sp.position().x;
            h.assertTrue(JobSkills.press(sp, 0), "검은 화살 cast");
            h.assertTrue(JobTraits9.pursuit(sp) == 1 && Math.abs(JobTraits9.speedBonus(sp) - 0.03) < 1e-9, "pursuit 1 → speed +3%: " + JobTraits9.pursuit(sp) + " · "
                    + JobTraits9.speedBonus(sp));
        }));
        steps.add(new RpgJobTests.Step(24, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(0), 7.5);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "검은 화살 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits9.curseOf(z.get(0)) == 1, "black arrow curse +1: " + JobTraits9.curseOf(z.get(0)));
            h.assertTrue(Math.abs(sp.position().x - mark[0]) > 1.5, "ran sideways: " + Math.abs(sp.position().x - mark[0]));
            h.assertTrue(RpgJobTests.gradientNumber(h, z.get(0), j), "shadowhunter gradient number");
            // 그림자 구르기: 옆으로 4칸 (8틱 — 받는 피해 없음) · 한가운데 그림자 화살 (활 평타 × 1.5 — 치명타 없이) · 맞은 과녁 저주 +1 · 추격 +1
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 5));
            JobTraits9.setPursuit(sp, 0);
            RpgCombat.forceCrit = false;
            mark[0] = 1.5 * bowShot(sp, w);
            mark[1] = sp.position().x;
            h.assertTrue(JobSkills.press(sp, 1), "그림자 구르기 cast");
            h.assertTrue(JobTraits9.rolling(sp) && JobTraits9.pursuit(sp) == 1, "rolling · pursuit 1");
            float hp = sp.getHealth();
            float after = RpgHealth.onLoss(sp, sp.damageSources().mobAttack(z.get(0)), hp, hp - 4F);
            h.assertTrue(Math.abs(after - hp) < 1e-4, "no damage while rolling: " + hp + " -> " + after);
        }));
        steps.add(new RpgJobTests.Step(30, () -> {
            RpgCombat.forceCrit = null;
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), mark[0]), "그림자 구르기 (bow × 1.5) " + TestDummies.dealt(z.get(0)) + " vs " + mark[0]);
            h.assertTrue(JobTraits9.curseOf(z.get(0)) == 1, "roll curse +1 (bow hit): " + JobTraits9.curseOf(z.get(0)));
            h.assertTrue(!JobTraits9.rolling(sp) && Math.abs(sp.position().x - mark[1]) > 2.0, "rolled sideways: " + Math.abs(sp.position().x - mark[1]));
            float hp = sp.getHealth();
            h.assertTrue(RpgHealth.onLoss(sp, sp.damageSources().mobAttack(z.get(0)), hp, hp - 4F) < hp, "damage again after the roll");
            // 저주 번짐: 꿰뚫는 저주 화살 5.5 (저주 +1) → 1.8초에 문양이 터짐 8 (저주 1 → × 1.02) · 둘레 3.5칸의 다른 과녁에게 번짐 4 (저주 +1)
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 5));
            z.add(dummy(h, 2.5, 5.5));
            RpgJobTests.clearStrays(h, z.toArray(new net.minecraft.world.entity.Entity[0]));
            h.assertTrue(JobSkills.press(sp, 2), "저주 번짐 cast");
        }));
        steps.add(new RpgJobTests.Step(50, () -> {
            double e = RpgJobTests.expected(sp, j, j.skill(2), 1);
            double a = e * 5.5 + e * 8 * 1.02;
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), a), "저주 번짐 (pierce + burst × curse) " + TestDummies.dealt(z.get(0)) + " vs " + a);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(1)), e * 4), "curse spreads " + TestDummies.dealt(z.get(1)) + " vs " + e * 4);
            h.assertTrue(JobTraits9.curseOf(z.get(0)) == 1 && JobTraits9.curseOf(z.get(1)) == 1, "curses 1 · 1: " + JobTraits9.curseOf(z.get(0)) + " · "
                    + JobTraits9.curseOf(z.get(1)));
            // 문양이 터지기 전에 쓰러지면 그 자리에서 바로 번짐 (체력 1 몹 — 과녁이 아님)
            for (Husk x : z) x.discard();
            z.clear();
            home(h, sp);
            Husk v = h.spawnWithNoFreeWill(EntityTypes.HUSK, 4.5F, 2F, 5.5F);
            v.setHealth(1F);
            victim[0] = v;
            z.add(dummy(h, 2.0, 4.5));
            RpgJobTests.clearStrays(h, v, z.get(0));
            JobSkills.resetCooldowns(sp);
            h.assertTrue(JobSkills.press(sp, 2), "저주 번짐 (kill) cast");
        }));
        steps.add(new RpgJobTests.Step(18, () -> {
            double e = RpgJobTests.expected(sp, j, j.skill(2), 1);
            h.assertTrue(victim[0].isRemoved() || !victim[0].isAlive(), "marked one fell");
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), e * 4), "spreads at once on death " + TestDummies.dealt(z.get(0)) + " vs " + e * 4);
            // 그믐밤: 태세 6초 — 활 평타 치명타 +20%p · 추격이 줄지 않음 · 노리던 몹이 나를 놓침 (쓸 때 · 그 뒤로도)
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 2.0, 3.0));
            z.get(0).setTarget(sp);
            JobTraits9.setPursuit(sp, 3);
            mark[0] = RpgCombat.critChanceOf(sp, w, null);
            h.assertTrue(JobSkills.press(sp, 3), "그믐밤 cast");
            double c1 = RpgCombat.critChanceOf(sp, w, null);
            h.assertTrue(JobTraits.stanceOn(sp, "shadowhunter_moonless") && Math.abs(c1 - mark[0] - 0.2) < 1e-9, "moonless: stance · crit +0.2 (" + mark[0] + " -> " + c1 + ")");
            h.assertTrue(z.get(0).getTarget() == null, "a mob lost me on cast");
            z.get(0).setTarget(sp);
        }));
        steps.add(new RpgJobTests.Step(30, () -> {
            h.assertTrue(JobTraits9.pursuit(sp) == 3, "moonless keeps pursuit: " + JobTraits9.pursuit(sp));
            h.assertTrue(z.get(0).getTarget() == null, "a mob lost me again (moonless)");
            // 어둠숲의 사냥: 가시덤불 · 저주 +2 → 저주 걸린 과녁에 검은 화살 6 × 4 → 사냥의 화살 36 (= 60 — 저주 2 → × 1.04)
            JobTraits.testEndStance(sp);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 6));
            RpgJobTests.clearStrays(h, z.get(0));
            JobSkills.resetCooldowns(sp);
            h.assertTrue(JobSkills.press(sp, 4), "어둠숲의 사냥 cast");
        }));
        steps.add(new RpgJobTests.Step(14, () -> h.assertTrue(JobTraits9.curseOf(z.get(0)) == 2, "hunt: curse 2 — " + JobTraits9.curseOf(z.get(0)))));
        steps.add(new RpgJobTests.Step(60, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(4), 60) * 1.04;
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "어둠숲의 사냥 " + TestDummies.dealt(z.get(0)) + " vs " + want);
        }));
        RpgJobTests.chain(h, steps, 0, () -> {
            RpgJobTests.cleanup(sp);
            h.assertTrue(JobTraits9.pursuit(sp) == 0 && JobTraits9.speedBonus(sp) == 0, "pursuit cleared on job change");
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }
}
