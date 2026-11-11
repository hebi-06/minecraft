package mmorpg.fabric.cosmetic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Economy;
import mmorpg.core.Json;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Props;
import mmorpg.fabric.RpgNet;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.economy.Wallet;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 장식 (0.13.0 — 무기 스킨 · 머리 · 등 장식 · 오라 · 칭호, 목록은 cosmetics.json {@link CosmeticDefs}).
 * <ul>
 * <li>얻기: 장식 창 (스탯 창 장식 단추 · U 키 · /장식) 에서 골드로 사기 (0.21.0 — 사용자 요청 "치장템은 골드로", 예전 에메랄드 값의 1/10) — 산 것은 바로 입음.
 * 한 번 사면 계속 가짐 (죽어도 그대로)</li>
 * <li>입기 · 벗기: 가진 것만. 무기 스킨은 무기 종류마다 하나 (손에 든 모습만 — 가방 아이콘 · 수치는 그대로). 무기는 숨길 수 없음</li>
 * <li>갑옷: 입은 갑옷은 늘 보이지 않음 (2026-11-08 사용자 — 효과만). 2026-11-11 갑옷 스킨 · 숨기기를 뺌 (사용자 "없애 갑옷 스킨과 모드 창의 숨기기 없애고")</li>
 * <li>기록: 플레이어 속성 rpg:cosmetics {"owned": [키…], "look": 모습} — 모습 ({@link Look}) 은 모두에게 보냄 (들어올 때 전체 표 · 바뀔 때 그 사람만),
 * 클라이언트가 보이는 사람마다 그림 (모드가 없는 클라이언트는 서버가 바닐라 모습으로 — VanillaCosmetics)</li>
 * </ul>
 */
public final class Cosmetics {
    private Cosmetics() {}

    public static final String DATA = "rpg:cosmetics";

    /** 내 장식 (가진 것 · 모습) */
    public record State(Set<String> owned, Look look) {}

