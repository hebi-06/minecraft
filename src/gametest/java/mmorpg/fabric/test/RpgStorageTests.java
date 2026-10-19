package mmorpg.fabric.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import mmorpg.core.Economy;
import mmorpg.core.Storage;
import mmorpg.fabric.RpgNet;
import mmorpg.fabric.economy.EconomyItems;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.storage.StorageData;
import mmorpg.fabric.storage.StorageWindow;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 0.22.0 엔더 창고 게임 시험 (서버): 스탯 창 단추 → 큰 상자 (54칸) · 바닐라 엔더 상자 → 1번 상자 같은 칸 (한 번만), 사기 (차례로 — 2번 300 실버
 * (지갑 + 가방 은화) · 3 · 4 · 5번 30 · 60 · 100 골드 · 모자라면 그대로), 상자 바꾸기 (같은 창 · 잠긴 상자 거절 · 든 아이템을 다른 상자로 ·
 * Shift + 누르기 · 마지막 상자 기억), 저장 (코덱 — 읽지 못한 아이템은 따로 두었다 다시 저장), 놓인 엔더 상자 (창고 · 뚜껑 · 웅크리고 블록 = 바닐라 ·
 * 위가 막히면 안 열림), 명령어 (/창고 사기 · 번호 · 정보 · 운영자 열기).
 */
public final class RpgStorageTests {
    private static ServerPlayer player(GameTestHelper h) {
        return RpgEconomyTests.player(h);
    }

    @GameTest(maxTicks = 20)
    public void openAndMigrate(GameTestHelper h) {
        ServerPlayer sp = player(h);
        sp.getEnderChestInventory().setItem(0, new ItemStack(Items.DIAMOND, 12));
        sp.getEnderChestInventory().setItem(13, new ItemStack(Items.IRON_INGOT, 5));
        h.assertTrue(RpgNet.statAction(sp, "ender"), "stat window button");
        StorageWindow.StorageMenu m = StorageWindow.menu(sp);
        h.assertTrue(m != null && m.getRowCount() == 6 && m.getContainer().getContainerSize() == Storage.SIZE && m.page() == 0 && !m.fromBlock(),
                "large chest storage (54 slots)");
        h.assertTrue(m.getSlot(0).getItem().is(Items.DIAMOND) && m.getSlot(0).getItem().getCount() == 12 && m.getSlot(13).getItem().is(Items.IRON_INGOT),
                "vanilla ender chest items moved to the same slots of chest 1");
        h.assertTrue(sp.getEnderChestInventory().isEmpty(), "vanilla ender chest emptied");
        StorageData d = StorageData.of(sp);
        h.assertTrue(d.unlocked() == 1 && d.stacks(0) == 2, "1 chest open, 2 stacks");
        sp.closeContainer();
        h.assertTrue(StorageWindow.menu(sp) == null, "closed");
        StorageWindow.open(sp, -1, null, null);
        h.assertTrue(StorageData.of(sp).stacks(0) == 2, "reopen: nothing moved twice");
        sp.closeContainer();
        RpgGearTests.done(h, sp);
    }

    @GameTest(maxTicks = 20)
    public void buyInOrder(GameTestHelper h) {
        ServerPlayer sp = player(h);
        StorageWindow.open(sp, -1, null, null);
        Wallet.set(sp, Economy.Kind.SILVER, 299);
        h.assertTrue(!StorageWindow.buy(sp).startsWith("§a") && StorageData.of(sp).unlocked() == 1 && Wallet.get(sp, Economy.Kind.SILVER) == 299, "299 silver: not enough");
        // 지갑 200 + 가방 은화 100 = 300 (지갑 먼저, 모자라면 동전)
        Wallet.set(sp, Economy.Kind.SILVER, 200);
        sp.getInventory().setItem(5, new ItemStack(EconomyItems.SILVER_COIN, 100));
        String r = StorageWindow.buy(sp);
        h.assertTrue(r.startsWith("§a") && StorageData.of(sp).unlocked() == 2 && Wallet.get(sp, Economy.Kind.SILVER) == 0
                && RpgEconomyTests.count(sp, EconomyItems.SILVER_COIN) == 0, "chest 2 = 300 silver: " + r);
        h.assertTrue(StorageWindow.menu(sp) != null && StorageWindow.menu(sp).page() == 1, "open window switched to chest 2");
        // 3번부터 골드
        Wallet.set(sp, Economy.Kind.SILVER, 100_000);
        h.assertTrue(!StorageWindow.buy(sp).startsWith("§a") && StorageData.of(sp).unlocked() == 2 && Wallet.get(sp, Economy.Kind.SILVER) == 100_000, "chest 3 needs gold");
        Wallet.set(sp, Economy.Kind.GOLD, 30 + 60 + 100);
        h.assertTrue(StorageWindow.buy(sp).startsWith("§a") && Wallet.get(sp, Economy.Kind.GOLD) == 160 && StorageData.of(sp).unlocked() == 3, "chest 3 = 30 gold");
        h.assertTrue(StorageWindow.buy(sp).startsWith("§a") && Wallet.get(sp, Economy.Kind.GOLD) == 100, "chest 4 = 60 gold");
        h.assertTrue(StorageWindow.buy(sp).startsWith("§a") && Wallet.get(sp, Economy.Kind.GOLD) == 0 && StorageData.of(sp).unlocked() == 5, "chest 5 = 100 gold");
        h.assertTrue(!StorageWindow.buy(sp).startsWith("§a") && StorageData.of(sp).unlocked() == 5, "all 5 open");
        h.assertTrue(StorageWindow.menu(sp).page() == 4, "on chest 5");
        sp.closeContainer();
        RpgGearTests.done(h, sp);
    }

