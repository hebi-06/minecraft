package mmorpg.fabric.test;

import java.util.List;
import java.util.regex.Pattern;
import mmorpg.core.Combat;
import mmorpg.core.Hp;
import mmorpg.core.Rules;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgData;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.RpgWeaponItem;
import mmorpg.fabric.StatSheet;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * 게임 시험 (서버를 띄워 실제 플레이어 · 몹으로): ./gradlew runGametest. 하나라도 실패하면 빌드 실패.
 * 수치는 규칙 코어 (애드온 JS 와 같은 값이 검증된 Java 포트) 로 계산한 기대값과 비교.
 */
public final class RpgGameTests implements FabricGameTest {
    private static final double EPS = 1e-3;
    private static final Pattern BEDROCK_ONLY_CODE = Pattern.compile("§[^0-9a-fk-or]");

    // ------------------------------------------------------------ 도움

    /** 시험용 플레이어 (생존 모드, 시험 자리 (1.5, 2, 1.5) 에서 +z 를 봄) */
    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        sp.setGameMode(GameType.SURVIVAL);
        Vec3 at = h.absoluteVec(new Vec3(1.5, 2, 1.5));
        sp.moveTo(at.x, at.y, at.z, 0F, 0F);
        RpgPlayers.onJoin(sp);
        return sp;
    }

    private static void done(GameTestHelper h, ServerPlayer sp) {
        h.getLevel().getServer().getPlayerList().remove(sp);
        h.succeed();
    }

    /** 방어 0 · 체력 500 인 움직이지 않는 좀비 */
    private static Zombie dummy(GameTestHelper h, double x, double y, double z) {
        Zombie zb = h.spawnWithNoFreeWill(EntityType.ZOMBIE, (float) x, (float) y, (float) z);
        zb.getAttribute(Attributes.ARMOR).setBaseValue(0);
        zb.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
        zb.setHealth(500);
        return zb;
    }

    private static ItemStack stackOf(Weapon w) {
        RpgWeaponItem item = RpgItems.byId(w.id());
        if (item == null) throw new IllegalStateException("not registered: " + w.id());
        return new ItemStack(item);
    }

    /** 무기를 쓸 수 있게 레벨 · 요구 스탯을 맞춤 (주 스탯은 최소 extra) */
    private static void meetReqs(ServerPlayer sp, Weapon w, int extra) {
        if (sp.experienceLevel < w.level()) {
            sp.setExperienceLevels(w.level());
            RpgPlayers.checkLevelUp(sp);
        }
        for (Weapons.Req r : w.reqs()) RpgPlayers.setStat(sp, r.stat(), (int) Math.max(Math.ceil(r.value()), r.stat().equals(w.stat()) ? extra : 0));
    }

    private static List<Display.TextDisplay> numbersNear(GameTestHelper h, Vec3 at) {
        return h.getLevel().getEntitiesOfClass(Display.TextDisplay.class, new net.minecraft.world.phys.AABB(at, at).inflate(3),
                e -> e.getTags().contains(DamageNumbers.TAG));
    }

    private static String textOf(Display.TextDisplay e) {
        return e.saveWithoutId(new CompoundTag()).getString("text");
    }

    private static void near(GameTestHelper h, double actual, double expected, double eps, String what) {
        h.assertTrue(Math.abs(actual - expected) <= eps, what + ": expected " + expected + ", got " + actual);
    }

    // ------------------------------------------------------------ 스탯 · 레벨

    @GameTest(template = EMPTY_STRUCTURE)
    public void startPoints(GameTestHelper h) {
        ServerPlayer sp = player(h);
        RpgData d = RpgData.of(sp);
        h.assertTrue(d.started(), "started");
        near(h, d.point(), Rules.get().obj("CONFIG").num("START_POINTS"), 0, "start points");
        near(h, sp.getMaxHealth(), Hp.maxHpOf(0, 0), EPS, "max hp at level 0");
        near(h, sp.getHealth(), sp.getMaxHealth(), EPS, "full health");
        // 다시 접속해도 시작 포인트를 또 받지 않음
        RpgPlayers.onJoin(sp);
        near(h, RpgData.of(sp).point(), d.point(), 0, "no second start points");
        done(h, sp);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void levelUpPoints(GameTestHelper h) {
        ServerPlayer sp = player(h);
        int start = RpgData.of(sp).point();
        sp.setExperienceLevels(10);
        int gained = RpgPlayers.checkLevelUp(sp);
        near(h, gained, 10 * Rules.get().obj("CONFIG").num("POINTS_PER_LEVEL"), 0, "points for 10 levels");
        RpgData d = RpgData.of(sp);
        near(h, d.point(), start + gained, 0, "points");
        near(h, d.lvmax(), 10, 0, "lvmax");
        near(h, sp.getMaxHealth(), Hp.maxHpOf(10, 0), EPS, "max hp at level 10");
        near(h, sp.getHealth(), sp.getMaxHealth(), EPS, "level bonus fills health");
        h.assertTrue(RpgPlayers.checkLevelUp(sp) == 0, "no points twice");
        // 레벨이 내려갔다 다시 올라도 포인트 없음, 최대 체력 그대로 (최고 레벨 기록)
        sp.setExperienceLevels(4);
        h.assertTrue(RpgPlayers.checkLevelUp(sp) == 0, "no points after level loss");
        near(h, sp.getMaxHealth(), Hp.maxHpOf(10, 0), EPS, "max hp keeps lvmax");
        sp.setExperienceLevels(10);
        h.assertTrue(RpgPlayers.checkLevelUp(sp) == 0, "no points for same level");
        done(h, sp);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void spendVitRaisesMaxHp(GameTestHelper h) {
        ServerPlayer sp = player(h);
        RpgPlayers.addPoints(sp, 10);
        int pts = RpgData.of(sp).point();
        sp.setHealth(10F);
        int n = RpgPlayers.spend(sp, "vit", 5);
        h.assertTrue(n == 5, "spent 5, got " + n);
        RpgData d = RpgData.of(sp);
        near(h, d.vit(), 5, 0, "vit");
        near(h, d.point(), pts - 5, 0, "points left");
        near(h, sp.getMaxHealth(), Hp.maxHpOf(0, 5), EPS, "max hp with vit 5");
        near(h, sp.getHealth(), 15, EPS, "health rises by the increase");
        // 포인트보다 많이: 있는 만큼만
        int m = RpgPlayers.spend(sp, "str", 1000);
        near(h, m, pts - 5, 0, "spend all left");
        near(h, RpgData.of(sp).point(), 0, 0, "no points left");
        h.assertTrue(RpgPlayers.spend(sp, "str", 1) == 0, "cannot spend without points");
        h.assertTrue(RpgPlayers.spend(sp, "luck", 1) == 0, "unknown stat");
        // 초기화: 찍은 만큼 돌려받고 최대 체력이 줄어듦
        int refund = RpgPlayers.reset(sp);
        near(h, refund, pts, 0, "refund");
        near(h, sp.getMaxHealth(), Hp.maxHpOf(0, 0), EPS, "max hp after reset");
        h.assertTrue(sp.getHealth() <= sp.getMaxHealth() + EPS, "health within max");
        done(h, sp);
    }

    // ------------------------------------------------------------ 체력

    @GameTest(template = EMPTY_STRUCTURE)
    public void healScalesWithMaxHp(GameTestHelper h) {
        ServerPlayer sp = player(h);
        RpgPlayers.setStat(sp, "vit", 80); // 최대 100
        near(h, sp.getMaxHealth(), 100, EPS, "max hp 100");
        sp.setHealth(50F);
        sp.heal(1F);
        near(h, sp.getHealth(), 55, EPS, "heal 1 = 5% of max");
        sp.setGameMode(GameType.CREATIVE);
        sp.setHealth(50F);
        sp.heal(1F);
        near(h, sp.getHealth(), 51, EPS, "creative heal unchanged");
        done(h, sp);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void damageAndShield(GameTestHelper h) {
        ServerPlayer sp = player(h);
        RpgPlayers.setStat(sp, "vit", 80);
        sp.setHealth(100F);
        sp.invulnerableTime = 0;
        sp.hurt(sp.damageSources().fellOutOfWorld(), 10F);
        near(h, sp.getHealth(), 90, EPS, "plain damage");
        Weapon shield = Weapons.weaponFrom("shield", 0, "common");
        meetReqs(sp, shield, 80);
        sp.setItemInHand(InteractionHand.MAIN_HAND, stackOf(shield));
        double r = Math.min(Rules.get().obj("SHIELD").num("passiveCap"), shield.passive() + 80 * Rules.get().obj("SHIELD").num("passivePerVit"));
        sp.invulnerableTime = 0;
        sp.hurt(sp.damageSources().fellOutOfWorld(), 10F);
        near(h, sp.getHealth(), 90 - 10 * (1 - r), EPS, "shield passive reduction");
        // 독 · 굶주림: 1 에서 멈춤
        sp.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
        near(h, RpgHealth.onLoss(sp, sp.damageSources().magic(), 1.5F, 0.5F), 1, EPS, "poison stops at 1");
        h.assertTrue(RpgHealth.onLoss(sp, sp.damageSources().fellOutOfWorld(), 1.5F, 0.5F) < 1, "void is lethal");
        done(h, sp);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void deathAndRespawnHealth(GameTestHelper h) {
        ServerPlayer sp = player(h);
        RpgPlayers.setStat(sp, "vit", 30);
        sp.invulnerableTime = 0;
        sp.hurt(sp.damageSources().fellOutOfWorld(), 1000F);
        h.assertTrue(sp.isDeadOrDying(), "dead");
        RpgHealth.onRespawn(sp, false, 0F);
        near(h, sp.getHealth(), Hp.maxHpOf(0, 30), EPS, "respawn full");
        done(h, sp);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 140)
    public void totemRevivesTenPercent(GameTestHelper h) {
        ServerPlayer sp = player(h);
        RpgPlayers.setStat(sp, "vit", 80); // 최대 100 → 10
        // 접속 직후 3초는 무적 (바닐라) → 지난 뒤
        h.runAfterDelay(70, () -> {
            sp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            sp.invulnerableTime = 0;
            sp.hurt(sp.damageSources().magic(), 1000F);
            h.assertTrue(!sp.isDeadOrDying(), "totem saved");
            near(h, sp.getHealth(), Hp.reviveHp(100), EPS, "revive 10%");
            done(h, sp);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void hpRatioSaved(GameTestHelper h) {
        ServerPlayer sp = player(h);
        RpgPlayers.setStat(sp, "vit", 30); // 50
        sp.setHealth(20F);
        RpgHealth.saveRatio(sp, true);
        near(h, RpgData.of(sp).hpRatio(), Hp.saveRatio(false, 20, 50), 1e-9, "saved ratio");
        sp.setHealth(50F);
        RpgHealth.onJoin(sp); // 다시 접속: 저장한 비율로
        near(h, sp.getHealth(), 20, EPS, "restored health");
        done(h, sp);
    }

    // ------------------------------------------------------------ 무기

    @GameTest(template = EMPTY_STRUCTURE)
    public void weaponsRegistered(GameTestHelper h) {
        h.assertTrue(RpgItems.WEAPONS.size() == 151, "151 weapons, got " + RpgItems.WEAPONS.size());
        for (RpgWeaponItem item : RpgItems.WEAPONS) {
            Weapon w = item.weapon();
            h.assertTrue(!w.legacy(), "legacy registered: " + w.id());
            String name = BedrockText.toJava(w.name());
            h.assertTrue(!BEDROCK_ONLY_CODE.matcher(name).find(), "bedrock code in name " + name);
            for (String line : Weapons.weaponInfo(w)) {
                String j = BedrockText.toJava(line);
                h.assertTrue(!BEDROCK_ONLY_CODE.matcher(j).find(), "bedrock code in lore " + j);
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void meleeDamageFormula(GameTestHelper h) {
        ServerPlayer sp = player(h);
        Weapon w = Weapons.weaponFrom("sword", 0, "common");
        meetReqs(sp, w, 10);
        sp.setItemInHand(InteractionHand.MAIN_HAND, stackOf(w));
        Zombie z = dummy(h, 1.5, 2, 3.5);
        float s = sp.getAttackStrengthScale(0.5F);
        RpgCombat.forceCrit = false;
        sp.attack(z);
        RpgCombat.forceCrit = null;
        double expected = w.damage() * Combat.powerMultiplier(RpgData.of(sp).effective(), w, 0) * (0.2 + s * s * 0.8);
        near(h, 500 - z.getHealth(), expected, 1e-3, "melee damage (strength " + s + ")");
        List<Display.TextDisplay> nums = numbersNear(h, z.getEyePosition());
        h.assertTrue(!nums.isEmpty(), "damage number shown");
        String want = DamageNumbers.number(expected);
        h.assertTrue(nums.stream().anyMatch(e -> textOf(e).contains(want)), "number text " + want + " in " + nums.stream().map(RpgGameTests::textOf).toList());
        // 치명타: 배율 · 금색 "!"
        z.invulnerableTime = 0;
        float hp0 = z.getHealth();
        float s2 = sp.getAttackStrengthScale(0.5F);
        RpgCombat.forceCrit = true;
        sp.attack(z);
        RpgCombat.forceCrit = null;
        double crit = w.damage() * Combat.powerMultiplier(RpgData.of(sp).effective(), w, 0) * (0.2 + s2 * s2 * 0.8) * w.critMult();
        near(h, hp0 - z.getHealth(), crit, 1e-3, "crit damage");
        String wantCrit = DamageNumbers.number(crit) + "!";
        h.assertTrue(numbersNear(h, z.getEyePosition()).stream().anyMatch(e -> textOf(e).contains(wantCrit)), "crit number " + wantCrit);
        done(h, sp);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void requirementBlocksAttack(GameTestHelper h) {
        ServerPlayer sp = player(h);
        Weapon w = Weapons.weaponFrom("sword", 10, "rare"); // 레벨 10 · 힘 필요
        sp.setItemInHand(InteractionHand.MAIN_HAND, stackOf(w));
        Zombie z = dummy(h, 1.5, 2, 3.5);
        sp.attack(z);
        near(h, z.getHealth(), 500, 0, "no damage without requirements");
        done(h, sp);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void bowHitsTarget(GameTestHelper h) {
        ServerPlayer sp = player(h);
        Weapon w = Weapons.weaponFrom("bow", 0, "common");
        meetReqs(sp, w, 10);
        ItemStack bow = stackOf(w);
        sp.setItemInHand(InteractionHand.MAIN_HAND, bow);
        Zombie z = dummy(h, 1.5, 2, 6.5);
        sp.lookAt(EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
        RpgCombat.fireRanged(sp, bow, w, null);
        double base = w.damage() * Combat.powerMultiplier(RpgData.of(sp).effective(), w, 0);
        h.succeedWhen(() -> {
            double dealt = 500 - z.getHealth();
            h.assertTrue(dealt > 0, "bow hit");
            h.assertTrue(Math.abs(dealt - base) < 1e-3 || Math.abs(dealt - base * w.critMult()) < 1e-3, "bow damage " + dealt + " vs " + base);
            h.getLevel().getServer().getPlayerList().remove(sp);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80)
    public void wandCastsAtClickedMob(GameTestHelper h) {
        ServerPlayer sp = player(h);
        Weapon w = Weapons.weaponFrom("wand", 0, "common");
        meetReqs(sp, w, 10);
        sp.setItemInHand(InteractionHand.MAIN_HAND, stackOf(w));
        Zombie z = dummy(h, 1.5, 2, 4.5);
        sp.attack(z); // 완드로 몹을 치면 그 몹에게 시전
        near(h, z.getHealth(), 500, 0, "no instant damage (cast first)");
        double base = w.damage() * Combat.powerMultiplier(RpgData.of(sp).effective(), w, 0);
        h.succeedWhen(() -> {
            double dealt = 500 - z.getHealth();
            h.assertTrue(dealt > 0, "wand hit");
            h.assertTrue(Math.abs(dealt - base) < 1e-3 || Math.abs(dealt - base * w.critMult()) < 1e-3, "wand damage " + dealt + " vs " + base);
            h.getLevel().getServer().getPlayerList().remove(sp);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60)
    public void damageNumberRisesAndVanishes(GameTestHelper h) {
        Zombie z = dummy(h, 3.5, 2, 3.5);
        DamageNumbers.show(z, 12.34, DamageNumbers.Style.NORMAL, "", 0);
        List<Display.TextDisplay> nums = numbersNear(h, z.getEyePosition());
        h.assertTrue(nums.size() == 1, "one number, got " + nums.size());
        Display.TextDisplay e = nums.get(0);
        h.assertTrue(textOf(e).contains("12.3"), "text 12.3: " + textOf(e));
        double y0 = e.getY();
        h.runAfterDelay(5, () -> h.assertTrue(e.getY() > y0 + 0.3, "rises"));
        h.runAfterDelay(22, () -> {
            h.assertTrue(e.isRemoved(), "removed after 18 ticks");
            h.succeed();
        });
    }

    // ------------------------------------------------------------ 스탯 창 · 명령어

    @GameTest(template = EMPTY_STRUCTURE)
    public void statSheetText(GameTestHelper h) {
        StatSheet s = StatSheet.of(10, 0, 0, 5, 3, 10, 10);
        near(h, s.maxHp(), Hp.maxHpOf(10, 5), EPS, "sheet max hp");
        h.assertTrue(s.statLine("str", false).contains("+20%"), s.statLine("str", false));
        h.assertTrue(s.effect("vit").contains(mmorpg.core.Js.str(Hp.maxHpOf(10, 5))), s.effect("vit"));
        Weapon w = Weapons.weaponFrom("sword", 10, "rare");
        List<String> lines = s.weaponLines(w, false);
        h.assertTrue(lines.get(0).contains("기준 무기"), lines.get(0));
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void weaponCommand(GameTestHelper h) {
        ServerPlayer sp = player(h);
        h.getLevel().getServer().getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4), "weapon sword 10 rare");
        RpgWeaponItem item = RpgItems.byId("rpg:sword_l10_rare");
        h.assertTrue(sp.getInventory().contains(new ItemStack(item)), "weapon given");
        h.getLevel().getServer().getCommands().performPrefixedCommand(sp.createCommandSourceStack().withPermission(4), "points 7");
        h.getLevel().getServer().getCommands().performPrefixedCommand(sp.createCommandSourceStack(), "rpg spend str 3");
        RpgData d = RpgData.of(sp);
        near(h, d.str(), 3, 0, "spent by command");
        done(h, sp);
    }
}
