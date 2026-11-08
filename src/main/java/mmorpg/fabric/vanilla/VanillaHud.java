package mmorpg.fabric.vanilla;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mmorpg.core.Hp;
import mmorpg.core.Js;
import mmorpg.core.Runes;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgData;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.job.JobSkills;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.rune.SkillBar;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2026-11-08 로드맵 2번 HUD (모드 없는 클라이언트 — 사용자 "다음 작업" · "스킬 Hud로도 표현해주면 안되나"): 모드 HUD (RpgHud · SkillBarHud) 와 같은
 * 그림을 액션바 글자로 그림.
 * <ul>
 * <li>체력 (하트 줄 자리): 하트 · 체력 막대 ("196 / 316") · 25% 아래 빨간 테 깜박임 · 맞으면 깎인 부분이 잠깐 남았다가 줄어듦 · 흡수 (노란 줄)</li>
 * <li>배지 (배고픔 줄 자리): 방어 · Lv · 남은 스탯 포인트</li>
 * <li>스킬 칸 (핫바 양옆 — 액션바는 화면 가운데 기준이라 오른쪽 끝에 붙일 수 없음): 오른쪽 룬 1 ~ 8 (4 × 2), 왼쪽 직업 1 ~ 5 (3 + 2).
 * 칸마다 모드 그림 (칸 틀 · 등급 / 직업 색 테 · 키 상자 + 번호) · 룬 · 직업 스킬 그림 · 재사용 대기 (위에서 덮는 어두운 덮개 + 남은 초).
 * 지금 모드 (F — 룬 · 직업) 의 키 상자는 금색, 잠긴 직업 스킬 · 탈것에 탄 동안은 그림을 어둡게. 화면 너비 424 (GUI) 이상에서 다 보임</li>
 * </ul>
 * 바닐라 하트 · 갑옷 · 배고픔 그림은 팩에서 투명 (VanillaPack — 배고픔은 게임에서 꺼져 있음).
 *
 * <p>그림 (tools/vanilla_hud.py → mmorpg/vanilla/gui/hud — 글꼴 rpg:hud · rpg:hud_r1 · rpg:hud_r2, 세로 자리는 글꼴의 ascent): 액션바 글자의 시작 x =
 * 화면 가운데 − 전체 나아감 / 2. 그림은 서로 겹쳐 그리고 (모든 HUD 글꼴에 자리 옮김 글자가 있음 — 같은 글꼴 · 색은 한 조각으로 이어 보냄) 끝에 처음 자리로
 * 돌아오므로 전체 나아감 = 알림 글자 너비 W → 그림은 가운데 기준 자리 + W / 2 에 둠. 알림 (Notify · 바닐라의 액션바 글자) 은 VanillaPackets 가
 * 가로채서 이 액션바의 글자 줄로 (갈무리 — 너비를 서버가 셈, 3초). 바뀐 것이 있을 때 · 30틱마다 다시 보냄 (바닐라 액션바는 60틱 뒤 사라지고
 * 40틱부터 흐려짐).
 */
public final class VanillaHud {
    private VanillaHud() {}

