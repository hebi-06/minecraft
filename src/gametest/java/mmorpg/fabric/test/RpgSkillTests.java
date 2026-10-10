package mmorpg.fabric.test;

import java.lang.reflect.Method;
import java.util.List;
import mmorpg.core.Combat;
import mmorpg.core.Obj;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.RpgData;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.RpgWeaponItem;
import mmorpg.fabric.skill.Guards;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import mmorpg.fabric.skill.Transcend;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 스킬 공통 도구 · 초월 무기 입력 게임 시험 (서버): 재사용 대기 · 화면 하단 표시, 그람 화룡 돌진 피해 (mult + afterMult — 무적 시간을 피해 둘 다),
 * 스킬 피해 숫자 모양, 받는 피해 감소 · 반사, 속박 · 침묵, 활 길게 누르기 입력 (짧게 = 평타 · 웅크리고 = 스킬 1), 소리 대응표가 모두 자바 소리로 풀리는지.
 */
public final class RpgSkillTests {
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

    /**
     * 피해 합이 타격들 (배율 mults × 기본 b) 의 어떤 치명타 조합 (각각 × c 또는 그대로) 과 같은지.
     * 시험들이 한 세계에서 함께 돌아 RpgCombat.forceCrit (공용) 를 믿을 수 없어서 치명타도 맞는 것으로 봄
     */
    private static boolean hitsMatch(double dealt, double b, double c, double... mults) {
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

    /** 쓰러지지 않는 과녁 허스크 (햇빛에 타지 않음 — 받은 피해는 TestDummies.dealt) */
    private static Husk dummy(GameTestHelper h, double x, double y, double z) {
        return TestDummies.make(h.spawnWithNoFreeWill(EntityTypes.HUSK, (float) x, (float) y, (float) z));
    }

    private static Weapon transcend(String type) {
        return Weapons.parseWeapon("rpg:" + type + "_l40_transcend");
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

    // ------------------------------------------------------------ 재사용 대기 · 표시

    @GameTest(maxTicks = 40)
    public void cooldownAndHud(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = transcend("sword");
        hold(sp, w);
        Obj k = w.skills().get(0);
        h.assertTrue(Transcend.has("gram_charge") && Transcend.has("tyrfing_waking"), "skills registered");
        h.assertTrue(Transcend.hudText(sp, w).contains("§a준비"), "ready: " + Transcend.hudText(sp, w));
        h.assertTrue(Transcend.castSkill(sp, w, 0), "first cast");
        h.assertTrue(Transcend.cooldownLeft(sp, k) == Math.round(k.num("cd") * 20), "cooldown " + Transcend.cooldownLeft(sp, k));
        h.assertFalse(Transcend.castSkill(sp, w, 0), "second cast blocked by cooldown");
        h.assertTrue(Transcend.hudText(sp, w).contains("12.0초"), "hud shows cooldown: " + Transcend.hudText(sp, w));
        Transcend.resetCooldowns(sp);
        h.assertTrue(Transcend.cooldownLeft(sp, k) == 0, "reset");
        h.runAfterDelay(20, () -> done(h, sp));
    }

    // ------------------------------------------------------------ 스킬 피해 (그람 화룡 돌진)

    @GameTest(maxTicks = 60)
    public void gramChargeHitsTwice(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = transcend("sword");
        hold(sp, w);
        Obj k = w.skills().get(0);
        Husk z = dummy(h, 1.5, 2, 4.5);
        double z0 = sp.getZ();
        h.assertTrue(Transcend.castSkill(sp, w, 0), "cast");
        h.assertTrue(sp.getZ() > z0 + 0.9, "dashed forward: " + z0 + " → " + sp.getZ());
        double b = base(sp, w);
        double c = w.critMult();
        h.runAfterDelay(2, () -> h.assertTrue(hitsMatch(TestDummies.dealt(z), b, c, k.num("mult")),
                "first hit " + TestDummies.dealt(z) + " vs " + k.num("mult") * b));
        h.runAfterDelay((int) k.num("delay") + 4, () -> {
            h.assertTrue(hitsMatch(TestDummies.dealt(z), b, c, k.num("mult"), k.num("afterMult")),
                    "both hits " + TestDummies.dealt(z) + " vs " + (k.num("mult") + k.num("afterMult")) * b);
            done(h, sp);
        });
    }

    @GameTest(maxTicks = 40)
    public void skillNumbersUseSkillStyle(GameTestHelper h) {
        ServerPlayer sp = player(h);
        Husk z = dummy(h, 3.5, 2, 3.5);
        h.assertTrue(Kit.applySkillDamage(sp, z, 12.34, false, Kit.Cause.ATTACK, DamageNumbers.Style.RUNE), "damage applied");
        List<Display.TextDisplay> nums = h.getLevel().getEntitiesOfClass(Display.TextDisplay.class, new AABB(z.getEyePosition(), z.getEyePosition()).inflate(3),
                e -> e.entityTags().contains(DamageNumbers.TAG));
        h.assertTrue(nums.size() == 1, "one number, got " + nums.size());
        String text = nums.get(0).getText().getString();
        h.assertTrue(text.contains("§e") && text.contains("12.3"), "rune style number: " + text);
        // 무적 시간 안의 두 번째 (같은 양) 는 들어가지 않음
        h.assertFalse(Kit.applySkillDamage(sp, z, 12.34, false, Kit.Cause.ATTACK, null), "blocked by i-frames");
        h.assertTrue(Math.abs(TestDummies.dealt(z) - 12.34) < 1e-3, "dealt once: " + TestDummies.dealt(z));
        done(h, sp);
    }

    // ------------------------------------------------------------ 감소 · 반사 · 상태

    @GameTest(maxTicks = 40)
    public void guardReducesAndReflects(GameTestHelper h) {
        ServerPlayer sp = player(h);
        Husk z = dummy(h, 2.5, 2, 2.5);
        Guards.set(sp, 40, 0.6, Guards.Reflect.amount(5, DamageNumbers.Style.RUNE), null);
        h.assertTrue(Guards.of(sp) != null && Math.abs(Guards.of(sp).reduce() - 0.6) < 1e-9, "guard on");
        float next = RpgHealth.onLoss(sp, sp.damageSources().mobAttack(z), 20F, 10F);
        h.assertTrue(Math.abs(next - (20 - 10 * 0.4)) < 1e-3, "60% less: " + next);
        h.runAfterDelay(3, () -> {
            h.assertTrue(Math.abs(TestDummies.dealt(z) - 5) < 1e-3, "reflected 5, got " + TestDummies.dealt(z));
            Guards.clear(sp.getUUID());
            done(h, sp);
        });
    }

    @GameTest(maxTicks = 40)
    public void rootAndSilence(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = transcend("sword");
        hold(sp, w);
        double speed = sp.getAttributeValue(Attributes.MOVEMENT_SPEED);
        Status.root(sp, 10, "속박");
        h.assertTrue(Status.isRooted(sp) && sp.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0, "rooted: speed " + sp.getAttributeValue(Attributes.MOVEMENT_SPEED));
        Status.silence(sp, 10);
        h.assertFalse(Transcend.castSkill(sp, w, 0), "silenced: no skill");
        h.runAfterDelay(14, () -> {
            h.assertFalse(Status.isRooted(sp), "root ended");
            h.assertTrue(Math.abs(sp.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < 1e-9, "speed back");
            h.assertFalse(Status.isSilenced(sp), "silence ended");
            done(h, sp);
        });
    }

    // ------------------------------------------------------------ 활 입력 (짧게 = 평타 · 웅크리고 = 스킬 1)

    @GameTest(maxTicks = 80)
    public void bowShortPressShootsAndSneakCasts(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = transcend("bow");
        hold(sp, w);
        Husk z = dummy(h, 1.5, 2, 6.5);
        sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
        ItemStack bow = sp.getMainHandItem();
        bow.use(h.getLevel(), sp, InteractionHand.MAIN_HAND);
        h.assertTrue(sp.isUsingItem(), "holding the bow");
        h.runAfterDelay(3, () -> {
            sp.releaseUsingItem(); // 짧게 → 평타
            h.assertFalse(sp.isUsingItem(), "released");
        });
        double shot = base(sp, w);
        h.runAfterDelay(20, () -> {
            h.assertTrue(hitsMatch(TestDummies.dealt(z), shot, w.critMult(), 1), "basic shot " + TestDummies.dealt(z) + " vs " + shot);
            // 웅크리고 누르면 스킬 1 (은월 관통) — 재사용 대기 시작
            sp.setShiftKeyDown(true);
            bow.use(h.getLevel(), sp, InteractionHand.MAIN_HAND);
            sp.setShiftKeyDown(false);
            h.assertTrue(Transcend.cooldownLeft(sp, w.skills().get(0)) > 0, "sneak cast skill 1");
            done(h, sp);
        });
    }

    // ------------------------------------------------------------ 소리

    @GameTest
    public void soundTableResolves(GameTestHelper h) {
        StringBuilder missing = new StringBuilder();
        for (String name : SkillFx.soundNames()) if (SkillFx.sound(name) == null) missing.append(name).append(' ');
        h.assertTrue(missing.isEmpty(), "unresolved sounds: " + missing);
        h.succeed();
    }
}
