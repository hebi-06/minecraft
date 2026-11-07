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
 * 메뉴의 뽑기 쪽 (VanillaMenu — 홈의 뽑기 카드, 사용자 "뽑기 들어가면 뽑기"). 모드 클라이언트의 뽑기 창 (GachaScreen) 대신:
 * <pre>
 * 뽑기 · 일반 뽑기                              천장 37/90
 * [일반][고급][한정][힘][민첩][마력][체력] [ ]     줄 0 — 뽑기 종류 (고른 것은 빛남)
 * [결과 1 ~ 5]                     [  1회  ]       줄 1
 * [결과 6 ~ 10]                    [ 10회  ]       줄 2
 * 뽑기권 12장 · 영웅 확정까지 4                     줄 3
 * </pre>
 * 뽑기 = GachaSystem.pullFromScreen (뽑기권 · 천장 · 기록 · 룬 주기 — 모드 창과 같음). 결과는 칸마다 하나씩 펼침 (3틱마다 · 등급마다 소리),
 * 다 펼치면 GachaSystem.revealNow (영웅 이상 알림 · 빛기둥).
 */
public final class VanillaGacha {
    private VanillaGacha() {}

    static void render(VanillaMenu.MenuGui g, VanillaGui.Title t) {
        ServerPlayer sp = g.sp;
        List<GachaSystem.Def> defs = GachaSystem.defs(sp.level().getServer());
        GachaSystem.Def cur = null;
        for (GachaSystem.Def d : defs) if (d.key().equals(g.gachaKey)) cur = d;
        if (cur == null && !defs.isEmpty()) cur = defs.get(0);
        g.gachaKey = cur == null ? null : cur.key();

        t.text(31, 0, "뽑기", VanillaGui.INK);
        if (cur != null) t.text(31 + VanillaGui.width("뽑기") + 5, 0, "· " + VanillaGui.fit(strip(cur.name()), 52), VanillaGui.SUB);
        // 뽑기 종류 (줄 0 · 칸 1 ~ 8)
        for (int i = 0; i < defs.size() && i < 8; i++) {
            GachaSystem.Def d = defs.get(i);
            boolean on = d == cur;
            int have = GachaSystem.tickets(sp, d.ticket());
            List<String> lore = new ArrayList<>();
            lore.add("§7" + GachaSystem.ticketName(d) + " §f" + have + "장");
            Gacha.Pity pity = d.banner().pity();
            if (d.pool() != null && pity.hard() > 0) lore.add("§7천장 §f" + GachaSystem.counter(sp, d.pool()).n5() + " / " + pity.hard());
            lore.add("");
            lore.add(on ? "§e골랐음" : "§a눌러서 고르기");
            ItemStack icon = VanillaGui.icon(modelOf(new ItemStack(d.ticket())), (on ? "§e▶ " : "§f") + strip(d.name()), lore);
            if (on) icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            String key = d.key();
            g.set(0, 1 + i, icon, VanillaMenu.MenuGui.left(() -> {
                if (key.equals(g.gachaKey)) return;
                g.gachaKey = key;
                g.gachaResults = List.of();
                g.gachaShown = 0;
                g.gachaSeq++;
                g.refresh();
            }));
        }
        if (cur == null) return;
        // 결과 (줄 1 ~ 2 · 칸 1 ~ 5)
        for (int i = 0; i < g.gachaShown && i < g.gachaResults.size() && i < 10; i++) {
            g.set(1 + i / 5, 1 + i % 5, g.gachaResults.get(i), null);
        }
        // 뽑기 단추 (칸 7 ~ 8 · 줄 1 = 1회, 줄 2 = 10회)
        int have = GachaSystem.tickets(sp, cur.ticket());
        int multi = Gacha.multi();
        button(g, 1, 1, have, cur);
        button(g, 2, multi, have, cur);
        // 아래 줄: 뽑기권 · 천장
        t.text(30, 71, VanillaGui.TICKET, VanillaGui.WHITE);
        t.text(30 + VanillaGui.width(VanillaGui.TICKET) + 3, 71, GachaSystem.ticketName(cur) + " " + have + "장", have > 0 ? VanillaGui.INK : VanillaGui.WARN);
        Gacha.Pity pity = cur.banner().pity();
        if (cur.pool() != null && pity.hard() > 0) {
            Gacha.Counter c = GachaSystem.counter(sp, cur.pool());
            t.right(168, 0, "천장 " + c.n5() + "/" + pity.hard(), VanillaGui.SUB);
            if (pity.heroEvery() > 0) t.right(168, 71, "영웅까지 " + Math.max(1, pity.heroEvery() - c.n4()), VanillaGui.SUB);
        }
    }

    private static void button(VanillaMenu.MenuGui g, int row, int count, int have, GachaSystem.Def d) {
        boolean ok = have >= count;
        List<String> lore = List.of("§7" + GachaSystem.ticketName(d) + " " + count + "장 §8(가진 것 " + have + ")", "",
                ok ? "§e눌러서 뽑기" : "§c뽑기권이 모자랍니다");
        String name = (ok ? "§d" : "§8") + count + "회 뽑기";
        String key = d.key();
        for (int c = 7; c <= 8; c++) {
            g.set(row, c, VanillaGui.blank(name, lore), VanillaMenu.MenuGui.left(() -> pull(g, key, count)));
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
        g.refresh();
        SkillFx.playFor(sp, "block.amethyst_block.chime", 0.8, 1.0);
        int n = icons.size();
        for (int i = 0; i < n; i++) {
            int k = i;
            Tasks.later(6 + i * 3, () -> {
                if (g.gachaSeq != seq || !g.isOpen() || g.page != VanillaMenu.Page.GACHA) return;
                g.gachaShown = k + 1;
                g.set(1 + k / 5, 1 + k % 5, icons.get(k), null);
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
