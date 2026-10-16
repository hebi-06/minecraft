package mmorpg.fabric.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import mmorpg.fabric.client.CosmeticRender;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 무기 스킨 (장식 — 0.13.0): 사람이 손에 든 아이템을 그릴 준비에서, 스킨이 있으면 모델만 바꾼 복사본으로.
 * 3인칭 · 다른 사람은 updateForLiving, 내 1인칭 손은 updateForTopItem · appendItemLayers (주인 = 나 · 손 표시) 를 지나감 — 셋 다.
 * 가방 · 핫바 아이콘 (updateForTopItem 이지만 GUI 표시) · 땅에 떨어진 것 (updateForNonLiving) 은 그대로 — 아이콘은 바뀌지 않음.
 */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @ModifyVariable(method = "updateForLiving", at = @At("HEAD"), argsOnly = true)
    private ItemStack mmorpg$weaponSkin(ItemStack stack, @Local(argsOnly = true) ItemDisplayContext context, @Local(argsOnly = true) LivingEntity entity) {
        return CosmeticRender.skinned(stack, context, entity);
    }

    @ModifyVariable(method = "updateForTopItem", at = @At("HEAD"), argsOnly = true)
    private ItemStack mmorpg$weaponSkinTop(ItemStack stack, @Local(argsOnly = true) ItemDisplayContext context, @Local(argsOnly = true) ItemOwner owner) {
        return owner instanceof LivingEntity entity ? CosmeticRender.skinned(stack, context, entity) : stack;
    }

    /** 1인칭 손이 층을 바로 붙이는 경우도 (같은 복사본을 다시 만들어도 모습은 같음) */
    @ModifyVariable(method = "appendItemLayers", at = @At("HEAD"), argsOnly = true)
    private ItemStack mmorpg$weaponSkinLayers(ItemStack stack, @Local(argsOnly = true) ItemDisplayContext context, @Local(argsOnly = true) ItemOwner owner) {
        return owner instanceof LivingEntity entity ? CosmeticRender.skinned(stack, context, entity) : stack;
    }
}
