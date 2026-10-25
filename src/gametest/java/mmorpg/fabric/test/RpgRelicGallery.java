package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import mmorpg.core.Relics;
import mmorpg.fabric.client.ArtifactScreen;
import mmorpg.fabric.client.ClientRelics;
import mmorpg.fabric.client.ClientRpgState;
import mmorpg.fabric.client.MmorpgClient;
import mmorpg.fabric.client.RelicScreen;
import mmorpg.fabric.client.StatScreen;
import mmorpg.fabric.client.rpgui.CharacterPanel;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.gear.RelicItem;
import mmorpg.fabric.gear.WeaponEnhance;
import mmorpg.fabric.relic.RelicSystem;
import mmorpg.fabric.smith.EnhanceWindow;
import mmorpg.fabric.smith.GearEnhance;
import mmorpg.fabric.smith.Smith;
import mmorpg.fabric.ui.Forms;
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
 * 0.43.0 아티팩트 사진첩 (화면 시험, only=relic — 실제 키 · 마우스 입력): 가방의 아티팩트 아이콘 · 설명, 인벤토리 패널의 그림 단추 둘 (장신구 · 아티팩트 — 풀이 ·
 * 누르면 판 창), 35렙 (영웅 · 초월은 빨갛게 · 누르면 알림), 50렙: 누르기 → 첫 빈 칸 (꽂음 연출), 끌어 놓기 → 고른 칸, 칸 고르기 → 스탯 줄 고르기 창 · 가방의 것을
 * 누르면 그 칸에, 줄 바꾸기, 판 가득 (힘 8칸 — 3단계 · 선 · 빛), 칸 · 패시브 풀이, 칸끼리 바꾸기 (줄도 함께), 가방으로 끌어 빼기 · [빼기], 탭 (장신구 ↔ 아티팩트 ·
 * I 키는 마지막 탭), 스탯 창 (판 합계가 스탯에), 3인칭 꽂기 연출 (초월 · 단계가 오름), 대장장이 강화 창의 장신구 · 아티팩트 탭 (판 칸을 빼지 않고 +1).
 * 사진 이름 rl_* → ci-logs 의 shots/.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgRelicGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-relic-gallery");
    /** 가방 9 ~ 에 넣을 아티팩트 {종류, 등급} */
    private static final String[][] BAG = {
        {"megingjord", "transcend"}, {"jarngreipr", "hero"}, {"draupnir", "transcend"}, {"aegishjalmr", "hero"}, {"gjallarhorn", "noble"},
        {"skidbladnir", "rare"}, {"idunn", "noble"}, {"mimir", "common"}, {"gleipnir", "hero"}, {"brisingamen", "rare"}, {"megingjord", "common"}
    };
    /** 판 가득 (힘 0 ~ 7 · 민첩 2 · 5 · 8 · 체력 4 · 7 · 8) — {종류, 등급, 줄} */
    private static final String[][] FULL = {
        {"megingjord", "transcend", "atk"}, {"megingjord", "hero", "critDmg"}, {"jarngreipr", "transcend", "crit"},
        {"draupnir", "hero", "rune"}, {"aegishjalmr", "transcend", "def"}, {"jarngreipr", "hero", "cdr"},
        {"draupnir", "transcend", "atk"}, {"aegishjalmr", "hero", "hp"}, {"gjallarhorn", "noble", "speed"}
    };
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("relic")) return;
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
                sp.getInventory().setItem(0, new ItemStack(mmorpg.fabric.ui.Icon.itemById("rpg:sword_l40_hero")));
                for (int i = 0; i < BAG.length; i++) sp.getInventory().setItem(9 + i, relic(BAG[i][0], BAG[i][1]));
                // 아이콘 (가방 셋째 · 넷째 줄 — 종류 열의 초월 · 일반)
                int slot = 20;
                for (String t : Relics.types()) {
                    if (slot >= 36) break;
                    sp.getInventory().setItem(slot++, relic(t, "transcend"));
                }
                sp.getInventory().setSelectedSlot(0);
            });
            ctx.waitTicks(20);
            part(ctx, "inventory", () -> inventory(ctx, conn));
            part(ctx, "low", () -> lowLevel(ctx, server, conn));
            server.runCommand("xp set @a 50 levels");
            ctx.waitTicks(10);
            part(ctx, "window", () -> window(ctx, server, conn));
            part(ctx, "tabs", () -> tabs(ctx, conn));
            part(ctx, "stats", () -> stats(ctx));
            part(ctx, "fx", () -> fx(ctx, server));
            part(ctx, "enhance", () -> enhance(ctx, server));
            ctx.setScreen(() -> null);
        }
        LOG.info("[relic-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
        if (!failures.isEmpty()) throw new AssertionError("relic gallery: " + String.join(" / ", failures));
    }

    private static ItemStack relic(String type, String grade) {
        return new ItemStack(GearItems.byId(Relics.id(type, grade)));
    }

    // ------------------------------------------------------------ 1. 인벤토리 (아이콘 · 설명 · 패널 단추)

    private void inventory(ClientGameTestContext ctx, TestServerConnection conn) {
        ctx.getInput().pressKey(o -> o.keyInventory);
        if (!waitScreen(ctx, sc -> sc instanceof InventoryScreen, "inventory (E)")) ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        ctx.waitTicks(5);
        away(ctx);
        shot(ctx, "rl_inventory");
        hoverAny(ctx, sc -> new int[] {(sc.width - 176) / 2 + 8 + 8, (sc.height - 166) / 2 + 84 + 8}); // 가방 첫 칸 = 초월 메긴교르드
        shot(ctx, "rl_item_tip");
        String lore = ctx.computeOnClient(mc -> {
            StringBuilder b = new StringBuilder();
            for (net.minecraft.network.chat.Component c : Screen.getTooltipFromItem(mc, relic("megingjord", "transcend"))) b.append(c.getString()).append(" / ");
            return b.toString();
        });
        check(lore.contains("메긴교르드") && lore.contains("착용 50렙") && lore.contains("두 배의 힘") && lore.contains("스탯 줄") && lore.contains("우클릭"), "lore (" + lore + ")");
        hoverAny(ctx, sc -> panel(sc).artifactButtonCenter());
        shot(ctx, "rl_panel_accessory_tip");
        hoverAny(ctx, sc -> panel(sc).relicButtonCenter());
        shot(ctx, "rl_panel_relic_tip");
        clickAny(ctx, conn, sc -> panel(sc).relicButtonCenter(), InputConstants.MOUSE_BUTTON_LEFT);
        check(waitScreen(ctx, sc -> sc instanceof RelicScreen, "relic window from the panel button"), "panel relic button opens the board");
        ctx.setScreen(() -> null);
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------ 2. 35렙 (영웅 · 초월은 아직)

    private void lowLevel(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        ctx.getInput().pressKey(MmorpgClient.artifactKey);
        if (!waitScreen(ctx, sc -> sc instanceof RelicScreen, "relic window (I — last tab)")) ctx.setScreen(RelicScreen::new);
        ctx.waitTicks(4);
        away(ctx);
        int n = ctx.computeOnClient(mc -> ((RelicScreen) mc.gui.screen()).bagSlots().size());
        check(n == BAG.length + Relics.types().size(), "window lists the bag relics (" + n + ")");
        shot(ctx, "rl_window_lv35");
        click(ctx, conn, s -> s.gridCenter(0), InputConstants.MOUSE_BUTTON_LEFT);
        shot(ctx, "rl_level_note");
        check(cell(server, 0).equals("-"), "35렙: transcend not equipped (" + cell(server, 0) + ")");
        int noble = indexOf(ctx, "gjallarhorn", "noble");
        if (noble >= 0) click(ctx, conn, s -> s.gridCenter(noble), InputConstants.MOUSE_BUTTON_LEFT);
        check(cell(server, 0).equals("gjallarhorn_noble"), "noble (30) at 35 → first empty cell (" + cell(server, 0) + ")");
        ctx.getInput().pressKey(MmorpgClient.artifactKey);
        ctx.waitTicks(4);
        check(ctx.computeOnClient(mc -> mc.gui.screen() == null), "I closes the window");
        if (ctx.computeOnClient(mc -> mc.gui.screen() != null)) ctx.setScreen(() -> null);
        server.runOnServer(s -> RelicSystem.unequip(player(s), 0));
    }

    // ------------------------------------------------------------ 3. 50렙: 꽂기 · 끌기 · 고르기 · 줄 · 판 가득 · 바꾸기 · 빼기

    private void window(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        ctx.getInput().pressKey(MmorpgClient.artifactKey);
        if (!waitScreen(ctx, sc -> sc instanceof RelicScreen, "relic window (I)")) ctx.setScreen(RelicScreen::new);
        ctx.waitTicks(4);
        int mt = indexOf(ctx, "megingjord", "transcend");
        click(ctx, conn, s -> s.gridCenter(mt), InputConstants.MOUSE_BUTTON_LEFT);
        shot(ctx, "rl_equip_anim");
        check(cell(server, 0).equals("megingjord_transcend"), "click → cell 1 (" + cell(server, 0) + ")");
        int jh = indexOf(ctx, "jarngreipr", "hero");
        drag(ctx, conn, s -> s.gridCenter(jh), s -> s.cellCenter(1), "rl_drag");
        check(cell(server, 1).equals("jarngreipr_hero"), "drag → cell 2 (" + cell(server, 1) + ")");
        // 칸 5 를 고름 → 줄 고르기 창 (빈 칸) → 가방의 드라우프니르를 누르면 그 칸에
        click(ctx, conn, s -> s.cellCenter(4), InputConstants.MOUSE_BUTTON_LEFT);
        check(ctx.computeOnClient(mc -> mc.gui.screen() instanceof RelicScreen s && s.selected() == 4), "cell 5 selected");
        away(ctx);
        shot(ctx, "rl_picker_empty");
        int dt = indexOf(ctx, "draupnir", "transcend");
        click(ctx, conn, s -> s.gridCenter(dt), InputConstants.MOUSE_BUTTON_LEFT);
        check(cell(server, 4).equals("draupnir_transcend"), "bag click → the selected cell (" + cell(server, 4) + ")");
        // 줄 바꾸기: 치명타 확률 (lineKeys 둘째)
        int critK = Relics.lineKeys().indexOf("crit");
        click(ctx, conn, s -> s.statCenter(critK), InputConstants.MOUSE_BUTTON_LEFT);
        check(server.computeOnServer(s -> RelicSystem.stat(player(s), 4)).equals("crit"), "line → crit");
        away(ctx);
        shot(ctx, "rl_picker");
        hover(ctx, s -> s.statCenter(Relics.lineKeys().indexOf("critDmg")));
        shot(ctx, "rl_picker_tip");
        // 판 가득 (서버 — 힘 8칸 · 3단계)
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            for (int i = 0; i < FULL.length; i++) RelicSystem.set(sp, i, relic(FULL[i][0], FULL[i][1]), FULL[i][2]);
        });
        settle(ctx, conn);
        click(ctx, conn, s -> s.cellCenter(4), InputConstants.MOUSE_BUTTON_LEFT); // 고른 칸을 다시 누르면 고르기 끝
        ctx.waitTicks(30);
        away(ctx);
        shot(ctx, "rl_board_full");
        int[] tiers = ctx.computeOnClient(mc -> new int[] {ClientRelics.board().tier("megingjord"), ClientRelics.board().tier("aegishjalmr"),
                ClientRelics.board().tier("gjallarhorn"), ClientRelics.board().active()});
        check(tiers[0] == 3 && tiers[1] == 3 && tiers[2] == 1 && tiers[3] == 5, "client tiers " + java.util.Arrays.toString(tiers));
        double atk = ctx.computeOnClient(mc -> ClientRelics.totals().getOrDefault("atk", 0.0));
        check(Math.abs(atk - (Relics.lineValue("atk", 4, 0) * 2)) < 1e-9, "client atk lines (" + atk + ")");
        hover(ctx, s -> s.cellCenter(0));
        shot(ctx, "rl_cell_tip");
        hover(ctx, s -> s.passiveCenter(0));
        shot(ctx, "rl_passive_tip");
        hover(ctx, s -> s.passiveCenter(3));
        shot(ctx, "rl_passive_more");
        // 칸끼리 바꾸기 (1 ↔ 9 — 줄도 함께)
        drag(ctx, conn, s -> s.cellCenter(0), s -> s.cellCenter(8), null);
        check(cell(server, 8).equals("megingjord_transcend") && cell(server, 0).equals("gjallarhorn_noble")
                && server.computeOnServer(s -> RelicSystem.stat(player(s), 8)).equals("atk"), "swap 1 ↔ 9 (" + cell(server, 0) + ", " + cell(server, 8) + ")");
        // 가방으로 끌어 빼기 · [빼기]
        drag(ctx, conn, s -> s.cellCenter(8), s -> s.gridCenter(20), null);
        check(cell(server, 8).equals("-"), "drag to the bag → off");
        click(ctx, conn, s -> s.cellCenter(2), InputConstants.MOUSE_BUTTON_LEFT);
        click(ctx, conn, RelicScreen::removeCenter, InputConstants.MOUSE_BUTTON_LEFT);
        check(cell(server, 2).equals("-"), "[빼기] → off");
        ctx.waitTicks(10);
        away(ctx);
        shot(ctx, "rl_board_removed");
        ctx.setScreen(() -> null);
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------ 4. 탭 (장신구 ↔ 아티팩트 · I 키는 마지막 탭)

    private void tabs(ClientGameTestContext ctx, TestServerConnection conn) {
        ctx.getInput().pressKey(MmorpgClient.artifactKey);
        if (!waitScreen(ctx, sc -> sc instanceof RelicScreen, "relic window (I)")) ctx.setScreen(RelicScreen::new);
        clickAny(ctx, conn, sc -> ((RelicScreen) sc).tabCenter(0), InputConstants.MOUSE_BUTTON_LEFT);
        check(waitScreen(ctx, sc -> sc instanceof ArtifactScreen, "accessory tab"), "tab → accessories");
        away(ctx);
        shot(ctx, "rl_tab_accessory");
        ctx.getInput().pressKey(MmorpgClient.artifactKey);
        ctx.waitTicks(4);
        ctx.getInput().pressKey(MmorpgClient.artifactKey);
        check(waitScreen(ctx, sc -> sc instanceof ArtifactScreen, "I → last tab (accessories)"), "I opens the last tab (accessories)");
        clickAny(ctx, conn, sc -> ((ArtifactScreen) sc).tabCenter(1), InputConstants.MOUSE_BUTTON_LEFT);
        check(waitScreen(ctx, sc -> sc instanceof RelicScreen, "relic tab"), "tab → relics");
        ctx.setScreen(() -> null);
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------ 5. 스탯 창 (판 합계가 스탯에)

    private void stats(ClientGameTestContext ctx) {
        ctx.getInput().pressKey(MmorpgClient.statsKey);
        if (!waitScreen(ctx, sc -> sc instanceof StatScreen, "stat window (K)")) ctx.setScreen(StatScreen::new);
        ctx.waitTicks(5);
        away(ctx);
        shot(ctx, "rl_stats");
        double[] t = ctx.computeOnClient(mc -> new double[] {ClientRpgState.sheet().tree("def"), ClientRelics.totals().getOrDefault("def", 0.0)});
        check(t[1] > 0 && t[0] >= t[1] - 1e-9, "stat sheet includes relic lines (def " + t[0] + " ≥ " + t[1] + ")");
        ctx.setScreen(() -> null);
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------ 6. 꽂기 연출 (3인칭 — 초월 · 단계가 오름)

    private void fx(ClientGameTestContext ctx, TestServerContext server) {
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        ctx.waitTicks(5);
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            for (int i = 0; i < 36; i++) {
                if (RelicItem.of(sp.getInventory().getItem(i)) == Relics.relic("megingjord", "transcend")) {
                    RelicSystem.equip(sp, i, 2);
                    break;
                }
            }
        });
        ctx.waitTicks(4);
        ctx.takeScreenshot(TestScreenshotOptions.of("rl_equip_fx").withSize(960, 540));
        ctx.waitTicks(8);
        ctx.takeScreenshot(TestScreenshotOptions.of("rl_equip_fx_late").withSize(960, 540));
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------ 7. 대장장이 강화 창의 장신구 · 아티팩트 탭

    private void enhance(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            sp.getInventory().setItem(30, new ItemStack(GearItems.byId("rpg:enhance_stone"), 64));
            sp.getInventory().setItem(31, new ItemStack(GearItems.byId("rpg:bright_stone"), 16));
            Forms.close(sp);
            GearEnhance.rand = () -> 0.0;
            mmorpg.fabric.smith.SmithUi.legacy = true; // 0.44.0: 예전 상자 창 (모드가 없는 클라이언트의 흐름) 을 찍음
            Smith.smithLoop(sp, "enhance", null);
        });
        try {
            if (!waitChest(ctx, "무기 강화")) failures.add("weapon enhance window did not open");
            ctx.takeScreenshot("rl_enhance_weapon");
            hoverSlot(ctx, 1);
            ctx.takeScreenshot("rl_enhance_tab_tip");
            clickSlot(ctx, 1);
            if (!waitChest(ctx, "장신구 · 아티팩트 강화")) failures.add("gear enhance window did not open");
            ctx.takeScreenshot("rl_enhance_gear");
            hoverSlot(ctx, 9 + 1);
            ctx.takeScreenshot("rl_enhance_gear_tip");
            clickSlot(ctx, 9 + 1);
            if (!waitChest(ctx, "아티팩트 강화 §8· §7+0") && !waitChest(ctx, "+0 → +1")) failures.add("confirm window did not open");
            ctx.takeScreenshot("rl_enhance_confirm");
            clickSlot(ctx, 11);
            ctx.waitTicks(EnhanceWindow.ANIM_TICKS / 2);
            ctx.takeScreenshot(TestScreenshotOptions.of("rl_enhance_hammer").withSize(960, 540));
            ctx.waitTicks(EnhanceWindow.ANIM_TICKS / 2 + 6);
            if (!waitChest(ctx, "장신구 · 아티팩트 강화")) failures.add("back to the gear window");
            ctx.waitTicks(4);
            ctx.takeScreenshot("rl_enhance_done");
            int n = server.computeOnServer(s -> WeaponEnhance.of(RelicSystem.get(player(s), 1)));
            check(n == 1, "board cell 2 enhanced to +1 without taking it off (" + n + ")");
        } finally {
            server.runOnServer(s -> {
                GearEnhance.rand = Math::random;
                mmorpg.fabric.smith.SmithUi.legacy = false;
                Forms.close(player(s));
            });
        }
        ctx.setScreen(() -> null);
        ctx.waitTicks(5);
    }

    // ------------------------------------------------------------ 도움

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    /** 칸 i 의 "종류_등급" (빈 칸 "-") */
    private static String cell(TestServerContext server, int i) {
        return server.computeOnServer(s -> {
            Relics.Relic r = RelicSystem.relic(player(s), i);
            return r == null ? "-" : r.type() + "_" + r.grade();
        });
    }

    /** 창의 가방 그리드에서 이 아티팩트의 순서 (없으면 -1) */
    private static int indexOf(ClientGameTestContext ctx, String type, String grade) {
        return ctx.computeOnClient(mc -> {
            if (!(mc.gui.screen() instanceof RelicScreen s)) return -1;
            List<Integer> bag = s.bagSlots();
            for (int k = 0; k < bag.size(); k++) {
                Relics.Relic r = RelicItem.of(mc.player.getInventory().getItem(bag.get(k)));
                if (r != null && r.type().equals(type) && r.grade().equals(grade)) return k;
            }
            return -1;
        });
    }

    private void part(ClientGameTestContext ctx, String name, Runnable run) {
        try {
            run.run();
            LOG.info("[relic-gallery] part {} finished", name);
        } catch (AssertionError | RuntimeException e) {
            check(false, "part " + name + " threw " + e);
            LOG.warn("[relic-gallery] part " + name, e);
        }
        try {
            ctx.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
            ctx.setScreen(() -> null);
            ctx.waitTicks(3);
        } catch (AssertionError | RuntimeException e) {
            LOG.warn("[relic-gallery] reset after " + name, e);
        }
    }

    private void check(boolean ok, String what) {
        LOG.info("[relic-gallery] {} {}", ok ? "OK  " : "FAIL", what);
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

    /** 판 창의 GUI 좌표 → 창 픽셀 좌표 */
    private static double[] at(ClientGameTestContext ctx, Function<RelicScreen, int[]> where) {
        return ctx.computeOnClient(mc -> {
            Screen sc = mc.gui.screen();
            if (!(sc instanceof RelicScreen s)) throw new AssertionError("not the relic window: " + (sc == null ? "none" : sc.getClass().getSimpleName()));
            int[] p = where.apply(s);
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            return new double[] {(p[0] + 0.5) * k, (p[1] + 0.5) * k};
        });
    }

    private static void hover(ClientGameTestContext ctx, Function<RelicScreen, int[]> where) {
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

    private static void click(ClientGameTestContext ctx, TestServerConnection conn, Function<RelicScreen, int[]> where, int button) {
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
    private static void drag(ClientGameTestContext ctx, TestServerConnection conn, Function<RelicScreen, int[]> from, Function<RelicScreen, int[]> to, String midShot) {
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
