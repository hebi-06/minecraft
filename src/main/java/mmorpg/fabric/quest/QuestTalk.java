package mmorpg.fabric.quest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.Notify;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.rune.RuneSmith;
import mmorpg.fabric.shop.Store;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.smith.Smith;
import mmorpg.fabric.ui.DialogForm;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.Icon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * NPC 대화 (quest.js talkTo · talkLoop · offerFlow · progressFlow · waitFlow · chooseFlow · choosePath · confirmAbandon · turnInFlow · fullBag · mainGreeting):
 * 인사말 (여러 장) → 퀘스트 선택지 (한 장에 TALK_PAGE 개, 넘치면 "다른 퀘스트 보기") + 상점 · 대장장이 · 룬술사 · 안녕.
 * 애드온의 await 고리는 받을 일 (콜백) 되부름으로: 창을 닫아도 애드온처럼 흐름이 이어짐 (예: 진행 중 창을 닫으면 대화창으로 돌아옴),
 * 흐름이 끝나면 done (null 이면 없음).
 */
public final class QuestTalk {
    private QuestTalk() {}

    private static final Rules R = Rules.get();
    private static final Obj TYPES = R.obj("TYPES");
    /** NPC 대화창 한 장에 보이는 퀘스트 수 */
    static final int TALK_PAGE = 3;
    private static final Map<String, Integer> KIND_ORDER = Map.ofEntries(Map.entry("deliver", 0), Map.entry("turnin", 0), Map.entry("offer", 1), Map.entry("progress", 2),
            Map.entry("wait", 2), Map.entry("more", 3), Map.entry("smith", 4), Map.entry("enhance", 4), Map.entry("craft", 4), Map.entry("artifact", 4), Map.entry("shop", 4), Map.entry("armor", 4),
            Map.entry("runesmith", 4), Map.entry("job", 4), Map.entry("jobup", 4), Map.entry("bye", 5));
    /** 대화창의 일거리 칸 (퀘스트가 아닌 것 — 0.27.0 직업 교관 job) */
    private static final Set<String> SERVICE = Set.of("shop", "armor", "smith", "enhance", "craft", "artifact", "runesmith", "job", "jobup", "bye");

    /** 대화창 선택지 하나 (kind: deliver · turnin · offer · progress · wait · more · 일거리) */
    record Option(String kind, Quest q, String text, Icon icon) {}

    private static String status(ServerPlayer sp, Quest q) {
        return Quests.statusOf(sp, q);
    }

