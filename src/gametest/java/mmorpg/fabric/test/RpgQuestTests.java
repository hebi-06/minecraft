package mmorpg.fabric.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Props;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgData;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgPlayers;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.quest.Loot;
import mmorpg.fabric.quest.MainNpcs;
import mmorpg.fabric.quest.MainQuests;
import mmorpg.fabric.quest.Quest;
import mmorpg.fabric.quest.QuestRewards;
import mmorpg.fabric.quest.QuestSystem;
import mmorpg.fabric.quest.QuestTalk;
import mmorpg.fabric.quest.QuestText;
import mmorpg.fabric.quest.Quests;
import mmorpg.fabric.quest.SideQuests;
import mmorpg.fabric.ui.FormNet;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.Inv;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * 4단계 퀘스트 시험 (서버, 클라이언트 없이 창 흐름 — Forms.HEADLESS): 만든 퀘스트 (처치 · 가져오기 · 말 걸기 · 레벨) 받기 → 진행 → 보고 → 보상,
 * 대화창의 "다른 퀘스트 보기" · 퀘스트 목록 (/quest), 메인 이야기 처음 단계 (시작 → 무기 시험 (실제 평타 · onWeaponHit) → 무기 고르기),
 * 반복 의뢰가 열리는 조건 · 예전 진행 옮기기 · 경험치 제한 · 반복 보고, 저장 (Props) 다시 읽기, 보스 처치 · 전설의 시련, /mainnpc 가 NPC 를 세움 · 옮김.
 */
public final class RpgQuestTests {
    // ------------------------------------------------------------ 도움

    private static ServerPlayer player(GameTestHelper h) {
        return RpgGearTests.player(h);
    }

    private static void done(GameTestHelper h, ServerPlayer sp) {
        RpgGearTests.done(h, sp);
    }

    /** 시험 자리 rel 에 RPG NPC (마네킹) 를 세움 */
    private static Mannequin npc(GameTestHelper h, Vec3 rel, String name) {
        Mannequin m = NpcData.spawnAt(h.getLevel(), h.absoluteVec(rel), null);
        NpcData.setName(m, name);
        NpcData.refresh(m, 0);
        return m;
    }

    /** 메인 퀘스트 NPC (번호를 정하고 세운 기록도 — 같은 번호의 예전 NPC 로 보고 치우지 않게) */
    private static Mannequin mainNpc(GameTestHelper h, String key, Vec3 rel) {
        Mannequin m = NpcData.spawnAt(h.getLevel(), h.absoluteVec(rel), null);
        NpcData.setKey(m, key);
        NpcData.setName(m, MainQuests.mainNpcByKey(key).name());
        NpcData.refresh(m, 0);
        NpcData.setMainRecord(key, m, null, null);
        return m;
    }

    private static Quest quest(String giver, String title, String type, String target, int count) {
        Quest q = new Quest();
        q.giver = giver;
        q.title = title;
        q.type = type;
        q.target = target;
        q.count = count;
        return q;
    }

    private static FormNet.DialogPayload dialog(ServerPlayer sp) {
        return Forms.last(sp) instanceof FormNet.DialogPayload d ? d : null;
    }

    private static String dialogText(ServerPlayer sp) {
        FormNet.DialogPayload d = dialog(sp);
        return d == null ? "(대화창 없음: " + Forms.last(sp) + ")" : BedrockText.plain(d.text());
    }

    private static List<String> choices(ServerPlayer sp) {
        FormNet.DialogPayload d = dialog(sp);
        List<String> out = new ArrayList<>();
        if (d != null) for (FormNet.ChoiceData c : d.choices()) out.add(BedrockText.plain(c.text()));
        return out;
    }

    /** 지금 대화창에서 글자가 들어간 선택지를 누름 */
    private static void pick(GameTestHelper h, ServerPlayer sp, String part) {
        List<String> cs = choices(sp);
        for (int i = 0; i < cs.size(); i++) {
            if (cs.get(i).contains(part)) {
                Forms.answer(sp, i, null);
                return;
            }
        }
        h.fail("선택지 없음: " + part + " — " + cs + " / " + dialogText(sp));
    }

    /** 선택지가 "다음" 하나뿐인 대사 장을 넘김 */
    private static void nextPages(ServerPlayer sp) {
        for (int i = 0; i < 12; i++) {
            List<String> cs = choices(sp);
            if (!Forms.pending(sp) || cs.size() != 1 || !cs.get(0).contains("다음")) return;
            Forms.answer(sp, 0, null);
        }
    }

    /** 연속 피해 막기 없이 피해 */
    private static void hurt(GameTestHelper h, LivingEntity e, DamageSource src, float amount) {
        e.setInvulnerableTime(0);
        e.damageCooldownTime = 0;
        e.hurtServer(h.getLevel(), src, amount);
    }

    private static Zombie zombie(GameTestHelper h, double x, double z, float hp) {
        Zombie zb = h.spawnWithNoFreeWill(EntityTypes.ZOMBIE, (float) x, 2F, (float) z);
        zb.setHealth(hp);
        return zb;
    }

    private static int trialCount(ServerPlayer sp) {
        int n = 0;
        for (int i = 0; i < Inv.SIZE; i++) if (QuestRewards.isTrial(Inv.get(sp, i))) n++;
        return n;
    }

    private static String status(ServerPlayer sp, String id) {
        return Quests.statusOf(sp, Quests.getQuest(id));
    }

    // ------------------------------------------------------------ 만든 퀘스트

    /** 처치 퀘스트: 대화창으로 받기 → 플레이어가 잡음 · 때린 뒤 다른 피해로 죽음 (10초 안) → 보고 → 에메랄드 · 경험치 (레벨 보상 → 바닐라 경험치, 9단계) · 스탯 포인트 */
    @GameTest(maxTicks = 40)
    public void customKillQuest(GameTestHelper h) {
        ServerPlayer sp = player(h);
        Mannequin giver = npc(h, new Vec3(3.5, 2, 3.5), "사냥꾼");
        Forms.HEADLESS.add(sp.getUUID());
        try {
            String key = NpcData.key(giver);
            Quest q = quest(key, "좀비 사냥", "kill", "minecraft:zombie", 2);
            q.targetName = "좀비";
            q.reward.emerald = 5;
            q.reward.xp = 1;
            q.reward.points = 2;
            Quest saved = Quests.saveQuest(q);
            h.assertTrue(saved.id.matches("q\\d+") && Quests.getQuest(saved.id) != null, "saved " + saved.id);
            h.assertTrue(Quests.badgeCount(key) == 1 && Quests.hasQuestFor(key), "badge " + Quests.badgeCount(key));
            h.assertTrue("available".equals(Quests.questMark(sp, key)), "mark " + Quests.questMark(sp, key));

            QuestTalk.talkLoop(sp, giver, null);
            h.assertTrue(dialogText(sp).contains("안녕하신가"), "greeting " + dialogText(sp));
            pick(h, sp, "좀비 사냥");
            h.assertTrue(dialogText(sp).contains("부탁이 하나 있네") && dialogText(sp).contains("좀비 2마리 처치") && dialogText(sp).contains("에메랄드 5"),
                    "offer " + dialogText(sp));
            pick(h, sp, "수락하기");
            h.assertFalse(Forms.pending(sp), "flow ended");
            h.assertTrue(status(sp, saved.id).equals("active"), "active");
            h.assertTrue(saved.id.equals(Quests.navOf(sp).q()), "nav pinned");

            // 플레이어가 잡음
            Zombie z1 = zombie(h, 1.5, 4.5, 1F);
            hurt(h, z1, sp.damageSources().playerAttack(sp), 50F);
            h.assertFalse(z1.isAlive(), "z1 dead");
            h.assertTrue(Quests.stateOf(sp).get(saved.id).n == 1, "one kill " + Quests.stateOf(sp).get(saved.id).n);
            // 때린 뒤 다른 피해로 죽음 → 10초 안에 마지막으로 때린 사람
            Zombie z2 = zombie(h, 2.5, 4.5, 10F);
            hurt(h, z2, sp.damageSources().playerAttack(sp), 1F);
            h.assertTrue(z2.isAlive(), "z2 alive after hit");
            hurt(h, z2, h.getLevel().damageSources().generic(), 100F);
            h.assertFalse(z2.isAlive(), "z2 dead");
            h.assertTrue(status(sp, saved.id).equals("ready"), "ready " + Quests.stateOf(sp).get(saved.id).n);
            h.assertTrue("ready".equals(Quests.questMark(sp, key)), "mark ready");
            h.assertTrue(Quests.navGoal(sp) != null && Quests.navGoal(sp).why().equals("turnin") && key.equals(Quests.navGoal(sp).npc()), "nav turnin");

            int money = Inv.money(sp);
            int lv = sp.experienceLevel;
            int xp0 = sp.totalExperience;
            int points = RpgData.of(sp).point();
            QuestTalk.talkLoop(sp, giver, null);
            pick(h, sp, "완료 보고");
            // 9단계 (레벨업 3배): 레벨 보상 1 = 지금 레벨의 바닐라 경험치 (완료 창에는 "경험치 N")
            int want = mmorpg.core.Leveling.pointsForLevels(lv, 1);
            h.assertTrue(dialogText(sp).contains("고맙네") && dialogText(sp).contains("퀘스트 완료") && dialogText(sp).contains("경험치 " + want),
                    "done " + dialogText(sp));
            pick(h, sp, "확인");
            h.assertFalse(Forms.pending(sp), "closed");
            h.assertTrue(Inv.money(sp) == money + 5, "emerald " + Inv.money(sp));
            h.assertTrue(sp.totalExperience - xp0 == want, "xp +" + want + " (got " + (sp.totalExperience - xp0) + ")");
            h.assertTrue(want < mmorpg.core.Leveling.need(lv), "1 quest level < 1 real level (×3)");
            h.assertTrue(RpgData.of(sp).point() >= points + 2, "points " + RpgData.of(sp).point() + " vs " + points);
            h.assertTrue(status(sp, saved.id).equals("done"), "done");
            h.assertTrue(Quests.questMark(sp, key) == null, "no mark");
            Quests.deleteQuest(saved.id);
        } finally {
            Forms.HEADLESS.remove(sp.getUUID());
            giver.discard();
        }
        done(h, sp);
    }

