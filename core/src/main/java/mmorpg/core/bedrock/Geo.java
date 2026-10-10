package mmorpg.core.bedrock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 베드락 모델 (geo.json 의 geometry 하나 — format 1.12.0): 뼈 (이름 · 부모 · 회전 중심 · 회전) 와 상자 (시작 · 크기 · 부풀림 · 거울 ·
 * 펼친 UV 또는 면마다 UV). 숫자는 베드락 그대로 (픽셀, 도). 자바 ModelPart 로 옮길 때 (클라이언트 BedrockModels):
 * x · z 그대로, y 는 뒤집음 (자바 모델은 아래가 +y — 뼈 자리 (px, 24 - py, pz)), 회전은 같은 수 (rx, ry, rz) — Blockbench 의 베드락 읽기
 * (x 뒤집고 x · y 회전 반대) 와 자바 내보내기 (x · y 뒤집고 x · y 회전 반대) 를 합친 것 (애드온 미리보기 도구와 꼭짓점이 같음을 확인).
 */
public final class Geo {
    public final String id;
    public final int textureWidth;
    public final int textureHeight;
    /** 보이는 범위 (visible_bounds_width · height · offset — 블록, 화면 밖 자르기용) */
    public final double boundsWidth;
    public final double boundsHeight;
    public final double[] boundsOffset;
    /** 뼈 (파일 순서) */
    public final List<Bone> bones;
    private final Map<String, Bone> byName;

    /** 뼈 하나 */
    public static final class Bone {
        /** 이름 (파일 그대로) */
        public final String name;
        /** 부모 뼈 이름 (없으면 null) */
        public final String parent;
        /** 회전 중심 (베드락 좌표, 픽셀) */
        public final double[] pivot;
        /** 기본 회전 (도) */
        public final double[] rotation;
        public final boolean mirror;
        public final double inflate;
        public final List<Cube> cubes;
        /** bones 안의 순서 */
        public final int index;

        Bone(String name, String parent, double[] pivot, double[] rotation, boolean mirror, double inflate, List<Cube> cubes, int index) {
            this.name = name;
            this.parent = parent;
            this.pivot = pivot;
            this.rotation = rotation;
            this.mirror = mirror;
            this.inflate = inflate;
            this.cubes = cubes;
            this.index = index;
        }
    }

    /** 상자 하나 */
    public static final class Cube {
        /** 시작 모서리 (베드락 좌표, 가장 작은 x · y · z) */
        public final double[] origin;
        public final double[] size;
        public final double inflate;
        public final boolean mirror;
        /** 펼친 UV 의 시작 (u, v) — 면마다 UV 면 null */
        public final double[] uv;
        /** 면마다 UV: "north" · "south" · "east" · "west" · "up" · "down" → {u, v, 폭, 높이} (없는 면은 그리지 않음) — 펼친 UV 면 null */
        public final Map<String, double[]> faces;
        /** 상자 회전 중심 · 회전 (없으면 null) */
        public final double[] pivot;
        public final double[] rotation;

        Cube(double[] origin, double[] size, double inflate, boolean mirror, double[] uv, Map<String, double[]> faces, double[] pivot, double[] rotation) {
            this.origin = origin;
            this.size = size;
            this.inflate = inflate;
            this.mirror = mirror;
            this.uv = uv;
            this.faces = faces;
            this.pivot = pivot;
            this.rotation = rotation;
        }
    }

    private Geo(String id, int tw, int th, double bw, double bh, double[] bo, List<Bone> bones) {
        this.id = id;
        this.textureWidth = tw;
        this.textureHeight = th;
        this.boundsWidth = bw;
        this.boundsHeight = bh;
        this.boundsOffset = bo;
        this.bones = Collections.unmodifiableList(bones);
        Map<String, Bone> m = new LinkedHashMap<>();
        for (Bone b : bones) m.put(b.name.toLowerCase(Locale.ROOT), b);
        this.byName = m;
    }

