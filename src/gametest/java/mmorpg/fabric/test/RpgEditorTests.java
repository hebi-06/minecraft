package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.npc.NpcEditor;
import mmorpg.fabric.npc.Npcs;
import mmorpg.fabric.quest.Names;
import mmorpg.fabric.quest.Quest;
import mmorpg.fabric.quest.Quests;
import mmorpg.fabric.ui.FormNet;
import mmorpg.fabric.ui.Forms;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * 4단계 NPC · 퀘스트 설정 창 시험 (editor.js — 서버, 클라이언트 없이 창 흐름 Forms.HEADLESS): NPC 관리 → NPC 만들기 → 설정 창
 * (이름 · 인사말 · 상점 · 대장장이 · 대화 미리보기 · 스킨 · 옮기기 · 지우기 — 마네킹의 이름표 · 태그 · Props), 처치 퀘스트 만들기 · 고치기 (잘못된 몹 ID 는
 * 까닭과 함께 다시 · 가져오기로 바꾸면 대상 이름 자동) · 지우기, 말 걸기 퀘스트 · 선행 퀘스트가 서로를 가리키면 다시, NPC 를 지우면 그 NPC 의 퀘스트 ·
 * 말 걸기 퀘스트 · 메인 NPC 기록도, 권한 (생존 · 운영자 아님 → 못 엶, /npc 는 관리자), 설정 도구로 NPC 누르기 → 설정 창 · 허공 → 관리 창,
 * 예전 역할 명령 (/npc spawn · tag).
 */
public final class RpgEditorTests {
    // 설정 창 버튼
    private static final int S_NAME = 0;
    private static final int S_GREET = 1;
    private static final int S_ROLES = 2;
    private static final int S_ADD_QUEST = 3;
    private static final int S_QUESTS = 4;
    private static final int S_PREVIEW = 5;
    private static final int S_MOVE = 6;
    private static final int S_SKIN = 7;
    private static final int S_DELETE = 8;
    private static final int S_CLOSE = 9;
    // 퀘스트 편집 창의 입력 칸 (순서)
    private static final int Q_TITLE = 0;
    private static final int Q_GIVER = 1;
    private static final int Q_TYPE = 2;
    private static final int Q_MOB = 3;
    private static final int Q_ITEM = 4;
    private static final int Q_CUSTOM = 5;
    private static final int Q_TALK = 6;
    private static final int Q_COUNT = 7;
    private static final int Q_TARGET_NAME = 8;
    private static final int Q_OFFER = 9;
    private static final int Q_PROGRESS = 10;
    private static final int Q_DONE = 11;
    private static final int Q_EMERALD = 12;
    private static final int Q_XP = 13;
    private static final int Q_POINTS = 14;
    private static final int Q_WTYPE = 15;
    private static final int Q_WLEVEL = 16;
    private static final int Q_WGRADE = 17;
    private static final int Q_RITEM = 18;
    private static final int Q_REQUIRES = 20;
    private static final int Q_MIN_LEVEL = 21;
    private static final int Q_REPEAT = 22;
    private static final int Q_INPUTS = 23;

    // ------------------------------------------------------------ 도움

    /** 크리에이티브 (편집 가능) 플레이어 · 창은 클라이언트 없이 */
    private static ServerPlayer admin(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        sp.setGameMode(GameType.CREATIVE);
        Forms.HEADLESS.add(sp.getUUID());
        return sp;
    }

    private static void finish(GameTestHelper h, ServerPlayer sp) {
        Forms.HEADLESS.remove(sp.getUUID());
        RpgGearTests.done(h, sp);
    }

    /** 시험 자리 rel 에 RPG NPC (마네킹) */
    private static Mannequin npc(GameTestHelper h, Vec3 rel, String name) {
        Mannequin m = NpcData.spawnAt(h.getLevel(), h.absoluteVec(rel), null);
        NpcData.setName(m, name);
        NpcData.refresh(m, 0);
        return m;
    }

    /** 시험이 만든 NPC · 퀘스트 치우기 (다른 시험의 목록에 남지 않게) */
    private static void cleanup(MinecraftServer server, List<? extends Entity> npcs) {
        for (Entity e : npcs) {
            if (e == null) continue;
            String key = NpcData.key(e);
            if (!key.isEmpty()) {
                for (Quest q : Quests.questsOf(key)) Quests.deleteQuest(q.id);
                NpcData.forget(server, key);
            }
            if (!e.isRemoved()) e.discard();
        }
    }

    private static String plain(String s) {
        return BedrockText.plain(s);
    }

    private static String nameTag(Entity e) {
        return e.getCustomName() == null ? "(이름표 없음)" : plain(e.getCustomName().getString());
    }

