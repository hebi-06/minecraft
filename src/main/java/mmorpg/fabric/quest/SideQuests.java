package mmorpg.fabric.quest;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mmorpg.core.Economy;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Rules;

/**
 * 메인 이야기 밖의 들어 있는 퀘스트 (sidequest.js — ID "sq_…", 편집 창에서 고칠 수 없음):
 * - 반복 의뢰 (side "repeat", 여관 주인 잉그리드 main_10): 몬스터마다 하나 — 메인 이야기에서 그 몬스터 처치 단계를 끝내면 열림.
 *   전리품을 가져오면 실버 (0.21.0 — 예전 에메랄드) + 경험치 (큰 의뢰는 골드도) (포인트 — 최고 레벨이 그 몬스터의 xpUntil 이상이면 0). 보고하면 바로 다시 받을 수 있음
 * - 왕실 퀘스트 (side "royal", 왕녀 시그리드 main_11): 35렙부터, 무기의 길을 고른 사람만. 7단계, 마지막 (이미르) 을 마치면 무기의 길에 맞는 초월 갑옷 한 벌
 * - 전설 퀘스트 (side "legend", 그 길의 스승): 전설 무기 4개는 이 퀘스트로만. 메인의 그 보스 단계를 끝내면 열림, 목표 종류 "trial" (처치 · 보스 목표 여럿)
 * - 초월 보스 전설 퀘스트 (side "legend", 왕녀, 길과 무관): 왕실 퀘스트를 모두 마치고 40렙부터 초월 보스 (수르트) 를 잡음
 * - 나라 의뢰 (side "nation", 2026-11-28 사용자 "번개의 나라 … 거기 가서 퀘스트 받게"): 나라 섬마다 아홉 단계 — 번개 나라 (20 ~ 28렙 · 고헤이 · 히비키 · 도도로키 —
 *   빈 이삭) · 바위 나라 (55 ~ 59렙 · 세베린 · 오르도 · 리브라 — 무게 없는 곳). 말 걸기 · 그 섬 몬스터 처치 · 전리품 · 보스 (나라의 몬스터 · 보스는 일반 몹을 만들 때)
 * 마인크래프트 클래스를 쓰지 않음 (규칙 코어 · Gson 만).
 */
public final class SideQuests {
    private SideQuests() {}

    private static final Rules R = Rules.get();
    private static final Obj BOSSES = R.obj("BOSSES");
    private static final Obj MONSTERS = R.obj("MONSTERS");
    private static final Obj MONSTER_DROPS = R.obj("MONSTER_DROPS");
    private static final Obj REPEAT_QUESTS = R.obj("REPEAT_QUESTS");

    public static final String SIDE_PREFIX = "sq_";
    public static final String REPEAT_GIVER = "main_10";
    public static final String ROYAL_GIVER = "main_11";
    /** 왕실 퀘스트를 받을 수 있는 레벨 */
    public static final int ROYAL_LEVEL = 35;
    /** 무기의 길 → 왕실 퀘스트 보상 갑옷 종류 (ARMOR_TYPES) */
    public static final Map<String, String> PATH_ARMOR = Map.of("sword", "str", "dagger", "dex", "axe", "strvit", "shield", "vit", "bow", "dex", "wand", "int");

    public static String repeatId(String mob) {
        return SIDE_PREFIX + "rep_" + mob;
    }

    public static String royalId(int step) {
        return SIDE_PREFIX + "royal_" + (step < 10 ? "0" : "") + step;
    }

    /** 초월 보스 전설 퀘스트 ID (왕녀가 줌, 무기의 길과 무관) */
    public static String transcendLegendId(String boss) {
        return SIDE_PREFIX + "legend_" + boss;
    }

    /** [제목, 줄 때, 진행 중, 완료] */
    private record T(String title, String offer, String progress, String done) {}

