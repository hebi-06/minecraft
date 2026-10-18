package mmorpg.fabric.test;

import java.lang.reflect.Method;
import java.util.List;
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
 * 0.16.0 한정 초월 룬 셋 게임 시험 (서버): 만상귀일 · 칠성 강림 · 천공의 눈을 실제로 써서 과녁 하나에 들어간 피해 = 위력 P × 배율 합
 * (0돌 34 · 6돌 55 — 처음 설계 32 · 50 보다 5 ~ 12% 위, 몹 무적 시간을 피해 모두), P 의 적용 스탯 = 네 스탯의 합 (네 스탯 150 → 600 · 마력만 150 → 150),
 * 만상귀일은 스탯 비율로 구슬 몫을 나눔 (마력만 찍으면 마력 구슬 하나가 17 을 다), 타격 수 (구슬 넷 + 폭발 · 별 7 + 낙하 · 광선 6 + 폭발),
 * 천공의 눈은 시전 중 시전자 구속 (천천히 걸음). 0.17.0 무간참역: 쓴 자리 영역 (과녁 4.5칸 — 반지름 8 안) 베기 2 × 10 + 마무리 일섬 14 = 34 (6돌 2.5 × 14 + 20 = 55),
 * 타격 = 베기 10 (6돌 14) + 일섬 1.
 * 사거리 · 범위가 넓어 시험마다 시험 환경을 따로 (mmorpg:lr_…) — 같은 묶음에서 함께 도는 다른 시험의 과녁을 맞히지 않게.
 */
public final class RpgLimitedRuneTests {
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

    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
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
     * family 의 초월 룬을 breakLevel 돌로 씀 (스탯 str · dex · vit · int) → wait 틱 뒤 과녁의 피해 = P × total (P = 그 계열 위력 × 돌파 계수),
     * 타격 수 = hits (마지막 시전 기록)
     */
    private static void cast(GameTestHelper h, String family, int breakLevel, double[] stats, double total, int hits, int wait, boolean slowCheck) {
        floor(h);
        ServerPlayer sp = player(h);
        sp.setExperienceLevels(40);
        RpgPlayers.checkLevelUp(sp);
        String[] keys = {"str", "dex", "vit", "int"};
        for (int i = 0; i < 4; i++) RpgPlayers.setStat(sp, keys[i], (int) stats[i]);
        Husk z = dummy(h, 1.5, 2, 6.0);
        Runes.Rune r = Runes.parseRune(Runes.runeId(family, "transcend"));
        h.assertTrue(r != null && Runes.limited(family), family + " limited transcend rune");
        h.runAfterDelay(2, () -> {
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
            double sum = 0;
            for (int i = 0; i < 4; i++) sum += stats[i];
            double P = RuneCast.power(sp, family) * RuneBreak.coef(breakLevel);
            // 적용 스탯 = 네 스탯의 합 (찍은 스탯 — 시험 서버는 갑옷 · 버프 없음)
            h.assertTrue(Math.abs(RuneCast.applied(sp, family).value() - sum) < 1e-6, family + " applied = total " + RuneCast.applied(sp, family).value() + " vs " + sum);
            h.assertTrue(RuneCast.castRune(sp, r, false, breakLevel), family + " cast");
            if (slowCheck) {
                h.runAfterDelay(30, () -> h.assertTrue(sp.hasEffect(MobEffects.SLOWNESS), "caster slowed while the beam lasts"));
            }
            h.runAfterDelay(wait, () -> {
                double dealt = TestDummies.dealt(z);
                h.assertTrue(Math.abs(dealt - total * P) < 1e-2 * P, family + " " + breakLevel + " dealt " + dealt + " vs " + total + " × " + P);
                double[] last = lastOf(sp, r.def().str("fn"));
                h.assertTrue(last != null && (int) last[1] == hits, family + " hits " + (last == null ? "none" : (int) last[1]) + " vs " + hits);
                h.assertTrue(last != null && Math.abs(last[0] - dealt) < 1e-2 * P, family + " total text sum " + (last == null ? 0 : last[0]));
                h.getLevel().getServer().getPlayerList().remove(sp);
                h.succeed();
            });
        });
    }

