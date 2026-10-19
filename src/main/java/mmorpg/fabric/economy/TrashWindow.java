package mmorpg.fabric.economy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgNet;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.ui.Inv;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 0.21.0 쓰레기통 (사용자 요청 — 버린 아이템이 가방으로 돌아오므로 지우는 곳): 바닐라 상자 창 27칸 (끌어 놓기 · Shift + 누르기 그대로).
 * 창을 닫으면 넣은 것을 모두 지움 (발밑 불꽃 · 연기 · 지글 소리), 마지막으로 지운 것은 다음에 지우기 전까지 "/쓰레기통 되살리기" 로 한 번 되찾음
 * (서버가 꺼지면 사라짐). 스탯 창 아랫줄 · /쓰레기통 (/trash) 으로 엶.
 */
public final class TrashWindow {
    private TrashWindow() {}

    public static final int SIZE = 27;
    static final double[] FLAME = {1, 0.45, 0.12};
    static final double[] ASH = {0.35, 0.33, 0.36};
    /** 사람마다 마지막으로 지운 것 (되살리기) */
    private static final Map<UUID, List<ItemStack>> LAST = new HashMap<>();
    /** 시험용: 지운 묶음 수 */
    public static int burned;

    public static void init() {
        RpgNet.onStatAction("trash", TrashWindow::open);
    }

    /** 쓰레기통 창을 엶 (관전 모드는 안 됨) */
    public static boolean open(ServerPlayer sp) {
        if (sp == null || sp.isRemoved()) return false;
        if (sp.isSpectator()) {
            Notify.notify(sp, "§c관전 모드에서는 쓰레기통을 열 수 없습니다", 60);
            return false;
        }
        SimpleContainer box = new SimpleContainer(SIZE);
        sp.openMenu(new SimpleMenuProvider((id, playerInv, p) -> new ChestMenu(MenuType.GENERIC_9x3, id, playerInv, box, 3) {
            @Override
            public void removed(Player player) {
                super.removed(player);
                if (player instanceof ServerPlayer s) burn(s, box);
            }
        }, Component.literal("쓰레기통 — 닫으면 지워집니다")));
        Notify.notify(sp, "§7지울 아이템을 넣고 창을 닫으세요 §8(마지막 것은 /쓰레기통 되살리기)", 60);
        SkillFx.play(sp.level(), "random.chestopen", V.up(sp.position(), 1), 0.7, 0.6);
        return true;
    }

    /** 창이 닫힘: 넣은 것을 모두 지우고 되살리기 기록 */
    static void burn(ServerPlayer sp, SimpleContainer box) {
        List<ItemStack> gone = new ArrayList<>();
        int n = 0;
        for (int i = 0; i < box.getContainerSize(); i++) {
            ItemStack s = box.removeItemNoUpdate(i);
            if (s.isEmpty()) continue;
            gone.add(s);
            n += s.getCount();
        }
        if (gone.isEmpty()) return;
        LAST.put(sp.getUUID(), gone);
        burned++;
        String what = gone.size() == 1 ? gone.get(0).getHoverName().getString() + (n > 1 ? " " + n + "개" : "") : gone.size() + "묶음 · " + n + "개";
        Notify.say(sp, "§7쓰레기통: §f" + what + " §7을(를) 지웠습니다 §8— 되찾기: /쓰레기통 되살리기 (다음에 지우기 전까지)");
        if (!sp.isRemoved()) burnFx(sp);
    }

    /** 지울 때: 발밑 불꽃 고리 · 연기 · 재 · 지글 소리 */
    static void burnFx(ServerPlayer sp) {
        ServerLevel level = sp.level();
        Vec3 feet = sp.position();
        SkillFx.rune(level, feet, Opt.of("size", 1.1, "life", 0.5, "color", FLAME, "alpha", 0.85, "progress", 1, "rate", 200, "grow", 0.3));
        SkillFx.sparks(level, V.up(feet, 0.4), FLAME, 10, 2.5);
        SkillFx.sparks(level, V.up(feet, 0.6), ASH, 8, 1.2);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {
            SkillFx.vanilla(level, "minecraft:basic_smoke_particle", feet.add((r.nextDouble() - 0.5) * 1.2, 0.2 + r.nextDouble() * 0.8, (r.nextDouble() - 0.5) * 1.2));
        }
        SkillFx.play(level, "random.fizz", V.up(feet, 0.5), 1.0, 0.8);
    }

    /** 마지막으로 지운 것을 되찾음 (가방에 모두 들어갈 때만) — 결과 글 */
    public static String restore(ServerPlayer sp) {
        List<ItemStack> last = LAST.get(sp.getUUID());
        if (last == null || last.isEmpty()) return "§7되찾을 것이 없습니다 §8(서버가 다시 켜졌거나 이미 되찾음)";
        if (!Inv.fitsAfter(sp, List.of(), last)) return "§c가방에 자리가 모자랍니다 §7(" + last.size() + "칸 필요)";
        LAST.remove(sp.getUUID());
        for (ItemStack s : last) Inv.give(sp, s);
        SkillFx.play(sp.level(), "random.orb", V.up(sp.position(), 1), 1.2, 0.8);
        return "§a지운 것을 되찾았습니다 §7(" + last.size() + "묶음)";
    }

    /** 시험용: 마지막으로 지운 것 (없으면 빈 목록) */
    public static List<ItemStack> last(UUID id) {
        return LAST.getOrDefault(id, List.of());
    }
}
