package mmorpg.fabric.vanilla;

import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.SimpleGuiElement;
import eu.pb4.sgui.api.gui.SimpleGui;
import mmorpg.core.Js;
import mmorpg.core.Stats;
import mmorpg.core.Weapon;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgNet;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.RpgWeaponItem;
import mmorpg.fabric.StatSheet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 4b: 모드 없는 클라이언트의 스탯 창 (모드는 StatScreen — 같은 그림 · 같은 일). 6줄 상자 메뉴 + 가방 자리까지 이 창의 칸 (sgui), 바탕은 그림 글꼴
 * (VanillaGui · tools/vanilla_gui.py stats.png — 남색 바탕 · 금테, 스탯 줄 · 단추 틀):
 * <pre>
 * 스탯                                 남은 포인트 15
 * [힘]  힘 120 +5            +24%   [트리] [+]
 * [민첩] …                                    (줄 1 ~ 3)
 * [무기] 무기 이름                     [아티팩트] [유물]
 *       피해 줄                        [장터] [쓰레기통]
 * [퀘스트 ··········] [창고 ············]   (가방 자리 4줄 × 단추 둘)
 * </pre>
 * + 단추: 좌클릭 +1 · Shift + 좌클릭 +5 (사용자 지정). 스탯 그림 · 트리 단추 = 그 스탯의 스탯 트리. 바뀌면 같은 메뉴를 다시 그림 (제목 · 칸).
 */
public final class VanillaStats {
    private VanillaStats() {}

    /** 스탯 창 (RpgNet.openStats — 모드 없는 클라이언트) */
    public static void open(ServerPlayer sp) {
        if (sp == null || sp.isRemoved()) return;
        StatsGui gui = new StatsGui(sp);
        gui.refresh();
        gui.open();
    }

    /** 칸 번호 (줄 r · 칸 c — 줄 6 ~ 8 가방 자리, 9 핫바 자리) */
    static int idx(int r, int c) {
        return r < 6 ? r * 9 + c : r < 9 ? 54 + (r - 6) * 9 + c : 81 + c;
    }

    private record Button(String key, String label, ItemStack icon, String tip, boolean on, Consumer<ServerPlayer> run) {}

    private static final class StatsGui extends SimpleGui {
        private final ServerPlayer sp;

        StatsGui(ServerPlayer sp) {
            super(MenuType.GENERIC_9x6, sp, true);
            this.sp = sp;
        }

        void refresh() {
            for (int i = 0; i < 90; i++) clearSlot(i);
            StatSheet s = StatSheet.of(sp);
            int point = s.point();
            VanillaGui.Title t = new VanillaGui.Title().background(VanillaGui.BG_STATS);
            t.text(10, 0, "스탯", VanillaGui.GOLD);
            t.right(168, 0, String.valueOf(point), point > 0 ? VanillaGui.GOLD : VanillaGui.DARK);
            t.right(168 - VanillaGui.width(String.valueOf(point)) - 4, 0, "남은 포인트", VanillaGui.GRAY);

            List<String> keys = Stats.STAT_KEYS;
            for (int i = 0; i < 4 && i < keys.size(); i++) statRow(t, s, keys.get(i), i, point);
            weaponRows(t, s);
            bottom(t);
            setTitle(t.build());
        }

        private void statRow(VanillaGui.Title t, StatSheet s, String k, int r, int point) {
            mmorpg.core.Obj info = Stats.info(k);
            String name = info.str("name");
            int color = VanillaGui.legacyColor(info.str("color"), VanillaGui.WHITE);
            int line = VanillaGui.rowLine(r);
            int value = s.stat(k);
            double eff = Stats.get(s.eff(), k);
            int bonus = (int) Math.round(eff - value);
            List<Object[]> parts = new ArrayList<>();
            parts.add(new Object[]{name + " ", color});
            parts.add(new Object[]{String.valueOf(value), VanillaGui.WHITE});
            if (bonus > 0) parts.add(new Object[]{"+" + bonus, VanillaGui.GREEN});
            t.parts(27, line, parts);
            String effect = k.equals("vit") ? "♥" + Js.str(Math.round(s.maxHp())) : "+" + pct(eff * Stats.damagePerPoint(k) * 100) + "%";
            t.right(130, line, effect, k.equals("vit") ? VanillaGui.RED : VanillaGui.GRAY);

            List<String> lore = new ArrayList<>(s.tooltip(k));
            if (!lore.isEmpty()) lore.remove(lore.size() - 1); // 모드 창의 "눌러서 +1 (Shift: +5)" 줄
            List<String> tip = new ArrayList<>(lore);
            tip.add("");
            tip.add(point > 0 ? "§a좌클릭 +1 §7· §aShift + 좌클릭 +5 §7(오른쪽 + 단추)" : "§8남은 포인트가 없습니다");
            tip.add("§b트리 단추: " + name + " 스탯 트리");
            ItemStack icon = VanillaGui.ui("icon_" + k, colorCode(info.str("color")) + name + " §f" + value + (bonus > 0 ? " §a+" + bonus : ""), tip);
            setSlot(idx(r, 0), new SimpleGuiElement(icon, (i, type, a, g) -> plus(k, type)));
            ItemStack tree = VanillaGui.named(new ItemStack(Items.OAK_SAPLING), "§a" + name + " 스탯 트리", List.of("§7스탯마다 받은 칸 · 고를 갈래"));
            setSlot(idx(r, 7), new SimpleGuiElement(tree, (i, type, a, g) -> {
                if (type.isLeft) mmorpg.fabric.stattree.StatTrees.open(sp, k);
            }));
            ItemStack plus = VanillaGui.ui(point > 0 ? "plus_on" : "plus_off", point > 0 ? "§a" + name + " +1" : "§8" + name + " +1",
                    List.of(point > 0 ? "§7좌클릭 +1 · Shift + 좌클릭 +5" : "§8남은 포인트가 없습니다", "§7남은 포인트 §f" + point));
            setSlot(idx(r, 8), new SimpleGuiElement(plus, (i, type, a, g) -> plus(k, type)));
        }

