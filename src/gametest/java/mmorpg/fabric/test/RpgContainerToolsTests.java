package mmorpg.fabric.test;

import mmorpg.core.Armor;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.container.ContainerTools;
import mmorpg.fabric.economy.EconomyItems;
import mmorpg.fabric.economy.TrashWindow;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.storage.StorageWindow;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 0.23.0 상자 정렬 · 모두 넣기 · 같은 종류 넣기 게임 시험 (서버 — ContainerTools): 정렬 (같은 아이템 합침 · 무기 → 갑옷 → 룬 → 모드 아이템 → 바닐라,
 * 같은 종류는 등급 · 레벨 높은 것 먼저 · 이미 정렬이면 그대로), 모두 넣기 (가방 27칸만 — 단축 줄은 그대로 · 상자가 차면 남음), 같은 종류 넣기,
 * 쓰레기통 · 셜커 (셜커 안에 셜커는 안 들어감) · 엔더 창고 (지금 상자 칸).
 */
public final class RpgContainerToolsTests {
    private static ServerPlayer player(GameTestHelper h) {
        return RpgEconomyTests.player(h);
    }

    private static ItemStack weapon(String type, int level, String grade) {
        return new ItemStack(RpgItems.byId(Weapons.weaponFrom(type, level, grade).id()));
    }

    private static ItemStack armor(String type, int level, String grade, String piece) {
        return new ItemStack(GearItems.byId(Armor.armorId(type, level, grade, piece)));
    }

    private static ChestMenu chest(ServerPlayer sp, SimpleContainer box) {
        return new ChestMenu(MenuType.GENERIC_9x3, 1, sp.getInventory(), box, 3);
    }

    @GameTest(maxTicks = 20)
    public void sortMergesAndOrders(GameTestHelper h) {
        ServerPlayer sp = player(h);
        SimpleContainer box = new SimpleContainer(27);
        box.setItem(0, new ItemStack(Items.DIRT, 10));
        box.setItem(1, weapon("sword", 20, "rare"));
        box.setItem(2, new ItemStack(Items.DIRT, 30));
        box.setItem(3, RuneItems.stack("int", "transcend"));
        box.setItem(5, new ItemStack(Items.DIRT, 40));
        box.setItem(7, armor("str", 20, "common", "boots"));
        box.setItem(8, new ItemStack(EconomyItems.GOLD_COIN, 5));
        box.setItem(10, new ItemStack(Items.COBBLESTONE, 64));
        box.setItem(12, weapon("sword", 40, "transcend"));
        box.setItem(20, armor("str", 40, "transcend", "helmet"));
        ChestMenu m = chest(sp, box);
        String r = ContainerTools.sort(m);
        h.assertTrue(r.startsWith("§a"), "sorted: " + r);
        h.assertTrue(ContainerTools.grade(box.getItem(0)) == 4 && ContainerTools.category(box.getItem(0)) == 0, "1st = transcend sword " + box.getItem(0));
        h.assertTrue(ContainerTools.grade(box.getItem(1)) == 1 && ContainerTools.category(box.getItem(1)) == 0, "2nd = rare sword " + box.getItem(1));
        h.assertTrue(ContainerTools.category(box.getItem(2)) == 1 && ContainerTools.grade(box.getItem(2)) == 4, "3rd = transcend helmet " + box.getItem(2));
        h.assertTrue(ContainerTools.category(box.getItem(3)) == 1 && ContainerTools.grade(box.getItem(3)) == 0, "4th = common boots " + box.getItem(3));
        h.assertTrue(ContainerTools.category(box.getItem(4)) == 2, "5th = rune " + box.getItem(4));
        h.assertTrue(box.getItem(5).is(EconomyItems.GOLD_COIN) && box.getItem(5).getCount() == 5, "6th = gold coins (mod item) " + box.getItem(5));
        // 바닐라: 아이템 번호 순, 흙 10 + 30 + 40 = 64 + 16 (많은 묶음 먼저)
        int dirt = BuiltInRegistries.ITEM.getId(Items.DIRT);
        int cobble = BuiltInRegistries.ITEM.getId(Items.COBBLESTONE);
        int d0 = dirt < cobble ? 6 : 7;
        int c0 = dirt < cobble ? 8 : 6;
        h.assertTrue(box.getItem(d0).is(Items.DIRT) && box.getItem(d0).getCount() == 64 && box.getItem(d0 + 1).is(Items.DIRT) && box.getItem(d0 + 1).getCount() == 16,
                "dirt merged 64 + 16 at " + d0 + ": " + box.getItem(d0) + " " + box.getItem(d0 + 1));
        h.assertTrue(box.getItem(c0).is(Items.COBBLESTONE) && box.getItem(c0).getCount() == 64, "cobblestone at " + c0);
        int used = 0;
        for (int i = 0; i < 27; i++) if (!box.getItem(i).isEmpty()) used++;
        h.assertTrue(used == 9 && box.getItem(9).isEmpty(), "12 stacks → 9 (front slots) " + used);
        h.assertTrue(ContainerTools.sort(m).startsWith("§7"), "sorting again changes nothing");
        RpgGearTests.done(h, sp);
    }

