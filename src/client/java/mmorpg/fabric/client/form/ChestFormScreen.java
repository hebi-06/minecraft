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

/**
 * 상자 모양 창 (애드온 server_form.json rpg_chest): 회색 상자 테두리 (chest_bg) 안에 제목 · 9 × 3 칸 (slot 20 × 20, 그림 16 × 16) · 본문.
 * 칸 오른쪽 아래 숫자 — 흰색 = 보유 수, 초록 = 가격 (바닐라 아이템 수 자리). 마우스를 올리면 밝아지고 이름 · 설명. 장식 칸은 누를 수 없음.
 * 제목 · 본문은 짙은 회색 (#404040, 그림자 없음 — 애드온 색), 덧붙임 §7 은 바탕에 묻히지 않게 §8 로. 본문이 화면보다 길면 휠로 넘김.
 */
public final class ChestFormScreen extends FormScreen {
    static final int GRID_W = 180;
    static final int CELL = 20;
    static final int PAD_X = 7;
    static final int PAD_Y = 6;
    static final int HEADER = 13;
    static final int GRID_H = 60;
    static final int GAP = 4;
    static final int LINE = 10;
    static final int TEXT = 0xFF404040;

    private final FormNet.ChestPayload form;
    private final String title;
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
    }

    @Override
    protected void init() {
        body = wrap(font, onLight(form.body()), GRID_W);
        w = GRID_W + PAD_X * 2;
        int fixed = PAD_Y + HEADER + GRID_H + GAP + PAD_Y;
        int fit = Math.max(1, (height - 4 - fixed) / LINE);
        bodyLines = Math.min(body.size(), fit);
        bodyScroll = Math.max(0, Math.min(bodyScroll, body.size() - bodyLines));
        h = fixed + bodyLines * LINE;
        left = (width - w) / 2;
        top = Math.max(2, (height - h) / 2);
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
        return top + PAD_Y + HEADER + GRID_H + GAP;
    }

    private FormNet.SlotData slot(int i) {
        return i >= 0 && i < form.slots().size() ? form.slots().get(i) : null;
    }

    /** 마우스 아래 칸 (빈 칸은 -1 — 보이지 않음) */
    private int slotAt(double mx, double my) {
        for (int i = 0; i < Math.min(27, form.slots().size()); i++) {
            FormNet.SlotData s = slot(i);
            if (s == null || s.empty()) continue;
            if (in(mx, my, slotX(i), slotY(i), CELL, CELL)) return i;
        }
        return -1;
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
        for (int i = 0; i < Math.min(27, form.slots().size()); i++) {
            int sx = slotX(i);
            int sy = slotY(i);
            sprite(g, "slot", sx, sy, CELL, CELL);
            FormNet.SlotData s = slot(i);
            if (s == null || s.empty()) continue;
            icon(g, s.icon(), sx + 2, sy + 2, 16);
            if (i == hover && s.kind() != Slot.DECO) sprite(g, "slot_hover", sx, sy, CELL, CELL);
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
                tip.add(Component.literal(s.name()));
                for (String l : s.lore()) tip.add(Component.literal(l));
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
                FormNet.SlotData s = slot(i);
                if (s != null && s.kind() != Slot.DECO) respond(i);
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
}
