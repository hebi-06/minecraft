package mmorpg.fabric.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Weapon;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Props;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.ui.Icon;
import mmorpg.fabric.ui.Inv;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

/**
 * 퀘스트 (quest.js 의 중심): 정의 저장소 · 플레이어 진행 · 상태 · 진행 처리 (처치 · 무기 시험 · 보스 · 주기 확인) · NPC 표시 · 길 안내 목적지.
 * - 퀘스트 정의: 세계 속성 "rpg:quest:&lt;번호&gt;" (NPC 편집 창에서 만들고 고침) + 애드온에 들어 있는 퀘스트 (MainQuests "mq_…" · SideQuests "sq_…" — 고칠 수 없음)
 * - 플레이어 진행: 플레이어 속성 "rpg:qs" ({ID: Entry}), 무기의 길 "rpg:main_path", 길 안내 "rpg:nav" ({off, q}) — 죽어도 유지
 * - 목표 종류: kill · collect · talk · level (만든 퀘스트), try · choose · boss (메인), trial (전설)
 * - 완료 보고: 준 NPC 에게 (말 걸기는 대상 NPC 에게) — 대화 · 보상은 QuestTalk · QuestRewards
 * - 길 안내 (nav.js 화면은 따로): navGoal = 지금 갈 곳. 받은 퀘스트를 안내하고, 끝내거나 포기하면 메인 이야기 → 진행 중인 다른 퀘스트
 * 자바에서 달라진 점: 플레이어 객체가 다시 태어나면 바뀌어서 캐시는 UUID 로. 세계 · 서버는 SERVER_STARTING 에 잡아 둠 (애드온의 world).
 */
public final class Quests {
    private Quests() {}

    private static final Rules R = Rules.get();
    private static final Obj MONSTERS = R.obj("MONSTERS");
    private static final Obj BOSSES = R.obj("BOSSES");
    private static final Obj TYPES = R.obj("TYPES");

    /** 목표 종류 이름 · 그림 (quest.js QUEST_TYPES) */
    public static final Map<String, Quest.Type> QUEST_TYPES = Quest.TYPES;
    /** 편집 창에서 고를 수 있는 목표 종류 (quest.js QUEST_TYPE_KEYS) */
    public static final List<String> QUEST_TYPE_KEYS = Quest.TYPE_KEYS;

    static final String Q_PREFIX = "rpg:quest:";
    static final String Q_SEQ = "rpg:quest_seq";
    static final String P_STATE = "rpg:qs";
    static final String P_PATH = "rpg:main_path";
    static final String P_NAV = "rpg:nav";

    /**
     * 엔티티 종류 ID (처치 퀘스트의 target 과 맞춰 봄 — 기본 Kit.typeId: "minecraft:zombie", 5단계 RPG 몬스터는 "rpg:scarecrow" 처럼).
     * 5단계 몬스터가 다른 몸 (바닐라 몹 + 태그) 이면 여기서 그 몬스터의 "rpg:&lt;키&gt;" 를 돌려주게 바꿈
     */
    public static Function<Entity, String> entityType = Kit::typeId;

    private static MinecraftServer server;

    static void setServer(MinecraftServer s) {
        server = s;
        QuestText.npcName = Quests::npcNameByKey;
        QuestText.questById = Quests::getQuest;
    }

    /** 지금 서버 (없으면 null) */
    public static MinecraftServer server() {
        return server;
    }

    /** 서버가 멈추면 (다른 세계를 열 수 있음) 캐시를 모두 비움 */
    static void clear() {
        server = null;
        quests = null;
        invalidate();
        STATES.clear();
        PATHS.clear();
        MAIN_NOTED.clear();
        ROYAL_NOTED.clear();
        LEGEND_NOTED.clear();
        NAVS.clear();
    }

    static void chat(ServerPlayer sp, String text) {
        sp.sendSystemMessage(Component.literal(BedrockText.toJava(text)));
    }

    /** 세운 NPC 의 이름 (세계 기록 — 없으면 "") */
    public static String npcNameByKey(String key) {
        if (server == null || key == null || key.isEmpty()) return "";
        return NpcData.nameByKey(server, key);
    }

    // ------------------------------------------------------------ 퀘스트 정의 저장소

    public static boolean isMainId(String id) {
        return id != null && id.startsWith(MainQuests.MAIN_PREFIX);
    }

    /** 애드온에 들어 있는 퀘스트 ID (메인 mq_ · 반복 의뢰 · 왕실 · 전설 sq_) — 저장 · 삭제로 바꿀 수 없음 */
    public static boolean isBuiltinId(String id) {
        return id != null && (id.startsWith(MainQuests.MAIN_PREFIX) || id.startsWith(SideQuests.SIDE_PREFIX));
    }

    /** 애드온에 들어 있는 퀘스트인지 (편집 창에서 고칠 수 없음, 이름표 [!] 에 세지 않음) */
    public static boolean isBuiltin(Quest q) {
        return q != null && (q.main || !q.side.isEmpty());
    }

    /** 만든 퀘스트 다듬기 (quest.js normalizeQuest) */
    public static Quest normalizeQuest(JsonElement raw) {
        return Quest.normalizeQuest(raw);
    }

    private static List<Quest> mainList;
    private static List<Quest> sideList;
    private static Map<String, Quest> quests;
    private static Map<String, List<Quest>> killIndex;
    private static Map<String, List<Quest>> giverIndex;
    private static Map<String, List<Quest>> talkIndex;
    private static List<Quest> sorted;

    /** 메인 퀘스트 (한 번만 만듦 — 이야기 순서) */
    static List<Quest> mainQuests() {
        if (mainList == null) {
            List<Quest> l = new ArrayList<>();
            for (JsonObject o : MainQuests.buildMainQuests()) l.add(Quest.normalizeBuiltin(o));
            mainList = List.copyOf(l);
        }
        return mainList;
    }

    /** 반복 의뢰 · 왕실 · 전설 퀘스트 (한 번만 만듦) */
    public static List<Quest> sideQuests() {
        if (sideList == null) {
            List<Quest> l = new ArrayList<>();
            for (JsonObject o : SideQuests.buildSideQuests()) l.add(Quest.normalizeBuiltin(o));
            sideList = List.copyOf(l);
        }
        return sideList;
    }