    @GameTest(maxTicks = 20)
    public void depositSkipsHotbar(GameTestHelper h) {
        ServerPlayer sp = player(h);
        sp.getInventory().setItem(0, weapon("sword", 10, "rare")); // 단축 줄
        sp.getInventory().setItem(4, new ItemStack(Items.TORCH, 16)); // 단축 줄
        sp.getInventory().setItem(9, new ItemStack(Items.DIRT, 20));
        sp.getInventory().setItem(20, new ItemStack(Items.STONE, 5));
        sp.getInventory().setItem(35, RuneItems.stack("str", "rare"));
        SimpleContainer box = new SimpleContainer(27);
        box.setItem(0, new ItemStack(Items.DIRT, 50));
        ChestMenu m = chest(sp, box);
        String r = ContainerTools.deposit(sp, m, false);
        h.assertTrue(r.startsWith("§a"), "deposit: " + r);
        h.assertTrue(sp.getInventory().getItem(9).isEmpty() && sp.getInventory().getItem(20).isEmpty() && sp.getInventory().getItem(35).isEmpty(), "bag emptied");
        h.assertTrue(!sp.getInventory().getItem(0).isEmpty() && sp.getInventory().getItem(4).is(Items.TORCH), "hotbar kept");
        h.assertTrue(box.getItem(0).getCount() == 64 && box.getItem(1).is(Items.DIRT) && box.getItem(1).getCount() == 6, "dirt merged into the chest stack first: "
                + box.getItem(0) + " " + box.getItem(1));
        h.assertTrue(RpgEconomyTests.count(sp, Items.DIRT) == 0 && countIn(box, Items.STONE) == 5, "all moved");
        h.assertTrue(ContainerTools.deposit(sp, m, false).startsWith("§7"), "nothing left to deposit");
        RpgGearTests.done(h, sp);
    }

    @GameTest(maxTicks = 20)
    public void depositFullChest(GameTestHelper h) {
        ServerPlayer sp = player(h);
        sp.getInventory().setItem(9, new ItemStack(Items.DIRT, 20));
        SimpleContainer box = new SimpleContainer(9);
        for (int i = 0; i < 9; i++) box.setItem(i, new ItemStack(Items.STONE, 64));
        ChestMenu m = new ChestMenu(MenuType.GENERIC_9x1, 1, sp.getInventory(), box, 1);
        String r = ContainerTools.deposit(sp, m, false);
        h.assertTrue(r.startsWith("§c") && sp.getInventory().getItem(9).getCount() == 20, "full chest: nothing moved: " + r);
        RpgGearTests.done(h, sp);
    }

    @GameTest(maxTicks = 20)
    public void stackOnlySame(GameTestHelper h) {
        ServerPlayer sp = player(h);
        sp.getInventory().setItem(10, new ItemStack(Items.DIRT, 30));
        sp.getInventory().setItem(11, new ItemStack(Items.STONE, 5));
        sp.getInventory().setItem(2, new ItemStack(Items.DIRT, 7)); // 단축 줄 — 그대로
        SimpleContainer box = new SimpleContainer(27);
        box.setItem(4, new ItemStack(Items.DIRT, 1));
        ChestMenu m = chest(sp, box);
        String r = ContainerTools.deposit(sp, m, true);
        h.assertTrue(r.startsWith("§a"), "stack: " + r);
        h.assertTrue(box.getItem(4).getCount() == 31 && sp.getInventory().getItem(10).isEmpty(), "dirt joined the chest's dirt");
        h.assertTrue(sp.getInventory().getItem(11).is(Items.STONE) && sp.getInventory().getItem(2).getCount() == 7, "stone (not in chest) and hotbar kept");
        RpgGearTests.done(h, sp);
    }

    @GameTest(maxTicks = 20)
    public void allowedMenus(GameTestHelper h) {
        ServerPlayer sp = player(h);
        h.assertTrue(ContainerTools.allowed(chest(sp, new SimpleContainer(27))), "chest allowed");
        h.assertTrue(ContainerTools.allowed(new ShulkerBoxMenu(1, sp.getInventory(), new SimpleContainer(27))), "shulker allowed");
        h.assertTrue(!ContainerTools.allowed(new TrashWindow.TrashMenu(1, sp.getInventory(), new SimpleContainer(TrashWindow.SIZE))), "trash refused");
        h.assertTrue(!ContainerTools.allowed(sp.inventoryMenu), "player inventory refused");
        // 엔더 창고: 지금 상자의 칸
        StorageWindow.open(sp, -1, null, null);
        StorageWindow.StorageMenu sm = StorageWindow.menu(sp);
        h.assertTrue(sm != null && ContainerTools.allowed(sm), "storage allowed");
        sp.getInventory().setItem(12, new ItemStack(Items.IRON_INGOT, 9));
        String r = ContainerTools.deposit(sp, sm, false);
        h.assertTrue(r.startsWith("§a") && mmorpg.fabric.storage.StorageData.of(sp).page(0).get(0).is(Items.IRON_INGOT), "deposit into storage chest 1: " + r);
        sp.closeContainer();
        RpgGearTests.done(h, sp);
    }

    @GameTest(maxTicks = 20)
    public void shulkerNoNesting(GameTestHelper h) {
        ServerPlayer sp = player(h);
        sp.getInventory().setItem(9, new ItemStack(Items.SHULKER_BOX));
        sp.getInventory().setItem(10, new ItemStack(Items.APPLE, 3));
        SimpleContainer box = new SimpleContainer(27);
        ShulkerBoxMenu m = new ShulkerBoxMenu(1, sp.getInventory(), box);
        String r = ContainerTools.deposit(sp, m, false);
        h.assertTrue(r.startsWith("§a") && sp.getInventory().getItem(9).is(Items.SHULKER_BOX) && countIn(box, Items.APPLE) == 3,
                "apples in, shulker box stays out: " + r);
        RpgGearTests.done(h, sp);
    }

    private static int countIn(SimpleContainer box, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < box.getContainerSize(); i++) if (box.getItem(i).is(item)) n += box.getItem(i).getCount();
        return n;
    }
}