    /** 가져오기 (반복 가능): 모으면 달성 알림 → 보고하면 아이템을 가져가고 에메랄드 + 아이템 보상, 다시 받을 수 있음 */
    @GameTest(maxTicks = 40)
    public void customCollectQuest(GameTestHelper h) {
        ServerPlayer sp = player(h);
        Mannequin giver = npc(h, new Vec3(3.5, 2, 3.5), "대장장이 견습생");
        Forms.HEADLESS.add(sp.getUUID());
        try {
            String key = NpcData.key(giver);
            Quest q = quest(key, "철 모으기", "collect", "minecraft:iron_ingot", 3);
            q.targetName = "철 주괴";
            q.repeat = true;
            q.reward.emerald = 4;
            q.reward.item = new Quest.RewardItem("minecraft:bread", 2, "빵", null);
            Quest saved = Quests.saveQuest(q);
            h.assertTrue(saved.repeat && saved.reward.item != null && saved.reward.item.amount == 2, "saved collect");
            QuestTalk.talkLoop(sp, giver, null);
            pick(h, sp, "철 모으기");
            h.assertTrue(dialogText(sp).contains("빵 ×2"), "reward text " + dialogText(sp));
            pick(h, sp, "수락하기");
            h.assertTrue(status(sp, saved.id).equals("active"), "active");
            Inv.give(sp, new ItemStack(Items.IRON_INGOT, 2));
            Quests.questTick(sp);
            h.assertTrue(status(sp, saved.id).equals("active") && Quests.stateOf(sp).get(saved.id).r == null, "2 of 3");
            h.assertTrue(Quests.objectiveOf(sp, saved).contains("(2/3)"), Quests.objectiveOf(sp, saved));
            Inv.give(sp, new ItemStack(Items.IRON_INGOT, 1));
            Quests.questTick(sp);
            h.assertTrue(status(sp, saved.id).equals("ready") && Integer.valueOf(1).equals(Quests.stateOf(sp).get(saved.id).r), "ready noted");
            int money = Inv.money(sp);
            QuestTalk.talkLoop(sp, giver, null);
            pick(h, sp, "완료 보고");
            pick(h, sp, "확인");
            // 반복 가능: 보고하면 바로 다시 받을 수 있어서 대화가 이어짐
            if (Forms.pending(sp)) pick(h, sp, "대화 끝내기");
            h.assertFalse(Forms.pending(sp), "closed");
            h.assertTrue(RpgGearTests.countIn(sp, Items.IRON_INGOT) == 0, "iron taken");
            h.assertTrue(RpgGearTests.countIn(sp, Items.BREAD) == 2, "bread given");
            h.assertTrue(Inv.money(sp) == money + 4, "emerald");
            h.assertTrue(status(sp, saved.id).equals("available") && Quests.stateOf(sp).get(saved.id).c == 1, "repeatable " + status(sp, saved.id));
            h.assertTrue("repeat".equals(Quests.questMark(sp, key)), "repeat mark " + Quests.questMark(sp, key));
            Quests.deleteQuest(saved.id);
        } finally {
            Forms.HEADLESS.remove(sp.getUUID());
            giver.discard();
        }
        done(h, sp);
    }

    /** 말 걸기 (다른 NPC 에게 전달) · 레벨 달성 */
    @GameTest(maxTicks = 40)
    public void customTalkAndLevelQuests(GameTestHelper h) {
        ServerPlayer sp = player(h);
        Mannequin a = npc(h, new Vec3(3.5, 2, 3.5), "촌장");
        Mannequin b = npc(h, new Vec3(5.5, 2, 3.5), "약초꾼");
        Forms.HEADLESS.add(sp.getUUID());
        try {
            String ka = NpcData.key(a);
            String kb = NpcData.key(b);
            Quest talk = quest(ka, "약초꾼에게", "talk", kb, 1);
            talk.reward.emerald = 3;
            Quest level = quest(ka, "성장", "level", "", 3);
            level.reward.points = 1;
            Quest t = Quests.saveQuest(talk);
            Quest l = Quests.saveQuest(level);
            h.assertTrue(Quests.objectiveText(t).equals("약초꾼에게 말 걸기"), Quests.objectiveText(t));
            QuestTalk.talkLoop(sp, a, null);
            pick(h, sp, "약초꾼에게");
            pick(h, sp, "수락하기");
            QuestTalk.talkLoop(sp, a, null);
            pick(h, sp, "성장");
            pick(h, sp, "수락하기");
            h.assertTrue(status(sp, t.id).equals("active") && status(sp, l.id).equals("active"), "both active");
            h.assertTrue("ready".equals(Quests.questMark(sp, kb)), "target mark");
            h.assertTrue(Quests.objectiveOf(sp, l).contains("레벨 3 달성 (현재 0)"), Quests.objectiveOf(sp, l));
            sp.setExperienceLevels(3);
            RpgPlayers.checkLevelUp(sp);
            Quests.questTick(sp);
            h.assertTrue(status(sp, l.id).equals("ready"), "level ready");
            int money = Inv.money(sp);
            QuestTalk.talkLoop(sp, b, null);
            pick(h, sp, "전달");
            nextPages(sp);
            pick(h, sp, "확인");
            h.assertFalse(Forms.pending(sp), "closed after deliver");
            h.assertTrue(status(sp, t.id).equals("done") && Inv.money(sp) == money + 3, "delivered");
            QuestTalk.talkLoop(sp, a, null);
            pick(h, sp, "완료 보고");
            pick(h, sp, "확인");
            h.assertTrue(status(sp, l.id).equals("done"), "level done");
            Quests.deleteQuest(t.id);
            Quests.deleteQuest(l.id);
        } finally {
            Forms.HEADLESS.remove(sp.getUUID());
            a.discard();
            b.discard();
        }
        done(h, sp);
    }