    /** 이름으로 뼈 (대소문자 가리지 않음 — 베드락과 같음), 없으면 null */
    public Bone bone(String name) {
        return name == null ? null : byName.get(name.toLowerCase(Locale.ROOT));
    }

    /** geometry JSON 하나 ({description, bones}) → 모델 */
    @SuppressWarnings("unchecked")
    public static Geo parse(Map<String, Object> g) {
        Map<String, Object> desc = (Map<String, Object>) g.get("description");
        String id = desc == null ? "?" : String.valueOf(desc.get("identifier"));
        int tw = desc == null ? 64 : (int) num(desc.get("texture_width"), 64);
        int th = desc == null ? 64 : (int) num(desc.get("texture_height"), 64);
        List<Bone> bones = new ArrayList<>();
        Object raw = g.get("bones");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?>)) continue;
                Map<String, Object> b = (Map<String, Object>) o;
                boolean mirror = Boolean.TRUE.equals(b.get("mirror"));
                double inflate = num(b.get("inflate"), 0);
                List<Cube> cubes = new ArrayList<>();
                if (b.get("cubes") instanceof List<?> cs) {
                    for (Object co : cs) {
                        if (!(co instanceof Map<?, ?>)) continue;
                        Map<String, Object> c = (Map<String, Object>) co;
                        double[] uv = null;
                        Map<String, double[]> faces = null;
                        Object u = c.get("uv");
                        if (u instanceof Map<?, ?> fm) {
                            faces = new LinkedHashMap<>();
                            for (Map.Entry<?, ?> e : fm.entrySet()) {
                                if (!(e.getValue() instanceof Map<?, ?>)) continue;
                                Map<String, Object> f = (Map<String, Object>) e.getValue();
                                double[] at = vec(f.get("uv"), 2, 0);
                                double[] sz = vec(f.get("uv_size"), 2, 0);
                                faces.put(String.valueOf(e.getKey()).toLowerCase(Locale.ROOT), new double[] {at[0], at[1], sz[0], sz[1]});
                            }
                        } else {
                            uv = vec(u, 2, 0);
                        }
                        cubes.add(new Cube(vec(c.get("origin"), 3, 0), vec(c.get("size"), 3, 0), c.containsKey("inflate") ? num(c.get("inflate"), 0) : inflate,
                                c.containsKey("mirror") ? Boolean.TRUE.equals(c.get("mirror")) : mirror, uv, faces,
                                c.containsKey("pivot") ? vec(c.get("pivot"), 3, 0) : null, c.containsKey("rotation") ? vec(c.get("rotation"), 3, 0) : null));
                    }
                }
                Object parent = b.get("parent");
                bones.add(new Bone(String.valueOf(b.get("name")), parent == null ? null : String.valueOf(parent), vec(b.get("pivot"), 3, 0),
                        vec(b.get("rotation"), 3, 0), mirror, inflate, Collections.unmodifiableList(cubes), bones.size()));
            }
        }
        double bw = desc == null ? 1 : num(desc.get("visible_bounds_width"), 1);
        double bh = desc == null ? 1 : num(desc.get("visible_bounds_height"), 1);
        double[] bo = desc == null ? new double[3] : vec(desc.get("visible_bounds_offset"), 3, 0);
        return new Geo(id, tw, th, bw, bh, bo, bones);
    }

    static double num(Object o, double fallback) {
        return o instanceof Number n ? n.doubleValue() : fallback;
    }

    static double[] vec(Object o, int n, double fallback) {
        double[] out = new double[n];
        java.util.Arrays.fill(out, fallback);
        if (o instanceof List<?> l) {
            for (int i = 0; i < n && i < l.size(); i++) out[i] = num(l.get(i), fallback);
        } else if (o instanceof Number x) {
            java.util.Arrays.fill(out, x.doubleValue());
        }
        return out;
    }
}
