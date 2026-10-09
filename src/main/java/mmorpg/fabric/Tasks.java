package mmorpg.fabric;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * 서버 틱 시계와 "N틱 뒤에 할 일" (애드온의 system.currentTick · system.runTimeout).
 * 활 빛줄기가 닿는 순간의 피해, 완드 시전, 처음 접속한 사람의 스탯 책 등. 서버가 멈추면 남은 일은 버림.
 */
public final class Tasks {
    private Tasks() {}

    private record Task(long due, Runnable run) {}

    private static final List<Task> TASKS = new ArrayList<>();
    private static long now;

    static void init() {
        ServerTickEvents.START_SERVER_TICK.register(server -> tick());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> TASKS.clear());
    }

    /** 지금 틱 (서버가 켜진 뒤 센 틱 — 쿨타임 · 표시 기록에 씀) */
    public static long now() {
        return now;
    }

    /** ticks 틱 뒤 (최소 1) 서버 스레드에서 실행 */
    public static void later(int ticks, Runnable run) {
        TASKS.add(new Task(now + Math.max(1, ticks), run));
    }

    private static void tick() {
        now++;
        if (TASKS.isEmpty()) return;
        List<Task> due = new ArrayList<>();
        TASKS.removeIf(t -> {
            if (t.due() > now) return false;
            due.add(t);
            return true;
        });
        for (Task t : due) {
            try {
                t.run().run();
            } catch (RuntimeException e) {
                Mmorpg.LOG.warn("[mmorpg] delayed task failed", e);
            }
        }
    }
}