    /** 반복 의뢰 대사 (몬스터마다 — 9단계 새 몬스터 8종 포함) — {n} = 개수 */
    private static final Map<String, T> REPEAT_LINES = Map.ofEntries(
            Map.entry("scarecrow", new T("짚단 거두기", "여관 지붕을 새로 이으려는데 짚이 모자라요.||허수아비가 떨어뜨리는 낡은 짚단 {n}개만 가져다주실래요? 몇 번이든 좋아요!",
                    "낡은 짚단 {n}개요. 허수아비를 쓰러뜨리면 가끔 떨어져요.", "고마워요! 이걸로 지붕이 따뜻해지겠어요.||또 모이면 언제든 가져와 주세요!")),
            Map.entry("nisse", new T("빨간 털모자", "니세들이 쓰는 빨간 털모자 말이에요, 꼬마 손님들이 그렇게 좋아한대요.||{n}개만 구해 주실래요? 여관 아이들 선물로 주려고요!",
                    "니세의 빨간 모자 {n}개요. 농장의 니세가 떨어뜨려요.", "어머, 귀여워라! 아이들이 신나서 뛰어다니겠어요.||또 구하면 가져와 주세요!")),
            Map.entry("boar", new T("멧돼지 엄니 손잡이", "멧돼지 엄니로 만든 칼 손잡이가 잘 안 미끄러진대요. 부엌칼을 바꾸려고요.||엄니 {n}개만 부탁해요!",
                    "멧돼지 엄니 {n}개요. 광폭 멧돼지가 떨어뜨려요. 들이받히지 마세요!", "단단하네요! 이제 고기도 술술 썰리겠어요.||또 부탁해도 되죠?")),
            Map.entry("gulon", new T("털가죽 이불", "겨울 이불을 만들려고요. 굴론 털가죽이 그렇게 따뜻하대요.||{n}장만 가져다주실래요? 손님들이 정말 좋아할 거예요!",
                    "굴론 털가죽 {n}장이요. 사막의 굴론이 떨어뜨려요.", "푹신해라! 오늘 밤 손님들은 따뜻하게 자겠어요.||또 부탁해도 되죠?")),
            Map.entry("sand_scorpion", new T("해독약 재료", "사막에서 온 손님들이 전갈 독에 자주 당해서 와요.||전갈 독침 {n}개만 있으면 해독약을 넉넉히 만들어 둘 수 있대요!",
                    "전갈 독침 {n}개요. 모래 전갈이 떨어뜨려요. 찔리지 않게 조심하세요!", "이제 독에 당한 손님이 와도 걱정 없어요!||또 모이면 가져와 주세요.")),
            Map.entry("skeljaskrimsli", new T("껍질 그릇", "스켈야스크림슬리 껍질로 만든 그릇이 요즘 상인들 사이에서 인기래요.||단단한 껍질 {n}개만 구해 주시면 값을 넉넉히 쳐 드릴게요!",
                    "단단한 껍질 {n}개요. 해안의 껍질 괴물이 떨어뜨려요.", "정말 단단하네요! 상인들이 줄을 서겠어요.||또 구하면 가져와 주세요.")),
            Map.entry("crab", new T("집게발 요리", "어부들이 그러는데 집게 게 집게발을 구우면 그렇게 맛있대요!||{n}개만 구해 주시면 오늘 저녁 특식으로 낼게요.",
                    "게 집게발 {n}개요. 갯바위의 집게 게가 떨어뜨려요.", "냄새부터 다르네요! 손님들이 줄을 서겠어요.||또 부탁드려요!")),
            Map.entry("worm_larva", new T("허물 등갓", "웜 유충 허물에 불을 비추면 은은한 호박색 빛이 난대요.||여관 등갓으로 쓰려고요. 허물 {n}개만 부탁해요!",
                    "유충 허물 {n}개요. 사막의 웜 유충이 떨어뜨려요. 모래가 흘러내리는 곳을 조심하세요!", "와, 방이 노을빛이 됐어요!||또 모이면 가져와 주세요.")),
            Map.entry("nix", new T("물빛 등불", "닉스 비늘에 불을 비추면 물빛이 일렁여서 정말 예뻐요.||여관 등불로 쓰려고요. 비늘 {n}개만 부탁해요!",
                    "닉스 비늘 {n}개요. 물가에서는 조심하세요!", "와, 여관이 호수 속처럼 반짝여요!||또 모이면 가져와 주세요.")),
            Map.entry("grendel", new T("괴물 발톱 장식", "그렌델의 발톱을 문 위에 걸어 두면 나쁜 것이 못 들어온대요.||무섭지만… {n}개만 구해 주실래요?",
                    "그렌델의 발톱 {n}개요. 바닷가 동굴의 그렌델이 떨어뜨려요. 꼭 살아 돌아오세요!", "으, 걸어 두니 든든하네요. 고마워요!||또 부탁해도 될까요?")),
            Map.entry("draugr", new T("얼음 창고", "서리 뼛조각은 녹지 않는 얼음처럼 차갑대요.||창고에 넣어 두면 음식이 상하지 않는다나요? {n}개만 구해 주세요!",
                    "서리 뼛조각 {n}개요. 북쪽 무덤의 드라우그가 떨어뜨려요.", "창고가 얼음장처럼 차가워졌어요! 고마워요.||또 부탁드려요.")),
            Map.entry("frost_wolf", new T("은빛 털목도리", "서리 늑대 가죽으로 목도리를 지으면 은빛으로 반짝인대요.||북쪽에서 온 손님들한테 팔려고요. 가죽 {n}장만 부탁해요!",
                    "서리 늑대 가죽 {n}장이요. 북쪽의 서리 늑대가 떨어뜨려요. 무리를 조심하세요!", "부드러워라! 금방 다 팔리겠어요.||또 구하면 가져와 주세요.")),
            Map.entry("troll", new T("엄니 공예", "트롤 엄니로 깎은 장식은 귀족들도 탐내는 물건이래요.||{n}개만 구해 주시면 제가 상인들한테 잘 팔아 볼게요!",
                    "트롤 엄니 {n}개요. 북쪽 산길의 트롤이 떨어뜨려요. 조심하세요!", "이렇게 큰 엄니라니! 상인들이 깜짝 놀라겠어요.||또 구하면 꼭 가져와 주세요.")),
            Map.entry("frost_giant", new T("녹지 않는 얼음", "서리 거인의 핵은 한여름에도 녹지 않는대요.||여관 얼음 창고에 넣으면 일 년 내내 시원하겠죠? {n}개만 구해 주세요!",
                    "서리 거인의 핵 {n}개요. 빙원의 서리 거인이 떨어뜨려요. 정말 조심하세요!", "창고에서 푸른빛이 나요… 아름다워요!||또 부탁드려요.")),
            // 0.32.0 공허 몬스터 다섯 (메인 이야기 밖 — 레벨로 열림: REPEAT_QUESTS.minLevel)
            Map.entry("void_leaper", new T("균열의 벌레", "요즘 하늘에 금이 가고 그 틈에서 보랏빛 벌레가 기어 나온대요.||학자님이 그 독주머니를 연구하고 싶으시대요. {n}개만 구해 주실래요?",
                    "공허 독주머니 {n}개요. 균열 곁의 공허 도약충이 떨어뜨려요. 무리로 덮치니 조심하세요!", "어머, 주머니 속에서 뭔가 출렁여요…||학자님께 바로 전할게요. 또 부탁드려요!")),
            Map.entry("void_stalker", new T("등 뒤의 칼날", "균열 근처에서 돌아온 사냥꾼들이 뒤에서 베였대요. 아무도 없었는데 말이에요.||그 칼날 조각을 대장장이 군나르 님이 보고 싶대요. {n}개만 부탁해요.",
                    "균열 칼날 {n}개요. 공허 추적자가 떨어뜨려요. 바닥에 보랏빛 금이 열리면 바로 비키세요!", "공간이 일렁여 보이는 칼날이라니…||군나르 님이 밤새 들여다보시겠어요. 고마워요!")),
            Map.entry("void_jelly", new T("떠다니는 등불", "균열 위를 떠다니는 해파리 이야기 들으셨어요? 몸속에서 작은 공허가 맥동한대요.||그 핵 {n}개만 구해 주시면 여관 등불로 써 보려고요. 위험하면 말고요!",
                    "공허 핵 {n}개요. 공허 해파리가 떨어뜨려요. 날아오는 구체는 터지기 전에 피하세요!", "와… 등불 속에서 별이 도는 것 같아요.||또 구하면 가져와 주세요!")),
            Map.entry("void_borer", new T("발밑의 소리", "광산 사람들이 땅이 갈라지며 거대한 벌레가 솟았다고 벌벌 떨어요.||그 턱 조각 {n}개를 가져오면 광산 입구에 걸어 두고 경고하겠대요.",
                    "굴착자의 턱 {n}개요. 공허 굴착자가 떨어뜨려요. 발밑이 빛나면 바로 뛰세요!", "이렇게 단단한 턱이라니, 바위도 뚫겠어요.||광산 사람들이 한시름 놓겠어요. 또 부탁해요!")),
            Map.entry("void_behemoth", new T("공허의 수정", "거수의 등에서 자라는 수정은 빛을 먹는대요. 촛불 곁에 두면 촛불이 어두워진다나요?||그래도 왕실 학자들이 꼭 구해 달래요. {n}개만 부탁드려요.",
                    "공허 수정 {n}개요. 공허 거수가 떨어뜨려요. 땅에서 솟는 가시 줄을 조심하세요!", "정말 빛이 빨려 들어가요… 신기해요.||학자들이 기뻐할 거예요. 또 부탁드려요!")));

    /**
     * 왕실 퀘스트 한 단계. goal: talk (npc) · collect (drop 몬스터 키, count) · boss (boss) · kill (mob, count),
     * reward: items = [아이템 키, 개수] 쌍 (MainQuests.rewardItemId), armor = 무기의 길에 맞는 초월 갑옷 (마지막)
     */
    private record Royal(int lv, String goal, String what, int count, int emerald, Object[] items, String armor, boolean finale, T text) {}

