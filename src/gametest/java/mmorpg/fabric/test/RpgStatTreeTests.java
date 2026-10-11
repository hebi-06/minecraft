package mmorpg.fabric.test;

import java.util.List;
import mmorpg.core.Runes;
import mmorpg.core.StatTree;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.rune.RuneGacha;
import mmorpg.fabric.rune.RuneItem;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.TicketItem;
import mmorpg.fabric.stattree.StatTreeCommands;
import mmorpg.fabric.stattree.StatTrees;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * 7단계 스탯 트리 게임 시험 (서버 — 시험 서버는 스탯 트리가 기본으로 꺼져 있어 TestSetup.treeOn 으로 켠 플레이어만):
 * 스탯을 찍으면 칸이 열리고 보상은 칸마다 처음 한 번만 (스탯을 되돌렸다 다시 찍어도 다시 안 줌), 보너스가 실제 수치에 (켠 플레이어 ÷ 끈 플레이어 —
 * 공격력 · 평타 피해 · 치명타 확률 · 치명타 배율 · 최대 체력 · 받는 피해 · 이동 속도 · 룬 재사용 대기 · 룬 피해), 갈래 고르기 · 에메랄드 초기화,
 * 스탯 룬 뽑기권 (등록 · 이름이 같은 계열만 나옴).
 */
public final class RpgStatTreeTests {
    private static final double EPS = 1e-6;

    private static ServerPlayer player(GameTestHelper h, boolean on) {
        ServerPlayer sp = RpgGearTests.player(h);
        if (on) TestSetup.treeOn(sp);
        return sp;
    }

    private static void done(GameTestHelper h, ServerPlayer... sps) {
        for (ServerPlayer sp : sps) {
            TestSetup.TREE_ON.remove(sp.getUUID());
            h.getLevel().getServer().getPlayerList().remove(sp);
        }
        h.succeed();
    }

    private static int count(ServerPlayer sp, net.minecraft.world.item.Item item) {
        return RpgGearTests.countIn(sp, item);
    }

    private static void near(GameTestHelper h, double a, double b, String what) {
        h.assertTrue(Math.abs(a - b) < EPS, what + " (" + a + " vs " + b + ")");
    }

    // ------------------------------------------------------------ 자람 · 보상 한 번

    @GameTest(maxTicks = 20)
    public void growsAndRewardsOnce(GameTestHelper h) {
        ServerPlayer sp = player(h, true);
        TicketItem strTicket = RuneItems.statTicket("str");
        RpgPlayers.addPoints(sp, 300);
        h.assertTrue(RpgPlayers.spend(sp, "str", 9) == 9, "spend 9");
        h.assertTrue(StatTrees.state(sp).claimedOf("str") == 0 && StatTrees.bonus(sp, "atk") == 0, "below 10: nothing");
        RpgPlayers.spend(sp, "str", 11); // 20 → 1 · 2 단계
        h.assertTrue(StatTrees.state(sp).claimedOf("str") == 2, "claimed 2: " + StatTrees.state(sp).claimedOf("str"));
        h.assertTrue(count(sp, strTicket) == 1, "2단계 → 힘 룬 뽑기권 1: " + count(sp, strTicket));
        near(h, StatTrees.bonus(sp, "atk"), 2, "단련 공격력 +2");
        near(h, StatTrees.bonus(sp, "critDmg"), 10, "굳센 손아귀 치명타 피해 +10");
        RpgPlayers.spend(sp, "str", 15); // 35 → 3단계 (갈래 — 보상 없음)
        h.assertTrue(count(sp, strTicket) == 1, "갈래 칸은 보상 없음");
        h.assertTrue(StatTree.pending("str", 35, StatTrees.state(sp).picksOf("str")).equals(List.of(2)), "갈래 기다림");
        // 스탯을 되돌렸다 다시 찍어도 보상은 다시 안 줌
        RpgPlayers.reset(sp);
        near(h, StatTrees.bonus(sp, "atk"), 0, "스탯 0 → 보너스 없음");
        h.assertTrue(StatTrees.state(sp).claimedOf("str") == 3, "받은 칸 기록은 그대로");
        RpgPlayers.spend(sp, "str", 25);
        h.assertTrue(count(sp, strTicket) == 1, "다시 찍어도 같은 칸 보상 없음: " + count(sp, strTicket));
        near(h, StatTrees.bonus(sp, "atk"), 2, "다시 찍으면 보너스는 다시");
        // 더 높이: 4 (룬 뽑기권) · 5 (힘 룬 뽑기권), 다 키우면 6 ~ 10
        RpgPlayers.setStat(sp, "str", 50);
        h.assertTrue(count(sp, strTicket) == 2 && count(sp, RuneItems.TICKET) == 1, "4 · 5단계 보상: " + count(sp, strTicket) + " / " + count(sp, RuneItems.TICKET));
        RpgPlayers.setStat(sp, "str", 120);
        h.assertTrue(StatTrees.state(sp).claimedOf("str") == 10, "다 자람");
        h.assertTrue(count(sp, strTicket) == 4 && count(sp, RuneItems.TICKET) == 2 && count(sp, RuneItems.ADV_TICKET) == 1,
                "한 나무 보상 7장: " + count(sp, strTicket) + " · " + count(sp, RuneItems.TICKET) + " · " + count(sp, RuneItems.ADV_TICKET));
        // 다른 나무는 따로
        RpgPlayers.setStat(sp, "vit", 20);
        h.assertTrue(count(sp, RuneItems.statTicket("vit")) == 1 && StatTrees.state(sp).claimedOf("vit") == 2, "체력 나무 2단계");
        StatTreeCommands.class.getName(); // 명령 클래스가 서버에 있음
        done(h, sp);
    }

