package mmorpg.fabric.vanilla;

import eu.pb4.sgui.api.ClickType;
import mmorpg.core.Armor;
import mmorpg.core.Artifacts;
import mmorpg.core.Js;
import mmorpg.core.Stats;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgData;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.artifact.ArtifactSystem;
import mmorpg.fabric.gear.ArtifactItem;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.gear.WeaponEnhance;
import mmorpg.fabric.skill.SkillFx;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 메뉴의 장비 쪽 (VanillaMenu). 2판 — 가운데 사람 그림 (도식):
 * <pre>
 * 장비                                    아티팩트 2/5
 * [투구] ┄┄┄┄┄  ( 사람 )  ┄┄┄┄ +N [반지]      줄 1
 * [갑옷] ┄┄┄┄┄  (  그림 )  ┄┄┄┄    [반지]      줄 2
 * [바지] ┄┄┄┄┄  (      )  ┄┄┄┄    [목걸이]    줄 3
 * [신발] ┄┄┄┄┄  (      )  ┄┄┄┄    [귀걸이]    줄 4
 * [요약]      +N [무기]  [왼손] +N   [팔찌]      줄 5
 * </pre>
 * 끼기: 가방의 갑옷 · 아티팩트를 누름 (갑옷은 요구 조건 확인 — 모자라면 끼지 않음, 있던 것은 그 가방 칸으로 · 아티팩트는 ArtifactSystem.equip 이 맞는 칸에).
 * 왼손: 가방의 아이템을 우클릭 · F (서로 바꿈 — Shift + F 가 메뉴라서 바닐라 왼손 바꾸기 대신). 빼기: 낀 칸을 누름 (가방으로 — 빈 칸이 없으면 그대로).
 * 무기는 핫바에서 고른 것 (보기만).
 */
