package mmorpg.fabric.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mmorpg.core.Runes;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.rune.SkillBar;
import mmorpg.fabric.rune.SkillBarNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * 스킬 칸 (11단계) 클라이언트 상태: 서버가 보낸 칸들 · 룬 재사용 대기 (클라이언트 틱으로 셈), 칸마다 연출 시각 (올림 · 뺌 · 시전 · 준비 · 눌림 · 막힘).
 * 키를 누르면 서버에 쓰기 (cast) 를 보내고 눌림 연출 (재사용 대기 중 · 빈 칸이면 막힘 연출).
 */
public final class ClientSkillBar {
    private ClientSkillBar() {}

    public static final int SIZE = SkillBar.SIZE;

    /** 칸들 (서버가 보낸 것 — 받기 전에는 빈 칸) */
    private static List<ItemStack> slots = empty();
    private static boolean received;
    /** 룬 ID → 재사용 대기 (끝나는 클라이언트 틱 · 전체 틱) */
    private record Cd(long end, int total) {}

    private static final Map<String, Cd> COOLDOWNS = new HashMap<>();
    /** 클라이언트 틱 (멈춤 화면에서는 멈춤) */
    private static long ticks;
    /** 칸마다 연출 시각 (밀리초, 0 = 없음) */
    static final long[] EQUIP_AT = new long[SIZE];
    static final long[] REMOVE_AT = new long[SIZE];
    static final long[] CAST_AT = new long[SIZE];
    static final long[] READY_AT = new long[SIZE];
    static final long[] PRESS_AT = new long[SIZE];
    static final long[] DENY_AT = new long[SIZE];

