package mmorpg.fabric.test;

import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.npc.Dummy;
import mmorpg.fabric.npc.Npcs;
import mmorpg.fabric.npc.TrainingDummy;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.rune.RuneSmith;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.ui.FormNet;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.Slot;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * 4단계 게임 시험 (서버): 룬술사 (룬 하나 갈기 · 겹친 룬 모두 갈기 — 룬마다 하나 남김 · 핫바 것을 남김 · 뽑기권 사기 · 모자라면 실패) 창 흐름
 * (클라이언트 없이 — Forms.HEADLESS), 훈련용 허수아비 (큰 피해에도 안 죽음 · 기록 · 머리 위 표시 · 면역 · 제자리 · 5초 뒤 초기화 · 도구로 치우기 · /dummy clear).
 */
public final class RpgRuneSmithTests {
    private static final Obj SMITH = Rules.get().obj("RUNE_SMITH");

    private static FormNet.ChestPayload chest(ServerPlayer sp) {
        return Forms.last(sp) instanceof FormNet.ChestPayload c ? c : null;
    }

    private static int grind(String grade) {
        return (int) SMITH.obj("grind").num(grade);
    }

    private static Item rune(String family, String grade) {
        return RuneItems.stack(family, grade).getItem();
    }

    /** 룬 하나 갈기: 룬 칸 (초록 숫자 = 조각) → 확인 창 (하나뿐인 룬 경고) → 갈기 → 룬이 사라지고 조각, 창 본문에 결과 */
    @GameTest(maxTicks = 20)
    public void grindOneRune(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        Forms.HEADLESS.add(sp.getUUID());
        try {
            sp.getInventory().setItem(12, RuneItems.stack("str", "rare"));
            int v = grind("rare");
            RuneSmith.openRunesmith(sp, "grind");
            FormNet.ChestPayload c = chest(sp);
            h.assertTrue(c != null && c.title().contains("룬 갈기") && c.slots().size() == 27, "grind window");
            h.assertTrue(c.slots().get(RuneSmith.TAB).name().contains("뽑기권"), "tab to tickets");
            h.assertTrue(c.slots().get(9).kind() == Slot.PRICE && c.slots().get(9).num() == v, "rune slot price " + c.slots().get(9).num());
            h.assertTrue(c.slots().get(8).kind() == Slot.DECO, "no duplicates");
            Forms.answer(sp, 9, null);
            c = chest(sp);
            h.assertTrue(c != null && c.title().equals("룬 갈기") && c.body().contains("하나뿐인 룬"), "confirm window");
            Forms.answer(sp, 11, null);
            h.assertTrue(RpgGearTests.countIn(sp, rune("str", "rare")) == 0, "rune taken");
            h.assertTrue(RpgGearTests.countIn(sp, RuneSmith.shardItem()) == v, "shards " + RpgGearTests.countIn(sp, RuneSmith.shardItem()));
            c = chest(sp);
            h.assertTrue(c != null && c.title().contains("룬 갈기") && c.body().contains("룬 1개"), "back with message: " + (c == null ? null : c.body()));
            h.assertTrue(c.slots().get(4).num() == v, "shard count slot");
            // 칸의 룬이 바뀌었으면 취소
            String msg = RuneSmith.grindRunes(sp, java.util.List.of(new RuneSmith.Pick(12, "rpg:rune_str_rare")));
            h.assertTrue(msg.contains("옮겨져서"), "moved rune: " + msg);
            Forms.answer(sp, -1, null);
            h.assertFalse(Forms.pending(sp), "closed");
        } finally {
            Forms.HEADLESS.remove(sp.getUUID());
        }
        RpgGearTests.done(h, sp);
    }

