package mmorpg.fabric.vanilla;

import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgItems;
import mmorpg.fabric.RpgWeaponItem;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.gear.RpgArmorItem;
import mmorpg.fabric.mob.Monsters;
import mmorpg.fabric.npc.Dummy;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * CI 시험 (ci/vanilla_probe.sh 가 서버를 -Dmmorpg.probe=true 로 켤 때만): 모드 없는 클라이언트 "Probe" (바닐라 + 사진만 찍는 작은 모드 src/probe)
 * 가 들어오면 장면을 차례로 만들고 채팅 신호로 사진을 찍게 함 — "[probe] shot 이름" (지금 화면) · "inv 이름" (인벤토리를 열고) · "cam back|first"
 * (3인칭 · 1인칭) · "done" (끝 — 클라이언트가 닫힘). 서버 로그 "[vanilla-probe] …".
 */
final class VanillaProbe {
    private VanillaProbe() {}

    static final String NAME = "Probe";

    static void init() {
        if (!Boolean.getBoolean("mmorpg.probe")) return;
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer p = handler.getPlayer();
            String name = p.getGameProfile().name();
            log("joined " + name + " modded=" + VanillaClients.isModded(p));
            if (NAME.equals(name)) Tasks.later(160, () -> {
                String films = System.getProperty("mmorpg.probe.films", "");
                if (!films.isEmpty() && !films.equals("false")) {
                    filmList(films);
                    film(p, 0);
                } else scene(p, 0);
            });
        });
    }

    /** 4b 시험: 스탯 창 투자 전 값 · 옮긴 룬 · 가방의 룬 수 */
    private static int strBefore, pointBefore, bagRunes, bagSlot;
    private static net.minecraft.world.inventory.AbstractContainerMenu statsMenu;
    private static ItemStack movedRune = ItemStack.EMPTY;
    /** 메뉴 시험: 뺐다 낄 투구 · 아티팩트 수 · 뽑기 전 룬 수 */
    private static ItemStack helmet = ItemStack.EMPTY;
    private static int artBefore, runesBefore;

    private static int countRunes(ServerPlayer p) {
        int n = 0;
        for (int i = 0; i < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE; i++) {
            if (mmorpg.fabric.rune.RuneItems.runeOf(p.getInventory().getItem(i)) != null) n += p.getInventory().getItem(i).getCount();
        }
        return n;
    }

    /** 창 시험의 응답 (상자 · 버튼 창 — 누른 번호, 입력창 — 값) */
    private static int formResult = Integer.MIN_VALUE;
    private static int formId = -1;
    private static java.util.Map<String, Object> modalResult;

    /** 장면에 둔 몬스터 (스킬 시험 전에 치움 — 오래 두면 시험 사람을 침) */
    private static final List<net.minecraft.world.entity.Entity> SPAWNED = new ArrayList<>();

    private static void scene(ServerPlayer p, int step) {
        if (p.isRemoved() || p.hasDisconnected()) {
            log("left at step " + step);
            return;
        }
        ServerLevel level = p.level();
        MinecraftServer server = level.getServer();
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(0, 0, 0));
        int y = top.getY();
        switch (step) {
            case 0 -> {
                run(server, "gamerule advance_time false");
                run(server, "gamerule advance_weather false");
                run(server, "time set noon");
                run(server, "weather clear");
                run(server, "tp " + NAME + " 0.5 " + y + " 0.5 0 15");
                give(p);
                next(p, 1, 20); // 가방 내용은 틱 끝에 감 — 채팅 신호보다 늦음
            }
            case 1 -> {
                say(p, "shot 01_hand");
                next(p, 2, 40);
            }
            case 2 -> {
                say(p, "inv 02_inventory");
                next(p, 3, 80);
            }
            case 3 -> {
                Block altar = BuiltInRegistries.BLOCK.getValue(Mmorpg.rpg("altar_scarecrow_king"));
                if (altar != null) level.setBlockAndUpdate(new BlockPos(-2, y, 5), altar.defaultBlockState());
                SPAWNED.add(Monsters.spawnAt(level, "scarecrow", new Vec3(1.5, y, 6.5), 180F));
                SPAWNED.add(Monsters.spawnAt(level, "draugr", new Vec3(4.5, y, 8.5), 200F));
                SPAWNED.add(Monsters.spawnAt(level, "troll", new Vec3(-3.5, y, 10.5), 160F));
                SPAWNED.add(Monsters.spawnAt(level, "gulon", new Vec3(0.5, y, 12.5), 180F));
                Dummy.spawnDummy(p);
                next(p, 4, 40); // 모델 뼈가 붙고 첫 자세가 갈 때까지
            }
            case 4 -> {
                say(p, "shot 03_world");
                next(p, 5, 40);
            }
            case 5 -> {
                say(p, "cam back");
                next(p, 6, 30);
            }
            case 6 -> {
                say(p, "shot 04_third");
                next(p, 7, 40);
            }
            case 7 -> {
                say(p, "cam first");
                for (net.minecraft.world.entity.Entity e : SPAWNED) if (e != null && !e.isRemoved()) e.discard();
                SPAWNED.clear();
                prepareSkills(p);
                next(p, 8, 30);
            }
            case 8 -> {
                say(p, "key f"); // 일반 → 룬 스킬
                next(p, 9, 30);
            }
            case 9 -> {
                check("rune mode", VanillaInput.mode(p) == VanillaInput.Mode.RUNE);
                check("held slot 9", p.getInventory().getSelectedSlot() == VanillaInput.ANCHOR);
                check("weapon moved to slot 9", p.getInventory().getItem(VanillaInput.ANCHOR).getItem() instanceof RpgWeaponItem);
                say(p, "shot 05_rune_mode");
                next(p, 10, 20);
            }
            case 10 -> {
                say(p, "key 1"); // 룬 스킬 칸 1
                next(p, 11, 12);
            }
            case 11 -> {
                mmorpg.core.Runes.Rune r = mmorpg.fabric.rune.RuneItems.runeOf(mmorpg.fabric.rune.SkillBar.get(p, 0));
                check("rune 1 cast (cooldown)", r != null && mmorpg.fabric.rune.RuneCast.cooldownLeft(p, r) > 0);
                check("held slot still 9", p.getInventory().getSelectedSlot() == VanillaInput.ANCHOR);
                say(p, "shot 06_cast");
                next(p, 12, 30);
            }
            case 12 -> {
                say(p, "key f"); // 룬 → 직업 스킬
                next(p, 13, 30);
            }
            case 13 -> {
                check("job mode", VanillaInput.mode(p) == VanillaInput.Mode.JOB);
                say(p, "shot 07_job_mode");
                next(p, 14, 20);
            }
            case 14 -> {
                say(p, "key f"); // 직업 → 일반
                next(p, 15, 30);
            }
            case 15 -> {
                check("normal mode", VanillaInput.mode(p) == VanillaInput.Mode.NORMAL);
                check("held slot back to 1", p.getInventory().getSelectedSlot() == 0);
                check("weapon back in slot 1", p.getInventory().getItem(0).getItem() instanceof RpgWeaponItem);
                say(p, "shot 08_normal");
                next(p, 160, 40);
            }
            case 160 -> { // 2026-11-08 HUD (VanillaHud): 체력 막대 · 배지 + 알림 줄 (액션바 글자를 가로채서 합침)
                check("hud active (vanilla · survival)", VanillaHud.active(p));
                mmorpg.fabric.Notify.notify(p, "§a시험 알림 §7— 액션바 글자 줄", 80);
                check("notify goes to hud message line", VanillaHud.message(p).contains("시험 알림"));
                // 스킬 칸 HUD (사용자 "스킬 Hud로도"): 룬 1 · 2 재사용 대기 (덮개 · 남은 초) — 룬 재사용과 같은 길 (SkillBarNet.cooldown)
                // 룬 1 · 2 · 3 = 12초 · 6초 · 마지막 1초 (노랑) — 19_hud_msg 사진 (15틱 뒤)
                int[] cds = {240, 120, 32};
                String cdName = null;
                for (int i = 0; i < cds.length; i++) {
                    mmorpg.core.Runes.Rune r = mmorpg.fabric.rune.RuneItems.runeOf(mmorpg.fabric.rune.SkillBar.get(p, i));
                    if (r == null) continue;
                    mmorpg.fabric.rune.SkillBarNet.cooldown(p, r.id(), cds[i], 300);
                    if (cdName == null) cdName = r.id();
                }
                check("skill hud knows rune cooldown", cdName != null && VanillaHud.cooldownLeft(p, cdName) > 0);
                // 남은 초는 칸 아래쪽 가운데 두 글자까지 (CI 42445608056 사진: 가운데 "12" 의 앞 숫자가 키 상자에 가려짐 → 테두리 숫자 그림)
                check("cooldown seconds fit a slot", VanillaHud.secsText(12).equals("12") && VanillaHud.secsText(99).equals("99")
                        && VanillaHud.secsText(100).equals("2m") && VanillaHud.secsText(5000).equals("9m"));
                boolean digits = true;
                for (char c : "0123456789m".toCharArray()) digits &= VanillaHud.hasSkillGlyph("sec_" + c);
                check("cooldown digit glyphs in skill fonts", digits);
                next(p, 161, 15);
            }
            case 161 -> {
                say(p, "shot 19_hud_msg");
                next(p, 162, 6); // 체력은 사진 뒤에 바꿈 (지난 판: 같은 틱에 바꿔서 사진에 낮은 체력이 찍힘)
            }
            case 162 -> {
                hpBefore = p.getHealth();
                p.setHealth(p.getMaxHealth() * 0.18F); // 25% 아래 → 빨간 테 깜박임 · 깎인 부분이 잠깐 밝게
                p.setAbsorptionAmount(p.getMaxHealth() * 0.1F);
                next(p, 163, 4);
            }
            case 163 -> {
                say(p, "shot 19b_hud_low");
                next(p, 164, 30);
            }
            case 164 -> {
                say(p, "shot 19c_hud_low_later");
                next(p, 165, 10);
            }
            case 165 -> {
                p.setAbsorptionAmount(0);
                p.setHealth(hpBefore > 0 ? hpBefore : p.getMaxHealth());
                next(p, 16, 20);
            }
            case 16 -> { // 4단계: 상자 창 → 바닐라 상자 메뉴
                formResult = Integer.MIN_VALUE;
                mmorpg.fabric.ui.ChestForm f = new mmorpg.fabric.ui.ChestForm("§6시험 상자 창", 3);
                f.fillRange(0, 9, mmorpg.fabric.ui.Slot.PANE);
                f.set(10, mmorpg.fabric.ui.Slot.of(p.getInventory().getItem(0).copy(), "§f무기 칸").lore("§7설명 한 줄"));
                f.set(12, mmorpg.fabric.ui.Slot.of(mmorpg.fabric.ui.Icon.sprite("icon_check"), "§a확인"));
                f.set(14, mmorpg.fabric.ui.Slot.of(mmorpg.fabric.ui.Icon.sprite("icon_cross"), "§c취소"));
                f.set(16, mmorpg.fabric.ui.Slot.of(new ItemStack(net.minecraft.world.item.Items.EMERALD), "§a가격 칸").price(1500));
                f.body("§7상자 창 본문 첫 줄\n§7둘째 줄");
                f.show(p, sel -> formResult = sel);
                next(p, 17, 20);
            }
            case 17 -> {
                check("chest form opened as chest menu", p.containerMenu != p.inventoryMenu);
                say(p, "shot 09_chest");
                next(p, 18, 20);
            }
            case 18 -> {
                say(p, "close"); // 상자 메뉴를 닫음 (Esc 와 같은 패킷)
                next(p, 19, 20);
            }
            case 19 -> {
                check("chest closed answers -1", formResult == -1);
                formResult = Integer.MIN_VALUE;
                int id = new mmorpg.fabric.ui.MenuForm("§b시험 버튼 창", "§f본문 글 §7(대화창)").button("첫째").button("둘째").button("셋째")
                        .show(p, sel -> formResult = sel);
                formId = id;
                next(p, 20, 20);
            }
            case 20 -> {
                say(p, "shot 10_menu");
                next(p, 21, 20);
            }
            case 21 -> {
                say(p, "click " + formId + " 1"); // 둘째 단추 (클라이언트가 보내는 것과 같은 패킷)
                next(p, 22, 20);
            }
            case 22 -> {
                check("menu button answers 1", formResult == 1);
                modalResult = null;
                new mmorpg.fabric.ui.ModalForm("§d시험 입력창").label("§7설명 글").text("name", "이름", "입력", "기본값")
                        .dropdown("pick", "고르기", List.of("가", "나", "다"), 1).slider("num", "숫자", 0, 10, 5).toggle("on", "켜기", true)
                        .show(p, map -> modalResult = map);
                formId = mmorpg.fabric.ui.Forms.pendingId(p);
                next(p, 23, 20);
            }
            case 23 -> {
                say(p, "shot 11_modal");
                next(p, 24, 20);
            }
            case 24 -> {
                say(p, "submit " + formId);
                next(p, 25, 20);
            }
            case 25 -> {
                java.util.Map<String, Object> m = modalResult;
                check("modal values " + m, m != null && "abc".equals(m.get("name")) && Integer.valueOf(2).equals(m.get("pick"))
                        && Integer.valueOf(7).equals(m.get("num")) && Boolean.FALSE.equals(m.get("on")));
                next(p, 26, 20);
            }
            case 26 -> { // 4b: 메뉴 (Shift + F — VanillaMenu)
                p.setExperienceLevels(60);
                wearSet(p);
                mmorpg.fabric.RpgPlayers.addPoints(p, 10);
                // 2026-11-08 총 스펙 · 전투력은 든 무기로 셈 → 쓸 수 있는 가장 높은 칼 (+7) 을 손 칸 (핫바 1) 에
                RpgWeaponItem usable = null;
                for (RpgWeaponItem w : RpgItems.WEAPONS) {
                    if (!w.weapon().type().equals("sword") || !mmorpg.fabric.RpgCombat.lackOf(p, w.weapon()).isEmpty()) continue;
                    if (usable == null || w.weapon().level() > usable.weapon().level()
                            || w.weapon().level() == usable.weapon().level() && w.weapon().gradeIndex() > usable.weapon().gradeIndex()) usable = w;
                }
                if (usable != null) {
                    ItemStack sword = new ItemStack(usable);
                    mmorpg.fabric.gear.WeaponEnhance.write(sword, 7);
                    p.getInventory().setItem(p.getInventory().getSelectedSlot(), sword);
                    log("held sword " + usable.weapon().id() + " +7");
                }
                if (mmorpg.fabric.rune.RuneItems.TICKET != null) p.getInventory().setItem(20, new ItemStack(mmorpg.fabric.rune.RuneItems.TICKET, 12));
                Item art = lowestArtifact();
                if (art != null) p.getInventory().setItem(22, new ItemStack(art));
                say(p, "shift on");
                next(p, 27, 10);
            }
            case 27 -> {
                say(p, "key f"); // Shift + F → 메뉴
                next(p, 28, 15);
            }
            case 28 -> {
                say(p, "shift off");
                VanillaMenu.MenuGui g = VanillaMenu.current(p);
                check("shift+F opens menu home", g != null && g.page == VanillaMenu.Page.HOME && p.containerMenu != p.inventoryMenu);
                check("still normal mode", VanillaInput.mode(p) == VanillaInput.Mode.NORMAL);
                statsMenu = p.containerMenu;
                say(p, "shot 12_menu_home");
                next(p, 29, 20);
            }
            case 29 -> {
                say(p, "slot " + VanillaMenu.idx(0, 2)); // 위 탭 = 스탯
                next(p, 30, 15);
            }
            case 30 -> {
                VanillaMenu.MenuGui g = VanillaMenu.current(p);
                check("stats tab (same menu)", g != null && g.page == VanillaMenu.Page.STATS && p.containerMenu == statsMenu);
                strBefore = mmorpg.fabric.RpgData.of(p).str();
                pointBefore = mmorpg.fabric.RpgData.of(p).point();
                say(p, "hover " + VanillaMenu.idx(2, 1)); // 힘 그림 위에 마우스 (설명 상자 모양 rpg:menu)
                next(p, 104, 10);
            }
            case 104 -> {
                // 2026-11-08 힘 설명: 피해 % 대신 공격력 +n (기준 무기 — 총 스펙과 같은 무기), "장비 · 버프" 줄은 그대로
                String tip = tipText(p, 2, 1);
                check("str tooltip shows attack +n, not damage % (" + tip.replace('\n', '|') + ")", tip.contains("공격력 +") && !tip.contains("피해 +"));
                say(p, "shot 13_stats");
                next(p, 105, 10);
            }
            case 105 -> {
                say(p, "hover -1");
                next(p, 31, 10);
            }
            case 31 -> {
                say(p, "slot " + VanillaMenu.idx(2, 1)); // 힘 그림 좌클릭 (+1)
                next(p, 32, 15);
            }
            case 32 -> {
                mmorpg.fabric.RpgData d = mmorpg.fabric.RpgData.of(p);
                check("str +1 (left click)", d.str() == strBefore + 1 && d.point() == pointBefore - 1);
                say(p, "shiftslot " + VanillaMenu.idx(3, 1)); // 힘 기둥 값 Shift + 좌클릭 (+5)
                next(p, 33, 15);
            }
            case 33 -> {
                mmorpg.fabric.RpgData d = mmorpg.fabric.RpgData.of(p);
                check("str +5 (shift left click)", d.str() == strBefore + 6 && d.point() == pointBefore - 6);
                check("menu still open (same menu)", p.containerMenu == statsMenu);
                say(p, "shot 13b_stats_after");
                next(p, 100, 20);
            }
            case 100 -> {
                say(p, "slot " + VanillaMenu.idx(5, 4)); // 가운데 아래 스탯 트리 단추
                next(p, 101, 15);
            }
            case 101 -> {
                VanillaMenu.MenuGui g = VanillaMenu.current(p);
                check("stat tree page from stats button", g != null && g.page == VanillaMenu.Page.TREE && p.containerMenu == statsMenu);
                check("stat tree view scrolled to first fork", g != null && g.treeTop == 2);
                say(p, "shot 13c_tree");
                next(p, 102, 20);
            }
            case 102 -> {
                say(p, "slot " + VanillaMenu.idx(3, VanillaTree.LEFT)); // 3단계 갈래 A (줄 3 — 첫 누름 = 고르기)
                next(p, 103, 15);
            }
            case 103 -> {
                VanillaMenu.MenuGui g = VanillaMenu.current(p);
                check("stat tree node selected (not picked yet)", g != null && g.treeSel == VanillaTree.code(2, 0)
                        && mmorpg.fabric.stattree.StatTrees.state(p).picksOf(g.treeStat).isEmpty());
                say(p, "slot " + VanillaMenu.idx(3, VanillaTree.LEFT)); // 한 번 더 = 갈래를 고름
                next(p, 106, 15);
            }
            case 106 -> {
                String st = VanillaMenu.current(p) == null ? "str" : VanillaMenu.current(p).treeStat;
                check("stat tree fork picked (second click)", !mmorpg.fabric.stattree.StatTrees.state(p).picksOf(st).isEmpty());
                say(p, "shot 13d_tree_pick");
                next(p, 107, 15);
            }
            case 107 -> {
                say(p, "slot " + VanillaMenu.idx(VanillaTree.DOWN[0], VanillaTree.DOWN[1])); // 방향 키 ▼ (아래 판 가운데)
                next(p, 108, 15);
            }
            case 108 -> {
                VanillaMenu.MenuGui g = VanillaMenu.current(p);
                check("stat tree scrolled down (D-pad)", g != null && g.treeTop == 4);
                say(p, "shot 13e_tree_down");
                next(p, 109, 15);
            }
            case 109 -> {
                say(p, "slot " + VanillaMenu.idx(9, 6)); // 합계 탭 (Σ)
                next(p, 110, 15);
            }
            case 110 -> {
                VanillaMenu.MenuGui g = VanillaMenu.current(p);
                check("stat tree sum tab", g != null && VanillaTree.SUM.equals(g.treeStat));
                say(p, "shot 13f_tree_sum");
                next(p, 34, 20);
            }
            case 34 -> {
                say(p, "slot " + VanillaMenu.idx(0, 6)); // 위 탭 = 스킬
                next(p, 35, 15);
            }
            case 35 -> {
                VanillaMenu.MenuGui g = VanillaMenu.current(p);
                check("skills tab", g != null && g.page == VanillaMenu.Page.SKILLS);
                say(p, "shot 14_skills");
                movedRune = mmorpg.fabric.rune.SkillBar.get(p, 0).copy();
                next(p, 36, 20);
            }
            case 36 -> {
                say(p, "slot " + VanillaMenu.idx(2, 0)); // 룬 칸 1 고르기
                next(p, 37, 15);
            }
            case 37 -> {
                say(p, "slot " + VanillaMenu.idx(2, 5)); // 룬 칸 6 (비어 있음) 으로 옮기기
                next(p, 38, 15);
            }
            case 38 -> {
                check("rune moved 1 -> 6", mmorpg.fabric.rune.SkillBar.get(p, 0).isEmpty()
                        && ItemStack.isSameItem(mmorpg.fabric.rune.SkillBar.get(p, 5), movedRune) && !movedRune.isEmpty());
                bagRunes = countRunes(p);
                say(p, "slot " + VanillaMenu.idx(2, 5)); // 룬 칸 6 고르기
                next(p, 39, 15);
            }
            case 39 -> {
                say(p, "slot " + VanillaMenu.idx(2, 8)); // 빼기
                next(p, 40, 15);
            }
            case 40 -> {
                check("rune removed to bag", mmorpg.fabric.rune.SkillBar.get(p, 5).isEmpty() && countRunes(p) == bagRunes + 1);
                bagSlot = -1;
                for (int i = 0; i < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE; i++) {
                    if (ItemStack.isSameItem(p.getInventory().getItem(i), movedRune)) bagSlot = i;
                }
                say(p, "slot " + VanillaMenu.invIndex(Math.max(0, bagSlot))); // 가방의 그 룬 고르기
                next(p, 41, 15);
            }
            case 41 -> {
                say(p, "slot " + VanillaMenu.idx(2, 0)); // 룬 칸 1 에 올리기
                next(p, 42, 15);
            }
            case 42 -> {
                check("bag rune equipped to 1", bagSlot >= 0 && ItemStack.isSameItem(mmorpg.fabric.rune.SkillBar.get(p, 0), movedRune));
                say(p, "shot 14b_skills_after");
                next(p, 43, 20);
            }
            case 43 -> {
                say(p, "slot " + VanillaMenu.idx(0, 4)); // 위 탭 = 장비
                next(p, 44, 15);
            }
            case 44 -> {
                VanillaMenu.MenuGui g = VanillaMenu.current(p);
                check("equip tab", g != null && g.page == VanillaMenu.Page.EQUIP);
                helmet = p.getItemBySlot(EquipmentSlot.HEAD).copy();
                check("wearing helmet", !helmet.isEmpty());
                say(p, "shot 15_equip");
                next(p, 140, 20);
            }
            case 140 -> {
                say(p, "hover " + VanillaMenu.idx(5, 0)); // 왼쪽 아래 정보 단추 = 총 스펙 (2026-11-08)
                next(p, 141, 10);
            }
            case 141 -> {
                mmorpg.fabric.CombatPowers.Sheet c = mmorpg.fabric.CombatPowers.of(p);
                String tip = tipText(p, 5, 0);
                check("total spec tooltip (" + tip.replace('\n', '|') + ")", c.cp() > 0 && tip.contains("총 스펙")
                        && tip.contains("전투력 " + VanillaGui.commas(c.cp())) && tip.contains("공격력 ") && tip.contains("최대 체력 "));
                // 지금 든 무기로 셈 (사용자 "무기를 지금 낀걸로") — 무기가 아닌 것을 들면 공격력 0 (전투력이 내려감), 잠깐 걸리는 룬 버프는 뺌
                mmorpg.core.Weapon held = mmorpg.fabric.RpgCombat.weaponOf(p.getMainHandItem());
                int sel = p.getInventory().getSelectedSlot();
                int bare = -1;
                for (int i = 0; i < 9 && bare < 0; i++) if (mmorpg.fabric.RpgCombat.weaponOf(p.getInventory().getItem(i)) == null) bare = i;
                p.getInventory().setSelectedSlot(Math.max(0, bare));
                long other = mmorpg.fabric.CombatPowers.of(p).cp();
                p.getInventory().setSelectedSlot(sel);
                check("combat power follows held weapon (" + (held == null ? "-" : held.id()) + " " + c.cp() + " / bare " + other + ")",
                        held != null && c.weapon() != null && c.weapon().id().equals(held.id()) && bare >= 0 && (c.usable() ? other < c.cp() : other == c.cp()));
                mmorpg.fabric.RpgStats.setBuff(p, 0.2);
                long buffed = mmorpg.fabric.CombatPowers.of(p).cp();
                mmorpg.fabric.RpgStats.setBuff(p, 0);
                check("combat power ignores rune buff (" + c.cp() + " / " + buffed + ")", buffed == c.cp());
                log("combat power " + c.cp() + " weapon " + (c.weapon() == null ? "-" : c.weapon().id() + " +" + c.enh() + (c.usable() ? "" : " (lack)")) + " spec " + c.spec());
                say(p, "shot 15c_equip_spec");
                next(p, 142, 10);
            }
            case 142 -> {
                say(p, "hover " + VanillaMenu.idx(5, 3)); // 든 무기 칸 — 무기 설명 (2026-11-08 공격력 · 1당 공격력, % 없음)
                next(p, 143, 10);
            }
            case 143 -> {
                say(p, "shot 15d_equip_weapon");
                next(p, 144, 10);
            }
            case 144 -> {
                say(p, "hover -1");
                next(p, 45, 10);
            }
            case 45 -> {
                say(p, "slot " + VanillaMenu.idx(1, 0)); // 투구 칸 → 가방으로
                next(p, 46, 15);
            }
            case 46 -> {
                int at = -1;
                for (int i = 0; i < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE; i++) {
                    if (!helmet.isEmpty() && ItemStack.isSameItem(p.getInventory().getItem(i), helmet)) at = i;
                }
                check("helmet taken off to bag", p.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && at >= 0);
                say(p, "slot " + VanillaMenu.invIndex(Math.max(0, at))); // 가방의 투구 → 다시 끼기
                next(p, 47, 15);
            }
            case 47 -> {
                check("helmet worn again from bag", ItemStack.isSameItem(p.getItemBySlot(EquipmentSlot.HEAD), helmet));
                artBefore = mmorpg.fabric.artifact.ArtifactSystem.worn(p);
                say(p, "slot " + VanillaMenu.invIndex(22)); // 가방의 아티팩트 → 끼기
                next(p, 48, 15);
            }
            case 48 -> {
                check("artifact equipped from bag", mmorpg.fabric.artifact.ArtifactSystem.worn(p) == artBefore + 1);
                say(p, "shot 15b_equip_after");
                next(p, 49, 20);
            }
            case 49 -> {
                // 2026-11-08 CI 42444726662: 둘레의 슬라임이 연출 중에 때려 연출이 끝남 (맞으면 바로 끝 — GachaCinematic) → 남은 장면은 맞지 않게
                for (net.minecraft.world.entity.Mob m : p.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(48),
                        e -> e instanceof net.minecraft.world.entity.monster.Enemy)) m.discard();
                p.getAbilities().invulnerable = true; // 플레이어 무적 (창작 모드와 같은 능력 — 26.3 에는 setInvulnerable 이 없음)
                p.onUpdateAbilities();
                // 한정 뽑기를 엶 (관리자 설정과 같은 것 — 초월 픽업 1 · 영웅 픽업 3) + 한정 룬 뽑기권
                List<String> all = mmorpg.core.Runes.ALL_FAMILIES;
                String err = mmorpg.fabric.gacha.GachaSystem.saveLimited(p.level().getServer(), new java.util.LinkedHashMap<>(java.util.Map.of(
                        "name", "불꽃의 축제", "featured5", List.of(all.get(all.size() - 1)),
                        "featured4", mmorpg.core.Runes.FAMILY_ORDER.subList(0, 3), "days", 14.0)), null);
                check("limited banner opened " + (err == null ? "" : err), err == null);
                if (mmorpg.fabric.rune.RuneItems.LIMITED_TICKET != null) p.getInventory().setItem(23, new ItemStack(mmorpg.fabric.rune.RuneItems.LIMITED_TICKET, 12));
                say(p, "slot " + VanillaMenu.idx(0, 1)); // 위 탭 = 홈
                next(p, 50, 15);
            }
            case 50 -> {
                say(p, "slot " + VanillaMenu.idx(4, 7)); // 뽑기 타일
                next(p, 51, 15);
            }
            case 51 -> {
                VanillaMenu.MenuGui g = VanillaMenu.current(p);
                check("gacha page from home card", g != null && g.page == VanillaMenu.Page.GACHA);
                check("limited banner selected first (top of list)", g != null && mmorpg.fabric.gacha.GachaSystem.LIMITED.equals(g.gachaKey));
                say(p, "shot 16_gacha");
                next(p, 112, 20);
            }
            case 112 -> {
                say(p, "slot " + VanillaMenu.idx(1, VanillaGacha.LIST_COL)); // 목록 둘째 = 일반
                next(p, 113, 15);
            }
            case 113 -> {
                VanillaMenu.MenuGui g = VanillaMenu.current(p);
                check("normal banner from list", g != null && mmorpg.fabric.gacha.GachaSystem.NORMAL.equals(g.gachaKey));
                say(p, "shot 16a_gacha_normal");
                next(p, 114, 15);
            }
            case 114 -> {
                say(p, "slot " + VanillaMenu.idx(0, VanillaGacha.LIST_COL)); // 목록 맨 위 = 한정
                next(p, 52, 15);
            }
            case 52 -> {
                runesBefore = countRunes(p);
                say(p, "slot " + VanillaMenu.idx(4, 1)); // 1회 뽑기 (뽑기권 그림 칸)
                next(p, 53, 15);
            }
            case 53 -> {
                VanillaMenu.MenuGui g = VanillaMenu.current(p);
                check("pull asks first (confirm 1)", g != null && g.gachaConfirm == 1 && countRunes(p) == runesBefore);
                say(p, "shot 16b_gacha_confirm");
                next(p, 115, 15);
            }
            case 115 -> {
                say(p, "slot " + VanillaMenu.idx(5, 2)); // 네
                next(p, 116, 14);
            }
            case 116 -> {
                check("yes pulls 1 rune", countRunes(p) == runesBefore + 1);
                check("cinematic plays (menu closed)", GachaCinematic.active(p) && p.containerMenu == p.inventoryMenu);
                say(p, "shot 16c_cine_intro");
                next(p, 117, 32);
            }
            case 117 -> {
                check("cinematic sealed", "SEALED".equals(GachaCinematic.phase(p)));
                say(p, "shot 16d_cine_sealed");
                next(p, 118, 6);
            }
            case 118 -> {
                say(p, "key f"); // 다음 (좌클릭 · 우클릭 · F 가 같은 일 — 시험은 F 패킷)
                next(p, 119, 16);
            }
            case 119 -> {
                check("left click opens (reveal)", "SUMMARY".equals(GachaCinematic.phase(p)));
                say(p, "shot 16e_cine_reveal");
                next(p, 120, 6);
            }
            case 120 -> {
                say(p, "key f"); // 닫기
                next(p, 121, 24);
            }
            case 121 -> {
                VanillaMenu.MenuGui g = VanillaMenu.current(p);
                check("after cinematic: gacha page again", !GachaCinematic.active(p) && g != null && g.page == VanillaMenu.Page.GACHA);
                runesBefore = countRunes(p);
                say(p, "shot 16f_gacha_after");
                next(p, 54, 15);
            }
            case 54 -> {
                say(p, "slot " + VanillaMenu.idx(4, 6)); // 10회 뽑기 (×10 칸)
                next(p, 122, 15);
            }
            case 122 -> {
                VanillaMenu.MenuGui g = VanillaMenu.current(p);
                check("10 pull asks first", g != null && g.gachaConfirm == mmorpg.core.Gacha.multi());
                say(p, "slot " + VanillaMenu.idx(5, 2)); // 네
                next(p, 123, 52);
            }
            case 123 -> {
                check("gacha 10 pulls give runes", countRunes(p) == runesBefore + mmorpg.core.Gacha.multi());
                check("ten cinematic sealed", "SEALED".equals(GachaCinematic.phase(p)));
                say(p, "shot 16g_cine_ten_sealed");
                next(p, 124, 6);
            }
            case 124 -> {
                say(p, "key f"); // 열기 (차례로 깨짐)
                next(p, 125, 13);
            }
            case 125 -> {
                say(p, "shot 16h_cine_ten_opening");
                next(p, 126, 45);
            }
            case 126 -> {
                say(p, "shot 16i_cine_ten_result");
                shiftTries = 0;
                next(p, 128, 4);
            }
            case 128 -> {
                if (GachaCinematic.active(p) && shiftTries++ < 4) {
                    say(p, "shift on"); // Shift: 건너뛰기 (결과에서는 닫기)
                    next(p, 129, 6);
                } else {
                    VanillaMenu.MenuGui g = VanillaMenu.current(p);
                    check("shift skips/closes, gacha page again", !GachaCinematic.active(p) && g != null && g.page == VanillaMenu.Page.GACHA);
                    say(p, "shot 16j_gacha_after_ten");
                    next(p, 56, 15);
                }
            }
            case 129 -> {
                say(p, "shift off");
                next(p, 128, 20);
            }
            case 56 -> {
                VanillaMenu.MenuGui g = VanillaMenu.current(p);
                var tab = g == null ? null : g.getGuiElement(VanillaMenu.idx(0, 2)); // 다른 쪽이면 스탯 탭 자리
                check("gacha window has no top tabs", g != null && g.page == VanillaMenu.Page.GACHA && (tab == null || tab.getItemStack().isEmpty()));
                say(p, "slot " + VanillaMenu.idx(0, 0)); // 돌아가기
                next(p, 130, 15);
            }
            case 130 -> {
                VanillaMenu.MenuGui g = VanillaMenu.current(p);
                check("back button goes home", g != null && g.page == VanillaMenu.Page.HOME);
                say(p, "slot " + VanillaMenu.idx(0, 8)); // 닫기 단추 (홈)
                next(p, 58, 15);
            }
            case 58 -> {
                check("close button closes menu", VanillaMenu.current(p) == null && p.containerMenu == p.inventoryMenu);
                mmorpg.fabric.gacha.GachaSystem.useTicketItem(p, mmorpg.fabric.rune.RuneItems.TICKET); // 뽑기권 우클릭과 같음
                next(p, 131, 15);
            }
            case 131 -> {
                VanillaMenu.MenuGui g = VanillaMenu.current(p);
                check("ticket use opens gacha window (normal)", g != null && g.page == VanillaMenu.Page.GACHA
                        && mmorpg.fabric.gacha.GachaSystem.NORMAL.equals(g.gachaKey));
                say(p, "shot 16k_gacha_ticket");
                next(p, 132, 15);
            }
            case 132 -> {
                say(p, "close");
                next(p, 150, 15);
            }
            case 150 -> { // 2026-11-08 진짜 상자 메뉴 (창고 · 쓰레기통) 도 메뉴와 같은 남색 · 금테 바탕 (VanillaGui.containerTitle)
                check("storage opens", mmorpg.fabric.storage.StorageWindow.open(p, -1, null, null));
                next(p, 151, 15);
            }
            case 151 -> {
                check("storage menu with navy title", p.containerMenu instanceof mmorpg.fabric.storage.StorageWindow.StorageMenu);
                say(p, "shot 17_storage");
                next(p, 152, 15);
            }
            case 152 -> {
                say(p, "close");
                next(p, 153, 15);
            }
            case 153 -> {
                check("trash opens", mmorpg.fabric.economy.TrashWindow.open(p));
                next(p, 154, 15);
            }
            case 154 -> {
                check("trash menu with navy title", p.containerMenu instanceof mmorpg.fabric.economy.TrashWindow.TrashMenu);
                say(p, "shot 18_trash");
                next(p, 155, 15);
            }
            case 155 -> {
                say(p, "close");
                next(p, 170, 15);
            }
            case 170 -> { // 2026-11-08 꾸미기 (VanillaCosmeticPage · VanillaCosmetics)
                run(server, "tp " + NAME + " 0.5 " + y + " 0.5 0 12");
                mmorpg.fabric.economy.Wallet.add(p, mmorpg.core.Economy.Kind.GOLD, 500);
                RpgWeaponItem sword = null;
                for (RpgWeaponItem w : RpgItems.WEAPONS) {
                    if (!w.weapon().type().equals("sword") || !mmorpg.fabric.RpgCombat.lackOf(p, w.weapon()).isEmpty()) continue;
                    if (sword == null || w.weapon().level() > sword.weapon().level()) sword = w;
                }
                if (sword != null) p.getInventory().setItem(p.getInventory().getSelectedSlot(), new ItemStack(sword));
                say(p, "cam back");
                next(p, 168, 20);
            }
            case 168 -> { // 갑옷 (RPG 힘 세트) 을 입었지만 보이지 않음 (사용자 "갑옷 아예 빼버려 안보이게를 기본으로")
                say(p, "shot 20a_self_no_armor");
                say(p, "cam first");
                VanillaMenu.open(p, VanillaMenu.Page.COSMETIC);
                next(p, 171, 15);
            }
            case 171 -> {
                VanillaMenu.MenuGui g = VanillaMenu.current(p);
                check("cosmetic page opens", g != null && g.page == VanillaMenu.Page.COSMETIC);
                say(p, "hover 18");
                say(p, "shot 20_cosmetic");
                next(p, 172, 15);
            }
            case 172 -> {
                say(p, "slot 18"); // 벚꽃 검: 첫 번째 누름 = 사기 확인
                next(p, 173, 8);
            }
            case 173 -> {
                VanillaMenu.MenuGui g = VanillaMenu.current(p);
                check("first click arms purchase", g != null && "sakura_sword".equals(g.cosArm));
                say(p, "slot 18"); // 두 번째 누름 = 사기 (바로 입음)
                next(p, 174, 10);
            }
            case 174 -> {
                check("bought and wearing sword skin", mmorpg.fabric.cosmetic.Cosmetics.owns(p, "sakura_sword")
                        && "sakura_sword".equals(VanillaCosmetics.lookOf(p).weaponSkin("sword")));
                for (String id : List.of("crown", "angel_wings", "star_aura", "title_hero")) {
                    mmorpg.fabric.cosmetic.Cosmetics.give(p, id);
                    mmorpg.fabric.cosmetic.Cosmetics.equip(p, id);
                }
                say(p, "slot " + (9 + VanillaCosmeticPage.TAB0 + 1)); // 머리 장식 탭 (2026-11-11 탭 다섯 — 가운데 칸 2 ~ 6)
                next(p, 175, 10);
            }
            case 175 -> {
                VanillaMenu.MenuGui g = VanillaMenu.current(p);
                check("cosmetic tab switches", g != null && "head".equals(g.cosTab));
                say(p, "hover 18");
                say(p, "shot 20b_cosmetic_head");
                next(p, 176, 15);
            }
            case 176 -> {
                mmorpg.fabric.cosmetic.Look look = VanillaCosmetics.lookOf(p);
                check("vanilla look cache follows equips", "crown".equals(look.get("head")) && "angel_wings".equals(look.get("back"))
                        && "title_hero".equals(look.get("title")));
                check("crown replaces helmet on head", VanillaCosmetics.visual(look, "helmet", false).kind() == VanillaCosmetics.HEAD_ITEM);
                // 2026-11-08 사용자 "갑옷 아예 빼버려 안보이게를 기본으로 하자 보이게 하지 마"
                check("armor hidden by default", VanillaCosmetics.visual(mmorpg.fabric.cosmetic.Look.EMPTY, "helmet", false).kind() == VanillaCosmetics.HIDE
                        && VanillaCosmetics.visual(mmorpg.fabric.cosmetic.Look.EMPTY, "chest", false).kind() == VanillaCosmetics.HIDE
                        && VanillaCosmetics.visual(look, "chest", false).kind() == VanillaCosmetics.HIDE
                        && VanillaCosmetics.visual(look, "helmet", true).kind() == VanillaCosmetics.HIDE);
                // 2026-11-11 사용자 "없애 갑옷 스킨과 모드 창의 숨기기 없애고"
                check("no armor skin slots", !mmorpg.fabric.cosmetic.CosmeticDefs.SLOTS.contains("chest")
                        && mmorpg.fabric.cosmetic.CosmeticDefs.all().values().stream().allMatch(d -> mmorpg.fabric.cosmetic.CosmeticDefs.SLOTS.contains(d.slot())));
                String info = VanillaCosmetics.holderInfo(p);
                log("holder " + info);
                check("title and wings ride the player", info.startsWith("title") && !info.endsWith("bones 0"));
                check("self motion view off by default", !VanillaRig.selfView(p));
                say(p, "slot 52"); // 2026-11-13 내 모션 보기 단추 (5, 7)
                next(p, 183, 8);
            }
            case 183 -> {
                check("self motion view toggles on", VanillaRig.selfView(p));
                say(p, "slot 51"); // 미리보기 (창을 닫고 마네킹)
                next(p, 177, 14);
            }
            case 177 -> {
                net.minecraft.world.entity.decoration.Mannequin m = VanillaCosmetics.previewOf(p);
                check("preview mannequin stands", m != null && VanillaMenu.current(p) == null);
                check("preview wears the look", m != null && VanillaCosmetics.holderInfo(m).startsWith("title"));
                check("equipment rewritten for viewers", VanillaCosmetics.rewrites > 0 && "crown".equals(VanillaCosmetics.lastHead));
                log("rewrites " + VanillaCosmetics.rewrites + " lastHead " + VanillaCosmetics.lastHead);
                if (m != null) {
                    log("mannequin " + m.getId() + " holder " + VanillaCosmetics.holderIds(m) + " passenger attachment "
                            + m.getDimensions(m.getPose()).attachments().getNullable(net.minecraft.world.entity.EntityAttachment.PASSENGER, 0, 0F)
                            + " player attachment " + p.getDimensions(p.getPose()).attachments().getNullable(net.minecraft.world.entity.EntityAttachment.PASSENGER, 0, 0F));
                }
                say(p, "ents");
                say(p, "shot 21_preview_front");
                next(p, 178, 62);
            }
            case 178 -> {
                say(p, "shot 21b_preview_back");
                next(p, 179, 10);
            }
            case 179 -> {
                say(p, "cam back");
                next(p, 180, 15);
            }
            case 180 -> {
                say(p, "shot 21c_self_back");
                say(p, "cam first");
                next(p, 181, 15);
            }
            case 181 -> {
                say(p, "shot 21d_hand_skin");
                next(p, 182, 10);
            }
            case 182 -> { // 2026-11-13 뼈 몸 (내 모션 보기 — 왕관 · 벚꽃 검 스킨): 3인칭에서 평타 한 번
                say(p, "cam back");
                next(p, 184, 12);
            }
            case 184 -> {
                mmorpg.fabric.motion.MotionSystem.swung(p, mmorpg.fabric.RpgCombat.weaponOf(p.getMainHandItem()));
                next(p, 185, 5);
            }
            case 185 -> {
                check("rig on self with the crown", VanillaRig.active(p) && !VanillaCosmetics.hatFor(p).isEmpty());
                check("wings follow the rig torso", VanillaRig.frames(p) != null);
                say(p, "ents");
                say(p, "shot 22_rig_self");
                next(p, 186, 14);
            }
            case 186 -> {
                say(p, "shot 22b_rig_self_later");
                VanillaRig.selfView(p, false);
                check("self view off removes the rig", !VanillaRig.active(p) && !VanillaRig.selfView(p));
                mmorpg.fabric.cosmetic.Cosmetics.reset(p);
                next(p, 57, 15);
            }
            default -> {
                say(p, "done");
                log("done");
            }
        }
    }

    /** 스킬 시험 준비: 스탯 · 스킬 칸 룬 넷 (계열 앞 넷의 일반 등급) · 직업 (검사) · 체력 60% (하트 비율 확인) */
    private static void prepareSkills(ServerPlayer p) {
        for (String k : new String[]{"str", "dex", "vit", "int"}) mmorpg.fabric.RpgPlayers.setStat(p, k, 120);
        int slot = 0;
        for (String fam : mmorpg.core.Runes.FAMILY_ORDER) {
            if (slot >= 4) break;
            Item rune = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(mmorpg.core.Runes.runeId(fam, "common")));
            if (rune == null || rune == net.minecraft.world.item.Items.AIR) continue;
            mmorpg.fabric.rune.SkillBar.set(p, slot++, new ItemStack(rune));
        }
        mmorpg.fabric.job.JobSystem.setJob(p, "swordsman");
        p.getInventory().setSelectedSlot(0);
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(0));
        p.setHealth(p.getMaxHealth() * 0.6F);
        log("skills: runes " + slot + ", max health " + p.getMaxHealth() + ", health " + p.getHealth());
    }

    /** 뽑기 연출 Shift 시도 수 */
    private static int shiftTries;
    /** HUD 사진 전 체력 */
    private static float hpBefore;

    private static void check(String what, boolean ok) {
        log("check " + (ok ? "ok" : "fail") + ": " + what);
    }

    /** 메뉴 칸 (줄 r · 칸 c) 의 이름 · 설명 글자 (§ 서식 빼고 — 줄마다 \n) */
    private static String tipText(ServerPlayer p, int r, int c) {
        VanillaMenu.MenuGui g = VanillaMenu.current(p);
        var el = g == null ? null : g.getGuiElement(VanillaMenu.idx(r, c));
        if (el == null) return "";
        ItemStack st = el.getItemStack();
        StringBuilder b = new StringBuilder();
        Component name = st.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (name != null) b.append(name.getString()).append('\n');
        net.minecraft.world.item.component.ItemLore lore = st.get(net.minecraft.core.component.DataComponents.LORE);
        if (lore != null) for (Component l : lore.lines()) b.append(l.getString()).append('\n');
        return b.toString().replaceAll("§.", "");
    }

    private static void next(ServerPlayer p, int step, int ticks) {
        Tasks.later(ticks, () -> scene(p, step));
    }

    // ------------------------------------------------------------ 2026-11-11 영상 (VFILM — 3D 효과 엔진 시범)

    /** 시범 (-Dmmorpg.probe.films=true): 도끼 넷 · 불의 정령 · 찍을 장 수 (틱) */
    static final List<String> PILOT_RUNES = List.of("rpg:rune_axe_common", "rpg:rune_axe_noble", "rpg:rune_axe_hero", "rpg:rune_axe_transcend",
            "rpg:rune_spirit_common");
    static final int[] PILOT_FRAMES = {72, 62, 66, 150, 96};
    /** 이번에 찍을 룬 · 장 수 (filmList) */
    static final List<String> FILM_RUNES = new ArrayList<>();
    static final List<Integer> FILM_FRAMES = new ArrayList<>();
    /** 2026-11-12 census: 영상 대신 룬마다 사진 둘 · 파티클 다리 셈 (모든 룬 — 모드 없는 클라이언트가 보는 것) */
    static boolean census;

    /**
     * 찍을 룬 (-Dmmorpg.probe.films=…): true = 시범, census = 모든 룬 (사진 · 셈), 그 밖 = 쉼표 목록 — 계열 (그 계열의 모든 등급) 또는 계열_등급,
     * 2026-11-12 job:&lt;직업 id&gt; (다섯 칸) · job:&lt;스킬 id&gt; (그 칸 — films/vanilla_job_&lt;스킬 id&gt;).
     * 장 수는 등급마다 (영웅 · 초월은 길게 — 모드 영상 RpgFilm.untilQuiet 의 끝과 비슷하게), 직업 스킬은 모드 영상의 끝 틱 (JOB_TIMES)
     */
    static void filmList(String spec) {
        FILM_RUNES.clear();
        FILM_FRAMES.clear();
        if (spec.equals("true")) {
            FILM_RUNES.addAll(PILOT_RUNES);
            for (int f : PILOT_FRAMES) FILM_FRAMES.add(f);
            return;
        }
        census = spec.equals("census");
        List<String> keys = new ArrayList<>();
        if (census) {
            for (String fam : mmorpg.core.Runes.ALL_FAMILIES) {
                for (String g : mmorpg.core.Runes.GRADE_ORDER) keys.add(fam + "_" + g);
            }
        } else {
            for (String k : spec.split(",")) {
                k = k.trim();
                if (k.isEmpty()) continue;
                if (k.startsWith("job:")) {
                    jobList(k.substring(4)); // 2026-11-12 직업 스킬 (job:<직업> = 다섯 칸 · job:<스킬 id> = 하나)
                    continue;
                }
                if (k.startsWith("motion:")) {
                    // 2026-11-13 뼈 몸 모션 (motion:<무기 종류> — 평타 연타 두 바퀴)
                    FILM_RUNES.add(k);
                    FILM_FRAMES.add(MOTION_FRAMES);
                    continue;
                }
                if (mmorpg.core.Runes.ALL_FAMILIES.contains(k)) for (String g : mmorpg.core.Runes.GRADE_ORDER) keys.add(k + "_" + g);
                else keys.add(k);
            }
        }
        for (String k : keys) {
            int cut = k.lastIndexOf('_');
            if (cut < 0) continue;
            String fam = k.substring(0, cut);
            String g = k.substring(cut + 1);
            String id = mmorpg.core.Runes.runeId(fam, g);
            if (mmorpg.core.Runes.parseRune(id) == null) continue;
            FILM_RUNES.add(id);
            FILM_FRAMES.add(switch (g) {
                case "transcend" -> 150;
                case "hero" -> 96;
                case "noble" -> 76;
                default -> 66;
            });
        }
        log("film list " + spec + " → " + FILM_RUNES.size() + " films" + (census ? " (census)" : ""));
    }
    /** 영상 동안 서버 틱 빠르기 (클라이언트가 틱마다 한 장을 찍을 틈 — 클라이언트도 같은 빠르기로 돎) */
    static final int FILM_RATE = 5;

    /**
     * -Dmmorpg.probe.films=true 일 때 (ci/vanilla_probe.sh VFILM): 다른 장면 없이 룬마다 과녁 앞에서 쓰고 모드 영상 (RpgRuneGallery) 과 같은 카메라
     * (옆 뒤 105° · 높이 — 사진기의 cam film, 화면 줄 숨김) 로 틱마다 사진 →
     * films/vanilla_&lt;계열&gt;_&lt;등급&gt; (ci/films.py 가 mp4)
     */
    private static void film(ServerPlayer p, int step) {
        if (p.isRemoved() || p.hasDisconnected()) {
            log("left film at step " + step);
            return;
        }
        ServerLevel level = p.level();
        MinecraftServer server = level.getServer();
        int y = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(0, 0, 0)).getY();
        if (step == 0) {
            run(server, "gamerule advance_time false");
            run(server, "gamerule advance_weather false");
            run(server, "time set noon");
            run(server, "weather clear");
            p.getAbilities().invulnerable = true;
            p.onUpdateAbilities();
            p.setExperienceLevels(40);
            mmorpg.fabric.RpgPlayers.checkLevelUp(p);
            for (String k : new String[]{"str", "dex", "vit", "int"}) mmorpg.fabric.RpgPlayers.setStat(p, k, 150);
            p.getInventory().clearContent();
            // 2026-11-13 뼈 몸 모션 (VanillaRig): 사진기는 프로브 자신의 화면 — 내 모션 보기를 켬 (기본은 내 화면에 뼈 몸을 그리지 않음)
            VanillaRig.selfView(p, true);
            // 2026-11-13 몸풀기: 첫 룬을 영상 전에 한 번 써 둠 — 묶음의 첫 영상만 쓴 직후 4 ~ 6장이 멈춘 채 찍혔음 (CI 43428424571 강철 피부 · 43423572384
            // 수호의 외침 — 이웃 프레임 차이 0, 그 뒤 한꺼번에 따라잡음: 처음 쓰는 길의 클래스 적재 · 모델 첫 표시). 남은 연출은 첫 영상의 filmReset 이 끝냄
            int wait = 40;
            mmorpg.core.Runes.Rune warm = FILM_RUNES.isEmpty() || census ? null : mmorpg.core.Runes.parseRune(FILM_RUNES.get(0));
            if (warm != null) {
                Tasks.later(10, () -> {
                    if (p.isRemoved()) return;
                    mmorpg.fabric.rune.RuneCast.resetCooldowns(p);
                    log("film warm-up " + FILM_RUNES.get(0) + " " + mmorpg.fabric.rune.RuneCast.castRune(p, warm, false));
                });
                wait = 110;
            }
            Tasks.later(wait, () -> film(p, 1));
            return;
        }
        int i = step - 1;
        for (net.minecraft.world.entity.Entity e : SPAWNED) if (e != null && !e.isRemoved()) e.discard();
        SPAWNED.clear();
        if (i >= FILM_RUNES.size()) {
            run(server, "tick rate 20");
            mmorpg.fabric.vfx.PBridge.thirdPerson(p, false);
            if (census) log("census total seen " + mmorpg.fabric.vfx.PBridge.SEEN + " made " + mmorpg.fabric.vfx.PBridge.made + " dust "
                    + mmorpg.fabric.vfx.PBridge.dusts + " dropped " + mmorpg.fabric.vfx.PBridge.dropped + " noGlyph " + mmorpg.fabric.vfx.PBridge.noGlyph
                    + " cards " + mmorpg.fabric.vfx.PBridge.cards + " solo " + mmorpg.fabric.vfx.PBridge.soloReused);
            say(p, "cam off");
            say(p, "done");
            log("done");
            return;
        }
        if (FILM_RUNES.get(i).startsWith("job:")) {
            jobFilm(p, step, FILM_RUNES.get(i), FILM_FRAMES.get(Math.min(i, FILM_FRAMES.size() - 1)), y);
            return;
        }
        if (FILM_RUNES.get(i).startsWith("motion:")) {
            motionFilm(p, step, FILM_RUNES.get(i).substring(7), FILM_FRAMES.get(Math.min(i, FILM_FRAMES.size() - 1)), y);
            return;
        }
        mmorpg.core.Runes.Rune r = mmorpg.core.Runes.parseRune(FILM_RUNES.get(i));
        if (r == null) {
            check("film rune " + FILM_RUNES.get(i), false);
            Tasks.later(2, () -> film(p, step + 1));
            return;
        }
        run(server, "tick rate 20");
        mmorpg.fabric.rune.RuneKit.filmReset(p); // 2026-11-13 앞 룬의 남은 연출 · 버프 (신체 강화의 기운 · 하트) 가 이 영상에 남지 않게
        run(server, "tp " + NAME + " 0.5 " + y + " 0.5 0 12");
        mmorpg.fabric.rune.RuneCast.resetCooldowns(p);
        mmorpg.fabric.RpgHealth.healFull(p);
        // 과녁: 모드 영상 (RpgRuneGallery.TARGETS) 과 같은 여섯 자리 — 앞 무리 넷 · 양옆 둘 (두 영상을 나란히 견주게)
        double[][] spots = {{0.5, 5.5}, {-1.0, 6.5}, {2.0, 6.0}, {0.5, 8.0}, {-1.8, 2.2}, {2.8, 2.2}};
        for (double[] sp : spots) {
            net.minecraft.world.entity.Entity e = Monsters.spawnAt(level, "draugr", new Vec3(sp[0], y, sp[1]), 180F);
            if (e instanceof net.minecraft.world.entity.Mob m) {
                m.setNoAi(true);
                var hp = m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (hp != null) hp.setBaseValue(200_000); // 영상 동안 쓰러지지 않게
                m.setHealth(m.getMaxHealth());
            }
            SPAWNED.add(e);
        }
        String name = "vanilla_" + r.family() + "_" + r.grade();
        int frames = FILM_FRAMES.get(Math.min(i, FILM_FRAMES.size() - 1));
        // 모드 없는 클라이언트는 1 · 3인칭을 서버가 모름 — 영상 카메라는 3인칭 (몸 둘레 그림 · 판을 봄, 1인칭 눈앞 그림은 숨김)
        mmorpg.fabric.vfx.PBridge.thirdPerson(p, true);
        // 카메라: 모드 영상과 같은 자리 (RpgRuneGallery.film — 영웅 · 초월은 넓게), 사진기가 지금 (옮긴 뒤) 몸 자리를 기준으로 삼음
        boolean big = r.grade().equals("hero") || r.grade().equals("transcend");
        say(p, "cam film 105 " + (big ? "7.6 4.2 1.9" : "7.0 3.6 1.1") + " 3.0 0.6");
        if (census) {
            Tasks.later(30, () -> census(p, r, name, step));
            return;
        }
        // 2026-11-13 평타 강화 룬 (피의 칼날 · 검신 강림 …): 칼을 들고 쓴 뒤 평타 넷 (평타마다 붙는 연출 — 검신 강림의 어깨 위 검이 찌름)
        boolean strikeRune = r.family().equals("strike");
        if (strikeRune) {
            run(server, "item replace entity " + NAME + " hotbar.0 with rpg:sword_l40_hero");
            p.getInventory().setSelectedSlot(0);
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(0));
        } else {
            p.getInventory().clearContent();
        }
        Tasks.later(30, () -> {
            run(server, "tick rate " + FILM_RATE);
            say(p, "film " + name + " " + frames);
            Tasks.later(3, () -> {
                boolean ok = mmorpg.fabric.rune.RuneCast.castRune(p, r, false);
                check("film cast " + name, ok);
                log("film " + name + " vfx " + mmorpg.fabric.vfx.Vfx.active() + " fx · props " + mmorpg.fabric.vfx.Vfx.propsMade + " · sprites "
                        + mmorpg.fabric.vfx.Vfx.spritesMade);
            });
            if (strikeRune) {
                for (int a = 0; a < 4; a++) {
                    Tasks.later(34 + a * 16, () -> {
                        if (p.isRemoved()) return;
                        net.minecraft.world.entity.Entity best = null;
                        double bd = 1e9;
                        for (net.minecraft.world.entity.Entity e : SPAWNED) {
                            if (e == null || e.isRemoved()) continue;
                            double d = e.distanceToSqr(p);
                            if (d < bd) {
                                bd = d;
                                best = e;
                            }
                        }
                        if (best != null) p.attack(best);
                    });
                }
            }
            Tasks.later(frames + 24, () -> film(p, step + 1));
        });
    }

    /**
     * 2026-11-12 census: 룬 하나를 쓰고 사진 둘 (쓴 뒤 10 · 26틱 — 영상 카메라) · 파티클 다리 셈을 로그 (이 룬이 보낸 파티클 ID · 글자 표시 · 가루 · 버림 · 그림 없음 ·
     * 카드 · 가장 많이 떠 있던 수) → 다음 룬 (쓴 뒤 70틱)
     */
    private static void census(ServerPlayer p, mmorpg.core.Runes.Rune r, String name, int step) {
        java.util.Map<String, Integer> seen0 = new java.util.TreeMap<>(mmorpg.fabric.vfx.PBridge.SEEN);
        int made0 = mmorpg.fabric.vfx.PBridge.made;
        int dust0 = mmorpg.fabric.vfx.PBridge.dusts;
        int drop0 = mmorpg.fabric.vfx.PBridge.dropped;
        int no0 = mmorpg.fabric.vfx.PBridge.noGlyph;
        int card0 = mmorpg.fabric.vfx.PBridge.cards;
        int solo0 = mmorpg.fabric.vfx.PBridge.soloReused;
        int[] peak = {0};
        boolean ok = mmorpg.fabric.rune.RuneCast.castRune(p, r, false);
        check("census cast " + name, ok);
        for (int t = 1; t <= 66; t++) Tasks.later(t, () -> peak[0] = Math.max(peak[0], mmorpg.fabric.vfx.PBridge.live()));
        Tasks.later(10, () -> say(p, "shot census_" + r.family() + "_" + r.grade() + "_a"));
        Tasks.later(26, () -> say(p, "shot census_" + r.family() + "_" + r.grade() + "_b"));
        Tasks.later(68, () -> {
            StringBuilder b = new StringBuilder();
            for (var e : mmorpg.fabric.vfx.PBridge.SEEN.entrySet()) {
                int d = e.getValue() - seen0.getOrDefault(e.getKey(), 0);
                if (d > 0) b.append(e.getKey().replace("rpg:", "")).append('=').append(d).append(' ');
            }
            log("census " + name + " made " + (mmorpg.fabric.vfx.PBridge.made - made0) + " dust " + (mmorpg.fabric.vfx.PBridge.dusts - dust0) + " dropped "
                    + (mmorpg.fabric.vfx.PBridge.dropped - drop0) + " noGlyph " + (mmorpg.fabric.vfx.PBridge.noGlyph - no0) + " cards "
                    + (mmorpg.fabric.vfx.PBridge.cards - card0) + " solo " + (mmorpg.fabric.vfx.PBridge.soloReused - solo0) + " peak " + peak[0] + " vfx "
                    + mmorpg.fabric.vfx.Vfx.active() + " | " + b.toString().trim());
            Tasks.later(2, () -> film(p, step + 1));
        });
    }

    // ------------------------------------------------------------ 2026-11-12 직업 스킬 영상 (VFILM job:<직업> · job:<스킬 id>)

    /**
     * 직업 스킬 영상의 끝 틱 (모드 영상 RpgJobGallery 의 CASES — 칸 순서. "24a3" = 태세 · 강화라 끝에 평타를 치고 3틱 더): 영상 장 수 = 쓰기 전 3 + 끝 + 8
     * (+ 평타 뒤 + 12)
     */
    private static final java.util.Map<String, String> JOB_TIMES = java.util.Map.ofEntries(
            java.util.Map.entry("swordsman", "30 16 22 24a3 66"),
            java.util.Map.entry("spellblade", "22 20 42 28 72"),
            java.util.Map.entry("warrior", "24 22 54 24a3 80"),
            java.util.Map.entry("mage", "18 18 76 58 84"),
            java.util.Map.entry("archer", "18 30a4 52 40a4 64"),
            java.util.Map.entry("assassin", "20 50 50 24a4 58"),
            java.util.Map.entry("lancer", "20 22 24 30a3 66"),
            java.util.Map.entry("reaper", "22 34 16 24a3 90"),
            java.util.Map.entry("paladin", "20 18 30 24a3 90"),
            java.util.Map.entry("priest", "14 16 76 24 60"),
            java.util.Map.entry("guardian", "14 16 30 24a3 180"),
            java.util.Map.entry("swordmaster", "28 26 36 26a3 52"),
            java.util.Map.entry("bloodknight", "42 18 46 26a3 52"),
            java.util.Map.entry("warlord", "46 56 28 36 50"),
            java.util.Map.entry("frostraider", "34 26 42 26a3 66"),
            java.util.Map.entry("arcaneblade", "28 22 32 30 48"),
            java.util.Map.entry("elementalblade", "30 22 50 20a4 86"),
            java.util.Map.entry("arcmage", "28 32 82 80 80"),
            java.util.Map.entry("necromancer", "36 80 56 124 90"),
            java.util.Map.entry("sniper", "34 30 24 30a4 52"),
            java.util.Map.entry("battlearcher", "20 30 18 30a4 84"),
            java.util.Map.entry("trickster", "28 26 46 24a8 76"),
            java.util.Map.entry("pierrot", "58 22 34 70 100"),
            java.util.Map.entry("stormlancer", "36 28 48 34 86"),
            java.util.Map.entry("dragoon", "40 48 46 42 82"), // 2026-11-13 변신: 사람으로 돌아오는 것까지 (끝 + DragonForm.LINGER)
            java.util.Map.entry("soulreaper", "30 30 62 26a3 66"),
            java.util.Map.entry("executioner", "30 24 34 26a3 80"),
            java.util.Map.entry("thunderlord", "28 24 40 24a3 66"),
            java.util.Map.entry("tyrant", "22 24 54 24a3 62"),
            java.util.Map.entry("saint", "24 22 60 24a3 104"),
            java.util.Map.entry("warlock", "26 54 54 24a3 90"),
            java.util.Map.entry("phoenix", "22 32 46 28a3 64"),
            java.util.Map.entry("darkknight", "30 26 30 22a3 70"));
    /** 다시 쓰는 스킬 (RpgJobGallery.AGAIN — JobRecast): 쓴 뒤 이 틱들에 같은 칸을 다시 누름 */
    private static final java.util.Map<String, int[]> JOB_AGAIN = java.util.Map.ofEntries(java.util.Map.entry("bloodknight_cleave", new int[] {12, 24}),
            java.util.Map.entry("necromancer_call", new int[] {16}), java.util.Map.entry("sniper_ult", new int[] {22}),
            java.util.Map.entry("trickster_swap", new int[] {12}), java.util.Map.entry("trickster_dash", new int[] {16}),
            java.util.Map.entry("stormlancer_drive", new int[] {12}), java.util.Map.entry("stormlancer_rend", new int[] {20}),
            java.util.Map.entry("dragoon_jump", new int[] {16}), java.util.Map.entry("executioner_ult", new int[] {36}),
            java.util.Map.entry("darkknight_crunch", new int[] {12}), java.util.Map.entry("executioner_headhunt", new int[] {14}),
            java.util.Map.entry("frostraider_throw", new int[] {14}), java.util.Map.entry("elementalblade_flare", new int[] {12}));
    /** 과녁 자리 (RpgJobGallery.TARGETS — 앞 과녁 · 무리 · 양옆): 집 (0.5, 0.5) 에서 남쪽 (+z) 을 봄 */
    private static final double[][] JOB_TARGETS = {{0.5, 3.2}, {-1.2, 4.5}, {2.2, 4.5}, {0.5, 6.0}, {-3.0, 2.5}, {4.0, 2.5}};

    /** what = 직업 id (다섯 칸) 또는 스킬 id (그 칸 하나) */
    private static void jobList(String what) {
        mmorpg.core.Jobs.Job j = mmorpg.core.Jobs.job(what);
        if (j != null) {
            for (int s = 0; s < mmorpg.core.Jobs.SLOTS; s++) {
                mmorpg.core.Jobs.Skill k = j.skill(s);
                if (k != null && !k.id().isEmpty()) addJob(j, s);
            }
            return;
        }
        List<mmorpg.core.Jobs.Job> all = new ArrayList<>(mmorpg.core.Jobs.all());
        all.addAll(mmorpg.core.Jobs.allAdvanced());
        for (mmorpg.core.Jobs.Job x : all) {
            mmorpg.core.Jobs.Skill k = x.skill(what);
            if (k != null) {
                addJob(x, k.slot());
                return;
            }
        }
        log("film list: no job or job skill '" + what + "'");
    }

    private static void addJob(mmorpg.core.Jobs.Job j, int slot) {
        int[] t = jobTime(j.id(), slot);
        FILM_RUNES.add("job:" + j.id() + ":" + slot);
        FILM_FRAMES.add(3 + t[0] + 8 + (t[1] > 0 ? t[2] + 12 : 0));
    }

    /** {끝 틱, 평타를 치는지 (1), 평타 뒤 틱} — 표에 없으면 60틱 */
    private static int[] jobTime(String job, int slot) {
        String row = JOB_TIMES.get(job);
        String[] a = row == null ? new String[0] : row.split(" ");
        if (slot < 0 || slot >= a.length) return new int[] {60, 0, 3};
        String c = a[slot];
        int cut = c.indexOf('a');
        if (cut < 0) return new int[] {Integer.parseInt(c), 0, 3};
        return new int[] {Integer.parseInt(c.substring(0, cut)), 1, Integer.parseInt(c.substring(cut + 1))};
    }

    /** 영상 카메라 (RpgJobGallery.filmView — 거리 · 높이 · 보는 높이 · 따라가는 몫, 그 밖은 RpgFilm 처음 값) */
    private static double[] jobView(String id) {
        return switch (id) {
            case "swordmaster_ult" -> new double[] {11.5, 3.0, 5.6, 0.4};
            case "swordmaster_echo" -> new double[] {8.5, 3.2, 1.2, 0.3};
            case "archer_ult" -> new double[] {11.0, 3.4, 2.8, 0.4};
            case "archer_focus" -> new double[] {7.5, 2.8, 1.2, 0.6};
            case "sniper_ult" -> new double[] {9.0, 3.4, 3.0, 0.5};
            case "trickster_twin" -> new double[] {7.0, 2.8, 1.4, 0.35};
            case "trickster_waltz" -> new double[] {7.5, 2.8, 1.2, 0.6};
            case "trickster_ult" -> new double[] {10.0, 3.8, 1.6, 0.35};
            case "arcmage_ult" -> new double[] {12.0, 3.6, 5.0, 0.4};
            case "arcmage_orbit" -> new double[] {7.0, 2.8, 1.6, 0.6};
            // 2026-11-13 3D 빙하 도끼 (길이 약 11칸 — 하늘에서 돌 때 자루 끝이 13칸 가까이) · 3D 빙하: 멀리 · 위를 봄
            case "frostraider_ult" -> new double[] {13.5, 3.8, 5.6, 0.4};
            case "frostraider_jotunn" -> new double[] {8.5, 3.0, 2.4, 0.5};
            case "frostraider_shatter" -> new double[] {8.0, 3.0, 1.6, 0.6};
            case "frostraider_leap" -> new double[] {8.5, 3.0, 1.4, 0.6};
            case "warrior_whirl" -> new double[] {7.5, 3.0, 1.2, 0.8};
            case "frostraider_throw" -> new double[] {8.0, 3.0, 2.2, 0.6};
            case "executioner_ult" -> new double[] {12.0, 3.6, 4.4, 0.35};
            case "executioner_raven" -> new double[] {8.0, 3.0, 1.4, 0.4};
            case "executioner_chains" -> new double[] {8.5, 3.0, 1.6, 0.5};
            case "executioner_knell" -> new double[] {7.0, 2.8, 2.0, 0.6};
            case "soulreaper_scythe" -> new double[] {8.0, 3.0, 1.8, 0.6};
            case "warlock_ult" -> new double[] {12.0, 3.4, 4.2, 0.4};
            // 2026-11-13 3D 검: 왕의 보물고 (몸 뒤 하늘의 금빛 문 아치 — 좌우 6.5칸 · 높이 7칸 · 과녁 위 8.8칸 대검) · 아스트랄 클레이모어 (6.9칸 거검 — 3칸 떠오름) ·
            // 월드 클리버 (머리 위 8.8칸 대검)
            case "elementalblade_ult" -> new double[] {13.5, 3.8, 4.4, 0.4};
            case "spellblade_ult" -> new double[] {11.5, 3.6, 4.0, 0.5};
            case "arcaneblade_ult" -> new double[] {12.5, 3.6, 4.6, 0.4};
            case "elementalblade_surge" -> new double[] {8.5, 3.0, 1.4, 0.5};
            case "phoenix_ult" -> new double[] {12.5, 3.0, 7.0, 0.4};
            case "darkknight_ult" -> new double[] {9.5, 3.0, 3.4, 0.5};
            case "phoenix_inferno" -> new double[] {8.0, 3.0, 1.4, 0.6};
            case "phoenix_wing", "darkknight_lunge" -> new double[] {7.0, 2.6, 1.3, 0.7};
            // 2026-11-12 드라군의 3D 용 (곁 · 오른쪽 뒤 · 하늘): 조금 멀리 · 드래곤폴은 하늘에 탄 모습과 땅이 다 보이게 올려다봄 (영상 1: 하늘에 있는 동안 화면 밖)
            case "dragoon_jump" -> new double[] {7.5, 2.4, 2.2, 0.9};
            case "dragoon_impale" -> new double[] {7.5, 2.6, 2.0, 1.0};
            case "dragoon_breath" -> new double[] {7.5, 2.4, 1.6, 1.0};
            case "dragoon_ult" -> new double[] {13.0, 4.0, 6.0, 0.5};
            // 2026-11-13 드라군 변신 (사람 대신 용 — 꼬리가 둘레 3칸을 휩쓺): 조금 멀리
            case "dragoon_tail" -> new double[] {7.5, 2.6, 1.4, 1.0};
            // 2026-11-13 워로드 3D 곰 (일어서면 4~5칸): 멀리 · 위를 봄 (CI 43442895567 카타클리즘 — 일어선 곰의 머리가 화면 위로 잘림)
            case "warlord_ult" -> new double[] {11.5, 3.4, 3.4, 0.4};
            case "warlord_hug" -> new double[] {8.5, 3.0, 2.2, 0.5};
            case "warlord_stomp" -> new double[] {8.5, 3.2, 2.2, 0.5};
            case "warlord_slumber" -> new double[] {9.5, 3.2, 2.6, 0.5};
            // 2026-11-13 3D 망령 사신 (키 3칸 남짓 — 몸 뒤 오른쪽) · 페일 하베스트의 거대한 사신 (2.4배): 멀리 · 위를 봄 (CI 43445225021 — 머리가 화면 위로 잘림)
            case "reaper_avatar" -> new double[] {8.5, 3.0, 2.0, 0.5};
            case "soulreaper_ult" -> new double[] {12.5, 3.6, 4.4, 0.4};
            default -> new double[] {6.0, 2.1, 1.0, 1.0};
        };
    }

    /**
     * 직업 스킬 하나 (모드 영상 RpgJobGallery.filmSkills 와 같은 차림): 집으로 · 직업 다시 (앞 칸의 태세 · 자원 지움) · 직업 무기 (40렙 영웅 — 1번 칸) · 과녁 여섯
     * (드라우그 — 룬 영상과 같음, 쓰러지지 않게) → 30틱 뒤 영상 시작 · 게이지 차림 (RpgJobGallery.prepare 중 서버에서 되는 것 — 아군 · 무덤 연출은 빼고) →
     * 3틱 뒤 JobSkills.press (모드 · 바닐라 클라이언트와 같은 길) · 다시 쓰기 · 태세면 끝에 평타
     */
    private static void jobFilm(ServerPlayer p, int step, String entry, int frames, int y) {
        ServerLevel level = p.level();
        MinecraftServer server = level.getServer();
        String[] a = entry.split(":");
        mmorpg.core.Jobs.Job j = a.length < 3 ? null : mmorpg.core.Jobs.job(a[1]);
        int slot = a.length < 3 ? -1 : Integer.parseInt(a[2]);
        mmorpg.core.Jobs.Skill k = j == null ? null : j.skill(slot);
        if (k == null) {
            check("film job " + entry, false);
            Tasks.later(2, () -> film(p, step + 1));
            return;
        }
        run(server, "tick rate 20");
        run(server, "tp " + NAME + " 0.5 " + y + " 0.5 0 10");
        String type = j.weapons().isEmpty() ? "sword" : j.weapons().get(0);
        run(server, "item replace entity " + NAME + " hotbar.0 with rpg:" + type + "_l40_hero");
        p.getInventory().setSelectedSlot(0);
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(0));
        mmorpg.fabric.job.JobSystem.setJob(p, j.id());
        mmorpg.fabric.job.JobSkills.resetCooldowns(p);
        mmorpg.fabric.rune.RuneCast.resetCooldowns(p);
        mmorpg.fabric.RpgHealth.healFull(p);
        List<net.minecraft.world.entity.LivingEntity> foes = new ArrayList<>();
        for (double[] t : JOB_TARGETS) {
            net.minecraft.world.entity.Entity e = Monsters.spawnAt(level, "draugr", new Vec3(t[0], y, t[1]), 180F);
            if (e instanceof net.minecraft.world.entity.Mob m) {
                m.setNoAi(true);
                var hp = m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (hp != null) hp.setBaseValue(200_000);
                m.setHealth(m.getMaxHealth());
                foes.add(m);
            }
            SPAWNED.add(e);
        }
        String id = k.id();
        String name = "vanilla_job_" + id;
        int[] time = jobTime(j.id(), slot);
        mmorpg.fabric.vfx.PBridge.thirdPerson(p, true);
        if (!VanillaRig.selfView(p)) VanillaRig.selfView(p, true);
        double[] cam = jobView(id);
        // 모드 직업 영상 (RpgFilm) 과 같은 카메라: 보는 쪽에서 왼쪽 뒤 125° · 몸 앞 2.5칸을 봄
        say(p, String.format(java.util.Locale.ROOT, "cam film 125 %.2f %.2f %.2f 2.5 %.2f", cam[0], cam[1], cam[2], cam[3]));
        Tasks.later(30, () -> {
            run(server, "tick rate " + FILM_RATE);
            jobPrepare(p, j, id, foes, y);
            say(p, "film " + name + " " + frames);
            Tasks.later(3, () -> {
                if (id.equals("assassin_shade")) run(server, "tp " + NAME + " ~ ~ ~ ~180 ~"); // 과녁을 등지고 씀 (모드 영상 TURN_AFTER)
                jobBeforeCast(p, id, foes);
                boolean ok = mmorpg.fabric.job.JobSkills.press(p, slot);
                check("film cast " + name, ok);
                log("film " + name + " vfx " + mmorpg.fabric.vfx.Vfx.active() + " fx · props " + mmorpg.fabric.vfx.Vfx.propsMade + " · sprites "
                        + mmorpg.fabric.vfx.Vfx.spritesMade);
                for (int again : JOB_AGAIN.getOrDefault(id, new int[0])) {
                    Tasks.later(again, () -> log("film " + name + " again " + mmorpg.fabric.job.JobSkills.press(p, slot)));
                }
                if (time[1] > 0) Tasks.later(time[0], () -> jobBasic(p, j, foes));
            });
            Tasks.later(frames + 24, () -> film(p, step + 1));
        });
    }

    /** 2026-11-13 뼈 몸 모션 영상의 장 수 (평타 연타 두 바퀴 + 뼈 몸이 남아 있는 모습) */
    static final int MOTION_FRAMES = 112;

    /**
     * 2026-11-13 뼈 몸 모션 영상 (VFILM motion:&lt;무기 종류&gt; — films/vanilla_motion_&lt;종류&gt;): 그 무기 (40렙 영웅) 를 들고 과녁 앞에서 평타 연타 (12틱마다 —
     * 칼은 가로 · 세로 · 회전, 단계가 하나 · 둘이면 셋까지 되풀이) 두 바퀴 (사이 20틱 쉼). 내 모션 보기를 켠 3인칭 화면 (직업 영상과 같은 카메라).
     * 첫 평타 뒤: 뼈 몸이 떴는지 (check) · 클라이언트의 둘레 엔티티 (뼈가 사람에게 탔는지 — ents)
     */
    private static void motionFilm(ServerPlayer p, int step, String type, int frames, int y) {
        ServerLevel level = p.level();
        MinecraftServer server = level.getServer();
        run(server, "tick rate 20");
        run(server, "tp " + NAME + " 0.5 " + y + " 0.5 0 10");
        run(server, "item replace entity " + NAME + " hotbar.0 with rpg:" + type + "_l40_hero");
        p.getInventory().setSelectedSlot(0);
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket(0));
        mmorpg.fabric.RpgHealth.healFull(p);
        List<net.minecraft.world.entity.LivingEntity> foes = new ArrayList<>();
        for (double[] t : JOB_TARGETS) {
            net.minecraft.world.entity.Entity e = Monsters.spawnAt(level, "draugr", new Vec3(t[0], y, t[1]), 180F);
            if (e instanceof net.minecraft.world.entity.Mob m) {
                m.setNoAi(true);
                var hp = m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                if (hp != null) hp.setBaseValue(200_000);
                m.setHealth(m.getMaxHealth());
                foes.add(m);
            }
            SPAWNED.add(e);
        }
        mmorpg.fabric.vfx.PBridge.thirdPerson(p, true);
        if (!VanillaRig.selfView(p)) VanillaRig.selfView(p, true);
        String name = "vanilla_motion_" + type;
        int per = Math.max(3, mmorpg.core.Motions.combo(type).size());
        say(p, "cam film 125 6.00 2.10 1.00 2.5 1.00");
        Tasks.later(30, () -> {
            run(server, "tick rate " + FILM_RATE);
            say(p, "film " + name + " " + frames);
            for (int r = 0; r < 2; r++) {
                for (int k = 0; k < per; k++) {
                    int at = 3 + r * (per * 12 + 20) + k * 12;
                    Tasks.later(at, () -> {
                        if (p.isRemoved()) return;
                        var e = nearest(p.position(), foes);
                        if (e != null) p.attack(e);
                    });
                }
            }
            Tasks.later(9, () -> {
                check("rig shown " + type + " (" + VanillaRig.lastMotion + ")", VanillaRig.active(p));
                say(p, "ents");
            });
            Tasks.later(frames + 24, () -> film(p, step + 1));
        });
    }

    /** 게이지 차림 (RpgJobGallery.prepare 의 서버 쪽 — 과녁은 드라우그, 아군 · 시험 허스크만 쓰는 것은 뺌) */
    private static void jobPrepare(ServerPlayer sp, mmorpg.core.Jobs.Job j, String id, List<net.minecraft.world.entity.LivingEntity> foes, int y) {
        String job = j.id();
        net.minecraft.world.entity.LivingEntity front = foes.isEmpty() ? null : foes.get(0);
        if (id.equals("swordsman_flurry")) mmorpg.fabric.job.JobTraits.setStacks(sp, 3);
        if (id.equals("warrior_crush")) mmorpg.fabric.job.JobTraitsMore.setRage(sp, 60);
        if (id.equals("archer_rapid")) mmorpg.fabric.job.JobTraitsMore.setBreath(sp, 3);
        mmorpg.fabric.job.JobTraitsMore.setChain(sp, 0);
        if (id.equals("assassin_bloom") || id.equals("assassin_ult")) for (var e : byFar(sp, foes)) mmorpg.fabric.job.JobTraits3.setMarks(sp, e, 5);
        if (id.equals("reaper_burst")) mmorpg.fabric.job.JobTraits3.setSouls(sp, 10);
        if (id.equals("paladin_smite") || id.equals("thunderlord_arc") || id.equals("thunderlord_spire")) {
            for (var e : byFar(sp, foes)) mmorpg.fabric.job.JobTraits4.setJudges(sp, e, 3);
        }
        if (id.equals("priest_hymn") || id.equals("priest_ult") || id.equals("saint_wing") || id.equals("saint_ult")) mmorpg.fabric.job.JobTraits4.setFaith(sp, 3);
        if (id.equals("guardian_bash")) mmorpg.fabric.job.JobTraits4.setCounter(sp, 30);
        if (id.equals("swordmaster_draw")) mmorpg.fabric.job.JobTraits.setStacks(sp, 3);
        if (id.equals("swordmaster_echo")) mmorpg.fabric.job.JobTraits.setStacks(sp, 8);
        if (id.startsWith("bloodknight_")) sp.setHealth(sp.getMaxHealth() * 0.6F);
        if (id.startsWith("warlord_")) mmorpg.fabric.job.JobTraitsMore.setRage(sp, 60);
        if (id.startsWith("frostraider_") && front != null) mmorpg.fabric.job.JobTraits16.setFrost(sp, front, 3);
        if (job.equals("elementalblade")) {
            mmorpg.fabric.job.ElementSystem.testClear();
            mmorpg.fabric.job.JobTraits17.testBlade(sp, null);
            String pre = switch (id) {
                case "elementalblade_flare", "elementalblade_bolt" -> "water";
                case "elementalblade_surge" -> "ice";
                case "elementalblade_tide" -> "fire";
                default -> null;
            };
            double power = mmorpg.fabric.job.JobSystem.power(sp, j);
            for (var e : foes) {
                e.addTag(mmorpg.fabric.job.ElementSystem.TEST_ELEMENTS);
                if (pre != null) mmorpg.fabric.job.ElementSystem.apply(sp, e, pre, power, true, false);
            }
        }
        if (id.equals("arcmage_vortex")) {
            mmorpg.fabric.job.JobTraitsMore.setChain(sp, 4);
            mmorpg.fabric.job.JobTraits8.testStars(sp, 4);
        }
        if (job.equals("necromancer")) {
            mmorpg.fabric.job.JobTraits8.clearGraves();
            mmorpg.fabric.job.NecroCrew.testClear(sp);
        }
        if (id.equals("necromancer_call")) {
            for (double[] g : new double[][] {{-2.5, 1.0}, {3.5, 1.0}, {0.5, -2.5}}) mmorpg.fabric.job.JobTraits8.testGrave(sp.level(), new Vec3(g[0], y, g[1]), 200);
        }
        if (id.equals("necromancer_mist")) {
            mmorpg.fabric.job.NecroCrew.testRaise(sp, new Vec3(-1.5, y, -1.0));
            mmorpg.fabric.job.NecroCrew.testRaise(sp, new Vec3(2.5, y, -1.0));
        }
        if (id.startsWith("sniper_")) mmorpg.fabric.job.JobTraitsMore.setBreath(sp, 5);
        if (id.equals("trickster_swap")) for (var e : byFar(sp, foes)) mmorpg.fabric.job.JobTraits3.setMarks(sp, e, 6);
        if (id.equals("trickster_ult")) for (var e : byFar(sp, foes)) mmorpg.fabric.job.JobTraits3.setMarks(sp, e, 3);
        if (job.equals("stormlancer")) mmorpg.fabric.job.JobTraits11.setWind(sp, 3);
        if (job.equals("soulreaper")) mmorpg.fabric.job.JobTraits3.setSouls(sp, id.equals("soulreaper_crescent") ? 14 : 6);
        if (job.equals("tyrant")) mmorpg.fabric.job.JobTraits13.setPower(sp, 4);
        // 엑시큐셔너: 체인 오브 저지먼트 · 길로틴 오브 헤븐은 앞 과녁이 체력 10% (처형 — 큰 숫자 · 해골 · 길로틴 오브 헤븐 다시 쓰기)
        if ((id.equals("executioner_chains") || id.equals("executioner_ult")) && front != null) front.setHealth(front.getMaxHealth() * 0.1F);
        if (job.equals("phoenix") || job.equals("darkknight")) {
            mmorpg.fabric.job.JobTraits15.testClear(sp);
            mmorpg.fabric.RpgHealth.clearShield(sp);
            if (id.equals("phoenix_wing") || id.equals("darkknight_lunge")) {
                // 앞으로 움직이는 스킬: 앞 과녁 · 오른쪽 과녁을 비킴 (모드 영상과 같음)
                moveFoe(sp, foes, new Vec3(0.5, y, 3.2), new Vec3(-1.6, y, 2.0));
                moveFoe(sp, foes, new Vec3(4.0, y, 2.5), new Vec3(-4.2, y, 5.4));
            }
            if (job.equals("phoenix")) mmorpg.fabric.job.JobTraits4.setCounter(sp, mmorpg.fabric.job.JobTraits4.counterCap(sp, j) * 0.6);
            else mmorpg.fabric.job.JobTraits15.setHunger(sp, id.equals("darkknight_lunge") ? 100 : 40);
        }
    }

    /** 먼 것부터 (게이지 대상 = 마지막에 찍은 가까운 과녁 — 모드 영상과 같음) */
    private static List<net.minecraft.world.entity.LivingEntity> byFar(ServerPlayer sp, List<net.minecraft.world.entity.LivingEntity> foes) {
        List<net.minecraft.world.entity.LivingEntity> out = new ArrayList<>(foes);
        out.removeIf(e -> e.isRemoved() || !e.isAlive());
        out.sort(java.util.Comparator.comparingDouble(e -> -e.distanceToSqr(sp)));
        return out;
    }

    private static net.minecraft.world.entity.LivingEntity nearest(Vec3 at, List<net.minecraft.world.entity.LivingEntity> foes) {
        net.minecraft.world.entity.LivingEntity best = null;
        for (var e : foes) {
            if (e.isRemoved() || !e.isAlive()) continue;
            if (best == null || e.distanceToSqr(at) < best.distanceToSqr(at)) best = e;
        }
        return best;
    }

    private static void moveFoe(ServerPlayer sp, List<net.minecraft.world.entity.LivingEntity> foes, Vec3 from, Vec3 to) {
        var e = nearest(from, foes);
        if (e == null || e.distanceToSqr(from) > 9) return;
        e.snapTo(to.x, to.y, to.z, 180F, 0F);
        e.setYHeadRot(180F);
        e.setYBodyRot(180F);
    }

    /** 키를 누르기 바로 전 (RpgJobGallery.beforeCast — 헌터스 마크 둘째까지 맞힘 · 받아넘기기에 맞음 · 이그니션 막음 · 맞음) */
    private static void jobBeforeCast(ServerPlayer sp, String id, List<net.minecraft.world.entity.LivingEntity> foes) {
        switch (id) {
            case "sniper_mark" -> Tasks.later(2, () -> {
                var e = nearest(sp.position(), foes);
                if (e != null && !sp.isRemoved()) mmorpg.fabric.job.JobTraits9.testMarkHits(sp, e, 2);
            });
            case "swordmaster_parry" -> Tasks.later(4, () -> foeHits(sp, nearest(sp.position(), foes), 4F, false));
            case "phoenix_ignition" -> {
                Tasks.later(8, () -> foeHits(sp, nearest(new Vec3(0.5, sp.getY(), 3.2), foes), 8F, true));
                Tasks.later(18, () -> foeHits(sp, nearest(new Vec3(0.5, sp.getY(), 3.2), foes), 8F, false));
            }
            default -> {}
        }
    }

    /** 과녁이 나를 침 (받는 피해 처리 — 과녁은 움직이지 않음 · block 이면 그 틱만 웅크려 막음) */
    private static void foeHits(ServerPlayer who, net.minecraft.world.entity.LivingEntity from, float amount, boolean block) {
        if (who == null || from == null || who.isRemoved() || from.isRemoved()) return;
        boolean was = who.isShiftKeyDown();
        if (block) who.setShiftKeyDown(true);
        float hp = who.getHealth();
        who.setHealth(mmorpg.fabric.RpgHealth.onLoss(who, who.damageSources().mobAttack(from), hp, hp - amount));
        if (block) who.setShiftKeyDown(was);
    }

    /** 태세 · 강화 뒤 평타 (모드 영상의 마우스 — 활 · 완드는 쓰기 (우클릭), 그 밖은 가장 가까운 과녁을 침 (Player#attack → 참격 · 범위)) */
    private static void jobBasic(ServerPlayer p, mmorpg.core.Jobs.Job j, List<net.minecraft.world.entity.LivingEntity> foes) {
        if (p.isRemoved()) return;
        boolean ranged = j.usesWeapon("bow") || j.usesWeapon("wand");
        if (ranged) {
            p.gameMode.useItem(p, p.level(), p.getMainHandItem(), net.minecraft.world.InteractionHand.MAIN_HAND);
            Tasks.later(1, () -> {
                if (!p.isRemoved() && p.isUsingItem()) p.releaseUsingItem();
            });
            return;
        }
        var e = nearest(p.position(), foes);
        if (e == null) return;
        p.attack(e); // 평타 동작 · 참격은 Slash.onAttack (PlayerMixin) 이 둘레에 알림
    }

    /** 핫바: 무기 넷 (검 · 활 · 완드 · 마검 — 종류마다 마지막 = 높은 등급) · 룬 · 장신구 · 유물 · 재료 · 제단, 갑옷: 힘 계열 한 벌 */
    private static void give(ServerPlayer p) {
        List<Item> hotbar = new ArrayList<>();
        for (String type : new String[]{"sword", "bow", "wand", "greatsword"}) {
            RpgWeaponItem last = null;
            for (RpgWeaponItem w : RpgItems.WEAPONS) if (w.weapon().type().equals(type)) last = w;
            if (last != null) hotbar.add(last);
        }
        for (String cls : new String[]{"RuneItem", "ArtifactItem", "RelicItem", "MaterialItem"}) {
            Item found = null;
            for (Item item : BuiltInRegistries.ITEM) {
                if (VanillaSupport.ours(BuiltInRegistries.ITEM.getKey(item)) && item.getClass().getSimpleName().equals(cls)) found = item;
            }
            if (found != null) hotbar.add(found);
        }
        Item altarItem = BuiltInRegistries.ITEM.getValue(Mmorpg.rpg("altar_scarecrow_king"));
        if (altarItem != null) hotbar.add(altarItem);
        for (int i = 0; i < hotbar.size() && i < 9; i++) p.getInventory().setItem(i, new ItemStack(hotbar.get(i)));
        RpgArmorItem chest = wearSet(p);
        log("gave " + hotbar.size() + " hotbar items, armor set " + (chest != null ? chest.piece().set() : "-"));
    }

    /** 힘 계열 갑옷 한 벌 (가장 낮은 레벨 — 요구 조건이 낮은 것) 을 입힘. 그 가슴 갑옷 (없으면 null) */
    private static RpgArmorItem wearSet(ServerPlayer p) {
        RpgArmorItem chest = null;
        for (RpgArmorItem a : GearItems.armor()) {
            if (!a.piece().type().equals("str") || !a.piece().slot().equals("Chest")) continue;
            if (chest == null || a.piece().level() < chest.piece().level()) chest = a;
        }
        if (chest != null) {
            String set = chest.piece().set();
            for (RpgArmorItem a : GearItems.armor()) {
                if (!a.piece().set().equals(set) || !a.piece().type().equals("str") || a.piece().level() != chest.piece().level()) continue;
                EquipmentSlot slot = switch (a.piece().slot()) {
                    case "Head" -> EquipmentSlot.HEAD;
                    case "Chest" -> EquipmentSlot.CHEST;
                    case "Legs" -> EquipmentSlot.LEGS;
                    default -> EquipmentSlot.FEET;
                };
                p.setItemSlot(slot, new ItemStack(a));
            }
        }
        return chest;
    }

    /** 착용 레벨이 가장 낮은 아티팩트 아이템 */
    private static Item lowestArtifact() {
        Item best = null;
        int bestLv = Integer.MAX_VALUE;
        for (Item item : BuiltInRegistries.ITEM) {
            mmorpg.core.Artifacts.Art a = mmorpg.fabric.gear.ArtifactItem.of(new ItemStack(item));
            if (a != null && a.level() < bestLv) {
                best = item;
                bestLv = a.level();
            }
        }
        return best;
    }

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    private static void say(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal("[probe] " + text));
        log("say " + text);
    }

    private static void log(String text) {
        Mmorpg.LOG.info("[vanilla-probe] {}", text);
    }

    static Identifier id(String path) {
        return Mmorpg.rpg(path);
    }
}
