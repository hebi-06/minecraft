package mmorpg.fabric.stattree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import mmorpg.core.Json;
import mmorpg.core.StatTree;
import mmorpg.core.Stats;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Props;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgData;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgNet;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.ui.Inv;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 스탯 트리 (7단계 — 애드온에는 없는 자바 판 기능, 계산은 규칙 코어 {@link StatTree} · stat_tree.json):
 * <ul>
 * <li>스탯을 찍으면 그 스탯의 나무가 저절로 자람 (찍은 포인트 — 장비 · 룬 버프 제외). 새 칸이 열리면 그 칸의 보상 (뽑기권) 을 처음 한 번만 줌
 * (나무마다 받은 칸 수를 적어 둠 — 스탯을 되돌렸다 다시 찍어도 다시 주지 않음) · 큰 글자 · 채팅 · 자라는 연출</li>
 * <li>갈래 칸: 열린 뒤 둘 중 하나를 고름 (스탯 트리 창 · /스탯트리). 갈래 초기화 = 그 나무의 고른 갈래를 모두 지움 — 에메랄드 (갈래당 perPick)</li>
 * <li>보너스: 공격력 (RpgCombat.powerOf · 룬 피해), 룬 피해, 치명타 확률 (평타 훅 · 스킬), 치명타 피해 (치명타 배율에 더함), 최대 체력
 * (RpgHealth.maxHpOf), 받는 피해 (RpgHealth.onLoss), 이동 속도 (속성 수정자 mmorpg:stat_tree_speed), 재사용 대기 (스킬 · 룬)</li>
 * <li>기록: 플레이어 속성 rpg:stat_tree (JSON — 고른 갈래 · 받은 칸). 클라이언트에는 {@link RpgNet.StatTreePayload}</li>
 * </ul>
 * {@link #active}: 스탯 트리를 쓰는 플레이어 (실제 게임 = 모두). 게임 시험은 기존 시험의 정해진 피해 · 체력 숫자를 지키려고 시험이 켠 플레이어만.
 */
public final class StatTrees {
    private StatTrees() {}

    public static final String DATA = "rpg:stat_tree";
    private static final Identifier SPEED_ID = Mmorpg.id("stat_tree_speed");

    /** 스탯 트리를 쓰는 플레이어 (게임 시험 — TestSetup 이 바꿈) */
    public static Predicate<ServerPlayer> active = sp -> true;

    /** 플레이어 → 보너스 합 (나무 넷 · 상한 적용 — refresh 때 다시) */
    private static final Map<UUID, Map<String, Double>> TOTALS = new HashMap<>();

    public static void init() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> refresh(newPlayer, false));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TOTALS.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> TOTALS.clear());
        RpgCombat.addCritBonusHook((sp, w, victim) -> critAdd(sp));
        RpgNet.beforeStatsOpen(sp -> sync(sp, ""));
        ServerPlayNetworking.registerGlobalReceiver(RpgNet.StatTreeActionPayload.TYPE, (p, context) -> action(context.player(), p));
        StatTreeCommands.init();
    }

    /** 스탯 트리 창이 보낸 것: pick (갈래 고르기) · reset (갈래 초기화) · sync (다시 보내 줌) */
    static void action(ServerPlayer sp, RpgNet.StatTreeActionPayload p) {
        String err = switch (p.action()) {
            case "pick" -> pick(sp, p.stat(), p.index(), p.option());
            case "reset" -> reset(sp, p.stat(), false);
            default -> null;
        };
        if (err != null) {
            Notify.warn(sp, err);
            SkillFx.playFor(sp, "note.bass", 0.7, 0.8);
        }
        sync(sp, "");
    }

    // ------------------------------------------------------------ 기록

    public static StatTree.State state(ServerPlayer sp) {
        return StatTree.State.parse(Props.get(sp, DATA));
    }

    static void save(ServerPlayer sp, StatTree.State s) {
        Props.set(sp, DATA, s.json());
    }

    /** 찍은 스탯 {str, dex, int, vit} */
    public static Map<String, Double> spent(ServerPlayer sp) {
        return RpgData.of(sp).base();
    }

    public static boolean isActive(ServerPlayer sp) {
        return sp != null && active.test(sp);
    }

    // ------------------------------------------------------------ 보너스

    /** 보너스 합 (나무 넷 — 상한 적용, 쓰지 않는 플레이어는 0) */
    public static double bonus(ServerPlayer sp, String kind) {
        if (sp == null) return 0;
        Map<String, Double> t = TOTALS.get(sp.getUUID());
        if (t == null) return 0;
        Double v = t.get(kind);
        return v == null ? 0 : v;
    }

    /** 보너스 합 전부 (없으면 빈 지도) */
    public static Map<String, Double> totals(ServerPlayer sp) {
        Map<String, Double> t = sp == null ? null : TOTALS.get(sp.getUUID());
        return t == null ? Map.of() : t;
    }

    /** 무기 피해 배율 (평타 · 스킬): 1 + 공격력 % */
    public static double atkMult(ServerPlayer sp) {
        return 1 + bonus(sp, "atk") / 100;
    }

    /** 룬 피해 배율: 1 + (공격력 + 룬 피해) % */
    public static double runeMult(ServerPlayer sp) {
        return 1 + (bonus(sp, "atk") + bonus(sp, "rune")) / 100;
    }

    /** 치명타 확률에 더할 것 (0 ~ 1) */
    public static double critAdd(ServerPlayer sp) {
        return bonus(sp, "crit") / 100;
    }

    /** 치명타 배율에 더할 것 (×1.5 → ×1.5 + 이 값) */
    public static double critDmgAdd(ServerPlayer sp) {
        return bonus(sp, "critDmg") / 100;
    }

    /** 최대 체력에 더할 것 */
    public static double hpAdd(ServerPlayer sp) {
        return bonus(sp, "hp");
    }

    /** 받는 피해 배율 (1 − 감소 %) */
    public static double defMult(ServerPlayer sp) {
        return Math.max(0, 1 - bonus(sp, "def") / 100);
    }

    /** 재사용 대기 배율 (1 − 감소 %) */
    public static double cdMult(ServerPlayer sp) {
        return Math.max(0.05, 1 - bonus(sp, "cdr") / 100);
    }

    // ------------------------------------------------------------ 자람 (스탯이 바뀐 뒤)

    /** 새로 열린 칸 하나 (보상 · 알림용) */
    private record Grown(String stat, int index, Map<String, Integer> reward) {}

    /**
     * 스탯이 바뀐 뒤 · 접속 · 다시 태어남 (RpgPlayers.update · onJoin — 최대 체력을 다시 계산하기 전에 부름): 새로 열린 칸의 보상을 주고 기록,
     * 보너스 합 · 이동 속도를 다시, 클라이언트에 보냄. announce = 큰 글자 · 채팅 · 연출 (접속 때는 조금 뒤에)
     */
    public static void refresh(ServerPlayer sp, boolean announce) {
        refresh(sp, announce ? 0 : -1);
    }

    /** 접속: 보상 · 보너스는 바로, 알림 · 연출은 2초 뒤 (불러오는 화면에 가리지 않게) */
    public static void refreshOnJoin(ServerPlayer sp) {
        refresh(sp, 40);
    }

    /** announceDelay: -1 = 알리지 않음, 0 = 바로, n = n틱 뒤 */
    private static void refresh(ServerPlayer sp, int announceDelay) {
        if (sp == null || sp.isRemoved()) return;
        if (!isActive(sp)) {
            if (TOTALS.remove(sp.getUUID()) != null) applySpeed(sp);
            sync(sp, "");
            return;
        }
        StatTree.State s = state(sp);
        Map<String, Double> spent = spent(sp);
        List<Grown> grown = new ArrayList<>();
        StatTree.State next = s;
        for (String stat : StatTree.stats()) {
            int tier = StatTree.tier(stat, Stats.get(spent, stat));
            int claimed = s.claimedOf(stat);
            if (tier <= claimed) continue;
            for (int i = claimed; i < tier; i++) grown.add(new Grown(stat, i, StatTree.rewards(stat, i, i + 1)));
            next = next.withClaimed(stat, tier);
        }
        if (next != s) save(sp, next);
        for (Grown g : grown) for (Map.Entry<String, Integer> e : g.reward().entrySet()) giveTickets(sp, e.getKey(), e.getValue());
        recompute(sp, next);
        if (!grown.isEmpty() && announceDelay == 0) announce(sp, grown);
        else if (!grown.isEmpty() && announceDelay > 0) Tasks.later(announceDelay, () -> {
            if (!sp.isRemoved()) announce(sp, grown);
        });
        sync(sp, "");
    }

    /** 보너스 합 다시 · 이동 속도 */
    private static void recompute(ServerPlayer sp, StatTree.State s) {
        TOTALS.put(sp.getUUID(), Map.copyOf(StatTree.totals(spent(sp), s)));
        applySpeed(sp);
    }

    private static void applySpeed(ServerPlayer sp) {
        AttributeInstance inst = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (inst == null) return;
        double v = bonus(sp, "speed") / 100;
        AttributeModifier cur = inst.getModifier(SPEED_ID);
        if (v <= 0) {
            if (cur != null) inst.removeModifier(SPEED_ID);
        } else if (cur == null || Math.abs(cur.amount() - v) > 1e-9) {
            inst.addOrUpdateTransientModifier(new AttributeModifier(SPEED_ID, v, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    /** 뽑기권 주기 (가방이 차면 발밑에 — Notify.give) */
    private static void giveTickets(ServerPlayer sp, String item, int n) {
        for (int left = n; left > 0; left -= 64) {
            ItemStack st = RuneItems.ticketById(item, Math.min(64, left));
            if (!st.isEmpty()) Notify.give(sp, st);
        }
    }

    // ------------------------------------------------------------ 갈래 · 초기화

    /** 갈래 고르기 (index = 칸, option = 0 · 1). 잘못이면 그 까닭 (null = 골랐음) */
    public static String pick(ServerPlayer sp, String stat, int index, int option) {
        if (!isActive(sp)) return "§c스탯 트리를 쓸 수 없습니다";
        StatTree.Tree t = StatTree.tree(stat);
        if (t == null || index < 0 || index >= t.nodes().size()) return "§c그런 칸이 없습니다";
        StatTree.Node n = t.nodes().get(index);
        if (!n.fork()) return "§c갈래 칸이 아닙니다";
        if (option < 0 || option >= n.pick().size()) return "§c그런 갈래가 없습니다";
        String name = Stats.info(stat).str("name");
        if (Stats.get(spent(sp), stat) < n.at()) return "§c" + name + " " + n.at() + "부터 고를 수 있습니다 §7(지금 " + (int) Stats.get(spent(sp), stat) + ")";
        StatTree.State s = state(sp);
        if (s.picksOf(stat).containsKey(index)) return "§c이미 고른 갈래입니다 §7(바꾸려면 갈래 초기화)";
        StatTree.State next = s.withPick(stat, index, option);
        save(sp, next);
        recompute(sp, next);
        RpgHealth.refreshMax(sp, false);
        StatTree.Option o = n.pick().get(option);
        Notify.say(sp, color(stat) + name + " 나무 §f" + (index + 1) + "단계 갈래: §e「" + o.name() + "」 §a" + StatTree.bonusText(o.bonus()));
        pickFx(sp, t.color());
        sync(sp, "");
        return null;
    }

    /** 갈래 초기화 비용 (그 나무) */
    public static int resetCost(ServerPlayer sp, String stat) {
        return StatTree.resetCost(state(sp).pickCount(stat));
    }

    /** 갈래 초기화 (에메랄드). 잘못이면 그 까닭 (null = 지움) */
    public static String reset(ServerPlayer sp, String stat, boolean free) {
        if (!isActive(sp)) return "§c스탯 트리를 쓸 수 없습니다";
        if (StatTree.tree(stat) == null) return "§c그런 나무가 없습니다";
        StatTree.State s = state(sp);
        int n = s.pickCount(stat);
        if (n == 0) return "§7고른 갈래가 없습니다";
        int cost = free ? 0 : StatTree.resetCost(n);
        int have = Inv.count(sp, Inv.CURRENCY);
        if (have < cost) return "§c" + Inv.CURRENCY_NAME + "가 모자랍니다 §7(필요 " + cost + ", 가진 것 " + have + ")";
        if (cost > 0 && !Inv.remove(sp, Inv.CURRENCY, cost)) return "§c" + Inv.CURRENCY_NAME + "를 꺼낼 수 없습니다";
        StatTree.State next = s.withoutPicks(stat);
        save(sp, next);
        recompute(sp, next);
        RpgHealth.refreshMax(sp, false);
        Notify.say(sp, color(stat) + Stats.info(stat).str("name") + " 나무 §f갈래 초기화 §7(갈래 " + n + "개" + (cost > 0 ? " · " + Inv.CURRENCY_NAME + " " + cost + "개" : "")
                + ") — 다시 고를 수 있습니다");
        resetFx(sp, StatTree.tree(stat).color());
        sync(sp, "");
        return null;
    }

    /**
     * 나무 넷의 고른 갈래를 모두 지움 (관리자 — 에메랄드 없이). 받은 칸 기록 (claimed) 은 그대로: 어떤 초기화로도 뽑기권을 다시 주지 않음.
     * 지운 갈래 수
     */
    public static int clear(ServerPlayer sp) {
        StatTree.State s = state(sp);
        int n = 0;
        StatTree.State next = s;
        for (String stat : StatTree.stats()) {
            n += s.pickCount(stat);
            next = next.withoutPicks(stat);
        }
        save(sp, next);
        if (isActive(sp)) recompute(sp, next);
        RpgHealth.refreshMax(sp, false);
        sync(sp, "");
        return n;
    }

    // ------------------------------------------------------------ 알림 · 연출

    static String color(String stat) {
        return Stats.info(stat).str("color");
    }

    /** 새 칸 알림: 큰 글자 (가장 높은 칸 하나) · 채팅 (칸마다) · 보상 · 자라는 연출 */
    private static void announce(ServerPlayer sp, List<Grown> grown) {
        Grown last = grown.get(grown.size() - 1);
        StatTree.Tree lt = StatTree.tree(last.stat());
        StatTree.Node ln = lt.nodes().get(last.index());
        String title = color(last.stat()) + "§l" + Stats.info(last.stat()).str("name") + " 나무 " + (last.index() + 1) + "단계";
        String sub = ln.fork() ? "§e갈래를 고르세요 §7(" + StatTree.nodeName(ln) + ")" : "§f" + ln.name() + " §a" + StatTree.bonusText(ln.bonus());
        Notify.title(sp, title, sub, 6, 50, 14);
        Map<String, Integer> total = new LinkedHashMap<>();
        for (Grown g : grown) {
            StatTree.Node n = StatTree.tree(g.stat()).nodes().get(g.index());
            String head = color(g.stat()) + Stats.info(g.stat()).str("name") + " 나무 §f" + (g.index() + 1) + "단계 ";
            if (n.fork()) {
                sp.sendSystemMessage(forkLine(head, g.stat(), n));
            } else {
                Notify.say(sp, head + "§e「" + n.name() + "」 §a" + StatTree.bonusText(n.bonus()));
            }
            for (Map.Entry<String, Integer> e : g.reward().entrySet()) total.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        if (!total.isEmpty()) {
            List<String> parts = new ArrayList<>();
            total.forEach((item, n) -> parts.add(StatTree.itemName(item) + " ×" + n));
            Notify.say(sp, "§d스탯 트리 보상: §f" + String.join(" · ", parts) + " §7(가방)");
        }
        growFx(sp, lt.color(), ln.fork(), !total.isEmpty());
    }

    /** 갈래 알림 줄: 갈래 이름을 누르면 그 갈래를 고름 (모드가 없어도 채팅으로) · [스탯 트리] 를 누르면 창 */
    private static MutableComponent forkLine(String head, String stat, StatTree.Node n) {
        MutableComponent line = Component.literal(BedrockText.toJava(Notify.PREFIX + head + "§e갈래 — 하나를 고르세요: "));
        for (int i = 0; i < n.pick().size(); i++) {
            StatTree.Option o = n.pick().get(i);
            String cmd = "/stattree pick " + stat + " " + (n.index() + 1) + " " + (i + 1);
            line.append(Component.literal("[" + o.name() + "]").withStyle(st -> st.withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent.RunCommand(cmd))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(BedrockText.toJava("§a" + StatTree.bonusText(o.bonus()) + "\n§7누르면 고름 (바꾸려면 갈래 초기화)"))))));
            line.append(Component.literal(" "));
        }
        line.append(Component.literal("[스탯 트리]").withStyle(st -> st.withColor(ChatFormatting.GOLD).withClickEvent(new ClickEvent.RunCommand("/stattree " + stat))));
        return line;
    }

    /** 색 (0 ~ 1) → ARGB */
    private static int argb(double[] c) {
        int r = (int) Math.round(Math.max(0, Math.min(1, c[0])) * 255);
        int g = (int) Math.round(Math.max(0, Math.min(1, c[1])) * 255);
        int b = (int) Math.round(Math.max(0, Math.min(1, c[2])) * 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** 나무가 자람: 발밑 마법진 · 빛기둥 · 퍼지는 고리 · 그 스탯 색으로 피어오르는 빛 (효과 파티클) · 빛 알갱이 · 소리 (갈래면 노란 고리 하나 더) */
    static void growFx(ServerPlayer sp, double[] color, boolean fork, boolean reward) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        SkillFx.rune(level, c, Opt.of("size", 2.0, "life", 1.3, "color", color, "alpha", 0.9, "progress", 1.0, "rate", 30.0, "grow", 0.25));
        SkillFx.fx(level, "rpg:arrow_shot", V.up(c, 3.2), Opt.of("dir", V.UP, "speed", 0.0, "size", 3.2, "thick", 0.45, "life", 0.7, "color", color, "alpha", 0.85));
        SkillFx.fx(level, "rpg:arrow_shot", V.up(c, 3.0), Opt.of("dir", V.UP, "speed", 0.0, "size", 3.0, "thick", 0.16, "life", 0.6, "color", SkillFx.WHITE, "alpha", 1.0));
        SkillFx.shock(level, c, color, 2.4, 0.6);
        SkillFx.sparks(level, V.up(c, 1.1), color, 14, 4);
        SkillFx.particle(level, ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, argb(color)), V.up(c, 1.0), 36, 0.7, 1.0);
        SkillFx.play(level, "random.levelup", c, 1.5, 0.7);
        SkillFx.play(level, "chime.amethyst_block", c, 1.2, 1.0);
        if (fork) Tasks.later(6, () -> {
            if (!sp.isRemoved()) SkillFx.shock(sp.level(), sp.position(), new double[] {1, 0.86, 0.3}, 1.6, 0.5);
        });
        if (reward) Tasks.later(8, () -> {
            if (!sp.isRemoved()) SkillFx.play(sp.level(), "random.orb", sp.position(), 1.4, 0.6);
        });
    }

    private static void pickFx(ServerPlayer sp, double[] color) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        SkillFx.shock(level, c, new double[] {1, 0.86, 0.3}, 1.8, 0.5);
        SkillFx.rune(level, c, Opt.of("size", 1.4, "life", 0.8, "color", color, "alpha", 0.85, "progress", 1.0, "rate", 60.0));
        SkillFx.sparks(level, V.up(c, 1.0), color, 10, 3);
        SkillFx.play(level, "beacon.power", c, 1.6, 0.7);
    }

    private static void resetFx(ServerPlayer sp, double[] color) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        SkillFx.shock(level, c, SkillFx.WHITE, 1.6, 0.5);
        SkillFx.particle(level, ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, argb(color)), V.up(c, 1.0), 16, 0.6, 1.0);
        SkillFx.play(level, "item.book.page_turn", c, 0.8, 1.0);
        SkillFx.play(level, "random.fizz", c, 1.4, 0.4);
    }

    // ------------------------------------------------------------ 클라이언트

    /** 클라이언트에 기록 · 보너스 합 보내기 (open = 열 탭 — "" 면 열지 않음) */
    public static void sync(ServerPlayer sp, String open) {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean on = isActive(sp);
        m.put("on", on);
        m.put("state", Json.parse(state(sp).json()));
        m.put("totals", new LinkedHashMap<>(totals(sp)));
        m.put("open", open == null ? "" : open);
        m.put("money", Inv.count(sp, Inv.CURRENCY));
        RpgNet.statTree(sp, new RpgNet.StatTreePayload(Json.stringify(m)));
    }

    /** 스탯 트리 창 열기 (모드 클라이언트 — 아니면 채팅 요약) */
    public static void open(ServerPlayer sp, String stat) {
        if (!isActive(sp)) {
            Notify.warn(sp, "§c스탯 트리를 쓸 수 없습니다");
            return;
        }
        String s = stat == null || StatTree.tree(stat) == null ? StatTree.stats().get(0) : stat;
        if (RpgNet.canSendStatTree(sp)) {
            RpgNet.sync(sp, false);
            sync(sp, s);
            return;
        }
        StatTreeCommands.chatSummary(sp);
    }
}