    /** RuneLimited 의 마지막 시전 기록 (피해 합 · 타격 수) — 패키지 밖이라 리플렉션 */
    private static double[] lastOf(ServerPlayer sp, String fn) {
        try {
            Class<?> c = Class.forName("mmorpg.fabric.rune.RuneLimited");
            var f = c.getDeclaredField("LAST");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, double[]> m = (java.util.Map<String, double[]>) f.get(null);
            return m.get(sp.getUUID() + "|" + fn);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final double[] EVEN = {150, 150, 150, 150};

    @GameTest(environment = "mmorpg:lr_arcana", maxTicks = 160)
    public void arcanaUnity(GameTestHelper h) {
        cast(h, "arcana", 0, EVEN, 17 + 17, 5, 110, false);
    }

    @GameTest(environment = "mmorpg:lr_arcana_int", maxTicks = 160)
    public void arcanaUnityOneOrb(GameTestHelper h) {
        // 마력만: 마력 구슬 하나가 빛줄기 몫 17 을 다 (타격 = 빛줄기 하나 + 폭발)
        cast(h, "arcana", 0, new double[] {0, 0, 0, 150}, 17 + 17, 2, 90, false);
    }

    @GameTest(environment = "mmorpg:lr_arcana6", maxTicks = 160)
    public void arcanaUnitySixStars(GameTestHelper h) {
        cast(h, "arcana", 6, EVEN, 29 + 26, 5, 110, false);
    }

    @GameTest(environment = "mmorpg:lr_dipper", maxTicks = 180)
    public void sevenStars(GameTestHelper h) {
        cast(h, "dipper", 0, EVEN, 2 * 7 + 20, 8, 140, false);
    }

    @GameTest(environment = "mmorpg:lr_dipper6", maxTicks = 200)
    public void sevenStarsSixStars(GameTestHelper h) {
        cast(h, "dipper", 6, EVEN, 3.5 * 8 + 27, 9, 160, false);
    }

    @GameTest(environment = "mmorpg:lr_eye", maxTicks = 160)
    public void heavenEye(GameTestHelper h) {
        cast(h, "skyeye", 0, EVEN, 3 * 6 + 16, 7, 110, true);
    }

    @GameTest(environment = "mmorpg:lr_eye6", maxTicks = 170)
    public void heavenEyeSixStars(GameTestHelper h) {
        cast(h, "skyeye", 6, EVEN, 5 * 7 + 20, 8, 125, true);
    }

    @GameTest(environment = "mmorpg:lr_endless", maxTicks = 200)
    public void endlessSlash(GameTestHelper h) {
        cast(h, "endless", 0, EVEN, 2 * 10 + 14, 11, 150, false);
    }

    @GameTest(environment = "mmorpg:lr_endless6", maxTicks = 240)
    public void endlessSlashSixStars(GameTestHelper h) {
        cast(h, "endless", 6, EVEN, 2.5 * 14 + 20, 15, 190, false);
    }

    /** 0.18.0 대지 가르기: 대검 내려치기 16 + 갈라진 땅 여진 3 × 6 (과녁은 시전자 앞 4.5칸 — 균열 안), 6돌 27 + 3.5 × 8 */
    @GameTest(environment = "mmorpg:lr_terra", maxTicks = 200)
    public void earthSplitter(GameTestHelper h) {
        cast(h, "terra", 0, EVEN, 16 + 3 * 6, 7, 140, false);
    }

    @GameTest(environment = "mmorpg:lr_terra6", maxTicks = 230)
    public void earthSplitterSixStars(GameTestHelper h) {
        cast(h, "terra", 6, EVEN, 27 + 3.5 * 8, 9, 165, false);
    }

    /** 네 스탯을 어떻게 찍어도 같은 위력 (합이 같으면) · 한 스탯만 찍은 사람의 자기 계열 룬과 같은 P */
    @GameTest(maxTicks = 20)
    public void totalStatPower(GameTestHelper h) {
        ServerPlayer sp = player(h);
        sp.setExperienceLevels(40);
        RpgPlayers.checkLevelUp(sp);
        double[][] builds = {{125, 0, 0, 0}, {0, 0, 0, 125}, {42, 42, 41, 0}, {31, 31, 31, 32}};
        String[] keys = {"str", "dex", "vit", "int"};
        for (double[] b : builds) {
            for (int i = 0; i < 4; i++) RpgPlayers.setStat(sp, keys[i], (int) b[i]);
            for (String f : List.of("arcana", "dipper", "skyeye", "endless", "terra")) {
                double P = RuneCast.power(sp, f);
                h.assertTrue(Math.abs(P - 12 * (1 + 125 * 0.02)) < 1e-6, f + " P " + P + " for " + java.util.Arrays.toString(b));
            }
        }
        for (int i = 0; i < 4; i++) RpgPlayers.setStat(sp, keys[i], i == 3 ? 125 : 0);
        h.assertTrue(Math.abs(RuneCast.power(sp, "int") - RuneCast.power(sp, "arcana")) < 1e-6, "pure mage: meteor P = limited P");
        h.getLevel().getServer().getPlayerList().remove(sp);
        h.succeed();
    }
}