    private static FormNet.MenuPayload menu(ServerPlayer sp) {
        return Forms.last(sp) instanceof FormNet.MenuPayload m ? m : null;
    }

    private static FormNet.ModalPayload modal(ServerPlayer sp) {
        return Forms.last(sp) instanceof FormNet.ModalPayload m ? m : null;
    }

    /** 지금 창 (실패 글자용) */
    private static String what(ServerPlayer sp) {
        Object p = Forms.last(sp);
        if (p instanceof FormNet.MenuPayload m) {
            List<String> b = new ArrayList<>();
            for (FormNet.ChoiceData c : m.buttons()) b.add(plain(c.text()));
            return "[menu " + plain(m.title()) + " | " + plain(m.text()).replace('\n', '/') + " | " + b + "]";
        }
        if (p instanceof FormNet.ModalPayload m) return "[modal " + plain(m.title()) + " " + inputsOf(m) + "]";
        if (p instanceof FormNet.DialogPayload d) return "[dialog " + plain(d.name()) + ": " + plain(d.text()) + "]";
        return String.valueOf(p);
    }

    private static boolean isMenu(ServerPlayer sp, String titlePart) {
        FormNet.MenuPayload m = menu(sp);
        return Forms.pending(sp) && m != null && plain(m.title()).contains(titlePart);
    }

    private static boolean isModal(ServerPlayer sp, String titlePart) {
        FormNet.ModalPayload m = modal(sp);
        return Forms.pending(sp) && m != null && plain(m.title()).contains(titlePart);
    }

    private static List<String> inputsOf(FormNet.ModalPayload m) {
        List<String> out = new ArrayList<>();
        for (FormNet.ControlData c : m.controls()) if (c.isInput()) out.add(c.value());
        return out;
    }

    /** 지금 입력창의 처음 값 (입력 칸 순서 — 바꿀 칸만 고쳐 그대로 돌려줌) */
    private static List<String> inputs(ServerPlayer sp) {
        FormNet.ModalPayload m = modal(sp);
        return m == null ? new ArrayList<>() : inputsOf(m);
    }

    /** 입력창 맨 위의 까닭 줄 (없으면 "") */
    private static String errorLine(ServerPlayer sp) {
        FormNet.ModalPayload m = modal(sp);
        if (m == null || m.controls().isEmpty() || m.controls().get(0).type() != FormNet.ControlData.LABEL) return "";
        return plain(m.controls().get(0).label());
    }

    /** NPC 고르기 (이름 목록 — 편집 창과 같은 순서) 에서 그 번호의 자리 */
    private static int npcIndex(MinecraftServer server, String key) {
        List<String[]> list = NpcData.list(server);
        for (int i = 0; i < list.size(); i++) if (list.get(i)[0].equals(key)) return i;
        return -1;
    }

    /** 선행 퀘스트 고르기에서 그 퀘스트의 자리 (0 = 없음 — 편집 창과 같은 목록: 만든 퀘스트, 고치는 퀘스트는 뺌) */
    private static int requiresIndex(String editingId, String questId) {
        int i = 0;
        for (Quest q : Quests.allQuests()) {
            if (Quests.isBuiltin(q) || q.id.equals(editingId)) continue;
            i++;
            if (q.id.equals(questId)) return i;
        }
        return 0;
    }

    private static List<Quest> made(String key) {
        List<Quest> out = new ArrayList<>();
        for (Quest q : Quests.questsOf(key)) if (!Quests.isBuiltin(q)) out.add(q);
        return out;
    }

    private static Mannequin namedNear(GameTestHelper h, ServerPlayer sp, String name) {
        for (Mannequin m : h.getLevel().getEntitiesOfClass(Mannequin.class, sp.getBoundingBox().inflate(8), e -> name.equals(NpcData.name(e)))) return m;
        return null;
    }

    private static Set<UUID> mannequinsNear(GameTestHelper h, ServerPlayer sp) {
        Set<UUID> out = new HashSet<>();
        for (Mannequin m : h.getLevel().getEntitiesOfClass(Mannequin.class, sp.getBoundingBox().inflate(8), e -> true)) out.add(m.getUUID());
        return out;
    }

    // ------------------------------------------------------------ 시험

