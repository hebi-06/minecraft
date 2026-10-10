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
 * 칸 오른쪽 아래 숫자 — 흰색 = 보유 수, 초록 = 가격. 마우스를 올리면 밝아지고 이름 · 설명. 장식 칸은 누를 수 없음.
 */
public final class ChestFormScreen extends FormScreen {
    static final int GRID_W = 180;
    static final int CELL = 20;
    static final int PAD_X = 7;
    static final int PAD_Y = 6;
    static final int HEADER = 13;
    static final int TEXT = 0xFF404040;

    private final FormNet.ChestPayload form;
    private List<FormattedCharSequence> body = List.of();
    private int left;
    private int top;
    private int w;
    private int h;

    public ChestFormScreen(FormNet.ChestPayload form) {
        super(form.id(), Component.literal(form.title()));
        this.form = form;
    }

    @Override
    protected void init() {
        body = wrap(font, form.body(), GRID_W);
        w = GRID_W + PAD_X * 2;
        int bodyH = body.isEmpty() ? 0 : body.size() * 10;
        h = PAD_Y + HEADER + 60 + 4 + bodyH + PAD_Y;
        left = (width - w) / 2;
        top = Math.max(2, (height - h) / 2);
    }

    private int slotX(int i) {
        return left + PAD_X + (i % 9) * CELL;
    }

    private int slotY(int i) {
        return top + PAD_Y + HEADER + (i / 9) * CELL;
    }

    /** 이 칸이 보이는지 (빈 칸은 안 보임) */
    private FormNet.SlotData slot(int i) {
        return i >= 0 && i < form.slots().size() ? form.slots().get(i) : null;
    }

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
        g.text(font, clip(font, form.title(), GRID_W - 22), x0 + 1, y0 + 1, TEXT, false);
        closeButton(g, closeX(), closeY(), in(mx, my, closeX(), closeY(), 11, 11));
        int hover = responded ? -1 : slotAt(mx, my);
        for (int i = 0; i < Math.min(27, form.slots().size()); i++) {
            int sx = slotX(i);
            int sy = slotY(i);
            sprite(g, "slot", sx, sy, CELL, CELL);
            FormNet.SlotData s = slot(i);
            if (s == null || s.empty()) continue;
            icon(g, s.icon(), sx + 2, sy + 2, 16);
            if (s.kind() == Slot.COUNT || s.kind() == Slot.PRICE) {
                String n = String.valueOf(s.num());
                int color = s.kind() == Slot.PRICE ? 0xFF55FF55 : 0xFFFFFFFF;
                g.text(font, n, sx + CELL - font.width(n), sy + CELL - 8, color, true);
            }
            if (i == hover && s.kind() != Slot.DECO) sprite(g, "slot_hover", sx, sy, CELL, CELL);
        }
        int by = y0 + HEADER + 60 + 4;
        for (FormattedCharSequence line : body) {
            g.text(font, line, x0, by, TEXT, false);
            by += 10;
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
}
