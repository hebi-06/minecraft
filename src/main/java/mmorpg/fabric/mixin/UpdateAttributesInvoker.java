package mmorpg.fabric.mixin;

import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** 속성 값 목록으로 속성 패킷 만들기 (공개 생성자는 AttributeInstance 만 받음 — VanillaPackets 가 최대 체력만 바꾼 새 패킷) */
@Mixin(ClientboundUpdateAttributesPacket.class)
public interface UpdateAttributesInvoker {
    @Invoker("<init>")
    static ClientboundUpdateAttributesPacket mmorpg$create(int entityId, List<ClientboundUpdateAttributesPacket.AttributeSnapshot> attributes) {
        throw new AssertionError();
    }
}
