package mmorpg.fabric.quest;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Rules;

/**
 * 메인 퀘스트 (mainquest.js): 애드온에 들어 있는 이야기. 운영자는 /mainnpc 번호 로 NPC 만 세우면 됨.
 * - 프롤로그: 모험을 꿈꾸는 작은 소년 → 할아버지 하랄 → 훈련 조교 브란 (무기 여섯 자루 시험 → 하나 고르기)
 * - 지역 퀘스트: 스승 (마을) → 농장 → 사막 → 바다 → 북쪽 → 스승 → 할아버지. 지역마다 NPC 한 명 (13 농장 · 14 사막 · 15 바다 · 16 북쪽).
 *   보스마다 그 지역 NPC 가 [그 지역 일반 몬스터 처치 → 그 몬스터 전리품 모으기 → 보스] 를 차례로 주고, 지역을 마치면 다음 지역 NPC 를 찾아가는 퀘스트.
 *   모든 길이 보스 8마리를 모두 잡음. 길마다 다른 것은 스승의 대사와 요르문간드 · 이미르 보상뿐
 * - 전설 무기 4개는 만들 수 없고 그 길의 전설 퀘스트로만 (SideQuests, side "legend")
 * - 정의는 여기에만 있음 (세계에 저장하지 않음 → 고치면 바로 바뀜, 편집 창에서는 고칠 수 없음). 단계 ID "mq_&lt;길&gt;_rNN",
 *   예전 (18단계 "mq_&lt;길&gt;_NN") 진행은 migrateMainState 가 가까운 단계로 옮김
 * - 대사: "|" 줄바꿈, "||" 다음 장. {master} = 그 길의 스승 이름, {mat} = 보스 재료 이름. 자바에서는 "터치" 를 "누르기" 로
 * 마인크래프트 클래스를 쓰지 않음 (규칙 코어 · Gson 만).
 */
public final class MainQuests {
    private MainQuests() {}

    private static final Rules R = Rules.get();
    private static final Obj BOSSES = R.obj("BOSSES");
    private static final Obj BOSS_MATERIALS = R.obj("BOSS_MATERIALS");
    private static final Obj RAID_MATERIALS = R.obj("RAID_MATERIALS");
    private static final Obj MONSTER_DROPS = R.obj("MONSTER_DROPS");
    private static final Obj MONSTERS = R.obj("MONSTERS");
    private static final Obj RUNE = R.obj("RUNE");

    /** 메인 퀘스트 ID 앞머리 */
    public static final String MAIN_PREFIX = "mq_";
    /** 무기의 길 순서 (= 무기 종류 순서) */
    public static final List<String> PATH_ORDER = List.copyOf(R.strings("TYPE_ORDER"));

    // ------------------------------------------------------------ NPC

    /** 스승의 인사말: own (제 길의 제자) · other (다른 길) · none (아직 무기를 고르지 않음) · done (이야기를 끝냄) */
    public record Greet(String own, String other, String none, String done) {}

    /**
     * 메인 퀘스트 NPC (/mainnpc 번호). key = NPC 번호 (퀘스트의 giver · target), waitLine = 레벨이 모자라 다음 퀘스트를 못 받을 때 ({lv} = 필요한 레벨),
     * greet = 스승만 (나머지는 QuestTalk.mainGreeting), path = 스승의 길, region = 지역 NPC 의 지역
     */
    public record MainNpc(int n, String key, String name, String role, String path, String region, boolean smith, boolean runesmith, String waitLine, Greet greet) {}

    private static MainNpc npc(int n, String name, String role, String wait) {
        return new MainNpc(n, "main_" + n, name, role, null, null, false, false, wait, null);
    }

    private static MainNpc mentor(int n, String name, String role, String path, String wait, Greet greet) {
        return new MainNpc(n, "main_" + n, name, role, path, null, false, false, wait, greet);
    }

    private static MainNpc regional(int n, String name, String role, String region, String wait) {
        return new MainNpc(n, "main_" + n, name, role, null, region, false, false, wait, null);
    }

    public static final List<MainNpc> MAIN_NPCS = List.of(
            npc(1, "할아버지 하랄", "이야기의 시작과 끝", "조금 더 강해지면 다시 오거라. 레벨 {lv}에 오르면 말이다."),
            npc(2, "훈련 조교 브란", "무기 시험 · 무기 고르기 (곁에 /dummy 허수아비를 두면 좋음)", "아직 이르다! 레벨 {lv}에 오르면 다시 와라!"),
            mentor(3, "검술 스승 시그문드", "칼의 길", "sword", "아직 때가 아니다. 레벨 {lv}에 오르면 다시 오너라.", new Greet(
                    "검은 마음을 비추는 거울이다. 오늘도 수련하러 왔나?",
                    "다른 무기를 골랐군. 길은 달라도 지키려는 마음은 같을 게다.",
                    "아직 무기를 고르지 않았군. 훈련장의 브란에게 먼저 가 보게.",
                    "서리 거인을 쓰러뜨린 영웅이 왔군. 이제 내가 너에게 배울 차례다.")),
            mentor(4, "그림자 스승 헬가", "단검의 길", "dagger", "…아직 약해. 레벨 {lv}에 오르면 와.", new Greet(
                    "…왔어? 발소리가 아직 커. 더 조용히 걸어.",
                    "…내 제자는 아니군. 용건 없으면 가 봐.",
                    "…무기도 없이 뭐 하러 왔어. 브란한테 먼저 가.",
                    "…그림자의 영웅이군. 이제 내가 네 등 뒤를 지켜 줄게.")),
            mentor(5, "도끼 전사 토르발", "도끼의 길", "axe", "하하, 아직 근육이 모자라! 레벨 {lv}에 오르면 와라!", new Greet(
                    "하하하! 왔구나, 꼬마 전사! 오늘도 땀 좀 흘려 볼까?",
                    "하하! 도끼는 아니지만 눈빛이 좋구나! 언제든 놀러 와라!",
                    "무기도 없는 꼬마로군! 브란에게 가서 하나 골라 와라! 하하!",
                    "북쪽의 영웅이 왔구나! 오늘은 잔치다! 하하하!")),
            mentor(6, "수호 기사 하콘", "방패의 길", "shield", "서두르지 말게. 레벨 {lv}에 오르면 다시 오게.", new Greet(
                    "방패는 나를 위한 것이 아니라 뒤에 선 이들을 위한 것이네. 어서 오게.",
                    "다른 길을 걷는군. 그래도 위험할 땐 내 방패 뒤로 오게.",
                    "아직 무기가 없군. 훈련장의 브란을 먼저 찾아가게.",
                    "이 세상의 방패가 찾아왔군. 영광일세.")),
            mentor(7, "엘프 사냥꾼 엘린", "활의 길", "bow", "조금만 더 기다려요. 레벨 {lv}에 오르면 다시 와요.", new Greet(
                    "바람이 오늘은 동쪽에서 불어요. 활을 쏘기 좋은 날이네요.",
                    "당신은 다른 길을 걷는군요. 그래도 숲은 모두를 반겨요.",
                    "아직 무기를 고르지 않았군요. 브란 씨에게 먼저 가 봐요.",
                    "바람의 영웅이 왔네요. 숲이 노래하고 있어요.")),
            mentor(8, "마법사 볼바", "완드의 길", "wand", "룬이 아직 때가 아니라 하느니라. 레벨 {lv}에 오르면 오거라.", new Greet(
                    "기다리고 있었느니라. 룬이 네가 올 것을 말해 주었지.",
                    "네 운명은 다른 무기에 묶여 있구나. 그 길을 가거라.",
                    "무기를 고르지 않은 자의 운명은 아직 흐릿하구나. 브란에게 가거라.",
                    "예언을 이룬 아이로구나. 이제 룬도 네 앞에서 침묵하느니라.")),
            new MainNpc(9, "main_9", "군나르", "대장장이 (강화 · 영웅 무기 레벨 · 보스 무기 제작) · 심부름", null, null, true, false,
                    "자네에겐 아직 이른 일이야. 레벨 {lv}에 오르면 다시 오게.", null),
            npc(10, "여관 주인 잉그리드", "심부름 · 소문 · 반복 의뢰 (몬스터 전리품, v2.7.0)", "아직은 위험해요! 레벨 {lv}에 오르면 다시 와 주세요."),
            // 메인 이야기 밖의 NPC (왕실 퀘스트 · 룬술사)
            npc(11, "왕녀 시그리드", "왕실 퀘스트 (35렙부터, 끝까지 마치면 초월 갑옷)", "아직은 위험한 부탁이에요. 레벨 {lv}에 오르면 다시 찾아와 주세요."),
            new MainNpc(12, "main_12", "룬술사 에이빈드", "룬술사 (룬 갈기 → 룬 조각 → 룬 뽑기권 · 고급 룬 뽑기권)", null, null, false, true,
                    "룬은 서두르는 자를 싫어하지. 레벨 {lv}에 오르면 오게.", null),
            // 지역 NPC — 그 지역의 사냥터 · 보스 제단 곁에 세움 (REGIONS)
            regional(13, "농부 올라프", "농장 지역 (허수아비 · 니세 · 광폭 멧돼지 · 허수아비 킹)", "farm", "아직은 위험하구먼. 레벨 {lv}에 오르면 다시 오게."),
            regional(14, "대상 우두머리 라그나르", "사막 지역 (굴론 · 모래 전갈 · 웜 유충 · 전갈 · 거대 웜)", "desert", "그 실력으론 아직이다. 레벨 {lv}에 오르면 다시 와!"),
            regional(15, "늙은 선장 비요른", "바다 지역 (스켈야스크림슬리 · 집게 게 · 닉스 · 그렌델 · 크라켄 · 요르문간드)", "sea", "서두르지 말게. 레벨 {lv}에 오르면 다시 오게나."),
            regional(16, "서리 사냥꾼 스카디", "북쪽 지역 (드라우그 · 서리 늑대 · 트롤 · 서리 거인 · 니드호그 · 스콜과 하티 · 이미르)", "north", "아직 약해. 레벨 {lv}에 오르면 와."));

    /** 메인 이야기에 나오는 NPC 번호 (1~8 마을 · 13~16 지역). 9 대장장이 · 10 여관 (반복 의뢰) · 11 왕녀 · 12 룬술사는 이야기 밖 */
    public static final List<Integer> STORY_NPC_NUMBERS = List.of(1, 2, 3, 4, 5, 6, 7, 8, 13, 14, 15, 16);

