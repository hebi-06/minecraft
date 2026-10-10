package mmorpg.fabric.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mmorpg.core.Js;
import mmorpg.fabric.Notify;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.ui.DialogForm;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.Icon;
import net.minecraft.server.level.ServerPlayer;

/**
 * 퀘스트 목록 (quest.js openQuestLog · questLogLoop · questDetail · mainLine · navLine — /quest): 진행 중인 퀘스트 (한 장에 LOG_PAGE 개),
 * 맨 위에 메인 이야기 한 줄 · 길 안내 한 줄, 길 안내 켜기 · 끄기, 퀘스트를 누르면 자세히 (포기 · 이 퀘스트 길 안내).
 */
public final class QuestLog {
    private QuestLog() {}

    /** 퀘스트 목록 한 장에 보이는 수 (휴대폰 화면 높이 기준) */
    static final int LOG_PAGE = 3;
    /** 길 안내 그림 (나침반) */
    static final Icon NAV_ICON = Icon.of("textures/items/compass_item");

    /** /quest: 퀘스트 목록 (창이 열려 있으면 무시) */
    public static void openQuestLog(ServerPlayer sp) {
        if (Forms.isOpen(sp)) return;
        questLogLoop(sp, null);
    }

    /** 퀘스트 목록 (창 흐름 — 끝나면 done, null 이면 없음) */
    public static void questLogLoop(ServerPlayer sp, Runnable done) {
        step(sp, 0, 0, done);
    }

    private static void end(Runnable done) {
        if (done != null) done.run();
    }

    /** 퀘스트 목록 맨 위의 메인 퀘스트 한 줄 (메인 NPC 를 세운 적이 없으면 "") */
    static String mainLine(ServerPlayer sp) {
        if (Quests.pathOf(sp).isEmpty() && Quests.npcNameByKey("main_1").isEmpty()) return "";
        Quest nx = Quests.nextMain(sp);
        if (nx == null) return "§6[메인] §a모든 이야기를 마쳤습니다!";
        String s = Quests.statusOf(sp, nx);
        if (s.equals("active") || s.equals("ready")) return "§6[메인] §f" + nx.title + " §7진행 중";
        if (s.equals("available")) return "§6[메인] §f" + QuestText.giverName(nx) + "§7에게 새 이야기 §8(" + nx.title + ")";
        if (Quests.lockReason(sp, nx).equals("level")) {
            return "§6[메인] §7레벨 " + nx.minLevel + "에 오르면 §f" + QuestText.giverName(nx) + "§7에게 §8(지금 " + Js.str(Quests.questLevel(sp)) + ")";
        }
        return "";
    }

    /** 퀘스트 목록 위의 길 안내 한 줄 */
    static String navLine(ServerPlayer sp) {
        if (Quests.navOf(sp).off()) return "§7길 안내: 꺼짐 §8(아래 버튼 · /nav)";
        Quests.NavGoal g = Quests.navGoal(sp);
        return g != null ? "§b길 안내: §f" + g.q().title : "§7길 안내: 갈 곳 없음";
    }