    private static final List<Royal> ROYAL = List.of(
            new Royal(ROYAL_LEVEL, "talk", "main_1", 0, 20, new Object[] {"ticket", 1}, null, false, new T(
                    "왕녀의 편지",
                    "모험가님, 저는 이 나라의 왕녀 시그리드예요. 신분을 숨기고 영웅을 찾아왔어요.||북쪽에서 서리 거인이 깨어나면 왕국도 무사하지 못해요.||먼저 이 편지를 하랄 어르신께 전해 주세요. 선왕과 북쪽 원정을 떠났던 분이라 들었어요.",
                    "하랄 어르신께 왕실의 편지를 전해 주세요.",
                    "왕실의 봉인이로구나… 시그리드 왕녀님이 직접 오셨다고?||선왕 폐하와 북쪽 원정을 떠났던 게 엊그제 같구나. 그때도 서리가 문제였지.||얘야, 왕녀님을 잘 도와 드리거라. 이 할아비의 부탁이다.")),
            new Royal(ROYAL_LEVEL, "collect", "troll", 10, 30, new Object[] {"ticket", 1}, null, false, new T(
                    "산성의 창살",
                    "북쪽 국경의 산성이 트롤들에게 부서지고 있어요.||트롤 엄니는 쇠보다 단단해서 창살로 쓰기에 딱이래요. 엄니 열 개를 구해 주세요.",
                    "트롤 엄니 열 개예요. 북쪽 산길의 트롤이 떨어뜨려요.",
                    "이렇게 굵은 엄니라니… 이걸로 창살을 세우면 트롤도 못 넘어올 거예요.||고마워요. 왕실의 이름으로 감사드려요.")),
            new Royal(36, "talk", "main_9", 0, 20, new Object[] {"stone", 3}, null, false, new T(
                    "왕실 문장",
                    "대장장이 군나르 님께 이 왕실 문장을 보여 주세요.||당신에게 어울리는 갑옷을 맞추려면 치수부터 재야 하거든요. 후후, 아직은 비밀이에요.",
                    "군나르 님께 왕실 문장을 보여 주세요.",
                    "이건 왕실 문장 아닌가! 자네, 대체 무슨 일에 휘말린 겐가?||…황제 갑옷이라고? 전설 속 갑옷을 내 손으로 다시 맞추게 되다니!||자, 팔을 벌려 보게. 치수를 재야지. 왕녀님께 잘 전해 주게!")),
            new Royal(37, "boss", "nidhogg", 0, 40, new Object[] {"ticket", 1, "bright", 1}, null, false, new T(
                    "세계수의 성소",
                    "세계수 뿌리 곁에는 왕실의 오래된 성소가 있어요.||니드호그가 그곳까지 갉아 들어오고 있대요. 흑룡을 쓰러뜨려 성소를 지켜 주세요!",
                    "니드호그는 날아오르면 낙인을 찍어요. 낙인이 찍히면 동료와 떨어지세요.",
                    "성소가 무사하대요! 기도하던 사제들이 울면서 기뻐했어요.||당신 같은 사람이 왕국에 있어서 다행이에요.")),
            new Royal(37, "boss", "skoll_hati", 0, 40, new Object[] {"ticket", 1, "scroll", 1}, null, false, new T(
                    "하늘을 쫓는 늑대",
                    "해를 쫓는 스콜과 달을 쫓는 하티가 왕국의 하늘을 어지럽히고 있어요.||낮과 밤이 뒤섞이면 농사도 항해도 할 수 없어요. 늑대들을 쫓아 주세요!",
                    "낮에는 스콜이, 밤에는 하티가 거세진대요. 시간을 읽으세요.",
                    "하늘이 제자리를 찾았어요! 오늘 밤엔 달도 별도 제대로 떴네요.||이제 마지막 위협만 남았어요.")),
            new Royal(38, "kill", "troll", 15, 40, new Object[] {"ore", 2}, null, false, new T(
                    "국경 토벌",
                    "산성 창살 덕분에 국경이 버티고 있지만, 트롤 무리가 계속 몰려와요.||트롤 열다섯을 쓰러뜨려 국경을 정리해 주세요. 마지막 싸움 전에 길을 열어 둬야 해요.",
                    "트롤은 상처가 금방 아물어요. 쉬지 말고 몰아치세요. 열다섯이에요.",
                    "국경이 조용해졌어요. 병사들이 당신 이름을 외치고 있어요!||이제… 서리 거인만 남았어요.")),
            new Royal(40, "boss", "ymir", 0, 64, new Object[] {"ticket_adv", 1}, "path", true, new T(
                    "서리 거인의 왕관",
                    "선왕 폐하께서 북쪽 원정에서 끝내 쓰러뜨리지 못한 거인, 이미르.||이미르를 쓰러뜨려 주세요. 왕실은 왕국을 지킨 영웅에게 황제 갑옷을 바치기로 했어요.||군나르 님이 당신 치수에 맞춰 손질해 두셨어요. 꼭 살아 돌아와요.",
                    "이미르가 숨을 들이쉬면 불이 켜진 모닥불 곁으로 가세요. 꼭 살아 돌아와요!",
                    "…해냈군요. 왕국의 하늘에서 서리가 걷혔어요.||왕실의 이름으로 당신에게 황제 갑옷을 바칩니다. 오늘부터 당신은 왕실의 수호자예요.||고마워요, 영웅님. 이 은혜는 잊지 않을게요.")));

    /** 왕실 퀘스트 단계 수 */
    public static final int ROYAL_STEPS = ROYAL.size();

    // ------------------------------------------------------------ 나라 의뢰 (2026-11-28)

    public static final String NATION = "nation";

    public static String nationId(String nation, int step) {
        return SIDE_PREFIX + "nation_" + nation + "_" + (step < 10 ? "0" : "") + step;
    }

    /**
     * 나라 의뢰 한 단계: giver = NPC 키 (main_17 …), goal: talk (what = 찾아갈 NPC 키) · kill (what = 몬스터, count) · collect (what = 전리품 몬스터, count) · boss (what = 보스),
     * items = [아이템 키, 개수] 쌍 (MainQuests.rewardItemId), finale = 마지막 단계
     */
    public record NationStep(int lv, String giver, String goal, String what, int count, int emerald, Object[] items, boolean finale, String title, String offer,
            String progress, String done) {}

    /** 나라 의뢰 줄기: id (퀘스트 ID 의 나라 이름) · 이름 · 단계 · NPC 인사말 (키 → [평소, 줄기를 끝낸 뒤]) */
    public record Nation(String id, String name, List<NationStep> steps, Map<String, String[]> greet) {}

    private static NationStep ns(int lv, String giver, String goal, String what, int count, int emerald, Object[] items, String title, String offer, String progress,
            String done) {
        return new NationStep(lv, giver, goal, what, count, emerald, items, false, title, offer, progress, done);
    }

