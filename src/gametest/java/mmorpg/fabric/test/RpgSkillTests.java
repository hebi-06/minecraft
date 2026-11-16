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
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 스킬 공통 도구 게임 시험 (서버): 무기 스킬 없음 (2026-11-16 지움 — 영웅 · 초월 무기도 스킬 줄 없음), 활은 누르면 바로 평타, 스킬 피해 숫자 모양,
 * 받는 피해 감소 · 반사, 속박 · 침묵, 소리 대응표가 모두 자바 소리로 풀리는지.
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

    // ------------------------------------------------------------ 2026-11-16 무기 스킬 없음 (사용자 "무기 스킬도 빼고 대신 무기 디자인을 늘리건데")

    @GameTest(maxTicks = 20)
    public void weaponsHaveNoSkills(GameTestHelper h) {
        for (String type : Weapons.allTypes()) {
            for (String g : List.of("hero", "transcend")) {
                for (double lv : List.of(30.0, 40.0, 60.0)) {
                    Weapon w = Weapons.weaponFrom(type, lv, g);
                    if (w == null) continue;
                    h.assertTrue(w.skills() == null, type + " " + g + " " + lv + " has no weapon skill");
                    h.assertTrue(Weapons.weaponInfo(w).stream().noneMatch(l -> l.contains("스킬1") || l.contains(": 스킬")), type + " " + g + " no skill lines");
                }
            }
        }
        h.succeed();
    }

    // ------------------------------------------------------------ 활 입력 (누르면 바로 평타 — 길게 누르기 없음)

    @GameTest(maxTicks = 60)
    public void bowPressShoots(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = transcend("bow");
        hold(sp, w);
        Husk z = dummy(h, 1.5, 2, 6.5);
        sp.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
        ItemStack bow = sp.getMainHandItem();
        bow.use(h.getLevel(), sp, InteractionHand.MAIN_HAND);
        h.assertFalse(sp.isUsingItem(), "no hold (no weapon skill)");
        double shot = base(sp, w);
        h.runAfterDelay(20, () -> {
            h.assertTrue(hitsMatch(TestDummies.dealt(z), shot, w.critMult(), 1), "basic shot " + TestDummies.dealt(z) + " vs " + shot);
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
        // 2026-11-11 숫자 색 규칙: 무속성 · 치명타 아님 = 흰색 (예전 룬 노랑 §e 대신)
        h.assertTrue(text.contains("12.3") && colorOf(nums.get(0), "12.3") == DamageNumbers.WHITE, "rune number white: " + text + " " + colorOf(nums.get(0), "12.3"));
        // 무적 시간 안의 두 번째 (같은 양) 는 들어가지 않음
        h.assertFalse(Kit.applySkillDamage(sp, z, 12.34, false, Kit.Cause.ATTACK, null), "blocked by i-frames");
        h.assertTrue(Math.abs(TestDummies.dealt(z) - 12.34) < 1e-3, "dealt once: " + TestDummies.dealt(z));
        // 치명타 = 노랑, 원소 피해 = 원소 색 (치명타 = 짙게)
        Husk z2 = dummy(h, 6.5, 2, 3.5);
        h.assertTrue(Kit.applySkillDamage(sp, z2, 23.45, true, Kit.Cause.ATTACK, DamageNumbers.Style.RUNE), "crit damage applied");
        h.assertTrue(numberColor(h, z2, "23.5") == DamageNumbers.YELLOW, "crit number yellow " + numberColor(h, z2, "23.5"));
        Husk z3 = dummy(h, 3.5, 2, 6.5);
        mmorpg.fabric.job.ElementSystem.with("fire", 10, false, () -> Kit.applySkillDamage(sp, z3, 34.56, false, Kit.Cause.ATTACK, DamageNumbers.Style.RUNE));
        int fire = numberColor(h, z3, "34.6");
        h.assertTrue(!mmorpg.core.Elements.on() || fire == DamageNumbers.ruleColor(false, "fire"), "fire number orange " + Integer.toHexString(fire));
        Husk z4 = dummy(h, 6.5, 2, 6.5);
        mmorpg.fabric.job.ElementSystem.with("ice", 10, false, () -> Kit.applySkillDamage(sp, z4, 45.67, true, Kit.Cause.ATTACK, DamageNumbers.Style.RUNE));
        int ice = numberColor(h, z4, "45.7");
        h.assertTrue(!mmorpg.core.Elements.on() || ice == DamageNumbers.ruleColor(true, "ice"), "ice crit number deep blue " + Integer.toHexString(ice));
        h.assertTrue(DamageNumbers.ruleColor(true, "ice") != DamageNumbers.ruleColor(false, "ice"), "crit element color differs");
        done(h, sp);
    }

    /** 피해 숫자 글자 조각 중 text 를 담은 조각의 색 (없으면 -1) */
    static int colorOf(Display.TextDisplay td, String text) {
        for (net.minecraft.network.chat.Component c : td.getText().getSiblings()) {
            if (c.getString().contains(text) && c.getStyle().getColor() != null) return c.getStyle().getColor().getValue();
        }
        return -1;
    }

    /** 대상 둘레 숫자 중 text 를 담은 것의 색 */
    static int numberColor(GameTestHelper h, LivingEntity z, String text) {
        for (Display.TextDisplay td : h.getLevel().getEntitiesOfClass(Display.TextDisplay.class, new AABB(z.getEyePosition(), z.getEyePosition()).inflate(3),
                e -> e.entityTags().contains(DamageNumbers.TAG))) {
            int c = colorOf(td, text);
            if (c >= 0) return c;
        }
        return -1;
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
        h.assertTrue(Status.isSilenced(sp), "silenced");
        h.runAfterDelay(14, () -> {
            h.assertFalse(Status.isRooted(sp), "root ended");
            h.assertTrue(Math.abs(sp.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed) < 1e-9, "speed back");
            h.assertFalse(Status.isSilenced(sp), "silence ended");
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