    /** 대화창: 퀘스트가 한 장 (3개) 을 넘으면 "다른 퀘스트 보기", 퀘스트 목록 (/quest): 진행 · 길 안내 끄기 */
    @GameTest(maxTicks = 40)
    public void talkPagesAndQuestLog(GameTestHelper h) {
        ServerPlayer sp = player(h);
        Mannequin giver = npc(h, new Vec3(3.5, 2, 3.5), "게시판");
        Forms.HEADLESS.add(sp.getUUID());
        List<String> ids = new ArrayList<>();
        try {
            String key = NpcData.key(giver);
            for (int i = 1; i <= 4; i++) {
                Quest q = quest(key, "부탁 " + i, "kill", "minecraft:skeleton", 5);
                q.targetName = "스켈레톤";
                ids.add(Quests.saveQuest(q).id);
            }
            QuestTalk.talkLoop(sp, giver, null);
            List<String> cs = choices(sp);
            h.assertTrue(cs.size() == 5 && cs.get(3).contains("다른 퀘스트 보기") && cs.get(3).contains("1/2") && cs.get(4).contains("대화 끝내기"), "page 1 " + cs);
            pick(h, sp, "다른 퀘스트 보기");
            cs = choices(sp);
            h.assertTrue(cs.size() == 3 && cs.get(0).contains("부탁 4") && cs.get(1).contains("2/2"), "page 2 " + cs);
            pick(h, sp, "부탁 4");
            pick(h, sp, "수락하기");
            h.assertFalse(Forms.pending(sp), "accepted");
            // /quest (명령어) → 퀘스트 목록
            h.getLevel().getServer().getCommands().performPrefixedCommand(sp.createCommandSourceStack(), "quest");
            FormNet.DialogPayload d = dialog(sp);
            h.assertTrue(d != null && BedrockText.plain(d.name()).equals("퀘스트 목록"), "quest log " + Forms.last(sp));
            h.assertTrue(dialogText(sp).contains("진행 중인 퀘스트") && dialogText(sp).contains("길 안내: 부탁 4"), "log text " + dialogText(sp));
            h.assertTrue(choices(sp).get(0).contains("부탁 4") && choices(sp).get(0).contains("(0/5)"), "log entry " + choices(sp));
            pick(h, sp, "길 안내 끄기");
            h.assertTrue(Quests.navOf(sp).off(), "nav off");
            h.assertTrue(choices(sp).stream().anyMatch(c -> c.contains("길 안내 켜기")), "toggle shown " + choices(sp));
            pick(h, sp, "길 안내 켜기");
            h.assertFalse(Quests.navOf(sp).off(), "nav on");
            pick(h, sp, "부탁 4"); // 자세히
            h.assertTrue(dialogText(sp).contains("의뢰: 게시판") && dialogText(sp).contains("스켈레톤 5마리 처치 (0/5)"), "detail " + dialogText(sp));
            pick(h, sp, "퀘스트 포기");
            pick(h, sp, "포기하기");
            h.assertTrue(status(sp, ids.get(3)).equals("available") && !Quests.stateOf(sp).containsKey(ids.get(3)), "abandoned");
            pick(h, sp, "닫기");
            h.assertFalse(Forms.pending(sp), "log closed");
        } finally {
            Forms.HEADLESS.remove(sp.getUUID());
            for (String id : ids) Quests.deleteQuest(id);
            giver.discard();
        }
        done(h, sp);
    }

    // ------------------------------------------------------------ 메인 이야기

    /** 메인 이야기 처음: 할아버지 → 브란 (전달) → 무기 시험 (시험용 무기 6, 실제 평타 + onWeaponHit) → 무기 고르기 (칼의 길) → 스승에게 가는 퀘스트 */
    @GameTest(maxTicks = 60)
    public void mainStoryStart(GameTestHelper h) {
        ServerPlayer sp = player(h);
        Mannequin harald = mainNpc(h, "main_1", new Vec3(3.5, 2, 3.5));
        Mannequin bran = mainNpc(h, "main_2", new Vec3(5.5, 2, 3.5));
        Husk target = TestDummies.make(h.spawnWithNoFreeWill(EntityTypes.HUSK, 1.5F, 2F, 3.5F));
        Forms.HEADLESS.add(sp.getUUID());
        try {
            h.assertTrue(Quests.nextMain(sp) != null && Quests.nextMain(sp).id.equals(MainQuests.MAIN_START), "first main quest");
            h.assertTrue("available".equals(Quests.questMark(sp, "main_1")), "harald mark");
            // 1. 할아버지 하랄: 모험의 꿈 받기
            QuestTalk.talkLoop(sp, harald, null);
            h.assertTrue(dialogText(sp).contains("우리 손자 왔구나"), "harald greeting " + dialogText(sp));
            pick(h, sp, "모험의 꿈");
            nextPages(sp);
            pick(h, sp, "수락하기");
            h.assertFalse(Forms.pending(sp), "accepted start");
            h.assertTrue(status(sp, MainQuests.MAIN_START).equals("active"), "start active");
            Quests.NavGoal g = Quests.navGoal(sp);
            h.assertTrue(g != null && g.why().equals("talk") && "main_2".equals(g.npc()) && g.main(), "nav to bran " + g);
            // 2. 브란에게 전달 → 이어서 무기 시험 받기 (시험용 무기 여섯)
            QuestTalk.talkLoop(sp, bran, null);
            h.assertTrue(dialogText(sp).contains("훈련장에 온 걸 환영한다"), "bran greeting " + dialogText(sp));
            pick(h, sp, "전달");
            nextPages(sp);
            pick(h, sp, "확인");
            h.assertTrue(status(sp, MainQuests.MAIN_START).equals("done") && Inv.money(sp) == 2, "start done, emerald " + Inv.money(sp));
            pick(h, sp, "여섯 자루의 무기");
            nextPages(sp);
            pick(h, sp, "수락하기");
            h.assertFalse(Forms.pending(sp), "accepted trial");
            h.assertTrue(status(sp, MainQuests.MAIN_TRIAL).equals("active") && trialCount(sp) == 6, "trial weapons " + trialCount(sp));
            // 3. 시험용 칼로 실제 평타 (RpgCombat 무기 맞힘 훅 → onWeaponHit)
            int swordSlot = -1;
            for (int i = 0; i < 9; i++) {
                Weapon w = RpgCombat.weaponOf(Inv.get(sp, i));
                if (QuestRewards.isTrial(Inv.get(sp, i)) && w != null && w.type().equals("sword")) swordSlot = i;
            }
            h.assertTrue(swordSlot >= 0, "trial sword in hotbar");
            sp.getInventory().setSelectedSlot(swordSlot);
            Weapon sword = RpgCombat.weaponOf(sp.getMainHandItem());
            for (Weapons.Req r : sword.reqs()) RpgPlayers.setStat(sp, r.stat(), (int) Math.ceil(r.value()) + 1);
            RpgCombat.forceCrit = false;
            sp.attack(target);
            RpgCombat.forceCrit = null;
            Quest.Entry e = Quests.stateOf(sp).get(MainQuests.MAIN_TRIAL);
            h.assertTrue(e.t != null && e.t.contains("sword") && e.n == 1, "sword tried " + (e.t == null ? null : e.t) + " dealt " + TestDummies.dealt(target));
            // 같은 종류는 한 번만, 나머지 다섯 종류
            Quests.onWeaponHit(sp, target, sword);
            h.assertTrue(e.t.size() == 1, "same type once");
            for (String type : List.of("dagger", "axe", "shield", "bow", "wand")) Quests.onWeaponHit(sp, target, Weapons.weaponFrom(type, 0, "common"));
            h.assertTrue(status(sp, MainQuests.MAIN_TRIAL).equals("ready"), "trial ready " + e.t);
            // 4. 보고 → 무기 고르기 (칼의 길)
            QuestTalk.talkLoop(sp, bran, null);
            pick(h, sp, "완료 보고");
            nextPages(sp);
            pick(h, sp, "확인");
            h.assertTrue(status(sp, MainQuests.MAIN_TRIAL).equals("done"), "trial done");
            pick(h, sp, "나의 무기");
            nextPages(sp);
            h.assertTrue(choices(sp).size() == 6 && dialogText(sp).contains("어떤 무기의 길을 걷겠나"), "path choices " + choices(sp));
            h.assertTrue(choices(sp).get(0).contains("칼") && choices(sp).get(5).contains("완드"), "path order " + choices(sp));
            Forms.answer(sp, 0, null);
            h.assertTrue(dialogText(sp).contains("칼의 길") && dialogText(sp).contains("검술 스승 시그문드"), "confirm " + dialogText(sp));
            pick(h, sp, "가겠습니다");
            nextPages(sp);
            h.assertTrue(dialogText(sp).contains("퀘스트 완료") && dialogText(sp).contains("시그문드를 찾아서"), "chosen " + dialogText(sp));
            pick(h, sp, "확인");
            h.assertFalse(Forms.pending(sp), "story flow ended");
            h.assertTrue(Quests.pathOf(sp).equals("sword") && "sword".equals(Props.get(sp, "rpg:main_path")), "path sword");
            h.assertTrue(trialCount(sp) == 0, "trial weapons taken back " + trialCount(sp));
            h.assertTrue(RpgGearTests.countIn(sp, RpgItems.byId("rpg:sword_l0_common")) >= 1, "got the sword");
            h.assertTrue(Inv.money(sp) == 7, "emerald 2 + 3 + 2 = " + Inv.money(sp));
            String first = MainQuests.firstPathQuest("sword");
            h.assertTrue(status(sp, first).equals("active") && first.equals(Quests.navOf(sp).q()), "mentor quest " + status(sp, first));
            g = Quests.navGoal(sp);
            h.assertTrue(g != null && g.why().equals("talk") && "main_3".equals(g.npc()), "nav to mentor " + g);
            h.assertTrue(Quests.lockReason(sp, Quests.getQuest(MainQuests.firstPathQuest("axe"))).equals("path"), "other path locked");
        } finally {
            Forms.HEADLESS.remove(sp.getUUID());
            harald.discard();
            bran.discard();
        }
        done(h, sp);
    }