    public static final List<Nation> NATIONS = List.of(
            new Nation("thunder", "번개 나라", List.of(
                    ns(20, "main_17", "talk", "main_18", 0, 16, new Object[] {"ticket", 1}, "빈 이삭",
                            "어서 오시오, 바람의 나라 손님. 여기는 이나에 마을이오.||저 논을 보시오. 벼가 지붕보다 높이 자랐는데, 이삭을 까 보면 속이 비었소.|올해만 그런 게 아니라오. 해마다 더 비어 가오.||이 빈 이삭을 산꼭대기 벼락받이 사당의 히비키에게 갖다주겠소?|북쪽 큰 도리이를 지나 붉은 도리이 길을 따라 오르면 되오.",
                            "산꼭대기 사당의 히비키에게 빈 이삭을 전해 주시오. 붉은 도리이 길을 따라 오르면 되오.",
                            "고헤이 촌장님이 보내셨군요. 이삭을… 아, 또 비었네요.||이나에 님은 번개로 벼를 여물게 해 주시는 분이에요. 요즘은 오히려 넘치게 내려 주시는데,|벼는 크기만 하고 속이 차지 않아요. 무언가가 땅의 힘을 갉아 먹고 있는 것 같아요.||마침 잘 오셨어요. 손을 좀 빌려주실래요?"),
                    ns(20, "main_18", "kill", "worm_larva", 12, 20, new Object[] {"stone", 2}, "무너지는 논둑",
                            "요즘 논둑이 밤새 무너져서 물이 아래 논으로 다 새어 나가요.||둑 밑을 파 보니 웜 유충이 굴을 뚫어 놨더라고요. 사막에서나 보던 벌레가 왜 여기까지 왔을까요.||산허리 논에 유충이 들끓어요. 열두 마리만 물리쳐 주세요.",
                            "산허리 논의 웜 유충 열두 마리예요. 둑 밑에서 튀어나오니 발밑을 조심하세요.",
                            "둑이 다시 물을 머금기 시작했어요! 고마워요.||그런데 이상해요. 유충이 판 굴이 모두 산 위, 이 사당 쪽을 향하고 있었어요.|꼭 무언가를 찾아 올라오는 것처럼요."),
                    ns(21, "main_18", "collect", "worm_larva", 8, 22, new Object[] {"ticket", 1}, "벼락맞이 등롱",
                            "밤마다 사당 기둥에 벼락이 떨어져요. 이나에 님이 논에 힘을 내려 주시는 거예요.||그런데 요즘은 벼락이 자꾸 길을 잃고 엉뚱한 데 떨어진대요.|도리이마다 등롱을 걸어 길을 밝히려는데, 유충 허물에 불을 비추면 호박빛이 은은하게 번져요.||허물 여덟 개만 모아 주실래요?",
                            "유충 허물 여덟 개예요. 웜 유충이 떨어뜨려요.",
                            "와, 등롱마다 노을빛이 도네요. 오늘 밤엔 벼락이 길을 잃지 않을 거예요.||아, 번개 왕 도도로키 님께서 당신을 보자고 하셨어요. 사당 본전에 계세요."),
                    ns(22, "main_19", "kill", "nix", 10, 24, new Object[] {"stone", 2}, "밤마다 줄어드는 물",
                            "그대가 바람의 나라에서 온 모험가인가. 짐은 이 나라의 왕, 도도로키다.||논물이 밤마다 줄어든다는 보고가 올라왔다. 둑이 아니라 물 자체가 사라진다 하더군.|산허리 못에 닉스가 들어앉아 밤마다 논물을 끌어간다.||닉스 열을 물리쳐라. 이 나라의 물은 벼의 것이다.",
                            "산허리 못의 닉스 열이다. 물가에서 노래가 들리면 귀를 막고 칼을 뽑아라.",
                            "수고했다. 물이 다시 논에 머문다는 보고가 왔다.||허나 닉스가 이 산까지 올라온 것은 처음이다. 바다가 이상하다는 소문과 무관하지 않겠지."),
                    ns(23, "main_19", "collect", "nix", 8, 26, new Object[] {"ticket", 1}, "물꼬 비늘",
                            "닉스의 비늘은 물을 한 방울도 새게 하지 않는다 들었다.||논마다 물꼬에 그 비늘을 덧대면, 닉스가 다시 와도 물을 빼 가지 못할 것이다.|닉스 비늘 여덟을 가져오라.",
                            "닉스 비늘 여덟이다. 닉스가 떨어뜨린다.",
                            "좋다. 물꼬마다 비늘을 덧대라 일러 두겠다.||…그대 덕에 논은 숨을 돌렸다. 허나 짐에게는 더 큰 걱정이 있다."),
                    ns(25, "main_19", "boss", "kraken", 0, 40, new Object[] {"ticket", 1, "kraken_ink", 1}, "쌀배의 뱃길",
                            "이 나라의 남는 쌀은 배에 실려 바람의 나라로 간다. 쌀이 오가야 여러 나라가 배를 곯지 않는다.||그런데 크라켄이 바다 섬 앞 뱃길에 자리를 잡고 쌀배를 끌어내리고 있다.|벌써 세 척이다. 뱃사람들이 바다에 나가기를 두려워한다.||크라켄을 쓰러뜨려 뱃길을 열어라. 바다 섬 서쪽 물목의 제단에서 불러낼 수 있다.",
                            "크라켄은 바다 섬 서쪽 물목의 보스 제단에서 불러낸다. 다리를 조심하라.",
                            "뱃길이 열렸다는 소식이 벌써 닿았다. 쌀배가 다시 돛을 올렸다.||짐이 그대에게 빚을 졌구나.|고헤이가 곳간 일로 그대를 찾더군. 마을에 내려가 보라."),
                    ns(26, "main_17", "kill", "grendel", 10, 28, new Object[] {"stone", 3}, "곳간의 그림자",
                            "왕께 들었소. 쌀배를 지켜 주셨다지. 고맙소.||헌데 곳간에도 일이 생겼소. 밤마다 그렌델이 내려와 곳간 문을 부수고 볏섬을 끌고 가오.|서쪽과 동쪽 숲에 놈들의 굴이 있소.||그렌델 열을 물리쳐 주시오.",
                            "서쪽 · 동쪽 숲의 그렌델 열이오. 놈들은 해가 지면 더 사나워지오.",
                            "이제 곳간지기들이 발 뻗고 자겠소.||헌데 놈들이 끌고 간 볏섬을 찾아보니 다 비어 있었다오. 놈들도 굶주렸던 게지."),
                    ns(27, "main_17", "collect", "grendel", 6, 30, new Object[] {"ticket", 1, "scroll", 1}, "발톱 낫",
                            "벼가 이렇게 높이 자라니 낫이 들지 않소. 줄기가 쇠처럼 질기다오.||그렌델 발톱은 쇠보다 단단하다 들었소. 발톱으로 낫을 벼리면 이 벼도 베어지겠지.|그렌델의 발톱 여섯 개를 구해 주시오.",
                            "그렌델의 발톱 여섯 개요. 그렌델이 떨어뜨리오.",
                            "허허, 낫이 줄기를 쓱쓱 베는구려!||…왕께서 사당에서 기다리신다 하오. 마지막으로 크게 부탁하실 일이 있으시다오."),
                    new NationStep(28, "main_19", "boss", "great_worm", 0, 56, new Object[] {"ticket_adv", 1, "worm_heart", 1}, true, "유충의 어미",
                            "히비키가 유충 굴의 방향을 일러 주었다. 굴은 모두 이 산, 사당의 벼락 기둥을 향해 있었다.||유충은 땅의 힘을, 벼락이 내린 힘을 찾아 오른 것이다. 그 어미는 사막 섬의 거대 웜이다.|어미가 살아 있는 한 유충은 끝없이 바다를 건너올 것이다.||거대 웜을 쓰러뜨려라. 그대가 돌아오면 오늘 밤 벼락맞이를 함께 보리라.",
                            "거대 웜은 사막 섬 남서쪽 신전의 보스 제단에서 불러낸다. 땅이 울리면 바로 피하라.",
                            "…들리는가. 벼락이 기둥에 떨어지는 소리가.||오늘 밤 벼락은 길을 잃지 않았다. 등롱을 따라 곧장 기둥에 내려앉았다.|히비키가 그러더군. 벼락을 맞은 논에서 속이 찬 이삭이 몇 나왔다고. 아직 몇뿐이지만, 시작이다.||번개 왕의 이름으로 그대를 이 나라의 벗으로 삼는다. 이나에의 은혜가 그대와 함께하기를.")),
                    Map.of("main_17", new String[] {"어서 오시오. 이나에 마을이오. 벼가 지붕보다 높다고 놀라지 마시오.", "우리 마을의 은인이 오셨구려! 햅쌀로 지은 밥 한 그릇 드시고 가오."},
                            "main_18", new String[] {"벼락받이 사당에 오신 걸 환영해요. 밤에 오시면 기둥에 벼락이 떨어지는 걸 볼 수 있어요.", "등롱 덕분에 요즘 벼락이 길을 잃지 않아요. 고마워요!"},
                            "main_19", new String[] {"짐은 번개 왕 도도로키다. 이나에의 은혜가 그대에게도 닿기를.", "이 나라의 벗이 왔구나. 오늘 밤 벼락맞이를 함께 보겠는가."})),
            new Nation("sage", "바위 나라", List.of(
                    ns(55, "main_20", "talk", "main_21", 0, 40, new Object[] {"ticket", 1}, "저울의 섬",
                            "바위 나라 현자 회의의 사절, 세베린입니다. 붉은 모래땅에서 징조를 재다 돌아왔지요.||저 위를 보십시오. 큰 저울입니다.|두 접시가 이 섬의 두 구역이고, 받침점에 현자 회의가 있습니다. 저울은 300년째 수평으로 멈춰 있지요.||의장께서 당신을 뵙고 싶어 하십니다. 받침점 아래 회의장으로 가 주십시오.",
                            "큰 저울 받침점 아래 회의장의 오르도 의장께 가 주십시오. 접시 계단을 내려가 동쪽 길을 따라가면 됩니다.",
                            "세베린이 보낸 분이군요. 현자 회의 의장, 오르도입니다.||본론을 말하겠습니다. 이 섬 동북쪽에 돌과 물건이 무게를 잃고 떠오르는 곳이 생겼습니다. 우리는 그곳을 '무게 없는 곳'이라 부릅니다.|회의는 까닭을 재고 있으나, 아직 아무것도 정하지 못했습니다."),
                    ns(55, "main_21", "talk", "main_22", 0, 40, new Object[] {"stone", 3}, "300년의 안건",
                            "이 섬의 모든 무게는 그라비스 님이 정하신 것입니다. 회의는 그 무게를 재고, 정해지지 않은 안건은 결코 넘기지 않습니다.|그래서 저울은 300년째 수평입니다.||무게 없는 곳은 새 안건입니다. 안건을 올리려면 먼저 무게를 재야 합니다.|동쪽 접시의 저울지기 리브라를 찾아가십시오. 이 나라에서 가장 정확한 저울을 가진 사람입니다.",
                            "동쪽 접시의 저울지기 리브라를 찾아가십시오.",
                            "의장님이 보내셨다고요? 잘됐어요, 일손이 모자랐거든요.||무게는 거짓말을 하지 않아요. 그런데 요즘 동북쪽에서 잰 것들은 하나같이 거짓말을 해요.|돌을 올리면 저울이 위로 올라가요. 아래가 아니라요."),
                    ns(55, "main_22", "kill", "void_jelly", 12, 44, new Object[] {"stone", 3}, "떠오르는 해파리",
                            "무게 없는 곳 위로 공허 해파리가 떠다녀요.||해파리가 떠다니는 자리일수록 땅이 더 가벼워져요. 원인인지 결과인지는 아직 몰라요.|재려면 일단 치워야 해요. 열두 마리만 물리쳐 주세요.",
                            "동북쪽 무게 없는 곳의 공허 해파리 열둘이에요. 날아오는 구체는 터지기 전에 피하세요.",
                            "해파리를 치우니 떠 있던 돌 몇 개가 내려앉았어요. 몇 개만요.||나머지는 여전히 떠 있어요. 해파리가 원인은 아니었네요. 기록해 둘게요."),
                    ns(56, "main_22", "collect", "void_jelly", 8, 46, new Object[] {"ticket", 1}, "무게가 없는 핵",
                            "해파리 몸속의 핵을 재 보고 싶어요.||제 생각이 맞다면… 아니, 재기 전엔 말하지 않을래요. 재지 않은 말은 소문일 뿐이니까요.|공허 핵 여덟 개를 가져다주세요.",
                            "공허 핵 여덟 개예요. 공허 해파리가 떨어뜨려요.",
                            "재 봤어요. 핵 여덟 개를 접시에 올리니까… 저울이 빈 접시보다 더 위로 올라갔어요.||무게가 없는 게 아니라, 무게가 모자라요. 0보다 작아요.|이건 이 세상의 물건이 아니에요. 세베린 님께 알려야겠어요."),
                    ns(56, "main_20", "kill", "void_borer", 10, 46, new Object[] {"stone", 3}, "땅 밑을 갉는 것",
                            "리브라의 측정을 들었습니다. 0보다 작은 무게라니, 기록에 없는 값입니다.||재 둔 것이 하나 더 있습니다. 남쪽 들의 땅이 해마다 조금씩 가라앉고 있습니다. 밑에서 공허 굴착자가 갉고 있기 때문입니다.|굴착자 열을 물리쳐 주십시오.",
                            "남쪽 들의 공허 굴착자 열입니다. 발밑이 빛나면 바로 뛰십시오.",
                            "가라앉던 땅이 멈췄습니다. 수치로 확인했습니다.||그런데 굴착자가 판 굴 벽에 이상한 것이 있었습니다. 글자 같은 무늬입니다."),
                    ns(57, "main_20", "collect", "void_borer", 6, 48, new Object[] {"ticket", 1, "scroll", 1}, "턱에 묻은 글자",
                            "굴착자의 턱에 굴 벽의 가루가 묻어 있다고 들었습니다.||그 무늬가 첫 말과 닮았습니다. 신들의 진명을 적는 글자 말입니다.|땅 밑에 왜 첫 말이 있는지 알아야 합니다. 굴착자의 턱 여섯 개를 가져와 주십시오.",
                            "굴착자의 턱 여섯 개입니다. 공허 굴착자가 떨어뜨립니다.",
                            "…틀림없습니다. 첫 말입니다. 아주 오래된 글자라 다 읽지는 못하겠습니다.||읽을 수 있는 것은 한 낱말뿐입니다. '받치다.'|땅 밑에 무언가를 받치라는 글이 있다는 뜻일까요. 의장께 보고하겠습니다."),
                    ns(58, "main_21", "kill", "void_behemoth", 8, 52, new Object[] {"bright", 1}, "무게 없는 곳의 거수",
                            "세베린의 보고를 들었습니다. 회의는 이 안건을 서둘러 다루기로 했습니다. 300년 만의 일입니다.||무게 없는 곳 한가운데에 공허 거수들이 자리를 잡았습니다. 거수가 있는 한 아무도 그곳을 잴 수 없습니다.|거수 여덟을 물리쳐 주십시오.",
                            "무게 없는 곳 한가운데의 공허 거수 여덟입니다. 땅에서 솟는 가시 줄을 조심하십시오.",
                            "거수가 물러나자 리브라가 한가운데까지 들어가 쟀습니다.||그곳의 무게는 0보다 훨씬 작았습니다. 그리고 리브라가 그러더군요. 균열 곶 쪽을 향할수록 더 작아진다고."),
                    ns(58, "main_21", "boss", "void_knight", 0, 64, new Object[] {"ticket", 1, "rift_shard", 1}, "무거운 맹세",
                            "그런데 이상한 기록이 하나 있습니다. 균열 곶 끝, 공허의 기사가 선 자리만은 무게가 그대로라는 것입니다.||공허 바로 앞인데도 말입니다. 회의는 그것을 '맹세의 무게'라 부르기로 했습니다.|그 기사와 겨루어 주십시오. 그가 지키는 무게가 무엇인지, 칼끝으로 재어 와 주십시오.",
                            "공허의 기사는 균열 곶 끝 신전의 보스 제단에서 불러냅니다. 그의 칼은 무겁습니다.",
                            "…돌아오셨군요. 칼이 부딪칠 때마다 땅이 무거워졌다는 리브라의 기록이 먼저 닿았습니다.||맹세는 무게를 가집니다. 회의는 이것을 첫 번째 사실로 적었습니다.|이제 마지막 측정만 남았습니다. 리브라에게 가 주십시오."),
                    new NationStep(59, "main_22", "collect", "void_behemoth", 6, 72, new Object[] {"ticket_adv", 1, "void_heart", 1}, true, "떨리는 저울",
                            "마지막 측정이에요. 큰 저울로 재야 해요.||한쪽 접시에 거수의 공허 수정을, 다른 쪽에 이 섬의 돌을 올릴 거예요.|300년 동안 아무것도 저울을 움직이지 못했어요. 공허라면… 움직일지도 몰라요.||공허 수정 여섯 개를 가져다주세요.",
                            "공허 수정 여섯 개예요. 공허 거수가 떨어뜨려요.",
                            "올렸어요. 보세요…||…떨렸어요. 300년 동안 꿈쩍도 않던 큰 저울이, 아주 조금이지만 떨렸어요.|공허 수정 쪽이 위로요. 공허는 무게를 빼앗아 가는 거예요.||회의가 300년 만에 새 안건을 올렸대요. '무게 없는 곳의 무게를 되찾는다.'|기록 맨 앞에 당신 이름이 적힐 거예요. 고마워요, 정확한 사람.")),
                    Map.of("main_20", new String[] {"바위 나라에 오신 것을 환영합니다. 이곳에서는 모든 것을 잽니다. 말도, 걸음도.", "당신의 측정은 정확했습니다. 회의의 기록이 그것을 증명합니다."},
                            "main_21", new String[] {"현자 회의 의장 오르도입니다. 정해지지 않은 일은 정해지지 않은 채로 둡니다. 그것이 규칙입니다.", "300년 만에 안건을 올리게 해 준 분이군요. 회의는 당신을 기억할 것입니다."},
                            "main_22", new String[] {"저울지기 리브라예요. 무게는 거짓말을 하지 않아요. 요즘은 빼고요.", "저울이 떨리던 그날을 잊지 못할 거예요. 당신 덕분이에요."})));