    private static Map<String, Quest> loadQuests() {
        if (quests != null) return quests;
        Map<String, Quest> map = new LinkedHashMap<>();
        for (Quest q : mainQuests()) map.put(q.id, q);
        for (Quest q : sideQuests()) map.put(q.id, q);
        if (server == null) return map; // 서버 전: 들어 있는 퀘스트만 (기억하지 않음)
        for (Map.Entry<String, String> e : Props.worldPrefixed(server, Q_PREFIX).entrySet()) {
            try {
                Quest q = Quest.normalizeQuest(JsonParser.parseString(e.getValue()));
                if (!q.id.isEmpty() && !isBuiltinId(q.id)) map.put(q.id, q);
            } catch (RuntimeException ex) {
                Mmorpg.LOG.warn("[mmorpg] quest {}: {}", e.getKey(), ex.toString());
            }
        }
        quests = map;
        return map;
    }

    private static void invalidate() {
        killIndex = null;
        giverIndex = null;
        talkIndex = null;
        sorted = null;
    }

    /** 메인 퀘스트가 먼저 (이야기 순서), 그다음 반복 의뢰 · 왕실 · 전설, 그다음 만든 퀘스트 (번호 순서) */
    public static int questOrder(Quest a, Quest b) {
        int ra = a.main ? 0 : !a.side.isEmpty() ? 1 : 2;
        int rb = b.main ? 0 : !b.side.isEmpty() ? 1 : 2;
        if (ra != rb) return ra - rb;
        if (ra < 2) {
            int c = Double.compare(a.ord, b.ord);
            return c != 0 ? c : a.id.compareTo(b.id);
        }
        double na = idNumber(a.id);
        double nb = idNumber(b.id);
        // 번호가 아닌 ID (예전 세계 등) 는 번호 뒤에 글자 순서로 (애드온은 NaN — 순서 없음)
        if (!Double.isNaN(na) && !Double.isNaN(nb)) return Double.compare(na, nb);
        if (Double.isNaN(na) != Double.isNaN(nb)) return Double.isNaN(na) ? 1 : -1;
        return a.id.compareTo(b.id);
    }