    /** 반복 의뢰: 메인 이야기의 그 몬스터 처치 단계를 끝내면 열림 (예전 18단계 진행도), 경험치 제한, 받자마자 보고 · 다시 받기 */
    @GameTest(maxTicks = 40)
    public void repeatQuestUnlocks(GameTestHelper h) {
        ServerPlayer sp = player(h);
        Mannequin ingrid = mainNpc(h, SideQuests.REPEAT_GIVER, new Vec3(3.5, 2, 3.5));
        Forms.HEADLESS.add(sp.getUUID());
        try {
            Quest rep = Quests.getQuest(SideQuests.repeatId("scarecrow"));
            h.assertTrue(rep != null && rep.afterStep == MainQuests.mainKillStep("scarecrow") && rep.repeat, "repeat def");
            h.assertTrue(Quests.statusOf(sp, rep).equals("locked") && Quests.lockReason(sp, rep).equals("requires"), "locked before path");
            // 칼의 길 · 허수아비 처치 단계 (s03) 를 끝낸 저장 데이터
            Props.set(sp, "rpg:main_path", "sword");
            Props.set(sp, "rpg:qs", "{\"" + MainQuests.mainId("sword", rep.afterStep) + "\":{\"s\":\"d\",\"n\":0,\"c\":1}}");
            Quests.forgetQuestPlayer(sp.getUUID());
            h.assertTrue(Quests.pathOf(sp).equals("sword"), "path read");
            h.assertTrue(Quests.statusOf(sp, rep).equals("available"), "unlocked " + Quests.lockReason(sp, rep));
            h.assertTrue(status(sp, SideQuests.repeatId("gulon")).equals("locked"), "gulon still locked");
            h.assertTrue("repeat".equals(Quests.questMark(sp, SideQuests.REPEAT_GIVER)), "repeat mark " + Quests.questMark(sp, SideQuests.REPEAT_GIVER));
            // 예전 (18단계) 진행: 허수아비 처치 (예전 2단계) 를 끝냈으면 열림 + 지역 퀘스트 단계 (31단계 → 9단계 46단계) 로 옮김 (한 번)
            Props.set(sp, "rpg:qs", "{\"mq_sword_02\":{\"s\":\"d\",\"n\":0,\"c\":1}}");
            Quests.forgetQuestPlayer(sp.getUUID());
            h.assertTrue(Quests.statusOf(sp, rep).equals("available"), "old progress unlocks");
            h.assertTrue(Quests.stateOf(sp).containsKey(MainQuests.MIGRATED_KEY) && Quests.stateOf(sp).containsKey(MainQuests.MIGRATED_S_KEY)
                    && Quests.stateOf(sp).get(MainQuests.mainId("sword", 3)).c == 1 && !Quests.stateOf(sp).containsKey(MainQuests.mainId("sword", 4)), "migrated");
            h.assertTrue(Props.get(sp, "rpg:qs").contains(MainQuests.MIGRATED_KEY), "migration saved");
            // 경험치 제한: 최고 레벨이 xpUntil (8) 이상이면 경험치 없음
            sp.setExperienceLevels(10);
            RpgPlayers.checkLevelUp(sp);
            Quest.Reward rw = QuestRewards.rewardFor(sp, rep);
            h.assertTrue(rw.xpPoints == 0 && rw.capped.equals("scarecrow") && Quests.rewardText(rep, rw).contains("경험치 없음"), Quests.rewardText(rep, rw));
            // 전리품을 이미 모아 왔으면 받자마자 보고
            Inv.give(sp, new ItemStack(GearItems.byId("rpg:loot_scarecrow"), rep.count));
            int money = Inv.money(sp);
            QuestTalk.talkLoop(sp, ingrid, null);
            h.assertTrue(dialogText(sp).contains("몬스터 전리품을 가져오셨나요"), "ingrid greeting " + dialogText(sp));
            pick(h, sp, "짚단 거두기");
            nextPages(sp);
            pick(h, sp, "수락하기");
            pick(h, sp, "완료 보고");
            nextPages(sp);
            pick(h, sp, "확인");
            h.assertTrue(choices(sp).stream().anyMatch(c -> c.contains("짚단 거두기")), "offered again " + choices(sp));
            pick(h, sp, "대화 끝내기");
            h.assertFalse(Forms.pending(sp), "closed");
            h.assertTrue(RpgGearTests.countIn(sp, GearItems.byId("rpg:loot_scarecrow")) == 0, "loot taken");
            h.assertTrue(Inv.money(sp) == money + rep.reward.emerald, "emerald " + Inv.money(sp));
            h.assertTrue(Quests.statusOf(sp, rep).equals("available") && Quests.stateOf(sp).get(rep.id).c == 1, "repeatable");
        } finally {
            Forms.HEADLESS.remove(sp.getUUID());
            ingrid.discard();
        }
        done(h, sp);
    }

