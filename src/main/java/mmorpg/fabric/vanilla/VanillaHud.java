package mmorpg.fabric.vanilla;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mmorpg.core.Hp;
import mmorpg.core.Js;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgData;
import mmorpg.fabric.Tasks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2026-11-08 로드맵 2번 HUD (모드 없는 클라이언트 — 사용자 "다음 작업"): 모드 HUD (RpgHud) 와 같은 그림을 액션바 글자로 그림 —
 * 하트 · 체력 막대 ("196 / 316") · 25% 아래 빨간 테 깜박임 · 맞으면 깎인 부분이 잠깐 밝게 남았다가 줄어듦 · 흡수 (노란 줄) ·
 * 방어 배지 · Lv 배지 · 남은 스탯 포인트. 바닐라 하트 · 갑옷 · 배고픔 그림은 팩에서 투명 (VanillaPack — 배고픔은 게임에서 꺼져 있음).
 *
 * <p>그림 (tools/vanilla_hud.py → mmorpg/vanilla/gui/hud — 글꼴 rpg:hud, 세로 자리는 글꼴의 ascent 로 정해짐): 액션바 글자의 시작 x =
 * 화면 가운데 − 전체 나아감 / 2. 그림은 서로 겹쳐 그리고 (자리 옮김 rpg:gui_space) 끝에 처음 자리로 돌아오므로 전체 나아감 = 알림 글자 너비 W →
 * 그림은 가운데 기준 자리 + W / 2 에 둠. 알림 (Notify · 바닐라의 액션바 글자) 은 VanillaPackets 가 가로채서 이 액션바의 글자 줄로
 * (갈무리 — 너비를 서버가 셈, 3초). 바뀐 것이 있을 때 · 30틱마다 다시 보냄 (바닐라 액션바는 60틱 뒤 사라지고 40틱부터 흐려짐).
 */
public final class VanillaHud {
    private VanillaHud() {}

