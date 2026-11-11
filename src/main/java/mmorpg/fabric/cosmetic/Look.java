package mmorpg.fabric.cosmetic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import mmorpg.core.Json;

/**
 * 한 사람의 장식 모습 (다른 사람에게도 보내는 것 — 가진 목록은 없음). weapon = 무기 종류 → 스킨 키,
 * slots = 칸 (head · back · aura · title) → 장식 키. 없는 칸 = 장식 없음. 바꿀 때마다 새 Look (바뀌지 않음).
 * JSON: {"weapon": {"sword": "sakura_sword"}, "back": "angel_wings", …}. 2026-11-11 갑옷 칸 · 숨기기 ("hide") 를 뺌 — 예전 기록의 그 값은 sanitize 가 버림.
 */
public record Look(Map<String, String> weapon, Map<String, String> slots) {
    public static final Look EMPTY = new Look(Map.of(), Map.of());

    public Look {
        weapon = Collections.unmodifiableMap(new LinkedHashMap<>(weapon));
        slots = Collections.unmodifiableMap(new LinkedHashMap<>(slots));
    }

    /** 그 무기 종류의 스킨 키 (없으면 null) */
    public String weaponSkin(String type) {
        return type == null ? null : weapon.get(type);
    }

    /** 칸의 값 (장식 키 · 없으면 null) */
    public String get(String slot) {
        return slots.get(slot);
    }

    /** 칸의 장식 (없음 · 모르는 키면 null) */
    public CosmeticDefs.Def def(String slot) {
        String v = slots.get(slot);
        return v == null ? null : CosmeticDefs.get(v);
    }

    public boolean isEmpty() {
        return weapon.isEmpty() && slots.isEmpty();
    }

    /** 칸 하나를 바꾼 새 모습 (value null = 원래대로) */
    public Look with(String slot, String value) {
        Map<String, String> s = new LinkedHashMap<>(slots);
        if (value == null) s.remove(slot);
        else s.put(slot, value);
        return new Look(weapon, s);
    }

    /** 무기 종류 하나의 스킨을 바꾼 새 모습 (id null = 기본 모습) */
    public Look withWeapon(String type, String id) {
        Map<String, String> w = new LinkedHashMap<>(weapon);
        if (id == null) w.remove(type);
        else w.put(type, id);
        return new Look(w, slots);
    }

    /** 맞지 않는 값을 뺀 모습: 모르는 칸 (예전 갑옷 칸) · 모르는 키 (예전 "hide") · 칸이 다른 키 · 가지지 않은 키 (owned 가 거짓) */
    public Look sanitize(Predicate<String> owned) {
        Map<String, String> w = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : weapon.entrySet()) {
            CosmeticDefs.Def d = CosmeticDefs.get(e.getValue());
            if (d != null && "weapon".equals(d.slot()) && e.getKey().equals(d.weapon()) && owned.test(d.id())) w.put(e.getKey(), d.id());
        }
        Map<String, String> s = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : slots.entrySet()) {
            CosmeticDefs.Slot slot = CosmeticDefs.slot(e.getKey());
            if (slot == null || "weapon".equals(slot.key())) continue;
            CosmeticDefs.Def d = CosmeticDefs.get(e.getValue());
            if (d != null && d.slot().equals(slot.key()) && owned.test(d.id())) s.put(slot.key(), d.id());
        }
        return new Look(w, s);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        if (!weapon.isEmpty()) m.put("weapon", new LinkedHashMap<>(weapon));
        for (String slot : CosmeticDefs.SLOTS) {
            String v = slots.get(slot);
            if (v != null) m.put(slot, v);
        }
        return m;
    }

    public String json() {
        return Json.stringify(toMap());
    }

    /** JSON 지도에서 (모르는 칸 · 잘못된 값은 그대로 두고 — 쓰는 쪽이 sanitize) */
    public static Look fromMap(Map<?, ?> m) {
        if (m == null) return EMPTY;
        Map<String, String> w = new LinkedHashMap<>();
        if (m.get("weapon") instanceof Map<?, ?> wm) {
            for (Map.Entry<?, ?> e : wm.entrySet()) if (e.getValue() != null) w.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
        }
        Map<String, String> s = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            String k = String.valueOf(e.getKey());
            if (k.equals("weapon") || e.getValue() == null || e.getValue() instanceof Map<?, ?>) continue;
            s.put(k, String.valueOf(e.getValue()));
        }
        return new Look(w, s);
    }

    public static Look parse(String json) {
        if (json == null || json.isBlank()) return EMPTY;
        try {
            return fromMap(Json.parseObject(json));
        } catch (RuntimeException e) {
            return EMPTY;
        }
    }
}
