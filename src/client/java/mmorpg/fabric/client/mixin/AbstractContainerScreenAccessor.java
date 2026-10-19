package mmorpg.fabric.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 상자 창의 자리 · 크기 (0.22.0 엔더 창고 상자 탭을 창 왼쪽에 붙이려고 — ClientStorage),
 * 0.23.0 RPG 창 (금테 바탕 · 금빛 글자 · 등급 색 칸 — RpgScreens): 제목 · "인벤토리" 글자 자리, 마우스 아래 칸
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int mmorpg$leftPos();

    @Accessor("topPos")
    int mmorpg$topPos();

    @Accessor("imageWidth")
    int mmorpg$imageWidth();

    @Accessor("imageHeight")
    int mmorpg$imageHeight();

    @Accessor("titleLabelX")
    int mmorpg$titleLabelX();

    @Accessor("titleLabelY")
    int mmorpg$titleLabelY();

    @Accessor("inventoryLabelX")
    int mmorpg$inventoryLabelX();

    @Accessor("inventoryLabelY")
    int mmorpg$inventoryLabelY();

    @Accessor("playerInventoryTitle")
    Component mmorpg$playerInventoryTitle();

    @Accessor("hoveredSlot")
    Slot mmorpg$hoveredSlot();
}
