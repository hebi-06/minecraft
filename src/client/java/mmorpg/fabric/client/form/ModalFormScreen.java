package mmorpg.fabric.client.form;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import mmorpg.fabric.ui.FormNet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * 입력창 (forms.js ModalBuilder — NPC · 퀘스트 설정): 금테 창에 제목 줄 · 설명 · 구분선과 입력 칸 (글자 · 고르기 · 밀대 · 켜고 끄기), 맨 아래 확인 단추.
 * 길면 휠로 스크롤. 확인하면 입력 칸의 값을 순서대로 보냄.
 */
public final class ModalFormScreen extends FormScreen {
    static final int W = 300;

    /** 정수 밀대 (min ~ max, 1 씩) */
    static final class IntSlider extends AbstractSliderButton {
        private final String label;
        private final int min;
        private final int max;

        IntSlider(int x, int y, int w, int h, String label, int min, int max, int v) {
            super(x, y, w, h, Component.empty(), max <= min ? 0 : (Math.max(min, Math.min(max, v)) - min) / (double) (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            updateMessage();
        }

        int intValue() {
            return max <= min ? min : min + (int) Math.round(value * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ": §e" + intValue()));
        }

        @Override
        protected void applyValue() {}
    }

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

    public ModalFormScreen(FormNet.ModalPayload form) {
        super(form.id(), Component.literal(form.title()));
        this.form = form;
    }

