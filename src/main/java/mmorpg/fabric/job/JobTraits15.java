package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Aegis;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-06 2차 직업 — 수호기사 2차 (재설계안 2판 — 사용자 "다음 작업") 의 특성 (서버 — jobs.json advance, 센티널의 반격 게이지는 JobTraits4 의 수호기사 줄기):
 * <ul>
 * <li>센티널 「반격」: 수호기사와 같이 막은 피해 → 반격 게이지 + 파수꾼의 위압 — 센티널이 도발한 적이 주는 피해 − dread (RpgHealth.weakenOf · 머리 위 무지개 고리).
 * 헤이븐 리프의 피난처 (안의 아군을 노린 막을 수 있는 평타 · 몹 투사체를 받지 않음 — RpgHealth.sourceReduce), 레인보우 램파트 (적 투사체를 지움 · 닿은 적 둔화 · 도발 —
 * 서버 틱), 나인 타이즈 (받거나 막은 피해의 rate 가 둘레 아군의 보호막으로 — RpgHealth.takenHook · blockHook)</li>
 * <li>어벤저 「앙갚음」 (1차와 거꾸로 — 막고 대신 맞는 대신 다 맞고 한 번에 되갚음): 웅크려 막기 없음 (RpgCombat.blockOff) · 받는 피해 + taken (RpgHealth.takenUp) — 받은
 * 피해의 rate 가 앙갚음으로 (최대 체력 × cap 까지 · keep 틱 동안 맞지 않으면 1초에 최대 체력 × decay 씩 사라짐) → 앙갚음을 쓰는 스킬이 고정 피해로 더함
 * ({@link #vengeanceTake}). 그러지 차지의 대상 = 나를 가장 많이 때린 적 ({@link #topAttacker}), 페인 이터 (둘레 아군이 받을 피해의 share 를 내가 × (1 − cut) 로 —
 * RpgHealth.lossShare · 대신 받은 피해도 앙갚음 · 내 받는 피해 − cut), 게이지 = 앙갚음 (최대 대비)</li>
 * </ul>
 */
public final class JobTraits15 {
    private JobTraits15() {}

    static final String SN = "sentinel";
    static final String AV = "avenger";
    static final String TIDES = "sentinel_tides";
    static final String PAIN = "avenger_pain";

    /** 센티널: 강철 하늘빛 · 진주빛 · 뿔피리 황금 · 바다 물결 */
    static final double[] SKY = {0.44, 0.66, 0.95};
    static final double[] SKY_CORE = {0.92, 0.96, 1.0};
    static final double[] GOLD = {1.0, 0.78, 0.3};
    static final double[] GOLD_CORE = {1.0, 0.97, 0.84};
    static final double[] SEA = {0.22, 0.74, 0.92};
    static final double[] SEA_CORE = {0.88, 1.0, 1.0};
    /** 무지개 일곱 빛 (빨강 → 보라 — 램파트 장막 · 무지개 다리) */
    static final double[][] BOW = {{1.0, 0.26, 0.3}, {1.0, 0.56, 0.18}, {1.0, 0.9, 0.3}, {0.36, 0.94, 0.42}, {0.28, 0.78, 1.0}, {0.36, 0.42, 1.0}, {0.74, 0.38, 1.0}};
    /** 어벤저: 불씨 · 뜨거운 속 · 재 · 흰 불 */
    static final double[] EMBER = {1.0, 0.42, 0.1};
    static final double[] EMBER_CORE = {1.0, 0.86, 0.56};
    static final double[] ASH = {0.22, 0.18, 0.2};
    static final double[] ASH_CORE = {0.56, 0.46, 0.42};
    static final double[] WHITE_HOT = {1.0, 0.95, 0.8};

    /** 빛의 무지개 한 빛 (i — 일곱 빛을 돎) 의 심지 (흰빛 쪽으로) */
    static double[] bowCore(int i) {
        double[] c = BOW[Math.floorMod(i, BOW.length)];
        return new double[] {c[0] + (1 - c[0]) * 0.65, c[1] + (1 - c[1]) * 0.65, c[2] + (1 - c[2]) * 0.65};
    }

    static double[] bow(int i) {
        return BOW[Math.floorMod(i, BOW.length)];
    }

    /** 파수꾼의 위압: 도발된 적 → 끝 틱 · 줄이는 몫 */
    public record Dread(LivingEntity e, long until, double cut) {}

    /** 헤이븐 리프 피난처: 쓴 사람 · 차원 · 가운데 (땅) · 반지름 · 끝 틱 */
    public record Haven(UUID owner, ServerLevel level, Vec3 c, double r, long until) {}

    /** 레인보우 램파트: 쓴 사람 · 차원 · 가운데 (땅) · 장벽이 뻗는 쪽 · 앞 (장벽의 면) · 반 너비 · 높이 · 끝 틱 · 둔화 세기 · 도발 틱 · 닿은 적 (다시 걸 틱) */
    static final class Rampart {
        final UUID owner;
        final ServerLevel level;
        final Vec3 c;
        final Vec3 along;
        final Vec3 normal;
        final double half;
        final double height;
        final long until;
        final int slow;
        final int taunt;
        final Map<UUID, Long> touched = new HashMap<>();
        int blocked;

        Rampart(UUID owner, ServerLevel level, Vec3 c, Vec3 along, Vec3 normal, double half, double height, long until, int slow, int taunt) {
            this.owner = owner;
            this.level = level;
            this.c = c;
            this.along = along;
            this.normal = normal;
            this.half = half;
            this.height = height;
            this.until = until;
            this.slow = slow;
            this.taunt = taunt;
        }
    }

    static final class State {
        /** 어벤저 앙갚음 (체력 양) · 마지막으로 맞은 틱 · 마지막으로 줄어든 틱 */
        double veng;
        long lastHit = -9999;
        long lastDecay = -9999;
        /** 나를 때린 적 → {준 피해, 마지막 틱} */
        final Map<UUID, double[]> foes = new HashMap<>();
        /** 나인 타이즈 물결 연출 틱 (아군마다 — 너무 잦지 않게) */
        final Map<UUID, Long> tideFx = new HashMap<>();
        /** 시험: 나인 타이즈가 준 보호막 합 · 페인 이터가 대신 받은 합 */
        double tideGiven;
        double painTaken;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<UUID, Dread> DREAD = new HashMap<>();
    private static final List<Haven> HAVENS = new ArrayList<>();
    private static final List<Rampart> RAMPARTS = new ArrayList<>();
    /** 페인 이터 태세인 어벤저 */
    private static final Set<UUID> PAINERS = new HashSet<>();

    static void init() {
        // 파수꾼의 위압: 센티널이 도발한 적이 주는 피해 − dread (다른 약화와 곱)
        java.util.function.ToDoubleFunction<Entity> wk = RpgHealth.weakenOf;
        RpgHealth.weakenOf = e -> 1 - (1 - wk.applyAsDouble(e)) * (1 - dreadOf(e));
        // 헤이븐 리프 피난처 (막을 수 있는 평타 · 몹 투사체 — 받지 않음) · 페인 이터 (내 받는 피해 − cut)
        java.util.function.ToDoubleBiFunction<ServerPlayer, DamageSource> sr = RpgHealth.sourceReduce;
        RpgHealth.sourceReduce = (p, src) -> Math.max(sr.applyAsDouble(p, src), Math.max(havenReduce(p, src), painCut(p)));
        // 어벤저: 웅크려 막기 없음 · 받는 피해 +
        java.util.function.Predicate<ServerPlayer> bo = RpgCombat.blockOff;
        RpgCombat.blockOff = p -> bo.test(p) || jobIf(p, AV) != null;
        java.util.function.ToDoubleFunction<ServerPlayer> tu = RpgHealth.takenUp;
        RpgHealth.takenUp = p -> tu.applyAsDouble(p) + takenUpOf(p);
        // 받은 피해 (보호막 앞): 어벤저 앙갚음 · 센티널 나인 타이즈
        RpgHealth.TakenHook th = RpgHealth.takenHook;
        RpgHealth.takenHook = (p, src, a) -> {
            th.taken(p, src, a);
            onTaken(p, src, a);
        };
        // 막음: 센티널 나인 타이즈 (막아 준 양도 물결로)
        RpgHealth.BlockHook bh = RpgHealth.blockHook;
        RpgHealth.blockHook = (p, src, a) -> {
            bh.blocked(p, src, a);
            tides(p, a);
        };
        // 페인 이터: 둘레 아군이 받을 피해의 몫을 어벤저가
        RpgHealth.LossShare ls = RpgHealth.lossShare;
        RpgHealth.lossShare = (p, src, loss) -> {
            double a = ls.share(p, src, loss);
            return a > 0 ? a : painShare(p, src, loss);
        };
        ServerTickEvents.END_SERVER_TICK.register(JobTraits15::tickGlobal);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATES.clear();
            DREAD.clear();
            HAVENS.clear();
            RAMPARTS.clear();
            PAINERS.clear();
        });
    }

    static Jobs.Job jobIf(ServerPlayer sp, String id) {
        Jobs.Job j = JobSystem.job(sp);
        return j != null && id.equals(j.id()) ? j : null;
    }

    static double trait(Jobs.Job j, String key, double fallback) {
        return j == null || j.trait() == null ? fallback : j.trait().def().numOr(key, fallback);
    }

    private static State of(ServerPlayer sp) {
        return STATES.computeIfAbsent(sp.getUUID(), k -> new State());
    }

    static void reset(ServerPlayer sp) {
        UUID id = sp.getUUID();
        STATES.remove(id);
        PAINERS.remove(id);
        HAVENS.removeIf(h -> h.owner().equals(id));
        RAMPARTS.removeIf(r -> r.owner.equals(id));
    }

    static void forget(UUID id) {
        STATES.remove(id);
        PAINERS.remove(id);
        HAVENS.removeIf(h -> h.owner().equals(id));
        RAMPARTS.removeIf(r -> r.owner.equals(id));
    }

    /** 죽음 · 차원 이동: 앙갚음 · 때린 적 기억 · 피난처 · 장벽 · 페인 이터가 끝남 */
    static void lifeEnded(UUID id) {
        State st = STATES.get(id);
        if (st != null) {
            st.veng = 0;
            st.foes.clear();
        }
        PAINERS.remove(id);
        HAVENS.removeIf(h -> h.owner().equals(id));
        RAMPARTS.removeIf(r -> r.owner.equals(id));
    }

    /** 영상 · 시험: 남은 앙갚음 · 피난처 · 장벽 · 위압을 지움 (스킬마다 따로 보이게) */
    public static void testClear(ServerPlayer p) {
        UUID id = p.getUUID();
        STATES.remove(id);
        PAINERS.remove(id);
        HAVENS.removeIf(h -> h.owner().equals(id));
        RAMPARTS.removeIf(r -> r.owner.equals(id));
        DREAD.clear();
    }

    static Vec3 chest(Entity p) {
        return V.up(p.position(), p.getBbHeight() * 0.62);
    }

    // ------------------------------------------------------------ 센티널 — 파수꾼의 위압

    /**
     * 센티널의 도발 (스킬이 부름): ticks 동안 그 적이 나를 노림 (Aegis.tauntFor — 머리 위 "도발") + 센티널이면 그동안 그 적이 주는 피해 − dread (머리 위 무지개 고리 —
     * {@link #tickGlobal} 가 16틱마다 그림)
     */
    static void taunt(ServerPlayer sp, LivingEntity e, int ticks) {
        if (e == null || e.isRemoved() || !e.isAlive() || ticks <= 0) return;
        Aegis.tauntFor(sp, e, ticks, true);
        Jobs.Job j = jobIf(sp, SN);
        if (j == null) return;
        Dread old = DREAD.get(e.getUUID());
        long until = Tasks.now() + ticks;
        DREAD.put(e.getUUID(), new Dread(e, old != null ? Math.max(old.until(), until) : until, trait(j, "dread", 0.15)));
        if (old == null && e.level() instanceof ServerLevel lv) dreadFx(lv, e, 0.5);
    }

    /** 위압 받는 적인지 (시험) */
    public static boolean dreaded(LivingEntity e) {
        Dread d = DREAD.get(e.getUUID());
        return d != null && d.until() > Tasks.now();
    }

    private static double dreadOf(Entity e) {
        if (DREAD.isEmpty() || e == null) return 0;
        Dread d = DREAD.get(e.getUUID());
        return d != null && d.until() > Tasks.now() ? d.cut() : 0;
    }

    /** 머리 위 무지개 고리 (일곱 빛 — 위압) */
    private static void dreadFx(ServerLevel lv, LivingEntity e, double life) {
        Vec3 top = RuneKit.overHead(e, 0.35);
        double r = 0.34 + Kit.bodyPad(e) * 0.25;
        int i = (int) ((Tasks.now() / 16) % BOW.length);
        Opt o = Opt.of("vel", RuneKit.entVel(e), "alpha", 0.9, "fade", 0.3);
        JobFx15.halo(lv, top, r, life, o, bow(i), bowCore(i));
        RuneKit.rfx(lv, "prop", top, Opt.of("size", 0.12, "life", life, "cell", (double) RuneKit.RFX.get("glint")[0], "spin", 30.0, "spinRate", 120.0, "color",
                bowCore(i + 3), "fade", 0.5, "vel", RuneKit.entVel(e)));
    }

    // ------------------------------------------------------------ 센티널 — 헤이븐 리프 피난처

    /** 피난처 시작 (헤이븐 리프 — 내려앉은 자리) */
    static void havenStart(ServerPlayer sp, Vec3 c, double r, int ticks) {
        HAVENS.removeIf(h -> h.owner().equals(sp.getUUID()));
        HAVENS.add(new Haven(sp.getUUID(), sp.level(), c, r, Tasks.now() + ticks));
    }

    /** 그 사람이 선 피난처 (없으면 null — 시험) */
    public static Haven havenOf(ServerPlayer p) {
        if (HAVENS.isEmpty()) return null;
        long now = Tasks.now();
        for (Haven h : HAVENS) {
            if (h.until() <= now || h.level() != p.level()) continue;
            if (V.hdist(p.position(), h.c()) > h.r() + 0.3 || p.getY() < h.c().y - 1.5 || p.getY() > h.c().y + 3.5) continue;
            ServerPlayer o = RuneKit.player(h.level(), h.owner());
            if (o == null || !(o == p || JobTraits4.ally(o, p))) continue;
            return h;
        }
        return null;
    }

    /**
     * RpgHealth.sourceReduce: 피난처 안이면 막을 수 있는 평타 (몹 근접 — 보스의 방향 없는 공격 markNoBlock 은 아님) · 몹 투사체를 받지 않음 (1). 다른 플레이어의
     * 피해 · 폭발 · 마법 장판은 그대로
     */
    private static double havenReduce(ServerPlayer p, DamageSource src) {
        if (HAVENS.isEmpty()) return 0;
        Entity from = src.getEntity();
        if (from == null || from instanceof Player || from == p) return 0;
        boolean melee = src.is(DamageTypes.MOB_ATTACK) || src.is(DamageTypes.MOB_ATTACK_NO_AGGRO);
        boolean shot = src.is(DamageTypes.MOB_PROJECTILE) || src.is(DamageTypes.ARROW) || src.is(DamageTypes.TRIDENT) || src.getDirectEntity() instanceof Projectile;
        if (!(melee || shot) || src.is(DamageTypes.EXPLOSION) || RpgCombat.noBlockNow(p)) return 0;
        Haven h = havenOf(p);
        if (h == null) return 0;
        if (RuneKit.fxQuota("jobHaven" + p.getId(), 1)) {
            Entity d = src.getDirectEntity() != null ? src.getDirectEntity() : from;
            havenBlockFx(h, chest(p), chest(d), Kit.facingOf(p));
        }
        return 1;
    }

    /**
     * 피난처가 막음: 공격이 돔 면에 닿는 자리 (때린 것이 돔 밖 — 막은 사람 가슴에서 그쪽으로 그은 줄이 돔 면을 지나는 곳 · 돔 안이면 막은 사람 앞 1.1칸) 에 무지개
     * 프리즘 · 금빛 터짐 · 돔 면을 따라 번지는 물결 고리 → 돔을 이 그림들 뒤에 다시 그림 (SentinelSkills.havenDome — 영상 3: 하늘빛 프리즘이 하늘빛 돔에 묻혔고, 돔 가운데
     * 뒤쪽이면 먼저 그린 돔에 가려 보이지 않았음)
     */
    private static void havenBlockFx(Haven h, Vec3 b, Vec3 a, Vec3 facing) {
        Vec3 ab = V.sub(a, b);
        double len = V.len(ab);
        Vec3 u = len > 1e-3 ? V.scale(ab, 1 / len) : facing;
        Vec3 q = V.sub(b, h.c());
        double qu = V.dot(q, u);
        double disc = qu * qu - (V.dot(q, q) - h.r() * h.r());
        double s = disc > 0 ? -qu + Math.sqrt(disc) : -1;
        Vec3 at;
        Vec3 n;
        if (s > 0.3 && s < len) {
            at = V.add(b, u, s);
            n = V.unit(V.sub(at, h.c()));
        } else {
            at = V.add(b, u, Math.max(0.5, Math.min(1.1, len * 0.5)));
            n = u;
        }
        int i = (int) (Kit.rand() * 7);
        ServerLevel lv = h.level();
        SkillFx.ring(lv, V.add(at, n, 0.06), n, GOLD_CORE, 1.5, 0.45, 0);
        SkillFx.ring(lv, V.add(at, n, 0.03), n, bowCore(i), 0.9, 0.35, 0);
        JobFx15.prism(lv, V.add(at, n, 0.15), 0.62, 5, 1, 0.32, false, Opt.of("alpha", 0.95), bow(i), bowCore(i));
        JobFx.burst(lv, at, 0.95, GOLD, GOLD_CORE, 0.26, Opt.empty());
        SkillFx.sparks(lv, at, bowCore((i + 3) % 7), 10, 3);
        SkillFx.play(lv, "item.shield.block", at, 1.4, 0.7);
        SkillFx.play(lv, "block.amethyst_block.hit", at, 1.6, 0.6);
        long left = h.until() - 4 - Tasks.now();
        ServerPlayer o = RuneKit.player(lv, h.owner());
        if (o != null && left > 2) SentinelSkills.havenDome(o, lv, h.c(), h.r(), left / 20.0);
    }

    // ------------------------------------------------------------ 센티널 — 레인보우 램파트

    /** 장벽 시작 (레인보우 램파트 — 솟은 틱): 가운데 c (땅) · 뻗는 쪽 along · 면 normal (쓴 사람이 본 쪽) */
    static void rampartStart(ServerPlayer sp, Vec3 c, Vec3 along, Vec3 normal, double half, double height, int ticks, int slow, int taunt) {
        RAMPARTS.removeIf(r -> r.owner.equals(sp.getUUID()));
        RAMPARTS.add(new Rampart(sp.getUUID(), sp.level(), c, along, normal, half, height, Tasks.now() + ticks, slow, taunt));
    }

    static void rampartEnd(ServerPlayer sp) {
        RAMPARTS.removeIf(r -> r.owner.equals(sp.getUUID()));
    }

    /** 시험: 이 사람의 장벽이 지운 투사체 수 (장벽이 없으면 −1) */
    public static int rampartBlocked(ServerPlayer sp) {
        for (Rampart r : RAMPARTS) if (r.owner.equals(sp.getUUID())) return r.blocked;
        return -1;
    }

    /** 장벽 띠 안인지 (면에서 depth 칸 · 너비 반 + 0.5칸 · 높이 −1 ~ height + 1) */
    static boolean inBand(Vec3 p, Vec3 c, Vec3 along, Vec3 normal, double half, double depth, double height, double pad) {
        Vec3 d = V.sub(p, c);
        return Math.abs(V.dot(d, normal)) <= depth + pad && Math.abs(V.dot(d, along)) <= half + 0.5 + pad && d.y >= -1.5 && d.y <= height + 1;
    }

    private static void tickRamparts(long now) {
        for (Iterator<Rampart> it = RAMPARTS.iterator(); it.hasNext(); ) {
            Rampart r = it.next();
            if (r.until <= now) {
                it.remove();
                continue;
            }
            ServerPlayer owner = RuneKit.player(r.level, r.owner);
            if (owner == null) {
                it.remove();
                continue;
            }
            // 투사체: 장벽 면을 지나는 (이번 틱 · 다음 틱 길) 적의 투사체를 지움
            Vec3 a0 = V.add(r.c, r.along, -r.half - 1);
            Vec3 a1 = V.add(r.c, r.along, r.half + 1);
            AABB box = new AABB(Math.min(a0.x, a1.x), r.c.y - 1, Math.min(a0.z, a1.z), Math.max(a0.x, a1.x), r.c.y + r.height + 1.5, Math.max(a0.z, a1.z)).inflate(4, 0, 4);
            for (Projectile pr : r.level.getEntitiesOfClass(Projectile.class, box, x -> !x.isRemoved())) {
                Entity shooter = pr.getOwner();
                if (shooter instanceof Player) continue;
                Vec3 p = pr.position();
                Vec3 v = pr.getDeltaMovement();
                double d0 = V.dot(V.sub(p, r.c), r.normal);
                double d1 = V.dot(V.sub(V.add(p, v), r.c), r.normal);
                boolean cross = Math.abs(d0) <= 0.7 || (d0 > 0) != (d1 > 0);
                if (!cross) continue;
                double s = Math.abs(d1 - d0) > 1e-6 ? Math.max(0, Math.min(1, -d0 / (d1 - d0))) : 0;
                Vec3 hit = Math.abs(d0) <= 0.7 ? p : V.add(p, V.scale(v, s));
                Vec3 off = V.sub(hit, r.c);
                if (Math.abs(V.dot(off, r.along)) > r.half + 0.3 || off.y < -0.8 || off.y > r.height + 0.6) continue;
                pr.discard();
                r.blocked++;
                if (RuneKit.fxQuota("jobRampart" + r.owner, 3)) {
                    // 지워짐: 그 자리 장막 빛의 프리즘 · 빛 터짐 · 불티 (영상 1: 프리즘 0.4 는 장막 앞에서 거의 보이지 않았음)
                    int i = (int) Math.floor((V.dot(off, r.along) + r.half) / (2 * r.half) * BOW.length);
                    JobFx15.prism(r.level, hit, 0.75, 5, 1, 0.3, false, Opt.of("alpha", 0.95), bow(i), bowCore(i));
                    JobFx.burst(r.level, hit, 1.0, bow(i), bowCore(i), 0.28, Opt.empty());
                    // 장벽 면에 번지는 물결 고리 (영상 2: 프리즘 · 터짐만으로는 장막 빛에 묻혔음)
                    SkillFx.ring(r.level, hit, r.normal, bowCore(i), 1.5, 0.4, 0);
                    SkillFx.ring(r.level, hit, r.normal, bow(i), 0.8, 0.3, 0);
                    SkillFx.sparks(r.level, hit, bowCore(i), 10, 3.5);
                    SkillFx.play(r.level, "block.amethyst_block.resonate", hit, 1.5, 0.7);
                    SkillFx.play(r.level, "item.shield.block", hit, 1.3, 0.6);
                }
            }
            // 닿은 적: 둔화 · 도발 (적마다 1초에 한 번)
            if (now % 4 != 0) continue;
            for (LivingEntity e : Kit.around(owner, r.c, r.half + 1.5, 4)) {
                if (!e.isAlive() || e.isRemoved() || !inBand(e.position(), r.c, r.along, r.normal, r.half, 0.9, r.height, Kit.bodyPad(e))) continue;
                Long next = r.touched.get(e.getUUID());
                if (next != null && next > now) continue;
                r.touched.put(e.getUUID(), now + 20);
                if (!JobKit.isBoss(e)) RuneKit.effect(e, "slowness", 30, r.slow);
                taunt(owner, e, r.taunt);
                if (e.level() instanceof ServerLevel el) {
                    int i = (int) Math.floor((V.dot(V.sub(e.position(), r.c), r.along) + r.half) / (2 * r.half) * BOW.length);
                    JobFx.burst(el, Kit.bodyPoint(e), 0.6 + Kit.bodyPad(e) * 0.3, bow(i), bowCore(i), 0.25, Opt.of("vel", RuneKit.entVel(e)));
                    SkillFx.play(el, "block.amethyst_block.chime", e.position(), 0.8, 0.6);
                }
            }
        }
    }

    // ------------------------------------------------------------ 센티널 — 나인 타이즈 · 어벤저 — 앙갚음 (받은 피해)

    private static void onTaken(ServerPlayer p, DamageSource src, double a) {
        if (!(a > 0)) return;
        tides(p, a);
        Jobs.Job j = jobIf(p, AV);
        if (j == null) return;
        gain(p, j, a);
        Entity from = src.getEntity();
        if (from instanceof LivingEntity && !(from instanceof Player) && from != p) {
            State st = of(p);
            long now = Tasks.now();
            double[] rec = st.foes.computeIfAbsent(from.getUUID(), u -> new double[] {0, now});
            if (now - rec[1] > 200) rec[0] = 0;
            rec[0] += a;
            rec[1] = now;
        }
    }

    /**
     * 나인 타이즈 (센티널 태세): 내가 받거나 막은 피해 amount × rate 가 둘레 radius 칸 아군 (나 포함) 의 shieldFor 틱 보호막으로 (한 사람에 최대 체력 × cap 까지) —
     * 나에게서 아군에게 물결이 흘러감
     */
    private static void tides(ServerPlayer sp, double amount) {
        if (!(amount > 0) || !JobTraits.stanceOn(sp, TIDES)) return;
        Jobs.Job j = jobIf(sp, SN);
        Jobs.Skill k = j == null ? null : j.skill(TIDES);
        if (k == null) return;
        double give = amount * k.num("rate", 0.3);
        // 다음 틱에 줌: 지금 주면 이 피해를 (보호막 앞에서 부르므로) 방금 생긴 내 보호막이 바로 흡수함
        RuneKit.later(1, () -> {
            if (!sp.isRemoved() && sp.isAlive()) tidesGive(sp, k, give);
        });
    }

    private static void tidesGive(ServerPlayer sp, Jobs.Skill k, double give) {
        int ticks = (int) k.num("shieldFor", 60);
        double capK = k.num("cap", 0.2);
        State st = of(sp);
        long now = Tasks.now();
        ServerLevel level = sp.level();
        for (ServerPlayer o : JobTraits4.allies(sp, sp.position(), k.num("radius", 8))) {
            double got = RpgHealth.addShield(o, give, ticks, RpgHealth.maxOf(o) * capK);
            if (!(got > 0.01)) continue;
            st.tideGiven += got;
            Long last = st.tideFx.get(o.getUUID());
            if (last != null && now - last < 6) continue;
            st.tideFx.put(o.getUUID(), now);
            Vec3 from = chest(sp);
            Vec3 to = chest(o);
            if (o == sp) {
                JobFx15.tide(level, V.add(from, Kit.facingOf(sp), 0.2), 0.55, 6, 2, 0.3, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), SEA, SEA_CORE);
            } else {
                // 나에게서 아군에게 흘러가는 물결 (영상 1: 0.2초에 3.6칸 — 너무 빨라 보이지 않았음 → 0.3 ~ 0.6초 · 크게 · 바닷빛 물줄기)
                Vec3 d = V.sub(to, from);
                double len = V.len(d);
                double go = Math.max(0.3, Math.min(0.6, len / 8));
                if (len > 0.3) {
                    JobFx15.tide(level, from, 0.7, 0, 6, go, true, Opt.of("dir", V.scale(d, 1 / len), "speed", len / go, "alpha", 0.95), SEA, SEA_CORE);
                    JobFx.line(level, from, to, SEA, SEA_CORE, go + 0.15, Opt.of("alpha", 0.6, "fade", 0.4));
                }
                RuneKit.later(Math.max(1, (int) Math.round(go * 20)), () -> {
                    if (!o.isRemoved()) JobFx15.tide(o.level(), chest(o), 0.6, 6, 2, 0.35, false, Opt.of("vel", RuneKit.bodyVel(o)), SEA, SEA_CORE);
                });
            }
            if (RuneKit.fxQuota("jobTides" + sp.getId(), 1)) SkillFx.play(level, "entity.player.splash.high_speed", sp.position(), 1.4, 0.35);
        }
    }

    /** 시험: 나인 타이즈가 준 보호막 합 */
    public static double tideGiven(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.tideGiven;
    }

    /** 앙갚음 상한 (최대 체력 × cap) */
    public static double vengeanceCap(ServerPlayer sp, Jobs.Job j) {
        return RpgHealth.maxOf(sp) * trait(j, "cap", 1.5);
    }

    /** 지금 앙갚음 (체력 양 — 시험) */
    public static double vengeance(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.veng;
    }

    /** 시험용: 앙갚음을 정함 */
    public static void setVengeance(ServerPlayer sp, double v) {
        Jobs.Job j = JobSystem.job(sp);
        State st = of(sp);
        st.veng = Math.max(0, Math.min(vengeanceCap(sp, j), v));
        st.lastHit = Tasks.now();
        JobTraits.sendGauge(sp, false);
    }

    /** 받은 피해 taken × rate 가 앙갚음으로 (상한까지) — 몸의 금에서 불씨 · 가득이면 불꽃 */
    static void gain(ServerPlayer sp, Jobs.Job j, double taken) {
        if (j == null || !(taken > 0)) return;
        State st = of(sp);
        double cap = vengeanceCap(sp, j);
        double before = st.veng;
        st.veng = Math.min(cap, st.veng + taken * trait(j, "rate", 0.5));
        st.lastHit = Tasks.now();
        if (st.veng > before + 1e-9 && RuneKit.fxQuota("jobVeng" + sp.getId(), 1)) {
            ServerLevel level = sp.level();
            Vec3 b = chest(sp);
            SkillFx.sparks(level, b, EMBER, 5 + (int) Math.round(st.veng / Math.max(1, cap) * 8), 2.5);
            if (before < cap && st.veng >= cap - 1e-6) {
                JobFx.eyeRing(sp, j, 2.4, 0.9, 0.4);
                JobFx15.cinder(level, ThunderlordSkills.ground(level, sp.position()), 2.6, 0, 3, 0.5, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), EMBER,
                        EMBER_CORE);
                SkillFx.play(level, "entity.blaze.shoot", sp.position(), 0.6, 0.6);
                SkillFx.play(level, "entity.polar_bear.warning", sp.position(), 0.6, 0.8);
            }
        }
        JobTraits.sendGauge(sp, false);
    }

    /** 앙갚음의 frac 몫을 씀 (스킬이 나갈 때 — 리프라이절 30% · 레크닝 · 조브레이커 전부): 쓴 양 (고정 피해로 더함) */
    static double vengeanceTake(ServerPlayer sp, double frac) {
        State st = STATES.get(sp.getUUID());
        if (st == null || !(st.veng > 0) || !(frac > 0)) return 0;
        double v = st.veng * Math.min(1, frac);
        st.veng -= v;
        if (st.veng < 1e-6) st.veng = 0;
        JobTraits.sendGauge(sp, false);
        return v;
    }

    /** 나를 memory 틱 안에 가장 많이 때린 적 (range 칸 안 · 맞을 수 있는 — 없으면 null) */
    static LivingEntity topAttacker(ServerPlayer sp, double range, int memory) {
        State st = STATES.get(sp.getUUID());
        if (st == null || st.foes.isEmpty()) return null;
        long now = Tasks.now();
        LivingEntity best = null;
        double most = 0;
        for (LivingEntity e : Kit.around(sp, sp.position(), range, 5)) {
            double[] rec = st.foes.get(e.getUUID());
            if (rec == null || now - rec[1] > memory || !e.isAlive() || e.isRemoved()) continue;
            if (rec[0] > most) {
                most = rec[0];
                best = e;
            }
        }
        return best;
    }

    /** 시험: 이 적이 나에게 준 피해 (기억 — 없으면 0) */
    public static double foeDamage(ServerPlayer sp, LivingEntity e) {
        State st = STATES.get(sp.getUUID());
        double[] rec = st == null ? null : st.foes.get(e.getUUID());
        return rec == null ? 0 : rec[0];
    }

    private static double takenUpOf(ServerPlayer p) {
        Jobs.Job j = jobIf(p, AV);
        return j == null ? 0 : trait(j, "taken", 0.1);
    }

    // ------------------------------------------------------------ 어벤저 — 페인 이터

    static void painStart(ServerPlayer sp) {
        PAINERS.add(sp.getUUID());
    }

    /** 시험: 페인 이터가 대신 받은 합 */
    public static double painTaken(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.painTaken;
    }

    /** RpgHealth.sourceReduce: 페인 이터 동안 내 받는 피해 − cut */
    private static double painCut(ServerPlayer p) {
        if (PAINERS.isEmpty() || !PAINERS.contains(p.getUUID()) || !JobTraits.stanceOn(p, PAIN)) return 0;
        Jobs.Job j = jobIf(p, AV);
        Jobs.Skill k = j == null ? null : j.skill(PAIN);
        return k == null ? 0 : k.num("cut", 0.2);
    }

    /**
     * RpgHealth.lossShare: 페인 이터 태세인 어벤저 (radius 칸 안) 가 이 아군이 받을 피해 loss 의 share 를 × (1 − cut) 로 대신 받음 (체력 1 아래로는 깎이지 않음 — 그만큼은
     * 아군에게 남음) · 대신 받은 피해도 앙갚음으로. 다른 플레이어 (결투) 의 피해 · 즉사 기믹 (최대 체력 5배 넘게) 은 나누지 않음
     */
    private static double painShare(ServerPlayer p, DamageSource src, double loss) {
        if (PAINERS.isEmpty() || src.getEntity() instanceof Player || loss >= RpgHealth.maxOf(p) * 5) return 0;
        for (Iterator<UUID> it = PAINERS.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            if (id.equals(p.getUUID())) continue;
            ServerPlayer a = RuneKit.player(p.level(), id);
            if (a == null || !JobTraits.stanceOn(a, PAIN)) {
                if (a == null || !a.isAlive()) continue;
                it.remove();
                continue;
            }
            Jobs.Job j = jobIf(a, AV);
            Jobs.Skill k = j == null ? null : j.skill(PAIN);
            if (k == null || !a.isAlive() || a.isDeadOrDying() || !RpgHealth.isRpgMode(a) || a.distanceTo(p) > k.num("radius", 8) || !JobTraits4.ally(a, p)) continue;
            double moved = loss * k.num("share", 0.5);
            double keep = 1 - Math.max(0, Math.min(0.95, k.num("cut", 0.2)));
            double taken = RpgHealth.takeShared(a, moved * keep);
            if (!(taken > 0)) continue;
            of(a).painTaken += taken;
            gain(a, j, taken);
            if (RuneKit.fxQuota("jobPain" + p.getId(), 1)) {
                ServerLevel level = p.level();
                JobFx.beam(level, chest(p), chest(a), EMBER, EMBER_CORE, 1.2, 0.3, Opt.of("alpha", 0.95));
                SkillFx.sparks(level, chest(p), EMBER, 5, 2.5);
                SkillFx.play(level, "entity.blaze.hurt", a.position(), 0.6, 0.4);
            }
            DamageNumbers.show(a, taken, DamageNumbers.Style.PLAYER, "", 0);
            return Math.min(moved, taken / keep);
        }
        return 0;
    }

    // ------------------------------------------------------------ 틱 · 태세 연출 · 게이지

    /** 사람마다 (JobTraits 가 4틱마다): 앙갚음이 사라짐 · 높은 앙갚음의 불씨 · 오래된 기억을 지움 */
    static void tick(ServerPlayer sp, Jobs.Job j, long now) {
        if (!j.id().equals(AV)) return;
        State st = STATES.get(sp.getUUID());
        if (st == null) return;
        int keep = (int) trait(j, "keep", 160);
        if (st.veng > 0 && now - st.lastHit >= keep && now - st.lastDecay >= 20) {
            st.veng = Math.max(0, st.veng - RpgHealth.maxOf(sp) * trait(j, "decay", 0.1));
            st.lastDecay = now;
            JobTraits.sendGauge(sp, false);
        }
        double cap = vengeanceCap(sp, j);
        if (st.veng >= cap * 0.35 && now % 8 == 0 && !sp.isSpectator()) {
            // 앙갚음이 차오른 몸: 금 사이로 새는 불씨 (3인칭 — 1인칭은 손 앞 불티)
            ServerLevel level = sp.level();
            double q = Math.min(1, st.veng / Math.max(1, cap));
            SkillFx.fx(level, "rpg:arrow_spark", V.up(sp.position(), 0.5 + Kit.rand() * 1.2), Opt.of("dir", V.UP, "speed", 1.2 + q, "size", 0.12 + q * 0.06, "grow", 0.8,
                    "life", 0.45, "color", EMBER, "alpha", 0.95));
            if (q >= 0.999) JobFx15.cinder(level, ThunderlordSkills.ground(level, sp.position()), 2.3, 2, 4, 0.5, true, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp),
                    "alpha", 0.6, "solo", 90.0), EMBER, EMBER_CORE);
        }
        if (now % 80 == 0) st.foes.values().removeIf(r -> now - r[1] > 400);
    }

    /** 서버 틱마다: 장벽 (투사체 · 닿은 적), 위압 고리 (16틱마다), 끝난 피난처를 지움 */
    private static void tickGlobal(MinecraftServer server) {
        long now = Tasks.now();
        if (!RAMPARTS.isEmpty()) tickRamparts(now);
        if (now % 16 == 0) {
            HAVENS.removeIf(h -> h.until() <= now);
            if (!DREAD.isEmpty()) {
                for (Iterator<Dread> it = DREAD.values().iterator(); it.hasNext(); ) {
                    Dread d = it.next();
                    LivingEntity e = d.e();
                    if (d.until() <= now || e.isRemoved() || !e.isAlive()) {
                        it.remove();
                        continue;
                    }
                    if (e.level() instanceof ServerLevel lv && RuneKit.fxQuota("jobDread", 12)) dreadFx(lv, e, 0.85);
                }
            }
        }
    }

    /** 태세 연출 (JobTraits.stanceFx — 8틱마다): 나인 타이즈 (둘레를 도는 아홉 물결) · 페인 이터 (불씨 기둥 · 아군에게서 이어지는 불씨 줄) */
    static boolean stanceFx(ServerPlayer sp, Jobs.Job j, String stance) {
        ServerLevel level = sp.level();
        long now = Tasks.now();
        if (stance.equals(TIDES)) {
            // 아홉 물결: 허리 높이 반지름 1.5칸을 돎 (8틱에 24° — 다음 자리로 흘러감 · 주인마다 하나씩 바꿔 그림)
            Vec3 c = V.up(sp.position(), 0.75);
            double w = Math.toRadians(3.0); // 틱당
            double base = now * w;
            for (int i = 0; i < 9; i++) {
                double a = base + i * Math.PI * 2 / 9;
                double r = 1.5;
                Vec3 p = V.add(c, new Vec3(Math.cos(a) * r, Math.sin(now * 0.12 + i) * 0.12, Math.sin(a) * r));
                Vec3 tan = new Vec3(-Math.sin(a) * r * w * 20, 0, Math.cos(a) * r * w * 20);
                JobFx15.tide(level, p, 0.36, 0, 6, 0.5, true, JobFx.hide1p(sp).with("vel", V.add(RuneKit.bodyVel(sp), tan), "alpha", 0.85, "solo", 80.0 + i, "fade",
                        0.1), SEA, SEA_CORE);
            }
            if (now % 40 == 0) JobFx15.halo(level, V.up(sp.position(), 0.06), 1.8, 0.8, Opt.of("alpha", 0.5, "vel", RuneKit.bodyVel(sp)), SEA, SEA_CORE);
            return true;
        }
        if (stance.equals(PAIN)) {
            JobFx15.cinder(level, ThunderlordSkills.ground(level, sp.position()), 2.5, 2, 4, 0.5, true, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.75,
                    "solo", 91.0), EMBER, EMBER_CORE);
            Jobs.Skill k = j.skill(PAIN);
            double r = k == null ? 8 : k.num("radius", 8);
            for (ServerPlayer o : JobTraits4.allies(sp, sp.position(), r)) {
                if (o == sp) continue;
                JobFx.beam(level, chest(o), chest(sp), EMBER, EMBER_CORE, 1.4, 0.45, Opt.of("alpha", 0.55, "fade", 0.3));
            }
            return true;
        }
        return false;
    }

    /** 태세가 끝남 (JobTraits): 페인 이터 → 불씨가 흩어짐, 나인 타이즈 → 물결이 부서짐 */
    static void stanceEnded(ServerPlayer sp, String id) {
        if (id.equals(PAIN)) {
            PAINERS.remove(sp.getUUID());
            SkillFx.sparks(sp.level(), chest(sp), EMBER, 12, 4);
            SkillFx.play(sp.level(), "block.fire.extinguish", sp.position(), 0.8, 0.5);
        } else if (id.equals(TIDES)) {
            JobFx15.tide(sp.level(), V.up(sp.position(), 0.8), 0.9, 6, 2, 0.4, false, JobFx.hide1p(sp), SEA, SEA_CORE);
            SkillFx.play(sp.level(), "entity.player.splash", sp.position(), 1.2, 0.6);
        }
    }

    /** 게이지 값: 어벤저 앙갚음 (0 ~ 100 — 상한 대비) */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        State st = STATES.get(sp.getUUID());
        if (st == null) return 0;
        double cap = vengeanceCap(sp, j);
        return cap <= 0 ? 0 : (int) Math.round(Math.min(1, st.veng / cap) * 100);
    }
}