        /** + 단추 · 스탯 그림: 좌클릭 +1, Shift + 좌클릭 +5 */
        private void plus(String k, ClickType type) {
            if (!type.isLeft) return;
            RpgPlayers.spend(sp, k, type.shift ? 5 : 1);
            refresh();
        }

        private void weaponRows(VanillaGui.Title t, StatSheet s) {
            ItemStack main = sp.getMainHandItem();
            Weapon w = StatSheet.referenceWeapon(main, sp.getOffhandItem(), sp.getInventory().getNonEquipmentItems(), s::usable);
            int line1 = VanillaGui.rowLine(4);
            int line2 = VanillaGui.rowLine(5);
            if (w == null) {
                t.text(27, line1, "기준 무기 없음", VanillaGui.DARK);
            } else {
                int enh = mmorpg.fabric.gear.WeaponEnhance.reference(main, sp.getInventory().getNonEquipmentItems(), w);
                List<String> lines = s.weaponLines(w, enh, false);
                t.text(27, line1, VanillaGui.fit(w.name() + (enh > 0 ? " +" + enh : ""), 100), VanillaGui.legacyColor(w.grade() == null ? null : gradeCode(w), VanillaGui.WHITE));
                if (!lines.isEmpty()) t.text(27, line2, VanillaGui.fit(strip(lines.get(lines.size() > 1 ? 1 : 0)), 100), VanillaGui.GRAY);
                List<String> tip = new ArrayList<>();
                tip.add("§7기준 무기 §8(가진 무기 중 쓸 수 있는 것 · 공격력 높은 것)");
                tip.addAll(lines);
                setSlot(idx(4, 0), new SimpleGuiElement(VanillaGui.named(weaponStack(w), "§f기준 무기 §8· §e" + w.name(), tip), GuiElement.EMPTY_CALLBACK));
            }
            small(4, 7, new ItemStack(Items.AMETHYST_SHARD), "§d아티팩트", "§7낀 아티팩트 요약 (채팅)", p -> mmorpg.fabric.artifact.ArtifactSystem.open(p));
            small(4, 8, new ItemStack(Items.ECHO_SHARD), "§6유물", "§7꽂은 유물 요약 (채팅)", p -> mmorpg.fabric.relic.RelicSystem.open(p));
            small(5, 7, new ItemStack(mmorpg.fabric.economy.EconomyItems.GOLD_COIN), "§6유저 장터", "§7다른 사람이 올린 물건을 사고 팖", p -> RpgNet.statAction(p, "market"));
            small(5, 8, new ItemStack(Items.LAVA_BUCKET), "§c쓰레기통", "§7넣고 닫으면 지워집니다", p -> RpgNet.statAction(p, "trash"));
        }

        private void small(int r, int c, ItemStack icon, String name, String tip, Consumer<ServerPlayer> run) {
            setSlot(idx(r, c), new SimpleGuiElement(VanillaGui.named(icon, name, List.of(tip)), (i, type, a, g) -> {
                if (type.isLeft) run.accept(sp);
            }));
        }

