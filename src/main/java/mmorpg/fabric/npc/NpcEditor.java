package mmorpg.fabric.npc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import mmorpg.core.Grade;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Props;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.mixin.MannequinAccessor;
import mmorpg.fabric.quest.Loot;
import mmorpg.fabric.quest.MainQuests;
import mmorpg.fabric.quest.Names;
import mmorpg.fabric.quest.Quest;
import mmorpg.fabric.quest.Quests;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.ui.DialogForm;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.Icon;
import mmorpg.fabric.ui.Inv;
import mmorpg.fabric.ui.MenuForm;
import mmorpg.fabric.ui.ModalForm;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * 관리자용 NPC · 퀘스트 설정 창 (editor.js — 명령어 없이 창으로):
 * /npc 또는 NPC 설정 도구로 허공 누르기 → NPC 관리 (NPC 만들기 · 가까운 NPC · 모든 퀘스트 · 설정 도구 받기),
 * NPC 설정 도구를 들고 NPC 누르기 → 그 NPC 설정 (이름 · 인사말 · 상점 · 대장장이 · 룬술사 · 퀘스트 추가 · 목록 · 대화 미리보기 · 옮기기 · 삭제),
 * 퀘스트 편집 (목표 몹 처치 · 아이템 가져오기 · NPC에게 말 걸기 · 레벨 달성, 대사, 보상, 조건). 운영자 또는 크리에이티브만 (canEdit).
 * 예전 /scriptevent rpg:npc · rpg:tag (npc.js applyRole · spawnRoleNpc · tagNearestNpc) 는 /npc spawn · /npc tag 로.
 * 자바에서 달라진 점: 애드온의 await 고리는 받을 일 (콜백) 되부름 (shop 패키지와 같은 방식 — 끝나면 done), 베드락 NPC 의 스킨 고르기
 * (크리에이티브에서 웅크리고 누르기) 대신 설정 창의 "스킨 바꾸기" (플레이어 이름 — 마네킹 스킨), 몹 ID 직접 입력은 자바 엔티티 이름
 * (베드락 이름도 Kit.javaType 으로) 과 RPG 몬스터 (rpg:gulon … — 5단계 몸이 등록되기 전에도) 를 받음, 가방의 RPG 아이템은 아이템 이름으로 보여 줌,
 * NPC 를 만들 때 · 지울 때 짧은 파티클 (메인 NPC 세우기 · 예전 NPC 치우기와 같은 것).
 */
public final class NpcEditor {
    private NpcEditor() {}

    private static final Rules R = Rules.get();
    private static final List<Double> LEVELS = R.numbers("LEVELS");
    private static final List<Double> ALL_LEVELS = R.numbers("ALL_LEVELS");
    private static final List<String> TYPE_ORDER = R.strings("TYPE_ORDER");
    private static final List<String> MONSTER_ORDER = R.strings("MONSTER_ORDER");
    private static final Obj TYPES = R.obj("TYPES");
    private static final Obj MONSTERS = R.obj("MONSTERS");
    private static final Obj MONSTER_DROPS = R.obj("MONSTER_DROPS");
    private static final String CURRENCY_NAME = R.obj("CONFIG").str("CURRENCY_NAME");

    private static final String TOOL_ICON = "textures/items/rpg/npc_tool";
    private static final String DELETE_ICON = "textures/ui/cancel";
    public static final String NO_PERM = "§cNPC 설정은 운영자 또는 크리에이티브 모드에서만 할 수 있습니다.";
    /** 마네킹 스킨으로 쓸 플레이어 이름 */
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    /** 들어 있는 퀘스트 종류 이름 (builtinCountText — 순서대로) */
    private static final String[][] BUILTIN_SIDE_NAMES = {{"repeat", "반복 의뢰"}, {"royal", "왕실 퀘스트"}, {"legend", "전설 퀘스트"}};

