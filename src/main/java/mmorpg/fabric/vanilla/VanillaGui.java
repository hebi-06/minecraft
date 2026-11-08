package mmorpg.fabric.vanilla;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mmorpg.fabric.Mmorpg;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 모드 없는 클라이언트의 그림 창 (1번 4b): 상자 메뉴의 제목 글자로 창 바탕 · 글자를 그림.
 *
 * <p>바닐라 상자 메뉴는 제목을 칸 아이템보다 먼저 그림 (1.21.7+ — 아이템이 위). 제목 = [뒤로 8] [바탕 그림 글자 (rpg:gui_bg — 메뉴 쪽 176 × 236,
 * 창 위 14 부터)] [자리 옮김 (rpg:gui_space — 앞뒤 1 ~ 256)] [글자 (줄 글꼴 rpg:gl&lt;d&gt; — 갈무리9 글자판, 세로 d 만큼 아래 ·
 * 숫자 글꼴 rpg:gn&lt;d&gt; — 갈무리7 숫자판)] … 처럼 이어 붙임. 글자 너비는 tools/vanilla_gui.py 가 만든 표 (text_widths.json · num_widths.json —
 * 마인크래프트 비트맵 글꼴 규칙과 같게) 로 셈. 바탕 · 글자판 · 글꼴은 VanillaPack 이 팩에만 넣음 (모드 클라이언트는 읽지 않음).
 *
 * <p>메뉴 2판 (2026-11-08 — 사용자 "색이나 배열이나 그런거 다르게", "바닐라 아이템으로 꾸미는가 너무 밤티남"): 6줄 상자 메뉴 (9 × 6), 모드 창과 같은
 * 남색 판 · 금테. 좌표는 창 왼쪽 위 기준: 칸 c 의 틀 x = 7 + 18c, 줄 r 의 틀 y = 17 + 18r (가방 줄 6 ~ 8 은 138 + 18(r − 6), 핫바 줄 9 는 196).
 * 글자판 칸 12px 의 위 = 3 + d (갈무리9 글자 = y d + 4 ~ d + 12, 갈무리7 숫자 = d + 6 ~ d + 12).
 */
public final class VanillaGui {
    private VanillaGui() {}

    /**
     * 줄 글꼴 (갈무리9) 의 세로 자리 d: 제목 줄 0 · 줄 1 ~ 5 가운데 (35 · 53 · 71 · 89 · 107) · 스킬 쪽 직업 안내 (103) ·
     * 아래 판 줄 6 ~ 9 (138 · 156 · 174 · 196 — 스탯 트리 · 뽑기 쪽이 가방 자리를 씀) · 창 밑 (223 — "[ 힘 스탯 트리 ]")
     */
    public static final int[] LINES = {0, 17, 35, 53, 71, 89, 103, 107, 138, 156, 174, 196, 223};
    /** 숫자 글꼴 (갈무리7) 의 세로 자리 d: 줄 1 ~ 5 가운데 (35 · 53 · 71 · 89 · 107 — 장비 강화 +N) · 스탯 값 (68) · 효과 (78) · 아래 판 (138 · 156 · 174 · 196) */
    public static final int[] NUM_LINES = {35, 53, 68, 71, 78, 89, 107, 138, 156, 174, 196};
    public static final Identifier SPACE = Mmorpg.rpg("gui_space");
    public static final Identifier BG = Mmorpg.rpg("gui_bg");
    /** 메뉴 아이템 설명 상자 (rpg:tooltip/menu_background · menu_frame — VanillaPack) */
    public static final Identifier TOOLTIP = Mmorpg.rpg("menu");

    /** 메뉴 쪽 바탕 (rpg:gui_bg 의 글자 — VanillaPack 이 같은 차례로 넣음): 그림 176 × 236 (창 위 14 부터) */
    public enum Bg {
        HOME("menu_home"), STATS("menu_stats"), EQUIP("menu_equip"), SKILLS("menu_skills"), TREE("menu_tree"), GACHA("menu_gacha"),
        GACHA_CONFIRM("menu_gacha_confirm"),
        /** 2026-11-08 Forms 상자 창 · 상자 메뉴 (창고 · 쓰레기통) — 줄 1 ~ 6 (containerTitle) */
        FORM1("form_1"), FORM2("form_2"), FORM3("form_3"), FORM4("form_4"), FORM5("form_5"), FORM6("form_6"),
        /** 2026-11-08 꾸미기 전용 창 (VanillaCosmeticPage) */
        COSMETIC("menu_cosmetic");

