package mmorpg.fabric.vanilla;

import mmorpg.core.Gacha;
import mmorpg.core.Runes;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.gacha.GachaSystem;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.skill.SkillFx;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 메뉴의 룬 뽑기 쪽 (VanillaMenu — 홈의 뽑기 타일). 2판 (사용자 "Claude 가 새로 디자인") — 마법진 · 카드 뒤집기:
 * <pre>
 * 룬 뽑기 · 일반 뽑기                          천장 37/90
 * [일반][고급][한정][힘][민첩][마력][체력]   [확률]    줄 1 — 뽑기 종류 (고른 것은 빛남)
 *  ◆      [결과 1 ~ 5]       ◆                       줄 2 (마법진 위 · 양쪽 수정)
 *         [결과 6 ~ 10]                              줄 3
 *        뽑기권 12장 · 영웅까지 4                     줄 4
 * [  1회 뽑기  ]           [  10회 뽑기  ]             줄 5
 * </pre>
 * 뽑기 = GachaSystem.pullFromScreen (뽑기권 · 천장 · 기록 · 룬 주기 — 모드 창과 같음). 결과는 먼저 카드 뒷면, 칸마다 하나씩 뒤집음 (3틱마다 · 등급마다 소리),
 * 다 펼치면 GachaSystem.revealNow (영웅 이상 알림 · 빛기둥).
 */
public final class VanillaGacha {
    private VanillaGacha() {}

    /** 결과 칸 i (0 ~ 9) 의 자리 — 마법진 위 줄 2 ~ 3 · 칸 2 ~ 6 */
    static int resultRow(int i) {
        return 2 + i / 5;
    }

    static int resultCol(int i) {
        return 2 + i % 5;
    }

    static void render(VanillaMenu.MenuGui g, VanillaGui.Title t) {
        ServerPlayer sp = g.sp;
        List<GachaSystem.Def> defs = GachaSystem.defs(sp.level().getServer());
        GachaSystem.Def cur = null;
        for (GachaSystem.Def d : defs) if (d.key().equals(g.gachaKey)) cur = d;
        if (cur == null && !defs.isEmpty()) cur = defs.get(0);
        g.gachaKey = cur == null ? null : cur.key();

        VanillaMenu.title(t, "룬 뽑기");
        if (cur != null) t.text(10 + VanillaGui.width("룬 뽑기") + 5, 0, "· " + VanillaGui.fit(strip(cur.name()), 62), VanillaGui.CREAM);
        // 뽑기 종류 (줄 1 · 칸 0 ~ 6)
        for (int i = 0; i < defs.size() && i < 7; i++) {
            GachaSystem.Def d = defs.get(i);
            boolean on = d == cur;
            int have = GachaSystem.tickets(sp, d.ticket());
            List<String> lore = new ArrayList<>();
            lore.add("§7" + GachaSystem.ticketName(d) + " §f" + have + "장");
            Gacha.Pity pity = d.banner().pity();
            if (d.pool() != null && pity.hard() > 0) lore.add("§7천장 §f" + GachaSystem.counter(sp, d.pool()).n5() + " / " + pity.hard());
            lore.add("");
            lore.add(on ? "§e골랐음" : "§a눌러서 고르기");
            ItemStack icon = VanillaGui.icon(modelOf(d.ticket() == null ? ItemStack.EMPTY : new ItemStack(d.ticket())), (on ? "§e▶ " : "§f") + strip(d.name()), lore);
            if (on) icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            String key = d.key();
            g.set(1, i, icon, VanillaMenu.MenuGui.left(() -> {
                if (key.equals(g.gachaKey)) return;
                g.gachaKey = key;
                g.gachaResults = List.of();
                g.gachaShown = 0;
                g.gachaSeq++;
                g.refresh();
            }));
        }
        if (cur == null) return;
        g.set(1, 8, VanillaGui.blank("§b확률 · 천장", rates(sp, cur)), null);
        // 결과 (마법진 위 — 아직 안 펼친 것은 카드 뒷면)
        for (int i = 0; i < g.gachaResults.size() && i < 10; i++) {
            g.set(resultRow(i), resultCol(i), i < g.gachaShown ? g.gachaResults.get(i) : back(), null);
        }
        // 뽑기 단추 (줄 5 · 칸 0 ~ 3 = 1회, 칸 5 ~ 8 = 10회)
        int have = GachaSystem.tickets(sp, cur.ticket());
        button(g, 0, 1, have, cur);
        button(g, 5, Gacha.multi(), have, cur);
        // 줄 4 가운데: 뽑기권 · 영웅 확정
        Gacha.Pity pity = cur.banner().pity();
        String line = GachaSystem.ticketName(cur) + " " + have + "장";
        if (cur.pool() != null && pity.hard() > 0) {
            Gacha.Counter c = GachaSystem.counter(sp, cur.pool());
            t.right(166, 0, "천장 " + c.n5() + "/" + pity.hard(), VanillaGui.MUTED);
            if (pity.heroEvery() > 0) line += " · 영웅까지 " + Math.max(1, pity.heroEvery() - c.n4());
        }
        int w = VanillaGui.width(VanillaGui.TICKET) + 3 + VanillaGui.width(line);
        int x = VanillaGui.cellMid(4) - w / 2;
        t.text(x, VanillaGui.rowLine(4), VanillaGui.TICKET, VanillaGui.WHITE);
        t.text(x + VanillaGui.width(VanillaGui.TICKET) + 3, VanillaGui.rowLine(4), line, have > 0 ? VanillaGui.CREAM : VanillaGui.RED);
    }

