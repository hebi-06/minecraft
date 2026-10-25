package mmorpg.fabric.test;

import java.util.List;
import mmorpg.core.Armor;
import mmorpg.core.Economy;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.rune.Breakthrough;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.rune.RuneSmith;
import mmorpg.fabric.rune.RuneSmithUi;
import mmorpg.fabric.shop.ArmorShop;
import mmorpg.fabric.shop.ShopUi;
import mmorpg.fabric.shop.Store;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.smith.SmithNet;
import mmorpg.fabric.smith.Workshop;
import mmorpg.fabric.ui.Forms;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 0.45.0 룬술사 · 상인 전용 창 (서버 — 클라이언트 대신 Workshop.FORCE: 보낸 패킷은 Workshop.SENT 에): 대장장이 창과 같은 흐름 (칸 → 동작 · WorkPayload →
 * 끝에 판정 · 결과 · 바뀐 목록, 모자라면 거절 알림).
 * <ul>
 *   <li>룬술사: 갈기 (겹친 룬 모두 갈기 · 하나뿐인 룬은 두 번 누름 — confirm, 룬은 끝에 가져감), 돌파 · 모두 돌파, 뽑기권 (1장 · 5장 — 모자라면 거절)</li>
 *   <li>상인: 무기 (쪽 · 사기 — 실버는 끝에), 갑옷 (부위 · 한 벌 — 모자라면 거절), 전리품 팔기 (개수 고르기 · 모두 팔기)</li>
 *   <li>세상: 룬술사는 둘레 마법 부여대 쪽 (없으면 옆으로 돌아섬), 상인은 사는 사람 쪽을 봄</li>
 * </ul>
 */
public final class RpgWorkshopUiTests {
    private static final Obj RUNE_SMITH = Rules.get().obj("RUNE_SMITH");

    private static ServerPlayer forced(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        Workshop.FORCE.add(sp.getUUID());
        Workshop.SENT.remove(sp.getUUID());
        return sp;
    }

    private static void finish(GameTestHelper h, ServerPlayer sp) {
        Workshop.action(sp, "close", "", 0, 0);
        Workshop.FORCE.remove(sp.getUUID());
        Workshop.SENT.remove(sp.getUUID());
        RpgGearTests.done(h, sp);
    }

    private static List<CustomPacketPayload> sent(ServerPlayer sp) {
        return Workshop.SENT.getOrDefault(sp.getUUID(), List.of());
    }

