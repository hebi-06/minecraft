package mmorpg.fabric.mount;

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
 * 탈것 목록 (/mmorpg/mounts.json — 6단계, 서버 · 클라이언트 같이 씀). 새 탈것 = mounts.json 에 한 항목 + 탈것 엔티티 (tools/bedrock_entities.py) +
 * 아이템 그림 (tools/mount_items.py). 키 (dire_wolf) 가 플레이어 데이터 · 명령어 · 아이템 이름에 쓰임.
 * <ul>
 * <li>name: 보이는 이름 · entity: 탈것 엔티티 ID (rpg:dire_wolf_mount) · item: 탈것 아이템 ID (쓰면 내 탈것 목록에 들어감)</li>
 * <li>starter: 처음부터 가진 탈것 (애드온처럼 누구나 다이어울프) · color: 연출 색 · particle: 부를 때 · 사라질 때 흩날리는 바닐라 파티클 (베드락 이름)</li>
 * <li>sounds.summon · sounds.gone: [베드락 소리, 높이, 크기] 목록 · desc: 아이템 · 고르기 창 설명 줄 · seatLift: 탄 사람 높이 보정 (없으면 기본)</li>
 * </ul>
 */
public final class MountDefs {
    private MountDefs() {}

    public static final String RESOURCE = "/mmorpg/mounts.json";

    /** 소리 하나 (베드락 이름 · 높이 · 크기) */
    public record Sound(String name, double pitch, double volume) {}

    /** 탈것 하나 */
    public record Def(String key, String name, String entity, String item, boolean starter, double[] color, String particle, List<Sound> summon,
            List<Sound> gone, List<String> desc, Double seatLift) {
        /** 아이템 ID 의 경로 (rpg:mount_dire_wolf → mount_dire_wolf, 없으면 "") */
        public String itemPath() {
            if (item == null || item.isEmpty()) return "";
            int c = item.indexOf(':');
            return c < 0 ? item : item.substring(c + 1);
        }
    }

    private static Map<String, Def> defs;

    /** 모든 탈것 (키 → 탈것, 파일 순서) */
    public static synchronized Map<String, Def> all() {
        if (defs == null) defs = load();
        return Collections.unmodifiableMap(defs);
    }

    /** 키의 탈것 (없으면 null) */
    public static Def get(String key) {
        return key == null ? null : all().get(key);
    }

    /** 엔티티 ID 의 탈것 (첫 번째 — 없으면 null) */
    public static Def byEntity(String entityId) {
        for (Def d : all().values()) if (d.entity().equals(entityId)) return d;
        return null;
    }

    /** 아이템 ID 의 탈것 (없으면 null) */
    public static Def byItem(String itemId) {
        for (Def d : all().values()) if (d.item() != null && d.item().equals(itemId)) return d;
        return null;
    }

    /** 처음부터 가진 탈것 키들 */
    public static List<String> starters() {
        List<String> out = new ArrayList<>();
        for (Def d : all().values()) if (d.starter()) out.add(d.key());
        return out;
    }

    /** 탈것 더하기 (시험 · 다른 모드 — 같은 키면 바꿈. 아이템 · 엔티티는 따로 등록되어 있어야 함) */
    public static synchronized void add(Def d) {
        all();
        defs.put(d.key(), d);
    }

    /** 더한 탈것 빼기 (시험 뒤) */
    public static synchronized void remove(String key) {
        all();
        defs.remove(key);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Def> load() {
        Map<String, Object> root;
        try (InputStream in = MountDefs.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException(RESOURCE + " 이 없음");
            root = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Map<String, Def> out = new LinkedHashMap<>();
        Object ms = root.get("mounts");
        if (!(ms instanceof Map<?, ?> mounts)) return out;
        for (Map.Entry<?, ?> e : mounts.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?> raw)) continue;
            Map<String, Object> m = (Map<String, Object>) raw;
            String key = String.valueOf(e.getKey());
            Map<String, Object> sounds = m.get("sounds") instanceof Map<?, ?> s ? (Map<String, Object>) s : Map.of();
            List<String> desc = new ArrayList<>();
            if (m.get("desc") instanceof List<?> l) for (Object o : l) desc.add(String.valueOf(o));
            out.put(key, new Def(key, str(m.get("name"), key), str(m.get("entity"), ""), str(m.get("item"), ""), Boolean.TRUE.equals(m.get("starter")),
                    color(m.get("color")), str(m.get("particle"), ""), sounds(sounds.get("summon")), sounds(sounds.get("gone")), List.copyOf(desc),
                    m.get("seatLift") instanceof Number n ? n.doubleValue() : null));
        }
        return out;
    }

    private static String str(Object o, String def) {
        return o == null ? def : String.valueOf(o);
    }

    private static double[] color(Object o) {
        if (o instanceof List<?> l && l.size() >= 3) {
            double[] c = new double[3];
            for (int i = 0; i < 3; i++) c[i] = l.get(i) instanceof Number n ? n.doubleValue() : 1;
            return c;
        }
        return new double[] {1, 1, 1};
    }

    private static List<Sound> sounds(Object o) {
        List<Sound> out = new ArrayList<>();
        if (!(o instanceof List<?> l)) return out;
        for (Object x : l) {
            if (!(x instanceof List<?> s) || s.isEmpty()) continue;
            double pitch = s.size() > 1 && s.get(1) instanceof Number n ? n.doubleValue() : 1;
            double vol = s.size() > 2 && s.get(2) instanceof Number n ? n.doubleValue() : 1;
            out.add(new Sound(String.valueOf(s.get(0)), pitch, vol));
        }
        return List.copyOf(out);
    }
}
