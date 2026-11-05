package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Combat;
import mmorpg.core.Jobs;
import mmorpg.core.SwingLooks;
import mmorpg.core.Weapon;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.motion.SwingLook;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Guards;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 2차 직업 — 전사 각성 워로드 · 반전 글래디에이터 특성 · 태세 (재설계안 2판 — claude/job-advance-redo.md, 서버 — jobs.json advance):
 * <ul>
 * <li>워로드 「분노」 (쌓고 줄어드는 것은 전사와 같음 — JobTraitsMore): 다 차면 곰의 화신 bearFor 틱 — 몸을 감싼 곰 (rfx_jbear · 3인칭 · 다른 사람), 도끼 평타가
 * 곰 발톱 세 줄로 넓어짐 (SWING_LOOK warlord_bear — 범위 ×1.5 · 휘두를 때마다 앞에 큰 발톱 세 줄, 피해 + fullBonus — JobTraitsMore.basicMult), 받는 피해
 * −bearGuard (Guards). 그동안 분노가 남은 시간만큼 줄어 끝나면 0 (쌓이지 않음). 동면 (WarlordSkills.slumber) 동안 받은 피해 (최대 체력 1%) 마다 분노 + rage.
 * 4번 칸은 베어 스톰프 (피해 스킬 — 2026-11-05 거인의 무게 태세 대신, WarlordSkills.stomp)</li>
 * <li>글래디에이터 「결투」: 도끼 평타 · 직업 스킬로 처음 친 적이 결투 상대 (그 적을 따라다니는 돌 말뚝 고리 — rfx_jstake). 결투가 every 틱 이어질 때마다 +1 (최대
 * max) — 상대에게 주는 피해 × (1 + 결투 × per), 다른 적에게는 × (1 − others) (도끼 평타 RpgCombat.meleeMult · 직업 스킬 JobKit.hit). 상대가 쓰러지면 near 칸 안의
 * 가장 가까운 적과 새 결투 (결투 × keep), lapse 틱 동안 상대를 못 맞히거나 range 칸 넘게 멀어지면 끝 (0). 4번 칸은 액스 그라인더 (피해 스킬 — 2026-11-05
 * 불사의 분노 태세 대신, GladiatorSkills.grinder). 홀름강의 결투장 동안 상대가 받는 피해 + vuln (RpgCombat.takenMult — GladiatorSkills.ult)</li>
 * </ul>
 */
public final class JobTraits6 {
    private JobTraits6() {}

    static final String WL = "warlord";
    static final String GL = "gladiator";
    /** SWING_LOOK 의 곰의 화신 */
    static final String BEAR_LOOK = "warlord_bear";
    /** rfx_jstake 장 수 (tools/job_fx_art_adv.py — 0 솟음 · 1 ~ 4 서 있음 · 5 가라앉음) */
    static final int STAKE = 6;
    /** 결투 고리의 말뚝 수 */
    static final int RING_STAKES = 8;
    /** 흙먼지 (누런 회색) */
    static final double[] DUST = {0.62, 0.52, 0.4};
    /** 결투 말뚝의 나무 (짙은 나무빛 — rfx_jstake 의 빛 칸 · 심지 칸은 직업 진홍: 밧줄 · 룬) */
    static final double[] WOOD = {0.45, 0.28, 0.16};
    private static final double[] WHITE = RuneKit.WHITE;

    static final class State {
        // 워로드
        long bearUntil;
        int bearTotal;
        long bearSwing = -1;
        long slumberUntil;
        // 글래디에이터
        UUID duel;
        long duelHit;
        long duelGain;
        int stacks;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** 시험: 결투가 저절로 오르지 않는 사람 (결투 값을 그대로 둠 — 기대 피해를 정확히) */
    private static final Set<UUID> FROZEN = new HashSet<>();

    /** 홀름강의 결투장: 상대 → {상대, 끝 틱, 받는 피해 더하기} */
    private record Arena(LivingEntity e, long until, double add) {}

