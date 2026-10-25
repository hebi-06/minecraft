package mmorpg.fabric.test;

import java.util.List;
import java.util.Map;
import mmorpg.core.Artifacts;
import mmorpg.core.Enhance;
import mmorpg.core.Relics;
import mmorpg.core.Runes;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.artifact.ArtifactSystem;
import mmorpg.fabric.gear.ArtifactItem;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.gear.RelicItem;
import mmorpg.fabric.gear.WeaponEnhance;
import mmorpg.fabric.motion.MotionSystem;
import mmorpg.fabric.relic.RelicPassives;
import mmorpg.fabric.relic.RelicSystem;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.smith.EnhanceWindow;
import mmorpg.fabric.smith.GearEnhance;
import mmorpg.fabric.smith.Smith;
import mmorpg.fabric.stattree.StatTrees;
import mmorpg.fabric.ui.FormNet;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.Icon;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * 0.43.0 아티팩트 게임 시험 (서버): 아이템 50 · 설명, 꽂기 (첫 빈 칸 · 고른 칸 · 꽂힌 것과 바꿈 — 줄은 칸의 것) · 스탯 줄 고르기 · 칸끼리 바꾸기 (줄도 함께) ·
 * 빼기 (줄은 칸에 남음) · 가득 참 · 가방이 참 · 들고 우클릭, 착용 레벨 (꽂지 못함 · 명령어로 넣은 것은 스탯 · 연결 없음 → 레벨이 오르면 켜짐), 이웃 연결 ·
 * 단계 (3 · 5 · 7칸 · 두 계열 · 순풍 이동 속도), 저장 (코덱 왕복), 패시브 열 (두 배의 힘 · 아홉째 밤 · 묶는 끈 · 순풍 · 나팔 · 젊음의 사과 · 공포 · 지혜의 일격 ·
 * 쇠장갑 · 여신의 축복), 드랍 (몬스터 레벨 구간 · 보스 표), 장신구 · 아티팩트 강화 (대장장이 강화 창의 탭 — 판 칸 · 낀 장신구 · 가방, 무기와 같은 확률 · 하락).
 * 난수 (RelicPassives.rand · RelicSystem.rand · GearEnhance.rand) 를 바꾸는 시험은 저마다 하나 (함께 도는 시험끼리 섞이지 않게).
 */
public final class RpgRelicTests {
    private static ItemStack relic(String type, String grade) {
        return new ItemStack(GearItems.byId(Relics.id(type, grade)));
    }

    private static String cellId(ServerPlayer sp, int i) {
        Relics.Relic r = RelicSystem.relic(sp, i);
        return r == null ? "-" : r.type() + "_" + r.grade();
    }

    private static void near(GameTestHelper h, double a, double b, String what) {
        h.assertTrue(Math.abs(a - b) < 1e-6, what + " (" + a + " vs " + b + ")");
    }

    private static ServerPlayer player(GameTestHelper h, int level) {
        ServerPlayer sp = RpgGearTests.player(h);
        sp.setExperienceLevels(level);
        RpgPlayers.checkLevelUp(sp);
        return sp;
    }

    /** 판 칸 cells 에 이 종류 (등급) 를 넣음 (명령어처럼 — 검사 없음) */
    private static void put(ServerPlayer sp, String type, String grade, int... cells) {
        for (int c : cells) RelicSystem.set(sp, c, relic(type, grade), null);
    }

    private static Husk dummy(GameTestHelper h, float x, float z) {
        return TestDummies.make(h.spawnWithNoFreeWill(EntityTypes.HUSK, x, 2F, z));
    }

    /** 무기를 쓸 수 있게 요구 스탯을 맞춤 (주 스탯은 10 이상) */
    private static void meetReqs(ServerPlayer sp, Weapon w) {
        for (Weapons.Req r : w.reqs()) RpgPlayers.setStat(sp, r.stat(), (int) Math.max(Math.ceil(r.value()), r.stat().equals(w.stat()) ? 10 : 0));
    }

