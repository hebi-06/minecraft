package mmorpg.fabric.mob;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mmorpg.core.Json;

/**
 * 애드온 엔티티 수치 (행동 팩 RPG_BP/entities/rpg_monsters · rpg_bosses — tools/bedrock_entities.py 가 /mmorpg/entities.json 으로 그대로 옮김).
 * 엔티티 ID (rpg:troll) 마다 체력 · 이동 · 충돌 상자 · 크기 · 중력 · 무리 · 집 반경 · 보스 막대 · 속성 (setProperty) · 효과 면역 · 피해 면역 · 행동.
 * 서버와 클라이언트가 같이 씀 (클라이언트는 속성 이름 · 기본값을 그리기 식에).
 */
public final class MobData {
    private MobData() {}

    public static final String RESOURCE = "/mmorpg/entities.json";

    /** 속성 하나 (bool · int · float · enum — enum 은 값 목록의 번호) */
    public record Prop(String id, String type, double def, double min, double max) {}

    /** 엔티티 종류 하나 */
    public record Def(String id, String group, double health, double movement, float width, float height, double scale, boolean gravity,
            boolean collision, boolean pushable, double knockback, double follow, boolean fireImmune, List<String> families, double homeRadius,
            String homeType, String bossName, double bossRange, Map<String, Prop> props, Set<String> effectImmune, Set<String> noDamage,
            boolean breathesWater, boolean persistent, boolean nameAlways, Map<String, Double> groupMovement, Map<String, List<List<String>>> events,
            Map<String, Map<String, Object>> behaviors) {

        /** 이 무리인지 (minecraft:type_family) */
        public boolean family(String f) {
            return families.contains(f);
        }

        public boolean monster() {
            return "monsters".equals(group);
        }

        /** 보스 (rpg_boss 무리 — 분신 · 소품은 아님) */
        public boolean boss() {
            return family("rpg_boss");
        }

        public boolean prop() {
            return family("rpg_boss_prop");
        }

        /** 이 종류의 속성 이름 (정의 순서) */
        public List<String> propNames() {
            return new ArrayList<>(props.keySet());
        }
    }

    private static Map<String, Def> defs;

    /** 모든 종류 (ID 순서는 파일 그대로 — 몬스터 다음 보스) */
    public static synchronized Map<String, Def> all() {
        if (defs == null) defs = load();
        return defs;
    }