    private static final Map<UUID, Arena> ARENAS = new HashMap<>();

    static void init() {
        RpgCombat.addWeaponHitHook(JobTraits6::onWeaponHit);
        RpgCombat.MeleeMult mm = RpgCombat.meleeMult;
        RpgCombat.meleeMult = (p, w, v) -> mm.mult(p, w, v) * meleeMult(p, w, v);
        java.util.function.ToDoubleFunction<LivingEntity> tm = RpgCombat.takenMult;
        RpgCombat.takenMult = e -> tm.applyAsDouble(e) * arenaTaken(e);
        RpgHealth.LossHook lh = RpgHealth.lossHook;
        RpgHealth.lossHook = (sp, src, loss, before, after) -> {
            lh.lost(sp, src, loss, before, after);
            onLoss(sp, src, loss);
        };
        java.util.function.BiConsumer<ServerPlayer, Weapon> sh = RpgCombat.swingHook;
        RpgCombat.swingHook = (sp, w) -> {
            sh.accept(sp, w);
            onSwing(sp, w);
        };
        // 곰의 화신: 도끼 평타의 궤적 색 · 범위 (SWING_LOOK warlord_bear — 태세가 아니라 따로)
        SwingLook.addSource((sp, out) -> {
            State st = STATES.get(sp.getUUID());
            if (st == null || st.bearUntil <= Tasks.now()) return;
            SwingLooks.Look l = SwingLooks.of(BEAR_LOOK);
            if (l != null) out.add(new SwingLook.Active(l, st.bearUntil, false));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATES.clear();
            ARENAS.clear();
            FROZEN.clear();
        });
    }

    static State of(ServerPlayer sp) {
        return STATES.computeIfAbsent(sp.getUUID(), k -> new State());
    }

    static Jobs.Job jobIf(ServerPlayer sp, String id) {
        Jobs.Job j = JobSystem.job(sp);
        return j != null && id.equals(j.id()) ? j : null;
    }

    static double trait(Jobs.Job j, String key, double fallback) {
        return j == null || j.trait() == null ? fallback : j.trait().def().numOr(key, fallback);
    }

    /** 직업을 바꿈 · 시험: 상태를 지움 */
    static void reset(ServerPlayer sp) {
        STATES.remove(sp.getUUID());
        FROZEN.remove(sp.getUUID());
    }

    static void forget(UUID id) {
        STATES.remove(id);
        FROZEN.remove(id);
    }

    static void lifeEnded(UUID id) {
        State st = STATES.get(id);
        if (st == null) return;
        st.bearUntil = 0;
        st.slumberUntil = 0;
        st.duel = null;
        st.stacks = 0;
    }

    // ------------------------------------------------------------ 워로드 「곰의 화신」

    /** 곰의 화신인지 */
    public static boolean bearOn(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st != null && st.bearUntil > Tasks.now();
    }

    /** 곰의 화신의 남은 몫 (0 ~ 1 — 아니면 0) */
    static double bearFrac(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        if (st == null || st.bearTotal <= 0) return 0;
        long left = st.bearUntil - Tasks.now();
        return left <= 0 ? 0 : Math.min(1, left / (double) st.bearTotal);
    }

    /** 시험: 곰의 화신 남은 틱 */
    public static int bearLeft(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : (int) Math.max(0, st.bearUntil - Tasks.now());
    }

