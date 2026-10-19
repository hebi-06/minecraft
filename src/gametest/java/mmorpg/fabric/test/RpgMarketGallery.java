package mmorpg.fabric.test;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import mmorpg.core.Armor;
import mmorpg.core.Economy;
import mmorpg.core.Market;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.client.ClientWallet;
import mmorpg.fabric.client.MmorpgClient;
import mmorpg.fabric.client.StatScreen;
import mmorpg.fabric.client.WalletHud;
import mmorpg.fabric.client.form.ChestFormScreen;
import mmorpg.fabric.client.form.ModalFormScreen;
import mmorpg.fabric.economy.DropGuard;
import mmorpg.fabric.economy.EconomyItems;
import mmorpg.fabric.economy.MarketStore;
import mmorpg.fabric.economy.MarketWindow;
import mmorpg.fabric.economy.TrashWindow;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.ui.FormNet;
import mmorpg.fabric.ui.Inv;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.21.0 화폐 · 유저 장터 · 쓰레기통 사진첩 (화면 시험 — 키 market):
 * <ol>
 * <li>지갑 (화면 왼쪽 아래 — 골드 1,234 · 실버 56,789): 클라이언트 값 = 서버 값, 골드 +25 · 실버 −40 → 떠오르는 글자 · 동전이 튐 (w_hud · w_hud_pop · w_hud_after)</li>
 * <li>금화 12개를 들고 우클릭 → 지갑 +12 (손 비움) · 웅크리고 은화 우클릭 → 가방의 은화 모두 (w_coin_use · w_coin_all)</li>
 * <li>스탯 창 윗줄 단추 (장터 · 쓰레기통 풀이) → 장터 창 (다른 판매자 셋의 물건 9개) → 칸 풀이 → 정렬 (싼 순) → 사기 확인 → 삼 (w_stats_* · w_market_*)</li>
 * <li>판매 등록: 가방 고르기 → 가격 입력창에 "350" 치기 → 올림 → 내 물건 → 내리기 확인 → 가방으로 (w_sell_* · w_mine_*)</li>
 * <li>보관함 (기간이 끝난 물건) → 꺼내기 (w_box*)</li>
 * <li>쓰레기통 (스탯 창 윗줄 → 상자 창 → 넣고 ESC → 지움 · 발밑 불꽃) · 되살리기 (w_trash*)</li>
 * <li>버린 아이템 (Q → 들고 있던 칸으로 · 안내 글자) (w_drop)</li>
 * </ol>
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgMarketGallery implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-market-gallery");
    private final List<String> failures = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (RpgProdSmoke.active() || !RpgProdSmoke.only("market")) return;
        ctx.getInput().resizeWindow(1280, 720);
        try (TestSingleplayerContext world = ctx.worldBuilder()
                .adjustSettings(c -> c.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL))
                .create()) {
            TestServerContext server = world.getServer();
            TestServerConnection conn = world.getConnection();
            conn.waitForChunksRender();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("tp @a 0.5 -60 0.5 0 0");
            ctx.runOnClient(mc -> mc.options.chatVisibility().set(ChatVisiblity.HIDDEN));
            ctx.waitTicks(20);
            part(ctx, "hud", () -> hud(ctx, server));
            part(ctx, "coins", () -> coins(ctx, server));
            part(ctx, "market", () -> market(ctx, server, conn));
            part(ctx, "sell", () -> sell(ctx, server, conn));
            part(ctx, "box", () -> box(ctx, server, conn));
            part(ctx, "trash", () -> trash(ctx, server, conn));
            part(ctx, "drop", () -> drop(ctx, server));
            ctx.setScreen(() -> null);
        }
        LOG.info("[market-gallery] done, {} failed checks{}", failures.size(), failures.isEmpty() ? "" : ": " + String.join(" / ", failures));
    }

    // ------------------------------------------------------------ 1. 지갑 HUD

    private void hud(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            sp.getInventory().clearContent();
            Wallet.set(sp, Economy.Kind.GOLD, 1234);
            Wallet.set(sp, Economy.Kind.SILVER, 56789);
        });
        ctx.waitTicks(30);
        long[] cv = ctx.computeOnClient(mc -> new long[] {ClientWallet.get(Economy.Kind.GOLD), ClientWallet.get(Economy.Kind.SILVER)});
        check(cv[0] == 1234 && cv[1] == 56789, "client wallet = server (" + cv[0] + " · " + cv[1] + ")");
        check(ctx.computeOnClient(mc -> WalletHud.visible()), "wallet HUD visible (bottom-left)");
        int[] box = ctx.computeOnClient(mc -> WalletHud.box(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()));
        int[] gui = ctx.computeOnClient(mc -> new int[] {mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()});
        check(box[0] + box[2] < gui[0] / 2 - 91 && box[1] + box[3] <= gui[1], "wallet left of the hotbar " + java.util.Arrays.toString(box) + " gui " + gui[0] + "x" + gui[1]);
        shot(ctx, "w_hud");
        server.runOnServer(s -> {
            Wallet.give(player(s), Economy.Kind.GOLD, 25);
            Inv.pay(player(s), 40);
        });
        ctx.waitTicks(5);
        shot(ctx, "w_hud_pop");
        ctx.waitTicks(40);
        cv = ctx.computeOnClient(mc -> new long[] {ClientWallet.get(Economy.Kind.GOLD), ClientWallet.get(Economy.Kind.SILVER)});
        check(cv[0] == 1259 && cv[1] == 56749, "after +25 gold · -40 silver (" + cv[0] + " · " + cv[1] + ")");
        shot(ctx, "w_hud_after");
    }

    // ------------------------------------------------------------ 2. 동전 우클릭

    private void coins(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            sp.getInventory().clearContent();
            sp.getInventory().setSelectedSlot(0);
            sp.getInventory().setItem(0, new ItemStack(EconomyItems.GOLD_COIN, 12));
        });
        ctx.waitTicks(4);
        long g0 = server.computeOnServer(s -> Wallet.get(player(s), Economy.Kind.GOLD));
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitTicks(3);
        shot(ctx, "w_coin_use");
        long g1 = server.computeOnServer(s -> Wallet.get(player(s), Economy.Kind.GOLD));
        boolean empty = server.computeOnServer(s -> player(s).getMainHandItem().isEmpty());
        check(g1 == g0 + 12 && empty, "right-click 12 gold coins → wallet " + g0 + " → " + g1 + ", hand empty " + empty);
        // 웅크리고 은화 우클릭 → 가방의 은화 모두
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            sp.getInventory().setItem(0, new ItemStack(EconomyItems.SILVER_COIN, 5));
            sp.getInventory().setItem(12, new ItemStack(EconomyItems.SILVER_COIN, 64));
            sp.getInventory().setItem(13, new ItemStack(EconomyItems.SILVER_COIN, 31));
        });
        ctx.waitTicks(4);
        long s0 = server.computeOnServer(s -> Wallet.get(player(s), Economy.Kind.SILVER));
        ctx.getInput().holdKey(o -> o.keyShift);
        ctx.waitTicks(3);
        ctx.getInput().pressKey(o -> o.keyUse);
        ctx.waitTicks(3);
        ctx.getInput().releaseKey(o -> o.keyShift);
        shot(ctx, "w_coin_all");
        long s1 = server.computeOnServer(s -> Wallet.get(player(s), Economy.Kind.SILVER));
        int left = server.computeOnServer(s -> RpgGearTests.countIn(player(s), EconomyItems.SILVER_COIN));
        check(s1 == s0 + 100 && left == 0, "sneak right-click → all 100 silver coins in (" + s0 + " → " + s1 + ", left " + left + ")");
        ctx.waitTicks(20);
    }

    // ------------------------------------------------------------ 3. 장터 둘러보기 · 사기

    private static final UUID HANS = UUID.fromString("00000000-0000-4000-8000-00000000a001");
    private static final UUID MIRA = UUID.fromString("00000000-0000-4000-8000-00000000a002");
    private static final UUID ODIN = UUID.fromString("00000000-0000-4000-8000-00000000a003");

    private void market(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> {
            MarketStore.seed(s, HANS, "대장장이한스", new ItemStack(RpgItems.byId(Weapons.weaponFrom("sword", 20, "rare").id())), 450);
            MarketStore.seed(s, HANS, "대장장이한스", new ItemStack(GearItems.byId(Armor.armorId("str", 40, "transcend", "chest"))), 1200);
            MarketStore.seed(s, HANS, "대장장이한스", new ItemStack(GearItems.byId("rpg:enhance_stone"), 5), 120);
            MarketStore.seed(s, MIRA, "룬수집가미라", RuneItems.stack("int", "hero"), 2800);
            MarketStore.seed(s, MIRA, "룬수집가미라", RuneItems.ticket(2, false), 300);
            MarketStore.seed(s, MIRA, "룬수집가미라", new ItemStack(GearItems.byId("rpg:star_ore"), 2), 220);
            MarketStore.seed(s, ODIN, "사냥꾼오딘", new ItemStack(GearItems.byId("rpg:loot_troll"), 16), 35);
            MarketStore.seed(s, ODIN, "사냥꾼오딘", new ItemStack(Items.GOLDEN_APPLE, 3), 60);
            MarketStore.seed(s, ODIN, "사냥꾼오딘", new ItemStack(Items.DIAMOND, 8), 90);
            player(s).getInventory().clearContent();
        });
        int listed = server.computeOnServer(s -> MarketStore.all(s).size());
        check(listed >= 9, "9 listings seeded (" + listed + ")");
        // 스탯 창 윗줄: 장터 · 쓰레기통 단추
        openStats(ctx);
        away(ctx);
        shot(ctx, "w_stats_header");
        hover(ctx, head(0));
        shot(ctx, "w_stats_tip_market");
        hover(ctx, head(1));
        shot(ctx, "w_stats_tip_trash");
        click(ctx, conn, head(0));
        if (!waitChest(ctx, c -> c.title().contains("유저 장터"), "market from the stat window")) return;
        away(ctx);
        shot(ctx, "w_market");
        // 큰 상자 크기 (9 × 6 — 사용자 요청): 줄 수 · 창 크기 · 화면 안
        int[] lay = ctx.computeOnClient(mc -> mc.gui.screen() instanceof ChestFormScreen c ? c.layout() : new int[] {0, -1});
        int[] box0 = ctx.computeOnClient(mc -> mc.gui.screen() instanceof ChestFormScreen c ? c.bounds() : new int[4]);
        int sh = ctx.computeOnClient(mc -> mc.gui.screen().height);
        check(lay[0] == 6 && box0[3] >= 170 && box0[1] >= 0 && box0[1] + box0[3] <= sh,
                "market is a large chest: " + lay[0] + " rows, " + box0[2] + " × " + box0[3] + " at y " + box0[1] + " (screen height " + sh + ")");
        hover(ctx, chestSlot(0));
        shot(ctx, "w_market_tip");
        hover(ctx, chestSlot(MarketWindow.HELP));
        shot(ctx, "w_market_help");
        // 정렬: 싼 순
        click(ctx, conn, chestSlot(MarketWindow.SORT));
        check(waitChest(ctx, c -> c.slots().get(MarketWindow.SORT).name().contains("싼 순") && c.slots().get(0).num() <= c.slots().get(1).num(), "sorted cheap"),
                "sort → cheap first");
        away(ctx);
        shot(ctx, "w_market_cheap");
        // 다이아몬드 8개 (90 골드) 를 삼
        int slot = ctx.computeOnClient(mc -> {
            if (!(mc.gui.screen() instanceof ChestFormScreen c)) return -1;
            for (int i = 0; i < MarketWindow.PER_PAGE; i++) if (c.form().slots().get(i).num() == 90) return i;
            return -1;
        });
        check(slot >= 0, "diamond listing on the page (" + slot + ")");
        if (slot < 0) return;
        long gold0 = server.computeOnServer(s -> Wallet.total(player(s), Economy.Kind.GOLD));
        // 물건 칸을 두 번 누름: 두 번째 누름은 확인 창의 같은 자리 (진열 · 빈 칸) 에 떨어져 아무 일도 없음 — 사기 칸은 맨 아랫줄
        int[] gridBefore = ctx.computeOnClient(mc -> ((ChestFormScreen) mc.gui.screen()).bounds());
        double[] pt = at(ctx, chestSlot(slot));
        ctx.getInput().setCursorPos(pt[0], pt[1]);
        ctx.waitTick();
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        conn.waitForServerboundPackets();
        ctx.waitTicks(2);
        conn.waitForClientboundPackets();
        if (!waitChest(ctx, c -> c.title().contains("사기 확인"), "buy confirm")) return;
        int[] gridConfirm = ctx.computeOnClient(mc -> ((ChestFormScreen) mc.gui.screen()).bounds());
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        conn.waitForServerboundPackets();
        ctx.waitTicks(3);
        conn.waitForClientboundPackets();
        ctx.waitTicks(2);
        long goldMid = server.computeOnServer(s -> Wallet.total(player(s), Economy.Kind.GOLD));
        boolean stillConfirm = ctx.computeOnClient(mc -> mc.gui.screen() instanceof ChestFormScreen c && c.form().title().contains("사기 확인"));
        check(stillConfirm && goldMid == gold0 && gridBefore[1] == gridConfirm[1],
                "double-click on a listing: only the confirm opens, nothing bought (gold " + gold0 + " → " + goldMid + ", grid top " + gridBefore[1] + " / " + gridConfirm[1] + ")");
        int[] confirmLayout = ctx.computeOnClient(mc -> ((ChestFormScreen) mc.gui.screen()).layout());
        check(confirmLayout[0] == 6 && confirmLayout[1] == MarketWindow.ITEM, "confirm: 6 rows, showcase at " + confirmLayout[1]);
        away(ctx);
        shot(ctx, "w_market_confirm");
        hover(ctx, chestSlot(MarketWindow.ITEM));
        shot(ctx, "w_market_confirm_tip");
        hover(ctx, chestSlot(MarketWindow.OK));
        shot(ctx, "w_market_confirm_ok");
        click(ctx, conn, chestSlot(MarketWindow.OK));
        check(waitChest(ctx, c -> c.title().contains("유저 장터") && c.body().contains("샀습니다"), "bought"), "bought: back to the market, result in the body");
        away(ctx);
        shot(ctx, "w_market_bought");
        long gold1 = server.computeOnServer(s -> Wallet.total(player(s), Economy.Kind.GOLD));
        int diamonds = server.computeOnServer(s -> RpgGearTests.countIn(player(s), Items.DIAMOND));
        long odin = server.computeOnServer(s -> MarketStore.pending(ODIN));
        check(gold1 == gold0 - 90 && diamonds == 8 && odin == 90 - Economy.fee(90), "paid 90 gold (" + gold0 + " → " + gold1 + "), 8 diamonds, offline seller pending " + odin);
        // 종류: 무기만
        click(ctx, conn, chestSlot(MarketWindow.KIND));
        check(waitChest(ctx, c -> c.slots().get(MarketWindow.KIND).name().contains("무기"), "kind weapon"), "kind → weapons");
        away(ctx);
        shot(ctx, "w_market_weapons");
        // 골드가 모자랄 때: 사기 칸이 회색 · 방벽 (누를 수 없음)
        long keepGold = server.computeOnServer(s -> Wallet.get(player(s), Economy.Kind.GOLD));
        server.runOnServer(s -> {
            Wallet.set(player(s), Economy.Kind.GOLD, 10);
            MarketWindow.open(player(s));
        });
        if (waitChest(ctx, c -> c.title().contains("유저 장터") && c.slots().get(MarketWindow.KIND).name().contains("전체"), "market (all)")) {
            int dear = ctx.computeOnClient(mc -> {
                if (!(mc.gui.screen() instanceof ChestFormScreen c)) return -1;
                for (int i = 0; i < MarketWindow.PER_PAGE; i++) if (c.form().slots().get(i).num() == 1200) return i;
                return -1;
            });
            check(dear >= 0, "armor listing (1,200) on the page (" + dear + ")");
            if (dear >= 0) {
                click(ctx, conn, chestSlot(dear));
                if (waitChest(ctx, c -> c.title().contains("사기 확인") && c.body().contains("모자랍니다"), "poor confirm")) {
                    away(ctx);
                    shot(ctx, "w_market_poor");
                    click(ctx, conn, chestSlot(MarketWindow.OK));
                    boolean stays = ctx.computeOnClient(mc -> mc.gui.screen() instanceof ChestFormScreen c && c.form().title().contains("사기 확인"));
                    long g = server.computeOnServer(s -> Wallet.get(player(s), Economy.Kind.GOLD));
                    check(stays && g == 10, "not enough gold: the buy cell does nothing (gold " + g + ")");
                }
            }
        }
        server.runOnServer(s -> Wallet.set(player(s), Economy.Kind.GOLD, keepGold));
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------ 4. 판매 등록 · 내 물건

    private void sell(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            sp.getInventory().setItem(3, new ItemStack(GearItems.byId("rpg:loot_nix"), 12));
            sp.getInventory().setItem(4, new ItemStack(EconomyItems.GOLD_COIN, 3));
            MarketWindow.open(sp);
        });
        if (!waitChest(ctx, c -> c.title().contains("유저 장터"), "market")) return;
        click(ctx, conn, chestSlot(MarketWindow.SELL));
        if (!waitChest(ctx, c -> c.title().contains("판매할 아이템"), "pick window")) return;
        away(ctx);
        shot(ctx, "w_sell_pick");
        int nix = ctx.computeOnClient(mc -> {
            if (!(mc.gui.screen() instanceof ChestFormScreen c)) return -1;
            for (int i = 0; i < MarketWindow.PER_PAGE; i++) {
                FormNet.SlotData d = c.form().slots().get(i);
                if (!d.empty() && d.icon().item().is(GearItems.byId("rpg:loot_nix"))) return i;
            }
            return -1;
        });
        int coin = ctx.computeOnClient(mc -> {
            if (!(mc.gui.screen() instanceof ChestFormScreen c)) return -1;
            for (int i = 0; i < MarketWindow.PER_PAGE; i++) {
                FormNet.SlotData d = c.form().slots().get(i);
                if (!d.empty() && d.icon().item().is(EconomyItems.GOLD_COIN)) return d.kind() == mmorpg.fabric.ui.Slot.DECO ? 1 : 0;
            }
            return -1;
        });
        check(nix == MarketWindow.pickIndex(3) && coin == 1, "nix scale (hotbar 3 → 4th row " + MarketWindow.pickIndex(3) + ") selectable, gold coins grey (deco): " + nix + " / " + coin);
        if (nix >= 0) hover(ctx, chestSlot(nix));
        shot(ctx, "w_sell_pick_tip");
        if (nix < 0) return;
        click(ctx, conn, chestSlot(nix));
        if (!waitScreen(ctx, sc -> sc instanceof ModalFormScreen, "price window")) return;
        away(ctx);
        shot(ctx, "w_sell_price");
        clickInput(ctx, 0, 0.5);
        ctx.getInput().typeChars("350");
        ctx.waitTicks(2);
        shot(ctx, "w_sell_typed");
        click(ctx, conn, sc -> center(((ModalFormScreen) sc).submitButton()));
        check(waitChest(ctx, c -> c.title().contains("유저 장터") && c.body().contains("올렸습니다"), "listed"), "listed: back to the market with the result");
        away(ctx);
        shot(ctx, "w_sell_listed");
        long[] mine = server.computeOnServer(s -> {
            List<Market.Listing<MarketStore.Ware>> l = MarketStore.mine(player(s));
            return new long[] {l.size(), l.isEmpty() ? 0 : l.get(0).price(), l.isEmpty() ? 0 : l.get(0).item().stack().getCount(),
                    RpgGearTests.countIn(player(s), GearItems.byId("rpg:loot_nix"))};
        });
        check(mine[0] == 1 && mine[1] == 350 && mine[2] == 12 && mine[3] == 0, "my listing 12 nix scales at 350 (" + java.util.Arrays.toString(mine) + ")");
        // 내 물건 → 내리기
        click(ctx, conn, chestSlot(MarketWindow.MINE));
        if (!waitChest(ctx, c -> c.title().contains("내 물건"), "mine")) return;
        away(ctx);
        shot(ctx, "w_mine");
        click(ctx, conn, chestSlot(0));
        if (!waitChest(ctx, c -> c.title().contains("내리기 확인"), "cancel confirm")) return;
        away(ctx);
        shot(ctx, "w_mine_cancel");
        click(ctx, conn, chestSlot(MarketWindow.OK));
        check(waitChest(ctx, c -> c.title().contains("내 물건") && c.body().contains("내렸습니다"), "cancelled"), "cancelled: back to my items");
        away(ctx);
        shot(ctx, "w_mine_after");
        int back = server.computeOnServer(s -> RpgGearTests.countIn(player(s), GearItems.byId("rpg:loot_nix")));
        check(back == 12, "nix scales back in the bag (" + back + ")");
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------ 5. 보관함

    private void box(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            MarketStore.seed(s, sp.getUUID(), sp.getName().getString(), new ItemStack(Items.IRON_INGOT, 20), 15);
            long now = System.currentTimeMillis();
            MarketStore.clock = () -> now + Economy.marketMillis() + 60_000;
            try {
                MarketStore.expire(s);
            } finally {
                MarketStore.clock = null;
            }
            MarketWindow.open(sp);
        });
        if (!waitChest(ctx, c -> c.title().contains("유저 장터"), "market")) return;
        check(waitChest(ctx, c -> c.slots().get(MarketWindow.BOX).num() >= 1, "box count"), "box button shows 1");
        away(ctx);
        shot(ctx, "w_box_button");
        click(ctx, conn, chestSlot(MarketWindow.BOX));
        if (!waitChest(ctx, c -> c.title().contains("보관함"), "box")) return;
        away(ctx);
        shot(ctx, "w_box");
        click(ctx, conn, chestSlot(0));
        check(waitChest(ctx, c -> c.title().contains("보관함") && c.body().contains("꺼냈습니다"), "claimed"), "claimed from the box");
        int iron = server.computeOnServer(s -> RpgGearTests.countIn(player(s), Items.IRON_INGOT));
        check(iron == 20, "20 iron back (" + iron + ")");
        away(ctx);
        shot(ctx, "w_box_after");
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        ctx.waitTicks(3);
    }

    // ------------------------------------------------------------ 6. 쓰레기통

    private void trash(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            sp.getInventory().clearContent();
            sp.getInventory().setItem(9, new ItemStack(Items.ROTTEN_FLESH, 30));
            sp.getInventory().setItem(10, new ItemStack(Items.COBBLESTONE, 64));
            sp.getInventory().setItem(11, new ItemStack(Items.POISONOUS_POTATO, 5));
        });
        ctx.waitTicks(3);
        openStats(ctx);
        click(ctx, conn, head(1));
        if (!waitScreen(ctx, sc -> sc instanceof ContainerScreen, "trash window")) return;
        String title = ctx.computeOnClient(mc -> mc.gui.screen().getTitle().getString());
        check(title.contains("쓰레기통"), "trash title '" + title + "'");
        // 가방의 셋을 쓰레기통 칸으로 (서버에서 옮김 — 화면에 그대로 보임)
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            if (!(sp.containerMenu instanceof ChestMenu m)) return;
            for (int i = 0; i < 3; i++) {
                m.getSlot(i * 2).set(sp.getInventory().removeItemNoUpdate(9 + i));
            }
            sp.getInventory().setChanged();
            m.broadcastChanges();
        });
        ctx.waitTicks(4);
        away(ctx);
        shot(ctx, "w_trash");
        int burned0 = server.computeOnServer(s -> TrashWindow.burned);
        ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
        conn.waitForServerboundPackets();
        ctx.waitTicks(2);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        ctx.waitTicks(3);
        shot(ctx, "w_trash_burn");
        int[] r = server.computeOnServer(s -> new int[] {TrashWindow.burned, TrashWindow.last(player(s).getUUID()).size(), RpgGearTests.countIn(player(s), Items.COBBLESTONE)});
        check(r[0] == burned0 + 1 && r[1] == 3 && r[2] == 0, "burned 3 stacks on close (" + java.util.Arrays.toString(r) + ")");
        String msg = server.computeOnServer(s -> TrashWindow.restore(player(s)));
        int cobble = server.computeOnServer(s -> RpgGearTests.countIn(player(s), Items.COBBLESTONE));
        check(msg.startsWith("§a") && cobble == 64, "restored: " + msg + " (" + cobble + ")");
        ctx.waitTicks(10);
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
    }

    // ------------------------------------------------------------ 7. 버린 아이템

    private void drop(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> {
            ServerPlayer sp = player(s);
            sp.getInventory().clearContent();
            sp.getInventory().setSelectedSlot(0);
            sp.getInventory().setItem(0, new ItemStack(RpgItems.byId(Weapons.weaponFrom("sword", 10, "rare").id())));
        });
        ctx.waitTicks(4);
        int kept0 = server.computeOnServer(s -> DropGuard.kept);
        ctx.getInput().pressKey(o -> o.keyDrop);
        ctx.waitTicks(4);
        shot(ctx, "w_drop");
        boolean held = server.computeOnServer(s -> !player(s).getInventory().getItem(0).isEmpty());
        int swords = server.computeOnServer(s -> RpgGearTests.countIn(player(s), RpgItems.byId(Weapons.weaponFrom("sword", 10, "rare").id())));
        int ground = server.computeOnServer(s -> player(s).level().getEntitiesOfClass(ItemEntity.class, player(s).getBoundingBox().inflate(5)).size());
        int kept = server.computeOnServer(s -> DropGuard.kept);
        boolean shown = ctx.computeOnClient(mc -> !mc.player.getInventory().getItem(0).isEmpty());
        check(held && swords == 1 && ground == 0 && kept == kept0 + 1 && shown,
                "Q: the sword stays in the hand slot (held " + held + ", swords " + swords + ", on ground " + ground + ", client shows " + shown + ")");
    }

    // ------------------------------------------------------------ 도움

    private void part(ClientGameTestContext ctx, String name, Runnable run) {
        try {
            run.run();
            LOG.info("[market-gallery] part {} finished", name);
        } catch (AssertionError | RuntimeException e) {
            check(false, "part " + name + " threw " + e);
            LOG.warn("[market-gallery] part " + name, e);
        }
        try {
            ctx.setScreen(() -> null);
            ctx.waitTicks(5);
        } catch (AssertionError | RuntimeException e) {
            LOG.warn("[market-gallery] reset after " + name, e);
        }
    }

    private void check(boolean ok, String what) {
        LOG.info("[market-gallery] {} {}", ok ? "OK  " : "FAIL", what);
        if (!ok) failures.add(what);
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
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

    private void openStats(ClientGameTestContext ctx) {
        ctx.getInput().pressKey(MmorpgClient.statsKey);
        if (!waitScreen(ctx, sc -> sc instanceof StatScreen, "stat window (K)")) ctx.setScreen(StatScreen::new);
        ctx.waitTicks(3);
    }

    private static Function<Screen, int[]> head(int i) {
        return sc -> ((StatScreen) sc).headCenter(i);
    }

    private static String screenName(Minecraft mc) {
        Screen sc = mc.gui.screen();
        if (sc == null) return "none";
        if (sc instanceof ChestFormScreen c) return "chest '" + c.form().title() + "'";
        return sc.getClass().getSimpleName();
    }

    private boolean waitScreen(ClientGameTestContext ctx, Predicate<Screen> test, String what) {
        try {
            ctx.waitFor(mc -> mc.gui.screen() != null && test.test(mc.gui.screen()), 100);
            ctx.waitTicks(2);
            return true;
        } catch (AssertionError | RuntimeException e) {
            check(false, "screen " + what + " did not open (now " + ctx.computeOnClient(RpgMarketGallery::screenName) + ")");
            return false;
        }
    }

    private boolean waitChest(ClientGameTestContext ctx, Predicate<FormNet.ChestPayload> test, String what) {
        return waitScreen(ctx, sc -> sc instanceof ChestFormScreen c && test.test(c.form()), what);
    }

    private static Function<Screen, int[]> chestSlot(int i) {
        return sc -> ((ChestFormScreen) sc).slotCenter(i);
    }

    private static double[] at(ClientGameTestContext ctx, Function<Screen, int[]> where) {
        return ctx.computeOnClient(mc -> {
            Screen sc = mc.gui.screen();
            int[] p = sc == null ? null : where.apply(sc);
            if (p == null) throw new AssertionError("no point on screen " + screenName(mc));
            double k = (double) mc.getWindow().getScreenWidth() / sc.width;
            return new double[] {(p[0] + 0.5) * k, (p[1] + 0.5) * k};
        });
    }

    private static void hover(ClientGameTestContext ctx, Function<Screen, int[]> where) {
        double[] p = at(ctx, where);
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTicks(3);
    }

    private static void click(ClientGameTestContext ctx, TestServerConnection conn, Function<Screen, int[]> where) {
        double[] p = at(ctx, where);
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTick();
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        conn.waitForServerboundPackets();
        ctx.waitTicks(2);
        conn.waitForClientboundPackets();
        ctx.waitTicks(2);
    }

    private void clickInput(ClientGameTestContext ctx, int i, double fx) {
        double[] p = at(ctx, sc -> {
            AbstractWidget w = ((ModalFormScreen) sc).input(i);
            return w == null ? null : new int[] {w.getX() + (int) (w.getWidth() * fx), w.getY() + w.getHeight() / 2};
        });
        ctx.getInput().setCursorPos(p[0], p[1]);
        ctx.waitTick();
        ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(2);
    }

    private static int[] center(AbstractWidget w) {
        return w == null ? null : new int[] {w.getX() + w.getWidth() / 2, w.getY() + w.getHeight() / 2};
    }
}
