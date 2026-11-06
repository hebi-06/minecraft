package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 2차 직업 — 창술사 2차 (재설계안 2판 — 2026-11-06 사용자 "다음 작업") 의 특성 (서버 — jobs.json advance, 스톰 랜서의 창끝 (끝자락 +15% · 밀쳐냄 · 기세) 은
 * JobTraits3 의 창술사 줄기):
 * <ul>
 * <li>스톰 랜서 「창끝」 + 바람: 창 평타가 창끝 (끝자락) 에 맞을 때마다 바람 +1 (최대 max · windFor 틱 못 쌓으면 사라짐 — {@link #lanceHit}) → 바람이 가득일 때
 * 창 평타를 맞히면 질풍 창 ({@link #gust} — 앞 gust 칸 · 폭 gustWidth 일직선의 적 모두 위력 × gustHit, 평타에 맞은 적은 gustDelay 틱 뒤) · 바람을 다 씀.
 * 바람이 가득이면 창끝에 바람 소용돌이 (8틱마다)</li>
 * <li>스톰 랜서 렌딩 게일: 여는 동안 창 평타가 맞을 때마다 그 적에 바람 창 하나 (적마다 spears 까지 — {@link #embed}) · 박힌 창 그림 (8틱마다) → 뽑음
 * ({@link #takeSpears})</li>
 * <li>드라군 「비상」 (1차 창끝과 거꾸로 — 거리 대신 높이): 직업 스킬로 떠 있는 동안 ({@link #airFor}) 받는 피해 − air · 드래곤폴로 하늘에 있는 동안 받는 피해 없음
 * ({@link #immuneFor}) · 내리꽂기 높이 덤 ({@link #heightMult} — 떨어진 높이 1칸당 + perBlock, maxBlocks 칸까지) · 게이지 = 발밑 땅에서의 높이</li>
 * </ul>
 */
public final class JobTraits11 {
    private JobTraits11() {}

    static final String SL = "stormlancer";
    static final String DG = "dragoon";
    static final String REND = "stormlancer_rend";

    static final class State {
        /** 스톰 랜서 바람 · 마지막으로 쌓은 틱 */
        int wind;
        long windAt;
        /** 드라군: 직업 스킬로 떠 있는 동안 (받는 피해 − air) · 드래곤폴로 하늘에 있는 동안 (받는 피해 없음) */
        long airUntil;
        long immuneUntil;
        /** 렌딩 게일이 열린 동안 · 연 틱 */
        long rendUntil;
        long rendAt;
        /** 렌딩 게일: 적 → 박힌 창 수 */
        final Map<UUID, Integer> spears = new HashMap<>();
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    static void init() {
        java.util.function.ToDoubleBiFunction<ServerPlayer, DamageSource> sr = RpgHealth.sourceReduce;
        RpgHealth.sourceReduce = (p, src) -> Math.max(sr.applyAsDouble(p, src), airGuard(p));
        RpgCombat.addWeaponHitHook(JobTraits11::onWeaponHit);
        ServerTickEvents.END_SERVER_TICK.register(JobTraits11::tickGlobal);
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

    static void lifeEnded(UUID id) {
        State st = STATES.get(id);
        if (st == null) return;
        st.wind = 0;
        st.airUntil = 0;
        st.immuneUntil = 0;
        st.rendUntil = 0;
        st.spears.clear();
    }

    // ------------------------------------------------------------ 스톰 랜서 바람 · 질풍 창

    /** 지금 바람 (시험 · 게이지) */
    public static int wind(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.wind;
    }

    /** 시험: 바람을 정함 */
    public static void setWind(ServerPlayer sp, int n) {
        State st = of(sp);
        st.wind = Math.max(0, n);
        st.windAt = Tasks.now();
        JobTraits.sendGauge(sp, false);
    }

    /**
     * 창 평타가 맞음 (JobTraits3 의 창술사 줄기 — 스톰 랜서만): 바람이 가득이면 질풍 창 (바람을 다 씀), 아니면 창끝 (edge) 에 맞았을 때 바람 +1 (창끝에 감기는 바람 ·
     * 가득 차면 소용돌이 · 맑은 소리)
     */
    static void lanceHit(ServerPlayer sp, LivingEntity victim, Jobs.Job j, boolean edge) {
        if (j == null || !SL.equals(j.id())) return;
        State st = of(sp);
        long now = Tasks.now();
        int max = Math.max(1, j.trait().max());
        if (st.wind > 0 && now - st.windAt > (long) trait(j, "windFor", 120)) st.wind = 0;
        ServerLevel level = sp.level();
        if (st.wind >= max) {
            st.wind = 0;
            gust(sp, victim, j);
        } else if (edge) {
            st.wind++;
            st.windAt = now;
            RuneKit.Hand h = RuneKit.handOf(sp);
            Vec3 tip = V.add(h.hand(), sp.getViewVector(1F), 1.2);
            JobFx.swirl(level, tip, 0.3 + 0.08 * st.wind, 0.35, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.85), JobKit.glow(j), JobKit.core(j));
            SkillFx.play(level, "item.trident.riptide_1", sp.position(), 1.6 + 0.1 * st.wind, 0.35);
            if (st.wind >= max) {
                JobFx.swirl(level, V.up(sp.position(), 1.2), 1.1, 0.5, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.7), JobKit.glow(j), JobKit.core(j));
                SkillFx.play(level, "block.amethyst_block.chime", sp.position(), 1.4, 0.6);
            }
        }
        JobTraits.sendGauge(sp, false);
    }

    /**
     * 질풍 창: 창끝에서 바람 창이 뻗어 평타에 맞은 적 쪽 (수평) 으로 gust 칸 · 폭 gustWidth — 줄의 적 모두 위력 × gustHit (평타에 맞은 적은 gustDelay 틱 뒤 — 무적
     * 시간). 그림: 날아가는 바람 소용돌이 · 일섬 · 바람 줄 · 맞은 자리 번쩍
     */
    static void gust(ServerPlayer sp, LivingEntity victim, Jobs.Job j) {
        ServerLevel level = sp.level();
        Vec3 from = V.up(sp.position(), 1.15);
        double dd = V.hdist(victim.position(), sp.position());
        Vec3 d = dd < 0.1 ? Kit.facingOf(sp) : V.flat(V.sub(victim.position(), sp.position()));
        double len = trait(j, "gust", 8);
        double half = trait(j, "gustWidth", 1.4) / 2;
        double P = JobSystem.power(sp, j);
        double mult = trait(j, "gustHit", 0.6);
        int delay = (int) trait(j, "gustDelay", 10);
        String life = RuneKit.lifeToken(sp);
        double can = Math.max(1.5, RuneKit.rayLength(level, from, d, len));
        Vec3 start = V.add(from, d, 0.8);
        Vec3 end = V.add(from, d, can);
        StormlancerSkills.lance(level, sp, j, start, end, 1.0, 50, 0.32);
        SkillFx.play(level, "item.trident.riptide_2", sp.position(), 1.35, 0.7);
        SkillFx.play(level, "entity.breeze.shoot", sp.position(), 1.2, 0.6);
        int n = 0;
        for (Kit.LineHit lh : Kit.lineHits(sp, from, d, can, half)) {
            LivingEntity e = lh.e();
            if (n++ >= 8) break;
            int at = (int) Math.round(lh.t() / 50.0 * 20);
            JobKit.strike(sp, e, j, P, mult, false, false, life, e == victim ? Math.max(delay, at) : at, x -> {
                if (!(e.level() instanceof ServerLevel lv)) return;
                if (RuneKit.fxQuota("jobGustHit", 6)) {
                    JobFx.hit(e, j, 0.8);
                    JobFx.swirl(lv, Kit.bodyToward(e, sp.getEyePosition(), d), 0.55 + Kit.bodyPad(e) * 0.3, 0.3, Opt.of("vel", RuneKit.entVel(e)), JobKit.glow(j),
                            JobKit.core(j));
                }
            });
        }
    }

    // ------------------------------------------------------------ 스톰 랜서 렌딩 게일

    /** 렌딩 게일을 엶 (window 틱 — 그동안 창 평타가 바람 창을 박음) */
    static void rendOpen(ServerPlayer sp, int window) {
        State st = of(sp);
        st.rendUntil = Tasks.now() + window;
        st.rendAt = Tasks.now();
        st.spears.clear();
    }

    /** 렌딩 게일이 열려 있는지 */
    public static boolean rendOn(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st != null && st.rendUntil > Tasks.now();
    }

    /** 그 적에 박힌 바람 창 (시험) */
    public static int spears(ServerPlayer sp, Entity e) {
        State st = STATES.get(sp.getUUID());
        return st == null || e == null ? 0 : st.spears.getOrDefault(e.getUUID(), 0);
    }

    /** 바람 창 n 개를 박음 (적마다 spears 까지) — 박힌 수 · 꽂히는 번쩍 */
    static int embed(ServerPlayer sp, LivingEntity e, Jobs.Job j, Jobs.Skill k, int n) {
        State st = of(sp);
        int max = (int) k.num("spears", 8);
        int was = st.spears.getOrDefault(e.getUUID(), 0);
        int now = Math.min(max, was + n);
        st.spears.put(e.getUUID(), now);
        if (now > was && e.level() instanceof ServerLevel level) {
            Vec3 b = Kit.bodyPoint(e);
            JobFx.stab(level, Kit.bodyToward(e, sp.getEyePosition(), sp.getViewVector(1F)), 0.45 + Kit.bodyPad(e) * 0.25, j, 0.2, Opt.of("vel", RuneKit.entVel(e)));
            for (int i = was; i < now; i++) stuck(level, e, i, j, 0.45);
            SkillFx.play(level, "item.trident.hit", b, 1.5 + 0.05 * now, 0.5);
        }
        return now;
    }

    /**
     * 박힌 바람 창 하나 (i 번째 — 몸 둘레 고르게 · 비스듬히 꽂힘). 2026-11-06 영상 1: 0.42 크기 · 몸 속 깊이 꽂힌 창이 과녁 몸에 가려 가는 줄로만 보였음 → 0.8 · 창끝만
     * 몸에 묻히고 자루가 바깥으로 길게 · 위쪽으로 더 기울임 (3인칭 카메라에서 몸 위로 솟아 보이게)
     */
    private static void stuck(ServerLevel level, LivingEntity e, int i, Jobs.Job j, double life) {
        double a = Math.toRadians(i * 137.5 + 20);
        double pad = Kit.bodyPad(e);
        Vec3 b = Kit.bodyPoint(e);
        Vec3 out = new Vec3(Math.cos(a), 0.55 + 0.2 * ((i % 3) - 1), Math.sin(a));
        Vec3 tip = V.add(V.up(b, ((i % 4) - 1.5) * 0.2 * (1 + pad)), V.unit(out), 0.1 + pad * 0.35);
        // 창끝이 몸 속 (tip) · 자루가 바깥으로 비스듬히
        JobFx.spear(level, tip, V.scale(V.unit(out), -1), 0.8 + pad * 0.18, 0, life, j, Opt.of("vel", RuneKit.entVel(e), "fade", 0.2, "alpha", 1.0));
    }

    /** 박힌 바람 창을 모두 거둠 (렌딩 게일을 닫음): 적 → 수 */
    static Map<LivingEntity, Integer> takeSpears(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        Map<LivingEntity, Integer> out = new java.util.LinkedHashMap<>();
        if (st == null) return out;
        st.rendUntil = 0;
        for (Map.Entry<UUID, Integer> en : st.spears.entrySet()) {
            Entity e = sp.level().getEntity(en.getKey());
            if (e instanceof LivingEntity le && le.isAlive() && !le.isRemoved() && le.distanceTo(sp) <= 32 && en.getValue() > 0) out.put(le, en.getValue());
        }
        st.spears.clear();
        return out;
    }

    /** 렌딩 게일의 남은 시간 (태세 줄 — {이름, 남은 틱, 전체 틱} · 없으면 null) */
    static Object[] stanceLine(ServerPlayer sp, Jobs.Job j) {
        if (j == null || !SL.equals(j.id())) return null;
        State st = STATES.get(sp.getUUID());
        long now = Tasks.now();
        if (st == null || st.rendUntil <= now) return null;
        Jobs.Skill k = j.skill(REND);
        return new Object[] {k == null ? "렌딩 게일" : k.name(), (int) (st.rendUntil - now), (int) Math.max(1, st.rendUntil - st.rendAt)};
    }

    /** 창 평타가 맞음 (HitHook): 렌딩 게일이 열려 있으면 그 적에 바람 창 하나 */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        if (w == null || victim == null || !"lance".equals(w.type())) return;
        Jobs.Job j = jobIf(sp, SL);
        if (j == null || !rendOn(sp)) return;
        Jobs.Skill k = j.skill(REND);
        if (k != null) embed(sp, victim, j, k, 1);
    }

    /** 8틱마다: 렌딩 게일로 박힌 바람 창 그림 (적을 따라감) */
    private static void tickGlobal(MinecraftServer server) {
        long now = Tasks.now();
        if (now % 8 != 0 || STATES.isEmpty()) return;
        for (Map.Entry<UUID, State> en : STATES.entrySet()) {
            State st = en.getValue();
            if (st.spears.isEmpty() || st.rendUntil <= now) continue;
            ServerPlayer sp = server.getPlayerList().getPlayer(en.getKey());
            Jobs.Job j = sp == null ? null : jobIf(sp, SL);
            if (j == null) continue;
            List<UUID> gone = new ArrayList<>();
            for (Map.Entry<UUID, Integer> s : st.spears.entrySet()) {
                Entity e = sp.level().getEntity(s.getKey());
                if (!(e instanceof LivingEntity le) || !le.isAlive() || le.isRemoved()) {
                    gone.add(s.getKey());
                    continue;
                }
                for (int i = 0; i < s.getValue(); i++) stuck(sp.level(), le, i, j, 0.5);
                // 박힌 수만큼 짙어지는 감긴 바람 (몸 둘레 — 칼리스타 E 의 창 꽂힌 표시처럼 멀리서도 보이게)
                JobFx.swirlLoop(sp.level(), Kit.bodyPoint(le), 0.75 + 0.1 * s.getValue() + Kit.bodyPad(le) * 0.4, 0.45, Opt.of("vel", RuneKit.entVel(le), "alpha",
                        Math.min(0.85, 0.35 + 0.07 * s.getValue())), JobKit.glow(j), JobKit.core(j));
            }
            gone.forEach(st.spears::remove);
        }
    }

    // ------------------------------------------------------------ 드라군 비상

    /** 직업 스킬로 떠 있음: ticks 틱 동안 받는 피해 − air (시험도 씀) */
    public static void airFor(ServerPlayer sp, int ticks) {
        State st = of(sp);
        st.airUntil = Math.max(st.airUntil, Tasks.now() + ticks);
    }

    /** 드래곤폴: ticks 틱 동안 받는 피해 없음 (하늘에 있음 — 시험도 씀) */
    public static void immuneFor(ServerPlayer sp, int ticks) {
        State st = of(sp);
        st.immuneUntil = Math.max(st.immuneUntil, Tasks.now() + ticks);
    }

    /** 시험: 떠 있는지 · 받는 피해 없는지 */
    public static boolean airborne(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st != null && st.airUntil > Tasks.now();
    }

    public static boolean immune(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st != null && st.immuneUntil > Tasks.now();
    }

    /** RpgHealth.sourceReduce: 드라군 — 하늘 (드래곤폴) 1 · 떠 있음 air · 아니면 0 */
    private static double airGuard(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        if (st == null) return 0;
        long now = Tasks.now();
        if (st.immuneUntil <= now && st.airUntil <= now) return 0;
        Jobs.Job j = jobIf(sp, DG);
        if (j == null) return 0;
        if (st.immuneUntil > now) return 1;
        return trait(j, "air", 0.3);
    }

    /** 내리꽂기 높이 덤: 떨어진 높이 fallen 칸 → 1 + min(fallen, maxBlocks) × perBlock (드라군이 아니면 1) */
    public static double heightMult(Jobs.Job j, double fallen) {
        if (j == null || !DG.equals(j.id())) return 1;
        double blocks = Math.max(0, Math.min(trait(j, "maxBlocks", 6), fallen));
        return 1 + blocks * trait(j, "perBlock", 0.05);
    }

    /** 마지막 내리꽂기의 높이 덤 (주인 → 배율 — 시험이 기대 피해를 셈) */
    private static final Map<UUID, Double> LAST_HEIGHT = new HashMap<>();

    /** 같은 것 — 이 사람의 마지막 내리꽂기로 적어 둠 */
    static double heightMult(ServerPlayer sp, Jobs.Job j, double fallen) {
        double m = heightMult(j, fallen);
        LAST_HEIGHT.put(sp.getUUID(), m);
        if (LAST_HEIGHT.size() > 64) LAST_HEIGHT.clear();
        return m;
    }

    /** 시험: 이 사람의 마지막 내리꽂기 높이 덤 (없으면 1) */
    public static double lastHeightMult(ServerPlayer sp) {
        return LAST_HEIGHT.getOrDefault(sp.getUUID(), 1.0);
    }

    // ------------------------------------------------------------ 게이지 · 틱

    /** 게이지: 스톰 랜서 = 바람 · 드라군 = 발밑 땅에서의 높이 (칸 — maxBlocks 까지) */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        if (SL.equals(j.id())) return wind(sp);
        if (DG.equals(j.id())) {
            if (sp.onGround()) return 0;
            return (int) Math.min(trait(j, "maxBlocks", 6), Math.floor(JobMove.airborne(sp)));
        }
        return 0;
    }

    /** 시험: 지금 게이지 값 (직업이 없으면 0) */
    public static int gaugeOf(ServerPlayer sp) {
        Jobs.Job j = JobSystem.job(sp);
        return j == null ? 0 : gaugeValue(sp, j);
    }

    /** 4틱마다 (JobTraits.tick): 스톰 랜서 바람이 사라짐 · 가득이면 창끝의 바람 소용돌이 (8틱마다) */
    static void tick(ServerPlayer sp, Jobs.Job j, long now) {
        if (!SL.equals(j.id())) return;
        State st = STATES.get(sp.getUUID());
        if (st == null || st.wind <= 0) return;
        if (now - st.windAt > (long) trait(j, "windFor", 120)) {
            st.wind = 0;
            JobTraits.sendGauge(sp, false);
            return;
        }
        if (st.wind >= Math.max(1, j.trait().max()) && now % 8 == 0 && !sp.isSpectator()) {
            RuneKit.Hand h = RuneKit.handOf(sp);
            Vec3 tip = V.add(h.hand(), sp.getViewVector(1F), 1.3);
            JobFx.swirl(sp.level(), tip, 0.42, 0.42, Opt.of("vel", RuneKit.bodyVel(sp), "alpha", 0.75), JobKit.glow(j), JobKit.core(j));
        }
    }
}