    @GameTest(maxTicks = 20)
    public void switchPages(GameTestHelper h) {
        ServerPlayer sp = player(h);
        StorageWindow.open(sp, -1, null, null);
        StorageWindow.StorageMenu m = StorageWindow.menu(sp);
        m.getSlot(3).set(new ItemStack(Items.GOLDEN_APPLE, 4));
        h.assertTrue(StorageData.of(sp).page(0).get(3).is(Items.GOLDEN_APPLE), "the window writes straight into the storage");
        h.assertTrue(!StorageWindow.switchPage(sp, 1) && m.page() == 0, "chest 2 locked");
        StorageData.of(sp).setUnlocked(3);
        // 든 아이템을 다른 상자로
        m.clicked(3, 0, ContainerInput.PICKUP, sp);
        h.assertTrue(m.getCarried().is(Items.GOLDEN_APPLE) && StorageData.of(sp).page(0).get(3).isEmpty(), "carrying the apples");
        h.assertTrue(StorageWindow.switchPage(sp, 2) && m.page() == 2 && StorageWindow.menu(sp) == m, "same window, chest 3");
        h.assertTrue(m.getCarried().is(Items.GOLDEN_APPLE) && m.getCarried().getCount() == 4 && m.getSlot(3).getItem().isEmpty(), "still carrying · chest 3 slots shown");
        m.clicked(10, 0, ContainerInput.PICKUP, sp);
        h.assertTrue(StorageData.of(sp).page(2).get(10).is(Items.GOLDEN_APPLE) && m.getCarried().isEmpty(), "put down in chest 3 slot 10");
        // Shift + 누르기: 가방 (9번 칸 = 창의 54번) → 지금 상자
        sp.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 32));
        m.quickMoveStack(sp, Storage.SIZE);
        h.assertTrue(StorageData.of(sp).stacks(2) == 2 && sp.getInventory().getItem(9).isEmpty(), "shift-click into chest 3");
        h.assertTrue(StorageData.of(sp).stacks(0) == 0 && StorageData.of(sp).last() == 2, "chest 1 empty · last chest 3");
        sp.closeContainer();
        StorageWindow.open(sp, -1, null, null);
        h.assertTrue(StorageWindow.menu(sp).page() == 2 && StorageWindow.menu(sp).getSlot(10).getItem().is(Items.GOLDEN_APPLE), "reopens at the last chest");
        sp.closeContainer();
        h.assertTrue(StorageWindow.open(sp, 4, null, null) && StorageWindow.menu(sp).page() == 2, "locked chest 5 → stays on an open chest");
        sp.closeContainer();
        RpgGearTests.done(h, sp);
    }

    @GameTest(maxTicks = 20)
    public void storageCodec(GameTestHelper h) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, h.getLevel().registryAccess());
        String json = "{\"unlocked\":2,\"last\":1,\"items\":[{\"page\":0,\"slot\":0,\"item\":{\"id\":\"minecraft:diamond\",\"count\":3}},"
                + "{\"page\":1,\"slot\":53,\"item\":{\"id\":\"minecraft:golden_apple\",\"count\":2}},"
                + "{\"page\":0,\"slot\":5,\"item\":{\"id\":\"rpg:no_such_item\",\"count\":1}}]}";
        StorageData d = StorageData.CODEC.parse(ops, JsonParser.parseString(json)).getOrThrow();
        h.assertTrue(d.unlocked() == 2 && d.last() == 1 && d.page(0).get(0).is(Items.DIAMOND) && d.page(0).get(0).getCount() == 3 && d.page(1).get(53).is(Items.GOLDEN_APPLE),
                "read items");
        h.assertTrue(d.page(0).get(5).isEmpty() && d.brokenCount() == 1, "unknown item kept aside (not lost)");
        JsonElement out = StorageData.CODEC.encodeStart(ops, d).getOrThrow();
        h.assertTrue(out.toString().contains("rpg:no_such_item") && out.toString().contains("broken"), "unknown item saved as it was: " + out);
        StorageData back = StorageData.CODEC.parse(ops, out).getOrThrow();
        h.assertTrue(back.page(0).get(0).getCount() == 3 && back.page(1).get(53).getCount() == 2 && back.brokenCount() == 1 && back.unlocked() == 2 && back.last() == 1,
                "round trip");
        StorageData empty = StorageData.CODEC.parse(ops, JsonParser.parseString("{}")).getOrThrow();
        h.assertTrue(empty.unlocked() == 1 && empty.stacks(0) == 0 && empty.brokenCount() == 0, "empty record → defaults");
        h.assertTrue(StorageData.TYPE != null, "attachment registered");
        h.succeed();
    }

    @GameTest(maxTicks = 40)
    public void enderChestBlock(GameTestHelper h) {
        ServerPlayer sp = player(h);
        BlockPos rel = new BlockPos(2, 1, 2);
        h.setBlock(rel, Blocks.ENDER_CHEST);
        BlockPos pos = h.absolutePos(rel);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        InteractionResult r = UseBlockCallback.EVENT.invoker().interact(sp, h.getLevel(), InteractionHand.MAIN_HAND, hit);
        StorageWindow.StorageMenu m = StorageWindow.menu(sp);
        h.assertTrue(r == InteractionResult.SUCCESS && m != null && m.fromBlock(), "ender chest block opens the storage (" + r + ")");
        h.assertTrue(StorageWindow.lidOpeners(h.getLevel(), pos) == 1, "lid open");
        sp.closeContainer();
        h.assertTrue(StorageWindow.lidOpeners(h.getLevel(), pos) == 0, "lid closed");
        // 웅크리고 블록을 들면 바닐라처럼 (그 위에 놓기 — 열지 않음)
        sp.setShiftKeyDown(true);
        sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
        r = UseBlockCallback.EVENT.invoker().interact(sp, h.getLevel(), InteractionHand.MAIN_HAND, hit);
        h.assertTrue(r == InteractionResult.PASS && StorageWindow.menu(sp) == null, "sneak + item = vanilla (" + r + ")");
        sp.setShiftKeyDown(false);
        sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // 위가 막히면 열리지 않음 (바닐라처럼)
        h.setBlock(rel.above(), Blocks.STONE);
        UseBlockCallback.EVENT.invoker().interact(sp, h.getLevel(), InteractionHand.MAIN_HAND, hit);
        h.assertTrue(StorageWindow.menu(sp) == null && StorageWindow.lidOpeners(h.getLevel(), pos) == 0, "blocked above: not opened");
        RpgGearTests.done(h, sp);
    }

    @GameTest(maxTicks = 20)
    public void storageCommands(GameTestHelper h) {
        ServerPlayer sp = player(h);
        var commands = h.getLevel().getServer().getCommands();
        commands.performPrefixedCommand(sp.createCommandSourceStack(), "창고");
        h.assertTrue(StorageWindow.menu(sp) != null, "/창고 opens");
        sp.closeContainer();
        commands.performPrefixedCommand(sp.createCommandSourceStack(), "창고 2");
        h.assertTrue(StorageWindow.menu(sp) == null, "/창고 2 locked");
        Wallet.set(sp, Economy.Kind.SILVER, 300);
        commands.performPrefixedCommand(sp.createCommandSourceStack(), "창고 사기");
        h.assertTrue(StorageData.of(sp).unlocked() == 2 && Wallet.get(sp, Economy.Kind.SILVER) == 0, "/창고 사기");
        commands.performPrefixedCommand(sp.createCommandSourceStack(), "창고 2");
        h.assertTrue(StorageWindow.menu(sp) != null && StorageWindow.menu(sp).page() == 1, "/창고 2 opens chest 2");
        sp.closeContainer();
        commands.performPrefixedCommand(sp.createCommandSourceStack(), "storage unlock @s 5");
        h.assertTrue(StorageData.of(sp).unlocked() == 2, "unlock needs an operator");
        commands.performPrefixedCommand(sp.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER), "창고 열기 @s 5");
        h.assertTrue(StorageData.of(sp).unlocked() == 5, "operator: 창고 열기 @s 5");
        commands.performPrefixedCommand(sp.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER), "storage unlock @s 9");
        h.assertTrue(StorageData.of(sp).unlocked() == Storage.chests(), "clamped to " + Storage.chests());
        RpgGearTests.done(h, sp);
    }
}
