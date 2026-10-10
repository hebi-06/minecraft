package mmorpg.fabric.mixin;

import mmorpg.fabric.boss.ArenaGuard;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 블록 아이템을 놓은 뒤 (arena.js onPlace — 베드락 afterEvents.playerPlaceBlock): 미리 막지 못하고 지어진 보스 필드 안에 놓인 블록은
 * (운영자 · 크리에이티브가 아니면) 되돌리고 아이템을 돌려줌.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place", at = @At("RETURN"))
    private void mmorpg$arenaPlace(BlockPlaceContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue() != null && cir.getReturnValue().consumesAction()) ArenaGuard.afterPlace(ctx);
    }
}
