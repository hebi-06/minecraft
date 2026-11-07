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
 * 모드 없는 클라이언트의 그림 창 (1번 4b — 사용자 "텍스처랑 넣고 … 최대한 비슷하게"): 상자 메뉴의 제목 글자로 창 바탕 · 글자를 그림.
 *
 * <p>바닐라 상자 메뉴는 제목을 칸 아이템보다 먼저 그림 (1.21.7+ — 아이템이 위). 제목 = [뒤로 8] [바탕 그림 글자 (rpg:gui_bg, 창 전체 176 × 222)]
 * [자리 옮김 (rpg:gui_space — 앞뒤 1 ~ 256)] [글자 (줄 글꼴 rpg:gl&lt;d&gt; — 갈무리9 글자판, 세로 d 만큼 아래)] … 처럼 이어 붙임.
 * 글자 너비는 tools/vanilla_gui.py 가 만든 표 (mmorpg/vanilla/gui/text_widths.json — 마인크래프트 비트맵 글꼴 규칙과 같게) 로 셈.
 * 바탕 · 글자판 · 글꼴은 VanillaPack 이 팩에만 넣음 (모드 클라이언트는 읽지 않음).
 *
 * <p>좌표는 창 왼쪽 위 기준 (칸 c 의 틀 x = 7 + 18c, 줄 r 의 틀 y = 17 + 18r — 가방 자리 줄 6 ~ 8 은 139 + 18(r − 6), 핫바 줄 9 는 197).
 * 글자 줄의 위 (글자판 칸 12px 의 위) = 3 + d.
 */
public final class VanillaGui {
    private VanillaGui() {}

    /** 줄 글꼴의 세로 자리 d (글자판 칸 위 = 3 + d): 제목 · 칸 줄 0 ~ 5 · 가방 줄 6 ~ 9 · 가방 위 글 */
    public static final int[] LINES = {0, 17, 35, 53, 71, 89, 107, 124, 139, 157, 175, 197};
    public static final Identifier SPACE = Mmorpg.rpg("gui_space");
    public static final Identifier BG = Mmorpg.rpg("gui_bg");
    /** 바탕 그림 글자 (rpg:gui_bg — VanillaPack 과 같은 순서) */
    public static final char BG_STATS = '';
    public static final char BG_SKILLS = '';
    public static final String[] BACKGROUNDS = {"stats", "skills"};
    /** 바탕 그림 크기 (모두 176 × 222 — 6줄 상자 창 전체) */
    public static final int BG_W = 176;
    public static final int BG_H = 222;
    /** 제목 글자가 시작하는 x (바닐라 titleLabelX) */
    static final int TITLE_X = 8;

    public static final int WHITE = 0xFFFFFF, GRAY = 0xAAAAAA, DARK = 0x777777, GOLD = 0xFFC94A, GREEN = 0x7CFF8A, RED = 0xFF6B6B,
            AQUA = 0x6FE6FF, PINK = 0xF08CFF;

    private static Map<Integer, Integer> widths;
    private static int space = 4;
    private static int fallback = 6;

    /** 칸 c 의 틀 x */
    public static int cellX(int c) {
        return 7 + 18 * c;
    }

    /** 줄 r 의 틀 y (0 ~ 5 위 칸, 6 ~ 8 가방, 9 핫바) */
    public static int cellY(int r) {
        return r < 6 ? 17 + 18 * r : r < 9 ? 139 + 18 * (r - 6) : 197;
    }

    /** 줄 r 의 칸 안에 가운데 맞춘 글자 줄 d (= 그 줄의 틀 y) */
    public static int rowLine(int r) {
        return cellY(r);
    }

    static Identifier lineFont(int d) {
        return Mmorpg.rpg("gl" + d);
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
        public Title background(char glyph) {
            move(0);
            root.append(Component.literal(String.valueOf(glyph)).withStyle(Style.EMPTY.withFont(new FontDescription.Resource(BG)).withColor(WHITE)));
            cursor = BG_W + 1;
            return this;
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

    /** 이름 · 설명 붙이기 (복사본) */
    public static ItemStack named(ItemStack stack, String name, List<String> lore) {
        ItemStack st = stack.copy();
        st.set(DataComponents.CUSTOM_NAME, Component.literal(name == null || name.isEmpty() ? " " : name).withStyle(Style.EMPTY.withItalic(false)));
        List<Component> lines = new ArrayList<>();
        if (lore != null) for (String l : lore) lines.add(Component.literal(l).withStyle(Style.EMPTY.withItalic(false)));
        st.set(DataComponents.LORE, new ItemLore(lines));
        return st;
    }

    /** § 색 글자 (Stats.info color 등) → RGB */
    public static int legacyColor(String code, int def) {
        if (code == null) return def;
        for (int i = 0; i + 1 < code.length(); i++) {
            if (code.charAt(i) != '§') continue;
            net.minecraft.ChatFormatting f = net.minecraft.ChatFormatting.getByCode(code.charAt(i + 1));
            if (f != null && f.getColor() != null) return f.getColor();
        }
        return def;
    }
}