    /** 곰의 화신 시작 (분노 가득): ticks 틱 — 받는 피해 −bearGuard · 몸을 감싼 곰 · 포효 · 번쩍임 · 알림 (화면은 흔들지 않음) */
    static void bearStart(ServerPlayer sp, Jobs.Job j, int ticks) {
        State st = of(sp);
        long now = Tasks.now();
        st.bearUntil = now + ticks;
        st.bearTotal = ticks;
        Guards.set(sp, ticks, trait(j, "bearGuard", 0.15), null, JobTraits6::bearOn);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        JobFx.bear(level, V.up(V.add(c, Kit.facingOf(sp), -0.35), 1.55), 1.9, 0.9, 0, JobFx.BEAR, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.9),
                bearGlow(j), bearCore(j));
        JobFx.aura(sp, j, 3.0, 1.0, 1.0);
        JobFx.smash(level, V.up(c, 0.05), 2.4, j, 0.5, Opt.of("alpha", 0.8));
        JobFx.eyeRing(sp, j, 2.4, 1.1, 0.5);
        SkillFx.sparks(level, V.up(c, 1.0), JobKit.glow(j), 14, 5);
        Ult.flash(level, c, JobKit.glow(j), Opt.of("radius", 4.0, "time", new double[] {0.03, 0.05, 0.3}), sp);
        SkillFx.play(level, "entity.polar_bear.warning", c, 0.7, 1.4);
        SkillFx.play(level, "mob.ravager.roar", c, 0.8, 0.9);
        Notify.notify(sp, "§6§l곰의 화신 §7— " + Math.round(ticks / 20.0) + "초 · 평타 곰 발톱 · 받는 피해 −" + Math.round(trait(j, "bearGuard", 0.15) * 100) + "%", 30);
        JobTraits.sendGauge(sp, false);
    }

    /** 곰의 기운 빛 (짙은 갈색 — 곰 그림의 몸) */
    static double[] bearGlow(Jobs.Job j) {
        return JobKit.core(j);
    }

    /** 곰 그림의 심지 (주황 — 눈 · 이빨 · 털끝) */
    static double[] bearCore(Jobs.Job j) {
        double[] a = JobKit.glow(j);
        return new double[] {a[0] + (1 - a[0]) * 0.3, a[1] + (1 - a[1]) * 0.3, a[2] + (1 - a[2]) * 0.3};
    }

    /** 곰의 화신이 끝남: 분노 0 · 곰이 흩어짐 */
    private static void bearEnd(ServerPlayer sp, Jobs.Job j) {
        JobTraitsMore.setRage(sp, 0);
        ServerLevel level = sp.level();
        JobFx.bear(level, V.up(V.add(sp.position(), Kit.facingOf(sp), -0.35), 1.55), 1.9, 0.4, JobFx.BEAR - 1, 1, false, JobFx.hide1p(sp).with("vel",
                RuneKit.bodyVel(sp), "alpha", 0.6), bearGlow(j), bearCore(j));
        SkillFx.sparks(level, V.up(sp.position(), 1.2), JobKit.glow(j), 8, 2.5);
        SkillFx.play(level, "block.amethyst_block.chime", sp.position(), 0.6, 0.5);
    }

    // ------------------------------------------------------------ 워로드 동면 · 거인의 무게

    /** 동면 시작 (WarlordSkills.slumber): ticks 틱 동안 받은 피해만큼 분노 */
    static void slumberStart(ServerPlayer sp, int ticks) {
        of(sp).slumberUntil = Tasks.now() + ticks;
    }

    /** 동면 중인지 */
    public static boolean slumbering(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st != null && st.slumberUntil > Tasks.now() && jobIf(sp, WL) != null;
    }