    /** 이 NPC 가 나오는 나라 (없으면 null) */
    public static Nation nationOfNpc(String key) {
        for (Nation n : NATIONS) if (n.greet().containsKey(key)) return n;
        return null;
    }

    /** 퀘스트 ID 의 나라 (나라 의뢰가 아니면 null) */
    public static Nation nationOfQuest(String questId) {
        for (Nation n : NATIONS) if (questId.startsWith(SIDE_PREFIX + "nation_" + n.id() + "_")) return n;
        return null;
    }

    /** 전설 퀘스트 목표 (kind: kill = mobs 중 어느 것이든 · boss = 그 보스) */
    private static JsonObject goalKill(int count, String label, String... mobs) {
        return J.o("kind", "kill", "mobs", List.of(mobs), "count", count, "label", label);
    }

    private static List<JsonObject> seaGoals() {
        return List.of(goalKill(40, "바다 몬스터", "skeljaskrimsli", "crab", "nix", "grendel"), J.o("kind", "boss", "boss", "jormungandr", "count", 3));
    }

    private static List<JsonObject> northGoals() {
        List<JsonObject> out = new ArrayList<>();
        out.add(goalKill(40, "북쪽 몬스터", "draugr", "frost_wolf", "troll", "frost_giant"));
        for (String boss : R.strings("BOSS_ORDER")) out.add(J.o("kind", "boss", "boss", boss, "count", 1));
        return out;
    }

