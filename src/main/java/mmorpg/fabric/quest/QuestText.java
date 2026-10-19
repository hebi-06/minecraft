package mmorpg.fabric.quest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;

/**
 * 퀘스트 글자 (quest.js 의 문구 함수 — 플레이어 없이 정의만으로): 목표 · 보상 · 조건 · 머리표 · 선택지 이름 (clipText — forms.js),
 * 전설의 시련 목표, 사냥터 추천 (loot.js huntKind). NPC 이름 · 퀘스트 찾기는 Quests 가 채우는 훅 (npcName · questById) 으로.
 * 마인크래프트 클래스를 쓰지 않음 (규칙 코어만).
 */
public final class QuestText {
    private QuestText() {}

    private static final Rules R = Rules.get();
    private static final Obj MONSTERS = R.obj("MONSTERS");
    private static final Obj BOSSES = R.obj("BOSSES");
    private static final Obj ARMOR_TYPES = R.obj("ARMOR_TYPES");
    private static final Obj STAT_INFO = R.obj("STAT_INFO");
    private static final Obj TYPES = R.obj("TYPES");
    private static final String CURRENCY_NAME = R.obj("CONFIG").str("CURRENCY_NAME");
    /** 왕실 갑옷 이름 (황제) */
    public static final String EMPEROR = R.obj("ARMOR_TIERS").obj("transcend_40").str("name");

    /** NPC 번호 → 세운 NPC 의 이름 (없으면 "" — Quests 가 세계 기록으로 채움) */
    public static Function<String, String> npcName = k -> "";
    /** 퀘스트 ID → 정의 (없으면 null — Quests 가 채움) */
    public static Function<String, Quest> questById = id -> null;

    /** 선택지에 들어가는 퀘스트 이름 폭 (뒤에 상태 표시가 붙음) */
    static final int TITLE_WIDTH = 160;
    static final String MAIN_TAG = "§6[메인] ";
    /** "[메인] " 폭 (이름 폭은 그대로 두고 앞에 붙임) — "[반복] " · "[왕실] " · "[전설] " 도 같은 폭 */
    static final int MAIN_TAG_WIDTH = 34;
    /** 반복 의뢰 · 왕실 · 전설 머리표 (선택지 · 목록 · 알림) */
    static final Map<String, String> SIDE_TAG = Map.of("repeat", "§b[반복] ", "royal", "§d[왕실] ", "legend", "§c[전설] ");

    /** 퀘스트 머리표 ("" = 만든 퀘스트) */
    public static String tagOf(Quest q) {
        if (q.main) return MAIN_TAG;
        if (!q.side.isEmpty()) return SIDE_TAG.getOrDefault(q.side, "");
        return "";
    }

    /** 알림 앞머리 (색 다음 굵게): "§6§l[메인] " · "§b§l[반복] " · … · 만든 퀘스트 "§e§l" */
    public static String noticeTag(Quest q) {
        String t = tagOf(q);
        return t.isEmpty() ? "§e§l" : t.substring(0, 2) + "§l" + t.substring(2);
    }

    // ------------------------------------------------------------ 이름 · 목표

    /** 주는 NPC 이름 (세운 NPC → 메인 NPC 이름 → "(NPC 없음)") */
    public static String giverName(Quest q) {
        String n = npcName.apply(q.giver);
        if (n != null && !n.isEmpty()) return n;
        n = MainQuests.mainNpcCall(q.giver);
        return n.isEmpty() ? "(NPC 없음)" : n;
    }

    /** 목표 대상 이름 */
    public static String targetLabel(Quest q) {
        if (!q.targetName.isEmpty()) return q.targetName;
        if (q.type.equals("talk")) {
            String n = npcName.apply(q.target);
            if (n != null && !n.isEmpty()) return n;
            n = MainQuests.mainNpcCall(q.target);
            return n.isEmpty() ? "NPC" : n;
        }
        if (q.type.equals("boss")) {
            Obj b = BOSSES.objOrNull(q.target);
            return b != null ? b.str("name") : q.target;
        }
        String s = Names.shortId(q.target);
        return s.isEmpty() ? "대상" : s;
    }