    /** 대사 속에서 부르는 이름 (이름표 이름과 다를 때) */
    private static final Map<String, String> CALL = Map.of("main_1", "할아버지 하랄", "main_2", "훈련 조교 브란", "main_9", "대장장이 군나르", "main_10", "여관 주인 잉그리드",
            "main_11", "왕녀 시그리드", "main_12", "룬술사 에이빈드");

    public static MainNpc mainNpcByKey(String key) {
        for (MainNpc d : MAIN_NPCS) if (d.key().equals(key)) return d;
        return null;
    }

    public static MainNpc mainNpcByNumber(int n) {
        for (MainNpc d : MAIN_NPCS) if (d.n() == n) return d;
        return null;
    }

    /** 퀘스트 목표 · 의뢰에 보이는 이름 (NPC 를 세우기 전에도 — 없으면 "") */
    public static String mainNpcCall(String key) {
        String c = CALL.get(key);
        if (c != null) return c;
        MainNpc d = mainNpcByKey(key);
        return d == null ? "" : d.name();
    }

    // ------------------------------------------------------------ 무기의 길

    /** 무기의 길: mentor = 스승 NPC 번호, desc = 고르는 창의 한 줄 설명, tint = 글자 색, color = 연출 색 */
    public record MainPath(String name, String mentor, String desc, String tint, double[] color) {}

    public static final Map<String, MainPath> MAIN_PATHS;

    static {
        Map<String, MainPath> m = new LinkedHashMap<>();
        m.put("sword", new MainPath("칼의 길", "main_3", "힘 · 균형 잡힌 근접 딜러", "§c", new double[] {1.0, 0.42, 0.36}));
        m.put("dagger", new MainPath("단검의 길", "main_4", "민첩 · 치명타가 잦은 근접", "§a", new double[] {0.4, 1.0, 0.55}));
        m.put("axe", new MainPath("도끼의 길", "main_5", "힘+체력 · 튼튼한 딜탱", "§6", new double[] {1.0, 0.6, 0.25}));
        m.put("shield", new MainPath("방패의 길", "main_6", "체력 · 동료를 지키는 탱커", "§d", new double[] {0.62, 0.8, 1.0}));
        m.put("bow", new MainPath("활의 길", "main_7", "민첩 · 멀리서 쏘는 사냥꾼", "§2", new double[] {0.75, 1.0, 0.5}));
        m.put("wand", new MainPath("완드의 길", "main_8", "마력 · 방어를 꿰뚫는 마법", "§9", new double[] {0.45, 0.62, 1.0}));
        MAIN_PATHS = Collections.unmodifiableMap(m);
    }

    /** 길 이름 ("sword" …) 인지 */
    public static boolean isPath(String type) {
        return type != null && MAIN_PATHS.containsKey(type);
    }

    // ------------------------------------------------------------ 지역

    /** 지역 인사말: none 무기 전 · before 아직 오지 않음 · here 이 지역 진행 중 · after 이 지역을 마침 */
    public record RegionGreet(String none, String before, String here, String after) {}

    /** 지역마다 NPC 한 명 (그 지역의 사냥터 · 보스 제단 곁에 세움). mobs · bosses = /mainnpc 목록에 보이는 "그 지역에 둘 것" */
    public record Region(String npc, String name, List<String> mobs, List<String> bosses, RegionGreet greet) {}

    public static final Map<String, Region> REGIONS;

    static {
        Map<String, Region> m = new LinkedHashMap<>();
        m.put("farm", new Region("main_13", "농장", List.of("scarecrow", "nisse", "boar"), List.of("scarecrow_king"), new RegionGreet(
                "모험가인가? 무기도 없이 밭에 나오면 위험하구먼. 마을 훈련장에 먼저 가 보게.",
                "허수아비들 때문에 큰일이구먼. 누가 좀 도와주면 좋으련만…",
                "어서 오게! 밭일은 잘 되어 가는가?",
                "자네 덕분에 다시 밭을 갈고 있구먼. 고맙네!")));
        m.put("desert", new Region("main_14", "사막", List.of("gulon", "sand_scorpion", "worm_larva"), List.of("scorpions", "great_worm"), new RegionGreet(
                "무기도 없이 사막에 오다니, 겁도 없군. 마을로 돌아가 무기부터 골라 와.",
                "길목이 막혀서 장사를 못 하고 있다. 쓸 만한 모험가가 없나…",
                "왔군! 사막 일은 어떻게 돼 가나?",
                "하! 사막의 영웅이 왔군! 덕분에 대상이 다시 움직인다.")));
        m.put("sea", new Region("main_15", "바다", List.of("skeljaskrimsli", "crab", "nix", "grendel"), List.of("kraken", "jormungandr"), new RegionGreet(
                "무기도 없이 바다에 나오면 큰일 나네. 마을에서 무기부터 고르게.",
                "요즘 바다가 심상치 않네. 배들이 자꾸 가라앉아…",
                "왔군. 바다 일은 잘 되어 가나?",
                "허허, 바다를 구한 영웅이 왔군! 언제든 배에 태워 주지.")));
        m.put("north", new Region("main_16", "북쪽", List.of("draugr", "frost_wolf", "troll", "frost_giant"), List.of("nidhogg", "skoll_hati", "ymir"), new RegionGreet(
                "…무기도 없이 여기까지? 돌아가. 여긴 얼어 죽기 딱 좋아.",
                "북쪽은 위험해. 아직 네가 올 곳이 아니야.",
                "왔어? 몸은 녹였어?",
                "…북쪽의 영웅이네. 여기선 언제든 환영이야.")));
        REGIONS = Collections.unmodifiableMap(m);
    }

    public static final List<String> REGION_ORDER = List.of("farm", "desert", "sea", "north");

    /** 이 NPC 가 맡은 지역 ("" = 지역 NPC 아님) */
    public static String regionOfNpc(String key) {
        for (String r : REGION_ORDER) if (REGIONS.get(r).npc().equals(key)) return r;
        return "";
    }

    // ------------------------------------------------------------ 보스마다 [처치 → 모으기 → 보스] (모든 길 같은 순서)

    /** 보상 표 (rewardOf 의 입력 — items = [키, 수] 쌍) */
    private record Spec(int emerald, int xp, int points, String weapon, Object[] items) {}

    private static Spec spec(int emerald, int xp, String weapon, Object... items) {
        return new Spec(emerald, xp, 0, weapon, items);
    }

    /** 보스 앞의 일 하나: kind kill (그 몬스터 처치) · collect (그 몬스터의 전리품 모으기), n 수, xp 보상 레벨 (받을 때 바닐라 경험치로 — 9단계), emerald */
    private record Task(String kind, String mob, int n, int xp, int emerald) {}

    private static Task kill(String mob, int n, int xp, int emerald) {
        return new Task("kill", mob, n, xp, emerald);
    }

    private static Task collect(String mob, int n, int xp, int emerald) {
        return new Task("collect", mob, n, xp, emerald);
    }

    /**
     * 보스 하나의 이야기: tasks (차례로 — 대사는 ARC_TEXT 의 같은 번호, 마지막 대사가 보스), lv: 보스 단계의 최소 레벨 (보스 레벨 -1, 이미르 40),
     * reward: 보스 보상 (grade 가 있으면 길마다 — bossReward).
     * 9단계: 지역마다 새 몬스터 둘의 처치 · 전리품 단계를 넣고, 처치 · 모으기 수를 늘림 (사용자 요청 — 퀘스트를 깨는 시간)
     */
    private record Arc(String boss, String region, List<Task> tasks, int lv, Spec reward, String grade) {}

    private static final List<Arc> ARCS = List.of(
            new Arc("scarecrow_king", "farm", List.of(kill("scarecrow", 12, 2, 5), kill("nisse", 10, 2, 5), kill("boar", 10, 2, 6), collect("boar", 6, 2, 6),
                    collect("scarecrow", 8, 2, 6)), 9, spec(12, 2, "l10_noble", "ticket", 1), null),
            new Arc("scorpions", "desert", List.of(kill("gulon", 14, 2, 10), collect("gulon", 10, 2, 12), kill("sand_scorpion", 12, 2, 12),
                    collect("sand_scorpion", 8, 2, 12)), 17, spec(18, 3, "l20_noble", "ticket", 1), null),
            new Arc("great_worm", "desert", List.of(kill("gulon", 12, 1, 14), collect("gulon", 8, 2, 16), kill("worm_larva", 12, 1, 16),
                    collect("worm_larva", 8, 2, 16)), 24, spec(24, 2, null, "stone", 4, "ore", 1, "ticket", 1), null),
            new Arc("kraken", "sea", List.of(kill("skeljaskrimsli", 14, 1, 14), kill("crab", 12, 1, 16), collect("crab", 8, 1, 16),
                    collect("skeljaskrimsli", 10, 1, 16)), 24, spec(24, 1, null, "stone", 2, "ore", 1, "ticket", 1), null),
            new Arc("jormungandr", "sea", List.of(kill("nix", 14, 1, 18), kill("grendel", 10, 1, 20), collect("grendel", 6, 1, 20),
                    collect("nix", 10, 2, 20)), 31, null, "hero"),
            new Arc("nidhogg", "north", List.of(kill("draugr", 14, 1, 24), collect("draugr", 10, 1, 24)), 37, spec(40, 1, null, "ticket", 1, "scroll", 1), null),
            new Arc("skoll_hati", "north", List.of(kill("frost_wolf", 12, 1, 26), collect("frost_wolf", 8, 1, 26), kill("troll", 10, 0, 28),
                    collect("troll", 8, 0, 28)), 37, spec(40, 0, null, "ticket", 1, "bright", 1), null),
            new Arc("ymir", "north", List.of(kill("draugr", 12, 0, 30), kill("frost_giant", 10, 1, 32), collect("frost_giant", 6, 1, 32),
                    collect("troll", 8, 0, 30)), 40, null, "transcend"));

    /** 지역 사이 · 마지막 귀환 보상 (에메랄드) */
    private static final Map<String, Integer> TRAVEL_EMERALD = Map.of("farm", 2, "desert", 4, "sea", 6, "north", 8, "home", 20);