    /** 겹친 룬 모두 갈기: 같은 룬 셋 (핫바 3 · 가방 20 · 30) + 다른 룬 하나 → 핫바의 것 하나만 남고 둘을 갊, 다른 룬은 그대로 */
    @GameTest(maxTicks = 20)
    public void grindDuplicatesKeepsOne(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        Forms.HEADLESS.add(sp.getUUID());
        try {
            for (int slot : new int[] {20, 3, 30}) sp.getInventory().setItem(slot, RuneItems.stack("dex", "noble"));
            sp.getInventory().setItem(25, RuneItems.stack("int", "common"));
            int v = grind("noble");
            java.util.List<RuneSmith.RuneSlot> dups = RuneSmith.duplicateSlots(sp);
            h.assertTrue(dups.size() == 2 && dups.stream().noneMatch(e -> e.slot() == 3), "keeps the hotbar rune: " + dups);
            java.util.List<RuneSmith.RuneSlot> all = RuneSmith.runeSlots(sp);
            h.assertTrue(all.size() == 4 && all.get(0).dup() == 3 && all.get(3).id().equals("rpg:rune_int_common"), "duplicates first: " + all);
            RuneSmith.openRunesmith(sp, "grind");
            FormNet.ChestPayload c = chest(sp);
            h.assertTrue(c != null && c.slots().get(8).kind() == Slot.PRICE && c.slots().get(8).num() == 2 * v, "dup slot price " + (c == null ? -1 : c.slots().get(8).num()));
            Forms.answer(sp, 8, null);
            c = chest(sp);
            h.assertTrue(c != null && c.title().contains("겹친 룬 모두 갈기"), "confirm duplicates");
            Forms.answer(sp, 11, null);
            h.assertTrue(RpgGearTests.countIn(sp, rune("dex", "noble")) == 1 && RuneItems.runeOf(sp.getInventory().getItem(3)) != null, "one kept in the hotbar");
            h.assertTrue(sp.getInventory().getItem(20).isEmpty() && sp.getInventory().getItem(30).isEmpty(), "bag copies ground");
            h.assertTrue(RpgGearTests.countIn(sp, rune("int", "common")) == 1, "other rune untouched");
            h.assertTrue(RpgGearTests.countIn(sp, RuneSmith.shardItem()) == 2 * v, "shards " + RpgGearTests.countIn(sp, RuneSmith.shardItem()));
            c = chest(sp);
            h.assertTrue(c != null && c.body().contains("룬 2개") && c.slots().get(8).kind() == Slot.DECO, "no more duplicates");
            Forms.answer(sp, -1, null);
        } finally {
            Forms.HEADLESS.remove(sp.getUUID());
        }
        RpgGearTests.done(h, sp);
    }

    /** 뽑기권 사기: 조각 25 → 룬 뽑기권 1장 (조각 -10), 5장 칸은 없음, 고급 (40) 은 모자라서 실패 (조각 그대로) */
    @GameTest(maxTicks = 20)
    public void buyTickets(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        Forms.HEADLESS.add(sp.getUUID());
        try {
            int price = (int) SMITH.obj("price").num("ticket");
            int adv = (int) SMITH.obj("price").num("adv");
            sp.getInventory().setItem(5, RuneSmith.createShardItem(25));
            RuneSmith.openRunesmith(sp, "tickets");
            FormNet.ChestPayload c = chest(sp);
            h.assertTrue(c != null && c.title().contains("뽑기권"), "ticket window");
            h.assertTrue(c.slots().get(11).num() == price && c.slots().get(15).num() == adv, "prices " + c.slots().get(11).num() + " / " + c.slots().get(15).num());
            h.assertTrue(BedrockText.plain(String.join(" ", c.slots().get(11).lore())).contains("일반 50%"), "odds: " + c.slots().get(11).lore());
            h.assertFalse(BedrockText.plain(String.join(" ", c.slots().get(15).lore())).contains("일반"), "advanced has no common");
            Forms.answer(sp, 11, null);
            c = chest(sp);
            h.assertTrue(c != null && c.title().contains("사기"), "buy window");
            h.assertTrue(c.slots().get(12).kind() == Slot.DECO, "no 5x when short");
            Forms.answer(sp, 11, null);
            h.assertTrue(RpgGearTests.countIn(sp, RuneItems.TICKET) == 1, "got a ticket");
            h.assertTrue(RpgGearTests.countIn(sp, RuneSmith.shardItem()) == 25 - price, "paid " + RpgGearTests.countIn(sp, RuneSmith.shardItem()));
            c = chest(sp);
            h.assertTrue(c != null && c.body().contains("구매"), "back with message");
            Forms.answer(sp, 15, null); // 고급 뽑기권 → 1장 (모자람)
            Forms.answer(sp, 11, null);
            h.assertTrue(RpgGearTests.countIn(sp, RuneItems.ADV_TICKET) == 0, "no advanced ticket");
            h.assertTrue(RpgGearTests.countIn(sp, RuneSmith.shardItem()) == 25 - price, "shards kept");
            c = chest(sp);
            h.assertTrue(c != null && c.body().contains("부족"), "short message: " + (c == null ? null : c.body()));
            Forms.answer(sp, RuneSmith.TAB, null); // 룬 갈기 창으로
            c = chest(sp);
            h.assertTrue(c != null && c.title().contains("룬 갈기"), "to the grind window");
            Forms.answer(sp, -1, null);
            h.assertFalse(Forms.pending(sp), "closed");
            h.assertTrue(RuneSmith.buyTicket(sp, false, 0).contains("부족"), "zero tickets refused");
        } finally {
            Forms.HEADLESS.remove(sp.getUUID());
        }
        RpgGearTests.done(h, sp);
    }

    private static String labelText(LivingEntity d) {
        Display.TextDisplay t = Dummy.labelEntity(d);
        return t == null ? "" : t.getText().getString();
    }

    private static boolean hit(GameTestHelper h, LivingEntity d, ServerPlayer sp, float amount) {
        d.setInvulnerableTime(0);
        d.damageCooldownTime = 0;
        return d.hurtServer(h.getLevel(), sp.damageSources().playerAttack(sp), amount);
    }

