package mmorpg.core.field;

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
import mmorpg.core.Obj;

/**
 * 섬 맵 설계 (field.json — 2026-11-07): 섬 · 다리 · 자리 (마을 · 거점 · 사냥터 · 보스 제단).
 * 섬 하나가 한 줄이라 새 섬은 줄을 더하면 됨 (사용자 "다음에 머 추가할때 편할거 같음").
 */
public final class FieldPlan {
    public static final String RESOURCE = "/mmorpg/field.json";

    /** 섬: 가운데 (x, z) · 반지름 · 지형 종류 (village · farm · desert · sea · islet · north · void · muspel) · 퀘스트 지역 (없으면 null) · 해안 들쭉날쭉함 */
    public record Island(int index, String id, String name, String terrain, String region, double x, double z, double radius, double coast,
            int levelMin, int levelMax) {}

    /** 자리: kind = town · tree · camp · zone · boss. 사냥터는 mob, 보스는 boss, 거점은 지역 NPC 번호 npc (없으면 0) */
    public record Site(String id, String kind, String island, double x, double z, double r, String mob, String boss, int npc, String name) {}

    /** 다리: 두 섬의 가운데를 잇는 줄 위 (해안에서 해안까지) */
    public record Bridge(String from, String to) {}

    /** 강 (바다 높이 물길) · 용암 강 (lava): 점들을 잇는 굽은 줄, 너비 = 가운데에서 물가까지 */
    public record River(String id, String island, boolean lava, double width, List<double[]> points) {}

    /** 호수: 둘레 땅보다 낮은 둥근 웅덩이 (물 높이 = 둘레 땅 − 1) */
    public record Lake(String id, String island, double x, double z, double r) {}

    public final long seed;
    public final int sea;
    public final double borderX;
    public final double borderZ;
    public final double borderSize;
    public final int spawnX;
    public final int spawnZ;
    public final int bridgeWidth;
    public final int bridgeDeck;
    public final List<Island> islands;
    public final List<Bridge> bridges;
    public final List<Site> sites;
    public final List<River> rivers;
    public final List<Lake> lakes;
    private final Map<String, Island> byId;

    private FieldPlan(Obj o) {
        seed = (long) o.num("seed");
        sea = (int) o.numOr("seaLevel", 63);
        Obj b = o.obj("border");
        borderX = b.num("x");
        borderZ = b.num("z");
        borderSize = b.num("size");
        List<Double> sp = o.numbersOrNull("spawn");
        spawnX = sp == null ? 0 : sp.get(0).intValue();
        spawnZ = sp == null ? 0 : sp.get(1).intValue();
        Obj br = o.objOrNull("bridge");
        bridgeWidth = br == null ? 5 : (int) br.numOr("width", 5);
        bridgeDeck = br == null ? sea + 7 : (int) br.numOr("deck", sea + 7);
        List<Island> is = new ArrayList<>();
        Map<String, Island> ids = new LinkedHashMap<>();
        for (Obj i : o.objsOrNull("islands")) {
            List<Double> lv = i.numbersOrNull("level");
            Island isl = new Island(is.size(), i.str("id"), i.str("name"), i.str("terrain"), i.strOrNull("region"), i.num("x"), i.num("z"), i.num("radius"),
                    i.numOr("coast", 0.15), lv == null ? 1 : lv.get(0).intValue(), lv == null ? 60 : lv.get(1).intValue());
            if (ids.put(isl.id(), isl) != null) throw new IllegalStateException("field.json: island " + isl.id() + " twice");
            is.add(isl);
        }
        islands = List.copyOf(is);
        byId = Collections.unmodifiableMap(ids);
        List<Bridge> bs = new ArrayList<>();
        for (Obj x : o.objsOrNull("bridges")) {
            Bridge bridge = new Bridge(x.str("from"), x.str("to"));
            if (!byId.containsKey(bridge.from()) || !byId.containsKey(bridge.to())) throw new IllegalStateException("field.json: bridge " + bridge);
            bs.add(bridge);
        }
        bridges = List.copyOf(bs);
        List<Site> ss = new ArrayList<>();
        for (Obj s : o.objsOrNull("sites")) {
            Site site = new Site(s.str("id"), s.str("kind"), s.str("island"), s.num("x"), s.num("z"), s.numOr("r", 0), s.strOrNull("mob"), s.strOrNull("boss"),
                    (int) s.numOr("npc", 0), s.strOrNull("name"));
            if (!byId.containsKey(site.island())) throw new IllegalStateException("field.json: site " + site.id() + " on unknown island " + site.island());
            ss.add(site);
        }
        sites = List.copyOf(ss);
        List<River> rs = new ArrayList<>();
        List<Obj> rl = o.objsOrNull("rivers");
        if (rl != null) {
            for (Obj r : rl) {
                List<double[]> pts = new ArrayList<>();
                for (Object p : r.list("points")) {
                    List<?> xy = (List<?>) p;
                    pts.add(new double[] {((Number) xy.get(0)).doubleValue(), ((Number) xy.get(1)).doubleValue()});
                }
                if (pts.size() < 2) throw new IllegalStateException("field.json: river " + r.str("id") + " needs two points");
                rs.add(new River(r.str("id"), r.str("island"), r.truthy("lava"), r.numOr("width", 6), List.copyOf(pts)));
            }
        }
        rivers = List.copyOf(rs);
        List<Lake> ls = new ArrayList<>();
        List<Obj> ll = o.objsOrNull("lakes");
        if (ll != null) for (Obj l : ll) ls.add(new Lake(l.str("id"), l.str("island"), l.num("x"), l.num("z"), l.num("r")));
        lakes = List.copyOf(ls);
    }

    public Island island(String id) {
        return byId.get(id);
    }

    /** 이 종류의 자리 모두 (적힌 차례) */
    public List<Site> sites(String kind) {
        List<Site> out = new ArrayList<>();
        for (Site s : sites) if (s.kind().equals(kind)) out.add(s);
        return out;
    }

    public Site site(String id) {
        for (Site s : sites) if (s.id().equals(id)) return s;
        return null;
    }

    public static FieldPlan parse(String json) {
        return new FieldPlan(Obj.of(Json.parseObject(json), "field"));
    }

    public static FieldPlan get() {
        return Holder.PLAN;
    }

    private static final class Holder {
        private static final FieldPlan PLAN = load();
    }

    private static FieldPlan load() {
        try (InputStream in = FieldPlan.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException(RESOURCE + " is not on the classpath");
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + RESOURCE, e);
        }
    }
}
