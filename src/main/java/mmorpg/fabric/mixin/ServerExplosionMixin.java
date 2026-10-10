package mmorpg.fabric.mixin;

import java.util.List;
import mmorpg.fabric.boss.ArenaGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 폭발 (크리퍼 · TNT …) 이 지어진 보스 필드의 블록을 부수지 않게 (arena.js onExplosionBefore — 베드락 beforeEvents.explosion 의 setImpactedBlocks):
 * 부술 블록 목록에서 필드 안 칸을 뺌.
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
    @Inject(method = "calculateExplodedPositions", at = @At("RETURN"), cancellable = true)
    private void mmorpg$keepArena(CallbackInfoReturnable<List<BlockPos>> cir) {
        List<BlockPos> list = cir.getReturnValue();
        List<BlockPos> keep = ArenaGuard.filterExplosion(((ServerExplosion) (Object) this).level(), list);
        if (keep != list) cir.setReturnValue(keep);
    }
}
