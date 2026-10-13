package mmorpg.fabric.test;

import java.lang.reflect.Method;
import java.util.List;
import mmorpg.core.Combat;
import mmorpg.core.Obj;
import mmorpg.core.Skills;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgData;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.RpgWeaponItem;
import mmorpg.fabric.skill.Guards;
import mmorpg.fabric.skill.Transcend;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 10단계 영웅 무기 스킬 게임 시험 (서버, 시험 환경 mmorpg:hero — 사거리가 긴 스킬이 다른 시험의 과녁을 맞히지 않게 따로 묶음):
 * 영웅 무기 6종 (30 · 35 · 40렙) 의 스킬 하나 · 조작 줄 · 길게 누르기 없음, 스킬마다 들어간 피해 (배율 합 — 몹 무적 시간을 피해 모두),
 * 그림자 습격의 등 뒤 이동 · 대상이 없으면 거절, 성광 강타의 보호막 · 기절, 정령의 연사의 화살 나눠 쏘기, 영혼 수확의 구속 · 회복,
 * 실제 입력 (근접 우클릭 · 웅크리고 우클릭, 활 그냥 누르기 = 바로 평타 · 웅크리고 = 스킬).
 */
public final class RpgHeroSkillTests {
    static final String ENV = "mmorpg:hero";

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

    private static void done(GameTestHelper h, ServerPlayer sp) {
        h.getLevel().getServer().getPlayerList().remove(sp);
        h.succeed();
    }

    /** 피해 합이 타격들 (배율 mults × 기본 b) 의 어떤 치명타 조합 (각각 × c 또는 그대로) 과 같은지 */
    static boolean hitsMatch(double dealt, double b, double c, double... mults) {
        for (int mask = 0; mask < (1 << mults.length); mask++) {
            double sum = 0;
            for (int i = 0; i < mults.length; i++) sum += mults[i] * b * ((mask >> i & 1) == 1 ? c : 1);
            if (Math.abs(dealt - sum) < 1e-3) return true;
        }
        return false;
    }