    /** 맞음: 동면 중이면 받은 피해 (최대 체력 1%) 마다 분노 + rage (한 번에 최대 30) · 막아낸 자리의 주황 물결 */
    private static void onLoss(ServerPlayer sp, DamageSource src, double loss) {
        if (!(loss > 0) || !slumbering(sp)) return;
        Jobs.Job j = jobIf(sp, WL);
        Jobs.Skill k = j == null ? null : j.skill(WarlordSkills.SLUMBER);
        double max = RpgHealth.maxOf(sp);
        if (k == null || !(max > 0)) return;
        int gain = (int) Math.min(30, Math.ceil(loss / max * 100 * k.num("rage", 4)));
        if (gain > 0) JobTraitsMore.gainRage(sp, j, gain);
        if (RuneKit.fxQuota("jobSlumberHit" + sp.getId(), 1)) {
            ServerLevel level = sp.level();
            Vec3 c = V.up(sp.position(), 0.9);
            JobFx.ringFacing(level, c, 1.1, j, V.UP, 0.3, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
            SkillFx.sparks(level, c, JobKit.glow(j), 8, 3.5);
            SkillFx.play(level, "entity.polar_bear.hurt", c, 0.6, 0.5);
        }
    }

    /** 근접 평타 배율: 결투 (상대 × (1 + 결투 × per) · 다른 적 × (1 − others)) */
    static double meleeMult(ServerPlayer sp, Weapon w, LivingEntity victim) {
        if (w == null || !"axe".equals(w.type())) return 1;
        Jobs.Job j = JobSystem.job(sp);
        if (j == null) return 1;
        if (!GL.equals(j.id()) || victim == null) return 1;
        LivingEntity t = duelTarget(sp);
        if (t == null) return 1; // 이 평타가 결투를 엶 (onWeaponHit) — 처음 한 대는 그대로
        return victim == t ? 1 + duelBonus(sp, j) : 1 - trait(j, "others", 0.4);
    }

    /** 휘두를 때마다 (허공 포함): 곰의 화신 — 앞에 큰 곰 발톱 세 줄 */
    private static void onSwing(ServerPlayer sp, Weapon w) {
        if (w == null || !"axe".equals(w.type())) return;
        Jobs.Job j = jobIf(sp, WL);
        if (j == null) return;
        State st = of(sp);
        long now = Tasks.now();
        ServerLevel level = sp.level();
        if (!bearOn(sp) || st.bearSwing == now) return;
        st.bearSwing = now;
        Vec3 look = sp.getViewVector(1F);
        Vec3 at = V.add(V.add(sp.getEyePosition(), look, 2.3), JobFx.screenUp(look), -0.2);
        double tilt = (now % 2 == 0 ? 20 : -20) + Kit.rand() * 10;
        JobFx.bearClaws(level, at, 1.5, tilt, 0.3, JobFx.only1p(sp).with("vel", RuneKit.bodyVel(sp)), j);
        JobFx.bearClaws(level, V.up(V.add(sp.position(), Kit.facingOf(sp), 1.6), 1.2), 1.25, tilt, 0.3, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), j);
        SkillFx.play(level, "entity.polar_bear.hurt", sp.position(), 0.7, 0.35);
    }

    // ------------------------------------------------------------ 글래디에이터 「결투」

