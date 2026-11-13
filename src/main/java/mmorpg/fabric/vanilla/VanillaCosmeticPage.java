package mmorpg.fabric.vanilla;

import mmorpg.core.Economy;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.cosmetic.CosmeticDefs;
import mmorpg.fabric.cosmetic.Cosmetics;
import mmorpg.fabric.cosmetic.Look;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.skill.SkillFx;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 모드 없는 클라이언트의 꾸미기 전용 창 (2026-11-08 로드맵 3번 — 메뉴 홈 빠른 단추 · /장식). 모드 장식 창 (CosmeticScreen) 과 같은 일:
 * 칸 탭 다섯 (무기 · 머리 · 등 · 오라 · 칭호 — 줄 1 가운데 칸 2 ~ 6) → 그 칸의 장식 (가진 것 = 그림, 아직 = 어둡게 · 입은 것 = 금 테 · 빛남),
 * 누르면 입기 / 벗기, 아직 가지지 않은 것은 두 번 눌러 골드로 사기 (사면 바로 입음 — Cosmetics.buy). 줄 5: 원래대로 (그 칸) · 미리보기 (창을 닫고
 * 앞에 내 모습 마네킹 — VanillaCosmetics.preview) · 도움말. 갑옷은 늘 안 보임 (2026-11-08 사용자 "갑옷 아예 빼버려 안보이게를 기본으로 하자 보이게
 * 하지 마"), 2026-11-11 갑옷 칸 탭 (갑옷 스킨) 을 뺌 (사용자 "없애 갑옷 스킨과 모드 창의 숨기기 없애고").
 * 글자는 칸 이름 · 금화만 (사용자 "글자를 최대한 줄여서" — 이름 · 설명 · 값은 마우스를 올리면).
 */
final class VanillaCosmeticPage {
    private VanillaCosmeticPage() {}

    /** 사기를 확인하는 시간 (두 번째 누름까지 — 틱) */
    static final int ARM_TICKS = 100;
    /** 입은 장식 테 · 사려고 누른 장식 테 */
    static final int WORN = VanillaGui.GOLD, ARMED = 0xFF8A50;
    /** 첫 칸 탭의 칸 (가운데로 — tools/vanilla_gui.py COSMETIC_TAB0 과 같게) */
    static final int TAB0 = (9 - CosmeticDefs.SLOTS.size()) / 2;

    static void render(VanillaMenu.MenuGui g, VanillaGui.Title t) {
        ServerPlayer sp = g.sp;
        Cosmetics.State st = Cosmetics.state(sp);
        Look look = st.look();
        String tab = CosmeticDefs.slot(g.cosTab) == null ? "weapon" : g.cosTab;
        long now = sp.level().getServer().getTickCount();
        if (g.cosArm != null && now - g.cosArmAt > ARM_TICKS) g.cosArm = null;

        // 줄 0: 돌아가기 · 가운데 판 (칸 이름 · 금화) · 닫기
        g.set(0, 0, VanillaGui.blank("§f돌아가기", List.of("§7메뉴 홈으로")), VanillaMenu.MenuGui.left(() -> g.show(VanillaMenu.Page.HOME)));
        g.set(0, 8, VanillaGui.blank("§c닫기", List.of("§7Shift + F 로 다시 열기")), VanillaMenu.MenuGui.left(g::close));
        t.text(VanillaGui.cellX(1) + 5, 17, tabName(tab), VanillaGui.CREAM);
        String gold = VanillaGui.commas(Wallet.total(sp, Economy.Kind.GOLD));
        int right = VanillaGui.cellX(7) + 13;
        t.right(right, 17, gold, VanillaGui.GOLD);
        t.text(right - VanillaGui.width(gold) - VanillaGui.width(VanillaGui.COIN) - 2, 17, VanillaGui.COIN, VanillaGui.WHITE);
        List<String> goldTip = List.of("§7장식은 골드로 삼 (보스 · 퀘스트 보상)", "§7가진 골드 §6" + gold);
        for (int c = 1; c <= 7; c++) g.set(0, c, VanillaGui.blank("§6골드", goldTip), null);

        // 줄 1: 칸 탭
        for (int i = 0; i < CosmeticDefs.SLOTS.size(); i++) {
            String key = CosmeticDefs.SLOTS.get(i);
            int col = TAB0 + i;
            boolean on = key.equals(tab);
            if (on) t.glyph(VanillaGui.cellX(col), 1, "tab_sel", 0x8A6A2A);
            t.glyph(VanillaGui.cellX(col), 1, "cat_" + key, on ? 0xFFF4D6 : VanillaGui.MUTED);
            List<String> lore = new ArrayList<>(wearing(look, key));
            lore.add("");
            lore.add(on ? "§7지금 보는 칸" : "§e눌러서 보기");
            g.set(1, col, VanillaGui.blank((on ? "§6" : "§f") + tabName(key), lore), VanillaMenu.MenuGui.left(() -> {
                if (key.equals(g.cosTab)) return;
                g.cosTab = key;
                g.cosArm = null;
                SkillFx.playFor(sp, "ui.button.click", 1.6, 0.3);
                g.refresh();
            }));
        }

        // 줄 2 ~ 4: 그 칸의 장식
        List<CosmeticDefs.Def> defs = CosmeticDefs.of(tab, null);
        for (int idx = 0; idx < Math.min(27, defs.size()); idx++) {
            CosmeticDefs.Def d = defs.get(idx);
            int r = 2 + idx / 9, c = idx % 9;
            boolean owned = st.owned().contains(d.id());
            boolean worn = worn(look, d);
            boolean armed = d.id().equals(g.cosArm);
            if (worn) t.glyph(VanillaGui.cellX(c), r, "ring", WORN);
            else if (armed) t.glyph(VanillaGui.cellX(c), r, "ring", ARMED);
            List<String> lore = new ArrayList<>();
            if ("weapon".equals(d.slot()) && d.weapon() != null) lore.add("§7" + weaponName(d.weapon()) + " 스킨 §8(손에 든 모습만)");
            for (String line : d.desc()) lore.add("§7" + line);
            if ("title".equals(d.slot()) && d.title() != null) lore.add("§7칭호: " + d.title());
            lore.add("");
            if (worn) lore.add("§a입는 중 §7— 누르면 벗기");
            else if (owned) lore.add("§e누르면 입기");
            else if (armed) lore.add("§c한 번 더 누르면 §6골드 " + d.price() + " §c으로 사기");
            else lore.add("§6골드 " + d.price() + " §7— 두 번 눌러 사기 (사면 바로 입음)");
            ItemStack icon = VanillaGui.icon(Mmorpg.rpg((owned ? "cosmetic_" : "vcosdim/") + d.id()), "§f" + d.name(), lore);
            if (worn) icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            g.set(r, c, icon, VanillaMenu.MenuGui.left(() -> click(g, d)));
        }

        // 줄 5: 단추
        g.set(5, 0, VanillaGui.blank("§f원래대로", List.of("weapon".equals(tab) ? "§7모든 무기 스킨을 벗음" : "§7이 칸의 장식을 벗음")),
                VanillaMenu.MenuGui.left(() -> act(g, "clear", tab, "")));
        g.set(5, 6, VanillaGui.blank("§b미리보기", List.of("§7창을 닫고 내 앞에 지금 모습을 15초 동안 세움",
                "§8(칭호 · 등 장식 · 오라는 내 화면에서 내 몸에는 그리지 않음 — 다른 사람에게 보임)")), VanillaMenu.MenuGui.left(() -> g.closeThen(VanillaCosmetics::preview)));
        // 2026-11-13 내 모션 보기 (VanillaRig — 싸우는 동안 뼈 몸 모션을 내 화면 (3인칭) 에도, 저장됨 · 기본 끔): 켜면 금 ring
        boolean self = VanillaRig.selfView(sp);
        if (self) t.glyph(VanillaGui.cellX(7), 5, "ring", WORN);
        g.set(5, 7, VanillaGui.blank((self ? "§a" : "§f") + "내 모션 보기", List.of(
                "§7싸우는 동안의 몸 동작 (평타 · 스킬 · 룬) 을",
                "§7내 화면 (3인칭 F5) 에도 그림",
                "§8다른 사람에게는 켜고 끄는 것과 상관없이 보임",
                "",
                self ? "§a켜짐 §7— 누르면 끄기" : "§7꺼짐 §e— 누르면 켜기")), VanillaMenu.MenuGui.left(() -> {
                    VanillaRig.selfView(sp, !VanillaRig.selfView(sp));
                    SkillFx.playFor(sp, "ui.button.click", 1.6, 0.3);
                    g.refresh();
                }));
        g.set(5, 8, VanillaGui.blank("§b꾸미기", List.of(
                "§f누르기 §7입기 · 벗기",
                "§f두 번 누르기 §7사기 (골드 — 사면 바로 입음)",
                "§7무기 스킨은 무기 종류마다 하나 · 손에 든 모습만",
                "§7갑옷은 보이지 않음 (막는 수치 · 효과는 그대로)",
                "§7산 장식은 계속 가짐 (죽어도 그대로)")), null);
    }

    /** 장식 칸 누름: 입은 것 = 벗기, 가진 것 = 입기, 아직 = 첫 번째는 표시 · 두 번째 (5초 안) 는 사기 */
    private static void click(VanillaMenu.MenuGui g, CosmeticDefs.Def d) {
        ServerPlayer sp = g.sp;
        Cosmetics.State st = Cosmetics.state(sp);
        if (worn(st.look(), d)) {
            act(g, "clear", d.slot(), "weapon".equals(d.slot()) ? d.weapon() : "");
            return;
        }
        if (st.owned().contains(d.id())) {
            act(g, "equip", d.id(), "");
            return;
        }
        long now = sp.level().getServer().getTickCount();
        if (d.id().equals(g.cosArm) && now - g.cosArmAt <= ARM_TICKS) {
            g.cosArm = null;
            act(g, "buy", d.id(), "");
            return;
        }
        g.cosArm = d.id();
        g.cosArmAt = now;
        SkillFx.playFor(sp, "note.pling", 0.9, 0.5);
        g.refresh();
    }

    /** 장식 일 (Cosmetics.action — 실패면 까닭을 알림) → 창을 다시 그림 */
    static void act(VanillaMenu.MenuGui g, String action, String a, String b) {
        Cosmetics.action(g.sp, action, a, b);
        SkillFx.playFor(g.sp, "ui.button.click", 1.4, 0.35);
        g.refresh();
    }

    static boolean worn(Look look, CosmeticDefs.Def d) {
        return "weapon".equals(d.slot()) ? d.id().equals(look.weaponSkin(d.weapon())) : d.id().equals(look.get(d.slot()));
    }

    /** 칸 탭 설명: 지금 입은 것 */
    private static List<String> wearing(Look look, String slot) {
        List<String> out = new ArrayList<>();
        if ("weapon".equals(slot)) {
            if (look.weapon().isEmpty()) out.add("§8무기 스킨 없음");
            for (String id : look.weapon().values()) {
                CosmeticDefs.Def d = CosmeticDefs.get(id);
                if (d != null) out.add("§7입는 중: §f" + d.name());
            }
            return out;
        }
        CosmeticDefs.Def d = look.def(slot);
        out.add(d == null ? "§8없음" : "§7입는 중: §f" + d.name());
        return out;
    }

    static String tabName(String slot) {
        return switch (slot) {
            case "weapon" -> "무기 스킨";
            case "head" -> "머리 장식";
            case "back" -> "등 장식";
            case "aura" -> "오라";
            case "title" -> "칭호";
            default -> slot;
        };
    }

    private static String weaponName(String type) {
        return switch (type) {
            case "sword" -> "칼";
            case "dagger" -> "단검";
            case "axe" -> "도끼";
            case "shield" -> "방패";
            case "bow" -> "활";
            case "wand" -> "완드";
            case "greatsword" -> "마검";
            case "lance" -> "창";
            case "scythe" -> "낫";
            case "hammer" -> "망치";
            case "scepter" -> "홀";
            default -> type;
        };
    }
}