    /** 돌 바닥 (시험 자리 y = 1, 8 × 8) */
    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }

    /** 쓰러지지 않는 과녁 허스크 (받은 피해는 TestDummies.dealt). yaw 180 = 북쪽 (나를) 을 봄 */
    private static Husk dummy(GameTestHelper h, double x, double y, double z, float yaw) {
        Husk m = TestDummies.make(h.spawnWithNoFreeWill(EntityTypes.HUSK, (float) x, (float) y, (float) z));
        m.setYRot(yaw);
        m.setYHeadRot(yaw);
        m.setYBodyRot(yaw);
        return m;
    }

    private static Weapon hero(String type, int level) {
        return Weapons.weaponFrom(type, level, "hero");
    }

    private static void hold(ServerPlayer sp, Weapon w) {
        if (sp.experienceLevel < w.level()) {
            sp.setExperienceLevels(w.level());
            RpgPlayers.checkLevelUp(sp);
        }
        for (String s : List.of("str", "dex", "int", "vit")) RpgPlayers.setStat(sp, s, 150);
        RpgWeaponItem item = RpgItems.byId(w.id());
        sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
    }

    private static double base(ServerPlayer sp, Weapon w) {
        return w.damage() * Combat.powerMultiplier(RpgData.of(sp).effective(), w, 0);
    }

    // ------------------------------------------------------------ 무기 · 조작

    @GameTest(environment = ENV, maxTicks = 20)
    public void heroWeaponsHaveOneSkill(GameTestHelper h) {
        ServerPlayer sp = player(h);
        h.assertTrue(Skills.heroTypes().size() == 10, "ten hero skill types (6 + 12단계 4) " + Skills.heroTypes());
        for (String type : Skills.heroTypes()) {
            boolean ranged = Weapons.isRangedType(type);
            for (int lv : new int[] {30, 35, 40}) {
                Weapon w = hero(type, lv);
                h.assertTrue(w != null && w.skills() != null && w.skills().size() == 1, type + " " + lv + " one skill");
                Obj k = w.skills().get(0);
                h.assertTrue(Transcend.has(k.str("id")), "registered " + k.str("id"));
                h.assertFalse(Transcend.holdable(w), type + " no long press");
                String controls = ranged ? "웅크리고 우클릭: 스킬" : "우클릭: 스킬";
                h.assertTrue(Weapons.weaponInfo(w).stream().anyMatch(l -> RpgWeaponItem.javaControls(l).endsWith(controls)), type + " tooltip " + controls);
            }
        }
        h.assertTrue(Transcend.holdable(Weapons.weaponFrom("bow", 40, "transcend")), "transcend bow keeps long press");
        Weapon w = hero("sword", 30);
        hold(sp, w);
        String hud = Transcend.hudText(sp, w);
        h.assertTrue(hud.contains("우클릭") && hud.contains(w.skills().get(0).str("name")) && hud.contains("§a준비"), "hud " + hud);
        done(h, sp);
    }

    @GameTest(environment = ENV, maxTicks = 90)
    public void heroInputMeleeAndBow(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        // 근접: 우클릭 · 웅크리고 우클릭 모두 그 스킬 (과녁 없이 — 검기는 대상이 없어도 나감)
        Weapon sword = hero("sword", 35);
        hold(sp, sword);
        Obj ks = sword.skills().get(0);
        sp.getMainHandItem().use(h.getLevel(), sp, InteractionHand.MAIN_HAND);
        h.assertTrue(Transcend.cooldownLeft(sp, ks) == Math.round(ks.num("cd") * 20), "right click casts: " + Transcend.cooldownLeft(sp, ks));
        Transcend.resetCooldowns(sp);
        sp.setShiftKeyDown(true);
        sp.getMainHandItem().use(h.getLevel(), sp, InteractionHand.MAIN_HAND);
        sp.setShiftKeyDown(false);
        h.assertTrue(Transcend.cooldownLeft(sp, ks) > 0, "sneak right click casts too");
        // 활: 그냥 누르면 바로 평타 (누르고 있지 않음 · 스킬 없음), 웅크리면 스킬 (검기가 다 지나간 뒤 과녁을 세움)
        h.runAfterDelay(30, () -> {
            Husk z = dummy(h, 1.5, 2, 6.5, 180F);
            Transcend.resetCooldowns(sp);
            Weapon bow = hero("bow", 40);
            hold(sp, bow);
            Obj kb = bow.skills().get(0);
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
            ItemStack stack = sp.getMainHandItem();
            stack.use(h.getLevel(), sp, InteractionHand.MAIN_HAND);
            h.assertFalse(sp.isUsingItem(), "hero bow: no hold");
            h.assertTrue(Transcend.cooldownLeft(sp, kb) == 0, "plain press is not the skill");
            double shot = base(sp, bow);
            h.runAfterDelay(20, () -> {
                h.assertTrue(hitsMatch(TestDummies.dealt(z), shot, bow.critMult(), 1), "basic shot " + TestDummies.dealt(z) + " vs " + shot);
                sp.setShiftKeyDown(true);
                stack.use(h.getLevel(), sp, InteractionHand.MAIN_HAND);
                sp.setShiftKeyDown(false);
                h.assertTrue(Transcend.cooldownLeft(sp, kb) > 0, "sneak press casts the skill");
                done(h, sp);
            });
        });
    }

    // ------------------------------------------------------------ 스킬마다 피해

    @GameTest(environment = ENV, maxTicks = 60)
    public void balmungWaveCutsThenBursts(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = hero("sword", 40);
        hold(sp, w);
        Obj k = w.skills().get(0);
        Husk z = dummy(h, 1.5, 2, 6.5, 180F);
        h.assertTrue(Transcend.castSkill(sp, w, 0), "cast");
        double b = base(sp, w);
        double c = w.critMult();
        h.runAfterDelay(9, () -> h.assertTrue(hitsMatch(TestDummies.dealt(z), b, c, k.num("mult")), "wave hit " + TestDummies.dealt(z) + " vs " + k.num("mult") * b));
        h.runAfterDelay(30, () -> {
            h.assertTrue(hitsMatch(TestDummies.dealt(z), b, c, k.num("mult"), k.num("afterMult")), "wave + scar " + TestDummies.dealt(z));
            done(h, sp);
        });
    }

    @GameTest(environment = ENV, maxTicks = 70)
    public void shadowAmbushGoesBehind(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = hero("dagger", 30);
        hold(sp, w);
        Obj k = w.skills().get(0);
        // 대상이 없으면 거절 (재사용 대기 없음)
        h.assertFalse(Transcend.castSkill(sp, w, 0), "no target: refused");
        h.assertTrue(Transcend.cooldownLeft(sp, k) == 0, "no cooldown when refused");
        Husk z = dummy(h, 1.5, 2, 4.5, 180F); // 나를 봄 → 등 뒤는 남쪽 (+z)
        Vec3 start = sp.position();
        h.assertTrue(Transcend.castSkill(sp, w, 0), "cast");
        h.assertTrue(sp.getZ() > z.getZ() + 1.0 && sp.position().distanceTo(start) > 3, "moved behind: " + sp.position() + " husk " + z.position());
        double b = base(sp, w);
        double c = w.critMult();
        int gap = (int) k.num("gap");
        h.runAfterDelay(gap * 2 + 6, () -> {
            h.assertTrue(hitsMatch(TestDummies.dealt(z), b, c, k.num("mult"), k.num("mult"), k.num("afterMult")), "stab + clones + burst " + TestDummies.dealt(z));
            done(h, sp);
        });
    }

    @GameTest(environment = ENV, maxTicks = 80)
    public void earthFissureLaunchesAndErupts(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = hero("axe", 40);
        hold(sp, w);
        Obj k = w.skills().get(0);
        Husk z = dummy(h, 1.5, 2, 4.5, 180F);
        h.assertTrue(Transcend.castSkill(sp, w, 0), "cast");
        double b = base(sp, w);
        double c = w.critMult();
        h.runAfterDelay(6, () -> h.assertTrue(hitsMatch(TestDummies.dealt(z), b, c, k.num("mult")), "spike hit " + TestDummies.dealt(z)));
        h.runAfterDelay(40, () -> {
            h.assertTrue(hitsMatch(TestDummies.dealt(z), b, c, k.num("mult"), k.num("afterMult")), "spike + eruption " + TestDummies.dealt(z));
            done(h, sp);
        });
    }

    @GameTest(environment = ENV, maxTicks = 90)
    public void durandSmiteGuardsAndStuns(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = hero("shield", 35);
        hold(sp, w);
        Obj k = w.skills().get(0);
        Husk z = dummy(h, 1.5, 2, 4.0, 180F);
        h.assertTrue(Transcend.castSkill(sp, w, 0), "cast");
        Guards.Guard g = Guards.of(sp);
        h.assertTrue(g != null && Math.abs(g.reduce() - k.num("reduce")) < 1e-9, "guard " + (g == null ? null : g.reduce()));
        double b = base(sp, w);
        double c = w.critMult();
        h.runAfterDelay(2, () -> {
            MobEffectInstance slow = z.getEffect(MobEffects.SLOWNESS);
            h.assertTrue(slow != null && slow.getAmplifier() >= 10, "stunned");
            h.assertTrue(hitsMatch(TestDummies.dealt(z), b, c, k.num("mult")), "smite " + TestDummies.dealt(z));
        });
        h.runAfterDelay((int) k.num("delay") + 6, () -> {
            h.assertTrue(hitsMatch(TestDummies.dealt(z), b, c, k.num("mult"), k.num("afterMult")), "smite + pillar " + TestDummies.dealt(z));
            h.assertTrue(Guards.of(sp) != null, "guard still on");
        });
        h.runAfterDelay((int) k.num("guard") + 4, () -> {
            h.assertTrue(Guards.of(sp) == null, "guard ended");
            done(h, sp);
        });
    }

    @GameTest(environment = ENV, maxTicks = 100)
    public void elfVolleyFollowsOneTarget(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = hero("bow", 40);
        hold(sp, w);
        Obj k = w.skills().get(0);
        Husk z = dummy(h, 1.5, 2, 7.0, 180F);
        sp.lookAt(EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
        h.assertTrue(Transcend.castSkill(sp, w, 0), "cast");
        double b = base(sp, w);
        double c = w.critMult();
        double m = k.num("mult");
        // 한 명: 무적 시간을 피해 0.5초마다 한 발 → 처음 한 발은 flight 틱에
        h.runAfterDelay((int) k.num("flight") + 2, () -> h.assertTrue(hitsMatch(TestDummies.dealt(z), b, c, m), "first arrow " + TestDummies.dealt(z)));
        h.runAfterDelay(60, () -> {
            h.assertTrue(hitsMatch(TestDummies.dealt(z), b, c, m, m, m, m), "four arrows " + TestDummies.dealt(z) + " vs " + 4 * m * b);
            done(h, sp);
        });
    }

    @GameTest(environment = ENV, maxTicks = 60)
    public void elfVolleySplitsAndNeedsTarget(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = hero("bow", 30);
        hold(sp, w);
        Obj k = w.skills().get(0);
        sp.setXRot(-60F); // 하늘을 봄: 겨눌 적 없음
        h.assertFalse(Transcend.castSkill(sp, w, 0), "no target: refused");
        h.assertTrue(Transcend.cooldownLeft(sp, k) == 0, "no cooldown when refused");
        Husk a = dummy(h, 1.5, 2, 6.0, 180F);
        Husk bb = dummy(h, 4.5, 2, 6.5, 180F);
        Husk cc = dummy(h, 6.5, 2, 4.5, 180F);
        sp.lookAt(EntityAnchorArgument.Anchor.EYES, a.getEyePosition());
        h.assertTrue(Transcend.castSkill(sp, w, 0), "cast");
        double base = base(sp, w);
        double c = w.critMult();
        double m = k.num("mult");
        h.runAfterDelay(30, () -> {
            h.assertTrue(TestDummies.dealt(a) > 0 && TestDummies.dealt(bb) > 0 && TestDummies.dealt(cc) > 0, "all three hit "
                    + TestDummies.dealt(a) + " / " + TestDummies.dealt(bb) + " / " + TestDummies.dealt(cc));
            double sum = TestDummies.dealt(a) + TestDummies.dealt(bb) + TestDummies.dealt(cc);
            h.assertTrue(hitsMatch(sum, base, c, m, m, m, m), "four arrows in all " + sum);
            h.assertTrue(hitsMatch(TestDummies.dealt(a), base, c, m, m), "aimed one got two " + TestDummies.dealt(a));
            done(h, sp);
        });
    }

    @GameTest(environment = ENV, maxTicks = 70)
    public void lichHarvestSlowsAndHeals(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = hero("wand", 40);
        hold(sp, w);
        Obj k = w.skills().get(0);
        Husk z = dummy(h, 1.5, 2, 5.5, 180F);
        sp.lookAt(EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
        // 저절로 차는 체력 막기 (배고픔 17 · 포화 0) 후 체력 절반
        sp.getFoodData().setFoodLevel(17);
        sp.getFoodData().setSaturation(0F);
        sp.setHealth(sp.getMaxHealth() * 0.5F);
        float hp0 = sp.getHealth();
        h.assertTrue(Transcend.castSkill(sp, w, 0), "cast");
        double b = base(sp, w);
        double c = w.critMult();
        int bind = (int) k.num("bind");
        int delay = (int) k.num("delay");
        h.runAfterDelay(bind + 2, () -> {
            MobEffectInstance slow = z.getEffect(MobEffects.SLOWNESS);
            h.assertTrue(slow != null && slow.getAmplifier() == (int) k.num("slowAmp"), "slowed " + slow);
            h.assertTrue(hitsMatch(TestDummies.dealt(z), b, c, k.num("mult")), "bind hit " + TestDummies.dealt(z));
        });
        h.runAfterDelay(bind + delay + (int) k.num("back") + 6, () -> {
            double dealt = TestDummies.dealt(z);
            h.assertTrue(hitsMatch(dealt, b, c, k.num("mult"), k.num("afterMult")), "bind + harvest " + dealt);
            double want = Math.min(dealt * k.num("heal"), RpgHealth.maxOf(sp) * k.num("healCap"));
            double got = sp.getHealth() - hp0;
            h.assertTrue(Math.abs(got - want) < 0.05, "healed " + got + " vs " + want);
            done(h, sp);
        });
    }
}
