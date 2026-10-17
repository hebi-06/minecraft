package mmorpg.fabric.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mmorpg.core.Gacha;
import mmorpg.core.Json;
import mmorpg.core.StatTree;
import mmorpg.fabric.gacha.GachaNet;
import mmorpg.fabric.gacha.GachaSystem;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.rune.TicketItem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 클라이언트의 룬 뽑기 (0.14.0): 서버가 보낸 상태 (한정 뽑기 설정 · 천장 기록 · 최근 기록 · 관리자인지) 와 뽑기 종류 (확률 · 천장은 core {@link Gacha} —
 * 서버와 같은 gacha.json), 뽑은 결과 → 연출 창 ({@link GachaShowScreen}). 뽑기권 수는 내 가방에서 셈.
 */
public final class ClientGacha {
    private ClientGacha() {}

    /** 창의 뽑기 종류 하나 (kind = limited · normal · advanced · stat) */
    public record Banner(String key, String name, String kind, Item ticket, Gacha.Banner banner, String pool, Gacha.Spec spec) {}

    /** 뽑은 것 하나 (서버 결과) */
    public record Pulled(String rune, String family, String grade, boolean featured, boolean lost, int at, boolean fresh) {}

    /** 뽑기 결과 (연출) */
    public record Result(long id, String banner, String name, List<Pulled> pulls) {}

    /** 최근 기록 한 줄 */
    public record Entry(long t, String banner, String rune, boolean featured, boolean lost, int at) {}

    private static boolean received;
    private static boolean admin;
    private static long offset;
    private static Gacha.Spec limited;
    private static boolean active;
    private static Map<String, Gacha.Counter> counters = Map.of();
    private static Map<String, Integer> totals = Map.of();
    private static List<Entry> history = List.of();
    private static String note = "";
    private static long noteAt;
    /** 뽑기를 보내고 결과를 기다리는 중 (이 시각까지 — 단추를 잠금) */
    private static long waitUntil;

    /** 시험용: 받은 결과 수 */
    public static int results;