    /** 결투 상대 (살아 있고 같은 세계 range 칸 안 · lapse 틱 안에 맞힘) — 없으면 null */
    public static LivingEntity duelTarget(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        if (st == null || st.duel == null) return null;
        Jobs.Job j = jobIf(sp, GL);
        if (j == null) return null;
        if (Tasks.now() - st.duelHit > trait(j, "lapse", 100)) return null;
        Entity e = sp.level().getEntity(st.duel);
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le.isRemoved()) return null;
        double r = trait(j, "range", 24);
        return le.distanceToSqr(sp) <= r * r ? le : null;
    }

    /** 지금 결투 (0 ~ max) */
    public static int duelStacks(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null || duelTarget(sp) == null ? 0 : st.stacks;
    }

    /** 상대에게 더하는 몫 (결투 × per) */
    static double duelBonus(ServerPlayer sp, Jobs.Job j) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.stacks * trait(j, "per", 0.03);
    }

    /** 시험: 결투를 e 와 stacks 로 엶 · freeze 면 저절로 오르지 않음 */
    public static void setDuel(ServerPlayer sp, LivingEntity e, int stacks, boolean freeze) {
        Jobs.Job j = jobIf(sp, GL);
        if (j == null || e == null) return;
        startDuel(sp, j, e, stacks, false);
        if (freeze) FROZEN.add(sp.getUUID());
        else FROZEN.remove(sp.getUUID());
    }

    /**
     * 결투를 엶 (e 와 · 결투 stacks 부터): 상대 둘레에 돌 말뚝이 솟음 (rfx_jstake 의 솟는 장) · 머리 위 붉은 섬광 · 쇠 부딪는 소리. 이어진 결투 (상대가 쓰러져 옮겨감)
     * 면 알림 줄이 다름
     */
    static void startDuel(ServerPlayer sp, Jobs.Job j, LivingEntity e, int stacks, boolean moved) {
        State st = of(sp);
        long now = Tasks.now();
        st.duel = e.getUUID();
        st.duelHit = now;
        st.duelGain = now;
        st.stacks = Math.max(0, Math.min(j.trait() == null ? 10 : j.trait().max(), stacks));
        if (e.level() instanceof ServerLevel level) {
            duelRing(level, e, j, 0, 1, 0.3);
            RuneKit.later(5, () -> {
                if (!e.isRemoved() && e.level() instanceof ServerLevel lv) duelRing(lv, e, j, 1, 4, 0.5);
            });
            Vec3 over = RuneKit.overHead(e, 0.35);
            JobFx.two(level, "jimpact", JobImpact.IMPACT, over, 0.3, Opt.of("size", 0.8, "spin", Kit.rand() * 360, "fade", 0.3, "vel", RuneKit.entVel(e)),
                    JobFx.outer(j), WHITE);
            SkillFx.play(level, "block.anvil.land", e.position(), 1.6, 0.45);
            SkillFx.play(level, "item.armor.equip_iron", e.position(), 0.8, 0.8);
        }
        if (moved) Notify.notify(sp, "§c§l결투 §7— 다음 상대 (결투 " + st.stacks + ")", 24);
        JobTraits.sendGauge(sp, false);
    }

    /** 결투를 끝냄 (0) */
    private static void endDuel(ServerPlayer sp, State st) {
        st.duel = null;
        st.stacks = 0;
        JobTraits.sendGauge(sp, false);
    }

    /**
     * 결투 고리 (rfx_jstake — 세워 카메라 쪽으로 돎): 상대 둘레 radius 칸 (몸 크기만큼 더) 에 말뚝 RING_STAKES 개 — 장 from 부터 count 장 (서 있음 1 ~ 4 는 이어 돌림)
     * · 상대를 따라 움직임
     */
    static void duelRing(ServerLevel level, LivingEntity e, Jobs.Job j, int from, int count, double life) {
        double r = trait(j, "radius", 3) + Kit.bodyPad(e);
        Vec3 c = e.position();
        double gy = RuneKit.groundAt(level, V.up(c, 0.5), c.y);
        double turn = (Tasks.now() % 360) * 0.6;
        boolean loop = from >= 1 && from + count <= 5;
        for (int i = 0; i < RING_STAKES; i++) {
            double a = Math.toRadians(turn + i * 360.0 / RING_STAKES);
            Vec3 g = new Vec3(c.x + Math.cos(a) * r, gy, c.z + Math.sin(a) * r);
            double size = 0.62;
            JobFx.cells(level, "jstake", STAKE, from, count, V.up(g, size), life, loop ? 8 : 0, loop, Opt.of("size", size, "vel", RuneKit.entVel(e), "fade", 0.2,
                    "phase", (double) (i % 4)), WOOD, JobFx.outer(j));
        }
    }

    /** 도끼 평타가 맞음 (피해가 들어간 뒤): 글래디에이터 — 결투 상대가 없으면 그 적과 결투 · 상대면 이어짐 */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        if (w == null || victim == null || !"axe".equals(w.type()) || !Combat.isMeleeWeapon(w)) return;
        Jobs.Job j = jobIf(sp, GL);
        if (j == null) return;
        LivingEntity t = duelTarget(sp);
        if (t == null) {
            if (victim.isAlive() && !victim.isRemoved()) startDuel(sp, j, victim, 0, false);
        } else if (t == victim) of(sp).duelHit = Tasks.now();
    }

    /** 직업 스킬 피해 배율 (JobKit.hit — 피해 전): 결투 상대가 없으면 이 적과 결투 · 상대 × (1 + 결투 × per) · 다른 적 × (1 − others) */
    static double duelMult(ServerPlayer sp, LivingEntity e, Jobs.Job j) {
        if (j == null || !GL.equals(j.id()) || e == null) return 1;
        LivingEntity t = duelTarget(sp);
        if (t == null) {
            if (!e.isAlive() || e.isRemoved()) return 1;
            startDuel(sp, j, e, 0, false);
            return 1;
        }
        return e == t ? 1 + duelBonus(sp, j) : 1 - trait(j, "others", 0.4);
    }

    /** 직업 스킬 피해가 들어간 뒤 (JobKit.hit): 결투 상대를 맞혔으면 이어짐 */
    static void afterJobHit(ServerPlayer sp, LivingEntity e, Jobs.Job j, double dealt) {
        if (j == null || !GL.equals(j.id()) || !(dealt > 0) || e == null) return;
        State st = STATES.get(sp.getUUID());
        if (st != null && e.getUUID().equals(st.duel)) st.duelHit = Tasks.now();
    }

    // ------------------------------------------------------------ 홀름강의 결투장 (받는 피해)

    /** 결투장: ticks 틱 동안 e 가 받는 피해 + add */
    static void arena(LivingEntity e, double add, int ticks) {
        if (e == null || e.isRemoved()) return;
        ARENAS.put(e.getUUID(), new Arena(e, Tasks.now() + ticks, add));
    }

    /** 결투장을 거둠 (상대가 쓰러짐 · 끝) */
    static void arenaEnd(LivingEntity e) {
        if (e != null) ARENAS.remove(e.getUUID());
    }

    /** 이 적의 받는 피해 배율 (결투장 안이면 1 + add) */
    public static double arenaTaken(LivingEntity e) {
        if (e == null || ARENAS.isEmpty()) return 1;
        Arena a = ARENAS.get(e.getUUID());
        return a == null || a.until() <= Tasks.now() ? 1 : 1 + a.add();
    }

    /** 서버 틱마다 (JobTraits): 끝난 결투장을 지움 */
    static void tickGlobal(long now) {
        if (!ARENAS.isEmpty()) ARENAS.values().removeIf(a -> a.until() <= now || a.e().isRemoved() || !a.e().isAlive());
    }

    // ------------------------------------------------------------ 틱 · 태세 연출 · 게이지

    /** 4틱마다 (JobTraits.tick): 곰의 화신이 끝남 · 곰 / 결투가 오름 · 옮겨감 · 끝남 · 말뚝 고리 */
    static void tick(ServerPlayer sp, Jobs.Job j, long now) {
        State st = STATES.get(sp.getUUID());
        if (st == null) return;
        if (WL.equals(j.id())) {
            if (st.bearTotal > 0 && st.bearUntil <= now) {
                st.bearTotal = 0;
                bearEnd(sp, j);
                JobTraits.sendGauge(sp, true);
            } else if (st.bearUntil > now && now % 8 == 0 && !sp.isSpectator()) {
                bearAura(sp, j);
            }
            return;
        }
        if (!GL.equals(j.id()) || st.duel == null) return;
        LivingEntity t = duelTarget(sp);
        if (t == null) {
            // 상대가 쓰러짐 (맞힌 지 lapse 안) → 가장 가까운 적과 새 결투 (결투 × keep) · 놓침 (오래 못 맞힘 · 멀어짐) → 끝
            Entity e = sp.level().getEntity(st.duel);
            boolean fell = (e == null || e.isRemoved() || (e instanceof LivingEntity le && !le.isAlive())) && now - st.duelHit <= trait(j, "lapse", 100);
            if (fell) {
                List<LivingEntity> near = JobKit.near(sp, trait(j, "near", 12), 1);
                if (!near.isEmpty()) {
                    startDuel(sp, j, near.get(0), (int) Math.floor(st.stacks * trait(j, "keep", 0.5)), true);
                    return;
                }
            }
            endDuel(sp, st);
            return;
        }
        if (!FROZEN.contains(sp.getUUID())) {
            int every = (int) Math.max(4, trait(j, "every", 20));
            int max = j.trait() == null ? 10 : j.trait().max();
            if (now - st.duelGain >= every) {
                st.duelGain += every;
                if (st.stacks < max) {
                    st.stacks++;
                    if (st.stacks == max && t.level() instanceof ServerLevel lv) {
                        SkillFx.play(lv, "block.anvil.use", t.position(), 1.4, 0.5);
                        Notify.notify(sp, "§c§l결투 " + max + " §7— 상대에게 +" + Math.round(max * trait(j, "per", 0.03) * 100) + "%", 24);
                    }
                    JobTraits.sendGauge(sp, false);
                }
            }
        }
        // 말뚝 고리 (8틱마다 — 서 있는 장을 이어 돌림, 상대를 따라감)
        if (now % 8 == 0 && t.level() instanceof ServerLevel lv && RuneKit.fxQuota("jobDuelRing", 6)) duelRing(lv, t, j, 1, 4, 0.5);
    }

    /** 곰의 화신 동안 8틱마다: 몸을 감싼 곰 (3인칭 · 다른 사람 — 포효 장면을 이어 돌림) · 몸의 주황 기운 · 1인칭 손의 불티 */
    private static void bearAura(ServerPlayer sp, Jobs.Job j) {
        ServerLevel level = sp.level();
        Vec3 c = V.up(V.add(sp.position(), Kit.facingOf(sp), -0.35), 1.55);
        // 영상 1: 빛 칸 0.55 는 몸을 덮는 주황 상자처럼 보였음 → 빛 옅게 · 선 (눈 · 이빨 · 발톱 · 테) 짙게
        JobFx.bear(level, c, 1.75, 0.46, 3, 4, true, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.4, "coreAlpha", 0.92), bearGlow(j), bearCore(j));
        JobFx.aura(sp, j, 2.6, 0.5, 0.5);
        RuneKit.Hand h = RuneKit.handOf(sp);
        SkillFx.fx(level, "rpg:arrow_spark", V.up(h.hand(), 0.1), Opt.of("dir", V.UP, "speed", 1.6, "size", 0.13, "grow", 0.8, "life", 0.35, "color", JobKit.glow(j),
                "alpha", 1.0));
    }

    /** 눈의 붉은 빛 (두 점 — 3인칭 · 다른 사람) */
    static void eyeGlint(ServerPlayer sp, Jobs.Job j, double life) {
        ServerLevel level = sp.level();
        Vec3 f = V.flat(sp.getViewVector(1F));
        Vec3 r = V.rightOf(f);
        Vec3 eye = V.add(sp.getEyePosition(), f, 0.32);
        for (int s = -1; s <= 1; s += 2) {
            RuneKit.rfx(level, "prop", V.add(eye, r, 0.11 * s), JobFx.hide1p(sp).with("size", 0.09, "life", life, "cell", (double) RuneKit.RFX.get("glint")[0], "spin", 0.0,
                    "spinRate", 90.0, "color", JobFx.outer(j), "grow", 0.4, "fade", 0.5, "vel", RuneKit.bodyVel(sp)));
        }
    }

    /** 게이지 값: 글래디에이터 = 결투 (워로드는 JobTraitsMore 의 분노) */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        return j != null && GL.equals(j.id()) ? duelStacks(sp) : 0;
    }

    /** 태세 줄 (태세가 없을 때): 곰의 화신 {이름, 남은 틱, 전체 틱} — 없으면 null */
    static Object[] stanceLine(ServerPlayer sp, Jobs.Job j) {
        if (j == null || !WL.equals(j.id())) return null;
        State st = STATES.get(sp.getUUID());
        long now = Tasks.now();
        if (st == null || st.bearUntil <= now) return null;
        return new Object[] {"곰의 화신", (int) (st.bearUntil - now), st.bearTotal};
    }
}
