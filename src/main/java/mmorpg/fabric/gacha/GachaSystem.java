package mmorpg.fabric.gacha;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import mmorpg.core.Gacha;
import mmorpg.core.Json;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Runes;
import mmorpg.core.StatTree;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Props;
import mmorpg.fabric.RpgNet;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneData;
import mmorpg.fabric.rune.RuneGacha;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.TicketItem;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.ui.Inv;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 룬 뽑기 (0.14.0 — 확률 · 천장은 {@link Gacha}):
 * <ul>
 * <li>뽑기 종류: 한정 (관리자가 연 것 — 한정 룬 뽑기권, 픽업 초월 · 영웅 반천장 · 확천장, 천장) · 일반 (룬 뽑기권 — 룬 85종 모두, 천장) ·
 * 고급 (고급 룬 뽑기권 — 일반 룬 없음) · 스탯 (스탯 룬 뽑기권 — 그 계열 5종). 1회 · 10회</li>
 * <li>뽑기 창 (클라이언트 GachaScreen — 스탯 창 뽑기 단추 · 뽑기권 우클릭 · /뽑기): 뽑으면 뽑기권을 내고 룬을 바로 주고 결과를 보냄 → 연출.
 * 영웅 이상은 연출에서 결과가 보일 때 (revealed — 늦어도 몇 초 뒤) 모두에게 알림 · 둘레에 빛기둥</li>
 * <li>모드가 없는 클라이언트 (뽑기 창을 못 엶): 뽑기권 우클릭은 예전처럼 눈앞 마법진으로 1회 ({@link RuneGacha#useTicket}) — 확률 · 천장은 같음</li>
 * <li>기록: 플레이어 속성 rpg:gacha {"pools": {normal · limited: 천장}, "totals", "seen" (뽑아 본 룬 — NEW 표시), "history" (최근)},
 * 한정 뽑기 설정은 세계 속성 rpg:gacha_limited ({@link Gacha.Spec}) — 한정 뽑기끼리 천장 · 확정은 이어짐</li>
 * </ul>
 */
public final class GachaSystem {
    private GachaSystem() {}

    public static final String DATA = "rpg:gacha";
    public static final String LIMITED_DATA = "rpg:gacha_limited";
    public static final String NORMAL = "normal";
    public static final String ADVANCED = "advanced";
    public static final String LIMITED = "limited";
    public static final String STAT = "stat_";
    /** 창에서 뽑기 사이의 최소 틱 (패킷을 마구 보내도) */
    private static final int THROTTLE = 6;
    private static final List<String> GRADES = Runes.GRADE_ORDER;

    /** 확률 굴림 ([0, 1) — 시험이 바꿀 수 있음) */
    public static DoubleSupplier rand = Kit::rand;

    /** 뽑기 종류 하나: 키 · 이름 · 뽑기권 · 확률과 천장 · 천장 기록 이름 (null = 천장 없음) · 한정 설정 (한정만) */
    public record Def(String key, String name, TicketItem ticket, Gacha.Banner banner, String pool, Gacha.Spec spec) {}

    /** 뽑은 것 하나 (기록): 때 · 뽑기 키 · 룬 · 픽업 · 놓침 · 몇 번째 */
    public record Entry(long t, String banner, String rune, boolean featured, boolean lost, int at) {
        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("t", (double) t);
            m.put("b", banner);
            m.put("r", rune);
            if (featured) m.put("f", true);
            if (lost) m.put("l", true);
            if (at > 0) m.put("a", at);
            return m;
        }

        static Entry fromMap(Object o) {
            if (!(o instanceof Map<?, ?> m)) return null;
            Object r = m.get("r");
            if (r == null) return null;
            return new Entry(m.get("t") instanceof Number n ? n.longValue() : 0, String.valueOf(m.get("b")), String.valueOf(r), Boolean.TRUE.equals(m.get("f")),
                    Boolean.TRUE.equals(m.get("l")), m.get("a") instanceof Number n ? n.intValue() : 0);
        }
    }

    /** 플레이어 기록 */
    public record State(Map<String, Gacha.Counter> pools, Map<String, Integer> totals, Set<String> seen, List<Entry> history) {}

    /** 뽑기 결과 (error 가 있으면 실패 — 아무것도 바뀌지 않음) */
    public record Drawn(String error, Def def, List<Gacha.Pull> pulls, List<Boolean> fresh, Gacha.Counter counter, long id) {
        static Drawn fail(String error) {
            return new Drawn(error, null, List.of(), List.of(), Gacha.Counter.ZERO, 0);
        }
    }

    /** 룬을 주는 때: NOW = 바로 (뽑기 창), PENDING = 적어 두고 연출 뒤 (눈앞 마법진 — RuneGacha) */
    public enum Deliver { NOW, PENDING }

    /** 연출에서 결과가 보이길 기다리는 뽑기 (알림 · 빛기둥) */
    private record Reveal(long id, String banner, String bannerName, List<Gacha.Pull> pulls, long deadline) {}

    private static final Map<UUID, Reveal> REVEALS = new HashMap<>();
    private static final Map<UUID, Long> LAST_PULL = new HashMap<>();
    private static long seq;

    public static void init() {
        GachaNet.register();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> sync(handler.player, "", null)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Reveal r = REVEALS.remove(handler.player.getUUID());
            if (r != null) announce(handler.player, r, false);
            LAST_PULL.remove(handler.player.getUUID());
        });
        ServerTickEvents.END_SERVER_TICK.register(GachaSystem::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            REVEALS.clear();
            LAST_PULL.clear();
        });
        RpgNet.onStatAction("gacha", sp -> open(sp, ""));
        GachaCommands.init();
    }

    // ------------------------------------------------------------ 뽑기 종류

    /** 한정 뽑기 설정 (끝난 것도 — 관리자 창이 이어 고침, 없으면 null) */
    public static Gacha.Spec limitedSpec(MinecraftServer server) {
        String raw = server == null ? null : Props.world(server, LIMITED_DATA);
        if (raw == null || raw.isEmpty()) return null;
        try {
            return Gacha.Spec.fromMap(Json.parseObject(raw), System.currentTimeMillis());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 지금 열린 한정 뽑기 (없으면 null) */
    public static Gacha.Spec activeLimited(MinecraftServer server) {
        Gacha.Spec s = limitedSpec(server);
        return s != null && s.activeAt(System.currentTimeMillis()) && s.problems().isEmpty() ? s : null;
    }

    /** 뽑기 종류들 (창의 탭 순서 — 한정은 열렸을 때만) */
    public static List<Def> defs(MinecraftServer server) {
        List<Def> out = new ArrayList<>();
        Gacha.Spec s = activeLimited(server);
        if (s != null && RuneItems.LIMITED_TICKET != null) out.add(new Def(LIMITED, s.name(), RuneItems.LIMITED_TICKET, s.banner(), LIMITED, s));
        out.add(new Def(NORMAL, "일반 뽑기", RuneItems.TICKET, Gacha.standard(), Gacha.normalPity() ? NORMAL : null, null));
        out.add(new Def(ADVANCED, "고급 뽑기", RuneItems.ADV_TICKET, Gacha.advanced(), null, null));
        for (StatTree.Ticket t : StatTree.tickets().values()) {
            TicketItem item = RuneItems.statTicket(t.stat());
            if (item != null && t.family() != null) out.add(new Def(STAT + t.stat(), t.name(), item, Gacha.single(t.family()), null, null));
        }
        return out;
    }

    public static Def def(MinecraftServer server, String key) {
        if (key == null) return null;
        for (Def d : defs(server)) if (d.key().equals(key)) return d;
        return null;
    }

    /** 뽑기권 → 뽑기 키 */
    public static String keyOf(TicketItem t) {
        if (t == null) return NORMAL;
        if (t.limited()) return LIMITED;
        if (t.stat() != null) return STAT + t.stat();
        return t.advanced() ? ADVANCED : NORMAL;
    }

    /** 뽑기권 이름 */
    public static String ticketName(Def d) {
        if (d.key().equals(LIMITED)) return Gacha.ticket().str("name");
        if (d.key().startsWith(STAT)) {
            StatTree.Ticket t = StatTree.tickets().get(d.key().substring(STAT.length()));
            return t != null ? t.name() : "스탯 룬 뽑기권";
        }
        return RuneKit.RUNE.str(d.key().equals(ADVANCED) ? "advTicketName" : "ticketName");
    }

    // ------------------------------------------------------------ 플레이어 기록

    public static State state(ServerPlayer sp) {
        Map<String, Gacha.Counter> pools = new LinkedHashMap<>();
        Map<String, Integer> totals = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        List<Entry> history = new ArrayList<>();
        String raw = Props.get(sp, DATA);
        if (raw != null && !raw.isEmpty()) {
            try {
                Map<String, Object> m = Json.parseObject(raw);
                if (m.get("pools") instanceof Map<?, ?> pm) for (Map.Entry<?, ?> e : pm.entrySet()) pools.put(String.valueOf(e.getKey()), Gacha.Counter.fromMap(e.getValue()));
                if (m.get("totals") instanceof Map<?, ?> tm) {
                    for (Map.Entry<?, ?> e : tm.entrySet()) if (e.getValue() instanceof Number n) totals.put(String.valueOf(e.getKey()), n.intValue());
                }
                if (m.get("seen") instanceof List<?> l) for (Object o : l) seen.add(String.valueOf(o));
                if (m.get("history") instanceof List<?> l) {
                    for (Object o : l) {
                        Entry en = Entry.fromMap(o);
                        if (en != null) history.add(en);
                    }
                }
            } catch (RuntimeException e) {
                // 깨진 기록은 처음부터
            }
        }
        return new State(pools, totals, seen, history);
    }

    static void save(ServerPlayer sp, State s) {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> pools = new LinkedHashMap<>();
        for (Map.Entry<String, Gacha.Counter> e : s.pools().entrySet()) pools.put(e.getKey(), e.getValue().toMap());
        m.put("pools", pools);
        m.put("totals", new LinkedHashMap<>(s.totals()));
        m.put("seen", new ArrayList<>(s.seen()));
        List<Object> h = new ArrayList<>();
        for (Entry e : s.history()) h.add(e.toMap());
        m.put("history", h);
        Props.set(sp, DATA, Json.stringify(m));
    }

    /** 천장 기록 (pool = normal · limited) */
    public static Gacha.Counter counter(ServerPlayer sp, String pool) {
        return state(sp).pools().getOrDefault(pool, Gacha.Counter.ZERO);
    }

    /** 천장 기록 바꾸기 (관리자 · 시험) — null 이면 지움 */
    public static void setCounter(ServerPlayer sp, String pool, Gacha.Counter c) {
        State s = state(sp);
        Map<String, Gacha.Counter> pools = new LinkedHashMap<>(s.pools());
        if (c == null) pools.remove(pool);
        else pools.put(pool, c);
        save(sp, new State(pools, s.totals(), s.seen(), s.history()));
    }

    /** 기록을 모두 지움 (관리자) */
    public static void reset(ServerPlayer sp) {
        Props.set(sp, DATA, null);
    }

    // ------------------------------------------------------------ 뽑기권

    /** 가방 (36칸) + 왼손의 뽑기권 수 */
    public static int tickets(ServerPlayer sp, Item ticket) {
        if (ticket == null) return 0;
        int n = Inv.count(sp, ticket);
        ItemStack off = sp.getOffhandItem();
        if (off.is(ticket)) n += off.getCount();
        return n;
    }

    /** 뽑기권 n 장 빼기 (가방 앞 칸부터 → 왼손 — 모자라면 아무것도 빼지 않음) */
    static boolean takeTickets(ServerPlayer sp, Item ticket, int n) {
        if (tickets(sp, ticket) < n) return false;
        Inventory inv = sp.getInventory();
        int left = n;
        for (int i = 0; i < Inv.SIZE && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !s.is(ticket)) continue;
            int take = Math.min(left, s.getCount());
            s.shrink(take);
            left -= take;
            if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
        }
        ItemStack off = sp.getOffhandItem();
        if (left > 0 && off.is(ticket)) {
            int take = Math.min(left, off.getCount());
            off.shrink(take);
            left -= take;
        }
        inv.setChanged();
        return left == 0;
    }

    // ------------------------------------------------------------ 뽑기

    /**
     * count 번 뽑기 (1 · 10): 뽑기권을 내고 (모자라면 실패), 천장 기록대로 굴리고, 룬을 줌 (NOW = 바로 — 가방에 들어갈 자리가 없으면 실패,
     * PENDING = 적어 두고 연출 뒤 — RuneGacha), 기록 (천장 · 수 · 뽑아 본 룬 · 최근). 실패면 아무것도 바뀌지 않음
     */
    public static Drawn draw(ServerPlayer sp, String key, int count, Deliver deliver) {
        if (sp == null || sp.isRemoved()) return Drawn.fail("§c뽑을 수 없습니다");
        if (count != 1 && count != Gacha.multi()) return Drawn.fail("§c뽑기는 1회 · " + Gacha.multi() + "회만 됩니다");
        MinecraftServer server = sp.level().getServer();
        Def d = def(server, key);
        if (d == null) return Drawn.fail(LIMITED.equals(key) ? "§c지금 열린 한정 뽑기가 없습니다" : "§c모르는 뽑기입니다: " + key);
        int have = tickets(sp, d.ticket());
        if (have < count) return Drawn.fail("§c" + ticketName(d) + "이 모자랍니다 §7(필요 " + count + " · 가진 것 " + have + ")");
        if (deliver == Deliver.NOW) {
            List<ItemStack> room = new ArrayList<>();
            for (int i = 0; i < count; i++) room.add(RuneItems.stack(Runes.FAMILY_ORDER.get(0), "common"));
            if (!Inv.fitsAfter(sp, List.of(new Inv.Take(d.ticket(), count)), room)) {
                return Drawn.fail("§c가방에 빈 칸이 모자랍니다 §7(룬 " + count + "개 — 칸을 비우고 다시)");
            }
        }
        State s = state(sp);
        Gacha.Counter c = d.pool() == null ? Gacha.Counter.ZERO : s.pools().getOrDefault(d.pool(), Gacha.Counter.ZERO);
        Gacha.Result r = Gacha.pull(d.banner(), c, count, rand);
        if (!takeTickets(sp, d.ticket(), count)) return Drawn.fail("§c" + ticketName(d) + "을 낼 수 없습니다");
        // 기록
        Map<String, Gacha.Counter> pools = new LinkedHashMap<>(s.pools());
        if (d.pool() != null) pools.put(d.pool(), r.counter());
        Map<String, Integer> totals = new LinkedHashMap<>(s.totals());
        totals.merge(d.key().startsWith(STAT) ? "stat" : d.key(), count, Integer::sum);
        Set<String> seen = new LinkedHashSet<>(s.seen());
        List<Boolean> fresh = new ArrayList<>();
        List<Entry> history = new ArrayList<>(s.history());
        long now = System.currentTimeMillis();
        for (Gacha.Pull p : r.pulls()) {
            fresh.add(seen.add(p.id()));
            history.add(new Entry(now, d.key(), p.id(), p.featured(), p.lost(), p.at()));
        }
        int max = Gacha.historyMax();
        if (history.size() > max) history = new ArrayList<>(history.subList(history.size() - max, history.size()));
        save(sp, new State(pools, totals, seen, history));
        // 주기
        if (deliver == Deliver.NOW) {
            for (Gacha.Pull p : r.pulls()) Notify.give(sp, RuneItems.stack(p.family(), p.grade()));
        } else {
            List<String> pending = new ArrayList<>(RuneData.pending(sp));
            for (Gacha.Pull p : r.pulls()) pending.add(p.id());
            RuneData.setPending(sp, pending);
        }
        return new Drawn(null, d, r.pulls(), List.copyOf(fresh), r.counter(), ++seq);
    }

    /** 뽑기 창의 뽑기: 뽑고 결과를 보냄 (연출) — 영웅 이상 알림 · 빛기둥은 연출에서 결과가 보일 때 */
    public static Drawn pullFromScreen(ServerPlayer sp, String key, int count) {
        long now = sp.level().getServer().getTickCount();
        Long last = LAST_PULL.get(sp.getUUID());
        if (last != null && now - last < THROTTLE) {
            sync(sp, "", "§7잠시 뒤에 다시 뽑으세요");
            return Drawn.fail("§7잠시 뒤에 다시 뽑으세요");
        }
        Reveal prev = REVEALS.remove(sp.getUUID());
        if (prev != null) announce(sp, prev, true);
        Drawn d = draw(sp, key, count, Deliver.NOW);
        if (d.error() != null) {
            Notify.warn(sp, d.error());
            SkillFx.playFor(sp, "note.bass", 0.7, 0.8);
            sync(sp, "", d.error());
            return d;
        }
        LAST_PULL.put(sp.getUUID(), now);
        REVEALS.put(sp.getUUID(), new Reveal(d.id(), d.def().key(), d.def().name(), d.pulls(), now + (count > 1 ? 240 : 140)));
        GachaNet.result(sp, resultJson(d));
        sync(sp, "", null);
        return d;
    }

    static String resultJson(Drawn d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", (double) d.id());
        m.put("banner", d.def().key());
        m.put("name", d.def().name());
        List<Object> pulls = new ArrayList<>();
        for (int i = 0; i < d.pulls().size(); i++) {
            Gacha.Pull p = d.pulls().get(i);
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("rune", p.id());
            o.put("family", p.family());
            o.put("grade", p.grade());
            if (p.featured()) o.put("featured", true);
            if (p.lost()) o.put("lost", true);
            if (p.at() > 0) o.put("at", p.at());
            if (i < d.fresh().size() && d.fresh().get(i)) o.put("new", true);
            pulls.add(o);
        }
        m.put("pulls", pulls);
        m.put("counter", d.counter().toMap());
        return Json.stringify(m);
    }

    /** 연출에서 결과가 보임 (클라이언트) */
    static void revealed(ServerPlayer sp, String id) {
        Reveal r = REVEALS.get(sp.getUUID());
        if (r == null || !String.valueOf(r.id()).equals(id.contains(".") ? id.substring(0, id.indexOf('.')) : id)) return;
        REVEALS.remove(sp.getUUID());
        announce(sp, r, true);
    }

    private static void tick(MinecraftServer server) {
        if (REVEALS.isEmpty()) return;
        long now = server.getTickCount();
        for (Iterator<Map.Entry<UUID, Reveal>> it = REVEALS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Reveal> e = it.next();
            if (e.getValue().deadline() > now) continue;
            it.remove();
            ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
            if (sp != null) announce(sp, e.getValue(), true);
        }
    }

    /** 결과 알림: 내 채팅 (등급별 수 · 높은 등급 이름), 영웅 이상은 모두에게, 둘레 빛기둥 (fx = 세계 연출) */
    private static void announce(ServerPlayer sp, Reveal r, boolean fx) {
        int[] byGrade = new int[GRADES.size()];
        int top = 0;
        List<String> big = new ArrayList<>();
        int from = Math.max(0, GRADES.indexOf(RuneKit.RUNE.str("broadcastFrom")));
        for (Gacha.Pull p : r.pulls()) {
            int gi = GRADES.indexOf(p.grade());
            if (gi < 0) continue;
            byGrade[gi]++;
            top = Math.max(top, gi);
            if (gi >= from) {
                Runes.Rune rr = Runes.parseRune(p.id());
                if (rr != null) big.add(rr.gradeColor() + rr.gradeName() + " 룬 " + rr.def().str("name") + (p.featured() ? " §e[픽업]" : "") + "§r");
            }
        }
        List<String> counts = new ArrayList<>();
        for (int gi = GRADES.size() - 1; gi >= 0; gi--) {
            if (byGrade[gi] == 0) continue;
            mmorpg.core.Grade g = Rules.get().grade(GRADES.get(gi));
            counts.add((g == null ? "§f" : g.color()) + (g == null ? GRADES.get(gi) : g.name()) + " " + byGrade[gi] + "§r");
        }
        String head = "§d[룬 뽑기] §f" + r.bannerName() + " " + r.pulls().size() + "회";
        Notify.say(sp, head + " §7→ " + String.join(" §7· ", counts));
        if (!big.isEmpty()) {
            sp.level().getServer().getPlayerList().broadcastSystemMessage(Component.literal(BedrockText.toJava(
                    Notify.PREFIX + sp.getName().getString() + "님이 §d" + r.bannerName() + "§f에서 " + String.join("§f, ", big) + "§f을(를) 얻었습니다!")), false);
        }
        if (fx && top >= 3 && !sp.isRemoved()) revealWorldFx(sp, GRADES.get(top));
    }

    /** 둘레 사람에게 보이는 빛기둥 (영웅 · 초월) */
    private static void revealWorldFx(ServerPlayer sp, String grade) {
        int gi = GRADES.indexOf(grade);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double[] col = RuneKit.RUNE_GRADE_FX.get(grade);
        double h = 5 + (gi - 3) * 3;
        SkillFx.fx(level, "rpg:arrow_shot", V.up(c, h), Opt.of("dir", V.UP, "speed", 0.0, "size", h, "thick", 0.8 + gi * 0.15, "life", 1.1, "color", col, "alpha", 0.85));
        SkillFx.fx(level, "rpg:arrow_shot", V.up(c, h), Opt.of("dir", V.UP, "speed", 0.0, "size", h * 0.95, "thick", 0.3, "life", 0.9, "color", SkillFx.WHITE,
                "alpha", 1.0));
        SkillFx.shock(level, c, col, 2.4 + gi * 0.5, 0.6);
        SkillFx.rune(level, c, Opt.of("size", 2.2 + gi * 0.3, "life", 1.2, "color", col, "alpha", 0.9, "progress", 1.0, "rate", 60.0, "grow", 0.3));
        SkillFx.vanilla(level, "minecraft:totem_particle", V.up(c, 1.2));
        SkillFx.play(level, gi >= 4 ? "random.totem" : "beacon.activate", c, 1.0, 0.9);
        if (gi >= 4) {
            SkillFx.play(level, "firework.large_blast", c, 1.0, 1);
            for (int i = 0; i < 3; i++) {
                final int ii = i;
                Tasks.later(3 + i * 4, () -> SkillFx.shock(level, c, ii % 2 == 1 ? SkillFx.WHITE : col, 3 + ii * 1.5, 0.6));
            }
        }
    }

    // ------------------------------------------------------------ 창

    /** 뽑기권 우클릭: 모드가 있으면 그 뽑기의 창, 없으면 예전처럼 눈앞 마법진으로 1회 */
    public static void useTicketItem(ServerPlayer sp, TicketItem t) {
        if (GachaNet.canSend(sp)) open(sp, keyOf(t));
        else RuneGacha.useTicket(sp, t);
    }

    /** 뽑기 창 열기 (key = 처음 볼 뽑기 — "" 이면 한정 → 일반) */
    public static void open(ServerPlayer sp, String key) {
        if (!GachaNet.canSend(sp)) {
            Notify.warn(sp, "§7뽑기 창은 모드가 있는 클라이언트에서 열립니다 §8(뽑기권을 들고 우클릭하면 1회)");
            return;
        }
        MinecraftServer server = sp.level().getServer();
        String k = key == null || key.isEmpty() || def(server, key) == null ? (activeLimited(server) != null ? LIMITED : NORMAL) : key;
        sync(sp, k, null);
    }

    /** 상태를 보냄 (open = 열 뽑기 키 · "" = 열지 않음, note = 창 아래 알림) */
    public static void sync(ServerPlayer sp, String open, String note) {
        if (sp == null || !GachaNet.canSend(sp)) return;
        GachaNet.state(sp, stateJson(sp, open, note));
    }

    /** 모두에게 다시 (한정 뽑기가 바뀜) */
    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sync(p, "", null);
    }

    static boolean isAdmin(ServerPlayer sp) {
        return sp.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    static String stateJson(ServerPlayer sp, String open, String note) {
        MinecraftServer server = sp.level().getServer();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("open", open == null ? "" : open);
        if (note != null && !note.isEmpty()) m.put("note", note);
        m.put("admin", isAdmin(sp));
        m.put("now", (double) System.currentTimeMillis());
        Gacha.Spec spec = limitedSpec(server);
        if (spec != null) m.put("limited", spec.toMap());
        m.put("active", activeLimited(server) != null);
        State s = state(sp);
        Map<String, Object> pools = new LinkedHashMap<>();
        for (Map.Entry<String, Gacha.Counter> e : s.pools().entrySet()) pools.put(e.getKey(), e.getValue().toMap());
        m.put("counters", pools);
        m.put("totals", new LinkedHashMap<>(s.totals()));
        List<Object> h = new ArrayList<>();
        List<Entry> hist = s.history();
        for (int i = Math.max(0, hist.size() - 60); i < hist.size(); i++) h.add(hist.get(i).toMap());
        m.put("history", h);
        return Json.stringify(m);
    }

    // ------------------------------------------------------------ 창의 일

    /** 클라이언트가 보낸 것 */
    static void action(ServerPlayer sp, String action, String a, String b) {
        switch (action) {
            case "open" -> open(sp, a);
            case "sync" -> sync(sp, "", null);
            case "pull" -> {
                int n;
                try {
                    n = Integer.parseInt(b.trim());
                } catch (NumberFormatException e) {
                    n = 1;
                }
                pullFromScreen(sp, a, n);
            }
            case "revealed" -> revealed(sp, a);
            case "limited_save" -> {
                String err = saveLimited(sp, a);
                if (err != null) {
                    SkillFx.playFor(sp, "note.bass", 0.7, 0.8);
                    sync(sp, "", err);
                }
            }
            case "limited_close" -> {
                String err = closeLimited(sp);
                if (err != null) sync(sp, "", err);
            }
            default -> Notify.warn(sp, "§c모르는 뽑기 일: " + action);
        }
    }

    // ------------------------------------------------------------ 관리자: 한정 뽑기

    /**
     * 한정 뽑기 저장 (관리자 창 — JSON: name · featured5 · featured4 · rate5 · rate4 · hard · softStart · heroEvery · share · price · days · id).
     * 같은 id 의 열린 한정 뽑기면 고침 (시작은 그대로 — 기간은 시작부터), 아니면 새로 엶 (지금부터). 실패면 까닭 (성공 null)
     */
    public static String saveLimited(ServerPlayer sp, String json) {
        if (!isAdmin(sp)) return "§c관리자만 한정 뽑기를 설정할 수 있습니다";
        Map<String, Object> m;
        try {
            m = Json.parseObject(json);
        } catch (RuntimeException e) {
            return "§c설정을 읽을 수 없습니다";
        }
        return saveLimited(sp.level().getServer(), m, sp);
    }

    /** 한정 뽑기 저장 (m = 설정 지도, by = 알릴 관리자 — null 이면 알리지 않음). 실패면 까닭 */
    public static String saveLimited(MinecraftServer server, Map<String, Object> m, ServerPlayer by) {
        long now = System.currentTimeMillis();
        Gacha.Spec cur = limitedSpec(server);
        String sentId = m.get("id") == null ? "" : String.valueOf(m.get("id"));
        boolean edit = cur != null && !sentId.isEmpty() && cur.id().equals(sentId) && cur.activeAt(now);
        long start = edit ? cur.start() : now;
        double days = m.get("days") instanceof Number n ? n.doubleValue() : Gacha.limitedDefaults().num("days");
        Map<String, Object> full = new LinkedHashMap<>(m);
        full.put("id", edit ? cur.id() : "lim_" + Long.toString(now, 36));
        full.put("start", (double) start);
        full.put("end", days > 0 ? (double) (start + (long) (days * 86_400_000L)) : 0.0);
        Gacha.Spec spec = Gacha.Spec.fromMap(full, now);
        List<String> bad = spec.problems();
        if (!bad.isEmpty()) return "§c" + String.join(" · ", bad);
        if (spec.end() > 0 && spec.end() <= now) return "§c이미 끝난 기간입니다 (기간을 늘리세요)";
        Props.setWorld(server, LIMITED_DATA, Json.stringify(spec.toMap()));
        syncAll(server);
        if (!edit) {
            String f5 = names(spec.featured5(), "transcend");
            String f4 = spec.featured4().isEmpty() ? "" : " §7· 영웅 픽업 §6" + names(spec.featured4(), "hero");
            server.getPlayerList().broadcastSystemMessage(Component.literal(BedrockText.toJava(Notify.PREFIX + "§d새 한정 뽑기 §6§l" + spec.name()
                    + "§r§f 이(가) 열렸습니다! §7초월 픽업 §c" + f5 + f4 + (spec.end() > 0 ? " §8(" + endText(spec.end()) + " 까지)" : ""))), false);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) SkillFx.playFor(p, "random.levelup", 1.4, 0.6);
        } else if (by != null) {
            Notify.say(by, "§d[룬 뽑기] §f한정 뽑기 §6" + spec.name() + "§f 설정을 고쳤습니다");
        }
        return null;
    }

    /** 한정 뽑기를 지금 끝냄 (관리자). 실패면 까닭 */
    public static String closeLimited(ServerPlayer sp) {
        if (!isAdmin(sp)) return "§c관리자만 한정 뽑기를 끝낼 수 있습니다";
        return closeLimited(sp.level().getServer());
    }

    public static String closeLimited(MinecraftServer server) {
        Gacha.Spec cur = activeLimited(server);
        if (cur == null) return "§7열린 한정 뽑기가 없습니다";
        long now = System.currentTimeMillis();
        Gacha.Spec done = new Gacha.Spec(cur.id(), cur.name(), cur.featured5(), cur.featured4(), cur.rate5(), cur.rate4(), cur.hard(), cur.softStart(),
                cur.heroEvery(), cur.share(), cur.start(), Math.max(cur.start() + 1, now), cur.price());
        Props.setWorld(server, LIMITED_DATA, Json.stringify(done.toMap()));
        syncAll(server);
        server.getPlayerList().broadcastSystemMessage(Component.literal(BedrockText.toJava(Notify.PREFIX + "§7한정 뽑기 §6" + cur.name()
                + "§7 이(가) 끝났습니다 §8(한정 룬 뽑기권은 다음 한정 뽑기에 쓸 수 있습니다)")), false);
        return null;
    }

    /** 계열 이름들 (룬 이름 — 그 등급) */
    static String names(List<String> families, String grade) {
        List<String> out = new ArrayList<>();
        for (String f : families) {
            Runes.Rune r = Runes.parseRune(Runes.runeId(f, grade));
            out.add(r == null ? f : r.def().str("name"));
        }
        return String.join(", ", out);
    }

    /** 끝나는 때 글자 (서버 시간 — 10.31 23:59) */
    static String endText(long end) {
        java.time.LocalDateTime t = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(end), java.time.ZoneId.systemDefault());
        return String.format(Locale.ROOT, "%d.%02d %02d:%02d", t.getMonthValue(), t.getDayOfMonth(), t.getHour(), t.getMinute());
    }

    /** 한정 뽑기 설정 한 줄 (명령어 · 알림) */
    public static String describe(Gacha.Spec s) {
        if (s == null) return "§7한정 뽑기 없음";
        long now = System.currentTimeMillis();
        String state = s.activeAt(now) ? "§a열림" : now < s.start() ? "§e시작 전" : "§8끝남";
        return "§6" + s.name() + " " + state + " §7· 초월 픽업 §c" + names(s.featured5(), "transcend") + (s.featured4().isEmpty() ? "" : " §7· 영웅 픽업 §6"
                + names(s.featured4(), "hero")) + " §7· 초월 " + mmorpg.core.Js.str(s.rate5()) + "% · 영웅 " + mmorpg.core.Js.str(s.rate4()) + "% · 천장 "
                + s.hard() + " (" + s.softStart() + "회부터 오름) · 영웅 " + s.heroEvery() + "회 · 픽업 " + mmorpg.core.Js.str(s.share()) + "% · 룬술사 "
                + (s.price() > 0 ? s.price() + " 조각" : "팔지 않음") + (s.end() > 0 ? " · " + endText(s.end()) + " 까지" : " · 끝없음");
    }

    /** 플레이어 천장 한 줄 */
    public static String describeCounters(ServerPlayer sp) {
        State s = state(sp);
        Gacha.Counter n = s.pools().getOrDefault(NORMAL, Gacha.Counter.ZERO);
        Gacha.Counter l = s.pools().getOrDefault(LIMITED, Gacha.Counter.ZERO);
        return "§f" + sp.getName().getString() + " §7일반 초월 " + n.n5() + " · 영웅 " + n.n4() + " §8| §7한정 초월 " + l.n5() + " · 영웅 " + l.n4()
                + (l.g5() ? " §a(다음 초월 픽업 확정)" : "") + (l.g4() ? " §a(다음 영웅 픽업 확정)" : "") + " §8| §7뽑은 수 " + s.totals();
    }

    /** 시험용: 기다리는 결과 알림이 있는지 */
    public static boolean revealPending(ServerPlayer sp) {
        return REVEALS.containsKey(sp.getUUID());
    }

    /** 시험용: 뽑기 사이 대기를 지움 */
    public static void clearThrottle(ServerPlayer sp) {
        LAST_PULL.remove(sp.getUUID());
    }

    /** 다른 곳 (룬술사) 이 쓰는 기본값 */
    public static Obj limitedDefaults() {
        return Gacha.limitedDefaults();
    }
}
