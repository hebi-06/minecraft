package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Armor;
import mmorpg.core.Economy;
import mmorpg.core.Rules;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.smith.Smith;
import mmorpg.fabric.smith.VoidArmorCraft;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.Icon;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.36.0 사진첩 (화면 시험, only=voidarmor): 공허의 갑옷 (초월 50렙 — 견본 ② 공허 갑각) 을 입은 모습 — 3인칭 앞 · 뒤, 마네킹 둘 (옆 · 앞), 밤 (빛나는 핏줄 ·
 * 수정 끝), 걷는 마네킹 (등 촉수가 걸음에 따라 흔들림), 투구 가까이, 가방의 아이콘 40 (종류 10 × 부위 4) · 설명, 대장장이 제작 창 넷째 쪽 (공허의 갑옷) →
 * 종류 창 → 확인 창 → 만듦, 무기 레벨 올리기 창 (영웅 · 초월 — 초월은 골드 + 공허 재료) · 확인 창, 60렙 초월 무기 설명.
 * 사진 이름 va_* → ci-logs 의 shots/.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgVoidArmorGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-voidarmor-gallery");
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] SLOT_ARGS = {"armor.head", "armor.chest", "armor.legs", "armor.feet"};
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("voidarmor")) return; // 배포 환경 시험 (prodClient) 은 짧은 시험만 · 고른 시험만 (MMORPG_ONLY)
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("tp @a 0.5 -60 0.5 0 0");
            server.runCommand("xp set @a 50 levels");
            for (String st : List.of("str", "dex", "int", "vit")) server.runCommand("execute as @a run rpg set " + st + " 200");
            ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
            ctx.waitTicks(20);
            worn(ctx, server, "");
            server.runCommand("time set 18000");
            ctx.waitTicks(5);
            worn(ctx, server, "_night");
            server.runCommand("time set 6000");
            walk(ctx, server);
            closeUp(ctx, server);
            inventory(ctx, server);
            craft(ctx, server);
            raise(ctx, server);
            weapon60(ctx, server);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        }
        LOG.info("[voidarmor-gallery] done, {} failed", failures.size());
        if (!failures.isEmpty()) throw new AssertionError("voidarmor gallery: " + String.join(" / ", failures));
    }

    private static String id(String type, int piece) {
        return Armor.armorId(type, 50, "transcend", Armor.PIECE_ORDER.get(piece));
    }

    private static ItemStack stack(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static void wear(TestServerContext server, String type) {
        for (int i = 0; i < 4; i++) server.runCommand("item replace entity @a " + SLOT_ARGS[i] + " with " + id(type, i));
    }

    private static List<Mannequin> mannequins(TestServerContext server, float[][] at) {
        return server.computeOnServer(s -> {
            ServerLevel level = player(s).level();
            List<Mannequin> list = new ArrayList<>();
            for (float[] a : at) {
                Mannequin m = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.COMMAND);
                m.snapTo(a[0], -60 + (a.length > 3 ? a[3] : 0), a[1], a[2], 0F);
                m.setYHeadRot(a[2]);
                m.setYBodyRot(a[2]);
                level.addFreshEntity(m);
                list.add(m);
            }
            return list;
        });
    }

    private static void dress(TestServerContext server, List<Mannequin> ms, String type) {
        server.runOnServer(s -> {
            for (Mannequin m : ms) for (int i = 0; i < 4; i++) m.setItemSlot(SLOTS[i], stack(id(type, i)));
        });
    }

    /** 입은 모습: 마네킹 둘 (옆 · 앞 — 1인칭으로 봄), 3인칭 앞 · 뒤 (suffix "" = 낮 · "_night" = 밤) */
    private void worn(ClientGameTestContext ctx, TestServerContext server, String suffix) {
        wear(server, "str");
        List<Mannequin> ms = mannequins(server, new float[][] {{-1.0F, -3.0F, -90F}, {1.6F, -3.0F, 0F}});
        dress(server, ms, "strvit");
        ctx.getInput().lookAt(180F, 12F);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.waitTicks(8);
        ctx.takeScreenshot(TestScreenshotOptions.of("va_mannequins" + suffix).withSize(960, 540));
        ctx.getInput().lookAt(0F, 0F);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        ctx.waitTicks(5);
        ctx.takeScreenshot(TestScreenshotOptions.of("va_front" + suffix).withSize(960, 540));
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        ctx.waitTicks(5);
        ctx.takeScreenshot(TestScreenshotOptions.of("va_back" + suffix).withSize(960, 540));
        boolean worn = server.computeOnServer(s -> {
            ServerPlayer sp = player(s);
            for (EquipmentSlot slot : SLOTS) if (sp.getItemBySlot(slot).isEmpty()) return false;
            return true;
        });
        if (!worn) failures.add("void set taken off" + suffix);
        LOG.info("[voidarmor-gallery] worn{} {}", suffix, worn);
        server.runOnServer(s -> ms.forEach(Mannequin::discard));
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.getInput().lookAt(0F, 0F);
    }

    /** 걷는 마네킹 (동쪽을 보며 틱마다 0.25칸 — 걷는 빠르기 → 등 촉수가 흔들림), 옆에서 봄 */
    private void walk(ClientGameTestContext ctx, TestServerContext server) {
        List<Mannequin> ms = mannequins(server, new float[][] {{-3.5F, -4.0F, -90F}});
        Mannequin m = ms.get(0);
        dress(server, ms, "dex");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.getInput().lookAt(180F, 10F);
        ctx.waitTicks(5);
        ctx.takeScreenshot(TestScreenshotOptions.of("va_walk_t0").withSize(960, 540));
        for (int t = 1; t <= 16; t++) {
            server.runOnServer(s -> m.snapTo(m.getX() + 0.25, m.getY(), m.getZ(), -90F, 0F));
            ctx.waitTicks(1);
            if (t == 6 || t == 10 || t == 14) ctx.takeScreenshot(TestScreenshotOptions.of("va_walk_t" + t).withSize(960, 540));
        }
        ctx.waitTicks(20);
        ctx.takeScreenshot(TestScreenshotOptions.of("va_walk_stop").withSize(960, 540));
        server.runOnServer(s -> m.discard());
        ctx.getInput().lookAt(0F, 0F);
        LOG.info("[voidarmor-gallery] walk");
    }

    /** 투구 가까이: 1.8칸 앞에서 나를 보는 마네킹 (머리 높이) · 비스듬히 선 마네킹 */
    private void closeUp(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 0.5 0 0");
        List<Mannequin> ms = mannequins(server, new float[][] {{0.5F, 2.3F, 180F}});
        dress(server, ms, "int");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.getInput().lookAt(0F, 0F);
        ctx.waitTicks(6);
        ctx.takeScreenshot(TestScreenshotOptions.of("va_close_front").withSize(960, 540));
        server.runOnServer(s -> {
            Mannequin m = ms.get(0);
            m.snapTo(m.getX(), m.getY(), m.getZ(), 140F, 0F);
            m.setYHeadRot(140F);
            m.setYBodyRot(140F);
        });
        ctx.waitTicks(6);
        ctx.takeScreenshot(TestScreenshotOptions.of("va_close_turn").withSize(960, 540));
        server.runOnServer(s -> {
            Mannequin m = ms.get(0);
            m.snapTo(m.getX(), m.getY(), m.getZ(), 0F, 0F);
            m.setYHeadRot(0F);
            m.setYBodyRot(0F);
        });
        ctx.waitTicks(6);
        ctx.takeScreenshot(TestScreenshotOptions.of("va_close_back").withSize(960, 540));
        server.runOnServer(s -> ms.forEach(Mannequin::discard));
        LOG.info("[voidarmor-gallery] close-up");
    }

    /** 가방: 종류 9 × 부위 4 (가방 세 줄 + 핫바 — 줄마다 한 부위) + 열째 종류를 입음, 가방 둘째 줄 첫 칸 (힘 갑옷) 에 마우스 */
    private void inventory(ClientGameTestContext ctx, TestServerContext server) {
        List<String> order = Rules.get().strings("ARMOR_TYPE_ORDER");
        wear(server, order.get(order.size() - 1));
        for (int p = 0; p < 4; p++) {
            for (int t = 0; t < 9 && t < order.size(); t++) {
                String slot = p < 3 ? "inventory." + (p * 9 + t) : "hotbar." + t;
                server.runCommand("item replace entity @a " + slot + " with " + id(order.get(t), p));
            }
        }
        ctx.setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
        ctx.waitTicks(5);
        ctx.takeScreenshot(TestScreenshotOptions.of("va_inventory").withSize(1280, 720));
        double[] at = ctx.computeOnClient(mc -> {
            var sc = mc.gui.screen();
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            double left = (sc.width - 176) / 2.0;
            double top = (sc.height - 166) / 2.0;
            return new double[] {(left + 8 + 8) * k, (top + 84 + 18 + 8) * k};
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTicks(5);
        ctx.takeScreenshot(TestScreenshotOptions.of("va_inventory_tooltip").withSize(1280, 720));
        ctx.setScreen(() -> null);
        server.runCommand("clear @a");
        wear(server, "str");
        LOG.info("[voidarmor-gallery] inventory");
    }

    // ------------------------------------------------------------ 대장장이

    /** 공허의 갑옷 쪽 → (힘) 종류 창 → 투구 확인 창 → 만듦 (골드 · 재료를 채워 둠 — 한 벌의 일부만 있게) */
    private void craft(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            Wallet.set(sp, Economy.Kind.GOLD, 30);
            sp.getInventory().setItem(9, new ItemStack(Icon.itemById("rpg:loot_void_leaper"), 64));
            sp.getInventory().setItem(10, new ItemStack(Icon.itemById("rpg:loot_void_leaper"), 6));
            sp.getInventory().setItem(11, new ItemStack(Icon.itemById("rpg:loot_void_stalker"), 33));
            sp.getInventory().setItem(12, new ItemStack(Icon.itemById("rpg:loot_void_jelly"), 18));
            Forms.close(sp);
            Smith.openCraftPage(sp, Smith.CRAFT_PAGES.size() - 1);
        });
        if (!waitChest(ctx, "공허")) failures.add("void craft page did not open");
        ctx.takeScreenshot("va_craft_page");
        hoverSlot(ctx, VoidArmorCraft.typeSlot("str"));
        ctx.takeScreenshot("va_craft_page_tooltip");
        clickSlot(ctx, VoidArmorCraft.typeSlot("str"));
        if (!waitChest(ctx, "힘 갑옷")) failures.add("type window did not open");
        ctx.takeScreenshot("va_type");
        hoverSlot(ctx, 10);
        ctx.takeScreenshot("va_type_tooltip");
        net.minecraft.world.item.Item helmet = BuiltInRegistries.ITEM.getValue(Identifier.parse(id("str", 0)));
        int before = server.computeOnServer(s -> RpgGearTests.countIn(player(s), helmet));
        clickSlot(ctx, 10);
        if (!waitChest(ctx, "공허의 갑옷 제작")) failures.add("confirm window did not open");
        ctx.takeScreenshot("va_confirm");
        clickSlot(ctx, 11);
        if (!waitChest(ctx, "힘 갑옷")) failures.add("back to the type window");
        ctx.waitTicks(10);
        ctx.takeScreenshot("va_crafted");
        boolean got = server.computeOnServer(s -> RpgGearTests.countIn(player(s), helmet) == before + 1); // 입은 투구도 셈 (가방 + 갑옷 칸)
        if (!got) failures.add("helmet not crafted");
        // 네 부위 한 번에 (모자람 — 재료 부족 문구)
        clickSlot(ctx, 15);
        if (!waitChest(ctx, "공허의 갑옷 제작")) failures.add("set confirm window");
        ctx.takeScreenshot("va_confirm_set");
        clickSlot(ctx, 11);
        if (!waitChest(ctx, "힘 갑옷")) failures.add("back to the type window (set)");
        ctx.takeScreenshot("va_lack_set");
        server.runOnServer(s -> Forms.close(player(s)));
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
        LOG.info("[voidarmor-gallery] craft got={}", got);
    }

    /** 무기 레벨 올리기 창: 영웅 30렙 칼 · 초월 40렙 칼 · 50렙 창 · 55렙 활 (60렙 낫은 목록에 없음) — 골드 · 재료는 40렙 칼 몫만 → 칼을 45렙으로 */
    private void raise(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("clear @a");
        wear(server, "str");
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            sp.getInventory().setItem(0, new ItemStack(RpgItems.byId(Weapons.weaponFrom("sword", 30, "hero").id())));
            sp.getInventory().setItem(1, new ItemStack(RpgItems.byId(Weapons.weaponFrom("sword", 40, "transcend").id())));
            sp.getInventory().setItem(2, new ItemStack(RpgItems.byId(Weapons.weaponFrom("lance", 50, "transcend").id())));
            sp.getInventory().setItem(3, new ItemStack(RpgItems.byId(Weapons.weaponFrom("bow", 55, "transcend").id())));
            sp.getInventory().setItem(4, new ItemStack(RpgItems.byId(Weapons.weaponFrom("scythe", 60, "transcend").id())));
            sp.getInventory().setItem(9, new ItemStack(Icon.itemById("rpg:loot_void_leaper"), 40));
            sp.getInventory().setItem(10, new ItemStack(Icon.itemById("rpg:loot_void_stalker"), 20));
            Wallet.set(sp, Economy.Kind.GOLD, 20);
            Forms.close(sp);
            Smith.smithLoop(sp, "smith", null);
        });
        if (!waitChest(ctx, "레벨 올리기")) failures.add("raise window did not open");
        ctx.takeScreenshot("va_raise");
        hoverSlot(ctx, 10);
        ctx.takeScreenshot("va_raise_tooltip");
        clickSlot(ctx, 10);
        if (!waitChest(ctx, "레벨 제한 올리기")) failures.add("raise confirm window");
        ctx.takeScreenshot("va_raise_confirm");
        clickSlot(ctx, 11);
        if (!waitChest(ctx, "레벨 올리기")) failures.add("back to the raise window");
        ctx.takeScreenshot("va_raise_done");
        boolean raised = server.computeOnServer(s -> player(s).getInventory().getItem(1).is(RpgItems.byId(Weapons.weaponFrom("sword", 45, "transcend").id())));
        if (!raised) failures.add("sword 40 → 45 not raised");
        server.runOnServer(s -> Forms.close(player(s)));
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
        LOG.info("[voidarmor-gallery] raise raised={}", raised);
    }

    /** 60렙 초월 칼 (같은 디자인) 을 들고 1인칭 · 가방 설명 */
    private void weapon60(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("clear @a");
        server.runCommand("item replace entity @a hotbar.0 with " + Weapons.weaponFrom("sword", 60, "transcend").id());
        server.runCommand("item replace entity @a hotbar.1 with " + Weapons.weaponFrom("sword", 40, "transcend").id());
        ctx.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.waitTicks(10);
        ctx.takeScreenshot(TestScreenshotOptions.of("va_hold_sword60").withSize(960, 540));
        ctx.setScreen(() -> new InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
        ctx.waitTicks(5);
        double[] at = ctx.computeOnClient(mc -> {
            var sc = mc.gui.screen();
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            double left = (sc.width - 176) / 2.0;
            double top = (sc.height - 166) / 2.0;
            return new double[] {(left + 8 + 8) * k, (top + 142 + 8) * k};
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTicks(5);
        ctx.takeScreenshot(TestScreenshotOptions.of("va_sword60_tooltip").withSize(1280, 720));
        ctx.setScreen(() -> null);
        LOG.info("[voidarmor-gallery] weapon 60");
    }

    // ------------------------------------------------------------ 도움

    /** 제목에 word 가 든 상자 창이 열릴 때까지 */
    private static boolean waitChest(ClientGameTestContext ctx, String word) {
        try {
            ctx.waitFor(mc -> mc.gui.screen() instanceof mmorpg.fabric.client.form.ChestFormScreen c && c.form().title().contains(word), 100);
            ctx.waitTicks(3);
            return true;
        } catch (AssertionError | RuntimeException e) {
            return false;
        }
    }

    /** 상자 창의 칸을 실제 마우스로 누름 (칸 자리는 ChestFormScreen.slotCenter — 창 픽셀로 바꿈) */
    private static void clickSlot(ClientGameTestContext ctx, int slot) {
        double[] p = slotPixel(ctx, slot);
        if (p == null) return;
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(2);
        ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(4);
    }

    private static void hoverSlot(ClientGameTestContext ctx, int slot) {
        double[] p = slotPixel(ctx, slot);
        if (p == null) return;
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(4);
    }

    private static double[] slotPixel(ClientGameTestContext ctx, int slot) {
        return ctx.computeOnClient(mc -> {
            if (!(mc.gui.screen() instanceof mmorpg.fabric.client.form.ChestFormScreen c)) return null;
            int[] q = c.slotCenter(slot);
            if (q == null) return null;
            double k = (double) mc.getWindow().getScreenWidth() / c.width;
            return new double[] {(q[0] + 0.5) * k, (q[1] + 0.5) * k};
        });
    }
}
