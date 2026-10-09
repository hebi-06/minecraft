package mmorpg.fabric;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/** 화면 하단 경고 · 알림 · 채팅 (fx.js warn · notify). 글자는 애드온의 베드락 서식 그대로 받아 자바 색으로 바꿈 */
public final class Notify {
    private Notify() {}

    public static final String PREFIX = "§6[RPG] §f";
    private static final Map<UUID, Long> LAST_WARN = new HashMap<>();

    static void init() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_WARN.clear());
    }

    /** 화면 하단 경고 + 낮은 소리 (1.5초에 한 번 — fx.js warn) */
    public static void warn(ServerPlayer sp, String text) {
        long now = Tasks.now();
        Long last = LAST_WARN.get(sp.getUUID());
        if (last != null && last + 30 > now) return;
        LAST_WARN.put(sp.getUUID(), now);
        sp.displayClientMessage(Component.literal(BedrockText.toJava(text)), true);
        sp.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.7F);
    }

    /** 화면 하단 알림 (fx.js notify) */
    public static void notify(ServerPlayer sp, String text) {
        sp.displayClientMessage(Component.literal(BedrockText.toJava(text)), true);
    }

    /** 채팅 "[RPG] …" */
    public static void say(ServerPlayer sp, String text) {
        sp.sendSystemMessage(Component.literal(BedrockText.toJava(PREFIX + text)));
    }

    /** 아이템 주기 (가방이 가득이면 발밑에 떨굼 — 애드온 giveItem) */
    public static void give(ServerPlayer sp, ItemStack stack) {
        if (!sp.getInventory().add(stack) && !stack.isEmpty()) sp.drop(stack, false);
    }

    static void forget(UUID id) {
        LAST_WARN.remove(id);
    }
}
