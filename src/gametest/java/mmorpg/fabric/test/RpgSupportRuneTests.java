package mmorpg.fabric.test;

import java.lang.reflect.Method;
import java.util.List;
import mmorpg.core.Runes;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.rune.RuneCast;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-28 지원 룬 다섯 게임 시험 (서버 — rune.RuneSupport): 피해 없이 회복 · 버프만.
 * <ul>
 * <li>라이프 볼트: 겨눈 아군이 없으면 나 (최대 체력 × heal × 회복 배율) · 둘레 3칸 아군 절반 · 먼 사람 그대로 → 앞 6칸 아군을 겨누면 그 아군만 (나는 6칸 밖)</li>
 * <li>워 드럼: 마지막 울림 뒤 나 · 4칸 아군 공격 속도 ×1.35 · 신속 I, 12칸 (반지름 8 밖) 그대로 · 상태 줄 → 5초 뒤 돌아옴</li>
 * <li>새크리드 서클: 원 안 (2칸) 아군 · 나 회복 (아군 절반) · 아군의 나쁜 효과 하나 (구속 — 약화보다 앞 차례) 만 걷힘 · 원 밖 그대로</li>
 * <li>발키리 송: 나 · 아군 가하는 피해 ×1.15 · 받는 피해 −15% (12칸 밖 그대로) → 10초 뒤 돌아옴</li>
 * <li>에덴 블룸: 나 · 아군 가하는 피해 ×1.18 (12칸 밖 그대로) · 회복 다섯 번 (나 heal · 아군 절반) → 12초 뒤 돌아옴</li>
 * </ul>
 * 범위가 넓어 시험마다 시험 환경을 따로 (mmorpg:sr_…) — 같은 묶음의 다른 시험 사람이 버프 · 회복을 받지 않게. 스탯 31 · 31 · 31 · 32 (합 125 → 회복 배율 2.25)
 */
