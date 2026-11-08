package mmorpg.fabric.vanilla;

import eu.pb4.sgui.api.elements.GuiElement;
import mmorpg.core.Gacha;
import mmorpg.core.Runes;
import mmorpg.core.StatTree;
import mmorpg.core.Stats;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.gacha.GachaSystem;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.skill.SkillFx;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 메뉴의 룬 뽑기 쪽 (VanillaMenu — 홈의 뽑기 타일). 3판 (2026-11-08 사용자 그림 "맨위에 관리자가 설정한 한정 뽑기, 그 아래에 일반뽑기들로 그리고
 * 뽑기 버튼을 누르면 정말로 사용하시겠습니까? 문구가 나오고 네 버튼을 누르면 … 우리만의 뽑기 연출"):
 * <pre>
 * 룬 뽑기 · 불꽃의 축제                       천장 37/90
 * ┌ 불꽃의 축제          ~11.21 ┐ [한정  3]   ← 목록 (칸 6 ~ 8 · 줄 1 ~ 4): 맨 위 = 관리자가 연 한정 뽑기, 아래 = 일반 · 고급 · 스탯
 * │      (큰 픽업 초월 룬)       │ [일반 12]      (그림 = 뽑기권, 글 = 이름 · 가진 뽑기권, 고른 줄 = 금 테 글자 'plate')
 * │                              │ [고급  0]
 * │ 영웅 픽업 [룬][룬][룬]       │ [스탯  5]
 * └──────────────────────────────┘ [i][⌚][?]   ← 확률 · 기록 · 도움말
 * 🎟 가진 뽑기권 3장          영웅까지 4 · 픽업 확정  ← 줄 6
 * 최근 [결과 10칸 — 등급 색 테]                     ← 줄 7 ~ 8
 * [🎟 1회 뽑기 ]         [🎟 10회 뽑기 ]             ← 줄 9
 * </pre>
 * 진열판 (칸 0 ~ 5 · 줄 1 ~ 5): 한정 = 픽업 초월 룬 (rpg:vbig — 2배, 1 ~ 3개) + 픽업 영웅 (rpg:vsh), 일반 · 고급 = 등급 사다리 (등급마다 룬 + 색 테),
 * 스탯 = 스탯 넷 (고른 것 = 주황 빛) + 그 계열 사다리. 단추 → 확인 창 (menu_gacha_confirm — "정말로 사용하시겠습니까?" · 네 / 아니요) →
 * 네 = 뽑기 (GachaSystem.pullFromScreen — 뽑기권 · 천장 · 기록 · 룬 주기) → 메뉴를 닫고 연출 (GachaCinematic) → 끝나면 이 쪽으로 (최근 결과).
 */
public final class VanillaGacha {
    private VanillaGacha() {}

    /** 목록 줄 (줄 1 ~ 4): 한정 · 일반 · 고급 · 스탯 */
    static final String STAT_GROUP = "stat";
    /** 등급 색 (RuneKit.RUNE_GRADE_FX 와 같음 — 일반 · 희귀 · 고귀 · 영웅 · 초월) */
    static final Map<String, Integer> GRADE_RGB = Map.of("common", 0xE0E0F0, "rare", 0x5C99FF, "noble", 0xB86BFF, "hero", 0xFF9E29,
            "transcend", 0xFF4242);

    /** 마지막 뽑기 (메뉴를 닫고 연출을 본 뒤 다시 열 때 — 사람마다) */
    record Last(String key, List<ItemStack> icons, List<String> grades) {}

    private static final Map<UUID, Last> LAST = new ConcurrentHashMap<>();

    static int gradeColor(String grade) {
        return GRADE_RGB.getOrDefault(grade, 0xFFFFFF);
    }

    /** 고른 뽑기 (없으면 한정 → 일반) */
    private static GachaSystem.Def current(VanillaMenu.MenuGui g, List<GachaSystem.Def> defs) {
        if (g.gachaKey == null) {
            Last last = LAST.get(g.sp.getUUID());
            if (last != null) g.gachaKey = last.key();
        }
        GachaSystem.Def cur = null;
        for (GachaSystem.Def d : defs) if (d.key().equals(g.gachaKey)) cur = d;
        if (cur == null) {
            for (GachaSystem.Def d : defs) if (d.key().equals(GachaSystem.LIMITED)) cur = d;
        }
        if (cur == null) {
            for (GachaSystem.Def d : defs) if (d.key().equals(GachaSystem.NORMAL)) cur = d;
        }
        if (cur == null && !defs.isEmpty()) cur = defs.get(0);
        g.gachaKey = cur == null ? null : cur.key();
        if (cur != null && cur.key().startsWith(GachaSystem.STAT)) g.gachaStat = cur.key().substring(GachaSystem.STAT.length());
        return cur;
    }

    private static GachaSystem.Def find(List<GachaSystem.Def> defs, String key) {
        for (GachaSystem.Def d : defs) if (d.key().equals(key)) return d;
        return null;
    }

    static void render(VanillaMenu.MenuGui g, VanillaGui.Title t) {
        ServerPlayer sp = g.sp;
        List<GachaSystem.Def> defs = GachaSystem.defs(sp.level().getServer());
        GachaSystem.Def cur = current(g, defs);
        VanillaMenu.title(t, "룬 뽑기");
        if (cur == null) return;
        t.text(10 + VanillaGui.width("룬 뽑기") + 5, 0, "· " + VanillaGui.fit(strip(cur.name()), 72), VanillaGui.CREAM);
        Gacha.Pity pity = cur.banner().pity();
        if (cur.pool() != null && pity.hard() > 0) t.right(166, 0, "천장 " + GachaSystem.counter(sp, cur.pool()).n5() + "/" + pity.hard(), VanillaGui.MUTED);
        if (g.gachaConfirm > 0) {
            confirm(g, t, cur);
            return;
        }
        list(g, t, defs, cur);
        showcase(g, t, cur);
        bottom(g, t, cur);
    }

    // ------------------------------------------------------------ 목록 (칸 6 ~ 8 · 줄 1 ~ 4)

    private static void list(VanillaMenu.MenuGui g, VanillaGui.Title t, List<GachaSystem.Def> defs, GachaSystem.Def cur) {
        ServerPlayer sp = g.sp;
        GachaSystem.Def limited = find(defs, GachaSystem.LIMITED);
        String statKey = GachaSystem.STAT + (g.gachaStat != null ? g.gachaStat : firstStat(defs));
        GachaSystem.Def stat = find(defs, statKey);
        if (stat == null) for (GachaSystem.Def d : defs) if (stat == null && d.key().startsWith(GachaSystem.STAT)) stat = d;
        Object[][] rows = {{limited, "한정"}, {find(defs, GachaSystem.NORMAL), "일반"}, {find(defs, GachaSystem.ADVANCED), "고급"}, {stat, "스탯"}};
        for (int i = 0; i < rows.length; i++) {
            int r = 1 + i;
            GachaSystem.Def d = (GachaSystem.Def) rows[i][0];
            String label = (String) rows[i][1];
            if (d == null) {
                List<String> lore = i == 0 ? List.of("§7지금 열린 한정 뽑기가 없습니다", "§8관리자가 열면 맨 위에 나옴") : List.of("§8없음");
                ItemStack icon = VanillaGui.icon(i == 0 && RuneItems.LIMITED_TICKET != null ? modelOf(new ItemStack(RuneItems.LIMITED_TICKET)) : Mmorpg.rpg("vui/blank"),
                        "§8" + label + " 뽑기", lore);
                g.set(r, 6, icon, null);
                for (int c = 7; c <= 8; c++) g.set(r, c, VanillaGui.blank("§8" + label + " 뽑기", lore), null);
                t.text(VanillaGui.cellX(7) + 2, VanillaGui.rowLine(r), label, VanillaGui.DIM);
                continue;
            }
            boolean on = d == cur || (i == 3 && cur.key().startsWith(GachaSystem.STAT));
            int have = GachaSystem.tickets(sp, d.ticket());
            if (on) t.glyph(VanillaGui.cellX(6) - 1, r, "plate", VanillaGui.GOLD);
            t.text(VanillaGui.cellX(7) + 2, VanillaGui.rowLine(r), label, on ? VanillaGui.GOLD : VanillaGui.CREAM);
            String n = VanillaGui.compact(have);
            t.num(166 - VanillaGui.numWidth(n), VanillaGui.rowLine(r), n, have > 0 ? VanillaGui.WHITE : VanillaGui.DIM);
            List<String> lore = new ArrayList<>();
            lore.add("§7" + GachaSystem.ticketName(d) + " §f" + have + "장");
            if (i == 0 && d.spec() != null && d.spec().end() > 0) lore.add("§7" + period(d.spec()));
            lore.add("");
            lore.add(on ? "§e보는 중" : "§a눌러서 보기");
            String key = d.key();
            GuiElement.ClickCallback cb = VanillaMenu.MenuGui.left(() -> pick(g, key));
            ItemStack icon = VanillaGui.icon(modelOf(new ItemStack(d.ticket())), (on ? "§e" : "§f") + strip(i == 3 ? "스탯 룬 뽑기" : d.name()), lore);
            if (on) icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            g.set(r, 6, icon, cb);
            for (int c = 7; c <= 8; c++) g.set(r, c, VanillaGui.blank((on ? "§e" : "§f") + strip(i == 3 ? "스탯 룬 뽑기" : d.name()), lore), cb);
        }
        // 줄 5: 확률 · 기록 · 도움말
        g.set(5, 6, VanillaGui.blank("§b확률 · 천장", rates(sp, cur)), null);
        g.set(5, 7, VanillaGui.blank("§d최근 기록", history(sp)), null);
        g.set(5, 8, VanillaGui.blank("§b룬 뽑기", List.of(
                "§7오른쪽 목록에서 뽑기를 고르고 아래 §f1회 · 10회",
                "§7맨 위는 관리자가 연 §d한정 뽑기 §7(기간 · 픽업)",
                "§7뽑으면 연출: §f좌클릭 §7계속 · §fShift §7건너뛰기",
                "§7뽑은 룬은 바로 가방으로")), null);
    }

    private static String firstStat(List<GachaSystem.Def> defs) {
        for (GachaSystem.Def d : defs) if (d.key().startsWith(GachaSystem.STAT)) return d.key().substring(GachaSystem.STAT.length());
        return "str";
    }

    private static void pick(VanillaMenu.MenuGui g, String key) {
        if (key.equals(g.gachaKey)) return;
        g.gachaKey = key;
        SkillFx.playFor(g.sp, "ui.button.click", 1.7, 0.3);
        g.refresh();
    }

    // ------------------------------------------------------------ 진열판 (칸 0 ~ 5 · 줄 1 ~ 5)

    private static void showcase(VanillaMenu.MenuGui g, VanillaGui.Title t, GachaSystem.Def cur) {
        if (cur.key().equals(GachaSystem.LIMITED) && cur.spec() != null) {
            limited(g, t, cur);
            return;
        }
        boolean stat = cur.key().startsWith(GachaSystem.STAT);
        String head = stat ? "스탯 룬 뽑기" : strip(cur.name());
        t.text(10, VanillaGui.rowLine(1), VanillaGui.fit(head, 70), VanillaGui.GOLD);
        List<String> families = cur.banner().families();
        if (stat) {
            String s = g.gachaStat;
            t.right(110, VanillaGui.rowLine(1), Stats.info(s).str("name"), VanillaTree.statColor(s));
            statChips(g, t);
            StatTree.Ticket tk = StatTree.tickets().get(s);
            ladder(g, t, cur, tk != null && tk.family() != null ? List.of(tk.family()) : families);
            t.text(10, VanillaGui.rowLine(5), VanillaGui.fit(Stats.info(s).str("name") + " 계열 룬 · 등급만 고름", 100), VanillaGui.MUTED);
            return;
        }
        t.right(110, VanillaGui.rowLine(1), cur.key().equals(GachaSystem.ADVANCED) ? "희귀 이상" : "룬 " + families.size() + "계열", VanillaGui.MUTED);
        ladder(g, t, cur, families);
        Gacha.Pity p = cur.banner().pity();
        String pity = cur.pool() != null && p.hard() > 0 ? "초월 천장 " + p.hard() + (p.heroEvery() > 0 ? " · 영웅 " + p.heroEvery() + "회마다" : "") : "천장 없음";
        t.text(10, VanillaGui.rowLine(5), VanillaGui.fit(pity, 100), VanillaGui.MUTED);
    }

    /** 한정: 픽업 초월 (큰 그림 1 ~ 3 — 칸 2 / 1 · 3 / 0 · 2 · 4 의 오른쪽 9px) · 픽업 영웅 (줄 5 · 칸 2 ~ 4) · 기간 */
    private static void limited(VanillaMenu.MenuGui g, VanillaGui.Title t, GachaSystem.Def cur) {
        Gacha.Spec spec = cur.spec();
        t.text(10, VanillaGui.rowLine(1), VanillaGui.fit(strip(spec.name()), 64), VanillaGui.GOLD);
        t.right(110, VanillaGui.rowLine(1), spec.end() > 0 ? "~" + mmdd(spec.end()) : "끝없음", VanillaGui.MUTED);
        List<String> f5 = spec.featured5();
        int n = Math.min(3, f5.size());
        int[][] cols = {{2}, {1, 3}, {0, 2, 4}};
        for (int k = 0; k < n; k++) {
            ItemStack rune = RuneItems.stack(f5.get(k), "transcend");
            g.set(3, cols[n - 1][k], VanillaGui.icon(Mmorpg.rpg("vbig/" + modelOf(rune).getPath()), runeName(rune, "transcend"),
                    List.of("§6픽업 초월", "§7초월이 나오면 " + mmorpg.core.Js.str(spec.share()) + "% 로 이 룬", "§7놓치면 다음 초월은 픽업 확정")), null);
        }
        List<String> f4 = spec.featured4();
        if (!f4.isEmpty()) {
            t.text(10, VanillaGui.rowLine(5), "영웅 픽업", VanillaGui.MUTED);
            for (int k = 0; k < f4.size() && k < 3; k++) {
                ItemStack rune = RuneItems.stack(f4.get(k), "hero");
                List<String> lore = new ArrayList<>(List.of("§6픽업 영웅"));
                if (k == 2 && f4.size() > 3) lore.add("§7그 밖 " + (f4.size() - 3) + "개 — 확률 단추");
                g.set(5, 2 + k, VanillaGui.icon(Mmorpg.rpg("vsh/" + modelOf(rune).getPath()), runeName(rune, "hero"), lore), null);
            }
        } else {
            t.text(10, VanillaGui.rowLine(5), "초월 픽업 " + mmorpg.core.Js.str(spec.share()) + "%", VanillaGui.MUTED);
        }
    }

    /** 스탯 넷 (줄 2 · 칸 1 ~ 4 — 고른 것 = 주황 빛) */
    private static void statChips(VanillaMenu.MenuGui g, VanillaGui.Title t) {
        ServerPlayer sp = g.sp;
        int k = 0;
        for (Map.Entry<String, StatTree.Ticket> e : StatTree.tickets().entrySet()) {
            if (k >= 4) break;
            String s = e.getKey();
            int col = 1 + k++;
            boolean on = s.equals(g.gachaStat);
            if (on) t.glyph(VanillaGui.cellX(col), 2, "tab_sel", VanillaTree.TAB);
            GachaSystem.Def d = GachaSystem.def(sp.level().getServer(), GachaSystem.STAT + s);
            int have = d == null ? 0 : GachaSystem.tickets(sp, d.ticket());
            g.set(2, col, VanillaGui.ui("icon_" + s, VanillaStats.colorCode(Stats.info(s).str("color")) + e.getValue().name(),
                    List.of("§7가진 것 §f" + have + "장", "", on ? "§e보는 중" : "§a눌러서 보기")), VanillaMenu.MenuGui.left(() -> {
                if (s.equals(g.gachaStat)) return;
                g.gachaStat = s;
                g.gachaKey = GachaSystem.STAT + s;
                SkillFx.playFor(sp, "ui.button.click", 1.7, 0.3);
                g.refresh();
            }));
        }
    }

    /** 등급 사다리 (줄 3 · 칸 0 ~ 4 의 오른쪽 9px — 등급마다 룬 + 색 테) · 줄 4 = 높은 등급 확률 */
    private static void ladder(VanillaMenu.MenuGui g, VanillaGui.Title t, GachaSystem.Def cur, List<String> families) {
        Map<String, Double> w = cur.banner().weights();
        double sum = 0;
        for (double v : w.values()) sum += v;
        List<String> grades = new ArrayList<>();
        for (String gr : Runes.GRADE_ORDER) if (w.getOrDefault(gr, 0.0) > 0) grades.add(gr);
        int start = Math.max(0, (5 - grades.size()) / 2);
        for (int k = 0; k < grades.size() && k < 5; k++) {
            String gr = grades.get(k);
            String fam = families.isEmpty() ? Runes.FAMILY_ORDER.get(0) : families.get((k * 3) % families.size());
            ItemStack rune = RuneItems.stack(fam, gr);
            int col = start + k;
            double pct = sum > 0 ? w.get(gr) / sum * 100 : 0;
            t.glyph(VanillaGui.cellX(col) + 9, 3, "ring", gradeColor(gr));
            mmorpg.core.Grade gd = mmorpg.core.Rules.get().grade(gr);
            g.set(3, col, VanillaGui.icon(Mmorpg.rpg("vsh/" + modelOf(rune).getPath()), (gd == null ? "§f" + gr : gd.color() + gd.name()) + " §f"
                    + String.format(java.util.Locale.ROOT, "%.2f", pct) + "%", List.of("§7예: " + strip(runeName(rune, gr)))), null);
        }
        // 줄 4: 초월 · 영웅 확률
        List<String> parts = new ArrayList<>();
        for (String gr : List.of("transcend", "hero")) {
            if (w.getOrDefault(gr, 0.0) <= 0 || sum <= 0) continue;
            mmorpg.core.Grade gd = mmorpg.core.Rules.get().grade(gr);
            parts.add((gd == null ? gr : gd.name()) + " " + trimPct(w.get(gr) / sum * 100));
        }
        if (!parts.isEmpty()) t.center(60, VanillaGui.rowLine(4), String.join(" · ", parts), VanillaGui.CREAM);
    }

    private static String trimPct(double v) {
        String s = String.format(java.util.Locale.ROOT, v >= 10 ? "%.0f" : v >= 1 ? "%.1f" : "%.2f", v);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s + "%";
    }

    // ------------------------------------------------------------ 아래 판 (줄 6 안내 · 줄 7 ~ 8 최근 결과 · 줄 9 단추)

    private static void bottom(VanillaMenu.MenuGui g, VanillaGui.Title t, GachaSystem.Def cur) {
        ServerPlayer sp = g.sp;
        int have = GachaSystem.tickets(sp, cur.ticket());
        t.text(10, 138, VanillaGui.TICKET, VanillaGui.WHITE);
        t.text(10 + VanillaGui.width(VanillaGui.TICKET) + 3, 138, "가진 뽑기권 " + have + "장", have > 0 ? VanillaGui.CREAM : VanillaGui.RED);
        Gacha.Pity pity = cur.banner().pity();
        if (cur.pool() != null && pity.hard() > 0) {
            Gacha.Counter c = GachaSystem.counter(sp, cur.pool());
            List<String> parts = new ArrayList<>();
            if (pity.heroEvery() > 0) parts.add("영웅까지 " + Math.max(1, pity.heroEvery() - c.n4()));
            if (cur.key().equals(GachaSystem.LIMITED) && c.g5()) parts.add("픽업 확정");
            if (!parts.isEmpty()) t.right(166, 138, String.join(" · ", parts), c.g5() ? VanillaGui.GOLD : VanillaGui.MUTED);
        }
        // 최근 결과 (이 사람의 마지막 뽑기 — 연출을 본 뒤)
        Last last = LAST.get(sp.getUUID());
        if (last != null) {
            for (int k = 0; k < last.icons().size() && k < 10; k++) {
                int r = 7 + k / 5, c = 2 + k % 5;
                t.glyph(VanillaGui.cellX(c), r, "ring", gradeColor(last.grades().get(k)));
                g.set(r, c, last.icons().get(k), null);
            }
        }
        // 단추 (줄 9): 뽑기권 그림 (칸 0 · 5) + 글 (칸 1 ~ 3 · 6 ~ 8 가운데)
        for (int[] b : new int[][]{{0, 1}, {5, Gacha.multi()}}) {
            int c0 = b[0], count = b[1];
            boolean ok = have >= count;
            String label = count + "회 뽑기";
            List<String> lore = List.of("§7" + GachaSystem.ticketName(cur) + " " + count + "장 §8(가진 것 " + have + ")", "",
                    ok ? "§e눌러서 뽑기" : "§c뽑기권이 모자랍니다");
            GuiElement.ClickCallback cb = VanillaMenu.MenuGui.left(() -> ask(g, count));
            g.set(9, c0, VanillaGui.icon(modelOf(new ItemStack(cur.ticket())), (ok ? "§d" : "§8") + label, lore), cb);
            for (int c = c0 + 1; c <= c0 + 3; c++) g.set(9, c, VanillaGui.blank((ok ? "§d" : "§8") + label, lore), cb);
            t.center((VanillaGui.cellX(c0 + 1) + VanillaGui.cellX(c0 + 3) + 17) / 2, 196, label, ok ? VanillaGui.CREAM : VanillaGui.DIM);
        }
    }

    /** 단추 → 확인 창 */
    private static void ask(VanillaMenu.MenuGui g, int count) {
        ServerPlayer sp = g.sp;
        GachaSystem.Def cur = GachaSystem.def(sp.level().getServer(), g.gachaKey);
        if (cur == null) return;
        if (GachaSystem.tickets(sp, cur.ticket()) < count) {
            Notify.notify(sp, "§c" + GachaSystem.ticketName(cur) + "이 모자랍니다 §7(필요 " + count + ")", 50);
            SkillFx.playFor(sp, "block.note_block.bass", 0.7, 0.8);
            return;
        }
        g.gachaConfirm = count;
        SkillFx.playFor(sp, "block.amethyst_block.hit", 1.2, 0.6);
        g.refresh();
    }

    // ------------------------------------------------------------ 확인 창

    private static void confirm(VanillaMenu.MenuGui g, VanillaGui.Title t, GachaSystem.Def cur) {
        ServerPlayer sp = g.sp;
        int count = g.gachaConfirm;
        int have = GachaSystem.tickets(sp, cur.ticket());
        t.center(88, VanillaGui.rowLine(3), "정말로 사용하시겠습니까?", VanillaGui.CREAM);
        g.set(4, 2, VanillaGui.icon(modelOf(new ItemStack(cur.ticket())), "§f" + GachaSystem.ticketName(cur), List.of("§7가진 것 " + have + "장")), null);
        t.text(VanillaGui.cellX(3) + 2, VanillaGui.rowLine(4), VanillaGui.fit(GachaSystem.ticketName(cur) + " ×" + count, 100), VanillaGui.GOLD);
        boolean ok = have >= count;
        t.text(VanillaGui.cellX(3) + 2, VanillaGui.rowLine(5), ok ? "가진 것 " + have + "장 → " + (have - count) + "장" : "모자랍니다 (가진 것 " + have + "장)",
                ok ? VanillaGui.MUTED : VanillaGui.RED);
        GuiElement.ClickCallback yes = VanillaMenu.MenuGui.left(() -> pull(g, count));
        GuiElement.ClickCallback no = VanillaMenu.MenuGui.left(() -> {
            g.gachaConfirm = 0;
            SkillFx.playFor(sp, "ui.button.click", 1.2, 0.3);
            g.refresh();
        });
        for (int c = 2; c <= 3; c++) g.set(6, c, VanillaGui.blank("§a네", List.of("§7" + GachaSystem.ticketName(cur) + " " + count + "장을 써서 뽑기")), yes);
        for (int c = 5; c <= 6; c++) g.set(6, c, VanillaGui.blank("§c아니요", List.of("§7돌아가기")), no);
    }

    /** 네: 뽑고 (룬은 바로 가방) 메뉴를 닫고 연출 → 끝나면 이 쪽 (최근 결과) */
    private static void pull(VanillaMenu.MenuGui g, int count) {
        ServerPlayer sp = g.sp;
        String key = g.gachaKey;
        g.gachaConfirm = 0;
        GachaSystem.Drawn d = GachaSystem.pullFromScreen(sp, key, count);
        if (d.error() != null) {
            g.refresh();
            return;
        }
        List<ItemStack> icons = new ArrayList<>();
        List<String> grades = new ArrayList<>();
        for (int i = 0; i < d.pulls().size(); i++) {
            Gacha.Pull p = d.pulls().get(i);
            boolean fresh = i < d.fresh().size() && d.fresh().get(i);
            icons.add(resultIcon(p, fresh));
            grades.add(p.grade());
        }
        LAST.put(sp.getUUID(), new Last(key, List.copyOf(icons), List.copyOf(grades)));
        g.close();
        GachaCinematic.play(sp, d, () -> VanillaMenu.open(sp, VanillaMenu.Page.GACHA));
    }

    /** 나간 사람의 마지막 뽑기를 지움 (VanillaClients 의 나감) */
    static void forget(UUID id) {
        LAST.remove(id);
    }

    /** 확률 · 천장 안내 (줄 5 칸 6 단추 설명) */
    private static List<String> rates(ServerPlayer sp, GachaSystem.Def d) {
        List<String> out = new ArrayList<>();
        double sum = 0;
        for (double v : d.banner().weights().values()) sum += v;
        for (Map.Entry<String, Double> e : d.banner().weights().entrySet()) {
            if (e.getValue() <= 0 || sum <= 0) continue;
            mmorpg.core.Grade gr = mmorpg.core.Rules.get().grade(e.getKey());
            out.add((gr == null ? "§f" + e.getKey() : gr.color() + gr.name()) + " §f" + String.format(java.util.Locale.ROOT, "%.2f", e.getValue() / sum * 100) + "%");
        }
        Gacha.Pity p = d.banner().pity();
        if (d.pool() != null && p.hard() > 0) out.add("§7초월 천장 §f" + p.hard() + "회");
        if (d.pool() != null && p.heroEvery() > 0) out.add("§7영웅 이상 §f" + p.heroEvery() + "회마다 확정");
        if (d.spec() != null) {
            out.add("");
            out.add("§6픽업 초월 §f" + names(d.spec().featured5(), "transcend"));
            if (!d.spec().featured4().isEmpty()) out.add("§6픽업 영웅 §f" + names(d.spec().featured4(), "hero"));
            out.add("§7픽업 몫 §f" + mmorpg.core.Js.str(d.spec().share()) + "%");
            if (d.spec().end() > 0) out.add("§7" + period(d.spec()));
        }
        return out;
    }

    private static String names(List<String> families, String grade) {
        List<String> out = new ArrayList<>();
        for (String f : families) out.add(strip(runeName(RuneItems.stack(f, grade), grade)));
        return String.join(", ", out);
    }

    /** 최근 기록 (줄 5 칸 7 단추 설명 — 마지막 10개) */
    private static List<String> history(ServerPlayer sp) {
        List<GachaSystem.Entry> h = GachaSystem.state(sp).history();
        List<String> out = new ArrayList<>();
        for (int i = h.size() - 1; i >= 0 && out.size() < 10; i--) {
            Runes.Rune r = Runes.parseRune(h.get(i).rune());
            if (r == null) continue;
            out.add(r.gradeColor() + r.gradeName() + " §f" + r.def().str("name") + (h.get(i).featured() ? " §6픽업" : ""));
        }
        if (out.isEmpty()) out.add("§8아직 뽑은 것이 없습니다");
        return out;
    }

    private static String period(Gacha.Spec s) {
        return "기간 " + mmdd(s.start()) + " ~ " + (s.end() > 0 ? mmdd(s.end()) : "");
    }

    private static String mmdd(long millis) {
        java.time.LocalDateTime t = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault());
        return String.format(java.util.Locale.ROOT, "%02d.%02d", t.getMonthValue(), t.getDayOfMonth());
    }

    /** 결과 칸 그림 (룬 그림 + 등급 이름 · 새로 얻음 · 픽업) */
    static ItemStack resultIcon(Gacha.Pull p, boolean fresh) {
        ItemStack rune = RuneItems.stack(p.family(), p.grade());
        List<String> lore = new ArrayList<>();
        if (fresh) lore.add("§e§lNEW §r§7처음 얻은 룬");
        if (p.featured()) lore.add("§6픽업");
        if (p.lost()) lore.add("§7픽업을 놓침 (다음 초월은 픽업 확정)");
        if (p.at() > 0) lore.add("§7" + p.at() + "번째에 나옴");
        lore.add("§8가방에 들어감");
        ItemStack icon = VanillaGui.icon(modelOf(rune), (fresh ? "§e★ " : "") + runeName(rune, p.grade()), lore);
        int gi = Runes.GRADE_ORDER.indexOf(p.grade());
        if (gi >= 3 || fresh) icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return icon;
    }

    /** 룬 이름 (등급 색 · 등급 · 이름) */
    static String runeName(ItemStack rune, String grade) {
        Runes.Rune r = RuneItems.runeOf(rune);
        if (r != null) return r.gradeColor() + r.gradeName() + " §f" + r.def().str("name");
        mmorpg.core.Grade gd = mmorpg.core.Rules.get().grade(grade);
        return (gd == null ? "§f" : gd.color() + gd.name() + " §f") + rune.getHoverName().getString();
    }

    static Identifier modelOf(ItemStack st) {
        Identifier m = st.isEmpty() ? null : st.get(DataComponents.ITEM_MODEL);
        return m != null ? m : Mmorpg.rpg("vui/blank");
    }

    static String strip(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }
}
