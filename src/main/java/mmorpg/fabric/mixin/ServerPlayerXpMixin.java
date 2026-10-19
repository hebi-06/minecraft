package mmorpg.fabric.mixin;

import mmorpg.core.Leveling;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 0.24.2 최고 레벨 (LEVELING.maxLevel = 60): 레벨을 바로 정하는 길 (/xp set … levels · 명령 · 시험) 도 최고 레벨까지만, 최고 레벨이면 막대 (그 레벨 안의 경험치) 는 비움.
 * 경험치를 받는 길 (giveExperiencePoints · giveExperienceLevels) 은 PlayerMixin.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerXpMixin {
    @ModifyVariable(method = "setExperienceLevels", at = @At("HEAD"), argsOnly = true)
    private int mmorpg$capLevel(int level) {
        return Math.min(level, Leveling.maxLevel());
    }

    @Inject(method = "setExperienceLevels", at = @At("TAIL"))
    private void mmorpg$emptyAtMax(int level, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (Leveling.atMax(self.experienceLevel)) self.experienceProgress = 0F;
    }

    @Inject(method = "setExperiencePoints", at = @At("TAIL"))
    private void mmorpg$noPointsAtMax(int points, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (Leveling.atMax(self.experienceLevel)) self.experienceProgress = 0F;
    }
}
