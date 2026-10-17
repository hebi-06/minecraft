package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mmorpg.core.Gacha;
import mmorpg.core.Json;
import mmorpg.core.Runes;
import mmorpg.fabric.Props;
import mmorpg.fabric.RpgNet;
import mmorpg.fabric.gacha.GachaSystem;
import mmorpg.fabric.rune.RuneData;
import mmorpg.fabric.rune.RuneGacha;
import mmorpg.fabric.rune.RuneItem;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.rune.RuneSmith;
import mmorpg.fabric.rune.TicketItem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 0.14.0 게임 시험 (서버) — 룬 뽑기: 한정 룬 뽑기권 (등록 · 그림 · 뽑기권 → 뽑기 키), 뽑기 종류 (일반 — 천장 · 고급 · 스탯), 1회 · 10회
 * (뽑기권을 내고 룬을 바로 줌 · 10회는 영웅 이상 하나 · 모자라면 · 횟수 · 가방이 차면 아무것도 바뀌지 않음), 천장 (90회째 초월 · 영웅 확정 · 기록 · 최근 100 ·
 * NEW), 한정 뽑기 (관리자 저장 · 검사 · 관리자만 · 확천장 · 반천장 기록 · 고치기 · 룬술사 판매 · 끝내기 · 명령어), 모드가 없는 클라이언트 (뽑기권 우클릭 →
 * 예전 눈앞 마법진 — 같은 천장), 스탯 창 단추. 한정 뽑기 설정은 세계에 하나라 한 시험 안에서 차례로 (끝에 지움).
 */
public final class RpgGachaTests {
    static final String ENV = "mmorpg:gacha";

    static ServerPlayer player(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        GachaSystem.reset(sp);
        sp.getInventory().clearContent();
        RuneData.setPending(sp, List.of());
        return sp;
    }

    static void give(ServerPlayer sp, Item item, int n) {
        for (int left = n; left > 0; left -= 64) sp.getInventory().add(new ItemStack(item, Math.min(64, left)));
    }

