package mmorpg.fabric.tree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import mmorpg.core.Json;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Props;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 세계수 기록 (worldtree.js 의 "기록" 부분 — treeRecords · addRecord · dropRecord · newLog · codeOf · encodeCodes · decodeCodes · saveLog · loadLog):
 * 세계 동적 속성 rpg:world_trees = 최근 keep 그루 [{id, d, x, y, z, h, s, r, by, v, fp, n}] (애드온과 같은 JSON),
 * rpg:wt_&lt;id&gt; (· rpg:wt_&lt;id&gt;.1 …, 30000자씩) = {p: 팔레트, w: 글자 수, c: 칸 번호들 (36진 · 같은 번호가 이어지면 줄임), y: [[칸, 깔개 높이]], e: [[칸, dx, dy, dz, 번호]]}.
 * 칸 번호 0 = 놓지 않음, k = 놓음 (원래 블록 = p[k - 1], p[0] = 공기).
 * <p>
 * 자바에서 달라진 점: 팔레트 항목은 자바 블록 이름 · 상태 [이름, {상태: 값}] (물에 잠김은 블록 상태 안이라 애드온의 셋째 칸 (1 = 물에 잠김) 은 쓰지 않음 —
 * 읽을 때는 받아 줌). 세계 속성은 {@link Props} (오버월드에 붙여 저장). 글자 한 칸이 65535바이트를 넘지 않게 애드온처럼 30000자씩 나눔.
 */
public final class TreeLog {
    /** 기록된 세계수 목록 (세계 속성 이름 — 애드온과 같음) */
    public static final String LIST_KEY = "rpg:world_trees";
    private static final int LOG_PART = 30000;
    private static final Pattern ID = Pattern.compile("^[a-z0-9]+$");
    private static final Pattern CODE = Pattern.compile("^[0-9a-z]+$");