        private void bottom(VanillaGui.Title t) {
            List<Button> list = List.of(
                    new Button("quest", "퀘스트", VanillaGui.ui("q_active", "", List.of()), "§7받은 퀘스트 · 진행", true, p -> RpgNet.statAction(p, "quest")),
                    new Button("ender", "창고", VanillaGui.ui("ender_chest", "", List.of()), "§7엔더 창고 (큰 상자 5개)", true, p -> RpgNet.statAction(p, "ender")),
                    new Button("mount", "탈것", VanillaGui.ui("icon_mount", "", List.of()), "§7고른 탈것을 불러 탐 · 타고 있으면 돌려보냄", true,
                            p -> RpgNet.statAction(p, "mount")),
                    new Button("mount_select", "탈것 선택", new ItemStack(Items.SADDLE), "§7탈 것 고르기", true, p -> RpgNet.statAction(p, "mount_select")),
                    new Button("skills", "스킬 칸", skillIcon(), "§7룬 올리기 · 빼기 · 옮기기 · 직업 스킬 보기", true, VanillaSkillBar::open),
                    new Button("tree", "스탯 트리", new ItemStack(Items.OAK_SAPLING), "§7스탯마다 받은 칸 · 고를 갈래", true,
                            p -> mmorpg.fabric.stattree.StatTrees.open(p, null)),
                    new Button("cosmetic", "꾸미기", new ItemStack(Items.ARMOR_STAND), "§8모드 없는 접속은 준비 중", false, p -> {}),
                    new Button("gacha", "뽑기", gachaIcon(), "§7뽑기권을 들고 우클릭하면 1회 §8(창은 준비 중)", true, p -> RpgNet.statAction(p, "gacha")));
            for (int i = 0; i < list.size(); i++) {
                Button b = list.get(i);
                int r = 6 + i / 2;
                int c0 = i % 2 == 0 ? 0 : 5;
                String name = (b.on() ? "§f" : "§8") + b.label();
                List<String> tip = List.of(b.tip());
                GuiElement.ClickCallback cb = (ix, type, a, g) -> {
                    if (type.isLeft && b.on()) b.run().accept(sp);
                };
                setSlot(idx(r, c0), new SimpleGuiElement(VanillaGui.named(b.icon(), name, tip), cb));
                for (int c = c0 + 1; c < c0 + 4; c++) setSlot(idx(r, c), new SimpleGuiElement(VanillaGui.blank(name, tip), cb));
                t.text(VanillaGui.cellX(c0) + 20, VanillaGui.rowLine(r), b.label(), b.on() ? VanillaGui.WHITE : VanillaGui.DARK);
            }
        }

        /** 스킬 칸 단추 그림: 첫 룬의 그림 (룬 아이템 그대로면 룬 설명이 섞임) — 없으면 마법 책 */
        private ItemStack skillIcon() {
            for (ItemStack st : mmorpg.fabric.rune.SkillBar.slots(sp)) {
                net.minecraft.resources.Identifier model = st.isEmpty() ? null : st.get(DataComponents.ITEM_MODEL);
                if (model != null) return VanillaGui.icon(model, "", List.of());
            }
            return new ItemStack(Items.ENCHANTED_BOOK);
        }

        private ItemStack gachaIcon() {
            ItemStack ticket = mmorpg.fabric.rune.RuneItems.TICKET == null ? ItemStack.EMPTY : new ItemStack(mmorpg.fabric.rune.RuneItems.TICKET);
            net.minecraft.resources.Identifier model = ticket.isEmpty() ? null : ticket.get(DataComponents.ITEM_MODEL);
            return model != null ? VanillaGui.icon(model, "", List.of()) : new ItemStack(Items.PAPER);
        }

        /** 기준 무기의 아이템 (가진 것 — 강화 · 꾸밈 그대로, 없으면 그 무기 아이템) */
        private ItemStack weaponStack(Weapon w) {
            List<ItemStack> all = new ArrayList<>();
            all.add(sp.getMainHandItem());
            all.add(sp.getOffhandItem());
            for (ItemStack st : sp.getInventory().getNonEquipmentItems()) all.add(st);
            for (ItemStack st : all) {
                Weapon x = RpgCombat.weaponOf(st);
                if (x != null && x.id().equals(w.id())) return st.copy();
            }
            for (RpgWeaponItem it : RpgItems.WEAPONS) if (it.weapon().id().equals(w.id())) return new ItemStack(it);
            return new ItemStack(Items.IRON_SWORD);
        }
    }

    private static String pct(double v) {
        double r = Math.round(v * 10) / 10.0;
        return r == Math.rint(r) ? String.valueOf((long) r) : String.format(Locale.ROOT, "%.1f", r);
    }

    /** 무기 등급 색 (§ 글자) */
    private static String gradeCode(Weapon w) {
        String t = w.title();
        return t != null && t.startsWith("§") && t.length() > 1 ? t.substring(0, 2) : "§f";
    }

    /** "§c" 같은 색 글자만 (Stats.info color 는 "§c" 꼴) */
    private static String colorCode(String c) {
        return c == null || c.length() < 2 ? "§f" : c.substring(0, 2);
    }

    /** § 서식 빼기 (줄 글꼴은 조각마다 한 색) */
    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }
}
