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
import mmorpg.fabric.job.JobRecast;
import mmorpg.fabric.job.JobSkills;
import mmorpg.fabric.job.JobSystem;
import mmorpg.fabric.job.JobTrainer;
import mmorpg.fabric.job.JobTraits;
import mmorpg.fabric.job.JobTraits10;
import mmorpg.fabric.job.JobTraits3;
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
 * <li>소드마스터 (mmorpg:job_sm — 재설계안 2판): 과녁 하나가 받은 피해 = 위력 × 피해 배율 × 배율 합 — 칼집 섬광 (투지 3 → 6칸 · 열 줄, 뽑는 6틱에 5) · 받아넘기기
 * (막는 동안 맞으면 깎이지 않고 받아침 3 · 투지 +3 / 막은 것이 없으면 내디뎌 벰 3) · 불의 울타리 (가로지르기 1.8 × 5) · 용의 피 (공격 속도 ×1.35 · 이동 +15% · −20% ·
 * 칼 평타 투지 +2) · 다시 벼린 칼 (충격 8 → 벼린 칼 사거리 +1.5 · +20% → 다시 누름 (14틱부터) 검풍 32 × 잃은 체력)</li>
 * <li>블러드 나이트 (mmorpg:job_bk — 재설계안 2판): 피의 갈증 (체력 4% → 위력 +10% · 체력 30% 에서 공격 속도 ×1.3) · 핏빛 대검 (세 번 다시 누름 — 10틱부터 · 칼끝 ×1.5) ·
 * 피의 돌진 (뛰어들어 3 · 기절 · 방어 깨짐) · 피의 사슬 (박음 3 → 끌어당김 6) · 칼을 뽑다 (칼날 10 · 내리꽂음 10 · X 베기 5 × 4) · 광란 (공격 속도 ×1.6 · 저절로 벰)</li>
 * <li>워로드 (mmorpg:job_wl — 재설계안 2판): 동면 (받는 피해 −60% · 맞으면 분노 · 깨어나며 3.9) · 분노 100 → 곰의 화신 (평타 +20% · −15% · 분노가 줄어듦) ·
 * 대지 가르기 (다시 누름 — 모은 몫만큼 6.5 × (0.5 → 1) · 끝까지 모으면 저절로 6.5) · 곰의 포옹 (양옆을 끌어와 11.7 · 양쪽이면 기절) · 마지막 휘두름 (안쪽 26 ·
 * 바깥 날 52 · 모으는 동안 −50%) · 거인의 무게 (공격 속도 ×0.75 · 도끼 평타 ×1.4 · 범위 모습)</li>
 * <li>글래디에이터 (mmorpg:job_gl — 재설계안 2판): 결투 (처음 맞힌 적 · 다른 적 −40% · 상대 +3% × 결투 · 1초마다 +1 · 상대가 쓰러지면 가까운 적에게 반) ·
 * 도끼 난타 (6.5) · 걸어 당기기 (끌어와 3.9 · 기절) · 여덟 팔 (11.7 · 받는 피해 +10%) · 불사의 분노 (쓰러지지 않음 · 공격 속도 ×1.4) · 홀름강 (결투장 — 다른 적은
 * 밀려남 · 받는 피해 +15% · 12 → 체력 30% 아래면 40 × 2)</li>
 * <li>룬 스미스 (mmorpg:job_rk — 재설계안 2판): 룬 모루 (갈라짐 3.4 → 모루가 솟아 터짐 5.1 · 각인 1) → 평타가 각인을 터뜨림 (위력 × 1.0) · 둘레로 번짐 (× 0.5) ·
 * 풀무 숨 (1.7 × 3 · 보호막 → 달궈짐) · 용광로 (15.3 + 달궈진 적은 식으며 깨짐 위력 × 0.8 · 기절 → 다시 달궈짐) · 담금질 (사거리 +1 · 평타가 맞힌 적에 쇳조각 → 1초 뒤 위력 × 0.45) ·
 * 쇳물 숫양 (지나감 24 + 쇳물 물결 44 · 달궈짐)</li>
 * <li>소드 콜러 (mmorpg:job_sc — 재설계안 2판): 스스로 싸우는 칼 (허공을 휘두르면 칼이 날아가 평타 그대로 · 이어 벨수록 공격 속도 +20% · 2초면 처음부터) · 베어라
 * (4.0 + 4.5 → 꽂혀 기다림) · 데려가라 (꽂힌 칼로 날아감 5.1) · 꿰뚫고 맴돌아라 (3.3 + 3.0 × 4 · 적이 없으면 쓰지 않음) · 둘로 나뉘어라 (평타 + 위력 × 0.9 · 거리 +4) ·
 * 칼날 감옥 (묶음 · 6 × 6 + 32)</li>
 * <li>아크메이지 (mmorpg:job_am): 별무리 마탄 (1.4 × 5 · 다른 적에게 별빛 0.7 × 5) · 서리 별 걸음 (두 잔상 2.8 · 1.4 — 빙결) · 쌍둥이 별 (1.4 × 6 + 4.2) · 꿀술의 노래
 * (스킬마다 별똥 위력 × 2.4) · 별자리 완성 (연쇄 5 — 별 다섯 × 2 + 선 1 · 연쇄는 처음부터) · 완드 평타 → 재사용 대기 −6틱 · 별의 노래 (3 × 9 + 1.4 × 10 + 15)</li>
 * <li>네크로맨서 (mmorpg:job_nc): 뼈 창 (4.9 + 2.1 · 표식) → 쓰러뜨리면 망자 선원 (rpg:naglfar_crew) 이 일어나 싸움 (위력 × 0.4 — 내 직업 스킬 피해) · 망자 일으키기 (둘 · +30%) ·
 * 망자와 자리 바꾸기 (자리 · 뼈 방벽 2.8 + 1.4) · 시체 폭발 (시체 · 망자를 터뜨림 8.4 + 1.4 × 3) · 나글파르의 출항 (14 + 3 × 6 + 24 · 선원 여섯)</li>
 * <li>스나이퍼 (mmorpg:job_sn): 꿰뚫는 연사 (1.2 × 2 + 1.4 × 2 + 2.3 · 뒤의 과녁 2.3) · 급소 사격 (호흡 8 → 치명타 확정 · 꿰뚫음 · 호흡 3 씀) · 순풍 공중 사격 (뜀 · 활 평타 × 1 세 발 ·
 * 호흡 그대로) · 울르의 폭우 (6.75 × 2 · 묶음) · 사과 겨누기 (호흡 8 · 급소 사격이 호흡을 쓰지 않음) · 세 번째 화살 (8 × 2 + 30 + 14 · 뜸 → 내려앉음)</li>
 * <li>섀도 헌터 (mmorpg:job_sh): 검은 화살 (2.5 × 3 · 저주 +1 · 추격 +1 → 이동 +3%) · 그림자 구르기 (받는 피해 없음 · 활 평타 × 1.5 · 저주 +1) · 저주 번짐 (5.5 → 터짐 8 × 저주 1.02 ·
 * 둘레 4 / 쓰러지면 바로 번짐) · 그믐밤 (치명타 +20%p · 추격이 줄지 않음 · 몹이 나를 놓침) · 어둠숲의 사냥 (저주 +2 → (6 × 4 + 36) × 1.04)</li>
 * <li>트릭스터 (mmorpg:job_tr): 표식 최대 7 · 표식 5 인 몹을 평타로 쓰러뜨리면 (+1) 옆 과녁에게 6 이 옮겨감 · 검은 장미 덤불 (표식 7 → 0.35 + 0.2 + 0.6 × 7 · 머리 위로 넘어 등 뒤 · 과녁을 봄) ·
 * 우트가르드의 환술 (환영 둘이 터짐 1.425 × 2 · 표식 +2) · 요르문간드의 똬리 (가며 2.4 → 조임 3.15 → 독 0.75 × 4 · 처음 자리로) · 끝없는 밤 (첫 평타 × 2.5 치명 · 풀려도 태세 ·
 * 쓰러뜨리면 다시 은신) · 개기일식 (1.25 × 4 × 4 · 표식 7 → 4 + 2 × 7)</li>
 * <li>블레이드 댄서 (mmorpg:job_bd): 춤사위 (평타 → 스킬 → 평타 번갈아 +1 · 하나당 +6% · 이동 +4% · 3초 끊기면 0) · 빛살 베기 (2.0 + 2.75 × 1.06) · 요정의 걸음 (0.95 × 3 ·
 * 받는 피해 −30%) · 칼날 원무 (2.85 × 3) · 눈부신 한낮 (평타 두 번 42% · 춤사위가 줄지 않음) · 빛무리 춤 (2.5 × 8 + 18)</li>
 * </ul>
 */
