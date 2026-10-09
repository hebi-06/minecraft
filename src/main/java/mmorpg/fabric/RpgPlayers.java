package mmorpg.fabric;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Stats;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * 플레이어 (main.js onJoin · levelUpTick, stats.js): 처음 접속하면 시작 포인트 · 스탯 책, 경험치 레벨이 처음 오를 때마다
 * 포인트 +3, 스탯 찍기 · 초기화. 데이터가 바뀌면 최대 체력을 다시 계산하고 클라이언트 (스탯 창 · HUD) 에 보냄.
 */
public final class RpgPlayers {
    private RpgPlayers() {}

    /** 이번 서버 실행에서 접속 처리를 한 플레이어 */
    private static final Set<UUID> JOINED = new HashSet<>();

    static void init() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onLeave(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            RpgHealth.onRespawn(newPlayer, alive, oldPlayer.getHealth());
            RpgNet.sync(newPlayer, false);
        });
        ServerTickEvents.END_SERVER_TICK.register(RpgPlayers::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> JOINED.clear());
    }

    /** 접속 (애드온 onJoin · setupNewPlayer). 처음이면 스탯 0 · 시작 포인트 · 최고 레벨 = 지금 레벨, 2초 뒤 스탯 책 */
    public static void onJoin(ServerPlayer sp) {
        JOINED.add(sp.getUUID());
        RpgData d = RpgData.of(sp);
        boolean isNew = !d.started();
        if (isNew) RpgData.set(sp, RpgData.first(sp.experienceLevel));
        RpgHealth.onJoin(sp);
        RpgNet.sync(sp, false);
        if (isNew) {
            Tasks.later(40, () -> {
                if (sp.isRemoved()) return;
                Notify.give(sp, new ItemStack(RpgItems.STAT_BOOK));
                Notify.say(sp, "스탯 책을 받았습니다. §7누르면 (또는 키 K) 스탯 창이 열립니다.");
            });
        }
    }

    static void onLeave(ServerPlayer sp) {
        JOINED.remove(sp.getUUID());
        RpgHealth.saveRatio(sp, true);
        RpgHealth.forget(sp.getUUID());
        RpgCombat.forget(sp.getUUID());
        Notify.forget(sp.getUUID());
    }

    private static void tick(MinecraftServer server) {
        long now = Tasks.now();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            // 접속 알림을 받지 못한 플레이어 (다른 모드 · 시험용 가짜 플레이어) 도 접속 처리
            if (!JOINED.contains(sp.getUUID())) onJoin(sp);
            RpgHealth.feed(sp);
            if (now % 4 == 0) checkLevelUp(sp);
            if (now % 20 == 0) RpgHealth.periodic(sp);
        }
    }

    /** 경험치 레벨이 처음 도달한 값이면 포인트 + 알림 (stats.js checkLevelUp, main.js levelUpTick). 받은 포인트 수 */
    public static int checkLevelUp(ServerPlayer sp) {
        RpgData d = RpgData.of(sp);
        int lv = sp.experienceLevel;
        if (lv <= d.lvmax()) return 0;
        int gained = (int) Stats.checkLevelUp(lv, d.lvmax());
        update(sp, d.withLvmax(lv).withPoint(d.point() + gained));
        Notify.notify(sp, "§e§l레벨 업! §r§f스탯 포인트 +" + gained + " §7(스탯 창: K · 스탯 책)");
        sp.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1F, 1.2F);
        return gained;
    }

    /** 데이터 바꿈 → 최대 체력 다시 (스탯 · 레벨: 늘어난 만큼 체력도) → 클라이언트에 보냄 */
    public static void update(ServerPlayer sp, RpgData d) {
        RpgData.set(sp, d);
        RpgHealth.refreshMax(sp, false);
        RpgNet.sync(sp, false);
    }

    /** 포인트를 써서 스탯 올림 (statui.js · stats.js spendPoint). 올린 수 (포인트가 모자라면 있는 만큼) */
    public static int spend(ServerPlayer sp, String key, int count) {
        if (!Stats.STAT_KEYS.contains(key) || count < 1) return 0;
        RpgData d = RpgData.of(sp);
        int n = Math.min(count, d.point());
        if (n <= 0) {
            sp.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.7F);
            RpgNet.sync(sp, false);
            return 0;
        }
        update(sp, d.withStat(key, d.stat(key) + n).withPoint(d.point() - n));
        sp.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.6F);
        Fx.statUp(sp);
        return n;
    }

    /** 포인트 더하기 (/rpg points, 음수는 빼기 — 0 아래로는 안 감) */
    public static void addPoints(ServerPlayer sp, int n) {
        RpgData d = RpgData.of(sp);
        update(sp, d.withPoint(Math.max(0, d.point() + n)));
    }

    /** 찍은 스탯을 모두 포인트로 되돌림 (stats.js resetStats). 돌려받은 포인트 */
    public static int reset(ServerPlayer sp) {
        RpgData d = RpgData.of(sp);
        int refund = d.str() + d.dex() + d.intel() + d.vit();
        update(sp, new RpgData(0, 0, 0, 0, d.point() + refund, d.lvmax(), d.hpRatio(), true));
        return refund;
    }

    /** 스탯 값을 바로 정함 (/rpg set — 시험용, 포인트는 그대로) */
    public static void setStat(ServerPlayer sp, String key, int value) {
        RpgData d = RpgData.of(sp);
        update(sp, d.withStat(key, Math.max(0, value)));
    }
}