    // ------------------------------------------------------------ 보너스 (켠 플레이어 ÷ 끈 플레이어)

    @GameTest(maxTicks = 40)
    public void bonusesApply(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        ServerPlayer a = player(h, true);
        ServerPlayer b = player(h, false);
        for (ServerPlayer p : List.of(a, b)) {
            RpgPlayers.setStat(p, "str", 20); // 단련 공격력 2 · 굳센 손아귀 치명타 피해 10
            RpgPlayers.setStat(p, "dex", 70); // 치명타 1 + 2, 이동 속도 3, 매의 눈 공격력 2, 연속 사격 재사용 대기 4 (갈래 둘은 안 고름)
            RpgPlayers.setStat(p, "int", 10); // 마나의 흐름 룬 피해 2
            RpgPlayers.setStat(p, "vit", 20); // 강건함 최대 체력 5 · 두꺼운 피부 받는 피해 2
        }
        near(h, StatTrees.atkMult(a), 1.04, "공격력 ×1.04");
        near(h, StatTrees.atkMult(b), 1, "끈 플레이어 ×1");
        Weapon w = Weapons.weaponFrom("sword", 0, "common");
        for (ServerPlayer p : List.of(a, b)) p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RpgItems.byId(w.id())));
        near(h, RpgCombat.powerOf(a, w) / RpgCombat.powerOf(b, w), 1.04, "무기 피해 배율 비");
        Husk z = TestDummies.make(h.spawnWithNoFreeWill(EntityTypes.HUSK, 3.5F, 2F, 3.5F));
        RpgCombat.forceCrit = false;
        double da = RpgCombat.meleeDamage(a, z, 1F, 1F);
        double db = RpgCombat.meleeDamage(b, z, 1F, 1F);
        RpgCombat.forceCrit = true;
        double ca = RpgCombat.meleeDamage(a, z, 1F, 1F);
        double cb = RpgCombat.meleeDamage(b, z, 1F, 1F);
        RpgCombat.forceCrit = null;
        RpgCombat.dropMark(z, a);
        RpgCombat.dropMark(z, b);
        near(h, da / db, 1.04, "평타 피해 비");
        near(h, ca / cb, 1.04 * (w.critMult() + 0.10) / w.critMult(), "치명타 평타 비 (공격력 · 치명타 배율 +0.1)");
        near(h, RpgCombat.critChanceOf(a, w, null) - RpgCombat.critChanceOf(b, w, null), 0.03, "치명타 확률 +3%p");
        near(h, RpgCombat.critMultOf(a, w) - RpgCombat.critMultOf(b, w), 0.10, "치명타 배율 +0.1");
        near(h, RpgHealth.maxHpOf(a) - RpgHealth.maxHpOf(b), 5, "최대 체력 +5");
        h.assertTrue(Math.abs(a.getMaxHealth() - RpgHealth.maxHpOf(a)) < 1e-3, "바닐라 최대 체력도 " + a.getMaxHealth());
        AttributeInstance sa = a.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance sb = b.getAttribute(Attributes.MOVEMENT_SPEED);
        near(h, sa.getValue(), sa.getBaseValue() * 1.03, "이동 속도 ×1.03");
        near(h, sb.getValue(), sb.getBaseValue(), "끈 플레이어 이동 속도 그대로");
        Runes.Rune r = Runes.parseRune("rpg:rune_str_common");
        h.assertTrue(RuneCast.cooldownTicks(a, r) == Math.round(r.cd() * 20 * 0.96) && RuneCast.cooldownTicks(b, r) == Math.round(r.cd() * 20),
                "룬 재사용 대기 -4%: " + RuneCast.cooldownTicks(a, r) + " / " + RuneCast.cooldownTicks(b, r));
        near(h, StatTrees.runeMult(a), 1.06, "룬 피해 ×(1 + 공격력 4 + 룬 2)");
        double got = RuneKit.runeHit(a, z, 10, 1, "str"); // 새 과녁 (받은 피해 0 에서)
        h.assertTrue(Math.abs(TestDummies.dealt(z) - 10.6) < 1e-3, "룬 피해 10 → 10.6 (" + TestDummies.dealt(z) + ", " + got + ")");
        // 받는 피해 -2% (방패 · 버프 없음)
        a.setHealth(a.getMaxHealth());
        b.setHealth(b.getMaxHealth());
        float ha = a.getHealth();
        float hb = b.getHealth();
        for (ServerPlayer p : List.of(a, b)) {
            p.setInvulnerableTime(0);
            p.damageCooldownTime = 0;
            p.hurtServer(h.getLevel(), p.damageSources().fellOutOfWorld(), 10F); // 접속 직후 무적도 지나는 피해 (RpgGameTests 와 같음)
        }
        double la = ha - a.getHealth();
        double lb = hb - b.getHealth();
        h.assertTrue(lb > 0 && Math.abs(la / lb - 0.98) < 1e-3, "받는 피해 ×0.98: " + la + " / " + lb);
        // 끄면 모두 원래대로
        TestSetup.treeOff(a);
        near(h, StatTrees.atkMult(a), 1, "끄면 공격력 원래대로");
        near(h, a.getAttribute(Attributes.MOVEMENT_SPEED).getValue(), sa.getBaseValue(), "끄면 이동 속도 원래대로");
        done(h, a, b);
    }

    // ------------------------------------------------------------ 갈래 · 초기화

    @GameTest(maxTicks = 20)
    public void pickAndReset(GameTestHelper h) {
        ServerPlayer sp = player(h, true);
        RpgPlayers.setStat(sp, "str", 35);
        h.assertTrue(StatTrees.pick(sp, "str", 5, 0) != null, "아직 안 열린 갈래는 못 고름");
        h.assertTrue(StatTrees.pick(sp, "str", 1, 0) != null, "갈래가 아닌 칸");
        h.assertTrue(StatTrees.pick(sp, "str", 2, 2) != null, "없는 갈래");
        h.assertTrue(StatTrees.pick(sp, "str", 2, 0) == null, "광전사");
        near(h, StatTrees.bonus(sp, "atk"), 6, "공격력 2 + 4");
        h.assertTrue(StatTrees.pick(sp, "str", 2, 1) != null, "이미 고름");
        h.assertTrue(StatTrees.resetCost(sp, "str") == StatTree.perPick(), "비용 = 갈래 1개");
        String err = StatTrees.reset(sp, "str", false);
        h.assertTrue(err != null && err.contains("모자랍니다"), "에메랄드 없음: " + err);
        h.assertTrue(StatTrees.state(sp).picksOf("str").size() == 1, "못 지웠으면 그대로");
        sp.getInventory().add(new ItemStack(Items.EMERALD, 30));
        h.assertTrue(StatTrees.reset(sp, "str", false) == null, "초기화");
        h.assertTrue(count(sp, Items.EMERALD) == 30 - StatTree.perPick(), "에메랄드 " + StatTree.perPick() + " 냄: " + count(sp, Items.EMERALD));
        h.assertTrue(StatTrees.state(sp).picksOf("str").isEmpty(), "갈래 지움");
        near(h, StatTrees.bonus(sp, "atk"), 2, "보너스 원래대로");
        h.assertTrue(StatTrees.reset(sp, "str", false) != null, "고른 갈래가 없으면 초기화 없음");
        h.assertTrue(StatTrees.pick(sp, "str", 2, 1) == null, "강철 피부");
        near(h, StatTrees.defMult(sp), 0.96, "받는 피해 ×0.96");
        h.assertTrue(StatTrees.state(sp).claimedOf("str") == 3 && count(sp, RuneItems.statTicket("str")) == 1, "보상은 다시 안 줌");
        // 체력 갈래 (최대 체력이 바로 바뀜)
        RpgPlayers.setStat(sp, "vit", 60);
        double before = RpgHealth.maxHpOf(sp);
        h.assertTrue(StatTrees.pick(sp, "vit", 5, 1) == null, "생명의 샘");
        near(h, RpgHealth.maxHpOf(sp) - before, 20, "최대 체력 +20");
        h.assertTrue(Math.abs(sp.getMaxHealth() - RpgHealth.maxHpOf(sp)) < 1e-3, "바닐라 최대 체력도");
        // 명령어: 채팅으로 고르기 (/stattree pick 스탯 단계 갈래 — 1부터) · 요약
        RpgPlayers.setStat(sp, "dex", 30);
        h.getLevel().getServer().getCommands().performPrefixedCommand(sp.createCommandSourceStack(), "stattree pick 민첩 3 2");
        h.assertTrue(Integer.valueOf(1).equals(StatTrees.state(sp).picksOf("dex").get(2)), "명령으로 바람걸음: " + StatTrees.state(sp).picksOf("dex"));
        h.getLevel().getServer().getCommands().performPrefixedCommand(sp.createCommandSourceStack(), "스탯트리 info");
        done(h, sp);
    }

    // ------------------------------------------------------------ 스탯 룬 뽑기권

    @GameTest(maxTicks = 140)
    public void statRuneTickets(GameTestHelper h) {
        for (String stat : StatTree.stats()) {
            TicketItem t = RuneItems.statTicket(stat);
            h.assertTrue(t != null && stat.equals(t.family()) && !t.advanced(), stat + " ticket");
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(t).toString();
            h.assertTrue(id.equals("rpg:" + stat + "_rune_ticket"), "id " + id);
            String name = t.getName(new ItemStack(t)).getString();
            h.assertTrue(name.contains(StatTree.tickets().get(stat).name()), "name " + name);
            h.assertTrue(RuneItems.ticketById(id, 2).getCount() == 2, "ticketById " + id);
        }
        h.assertTrue(RuneItems.ticketById("rpg:rune_ticket", 1).is(RuneItems.TICKET), "ticketById rune");
        ServerPlayer sp = player(h, false); // 뽑기권은 스탯 트리와 상관없이 쓸 수 있음
        TicketItem dex = RuneItems.statTicket("dex");
        sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(dex, 3));
        draw(h, sp, dex, 3);
    }

    /** 뽑기 n 번 (연출 뒤 룬 지급 — 모두 민첩 계열인지) */
    private void draw(GameTestHelper h, ServerPlayer sp, TicketItem t, int left) {
        if (left == 0) {
            int dexRunes = 0;
            int other = 0;
            for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
                ItemStack s = sp.getInventory().getItem(i);
                if (s.getItem() instanceof RuneItem ri) {
                    if (ri.rune().family().equals(t.family())) dexRunes += s.getCount();
                    else other += s.getCount();
                }
            }
            h.assertTrue(dexRunes == 3 && other == 0, "민첩 룬 3 · 다른 계열 0: " + dexRunes + " / " + other);
            h.assertTrue(count(sp, t) == 0, "뽑기권 3장 다 씀 (" + count(sp, t) + ")"); // 마지막 룬은 빈 손 칸에 들어갈 수 있음
            done(h, sp);
            return;
        }
        h.assertTrue(RuneGacha.useTicket(sp, t), "draw " + left);
        h.runAfterDelay(RuneGacha.DRAW_TICKS + 2, () -> draw(h, sp, t, left - 1));
    }

    static Identifier id(String s) {
        return Identifier.parse(s);
    }
}
