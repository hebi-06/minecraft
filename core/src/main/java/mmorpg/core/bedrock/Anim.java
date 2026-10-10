package mmorpg.core.bedrock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mmorpg.core.Molang;

/**
 * 베드락 동작 하나 (animation.json 의 animations 한 줄): 뼈마다 회전 · 위치 · 크기 채널 (고정 식 [x, y, z] 또는 키프레임 {초: 값}).
 * 키프레임 사이는 곧게 (linear — 애드온 동작은 모두 이것), 첫 키프레임 전은 첫 값 · 마지막 뒤는 마지막 값, 값의 식은 그때그때 계산.
 * 반복: loop true = 되풀이, "hold_on_last_frame" = 끝 자세 유지, 없음 = 한 번 (끝나면 더하지 않음 — 뼈는 다른 동작 · 기본 자세로).
 * 길이: animation_length, 없으면 마지막 키프레임 시각.
 */
public final class Anim {
    public enum Loop { ONCE, LOOP, HOLD }

    public final String name;
    public final Loop loop;
    /** 초 (0 = 키프레임 없음 — 식만 있는 동작) */
    public final double length;
    /** 소문자 뼈 이름 → 채널들 */
    public final Map<String, Track> bones;

    /** 뼈 하나의 채널 (없으면 null) */
    public static final class Track {
        public final Channel rotation;
        public final Channel position;
        public final Channel scale;

        Track(Channel rotation, Channel position, Channel scale) {
            this.rotation = rotation;
            this.position = position;
            this.scale = scale;
        }
    }

    /** 채널: 키프레임 시각 (정렬) 과 값 (키프레임마다 앞 · 뒤 [x, y, z] 식) — 고정 값이면 times 가 비어 있고 post[0] 만 */
    public static final class Channel {
        final double[] times;
        final Molang.Expr[][] pre;
        final Molang.Expr[][] post;

        Channel(double[] times, Molang.Expr[][] pre, Molang.Expr[][] post) {
            this.times = times;
            this.pre = pre;
            this.post = post;
        }

        /** 마지막 키프레임 시각 (고정 값이면 0) */
        public double lastTime() {
            return times.length == 0 ? 0 : times[times.length - 1];
        }

        /** t 초의 값 → out[0..2] */
        public void sample(double t, double[] v, double[] out) {
            if (times.length == 0) {
                eval(post[0], v, out);
                return;
            }
            int n = times.length;
            if (t <= times[0]) {
                eval(pre[0], v, out);
                return;
            }
            if (t >= times[n - 1]) {
                eval(post[n - 1], v, out);
                return;
            }
            int i = 0;
            while (i + 1 < n && times[i + 1] <= t) i++;
            double t0 = times[i];
            double t1 = times[i + 1];
            double u = t1 > t0 ? (t - t0) / (t1 - t0) : 1;
            double[] a = new double[3];
            double[] b = new double[3];
            eval(post[i], v, a);
            eval(pre[i + 1], v, b);
            for (int k = 0; k < 3; k++) out[k] = a[k] + (b[k] - a[k]) * u;
        }

        private static void eval(Molang.Expr[] e, double[] v, double[] out) {
            for (int k = 0; k < 3; k++) out[k] = e[k].eval(v);
        }
    }

    private Anim(String name, Loop loop, double length, Map<String, Track> bones) {
        this.name = name;
        this.loop = loop;
        this.length = length;
        this.bones = Collections.unmodifiableMap(bones);
    }