    private static SmithNet.ViewPayload lastView(ServerPlayer sp) {
        List<CustomPacketPayload> l = sent(sp);
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i) instanceof SmithNet.ViewPayload v) return v;
        return null;
    }

    private static SmithNet.WorkPayload lastWork(ServerPlayer sp) {
        List<CustomPacketPayload> l = sent(sp);
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i) instanceof SmithNet.WorkPayload w) return w;
        return null;
    }

    private static int works(ServerPlayer sp) {
        int n = 0;
        for (CustomPacketPayload p : sent(sp)) if (p instanceof SmithNet.WorkPayload) n++;
        return n;
    }

    private static SmithNet.Entry entry(SmithNet.ViewPayload v, String key) {
        if (v == null) return null;
        for (SmithNet.Entry e : v.entries()) if (e.key().equals(key)) return e;
        return null;
    }

    private static int grind(String grade) {
        return (int) RUNE_SMITH.obj("grind").num(grade);
    }

    private static Item rune(String family, String grade) {
        return RuneItems.stack(family, grade).getItem();
    }

    // ------------------------------------------------------------ 룬술사

    /**
     * 갈기: 룬술사 창 (탭 셋 · 상자 창 없음) · 겹친 룬 모두 갈기 칸 (맨 위 · 두 번 누름) · 하나뿐인 룬 (두 번 누름 · 알림) · 겹친 룬 (한 번) → 동작
     * (rune_channel · 룬은 끝에 가져감) → 조각 · 결과 · 바뀐 목록 → 닫으면 done
     */
    @GameTest(maxTicks = 100)
    public void runeGrindInWindow(GameTestHelper h) {
        ServerPlayer sp = forced(h);
        sp.getInventory().setItem(3, RuneItems.stack("dex", "noble"));
        sp.getInventory().setItem(20, RuneItems.stack("dex", "noble"));
        sp.getInventory().setItem(25, RuneItems.stack("int", "common"));
        boolean[] closed = {false};
        RuneSmith.runesmithLoop(sp, null, "grind", () -> closed[0] = true);
        SmithNet.ViewPayload v = lastView(sp);
        h.assertTrue(v != null && v.open() && v.kind().equals("runesmith") && v.role().equals("룬술사") && v.tab().equals(RuneSmithUi.GRIND)
                && v.tabs().equals(List.of(RuneSmithUi.GRIND, RuneSmithUi.BREAK, RuneSmithUi.TICKETS)) && v.tabNames().equals(List.of("룬 갈기", "돌파", "뽑기권")),
                "rune smith window " + v);
        h.assertTrue(Forms.last(sp) == null, "no chest window for a modded client");
        SmithNet.Entry dup = entry(v, "dup");
        h.assertTrue(dup != null && dup.can() && dup.badge().equals("+" + grind("noble")) && !dup.confirm().isEmpty() && v.entries().get(0) == dup,
                "duplicates row on top " + dup);
        SmithNet.Entry only = entry(v, "r:25:rpg:rune_int_common:0");
        h.assertTrue(only != null && !only.confirm().isEmpty() && only.note().contains("하나뿐"), "unique rune needs two presses " + only);
        String bagKey = "r:20:rpg:rune_dex_noble:0";
        SmithNet.Entry bag = entry(v, bagKey);
        h.assertTrue(bag != null && bag.confirm().isEmpty() && bag.from().contains("+" + grind("noble")), "duplicate rune row " + bag);
        h.assertTrue(v.res().size() == 3 && v.res().get(0).count() == 0, "resources: shards 0");
        Workshop.action(sp, "do", bagKey, 0, 0);
        SmithNet.WorkPayload work = lastWork(sp);
        h.assertTrue(work != null && work.motion().equals(RuneSmithUi.GRIND_WORK.motion()) && work.fx().equals("grind") && work.ticks() == RuneSmithUi.GRIND_WORK.ticks()
                && work.impacts().equals(RuneSmithUi.GRIND_WORK.impacts()) && work.item().is(rune("dex", "noble")) && work.npc() == -1, "work " + work);
        h.assertTrue(Workshop.busy(sp) && lastView(sp).busy() && RuneItems.runeOf(sp.getInventory().getItem(20)) != null, "busy · rune taken only at the end");
        int n = works(sp);
        Workshop.action(sp, "do", bagKey, 0, 0);
        Workshop.action(sp, "tab", RuneSmithUi.BREAK, 0, 0);
        h.assertTrue(works(sp) == n && RuneSmithUi.GRIND.equals(Workshop.testTab(sp)), "ignored while working");
        h.runAfterDelay(RuneSmithUi.GRIND_WORK.ticks() + 2, () -> {
            SmithNet.Result r = Workshop.testResult(sp);
            h.assertTrue(r.seq() == 1 && r.kind().equals("done") && r.text().contains("+" + grind("noble")), "result " + r);
            h.assertTrue(sp.getInventory().getItem(20).isEmpty() && RuneItems.runeOf(sp.getInventory().getItem(3)) != null, "bag copy ground · hotbar kept");
            h.assertTrue(RpgGearTests.countIn(sp, RuneSmith.shardItem()) == grind("noble"), "shards " + RpgGearTests.countIn(sp, RuneSmith.shardItem()));
            SmithNet.ViewPayload after = lastView(sp);
            h.assertTrue(!after.busy() && entry(after, "dup") == null && entry(after, bagKey) == null && after.res().get(0).count() == grind("noble"), "list updated");
            Workshop.action(sp, "close", "", 0, 0);
            h.assertTrue(closed[0] && !Workshop.isOpen(sp), "close runs done");
            finish(h, sp);
        });
    }

    /** 돌파: 같은 룬 셋 (0 · 0 · 0) → 칸 하나 (0 → 1돌 · rune_merge) → 모두 돌파 (1 + 0 → 2돌) — 재료는 끝에 */
    @GameTest(maxTicks = 120)
    public void runeBreakInWindow(GameTestHelper h) {
        ServerPlayer sp = forced(h);
        for (int slot : new int[] {9, 10, 11}) sp.getInventory().setItem(slot, Breakthrough.stack("vit", "common", 0));
        RuneSmith.runesmithLoop(sp, null, "break", null);
        SmithNet.ViewPayload v = lastView(sp);
        h.assertTrue(v != null && v.tab().equals(RuneSmithUi.BREAK), "break tab");
        SmithNet.Entry all = entry(v, "ball");
        SmithNet.Entry one = v.entries().stream().filter(e -> e.key().startsWith("b:")).findFirst().orElse(null);
        h.assertTrue(all != null && !all.confirm().isEmpty() && one != null && one.from().equals("0돌") && one.to().equals("1돌") && one.action().equals("돌파"),
                "merge rows " + v.entries());
        Workshop.action(sp, "do", one.key(), 0, 0);
        SmithNet.WorkPayload work = lastWork(sp);
        h.assertTrue(work != null && work.motion().equals(RuneSmithUi.MERGE_WORK.motion()) && work.fx().equals("merge") && work.ticks() == RuneSmithUi.MERGE_WORK.ticks(),
                "merge work " + work);
        h.assertTrue(RpgGearTests.countIn(sp, rune("vit", "common")) == 3, "material taken only at the end");
        h.runAfterDelay(RuneSmithUi.MERGE_WORK.ticks() + 2, () -> {
            SmithNet.Result r = Workshop.testResult(sp);
            h.assertTrue(r.kind().equals("success") && Breakthrough.of(r.item()) == 1, "merged to 1 " + r);
            h.assertTrue(RpgGearTests.countIn(sp, rune("vit", "common")) == 2, "two runes left");
            Workshop.action(sp, "do", "ball", 0, 0);
            h.assertTrue(lastWork(sp) != null && lastWork(sp).key().equals("ball"), "break all started");
            h.runAfterDelay(RuneSmithUi.MERGE_WORK.ticks() + 2, () -> {
                SmithNet.Result r2 = Workshop.testResult(sp);
                int level = -1;
                for (int i = 0; i < 36; i++) if (sp.getInventory().getItem(i).is(rune("vit", "common"))) level = Breakthrough.of(sp.getInventory().getItem(i));
                h.assertTrue(r2.kind().equals("success") && RpgGearTests.countIn(sp, rune("vit", "common")) == 1 && level == 2, "all merged to 2 (" + level + ") " + r2);
                h.assertTrue(entry(lastView(sp), "ball") == null, "nothing left to merge");
                finish(h, sp);
            });
        });
    }

    /** 뽑기권: 조각 25 → 룬 뽑기권 (1장 됨 · 5장 모자람 → 거절) · 고급 (40 — 모자람: 흐리게) → 1장 사기 (ticket) */
    @GameTest(maxTicks = 60)
    public void runeTicketsInWindow(GameTestHelper h) {
        ServerPlayer sp = forced(h);
        int price = (int) RUNE_SMITH.obj("price").num("ticket");
        sp.getInventory().setItem(5, RuneSmith.createShardItem(25));
        RuneSmith.runesmithLoop(sp, null, "tickets", null);
        SmithNet.ViewPayload v = lastView(sp);
        SmithNet.Entry t = entry(v, "t:n");
        SmithNet.Entry adv = entry(v, "t:a");
        h.assertTrue(v.tab().equals(RuneSmithUi.TICKETS) && t != null && t.variants().size() == 2 && t.variants().get(0).can() && !t.variants().get(1).can()
                && t.variants().get(1).note().contains("더 필요") && t.variants().get(0).costs().get(0).need() == price, "ticket row " + t);
        h.assertTrue(adv != null && !adv.can() && adv.state() == 1, "advanced ticket too expensive " + adv);
        Workshop.action(sp, "do", "t:n", 1, 0);
        h.assertTrue(lastWork(sp) == null && Workshop.testMessage(sp).contains("더 필요"), "5 tickets refused: " + Workshop.testMessage(sp));
        Workshop.action(sp, "do", "t:n", 0, 0);
        SmithNet.WorkPayload work = lastWork(sp);
        h.assertTrue(work != null && work.fx().equals("ticket") && work.item().is(RuneItems.TICKET), "ticket work " + work);
        h.runAfterDelay(RuneSmithUi.TICKET_WORK.ticks() + 2, () -> {
            h.assertTrue(RpgGearTests.countIn(sp, RuneItems.TICKET) == 1 && RpgGearTests.countIn(sp, RuneSmith.shardItem()) == 25 - price, "bought one ticket");
            h.assertTrue(Workshop.testResult(sp).kind().equals("done"), "result done");
            finish(h, sp);
        });
    }

    // ------------------------------------------------------------ 상인

    /** 무기: 상인 창 (탭 셋 · 쪽) · 10렙 일반 칼 사기 (shop_hand — 실버는 끝에) · 새 무기 쪽 */
    @GameTest(maxTicks = 60)
    public void shopBuyWeapon(GameTestHelper h) {
        ServerPlayer sp = forced(h);
        Weapon w = Weapons.weaponFrom("sword", 10, "common");
        int price = (int) Weapons.shopPrice(w);
        Wallet.set(sp, Economy.Kind.SILVER, price + 7);
        Store.storeLoop(sp, null, 10, "weapons", null);
        SmithNet.ViewPayload v = lastView(sp);
        h.assertTrue(v != null && v.kind().equals("shop") && v.role().equals("상인") && v.tabs().equals(List.of(ShopUi.WEAPONS, ShopUi.ARMOR, ShopUi.SELL))
                && v.tab().equals(ShopUi.WEAPONS), "shop window " + v);
        h.assertTrue(Forms.last(sp) == null, "no chest window");
        boolean more = !Weapons.moreTypes().isEmpty();
        h.assertTrue(more ? v.pages().size() == 2 : v.pages().isEmpty(), "pages " + v.pages());
        SmithNet.Entry e = entry(v, "w:" + w.id());
        h.assertTrue(e != null && e.can() && e.costs().size() == 1 && e.costs().get(0).need() == price && e.costs().get(0).have() == price + 7 && !e.group().isEmpty(),
                "sword row " + e);
        Workshop.action(sp, "do", "w:" + w.id(), 0, 0);
        SmithNet.WorkPayload work = lastWork(sp);
        h.assertTrue(work != null && work.motion().equals(ShopUi.BUY_WORK.motion()) && work.fx().equals("buy") && work.ticks() == ShopUi.BUY_WORK.ticks()
                && work.item().is(RpgItems.byId(w.id())), "buy work " + work);
        h.assertTrue(RpgGearTests.silver(sp) == price + 7, "paid only at the end");
        h.runAfterDelay(ShopUi.BUY_WORK.ticks() + 2, () -> {
            h.assertTrue(RpgGearTests.silver(sp) == 7 && RpgGearTests.countIn(sp, RpgItems.byId(w.id())) == 1, "bought: silver " + RpgGearTests.silver(sp));
            SmithNet.Result r = Workshop.testResult(sp);
            h.assertTrue(r.kind().equals("done") && r.title().contains("구매"), "result " + r);
            SmithNet.Entry again = entry(lastView(sp), "w:" + w.id());
            h.assertTrue(again != null && !again.can() && again.state() == 1 && again.note().contains("더 필요"), "now short " + again);
            if (more) {
                Workshop.action(sp, "page", "", 1, 0);
                SmithNet.ViewPayload v2 = lastView(sp);
                String t = Weapons.moreTypes().get(0);
                boolean found = v2.page() == 1 && v2.entries().stream().anyMatch(x -> x.key().equals("w:" + Weapons.weaponFrom(t, 10, "common").id()));
                h.assertTrue(found, "new weapons page");
            }
            finish(h, sp);
        });
    }

    /** 갑옷: 힘 갑옷 (고를 것 다섯 — 투구는 되고 한 벌은 모자람 → 거절) → 투구 사기 */
    @GameTest(maxTicks = 60)
    public void shopBuyArmor(GameTestHelper h) {
        ServerPlayer sp = forced(h);
        int lv = ArmorShop.armorLevelFor(10);
        Armor.Piece helm = Armor.armorFrom("str", lv, "common", "helmet");
        int price = (int) Armor.armorPrice(helm);
        Wallet.set(sp, Economy.Kind.SILVER, price);
        Store.storeLoop(sp, null, 10, "armor", null);
        SmithNet.ViewPayload v = lastView(sp);
        SmithNet.Entry e = entry(v, "a:str");
        h.assertTrue(v.tab().equals(ShopUi.ARMOR) && e != null && e.variants().size() == 5 && e.variants().get(0).can() && !e.variants().get(4).can()
                && e.variants().get(4).label().equals("한 벌") && !e.variants().get(4).info().isEmpty(), "armor row " + e);
        Workshop.action(sp, "do", "a:str", 4, 0);
        h.assertTrue(lastWork(sp) == null && Workshop.testMessage(sp).contains("더 필요"), "set refused: " + Workshop.testMessage(sp));
        Workshop.action(sp, "do", "a:str", 0, 0);
        SmithNet.WorkPayload work = lastWork(sp);
        Item helmet = GearItems.byId(helm.id());
        h.assertTrue(work != null && work.fx().equals("buy") && work.item().is(helmet), "helmet work " + work);
        h.runAfterDelay(ShopUi.BUY_WORK.ticks() + 2, () -> {
            h.assertTrue(RpgGearTests.countIn(sp, helmet) == 1 && RpgGearTests.silver(sp) == 0, "helmet bought");
            finish(h, sp);
        });
    }

    /** 전리품 팔기: 트롤 12 · 닉스 3 → 고를 것 (1개 · 10개 · 모두 12 / 1개 · 모두 3) → 트롤 10개 (shop_count) → 모두 팔기 */
    @GameTest(maxTicks = 80)
    public void shopSellLoot(GameTestHelper h) {
        ServerPlayer sp = forced(h);
        Item troll = GearItems.byId("rpg:loot_troll");
        Item nix = GearItems.byId("rpg:loot_nix");
        Obj drops = Rules.get().obj("MONSTER_DROPS");
        int pt = (int) drops.obj("troll").num("price");
        int pn = (int) drops.obj("nix").num("price");
        sp.getInventory().setItem(11, new ItemStack(troll, 12));
        sp.getInventory().setItem(12, new ItemStack(nix, 3));
        Wallet.set(sp, Economy.Kind.SILVER, 0);
        Store.storeLoop(sp, null, 10, "sell", null);
        SmithNet.ViewPayload v = lastView(sp);
        SmithNet.Entry all = entry(v, "all");
        SmithNet.Entry t = entry(v, "l:troll");
        SmithNet.Entry n = entry(v, "l:nix");
        h.assertTrue(v.tab().equals(ShopUi.SELL) && all != null && v.entries().get(0) == all && all.badge().equals("+" + Economy.fmt(12L * pt + 3L * pn)), "sell all row " + all);
        h.assertTrue(t != null && t.variants().size() == 3 && t.variants().get(1).label().equals("10개") && t.variants().get(2).label().equals("모두 12"), "troll choices " + t);
        h.assertTrue(n != null && n.variants().size() == 2 && n.variants().get(1).label().equals("모두 3"), "nix choices " + n);
        Workshop.action(sp, "do", "l:troll", 1, 0);
        SmithNet.WorkPayload work = lastWork(sp);
        h.assertTrue(work != null && work.motion().equals(ShopUi.SELL_WORK.motion()) && work.fx().equals("sell") && work.item().is(troll) && work.item().getCount() == 10,
                "sell work " + work);
        h.assertTrue(RpgGearTests.countIn(sp, troll) == 12, "loot taken only at the end");
        h.runAfterDelay(ShopUi.SELL_WORK.ticks() + 2, () -> {
            h.assertTrue(RpgGearTests.countIn(sp, troll) == 2 && RpgGearTests.silver(sp) == 10 * pt, "sold 10 trolls: silver " + RpgGearTests.silver(sp));
            Workshop.action(sp, "do", "all", 0, 0);
            h.runAfterDelay(ShopUi.SELL_WORK.ticks() + 2, () -> {
                h.assertTrue(RpgGearTests.countIn(sp, troll) == 0 && RpgGearTests.countIn(sp, nix) == 0 && RpgGearTests.silver(sp) == 12 * pt + 3 * pn,
                        "sold all: silver " + RpgGearTests.silver(sp));
                h.assertTrue(entry(lastView(sp), "all") == null && Workshop.testResult(sp).title().contains(Economy.fmt(2L * pt + 3L * pn)), "nothing left · result "
                        + Workshop.testResult(sp).title());
                finish(h, sp);
            });
        });
    }

    // ------------------------------------------------------------ 세상

    /** 룬술사 NPC: 바로 옆 마법 부여대 쪽을 봄 → 치우면 옆으로 돌아섬 (90°), 상인 NPC: 사는 사람 쪽을 봄 · 다른 가게의 일 중에는 열리지 않음 */
    @GameTest(maxTicks = 160)
    public void npcFacing(GameTestHelper h) {
        ServerPlayer sp = forced(h);
        ServerLevel level = h.getLevel();
        Vec3 at = h.absoluteVec(new Vec3(2.5, 2, 2.5));
        Mannequin npc = NpcData.spawnAt(level, at, at.add(0, 0, 3)); // 남쪽 (+z) 을 봄
        BlockPos table = h.absolutePos(new BlockPos(3, 2, 2)); // 바로 동쪽
        level.setBlockAndUpdate(table, Blocks.ENCHANTING_TABLE.defaultBlockState());
        sp.getInventory().setItem(20, RuneItems.stack("str", "rare"));
        sp.getInventory().setItem(21, RuneItems.stack("str", "rare"));
        RuneSmith.runesmithLoop(sp, npc, "grind", null);
        h.assertTrue(lastView(sp).npc() == npc.getId(), "npc in the view");
        Workshop.action(sp, "do", "r:21:rpg:rune_str_rare:0", 0, 0);
        SmithNet.WorkPayload w1 = lastWork(sp);
        float want = Kit.lookRotation(npc.position(), new Vec3(table.getX() + 0.5, npc.getY(), table.getZ() + 0.5))[0];
        h.assertTrue(w1 != null && w1.npc() == npc.getId() && Math.abs(Mth.wrapDegrees(w1.yaw() - want)) < 0.5F, "faces the enchanting table " + (w1 == null ? null : w1.yaw()));
        h.assertTrue(Workshop.npcWorkTick(npc) >= 0, "npc work noted");
        // 일하는 중 다른 가게는 열리지 않음 (done 은 바로)
        boolean[] done = {false};
        Store.storeLoop(sp, npc, 10, "weapons", () -> done[0] = true);
        h.assertTrue(done[0] && "runesmith".equals(Workshop.kindOf(sp)), "other shop refused while working");
        h.runAfterDelay(RuneSmithUi.GRIND_WORK.ticks() + 3, () -> {
            level.setBlockAndUpdate(table, Blocks.AIR.defaultBlockState());
            Workshop.action(sp, "do", "r:20:rpg:rune_str_rare:0", 0, 0);
            SmithNet.WorkPayload w2 = lastWork(sp);
            h.assertTrue(w2 != null && w2 != w1 && Math.abs(Math.abs(Mth.wrapDegrees(w2.yaw() - npc.getYRot())) - 90F) < 0.5F, "no table: turns sideways " + (w2 == null ? null : w2.yaw()));
            h.runAfterDelay(RuneSmithUi.GRIND_WORK.ticks() + 3, () -> {
                Workshop.action(sp, "close", "", 0, 0);
                Wallet.set(sp, Economy.Kind.SILVER, 999_999);
                Weapon w = Weapons.weaponFrom("dagger", 10, "common");
                Store.storeLoop(sp, npc, 10, "weapons", null);
                h.assertTrue("shop".equals(Workshop.kindOf(sp)), "shop window");
                Workshop.action(sp, "do", "w:" + w.id(), 0, 0);
                SmithNet.WorkPayload w3 = lastWork(sp);
                float toBuyer = Kit.lookRotation(npc.position(), new Vec3(sp.getX(), npc.getY(), sp.getZ()))[0];
                h.assertTrue(w3 != null && w3.fx().equals("buy") && Math.abs(Mth.wrapDegrees(w3.yaw() - toBuyer)) < 0.5F, "merchant faces the buyer " + (w3 == null ? null : w3.yaw()));
                h.runAfterDelay(ShopUi.BUY_WORK.ticks() + 3, () -> {
                    npc.discard();
                    finish(h, sp);
                });
            });
        });
    }
}
