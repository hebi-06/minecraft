package mmorpg.fabric.test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Economy;
import mmorpg.core.Market;
import mmorpg.fabric.Props;
import mmorpg.fabric.RpgNet;
import mmorpg.fabric.economy.DropGuard;
import mmorpg.fabric.economy.EconomyItems;
import mmorpg.fabric.economy.EconomySystem;
import mmorpg.fabric.economy.MarketStore;
import mmorpg.fabric.economy.MarketWindow;
import mmorpg.fabric.economy.TrashWindow;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.quest.MainQuests;
import mmorpg.fabric.quest.Quest;
import mmorpg.fabric.quest.QuestRewards;
import mmorpg.fabric.quest.Quests;
import mmorpg.fabric.quest.SideQuests;
import mmorpg.fabric.ui.FormNet;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.Inv;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/**
 * 0.21.0 게임 시험 (서버) — 화폐 · 유저 장터 · 쓰레기통 · 버린 아이템:
 * 지갑 (금화 · 은화 우클릭 · 웅크리면 모두 · 꺼내기 · 자리 · 내기 순서 (지갑 → 동전) · 가득 차면 동전), 에메랄드 → 실버 (처음 접속 한 번 · 엔더 상자 ·
 * 블록 9개 · /환전), 버린 아이템 (Q · 통째 · 창작은 그대로 · 끄면 바닐라), 쓰레기통 (닫으면 지움 · 되살리기 · 자리 없으면 안 됨), 장터 (올리기 · 거절 ·
 * 사기 · 수수료 · 접속하지 않은 판매자 · 내리기 · 기간 끝 · 보관함 · 저장 · 다시 읽기 · 창 흐름), 골드 보상 (메인 · 반복 · 왕실 · 보상 주기),
 * 명령어 (/골드 · /실버 · /지갑 · /환전 · /장터 · /쓰레기통) · 스탯 창 단추 (market · trash).
 */
public final class RpgEconomyTests {
    static ServerPlayer player(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        sp.getInventory().clearContent();
        Wallet.set(sp, Economy.Kind.GOLD, 0);
        Wallet.set(sp, Economy.Kind.SILVER, 0);
        return sp;
    }

    static int count(ServerPlayer sp, net.minecraft.world.item.Item item) {
        return RpgGearTests.countIn(sp, item);
    }

    // ------------------------------------------------------------ 지갑 · 동전