    /** 전설 퀘스트 하나: path 의 스승이 줌. after = 메인 이야기에서 이 보스를 잡는 단계를 끝내면 열림, lv = 최소 레벨, weapon = 보상 (전설 무기) */
    public record Legend(String path, String grade, String weapon, String after, int lv, List<JsonObject> goals, String title, String offer, String progress, String done) {}

    /** 전설 퀘스트 4개 */
    public static final List<Legend> LEGENDS = List.of(
            new Legend("shield", "hero", "shield_l30_hero", "jormungandr", 30, seaGoals(), "비요른의 방패",
                    "요르문간드를 쓰러뜨렸다니 장하네. 하지만 스승님의 방패는 아직 그 뱀의 뱃속 깊은 곳에 있을 걸세.||뱀은 쓰러져도 다시 일어나지. 세 번 쓰러뜨려 그 방패를 토해 내게 하게.||그리고 바다 몬스터 마흔을 물리쳐 바닷길을 지키게. 그것이 비요른 경의 방패를 들 자격일세.",
                    "바다 몬스터 마흔, 요르문간드 세 번일세. 스승님의 방패를 부탁하네.",
                    "이 방패는… 틀림없이 스승님의 방패일세!||비요른의 방패, 자네가 가지게. 스승님도 그것을 바라실 걸세."),
            new Legend("bow", "hero", "bow_l30_hero", "jormungandr", 30, seaGoals(), "잃어버린 엘프의 활",
                    "요르문간드를 쓰러뜨렸다고요? 정말 대단해요!||하지만 엘프의 활은 아직 뱀의 뱃속 깊은 곳에 있을 거예요. 세 번은 쓰러뜨려야 토해 낼 거예요.||바다 몬스터 마흔도 물리쳐 줘요. 제 숲을 삼킨 바다를 깨끗하게 해 주고 싶어요.",
                    "바다 몬스터 마흔, 요르문간드 세 번이에요. 부탁해요.",
                    "이 활… 정말 엘프의 활이에요! 다시 볼 수 있을 줄은 몰랐어요.||당신이 가져요. 이 활은 당신 손에서 다시 노래할 거예요."),
            new Legend("sword", "transcend", "sword_l40_transcend", "ymir", 40, northGoals(), "그람의 부활",
                    "이미르를 쓰러뜨렸다니… 네가 해냈구나.||하지만 그람을 되살리려면 이 세상 모든 괴수를 이겨 낸 칼끝이 필요하다.||보스 여덟을 종류마다 한 번씩, 그리고 북쪽 몬스터 마흔을 베어라. 그때 그람이 깨어날 게다.",
                    "보스 여덟 종류를 하나씩, 북쪽 몬스터 마흔이다. 그람이 기다린다.",
                    "…보아라, 이 붉은 불꽃을. 그람이 다시 숨을 쉰다.||삼십 년 전 내가 잃은 검이 네 손에서 다시 살아났다. 너야말로 이 검의 주인이다."),
            new Legend("shield", "transcend", "shield_l40_transcend", "ymir", 40, northGoals(), "스발린",
                    "이미르의 얼음이 녹은 자리에서 태양을 막는 방패, 스발린의 기운이 느껴진다네.||하지만 그 방패는 모든 괴수를 이겨 낸 자에게만 모습을 드러낸다고 하지.||보스 여덟을 종류마다 한 번씩, 그리고 북쪽 몬스터 마흔을 물리치게. 그때 스발린이 자네를 고를 걸세.",
                    "보스 여덟 종류를 하나씩, 북쪽 몬스터 마흔일세.",
                    "얼음 속에서 이것이 나왔네… 스발린!||해 앞에 서서 세상이 불타지 않게 막는다는 방패가 자네를 주인으로 골랐군. 받게. 자네가 가질 자격이 있네."));

