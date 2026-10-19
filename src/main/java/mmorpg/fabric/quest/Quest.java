package mmorpg.fabric.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Rules;

/**
 * 퀘스트 하나 (quest.js 의 Quest · Reward · Goal · Entry typedef) 와 다듬기 (normalizeQuest · normalizeBuiltin), 저장 JSON.
 * 저장 모양은 애드온과 같음: 세계 속성 "rpg:quest:&lt;번호&gt;" = normalizeQuest 결과 ({id, title, giver, type, target, targetName, count, offer,
 * progress, done, reward: {emerald, xp, points, weapon, item}, repeat, requires, minLevel}), 플레이어 "rpg:qs" = {퀘스트 ID: Entry}
 * — 베드락 세계의 동적 속성을 그대로 옮겨 와도 읽힘. 필드는 애드온 객체처럼 바꿀 수 있음 (편집 창이 복사본 copy() 를 고쳐 Quests.saveQuest 로).
 * 마인크래프트 클래스를 쓰지 않음 (규칙 코어 · Gson 만).
 */
public final class Quest {
    /** 목표 종류 이름 · 그림 (quest.js QUEST_TYPES — 그림은 애드온 경로, 창은 Icon.of 로) */
    public record Type(String name, String icon) {}

    public static final Map<String, Type> TYPES;

    static {
        Map<String, Type> m = new LinkedHashMap<>();
        m.put("kill", new Type("몹 처치", "textures/items/iron_sword"));
        m.put("collect", new Type("아이템 가져오기", "textures/items/bundle"));
        m.put("talk", new Type("NPC에게 말 걸기", "textures/items/paper"));
        m.put("level", new Type("레벨 달성", "textures/items/experience_bottle"));
        m.put("try", new Type("무기 시험", "textures/items/iron_axe"));
        m.put("choose", new Type("무기 고르기", "textures/items/book_enchanted"));
        m.put("boss", new Type("보스 처치", "textures/items/nether_star"));
        m.put("trial", new Type("전설의 시련", "textures/items/totem"));
        TYPES = Collections.unmodifiableMap(m);
    }

    /** 편집 창에서 고를 수 있는 목표 종류 (나머지는 애드온에 들어 있는 퀘스트 전용 — quest.js QUEST_TYPE_KEYS) */
    public static final List<String> TYPE_KEYS = List.of("kill", "collect", "talk", "level");

    private static final Rules R = Rules.get();
    private static final Obj MONSTERS = R.obj("MONSTERS");
    private static final Obj BOSSES = R.obj("BOSSES");

    // ------------------------------------------------------------ 필드 (quest.js Quest)

    public String id = "";
    public String title = "퀘스트";
    /** 주는 NPC 번호 */
    public String giver = "";
    /** kill · collect · talk · level (만든 퀘스트), try · choose · boss · trial (애드온에 들어 있는 퀘스트) */
    public String type = "kill";
    /** 처치: 몹 ID, 가져오기: 아이템 ID, 말 걸기: NPC 번호, 보스: 보스 키 */
    public String target = "";
    public String targetName = "";
    public int count = 1;
    /** 대사 (| 줄바꿈, || 다음 장) */
    public String offer = "";
    public String progress = "";
    public String done = "";
    public Reward reward = new Reward();
    public boolean repeat;
    /** 먼저 끝낼 퀘스트 ID */
    public String requires = "";
    public int minLevel;

    // 애드온에 들어 있는 퀘스트만 (만든 퀘스트는 기본값)
    /** 메인 퀘스트 */
    public boolean main;
    /** "repeat" (반복 의뢰) · "royal" (왕실) · "legend" (전설) · "" */
    public String side = "";
    /** 그 무기의 길에서만 ("" = 모두) */
    public String path = "";
    /** 순서 */
    public double ord;
    /** 길 안의 단계 (1 ~ PATH_STEPS) · 왕실 단계 */
    public int step;
    /** 수락할 때 주는 시험용 무기 종류 */
    public List<String> give = new ArrayList<>();
    /** 길마다 완료 대사 (무기 고르기 — 없으면 null) */
    public Map<String, String> doneBy;
    /** 전설 무기를 주는 퀘스트 (큰 글자 · 모두에게 알림) */
    public boolean legend;
    /** 마지막 (메인 이야기 · 왕실) */
    public boolean finale;
    /** 반복 의뢰 · 전설: 메인 이야기의 이 단계를 끝내면 열림 */
    public int afterStep;
    /** 경험치 제한 몬스터 (반복 의뢰) */
    public String xpMob = "";
    /** 전설의 시련 (trial) 목표 (아니면 null) */
    public List<Goal> goals;
    /** 초월 보스 전설: 끝낼 때 큰 글자 (무기 보상 대신) */
    public String honor = "";

