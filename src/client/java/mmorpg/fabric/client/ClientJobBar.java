package mmorpg.fabric.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mmorpg.fabric.job.JobSkills;
import mmorpg.fabric.rune.SkillBarNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

/**
 * 0.26.0 직업 스킬 칸 클라이언트 상태 (서버 JobSkills): 칸 5 의 스킬 id · 이름 · 그림, 재사용 대기 (클라이언트 틱으로 셈), 칸마다 연출 시각 (눌림 · 막힘 ·
 * 시전 · 준비). 키 = Shift + 룬 스킬 칸 1 ~ 5 의 키 (MmorpgClient — Shift 를 누른 채면 룬 칸 대신 이쪽).
 */
public final class ClientJobBar {
    private ClientJobBar() {}

    public static final int SIZE = JobSkills.SIZE;

    private static List<String> ids = blank();
    private static List<String> names = blank();
    private static List<ItemStack> icons = noIcons();
    private static boolean received;

    private record Cd(long end, int total) {}

    /** 스킬 id → 재사용 대기 */
    private static final Map<String, Cd> COOLDOWNS = new HashMap<>();
    private static long ticks;
    static final long[] PRESS_AT = new long[SIZE];
    static final long[] DENY_AT = new long[SIZE];
    static final long[] CAST_AT = new long[SIZE];
    static final long[] READY_AT = new long[SIZE];

    private static List<String> blank() {
        List<String> l = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) l.add("");
        return l;
    }

    private static List<ItemStack> noIcons() {
        List<ItemStack> l = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) l.add(ItemStack.EMPTY);
        return l;
    }

    static void clear() {
        ids = blank();
        names = blank();
        icons = noIcons();
        received = false;
        COOLDOWNS.clear();
        for (long[] a : new long[][] {PRESS_AT, DENY_AT, CAST_AT, READY_AT}) java.util.Arrays.fill(a, 0);
    }

    /** 서버가 보낸 칸들 */
    static void update(JobSkills.BarPayload p) {
        List<String> ni = blank();
        List<String> nn = blank();
        List<ItemStack> nc = noIcons();
        for (int i = 0; i < Math.min(SIZE, p.ids().size()); i++) {
            ni.set(i, p.ids().get(i));
            nn.set(i, i < p.names().size() ? p.names().get(i) : "");
            nc.set(i, i < p.icons().size() ? p.icons().get(i) : ItemStack.EMPTY);
        }
        ids = ni;
        names = nn;
        icons = nc;
        received = true;
    }

    /** 재사용 대기 (SkillBarNet.CooldownPayload 의 이름이 "job:" 으로 시작 — ClientSkillBar 가 넘김) */
    static void cooldown(String id, int left, int total) {
        if (left <= 0) {
            COOLDOWNS.remove(id);
            return;
        }
        COOLDOWNS.put(id, new Cd(ticks + left, Math.max(left, total)));
        int i = ids.indexOf(id);
        if (i >= 0) CAST_AT[i] = Util.getMillis();
    }

    static void clearCooldowns() {
        COOLDOWNS.clear();
    }

    /** 매 클라이언트 틱: 끝난 재사용 대기 → 준비 연출 */
    static void tick(Minecraft mc) {
        if (mc.level == null) return;
        if (!mc.isPaused()) ticks++;
        if (COOLDOWNS.isEmpty()) return;
        long now = Util.getMillis();
        for (var it = COOLDOWNS.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getValue().end() > ticks) continue;
            it.remove();
            int i = ids.indexOf(e.getKey());
            if (i >= 0) READY_AT[i] = now;
        }
    }

    // ------------------------------------------------------------ 읽기

    public static boolean received() {
        return received;
    }

    /** 칸 i 가 비었는지 (직업 스킬 없음) */
    public static boolean empty(int i) {
        return i < 0 || i >= SIZE || ids.get(i).isEmpty();
    }

    /** 칸에 직업 스킬이 하나라도 있는지 */
    public static boolean any() {
        for (int i = 0; i < SIZE; i++) if (!empty(i)) return true;
        return false;
    }

    public static String name(int i) {
        return i < 0 || i >= SIZE ? "" : names.get(i);
    }

    public static ItemStack icon(int i) {
        return i < 0 || i >= SIZE ? ItemStack.EMPTY : icons.get(i);
    }

    public static double cooldownLeft(int i, float partial) {
        Cd c = empty(i) ? null : COOLDOWNS.get(ids.get(i));
        return c == null ? 0 : Math.max(0, (c.end() - ticks - partial) / 20.0);
    }

    public static double cooldownFrac(int i, float partial) {
        Cd c = empty(i) ? null : COOLDOWNS.get(ids.get(i));
        if (c == null || c.total() <= 0) return 0;
        return Math.max(0, Math.min(1, (c.end() - ticks - partial) / c.total()));
    }

    /** 칸 i 의 키 글자 ("⇧R" — Shift + 룬 칸 i 의 키, 그 키가 지정 안 됨이면 "") */
    public static String keyLabel(int i) {
        String k = ClientSkillBar.keyLabel(i);
        return k.isEmpty() ? "" : "⇧" + k;
    }

    // ------------------------------------------------------------ 키

    /** 칸 i 의 키 (Shift + 룬 칸 i 의 키): 서버에 쓰기 + 눌림 연출 (빈 칸 · 재사용 대기 중이면 막힘 연출 · 낮은 소리) */
    static void press(int i) {
        if (i < 0 || i >= SIZE) return;
        long now = Util.getMillis();
        PRESS_AT[i] = now;
        if (empty(i) || cooldownLeft(i, 0) > 0.05) {
            DENY_AT[i] = now;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.4F));
        }
        ClientPlayNetworking.send(new SkillBarNet.ActionPayload("job", i, 0));
    }
}
