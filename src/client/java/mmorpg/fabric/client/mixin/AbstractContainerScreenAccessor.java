package mmorpg.fabric.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 0.22.0 상자 창의 자리 · 크기 (엔더 창고 상자 탭을 창 왼쪽에 붙이려고 — ClientStorage) */
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
}
