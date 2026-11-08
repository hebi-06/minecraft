package mmorpg.fabric.vanilla;

import eu.pb4.sgui.api.ClickType;
import mmorpg.core.Armor;
import mmorpg.core.Artifacts;
import mmorpg.core.CombatPower;
import mmorpg.fabric.CombatPowers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgData;
import mmorpg.fabric.RpgHealth;
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

/**
 * 메뉴의 장비 쪽 (VanillaMenu). 2판 — 가운데 사람 그림 (도식):
 * <pre>
 *                                         ⚔ 12,345   (제목 줄: 총 전투력 — CombatPowers)
 * [투구] ┄┄┄┄┄  ( 사람 )  ┄┄┄┄ +N [반지]      줄 1
 * [갑옷] ┄┄┄┄┄  (  그림 )  ┄┄┄┄    [반지]      줄 2
 * [바지] ┄┄┄┄┄  (      )  ┄┄┄┄    [목걸이]    줄 3
 * [신발] ┄┄┄┄┄  (      )  ┄┄┄┄    [귀걸이]    줄 4
 * [총 스펙]   +N [무기]  [왼손] +N   [팔찌]      줄 5  (총 스펙 = 정보 단추 설명 — 전투력 · 공격력 · 치명타 · 체력 …)
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
        // 제목 줄: 전투력만 (칼 그림 + 숫자 — 2026-11-08 총 전투력, 보여주기 · 순위용)
        CombatPowers.Sheet power = CombatPowers.of(sp);
        String cp = VanillaGui.commas(power.cp());
        t.right(166, 0, cp, VanillaGui.GOLD);
        t.text(166 - VanillaGui.width(cp) - VanillaGui.width(VanillaGui.POWER) - 2, 0, VanillaGui.POWER, VanillaGui.WHITE);
        // 그 밖의 글자 없음 (사용자 "글자를 최대한 줄여서" — 아티팩트 수 · 설명은 칸의 설명 상자)
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
        g.set(5, 0, VanillaGui.blank("§6§l총 스펙", spec(power)), null);
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

    /**
     * 총 스펙 (왼쪽 아래 정보 단추 — 2026-11-08 사용자 "총 스펙 표기할때 잘 만 표시해주고 공격력으로 바꾸고 … 총전투력"): 전투력 · 공격력 (무기 + 스탯 ×
     * 스탯 트리) · 치명타 · 최대 체력 · 받는 피해 · 방어 · 룬 피해 · 재사용 대기 · 이동 속도. 지금 든 무기 · 입은 갑옷 · 낀 아티팩트 · 스탯 트리,
     * 잠깐 걸리는 룬 버프는 뺌 (CombatPowers — 순위용 값과 같음). 0 인 줄은 뺌.
     */
    static List<String> spec(CombatPowers.Sheet c) {
        CombatPower.Spec s = c.spec();
        List<String> out = new ArrayList<>();
        out.add("§7전투력 §6§l" + VanillaGui.commas(c.cp()));
        out.add("");
        out.add("§7공격력 §f" + VanillaStats.one(s.attack()) + (c.weapon() != null && !c.usable() ? " §c(든 무기의 요구 조건이 모자람)" : ""));
        if (c.usable()) {
            // 깡딜 (사용자 "깡딜로 하자"): 스탯 트리 공격력 % 도 더해진 공격력 숫자로
            double tree = (c.weaponAttack() + c.statAttack()) * c.atkPct() / 100;
            out.add("§7  무기 " + VanillaStats.one(c.weaponAttack()) + " · 스탯 +" + VanillaStats.one(c.statAttack())
                    + (tree > 0 ? " · 스탯 트리 +" + VanillaStats.one(tree) : ""));
            out.add("§7치명타 확률 §f" + VanillaStats.one(s.critChance() * 100) + "%");
            out.add("§7치명타 피해 §f×" + VanillaStats.two(s.critMult()));
        }
        out.add("§7최대 체력 §f" + VanillaStats.one(s.maxHp()));
        if (s.reduction() > 0) out.add("§7받는 피해 §f-" + VanillaStats.one(s.reduction() * 100) + "%");
        if (s.armor() > 0) out.add("§7방어 §f" + VanillaStats.one(s.armor()));
        if (s.rune() > 0) out.add("§7룬 피해 §f+" + VanillaStats.one(s.rune()) + "%");
        if (s.cdr() > 0) out.add("§7재사용 대기 §f-" + VanillaStats.one(s.cdr()) + "%");
        if (s.speed() > 0) out.add("§7이동 속도 §f+" + VanillaStats.one(s.speed()) + "%");
        out.add("");
        out.add(c.weapon() == null ? "§7든 RPG 무기가 없음 (공격력 0)" : "§7든 무기 " + (c.enh() > 0 ? "+" + c.enh() + " " : "") + c.weapon().title());
        out.add("§7입은 갑옷 · 낀 아티팩트 · 스탯 트리 포함 · 룬 버프 제외");
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