    /** 이 NPC 와의 대화 선택지 (순서: 보고 · 전달 → 메인 → 왕실 · 전설 → 만든 퀘스트 → 반복 의뢰 → 일거리) */
    static List<Option> dialogOptions(ServerPlayer sp, String key, int lv, boolean smith, boolean runesmith) {
        List<Option> opts = new ArrayList<>();
        for (Quest q : Quests.talkTargetsOf(key)) {
            Quest.Entry e = Quests.stateOf(sp).get(q.id);
            if (!q.giver.equals(key) && e != null && e.active()) opts.add(new Option("deliver", q, QuestText.titleOf(q, "§a") + " §7(전달)", DialogForm.READY));
        }
        for (Quest q : Quests.questsOf(key)) {
            String s = status(sp, q);
            if (s.equals("ready")) opts.add(new Option("turnin", q, QuestText.titleOf(q, "§a") + " §7(완료 보고)", DialogForm.READY));
            else if (s.equals("available")) opts.add(new Option("offer", q, QuestText.titleOf(q, "§e"), DialogForm.NEW));
            else if (s.equals("active")) opts.add(new Option("progress", q, QuestText.titleOf(q, "§f") + " §7(진행 중)", DialogForm.ACTIVE));
            // 메인 · 왕실 · 전설: 레벨만 모자란 다음 이야기는 필요한 레벨과 함께 보여 줌
            else if (s.equals("locked") && (q.main || q.side.equals("royal") || q.side.equals("legend")) && Quests.lockReason(sp, q).equals("level")) {
                opts.add(new Option("wait", q, QuestText.titleOf(q, "§7") + " §8(레벨 " + q.minLevel + ")", Icon.of(Quest.TYPES.get("level").icon())));
            }
        }
        if (smith) opts.add(new Option("enhance", null, "§6무기 강화 §7(+1~+10)", Icon.of("textures/items/nether_star")));
        if (smith) opts.add(new Option("smith", null, "§6무기 레벨 올리기 §7(영웅 · 초월)", Icon.of("textures/items/iron_ingot")));
        // 0.36.0 제작 창 바로 가기 (보스 무기 · 공허의 갑옷 — 창 안에서도 레벨 올리기 창과 오감)
        if (smith) opts.add(new Option("craft", null, "§6보스 무기 · §5공허의 갑옷 §6제작", Icon.of("textures/items/iron_sword")));
        // 0.37.0 아티팩트 제작 바로 가기 (제작 창의 아티팩트 쪽)
        if (smith && Smith.artifactPageIndex() >= 0) {
            opts.add(new Option("artifact", null, "§d아티팩트 제작 §7(반지 · 목걸이 · 귀걸이 · 팔찌)", Icon.item(mmorpg.core.Artifacts.id("ring", "hero"))));
        }
        if (lv >= 0) opts.add(new Option("shop", null, "§b무기 상점 보기", DialogForm.SHOP));
        if (lv >= 0) opts.add(new Option("armor", null, "§b갑옷 상점 보기", Icon.of("textures/items/iron_chestplate")));
        if (runesmith) opts.add(new Option("runesmith", null, "§d룬 갈기 · 돌파 · 뽑기권", Icon.of(R.obj("RUNE_SMITH").str("shardIcon"))));
        opts.add(new Option("bye", null, "§7대화 끝내기", DialogForm.BYE));
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < opts.size(); i++) idx.add(i);
        idx.sort(Comparator.<Integer>comparingInt(i -> first(opts.get(i))).thenComparingInt(i -> group(opts.get(i)))
                .thenComparingInt(i -> KIND_ORDER.getOrDefault(opts.get(i).kind(), 9)).thenComparingInt(i -> i));
        List<Option> out = new ArrayList<>();
        for (int i : idx) out.add(opts.get(i));
        return out;
    }

    private static int first(Option o) {
        return o.kind().equals("deliver") || o.kind().equals("turnin") ? 0 : 1;
    }

    private static int group(Option o) {
        Quest q = o.q();
        if (q == null) return 9;
        if (q.main) return 0;
        if (q.side.equals("royal") || q.side.equals("legend")) return 1;
        return q.side.equals("repeat") ? 3 : 2;
    }

    /** 보고 · 전달하거나 받을 퀘스트가 남았는지 (완료 보고 뒤 대화를 이어 갈지) */
    static boolean hasMoreToDo(ServerPlayer sp, String key) {
        for (Option o : dialogOptions(sp, key, -1, false, false)) {
            if (o.kind().equals("deliver") || o.kind().equals("turnin") || o.kind().equals("offer")) return true;
        }
        return false;
    }

    /** NPC 를 누름 (창이 열려 있으면 무시 — chestui.js withFormLock) */
    public static void talkTo(ServerPlayer sp, Entity npc) {
        if (Forms.isOpen(sp)) return;
        talkLoop(sp, npc, null);
    }

    /** 대화 하나의 흐름 상태 (애드온 talkLoop 의 지역 변수) */
    private static final class Talk {
        final ServerPlayer sp;
        final Entity npc;
        final String key;
        final String name;
        final Runnable done;
        boolean first = true;
        int page;
        int guard;

        Talk(ServerPlayer sp, Entity npc, Runnable done) {
            this.sp = sp;
            this.npc = npc;
            this.key = NpcData.key(npc, true);
            this.name = NpcData.name(npc);
            this.done = done;
        }

        void end() {
            if (done != null) done.run();
        }
    }

    /** NPC 대화 (창 흐름 — 편집 창의 "대화 미리보기" 도). 끝나면 done (null 이면 없음) */
    public static void talkLoop(ServerPlayer sp, Entity npc, Runnable done) {
        step(new Talk(sp, npc, done));
    }

    private static void step(Talk t) {
        if (t.guard++ >= 50) {
            t.end();
            return;
        }
        ServerPlayer sp = t.sp;
        if (t.npc.isRemoved() || !t.npc.isAlive() || sp.isRemoved()) {
            t.end();
            return;
        }
        int lv = NpcData.shopLevel(t.npc);
        boolean smith = NpcData.isSmith(t.npc);
        boolean runesmith = NpcData.isRunesmith(t.npc);
        String greet = NpcData.greeting(t.npc);
        if (greet.isEmpty()) greet = mainGreeting(sp, t.key);
        List<Option> all = dialogOptions(sp, t.key, lv, smith, runesmith);
        // 0.27.0 직업 교관: "직업 고르기 · 바꾸기" (대화 끝내기 바로 앞)
        boolean job = NpcData.isJobTrainer(t.npc);
        if (job) {
            int bye = all.size();
            for (int i = 0; i < all.size(); i++) if (all.get(i).kind().equals("bye")) bye = i;
            all.add(bye, new Option("job", null, mmorpg.fabric.job.JobTrainer.optionText(sp), mmorpg.fabric.ui.Icon.of("textures/items/book_enchanted")));
            // 0.31.0 직업 스킬 강화 (직업이 있을 때 — 직업 바꾸기 다음)
            String up = mmorpg.fabric.job.JobTrainer.upOptionText(sp);
            if (up != null) all.add(bye + 1, new Option("jobup", null, up, mmorpg.fabric.ui.Icon.item(net.minecraft.world.item.Items.NETHER_STAR)));
        }
        List<Option> questOpts = new ArrayList<>();
        for (Option o : all) if (!SERVICE.contains(o.kind())) questOpts.add(o);
        boolean hasQuest = !questOpts.isEmpty();
        // 휴대폰 화면 높이에 맞게 퀘스트는 한 장에 TALK_PAGE 개까지, 넘치면 "다른 퀘스트" 버튼
        int pages = Math.max(1, (questOpts.size() + TALK_PAGE - 1) / TALK_PAGE);
        t.page = t.page % pages;
        List<Option> opts = new ArrayList<>(questOpts.subList(t.page * TALK_PAGE, Math.min(questOpts.size(), t.page * TALK_PAGE + TALK_PAGE)));
        if (pages > 1) opts.add(new Option("more", null, "§f다른 퀘스트 보기 §7(" + (t.page + 1) + "/" + pages + ")", DialogForm.NEXT));
        for (Option o : all) if (SERVICE.contains(o.kind())) opts.add(o);
        // 인사말도 퀘스트도 없는 상점 · 대장장이 · 룬술사 NPC 는 바로 그 창을 엶 (둘 이상이면 대화창에서 고름)
        int services = (lv >= 0 ? 1 : 0) + (smith ? 1 : 0) + (runesmith ? 1 : 0) + (job ? 1 : 0);
        if (t.first && greet.isEmpty() && !hasQuest && services == 1) {
            if (smith) Smith.smithLoop(sp, "enhance", t.done); // 대장장이는 강화 창부터
            else if (runesmith) RuneSmith.runesmithLoop(sp, "grind", t.done);
            else if (job) mmorpg.fabric.job.JobTrainer.open(sp, t.done); // 0.27.0 직업 교관: 전직 창부터
            else Store.storeLoop(sp, lv, "weapons", t.done);
            return;
        }
        String fallback = smith ? "무기를 벼려 주지. 강화석이나 별철 광석은 챙겨 왔나?"
                : runesmith ? "룬은 돌고 도는 법이지. 쓰지 않는 룬이 있으면 가져오게."
                : job ? "길을 정할 때가 되었군. 손에 맞는 무기를 따라 직업을 고르게."
                : lv >= 0 ? "어서 오게. 좋은 무기와 갑옷이 많다네." : "안녕하신가, 모험가.";
        if (t.first) {
            t.first = false;
            DialogForm.showPages(sp, t.name, greet, fallback, text -> {
                if (text == null) t.end();
                else choose(t, text, opts, lv);
            });
        } else choose(t, QuestText.lastPage(greet, fallback), opts, lv);
    }

    private static void choose(Talk t, String text, List<Option> opts, int lv) {
        ServerPlayer sp = t.sp;
        DialogForm form = new DialogForm(t.name, text);
        for (Option o : opts) form.choice(o.text(), o.icon());
        form.show(sp, sel -> {
            Option o = sel >= 0 && sel < opts.size() ? opts.get(sel) : null;
            if (o == null || o.kind().equals("bye")) {
                t.end();
                return;
            }
            Quest q = o.q();
            switch (o.kind()) {
                case "more" -> {
                    t.page++;
                    step(t);
                }
                case "shop", "armor" -> Store.storeLoop(sp, lv, o.kind().equals("armor") ? "armor" : "weapons", t.done);
                case "smith", "enhance", "craft", "artifact" -> Smith.smithLoop(sp, o.kind(), t.done);
                case "runesmith" -> RuneSmith.runesmithLoop(sp, "grind", t.done);
                case "job" -> mmorpg.fabric.job.JobTrainer.open(sp, t.done); // 0.27.0 전직 · 직업 바꾸기 창
                case "jobup" -> mmorpg.fabric.job.JobUpgrades.open(sp, t.done); // 0.31.0 직업 스킬 강화 창
                // 받자마자 끝낼 수 있으면 (전리품을 이미 모아 온 반복 의뢰 등) 대화를 이어 가 바로 보고
                case "offer" -> offerFlow(sp, t.name, q, ok -> {
                    if (!ok || status(sp, q).equals("ready")) step(t);
                    else t.end();
                });
                case "progress" -> progressFlow(sp, t.name, q, () -> step(t));
                case "wait" -> waitFlow(sp, t.name, t.key, q, () -> step(t));
                // 완료 보고 · 전달: 이 NPC 에게 이어서 할 일 (다음 이야기 등) 이 있으면 대화를 이어 감
                default -> turnInFlow(sp, t.name, q, ok -> {
                    if (ok && !t.npc.isRemoved() && !sp.isRemoved() && hasMoreToDo(sp, t.key)) step(t);
                    else t.end();
                });
            }
        });
    }

    /** 퀘스트 제안. 수락하면 true */
    static void offerFlow(ServerPlayer sp, String name, Quest q, Consumer<Boolean> then) {
        if (q.type.equals("choose")) {
            chooseFlow(sp, name, q, then);
            return;
        }
        DialogForm.showPages(sp, name, q.offer, "부탁이 하나 있네.", text -> {
            if (text == null) {
                then.accept(false);
                return;
            }
            String body = text + "\n\n§e§l" + q.title + "§r\n§7목표: §f" + QuestText.objectiveText(q) + "\n§7보상: §f" + QuestText.rewardText(q, QuestRewards.rewardFor(sp, q));
            new DialogForm(name, body).choice("§a수락하기", DialogForm.OK).choice("§7다음에 할게요", DialogForm.BYE).show(sp, sel -> {
                if (sel != 0 || !status(sp, q).equals("available")) {
                    then.accept(false);
                    return;
                }
                Quests.accept(sp, q);
                then.accept(true);
            });
        });
    }

    static void progressFlow(ServerPlayer sp, String name, Quest q, Runnable then) {
        List<String> extra = new ArrayList<>();
        if (q.type.equals("trial")) {
            List<String> left = QuestText.trialBossesLeft(q, Quests.trialCounts(sp, q));
            if (!left.isEmpty() && left.size() <= 8) extra.add("§7남은 보스: §f" + String.join(", ", left));
        }
        if (q.type.equals("try")) {
            Quest.Entry e = Quests.stateOf(sp).get(q.id);
            List<String> tried = e != null && e.t != null ? e.t : List.of();
            List<String> left = new ArrayList<>();
            for (String t : MainQuests.PATH_ORDER) if (!tried.contains(t)) left.add(TYPES.obj(t).str("name"));
            if (!left.isEmpty()) extra.add("§7남은 무기: §f" + String.join(", ", left));
        }
        if (q.type.equals("boss")) extra.add("§7보스 제단을 눌러 불러냅니다");
        String body = QuestText.lastPage(q.progress, "아직 끝나지 않은 모양이군.") + "\n\n§e§l" + q.title + "§r\n§7목표: §f" + Quests.objectiveOf(sp, q)
                + (extra.isEmpty() ? "" : "\n" + String.join("\n", extra));
        new DialogForm(name, body).choice("§f알겠습니다", DialogForm.OK).choice("§c퀘스트 포기", DialogForm.BYE).show(sp, sel -> {
            if (sel == 1) confirmAbandon(sp, name, q, ok -> then.run());
            else then.run();
        });
    }

    /** 메인 · 왕실 · 전설: 레벨이 모자람 (필요한 레벨과 사냥터) */
    static void waitFlow(ServerPlayer sp, String name, String key, Quest q, Runnable then) {
        MainQuests.MainNpc def = MainQuests.mainNpcByKey(key);
        String line = (def != null ? def.waitLine() : "레벨 {lv}에 오르면 다시 오게.").replace("{lv}", String.valueOf(q.minLevel));
        double lv = Quests.questLevel(sp);
        String body = line + "\n\n§e§l" + q.title + "§r\n§7필요 레벨: §f" + q.minLevel + " §7(지금 " + Js.str(lv) + ")\n§7추천 사냥: §f" + QuestText.huntHint(lv);
        new DialogForm(name, body).choice("§f알겠습니다", DialogForm.OK).show(sp, sel -> then.run());
    }

    /**
     * 메인 퀘스트: 무기의 길 고르기. 고르면 true. 휴대폰 화면 높이 때문에 대사는 모두 앞 장으로 넘기고, 고르는 창은 물음 한 줄 + 무기 여섯 (창을 닫으면 다음에)
     */
    static void chooseFlow(ServerPlayer sp, String name, Quest q, Consumer<Boolean> then) {
        choosePages(sp, name, q, QuestText.toPages(q.offer), 0, then);
    }

    private static void choosePages(ServerPlayer sp, String name, Quest q, List<String> pages, int i, Consumer<Boolean> then) {
        if (i >= pages.size()) {
            choosePick(sp, name, q, 0, then);
            return;
        }
        new DialogForm(name, pages.get(i)).choice("§f다음", DialogForm.NEXT).show(sp, sel -> {
            if (sel != 0) then.accept(false);
            else choosePages(sp, name, q, pages, i + 1, then);
        });
    }

    private static void choosePick(ServerPlayer sp, String name, Quest q, int guard, Consumer<Boolean> then) {
        if (guard >= 20) {
            then.accept(false);
            return;
        }
        DialogForm form = new DialogForm(name, "§l어떤 무기의 길을 걷겠나?§r\n§7창을 닫으면 다음에 고릅니다");
        for (String t : MainQuests.PATH_ORDER) {
            MainQuests.MainPath p = MainQuests.MAIN_PATHS.get(t);
            form.choice(p.tint() + TYPES.obj(t).str("name") + " §7· " + p.desc(), Icon.of("textures/items/rpg/" + t + "/" + t + "_l0_common"));
        }
        form.show(sp, sel -> {
            String type = sel >= 0 && sel < MainQuests.PATH_ORDER.size() ? MainQuests.PATH_ORDER.get(sel) : null;
            if (type == null) {
                then.accept(false);
                return;
            }
            MainQuests.MainPath p = MainQuests.MAIN_PATHS.get(type);
            new DialogForm(name, p.tint() + "§l" + p.name() + "§r을 걷겠나?\n§7스승: §f" + MainQuests.mainNpcCall(p.mentor()) + "\n§7주 스탯: §f" + QuestText.statText(type)
                    + "\n§c한 번 고르면 바꿀 수 없다!").choice("§a" + p.name() + "로 가겠습니다", DialogForm.OK).choice("§7다시 고르기", DialogForm.NEXT).show(sp, sure -> {
                        if (sure < 0) {
                            then.accept(false);
                            return;
                        }
                        if (sure != 0) {
                            choosePick(sp, name, q, guard + 1, then);
                            return;
                        }
                        if (!status(sp, q).equals("available")) {
                            then.accept(false);
                            return;
                        }
                        Quest.Reward rw = q.reward.copy();
                        rw.weapon = type + "_l0_common";
                        int missing = QuestRewards.missingSlots(sp, q, rw, true);
                        if (missing > 0) {
                            fullBag(sp, name, missing, () -> then.accept(false));
                            return;
                        }
                        choosePath(sp, name, q, type, () -> then.accept(true));
                    });
        });
    }

    /** 길을 정함: 시험용 무기를 돌려받고 고른 무기 (0렙 일반) + 보상, 스승에게 가는 퀘스트를 바로 받음 */
    static void choosePath(ServerPlayer sp, String name, Quest q, String type, Runnable then) {
        Quests.setPath(sp, type);
        QuestRewards.takeBackTrials(sp);
        Quest.Reward reward = q.reward.copy();
        reward.weapon = type + "_l0_common";
        Quests.finish(sp, q, reward);
        QuestFx.pathFx(sp, type);
        Quest nx = Quests.getQuest(MainQuests.firstPathQuest(type));
        if (nx != null && status(sp, nx).equals("available")) Quests.accept(sp, nx);
        String doneText = q.doneBy != null ? q.doneBy.getOrDefault(type, "") : "";
        DialogForm.showPages(sp, name, doneText, "스승을 찾아가라.", text -> {
            if (text == null) {
                then.run();
                return;
            }
            String next = nx != null ? "\n§6새 퀘스트: §f" + nx.title + " §7- " + QuestText.objectiveText(nx) : "";
            new DialogForm(name, text + "\n\n§6§l퀘스트 완료!§r §f" + q.title + "\n§7받은 보상: §f" + QuestText.rewardText(q, reward) + QuestRewards.rewardLevelNote(sp, reward) + next)
                    .choice("§a확인", DialogForm.OK).show(sp, sel -> then.run());
        });
    }

    /** 포기 확인. 포기했으면 true */
    static void confirmAbandon(ServerPlayer sp, String name, Quest q, Consumer<Boolean> then) {
        new DialogForm(name, "§e" + q.title + "§r 퀘스트를 포기할까요?\n§7지금까지의 진행 상황이 사라집니다.").choice("§c포기하기", DialogForm.BYE).choice("§f취소", DialogForm.OK)
                .show(sp, sel -> {
                    if (sel != 0) {
                        then.accept(false);
                        return;
                    }
                    Quests.abandon(sp, q);
                    Notify.notify(sp, "§7퀘스트 포기: " + q.title);
                    SkillFx.playFor(sp, "note.bass", 0.6, 1);
                    then.accept(true);
                });
    }

    /** 완료 보고 · 전달. 끝냈으면 true */
    static void turnInFlow(ServerPlayer sp, String name, Quest q, Consumer<Boolean> then) {
        if (q.type.equals("talk")) {
            Quest.Entry e = Quests.stateOf(sp).get(q.id);
            if (e == null || !e.active()) {
                then.accept(false);
                return;
            }
        } else if (!status(sp, q).equals("ready")) {
            then.accept(false);
            return;
        }
        Quest.Reward reward = QuestRewards.rewardFor(sp, q); // 경험치 제한 · 왕실 갑옷 종류
        int missing = QuestRewards.missingSlots(sp, q, reward, false);
        if (missing > 0) {
            fullBag(sp, name, missing, () -> then.accept(false));
            return;
        }
        if (!Quests.finish(sp, q, reward)) {
            new DialogForm(name, "§c" + QuestText.targetLabel(q) + "이(가) 모자라군.\n§7필요: " + q.count + "개").choice("§f확인", DialogForm.OK).show(sp, sel -> then.accept(false));
            return;
        }
        // 보상은 이미 지급됨. 아래는 완료 대사
        DialogForm.showPages(sp, name, q.done, "고맙네! 약속한 보상일세.", text -> {
            if (text == null) {
                then.accept(true);
                return;
            }
            new DialogForm(name, text + "\n\n§6§l퀘스트 완료!§r §f" + q.title + "\n§7받은 보상: §f" + QuestText.rewardText(q, reward) + QuestRewards.rewardLevelNote(sp, reward))
                    .choice("§a확인", DialogForm.OK).show(sp, sel -> then.accept(true));
        });
    }

    /** 가방이 모자랄 때 대화 */
    static void fullBag(ServerPlayer sp, String name, int missing, Runnable then) {
        SkillFx.playFor(sp, "note.bass", 0.7, 1);
        new DialogForm(name, "§c가방이 꽉 찼군. 보상을 넣을 자리가 없어.\n§7빈 칸 §f" + missing + "칸§7을 만들고 다시 말을 걸어 주세요.").choice("§f확인", DialogForm.OK)
                .show(sp, sel -> then.run());
    }

    // ------------------------------------------------------------ 메인 퀘스트 NPC 인사말

    /** 메인 퀘스트 NPC 의 인사말 (NPC 설정 창에서 인사말을 넣으면 그것을 씀 — 아니면 ""). 지역 NPC: 무기 전 · 아직 오지 않음 · 진행 중 · 마침 */
    public static String mainGreeting(ServerPlayer sp, String key) {
        MainQuests.MainNpc def = MainQuests.mainNpcByKey(key);
        if (def == null) return "";
        String path = Quests.pathOf(sp);
        boolean ended = Quests.mainFinished(sp);
        MainQuests.Region region = def.region() != null ? MainQuests.REGIONS.get(def.region()) : null;
        if (region != null) {
            if (path.isEmpty()) return region.greet().none();
            List<Quest> chain = Quests.mainChain(path);
            boolean any = false;
            boolean allDone = true;
            for (Quest q : chain) {
                if (!q.giver.equals(key)) continue;
                any = true;
                if (!status(sp, q).equals("done")) allDone = false;
            }
            if (any && allDone) return region.greet().after();
            Quest arrive = null;
            for (Quest q : chain) {
                if (q.type.equals("talk") && q.target.equals(key)) {
                    arrive = q;
                    break;
                }
            }
            return arrive != null && status(sp, arrive).equals("done") ? region.greet().here() : region.greet().before();
        }
        if (def.greet() != null) {
            if (path.isEmpty()) return def.greet().none();
            if (!path.equals(def.path())) return def.greet().other();
            return ended ? def.greet().done() : def.greet().own();
        }
        Map<String, Quest.Entry> st = Quests.stateOf(sp);
        Quest.Entry start = st.get(MainQuests.MAIN_START);
        switch (key) {
            case "main_1" -> {
                if (ended) return "우리 영웅 왔구나! 오늘은 어떤 모험을 했니?";
                if (!path.isEmpty()) return MainQuests.MAIN_PATHS.get(path).name() + " 수련은 잘 되어 가니? 끼니는 거르지 말거라.";
                if (start != null && start.c > 0) return "브란에게는 다녀왔니? 무섭게 생겼어도 속은 따뜻한 사람이란다.";
                return "오, 우리 손자 왔구나. 또 언덕에서 먼 곳을 보고 있었니?";
            }
            case "main_2" -> {
                if (!path.isEmpty()) return MainQuests.MAIN_PATHS.get(path).name() + " 수련은 어떠냐? 기본을 잊지 마라!";
                if (start == null) return "여긴 훈련장이다, 꼬마. 모험가가 되고 싶으면 먼저 하랄 어르신께 허락을 받아 와라!";
                return "훈련장에 온 걸 환영한다! 땀 흘릴 준비는 됐겠지?";
            }
            case "main_9" -> {
                return "무기를 벼려 주지. 강화석이나 별철 광석은 챙겨 왔나?";
            }
            case "main_10" -> {
                // 반복 의뢰: 열린 것이 있으면 의뢰 이야기
                for (Quest q : Quests.sideQuests()) {
                    if (q.side.equals("repeat") && !status(sp, q).equals("locked")) return "어서 오세요! 몬스터 전리품을 가져오셨나요? 의뢰가 잔뜩 쌓여 있어요!";
                }
                return "어서 오세요! 따뜻한 수프가 있어요. 소문이 궁금하면 언제든지요!";
            }
            case "main_11" -> {
                if (Quests.royalFinished(sp)) {
                    // 초월 보스 전설 퀘스트 (왕실 퀘스트를 마친 뒤): 진행 중 → 받을 수 있음 → 가장 나중에 잡은 것 순서
                    String[] s = new String[SideQuests.TRANSCEND_LEGENDS.size()];
                    for (int i = 0; i < s.length; i++) {
                        Quest lq = Quests.getQuest(SideQuests.transcendLegendId(SideQuests.TRANSCEND_LEGENDS.get(i).boss()));
                        s[i] = lq != null ? status(sp, lq) : "locked";
                    }
                    for (int i = 0; i < s.length; i++) if (s[i].equals("active")) return SideQuests.TRANSCEND_LEGENDS.get(i).greet().active();
                    for (int i = 0; i < s.length; i++) if (s[i].equals("available")) return SideQuests.TRANSCEND_LEGENDS.get(i).greet().offer();
                    for (int i = s.length - 1; i >= 0; i--) {
                        if (s[i].equals("ready") || s[i].equals("done")) return SideQuests.TRANSCEND_LEGENDS.get(i).greet().done();
                    }
                    return "왕실의 수호자님! 황제 갑옷이 정말 잘 어울려요.";
                }
                if (path.isEmpty()) return "모험가님, 반가워요. 아직 무기를 고르지 않으셨군요. 훈련장의 브란 님께 먼저 가 보세요.";
                Quest nr = Quests.nextRoyal(sp);
                if (Quests.questLevel(sp) < (nr != null ? nr.minLevel : 0)) return "북쪽의 위협에 맞설 영웅을 찾고 있어요. 조금 더 강해지면 다시 와 주세요.";
                return "와 주셨군요, 모험가님. 왕국을 위해 힘을 빌려주세요.";
            }
            case "main_12" -> {
                return "룬은 돌고 도는 법이지. 겹친 룬이나 쓰지 않는 룬이 있으면 가져오게. 갈아서 조각으로 만들어 주지.";
            }
            default -> {
                return "";
            }
        }
    }
}
