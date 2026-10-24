package mmorpg.fabric.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import mmorpg.core.Artifacts;
import mmorpg.core.Stats;
import mmorpg.fabric.artifact.ArtifactNet;
import mmorpg.fabric.artifact.ArtifactSystem;
import mmorpg.fabric.gear.ArtifactItem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * 0.37.0 아티팩트 클라이언트 상태: 서버가 보낸 칸 다섯 · 칸마다 연출 시각 (낌 · 뺌), 보너스 합 (core Artifacts — 서버와 같은 식: 칸 종류 · 착용 레벨).
 * 스탯 시트 (ClientRpgState.sheet) 가 스탯 트리 합에 더해 씀 (피해 · 치명타 · 최대 체력 · 룬 툴팁 숫자).
 */
public final class ClientArtifacts {
    private ClientArtifacts() {}

    public static final int SIZE = ArtifactSystem.SIZE;

    private static List<ItemStack> slots = empty();
    private static boolean received;
    /** 칸마다 연출 시각 (밀리초, 0 = 없음) */
    static final long[] EQUIP_AT = new long[SIZE];
    static final long[] REMOVE_AT = new long[SIZE];
    /** 합계 줄이 바뀐 시각 (보너스 종류 → 밀리초) */
    static final java.util.Map<String, Long> CHANGED_AT = new java.util.HashMap<>();

    private static List<ItemStack> empty() {
        List<ItemStack> l = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) l.add(ItemStack.EMPTY);
        return l;
    }

    static void clear() {
        slots = empty();
        received = false;
        Arrays.fill(EQUIP_AT, 0);
        Arrays.fill(REMOVE_AT, 0);
        CHANGED_AT.clear();
    }

    /** 서버가 보낸 칸들: 바뀐 칸에 연출 (처음 받은 것은 연출 없이) + 소리, 바뀐 합계 줄 표시. open 이면 창 */
    static void update(ArtifactNet.SlotsPayload p) {
        List<ItemStack> next = empty();
        for (int i = 0; i < Math.min(SIZE, p.slots().size()); i++) next.set(i, p.slots().get(i));
        long now = Util.getMillis();
        Map<String, Double> before = totals();
        boolean filled = false;
        boolean emptied = false;
        int top = -1;
        if (received) {
            for (int i = 0; i < SIZE; i++) {
                if (ItemStack.matches(slots.get(i), next.get(i))) continue;
                if (!next.get(i).isEmpty()) {
                    EQUIP_AT[i] = now;
                    filled = true;
                    Artifacts.Art a = ArtifactItem.of(next.get(i));
                    if (a != null) top = Math.max(top, a.gradeIndex());
                } else {
                    REMOVE_AT[i] = now;
                    emptied = true;
                }
            }
        }
        slots = next;
        received = true;
        if (filled || emptied) {
            Map<String, Double> after = totals();
            java.util.Set<String> keys = new java.util.HashSet<>(before.keySet());
            keys.addAll(after.keySet());
            for (String k : keys) if (Math.abs(after.getOrDefault(k, 0.0) - before.getOrDefault(k, 0.0)) > 1e-9) CHANGED_AT.put(k, now);
        }
        Minecraft mc = Minecraft.getInstance();
        if (filled) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F + 0.12F * Math.max(0, top), 1.0F));
            if (top >= 3) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BEACON_POWER_SELECT, 1.4F + 0.2F * (top - 3), 0.45F));
        } else if (emptied) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.8F, 0.7F));
        }
        if (p.open() && mc.player != null) {
            if (!(mc.gui.screen() instanceof ArtifactScreen)) mc.gui.setScreen(new ArtifactScreen());
        }
    }

    public static boolean received() {
        return received;
    }

    /** 칸 i 의 아이템 (없으면 EMPTY) */
    public static ItemStack get(int i) {
        return i < 0 || i >= SIZE ? ItemStack.EMPTY : slots.get(i);
    }

    /** 칸 i 의 아티팩트 (없으면 null) */
    public static Artifacts.Art art(int i) {
        return ArtifactItem.of(get(i));
    }

    /** 낀 칸 수 */
    public static int worn() {
        int n = 0;
        for (ItemStack s : slots) if (ArtifactItem.of(s) != null) n++;
        return n;
    }

    /** 지금 렙제 레벨 (최고 레벨 · 지금 레벨 중 큰 것 — 서버 RpgCombat.reqLevelOf 와 같음) */
    public static double level() {
        Minecraft mc = Minecraft.getInstance();
        return Stats.reqLevelOf(ClientRpgState.lvmax(), mc.player == null ? 0 : mc.player.experienceLevel);
    }

    /** 칸 순서의 아이템 ID (빈 칸 null) */
    static List<String> ids() {
        List<String> out = new ArrayList<>(SIZE);
        for (ItemStack s : slots) {
            Artifacts.Art a = ArtifactItem.of(s);
            out.add(a == null ? null : a.id());
        }
        return out;
    }

    /** 아티팩트 보너스 합 (상한 전 — 서버 ArtifactSystem.totals 와 같은 식) */
    public static Map<String, Double> totals() {
        return Artifacts.totals(ids(), level());
    }

    /** 서버에 창의 일 (equip · unequip · move · sync) */
    static void send(String action, int a, int b) {
        if (ClientPlayNetworking.canSend(ArtifactNet.ActionPayload.TYPE)) ClientPlayNetworking.send(new ArtifactNet.ActionPayload(action, a, b));
    }
}