    private static String bossName(String key) {
        Obj b = key == null ? null : BOSSES.objOrNull(key);
        return b != null ? b.str("name") : String.valueOf(key);
    }

    /** counts 의 i 번째 (없으면 0) */
    private static int at(List<Integer> counts, int i) {
        return counts != null && i < counts.size() && counts.get(i) != null ? counts.get(i) : 0;
    }

    /**
     * 전설의 시련 목표 글자: "바다 몬스터 12/40마리 · 요르문간드 1/3번", 보스가 모두 한 번씩이면 "보스 8종 각 1번 3/8". counts = 목표마다 진행 (null 이면 진행 없이)
     */
    public static String trialText(Quest q, List<Integer> counts) {
        List<Quest.Goal> goals = q.goals == null ? List.of() : q.goals;
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < goals.size(); i++) {
            Quest.Goal g = goals.get(i);
            if (g.kind().equals("kill")) parts.add(g.label() + " " + (counts != null ? Math.min(at(counts, i), g.count()) + "/" : "") + g.count() + "마리");
        }
        List<Integer> bosses = new ArrayList<>();
        for (int i = 0; i < goals.size(); i++) if (goals.get(i).kind().equals("boss")) bosses.add(i);
        boolean allOnce = true;
        for (int i : bosses) if (goals.get(i).count() != 1) allOnce = false;
        if (bosses.size() > 1 && allOnce) {
            int done = 0;
            if (counts != null) for (int i : bosses) if (at(counts, i) >= 1) done++;
            parts.add("보스 " + bosses.size() + "종 각 1번" + (counts != null ? " " + done + "/" + bosses.size() : ""));
        } else {
            for (int i : bosses) {
                Quest.Goal g = goals.get(i);
                parts.add(bossName(g.boss()) + " " + (counts != null ? Math.min(at(counts, i), g.count()) + "/" : "") + g.count() + "번");
            }
        }
        return String.join(" · ", parts);
    }

    /** 전설의 시련: 아직 잡지 않은 보스 이름 */
    public static List<String> trialBossesLeft(Quest q, List<Integer> counts) {
        List<String> out = new ArrayList<>();
        List<Quest.Goal> goals = q.goals == null ? List.of() : q.goals;
        for (int i = 0; i < goals.size(); i++) {
            Quest.Goal g = goals.get(i);
            if (g.kind().equals("boss") && at(counts, i) < g.count()) out.add(bossName(g.boss()));
        }
        return out;
    }

    /** 목표 글자 (진행 없이) */
    public static String objectiveText(Quest q) {
        return objectiveText(q, null);
    }

    /** 목표 글자. n = 진행도 (null 이 아니면 괄호로 — 레벨은 "(현재 n)") */
    public static String objectiveText(Quest q, Double n) {
        String t = targetLabel(q);
        String prog = n == null ? "" : " (" + Js.str(Math.min(n, q.count)) + "/" + q.count + ")";
        return switch (q.type) {
            case "kill" -> t + " " + q.count + "마리 처치" + prog;
            case "collect" -> t + " " + q.count + "개 가져오기" + prog;
            case "talk" -> t + "에게 말 걸기";
            case "try" -> "무기 " + q.count + "종으로 한 번씩 때리기" + prog;
            case "choose" -> "무기 하나 고르기";
            case "boss" -> {
                Obj b = BOSSES.objOrNull(q.target);
                Double lv = b == null ? null : b.numOrNull("level");
                yield "보스 " + t + (lv != null && lv != 0 ? "(Lv." + Js.str(lv) + ")" : "") + " 처치" + prog;
            }
            case "trial" -> trialText(q, null);
            default -> "레벨 " + q.count + " 달성" + (n == null ? "" : " (현재 " + Js.str(n) + ")");
        };
    }

    /** 아이템 보상이 RPG 무기면 지금 규칙의 무기 (구버전 무기는 같은 등급의 가장 가까운 레벨로 — 아니면 null) */
    public static Weapon rewardItemWeapon(Quest.RewardItem item) {
        return item == null ? null : Weapons.currentWeapon(Weapons.parseWeapon(item.id));
    }

    /** 무기 보상 (없으면 null) */
    public static Weapon rewardWeapon(Quest.Reward r) {
        return r.weapon.isEmpty() ? null : Weapons.currentWeapon(Weapons.parseWeapon("rpg:" + r.weapon));
    }

    /** 보상 글자 (정의의 보상) */
    public static String rewardText(Quest q) {
        return rewardText(q, q.reward);
    }

    /** 보상 글자. rw = 실제로 주는 보상 (고르기는 고른 무기, rewardFor: 경험치 제한 · 갑옷 종류) */
    public static String rewardText(Quest q, Quest.Reward r) {
        List<String> parts = new ArrayList<>();
        if (q.type.equals("choose") && r.weapon.isEmpty()) parts.add("고른 무기");
        if (r.emerald > 0) parts.add(CURRENCY_NAME + " " + r.emerald);
        if (r.gold > 0) parts.add("§6골드 " + r.gold + "§r§f");
        if (r.xp > 0) parts.add("경험치 " + r.xp + "레벨분"); // 정의 (받을 때 그 레벨 수만큼의 바닐라 경험치로 — 레벨업 3배)
        if (r.xpPoints > 0) parts.add("경험치 " + r.xpPoints);
        else if (!r.capped.isEmpty() && MONSTERS.objOrNull(r.capped) != null) {
            parts.add("§7경험치 없음 (최고 레벨 " + Js.str(MONSTERS.obj(r.capped).num("xpUntil")) + "부터)§f");
        }
        if (r.points > 0) parts.add("스탯 포인트 " + r.points);
        if (!r.armorType.isEmpty() && ARMOR_TYPES.objOrNull(r.armorType) != null) {
            parts.add("§c" + EMPEROR + " 갑옷 세트§r§f (" + ARMOR_TYPES.obj(r.armorType).str("name") + ", 초월)");
        } else if (r.armor.equals("path")) parts.add("§c" + EMPEROR + " 갑옷 세트§r§f (무기의 길에 맞는 종류, 초월)");
        Weapon w = rewardWeapon(r);
        if (w != null) parts.add(w.name() + "§r§f");
        if (r.item != null) {
            Weapon iw = rewardItemWeapon(r.item);
            String tag = r.item.nameTag != null && !r.item.nameTag.isEmpty() ? r.item.nameTag : null;
            if (iw != null) parts.add((tag != null ? tag : iw.name()) + "§r§f ×" + r.item.amount);
            else parts.add((!r.item.name.isEmpty() ? r.item.name : Names.shortId(r.item.id)) + " ×" + r.item.amount);
        }
        for (Quest.RewardItem it : r.items) parts.add(MainQuests.rewardItemName(it.id) + " ×" + it.amount);
        return parts.isEmpty() ? "없음" : String.join("§7, §f", parts);
    }

    /** 조건 글자 (편집 창 · 관리자) */
    public static String conditionText(Quest q) {
        List<String> parts = new ArrayList<>();
        if (q.main) parts.add(!q.path.isEmpty() ? "메인 퀘스트 · " + MainQuests.MAIN_PATHS.get(q.path).name() : "메인 퀘스트");
        if (q.side.equals("repeat")) parts.add("반복 의뢰 · 메인 퀘스트 " + q.afterStep + "단계 (그 몬스터 처치) 뒤");
        if (q.side.equals("royal")) parts.add("왕실 퀘스트 · 무기의 길을 고른 뒤");
        if (q.side.equals("legend")) {
            MainQuests.MainPath p = MainQuests.MAIN_PATHS.get(q.path);
            parts.add(!q.path.isEmpty() ? "전설 퀘스트 · " + (p != null ? p.name() : "") + " · 메인 퀘스트 " + q.afterStep + "단계 (보스) 뒤" : "전설 퀘스트 · 초월 보스 (무기의 길과 무관)");
        }
        if (!q.requires.isEmpty()) {
            Quest r = questById.apply(q.requires);
            parts.add("먼저 끝낼 퀘스트: " + (r != null ? r.title : "(지워짐)"));
        }
        if (q.minLevel > 0) parts.add("레벨 " + q.minLevel + " 이상");
        if (q.repeat) parts.add("반복 가능");
        return parts.isEmpty() ? "없음" : String.join(", ", parts);
    }

    // ------------------------------------------------------------ 대사 · 선택지

    /** 저장된 대사를 장으로 (forms.js toPages: "||" = 다음 장, "|" = 줄바꿈 — DialogForm.toPages 와 같음) */
    public static List<String> toPages(String text) {
        List<String> out = new ArrayList<>();
        for (String p : (text == null ? "" : text).split("\\|\\|", -1)) {
            String page = String.join("\n", Arrays.asList(p.split("\\|", -1))).strip();
            if (!page.isEmpty()) out.add(page);
        }
        return out;
    }

    /** 마지막 장 (없으면 fallback) */
    public static String lastPage(String text, String fallback) {
        List<String> pages = toPages(text);
        return pages.isEmpty() ? fallback : pages.get(pages.size() - 1);
    }

    /** 글자 폭 (forms.js glyphWidth — 대화창 UI 픽셀 대략) */
    private static int glyphWidth(int c) {
        if (c == 0x20) return 4;
        if ((c >= 0x1100 && c <= 0x11ff) || (c >= 0x3000 && c <= 0x9fff) || (c >= 0xac00 && c <= 0xd7a3) || (c >= 0xff00 && c <= 0xffef)) return 9;
        return 6;
    }

    /** 보이는 폭이 maxPx 를 넘으면 잘라서 "..." (forms.js clipText — § 서식은 폭에 넣지 않음) */
    public static String clipText(String text, int maxPx) {
        int[] cps = (text == null ? "" : text).codePoints().toArray();
        int width = 0;
        for (int i = 0; i < cps.length; i++) {
            if (cps[i] == '§') i++;
            else width += glyphWidth(cps[i]);
        }
        if (width <= maxPx) return text == null ? "" : text;
        int limit = maxPx - 18;
        int w = 0;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < cps.length; i++) {
            int ch = cps[i];
            if (ch == '§') {
                out.appendCodePoint(ch);
                if (i + 1 < cps.length) out.appendCodePoint(cps[i + 1]);
                i++;
                continue;
            }
            int g = glyphWidth(ch);
            if (w + g > limit) break;
            w += g;
            out.appendCodePoint(ch);
        }
        return out + "§r...";
    }

    /** 선택지 · 목록에 보이는 퀘스트 이름 (메인 · 반복 · 왕실 · 전설은 앞에 머리표) */
    public static String titleOf(Quest q, String color) {
        String tag = tagOf(q);
        return !tag.isEmpty() ? clipText(tag + color + q.title, TITLE_WIDTH + MAIN_TAG_WIDTH) : color + clipText(q.title, TITLE_WIDTH);
    }

    /** 주 스탯 이름 (도끼는 힘 + 체력) */
    public static String statText(String type) {
        Obj t = TYPES.obj(type);
        List<String> stats = new ArrayList<>();
        stats.add(t.str("stat"));
        Obj extra = t.objOrNull("reqExtra");
        if (extra != null) stats.addAll(extra.keys());
        List<String> names = new ArrayList<>();
        for (String s : stats) {
            Obj info = STAT_INFO.objOrNull(s);
            names.add(info != null ? info.str("name") : s);
        }
        return String.join(" + ", names);
    }

    // ------------------------------------------------------------ 사냥터 추천 (loot.js huntKind)

    /** 레벨에 맞는 몬스터 (몬스터 레벨 ≤ 내 레벨 + 2 중 가장 높은 것) */
    public static String huntKind(double lv) {
        List<String> order = R.strings("MONSTER_ORDER");
        String best = order.get(0);
        for (String k : order) if (MONSTERS.obj(k).num("level") <= lv + 2) best = k;
        return best;
    }

    /** 레벨에 맞는 사냥터 이름 */
    public static String huntHint(double lv) {
        Obj m = MONSTERS.obj(huntKind(lv));
        return m.str("name") + "(Lv." + Js.str(m.num("level")) + ")";
    }
}
