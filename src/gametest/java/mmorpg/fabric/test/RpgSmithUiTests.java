package mmorpg.fabric.test;

import java.util.List;
import java.util.Map;
import mmorpg.core.Artifacts;
import mmorpg.core.Enhance;
import mmorpg.core.Grade;
import mmorpg.core.Rules;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.gear.WeaponEnhance;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.smith.Smith;
import mmorpg.fabric.smith.SmithNet;
import mmorpg.fabric.smith.SmithUi;
import mmorpg.fabric.smith.VoidArmorCraft;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.Icon;
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
 * 0.44.0 대장장이 전용 창 (서버 — 클라이언트 대신 SmithUi.FORCE: 보낸 패킷은 SmithUi.SENT 에): 강화 탭 칸 (성공 확률 · 재료 가진 수 / 필요) → 망치질
 * (WorkPayload · 재료는 끝에) → 판정 · 결과 · 바뀐 칸, 망치질 중 다시 누름 · 탭 바꿈 무시, 탭 · 제작 쪽 (공허의 갑옷 부위 다섯 · 장신구 묶음), 모자라면 거절 알림,
 * NPC 와 모루 (모루 쪽 yaw · 모루가 없으면 잠깐 놓는 모루 — 망치질 뒤 사라짐), 망치질 중에 닫아도 끝나면 결과 · 창 닫힘 (done).
 * 강화 성공 여부는 EnhanceWindow.rand 를 바꾸지 않고 (RpgSmithTests 와 함께 돎) 결과에 맞는지 봄.
 */
public final class RpgSmithUiTests {
    private static Item stone() {
        return GearItems.byId("rpg:enhance_stone");
    }

    private static ServerPlayer forced(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        SmithUi.FORCE.add(sp.getUUID());
        SmithUi.SENT.remove(sp.getUUID());
        return sp;
    }

    private static void finish(GameTestHelper h, ServerPlayer sp) {
        SmithUi.action(sp, "close", "", 0, 0);
        SmithUi.FORCE.remove(sp.getUUID());
        SmithUi.SENT.remove(sp.getUUID());
        RpgGearTests.done(h, sp);
    }