    private static void floor(GameTestHelper h) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
    }

    /** 아이템 50: 등록 · 이름 · 설명 (별칭 · 계열 · 패시브 · 스탯 줄) · 하나씩 · 초월만 반짝임 */
    @GameTest(maxTicks = 20)
    public void items(GameTestHelper h) {
        h.assertTrue(Relics.enabled() && Relics.all().size() == 50 && Relics.types().size() == 10, "10 types × 5 grades");
        for (Relics.Relic r : Relics.all()) {
            Item it = GearItems.byId(r.id());
            h.assertTrue(it instanceof RelicItem ri && ri.relic() == r, "registered " + r.id());
            ItemStack st = new ItemStack(it);
            h.assertTrue(st.getMaxStackSize() == 1, "stack 1 " + r.id());
            String shown = net.minecraft.ChatFormatting.stripFormatting(st.getHoverName().getString());
            h.assertTrue(r.name().equals(shown), "name " + shown + " vs " + r.name());
            h.assertTrue(st.hasFoil() == r.grade().equals("transcend"), "glint " + r.id());
        }
        List<String> lore = Relics.lore(Relics.relic("draupnir", "hero"), 0, 50);
        String all = String.join(" / ", lore);
        h.assertTrue(all.contains("아티팩트") && all.contains("아홉째 밤") && all.contains("스탯 줄") && all.contains("영웅") && all.contains("착용 40렙"), "lore: " + all);
        h.assertTrue(RelicItem.of(new ItemStack(Icon.itemById("rpg:loot_troll"))) == null && ArtifactItem.of(relic("idunn", "rare")) == null, "not a relic / not an accessory");
        h.assertTrue(mmorpg.fabric.container.ContainerTools.grade(relic("gleipnir", "noble")) == 2, "grade under for the RPG window");
        h.succeed();
    }

    /** 꽂기 · 스탯 줄 · 바꾸기 · 빼기 · 가득 참 · 가방이 참 · 들고 우클릭 */
    @GameTest(maxTicks = 20)
    public void equipMoveStat(GameTestHelper h) {
        ServerPlayer sp = player(h, 50);
        h.assertFalse(StatTrees.isActive(sp), "stat tree off for test players (relics still count)");
        h.assertTrue(RelicSystem.totals(sp).isEmpty() && RelicSystem.SIZE == 9, "empty board of 9");
        sp.getInventory().setItem(9, relic("megingjord", "transcend"));
        h.assertTrue(RelicSystem.equip(sp, 9, -1) == null && cellId(sp, 0).equals("megingjord_transcend"), "first empty cell");
        h.assertTrue(sp.getInventory().getItem(9).isEmpty(), "left the bag");
        h.assertTrue(RelicSystem.stat(sp, 0).equals(Relics.defaultLine()), "default line " + RelicSystem.stat(sp, 0));
        near(h, RelicSystem.totals(sp).get("atk"), 1.5, "transcend atk line");
        near(h, StatTrees.atkMult(sp), 1.015, "atkMult");
        // 줄 바꾸기 (치명타 확률)
        h.assertTrue(RelicSystem.setStat(sp, 0, "crit") == null && RelicSystem.stat(sp, 0).equals("crit"), "pick crit");
        h.assertTrue(RelicSystem.setStat(sp, 0, "nope") != null, "unknown line refused");
        near(h, RelicSystem.totals(sp).get("crit"), 1.5, "crit line");
        h.assertFalse(RelicSystem.totals(sp).containsKey("atk"), "atk gone");
        near(h, StatTrees.critAdd(sp), 0.015, "critAdd");
        // 칸끼리 바꾸기: 아이템과 줄이 함께
        h.assertTrue(RelicSystem.move(sp, 0, 4) == null && cellId(sp, 4).equals("megingjord_transcend") && RelicSystem.stat(sp, 4).equals("crit")
                && cellId(sp, 0).equals("-") && RelicSystem.stat(sp, 0).equals(Relics.defaultLine()), "move keeps the line with the relic");
        // 꽂힌 칸에 꽂기 → 바꿈 (꽂혀 있던 것은 그 가방 칸으로), 줄은 칸의 것 (치명타)
        sp.getInventory().setItem(10, relic("skidbladnir", "rare"));
        h.assertTrue(RelicSystem.equip(sp, 10, 4) == null && cellId(sp, 4).equals("skidbladnir_rare"), "swap into cell 5");
        h.assertTrue(RelicItem.of(sp.getInventory().getItem(10)) == Relics.relic("megingjord", "transcend"), "old one back in bag slot 10");
        near(h, RelicSystem.totals(sp).get("crit"), 0.6, "rare crit line (the cell's line)");
        // 빼기: 가방으로, 줄은 칸에 남음
        h.assertTrue(RelicSystem.unequip(sp, 4) == null && cellId(sp, 4).equals("-") && RelicSystem.stat(sp, 4).equals("crit"), "unequip keeps the line");
        h.assertTrue(RelicSystem.totals(sp).isEmpty(), "nothing left");
        // 들고 우클릭: 첫 빈 칸
        sp.getInventory().setItem(sp.getInventory().getSelectedSlot(), relic("idunn", "hero"));
        h.assertTrue(RelicSystem.equipHeld(sp, InteractionHand.MAIN_HAND) == null && cellId(sp, 0).equals("idunn_hero") && sp.getMainHandItem().isEmpty(), "right click");
        // 가득 참 → 첫 빈 칸이 없음
        for (int i = 1; i < RelicSystem.SIZE; i++) RelicSystem.set(sp, i, relic("mimir", "common"), null);
        sp.getInventory().setItem(11, relic("gleipnir", "common"));
        h.assertTrue(RelicSystem.equip(sp, 11, -1) != null && !sp.getInventory().getItem(11).isEmpty(), "board full: stays in the bag");
        h.assertTrue(RelicSystem.equip(sp, 11, 3) == null && cellId(sp, 3).equals("gleipnir_common"), "but a chosen cell swaps");
        // 가방이 차면 빼지 않음
        for (int i = 0; i < 36; i++) if (sp.getInventory().getItem(i).isEmpty()) sp.getInventory().setItem(i, new ItemStack(net.minecraft.world.item.Items.DIRT));
        h.assertTrue(RelicSystem.unequip(sp, 0) != null && cellId(sp, 0).equals("idunn_hero"), "bag full: stays");
        sp.getInventory().clearContent();
        // 장신구는 판에 꽂지 못함
        sp.getInventory().setItem(12, new ItemStack(GearItems.byId(Artifacts.id("ring", "rare"))));
        h.assertTrue(RelicSystem.equip(sp, 12, 2) != null && cellId(sp, 2).equals("mimir_common"), "accessory refused");
        RpgGearTests.done(h, sp);
    }

    /** 착용 레벨: 모자라면 꽂지 못함, 명령어로 넣은 것은 스탯 · 연결 · 패시브 없음 → 레벨이 오르면 켜짐 */
    @GameTest(maxTicks = 20)
    public void levelGate(GameTestHelper h) {
        ServerPlayer sp = player(h, 15);
        sp.getInventory().setItem(9, relic("aegishjalmr", "noble"));
        h.assertTrue(RelicSystem.equip(sp, 9, -1) != null && cellId(sp, 0).equals("-") && !sp.getInventory().getItem(9).isEmpty(), "noble (30) at 15: no");
        put(sp, "aegishjalmr", "noble", 0, 1, 2);
        Relics.Board b = RelicSystem.board(sp);
        h.assertTrue(!b.usable()[0] && b.links().isEmpty() && RelicSystem.totals(sp).isEmpty() && RelicSystem.tier(sp, "aegishjalmr") == 0, "unusable: nothing");
        sp.setExperienceLevels(30);
        RpgPlayers.checkLevelUp(sp);
        b = RelicSystem.board(sp);
        h.assertTrue(b.usable()[0] && b.links().size() == 4 && RelicSystem.tier(sp, "aegishjalmr") == 1, "30: usable · linked (힘 + 체력 — 2 × 2) · tier 1 " + b.links());
        near(h, RelicSystem.totals(sp).get("atk"), 0.9 * 3, "three noble atk lines");
        RpgGearTests.done(h, sp);
    }

    /** 이웃 연결 · 단계: 힘 3칸 → 1단계, 5칸 → 2단계 (두 계열이면 큰 쪽), 7칸 → 3단계 · 순풍 이동 속도 (판 합) */
    @GameTest(maxTicks = 20)
    public void linksAndTiers(GameTestHelper h) {
        ServerPlayer sp = player(h, 50);
        // 0 1 2 / 3 4 5 / 6 7 8
        put(sp, "megingjord", "common", 0, 1, 2);
        h.assertTrue(RelicSystem.tier(sp, "megingjord") == 1, "힘 3 → 1단계");
        put(sp, "jarngreipr", "common", 3, 4);
        h.assertTrue(RelicSystem.tier(sp, "megingjord") == 2 && RelicSystem.tier(sp, "jarngreipr") == 2, "힘 5 → 2단계 (both)");
        put(sp, "skidbladnir", "common", 5);
        h.assertTrue(RelicSystem.tier(sp, "skidbladnir") == 1 && RelicSystem.tier(sp, "jarngreipr") == 2, "민첩 3 (3 · 4 · 5) → 순풍 1단계, 쇠장갑은 큰 쪽 2");
        double speedLine = Relics.type("skidbladnir").passive().value("speed", 1);
        near(h, RelicSystem.totals(sp).getOrDefault("speed", 0.0), speedLine, "순풍 이동 속도 (패시브 몫)");
        AttributeModifier mod = sp.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(Mmorpg.id("stat_tree_speed"));
        h.assertTrue(mod != null && Math.abs(mod.amount() - speedLine / 100) < 1e-9, "speed modifier " + (mod == null ? null : mod.amount()));
        put(sp, "aegishjalmr", "common", 6, 7);
        h.assertTrue(RelicSystem.tier(sp, "megingjord") == 3 && RelicSystem.tier(sp, "aegishjalmr") == 3, "힘 7 → 3단계");
        Relics.Board b = RelicSystem.board(sp);
        h.assertTrue(b.active() == 4 && b.filled() == 8, "four passives on · 8 filled (" + b.tiers() + ")");
        // 칸 하나를 빼면 다시 셈 (4번 칸을 빼도 1 → 4 → 7 → 6 으로 이어져 힘 6칸 → 2단계)
        h.assertTrue(RelicSystem.unequip(sp, 3) == null, "unequip 4");
        h.assertTrue(RelicSystem.tier(sp, "megingjord") == 2 && RelicSystem.tier(sp, "aegishjalmr") == 2, "힘 0 1 2 4 6 7 = 6칸 → 2단계 (" + RelicSystem.board(sp).groups() + ")");
        h.assertTrue(RelicSystem.unequip(sp, 4) == null, "unequip 5");
        h.assertTrue(RelicSystem.tier(sp, "megingjord") == 1 && RelicSystem.tier(sp, "aegishjalmr") == 0, "힘 0 1 2 · 6 7 → 1단계 · 꺼짐 (" + RelicSystem.board(sp).groups() + ")");
        RpgGearTests.done(h, sp);
    }

    /** 저장: 칸 아홉 (아이템 · 줄 — 코덱 왕복) · 죽어도 남음 */
    @GameTest(maxTicks = 20)
    public void persistence(GameTestHelper h) {
        ServerPlayer sp = player(h, 50);
        RelicSystem.set(sp, 0, relic("brisingamen", "hero"), "hp");
        ItemStack en = relic("draupnir", "transcend");
        WeaponEnhance.write(en, 7);
        RelicSystem.set(sp, 8, en, "critDmg");
        RelicSystem.setStat(sp, 4, "speed");
        var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag tag = RelicSystem.Data.CODEC.encodeStart(ops, sp.getAttached(RelicSystem.TYPE)).getOrThrow();
        RelicSystem.Data back = RelicSystem.Data.CODEC.parse(ops, tag).getOrThrow();
        h.assertTrue(back.cells().size() == RelicSystem.SIZE, "9 cells");
        h.assertTrue(RelicItem.of(back.cells().get(0).item()) == Relics.relic("brisingamen", "hero") && back.cells().get(0).stat().equals("hp"), "cell 1");
        h.assertTrue(back.cells().get(4).item().isEmpty() && back.cells().get(4).stat().equals("speed"), "empty cell keeps its line");
        h.assertTrue(RelicItem.of(back.cells().get(8).item()) == Relics.relic("draupnir", "transcend") && WeaponEnhance.of(back.cells().get(8).item()) == 7
                && back.cells().get(8).stat().equals("critDmg"), "cell 9 with +7");
        h.assertTrue(RelicSystem.TYPE.copyOnDeath(), "kept on death");
        RpgGearTests.done(h, sp);
    }

    /** 「두 배의 힘」 (평타 다섯째 × 1.2) · 「쇠장갑」 (스킬 +5%) · 「순풍」 (구른 뒤 +4%) · 「공포」 (가까운 적 −5%) · 「여신의 축복」 (룬 → 보호막 3%) */
    @GameTest(maxTicks = 80)
    public void passivesA(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h, 50);
        Weapon w = Weapons.weaponFrom("sword", 0, "common");
        meetReqs(sp, w);
        sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RpgItems.byId(w.id())));
        Husk z = dummy(h, 1.5F, 3.5F);
        Husk far = dummy(h, 6.5F, 6.5F);
        // 두 배의 힘 1단계 (힘 3칸)
        put(sp, "megingjord", "common", 0, 1, 2);
        RelicPassives.testReset(sp);
        double[] d = new double[5];
        RpgCombat.forceCrit = false;
        try {
            for (int i = 0; i < 5; i++) {
                d[i] = RpgCombat.meleeDamage(sp, z, 1F, 1F);
                RpgCombat.dropMark(z, sp);
            }
        } finally {
            RpgCombat.forceCrit = null;
        }
        h.assertTrue(d[0] > 0 && Math.abs(d[1] - d[0]) < 1e-4 && Math.abs(d[3] - d[0]) < 1e-4, "hits 1 ~ 4 plain " + java.util.Arrays.toString(d));
        h.assertTrue(Math.abs(d[4] / d[0] - 1.2) < 1e-4, "5th hit × 1.2 " + java.util.Arrays.toString(d));
        h.assertTrue(RelicPassives.meginCount(sp) == 0, "count restarts");
        // 쇠장갑 1단계 (힘 · 민첩 3칸 — 3 · 4 · 5)
        put(sp, "jarngreipr", "common", 3, 4, 5);
        near(h, Kit.skillMult.applyAsDouble(sp), 1 + Relics.type("jarngreipr").passive().value("skill", RelicSystem.tier(sp, "jarngreipr")) / 100, "skill mult");
        // 순풍 1단계 (민첩 3칸 — 6 · 7 · 8, 쇠장갑 줄과 이어져 민첩 6칸 → 2단계)
        put(sp, "skidbladnir", "common", 6, 7, 8);
        int tw = RelicSystem.tier(sp, "skidbladnir");
        h.assertTrue(tw == 2, "민첩 3 · 4 · 5 · 6 · 7 · 8 = 6칸 → 2단계 (" + tw + ")");
        near(h, RpgCombat.dealtMult.applyAsDouble(sp), 1, "no roll yet");
        MotionSystem.onRoll.accept(sp);
        near(h, RpgCombat.dealtMult.applyAsDouble(sp), 1 + Relics.type("skidbladnir").passive().value("dealt", tw) / 100, "after a roll");
        // 공포 (힘 · 체력 — 힘 줄에 이어 붙임: 메긴교르드 0 · 1 · 2 위에는 칸이 없으니 3 · 4 · 5 를 바꿈)
        put(sp, "aegishjalmr", "common", 3, 4, 5);
        int tf = RelicSystem.tier(sp, "aegishjalmr");
        h.assertTrue(tf >= 1, "공포 on (" + tf + ")");
        DamageSource near = z.damageSources().mobAttack(z);
        DamageSource away = far.damageSources().mobAttack(far);
        near(h, RpgHealth.sourceReduce.applyAsDouble(sp, near), Relics.type("aegishjalmr").passive().value("cut", tf) / 100, "near foe −cut");
        near(h, RpgHealth.sourceReduce.applyAsDouble(sp, away), 0, "far foe: nothing");
        // 여신의 축복 (체력 · 마력 3칸 — 6 · 7 · 8 을 바꿈) → 룬을 쓰면 보호막
        put(sp, "brisingamen", "common", 6, 7, 8);
        int tb = RelicSystem.tier(sp, "brisingamen");
        h.assertTrue(tb >= 1, "축복 on");
        double before = RpgHealth.shieldOf(sp);
        RuneCast.onCast.accept(sp, Runes.parseRune("rpg:rune_str_common"));
        near(h, RpgHealth.shieldOf(sp) - before, RpgHealth.maxOf(sp) * Relics.type("brisingamen").passive().value("shield", tb) / 100, "shield");
        RelicPassives.testReset(sp);
        RpgGearTests.done(h, sp);
    }

    /** 「아홉째 밤」 (아홉째 맞힘 → 금고리) · 「묶는 끈」 (묶음 · 느림 · 받는 피해 +3%) · 「젊음의 사과」 (35% 아래로 → 10% 회복, 한 번) */
    @GameTest(maxTicks = 120)
    public void passivesB(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h, 50);
        Weapon w = Weapons.weaponFrom("sword", 0, "common");
        meetReqs(sp, w);
        sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(RpgItems.byId(w.id())));
        Husk z = dummy(h, 1.5F, 4.5F);
        Husk z2 = dummy(h, 5.5F, 1.5F);
        put(sp, "draupnir", "common", 0, 1, 2);
        RelicPassives.testReset(sp);
        for (int i = 0; i < 8; i++) RpgCombat.weaponHit(sp, z, w, null);
        h.assertTrue(RelicPassives.draupCount(sp) == 8, "eight counted (" + RelicPassives.draupCount(sp) + ")");
        double before = TestDummies.dealt(z);
        RpgCombat.weaponHit(sp, z, w, null);
        h.assertTrue(RelicPassives.draupCount(sp) == 0, "ninth → rings");
        // 묶는 끈 (민첩 · 마력 3칸) → 다른 적
        put(sp, "gleipnir", "common", 3, 4, 5);
        int tg = RelicSystem.tier(sp, "gleipnir");
        RpgCombat.weaponHit(sp, z2, w, null);
        h.assertTrue(RelicPassives.bound(z2) && z2.hasEffect(MobEffects.SLOWNESS), "bound · slowed");
        near(h, RpgCombat.takenMult.applyAsDouble(z2), 1 + Relics.type("gleipnir").passive().value("taken", tg) / 100, "taken +");
        String mark = DamageNumbers.victimMark.apply(z2);
        h.assertTrue(mark.contains("∞"), "number mark " + mark);
        RelicPassives.testUnbind(z2);
        near(h, RpgCombat.takenMult.applyAsDouble(z2), 1, "unbound");
        // 젊음의 사과 (체력 3칸)
        put(sp, "idunn", "common", 6, 7, 8);
        int ti = RelicSystem.tier(sp, "idunn");
        double max = RpgHealth.maxOf(sp);
        sp.setHealth((float) (max * 0.3));
        RpgHealth.lossHook.lost(sp, sp.damageSources().generic(), max * 0.2, max * 0.5, max * 0.3);
        double heal = Relics.type("idunn").passive().value("heal", ti) / 100;
        h.assertTrue(Math.abs(sp.getHealth() - max * (0.3 + heal)) < 1e-3, "apple heal " + sp.getHealth() + " vs " + max * (0.3 + heal));
        float hp1 = sp.getHealth();
        sp.setHealth((float) (max * 0.3));
        RpgHealth.lossHook.lost(sp, sp.damageSources().generic(), max * 0.2, max * 0.5, max * 0.3);
        h.assertTrue(Math.abs(sp.getHealth() - max * 0.3) < 1e-3 && hp1 > sp.getHealth(), "cooldown: once");
        // 금고리가 떨어지면 (8개 × 2틱 + 2) 적이 피해를 받음
        h.runAfterDelay(30, () -> {
            h.assertTrue(TestDummies.dealt(z) > before, "rings hit (" + before + " → " + TestDummies.dealt(z) + ")");
            RelicPassives.testReset(sp);
            RpgGearTests.done(h, sp);
        });
    }

    /** 「파수꾼의 나팔」: 적을 때리면 울림 → 나와 가까운 아군 가하는 피해 + · 받는 피해 − (그 자리에서 보고 지움 — 다른 시험에 새지 않게) */
    @GameTest(maxTicks = 20)
    public void passiveHorn(GameTestHelper h) {
        ServerPlayer sp = player(h, 50);
        ServerPlayer ally = player(h, 10);
        Husk z = dummy(h, 1.5F, 3.5F);
        put(sp, "gjallarhorn", "common", 0, 1, 2);
        int t = RelicSystem.tier(sp, "gjallarhorn");
        h.assertTrue(t == 1, "horn tier 1");
        RelicPassives.testReset(sp);
        RelicPassives.testReset(ally);
        double dealt = Relics.type("gjallarhorn").passive().value("dealt", t) / 100;
        double cut = Relics.type("gjallarhorn").passive().value("cut", t) / 100;
        try {
            DamageNumbers.dealtHook.dealt(sp, z, 10);
            near(h, RelicPassives.hornDealt(sp), dealt, "me");
            near(h, RelicPassives.hornDealt(ally), dealt, "ally");
            near(h, RpgCombat.dealtMult.applyAsDouble(ally), 1 + dealt, "ally dealt mult");
            h.assertTrue(RpgHealth.buffReduce.applyAsDouble(ally) >= cut - 1e-9, "ally cut " + RpgHealth.buffReduce.applyAsDouble(ally));
        } finally {
            // 나팔은 둘레 12칸 모든 플레이어에게 — 함께 도는 다른 시험의 플레이어에게 남지 않게 같은 틱에 지움
            for (ServerPlayer o : h.getLevel().players()) if (o.distanceTo(sp) <= 13) RelicPassives.testClearHorn(o);
            RelicPassives.testReset(sp);
            RelicPassives.testReset(ally);
        }
        h.getLevel().getServer().getPlayerList().remove(ally);
        RpgGearTests.done(h, sp);
    }

    /** 「지혜의 일격」: 룬 피해 확률 (rand 0 → × 1.5, 0.99 → 그대로) */
    @GameTest(maxTicks = 20)
    public void passiveWisdom(GameTestHelper h) {
        floor(h);
        ServerPlayer sp = player(h, 50);
        Husk z = dummy(h, 1.5F, 3.5F);
        put(sp, "mimir", "common", 0, 1, 2);
        int t = RelicSystem.tier(sp, "mimir");
        try {
            RelicPassives.rand = () -> 0.99;
            double a0 = TestDummies.dealt(z);
            RuneKit.runeHit(sp, z, 10, 1, "int");
            double plain = TestDummies.dealt(z) - a0;
            z.setInvulnerableTime(0);
            z.damageCooldownTime = 0;
            RelicPassives.rand = () -> 0.0;
            double a1 = TestDummies.dealt(z);
            RuneKit.runeHit(sp, z, 10, 1, "int");
            double wise = TestDummies.dealt(z) - a1;
            h.assertTrue(plain > 0 && Math.abs(wise / plain - Relics.type("mimir").passive().value("mult", t)) < 1e-3, "wisdom × mult (" + plain + " → " + wise + ")");
        } finally {
            RelicPassives.rand = Math::random;
        }
        RpgGearTests.done(h, sp);
    }

    /** 드랍: 몬스터 레벨 구간 (4렙 아래 없음 · 25렙 희귀) · 보스 표 (크라켄 희귀 하나 · 헬 초월 둘) */
    @GameTest(maxTicks = 20)
    public void drops(GameTestHelper h) {
        ServerPlayer sp = player(h, 10);
        sp.getInventory().clearContent();
        try {
            RelicSystem.rand = () -> 0.0;
            RelicSystem.dropForMonster(sp, 2, "새끼 멧돼지");
            h.assertTrue(RpgGearTests.countIn(sp, GearItems.byId(Relics.id("megingjord", "common"))) == 0, "level 2: nothing");
            RelicSystem.dropForMonster(sp, 25, "트롤");
            h.assertTrue(RpgGearTests.countIn(sp, GearItems.byId(Relics.id("megingjord", "rare"))) == 1, "level 25: rare (type = first at r 0)");
            List<String> kraken = RelicSystem.dropForBoss(sp, "kraken");
            h.assertTrue(kraken.size() == 1 && kraken.get(0).contains("메긴교르드"), "kraken: one rare " + kraken);
            List<String> hel = RelicSystem.dropForBoss(sp, "hel");
            h.assertTrue(hel.size() == 2 && RpgGearTests.countIn(sp, GearItems.byId(Relics.id("megingjord", "transcend"))) == 2, "hel: two transcend " + hel);
            RelicSystem.rand = () -> 0.99;
            RelicSystem.dropForMonster(sp, 25, "트롤");
            h.assertTrue(RpgGearTests.countIn(sp, GearItems.byId(Relics.id("megingjord", "rare"))) == 2 - 0, "unlucky roll: still 2 rare (kraken + level 25)");
            h.assertTrue(RelicSystem.dropForBoss(sp, "nobody").isEmpty(), "unknown boss: nothing");
        } finally {
            RelicSystem.rand = Math::random;
        }
        RpgGearTests.done(h, sp);
    }

    // ------------------------------------------------------------ 장신구 · 아티팩트 강화 (대장장이 강화 창의 탭)

    private static FormNet.ChestPayload chest(ServerPlayer sp) {
        return Forms.last(sp) instanceof FormNet.ChestPayload c ? c : null;
    }

    private static String body(ServerPlayer sp) {
        FormNet.ChestPayload c = chest(sp);
        return c == null ? "(창 없음)" : c.body();
    }

    /** 탭 · 칸 배치 (판 · 낀 장신구 · 가방) · 판 칸 강화 (빼지 않고 — 줄 그대로, 합계 × 1.04) · 낀 장신구 강화 · 실패 하락 (+5 → +4) · 재료 */
    @GameTest(maxTicks = 300)
    public void gearEnhance(GameTestHelper h) {
        ServerPlayer sp = player(h, 50);
        Forms.HEADLESS.add(sp.getUUID());
        Item stone = GearItems.byId("rpg:enhance_stone");
        Item bright = GearItems.byId("rpg:bright_stone");
        sp.getInventory().setItem(20, new ItemStack(stone, 64));
        sp.getInventory().setItem(21, new ItemStack(bright, 10));
        RelicSystem.set(sp, 2, relic("brisingamen", "common"), "hp");
        ArtifactSystem.set(sp, 0, new ItemStack(GearItems.byId(Artifacts.id("ring", "common"))));
        sp.getInventory().setItem(9, relic("gleipnir", "rare"));
        double hp0 = RelicSystem.totals(sp).get("hp");
        Smith.smithLoop(sp, "enhance", null);
        FormNet.ChestPayload c = chest(sp);
        h.assertTrue(c != null && c.title().contains("무기 강화") && c.slots().get(1).name().contains("장신구 · 아티팩트"), "weapon window has the gear tab");
        h.assertTrue(Forms.answer(sp, 1, null), "open gear tab");
        c = chest(sp);
        h.assertTrue(c != null && c.title().contains("장신구 · 아티팩트 강화") && c.slots().size() == 54, "gear window (6 rows) " + (c == null ? null : c.title()));
        h.assertTrue(c.slots().get(9 + 2).icon().item().is(GearItems.byId(Relics.id("brisingamen", "common"))), "board cell 3 in row 2");
        h.assertTrue(c.slots().get(18).icon().item().is(GearItems.byId(Artifacts.id("ring", "common"))), "ring 1 in row 3");
        h.assertTrue(c.slots().get(27).icon().item().is(GearItems.byId(Relics.id("gleipnir", "rare"))), "bag relic in row 4");
        // 판 칸 +0 → +1 성공
        h.assertTrue(Forms.answer(sp, 11, null), "pick board cell");
        h.assertTrue(chest(sp) != null && chest(sp).title().contains("아티팩트 강화") && chest(sp).title().contains("+0 → +1"), "confirm " + chest(sp).title());
        GearEnhance.rand = () -> 0.0;
        h.assertTrue(Forms.answer(sp, 11, null), "go");
        h.assertTrue(Forms.last(sp) == null && Forms.isOpen(sp), "hammering");
        h.runAfterDelay(EnhanceWindow.ANIM_TICKS + 3, () -> {
            h.assertTrue(body(sp).contains("성공"), "success " + body(sp));
            h.assertTrue(WeaponEnhance.of(RelicSystem.get(sp, 2)) == 1 && RelicSystem.stat(sp, 2).equals("hp"), "board cell +1, line kept");
            near(h, RelicSystem.totals(sp).get("hp"), hp0 * (1 + Enhance.gearBonus(1)), "line × 1.04");
            h.assertTrue(RpgGearTests.countIn(sp, stone) == 63, "one stone used");
            // 낀 장신구 +5 → +6 실패: 하락 (+4)
            ItemStack ring = ArtifactSystem.get(sp, 0).copy();
            WeaponEnhance.write(ring, 5);
            ArtifactSystem.set(sp, 0, ring);
            h.assertTrue(Forms.answer(sp, 2, null), "refresh (stone slot)");
            h.assertTrue(chest(sp).slots().get(18).name().contains("+5"), "ring shows +5");
            h.assertTrue(Forms.answer(sp, 18, null), "pick ring");
            GearEnhance.rand = () -> 0.99;
            h.assertTrue(Forms.answer(sp, 11, null), "go");
            h.runAfterDelay(EnhanceWindow.ANIM_TICKS + 3, () -> {
                h.assertTrue(body(sp).contains("떨어졌습니다"), "drop " + body(sp));
                h.assertTrue(WeaponEnhance.of(ArtifactSystem.get(sp, 0)) == 4, "ring +4 (" + WeaponEnhance.of(ArtifactSystem.get(sp, 0)) + ")");
                near(h, ArtifactSystem.totals(sp).getOrDefault("atk", 0.0), Artifacts.art("ring", "common").bonus().get("atk") * (1 + Enhance.gearBonus(4)), "ring option × (1 + bonus 4)");
                GearEnhance.rand = Math::random;
                Forms.answer(sp, -1, null);
                Forms.HEADLESS.remove(sp.getUUID());
                RpgGearTests.done(h, sp);
            });
        });
    }

    /** 장신구 옵션 강화 배율 (core) — 서버 합계와 같음 */
    @GameTest(maxTicks = 20)
    public void accessoryEnhanceTotals(GameTestHelper h) {
        ServerPlayer sp = player(h, 50);
        ItemStack ring = new ItemStack(GearItems.byId(Artifacts.id("ring", "transcend")));
        WeaponEnhance.write(ring, 10);
        ArtifactSystem.set(sp, 0, ring);
        Map<String, Double> t = ArtifactSystem.totals(sp);
        for (Map.Entry<String, Double> e : Artifacts.art("ring", "transcend").bonus().entrySet()) {
            near(h, t.get(e.getKey()), e.getValue() * 1.5, "+10 → × 1.5 " + e.getKey());
        }
        h.assertTrue(ring.getHoverName().getString().contains("+10"), "name +10: " + ring.getHoverName().getString());
        RpgGearTests.done(h, sp);
    }
}
