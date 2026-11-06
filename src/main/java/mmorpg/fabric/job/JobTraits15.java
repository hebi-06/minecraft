package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Combat;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-06 2차 직업 — 수호기사 2차의 특성 (다시 — 사용자 "레인보우 컨셉이 개짜치고 반전도 이렇다할 멋이 없음" → 고른 것: 푸른 불사조 · 타락한 흑기사. 서버 — jobs.json
 * advance, 피닉스 가드의 반격 게이지는 JobTraits4 의 수호기사 줄기):
 * <ul>
 * <li>피닉스 가드 「반격」: 수호기사와 같이 막은 피해 → 반격 게이지 + 불사조의 재 — 막아 준 피해의 ash 가 푸른 불꽃 깃털이 되어 둘레 ashRadius 칸에서 체력 비율이 가장
 * 낮은 아군 (나 포함) 의 ashFor 틱 보호막으로 (한 사람에 최대 체력 × ashCap 까지 — RpgHealth.blockHook). 이그니션 (태세): 받는 피해 − cut (RpgHealth.sourceReduce) ·
 * 밀려나지 않음 · 맞거나 막을 때마다 (every 틱에 한 번) 둘레 radius 칸 적에게 불꽃이 터짐 (위력 × burst — 반격 게이지는 쓰지 않음) · 불사조의 재 × ash</li>
 * <li>다크 나이트 「탐식」 (1차와 거꾸로 — 막아 지키던 방패가 굶주린 아가리가 되어 적을 먹어 치움): 직업 스킬로 물어뜯을 때마다 적 하나당 굶주림 + bite (방패 평타 + basic ·
 * 최대 100 · keep 틱 동안 물지 못하면 1초에 decay 씩 줄어듦) → 가득이면 다음에 무는 적을 통째로 삼킴 (그 적 최대 체력 × devour (위력 × devourCap 까지) 를 고정 피해로 더함
 * — {@link #devourTake} · 내 최대 체력 × heal 회복 · 굶주림 0). 언홀리 프렌지 (태세): 방패 평타가 물어뜯기 (평타 + 위력 × bite — RpgCombat.addWeaponBonusHook) · 공격
 * 속도 + speed (RpgCombat.attackSpeed) · 굶주림 × hunger. 게이지 = 굶주림</li>
 * </ul>
 */
public final class JobTraits15 {
    private JobTraits15() {}

    static final String PH = "phoenix";
    static final String DK = "darkknight";
    static final String IGNITION = "phoenix_ignition";
    static final String FRENZY = "darkknight_frenzy";
    /** 언홀리 프렌지 평타의 더한 몫 구분 (WeaponBonus.parts) */
    static final String KEY_FRENZY = "job_dk_frenzy";

    /** 피닉스 가드: 푸른 불꽃 · 흰 불 · 짙은 푸른 불 */
    static final double[] AZURE = {0.26, 0.62, 1.0};
    static final double[] AZURE_CORE = {0.9, 0.97, 1.0};
    static final double[] DEEP = {0.12, 0.34, 1.0};
    static final double[] DEEP_CORE = {0.62, 0.86, 1.0};
    /** 다크 나이트: 심연 (검은 빛 — 그림의 빛 칸) · 핏빛 (그림의 심지 칸) · 붉은 빛 · 그 속 */
    static final double[] VOID = {0.16, 0.03, 0.06};
    static final double[] BLOOD = {1.0, 0.22, 0.28};
    static final double[] CRIMSON = {0.9, 0.12, 0.2};
    static final double[] CRIMSON_CORE = {1.0, 0.62, 0.6};

    static final class State {
        /** 피닉스 가드: 이그니션 불꽃이 마지막으로 터진 틱 · 시험 — 불사조의 재가 준 보호막 합 · 아군마다 깃털 연출 틱 */
        long burstAt = -9999;
        double ashGiven;
        final Map<UUID, Long> ashFx = new HashMap<>();
        /** 다크 나이트: 굶주림 (0 ~ 100) · 마지막으로 문 틱 · 마지막으로 줄어든 틱 · 시험 — 삼킨 수 */
        double hunger;
        long lastBite = -9999;
        long lastDecay = -9999;
        int devoured;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    static void init() {
        // 이그니션: 받는 피해 − cut (다른 감소와 큰 쪽)
        java.util.function.ToDoubleBiFunction<ServerPlayer, DamageSource> sr = RpgHealth.sourceReduce;
        RpgHealth.sourceReduce = (p, src) -> Math.max(sr.applyAsDouble(p, src), ignitionCut(p));
        // 막음: 불사조의 재 · 이그니션 불꽃
        RpgHealth.BlockHook bh = RpgHealth.blockHook;
        RpgHealth.blockHook = (p, src, a) -> {
            bh.blocked(p, src, a);
            onBlocked(p, src, a);
        };
        // 받은 피해 (보호막 앞): 이그니션 불꽃
        RpgHealth.TakenHook th = RpgHealth.takenHook;
        RpgHealth.takenHook = (p, src, a) -> {
            th.taken(p, src, a);
            onTaken(p, src, a);
        };
        // 다크 나이트: 방패 평타 (굶주림 · 프렌지 물어뜯기) · 프렌지 공격 속도
        RpgCombat.addWeaponBonusHook(JobTraits15::frenzyBonus);
        RpgCombat.addWeaponHitHook(JobTraits15::onWeaponHit);
        java.util.function.ToDoubleFunction<ServerPlayer> as = RpgCombat.attackSpeed;
        RpgCombat.attackSpeed = p -> as.applyAsDouble(p) * frenzySpeed(p);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATES.clear());
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
    }

    static void forget(UUID id) {
        STATES.remove(id);
    }

    /** 죽음 · 차원 이동: 굶주림이 사라짐 */
    static void lifeEnded(UUID id) {
        State st = STATES.get(id);
        if (st != null) st.hunger = 0;
    }

    /** 영상 · 시험: 남은 굶주림 · 불꽃 틱을 지움 (스킬마다 따로 보이게) */
    public static void testClear(ServerPlayer p) {
        STATES.remove(p.getUUID());
    }

    static Vec3 chest(Entity p) {
        return V.up(p.position(), p.getBbHeight() * 0.62);
    }

    /** 도발 (스킬이 부름): ticks 동안 그 적이 나를 노림 (Aegis.tauntFor — 머리 위 "도발") */
    static void taunt(ServerPlayer sp, LivingEntity e, int ticks) {
        if (e == null || e.isRemoved() || !e.isAlive() || ticks <= 0) return;
        Aegis.tauntFor(sp, e, ticks, true);
    }

    // ------------------------------------------------------------ 피닉스 가드 — 불사조의 재 · 이그니션

    private static double ignitionCut(ServerPlayer p) {
        if (!JobTraits.stanceOn(p, IGNITION)) return 0;
        Jobs.Job j = jobIf(p, PH);
        Jobs.Skill k = j == null ? null : j.skill(IGNITION);
        return k == null ? 0 : k.num("cut", 0.2);
    }

    private static void onBlocked(ServerPlayer p, DamageSource src, double a) {
        if (!(a > 0)) return;
        Jobs.Job j = jobIf(p, PH);
        if (j == null) return;
        ignitionBurst(p, j);
        double give = a * trait(j, "ash", 0.2);
        if (JobTraits.stanceOn(p, IGNITION)) {
            Jobs.Skill k = j.skill(IGNITION);
            give *= k == null ? 2 : k.num("ash", 2);
        }
        double g = give;
        // 다음 틱에 줌: 지금 주면 이 피해를 (보호막 앞에서 부르므로) 방금 생긴 내 보호막이 바로 흡수함
        RuneKit.later(1, () -> {
            if (!p.isRemoved() && p.isAlive()) ashGive(p, j, g);
        });
    }

    private static void onTaken(ServerPlayer p, DamageSource src, double a) {
        if (!(a > 0)) return;
        Jobs.Job j = jobIf(p, PH);
        if (j != null) ignitionBurst(p, j);
    }

    /** 둘레 r 칸에서 체력 비율이 가장 낮은 아군 (나 포함 — 같으면 나) */
    static ServerPlayer lowest(ServerPlayer sp, double r) {
        ServerPlayer best = sp;
        double low = sp.getHealth() / Math.max(1e-3, sp.getMaxHealth());
        for (ServerPlayer o : JobTraits4.allies(sp, sp.position(), r)) {
            if (o == sp || o.isDeadOrDying()) continue;
            double q = o.getHealth() / Math.max(1e-3, o.getMaxHealth());
            if (q < low - 1e-6) {
                low = q;
                best = o;
            }
        }
        return best;
    }

    /**
     * 불사조의 재: give 만큼을 둘레 ashRadius 칸에서 체력 비율이 가장 낮은 아군 (나 포함) 의 ashFor 틱 보호막으로 (최대 체력 × ashCap 까지) — 내 방패에서 그 아군에게 푸른
     * 불꽃 깃털이 날아감 (나면 방패 앞의 작은 날개)
     */
    private static void ashGive(ServerPlayer sp, Jobs.Job j, double give) {
        ServerPlayer o = lowest(sp, trait(j, "ashRadius", 8));
        double got = RpgHealth.addShield(o, give, (int) trait(j, "ashFor", 60), RpgHealth.maxOf(o) * trait(j, "ashCap", 0.15));
        if (!(got > 0.01)) return;
        State st = of(sp);
        st.ashGiven += got;
        long now = Tasks.now();
        Long last = st.ashFx.get(o.getUUID());
        if (last != null && now - last < 6) return;
        st.ashFx.put(o.getUUID(), now);
        ServerLevel level = sp.level();
        RuneKit.Hand h = RuneKit.handOf(sp);
        Vec3 from = V.up(V.add(h.hand(), h.f(), 0.4), 0.25);
        if (o == sp) {
            JobFx15.fwings(level, V.up(sp.position(), 1.35), 1.6, 6, 2, 0.35, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.85), AZURE, AZURE_CORE);
            SkillFx.sparks(level, from, AZURE_CORE, 6, 2.5);
        } else {
            // 방패에서 아군에게 날아가는 불꽃 깃털 (0.3 ~ 0.6초) · 그 길의 옅은 불줄
            Vec3 to = chest(o);
            Vec3 d = V.sub(to, from);
            double len = V.len(d);
            double go = Math.max(0.3, Math.min(0.6, len / 10));
            if (len > 0.3) {
                JobFx.cells(level, "jfeather", JobFx.FEATHER, 0, JobFx.FEATHER, from, go, 12, true, Opt.of("size", 0.38, "vel", V.scale(d, 1 / go), "spin", Kit.rand() * 360,
                        "fade", 0.2), AZURE, AZURE_CORE);
                JobFx.line(level, from, to, AZURE, AZURE_CORE, go + 0.15, Opt.of("alpha", 0.55, "fade", 0.4));
            }
            RuneKit.later(Math.max(1, (int) Math.round(go * 20)), () -> {
                if (o.isRemoved()) return;
                JobFx15.fwings(o.level(), V.up(o.position(), 1.35), 1.6, 6, 2, 0.35, false, Opt.of("vel", RuneKit.bodyVel(o), "alpha", 0.85), AZURE, AZURE_CORE);
                SkillFx.sparks(o.level(), chest(o), AZURE_CORE, 6, 2.5);
            });
        }
        if (RuneKit.fxQuota("jobAsh" + sp.getId(), 1)) SkillFx.play(level, "block.fire.ambient", sp.position(), 1.6, 0.9);
    }

    /** 시험: 불사조의 재가 준 보호막 합 */
    public static double ashGiven(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.ashGiven;
    }

    /** 이그니션: 맞거나 막을 때마다 (every 틱에 한 번) 둘레 radius 칸 적에게 불꽃이 터짐 (위력 × burst — 반격 게이지는 쓰지 않음) */
    private static void ignitionBurst(ServerPlayer sp, Jobs.Job j) {
        if (!JobTraits.stanceOn(sp, IGNITION)) return;
        Jobs.Skill k = j.skill(IGNITION);
        if (k == null) return;
        State st = of(sp);
        long now = Tasks.now();
        if (now - st.burstAt < (long) k.num("every", 10)) return;
        st.burstAt = now;
        double P = JobSystem.power(sp, j);
        double r = k.num("radius", 3);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 g = ThunderlordSkills.ground(level, c);
        // 날개에서 터지는 푸른 불: 발밑 고리 · 둘레 불길 · 날개 깃털
        RuneKit.novaRing(level, c, 0.6, r / 0.6 - 1, AZURE, AZURE_CORE, Opt.of("life", 0.35));
        JobFx15.halo(level, V.up(g, 0.06), r, 0.4, Opt.of("alpha", 0.85), AZURE, AZURE_CORE);
        JobFx.burst(level, V.up(c, 1.1), 1.6, AZURE, AZURE_CORE, 0.28, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + Kit.rand() * 0.4;
            Vec3 p = V.add(g, new Vec3(Math.cos(a), 0, Math.sin(a)), r * 0.7);
            JobFx15.cinder(level, p, 1.5, 0, 6, 0.45, false, Opt.of("alpha", 0.85), AZURE, AZURE_CORE);
        }
        SkillFx.play(level, "item.firecharge.use", c, 1.3, 0.6);
        for (LivingEntity e : Kit.around(sp, c, r, 3)) {
            if (!e.isAlive() || e.isRemoved()) continue;
            quietHit(sp, e, j, P, k.num("burst", 0.6));
            if (e.level() instanceof ServerLevel el) JobFx.burst(el, Kit.bodyPoint(e), 0.8 + Kit.bodyPad(e) * 0.3, AZURE, AZURE_CORE, 0.25, Opt.of("vel",
                    RuneKit.entVel(e)));
        }
    }

    /** 반격 게이지를 쓰지 않는 직업 피해 한 번 (이그니션 불꽃 — 지금 바로 · 무적 시간이면 버림) */
    static boolean quiet;

    private static void quietHit(ServerPlayer sp, LivingEntity e, Jobs.Job j, double P, double mult) {
        quiet = true;
        try {
            JobKit.hit(sp, e, j, P, mult, true, false);
        } finally {
            quiet = false;
        }
    }

    // ------------------------------------------------------------ 다크 나이트 — 탐식

    /** 굶주림 (시험) */
    public static double hunger(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.hunger;
    }

    /** 시험용: 굶주림을 정함 */
    public static void setHunger(ServerPlayer sp, double v) {
        State st = of(sp);
        st.hunger = Math.max(0, Math.min(100, v));
        st.lastBite = Tasks.now();
        JobTraits.sendGauge(sp, false);
    }

    /** 시험: 통째로 삼킨 수 */
    public static int devoured(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.devoured;
    }

    private static double hungerMax(Jobs.Job j) {
        return j == null || j.trait() == null || j.trait().max() <= 0 ? 100 : j.trait().max();
    }

    /** 물어뜯음 (직업 스킬의 물기가 들어감 — 적 하나): 굶주림 + bite (프렌지면 × hunger) — 가득 차면 눈이 붉게 · 으르렁 */
    static void bite(ServerPlayer sp, Jobs.Job j, double amount) {
        if (j == null || !DK.equals(j.id()) || !(amount > 0)) return;
        State st = of(sp);
        double before = st.hunger;
        double mult = 1;
        if (JobTraits.stanceOn(sp, FRENZY)) {
            Jobs.Skill k = j.skill(FRENZY);
            mult = k == null ? 2 : k.num("hunger", 2);
        }
        double max = hungerMax(j);
        st.hunger = Math.min(max, st.hunger + amount * mult);
        st.lastBite = Tasks.now();
        if (before < max && st.hunger >= max - 1e-6) {
            ServerLevel level = sp.level();
            RuneKit.Hand h = RuneKit.handOf(sp);
            JobFx15.dmaw(level, V.up(V.add(h.hand(), h.f(), 0.45), 0.2), 0.42, 5, 3, 0.45, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), VOID, BLOOD);
            JobFx.eyeRing(sp, j, 2.4, 0.9, 0.4);
            SkillFx.play(level, "entity.wolf.growl", sp.position(), 0.5, 1.0);
            SkillFx.play(level, "entity.warden.heartbeat", sp.position(), 1.0, 1.0);
            JobSystem.note(sp, "§4탐식 §7— 굶주림이 가득 참 (다음에 무는 적을 통째로 삼킴)");
        }
        JobTraits.sendGauge(sp, false);
    }

    /** 스킬의 물기 한 번 (그 적 e — 피해가 들어간 뒤): 굶주림 + trait bite */
    static void bitSkill(ServerPlayer sp, Jobs.Job j) {
        bite(sp, j, trait(j, "bite", 12));
    }

    /**
     * 통째로 삼킴 (스킬의 물기가 나가기 전): 굶주림이 가득이면 굶주림 0 · 그 적 e 에 더할 고정 피해 (최대 체력 × devour — 위력 × devourCap 까지) — 아니면 0. 들어가면
     * {@link #devoured} (회복 · 그림)
     */
    static double devourTake(ServerPlayer sp, Jobs.Job j, LivingEntity e, double P) {
        if (j == null || !DK.equals(j.id()) || e == null) return 0;
        State st = STATES.get(sp.getUUID());
        if (st == null || st.hunger < hungerMax(j) - 1e-6) return 0;
        st.hunger = 0;
        JobTraits.sendGauge(sp, false);
        return Math.min(e.getMaxHealth() * trait(j, "devour", 0.08), P * trait(j, "devourCap", 6));
    }

    /** 삼킴이 들어감: 내 최대 체력 × heal 회복 · 그 적을 덮치는 큰 아가리 · 핏빛 · 글자 */
    static void devoured(ServerPlayer sp, Jobs.Job j, LivingEntity e, double flat) {
        State st = of(sp);
        st.devoured++;
        double healed = JobTraits4.heal(sp, RpgHealth.maxOf(sp) * trait(j, "heal", 0.06));
        if (e.level() instanceof ServerLevel el) {
            Vec3 b = Kit.bodyPoint(e);
            double pad = Kit.bodyPad(e);
            JobFx15.dmaw(el, V.add(b, V.flat(V.sub(sp.position(), e.position())), 0.25), 1.25 + pad * 0.6, 2, 4, 0.5, false, Opt.of("vel", RuneKit.entVel(e), "alpha",
                    1.0), VOID, BLOOD);
            JobFx.burst(el, b, 1.6 + pad * 0.5, CRIMSON, CRIMSON_CORE, 0.35, Opt.empty());
            SkillFx.sparks(el, b, BLOOD, 16, 5);
            SkillFx.play(el, "entity.ravager.attack", b, 0.6, 1.0);
            SkillFx.play(el, "entity.generic.eat", b, 0.5, 1.0);
        }
        ServerLevel level = sp.level();
        JobFx.burst(level, chest(sp), 1.0, CRIMSON, CRIMSON_CORE, 0.3, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
        SkillFx.sparks(level, chest(sp), CRIMSON_CORE, 8, 2.5);
        JobSystem.note(sp, "§4탐식 §7— 통째로 삼킴 +" + Math.round(flat) + (healed >= 0.5 ? " · 회복 " + Math.round(healed) : ""));
    }

    /** 언홀리 프렌지: 방패 평타가 물어뜯기 (평타 + 위력 × bite) */
    private static RpgCombat.Bonus frenzyBonus(ServerPlayer sp, LivingEntity victim, Weapon w) {
        if (w == null || victim == null || !"shield".equals(w.type()) || !JobTraits.stanceOn(sp, FRENZY)) return null;
        Jobs.Job j = jobIf(sp, DK);
        Jobs.Skill k = j == null ? null : j.skill(FRENZY);
        if (k == null) return null;
        double add = JobSystem.power(sp, j) * k.num("bite", 0.5) * mmorpg.fabric.stattree.StatTrees.atkMult(sp);
        return new RpgCombat.Bonus(add, "", KEY_FRENZY, DamageNumbers.Style.JOB, null, JobKit.grad(j));
    }

    private static double frenzySpeed(ServerPlayer p) {
        if (!JobTraits.stanceOn(p, FRENZY)) return 1;
        Jobs.Job j = jobIf(p, DK);
        Jobs.Skill k = j == null ? null : j.skill(FRENZY);
        return k == null ? 1 : 1 + k.num("speed", 0.2);
    }

    /** 무기로 맞힘: 다크 나이트 방패 평타 — 굶주림 + basic (프렌지면 물어뜯는 작은 아가리 · 핏빛) */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        if (w == null || victim == null || !"shield".equals(w.type()) || !Combat.isMeleeWeapon(w)) return;
        Jobs.Job j = jobIf(sp, DK);
        if (j == null) return;
        bite(sp, j, trait(j, "basic", 4));
        boolean frenzy = false;
        if (bonus != null) for (RpgCombat.Bonus b : bonus.parts()) if (KEY_FRENZY.equals(b.key())) frenzy = true;
        if (!frenzy || !(victim.level() instanceof ServerLevel el)) return;
        Vec3 b = Kit.bodyPoint(victim);
        double pad = Kit.bodyPad(victim);
        JobFx15.dmaw(el, V.add(b, V.flat(V.sub(sp.position(), victim.position())), 0.2), 0.6 + pad * 0.3, 2, 3, 0.3, false, Opt.of("vel", RuneKit.entVel(victim)), VOID,
                BLOOD);
        SkillFx.sparks(el, b, BLOOD, 6, 3);
        if (RuneKit.fxQuota("jobFrenzy" + sp.getId(), 1)) SkillFx.play(el, "entity.wolf.growl", b, 1.4, 0.45);
    }

    // ------------------------------------------------------------ 틱 · 태세 연출 · 게이지

    /** 사람마다 (JobTraits 가 4틱마다): 굶주림이 줄어듦 · 가득 찬 방패의 핏빛 숨 */
    static void tick(ServerPlayer sp, Jobs.Job j, long now) {
        if (!j.id().equals(DK)) return;
        State st = STATES.get(sp.getUUID());
        if (st == null) return;
        int keep = (int) trait(j, "keep", 160);
        if (st.hunger > 0 && now - st.lastBite >= keep && now - st.lastDecay >= 20) {
            st.hunger = Math.max(0, st.hunger - trait(j, "decay", 10));
            st.lastDecay = now;
            JobTraits.sendGauge(sp, false);
        }
        if (st.hunger >= hungerMax(j) - 1e-6 && now % 12 == 0 && !sp.isSpectator()) {
            // 가득 찬 굶주림: 방패의 아가리에서 새는 핏빛 숨 · 붉은 불티
            ServerLevel level = sp.level();
            RuneKit.Hand h = RuneKit.handOf(sp);
            Vec3 at = V.up(V.add(h.hand(), h.f(), 0.4), 0.2);
            JobFx.smoke(level, at, 0.35, VOID, BLOOD, 0.6, JobFx.hide1p(sp).with("dir", V.UP, "speed", 0.6, "alpha", 0.6));
            SkillFx.fx(level, "rpg:arrow_spark", at, Opt.of("dir", V.UP, "speed", 1.2, "size", 0.12, "grow", 0.8, "life", 0.4, "color", BLOOD, "alpha", 0.95));
        }
    }

    /** 태세 연출 (JobTraits.stanceFx — 8틱마다): 이그니션 (등의 푸른 불꽃 날개) · 언홀리 프렌지 (방패의 아가리 · 붉은 눈 · 핏빛 숨) */
    static boolean stanceFx(ServerPlayer sp, Jobs.Job j, String stance) {
        ServerLevel level = sp.level();
        if (stance.equals(IGNITION)) {
            Vec3 back = V.up(V.add(sp.position(), Kit.facingOf(sp), -0.3), 1.35);
            // (날갯짓은 다시 그려도 이어지게 — phase = 지금 틱의 장)
            JobFx15.fwings(level, back, 3.6, 2, 4, 0.5, true, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "solo", 92.0, "fade", 0.05, "fps", 10.0, "phase",
                    (Tasks.now() * 10 / 20.0) % 4), AZURE, AZURE_CORE);
            SkillFx.fx(level, "rpg:arrow_spark", V.up(back, -0.4 + Kit.rand() * 0.8), Opt.of("dir", V.UP, "speed", 1.4, "size", 0.14, "grow", 0.8, "life", 0.45, "color",
                    AZURE, "alpha", 0.95));
            return true;
        }
        if (stance.equals(FRENZY)) {
            RuneKit.Hand h = RuneKit.handOf(sp);
            Vec3 at = V.up(V.add(h.hand(), h.f(), 0.45), 0.2);
            JobFx15.dmaw(level, at, 0.4, 1, 2, 0.5, true, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "solo", 93.0, "fade", 0.05, "fps", 6.0, "phase", (Tasks
                    .now() * 6 / 20.0) % 2), VOID, BLOOD);
            JobFx.smoke(level, V.up(sp.position(), 1.0), 0.7, VOID, BLOOD, 0.7, JobFx.hide1p(sp).with("dir", V.UP, "speed", 0.5, "alpha", 0.45));
            SkillFx.fx(level, "rpg:arrow_spark", V.up(sp.position(), 0.4 + Kit.rand() * 1.2), Opt.of("dir", V.UP, "speed", 1.2, "size", 0.13, "grow", 0.8, "life", 0.4,
                    "color", BLOOD, "alpha", 0.95));
            return true;
        }
        return false;
    }

    /** 태세가 끝남 (JobTraits): 이그니션 → 날개가 불꽃 깃털로 흩어짐, 언홀리 프렌지 → 아가리가 다물며 연기 */
    static void stanceEnded(ServerPlayer sp, String id) {
        ServerLevel level = sp.level();
        if (id.equals(IGNITION)) {
            JobFx15.fwings(level, V.up(V.add(sp.position(), Kit.facingOf(sp), -0.3), 1.35), 3.6, 7, 1, 0.5, false, JobFx.hide1p(sp).with("solo", 92.0), AZURE, AZURE_CORE);
            SkillFx.play(level, "block.fire.extinguish", sp.position(), 1.2, 0.5);
        } else if (id.equals(FRENZY)) {
            RuneKit.Hand h = RuneKit.handOf(sp);
            JobFx15.dmaw(level, V.up(V.add(h.hand(), h.f(), 0.45), 0.2), 0.4, 7, 1, 0.4, false, JobFx.hide1p(sp).with("solo", 93.0), VOID, BLOOD);
            SkillFx.play(level, "entity.wolf.whine", sp.position(), 0.6, 0.5);
        }
    }

    /** 게이지 값: 다크 나이트 굶주림 (0 ~ 100) */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        State st = STATES.get(sp.getUUID());
        if (st == null) return 0;
        return (int) Math.round(Math.max(0, Math.min(hungerMax(j), st.hunger)));
    }

    /** 이 사람이 다크 나이트이며 굶주림이 가득인지 (스킬 연출) */
    static boolean full(ServerPlayer sp) {
        Jobs.Job j = jobIf(sp, DK);
        State st = STATES.get(sp.getUUID());
        return j != null && st != null && st.hunger >= hungerMax(j) - 1e-6;
    }
}