public final class RpgSupportRuneTests {
    private static ServerPlayer player(GameTestHelper h, double x, double z) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        try {
            Method m = ServerGamePacketListenerImpl.class.getDeclaredMethod("markClientLoaded");
            m.setAccessible(true);
            m.invoke(sp.connection);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot mark mock player loaded", e);
        }
        sp.setGameMode(GameType.SURVIVAL);
        Vec3 at = h.absoluteVec(new Vec3(x, 2, z));
        sp.snapTo(at.x, at.y, at.z, 0F, 0F);
        RpgPlayers.onJoin(sp);
        return sp;
    }

    /** 시전자: 40렙 · 스탯 31 · 31 · 31 · 32 */
    private static ServerPlayer caster(GameTestHelper h, double x, double z) {
        ServerPlayer sp = player(h, x, z);
        sp.setExperienceLevels(40);
        RpgPlayers.checkLevelUp(sp);
        String[] keys = {"str", "dex", "vit", "int"};
        int[] st = {31, 31, 31, 32};
        for (int i = 0; i < 4; i++) RpgPlayers.setStat(sp, keys[i], st[i]);
        return sp;
    }

    private static void floor(GameTestHelper h, int n) {
        for (int x = 0; x < n; x++) for (int z = 0; z < n; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }

    private static void near(GameTestHelper h, double a, double b, double eps, String what) {
        h.assertTrue(Math.abs(a - b) <= eps, what + " (" + a + " vs " + b + ")");
    }

    /** 체력을 최대의 frac 으로 */
    private static void hp(ServerPlayer p, double frac) {
        p.setHealth((float) Math.max(1, p.getMaxHealth() * frac));
    }

    /** 회복 기대값의 허용 (자연 회복 몇 칸 · 반올림) */
    private static double eps(ServerPlayer p) {
        return 2.0 + p.getMaxHealth() * 0.01;
    }

    private static Runes.Rune rune(String fam, String g) {
        Runes.Rune r = Runes.parseRune(Runes.runeId(fam, g));
        if (r == null) throw new IllegalStateException("no rune " + fam + " " + g);
        return r;
    }

    private static void done(GameTestHelper h, List<ServerPlayer> ps) {
        for (ServerPlayer p : ps) h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    @GameTest(environment = "mmorpg:sr_bolt", maxTicks = 60)
    public void lifeBolt(GameTestHelper h) {
        floor(h, 16);
        ServerPlayer sp = caster(h, 1.5, 1.5);
        ServerPlayer side = player(h, 4.0, 1.5);
        ServerPlayer far = player(h, 12.5, 1.5);
        Runes.Rune r = rune("lifebolt", "common");
        double heal = r.def().num("heal");
        double scale = Runes.supportScale(RpgStats.effective(sp), r.family());
        near(h, scale, 2.25, 1e-9, "support scale (sum 125)");
        double[] before = new double[3];
        h.runAfterDelay(2, () -> {
            for (ServerPlayer p : List.of(sp, side, far)) hp(p, 0.3);
            before[0] = sp.getHealth();
            before[1] = side.getHealth();
            before[2] = far.getHealth();
            h.assertTrue(RuneCast.castRune(sp, r, false, 0), "life bolt cast (self)");
        });
        h.runAfterDelay(14, () -> {
            near(h, sp.getHealth() - before[0], RpgHealth.maxOf(sp) * heal * scale, eps(sp), "self heal");
            near(h, side.getHealth() - before[1], RpgHealth.maxOf(side) * heal * scale * 0.5, eps(side), "splash ally half");
            near(h, far.getHealth() - before[2], 0, 1.01, "far: none");
        });
        // 앞 6칸 아군을 겨눔: 그 아군만 (나 · 옆 사람은 6칸 넘게 떨어짐)
        ServerPlayer[] front = new ServerPlayer[1];
        h.runAfterDelay(18, () -> {
            front[0] = player(h, 1.5, 7.5);
            RuneCast.resetCooldowns(sp);
            for (ServerPlayer p : List.of(sp, side, front[0])) hp(p, 0.3);
            before[0] = sp.getHealth();
            before[1] = front[0].getHealth();
            h.assertTrue(RuneCast.castRune(sp, r, false, 0), "life bolt cast (aimed ally)");
        });
        h.runAfterDelay(32, () -> {
            near(h, front[0].getHealth() - before[1], RpgHealth.maxOf(front[0]) * heal * scale, eps(front[0]), "aimed ally heal");
            near(h, sp.getHealth() - before[0], 0, 1.01, "caster not healed (6 blocks away)");
            done(h, List.of(sp, side, far, front[0]));
        });
    }

    @GameTest(environment = "mmorpg:sr_drum", maxTicks = 160)
    public void warDrum(GameTestHelper h) {
        floor(h, 16);
        ServerPlayer sp = caster(h, 1.5, 1.5);
        ServerPlayer ally = player(h, 1.5, 5.5);
        ServerPlayer far = player(h, 13.5, 1.5);
        Runes.Rune r = rune("wardrum", "rare");
        double haste = r.def().num("haste");
        int ticks = (int) r.def().num("buffTicks");
        double[] base = new double[3];
        h.runAfterDelay(2, () -> {
            base[0] = RpgCombat.attackSpeed.applyAsDouble(sp);
            base[1] = RpgCombat.attackSpeed.applyAsDouble(ally);
            base[2] = RpgCombat.attackSpeed.applyAsDouble(far);
            h.assertTrue(RuneCast.castRune(sp, r, false, 0), "war drum cast");
        });
        h.runAfterDelay(24, () -> {
            near(h, RpgCombat.attackSpeed.applyAsDouble(sp) / base[0], 1 + haste, 1e-6, "caster attack speed");
            near(h, RpgCombat.attackSpeed.applyAsDouble(ally) / base[1], 1 + haste, 1e-6, "ally attack speed");
            near(h, RpgCombat.attackSpeed.applyAsDouble(far) / base[2], 1, 1e-9, "far: none");
            MobEffectInstance s = sp.getEffect(MobEffects.SPEED);
            h.assertTrue(s != null && s.getAmplifier() == 0, "speed I " + s);
            h.assertTrue(ally.getEffect(MobEffects.SPEED) != null && far.getEffect(MobEffects.SPEED) == null, "ally speed · far none");
            h.assertTrue(Notify.hudSuffix(ally).contains("워 드럼") && !Notify.hudSuffix(far).contains("워 드럼"), "hud " + Notify.hudSuffix(ally));
        });
        h.runAfterDelay(24 + ticks, () -> {
            near(h, RpgCombat.attackSpeed.applyAsDouble(sp) / base[0], 1, 1e-9, "caster back");
            near(h, RpgCombat.attackSpeed.applyAsDouble(ally) / base[1], 1, 1e-9, "ally back");
            h.assertTrue(!Notify.hudSuffix(ally).contains("워 드럼"), "hud cleared");
            done(h, List.of(sp, ally, far));
        });
    }

    @GameTest(environment = "mmorpg:sr_circle", maxTicks = 160)
    public void sacredCircle(GameTestHelper h) {
        floor(h, 16);
        ServerPlayer sp = caster(h, 3.5, 3.5);
        ServerPlayer ally = player(h, 5.5, 3.5);
        ServerPlayer out = player(h, 12.5, 3.5);
        Runes.Rune r = rune("sacredcircle", "noble");
        double heal = r.def().num("heal");
        int pulses = (int) r.def().num("pulses");
        int gap = (int) r.def().num("gap");
        double scale = Runes.supportScale(RpgStats.effective(sp), r.family());
        double[] before = new double[3];
        h.runAfterDelay(2, () -> {
            for (ServerPlayer p : List.of(sp, ally, out)) hp(p, 0.3);
            before[0] = sp.getHealth();
            before[1] = ally.getHealth();
            before[2] = out.getHealth();
            ally.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 400, 0));
            ally.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 0));
            h.assertTrue(RuneCast.castRune(sp, r, false, 0), "sacred circle cast");
        });
        h.runAfterDelay(12, () -> h.assertTrue(!ally.hasEffect(MobEffects.SLOWNESS) && ally.hasEffect(MobEffects.WEAKNESS),
                "cleanse one (slowness first) · weakness stays"));
        h.runAfterDelay(2 + 4 + (pulses - 1) * gap + 6, () -> {
            near(h, sp.getHealth() - before[0], RpgHealth.maxOf(sp) * heal * scale, eps(sp), "caster heal (all pulses)");
            near(h, ally.getHealth() - before[1], RpgHealth.maxOf(ally) * heal * scale * 0.5, eps(ally), "ally half");
            near(h, out.getHealth() - before[2], 0, 2.01, "outside the circle: none");
            done(h, List.of(sp, ally, out));
        });
    }

    @GameTest(environment = "mmorpg:sr_valk", maxTicks = 260)
    public void valkyrieSong(GameTestHelper h) {
        floor(h, 16);
        ServerPlayer sp = caster(h, 1.5, 1.5);
        ServerPlayer ally = player(h, 1.5, 5.5);
        ServerPlayer far = player(h, 13.5, 1.5);
        Runes.Rune r = rune("valkyriesong", "hero");
        double up = r.def().num("buffUp");
        double cut = r.def().num("buffCut");
        int descend = (int) r.def().num("descend");
        int ticks = (int) r.def().num("buffTicks");
        h.runAfterDelay(2, () -> {
            near(h, RpgCombat.dealtMult.applyAsDouble(sp), 1, 1e-9, "no buff before");
            h.assertTrue(RuneCast.castRune(sp, r, false, 0), "valkyrie song cast");
        });
        h.runAfterDelay(2 + descend + 6, () -> {
            near(h, RpgCombat.dealtMult.applyAsDouble(sp), 1 + up, 1e-9, "caster dealt");
            near(h, RpgCombat.dealtMult.applyAsDouble(ally), 1 + up, 1e-9, "ally dealt");
            near(h, RpgCombat.dealtMult.applyAsDouble(far), 1, 1e-9, "far: none");
            near(h, RpgHealth.buffReduce.applyAsDouble(ally), cut, 1e-9, "ally cut");
            near(h, RpgHealth.buffReduce.applyAsDouble(far), 0, 1e-9, "far: no cut");
            h.assertTrue(Notify.hudSuffix(sp).contains("발키리"), "hud " + Notify.hudSuffix(sp));
        });
        h.runAfterDelay(2 + descend + 6 + ticks, () -> {
            near(h, RpgCombat.dealtMult.applyAsDouble(sp), 1, 1e-9, "caster back");
            near(h, RpgHealth.buffReduce.applyAsDouble(ally), 0, 1e-9, "ally back");
            done(h, List.of(sp, ally, far));
        });
    }

    @GameTest(environment = "mmorpg:sr_eden", maxTicks = 320)
    public void edenBloom(GameTestHelper h) {
        floor(h, 20);
        ServerPlayer sp = caster(h, 5.5, 5.5);
        ServerPlayer ally = player(h, 7.5, 5.5);
        ServerPlayer far = player(h, 18.5, 5.5);
        Runes.Rune r = rune("edenbloom", "transcend");
        double heal = r.def().num("heal");
        double up = r.def().num("buffUp");
        int charge = (int) r.def().num("charge");
        int pulses = (int) r.def().num("pulses");
        int gap = (int) r.def().num("gap");
        int ticks = (int) r.def().num("buffTicks");
        double scale = Runes.supportScale(RpgStats.effective(sp), r.family());
        double[] before = new double[2];
        h.runAfterDelay(2, () -> {
            for (ServerPlayer p : List.of(sp, ally, far)) hp(p, 0.1);
            before[0] = sp.getHealth();
            before[1] = ally.getHealth();
            h.assertTrue(RuneCast.castRune(sp, r, false, 0), "eden bloom cast");
        });
        h.runAfterDelay(2 + charge + 4, () -> {
            near(h, RpgCombat.dealtMult.applyAsDouble(sp), 1 + up, 1e-9, "caster dealt");
            near(h, RpgCombat.dealtMult.applyAsDouble(ally), 1 + up, 1e-9, "ally dealt");
            near(h, RpgCombat.dealtMult.applyAsDouble(far), 1, 1e-9, "far: none");
            h.assertTrue(Notify.hudSuffix(ally).contains("에덴"), "hud " + Notify.hudSuffix(ally));
        });
        h.runAfterDelay(2 + charge + (pulses - 1) * gap + 6, () -> {
            near(h, sp.getHealth() - before[0], RpgHealth.maxOf(sp) * heal * scale, eps(sp), "caster heal (all pulses)");
            near(h, ally.getHealth() - before[1], RpgHealth.maxOf(ally) * heal * scale * 0.5, eps(ally), "ally half");
        });
        h.runAfterDelay(2 + charge + ticks + 6, () -> {
            near(h, RpgCombat.dealtMult.applyAsDouble(sp), 1, 1e-9, "caster back");
            done(h, List.of(sp, ally, far));
        });
    }
}