    /**
     * 큰 보상 · 연출: 전설 퀘스트 (듀란드의 방패 — 전설 무기 · 렙제 알림), 왕실 마지막 (길에 맞는 초월 갑옷 한 벌 · 고급 뽑기권), 초월 보스 전설 (칭호 · 보스 재료),
     * 메인 이야기의 끝 (영웅의 귀환 — 할아버지에게 전달)
     */
    @GameTest(maxTicks = 40)
    public void finaleRewardsAndFx(GameTestHelper h) {
        ServerPlayer sp = player(h);
        Mannequin hakon = mainNpc(h, "main_6", new Vec3(3.5, 2, 3.5));
        Mannequin sigrid = mainNpc(h, SideQuests.ROYAL_GIVER, new Vec3(5.5, 2, 3.5));
        Mannequin harald = mainNpc(h, "main_1", new Vec3(5.5, 2, 5.5));
        Forms.HEADLESS.add(sp.getUUID());
        try {
            String legend = MainQuests.legendId("shield", "hero");
            String royal = SideQuests.royalId(SideQuests.ROYAL_STEPS);
            String surtr = SideQuests.transcendLegendId("surtr");
            String outro = MainQuests.mainId("shield", MainQuests.PATH_STEPS);
            Props.set(sp, "rpg:main_path", "shield");
            Props.set(sp, "rpg:qs", "{\"" + legend + "\":{\"s\":\"a\",\"n\":43,\"c\":0,\"o\":[40,3]},\"" + royal + "\":{\"s\":\"a\",\"n\":1,\"c\":0},\"" + surtr
                    + "\":{\"s\":\"a\",\"n\":1,\"c\":0},\"" + outro + "\":{\"s\":\"a\",\"n\":0,\"c\":0}}");
            Quests.forgetQuestPlayer(sp.getUUID());
            h.assertTrue(status(sp, legend).equals("ready") && status(sp, royal).equals("ready") && status(sp, surtr).equals("ready"), "ready");
            // 1. 듀란드의 방패 (수호 기사 하콘)
            QuestTalk.talkLoop(sp, hakon, null);
            h.assertTrue(dialogText(sp).contains("뒤에 선 이들을 위한 것"), "mentor greeting " + dialogText(sp));
            pick(h, sp, "듀란드의 방패");
            nextPages(sp);
            h.assertTrue(dialogText(sp).contains("레벨 30부터 쓸 수 있음"), "level note " + dialogText(sp));
            pick(h, sp, "확인");
            h.assertFalse(Forms.pending(sp), "legend done");
            h.assertTrue(RpgGearTests.countIn(sp, RpgItems.byId("rpg:shield_l30_hero")) == 1 && status(sp, legend).equals("done"), "legend weapon");
            // 2. 왕실 마지막 → 이어서 초월 보스 전설 (왕녀 시그리드)
            int money = Inv.money(sp);
            QuestTalk.talkLoop(sp, sigrid, null);
            pick(h, sp, "서리 거인의 왕관");
            nextPages(sp);
            h.assertTrue(dialogText(sp).contains("황제 갑옷 세트") && dialogText(sp).contains("체력"), "royal reward " + dialogText(sp));
            pick(h, sp, "확인");
            pick(h, sp, "무스펠의 불꽃 거인");
            nextPages(sp);
            h.assertTrue(dialogText(sp).contains("레바테인") || dialogText(sp).contains("퀘스트 완료"), "surtr done " + dialogText(sp));
            pick(h, sp, "확인");
            h.assertFalse(Forms.pending(sp), "royal flow ended " + choices(sp));
            for (String piece : mmorpg.core.Armor.PIECE_ORDER) {
                ItemStack a = mmorpg.fabric.shop.ArmorShop.armorItem(mmorpg.core.Armor.armorFrom("vit", 40, "transcend", piece));
                h.assertTrue(RpgGearTests.countIn(sp, a.getItem()) == 1, "emperor vit " + piece);
            }
            h.assertTrue(Inv.money(sp) == money + 64 + 128, "emerald " + Inv.money(sp));
            h.assertTrue(RpgGearTests.countIn(sp, GearItems.byId("rpg:laevateinn_shard")) == 2 && RpgGearTests.countIn(sp, GearItems.byId("rpg:muspel_heart")) == 1,
                    "surtr materials");
            h.assertTrue(status(sp, royal).equals("done") && status(sp, surtr).equals("done"), "royal · surtr done");
            // 3. 영웅의 귀환 (할아버지에게 전달) → 메인 이야기 끝
            QuestTalk.talkLoop(sp, harald, null);
            h.assertTrue(dialogText(sp).contains("방패의 길 수련은 잘 되어 가니"), "harald greeting " + dialogText(sp));
            pick(h, sp, "전달");
            nextPages(sp);
            pick(h, sp, "확인");
            if (Forms.pending(sp)) pick(h, sp, "대화 끝내기");
            h.assertTrue(status(sp, outro).equals("done") && Quests.mainFinished(sp), "main finished");
            h.assertTrue(RpgGearTests.countIn(sp, mmorpg.fabric.rune.RuneItems.ADV_TICKET) == 4, "advanced tickets " + RpgGearTests.countIn(sp, mmorpg.fabric.rune.RuneItems.ADV_TICKET));
            h.assertTrue(QuestText.lastPage(Quests.getQuest(outro).done, "").contains("네 모험은 이제부터 시작이란다"), "outro text");
        } finally {
            Forms.HEADLESS.remove(sp.getUUID());
            hakon.discard();
            sigrid.discard();
            harald.discard();
        }
        done(h, sp);
    }

    // ------------------------------------------------------------ 저장

    /** 퀘스트 정의 (세계 속성 — 애드온과 같은 JSON) · 플레이어 진행 · 길 안내 (플레이어 속성) 를 다시 읽어도 같음, 들어 있는 퀘스트는 저장 · 삭제로 안 바뀜 */
    @GameTest(maxTicks = 40)
    public void questSaveReload(GameTestHelper h) {
        ServerPlayer sp = player(h);
        MinecraftServer server = h.getLevel().getServer();
        Mannequin giver = npc(h, new Vec3(3.5, 2, 3.5), "기록관");
        Forms.HEADLESS.add(sp.getUUID());
        String id = null;
        try {
            String key = NpcData.key(giver);
            Quest q = quest(key, "저장 시험", "kill", "rpg:gulon", 3);
            q.targetName = "굴론";
            q.repeat = true;
            q.minLevel = 0;
            q.reward.weapon = "sword_l10_rare";
            q.reward.item = new Quest.RewardItem("minecraft:diamond", 2, "다이아몬드", "§b빛나는 돌");
            Quest saved = Quests.saveQuest(q);
            id = saved.id;
            String raw = Props.world(server, "rpg:quest:" + id);
            JsonObject j = JsonParser.parseString(raw).getAsJsonObject();
            h.assertTrue(j.get("id").getAsString().equals(id) && j.get("count").getAsInt() == 3 && j.get("repeat").getAsBoolean()
                    && j.getAsJsonObject("reward").getAsJsonObject("item").get("nameTag").getAsString().equals("§b빛나는 돌")
                    && j.getAsJsonObject("reward").get("weapon").getAsString().equals("sword_l10_rare"), "stored json " + raw);
            Quests.reload();
            Quest back = Quests.getQuest(id);
            h.assertTrue(back != null && back != saved && back.toJson().equals(saved.toJson()), "reloaded " + (back == null ? null : back.toJson()));
            // 편집: 복사본을 고쳐 저장 (수는 범위 안으로)
            Quest edit = back.copy();
            edit.title = "고친 이름";
            edit.count = 99999;
            Quest e2 = Quests.saveQuest(edit);
            h.assertTrue(e2.id.equals(id) && e2.count == 10000 && Quests.getQuest(id).title.equals("고친 이름"), "edited " + e2.toJson());
            // 들어 있는 퀘스트는 저장 · 삭제로 바뀌지 않음
            Quest mq = Quests.getQuest(MainQuests.MAIN_START).copy();
            mq.title = "바꾸면 안 됨";
            Quests.saveQuest(mq);
            Quests.deleteQuest(MainQuests.MAIN_START);
            h.assertTrue(Quests.getQuest(MainQuests.MAIN_START) != null && Quests.getQuest(MainQuests.MAIN_START).title.equals("모험의 꿈"), "builtin unchanged");
            // 플레이어 진행 · 길 안내 저장 → 다시 읽기
            QuestTalk.talkLoop(sp, giver, null);
            pick(h, sp, "고친 이름");
            pick(h, sp, "수락하기");
            String qs = Props.get(sp, "rpg:qs");
            h.assertTrue(qs != null && qs.contains("\"" + id + "\":{\"s\":\"a\",\"n\":0,\"c\":0}"), "state json " + qs);
            Quests.setNavOff(sp, true);
            h.assertTrue(("{\"off\":1,\"q\":\"" + id + "\"}").equals(Props.get(sp, "rpg:nav")), "nav json " + Props.get(sp, "rpg:nav"));
            Quests.forgetQuestPlayer(sp.getUUID());
            h.assertTrue(Quests.stateOf(sp).get(id).active() && Quests.navOf(sp).off() && Quests.navOf(sp).q().equals(id), "state reloaded");
            h.assertTrue(Quests.navGoal(sp) == null, "nav off → no goal");
            Quests.setNavOff(sp, false);
            h.assertTrue(Quests.navGoal(sp) != null && Quests.navGoal(sp).why().equals("hunt") && "gulon".equals(Quests.navGoal(sp).mob()), "hunt gulon " + Quests.navGoal(sp));
            // 기록 초기화 (/questreset)
            Quests.resetQuests(sp);
            h.assertTrue(Quests.stateOf(sp).isEmpty() && "{}".equals(Props.get(sp, "rpg:qs")) && Quests.pathOf(sp).isEmpty(), "reset");
            Quests.deleteQuest(id);
            h.assertTrue(Quests.getQuest(id) == null && Props.world(server, "rpg:quest:" + id) == null, "deleted");
            id = null;
        } finally {
            Forms.HEADLESS.remove(sp.getUUID());
            if (id != null) Quests.deleteQuest(id);
            giver.discard();
        }
        done(h, sp);
    }

    // ------------------------------------------------------------ 보스 · 전설의 시련 · 전리품