    /**
     * 관리 창 → NPC 만들기 (내 앞 마네킹) → 설정 창: 이름 · 인사말 · 상점 10렙 + 대장장이 · 대화 미리보기 · 스킨, 처치 퀘스트 만들기 (이름표 [!]) →
     * 목록에서 고치기 (모르는 몹 ID → 까닭 · 가져오기로 바꾸면 대상 이름 자동) → 지우기, 내 자리로 옮기기, NPC 지우기 → 관리 창 (설정 도구 받기 · 모든 퀘스트)
     */
    @GameTest(maxTicks = 20)
    public void managerCreatesAndEditsNpc(GameTestHelper h) {
        ServerPlayer sp = admin(h);
        MinecraftServer server = h.getLevel().getServer();
        String name = "편집 시험 촌장";
        String name2 = "편집 시험 이장";
        List<Entity> spawned = new ArrayList<>();
        try {
            NpcEditor.openNpcManager(sp);
            FormNet.MenuPayload m = menu(sp);
            h.assertTrue(isMenu(sp, "NPC 관리") && m.buttons().size() == 8, "manager window " + what(sp));
            Forms.answer(sp, 0, null); // NPC 만들기 (대화·퀘스트)
            h.assertTrue(isModal(sp, "NPC 만들기") && inputs(sp).equals(List.of("촌장", "")), "create window " + what(sp));
            Forms.answer(sp, 0, List.of(name, "어서 오게.|반갑네||두 번째 장"));
            Mannequin npc = namedNear(h, sp, name);
            h.assertTrue(npc != null, "npc spawned " + what(sp));
            spawned.add(npc);
            String key = NpcData.key(npc);
            h.assertTrue(npc.position().distanceTo(sp.position()) < 2.5, "in front of me " + npc.position() + " / " + sp.position());
            h.assertTrue(key.startsWith("n") && npc.entityTags().contains(NpcData.TAG), "key + tag " + key + " " + npc.entityTags());
            h.assertTrue(NpcData.greeting(npc).equals("어서 오게.|반갑네||두 번째 장"), "greeting " + NpcData.greeting(npc));
            h.assertTrue(nameTag(npc).contains(name) && NpcData.nameByKey(server, key).equals(name), "name tag / registry " + nameTag(npc));
            m = menu(sp);
            h.assertTrue(isMenu(sp, "NPC 설정 · " + name) && plain(m.text()).contains("번호 " + key) && m.buttons().size() == 10, "settings window " + what(sp));

            // 이름
            Forms.answer(sp, S_NAME, null);
            h.assertTrue(isModal(sp, "이름 바꾸기") && inputs(sp).equals(List.of(name)), "rename window " + what(sp));
            Forms.answer(sp, 0, List.of(name2));
            h.assertTrue(NpcData.name(npc).equals(name2) && nameTag(npc).contains(name2) && NpcData.nameByKey(server, key).equals(name2), "renamed " + nameTag(npc));
            h.assertTrue(isMenu(sp, "NPC 설정 · " + name2), "back to settings " + what(sp));

            // 인사말 (여러 장 · 앞뒤 공백 정리)
            Forms.answer(sp, S_GREET, null);
            h.assertTrue(isModal(sp, "인사말 바꾸기") && inputs(sp).equals(List.of("어서 오게.|반갑네||두 번째 장")), "greeting window " + what(sp));
            Forms.answer(sp, 0, List.of("  안녕하신가|모험가여  "));
            h.assertTrue(NpcData.greeting(npc).equals("안녕하신가|모험가여"), "greeting " + NpcData.greeting(npc));

            // 상점 10렙 + 대장장이
            Forms.answer(sp, S_ROLES, null);
            h.assertTrue(isModal(sp, "상점·대장장이·룬술사") && inputs(sp).equals(List.of("0", "false", "false")), "roles window " + what(sp));
            String lv10 = String.valueOf(Rules.get().numbers("LEVELS").indexOf(10.0) + 1);
            Forms.answer(sp, 0, List.of(lv10, "true", "false"));
            h.assertTrue(NpcData.shopLevel(npc) == 10 && NpcData.isSmith(npc) && !NpcData.isRunesmith(npc), "roles " + npc.entityTags());
            h.assertTrue(npc.entityTags().containsAll(List.of("rpg_shop", "rpg_shop_lv_10", "rpg_smith")), "tags " + npc.entityTags());
            String tag = nameTag(npc);
            h.assertTrue(tag.contains("[상점]") && tag.contains("[대장장이]") && tag.contains("(10렙)") && !tag.contains("[!]"), "name tag " + tag);
            Forms.answer(sp, S_ROLES, null);
            h.assertTrue(inputs(sp).equals(List.of(lv10, "true", "false")), "roles window shows the settings " + inputs(sp));
            Forms.answer(sp, -1, null); // 닫으면 그대로
            h.assertTrue(isMenu(sp, "NPC 설정") && NpcData.shopLevel(npc) == 10 && NpcData.isSmith(npc), "closing keeps roles " + what(sp));

            // 대화 미리보기 (인사말 → 닫으면 설정 창으로)
            Forms.answer(sp, S_PREVIEW, null);
            h.assertTrue(Forms.last(sp) instanceof FormNet.DialogPayload d && plain(d.text()).contains("안녕하신가") && plain(d.name()).equals(name2), "preview " + what(sp));
            Forms.answer(sp, -1, null);
            h.assertTrue(isMenu(sp, "NPC 설정"), "preview ends in settings " + what(sp));

            // 스킨 (플레이어 이름 · 잘못된 이름은 그대로 · 비우면 기본)
            Forms.answer(sp, S_SKIN, null);
            h.assertTrue(isModal(sp, "스킨 바꾸기") && inputs(sp).equals(List.of("")), "skin window " + what(sp));
            Forms.answer(sp, 0, List.of("Notch"));
            h.assertTrue(NpcEditor.skinName(npc).equals("Notch"), "skin " + NpcEditor.skinName(npc));
            Forms.answer(sp, S_SKIN, null);
            Forms.answer(sp, 0, List.of("이름 아님!"));
            h.assertTrue(NpcEditor.skinName(npc).equals("Notch"), "bad name ignored " + NpcEditor.skinName(npc));
            Forms.answer(sp, S_SKIN, null);
            Forms.answer(sp, 0, List.of(""));
            h.assertTrue(NpcEditor.skinName(npc).isEmpty(), "default skin " + NpcEditor.skinName(npc));

            // 처치 퀘스트 만들기
            Forms.answer(sp, S_ADD_QUEST, null);
            h.assertTrue(isModal(sp, "퀘스트 만들기"), "quest window " + what(sp));
            List<String> q = inputs(sp);
            h.assertTrue(q.size() == Q_INPUTS, "quest inputs " + q.size() + " " + q);
            h.assertTrue(q.get(Q_TITLE).equals("새 퀘스트") && q.get(Q_TYPE).equals("0") && q.get(Q_MOB).equals("0") && q.get(Q_COUNT).equals("10")
                    && q.get(Q_EMERALD).equals("5") && q.get(Q_WTYPE).equals("0") && q.get(Q_RITEM).equals("0") && q.get(Q_REPEAT).equals("false"), "new quest defaults " + q);
            h.assertTrue(q.get(Q_GIVER).equals(String.valueOf(npcIndex(server, key))), "giver preselected " + q.get(Q_GIVER) + " vs " + npcIndex(server, key));
            q.set(Q_TITLE, "허수아비 소탕 시험");
            q.set(Q_COUNT, "5");
            q.set(Q_OFFER, "밭을 지켜 주게.|허수아비 5마리!");
            q.set(Q_PROGRESS, "아직인가?");
            q.set(Q_DONE, "고맙네!");
            q.set(Q_EMERALD, "7");
            q.set(Q_XP, "2");
            q.set(Q_POINTS, "1");
            q.set(Q_WTYPE, "1"); // 칼
            q.set(Q_WLEVEL, String.valueOf(Rules.get().numbers("ALL_LEVELS").indexOf(10.0)));
            q.set(Q_WGRADE, "1"); // 희귀
            q.set(Q_MIN_LEVEL, "3");
            q.set(Q_REPEAT, "true");
            Forms.answer(sp, 0, q);
            List<Quest> mine = made(key);
            h.assertTrue(mine.size() == 1, "one quest " + mine.size() + " " + what(sp));
            Quest kill = mine.get(0);
            Names.Named mob = Names.MOBS.get(0);
            h.assertTrue(kill.title.equals("허수아비 소탕 시험") && kill.type.equals("kill") && kill.giver.equals(key) && kill.target.equals(mob.id()) && kill.targetName.equals(mob.name()),
                    "kill quest " + kill.toJson());
            h.assertTrue(kill.count == 5 && kill.reward.emerald == 7 && kill.reward.xp == 2 && kill.reward.points == 1 && kill.reward.weapon.equals("sword_l10_rare")
                    && kill.minLevel == 3 && kill.repeat && kill.offer.equals("밭을 지켜 주게.|허수아비 5마리!") && kill.done.equals("고맙네!"), "kill quest fields " + kill.toJson());
            h.assertTrue(isMenu(sp, "NPC 설정") && npc.entityTags().contains("rpg_quest") && nameTag(npc).contains("[!]"), "quest badge " + nameTag(npc) + " " + what(sp));

            // 목록 → 고치기: 모르는 몹 ID → 까닭과 함께 다시, 가져오기 (전리품) 로 바꾸면 대상 이름 자동
            Forms.answer(sp, S_QUESTS, null);
            m = menu(sp);
            h.assertTrue(isMenu(sp, "퀘스트 목록") && m.buttons().size() == 3 && plain(m.buttons().get(1).text()).contains("허수아비 소탕 시험"), "quest list " + what(sp));
            Forms.answer(sp, 1, null);
            m = menu(sp);
            h.assertTrue(isMenu(sp, "퀘스트") && plain(m.text()).contains("5마리 처치") && plain(m.text()).contains("에메랄드 7") && plain(m.text()).contains("레벨 3 이상"), "quest actions " + what(sp));
            Forms.answer(sp, 0, null); // 수정
            h.assertTrue(isModal(sp, "퀘스트 수정"), "edit window " + what(sp));
            q = inputs(sp);
            h.assertTrue(q.get(Q_TITLE).equals("허수아비 소탕 시험") && q.get(Q_COUNT).equals("5") && q.get(Q_WTYPE).equals("1") && q.get(Q_WGRADE).equals("1")
                    && q.get(Q_REPEAT).equals("true") && q.get(Q_TARGET_NAME).equals(mob.name()) && q.get(Q_OFFER).equals("밭을 지켜 주게.|허수아비 5마리!"), "edit defaults " + q);
            q.set(Q_MOB, String.valueOf(Names.MOBS.size())); // 직접 입력
            q.set(Q_CUSTOM, "not_a_mob");
            Forms.answer(sp, 0, q);
            h.assertTrue(isModal(sp, "퀘스트 수정") && errorLine(sp).contains("알 수 없는 몹 ID") && errorLine(sp).contains("minecraft:not_a_mob"), "asks again " + what(sp));
            h.assertTrue(kill.type.equals("kill") && kill.count == 5 && Quests.getQuest(kill.id) == kill, "not saved yet " + kill.toJson());
            q = inputs(sp);
            q.set(Q_TITLE, "짚단 모으기 시험");
            q.set(Q_TYPE, "1"); // 아이템 가져오기
            q.set(Q_ITEM, "1"); // 첫 몬스터 전리품
            q.set(Q_CUSTOM, "");
            q.set(Q_COUNT, "8");
            q.set(Q_REPEAT, "false");
            Forms.answer(sp, 0, q);
            Quest col = Quests.getQuest(kill.id);
            Obj drop = Rules.get().obj("MONSTER_DROPS").obj(Rules.get().strings("MONSTER_ORDER").get(0));
            h.assertTrue(col != null && col.type.equals("collect") && col.target.equals(drop.str("item")) && col.targetName.equals(drop.str("name")) && col.count == 8 && !col.repeat
                    && col.title.equals("짚단 모으기 시험") && col.reward.weapon.equals("sword_l10_rare"), "collect quest " + (col == null ? null : col.toJson()));
            h.assertTrue(isMenu(sp, "퀘스트 목록") && plain(menu(sp).buttons().get(1).text()).contains("짚단 모으기 시험"), "back to the list " + what(sp));

            // 지우기
            Forms.answer(sp, 1, null);
            Forms.answer(sp, 1, null); // 삭제
            h.assertTrue(isMenu(sp, "퀘스트 삭제"), "delete confirm " + what(sp));
            Forms.answer(sp, 0, null);
            h.assertTrue(Quests.getQuest(kill.id) == null && made(key).isEmpty(), "quest deleted");
            m = menu(sp);
            h.assertTrue(isMenu(sp, "퀘스트 목록") && m.buttons().size() == 2 && plain(m.text()).contains("퀘스트가 없습니다"), "empty list " + what(sp));
            Forms.answer(sp, 1, null); // 뒤로
            h.assertTrue(isMenu(sp, "NPC 설정") && !npc.entityTags().contains("rpg_quest") && !nameTag(npc).contains("[!]"), "no badge " + nameTag(npc) + " " + what(sp));

            // 내 자리로 옮기기 (길 안내 자리도)
            Vec3 to = sp.position().add(1.5, 0, -0.5);
            sp.snapTo(to.x, to.y, to.z, 90F, 0F);
            Forms.answer(sp, S_MOVE, null);
            h.assertTrue(npc.position().distanceTo(to) < 0.01, "moved " + npc.position() + " vs " + to);
            NpcData.Spot spot = NpcData.pos(server, key);
            h.assertTrue(spot != null && Math.abs(spot.x() - to.x) < 0.11 && Math.abs(spot.z() - to.z) < 0.11, "nav spot " + spot + " vs " + to);
            h.assertTrue(isMenu(sp, "NPC 설정"), "after moving " + what(sp));

            // NPC 지우기 (취소 → 그대로, 지우기 → 이름 목록 · 자리 기록도) → 관리 창으로
            Forms.answer(sp, S_DELETE, null);
            h.assertTrue(isMenu(sp, "NPC 삭제") && plain(menu(sp).text()).contains(name2), "delete npc confirm " + what(sp));
            Forms.answer(sp, 1, null); // 취소
            h.assertTrue(isMenu(sp, "NPC 설정") && !npc.isRemoved(), "cancel keeps the npc");
            Forms.answer(sp, S_DELETE, null);
            Forms.answer(sp, 0, null);
            h.assertTrue(npc.isRemoved() && NpcData.nameByKey(server, key).isEmpty() && NpcData.pos(server, key) == null, "npc deleted and forgotten");
            h.assertTrue(isMenu(sp, "NPC 관리"), "back to the manager " + what(sp));

            // 설정 도구 받기 · 모든 퀘스트 (닫으면 관리 창) · 닫기
            Forms.answer(sp, 6, null);
            h.assertTrue(RpgGearTests.countIn(sp, GearItems.NPC_TOOL) == 1 && isMenu(sp, "NPC 관리"), "got the npc tool " + what(sp));
            Forms.answer(sp, 5, null);
            h.assertTrue(isMenu(sp, "모든 퀘스트"), "all quests " + what(sp));
            Forms.answer(sp, -1, null);
            h.assertTrue(isMenu(sp, "NPC 관리"), "manager again " + what(sp));
            Forms.answer(sp, 7, null);
            h.assertFalse(Forms.pending(sp), "manager closed");
        } finally {
            cleanup(server, spawned);
        }
        finish(h, sp);
    }