    /** 보상 (quest.js Reward). xpPoints · items · armor 는 애드온에 들어 있는 퀘스트만, armorType · capped 는 rewardFor 가 정함 */
    public static final class Reward {
        /** 실버 (0.21.0 — 예전 에메랄드 값 그대로, 저장 이름 "emerald" 도 그대로) */
        public int emerald;
        /** 골드 (0.21.0 — 메인 퀘스트 · 반복 의뢰 · 전설 · 왕실 · 만든 퀘스트) */
        public int gold;
        /** 경험치 레벨 */
        public int xp;
        /** 스탯 포인트 */
        public int points;
        /** 무기 ("sword_l10_noble" — rpg: 뺀 것) */
        public String weapon = "";
        /** 아이템 보상 (만든 퀘스트 — 없으면 null) */
        public RewardItem item;
        /** 재료 보상 ({id, amount}) */
        public List<RewardItem> items = new ArrayList<>();
        /** 경험치 포인트 (반복 의뢰) */
        public int xpPoints;
        /** "path" = 무기의 길에 맞는 초월 갑옷 (왕실) */
        public String armor = "";
        /** 실제로 줄 갑옷 종류 (rewardFor) */
        public String armorType = "";
        /** 경험치 제한에 걸린 몬스터 (rewardFor) */
        public String capped = "";

        public Reward copy() {
            Reward r = new Reward();
            r.emerald = emerald;
            r.gold = gold;
            r.xp = xp;
            r.points = points;
            r.weapon = weapon;
            r.item = item == null ? null : item.copy();
            r.items = new ArrayList<>();
            for (RewardItem it : items) r.items.add(it.copy());
            r.xpPoints = xpPoints;
            r.armor = armor;
            r.armorType = armorType;
            r.capped = capped;
            return r;
        }
    }

    /** 아이템 보상 하나 (id · 개수 · 보이는 이름 · 붙일 이름 — 이름은 만든 퀘스트의 아이템 보상만) */
    public static final class RewardItem {
        public String id;
        public int amount;
        public String name = "";
        /** 아이템에 붙일 이름 (없으면 null) */
        public String nameTag;

        public RewardItem(String id, int amount) {
            this.id = id;
            this.amount = amount;
        }

        public RewardItem(String id, int amount, String name, String nameTag) {
            this(id, amount);
            this.name = name == null ? "" : name;
            this.nameTag = nameTag;
        }

        public RewardItem copy() {
            return new RewardItem(id, amount, name, nameTag);
        }
    }

    /** 전설의 시련 목표 (quest.js Goal): kind "kill" (mobs 중 어느 것이든) · "boss" */
    public record Goal(String kind, List<String> mobs, String boss, int count, String label) {}

    /** 플레이어 진행 한 칸 (quest.js Entry): s "a" 진행 중 · "d" 완료, n 처치 수, c 완료 횟수, r 달성 알림, t 써 본 무기 종류, o 목표마다 진행 */
    public static final class Entry {
        public String s = "";
        public int n;
        public int c;
        public Integer r;
        public List<String> t;
        public List<Integer> o;

        public Entry(String s, int n, int c) {
            this.s = s;
            this.n = n;
            this.c = c;
        }

        public boolean active() {
            return "a".equals(s);
        }

        public Entry copy() {
            Entry e = new Entry(s, n, c);
            e.r = r;
            e.t = t == null ? null : new ArrayList<>(t);
            e.o = o == null ? null : new ArrayList<>(o);
            return e;
        }

        public JsonObject toJson() {
            JsonObject j = new JsonObject();
            j.addProperty("s", s);
            j.addProperty("n", n);
            j.addProperty("c", c);
            if (r != null) j.addProperty("r", r);
            if (t != null) {
                JsonArray a = new JsonArray();
                for (String x : t) a.add(x);
                j.add("t", a);
            }
            if (o != null) {
                JsonArray a = new JsonArray();
                for (int x : o) a.add(x);
                j.add("o", a);
            }
            return j;
        }

