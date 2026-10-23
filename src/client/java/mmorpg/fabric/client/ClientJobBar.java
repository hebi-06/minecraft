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
 * 0.27.0 직업 (id · 이름 · 두 빛 — 칸 테두리 · 글자), 칸마다 잠김 사유 ("15렙" · "칼 · 마검" · "요구 스탯"), 직업 자원 게이지 (검세 · 마력 각인) · 태세 남은 시간
 * (JobSkills.GaugePayload — 남은 틱은 받은 뒤 클라이언트 틱으로 셈).
 */
public final class ClientJobBar {
    private ClientJobBar() {}

    public static final int SIZE = JobSkills.SIZE;

    private static List<String> ids = blank();
    private static List<String> names = blank();
    private static List<ItemStack> icons = noIcons();
    private static List<String> locks = blank();
    /** 0.31.0 칸마다 강화 단계 (0 ~ 5) */
    private static int[] ups = new int[SIZE];
    /** 0.31.0 강화 단계가 오른 시각 (칸 배지가 반짝임) */
    static final long[] UP_AT = new long[SIZE];
    private static boolean received;
    /** 0.27.0 직업 (없으면 "") · 두 빛 (0xRRGGBB) */
    private static String job = "";
    private static String jobName = "";
    private static int colorA = 0x7FD8FF;
    private static int colorB = 0x7FD8FF;
    /** 0.27.0 직업 자원 게이지 · 태세 (끝나는 클라이언트 틱 · 전체 틱) */
    private static String gaugeName = "";
    private static int gaugeValue;
    private static int gaugeMax;
    private static String stance = "";
    private static long stanceEnd;
    private static int stanceTotal;

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
        locks = blank();
        ups = new int[SIZE];
        java.util.Arrays.fill(UP_AT, 0);
        received = false;
        job = "";
        jobName = "";
        colorA = colorB = 0x7FD8FF;
        gaugeName = "";
        gaugeValue = gaugeMax = 0;
        stance = "";
        stanceEnd = 0;
        stanceTotal = 0;
        COOLDOWNS.clear();
        for (long[] a : new long[][] {PRESS_AT, DENY_AT, CAST_AT, READY_AT}) java.util.Arrays.fill(a, 0);
    }

    /** 서버가 보낸 칸들 */
    static void update(JobSkills.BarPayload p) {
        List<String> ni = blank();
        List<String> nn = blank();
        List<ItemStack> nc = noIcons();
        List<String> nl = blank();
        int[] nu = new int[SIZE];
        for (int i = 0; i < Math.min(SIZE, p.ids().size()); i++) {
            ni.set(i, p.ids().get(i));
            nn.set(i, i < p.names().size() ? p.names().get(i) : "");
            nc.set(i, i < p.icons().size() ? p.icons().get(i) : ItemStack.EMPTY);
            nl.set(i, i < p.locks().size() ? p.locks().get(i) : "");
            nu[i] = i < p.ups().size() ? Math.max(0, p.ups().get(i)) : 0;
        }
        // 잠김이 풀린 칸: 준비 연출 (레벨 · 무기를 맞춤)
        long now = Util.getMillis();
        for (int i = 0; i < SIZE; i++) if (received && ni.get(i).equals(ids.get(i)) && !locks.get(i).isEmpty() && nl.get(i).isEmpty()) READY_AT[i] = now;
        // 0.31.0 강화 단계가 오른 칸: 배지가 반짝임 + 준비 연출
        for (int i = 0; i < SIZE; i++) {
            if (received && ni.get(i).equals(ids.get(i)) && nu[i] > ups[i]) {
                UP_AT[i] = now;
                READY_AT[i] = now;
            }
        }
        ups = nu;
        ids = ni;
        names = nn;
        icons = nc;
        locks = nl;
        job = p.job();
        jobName = p.jobName();
        colorA = job.isEmpty() ? 0x7FD8FF : p.colorA() & 0xFFFFFF;
        colorB = job.isEmpty() ? 0x7FD8FF : p.colorB() & 0xFFFFFF;
        received = true;
    }

    /** 0.27.0 직업 자원 게이지 · 태세 (서버가 바뀔 때 보냄) */
    static void gauge(JobSkills.GaugePayload p) {
        gaugeName = p.name();
        gaugeValue = Math.max(0, p.value());
        gaugeMax = Math.max(0, p.max());
        stance = p.left() > 0 ? p.stance() : "";
        stanceEnd = p.left() > 0 ? ticks + p.left() : 0;
        stanceTotal = Math.max(p.left(), p.total());
    }

    /** 재사용 대기 (SkillBarNet.CooldownPayload 의 이름이 "job:" 으로 시작 — ClientSkillBar 가 넘김) */
    static void cooldown(String id, int left, int total) {
        int i = ids.indexOf(id);
        if (left <= 0) {
            // 0.28.0 줄어서 끝남 (매지션 주문 연쇄): 준비 연출
            if (COOLDOWNS.remove(id) != null && i >= 0) READY_AT[i] = Util.getMillis();
            return;
        }
        Cd old = COOLDOWNS.get(id);
        COOLDOWNS.put(id, new Cd(ticks + left, Math.max(left, total)));
        // 시전 연출은 새 재사용 대기일 때만 (줄어든 재사용 대기 — 남은 틱이 짧아짐 — 는 덮개만 걷힘)
        if (i >= 0 && (old == null || old.end() <= ticks || old.end() < ticks + left)) CAST_AT[i] = Util.getMillis();
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

    /** 칸 i 의 스킬 id (없으면 "" — 아이콘 job_skill/&lt;id&gt;) */
    public static String id(int i) {
        return i < 0 || i >= SIZE ? "" : ids.get(i);
    }

    public static String name(int i) {
        return i < 0 || i >= SIZE ? "" : names.get(i);
    }

    /** 0.27.0 칸 i 의 잠김 사유 ("" = 쓸 수 있음 — "15렙" · "칼 · 마검" · "요구 스탯") */
    public static String lock(int i) {
        return i < 0 || i >= SIZE ? "" : locks.get(i);
    }

    public static boolean locked(int i) {
        return !empty(i) && !lock(i).isEmpty();
    }

    /** 0.31.0 칸 i 의 강화 단계 (0 ~ 5) */
    public static int up(int i) {
        return i < 0 || i >= SIZE ? 0 : ups[i];
    }

    /** 0.27.0 직업 id (없으면 "") */
    public static String jobId() {
        return job;
    }

    public static String jobName() {
        return jobName;
    }

    /** 직업 첫째 빛 · 둘째 빛 (0xRRGGBB — 직업이 없으면 하늘색) */
    public static int colorA() {
        return colorA;
    }

    public static int colorB() {
        return colorB;
    }

    /** 직업 자원 (검세 · 마력 각인 — max 0 = 없음) */
    public static String gaugeName() {
        return gaugeName;
    }

    public static int gaugeValue() {
        return gaugeValue;
    }

    public static int gaugeMax() {
        return gaugeMax;
    }

    /** 걸린 태세 이름 (없으면 "") */
    public static String stance() {
        return stanceEnd > ticks ? stance : "";
    }

    /** 태세 남은 초 */
    public static double stanceLeft(float partial) {
        return stanceEnd > ticks ? Math.max(0, (stanceEnd - ticks - partial) / 20.0) : 0;
    }

    /** 태세 남은 비율 (0 ~ 1) */
    public static double stanceFrac(float partial) {
        if (stanceEnd <= ticks || stanceTotal <= 0) return 0;
        return Math.max(0, Math.min(1, (stanceEnd - ticks - partial) / stanceTotal));
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
        if (empty(i) || locked(i) || cooldownLeft(i, 0) > 0.05) {
            DENY_AT[i] = now;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.4F));
        }
        ClientPlayNetworking.send(new SkillBarNet.ActionPayload("job", i, 0));
    }
}