    static void init() {
        ClientPlayNetworking.registerGlobalReceiver(GachaNet.StatePayload.TYPE, (payload, context) -> {
            String open = update(payload.json());
            Minecraft mc = context.client();
            if (mc.player == null) return;
            if (mc.gui.screen() instanceof GachaScreen s) {
                s.refresh(open);
            } else if (mc.gui.screen() instanceof GachaAdminScreen a) {
                a.refresh();
            } else if (open != null && !open.isEmpty() && !(mc.gui.screen() instanceof GachaShowScreen)) {
                mc.gui.setScreen(new GachaScreen(open));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(GachaNet.ResultPayload.TYPE, (payload, context) -> {
            Result r = parseResult(payload.json());
            if (r == null || context.client().player == null) return;
            results++;
            waitUntil = 0;
            Minecraft mc = context.client();
            GachaScreen parent = mc.gui.screen() instanceof GachaScreen s ? s : mc.gui.screen() instanceof GachaShowScreen sh ? sh.parent() : new GachaScreen(r.banner());
            mc.gui.setScreen(new GachaShowScreen(r, parent));
        });
    }

    /** 상태 받기 → 열 뽑기 키 ("" = 열지 않음) */
    static String update(String json) {
        Map<String, Object> m;
        try {
            m = Json.parseObject(json);
        } catch (RuntimeException e) {
            return null;
        }
        received = true;
        admin = Boolean.TRUE.equals(m.get("admin"));
        if (m.get("now") instanceof Number n) offset = n.longValue() - System.currentTimeMillis();
        limited = m.get("limited") instanceof Map<?, ?> lm ? Gacha.Spec.fromMap(lm, now()) : null;
        active = Boolean.TRUE.equals(m.get("active"));
        Map<String, Gacha.Counter> c = new LinkedHashMap<>();
        if (m.get("counters") instanceof Map<?, ?> cm) for (Map.Entry<?, ?> e : cm.entrySet()) c.put(String.valueOf(e.getKey()), Gacha.Counter.fromMap(e.getValue()));
        counters = c;
        Map<String, Integer> t = new LinkedHashMap<>();
        if (m.get("totals") instanceof Map<?, ?> tm) for (Map.Entry<?, ?> e : tm.entrySet()) if (e.getValue() instanceof Number n) t.put(String.valueOf(e.getKey()), n.intValue());
        totals = t;
        List<Entry> h = new ArrayList<>();
        if (m.get("history") instanceof List<?> l) {
            for (Object o : l) {
                if (!(o instanceof Map<?, ?> e) || e.get("r") == null) continue;
                h.add(new Entry(e.get("t") instanceof Number n ? n.longValue() : 0, String.valueOf(e.get("b")), String.valueOf(e.get("r")), Boolean.TRUE.equals(e.get("f")),
                        Boolean.TRUE.equals(e.get("l")), e.get("a") instanceof Number n ? n.intValue() : 0));
            }
        }
        history = h;
        if (m.get("note") instanceof String s && !s.isEmpty()) {
            note = s;
            noteAt = System.currentTimeMillis();
            waitUntil = 0;
        }
        return m.get("open") == null ? "" : String.valueOf(m.get("open"));
    }

    static Result parseResult(String json) {
        try {
            Map<String, Object> m = Json.parseObject(json);
            List<Pulled> pulls = new ArrayList<>();
            if (m.get("pulls") instanceof List<?> l) {
                for (Object o : l) {
                    if (!(o instanceof Map<?, ?> p)) continue;
                    pulls.add(new Pulled(String.valueOf(p.get("rune")), String.valueOf(p.get("family")), String.valueOf(p.get("grade")), Boolean.TRUE.equals(p.get("featured")),
                            Boolean.TRUE.equals(p.get("lost")), p.get("at") instanceof Number n ? n.intValue() : 0, Boolean.TRUE.equals(p.get("new"))));
                }
            }
            if (m.get("counter") instanceof Map<?, ?> cm && m.get("banner") != null) {
                String key = String.valueOf(m.get("banner"));
                String pool = GachaSystem.LIMITED.equals(key) ? GachaSystem.LIMITED : GachaSystem.NORMAL.equals(key) && Gacha.normalPity() ? GachaSystem.NORMAL : null;
                if (pool != null) {
                    Map<String, Gacha.Counter> c = new LinkedHashMap<>(counters);
                    c.put(pool, Gacha.Counter.fromMap(cm));
                    counters = c;
                }
            }
            return new Result(m.get("id") instanceof Number n ? n.longValue() : 0, String.valueOf(m.get("banner")), String.valueOf(m.get("name")), List.copyOf(pulls));
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------ 상태

    public static boolean received() {
        return received;
    }

    public static boolean admin() {
        return admin;
    }

    /** 서버 시각 (밀리초 — 기간 · 남은 때) */
    public static long now() {
        return System.currentTimeMillis() + offset;
    }

    /** 한정 뽑기 설정 (끝난 것도 — 관리자 창) */
    public static Gacha.Spec limited() {
        return limited;
    }

    public static boolean limitedActive() {
        return active && limited != null;
    }

    public static Gacha.Counter counter(String pool) {
        return pool == null ? Gacha.Counter.ZERO : counters.getOrDefault(pool, Gacha.Counter.ZERO);
    }

    public static Map<String, Integer> totals() {
        return totals;
    }

    public static List<Entry> history() {
        return history;
    }

    /** 창 아래 알림 (4초) */
    public static String note() {
        return System.currentTimeMillis() - noteAt < 4000 ? note : "";
    }

    static void clearNote() {
        note = "";
    }

    public static boolean waiting() {
        return System.currentTimeMillis() < waitUntil;
    }

    /** 뽑기 종류들 (창의 탭 — 한정은 열렸을 때만, 스탯 룬 뽑기권은 가진 것만) */
    public static List<Banner> banners() {
        List<Banner> out = new ArrayList<>();
        if (limitedActive() && RuneItems.LIMITED_TICKET != null) {
            out.add(new Banner(GachaSystem.LIMITED, limited.name(), "limited", RuneItems.LIMITED_TICKET, limited.banner(), GachaSystem.LIMITED, limited));
        }
        out.add(new Banner(GachaSystem.NORMAL, "일반 뽑기", "normal", RuneItems.TICKET, Gacha.standard(), Gacha.normalPity() ? GachaSystem.NORMAL : null, null));
        out.add(new Banner(GachaSystem.ADVANCED, "고급 뽑기", "advanced", RuneItems.ADV_TICKET, Gacha.advanced(), null, null));
        for (StatTree.Ticket t : StatTree.tickets().values()) {
            TicketItem item = RuneItems.statTicket(t.stat());
            if (item == null || t.family() == null || tickets(item) <= 0) continue;
            out.add(new Banner(GachaSystem.STAT + t.stat(), t.name(), "stat", item, Gacha.single(t.family()), null, null));
        }
        return out;
    }

    /** 내 가방 (36칸) + 왼손의 뽑기권 수 */
    public static int tickets(Item item) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || item == null) return 0;
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        if (p.getOffhandItem().is(item)) n += p.getOffhandItem().getCount();
        return n;
    }

    // ------------------------------------------------------------ 보내기

    public static void send(String action, String a, String b) {
        if (ClientPlayNetworking.canSend(GachaNet.ActionPayload.TYPE)) ClientPlayNetworking.send(new GachaNet.ActionPayload(action, a, b));
    }

    /** 뽑기 (결과가 올 때까지 단추를 잠금 — 최대 4초) */
    public static void pull(String key, int count) {
        waitUntil = System.currentTimeMillis() + 4000;
        clearNote();
        send("pull", key, String.valueOf(count));
    }

    static void clear() {
        received = false;
        admin = false;
        limited = null;
        active = false;
        counters = Map.of();
        totals = Map.of();
        history = List.of();
        note = "";
        waitUntil = 0;
    }
}