public final class RpgJobAdvanceTests {
    static final String ENV_ADV = "mmorpg:job_adv";
    static final String ENV_SM = "mmorpg:job_sm";
    static final String ENV_BK = "mmorpg:job_bk";
    static final String ENV_WL = "mmorpg:job_wl";
    static final String ENV_GL = "mmorpg:job_gl";
    static final String ENV_RK = "mmorpg:job_rk";
    static final String ENV_SC = "mmorpg:job_sc";
    static final String ENV_AM = "mmorpg:job_am";
    static final String ENV_NC = "mmorpg:job_nc";
    static final String ENV_SN = "mmorpg:job_sn";
    static final String ENV_SH = "mmorpg:job_sh";
    static final String ENV_TR = "mmorpg:job_tr";
    static final String ENV_BD = "mmorpg:job_bd";

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
            JobUpgrades.set(sp, "swordsman_moon", 2); // 셋째 칸 (원월참) → 2차의 셋째 칸 (불의 울타리 · 피의 사슬)
            Forms.answer(sp, JobTrainer.AWAKEN_SLOT, null);
            h.assertTrue(chest(sp) != null && chest(sp).title().contains("2차 전직 확인"), "advance confirm");
            Forms.answer(sp, 11, null);
            h.assertTrue("swordmaster".equals(JobSystem.jobOf(sp)) && Wallet.get(sp, Economy.Kind.SILVER) == 0 && done[0] == 1, "awaken free: " + JobSystem.jobOf(sp));
            h.assertTrue(JobTrainer.optionText(sp).contains("시구르드(소드마스터)") && JobSystem.job(sp).display().equals("시구르드(소드마스터)"), "display 이명(직업명)");
            h.assertTrue(JobUpgrades.level(sp, "swordmaster_ring") == 2, "upgrade carries to the same slot: " + JobUpgrades.level(sp, "swordmaster_ring"));
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
            h.assertTrue(JobUpgrades.level(sp, "bloodknight_chains") == 2, "upgrade carries (invert)");
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
            // 0.51.0 전사의 2차 전직 (뵈드바르(워로드) · 재설계안 2판 스타르카드(글래디에이터)) — 1차 직업에서 처음이라 무료
            JobTrainer.open(sp, () -> done[0]++);
            FormNet.ChestPayload wa = chest(sp);
            h.assertTrue(wa != null && wa.slots().get(JobTrainer.ADV_SLOT).kind() != 3, "warrior: 2차 전직 open");
            Forms.answer(sp, JobTrainer.ADV_SLOT, null);
            FormNet.ChestPayload wadv = chest(sp);
            h.assertTrue(wadv != null && wadv.slots().get(JobTrainer.AWAKEN_SLOT).name().contains("뵈드바르(워로드)")
                    && wadv.slots().get(JobTrainer.INVERT_SLOT).name().contains("스타르카드(글래디에이터)"), "warrior advance cards");
            Forms.answer(sp, JobTrainer.INVERT_SLOT, null);
            h.assertTrue(chest(sp) != null && chest(sp).title().contains("2차 전직 확인"), "warrior advance confirm");
            Forms.answer(sp, 11, null);
            h.assertTrue("gladiator".equals(JobSystem.jobOf(sp)) && Wallet.get(sp, Economy.Kind.SILVER) == 0
                    && JobSystem.job(sp).display().equals("스타르카드(글래디에이터)"), "gladiator free from the first job: " + JobSystem.jobOf(sp));
            // 0.51.0 마검사의 2차 전직 (뵐룬드(룬 스미스) · 스키르니르(소드 콜러) — 재설계안 2판) — 다른 1차 직업 (300) 으로 바꾼 뒤 처음이라 무료
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
            h.assertTrue(sadv != null && sadv.slots().get(JobTrainer.AWAKEN_SLOT).name().contains("뵐룬드(룬 스미스)")
                    && sadv.slots().get(JobTrainer.INVERT_SLOT).name().contains("스키르니르(소드 콜러)"), "spellblade advance cards");
            Forms.answer(sp, JobTrainer.AWAKEN_SLOT, null);
            h.assertTrue(chest(sp) != null && chest(sp).title().contains("2차 전직 확인"), "spellblade advance confirm");
            Forms.answer(sp, 11, null);
            h.assertTrue("runeknight".equals(JobSystem.jobOf(sp)) && Wallet.get(sp, Economy.Kind.SILVER) == 0 && JobSystem.job(sp).display().equals("뵐룬드(룬 스미스)"),
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
            // 0.51.0 암살자의 2차 전직 (우트가르드(트릭스터) · 료스알프(블레이드 댄서)) — 다른 1차 직업 (300) 으로 바꾼 뒤 처음이라 무료
            Wallet.set(sp, Economy.Kind.SILVER, 300);
            JobTrainer.open(sp, () -> done[0]++);
            Forms.answer(sp, JobTrainer.slotOf("assassin"), null);
            Forms.answer(sp, 11, null);
            h.assertTrue("assassin".equals(JobSystem.jobOf(sp)), "changed to assassin: " + JobSystem.jobOf(sp));
            JobTrainer.open(sp, () -> done[0]++);
            FormNet.ChestPayload as = chest(sp);
            h.assertTrue(as != null && as.slots().get(JobTrainer.ADV_SLOT).kind() != 3, "assassin: 2차 전직 open");
            Forms.answer(sp, JobTrainer.ADV_SLOT, null);
            FormNet.ChestPayload asadv = chest(sp);
            h.assertTrue(asadv != null && asadv.slots().get(JobTrainer.AWAKEN_SLOT).name().contains("우트가르드(트릭스터)")
                    && asadv.slots().get(JobTrainer.INVERT_SLOT).name().contains("료스알프(블레이드 댄서)"), "assassin advance cards");
            Forms.answer(sp, JobTrainer.INVERT_SLOT, null);
            h.assertTrue(chest(sp) != null && chest(sp).title().contains("2차 전직 확인"), "assassin advance confirm");
            Forms.answer(sp, 11, null);
            h.assertTrue("bladedancer".equals(JobSystem.jobOf(sp)) && Wallet.get(sp, Economy.Kind.SILVER) == 0
                    && JobSystem.job(sp).display().equals("료스알프(블레이드 댄서)"), "bladedancer free from the first job: " + JobSystem.jobOf(sp));
            // 창술사의 2차 전직은 아직 준비 중 (다른 1차 직업 — 300 · 2차가 풀림)
            Wallet.set(sp, Economy.Kind.SILVER, 300);
            JobTrainer.open(sp, () -> done[0]++);
            Forms.answer(sp, JobTrainer.slotOf("lancer"), null);
            Forms.answer(sp, 11, null);
            h.assertTrue("lancer".equals(JobSystem.jobOf(sp)), "changed to lancer: " + JobSystem.jobOf(sp));
            JobTrainer.open(sp, () -> done[0]++);
            FormNet.ChestPayload la = chest(sp);
            h.assertTrue(la != null && la.slots().get(JobTrainer.ADV_SLOT).kind() == 3, "lancer: 2차 전직 dim (준비 중)");
            Forms.answer(sp, -1, null);
        } finally {
            JobUpgrades.set(sp, "swordsman_moon", 0);
            RpgJobTests.cleanup(sp);
        }
        RpgGearTests.done(h, sp);
    }

    // ------------------------------------------------------------ 시구르드(소드마스터) — 0.51.0 재설계안 2판

    @GameTest(environment = ENV_SM, maxTicks = 520)
    public void swordmasterSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h, "swordmaster", 50);
        Jobs.Job j = Jobs.job("swordmaster");
        Weapon w = Weapons.weaponFrom("sword", 0, "common");
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 0, 2.5)));
        List<RpgJobTests.Step> steps = new ArrayList<>();
        // 칼집 섬광: 투지 3 → 6칸 · 열 줄 (투지 셋 씀) — 칼집에 넣은 6틱 동안은 피해 없음 → 뽑는 틱에 5 (한 번에 · 숫자만 여러 줄)
        steps.add(new RpgJobTests.Step(1, () -> {
            home(h, sp);
            JobTraits.setStacks(sp, 3);
            h.assertTrue(JobSkills.press(sp, 0), "칼집 섬광 cast");
            h.assertTrue(JobTraits.stacks(sp) == 0, "wave used 3 stacks: " + JobTraits.stacks(sp));
        }));
        steps.add(new RpgJobTests.Step(4, () -> h.assertTrue(TestDummies.dealt(z.get(0)) == 0, "칼집 섬광: no damage while sheathed")));
        steps.add(new RpgJobTests.Step(10, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(0), 5);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "칼집 섬광 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(RpgJobTests.gradientNumber(h, z.get(0), j), "swordmaster gradient number");
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2));
            JobTraits.setStacks(sp, 0);
            // 받아넘기기: 막는 동안 맞음 (받는 피해 처리 — RpgHealth.onLoss) → 깎이지 않음 · 다음 틱 받아침
            h.assertTrue(JobSkills.press(sp, 1), "받아넘기기 cast");
            float hp = sp.getHealth();
            float left = mmorpg.fabric.RpgHealth.onLoss(sp, sp.damageSources().mobAttack(z.get(0)), hp, hp - 4F);
            h.assertTrue(Math.abs(left - hp) < 1e-4, "받아넘기기 blocks the hit: " + left + " vs " + hp);
        }));
        // 받아치기: 막은 다음 틱 → +6 에 3 (숫자 세 줄) · 투지 +3, 막았으니 0.5초의 내디뎌 베기는 없음 (16틱 뒤에도 3 그대로)
        steps.add(new RpgJobTests.Step(16, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(1), 3);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "받아치기 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits.stacks(sp) == 3, "riposte stacks +3: " + JobTraits.stacks(sp));
            JobSkills.resetCooldowns(sp);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2));
            h.assertTrue(JobSkills.press(sp, 1), "받아넘기기 cast (nothing comes)");
        }));
        steps.add(new RpgJobTests.Step(14, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(1), 3);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "받아넘기기 step cut " + TestDummies.dealt(z.get(0)) + " vs " + want);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2.5));
            JobTraits.setStacks(sp, 0);
            h.assertTrue(JobSkills.press(sp, 2), "불의 울타리 cast");
        }));
        // 불의 울타리: 가로지르기 다섯 (6 · 16 · 26 · 36 · 46) — 울타리 안의 과녁을 지나며 1.8 씩 (= 9)
        double[] speed0 = {0, 0};
        steps.add(new RpgJobTests.Step(60, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(2), 9);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "불의 울타리 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2));
            speed0[0] = RpgCombat.attackSpeed.applyAsDouble(sp);
            speed0[1] = sp.getAttributeValue(Attributes.MOVEMENT_SPEED);
            h.assertTrue(JobSkills.press(sp, 3), "용의 피 cast");
            double after = RpgCombat.attackSpeed.applyAsDouble(sp);
            h.assertTrue(JobTraits.stanceOn(sp, "swordmaster_blood") && Math.abs(Guards.of(sp).reduce() - 0.2) < 1e-9, "용의 피 stance on, guard 20%");
            h.assertTrue(Math.abs(after / speed0[0] - 1.35) < 1e-6, "용의 피 attack speed ×1.35: " + speed0[0] + " → " + after);
        }));
        // 용의 피: 이동 속도 + 기본 × 15% · 칼 평타가 투지 +2
        steps.add(new RpgJobTests.Step(4, () -> {
            var ms = sp.getAttribute(Attributes.MOVEMENT_SPEED);
            double gain = sp.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed0[1];
            h.assertTrue(ms != null && Math.abs(gain - ms.getBaseValue() * 0.15) < 1e-6, "용의 피 move speed +15%: " + gain);
            JobTraits.setStacks(sp, 0);
            sp.attack(z.get(0));
            h.assertTrue(JobTraits.stacks(sp) == 2, "용의 피 basic +2 stacks: " + JobTraits.stacks(sp));
        }));
        // 다시 벼린 칼: 6 에 둘레 충격 8 (벼린 칼 전) → 벼린 칼 (사거리 +1.5 · 직업 스킬 · 칼 평타 +20%). 다시 누름은 14틱부터 (그 전은 버림) →
        // 3 에 검풍 32 × (1 + 잃은 체력 50%) × 1.2
        steps.add(new RpgJobTests.Step(12, () -> {
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2.5));
            JobTraits.setStacks(sp, 0);
            h.assertTrue(JobSkills.press(sp, 4), "다시 벼린 칼 cast");
            h.assertTrue(JobRecast.pending(sp, "swordmaster_ult") && JobSkills.cooldownLeft(sp, "swordmaster_ult") == 0, "recast window open · no cooldown yet");
        }));
        steps.add(new RpgJobTests.Step(8, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(4), 8);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "다시 벼린 칼 burst " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits5.forged(sp) && Math.abs(JobTraits5.forgeReach(sp) - 1.5) < 1e-9, "forged: reach +" + JobTraits5.forgeReach(sp));
            h.assertTrue(!JobSkills.press(sp, 4) && JobRecast.pending(sp, "swordmaster_ult"), "recast before the gap is ignored");
        }));
        steps.add(new RpgJobTests.Step(6, () -> {
            z.get(0).setHealth(z.get(0).getMaxHealth() * 0.5F);
            h.assertTrue(JobSkills.press(sp, 4), "다시 벼린 칼 recast (검풍)");
            h.assertTrue(!JobRecast.pending(sp, "swordmaster_ult") && JobSkills.cooldownLeft(sp, "swordmaster_ult") > 0, "recast closes the window · cooldown");
        }));
        double[] before = {0};
        steps.add(new RpgJobTests.Step(16, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(4), 8 + 32 * 1.5 * 1.2);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "다시 벼린 칼 검풍 (잃은 체력 50% · 벼린 칼) " + TestDummies.dealt(z.get(0)) + " vs " + want);
            // 벼린 칼 평타 +20% (평타에 더한 몫)
            before[0] = TestDummies.dealt(z.get(0));
            RpgCombat.forceCrit = false;
            double base = RpgJobTests.basic(sp, w);
            sp.attack(z.get(0));
            RpgCombat.forceCrit = null;
            double got = TestDummies.dealt(z.get(0)) - before[0];
            h.assertTrue(RpgJobTests.near(got, base * 1.2), "forged basic +20% " + got + " vs " + base * 1.2);
        }));
        RpgJobTests.chain(h, steps, 0, () -> {
            RpgJobTests.cleanup(sp);
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }

    // ------------------------------------------------------------ 다인슬레이프(블러드 나이트) — 0.51.0 재설계안 2판

    @GameTest(environment = ENV_BK, maxTicks = 480)
    public void bloodknightSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h, "bloodknight", 50);
        Jobs.Job j = Jobs.job("bloodknight");
        double bonus = 1 + j.trait().def().numOr("bonus", 0.1);
        double cost = j.trait().def().numOr("cost", 0.04);
        double sweet = j.skill(0).num("sweet", 1.5);
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 0, 3.6)));
        List<RpgJobTests.Step> steps = new ArrayList<>();
        // 핏빛 대검: 세 번 (다시 누름은 10틱부터 — 그 전은 버림) · 과녁이 칼끝 (3.2칸 너머 · 내리찍은 가운데) 이라 × 1.5 · 단계마다 체력 4% 를 바쳐 위력 +10%
        steps.add(new RpgJobTests.Step(1, () -> {
            home(h, sp);
            sp.setHealth(sp.getMaxHealth());
            h.assertTrue(JobSkills.press(sp, 0), "핏빛 대검 1 cast");
            h.assertTrue(Math.abs(sp.getHealth() - sp.getMaxHealth() * (1 - cost)) < sp.getMaxHealth() * 0.01, "paid 4% hp: " + sp.getHealth() + " / " + sp.getMaxHealth());
            h.assertTrue(JobRecast.pending(sp, "bloodknight_cleave") && JobSkills.cooldownLeft(sp, "bloodknight_cleave") == 0, "cleave recast window · no cooldown yet");
        }));
        steps.add(new RpgJobTests.Step(6, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(0), 1.4 * sweet * bonus);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "핏빛 대검 1 (칼끝) " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(RpgJobTests.gradientNumber(h, z.get(0), j), "bloodknight gradient number");
            h.assertTrue(!JobSkills.press(sp, 0), "recast before the gap (10) is ignored");
        }));
        steps.add(new RpgJobTests.Step(4, () -> h.assertTrue(JobSkills.press(sp, 0), "핏빛 대검 2 cast")));
        steps.add(new RpgJobTests.Step(10, () -> h.assertTrue(JobSkills.press(sp, 0), "핏빛 대검 3 cast")));
        double[] start = {0};
        steps.add(new RpgJobTests.Step(12, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(0), 5 * sweet * bonus);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "핏빛 대검 3단 (칼끝) " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(!JobRecast.pending(sp, "bloodknight_cleave") && JobSkills.cooldownLeft(sp, "bloodknight_cleave") > 0, "cleave done → cooldown");
            // 피의 돌진: 5칸 앞 과녁에게 뛰어듦
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 5));
            sp.setHealth(sp.getMaxHealth());
            start[0] = sp.position().z;
            h.assertTrue(JobSkills.press(sp, 1), "피의 돌진 cast");
        }));
        // 피의 돌진: 6 에 내려 박음 3 (+10%) · 기절 (느림 10) · 방어 깨짐 +10%
        steps.add(new RpgJobTests.Step(9, () -> {
            double moved = sp.position().z - start[0];
            h.assertTrue(moved > 2.5 && moved < 4.5, "피의 돌진 leapt to the target: " + moved);
            double want = RpgJobTests.expected(sp, j, j.skill(1), 3 * bonus);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "피의 돌진 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            var slow = z.get(0).getEffect(MobEffects.SLOWNESS);
            h.assertTrue(slow != null && slow.getAmplifier() >= 10 && Math.abs(JobTraitsMore.breakMult(z.get(0)) - 1.1) < 1e-6,
                    "stunned · armor break +10%: " + JobTraitsMore.breakMult(z.get(0)));
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 3));
            sp.setHealth(sp.getMaxHealth());
            h.assertTrue(JobSkills.press(sp, 2), "피의 사슬 cast");
        }));
        // 피의 사슬: 4 에 박음 3 → 34 에 끌어당겨 2틱 뒤 6 (모두 +10%)
        steps.add(new RpgJobTests.Step(12, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(2), 3 * bonus);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "피의 사슬 bind " + TestDummies.dealt(z.get(0)) + " vs " + want);
        }));
        steps.add(new RpgJobTests.Step(30, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(2), 9 * bonus);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "피의 사슬 pull " + TestDummies.dealt(z.get(0)) + " vs " + want);
            // 칼을 뽑다: 6칸 앞 과녁
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 6));
            sp.setHealth(sp.getMaxHealth());
            start[0] = sp.position().z;
            h.assertTrue(JobSkills.press(sp, 4), "칼을 뽑다 cast");
        }));
        // 칼을 뽑다: 8 에 칼날 10 → 과녁에게 날아가 내리꽂음 10 → 10틱마다 X 베기 5 × 4 (= 40, +10%)
        steps.add(new RpgJobTests.Step(30, () -> {
            double moved = sp.position().z - start[0];
            h.assertTrue(moved > 3.0, "칼을 뽑다 flew to the target: " + moved);
        }));
        steps.add(new RpgJobTests.Step(45, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(4), 40 * bonus);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "칼을 뽑다 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            // 광란 (마지막 — 끝날 때까지 둘레를 저절로 벰)
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2));
            sp.setHealth(sp.getMaxHealth());
            double before = RpgCombat.attackSpeed.applyAsDouble(sp);
            h.assertTrue(JobSkills.press(sp, 3), "광란 cast");
            double after = RpgCombat.attackSpeed.applyAsDouble(sp);
            h.assertTrue(JobTraits.stanceOn(sp, "bloodknight_frenzy") && Math.abs(after / before - 1.6) < 1e-6, "광란 attack speed ×1.6: " + before + " → " + after);
        }));
        // 광란: 둘레 3.6칸의 과녁을 저절로 벰 (누르지 않음) · 피의 갈증: 체력 30% 에서 공격 속도 × 1.3 (광란과 곱)
        steps.add(new RpgJobTests.Step(16, () -> {
            h.assertTrue(TestDummies.dealt(z.get(0)) > 0, "광란 auto attack: " + TestDummies.dealt(z.get(0)));
            double full = RpgCombat.attackSpeed.applyAsDouble(sp);
            sp.setHealth(sp.getMaxHealth() * 0.3F);
            double low = RpgCombat.attackSpeed.applyAsDouble(sp);
            h.assertTrue(Math.abs(low / full - 1.3) < 1e-3, "피의 갈증 attack speed ×1.3 at 30% hp: " + full + " → " + low);
        }));
        RpgJobTests.chain(h, steps, 0, () -> {
            RpgJobTests.cleanup(sp);
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }


    // ------------------------------------------------------------ 뵈드바르(워로드) — 0.51.0 재설계안 2판

    /** 밀 수 있는 과녁 (끌어오기 · 밀어내기 — 넉백 저항 0) */
    static Husk loose(GameTestHelper h, double dx, double dz) {
        Husk m = dummy(h, dx, dz);
        m.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0);
        return m;
    }

    @GameTest(environment = ENV_WL, maxTicks = 560)
    public void warlordSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h, "warlord", 50);
        Weapon w = Weapons.weaponFrom("axe", 0, "common");
        RpgJobTests.hold(sp, w);
        Jobs.Job j = Jobs.job("warlord");
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 0, 2)));
        List<RpgJobTests.Step> steps = new ArrayList<>();
        // 동면 (40틱): 받는 피해 −60% · 맞으면 (최대 체력 1% 마다) 분노 + 4 (맞음 +5 와 따로) → 40 에 깨어나며 둘레 3.9
        steps.add(new RpgJobTests.Step(1, () -> {
            home(h, sp);
            JobTraitsMore.setRage(sp, 0);
            h.assertTrue(JobSkills.press(sp, 1), "동면 cast");
            var g = Guards.of(sp);
            h.assertTrue(JobTraits6.slumbering(sp) && g != null && Math.abs(g.reduce() - 0.6) < 1e-9 && sp.hasEffect(MobEffects.SLOWNESS),
                    "slumber: guard 60% · slowed: " + (g == null ? null : g.reduce()));
            double max = mmorpg.fabric.RpgHealth.maxOf(sp);
            float hp = sp.getHealth();
            mmorpg.fabric.RpgHealth.lossHook.lost(sp, sp.damageSources().mobAttack(z.get(0)), max * 0.047, hp, hp - max * 0.047);
            h.assertTrue(JobTraitsMore.rage(sp) == 5 + 19, "slumber rage: hurt +5 · 4.7% × 4 → +19: " + JobTraitsMore.rage(sp));
        }));
        steps.add(new RpgJobTests.Step(30, () -> h.assertTrue(TestDummies.dealt(z.get(0)) == 0, "동면: nothing before waking")));
        steps.add(new RpgJobTests.Step(14, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(1), 3.9);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "동면 wake " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(RpgJobTests.gradientNumber(h, z.get(0), j), "warlord gradient number");
            h.assertTrue(!JobTraits6.slumbering(sp), "slumber over");
            // 분노 96 에서 도끼 평타 (50 이상 +10%) → +8 → 100 → 곰의 화신 (8초 · 받는 피해 −15% · 곰 발톱 평타 모습)
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2));
            RpgCombat.forceCrit = false;
            JobTraitsMore.setRage(sp, 96);
            double base = RpgJobTests.basic(sp, w);
            sp.attack(z.get(0));
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), base * 1.1), "rage 96 basic × 1.1: " + TestDummies.dealt(z.get(0)) + " vs " + base * 1.1);
            h.assertTrue(JobTraits6.bearOn(sp) && JobTraits6.bearLeft(sp) > 150 && JobTraitsMore.rage(sp) == 100, "bear avatar on at rage 100: " + JobTraits6.bearLeft(sp));
            h.assertTrue(Math.abs(Guards.of(sp).reduce() - 0.15) < 1e-9, "avatar guard 15%: " + Guards.of(sp).reduce());
            h.assertTrue("warlord_bear".equals(mmorpg.fabric.motion.SwingLook.testKey(sp)), "bear swing look: " + mmorpg.fabric.motion.SwingLook.testKey(sp));
        }));
        // 곰의 화신 동안 도끼 평타 × 1.2 · 분노가 줄어듦 → 대지 가르기 (모으기 — 다시 누를 수 있는 창 · 재사용 대기 없음 · 느려짐)
        long[] t0 = {0};
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
            t0[0] = mmorpg.fabric.job.JobKit.now();
            h.assertTrue(JobSkills.press(sp, 0), "대지 가르기 charge");
            h.assertTrue(JobRecast.pending(sp, "warlord_rend") && JobSkills.cooldownLeft(sp, "warlord_rend") == 0 && sp.hasEffect(MobEffects.SLOWNESS),
                    "rend charging: recast window · no cooldown · slowed");
        }));
        // 12틱 모아 다시 누름 → 4 에 내려침: 6.5 × (0.5 + 0.5 × 12/24)
        double[] frac = {0};
        steps.add(new RpgJobTests.Step(12, () -> {
            long held = mmorpg.fabric.job.JobKit.now() - t0[0];
            frac[0] = 0.5 + 0.5 * Math.min(1, held / 24.0);
            h.assertTrue(TestDummies.dealt(z.get(0)) == 0, "no damage while charging");
            h.assertTrue(JobSkills.press(sp, 0), "대지 가르기 release (held " + held + ")");
            h.assertTrue(!JobRecast.pending(sp, "warlord_rend") && JobSkills.cooldownLeft(sp, "warlord_rend") > 0 && !sp.hasEffect(MobEffects.SLOWNESS),
                    "released: window closed · cooldown · not slowed");
        }));
        steps.add(new RpgJobTests.Step(7, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(0), 6.5 * frac[0]);
            h.assertTrue(frac[0] < 1 && RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "대지 가르기 (part " + frac[0] + ") " + TestDummies.dealt(z.get(0)) + " vs " + want);
            // 끝까지 모음 (다시 누르지 않음): 24틱에 저절로 내려침 → 28 에 6.5 (돌창)
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2.6));
            JobSkills.resetCooldowns(sp);
            h.assertTrue(JobSkills.press(sp, 0), "대지 가르기 charge (full)");
        }));
        steps.add(new RpgJobTests.Step(22, () -> h.assertTrue(TestDummies.dealt(z.get(0)) == 0 && JobRecast.pending(sp, "warlord_rend"), "still charging at 22")));
        steps.add(new RpgJobTests.Step(10, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(0), 6.5);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "대지 가르기 (full) " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(!JobRecast.pending(sp, "warlord_rend") && JobSkills.cooldownLeft(sp, "warlord_rend") > 0, "auto release → cooldown");
            // 곰의 포옹: 왼쪽 · 오른쪽 (밀 수 있는 과녁) 을 앞 1.8칸으로 끌어와 12 에 11.7 · 양쪽이라 기절
            for (Husk x : z) x.discard();
            z.clear();
            home(h, sp);
            z.add(loose(h, -2.6, 1.2));
            z.add(loose(h, 2.6, 1.2));
            h.assertTrue(JobSkills.press(sp, 2), "곰의 포옹 cast");
        }));
        steps.add(new RpgJobTests.Step(9, () -> {
            for (Husk x : z) {
                double dx = Math.abs(x.getX() - sp.getX());
                h.assertTrue(dx < 1.6 && x.getZ() - sp.getZ() > 0.8, "hug pulled to the front: dx " + dx + " · dz " + (x.getZ() - sp.getZ()));
            }
        }));
        steps.add(new RpgJobTests.Step(5, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(2), 11.7);
            for (Husk x : z) {
                h.assertTrue(RpgJobTests.near(TestDummies.dealt(x), want), "곰의 포옹 " + TestDummies.dealt(x) + " vs " + want);
                var slow = x.getEffect(MobEffects.SLOWNESS);
                h.assertTrue(slow != null && slow.getAmplifier() >= 10, "both sides → stunned");
            }
            // 마지막 휘두름: 24틱 모음 (받는 피해 −50%) → 둘레 8칸 한 바퀴 — 안쪽 (5칸 안) 26 · 바깥 날 52
            for (Husk x : z) x.discard();
            z.clear();
            home(h, sp);
            z.add(dummy(h, 0, 2.5));
            z.add(dummy(h, 0, 5.6)); // 바깥 고리 (5 ~ 8칸) — 시험 자리 (8×8) 안
            h.assertTrue(JobSkills.press(sp, 4), "마지막 휘두름 cast");
            h.assertTrue(Math.abs(Guards.of(sp).reduce() - 0.5) < 1e-9, "windup guard 50%: " + Guards.of(sp).reduce());
        }));
        steps.add(new RpgJobTests.Step(20, () -> h.assertTrue(TestDummies.dealt(z.get(0)) == 0 && TestDummies.dealt(z.get(1)) == 0, "nothing during the windup")));
        steps.add(new RpgJobTests.Step(8, () -> {
            double inner = RpgJobTests.expected(sp, j, j.skill(4), 26);
            double outer = RpgJobTests.expected(sp, j, j.skill(4), 52);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), inner), "마지막 휘두름 inner " + TestDummies.dealt(z.get(0)) + " vs " + inner);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(1)), outer), "마지막 휘두름 outer edge " + TestDummies.dealt(z.get(1)) + " vs " + outer);
        }));
        // 거인의 무게: 곰의 화신이 끝난 뒤 (분노 0 — 평타 그대로) · 공격 속도 × 0.75 · 도끼 평타 × 1.4 · 범위 모습 (×1.6)
        double[] speed0 = {0};
        steps.add(new RpgJobTests.Step(70, () -> {
            h.assertTrue(!JobTraits6.bearOn(sp) && JobTraitsMore.rage(sp) < 50, "avatar over: rage " + JobTraitsMore.rage(sp));
            for (Husk x : z) x.discard();
            z.clear();
            home(h, sp);
            z.add(dummy(h, 0, 2));
            speed0[0] = RpgCombat.attackSpeed.applyAsDouble(sp);
            h.assertTrue(JobSkills.press(sp, 3), "거인의 무게 cast");
            double after = RpgCombat.attackSpeed.applyAsDouble(sp);
            h.assertTrue(JobTraits6.titanOn(sp) && Math.abs(after / speed0[0] - 0.75) < 1e-6, "titan attack speed ×0.75: " + speed0[0] + " → " + after);
            h.assertTrue("warlord_titan".equals(mmorpg.fabric.motion.SwingLook.testKey(sp)), "titan swing look: " + mmorpg.fabric.motion.SwingLook.testKey(sp));
        }));
        steps.add(new RpgJobTests.Step(14, () -> {
            RpgCombat.forceCrit = false;
            double base = RpgJobTests.basic(sp, w);
            sp.attack(z.get(0));
            RpgCombat.forceCrit = null;
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), base * 1.4), "titan basic × 1.4: " + TestDummies.dealt(z.get(0)) + " vs " + base * 1.4);
        }));
        RpgJobTests.chain(h, steps, 0, () -> {
            RpgJobTests.cleanup(sp);
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }

    // ------------------------------------------------------------ 스타르카드(글래디에이터) — 0.51.0 재설계안 2판

    @GameTest(environment = ENV_GL, maxTicks = 640)
    public void gladiatorSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h, "gladiator", 50);
        Weapon w = Weapons.weaponFrom("axe", 0, "common");
        RpgJobTests.hold(sp, w);
        Jobs.Job j = Jobs.job("gladiator");
        double per = j.trait().def().numOr("per", 0.03);
        double others = j.trait().def().numOr("others", 0.4);
        // 둘째 과녁은 등 뒤 (첫 평타의 앞 범위에 들지 않게 — 결투는 처음 맞힌 앞 과녁)
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 0, 2), dummy(h, 0, -2.5)));
        List<RpgJobTests.Step> steps = new ArrayList<>();
        // 결투: 처음 도끼 평타로 맞힌 적이 상대 (그 한 대는 그대로)
        steps.add(new RpgJobTests.Step(1, () -> {
            home(h, sp);
            RpgCombat.forceCrit = false;
            double base = RpgJobTests.basic(sp, w);
            sp.attack(z.get(0));
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), base), "first basic × 1: " + TestDummies.dealt(z.get(0)) + " vs " + base);
            h.assertTrue(JobTraits6.duelTarget(sp) == z.get(0) && JobTraits6.duelStacks(sp) == 0, "duel opened on the first enemy");
        }));
        // 다른 적 × (1 − 0.4) · 상대는 × (1 + 결투 × 3%)
        steps.add(new RpgJobTests.Step(14, () -> {
            double base = RpgJobTests.basic(sp, w);
            double before = TestDummies.dealt(z.get(1));
            sp.attack(z.get(1));
            double got = TestDummies.dealt(z.get(1)) - before;
            h.assertTrue(RpgJobTests.near(got, base * (1 - others)), "other enemy −40%: " + got + " vs " + base * (1 - others));
            h.assertTrue(JobTraits6.duelTarget(sp) == z.get(0), "duel stays on the first enemy");
            JobTraits6.setDuel(sp, z.get(0), 5, true);
        }));
        double[] mark = {0};
        steps.add(new RpgJobTests.Step(14, () -> {
            double before = TestDummies.dealt(z.get(0));
            double base = RpgJobTests.basic(sp, w);
            sp.attack(z.get(0));
            RpgCombat.forceCrit = null;
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)) - before, base * (1 + 5 * per)), "duel 5 → +15%: " + (TestDummies.dealt(z.get(0)) - before) + " vs "
                    + base * (1 + 5 * per));
            // 1초마다 +1 (얼리지 않음)
            JobTraits6.setDuel(sp, z.get(0), 0, false);
        }));
        steps.add(new RpgJobTests.Step(44, () -> {
            h.assertTrue(JobTraits6.duelStacks(sp) == 2, "duel grows 1 per second: " + JobTraits6.duelStacks(sp));
            // 상대가 쓰러짐 → 가까운 적과 새 결투 (결투 × 0.5)
            JobTraits6.setDuel(sp, z.get(0), 6, true);
            z.get(0).discard();
        }));
        steps.add(new RpgJobTests.Step(8, () -> {
            h.assertTrue(JobTraits6.duelTarget(sp) == z.get(1) && JobTraits6.duelStacks(sp) == 3, "duel moves to the nearest with half: " + JobTraits6.duelStacks(sp));
            // 도끼 난타: 결투 4 (얼림) — 6.5 × 1.12 (2 · 12 에 무리마다 한 번)
            for (Husk x : z) x.discard();
            z.clear();
            home(h, sp);
            z.add(dummy(h, 0, 2.5));
            JobTraits6.setDuel(sp, z.get(0), 4, true);
            h.assertTrue(JobSkills.press(sp, 0), "도끼 난타 cast");
        }));
        steps.add(new RpgJobTests.Step(16, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(0), 6.5) * (1 + 4 * per);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "도끼 난타 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(RpgJobTests.gradientNumber(h, z.get(0), j), "gladiator gradient number");
            // 걸어 당기기: 5칸 앞 (밀 수 있는 과녁) 을 걸어 끌어와 6 에 3.9 · 기절
            z.get(0).discard();
            home(h, sp);
            z.set(0, loose(h, 0, 5));
            JobTraits6.setDuel(sp, z.get(0), 0, true);
            mark[0] = z.get(0).getZ() - sp.getZ();
            h.assertTrue(JobSkills.press(sp, 1), "걸어 당기기 cast");
        }));
        steps.add(new RpgJobTests.Step(9, () -> {
            double dz = z.get(0).getZ() - sp.getZ();
            double want = RpgJobTests.expected(sp, j, j.skill(1), 3.9);
            h.assertTrue(dz < 2.6 && dz < mark[0] - 2, "hooked in: " + mark[0] + " → " + dz);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "걸어 당기기 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            var slow = z.get(0).getEffect(MobEffects.SLOWNESS);
            h.assertTrue(slow != null && slow.getAmplifier() >= 10, "hook stun");
            // 여덟 팔: 4 · 14 · 24 · 34 에 2.6 · 2.6 · 2.6 · 3.9 (= 11.7) → 받는 피해 +10%
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2.5));
            JobTraits6.setDuel(sp, z.get(0), 0, true);
            h.assertTrue(JobSkills.press(sp, 2), "여덟 팔 cast");
        }));
        steps.add(new RpgJobTests.Step(30, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(2), 7.8);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "여덟 팔 three volleys " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(Math.abs(JobTraitsMore.breakMult(z.get(0)) - 1) < 1e-9, "no break before the last volley");
        }));
        steps.add(new RpgJobTests.Step(10, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(2), 11.7);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "여덟 팔 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(Math.abs(JobTraitsMore.breakMult(z.get(0)) - 1.1) < 1e-9, "break +10%: " + JobTraitsMore.breakMult(z.get(0)));
            // 불사의 분노: 쓰러지지 않음 · 공격 속도 × 1.4
            double before = RpgCombat.attackSpeed.applyAsDouble(sp);
            h.assertTrue(!mmorpg.fabric.RpgHealth.undying.test(sp), "not undying before fury");
            h.assertTrue(JobSkills.press(sp, 3), "불사의 분노 cast");
            double after = RpgCombat.attackSpeed.applyAsDouble(sp);
            h.assertTrue(JobTraits6.furyOn(sp) && mmorpg.fabric.RpgHealth.undying.test(sp) && Math.abs(after / before - 1.4) < 1e-6,
                    "fury: undying · attack speed ×1.4: " + before + " → " + after);
            h.assertTrue("gladiator_fury".equals(mmorpg.fabric.motion.SwingLook.testKey(sp)), "fury swing look: " + mmorpg.fabric.motion.SwingLook.testKey(sp));
        }));
        // 홀름강: 3칸 앞 상대 (결투 0 얼림) 와 결투장 (둘 사이 가운데 · 7칸) — 안의 다른 적 (밀 수 있음) 은 밖으로 · 상대 받는 피해 +15% → 10 에 12 →
        // 140 에 40 (체력 30% 아래 — 두 배)
        Vec3[] cg = {Vec3.ZERO};
        steps.add(new RpgJobTests.Step(4, () -> {
            for (Husk x : z) x.discard();
            z.clear();
            home(h, sp);
            z.add(dummy(h, 0, 3));
            z.add(loose(h, 2.0, 4.0));
            JobTraits6.setDuel(sp, z.get(0), 0, true);
            cg[0] = sp.position().lerp(z.get(0).position(), 0.5);
            h.assertTrue(JobSkills.press(sp, 4), "홀름강 cast");
            h.assertTrue(Math.abs(JobTraits6.arenaTaken(z.get(0)) - 1.15) < 1e-9 && Math.abs(JobTraits6.arenaTaken(z.get(1)) - 1) < 1e-9, "arena: the foe takes +15%");
        }));
        steps.add(new RpgJobTests.Step(6, () -> {
            Husk o = z.get(1);
            double d = Math.hypot(o.getX() - cg[0].x, o.getZ() - cg[0].z);
            h.assertTrue(d > 7.5, "other enemy pushed out of the arena: " + d);
        }));
        steps.add(new RpgJobTests.Step(8, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(4), 12) * 1.15;
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "홀름강 opening " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(TestDummies.dealtBy(z.get(1)) == 0, "the outsider is not hit (pushed out — only wall damage)");
        }));
        steps.add(new RpgJobTests.Step(118, () -> z.get(0).setHealth(z.get(0).getMaxHealth() * 0.2F)));
        steps.add(new RpgJobTests.Step(12, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(4), 12 + 40 * 2) * 1.15;
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "홀름강 finale (below 30% → ×2) " + TestDummies.dealt(z.get(0)) + " vs " + want);
        }));
        steps.add(new RpgJobTests.Step(4, () -> h.assertTrue(Math.abs(JobTraits6.arenaTaken(z.get(0)) - 1) < 1e-9, "arena gone")));
        RpgJobTests.chain(h, steps, 0, () -> {
            RpgJobTests.cleanup(sp);
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }

    // ------------------------------------------------------------ 뵐룬드(룬 스미스) — 재설계안 2판

    @GameTest(environment = ENV_RK, maxTicks = 640)
    public void runeknightSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = RpgJobTests.jobPlayer(h, "runeknight", Map.of("int", 30, "str", 20), 50);
        Jobs.Job j = Jobs.job("runeknight");
        Weapon w = Weapons.weaponFrom("sword", 0, "common");
        double spread = j.trait().def().numOr("markBurst", 1.0) * j.trait().def().numOr("spread", 0.5);
        double quench = j.trait().def().numOr("quench", 0.8);
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 0, 3)));
        List<RpgJobTests.Step> steps = new ArrayList<>();
        double[] mark = {0, 0};
        // 룬 모루: 4 에 내려찍음 → 갈라짐이 3칸 과녁에 닿음 (3.4) → 그 자리에 모루가 솟아 12틱 뒤 터짐 (5.1) = 8.5 · 각인 1 (스킬마다 적마다 한 번)
        steps.add(new RpgJobTests.Step(1, () -> {
            home(h, sp);
            h.assertTrue(JobSkills.press(sp, 0), "룬 모루 cast");
        }));
        steps.add(new RpgJobTests.Step(26, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(0), 8.5);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "룬 모루 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits.marks(sp, z.get(0)) == 1, "one mark per cast: " + JobTraits.marks(sp, z.get(0)));
            h.assertTrue(RpgJobTests.gradientNumber(h, z.get(0), j), "rune smith gradient number");
            // 과녁 곁 (1.5칸 앞) 에 서고 · 각인 찍힌 과녁 뒤 2.2칸에 다른 과녁 (번짐을 받음 — 평타는 닿지 않는 거리)
            homeAt(h, sp, 3.0);
            z.add(dummy(h, 0, 5.2));
        }));
        // 평타가 각인 하나를 터뜨림 (위력 × 1.0) → 둘레 3칸의 다른 적에게 룬 폭발이 번짐 (1틱 뒤 — 위력 × 1.0 × 0.5)
        steps.add(new RpgJobTests.Step(4, () -> {
            RpgCombat.forceCrit = false;
            mark[0] = TestDummies.dealt(z.get(0));
            mark[1] = RpgJobTests.basic(sp, w);
            sp.attack(z.get(0));
            RpgCombat.forceCrit = null;
        }));
        steps.add(new RpgJobTests.Step(3, () -> {
            double unit = raw(sp, j, 1.0);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)) - mark[0], mark[1] + unit), "basic + mark burst " + (TestDummies.dealt(z.get(0)) - mark[0])
                    + " vs " + (mark[1] + unit));
            h.assertTrue(JobTraits.marks(sp, z.get(0)) == 0, "mark used");
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(1)), unit * spread), "rune burst spreads " + TestDummies.dealt(z.get(1)) + " vs " + unit * spread);
            for (Husk x : z) x.discard();
            z.clear();
            home(h, sp);
            z.add(dummy(h, 0, 2.5));
            h.assertTrue(JobSkills.press(sp, 1), "풀무 숨 cast");
            h.assertTrue(RpgHealth.shieldOf(sp) > 0, "bellows shield " + RpgHealth.shieldOf(sp));
        }));
        // 풀무 숨: 4 · 14 · 24 에 큰 숨 (1.7 × 3 = 5.1) → 30 에 끝나면 맞은 적이 달궈짐
        steps.add(new RpgJobTests.Step(34, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(1), 5.1);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "풀무 숨 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits7.heatedOf(sp, z.get(0)), "heated after the bellows");
            mark[0] = TestDummies.dealt(z.get(0));
            h.assertTrue(JobSkills.press(sp, 2), "용광로 cast");
        }));
        // 용광로: 16 에 쇳물 기둥 (15.3) — 달궈진 과녁이라 식으며 깨짐 (위력 × 0.8 · 짧은 기절) → 다시 달궈짐
        steps.add(new RpgJobTests.Step(19, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(2), 15.3) + raw(sp, j, quench);
            double got = TestDummies.dealt(z.get(0)) - mark[0];
            h.assertTrue(RpgJobTests.near(got, want), "용광로 + quench " + got + " vs " + want);
            h.assertTrue(z.get(0).hasEffect(MobEffects.SLOWNESS), "quench stun");
            h.assertTrue(JobTraits7.heatedOf(sp, z.get(0)), "heated again by the furnace");
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2));
            double r0 = sp.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
            h.assertTrue(JobSkills.press(sp, 3), "담금질 cast");
            double r1 = sp.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
            h.assertTrue(JobTraits.stanceOn(sp, "runeknight_temper") && Math.abs(JobTraits.reachBonus(sp) - 1.0) < 1e-9 && Math.abs(r1 - r0 - 1.0) < 1e-9,
                    "temper stance · reach +1: " + JobTraits.reachBonus(sp) + ", " + r0 + " -> " + r1);
            h.assertTrue("runeknight_temper".equals(mmorpg.fabric.motion.SwingLook.testKey(sp)), "tempered greatsword look: " + mmorpg.fabric.motion.SwingLook.testKey(sp));
        }));
        // 담금질: 칼 평타가 맞힌 적에 달군 쇳조각이 박혀 1초 뒤 터짐 (위력 × 0.45 · 마법)
        steps.add(new RpgJobTests.Step(16, () -> {
            RpgCombat.forceCrit = false;
            mark[1] = RpgJobTests.basic(sp, w);
            sp.attack(z.get(0));
            RpgCombat.forceCrit = null;
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), mark[1]), "tempered basic (shard not burst yet) " + TestDummies.dealt(z.get(0)) + " vs " + mark[1]);
        }));
        steps.add(new RpgJobTests.Step(26, () -> {
            double want = mark[1] + raw(sp, j, 0.45);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "hot shard burst " + TestDummies.dealt(z.get(0)) + " vs " + want);
            JobTraits.testEndStance(sp);
            JobSkills.resetCooldowns(sp);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 3));
            h.assertTrue(JobSkills.press(sp, 4), "쇳물 숫양 cast");
        }));
        // 쇳물 숫양: 달려온 숫양이 지나감 (24 · 21 틱쯤) → 23 에 대검과 맞부딪침 → 30 에 받아쳐 쇳물 물결 (44) = 68 · 달궈짐 · 각인 1
        steps.add(new RpgJobTests.Step(44, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(4), 68);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "쇳물 숫양 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits7.heatedOf(sp, z.get(0)), "heated by the molten wave");
            h.assertTrue(JobTraits.marks(sp, z.get(0)) == 1, "one mark per cast: " + JobTraits.marks(sp, z.get(0)));
        }));
        RpgJobTests.chain(h, steps, 0, () -> {
            RpgJobTests.cleanup(sp);
            h.assertTrue(!JobTraits7.heatedOf(sp, z.get(0)), "heat cleared on job change");
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }

    // ------------------------------------------------------------ 스키르니르(소드 콜러) — 재설계안 2판 (투르스(헥스 블레이드) 대신)

    @GameTest(environment = ENV_SC, maxTicks = 640)
    public void swordcallerSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = RpgJobTests.jobPlayer(h, "swordcaller", Map.of("int", 30, "str", 20), 50);
        Jobs.Job j = Jobs.job("swordcaller");
        Weapon w = Weapons.weaponFrom("sword", 0, "common");
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 0, 5)));
        List<RpgJobTests.Step> steps = new ArrayList<>();
        double[] mark = {0, 0};
        // 스스로 싸우는 칼: 허공을 휘두르면 칼이 날아가 5칸 앞 과녁을 대신 벰 (평타 그대로 · 범위 피해 없음) → 이어 벨수록 쌓임 (하나당 공격 속도 +20%)
        steps.add(new RpgJobTests.Step(1, () -> {
            home(h, sp);
            RpgCombat.forceCrit = false;
            mmorpg.fabric.Slash.onPunch(sp);
        }));
        steps.add(new RpgJobTests.Step(6, () -> {
            double want = RpgJobTests.basic(sp, w);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "flying sword basic " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits7.stacksOf(sp) == 1 && Math.abs(RpgCombat.attackSpeed.applyAsDouble(sp) - 1.2) < 1e-9, "chain 1 · speed 1.2: "
                    + JobTraits7.stacksOf(sp) + " · " + RpgCombat.attackSpeed.applyAsDouble(sp));
        }));
        steps.add(new RpgJobTests.Step(14, () -> mmorpg.fabric.Slash.onPunch(sp)));
        steps.add(new RpgJobTests.Step(6, () -> {
            double want = RpgJobTests.basic(sp, w) * 2;
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "second flying cut " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits7.stacksOf(sp) == 2 && Math.abs(RpgCombat.attackSpeed.applyAsDouble(sp) - 1.4) < 1e-9, "chain 2 · speed 1.4: "
                    + JobTraits7.stacksOf(sp) + " · " + RpgCombat.attackSpeed.applyAsDouble(sp));
        }));
        // 2초 못 베면 처음부터
        steps.add(new RpgJobTests.Step(48, () -> {
            h.assertTrue(JobTraits7.stacksOf(sp) == 0 && Math.abs(RpgCombat.attackSpeed.applyAsDouble(sp) - 1) < 1e-9, "chain lapsed: " + JobTraits7.stacksOf(sp));
            RpgCombat.forceCrit = null;
            mark[0] = TestDummies.dealt(z.get(0));
            h.assertTrue(JobSkills.press(sp, 0), "베어라 cast");
        }));
        // 베어라: 칼이 겨눈 자리로 날아가 둘레를 두 번 (4.0) → 10틱 뒤 두 번 더 (4.5) = 8.5 → 땅에 꽂혀 기다림
        steps.add(new RpgJobTests.Step(28, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(0), 8.5);
            double got = TestDummies.dealt(z.get(0)) - mark[0];
            h.assertTrue(RpgJobTests.near(got, want), "베어라 " + got + " vs " + want);
            h.assertTrue(JobTraits7.stuckOf(sp), "sword stuck, waiting");
            h.assertTrue(RpgJobTests.gradientNumber(h, z.get(0), j), "sword caller gradient number");
            mark[0] = TestDummies.dealt(z.get(0));
            mark[1] = sp.position().z;
            h.assertTrue(JobSkills.press(sp, 1), "데려가라 cast");
        }));
        // 데려가라: 꽂힌 칼 (과녁 자리) 곁으로 날아감 — 지나가며 과녁을 벰 (5.1) · 칼을 뽑아 쥠
        steps.add(new RpgJobTests.Step(12, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(1), 5.1);
            double got = TestDummies.dealt(z.get(0)) - mark[0];
            h.assertTrue(RpgJobTests.near(got, want), "데려가라 " + got + " vs " + want);
            h.assertTrue(sp.position().z - mark[1] > 3.5, "flew to the sword: " + (sp.position().z - mark[1]));
            h.assertTrue(!JobTraits7.stuckOf(sp), "sword taken back");
            home(h, sp);
            mark[0] = TestDummies.dealt(z.get(0));
            h.assertTrue(JobSkills.press(sp, 2), "꿰뚫고 맴돌아라 cast");
        }));
        // 꿰뚫고 맴돌아라: 겨눈 과녁을 꿰뚫고 (3.3) 10틱마다 둘레를 돌며 벰 (3.0 × 4 — 숫자 셋씩) = 15.3
        steps.add(new RpgJobTests.Step(52, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(2), 15.3);
            double got = TestDummies.dealt(z.get(0)) - mark[0];
            h.assertTrue(RpgJobTests.near(got, want), "꿰뚫고 맴돌아라 " + got + " vs " + want);
            // 겨눈 적이 없으면 쓰지 않음 (뒤로 돌아섬 — 재사용 대기 없음)
            JobSkills.resetCooldowns(sp);
            Vec3 at = h.absoluteVec(new Vec3(4.5, 2, 1.5));
            sp.snapTo(at.x, at.y, at.z, 180F, 0F);
            sp.setYHeadRot(180F);
            h.assertTrue(!JobSkills.press(sp, 2), "no target → not cast");
            home(h, sp);
            h.assertTrue(JobSkills.press(sp, 3), "둘로 나뉘어라 cast");
            h.assertTrue(JobTraits.stanceOn(sp, "swordcaller_twin") && "swordcaller_twin".equals(mmorpg.fabric.motion.SwingLook.testKey(sp)), "twin stance · look: "
                    + mmorpg.fabric.motion.SwingLook.testKey(sp));
            h.assertTrue(Math.abs(JobTraits7.flyRangeOf(sp) - 14) < 1e-9, "fly range +4: " + JobTraits7.flyRangeOf(sp));
            mark[0] = TestDummies.dealt(z.get(0));
            RpgCombat.forceCrit = false;
        }));
        // 둘로 나뉘어라: 칼 평타 = 평타 + 둘째 칼 (위력 × 0.9 — 직업 색 숫자)
        steps.add(new RpgJobTests.Step(16, () -> mmorpg.fabric.Slash.onPunch(sp)));
        steps.add(new RpgJobTests.Step(6, () -> {
            double want = RpgJobTests.basic(sp, w) + raw(sp, j, 0.9);
            double got = TestDummies.dealt(z.get(0)) - mark[0];
            h.assertTrue(RpgJobTests.near(got, want), "twin swords " + got + " vs " + want);
            RpgCombat.forceCrit = null;
            JobTraits.testEndStance(sp);
            h.assertTrue(Math.abs(JobTraits7.flyRangeOf(sp) - 10) < 1e-9, "fly range back to 10: " + JobTraits7.flyRangeOf(sp));
            JobSkills.resetCooldowns(sp);
            mark[0] = TestDummies.dealt(z.get(0));
            h.assertTrue(JobSkills.press(sp, 4), "칼날 감옥 cast");
        }));
        // 칼날 감옥: 10 에 둘러싸 묶음 → 10틱마다 사방에서 벰 (6 × 6) → 70 에 칼들이 꿰뚫으며 돌아오는 큰 한 방 (32) = 68
        steps.add(new RpgJobTests.Step(22, () -> h.assertTrue(z.get(0).hasEffect(MobEffects.SLOWNESS), "imprisoned (rooted)")));
        steps.add(new RpgJobTests.Step(56, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(4), 68);
            double got = TestDummies.dealt(z.get(0)) - mark[0];
            h.assertTrue(RpgJobTests.near(got, want), "칼날 감옥 " + got + " vs " + want);
        }));
        RpgJobTests.chain(h, steps, 0, () -> {
            RpgJobTests.cleanup(sp);
            h.assertTrue(JobTraits7.stacksOf(sp) == 0, "chain cleared on job change");
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
        // 시험 자리는 barrier 벽 안 8 × 8 칸 (z 8 부터 벽 — 벽 속 과녁은 숨막힘 피해가 더해짐): 뒤로 물러나는 스킬은 앞쪽에서 써서 벽 안에 머묾
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 0, 4.5), dummy(h, 0, 6.0)));
        List<RpgJobTests.Step> steps = new ArrayList<>();
        double[] mark = {0, 0};
        // 꿰뚫는 연사: 뒤로 구르며 1.2 × 2 → 무릎 꿇고 1.4 × 2 + 꿰뚫는 화살 2.3 (겨눈 과녁 = 7.5) · 같은 일직선 뒤의 과녁도 꿰뚫음 (2.3)
        steps.add(new RpgJobTests.Step(1, () -> {
            homeAt(h, sp, 3.5);
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
            homeAt(h, sp, 6.0);
            z.add(dummy(h, 0, 6.0));
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
            homeAt(h, sp, 6.0);
            z.set(0, dummy(h, 0, 6.0));
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

    // ------------------------------------------------------------ 우트가르드(트릭스터)

    /** 시험 자리에서 (dx, dz) 의 체력 1 몹 (과녁이 아님 — 쓰러짐) */
    static Husk weak(GameTestHelper h, double dx, double dz) {
        Husk m = h.spawnWithNoFreeWill(EntityTypes.HUSK, (float) (4.5 + dx), 2F, (float) (1.5 + dz));
        m.setHealth(1F);
        return m;
    }

    @GameTest(environment = ENV_TR, maxTicks = 720)
    public void tricksterSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = RpgJobTests.jobPlayer(h, "trickster", Map.of("dex", 50), 50);
        Weapon w = Weapons.weaponFrom("dagger", 0, "common");
        RpgJobTests.hold(sp, w);
        Jobs.Job j = Jobs.job("trickster");
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 0, 2.0)));
        List<RpgJobTests.Step> steps = new ArrayList<>();
        double[] mark = {0, 0};
        Husk[] victim = {null};
        // 표식: 단검 평타 +1 (최대 7 — 1차는 5)
        steps.add(new RpgJobTests.Step(1, () -> {
            home(h, sp);
            RpgJobTests.clearStrays(h, z.get(0));
            RpgCombat.forceCrit = false;
            JobTraits3.setMarks(sp, z.get(0), 6);
            sp.attack(z.get(0));
            h.assertTrue(JobTraits3.marks(sp, z.get(0)) == 7, "trickster marks 6 → 7: " + JobTraits3.marks(sp, z.get(0)));
        }));
        steps.add(new RpgJobTests.Step(12, () -> {
            sp.attack(z.get(0));
            h.assertTrue(JobTraits3.marks(sp, z.get(0)) == 7, "marks capped at 7: " + JobTraits3.marks(sp, z.get(0)));
            // 표식 옮겨감: 표식 5 인 몹을 단검 평타로 쓰러뜨리면 (쓰러뜨린 평타도 표식 +1 — 맞힘 훅이 쓰러짐보다 먼저) 둘레 6칸의 가장 가까운 과녁에게 6
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 2.0, 3.5));
            victim[0] = weak(h, 0, 2.0);
            RpgJobTests.clearStrays(h, z.get(0), victim[0]);
            JobTraits3.setMarks(sp, victim[0], 5);
        }));
        steps.add(new RpgJobTests.Step(12, () -> {
            sp.attack(victim[0]);
            h.assertTrue(victim[0].isRemoved() || !victim[0].isAlive(), "marked mob fell");
        }));
        steps.add(new RpgJobTests.Step(8, () -> {
            h.assertTrue(JobTraits3.marks(sp, z.get(0)) == 6, "marks shifted to the nearest (5 + the killing blow): " + JobTraits3.marks(sp, z.get(0)));
            // 검은 장미 덤불: 표식 7 → 파고들어 0.35 → 머리 위로 넘어 등 뒤에서 터뜨림 0.2 + 0.6 × 7 (= 4.75) · 표식이 사라짐 · 과녁을 봄
            RpgCombat.forceCrit = null;
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2.5));
            RpgJobTests.clearStrays(h, z.get(0));
            JobTraits3.setMarks(sp, z.get(0), 7);
            h.assertTrue(JobSkills.press(sp, 0), "검은 장미 덤불 cast");
            h.assertTrue(JobTraits3.marks(sp, z.get(0)) == 0, "rose takes the marks");
        }));
        steps.add(new RpgJobTests.Step(16, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(0), 4.75);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "검은 장미 덤불 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(RpgJobTests.gradientNumber(h, z.get(0), j), "trickster gradient number");
            h.assertTrue(sp.position().z > z.get(0).getZ() + 0.6, "vaulted behind: " + sp.position().z + " vs target " + z.get(0).getZ());
            h.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(sp.getYRot() - 180F)) < 45F, "faces the target after the vault: " + sp.getYRot());
            // 우트가르드의 환술: 앞으로 순간 이동 · 원래 자리 양옆 환영 둘 — 닿은 과녁 → 둘 다 터짐 (1.425 × 2 = 2.85) · 표식 +2
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 1.4));
            RpgJobTests.clearStrays(h, z.get(0));
            mark[0] = sp.position().z;
            h.assertTrue(JobSkills.press(sp, 1), "우트가르드의 환술 cast");
        }));
        steps.add(new RpgJobTests.Step(10, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(1), 2.85);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "우트가르드의 환술 (two illusions) " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits3.marks(sp, z.get(0)) == 2, "mirage marks +2: " + JobTraits3.marks(sp, z.get(0)));
            h.assertTrue(sp.position().z - mark[0] > 4, "teleported ahead: " + (sp.position().z - mark[0]));
            // 요르문간드의 똬리: S 자로 갔다가 되돌아옴 — 가며 벰 2.4 (표식 +1) → 0.9초에 조임 3.15 (표식 +1) → 독 0.75 × 4 (= 8.55) · 처음 자리로
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2.5));
            RpgJobTests.clearStrays(h, z.get(0));
            mark[1] = sp.position().z;
            h.assertTrue(JobSkills.press(sp, 2), "요르문간드의 똬리 cast");
        }));
        steps.add(new RpgJobTests.Step(12, () -> {
            double cut = RpgJobTests.expected(sp, j, j.skill(2), 2.4);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), cut), "똬리 cut " + TestDummies.dealt(z.get(0)) + " vs " + cut);
            h.assertTrue(JobTraits3.marks(sp, z.get(0)) == 1, "coil cut marks +1: " + JobTraits3.marks(sp, z.get(0)));
        }));
        steps.add(new RpgJobTests.Step(50, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(2), 8.55);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "요르문간드의 똬리 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits3.marks(sp, z.get(0)) == 2, "coil squeeze marks +1: " + JobTraits3.marks(sp, z.get(0)));
            h.assertTrue(Math.abs(sp.position().z - mark[1]) < 1.2, "came back to the start: " + (sp.position().z - mark[1]));
            // 끝없는 밤: 은신 (투명 · 태세 10초) → 첫 단검 평타 치명타 확정 × 2.5 → 풀림 (태세는 남음 · 이동 +30%) → 몹을 쓰러뜨리면 다시 은신
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2.0));
            RpgJobTests.clearStrays(h, z.get(0));
            h.assertTrue(JobSkills.press(sp, 3), "끝없는 밤 cast");
            h.assertTrue(JobTraits3.veilOn(sp) && JobTraits.stanceOn(sp, "trickster_night") && sp.hasEffect(MobEffects.INVISIBILITY), "night: stealth · stance · invisible");
        }));
        steps.add(new RpgJobTests.Step(4, () -> {
            double base = RpgJobTests.basic(sp, w);
            sp.attack(z.get(0));
            double want = base * 2.5 * RpgCombat.critMultOf(sp, w);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "night first hit " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(!JobTraits3.veilOn(sp) && JobTraits.stanceOn(sp, "trickster_night") && !sp.hasEffect(MobEffects.INVISIBILITY), "attack breaks the stealth (stance stays)");
            h.assertTrue(Math.abs(JobTraits3.speedBonus(sp, false) - 0.3) < 1e-9, "speed +30% after stealth: " + JobTraits3.speedBonus(sp, false));
            victim[0] = weak(h, 1.0, 2.0);
        }));
        steps.add(new RpgJobTests.Step(12, () -> {
            sp.attack(victim[0]);
            h.assertTrue(victim[0].isRemoved() || !victim[0].isAlive(), "night: mob fell");
            h.assertTrue(JobTraits3.veilOn(sp) && JobTraits3.againCount(sp) == 1 && sp.hasEffect(MobEffects.INVISIBILITY), "kill → stealth again (1)");
        }));
        // 다시 은신 3초가 지나면 풀림 (태세는 남음)
        steps.add(new RpgJobTests.Step(66, () -> {
            h.assertTrue(!JobTraits3.veilOn(sp) && JobTraits.stanceOn(sp, "trickster_night"), "re-stealth ends (stance stays)");
            // 개기일식: 비수 넷 (나 + 환영 셋) × 1.25 × 네 번 (비수마다 표식 +1 → 7) → 해가 뜰 때 표식 7 → 4 + 2 × 7 (= 38)
            JobTraits.testEndStance(sp);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 3.0));
            RpgJobTests.clearStrays(h, z.get(0));
            JobSkills.resetCooldowns(sp);
            h.assertTrue(JobSkills.press(sp, 4), "개기일식 cast");
        }));
        steps.add(new RpgJobTests.Step(30, () -> h.assertTrue(JobTraits3.marks(sp, z.get(0)) == 7, "eclipse daggers fill marks 7: " + JobTraits3.marks(sp, z.get(0)))));
        steps.add(new RpgJobTests.Step(36, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(4), 38);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "개기일식 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits3.marks(sp, z.get(0)) == 0, "dawn bursts all marks");
        }));
        RpgJobTests.chain(h, steps, 0, () -> {
            RpgCombat.forceCrit = null;
            RpgJobTests.cleanup(sp);
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }

    // ------------------------------------------------------------ 료스알프(블레이드 댄서)

    @GameTest(environment = ENV_BD, maxTicks = 760)
    public void bladedancerSkills(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = RpgJobTests.jobPlayer(h, "bladedancer", Map.of("dex", 50), 50);
        Weapon w = Weapons.weaponFrom("dagger", 0, "common");
        RpgJobTests.hold(sp, w);
        Jobs.Job j = Jobs.job("bladedancer");
        List<Husk> z = new ArrayList<>(List.of(dummy(h, 0, 2.0)));
        List<RpgJobTests.Step> steps = new ArrayList<>();
        double[] mark = {0, 0};
        // 춤사위: 단검 평타 (처음 — 쌓이지 않음) → 직업 스킬이 맞음 → +1 (이동 +4%)
        steps.add(new RpgJobTests.Step(1, () -> {
            home(h, sp);
            RpgJobTests.clearStrays(h, z.get(0));
            RpgCombat.forceCrit = false;
            JobTraits10.setDance(sp, 0);
            double base = RpgJobTests.basic(sp, w);
            sp.attack(z.get(0));
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), base), "first basic (no dance) " + TestDummies.dealt(z.get(0)) + " vs " + base);
            h.assertTrue(JobTraits10.dance(sp) == 0 && JobTraits10.lastKind(sp) == 1, "basic first: dance 0 · last basic");
            // 빛살 베기: 돌며 벰 2.0 (스킬 쪽 → 춤사위 1) → 앞을 크게 벰 2.75 × 1.06
            z.get(0).discard();
            z.set(0, dummy(h, 0, 2.5));
            RpgJobTests.clearStrays(h, z.get(0));
            h.assertTrue(JobSkills.press(sp, 0), "빛살 베기 cast");
        }));
        steps.add(new RpgJobTests.Step(4, () -> h.assertTrue(JobTraits10.dance(sp) == 1 && Math.abs(JobTraits10.speedBonus(sp) - 0.04) < 1e-9,
                "basic → skill: dance 1 · speed +4%: " + JobTraits10.dance(sp) + " · " + JobTraits10.speedBonus(sp))));
        steps.add(new RpgJobTests.Step(12, () -> {
            double e = RpgJobTests.expected(sp, j, j.skill(0), 1);
            double want = e * 2.0 + e * 2.75 * 1.06;
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "빛살 베기 (spin + slash × dance 1) " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(RpgJobTests.gradientNumber(h, z.get(0), j), "bladedancer gradient number");
            // 평타 (춤사위 1 → × 1.06) → 춤사위 2 (이동 +8%)
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2.0));
            RpgJobTests.clearStrays(h, z.get(0));
            double base = RpgJobTests.basic(sp, w);
            sp.attack(z.get(0));
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), base * 1.06), "basic × dance 1 " + TestDummies.dealt(z.get(0)) + " vs " + base * 1.06);
            h.assertTrue(JobTraits10.dance(sp) == 2 && Math.abs(JobTraits10.speedBonus(sp) - 0.08) < 1e-9, "skill → basic: dance 2 · speed +8%");
        }));
        // 3초 동안 아무것도 맞히지 못하면 0
        steps.add(new RpgJobTests.Step(70, () -> {
            h.assertTrue(JobTraits10.dance(sp) == 0 && JobTraits10.speedBonus(sp) == 0, "dance fades after 3s: " + JobTraits10.dance(sp));
            // 요정의 걸음: 옆으로 스텝 셋 (0.95 × 3) · 받는 피해 −30% (1.5초)
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2.5));
            RpgJobTests.clearStrays(h, z.get(0));
            JobTraits10.setDance(sp, 0);
            mark[0] = sp.position().x;
            h.assertTrue(JobSkills.press(sp, 1), "요정의 걸음 cast");
            h.assertTrue(Math.abs(JobTraits10.guarding(sp) - 0.3) < 1e-9, "fairy step guard 0.3: " + JobTraits10.guarding(sp));
            float hp = sp.getHealth();
            mark[1] = hp - RpgHealth.onLoss(sp, sp.damageSources().mobAttack(z.get(0)), hp, hp - 4F);
        }));
        steps.add(new RpgJobTests.Step(36, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(1), 2.85);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "요정의 걸음 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            h.assertTrue(JobTraits10.guarding(sp) == 0, "guard ends");
            float hp = sp.getHealth();
            double loss = hp - RpgHealth.onLoss(sp, sp.damageSources().mobAttack(z.get(0)), hp, hp - 4F);
            h.assertTrue(loss > 0 && Math.abs(mark[1] - loss * 0.7) < 1e-3, "step guard −30%: " + mark[1] + " vs " + loss * 0.7);
            // 칼날 원무: 제자리에서 2.85 × 3
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2.0));
            RpgJobTests.clearStrays(h, z.get(0));
            JobTraits10.setDance(sp, 0);
            h.assertTrue(JobSkills.press(sp, 2), "칼날 원무 cast");
        }));
        steps.add(new RpgJobTests.Step(26, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(2), 8.55);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "칼날 원무 " + TestDummies.dealt(z.get(0)) + " vs " + want);
            // 눈부신 한낮: 태세 — 단검 평타 두 번 (두 번째 42%) · 춤사위가 줄지 않음
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 2.0));
            RpgJobTests.clearStrays(h, z.get(0));
            JobTraits10.setDance(sp, 0);
            h.assertTrue(JobSkills.press(sp, 3), "눈부신 한낮 cast");
            h.assertTrue(JobTraits.stanceOn(sp, "bladedancer_noon"), "noon stance");
        }));
        steps.add(new RpgJobTests.Step(4, () -> {
            double base = RpgJobTests.basic(sp, w);
            sp.attack(z.get(0));
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), base * 1.42), "noon echo " + TestDummies.dealt(z.get(0)) + " vs " + base * 1.42);
            JobTraits10.setDance(sp, 3);
        }));
        steps.add(new RpgJobTests.Step(70, () -> {
            h.assertTrue(JobTraits10.dance(sp) == 3, "noon keeps the dance: " + JobTraits10.dance(sp));
            // 빛무리 춤: 2.5 × 8 + 18
            JobTraits.testEndStance(sp);
            z.get(0).discard();
            home(h, sp);
            z.set(0, dummy(h, 0, 3.0));
            RpgJobTests.clearStrays(h, z.get(0));
            JobTraits10.setDance(sp, 0);
            JobSkills.resetCooldowns(sp);
            h.assertTrue(JobSkills.press(sp, 4), "빛무리 춤 cast");
        }));
        steps.add(new RpgJobTests.Step(60, () -> {
            double want = RpgJobTests.expected(sp, j, j.skill(4), 38);
            h.assertTrue(RpgJobTests.near(TestDummies.dealt(z.get(0)), want), "빛무리 춤 " + TestDummies.dealt(z.get(0)) + " vs " + want);
        }));
        RpgJobTests.chain(h, steps, 0, () -> {
            RpgCombat.forceCrit = null;
            RpgJobTests.cleanup(sp);
            h.assertTrue(JobTraits10.dance(sp) == 0 && JobTraits10.speedBonus(sp) == 0, "dance cleared on job change");
            for (Husk x : z) x.discard();
            RpgGearTests.done(h, sp);
        });
    }
}
