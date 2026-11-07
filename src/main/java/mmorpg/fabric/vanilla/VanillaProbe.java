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
            if (NAME.equals(name)) Tasks.later(160, () -> scene(p, 0));
        });
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
                next(p, 16, 40);
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

    private static void check(String what, boolean ok) {
        log("check " + (ok ? "ok" : "fail") + ": " + what);
    }

    private static void next(ServerPlayer p, int step, int ticks) {
        Tasks.later(ticks, () -> scene(p, step));
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
        RpgArmorItem chest = null;
        for (RpgArmorItem a : GearItems.armor()) if (a.piece().type().equals("str") && a.piece().slot().equals("Chest")) chest = a;
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
        log("gave " + hotbar.size() + " hotbar items, armor set " + (chest != null ? chest.piece().set() : "-"));
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
