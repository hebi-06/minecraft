package mmorpg.fabric.test;

import com.mojang.serialization.JsonOps;
import java.util.List;
import mmorpg.core.Runes;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.rune.Breakthrough;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.rune.RuneSystem;
import mmorpg.fabric.rune.SkillBar;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 11단계 스킬 칸 게임 시험 (서버, 시험 환경 mmorpg:skillbar — 룬 연출이 다른 시험의 과녁을 맞히지 않게 따로 묶음):
 * 올리기 (가방 → 칸 · 칸에 있던 룬과 바꿈 · 룬만 · 같은 룬은 한 칸에만) · 옮기기 · 빼기 (가방 → 핫바 순 · 가방이 차면 안 됨),
 * 룬은 스킬 칸에서만 씀 (칸을 골라도 · 들고 우클릭해도 쓰지 않음 — 우클릭은 빈 칸에 올림 · 같은 룬이면 단계가 다를 때 바꿔 끼움), 칸의 키로 쓰기 (재사용 대기 · 빈 칸),
 * 저장 (코덱 — 돌파 단계 그대로) · 죽어도 남음, 돌파가 스킬 칸의 룬도 합침 (결과는 칸에 그대로), 암살자 콤보가 스킬 칸의 룬을 셈.
 */
public final class RpgSkillBarTests {
    static final String ENV = "mmorpg:skillbar";

    private static Runes.Rune rune(String family, String grade) {
        return Runes.parseRune(Runes.runeId(family, grade));
    }

    /** 칸의 룬 ID · 단계 ("-" = 빈 칸) */
    private static String bar(ServerPlayer sp, int i) {
        return describe(SkillBar.get(sp, i));
    }

    private static String inv(ServerPlayer sp, int i) {
        return describe(sp.getInventory().getItem(i));
    }

    private static String describe(ItemStack s) {
        Runes.Rune r = RuneItems.runeOf(s);
        return r == null ? (s.isEmpty() ? "-" : s.getItem().toString()) : r.id() + "@" + Breakthrough.of(s);
    }

    private static String id(String family, String grade, int level) {
        return Runes.runeId(family, grade) + "@" + level;
    }

    /** 올리기 · 바꾸기 · 룬만 · 같은 룬은 한 칸에만 · 옮기기 · 빼기 (가방 먼저) · 가방이 차면 빼지 못함 */
    @GameTest(environment = ENV, maxTicks = 20)
    public void equipMoveUnequip(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        for (int i = 0; i < 36; i++) sp.getInventory().setItem(i, ItemStack.EMPTY);
        sp.getInventory().setItem(3, Breakthrough.stack("str", "common", 0));
        sp.getInventory().setItem(4, Breakthrough.stack("int", "rare", 2));
        sp.getInventory().setItem(5, new ItemStack(Items.STONE));
        sp.getInventory().setItem(6, Breakthrough.stack("str", "common", 1));
        h.assertTrue(SkillBar.equip(sp, 3, 0) == null && bar(sp, 0).equals(id("str", "common", 0)) && inv(sp, 3).equals("-"), "equip 3 → 0: " + bar(sp, 0) + " " + inv(sp, 3));
        h.assertTrue(SkillBar.equip(sp, 5, 1) != null && bar(sp, 1).equals("-") && sp.getInventory().getItem(5).is(Items.STONE), "only runes");
        h.assertTrue(SkillBar.equip(sp, 4, 0) == null && bar(sp, 0).equals(id("int", "rare", 2)) && inv(sp, 4).equals(id("str", "common", 0)),
                "swap into 0: " + bar(sp, 0) + " / bag " + inv(sp, 4));
        h.assertTrue(SkillBar.equip(sp, 4, 2) == null && bar(sp, 2).equals(id("str", "common", 0)) && inv(sp, 4).equals("-"), "equip 4 → 2");
        String dup = SkillBar.equip(sp, 6, 3);
        h.assertTrue(dup != null && dup.contains("3번") && bar(sp, 3).equals("-") && inv(sp, 6).equals(id("str", "common", 1)), "same rune only once: " + dup);
        // 같은 칸에는 다른 단계의 같은 룬으로 바꿔 끼울 수 있음
        h.assertTrue(SkillBar.equip(sp, 6, 2) == null && bar(sp, 2).equals(id("str", "common", 1)) && inv(sp, 6).equals(id("str", "common", 0)), "re-level swap");
        h.assertTrue(SkillBar.move(sp, 0, 7) == null && bar(sp, 7).equals(id("int", "rare", 2)) && bar(sp, 0).equals("-"), "move 0 → 7");
        h.assertTrue(SkillBar.move(sp, 2, 7) == null && bar(sp, 2).equals(id("int", "rare", 2)) && bar(sp, 7).equals(id("str", "common", 1)), "swap 2 ↔ 7");
        h.assertTrue(SkillBar.indexOf(sp, Runes.runeId("int", "rare")) == 2 && SkillBar.firstEmpty(sp) == 0, "index / first empty");
        // 빼기: 가방 (9 ~ 35) 의 첫 빈 칸으로 (핫바가 비어 있어도)
        h.assertTrue(SkillBar.unequip(sp, 7) == null && bar(sp, 7).equals("-") && inv(sp, 9).equals(id("str", "common", 1)), "unequip to bag 9: " + inv(sp, 9));
        h.assertTrue(SkillBar.unequip(sp, 7) == null, "empty slot: nothing");
        // 가방이 가득 차면 빼지 못함 (칸에 그대로)
        for (int i = 0; i < 36; i++) if (sp.getInventory().getItem(i).isEmpty()) sp.getInventory().setItem(i, new ItemStack(Items.STONE));
        h.assertTrue(SkillBar.unequip(sp, 2) != null && bar(sp, 2).equals(id("int", "rare", 2)), "full bag: kept");
        h.assertTrue(SkillBar.equip(sp, 99, 0) != null && SkillBar.equip(sp, 3, 8) != null && SkillBar.move(sp, -1, 2) != null, "bad slots");
        RpgGearTests.done(h, sp);
    }