    static final Identifier FONT = Mmorpg.rpg("hud");
    /** 글자 글꼴 (줄 글꼴과 같은 글자판 — 액션바 글자 줄 기준 칸 위 = 화면 아래 − 75 + d): 막대 글자 · 배지 글자 · 알림 줄 */
    public static final int BAR_D = 29, BADGE_D = 30, MSG_D = 1;
    /** 스킬 칸 줄 (위 · 아래) 의 칸 위 = 화면 아래 − ROW_K (tools/vanilla_hud.py SKILL_ROWS) */
    static final int[] ROW_K = {39, 20};
    /** 남은 초 글자 (글자판 — 칸 위 + 2 = 77 − k, 그림자는 1 아래) */
    static final int[] SEC_D = {77 - ROW_K[0], 77 - ROW_K[1]};
    static final int[] TEXT_D = {BAR_D, BADGE_D, MSG_D, SEC_D[0], SEC_D[0] + 1, SEC_D[1], SEC_D[1] + 1};
    /** 숫자 글꼴 (작은 숫자판 — 칸 위 = 화면 아래 − 75 + d): 키 번호 (칸 위 − 2 = 73 − k), 줄마다 */
    static final int[] NUM_D = {73 - ROW_K[0], 73 - ROW_K[1]};
    /** 칸 크기 · 칸 사이 (칸 + 1) · 룬 칸 왼쪽 끝 (가운데 + RUNE_X) · 직업 칸 오른쪽 끝 (가운데 − JOB_RIGHT) */
    static final int SLOT = 18, PITCH = 19, RUNE_X = 137, JOB_RIGHT = 137;
    /** 알림을 보여 주는 틱 (바닐라 액션바와 같음) */
    static final int MSG_TICKS = 60;
    /** 바뀐 것이 없어도 다시 보내는 틱 */
    static final int KEEP = 30;
    /** 채움 최대 너비 (tools/vanilla_hud.py FILL_W) */
    static final int FILL_W = 94;
    static final int WHITE = 0xFFFFFF, GRAY = 0xAAAAAA, GOLD = 0xFFCC52, YELLOW = 0xFFE066, ABSORB = 0xFFD24A,
            TRAIL = 0xD4A86A, // 깎인 부분: 크림색은 흰 글자가 묻힘 (CI 42441254551 사진) → 어두운 살구색
            KEY_ON = 0xFFD678, KEY_OFF = 0x9A9CB4, DIM = 0x5A5A66, EMPTY_EDGE = 0x4A4C60;

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    /** 사람 → 재사용 대기 (이름 → {끝나는 틱, 전체 틱}) — SkillBarNet.cooldown (룬 = 룬 id, 직업 = "job:" + 스킬 id) */
    private static final Map<UUID, Map<String, long[]>> COOLDOWNS = new ConcurrentHashMap<>();
    /** 이 HUD 가 보내는 액션바는 VanillaPackets 가 가로채지 않음 */
    private static final ThreadLocal<Boolean> SENDING = ThreadLocal.withInitial(() -> false);
    /** 이름 → {글자, 나아감} (체력 그림: rpg:hud, 스킬 칸 그림: 줄 글꼴 rpg:hud_r1 · rpg:hud_r2 — 글자는 같음) */
    private static Map<String, String[]> glyphs;
    private static Map<String, String[]> skillGlyphs;
    private static int[] strips = {64, 32, 16, 8, 4, 2, 1};
    private static int[] lvWidths = {34, 38, 42, 46};