    /** NPC 설정 훅 (Npcs.openSettings · openManager) 과 /npc 명령어 */
    public static void init() {
        Npcs.openSettings = NpcEditor::openNpcSettings;
        Npcs.openManager = NpcEditor::openNpcManager;
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    /** 운영자이거나 크리에이티브 모드면 편집 가능 (editor.js canEdit — 게임 모드는 실제 모드로: 시험용 가짜 플레이어는 isCreative 가 늘 참) */
    public static boolean canEdit(ServerPlayer sp) {
        try {
            return sp.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || sp.gameMode.getGameModeForPlayer() == GameType.CREATIVE;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** NPC 관리 창 (/npc · NPC 설정 도구로 허공 누르기). 창이 열려 있으면 무시 (withFormLock) */
    public static void openNpcManager(ServerPlayer sp) {
        if (!canEdit(sp)) {
            Notify.warn(sp, NO_PERM);
            return;
        }
        if (Forms.isOpen(sp)) return;
        managerLoop(sp, 0);
    }

    /** 그 NPC 의 설정 창 (NPC 설정 도구를 들고 NPC 누르기). 창이 열려 있으면 무시 */
    public static void openNpcSettings(ServerPlayer sp, Entity npc) {
        if (!canEdit(sp)) {
            Notify.warn(sp, NO_PERM);
            return;
        }
        if (npc == null || Forms.isOpen(sp)) return;
        settingsLoop(sp, npc, 0, null);
    }

    // ------------------------------------------------------------ 도움

    private static void end(Runnable done) {
        if (done != null) done.run();
    }

    /** 이름표 · 태그 · 이름 목록을 맞추고 번호를 돌려줌 (refresh) */
    private static String refresh(Entity npc) {
        return NpcData.refresh(npc, Quests.badgeCount(NpcData.key(npc, true)));
    }

    /** 편집 창에서 다루는 퀘스트 (메인 · 반복 의뢰 · 왕실 · 전설 퀘스트는 애드온에 들어 있어 빼고) */
    private static List<Quest> editable(List<Quest> list) {
        List<Quest> out = new ArrayList<>();
        for (Quest q : list) if (!Quests.isBuiltin(q)) out.add(q);
        return out;
    }

    private static void say(ServerPlayer sp, String text) {
        Notify.say(sp, text);
    }

    /** 대사 미리보기 (| 는 띄어쓰기, max 글자 넘으면 ...) */
    static String preview(String text, int max) {
        String t = (text == null ? "" : text).replace('|', ' ').strip();
        if (t.isEmpty()) return "(없음)";
        return t.length() > max ? t.substring(0, max) + "..." : t;
    }

    private static String text(Map<String, Object> v, String key) {
        Object o = v.get(key);
        return o == null ? "" : String.valueOf(o);
    }

    private static int num(Map<String, Object> v, String key) {
        return v.get(key) instanceof Integer i ? i : 0;
    }

    private static boolean flag(Map<String, Object> v, String key) {
        return Boolean.TRUE.equals(v.get(key));
    }

    private static int level(List<Double> list, int i) {
        return (int) (double) list.get(i >= 0 && i < list.size() ? i : 0);
    }

    private static Quest.Type typeOf(Quest q) {
        Quest.Type t = Quests.QUEST_TYPES.get(q.type);
        return t != null ? t : Quests.QUEST_TYPES.get("kill");
    }

    /** NPC 머리 위 파티클 (만들 때 토템 · 지울 때 연기) */
    private static void npcFx(Entity npc, String particle, double up) {
        if (npc.level() instanceof ServerLevel level) {
            Vec3 h = npc.getEyePosition();
            SkillFx.vanilla(level, particle, new Vec3(h.x, h.y + up, h.z));
        }
    }

    /** 이전 버전의 무기 보상 (영웅 0 ~ 20렙 · 초월 0 ~ 30렙 — 줄 때 가까운 레벨로 바뀜) 인지 */
    private static boolean legacyReward(Quest q) {
        Weapon w = q.reward.weapon.isEmpty() ? null : Weapons.parseWeapon("rpg:" + q.reward.weapon);
        Weapon iw = q.reward.item != null ? Weapons.parseWeapon(q.reward.item.id) : null;
        return (w != null && w.legacy()) || (iw != null && iw.legacy());
    }

    /** 들어 있는 퀘스트 (고칠 수 없음) 를 종류마다: "메인 퀘스트 2개 · 전설 퀘스트 1개" (없으면 "") */
    static String builtinCountText(List<Quest> list) {
        List<String> parts = new ArrayList<>();
        int mainN = 0;
        for (Quest q : list) if (Quests.isBuiltin(q) && q.main) mainN++;
        if (mainN > 0) parts.add("메인 퀘스트 " + mainN + "개");
        for (String[] side : BUILTIN_SIDE_NAMES) {
            int n = 0;
            for (Quest q : list) if (Quests.isBuiltin(q) && q.side.equals(side[0])) n++;
            if (n > 0) parts.add(side[1] + " " + n + "개");
        }
        return String.join(" · ", parts);
    }

    // ------------------------------------------------------------ NPC 관리

    private static void managerLoop(ServerPlayer sp, int guard) {
        if (guard >= 100) return;
        List<Quest> all = Quests.allQuests();
        List<Quest> mine = editable(all);
        int mainCount = 0;
        int sideCount = 0;
        for (Quest q : all) {
            if (q.main) mainCount++;
            if (!q.side.isEmpty()) sideCount++;
        }
        // v1.1.9: 이전 버전에서 만든 퀘스트의 구버전 무기 보상 (영웅 0~20렙 · 초월 0~30렙) 알림
        int old = 0;
        for (Quest q : mine) if (legacyReward(q)) old++;
        String oldNote = old > 0 ? "\n\n§e구버전 무기 보상 퀘스트 " + old + "개§r§7: 줄 때 같은 등급의 가장 가까운 레벨(영웅 30렙, 초월 40렙)로 바뀝니다. 퀘스트를 열어 저장하면 정리됩니다." : "";
        // v2.6.0: 메인 퀘스트는 애드온에 들어 있음 (/mainnpc 로 NPC 만 세우면 진행)
        String mainNote = mainCount > 0 ? "\n\n§6메인 퀘스트 " + mainCount + "개§r§7" + (sideCount > 0 ? " + 반복 의뢰 · 왕실 · 전설 퀘스트 " + sideCount + "개" : "")
                + ": /mainnpc 번호 로 NPC를 세우면 바로 진행됩니다 (여기서는 고칠 수 없음)." : "";
        new MenuForm("NPC 관리", "§lNPC 만들기§r를 누르면 내 앞에 NPC가 생기고 설정 창이 열립니다.\n\n§7NPC 설정 도구를 들고 NPC를 눌러도 설정 창이 열립니다."
                + "\n§7스킨은 NPC 설정 창의 스킨 바꾸기에서 플레이어 이름으로 바꿉니다." + mainNote + oldNote)
                .button("NPC 만들기 §8(대화·퀘스트)", DialogForm.NEW)
                .button("상점 NPC 만들기", Icon.of("textures/items/emerald"))
                .button("대장장이 NPC 만들기 §8(강화·영웅 레벨)", Icon.of("textures/items/iron_ingot"))
                .button("룬술사 NPC 만들기 §8(룬 갈기·뽑기권)", Icon.of("textures/items/rpg/rune_shard"))
                .button("가까운 NPC 설정 §8(8칸 안)", Icon.of("textures/items/name_tag"))
                .button("모든 퀘스트 §8(" + mine.size() + "개)", Icon.of("textures/items/book_writable"))
                .button("NPC 설정 도구 받기", Icon.of(TOOL_ICON))
                .button("닫기", DialogForm.BYE)
                .show(sp, sel -> {
                    if (sel < 0 || sel == 7) return;
                    Runnable again = () -> managerLoop(sp, guard + 1);
                    switch (sel) {
                        case 0, 1, 2, 3 -> createNpcFlow(sp, sel == 1, sel == 2, sel == 3, npc -> {
                            if (npc != null) settingsLoop(sp, npc, 0, again);
                            else again.run();
                        });
                        case 4 -> {
                            Mannequin npc = NpcData.nearest(sp, 8);
                            if (npc == null) {
                                say(sp, "§c8칸 안에 NPC가 없습니다.");
                                again.run();
                            } else settingsLoop(sp, npc, 0, again);
                        }
                        case 5 -> questListLoop(sp, "", 0, again);
                        case 6 -> {
                            Notify.give(sp, new ItemStack(GearItems.NPC_TOOL));
                            say(sp, "NPC 설정 도구를 받았습니다. §7들고 NPC를 누르세요.");
                            again.run();
                        }
                        default -> again.run();
                    }
                });
    }

    /** NPC 만들기 창 → 내 앞에 마네킹 NPC (닫으면 null) */
    private static void createNpcFlow(ServerPlayer sp, boolean shop, boolean smith, boolean runesmith, Consumer<Entity> then) {
        ModalForm b = new ModalForm(runesmith ? "룬술사 NPC 만들기" : smith ? "대장장이 NPC 만들기" : shop ? "상점 NPC 만들기" : "NPC 만들기")
                .text("name", "이름", "예: 촌장", runesmith ? "룬술사" : smith ? "대장장이" : shop ? "무기상" : "촌장")
                .text("greet", "인사말 §7(| 줄바꿈, || 다음 장)", "예: 어서 오게, 모험가여.", "");
        if (shop) {
            List<String> opts = new ArrayList<>();
            for (double l : LEVELS) opts.add(Js.str(l) + "렙 무기");
            b.dropdown("lv", "파는 무기 레벨", opts, 0);
        }
        b.submit("만들기").show(sp, v -> {
            if (v == null) {
                then.accept(null);
                return;
            }
            Mannequin npc;
            try {
                npc = NpcData.spawnNear(sp);
            } catch (RuntimeException e) {
                say(sp, "§cNPC를 세우지 못했습니다: " + e.getMessage());
                then.accept(null);
                return;
            }
            NpcData.setName(npc, text(v, "name"));
            NpcData.setGreeting(npc, text(v, "greet"));
            if (shop) NpcData.setShop(npc, level(LEVELS, num(v, "lv")));
            if (smith) NpcData.setSmith(npc, true);
            if (runesmith) NpcData.setRunesmith(npc, true);
            refresh(npc);
            SkillFx.playFor(sp, "random.orb", 1.2, 1);
            npcFx(npc, "minecraft:totem_particle", 0.4);
            then.accept(npc);
        });
    }

    // ------------------------------------------------------------ NPC 한 명 설정

    /** 그 NPC 의 설정 창 반복 (닫거나 지우면 done — 관리 창에서 왔으면 관리 창으로) */
    private static void settingsLoop(ServerPlayer sp, Entity npc, int guard, Runnable done) {
        if (guard >= 100) {
            end(done);
            return;
        }
        if (npc.isRemoved() || !npc.isAlive()) {
            say(sp, "§cNPC가 사라졌습니다.");
            end(done);
            return;
        }
        String key = refresh(npc);
        String name = NpcData.name(npc);
        int lv = NpcData.shopLevel(npc);
        List<Quest> all = Quests.questsOf(key);
        List<Quest> list = editable(all);
        String builtinText = builtinCountText(all);
        String greet = NpcData.greeting(npc);
        MainQuests.MainNpc main = MainQuests.mainNpcByKey(key);
        String skin = npc instanceof Mannequin m ? skinName(m) : "";
        List<String> info = new ArrayList<>();
        info.add("§l" + name + "§r §8(번호 " + key + ")");
        if (main != null) info.add("§6메인 퀘스트 NPC " + main.n() + "번§7 · " + main.role());
        info.add("§7상점: §f" + (lv >= 0 ? lv + "렙 무기·갑옷 상점" : "없음") + "§7 · 대장장이: §f" + (NpcData.isSmith(npc) ? "예" : "아니오") + "§7 · 룬술사: §f"
                + (NpcData.isRunesmith(npc) ? "예" : "아니오"));
        info.add("§7퀘스트: §f" + list.size() + "개" + (builtinText.isEmpty() ? "" : " §8(+ " + builtinText + ", 고칠 수 없음)"));
        info.add("§7인사말: §f" + preview(greet, 40) + (main != null && greet.isEmpty() ? " §8(비우면 메인 퀘스트 인사말)" : ""));
        info.add("§7스킨: §f" + (skin.isEmpty() ? "기본" : skin));
        new MenuForm("NPC 설정 · " + name, String.join("\n", info))
                .button("이름 바꾸기", Icon.of("textures/items/name_tag"))
                .button("인사말 바꾸기", Icon.of("textures/items/book_writable"))
                .button("상점·대장장이·룬술사 설정", Icon.of("textures/items/emerald"))
                .button("퀘스트 추가", DialogForm.NEW)
                .button("퀘스트 목록·수정 §8(" + list.size() + "개)", Icon.of("textures/items/book_normal"))
                .button("대화 미리보기", Icon.of("textures/items/paper"))
                .button("내 자리로 옮기기", Icon.of("textures/items/ender_pearl"))
                .button("스킨 바꾸기 §8(플레이어 이름)", Icon.item(Items.PLAYER_HEAD))
                .button("§cNPC 삭제", Icon.of(DELETE_ICON))
                .button("닫기", DialogForm.BYE)
                .show(sp, sel -> {
                    Runnable again = () -> settingsLoop(sp, npc, guard + 1, done);
                    switch (sel) {
                        case 0 -> new ModalForm("이름 바꾸기").text("name", "이름", "예: 촌장", name).submit("저장").show(sp, v -> {
                            if (v != null) NpcData.setName(npc, text(v, "name"));
                            again.run();
                        });
                        case 1 -> new ModalForm("인사말 바꾸기")
                                .label("§7| 는 줄바꿈, || 는 다음 장입니다. (다음 장은 '다음' 버튼으로 넘김)")
                                .text("greet", "인사말", "예: 어서 오게.|요즘 마을이 흉흉하다네.", DialogForm.toEditText(greet))
                                .submit("저장")
                                .show(sp, v -> {
                                    if (v != null) NpcData.setGreeting(npc, text(v, "greet"));
                                    again.run();
                                });
                        case 2 -> roleFlow(sp, npc, lv, again);
                        case 3 -> editQuestFlow(sp, null, key, saved -> again.run());
                        case 4 -> questListLoop(sp, key, 0, again);
                        case 5 -> Quests.talkLoop(sp, npc, again);
                        case 6 -> settingsLoop(sp, moveHere(sp, npc, key, main), guard + 1, done);
                        case 7 -> skinFlow(sp, npc, again);
                        case 8 -> confirmDelete(sp, npc, key, list.size(), deleted -> {
                            if (deleted) end(done);
                            else again.run();
                        });
                        default -> end(done);
                    }
                });
    }

    /** 상점 · 대장장이 · 룬술사 설정 */
    private static void roleFlow(ServerPlayer sp, Entity npc, int lv, Runnable then) {
        List<String> opts = new ArrayList<>();
        opts.add("상점 아님");
        for (double l : LEVELS) opts.add(Js.str(l) + "렙 무기 상점 (갑옷 · 전리품 팔기)");
        new ModalForm("상점·대장장이·룬술사 설정")
                .dropdown("lv", "이 NPC를 누르면 여는 상점", opts, lv >= 0 ? LEVELS.indexOf((double) lv) + 1 : 0)
                .toggle("smith", "대장장이 §7(무기 강화 +1~+10 · 영웅 무기 레벨 30→35→40렙)", NpcData.isSmith(npc))
                .toggle("runesmith", "룬술사 §7(룬 갈기 → 룬 조각 → 룬 뽑기권 · 고급 룬 뽑기권)", NpcData.isRunesmith(npc))
                .submit("저장")
                .show(sp, v -> {
                    if (v != null) {
                        int i = num(v, "lv");
                        NpcData.setShop(npc, i > 0 && i - 1 < LEVELS.size() ? level(LEVELS, i - 1) : -1);
                        NpcData.setSmith(npc, flag(v, "smith"));
                        NpcData.setRunesmith(npc, flag(v, "runesmith"));
                    }
                    then.run();
                });
    }

    /**
     * 내 자리로 옮기기 (내 방향을 봄). 다른 차원이면 새 엔티티가 되므로 옮긴 엔티티를 돌려줌 (실패하면 그대로).
     * 길 안내 자리 · 메인 NPC 기록도 옮긴 자리로 (옮긴 직후에는 엔티티 위치가 아직 예전일 수 있어서 직접 적음)
     */
    private static Entity moveHere(ServerPlayer sp, Entity npc, String key, MainQuests.MainNpc main) {
        try {
            ServerLevel level = sp.level();
            MinecraftServer server = level.getServer();
            Vec3 at = sp.position();
            String dim = NpcData.dimId(level);
            float yaw = sp.getYRot();
            NpcData.Spot rec = main != null ? NpcData.mainRecord(server, key) : null;
            boolean wasMain = rec != null && rec.id().equals(npc.getUUID().toString());
            Entity moved = npc.teleport(new TeleportTransition(level, at, Vec3.ZERO, yaw, 0F, TeleportTransition.DO_NOTHING));
            if (moved == null) throw new IllegalStateException("teleport");
            moved.setYHeadRot(yaw);
            moved.setYBodyRot(yaw);
            if (wasMain) NpcData.setMainRecord(key, moved, at, dim);
            NpcData.notePos(moved, key, at, dim);
            say(sp, "NPC를 옮겼습니다.");
            return moved;
        } catch (RuntimeException e) {
            say(sp, "§c옮기지 못했습니다: " + e);
            return npc;
        }
    }

    /** 마네킹 스킨의 플레이어 이름 (기본 스킨이면 "") */
    public static String skinName(Mannequin m) {
        ResolvableProfile p = m.getProfile();
        return p == null ? "" : p.name().orElse("");
    }

    /** 마네킹 스킨을 그 플레이어의 스킨으로 (빈 이름이면 기본 스킨 — 스킨 그림은 클라이언트가 이름으로 찾아 옴) */
    public static void setSkin(Mannequin m, String player) {
        String n = player == null ? "" : player.strip();
        ((MannequinAccessor) m).mmorpg$setProfile(n.isEmpty() ? Mannequin.DEFAULT_PROFILE : ResolvableProfile.createUnresolved(n));
    }

    /** 스킨 바꾸기 (베드락 NPC 의 스킨 고르기 대신 — 플레이어 이름) */
    private static void skinFlow(ServerPlayer sp, Entity npc, Runnable then) {
        if (!(npc instanceof Mannequin m)) {
            then.run();
            return;
        }
        new ModalForm("스킨 바꾸기")
                .label("§7플레이어 이름을 넣으면 그 사람의 스킨을 입습니다. 비우면 기본 스킨입니다.")
                .text("skin", "플레이어 이름", "예: Notch", skinName(m))
                .submit("저장")
                .show(sp, v -> {
                    if (v != null) {
                        String n = text(v, "skin").strip();
                        if (!n.isEmpty() && !PLAYER_NAME.matcher(n).matches()) say(sp, "§c플레이어 이름은 영문 · 숫자 · _ 16자까지입니다: " + n);
                        else setSkin(m, n);
                    }
                    then.run();
                });
    }

    /** NPC 삭제 확인 → 지우면 true (이 NPC 가 주는 퀘스트 · 이 NPC 에게 말 걸기 퀘스트도 — 메인 퀘스트는 애드온에 있어 남음) */
    private static void confirmDelete(ServerPlayer sp, Entity npc, String key, int count, Consumer<Boolean> then) {
        // 이 NPC 에게 말 걸기 퀘스트는 NPC 가 없으면 끝낼 수 없으므로 함께 지운다
        List<Quest> talkTo = new ArrayList<>();
        for (Quest q : editable(Quests.allQuests())) if (q.type.equals("talk") && q.target.equals(key) && !q.giver.equals(key)) talkTo.add(q);
        MainQuests.MainNpc main = MainQuests.mainNpcByKey(key);
        List<String> lines = new ArrayList<>();
        lines.add("§c" + NpcData.name(npc) + "§r NPC를 지울까요?");
        if (count > 0) lines.add("이 NPC가 주는 퀘스트 " + count + "개도 함께 지워집니다.");
        if (!talkTo.isEmpty()) lines.add("이 NPC에게 말 걸기 퀘스트 " + talkTo.size() + "개도 함께 지워집니다.");
        if (main != null) lines.add("§6메인 퀘스트 NPC입니다.§r 지운 뒤 /mainnpc " + main.n() + " 으로 다시 세울 수 있습니다 (플레이어 진행은 그대로).");
        new MenuForm("NPC 삭제", String.join("\n", lines)).button("§c지우기", Icon.of(DELETE_ICON)).button("취소", DialogForm.BYE).show(sp, sel -> {
            if (sel != 0) {
                then.accept(false);
                return;
            }
            MinecraftServer server = sp.level().getServer();
            for (Quest q : editable(Quests.questsOf(key))) Quests.deleteQuest(q.id);
            for (Quest q : talkTo) Quests.deleteQuest(q.id);
            NpcData.forget(server, key);
            // 예전 중복 NPC 를 지울 때는 지금 NPC 의 기록을 남김
            NpcData.Spot rec = main != null ? NpcData.mainRecord(server, key) : null;
            if (rec != null && rec.id().equals(npc.getUUID().toString())) NpcData.forgetMainRecord(server, key);
            if (!npc.isRemoved()) {
                npcFx(npc, "minecraft:death_explosion_emitter", -0.6);
                npc.discard();
            }
            say(sp, "NPC를 지웠습니다.");
            then.accept(true);
        });
    }

    // ------------------------------------------------------------ 퀘스트 목록 (관리자)

    /** 퀘스트 목록 반복. giverKey 가 빈 글자면 모든 퀘스트 */
    private static void questListLoop(ServerPlayer sp, String giverKey, int guard, Runnable done) {
        if (guard >= 100) {
            end(done);
            return;
        }
        boolean byNpc = !giverKey.isEmpty();
        List<Quest> list = editable(byNpc ? Quests.questsOf(giverKey) : Quests.allQuests());
        MenuForm form = new MenuForm(byNpc ? "퀘스트 목록" : "모든 퀘스트", list.isEmpty() ? "퀘스트가 없습니다." : "고칠 퀘스트를 고르세요.");
        if (byNpc) form.button("§2+ 퀘스트 추가", DialogForm.NEW);
        for (Quest q : list) {
            Quest.Type t = typeOf(q);
            form.button(q.title + " §8(" + (byNpc ? t.name() : Quests.giverName(q)) + ")", Icon.of(t.icon()));
        }
        form.button("뒤로", DialogForm.BYE);
        form.show(sp, sel -> {
            if (sel < 0) {
                end(done);
                return;
            }
            Runnable again = () -> questListLoop(sp, giverKey, guard + 1, done);
            int i = sel;
            if (byNpc) {
                if (i == 0) {
                    editQuestFlow(sp, null, giverKey, saved -> again.run());
                    return;
                }
                i--;
            }
            if (i >= list.size()) end(done);
            else questActions(sp, list.get(i), again);
        });
    }

    /** 퀘스트 하나: 수정 · 삭제 */
    private static void questActions(ServerPlayer sp, Quest q, Runnable then) {
        String text = String.join("\n", "§l" + q.title + "§r", "§7주는 NPC: §f" + Quests.giverName(q), "§7목표: §f" + Quests.objectiveText(q),
                "§7보상: §f" + Quests.rewardText(q), "§7조건: §f" + Quests.conditionText(q));
        new MenuForm("퀘스트", text)
                .button("수정", Icon.of("textures/items/book_writable"))
                .button("§c삭제", Icon.of(DELETE_ICON))
                .button("뒤로", DialogForm.BYE)
                .show(sp, sel -> {
                    if (sel == 0) editQuestFlow(sp, q, q.giver, saved -> then.run());
                    else if (sel == 1) {
                        new MenuForm("퀘스트 삭제", "§c" + q.title + "§r 퀘스트를 지울까요?\n진행 중인 플레이어의 기록도 사라집니다.")
                                .button("§c지우기", Icon.of(DELETE_ICON))
                                .button("취소", DialogForm.BYE)
                                .show(sp, c -> {
                                    if (c == 0) {
                                        Quests.deleteQuest(q.id);
                                        say(sp, "퀘스트를 지웠습니다: " + q.title);
                                    }
                                    then.run();
                                });
                    } else then.run();
                });
    }

    // ------------------------------------------------------------ 퀘스트 편집

    /** 가방의 아이템 하나 (같은 종류는 수를 합침) — 보이는 이름 · 붙인 이름 (없으면 null) */
    static final class InvItem {
        final String id;
        int amount;
        final String name;
        final String nameTag;

        InvItem(String id, int amount, String name, String nameTag) {
            this.id = id;
            this.amount = amount;
            this.name = name;
            this.nameTag = nameTag;
        }
    }

    /** 가져오기 목표로 고를 아이템 (drop = 몬스터 전리품) */
    record Collectable(String id, String name, boolean drop) {}

    /** 붙인 이름 (애드온 nameTag — 없으면 null) */
    private static String customName(ItemStack s) {
        Component c = s.get(DataComponents.CUSTOM_NAME);
        String t = c == null ? "" : c.getString();
        return t.isEmpty() ? null : t;
    }

    /** 아이템 이름: RPG 아이템 (갑옷 · 재료 · 룬) 은 아이템 이름, 바닐라는 names.js 이름표 (없으면 ID 뒷부분) */
    private static String itemLabel(ItemStack s, String id) {
        if (id.startsWith("rpg:")) {
            String n = BedrockText.plain(s.getItem().getName(s).getString());
            if (!n.isEmpty()) return n;
        }
        return Names.itemName(id, null);
    }

    /** 가방 (핫바 + 36칸) 의 아이템 종류 (NPC 설정 도구는 뺌 — editor.js inventoryItems) */
    static List<InvItem> inventoryItems(ServerPlayer sp) {
        Map<String, InvItem> map = new LinkedHashMap<>();
        for (int i = 0; i < Inv.SIZE; i++) {
            ItemStack s = Inv.get(sp, i);
            if (s.isEmpty() || (GearItems.NPC_TOOL != null && s.is(GearItems.NPC_TOOL))) continue;
            String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
            InvItem prev = map.get(id);
            if (prev != null) {
                prev.amount += s.getCount();
                continue;
            }
            String tag = customName(s);
            Weapon w = Weapons.parseWeapon(id);
            String name = tag != null ? tag : w != null ? NpcData.stripFormat(w.name()) : itemLabel(s, id);
            map.put(id, new InvItem(id, s.getCount(), name, tag));
        }
        return new ArrayList<>(map.values());
    }

    /** 처치 퀘스트에 쓸 수 있는 몹 ID: 자바 엔티티 (베드락 이름도) · RPG 몬스터 (rpg:&lt;키&gt; — 5단계 몸이 등록되기 전에도) */
    static boolean isMobId(String id) {
        String k = Names.mobKey(id);
        if (k.startsWith("rpg:") && MONSTERS.objOrNull(k.substring(4)) != null) return true;
        for (String c : new String[] {id, k}) {
            Identifier rl = Identifier.tryParse(Kit.javaType(c));
            if (rl != null && BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) return true;
        }
        return false;
    }

    /** 선행 퀘스트를 따라가다 자기 자신으로 돌아오는지 */
    static boolean requiresLoop(Quest d) {
        Set<String> seen = new HashSet<>();
        seen.add(d.id.isEmpty() ? "(new)" : d.id);
        String cur = d.requires;
        for (int i = 0; cur != null && !cur.isEmpty() && i < 100; i++) {
            if (!seen.add(cur)) return true;
            Quest q = Quests.getQuest(cur);
            cur = q != null ? q.requires : "";
        }
        return false;
    }

    private static Quest newQuest(String giverKey) {
        Quest q = new Quest();
        q.id = "";
        q.title = "새 퀘스트";
        q.giver = giverKey;
        q.type = "kill";
        q.target = "rpg:scarecrow";
        q.targetName = "";
        q.count = 10;
        q.reward.emerald = 5;
        return q;
    }

    private static int indexOfKey(List<String[]> npcs, String key) {
        for (int i = 0; i < npcs.size(); i++) if (npcs.get(i)[0].equals(key)) return i;
        return -1;
    }

    /**
     * 퀘스트 만들기 · 수정 창. 저장하면 저장된 퀘스트를 then 으로 (닫으면 null). 입력이 틀리면 맨 위에 까닭을 적어 다시 (10번까지).
     * quest = 고칠 퀘스트 (새로 만들면 null — 원본은 바꾸지 않고 복사본을 고쳐 Quests.saveQuest), giverKey = 주는 NPC 번호
     */
    static void editQuestFlow(ServerPlayer sp, Quest quest, String giverKey, Consumer<Quest> then) {
        Quest d = quest != null ? quest.copy() : newQuest(giverKey);
        editStep(sp, quest, d, "", 0, then);
    }

    private static void editStep(ServerPlayer sp, Quest quest, Quest d, String error, int tries, Consumer<Quest> then) {
        if (tries >= 10) {
            then.accept(null);
            return;
        }
        boolean isNew = quest == null;
        List<String[]> npcs = NpcData.list(sp.level().getServer());
        List<String> npcLabels = new ArrayList<>();
        for (String[] n : npcs) npcLabels.add(n[1] + " §8(" + n[0] + ")");
        if (npcLabels.isEmpty()) npcLabels.add("(NPC 없음)");
        List<Quest> others = new ArrayList<>();
        for (Quest q : editable(Quests.allQuests())) if (!q.id.equals(d.id)) others.add(q);
        List<InvItem> items = inventoryItems(sp);
        // v2.7.0: 가져오기 목표는 몬스터 전리품 6종이 먼저, 그다음 내 인벤토리 (전리품은 한 번만)
        List<Collectable> collectables = new ArrayList<>();
        for (String k : MONSTER_ORDER) {
            Obj drop = MONSTER_DROPS.objOrNull(k);
            if (drop != null) collectables.add(new Collectable(drop.str("item"), drop.str("name"), true));
        }
        for (InvItem it : items) if (Loot.dropKeyOf(it.id) == null) collectables.add(new Collectable(it.id, it.name, false));
        int mobIdx = -1;
        for (int i = 0; i < Names.MOBS.size(); i++) {
            if (Names.mobKey(Names.MOBS.get(i).id()).equals(Names.mobKey(d.target))) {
                mobIdx = i;
                break;
            }
        }
        int itemIdx = -1;
        for (int i = 0; i < collectables.size(); i++) {
            if (collectables.get(i).id().equals(d.target)) {
                itemIdx = i;
                break;
            }
        }
        // 구버전 보상은 실제로 줄 레벨로 보여 줌
        Weapon w = d.reward.weapon.isEmpty() ? null : Weapons.currentWeapon(Weapons.parseWeapon("rpg:" + d.reward.weapon));
        String customDefault = (d.type.equals("kill") && mobIdx < 0) || (d.type.equals("collect") && itemIdx < 0) ? d.target : "";
        boolean hadItem = d.reward.item != null;

        ModalForm b = new ModalForm(isNew ? "퀘스트 만들기" : "퀘스트 수정");
        if (!error.isEmpty()) b.label("§c" + error);
        b.header("기본");
        b.text("title", "퀘스트 이름", "예: 허수아비 소탕", d.title);
        b.dropdown("giver", "퀘스트를 주는 NPC", npcLabels, Math.max(0, indexOfKey(npcs, d.giver)));
        List<String> typeNames = new ArrayList<>();
        for (String k : Quests.QUEST_TYPE_KEYS) typeNames.add(Quests.QUEST_TYPES.get(k).name());
        b.dropdown("type", "목표 종류", typeNames, Math.max(0, Quests.QUEST_TYPE_KEYS.indexOf(d.type)));
        List<String> mobLabels = new ArrayList<>();
        for (Names.Named m : Names.MOBS) mobLabels.add(m.name());
        mobLabels.add("직접 입력 (아래 ID 칸)");
        b.dropdown("mob", "[몹 처치] 처치할 몹", mobLabels, d.type.equals("kill") ? (mobIdx >= 0 ? mobIdx : Names.MOBS.size()) : 0);
        List<String> itemLabels = new ArrayList<>();
        itemLabels.add("직접 입력 (아래 ID 칸)");
        for (Collectable it : collectables) itemLabels.add(it.name() + " §8(" + (it.drop() ? "전리품" : Names.shortId(it.id())) + ")");
        b.dropdown("item", "[아이템 가져오기] 가져올 아이템 (몬스터 전리품 · 내 인벤토리)", itemLabels, d.type.equals("collect") ? itemIdx + 1 : 0);
        b.text("custom", "몹·아이템 ID 직접 입력", "예: rpg:gulon, rpg:loot_troll", customDefault);
        b.dropdown("talk", "[NPC에게 말 걸기] 대상 NPC", npcLabels, d.type.equals("talk") ? Math.max(0, indexOfKey(npcs, d.target)) : 0);
        b.slider("count", "개수 (몹·아이템) / 목표 레벨", 1, 100, d.count);
        b.text("targetName", "화면에 보일 대상 이름 (비우면 자동)", "예: 허수아비", d.targetName);
        b.divider();
        b.header("대사 (| 줄바꿈, || 다음 장)");
        b.text("offer", "퀘스트를 줄 때", "예: 밭에 허수아비가 늘었네.|10마리만 처치해 주게.", DialogForm.toEditText(d.offer));
        b.text("progress", "진행 중일 때", "예: 아직 멀었나?", DialogForm.toEditText(d.progress));
        b.text("done", "완료했을 때", "예: 고맙네! 약속한 보상일세.", DialogForm.toEditText(d.done));
        b.divider();
        b.header("보상");
        b.slider("emerald", CURRENCY_NAME, 0, 64, d.reward.emerald);
        b.slider("xp", "경험치 레벨", 0, 30, d.reward.xp);
        b.slider("points", "스탯 포인트", 0, 10, d.reward.points);
        List<String> wtypes = new ArrayList<>();
        wtypes.add("없음");
        for (String t : TYPE_ORDER) wtypes.add(TYPES.obj(t).str("name"));
        b.dropdown("wtype", "무기 보상", wtypes, w != null ? TYPE_ORDER.indexOf(w.type()) + 1 : 0);
        List<String> wlevels = new ArrayList<>();
        for (double l : ALL_LEVELS) wlevels.add(Js.str(l) + "렙");
        b.dropdown("wlevel", "무기 레벨 §7(영웅 30·35·40, 초월 40)", wlevels, w != null ? Math.max(0, ALL_LEVELS.indexOf((double) w.level())) : 0);
        List<String> grades = new ArrayList<>();
        for (Grade g : R.grades()) grades.add(g.name());
        b.dropdown("wgrade", "무기 등급", grades, w != null ? w.gradeIndex() : 0);
        List<String> rewardLabels = new ArrayList<>();
        rewardLabels.add("없음");
        if (hadItem) rewardLabels.add("지금 설정 유지: " + (!d.reward.item.name.isEmpty() ? d.reward.item.name : Names.shortId(d.reward.item.id)));
        for (InvItem it : items) rewardLabels.add(it.name + " §8(" + Names.shortId(it.id) + ")");
        b.dropdown("ritem", "아이템 보상 (내 인벤토리에서 고르기, 이름만 저장되고 마법 부여는 저장 안 됨)", rewardLabels, hadItem ? 1 : 0);
        b.slider("ramount", "아이템 보상 개수", 1, 64, hadItem ? d.reward.item.amount : 1);
        b.divider();
        b.header("조건");
        List<String> reqLabels = new ArrayList<>();
        reqLabels.add("없음");
        int reqIdx = -1;
        for (int i = 0; i < others.size(); i++) {
            Quest q = others.get(i);
            reqLabels.add(q.title + " §8(" + Quests.giverName(q) + ")");
            if (reqIdx < 0 && q.id.equals(d.requires)) reqIdx = i;
        }
        b.dropdown("requires", "먼저 끝내야 하는 퀘스트", reqLabels, reqIdx + 1);
        b.slider("minLevel", "최소 경험치 레벨", 0, 100, d.minLevel);
        b.toggle("repeat", "반복 가능 (몹 처치·아이템 가져오기만, 완료 후 다시 받기)", d.repeat);
        b.submit(isNew ? "퀘스트 만들기" : "저장");
        b.show(sp, v -> {
            if (v == null) {
                then.accept(null);
                return;
            }
            String err = apply(sp, quest, d, v, npcs, others, items, collectables, hadItem);
            if (!err.isEmpty()) {
                editStep(sp, quest, d, err, tries + 1, then);
                return;
            }
            Quest saved = Quests.saveQuest(d);
            say(sp, "퀘스트 " + (isNew ? "만듦" : "저장") + ": §e" + saved.title + " §7(" + Quests.objectiveText(saved) + ")");
            SkillFx.playFor(sp, "random.orb", 1.3, 1);
            then.accept(saved);
        });
    }

    /** 입력 반영 (d 를 고침). 틀린 곳이 있으면 까닭 (없으면 "") */
    private static String apply(ServerPlayer sp, Quest quest, Quest d, Map<String, Object> v, List<String[]> npcs, List<Quest> others, List<InvItem> items,
            List<Collectable> collectables, boolean hadItem) {
        String error = "";
        String title = text(v, "title").strip();
        d.title = title.isEmpty() ? "퀘스트" : title;
        int gi = num(v, "giver");
        if (gi < npcs.size()) d.giver = npcs.get(gi)[0];
        String prevTarget = d.target;
        int ti = num(v, "type");
        d.type = ti < Quests.QUEST_TYPE_KEYS.size() ? Quests.QUEST_TYPE_KEYS.get(ti) : "kill";
        d.count = Math.max(1, num(v, "count"));
        String custom = Names.normalizeId(text(v, "custom"));
        String autoName = "";
        switch (d.type) {
            case "kill" -> {
                int mi = num(v, "mob");
                if (mi < Names.MOBS.size()) {
                    d.target = Names.MOBS.get(mi).id();
                    autoName = Names.MOBS.get(mi).name();
                } else if (custom.isEmpty()) error = "처치할 몹을 고르거나 몹 ID를 입력하세요.";
                else if (!isMobId(custom)) error = "알 수 없는 몹 ID입니다: " + custom;
                else {
                    d.target = custom;
                    autoName = Names.mobName(custom);
                }
            }
            case "collect" -> {
                int ii = num(v, "item");
                Collectable it = ii > 0 && ii - 1 < collectables.size() ? collectables.get(ii - 1) : null;
                if (it != null) {
                    d.target = it.id();
                    autoName = it.name();
                } else if (custom.isEmpty()) error = "가져올 아이템을 고르거나 아이템 ID를 입력하세요.";
                else if (Icon.itemById(custom) == null) error = "알 수 없는 아이템 ID입니다: " + custom;
                else {
                    d.target = custom;
                    autoName = Names.ITEM_NAMES.getOrDefault(custom, Names.shortId(custom));
                }
            }
            case "talk" -> {
                int t = num(v, "talk");
                String[] n = t < npcs.size() ? npcs.get(t) : null;
                if (n == null) error = "말 걸 NPC가 없습니다. NPC를 먼저 하나 더 만드세요.";
                else if (n[0].equals(d.giver)) error = "퀘스트를 주는 NPC와 다른 NPC를 고르세요.";
                else {
                    d.target = n[0];
                    autoName = n[1];
                }
            }
            default -> d.target = "";
        }
        // 대상이 바뀌었는데 이름 칸을 그대로 두었으면 자동 이름으로 바꾼다
        String typed = text(v, "targetName").strip();
        boolean stale = quest != null && typed.equals(quest.targetName) && !d.target.equals(prevTarget);
        d.targetName = !typed.isEmpty() && !stale ? typed : autoName;
        d.offer = text(v, "offer").strip();
        d.progress = text(v, "progress").strip();
        d.done = text(v, "done").strip();
        d.reward.emerald = num(v, "emerald");
        d.reward.xp = num(v, "xp");
        d.reward.points = num(v, "points");
        d.reward.weapon = "";
        int wt = num(v, "wtype");
        if (wt > 0 && wt - 1 < TYPE_ORDER.size()) {
            // 그 등급에 없는 레벨이면 가장 가까운 레벨로 바꾼다 (영웅 30·35·40렙, 초월 40렙, 나머지는 35렙 없음)
            List<Grade> grades = R.grades();
            int wg = num(v, "wgrade");
            Grade g = wg < grades.size() ? grades.get(wg) : null;
            String gid = g != null ? g.id() : "common";
            int wl = num(v, "wlevel");
            double want = wl < ALL_LEVELS.size() ? ALL_LEVELS.get(wl) : 0;
            double lv = Weapons.isValidWeapon(want, gid) ? want : Weapons.nearestGradeLevel(want, gid);
            if (lv != want) say(sp, "§e" + (g != null ? g.name() : gid) + " 등급은 " + Weapons.gradeLevelsText(gid) + " 무기만 있어서 보상을 " + Js.str(lv) + "렙으로 바꿨습니다.");
            d.reward.weapon = TYPE_ORDER.get(wt - 1) + "_l" + Js.str(lv) + "_" + gid;
        }
        int ri = num(v, "ritem");
        int amount = num(v, "ramount");
        if (ri == 0) d.reward.item = null;
        else if (hadItem && ri == 1) {
            Quest.RewardItem keep = d.reward.item.copy();
            keep.amount = amount;
            d.reward.item = keep;
        } else {
            int k = ri - (hadItem ? 2 : 1);
            InvItem it = k >= 0 && k < items.size() ? items.get(k) : null;
            d.reward.item = it != null ? new Quest.RewardItem(it.id, amount, it.name, it.nameTag) : null;
        }
        Weapon rw = d.reward.item != null ? Weapons.parseWeapon(d.reward.item.id) : null;
        if (rw != null && rw.legacy()) {
            Weapon cw = Weapons.currentWeapon(rw);
            Quest.RewardItem it = d.reward.item.copy();
            it.id = cw.id();
            it.name = it.nameTag != null && !it.nameTag.isEmpty() ? it.nameTag : NpcData.stripFormat(cw.name());
            d.reward.item = it;
            say(sp, "§e" + rw.grade().name() + " 등급은 " + Weapons.gradeLevelsText(rw.grade().id()) + " 무기만 있어서 아이템 보상을 " + cw.level() + "렙 무기로 바꿨습니다.");
        }
        int rq = num(v, "requires");
        d.requires = rq > 0 && rq - 1 < others.size() ? others.get(rq - 1).id : "";
        d.minLevel = num(v, "minLevel");
        d.repeat = flag(v, "repeat");
        if (error.isEmpty() && d.giver.isEmpty()) error = "퀘스트를 줄 NPC가 없습니다.";
        if (error.isEmpty() && !d.requires.isEmpty() && requiresLoop(d)) error = "먼저 끝내야 하는 퀘스트가 서로를 가리킵니다. 다른 퀘스트를 고르세요.";
        return error;
    }

    // ------------------------------------------------------------ 예전 /scriptevent 역할 명령 (npc.js applyRole)

    /**
     * 역할 붙이기 (npc.js applyRole — 예전 /scriptevent rpg:npc · rpg:tag): role = quest (대화 NPC, arg = 이름) · shop (arg = 상점 레벨) ·
     * smith · runesmith (arg = 이름) · clear (rpg_ 태그를 모두 떼고 이름표는 이름만). 알림 글자를 돌려줌
     */
    public static String applyRole(Entity npc, String role, String arg) {
        String a = arg == null ? "" : arg.strip();
        boolean named = Props.get(npc, "rpg:npc_name") != null;
        switch (role) {
            case "clear" -> {
                for (String tag : List.copyOf(npc.entityTags())) if (tag.startsWith("rpg_")) npc.removeTag(tag);
                npc.setCustomName(Component.literal(NpcData.name(npc))); // [!] · [상점] · [대장장이] 표시를 뗀 이름만
                return "역할을 지웠습니다.";
            }
            case "smith" -> {
                NpcData.setSmith(npc, true);
                if (!a.isEmpty()) NpcData.setName(npc, a);
                else if (!named) NpcData.setName(npc, "대장장이");
                refresh(npc);
                return "대장장이로 지정했습니다. §7영웅 무기의 레벨 제한을 올려 줍니다.";
            }
            case "runesmith" -> {
                NpcData.setRunesmith(npc, true);
                if (!a.isEmpty()) NpcData.setName(npc, a);
                else if (!named) NpcData.setName(npc, "룬술사");
                refresh(npc);
                return "룬술사로 지정했습니다. §7룬을 갈아 룬 조각으로, 룬 조각으로 뽑기권을 팝니다.";
            }
            case "shop" -> {
                int lv = level(LEVELS, 0);
                try {
                    double n = Double.parseDouble(a);
                    if (LEVELS.contains(n)) lv = (int) n;
                } catch (NumberFormatException e) {
                    // 첫 레벨
                }
                NpcData.setShop(npc, lv);
                if (!named) NpcData.setName(npc, "무기상");
                refresh(npc);
                return lv + "렙 무기 상점으로 지정했습니다.";
            }
            default -> {
                if (!a.isEmpty()) NpcData.setName(npc, a);
                refresh(npc);
                return "대화 NPC로 지정했습니다. §7퀘스트는 /npc 또는 NPC 설정 도구로 추가하세요.";
            }
        }
    }

    /** 내 앞에 그 역할의 NPC 를 세움 (npc.js spawnRoleNpc — 대화 NPC 는 이름이 없으면 촌장) */
    public static String spawnRoleNpc(ServerPlayer sp, String role, String arg) {
        String a = arg == null ? "" : arg;
        Mannequin npc = NpcData.spawnNear(sp);
        npcFx(npc, "minecraft:totem_particle", 0.4);
        return applyRole(npc, role, a.isBlank() && role.equals("quest") ? "촌장" : a);
    }

    /** 6칸 안의 가장 가까운 NPC 에 역할 (npc.js tagNearestNpc) */
    public static String tagNearestNpc(ServerPlayer sp, String role, String arg) {
        Mannequin npc = NpcData.nearest(sp, 6);
        if (npc == null) return "6칸 안에 NPC가 없습니다.";
        return applyRole(npc, role, arg);
    }

    // ------------------------------------------------------------ 명령어 (commands.js /npc — 관리자)

    private static boolean admin(CommandSourceStack s) {
        return s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    /** /npc (NPC 관리 창) · /npc spawn &lt;역할&gt; [이름 · 상점 레벨] · /npc tag &lt;역할&gt; [이름 · 상점 레벨] */
    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("npc").requires(NpcEditor::admin)
                .executes(c -> {
                    openNpcManager(c.getSource().getPlayerOrException());
                    return 1;
                })
                .then(roleCommand("spawn", false))
                .then(roleCommand("tag", true)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> roleCommand(String sub, boolean tag) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(sub);
        List<String> roles = tag ? List.of("quest", "shop", "smith", "runesmith", "clear") : List.of("quest", "shop", "smith", "runesmith");
        for (String role : roles) {
            LiteralArgumentBuilder<CommandSourceStack> r = Commands.literal(role).executes(c -> runRole(c, tag, role, ""));
            if (!role.equals("clear")) {
                r.then(Commands.argument("arg", StringArgumentType.greedyString()).executes(c -> runRole(c, tag, role, StringArgumentType.getString(c, "arg"))));
            }
            node.then(r);
        }
        return node;
    }

    private static int runRole(CommandContext<CommandSourceStack> c, boolean tag, String role, String arg) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        say(sp, tag ? tagNearestNpc(sp, role, arg) : spawnRoleNpc(sp, role, arg));
        return 1;
    }
}
