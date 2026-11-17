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
        Object pt = paint;
        TASKS.add(new Task(now + Math.max(1, ticks), own == 0 && tg == null && el == null && pt == null ? run : () -> {
            int prev = owner;
            String prevTag = tag;
            Object prevEl = elem;
            Object prevPt = paint;
            owner = own;
            tag = tg;
            elem = el;
            paint = pt;
            try {
                run.run();
            } finally {
                owner = prev;
                tag = prevTag;
                elem = prevEl;
                paint = prevPt;
            }
        }));
    }

    /**
     * 2026-11-17 지금의 그림 맥락 (주인 owner · 꼬리표 tag · 칠할 묶음 paint) 을 묶은 run — 연출 시간표 (Vfx.Fx.on · each) 가 나중에 불러도 같은 맥락으로 그림.
     * 원소 맥락 (elem — 맞은 적에 원소를 묻힘) 은 놀이에 닿으므로 묶지 않음. CI 43989923127: 시간표 안에서 부른 예전 그림이 맥락 없이 (@-) 남았음 (검사 초월의 칼빛 불티 28 등)
     */
    public static Runnable bind(Runnable run) {
        int own = owner;
        String tg = tag;
        Object pt = paint;
        if (own == 0 && tg == null && pt == null) return run;
        return () -> {
            int prev = owner;
            String prevTag = tag;
            Object prevPt = paint;
            owner = own;
            tag = tg;
            paint = pt;
            try {
                run.run();
            } finally {
                owner = prev;
                tag = prevTag;
                paint = prevPt;
            }
        };
    }

    /** bind 의 나이를 받는 꼴 (Vfx.Fx.each) */
    public static java.util.function.IntConsumer bind(java.util.function.IntConsumer fn) {
        int own = owner;
        String tg = tag;
        Object pt = paint;
        if (own == 0 && tg == null && pt == null) return fn;
        return t -> {
            int prev = owner;
            String prevTag = tag;
            Object prevPt = paint;
            owner = own;
            tag = tg;
            paint = pt;
            try {
                fn.accept(t);
            } finally {
                owner = prev;
                tag = prevTag;
                paint = prevPt;
            }
        };
    }

    /**
     * 2026-11-16 지금 효과를 칠할 직업 묶음 (null = 없음 — 직업 스킬을 쓰는 동안 · 태세 연출 동안 JobSkills · JobTraits 가 정함 (job.PhantomFx.Lib), later 가 이어 줌):
     * 칠한 빛 배열이 아닌 빛 (원소 빛 · 속빛 · 겉빛) 으로 부른 예전 불티 · 공용 룬 그림도 그 직업 묶음으로 (위르드식 남은 예전 그림)
     */
    private static Object paint;

    public static Object paint() {
        return paint;
    }

    /** 칠할 묶음을 바꾸고 예전 값을 돌려줌 (끝나면 되돌림) */
    public static Object paint(Object p) {
        Object prev = paint;
        paint = p;
        return prev;
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