    @GameTest(maxTicks = 20)
    public void walletCoins(GameTestHelper h) {
        ServerPlayer sp = player(h);
        h.assertTrue(EconomyItems.GOLD_COIN.kind == Economy.Kind.GOLD && EconomyItems.SILVER_COIN.kind == Economy.Kind.SILVER && EconomyItems.coin(Economy.Kind.GOLD) == EconomyItems.GOLD_COIN, "coin items");
        h.assertTrue(Inv.CURRENCY == EconomyItems.SILVER_COIN && Inv.CURRENCY_NAME.equals("실버"), "NPC currency = silver coin " + Inv.CURRENCY);
        h.assertTrue(new ItemStack(EconomyItems.GOLD_COIN).getHoverName().getString().contains("금화"), "gold coin name");
        // 우클릭: 손의 묶음만
        sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EconomyItems.GOLD_COIN, 12));
        sp.getInventory().setItem(20, new ItemStack(EconomyItems.GOLD_COIN, 5));
        EconomyItems.GOLD_COIN.use(sp.level(), sp, InteractionHand.MAIN_HAND);
        h.assertTrue(Wallet.get(sp, Economy.Kind.GOLD) == 12 && sp.getMainHandItem().isEmpty() && count(sp, EconomyItems.GOLD_COIN) == 5, "deposit held stack " + Wallet.get(sp, Economy.Kind.GOLD));
        // 웅크리고 우클릭: 가방의 같은 동전 모두 (은화는 그대로)
        sp.getInventory().setItem(21, new ItemStack(EconomyItems.SILVER_COIN, 7));
        sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(EconomyItems.GOLD_COIN, 1));
        h.assertTrue(Wallet.deposit(sp, InteractionHand.MAIN_HAND, Economy.Kind.GOLD, true) == 6, "deposit all gold");
        h.assertTrue(Wallet.get(sp, Economy.Kind.GOLD) == 18 && count(sp, EconomyItems.GOLD_COIN) == 0 && count(sp, EconomyItems.SILVER_COIN) == 7, "all gold in, silver left");
        // 꺼내기: 지갑에서 금화로 (모자라면 · 자리 없으면 거절)
        h.assertTrue(!Wallet.withdraw(sp, Economy.Kind.GOLD, 19).startsWith("§a") && Wallet.get(sp, Economy.Kind.GOLD) == 18, "withdraw too much refused");
        h.assertTrue(Wallet.withdraw(sp, Economy.Kind.GOLD, 10).startsWith("§a") && Wallet.get(sp, Economy.Kind.GOLD) == 8 && count(sp, EconomyItems.GOLD_COIN) == 10, "withdraw 10");
        for (int i = 0; i < Inv.SIZE; i++) if (Inv.get(sp, i).isEmpty()) Inv.set(sp, i, new ItemStack(Items.DIRT, 64));
        Inv.set(sp, 0, new ItemStack(EconomyItems.GOLD_COIN, 62));
        Inv.set(sp, 1, new ItemStack(Items.DIRT, 64));
        int coins = count(sp, EconomyItems.GOLD_COIN);
        h.assertTrue(!Wallet.withdraw(sp, Economy.Kind.GOLD, 5).startsWith("§a") && Wallet.get(sp, Economy.Kind.GOLD) == 8, "no room → refused " + coins);
        h.assertTrue(Wallet.withdraw(sp, Economy.Kind.GOLD, 2).startsWith("§a") && count(sp, EconomyItems.GOLD_COIN) == coins + 2, "fits 2 on the partial stack");
        sp.getInventory().clearContent();
        // 내기: 지갑 먼저 → 모자라면 가방의 동전 → 모두 모자라면 그대로
        Wallet.set(sp, Economy.Kind.SILVER, 30);
        Inv.set(sp, 3, new ItemStack(EconomyItems.SILVER_COIN, 20));
        h.assertTrue(Inv.money(sp) == 50 && Wallet.total(sp, Economy.Kind.SILVER) == 50, "money = wallet + coins");
        h.assertTrue(Inv.payTakes(sp, 20).isEmpty() && Inv.payTakes(sp, 45).get(0).n() == 15, "coin takes only beyond the wallet");
        h.assertTrue(Inv.pay(sp, 20) && Wallet.get(sp, Economy.Kind.SILVER) == 10 && count(sp, EconomyItems.SILVER_COIN) == 20, "pay from the wallet first");
        h.assertTrue(Inv.pay(sp, 25) && Wallet.get(sp, Economy.Kind.SILVER) == 0 && count(sp, EconomyItems.SILVER_COIN) == 5, "then coins");
        h.assertTrue(!Inv.pay(sp, 6) && count(sp, EconomyItems.SILVER_COIN) == 5, "short → nothing taken");
        // 받기: 지갑으로, 가득이면 남은 몫은 동전
        Inv.earn(sp, 40);
        h.assertTrue(Wallet.get(sp, Economy.Kind.SILVER) == 40, "earn → wallet");
        Wallet.set(sp, Economy.Kind.GOLD, Economy.walletMax() - 3);
        Wallet.give(sp, Economy.Kind.GOLD, 10);
        h.assertTrue(Wallet.get(sp, Economy.Kind.GOLD) == Economy.walletMax() && count(sp, EconomyItems.GOLD_COIN) == 7, "full wallet → 7 coins");
        // 저장 (붙인 값 · 죽어도 그대로)
        h.assertTrue(Wallet.TYPE != null && sp.getAttached(Wallet.TYPE) != null && sp.getAttached(Wallet.TYPE).silver() == 40, "attached purse");
        RpgGearTests.done(h, sp);
    }

    // ------------------------------------------------------------ 에메랄드 → 실버

    @GameTest(maxTicks = 20)
    public void emeraldsBecomeSilver(GameTestHelper h) {
        ServerPlayer sp = player(h);
        h.assertTrue(Props.get(sp, Wallet.MIGRATED) != null, "new player marked (nothing to convert)");
        Props.set(sp, Wallet.MIGRATED, null); // 0.21.0 전에 있던 사람처럼
        sp.getInventory().setItem(4, new ItemStack(Items.EMERALD, 37));
        sp.getInventory().setItem(30, new ItemStack(Items.EMERALD_BLOCK, 2));
        sp.getInventory().setItem(40, new ItemStack(Items.EMERALD, 3)); // 왼손
        sp.getEnderChestInventory().setItem(5, new ItemStack(Items.EMERALD, 10));
        h.assertTrue(Wallet.migrate(sp) == 37 + 18 + 3 + 10, "converted " + Wallet.get(sp, Economy.Kind.SILVER));
        h.assertTrue(Wallet.get(sp, Economy.Kind.SILVER) == 68 && count(sp, Items.EMERALD) == 0 && count(sp, Items.EMERALD_BLOCK) == 0
                && sp.getEnderChestInventory().getItem(5).isEmpty() && sp.getInventory().getItem(40).isEmpty(), "all emeralds gone, silver 68");
        sp.getInventory().setItem(4, new ItemStack(Items.EMERALD, 5));
        h.assertTrue(Wallet.migrate(sp) == 0 && count(sp, Items.EMERALD) == 5, "only once");
        // /환전: 가방의 에메랄드 → 실버 1:1
        h.assertTrue(Wallet.exchange(sp).startsWith("§a") && Wallet.get(sp, Economy.Kind.SILVER) == 73 && count(sp, Items.EMERALD) == 0, "exchange 5");
        h.assertTrue(!Wallet.exchange(sp).startsWith("§a"), "nothing to exchange");
        RpgGearTests.done(h, sp);
    }

    // ------------------------------------------------------------ 버린 아이템 · 쓰레기통

    @GameTest(maxTicks = 20)
    public void dropComesBack(GameTestHelper h) {
        ServerPlayer sp = player(h);
        sp.getInventory().setSelectedSlot(2);
        sp.getInventory().setItem(2, new ItemStack(Items.DIAMOND, 5));
        int kept = DropGuard.kept;
        sp.drop(false); // Q: 하나
        h.assertTrue(sp.getInventory().getItem(2).is(Items.DIAMOND) && sp.getInventory().getItem(2).getCount() == 5, "one diamond back on the stack");
        sp.drop(true); // Ctrl + Q: 통째
        h.assertTrue(sp.getInventory().getItem(2).getCount() == 5 && count(sp, Items.DIAMOND) == 5, "whole stack back in the held slot");
        h.assertTrue(DropGuard.kept >= kept + 2, "counted " + (DropGuard.kept - kept));
        AABB around = sp.getBoundingBox().inflate(4);
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, around, e -> e.getItem().is(Items.DIAMOND)).isEmpty(), "nothing on the ground");
        // 창작 모드는 바닐라대로
        sp.setGameMode(GameType.CREATIVE);
        sp.drop(true);
        h.assertTrue(sp.getInventory().getItem(2).isEmpty(), "creative drops");
        h.getLevel().getEntitiesOfClass(ItemEntity.class, around, e -> e.getItem().is(Items.DIAMOND)).forEach(ItemEntity::discard);
        sp.setGameMode(GameType.SURVIVAL);
        // 끄면 바닐라
        sp.getInventory().setItem(2, new ItemStack(Items.EMERALD, 1));
        DropGuard.enabled = false;
        try {
            sp.drop(true);
            h.assertTrue(sp.getInventory().getItem(2).isEmpty(), "switch off → vanilla drop");
        } finally {
            DropGuard.enabled = true;
        }
        h.getLevel().getEntitiesOfClass(ItemEntity.class, around).forEach(ItemEntity::discard);
        RpgGearTests.done(h, sp);
    }

    @GameTest(maxTicks = 20)
    public void trashBurnsAndRestores(GameTestHelper h) {
        ServerPlayer sp = player(h);
        h.assertTrue(TrashWindow.open(sp) && sp.containerMenu instanceof ChestMenu, "trash window open");
        ChestMenu menu = (ChestMenu) sp.containerMenu;
        h.assertTrue(menu.getRowCount() == 3, "27 slots");
        menu.getSlot(0).set(new ItemStack(Items.ROTTEN_FLESH, 20));
        menu.getSlot(5).set(new ItemStack(GearItems.byId("rpg:loot_troll"), 3));
        int burned = TrashWindow.burned;
        sp.closeContainer();
        h.assertTrue(TrashWindow.burned == burned + 1 && TrashWindow.last(sp.getUUID()).size() == 2, "burned 2 stacks");
        h.assertTrue(count(sp, Items.ROTTEN_FLESH) == 0, "not in the bag");
        // 되살리기: 가방에 자리가 없으면 안 됨 → 비우면 됨 (한 번만)
        for (int i = 0; i < Inv.SIZE; i++) Inv.set(sp, i, new ItemStack(Items.DIRT, 64));
        h.assertTrue(!TrashWindow.restore(sp).startsWith("§a") && TrashWindow.last(sp.getUUID()).size() == 2, "no room → kept");
        sp.getInventory().clearContent();
        h.assertTrue(TrashWindow.restore(sp).startsWith("§a") && count(sp, Items.ROTTEN_FLESH) == 20 && count(sp, GearItems.byId("rpg:loot_troll")) == 3, "restored");
        h.assertTrue(!TrashWindow.restore(sp).startsWith("§a"), "restore only once");
        // 빈 채로 닫으면 아무것도 지우지 않음
        TrashWindow.open(sp);
        burned = TrashWindow.burned;
        sp.closeContainer();
        h.assertTrue(TrashWindow.burned == burned, "empty close burns nothing");
        // 스탯 창 단추
        h.assertTrue(RpgNet.statAction(sp, "trash") && sp.containerMenu instanceof ChestMenu, "stat button trash");
        sp.closeContainer();
        RpgGearTests.done(h, sp);
    }

    // ------------------------------------------------------------ 장터

    @GameTest(maxTicks = 40)
    public void marketTrades(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer a = player(h);
        ServerPlayer b = player(h);
        UUID ua = a.getUUID();
        try {
            // 올리기 · 거절
            Inv.set(a, 0, new ItemStack(GearItems.byId("rpg:loot_troll"), 10));
            Inv.set(a, 1, new ItemStack(EconomyItems.GOLD_COIN, 5));
            Inv.set(a, 2, new ItemStack(Items.DIAMOND_SWORD));
            h.assertTrue(MarketStore.refuse(Inv.get(a, 1)) != null && !MarketStore.list(a, 1, null, 5, 10).ok(), "coins refused");
            h.assertTrue(!MarketStore.list(a, 0, null, 4, 0).ok() && !MarketStore.list(a, 0, null, 4, Economy.maxPrice() + 1).ok() && count(a, GearItems.byId("rpg:loot_troll")) == 10,
                    "price range refused, nothing taken");
            h.assertTrue(!MarketStore.list(a, 0, new ItemStack(Items.DIRT), 4, 10).ok(), "moved item refused");
            MarketStore.Outcome o = MarketStore.list(a, 0, Inv.get(a, 0).copy(), 4, 1000);
            h.assertTrue(o.ok() && count(a, GearItems.byId("rpg:loot_troll")) == 6, "listed 4 tusks: " + o.text());
            List<Market.Listing<MarketStore.Ware>> mine = MarketStore.mine(a);
            h.assertTrue(mine.size() == 1 && mine.get(0).price() == 1000 && mine.get(0).item().stack().getCount() == 4 && mine.get(0).sellerName().equals(a.getName().getString()),
                    "my listing " + mine);
            long id = mine.get(0).id();
            // 사기: 본인은 못 삼 · 골드가 모자람 · 삼 (수수료 5% — 판매자 950)
            h.assertTrue(!MarketStore.buy(a, id).ok(), "own listing refused");
            Wallet.set(b, Economy.Kind.GOLD, 500);
            h.assertTrue(!MarketStore.buy(b, id).ok() && Wallet.get(b, Economy.Kind.GOLD) == 500, "not enough gold");
            Wallet.set(b, Economy.Kind.GOLD, 900);
            Inv.set(b, 8, new ItemStack(EconomyItems.GOLD_COIN, 150)); // 지갑 900 + 금화 150 → 1,000 을 냄 (금화 100)
            h.assertTrue(MarketStore.buy(b, id).ok(), "bought");
            h.assertTrue(count(b, GearItems.byId("rpg:loot_troll")) == 4 && Wallet.get(b, Economy.Kind.GOLD) == 0 && count(b, EconomyItems.GOLD_COIN) == 50, "buyer paid 1,000");
            h.assertTrue(Wallet.get(a, Economy.Kind.GOLD) == 950 && MarketStore.find(a, id) == null, "seller +950 (fee 50), listing gone");
            h.assertTrue(!MarketStore.buy(b, id).ok(), "sold once");
            // 접속하지 않은 판매자: 대금은 다음 접속에
            long id2 = MarketStore.list(a, 2, null, 1, 100).ok() ? MarketStore.mine(a).get(0).id() : -1;
            h.assertTrue(id2 > 0, "listed the sword");
            server.getPlayerList().remove(a);
            Wallet.set(b, Economy.Kind.GOLD, 100);
            h.assertTrue(MarketStore.buy(b, id2).ok() && count(b, Items.DIAMOND_SWORD) == 1, "bought the sword (seller offline)");
            h.assertTrue(MarketStore.pending(ua) == 95 && Wallet.get(a, Economy.Kind.GOLD) == 950, "pending 95 for the offline seller");
            EconomySystem.onJoin(a);
            h.assertTrue(MarketStore.pending(ua) == 0 && Wallet.get(a, Economy.Kind.GOLD) == 1045, "paid on join " + Wallet.get(a, Economy.Kind.GOLD));
            // 한 사람 10개
            Inv.set(a, 3, new ItemStack(Items.COBBLESTONE, 64));
            for (int i = 0; i < Economy.marketMax(); i++) h.assertTrue(MarketStore.list(a, 3, null, 1, 5 + i).ok(), "list " + i);
            h.assertTrue(!MarketStore.list(a, 3, null, 1, 5).ok() && MarketStore.mine(a).size() == Economy.marketMax(), "11th refused");
            // 내리기 (가방으로) · 남의 것은 못 내림 · 운영자는 내림 (판매자 보관함으로)
            long c1 = MarketStore.mine(a).get(0).id();
            h.assertTrue(!MarketStore.cancel(b, c1, false).ok(), "not owner");
            int cobble = count(a, Items.COBBLESTONE);
            h.assertTrue(MarketStore.cancel(a, c1, false).ok() && count(a, Items.COBBLESTONE) == cobble + 1, "cancel → bag");
            long c2 = MarketStore.mine(a).get(0).id();
            h.assertTrue(MarketStore.cancel(b, c2, true).ok() && MarketStore.box(a).size() == 1, "admin remove → seller's box");
            // 저장 · 다시 읽기 (아이템 JSON)
            int before = MarketStore.mine(a).size();
            MarketStore.State st = server.overworld().getAttached(MarketStore.TYPE);
            h.assertTrue(st != null && st.listings().stream().filter(x -> x.seller().equals(ua.toString())).count() == before, "saved listings " + before);
            MarketStore.load(server);
            h.assertTrue(MarketStore.mine(a).size() == before && MarketStore.mine(a).get(0).item().stack().is(Items.COBBLESTONE) && MarketStore.box(a).size() == 1,
                    "reloaded " + MarketStore.mine(a).size());
            // 기간 끝 → 보관함 (꺼내기)
            long now = System.currentTimeMillis();
            MarketStore.clock = () -> now + Economy.marketMillis() + 60_000;
            MarketStore.expire(server);
            h.assertTrue(MarketStore.mine(a).isEmpty() && MarketStore.box(a).size() == before + 1, "expired → box " + MarketStore.box(a).size());
            MarketStore.clock = null;
            a.getInventory().clearContent();
            h.assertTrue(MarketStore.claim(a, 0).ok() && MarketStore.box(a).size() == before, "claim one");
            h.assertTrue(MarketStore.claim(a, -1).ok() && MarketStore.box(a).isEmpty() && count(a, Items.COBBLESTONE) == before + 1, "claim all");
        } finally {
            MarketStore.clock = null;
            for (Market.Listing<MarketStore.Ware> l : MarketStore.all(server)) {
                if (l.seller().equals(ua)) MarketStore.cancel(a, l.id(), true);
            }
            MarketStore.claim(a, -1);
        }
        server.getPlayerList().remove(b);
        h.succeed();
    }

    /** 장터 창 흐름 (클라이언트 없이): 판매 등록 (가방 고르기 → 가격 입력창) → 둘러보기 → 다른 사람이 사기 확인 → 삼 */
    @GameTest(maxTicks = 40)
    public void marketWindowFlow(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer a = player(h);
        ServerPlayer b = player(h);
        Forms.HEADLESS.add(a.getUUID());
        Forms.HEADLESS.add(b.getUUID());
        try {
            Inv.set(a, 0, new ItemStack(Items.GOLDEN_APPLE, 3));
            h.assertTrue(RpgNet.statAction(a, "market") && chest(a) != null && chest(a).title().contains("유저 장터"), "stat button → market");
            h.assertTrue(chest(a).slots().get(MarketWindow.SELL).name().contains("판매 등록"), "sell button");
            Forms.answer(a, MarketWindow.SELL, null);
            h.assertTrue(chest(a) != null && chest(a).title().contains("판매할 아이템"), "pick window " + title(a));
            h.assertTrue(chest(a).slots().get(0).name().contains("황금 사과") || chest(a).slots().get(0).name().toLowerCase().contains("apple"), "bag item shown " + chest(a).slots().get(0).name());
            Forms.answer(a, 0, null);
            FormNet.ModalPayload m = Forms.last(a) instanceof FormNet.ModalPayload mm ? mm : null;
            h.assertTrue(m != null && m.title().contains("판매 등록"), "price window");
            // 가격 "1,250" · 개수 2
            Forms.answer(a, 0, List.of("1,250", "2"));
            h.assertTrue(chest(a) != null && chest(a).title().contains("유저 장터") && chest(a).body().contains("올렸습니다"), "back to market " + body(a));
            h.assertTrue(count(a, Items.GOLDEN_APPLE) == 1 && MarketStore.mine(a).size() == 1 && MarketStore.mine(a).get(0).price() == 1250, "listed 2 apples at 1,250");
            Forms.answer(a, -1, null);
            // 사는 사람: 둘러보기 → 그 칸 → 확인 → 삼
            Wallet.set(b, Economy.Kind.GOLD, 2000);
            MarketWindow.open(b);
            int slot = -1;
            List<FormNet.SlotData> slots = chest(b).slots();
            for (int i = 0; i < MarketWindow.PER_PAGE; i++) if (slots.get(i).name().contains("×2") && slots.get(i).num() == 1250) slot = i;
            h.assertTrue(slot >= 0, "listing shown with price " + slots.subList(0, 18));
            Forms.answer(b, slot, null);
            h.assertTrue(chest(b) != null && chest(b).title().contains("사기 확인"), "confirm " + title(b));
            Forms.answer(b, MarketWindow.OK, null);
            h.assertTrue(chest(b) != null && chest(b).body().contains("샀습니다") && count(b, Items.GOLDEN_APPLE) == 2 && Wallet.get(b, Economy.Kind.GOLD) == 750,
                    "bought: " + body(b));
            h.assertTrue(Wallet.get(a, Economy.Kind.GOLD) == 1250 - Economy.fee(1250), "seller got " + Wallet.get(a, Economy.Kind.GOLD));
            Forms.answer(b, -1, null);
            h.assertTrue(MarketWindow.parsePrice("1,500 골드") == 1500 && MarketWindow.parsePrice("abc") == -1 && MarketWindow.parsePrice("") == -1, "parse price");
        } finally {
            Forms.HEADLESS.remove(a.getUUID());
            Forms.HEADLESS.remove(b.getUUID());
        }
        server.getPlayerList().remove(b);
        RpgGearTests.done(h, a);
    }

    private static FormNet.ChestPayload chest(ServerPlayer sp) {
        return Forms.last(sp) instanceof FormNet.ChestPayload c ? c : null;
    }

    private static String title(ServerPlayer sp) {
        return chest(sp) == null ? String.valueOf(Forms.last(sp)) : chest(sp).title();
    }

    private static String body(ServerPlayer sp) {
        return chest(sp) == null ? String.valueOf(Forms.last(sp)) : chest(sp).body();
    }

    // ------------------------------------------------------------ 골드 보상 · 명령어

    @GameTest(maxTicks = 20)
    public void goldRewardsAndCommands(GameTestHelper h) {
        ServerPlayer sp = player(h);
        // 메인 퀘스트: 보스 단계는 골드 (실버 ÷ 50 + 2), 반복 의뢰는 큰 것만
        int bossSteps = 0;
        for (com.google.gson.JsonObject q : MainQuests.buildMainQuests()) {
            com.google.gson.JsonObject r = q.getAsJsonObject("reward");
            int silver = r.get("emerald").getAsInt();
            int gold = r.has("gold") ? r.get("gold").getAsInt() : 0;
            boolean boss = "boss".equals(q.get("type").getAsString());
            h.assertTrue(gold == Economy.mainGold(silver, boss), q.get("id").getAsString() + " gold " + gold);
            if (boss) bossSteps++;
        }
        h.assertTrue(bossSteps > 0, "boss steps " + bossSteps);
        for (com.google.gson.JsonObject q : SideQuests.buildSideQuests()) {
            if (!q.has("repeat") || !q.get("repeat").getAsBoolean()) continue;
            com.google.gson.JsonObject r = q.getAsJsonObject("reward");
            int gold = r.has("gold") ? r.get("gold").getAsInt() : 0;
            h.assertTrue(gold == Economy.repeatGold(r.get("emerald").getAsInt()), "repeat " + q.get("id").getAsString() + " gold " + gold);
        }
        // 보상 주기: 실버 · 골드는 지갑으로 (아이템 묶음에 없음)
        Quest.Reward rw = new Quest.Reward();
        rw.emerald = 30;
        rw.gold = 3;
        h.assertTrue(QuestRewards.rewardStacks(rw).isEmpty(), "no currency stacks");
        QuestRewards.giveRewards(sp, rw);
        h.assertTrue(Wallet.get(sp, Economy.Kind.SILVER) == 30 && Wallet.get(sp, Economy.Kind.GOLD) == 3, "reward → wallet");
        Quest builtin = Quests.getQuest(MainQuests.mainId("sword", 5));
        h.assertTrue(builtin == null || builtin.reward.gold == Economy.mainGold(builtin.reward.emerald, "boss".equals(builtin.type)), "parsed gold");
        // 명령어
        var commands = h.getLevel().getServer().getCommands();
        var root = commands.getDispatcher().getRoot();
        for (String c : List.of("골드", "실버", "gold", "silver", "지갑", "wallet", "환전", "exchange", "장터", "market", "쓰레기통", "trash")) {
            h.assertTrue(root.getChild(c) != null, "command " + c);
        }
        commands.performPrefixedCommand(sp.createCommandSourceStack(), "골드 2");
        h.assertTrue(Wallet.get(sp, Economy.Kind.GOLD) == 1 && count(sp, EconomyItems.GOLD_COIN) == 2, "/골드 2 → 2 gold coins");
        commands.performPrefixedCommand(sp.createCommandSourceStack(), "골드 넣기");
        h.assertTrue(Wallet.get(sp, Economy.Kind.GOLD) == 3 && count(sp, EconomyItems.GOLD_COIN) == 0, "/골드 넣기");
        commands.performPrefixedCommand(sp.createCommandSourceStack(), "실버 10");
        h.assertTrue(Wallet.get(sp, Economy.Kind.SILVER) == 20 && count(sp, EconomyItems.SILVER_COIN) == 10, "/실버 10");
        commands.performPrefixedCommand(sp.createCommandSourceStack(), "지갑 주기 @s 골드 7");
        h.assertTrue(Wallet.get(sp, Economy.Kind.GOLD) == 3, "admin give needs permission");
        commands.performPrefixedCommand(sp.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER), "지갑 주기 @s 골드 7");
        commands.performPrefixedCommand(sp.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER), "wallet set @s silver 5");
        h.assertTrue(Wallet.get(sp, Economy.Kind.GOLD) == 10 && Wallet.get(sp, Economy.Kind.SILVER) == 5, "admin give / set");
        sp.getInventory().setItem(7, new ItemStack(Items.EMERALD, 4));
        commands.performPrefixedCommand(sp.createCommandSourceStack(), "환전");
        h.assertTrue(Wallet.get(sp, Economy.Kind.SILVER) == 9 && count(sp, Items.EMERALD) == 0, "/환전");
        RpgGearTests.done(h, sp);
    }

    /** 지갑 · 장터 설정 (rules_more ECONOMY) — core 시험과 같은 값을 서버에서도 */
    @GameTest(maxTicks = 20)
    public void economyConfig(GameTestHelper h) {
        h.assertTrue(Economy.fee(1000) == 50 && Economy.marketMax() == 10 && Economy.marketMillis() == 72L * 3_600_000L, "market rules");
        Map<String, Integer> boss = Map.of("scarecrow_king", 1, "surtr", 6);
        for (Map.Entry<String, Integer> e : boss.entrySet()) h.assertTrue(Economy.bossGold(e.getKey())[0] == e.getValue(), "boss gold " + e.getKey());
        h.assertTrue(mmorpg.fabric.ui.DialogForm.SHOP.item().is(EconomyItems.SILVER_COIN), "shop choice icon = silver coin");
        h.succeed();
    }
}
