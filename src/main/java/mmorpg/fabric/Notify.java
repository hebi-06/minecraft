package mmorpg.fabric;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** 화면 하단 경고 · 알림 · 채팅 · 그 사람에게만 들리는 소리 (fx.js warn · notify). 글자는 애드온의 베드락 서식 그대로 받아 자바 색으로 */
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
        sp.sendSystemMessage(Component.literal(BedrockText.toJava(text)), true);
        sound(sp, SoundEvents.NOTE_BLOCK_BASS, 0.8F, 0.7F);
    }

    /** 화면 하단 알림 (fx.js notify) */
    public static void notify(ServerPlayer sp, String text) {
        sp.sendSystemMessage(Component.literal(BedrockText.toJava(text)), true);
    }

    /** 채팅 "[RPG] …" */
    public static void say(ServerPlayer sp, String text) {
        sp.sendSystemMessage(Component.literal(BedrockText.toJava(PREFIX + text)));
    }

    /** 그 플레이어에게만 들리는 소리 (애드온 player.playSound) */
    public static void sound(ServerPlayer sp, Holder<SoundEvent> sound, float volume, float pitch) {
        sp.connection.send(new ClientboundSoundPacket(sound, SoundSource.PLAYERS, sp.getX(), sp.getEyeY(), sp.getZ(), volume, pitch, sp.getRandom().nextLong()));
    }

    public static void sound(ServerPlayer sp, SoundEvent sound, float volume, float pitch) {
        sound(sp, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), volume, pitch);
    }

    /** 아이템 주기 (가방이 가득이면 발밑에 떨굼 — 애드온 giveItem) */
    public static void give(ServerPlayer sp, ItemStack stack) {
        if (sp.getInventory().add(stack) || stack.isEmpty()) return;
        sp.level().addFreshEntity(new ItemEntity(sp.level(), sp.getX(), sp.getY(), sp.getZ(), stack));
    }

    static void forget(UUID id) {
        LAST_WARN.remove(id);
    }
}
