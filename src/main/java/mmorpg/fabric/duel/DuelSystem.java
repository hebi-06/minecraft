package mmorpg.fabric.duel;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import mmorpg.core.Obj;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Props;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.ui.DialogForm;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.MenuForm;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * 결투 (PvP — pvp.js 의 자바 판). 애드온 사용자 요청: "/pvp [플레이어 닉네임] 치면 pvp 수락 또는 거절 버튼이 나오고 수락버튼을 누르면 pvp가 가능하게 하고
 * pvp를 할때 데미지를 감소시켜서 받게 해" — 피해 90% 감소 · 평타 + 스킬 + 룬 · 쓰러지거나 /pvp 끝 (나가도 끝).
 * <ul>
 * <li>/pvp 닉네임 (/결투): 신청 → 상대 화면 아래 대화창에 수락 · 거절 (PVP.requestTicks 안에 답이 없으면 취소 · 창도 닫음). /pvp 수락 · /pvp 거절 로도 답함
 * (창을 닫으면 신청은 남음). 서로 신청하면 바로 수락. 받는 사람이 싸우는 중 (방금 다침) 이면 창 없이 글로만, 다른 창이 열려 있으면 닫힐 때까지 기다림.
 * /pvp 만 치면 결투 창 (접속한 사람 목록 · 받은 신청 · 결투 끝내기 · 신청 받지 않기). /pvp 차단 = 신청 받지 않기 (다시 치면 받기)</li>
 * <li>수락하면 3 · 2 · 1 (PVP.countdownTicks) 뒤 시작: 둘만 서로 평타 · 활 · 완드 · 스킬 · 룬 (반사 포함) 피해 × PVP.mult (길들인 동물의 피해는 주인의 것).
 * 결투 상대가 아닌 플레이어 사이의 피해는 막음 (PVP.protectOthers). 회복 · 보호막 · 방벽은 결투 중인 사람에게 주지 않음</li>
 * <li>끝: 쓰러짐 (PVP.lethal 이 아니면 결투 상대 (펫 포함) 의 피해, 또는 결투 상대가 PVP.blameTicks 안에 때린 뒤의 불 · 용암 · 떨어짐 · 위더로는 죽지 않고
 * 체력 1로 남고 짐 — RpgHealth 결투 훅. 진 뒤 PVP.graceTicks 동안은 남은 불 · 용암 · 떨어짐 · 위더로 쓰러지지 않음) · 몹 · 보스 · /kill 에 죽음 ·
 * /pvp 끝 · 나감 · 생존 · 모험 모드가 아니게 됨</li>
 * <li>세계의 PvP (게임 규칙 pvp) 가 꺼져 있으면 결투 동안만 켜고, 결투가 모두 끝나면 (또는 다시 불러오면) 되돌림</li>
 * <li>연출: 3 · 2 · 1 제목 · 발밑에 그려지는 붉은 마법진 · 머리 위 결투 표식 (교차한 두 검 rpg:rfx_duel) → 시작 충격파, 결투 동안 두 사람 머리 위 표식,
 * 끝나면 승리 (금빛) · 패배 · 끝 제목. 상태 줄에 상대 이름 · 남은 체력 ({@link Duels#hud}), 맞으면 머리 위에 빨간 피해 숫자</li>
 * </ul>
 */
public final class DuelSystem {
    private DuelSystem() {}

    static final Obj PVP = Duels.PVP;
    static final double MULT = PVP.num("mult");
    static final int REQUEST = (int) PVP.num("requestTicks");
    static final int COUNTDOWN = (int) PVP.num("countdownTicks");
    static final int RETRY = (int) PVP.num("retryTicks");
    static final int ASK_GAP = (int) PVP.num("askGapTicks");
    static final int QUIET = (int) PVP.num("quietIfHurtTicks");
    static final boolean LETHAL = PVP.truthy("lethal");
    static final int GRACE = (int) PVP.num("graceTicks");
    static final int BLAME = (int) PVP.num("blameTicks");
    static final boolean PROTECT = PVP.truthy("protectOthers");
    static final int MARK_EVERY = Math.max(1, (int) PVP.num("markEvery"));
    static final double[] COLOR = BossKit.rgb(PVP, "color");
    static final double[] CORE = BossKit.rgb(PVP, "core");
    static final double[] WIN = BossKit.rgb(PVP, "win");

    static final Set<String> END_WORDS = Set.of("끝", "end", "stop", "그만", "끝내기");
    static final Set<String> ACCEPT_WORDS = Set.of("수락", "accept", "yes");
    static final Set<String> DECLINE_WORDS = Set.of("거절", "decline", "no");
    static final Set<String> BLOCK_WORDS = Set.of("차단", "block");
    /** 우리가 결투 때문에 세계의 PvP 를 켰는지 (세계에 저장 — 결투 중에 세계를 닫아도 다음에 되돌림) */
    static final String RULE_KEY = "rpg:pvp_rule_by_duel";
    /** 결투 신청을 받지 않음 (플레이어에 저장) */
    static final String BLOCK_KEY = "rpg:pvp_block";

    /** 받은 신청 (받는 사람 하나에 하나) */
    static final class Req {
        final int id;
        final UUID from;
        final String fromName;
        final UUID to;
        final String toName;
        final long at;
        final long until;
        /** 우리 대화창 번호 (열려 있으면 Forms.pendingId 와 같음, 없으면 -1) */
        int formId = -1;

        Req(int id, UUID from, String fromName, UUID to, String toName, long at, long until) {
            this.id = id;
            this.from = from;
            this.fromName = fromName;
            this.to = to;
            this.toName = toName;
            this.at = at;
            this.until = until;
        }
    }

    /** 받은 신청: 받는 사람 → 신청 */
    private static final Map<UUID, Req> REQUESTS = new HashMap<>();
    /** 보낸 신청: 보낸 사람 → 받는 사람 */
    private static final Map<UUID, UUID> OUTGOING = new HashMap<>();
    /** 거절 · 만료 · 취소된 틱: "보낸 사람|받는 사람" → 틱 (PVP.retryTicks 동안 그 사람에게 다시 신청하지 못함) */
    private static final Map<String, Long> REFUSED = new HashMap<>();
    /** 마지막으로 신청을 보낸 틱 (PVP.askGapTicks — 누구에게든) */
    private static final Map<UUID, Long> LAST_ASK = new HashMap<>();
    /** 결투에서 진 사람 → 이 틱까지는 남은 불 · 용암 · 떨어짐 · 위더로 쓰러지지 않음 */
    private static final Map<UUID, Long> GRACE_UNTIL = new HashMap<>();
    /** 결투 상대에게 (또는 그 탓으로) 쓰러진 사람 → 틱 (다음 틱 lost 가 결투를 끝냄) */
    private static final Map<UUID, Long> LOST_AT = new HashMap<>();
    private static int nextRequest = 1;
    private static MinecraftServer server;

    public static void init() {
        RpgHealth.duel = new RpgHealth.DuelHooks() {
            @Override
            public double scale(ServerPlayer sp, DamageSource source, double loss) {
                return scaleLoss(sp, source, loss);
            }

            @Override
            public boolean floor(ServerPlayer sp, DamageSource source) {
                return duelFloor(sp, source);
            }

            @Override
            public void lost(ServerPlayer sp, DamageSource source) {
                duelLost(sp);
            }
        };
        Kit.duelPartner = Duels::isPartner;
        RuneKit.canAssist = Duels::canAssist;
        Notify.addHudSuffix(Duels::hud);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> allow(entity, source));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer dead)) return;
            Duels.Duel d = Duels.of(dead.getUUID());
            if (d == null) return;
            ServerPlayer from = Duels.attackerOf(source);
            endDuel(dead.getUUID(), from != null && from.getUUID().equals(d.partner()) ? "defeat" : "fell", dead.getPlainTextName());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> onLeave(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            long now = Tasks.now();
            if (now % 10 == 0) tick(s);
            if (now % MARK_EVERY == 0) markTick(s);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            Tasks.later(1, () -> restoreRule(s)); // 결투 중에 세계를 닫았으면 켜 둔 PvP 를 되돌림 (결투는 저장하지 않음)
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            Duels.clearAll();
            REQUESTS.clear();
            OUTGOING.clear();
            REFUSED.clear();
            LAST_ASK.clear();
            GRACE_UNTIL.clear();
            LOST_AT.clear();
            server = null;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    // ------------------------------------------------------------ 찾기 · 확인

    private static MinecraftServer server(ServerPlayer p) {
        return p != null ? p.level().getServer() : server;
    }

    static ServerPlayer player(MinecraftServer s, UUID id) {
        if (s == null || id == null) return null;
        ServerPlayer p = s.getPlayerList().getPlayer(id);
        return p != null && !p.isRemoved() ? p : null;
    }

    private static List<ServerPlayer> online(MinecraftServer s) {
        return s == null ? List.of() : new ArrayList<>(s.getPlayerList().getPlayers());
    }

    private static String nameOf(ServerPlayer p) {
        return p.getPlainTextName();
    }

    /** 이름으로 접속한 플레이어 찾기: 똑같은 이름 (대소문자 무시) → 그 이름으로 시작하는 사람이 하나뿐이면 그 사람. 못 찾으면 null 과 까닭 */
    static Object[] findPlayer(ServerPlayer self, String name) {
        String want = name == null ? "" : name.trim();
        if (want.startsWith("@")) return new Object[] {null, "§c선택자 (@p · @a 등) 는 쓸 수 없습니다. 이름을 쓰거나 /pvp 만 쳐서 목록에서 고르세요."};
        String low = want.toLowerCase(Locale.ROOT);
        List<ServerPlayer> list = online(server(self));
        List<ServerPlayer> exact = new ArrayList<>();
        List<ServerPlayer> pre = new ArrayList<>();
        for (ServerPlayer p : list) {
            String n = nameOf(p).toLowerCase(Locale.ROOT);
            if (n.equals(low)) exact.add(p);
            if (n.startsWith(low)) pre.add(p);
        }
        if (exact.size() == 1) return new Object[] {exact.get(0), null};
        if (!low.isEmpty() && pre.size() == 1) return new Object[] {pre.get(0), null};
        if (!low.isEmpty() && pre.size() > 1) {
            return new Object[] {null, "§c'" + want + "' 로 시작하는 사람이 여럿입니다: §f" + String.join(", ", pre.stream().map(DuelSystem::nameOf).toList())};
        }
        List<String> others = new ArrayList<>();
        for (ServerPlayer p : list) if (p != self && others.size() < 12) others.add(nameOf(p));
        return new Object[] {null, "§c'" + want + "' 님을 찾을 수 없습니다." + (others.isEmpty() ? " §7다른 접속자가 없습니다." : " §7접속한 사람: §f" + String.join(", ", others))};
    }

    private static boolean live(Req req) {
        return req != null && REQUESTS.get(req.to) == req && Tasks.now() < req.until;
    }

    static void say(ServerPlayer p, String text) {
        if (p != null && !p.isRemoved()) p.sendSystemMessage(Component.literal(BedrockText.toJava(text)));
    }

    private static void sound(ServerPlayer p, String id, double pitch, double volume) {
        if (p != null && !p.isRemoved()) SkillFx.playFor(p, id, pitch, volume);
    }

    /** 결투 신청을 받지 않는지 (/pvp 차단) */
    public static boolean blocksDuels(ServerPlayer p) {
        return "true".equals(Props.get(p, BLOCK_KEY));
    }

    static void setBlock(ServerPlayer p, boolean on) {
        Props.set(p, BLOCK_KEY, on ? "true" : null);
        say(p, on ? "§7이제 결투 신청을 받지 않습니다. §f(다시 받기: /pvp 차단)" : "§7이제 결투 신청을 받습니다.");
    }

    private static boolean rpgMode(ServerPlayer p) {
        return RpgHealth.isRpgMode(p);
    }

    // ------------------------------------------------------------ 연출

    /** 결투 표식 (교차한 두 검): 빛 (붉은색 — 또는 color) + 심지 (흰 금빛) 두 장. vel = 몸을 따라오는 속도 (칸/초) */
    static void duelMark(ServerLevel level, Vec3 at, Opt o) {
        double alpha = o.num("alpha", 1);
        Opt base = Opt.of("dir", V.UP, "size", o.num("size", 0.3), "life", o.num("life", 1), "spin", 0, "spinRate", 0, "grow", o.num("grow", 0), "frames", 1,
                "fps", 0, "loop", 0, "phase", 0, "fade", o.num("fade", 0), "vel", o.vec("vel", Vec3.ZERO), "alpha", alpha);
        RuneKit.rfx(level, "duel", at, base.with("cell", 0, "color", o.color("color", COLOR)));
        RuneKit.rfx(level, "duel", at, base.with("cell", 1, "color", CORE));
    }

    /** 머리 위 (결투 표식 자리) */
    static Vec3 overHead(ServerPlayer p, double h) {
        return p.getEyePosition().add(0, h, 0);
    }

    /** 몸이 움직이는 속도 (칸/초, 수평 — 표식이 몸을 따라오게) */
    static Vec3 bodyVel(ServerPlayer p) {
        return new Vec3((p.getX() - p.xo) * 20, 0, (p.getZ() - p.zo) * 20);
    }

    /** 3 · 2 · 1 한 번 (left = 남은 초): 제목 · 종소리 · 발밑에 그려지는 붉은 마법진 · 머리 위 표식 */
    static void countdownCue(ServerPlayer p, int left, String other) {
        Notify.title(p, "§c§l" + left, "§f" + other + " §7님과 결투", 0, 16, 4);
        sound(p, "block.bell.hit", 0.8 + (3 - left) * 0.15, 0.8);
        int total = Math.max(1, Math.round(COUNTDOWN / 20F));
        ServerLevel level = p.level();
        SkillFx.rune(level, p.position(), Opt.of("size", 1.5, "life", 1.05, "color", COLOR, "alpha", 0.85, "progress", Math.min(1.0, (total - left + 1) / (double) total),
                "rate", 30));
        duelMark(level, overHead(p, 0.9), Opt.of("size", 0.42, "life", 1.0, "fade", 0.25, "grow", 0.15, "vel", bodyVel(p)));
    }

    /** 결투 시작: 제목 (짧게) · 충격파 · 큰 표식 */
    static void startCue(ServerPlayer p, String other) {
        Notify.title(p, "§c§l결투 시작!", "§f상대: " + other + " §7· 받는 피해 " + Math.round((1 - MULT) * 100) + "% 감소", 0, 12, 4);
        ServerLevel level = p.level();
        Vec3 c = p.position();
        SkillFx.shock(level, c, COLOR, 1.6, 0.5);
        SkillFx.burst(level, V.up(c, 1.1), COLOR, 1.1, 0.3);
        SkillFx.sparks(level, V.up(c, 1.1), CORE, 8, 4);
        duelMark(level, overHead(p, 0.9), Opt.of("size", 0.55, "life", 0.7, "grow", 0.6, "fade", 0.6, "vel", bodyVel(p)));
        SkillFx.play(level, "mob.evocation_illager.prepare_attack", c, 1.0, 0.9);
        SkillFx.play(level, "block.bell.hit", c, 1.5, 1.0);
    }

    /** 끝 연출: kind = win (금빛) · lose (잿빛) · end (조용히) */
    static void endCue(ServerPlayer p, String kind, String title, String subtitle) {
        if (p == null || p.isRemoved()) return;
        Notify.title(p, title, subtitle, 4, 30, 10);
        ServerLevel level = p.level();
        Vec3 c = p.position();
        Vec3 chest = V.up(c, 1.1);
        switch (kind) {
            case "win" -> {
                SkillFx.burst(level, chest, WIN, 1.3, 0.35);
                SkillFx.sparks(level, chest, WIN, 12, 5);
                SkillFx.shock(level, c, WIN, 1.8, 0.55);
                duelMark(level, overHead(p, 0.9), Opt.of("size", 0.5, "life", 0.8, "grow", 0.5, "fade", 0.6, "color", WIN, "vel", bodyVel(p)));
                SkillFx.play(level, "random.levelup", c, 1.0, 1.0);
                SkillFx.play(level, "firework.twinkle", c, 1.0, 0.8);
            }
            case "lose" -> {
                SkillFx.burst(level, chest, new double[] {0.45, 0.45, 0.5}, 1.0, 0.4);
                SkillFx.sparks(level, chest, new double[] {0.6, 0.6, 0.65}, 6, 2);
                SkillFx.play(level, "random.anvil_land", c, 0.6, 0.7);
            }
            default -> sound(p, "beacon.deactivate", 1.2, 0.7);
        }
    }

    // ------------------------------------------------------------ 세계의 PvP 설정

    static boolean pvpOn(MinecraftServer s) {
        return s.getGameRules().get(GameRules.PVP);
    }

    private static void setPvp(MinecraftServer s, boolean on) {
        s.getGameRules().set(GameRules.PVP, on, s);
        for (ServerLevel l : s.getAllLevels()) if (l.getGameRules() != s.getGameRules()) l.getGameRules().set(GameRules.PVP, on, s);
    }

    /** 결투 동안 세계의 PvP 가 꺼져 있으면 켬 (결투가 아닌 사람 사이의 피해는 막으므로 꺼진 것과 같음) */
    static void ensureRule(MinecraftServer s) {
        if (s == null || pvpOn(s)) return;
        Props.setWorld(s, RULE_KEY, "true");
        setPvp(s, true);
    }

    /** 결투가 모두 끝났고 우리가 켠 것이면 되돌림 (다시 불러온 뒤에도) */
    static void restoreRule(MinecraftServer s) {
        if (s == null || Duels.any() || !"true".equals(Props.world(s, RULE_KEY))) return;
        Props.setWorld(s, RULE_KEY, null);
        setPvp(s, false);
    }

    // ------------------------------------------------------------ 신청 · 답

    private static void dropRequest(Req req) {
        if (REQUESTS.get(req.to) == req) REQUESTS.remove(req.to);
        if (req.to.equals(OUTGOING.get(req.from))) OUTGOING.remove(req.from);
    }

    /** 이 사람이 보냈거나 받은 신청을 모두 취소 (결투를 시작하거나 나갈 때). why = 상대에게 보일 까닭 */
    private static void cancelRequestsOf(MinecraftServer s, UUID id, Function<Req, String> why) {
        for (Req req : new ArrayList<>(REQUESTS.values())) {
            if (!req.from.equals(id) && !req.to.equals(id)) continue;
            dropRequest(req);
            closeIfShown(s, req);
            ServerPlayer other = player(s, req.from.equals(id) ? req.to : req.from);
            if (other != null && why != null) say(other, why.apply(req));
        }
    }

    /** 우리 신청 창이 아직 열려 있으면 닫음 (다른 창은 그대로) */
    private static void closeIfShown(MinecraftServer s, Req req) {
        if (req.formId < 0) return;
        ServerPlayer t = player(s, req.to);
        if (t != null && Forms.pendingId(t) == req.formId) Forms.close(t);
        req.formId = -1;
    }

    /** 결투 신청 창 (받는 사람 화면 아래 대화창). 다른 창이 열려 있으면 닫힐 때까지 기다림 (5틱마다) */
    private static void ask(ServerPlayer target, Req req, int tries) {
        if (!live(req) || target.isRemoved()) return;
        if (Forms.isOpen(target) || target.containerMenu != target.inventoryMenu) {
            if (tries == 2) Notify.bar(target, "§e⚔ " + req.fromName + " 님의 결투 신청 §7— 창을 닫으면 결투 창이 열립니다 (/pvp 수락 · /pvp 거절)");
            Tasks.later(5, () -> {
                ServerPlayer t = player(server(target), req.to);
                if (t != null) ask(t, req, tries + 1);
            });
            return;
        }
        int pct = (int) Math.round((1 - MULT) * 100);
        String body = "§f결투 (PvP) 를 신청합니다!\n§7· 서로에게만 평타 · 스킬 · 룬 피해 §f(받는 피해 " + pct + "% 감소)\n§7· "
                + (LETHAL ? "쓰러지면 (죽음)" : "쓰러지면 (체력 1로 남음)") + " · /pvp 끝 · 나가면 끝\n§7· " + Math.round(REQUEST / 20F) + "초 안에 답하세요";
        req.formId = new DialogForm("§c⚔ §f" + req.fromName, body).choice("§2⚔ 결투 수락", DialogForm.OK).choice("§4거절", DialogForm.BYE).show(target, sel -> {
            req.formId = -1;
            if (!live(req)) {
                if (sel >= 0) say(target, "§7이미 끝난 결투 신청입니다.");
                return;
            }
            if (sel < 0) {
                say(target, "§7창을 닫았습니다. §f/pvp 수락 · /pvp 거절 §7로 답할 수 있습니다 (" + (long) Math.ceil((req.until - Tasks.now()) / 20.0) + "초 안).");
                return;
            }
            answer(server(target), req, sel == 0);
        });
    }

    /** 신청에 답함 (창 · /pvp 수락 · /pvp 거절 · 결투 창) */
    static void answer(MinecraftServer s, Req req, boolean yes) {
        if (!live(req)) return;
        dropRequest(req);
        closeIfShown(s, req);
        ServerPlayer from = player(s, req.from);
        ServerPlayer to = player(s, req.to);
        if (to == null) return;
        if (from == null) {
            say(to, "§7" + req.fromName + " 님이 나가서 결투 신청이 취소되었습니다.");
            return;
        }
        if (!yes) {
            REFUSED.put(req.from + "|" + req.to, Tasks.now());
            say(from, "§c⚔ " + req.toName + " 님이 결투를 거절했습니다.");
            say(to, "§7" + req.fromName + " 님의 결투 신청을 거절했습니다.");
            sound(from, "note.bass", 0.8, 0.8);
            return;
        }
        ServerPlayer busy = Duels.inDuel(from.getUUID()) ? from : Duels.inDuel(to.getUUID()) ? to : null;
        if (busy != null) {
            String msg = "§c" + nameOf(busy) + " 님이 이미 다른 사람과 결투 중이라 시작하지 못했습니다.";
            say(from, msg);
            say(to, msg);
            return;
        }
        ServerPlayer notRpg = !rpgMode(from) ? from : !rpgMode(to) ? to : null;
        if (notRpg != null) {
            String msg = "§c" + nameOf(notRpg) + " 님이 생존 · 모험 모드가 아니라 결투를 시작하지 못했습니다.";
            say(from, msg);
            say(to, msg);
            return;
        }
        startDuel(from, to);
    }

    /** /pvp 닉네임 */
    public static void requestDuel(ServerPlayer player, String name) {
        Object[] found = findPlayer(player, name);
        ServerPlayer target = (ServerPlayer) found[0];
        if (target == null) {
            say(player, found[1] != null ? (String) found[1] : "§c찾을 수 없습니다.");
            return;
        }
        requestTo(player, target);
    }

    /** 결투 신청 (찾은 사람에게 — 결투 창 · 시험도 이것으로) */
    public static void requestTo(ServerPlayer player, ServerPlayer target) {
        MinecraftServer s = server(player);
        server = s;
        if (target == player) {
            say(player, "§c자기 자신에게는 결투를 신청할 수 없습니다.");
            return;
        }
        Duels.Duel mine = Duels.of(player.getUUID());
        if (mine != null) {
            say(player, "§c이미 " + mine.partnerName() + " 님과 결투 중입니다. §7(끝내기: /pvp 끝)");
            return;
        }
        if (Duels.inDuel(target.getUUID())) {
            say(player, "§c" + nameOf(target) + " 님은 다른 사람과 결투 중입니다.");
            return;
        }
        if (!rpgMode(player)) {
            say(player, "§c생존 · 모험 모드에서만 결투할 수 있습니다.");
            return;
        }
        if (!rpgMode(target)) {
            say(player, "§c" + nameOf(target) + " 님은 생존 · 모험 모드가 아니라 결투할 수 없습니다.");
            return;
        }
        // 상대가 먼저 나에게 신청했으면 바로 수락
        Req theirs = REQUESTS.get(player.getUUID());
        if (live(theirs) && theirs.from.equals(target.getUUID())) {
            answer(s, theirs, true);
            return;
        }
        if (blocksDuels(target)) {
            say(player, "§7" + nameOf(target) + " 님은 결투 신청을 받지 않습니다.");
            return;
        }
        long now = Tasks.now();
        Req pending = REQUESTS.get(target.getUUID());
        if (live(pending)) {
            if (pending.from.equals(player.getUUID())) say(player, "§7이미 " + nameOf(target) + " 님에게 신청했습니다. §f(" + (long) Math.ceil((pending.until - now) / 20.0) + "초 남음)");
            else say(player, "§c" + nameOf(target) + " 님이 다른 결투 신청에 답하는 중입니다. 잠시 뒤에 다시 신청하세요.");
            return;
        }
        Long back = REFUSED.get(player.getUUID() + "|" + target.getUUID());
        if (back != null && now - back < RETRY) {
            say(player, "§7" + nameOf(target) + " 님에게는 " + (long) Math.ceil((RETRY - (now - back)) / 20.0) + "초 뒤에 다시 신청할 수 있습니다.");
            return;
        }
        Long last = LAST_ASK.get(player.getUUID());
        if (last != null && now - last < ASK_GAP) {
            say(player, "§7" + (long) Math.ceil((ASK_GAP - (now - last)) / 20.0) + "초 뒤에 다시 신청할 수 있습니다.");
            return;
        }
        // 다른 사람에게 보낸 신청은 취소 (한 번에 하나 — 그 사람에게는 retryTicks 동안 다시 보내지 못함)
        UUID prevTo = OUTGOING.get(player.getUUID());
        Req prev = prevTo != null ? REQUESTS.get(prevTo) : null;
        if (prev != null && prev.from.equals(player.getUUID())) {
            dropRequest(prev);
            closeIfShown(s, prev);
            REFUSED.put(prev.from + "|" + prev.to, now);
            say(player(s, prev.to), "§7" + nameOf(player) + " 님이 결투 신청을 취소했습니다.");
        }
        Req req = new Req(nextRequest++, player.getUUID(), nameOf(player), target.getUUID(), nameOf(target), now, now + REQUEST);
        REQUESTS.put(target.getUUID(), req);
        OUTGOING.put(player.getUUID(), target.getUUID());
        LAST_ASK.put(player.getUUID(), now);
        long secs = Math.round(REQUEST / 20F);
        say(player, "§e⚔ " + nameOf(target) + " 님에게 결투를 신청했습니다. §7(" + secs + "초 안에 답이 없으면 취소)");
        sound(target, "block.bell.hit", 0.8, 1.0);
        // 받는 사람이 싸우는 중 (방금 다침) 이면 창을 띄우지 않음 (창이 열리면 움직이지 못함) — 글로만
        boolean fighting = now - Kit.lastHurtTick(target, null) < QUIET;
        if (fighting) {
            say(target, "§e⚔ " + nameOf(player) + " 님이 결투를 신청했습니다. §7싸우는 중이라 창을 띄우지 않았습니다 — §f/pvp 수락 · /pvp 거절 §7(또는 /pvp 로 창, " + secs + "초 안)");
            Notify.bar(target, "§e⚔ " + nameOf(player) + " 님의 결투 신청 §7— /pvp 수락 · /pvp 거절");
            return;
        }
        say(target, "§e⚔ " + nameOf(player) + " 님이 결투를 신청했습니다. §7(창에서 수락 · 거절, 또는 /pvp 수락 · /pvp 거절)");
        ask(target, req, 0);
    }

    /** 받은 신청에 글로 답함 (/pvp 수락 · /pvp 거절) */
    static void answerTyped(ServerPlayer player, boolean yes) {
        Req req = REQUESTS.get(player.getUUID());
        if (!live(req)) {
            say(player, "§7받은 결투 신청이 없습니다.");
            return;
        }
        answer(server(player), req, yes);
    }

    // ------------------------------------------------------------ 결투

    static void startDuel(ServerPlayer a, ServerPlayer b) {
        MinecraftServer s = server(a);
        cancelRequestsOf(s, a.getUUID(), r -> "§7" + (r.from.equals(a.getUUID()) ? r.fromName : r.toName) + " 님이 다른 결투를 시작해 신청이 취소되었습니다.");
        cancelRequestsOf(s, b.getUUID(), r -> "§7" + (r.from.equals(b.getUUID()) ? r.fromName : r.toName) + " 님이 다른 결투를 시작해 신청이 취소되었습니다.");
        ensureRule(s);
        GRACE_UNTIL.remove(a.getUUID());
        GRACE_UNTIL.remove(b.getUUID());
        long start = Tasks.now() + COUNTDOWN;
        int token = Duels.set(a, b, start);
        int pct = (int) Math.round((1 - MULT) * 100);
        int secs = Math.max(1, Math.round(COUNTDOWN / 20F));
        for (ServerPlayer[] po : new ServerPlayer[][] {{a, b}, {b, a}}) {
            say(po[0], "§6⚔ " + nameOf(po[1]) + " 님과 결투! §7" + secs + "초 뒤 시작 · 받는 피해 " + pct + "% 감소 · "
                    + (LETHAL ? "쓰러지면 짐 (죽음)" : "쓰러지면 짐 (체력 1로 남음)") + " · 끝내기 /pvp 끝");
        }
        UUID ia = a.getUUID();
        UUID ib = b.getUUID();
        String na = nameOf(a);
        String nb = nameOf(b);
        for (int i = 0; i < secs; i++) {
            int left = secs - i;
            Runnable cue = () -> {
                if (!same(ia, token)) return;
                ServerPlayer pa = player(s, ia);
                ServerPlayer pb = player(s, ib);
                if (pa != null) countdownCue(pa, left, nb);
                if (pb != null) countdownCue(pb, left, na);
            };
            if (i == 0) cue.run();
            else Tasks.later(Math.round((float) i * COUNTDOWN / secs), cue);
        }
        Tasks.later(Math.max(1, COUNTDOWN), () -> {
            if (!same(ia, token)) return;
            ServerPlayer pa = player(s, ia);
            ServerPlayer pb = player(s, ib);
            if (pa != null) startCue(pa, nb);
            if (pb != null) startCue(pb, na);
        });
    }

    private static boolean same(UUID id, int token) {
        Duels.Duel d = Duels.of(id);
        return d != null && d.token() == token;
    }

    /**
     * 결투 끝 (둘 다). reason: defeat (id 가 쓰러져 짐 — 상대가 이김) · fell (몹 · 보스 등에 죽음) · command (/pvp 끝) · left (나감) · mode (생존 · 모험이 아니게 됨).
     * @param id 끝나게 한 사람 (진 사람 · 죽은 사람 · /pvp 끝을 친 사람 · 나간 사람)
     */
    static boolean endDuel(UUID id, String reason, String name) {
        Duels.Duel d = Duels.clear(id);
        if (d == null) return false;
        MinecraftServer s = server;
        ServerPlayer me = player(s, id);
        ServerPlayer other = player(s, d.partner());
        String myName = me != null ? nameOf(me) : name != null ? name : "상대";
        long now = Tasks.now();
        switch (reason) {
            case "defeat" -> {
                if (!LETHAL && LOST_AT.containsKey(d.partner())) GRACE_UNTIL.put(d.partner(), now + GRACE); // 같은 틱에 서로 쓰러뜨림: 이긴 사람도 체력 1 — 잠깐 보호
                if (!LETHAL && me != null) {
                    // 쓰러졌지만 죽지 않음: 잠깐 (graceTicks) 은 남은 불 · 용암 · 떨어짐 · 위더로 쓰러지지 않고, 남은 불 · 독 · 위더는 없앰
                    GRACE_UNTIL.put(id, now + GRACE);
                    me.clearFire();
                    me.removeEffect(MobEffects.POISON);
                    me.removeEffect(MobEffects.WITHER);
                }
                endCue(other, "win", "§6§l승리!", "§f" + myName + " 님이 쓰러졌습니다");
                endCue(me, "lose", "§c§l패배", "§f" + d.partnerName() + " 님에게 졌습니다" + (LETHAL ? "" : " §7· 체력 1"));
                String msg = "§6⚔ 결투 끝: §f" + d.partnerName() + " §6승리 §7(" + myName + " 님이 쓰러짐)";
                say(me, msg);
                say(other, msg);
            }
            case "fell" -> {
                endCue(other, "end", "§7결투 끝", "§f" + myName + " 님이 쓰러졌습니다");
                say(other, "§7⚔ 결투 끝: " + myName + " 님이 쓰러졌습니다.");
                say(me, "§7⚔ 결투 끝: 쓰러졌습니다.");
            }
            case "command" -> {
                endCue(me, "end", "§7결투 끝", "§f" + d.partnerName() + " 님과의 결투를 끝냈습니다");
                endCue(other, "end", "§7결투 끝", "§f" + myName + " 님이 결투를 끝냈습니다");
                say(me, "§7⚔ " + d.partnerName() + " 님과의 결투를 끝냈습니다.");
                say(other, "§7⚔ " + myName + " 님이 결투를 끝냈습니다.");
            }
            case "left" -> {
                endCue(other, "end", "§7결투 끝", "§f" + myName + " 님이 나갔습니다");
                say(other, "§7⚔ " + myName + " 님이 나가서 결투가 끝났습니다.");
            }
            case "mode" -> {
                String msg = "§7⚔ " + myName + " 님이 생존 · 모험 모드가 아니어서 결투가 끝났습니다.";
                endCue(other, "end", "§7결투 끝", "§f" + myName + " 님이 생존 · 모험 모드가 아님");
                say(me, msg);
                say(other, msg);
            }
            default -> {
            }
        }
        if (!Duels.any()) restoreRule(s);
        return true;
    }

    /** /pvp 끝: 결투를 끝내거나, 보낸 신청을 취소 (그 사람에게는 retryTicks 동안 다시 보내지 못함) */
    static void endByCommand(ServerPlayer player) {
        if (endDuel(player.getUUID(), "command", nameOf(player))) return;
        MinecraftServer s = server(player);
        UUID to = OUTGOING.get(player.getUUID());
        Req req = to != null ? REQUESTS.get(to) : null;
        if (live(req) && req.from.equals(player.getUUID())) {
            dropRequest(req);
            closeIfShown(s, req);
            REFUSED.put(req.from + "|" + req.to, Tasks.now());
            say(player, "§7" + req.toName + " 님에게 보낸 결투 신청을 취소했습니다.");
            say(player(s, req.to), "§7" + nameOf(player) + " 님이 결투 신청을 취소했습니다.");
            return;
        }
        say(player, "§7결투 중이 아닙니다.");
    }

    // ------------------------------------------------------------ /pvp 만 쳤을 때: 결투 창

    static void openMenu(ServerPlayer player) {
        MinecraftServer s = server(player);
        Duels.Duel d = Duels.of(player.getUUID());
        if (d != null) {
            ServerPlayer p = player(s, d.partner());
            long hp = p != null ? Math.round(p.getHealth() / Math.max(1F, p.getMaxHealth()) * 100) : 0;
            new MenuForm("§c⚔ 결투", "§f" + d.partnerName() + " §7님과 결투 중 §f(상대 체력 " + hp + "%)").button("§4결투 끝내기", DialogForm.BYE).button("§7닫기")
                    .show(player, sel -> {
                        Duels.Duel now = Duels.of(player.getUUID());
                        if (sel == 0 && now != null && now.token() == d.token()) endDuel(player.getUUID(), "command", nameOf(player));
                    });
            return;
        }
        Req inc = REQUESTS.get(player.getUUID());
        if (live(inc)) {
            new MenuForm("§c⚔ 결투 신청", "§f" + inc.fromName + " §7님이 결투를 신청했습니다.").button("§2⚔ 결투 수락", DialogForm.OK).button("§4거절", DialogForm.BYE)
                    .button("§7닫기").show(player, sel -> {
                        if (sel == 0 || sel == 1) answer(s, inc, sel == 0);
                    });
            return;
        }
        List<ServerPlayer> others = new ArrayList<>();
        for (ServerPlayer p : online(s)) if (p != player && others.size() < 30) others.add(p);
        int pct = (int) Math.round((1 - MULT) * 100);
        boolean blocked = blocksDuels(player);
        MenuForm menu = new MenuForm("§c⚔ 결투 신청", (others.isEmpty() ? "§7다른 접속자가 없습니다."
                : "§f결투할 사람을 고르세요.\n§7받은 사람에게 수락 · 거절 창이 뜹니다. 수락하면 서로에게만 평타 · 스킬 · 룬 피해 (받는 피해 " + pct + "% 감소). 끝내기: /pvp 끝")
                + (blocked ? "\n§c지금은 결투 신청을 받지 않습니다." : ""));
        List<UUID> ids = new ArrayList<>();
        for (ServerPlayer p : others) {
            String busy = Duels.inDuel(p.getUUID()) ? " §7(결투 중)" : !rpgMode(p) ? " §7(생존 · 모험 아님)" : blocksDuels(p) ? " §7(신청 받지 않음)" : "";
            menu.button("§f" + nameOf(p) + busy);
            ids.add(p.getUUID());
        }
        menu.button(blocked ? "§2결투 신청 받기" : "§7결투 신청 받지 않기");
        menu.button("§7닫기");
        menu.show(player, sel -> {
            if (sel < 0 || player.isRemoved()) return;
            if (sel == ids.size()) {
                setBlock(player, !blocked);
                return;
            }
            if (sel > ids.size()) return;
            ServerPlayer t = player(s, ids.get(sel));
            if (t == null) {
                say(player, "§7그 사람이 나갔습니다.");
                return;
            }
            requestTo(player, t);
        });
    }

    /** /pvp [닉네임 | 끝 | 수락 | 거절 | 차단] */
    public static void pvpCommand(ServerPlayer player, String arg) {
        server = player.level().getServer();
        String a = arg == null ? "" : arg.trim();
        if (a.isEmpty()) {
            openMenu(player);
            return;
        }
        String low = a.toLowerCase(Locale.ROOT);
        if (END_WORDS.contains(low)) endByCommand(player);
        else if (ACCEPT_WORDS.contains(low)) answerTyped(player, true);
        else if (DECLINE_WORDS.contains(low)) answerTyped(player, false);
        else if (BLOCK_WORDS.contains(low)) setBlock(player, !blocksDuels(player));
        else requestDuel(player, a);
    }

    private static final SuggestionProvider<CommandSourceStack> ARGS = (c, b) -> {
        List<String> l = new ArrayList<>(List.of("끝", "수락", "거절", "차단"));
        for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) if (p != c.getSource().getPlayer()) l.add(nameOf(p));
        return SharedSuggestionProvider.suggest(l, b);
    };

    static void register(CommandDispatcher<CommandSourceStack> d) {
        for (String name : List.of("pvp", "결투")) {
            d.register(Commands.literal(name).executes(c -> run(c, ""))
                    .then(Commands.argument("arg", StringArgumentType.greedyString()).suggests(ARGS).executes(c -> run(c, StringArgumentType.getString(c, "arg")))));
        }
    }

    private static int run(CommandContext<CommandSourceStack> c, String arg) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        try {
            pvpCommand(sp, arg);
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[mmorpg] pvp", e);
            say(sp, "§c오류: " + e);
        }
        return 1;
    }

    // ------------------------------------------------------------ 피해 (막기 · 줄이기 · 쓰러지지 않음)

    /**
     * 플레이어끼리의 피해 (화살 등을 쏜 사람 · 길들인 동물의 주인 포함): 결투 상대면 들어감 (줄이기는 RpgHealth 결투 훅), 아니면 막음 (PVP.protectOthers).
     * 자기 피해 · 쓰러지는 중 · 플레이어가 아닌 것은 그대로
     */
    static boolean allow(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer victim)) return true;
        ServerPlayer from = Duels.attackerOf(source);
        if (from == null) return !(PROTECT && Duels.petOfAbsentOwner(source)); // 주인이 접속하지 않은 길들인 동물: 결투일 수 없으므로 막음
        if (from == victim) return true;
        if (victim.isDeadOrDying()) return true;
        if (Duels.isPartner(from, victim)) return true;
        return !PROTECT;
    }

    /** 결투 상대의 피해면 × PVP.mult (그 뒤 잠깐의 불 · 용암 · 떨어짐은 결투 상대 탓) */
    static double scaleLoss(ServerPlayer sp, DamageSource source, double loss) {
        ServerPlayer from = Duels.attackerOf(source);
        if (from == null || from == sp || !Duels.isPartner(from, sp)) return loss;
        Duels.notePartnerHit(sp.getUUID());
        return Duels.damage(sp, loss);
    }

    /** 주인 없는 피해 (때린 엔티티 없음) 이고 결투 상대 탓이 될 수 있는 원인 (불 · 용암 · 떨어짐 · 위더) 인지 */
    static boolean blameable(DamageSource source) {
        if (source.getEntity() != null) return false;
        return source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE) || source.is(DamageTypes.CAMPFIRE) || source.is(DamageTypes.LAVA)
                || source.is(DamageTypes.FALL) || source.is(DamageTypes.WITHER);
    }

    /** 결투에서 진 것으로 셀 피해: 결투 상대 (길들인 동물 포함) 의 피해, 또는 결투 상대가 PVP.blameTicks 안에 때린 뒤의 불 · 용암 · 떨어짐 · 위더 */
    static boolean duelBlow(Duels.Duel d, ServerPlayer victim, DamageSource source) {
        ServerPlayer from = Duels.attackerOf(source);
        if (from != null) return from.getUUID().equals(d.partner());
        return blameable(source) && Tasks.now() - Duels.partnerHitAt(victim.getUUID()) <= BLAME;
    }

    /** RpgHealth 결투 훅 (죽을 피해일 때): true = 죽지 않고 체력 1 (결투에서 짐 — 다음 틱 duelLost 가 끝냄), 진 뒤 잠깐은 남은 불 · 용암 · 떨어짐 · 위더만 */
    static boolean duelFloor(ServerPlayer sp, DamageSource source) {
        if (LETHAL) return false;
        long now = Tasks.now();
        Duels.Duel d = Duels.of(sp.getUUID());
        if (d != null && now >= d.start() && duelBlow(d, sp, source)) {
            LOST_AT.put(sp.getUUID(), now);
            return true;
        }
        Long g = GRACE_UNTIL.get(sp.getUUID());
        return g != null && g > now && blameable(source);
    }

    /** RpgHealth 결투 훅: 결투 상대 (탓) 에게 쓰러져 체력 1 로 남음 = 결투에서 짐 (그 사이 결투가 끝났으면 아무 일 없음) */
    static void duelLost(ServerPlayer sp) {
        if (LOST_AT.remove(sp.getUUID()) == null) return;
        if (Duels.inDuel(sp.getUUID())) endDuel(sp.getUUID(), "defeat", nameOf(sp));
    }

    // ------------------------------------------------------------ 틱 · 이벤트

    private static void tick(MinecraftServer s) {
        server = s;
        long now = Tasks.now();
        // 만료된 신청
        for (Req req : new ArrayList<>(REQUESTS.values())) {
            if (now < req.until) continue;
            dropRequest(req);
            closeIfShown(s, req);
            REFUSED.put(req.from + "|" + req.to, now);
            say(player(s, req.from), "§7" + req.toName + " 님이 " + Math.round(REQUEST / 20F) + "초 동안 답하지 않아 결투 신청이 취소되었습니다.");
            say(player(s, req.to), "§7" + req.fromName + " 님의 결투 신청이 시간이 지나 취소되었습니다.");
        }
        REFUSED.values().removeIf(t -> now - t >= RETRY);
        LAST_ASK.values().removeIf(t -> now - t >= ASK_GAP);
        GRACE_UNTIL.values().removeIf(t -> t <= now);
        LOST_AT.values().removeIf(t -> now - t > 20);
        for (UUID id : Duels.duelists()) {
            Duels.Duel d = Duels.of(id);
            if (d == null) continue;
            ServerPlayer me = player(s, id);
            ServerPlayer other = player(s, d.partner());
            if (me == null && other == null) {
                // 둘 다 사라짐 (나감 알림을 놓친 경우)
                Duels.clear(id);
                if (!Duels.any()) restoreRule(s);
            } else if (other == null) {
                endDuel(d.partner(), "left", d.partnerName());
            } else if (me != null && !rpgMode(me)) {
                endDuel(id, "mode", nameOf(me)); // 크리에이티브 등으로 바꿈: 다치지 않으므로 결투 끝
            }
        }
    }

    /** 결투 중인 두 사람 머리 위 표식 (시작한 뒤) */
    private static void markTick(MinecraftServer s) {
        long now = Tasks.now();
        for (UUID id : Duels.duelists()) {
            Duels.Duel d = Duels.of(id);
            if (d == null || now < d.start()) continue;
            ServerPlayer p = player(s, id);
            if (p == null) continue;
            duelMark(p.level(), overHead(p, 0.75), Opt.of("size", 0.3, "life", MARK_EVERY / 20.0 + 0.05, "alpha", 0.95, "fade", 0.1, "vel", bodyVel(p)));
        }
    }

    private static void onLeave(ServerPlayer sp) {
        MinecraftServer s = server(sp);
        UUID id = sp.getUUID();
        String name = nameOf(sp);
        cancelRequestsOf(s, id, r -> "§7" + (r.from.equals(id) ? r.fromName : r.toName) + " 님이 나가서 결투 신청이 취소되었습니다.");
        OUTGOING.remove(id);
        GRACE_UNTIL.remove(id);
        LAST_ASK.remove(id);
        LOST_AT.remove(id);
        endDuel(id, "left", name);
    }

    // ------------------------------------------------------------ 시험용

    /** 시험용: 받은 신청이 있는지 */
    public static boolean hasRequest(ServerPlayer to) {
        return live(REQUESTS.get(to.getUUID()));
    }

    /** 시험용: 신청 기록 (거절 · 다시 신청 대기) 을 지움 */
    public static void resetRequests() {
        REQUESTS.clear();
        OUTGOING.clear();
        REFUSED.clear();
        LAST_ASK.clear();
    }
}
