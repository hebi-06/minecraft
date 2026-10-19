package mmorpg.fabric.client.form;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import mmorpg.fabric.ui.FormNet;
import mmorpg.fabric.ui.Slot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 상자 모양 창 (애드온 server_form.json rpg_chest): 회색 상자 테두리 (chest_bg) 안에 제목 · 9 × 3 칸 (slot 20 × 20, 그림 16 × 16) · 본문.
 * 0.21.0 줄 수 = 받은 칸 수 ÷ 9 (1 ~ 6 — 유저 장터는 큰 상자 9 × 6), 진열 칸 (Slot.BIG) 은 그 칸을 가운데로 3 × 3 칸 크기 · 그림 3배.
 * 칸 오른쪽 아래 숫자 — 흰색 = 보유 수, 초록 = 가격 (바닐라 아이템 수 자리). 마우스를 올리면 밝아지고 이름 · 설명. 장식 · 진열 칸은 누를 수 없음.
 * 제목 · 본문은 짙은 회색 (#404040, 그림자 없음 — 애드온 색), 덧붙임 §7 은 바탕에 묻히지 않게 §8 로. 본문이 화면보다 길면 휠로 넘김.
 */
public final class ChestFormScreen extends FormScreen {
    static final int GRID_W = 180;
    static final int CELL = 20;
    static final int PAD_X = 7;
    static final int PAD_Y = 6;
    static final int HEADER = 13;
    static final int GAP = 4;
    static final int LINE = 10;
    static final int TEXT = 0xFF404040;
    /** 진열 칸 그림 크기 (16 × 3 — 3 × 3 칸 60 안) */
    static final int BIG_ICON = 48;
    /** 창 자리를 정하는 본문 줄 수 (실제 줄 수와 상관없이 — 칸 자리가 창마다 같게) */
    static final int NOMINAL_LINES = 4;

    private final FormNet.ChestPayload form;
    private final String title;
    /** 줄 수 (칸 수 ÷ 9 — 보통 3, 0.21.0 큰 상자 6) */
    private final int rows;
    /** 진열 칸 번호 (없으면 -1) — 둘레 8칸은 그리지 않음 */
    private final int big;
    private List<FormattedCharSequence> body = List.of();
    private int left;
    private int top;
    private int w;
    private int h;
    /** 보이는 본문 줄 수 · 넘긴 줄 수 */
    private int bodyLines;
    private int bodyScroll;

    public ChestFormScreen(FormNet.ChestPayload form) {
        super(form.id(), Component.literal(form.title()));
        this.form = form;
        this.title = onLight(form.title());
        this.rows = Math.max(1, Math.min(6, (form.slots().size() + 8) / 9));
        this.big = findBig();
    }

    /** 그릴 칸 수 (줄 × 9 까지) */
    private int cells() {
        return Math.min(rows * 9, form.slots().size());
    }

    private int gridH() {
        return rows * CELL;
    }

    /** 진열 칸: 가장자리가 아닌 첫 BIG 칸 (가장자리의 BIG 은 보통 크기 장식처럼) */
    private int findBig() {
        for (int i = 0; i < cells(); i++) {
            FormNet.SlotData s = form.slots().get(i);
            if (s.empty() || s.kind() != Slot.BIG) continue;
            int r = i / 9;
            int c = i % 9;
            if (r >= 1 && r <= rows - 2 && c >= 1 && c <= 7) return i;
        }
        return -1;
    }

    /** 진열 칸이 덮는 둘레 칸 */
    private boolean covered(int i) {
        return big >= 0 && i != big && Math.abs(i / 9 - big / 9) <= 1 && Math.abs(i % 9 - big % 9) <= 1;
    }

    /** 0.21.0 칸 이름의 "{item}" = 칸 아이템의 이름 (보는 사람의 언어 · 아이템 색 그대로 — 유저 장터의 바닐라 아이템) */
    static final String ITEM = "{item}";
    /** 0.21.0 설명 줄 "{tooltip}" = 칸 아이템의 설명 줄 (보는 사람의 언어 — 무기 · 갑옷 · 룬 · 마법 부여) */
    static final String TOOLTIP = "{tooltip}";
    static final int TOOLTIP_MAX = 12;

    static Component withItemName(String name, ItemStack icon) {
        int at = name.indexOf(ITEM);
        if (at < 0 || icon.isEmpty()) return Component.literal(name.replace(ITEM, ""));
        return Component.literal(name.substring(0, at)).append(icon.getHoverName()).append(Component.literal(name.substring(at + ITEM.length())));
    }

    private List<Component> itemTooltip(ItemStack icon) {
        List<Component> out = new ArrayList<>();
        try {
            List<Component> lines = icon.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL);
            for (int i = 1; i < lines.size() && out.size() < TOOLTIP_MAX; i++) out.add(lines.get(i));
            if (lines.size() - 1 > TOOLTIP_MAX) out.add(Component.literal("§8…"));
        } catch (RuntimeException e) {
            // 설명을 만들 수 없으면 이름만
        }
        return out;
    }

    /** 본문의 "{slot:N}" = 칸 N 아이템의 이름 (보는 사람의 언어) */
    private String localize(String text) {
        if (text == null || !text.contains("{slot:")) return text;
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (true) {
            int a = text.indexOf("{slot:", i);
            int b = a < 0 ? -1 : text.indexOf('}', a);
            if (a < 0 || b < 0) {
                out.append(text.substring(i));
                break;
            }
            out.append(text, i, a);
            String name = "";
            try {
                FormNet.SlotData d = slot(Integer.parseInt(text.substring(a + 6, b)));
                if (d != null && !d.icon().item().isEmpty()) name = d.icon().item().getHoverName().getString();
            } catch (NumberFormatException e) {
                // 잘못된 번호는 비움
            }
            out.append(name);
            i = b + 1;
        }
        return out.toString();
    }

    @Override
    protected void init() {
        body = wrap(font, onLight(localize(form.body())), GRID_W);
        w = GRID_W + PAD_X * 2;
        int fixed = PAD_Y + HEADER + gridH() + GAP + PAD_Y;
        int fit = Math.max(1, (height - 4 - fixed) / LINE);
        bodyLines = Math.min(body.size(), fit);
        bodyScroll = Math.max(0, Math.min(bodyScroll, body.size() - bodyLines));
        h = fixed + bodyLines * LINE;
        left = (width - w) / 2;
        // 0.21.0 위치는 본문 NOMINAL_LINES 줄 기준으로 가운데 — 본문 줄 수가 바뀌어도 (결과 한 줄 · 확인 창) 같은 줄 수의 창은 칸 자리가 그대로
        // (길면 아래로 늘고, 화면을 넘으면 위로 올림). 다음 창의 같은 자리에 무엇이 있는지가 정해져 두 번 누르기를 막을 수 있음
        int nominal = fixed + NOMINAL_LINES * LINE;
        top = Math.max(2, Math.min((height - nominal) / 2, height - 2 - h));
    }

    /** 칸 숫자: 999 까지 그대로, 1000 ~ 9999 는 1k · 1.2k, 그 위는 12k (칸 폭 20 에 들어가게 — 애드온은 세 자리까지) */
    static String shortNum(int n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 10000) {
            int t = n / 100;
            return (t % 10 == 0 ? String.valueOf(t / 10) : t / 10 + "." + t % 10) + "k";
        }
        if (n < 1_000_000) return n / 1000 + "k";
        return n / 1_000_000 + "m";
    }

    private int slotX(int i) {
        return left + PAD_X + (i % 9) * CELL;
    }

    private int slotY(int i) {
        return top + PAD_Y + HEADER + (i / 9) * CELL;
    }

    private int bodyTop() {
        return top + PAD_Y + HEADER + gridH() + GAP;
    }

    private FormNet.SlotData slot(int i) {
        return i >= 0 && i < form.slots().size() ? form.slots().get(i) : null;
    }

    /** 마우스 아래 칸 (빈 칸은 -1 — 보이지 않음, 진열 칸은 3 × 3 칸 넓이) */
    private int slotAt(double mx, double my) {
        if (big >= 0 && in(mx, my, slotX(big) - CELL, slotY(big) - CELL, CELL * 3, CELL * 3)) return big;
        for (int i = 0; i < cells(); i++) {
            if (covered(i)) continue;
            FormNet.SlotData s = slot(i);
            if (s == null || s.empty()) continue;
            if (in(mx, my, slotX(i), slotY(i), CELL, CELL)) return i;
        }
        return -1;
    }

    /** 누를 수 있는 칸인지 (장식 · 진열 칸은 아님) */
    private static boolean pressable(FormNet.SlotData s) {
        return s != null && !s.empty() && s.kind() != Slot.DECO && s.kind() != Slot.BIG;
    }

    /**
     * 진열 칸: 3 × 3 칸 자리에 바닐라 칸 테두리 (slot 그림과 같은 색 — 위 · 왼쪽 짙게, 아래 · 오른쪽 흰빛) · 위에서 내려오는 옅은 빛 ·
     * 그림 3배 (48) · 개수 (2배 글자 — 오른쪽 아래)
     */
    private void drawBig(GuiGraphicsExtractor g, FormNet.SlotData s) {
        int n = CELL * 3;
        int x = slotX(big) - CELL;
        int y = slotY(big) - CELL;
        g.fill(x, y, x + n, y + n, 0xFF8B8B8B);
        g.fillGradient(x + 1, y + 1, x + n - 1, y + n / 2, 0x30FFFFFF, 0x00FFFFFF);
        g.fill(x, y, x + n, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + n, 0xFF373737);
        g.fill(x + 1, y + n - 1, x + n, y + n, 0xFFFFFFFF);
        g.fill(x + n - 1, y + 1, x + n, y + n, 0xFFFFFFFF);
        if (s == null || s.empty()) return;
        icon(g, s.icon(), x + (n - BIG_ICON) / 2, y + (n - BIG_ICON) / 2, BIG_ICON);
        int count = s.icon().item().isEmpty() ? 1 : s.icon().item().getCount();
        if (count > 1) {
            String t = String.valueOf(count);
            g.pose().pushMatrix();
            g.pose().translate(x + n - 3, y + n - 3);
            g.pose().scale(2F, 2F);
            g.text(font, t, -font.width(t), -8, 0xFFFFFFFF, true);
            g.pose().popMatrix();
        }
    }

    private int closeX() {
        return left + w - 15;
    }

    private int closeY() {
        return top + 4;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        super.extractRenderState(g, mx, my, partialTick);
        sprite(g, "chest_bg", left, top, w, h);
        int x0 = left + PAD_X;
        int y0 = top + PAD_Y;
        g.text(font, clip(font, title, GRID_W - 22), x0 + 1, y0 + 1, TEXT, false);
        closeButton(g, closeX(), closeY(), in(mx, my, closeX(), closeY(), 11, 11));
        int hover = responded ? -1 : slotAt(mx, my);
        for (int i = 0; i < cells(); i++) {
            if (covered(i)) continue;
            if (i == big) {
                drawBig(g, slot(i));
                continue;
            }
            int sx = slotX(i);
            int sy = slotY(i);
            sprite(g, "slot", sx, sy, CELL, CELL);
            FormNet.SlotData s = slot(i);
            if (s == null || s.empty()) continue;
            icon(g, s.icon(), sx + 2, sy + 2, 16);
            // 흐린 장식 칸 (Slot.dim — 장식 칸의 숫자 1): 칸 안쪽 색을 반쯤 덮어 그림을 흐리게 (누를 수 없는 아이템)
            if (s.kind() == Slot.DECO && s.num() == 1) g.fill(sx + 1, sy + 1, sx + CELL - 1, sy + CELL - 1, 0xA88B8B8B);
            if (i == hover && pressable(s)) sprite(g, "slot_hover", sx, sy, CELL, CELL);
            if (s.kind() == Slot.COUNT || s.kind() == Slot.PRICE) {
                // 바닐라 아이템 수와 같은 자리 (그림 오른쪽 아래 — 칸 테두리 안), 네 자리부터 줄임 (옆 칸을 덮지 않게 — 온전한 수는 이름 · 설명에)
                String n = shortNum(s.num());
                int color = s.kind() == Slot.PRICE ? 0xFF55FF55 : 0xFFFFFFFF;
                g.text(font, n, sx + CELL - 1 - font.width(n), sy + 11, color, true);
            }
        }
        // 본문 (넘치면 휠로 — 오른쪽에 스크롤 막대)
        int by = bodyTop();
        boolean scrolls = body.size() > bodyLines;
        if (scrolls) g.enableScissor(x0, by, x0 + GRID_W, by + bodyLines * LINE);
        for (int i = bodyScroll; i < Math.min(body.size(), bodyScroll + bodyLines); i++) {
            g.text(font, body.get(i), x0, by + (i - bodyScroll) * LINE, TEXT, false);
        }
        if (scrolls) {
            g.disableScissor();
            int view = bodyLines * LINE;
            int max = (body.size() - bodyLines) * LINE;
            int x = left + w - 5;
            g.fill(x, by, x + 2, by + view, 0x40000000);
            int barH = Math.max(6, view * view / (view + max));
            int barY = by + (view - barH) * bodyScroll * LINE / max;
            g.fill(x, barY, x + 2, barY + barH, 0xFF555555);
        }
        if (hover >= 0) {
            FormNet.SlotData s = slot(hover);
            if (s != null && !s.name().isEmpty()) {
                List<Component> tip = new ArrayList<>();
                ItemStack icon = s.icon().item();
                tip.add(withItemName(s.name(), icon));
                for (String l : s.lore()) {
                    if (l.equals(TOOLTIP) && !icon.isEmpty()) tip.addAll(itemTooltip(icon));
                    else tip.add(Component.literal(l));
                }
                // 아이템 설명이 없으면 그 앞의 빈 줄이 끝에 남음 — 끝의 빈 줄은 뺌
                while (tip.size() > 1 && tip.get(tip.size() - 1).getString().isBlank()) tip.remove(tip.size() - 1);
                g.setComponentTooltipForNextFrame(font, tip, mx, my);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            if (in(event.x(), event.y(), closeX(), closeY(), 11, 11)) {
                onClose();
                return true;
            }
            if (responded) return true;
            int i = slotAt(event.x(), event.y());
            if (i >= 0) {
                if (pressable(slot(i))) respond(i);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (body.size() > bodyLines) {
            bodyScroll = (int) Math.max(0, Math.min(body.size() - bodyLines, bodyScroll - Math.signum(sy) * Math.max(1, Math.round(Math.abs(sy)))));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    /** 시험용: 칸 i 의 가운데 (화면 좌표) */
    public int[] slotCenter(int i) {
        return new int[] {slotX(i) + CELL / 2, slotY(i) + CELL / 2};
    }

    public FormNet.ChestPayload form() {
        return form;
    }

    /** 시험용: 닫기 단추 가운데 */
    public int[] closeCenter() {
        return new int[] {closeX() + 5, closeY() + 5};
    }

    /** 시험용: 창 자리 [왼쪽, 위, 폭, 높이] */
    public int[] bounds() {
        return new int[] {left, top, w, h};
    }

    /** 시험용: 줄 수 · 진열 칸 번호 (없으면 -1) */
    public int[] layout() {
        return new int[] {rows, big};
    }
}
