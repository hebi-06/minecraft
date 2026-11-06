package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mmorpg.core.Jobs;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.job.JobSkills;
import mmorpg.fabric.job.JobSystem;
import mmorpg.fabric.stattree.StatTrees;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.50.0 직업 스킬 시간 보정 (사용자 "룬에서 계수 바꿨던 거처럼 유틸에 따라서 계수 바꾸는걸 똑같이" — 선딜 · 지속은 룬과 같은 규칙): 직업마다 피해 스킬을 차례로
 * 앞 과녁 하나에 써서, 쓴 틱부터 첫 피해 · 마지막 피해까지의 틱을 잼 (과녁이 밀려나지 않게 밀쳐냄 저항 1). 결과는 로그 [job-timing] (CI 의 job-timing.txt).
 * 확인: 피해가 들어가고, 다 맞은 스킬의 마지막 피해 틱 = jobs.json 의 t (±3틱 — 몇 타가 빗나간 스킬은 t + 3 안), 첫 피해 틱 = w (선딜 — ±3틱, w 가 없으면
 * 8틱 안 · w 0 을 적은 스킬은 빼고), 들어간 피해 ≤ 위력 × 배율 합 × 피해 배율 (Jobs.damageScale) × 스탯 트리 공격력 × 가호.
 * 0.51.0 다시 쓰는 스킬 (JobRecast — 핏빛 대검 세 번 · 다시 벼린 칼의 검풍) 은 창이 열려 있는 동안 RECAST 틱마다 다시 누름 (받아 줄 때까지).
 */
public final class RpgJobTimingTests {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-job-timing");
    /** 마지막 피해 뒤 이만큼 더 피해가 없으면 끝 · 아무 피해가 없으면 이만큼 · 한 스킬을 재는 가장 긴 틱 */
    private static final int QUIET = 100;
    private static final int NONE = 200;
    private static final int MAX = 400;
    /** 0.51.0 재설계안 2판: 첫 피해와 마지막 피해 사이가 QUIET 보다 긴 스킬 (홀름강 — 10 에 열고 140 에 내려찍음) */
    private static final Map<String, Integer> QUIET_OF = Map.of("gladiator_ult", 160);
    /** 과녁 거리 (앞 칸 — 없으면 2.5): 멀리 내려앉는 스킬 */
    // 0.50.0 시험 1: 장대 도약 과녁을 7칸에 두었더니 시험 자리 둘레 barrier 벽 속 (바닥 8×8) 이라 숨막힘 피해가 10틱마다 들어갔음 → 5.5칸
    // 2026-11-06 미라주 대시 (트릭스터 2판): 6칸 뛰어들어 내려선 자리 둘레를 벰 — 과녁을 내려설 자리에
    // 2026-11-06 크로스 게일 (스톰 랜서): 두 바람 창이 엇갈리는 창끝 (6.5칸 — 벽 앞) 에 과녁 · 셋 다 맞음
    private static final Map<String, Double> AHEAD = Map.of("lancer_vault", 5.5, "spellblade_blink", 6.0, "mage_phase", 1.2, "trickster_dash", 6.0, "stormlancer_cross", 6.0);