public final class VanillaEquip {
    private VanillaEquip() {}

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] ARMOR_NAMES = {"투구", "갑옷", "바지", "신발"};

    static void render(VanillaMenu.MenuGui g, VanillaGui.Title t) {
        ServerPlayer sp = g.sp;
        VanillaMenu.title(t, "장비");
        t.right(166, 0, "아티팩트 " + ArtifactSystem.worn(sp) + "/" + ArtifactSystem.SIZE, VanillaGui.MUTED);
        // 갑옷 넷 (칸 0 · 줄 1 ~ 4 — 바탕의 점선이 몸까지)
        for (int i = 0; i < 4; i++) {
            EquipmentSlot es = ARMOR[i];
            ItemStack cur = sp.getItemBySlot(es);
            String name = ARMOR_NAMES[i];
            if (cur.isEmpty()) {
                g.set(1 + i, 0, VanillaGui.blank("§7" + name + " §8(비어 있음)", List.of("§7가방의 " + name + "을(를) 누르면 낌")), null);
            } else {
                g.set(1 + i, 0, cur.copy(), VanillaMenu.MenuGui.left(() -> takeOff(g, es, name)));
            }
        }
        // 무기 (줄 5 · 칸 3 — +N 은 왼쪽) · 왼손 (칸 5 — +N 은 오른쪽)
        ItemStack main = sp.getMainHandItem();
        if (main.isEmpty()) {
            g.set(5, 3, VanillaGui.blank("§7손에 든 것 없음", List.of("§7핫바에서 무기를 고르면 여기 보임")), null);
        } else {
            g.set(5, 3, main.copy(), null);
            int n = WeaponEnhance.of(main);
            if (n > 0) t.num(VanillaGui.cellX(3) - 2 - VanillaGui.numWidth("+" + n), VanillaGui.rowLine(5), "+" + n, color(n));
        }
        ItemStack off = sp.getOffhandItem();
        if (off.isEmpty()) {
            g.set(5, 5, VanillaGui.blank("§7왼손 §8(비어 있음)", List.of("§7가방의 아이템을 우클릭 (또는 마우스를 올리고 F) 하면 왼손으로")), null);
        } else {
            g.set(5, 5, off.copy(), VanillaMenu.MenuGui.left(() -> takeOff(g, EquipmentSlot.OFFHAND, "왼손")));
            int n = WeaponEnhance.of(off);
            if (n > 0) t.num(VanillaGui.cellX(6) + 2, VanillaGui.rowLine(5), "+" + n, color(n));
        }
        g.set(5, 0, VanillaGui.blank("§a장비 요약", summary(sp)), null);
        // 아티팩트 다섯 (칸 8 · 줄 1 ~ 5 — 강화 +N 은 칸 왼쪽)
        for (int i = 0; i < ArtifactSystem.SIZE && i < 5; i++) {
            int slot = i;
            ItemStack a = ArtifactSystem.get(sp, i);
            String name = Artifacts.slotName(i);
            if (a.isEmpty()) {
                g.set(1 + i, 8, VanillaGui.blank("§7" + name + " §8(비어 있음)", List.of("§7가방의 " + typeName(i) + "을(를) 누르면 낌")), null);
            } else {
                g.set(1 + i, 8, a.copy(), VanillaMenu.MenuGui.left(() -> {
                    ArtifactSystem.unequip(sp, slot);
                    g.refresh();
                }));
                int n = WeaponEnhance.of(a);
                if (n > 0) t.num(VanillaGui.cellX(8) - 2 - VanillaGui.numWidth("+" + n), VanillaGui.rowLine(1 + i), "+" + n, color(n));
            }
        }
    }

    /** 강화 +N 색 (7 이상 금색) */
    private static int color(int n) {
        return n >= 7 ? VanillaGui.GOLD : VanillaGui.WHITE;
    }

    private static String typeName(int slot) {
        Artifacts.Type ty = Artifacts.type(Artifacts.slotType(slot));
        return ty == null ? "아티팩트" : ty.name();
    }

    /** 장비 요약 (정보 단추 설명) */
    private static List<String> summary(ServerPlayer sp) {
        List<String> out = new ArrayList<>();
        Map<String, Double> bonus = RpgStats.bonusOf(sp);
        List<String> parts = new ArrayList<>();
        for (String k : Stats.STAT_KEYS) {
            double v = bonus.getOrDefault(k, 0.0);
            if (v != 0) parts.add(Stats.info(k).str("name") + " +" + Js.str(v));
        }
        out.add("§7갑옷 스탯 §f" + (parts.isEmpty() ? "없음" : String.join(" · ", parts)));
        out.add("§7아티팩트 §f" + ArtifactSystem.worn(sp) + " / " + ArtifactSystem.SIZE + "칸");
        int en = WeaponEnhance.of(sp.getMainHandItem());
        if (en > 0) out.add("§7무기 강화 §f+" + en);
        out.add("");
        out.add("§7가방의 갑옷 · 아티팩트를 누르면 낌");
        out.add("§7낀 칸을 누르면 가방으로");
        return out;
    }

    /** 낀 것을 가방으로 */
    private static void takeOff(VanillaMenu.MenuGui g, EquipmentSlot es, String name) {
        ServerPlayer sp = g.sp;
        ItemStack cur = sp.getItemBySlot(es);
        if (cur.isEmpty()) return;
        if (!VanillaMenu.bag(sp, cur.copy())) {
            fail(sp, "§c가방에 빈 칸이 없습니다 §7(한 칸 비우고 다시)");
            return;
        }
        sp.setItemSlot(es, ItemStack.EMPTY);
        SkillFx.playFor(sp, "item.armor.equip_generic", 0.8, 0.7);
        g.refresh();
    }

    /** 가방 칸 누름: 좌클릭 = 아티팩트 · 갑옷 끼기, 우클릭 · F = 왼손과 바꿈 */
    static void inventoryClick(VanillaMenu.MenuGui g, int slot, ClickType type) {
        ServerPlayer sp = g.sp;
        Inventory inv = sp.getInventory();
        if (type == ClickType.OFFHAND_SWAP || type.isRight) {
            ItemStack off = sp.getOffhandItem().copy();
            ItemStack it = inv.getItem(slot).copy();
            if (off.isEmpty() && it.isEmpty()) return;
            sp.setItemSlot(EquipmentSlot.OFFHAND, it);
            inv.setItem(slot, off);
            inv.setChanged();
            SkillFx.playFor(sp, "item.armor.equip_generic", 1.2, 0.7);
            g.refresh();
            return;
        }
        if (!type.isLeft) return;
        ItemStack it = inv.getItem(slot);
        if (it.isEmpty()) return;
        if (ArtifactItem.of(it) != null) {
            ArtifactSystem.equip(sp, slot, -1);
            g.refresh();
            return;
        }
        Equippable eq = it.get(DataComponents.EQUIPPABLE);
        EquipmentSlot es = eq == null ? null : eq.slot();
        if (es == null || es.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
            fail(sp, "§7갑옷 · 아티팩트를 누르면 낍니다");
            return;
        }
        Armor.Piece a = GearItems.armorOf(it);
        if (a != null && RpgHealth.isRpgMode(sp)) {
            List<String> lack = Armor.armorLack(a, RpgCombat.reqLevelOf(sp), RpgData.of(sp).base());
            if (!lack.isEmpty()) {
                fail(sp, "§c" + a.title() + ": " + String.join(" · ", lack) + " 필요");
                return;
            }
        }
        ItemStack cur = sp.getItemBySlot(es).copy();
        ItemStack one = it.copyWithCount(1);
        if (it.getCount() > 1) {
            if (!cur.isEmpty() && !VanillaMenu.bag(sp, cur)) {
                fail(sp, "§c가방에 빈 칸이 없습니다 §7(한 칸 비우고 다시)");
                return;
            }
            it.shrink(1);
        } else {
            inv.setItem(slot, cur);
        }
        inv.setChanged();
        sp.setItemSlot(es, one);
        SkillFx.playFor(sp, "item.armor.equip_generic", 0.9, 1.0);
        g.refresh();
    }

    private static void fail(ServerPlayer sp, String text) {
        Notify.notify(sp, text, 40);
        SkillFx.playFor(sp, "block.note_block.bass", 0.7, 0.8);
    }
}
