package mmorpg.fabric.cosmetic;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mmorpg.core.Json;

/**
 * 장식 목록 (/mmorpg/cosmetics.json — 0.13.0, 서버 · 클라이언트 같이 씀). 칸 (slots) · 항목 (items) — 항목 키 (crown) 가 플레이어 기록 ·
 * 명령어 · 아이템 모델 (rpg:cosmetic_&lt;키&gt;) 에 쓰임. 그림 · 모델은 tools/cosmetic_art.py (무기 스킨 모델은 tools/weapon_models.py).
 */
public final class CosmeticDefs {
    private CosmeticDefs() {}

    public static final String RESOURCE = "/mmorpg/cosmetics.json";
    /** 칸 순서 (장식 창의 탭 순서와 같음) */
    public static final List<String> SLOTS = List.of("weapon", "helmet", "chest", "legs", "boots", "head", "back", "aura", "title");
    /** 갑옷 칸 (숨기기 · 스킨) */
    public static final List<String> ARMOR = List.of("helmet", "chest", "legs", "boots");

    /** 칸 하나 (hide = 숨기기 가능 — 갑옷 칸만, 무기는 숨길 수 없음) */
    public record Slot(String key, String name, boolean hide) {}

    /** 오라: kind (embers · orbit · frost) · 초당 수 · 색들 (0~1) */
    public record Aura(String kind, double rate, List<double[]> colors) {}

    /**
     * 장식 하나. weapon · design = 무기 스킨 (무기 종류 · 디자인), model = 갑옷 스킨 · 머리 · 등 장식 모델 (assets/mmorpg/cosmetic/&lt;model&gt;.json),
     * set = 갑옷 스킨 세트, aura · title = 오라 · 칭호 글, hideHelmet = 머리 장식을 쓴 동안 투구를 그리지 않음 (머리에 얹는 모자 · 왕관)
     */
    public record Def(String id, String slot, String name, int price, List<String> desc, double[] color, String weapon, String design, String model,
            String set, Aura aura, String title, boolean hideHelmet) {
        /** 목록 · 연출 색 (0xRRGGBB) */
        public int rgb() {
            return (to255(color[0]) << 16) | (to255(color[1]) << 8) | to255(color[2]);
        }
    }

    private static Map<String, Slot> slots;
    private static Map<String, Def> defs;

    /** 모든 장식 (키 → 장식, 파일 순서) */
    public static synchronized Map<String, Def> all() {
        if (defs == null) load();
        return Collections.unmodifiableMap(defs);
    }

    /** 칸들 (키 → 칸, SLOTS 순서) */
    public static synchronized Map<String, Slot> slots() {
        if (slots == null) load();
        return Collections.unmodifiableMap(slots);
    }

    public static Def get(String id) {
        return id == null ? null : all().get(id);
    }

    public static Slot slot(String key) {
        return key == null ? null : slots().get(key);
    }

    /** 그 칸의 장식들 (파일 순서). weapon 칸은 type 의 무기 스킨만 (type 이 null 이면 모두) */
    public static List<Def> of(String slot, String type) {
        List<Def> out = new ArrayList<>();
        for (Def d : all().values()) {
            if (!d.slot().equals(slot)) continue;
            if (type != null && "weapon".equals(slot) && !type.equals(d.weapon())) continue;
            out.add(d);
        }
        return out;
    }

    /** 장식 더하기 · 빼기 (시험 — 같은 키면 바꿈) */
    public static synchronized void add(Def d) {
        all();
        defs.put(d.id(), d);
    }

    public static synchronized void remove(String id) {
        all();
        defs.remove(id);
    }

    @SuppressWarnings("unchecked")
    private static void load() {
        Map<String, Object> root;
        try (InputStream in = CosmeticDefs.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException(RESOURCE + " 이 없음");
            root = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Map<String, Slot> s = new LinkedHashMap<>();
        Map<String, Object> rawSlots = root.get("slots") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        for (String key : SLOTS) {
            Map<String, Object> o = rawSlots.get(key) instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
            s.put(key, new Slot(key, str(o.get("name"), key), Boolean.TRUE.equals(o.get("hide"))));
        }
        Map<String, Def> out = new LinkedHashMap<>();
        Map<String, Object> items = root.get("items") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        for (Map.Entry<String, Object> e : items.entrySet()) {
            if (e.getKey().startsWith("_") || !(e.getValue() instanceof Map<?, ?> raw)) continue;
            Map<String, Object> o = (Map<String, Object>) raw;
            String slot = str(o.get("slot"), "");
            if (!s.containsKey(slot)) continue;
            List<String> desc = new ArrayList<>();
            if (o.get("desc") instanceof List<?> l) for (Object x : l) desc.add(String.valueOf(x));
            Aura aura = null;
            if (o.get("aura") instanceof Map<?, ?> a) {
                Map<String, Object> am = (Map<String, Object>) a;
                List<double[]> cols = new ArrayList<>();
                if (am.get("colors") instanceof List<?> cl) for (Object c : cl) cols.add(color(c));
                if (cols.isEmpty()) cols.add(new double[] {1, 1, 1});
                aura = new Aura(str(am.get("kind"), "embers"), am.get("rate") instanceof Number n ? n.doubleValue() : 8, List.copyOf(cols));
            }
            out.put(e.getKey(), new Def(e.getKey(), slot, str(o.get("name"), e.getKey()), o.get("price") instanceof Number n ? Math.max(0, n.intValue()) : 0,
                    List.copyOf(desc), color(o.get("color")), strOrNull(o.get("weapon")), strOrNull(o.get("design")), strOrNull(o.get("model")),
                    strOrNull(o.get("set")), aura, strOrNull(o.get("title")), Boolean.TRUE.equals(o.get("hideHelmet"))));
        }
        slots = s;
        defs = out;
    }

    private static String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    private static String strOrNull(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static double[] color(Object o) {
        if (o instanceof List<?> l && l.size() >= 3) {
            double[] c = new double[3];
            for (int i = 0; i < 3; i++) c[i] = l.get(i) instanceof Number n ? n.doubleValue() : 1;
            return c;
        }
        return new double[] {1, 1, 1};
    }

    private static int to255(double v) {
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }
}
