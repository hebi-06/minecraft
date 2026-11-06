package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-06 2차 직업 — 사제 2차 (재설계안 2판 — 사용자 "다음 작업") 의 특성 (서버 — jobs.json advance, 세인트의 신앙 쌓임 · 홀 흡혈 넘침은 JobTraits4 의 사제 줄기):
 * <ul>
 * <li>세인트 「신앙」: 사제와 같이 회복 · 보호막 스킬이 +1 · 공격 스킬이 모두 씀 + 넘치는 빛 — 직업 스킬 회복이 체력을 넘치면 넘친 만큼 overFor 틱 보호막 (최대 체력 × overCap
 * 까지 — {@link #saintHeal}). 페탈 베일 (꽃잎 장막 — 보호막이 깨지거나 끝나면 꽃잎이 터져 적을 밀쳐냄), 소울본드 (이어진 아군이 받을 피해의 share 를 세인트가 —
 * RpgHealth.lossShare · 회복 + healUp), 에버블룸 (꽃밭 안의 아군은 쓰러지지 않음 — RpgHealth.undying · 받은 피해를 적어 두었다가 꽃이 필 때 되돌림 — RpgHealth.lossHook)</li>
 * <li>워록 「생명 착취」 (1차와 거꾸로 — 아군에게 체력을 주는 대신 빼앗아 씀): 피해 직업 스킬을 쓸 때 둘레 radius 칸 아군 (나 포함) 의 체력 take 씩 (floor 아래는 빼지
 * 않음 · 다른 아군이 없으면 나 solo) — 빼앗은 몫 (take 하나) 하나당 위력 + bonus (최대 max 몫 — {@link #castMult} · 빼앗는 것은 나간 뒤 {@link #afterCast}). 애시본 동안은
 * 내 체력만 (self — 체력 1 에서 멈춤) · 위력 + power. 블러드 뱅큇 피의 역병 (받는 피해 + vuln — RpgCombat.takenMult), 크림슨 마이어 이동 속도 (Attributes.MOVEMENT_SPEED)</li>
 * </ul>
 */
public final class JobTraits14 {
    private JobTraits14() {}

    static final String SA = "saint";
    static final String WK = "warlock";
    static final String BOND = "saint_bond";
    static final String ASH = "warlock_ash";
    static final double TAU = Math.PI * 2;

    /** 세인트: 금빛 · 새잎 초록 · 꽃잎 (장밋빛 흰) */
    static final double[] GOLD = {1.0, 0.88, 0.5};
    static final double[] GOLD_CORE = {1.0, 0.98, 0.88};
    static final double[] LEAF = {0.45, 1.0, 0.6};
    static final double[] LEAF_CORE = {0.9, 1.0, 0.92};
    static final double[] PETAL = {1.0, 0.62, 0.8};
    static final double[] PETAL_CORE = {1.0, 0.95, 0.97};
    /** 워록: 피 (짙은 진홍 몸 · 밝은 속) · 마녀의 금빛 · 불씨 · 재 */
    static final double[] BLOOD = {0.66, 0.02, 0.12};
    static final double[] BLOOD_CORE = {1.0, 0.34, 0.36};
    static final double[] WGOLD = {1.0, 0.68, 0.22};
    static final double[] WGOLD_CORE = {1.0, 0.95, 0.74};
    static final double[] EMBER = {1.0, 0.3, 0.12};
    static final double[] EMBER_CORE = {1.0, 0.86, 0.46};
    static final double[] ASHEN = {0.2, 0.14, 0.16};
    static final double[] ASHEN_CORE = {0.46, 0.36, 0.36};

    /** 빼앗은 체력 (누구 · 얼마) */
    record Pay(UUID who, double amount) {}

    /** 페탈 베일: 세인트 · 감싸기 시작 틱 · 끝 틱 · 터지는 반지름 · 밀쳐냄 */
    record Veil(UUID saint, long from, long until, double radius, double knock) {}

    /** 에버블룸 꽃밭: 쓴 사람 · 차원 · 가운데 · 반지름 · 끝 틱 · 안에서 받은 피해 (사람마다) */
    static final class Field {
        final UUID owner;
        final ServerLevel level;
        final Vec3 center;
        final double radius;
        final long until;
        final Map<UUID, Double> lost = new HashMap<>();

        Field(UUID owner, ServerLevel level, Vec3 center, double radius, long until) {
            this.owner = owner;
            this.level = level;
            this.center = center;
            this.radius = radius;
            this.until = until;
        }
    }

    static final class State {
        /** 세인트 소울본드: 이어진 아군 (없으면 null — 회복만) */
        UUID bondTo;
        /** 워록 생명 착취: 이번 시전에 빼앗을 것 (castMult 가 정함 → afterCast 가 빼앗음) */
        List<Pay> plan;
        /** 마지막으로 빼앗은 것 · 그 틱 (생귄 랜스가 맞히면 되돌림) */
        List<Pay> paid = new ArrayList<>();
        long paidAt = -9999;
        /** 게이지: 다음 스킬이 빼앗을 몫 */
        int units;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** 페탈 베일: 감싼 사람 → 장막 */
    private static final Map<UUID, Veil> VEILS = new HashMap<>();
    /** 소울본드: 이어진 아군 → 세인트 */
    private static final Map<UUID, UUID> BONDS = new HashMap<>();
    /** 에버블룸 꽃밭 */
    private static final List<Field> FIELDS = new ArrayList<>();
    /** 에버블룸: 쓰러지지 않는 사람 → 끝 틱 */
    private static final Map<UUID, Long> BLOOM = new HashMap<>();
    /** 블러드 뱅큇 피의 역병: 적 → {끝 틱, 받는 피해 +} */
    private static final Map<UUID, double[]> PLAGUE = new HashMap<>();
    /** 크림슨 마이어 이동 속도 */
    private static final Identifier MIRE_SPEED = Mmorpg.id("job_warlock_mire");

    static void init() {
        java.util.function.ToDoubleFunction<LivingEntity> tm = RpgCombat.takenMult;
        RpgCombat.takenMult = e -> tm.applyAsDouble(e) * plagueMult(e);
        java.util.function.Predicate<ServerPlayer> und = RpgHealth.undying;
        java.util.function.Consumer<ServerPlayer> saved = RpgHealth.undyingSaved;
        RpgHealth.undying = p -> und.test(p) || bloomOn(p);
        RpgHealth.undyingSaved = p -> {
            if (und.test(p)) saved.accept(p);
            else bloomSaved(p);
        };
        RpgHealth.LossShare ls = RpgHealth.lossShare;
        RpgHealth.lossShare = (p, src, loss) -> {
            double a = ls.share(p, src, loss);
            return a > 0 ? a : bondShare(p, src, loss);
        };
        RpgHealth.LossHook lh = RpgHealth.lossHook;
        RpgHealth.lossHook = (sp, src, loss, before, after) -> {
            lh.lost(sp, src, loss, before, after);
            recordLoss(sp, before - after); // 실제로 깎인 체력 (쓰러지지 않음이 체력 1 에서 버틴 큰 피해는 깎인 만큼만 — 되돌림이 그 피해 전부가 아니게)
        };
        ServerTickEvents.END_SERVER_TICK.register(JobTraits14::tickGlobal);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATES.clear();
            VEILS.clear();
            BONDS.clear();
            FIELDS.clear();
            BLOOM.clear();
            PLAGUE.clear();
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
        STATES.remove(sp.getUUID());
        BONDS.values().removeIf(s -> s.equals(sp.getUUID()));
        BLOOM.remove(sp.getUUID());
        mireSpeed(sp, 0);
    }

    static void forget(UUID id) {
        STATES.remove(id);
        BONDS.values().removeIf(s -> s.equals(id));
        BONDS.remove(id);
        VEILS.remove(id);
        BLOOM.remove(id);
    }

    static void lifeEnded(UUID id) {
        State st = STATES.get(id);
        if (st != null) {
            st.plan = null;
            st.paid = new ArrayList<>();
            st.bondTo = null;
        }
        BONDS.values().removeIf(s -> s.equals(id));
        BONDS.remove(id);
        VEILS.remove(id);
        BLOOM.remove(id);
    }

    static Vec3 chest(ServerPlayer p) {
        return V.up(p.position(), p.getBbHeight() * 0.62);
    }

    // ------------------------------------------------------------ 세인트 — 넘치는 빛 · 소울본드 회복

    /** 세인트의 직업 스킬 회복 배율 (소울본드 동안 1 + healUp) */
    static double healMult(ServerPlayer saint) {
        if (!JobTraits.stanceOn(saint, BOND)) return 1;
        Jobs.Job j = jobIf(saint, SA);
        Jobs.Skill k = j == null ? null : j.skill(BOND);
        return 1 + (k == null ? 0.4 : k.num("healUp", 0.4));
    }

    /**
     * 세인트의 직업 스킬 회복 (소울본드 × healMult): 오른 만큼 숫자 · 넘친 만큼 overFor 틱 보호막 (최대 체력 × overCap 까지 — 넘치는 빛 · 몸을 감싸는 꽃잎 빛). 실제로 오른 양
     */
    static double saintHeal(ServerPlayer saint, ServerPlayer to, double amount) {
        if (to == null || !(amount > 0)) return 0;
        double a = amount * healMult(saint);
        double got = JobTraits4.heal(to, a);
        Jobs.Job j = jobIf(saint, SA);
        double over = a - got;
        if (j != null && over > 0.05 && RpgHealth.isRpgMode(to) && to.isAlive()) {
            double max = RpgHealth.maxOf(to);
            double added = RpgHealth.addShield(to, over * trait(j, "overShield", 1.0), (int) trait(j, "overFor", 80), max * trait(j, "overCap", 0.2));
            if (added > 0.05 && RuneKit.fxQuota("saintOver" + to.getId(), 1)) {
                ServerLevel level = to.level();
                JobFx14.lotus(level, V.up(to.position(), 0.08), 0.9, 2, 4, 0.5, false, Opt.of("alpha", 0.85, "vel", RuneKit.bodyVel(to)), PETAL, PETAL_CORE);
                JobFx.aegis(level, V.up(to.position(), 1.1), 0.42, j, 0, 0.4, (to == saint ? JobFx.hide1p(saint) : Opt.empty()).with("vel", RuneKit.bodyVel(to)));
            }
        }
        return got;
    }

    /** 시험: 이 세인트가 소울본드로 이은 아군 (태세가 아니면 null) */
    public static UUID bondOf(ServerPlayer saint) {
        State st = STATES.get(saint.getUUID());
        return st == null || !JobTraits.stanceOn(saint, BOND) ? null : st.bondTo;
    }

    /** 소울본드를 이음 (아군이 없으면 나만 — 회복 +) */
    static void bondStart(ServerPlayer saint, ServerPlayer ally) {
        BONDS.values().removeIf(s -> s.equals(saint.getUUID()));
        if (ally != null && ally != saint) BONDS.put(ally.getUUID(), saint.getUUID());
        of(saint).bondTo = ally == null || ally == saint ? null : ally.getUUID();
    }

    /**
     * RpgHealth.lossShare: 소울본드로 이어진 아군이 받을 피해 loss 의 share 를 세인트가 (체력 1 아래로는 깎이지 않음 — 그만큼은 아군에게 남음). 다른 플레이어 (결투) 의 피해 ·
     * 즉사 기믹 (최대 체력 5배 넘게) 은 나누지 않음. 아군에게서 덜어 낸 양
     */
    private static double bondShare(ServerPlayer p, DamageSource src, double loss) {
        if (BONDS.isEmpty()) return 0;
        UUID sid = BONDS.get(p.getUUID());
        if (sid == null) return 0;
        ServerPlayer s = RuneKit.player(p.level(), sid);
        if (s == null || !JobTraits.stanceOn(s, BOND)) {
            BONDS.remove(p.getUUID());
            return 0;
        }
        if (s == p || !s.isAlive() || s.isDeadOrDying() || !RpgHealth.isRpgMode(s) || s.distanceTo(p) > 24) return 0;
        if (src.getEntity() instanceof Player || loss >= RpgHealth.maxOf(p) * 5) return 0;
        Jobs.Job j = jobIf(s, SA);
        Jobs.Skill k = j == null ? null : j.skill(BOND);
        if (k == null) return 0;
        double moved = loss * k.num("share", 0.2);
        double taken = RpgHealth.takeShared(s, moved);
        if (!(taken > 0)) return 0;
        ServerLevel level = p.level();
        if (RuneKit.fxQuota("saintBond" + p.getId(), 1)) {
            // 리본을 따라 금빛이 세인트에게로 · 아군 몸의 빛의 방패
            JobFx.soulFly(level, chest(p), chest(s), 14, 0.3, GOLD, GOLD_CORE, Opt.empty());
            JobFx.aegis(level, V.up(p.position(), 1.1), 0.4, j, 2, 0.3, Opt.of("alpha", 0.8, "vel", RuneKit.bodyVel(p)));
            SkillFx.play(level, "block.amethyst_block.hit", s.position(), 1.4, 0.6);
        }
        DamageNumbers.show(s, taken, DamageNumbers.Style.PLAYER, "", 0);
        return Math.min(moved, taken);
    }

    // ------------------------------------------------------------ 세인트 — 페탈 베일

    /** 페탈 베일: to 를 from 틱 뒤부터 ticks 동안 감쌈 (보호막은 PetalVeil 이 넣음 — 깨지거나 끝나면 터짐) */
    static void veilOn(ServerPlayer saint, ServerPlayer to, int from, int ticks, double radius, double knock) {
        long now = Tasks.now();
        VEILS.put(to.getUUID(), new Veil(saint.getUUID(), now + from, now + from + ticks, radius, knock));
    }

    /** 시험: 페탈 베일이 감싸고 있는지 */
    public static boolean veiled(ServerPlayer p) {
        Veil v = VEILS.get(p.getUUID());
        return v != null && v.until() > Tasks.now();
    }

    /** 장막이 터짐: 꽃잎이 사방으로 · 꽃이 흩날림 · 둘레 radius 칸 적 밀쳐냄 */
    private static void veilBurst(ServerPlayer t, Veil v) {
        ServerLevel level = t.level();
        Vec3 c = chest(t);
        for (int i = 0; i < 14; i++) {
            double a = i * TAU / 14 + Kit.rand() * 0.3;
            Vec3 d = V.unit(new Vec3(Math.cos(a), 0.25 + Kit.rand() * 0.5, Math.sin(a)));
            petal(level, V.add(c, d, 0.4), d, 4.5 + Kit.rand() * 2.5, 0.16, 0.6, Opt.empty());
        }
        JobFx14.bloom(level, c, 0.8, 5, 3, 0.4, false, Opt.of("alpha", 0.9, "vel", RuneKit.bodyVel(t)), PETAL, PETAL_CORE);
        JobFx14.lotus(level, V.up(t.position(), 0.06), v.radius(), 5, 3, 0.45, false, Opt.of("alpha", 0.7), PETAL, PETAL_CORE);
        SkillFx.play(level, "block.cherry_leaves.break", t.position(), 1.2, 1.0);
        SkillFx.play(level, "block.amethyst_block.chime", t.position(), 1.6, 0.6);
        ServerPlayer saint = RuneKit.player(level, v.saint());
        if (saint == null) return;
        for (LivingEntity e : Kit.around(saint, t.position(), v.radius(), 3)) {
            if (!e.isAlive() || e.isRemoved() || !JobKit.pushable(e)) continue;
            RuneKit.knock(e, V.sub(e.position(), t.position()), v.knock(), 0.25);
        }
    }

    /** 꽃잎 하나 (rfx_jpetal2 4 ~ 7 — 팔랑임 · 세인트 꽃잎빛) dir 쪽으로 speed 칸/초 */
    static void petal(ServerLevel level, Vec3 at, Vec3 dir, double speed, double size, double life, Opt o) {
        JobFx.cells(level, "jpetal2", JobFx.PETAL, 4, 4, at, life, 10, true, o.with("dir", dir, "speed", speed, "size", size, "spin", Kit.rand() * 360, "spinRate",
                (Kit.rand() - 0.5) * 500), PETAL, PETAL_CORE);
    }

    // ------------------------------------------------------------ 세인트 — 에버블룸

    /** 꽃밭을 펼침 (center · radius · ticks) — 안에서 받은 피해를 적어 둠 */
    static Field fieldStart(ServerPlayer owner, Vec3 center, double radius, int ticks) {
        Field f = new Field(owner.getUUID(), owner.level(), center, radius, Tasks.now() + ticks);
        FIELDS.add(f);
        return f;
    }

    static void fieldEnd(Field f) {
        FIELDS.remove(f);
    }

    /** 시험: 이 꽃밭에 적힌 받은 피해 */
    public static double lostIn(Object field, ServerPlayer p) {
        return field instanceof Field f ? f.lost.getOrDefault(p.getUUID(), 0.0) : 0;
    }

    /** RpgHealth.lossHook: 꽃밭 안의 아군이 실제로 깎인 체력을 적음 (한 번에 최대 체력까지) */
    private static void recordLoss(ServerPlayer p, double loss) {
        if (FIELDS.isEmpty() || p == null || !(loss > 0)) return;
        long now = Tasks.now();
        for (Field f : FIELDS) {
            if (f.until < now || f.level != p.level() || V.hdist(p.position(), f.center) > f.radius) continue;
            ServerPlayer owner = RuneKit.player(p.level(), f.owner);
            if (owner != null && !JobTraits4.ally(owner, p)) continue;
            f.lost.merge(p.getUUID(), Math.min(loss, RpgHealth.maxOf(p)), Double::sum);
        }
    }

    /** ticks 동안 쓰러지지 않음 (에버블룸) */
    static void bloomFor(ServerPlayer p, int ticks) {
        BLOOM.merge(p.getUUID(), Tasks.now() + Math.max(0, ticks), Math::max);
    }

    /** 에버블룸으로 쓰러지지 않는 중인지 */
    public static boolean bloomOn(ServerPlayer p) {
        Long t = BLOOM.get(p.getUUID());
        return t != null && t > Tasks.now();
    }

    /** 에버블룸이 쓰러짐을 막음 (다음 틱): 꽃잎빛 번쩍 · 글자 */
    private static void bloomSaved(ServerPlayer p) {
        if (!RuneKit.fxQuota("saintBloomSaved" + p.getId(), 1)) return;
        ServerLevel level = p.level();
        JobFx14.bloom(level, chest(p), 0.7, 4, 3, 0.5, false, JobFx.hide1p(p).with("vel", RuneKit.bodyVel(p)), PETAL, PETAL_CORE);
        JobFx14.bloom(level, V.add(p.getEyePosition(), p.getViewVector(1F), 2.4), 0.4, 4, 3, 0.5, false, JobFx.only1p(p), PETAL, PETAL_CORE);
        SkillFx.play(level, "block.amethyst_block.chime", p.position(), 1.8, 0.8);
        Notify.notify(p, "§d§l에버블룸 §r§7— 쓰러지지 않았습니다", 30);
    }

    // ------------------------------------------------------------ 워록 — 생명 착취

    /** 빼앗을 수 있는 사람 (RPG 체력 · 살아 있음 · 체력이 floor 이상) */
    private static boolean drainable(ServerPlayer p, double floor) {
        return RpgHealth.isRpgMode(p) && p.isAlive() && !p.isDeadOrDying() && p.getHealth() >= p.getMaxHealth() * floor;
    }

    /**
     * 이번 시전에 빼앗을 것: 애시본 동안은 나만 (self — 체력 1 까지), 아니면 둘레 radius 칸의 다른 아군 (floor 이상) 마다 take · 나도 take (floor 이상이면) — 다른 아군이 없으면
     * 나 solo
     */
    static List<Pay> plan(ServerPlayer sp, Jobs.Job j) {
        List<Pay> out = new ArrayList<>();
        if (JobTraits.stanceOn(sp, ASH)) {
            Jobs.Skill a = j.skill(ASH);
            double amount = Math.min(RpgHealth.maxOf(sp) * (a == null ? 0.08 : a.num("self", 0.08)), sp.getHealth() - 1);
            if (RpgHealth.isRpgMode(sp) && amount > 0.05) out.add(new Pay(sp.getUUID(), amount));
            return out;
        }
        double floor = trait(j, "floor", 0.25);
        double take = trait(j, "take", 0.04);
        List<ServerPlayer> others = new ArrayList<>();
        for (ServerPlayer o : JobTraits4.allies(sp, sp.position(), trait(j, "radius", 10))) if (o != sp && drainable(o, floor)) others.add(o);
        boolean me = drainable(sp, floor);
        if (others.isEmpty()) {
            if (me) out.add(new Pay(sp.getUUID(), RpgHealth.maxOf(sp) * trait(j, "solo", 0.08)));
            return out;
        }
        if (me) out.add(new Pay(sp.getUUID(), RpgHealth.maxOf(sp) * take));
        others.sort(java.util.Comparator.comparingDouble(o -> o.distanceToSqr(sp)));
        int room = Math.max(1, j.trait().max()) - (me ? 1 : 0);
        for (int i = 0; i < Math.min(room, others.size()); i++) out.add(new Pay(others.get(i).getUUID(), RpgHealth.maxOf(others.get(i)) * take));
        return out;
    }

    /** 빼앗은 몫 (take 하나 = 그 사람 최대 체력의 take — 나 혼자 8% 는 둘) — 최대 max */
    static int unitsOf(ServerPlayer sp, Jobs.Job j, List<Pay> pays) {
        double take = trait(j, "take", 0.04);
        double u = 0;
        for (Pay p : pays) {
            ServerPlayer o = RuneKit.player(sp.level(), p.who());
            double max = o == null ? RpgHealth.maxOf(sp) : RpgHealth.maxOf(o);
            u += p.amount() / Math.max(1e-6, max * take);
        }
        return Math.max(0, Math.min(Math.max(1, j.trait().max()), (int) Math.round(u)));
    }

    /** 이번 시전의 위력 배율 (JobSystem): 워록의 피해 직업 스킬 — 1 + 빼앗을 몫 × bonus (+ 애시본 power). 빼앗는 것은 나간 뒤 (afterCast) */
    static double castMult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k) {
        if (j == null || !WK.equals(j.id())) return 1;
        State st = of(sp);
        st.plan = null;
        if (k == null || k.total() <= 0) return 1;
        List<Pay> plan = plan(sp, j);
        st.plan = plan;
        double m = 1 + unitsOf(sp, j, plan) * trait(j, "bonus", 0.1);
        if (JobTraits.stanceOn(sp, ASH)) {
            Jobs.Skill a = j.skill(ASH);
            m += a == null ? 0.4 : a.num("power", 0.4);
        }
        return m;
    }

    /** 나간 뒤 (JobSystem): 워록 — 정해 둔 만큼 빼앗음 (핏줄이 쓴 사람의 홀로). 알림 줄에 덧붙일 말 (없으면 null) */
    static String afterCast(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, boolean ok) {
        if (j == null || !WK.equals(j.id())) return null;
        State st = STATES.get(sp.getUUID());
        if (st == null || st.plan == null) return null;
        List<Pay> plan = st.plan;
        st.plan = null;
        if (!ok || plan.isEmpty()) return null;
        List<Pay> done = new ArrayList<>();
        boolean ash = JobTraits.stanceOn(sp, ASH);
        for (Pay p : plan) {
            ServerPlayer o = RuneKit.player(sp.level(), p.who());
            if (o == null || !o.isAlive()) continue;
            double amount = o == sp && ash ? Math.min(p.amount(), o.getHealth() - 1) : p.amount();
            double got = RpgHealth.payHp(o, amount);
            if (!(got > 0)) continue;
            done.add(new Pay(o.getUUID(), got));
            drainFx(sp, o, got);
        }
        st.paid = done;
        st.paidAt = Tasks.now();
        if (done.isEmpty()) return null;
        int u = unitsOf(sp, j, done);
        SkillFx.play(sp.level(), "entity.warden.heartbeat", sp.position(), 1.2, 0.8);
        String who = done.size() == 1 && done.get(0).who().equals(sp.getUUID()) ? "나" : done.size() + "명";
        return "§4생명 착취 " + who + " · 위력 +" + Math.round(u * trait(j, "bonus", 0.1) * 100) + "%" + (ash ? " §6· 애시본 +" + Math.round(
                (j.skill(ASH) == null ? 0.4 : j.skill(ASH).num("power", 0.4)) * 100) + "%" : "");
    }

    /** 빼앗음: 그 사람 몸에서 핏방울이 쓴 사람의 홀로 날아감 · 핏빛 숫자 · 튀는 피 */
    private static void drainFx(ServerPlayer sp, ServerPlayer o, double got) {
        ServerLevel level = sp.level();
        Vec3 from = chest(o);
        RuneKit.Hand h = RuneKit.handOf(sp);
        Vec3 to = V.up(V.add(h.hand(), h.f(), 0.45), 0.4);
        for (int i = 0; i < 4; i++) {
            Vec3 s = V.add(from, new Vec3((Kit.rand() - 0.5) * 0.7, (Kit.rand() - 0.5) * 0.7, (Kit.rand() - 0.5) * 0.7));
            JobFx.soulFly(level, s, o == sp ? V.up(to, 0.35) : to, 10 + i * 1.5, 0.22, BLOOD, BLOOD_CORE, Opt.empty());
        }
        JobFx.burst(level, from, 0.55, BLOOD, BLOOD_CORE, 0.25, (o == sp ? JobFx.hide1p(sp) : Opt.empty()).with("vel", RuneKit.bodyVel(o)));
        SkillFx.sparks(level, from, BLOOD_CORE, 6, 2.5);
        DamageNumbers.show(o, got, DamageNumbers.Style.PLAYER, "", 0);
    }

    /** 생귄 랜스가 맞음: 마지막으로 빼앗은 체력 × frac 를 빼앗긴 사람마다 되돌림 (핏방울이 날아가 닿으면 — 2초 안의 것만). 되돌린 사람 수 */
    static int giveBack(ServerPlayer sp, Vec3 from, double frac) {
        State st = STATES.get(sp.getUUID());
        if (st == null || st.paid.isEmpty() || Tasks.now() - st.paidAt > 40 || !(frac > 0)) return 0;
        ServerLevel level = sp.level();
        int n = 0;
        for (Pay p : st.paid) {
            ServerPlayer o = RuneKit.player(level, p.who());
            if (o == null || !o.isAlive()) continue;
            double amount = p.amount() * frac;
            int ticks = JobFx.soulFly(level, from, chest(o), 16, 0.3, BLOOD, BLOOD_CORE, Opt.empty());
            JobFx.soulFly(level, V.up(from, 0.3), chest(o), 13, 0.2, WGOLD, WGOLD_CORE, Opt.empty());
            RuneKit.later(Math.max(1, ticks), () -> {
                if (o.isRemoved() || !o.isAlive()) return;
                JobTraits4.heal(o, amount);
                JobFx.burst(o.level(), chest(o), 0.45, WGOLD, WGOLD_CORE, 0.25, (o == sp ? JobFx.hide1p(sp) : Opt.empty()).with("vel", RuneKit.bodyVel(o)));
            });
            n++;
        }
        st.paid = new ArrayList<>();
        return n;
    }

    /** 시험: 마지막으로 빼앗은 체력 (사람마다 합) */
    public static double lastPaid(ServerPlayer sp, ServerPlayer who) {
        State st = STATES.get(sp.getUUID());
        if (st == null) return 0;
        double sum = 0;
        for (Pay p : st.paid) if (p.who().equals(who.getUUID())) sum += p.amount();
        return sum;
    }

    /** 시험 · 게이지: 다음 스킬이 빼앗을 몫 */
    public static int units(ServerPlayer sp) {
        Jobs.Job j = jobIf(sp, WK);
        return j == null ? 0 : unitsOf(sp, j, plan(sp, j));
    }

    /**
     * 블러드 뱅큇 잔치: sp 둘레 r 칸 다른 아군 (floor 이상) 의 체력 frac 씩 빼앗음 — 핏줄이 to 로. 빼앗은 사람 수
     */
    static int feast(ServerPlayer sp, Jobs.Job j, Vec3 to, double r, double frac) {
        double floor = trait(j, "floor", 0.25);
        ServerLevel level = sp.level();
        int n = 0;
        for (ServerPlayer o : JobTraits4.allies(sp, sp.position(), r)) {
            if (o == sp || !drainable(o, floor)) continue;
            double got = RpgHealth.payHp(o, RpgHealth.maxOf(o) * frac);
            if (!(got > 0)) continue;
            n++;
            Vec3 from = chest(o);
            for (int i = 0; i < 5; i++) {
                Vec3 s = V.add(from, new Vec3((Kit.rand() - 0.5) * 0.8, (Kit.rand() - 0.5) * 0.8, (Kit.rand() - 0.5) * 0.8));
                JobFx.soulFly(level, s, to, 9 + i * 1.5, 0.26, BLOOD, BLOOD_CORE, Opt.empty());
            }
            JobFx.beam(level, from, to, BLOOD, BLOOD_CORE, 2.0, 0.35, Opt.of("alpha", 0.8));
            DamageNumbers.show(o, got, DamageNumbers.Style.PLAYER, "", 0);
        }
        return n;
    }

    // ------------------------------------------------------------ 워록 — 피의 역병 · 크림슨 마이어

    /** 피의 역병: ticks 동안 받는 피해 + vuln */
    static void plague(LivingEntity e, int ticks, double vuln) {
        PLAGUE.put(e.getUUID(), new double[] {Tasks.now() + ticks, vuln});
    }

    /** 시험: 피의 역병이 걸려 있는지 */
    public static boolean plagued(LivingEntity e) {
        double[] p = e == null ? null : PLAGUE.get(e.getUUID());
        return p != null && p[0] > Tasks.now();
    }

    /** RpgCombat.takenMult: 피의 역병 1 + vuln */
    private static double plagueMult(LivingEntity e) {
        if (PLAGUE.isEmpty() || e == null) return 1;
        double[] p = PLAGUE.get(e.getUUID());
        return p == null || p[0] <= Tasks.now() ? 1 : 1 + p[1];
    }

    /** 크림슨 마이어 이동 속도 + amount (0 = 뗌) */
    static void mireSpeed(ServerPlayer sp, double amount) {
        AttributeInstance a = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null) return;
        if (amount > 0) a.addOrUpdateTransientModifier(new AttributeModifier(MIRE_SPEED, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        else a.removeModifier(MIRE_SPEED);
    }

    // ------------------------------------------------------------ 게이지 · 태세 · 틱

    /** 게이지: 워록 = 다음 스킬이 빼앗을 몫 (4틱마다 셈) */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.units;
    }

    /** 태세 연출 (JobTraits.stanceFx — 8틱마다): 소울본드 리본 · 애시본 불길 (JobTraits14.tick · tickGlobal 이 그림) */
    static boolean stanceFx(ServerPlayer sp, Jobs.Job j, String stance) {
        return BOND.equals(stance) || ASH.equals(stance);
    }

    /** 태세가 끝남: 소울본드 → 리본을 끊음 */
    static void stanceEnded(ServerPlayer sp, String id) {
        if (!BOND.equals(id)) return;
        BONDS.values().removeIf(s -> s.equals(sp.getUUID()));
        State st = STATES.get(sp.getUUID());
        if (st != null) st.bondTo = null;
    }

    /** 4틱마다 (JobTraits.tick): 워록 — 게이지 (빼앗을 몫) · 애시본 불길 (8틱마다) */
    static void tick(ServerPlayer sp, Jobs.Job j, long now) {
        if (!WK.equals(j.id())) return;
        State st = of(sp);
        int u = unitsOf(sp, j, plan(sp, j));
        if (u != st.units) {
            st.units = u;
            JobTraits.sendGauge(sp, false);
        }
        if (now % 8 != 0 || !JobTraits.stanceOn(sp, ASH)) return;
        ServerLevel level = sp.level();
        Opt o = JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp));
        JobFx14.pyre(level, sp.position(), 1.7, 3, 3, 0.5, true, o.with("alpha", 0.6, "coreAlpha", 0.55), EMBER, EMBER_CORE);
        for (int i = 0; i < 3; i++) {
            double a = now * 0.3 + i * TAU / 3;
            Vec3 p = V.up(V.add(sp.position(), new Vec3(Math.cos(a), 0, Math.sin(a)), 0.8), 0.5 + i * 0.5);
            SkillFx.fx(level, "rpg:arrow_spark", p, Opt.of("dir", V.UP, "speed", 0.9, "size", 0.13, "grow", 0.4, "life", 0.45, "color", i % 2 == 0 ? WGOLD : BLOOD_CORE,
                    "alpha", 0.95));
        }
    }

    /** 서버 틱마다: 페탈 베일 (2틱마다 — 깨지거나 끝나면 터짐 · 도는 꽃잎) · 소울본드 리본 (4틱마다) · 끝난 것 지움 */
    private static void tickGlobal(MinecraftServer server) {
        long now = Tasks.now();
        if (!VEILS.isEmpty() && now % 2 == 0) {
            for (Iterator<Map.Entry<UUID, Veil>> it = VEILS.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Veil> en = it.next();
                Veil v = en.getValue();
                ServerPlayer t = server.getPlayerList().getPlayer(en.getKey());
                if (t == null || !t.isAlive()) {
                    it.remove();
                    continue;
                }
                if (now < v.from()) continue;
                boolean broken = now >= v.from() + 2 && RpgHealth.shieldOf(t) <= 0.01;
                if (broken || now >= v.until()) {
                    it.remove();
                    veilBurst(t, v);
                    continue;
                }
                if (now % 4 == 0) drawVeil(t, now);
            }
        }
        if (!BONDS.isEmpty() && now % 4 == 0) drawBonds(server, now);
        if (now % 20 == 0) {
            if (!BLOOM.isEmpty()) BLOOM.values().removeIf(t -> t <= now);
            if (!PLAGUE.isEmpty()) PLAGUE.values().removeIf(p -> p[0] <= now);
            FIELDS.removeIf(f -> f.until + 40 < now);
        }
    }

    /** 감싼 꽃잎: 몸 둘레를 도는 꽃잎 여섯 (높이를 달리) · 발밑 작은 연꽃 (8틱마다) */
    private static void drawVeil(ServerPlayer t, long now) {
        ServerLevel level = t.level();
        Vec3 base = t.position();
        Vec3 vel = RuneKit.bodyVel(t);
        for (int i = 0; i < 6; i++) {
            double a = now * 0.32 + i * TAU / 6;
            double h = 0.35 + (i % 3) * 0.55 + Math.sin(now * 0.2 + i) * 0.12;
            Vec3 p = V.up(V.add(base, new Vec3(Math.cos(a), 0, Math.sin(a)), 0.82), h);
            Vec3 tan = V.unit(new Vec3(-Math.sin(a), 0.12, Math.cos(a)));
            petal(level, p, tan, 5.0, 0.15, 0.24, Opt.of("vel", vel));
        }
        if (now % 8 == 0) JobFx14.lotus(level, V.up(base, 0.07), 1.15, 3, 3, 0.45, true, Opt.of("alpha", 0.7, "vel", vel), PETAL, PETAL_CORE);
    }

    /** 소울본드 리본: 세인트 가슴 → 아군 가슴의 금빛 리본 · 그 위를 흐르는 꽃잎 · 둘의 머리 위 작은 연꽃 (16틱마다) */
    private static void drawBonds(MinecraftServer server, long now) {
        for (Iterator<Map.Entry<UUID, UUID>> it = BONDS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, UUID> en = it.next();
            ServerPlayer ally = server.getPlayerList().getPlayer(en.getKey());
            ServerPlayer s = server.getPlayerList().getPlayer(en.getValue());
            if (ally == null || s == null || !JobTraits.stanceOn(s, BOND)) {
                it.remove();
                continue;
            }
            if (ally.level() != s.level() || s.distanceTo(ally) > 24) continue;
            ServerLevel level = s.level();
            Vec3 a = chest(s);
            Vec3 b = chest(ally);
            JobFx.beam(level, a, b, GOLD, GOLD_CORE, 2.0, 0.26, Opt.of("alpha", 0.6, "fade", 0.3));
            if (now % 8 == 0) {
                double t = Kit.rand();
                Vec3 p = V.lerp(a, b, t);
                Vec3 d = V.sub(b, a);
                double len = V.len(d);
                if (len > 0.5) petal(level, p, V.scale(d, 1 / len), 2.5, 0.14, 0.5, Opt.empty());
            }
            if (now % 16 == 0) {
                for (ServerPlayer o : new ServerPlayer[] {s, ally}) {
                    JobFx14.lotus(level, V.up(o.position(), o.getBbHeight() + 0.45), 0.42, 3, 3, 0.85, true, (o == s ? JobFx.hide1p(s) : Opt.empty()).with("alpha", 0.85,
                            "vel", RuneKit.bodyVel(o)), GOLD, GOLD_CORE);
                }
            }
        }
    }
}