    /**
     * 훈련용 허수아비: 눈앞 3칸에 세움 (나를 봄) → 체력보다 큰 피해에도 안 죽고 체력이 참 · 원래 피해로 기록 (합 · 횟수 · 최고 한 방 · 머리 위 표시),
     * 떨어짐 피해는 받지 않음, 밀려나면 제자리로, 5초 동안 안 맞으면 기록을 지움, NPC 설정 도구로 누르면 치움 (크리에이티브), /dummy clear
     */
    @GameTest(maxTicks = 200)
    public void dummyNeverDies(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        TrainingDummy d = Dummy.spawnDummy(sp);
        h.assertTrue(d != null && Dummy.isDummy(d) && Kit.hasFamily(d, "rpg_dummy"), "spawned (rpg_dummy)");
        h.assertTrue(Math.abs(d.getZ() - sp.getZ() - 3) < 1e-6 && Math.abs(d.getX() - sp.getX()) < 1e-6, "3 blocks ahead " + d.position());
        h.assertTrue(Math.abs(Kit.lookRotation(d.position(), sp.position())[0] - d.getYRot()) < 1, "faces the player " + d.getYRot());
        h.assertTrue(labelText(d).contains("훈련용 허수아비") && labelText(d).contains("때리면"), "intro label: " + labelText(d));
        h.assertTrue(Kit.canHit(sp, d), "skills can hit it");
        float max = d.getMaxHealth();
        h.assertTrue(hit(h, d, sp, 5000F), "big hit lands");
        h.assertTrue(d.isAlive() && !d.isRemoved() && d.getHealth() == max, "never dies, refilled " + d.getHealth());
        Dummy.Session s = Dummy.dummySession(d);
        h.assertTrue(s != null && s.hits() == 1 && s.total() == 5000 && s.max() == 5000, "recorded original damage " + (s == null ? null : s.total()));
        h.assertTrue(hit(h, d, sp, 12.5F), "small hit lands");
        s = Dummy.dummySession(d);
        h.assertTrue(s.hits() == 2 && Math.abs(s.total() - 5012.5) < 1e-3 && s.max() == 5000, "two hits " + s.total());
        h.assertTrue(labelText(d).contains("5,013") && labelText(d).contains("2회") && labelText(d).contains("5,000"), "label: " + labelText(d));
        h.assertFalse(d.hurtServer(h.getLevel(), d.damageSources().fall(), 10F), "no fall damage");
        h.assertTrue(Dummy.dummySession(d).hits() == 2, "fall not recorded");
        h.assertTrue(Dummy.dummyNum(1234.56).equals("1,235") && Dummy.dummyNum(12.34).equals("12.3") && Dummy.dummyNum(5).equals("5.0"), "numbers");
        Vec3 anchor = Dummy.anchorOf(d);
        h.assertTrue(anchor != null && anchor.distanceTo(d.position()) < 1e-6, "anchor at spawn");
        d.setPos(d.getX() + 1.5, d.getY(), d.getZ() - 0.5); // 밀려남
        h.runAfterDelay(3, () -> {
            h.assertTrue(Math.hypot(d.getX() - anchor.x, d.getZ() - anchor.z) < 0.06, "snapped back " + d.position());
            Display.TextDisplay label = Dummy.labelEntity(d);
            h.assertTrue(label != null && Math.hypot(label.getX() - d.getX(), label.getZ() - d.getZ()) < 0.06 && label.getY() > d.getY() + 2.3, "label above");
        });
        // 5초 (100틱) 동안 안 맞으면 기록을 지우고 안내 표시로 (10틱마다 확인)
        h.runAfterDelay(125, () -> {
            h.assertTrue(Dummy.dummySession(d) == null, "session cleared after idle");
            h.assertTrue(labelText(d).contains("때리면"), "intro label again: " + labelText(d));
            // NPC 설정 도구로 누르기 (크리에이티브) → 다음 틱에 치움, 같은 누름의 도구 사용은 관리 창을 열지 않게 표시
            sp.setGameMode(GameType.CREATIVE);
            sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GearItems.NPC_TOOL));
            InteractionResult r = UseEntityCallback.EVENT.invoker().interact(sp, h.getLevel(), InteractionHand.MAIN_HAND, d, null);
            h.assertTrue(r.consumesAction(), "tool tap consumed " + r);
            h.assertTrue(Npcs.recentToolTap(sp), "tool tap marked");
            Display.TextDisplay label = Dummy.labelEntity(d);
            h.runAfterDelay(3, () -> {
                h.assertTrue(d.isRemoved(), "removed by the tool");
                h.assertTrue(label == null || label.isRemoved(), "label removed");
                // /dummy clear: 주변 48칸
                TrainingDummy d2 = Dummy.spawnDummy(sp);
                h.assertTrue(d2 != null, "second dummy");
                h.assertTrue(Dummy.clearDummies(sp, 48) >= 1 && d2.isRemoved(), "cleared");
                RpgGearTests.done(h, sp);
            });
        });
    }
}