    /** 룬은 스킬 칸에서만: 칸을 골라도 · 들고 우클릭해도 쓰지 않음 (우클릭 = 빈 칸에 올림), 칸의 키 (cast) 로 씀 · 재사용 대기 · 빈 칸 */
    @GameTest(environment = ENV, maxTicks = 40)
    public void castOnlyFromSkillBar(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        RpgPlayers.setStat(sp, "str", 20);
        Runes.Rune slam = rune("str", "common"); // 대지 강타: 대상이 없어도 나감
        sp.getInventory().setItem(0, ItemStack.EMPTY);
        sp.getInventory().setItem(1, Breakthrough.stack("str", "common", 0));
        sp.getInventory().setSelectedSlot(0);
        h.runAfterDelay(2, () -> {
            sp.getInventory().setSelectedSlot(1); // 칸을 골라도 쓰지 않음 (예전 /runequick on)
            h.runAfterDelay(3, () -> {
                h.assertTrue(RuneCast.cooldownLeft(sp, slam) == 0, "no cast on hotbar select");
                // 들고 우클릭: 쓰지 않고 첫 빈 칸 (0) 에 올림
                sp.getMainHandItem().use(h.getLevel(), sp, InteractionHand.MAIN_HAND);
                h.assertTrue(RuneCast.cooldownLeft(sp, slam) == 0, "no cast on right click");
                h.assertTrue(bar(sp, 0).equals(id("str", "common", 0)) && sp.getMainHandItem().isEmpty(), "right click equips: " + bar(sp, 0));
                // 같은 룬 · 같은 단계를 또 우클릭: 그대로 (손에 남음)
                sp.setItemInHand(InteractionHand.MAIN_HAND, Breakthrough.stack("str", "common", 0));
                sp.getMainHandItem().use(h.getLevel(), sp, InteractionHand.MAIN_HAND);
                h.assertTrue(bar(sp, 0).equals(id("str", "common", 0)) && describe(sp.getMainHandItem()).equals(id("str", "common", 0)) && SkillBar.firstEmpty(sp) == 1,
                        "same rune stays in hand");
                // 다른 단계 (3돌) 를 우클릭: 그 칸과 바꿔 끼움
                sp.setItemInHand(InteractionHand.MAIN_HAND, Breakthrough.stack("str", "common", 3));
                sp.getMainHandItem().use(h.getLevel(), sp, InteractionHand.MAIN_HAND);
                h.assertTrue(bar(sp, 0).equals(id("str", "common", 3)) && describe(sp.getMainHandItem()).equals(id("str", "common", 0)), "re-level by right click: "
                        + bar(sp, 0) + " / hand " + describe(sp.getMainHandItem()));
                sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD)); // 무기를 든 채 키로 씀
                // 칸의 키로 씀: 그 아이템의 돌파 단계 · 재사용 대기 · 가방 아이콘 쓸기 표시
                h.assertTrue(SkillBar.cast(sp, 0), "cast slot 0");
                h.assertTrue(RuneCast.cooldownLeft(sp, slam) > 0 && Breakthrough.castLevel(sp, "groundSlam") == 3, "cooldown + level 3");
                h.assertTrue(sp.getCooldowns().isOnCooldown(Breakthrough.stack("str", "common", 0)), "item sweep");
                h.assertTrue(sp.getMainHandItem().is(Items.IRON_SWORD), "weapon stays in hand");
                h.assertFalse(SkillBar.cast(sp, 0), "blocked by cooldown");
                h.assertFalse(SkillBar.cast(sp, 5), "empty slot");
                h.assertFalse(SkillBar.cast(sp, 9), "no such slot");
                RuneCast.resetCooldowns(sp);
                h.assertTrue(SkillBar.cast(sp, 0), "after reset");
                RuneCast.resetCooldowns(sp);
                RpgGearTests.done(h, sp);
            });
        });
    }

    /** 저장: 코덱으로 쓰고 다시 읽어도 칸 · 돌파 단계 그대로 (8칸 — 모자라면 빈 칸), 죽어도 남음 (copyOnDeath) */
    @GameTest(environment = ENV, maxTicks = 20)
    public void savedAndKeptOnDeath(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        SkillBar.set(sp, 1, Breakthrough.stack("int", "transcend", 4));
        SkillBar.set(sp, 6, Breakthrough.stack("assassin", "rare", 0));
        RegistryOps<com.google.gson.JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, h.getLevel().registryAccess());
        com.google.gson.JsonElement json = SkillBar.Data.CODEC.encodeStart(ops, new SkillBar.Data(SkillBar.slots(sp))).getOrThrow();
        SkillBar.Data back = SkillBar.Data.CODEC.parse(ops, json).getOrThrow();
        h.assertTrue(back.slots().size() == SkillBar.SIZE, "8 slots: " + back.slots().size());
        h.assertTrue(describe(back.slots().get(1)).equals(id("int", "transcend", 4)) && describe(back.slots().get(6)).equals(id("assassin", "rare", 0))
                && back.slots().get(0).isEmpty(), "loaded: " + json);
        com.google.gson.JsonElement shortList = SkillBar.Data.CODEC.encodeStart(ops, new SkillBar.Data(List.of(Breakthrough.stack("vit", "hero", 1)))).getOrThrow();
        SkillBar.Data padded = SkillBar.Data.CODEC.parse(ops, shortList).getOrThrow();
        h.assertTrue(padded.slots().size() == SkillBar.SIZE && describe(padded.slots().get(0)).equals(id("vit", "hero", 1)), "padded to 8");
        h.assertTrue(SkillBar.TYPE.copyOnDeath() && SkillBar.TYPE.isPersistent(), "kept on death · saved");
        RpgGearTests.done(h, sp);
    }

    /** 돌파: 스킬 칸의 룬도 합침 — 칸의 룬이 남는 룬 (같은 단계면 칸이 먼저) · 가방의 높은 단계와 합쳐도 결과는 칸에 그대로 */
    @GameTest(environment = ENV, maxTicks = 20)
    public void breakthroughUsesSkillBar(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        for (int i = 0; i < 36; i++) sp.getInventory().setItem(i, ItemStack.EMPTY);
        // 칸 0: 메테오 1돌 + 가방 20: 메테오 0돌 → 칸 0 이 2돌, 가방 20 비움
        SkillBar.set(sp, 0, Breakthrough.stack("int", "transcend", 1));
        sp.getInventory().setItem(20, Breakthrough.stack("int", "transcend", 0));
        List<Breakthrough.Merge> opts = Breakthrough.options(sp);
        h.assertTrue(opts.size() == 1 && opts.get(0).slot() == SkillBar.SLOT_BASE && opts.get(0).result() == 2, "merge into bar 0: " + opts);
        h.assertTrue(Breakthrough.merge(sp, opts.get(0)).contains("2돌"), "merged");
        h.assertTrue(bar(sp, 0).equals(id("int", "transcend", 2)) && inv(sp, 20).equals("-"), "bar 0 = 2: " + bar(sp, 0) + " bag " + inv(sp, 20));
        // 칸 1: 거인의 일격 0돌, 가방 12: 거인의 일격 2돌 (가방이 남는 룬) → 결과 3돌은 칸 1 에 (올린 룬이 그대로 남게)
        SkillBar.set(sp, 1, Breakthrough.stack("str", "hero", 0));
        sp.getInventory().setItem(12, Breakthrough.stack("str", "hero", 2));
        Breakthrough.Merge m = null;
        for (Breakthrough.Merge x : Breakthrough.options(sp)) if (x.target().r().id().equals(Runes.runeId("str", "hero"))) m = x;
        h.assertTrue(m != null && m.target().slot() == 12 && m.material().slot() == SkillBar.SLOT_BASE + 1 && m.slot() == SkillBar.SLOT_BASE + 1 && m.result() == 3,
                "bag target, result in bar 1: " + m);
        Breakthrough.merge(sp, m);
        h.assertTrue(bar(sp, 1).equals(id("str", "hero", 3)) && inv(sp, 12).equals("-"), "bar 1 = 3: " + bar(sp, 1) + " bag " + inv(sp, 12));
        h.assertTrue(Breakthrough.where(SkillBar.SLOT_BASE + 1).equals("스킬 칸 2") && Breakthrough.where(3).equals("핫바 4번 칸") && Breakthrough.where(20).equals("가방"),
                "slot names");
        // 모두 돌파: 칸 0 (2돌) + 가방 2개 (0돌 · 0돌) → 칸 0 이 3돌 → 4돌
        sp.getInventory().setItem(25, Breakthrough.stack("int", "transcend", 0));
        sp.getInventory().setItem(26, Breakthrough.stack("int", "transcend", 0));
        List<Breakthrough.Merge> all = Breakthrough.planAll(sp);
        String msg = Breakthrough.mergeAll(sp, all);
        h.assertTrue(bar(sp, 0).equals(id("int", "transcend", 4)) && inv(sp, 25).equals("-") && inv(sp, 26).equals("-"), "all into bar 0: " + bar(sp, 0) + " — " + msg);
        RpgGearTests.done(h, sp);
    }

    /** 암살자 콤보: "가진" 룬 = 스킬 칸의 룬 — 가방에만 있으면 콤보 없음, 칸에 둘이면 이어 쓰기 (상태 줄 "2 콤보") */
    @GameTest(environment = ENV, maxTicks = 40)
    public void assassinComboCountsSkillBar(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        RpgPlayers.setStat(sp, "dex", 30);
        Runes.Rune stars = rune("assassin", "rare");
        Runes.Rune smoke = rune("assassin", "noble");
        // 가방에만: 콤보 없음
        sp.getInventory().setItem(20, RuneItems.stack("assassin", "rare"));
        sp.getInventory().setItem(21, RuneItems.stack("assassin", "noble"));
        h.assertTrue(RuneCast.castRune(sp, stars, true) && RuneCast.castRune(sp, smoke, true), "cast from api");
        String hud = RuneCast.combo.hud(sp);
        h.assertTrue(hud.isEmpty(), "bag only: no combo hud '" + hud + "'");
        h.runAfterDelay(4, () -> {
            RuneCast.resetCooldowns(sp);
            RuneSystem.forget(sp.getUUID());
            // 스킬 칸에 둘: 이어 쓰기
            h.assertTrue(SkillBar.equip(sp, 20, 0) == null && SkillBar.equip(sp, 21, 1) == null, "equipped two");
            h.assertTrue(SkillBar.cast(sp, 0) && SkillBar.cast(sp, 1), "cast both from the bar");
            String hud2 = RuneCast.combo.hud(sp).replaceAll("§.", "");
            h.assertTrue(hud2.contains("암살자 2/5") && hud2.contains("2 콤보"), "combo hud '" + hud2 + "'");
            h.runAfterDelay(4, () -> {
                RuneCast.resetCooldowns(sp);
                RuneSystem.forget(sp.getUUID());
                RpgGearTests.done(h, sp);
            });
        });
    }
}