    /**
     * 길의 단계 (정의만 — 모든 길 같은 뼈대). kind: intro (조교 → 스승) · depart (스승 → 농장) · travel (지역 → 다음 지역) · kill · collect · boss ·
     * home (북쪽 → 스승) · outro (스승 → 할아버지). giver · to = NPC 번호 ("@mentor" = 그 길의 스승), task = 보스 이야기 안의 번호 (kill · collect)
     */
    record Step(String kind, String giver, String to, String region, Arc arc, String mob, int count, String boss, int lv, int task) {}

    private static List<Step> skeleton() {
        List<Step> steps = new ArrayList<>();
        steps.add(new Step("intro", "main_2", "@mentor", null, null, null, 0, null, 0, -1));
        String at = "";
        int lv = 0;
        for (Arc arc : ARCS) {
            String npc = REGIONS.get(arc.region()).npc();
            if (!arc.region().equals(at)) {
                steps.add(new Step(at.isEmpty() ? "depart" : "travel", at.isEmpty() ? "@mentor" : REGIONS.get(at).npc(), npc, arc.region(), null, null, 0, null, lv, -1));
                at = arc.region();
            }
            for (int i = 0; i < arc.tasks().size(); i++) {
                Task t = arc.tasks().get(i);
                steps.add(new Step(t.kind(), npc, null, null, arc, t.mob(), t.n(), null, lv, i));
            }
            steps.add(new Step("boss", npc, null, null, arc, null, 0, arc.boss(), arc.lv(), -1));
            lv = arc.lv();
        }
        steps.add(new Step("home", REGIONS.get(at).npc(), "@mentor", null, null, null, 0, null, lv, -1));
        steps.add(new Step("outro", "@mentor", "main_1", null, null, null, 0, null, lv, -1));
        return List.copyOf(steps);
    }

    /** 단계의 표지 (옮기기용 — 같은 일이면 같은 글자): intro · depart · travel:지역 · kill:보스:몬스터 · collect:보스:몬스터 · boss:보스 · home · outro */
    static String signature(Step s) {
        return switch (s.kind()) {
            case "travel" -> "travel:" + s.region();
            case "kill", "collect" -> s.kind() + ":" + s.arc().boss() + ":" + s.mob();
            case "boss" -> "boss:" + s.boss();
            default -> s.kind();
        };
    }

    private static final List<Step> SKELETON = skeleton();
    /** 단계 수 (길마다 같음) */
    public static final int PATH_STEPS = SKELETON.size();

    /** 길의 단계 정의 (스승 번호를 채움) */
    static List<Step> pathSteps(String type) {
        MainPath p = MAIN_PATHS.get(type);
        String mentor = p == null ? "" : p.mentor();
        List<Step> out = new ArrayList<>();
        for (Step s : SKELETON) {
            out.add(new Step(s.kind(), "@mentor".equals(s.giver()) ? mentor : s.giver(), "@mentor".equals(s.to()) ? mentor : s.to(), s.region(), s.arc(), s.mob(), s.count(),
                    s.boss(), s.lv(), s.task()));
        }
        return out;
    }

    /** 그 몬스터를 처음 잡는 단계 (모든 길 같은 단계, 없으면 0) — 잉그리드의 반복 의뢰가 열리는 단계 */
    public static int mainKillStep(String mob) {
        for (int i = 0; i < SKELETON.size(); i++) if (SKELETON.get(i).kind().equals("kill") && SKELETON.get(i).mob().equals(mob)) return i + 1;
        return 0;
    }

    /** 그 보스를 잡는 단계 (없으면 0) — 전설 퀘스트가 열리는 단계 */
    public static int mainBossStep(String boss) {
        for (int i = 0; i < SKELETON.size(); i++) if (SKELETON.get(i).kind().equals("boss") && SKELETON.get(i).boss().equals(boss)) return i + 1;
        return 0;
    }

    /** 그 길 · 등급의 보스 무기 제작법 (없으면 null — 전설 무기는 만들 수 없음) */
    private static Obj craftOf(String type, String grade) {
        for (Obj r : R.root().objsOrNull("BOSS_CRAFT")) if (r.str("type").equals(type) && r.str("grade").equals(grade)) return r;
        return null;
    }

    // ------------------------------------------------------------ 대사

    /** [제목, 줄 때, 진행 중, 완료] — 완료 대사는 보고받는 NPC 가 말함 (말 걸기는 받는 사람) */
    record T(String title, String offer, String progress, String done) {
        T withDone(String d) {
            return new T(title, offer, progress, d);
        }
    }

    private static final T START = new T("모험의 꿈",
            "또 언덕 위에서 먼 곳을 보고 있었구나. 모험가가 되고 싶은 게냐?||허허, 이 할아비도 젊을 땐 세계수 아래를 누비던 모험가였단다.||좋다. 훈련장의 브란에게 가 보거라. 무기 쓰는 법을 가르쳐 줄 게다.",
            "브란은 훈련장에 있단다. 어서 가 보거라.",
            "하랄 어르신의 손자라고? 하하, 눈빛이 제법이군!||좋다, 오늘부터 훈련이다. 각오는 됐겠지?");
    private static final T TRIAL = new T("여섯 자루의 무기",
            "모험가에게 가장 중요한 건 자기 손에 맞는 무기를 찾는 거다.||칼, 단검, 도끼, 방패, 활, 완드. 여섯 자루를 모두 빌려주마.||하나씩 들고 허수아비든 몬스터든 한 번씩 맞혀 봐라!",
            "아직 안 써 본 무기가 있군. 여섯 자루 모두 한 번씩 맞혀 봐라!",
            "어떠냐, 손맛이 다 다르지?||활과 완드는 누르면 쏘고, 방패는 들고 웅크리면 앞을 막는다. 기억해 둬라.");
    private static final T CHOOSE = new T("나의 무기", "이제 고를 시간이다. 가장 손에 맞았던 무기를 골라라.||고른 무기의 스승을 소개해 주마. 한 번 고르면 다시는 바꿀 수 없다!", "", "");

