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
 * 시전 · 준비). 2026-11-18 축복의 길: 키 = 스킬 키 여섯 (서버 SkillKeys — 우 · ⇧우 · ⇧좌 · ⇧⇧ · Q · ⇧Q 에 칸 · 공용 스킬을 끼움, 서버가 패킷으로 받음 —
 * 예전 Shift + 룬 칸 키는 없앰). HUD 는 키 차례로 그림 ({@link #keyWhat} · 끼운 공용 스킬 {@link #commonId}).
 * 0.27.0 직업 (id · 이름 · 두 빛 — 칸 테두리 · 글자), 칸마다 잠김 사유 ("15렙" · "칼 · 마검" · "요구 스탯"), 직업 자원 게이지 (검세 · 마력 각인) · 태세 남은 시간
 * (JobSkills.GaugePayload — 남은 틱은 받은 뒤 클라이언트 틱으로 셈).
 */
public final class ClientJobBar {
    private ClientJobBar() {}

    public static final int SIZE = JobSkills.SIZE;
    /** 2026-11-18 스킬 키 수 (서버 SkillKeys.Key 차례) */
    public static final int KEYS = mmorpg.fabric.job.SkillKeys.SIZE;
    /** 키마다 끼운 것 ("j1" ~ "j5" · "c" · "") · 끼운 공용 스킬 (id · 이름 · 나무 스탯 — 없으면 "") */
    private static List<String> keys = List.of("", "", "", "", "", "");
    private static String commonId = "";
    private static String commonName = "";
    private static String commonStat = "";
    static final long[] COMMON_FX = new long[2]; // [시전, 준비] 시각

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
    /** 0.51.0 서버가 알린 공격 속도 배율 (0 = 아직 — 게이지로 셈) */
    private static double speed;
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
        for (long[] a : new long[][] {PRESS_AT, DENY_AT, CAST_AT, READY_AT, COMMON_FX}) java.util.Arrays.fill(a, 0);
        keys = List.of("", "", "", "", "", "");
        commonId = commonName = commonStat = "";
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
        List<String> nk = new ArrayList<>(KEYS);
        for (int i = 0; i < KEYS; i++) nk.add(i < p.keys().size() ? p.keys().get(i) : "");
        keys = List.copyOf(nk);
        commonId = p.common();
        commonName = p.commonName();
        commonStat = p.commonStat();
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
        speed = p.speed(); // 0.51.0 서버의 공격 속도 배율
    }

    /** 재사용 대기 (SkillBarNet.CooldownPayload 의 이름이 "job:" 으로 시작 — 스킬 id, 2026-11-18 공용 스킬은 "common:&lt;id&gt;" 그대로 — ClientSkillBar 가 넘김) */
    static void cooldown(String id, int left, int total) {
        int i = ids.indexOf(id);
        boolean common = id.startsWith(mmorpg.fabric.job.CommonSkills.CD_PREFIX);
        if (left <= 0) {
            // 0.28.0 줄어서 끝남 (매지션 주문 연쇄): 준비 연출
            if (COOLDOWNS.remove(id) != null) {
                if (i >= 0) READY_AT[i] = Util.getMillis();
                if (common) COMMON_FX[1] = Util.getMillis();
            }
            return;
        }
        Cd old = COOLDOWNS.get(id);
        COOLDOWNS.put(id, new Cd(ticks + left, Math.max(left, total)));
        // 시전 연출은 새 재사용 대기일 때만 (줄어든 재사용 대기 — 남은 틱이 짧아짐 — 는 덮개만 걷힘)
        boolean fresh = old == null || old.end() <= ticks || old.end() < ticks + left;
        if (i >= 0 && fresh) CAST_AT[i] = Util.getMillis();
        if (common && fresh) COMMON_FX[0] = Util.getMillis();
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
            if (e.getKey().startsWith(mmorpg.fabric.job.CommonSkills.CD_PREFIX)) COMMON_FX[1] = now;
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

    /**
     * 0.51.0 공격 속도 배율 (근접 평타 사이 — ClientMotions 의 선입력): 서버가 보낸 값 (게이지 payload 의 speed — 태세 · 체력 · 거인의 무게 · 불사의 분노),
     * 없으면 직업 특성에 atkSpeed (게이지 하나당) 가 있을 때 1 + atkSpeed × 게이지 값, 아니면 1
     */
    public static double attackSpeed() {
        if (speed > 0) return speed; // 0.51.0 서버가 셈 (태세 · 체력 — 소드마스터 용의 피 · 블러드 나이트 광란 · 피의 갈증)
        if (job.isEmpty() || gaugeValue <= 0) return 1;
        mmorpg.core.Jobs.Job j = mmorpg.core.Jobs.job(job);
        double per = j == null || j.trait() == null ? 0 : j.trait().def().numOr("atkSpeed", 0);
        return per > 0 ? 1 + per * Math.min(gaugeValue, Math.max(0, j.trait().max())) : 1;
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

    /** 직업 칸 i 의 키 글자 (2026-11-18 — 그 칸을 끼운 스킬 키: "우" · "⇧우" · "⇧좌" · "⇧⇧" · "Q" · "⇧Q", 키에 없으면 "") */
    public static String keyLabel(int i) {
        int k = keys.indexOf("j" + (i + 1));
        return k < 0 ? "" : mmorpg.fabric.job.SkillKeys.Key.values()[k].shortLabel;
    }

    // ------------------------------------------------------------ 2026-11-18 스킬 키 여섯

    /** 키 k 에 끼운 것 ("j1" ~ "j5" · "c" · "") */
    public static String keyWhat(int k) {
        return k < 0 || k >= KEYS ? "" : keys.get(k);
    }

    /** 키 k 의 글자 (우 · ⇧우 · ⇧좌 · ⇧⇧ · Q · ⇧Q) */
    public static String keyName(int k) {
        return k < 0 || k >= KEYS ? "" : mmorpg.fabric.job.SkillKeys.Key.values()[k].shortLabel;
    }

    /** 끼운 공용 스킬 id · 이름 · 나무 스탯 (없으면 "") */
    public static String commonId() {
        return commonId;
    }

    public static String commonName() {
        return commonName;
    }

    public static String commonStat() {
        return commonStat;
    }

    public static double commonCooldownLeft(float partial) {
        Cd c = commonId.isEmpty() ? null : COOLDOWNS.get(mmorpg.fabric.job.CommonSkills.CD_PREFIX + commonId);
        return c == null ? 0 : Math.max(0, (c.end() - ticks - partial) / 20.0);
    }

    public static double commonCooldownFrac(float partial) {
        Cd c = commonId.isEmpty() ? null : COOLDOWNS.get(mmorpg.fabric.job.CommonSkills.CD_PREFIX + commonId);
        if (c == null || c.total() <= 0) return 0;
        return Math.max(0, Math.min(1, (c.end() - ticks - partial) / c.total()));
    }
}