    /** 카드 뒷면 (펼치기 전) */
    private static ItemStack back() {
        return VanillaGui.ui("card_back", "§8?", List.of());
    }

    /** 확률 · 천장 안내 (오른쪽 위 단추 설명) */
    private static List<String> rates(ServerPlayer sp, GachaSystem.Def d) {
        List<String> out = new ArrayList<>();
        double sum = 0;
        for (double v : d.banner().weights().values()) sum += v;
        for (java.util.Map.Entry<String, Double> e : d.banner().weights().entrySet()) {
            if (e.getValue() <= 0 || sum <= 0) continue;
            mmorpg.core.Grade gr = mmorpg.core.Rules.get().grade(e.getKey());
            out.add((gr == null ? "§f" + e.getKey() : gr.color() + gr.name()) + " §f" + String.format(java.util.Locale.ROOT, "%.2f", e.getValue() / sum * 100) + "%");
        }
        Gacha.Pity p = d.banner().pity();
        if (p.hard() > 0) out.add("§7초월 천장 §f" + p.hard() + "회");
        if (p.heroEvery() > 0) out.add("§7영웅 이상 §f" + p.heroEvery() + "회마다 확정");
        return out;
    }

    private static void button(VanillaMenu.MenuGui g, int c0, int count, int have, GachaSystem.Def d) {
        boolean ok = have >= count;
        List<String> lore = List.of("§7" + GachaSystem.ticketName(d) + " " + count + "장 §8(가진 것 " + have + ")", "",
                ok ? "§e눌러서 뽑기" : "§c뽑기권이 모자랍니다");
        String name = (ok ? "§d" : "§8") + count + "회 뽑기";
        String key = d.key();
        for (int c = c0; c < c0 + 4; c++) {
            g.set(5, c, VanillaGui.blank(name, lore), VanillaMenu.MenuGui.left(() -> pull(g, key, count)));
        }
    }

    /** 뽑기 → 결과를 하나씩 펼침 */
    private static void pull(VanillaMenu.MenuGui g, String key, int count) {
        ServerPlayer sp = g.sp;
        if (g.gachaShown < g.gachaResults.size()) return; // 펼치는 중
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
        long seq = ++g.gachaSeq;
        g.gachaResults = List.copyOf(icons);
        g.gachaShown = 0;
        g.refresh(); // 카드 뒷면 (펼치기 전)
        SkillFx.playFor(sp, "block.amethyst_block.chime", 0.8, 1.0);
        int n = icons.size();
        for (int i = 0; i < n; i++) {
            int k = i;
            Tasks.later(6 + i * 3, () -> {
                if (g.gachaSeq != seq || !g.isOpen() || g.page != VanillaMenu.Page.GACHA) return;
                g.gachaShown = k + 1;
                g.set(resultRow(k), resultCol(k), icons.get(k), null);
                revealSound(sp, grades.get(k), k);
            });
        }
        long id = d.id();
        Tasks.later(6 + n * 3 + 8, () -> {
            GachaSystem.revealNow(sp, id);
            if (g.gachaSeq == seq && g.isOpen() && g.page == VanillaMenu.Page.GACHA) g.refresh();
        });
    }

    /** 펼칠 때 소리 (등급이 높을수록 다름) */
    private static void revealSound(ServerPlayer sp, String grade, int k) {
        int gi = Runes.GRADE_ORDER.indexOf(grade);
        if (gi >= 4) SkillFx.playFor(sp, "item.totem.use", 1.2, 0.6);
        else if (gi == 3) SkillFx.playFor(sp, "entity.player.levelup", 1.3, 0.7);
        else if (gi == 2) SkillFx.playFor(sp, "block.note_block.chime", 1.2 + k * 0.03, 0.6);
        else SkillFx.playFor(sp, "block.note_block.pling", 0.9 + k * 0.06, 0.4);
    }

    /** 결과 칸 그림 (룬 그림 + 등급 이름 · 새로 얻음 · 픽업) */
    private static ItemStack resultIcon(Gacha.Pull p, boolean fresh) {
        ItemStack rune = RuneItems.stack(p.family(), p.grade());
        Runes.Rune r = Runes.parseRune(p.id());
        String name = r == null ? p.id() : r.gradeColor() + r.gradeName() + " §f" + r.def().str("name");
        List<String> lore = new ArrayList<>();
        if (fresh) lore.add("§e§lNEW §r§7처음 얻은 룬");
        if (p.featured()) lore.add("§6픽업");
        if (p.lost()) lore.add("§7픽업을 놓침 (다음 초월은 픽업 확정)");
        if (p.at() > 0) lore.add("§7" + p.at() + "번째에 나옴");
        lore.add("§8가방에 들어감");
        ItemStack icon = VanillaGui.icon(modelOf(rune), (fresh ? "§e★ " : "") + name, lore);
        int gi = Runes.GRADE_ORDER.indexOf(p.grade());
        if (gi >= 3 || fresh) icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return icon;
    }

    private static Identifier modelOf(ItemStack st) {
        Identifier m = st.isEmpty() ? null : st.get(DataComponents.ITEM_MODEL);
        return m != null ? m : Mmorpg.rpg("vui/blank");
    }

    private static String strip(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }
}
