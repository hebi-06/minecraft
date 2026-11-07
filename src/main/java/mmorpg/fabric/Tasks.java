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

    /**
     * ticks 틱 뒤 (최소 1) 서버 스레드에서 실행. 0.48.2 지금의 효과 주인 (owner) · 0.50.0 꼬리표 (tag) · 2026-11-07 원소 맥락 (elem) 을 잡아 두었다가 실행할 때
     * 되살림
     */
    public static void later(int ticks, Runnable run) {
        int own = owner;
        String tg = tag;
        Object el = elem;
        TASKS.add(new Task(now + Math.max(1, ticks), own == 0 && tg == null && el == null ? run : () -> {
            int prev = owner;
            String prevTag = tag;
            Object prevEl = elem;
            owner = own;
            tag = tg;
            elem = el;
            try {
                run.run();
            } finally {
                owner = prev;
                tag = prevTag;
                elem = prevEl;
            }
        }));
    }

    /**
     * 2026-11-07 지금 효과의 원소 맥락 (null = 무속성 — 원소 스킬 · 룬을 쓰는 동안 JobSystem · RuneCast 가 정함 (job.ElementSystem.Ctx), later 가 이어 줌):
     * 그 동안 들어간 직업 · 룬 피해가 맞은 적에 원소를 묻힘
     */
    private static Object elem;

    public static Object elem() {
        return elem;
    }

    /** 원소 맥락을 바꾸고 예전 값을 돌려줌 (끝나면 되돌림) */
    public static Object elem(Object e) {
        Object prev = elem;
        elem = e;
        return prev;
    }

    /**
     * 0.50.0 지금 효과의 꼬리표 (null = 없음 — 룬을 쓰는 동안 RuneCast 가 "계열:등급" 으로 정함, later 가 이어 줌): 룬 피해가 들어간 적에 그 계열의 맞은 자리
     * 그림 (RuneBoost.mark) · 아래 등급 룬의 그림 후광
     */
    private static String tag;

    public static String tag() {
        return tag;
    }

    /** 꼬리표를 바꾸고 예전 값을 돌려줌 (끝나면 되돌림) */
    public static String tag(String t) {
        String prev = tag;
        tag = t;
        return prev;
    }

    /**
     * 0.48.2 지금 효과를 내는 사람 (엔티티 번호, 0 = 없음 — 룬을 쓰는 동안 RuneCast 가 정함, later 가 이어 줌): 룬의 참격 · 휘두른 자국 판이 주인의 1인칭에선
     * 원래 파티클로 (RuneKit.arc · waveCard)
     */
    private static int owner;

    public static int owner() {
        return owner;
    }

    /** 주인을 바꾸고 예전 값을 돌려줌 (끝나면 되돌림) */
    public static int owner(int id) {
        int prev = owner;
        owner = id;
        return prev;
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
