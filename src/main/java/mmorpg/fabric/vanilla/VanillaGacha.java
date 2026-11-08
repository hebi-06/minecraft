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
 * 룬 뽑기 전용 창 (메뉴의 GACHA 쪽 — 위 탭 없음). 4판 (2026-11-08 사용자 그림 · "뽑기을 들어가면 기존 메인 인터페이스에서 뽑기 전용 인터페이스로
 * 넘어가고 뽑기만 할 수 있는 창으로 넘어가게 하고 싶음 위에 바 있는게 거슬림" · "글자를 최대한 줄여서 깔끔하게 … 마우스 커서를 올리면 설명"):
 * <pre>
 * [←]  ·   (큰 룬 — 진열판 가운데)   ·  [i] [한정]    ← 줄 0: 돌아가기 · 정보 (뽑기 이름 · 기간 · 픽업 · 확률 · 천장) · 목록 칸 8
 *        (마법진 · 받침)                   [일반]       목록: 맨 위 = 관리자가 연 한정 뽑기, 아래 = 일반 · 고급 · 스탯 (그림 + 가진 장 수)
 *                                          [고급]
 *        스탯 뽑기면 줄 0 에 스탯 넷        [스탯]
 *    [🎟 ×1]          [🎟 ×10]              [⌚]       ← 줄 4: 뽑기 단추 · 기록
 *  [▓▓▓▓░░░░░░ 천장 막대 ░░░░]  [선물]     [?]        ← 줄 5: 천장 (마우스를 올리면 n / 90)
 * [ 진짜 가방 ]                                       ← 아래 판
 * </pre>
 * 글자는 단추의 ×1 · ×10 뿐 (나머지는 설명 상자). 단추 → 확인 창 (menu_gacha_confirm — "정말로 사용하시겠습니까?" · 뽑기권 × 수 · 네 / 아니요) →
 * 네 = 뽑기 (GachaSystem.pullFromScreen — 룬은 바로 가방) → 메뉴를 닫고 연출 (GachaCinematic) → 끝나면 이 창. 뽑기권 우클릭 · /뽑기 도 이 창
 * (모드 없는 클라이언트 — GachaSystem).
 */
public final class VanillaGacha {
    private VanillaGacha() {}

    /** 목록 칸 · 큰 룬 줄 */
    static final int LIST_COL = 8, BIG_ROW = 1;
    /** 등급 색 (RuneKit.RUNE_GRADE_FX 와 같음 — 일반 · 희귀 · 고귀 · 영웅 · 초월) */
    static final Map<String, Integer> GRADE_RGB = Map.of("common", 0xE0E0F0, "rare", 0x5C99FF, "noble", 0xB86BFF, "hero", 0xFF9E29,
            "transcend", 0xFF4242);

    /** 마지막으로 고른 뽑기 (메뉴를 닫고 연출을 본 뒤 다시 열 때 — 사람마다) */
    private static final Map<UUID, String> LAST = new ConcurrentHashMap<>();

    static int gradeColor(String grade) {
        return GRADE_RGB.getOrDefault(grade, 0xFFFFFF);
    }

    /** 뽑기 창 열기 (key = 처음 볼 뽑기 — null 이면 마지막 → 한정 → 일반): 홈 타일 · 뽑기권 우클릭 · /뽑기 */
    public static void openFor(ServerPlayer sp, String key) {
        if (key != null && !key.isEmpty()) LAST.put(sp.getUUID(), key);
        VanillaMenu.MenuGui g = VanillaMenu.current(sp);
        if (g != null) {
            if (key != null && !key.isEmpty()) g.gachaKey = key;
            g.show(VanillaMenu.Page.GACHA);
        } else {
            VanillaMenu.open(sp, VanillaMenu.Page.GACHA);
        }
    }

    /** 고른 뽑기 (없으면 마지막 → 한정 → 일반) */
    private static GachaSystem.Def current(VanillaMenu.MenuGui g, List<GachaSystem.Def> defs) {
        if (g.gachaKey == null) g.gachaKey = LAST.get(g.sp.getUUID());
        GachaSystem.Def cur = find(defs, g.gachaKey);
        if (cur == null) cur = find(defs, GachaSystem.LIMITED);
        if (cur == null) cur = find(defs, GachaSystem.NORMAL);
        if (cur == null && !defs.isEmpty()) cur = defs.get(0);
        g.gachaKey = cur == null ? null : cur.key();
        if (cur != null) LAST.put(g.sp.getUUID(), cur.key());
        if (cur != null && cur.key().startsWith(GachaSystem.STAT)) g.gachaStat = cur.key().substring(GachaSystem.STAT.length());
        return cur;
    }

    private static GachaSystem.Def find(List<GachaSystem.Def> defs, String key) {
        if (key == null) return null;
        for (GachaSystem.Def d : defs) if (d.key().equals(key)) return d;
        return null;
    }

    static void render(VanillaMenu.MenuGui g, VanillaGui.Title t) {
        ServerPlayer sp = g.sp;
        List<GachaSystem.Def> defs = GachaSystem.defs(sp.level().getServer());
        GachaSystem.Def cur = current(g, defs);
        if (cur == null) return;
        if (g.gachaConfirm > 0) {
            confirm(g, t, cur);
            return;
        }
        g.set(0, 0, VanillaGui.blank("§f돌아가기", List.of("§7메뉴 홈으로")), VanillaMenu.MenuGui.left(() -> g.show(VanillaMenu.Page.HOME)));
        List<String> info = new ArrayList<>();
        info.add("§6§l" + strip(cur.name()));
        info.addAll(rates(cur));
        g.set(0, 7, VanillaGui.blank("§b뽑기 정보", info), null);
        list(g, t, defs, cur);
        showcase(g, t, cur);
        buttons(g, t, cur);
        pity(g, t, cur);
        g.set(4, 8, VanillaGui.blank("§d최근 뽑기", history(sp)), null);
        g.set(5, 8, VanillaGui.blank("§b룬 뽑기", List.of(
                "§7오른쪽 목록에서 뽑기를 고르고 아래 §f×1 · ×10",
                "§7맨 위는 관리자가 연 §d한정 뽑기 §7(기간 · 픽업 — §fi§7)",
                "§7연출: §f좌클릭 §7계속 · §fShift §7건너뛰기",
                "§7막대: 천장 (가득 차면 초월 확정)",
                "§7뽑은 룬은 바로 가방으로")), null);
    }

    // ------------------------------------------------------------ 목록 (칸 8 · 줄 0 ~ 3 — 한정 · 일반 · 고급 · 스탯)

    private static void list(VanillaMenu.MenuGui g, VanillaGui.Title t, List<GachaSystem.Def> defs, GachaSystem.Def cur) {
        ServerPlayer sp = g.sp;
        GachaSystem.Def stat = find(defs, GachaSystem.STAT + (g.gachaStat != null ? g.gachaStat : firstStat(defs)));
        if (stat == null) for (GachaSystem.Def d : defs) if (stat == null && d.key().startsWith(GachaSystem.STAT)) stat = d;
        GachaSystem.Def[] rows = {find(defs, GachaSystem.LIMITED), find(defs, GachaSystem.NORMAL), find(defs, GachaSystem.ADVANCED), stat};
        String[] names = {"한정 뽑기", "일반 뽑기", "고급 뽑기", "스탯 룬 뽑기"};
        for (int i = 0; i < rows.length; i++) {
            GachaSystem.Def d = rows[i];
            if (d == null) {
                List<String> lore = i == 0 ? List.of("§7지금 열린 한정 뽑기가 없습니다", "§8관리자가 열면 여기에") : List.of("§8없음");
                Identifier m = i == 0 && RuneItems.LIMITED_TICKET != null ? dim(modelOf(new ItemStack(RuneItems.LIMITED_TICKET))) : Mmorpg.rpg("vui/blank");
                g.set(i, LIST_COL, VanillaGui.icon(m, "§8" + names[i], lore), null);
                continue;
            }
            boolean on = d == cur || (i == 3 && cur.key().startsWith(GachaSystem.STAT));
            int have = GachaSystem.tickets(sp, d.ticket());
            if (on) t.glyph(VanillaGui.cellX(LIST_COL) - 2, i, "frame_sel", VanillaGui.GOLD);
            List<String> lore = new ArrayList<>();
            if (i == 0) lore.add("§d" + strip(d.name()) + (d.spec() != null && d.spec().end() > 0 ? " §8~" + mmdd(d.spec().end()) : ""));
            lore.add("§7" + GachaSystem.ticketName(d) + " §f" + have + "장");
            if (!on) lore.add("§a눌러서 보기");
            Identifier model = modelOf(new ItemStack(d.ticket()));
            ItemStack icon = VanillaGui.icon(have > 0 ? model : dim(model), (on ? "§e" : "§f") + names[i], lore);
            if (have > 1) icon.setCount(Math.min(99, have));
            String key = d.key();
            g.set(i, LIST_COL, icon, VanillaMenu.MenuGui.left(() -> pick(g, key)));
        }
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

    /** 어둡게 칠한 그림 (rpg:vdim/&lt;그림&gt; — VanillaPack) */
    private static Identifier dim(Identifier model) {
        return Mmorpg.rpg("vdim/" + model.getPath());
    }

    // ------------------------------------------------------------ 진열판 (칸 0 ~ 7 · 줄 0 ~ 3)

    private static void showcase(VanillaMenu.MenuGui g, VanillaGui.Title t, GachaSystem.Def cur) {
        if (cur.key().equals(GachaSystem.LIMITED) && cur.spec() != null && !cur.spec().featured5().isEmpty()) {
            Gacha.Spec spec = cur.spec();
            List<String> f5 = spec.featured5();
            int n = Math.min(3, f5.size());
            int[][] cols = {{3}, {2, 4}, {1, 3, 5}};
            for (int k = 0; k < n; k++) {
                ItemStack rune = RuneItems.stack(f5.get(k), "transcend");
                List<String> lore = new ArrayList<>();
                lore.add("§6픽업 초월");
                lore.add("§7초월이 나오면 " + mmorpg.core.Js.str(spec.share()) + "% 로 이 룬 §8(놓치면 다음 초월은 확정)");
                if (k == 0 && !spec.featured4().isEmpty()) lore.add("§6픽업 영웅 §f" + names(spec.featured4(), "hero"));
                g.set(BIG_ROW, cols[n - 1][k], VanillaGui.icon(Mmorpg.rpg("vbig/" + modelOf(rune).getPath()), runeName(rune, "transcend"), lore), null);
            }
            return;
        }
        // 그 밖: 뽑기권을 크게 (설명 = 이름 · 확률)
        ItemStack ticket = new ItemStack(cur.ticket());
        List<String> lore = new ArrayList<>(rates(cur));
        g.set(BIG_ROW, 3, VanillaGui.icon(Mmorpg.rpg("vbig/" + modelOf(ticket).getPath()), "§f" + strip(cur.key().startsWith(GachaSystem.STAT) ? cur.name() : cur.name()),
                lore), null);
        if (cur.key().startsWith(GachaSystem.STAT)) statChips(g, t);
    }

    /** 스탯 넷 (줄 0 · 칸 2 ~ 5 — 고른 것 = 주황 빛) */
    private static void statChips(VanillaMenu.MenuGui g, VanillaGui.Title t) {
        ServerPlayer sp = g.sp;
        int k = 0;
        for (Map.Entry<String, StatTree.Ticket> e : StatTree.tickets().entrySet()) {
            if (k >= 4) break;
            String s = e.getKey();
            int col = 2 + k++;
            boolean on = s.equals(g.gachaStat);
            if (on) t.glyph(VanillaGui.cellX(col), 0, "tab_sel", VanillaTree.TAB);
            GachaSystem.Def d = GachaSystem.def(sp.level().getServer(), GachaSystem.STAT + s);
            int have = d == null ? 0 : GachaSystem.tickets(sp, d.ticket());
            g.set(0, col, VanillaGui.ui("icon_" + s, VanillaStats.colorCode(Stats.info(s).str("color")) + e.getValue().name(),
                    List.of("§7가진 것 §f" + have + "장", on ? "§e보는 중" : "§a눌러서 보기")), VanillaMenu.MenuGui.left(() -> {
                if (s.equals(g.gachaStat)) return;
                g.gachaStat = s;
                g.gachaKey = GachaSystem.STAT + s;
                SkillFx.playFor(sp, "ui.button.click", 1.7, 0.3);
                g.refresh();
            }));
        }
    }

    // ------------------------------------------------------------ 단추 (줄 4) · 천장 (줄 5)

    private static void buttons(VanillaMenu.MenuGui g, VanillaGui.Title t, GachaSystem.Def cur) {
        int have = GachaSystem.tickets(g.sp, cur.ticket());
        for (int[] b : new int[][]{{1, 1}, {5, Gacha.multi()}}) {
            int c0 = b[0], count = b[1];
            boolean ok = have >= count;
            List<String> lore = List.of("§7" + GachaSystem.ticketName(cur) + " §f" + count + "장§7을 씁니다", "§8가진 것 " + have + "장",
                    ok ? "§e눌러서 뽑기" : "§c뽑기권이 모자랍니다");
            String name = (ok ? "§d" : "§8") + "[ " + count + "회 뽑기 ]";
            GuiElement.ClickCallback cb = VanillaMenu.MenuGui.left(() -> ask(g, count));
            g.set(4, c0, VanillaGui.icon(have > 0 ? modelOf(new ItemStack(cur.ticket())) : dim(modelOf(new ItemStack(cur.ticket()))), name, lore), cb);
            g.set(4, c0 + 1, VanillaGui.blank(name, lore), cb);
            t.num(VanillaGui.cellX(c0 + 1) + 2, VanillaGui.rowLine(4), "×" + count, ok ? VanillaGui.CREAM : VanillaGui.DIM);
        }
    }

    /** 천장 막대 (채움 글자) · 선물 칸 (5, 7 — 설명 = n / 천장 · 영웅까지 · 픽업 확정) */
    private static void pity(VanillaMenu.MenuGui g, VanillaGui.Title t, GachaSystem.Def cur) {
        Gacha.Pity p = cur.banner().pity();
        List<String> lore = new ArrayList<>();
        boolean has = cur.pool() != null && p.hard() > 0;
        if (!has) {
            g.set(5, 7, VanillaGui.ui("gift_locked", "§8천장 없음", List.of("§7이 뽑기는 천장이 없습니다")), null);
            return;
        }
        Gacha.Counter c = GachaSystem.counter(g.sp, cur.pool());
        int k = Math.max(0, Math.min(VanillaGui.BAR_STEPS, Math.round((float) c.n5() / p.hard() * VanillaGui.BAR_STEPS)));
        boolean near = p.softStart() > 0 && c.n5() >= p.softStart();
        t.glyph(VanillaGui.BAR_X, -1, "bar" + k, near ? 0xFF9E29 : VanillaGui.GOLD);
        lore.add("§7천장 §f" + c.n5() + " §7/ " + p.hard() + " §8(" + p.hard() + "회째 초월 확정)");
        if (p.softStart() > 0) lore.add("§7" + p.softStart() + "회부터 초월 확률이 오름");
        if (p.heroEvery() > 0) lore.add("§7영웅까지 §f" + Math.max(1, p.heroEvery() - c.n4()) + "회");
        if (cur.key().equals(GachaSystem.LIMITED) && c.g5()) lore.add("§e다음 초월은 픽업 확정");
        ItemStack gift = VanillaGui.ui("gift", "§6천장", lore);
        if (near || c.g5()) gift.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        g.set(5, 7, gift, null);
        for (int col = 0; col <= 6; col++) g.set(5, col, VanillaGui.blank("§6천장 §f" + c.n5() + " / " + p.hard(), lore), null);
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
        t.center(88, VanillaGui.rowLine(2), "정말로 사용하시겠습니까?", VanillaGui.CREAM);
        g.set(3, 3, VanillaGui.icon(modelOf(new ItemStack(cur.ticket())), "§f" + GachaSystem.ticketName(cur),
                List.of("§7가진 것 " + have + "장 → " + Math.max(0, have - count) + "장")), null);
        t.num(VanillaGui.cellX(4) + 2, VanillaGui.rowLine(3), "×" + count, have >= count ? VanillaGui.GOLD : VanillaGui.RED);
        GuiElement.ClickCallback yes = VanillaMenu.MenuGui.left(() -> pull(g, count));
        GuiElement.ClickCallback no = VanillaMenu.MenuGui.left(() -> {
            g.gachaConfirm = 0;
            SkillFx.playFor(sp, "ui.button.click", 1.2, 0.3);
            g.refresh();
        });
        for (int c = 2; c <= 3; c++) g.set(5, c, VanillaGui.blank("§a네", List.of("§7" + GachaSystem.ticketName(cur) + " " + count + "장을 써서 뽑기")), yes);
        for (int c = 5; c <= 6; c++) g.set(5, c, VanillaGui.blank("§c아니요", List.of("§7돌아가기")), no);
    }

    /** 네: 뽑고 (룬은 바로 가방) 메뉴를 닫고 연출 → 끝나면 이 창 */
    private static void pull(VanillaMenu.MenuGui g, int count) {
        ServerPlayer sp = g.sp;
        String key = g.gachaKey;
        g.gachaConfirm = 0;
        GachaSystem.Drawn d = GachaSystem.pullFromScreen(sp, key, count);
        if (d.error() != null) {
            g.refresh();
            return;
        }
        LAST.put(sp.getUUID(), key);
        g.close();
        GachaCinematic.play(sp, d, () -> VanillaMenu.open(sp, VanillaMenu.Page.GACHA));
    }

    /** 나간 사람의 마지막 뽑기를 지움 */
    static void forget(UUID id) {
        LAST.remove(id);
    }

    /** 확률 · 천장 · 픽업 · 기간 (정보 단추 · 큰 그림 설명) */
    private static List<String> rates(GachaSystem.Def d) {
        List<String> out = new ArrayList<>();
        double sum = 0;
        for (double v : d.banner().weights().values()) sum += v;
        for (String gr : Runes.GRADE_ORDER) {
            double w = d.banner().weights().getOrDefault(gr, 0.0);
            if (w <= 0 || sum <= 0) continue;
            mmorpg.core.Grade g = mmorpg.core.Rules.get().grade(gr);
            out.add((g == null ? "§f" + gr : g.color() + g.name()) + " §f" + String.format(java.util.Locale.ROOT, "%.2f", w / sum * 100) + "%");
        }
        Gacha.Pity p = d.banner().pity();
        if (d.pool() != null && p.hard() > 0) out.add("§7초월 천장 §f" + p.hard() + "회");
        if (d.pool() != null && p.heroEvery() > 0) out.add("§7영웅 이상 §f" + p.heroEvery() + "회마다 확정");
        if (d.spec() != null) {
            out.add("");
            out.add("§6픽업 초월 §f" + names(d.spec().featured5(), "transcend"));
            if (!d.spec().featured4().isEmpty()) out.add("§6픽업 영웅 §f" + names(d.spec().featured4(), "hero"));
            out.add("§7픽업 몫 §f" + mmorpg.core.Js.str(d.spec().share()) + "%");
            if (d.spec().end() > 0) out.add("§7기간 §f" + mmdd(d.spec().start()) + " ~ " + mmdd(d.spec().end()));
        }
        return out;
    }

    private static String names(List<String> families, String grade) {
        List<String> out = new ArrayList<>();
        for (String f : families) out.add(strip(runeName(RuneItems.stack(f, grade), grade)));
        return String.join(", ", out);
    }

    /** 최근 기록 (마지막 10개) */
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

    private static String mmdd(long millis) {
        java.time.LocalDateTime t = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), java.time.ZoneId.systemDefault());
        return String.format(java.util.Locale.ROOT, "%02d.%02d", t.getMonthValue(), t.getDayOfMonth());
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
