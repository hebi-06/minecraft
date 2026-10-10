package mmorpg.fabric.test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import mmorpg.core.Armor;
import mmorpg.core.Enhance;
import mmorpg.core.Rules;
import mmorpg.core.Stats;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.gear.WeaponEnhance;
import mmorpg.fabric.shop.Store;
import mmorpg.fabric.ui.FormNet;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.Inv;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.gear.ArmorSystem;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.gear.RpgArmorItem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * 4단계 게임 시험 (서버): 갑옷 120 · 재료 등록, 갑옷 방어 · 스탯 보너스 (입으면 실제 스탯 · 최대 체력), 요구 조건이 모자라면 벗겨 가방으로,
 * 무기 강화 단계 (이름 · 피해 배율), 상점 창 흐름 (클라이언트 없이 — 무기 사기 · 전리품 팔기).
 */
public final class RpgGearTests {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    static ServerPlayer player(GameTestHelper h) {
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

    static void done(GameTestHelper h, ServerPlayer sp) {
        h.getLevel().getServer().getPlayerList().remove(sp);
        h.succeed();
    }

    /** 가방에서 이 아이템 수 */
    static int countIn(ServerPlayer sp, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack s = sp.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private static ItemStack armor(String type, int level, String grade, String piece) {
        return new ItemStack(GearItems.byId(Armor.armorId(type, level, grade, piece)));
    }

    @GameTest(maxTicks = 20)
    public void gearItemsRegistered(GameTestHelper h) {
        h.assertTrue(GearItems.armor().size() == 120, "armor " + GearItems.armor().size());
        for (RpgArmorItem it : GearItems.armor()) {
            Armor.Piece a = it.piece();
            ItemStack s = new ItemStack(it);
            Equippable eq = s.get(DataComponents.EQUIPPABLE);
            int pi = Armor.PIECE_ORDER.indexOf(a.piece());
            h.assertTrue(eq != null && eq.slot() == SLOTS[pi], "equippable slot " + a.id());
            h.assertTrue(s.getMaxStackSize() == 1, "stack 1 " + a.id());
            double[] prot = {0};
            s.forEachModifier(SLOTS[pi], (attr, mod) -> {
                if (attr.value() == Attributes.ARMOR.value()) prot[0] += mod.amount();
            });
            h.assertTrue(prot[0] == a.prot(), "armor points " + a.id() + " " + prot[0] + " vs " + a.prot());
            h.assertTrue(it.getName(s).getString().contains(a.title()), "name " + a.id());
        }
        // 재료: 레이드 4 · 보스 · 전리품 · 룬 조각 · NPC 설정 도구
        for (String id : List.of("rpg:star_ore", "rpg:guard_scroll", "rpg:kraken_ink", "rpg:laevateinn_shard", "rpg:loot_troll", "rpg:rune_shard", "rpg:npc_tool")) {
            h.assertTrue(GearItems.byId(id) != null, "material " + id);
        }
        h.assertTrue(new ItemStack(GearItems.byId("rpg:star_ore")).getMaxStackSize() == 64 && new ItemStack(GearItems.NPC_TOOL).getMaxStackSize() == 1, "material stacks");
        h.succeed();
    }

    /** 적룡 힘 세트 (40렙 일반) 를 다 입으면: 방어 = 세트 방어, 실제 힘 = 찍은 힘 + 세트 보너스 */
    @GameTest(maxTicks = 20)
    public void armorBonusAndProtection(GameTestHelper h) {
        ServerPlayer sp = player(h);
        sp.setExperienceLevels(40);
        Armor.Piece chest = Armor.armorFrom("strvit", 40, "common", "chest");
        for (Weapons.Req q : chest.reqs()) RpgPlayers.setStat(sp, q.stat(), (int) Math.ceil(q.value()));
        double hpBefore = RpgHealth.maxHpOf(sp);
        Map<String, Double> base = Map.copyOf(RpgStats.effective(sp));
        for (int i = 0; i < 4; i++) sp.setItemSlot(SLOTS[i], armor("strvit", 40, "common", Armor.PIECE_ORDER.get(i)));
        ArmorSystem.armorTick(sp);
        for (int i = 0; i < 4; i++) h.assertFalse(sp.getItemBySlot(SLOTS[i]).isEmpty(), "kept " + SLOTS[i]);
        double[] prot = {0};
        for (EquipmentSlot slot : SLOTS) {
            sp.getItemBySlot(slot).forEachModifier(slot, (attr, mod) -> {
                if (attr.value() == Attributes.ARMOR.value()) prot[0] += mod.amount();
            });
        }
        h.assertTrue(prot[0] == chest.setProt(), "set armor " + prot[0] + " vs " + chest.setProt());
        for (Map.Entry<String, Double> e : chest.setBonus().entrySet()) {
            double now = Stats.get(RpgStats.effective(sp), e.getKey());
            h.assertTrue(now == Stats.get(base, e.getKey()) + e.getValue(), e.getKey() + " " + now + " vs " + Stats.get(base, e.getKey()) + " + " + e.getValue());
        }
        h.assertTrue(RpgHealth.maxHpOf(sp) > hpBefore, "vit bonus raises max hp " + hpBefore + " → " + RpgHealth.maxHpOf(sp));
        // 벗으면 보너스도 없어짐
        sp.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        ArmorSystem.armorTick(sp);
        double str = Stats.get(RpgStats.effective(sp), "str");
        h.assertTrue(str == Stats.get(base, "str") + chest.setBonus().get("str") - chest.bonus().getOrDefault("str", 0.0), "chest off str " + str);
        done(h, sp);
    }

    /** 요구 조건이 모자라면 벗겨 가방으로 (보너스 없음) · 크리에이티브는 그대로 */
    @GameTest(maxTicks = 20)
    public void armorLackTakesOff(GameTestHelper h) {
        ServerPlayer sp = player(h);
        ItemStack chest = armor("str", 40, "transcend", "chest");
        sp.setItemSlot(EquipmentSlot.CHEST, chest.copy());
        sp.setGameMode(GameType.CREATIVE);
        ArmorSystem.armorTick(sp);
        h.assertFalse(sp.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "creative keeps it");
        sp.setGameMode(GameType.SURVIVAL);
        ArmorSystem.armorTick(sp);
        h.assertTrue(sp.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "taken off");
        h.assertTrue(countIn(sp, chest.getItem()) == 1, "back in the bag");
        h.assertTrue(RpgStats.bonusOf(sp).isEmpty(), "no bonus");
        done(h, sp);
    }

    /** 강화 단계: 이름 "+N", 피해 배율 × (1 + 강화 보너스) — 손에 든 그 무기만 */
    @GameTest(maxTicks = 20)
    public void enhanceRaisesWeaponPower(GameTestHelper h) {
        ServerPlayer sp = player(h);
        Weapon w = Weapons.weaponFrom("sword", 10, "common");
        for (Weapons.Req q : w.reqs()) RpgPlayers.setStat(sp, q.stat(), (int) Math.ceil(q.value()) + 5);
        ItemStack stack = new ItemStack(RpgItems.byId(w.id()));
        sp.setItemInHand(InteractionHand.MAIN_HAND, stack);
        double p0 = RpgCombat.powerOf(sp, w);
        WeaponEnhance.write(sp.getMainHandItem(), 5);
        double p5 = RpgCombat.powerOf(sp, w);
        h.assertTrue(WeaponEnhance.of(sp.getMainHandItem()) == 5, "stored");
        h.assertTrue(Math.abs(p5 - p0 * (1 + Enhance.enhanceBonus(5))) < 1e-9, "power " + p0 + " -> " + p5);
        h.assertTrue(sp.getMainHandItem().getHoverName().getString().contains("+5"), "name " + sp.getMainHandItem().getHoverName().getString());
        WeaponEnhance.write(sp.getMainHandItem(), 0);
        h.assertTrue(WeaponEnhance.of(sp.getMainHandItem()) == 0 && !sp.getMainHandItem().has(WeaponEnhance.ENHANCE), "cleared");
        done(h, sp);
    }

    private static FormNet.ChestPayload chest(ServerPlayer sp) {
        return Forms.last(sp) instanceof FormNet.ChestPayload c ? c : null;
    }

    /** 상점 (클라이언트 없이 창 흐름): 10렙 일반 칼 칸 → 구매 확인 → 사기 (에메랄드 빠지고 칼), 닫으면 흐름 끝 */
    @GameTest(maxTicks = 20)
    public void shopBuysWeapon(GameTestHelper h) {
        ServerPlayer sp = player(h);
        Forms.HEADLESS.add(sp.getUUID());
        try {
            sp.getInventory().setItem(9, new ItemStack(Items.EMERALD, 64));
            Store.openStore(sp, 10, "weapons");
            FormNet.ChestPayload c = chest(sp);
            h.assertTrue(c != null && c.title().contains("무기 상점") && c.slots().size() == 27, "shop opened");
            Weapon w = Weapons.weaponFrom("sword", 10, "common");
            int price = (int) Weapons.shopPrice(w);
            h.assertTrue(c.slots().get(11).num() == price && c.slots().get(11).kind() == mmorpg.fabric.ui.Slot.PRICE, "price shown " + c.slots().get(11).num());
            h.assertTrue(Forms.answer(sp, 11, null), "pick sword");
            c = chest(sp);
            h.assertTrue(c != null && c.title().contains("구매 확인"), "confirm window");
            Forms.answer(sp, 11, null);
            h.assertTrue(countIn(sp, RpgItems.byId(w.id())) == 1, "got the sword");
            h.assertTrue(Inv.money(sp) == 64 - price, "paid " + Inv.money(sp));
            c = chest(sp);
            h.assertTrue(c != null && c.body().contains("구매 완료"), "back to shop with message");
            Forms.answer(sp, -1, null);
            h.assertFalse(Forms.pending(sp), "closed");
        } finally {
            Forms.HEADLESS.remove(sp.getUUID());
        }
        done(h, sp);
    }

    /** 전리품 팔기: 트롤 엄니 10개 → 모두 팔기 → 1개 값 × 10 에메랄드 */
    @GameTest(maxTicks = 20)
    public void shopSellsLoot(GameTestHelper h) {
        ServerPlayer sp = player(h);
        Forms.HEADLESS.add(sp.getUUID());
        try {
            net.minecraft.world.item.Item troll = GearItems.byId("rpg:loot_troll");
            sp.getInventory().setItem(5, new ItemStack(troll, 10));
            int price = (int) Rules.get().obj("MONSTER_DROPS").obj("troll").num("price");
            int index = Rules.get().strings("MONSTER_ORDER").indexOf("troll");
            Store.openStore(sp, 0, "sell");
            FormNet.ChestPayload c = chest(sp);
            h.assertTrue(c != null && c.title().contains("전리품 팔기"), "sell window");
            h.assertTrue(c.slots().get(10 + index).num() == 10, "count shown");
            Forms.answer(sp, 10 + index, null); // 그 전리품 → 개수 고르기
            h.assertTrue(chest(sp) != null && chest(sp).title().contains("팔기"), "amount window");
            Forms.answer(sp, 13, null); // 모두
            h.assertTrue(countIn(sp, troll) == 0, "loot taken");
            h.assertTrue(Inv.money(sp) == price * 10, "paid " + Inv.money(sp) + " vs " + price * 10);
            Forms.answer(sp, -1, null);
        } finally {
            Forms.HEADLESS.remove(sp.getUUID());
        }
        done(h, sp);
    }
}