    /** 초월 보스 전설 퀘스트 인사말 (왕녀): offer 받을 수 있음 · active 진행 중 · done 잡음 */
    public record LegendGreet(String offer, String active, String done) {}

    /**
     * 초월 보스 전설 퀘스트 (왕녀가 줌): boss = 잡을 초월 보스, lv = 최소 레벨, 왕실 퀘스트 마지막 단계를 끝내면 열림.
     * items = [아이템 키, 개수] 쌍, honor = 끝냈을 때 큰 글자 (무기 보상이 없으므로 칭호로 축하)
     */
    public record TranscendLegend(String boss, int lv, int emerald, Object[] items, String honor, LegendGreet greet, String title, String offer, String progress, String done) {}

    public static final List<TranscendLegend> TRANSCEND_LEGENDS = List.of(
            new TranscendLegend("surtr", 40, 128, new Object[] {"ticket_adv", 2, "laevateinn_shard", 2, "muspel_heart", 1}, "무스펠의 불을 끈 자",
                    new LegendGreet("왕실의 수호자님, 남쪽 하늘이 밤마다 붉게 타고 있어요. 드릴 말씀이 있어요.",
                            "수르트의 불꽃은 신전째로 녹여요. 꼭 살아 돌아와요, 영웅님.",
                            "무스펠의 불을 끈 영웅님! 왕국 어디를 가도 당신 이야기뿐이에요."),
                    "무스펠의 불꽃 거인",
                    "왕실의 수호자님, 남쪽 하늘이 밤마다 붉게 타고 있어요.||무스펠헤임의 불의 거인, 수르트가 깨어났대요.|옛 신전째로 세상을 태우려 한대요.||선왕 폐하의 기록에 따르면 그 거인은 쉽게 쓰러지지 않는대요.|칼이 땅에 박힌 틈을 노리고, 신전의 사슬과 얼음을 쓰래요.||이건 왕국만의 일이 아니에요. 부탁드려요, 영웅님.",
                    "칼이 금빛으로 번쩍이면 스킬로 반격하고, 업화가 타오르면 모두 함께 쳐서 꺼뜨리세요!",
                    "…남쪽 하늘이 다시 파랗게 돌아왔어요.||무스펠의 불꽃을 끈 영웅이라니, 왕국의 노래가 하나 더 생기겠어요.||거인이 남긴 불씨는 대장장이들이 탐낼 거예요.|언젠가 쓸 곳이 생길 거예요. 잘 간직해 두세요."));

    private static JsonObject reward(int emerald, String weapon, Object[] items) {
        return J.o("emerald", emerald, "xp", 0, "points", 0, "weapon", weapon, "item", JsonNull.INSTANCE, "items", MainQuests.items(items));
    }

    /** 0.21.0 골드 보상 (메인 퀘스트와 같은 식 — 실버 ÷ mainGoldPer, 보스면 + bossStepGold) */
    private static JsonObject withGold(JsonObject reward, boolean boss) {
        int gold = Economy.mainGold(reward.get("emerald").getAsInt(), boss);
        if (gold > 0) reward.addProperty("gold", gold);
        return reward;
    }

