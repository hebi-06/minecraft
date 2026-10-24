package mmorpg.fabric.test;

import java.util.List;
import java.util.Map;
import mmorpg.core.Artifacts;
import mmorpg.core.Economy;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.artifact.ArtifactSystem;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.gear.ArtifactItem;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.smith.ArtifactCraft;
import mmorpg.fabric.smith.Smith;
import mmorpg.fabric.stattree.StatTrees;
import mmorpg.fabric.ui.FormNet;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.Icon;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 0.37.0 아티팩트 게임 시험 (서버): 아이템 20 · 설명, 끼기 (맞는 칸 저절로 · 반지 둘 · 다 차면 낮은 등급과 바꿈) · 착용 레벨 · 칸 종류 · 반지 1 ↔ 2 ·
 * 빼기 · 가방이 차면 그대로, 보너스 (스탯 트리를 쓰지 않는 시험 플레이어도 — 공격력 · 룬 · 치명타 · 치명타 피해 · 최대 체력 · 받는 피해 · 재사용 대기 ·
 * 이동 속도), 저장 (코덱 왕복), 들고 우클릭, 대장장이 아티팩트 쪽 (6줄 · 칸 · 재료 부족 · 실버 · 골드 · 보스 재료 · 창 열기 칸).
 */
public final class RpgArtifactTests {
    private static ItemStack art(String type, String grade) {
        return new ItemStack(GearItems.byId(Artifacts.id(type, grade)));
    }

    private static String slotId(ServerPlayer sp, int i) {
        Artifacts.Art a = ArtifactSystem.art(sp, i);
        return a == null ? "-" : a.type() + "_" + a.grade();
    }

    private static FormNet.ChestPayload chest(ServerPlayer sp) {
        return Forms.last(sp) instanceof FormNet.ChestPayload c ? c : null;
    }

    private static String body(ServerPlayer sp) {
        FormNet.ChestPayload c = chest(sp);
        return c == null ? "(창 없음)" : c.body();
    }

    private static void near(GameTestHelper h, double a, double b, String what) {
        h.assertTrue(Math.abs(a - b) < 1e-6, what + " (" + a + " vs " + b + ")");
    }

    /** 아이템 20: 등록 · 이름 · 설명 · 하나씩 · 초월만 반짝임 */
    @GameTest(maxTicks = 20)
    public void items(GameTestHelper h) {
        h.assertTrue(Artifacts.all().size() == 20, "20 artifacts");
        for (Artifacts.Art a : Artifacts.all()) {
            Item it = GearItems.byId(a.id());
            h.assertTrue(it instanceof ArtifactItem ai && ai.art() == a, "registered " + a.id());
            ItemStack st = new ItemStack(it);
            h.assertTrue(st.getMaxStackSize() == 1, "stack 1 " + a.id());
            String shown = net.minecraft.ChatFormatting.stripFormatting(st.getHoverName().getString());
            h.assertTrue(a.name().equals(shown) && st.getHoverName().getString().startsWith(a.gradeColor().substring(0, 2)), "name " + st.getHoverName().getString());
            h.assertTrue(st.hasFoil() == a.grade().equals("transcend"), "glint " + a.id());
        }
        h.assertTrue(ArtifactItem.of(new ItemStack(Icon.itemById("rpg:loot_troll"))) == null, "not an artifact");
        h.assertTrue(mmorpg.fabric.container.ContainerTools.grade(art("ring", "hero")) == 3, "grade under for the RPG window");
        h.succeed();
    }

