package mmorpg.fabric.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 마네킹 (RPG NPC 의 몸) 의 감춰진 설정: 밀리지 않음 · 아래 설명 줄 · 스킨 */
@Mixin(Mannequin.class)
public interface MannequinAccessor {
    @Invoker("setImmovable")
    void mmorpg$setImmovable(boolean immovable);

    @Invoker("setDescription")
    void mmorpg$setDescription(Component description);

    @Invoker("setHideDescription")
    void mmorpg$setHideDescription(boolean hide);

    @Invoker("setProfile")
    void mmorpg$setProfile(ResolvableProfile profile);
}
