package mmorpg.fabric.client;

import java.util.List;
import mmorpg.fabric.client.motion.ClientMotions;
import mmorpg.fabric.smith.SmithNet;
import mmorpg.fabric.smith.SmithUi;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * 0.44.0 대장장이 전용 창 (클라이언트 상태): 서버가 보낸 창 (ViewPayload — open 이면 창을 엶) · 망치질 (WorkPayload — 창 패널의 대장장이와 세상의 그 NPC 가 같은
 * 동작, 망치가 닿는 틱마다 모루 소리 · 창의 불꽃) · 새 결과 (seq 가 바뀌면 소리 · 창 연출 한 번).
 * 0.45.0: 룬술사 · 상인 창도 같은 상태 — 일 (Work) 의 동작 · 연출 종류 (fx: forge · grind · merge · ticket · buy · sell) 에 따라 닿는 소리 · 결과 소리가 다름.
 */
public final class ClientSmith {
    private ClientSmith() {}

    /** 일 하나: NPC (엔티티 번호 — -1 없음) · 칸 · 패널에 올린 것 · 틱 · 닿는 틱 · 빛 색 (등급) · 시작 (ClientMotions 틱) · 동작 · 연출 종류 */
    public record Work(int npc, String key, ItemStack item, int ticks, List<Integer> impacts, int grade, long start, String motion, String fx) {}

    static SmithNet.ViewPayload view;
    static Work work;
    /** 이번 망치질에서 닿은 수 */
    static int fired;
    /** 마지막으로 닿은 때 (밀리초) · 몇 번째 */
    static long hitAt = -1;
    static int hitIndex;
    /** 본 결과 번호 · 마지막 결과 · 그때 (밀리초) */
    static int seenSeq;
    static SmithNet.Result result;
    static long resultAt = -1;

    static void clear() {
        view = null;
        work = null;
        fired = 0;
        hitAt = -1;
        seenSeq = 0;
        result = null;
        resultAt = -1;
    }

    /** 서버가 보낸 창: open 이면 창을 엶 (열려 있으면 그대로 바꿈). 새 결과면 소리 · 연출 */
    static void view(SmithNet.ViewPayload p, Minecraft mc) {
        SmithNet.ViewPayload before = view;
        view = p;
        SmithNet.Result r = p.result();
        int seq = r == null ? 0 : r.seq();
        if (p.open()) seenSeq = seq; // 새로 연 창: 지난 결과는 다시 보이지 않음
        else if (seq != seenSeq) {
            seenSeq = seq;
            if (r != null && !r.kind().isEmpty()) {
                result = r;
                resultAt = Util.getMillis();
                resultSound(r.kind(), work != null ? work.fx() : "forge");
            }
        }
        if (p.open() && !(mc.gui.screen() instanceof SmithScreen)) mc.gui.setScreen(new SmithScreen());
        else if (mc.gui.screen() instanceof SmithScreen s) s.onView(before, p);
    }

    /** 일 시작: 창 패널 · 세상의 NPC (모루 · 받침 · 사는 사람 쪽을 보며) 동작 */
    static void work(SmithNet.WorkPayload p) {
        long start = ClientMotions.ticks() + 1;
        String motion = p.motion() == null || p.motion().isEmpty() ? SmithUi.MOTION : p.motion();
        String fx = p.fx() == null || p.fx().isEmpty() ? "forge" : p.fx();
        work = new Work(p.npc(), p.key(), p.item(), Math.max(1, p.ticks()), p.impacts(), p.grade(), start, motion, fx);
        fired = 0;
        result = null;
        resultAt = -1;
        if (p.npc() >= 0) ClientMotions.start(p.npc(), motion, p.yaw());
    }

    /** 지금 망치질 (끝났으면 null) */
    public static Work work() {
        return work;
    }

    /** 망치질 진행 (0 ~ 1 — 없으면 -1) */
    public static double progress(float partial) {
        Work w = work;
        if (w == null) return -1;
        double t = (ClientMotions.ticks() + partial - w.start()) / w.ticks();
        return t < 0 || t > 1 ? (t > 1 ? 1 : 0) : t;
    }

    /** 시험: 마지막 창 · 결과 */
    public static SmithNet.ViewPayload lastView() {
        return view;
    }

    public static SmithNet.Result lastResult() {
        return result;
    }

    /** 시험: 이번 망치질에서 닿은 수 */
    public static int hits() {
        return fired;
    }

