package mmorpg.fabric.gear;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mmorpg.core.Armor;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgData;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.Tasks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * 입은 갑옷 (armor.js armorTick — 0.5초마다): 요구 조건 (최고 레벨 · 직접 찍은 스탯) 이 모자란 부위는 벗겨 가방으로 (가득 차면 발밑),
 * 나머지 부위의 스탯 보너스를 실제 스탯에 (RpgStats.setBonus — 피해 · 최대 체력 · 룬 위력). 크리에이티브 · 관전은 벗기지 않음 (보너스도 없음),
 * 다른 부위 칸에 들어간 갑옷 (명령어로 넣은 것) 은 보너스 없음.
 */
public final class ArmorSystem {
    private ArmorSystem() {}

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] PIECES = {"helmet", "chest", "legs", "boots"};

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (Tasks.now() % 10 == 0) tick(server);
        });
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) armorTick(sp);
    }

    /** 이 사람의 입은 갑옷을 확인 (armorTick) */
    public static void armorTick(ServerPlayer sp) {
        if (sp.isRemoved()) return;
        boolean enforce = RpgHealth.isRpgMode(sp);
        Map<String, Double> total = new HashMap<>();
        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack item = sp.getItemBySlot(SLOTS[i]);
            Armor.Piece a = GearItems.armorOf(item);
            if (a == null || !a.piece().equals(PIECES[i])) continue;
            List<String> lack = Armor.armorLack(a, RpgCombat.reqLevelOf(sp), RpgData.of(sp).base());
            if (!lack.isEmpty()) {
                if (enforce) takeOff(sp, SLOTS[i], item, a, lack);
                continue;
            }
            if (!enforce) continue;
            for (Map.Entry<String, Double> e : a.bonus().entrySet()) total.merge(e.getKey(), e.getValue(), Double::sum);
        }
        RpgStats.setBonus(sp, total);
    }

    /** 벗겨서 가방으로 (가득 차면 발밑) */
    private static void takeOff(ServerPlayer sp, EquipmentSlot slot, ItemStack item, Armor.Piece a, List<String> lack) {
        ItemStack copy = item.copy();
        sp.setItemSlot(slot, ItemStack.EMPTY);
        if (!sp.getItemBySlot(slot).isEmpty()) return;
        Notify.give(sp, copy);
        Notify.notify(sp, "§c" + a.title() + ": " + String.join(" · ", lack) + " 필요 §7- 벗겼습니다", 50);
        Notify.sound(sp, SoundEvents.VILLAGER_NO, 0.6F, 1F);
    }

    /** 시험 · 다른 기능용: 갑옷 이름 (자바 서식) */
    public static String nameOf(Armor.Piece a) {
        return BedrockText.toJava(a.name());
    }
}
