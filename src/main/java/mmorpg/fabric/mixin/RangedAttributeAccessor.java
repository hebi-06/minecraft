package mmorpg.fabric.mixin;

import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 범위 속성의 최댓값 (최대 체력 상한 1024 → 보스 체력 90000 — mob.RpgEntities) */
@Mixin(RangedAttribute.class)
public interface RangedAttributeAccessor {
    @Mutable
    @Accessor("maxValue")
    void mmorpg$setMaxValue(double max);
}
