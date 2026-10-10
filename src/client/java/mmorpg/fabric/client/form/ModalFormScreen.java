package mmorpg.fabric.client.form;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import mmorpg.fabric.ui.FormNet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

/**
 * 입력창 (forms.js ModalBuilder — NPC · 퀘스트 설정): 금테 창에 제목 줄 (금색 굵게) · 설명 · 구분선과 입력 칸 — 글자 (바닐라 글자 칸),
 * 고르기 (누르면 목록이 펼쳐짐), 밀대, 켜고 끄기 (스위치) — 맨 아래 확인 단추. 입력 칸은 대화창 단추 그림으로 그림 (FormWidgets).
 * 화면보다 길면 휠로 넘김 (창 밖으로 반쯤 나간 칸은 잘려 보이고 누르지 않음). 확인하면 입력 칸의 값을 순서대로 보냄.
 */
public final class ModalFormScreen extends FormScreen {
    static final int W = 300;
    static final int MARGIN = 8;
    static final int HEAD = 24;
    static final int POPUP_ROW = 14;
    static final int POPUP_MAX = 8;

    /** 그려 둘 줄 (글자 · 구분선) 또는 위젯, 스크롤 전 자리 */
    private record Row(int y, int h, List<FormattedCharSequence> text, int color, boolean divider, AbstractWidget widget) {}

    private final FormNet.ModalPayload form;
    private final List<Row> rows = new ArrayList<>();
    private final List<Supplier<String>> values = new ArrayList<>();
    private int left;
    private int top;
    private int w;
    private int h;
    private int areaTop;
    private int areaH;
    private int scroll;
    private int maxScroll;
    /** 펼친 고르기 목록 (없으면 null) · 목록의 스크롤 (줄) */
    private FormWidgets.Choice open;
    private int popupScroll;

    public ModalFormScreen(FormNet.ModalPayload form) {
        super(form.id(), Component.literal(form.title()));
        this.form = form;
    }

    @Override
    protected void init() {
        // 다시 만들 때 (창 크기 바뀜) 적어 둔 값을 이어 받음
        List<String> keep = values.isEmpty() ? null : current();
        open = null;
        rows.clear();
        values.clear();
        w = Math.min(W, width - 8);
        int inner = w - 24;
        int y = 0;
        int input = 0;
        boolean first = true;
        for (FormNet.ControlData c : form.controls()) {
            String v = keep != null && input < keep.size() ? keep.get(input) : c.value();
            switch (c.type()) {
                case FormNet.ControlData.HEADER -> {
                    if (!first) y += 3;
                    List<FormattedCharSequence> t = wrap(font, "§l" + c.label(), inner);
                    rows.add(new Row(y, t.size() * 10, t, 0xFFFFD659, false, null));
                    y += t.size() * 10 + 4;
                }
                case FormNet.ControlData.LABEL -> {
                    List<FormattedCharSequence> t = wrap(font, c.label(), inner);
                    rows.add(new Row(y, t.size() * 10, t, 0xFFD8D8E0, false, null));
                    y += t.size() * 10 + 4;
                }
                case FormNet.ControlData.DIVIDER -> {
                    rows.add(new Row(y + 2, 1, List.of(), 0x90E0C070, true, null));
                    y += 6;
                }
                case FormNet.ControlData.TEXT -> {
                    y = label(c.label(), y, inner);
                    EditBox box = new EditBox(font, 0, y, inner, 18, Component.literal(c.label()));
                    box.setMaxLength(600);
                    box.setValue(v);
                    if (!c.placeholder().isEmpty()) box.setHint(Component.literal("§7" + c.placeholder()));
                    rows.add(new Row(y, 18, List.of(), 0, false, box));
                    values.add(box::getValue);
                    y += 18 + 6;
                    input++;
                }
                case FormNet.ControlData.DROPDOWN -> {
                    y = label(c.label(), y, inner);
                    List<String> opts = c.options().isEmpty() ? List.of("-") : c.options();
                    FormWidgets.Choice ch = new FormWidgets.Choice(0, y, inner, 20, Component.literal(c.label()), opts, parse(v, 0), this::openList);
                    rows.add(new Row(y, 20, List.of(), 0, false, ch));
                    values.add(() -> String.valueOf(ch.index));
                    y += 20 + 6;
                    input++;
                }
                case FormNet.ControlData.SLIDER -> {
                    FormWidgets.Slider s = new FormWidgets.Slider(0, y, inner, 20, c.label(), c.min(), c.max(), parse(v, c.min()));
                    rows.add(new Row(y, 20, List.of(), 0, false, s));
                    values.add(() -> String.valueOf(s.intValue()));
                    y += 20 + 6;
                    input++;
                }
                case FormNet.ControlData.TOGGLE -> {
                    FormWidgets.Toggle t = new FormWidgets.Toggle(0, y, inner, 20, Component.literal(c.label()), Boolean.parseBoolean(v));
                    rows.add(new Row(y, 20, List.of(), 0, false, t));
                    values.add(() -> String.valueOf(t.value));
                    y += 20 + 4;
                    input++;
                }
                default -> {}
            }
            first = false;
        }
        FormWidgets.Action submit = new FormWidgets.Action(0, y + 2, inner, 20, Component.literal(form.submit()), () -> {
            if (!responded) respond(0, current());
        });
        rows.add(new Row(y + 2, 20, List.of(), 0, false, submit));
        y += 22 + 2;
        int contentH = y;
        h = Math.min(HEAD + contentH + 8, height - MARGIN * 2);
        areaH = Math.max(24, h - HEAD - 8);
        maxScroll = Math.max(0, contentH - areaH);
        scroll = Math.min(scroll, maxScroll);
        left = (width - w) / 2;
        top = (height - h) / 2;
        areaTop = top + HEAD;
        for (Row r : rows) if (r.widget() != null) addRenderableWidget(r.widget());
        place();
    }

