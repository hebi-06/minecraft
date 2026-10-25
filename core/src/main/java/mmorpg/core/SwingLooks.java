package mmorpg.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 0.41.0 평타 강화 모습 (rules_more SWING_LOOK — 사용자 "평타 강화 스킬은 평타 색이 바뀌고 범위가 더 넓어지는걸로", 고른 것: 룬마다 다르게 · 휘두를 때마다 1번 ·
 * 직업 평타 버프도 포함). 평타 강화 룬 (RUNES.strike 의 fn) · 직업 평타 태세 (스킬 id) 가 걸린 동안 근접 평타의 참격 궤적 색 (glow 빛 · aura 기운) 과
 * 참격 범위 배율 (scale — range 가 있으면 그 칸 아래로는 줄지 않음). 서버 (범위 · 화살 색) 와 클라이언트 (궤적) 가 같이 씀.
 */
public final class SwingLooks {
    private SwingLooks() {}

    /**
     * 모습 하나: key (룬 fn · 직업 스킬 id) · 이름 · 범위 배율 · 최소 칸 (0 = 없음) · 빛 · 기운 (RGB 0 ~ 1) · 무기 종류 (비면 모두)
     */
    public record Look(String key, String name, double scale, double range, double[] glow, double[] aura, List<String> weapons) {
        /** 이 무기 종류에 붙는지 */
        public boolean fits(String weaponType) {
            return weapons.isEmpty() || (weaponType != null && weapons.contains(weaponType));
        }

        /** 기본 범위 base 칸에서의 범위 배율: max(scale, range / base) */
        public double scaleAt(double base) {
            return range > 0 && base > 0 ? Math.max(scale, range / base) : scale;
        }
    }

    private static Map<String, Look> cache;

    private static Obj cfg() {
        return Rules.get().root().objOrNull("SWING_LOOK");
    }

    /** 평타 강화 모습을 쓰는지 (끄면 예전대로 — 색 · 범위 그대로) */
    public static boolean enabled() {
        Obj c = cfg();
        return c != null && c.truthy("enabled");
    }

    private static synchronized Map<String, Look> all() {
        if (cache != null) return cache;
        Map<String, Look> out = new LinkedHashMap<>();
        Obj c = cfg();
        Obj looks = c == null ? null : c.objOrNull("looks");
        if (looks != null) {
            for (String k : looks.keys()) {
                if (k.startsWith("_")) continue;
                Obj o = looks.obj(k);
                List<String> ws = new ArrayList<>();
                if (o.has("weapons")) for (Object x : o.list("weapons")) ws.add(String.valueOf(x));
                out.put(k, new Look(k, o.strOrNull("name") != null ? o.str("name") : k, Math.max(1, o.numOr("scale", 1)), Math.max(0, o.numOr("range", 0)),
                        rgb(o, "glow"), rgb(o, "aura"), Collections.unmodifiableList(ws)));
            }
        }
        cache = Collections.unmodifiableMap(out);
        return cache;
    }

    private static double[] rgb(Obj o, String key) {
        if (!o.has(key)) return null;
        List<Object> l = o.list(key);
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) c[i] = Math.max(0, Math.min(1, ((Number) l.get(i)).doubleValue()));
        return c;
    }

    /** 이 key (룬 fn · 직업 스킬 id) 의 모습 (꺼져 있거나 없으면 null) */
    public static Look of(String key) {
        if (key == null || !enabled()) return null;
        return all().get(key);
    }

    /** 표의 key 전부 (시험) */
    public static Set<String> keys() {
        return all().keySet();
    }

    /** 무기 종류 · 등급의 첫 평타 동작 (연타 1단) 의 참격 범위 거리 — 여러 모습 중 범위가 가장 넓은 것을 고를 때의 기준 (없으면 0) */
    public static double baseRange(String weaponType, int grade) {
        List<String> combo = Motions.combo(weaponType);
        if (combo.isEmpty()) return 0;
        Motions.Area a = Motions.area(combo.get(0), weaponType, grade);
        return a == null ? 0 : a.range();
    }

    /** RGB (0 ~ 1) → 0xRRGGBB (검정이어도 1 — 0 은 "없음") */
    public static int pack(double[] c) {
        if (c == null) return 0;
        int r = (int) Math.round(Math.max(0, Math.min(1, c[0])) * 255);
        int g = (int) Math.round(Math.max(0, Math.min(1, c[1])) * 255);
        int b = (int) Math.round(Math.max(0, Math.min(1, c[2])) * 255);
        int v = (r << 16) | (g << 8) | b;
        return v == 0 ? 1 : v;
    }
}
