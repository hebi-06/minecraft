package mmorpg.fabric.test;

import java.lang.reflect.Method;
import mmorpg.core.RuneBreak;
import mmorpg.core.Runes;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.rune.RuneCast;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 0.25.0 두 스탯 룬 계열 여섯 (해류 · 골렘 · 무투 · 사냥꾼 · 화산 · 투사) 게임 시험 (서버): 30종을 실제로 써서 앞 3칸에 선 과녁 하나에 들어간 피해 =
 * 위력 P × 총 배율 (일반 4 · 희귀 7 · 고귀 12 · 영웅 20 · 초월 32 — 같은 적을 여러 번 · 화상 · 마무리가 겹쳐도 몹 무적 시간을 피해 모두), 초월 여섯은 6돌도
 * (P × 돌파 계수 × 50). 적용 스탯 = 주 스탯 + 보조 스탯 × 0.5 (네 스탯 120 → 180). 사거리 · 범위가 넓어 시험마다 시험 환경을 따로 (mmorpg:pr_…).
 */
public final class RpgPairRuneTests {
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
        return sp;
    }

    private static void floor(GameTestHelper h, int n) {
        for (int x = 0; x < n; x++) for (int z = 0; z < n; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }

    /** 앞 묶음의 시험이 남긴 적대 몹을 치움 (범위 안 적 모두를 치는 룬이 남의 몹까지 세지 않게 — RpgLimitedRuneTests 와 같음) */
    private static void clearStrays(GameTestHelper h, net.minecraft.world.entity.Entity... keep) {
        var box = new net.minecraft.world.phys.AABB(h.absoluteVec(new Vec3(-14, -4, -14)), h.absoluteVec(new Vec3(30, 12, 30)));
        java.util.Set<net.minecraft.world.entity.Entity> own = java.util.Set.of(keep);
        for (net.minecraft.world.entity.Mob m : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box,
                e -> !own.contains(e) && e instanceof net.minecraft.world.entity.monster.Enemy)) {
            m.discard();
        }
    }

    /** 쓰러지지 않는 과녁 허스크 (불에 타지 않음 — 받은 피해는 TestDummies.dealt) */
    private static Husk dummy(GameTestHelper h, double x, double y, double z) {
        Husk m = TestDummies.make(h.spawnWithNoFreeWill(EntityTypes.HUSK, (float) x, (float) y, (float) z));
        m.setYRot(180F);
        m.setYHeadRot(180F);
        m.setYBodyRot(180F);
        m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60, 0, false, false));
        return m;
    }

    /**
     * family 의 grade 룬을 breakLevel 돌로 씀 (네 스탯 120 — 적용 스탯 = 주 + 보조 × 0.5 = 180) → wait 틱 뒤 앞 3칸 과녁의 피해 = P × 그 단계의 총 배율
     */
    private static void cast(GameTestHelper h, String family, String grade, int breakLevel, int wait) {
        floor(h, 10);
        ServerPlayer sp = player(h);
        sp.setExperienceLevels(40);
        RpgPlayers.checkLevelUp(sp);
        for (String s : new String[] {"str", "dex", "vit", "int"}) RpgPlayers.setStat(sp, s, 120);
        Husk z = dummy(h, 1.5, 2, 4.5);
        Runes.Rune r = Runes.parseRune(Runes.runeId(family, grade));
        h.assertTrue(r != null && Runes.PAIR_FAMILIES.contains(family), family + " " + grade + " pair rune");
        h.runAfterDelay(2, () -> {
            clearStrays(h, z);
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
            h.assertTrue(Math.abs(RuneCast.applied(sp, family).value() - 180) < 1e-6, family + " applied = 120 + 120 × 0.5: " + RuneCast.applied(sp, family).value());
            double P = RuneCast.power(sp, family) * RuneBreak.coef(breakLevel);
            double total = Runes.runeTotalMult(RuneBreak.def(r, breakLevel));
            h.assertTrue(RuneCast.castRune(sp, r, false, breakLevel), family + " " + grade + " cast");
            h.runAfterDelay(wait, () -> {
                double dealt = TestDummies.dealt(z);
                h.assertTrue(Math.abs(dealt - total * P) < 1e-2 * P, family + " " + grade + " " + breakLevel + " dealt " + dealt + " vs " + total + " × " + P);
                h.getLevel().getServer().getPlayerList().remove(sp);
                h.succeed();
            });
        });
    }

    // ------------------------------------------------------------ 해류 (마력 + 체력)

    @GameTest(environment = "mmorpg:pr_tide_common", maxTicks = 60)
    public void tideCommon(GameTestHelper h) {
        cast(h, "tide", "common", 0, 25);
    }

    @GameTest(environment = "mmorpg:pr_tide_rare", maxTicks = 100)
    public void tideRare(GameTestHelper h) {
        cast(h, "tide", "rare", 0, 62);
    }

    @GameTest(environment = "mmorpg:pr_tide_noble", maxTicks = 110)
    public void tideNoble(GameTestHelper h) {
        cast(h, "tide", "noble", 0, 72);
    }

    @GameTest(environment = "mmorpg:pr_tide_hero", maxTicks = 90)
    public void tideHero(GameTestHelper h) {
        cast(h, "tide", "hero", 0, 48);
    }

    @GameTest(environment = "mmorpg:pr_tide_transcend", maxTicks = 140)
    public void tideTranscend(GameTestHelper h) {
        cast(h, "tide", "transcend", 0, 100);
    }

    @GameTest(environment = "mmorpg:pr_tide_t6", maxTicks = 160)
    public void tideTranscendSixStars(GameTestHelper h) {
        cast(h, "tide", "transcend", 6, 120);
    }

    // ------------------------------------------------------------ 골렘 (체력 + 마력)

    @GameTest(environment = "mmorpg:pr_golem_common", maxTicks = 60)
    public void golemCommon(GameTestHelper h) {
        cast(h, "golem", "common", 0, 25);
    }

    @GameTest(environment = "mmorpg:pr_golem_rare", maxTicks = 80)
    public void golemRare(GameTestHelper h) {
        cast(h, "golem", "rare", 0, 40);
    }

    @GameTest(environment = "mmorpg:pr_golem_noble", maxTicks = 90)
    public void golemNoble(GameTestHelper h) {
        cast(h, "golem", "noble", 0, 52);
    }

    @GameTest(environment = "mmorpg:pr_golem_hero", maxTicks = 130)
    public void golemHero(GameTestHelper h) {
        cast(h, "golem", "hero", 0, 88);
    }

    @GameTest(environment = "mmorpg:pr_golem_transcend", maxTicks = 150)
    public void golemTranscend(GameTestHelper h) {
        cast(h, "golem", "transcend", 0, 108);
    }

    @GameTest(environment = "mmorpg:pr_golem_t6", maxTicks = 180)
    public void golemTranscendSixStars(GameTestHelper h) {
        cast(h, "golem", "transcend", 6, 140);
    }

    // ------------------------------------------------------------ 무투 (민첩 + 힘)

    @GameTest(environment = "mmorpg:pr_monk_common", maxTicks = 80)
    public void monkCommon(GameTestHelper h) {
        cast(h, "monk", "common", 0, 42);
    }

    @GameTest(environment = "mmorpg:pr_monk_rare", maxTicks = 70)
    public void monkRare(GameTestHelper h) {
        cast(h, "monk", "rare", 0, 32);
    }

    @GameTest(environment = "mmorpg:pr_monk_noble", maxTicks = 60)
    public void monkNoble(GameTestHelper h) {
        cast(h, "monk", "noble", 0, 26);
    }

    @GameTest(environment = "mmorpg:pr_monk_hero", maxTicks = 100)
    public void monkHero(GameTestHelper h) {
        cast(h, "monk", "hero", 0, 62);
    }

    @GameTest(environment = "mmorpg:pr_monk_transcend", maxTicks = 140)
    public void monkTranscend(GameTestHelper h) {
        cast(h, "monk", "transcend", 0, 100);
    }

    @GameTest(environment = "mmorpg:pr_monk_t6", maxTicks = 160)
    public void monkTranscendSixStars(GameTestHelper h) {
        cast(h, "monk", "transcend", 6, 120);
    }

    // ------------------------------------------------------------ 사냥꾼 (민첩 + 체력)

    @GameTest(environment = "mmorpg:pr_hunter_common", maxTicks = 60)
    public void hunterCommon(GameTestHelper h) {
        cast(h, "hunter", "common", 0, 22);
    }

    @GameTest(environment = "mmorpg:pr_hunter_rare", maxTicks = 90)
    public void hunterRare(GameTestHelper h) {
        cast(h, "hunter", "rare", 0, 48);
    }

    @GameTest(environment = "mmorpg:pr_hunter_noble", maxTicks = 80)
    public void hunterNoble(GameTestHelper h) {
        cast(h, "hunter", "noble", 0, 44);
    }

    @GameTest(environment = "mmorpg:pr_hunter_hero", maxTicks = 110)
    public void hunterHero(GameTestHelper h) {
        cast(h, "hunter", "hero", 0, 68);
    }

    @GameTest(environment = "mmorpg:pr_hunter_transcend", maxTicks = 140)
    public void hunterTranscend(GameTestHelper h) {
        cast(h, "hunter", "transcend", 0, 98);
    }

    @GameTest(environment = "mmorpg:pr_hunter_t6", maxTicks = 160)
    public void hunterTranscendSixStars(GameTestHelper h) {
        cast(h, "hunter", "transcend", 6, 120);
    }

    // ------------------------------------------------------------ 화산 (힘 + 마력)

    @GameTest(environment = "mmorpg:pr_volcano_common", maxTicks = 120)
    public void volcanoCommon(GameTestHelper h) {
        cast(h, "volcano", "common", 0, 78);
    }

    @GameTest(environment = "mmorpg:pr_volcano_rare", maxTicks = 130)
    public void volcanoRare(GameTestHelper h) {
        cast(h, "volcano", "rare", 0, 85);
    }

    @GameTest(environment = "mmorpg:pr_volcano_noble", maxTicks = 170)
    public void volcanoNoble(GameTestHelper h) {
        cast(h, "volcano", "noble", 0, 128);
    }

    @GameTest(environment = "mmorpg:pr_volcano_hero", maxTicks = 130)
    public void volcanoHero(GameTestHelper h) {
        cast(h, "volcano", "hero", 0, 88);
    }

    @GameTest(environment = "mmorpg:pr_volcano_transcend", maxTicks = 150)
    public void volcanoTranscend(GameTestHelper h) {
        cast(h, "volcano", "transcend", 0, 104);
    }

    @GameTest(environment = "mmorpg:pr_volcano_t6", maxTicks = 250)
    public void volcanoTranscendSixStars(GameTestHelper h) {
        cast(h, "volcano", "transcend", 6, 205);
    }

    // ------------------------------------------------------------ 투사 (체력 + 민첩)

    @GameTest(environment = "mmorpg:pr_gladiator_common", maxTicks = 60)
    public void gladiatorCommon(GameTestHelper h) {
        cast(h, "gladiator", "common", 0, 20);
    }

    @GameTest(environment = "mmorpg:pr_gladiator_rare", maxTicks = 70)
    public void gladiatorRare(GameTestHelper h) {
        cast(h, "gladiator", "rare", 0, 32);
    }

    @GameTest(environment = "mmorpg:pr_gladiator_noble", maxTicks = 90)
    public void gladiatorNoble(GameTestHelper h) {
        cast(h, "gladiator", "noble", 0, 52);
    }

    @GameTest(environment = "mmorpg:pr_gladiator_hero", maxTicks = 140)
    public void gladiatorHero(GameTestHelper h) {
        cast(h, "gladiator", "hero", 0, 98);
    }

    @GameTest(environment = "mmorpg:pr_gladiator_transcend", maxTicks = 140)
    public void gladiatorTranscend(GameTestHelper h) {
        cast(h, "gladiator", "transcend", 0, 98);
    }

    @GameTest(environment = "mmorpg:pr_gladiator_t6", maxTicks = 160)
    public void gladiatorTranscendSixStars(GameTestHelper h) {
        cast(h, "gladiator", "transcend", 6, 118);
    }
}
