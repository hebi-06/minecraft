package mmorpg.fabric.client.mixin;

import mmorpg.fabric.client.NavHud;
import mmorpg.fabric.client.TitleFit;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 큰 글자 (제목) 가 떠 있는 동안 길 안내 칸을 숨기려고 제목 패킷을 알려 줌 (애드온: 일반 제목이 떠 있으면 UI 가 칸을 숨김 — hud_screen.json rpg_nav_row),
 * 긴 제목 · 부제목을 화면에 맞게 줄이려고 글을 알려 줌 (TitleFit). 패킷을 처리한 뒤 (TAIL — 게임 스레드에서만 닿음)
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientTitleMixin {
    @Inject(method = "setTitleText", at = @At("TAIL"))
    private void mmorpg$title(ClientboundSetTitleTextPacket packet, CallbackInfo ci) {
        NavHud.onTitle(packet.text());
        TitleFit.onTitle(packet.text());
    }

    @Inject(method = "setSubtitleText", at = @At("TAIL"))
    private void mmorpg$subtitle(ClientboundSetSubtitleTextPacket packet, CallbackInfo ci) {
        TitleFit.onSubtitle(packet.text());
    }

    @Inject(method = "setTitlesAnimation", at = @At("TAIL"))
    private void mmorpg$titleTimes(ClientboundSetTitlesAnimationPacket packet, CallbackInfo ci) {
        NavHud.onTitleTimes(packet.getFadeIn(), packet.getStay(), packet.getFadeOut());
        TitleFit.onTimes(packet.getFadeIn(), packet.getStay(), packet.getFadeOut());
    }

    @Inject(method = "handleTitlesClear", at = @At("TAIL"))
    private void mmorpg$titleClear(ClientboundClearTitlesPacket packet, CallbackInfo ci) {
        NavHud.onTitleClear();
        TitleFit.onClear(packet.shouldResetTimes());
    }
}