    private static Husk dummy(GameTestHelper h, double ahead) {
        Husk m = TestDummies.make(h.spawnWithNoFreeWill(EntityTypes.HUSK, 1.5F, 2F, (float) (1.5 + ahead)));
        m.setYRot(180F);
        m.setYHeadRot(180F);
        m.setYBodyRot(180F);
        m.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 600, 0, false, false));
        return m;
    }

    private static void clearStrays(GameTestHelper h, net.minecraft.world.entity.Entity... keep) {
        var box = new net.minecraft.world.phys.AABB(h.absoluteVec(new Vec3(-14, -4, -14)), h.absoluteVec(new Vec3(30, 12, 30)));
        java.util.Set<net.minecraft.world.entity.Entity> own = java.util.Set.of(keep);
        for (net.minecraft.world.entity.Mob m : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box,
                e -> !own.contains(e) && e instanceof net.minecraft.world.entity.monster.Enemy)) {
            m.discard();
        }
    }

    /** 이 직업의 피해 스킬을 차례로 재는 상태 */
    private static final class Probe {
        final List<Jobs.Skill> skills = new ArrayList<>();
        int idx;
        boolean measuring;
        final List<String> failures = new ArrayList<>();
        long t0;
        int first = -1;
        int last = -1;
        final List<Integer> hits = new ArrayList<>();
        double prev;
        double want;
        boolean cast;
        Husk z;
        /** 0.51.0 다시 쓰는 스킬 (JobRecast): 마지막으로 누른 틱 (쓴 틱부터) */
        int lastPress;
    }

    /** 0.51.0 다시 쓰는 스킬 (핏빛 대검 · 다시 벼린 칼): 창이 열려 있으면 앞 누름에서 이만큼 뒤부터 다시 누름 (받아 줄 때까지 — 스킬의 gap) — jobs.json t 와 같은 셈 */
    static final int RECAST = 12;

    private static void probe(GameTestHelper h, String job) {
        for (int x = -2; x < 12; x++) for (int z = -2; z < 14; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        ServerPlayer sp = RpgGearTests.player(h);
        sp.setExperienceLevels(40);
        RpgPlayers.checkLevelUp(sp);
        for (String s : new String[] {"str", "dex", "vit", "int"}) RpgPlayers.setStat(sp, s, 120);
        Jobs.Job j = Jobs.job(job);
        sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RpgItems.byId(Weapons.weaponId(j.weapons().get(0), 40, "transcend"))));
        JobSystem.setJob(sp, job);
        // 0.51.0 네크로맨서: 망자 (궁극기의 선원) 가 스킬 뒤에도 과녁을 쳐 마지막 피해 틱이 밀리지 않게 — 망자는 움직이되 치지 않음
        if (job.equals("necromancer")) mmorpg.fabric.job.NecroCrew.PASSIVE.add(sp.getUUID());
        Probe p = new Probe();
        for (Jobs.Skill k : j.skills()) if (k.total() > 0) p.skills.add(k);
        h.assertTrue(!p.skills.isEmpty(), job + " damage skills");
        h.succeedWhen(() -> {
            long now = h.getTick();
            if (now < 3) throw h.assertionException("loading");
            if (!p.measuring) {
                if (p.idx >= p.skills.size()) {
                    JobSkills.resetCooldowns(sp);
                    mmorpg.fabric.job.NecroCrew.PASSIVE.remove(sp.getUUID());
                    JobSystem.setJob(sp, null);
                    h.getLevel().getServer().getPlayerList().remove(sp);
                    h.assertTrue(p.failures.isEmpty(), job + " timing: " + String.join(" / ", p.failures));
                    return;
                }
                Jobs.Skill k = p.skills.get(p.idx);
                Vec3 at = h.absoluteVec(new Vec3(1.5, 2, 1.5));
                sp.snapTo(at.x, at.y, at.z, 0F, 0F);
                sp.setDeltaMovement(Vec3.ZERO);
                sp.removeAllEffects();
                RpgHealth.healFull(sp);
                JobSkills.resetCooldowns(sp);
                mmorpg.fabric.rune.RuneMarks.testClear(); // 앞 스킬의 가하는 피해 증가 (발키리의 가호) 가 다음 스킬에 남지 않게
                p.z = dummy(h, AHEAD.getOrDefault(k.id(), 2.5));
                clearStrays(h, p.z);
                sp.lookAt(EntityAnchorArgument.Anchor.EYES, p.z.getEyePosition());
                p.want = JobSystem.power(sp, j) * k.total() * Jobs.damageScale(k) * StatTrees.atkMult(sp) * RpgCombat.dealtMult.applyAsDouble(sp);
                p.cast = JobSkills.press(sp, k.slot());
                p.t0 = now;
                p.lastPress = 0;
                p.first = -1;
                p.last = -1;
                p.hits.clear();
                p.prev = 0;
                p.measuring = true;
                throw h.assertionException("measuring");
            }
            int t = (int) (now - p.t0);
            Jobs.Skill kk = p.skills.get(p.idx);
            if (p.cast && mmorpg.fabric.job.JobRecast.pending(sp, kk.id()) && t - p.lastPress >= RECAST && JobSkills.press(sp, kk.slot())) p.lastPress = t;
            double d = TestDummies.dealtBy(p.z);
            if (d > p.prev + 1e-9) {
                if (p.first < 0) p.first = t;
                p.last = t;
                if (p.hits.size() < 80) p.hits.add(t);
                p.prev = d;
            }
            boolean done = t >= MAX || (p.first >= 0 && t - p.last >= QUIET_OF.getOrDefault(kk.id(), QUIET)) || (p.first < 0 && t >= NONE) || !p.cast && t >= 5;
            if (!done) throw h.assertionException("measuring " + t);
            Jobs.Skill k = p.skills.get(p.idx);
            LOG.info("[job-timing] {} {} {} cast={} first={} last={} dealt={} want={} hits={}", job, k.slot(), k.id(), p.cast, p.first, p.last,
                    String.format(Locale.ROOT, "%.1f", p.prev), String.format(Locale.ROOT, "%.1f", p.want), p.hits);
            String id = k.id();
            boolean full = p.want > 0 && p.prev >= p.want * 0.99;
            if (!p.cast) p.failures.add(id + " not cast");
            else if (!(p.prev > 0)) p.failures.add(id + " no damage");
            else {
                int want = (int) k.def().num("t");
                int w = (int) k.def().numOr("w", 0);
                if (full && Math.abs(p.last - want) > 3) p.failures.add(id + " last " + p.last + " vs t " + want);
                else if (!full && p.last > want + 3) p.failures.add(id + " (partial) last " + p.last + " > t " + want);
                if (w >= 6 && Math.abs(p.first - w) > 3) p.failures.add(id + " first " + p.first + " vs w " + w);
                if (!k.def().has("w") && p.first >= 8) p.failures.add(id + " first " + p.first + " but no w (windup)");
            }
            p.z.discard();
            p.idx++;
            p.measuring = false;
            throw h.assertionException("next");
        });
    }

    @GameTest(environment = "mmorpg:jt_sw", maxTicks = 2400)
    public void timingSwordsman(GameTestHelper h) {
        probe(h, "swordsman");
    }

    @GameTest(environment = "mmorpg:jt_wa", maxTicks = 2400)
    public void timingWarrior(GameTestHelper h) {
        probe(h, "warrior");
    }

    @GameTest(environment = "mmorpg:jt_sb", maxTicks = 2400)
    public void timingSpellblade(GameTestHelper h) {
        probe(h, "spellblade");
    }

    @GameTest(environment = "mmorpg:jt_mg", maxTicks = 2400)
    public void timingMage(GameTestHelper h) {
        probe(h, "mage");
    }

    @GameTest(environment = "mmorpg:jt_ar", maxTicks = 2400)
    public void timingArcher(GameTestHelper h) {
        probe(h, "archer");
    }

    @GameTest(environment = "mmorpg:jt_as", maxTicks = 2400)
    public void timingAssassin(GameTestHelper h) {
        probe(h, "assassin");
    }

    @GameTest(environment = "mmorpg:jt_la", maxTicks = 2400)
    public void timingLancer(GameTestHelper h) {
        probe(h, "lancer");
    }

    @GameTest(environment = "mmorpg:jt_re", maxTicks = 2400)
    public void timingReaper(GameTestHelper h) {
        probe(h, "reaper");
    }

    @GameTest(environment = "mmorpg:jt_pa", maxTicks = 2400)
    public void timingPaladin(GameTestHelper h) {
        probe(h, "paladin");
    }

    @GameTest(environment = "mmorpg:jt_pr", maxTicks = 2400)
    public void timingPriest(GameTestHelper h) {
        probe(h, "priest");
    }

    @GameTest(environment = "mmorpg:jt_gu", maxTicks = 2400)
    public void timingGuardian(GameTestHelper h) {
        probe(h, "guardian");
    }

    /** 0.51.0 2차 직업 — 검사 각성 소드마스터 */
    @GameTest(environment = "mmorpg:jt_sm", maxTicks = 2400)
    public void timingSwordmaster(GameTestHelper h) {
        probe(h, "swordmaster");
    }

    /** 0.51.0 2차 직업 — 검사 반전 블러드 나이트 (스킬마다 체력을 바쳐 +10% — 다 맞은 것으로 셈) */
    @GameTest(environment = "mmorpg:jt_bk", maxTicks = 2400)
    public void timingBloodknight(GameTestHelper h) {
        probe(h, "bloodknight");
    }

    /** 0.51.0 2차 직업 — 전사 각성 워로드 */
    @GameTest(environment = "mmorpg:jt_wl", maxTicks = 2400)
    public void timingWarlord(GameTestHelper h) {
        probe(h, "warlord");
    }

    /**
     * 0.51.0 2차 직업 — 전사 반전 글래디에이터 (재설계안 2판 — 결투가 앞 과녁에서 다음 과녁으로 옮겨가 더 들어감: 다 맞은 것으로 셈 · 홀름강은 결투장
     * 받는 피해 +15%)
     */
    @GameTest(environment = "mmorpg:jt_gl", maxTicks = 2400)
    public void timingGladiator(GameTestHelper h) {
        probe(h, "gladiator");
    }

    /**
     * 0.51.0 2차 직업 — 마검사 각성 아케인 블레이드 (룬 스미스 대신: 크레센트 엣지는 가로 검기 7 → 세로 검기 17, 팬텀 크로스 1 → 12, 아케인 템페스트 8 → 20 · 월드 클리버는
     * 내려침 21 → 솟구침 31)
     */
    @GameTest(environment = "mmorpg:jt_ab", maxTicks = 2400)
    public void timingArcaneblade(GameTestHelper h) {
        probe(h, "arcaneblade");
    }

    /**
     * 0.51.0 2차 직업 — 마검사 반전 소드 콜러 (재설계안 2판: 베어라 3 → 13, 데려가라는 꽂힌 칼이 없어 앞 8칸 · 지나가며 3, 꿰뚫고 맴돌아라 3 → 43 ·
     * 칼날 감옥 10 → 70)
     */
    @GameTest(environment = "mmorpg:jt_sc", maxTicks = 2400)
    public void timingSwordcaller(GameTestHelper h) {
        probe(h, "swordcaller");
    }

    /** 0.51.0 2차 직업 — 매지션 각성 아크메이지 (스킬 사이 5초 — 연쇄 · 별 없이, 별무리 마탄은 과녁 하나라 튀는 별빛 없음) */
    @GameTest(environment = "mmorpg:jt_am", maxTicks = 2400)
    public void timingArcmage(GameTestHelper h) {
        probe(h, "arcmage");
    }

    /** 0.51.0 2차 직업 — 매지션 반전 네크로맨서 (망자가 없어 자리 바꾸기는 뼈 허수아비 · 시체 폭발은 땅 속 망자, 망자는 치지 않음) */
    @GameTest(environment = "mmorpg:jt_nc", maxTicks = 2400)
    public void timingNecromancer(GameTestHelper h) {
        probe(h, "necromancer");
    }

    /**
     * 0.51.0 2차 직업 — 궁수 각성 스나이퍼 (재설계안 2판: 하트시커는 12틱에 다시 눌러 일부 — 13 에 한 발, 트릭 샷은 과녁 하나라 같은 과녁에게 10틱마다 두 번 더 — 24,
     * 데드아이는 12틱마다 다시 눌러 13 · 25 · 37)
     */
    @GameTest(environment = "mmorpg:jt_sn", maxTicks = 2400)
    public void timingSniper(GameTestHelper h) {
        probe(h, "sniper");
    }

    /** 0.51.0 2차 직업 — 궁수 반전 배틀 아처 (재설계안 2판 — 섀도 헌터 대신: 과녁이 2.5칸 앞이라 난사의 가까운 적 +20% — 다 들어감 · 호넷 스웜 7 · 스플린터 샷 6 · 퀴버 스톰 70) */
    @GameTest(environment = "mmorpg:jt_ba", maxTicks = 2400)
    public void timingBattlearcher(GameTestHelper h) {
        probe(h, "battlearcher");
    }

    /**
     * 0.51.0 2차 직업 — 암살자 각성 트릭스터 (재설계안 2판 — 2026-11-06: 섀도 스와프는 12틱에 다시 눌러 14 에 찌름 · 표식 1 이라 일부, 미라주 대시는 과녁을 내려설 자리 (6칸)
     * 에 — 4 에 벰 · 12 에 다시 눌러 돌아옴, 사우전드 팬텀 4 ~ 34, 와일드파이어 피스트는 표식 4 라 일부 — 62 에 터짐)
     */
    @GameTest(environment = "mmorpg:jt_tr", maxTicks = 2400)
    public void timingTrickster(GameTestHelper h) {
        probe(h, "trickster");
    }

    /**
     * 2026-11-06 2차 직업 — 암살자 반전 피에로 (블레이드 댄서 대신: 잭 인 더 박스는 상자의 칼 10 ~ 50 · 핸드스프링 8 · 벌룬 퍼레이드 14 · 24 · 할루시네이트는 인형의
     * 뿅망치 20 ~ 200 · 그랜드 피날레 10 ~ 90 — 소환물이 과녁을 노리지만 과녁은 움직이지 않음)
     */
    @GameTest(environment = "mmorpg:jt_pi", maxTicks = 2400)
    public void timingPierrot(GameTestHelper h) {
        probe(h, "pierrot");
    }

    /**
     * 0.51.0 2차 직업 — 창술사 각성 스톰 랜서 (2026-11-06: 크로스 게일 2 · 6 → 엇갈림 26 (과녁은 엇갈리는 6칸 앞), 게일 드라이브 지나감 → 12틱 다시 누름 → 14, 템페스트 드릴
     * 6 ~ 36, 렌딩 게일 — 박힌 창 둘 → 12틱 다시 눌러 14에 뽑음 (박은 창만큼 · w 0), 아이 오브 더 스톰 14 ~ 70)
     */
    @GameTest(environment = "mmorpg:jt_sl", maxTicks = 2400)
    public void timingStormlancer(GameTestHelper h) {
        probe(h, "stormlancer");
    }

    /**
     * 0.51.0 2차 직업 — 창술사 반전 드라군 (웜테일 6 · 18, 하이 점프 — 12틱 다시 눌러 17에 내리꽂음 (w 15), 임페일 6 · 20, 드래곤 브레스 14, 드래곤폴 46 · 56 — 시험 자리
     * 천장이 낮아 높이 덤은 조금)
     */
    @GameTest(environment = "mmorpg:jt_dg", maxTicks = 2400)
    public void timingDragoon(GameTestHelper h) {
        probe(h, "dragoon");
    }

    /**
     * 0.51.0 2차 직업 — 사신 각성 소울 리퍼 (2026-11-06: 리핑 크레센트 4 · 14, 엄브럴 트레스패스 몸속 → 18, 바운티풀 하베스트 10 ~ 40 → 50, 랜턴 오브 소울즈 사슬 8 ~ 152 (영혼
     * 15 에서 해방 — 망령이 그 사이에 박힘), 페일 하베스트 20 · 30 → 46)
     */
    @GameTest(environment = "mmorpg:jt_sr", maxTicks = 2400)
    public void timingSoulreaper(GameTestHelper h) {
        probe(h, "soulreaper");
    }

    /**
     * 0.51.0 2차 직업 — 사신 반전 엑시큐셔너 (컨뎀 6, 섀도 스토크 8 (건너편에 내려섬), 길로틴 22, 비포 선셋 — 10 순간 이동 → 12 · 22 (과녁이 쓰러지지 않아 다시 쓰기 없음))
     */
    @GameTest(environment = "mmorpg:jt_ex", maxTicks = 2400)
    public void timingExecutioner(GameTestHelper h) {
        probe(h, "executioner");
    }

    /**
     * 2026-11-06 2차 직업 — 성기사 각성 썬더로드 (아크 스매시 6 · 16 (갈래 — 다른 적이 없어 그 과녁에 다시 내리꽂힘), 썬더스텝 6 (벼락으로 내리꽂힘), 스톰 스파이어
     * 16 · 26, 던브레이커 10 · 30 · 46 — 썬더헤드는 피해 없는 태세)
     */
    @GameTest(environment = "mmorpg:jt_tl", maxTicks = 2400)
    public void timingThunderlord(GameTestHelper h) {
        probe(h, "thunderlord");
    }

    /**
     * 2026-11-06 2차 직업 — 성기사 반전 타이런트 (필리지 8, 섀클 볼트 10 (4 ~ 10 끌려감), 킹스 그립 10 · 20 · 30 · 40 (과녁은 밀 수 없어 들지 않음), 스톨른 썬더
     * 10 · 20 · 30 · 44 — 빼앗은 힘이 쌓여 들어간 피해는 쓴 때의 배율 이상)
     */
    @GameTest(environment = "mmorpg:jt_ty", maxTicks = 2400)
    public void timingTyrant(GameTestHelper h) {
        probe(h, "tyrant");
    }
}
