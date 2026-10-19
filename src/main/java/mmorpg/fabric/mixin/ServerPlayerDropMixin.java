package mmorpg.fabric.mixin;

import mmorpg.fabric.economy.DropGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 0.21.0 버린 아이템은 가방으로 (사용자 요청 "아이템을 버리면 인벤으로 다시 들어가는 기능"):
 * <ul>
 * <li>핫바에서 Q · Ctrl+Q (ServerPlayer.drop(boolean)): 처음에 막음 — 아무것도 빼지 않고 그 칸 그대로 (클라이언트가 미리 뺀 칸은 다시 보냄)</li>
 * <li>창 안에서 Q · 창 밖으로 끌어 놓기 (창이 이미 뺀 아이템으로 ServerPlayer.drop(아이템, …)): 살아 있으면 가방에 다시 넣고 떨어뜨리지 않음</li>
 * </ul>
 * 죽을 때 떨어지는 것 · 창작 · 관전 · 가방이 가득 차서 남은 몫은 그대로 떨어짐 (DropGuard). 지우려면 쓰레기통 (/쓰레기통).
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDropMixin {
    @Inject(method = "drop(Z)V", at = @At("HEAD"), cancellable = true)
    private void mmorpg$keepSelected(boolean all, CallbackInfo ci) {
        if (DropGuard.blockHotbarDrop((ServerPlayer) (Object) this)) ci.cancel();
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"),
            cancellable = true)
    private void mmorpg$keepDropped(ItemStack stack, boolean throwRandomly, Prediction prediction, CallbackInfoReturnable<ItemEntity> cir) {
        if (DropGuard.keep((ServerPlayer) (Object) this, stack)) cir.setReturnValue(null);
    }
}