    /** 입력 칸 위의 이름 줄 (흰 글자) — 다음 자리 */
    private int label(String text, int y, int inner) {
        if (text == null || text.isEmpty()) return y;
        List<FormattedCharSequence> t = wrap(font, text, inner);
        rows.add(new Row(y, t.size() * 10, t, 0xFFFFFFFF, false, null));
        return y + t.size() * 10 + 2;
    }

    private static int parse(String s, int def) {
        try {
            return (int) Math.round(Double.parseDouble(s.trim()));
        } catch (RuntimeException e) {
            return def;
        }
    }

    private List<String> current() {
        List<String> out = new ArrayList<>();
        for (Supplier<String> v : values) out.add(v.get());
        return out;
    }

    /** 스크롤에 맞춰 위젯 자리 (창 밖으로 다 나간 위젯은 숨김 — 반쯤 걸친 위젯은 잘려 보임) */
    private void place() {
        for (Row r : rows) {
            AbstractWidget wd = r.widget();
            if (wd == null) continue;
            int y = areaTop + r.y() - scroll;
            wd.setX(left + 12);
            wd.setY(y);
            wd.visible = y + r.h() > areaTop && y < areaTop + areaH;
        }
    }

    private int closeX() {
        return left + w - 17;
    }

    private int closeY() {
        return top + 6;
    }

    // ------------------------------------------------------------ 고르기 목록

    private void openList(FormWidgets.Choice c) {
        if (responded) return;
        open = c;
        int vis = popupRows();
        popupScroll = Math.max(0, Math.min(c.options.size() - vis, c.index - vis / 2));
    }

    private int popupRows() {
        if (open == null) return 0;
        int fit = Math.max(3, (height - 8) / POPUP_ROW - 1);
        return Math.min(open.options.size(), Math.min(POPUP_MAX, fit));
    }

    /** 펼친 목록 자리 [x, y, 폭, 높이] — 칸 아래 (모자라면 위) */
    private int[] popupRect() {
        int ph = popupRows() * POPUP_ROW + 4;
        int px = open.getX();
        int pw = open.getWidth();
        int py = open.getY() + open.getHeight() + 1;
        if (py + ph > height - 4) py = open.getY() - ph - 1;
        py = Math.max(4, Math.min(py, height - 4 - ph));
        return new int[] {px, py, pw, ph};
    }

    private int popupAt(double mx, double my) {
        if (open == null) return -1;
        int[] r = popupRect();
        if (!in(mx, my, r[0] + 2, r[1] + 2, r[2] - 4, r[3] - 4)) return -1;
        int i = popupScroll + (int) ((my - r[1] - 2) / POPUP_ROW);
        return i >= 0 && i < open.options.size() ? i : -1;
    }

    private void drawPopup(GuiGraphicsExtractor g, int mx, int my) {
        int[] r = popupRect();
        int x = r[0];
        int y = r[1];
        g.fill(x, y, x + r[2], y + r[3], 0xFF0A0A10);
        g.fill(x + 1, y + 1, x + r[2] - 1, y + r[3] - 1, 0xFFC9A24A);
        g.fill(x + 2, y + 2, x + r[2] - 2, y + r[3] - 2, 0xFF161827);
        int vis = popupRows();
        int hover = popupAt(mx, my);
        for (int k = 0; k < vis; k++) {
            int i = popupScroll + k;
            if (i >= open.options.size()) break;
            int ry = y + 2 + k * POPUP_ROW;
            if (i == open.index) g.fill(x + 2, ry, x + r[2] - 2, ry + POPUP_ROW, 0x70A07828);
            if (i == hover) g.fill(x + 2, ry, x + r[2] - 2, ry + POPUP_ROW, 0x30FFFFFF);
            g.text(font, clip(font, open.options.get(i), r[2] - 18), x + 7, ry + 3, i == open.index ? 0xFFFFE08A : 0xFFFFFFFF, true);
        }
        scrollbar(g, x + r[2] - 5, y + 2, vis * POPUP_ROW, popupScroll * POPUP_ROW, (open.options.size() - vis) * POPUP_ROW);
    }

