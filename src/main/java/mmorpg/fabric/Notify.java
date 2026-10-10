package mmorpg.fabric;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
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
    /** 이 틱까지는 화면 하단 상태 줄 (스킬 재사용 표시 등) 이 알림을 덮어쓰지 않음 (fx.js noticeUntil) */
    private static final Map<UUID, Long> NOTICE_UNTIL = new HashMap<>();
    /** 상태 줄 덧붙임 (fx.js addHudSuffix — 룬 · 초월 무기 표시 뒤: 마검 부여 등) */
    private static final List<Function<ServerPlayer, String>> HUD_SUFFIX = new ArrayList<>();

    static void init() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            LAST_WARN.clear();
            NOTICE_UNTIL.clear();
        });
    }

    /** 화면 하단 경고 + 낮은 소리 (1.5초에 한 번 — fx.js warn). 2초 동안 상태 줄이 덮어쓰지 않음 */
    public static void warn(ServerPlayer sp, String text) {
        long now = Tasks.now();
        Long last = LAST_WARN.get(sp.getUUID());
        if (last != null && last + 30 > now) return;
        LAST_WARN.put(sp.getUUID(), now);
        NOTICE_UNTIL.put(sp.getUUID(), now + 40);
        sp.sendSystemMessage(Component.literal(BedrockText.toJava(text)), true);
        sound(sp, SoundEvents.NOTE_BLOCK_BASS, 0.8F, 0.7F);
    }

    /** 화면 하단 알림 (fx.js notify — 3초 동안 상태 줄이 덮어쓰지 않음) */
    public static void notify(ServerPlayer sp, String text) {
        notify(sp, text, 60);
    }

    /** 화면 하단 알림, hold 틱 동안 상태 줄이 덮어쓰지 않음 */
    public static void notify(ServerPlayer sp, String text, int hold) {
        NOTICE_UNTIL.put(sp.getUUID(), Tasks.now() + hold);
        sp.sendSystemMessage(Component.literal(BedrockText.toJava(text)), true);
    }

    /** 지금 상태 줄을 띄워도 되는지 (알림 · 경고가 떠 있는 동안은 아님 — fx.js actionbarFree) */
    public static boolean actionbarFree(ServerPlayer sp) {
        Long until = NOTICE_UNTIL.get(sp.getUUID());
        return until == null || until <= Tasks.now();
    }

    /** 상태 줄 (재사용 대기 표시 등 — 알림으로 치지 않음) */
    public static void bar(ServerPlayer sp, String text) {
        sp.sendSystemMessage(Component.literal(BedrockText.toJava(text)), true);
    }

    public static void addHudSuffix(Function<ServerPlayer, String> fn) {
        if (fn != null) HUD_SUFFIX.add(fn);
    }

    /** 상태 줄에 덧붙일 글자 (" §8| …" 모양, 없으면 "") */
    public static String hudSuffix(ServerPlayer sp) {
        StringBuilder out = new StringBuilder();
        for (Function<ServerPlayer, String> fn : HUD_SUFFIX) {
            try {
                String t = fn.apply(sp);
                if (t != null && !t.isEmpty()) out.append(" §8| ").append(t);
            } catch (RuntimeException e) {
                Mmorpg.LOG.warn("[mmorpg] hud suffix", e);
            }
        }
        return out.toString();
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

    /** 큰 글자 (hp.js showRealTitle — 0.5초 차오름 · 3.5초 · 1초 걷힘). subtitle 은 null 이면 없음 */
    public static void title(ServerPlayer sp, String title, String subtitle) {
        sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 70, 20));
        sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal(subtitle == null ? "" : BedrockText.toJava(subtitle))));
        sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal(BedrockText.toJava(title))));
    }

    /** 아이템 주기 (가방이 가득이면 발밑에 떨굼 — 애드온 giveItem) */
    public static void give(ServerPlayer sp, ItemStack stack) {
        if (sp.getInventory().add(stack) || stack.isEmpty()) return;
        sp.level().addFreshEntity(new ItemEntity(sp.level(), sp.getX(), sp.getY(), sp.getZ(), stack));
    }

    static void forget(UUID id) {
        LAST_WARN.remove(id);
        NOTICE_UNTIL.remove(id);
    }
}