    /** 보스 처치 (5단계 보스가 부름) → 보스 퀘스트 · 전설의 시련 보스 목표, RPG 몬스터 처치 (종류 ID 훅) → 시련의 처치 목표 */
    @GameTest(maxTicks = 40)
    public void bossKillAndTrial(GameTestHelper h) {
        ServerPlayer sp = player(h);
        String bossStep = MainQuests.mainId("sword", MainQuests.mainBossStep("scarecrow_king"));
        String legend = MainQuests.legendId("sword", "transcend");
        Props.set(sp, "rpg:main_path", "sword");
        Props.set(sp, "rpg:qs", "{\"" + bossStep + "\":{\"s\":\"a\",\"n\":0,\"c\":0},\"" + legend + "\":{\"s\":\"a\",\"n\":0,\"c\":0,\"o\":[0,0,0,0,0,0,0,0,0]}}");
        Quests.forgetQuestPlayer(sp.getUUID());
        Quest boss = Quests.getQuest(bossStep);
        Quest trial = Quests.getQuest(legend);
        h.assertTrue(boss.type.equals("boss") && boss.target.equals("scarecrow_king") && trial.type.equals("trial") && trial.goals.size() == 9, "defs");
        h.assertTrue(Quests.statusOf(sp, boss).equals("active"), "boss active");
        Quests.NavGoal g = Quests.goalOf(sp, trial);
        h.assertTrue(g != null && g.why().equals("hunt") && g.mobs().equals(List.of("draugr", "frost_wolf", "troll", "frost_giant")) && g.count() == 40,
                "trial goal (9단계: 북쪽 새 몬스터도) " + g);
        QuestSystem.onBossKilled("scarecrow_king", List.of(sp));
        h.assertTrue(Quests.statusOf(sp, boss).equals("ready"), "boss ready");
        h.assertTrue(Quests.trialCounts(sp, trial).get(1) == 1, "trial boss goal " + Quests.trialCounts(sp, trial));
        h.assertTrue(Quests.objectiveOf(sp, trial).contains("보스 8종 각 1번 1/8"), Quests.objectiveOf(sp, trial));
        QuestSystem.onBossKilled("scarecrow_king", List.of(sp)); // 같은 보스는 한 번만 셈
        h.assertTrue(Quests.trialCounts(sp, trial).get(1) == 1 && Quests.stateOf(sp).get(boss.id).n == 1, "no double count");
        // RPG 몬스터 처치 (5단계 몬스터: 종류 ID 를 rpg:<키> 로 — 여기서는 좀비를 드라우그로 봄)
        Zombie z = zombie(h, 1.5, 4.5, 1F);
        Function<Entity, String> prev = Quests.entityType;
        Quests.entityType = e -> e == z ? "rpg:draugr" : prev.apply(e);
        try {
            hurt(h, z, sp.damageSources().playerAttack(sp), 50F);
        } finally {
            Quests.entityType = prev;
        }
        h.assertTrue(Quests.trialCounts(sp, trial).get(0) == 1, "trial kill goal " + Quests.trialCounts(sp, trial));
        h.assertTrue(Quests.stateOf(sp).get(trial.id).n == 2, "trial total " + Quests.stateOf(sp).get(trial.id).n);
        done(h, sp);
    }

    /** 전리품 · 사냥 추천 (loot.js 의 일부 — 5단계 몬스터가 씀) */
    @GameTest(maxTicks = 20)
    public void lootHelpers(GameTestHelper h) {
        ServerPlayer sp = player(h);
        h.assertTrue("troll".equals(Loot.dropKeyOf("rpg:loot_troll")) && Loot.dropKeyOf("minecraft:dirt") == null, "drop key");
        ItemStack s = Loot.dropItem("troll", 3);
        h.assertTrue(s.is(GearItems.byId("rpg:loot_troll")) && s.getCount() == 3, "drop item");
        h.assertTrue(Loot.dropItemIds().size() == 14 && Loot.dropItemIds().contains("rpg:loot_frost_giant"), "drop ids (9단계 14종) " + Loot.dropItemIds());
        h.assertTrue(Loot.huntKind(0).equals("scarecrow") && Loot.huntKind(9).equals("gulon"), "hunt kind");
        h.assertTrue(Loot.monsterXp(sp, "scarecrow") > 0 && !Loot.xpCapped(sp, "scarecrow"), "xp at level 0");
        // 들어 있는 퀘스트 수 · 순서 (메인 3 + 6 × 46, 반복 14 · 왕실 7 · 전설 5)
        h.assertTrue(Quests.allQuests().stream().filter(q -> q.main).count() == 3 + 6L * MainQuests.PATH_STEPS, "main count");
        h.assertTrue(Quests.sideQuests().size() == 26, "side count " + Quests.sideQuests().size());
        h.assertTrue(Quests.allQuests().get(0).id.equals(MainQuests.MAIN_START), "order");
        // 9단계 새 몬스터도 레벨 순서로 추천 (2렙 → 니세 Lv.4, 37렙 → 서리 거인 Lv.39)
        h.assertTrue(Loot.huntKind(2).equals("nisse") && Loot.huntKind(37).equals("frost_giant"), "hunt kind (new monsters) " + Loot.huntKind(2) + " " + Loot.huntKind(37));
        done(h, sp);
    }

    // ------------------------------------------------------------ 9단계: 레벨업 3배 · 지역 이야기의 새 몬스터 단계 · 31단계 → 46단계 옮기기

    /** 레벨업에 필요한 경험치 = 바닐라 × 3 (사용자 요청 "약 3배"): 0렙 7 → 21 · 15렙 37 → 111 · 30렙 112 → 336, 21 포인트면 0 → 1렙 */
    @GameTest(maxTicks = 20)
    public void levelingTripled(GameTestHelper h) {
        ServerPlayer sp = player(h);
        h.assertTrue(mmorpg.core.Leveling.needMult() == 3, "needMult " + mmorpg.core.Leveling.needMult());
        for (int[] c : new int[][] {{0, 21}, {14, 105}, {15, 111}, {29, 321}, {30, 336}, {40, 606}}) {
            sp.setExperienceLevels(c[0]);
            h.assertTrue(sp.getXpNeededForNextLevel() == c[1], "need at level " + c[0] + ": " + sp.getXpNeededForNextLevel() + " (want " + c[1] + ")");
        }
        sp.setExperienceLevels(0);
        sp.experienceProgress = 0F;
        sp.giveExperiencePoints(20);
        h.assertTrue(sp.experienceLevel == 0, "20 points: still level 0 (" + sp.experienceLevel + ")");
        sp.setExperienceLevels(0);
        sp.experienceProgress = 0F;
        sp.giveExperiencePoints(21);
        h.assertTrue(sp.experienceLevel == 1 && sp.experienceProgress < 0.01F, "21 points: level 1 (" + sp.experienceLevel + ", " + sp.experienceProgress + ")");
        // 퀘스트 레벨 보상 2 = 지금 레벨부터 바닐라 두 레벨의 경험치 (실제로는 2/3 레벨)
        h.assertTrue(mmorpg.core.Leveling.pointsForLevels(1, 2) == 9 + 11, "quest 2 levels at level 1 = " + mmorpg.core.Leveling.pointsForLevels(1, 2));
        done(h, sp);
    }

