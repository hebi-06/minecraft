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
                say(p, "shot 01_hand");
                next(p, 1, 60);
            }
            case 1 -> {
                say(p, "inv 02_inventory");
                next(p, 2, 80);
            }
            case 2 -> {
                Block altar = BuiltInRegistries.BLOCK.getValue(Mmorpg.rpg("altar_scarecrow_king"));
                if (altar != null) level.setBlockAndUpdate(new BlockPos(-2, y, 5), altar.defaultBlockState());
                Monsters.spawnAt(level, "scarecrow", new Vec3(1.5, y, 6.5), 180F);
                Dummy.spawnDummy(p);
                say(p, "shot 03_world");
                next(p, 3, 60);
            }
            case 3 -> {
                say(p, "cam back");
                next(p, 4, 30);
            }
            case 4 -> {
                say(p, "shot 04_third");
                next(p, 5, 60);
            }
            default -> {
                say(p, "done");
                log("done");
            }
        }
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