    public static void init() {
        CosmeticNet.register();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> join(handler.player, server));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> leave(handler.player, server));
        // 다시 살아나면 (죽어도 기록은 그대로) 모두에게 다시 — 클라이언트는 새 엔티티로 다시 그림
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> broadcast(newPlayer));
        RpgNet.onStatAction("cosmetic", sp -> sync(sp, "open"));
        CosmeticCommands.init();
    }

    // ------------------------------------------------------------ 기록

    public static State state(ServerPlayer sp) {
        Set<String> owned = new LinkedHashSet<>();
        Look look = Look.EMPTY;
        String raw = Props.get(sp, DATA);
        if (raw != null && !raw.isEmpty()) {
            try {
                Map<String, Object> m = Json.parseObject(raw);
                if (m.get("owned") instanceof List<?> l) for (Object o : l) owned.add(String.valueOf(o));
                if (m.get("look") instanceof Map<?, ?> lm) look = Look.fromMap(lm);
            } catch (RuntimeException e) {
                // 잘못된 기록이면 처음부터
            }
        }
        owned.retainAll(CosmeticDefs.all().keySet());
        Set<String> has = owned;
        return new State(owned, look.sanitize(has::contains));
    }

    static void save(ServerPlayer sp, State s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("owned", new ArrayList<>(s.owned()));
        m.put("look", s.look().toMap());
        Props.set(sp, DATA, Json.stringify(m));
    }

    public static Look look(ServerPlayer sp) {
        return state(sp).look();
    }

    public static boolean owns(ServerPlayer sp, String id) {
        return state(sp).owned().contains(id);
    }

    // ------------------------------------------------------------ 장식 창의 일

    /** 클라이언트가 보낸 것 (모르는 일 · 실패는 까닭을 화면 하단에) — 끝나면 내 장식을 다시 보냄 */
    public static void action(ServerPlayer sp, String action, String a, String b) {
        String err = switch (action) {
            case "buy" -> buy(sp, a);
            case "equip" -> equip(sp, a);
            case "clear" -> clear(sp, a, b);
            case "sync" -> null;
            default -> "§c모르는 장식 일: " + action;
        };
        if (err != null) {
            Notify.warn(sp, err);
            SkillFx.playFor(sp, "note.bass", 0.7, 0.8);
        }
        sync(sp, "");
    }

    /** 사기: 골드 price 를 내고 (지갑 먼저 · 모자라면 가방의 금화) 내 것으로 · 바로 입음. 실패면 까닭 (성공 null) */
    public static String buy(ServerPlayer sp, String id) {
        CosmeticDefs.Def d = CosmeticDefs.get(id);
        if (d == null) return "§c없는 장식입니다";
        State s = state(sp);
        if (s.owned().contains(id)) return "§7이미 가진 장식입니다: §f" + d.name();
        long money = Wallet.total(sp, Economy.Kind.GOLD);
        if (money < d.price()) return "§c골드가 모자랍니다 §7(" + d.name() + " " + d.price() + " · 가진 것 " + money + ")";
        if (d.price() > 0 && !Wallet.pay(sp, Economy.Kind.GOLD, d.price())) return "§c골드를 낼 수 없습니다";
        Set<String> owned = new LinkedHashSet<>(s.owned());
        owned.add(id);
        save(sp, new State(owned, apply(s.look(), d)));
        broadcast(sp);
        buyFx(sp, d);
        Notify.say(sp, "§d[장식] §f" + d.name() + " §7을(를) 샀습니다 §8(골드 " + d.price() + ") §7— 바로 입었습니다");
        return null;
    }

    /** 입기 (가진 것만). 실패면 까닭 */
    public static String equip(ServerPlayer sp, String id) {
        CosmeticDefs.Def d = CosmeticDefs.get(id);
        if (d == null) return "§c없는 장식입니다";
        State s = state(sp);
        if (!s.owned().contains(id)) return "§c아직 가지지 않은 장식입니다: §f" + d.name() + " §7(골드 " + d.price() + ")";
        Look next = apply(s.look(), d);
        if (next.equals(s.look())) return null;
        save(sp, new State(s.owned(), next));
        broadcast(sp);
        equipFx(sp, d);
        return null;
    }

    /** 칸을 원래대로 (무기 칸은 type 의 스킨 → 기본 모습 — type 이 없으면 모든 무기 종류, 장식 칸 → 없음) */
    public static String clear(ServerPlayer sp, String slot, String type) {
        if (CosmeticDefs.slot(slot) == null) return "§c모르는 칸: " + slot;
        State s = state(sp);
        Look next;
        if ("weapon".equals(slot)) next = type == null || type.isBlank() ? new Look(java.util.Map.of(), s.look().slots()) : s.look().withWeapon(type, null);
        else next = s.look().with(slot, null);
        if (next.equals(s.look())) return null;
        save(sp, new State(s.owned(), next));
        broadcast(sp);
        return null;
    }

    /** 장식을 입힌 모습 */
    static Look apply(Look look, CosmeticDefs.Def d) {
        return "weapon".equals(d.slot()) ? look.withWeapon(d.weapon(), d.id()) : look.with(d.slot(), d.id());
    }

    // ------------------------------------------------------------ 운영자 (명령어 · 시험)

    /** 주기 (값 없이). 새로 들어갔으면 true */
    public static boolean give(ServerPlayer sp, String id) {
        if (CosmeticDefs.get(id) == null) return false;
        State s = state(sp);
        if (s.owned().contains(id)) return false;
        Set<String> owned = new LinkedHashSet<>(s.owned());
        owned.add(id);
        save(sp, new State(owned, s.look()));
        sync(sp, "");
        return true;
    }

    /** 빼기 (입고 있었으면 벗김). 뺐으면 true */
    public static boolean take(ServerPlayer sp, String id) {
        State s = state(sp);
        if (!s.owned().contains(id)) return false;
        Set<String> owned = new LinkedHashSet<>(s.owned());
        owned.remove(id);
        save(sp, new State(owned, s.look().sanitize(owned::contains)));
        broadcast(sp);
        return true;
    }

    /** 모두 지움 (가진 것 · 모습) */
    public static void reset(ServerPlayer sp) {
        save(sp, new State(Set.of(), Look.EMPTY));
        broadcast(sp);
    }

    // ------------------------------------------------------------ 보내기

    /** 내 장식을 클라이언트에 (open = 열 탭 — "" 이면 열지 않음) */
    public static void sync(ServerPlayer sp, String open) {
        if (sp == null || sp.isRemoved() || !CosmeticNet.canSend(sp)) return;
        State s = state(sp);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("owned", new ArrayList<>(s.owned()));
        m.put("look", s.look().toMap());
        m.put("open", open == null ? "" : open);
        CosmeticNet.state(sp, Json.stringify(m));
    }

    /** 이 사람의 모습을 모두에게 (나에게는 내 장식도) */
    public static void broadcast(ServerPlayer sp) {
        if (sp == null) return;
        MinecraftServer server = sp.level().getServer();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(sp.getUUID().toString(), look(sp).toMap());
        String json = Json.stringify(m);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) CosmeticNet.looks(p, false, json);
        sync(sp, "");
        mmorpg.fabric.vanilla.VanillaCosmetics.changed(sp); // 2026-11-08 모드 없는 클라이언트가 보는 그림 (장비 · 태운 장식)
    }

    /** 사람이 아닌 몸 (꾸미기 미리보기 마네킹) 의 모습을 모드 클라이언트에 (look null = 지움) */
    public static void showLook(MinecraftServer server, UUID id, Look look) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(id.toString(), look == null ? null : look.toMap());
        String json = Json.stringify(m);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) CosmeticNet.looks(p, false, json);
    }

    private static void join(ServerPlayer sp, MinecraftServer server) {
        Map<String, Object> all = new LinkedHashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Look l = look(p);
            if (!l.isEmpty()) all.put(p.getUUID().toString(), l.toMap());
        }
        CosmeticNet.looks(sp, true, Json.stringify(all));
        Look mine = look(sp);
        if (!mine.isEmpty()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put(sp.getUUID().toString(), mine.toMap());
            String json = Json.stringify(m);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) if (p != sp) CosmeticNet.looks(p, false, json);
        }
        sync(sp, "");
    }

    private static void leave(ServerPlayer sp, MinecraftServer server) {
        UUID id = sp.getUUID();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(id.toString(), null);
        String json = Json.stringify(m);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (p != sp) CosmeticNet.looks(p, false, json);
    }

    // ------------------------------------------------------------ 연출

    private static double[] color(CosmeticDefs.Def d) {
        return d.color();
    }

    /** 사기: 발밑 룬 · 빛기둥 · 반짝임 · 소리 (장식 색) */
    static void buyFx(ServerPlayer sp, CosmeticDefs.Def d) {
        ServerLevel level = sp.level();
        Vec3 at = sp.position();
        double[] c = color(d);
        SkillFx.rune(level, at, Opt.of("size", 1.6, "life", 1.0, "color", c, "alpha", 0.85, "progress", 1, "rate", 90, "grow", 0.25));
        SkillFx.fx(level, "rpg:arrow_shot", V.up(at, 1.6), Opt.of("dir", V.UP, "speed", 0, "size", 1.6, "thick", 0.35, "life", 0.55, "color", c, "alpha", 0.7));
        SkillFx.sparks(level, V.up(at, 1.0), c, 14, 3.5);
        SkillFx.sparks(level, V.up(at, 1.5), SkillFx.WHITE, 8, 2.5);
        SkillFx.play(level, "random.levelup", at, 1.5, 0.6);
        SkillFx.play(level, "note.chime", at, 1.3, 0.9);
    }

    /** 입기: 작은 반짝임 · 소리 */
    static void equipFx(ServerPlayer sp, CosmeticDefs.Def d) {
        ServerLevel level = sp.level();
        Vec3 at = sp.position();
        SkillFx.sparks(level, V.up(at, 1.1), color(d), 8, 2.2);
        SkillFx.play(level, "random.orb", at, 1.4, 0.5);
    }
}