    /** 무기를 고른 뒤 조교의 말 (길마다) */
    private static final Map<String, String> CHOSEN;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("sword", "칼이라! 정직하고 굳센 무기지.||검술 스승 시그문드를 찾아가라. 이 추천서를 보여 주면 될 거다.");
        m.put("dagger", "단검이라… 빠르고 날카롭군.||그림자 스승 헬가를 찾아가라. 말수가 적은 사람이니 놀라지 마라.");
        m.put("axe", "도끼! 하하, 배짱 있는 녀석이군.||도끼 전사 토르발을 찾아가라. 목소리가 커서 금방 찾을 거다.");
        m.put("shield", "방패를 고르다니, 남을 지키는 길이군.||수호 기사 하콘을 찾아가라. 마을에서 가장 믿음직한 기사다.");
        m.put("bow", "활이라. 눈이 좋은 녀석이었군.||엘프 사냥꾼 엘린을 찾아가라. 숲에서 온 엘프지만 마음이 따뜻하다.");
        m.put("wand", "완드라… 마력이 느껴지더라니.||마법사 볼바를 찾아가라. 앞날을 읽는다는 이상한 노파다.");
        CHOSEN = Collections.unmodifiableMap(m);
    }

    /** 스승 (길마다): intro = 브란 → 스승 (추천서), depart = 스승 → 농장 (완료 대사는 농부), home = 북쪽에서 돌아왔을 때 스승의 말, outro = 스승 → 할아버지 */
    private record Master(T intro, T depart, String home, T outro) {}

    private static final Map<String, Master> MASTER = Map.of(
            "sword", new Master(
                    new T("시그문드를 찾아서", "칼을 골랐으니 검술 스승 시그문드에게 가 봐라.||이 추천서를 보여 주면 될 거다.", "시그문드에게 추천서를 보여 줘라.",
                            "브란의 추천서로군. 칼을 골랐다고?||나는 시그문드. 한때 왕실 근위대장이었지.||좋다, 오늘부터 너는 내 제자다. 칼은 힘으로 휘두르되 마음으로 멈추는 것이다."),
                    new T("첫 실전",
                            "기본은 익혔다. 이제 실전이다.||마을 밖 농장의 허수아비들이 저절로 움직인다더군. 세계수가 시들며 생긴 일이지.||농부 올라프를 찾아가 도와라. 그리고 이 검 조각을 가져가거라.||우리 가문의 검 그람의 조각이다. 삼십 년 전 서리 거인 이미르 앞에서 부서졌지.||이미르를 쓰러뜨리거든 돌아오너라. 그람을 되살릴 길을 알려 주마.",
                            "농장의 농부 올라프를 찾아가라.", ""),
                    "…이미르를 쓰러뜨렸구나. 삼십 년 전 내가 끝내지 못한 일을 네가 해냈다.||이제 그람을 되살릴 차례다. 준비가 되면 내게 말하거라.",
                    new T("영웅의 귀환", "더는 가르칠 것이 없구나. 너는 나의 자랑스러운 제자다.||할아버지께 가 보아라. 누구보다 너를 기다리셨을 게다.", "하랄 어르신께 가 보아라.",
                            "시그문드가 그러더구나. 네가 이미르를 쓰러뜨렸다고!||언덕에서 모험을 꿈꾸던 꼬마가 세계를 구한 영웅이 되어 돌아왔구나.||이 할아비는 이제 여한이 없다. 네 모험은 이제부터 시작이란다.")),
            "dagger", new Master(
                    new T("헬가를 찾아서", "단검을 골랐으니 그림자 스승 헬가에게 가 봐라.||이 추천서를 건네면 된다. 말수가 적어도 놀라지 마라.", "헬가에게 추천서를 건네라.",
                            "…브란이 보냈군. 단검을 골랐다고.||난 헬가. 이 마을의 그림자를 지키지.||좋아, 받아 주지. 대신 내 말은 두 번 하지 않아."),
                    new T("그림자의 첫 임무",
                            "…기본은 됐어. 이제 밖으로 나가.||농장의 허수아비들이 움직이고 있어. 농부 올라프를 찾아가 도와.||크라켄의 칠흑 먹물, 스콜의 불꽃 송곳니. 좋은 단검의 재료야. 보이면 챙겨.||군나르가 벼려 줄 거야. 그리고… 살아서 돌아와.",
                            "농장의 올라프. 가.", ""),
                    "…살아 돌아왔군. 다행이야. 진심으로.||스카디가 전갈을 보냈더군. 넌 이제 내 제자가 아니라 동료야.",
                    new T("영웅의 귀환", "가르칠 건 다 가르쳤어. 이제 넌 나보다 조용히 걷지.||할아버지께 가 봐. …기다리고 계셔.", "할아버지께.",
                            "얘야! 그 무뚝뚝한 헬가가 너를 칭찬하더구나!||언덕에서 모험을 꿈꾸던 꼬마가 세계를 지킨 영웅이 되었구나.||자랑스럽다. 네 모험은 이제부터 시작이란다.")),
            "axe", new Master(
                    new T("토르발을 찾아서", "도끼를 골랐으니 도끼 전사 토르발에게 가 봐라.||목소리가 커서 금방 찾을 거다. 이 추천서를 건네라.", "토르발에게 추천서를 건네라.",
                            "하하하! 도끼를 골랐다고? 브란이 제대로 된 녀석을 보냈군!||나는 토르발! 북쪽 얼음 땅에서 왔다.||도끼는 힘으로 내리찍고 몸으로 버티는 무기다. 힘과 체력을 함께 길러라!"),
                    new T("전사의 첫걸음",
                            "하하! 이제 도끼 맛을 보여 줄 때다!||농장의 허수아비들이 날뛴다더군. 농부 올라프를 찾아가 도와라!||거대 웜의 심장석, 니드호그의 세계수 가지! 군나르에게 가져가면 전설의 도끼가 된다!||그리고 내 고향을 얼린 서리 거인 이미르… 언젠가 꼭 쓰러뜨려 다오!",
                            "농장의 올라프에게 가라! 하하!", ""),
                    "돌아왔구나! 북쪽 하늘이 녹고 있다더군!||내 고향 사람들도 이제 편히 잠들겠지. 고맙다, 전사여! 하하… 정말 고맙다.",
                    new T("영웅의 귀환", "하하하! 이제 네게 가르칠 건 없다! 하랄에게 가 봐라.||우리 전우가 너를 기다린다!", "하랄에게 가라!",
                            "네가 이미르를 쓰러뜨렸다고! 우리가 끝내 못 한 일을…||언덕에서 모험을 꿈꾸던 꼬마가 북쪽의 영웅이 되었구나.||고맙다, 얘야. 네 모험은 이제부터 시작이란다.")),
            "shield", new Master(
                    new T("하콘을 찾아서", "방패를 골랐으니 수호 기사 하콘에게 가 봐라.||마을에서 가장 믿음직한 기사다. 이 추천서를 건네라.", "하콘에게 추천서를 건네라.",
                            "브란이 보냈군. 방패를 골랐다고?||나는 하콘, 이 마을을 지키는 기사일세.||방패를 들고 웅크리면 앞에서 오는 공격을 막을 수 있네. 남을 지키는 법부터 배우세."),
                    new T("지키는 자의 첫걸음",
                            "이제 마을 밖을 지킬 차례일세.||농장의 허수아비들 때문에 농부들이 밭에 나가지 못한다네. 농부 올라프를 찾아가 돕게.||내 스승 듀란드 경은 요르문간드에 맞서다 바다로 사라지셨네. 그분의 방패가 그 뱀의 뱃속에 있다지.||요르문간드를 쓰러뜨리거든 돌아오게. 그 방패 이야기를 해 주겠네.",
                            "농장의 농부 올라프를 찾아가게.", ""),
                    "이미르의 얼음이 녹았다지? 자네가 해냈군!||그 얼음 속에서 신들의 방패 아이기스의 기운이 느껴진다네. 준비가 되면 말하게.",
                    new T("영웅의 귀환", "이제 내가 자네를 지킬 일은 없겠군. 자네가 모두를 지키니까.||할아버지께 가 보게. 기다리고 계실 걸세.", "하랄 어르신께 가 보게.",
                            "하콘이 그러더구나. 네가 모두를 지켜 냈다고!||언덕에서 모험을 꿈꾸던 꼬마가 모두를 지키는 방패가 되어 돌아왔구나.||자랑스럽다, 얘야. 네 모험은 이제부터 시작이란다.")),
            "bow", new Master(
                    new T("엘린을 찾아서", "활을 골랐으니 엘프 사냥꾼 엘린에게 가 봐라.||숲에서 온 엘프지만 마음이 따뜻하다. 이 추천서를 건네라.", "엘린에게 추천서를 건네라.",
                            "브란 씨가 보낸 아이군요. 활을 골랐다고요?||저는 엘린, 숲에서 온 엘프예요.||활은 숨을 고르고 바람을 읽는 무기예요. 천천히 배워 봐요."),
                    new T("숲 밖으로",
                            "이제 숲 밖으로 나갈 때예요.||농장의 허수아비들이 움직인대요. 농부 올라프 씨를 찾아가 도와줘요.||오래전 요르문간드가 바다를 일으켜 제 고향 숲을 삼켰어요. 일족의 보물, 엘프의 활도요.||요르문간드를 쓰러뜨리면 돌아와 줘요. 그 활 이야기를 해 줄게요.",
                            "농장의 올라프 씨를 찾아가요.", ""),
                    "돌아왔군요… 숲이 다시 녹고 있어요. 새들이 노래해요.||약속을 지켜 줘서 고마워요. 정말로요.",
                    new T("영웅의 귀환", "이제 제가 가르칠 것은 없어요. 당신은 바람 그 자체예요.||할아버지께 가 봐요. 기다리고 계실 거예요.", "하랄 할아버지께요.",
                            "엘린이 그러더구나. 숲이 다시 노래한다고!||언덕에서 모험을 꿈꾸던 꼬마가 세계를 지킨 영웅이 되었구나.||자랑스럽다. 네 모험은 이제부터 시작이란다.")),
            "wand", new Master(
                    new T("볼바를 찾아서", "완드를 골랐으니 마법사 볼바에게 가 봐라.||앞날을 읽는다는 이상한 노파다. 이 추천서를 건네라.", "볼바에게 추천서를 건네라.",
                            "왔구나. 룬이 네가 올 것을 말해 주었느니라.||나는 볼바, 세계수의 룬을 읽는 자.||완드는 마력으로 쏘는 무기. 마력을 기르고, 시전하는 동안 흔들리지 말거라."),
                    new T("운명의 첫 줄",
                            "룬이 말하는구나. 네 여정이 시작될 때라고.||농장의 허수아비들이 움직이느니라. 농부 올라프를 찾아가 도와주거라.||예언을 들려주마. '흑룡이 뿌리를 갉고, 서리 거인이 눈을 뜨고, 작은 자가 그를 잠재우리라.'||작은 자, 그것이 바로 너니라. 가거라.",
                            "농장의 올라프를 찾아가거라.", ""),
                    "…룬이 고요해졌구나. 예언이 끝났느니라.||작은 자가 서리 거인을 잠재웠구나. 이제 너의 이야기는 네가 쓰거라.",
                    new T("영웅의 귀환", "내 룬은 더 이상 네 앞날을 읽지 못하느니라. 네 운명은 이제 네 것이니.||할아비에게 가거라. 기다리고 있느니라.", "하랄에게 가거라.",
                            "볼바의 예언 속 작은 자가… 정말 우리 손자였구나!||언덕에서 모험을 꿈꾸던 꼬마가 예언을 이룬 영웅이 되었구나.||자랑스럽다, 얘야. 네 모험은 이제부터 시작이란다.")));

    /** 지역 NPC 의 첫인사 (그 지역으로 가는 퀘스트의 완료 대사) */
    private static final Map<String, String> ARRIVE = Map.of(
            "farm", "{master}의 제자라고? 어이구, 잘 왔구먼!||나는 농부 올라프여. 요즘 밭의 허수아비들이 저절로 움직여서 큰일이구먼.",
            "desert", "올라프 영감이 보냈다고? 하, 마침 잘 왔다!||나는 라그나르, 이 사막을 건너는 대상의 우두머리다. 요즘 길이 완전히 막혔어.",
            "sea", "라그나르 녀석이 보냈다고? 허허, 반갑네.||나는 비요른, 이 바다에서 오십 년을 산 늙은 선장이라네.",
            "north", "비요른 영감이 보냈어? …좋아, 쓸 만해 보이네.||난 스카디, 북쪽 눈밭의 사냥꾼이야. 여긴 지금 서리가 모든 걸 삼키고 있어.");

    /** 다음 지역 (또는 마을의 스승) 으로 가는 퀘스트 — 키 = 가는 곳 (완료 대사는 ARRIVE · 스승은 MASTER.home) */
    private static final Map<String, T> TRAVEL = Map.of(
            "desert", new T("사막의 길목", "고맙네, 젊은이. 그런데 사막 길목에서 상인들이 발이 묶였다는구먼.||대상 우두머리 라그나르를 찾아가 보게. 자네 같은 사람이 필요할 거여.",
                    "사막의 라그나르를 찾아가 보게. 대상 천막에 있을 거여.", ""),
            "sea", new T("항구의 선장", "사막은 이제 걱정 없겠군. 그런데 바닷길에서 배들이 가라앉는다는 소식이 왔다.||항구의 늙은 선장 비요른을 찾아가 봐. 바다라면 그 영감이 제일 잘 알지.",
                    "바닷가의 비요른 선장을 찾아가. 항구에 있을 거다.", ""),
            "north", new T("북쪽의 사냥꾼", "바다는 이제 걱정 없겠네. 그런데 북쪽에서 서리가 내려온다는 소문이 도네.||서리 사냥꾼 스카디를 찾아가 보게. 그 땅은 그 아가씨가 제일 잘 안다네.",
                    "북쪽의 스카디를 찾아가게. 눈밭의 사냥꾼 막사에 있을 걸세.", ""),
            "home", new T("스승에게", "북쪽 하늘이 녹고 있어. 네 덕분이야.||이제 마을로 돌아가. 네 스승이 기다리고 있을 거야.", "마을의 네 스승에게 돌아가.", ""));

    /**
     * 보스마다 [일 대사 … , 보스 대사] (Arc.tasks 와 같은 차례 — 모든 길 같음, 요르문간드 · 이미르의 보스 완료 대사는 BOSS_DONE).
     * 9단계: 새 몬스터 단계 대사를 넣고, 보스 바로 앞 단계의 완료 대사가 보스로 이어지게 함. 수는 Arc.tasks 와 같게 (열둘 · 여덟 …)
     */
    private static final Map<String, List<T>> ARC_TEXT = Map.of(
            "scarecrow_king", List.of(
                    new T("움직이는 허수아비", "밭에 세워 둔 허수아비들이 밤마다 걸어 다녀서 아무도 밭에 못 나가는구먼.||허수아비 열둘을 쓰러뜨려 주게. 짚단을 휘두르니 조심하고.",
                            "허수아비 열둘이여. 밭 한가운데에 몰려 있구먼.", "어이구, 시원하게도 쓰러뜨리는구먼! 자네 솜씨가 보통이 아니여."),
                    new T("장난꾸러기 니세", "허수아비를 쓰러뜨리고 나니 이번엔 니세들이 말썽이구먼.||원래는 곳간을 지켜 주는 착한 요정들인데, 요즘은 쇠스랑을 들고 사람을 찔러 대네.||니세 열만 혼내 주게. 찌르고는 깡충 물러나니 쫓아가서 치게!",
                            "니세 열이여. 찌르고 물러나면 바로 쫓아가게.", "어이구, 이제야 곳간이 조용하구먼.||그런데 요정들까지 이러는 걸 보니 밭에 뭔가 단단히 잘못됐어."),
                    new T("광폭 멧돼지", "숲에서 멧돼지들이 미쳐 날뛰며 밭을 갈아엎고 있구먼.||앞발로 땅을 긁으면 곧 들이받으니, 그 앞에서 옆으로 비켜서게.||멧돼지 열만 잡아 주게. 울타리가 남아나질 않아!",
                            "멧돼지 열이여. 땅을 긁으면 옆으로 비키게!", "휴, 울타리가 살았구먼! 자네 다리가 빠르기도 하구먼."),
                    new T("멧돼지 엄니", "멧돼지 엄니를 갈아 쟁기 날에 대면 땅이 아무리 굳어도 잘 갈린다네.||엄니 여섯 개만 구해 주게. 멧돼지를 잡으면 가끔 떨어지는구먼.",
                            "멧돼지 엄니 여섯이여. 광폭 멧돼지가 떨어뜨리는구먼.", "튼튼한 쟁기 날이 되겠구먼! 고맙네.||이제 남은 건 허수아비들이 떨어뜨린 그 짚단이여."),
                    new T("질긴 짚단", "쓰러진 허수아비에서 나온 짚은 이상하게 질기더구먼. 뭔가 기운이 남았어.||낡은 짚단 여덟 개만 모아다 주게. 어디서 온 기운인지 살펴봐야겠구먼.",
                            "낡은 짚단 여덟이여. 허수아비를 쓰러뜨리면 가끔 떨어지는구먼.", "이 짚 좀 보게. 밭 가운데 제단 쪽으로 기운이 흘러가는구먼.||허수아비들을 부리는 놈이 거기 있는 게 틀림없어."),
                    new T("수확의 왕", "놈의 정체가 드러났구먼. 수확의 왕, 허수아비 킹이여.||밭 가운데 제단을 누르면 놈이 나온다네. 동료를 모아 쓰러뜨려 주게!",
                            "놈은 분신을 만들어 숨는다네. 진짜는 어깨에 까마귀를 얹고 있구먼.", "해냈구먼! 이제 다시 밭을 갈 수 있겠어.||{master}께서 자네가 이기면 주라고 맡겨 둔 무기여. 받게!")),
            "scorpions", List.of(
                    new T("모래 위의 굴론 떼", "모래 언덕마다 굴론 떼가 들끓어서 낙타가 놀라 달아나고 있다.||굴론 열넷을 쫓아내 줘. 빠르고 자꾸 달려드니 맞받아쳐!",
                            "굴론 열넷이다. 달려들기 전에 몸을 낮추니 그때를 노려.", "하! 이제야 낙타들이 진정하는군. 솜씨 좋은데?"),
                    new T("굴론 털가죽", "모래바람을 막으려면 두꺼운 천막이 필요해. 굴론 털가죽이 그만이지.||털가죽 열 장만 구해 줘. 굴론을 잡으면 가끔 떨어진다.",
                            "굴론 털가죽 열 장이다. 사막의 굴론이 떨어뜨려.", "튼튼한 천막이 되겠군! 이제 모래바람도 버티겠어.||그런데 길목을 막은 진짜 문제는 따로 있다."),
                    new T("모래 전갈 떼", "진짜 문제는 이놈들이다. 길목 바위틈마다 모래 전갈이 숨어 있어.||꼬리 독침에 찔리면 한동안 독이 돌아. 열둘을 치워 줘.||저렇게 많은 걸 보면 어딘가에 어미가 있을 거다.",
                            "모래 전갈 열둘이다. 꼬리를 치켜들면 물러서.", "독침이 장난 아니군. 그래도 덕분에 길목이 좀 트였다."),
                    new T("전갈 독침", "독침에 남은 독으로 해독약을 만들 수 있다더군. 대상 사람들한테 꼭 필요해.||독침 여덟 개를 모아 줘. 모래 전갈을 잡으면 가끔 떨어진다.",
                            "전갈 독침 여덟 개다. 모래 전갈이 떨어뜨려.", "해독약이 넉넉해졌다! 이제 놈들의 어미를 상대할 수 있어.||독침에 묻은 모래를 보니, 놈들은 길목 한가운데서 나왔다."),
                    new T("모래와 바위의 한 쌍", "길목에 거대한 전갈 두 마리가 자리를 잡았다. 모래와 바위의 한 쌍이지.||둘이 붙어 있으면 서로를 지켜. 동료와 나눠서 떼어 놓고 쓰러뜨려!",
                            "둘을 떼어 놓아. 바위 전갈이 울부짖으면 등을 돌리고.", "길이 열렸다! 대상이 다시 움직일 수 있어.||상인 조합이 고맙다며 이 무기를 내놨다. 네 손에 맞춰 벼렸다더군.")),
            "great_worm", List.of(
                    new T("흔들리는 모래", "전갈이 사라지자 이번엔 땅이 울린다. 굴론들이 겁에 질려 날뛰고 있어.||굴론 열둘을 잠재워 줘. 땅속에서 뭔가 큰 게 깨어나고 있다.",
                            "굴론 열둘이다. 땅이 흔들리면 잠깐 멈춰서 살펴.", "굴론들이 그렇게 겁먹은 건 처음 본다. 땅속의 그놈 때문이야."),
                    new T("모래 망토", "땅속 놈을 상대하려면 모래에 몸을 숨겨야 해.||굴론 털가죽 여덟 장으로 모래 망토를 지어 주지. 털가죽을 모아 와.",
                            "굴론 털가죽 여덟 장이다.", "모래 망토다. 이걸 두르면 놈도 쉽게 못 찾을 거다.||그런데 모래 속에서 뭔가 꿈틀거린다. 놈의 새끼들이야."),
                    new T("모래 속의 유충", "거대 웜의 새끼들이 모래 속에 숨어 있다가 사람이 지나가면 튀어나온다.||발밑 모래가 흘러내리면 거기 있는 거다. 열둘을 잡아 줘.",
                            "웜 유충 열둘이다. 모래가 흘러내리는 곳을 조심해.", "새끼가 이 정도면 어미는 대체 얼마나 큰 거냐…"),
                    new T("유충 허물", "유충이 벗어 놓은 허물을 따라가면 놈의 굴이 나온다.||허물 여덟 개를 모아 와. 유충을 잡으면 가끔 떨어진다.",
                            "유충 허물 여덟 개다. 웜 유충이 떨어뜨려.", "허물이 전부 같은 쪽을 가리키는군. 사막 깊은 곳의 제단이다.||이제 그놈을 불러낼 차례야."),
                    new T("대지의 포식자", "거대 웜이 깨어났다. 땅속을 옮겨 다니다 갑자기 솟구치지.||사막 깊은 곳의 제단을 누르면 놈이 나온다. 땅이 갈라지는 소리를 듣고 피해!",
                            "땅이 흔들리면 그 자리를 떠. 놈이 귀 기울일 땐 움직이지 마.", "땅이 조용해졌다! 사막이 다시 숨을 쉬는군.||놈이 떨어뜨린 재료는 모아 둬. 마을의 군나르가 무기를 벼려 줄 거다.")),
            "kraken", List.of(
                    new T("해안의 껍질 괴물", "해안에 스켈야스크림슬리가 몰려와 어부들이 그물을 못 던지네.||열넷만 쫓아 주게. 목이 길어서 생각보다 멀리서 물어뜯는다네.",
                            "스켈야스크림슬리 열넷일세. 껍질이 두꺼우니 깊게 치게.", "허허, 해안이 조용해졌군. 어부들이 고마워할 걸세."),
                    new T("갯바위의 집게 게", "갯바위마다 집게 게가 몰려와 그물을 다 찢어 놓는다네.||등딱지가 단단해서 웬만한 칼은 튕겨 나가지. 집게에 물리면 한동안 꼼짝 못 하네.||열둘만 잡아 주게.",
                            "집게 게 열둘일세. 집게를 쳐들면 물러나게.", "허허, 갯바위가 깨끗해졌군. 어부들이 춤을 추겠어."),
                    new T("게 집게발", "집게발 껍질을 녹여 뱃전에 바르면 따개비가 붙지 않는다네.||게 집게발 여덟 개만 구해 주게. 집게 게를 잡으면 가끔 떨어지네.",
                            "게 집게발 여덟 개일세. 집게 게가 떨어뜨리네.", "좋은 집게발이야! 이제 뱃바닥만 손보면 되겠군."),
                    new T("단단한 껍질", "가라앉은 신전으로 가려면 배를 손봐야 하네.||단단한 껍질 열 개로 뱃바닥을 덧대면 촉수도 못 뚫지.",
                            "단단한 껍질 열 개일세. 해안의 껍질 괴물이 떨어뜨리네.", "이제 배가 웬만한 촉수엔 끄떡없겠군.||자, 바다 밑에 무엇이 있는지 말해 주지."),
                    new T("심해의 군주", "바다 깊은 곳에서 크라켄이 올라왔네. 배들이 가라앉은 건 다 놈 짓이야.||가라앉은 신전의 제단을 누르면 놈이 나온다네. 촉수부터 끊게!",
                            "촉수가 살아 있으면 놈이 낫는다네. 큰 물결이 오면 기둥 뒤로 숨게.", "바다가 잠잠해졌군! 오십 년 뱃사람 인생에 이런 날이 다 오다니.||놈이 떨어뜨린 재료는 모아 두게. 마을의 군나르가 무기를 벼려 줄 걸세.")),
            "jormungandr", List.of(
                    new T("물가의 닉스", "요즘 물가에서 닉스가 사람들을 물속으로 끌어들이려 하네.||열넷을 물리쳐 주게. 붙잡히면 몸이 무거워지니 거리를 재게.",
                            "닉스 열넷일세. 물가에서는 발을 조심하게.", "고맙네. 닉스가 이렇게 날뛰는 건 더 큰 놈이 깨어난 탓일세."),
                    new T("동굴의 그렌델", "닉스를 몰아내니 바닷가 동굴에서 그렌델이 기어 나왔다네.||밤마다 뱃사람들을 끌고 가는 괴물이야. 발톱에 긁히면 몸이 시들어 버리지.||열을 쓰러뜨려 주게. 키가 크니 팔이 닿는 거리를 조심하게.",
                            "그렌델 열일세. 할퀴면 몸이 시드니 맞지 말게.", "그렌델이 이렇게 날뛰는 건 처음일세. 바다 밑의 그 뱀이 깨어난 탓이야."),
                    new T("그렌델의 발톱", "그렌델의 발톱은 뱀의 비늘도 긁는다네. 작살 끝에 달면 그만이지.||발톱 여섯 개만 모아 주게. 그렌델을 쓰러뜨리면 가끔 떨어지네.",
                            "그렌델의 발톱 여섯 개일세. 그렌델이 떨어뜨리네.", "작살이 무시무시해졌군! 이제 뱃전만 지키면 되겠어."),
                    new T("닉스 비늘", "뱀의 섬으로 가는 물길은 독이 섞여 있다네.||닉스 비늘 열 개를 갈아 뱃전에 바르면 독물에도 배가 상하지 않지.",
                            "닉스 비늘 열 개일세. 물가의 닉스가 떨어뜨리네.", "물빛 비늘이로군. 이제 배가 뱀의 섬까지 버틸 걸세.||마지막으로, 그 뱀 이야기를 해 주지."),
                    new T("세계를 휘감은 뱀", "요르문간드가 몸을 일으켰네. 세계를 휘감은 뱀이지.||뱀의 섬 제단을 누르면 놈이 나온다네. 독을 조심하게!",
                            "해자의 독물에는 들어가지 말게. 범람하면 흰 고리 안으로 피하게.", "")),
            "nidhogg", List.of(
                    new T("북쪽 무덤의 망자", "북쪽 무덤에서 드라우그가 일어났어. 서리를 두른 망자들이야.||열넷을 다시 잠재워. 맞으면 몸이 굳으니 맞지 말고 베.",
                            "드라우그 열넷. 몸이 굳으면 물러서.", "…망자들이 일어난 건 징조야. 뭔가 큰 게 깨어나고 있어."),
                    new T("서리 뼛조각", "드라우그의 뼈에는 녹지 않는 서리가 남아.||서리 뼛조각 열 개를 가져와. 그 서리가 어디서 오는지 읽어 볼 거야.",
                            "서리 뼛조각 열 개. 드라우그가 떨어뜨려.", "…이 서리, 세계수 뿌리 쪽에서 흘러와."),
                    new T("세계수를 갉는 흑룡", "세계수의 뿌리를 갉는 흑룡, 니드호그가 모습을 드러냈어.||세계수가 쓰러지면 서리를 막을 게 없어. 뿌리의 제단을 눌러서 불러내.",
                            "흑룡이 날아오르면 기다려. 낙인이 찍히면 동료와 떨어져.", "세계수가 숨을 돌렸어. …잘했어.||그런데 하늘이 이상해. 해와 달이 쫓기고 있어.")),
            "skoll_hati", List.of(
                    new T("서리 늑대 무리", "해와 달을 쫓는 늑대들이 내려오기 전에, 그 무리가 먼저 왔어. 서리 늑대야.||하나를 건드리면 울부짖어서 둘레 늑대가 다 몰려와. 하나씩 떼어 내.||열둘. 물리면 몸이 얼어붙으니까 거리를 재.",
                            "서리 늑대 열둘. 울부짖으면 무리가 몰려와.", "…무리가 흩어졌어. 저 늑대들은 하늘의 늑대를 따라온 거야."),
                    new T("서리 늑대 가죽", "서리 늑대 가죽으로 털옷을 지으면 이미르의 숨결도 버틸 수 있어.||가죽 여덟 장. 서리 늑대가 떨어뜨려.",
                            "서리 늑대 가죽 여덟 장. 서리 늑대가 떨어뜨려.", "따뜻해. …고마워.||이제 돌 고리 둘레의 트롤들이야."),
                    new T("돌 고리의 트롤", "해와 달의 돌 고리 둘레를 트롤들이 차지했어.||열을 쓰러뜨려. 상처가 금방 아무니까 쉬지 말고 몰아쳐.",
                            "트롤 열. 재생하기 전에 몰아쳐.", "길이 열렸어. 트롤 맷집이 장난이 아니지?"),
                    new T("트롤 엄니", "늑대들은 트롤 엄니 냄새를 싫어해.||엄니 여덟 개로 부적을 깎아 줄게. 늑대들의 눈을 피할 수 있을 거야.",
                            "트롤 엄니 여덟 개. 트롤이 떨어뜨려.", "부적이 됐어. 이제 늑대들을 불러낼 차례야."),
                    new T("해와 달을 쫓는 늑대", "하늘이 어지러워. 해를 쫓는 스콜과 달을 쫓는 하티가 내려왔어.||돌 고리의 제단을 눌러서 불러내. 낮과 밤에 따라 늑대들이 달라져.",
                            "낮에는 스콜이, 밤에는 하티가 거세져. 시간을 읽어.", "하늘이 제자리로 돌아왔어. …잘했어.||이제 남은 건 하나야. 서리 거인.")),
            "ymir", List.of(
                    new T("거인의 숨결", "이미르의 숨결에 망자들이 다시 일어났어. 빙원을 드라우그가 메우고 있어.||열둘을 잠재워. 거인에게 가는 길을 열어야 해.",
                            "드라우그 열둘. 몸이 굳으면 물러서.", "길이 열렸어. 저 너머가 태초의 빙원이야."),
                    new T("빙원의 서리 거인", "빙원 입구를 이미르의 졸개들이 지키고 있어. 서리 거인이야.||내려치면 둘레가 다 얼어붙어. 바로 옆에 붙어 있지 마.||열. 쓰러뜨려야 거인에게 가는 길이 열려.",
                            "서리 거인 열. 내려칠 때 둘레에서 벗어나.", "…졸개가 이 정도라니. 이미르는 진짜 산만 할 거야."),
                    new T("서리 거인의 핵", "서리 거인의 몸속에는 얼음 핵이 있어. 그 핵이 이미르의 숨결을 부르는 거야.||핵 여섯 개를 가져와. 거꾸로 쓰면 숨결을 막는 결계가 돼.",
                            "서리 거인의 핵 여섯 개. 서리 거인이 떨어뜨려.", "결계가 됐어. 숨결이 오면 이 안으로 들어와.||이제 불씨만 지키면 돼."),
                    new T("빙원의 화로", "이미르의 숨결은 모든 걸 얼려. 불씨를 지킬 화로가 필요해.||트롤 엄니 여덟 개로 화로를 깎을 거야. 엄니를 모아 와.",
                            "트롤 엄니 여덟 개. 트롤이 떨어뜨려.", "화로가 됐어. 이 불씨가 꺼지지 않으면 거인의 숨결도 버틸 수 있어.||…이제 거인을 만나러 가자."),
                    new T("태초의 서리 거인", "태초의 서리 거인, 이미르가 완전히 깨어났어.||빙원의 제단을 눌러서 불러내. 숨결이 오면 불이 켜진 모닥불 곁으로 가.",
                            "숨결이 오면 모닥불 곁으로. 다리를 노려 무릎을 꿇려.", "")));

    /** 요르문간드 · 이미르 완료 대사: legend = 그 길의 전설 무기 (스승의 전설 퀘스트 안내), mat = 제작 주재료 ({mat}) */
    private record BossDone(String legend, String mat) {}

    private static final Map<String, BossDone> BOSS_DONE = Map.of(
            "jormungandr", new BossDone(
                    "놈의 뱃속 깊은 곳에서 무언가 빛나는 걸 봤네. 한 번으로는 토해 내지 않을 모양이야.||자네 스승에게 가 보게. 그분이라면 방법을 알 걸세.",
                    "뱀이 쓰러지며 토해 낸 보물 더미에서 쓸 만한 재료를 골라 두었네.||{mat}일세. 다른 재료와 모아 마을의 군나르에게 가져가면 영웅 무기를 벼려 줄 걸세."),
            "ymir", new BossDone(
                    "…해냈어. 거인이 쓰러지자 얼음 속에서 뭔가가 울렸어.||네 스승한테 가 봐. 네 무기 이야기는 그 사람이 알 거야.",
                    "…해냈어. 거인이 쓰러진 자리에서 이걸 건졌어.||이건 {mat}. 다른 재료와 모아서 군나르에게 가져가. 초월 무기를 벼려 줄 거야."));

    // ------------------------------------------------------------ 퀘스트 만들기

    /** 보상 아이템 키 → 아이템 ID (레이드 재료 · 보스 재료 · ticket · ticket_adv · 전리품 몬스터 키) */
    public static String rewardItemId(String k) {
        Obj m = RAID_MATERIALS.objOrNull(k);
        if (m != null) return m.str("item");
        m = BOSS_MATERIALS.objOrNull(k);
        if (m != null) return m.str("item");
        if (k.equals("ticket")) return RUNE.str("ticketItem");
        if (k.equals("ticket_adv")) return RUNE.str("advTicketItem");
        m = MONSTER_DROPS.objOrNull(k);
        if (m != null) return m.str("item");
        return k;
    }

    /** 아이템 ID → 보이는 이름 */
    public static String rewardItemName(String id) {
        for (String k : RAID_MATERIALS.keys()) if (RAID_MATERIALS.obj(k).str("item").equals(id)) return RAID_MATERIALS.obj(k).str("name");
        for (String k : BOSS_MATERIALS.keys()) if (BOSS_MATERIALS.obj(k).str("item").equals(id)) return BOSS_MATERIALS.obj(k).str("name");
        for (String k : MONSTER_DROPS.keys()) if (MONSTER_DROPS.obj(k).str("item").equals(id)) return MONSTER_DROPS.obj(k).str("name");
        if (id.equals(RUNE.str("ticketItem"))) return RUNE.str("ticketName");
        if (id.equals(RUNE.str("advTicketItem"))) return RUNE.str("advTicketName");
        String n = Names.ITEM_NAMES.get(id);
        return n != null ? n : id.replaceFirst("^minecraft:", "");
    }

    /** [[키, 수], …] → [{id, amount}, …] */
    static List<JsonObject> items(Object... pairs) {
        List<JsonObject> out = new ArrayList<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) out.add(J.o("id", rewardItemId((String) pairs[i]), "amount", pairs[i + 1]));
        return out;
    }

    /** rewardOf: { emerald, xp, points, weapon, item: null, items } */
    static JsonObject rewardOf(int emerald, int xp, int points, String weapon, Object... items) {
        return J.o("emerald", emerald, "xp", xp, "points", points, "weapon", weapon == null ? "" : weapon, "item", JsonNull.INSTANCE, "items", items(items));
    }

    private static JsonObject rewardOf(Spec s) {
        return rewardOf(s.emerald(), s.xp(), s.points(), s.weapon(), s.items());
    }

    /** bossReward 결과: reward = 보상, mat = 준 주재료 키 ("" = 없음) */
    public record BossReward(JsonObject reward, String mat) {}

    /**
     * 요르문간드 (hero) · 이미르 (transcend) 보상: 그 길 · 등급의 무기를 만들 수 있으면 주재료 절반 + 제작 에메랄드 · 별철 광석,
     * 전설 무기 길이면 (만들 수 없음 — 전설 퀘스트) 같은 에메랄드 · 별철 광석만. 룬 뽑기권 1 · 2장, 경험치 2 · 3레벨
     */
    public static BossReward bossReward(String type, String grade) {
        boolean big = grade.equals("transcend");
        int ticket = big ? 2 : 1;
        Obj craft = craftOf(type, grade);
        int emerald = big ? 128 : 64;
        int ore = big ? 4 : 2;
        if (craft == null) return new BossReward(rewardOf(emerald, big ? 3 : 2, 0, "", "ore", ore, "ticket", ticket), "");
        return new BossReward(rewardOf((int) craft.num("emerald"), big ? 3 : 2, 0, "", craft.str("mat"), (int) Math.floor(craft.num("count") / 2), "ore",
                (int) craft.num("ore"), "ticket", ticket), craft.str("mat"));
    }

    /** 몬스터 이름 (목표 글자) */
    private static String mobLabel(String k) {
        Obj m = MONSTERS.obj(k);
        return m.str("name") + "(Lv." + Js.str(m.num("level")) + ")";
    }

    /** 단계 ID: "mq_&lt;길&gt;_sNN" (9단계 46단계 — 예전 31단계 "mq_&lt;길&gt;_rNN" · 18단계 "mq_&lt;길&gt;_NN" 과 겹치지 않게) */
    public static String mainId(String type, int step) {
        return MAIN_PREFIX + type + "_s" + (step < 10 ? "0" : "") + step;
    }

    /** 예전 (v2.9.0 ~ 0.8.x) 31단계 ID "mq_&lt;길&gt;_rNN" */
    static String rId(String type, int step) {
        return MAIN_PREFIX + type + "_r" + (step < 10 ? "0" : "") + step;
    }

    /** 전설 퀘스트 ID (SideQuests) — grade: "hero" · "transcend" */
    public static String legendId(String type, String grade) {
        return "sq_legend_" + type + "_" + grade;
    }

    /** 시작 퀘스트 ID */
    public static final String MAIN_START = MAIN_PREFIX + "start";
    public static final String MAIN_TRIAL = MAIN_PREFIX + "trial";
    public static final String MAIN_CHOOSE = MAIN_PREFIX + "choose";

    /** 대사 속 {master} · {mat} 채우기 */
    private static String fill(String text, String type, String mat) {
        MainPath p = MAIN_PATHS.get(type);
        String matName = "";
        if (mat != null && !mat.isEmpty()) {
            Obj m = BOSS_MATERIALS.objOrNull(mat);
            matName = m != null ? m.str("name") : mat;
        }
        return (text == null ? "" : text).replace("{master}", mainNpcCall(p == null ? "" : p.mentor())).replace("{mat}", matName);
    }

    /**
     * 메인 퀘스트 전체 (정의 그대로 — Quest.normalizeBuiltin 이 다듬어 씀). 추가 필드: path (그 길에서만, "" = 모두), ord (순서),
     * give (수락할 때 주는 시험용 무기 종류), doneBy (길마다 완료 대사: 고르기), finale (마지막), step (길 안의 단계 1 ~ PATH_STEPS)
     */
    public static List<JsonObject> buildMainQuests() {
        List<JsonObject> out = new ArrayList<>();
        out.add(J.o("id", MAIN_START, "ord", 1, "path", "", "giver", "main_1", "type", "talk", "target", "main_2", "targetName", mainNpcCall("main_2"), "count", 1,
                "title", START.title(), "offer", START.offer(), "progress", START.progress(), "done", START.done(), "reward", rewardOf(2, 0, 0, ""), "requires", "", "minLevel", 0));
        out.add(J.o("id", MAIN_TRIAL, "ord", 2, "path", "", "giver", "main_2", "type", "try", "target", "", "targetName", "", "count", PATH_ORDER.size(),
                "title", TRIAL.title(), "offer", TRIAL.offer(), "progress", TRIAL.progress(), "done", TRIAL.done(), "give", new ArrayList<>(PATH_ORDER),
                "reward", rewardOf(3, 1, 0, ""), "requires", MAIN_START, "minLevel", 0));
        out.add(J.o("id", MAIN_CHOOSE, "ord", 3, "path", "", "giver", "main_2", "type", "choose", "target", "", "targetName", "", "count", 1,
                "title", CHOOSE.title(), "offer", CHOOSE.offer(), "progress", CHOOSE.progress(), "done", CHOOSE.done(), "doneBy", new LinkedHashMap<>(CHOSEN),
                "reward", rewardOf(2, 0, 0, ""), "requires", MAIN_TRIAL, "minLevel", 0));

        for (String type : PATH_ORDER) {
            Master m = MASTER.get(type);
            String prev = MAIN_CHOOSE;
            List<Step> steps = pathSteps(type);
            for (int i = 0; i < steps.size(); i++) {
                Step s = steps.get(i);
                int step = i + 1;
                JsonObject q = J.o("id", mainId(type, step), "ord", 10 + step, "step", step, "path", type, "giver", s.giver(), "requires", prev, "minLevel", s.lv(), "count", 1,
                        "target", "", "targetName", "");
                T t = new T("", "", "", "");
                JsonObject reward;
                String mat = "";
                switch (s.kind()) {
                    case "intro", "depart", "travel", "home", "outro" -> {
                        String to = s.to() == null ? "" : s.to();
                        J.put(q, "type", "talk", "target", to, "targetName", mainNpcCall(to));
                        switch (s.kind()) {
                            case "intro" -> {
                                t = m.intro();
                                reward = rewardOf(3, 0, 0, "");
                            }
                            case "depart" -> {
                                t = m.depart().withDone(ARRIVE.get("farm"));
                                reward = rewardOf(TRAVEL_EMERALD.get("farm"), 0, 0, "");
                            }
                            case "travel" -> {
                                t = TRAVEL.get(s.region()).withDone(ARRIVE.get(s.region()));
                                reward = rewardOf(TRAVEL_EMERALD.get(s.region()), 0, 0, "");
                            }
                            case "home" -> {
                                t = TRAVEL.get("home").withDone(m.home());
                                reward = rewardOf(TRAVEL_EMERALD.get("home"), 0, 0, "");
                            }
                            default -> {
                                t = m.outro();
                                reward = rewardOf(50, 0, 3, "", "ticket_adv", 1);
                                J.put(q, "finale", true);
                            }
                        }
                    }
                    default -> {
                        Arc arc = s.arc();
                        String k = s.mob();
                        List<T> texts = ARC_TEXT.get(arc.boss());
                        if (s.kind().equals("kill")) {
                            Task tk = arc.tasks().get(s.task());
                            J.put(q, "type", "kill", "target", "rpg:" + k, "targetName", mobLabel(k), "count", s.count());
                            t = texts.get(s.task());
                            reward = rewardOf(tk.emerald(), tk.xp(), 0, "");
                        } else if (s.kind().equals("collect")) {
                            Task tk = arc.tasks().get(s.task());
                            Obj d = MONSTER_DROPS.obj(k);
                            J.put(q, "type", "collect", "target", d.str("item"), "targetName", d.str("name"), "count", s.count());
                            t = texts.get(s.task());
                            reward = rewardOf(tk.emerald(), tk.xp(), 0, "");
                        } else {
                            String boss = s.boss();
                            J.put(q, "type", "boss", "target", boss, "targetName", BOSSES.obj(boss).str("name"));
                            t = texts.get(texts.size() - 1);
                            if (arc.grade() != null) {
                                BossReward r = bossReward(type, arc.grade());
                                reward = r.reward();
                                mat = r.mat();
                                BossDone bd = BOSS_DONE.get(boss);
                                t = t.withDone(!r.mat().isEmpty() ? bd.mat() : bd.legend());
                            } else reward = rewardOf(arc.reward());
                        }
                    }
                }
                // 단계 표의 "l10_noble" = 그 길의 무기 종류
                String weapon = reward.get("weapon").getAsString();
                if (!weapon.isEmpty() && weapon.matches("^l\\d+_.*")) reward.addProperty("weapon", type + "_" + weapon);
                J.put(q, "title", t.title(), "offer", fill(t.offer(), type, mat), "progress", fill(t.progress(), type, mat), "done", fill(t.done(), type, mat), "reward", reward);
                out.add(q);
                prev = mainId(type, step);
            }
        }
        return out;
    }

    /** 무기를 고른 뒤 처음 받는 퀘스트 (조교 → 스승) */
    public static String firstPathQuest(String type) {
        return mainId(type, 1);
    }

    // ------------------------------------------------------------ 예전 (18단계) 진행 옮기기 (v2.9.0)

    /**
     * 예전 (v2.6.0~2.8.x) 18단계 진행 → 지역 퀘스트 단계. 예전에 끝낸 마지막 단계 (인덱스) → 지역 퀘스트에서 끝낸 것으로 볼 마지막 단계.
     * 같은 레벨의 두 보스 중 하나를 잡았으면 둘 다 끝낸 것으로. 17 이미르는 스승에게 보고했으므로 마을 → 30 (스승에게) 까지
     */
    private static final int[] OLD_TO_NEW = {0, 1, 3, 4, 4, 5, 6, 6, 9, 9, 9, 16, 16, 19, 20, 20, 26, 30, 31};
    /** 예전 보스 단계 → 새 보스 단계: 잡았는데 아직 보고하지 않았으면 새 보스 단계를 보고만 남은 채로 */
    private static final Map<Integer, Integer> OLD_BOSS = Map.of(5, 5, 8, 9, 11, 16, 13, 19, 16, 26, 17, 29);
    /** 받아 둔 예전 말 걸기 단계 → 같은 새 단계 (진행 중 그대로): 1 스승 찾아가기 · 18 영웅의 귀환 */
    private static final Map<Integer, Integer> OLD_TALK = Map.of(1, 1, 18, 31);
    private static final Pattern OLD_ID = Pattern.compile("^mq_(sword|dagger|axe|shield|bow|wand)_(\\d\\d)$");
    /** 예전 (18단계) 에서 그 몬스터를 잡던 단계 — 옮긴 사람의 반복 의뢰가 다시 잠기지 않게 */
    private static final Map<String, Integer> OLD_KILL_STEP = Map.of("scarecrow", 2, "gulon", 4, "skeljaskrimsli", 7, "nix", 9, "draugr", 12, "troll", 15);
    /** 옮긴 기록 표시 (한 번만 옮김). 예전 기록은 지우지 않음 */
    public static final String MIGRATED_KEY = "_mig290";
    /** 전설 퀘스트를 "모두 채움" 으로 둘 때의 목표별 수 */
    private static final int FULL = 9999;

    private static String two(int n) {
        return (n < 10 ? "0" : "") + n;
    }

    /** 예전 기록에서 그 몬스터의 처치 단계를 끝냈는지 */
    public static boolean oldKillDone(Map<String, Quest.Entry> st, String path, String mob) {
        Integer n = OLD_KILL_STEP.get(mob);
        if (n == null || !isPath(path)) return false;
        Quest.Entry e = st.get(MAIN_PREFIX + path + "_" + two(n));
        return e != null && e.c > 0;
    }

    /**
     * 플레이어 진행 ("rpg:qs") 에 예전 메인 단계가 있으면 (처음 한 번) 지역 퀘스트 단계로 옮김. 바꿨으면 true
     * - 끝낸 마지막 단계까지를 끝낸 것으로 (보상은 다시 주지 않음)
     * - 진행 중이던 단계: 보스를 잡고 보고 전이면 그 새 보스 단계를 보고만 남은 채로, 스승 찾아가기 · 영웅의 귀환은 그대로 진행 중, 나머지는 버림
     * - 전설 무기: 예전 요르문간드 (13 — 활 · 방패) · 이미르 (17 — 칼 · 방패) 단계를 끝냈으면 그 전설 퀘스트도 끝,
     *   잡았는데 보고 전이면 그 전설 퀘스트를 모두 채운 채로
     */
    public static boolean migrateMainState(Map<String, Quest.Entry> st, String path) {
        if (st.containsKey(MIGRATED_KEY)) return false;
        int last = 0;
        int ready = 0;
        int talk = 0;
        boolean found = false;
        for (Map.Entry<String, Quest.Entry> en : st.entrySet()) {
            Matcher m = OLD_ID.matcher(en.getKey());
            if (!m.matches()) continue;
            found = true;
            if (!m.group(1).equals(path)) continue;
            Quest.Entry e = en.getValue();
            int n = Integer.parseInt(m.group(2));
            if (e != null && e.c > 0) last = Math.max(last, n);
            else if (e != null && e.active()) {
                if (OLD_TALK.containsKey(n)) talk = n;
                else if (OLD_BOSS.containsKey(n) && e.n >= 1) ready = n;
            }
        }
        if (!found) return false;
        st.put(MIGRATED_KEY, new Quest.Entry("d", 0, 1));
        if (!isPath(path)) return true;
        int to = OLD_TO_NEW[Math.min(OLD_TO_NEW.length - 1, last)];
        int readyAt = ready > last ? OLD_BOSS.get(ready) : 0;
        if (readyAt > 0) to = Math.max(to, readyAt - 1);
        // 31단계 (rNN) 로 적음 — 바로 뒤 migrateRState 가 9단계 46단계 (sNN) 로 옮김
        for (int s = 1; s <= to; s++) done(st, rId(path, s));
        Quest.Entry atReady = readyAt > 0 ? st.get(rId(path, readyAt)) : null;
        Integer talkTo = OLD_TALK.get(talk);
        if (readyAt > 0 && !(atReady != null && atReady.c > 0)) st.put(rId(path, readyAt), new Quest.Entry("a", 1, 0));
        else if (talk > last && talkTo != null && talkTo == to + 1) st.put(rId(path, talkTo), new Quest.Entry("a", 0, 0));
        legend(st, path, "hero", 13, List.of("bow", "shield"), last, ready);
        legend(st, path, "transcend", 17, List.of("sword", "shield"), last, ready);
        return true;
    }

    // ------------------------------------------------------------ 31단계 (rNN) → 9단계 46단계 (sNN) 옮기기

    /** 예전 31단계 (v2.9.0 ~ 0.8.x) 의 단계 표지 (1 ~ 31 — 그때 skeleton 의 차례) */
    private static final List<String> R_SIGS = List.of("intro", "depart",
            "kill:scarecrow_king:scarecrow", "collect:scarecrow_king:scarecrow", "boss:scarecrow_king",
            "travel:desert", "kill:scorpions:gulon", "collect:scorpions:gulon", "boss:scorpions",
            "kill:great_worm:gulon", "collect:great_worm:gulon", "boss:great_worm",
            "travel:sea", "kill:kraken:skeljaskrimsli", "collect:kraken:skeljaskrimsli", "boss:kraken",
            "kill:jormungandr:nix", "collect:jormungandr:nix", "boss:jormungandr",
            "travel:north", "kill:nidhogg:draugr", "collect:nidhogg:draugr", "boss:nidhogg",
            "kill:skoll_hati:troll", "collect:skoll_hati:troll", "boss:skoll_hati",
            "kill:ymir:draugr", "collect:ymir:troll", "boss:ymir", "home", "outro");
    /** 31단계 → 46단계 옮긴 기록 표시 (한 번만). 예전 기록 (rNN) 은 지우지 않음 */
    public static final String MIGRATED_S_KEY = "_mig090";
    private static final Pattern R_ID = Pattern.compile("^mq_(sword|dagger|axe|shield|bow|wand)_r(\\d\\d)$");

    /** 그 표지의 새 단계 번호 (1 ~ PATH_STEPS, 없으면 0) */
    static int stepOfSignature(String sig) {
        for (int i = 0; i < SKELETON.size(); i++) if (signature(SKELETON.get(i)).equals(sig)) return i + 1;
        return 0;
    }

    /** 예전 31단계의 그 단계 (1 ~ 31) 가 옮겨 가는 새 단계 번호 (시험용) */
    public static int rToStep(int r) {
        return r < 1 || r > R_SIGS.size() ? 0 : stepOfSignature(R_SIGS.get(r - 1));
    }

    /**
     * 예전 31단계 진행 → 9단계 46단계 (처음 한 번, 바꿨으면 true):
     * - 끝낸 마지막 단계와 같은 일의 새 단계까지 끝낸 것으로 (그 앞에 새로 생긴 단계도 — 보상은 다시 주지 않음)
     * - 보스를 잡고 보고 전이면 그 새 보스 단계를 보고만 남은 채로 (그 앞은 모두 끝)
     * - 진행 중이던 단계는 새 단계에서도 바로 다음이면 진행 그대로, 아니면 버림 (NPC 에게 다시 받음 — 사이에 새 몬스터 단계가 생김)
     */
    public static boolean migrateRState(Map<String, Quest.Entry> st, String path) {
        if (st.containsKey(MIGRATED_S_KEY)) return false;
        boolean found = false;
        int last = 0;
        int ready = 0;
        int active = 0;
        Quest.Entry activeE = null;
        for (Map.Entry<String, Quest.Entry> en : st.entrySet()) {
            Matcher m = R_ID.matcher(en.getKey());
            if (!m.matches()) continue;
            found = true;
            if (!m.group(1).equals(path)) continue;
            int n = Integer.parseInt(m.group(2));
            Quest.Entry e = en.getValue();
            if (n < 1 || n > R_SIGS.size() || e == null) continue;
            if (e.c > 0) last = Math.max(last, n);
            else if (e.active()) {
                if (R_SIGS.get(n - 1).startsWith("boss:") && e.n >= 1) ready = Math.max(ready, n);
                else if (n > active) {
                    active = n;
                    activeE = e;
                }
            }
        }
        if (!found) return false;
        st.put(MIGRATED_S_KEY, new Quest.Entry("d", 0, 1));
        if (!isPath(path)) return true;
        int to = rToStep(last);
        int readyAt = ready > last ? rToStep(ready) : 0;
        if (readyAt > 0) to = Math.max(to, readyAt - 1);
        for (int s = 1; s <= to; s++) done(st, mainId(path, s));
        if (readyAt > 0) {
            Quest.Entry at = st.get(mainId(path, readyAt));
            if (!(at != null && at.c > 0)) st.put(mainId(path, readyAt), new Quest.Entry("a", 1, 0));
        } else if (active > last && activeE != null) {
            int s = rToStep(active);
            if (s == to + 1) st.put(mainId(path, s), activeE.copy());
        }
        return true;
    }

    private static void done(Map<String, Quest.Entry> st, String id) {
        Quest.Entry e = st.get(id);
        if (!(e != null && e.c > 0)) st.put(id, new Quest.Entry("d", 0, 1));
    }

    private static void legend(Map<String, Quest.Entry> st, String path, String grade, int oldStep, List<String> paths, int last, int ready) {
        if (!paths.contains(path)) return;
        String id = legendId(path, grade);
        if (last >= oldStep) done(st, id);
        else if (ready == oldStep) {
            Quest.Entry e = st.get(id);
            if (!(e != null && e.c > 0)) {
                Quest.Entry n = new Quest.Entry("a", 0, 0);
                n.o = new ArrayList<>(Collections.nCopies(16, FULL));
                st.put(id, n);
            }
        }
    }
}