        public final String file;

        Bg(String file) {
            this.file = file;
        }

        public char glyph() {
            return (char) ('\uE000' + ordinal());
        }
    }

    /** 바탕 그림 크기 · 위 여유 (그림 위가 창 위보다 MENU_TOP 위 — 탭 막대 걸이) */
    public static final int BG_W = 176;
    public static final int MENU_H = 236;
    public static final int MENU_TOP = 14;
    /** 제목 글자가 시작하는 x (바닐라 titleLabelX) */
    static final int TITLE_X = 8;

    /**
     * 2026-11-08 상자 창 제목 (Forms 상자 창 · 창고 · 쓰레기통 — 메뉴와 같은 남색 · 금테 바탕 form_&lt;rows&gt; + 제목 글자 크림색, § 서식은 뺌).
     * 바탕 그림은 줄 글꼴이 있는 클라이언트 (모드 없는 클라이언트 — 팩) 에만 — 모드 클라이언트에는 plainTitle 그대로.
     */
    public static Component containerTitle(int rows, String title) {
        Bg bg = switch (Math.max(1, Math.min(6, rows))) {
            case 1 -> Bg.FORM1;
            case 2 -> Bg.FORM2;
            case 3 -> Bg.FORM3;
            case 4 -> Bg.FORM4;
            case 5 -> Bg.FORM5;
            default -> Bg.FORM6;
        };
        String plain = title == null ? "" : title.replaceAll("§.", "").trim();
        return new Title().background(bg).text(TITLE_X, 0, fit(plain, BG_W - 2 * TITLE_X), CREAM).build();
    }

    /** 바닐라 클라이언트면 containerTitle, 모드 클라이언트면 글자 그대로 */
    public static Component containerTitle(net.minecraft.server.level.ServerPlayer sp, int rows, String title) {
        return VanillaClients.isVanilla(sp) ? containerTitle(rows, title) : Component.literal(title == null ? "" : title);
    }

    /** 그림 글자 (글자판 끝 — 흰색으로 쓰면 그림 색 그대로) */
    public static final String COIN = "\uE100", HEART = "\uE101", GEM = "\uE102", TICKET = "\uE103", POWER = "\uE104";

    /** 남색 판 위 글자 색 */
    public static final int WHITE = 0xFFFFFF, CREAM = 0xF0E4C8, GOLD = 0xFFD678, MUTED = 0x969ABE, DIM = 0x5C6084, GREEN = 0x8CF096,
            RED = 0xF06E6E, TEAL = 0x6EDCE6, PINK = 0xF0A0F0, GRAY = 0xAAAAAA;

    private static Map<Integer, Integer> widths;
    private static int space = 4;
    private static int fallback = 6;

    /** 줄 글자 (선 · 칸 테 · 탭 빛 · 결과 테 — rpg:gt&lt;줄&gt;) 가 있는 줄 (0 ~ 9) · 나머지 글자 (세모 · 스크롤 손잡이 — rpg:gtx) */
    public static final int GLYPH_ROWS = 10;
    /** 세모 (탭 아래) 의 y · 스크롤 손잡이 (나무 판 오른쪽) 의 y — tools/vanilla_gui.py 와 같음 */
    public static final int TRI_Y = 215, THUMB_Y = 38, THUMB_X = 168, THUMB_STEPS = 8;
    /** 뽑기 창의 천장 막대 채움 (rpg:gtx bar&lt;k&gt; — 왼쪽 x · 위 y · 칸 수) */
    public static final int BAR_X = 11, BAR_Y = 113, BAR_STEPS = 20;

    /** 글자 (tree_glyphs.json — 이름 → 글자 · 너비) */
    private static Map<String, String[]> glyphs;