    /** 세계수 하나의 기록 (worldtree.js TreeRecord): h = 높이, s = 씨앗, r = 잎 끝까지 거리, by = 심은 사람, fp = 설계 지문, n = 칸 수 */
    public record Rec(String id, String d, int x, int y, int z, int h, int s, int r, String by, int v, long fp, int n) {
        public BlockPos base() {
            return new BlockPos(x, y, z);
        }

        Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("d", d);
            m.put("x", x);
            m.put("y", y);
            m.put("z", z);
            m.put("h", h);
            m.put("s", s);
            m.put("r", r);
            m.put("by", by);
            m.put("v", v);
            m.put("fp", fp);
            m.put("n", n);
            return m;
        }
    }

    /** 함께 치운 칸 (세계 칸 좌표, 원래 블록 번호) */
    public record Extra(int x, int y, int z, int code) {}

    /** 칸 번호들 (0 = 놓지 않음) */
    public final int[] codes;
    /** 팔레트 [이름, {상태: 값}] — 번호 k 의 블록 = palette[k - 1] */
    public final List<List<Object>> palette;
    private final Map<String, Integer> pidx;
    /** 깔개 · 덤불을 놓은 높이 (칸 번호 → 밑동 기준 dy) */
    public final Map<Integer, Integer> dys;
    /** 함께 치운 칸 (칸 번호 → 아래에서 위 차례) */
    public final Map<Integer, List<Extra>> extras;
    /** 저장할 글의 앞부분 (자라는 동안 끝난 단계는 다시 줄이지 않음) */
    private int encW;
    private int encUpTo;
    private String encPrefix = "";
    /** 팔레트 번호 → 블록 상태 (알 수 없는 블록은 null) */
    private final Map<Integer, BlockState> resolved = new HashMap<>();

    private TreeLog(int[] codes, List<List<Object>> palette, Map<Integer, Integer> dys, Map<Integer, List<Extra>> extras) {
        this.codes = codes;
        this.palette = palette;
        this.pidx = new HashMap<>();
        for (int i = 0; i < palette.size(); i++) pidx.put(Json.stringify(palette.get(i)), i + 1);
        this.dys = dys;
        this.extras = extras;
    }

    /** 빈 기록 (칸 n 개) — 팔레트 1번 = 공기 */
    public static TreeLog fresh(int n) {
        List<List<Object>> p = new ArrayList<>();
        p.add(List.of("minecraft:air", new LinkedHashMap<String, Object>()));
        return new TreeLog(new int[n], p, new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    /** 블록 → 팔레트 번호 (1 = 공기) */
    public int codeOf(BlockState b) {
        if (b == null || b.isAir()) return 1;
        List<Object> e = TreeBlocks.entryOf(b);
        String k = Json.stringify(e);
        Integer c = pidx.get(k);
        if (c == null) {
            palette.add(e);
            c = palette.size();
            pidx.put(k, c);
        }
        return c;
    }

    /** 팔레트 번호 → 블록 상태 (알 수 없는 블록이면 null) */
    public BlockState stateOf(int code) {
        if (resolved.containsKey(code)) return resolved.get(code);
        BlockState s = null;
        if (code >= 1 && code <= palette.size()) {
            List<Object> e = palette.get(code - 1);
            s = TreeBlocks.stateOf((String) e.get(0), (Map<?, ?>) e.get(1));
        }
        resolved.put(code, s);
        return s;
    }

    /** 놓은 칸 수 (번호가 0 이 아닌 칸) */
    public int placed() {
        int n = 0;
        for (int c : codes) if (c > 0) n++;
        return n;
    }

    // ------------------------------------------------------------ 칸 번호 글

    /** 칸 번호들 → 짧은 글: 한 칸 = w 자리 36진, 같은 번호가 3번 넘게 이어지면 "번호*개수(36진)," (worldtree.js encodeCodes) */
    public static String encodeCodes(int[] codes, int from, int to, int w) {
        StringBuilder out = new StringBuilder();
        for (int i = from; i < to;) {
            int j = i;
            while (j < to && codes[j] == codes[i]) j++;
            String c = Integer.toString(codes[i], 36);
            while (c.length() < w) c = "0" + c;
            int n = j - i;
            if (n >= 3) out.append(c).append('*').append(Integer.toString(n, 36)).append(',');
            else for (int k = 0; k < n; k++) out.append(c);
            i = j;
        }
        return out.toString();
    }

    /** 짧은 글 → 칸 번호들 (n 칸). 틀린 글이면 null (worldtree.js decodeCodes) */
    public static int[] decodeCodes(String s, int n, int w) {
        int[] out = new int[n];
        int k = 0;
        for (int i = 0; i < s.length();) {
            if (i + w > s.length()) return null;
            String tok = s.substring(i, i + w);
            if (!CODE.matcher(tok).matches()) return null;
            int v = Integer.parseInt(tok, 36);
            i += w;
            int cnt = 1;
            if (i < s.length() && s.charAt(i) == '*') {
                int e = s.indexOf(',', i);
                if (e < 0) return null;
                try {
                    cnt = Integer.parseInt(s.substring(i + 1, e), 36);
                } catch (NumberFormatException ex) {
                    return null;
                }
                i = e + 1;
                if (!(cnt > 0)) return null;
            }
            if ((long) k + cnt > n) return null;
            java.util.Arrays.fill(out, k, k + cnt, v);
            k += cnt;
        }
        return k == n ? out : null;
    }

    // ------------------------------------------------------------ 세계에 (최근 keep 그루 + 칸마다 원래 블록)

    private static String logKey(String id, int k) {
        return k == 0 ? "rpg:wt_" + id : "rpg:wt_" + id + "." + k;
    }

    private static int num(Object o) {
        return o instanceof Number n ? (int) n.doubleValue() : 0;
    }

    private static boolean finite(Object o) {
        return o instanceof Number n && Double.isFinite(n.doubleValue());
    }

    /** 기록된 세계수 (오래된 것부터) — 망가진 항목은 뺌 */
    public static List<Rec> records(MinecraftServer server) {
        List<Rec> out = new ArrayList<>();
        String raw = Props.world(server, LIST_KEY);
        if (raw == null || raw.isEmpty()) return out;
        Object a;
        try {
            a = Json.parse(raw);
        } catch (RuntimeException e) {
            return out;
        }
        if (!(a instanceof List<?> list)) return out;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> t) || !(t.get("id") instanceof String id) || !ID.matcher(id).matches() || !(t.get("d") instanceof String d)) continue;
            boolean ok = true;
            for (String k : new String[] {"x", "y", "z", "h", "s", "r", "fp", "n"}) if (!finite(t.get(k))) ok = false;
            if (!ok) continue;
            out.add(new Rec(id, d, num(t.get("x")), num(t.get("y")), num(t.get("z")), num(t.get("h")), num(t.get("s")), num(t.get("r")),
                    t.get("by") instanceof String by ? by : "?", num(t.get("v")), ((Number) t.get("fp")).longValue(), num(t.get("n"))));
        }
        return out;
    }

    /** 마지막으로 심은 세계수 기록 (없으면 null) */
    public static Rec last(MinecraftServer server) {
        List<Rec> list = records(server);
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }

    public static void saveRecords(MinecraftServer server, List<Rec> list) {
        List<Object> out = new ArrayList<>();
        for (Rec r : list) out.add(r.json());
        Props.setWorld(server, LIST_KEY, list.isEmpty() ? null : Json.stringify(out));
    }

    public static void dropLog(MinecraftServer server, String id) {
        for (int k = 0; k < 64; k++) {
            if (Props.world(server, logKey(id, k)) == null) break;
            Props.setWorld(server, logKey(id, k), null);
        }
    }

    /** 기록에 더함 (keep 그루가 넘으면 오래된 것과 그 칸 기록을 지움) */
    public static void addRecord(MinecraftServer server, Rec rec, int keep) {
        List<Rec> list = records(server);
        list.add(rec);
        while (list.size() > keep) {
            Rec old = list.remove(0);
            dropLog(server, old.id());
        }
        saveRecords(server, list);
    }

    public static void dropRecord(MinecraftServer server, String id) {
        List<Rec> list = records(server);
        list.removeIf(r -> r.id().equals(id));
        saveRecords(server, list);
        dropLog(server, id);
    }

    /**
     * 기록을 세계에 (30000자씩 나눠서). stable = 이 칸 번호 앞은 더 바뀌지 않음 (자라는 동안 끝난 단계: 다시 줄이지 않고 이어 붙임 — worldtree.js saveLog)
     */
    public boolean save(MinecraftServer server, String id, BlockPos base, int stable) {
        int w = palette.size() < 36 ? 1 : 2;
        if (encW != w || encUpTo > stable) {
            encW = w;
            encUpTo = 0;
            encPrefix = "";
        }
        if (stable > encUpTo) {
            encPrefix += encodeCodes(codes, encUpTo, stable, w);
            encUpTo = stable;
        }
        List<Object> e = new ArrayList<>();
        for (Map.Entry<Integer, List<Extra>> en : extras.entrySet()) {
            for (Extra x : en.getValue()) e.add(List.of(en.getKey(), x.x() - base.getX(), x.y() - base.getY(), x.z() - base.getZ(), x.code()));
        }
        List<Object> y = new ArrayList<>();
        for (Map.Entry<Integer, Integer> d : dys.entrySet()) y.add(List.of(d.getKey(), d.getValue()));
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("p", palette);
        o.put("w", w);
        o.put("c", encPrefix + encodeCodes(codes, stable, codes.length, w));
        o.put("y", y);
        o.put("e", e);
        String data = Json.stringify(o);
        int parts = Math.max(1, (data.length() + LOG_PART - 1) / LOG_PART);
        try {
            for (int k = 0; k < parts; k++) Props.setWorld(server, logKey(id, k), data.substring(k * LOG_PART, Math.min(data.length(), (k + 1) * LOG_PART)));
            for (int k = parts; k < 64 && Props.world(server, logKey(id, k)) != null; k++) Props.setWorld(server, logKey(id, k), null);
            return true;
        } catch (RuntimeException err) {
            Mmorpg.LOG.warn("[mmorpg] world tree record", err);
            return false;
        }
    }

    /** 세계의 기록 (칸 rec.n 개). 없거나 망가졌으면 null (worldtree.js loadLog) */
    public static TreeLog load(MinecraftServer server, Rec rec) {
        try {
            StringBuilder s = new StringBuilder();
            for (int k = 0; k < 64; k++) {
                String v = Props.world(server, logKey(rec.id(), k));
                if (v == null) break;
                s.append(v);
            }
            if (s.length() == 0) return null;
            if (!(Json.parse(s.toString()) instanceof Map<?, ?> o)) return null;
            if (!(o.get("p") instanceof List<?> p)) return null;
            List<List<Object>> palette = new ArrayList<>();
            for (Object pe : p) {
                if (!(pe instanceof List<?> l) || l.size() < 2 || l.size() > 3 || !(l.get(0) instanceof String name) || !(l.get(1) instanceof Map<?, ?> st)) return null;
                if (l.size() == 3 && !(l.get(2) instanceof Number one && one.doubleValue() == 1)) return null;
                palette.add(List.of(name, st));
            }
            int w = o.get("w") instanceof Number wn ? (int) wn.doubleValue() : 0;
            if ((w != 1 && w != 2) || !(o.get("c") instanceof String c) || !(o.get("y") instanceof List<?> ys) || !(o.get("e") instanceof List<?> es)) return null;
            int[] codes = decodeCodes(c, rec.n(), w);
            if (codes == null) return null;
            for (int code : codes) if (code > palette.size()) return null;
            Map<Integer, Integer> dys = new LinkedHashMap<>();
            for (Object d : ys) if (d instanceof List<?> dl && dl.size() >= 2 && isInt(dl.get(0)) && isInt(dl.get(1))) dys.put(num(dl.get(0)), num(dl.get(1)));
            Map<Integer, List<Extra>> extras = new LinkedHashMap<>();
            for (Object x : es) {
                if (!(x instanceof List<?> xl) || xl.size() != 5) continue;
                boolean ints = true;
                for (Object v : xl) if (!isInt(v)) ints = false;
                if (!ints || num(xl.get(4)) < 1 || num(xl.get(4)) > palette.size()) continue;
                extras.computeIfAbsent(num(xl.get(0)), k -> new ArrayList<>())
                        .add(new Extra(rec.x() + num(xl.get(1)), rec.y() + num(xl.get(2)), rec.z() + num(xl.get(3)), num(xl.get(4))));
            }
            return new TreeLog(codes, palette, dys, extras);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isInt(Object o) {
        return o instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue()) && Math.abs(n.doubleValue()) < 1e9;
    }
}
