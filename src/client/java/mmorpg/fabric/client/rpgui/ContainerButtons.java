package mmorpg.fabric.client.rpgui;

import java.util.List;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.client.mixin.AbstractContainerScreenAccessor;
import mmorpg.fabric.container.ContainerTools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * 0.23.0 상자 정렬 · 모두 넣기 · 같은 종류 넣기 단추 (상자 창 오른쪽 — 위에서부터, 사용자가 고른 것): 금테 단추 · 그림, 마우스를 올리면 풀이,
 * 누르면 서버가 처리 (ContainerTools — 쓰레기통 같은 창은 서버가 "안 됨" 이라 답해 단추가 보이지 않음).
 */
public final class ContainerButtons extends AbstractWidget {
    public static final int S = 20;
    static final int GAP = 2;
    static final List<Identifier> ICONS = List.of(Mmorpg.id("rpgui/icon_sort"), Mmorpg.id("rpgui/icon_deposit"), Mmorpg.id("rpgui/icon_stack"));
    static final List<List<String>> TIPS = List.of(
            List.of("§6§l정렬", "§7같은 아이템을 합치고 줄 세움", "§7무기 → 갑옷 → 룬 → 재료 → 그 밖", "§8같은 종류는 등급 · 레벨 높은 것 먼저"),
            List.of("§a§l모두 넣기", "§7가방의 아이템을 모두 상자에", "§8단축 줄 · 갑옷 · 왼손은 그대로"),
            List.of("§b§l같은 종류 넣기", "§7상자에 이미 있는 종류만 가방에서 넣음", "§8단축 줄 · 갑옷 · 왼손은 그대로"));

    private final AbstractContainerScreen<?> screen;
    private int pressedAt = -1;
    private long pressedMs;

    ContainerButtons(AbstractContainerScreen<?> screen) {
        super(0, 0, S, ContainerTools.ACTIONS.size() * (S + GAP), Component.literal("상자 단추"));
        this.screen = screen;
        place();
    }

    private void place() {
        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) screen;
        setX(a.mmorpg$leftPos() + a.mmorpg$imageWidth() + GAP);
        setY(a.mmorpg$topPos() + 4);
    }

    /** 서버가 이 창에 단추를 허락했는지 (시험용으로도) */
    public boolean shown() {
        return ClientRpgUi.allowed(screen.getMenu().containerId);
    }

    /** 시험용: 단추 i (0 정렬 · 1 모두 넣기 · 2 같은 종류) 의 가운데 */
    public int[] center(int i) {
        place();
        return new int[] {getX() + S / 2, btnY(i) + S / 2};
    }

    private int btnY(int i) {
        return getY() + i * (S + GAP);
    }

    private int at(double mx, double my) {
        if (mx < getX() || mx >= getX() + S) return -1;
        for (int i = 0; i < ContainerTools.ACTIONS.size(); i++) if (my >= btnY(i) && my < btnY(i) + S) return i;
        return -1;
    }

    @Override
    public boolean isMouseOver(double mx, double my) {
        return visible && shown() && at(mx, my) >= 0;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!shown()) return false;
        place();
        int i = at(event.x(), event.y());
        if (i < 0) return false;
        playDownSound(Minecraft.getInstance().getSoundManager());
        pressedAt = i;
        pressedMs = System.currentTimeMillis();
        ClientRpgUi.sendAction(screen.getMenu().containerId, ContainerTools.ACTIONS.get(i));
        return true;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float partialTick) {
        if (!shown()) return;
        place();
        Font font = Minecraft.getInstance().font;
        int hover = at(mx, my);
        boolean recent = System.currentTimeMillis() - pressedMs < 150;
        for (int i = 0; i < ICONS.size(); i++) {
            int y = btnY(i);
            boolean pressed = recent && pressedAt == i;
            RpgUi.button(g, getX(), y, S, S, hover == i, pressed);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, ICONS.get(i), getX() + 2, y + 2 + (pressed ? 1 : 0), 16, 16);
        }
        if (hover >= 0) g.setComponentTooltipForNextFrame(font, TIPS.get(hover).stream().map(s -> (Component) Component.literal(s)).toList(), mx, my);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