    private static double idNumber(String id) {
        if (id.length() < 2) return Double.NaN;
        try {
            return Double.parseDouble(id.substring(1));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** 모든 퀘스트 (메인 → 반복 · 왕실 · 전설 → 만든 퀘스트 순서, 복사본 목록) */
    public static List<Quest> allQuests() {
        List<Quest> s = sorted;
        if (s == null) {
            s = new ArrayList<>(loadQuests().values());
            s.sort(Quests::questOrder);
            s = List.copyOf(s);
            if (quests != null) sorted = s;
        }
        return new ArrayList<>(s);
    }

    /** ID 의 퀘스트 (없으면 null) */
    public static Quest getQuest(String id) {
        return id == null ? null : loadQuests().get(id);
    }

    private static void indexes() {
        if (giverIndex != null && talkIndex != null) return;
        Map<String, List<Quest>> g = new HashMap<>();
        Map<String, List<Quest>> t = new HashMap<>();
        for (Quest q : allQuests()) {
            g.computeIfAbsent(q.giver, k -> new ArrayList<>()).add(q);
            if (q.type.equals("talk") && !q.target.isEmpty()) t.computeIfAbsent(q.target, k -> new ArrayList<>()).add(q);
        }
        giverIndex = g;
        talkIndex = t;
    }

    /** 이 NPC 가 주는 퀘스트 (순서대로) */
    public static List<Quest> questsOf(String giverKey) {
        if (giverKey == null || giverKey.isEmpty()) return new ArrayList<>();
        indexes();
        List<Quest> l = giverIndex.getOrDefault(giverKey, List.of());
        if (quests == null) invalidate();
        return new ArrayList<>(l);
    }

    /** 이 NPC 에게 말 걸기 퀘스트 */
    static List<Quest> talkTargetsOf(String key) {
        if (key == null || key.isEmpty()) return List.of();
        indexes();
        List<Quest> l = talkIndex.getOrDefault(key, List.of());
        if (quests == null) invalidate();
        return l;
    }

    /** 저장하고 저장된 퀘스트를 돌려줌. 번호가 없으면 새로 붙임. 애드온에 들어 있는 퀘스트는 바꾸지 않음 (그대로 돌려줌) */
    public static Quest saveQuest(Quest q) {
        Map<String, Quest> map = loadQuests();
        if (q != null && isBuiltinId(q.id)) return map.get(q.id);
        if (server == null) throw new IllegalStateException("quest: no server");
        Quest nq = Quest.normalizeQuest(q == null ? new JsonObject() : q.toJson());
        if (nq.id.isEmpty()) {
            int n = (int) Props.worldNum(server, Q_SEQ, 0) + 1;
            Props.setWorld(server, Q_SEQ, String.valueOf(n));
            nq.id = "q" + n;
        }
        Props.setWorld(server, Q_PREFIX + nq.id, nq.toJson().toString());
        map.put(nq.id, nq);
        invalidate();
        return nq;
    }

    /** 지움 (애드온에 들어 있는 퀘스트는 지우지 않음) */
    public static void deleteQuest(String id) {
        if (id == null || isBuiltinId(id) || server == null) return;
        Props.setWorld(server, Q_PREFIX + id, null);
        loadQuests().remove(id);
        invalidate();
    }

    /** 처치 퀘스트의 대상 키: 같은 몹의 예전 · 새 ID (_v2), 베드락 · 자바 이름 (zombie_pigman → zombified_piglin) 을 하나로 */
    static String killKey(String id) {
        return Names.mobKey(Kit.javaType(Names.mobKey(id)));
    }

    private static Map<String, List<Quest>> kills() {
        Map<String, List<Quest>> idx = killIndex;
        if (idx == null) {
            idx = new HashMap<>();
            for (Quest q : loadQuests().values()) if (q.type.equals("kill")) idx.computeIfAbsent(killKey(q.target), k -> new ArrayList<>()).add(q);
            if (quests != null) killIndex = idx;
        }
        return idx;
    }

    // ------------------------------------------------------------ 플레이어 진행

    private static final Map<UUID, Map<String, Quest.Entry>> STATES = new HashMap<>();
    /** 무기의 길 (메인 퀘스트) */
    private static final Map<UUID, String> PATHS = new HashMap<>();
    /** 메인 "새 이야기" 알림을 한 퀘스트 (같은 퀘스트는 한 번) */
    private static final Map<UUID, String> MAIN_NOTED = new HashMap<>();
    /** 왕실 "새 부탁" 알림을 한 퀘스트 */
    private static final Map<UUID, String> ROYAL_NOTED = new HashMap<>();
    /** 전설 "열림" 알림을 한 퀘스트 */
    private static final Map<UUID, Set<String>> LEGEND_NOTED = new HashMap<>();
    /** 길 안내 설정 */
    private static final Map<UUID, Nav> NAVS = new HashMap<>();

    /** 길 안내 설정: off = 껐음 (/nav), q = 안내할 퀘스트 (받은 퀘스트 · 퀘스트 목록에서 고른 퀘스트, 없으면 "") */
    public record Nav(boolean off, String q) {}

    /** 플레이어 진행 ({퀘스트 ID: 기록} — 바꾼 뒤 saveState). 처음 읽을 때 예전 (18단계) 메인 진행을 옮김 */
    public static Map<String, Quest.Entry> stateOf(ServerPlayer sp) {
        Map<String, Quest.Entry> cached = STATES.get(sp.getUUID());
        if (cached != null) return cached;
        Map<String, Quest.Entry> st = Quest.parseState(Props.get(sp, P_STATE));
        STATES.put(sp.getUUID(), st);
        try {
            if (MainQuests.migrateMainState(st, pathOf(sp))) saveState(sp);
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[mmorpg] main quest migration", e);
        }
        return st;
    }

    /** 진행 저장 (플레이어 속성 — 죽어도 유지) */
    public static void saveState(ServerPlayer sp) {
        Props.set(sp, P_STATE, Quest.stateJson(stateOf(sp)));
    }

    /** 메인 퀘스트에서 고른 무기의 길 (무기 종류, 아직 안 골랐으면 "") */
    public static String pathOf(ServerPlayer sp) {
        String cached = PATHS.get(sp.getUUID());
        if (cached != null) return cached;
        String raw = Props.get(sp, P_PATH);
        String v = raw != null && MainQuests.isPath(raw) ? raw : "";
        PATHS.put(sp.getUUID(), v);
        return v;
    }

    static void setPath(ServerPlayer sp, String type) {
        String t = type == null ? "" : type;
        PATHS.put(sp.getUUID(), t);
        Props.set(sp, P_PATH, t.isEmpty() ? null : t);
    }

    /** 나간 플레이어의 캐시를 지움 (진행은 플레이어 속성에 그대로) */
    public static void forgetQuestPlayer(UUID id) {
        STATES.remove(id);
        PATHS.remove(id);
        MAIN_NOTED.remove(id);
        ROYAL_NOTED.remove(id);
        LEGEND_NOTED.remove(id);
        NAVS.remove(id);
    }

    /** 퀘스트 기록 초기화 (시험용 /questreset): 무기의 길 · 길 안내로 고른 퀘스트도 지움 (켜고 끈 것은 그대로) */
    public static void resetQuests(ServerPlayer sp) {
        STATES.put(sp.getUUID(), new LinkedHashMap<>());
        saveState(sp);
        setPath(sp, "");
        MAIN_NOTED.remove(sp.getUUID());
        ROYAL_NOTED.remove(sp.getUUID());
        LEGEND_NOTED.remove(sp.getUUID());
        pinQuest(sp, "");
    }

    // ------------------------------------------------------------ 길 안내 설정

    /** 길 안내 설정 */
    public static Nav navOf(ServerPlayer sp) {
        Nav cached = NAVS.get(sp.getUUID());
        if (cached != null) return cached;
        Nav v = new Nav(false, "");
        String raw = Props.get(sp, P_NAV);
        if (raw != null) {
            try {
                JsonElement e = JsonParser.parseString(raw);
                if (e.isJsonObject()) {
                    JsonObject o = e.getAsJsonObject();
                    JsonElement q = o.get("q");
                    v = new Nav(Quest.truthy(o.get("off")), q != null && q.isJsonPrimitive() && q.getAsJsonPrimitive().isString() ? q.getAsString() : "");
                }
            } catch (RuntimeException ex) {
                v = new Nav(false, "");
            }
        }
        NAVS.put(sp.getUUID(), v);
        return v;
    }

    private static Consumer<ServerPlayer> navChanged = p -> {};

    /** 갈 곳이 바뀔 일이 생기면 (길 안내 화면이 등록: 다음 틱에 바로 다시 정함) */
    public static void onNavChange(Consumer<ServerPlayer> fn) {
        navChanged = fn == null ? p -> {} : fn;
    }

    static void navNow(ServerPlayer sp) {
        try {
            navChanged.accept(sp);
        } catch (RuntimeException e) {
            // 무시
        }
    }

    private static void saveNav(ServerPlayer sp, Nav v) {
        NAVS.put(sp.getUUID(), v);
        String json = null;
        if (v.off() || !v.q().isEmpty()) {
            JsonObject o = new JsonObject();
            o.addProperty("off", v.off() ? 1 : 0);
            o.addProperty("q", v.q());
            json = o.toString();
        }
        Props.set(sp, P_NAV, json);
        navNow(sp);
    }

    /** 길 안내 켜기 · 끄기 */
    public static void setNavOff(ServerPlayer sp, boolean off) {
        saveNav(sp, new Nav(off, navOf(sp).q()));
    }

    /** 이 퀘스트를 안내 ("" = 고른 퀘스트 없음: 메인 이야기 → 진행 중인 다른 퀘스트) */
    public static void pinQuest(ServerPlayer sp, String id) {
        saveNav(sp, new Nav(navOf(sp).off(), id == null ? "" : id));
    }

    /** 고른 퀘스트를 끝내거나 포기했으면 고른 것을 풂 */
    public static void unpin(ServerPlayer sp, String id) {
        if (navOf(sp).q().equals(id)) pinQuest(sp, "");
    }

    // ------------------------------------------------------------ 진행도 · 상태

    /** 전설의 시련: 목표마다 진행 (받지 않았으면 모두 0) */
    public static List<Integer> trialCounts(ServerPlayer sp, Quest q) {
        Quest.Entry e = stateOf(sp).get(q.id);
        List<Integer> out = new ArrayList<>();
        int n = q.goals == null ? 0 : q.goals.size();
        for (int i = 0; i < n; i++) out.add(Math.max(0, e != null && e.o != null && i < e.o.size() && e.o.get(i) != null ? e.o.get(i) : 0));
        return out;
    }

    /** 지금 진행도 (처치 수 / 가진 아이템 수 / 레벨 / 써 본 무기 종류 수 / 전설의 시련: 목표마다 채운 수의 합) */
    public static double progressOf(ServerPlayer sp, Quest q) {
        Quest.Entry e = stateOf(sp).get(q.id);
        switch (q.type) {
            case "kill", "boss" -> {
                return e == null ? 0 : e.n;
            }
            case "trial" -> {
                List<Integer> c = trialCounts(sp, q);
                int n = 0;
                for (int i = 0; i < c.size(); i++) n += Math.min(c.get(i), q.goals.get(i).count());
                return n;
            }
            case "try" -> {
                return e == null || e.t == null ? 0 : e.t.size();
            }
            case "collect" -> {
                return Inv.count(sp, Icon.itemById(q.target));
            }
            case "level" -> {
                return questLevel(sp);
            }
            default -> {
                return 0;
            }
        }
    }

    /** 목표를 채웠는지 (말 걸기 · 무기 고르기는 보고 · 고르기로만) */
    public static boolean isReady(ServerPlayer sp, Quest q) {
        if (q.type.equals("talk") || q.type.equals("choose")) return false;
        return progressOf(sp, q) >= q.count;
    }

    /** 퀘스트 조건의 레벨 = 경험치 레벨의 최고 기록 (죽어서 경험치를 잃어도 받은 이야기가 잠기지 않게 — 무기 렙제와 같음) */
    public static double questLevel(ServerPlayer sp) {
        return RpgCombat.reqLevelOf(sp);
    }

    /** 먼저 끝낼 퀘스트를 끝냈는지 (무기 고르기는 길이 정해져 있으면 끝낸 것). 반복 의뢰 · 전설: 내 길의 메인 afterStep 단계를 끝냈는지 */
    static boolean requiresMet(ServerPlayer sp, Quest q) {
        if (q.afterStep != 0) {
            String path = pathOf(sp);
            Map<String, Quest.Entry> st = stateOf(sp);
            if (path.isEmpty()) return false;
            Quest.Entry at = st.get(MainQuests.mainId(path, q.afterStep));
            // 예전 (18단계) 에서 그 몬스터 처치 단계를 끝냈으면 반복 의뢰는 그대로 열림
            if (!(at != null && at.c > 0 || (q.side.equals("repeat") && MainQuests.oldKillDone(st, path, q.xpMob)))) return false;
        }
        if (q.requires.isEmpty() || getQuest(q.requires) == null) return true;
        Quest.Entry r = stateOf(sp).get(q.requires);
        if (r != null && r.c > 0) return true;
        return q.requires.equals(MainQuests.MAIN_CHOOSE) && !pathOf(sp).isEmpty();
    }

    /** 받을 수 없는 까닭: "path" (다른 무기의 길 · 이미 고름) · "requires" (앞 퀘스트) · "level" · "" (받을 수 있음) — 레벨은 마지막 */
    public static String lockReason(ServerPlayer sp, Quest q) {
        if ((q.main || q.side.equals("legend")) && !q.path.isEmpty() && !pathOf(sp).equals(q.path)) return "path";
        if (q.type.equals("choose") && !pathOf(sp).isEmpty()) return "path";
        if (!requiresMet(sp, q)) return "requires";
        if (q.minLevel > 0 && questLevel(sp) < q.minLevel) return "level";
        return "";
    }

    /** "locked" · "available" · "active" · "ready" · "done" */
    public static String statusOf(ServerPlayer sp, Quest q) {
        Quest.Entry e = stateOf(sp).get(q.id);
        if (q.type.equals("choose") && (!pathOf(sp).isEmpty() || (e != null && e.c > 0))) return "done";
        if (e != null && e.active()) return isReady(sp, q) ? "ready" : "active";
        if (e != null && e.c > 0 && !q.repeat) return "done";
        return lockReason(sp, q).isEmpty() ? "available" : "locked";
    }

    /** 메인 퀘스트 목록 (그 길 또는 프롤로그, 이야기 순서) */
    static List<Quest> mainChain(String path) {
        List<Quest> out = new ArrayList<>();
        for (Quest q : mainQuests()) if (q.path.equals(path)) out.add(q);
        return out;
    }

    /** 이 플레이어의 다음 메인 퀘스트 (진행 중이거나 아직 끝내지 않은 첫 퀘스트, 모두 끝냈으면 null) */
    public static Quest nextMain(ServerPlayer sp) {
        for (Quest q : mainChain(pathOf(sp))) if (!statusOf(sp, q).equals("done")) return q;
        return null;
    }

    /** 메인 이야기를 끝까지 마쳤는지 */
    public static boolean mainFinished(ServerPlayer sp) {
        String path = pathOf(sp);
        Quest last = path.isEmpty() ? null : getQuest(MainQuests.mainId(path, MainQuests.PATH_STEPS));
        return last != null && statusOf(sp, last).equals("done");
    }

    /** 왕실 퀘스트: 다음 단계 (모두 끝냈으면 null) */
    public static Quest nextRoyal(ServerPlayer sp) {
        for (Quest q : sideQuests()) if (q.side.equals("royal") && !statusOf(sp, q).equals("done")) return q;
        return null;
    }

    /** 왕실 퀘스트를 끝까지 마쳤는지 */
    public static boolean royalFinished(ServerPlayer sp) {
        boolean any = false;
        for (Quest q : sideQuests()) if (q.side.equals("royal")) any = true;
        return any && nextRoyal(sp) == null;
    }

    // ------------------------------------------------------------ 글자 (편집 창 · 대화가 씀 — QuestText)

    public static String giverName(Quest q) {
        return QuestText.giverName(q);
    }

    public static String objectiveText(Quest q) {
        return QuestText.objectiveText(q);
    }

    public static String objectiveText(Quest q, Double n) {
        return QuestText.objectiveText(q, n);
    }

    public static String rewardText(Quest q) {
        return QuestText.rewardText(q);
    }

    public static String rewardText(Quest q, Quest.Reward rw) {
        return QuestText.rewardText(q, rw);
    }

    public static String conditionText(Quest q) {
        return QuestText.conditionText(q);
    }

    /** 진행을 붙인 목표 글자 (말 걸기는 진행 없음) */
    public static String objectiveOf(ServerPlayer sp, Quest q) {
        if (q.type.equals("trial")) return QuestText.trialText(q, trialCounts(sp, q));
        return QuestText.objectiveText(q, q.type.equals("talk") ? null : progressOf(sp, q));
    }

    // ------------------------------------------------------------ 진행 처리

    /** 받기 */
    static void accept(ServerPlayer sp, Quest q) {
        Map<String, Quest.Entry> st = stateOf(sp);
        Quest.Entry prev = st.get(q.id);
        Quest.Entry e = new Quest.Entry("a", 0, prev != null ? prev.c : 0);
        if (q.type.equals("try")) e.t = new ArrayList<>();
        if (q.type.equals("trial")) {
            e.o = new ArrayList<>();
            for (int i = 0; i < (q.goals == null ? 0 : q.goals.size()); i++) e.o.add(0);
        }
        st.put(q.id, e);
        saveState(sp);
        if (!q.give.isEmpty()) QuestRewards.giveTrialWeapons(sp, q.give);
        pinQuest(sp, q.id); // 길 안내: 받은 퀘스트를 안내
        Notify.notify(sp, QuestText.noticeTag(q) + "퀘스트 수락 §r§f" + q.title + " §7- " + QuestText.objectiveText(q));
        SkillFx.playFor(sp, "random.orb", 0.9, 1);
        QuestFx.sparkle(sp, "minecraft:villager_happy");
    }

    /** 포기 (끝낸 적이 있으면 완료로 되돌림, 무기 시험이면 빌린 무기도 돌려받음) */
    public static void abandon(ServerPlayer sp, Quest q) {
        Map<String, Quest.Entry> st = stateOf(sp);
        Quest.Entry cur = st.get(q.id);
        int c = cur != null ? cur.c : 0;
        if (c > 0) st.put(q.id, new Quest.Entry("d", 0, c));
        else st.remove(q.id);
        saveState(sp);
        unpin(sp, q.id);
        if (!q.give.isEmpty()) QuestRewards.takeBackTrials(sp);
    }

    /** 완료 처리. 가져올 아이템이 모자라면 false. reward = 실제로 주는 보상 (고르기는 고른 무기) */
    static boolean finish(ServerPlayer sp, Quest q, Quest.Reward reward) {
        if (q.type.equals("collect") && !Inv.remove(sp, Icon.itemById(q.target), q.count)) return false;
        QuestRewards.giveRewards(sp, reward);
        // 받은 무기를 아직 쓸 수 없으면 채팅에도 남김 (완료 대사를 닫아 완료 창을 보지 못해도)
        String lvLine = QuestRewards.rewardLevelLine(sp, reward);
        if (!lvLine.isEmpty()) chat(sp, "§6[보상] §r" + lvLine);
        String armorLine = QuestRewards.rewardArmorLine(sp, reward);
        if (!armorLine.isEmpty()) chat(sp, "§6[보상] §r" + armorLine);
        Map<String, Quest.Entry> st = stateOf(sp);
        Quest.Entry prev = st.get(q.id);
        st.put(q.id, new Quest.Entry("d", 0, (prev != null ? prev.c : 0) + 1));
        saveState(sp);
        unpin(sp, q.id);
        navNow(sp);
        Notify.notify(sp, (QuestText.tagOf(q).isEmpty() ? "§6§l퀘스트 완료!" : QuestText.noticeTag(q) + "퀘스트 완료!") + " §r§f" + q.title);
        SkillFx.playFor(sp, "random.levelup", 1.1, 1);
        QuestFx.sparkle(sp, "minecraft:totem_particle");
        try {
            if (q.legend) QuestFx.legendFx(sp, reward, q);
            if (q.finale && q.side.equals("royal")) QuestFx.royalFx(sp, reward);
            else if (q.finale) QuestFx.finaleFx(sp);
            if (q.main && q.type.equals("kill")) repeatOpened(sp, q);
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[mmorpg] main quest fx", e);
        }
        return true;
    }

    /** 메인 처치 단계를 끝내면 그 몬스터의 반복 의뢰가 열림 → 채팅으로 한 번 (잉그리드를 세운 적이 있을 때) */
    private static void repeatOpened(ServerPlayer sp, Quest q) {
        if (npcNameByKey(SideQuests.REPEAT_GIVER).isEmpty()) return;
        for (Quest r : sideQuests()) {
            if (!r.side.equals("repeat") || r.afterStep != q.step || !statusOf(sp, r).equals("available")) continue;
            chat(sp, "§b[반복 의뢰] §f" + giverName(r) + "§7에게 §f" + r.title + "§7 의뢰가 열렸습니다 §8(" + objectiveText(r) + " · 몇 번이든)");
        }
    }

    /** 목표 달성 알림 */
    static void notifyReady(ServerPlayer sp, Quest q) {
        navNow(sp); // 길 안내: 이제 준 NPC 에게
        String where = q.type.equals("talk") ? "" : " §7- " + giverName(q) + "에게 돌아가세요";
        Notify.notify(sp, "§a§l퀘스트 목표 달성! §r§f" + q.title + where, 80);
        SkillFx.playFor(sp, "random.levelup", 1.4, 0.8);
        QuestFx.sparkle(sp, "minecraft:totem_particle");
    }

    /** 진행 중인 퀘스트와 그 기록 */
    record Active(Quest q, Quest.Entry e) {}

    /** 진행 중인 퀘스트 중 이 종류 */
    static List<Active> activeOfType(ServerPlayer sp, String type) {
        List<Active> out = new ArrayList<>();
        for (Map.Entry<String, Quest.Entry> en : stateOf(sp).entrySet()) {
            if (!en.getValue().active()) continue;
            Quest q = getQuest(en.getKey());
            if (q != null && q.type.equals(type)) out.add(new Active(q, en.getValue()));
        }
        return out;
    }

    /** 몹이 죽음 (AFTER_DEATH): 잡은 사람 = Loot.killCredit → 처치 퀘스트 · 전설의 시련 (RPG 몬스터만) */
    public static void onEntityDied(LivingEntity dead, DamageSource source) {
        ServerPlayer player = Loot.killCredit(dead, source);
        if (player == null) return;
        String typeId;
        try {
            typeId = entityType.apply(dead);
        } catch (RuntimeException e) {
            return;
        }
        if (typeId == null) return;
        List<Quest> list = kills().getOrDefault(killKey(typeId), List.of());
        String mob = Names.shortId(typeId);
        List<Active> trials = typeId.startsWith("rpg:") && MONSTERS.objOrNull(mob) != null ? activeOfType(player, "trial") : List.of();
        if (list.isEmpty() && trials.isEmpty()) return;
        Map<String, Quest.Entry> st = stateOf(player);
        boolean changed = false;
        for (Active a : trials) if (trialStep(player, a.q(), a.e(), g -> g.kind().equals("kill") && g.mobs() != null && g.mobs().contains(mob))) changed = true;
        for (Quest q : list) {
            Quest.Entry e = st.get(q.id);
            if (e == null || !e.active() || e.n >= q.count) continue;
            e.n++;
            changed = true;
            if (e.n >= q.count) notifyReady(player, q);
            else {
                Notify.notify(player, "§e[퀘스트] §f" + q.title + " §7· §f" + QuestText.targetLabel(q) + " §e" + e.n + "/" + q.count);
                SkillFx.playFor(player, "random.orb", 0.7 + 0.6 * e.n / q.count, 0.5);
            }
        }
        if (changed) saveState(player);
    }

    /** 전설의 시련: 맞는 목표 하나를 채움 (덜 찬 것 중 첫 목표). 채웠으면 true (저장은 부른 쪽) */
    static boolean trialStep(ServerPlayer sp, Quest q, Quest.Entry e, Predicate<Quest.Goal> match) {
        List<Quest.Goal> goals = q.goals == null ? List.of() : q.goals;
        List<Integer> o = new ArrayList<>();
        for (int i = 0; i < goals.size(); i++) o.add(Math.max(0, e.o != null && i < e.o.size() && e.o.get(i) != null ? e.o.get(i) : 0));
        int idx = -1;
        for (int k = 0; k < goals.size(); k++) {
            if (match.test(goals.get(k)) && o.get(k) < goals.get(k).count()) {
                idx = k;
                break;
            }
        }
        if (idx < 0) return false;
        o.set(idx, o.get(idx) + 1);
        e.o = o;
        int n = 0;
        for (int k = 0; k < goals.size(); k++) n += Math.min(o.get(k), goals.get(k).count());
        e.n = n;
        if (e.n >= q.count) notifyReady(sp, q);
        else {
            Notify.notify(sp, "§c[전설] §f" + q.title + " §7· §f" + QuestText.trialText(q, o));
            SkillFx.playFor(sp, "random.orb", 0.7 + 0.6 * e.n / q.count, 0.5);
        }
        return true;
    }

    /** RPG 무기로 몹을 맞힘 (평타 · 활 · 완드 피해가 실제로 들어감 — RpgCombat 훅) → 무기 시험 (길이 있는 여섯 종류만) */
    public static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w) {
        if (w == null || w.type() == null || sp == null || sp.isRemoved()) return;
        if (victim == null || victim.isRemoved() || victim instanceof Player || NpcData.isNpcEntity(victim) || victim.entityTags().contains(NpcData.TAG)) return;
        List<Active> list = activeOfType(sp, "try");
        if (list.isEmpty()) return;
        boolean changed = false;
        for (Active a : list) {
            Quest q = a.q();
            Quest.Entry e = a.e();
            if (e.t == null) e.t = new ArrayList<>();
            if (e.t.contains(w.type()) || e.t.size() >= q.count || !MainQuests.PATH_ORDER.contains(w.type())) continue;
            e.t.add(w.type());
            e.n = e.t.size();
            changed = true;
            if (e.t.size() >= q.count) notifyReady(sp, q);
            else {
                Notify.notify(sp, "§e[퀘스트] §f" + q.title + " §7· §a" + TYPES.obj(w.type()).str("name") + " 손맛 확인 §e" + e.t.size() + "/" + q.count);
                SkillFx.playFor(sp, "random.orb", 0.8 + 0.6 * e.t.size() / q.count, 0.6);
            }
        }
        if (changed) saveState(sp);
    }

    /** 보스를 쓰러뜨림 (5단계 보스가 부름: 보상을 받은 사람 모두) → 보스 처치 · 전설의 시련 보스 목표. key = 보스 키 (BOSSES) */
    public static void onBossKilled(String key, List<ServerPlayer> players) {
        if (players == null) return;
        for (ServerPlayer p : players) {
            if (p == null || p.isRemoved()) continue;
            boolean changed = false;
            for (Active a : activeOfType(p, "boss")) {
                Quest q = a.q();
                Quest.Entry e = a.e();
                if (!q.target.equals(key) || e.n >= q.count) continue;
                e.n++;
                changed = true;
                if (e.n >= q.count) notifyReady(p, q);
                else Notify.notify(p, "§e[퀘스트] §f" + q.title + " §7· §f" + QuestText.targetLabel(q) + " §e" + e.n + "/" + q.count);
            }
            for (Active a : activeOfType(p, "trial")) if (trialStep(p, a.q(), a.e(), g -> g.kind().equals("boss") && key.equals(g.boss()))) changed = true;
            if (changed) saveState(p);
        }
    }

    /** 아이템 · 레벨 퀘스트 달성 알림 + 메인 · 전설 · 왕실 새 이야기 알림 (2초마다) */
    public static void questTick(ServerPlayer sp) {
        Map<String, Quest.Entry> st = stateOf(sp);
        boolean changed = false;
        for (Map.Entry<String, Quest.Entry> en : st.entrySet()) {
            Quest.Entry e = en.getValue();
            if (!e.active()) continue;
            Quest q = getQuest(en.getKey());
            if (q == null || (!q.type.equals("collect") && !q.type.equals("level"))) continue;
            boolean ready = isReady(sp, q);
            boolean noted = e.r != null && e.r != 0;
            if (ready && !noted) {
                e.r = 1;
                changed = true;
                notifyReady(sp, q);
            } else if (!ready && noted) {
                e.r = 0;
                changed = true;
            }
        }
        if (changed) saveState(sp);
        mainTick(sp);
    }

    /** 다음 메인 퀘스트를 받을 수 있게 되면 (레벨이 오르는 등) 한 번 알림. 그 NPC 를 세운 적이 없으면 알리지 않음 */
    private static void mainTick(ServerPlayer sp) {
        UUID id = sp.getUUID();
        Quest nx = nextMain(sp);
        if (nx != null && !npcNameByKey(nx.giver).isEmpty() && statusOf(sp, nx).equals("available") && !nx.id.equals(MAIN_NOTED.get(id))) {
            MAIN_NOTED.put(id, nx.id);
            Notify.notify(sp, "§6§l[메인] §r§f" + giverName(nx) + "에게 새 이야기가 있습니다 §7- " + nx.title, 80);
            SkillFx.playFor(sp, "random.orb", 0.8, 0.7);
            return;
        }
        // 전설 퀘스트: 받을 수 있게 되면 한 번 (그 스승을 세운 적이 있을 때만 — 초월 보스 전설은 길을 보지 않음)
        String path = pathOf(sp);
        if (!path.isEmpty()) {
            Set<String> noted = LEGEND_NOTED.getOrDefault(id, new HashSet<>());
            for (Quest lq : sideQuests()) {
                if (!lq.side.equals("legend") || (!lq.path.isEmpty() && !lq.path.equals(path)) || noted.contains(lq.id) || npcNameByKey(lq.giver).isEmpty()
                        || !statusOf(sp, lq).equals("available")) continue;
                noted.add(lq.id);
                LEGEND_NOTED.put(id, noted);
                Notify.notify(sp, "§c§l[전설] §r§f" + giverName(lq) + "에게 전설 퀘스트가 있습니다 §7- " + lq.title, 80);
                SkillFx.playFor(sp, "random.orb", 0.7, 0.8);
                return;
            }
        }
        // 왕실 퀘스트: 다음 단계를 받을 수 있게 되면 한 번 (왕녀를 세운 적이 있을 때만)
        Quest rq = nextRoyal(sp);
        if (rq == null || npcNameByKey(rq.giver).isEmpty() || !statusOf(sp, rq).equals("available") || rq.id.equals(ROYAL_NOTED.get(id))) return;
        ROYAL_NOTED.put(id, rq.id);
        Notify.notify(sp, "§d§l[왕실] §r§f" + giverName(rq) + "에게 새 부탁이 있습니다 §7- " + rq.title, 80);
        SkillFx.playFor(sp, "random.orb", 1.0, 0.7);
    }

    // ------------------------------------------------------------ NPC 표시 (Npcs 훅)

    /**
     * 이름표 [!] 에 셀 퀘스트 수: 편집 창으로 만든 퀘스트만 (메인 · 반복 · 왕실 · 전설은 사람마다 달라 모두에게 [!] 를 띄우지 않고,
     * 받을 수 있는 사람에게만 머리 위 파티클 · 접근 안내로 알림)
     */
    public static int badgeCount(String key) {
        int n = 0;
        for (Quest q : questsOf(key)) if (!isBuiltin(q)) n++;
        return n;
    }

    /** 이 NPC 와 관련된 퀘스트 (주는 퀘스트 또는 말 걸기 대상) 가 있는지 */
    public static boolean hasQuestFor(String key) {
        if (key == null || key.isEmpty()) return false;
        return !questsOf(key).isEmpty() || !talkTargetsOf(key).isEmpty();
    }

    /** NPC 머리 위 표시: "ready" (보고할 퀘스트) · "available" (받을 퀘스트) · "repeat" (받을 것이 반복 의뢰뿐) · null */
    public static String questMark(ServerPlayer sp, String key) {
        if (key == null || key.isEmpty()) return null;
        for (Quest q : talkTargetsOf(key)) {
            Quest.Entry e = stateOf(sp).get(q.id);
            if (!q.giver.equals(key) && e != null && e.active()) return "ready";
        }
        String mark = "";
        for (Quest q : questsOf(key)) {
            String s = statusOf(sp, q);
            if (s.equals("ready")) return "ready";
            if (s.equals("available")) mark = q.repeat && !mark.equals("available") ? "repeat" : "available";
        }
        return mark.isEmpty() ? null : mark;
    }

    // ------------------------------------------------------------ 길 안내 목적지 (nav.js 가 그림)

    /**
     * 지금 가야 할 곳. why: turnin (준 NPC 에게 보고) · talk (그 NPC 에게) · offer (다음 메인 이야기를 줄 NPC) · hunt (몬스터 처치 → 그 몬스터 사냥터) ·
     * boss (보스 처치 → 그 보스 제단) · try (무기 시험 → 허수아비) · collect (모으기 — 전리품이면 그 몬스터 사냥터) · level (레벨이 모자람 → 레벨에 맞는 사냥터).
     * npc = NPC 번호, mob = 몬스터 키 (RPG 몬스터만), mobs = 전설의 시련 — 이 몬스터들 중 가장 가까운 사냥터, boss = 보스 키, what = 대상 이름,
     * n · count = 진행 (count 0 = 진행 없음), lv = 필요 레벨. 없는 글자는 null, mobs 는 없으면 빈 목록
     */
    public record NavGoal(Quest q, boolean main, String why, String npc, String mob, List<String> mobs, String boss, String what, int n, int count, int lv) {}

    private static NavGoal goal(Quest q, String why, String npc) {
        return new NavGoal(q, q.main, why, npc, null, List.of(), null, null, 0, 0, 0);
    }

    /** 메인 이야기를 시작했는지 (할아버지 하랄의 첫 퀘스트를 받았거나 길을 골랐음) */
    static boolean mainStarted(ServerPlayer sp) {
        return !pathOf(sp).isEmpty() || stateOf(sp).containsKey(MainQuests.MAIN_START);
    }

    /** 퀘스트 → 갈 곳 (갈 곳이 없으면 null) */
    public static NavGoal goalOf(ServerPlayer sp, Quest q) {
        String s = statusOf(sp, q);
        boolean main = q.main;
        if (s.equals("ready")) return goal(q, "turnin", q.giver);
        if (s.equals("active")) {
            if (q.type.equals("talk")) return goal(q, "talk", q.target);
            int n = (int) progressOf(sp, q);
            switch (q.type) {
                case "kill" -> {
                    String k = Names.shortId(q.target);
                    String mob = q.target.startsWith("rpg:") && MONSTERS.objOrNull(k) != null ? k : null;
                    return new NavGoal(q, main, "hunt", null, mob, List.of(), null, QuestText.targetLabel(q), n, q.count, 0);
                }
                case "boss" -> {
                    return new NavGoal(q, main, "boss", null, null, List.of(), q.target, QuestText.targetLabel(q), n, q.count, 0);
                }
                case "trial" -> {
                    List<Integer> o = trialCounts(sp, q);
                    List<Quest.Goal> goals = q.goals == null ? List.of() : q.goals;
                    for (int i = 0; i < goals.size(); i++) {
                        Quest.Goal g = goals.get(i);
                        if (o.get(i) >= g.count()) continue;
                        if (g.kind().equals("boss")) {
                            Obj b = BOSSES.objOrNull(g.boss());
                            return new NavGoal(q, main, "boss", null, null, List.of(), g.boss(), b != null ? b.str("name") : g.boss(), o.get(i), g.count(), 0);
                        }
                        return new NavGoal(q, main, "hunt", null, null, g.mobs() == null ? List.of() : g.mobs(), null, g.label(), o.get(i), g.count(), 0);
                    }
                    return null;
                }
                case "try" -> {
                    return new NavGoal(q, main, "try", null, R.strings("MONSTER_ORDER").get(0), List.of(), null, "무기 시험", n, q.count, 0);
                }
                case "collect" -> {
                    return new NavGoal(q, main, "collect", null, Loot.dropKeyOf(q.target), List.of(), null, QuestText.targetLabel(q), n, q.count, 0);
                }
                case "level" -> {
                    return new NavGoal(q, main, "level", null, Loot.huntKind(questLevel(sp)), List.of(), null, null, 0, 0, q.count);
                }
                default -> {
                    return null;
                }
            }
        }
        // 메인 이야기의 다음 단계: 받을 수 있으면 준 NPC 에게, 레벨만 모자라면 레벨에 맞는 사냥터로
        // (이야기를 시작하지 않았으면 그 NPC 를 세운 적이 있을 때만 — 메인 NPC 가 없는 세계에서는 안내하지 않음)
        if (!main || (!mainStarted(sp) && npcNameByKey(q.giver).isEmpty())) return null;
        if (s.equals("available")) return goal(q, "offer", q.giver);
        if (s.equals("locked") && lockReason(sp, q).equals("level")) {
            return new NavGoal(q, main, "level", null, Loot.huntKind(questLevel(sp)), List.of(), null, null, 0, 0, q.minLevel);
        }
        return null;
    }

    /** 순서 (작을수록 먼저): 보고 → 메인 이야기의 진행 중 단계 → 다른 퀘스트의 말 걸기 → 다른 퀘스트의 나머지 → 다음 메인 이야기 받기 → 레벨 안내 */
    static int navRank(NavGoal g) {
        if (g.why().equals("turnin")) return 0;
        if (g.main() && !g.why().equals("offer") && !g.why().equals("level")) return 1;
        if (g.why().equals("talk")) return 2;
        if (!g.main()) return 3;
        return g.why().equals("offer") ? 4 : 5;
    }

    /** 크리에이티브 · 관전: 짓는 중인 운영자 — 받지 않은 메인 이야기 (새 이야기 · 레벨 안내) 는 안내하지 않음 */
    static boolean building(ServerPlayer sp) {
        GameType m = sp.gameMode.getGameModeForPlayer();
        return m == GameType.CREATIVE || m == GameType.SPECTATOR;
    }

    /**
     * 지금 가야 할 곳: 고른 퀘스트 → 없거나 갈 곳이 없으면 navRank 순서 (같으면 메인 먼저). 길 안내를 껐으면 null
     */
    public static NavGoal navGoal(ServerPlayer sp) {
        Nav nav = navOf(sp);
        if (nav.off()) return null;
        Quest pinned = nav.q().isEmpty() ? null : getQuest(nav.q());
        NavGoal g = pinned != null ? goalOf(sp, pinned) : null;
        if (g != null) return g;
        List<NavGoal> cands = new ArrayList<>();
        Quest nx = nextMain(sp);
        NavGoal mg = nx != null ? goalOf(sp, nx) : null;
        if (mg != null) cands.add(mg);
        for (Map.Entry<String, Quest.Entry> en : stateOf(sp).entrySet()) {
            if (!en.getValue().active()) continue;
            Quest q = getQuest(en.getKey());
            NavGoal og = q != null && !q.main ? goalOf(sp, q) : null;
            if (og != null) cands.add(og);
        }
        boolean build = building(sp);
        NavGoal best = null;
        int rank = 99;
        for (NavGoal c : cands) {
            if (build && c.main() && (c.why().equals("offer") || c.why().equals("level"))) continue;
            int r = navRank(c);
            if (r < rank) {
                rank = r;
                best = c;
            }
        }
        return best;
    }
}
