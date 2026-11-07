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
 * 모드 없는 클라이언트의 그림 창 (1번 4b — 사용자 "텍스처랑 넣고 … 최대한 비슷하게", "위르드 서버 처럼"): 상자 메뉴의 제목 글자로 창 바탕 · 글자를 그림.
 *
 * <p>바닐라 상자 메뉴는 제목을 칸 아이템보다 먼저 그림 (1.21.7+ — 아이템이 위). 제목 = [뒤로 8] [바탕 그림 글자 (rpg:gui_bg — 메뉴 쪽 176 × 198,
 * 창 위 12 부터)] [자리 옮김 (rpg:gui_space — 앞뒤 1 ~ 256)] [글자 (줄 글꼴 rpg:gl&lt;d&gt; — 갈무리9 글자판, 세로 d 만큼 아래 ·
 * 숫자 글꼴 rpg:gn&lt;d&gt; — 갈무리7 숫자판)] … 처럼 이어 붙임. 글자 너비는 tools/vanilla_gui.py 가 만든 표 (text_widths.json · num_widths.json —
 * 마인크래프트 비트맵 글꼴 규칙과 같게) 로 셈. 바탕 · 글자판 · 글꼴은 VanillaPack 이 팩에만 넣음 (모드 클라이언트는 읽지 않음).
 *
 * <p>좌표는 4줄 상자 메뉴 (9 × 4 — VanillaMenu) 창 왼쪽 위 기준: 칸 c 의 틀 x = 7 + 18c, 줄 r 의 틀 y = 17 + 18r (가방 줄 4 ~ 6 은
 * 102 + 18(r − 4), 핫바 줄 7 은 160). 글자판 칸 12px 의 위 = 3 + d (갈무리9 글자 = y d + 4 ~ d + 12, 갈무리7 숫자 = d + 6 ~ d + 12).
 */
public final class VanillaGui {
    private VanillaGui() {}

    /** 줄 글꼴 (갈무리9) 의 세로 자리 d: 제목 줄 · 스킬 쪽 머리 줄 둘 · 스탯 트리 갈래 이름 둘 (줄 1 · 2 가운데) · 아래 줄 (줄 3) */
    public static final int[] LINES = {0, 15, 35, 51, 53, 71};
    /** 숫자 글꼴 (갈무리7) 의 세로 자리 d: 장비 쪽 손 +N (칸 위) · 스탯 값 · 아티팩트 +N (칸 아래) */
    public static final int[] NUM_LINES = {1, 38, 67};
    public static final Identifier SPACE = Mmorpg.rpg("gui_space");
    public static final Identifier BG = Mmorpg.rpg("gui_bg");
    /** 메뉴 아이템 설명 상자 (rpg:tooltip/menu_background · menu_frame — VanillaPack) */
    public static final Identifier TOOLTIP = Mmorpg.rpg("menu");

    /** 메뉴 쪽 바탕 (rpg:gui_bg 의 글자 — VanillaPack 이 같은 차례로 넣음): 그림 176 × 198 (창 위 12 부터) */
    public enum Bg {
        HOME("menu_home"), STATS("menu_stats"), SKILLS("menu_skills"), EQUIP("menu_equip"), GACHA("menu_gacha"), TREE("menu_tree");

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
    public static final int MENU_H = 198;
    public static final int MENU_TOP = 12;
    /** 제목 글자가 시작하는 x (바닐라 titleLabelX) */
    static final int TITLE_X = 8;

    /** 그림 글자 (글자판 끝 — 흰색으로 쓰면 그림 색 그대로) */
    public static final String COIN = "\uE100", HEART = "\uE101", GEM = "\uE102", TICKET = "\uE103";

    /** 어두운 바탕 위 */
    public static final int WHITE = 0xFFFFFF, GRAY = 0xAAAAAA, DARK = 0x777777, GOLD = 0xFFC94A, GREEN = 0x9CFF8A, RED = 0xFF6B6B,
            AQUA = 0x6FE6FF, PINK = 0xF08CFF;
    /** 양피지 위 (밝은 판 — 어두운 글자) */
    public static final int INK = 0x4A3524, SUB = 0x8C7052, OK = 0x3F7A34, WARN = 0xA0352E, GOLD_INK = 0x9A6A14, TEAL_INK = 0x1F6F78,
            PURPLE_INK = 0x7A3F8C;

    private static Map<Integer, Integer> widths;
    private static int space = 4;
    private static int fallback = 6;

    /** 칸 c 의 틀 x */
    public static int cellX(int c) {
        return 7 + 18 * c;
    }

    /** 9 × 4 메뉴 줄 r 의 틀 y (0 ~ 3 메뉴 칸, 4 ~ 6 가방, 7 핫바) */
    public static int cellY(int r) {
        return r < 4 ? 17 + 18 * r : r < 7 ? 102 + 18 * (r - 4) : 160;
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
