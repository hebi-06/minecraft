package mmorpg.fabric.client.form;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 입력창 위젯 — 바닐라 회색 돌 단추 대신 애드온 대화창 단추 그림 (dialog_btn 옅은 흰 테 · 마우스를 올리면 dialog_btn_hover 금테) 으로 그림:
 * 켜고 끄기 (왼쪽 글자 · 오른쪽 스위치), 고르기 (누르면 목록이 펼쳐짐 — 베드락 드롭다운처럼), 밀대 (금색 손잡이), 확인 단추 (금테).
 */
final class FormWidgets {
    private FormWidgets() {}

    static Font font() {
        return Minecraft.getInstance().font;
    }

    /** 단추 바탕 (lit: 마우스 · 초점 — 금테) */
    static void face(GuiGraphicsExtractor g, AbstractWidget w, boolean lit) {
        FormScreen.sprite(g, lit ? "dialog_btn_hover" : "dialog_btn", w.getX(), w.getY(), w.getWidth(), w.getHeight());
    }

    /** 글자 한 줄의 세로 가운데 */
    static int textY(AbstractWidget w) {
        return w.getY() + (w.getHeight() - 8) / 2;
    }

    /** 켜고 끄기: 왼쪽 글자, 오른쪽 스위치 (켜짐 = 초록 바탕 · 손잡이 오른쪽) */
    static final class Toggle extends AbstractWidget {
        boolean value;

        Toggle(int x, int y, int w, int h, Component label, boolean value) {
            super(x, y, w, h, label);
            this.value = value;
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            value = !value;
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (!active || !visible || !event.isSelection()) return false;
            value = !value;
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
            face(g, this, isHoveredOrFocused());
            Font f = font();
            int sw = 22;
            int sh = 10;
            int sx = getX() + getWidth() - sw - 5;
            int sy = getY() + (getHeight() - sh) / 2;
            g.text(f, FormScreen.clip(f, getMessage().getString(), sx - getX() - 12), getX() + 7, textY(this), 0xFFFFFFFF, true);
            g.fill(sx, sy, sx + sw, sy + sh, 0xFF0A0A10);
            g.fill(sx + 1, sy + 1, sx + sw - 1, sy + sh - 1, value ? 0xFF3FA34D : 0xFF3A3D4A);
            int kx = value ? sx + sw - 9 : sx + 1;
            g.fill(kx, sy + 1, kx + 8, sy + sh - 1, value ? 0xFFF4F4F4 : 0xFF9A9CA6);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /** 고르기: 고른 값 · 오른쪽 ▼. 누르면 onOpen (창이 목록을 펼침), 초점일 때 ← → 로도 바꿈 */
    static final class Choice extends AbstractWidget {
        final List<String> options;
        int index;
        private final Consumer<Choice> onOpen;

        Choice(int x, int y, int w, int h, Component label, List<String> options, int index, Consumer<Choice> onOpen) {
            super(x, y, w, h, label);
            this.options = options;
            this.index = Math.max(0, Math.min(options.size() - 1, index));
            this.onOpen = onOpen;
        }

        String current() {
            return options.isEmpty() ? "" : options.get(index);
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            onOpen.accept(this);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (!active || !visible) return false;
            if (event.isSelection()) {
                playDownSound(Minecraft.getInstance().getSoundManager());
                onOpen.accept(this);
                return true;
            }
            if (event.isLeft() || event.isRight()) {
                index = Math.floorMod(index + (event.isRight() ? 1 : -1), Math.max(1, options.size()));
                return true;
            }
            return false;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
            face(g, this, isHoveredOrFocused());
            Font f = font();
            g.text(f, FormScreen.clip(f, current(), getWidth() - 26), getX() + 7, textY(this), 0xFFFFFFFF, true);
            // ▼ (금색 삼각형)
            int ax = getX() + getWidth() - 14;
            int ay = getY() + getHeight() / 2 - 2;
            for (int r = 0; r < 4; r++) g.fill(ax + r, ay + r, ax + 7 - r, ay + r + 1, 0xFFE8C46A);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /** 정수 밀대 (min ~ max, 1 씩): 옅은 바탕 · 손잡이까지 금빛 · 금색 손잡이 · 가운데 "이름: 값" */
    static final class Slider extends AbstractSliderButton {
        private final String label;
        private final int min;
        private final int max;

        Slider(int x, int y, int w, int h, String label, int min, int max, int v) {
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

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
            face(g, this, isHoveredOrFocused());
            int hx = getX() + (int) (value * (getWidth() - 8));
            g.fill(getX() + 1, getY() + 1, hx + 4, getY() + getHeight() - 1, 0x40E8B23A);
            g.fill(hx, getY(), hx + 8, getY() + getHeight(), 0xFF0A0A10);
            g.fill(hx + 1, getY() + 1, hx + 7, getY() + getHeight() - 1, isHoveredOrFocused() ? 0xFFFFE08A : 0xFFE0B451);
            g.fill(hx + 2, getY() + 2, hx + 6, getY() + 3, 0x80FFFFFF);
            g.centeredText(font(), getMessage(), getX() + getWidth() / 2, textY(this), 0xFFFFFFFF);
        }
    }

    /** 확인 단추 (금테 — 마우스를 올리면 더 밝게) */
    static final class Action extends AbstractWidget {
        private final Runnable run;

        Action(int x, int y, int w, int h, Component label, Runnable run) {
            super(x, y, w, h, label);
            this.run = run;
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            run.run();
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (!active || !visible || !event.isSelection()) return false;
            playDownSound(Minecraft.getInstance().getSoundManager());
            run.run();
            return true;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
            FormScreen.sprite(g, isHoveredOrFocused() ? "dialog_btn_press" : "dialog_btn_hover", getX(), getY(), getWidth(), getHeight());
            g.centeredText(font(), getMessage(), getX() + getWidth() / 2, textY(this), 0xFFFFFFFF);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