    private static synchronized Map<String, String[]> glyphs() {
        if (glyphs != null) return glyphs;
        Map<String, String[]> m = new HashMap<>();
        try (InputStream in = VanillaGui.class.getResourceAsStream("/mmorpg/vanilla/gui/tree_glyphs.json")) {
            if (in != null) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("glyphs");
                for (Map.Entry<String, com.google.gson.JsonElement> e : o.entrySet()) {
                    com.google.gson.JsonArray a = e.getValue().getAsJsonArray();
                    m.put(e.getKey(), new String[]{a.get(0).getAsString(), a.get(1).getAsString()});
                }
            }
        } catch (Exception e) {
            Mmorpg.LOG.warn("[vanilla] gui glyphs: {}", e.toString());
        }
        glyphs = m;
        return m;
    }

    /** 줄 r 의 글자 글꼴 (선 · 칸 테 — 칸 틀 위 · 왼쪽 기준) */
    static Identifier glyphFont(int row) {
        return Mmorpg.rpg("gt" + row);
    }

    static final Identifier GLYPH_X = Mmorpg.rpg("gtx");

    /** 칸 c 의 틀 x */
    public static int cellX(int c) {
        return 7 + 18 * c;
    }

    /** 9 × 6 메뉴 줄 r 의 틀 y (0 ~ 5 메뉴 칸, 6 ~ 8 가방, 9 핫바) */
    public static int cellY(int r) {
        return r < 6 ? 17 + 18 * r : r < 9 ? 138 + 18 * (r - 6) : 196;
    }

    /** 줄 r (0 ~ 5) 가운데에 맞춘 글자 줄 d (갈무리9 · 갈무리7 둘 다 — 줄 1 ~ 5 = 35 · 53 · 71 · 89 · 107) */
    public static int rowLine(int r) {
        return 17 + 18 * r;
    }

    /** 칸 c 의 가운데 x */
    public static int cellMid(int c) {
        return 16 + 18 * c;
    }

    static Identifier lineFont(int d) {
        return Mmorpg.rpg("gl" + d);
    }

    static Identifier numFont(int d) {
        return Mmorpg.rpg("gn" + d);
    }

    private static synchronized Map<Integer, Integer> widths() {
        if (widths != null) return widths;
        Map<Integer, Integer> m = new HashMap<>();
        try (InputStream in = VanillaGui.class.getResourceAsStream("/mmorpg/vanilla/gui/text_widths.json")) {
            if (in != null) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                space = o.get("space").getAsInt();
                fallback = o.get("default").getAsInt();
                for (Map.Entry<String, com.google.gson.JsonElement> e : o.getAsJsonObject("widths").entrySet()) {
                    m.put(e.getKey().codePointAt(0), e.getValue().getAsInt());
                }
            }
        } catch (Exception e) {
            Mmorpg.LOG.warn("[vanilla] gui text widths: {}", e.toString());
        }
        widths = m;
        return m;
    }

    private static Map<Integer, Integer> numWidths;
    private static int numSpace = 3;

    private static synchronized Map<Integer, Integer> numWidths() {
        if (numWidths != null) return numWidths;
        Map<Integer, Integer> m = new HashMap<>();
        try (InputStream in = VanillaGui.class.getResourceAsStream("/mmorpg/vanilla/gui/num_widths.json")) {
            if (in != null) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                numSpace = o.get("space").getAsInt();
                for (Map.Entry<String, com.google.gson.JsonElement> e : o.getAsJsonObject("widths").entrySet()) {
                    m.put(e.getKey().codePointAt(0), e.getValue().getAsInt());
                }
            }
        } catch (Exception e) {
            Mmorpg.LOG.warn("[vanilla] gui number widths: {}", e.toString());
        }
        numWidths = m;
        return m;
    }

    /** 숫자 글꼴 (갈무리7) 의 너비 — 숫자판에 없는 글자는 빈칸 */
    public static int numWidth(String s) {
        Map<Integer, Integer> w = numWidths();
        int n = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            n += w.getOrDefault(cp, numSpace);
        }
        return n;
    }

    /** 숫자판에 없는 글자는 빈칸으로 */
    static String numDrawable(String s) {
        Map<Integer, Integer> w = numWidths();
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            out.appendCodePoint(w.containsKey(cp) ? cp : ' ');
        }
        return out.toString();
    }

    /** 1000 이상은 "1.2k" (숫자 칸 하나에 들어가게) */
    public static String compact(long v) {
        if (Math.abs(v) < 1000) return String.valueOf(v);
        double k = v / 1000.0;
        return (Math.abs(k) < 10 ? String.format(java.util.Locale.ROOT, "%.1f", k) : String.valueOf(Math.round(k))) + "k";
    }

    /** 1,234,567 (쉼표) */
    public static String commas(long v) {
        return String.format(java.util.Locale.ROOT, "%,d", v);
    }

    /** 글자 너비 (줄 글꼴 — § 서식은 0) */
    public static int width(String s) {
        Map<Integer, Integer> w = widths();
        int n = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '§') {
                i++;
                continue;
            }
            n += cp == ' ' ? space : w.getOrDefault(cp, fallback);
        }
        return n;
    }

    /** 글자 하나의 너비 (줄 글꼴 — 글자판 그림 너비 + 1, 빈칸은 space) */
    static int charWidth(int cp) {
        return cp == ' ' ? space : widths().getOrDefault(cp, fallback);
    }

    /** 줄 글꼴 글자판에 있는 글자인지 (없으면 그리지 못함 — 빈칸으로 바꿈) */
    static String drawable(String s) {
        Map<Integer, Integer> w = widths();
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == ' ' || w.containsKey(cp)) out.appendCodePoint(cp);
            else out.append(' ');
        }
        return out.toString();
    }

    /** 너비가 max 를 넘으면 뒤를 자르고 "…" */
    public static String fit(String s, int max) {
        if (width(s) <= max) return s;
        String dots = "…";
        int dw = width(dots);
        StringBuilder out = new StringBuilder();
        int n = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int cw = width(new String(Character.toChars(cp)));
            if (n + cw + dw > max) break;
            out.appendCodePoint(cp);
            n += cw;
            i += Character.charCount(cp);
        }
        return out + dots;
    }

    /** 자리 옮김 글자 (2 의 거듭제곱 칸 — 앞 U+F811 ~, 뒤 U+F801 ~) */
    static String spaces(int dx) {
        StringBuilder b = new StringBuilder();
        int left = Math.abs(dx);
        char base = dx < 0 ? '' : '';
        for (int bit = 8; bit >= 0; bit--) {
            int v = 1 << bit;
            while (left >= v) {
                b.append((char) (base + bit));
                left -= v;
            }
        }
        return b.toString();
    }

    /** 제목 (바탕 + 글자들) 만들기 — 글자는 넣은 차례로 그려지고, 칸 자리는 앞 글자 너비로 셈 */
    public static final class Title {
        private final MutableComponent root = Component.empty().withStyle(Style.EMPTY.withColor(WHITE));
        /** 지금 글자 자리 (창 왼쪽 기준) */
        private int cursor = TITLE_X;

        private void move(int x) {
            if (x == cursor) return;
            root.append(Component.literal(spaces(x - cursor)).withStyle(Style.EMPTY.withFont(new FontDescription.Resource(SPACE))));
            cursor = x;
        }

        /** 바탕 그림 (창 왼쪽 위에 — 그림 너비 + 1 만큼 나아감) */
        public Title background(Bg bg) {
            move(0);
            root.append(Component.literal(String.valueOf(bg.glyph())).withStyle(Style.EMPTY.withFont(new FontDescription.Resource(BG)).withColor(WHITE)));
            cursor = BG_W + 1;
            return this;
        }

        /** 숫자 (갈무리7 숫자판 — 숫자 · + - % . / : , × k, 그 밖은 빈칸) x 부터 · 줄 d */
        public Title num(int x, int line, String s, int rgb) {
            if (s == null || s.isEmpty()) return this;
            s = numDrawable(s);
            move(x);
            root.append(Component.literal(s).withStyle(Style.EMPTY.withFont(new FontDescription.Resource(numFont(line))).withColor(rgb)));
            cursor += numWidth(s);
            return this;
        }

        /** 숫자를 가운데 x 에 */
        public Title numCenter(int x, int line, String s, int rgb) {
            return num(x - (numWidth(s) - 1) / 2, line, s, rgb);
        }

        /** 글자를 가운데 x 에 */
        public Title center(int x, int line, String s, int rgb) {
            return text(x - (width(drawable(s)) - 1) / 2, line, s, rgb);
        }

        /** 지금 글자 자리 (다음 글자가 이어질 x) */
        public int cursor() {
            return cursor;
        }

        /** x (창 왼쪽 기준) · 줄 d 에 글자 (색 rgb) */
        public Title text(int x, int line, String s, int rgb) {
            if (s == null || s.isEmpty()) return this;
            String t = drawable(s);
            move(x);
            root.append(Component.literal(t).withStyle(Style.EMPTY.withFont(new FontDescription.Resource(lineFont(line))).withColor(rgb)));
            cursor += width(t);
            return this;
        }

        /**
         * 그림 글자 (tree_glyphs.json 의 이름 — 흰 그림을 색 rgb 로 물들임): 줄 글자 (row 0 ~ 9 — 선 반쪽 · 칸 테 · 탭 빛 · 결과 테 · 목록 테) 는
         * x = 그 칸 틀 왼쪽 기준 (칸 테는 2px, 목록 테는 1px 바깥에서 시작 — 그만큼 빼서 줌), row = -1 이면 rpg:gtx (세모 · 스크롤 손잡이)
         */
        public Title glyph(int x, int row, String name, int rgb) {
            String[] g = glyphs().get(name);
            if (g == null) return this;
            move(x);
            Identifier f = row < 0 ? GLYPH_X : glyphFont(row);
            root.append(Component.literal(g[0]).withStyle(Style.EMPTY.withFont(new FontDescription.Resource(f)).withColor(rgb)));
            cursor += Integer.parseInt(g[1]);
            return this;
        }

        /** 오른쪽 끝이 x 가 되게 */
        public Title right(int x, int line, String s, int rgb) {
            return text(x - width(drawable(s)), line, s, rgb);
        }

        /** 여러 색 조각을 이어서 (x 부터) — 조각 = {글자, 색} */
        public Title parts(int x, int line, List<Object[]> parts) {
            int at = x;
            for (Object[] p : parts) {
                String s = (String) p[0];
                text(at, line, s, (Integer) p[1]);
                at += width(drawable(s));
            }
            return this;
        }

        public Component build() {
            return root;
        }
    }

    // ------------------------------------------------------------ 칸 아이템

    /** 그림만 있는 아이템 (종이 + 아이템 모델) — 이름 · 설명 (기울임 없이) */
    public static ItemStack icon(Identifier model, String name, List<String> lore) {
        ItemStack st = new ItemStack(Items.PAPER);
        st.set(DataComponents.ITEM_MODEL, model);
        return named(st, name, lore);
    }

    /** 칸 그림 rpg:vui/&lt;이름&gt; (창 그림 · 단추 그림) */
    public static ItemStack ui(String sprite, String name, List<String> lore) {
        return icon(Mmorpg.rpg("vui/" + VanillaInput.iconId(sprite)), name, lore);
    }

    /** 보이지 않는 단추 (바탕 그림의 단추 위 — 마우스를 올리면 설명) */
    public static ItemStack blank(String name, List<String> lore) {
        return ui("blank", name, lore);
    }

    /** 이름 · 설명 붙이기 (복사본 — 메뉴 설명 상자 모양 rpg:menu) */
    public static ItemStack named(ItemStack stack, String name, List<String> lore) {
        ItemStack st = stack.copy();
        st.set(DataComponents.TOOLTIP_STYLE, TOOLTIP);
        st.set(DataComponents.CUSTOM_NAME, Component.literal(name == null || name.isEmpty() ? " " : name).withStyle(Style.EMPTY.withItalic(false)));
        List<Component> lines = new ArrayList<>();
        if (lore != null) for (String l : lore) lines.add(Component.literal(l).withStyle(Style.EMPTY.withItalic(false)));
        st.set(DataComponents.LORE, new ItemLore(lines));
        return st;
    }

    /** 쿨타임 묶음 (서버가 보내는 쿨타임 덮개가 이 그림에도 그려지게 — 진짜 아이템 · 직업 스킬과 같은 묶음) */
    public static ItemStack cooldown(ItemStack icon, Identifier group) {
        if (group != null) icon.set(DataComponents.USE_COOLDOWN, new net.minecraft.world.item.component.UseCooldown(0.01F, java.util.Optional.of(group)));
        return icon;
    }

    /** 진짜 아이템의 쿨타임 묶음 (USE_COOLDOWN 의 묶음, 없으면 아이템 id) */
    public static Identifier cooldownGroup(ItemStack real) {
        if (real == null || real.isEmpty()) return null;
        net.minecraft.world.item.component.UseCooldown c = real.get(DataComponents.USE_COOLDOWN);
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(real.getItem());
        return c != null ? c.cooldownGroup().orElse(id) : id;
    }

    /** 바닐라 § 색 (0 ~ f) 의 RGB */
    private static final int[] LEGACY = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    /** § 색 글자 (Stats.info color 등 — 처음 나오는 색) → RGB */
    public static int legacyColor(String code, int def) {
        if (code == null) return def;
        for (int i = 0; i + 1 < code.length(); i++) {
            if (code.charAt(i) != '§') continue;
            int v = Character.digit(code.charAt(i + 1), 16);
            if (v >= 0) return LEGACY[v];
        }
        return def;
    }
}
