package mmorpg.core.bedrock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mmorpg.core.Molang;

/**
 * 베드락 렌더 컨트롤러 (애드온이 쓰는 부분): 텍스처 (Texture.x 또는 Array.이름[식] — 엔티티의 텍스처 이름 목록에서 고름),
 * 덮는 색 overlay_color (r · g · b · a 숫자 또는 식 — 텍스처 위에 그 색을 a 만큼 섞음), 부위 보이기 part_visibility ([{뼈 이름 (* = 모두): 참/거짓 · 식}],
 * 뒤의 것이 앞의 것을 덮음). controller.render.default = 기본 텍스처 하나 · 덮는 색 없음.
 */
public final class RenderCtl {
    private final List<String> fixed;
    private final List<String> array;
    private final Molang.Expr index;
    private final Molang.Expr[] overlay;
    private final List<Map.Entry<String, Molang.Expr>> visibility;

    private RenderCtl(List<String> fixed, List<String> array, Molang.Expr index, Molang.Expr[] overlay, List<Map.Entry<String, Molang.Expr>> visibility) {
        this.fixed = fixed;
        this.array = array;
        this.index = index;
        this.overlay = overlay;
        this.visibility = visibility;
    }

    /** 기본 컨트롤러 (controller.render.default) */
    public static RenderCtl defaults() {
        return new RenderCtl(List.of("default"), null, null, null, List.of());
    }

    /** 컨트롤러 JSON → 컨트롤러 (식은 ctx 칸으로) */
    @SuppressWarnings("unchecked")
    public static RenderCtl parse(Map<String, Object> c, Molang.Vars ctx) {
        Map<String, List<String>> arrays = new LinkedHashMap<>();
        if (c.get("arrays") instanceof Map<?, ?> am && am.get("textures") instanceof Map<?, ?> tm) {
            for (Map.Entry<?, ?> e : tm.entrySet()) {
                List<String> keys = new ArrayList<>();
                if (e.getValue() instanceof List<?> l) for (Object o : l) keys.add(textureKey(String.valueOf(o)));
                arrays.put(String.valueOf(e.getKey()).toLowerCase(Locale.ROOT), keys);
            }
        }
        List<String> fixed = List.of("default");
        List<String> array = null;
        Molang.Expr index = null;
        if (c.get("textures") instanceof List<?> tl && !tl.isEmpty()) {
            String t = String.valueOf(tl.get(0));
            int open = t.indexOf('[');
            if (t.toLowerCase(Locale.ROOT).startsWith("array.") && open > 0 && t.endsWith("]")) {
                array = arrays.getOrDefault(t.substring(0, open).toLowerCase(Locale.ROOT), List.of("default"));
                index = Molang.parse(t.substring(open + 1, t.length() - 1), ctx);
            } else {
                fixed = List.of(textureKey(t));
            }
        }
        Molang.Expr[] overlay = null;
        if (c.get("overlay_color") instanceof Map<?, ?> om) {
            overlay = new Molang.Expr[4];
            String[] ch = {"r", "g", "b", "a"};
            for (int i = 0; i < 4; i++) overlay[i] = Molang.of(om.containsKey(ch[i]) ? om.get(ch[i]) : (i == 3 ? 1.0 : 0.0), ctx);
        }
        List<Map.Entry<String, Molang.Expr>> vis = new ArrayList<>();
        if (c.get("part_visibility") instanceof List<?> pl) {
            for (Object o : pl) {
                if (!(o instanceof Map<?, ?> m)) continue;
                for (Map.Entry<?, ?> e : m.entrySet()) vis.add(Map.entry(String.valueOf(e.getKey()).toLowerCase(Locale.ROOT), Molang.of(e.getValue(), ctx)));
            }
        }
        return new RenderCtl(fixed, array, index, overlay, Collections.unmodifiableList(vis));
    }

    /** "Texture.day" → "day" */
    private static String textureKey(String s) {
        int dot = s.indexOf('.');
        return (dot >= 0 ? s.substring(dot + 1) : s).toLowerCase(Locale.ROOT);
    }

    /** 지금 텍스처 이름 (엔티티 설명의 textures 의 열쇠) */
    public String texture(double[] v) {
        if (array == null) return fixed.get(0);
        int i = (int) Math.floor(index.eval(v));
        return array.get(Math.max(0, Math.min(array.size() - 1, i)));
    }

    /** 덮는 색 (r, g, b, a — 0 ~ 1) → out, 덮지 않으면 false */
    public boolean overlay(double[] v, float[] out) {
        if (overlay == null) return false;
        for (int i = 0; i < 4; i++) out[i] = (float) Math.max(0, Math.min(1, overlay[i].eval(v)));
        return out[3] > 0;
    }

    /** 뼈마다 보이는지 → visible (geo.bones 순서, 처음은 모두 참) */
    public void visibility(double[] v, Geo geo, boolean[] visible) {
        java.util.Arrays.fill(visible, true);
        for (Map.Entry<String, Molang.Expr> e : visibility) {
            boolean on = e.getValue().eval(v) != 0;
            String pat = e.getKey();
            for (Geo.Bone b : geo.bones) {
                if (matches(pat, b.name.toLowerCase(Locale.ROOT))) visible[b.index] = on;
            }
        }
    }

    public boolean hasVisibility() {
        return !visibility.isEmpty();
    }

    private static boolean matches(String pat, String name) {
        if (pat.equals("*")) return true;
        if (!pat.contains("*")) return pat.equals(name);
        String re = ("\\Q" + pat + "\\E").replace("*", "\\E.*\\Q");
        return name.matches(re);
    }
}
