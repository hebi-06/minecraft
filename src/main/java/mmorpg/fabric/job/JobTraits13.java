package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 2차 직업 — 성기사 2차 (재설계안 2판 — 2026-11-06 사용자 "다음 작업") 의 특성 (서버 — jobs.json advance, 썬더로드의 심판 쌓임 · 벼락 · 수호 오라는 JobTraits4 의
 * 성기사 줄기):
 * <ul>
 * <li>썬더로드 「심판」: 성기사와 같이 쌓여 max 에서 벼락 → 그 벼락이 둘레 forkRange 칸의 다른 적 forks 에게 갈라져 튐 (위력 × forkHit · forkStun 틱 기절) · 둘레
 * shieldRadius 칸 아군 (나 포함) shieldFor 틱 보호막 (최대 체력 × shield) ({@link #thunderVerdict}). 썬더헤드 (먹구름 — 망치 평타마다 벼락 · 네 번째마다 번개 고리 ·
 * 맞을 때마다 아군 보호막), 썬더스텝 · 던브레이커 빛의 가호 (받는 피해 없음 — {@link #guardFor})</li>
 * <li>타이런트 「강탈」 (1차와 거꾸로 — 수호 오라 없음): 망치 평타 · 직업 스킬이 힘을 빼앗음 ({@link #steal} — 최대 max · keep 틱 동안 못 빼앗으면 decay 틱마다 하나씩
 * 사라짐) — 하나당 주는 피해 + dealt (RpgCombat.dealtMult) · 받는 피해 − taken (RpgHealth.sourceReduce) · 몸 크기 + grow (Attributes.SCALE), 빼앗긴 적은
 * weakenFor 틱 주는 피해 − weaken 씩 (RpgHealth.weakenOf — 최대 −30%), 콜로서스 (크기 + grow · 받는 피해 − reduce · 빼앗는 힘 × stealMult), 게이지 = 빼앗은 힘</li>
 * </ul>
 */
public final class JobTraits13 {
    private JobTraits13() {}

    static final String TL = "thunderlord";
    static final String TY = "tyrant";
    static final String HEAD = "thunderlord_head";
    static final String COLOSSUS = "tyrant_colossus";

    /** 썬더로드: 금빛 벼락 · 하늘빛 · 먹구름 (짙은 보라 회색) · 새벽 (복숭앗빛 금) · 돌 (잿빛) */
    static final double[] GOLD = {1.0, 0.84, 0.3};
    static final double[] GOLD_CORE = {1.0, 0.97, 0.82};
    static final double[] SKY = {0.56, 0.86, 1.0};
    static final double[] SKY_CORE = {0.92, 0.98, 1.0};
    static final double[] CLOUD = {0.15, 0.13, 0.22};
    static final double[] CLOUD_CORE = {0.36, 0.33, 0.48};
    static final double[] DAWN = {1.0, 0.7, 0.42};
    static final double[] DAWN_CORE = {1.0, 0.95, 0.8};
    static final double[] STONE = {0.5, 0.48, 0.46};
    static final double[] STONE_CORE = {1.0, 0.86, 0.5};
    /** 타이런트: 훔친 번개 (보랏빛) · 빼앗은 힘 (핏빛 붉은) */
    static final double[] VIOLET = {0.7, 0.32, 1.0};
    static final double[] VIOLET_CORE = {0.95, 0.86, 1.0};
    static final double[] CRIMSON = {1.0, 0.3, 0.34};
    static final double[] CRIMSON_CORE = {1.0, 0.82, 0.8};

    static final class State {
        /** 썬더로드 썬더헤드: 망치 평타 수 (네 번째마다 번개 고리) · 맞아서 아군 보호막을 준 틱 */
        int headHits;
        long shieldAt = -9999;
        /** 타이런트 강탈: 빼앗은 힘 · 마지막으로 빼앗은 틱 · 마지막으로 사라진 틱 · 마지막 망치 평타로 빼앗은 틱 */
        int power;
        long gainAt;
        long decayAt;
        long hammerAt = -9999;
        /** 스톨른 썬더 동안 더 커지는 몸 (몫 — 끝나면 0) */
        double ultGrow;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** 받는 피해 없음 (썬더스텝 · 던브레이커 빛의 가호): 사람 → 끝나는 틱 */
    private static final Map<UUID, Long> GUARD = new HashMap<>();
    /** 힘을 빼앗긴 적: 적 → {빼앗긴 힘, 끝나는 틱} */
    private static final Map<UUID, double[]> ROBBED = new HashMap<>();
    /** 타이런트 몸 크기 (Attributes.SCALE — 강탈 + 콜로서스) */
    private static final Identifier SCALE_ID = Mmorpg.id("job_tyrant_scale");
    /** 썬더헤드 먹구름 벼락 (평타에 더한 몫의 구분) */
    static final String KEY_HEAD = "job_thunderhead";

    static void init() {
        RpgCombat.addScaledBonusHook(JobTraits13::headBonus);
        RpgCombat.addWeaponHitHook(JobTraits13::onWeaponHit);
        java.util.function.ToDoubleFunction<ServerPlayer> dm = RpgCombat.dealtMult;
        RpgCombat.dealtMult = p -> dm.applyAsDouble(p) * dealtMult(p);
        java.util.function.ToDoubleBiFunction<ServerPlayer, DamageSource> sr = RpgHealth.sourceReduce;
        RpgHealth.sourceReduce = (p, src) -> Math.max(sr.applyAsDouble(p, src), sourceReduce(p));
        java.util.function.ToDoubleFunction<Entity> wk = RpgHealth.weakenOf;
        RpgHealth.weakenOf = e -> 1 - (1 - wk.applyAsDouble(e)) * (1 - weakenOf(e));
        RpgHealth.LossHook lh = RpgHealth.lossHook;
        RpgHealth.lossHook = (sp, src, loss, before, after) -> {
            lh.lost(sp, src, loss, before, after);
            onLoss(sp);
        };
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATES.clear();
            GUARD.clear();
            ROBBED.clear();
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
        GUARD.remove(sp.getUUID());
        scaleMod(sp, 0);
    }

    static void forget(UUID id) {
        STATES.remove(id);
        GUARD.remove(id);
    }

    static void lifeEnded(UUID id) {
        State st = STATES.get(id);
        GUARD.remove(id);
        if (st == null) return;
        st.headHits = 0;
        st.power = 0;
        st.ultGrow = 0;
    }

    // ------------------------------------------------------------ 받는 피해 · 주는 피해

    /** ticks 틱 동안 받는 피해 없음 (썬더스텝 — 번개가 된 동안 · 던브레이커 빛의 가호) */
    static void guardFor(ServerPlayer p, int ticks) {
        long until = Tasks.now() + Math.max(0, ticks);
        GUARD.merge(p.getUUID(), until, Math::max);
    }

    /** 시험: 받는 피해 없음이 걸려 있는지 */
    public static boolean guarded(ServerPlayer p) {
        Long until = GUARD.get(p.getUUID());
        return until != null && until > Tasks.now();
    }

    /** RpgHealth.sourceReduce: 빛의 가호 1 (받지 않음) · 타이런트 빼앗은 힘 하나당 taken (+ 콜로서스 reduce) */
    private static double sourceReduce(ServerPlayer p) {
        if (guarded(p)) return 1;
        Jobs.Job j = jobIf(p, TY);
        if (j == null) return 0;
        State st = STATES.get(p.getUUID());
        double r = st == null ? 0 : st.power * trait(j, "taken", 0.02);
        if (JobTraits.stanceOn(p, COLOSSUS)) {
            Jobs.Skill k = j.skill(COLOSSUS);
            r += k == null ? 0.1 : k.num("reduce", 0.1);
        }
        return Math.min(0.6, r);
    }

    /** RpgCombat.dealtMult: 타이런트 빼앗은 힘 하나당 + dealt (평타 · 직업 스킬 · 룬 — 가하는 피해 배율) */
    private static double dealtMult(ServerPlayer p) {
        Jobs.Job j = jobIf(p, TY);
        State st = j == null ? null : STATES.get(p.getUUID());
        return st == null ? 1 : 1 + st.power * trait(j, "dealt", 0.03);
    }

    /** 시험: 타이런트 가하는 피해 배율 (빼앗은 힘) */
    public static double powerMult(ServerPlayer p) {
        return dealtMult(p);
    }

    /** RpgHealth.weakenOf: 힘을 빼앗긴 적이 플레이어에게 주는 피해 − weaken × 빼앗긴 힘 (최대 30%) */
    private static double weakenOf(Entity e) {
        if (e == null || ROBBED.isEmpty()) return 0;
        double[] r = ROBBED.get(e.getUUID());
        if (r == null || r[1] <= Tasks.now()) return 0;
        return Math.min(0.3, r[0]);
    }

    /** 시험: 이 적이 빼앗긴 몫 (주는 피해 감소) */
    public static double robbedOf(Entity e) {
        return weakenOf(e);
    }

    /** 내가 맞음 (RpgHealth.lossHook): 썬더헤드 동안 shieldGap 틱에 한 번 둘레 아군 (나 포함) 보호막 */
    private static void onLoss(ServerPlayer sp) {
        if (sp == null || !JobTraits.stanceOn(sp, HEAD)) return;
        Jobs.Job j = jobIf(sp, TL);
        Jobs.Skill k = j == null ? null : j.skill(HEAD);
        if (k == null) return;
        State st = of(sp);
        long now = Tasks.now();
        if (now - st.shieldAt < (long) k.num("shieldGap", 20)) return;
        st.shieldAt = now;
        RuneKit.later(1, () -> {
            if (!sp.isRemoved()) shieldAllies(sp, j, sp.position(), k.num("shieldRadius", 6), k.num("shield", 0.03), (int) k.num("shieldFor", 60), 0.38);
        });
    }

    /** 둘레 r 칸 아군 (나 포함) ticks 틱 보호막 (최대 체력 × frac) — 아군마다 빛의 방패 */
    static void shieldAllies(ServerPlayer sp, Jobs.Job j, Vec3 c, double r, double frac, int ticks, double size) {
        ServerLevel level = sp.level();
        for (ServerPlayer o : JobTraits4.allies(sp, c, r)) {
            double max = RpgHealth.maxOf(o);
            RpgHealth.addShield(o, max * frac, ticks, max * frac);
            JobFx.aegis(level, V.up(o.position(), 1.1), size, j, 0, 0.4, (o == sp ? JobFx.hide1p(sp) : Opt.empty()).with("vel", RuneKit.bodyVel(o)));
        }
        SkillFx.play(level, "block.beacon.power_select", c, 1.8, 0.45);
    }

    // ------------------------------------------------------------ 썬더로드 심판의 벼락 · 썬더헤드

    /**
     * 썬더로드 심판의 벼락 (JobTraits4.verdict 뒤): 둘레 forkRange 칸의 다른 적 forks 에게 갈라져 튐 (가까운 차례 · 위력 × forkHit · forkStun 틱 기절 — 보스 제외) ·
     * 둘레 shieldRadius 칸 아군 (나 포함) shieldFor 틱 보호막 (최대 체력 × shield)
     */
    static void thunderVerdict(ServerPlayer sp, LivingEntity e, Jobs.Job j) {
        if (!(e.level() instanceof ServerLevel level)) return;
        String life = RuneKit.lifeToken(sp);
        double P = JobSystem.power(sp, j);
        int forks = Math.max(0, (int) trait(j, "forks", 2));
        double mult = trait(j, "forkHit", 0.8);
        int stun = (int) trait(j, "forkStun", 10);
        List<LivingEntity> foes = new ArrayList<>(Kit.around(sp, e.position(), trait(j, "forkRange", 6), 4));
        foes.removeIf(x -> x == e || !x.isAlive() || x.isRemoved());
        foes.sort(java.util.Comparator.comparingDouble(x -> x.distanceToSqr(e)));
        Vec3 from = Kit.bodyPoint(e);
        for (int i = 0; i < Math.min(forks, foes.size()); i++) {
            LivingEntity x = foes.get(i);
            Vec3 to = Kit.bodyPoint(x);
            JobFx.arc(level, from, to, GOLD, GOLD_CORE, 0.3, Opt.empty());
            JobFx.arc(level, from, to, SKY, SKY_CORE, 0.2, Opt.of("alpha", 0.7));
            JobKit.hitLater(sp, x, life, JobKit.now() + 2, () -> {
                if (JobKit.hit(sp, x, j, P, mult, true, false) > 0 && x.level() instanceof ServerLevel xl) {
                    JobTraits4.stun(x, j, stun);
                    JobImpact.fx(sp, x, j, 0.9);
                    JobFx.volt(xl, V.up(x.position(), 0.08), 0.8 + Kit.bodyPad(x) * 0.5, j, 0.3, Opt.of("vel", RuneKit.entVel(x)));
                }
            });
        }
        if (!foes.isEmpty() && forks > 0) SkillFx.play(level, "entity.lightning_bolt.impact", from, 1.6, 0.45);
        shieldAllies(sp, j, sp.position(), trait(j, "shieldRadius", 6), trait(j, "shield", 0.04), (int) trait(j, "shieldFor", 60), 0.4);
    }

    /** 썬더헤드를 씀: 평타 수를 처음부터 */
    static void headOn(ServerPlayer sp) {
        State st = of(sp);
        st.headHits = 0;
        st.shieldAt = -9999;
    }

    /** 썬더헤드 동안 망치 평타에 더함: 먹구름 벼락 (평타 × bolt) */
    private static RpgCombat.Bonus headBonus(ServerPlayer sp, LivingEntity victim, Weapon w, double dmg) {
        if (w == null || !"hammer".equals(w.type()) || !(dmg > 0) || !JobTraits.stanceOn(sp, HEAD)) return null;
        Jobs.Job j = jobIf(sp, TL);
        Jobs.Skill k = j == null ? null : j.skill(HEAD);
        if (k == null) return null;
        return new RpgCombat.Bonus(dmg * k.num("bolt", 0.5), "", KEY_HEAD, DamageNumbers.Style.JOB, null, JobKit.grad(j));
    }

    /** 무기로 맞힘: 썬더헤드 (먹구름 벼락 · 심판 + judge 더 · every 번째마다 번개 고리) · 타이런트 망치 평타 (힘 1 — hammerGap 틱에 한 번) */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        if (w == null || victim == null || !"hammer".equals(w.type())) return;
        Jobs.Job ty = jobIf(sp, TY);
        if (ty != null) {
            State st = of(sp);
            long now = Tasks.now();
            if (now - st.hammerAt >= (long) trait(ty, "hammerGap", 10)) {
                st.hammerAt = now;
                steal(sp, victim, ty, 1);
            }
            return;
        }
        Jobs.Job j = jobIf(sp, TL);
        if (j == null || !JobTraits.stanceOn(sp, HEAD)) return;
        Jobs.Skill k = j.skill(HEAD);
        if (k == null || !(victim.level() instanceof ServerLevel level)) return;
        JobTraits4.judge(sp, victim, j, (int) k.num("judge", 1));
        State st = of(sp);
        st.headHits++;
        // 먹구름에서 그 적에게 벼락
        Vec3 g = victim.position();
        JobFx.thunder(level, g, 7.5 + victim.getBbHeight(), j, 0.3, Opt.of("vel", RuneKit.entVel(victim)));
        JobFx.volt(level, V.up(g, 0.08), 0.9 + Kit.bodyPad(victim) * 0.5, j, 0.3, Opt.of("vel", RuneKit.entVel(victim)));
        SkillFx.play(level, "entity.lightning_bolt.impact", g, 1.5, 0.4);
        int every = Math.max(2, (int) k.num("every", 4));
        if (st.headHits % every != 0) return;
        // 네 번째: 그 적 둘레 번개 고리 — 보스 아닌 적 기절
        double r = k.num("ringRadius", 3.5);
        int stun = (int) k.num("ringStun", 10);
        JobFx.volt(level, V.up(g, 0.1), r, j, 0.45, Opt.empty());
        JobFx.halo(level, V.up(g, 0.06), r, j, 0.45, Opt.of("alpha", 0.8));
        RuneKit.novaRing(level, g, 0.6, r / 0.6 - 1, GOLD, GOLD_CORE, Opt.of("life", 0.4));
        SkillFx.play(level, "item.trident.thunder", g, 1.3, 0.45);
        for (LivingEntity x : Kit.around(sp, g, r, 3)) {
            if (!x.isAlive() || x.isRemoved()) continue;
            JobTraits4.stun(x, j, stun);
            JobFx.arc(level, V.up(g, 0.4), Kit.bodyPoint(x), GOLD, GOLD_CORE, 0.25, Opt.empty());
        }
        JobSystem.note(sp, "§e썬더헤드 §7— 번개 고리");
    }

    // ------------------------------------------------------------ 타이런트 강탈

    /** 지금 빼앗은 힘 (시험 · 게이지) */
    public static int power(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null || jobIf(sp, TY) == null ? 0 : st.power;
    }

    /** 시험용: 빼앗은 힘을 정함 */
    public static void setPower(ServerPlayer sp, int n) {
        Jobs.Job j = jobIf(sp, TY);
        if (j == null) return;
        State st = of(sp);
        st.power = Math.max(0, Math.min(Math.max(1, j.trait().max()), n));
        st.gainAt = Tasks.now();
        applyScale(sp, j);
        JobTraits.sendGauge(sp, false);
    }

    /**
     * 힘 n 을 빼앗음 (콜로서스 동안 × stealMult): 최대 max · 빼앗긴 적은 weakenFor 틱 주는 피해 − weaken × n · 그 적에서 핏빛 기운이 내 몸으로 · 몸 크기. 빼앗은 수
     */
    static int steal(ServerPlayer sp, LivingEntity e, Jobs.Job j, int n) {
        if (j == null || !TY.equals(j.id()) || n <= 0) return 0;
        if (JobTraits.stanceOn(sp, COLOSSUS)) {
            Jobs.Skill k = j.skill(COLOSSUS);
            n = (int) Math.round(n * (k == null ? 2 : k.num("stealMult", 2)));
        }
        State st = of(sp);
        int max = Math.max(1, j.trait().max());
        int before = st.power;
        st.power = Math.min(max, st.power + n);
        st.gainAt = Tasks.now();
        st.decayAt = st.gainAt;
        if (e != null) {
            double[] r = ROBBED.computeIfAbsent(e.getUUID(), x -> new double[2]);
            if (r[1] <= Tasks.now()) r[0] = 0;
            r[0] = Math.min(0.3, r[0] + n * trait(j, "weaken", 0.03));
            r[1] = Tasks.now() + (long) trait(j, "weakenFor", 160);
            if (e.level() instanceof ServerLevel level) {
                // 빼앗음: 그 적에서 핏빛 기운이 내 가슴으로 · 그 적 둘레 보랏빛 지직
                Vec3 from = Kit.bodyPoint(e);
                Vec3 to = V.up(sp.position(), 1.2);
                for (int i = 0; i < Math.min(4, n + 1); i++) {
                    Vec3 s = V.add(from, new Vec3((Kit.rand() - 0.5) * 0.6, (Kit.rand() - 0.5) * 0.6, (Kit.rand() - 0.5) * 0.6));
                    JobFx.soulFly(level, s, to, 9 + i, 0.26, CRIMSON, CRIMSON_CORE, Opt.empty());
                }
                JobFx.arc(level, from, to, VIOLET, VIOLET_CORE, 0.2, Opt.of("alpha", 0.7));
                JobFx.burst(level, from, 0.7 + Kit.bodyPad(e) * 0.4, CRIMSON, CRIMSON_CORE, 0.25, Opt.of("vel", RuneKit.entVel(e)));
                SkillFx.play(level, "entity.warden.heartbeat", sp.position(), 1.3, 0.6);
            }
        }
        if (st.power != before) {
            applyScale(sp, j);
            if (st.power >= max && before < max) {
                JobSystem.note(sp, "§5강탈 §7— 힘 " + max + " (주는 피해 +" + Math.round(max * trait(j, "dealt", 0.03) * 100) + "%)");
                SkillFx.play(sp.level(), "entity.ravager.roar", sp.position(), 1.2, 0.5);
            }
        }
        JobTraits.sendGauge(sp, false);
        return st.power - before;
    }

    /** 스톨른 썬더: 몸이 amount 만큼 더 커짐 (0 = 끝 — 직업이 바뀌었으면 j 가 null) */
    static void ultGrow(ServerPlayer sp, Jobs.Job j, double amount) {
        if (j == null) {
            State st = STATES.get(sp.getUUID());
            if (st != null) st.ultGrow = 0;
            scaleMod(sp, 0);
            return;
        }
        of(sp).ultGrow = Math.max(0, amount);
        applyScale(sp, j);
    }

    /** 몸 크기 = 1 + 빼앗은 힘 × grow (+ 콜로서스 grow · 스톨른 썬더) — Attributes.SCALE */
    static void applyScale(ServerPlayer sp, Jobs.Job j) {
        State st = STATES.get(sp.getUUID());
        double v = st == null ? 0 : st.power * trait(j, "grow", 0.02) + st.ultGrow;
        if (JobTraits.stanceOn(sp, COLOSSUS)) {
            Jobs.Skill k = j.skill(COLOSSUS);
            v += k == null ? 0.3 : k.num("grow", 0.3);
        }
        scaleMod(sp, v);
    }

    private static void scaleMod(ServerPlayer sp, double amount) {
        AttributeInstance a = sp.getAttribute(Attributes.SCALE);
        if (a == null) return;
        if (amount > 1e-6) a.addOrUpdateTransientModifier(new AttributeModifier(SCALE_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        else a.removeModifier(SCALE_ID);
    }

    /** 시험: 지금 몸 크기 (Attributes.SCALE) */
    public static double scaleOf(ServerPlayer sp) {
        AttributeInstance a = sp.getAttribute(Attributes.SCALE);
        return a == null ? 1 : a.getValue();
    }

    /** 태세가 끝남 (JobTraits.tick): 콜로서스 → 몸 크기를 강탈 몫만 */
    static void stanceEnded(ServerPlayer sp, String id) {
        if (!COLOSSUS.equals(id)) return;
        Jobs.Job j = jobIf(sp, TY);
        if (j != null) applyScale(sp, j);
        else scaleMod(sp, 0);
    }

    /** 게이지: 타이런트 = 빼앗은 힘 */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        return power(sp);
    }

    /** 태세 연출 (JobTraits.stanceFx — 8틱마다): 썬더헤드 먹구름 (JobTraits13.tick 이 그림) · 콜로서스 왕관 */
    static boolean stanceFx(ServerPlayer sp, Jobs.Job j, String stance) {
        return HEAD.equals(stance) || COLOSSUS.equals(stance);
    }

    /**
     * 4틱마다 (JobTraits.tick): 썬더로드 — 썬더헤드 먹구름 (8틱마다 · 머리 위를 따라감 · 속의 번쩍임), 타이런트 — 빼앗은 힘이 keep 틱 동안 늘지 않으면 decay 틱마다 하나씩
     * 사라짐 · 콜로서스 왕관 (8틱마다) · 힘이 많으면 몸에서 오르는 보랏빛
     */
    static void tick(ServerPlayer sp, Jobs.Job j, long now) {
        if (now % 16 == 0 && !ROBBED.isEmpty()) ROBBED.values().removeIf(r -> r[1] <= now);
        if (now % 16 == 0 && !GUARD.isEmpty()) GUARD.values().removeIf(t -> t <= now);
        if (TL.equals(j.id())) {
            if (now % 8 == 0 && JobTraits.stanceOn(sp, HEAD)) {
                ServerLevel level = sp.level();
                Vec3 at = V.up(sp.position(), sp.getBbHeight() + 2.6);
                Opt o = JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp));
                JobFx.cloud(level, at, 1.6, 2, 4, 0.5, true, o.with("alpha", 0.95), CLOUD, GOLD);
                if (now % 16 == 0) JobFx.arc(level, V.add(at, new Vec3((Kit.rand() - 0.5) * 2.0, -0.2, (Kit.rand() - 0.5) * 2.0)), V.add(at,
                        new Vec3((Kit.rand() - 0.5) * 2.4, -0.4, (Kit.rand() - 0.5) * 2.4)), GOLD, GOLD_CORE, 0.15, o);
            }
            return;
        }
        if (!TY.equals(j.id())) return;
        State st = STATES.get(sp.getUUID());
        if (now % 20 == 0) applyScale(sp, j); // 죽음 · 차원 이동 뒤 남은 크기를 맞춤
        if (st != null && st.power > 0) {
            long keep = (long) trait(j, "keep", 160);
            long decay = Math.max(1, (long) trait(j, "decay", 20));
            if (now - st.gainAt >= keep && now - st.decayAt >= decay) {
                st.power--;
                st.decayAt = now;
                applyScale(sp, j);
                JobTraits.sendGauge(sp, false);
            }
        }
        if (now % 8 != 0) return;
        ServerLevel level = sp.level();
        if (JobTraits.stanceOn(sp, COLOSSUS)) {
            // 왕관: 머리 위에 떠 있는 보랏빛 번개 왕관 (몸 크기를 따라 높이)
            Vec3 at = V.up(sp.position(), sp.getBbHeight() + 0.45);
            JobFx.crown(level, at, 0.42 * Math.max(1, scaleOf(sp)), 2, 4, 0.5, true, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), VIOLET, VIOLET_CORE);
        }
        if (st != null && st.power >= 4 && now % 16 == 0) {
            JobFx.smoke(level, V.up(sp.position(), 0.6), 0.6 + st.power * 0.05, VIOLET, CRIMSON, 0.5, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.5,
                    "dir", V.UP, "speed", 1.2));
        }
    }
}
