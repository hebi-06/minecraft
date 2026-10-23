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
            new Legend("shield", "hero", "shield_l30_hero", "jormungandr", 30, seaGoals(), "듀란드의 방패",
                    "요르문간드를 쓰러뜨렸다니 장하네. 하지만 스승님의 방패는 아직 그 뱀의 뱃속 깊은 곳에 있을 걸세.||뱀은 쓰러져도 다시 일어나지. 세 번 쓰러뜨려 그 방패를 토해 내게 하게.||그리고 바다 몬스터 마흔을 물리쳐 바닷길을 지키게. 그것이 듀란드 경의 방패를 들 자격일세.",
                    "바다 몬스터 마흔, 요르문간드 세 번일세. 스승님의 방패를 부탁하네.",
                    "이 방패는… 틀림없이 스승님의 방패일세!||듀란드의 방패, 자네가 가지게. 스승님도 그것을 바라실 걸세."),
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
     * 들어 있는 퀘스트 전체 (반복 의뢰 14 (9단계 새 몬스터 8 포함) + 왕실 7 + 전설 4 + 초월 보스 전설 1 — 정의 그대로, Quest.normalizeBuiltin 이 다듬어 씀).
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