    static final Identifier FONT = Mmorpg.rpg("hud");
    /** 글자 글꼴 (줄 글꼴과 같은 글자판 — 액션바 글자 줄 기준 칸 위 = 화면 아래 − 75 + d): 막대 글자 · 배지 글자 · 알림 줄 */
    public static final int BAR_D = 29, BADGE_D = 30, MSG_D = 1;
    static final int[] TEXT_D = {BAR_D, BADGE_D, MSG_D};
    /** 알림을 보여 주는 틱 (바닐라 액션바와 같음) */
    static final int MSG_TICKS = 60;
    /** 바뀐 것이 없어도 다시 보내는 틱 */
    static final int KEEP = 30;
    /** 채움 최대 너비 (tools/vanilla_hud.py FILL_W) */
    static final int FILL_W = 94;
    static final int WHITE = 0xFFFFFF, GRAY = 0xAAAAAA, GOLD = 0xFFCC52, YELLOW = 0xFFE066, TRAIL = 0xD4A86A, ABSORB = 0xFFD24A; // 깎인 부분: 크림색은 흰 글자가 묻힘 (CI 42441254551 사진) → 어두운 살구색

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    /** 이 HUD 가 보내는 액션바는 VanillaPackets 가 가로채지 않음 */
    private static final ThreadLocal<Boolean> SENDING = ThreadLocal.withInitial(() -> false);
    private static Map<String, String[]> glyphs;
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
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.player.getUUID()));
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

    /** 시험 · 사진: 지금 보이는 알림 글자 */
    public static String message(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null || st.msgUntil <= Tasks.now() ? "" : st.msg;
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
        String key = fill + "|" + trailW + "|" + absorbW + "|" + warn + "|" + hpText + maxText + "|" + armor + "|" + level + "|" + points + "|" + msg;
        if (!force && key.equals(st.key) && now - st.sent < KEEP) return null;
        st.key = key;
        st.sent = now;
        return new ClientboundSetActionBarTextPacket(build(fill, trailW, absorbW, warn, hpText, maxText, armor, level, points, msg));
    }

    /** HUD 글자 (가운데 기준 자리 → + W / 2) + 알림 글자 */
    static Component build(int fill, int trailW, int absorbW, boolean warn, String hpText, String maxText, int armor, int level, int points, String msg) {
        String m = drawableKeep(msg);
        int base = VanillaGui.width(m) / 2;
        Line l = new Line();
        // 체력 (왼쪽 — 하트 줄 자리)
        l.glyph(base - 123, "hp_icon", WHITE);
        l.glyph(base - 109, "hp_frame", WHITE);
        if (trailW > fill) l.strip(base - 108 + fill, trailW - fill, "trail", TRAIL);
        if (fill > 0) l.glyph(base - 108, "fill_" + Math.min(FILL_W, fill), WHITE);
        if (absorbW > 0) l.strip(base - 108, absorbW, "absorb", ABSORB);
        if (warn) l.glyph(base - 111, "hp_warn", WHITE);
        int tw = VanillaGui.width(hpText) + VanillaGui.width(maxText);
        int tx = base - 61 - tw / 2;
        l.text(tx, BAR_D, hpText, WHITE);
        l.text(tx + VanillaGui.width(hpText), BAR_D, maxText, GRAY);
        // 방어 · 레벨 · 포인트 (오른쪽 — 배고픔 줄 자리)
        l.glyph(base + 12, "armor_badge", WHITE);
        l.glyph(base + 15, "armor_icon", WHITE);
        l.text(base + 26, BADGE_D, String.valueOf(armor), WHITE);
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
        l.glyph(lx, "lv_badge_" + lw, WHITE);
        l.text(lx + 4, BADGE_D, "Lv", GOLD);
        l.text(lx + 6 + VanillaGui.width("Lv"), BADGE_D, lv, WHITE);
        if (points > 0) {
            int qx = lx + lw + 3;
            l.glyph(qx, "point", WHITE);
            l.text(qx + 17, BADGE_D, "+" + points, YELLOW);
        }
        // 알림 줄 (처음 자리부터 — 가운데 맞춤)
        l.move(0);
        if (!m.isEmpty()) l.text(0, MSG_D, m, WHITE);
        return l.root;
    }

    /** 액션바 한 줄 만들기 (처음 자리 = 0 — 자리 옮김 rpg:gui_space) */
    static final class Line {
        final MutableComponent root = Component.empty().withStyle(Style.EMPTY.withColor(WHITE));
        int cursor;

        void move(int x) {
            if (x == cursor) return;
            root.append(Component.literal(VanillaGui.spaces(x - cursor)).withStyle(Style.EMPTY.withFont(new FontDescription.Resource(VanillaGui.SPACE))));
            cursor = x;
        }

        void glyph(int x, String name, int rgb) {
            String[] g = glyphs().get(name);
            if (g == null) return;
            move(x);
            root.append(Component.literal(g[0]).withStyle(Style.EMPTY.withFont(new FontDescription.Resource(FONT)).withColor(rgb)));
            cursor += Integer.parseInt(g[1]);
        }

        /** 흰 조각 (trail · absorb — 1 · 2 · 4 … 64) 으로 너비 w 를 채움 (조각마다 나아감 = 너비 + 1 이라 1 씩 되돌림) */
        void strip(int x, int w, String kind, int rgb) {
            int at = x;
            int left = w;
            for (int s : strips) {
                while (left >= s) {
                    glyph(at, kind + "_" + s, rgb);
                    at += s;
                    left -= s;
                }
            }
        }

        void text(int x, int d, String s, int rgb) {
            if (s == null || s.isEmpty()) return;
            String t = drawableKeep(s);
            move(x);
            root.append(Component.literal(t).withStyle(Style.EMPTY.withFont(new FontDescription.Resource(Mmorpg.rpg("hud_t" + d))).withColor(rgb)));
            cursor += VanillaGui.width(t);
        }
    }

    /** 글자판에 없는 글자는 빈칸으로 (§ 서식은 그대로 — 색) */
    static String drawableKeep(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (cp == '§' && i + 1 < s.length()) {
                char f = Character.toLowerCase(s.charAt(i + 1));
                if (f != 'l' && f != 'k') out.append('§').append(s.charAt(i + 1)); // 굵게 · 뒤섞기는 뺌 (나아감이 바뀌어 가운데가 어긋남)
                i += 2;
                continue;
            }
            out.append(VanillaGui.drawable(new String(Character.toChars(cp))));
            i += Character.charCount(cp);
        }
        return out.toString();
    }

    /** 그림 글자 표 (hud.json glyphs — 이름 → {글자, 나아감}) */
    private static synchronized Map<String, String[]> glyphs() {
        if (glyphs != null) return glyphs;
        Map<String, String[]> m = new HashMap<>();
        try (InputStream in = VanillaHud.class.getResourceAsStream("/mmorpg/vanilla/gui/hud/hud.json")) {
            if (in != null) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, com.google.gson.JsonElement> e : o.getAsJsonObject("glyphs").entrySet()) {
                    com.google.gson.JsonArray a = e.getValue().getAsJsonArray();
                    m.put(e.getKey(), new String[]{a.get(0).getAsString(), a.get(1).getAsString()});
                }
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
        glyphs = m;
        return m;
    }
}