    /**
     * 들어 있는 퀘스트 전체 (반복 의뢰 19 + 왕실 7 + 전설 4 + 나라 의뢰 18 (2026-11-28 — 나라마다 9) + 초월 보스 전설 1 — 정의 그대로, Quest.normalizeBuiltin 이 다듬어 씀).
     * 추가 필드: side, ord (순서), afterStep (이 메인 단계를 끝내면 열림), xpMob (경험치 제한 몬스터), reward.xpPoints, reward.armor ("path"),
     * finale (왕실 마지막), honor (초월 보스 전설: 끝낼 때 큰 글자)
     */
    public static List<JsonObject> buildSideQuests() {
        List<JsonObject> out = new ArrayList<>();
        List<String> order = R.strings("MONSTER_ORDER");
        for (int i = 0; i < order.size(); i++) {
            String mob = order.get(i);
            Obj rq = REPEAT_QUESTS.objOrNull(mob);
            Obj d = MONSTER_DROPS.objOrNull(mob);
            T l = REPEAT_LINES.get(mob);
            if (rq == null || d == null || l == null) continue;
            String n = Js.str(rq.num("count"));
            JsonObject reward = J.o("emerald", rq.num("emerald"), "xp", 0, "points", 0, "weapon", "", "item", JsonNull.INSTANCE, "items", List.of(), "xpPoints", rq.num("xp"));
            int gold = Economy.repeatGold((int) rq.num("emerald")); // 0.21.0 큰 의뢰만 골드
            if (gold > 0) reward.addProperty("gold", gold);
            out.add(J.o("id", repeatId(mob), "side", "repeat", "ord", 100 + i, "giver", REPEAT_GIVER, "type", "collect", "target", d.str("item"), "targetName", d.str("name"),
                    "count", rq.num("count"), "title", l.title(), "offer", l.offer().replace("{n}", n), "progress", l.progress().replace("{n}", n),
                    "done", l.done().replace("{n}", n), "reward", reward, "repeat", true, "requires", "", "afterStep", MainQuests.mainKillStep(mob), "xpMob", mob,
                    "minLevel", rq.numOr("minLevel", 0))); // 0.32.0 공허 몬스터: 메인 처치 단계가 없어 레벨로 열림
        }
        String prev = MainQuests.MAIN_CHOOSE;
        for (int i = 0; i < ROYAL.size(); i++) {
            Royal r = ROYAL.get(i);
            int step = i + 1;
            JsonObject reward = withGold(J.o("emerald", r.emerald(), "xp", 0, "points", 0, "weapon", "", "item", JsonNull.INSTANCE, "items", MainQuests.items(r.items()),
                    "armor", r.armor() == null ? "" : r.armor()), !List.of("talk", "collect", "kill").contains(r.goal()));
            JsonObject q = J.o("id", royalId(step), "side", "royal", "ord", 200 + step, "step", step, "giver", ROYAL_GIVER, "title", r.text().title(), "offer", r.text().offer(),
                    "progress", r.text().progress(), "done", r.text().done(), "count", 1, "target", "", "targetName", "", "reward", reward, "repeat", false,
                    "requires", prev, "minLevel", r.lv(), "finale", r.finale());
            switch (r.goal()) {
                case "talk" -> J.put(q, "type", "talk", "target", r.what(), "targetName", MainQuests.mainNpcCall(r.what()));
                case "collect" -> J.put(q, "type", "collect", "target", MONSTER_DROPS.obj(r.what()).str("item"), "targetName", MONSTER_DROPS.obj(r.what()).str("name"),
                        "count", r.count());
                case "kill" -> J.put(q, "type", "kill", "target", "rpg:" + r.what(), "targetName",
                        MONSTERS.obj(r.what()).str("name") + "(Lv." + Js.str(MONSTERS.obj(r.what()).num("level")) + ")", "count", r.count());
                default -> {
                    Obj b = BOSSES.objOrNull(r.what());
                    J.put(q, "type", "boss", "target", r.what(), "targetName", b != null ? b.str("name") : r.what());
                }
            }
            out.add(q);
            prev = royalId(step);
        }
        // 전설 퀘스트 (그 무기의 길의 스승, 메인의 그 보스 단계 뒤)
        for (int i = 0; i < LEGENDS.size(); i++) {
            Legend l = LEGENDS.get(i);
            int count = 0;
            for (JsonObject g : l.goals()) count += g.get("count").getAsInt();
            out.add(J.o("id", MainQuests.legendId(l.path(), l.grade()), "side", "legend", "ord", 300 + i, "path", l.path(), "giver", MainQuests.MAIN_PATHS.get(l.path()).mentor(),
                    "type", "trial", "target", "", "targetName", l.title(), "goals", l.goals(), "count", count, "title", l.title(), "offer", l.offer(), "progress", l.progress(),
                    "done", l.done(), "reward", withGold(reward(0, l.weapon(), new Object[0]), true), "legend", true, "repeat", false, "requires", "",
                    "afterStep", MainQuests.mainBossStep(l.after()), "minLevel", l.lv()));
        }
        // 나라 의뢰 (나라마다 아홉 단계 — 차례대로)
        for (int ni = 0; ni < NATIONS.size(); ni++) {
            Nation nat = NATIONS.get(ni);
            String before = "";
            for (int i = 0; i < nat.steps().size(); i++) {
                NationStep st = nat.steps().get(i);
                int step = i + 1;
                JsonObject reward = withGold(J.o("emerald", st.emerald(), "xp", 0, "points", 0, "weapon", "", "item", JsonNull.INSTANCE, "items", MainQuests.items(st.items())),
                        st.goal().equals("boss"));
                JsonObject q = J.o("id", nationId(nat.id(), step), "side", NATION, "ord", 400 + ni * 20 + step, "step", step, "giver", st.giver(), "title", st.title(),
                        "offer", st.offer(), "progress", st.progress(), "done", st.done(), "count", 1, "target", "", "targetName", "", "reward", reward, "repeat", false,
                        "requires", before, "minLevel", st.lv(), "finale", st.finale());
                switch (st.goal()) {
                    case "talk" -> J.put(q, "type", "talk", "target", st.what(), "targetName", MainQuests.mainNpcCall(st.what()));
                    case "collect" -> J.put(q, "type", "collect", "target", MONSTER_DROPS.obj(st.what()).str("item"), "targetName", MONSTER_DROPS.obj(st.what()).str("name"),
                            "count", st.count());
                    case "kill" -> J.put(q, "type", "kill", "target", "rpg:" + st.what(), "targetName",
                            MONSTERS.obj(st.what()).str("name") + "(Lv." + Js.str(MONSTERS.obj(st.what()).num("level")) + ")", "count", st.count());
                    default -> {
                        Obj b = BOSSES.objOrNull(st.what());
                        J.put(q, "type", "boss", "target", st.what(), "targetName", b != null ? b.str("name") : st.what());
                    }
                }
                out.add(q);
                before = nationId(nat.id(), step);
            }
        }
        // 초월 보스 전설 퀘스트 (왕녀, 왕실 퀘스트 마지막 단계 뒤)
        for (int i = 0; i < TRANSCEND_LEGENDS.size(); i++) {
            TranscendLegend l = TRANSCEND_LEGENDS.get(i);
            Obj b = BOSSES.objOrNull(l.boss());
            out.add(J.o("id", transcendLegendId(l.boss()), "side", "legend", "ord", 320 + i, "giver", ROYAL_GIVER, "type", "boss", "target", l.boss(),
                    "targetName", b != null ? b.str("name") : l.boss(), "count", 1, "title", l.title(), "offer", l.offer(), "progress", l.progress(), "done", l.done(),
                    "reward", withGold(reward(l.emerald(), "", l.items()), true), "legend", true, "honor", l.honor(), "repeat", false, "requires", royalId(ROYAL_STEPS),
                    "minLevel", l.lv()));
        }
        return out;
    }
}