    private static List<ItemStack> empty() {
        List<ItemStack> l = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) l.add(ItemStack.EMPTY);
        return l;
    }

    static void clear() {
        slots = empty();
        received = false;
        COOLDOWNS.clear();
        for (long[] a : new long[][] {EQUIP_AT, REMOVE_AT, CAST_AT, READY_AT, PRESS_AT, DENY_AT}) java.util.Arrays.fill(a, 0);
    }

    /** 서버가 보낸 칸들: 바뀐 칸에 연출 (처음 받은 것은 연출 없이) + 소리 */
    static void update(SkillBarNet.BarPayload p) {
        List<ItemStack> next = empty();
        for (int i = 0; i < Math.min(SIZE, p.slots().size()); i++) next.set(i, p.slots().get(i));
        long now = Util.getMillis();
        boolean filled = false;
        boolean emptied = false;
        if (received) {
            for (int i = 0; i < SIZE; i++) {
                ItemStack a = slots.get(i);
                ItemStack b = next.get(i);
                if (ItemStack.matches(a, b)) continue;
                if (!b.isEmpty()) {
                    EQUIP_AT[i] = now;
                    filled = true;
                } else {
                    REMOVE_AT[i] = now;
                    emptied = true;
                }
            }
        }
        slots = next;
        received = true;
        Minecraft mc = Minecraft.getInstance();
        if (filled) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_GENERIC, 1.25F));
        else if (emptied) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BUNDLE_REMOVE_ONE, 1.1F));
    }

    /** 서버가 보낸 재사용 대기 ("*" = 모두 지움 · left 0 = 끝남). 스킬 칸의 룬이 대기를 시작하면 시전 연출 */
    static void cooldown(SkillBarNet.CooldownPayload p) {
        if ("*".equals(p.rune())) {
            COOLDOWNS.clear();
            return;
        }
        if (p.left() <= 0) {
            COOLDOWNS.remove(p.rune());
            return;
        }
        COOLDOWNS.put(p.rune(), new Cd(ticks + p.left(), Math.max(p.left(), p.total())));
        int i = indexOf(p.rune());
        if (i >= 0) CAST_AT[i] = Util.getMillis();
    }

    /** 매 클라이언트 틱: 틱을 세고, 끝난 재사용 대기 → 그 칸 준비 연출 + 작은 소리 */
    static void tick(Minecraft mc) {
        if (mc.level == null) return;
        if (!mc.isPaused()) ticks++;
        if (COOLDOWNS.isEmpty()) return;
        long now = Util.getMillis();
        boolean ping = false;
        for (var it = COOLDOWNS.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getValue().end() > ticks) continue;
            it.remove();
            int i = indexOf(e.getKey());
            if (i < 0) continue;
            READY_AT[i] = now;
            ping = true;
        }
        if (ping) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.7F, 0.35F));
    }

    // ------------------------------------------------------------ 읽기

    /** 모드가 있는 서버에서 칸을 받았는지 */
    public static boolean received() {
        return received;
    }

    public static ItemStack get(int i) {
        return i < 0 || i >= SIZE ? ItemStack.EMPTY : slots.get(i);
    }

    /** 칸에 룬이 하나라도 있는지 */
    public static boolean any() {
        for (ItemStack s : slots) if (!s.isEmpty()) return true;
        return false;
    }

    /** 첫 빈 칸 (없으면 -1) */
    public static int firstEmpty() {
        for (int i = 0; i < SIZE; i++) if (slots.get(i).isEmpty()) return i;
        return -1;
    }

    /** 이 룬 ID 가 올라간 칸 (없으면 -1) */
    public static int indexOf(String runeId) {
        for (int i = 0; i < SIZE; i++) {
            Runes.Rune r = RuneItems.runeOf(slots.get(i));
            if (r != null && r.id().equals(runeId)) return i;
        }
        return -1;
    }

    /** 칸 i 의 남은 재사용 대기 (초, 0 = 준비) — partial = 틱 사이 */
    public static double cooldownLeft(int i, float partial) {
        Runes.Rune r = RuneItems.runeOf(get(i));
        if (r == null) return 0;
        Cd c = COOLDOWNS.get(r.id());
        if (c == null) return 0;
        return Math.max(0, (c.end() - ticks - partial) / 20.0);
    }

    /** 칸 i 의 남은 재사용 대기 비율 (1 = 막 시작 · 0 = 준비) */
    public static double cooldownFrac(int i, float partial) {
        Runes.Rune r = RuneItems.runeOf(get(i));
        if (r == null) return 0;
        Cd c = COOLDOWNS.get(r.id());
        if (c == null || c.total() <= 0) return 0;
        return Math.max(0, Math.min(1, (c.end() - ticks - partial) / c.total()));
    }

    // ------------------------------------------------------------ 키

    /** 칸 i 의 키를 누름: 서버에 쓰기 + 눌림 연출 (재사용 대기 중 · 빈 칸이면 막힘 연출도) */
    static void press(int i) {
        long now = Util.getMillis();
        PRESS_AT[i] = now;
        if (get(i).isEmpty() || cooldownLeft(i, 0) > 0.05) DENY_AT[i] = now;
        ClientPlayNetworking.send(new SkillBarNet.ActionPayload("cast", i, 0));
    }

    /** 서버에 스킬 칸 창의 일 (equip · unequip · move) */
    static void send(String action, int a, int b) {
        ClientPlayNetworking.send(new SkillBarNet.ActionPayload(action, a, b));
    }

    /** 칸 i 의 키 이름 (짧게 — "R" · "M4" · 지정 안 됨이면 "") */
    public static String keyLabel(int i) {
        KeyMapping k = i >= 0 && i < MmorpgClient.skillKeys.length ? MmorpgClient.skillKeys[i] : null;
        return shortKey(k);
    }

    static String shortKey(KeyMapping k) {
        if (k == null || k.isUnbound()) return "";
        String s = k.getTranslatedKeyMessage().getString().trim();
        if (s.length() <= 3) return s.toUpperCase(Locale.ROOT);
        String low = s.toLowerCase(Locale.ROOT);
        String digits = s.replaceAll("[^0-9]", "");
        if (low.contains("mouse") || s.contains("마우스")) return "M" + digits;
        if (low.startsWith("keypad") || low.startsWith("num") || s.contains("키패드")) return "N" + digits;
        return s.substring(0, 2).toUpperCase(Locale.ROOT);
    }
}