    private static void step(ServerPlayer sp, int pageIn, int guard, Runnable done) {
        if (guard >= 100 || sp.isRemoved()) {
            end(done);
            return;
        }
        List<Quest> list = new ArrayList<>();
        for (Map.Entry<String, Quest.Entry> en : Quests.stateOf(sp).entrySet()) {
            Quest q = en.getValue().active() ? Quests.getQuest(en.getKey()) : null;
            if (q != null) list.add(q);
        }
        list.sort(Quests::questOrder);
        int pages = Math.max(1, (list.size() + LOG_PAGE - 1) / LOG_PAGE);
        int page = Math.min(pageIn, pages - 1);
        List<Quest> shown = list.subList(page * LOG_PAGE, Math.min(list.size(), page * LOG_PAGE + LOG_PAGE));
        String main = mainLine(sp);
        String head = (main.isEmpty() ? "" : main + "\n") + (list.isEmpty()
                ? "진행 중인 퀘스트가 없습니다.\n§7이름 앞에 §e[!]§7 가 붙은 NPC에게 말을 걸어 보세요."
                : "진행 중인 퀘스트 §e" + list.size() + "개§r" + (pages > 1 ? " §7(" + (page + 1) + "/" + pages + "쪽)" : "") + "\n§7퀘스트를 누르면 자세히 봅니다.");
        DialogForm form = new DialogForm("퀘스트 목록", head + "\n" + navLine(sp));
        for (Quest q : shown) {
            boolean ready = !q.type.equals("talk") && Quests.isReady(sp, q);
            Double n = q.type.equals("talk") ? null : Quests.progressOf(sp, q);
            String label = ready ? "(완료 가능)" : n == null ? "" : "(" + Js.str(Math.min(n, q.count)) + "/" + q.count + ")";
            form.choice(QuestText.titleOf(q, ready ? "§a" : "§f") + " §7" + label, ready ? DialogForm.READY : Icon.of(Quest.TYPES.get(q.type).icon()));
        }
        boolean hasNext = pages > 1;
        if (hasNext) form.choice("§f다음 쪽 §7(" + ((page + 1) % pages + 1) + "/" + pages + ")", DialogForm.NEXT);
        boolean navOff = Quests.navOf(sp).off();
        form.choice(navOff ? "§b길 안내 켜기" : "§7길 안내 끄기", NAV_ICON);
        form.choice("§7닫기", DialogForm.BYE);
        List<Quest> picks = List.copyOf(shown);
        form.show(sp, sel -> {
            if (sel < 0) {
                end(done);
                return;
            }
            if (sel < picks.size()) {
                questDetail(sp, picks.get(sel), () -> step(sp, page, guard + 1, done));
                return;
            }
            if (hasNext && sel == picks.size()) {
                step(sp, (page + 1) % pages, guard + 1, done);
                return;
            }
            if (sel == picks.size() + (hasNext ? 1 : 0)) {
                Quests.setNavOff(sp, !navOff);
                Notify.notify(sp, navOff ? "§b§l길 안내 §r§f켰습니다" : "§7길 안내를 껐습니다 §8(/nav 로 다시 켜기)");
                SkillFx.playFor(sp, "random.click", navOff ? 1.3 : 0.8, 1);
                step(sp, page, guard + 1, done);
                return;
            }
            end(done);
        });
    }

    /** 퀘스트 자세히 (돌아가기 · 포기 · 이 퀘스트 길 안내) */
    static void questDetail(ServerPlayer sp, Quest q, Runnable then) {
        boolean ready = !q.type.equals("talk") && Quests.isReady(sp, q);
        List<String> left = q.type.equals("trial") ? QuestText.trialBossesLeft(q, Quests.trialCounts(sp, q)) : List.of();
        List<String> lines = new ArrayList<>();
        String kind;
        if (q.main) kind = "§6메인 퀘스트" + (q.path.isEmpty() ? "" : " · " + MainQuests.MAIN_PATHS.get(q.path).name());
        else if (q.side.equals("repeat")) kind = "§b반복 의뢰 §7(보고하면 바로 다시 받을 수 있음)";
        else if (q.side.equals("royal")) kind = "§d왕실 퀘스트";
        else if (q.side.equals("legend")) {
            MainQuests.MainPath p = MainQuests.MAIN_PATHS.get(q.path);
            kind = "§c전설 퀘스트 §7(" + (q.path.isEmpty() ? "초월 보스" : p != null ? p.name() : "") + ", 한 번)";
        } else kind = "";
        if (!kind.isEmpty()) lines.add(kind);
        lines.add("§7의뢰: §f" + QuestText.giverName(q));
        lines.add("§7목표: §f" + Quests.objectiveOf(sp, q));
        if (!left.isEmpty() && left.size() <= 8) lines.add("§7남은 보스: §f" + String.join(", ", left));
        if (q.type.equals("boss") && !ready) lines.add("§7보스 제단을 눌러 불러냅니다");
        lines.add("§7보상: §f" + QuestText.rewardText(q, QuestRewards.rewardFor(sp, q)));
        if (ready) lines.add("§a목표 달성! " + QuestText.giverName(q) + "에게 돌아가세요.");
        // 길 안내: 이 퀘스트로 안내 (켜져 있고 지금 다른 퀘스트를 안내하는 중이면)
        Quests.NavGoal cur = Quests.navGoal(sp);
        boolean canPin = !Quests.navOf(sp).off() && (cur == null || !cur.q().id.equals(q.id)) && Quests.goalOf(sp, q) != null;
        DialogForm form = new DialogForm(q.title, String.join("\n", lines)).choice("§f돌아가기", DialogForm.OK).choice("§c퀘스트 포기", DialogForm.BYE);
        if (canPin) form.choice("§b이 퀘스트 길 안내", NAV_ICON);
        form.show(sp, sel -> {
            if (sel == 1) {
                QuestTalk.confirmAbandon(sp, q.title, q, ok -> then.run());
                return;
            }
            if (sel == 2 && canPin) {
                Quests.pinQuest(sp, q.id);
                Notify.notify(sp, "§b§l길 안내 §r§f" + q.title);
                SkillFx.playFor(sp, "random.click", 1.3, 1);
            }
            then.run();
        });
    }
}