    // ------------------------------------------------------------ 그리기 · 입력

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        panel(g, left, top, w, h);
        g.text(font, clip(font, form.title(), w - 44), left + 12, top + 9, 0xFFFFD659, true);
        boolean blocked = open != null;
        closeButton(g, closeX(), closeY(), !blocked && in(mx, my, closeX(), closeY(), 11, 11));
        g.enableScissor(left + 4, areaTop, left + w - 4, areaTop + areaH);
        for (Row r : rows) {
            if (r.widget() != null) continue;
            int y = areaTop + r.y() - scroll;
            if (y + r.h() < areaTop || y > areaTop + areaH) continue;
            if (r.divider()) {
                g.fill(left + 12, y, left + w - 12, y + 1, r.color());
                continue;
            }
            for (FormattedCharSequence line : r.text()) {
                g.text(font, line, left + 12, y, r.color(), true);
                y += 10;
            }
        }
        // 목록이 펼쳐진 동안 뒤의 위젯은 마우스에 반응하지 않게 · 창 밖 (잘린 곳) 에서도
        boolean inArea = my >= areaTop && my < areaTop + areaH;
        int wx = blocked || !inArea ? -1 : mx;
        int wy = blocked || !inArea ? -1 : my;
        super.extractRenderState(g, wx, wy, partialTick);
        g.disableScissor();
        scrollbar(g, left + w - 9, areaTop, areaH, scroll, maxScroll);
        if (open != null) {
            g.nextStratum();
            drawPopup(g, mx, my);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (open != null) {
            int i = popupAt(event.x(), event.y());
            if (i >= 0) {
                open.index = i;
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            open = null;
            return true;
        }
        if (in(event.x(), event.y(), closeX(), closeY(), 11, 11)) {
            onClose();
            return true;
        }
        if (responded) return true;
        // 스크롤 영역 밖으로 잘린 위젯은 누르지 않음
        if (event.y() < areaTop || event.y() >= areaTop + areaH) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (open != null) {
            int max = open.options.size() - popupRows();
            if (max > 0) popupScroll = (int) Math.max(0, Math.min(max, popupScroll - Math.signum(sy)));
            return true;
        }
        if (maxScroll > 0) {
            scroll = (int) Math.max(0, Math.min(maxScroll, scroll - sy * 20));
            place();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (open != null) {
            // 펼친 목록: ↑ ↓ 로 고르고 Enter · ESC 로 접음
            if (event.isUp() || event.isDown()) {
                open.index = Math.max(0, Math.min(open.options.size() - 1, open.index + (event.isDown() ? 1 : -1)));
                int vis = popupRows();
                popupScroll = Math.max(0, Math.min(open.options.size() - vis, Math.max(Math.min(popupScroll, open.index), open.index - vis + 1)));
                return true;
            }
            if (event.isEscape() || event.isSelection() || event.isConfirmation()) {
                open = null;
                return true;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    /** 시험용: 지금 값 (입력 칸 순서) */
    public List<String> values() {
        return current();
    }

    /** 시험용: 확인 */
    public void submit() {
        respond(0, current());
    }

    /** 시험용: i 번째 입력 칸 위젯 (글자 · 고르기 · 밀대 · 켜고 끄기 순서, 없으면 null) */
    public AbstractWidget input(int i) {
        int n = 0;
        for (Row r : rows) {
            if (r.widget() == null || r.widget() instanceof FormWidgets.Action) continue;
            if (n++ == i) return r.widget();
        }
        return null;
    }

    /** 시험용: 확인 단추 */
    public AbstractWidget submitButton() {
        for (Row r : rows) if (r.widget() instanceof FormWidgets.Action b) return b;
        return null;
    }

    /** 시험용: 펼친 고르기 목록의 i 번째 줄 가운데 (펼치지 않았거나 안 보이면 null) */
    public int[] listItemCenter(int i) {
        if (open == null) return null;
        int vis = popupRows();
        if (i < popupScroll || i >= popupScroll + vis) return null;
        int[] r = popupRect();
        return new int[] {r[0] + r[2] / 2, r[1] + 2 + (i - popupScroll) * POPUP_ROW + POPUP_ROW / 2};
    }

    /** 시험용: 고르기 목록이 펼쳐져 있는지 */
    public boolean listOpen() {
        return open != null;
    }

    /** 시험용: 스크롤 영역 [위, 높이] (화면 좌표) */
    public int[] area() {
        return new int[] {areaTop, areaH};
    }

    /** 시험용: 닫기 단추 가운데 */
    public int[] closeCenter() {
        return new int[] {closeX() + 5, closeY() + 5};
    }
}