    /** ID (rpg:troll) → 종류 (없으면 null) */
    public static Def get(String id) {
        return all().get(id);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Def> load() {
        Map<String, Object> root;
        try (InputStream in = MobData.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException(RESOURCE + " 이 없음 (tools/bedrock_entities.py)");
            root = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Map<String, Def> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : root.entrySet()) {
            Map<String, Object> d = (Map<String, Object>) e.getValue();
            Map<String, Object> c = map(d.get("components"));
            Map<String, Object> phys = map(c.get("minecraft:physics"));
            Map<String, Object> box = map(c.get("minecraft:collision_box"));
            Map<String, Object> home = map(c.get("minecraft:home"));
            Map<String, Object> boss = map(c.get("minecraft:boss"));
            List<String> families = new ArrayList<>();
            for (Object f : list(map(c.get("minecraft:type_family")).get("family"))) families.add(String.valueOf(f));
            Map<String, Prop> props = new LinkedHashMap<>();
            for (Map.Entry<String, Object> pe : map(d.get("properties")).entrySet()) {
                Map<String, Object> p = map(pe.getValue());
                String type = String.valueOf(p.getOrDefault("type", "float"));
                Object dv = p.get("default");
                double def = dv instanceof Boolean b ? (b ? 1 : 0) : dv instanceof Number n ? n.doubleValue() : 0;
                if ("enum".equals(type) && dv instanceof String s) def = list(p.get("values")).indexOf(s);
                List<Object> range = list(p.get("range"));
                double min = range.size() == 2 ? num(range.get(0), 0) : Double.NEGATIVE_INFINITY;
                double max = range.size() == 2 ? num(range.get(1), 0) : Double.POSITIVE_INFINITY;
                props.put(pe.getKey(), new Prop(pe.getKey(), type, def, min, max));
            }
            Set<String> effects = new LinkedHashSet<>();
            for (Object f : list(map(c.get("minecraft:mob_effect_immunity")).get("mob_effects"))) effects.add(String.valueOf(f));
            Set<String> noDamage = new LinkedHashSet<>();
            Object triggers = map(c.get("minecraft:damage_sensor")).get("triggers");
            for (Object t : triggers instanceof List<?> l ? l : triggers == null ? List.of() : List.of(triggers)) {
                Map<String, Object> tm = map(t);
                if ("no".equals(String.valueOf(tm.get("deals_damage"))) || Boolean.FALSE.equals(tm.get("deals_damage"))) noDamage.add(String.valueOf(tm.get("cause")));
            }
            Map<String, Double> groupMove = new LinkedHashMap<>();
            for (Map.Entry<String, Object> g : map(d.get("component_groups")).entrySet()) {
                Map<String, Object> mv = map(map(g.getValue()).get("minecraft:movement"));
                if (mv.containsKey("value")) groupMove.put(g.getKey(), num(mv.get("value"), 0));
            }
            Map<String, List<List<String>>> events = new LinkedHashMap<>();
            for (Map.Entry<String, Object> ev : map(d.get("events")).entrySet()) {
                Map<String, Object> em = map(ev.getValue());
                List<String> add = new ArrayList<>();
                List<String> remove = new ArrayList<>();
                for (Object o : list(map(em.get("add")).get("component_groups"))) add.add(String.valueOf(o));
                for (Object o : list(map(em.get("remove")).get("component_groups"))) remove.add(String.valueOf(o));
                events.put(ev.getKey(), List.of(add, remove));
            }
            Map<String, Map<String, Object>> behaviors = new LinkedHashMap<>();
            for (Map.Entry<String, Object> ce : c.entrySet()) {
                if (ce.getKey().startsWith("minecraft:behavior.")) behaviors.put(ce.getKey().substring("minecraft:behavior.".length()), map(ce.getValue()));
            }
            Map<String, Object> breath = map(c.get("minecraft:breathable"));
            out.put(e.getKey(), new Def(e.getKey(), String.valueOf(d.get("group")),
                    num(map(c.get("minecraft:health")).get("value"), 20),
                    num(map(c.get("minecraft:movement")).get("value"), 0),
                    (float) num(box.get("width"), 0.6), (float) num(box.get("height"), 1.8),
                    num(map(c.get("minecraft:scale")).get("value"), 1),
                    !Boolean.FALSE.equals(phys.get("has_gravity")), !Boolean.FALSE.equals(phys.get("has_collision")),
                    !Boolean.FALSE.equals(map(c.get("minecraft:pushable")).get("is_pushable")),
                    num(map(c.get("minecraft:knockback_resistance")).get("value"), 0),
                    num(map(c.get("minecraft:follow_range")).get("value"), 16),
                    c.containsKey("minecraft:fire_immune"), Collections.unmodifiableList(families),
                    num(home.get("restriction_radius"), 0), home.containsKey("restriction_type") ? String.valueOf(home.get("restriction_type")) : null,
                    boss.containsKey("name") ? String.valueOf(boss.get("name")) : null, num(boss.get("hud_range"), 0),
                    Collections.unmodifiableMap(props), Collections.unmodifiableSet(effects), Collections.unmodifiableSet(noDamage),
                    Boolean.TRUE.equals(breath.get("breathes_water")), c.containsKey("minecraft:persistent"),
                    Boolean.TRUE.equals(map(c.get("minecraft:nameable")).get("always_show")), Collections.unmodifiableMap(groupMove),
                    Collections.unmodifiableMap(events), Collections.unmodifiableMap(behaviors)));
        }
        return Collections.unmodifiableMap(out);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object o) {
        return o instanceof List<?> l ? (List<Object>) l : List.of();
    }

    static double num(Object o, double fallback) {
        return o instanceof Number n ? n.doubleValue() : fallback;
    }
}