    @Override
    protected void init() {
        // 다시 만들 때 (창 크기 바뀜) 적어 둔 값을 이어 받음
        List<String> keep = values.isEmpty() ? null : current();
        rows.clear();
        values.clear();
        w = Math.min(W, width - 8);
        int inner = w - 24;
        int x = 0;
        int y = 0;
        int input = 0;
        for (FormNet.ControlData c : form.controls()) {
            switch (c.type()) {
                case FormNet.ControlData.HEADER -> {
                    List<FormattedCharSequence> t = wrap(font, "§l" + c.label(), inner);
                    rows.add(new Row(y + 2, t.size() * 10 + 2, t, 0xFFFFD659, false, null));
                    y += t.size() * 10 + 6;
                }
                case FormNet.ControlData.LABEL -> {
                    List<FormattedCharSequence> t = wrap(font, c.label(), inner);
                    rows.add(new Row(y, t.size() * 10, t, 0xFFDDDDDD, false, null));
                    y += t.size() * 10 + 4;
                }
                case FormNet.ControlData.DIVIDER -> {
                    rows.add(new Row(y + 3, 1, List.of(), 0x80E0C070, true, null));
                    y += 8;
                }
                case FormNet.ControlData.TEXT -> {
                    List<FormattedCharSequence> t = wrap(font, c.label(), inner);
                    rows.add(new Row(y, t.size() * 10, t, 0xFFFFFFFF, false, null));
                    y += t.size() * 10 + 1;
                    EditBox box = new EditBox(font, x, y, inner, 18, Component.literal(c.label()));
                    box.setMaxLength(600);
                    String v = keep != null && input < keep.size() ? keep.get(input) : c.value();
                    box.setValue(v);
                    if (!c.placeholder().isEmpty()) box.setHint(Component.literal("§7" + c.placeholder()));
                    rows.add(new Row(y, 18, List.of(), 0, false, box));
                    values.add(box::getValue);
                    y += 22;
                    input++;
                }
                case FormNet.ControlData.DROPDOWN -> {
                    List<FormattedCharSequence> t = wrap(font, c.label(), inner);
                    rows.add(new Row(y, t.size() * 10, t, 0xFFFFFFFF, false, null));
                    y += t.size() * 10 + 1;
                    List<Integer> idx = new ArrayList<>();
                    for (int i = 0; i < c.options().size(); i++) idx.add(i);
                    int start = parse(keep != null && input < keep.size() ? keep.get(input) : c.value(), 0);
                    start = Math.max(0, Math.min(idx.size() - 1, start));
                    List<String> opts = c.options();
                    CycleButton<Integer> btn = CycleButton.builder((Integer i) -> Component.literal(clip(font, opts.get(i), inner - 12)), start)
                            .withValues(idx).displayOnlyValue().create(x, y, inner, 20, Component.literal(c.label()));
                    rows.add(new Row(y, 20, List.of(), 0, false, btn));
                    values.add(() -> String.valueOf(btn.getValue()));
                    y += 24;
                    input++;
                }
                case FormNet.ControlData.SLIDER -> {
                    int v = parse(keep != null && input < keep.size() ? keep.get(input) : c.value(), c.min());
                    IntSlider s = new IntSlider(x, y, inner, 20, c.label(), c.min(), c.max(), v);
                    rows.add(new Row(y, 20, List.of(), 0, false, s));
                    values.add(() -> String.valueOf(s.intValue()));
                    y += 24;
                    input++;
                }
                case FormNet.ControlData.TOGGLE -> {
                    boolean v = Boolean.parseBoolean(keep != null && input < keep.size() ? keep.get(input) : c.value());
                    CycleButton<Boolean> t = CycleButton.onOffBuilder(v).create(x, y, inner, 20, Component.literal(c.label()));
                    rows.add(new Row(y, 20, List.of(), 0, false, t));
                    values.add(() -> String.valueOf(t.getValue()));
                    y += 24;
                    input++;
                }
                default -> {}
            }
        }
        Button submit = Button.builder(Component.literal(form.submit()), b -> respond(0, current())).bounds(x, y + 2, inner, 20).build();
        rows.add(new Row(y + 2, 20, List.of(), 0, false, submit));
        y += 26;
        int contentH = y;
        int head = 24;
        h = Math.min(head + contentH + 8, height - 8);
        areaH = Math.max(24, h - head - 8);
        maxScroll = Math.max(0, contentH - areaH);
        scroll = Math.min(scroll, maxScroll);
        left = (width - w) / 2;
        top = (height - h) / 2;
        areaTop = top + head;
        for (Row r : rows) if (r.widget() != null) addRenderableWidget(r.widget());
        place();
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

    /** 스크롤에 맞춰 위젯 자리 (창 밖으로 나간 위젯은 숨김 — 눌리지 않게) */
    private void place() {
        for (Row r : rows) {
            AbstractWidget wd = r.widget();
            if (wd == null) continue;
            int y = areaTop + r.y() - scroll;
            wd.setX(left + 12);
            wd.setY(y);
            wd.visible = y >= areaTop && y + r.h() <= areaTop + areaH;
        }
    }

    private int closeX() {
        return left + w - 16;
    }

    private int closeY() {
        return top + 6;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        sprite(g, "dialog_bg", left, top, w, h);
        g.text(font, clip(font, form.title(), w - 40), left + 12, top + 9, 0xFFFFD659, true);
        closeButton(g, closeX(), closeY(), in(mx, my, closeX(), closeY(), 11, 11));
        g.enableScissor(left + 4, areaTop, left + w - 4, areaTop + areaH);
        for (Row r : rows) {
            if (r.widget() != null) continue;
            int y = areaTop + r.y() - scroll;
            if (r.divider()) {
                g.fill(left + 12, y, left + w - 12, y + 1, r.color());
                continue;
            }
            for (FormattedCharSequence line : r.text()) {
                g.text(font, line, left + 12, y, r.color(), true);
                y += 10;
            }
        }
        super.extractRenderState(g, mx, my, partialTick);
        g.disableScissor();
        if (maxScroll > 0) {
            int barH = Math.max(8, areaH * areaH / (areaH + maxScroll));
            int barY = areaTop + (areaH - barH) * scroll / maxScroll;
            g.fill(left + w - 8, barY, left + w - 6, barY + barH, 0xA0E0C070);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (in(event.x(), event.y(), closeX(), closeY(), 11, 11)) {
            onClose();
            return true;
        }
        if (responded) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (maxScroll > 0) {
            scroll = (int) Math.max(0, Math.min(maxScroll, scroll - sy * 20));
            place();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    /** 시험용: 지금 값 (입력 칸 순서) */
    public List<String> values() {
        return current();
    }

    /** 시험용: i 번째 입력 칸 위젯 (글자 · 고르기 · 밀대 · 켜고 끄기 순서, 없으면 null) */
    public AbstractWidget input(int i) {
        int n = 0;
        for (Row r : rows) {
            if (r.widget() == null || r.widget() instanceof Button) continue;
            if (n++ == i) return r.widget();
        }
        return null;
    }

    /** 시험용: 확인 단추 */
    public AbstractWidget submitButton() {
        for (Row r : rows) if (r.widget() instanceof Button b) return b;
        return null;
    }

    /** 시험용: 닫기 단추 가운데 */
    public int[] closeCenter() {
        return new int[] {closeX() + 5, closeY() + 5};
    }

    /** 시험용: 확인 */
    public void submit() {
        respond(0, current());
    }
}