    /** 동작 JSON 하나 → 동작 (식은 vars 의 칸으로) */
    @SuppressWarnings("unchecked")
    public static Anim parse(String name, Map<String, Object> a, Molang.Vars vars) {
        Object lp = a.get("loop");
        Loop loop = Boolean.TRUE.equals(lp) ? Loop.LOOP : "hold_on_last_frame".equals(lp) ? Loop.HOLD : Loop.ONCE;
        Map<String, Track> bones = new LinkedHashMap<>();
        double last = 0;
        if (a.get("bones") instanceof Map<?, ?> bm) {
            for (Map.Entry<?, ?> e : bm.entrySet()) {
                if (!(e.getValue() instanceof Map<?, ?>)) continue;
                Map<String, Object> ch = (Map<String, Object>) e.getValue();
                Channel r = channel(ch.get("rotation"), vars, 0);
                Channel p = channel(ch.get("position"), vars, 0);
                Channel s = channel(ch.get("scale"), vars, 1);
                for (Channel c : new Channel[] {r, p, s}) if (c != null) last = Math.max(last, c.lastTime());
                bones.put(String.valueOf(e.getKey()).toLowerCase(Locale.ROOT), new Track(r, p, s));
            }
        }
        double length = a.get("animation_length") instanceof Number n ? n.doubleValue() : last;
        return new Anim(name, loop, length, bones);
    }

    /** 이 시각에 동작이 끝났는지 (한 번 끝까지 돎) */
    public boolean finished(double t) {
        return t >= length;
    }

    /** 시작 뒤 t 초 → 채널에 넣을 시각 (반복이면 되풀이, 유지면 끝에서 멈춤), 한 번짜리가 끝났으면 -1 (더하지 않음) */
    public double localTime(double t) {
        if (t < 0) t = 0;
        switch (loop) {
            case LOOP:
                return length > 0 ? t % length : t;
            case HOLD:
                return Math.min(t, length);
            default:
                return length > 0 && t > length ? -1 : t;
        }
    }

    // ------------------------------------------------------------ 채널 읽기

    @SuppressWarnings("unchecked")
    private static Channel channel(Object o, Molang.Vars vars, double fallback) {
        if (o == null) return null;
        if (o instanceof Map<?, ?> m) {
            List<double[]> order = new ArrayList<>();
            List<Object> vals = new ArrayList<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                double t;
                try {
                    t = Double.parseDouble(String.valueOf(e.getKey()));
                } catch (NumberFormatException ex) {
                    continue;
                }
                order.add(new double[] {t, vals.size()});
                vals.add(e.getValue());
            }
            order.sort((x, y) -> Double.compare(x[0], y[0]));
            int n = order.size();
            double[] times = new double[n];
            Molang.Expr[][] pre = new Molang.Expr[n][];
            Molang.Expr[][] post = new Molang.Expr[n][];
            for (int i = 0; i < n; i++) {
                times[i] = order.get(i)[0];
                Object v = vals.get((int) order.get(i)[1]);
                if (v instanceof Map<?, ?> pp) {
                    Map<String, Object> kv = (Map<String, Object>) pp;
                    Object po = kv.containsKey("post") ? kv.get("post") : kv.get("pre");
                    Object pr = kv.containsKey("pre") ? kv.get("pre") : kv.get("post");
                    post[i] = vec(po, vars, fallback);
                    pre[i] = vec(pr, vars, fallback);
                } else {
                    post[i] = vec(v, vars, fallback);
                    pre[i] = post[i];
                }
            }
            if (n == 0) return null;
            return new Channel(times, pre, post);
        }
        Molang.Expr[] v = vec(o, vars, fallback);
        return new Channel(new double[0], new Molang.Expr[][] {v}, new Molang.Expr[][] {v});
    }

    /** [x, y, z] (숫자 · 식) 또는 하나 (셋 모두 같은 값) → 식 셋 */
    private static Molang.Expr[] vec(Object o, Molang.Vars vars, double fallback) {
        Molang.Expr[] out = new Molang.Expr[3];
        if (o instanceof List<?> l) {
            for (int k = 0; k < 3; k++) out[k] = k < l.size() ? Molang.of(l.get(k), vars) : Molang.constant(fallback);
        } else if (o != null) {
            Molang.Expr e = Molang.of(o, vars);
            out[0] = out[1] = out[2] = e;
        } else {
            out[0] = out[1] = out[2] = Molang.constant(fallback);
        }
        return out;
    }
}