    static List<Runes.Rune> runes(ServerPlayer sp) {
        List<Runes.Rune> out = new ArrayList<>();
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            ItemStack s = sp.getInventory().getItem(i);
            if (s.getItem() instanceof RuneItem ri) for (int k = 0; k < s.getCount(); k++) out.add(ri.rune());
        }
        return out;
    }

    /** 가방의 룬을 모두 치움 (뽑기권은 그대로) */
    static void clearRunes(ServerPlayer sp) {
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            if (sp.getInventory().getItem(i).getItem() instanceof RuneItem) sp.getInventory().setItem(i, ItemStack.EMPTY);
        }
    }

    static boolean res(String path) {
        return RpgGachaTests.class.getResource(path) != null;
    }

    @GameTest(environment = ENV, maxTicks = 20)
    public void ticketsAndBanners(GameTestHelper h) {
        TicketItem lim = RuneItems.LIMITED_TICKET;
        h.assertTrue(lim != null && lim.limited() && !lim.advanced() && lim.stat() == null, "limited ticket registered");
        h.assertTrue(BuiltInRegistries.ITEM.getKey(lim).toString().equals(Gacha.ticket().str("item")), "limited ticket id " + BuiltInRegistries.ITEM.getKey(lim));
        h.assertTrue(res("/assets/rpg/items/limited_rune_ticket.json") && res("/assets/rpg/models/item/limited_rune_ticket.json")
                && res("/assets/rpg/textures/item/rune/limited_rune_ticket.png"), "limited ticket assets");
        for (String s : new String[] {"glow", "rays", "ring", "star", "card", "card_back", "shine", "banner_limited", "banner_normal", "circle_0", "circle_9"}) {
            h.assertTrue(res("/assets/mmorpg/textures/gui/sprites/gacha/" + s + ".png"), "gacha sprite " + s);
        }
        h.assertTrue(RuneItems.ticketById(Gacha.ticket().str("item"), 3).getCount() == 3, "ticketById limited");
        h.assertTrue(GachaSystem.keyOf(RuneItems.TICKET).equals("normal") && GachaSystem.keyOf(RuneItems.ADV_TICKET).equals("advanced")
                && GachaSystem.keyOf(lim).equals("limited") && GachaSystem.keyOf(RuneItems.statTicket("str")).equals("stat_str"), "ticket → banner key");
        MinecraftServer server = h.getLevel().getServer();
        List<String> keys = new ArrayList<>();
        for (GachaSystem.Def d : GachaSystem.defs(server)) if (!d.key().equals("limited")) keys.add(d.key());
        h.assertTrue(keys.subList(0, 2).equals(List.of("normal", "advanced")) && keys.contains("stat_str") && keys.contains("stat_int"), "banners " + keys);
        GachaSystem.Def normal = GachaSystem.def(server, "normal");
        h.assertTrue(normal.pool().equals("normal") && normal.banner().pity().hard() == 90 && normal.banner().pity().heroEvery() == 10, "normal: pity 90 · 10");
        h.assertTrue(GachaSystem.def(server, "advanced").pool() == null && GachaSystem.def(server, "stat_str").banner().families().equals(List.of("str")), "no pity · one family");
        h.succeed();
    }

    @GameTest(environment = ENV, maxTicks = 40)
    public void pullOneAndTen(GameTestHelper h) {
        ServerPlayer sp = player(h);
        give(sp, RuneItems.TICKET, 11);
        GachaSystem.Drawn d = GachaSystem.draw(sp, "normal", 10, GachaSystem.Deliver.NOW);
        h.assertTrue(d.error() == null && d.pulls().size() == 10, "10 pulls " + d.error());
        h.assertTrue(GachaSystem.tickets(sp, RuneItems.TICKET) == 1 && runes(sp).size() == 10, "10 tickets paid · 10 runes " + runes(sp).size());
        h.assertTrue(d.pulls().stream().anyMatch(p -> p.grade().equals("hero") || p.grade().equals("transcend")), "hero or better within 10 " + d.pulls());
        GachaSystem.State s = GachaSystem.state(sp);
        h.assertTrue(s.totals().get("normal") == 10 && s.history().size() == 10, "totals · history " + s.totals());
        Set<String> ids = new HashSet<>();
        for (Gacha.Pull p : d.pulls()) ids.add(p.id());
        long fresh = d.fresh().stream().filter(b -> b).count();
        h.assertTrue(fresh == ids.size() && s.seen().equals(ids), "NEW = first time each rune (" + fresh + " / " + ids.size() + ")");
        // 모자람 · 횟수 · 아무것도 바뀌지 않음
        GachaSystem.Drawn none = GachaSystem.draw(sp, "normal", 10, GachaSystem.Deliver.NOW);
        h.assertTrue(none.error() != null && none.error().contains("모자랍니다") && GachaSystem.tickets(sp, RuneItems.TICKET) == 1 && runes(sp).size() == 10,
                "not enough tickets " + none.error());
        h.assertTrue(GachaSystem.draw(sp, "normal", 5, GachaSystem.Deliver.NOW).error() != null, "only 1 or 10");
        h.assertTrue(GachaSystem.draw(sp, "nope", 1, GachaSystem.Deliver.NOW).error() != null, "unknown banner");
        GachaSystem.Drawn one = GachaSystem.draw(sp, "normal", 1, GachaSystem.Deliver.NOW);
        h.assertTrue(one.error() == null && GachaSystem.tickets(sp, RuneItems.TICKET) == 0 && runes(sp).size() == 11
                && GachaSystem.state(sp).totals().get("normal") == 11, "1 pull");
        // 가방이 차면: 뽑지 않음
        sp.getInventory().clearContent();
        for (int i = 0; i < 35; i++) sp.getInventory().setItem(i, new ItemStack(Items.DIRT));
        sp.getInventory().setItem(35, new ItemStack(RuneItems.TICKET, 10));
        GachaSystem.Drawn full = GachaSystem.draw(sp, "normal", 10, GachaSystem.Deliver.NOW);
        h.assertTrue(full.error() != null && full.error().contains("빈 칸") && GachaSystem.tickets(sp, RuneItems.TICKET) == 10, "bag full refused " + full.error());
        h.assertTrue(GachaSystem.draw(sp, "normal", 1, GachaSystem.Deliver.NOW).error() != null, "single also needs a free slot");
        sp.getInventory().setItem(0, ItemStack.EMPTY);
        h.assertTrue(GachaSystem.draw(sp, "normal", 1, GachaSystem.Deliver.NOW).error() == null && GachaSystem.tickets(sp, RuneItems.TICKET) == 9, "one free slot → 1 pull");
        RpgGearTests.done(h, sp);
    }

    @GameTest(environment = ENV, maxTicks = 40)
    public void pityAndHistory(GameTestHelper h) {
        ServerPlayer sp = player(h);
        give(sp, RuneItems.TICKET, 130);
        // 90회째 초월 확정
        GachaSystem.setCounter(sp, "normal", new Gacha.Counter(89, 3, false, false));
        GachaSystem.Drawn top = GachaSystem.draw(sp, "normal", 1, GachaSystem.Deliver.NOW);
        h.assertTrue(top.error() == null && top.pulls().get(0).grade().equals("transcend") && top.pulls().get(0).at() == 90, "hard pity → transcend " + top.pulls());
        Gacha.Counter c = GachaSystem.counter(sp, "normal");
        h.assertTrue(c.n5() == 0 && c.n4() == 4, "counter after transcend " + c);
        GachaSystem.State st = GachaSystem.state(sp);
        GachaSystem.Entry last = st.history().get(st.history().size() - 1);
        h.assertTrue(last.at() == 90 && last.banner().equals("normal") && last.rune().equals(top.pulls().get(0).id()), "history entry " + last);
        // 영웅 확정 (10회째 — 초월이 먼저일 수 있음)
        GachaSystem.setCounter(sp, "normal", new Gacha.Counter(5, 9, false, false));
        GachaSystem.Drawn hero = GachaSystem.draw(sp, "normal", 1, GachaSystem.Deliver.NOW);
        String g = hero.pulls().get(0).grade();
        h.assertTrue(g.equals("hero") || g.equals("transcend"), "hero guaranteed at 10 " + g);
        // 최근 기록은 100개까지 (가방이 차지 않게 룬은 치우며)
        for (int i = 0; i < 11; i++) {
            clearRunes(sp);
            GachaSystem.Drawn d = GachaSystem.draw(sp, "normal", 10, GachaSystem.Deliver.NOW);
            h.assertTrue(d.error() == null, "draw " + i + " " + d.error());
        }
        sp.getInventory().clearContent();
        st = GachaSystem.state(sp);
        h.assertTrue(st.history().size() == Gacha.historyMax() && st.totals().get("normal") == 112, "history capped " + st.history().size() + " · " + st.totals());
        // 고급: 일반 룬 없음 · 천장 기록 없음
        give(sp, RuneItems.ADV_TICKET, 10);
        Gacha.Counter before = GachaSystem.counter(sp, "normal");
        GachaSystem.Drawn adv = GachaSystem.draw(sp, "advanced", 10, GachaSystem.Deliver.NOW);
        h.assertTrue(adv.error() == null && adv.pulls().stream().noneMatch(p -> p.grade().equals("common")), "advanced: no commons " + adv.pulls());
        h.assertTrue(GachaSystem.counter(sp, "normal").equals(before) && !GachaSystem.state(sp).pools().containsKey("advanced"), "advanced: no pity record");
        // 스탯 룬 뽑기권: 그 계열만
        sp.getInventory().clearContent();
        give(sp, RuneItems.statTicket("str"), 10);
        GachaSystem.Drawn str = GachaSystem.draw(sp, "stat_str", 10, GachaSystem.Deliver.NOW);
        h.assertTrue(str.error() == null && str.pulls().stream().allMatch(p -> p.family().equals("str")) && GachaSystem.state(sp).totals().get("stat") == 10,
                "stat ticket: str family only " + str.pulls());
        RpgGearTests.done(h, sp);
    }

    @GameTest(environment = ENV, maxTicks = 60)
    public void limitedBanner(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Props.setWorld(server, GachaSystem.LIMITED_DATA, null);
        ServerPlayer sp = player(h);
        h.assertTrue(GachaSystem.activeLimited(server) == null && GachaSystem.def(server, "limited") == null, "no limited at first");
        give(sp, RuneItems.LIMITED_TICKET, 12);
        h.assertTrue(GachaSystem.draw(sp, "limited", 1, GachaSystem.Deliver.NOW).error().contains("한정 뽑기가 없습니다"), "no banner → refused");
        // 저장 (관리자 창이 보내는 모양)
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "시험 축제");
        m.put("featured5", List.of("spirit"));
        m.put("featured4", List.of("axe", "bow", "holy"));
        m.put("days", 1.0);
        m.put("price", 15.0);
        h.assertTrue(GachaSystem.saveLimited(server, m, null) == null, "save");
        Gacha.Spec spec = GachaSystem.activeLimited(server);
        h.assertTrue(spec != null && spec.name().equals("시험 축제") && spec.featured5().equals(List.of("spirit")) && spec.hard() == 90 && spec.share() == 50
                && spec.end() - spec.start() == 86_400_000L, "active limited " + spec);
        h.assertTrue(GachaSystem.defs(server).get(0).key().equals("limited") && GachaSystem.def(server, "limited").pool().equals("limited"), "limited tab first");
        // 틀린 설정 · 관리자만
        Map<String, Object> bad = new LinkedHashMap<>(m);
        bad.put("featured5", List.of());
        String err = GachaSystem.saveLimited(server, bad, null);
        h.assertTrue(err != null && err.contains("픽업 초월") && GachaSystem.activeLimited(server).featured5().equals(List.of("spirit")), "invalid refused " + err);
        if (sp.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) server.getPlayerList().deop(sp.nameAndId());
        h.assertTrue(GachaSystem.saveLimited(sp, Json.stringify(m)).contains("관리자만"), "players cannot save");
        // 확천장: 다음 초월은 픽업
        GachaSystem.setCounter(sp, "limited", new Gacha.Counter(89, 0, true, false));
        GachaSystem.Drawn sure = GachaSystem.draw(sp, "limited", 1, GachaSystem.Deliver.NOW);
        Gacha.Pull p0 = sure.pulls().get(0);
        h.assertTrue(p0.grade().equals("transcend") && p0.featured() && p0.family().equals("spirit") && !GachaSystem.counter(sp, "limited").g5(),
                "guaranteed featured transcend " + p0);
        // 반천장: 결과와 기록이 맞음 (놓치면 확정이 생기고 픽업이 아닌 계열)
        GachaSystem.setCounter(sp, "limited", new Gacha.Counter(89, 0, false, false));
        Gacha.Pull p1 = GachaSystem.draw(sp, "limited", 1, GachaSystem.Deliver.NOW).pulls().get(0);
        Gacha.Counter after = GachaSystem.counter(sp, "limited");
        h.assertTrue(p1.grade().equals("transcend") && p1.featured() == !after.g5() && p1.lost() == after.g5() && (p1.featured() == p1.family().equals("spirit")),
                "50/50 consistent " + p1 + " " + after);
        // 10회 (일반과 따로 셈)
        Gacha.Counter normal = GachaSystem.counter(sp, "normal");
        GachaSystem.Drawn ten = GachaSystem.draw(sp, "limited", 10, GachaSystem.Deliver.NOW);
        h.assertTrue(ten.error() == null && GachaSystem.tickets(sp, RuneItems.LIMITED_TICKET) == 0 && GachaSystem.counter(sp, "normal").equals(normal),
                "limited 10 · normal pity untouched " + ten.error());
        // 고치기: 같은 id 면 시작 그대로
        Map<String, Object> edit = new LinkedHashMap<>(m);
        edit.put("id", spec.id());
        edit.put("name", "시험 축제 2");
        edit.put("days", 2.0);
        h.assertTrue(GachaSystem.saveLimited(server, edit, null) == null, "edit");
        Gacha.Spec edited = GachaSystem.activeLimited(server);
        h.assertTrue(edited.id().equals(spec.id()) && edited.start() == spec.start() && edited.name().equals("시험 축제 2")
                && edited.end() - edited.start() == 2 * 86_400_000L, "edit keeps id · start " + edited);
        // 룬술사: 한정 룬 뽑기권 (값 15)
        RuneSmith.TicketInfo info = RuneSmith.limitedInfo(server);
        h.assertTrue(info != null && info.price() == 15, "runesmith sells limited tickets " + info);
        give(sp, RuneSmith.shardItem(), 31);
        String bought = RuneSmith.buyLimited(sp, 2);
        h.assertTrue(bought.contains("구매") && GachaSystem.tickets(sp, RuneItems.LIMITED_TICKET) == 2 && RpgGearTests.countIn(sp, RuneSmith.shardItem()) == 1,
                "bought 2 " + bought);
        // 끝내기 → 뽑기권은 남음 · 뽑을 수 없음 · 룬술사도 팔지 않음
        h.assertTrue(GachaSystem.closeLimited(server) == null && GachaSystem.activeLimited(server) == null && GachaSystem.limitedSpec(server) != null, "closed");
        h.assertTrue(GachaSystem.tickets(sp, RuneItems.LIMITED_TICKET) == 2 && GachaSystem.draw(sp, "limited", 1, GachaSystem.Deliver.NOW).error() != null
                && RuneSmith.limitedInfo(server) == null, "after close");
        // 명령어 (운영자): 열기 · 보기 · 끝
        var commands = server.getCommands();
        var src = sp.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER);
        commands.performPrefixedCommand(src, "뽑기 한정 열기 \"명령 축제\" spirit axe+bow 3");
        Gacha.Spec cmd = GachaSystem.activeLimited(server);
        h.assertTrue(cmd != null && cmd.name().equals("명령 축제") && cmd.featured4().equals(List.of("axe", "bow")) && cmd.end() - cmd.start() == 3 * 86_400_000L,
                "command start " + cmd);
        commands.performPrefixedCommand(src, "gacha limited close");
        h.assertTrue(GachaSystem.activeLimited(server) == null, "command close");
        commands.performPrefixedCommand(src, "gacha ticket 3 @s");
        h.assertTrue(GachaSystem.tickets(sp, RuneItems.LIMITED_TICKET) == 5, "command ticket give " + GachaSystem.tickets(sp, RuneItems.LIMITED_TICKET));
        Props.setWorld(server, GachaSystem.LIMITED_DATA, null);
        RpgGearTests.done(h, sp);
    }

    @GameTest(environment = ENV, maxTicks = 60)
    public void fallbackAndHooks(GameTestHelper h) {
        ServerPlayer sp = player(h);
        give(sp, RuneItems.TICKET, 2);
        // 모드가 없는 클라이언트 (가짜 플레이어): 우클릭 → 예전 눈앞 마법진 (적어 두고 연출 뒤) — 같은 천장 기록
        GachaSystem.useTicketItem(sp, RuneItems.TICKET);
        h.assertTrue(RuneGacha.drawing(sp) && RuneData.pending(sp).size() == 1 && GachaSystem.tickets(sp, RuneItems.TICKET) == 1, "fallback draw");
        GachaSystem.State s = GachaSystem.state(sp);
        Gacha.Counter c = GachaSystem.counter(sp, "normal");
        h.assertTrue(s.totals().get("normal") == 1 && s.history().size() == 1 && !c.equals(Gacha.Counter.ZERO), "fallback recorded " + s.totals() + " " + c);
        h.runAfterDelay(RuneGacha.DRAW_TICKS + 2, () -> {
            h.assertTrue(!RuneGacha.drawing(sp) && RuneData.pending(sp).isEmpty() && runes(sp).size() == 1, "delivered after the animation");
            h.assertTrue(RpgNet.statAction(sp, "gacha"), "stat window button");
            var root = h.getLevel().getServer().getCommands().getDispatcher().getRoot();
            h.assertTrue(root.getChild("뽑기") != null && root.getChild("gacha") != null, "commands");
            for (String sub : new String[] {"한정", "뽑기권", "천장", "초기화"}) h.assertTrue(root.getChild("뽑기").getChild(sub) != null, "/뽑기 " + sub);
            RpgGearTests.done(h, sp);
        });
    }
}
