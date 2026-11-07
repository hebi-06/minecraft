package mmorpg.fabric.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 체력 데이터 칸 (모드 없는 클라이언트의 자기 체력을 하트 비율로 — VanillaPackets) */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("DATA_HEALTH_ID")
    static EntityDataAccessor<Float> mmorpg$healthId() {
        throw new AssertionError();
    }
}
