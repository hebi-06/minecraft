package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 0.51.0 다시 쓰기 (재설계안 2판 — 사용자 "스킬 컨셉은 롤같은거 많이 참고하면 좋음": 리븐 R · 아트록스 Q 처럼 같은 칸을 몇 초 안에 다시 누르면 다음 단계):
 * 스킬이 나가며 {@link #open} 하면 그 칸은 window 틱 동안 재사용 대기 없이 다시 누를 수 있음 (JobSkills.press — 재사용 대기는 마지막 단계가 나가거나 창이 닫힐 때
 * 시작). 다시 누르면 같은 스킬 함수가 불리고 {@link #stage} 로 몇 번째인지 앎. 창이 닫히면 (다시 누르지 않음) onExpire (리븐 R 처럼 끝날 때 저절로 나가는 것 —
 * 없으면 null) 를 부르고 재사용 대기 시작. 다시 누른 단계의 동작은 {@link #motion} (JobSystem.Slot.cast 가 스킬 id 동작 대신 보냄).
 */
public final class JobRecast {
    private JobRecast() {}

    /** 열린 창: 단계 (1 = 첫 단계가 나감 — 다음 누름이 2단계) · 닫히는 틱 · 열린 틱 · 다시 누를 수 있는 가장 이른 틱 (열린 틱부터) · 재사용 대기 (틱) · 목숨 · 닫힐 때 */
    private record Open(int stage, long until, long at, int gap, int cd, String life, Runnable onExpire) {}

    private static final Map<String, Open> OPEN = new HashMap<>();
    private static final Map<UUID, String> MOTION = new HashMap<>();
    /** 다시 누를 수 있는 가장 이른 틱 (앞 단계가 나가고 이만큼 — 같은 틱에 두 번 눌러 두 단계가 겹치지 않게 · 스킬마다 더 길게: open 의 gap) */
    static final int MIN_GAP = 6;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(JobRecast::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            OPEN.clear();
            MOTION.clear();
        });
    }

    private static String key(ServerPlayer sp, String skillId) {
        return sp.getUUID() + "|" + skillId;
    }

    /** 스킬 k 가 stage 단계까지 나감 — window 틱 안에 다시 누르면 다음 단계 (닫히면 onExpire 뒤 재사용 대기) */
    static void open(ServerPlayer sp, Jobs.Skill k, int stage, int window, Runnable onExpire) {
        open(sp, k, stage, window, MIN_GAP, onExpire);
    }

    /**
     * 같은 것 — 다시 누름은 gap 틱 뒤부터 (같은 적을 다시 칠 때 몹 무적 시간 10틱을 넘기게: 핏빛 대검 · 다시 벼린 칼 — jobs.json gap). 그 전에 누르면 버림
     * (JobSkills.press 가 false — 재사용 대기도 그대로)
     */
    static void open(ServerPlayer sp, Jobs.Skill k, int stage, int window, int gap, Runnable onExpire) {
        int g = Math.max(MIN_GAP, gap);
        OPEN.put(key(sp, k.id()), new Open(stage, Tasks.now() + Math.max(g + 1, window), Tasks.now(), g, JobSystem.cooldownTicks(sp, k), RuneKit.lifeToken(sp),
                onExpire));
    }

    /** 다시 누를 수 있는 창이 열려 있는지 (JobSkills.press — 재사용 대기를 보지 않음) */
    public static boolean pending(ServerPlayer sp, String skillId) {
        Open o = OPEN.get(key(sp, skillId));
        return o != null && o.until() > Tasks.now();
    }

    /** 지금까지 나간 단계 (창이 없으면 0 — 다음 누름이 첫 단계) */
    static int stage(ServerPlayer sp, String skillId) {
        Open o = OPEN.get(key(sp, skillId));
        return o != null && o.until() > Tasks.now() ? o.stage() : 0;
    }

    /** 앞 단계가 나간 지 gap 틱이 지났는지 (안 지났으면 이번 누름은 버림) */
    public static boolean ready(ServerPlayer sp, String skillId) {
        Open o = OPEN.get(key(sp, skillId));
        return o == null || Tasks.now() - o.at() >= o.gap();
    }

    /** 남은 창 (틱 — 없으면 0) */
    public static long left(ServerPlayer sp, String skillId) {
        Open o = OPEN.get(key(sp, skillId));
        return o == null ? 0 : Math.max(0, o.until() - Tasks.now());
    }

    /** 마지막 단계가 나감: 창을 닫음 (재사용 대기는 JobSkills.press 가 — 창이 없으면 바로 시작) */
    static void close(ServerPlayer sp, String skillId) {
        OPEN.remove(key(sp, skillId));
    }

    /** 이번 누름의 동작 (단계 열쇠 — 스킬id:단계) */
    static void motion(ServerPlayer sp, String key) {
        MOTION.put(sp.getUUID(), key);
    }

    /** JobSystem.Slot.cast: 이번 누름에 정한 동작 (없으면 null — 스킬 id 동작) */
    static String takeMotion(ServerPlayer sp) {
        return MOTION.remove(sp.getUUID());
    }

    /** 이 사람의 창을 모두 닫음 (직업을 바꿈 · 시험 — 재사용 대기 없이) */
    public static void clear(ServerPlayer sp) {
        String p = sp.getUUID() + "|";
        OPEN.keySet().removeIf(k -> k.startsWith(p));
        MOTION.remove(sp.getUUID());
    }

    static void forget(UUID id) {
        String p = id + "|";
        OPEN.keySet().removeIf(k -> k.startsWith(p));
        MOTION.remove(id);
    }

    private static void tick(MinecraftServer server) {
        if (OPEN.isEmpty()) return;
        long now = Tasks.now();
        var it = OPEN.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Open o = e.getValue();
            if (o.until() > now) continue;
            it.remove();
            String k = e.getKey();
            int bar = k.indexOf('|');
            if (bar < 0) continue;
            ServerPlayer sp;
            try {
                sp = server.getPlayerList().getPlayer(UUID.fromString(k.substring(0, bar)));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (sp == null || !RuneKit.sameLife(sp, o.life())) continue;
            String skillId = k.substring(bar + 1);
            if (o.onExpire() != null) {
                try {
                    o.onExpire().run();
                } catch (RuntimeException ex) {
                    Mmorpg.LOG.warn("[mmorpg] job recast expire {}", skillId, ex);
                }
            }
            JobSkills.startCooldown(sp, skillId, o.cd());
        }
    }
}
