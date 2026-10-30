package mmorpg.fabric.test;

import java.lang.reflect.Method;
import java.util.List;
import mmorpg.core.RuneBreak;
import mmorpg.core.Runes;
import mmorpg.fabric.FxDisplays;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Guards;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
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
 * 0.19.0 세계수의 가호 (버프 — 피해 없음): 반지름 안 아군 (나 포함) 만 가하는 피해 × (1 + 가호) · 받는 피해 × (1 − 가호, Guards 감소와는 센 것 하나),
 * 끝나면 그대로 (0.20.1 — 끝에 띄우던 요약 글자는 없음).
 * 0.20.0 심연의 낙인 (적 약화 — 피해 없음): 조준한 곳 반지름 안 적만 받는 피해 × (1 + 낙인) · 그 적이 준 피해 × (1 − 낙인), 가호와 곱 (1.44배),
 * 적이 없으면 재사용 대기 없이 거절.
 * 0.24.0 만년빙하: 조준한 과녁 둘레 얼음 필드 — 거대한 얼음 3 × 8 + 빙하 붕괴 10 = 34 (6돌 3.5 × 12 + 13 = 55), 타격 = 얼음 8 (6돌 12) + 붕괴 1,
 * 빙결 (같은 적이 3번 꿰뚫리면 2초 기절 · ❄ · 풀리면 다시 쌓임 · 붕괴에 부서짐 · 필드 밖은 그대로).
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
        floor(h, 8);
    }

    private static void floor(GameTestHelper h, int n) {
        for (int x = 0; x < n; x++) for (int z = 0; z < n; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }

    /** 시험용 플레이어를 (x, z) 에 (남쪽 +z 를 봄) */
    private static ServerPlayer playerAt(GameTestHelper h, double x, double z) {
        ServerPlayer sp = player(h);
        Vec3 at = h.absoluteVec(new Vec3(x, 2, z));
        sp.snapTo(at.x, at.y, at.z, 0F, 0F);
        return sp;
    }

    /** 연속 피해 막기 없이 피해 (바닐라 경로: hurtServer → Player.actuallyHurt → RpgHealth.onLoss) */
    private static boolean hurt(GameTestHelper h, LivingEntity e, DamageSource src, float amount) {
        e.setInvulnerableTime(0);
        e.damageCooldownTime = 0;
        return e.hurtServer(h.getLevel(), src, amount);
    }

    /** 체력을 가득 채우고 amount 피해 (세계 밖 — 무적 · 방어구 무시) → 깎인 체력 */
    private static double lossOf(GameTestHelper h, ServerPlayer sp, float amount) {
        RpgHealth.healFull(sp);
        float before = sp.getHealth();
        hurt(h, sp, sp.damageSources().fellOutOfWorld(), amount);
        return before - sp.getHealth();
    }

    /** RuneGrove 의 마지막 시전 {받은 사람 수, 끝 틱} — 패키지 밖이라 리플렉션 */
    private static double[] graceOf(ServerPlayer sp) {
        try {
            Class<?> c = Class.forName("mmorpg.fabric.rune.RuneGrove");
            Method m = c.getDeclaredMethod("last", java.util.UUID.class);
            m.setAccessible(true);
            return (double[]) m.invoke(null, sp.getUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** RuneAbyss 의 마지막 시전 {낙인 찍은 수, 끝 틱} — 패키지 밖이라 리플렉션 */
    private static double[] brandOf(ServerPlayer sp) {
        try {
            Class<?> c = Class.forName("mmorpg.fabric.rune.RuneAbyss");
            Method m = c.getDeclaredMethod("last", java.util.UUID.class);
            m.setAccessible(true);
            return (double[]) m.invoke(null, sp.getUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void near(GameTestHelper h, double a, double b, double eps, String what) {
        h.assertTrue(Math.abs(a - b) <= eps, what + " (" + a + " vs " + b + ")");
    }

    /** 쓰러지지 않는 과녁 허스크 (불에 타지 않음 — 받은 피해는 TestDummies.dealt) */
    /**
     * 앞 묶음의 시험이 남긴 적대 몹 (시험 구조물 밖에 세운 과녁은 구조물을 지울 때 남음 — 묶음마다 같은 자리에서 시작) 을 치움:
     * 낙인처럼 "반지름 안 적 모두" 를 세는 시험이 남의 몹까지 세지 않게 (0.23.0 — 시험이 늘어 자리가 바뀌자 "marked 2: 3")
     */
    private static void clearStrays(GameTestHelper h, net.minecraft.world.entity.Entity... keep) {
        var box = new net.minecraft.world.phys.AABB(h.absoluteVec(new Vec3(-12, -4, -12)), h.absoluteVec(new Vec3(28, 10, 28)));
        java.util.Set<net.minecraft.world.entity.Entity> own = java.util.Set.of(keep);
        for (net.minecraft.world.entity.Mob m : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box,
                e -> !own.contains(e) && e instanceof net.minecraft.world.entity.monster.Enemy)) {
            m.discard();
        }
    }

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
            clearStrays(h, z); // 영역 · 균열 룬은 범위 안 적 모두를 침 — 앞 묶음이 남긴 몹까지 세지 않게 ("endless hits 30 vs 15")
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
            double sum = 0;
            for (int i = 0; i < 4; i++) sum += stats[i];
            double P = RuneCast.power(sp, family) * RuneBreak.coef(breakLevel) * Runes.spreadMult(r); // 0.26.0 지속 보정 (한정 룬도)
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
                h.assertTrue(last != null && Math.abs(last[0] - dealt) < 1e-2 * P, family + " recorded sum " + (last == null ? 0 : last[0]));
                h.getLevel().getServer().getPlayerList().remove(sp);
                h.succeed();
            });
        });
    }

    /** RuneGlacier 의 마지막 시전 {얼린 수, 거대한 얼음 수, 필드 반지름} — 패키지 밖이라 리플렉션 */
    private static double[] glacierOf(ServerPlayer sp) {
        try {
            Class<?> c = Class.forName("mmorpg.fabric.rune.RuneGlacier");
            Method m = c.getDeclaredMethod("last", java.util.UUID.class);
            m.setAccessible(true);
            return (double[]) m.invoke(null, sp.getUUID());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
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

    /**
     * 0.19.0 세계수의 가호 0돌 (스탯 31 · 31 · 31 · 32 — 합 125 → 가호 20%): 시전자 · 5칸 아군은 가하는 피해 ×1.2 · 받는 피해 ×0.8, 13칸 (반지름 8 밖) 은 그대로.
     * 룬 피해가 1.2배, 같은 피해에 아군은 바깥 사람의 0.8배 (강철 피부 같은 감소 45% 가 함께면 센 것 하나 — 0.55배),
     * 시전 기록 = 받은 사람 2, 12초 뒤 그대로 돌아옴 (0.20.1 — 요약 글자 없음)
     */
    @GameTest(environment = "mmorpg:lr_grace", maxTicks = 330)
    public void worldTreeGrace(GameTestHelper h) {
        floor(h, 16);
        ServerPlayer sp = playerAt(h, 1.5, 1.5);
        ServerPlayer ally = playerAt(h, 1.5, 6.5);
        ServerPlayer far = playerAt(h, 14.5, 1.5);
        sp.setExperienceLevels(40);
        RpgPlayers.checkLevelUp(sp);
        String[] keys = {"str", "dex", "vit", "int"};
        int[] st = {31, 31, 31, 32};
        for (int i = 0; i < 4; i++) RpgPlayers.setStat(sp, keys[i], st[i]);
        Husk z = dummy(h, 4.5, 2, 3.5);
        Runes.Rune r = Runes.parseRune("rpg:rune_yggdrasil_transcend");
        h.assertTrue(r != null && Runes.limited("yggdrasil"), "grace rune");
        int charge = (int) r.def().num("charge");
        int ticks = (int) r.def().num("ticks");
        double[] d0 = new double[1];
        h.runAfterDelay(2, () -> {
            near(h, RpgCombat.dealtMult.applyAsDouble(sp), 1, 1e-9, "no grace before");
            RuneKit.runeHit(sp, z, 10, 1, "int");
            d0[0] = TestDummies.dealt(z);
            h.assertTrue(d0[0] > 0, "rune hit before");
            h.assertTrue(RuneCast.castRune(sp, r, false, 0), "grace cast");
        });
        h.runAfterDelay(3 + charge + 2, () -> {
            near(h, RpgCombat.dealtMult.applyAsDouble(sp), 1.2, 1e-9, "caster dealt ×1.2");
            near(h, RpgCombat.dealtMult.applyAsDouble(ally), 1.2, 1e-9, "ally dealt ×1.2");
            near(h, RpgCombat.dealtMult.applyAsDouble(far), 1, 1e-9, "outside radius: no grace");
            near(h, RpgHealth.buffReduce.applyAsDouble(ally), 0.2, 1e-9, "ally cut 20%");
            near(h, RpgHealth.buffReduce.applyAsDouble(far), 0, 1e-9, "outside: no cut");
            h.assertTrue(Notify.hudSuffix(ally).contains("세계수") && !Notify.hudSuffix(far).contains("세계수"), "hud " + Notify.hudSuffix(ally));
            double[] g = graceOf(sp);
            h.assertTrue(g != null && (int) g[0] == 2, "members 2: " + (g == null ? "none" : (int) g[0]));
            near(h, g[1] - Tasks.now(), ticks - 2, 3, "grace lasts " + ticks + " ticks");
            h.assertTrue(FxDisplays.activeCount() >= 1, "world tree display");
        });
        h.runAfterDelay(34, () -> {
            // 룬 피해 1.2배 (첫 피해에서 32틱 — 몹 무적 시간 밖)
            RuneKit.runeHit(sp, z, 10, 1, "int");
            double d1 = TestDummies.dealt(z) - d0[0];
            near(h, d1 / d0[0], 1.2, 1e-4, "rune damage ×1.2");
        });
        h.runAfterDelay(70, () -> {
            double a = lossOf(h, ally, 10F);
            double b = lossOf(h, far, 10F);
            h.assertTrue(b > 0, "outsider hurt " + b);
            near(h, a / b, 0.8, 1e-4, "ally takes ×0.8");
            // 다른 감소 (45%) 가 함께: 센 것 하나 (0.55배)
            Guards.set(ally, 40, 0.45, null, null);
            Guards.set(far, 40, 0.45, null, null);
            double a2 = lossOf(h, ally, 10F);
            double b2 = lossOf(h, far, 10F);
            near(h, a2 / b, 0.55, 1e-4, "stronger guard wins (no stacking)");
            near(h, a2, b2, 1e-4, "same as the guard alone");
        });
        h.runAfterDelay(3 + charge + ticks + 6, () -> {
            near(h, RpgCombat.dealtMult.applyAsDouble(sp), 1, 1e-9, "caster grace ended");
            near(h, RpgHealth.buffReduce.applyAsDouble(ally), 0, 1e-9, "ally grace ended");
            h.assertTrue(!Notify.hudSuffix(ally).contains("세계수"), "hud cleared");
            for (ServerPlayer p : List.of(sp, ally, far)) h.getLevel().getServer().getPlayerList().remove(p);
            h.succeed();
        });
    }

    /** 6돌: 가호 32% (계수 ×1.6) · 반지름 12칸 (10칸 아군 받음 · 14칸 밖) · 16초 · 세계수 개화 (큰 나무) */
    @GameTest(environment = "mmorpg:lr_grace6", maxTicks = 80)
    public void worldTreeGraceSixStars(GameTestHelper h) {
        floor(h, 17);
        ServerPlayer sp = playerAt(h, 1.5, 1.5);
        ServerPlayer ally = playerAt(h, 1.5, 11.5);
        ServerPlayer far = playerAt(h, 15.5, 1.5);
        sp.setExperienceLevels(40);
        RpgPlayers.checkLevelUp(sp);
        String[] keys = {"str", "dex", "vit", "int"};
        int[] st = {31, 31, 31, 32};
        for (int i = 0; i < 4; i++) RpgPlayers.setStat(sp, keys[i], st[i]);
        Runes.Rune r = Runes.parseRune("rpg:rune_yggdrasil_transcend");
        int charge = (int) r.def().num("charge");
        h.runAfterDelay(2, () -> h.assertTrue(RuneCast.castRune(sp, r, false, 6), "grace 6 cast"));
        h.runAfterDelay(3 + charge + 2, () -> {
            near(h, RpgCombat.dealtMult.applyAsDouble(sp), 1.32, 1e-9, "caster ×1.32");
            near(h, RpgCombat.dealtMult.applyAsDouble(ally), 1.32, 1e-9, "ally at 10 (radius 12) ×1.32");
            near(h, RpgHealth.buffReduce.applyAsDouble(ally), 0.32, 1e-9, "ally cut 32%");
            near(h, RpgCombat.dealtMult.applyAsDouble(far), 1, 1e-9, "outside 12");
            double[] g = graceOf(sp);
            near(h, g[1] - Tasks.now(), 320 - 2, 3, "16 s");
            h.assertTrue(FxDisplays.activeCount() >= 1, "world tree display");
            for (ServerPlayer p : List.of(sp, ally, far)) h.getLevel().getServer().getPlayerList().remove(p);
            h.succeed();
        });
    }

    /**
     * 0.20.0 심연의 낙인 0돌 (스탯 31 · 31 · 31 · 32 — 합 125 → 낙인 20%): 조준한 과녁 (5칸 앞) 둘레 반지름 6칸 안 과녁 둘에 낙인, 그 밖 (8칸) 은 없음.
     * 낙인 찍힌 과녁이 받는 룬 피해 1.2배 · 그 과녁이 준 피해 0.8배 (낙인 없는 몹은 그대로), 세계수의 가호를 더하면 1.2 × 1.2 = 1.44배 (곱연산),
     * 시전 기록 (찍은 수 2), 10초 뒤 그대로 (0.20.1 — 요약 글자 없음)
     */
    @GameTest(environment = "mmorpg:lr_abyss", maxTicks = 300)
    public void abyssBrand(GameTestHelper h) {
        floor(h, 16);
        ServerPlayer sp = playerAt(h, 1.5, 1.5);
        ServerPlayer far = playerAt(h, 14.5, 1.5);
        sp.setExperienceLevels(40);
        RpgPlayers.checkLevelUp(sp);
        String[] keys = {"str", "dex", "vit", "int"};
        int[] st = {31, 31, 31, 32};
        for (int i = 0; i < 4; i++) RpgPlayers.setStat(sp, keys[i], st[i]);
        Husk z1 = dummy(h, 1.5, 2, 6.5);
        Husk z2 = dummy(h, 4.5, 2, 8.5);
        Husk z3 = dummy(h, 1.5, 2, 14.5);
        Runes.Rune r = Runes.parseRune("rpg:rune_abyss_transcend");
        Runes.Rune grace = Runes.parseRune("rpg:rune_yggdrasil_transcend");
        h.assertTrue(r != null && grace != null && Runes.limited("abyss"), "brand rune");
        int charge = (int) r.def().num("charge");
        int ticks = (int) r.def().num("ticks");
        double[] d0 = new double[1];
        h.runAfterDelay(2, () -> {
            clearStrays(h, z1, z2, z3);
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, z1.getEyePosition());
            near(h, RpgCombat.takenMult.applyAsDouble(z1), 1, 1e-9, "no brand before");
            RuneKit.runeHit(sp, z1, 10, 1, "int");
            d0[0] = TestDummies.dealt(z1);
            h.assertTrue(d0[0] > 0, "rune hit before");
            h.assertTrue(RuneCast.castRune(sp, r, false, 0), "brand cast");
        });
        h.runAfterDelay(3 + charge + 4, () -> {
            near(h, RpgCombat.takenMult.applyAsDouble(z1), 1.2, 1e-9, "aimed husk takes ×1.2");
            near(h, RpgCombat.takenMult.applyAsDouble(z2), 1.2, 1e-9, "husk in radius takes ×1.2");
            near(h, RpgCombat.takenMult.applyAsDouble(z3), 1, 1e-9, "outside radius: no brand");
            near(h, RpgHealth.weakenOf.applyAsDouble(z1), 0.2, 1e-9, "branded husk deals -20%");
            near(h, RpgHealth.weakenOf.applyAsDouble(z3), 0, 1e-9, "outside: not weakened");
            double[] b = brandOf(sp);
            h.assertTrue(b != null && (int) b[0] == 2, "marked 2: " + (b == null ? "none" : (int) b[0]));
            near(h, b[1] - Tasks.now(), ticks - 4, 3, "brand lasts " + ticks + " ticks");
            h.assertTrue(Notify.hudSuffix(sp).contains("낙인"), "hud " + Notify.hudSuffix(sp));
            // 낙인 찍힌 몹이 준 피해 × 0.8 (RpgHealth.onLoss — 체력 100 → 90 의 피해 10)
            float a = RpgHealth.onLoss(far, far.damageSources().mobAttack(z1), 100F, 90F);
            float c = RpgHealth.onLoss(far, far.damageSources().mobAttack(z3), 100F, 90F);
            near(h, 100 - a, 8, 1e-3, "branded mob deals ×0.8");
            near(h, 100 - c, 10, 1e-3, "unbranded mob deals ×1");
        });
        h.runAfterDelay(36, () -> {
            RuneKit.runeHit(sp, z1, 10, 1, "int");
            double d1 = TestDummies.dealt(z1) - d0[0];
            near(h, d1 / d0[0], 1.2, 1e-4, "rune damage ×1.2 on the branded husk");
            h.assertTrue(RuneCast.castRune(sp, grace, false, 0), "grace cast");
        });
        h.runAfterDelay(36 + 18 + 8, () -> {
            near(h, RpgCombat.dealtMult.applyAsDouble(sp), 1.2, 1e-9, "grace on the caster");
            double before = TestDummies.dealt(z1);
            RuneKit.runeHit(sp, z1, 10, 1, "int");
            double d2 = TestDummies.dealt(z1) - before;
            near(h, d2 / d0[0], 1.44, 1e-4, "grace × brand = 1.2 × 1.2 (multiplicative)");
        });
        h.runAfterDelay(3 + charge + ticks + 6, () -> {
            near(h, RpgCombat.takenMult.applyAsDouble(z1), 1, 1e-9, "brand ended");
            near(h, RpgHealth.weakenOf.applyAsDouble(z1), 0, 1e-9, "weaken ended");
            for (ServerPlayer p : List.of(sp, far)) h.getLevel().getServer().getPlayerList().remove(p);
            h.succeed();
        });
    }

    /** 6돌: 낙인 32% · 반지름 8칸 (가운데에서 7.5칸 과녁은 받음 · 9.5칸은 밖) · 14초 */
    @GameTest(environment = "mmorpg:lr_abyss6", maxTicks = 80)
    public void abyssBrandSixStars(GameTestHelper h) {
        floor(h, 16);
        ServerPlayer sp = playerAt(h, 1.5, 1.5);
        sp.setExperienceLevels(40);
        RpgPlayers.checkLevelUp(sp);
        String[] keys = {"str", "dex", "vit", "int"};
        int[] st = {31, 31, 31, 32};
        for (int i = 0; i < 4; i++) RpgPlayers.setStat(sp, keys[i], st[i]);
        Husk z1 = dummy(h, 1.5, 2, 6.5);
        Husk z2 = dummy(h, 9.0, 2, 6.5);
        Husk z3 = dummy(h, 11.0, 2, 6.5);
        Runes.Rune r = Runes.parseRune("rpg:rune_abyss_transcend");
        int charge = (int) r.def().num("charge");
        h.runAfterDelay(2, () -> {
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, z1.getEyePosition());
            h.assertTrue(RuneCast.castRune(sp, r, false, 6), "brand 6 cast");
        });
        h.runAfterDelay(3 + charge + 4, () -> {
            near(h, RpgCombat.takenMult.applyAsDouble(z1), 1.32, 1e-9, "6: ×1.32");
            near(h, RpgCombat.takenMult.applyAsDouble(z2), 1.32, 1e-9, "6: 7.5 from the center (radius 8)");
            near(h, RpgCombat.takenMult.applyAsDouble(z3), 1, 1e-9, "6: 9.5 outside");
            near(h, RpgHealth.weakenOf.applyAsDouble(z1), 0.32, 1e-9, "6: deals -32%");
            near(h, brandOf(sp)[1] - Tasks.now(), 280 - 4, 3, "6: 14 s");
            h.getLevel().getServer().getPlayerList().remove(sp);
            h.succeed();
        });
    }

    /** 조준한 곳 반지름 안에 적이 없으면 거절 (재사용 대기 없음) */
    @GameTest(environment = "mmorpg:lr_abyss_none", maxTicks = 20)
    public void abyssBrandNoTarget(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Runes.Rune r = Runes.parseRune("rpg:rune_abyss_transcend");
        // 조준한 곳 (20칸 앞) 은 이 시험 자리 밖 — 다른 묶음에서 밀려 나온 몹 · 저절로 생긴 몹이 있으면 치우고 (무엇이었는지 남김) 거절을 봄
        Vec3 aim = RuneKit.groundAim(sp, 20);
        List<LivingEntity> stray = h.getLevel().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(aim, aim).inflate(10, 6, 10),
                e -> !(e instanceof net.minecraft.world.entity.player.Player));
        if (!stray.isEmpty()) org.slf4j.LoggerFactory.getLogger("mmorpg-limited-rune-test").warn("[limited-rune-test] stray entities near the abyss aim {}: {}", aim, stray);
        stray.forEach(e -> e.discard());
        h.assertFalse(RuneCast.castRune(sp, r, false, 0), "no target: refused");
        h.assertTrue(RuneCast.cooldownLeft(sp, r) == 0, "no cooldown when refused");
        h.getLevel().getServer().getPlayerList().remove(sp);
        h.succeed();
    }

    /** 0.24.0 만년빙하: 거대한 얼음 3 × 8 + 빙하 붕괴 10 (과녁은 조준한 곳 — 필드 가운데), 6돌 3.5 × 12 + 13 */
    @GameTest(environment = "mmorpg:lr_glacier", maxTicks = 200)
    public void glacierField(GameTestHelper h) {
        cast(h, "glacier", 0, EVEN, 3 * 8 + 10, 9, 150, false);
    }

    @GameTest(environment = "mmorpg:lr_glacier6", maxTicks = 250)
    public void glacierFieldSixStars(GameTestHelper h) {
        cast(h, "glacier", 6, EVEN, 3.5 * 12 + 13, 13, 205, false);
    }

    /**
     * 0.24.0 만년빙하 빙결 (0돌 — 얼음 피해 17 · 31 · 45 · … · 115틱, 붕괴 128틱): 같은 과녁이 얼음에 세 번 꿰뚫리면 (45틱) 2초 얼어붙음 (구속 XI · 나약함 V —
     * 기절 · 피해 숫자 앞 ❄ · 얼음 덩어리 표시 엔티티), 얼어 있는 동안 (59 · 73틱) 은 쌓이지 않고 풀린 뒤 (85틱) 다시 세 번 (87 · 101 · 115틱) 이면 또 얼음
     * (시전 기록 얼린 수 2), 붕괴에 얼음째 부서짐 (기절도 풀림). 필드 밖 (가운데에서 8칸) 과녁은 맞지도 얼지도 않음
     */
    @GameTest(environment = "mmorpg:lr_glacier_freeze", maxTicks = 200)
    public void glacierFreeze(GameTestHelper h) {
        floor(h, 16);
        ServerPlayer sp = playerAt(h, 1.5, 1.5);
        sp.setExperienceLevels(40);
        RpgPlayers.checkLevelUp(sp);
        String[] keys = {"str", "dex", "vit", "int"};
        int[] st = {31, 31, 31, 32};
        for (int i = 0; i < 4; i++) RpgPlayers.setStat(sp, keys[i], st[i]);
        Husk z = dummy(h, 1.5, 2, 6.5);
        Husk out = dummy(h, 1.5, 2, 14.5);
        Runes.Rune r = Runes.parseRune("rpg:rune_glacier_transcend");
        h.assertTrue(r != null && Runes.limited("glacier"), "glacier rune");
        int[] fx0 = {0};
        h.runAfterDelay(2, () -> {
            clearStrays(h, z, out);
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
            fx0[0] = FxDisplays.spawned;
            h.assertTrue(RuneCast.castRune(sp, r, false, 0), "glacier cast");
        });
        h.runAfterDelay(2 + 40, () -> {
            h.assertFalse(z.hasEffect(MobEffects.SLOWNESS), "two hits: not frozen yet");
            h.assertTrue(TestDummies.dealt(z) > 0, "ice hits the husk");
            h.assertTrue(FxDisplays.spawned - fx0[0] > 20, "ice field + giant ice displays " + (FxDisplays.spawned - fx0[0]));
        });
        h.runAfterDelay(2 + 49, () -> {
            MobEffectInstance s = z.getEffect(MobEffects.SLOWNESS);
            h.assertTrue(s != null && s.getAmplifier() >= 10, "third hit: frozen (slowness XI)");
            h.assertTrue(z.hasEffect(MobEffects.WEAKNESS), "frozen: weakness");
            h.assertTrue(mmorpg.fabric.DamageNumbers.victimMark.apply(z).contains("❄"), "frozen mark ❄");
            double[] g = glacierOf(sp);
            h.assertTrue(g != null && (int) g[0] == 1, "frozen 1: " + (g == null ? "none" : (int) g[0]));
            h.assertTrue(TestDummies.dealt(out) == 0 && !out.hasEffect(MobEffects.SLOWNESS), "outside the field: untouched");
        });
        h.runAfterDelay(2 + 90, () -> {
            h.assertFalse(z.hasEffect(MobEffects.SLOWNESS), "thawed after 2 s");
            h.assertFalse(mmorpg.fabric.DamageNumbers.victimMark.apply(z).contains("❄"), "no mark after thaw");
            h.assertTrue((int) glacierOf(sp)[0] == 1, "no stacking while frozen");
        });
        h.runAfterDelay(2 + 118, () -> {
            h.assertTrue(z.hasEffect(MobEffects.SLOWNESS), "three more hits: frozen again");
            h.assertTrue((int) glacierOf(sp)[0] == 2, "frozen 2: " + (int) glacierOf(sp)[0]);
        });
        h.runAfterDelay(2 + 134, () -> {
            h.assertFalse(z.hasEffect(MobEffects.SLOWNESS), "the collapse shatters the ice (stun ends)");
            h.assertTrue(TestDummies.dealt(out) == 0, "outside the field: no damage");
            h.getLevel().getServer().getPlayerList().remove(sp);
            h.succeed();
        });
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
            for (String f : List.of("arcana", "dipper", "skyeye", "endless", "terra", "glacier")) {
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
