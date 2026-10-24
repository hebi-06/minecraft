package mmorpg.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 0.38.0 동작 규칙 (rules_more.json MOTION — 서버 · 클라이언트가 같이 씀): 무기 종류마다 평타 동작 차례 (연타 단계), 회전 베기 둘레 판정,
 * 쉬프트 두 번 구르기 (거리 · 틱 · 재사용 대기 · 두 번 누름 간격), 스킬 → 동작 (무기 스킬 id · 직업 스킬 id · rune:룬 fn).
 * 동작의 모양 (열쇠 자세) 은 클라이언트 assets/mmorpg/motion/poses.json.
 */
public final class Motions {
    private Motions() {}

    /** 동작 이름 전부 (poses.json 과 같아야 함 — 시험이 확인) */
    public static final Set<String> KNOWN = Set.of("slash_h", "slash_v", "spin", "dagger_x", "chop", "thrust", "bash", "smite", "skill_slash", "skill_flurry",
            "slam", "leap_slam", "dash", "throw", "throw_weapon", "cast", "cast_up", "guard", "shoot", "shoot_up", "spin2", "roll");

    private static Obj cfg() {
        return Rules.get().obj("MOTION");
    }

    /** 무기 종류의 평타 동작 차례 (없으면 빈 목록 — 바닐라 휘두르기) */
    public static List<String> combo(String weaponType) {
        Obj c = cfg().objOrNull("combo");
        if (c == null || weaponType == null || !c.has(weaponType)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object o : c.list(weaponType)) out.add(String.valueOf(o));
        return Collections.unmodifiableList(out);
    }

    /** 다음 평타가 이어지는 틱 수 */
    public static int comboWindow() {
        return (int) cfg().numOr("comboWindow", 24);
    }

    /**
     * 다음 평타의 연타 단계 (1 ~ size): 앞 평타 (단계 lastStep · 틱 lastTick) 에서 comboWindow 틱 안이면 다음 단계 (끝이면 1), 아니면 1.
     * lastStep 0 = 앞 평타 없음.
     */
    public static int nextStep(int lastStep, long lastTick, long now, int size) {
        if (size <= 0) return 0;
        if (lastStep <= 0 || now - lastTick > comboWindow() || now < lastTick) return 1;
        return lastStep % size + 1;
    }

    /** 회전 베기가 둘레 적에게 주는 몫 (맞힌 피해 ×) */
    public static double spinRatio() {
        return cfg().obj("spin").numOr("ratio", 0.4);
    }

    /** 회전 베기 둘레 (칸) */
    public static double spinRange() {
        return cfg().obj("spin").numOr("range", 3);
    }

    private static Obj roll() {
        return cfg().obj("roll");
    }

    public static int rollTicks() {
        return (int) roll().numOr("ticks", 10);
    }

    public static double rollDistance() {
        return roll().numOr("distance", 4);
    }

    public static int rollCooldown() {
        return (int) roll().numOr("cooldown", 20);
    }

    /** 쉬프트를 두 번 누른 것으로 보는 간격 (틱) */
    public static int doubleTap() {
        return (int) roll().numOr("doubleTap", 6);
    }

    /**
     * 구르기 틱마다 빠르기 (칸/틱 — 합 = rollDistance): 처음이 빠르고 끝으로 갈수록 느려짐 ((1 - i / n)^0.6 꼴).
     */
    public static double[] rollSpeeds() {
        int n = Math.max(1, rollTicks());
        double[] v = new double[n];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            v[i] = Math.pow(1 - (i + 0.5) / n, 0.6);
            sum += v[i];
        }
        double k = rollDistance() / sum;
        for (int i = 0; i < n; i++) v[i] *= k;
        return v;
    }

    /** 스킬 → 동작 (key = 무기 스킬 id · 직업 스킬 id · "rune:" + 룬 fn). 없으면 null */
    public static String skillMotion(String key) {
        Obj s = cfg().objOrNull("skills");
        if (s == null || key == null) return null;
        String m = s.strOrNull(key);
        return m != null && KNOWN.contains(m) ? m : null;
    }

    /** 스킬 표의 열쇠 전부 (시험) */
    public static Set<String> skillKeys() {
        Obj s = cfg().objOrNull("skills");
        return s == null ? Set.of() : s.keys();
    }
}
