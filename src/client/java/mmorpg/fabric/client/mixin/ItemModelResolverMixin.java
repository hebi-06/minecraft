package mmorpg.fabric.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import mmorpg.fabric.client.CosmeticRender;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 무기 스킨 (장식 — 0.13.0): 사람이 손에 든 아이템을 그릴 준비 (1인칭 · 3인칭 모두 updateForLiving) 에서, 스킨이 있으면 모델만 바꾼 복사본으로.
 * 가방 · 핫바 아이콘 (updateForTopItem) · 땅에 떨어진 것 (updateForNonLiving) 은 지나가지 않음 — 아이콘은 그대로.
 */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @ModifyVariable(method = "updateForLiving", at = @At("HEAD"), argsOnly = true)
    private ItemStack mmorpg$weaponSkin(ItemStack stack, @Local(argsOnly = true) ItemDisplayContext context, @Local(argsOnly = true) LivingEntity entity) {
        return CosmeticRender.skinned(stack, context, entity);
    }
}
