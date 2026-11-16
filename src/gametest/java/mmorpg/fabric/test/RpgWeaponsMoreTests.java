package mmorpg.fabric.test;

import java.lang.reflect.Method;
import java.util.List;
import mmorpg.core.Combat;
import mmorpg.core.Grade;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Runes;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgData;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.RpgWeaponItem;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.skill.Guards;
import mmorpg.fabric.smith.Smith;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 12단계 새 무기 게임 시험 (서버): 창 · 낫 · 전투 망치 · 성스러운 홀 × 고귀 · 영웅 · 초월 24개의 등록 · 조합 · 무기 스킬 없음 (2026-11-16 지움) · 창의 사거리 속성,
 * 기본 공격 특징 (낫 넓게 베기 · 망치 내려찍기 — 다른 적에게 준 피해의 비율, 앞 · 둘레 밖은 안 맞음 · 홀 회복), 대장장이 제작 (고귀 쪽 — 전리품 + 실버만),
 * 정령왕 강림 (합 ×32 · 6돌 ×50 — 시험 환경 mmorpg:w_king · w_king6).
 */
public final class RpgWeaponsMoreTests {
    static final String ENV = "mmorpg:weapons";

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
        RpgCombat.forceCrit = null;
        h.getLevel().getServer().getPlayerList().remove(sp);
        h.succeed();
    }

    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }

    /** 쓰러지지 않는 과녁 허스크 (불에 타지 않음 — 받은 피해는 TestDummies.dealt). yaw 180 = 나를 봄 */
    private static Husk dummy(GameTestHelper h, double x, double y, double z) {
        Husk m = TestDummies.make(h.spawnWithNoFreeWill(EntityTypes.HUSK, (float) x, (float) y, (float) z));
        m.setYRot(180F);
        m.setYHeadRot(180F);
        m.setYBodyRot(180F);
        m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60, 0, false, false));
        return m;
    }

    private static Weapon weapon(String type, int level, String grade) {
        return Weapons.weaponFrom(type, level, grade);
    }

    private static void hold(ServerPlayer sp, Weapon w) {
        if (sp.experienceLevel < w.level()) {
            sp.setExperienceLevels(w.level());
            RpgPlayers.checkLevelUp(sp);
        }
        for (String s : List.of("str", "dex", "int", "vit")) RpgPlayers.setStat(sp, s, 150);
        sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RpgItems.byId(w.id())));
    }

    private static double base(ServerPlayer sp, Weapon w) {
        return w.damage() * Combat.powerMultiplier(RpgData.of(sp).effective(), w, 0);
    }

    /** 피해 합이 타격들 (배율 mults × 기본 b) 의 어떤 치명타 조합 (각각 × c 또는 그대로) 과 같은지 (시험들이 한 세계에서 함께 돌아 치명타도 맞는 것으로 봄) */
    static boolean hitsMatch(double dealt, double b, double c, double... mults) {
        for (int mask = 0; mask < (1 << mults.length); mask++) {
            double sum = 0;
            for (int i = 0; i < mults.length; i++) sum += mults[i] * b * ((mask >> i & 1) == 1 ? c : 1);
            if (Math.abs(dealt - sum) < 1e-3) return true;
        }
        return false;
    }

    // ------------------------------------------------------------ 등록 · 조작

    @GameTest(environment = ENV, maxTicks = 20)
    public void newWeaponsRegistered(GameTestHelper h) {
        ServerPlayer sp = player(h);
        int n = 0;
        for (String type : Weapons.moreTypes()) {
            for (Grade g : Rules.get().grades()) {
                for (double lv : Rules.get().numbers("ALL_LEVELS")) {
                    boolean valid = Weapons.isValidWeapon(lv, g.id(), type);
                    RpgWeaponItem item = RpgItems.byId(Weapons.weaponId(type, lv, g.id()));
                    h.assertTrue((item != null) == valid, type + " " + g.id() + " " + lv + " registered " + valid);
                    if (item == null) continue;
                    n++;
                    Weapon w = item.weapon();
                    h.assertTrue(Combat.isMeleeWeapon(w), w.id() + " melee");
                    h.assertTrue(w.skills() == null, w.id() + " no weapon skill (2026-11-16)");
                }
            }
        }
        h.assertTrue(n == 96, "96 new weapons (일반 · 희귀 56 + 고귀 · 영웅 · 초월 24 + 0.36.0 초월 45 ~ 60렙 16), got " + n);
        // 창: 몹을 칠 수 있는 거리 +1 (아이템 속성 — 손에 들면)
        ItemStack lance = new ItemStack(RpgItems.byId("rpg:lance_l40_transcend"));
        ItemAttributeModifiers mods = lance.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        h.assertTrue(mods.modifiers().stream().anyMatch(e -> e.attribute().equals(Attributes.ENTITY_INTERACTION_RANGE) && Math.abs(e.modifier().amount() - 1) < 1e-9),
                "lance reach +1 " + mods.modifiers());
        ItemStack hammer = new ItemStack(RpgItems.byId("rpg:hammer_l40_transcend"));
        h.assertTrue(hammer.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
                .noneMatch(e -> e.attribute().equals(Attributes.ENTITY_INTERACTION_RANGE)), "hammer keeps the normal reach");
        done(h, sp);
    }

    // ------------------------------------------------------------ 기본 공격 특징

    /** 0.39.0: 낫의 넓게 베기 (40%) 는 참격 범위 피해 (앞 120° · 100% — SwingArea) 로 바뀜 (1틱 뒤) */
    @GameTest(environment = ENV, maxTicks = 30)
    public void scytheSweepHitsOthersInFront(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = weapon("scythe", 20, "noble");
        hold(sp, w);
        Husk a = dummy(h, 1.5, 2, 3.5); // 맞힌 적 (앞 2칸)
        Husk b = dummy(h, 3.0, 2, 3.5); // 앞 비스듬히 (약 37°) — 참격 범위
        Husk c = dummy(h, 1.5, 2, 0.6); // 뒤 — 안 맞음 (시험 자리 가장자리의 barrier 벽에 눈이 걸려 숨 막힘 피해를 받지 않게 0.6)
        RpgCombat.forceCrit = false;
        double[] main = new double[1];
        h.runAfterDelay(2, () -> {
            sp.attack(a);
            main[0] = TestDummies.dealt(a);
            h.assertTrue(main[0] > 0, "main hit " + main[0]);
        });
        h.runAfterDelay(4, () -> {
            double x = TestDummies.dealt(b);
            double cm = w.critMult(); // 함께 도는 시험이 forceCrit 를 풀 수 있어 치명타 차이는 둘 다 맞는 것으로
            h.assertTrue(Math.abs(x - main[0]) < 1e-3 || Math.abs(x - main[0] * cm) < 1e-3 || Math.abs(x * cm - main[0]) < 1e-3,
                    "slash area 100% " + x + " vs " + main[0]);
            h.assertTrue(TestDummies.dealt(c) == 0, "behind not hit " + TestDummies.dealt(c));
            h.assertTrue(Math.abs(TestDummies.dealt(a) - main[0]) < 1e-3, "aimed hit once");
            done(h, sp);
        });
    }

    @GameTest(environment = ENV, maxTicks = 30)
    public void hammerQuakeHitsAroundTheTarget(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = weapon("hammer", 20, "noble");
        hold(sp, w);
        Husk a = dummy(h, 1.5, 2, 3.5);
        Husk b = dummy(h, 3.0, 2, 4.0); // 맞힌 적 둘레 1.6칸
        Husk c = dummy(h, 1.5, 2, 6.8); // 3.3칸 — 안 맞음
        RpgCombat.forceCrit = false;
        h.runAfterDelay(2, () -> {
            sp.attack(a);
            Obj quake = w.typeDef().obj("quake");
            double main = TestDummies.dealt(a);
            h.assertTrue(main > 0, "main hit " + main);
            h.assertTrue(Math.abs(TestDummies.dealt(b) - main * quake.num("ratio")) < 1e-3, "quake " + TestDummies.dealt(b) + " vs " + main * quake.num("ratio"));
            h.assertTrue(TestDummies.dealt(c) == 0, "far not hit " + TestDummies.dealt(c));
            done(h, sp);
        });
    }

    @GameTest(environment = ENV, maxTicks = 30)
    public void scepterHealsOnHit(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h);
        Weapon w = weapon("scepter", 20, "noble");
        hold(sp, w);
        Husk a = dummy(h, 1.5, 2, 3.5);
        sp.getFoodData().setFoodLevel(17);
        sp.getFoodData().setSaturation(0F);
        sp.setHealth(sp.getMaxHealth() * 0.5F);
        RpgCombat.forceCrit = false;
        h.runAfterDelay(2, () -> {
            float hp0 = sp.getHealth();
            sp.attack(a);
            Obj steal = w.typeDef().obj("lifesteal");
            double want = Math.min(TestDummies.dealt(a) * steal.num("rate"), RpgHealth.maxOf(sp) * steal.num("cap"));
            double got = sp.getHealth() - hp0;
            h.assertTrue(want > 0 && Math.abs(got - want) < 0.05, "healed " + got + " vs " + want);
            done(h, sp);
        });
    }

    @GameTest(environment = ENV, maxTicks = 20)
    public void nobleCraftNeedsOnlyDrops(GameTestHelper h) {
        ServerPlayer sp = player(h);
        List<Smith.Recipe> noble = Smith.craftPage(Smith.craftRecipes(), 2);
        h.assertTrue(noble.size() == 8, "noble page: 8 weapons, got " + noble.size());
        h.assertTrue(Smith.craftPage(Smith.craftRecipes(), 0).size() == 8 && Smith.craftPage(Smith.craftRecipes(), 1).size() == 9, "hero 8 · transcend 9");
        // 0.36.0: 넷째 쪽 = 공허의 갑옷 · 0.37.0 다섯째 쪽 = 아티팩트 (무기 목록 없이도 건너뛰지 않음)
        h.assertTrue(Smith.nextCraftPage(Smith.craftRecipes(), 0) == 1 && Smith.nextCraftPage(Smith.craftRecipes(), 1) == 2
                && Smith.nextCraftPage(Smith.craftRecipes(), 2) == 3 && Smith.voidPage(3) && Smith.nextCraftPage(Smith.craftRecipes(), 3) == 4
                && Smith.artifactPage(4) && Smith.nextCraftPage(Smith.craftRecipes(), 4) == 0,
                "page cycle hero → transcend → noble → void armor → artifacts → hero");
        Smith.Recipe r = noble.get(0);
        h.assertTrue(r.ore() == 0 && r.mats().stream().allMatch(m -> m.kind().equals("drop")), "drops only");
        for (Smith.Mat m : r.mats()) sp.getInventory().add(new ItemStack(m.item(), m.count()));
        sp.getInventory().setItem(30, new ItemStack(mmorpg.fabric.economy.EconomyItems.SILVER_COIN, r.emerald()));
        h.assertTrue(Smith.firstCraftPage(sp, Smith.craftRecipes()) == 2, "opens on the noble page with those drops");
        String msg = Smith.craft(sp, r);
        h.assertTrue(msg.contains("완료"), "crafted: " + msg);
        h.assertTrue(RpgGearTests.countIn(sp, RpgItems.byId(r.w().id())) == 1, "got " + r.w().id());
        for (Smith.Mat m : r.mats()) h.assertTrue(RpgGearTests.countIn(sp, m.item()) == 0, "used " + m.name());
        done(h, sp);
    }

    /**
     * 무기 상점 둘째 쪽 (12단계 보강): 쪽 넘김 칸 → 새 무기 4종 일반 · 희귀 (10렙 — 값 · 칸) → 창 사기 (실버 · 창) → 같은 쪽으로 돌아옴 → 다시 넘기면
     * 기본 무기 쪽
     */
    @GameTest(environment = ENV, maxTicks = 20)
    public void shopSellsNewWeapons(GameTestHelper h) {
        ServerPlayer sp = player(h);
        mmorpg.fabric.ui.Forms.HEADLESS.add(sp.getUUID());
        try {
            mmorpg.fabric.economy.Wallet.set(sp, mmorpg.core.Economy.Kind.SILVER, 64);
            mmorpg.fabric.shop.Store.openStore(sp, 10, "weapons");
            mmorpg.fabric.ui.FormNet.ChestPayload c = shopChest(sp);
            h.assertTrue(c != null && c.title().contains("무기 상점") && !c.title().contains("새 무기"), "shop opens on the basic page");
            h.assertTrue(c.slots().get(mmorpg.fabric.shop.Shop.PAGE_TAB).name().contains("새 무기"), "page tab " + c.slots().get(mmorpg.fabric.shop.Shop.PAGE_TAB).name());
            h.assertTrue(mmorpg.fabric.ui.Forms.answer(sp, mmorpg.fabric.shop.Shop.PAGE_TAB, null), "turn the page");
            c = shopChest(sp);
            h.assertTrue(c != null && c.title().contains("새 무기"), "new weapons page " + (c == null ? null : c.title()));
            List<String> types = mmorpg.fabric.shop.Shop.pageTypes(1);
            h.assertTrue(types.equals(Weapons.moreTypes()), "page 2 types " + types);
            for (int r = 0; r < 2; r++) {
                for (int i = 0; i < types.size(); i++) {
                    Weapon w = Weapons.weaponFrom(types.get(i), 10, r == 0 ? "common" : "rare");
                    int slot = mmorpg.fabric.shop.Shop.firstSlot(r, 1) + i;
                    h.assertTrue(w != null && c.slots().get(slot).num() == (int) Weapons.shopPrice(w) && c.slots().get(slot).kind() == mmorpg.fabric.ui.Slot.PRICE,
                            types.get(i) + " row " + r + " slot " + slot + " price " + c.slots().get(slot).num());
                }
            }
            Weapon lance = Weapons.weaponFrom("lance", 10, "common");
            int price = (int) Weapons.shopPrice(lance);
            mmorpg.fabric.ui.Forms.answer(sp, mmorpg.fabric.shop.Shop.firstSlot(0, 1), null);
            c = shopChest(sp);
            h.assertTrue(c != null && c.title().contains("구매 확인"), "confirm window");
            mmorpg.fabric.ui.Forms.answer(sp, 11, null);
            h.assertTrue(RpgGearTests.countIn(sp, RpgItems.byId(lance.id())) == 1, "got the lance");
            h.assertTrue(mmorpg.fabric.ui.Inv.money(sp) == 64 - price, "paid " + mmorpg.fabric.ui.Inv.money(sp));
            c = shopChest(sp);
            h.assertTrue(c != null && c.title().contains("새 무기") && c.body().contains("구매 완료"), "back to the new weapons page");
            mmorpg.fabric.ui.Forms.answer(sp, mmorpg.fabric.shop.Shop.PAGE_TAB, null);
            c = shopChest(sp);
            h.assertTrue(c != null && !c.title().contains("새 무기") && c.slots().get(11).num() == (int) Weapons.shopPrice(Weapons.weaponFrom("sword", 10, "common")),
                    "back to the basic page");
            mmorpg.fabric.ui.Forms.answer(sp, -1, null);
            h.assertFalse(mmorpg.fabric.ui.Forms.pending(sp), "closed");
        } finally {
            mmorpg.fabric.ui.Forms.HEADLESS.remove(sp.getUUID());
        }
        done(h, sp);
    }

    private static mmorpg.fabric.ui.FormNet.ChestPayload shopChest(ServerPlayer sp) {
        return mmorpg.fabric.ui.Forms.last(sp) instanceof mmorpg.fabric.ui.FormNet.ChestPayload c ? c : null;
    }

    // ------------------------------------------------------------ 정령왕 강림

    private static void kingCase(GameTestHelper h, int breakLevel, double total) {
        floor(h);
        ServerPlayer sp = player(h);
        sp.setExperienceLevels(40);
        RpgPlayers.checkLevelUp(sp);
        for (String s : List.of("str", "dex", "int", "vit")) RpgPlayers.setStat(sp, s, 150);
        Husk z = dummy(h, 1.5, 2, 6.0);
        Runes.Rune r = Runes.parseRune(Runes.runeId("spirit", "transcend"));
        h.assertTrue(r != null && r.def().str("fn").equals("spiritKing"), "spirit king rune");
        h.runAfterDelay(2, () -> {
            sp.lookAt(EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
            double P = RuneCast.power(sp, "spirit") * mmorpg.core.RuneBreak.coef(breakLevel) * Runes.damageScale(r); // 0.49.0 피해 배율 (유틸 값 · 시간 보정)
            h.assertTrue(RuneCast.castRune(sp, r, false, breakLevel), "cast");
            h.runAfterDelay(130, () -> {
                double dealt = TestDummies.dealt(z);
                h.assertTrue(Math.abs(dealt - total * P) < 1e-2 * P, "spirit king dealt " + dealt + " vs " + total + " × " + P);
                done(h, sp);
            });
        });
    }

    @GameTest(environment = "mmorpg:w_king", maxTicks = 160)
    public void spiritKingDescends(GameTestHelper h) {
        kingCase(h, 0, 32);
    }

    @GameTest(environment = "mmorpg:w_king6", maxTicks = 160)
    public void spiritKingSixStars(GameTestHelper h) {
        kingCase(h, 6, 50);
    }
}