    /**
     * 9단계 메인 이야기: 길마다 46단계, 지역마다 새 몬스터 둘의 처치 단계 (그 지역 NPC 가 줌) 와 그 전리품 모으기 단계 (보스 앞), 처치 · 모으기 수 늘림
     * (칼의 길 처치 86 → 190 · 모으기 56 → 122), 모든 길 · 모든 단계에 대사 · 앞 단계 이어짐
     */
    @GameTest(maxTicks = 20)
    public void mainArcsWithNewMonsters(GameTestHelper h) {
        h.assertTrue(MainQuests.PATH_STEPS == 46, "46 steps " + MainQuests.PATH_STEPS);
        // [몬스터, 주는 NPC, 처치 수, 전리품 단계 (니세는 처치만 — 농장이 너무 길지 않게)]
        Object[][] want = {{"nisse", "main_13", 10, false}, {"boar", "main_13", 10, true}, {"sand_scorpion", "main_14", 12, true}, {"worm_larva", "main_14", 12, true},
            {"crab", "main_15", 12, true}, {"grendel", "main_15", 10, true}, {"frost_wolf", "main_16", 12, true}, {"frost_giant", "main_16", 10, true}};
        for (Object[] w : want) {
            String mob = (String) w[0];
            int kill = MainQuests.mainKillStep(mob);
            Quest q = Quests.getQuest(MainQuests.mainId("sword", kill));
            h.assertTrue(q != null && q.type.equals("kill") && q.target.equals("rpg:" + mob) && q.giver.equals(w[1]) && q.count == (int) w[2],
                    mob + " kill step " + kill + " " + (q == null ? null : q.type + " " + q.target + " " + q.giver + " " + q.count));
            boolean collect = false;
            for (int s = kill + 1; s <= MainQuests.PATH_STEPS; s++) {
                Quest c = Quests.getQuest(MainQuests.mainId("sword", s));
                if (c.type.equals("boss")) break;
                if (c.type.equals("collect") && c.target.equals("rpg:loot_" + mob)) collect = true;
            }
            h.assertTrue(collect == (boolean) w[3], mob + " loot step before the boss: " + collect);
        }
        // 농장: 허수아비 12 → 니세 10 → 멧돼지 10 → 멧돼지 엄니 6 → 낡은 짚단 8 → 허수아비 킹
        String[] farm = {"kill rpg:scarecrow 12", "kill rpg:nisse 10", "kill rpg:boar 10", "collect rpg:loot_boar 6", "collect rpg:loot_scarecrow 8", "boss scarecrow_king 1"};
        for (int i = 0; i < farm.length; i++) {
            Quest q = Quests.getQuest(MainQuests.mainId("sword", 3 + i));
            h.assertTrue((q.type + " " + q.target + " " + q.count).equals(farm[i]), "farm step " + (3 + i) + ": " + q.type + " " + q.target + " " + q.count);
        }
        int kills = 0;
        int collects = 0;
        for (String path : MainQuests.PATH_ORDER) {
            for (int s = 1; s <= MainQuests.PATH_STEPS; s++) {
                Quest q = Quests.getQuest(MainQuests.mainId(path, s));
                h.assertTrue(q != null && !q.title.isEmpty() && !q.offer.isEmpty() && !q.progress.isEmpty() && !q.done.isEmpty(), path + " step " + s + " text");
                h.assertTrue(q.requires.equals(s == 1 ? MainQuests.MAIN_CHOOSE : MainQuests.mainId(path, s - 1)), path + " step " + s + " requires " + q.requires);
                if (path.equals("sword") && q.type.equals("kill")) kills += q.count;
                if (path.equals("sword") && q.type.equals("collect")) collects += q.count;
            }
        }
        h.assertTrue(kills == 190 && collects == 122, "kills " + kills + " · collects " + collects + " (0.8: 86 · 56)");
        h.succeed();
    }

    private static String rid(String path, int n) {
        return MainQuests.MAIN_PREFIX + path + "_r" + (n < 10 ? "0" : "") + n;
    }

    /** 예전 31단계 기록: 1 ~ doneTo 끝, activeAt 진행 중 (n) */
    private static java.util.Map<String, Quest.Entry> rState(String path, int doneTo, int activeAt, int n) {
        java.util.Map<String, Quest.Entry> st = new java.util.LinkedHashMap<>();
        for (int i = 1; i <= doneTo; i++) st.put(rid(path, i), new Quest.Entry("d", 0, 1));
        if (activeAt > 0) st.put(rid(path, activeAt), new Quest.Entry("a", n, 0));
        return st;
    }

    private static boolean sDone(java.util.Map<String, Quest.Entry> st, String path, int s) {
        Quest.Entry e = st.get(MainQuests.mainId(path, s));
        return e != null && e.c > 0;
    }

    /**
     * 예전 31단계 (0.8.x 까지 "mq_&lt;길&gt;_rNN") 진행 → 9단계 46단계: 같은 일의 새 단계까지 끝 (사이에 생긴 새 몬스터 단계도), 잡고 보고 전인 보스는 보고만 남은 채로,
     * 진행 중이던 단계는 새 단계에서도 바로 다음이면 그대로 (아니면 버림 — 사이에 새 몬스터 단계), 다른 길 기록은 건드리지 않음, 한 번만. 저장된 진행도 처음 읽을 때 옮김
     */
    @GameTest(maxTicks = 20)
    public void migrate31To46(GameTestHelper h) {
        int[] map = {0, 1, 2, 3, 7, 8, 9, 10, 11, 14, 15, 16, 19, 20, 21, 24, 25, 26, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 43, 44, 45, 46};
        for (int r = 1; r <= 31; r++) h.assertTrue(MainQuests.rToStep(r) == map[r], "r" + r + " → s" + MainQuests.rToStep(r) + " (want " + map[r] + ")");
        // 1. 허수아비 킹을 잡고 보고함 (r05) → 새 보스 단계 (s08) 까지 끝 — 사이의 니세 · 멧돼지 단계도
        java.util.Map<String, Quest.Entry> st = rState("sword", 5, 0, 0);
        h.assertTrue(MainQuests.migrateRState(st, "sword") && st.containsKey(MainQuests.MIGRATED_S_KEY), "migrated once");
        for (int s = 1; s <= 8; s++) h.assertTrue(sDone(st, "sword", s), "s" + s + " done");
        h.assertTrue(!st.containsKey(MainQuests.mainId("sword", 9)), "s09 (travel) still to take");
        h.assertFalse(MainQuests.migrateRState(st, "sword"), "only once");
        // 2. 보스를 잡고 보고 전 (r05 진행 n=1) → s01 ~ s07 끝, s08 보고만 남음
        st = rState("sword", 4, 5, 1);
        MainQuests.migrateRState(st, "sword");
        Quest.Entry boss = st.get(MainQuests.mainId("sword", 8));
        h.assertTrue(sDone(st, "sword", 7) && boss != null && boss.active() && boss.n == 1 && boss.c == 0, "boss ready to report " + boss);
        // 3. 바다에 와서 (r13) 껍질 괴물 처치 중 (r14 n=5) → s20 까지 끝, s21 진행 그대로
        st = rState("dagger", 13, 14, 5);
        MainQuests.migrateRState(st, "dagger");
        Quest.Entry kill = st.get(MainQuests.mainId("dagger", 21));
        h.assertTrue(sDone(st, "dagger", 20) && kill != null && kill.active() && kill.n == 5, "active kill kept " + kill);
        // 4. 허수아비 처치 (r03) 뒤 짚단 모으는 중 (r04) → 새 단계로는 바로 다음이 아님 (니세 · 멧돼지가 먼저) → 버림
        st = rState("axe", 3, 4, 3);
        MainQuests.migrateRState(st, "axe");
        h.assertTrue(sDone(st, "axe", 3) && !st.containsKey(MainQuests.mainId("axe", 4)) && !st.containsKey(MainQuests.mainId("axe", 7)), "non-adjacent active dropped");
        // 5. 다른 길의 기록은 옮기지 않음 · 6. 끝까지 (r31) → 46단계 모두
        st = rState("sword", 5, 0, 0);
        MainQuests.migrateRState(st, "bow");
        h.assertTrue(st.containsKey(MainQuests.MIGRATED_S_KEY) && !st.containsKey(MainQuests.mainId("sword", 1)) && !st.containsKey(MainQuests.mainId("bow", 1)), "other path");
        st = rState("wand", 31, 0, 0);
        MainQuests.migrateRState(st, "wand");
        for (int s = 1; s <= MainQuests.PATH_STEPS; s++) h.assertTrue(sDone(st, "wand", s), "finished story: s" + s);
        h.assertFalse(MainQuests.migrateRState(new java.util.LinkedHashMap<>(), "wand"), "nothing to migrate");
        // 저장된 진행 (사막까지 끝 — r13 바다 도착) 을 처음 읽음 → 옮겨 저장, 반복 의뢰: 웜 유충 (사막) 열림 · 집게 게 (바다) 잠김
        ServerPlayer sp = player(h);
        StringBuilder qs = new StringBuilder("{");
        for (int i = 1; i <= 13; i++) qs.append(i > 1 ? "," : "").append("\"").append(rid("shield", i)).append("\":{\"s\":\"d\",\"n\":0,\"c\":1}");
        Props.set(sp, "rpg:main_path", "shield");
        Props.set(sp, "rpg:qs", qs.append("}").toString());
        Quests.forgetQuestPlayer(sp.getUUID());
        sp.setExperienceLevels(30); // 바다 단계 (레벨 24) 를 받을 수 있게
        RpgPlayers.checkLevelUp(sp);
        h.assertTrue(sDone(Quests.stateOf(sp), "shield", 20) && !Quests.stateOf(sp).containsKey(MainQuests.mainId("shield", 21)), "saved progress migrated");
        h.assertTrue(Props.get(sp, "rpg:qs").contains(MainQuests.MIGRATED_S_KEY), "migration saved");
        h.assertTrue(status(sp, MainQuests.mainId("shield", 21)).equals("available"), "next: sea kill step " + status(sp, MainQuests.mainId("shield", 21)));
        h.assertTrue(status(sp, SideQuests.repeatId("worm_larva")).equals("available") && status(sp, SideQuests.repeatId("crab")).equals("locked"),
                "repeat quests by region " + status(sp, SideQuests.repeatId("worm_larva")) + " " + status(sp, SideQuests.repeatId("crab")));
        done(h, sp);
    }