    /**
     * 말 걸기 퀘스트 (주는 NPC 와 같은 NPC 를 고르면 다시 · 대상 이름 = NPC 이름), 선행 퀘스트가 서로를 가리키면 다시,
     * 대상 NPC 를 지우면 그 말 걸기 퀘스트도, 메인 퀘스트 NPC 를 지우면 세운 기록도 (설정 창을 바로 열었으면 닫을 때 흐름 끝)
     */
    @GameTest(maxTicks = 20)
    public void talkQuestAndDeleteCleanup(GameTestHelper h) {
        ServerPlayer sp = admin(h);
        MinecraftServer server = h.getLevel().getServer();
        Mannequin a = npc(h, new Vec3(3.5, 2, 3.5), "말 걸기 시험 A");
        Mannequin b = npc(h, new Vec3(5.5, 2, 3.5), "말 걸기 시험 B");
        List<Mannequin> spawned = new ArrayList<>(List.of(a, b));
        String ka = NpcData.key(a);
        String kb = NpcData.key(b);
        try {
            NpcEditor.openNpcSettings(sp, a);
            h.assertTrue(isMenu(sp, "NPC 설정 · 말 걸기 시험 A"), "settings " + what(sp));
            Forms.answer(sp, S_ADD_QUEST, null);
            List<String> q = inputs(sp);
            q.set(Q_TITLE, "B 에게 말 걸기");
            q.set(Q_GIVER, String.valueOf(npcIndex(server, ka)));
            q.set(Q_TYPE, "2"); // NPC 에게 말 걸기
            q.set(Q_TALK, String.valueOf(npcIndex(server, ka)));
            q.set(Q_REPEAT, "true");
            Forms.answer(sp, 0, q);
            h.assertTrue(isModal(sp, "퀘스트 만들기") && errorLine(sp).contains("다른 NPC"), "same npc → asks again " + what(sp));
            q = inputs(sp);
            q.set(Q_TALK, String.valueOf(npcIndex(server, kb)));
            Forms.answer(sp, 0, q);
            List<Quest> mine = made(ka);
            h.assertTrue(mine.size() == 1, "talk quest saved " + mine.size() + " " + what(sp));
            Quest talk = mine.get(0);
            h.assertTrue(talk.type.equals("talk") && talk.target.equals(kb) && talk.targetName.equals("말 걸기 시험 B") && !talk.repeat, "talk quest " + talk.toJson());

            // 둘째 퀘스트 (선행 = 말 걸기) → 말 걸기의 선행을 둘째로 하면 서로를 가리킴
            Forms.answer(sp, S_ADD_QUEST, null);
            q = inputs(sp);
            q.set(Q_TITLE, "선행 시험");
            q.set(Q_REQUIRES, String.valueOf(requiresIndex("", talk.id)));
            Forms.answer(sp, 0, q);
            Quest second = null;
            for (Quest x : made(ka)) if (x.title.equals("선행 시험")) second = x;
            h.assertTrue(second != null && second.requires.equals(talk.id), "requires saved " + (second == null ? what(sp) : second.toJson()));
            Forms.answer(sp, S_QUESTS, null);
            h.assertTrue(isMenu(sp, "퀘스트 목록") && menu(sp).buttons().size() == 4, "two quests " + what(sp));
            Forms.answer(sp, 1, null); // 말 걸기 퀘스트
            Forms.answer(sp, 0, null); // 수정
            q = inputs(sp);
            q.set(Q_REQUIRES, String.valueOf(requiresIndex(talk.id, second.id)));
            Forms.answer(sp, 0, q);
            h.assertTrue(isModal(sp, "퀘스트 수정") && errorLine(sp).contains("서로를 가리킵니다"), "requires loop → asks again " + what(sp));
            Forms.answer(sp, -1, null); // 닫으면 저장 안 함 → 목록
            h.assertTrue(Quests.getQuest(talk.id).requires.isEmpty() && isMenu(sp, "퀘스트 목록"), "loop not saved " + what(sp));
            Forms.answer(sp, -1, null); // 목록 닫기 → 설정 창
            h.assertTrue(isMenu(sp, "NPC 설정"), "settings again " + what(sp));
            Forms.answer(sp, S_CLOSE, null);
            h.assertFalse(Forms.pending(sp), "opened directly → closing ends the flow");

            // B 를 지우면 B 에게 말 걸기 퀘스트도 (A 의 둘째 퀘스트는 남음)
            NpcEditor.openNpcSettings(sp, b);
            Forms.answer(sp, S_DELETE, null);
            h.assertTrue(isMenu(sp, "NPC 삭제") && plain(menu(sp).text()).contains("말 걸기 퀘스트 1개"), "delete b " + what(sp));
            Forms.answer(sp, 0, null);
            h.assertTrue(b.isRemoved() && Quests.getQuest(talk.id) == null && Quests.getQuest(second.id) != null, "talk quest went with b");
            h.assertFalse(Forms.pending(sp), "flow ended after deleting");

            // 메인 퀘스트 NPC (15번): 지우면 세운 기록도 · /mainnpc 로 다시 세울 수 있다는 안내
            Mannequin main = NpcData.spawnAt(h.getLevel(), h.absoluteVec(new Vec3(3.5, 2, 5.5)), null);
            spawned.add(main);
            NpcData.setKey(main, "main_15");
            NpcData.setName(main, "메인 시험 15");
            NpcData.refresh(main, 0);
            NpcData.setMainRecord("main_15", main, null, null);
            NpcEditor.openNpcSettings(sp, main);
            h.assertTrue(isMenu(sp, "NPC 설정") && plain(menu(sp).text()).contains("메인 퀘스트 NPC 15번"), "main npc settings " + what(sp));
            Forms.answer(sp, S_DELETE, null);
            h.assertTrue(isMenu(sp, "NPC 삭제") && plain(menu(sp).text()).contains("/mainnpc 15"), "main delete note " + what(sp));
            Forms.answer(sp, 0, null);
            h.assertTrue(main.isRemoved() && NpcData.mainRecord(server, "main_15") == null && NpcData.nameByKey(server, "main_15").isEmpty(), "main record forgotten");
        } finally {
            cleanup(server, spawned);
        }
        finish(h, sp);
    }

