package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import mmorpg.core.Artifacts;
import mmorpg.core.Economy;
import mmorpg.fabric.artifact.ArtifactSystem;
import mmorpg.fabric.client.ArtifactScreen;
import mmorpg.fabric.client.ClientArtifacts;
import mmorpg.fabric.client.ClientRpgState;
import mmorpg.fabric.client.MmorpgClient;
import mmorpg.fabric.client.StatScreen;
import mmorpg.fabric.client.rpgui.CharacterPanel;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.gear.ArtifactItem;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.smith.ArtifactCraft;
import mmorpg.fabric.smith.Smith;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.Icon;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.37.0 사진첩 (화면 시험, only=artifact — 실제 키 · 마우스 입력): 가방의 아티팩트 아이콘 20 · 설명, 인벤토리 패널의 "아티팩트" 단추 (풀이 · 누르면 창),
 * I 키 → 아티팩트 창 (35렙: 영웅 · 초월은 빨갛게 · 누르면 "50렙부터" 알림), 50렙: 누르기 → 반지 1 (낌 연출), 끌어 놓기 → 반지 2, 목걸이 · 귀걸이 · 팔찌,
 * 귀걸이를 목걸이 칸에 → 알림, 칸 · 합계 줄 풀이, 반지 1 ↔ 2, 오른쪽 클릭 빼기 · 무대 밖으로 끌어 빼기, 스탯 창 (아티팩트 공격력이 피해 숫자에),
 * 3인칭 끼기 연출 (초월 — 마법진 · 불꽃), 대장장이 아티팩트 쪽 · 칸 풀이 · 확인 창 · 만듦. 사진 이름 af_* → ci-logs 의 shots/.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgArtifactGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-artifact-gallery");
    /** 가방 9 ~ 에 넣을 아티팩트 {종류, 등급} */
    private static final String[][] BAG = {
        {"ring", "transcend"}, {"ring", "hero"}, {"necklace", "transcend"}, {"earring", "transcend"}, {"bracelet", "transcend"},
        {"ring", "rare"}, {"necklace", "noble"}, {"earring", "common"}, {"bracelet", "hero"}, {"ring", "common"}, {"necklace", "rare"}
    };
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("artifact")) return;
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            TestServerConnection conn = world.getConnection();
            conn.waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("tp @a 0.5 -60 0.5 0 0");
            server.runCommand("xp set @a 35 levels");
            for (String st : List.of("str", "dex", "int", "vit")) server.runCommand("execute as @a run rpg set " + st + " 120");
            ctx.runOnClient(mc -> {
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
            server.runOnServer(s -> {
                ServerPlayer sp = player(s);
                sp.getInventory().clearContent();
                sp.getInventory().setItem(0, new ItemStack(Icon.itemById("rpg:sword_l40_hero")));
                for (int i = 0; i < BAG.length; i++) sp.getInventory().setItem(9 + i, art(BAG[i][0], BAG[i][1]));
                // 아이콘 20 모두 (가방 셋째 줄 + 넷째 줄 — 종류 넷 × 등급 다섯 중 BAG 에 없는 것)
                int slot = 20;
                for (Artifacts.Art a : Artifacts.all()) {
                    if (slot >= 36) break;
                    boolean inBag = false;
                    for (String[] b : BAG) inBag |= a.type().equals(b[0]) && a.grade().equals(b[1]);
                    if (!inBag) sp.getInventory().setItem(slot++, new ItemStack(GearItems.byId(a.id())));
                }
                sp.getInventory().setSelectedSlot(0);
            });
            ctx.waitTicks(20);
            part(ctx, "inventory", () -> inventory(ctx, conn));
            part(ctx, "low", () -> lowLevel(ctx, server, conn));
            server.runCommand("xp set @a 50 levels");
            ctx.waitTicks(10);
            part(ctx, "window", () -> window(ctx, server, conn));
            part(ctx, "stats", () -> stats(ctx));
            part(ctx, "fx", () -> fx(ctx, server));
            part(ctx, "craft", () -> craft(ctx, server, conn));
            ctx.setScreen(() -> null);
        }
        LOG.info("[artifact-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        if (!failures.isEmpty()) throw new AssertionError("artifact gallery: " + String.join(" / ", failures));
    }

    private static ItemStack art(String type, String grade) {
        return new ItemStack(GearItems.byId(Artifacts.id(type, grade)));
    }

    // ------------------------------------------------------------ 1. 인벤토리 (아이콘 · 설명 · 패널 단추)

    private void inventory(ClientGameTestContext ctx, TestServerConnection conn) {
        ctx.getInput().pressKey(o -> o.keyInventory);
        if (!waitScreen(ctx, sc -> sc instanceof InventoryScreen, "inventory (E)")) ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        ctx.waitTicks(5);
        away(ctx);
        shot(ctx, "af_inventory");
        hoverAny(ctx, sc -> new int[] {(sc.width - 176) / 2 + 8 + 8, (sc.height - 166) / 2 + 84 + 8}); // 가방 첫 칸 = 초월 반지
        shot(ctx, "af_item_tip");
        String lore = ctx.computeOnClient(mc -> {
            StringBuilder b = new StringBuilder();
            for (net.minecraft.network.chat.Component c : Screen.getTooltipFromItem(mc, art("ring", "transcend"))) b.append(c.getString()).append(" / ");
            return b.toString();
        });
        check(lore.contains("무스펠의 반지") && lore.contains("착용 50렙") && lore.contains("공격력 +4%") && lore.contains("치명타 확률 +3%p") && lore.contains("우클릭"),
                "transcend ring lore (" + lore + ")");
        hoverAny(ctx, sc -> panel(sc).artifactButtonCenter());
        shot(ctx, "af_panel_tip");
        clickAny(ctx, conn, sc -> panel(sc).artifactButtonCenter(), InputConstants.MOUSE_BUTTON_LEFT);
        check(waitScreen(ctx, sc -> sc instanceof ArtifactScreen, "artifact window from the panel button"), "panel button opens the artifact window");
        ctx.setScreen(() -> null);
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------ 2. 35렙 (영웅 · 초월은 아직)

    private void lowLevel(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        ctx.getInput().pressKey(MmorpgClient.artifactKey);
        if (!waitScreen(ctx, sc -> sc instanceof ArtifactScreen, "artifact window (I)")) ctx.setScreen(ArtifactScreen::new);
        ctx.waitTicks(4);
        away(ctx);
        int n = ctx.computeOnClient(mc -> ((ArtifactScreen) mc.gui.screen()).bagSlots().size());
        check(n == Artifacts.all().size(), "window lists the bag artifacts (" + n + ")");
        shot(ctx, "af_window_lv35");
        // 그리드 첫 칸 (높은 등급 먼저 = 초월) 을 누름 → 50렙부터 알림 · 그대로
        click(ctx, conn, s -> s.gridCenter(0), InputConstants.MOUSE_BUTTON_LEFT);
        shot(ctx, "af_level_note");
        check(slot(server, 0).equals("-") && slot(server, 2).equals("-"), "35렙: nothing equipped (" + slot(server, 0) + ")");
        // 고귀 목걸이 (30렙) 는 됨 → 목걸이 칸
        int noble = indexOf(ctx, "necklace", "noble");
        if (noble >= 0) click(ctx, conn, s -> s.gridCenter(noble), InputConstants.MOUSE_BUTTON_LEFT);
        check(slot(server, 2).equals("necklace_noble"), "noble necklace at 35 (" + slot(server, 2) + ")");
        ctx.getInput().pressKey(MmorpgClient.artifactKey);
        ctx.waitTicks(4);
        check(ctx.computeOnClient(mc -> mc.gui.screen() == null), "I closes the window");
        if (ctx.computeOnClient(mc -> mc.gui.screen() != null)) ctx.setScreen(() -> null);
    }

    // ------------------------------------------------------------ 3. 50렙: 끼기 · 끌기 · 바꾸기 · 빼기

    private void window(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        ctx.getInput().pressKey(MmorpgClient.artifactKey);
        if (!waitScreen(ctx, sc -> sc instanceof ArtifactScreen, "artifact window (I)")) ctx.setScreen(ArtifactScreen::new);
        ctx.waitTicks(4);
        // 초월 반지 누르기 → 반지 1 (낌 연출 중 사진)
        int ringT = indexOf(ctx, "ring", "transcend");
        click(ctx, conn, s -> s.gridCenter(ringT), InputConstants.MOUSE_BUTTON_LEFT);
        shot(ctx, "af_equip_anim");
        check(slot(server, 0).equals("ring_transcend"), "click → ring 1 (" + slot(server, 0) + ")");
        // 영웅 반지 끌어 반지 2 에
        int ringH = indexOf(ctx, "ring", "hero");
        drag(ctx, conn, s -> s.gridCenter(ringH), s -> s.slotCenter(1), "af_drag");
        check(slot(server, 1).equals("ring_hero"), "drag → ring 2 (" + slot(server, 1) + ")");
        // 초월 목걸이 누르기 → 목걸이 칸 (고귀 목걸이와 바꿈 — 고귀는 가방으로)
        int neck = indexOf(ctx, "necklace", "transcend");
        click(ctx, conn, s -> s.gridCenter(neck), InputConstants.MOUSE_BUTTON_LEFT);
        check(slot(server, 2).equals("necklace_transcend"), "necklace swapped (" + slot(server, 2) + ")");
        // 귀걸이를 목걸이 칸에 끌어 놓기 → 알림만
        int ear = indexOf(ctx, "earring", "transcend");
        drag(ctx, conn, s -> s.gridCenter(ear), s -> s.slotCenter(2), null);
        shot(ctx, "af_wrong_slot");
        check(slot(server, 2).equals("necklace_transcend") && slot(server, 3).equals("-"), "earring onto necklace slot: no");
        int ear2 = indexOf(ctx, "earring", "transcend");
        click(ctx, conn, s -> s.gridCenter(ear2), InputConstants.MOUSE_BUTTON_LEFT);
        int brace = indexOf(ctx, "bracelet", "transcend");
        click(ctx, conn, s -> s.gridCenter(brace), InputConstants.MOUSE_BUTTON_LEFT);
        check(slot(server, 3).equals("earring_transcend") && slot(server, 4).equals("bracelet_transcend"), "earring · bracelet");
        ctx.waitTicks(20);
        away(ctx);
        shot(ctx, "af_window_full");
        double atk = ctx.computeOnClient(mc -> ClientArtifacts.totals().getOrDefault("atk", 0.0));
        check(Math.abs(atk - 7) < 1e-9, "client totals atk 4 + 3 (" + atk + ")");
        hover(ctx, s -> s.slotCenter(0));
        shot(ctx, "af_slot_tip");
        hover(ctx, s -> s.sumCenter(0));
        shot(ctx, "af_sum_tip");
        // 반지 1 ↔ 2
        drag(ctx, conn, s -> s.slotCenter(0), s -> s.slotCenter(1), null);
        check(slot(server, 0).equals("ring_hero") && slot(server, 1).equals("ring_transcend"), "swap rings (" + slot(server, 0) + ", " + slot(server, 1) + ")");
        // 오른쪽 클릭으로 목걸이 빼기 · 팔찌를 무대 밖으로 끌어 빼기
        click(ctx, conn, s -> s.slotCenter(2), InputConstants.MOUSE_BUTTON_RIGHT);
        check(slot(server, 2).equals("-"), "right click → necklace off");
        drag(ctx, conn, s -> s.slotCenter(4), s -> s.gridCenter(22), null);
        check(slot(server, 4).equals("-"), "drag off the stage → bracelet off");
        shot(ctx, "af_window_removed");
        // 다시 모두 (뒤의 사진 — 스탯 · 연출)
        int neck2 = indexOf(ctx, "necklace", "transcend");
        click(ctx, conn, s -> s.gridCenter(neck2), InputConstants.MOUSE_BUTTON_LEFT);
        int brace2 = indexOf(ctx, "bracelet", "transcend");
        click(ctx, conn, s -> s.gridCenter(brace2), InputConstants.MOUSE_BUTTON_LEFT);
        check(ArtifactSystem.SIZE == 5 && server.computeOnServer(s -> ArtifactSystem.worn(player(s))) == 5, "all five on");
        ctx.setScreen(() -> null);
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------ 4. 스탯 창 (아티팩트가 피해 · 체력에)

    private void stats(ClientGameTestContext ctx) {
        ctx.getInput().pressKey(MmorpgClient.statsKey);
        if (!waitScreen(ctx, sc -> sc instanceof StatScreen, "stat window (K)")) ctx.setScreen(StatScreen::new);
        ctx.waitTicks(5);
        away(ctx);
        shot(ctx, "af_stats");
        double[] t = ctx.computeOnClient(mc -> new double[] {ClientRpgState.sheet().tree("atk"), ClientRpgState.sheet().tree("hp"), ClientRpgState.sheet().tree("critDmg")});
        check(t[0] >= 7 && t[1] >= 40 && t[2] >= 12, "stat sheet includes artifacts (atk " + t[0] + " · hp " + t[1] + " · critDmg " + t[2] + ")");
        ctx.setScreen(() -> null);
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------ 5. 끼기 연출 (3인칭 — 초월)

    private void fx(ClientGameTestContext ctx, TestServerContext server) {
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        ctx.waitTicks(5);
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            ArtifactSystem.unequip(sp, 3);
            for (int i = 0; i < 36; i++) {
                if (ArtifactItem.of(sp.getInventory().getItem(i)) == Artifacts.art("earring", "transcend")) {
                    ArtifactSystem.equip(sp, i, -1);
                    break;
                }
            }
        });
        ctx.waitTicks(4);
        ctx.takeScreenshot(TestScreenshotOptions.of("af_equip_fx").withSize(960, 540));
        ctx.waitTicks(6);
        ctx.takeScreenshot(TestScreenshotOptions.of("af_equip_fx_late").withSize(960, 540));
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------ 6. 대장장이 아티팩트 쪽

    private void craft(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            Wallet.set(sp, Economy.Kind.SILVER, 120);
            Wallet.set(sp, Economy.Kind.GOLD, 4);
            sp.getInventory().setItem(30, new ItemStack(Icon.itemById("rpg:loot_crab"), 10));
            sp.getInventory().setItem(31, new ItemStack(Icon.itemById("rpg:loot_worm_larva"), 8));
            sp.getInventory().setItem(32, new ItemStack(Icon.itemById("rpg:loot_troll"), 5));
            Forms.close(sp);
            Smith.openCraftPage(sp, Smith.artifactPageIndex());
        });
        if (!waitChest(ctx, "장신구")) failures.add("artifact craft page did not open");
        ctx.takeScreenshot("af_craft_page");
        hoverSlot(ctx, ArtifactCraft.slotOf("ring", "rare"));
        ctx.takeScreenshot("af_craft_tip");
        hoverSlot(ctx, 48); // 아랫줄 희귀 재료 (아랫줄 셋째 칸부터 일반 · 희귀 …)
        ctx.takeScreenshot("af_craft_mats");
        int before = server.computeOnServer(s -> RpgGearTests.countIn(player(s), GearItems.byId(Artifacts.id("ring", "rare"))));
        clickSlot(ctx, ArtifactCraft.slotOf("ring", "rare"));
        if (!waitChest(ctx, "장신구 만들기")) failures.add("confirm window did not open");
        ctx.takeScreenshot("af_craft_confirm");
        clickSlot(ctx, 11);
        if (!waitChest(ctx, "대장장이")) failures.add("back to the artifact page");
        ctx.waitTicks(8);
        ctx.takeScreenshot("af_crafted");
        boolean got = server.computeOnServer(s -> RpgGearTests.countIn(player(s), GearItems.byId(Artifacts.id("ring", "rare"))) == before + 1);
        check(got, "rare ring crafted");
        // 영웅 (골드 · 재료 모자람) → 재료 부족
        clickSlot(ctx, ArtifactCraft.slotOf("bracelet", "hero"));
        if (waitChest(ctx, "장신구 만들기")) {
            clickSlot(ctx, 11);
            waitChest(ctx, "대장장이");
            ctx.takeScreenshot("af_craft_lack");
        }
        server.runOnServer(s -> Forms.close(player(s)));
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
    }

    // ------------------------------------------------------------ 도움

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    /** 칸 i 의 "종류_등급" (빈 칸 "-") */
    private static String slot(TestServerContext server, int i) {
        return server.computeOnServer(s -> {
            Artifacts.Art a = ArtifactSystem.art(player(s), i);
            return a == null ? "-" : a.type() + "_" + a.grade();
        });
    }

    /** 창의 가방 그리드에서 이 아티팩트의 순서 (없으면 -1) */
    private static int indexOf(ClientGameTestContext ctx, String type, String grade) {
        return ctx.computeOnClient(mc -> {
            if (!(mc.gui.screen() instanceof ArtifactScreen s)) return -1;
            List<Integer> bag = s.bagSlots();
            for (int n = 0; n < bag.size(); n++) {
                Artifacts.Art a = ArtifactItem.of(mc.player.getInventory().getItem(bag.get(n)));
                if (a != null && a.type().equals(type) && a.grade().equals(grade)) return n;
            }
            return -1;
        });
    }

    private void part(ClientGameTestContext ctx, String name, Runnable run) {
        try {
            run.run();
            LOG.info("[artifact-gallery] part {} finished", name);
        } catch (AssertionError | RuntimeException e) {
            check(false, "part " + name + " threw " + e);
            LOG.warn("[artifact-gallery] part " + name, e);
        }
        try {
            ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
            ctx.setScreen(() -> null);
            ctx.waitTicks(3);
        } catch (AssertionError | RuntimeException e) {
            LOG.warn("[artifact-gallery] reset after " + name, e);
        }
    }

    private void check(boolean ok, String what) {
        LOG.info("[artifact-gallery] {} {}", ok ? "OK  " : "FAIL", what);
        if (!ok) failures.add(what);
    }

    private static void shot(ClientGameTestContext ctx, String name) {
        ctx.runOnClient(mc -> mc.gui.toastManager().clear());
        ctx.waitTick();
        ctx.takeScreenshot(name);
    }

    private static void away(ClientGameTestContext ctx) {
        ctx.getInput().setCursorPos(6, 6);
        ctx.waitTicks(2);
    }

    private boolean waitScreen(ClientGameTestContext ctx, java.util.function.Predicate<Screen> test, String what) {
        try {
            ctx.waitFor(mc -> mc.gui.screen() != null && test.test(mc.gui.screen()), 60);
            ctx.waitTicks(2);
            return true;
        } catch (AssertionError | RuntimeException e) {
            check(false, "screen " + what + " did not open");
            return false;
        }
    }

    private static CharacterPanel panel(Screen sc) {
        if (sc == null) return null;
        for (AbstractWidget w : Screens.getWidgets(sc)) if (w instanceof CharacterPanel p) return p;
        throw new AssertionError("no character panel");
    }

    /** 아티팩트 창의 GUI 좌표 → 창 픽셀 좌표 */
    private static double[] at(ClientGameTestContext ctx, Function<ArtifactScreen, int[]> where) {
        return ctx.computeOnClient(mc -> {
            Screen sc = mc.gui.screen();
            if (!(sc instanceof ArtifactScreen s)) throw new AssertionError("not the artifact window: " + (sc == null ? "none" : sc.getClass().getSimpleName()));
            int[] p = where.apply(s);
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            return new double[] {(p[0] + 0.5) * k, (p[1] + 0.5) * k};
        });
    }

    private static void hover(ClientGameTestContext ctx, Function<ArtifactScreen, int[]> where) {
        double[] p = at(ctx, where);
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(3);
    }

    private static double[] atAny(ClientGameTestContext ctx, Function<Screen, int[]> where) {
        return ctx.computeOnClient(mc -> {
            Screen sc = mc.gui.screen();
            if (sc == null) throw new AssertionError("no screen");
            int[] q = where.apply(sc);
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            return new double[] {(q[0] + 0.5) * k, (q[1] + 0.5) * k};
        });
    }

    private static void hoverAny(ClientGameTestContext ctx, Function<Screen, int[]> where) {
        double[] p = atAny(ctx, where);
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(3);
    }

    private static void settle(ClientGameTestContext ctx, TestServerConnection conn) {
        ctx.waitTick();
        conn.waitForServerboundPackets();
        ctx.waitTicks(2);
        conn.waitForClientboundPackets();
        ctx.waitTicks(2);
    }

    private static void click(ClientGameTestContext ctx, TestServerConnection conn, Function<ArtifactScreen, int[]> where, int button) {
        hover(ctx, where);
        ctx.getInput().pressMouse(button);
        settle(ctx, conn);
    }

    private static void clickAny(ClientGameTestContext ctx, TestServerConnection conn, Function<Screen, int[]> where, int button) {
        hoverAny(ctx, where);
        ctx.getInput().pressMouse(button);
        settle(ctx, conn);
    }

    /** 끌어 놓기 (누른 채 6걸음으로 옮김 — 중간 사진 이름이 있으면 놓기 전에 사진) */
    private static void drag(ClientGameTestContext ctx, TestServerConnection conn, Function<ArtifactScreen, int[]> from, Function<ArtifactScreen, int[]> to, String midShot) {
        double[] a = at(ctx, from);
        double[] b = at(ctx, to);
        ctx.getInput().setCursorPos(a[0], a[1]);
        ctx.waitTicks(2);
        ctx.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTick();
        for (int k = 1; k <= 6; k++) {
            ctx.getInput().setCursorPos(a[0] + (b[0] - a[0]) * k / 6, a[1] + (b[1] - a[1]) * k / 6);
            ctx.waitTick();
        }
        if (midShot != null) shot(ctx, midShot);
        ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
        settle(ctx, conn);
    }

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

    private static void clickSlot(ClientGameTestContext ctx, int slot) {
        double[] p = slotPixel(ctx, slot);
        if (p == null) return;
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(2);
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
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
