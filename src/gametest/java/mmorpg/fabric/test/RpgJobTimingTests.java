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
import mmorpg.fabric.StatTrees;
import mmorpg.fabric.job.JobSkills;
import mmorpg.fabric.job.JobSystem;
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
 */
public final class RpgJobTimingTests {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-job-timing");
    /** 마지막 피해 뒤 이만큼 더 피해가 없으면 끝 · 아무 피해가 없으면 이만큼 · 한 스킬을 재는 가장 긴 틱 */
    private static final int QUIET = 100;
    private static final int NONE = 200;
    private static final int MAX = 400;
    /** 과녁 거리 (앞 칸 — 없으면 2.5): 멀리 내려앉는 스킬 */
    private static final Map<String, Double> AHEAD = Map.of("lancer_vault", 7.0, "spellblade_blink", 6.0, "mage_phase", 1.2);

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
    }

    private static void probe(GameTestHelper h, String job) {
        for (int x = -2; x < 12; x++) for (int z = -2; z < 14; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        ServerPlayer sp = RpgGearTests.player(h);
        sp.setExperienceLevels(40);
        RpgPlayers.checkLevelUp(sp);
        for (String s : new String[] {"str", "dex", "vit", "int"}) RpgPlayers.setStat(sp, s, 120);
        Jobs.Job j = Jobs.job(job);
        sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RpgItems.byId(Weapons.weaponId(j.weapons().get(0), 40, "transcend"))));
        JobSystem.setJob(sp, job);
        Probe p = new Probe();
        for (Jobs.Skill k : j.skills()) if (k.total() > 0) p.skills.add(k);
        h.assertTrue(!p.skills.isEmpty(), job + " damage skills");
        h.succeedWhen(() -> {
            long now = h.getTick();
            if (now < 3) throw h.assertionException("loading");
            if (!p.measuring) {
                if (p.idx >= p.skills.size()) {
                    JobSkills.resetCooldowns(sp);
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
                p.first = -1;
                p.last = -1;
                p.hits.clear();
                p.prev = 0;
                p.measuring = true;
                throw h.assertionException("measuring");
            }
            int t = (int) (now - p.t0);
            double d = TestDummies.dealt(p.z);
            if (d > p.prev + 1e-9) {
                if (p.first < 0) p.first = t;
                p.last = t;
                if (p.hits.size() < 80) p.hits.add(t);
                p.prev = d;
            }
            boolean done = t >= MAX || (p.first >= 0 && t - p.last >= QUIET) || (p.first < 0 && t >= NONE) || !p.cast && t >= 5;
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
}
