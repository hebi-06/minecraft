package mmorpg.fabric.client.mixin;

import mmorpg.fabric.client.motion.ClientMotions;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 0.38.0 동작: 공격 키 — 평타 연타 단계를 정해 서버에 먼저 보내고 (휘두르기에 동작 시작 — ClientLivingEntityMixin), 구르는 동안은 공격 없음 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void mmorpg$attackBegin(CallbackInfoReturnable<Boolean> cir) {
        if (!ClientMotions.attackBegin((Minecraft) (Object) this)) {
            ClientMotions.attackEnd();
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "startAttack", at = @At("RETURN"))
    private void mmorpg$attackEnd(CallbackInfoReturnable<Boolean> cir) {
        ClientMotions.attackEnd();
    }
}
