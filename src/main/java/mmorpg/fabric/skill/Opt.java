package mmorpg.fabric.skill;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

/**
 * 자바스크립트 { … } 선택값 묶음 (스킬 · 룬 코드를 애드온과 같은 모양으로 옮기려고): 없는 값은 기본값 (JS 의 o.x ?? 기본).
 * 값: 숫자 (Number), 방향 (Vec3), 색 (double[]), 참거짓 (Boolean), 그 밖의 것.
 */
public final class Opt {
    private final Map<String, Object> m;

    private Opt(Map<String, Object> m) {
        this.m = m;
    }

    /** Opt.of("size", 0.5, "dir", v, …) */
    public static Opt of(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) if (kv[i + 1] != null) m.put((String) kv[i], kv[i + 1]);
        return new Opt(m);
    }

    public static Opt empty() {
        return new Opt(new HashMap<>());
    }

    /** { ...this, k: v, … } */
    public Opt with(Object... kv) {
        Map<String, Object> n = new HashMap<>(m);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i + 1] == null) n.remove((String) kv[i]);
            else n.put((String) kv[i], kv[i + 1]);
        }
        return new Opt(n);
    }

    public boolean has(String k) {
        return m.containsKey(k);
    }

    /** 들어 있는 이름들 */
    public java.util.Set<String> keys() {
        return m.keySet();
    }

    public Object get(String k) {
        return m.get(k);
    }

    public double num(String k, double def) {
        return m.get(k) instanceof Number n ? n.doubleValue() : def;
    }

    /** 없으면 null */
    public Double numOrNull(String k) {
        return m.get(k) instanceof Number n ? n.doubleValue() : null;
    }

    public Vec3 vec(String k, Vec3 def) {
        return m.get(k) instanceof Vec3 v ? v : def;
    }

    public double[] color(String k, double[] def) {
        return m.get(k) instanceof double[] c ? c : def;
    }

    public boolean bool(String k, boolean def) {
        Object v = m.get(k);
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0;
        return v != null || def;
    }

    /** JS 참 (값이 있고 false · 0 이 아님) */
    public boolean truthy(String k) {
        Object v = m.get(k);
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0;
        return true;
    }

    public String str(String k, String def) {
        return m.get(k) instanceof String s ? s : def;
    }
}
