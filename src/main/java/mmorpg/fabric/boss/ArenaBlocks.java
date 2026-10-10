package mmorpg.fabric.boss;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 베드락 블록 이름 · 상태 → 자바 블록 상태 (arena.js permOf 의 자바 판 — 신전 설계 {@link ArenaThemes} 는 애드온의 베드락 이름을 그대로 씀).
 * <ul>
 * <li>이름이 다른 블록: deadbush → dead_bush, grass_path → dirt_path, dirt_with_roots → rooted_dirt, waterlily → lily_pad, lit_pumpkin → jack_o_lantern,
 * magma → magma_block, nether_brick → nether_bricks, red_nether_brick → red_nether_bricks, snow (눈 블록) → snow_block, snow_layer → snow (눈 층)</li>
 * <li>상태: pillar_axis → axis, minecraft:cardinal_direction · minecraft:block_face → facing, 랜턴 hanging, 뾰족한 점적석 hanging → vertical_direction
 * (false = up) · dripstone_thickness → thickness, 양초 candles (0~3 → 1~4) · lit, 모닥불 extinguished → lit 반대, 바다 피클 cluster_count (0~3 → 1~4) ·
 * dead_bit → waterlogged 반대, 눈 층 height (0~7 → layers 1~8), 구리 전구 powered_bit → powered, 창백한 이끼 tip, 울타리 minecraft:connection_* → 방향,
 * 담장 wall_connection_type_* (none · short · tall → none · low · tall) · wall_post_bit → up</li>
 * <li>자바에 없는 상태는 버림: 산호 부채 방향 (자바 바닥 부채는 방향 없음), 눈 층 covered_bit, 해골 facing_direction 1 (바닥 해골 = 자바 기본),
 * 호박 방향 (자바 호박은 방향 없음), 매끄러운 석영 축</li>
 * <li>모든 블록은 물에 잠기지 않게 (waterlogged = false — 산호 부채 · 바다 피클의 자바 기본은 잠김)</li>
 * </ul>
 * 대응이 없는 블록 이름은 로그로 한 번 알리고 돌 (애드온 permOf 와 같음). 이음 (울타리 · 담장) 은 설계가 이웃으로 셈한 것을 그대로 (planOps).
 */
public final class ArenaBlocks {
    private ArenaBlocks() {}

    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-arena");

    /** 베드락 이름 → 자바 이름 (이름이 다른 것만, minecraft: 뺀 것) */
    private static final Map<String, String> RENAME = Map.ofEntries(
            Map.entry("deadbush", "dead_bush"),
            Map.entry("grass_path", "dirt_path"),
            Map.entry("dirt_with_roots", "rooted_dirt"),
            Map.entry("waterlily", "lily_pad"),
            Map.entry("lit_pumpkin", "jack_o_lantern"),
            Map.entry("magma", "magma_block"),
            Map.entry("nether_brick", "nether_bricks"),
            Map.entry("red_nether_brick", "red_nether_bricks"),
            Map.entry("snow", "snow_block"),
            Map.entry("snow_layer", "snow"));

    /** 버리는 베드락 상태 (자바에 없음) */
    private static final Set<String> DROPPED = Set.of("coral_fan_direction", "covered_bit", "facing_direction");

    private static final Map<String, BlockState> CACHE = new HashMap<>();
    /** 대응이 없어 돌로 바꾼 블록 · 자바 블록에 없는 상태 (시험용) */
    private static final Set<String> PROBLEMS = Collections.synchronizedSet(new LinkedHashSet<>());

    /** 베드락 블록 이름 → 자바 블록 ID (minecraft:lit_pumpkin → minecraft:jack_o_lantern) */
    public static String javaId(String bedrock) {
        String path = bedrock.startsWith("minecraft:") ? bedrock.substring("minecraft:".length()) : bedrock;
        String ns = bedrock.contains(":") ? bedrock.substring(0, bedrock.indexOf(':')) : "minecraft";
        if (ns.equals("minecraft")) return "minecraft:" + RENAME.getOrDefault(path, path);
        return bedrock;
    }

    /** 설계의 한 칸 (Arena.Cell — 수르트의 사슬 기둥 · 얼음 결정 · 서리 바닥을 되돌릴 때) → 자바 블록 상태 */
    public static BlockState state(Arena.Cell c) {
        return state(c.block(), c.states());
    }

    /** 베드락 블록 이름 · 상태 (없으면 null) → 자바 블록 상태 (대응이 없으면 로그 한 번 + 돌) */
    public static BlockState state(String bedrock, Map<String, ?> states) {
        String key = bedrock + (states == null ? "" : states.toString());
        synchronized (CACHE) {
            BlockState cached = CACHE.get(key);
            if (cached != null) return cached;
        }
        BlockState s = resolve(bedrock, states);
        synchronized (CACHE) {
            CACHE.put(key, s);
        }
        return s;
    }

