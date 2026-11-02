package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.motion.MotionSystem;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 타격감 (사용자 "이펙트가 전직업보다 안좋았음 타격감이 없었음 이펙트가 작았음" · "화면 흔들림은 넣지마 멀미나"): 2차 직업 스킬이 맞힌 자리 —
 * <ul>
 * <li>큰 타격 그림: 직업의 맞은 자리 (JobFx.hit — 1차보다 크게) + 타격 섬광 (rfx_jimpact — 흰 심지 · 직업 빛 가시 · 퍼지는 고리) + 튀는 불티</li>
 * <li>멈칫: 쓴 사람의 동작 · 1인칭 손 · 참격 궤적이 맞는 순간 2 ~ 4틱 멈췄다가 멈춘 만큼 조금 빠르게 따라잡음 (MotionSystem.hitStop · ClientMotions.CATCH —
 * 한 번에 여럿을 맞혀도 한 번 · GAP 틱에 한 번)</li>
 * <li>맞은 몸이 움찔 (밀 수 있는 적은 맞은 쪽으로 조금 밀림) · 무게 있는 타격 소리 (센 타격일수록 낮고 겹침)</li>
 * </ul>
 * 화면은 흔들지 않음. power: 1 = 보통 · 1.5 = 센 타격 (끝타 · 처형) · 2 이상 = 궁극기 마지막.
 */
final class JobImpact {
    private JobImpact() {}

    /** rfx_jimpact 장 수 (tools/job_fx_art_adv.py) */
    static final int IMPACT = 8;
    /** 쓴 사람마다 마지막 멈칫 틱 (같은 틱에 여럿을 맞혀도 한 번 · GAP 틱 안에 다시는 멈추지 않음) */
    private static final Map<UUID, Long> LAST_STOP = new HashMap<>();
    /**
     * 멈칫 사이 가장 짧은 틱: 클라이언트는 멈춘 만큼을 뒤에서 빠르게 따라잡음 (ClientMotions.CATCH — 멈춘 길이의 2배 동안). 그 전에 또 멈추면 뒤의 베기가
     * 효과보다 늦어지므로 (여럿을 차례로 맞히는 검풍 · 연타) 한 동작에서 사실상 한 번
     */
    static final int GAP = 8;
    private static final double[] WHITE = {1.0, 1.0, 1.0};

    static void init() {
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> LAST_STOP.clear());
    }

    static void forget(UUID id) {
        LAST_STOP.remove(id);
    }

    /** 맞은 자리 (그림 · 소리 · 움찔 · 멈칫) — e 를 맞힌 직후 (JobKit.strike 의 then) */
    static void hit(ServerPlayer sp, LivingEntity e, Jobs.Job j, double power) {
        if (e == null || !(e.level() instanceof ServerLevel)) return;
        fx(sp, e, j, power);
        double p = Math.max(0.6, power);
        stop(sp, p >= 2 ? 4 : p >= 1.4 ? 3 : 2);
    }

    /**
     * 맞은 자리의 그림 · 소리 · 움찔만 (멈칫 없이) — 0.51.0 빠른 연타의 숫자 줄마다 (글래디에이터 도끼 난타 · 여덟 팔: 피해는 무리마다 한 번이고 도끼질은 줄마다 —
     * 멈칫은 무리의 센 도끼질에만)
     */
    static void fx(ServerPlayer sp, LivingEntity e, Jobs.Job j, double power) {
        if (e == null || !(e.level() instanceof ServerLevel level)) return;
        double p = Math.max(0.6, power);
        Vec3 b = Kit.bodyPoint(e);
        double body = 0.8 + Kit.bodyPad(e) * 0.6;
        if (RuneKit.fxQuota("jobImpact", 10)) {
            // 직업의 맞은 자리를 1차보다 크게 · 타격 섬광 (흰 심지 → 직업 빛 가시 → 고리) · 섬광 한 겹 더 (센 타격)
            JobFx.hit(e, j, 1.15 * p);
            double size = body * (0.95 + 0.45 * p);
            double spin = Kit.rand() * 360;
            JobFx.two(level, "jimpact", IMPACT, b, 0.3 + 0.04 * p, Opt.of("size", size, "spin", spin, "fade", 0.25, "coreAlpha", 1.0), JobFx.outer(j), WHITE);
            if (p >= 1.4) JobFx.two(level, "jimpact", IMPACT, b, 0.42, Opt.of("size", size * 1.45, "spin", spin + 22, "fade", 0.3, "alpha", 0.55, "coreAlpha", 0.45),
                    JobFx.outer(j), JobFx.inner(j));
            SkillFx.sparks(level, b, JobFx.inner(j), (int) Math.round(6 + 5 * p), 4.0 + 1.5 * p);
        }
        // 움찔: 밀 수 있는 적은 쓴 사람 반대쪽으로 조금 (보스 · 허수아비는 그대로)
        if (JobKit.pushable(e) && p >= 1.2) RuneKit.knock(e, V.flat(V.sub(e.position(), sp.position())), 0.06 * p, 0.04);
        // 무게 있는 타격 소리 (센 타격은 낮은 쿵을 겹침)
        if (RuneKit.fxQuota("jobImpactSound", 3)) {
            SkillFx.play(level, "entity.player.attack.strong", b, 0.95 - 0.08 * p, 0.6);
            if (p >= 1.4) SkillFx.play(level, "entity.player.attack.crit", b, 0.8, 0.7);
            if (p >= 2) SkillFx.play(level, "item.mace.smash_ground", b, 0.75, 0.6);
        }
    }

    /** 멈칫만 (그림 없이): ticks 틱 — GAP 틱 안에 다시 오면 버림 */
    static void stop(ServerPlayer sp, int ticks) {
        long now = Tasks.now();
        Long last = LAST_STOP.get(sp.getUUID());
        if (last != null && now - last < GAP) return;
        LAST_STOP.put(sp.getUUID(), now);
        MotionSystem.hitStop(sp, ticks);
    }
}