    /** 생존 · 운영자 아님 → 관리 · 설정 창이 열리지 않음, /npc 는 관리자 명령어, 크리에이티브면 열림 (열린 동안 또 열지 않음) */
    @GameTest(maxTicks = 20)
    public void editorNeedsOperatorOrCreative(GameTestHelper h) {
        ServerPlayer sp = RpgGearTests.player(h);
        Forms.HEADLESS.add(sp.getUUID());
        MinecraftServer server = h.getLevel().getServer();
        Mannequin npc = npc(h, new Vec3(3.5, 2, 3.5), "권한 시험 NPC");
        try {
            if (sp.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) server.getPlayerList().deop(sp.nameAndId());
            h.assertFalse(sp.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER), "mock player is not an operator");
            h.assertFalse(NpcEditor.canEdit(sp), "survival non-op cannot edit");
            NpcEditor.openNpcManager(sp);
            h.assertFalse(Forms.pending(sp), "no manager window " + what(sp));
            NpcEditor.openNpcSettings(sp, npc);
            h.assertFalse(Forms.pending(sp), "no settings window " + what(sp));
            Npcs.openSettings.accept(sp, npc); // 설정 도구 훅도 같은 판정
            h.assertFalse(Forms.pending(sp), "no settings window via the hook " + what(sp));
            var commands = server.getCommands();
            h.assertTrue(commands.getDispatcher().getRoot().getChild("npc") != null, "/npc registered");
            commands.performPrefixedCommand(sp.createCommandSourceStack().withPermission(LevelBasedPermissionSet.ALL), "npc");
            h.assertFalse(Forms.pending(sp), "/npc needs permission");
            sp.setGameMode(GameType.CREATIVE);
            h.assertTrue(NpcEditor.canEdit(sp), "creative can edit");
            commands.performPrefixedCommand(sp.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER), "npc");
            h.assertTrue(isMenu(sp, "NPC 관리"), "/npc opens the manager " + what(sp));
            int id = menu(sp).id();
            NpcEditor.openNpcManager(sp);
            NpcEditor.openNpcSettings(sp, npc);
            h.assertTrue(menu(sp) != null && menu(sp).id() == id, "form lock: no new window while one is open " + what(sp));
            Forms.answer(sp, -1, null);
            h.assertFalse(Forms.pending(sp), "closed");
        } finally {
            cleanup(server, List.of(npc));
            Forms.HEADLESS.remove(sp.getUUID());
        }
        RpgGearTests.done(h, sp);
    }

    /** NPC 설정 도구를 들고 NPC 누르기 → 다음 틱에 그 NPC 설정 창 (같은 누름의 아이템 사용은 관리 창을 열지 않음), 허공 누르기 → 관리 창 */
    @GameTest(maxTicks = 30)
    public void toolTapOpensSettings(GameTestHelper h) {
        ServerPlayer sp = admin(h);
        MinecraftServer server = h.getLevel().getServer();
        Mannequin npc = npc(h, new Vec3(1.5, 2, 3.5), "도구 시험 NPC");
        sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GearItems.NPC_TOOL));
        InteractionResult r = UseEntityCallback.EVENT.invoker().interact(sp, h.getLevel(), InteractionHand.MAIN_HAND, npc, null);
        h.assertTrue(r.consumesAction() && Npcs.recentToolTap(sp), "tool tap consumed " + r);
        Npcs.onToolUse(sp); // 같은 누름의 아이템 사용
        h.assertFalse(Forms.pending(sp), "no manager on the same tap " + what(sp));
        h.runAfterDelay(3, () -> {
            try {
                h.assertTrue(isMenu(sp, "NPC 설정 · 도구 시험 NPC"), "settings opened by the tool " + what(sp));
                Forms.answer(sp, -1, null);
                h.assertFalse(Forms.pending(sp), "closed");
                Npcs.onToolUse(sp); // 허공 누르기
                h.assertTrue(isMenu(sp, "NPC 관리"), "manager by the tool " + what(sp));
                Forms.answer(sp, -1, null);
            } finally {
                cleanup(server, List.of(npc));
            }
            finish(h, sp);
        });
    }

    /** 예전 역할 명령: /npc spawn shop 10 → 내 앞에 10렙 상점 NPC (무기상), /npc tag smith 이름 → 가까운 NPC 를 대장장이로, /npc tag clear → 역할 지움 */
    @GameTest(maxTicks = 20)
    public void npcRoleCommands(GameTestHelper h) {
        ServerPlayer sp = admin(h);
        MinecraftServer server = h.getLevel().getServer();
        var commands = server.getCommands();
        var src = sp.createCommandSourceStack().withPermission(LevelBasedPermissionSet.OWNER);
        Set<UUID> before = mannequinsNear(h, sp);
        List<Mannequin> spawned = new ArrayList<>();
        try {
            commands.performPrefixedCommand(src, "npc spawn shop 10");
            for (Mannequin m : h.getLevel().getEntitiesOfClass(Mannequin.class, sp.getBoundingBox().inflate(8), e -> !before.contains(e.getUUID()))) spawned.add(m);
            h.assertTrue(spawned.size() == 1, "one npc spawned " + spawned.size());
            Mannequin shop = spawned.get(0);
            h.assertTrue(NpcData.shopLevel(shop) == 10 && NpcData.name(shop).equals("무기상") && nameTag(shop).contains("[상점]") && NpcData.isRpgNpc(shop), "shop npc " + nameTag(shop));
            commands.performPrefixedCommand(src, "npc tag smith 대장장이 브론");
            h.assertTrue(NpcData.isSmith(shop) && NpcData.name(shop).equals("대장장이 브론") && nameTag(shop).contains("[대장장이]"), "tagged smith " + nameTag(shop));
            commands.performPrefixedCommand(src, "npc tag clear");
            h.assertTrue(shop.entityTags().stream().noneMatch(t -> t.startsWith("rpg_")) && nameTag(shop).equals("대장장이 브론"), "cleared " + shop.entityTags() + " " + nameTag(shop));
            h.assertFalse(Forms.pending(sp), "role commands open no window");
        } finally {
            cleanup(server, spawned);
        }
        finish(h, sp);
    }
}
