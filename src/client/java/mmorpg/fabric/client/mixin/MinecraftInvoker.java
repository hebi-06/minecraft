package mmorpg.fabric.client.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 0.39.0 선입력: 평타 사이가 지나면 미뤄 둔 공격 키를 그때 누른 것처럼 (Minecraft.startAttack — ClientMotions.tick) */
@Mixin(Minecraft.class)
public interface MinecraftInvoker {
    @Invoker("startAttack")
    boolean mmorpg$startAttack();
}