    /**
     * 지역 이야기의 새 몬스터 단계 (농장): 허수아비 처치를 마치면 올라프가 "장난꾸러기 니세" (10마리) 를 줌 → 대사 · 길 안내 (니세 사냥) →
     * 진짜 니세를 잡으면 셈 → 보고 (대사 · 에메랄드) → 대화가 이어져 다음 단계 (광폭 멧돼지) 를 받을 수 있음
     */
    @GameTest(maxTicks = 40)
    public void regionalNewMonsterStep(GameTestHelper h) {
        ServerPlayer sp = player(h);
        Mannequin olaf = mainNpc(h, "main_13", new Vec3(3.5, 2, 3.5));
        Forms.HEADLESS.add(sp.getUUID());
        mmorpg.fabric.mob.RpgMonster nisse = null;
        try {
            int ks = MainQuests.mainKillStep("nisse");
            String nisseStep = MainQuests.mainId("sword", ks);
            String boarStep = MainQuests.mainId("sword", MainQuests.mainKillStep("boar"));
            StringBuilder qs = new StringBuilder("{");
            for (int s = 1; s < ks; s++) qs.append(s > 1 ? "," : "").append("\"").append(MainQuests.mainId("sword", s)).append("\":{\"s\":\"d\",\"n\":0,\"c\":1}");
            Props.set(sp, "rpg:main_path", "sword");
            Props.set(sp, "rpg:qs", qs.append("}").toString());
            Quests.forgetQuestPlayer(sp.getUUID());
            h.assertTrue(status(sp, nisseStep).equals("available") && status(sp, boarStep).equals("locked"), "nisse step available " + status(sp, nisseStep));
            QuestTalk.talkLoop(sp, olaf, null);
            pick(h, sp, "장난꾸러기 니세");
            nextPages(sp);
            h.assertTrue(dialogText(sp).contains("니세 열만 혼내 주게") && dialogText(sp).contains("니세(Lv.4)"), "offer " + dialogText(sp));
            pick(h, sp, "수락하기");
            h.assertTrue(status(sp, nisseStep).equals("active"), "active");
            Quests.NavGoal g = Quests.navGoal(sp);
            h.assertTrue(g != null && g.why().equals("hunt") && "nisse".equals(g.mob()), "nav: hunt nisse " + g);
            // 9마리 잡아 둔 채로 → 진짜 니세 하나를 잡음
            Quests.stateOf(sp).get(nisseStep).n = 9;
            nisse = mmorpg.fabric.mob.Monsters.spawnAt(h.getLevel(), "nisse", h.absoluteVec(new Vec3(1.5, 2, 4.5)), 0F);
            hurt(h, nisse, sp.damageSources().playerAttack(sp), 1000F);
            h.assertTrue(nisse.isDeadOrDying() && status(sp, nisseStep).equals("ready"), "10th nisse → ready " + Quests.stateOf(sp).get(nisseStep).n);
            int money = Inv.money(sp);
            QuestTalk.talkLoop(sp, olaf, null);
            pick(h, sp, "완료 보고");
            nextPages(sp);
            h.assertTrue(dialogText(sp).contains("밭에 뭔가 단단히 잘못됐어") && dialogText(sp).contains("퀘스트 완료"), "done " + dialogText(sp));
            pick(h, sp, "확인");
            h.assertTrue(status(sp, nisseStep).equals("done") && Inv.money(sp) == money + Quests.getQuest(nisseStep).reward.emerald, "reported · emerald");
            h.assertTrue(choices(sp).stream().anyMatch(c -> c.contains("광폭 멧돼지")), "next step offered " + choices(sp));
            pick(h, sp, "대화 끝내기");
            h.assertFalse(Forms.pending(sp), "closed");
        } finally {
            Forms.HEADLESS.remove(sp.getUUID());
            olaf.discard();
            if (nisse != null) nisse.discard();
        }
        done(h, sp);
    }

    // ------------------------------------------------------------ /mainnpc

    /** /mainnpc 12: 룬술사 에이빈드를 내 앞에 세움 (이름 · 룬술사 · 세운 기록), 다시 치면 새로 만들지 않고 옮김. 번호 없이 치면 목록 */
    @GameTest(maxTicks = 20)
    public void mainNpcSpawnsAndMoves(GameTestHelper h) {
        ServerPlayer sp = player(h);
        MinecraftServer server = h.getLevel().getServer();
        h.assertTrue(server.getCommands().getDispatcher().getRoot().getChild("mainnpc") != null && server.getCommands().getDispatcher().getRoot().getChild("quest") != null,
                "commands registered");
        String list = MainNpcs.mainNpcCommand(sp, null);
        h.assertTrue(list.contains("[메인 퀘스트 NPC]") && list.contains("룬술사 에이빈드") && list.contains("농장"), "list");
        String made = MainNpcs.mainNpcCommand(sp, 12);
        h.assertTrue(made.contains("세웠습니다"), made);
        List<Mannequin> npcs = alive(NpcData.loaded(server).getOrDefault("main_12", List.of()));
        h.assertTrue(npcs.size() == 1, "one npc " + npcs.size());
        Mannequin npc = npcs.get(0);
        try {
            h.assertTrue(NpcData.isRunesmith(npc) && NpcData.name(npc).equals("룬술사 에이빈드") && NpcData.isRpgNpc(npc), "npc data");
            h.assertTrue(NpcData.mainRecord(server, "main_12").id().equals(npc.getUUID().toString()), "main record");
            h.assertTrue(NpcData.nameByKey(server, "main_12").equals("룬술사 에이빈드"), "name registry");
            Vec3 spot = NpcData.spotNear(sp);
            h.assertTrue(npc.position().distanceTo(spot) < 0.01, "in front " + npc.position() + " vs " + spot);
            // 다른 자리에서 다시: 옮김
            Vec3 at = h.absoluteVec(new Vec3(5.5, 2, 1.5));
            sp.snapTo(at.x, at.y, at.z, 0F, 0F);
            Vec3 spot2 = NpcData.spotNear(sp);
            String moved = MainNpcs.mainNpcCommand(sp, 12);
            h.assertTrue(moved.contains("옮겼습니다"), moved);
            List<Mannequin> after = alive(NpcData.loaded(server).getOrDefault("main_12", List.of()));
            h.assertTrue(after.size() == 1 && after.get(0).getUUID().equals(npc.getUUID()), "same npc " + after.size());
            h.assertTrue(after.get(0).position().distanceTo(spot2) < 0.01, "moved " + after.get(0).position() + " vs " + spot2);
            NpcData.Spot rec = NpcData.mainRecord(server, "main_12");
            h.assertTrue(rec.x() == Math.floor(spot2.x) && rec.z() == Math.floor(spot2.z), "record moved " + rec);
            h.assertTrue(MainNpcs.mainNpcCommand(sp, 99).contains("[메인 퀘스트 NPC]"), "unknown number → list");
        } finally {
            for (Mannequin m : NpcData.loaded(server).getOrDefault("main_12", List.of())) m.discard();
        }
        done(h, sp);
    }

    private static List<Mannequin> alive(List<Mannequin> list) {
        List<Mannequin> out = new ArrayList<>();
        for (Mannequin m : list) if (!m.isRemoved() && m.isAlive()) out.add(m);
        return out;
    }
}