    /** 시험용: 대응이 없던 블록 · 상태 (비었으면 모두 자바로 풀림) */
    public static Set<String> problems() {
        synchronized (PROBLEMS) {
            return Set.copyOf(PROBLEMS);
        }
    }

    private static void problem(String what) {
        if (PROBLEMS.add(what)) LOG.warn("[mmorpg] arena block: {}", what);
    }

    private static BlockState resolve(String bedrock, Map<String, ?> states) {
        String id = javaId(bedrock);
        Identifier rl = Identifier.tryParse(id);
        if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) {
            problem("no Java block for " + bedrock + " (" + id + ") — stone");
            return Blocks.STONE.defaultBlockState();
        }
        Block block = BuiltInRegistries.BLOCK.getValue(rl);
        BlockState s = block.defaultBlockState();
        s = with(s, id, "waterlogged", "false", false);
        if (states != null) {
            for (Map.Entry<String, ?> e : states.entrySet()) s = apply(s, id, e.getKey(), e.getValue());
        }
        return s;
    }

    private static boolean bool(Object v) {
        return v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v));
    }

    private static int intOf(Object v) {
        if (v instanceof Number n) return n.intValue();
        try {
            return (int) Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String wallSide(Object v) {
        return switch (String.valueOf(v)) {
            case "short" -> "low";
            case "tall" -> "tall";
            default -> "none";
        };
    }

    /** 베드락 상태 하나를 자바 상태로 */
    private static BlockState apply(BlockState s, String id, String k, Object v) {
        if (DROPPED.contains(k)) return s;
        String sv = String.valueOf(v);
        return switch (k) {
            // 호박 (자바는 방향 없음) · 매끄러운 석영 (자바는 축 없음) 은 없어도 문제 아님
            case "pillar_axis" -> with(s, id, "axis", sv, !id.equals("minecraft:smooth_quartz"));
            case "minecraft:cardinal_direction" -> with(s, id, "facing", sv, !id.equals("minecraft:pumpkin"));
            case "minecraft:block_face" -> with(s, id, "facing", sv, true);
            case "hanging" -> id.equals("minecraft:pointed_dripstone") ? with(s, id, "vertical_direction", bool(v) ? "down" : "up", true) : with(s, id, "hanging", sv, true);
            case "dripstone_thickness" -> with(s, id, "thickness", sv.equals("merge") ? "tip_merge" : sv, true);
            case "candles" -> with(s, id, "candles", Integer.toString(intOf(v) + 1), true);
            case "lit" -> with(s, id, "lit", sv, true);
            case "extinguished" -> with(s, id, "lit", Boolean.toString(!bool(v)), true);
            case "cluster_count" -> with(s, id, "pickles", Integer.toString(intOf(v) + 1), true);
            case "dead_bit" -> with(s, id, "waterlogged", Boolean.toString(!bool(v)), true);
            case "height" -> with(s, id, "layers", Integer.toString(intOf(v) + 1), true);
            case "powered_bit" -> with(s, id, "powered", sv, true);
            case "tip" -> with(s, id, "tip", sv, true);
            case "minecraft:connection_east" -> with(s, id, "east", sv, true);
            case "minecraft:connection_west" -> with(s, id, "west", sv, true);
            case "minecraft:connection_south" -> with(s, id, "south", sv, true);
            case "minecraft:connection_north" -> with(s, id, "north", sv, true);
            case "wall_connection_type_east" -> with(s, id, "east", wallSide(v), true);
            case "wall_connection_type_west" -> with(s, id, "west", wallSide(v), true);
            case "wall_connection_type_south" -> with(s, id, "south", wallSide(v), true);
            case "wall_connection_type_north" -> with(s, id, "north", wallSide(v), true);
            case "wall_post_bit" -> with(s, id, "up", sv, true);
            default -> {
                problem("unknown Bedrock state " + k + "=" + sv + " on " + id);
                yield s;
            }
        };
    }

    /** 자바 상태 이름 · 값 (글자) 을 넣음 — 블록에 없는 상태면 그대로 (expected 면 문제로 적음) */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState with(BlockState s, String id, String prop, String value, boolean expected) {
        Property<?> p = s.getBlock().getStateDefinition().getProperty(prop);
        if (p == null) {
            if (expected) problem(id + " has no property " + prop);
            return s;
        }
        Optional<?> v = p.getValue(value);
        if (v.isEmpty()) {
            problem(id + " " + prop + " has no value " + value);
            return s;
        }
        return s.setValue((Property) p, (Comparable) v.get());
    }
}
