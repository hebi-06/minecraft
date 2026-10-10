package mmorpg.fabric.client.form;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import mmorpg.fabric.ui.FormNet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * 버튼 목록 창 (관리자 메뉴 — forms.js MenuForm): 가운데 금테 창에 제목 · 설명 · 버튼 줄 (그림 + 글자). 길면 휠로 스크롤.
 */
public final class MenuFormScreen extends FormScreen {
    static final int W = 280;
    static final int ROW = 22;
    static final int BTN_H = 20;

    private final FormNet.MenuPayload form;
    private List<FormattedCharSequence> text = List.of();
    private int left;
    private int top;
    private int w;
    private int h;
    private int listTop;
    private int listH;
    private int scroll;
    private int maxScroll;

    public MenuFormScreen(FormNet.MenuPayload form) {
        super(form.id(), Component.literal(form.title()));
        this.form = form;
    }

    @Override
    protected void init() {
        w = Math.min(W, width - 8);
        text = wrap(font, form.text(), w - 24);
        int textH = text.size() * 10;
        int listFull = form.buttons().size() * ROW;
        int head = 10 + 12 + (textH > 0 ? textH + 6 : 0);
        h = Math.min(head + listFull + 10, height - 8);
        listH = Math.max(ROW, h - head - 10);
        maxScroll = Math.max(0, listFull - listH);
        scroll = Math.min(scroll, maxScroll);
        left = (width - w) / 2;
        top = (height - h) / 2;
        listTop = top + head;
    }

    private int buttonAt(double mx, double my) {
        if (mx < left + 12 || mx >= left + w - 12 || my < listTop || my >= listTop + listH) return -1;
        int i = (int) ((my - listTop + scroll) / ROW);
        double inRow = (my - listTop + scroll) - i * ROW;
        return i >= 0 && i < form.buttons().size() && inRow < BTN_H ? i : -1;
    }

    private int closeX() {
        return left + w - 16;
    }

    private int closeY() {
        return top + 6;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        super.extractRenderState(g, mx, my, partialTick);
        sprite(g, "dialog_bg", left, top, w, h);
        g.text(font, clip(font, form.title(), w - 40), left + 12, top + 10, 0xFFFFD659, true);
        closeButton(g, closeX(), closeY(), in(mx, my, closeX(), closeY(), 11, 11));
        int y = top + 22;
        for (FormattedCharSequence line : text) {
            g.text(font, line, left + 12, y, 0xFFFFFFFF, true);
            y += 10;
        }
        int hover = responded ? -1 : buttonAt(mx, my);
        g.enableScissor(left + 4, listTop, left + w - 4, listTop + listH);
        List<FormNet.ChoiceData> list = form.buttons();
        for (int i = 0; i < list.size(); i++) {
            int by = listTop + i * ROW - scroll;
            if (by + BTN_H < listTop || by > listTop + listH) continue;
            FormNet.ChoiceData c = list.get(i);
            int bx = left + 12;
            int bw = w - 24;
            sprite(g, i == hover ? "dialog_btn_hover" : "dialog_btn", bx, by, bw, BTN_H);
            boolean icon = !c.icon().isEmpty();
            if (icon) icon(g, c.icon(), bx + 3, by + 2, 16);
            int tx = icon ? bx + 23 : bx + 6;
            g.text(font, clip(font, c.text(), bw - (tx - bx) - 4), tx, by + 6, 0xFFFFFFFF, true);
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int barH = Math.max(8, listH * listH / (listH + maxScroll));
            int barY = listTop + (listH - barH) * scroll / maxScroll;
            g.fill(left + w - 8, barY, left + w - 6, barY + barH, 0xA0E0C070);
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
            int i = buttonAt(event.x(), event.y());
            if (i >= 0) {
                respond(i);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (maxScroll > 0) {
            scroll = (int) Math.max(0, Math.min(maxScroll, scroll - sy * ROW));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    /** 시험용: 버튼 i 의 가운데 (화면 좌표, 지금 스크롤 기준) */
    public int[] buttonCenter(int i) {
        return new int[] {left + w / 2, listTop + i * ROW - scroll + BTN_H / 2};
    }

    public FormNet.MenuPayload form() {
        return form;
    }

    /** 시험용: 닫기 단추 가운데 */
    public int[] closeCenter() {
        return new int[] {closeX() + 5, closeY() + 5};
    }
}
