package mmorpg.fabric.mixin;

import mmorpg.fabric.economy.DropGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 0.21.0 버린 아이템은 가방으로 (사용자 요청 "아이템을 버리면 인벤으로 다시 들어가는 기능"): 플레이어가 아이템을 떨어뜨리는 모든 길
 * (Q · Ctrl+Q · 창 밖으로 끌어 놓기 — ServerPlayer.drop) 의 처음에 살아 있으면 가방에 다시 넣고 떨어뜨리지 않음 (DropGuard). 죽을 때 떨어지는 것 ·
 * 가방이 가득 차서 남은 몫은 그대로 떨어짐. 지우려면 쓰레기통 (/쓰레기통).
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDropMixin {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"),
            cancellable = true)
    private void mmorpg$keepDropped(ItemStack stack, boolean throwRandomly, Prediction prediction, CallbackInfoReturnable<ItemEntity> cir) {
        if (DropGuard.keep((ServerPlayer) (Object) this, stack)) cir.setReturnValue(null);
    }
}
