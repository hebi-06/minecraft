package mmorpg.fabric.client.form;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import mmorpg.fabric.ui.FormNet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * NPC 대화창 (애드온 server_form.json rpg_dialog): 화면 아래 가운데 폭 300 — 금테 이름표 (dialog_name, 금색 이름) · 어두운 금테 상자 (dialog_bg) 안에
 * 대사 (흰 글자) · 선택지 줄 (dialog_btn, 높이 18 · 간격 20, 왼쪽 그림 12 × 12 · 글자). 세상은 흐리지 않고 그대로 보임 (아래쪽만 살짝 어둡게).
 * 대화창이 열린 동안 아래쪽 HUD (핫바 · 체력 · 화면 하단 글자 · 조준점) 는 숨김 (FormClient — 상자가 그 자리를 덮음).
 * 선택지가 많으면 휠로 넘김 (대사는 그대로), 대사가 너무 길면 대사도 휠로. 숫자 키 1 ~ 9 로도 고름.
 */
public final class DialogFormScreen extends FormScreen {
    static final int W = 300;
    static final int NAME_H = 16;
    static final int ROW = 20;
    static final int BTN_H = 18;
    static final int LINE = 10;

    private final FormNet.DialogPayload form;
    private List<FormattedCharSequence> text = List.of();
    private int left;
    private int boxTop;
    private int boxH;
    private int w;
    /** 보이는 대사 줄 · 넘긴 대사 줄 */
    private int textLines;
    private int textScroll;
    private int choicesTop;
    private int choicesH;
    private int scroll;
    private int maxScroll;

    public DialogFormScreen(FormNet.DialogPayload form) {
        super(form.id(), Component.literal(form.name()));
        this.form = form;
    }

    @Override
    protected void init() {
        w = Math.min(W, width - 8);
        text = wrap(font, form.text(), w - 20);
        int listH = form.choices().size() * ROW;
        int maxBox = height - 6 - NAME_H - 4;
        // 대사가 길어도 선택지 두 줄 (적으면 그만큼) 은 보이게
        int room = Math.max(LINE, maxBox - 16 - 7 - Math.min(listH, 2 * ROW));
        textLines = Math.min(text.size(), room / LINE);
        textScroll = Math.max(0, Math.min(textScroll, text.size() - textLines));
        int textH = textLines * LINE;
        boxH = Math.min(textH + 7 + listH + 16, maxBox);
        choicesH = Math.max(ROW, boxH - 16 - textH - 7);
        maxScroll = Math.max(0, listH - choicesH);
        scroll = Math.min(scroll, maxScroll);
        left = (width - w) / 2;
        boxTop = height - 6 - boxH;
        choicesTop = boxTop + 9 + textH + 7;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        // 세상을 흐리지 않음 (대화하는 NPC 가 보이게) — 아래쪽만 살짝 어둡게
        g.fillGradient(0, height / 2, width, height, 0x00000000, 0x60000000);
    }

    private int choiceAt(double mx, double my) {
        if (mx < left + 10 || mx >= left + w - 10 || my < choicesTop || my >= choicesTop + choicesH) return -1;
        int i = (int) ((my - choicesTop + scroll) / ROW);
        double inRow = (my - choicesTop + scroll) - i * ROW;
        return i >= 0 && i < form.choices().size() && inRow < BTN_H ? i : -1;
    }

    private int closeX() {
        return left + w - 13;
    }

    private int closeY() {
        return boxTop - NAME_H + 2;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        super.extractRenderState(g, mx, my, partialTick);
        // 이름표 (상자 위 왼쪽 — 아래 1칸이 상자 테두리에 겹침)
        String name = form.name();
        if (!name.isEmpty()) {
            int plateW = Math.min(w - 30, font.width(name) + 16);
            int plateX = left + 6;
            int plateY = boxTop - NAME_H + 1;
            sprite(g, "dialog_name", plateX, plateY, plateW, NAME_H);
            g.text(font, clip(font, name, plateW - 10), plateX + (plateW - Math.min(font.width(name), plateW - 10)) / 2, plateY + 5, 0xFFFFD659, true);
        }
        closeButton(g, closeX(), closeY(), in(mx, my, closeX(), closeY(), 11, 11));
        // 상자 · 대사
        sprite(g, "dialog_bg", left, boxTop, w, boxH);
        int ty = boxTop + 9;
        boolean textScrolls = text.size() > textLines;
        if (textScrolls) g.enableScissor(left + 4, ty, left + w - 4, ty + textLines * LINE);
        for (int i = textScroll; i < Math.min(text.size(), textScroll + textLines); i++) {
            g.text(font, text.get(i), left + 10, ty + (i - textScroll) * LINE, 0xFFFFFFFF, true);
        }
        if (textScrolls) {
            g.disableScissor();
            scrollbar(g, left + w - 7, ty, textLines * LINE, textScroll * LINE, (text.size() - textLines) * LINE);
        }
        // 선택지
        int hover = responded ? -1 : choiceAt(mx, my);
        g.enableScissor(left + 4, choicesTop, left + w - 4, choicesTop + choicesH);
        List<FormNet.ChoiceData> list = form.choices();
        int bx = left + 10;
        int bw = w - 20;
        for (int i = 0; i < list.size(); i++) {
            int by = choicesTop + i * ROW - scroll;
            if (by + BTN_H < choicesTop || by > choicesTop + choicesH) continue;
            FormNet.ChoiceData c = list.get(i);
            sprite(g, i == hover ? "dialog_btn_hover" : "dialog_btn", bx, by, bw, BTN_H);
            if (!c.icon().isEmpty()) icon(g, c.icon(), bx + 4, by + 3, 12);
            g.text(font, clip(font, c.text(), bw - 24), bx + 20, by + 5, 0xFFFFFFFF, true);
        }
        g.disableScissor();
        scrollbar(g, left + w - 7, choicesTop, choicesH, scroll, maxScroll);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            if (in(event.x(), event.y(), closeX(), closeY(), 11, 11)) {
                onClose();
                return true;
            }
            if (responded) return true;
            int i = choiceAt(event.x(), event.y());
            if (i >= 0) {
                respond(i);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (text.size() > textLines && my < choicesTop) {
            textScroll = (int) Math.max(0, Math.min(text.size() - textLines, textScroll - Math.signum(sy)));
            return true;
        }
        if (maxScroll > 0) {
            scroll = (int) Math.max(0, Math.min(maxScroll, scroll - sy * ROW));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int k = event.key();
        if (!responded && k >= InputConstants.KEY_1 && k <= InputConstants.KEY_9) {
            int i = k - InputConstants.KEY_1;
            if (i < form.choices().size()) {
                respond(i);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    /** 시험용: 선택지 i 의 가운데 (화면 좌표) */
    public int[] choiceCenter(int i) {
        return new int[] {left + w / 2, choicesTop + i * ROW - scroll + BTN_H / 2};
    }

    public FormNet.DialogPayload form() {
        return form;
    }

    /** 시험용: 닫기 단추 가운데 */
    public int[] closeCenter() {
        return new int[] {closeX() + 5, closeY() + 5};
    }
}