    /** 한 사람의 HUD 상태 (깎인 부분 · 알림 · 마지막으로 보낸 것) */
    static final class State {
        float trail = -1;
        float last = -1;
        int hold;
        String msg = "";
        long msgUntil;
        long sent = -1000;
        String key = "";
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) tick(sp);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            STATES.remove(handler.player.getUUID());
            COOLDOWNS.remove(handler.player.getUUID());
        });
    }

    /** 이 사람에게 HUD 를 그릴지 (모드 없는 클라이언트 · 생존 · 모험 · 살아 있음 · 룬 뽑기 연출 중이 아님) */
    public static boolean active(ServerPlayer sp) {
        return sp != null && !sp.isRemoved() && VanillaClients.isVanilla(sp) && !sp.isSpectator() && !sp.isCreative() && sp.isAlive()
                && !GachaCinematic.active(sp);
    }

    /**
     * VanillaPackets: 액션바로 가는 글자 (알림 · 상태 줄 · 바닐라 것). HUD 를 그리는 사람이면 알림 줄로 넣고 HUD 와 합친 패킷을 돌려줌 (바꿔 보냄),
     * 아니면 null (그대로).
     */
    public static Packet<?> overlay(ServerPlayer sp, Component text) {
        if (SENDING.get() || !active(sp)) return null;
        State st = STATES.computeIfAbsent(sp.getUUID(), k -> new State());
        st.msg = text == null ? "" : text.getString();
        st.msgUntil = Tasks.now() + MSG_TICKS;
        return packet(sp, st, true);
    }

    /**
     * 재사용 대기 (SkillBarNet.cooldown — 모드 없는 클라이언트도): 이름 = 룬 id · "job:" + 직업 스킬 id, "*" = 룬 재사용 대기를 모두 지움.
     * left 0 = 끝 (덮개를 걷음)
     */
    public static void cooldown(ServerPlayer sp, String name, int left, int total) {
        if (sp == null || name == null || !VanillaClients.isVanilla(sp)) return;
        Map<String, long[]> m = COOLDOWNS.computeIfAbsent(sp.getUUID(), k -> new ConcurrentHashMap<>());
        if (name.equals("*")) {
            m.keySet().removeIf(k -> !k.startsWith(JobSkills.CD_PREFIX));
            return;
        }
        if (left <= 0) m.remove(name);
        else m.put(name, new long[]{Tasks.now() + left, Math.max(left, total)});
    }

    /** 시험 · 사진: 지금 보이는 알림 글자 */
    public static String message(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null || st.msgUntil <= Tasks.now() ? "" : st.msg;
    }

    /** 시험: 이름의 남은 재사용 대기 (틱, 없으면 0) — HUD 가 아는 값 */
    public static long cooldownLeft(ServerPlayer sp, String name) {
        Map<String, long[]> m = COOLDOWNS.get(sp.getUUID());
        long[] c = m == null ? null : m.get(name);
        return c == null ? 0 : Math.max(0, c[0] - Tasks.now());
    }

    static void tick(ServerPlayer sp) {
        if (!active(sp)) {
            State st = STATES.get(sp.getUUID());
            if (st != null) {
                st.trail = st.last = -1;
                st.key = "";
            }
            return;
        }
        State st = STATES.computeIfAbsent(sp.getUUID(), k -> new State());
        float max = sp.getMaxHealth();
        float ratio = max > 0 ? Math.max(0, Math.min(1, sp.getHealth() / max)) : 0;
        if (st.last < 0 || st.trail < ratio) st.trail = ratio;
        if (ratio < st.last - 1e-4) st.hold = 10;
        st.last = ratio;
        if (st.hold > 0) st.hold--;
        else if (st.trail > ratio) st.trail = Math.max(ratio, st.trail - 0.02F);
        Packet<?> p = packet(sp, st, false);
        if (p == null) return;
        SENDING.set(true);
        try {
            sp.connection.send(p);
        } finally {
            SENDING.set(false);
        }
    }

    /** 스킬 칸 하나 (그릴 것): 그림 이름 (없으면 null = 빈 칸) · 테 색 · 그림 색 · 덮개 줄 수 (0 ~ 16) · 남은 초 (0 = 없음) */
    record Slot(String icon, int edge, int tint, int cover, int secs) {
        String key() {
            return icon + "," + Integer.toHexString(edge) + "," + Integer.toHexString(tint) + "," + cover + "," + secs;
        }
    }

    /** 이번 HUD 액션바 — 바뀐 것이 없고 KEEP 틱이 안 지났으면 null (force = 알림이 새로 옴) */
    static Packet<?> packet(ServerPlayer sp, State st, boolean force) {
        long now = Tasks.now();
        float hp = sp.getHealth();
        float max = sp.getMaxHealth();
        float ratio = max > 0 ? Math.max(0, Math.min(1, hp / max)) : 0;
        int pct = Math.max(0, Math.min(100, (int) Math.floor(ratio * 100)));
        if (hp > 0 && pct == 0) pct = 1;
        int fill = Math.round(FILL_W * pct / 100F);
        int trailW = st.trail > ratio ? Math.round(FILL_W * st.trail) : 0;
        float absorb = sp.getAbsorptionAmount();
        int absorbW = absorb > 0 && max > 0 ? Math.min(FILL_W, Math.round(FILL_W * absorb / max)) : 0;
        boolean warn = pct > 0 && pct < 26 && (now / 10) % 2 == 0;
        String hpText = Hp.hpNumber(!sp.isAlive(), hp);
        String maxText = " / " + Js.str(Js.round(max * 10) / 10.0) + (absorb > 0 ? " +" + Js.str(Js.round(absorb * 10) / 10.0) : "");
        int armor = Math.min(99, sp.getArmorValue());
        RpgData d = RpgData.of(sp);
        int level = Math.min(999, Math.max(d.lvmax(), sp.experienceLevel));
        int points = d.point();
        String msg = st.msgUntil > now ? st.msg : "";
        VanillaInput.Mode mode = VanillaInput.mode(sp);
        Slot[] runes = runeSlots(sp, now);
        Slot[] jobs = jobSlots(sp, now);
        StringBuilder key = new StringBuilder().append(fill).append('|').append(trailW).append('|').append(absorbW).append('|').append(warn).append('|')
                .append(hpText).append(maxText).append('|').append(armor).append('|').append(level).append('|').append(points).append('|').append(mode)
                .append('|').append(msg);
        for (Slot s : runes) key.append('|').append(s.key());
        for (Slot s : jobs) key.append('|').append(s.key());
        String k = key.toString();
        if (!force && k.equals(st.key) && now - st.sent < KEEP) return null;
        st.key = k;
        st.sent = now;
        return new ClientboundSetActionBarTextPacket(build(fill, trailW, absorbW, warn, hpText, maxText, armor, level, points, mode, runes, jobs, msg));
    }

    /** 룬 칸 8 (SkillBar): 그림 = 룬 그림 (아이템 모델 이름 — 룬 텍스처와 같음), 테 = 등급 색 (모드 SkillBarHud.gradeColor) */
    static Slot[] runeSlots(ServerPlayer sp, long now) {
        Slot[] out = new Slot[SkillBar.SIZE];
        boolean riding = sp.isPassenger();
        for (int i = 0; i < SkillBar.SIZE; i++) {
            ItemStack st = SkillBar.get(sp, i);
            Runes.Rune r = st.isEmpty() ? null : RuneItems.runeOf(st);
            if (r == null) {
                out[i] = new Slot(null, EMPTY_EDGE, WHITE, 0, 0);
                continue;
            }
            Identifier model = st.get(DataComponents.ITEM_MODEL);
            String icon = "rune:" + (model != null ? model.getPath() : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath());
            int[] cd = cover(sp, r.id(), now);
            out[i] = new Slot(icon, gradeColor(r.grade()), riding ? DIM : WHITE, cd[0], cd[1]);
        }
        return out;
    }

    /** 직업 칸 5 (JobSkills): 그림 = 직업 스킬 그림, 테 = 직업 색, 잠긴 칸은 어둡게 (재사용 대기 없음) */
    static Slot[] jobSlots(ServerPlayer sp, long now) {
        Slot[] out = new Slot[JobSkills.SIZE];
        JobSkills.Header h = JobSkills.header.apply(sp);
        int color = h == null ? EMPTY_EDGE : h.colorA() & 0xFFFFFF;
        boolean riding = sp.isPassenger();
        for (int i = 0; i < JobSkills.SIZE; i++) {
            JobSkills.Skill s = JobSkills.skill(sp, i);
            if (s == null) {
                out[i] = new Slot(null, EMPTY_EDGE, WHITE, 0, 0);
                continue;
            }
            String lock = s.lock(sp);
            boolean locked = lock != null && !lock.isEmpty();
            int[] cd = locked ? new int[]{0, 0} : cover(sp, JobSkills.CD_PREFIX + s.id(), now);
            out[i] = new Slot("job:" + VanillaInput.iconId(s.id()), color, locked || riding ? DIM : WHITE, cd[0], cd[1]);
        }
        return out;
    }

    /** 재사용 대기 덮개 {줄 수 1 ~ 16 (0 = 없음), 남은 초 (올림)} */
    static int[] cover(ServerPlayer sp, String name, long now) {
        Map<String, long[]> m = COOLDOWNS.get(sp.getUUID());
        long[] c = m == null ? null : m.get(name);
        if (c == null) return new int[]{0, 0};
        long left = c[0] - now;
        if (left <= 0) {
            m.remove(name);
            return new int[]{0, 0};
        }
        int rows = (int) Math.max(1, Math.min(16, Math.ceil(16.0 * left / Math.max(1, c[1]))));
        return new int[]{rows, (int) Math.ceil(left / 20.0)};
    }

    /** 등급 색 (모드 SkillBarHud.gradeColor 와 같음) */
    static int gradeColor(String grade) {
        return switch (grade == null ? "" : grade) {
            case "rare" -> 0x4C8BFF;
            case "noble" -> 0xB66CFF;
            case "hero" -> 0xFF9A2E;
            case "transcend" -> 0xFF4A3D;
            default -> 0xC8C8C8;
        };
    }

    /** HUD 글자 (가운데 기준 자리 → + W / 2) + 알림 글자 */
    static Component build(int fill, int trailW, int absorbW, boolean warn, String hpText, String maxText, int armor, int level, int points,
                           VanillaInput.Mode mode, Slot[] runes, Slot[] jobs, String msg) {
        String m = drawableKeep(msg);
        int base = VanillaGui.width(m) / 2;
        Line l = new Line();
        // 체력 (왼쪽 — 하트 줄 자리)
        l.glyph(FONT, base - 123, "hp_icon", WHITE);
        l.glyph(FONT, base - 109, "hp_frame", WHITE);
        if (trailW > fill) l.strip(base - 108 + fill, trailW - fill, "trail", TRAIL);
        if (fill > 0) l.glyph(FONT, base - 108, "fill_" + Math.min(FILL_W, fill), WHITE);
        if (absorbW > 0) l.strip(base - 108, absorbW, "absorb", ABSORB);
        if (warn) l.glyph(FONT, base - 111, "hp_warn", WHITE);
        int tw = VanillaGui.width(hpText) + VanillaGui.width(maxText);
        int tx = base - 61 - tw / 2;
        l.text(textFont(BAR_D), tx, hpText, WHITE);
        l.text(textFont(BAR_D), tx + VanillaGui.width(hpText), maxText, GRAY);
        // 방어 · 레벨 · 포인트 (오른쪽 — 배고픔 줄 자리)
        l.glyph(FONT, base + 12, "armor_badge", WHITE);
        l.glyph(FONT, base + 15, "armor_icon", WHITE);
        l.text(textFont(BADGE_D), base + 26, String.valueOf(armor), WHITE);
        String lv = String.valueOf(level);
        int need = Math.max(34, 8 + VanillaGui.width("Lv") + 2 + VanillaGui.width(lv));
        int lw = lvWidths[lvWidths.length - 1];
        for (int w : lvWidths) {
            if (w >= need) {
                lw = w;
                break;
            }
        }
        int lx = base + 44;
        l.glyph(FONT, lx, "lv_badge_" + lw, WHITE);
        l.text(textFont(BADGE_D), lx + 4, "Lv", GOLD);
        l.text(textFont(BADGE_D), lx + 6 + VanillaGui.width("Lv"), lv, WHITE);
        if (points > 0) {
            int qx = lx + lw + 3;
            l.glyph(FONT, qx, "point", WHITE);
            l.text(textFont(BADGE_D), qx + 17, "+" + points, YELLOW);
        }
        // 스킬 칸: 오른쪽 룬 1 ~ 8 (4 × 2) · 왼쪽 직업 1 ~ 5 (위 3 · 아래 2, 오른쪽 맞춤)
        for (int i = 0; i < runes.length; i++) {
            slot(l, base + RUNE_X + (i % 4) * PITCH, i / 4, i + 1, runes[i], false, mode == VanillaInput.Mode.RUNE);
        }
        for (int i = 0; i < jobs.length; i++) {
            int row = i < 3 ? 0 : 1;
            int col = i < 3 ? i : i - 2; // 아래 줄 2칸은 오른쪽 둘 자리
            slot(l, base - JOB_RIGHT - 3 * PITCH + 1 + col * PITCH, row, i + 1, jobs[i], true, mode == VanillaInput.Mode.JOB);
        }
        // 알림 줄 (처음 자리부터 — 가운데 맞춤)
        l.move(0);
        if (!m.isEmpty()) l.text(textFont(MSG_D), 0, m, WHITE);
        return l.build();
    }

    /** 칸 하나: 틀 · 테 · 그림 · 덮개 · 남은 초 · 키 상자 + 번호 (x = 칸 왼쪽, row 0 = 위 줄) */
    private static void slot(Line l, int x, int row, int number, Slot s, boolean job, boolean on) {
        Identifier f = Mmorpg.rpg(row == 0 ? "hud_r1" : "hud_r2");
        l.skill(f, x, job ? "job_frame" : "skill_frame", WHITE);
        if (s.icon() != null) l.skill(f, x + 1, s.icon(), s.tint());
        if (s.cover() > 0) l.skill(f, x + 1, "cover_" + s.cover(), WHITE);
        l.skill(f, x, job ? "job_edge" : "skill_edge", s.edge());
        if (s.secs() > 0) { // 남은 초 (갈무리9 · 1px 그림자 — CI 42444726662 사진: 작은 숫자판은 그림 위에서 잘 안 보임)
            String t = String.valueOf(s.secs());
            int tx = x + 1 + (17 - VanillaGui.width(t)) / 2;
            l.text(textFont(SEC_D[row] + 1), tx + 1, t, 0x101018);
            l.text(textFont(SEC_D[row]), tx, t, WHITE);
        }
        l.skill(f, x - 1, "key", on ? KEY_ON : KEY_OFF);
        l.text(numFont(73 - ROW_K[row]), x + 1, String.valueOf(number), on ? KEY_ON : KEY_OFF);
    }

    static Identifier textFont(int d) {
        return Mmorpg.rpg("hud_t" + d);
    }

    static Identifier numFont(int d) {
        return Mmorpg.rpg("hud_n" + d);
    }

    /**
     * 액션바 한 줄 만들기 (처음 자리 = 0). 같은 글꼴 · 색이 이어지면 한 조각으로 (모든 HUD 글꼴에 자리 옮김 글자 U+F801 ~ · U+F811 ~ 가 있음 —
     * VanillaPack) — 조각 수 · 패킷 크기를 줄임.
     */
    static final class Line {
        final MutableComponent root = Component.empty().withStyle(Style.EMPTY.withColor(WHITE));
        int cursor;
        private Identifier font;
        private int color;
        private final StringBuilder run = new StringBuilder();

        private void flush() {
            if (run.length() == 0) return;
            root.append(Component.literal(run.toString()).withStyle(Style.EMPTY.withFont(new FontDescription.Resource(font)).withColor(color)));
            run.setLength(0);
        }

        private void use(Identifier f, int rgb) {
            if (f.equals(font) && rgb == color) return;
            flush();
            font = f;
            color = rgb;
        }

        void move(int x) {
            if (x == cursor) return;
            if (font == null) use(FONT, WHITE);
            run.append(VanillaGui.spaces(x - cursor));
            cursor = x;
        }

        void glyph(Identifier f, int x, String name, int rgb) {
            put(f, x, glyphs().get(name), rgb);
        }

        void skill(Identifier f, int x, String name, int rgb) {
            glyphs();
            put(f, x, skillGlyphs.get(name), rgb);
        }

        private void put(Identifier f, int x, String[] g, int rgb) {
            if (g == null) return;
            use(f, rgb);
            move(x);
            run.append(g[0]);
            cursor += Integer.parseInt(g[1]);
        }

        /** 흰 조각 (trail · absorb — 1 · 2 · 4 … 64) 으로 너비 w 를 채움 (조각마다 나아감 = 너비 + 1 이라 1 씩 되돌림) */
        void strip(int x, int w, String kind, int rgb) {
            int at = x;
            int left = w;
            for (int s : strips) {
                while (left >= s) {
                    glyph(FONT, at, kind + "_" + s, rgb);
                    at += s;
                    left -= s;
                }
            }
        }

        /** 글자 (§ 서식이 있으면 그 조각을 끝냄 — 뒤 그림에 색이 번지지 않게) */
        void text(Identifier f, int x, String s, int rgb) {
            if (s == null || s.isEmpty()) return;
            String t = drawableKeep(s);
            use(f, rgb);
            move(x);
            run.append(t);
            cursor += f.getPath().startsWith("hud_n") ? VanillaGui.numWidth(t) : VanillaGui.width(t);
            if (t.indexOf('§') >= 0) {
                flush();
                font = null;
            }
        }

        Component build() {
            flush();
            return root;
        }
    }

    /** 글자판에 없는 글자는 빈칸으로 (§ 서식은 그대로 — 색, 굵게 · 뒤섞기는 뺌: 나아감이 바뀌어 가운데가 어긋남) */
    static String drawableKeep(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (cp == '§' && i + 1 < s.length()) {
                char f = Character.toLowerCase(s.charAt(i + 1));
                if (f != 'l' && f != 'k') out.append('§').append(s.charAt(i + 1));
                i += 2;
                continue;
            }
            out.append(VanillaGui.drawable(new String(Character.toChars(cp))));
            i += Character.charCount(cp);
        }
        return out.toString();
    }

    /** 그림 글자 표 (hud.json glyphs · skill_glyphs — 이름 → {글자, 나아감}) */
    private static synchronized Map<String, String[]> glyphs() {
        if (glyphs != null) return glyphs;
        Map<String, String[]> m = new HashMap<>();
        Map<String, String[]> sk = new HashMap<>();
        try (InputStream in = VanillaHud.class.getResourceAsStream("/mmorpg/vanilla/gui/hud/hud.json")) {
            if (in != null) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                read(o.getAsJsonObject("glyphs"), m);
                if (o.has("skill_glyphs")) read(o.getAsJsonObject("skill_glyphs"), sk);
                if (o.has("strips")) {
                    List<Integer> s = new java.util.ArrayList<>();
                    for (com.google.gson.JsonElement x : o.getAsJsonArray("strips")) s.add(x.getAsInt());
                    s.sort(java.util.Comparator.reverseOrder());
                    strips = s.stream().mapToInt(Integer::intValue).toArray();
                }
                if (o.has("lv_widths")) {
                    List<Integer> s = new java.util.ArrayList<>();
                    for (com.google.gson.JsonElement x : o.getAsJsonArray("lv_widths")) s.add(x.getAsInt());
                    s.sort(null);
                    lvWidths = s.stream().mapToInt(Integer::intValue).toArray();
                }
            }
        } catch (Exception e) {
            Mmorpg.LOG.warn("[vanilla] hud glyphs: {}", e.toString());
        }
        skillGlyphs = sk;
        glyphs = m;
        return m;
    }

    private static void read(JsonObject o, Map<String, String[]> into) {
        for (Map.Entry<String, com.google.gson.JsonElement> e : o.entrySet()) {
            com.google.gson.JsonArray a = e.getValue().getAsJsonArray();
            into.put(e.getKey(), new String[]{a.get(0).getAsString(), a.get(1).getAsString()});
        }
    }
}