    /** 클라이언트 틱: 망치가 닿는 틱이면 모루 소리 · 창의 불꽃 */
    static void tick(Minecraft mc) {
        Work w = work;
        if (w == null) return;
        long el = ClientMotions.ticks() - w.start();
        while (fired < w.impacts().size() && el >= w.impacts().get(fired)) {
            hit(mc, fired, fired == w.impacts().size() - 1);
            fired++;
        }
        if (el > w.ticks() + 60) work = null;
    }

    private static void hit(Minecraft mc, int i, boolean last) {
        hitAt = Util.getMillis();
        hitIndex = i;
        Work w = work;
        // 창에서 듣는 소리 (세상의 NPC 소리는 둘레 사람만)
        switch (w == null ? "forge" : w.fx()) {
            case "grind" -> {
                // 맑은 소리가 한 번마다 높아지다 마지막에 깨짐
                sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F + i * 0.18F, 0.9F);
                if (last) {
                    sound(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 0.9F);
                    sound(SoundEvents.GRINDSTONE_USE, 1.15F, 0.45F);
                }
            }
            case "merge" -> {
                sound(last ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.AMETHYST_BLOCK_RESONATE, last ? 1.3F : 1.0F, 0.9F);
                if (last) sound(SoundEvents.BEACON_POWER_SELECT, 1.6F, 0.45F);
            }
            case "ticket" -> {
                sound(SoundEvents.AMETHYST_BLOCK_HIT, 1.0F + i * 0.15F, 0.9F);
                if (last) sound(SoundEvents.VAULT_EJECT_ITEM, 1.2F, 0.8F);
            }
            case "buy" -> {
                // 계산대에 올림 (나무 · 툭) → 건넴 (은화 소리)
                if (i == 0) sound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.9F, 0.8F);
                if (last) {
                    sound(SoundEvents.CHAIN_PLACE, 1.6F, 0.55F);
                    sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.3F, 0.5F);
                }
            }
            case "sell" -> sound(SoundEvents.CHAIN_PLACE, 1.4F + i * 0.12F, 0.6F);
            default -> {
                // 땅 · 땅 · 쾅 (마지막은 낮고 크게)
                sound(SoundEvents.ANVIL_USE, last ? 0.86F : 1.0F + i * 0.07F + (float) (Math.random() * 0.06), last ? 0.75F : 0.6F);
                if (last) sound(SoundEvents.ANVIL_LAND, 1.5F, 0.22F);
            }
        }
        if (mc.gui.screen() instanceof SmithScreen s) s.onHit(i, last);
    }

    /** 결과 소리 (창에서 — fx = 일의 종류: 사기 · 팔기 · 뽑기권은 은화 · 꺼내는 소리) */
    private static void resultSound(String kind, String fx) {
        if (kind.equals("done") && (fx.equals("buy") || fx.equals("sell"))) {
            sound(SoundEvents.VILLAGER_YES, 1.0F, 0.6F);
            sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.5F, 0.55F);
            return;
        }
        if (kind.equals("done") && (fx.equals("grind") || fx.equals("ticket"))) {
            sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 0.9F);
            sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F, 0.45F);
            return;
        }
        switch (kind) {
            case "success" -> {
                sound(SoundEvents.PLAYER_LEVELUP, 1.25F, 0.7F);
                sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 1.0F);
            }
            case "done" -> {
                sound(SoundEvents.PLAYER_LEVELUP, 1.35F, 0.6F);
                sound(SoundEvents.SMITHING_TABLE_USE, 1.0F, 0.7F);
            }
            case "down" -> {
                sound(SoundEvents.ANVIL_BREAK, 0.8F, 0.7F);
                sound(SoundEvents.FIRE_EXTINGUISH, 0.7F, 0.35F);
            }
            case "guard" -> {
                sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.0F);
                sound(SoundEvents.FIRE_EXTINGUISH, 1.2F, 0.3F);
            }
            case "keep" -> sound(SoundEvents.FIRE_EXTINGUISH, 1.25F, 0.45F);
            default -> Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.6F));
        }
    }

    static void sound(SoundEvent e, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, volume));
    }

    /** 창의 일을 서버에 (tab · page · do · sync · close) */
    static void send(String action, String key, int a, int b) {
        if (ClientPlayNetworking.canSend(SmithNet.ActionPayload.TYPE)) ClientPlayNetworking.send(new SmithNet.ActionPayload(action, key == null ? "" : key, a, b));
    }
}