        /** 저장된 한 칸 (모양이 틀리면 기본값으로 — JS 의 e?.c > 0 처럼 없는 값은 0) */
        public static Entry fromJson(JsonElement el) {
            Entry e = new Entry("", 0, 0);
            if (el == null || !el.isJsonObject()) return e;
            JsonObject j = el.getAsJsonObject();
            JsonElement s = j.get("s");
            e.s = s != null && s.isJsonPrimitive() && s.getAsJsonPrimitive().isString() ? s.getAsString() : "";
            e.n = (int) floor0(j.get("n"));
            e.c = (int) floor0(j.get("c"));
            if (j.has("r") && !j.get("r").isJsonNull()) e.r = (int) floor0(j.get("r"));
            JsonElement t = j.get("t");
            if (t != null && t.isJsonArray()) {
                e.t = new ArrayList<>();
                for (JsonElement x : t.getAsJsonArray()) e.t.add(jsString(x));
            }
            JsonElement o = j.get("o");
            if (o != null && o.isJsonArray()) {
                e.o = new ArrayList<>();
                for (JsonElement x : o.getAsJsonArray()) e.o.add((int) floor0(x));
            }
            return e;
        }
    }

    /** 플레이어 진행 전체 ("rpg:qs" — 순서 유지) */
    public static Map<String, Entry> parseState(String raw) {
        Map<String, Entry> st = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return st;
        try {
            JsonElement el = JsonParser.parseString(raw);
            if (!el.isJsonObject()) return st;
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) st.put(e.getKey(), Entry.fromJson(e.getValue()));
        } catch (RuntimeException e) {
            return new LinkedHashMap<>();
        }
        return st;
    }

    public static String stateJson(Map<String, Entry> st) {
        JsonObject j = new JsonObject();
        for (Map.Entry<String, Entry> e : st.entrySet()) j.add(e.getKey(), e.getValue().toJson());
        return j.toString();
    }

    // ------------------------------------------------------------ 복사 · JSON

    /** 깊은 복사 (편집 창: JSON.parse(JSON.stringify(quest))) */
    public Quest copy() {
        Quest q = new Quest();
        q.id = id;
        q.title = title;
        q.giver = giver;
        q.type = type;
        q.target = target;
        q.targetName = targetName;
        q.count = count;
        q.offer = offer;
        q.progress = progress;
        q.done = done;
        q.reward = reward.copy();
        q.repeat = repeat;
        q.requires = requires;
        q.minLevel = minLevel;
        q.main = main;
        q.side = side;
        q.path = path;
        q.ord = ord;
        q.step = step;
        q.give = new ArrayList<>(give);
        q.doneBy = doneBy == null ? null : new LinkedHashMap<>(doneBy);
        q.legend = legend;
        q.finale = finale;
        q.afterStep = afterStep;
        q.xpMob = xpMob;
        q.goals = goals == null ? null : new ArrayList<>(goals);
        q.honor = honor;
        return q;
    }

    /** 만든 퀘스트의 저장 JSON (애드온 JSON.stringify(normalizeQuest(q)) 과 같은 모양) */
    public JsonObject toJson() {
        JsonObject j = new JsonObject();
        j.addProperty("id", id);
        j.addProperty("title", title);
        j.addProperty("giver", giver);
        j.addProperty("type", type);
        j.addProperty("target", target);
        j.addProperty("targetName", targetName);
        j.addProperty("count", count);
        j.addProperty("offer", offer);
        j.addProperty("progress", progress);
        j.addProperty("done", done);
        JsonObject r = new JsonObject();
        r.addProperty("emerald", reward.emerald);
        if (reward.gold > 0) r.addProperty("gold", reward.gold);
        r.addProperty("xp", reward.xp);
        r.addProperty("points", reward.points);
        r.addProperty("weapon", reward.weapon);
        r.add("item", itemJson(reward.item, true));
        j.add("reward", r);
        j.addProperty("repeat", repeat);
        j.addProperty("requires", requires);
        j.addProperty("minLevel", minLevel);
        return j;
    }

    private static JsonElement itemJson(RewardItem it, boolean named) {
        if (it == null) return JsonNull.INSTANCE;
        JsonObject o = new JsonObject();
        o.addProperty("id", it.id);
        o.addProperty("amount", it.amount);
        if (named) {
            o.addProperty("name", it.name);
            if (it.nameTag != null) o.addProperty("nameTag", it.nameTag);
        }
        return o;
    }

    /** 모든 필드 (애드온에 들어 있는 퀘스트의 normalizeBuiltin 결과와 같은 모양 — 확인 · 기록용) */
    public JsonObject toFullJson() {
        JsonObject j = toJson();
        JsonObject r = j.getAsJsonObject("reward");
        JsonArray items = new JsonArray();
        for (RewardItem it : reward.items) items.add(itemJson(it, false));
        r.add("items", items);
        r.addProperty("xpPoints", reward.xpPoints);
        r.addProperty("armor", reward.armor);
        j.addProperty("main", main);
        j.addProperty("side", side);
        j.addProperty("path", path);
        j.addProperty("ord", ord);
        j.addProperty("step", step);
        JsonArray g = new JsonArray();
        for (String t : give) g.add(t);
        j.add("give", g);
        if (doneBy != null) {
            JsonObject d = new JsonObject();
            for (Map.Entry<String, String> e : doneBy.entrySet()) d.addProperty(e.getKey(), e.getValue());
            j.add("doneBy", d);
        }
        j.addProperty("legend", legend);
        j.addProperty("finale", finale);
        j.addProperty("afterStep", afterStep);
        j.addProperty("xpMob", xpMob);
        if (goals != null) {
            JsonArray a = new JsonArray();
            for (Goal x : goals) {
                JsonObject o = new JsonObject();
                o.addProperty("kind", x.kind());
                if (x.mobs() != null) {
                    JsonArray m = new JsonArray();
                    for (String s : x.mobs()) m.add(s);
                    o.add("mobs", m);
                }
                if (x.boss() != null) o.addProperty("boss", x.boss());
                o.addProperty("count", x.count());
                if (x.label() != null) o.addProperty("label", x.label());
                a.add(o);
            }
            j.add("goals", a);
        }
        j.addProperty("honor", honor);
        return j;
    }

    // ------------------------------------------------------------ 다듬기 (quest.js normalizeQuest · normalizeBuiltin · normalizeGoals)

    /** int(v, max): 0 ~ max 의 정수 (숫자가 아니면 0) */
    static int clampInt(JsonElement v, int max) {
        double n = floor0(v);
        return (int) Math.max(0, Math.min(max, n));
    }

    /** Math.floor(Number(v) || 0) */
    static double floor0(JsonElement v) {
        double n = jsNumber(v);
        if (Double.isNaN(n) || n == 0) return 0;
        return Math.floor(n);
    }

    private static final Pattern DECIMAL = Pattern.compile("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

    /** JS Number(v) (없는 값 · 객체 = NaN, null = 0, 참거짓 = 1 · 0, 글자 = 10진수 · 0x 16진수 · Infinity) */
    static double jsNumber(JsonElement v) {
        if (v == null) return Double.NaN;
        if (v.isJsonNull()) return 0;
        if (v.isJsonArray()) {
            JsonArray a = v.getAsJsonArray();
            if (a.isEmpty()) return 0;
            return a.size() == 1 && !a.get(0).isJsonArray() && !a.get(0).isJsonObject() ? jsNumber(new JsonPrimitive(jsString(a.get(0)))) : Double.NaN;
        }
        if (!v.isJsonPrimitive()) return Double.NaN;
        JsonPrimitive p = v.getAsJsonPrimitive();
        if (p.isBoolean()) return p.getAsBoolean() ? 1 : 0;
        if (p.isNumber()) return p.getAsDouble();
        String s = p.getAsString().strip();
        if (s.isEmpty()) return 0;
        if (DECIMAL.matcher(s).matches()) return Double.parseDouble(s);
        if (s.matches("0[xX][0-9a-fA-F]+")) return Long.parseLong(s.substring(2), 16);
        return switch (s) {
            case "Infinity", "+Infinity" -> Double.POSITIVE_INFINITY;
            case "-Infinity" -> Double.NEGATIVE_INFINITY;
            default -> Double.NaN;
        };
    }

    /** JS String(v) (없는 값은 부르는 쪽이 ?? 로) */
    static String jsString(JsonElement v) {
        if (v == null) return "undefined";
        if (v.isJsonNull()) return "null";
        if (v.isJsonPrimitive()) {
            JsonPrimitive p = v.getAsJsonPrimitive();
            if (p.isString()) return p.getAsString();
            if (p.isNumber()) return Js.str(p.getAsDouble());
            return String.valueOf(p.getAsBoolean());
        }
        if (v.isJsonArray()) {
            StringBuilder b = new StringBuilder();
            JsonArray a = v.getAsJsonArray();
            for (int i = 0; i < a.size(); i++) {
                if (i > 0) b.append(',');
                if (!a.get(i).isJsonNull()) b.append(jsString(a.get(i)));
            }
            return b.toString();
        }
        return "[object Object]";
    }

    /** String(v ?? fallback) */
    static String str(JsonObject o, String key, String fallback) {
        JsonElement v = o == null ? null : o.get(key);
        return v == null || v.isJsonNull() ? fallback : jsString(v);
    }

    /** JS 참 (!!v) */
    static boolean truthy(JsonElement v) {
        if (v == null || v.isJsonNull()) return false;
        if (v.isJsonPrimitive()) {
            JsonPrimitive p = v.getAsJsonPrimitive();
            if (p.isBoolean()) return p.getAsBoolean();
            if (p.isNumber()) {
                double d = p.getAsDouble();
                return d != 0 && !Double.isNaN(d);
            }
            return !p.getAsString().isEmpty();
        }
        return true;
    }

    private static JsonObject objOf(JsonElement v) {
        return v != null && v.isJsonObject() ? v.getAsJsonObject() : null;
    }

    private static boolean isString(JsonElement v) {
        return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString();
    }

    /** 만든 퀘스트 다듬기 (quest.js normalizeQuest — 편집 창 · 저장된 정의) */
    public static Quest normalizeQuest(JsonElement raw) {
        return normalizeQuest(raw, false);
    }

    /**
     * quest.js normalizeQuest: 모르는 값은 기본값, 수는 범위 안으로. main = 메인 퀘스트 정의 (메인 전용 목표 종류 try · choose · boss · trial 을 받음).
     * 레벨 · 말 걸기 퀘스트는 반복하면 보상을 무한히 받을 수 있어서 반복 불가
     */
    public static Quest normalizeQuest(JsonElement raw, boolean main) {
        JsonObject q = objOf(raw);
        if (q == null) q = new JsonObject();
        JsonObject r = objOf(q.get("reward"));
        if (r == null) r = new JsonObject();
        Quest n = new Quest();
        JsonObject it = objOf(r.get("item"));
        if (it != null && truthy(it.get("id"))) {
            n.reward.item = new RewardItem(jsString(it.get("id")), Math.max(1, clampInt(it.get("amount"), 6400)), str(it, "name", ""), null);
            if (truthy(it.get("nameTag"))) n.reward.item.nameTag = jsString(it.get("nameTag"));
        }
        String type = str(q, "type", "");
        boolean known = q.has("type") && isString(q.get("type")) && (main ? TYPES.containsKey(type) : TYPE_KEYS.contains(type));
        n.type = known ? type : "kill";
        n.id = str(q, "id", "");
        String title = str(q, "title", "").strip();
        n.title = title.isEmpty() ? "퀘스트" : title;
        n.giver = str(q, "giver", "");
        n.target = str(q, "target", "");
        n.targetName = str(q, "targetName", "");
        n.count = Math.max(1, clampInt(q.get("count"), 10000));
        n.offer = str(q, "offer", "");
        n.progress = str(q, "progress", "");
        n.done = str(q, "done", "");
        n.reward.emerald = clampInt(r.get("emerald"), 6400);
        n.reward.gold = clampInt(r.get("gold"), 1000);
        n.reward.xp = clampInt(r.get("xp"), 1000);
        n.reward.points = clampInt(r.get("points"), 1000);
        n.reward.weapon = str(r, "weapon", "");
        n.repeat = truthy(q.get("repeat")) && (n.type.equals("kill") || n.type.equals("collect"));
        n.requires = str(q, "requires", "");
        n.minLevel = clampInt(q.get("minLevel"), 10000);
        return n;
    }

    /** 애드온에 들어 있는 퀘스트 (메인 · 반복 의뢰 · 왕실 · 전설) 다듬기 (quest.js normalizeBuiltin) */
    public static Quest normalizeBuiltin(JsonObject q) {
        Quest n = normalizeQuest(q, true);
        JsonObject r = objOf(q.get("reward"));
        JsonElement itemsRaw = r == null ? null : r.get("items");
        if (itemsRaw != null && itemsRaw.isJsonArray()) {
            for (JsonElement e : itemsRaw.getAsJsonArray()) {
                JsonObject it = objOf(e);
                if (it == null || !truthy(it.get("id"))) continue;
                n.reward.items.add(new RewardItem(jsString(it.get("id")), Math.max(1, clampInt(it.get("amount"), 6400))));
            }
        }
        String side = str(q, "side", "");
        if (!side.equals("repeat") && !side.equals("royal") && !side.equals("legend")) side = "";
        n.repeat = side.equals("repeat") && (n.type.equals("kill") || n.type.equals("collect"));
        n.reward.xpPoints = clampInt(r == null ? null : r.get("xpPoints"), 100000);
        n.reward.armor = r != null && "path".equals(str(r, "armor", "")) && isString(r.get("armor")) ? "path" : "";
        n.main = side.isEmpty();
        n.side = side;
        String path = isString(q.get("path")) ? q.get("path").getAsString() : "";
        n.path = MainQuests.MAIN_PATHS.containsKey(path) ? path : "";
        double ord = jsNumber(q.get("ord"));
        n.ord = Double.isNaN(ord) ? 0 : ord;
        double step = jsNumber(q.get("step"));
        n.step = Double.isNaN(step) ? 0 : (int) step;
        n.give = new ArrayList<>();
        JsonElement give = q.get("give");
        if (give != null && give.isJsonArray()) {
            for (JsonElement t : give.getAsJsonArray()) if (isString(t) && MainQuests.PATH_ORDER.contains(t.getAsString())) n.give.add(t.getAsString());
        }
        JsonObject doneBy = objOf(q.get("doneBy"));
        if (doneBy != null) {
            n.doneBy = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : doneBy.entrySet()) n.doneBy.put(e.getKey(), jsString(e.getValue()));
        }
        n.legend = truthy(q.get("legend"));
        n.finale = truthy(q.get("finale"));
        double after = jsNumber(q.get("afterStep"));
        n.afterStep = Double.isNaN(after) ? 0 : (int) after;
        String xpMob = isString(q.get("xpMob")) ? q.get("xpMob").getAsString() : "";
        n.xpMob = MONSTERS.objOrNull(xpMob) != null ? xpMob : "";
        n.goals = n.type.equals("trial") ? normalizeGoals(q.get("goals")) : null;
        n.honor = isString(q.get("honor")) ? q.get("honor").getAsString() : "";
        return n;
    }

    /** 전설의 시련 목표 다듬기 (모르는 몬스터 · 보스 · 종류는 뺌 — quest.js normalizeGoals) */
    static List<Goal> normalizeGoals(JsonElement list) {
        List<Goal> out = new ArrayList<>();
        if (list == null || !list.isJsonArray()) return out;
        for (JsonElement e : list.getAsJsonArray()) {
            JsonObject g = objOf(e);
            if (g == null) continue;
            int count = Math.max(1, clampInt(g.get("count"), 10000));
            String kind = str(g, "kind", "");
            if (kind.equals("boss") && isString(g.get("boss")) && BOSSES.objOrNull(g.get("boss").getAsString()) != null) {
                out.add(new Goal("boss", null, g.get("boss").getAsString(), count, null));
            } else if (kind.equals("kill")) {
                List<String> mobs = new ArrayList<>();
                JsonElement ms = g.get("mobs");
                if (ms != null && ms.isJsonArray()) {
                    for (JsonElement m : ms.getAsJsonArray()) if (isString(m) && MONSTERS.objOrNull(m.getAsString()) != null) mobs.add(m.getAsString());
                }
                if (mobs.isEmpty()) continue;
                String label = str(g, "label", "");
                if (label.isEmpty()) {
                    List<String> names = new ArrayList<>();
                    for (String m : mobs) names.add(MONSTERS.obj(m).str("name"));
                    label = String.join(" · ", names);
                }
                out.add(new Goal("kill", List.copyOf(mobs), null, count, label));
            }
        }
        return out;
    }
}
