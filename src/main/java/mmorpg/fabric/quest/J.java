package mmorpg.fabric.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Map;

/**
 * 애드온 객체 { … } 를 그대로 옮겨 적는 JSON 도우미 (메인 · 반복 의뢰 · 왕실 · 전설 퀘스트 정의를 애드온처럼 객체로 만든 뒤
 * Quest.normalizeBuiltin 으로 다듬음). null 값은 넣지 않음 (JS 의 undefined 처럼) — JSON null 은 JsonNull.INSTANCE.
 */
final class J {
    private J() {}

    /** { k: v, … } */
    static JsonObject o(Object... kv) {
        return put(new JsonObject(), kv);
    }

    /** Object.assign(o, { k: v, … }) */
    static JsonObject put(JsonObject o, Object... kv) {
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i + 1] == null) continue;
            o.add((String) kv[i], el(kv[i + 1]));
        }
        return o;
    }

    /** [a, b, …] */
    static JsonArray a(Object... items) {
        JsonArray a = new JsonArray();
        for (Object it : items) a.add(el(it));
        return a;
    }

    static JsonElement el(Object v) {
        if (v == null) return JsonNull.INSTANCE;
        if (v instanceof JsonElement e) return e.deepCopy();
        if (v instanceof String s) return new JsonPrimitive(s);
        if (v instanceof Number n) return new JsonPrimitive(n);
        if (v instanceof Boolean b) return new JsonPrimitive(b);
        if (v instanceof List<?> list) {
            JsonArray a = new JsonArray();
            for (Object it : list) a.add(el(it));
            return a;
        }
        if (v instanceof Object[] arr) return a(arr);
        if (v instanceof Map<?, ?> m) {
            JsonObject o = new JsonObject();
            for (Map.Entry<?, ?> e : m.entrySet()) if (e.getValue() != null) o.add(String.valueOf(e.getKey()), el(e.getValue()));
            return o;
        }
        throw new IllegalArgumentException("json: " + v.getClass());
    }
}