    private static List<CustomPacketPayload> sent(ServerPlayer sp) {
        return SmithUi.SENT.getOrDefault(sp.getUUID(), List.of());
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

    /** 강화 탭: 칸 → 망치질 (재료는 끝에 · 다시 누름 · 탭 바꿈 무시) → TICKS 뒤 판정 (성공이면 +1 · 실패면 그대로 — +0 은 떨어지지 않음) · 강화석 1 · 결과 · 바뀐 칸 → 닫으면 done */
    @GameTest(maxTicks = 100)
    public void enhanceInWindow(GameTestHelper h) {
        ServerPlayer sp = forced(h);
        Weapon w = Weapons.weaponFrom("sword", 10, "common");
        sp.getInventory().setItem(0, new ItemStack(RpgItems.byId(w.id())));
        sp.getInventory().setItem(20, new ItemStack(stone(), 64));
        boolean[] closed = {false};
        Smith.smithLoop(sp, null, "enhance", () -> closed[0] = true);
        SmithNet.ViewPayload v = lastView(sp);
        h.assertTrue(v != null && v.open() && v.tab().equals(SmithUi.ENHANCE) && v.tabs().containsAll(List.of(SmithUi.RAISE, SmithUi.CRAFT)), "window opened " + v);
        h.assertTrue(Forms.last(sp) == null, "no chest window for a modded client");
        SmithNet.Entry e = entry(v, "w:0");
        Enhance.Cost c = Enhance.enhanceCost(0, "common");
        h.assertTrue(e != null && e.can() && e.state() == 0 && e.from().equals("+0") && e.to().equals("+1") && Math.abs(e.rate() - c.rate()) < 1e-9, "weapon row " + e);
        h.assertTrue(e.costs().size() == 1 && e.costs().get(0).have() == 64 && e.costs().get(0).need() == 1 && e.scroll() == -1, "stone 64 / 1 · no scroll at +0");
        h.assertTrue(v.res().size() == 3 && v.res().get(0).count() == 64, "resources row (stones)");
        SmithUi.action(sp, "do", "w:0", 0, 0);
        SmithNet.WorkPayload work = lastWork(sp);
        h.assertTrue(work != null && work.key().equals("w:0") && work.ticks() == SmithUi.TICKS && work.impacts().equals(SmithUi.IMPACTS) && work.npc() == -1,
                "work started " + work);
        h.assertTrue(work.item().is(RpgItems.byId(w.id())) && work.grade() == 0, "anvil item = the weapon");
        h.assertTrue(SmithUi.busy(sp) && lastView(sp).busy(), "busy view");
        h.assertTrue(RpgGearTests.countIn(sp, stone()) == 64, "materials are taken only at the end");
        int n = works(sp);
        SmithUi.action(sp, "do", "w:0", 0, 0);
        SmithUi.action(sp, "tab", SmithUi.RAISE, 0, 0);
        h.assertTrue(works(sp) == n && SmithUi.ENHANCE.equals(SmithUi.testTab(sp)), "ignored while hammering");
        h.runAfterDelay(SmithUi.TICKS + 2, () -> {
            SmithNet.Result r = SmithUi.testResult(sp);
            int lv = WeaponEnhance.of(sp.getInventory().getItem(0));
            h.assertTrue(r.seq() == 1 && (r.kind().equals("success") && lv == 1 || r.kind().equals("keep") && lv == 0), "result " + r.kind() + " level " + lv);
            h.assertTrue(r.item().is(RpgItems.byId(w.id())) && WeaponEnhance.of(r.item()) == lv, "result item = the weapon now");
            h.assertTrue(RpgGearTests.countIn(sp, stone()) == 63, "one stone used: " + RpgGearTests.countIn(sp, stone()));
            SmithNet.ViewPayload after = lastView(sp);
            h.assertTrue(!after.busy() && after.result().seq() == 1 && !after.open() && !SmithUi.busy(sp), "view after");
            SmithNet.Entry e2 = entry(after, "w:0");
            h.assertTrue(e2 != null && e2.from().equals("+" + lv) && e2.costs().get(0).have() == 63, "row updated " + e2);
            SmithUi.action(sp, "close", "", 0, 0);
            h.assertTrue(closed[0] && !SmithUi.isOpen(sp), "close runs done");
            finish(h, sp);
        });
    }

    /** 탭 · 쪽: 레벨 올리기 (영웅 30렙 칼 — 모자라면 거절 알림) · 제작 (쪽 목록 · 영웅 무기 · 공허의 갑옷 부위 다섯 · 장신구 묶음) · 장신구 · 아티팩트 (빈 글) */
    @GameTest(maxTicks = 40)
    public void tabsAndPages(GameTestHelper h) {
        ServerPlayer sp = forced(h);
        Weapon w30 = Weapons.weaponFrom("sword", 30, "hero");
        sp.getInventory().setItem(2, new ItemStack(RpgItems.byId(w30.id())));
        Smith.smithLoop(sp, null, "smith", null);
        SmithNet.ViewPayload v = lastView(sp);
        h.assertTrue(v != null && v.tab().equals(SmithUi.RAISE), "smith → raise tab");
        SmithNet.Entry e = entry(v, "r:2");
        h.assertTrue(e != null && !e.can() && e.state() == 1 && e.note().contains("더 필요") && e.from().equals("30렙") && e.to().equals("35렙"), "raise row " + e);
        SmithUi.action(sp, "do", "r:2", 0, 0);
        h.assertTrue(lastWork(sp) == null && SmithUi.testMessage(sp).contains("더 필요"), "lack refused: " + SmithUi.testMessage(sp));
        SmithUi.action(sp, "tab", SmithUi.CRAFT, 0, 0);
        v = lastView(sp);
        h.assertTrue(v.tab().equals(SmithUi.CRAFT) && v.pages().size() == Smith.CRAFT_PAGES.size() && v.message().isEmpty(), "craft pages " + v.pages());
        SmithUi.action(sp, "page", "", 0, 0);
        v = lastView(sp);
        h.assertTrue(v.page() == 0 && !v.entries().isEmpty() && v.entries().get(0).key().startsWith("c:") && v.entries().get(0).action().equals("만들기"),
                "hero page weapons " + v.entries().size());
        SmithUi.action(sp, "page", "", 3, 0);
        v = lastView(sp);
        List<String> types = Rules.get().strings("ARMOR_TYPE_ORDER");
        h.assertTrue(Smith.voidPage(3) && v.entries().size() == types.size() && v.entries().get(0).variants().size() == 5
                && v.entries().get(0).variants().get(4).label().equals("한 벌"), "void armor rows · 5 choices (" + v.entries().size() + ")");
        SmithUi.action(sp, "page", "", 4, 0);
        v = lastView(sp);
        int arts = 0;
        for (String t : Artifacts.types()) for (Grade g : Rules.get().grades()) if (Artifacts.art(t, g.id()) != null) arts++;
        h.assertTrue(Smith.artifactPage(4) && v.entries().size() == arts && !v.entries().get(0).group().isEmpty() && v.entries().get(1).group().isEmpty(),
                "artifact rows grouped by type (" + v.entries().size() + " / " + arts + ")");
        if (Enhance.gearEnabled()) {
            SmithUi.action(sp, "tab", SmithUi.GEAR, 0, 0);
            v = lastView(sp);
            h.assertTrue(v.tab().equals(SmithUi.GEAR) && v.entries().isEmpty() && !v.empty().isEmpty(), "gear tab: empty text");
        }
        SmithUi.action(sp, "tab", "nothing", 0, 0);
        h.assertTrue(SmithUi.testTab(sp).equals(Enhance.gearEnabled() ? SmithUi.GEAR : SmithUi.CRAFT), "unknown tab ignored");
        finish(h, sp);
    }

    /** NPC 와 모루: 둘레 모루 쪽을 봄 (yaw) · 잠깐 모루 없음 → 모루를 치우면 옆으로 돌아서서 (90°) 그 앞에 잠깐 모루 (망치질 뒤 사라짐) */
    @GameTest(maxTicks = 200)
    public void npcAndAnvil(GameTestHelper h) {
        ServerPlayer sp = forced(h);
        ServerLevel level = h.getLevel();
        Vec3 at = h.absoluteVec(new Vec3(2.5, 2, 2.5));
        Mannequin npc = NpcData.spawnAt(level, at, at.add(0, 0, 3)); // 남쪽 (+z) 을 봄
        BlockPos anvil = h.absolutePos(new BlockPos(3, 2, 2)); // 바로 동쪽 (가운데끼리 1칸)
        level.setBlockAndUpdate(anvil, Blocks.ANVIL.defaultBlockState());
        Weapon w = Weapons.weaponFrom("dagger", 10, "rare");
        sp.getInventory().setItem(0, new ItemStack(RpgItems.byId(w.id())));
        sp.getInventory().setItem(20, new ItemStack(stone(), 64));
        Smith.smithLoop(sp, npc, "enhance", null);
        SmithNet.ViewPayload v = lastView(sp);
        h.assertTrue(v != null && v.npc() == npc.getId(), "npc in the view");
        SmithUi.action(sp, "do", "w:0", 0, 0);
        SmithNet.WorkPayload w1 = lastWork(sp);
        float want = Kit.lookRotation(npc.position(), new Vec3(anvil.getX() + 0.5, npc.getY(), anvil.getZ() + 0.5))[0];
        h.assertTrue(w1 != null && w1.npc() == npc.getId() && Math.abs(Mth.wrapDegrees(w1.yaw() - want)) < 0.5F, "faces the anvil " + (w1 == null ? null : w1.yaw()) + " vs " + want);
        h.assertTrue(SmithUi.npcWorkTick(npc) >= 0 && SmithUi.tempAnvil(npc) == null, "real anvil: no temporary anvil");
        h.runAfterDelay(SmithUi.TICKS + 3, () -> {
            level.setBlockAndUpdate(anvil, Blocks.AIR.defaultBlockState());
            SmithUi.action(sp, "do", "w:0", 0, 0);
            SmithNet.WorkPayload w2 = lastWork(sp);
            // 모루가 없으면 옆으로 돌아섬 (보던 사람에게 옆모습 — 오른쪽 · 막혔으면 왼쪽)
            h.assertTrue(w2 != null && w2 != w1 && Math.abs(Math.abs(Mth.wrapDegrees(w2.yaw() - npc.getYRot())) - 90F) < 0.5F,
                    "no anvil: turns sideways " + (w2 == null ? null : w2.yaw()) + " vs body " + npc.getYRot());
            h.assertTrue(SmithUi.tempAnvil(npc) != null, "temporary anvil placed");
            h.runAfterDelay(SmithUi.TICKS + 40, () -> {
                h.assertTrue(SmithUi.tempAnvil(npc) == null, "temporary anvil removed after the hammering");
                npc.discard();
                finish(h, sp);
            });
        });
    }

    /** 공허의 갑옷 (고른 부위 — 투구만 되고 한 벌은 모자람 → 거절) · 망치질 중에 창을 닫아도 끝나면 만들어지고 그때 창 닫힘 (done) */
    @GameTest(maxTicks = 80)
    public void closeWhileHammering(GameTestHelper h) {
        ServerPlayer sp = forced(h);
        VoidArmorCraft.Cost helm = VoidArmorCraft.pieceCost("helmet");
        int slot = 18;
        for (Map.Entry<String, Integer> x : helm.items().entrySet()) {
            Item it = Icon.itemById(x.getKey());
            for (int n = x.getValue(); n > 0; n -= 64) sp.getInventory().setItem(slot++, new ItemStack(it, Math.min(64, n)));
        }
        mmorpg.fabric.economy.Wallet.set(sp, mmorpg.core.Economy.Kind.GOLD, helm.gold());
        Item helmet = GearItems.byId("rpg:armor_str_l50_transcend_helmet");
        boolean[] closed = {false};
        Smith.smithLoop(sp, null, "craft", () -> closed[0] = true);
        SmithUi.action(sp, "page", "", 3, 0);
        SmithNet.Entry e = entry(lastView(sp), "v:str");
        h.assertTrue(e != null && e.variants().get(0).can() && !e.variants().get(4).can() && e.can(), "helmet can · set cannot " + e);
        SmithUi.action(sp, "do", "v:str", 4, 0);
        h.assertTrue(lastWork(sp) == null && SmithUi.testMessage(sp).contains("더 필요"), "set refused: " + SmithUi.testMessage(sp));
        SmithUi.action(sp, "do", "v:str", 0, 0);
        SmithNet.WorkPayload w = lastWork(sp);
        h.assertTrue(w != null && w.item().is(helmet) && w.grade() == 4, "helmet on the anvil " + w);
        SmithUi.action(sp, "close", "", 0, 0);
        h.assertTrue(!closed[0] && SmithUi.busy(sp), "closing while hammering waits for the result");
        h.runAfterDelay(SmithUi.TICKS + 2, () -> {
            h.assertTrue(closed[0] && !SmithUi.isOpen(sp), "done after the hammering");
            h.assertTrue(RpgGearTests.countIn(sp, helmet) == 1 && mmorpg.fabric.economy.Wallet.total(sp, mmorpg.core.Economy.Kind.GOLD) == 0, "helmet crafted · paid");
            finish(h, sp);
        });
    }
}
