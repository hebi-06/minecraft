package mmorpg.fabric.client.rpgui;

import mmorpg.fabric.client.mixin.AbstractContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.Slot;

/**
 * 0.23.0 RPG 창 (사용자 요청 "인벤토리 UI 랑 상자 UI 를 RPG 느낌으로" → 시안 A 어둠의 금테): 인벤토리 · 상자 · 큰 상자 · 엔더 창고 · 통 · 셜커 상자.
 * 칸 자리는 바닐라 그대로 (다른 모드 · 손에 익은 자리), 바닐라 바탕을 그린 뒤 그 위를 덮음 (믹스인 — extractBackground 끝):
 * 금테 창 · 어두운 칸 (창의 모든 칸 자리) · 인벤토리 사람 그림 칸 (남빛) · 제작 화살표. 글자는 금빛 · 그림자, 칸 아래 등급 색, 금빛 마우스 강조.
 * 창작 모드 인벤토리 · 화로 · 제작대 같은 다른 창은 바닐라 그대로.
 */
public final class RpgScreens {
    private RpgScreens() {}

    /** RPG 창으로 그리는 창인지 */
    public static boolean themed(Screen s) {
        return s instanceof InventoryScreen || s instanceof ContainerScreen || s instanceof ShulkerBoxScreen;
    }

    private static AbstractContainerScreenAccessor acc(AbstractContainerScreen<?> s) {
        return (AbstractContainerScreenAccessor) s;
    }

    /** 창의 모든 칸 자리에 어두운 칸 (보이는 칸만) */
    private static void slots(GuiGraphicsExtractor g, AbstractContainerScreen<?> s, int left, int top) {
        for (Slot slot : s.getMenu().slots) if (slot.isActive()) RpgUi.slot(g, left + slot.x, top + slot.y);
    }

    /** 인벤토리 바탕: 금테 창 · 사람 그림 칸 (26, 8 — 49 × 70) · 제작 화살표 · 칸 */
    public static void inventoryBackground(InventoryScreen s, GuiGraphicsExtractor g) {
        AbstractContainerScreenAccessor a = acc(s);
        int l = a.mmorpg$leftPos();
        int t = a.mmorpg$topPos();
        RpgUi.panel(g, l, t, a.mmorpg$imageWidth(), a.mmorpg$imageHeight());
        g.blitSprite(RenderPipelines.GUI_TEXTURED, RpgUi.MODEL_BG, l + 26, t + 8, 49, 70);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, RpgUi.ARROW, l + 135, t + 30, 16, 13);
        slots(g, s, l, t);
    }

    /** 상자 · 셜커 바탕: 금테 창 · 칸 */
    public static void containerBackground(AbstractContainerScreen<?> s, GuiGraphicsExtractor g) {
        AbstractContainerScreenAccessor a = acc(s);
        int l = a.mmorpg$leftPos();
        int t = a.mmorpg$topPos();
        RpgUi.panel(g, l, t, a.mmorpg$imageWidth(), a.mmorpg$imageHeight());
        slots(g, s, l, t);
    }

    /** 인벤토리 글자 ("제작" — 창 기준 자리): 금빛 · 그림자. 그렸으면 true (바닐라 글자는 건너뜀) */
    public static boolean inventoryLabels(InventoryScreen s, GuiGraphicsExtractor g) {
        AbstractContainerScreenAccessor a = acc(s);
        RpgUi.label(g, font(), s.getTitle(), a.mmorpg$titleLabelX(), a.mmorpg$titleLabelY());
        return true;
    }

    /** 상자 · 셜커 글자 (제목 · "인벤토리"): 금빛 · 그림자. 그렸으면 true */
    public static boolean containerLabels(AbstractContainerScreen<?> s, GuiGraphicsExtractor g) {
        if (!themed(s) || s instanceof InventoryScreen) return false;
        AbstractContainerScreenAccessor a = acc(s);
        Font font = font();
        RpgUi.label(g, font, s.getTitle(), a.mmorpg$titleLabelX(), a.mmorpg$titleLabelY());
        RpgUi.label(g, font, a.mmorpg$playerInventoryTitle(), a.mmorpg$inventoryLabelX(), a.mmorpg$inventoryLabelY());
        return true;
    }

    /** 칸 하나를 그리기 전 (창 기준 자리): 아이템 아래 등급 색 · 마우스 아래면 금빛 강조 */
    public static void slotUnder(AbstractContainerScreen<?> s, GuiGraphicsExtractor g, Slot slot) {
        if (!themed(s)) return;
        RpgUi.gradeUnder(g, slot.getItem(), slot.x, slot.y, 16, Util.getMillis());
        if (slot == acc(s).mmorpg$hoveredSlot() && slot.isHighlightable()) RpgUi.hover(g, slot.x, slot.y, 16);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }
}
