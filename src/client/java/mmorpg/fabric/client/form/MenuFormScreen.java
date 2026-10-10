package mmorpg.fabric.client.form;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import mmorpg.fabric.ui.FormNet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * 버튼 목록 창 (관리자 메뉴 — forms.js MenuForm): 가운데 금테 창에 제목 (금색) · 설명 (흰 글자) · 버튼 줄 (대화창 선택지와 같은 모양 — 그림 + 글자).
 * 화면보다 길면 버튼 줄만 휠로 넘김 (오른쪽 스크롤 막대). 위아래 8칸은 비워 둠.
 */
public final class MenuFormScreen extends FormScreen {
    static final int W = 280;
    static final int ROW = 22;
    static final int BTN_H = 20;
    static final int MARGIN = 8;

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
        int listFull = Math.max(BTN_H, form.buttons().size() * ROW - (ROW - BTN_H));
        // 제목 (위 9칸 + 줄 높이 10) · 설명 · 버튼 줄 · 아래 여백 10
        int head = 9 + 10 + 5 + (textH > 0 ? textH + 5 : 0);
        h = Math.min(head + listFull + 10, height - MARGIN * 2);
        listH = Math.max(BTN_H, h - head - 10);
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
        return left + w - 17;
    }

    private int closeY() {
        return top + 6;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        super.extractRenderState(g, mx, my, partialTick);
        sprite(g, "dialog_bg", left, top, w, h);
        g.text(font, clip(font, form.title(), w - 44), left + 12, top + 9, 0xFFFFD659, true);
        closeButton(g, closeX(), closeY(), in(mx, my, closeX(), closeY(), 11, 11));
        int y = top + 24;
        for (FormattedCharSequence line : text) {
            g.text(font, line, left + 12, y, 0xFFFFFFFF, true);
            y += 10;
        }
        int hover = responded ? -1 : buttonAt(mx, my);
        g.enableScissor(left + 4, listTop, left + w - 4, listTop + listH);
        List<FormNet.ChoiceData> list = form.buttons();
        int bx = left + 12;
        int bw = w - 24 - (maxScroll > 0 ? 4 : 0);
        for (int i = 0; i < list.size(); i++) {
            int by = listTop + i * ROW - scroll;
            if (by + BTN_H < listTop || by > listTop + listH) continue;
            FormNet.ChoiceData c = list.get(i);
            sprite(g, i == hover ? "dialog_btn_hover" : "dialog_btn", bx, by, bw, BTN_H);
            boolean icon = !c.icon().isEmpty();
            if (icon) icon(g, c.icon(), bx + 3, by + 2, 16);
            int tx = icon ? bx + 23 : bx + 7;
            g.text(font, clip(font, c.text(), bw - (tx - bx) - 5), tx, by + 6, 0xFFFFFFFF, true);
        }
        g.disableScissor();
        scrollbar(g, left + w - 9, listTop, listH, scroll, maxScroll);
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