    /** 끼기 · 보너스: 50렙 · 초월 다섯 → 합 (공격력 8 · 치명타 6 · 최대 체력 40 · 받는 피해 4 · 룬 8 · 재사용 4 · 치명타 피해 12 · 이동 4) */
    @GameTest(maxTicks = 20)
    public void equipAndBonus(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        sp.setExperienceLevels(50);
        h.assertFalse(StatTrees.isActive(sp), "stat tree off for test players (artifacts still count)");
        double hp0 = RpgHealth.maxOf(sp);
        h.assertTrue(StatTrees.atkMult(sp) == 1.0 && ArtifactSystem.totals(sp).isEmpty(), "nothing yet");
        String[][] put = {{"ring", "transcend"}, {"ring", "transcend"}, {"necklace", "transcend"}, {"earring", "transcend"}, {"bracelet", "transcend"}};
        for (int i = 0; i < put.length; i++) sp.getInventory().setItem(9 + i, art(put[i][0], put[i][1]));
        for (int i = 0; i < put.length; i++) h.assertTrue(ArtifactSystem.equip(sp, 9 + i, -1) == null, "equip " + put[i][0]);
        h.assertTrue(slotId(sp, 0).equals("ring_transcend") && slotId(sp, 1).equals("ring_transcend") && slotId(sp, 2).equals("necklace_transcend")
                && slotId(sp, 3).equals("earring_transcend") && slotId(sp, 4).equals("bracelet_transcend"), "slots auto");
        for (int i = 0; i < put.length; i++) h.assertTrue(sp.getInventory().getItem(9 + i).isEmpty(), "left the bag " + i);
        Map<String, Double> t = ArtifactSystem.totals(sp);
        near(h, t.get("atk"), 8, "atk total");
        near(h, StatTrees.atkMult(sp), 1.08, "atkMult");
        near(h, StatTrees.runeMult(sp), 1.16, "runeMult (atk + rune)");
        near(h, StatTrees.critAdd(sp), 0.06, "critAdd");
        near(h, StatTrees.critDmgAdd(sp), 0.12, "critDmgAdd");
        near(h, StatTrees.defMult(sp), 0.96, "defMult");
        near(h, StatTrees.cdMult(sp), 0.96, "cdMult");
        near(h, StatTrees.hpAdd(sp), 40, "hpAdd");
        near(h, RpgHealth.maxOf(sp), hp0 + 40, "max HP +40");
        AttributeModifier speed = sp.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Mmorpg.id("stat_tree_speed"));
        h.assertTrue(speed != null && Math.abs(speed.amount() - 0.04) < 1e-9, "speed +4% " + (speed == null ? null : speed.amount()));
        Map<String, Double> all = StatTrees.allTotals(sp);
        near(h, all.get("critDmg"), 12, "allTotals (stat sheet)");
        // 빼기: 목걸이 → 가방, 최대 체력 되돌림
        h.assertTrue(ArtifactSystem.unequip(sp, 2) == null && ArtifactSystem.get(sp, 2).isEmpty(), "unequip necklace");
        h.assertTrue(RpgGearTests.countIn(sp, GearItems.byId(Artifacts.id("necklace", "transcend"))) == 1, "necklace back in the bag");
        near(h, RpgHealth.maxOf(sp), hp0, "max HP back");
        near(h, StatTrees.defMult(sp), 1.0, "def back");
        // 모두 빼면 이동 속도 수정자도 없음
        for (int i = 0; i < ArtifactSystem.SIZE; i++) ArtifactSystem.unequip(sp, i);
        h.assertTrue(sp.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Mmorpg.id("stat_tree_speed")) == null, "speed modifier removed");
        h.assertTrue(ArtifactSystem.totals(sp).isEmpty() && ArtifactSystem.worn(sp) == 0, "empty");
        RpgGearTests.done(h, sp);
    }

    /** 착용 레벨 · 칸 종류 · 반지 1 ↔ 2 · 다 차면 낮은 등급과 바꿈 · 가방이 차면 빼지 않음 · 들고 우클릭 */
    @GameTest(maxTicks = 20)
    public void rules(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        sp.setExperienceLevels(25);
        sp.getInventory().setItem(9, art("ring", "hero"));
        h.assertTrue(ArtifactSystem.equip(sp, 9, -1) != null && ArtifactSystem.get(sp, 0).isEmpty(), "hero ring at 25: no");
        h.assertTrue(!sp.getInventory().getItem(9).isEmpty(), "stays in the bag");
        sp.getInventory().setItem(10, art("earring", "rare"));
        h.assertTrue(ArtifactSystem.equip(sp, 10, -1) == null && slotId(sp, 3).equals("earring_rare"), "rare earring at 25: ok");
        // 칸 종류
        sp.getInventory().setItem(11, art("necklace", "common"));
        h.assertTrue(ArtifactSystem.equip(sp, 11, 0) != null && ArtifactSystem.get(sp, 0).isEmpty(), "necklace into ring 1: no");
        h.assertTrue(ArtifactSystem.equip(sp, 11, 2) == null && slotId(sp, 2).equals("necklace_common"), "necklace into its slot");
        // 반지 둘 · 반지 1 ↔ 2 · 다른 종류 칸으로 옮기기 안 됨
        sp.setExperienceLevels(45);
        h.assertTrue(ArtifactSystem.equip(sp, 9, -1) == null && slotId(sp, 0).equals("ring_hero"), "hero ring → ring 1");
        sp.getInventory().setItem(12, art("ring", "common"));
        h.assertTrue(ArtifactSystem.equip(sp, 12, -1) == null && slotId(sp, 1).equals("ring_common"), "common ring → ring 2 (empty)");
        h.assertTrue(ArtifactSystem.move(sp, 0, 1) == null && slotId(sp, 0).equals("ring_common") && slotId(sp, 1).equals("ring_hero"), "swap rings");
        h.assertTrue(ArtifactSystem.move(sp, 1, 2) != null && slotId(sp, 2).equals("necklace_common"), "ring → necklace slot: no");
        // 둘 다 차 있으면 낮은 등급 (일반 — 반지 1) 과 바꿈, 바뀐 것은 그 가방 칸으로
        sp.getInventory().setItem(13, art("ring", "rare"));
        h.assertTrue(ArtifactSystem.equip(sp, 13, -1) == null && slotId(sp, 0).equals("ring_rare") && slotId(sp, 1).equals("ring_hero"), "replace the lower ring");
        h.assertTrue(ArtifactItem.of(sp.getInventory().getItem(13)) == Artifacts.art("ring", "common"), "common ring back in slot 13");
        // 가방이 차면 빼지 않음
        for (int i = 0; i < 36; i++) if (sp.getInventory().getItem(i).isEmpty()) sp.getInventory().setItem(i, new ItemStack(net.minecraft.world.item.Items.DIRT));
        h.assertTrue(ArtifactSystem.unequip(sp, 0) != null && slotId(sp, 0).equals("ring_rare"), "bag full: stays");
        sp.getInventory().clearContent();
        // 들고 우클릭: 팔찌 → 팔찌 칸
        sp.getInventory().setItem(sp.getInventory().getSelectedSlot(), art("bracelet", "noble"));
        h.assertTrue(ArtifactSystem.equipHeld(sp, InteractionHand.MAIN_HAND) == null && slotId(sp, 4).equals("bracelet_noble"), "right click equips");
        h.assertTrue(sp.getMainHandItem().isEmpty(), "hand empty after equip");
        // 레벨이 모자란 아티팩트는 칸에 있어도 보너스 없음 (명령어로 넣은 것)
        ArtifactSystem.set(sp, 4, art("bracelet", "transcend"));
        h.assertFalse(ArtifactSystem.totals(sp).containsKey("critDmg"), "transcend bracelet at 45: no bonus " + ArtifactSystem.totals(sp));
        sp.setExperienceLevels(50);
        mmorpg.fabric.RpgPlayers.checkLevelUp(sp); // 레벨이 오르면 (RpgPlayers.update) 다시 셈
        h.assertTrue(ArtifactSystem.totals(sp).getOrDefault("critDmg", 0.0) == 12, "50렙: bonus " + ArtifactSystem.totals(sp));
        RpgGearTests.done(h, sp);
    }

    /** 저장: 칸 다섯 (코덱 왕복 — 플레이어 첨부로 저장되는 모양) */
    @GameTest(maxTicks = 20)
    public void persistence(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        sp.setExperienceLevels(50);
        ArtifactSystem.set(sp, 0, art("ring", "hero"));
        ArtifactSystem.set(sp, 3, art("earring", "transcend"));
        var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag tag = ArtifactSystem.Data.CODEC.encodeStart(ops, sp.getAttached(ArtifactSystem.TYPE)).getOrThrow();
        ArtifactSystem.Data back = ArtifactSystem.Data.CODEC.parse(ops, tag).getOrThrow();
        h.assertTrue(back.slots().size() == ArtifactSystem.SIZE, "5 slots");
        h.assertTrue(ArtifactItem.of(back.slots().get(0)) == Artifacts.art("ring", "hero") && back.slots().get(1).isEmpty()
                && ArtifactItem.of(back.slots().get(3)) == Artifacts.art("earring", "transcend"), "round trip " + back.slots());
        h.assertTrue(ArtifactSystem.TYPE.copyOnDeath(), "kept on death");
        RpgGearTests.done(h, sp);
    }

    /** 대장장이 아티팩트 쪽: 6줄 · 종류 넷 × 등급 다섯 · 확인 창 · 재료 부족 (아무것도 쓰지 않음) · 실버 · 골드 · 보스 재료 · 창 열기 칸 */
    @GameTest(maxTicks = 20)
    public void craft(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        Forms.HEADLESS.add(sp.getUUID());
        try {
            h.assertTrue(Smith.artifactPageIndex() == Smith.CRAFT_PAGES.size() - 1 && Smith.CRAFT_PAGES.get(Smith.artifactPageIndex()).label().equals("아티팩트"),
                    "artifact page last " + Smith.CRAFT_PAGES);
            Smith.smithLoop(sp, "artifact", null);
            FormNet.ChestPayload c = chest(sp);
            h.assertTrue(c != null && c.title().contains("아티팩트") && c.slots().size() == 54, "page (6 rows): " + (c == null ? null : c.title()));
            for (String t : Artifacts.types()) {
                for (String g : List.of("common", "rare", "noble", "hero", "transcend")) {
                    int s = ArtifactCraft.slotOf(t, g);
                    h.assertTrue(c.slots().get(s).icon().item().is(GearItems.byId(Artifacts.id(t, g))), "slot " + s + " = " + t + " " + g);
                }
            }
            int ring = ArtifactCraft.slotOf("ring", "common");
            Forms.answer(sp, ring, null);
            h.assertTrue(chest(sp) != null && chest(sp).title().contains("아티팩트 만들기") && body(sp).contains("공격력 +1%"), "confirm: " + body(sp));
            Forms.answer(sp, 11, null);
            h.assertTrue(body(sp).contains("재료 부족") && body(sp).contains("실버 20") && body(sp).contains("멧돼지 엄니 6"), "lack: " + body(sp));
            Item tusk = Icon.itemById("rpg:loot_boar");
            Item pelt = Icon.itemById("rpg:loot_gulon");
            sp.getInventory().setItem(20, new ItemStack(tusk, 7));
            sp.getInventory().setItem(21, new ItemStack(pelt, 6));
            Wallet.set(sp, Economy.Kind.SILVER, 25);
            Forms.answer(sp, ring, null);
            Forms.answer(sp, 11, null);
            h.assertTrue(body(sp).contains("완료"), "ring done: " + body(sp));
            h.assertTrue(RpgGearTests.countIn(sp, GearItems.byId(Artifacts.id("ring", "common"))) == 1 && RpgGearTests.countIn(sp, tusk) == 1
                    && RpgGearTests.countIn(sp, pelt) == 0 && Wallet.total(sp, Economy.Kind.SILVER) == 5, "paid: silver " + Wallet.total(sp, Economy.Kind.SILVER));
            // 초월 목걸이: 골드 + 공허 전리품 + 수르트 재료 (착용 레벨이 모자라도 만들 수 있음)
            Artifacts.Cost tc = Artifacts.cost("transcend");
            int slot = 27;
            for (Map.Entry<String, Integer> e : tc.items().entrySet()) sp.getInventory().setItem(slot++, new ItemStack(Icon.itemById(e.getKey()), e.getValue()));
            Wallet.set(sp, Economy.Kind.GOLD, tc.gold() - 1);
            int neck = ArtifactCraft.slotOf("necklace", "transcend");
            Forms.answer(sp, neck, null);
            Forms.answer(sp, 11, null);
            h.assertTrue(body(sp).contains("재료 부족") && body(sp).contains("골드 1"), "gold short: " + body(sp));
            h.assertTrue(RpgGearTests.countIn(sp, Icon.itemById("rpg:muspel_heart")) == 1, "nothing used");
            Wallet.set(sp, Economy.Kind.GOLD, tc.gold());
            Forms.answer(sp, neck, null);
            Forms.answer(sp, 11, null);
            h.assertTrue(body(sp).contains("완료"), "necklace done: " + body(sp));
            h.assertTrue(RpgGearTests.countIn(sp, GearItems.byId(Artifacts.id("necklace", "transcend"))) == 1 && Wallet.total(sp, Economy.Kind.GOLD) == 0
                    && RpgGearTests.countIn(sp, Icon.itemById("rpg:muspel_heart")) == 0 && RpgGearTests.countIn(sp, Icon.itemById("rpg:laevateinn_shard")) == 0
                    && RpgGearTests.countIn(sp, Icon.itemById("rpg:loot_void_leaper")) == 0, "paid transcend");
            // 윗줄 가운데 칸 = 다음 쪽 (영웅), 아랫줄 왼쪽 칸 = 아티팩트 창 (흐름 끝)
            Forms.answer(sp, 4, null);
            h.assertTrue(chest(sp) != null && chest(sp).title().contains("영웅"), "next page: " + (chest(sp) == null ? null : chest(sp).title()));
            Forms.answer(sp, -1, null);
            Smith.smithLoop(sp, "artifact", null);
            Forms.answer(sp, ArtifactCraft.OPEN_SLOT, null);
            h.assertFalse(Forms.pending(sp), "open slot ends the smith flow");
        } finally {
            Forms.HEADLESS.remove(sp.getUUID());
        }
        RpgGearTests.done(h, sp);
    }
}
