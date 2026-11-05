package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 2차 직업 — 암살자 2차 (재설계안 2판 — 2026-11-06 사용자 "다음 작업") 의 특성 · 태세 (서버 — jobs.json advance, 트릭스터의 표식 · 등 뒤 치명은 JobTraits3
 * 의 암살자 줄기):
 * <ul>
 * <li>트릭스터 미러 팰리스 (태세): 몸 둘레를 거울 여섯이 돎 (2틱마다 — 바깥 · 안쪽 두 장) · 단검 평타는 어느 쪽에서 쳐도 등 뒤 ({@link #anySide} — JobTraits3
 * 치명타) · 거울에 비친 내 모습이 그 적의 등 뒤에서 한 번 더 벰 (평타 × reflect — RpgCombat.addScaledBonusHook, reflectDelay 틱 뒤 그림) · 표식 +1 더</li>
 * <li>트릭스터 와일드파이어 피스트: 들불 표식이 남은 동안 그 적에게 준 다른 피해 (평타 · 다른 스킬 · 룬 — 궁극기 자신의 타격은 빼고) 를 쌓음 (AFTER_DAMAGE)</li>
 * <li>피에로 「쇼타임」 (1차 표식과 거꾸로 — 숨어서 노리던 눈길 대신 모두의 눈길을 끌어모음): 소환물이 둘레 몬스터를 끌어들여 대신 맞음 (PierrotSummons) · 받는 피해의
 * share 를 가장 가까운 소환물이 나눠 받음 (RpgHealth.lossShare) · 부서지면 색종이 폭죽. 게이지 = 소환물 수, 할루시네이트의 남은 시간은 태세 줄에. 핸드스프링: guardFor
 * 틱 받는 피해 − guard (RpgHealth.sourceReduce). 몸에서 가장 가까운 소환물로 금빛 실 (8틱마다 — 나눠 받는 줄)</li>
 * </ul>
 */
public final class JobTraits10 {
    private JobTraits10() {}

    static final String TR = "trickster";
    static final String PALACE = "trickster_palace";
    /** 평타에 더한 몫의 구분 (미러 팰리스의 거울 베기) */
    static final String KEY_PALACE = "job_palace";
    static final String PR = "pierrot";
    static final String DOLL = "pierrot_doll";

    static final class State {
        /** 핸드스프링: 받는 피해 − guard · 끝나는 틱 */
        long guardUntil;
        double guard;
    }

    /** 들불 표식 하나: 건 사람 · 끝나는 틱 · 쌓인 피해 */
    static final class Fire {
        final UUID owner;
        final long until;
        double stored;

        Fire(UUID owner, long until) {
            this.owner = owner;
            this.until = until;
        }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** 적 UUID → 들불 표식 */
    private static final Map<UUID, Fire> FIRES = new HashMap<>();
    /** 궁극기 자신의 타격 중 (쌓지 않음) */
    static boolean ownFire;

    static void init() {
        RpgCombat.addScaledBonusHook(JobTraits10::palaceBonus);
        RpgCombat.addWeaponHitHook(JobTraits10::onWeaponHit);
        RpgHealth.LossShare ls = RpgHealth.lossShare;
        RpgHealth.lossShare = (p, src, loss) -> {
            double a = ls.share(p, src, loss);
            return a + showShare(p, loss - a);
        };
        java.util.function.ToDoubleBiFunction<ServerPlayer, DamageSource> sr = RpgHealth.sourceReduce;
        RpgHealth.sourceReduce = (p, src) -> Math.max(sr.applyAsDouble(p, src), springGuard(p));
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> storeFire(entity, source, taken));
        ServerTickEvents.END_SERVER_TICK.register(JobTraits10::palaceTick);
        PierrotSummons.init();
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATES.clear();
            FIRES.clear();
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
        PierrotSummons.dismissAll(sp.getUUID(), true);
        FIRES.values().removeIf(f -> f.owner.equals(sp.getUUID()));
    }

    static void forget(UUID id) {
        STATES.remove(id);
        PierrotSummons.dismissAll(id, false);
        FIRES.values().removeIf(f -> f.owner.equals(id));
    }

    static void lifeEnded(UUID id) {
        State st = STATES.get(id);
        if (st != null) st.guardUntil = 0;
        PierrotSummons.dismissAll(id, true);
        FIRES.values().removeIf(f -> f.owner.equals(id));
    }

    // ------------------------------------------------------------ 트릭스터 미러 팰리스

    /** 미러 팰리스 태세 중 (단검 평타가 어느 쪽에서 쳐도 등 뒤 — JobTraits3 의 등 뒤 치명타 · 엇갈린 베기) */
    static boolean anySide(ServerPlayer sp) {
        return JobTraits.stanceOn(sp, PALACE) && jobIf(sp, TR) != null;
    }

    /** 미러 팰리스: 단검 평타에 거울 베기 (평타 피해 × reflect — 강화) */
    private static RpgCombat.Bonus palaceBonus(ServerPlayer sp, LivingEntity victim, Weapon w, double dmg) {
        if (w == null || !"dagger".equals(w.type()) || !(dmg > 0) || !anySide(sp)) return null;
        Jobs.Job j = jobIf(sp, TR);
        Jobs.Skill k = j == null ? null : j.skill(PALACE);
        double r = k == null ? 0.5 : JobUpgrades.num(sp, k, "reflect", 0.5);
        return new RpgCombat.Bonus(dmg * r, "", KEY_PALACE, DamageNumbers.Style.JOB, null, JobKit.grad(j));
    }

    /** 무기로 맞힘 (미러 팰리스): 표식 +1 더 · reflectDelay 틱 뒤 그 적의 등 뒤에 거울이 번쩍이며 비친 내 모습이 벰 */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        if (victim == null || w == null || !"dagger".equals(w.type()) || !anySide(sp)) return;
        Jobs.Job j = jobIf(sp, TR);
        if (j == null) return;
        JobTraits3.mark(sp, victim, j);
        Jobs.Skill k = j.skill(PALACE);
        int delay = k == null ? 4 : Math.max(1, (int) k.num("reflectDelay", 4));
        String life = RuneKit.lifeToken(sp);
        Vec3 from = sp.position();
        RuneKit.later(delay, () -> {
            if (!RuneKit.sameLife(sp, life) || victim.isRemoved() || !(victim.level() instanceof ServerLevel lv)) return;
            Vec3 away = V.flat(V.sub(victim.position(), from));
            if (V.len(away) < 1e-3) away = Kit.facingOf(sp);
            Vec3 back = V.add(victim.position(), away, 1.0 + Kit.bodyPad(victim));
            Vec3 face = V.scale(away, -1);
            Vec3 vel = RuneKit.entVel(victim);
            // 거울 한 장이 번쩍 → 비친 내 모습 (청록 테두리) 이 베고 거울 조각으로 깨짐
            JobFx.pane(lv, V.up(back, 1.05), face, 1.9, 0.3, Opt.of("vel", vel, "alpha", 0.9), TricksterSkills.ILLUSION, TricksterSkills.ILLUSION_CORE);
            JobFx.pane(lv, V.up(back, 1.05), away, 1.9, 0.3, Opt.of("vel", vel, "alpha", 0.9), TricksterSkills.ILLUSION, TricksterSkills.ILLUSION_CORE);
            TricksterSkills.illusion(sp, lv, back, face, 2, j, 0.3, 0.9);
            Vec3 b = Kit.bodyPoint(victim);
            JobFx.cross(lv, b, 0.6 + Kit.bodyPad(victim) * 0.35, j, 0.24, Opt.of("vel", vel, "spin", Kit.rand() * 360));
            RuneKit.later(5, () -> TricksterSkills.shatter(lv, V.up(back, 1.1), 0.45, j));
            SkillFx.play(lv, "block.amethyst_block.hit", b, 1.6, 0.6);
            SkillFx.play(lv, "entity.player.attack.sweep", b, 1.8, 0.4);
        });
    }

    /**
     * 2틱마다: 미러 팰리스 태세인 트릭스터 둘레를 거울 mirrors 장이 돎 (반지름 radius · 3초에 한 바퀴 — 바깥을 보는 장과 안쪽을 보는 장 · 다음 그림까지 도는 쪽으로 움직임),
     * 거울 사이 바닥에 옅은 고리
     */
    private static void palaceTick(MinecraftServer server) {
        long now = Tasks.now();
        if (now % 2 != 0) return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (!anySide(sp) || sp.isSpectator()) continue;
            Jobs.Job j = jobIf(sp, TR);
            Jobs.Skill k = j.skill(PALACE);
            int n = k == null ? 6 : Math.max(3, (int) k.num("mirrors", 6));
            double r = k == null ? 2.6 : k.num("radius", 2.6);
            ServerLevel level = sp.level();
            double w = Math.PI * 2 / 60; // 틱마다 (3초에 한 바퀴)
            double a0 = now * w;
            Vec3 c = sp.position();
            Vec3 bodyVel = RuneKit.bodyVel(sp);
            for (int i = 0; i < n; i++) {
                double a = a0 + i * Math.PI * 2 / n;
                Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                Vec3 at = new Vec3(c.x + out.x * r, c.y + 1.15 + 0.12 * Math.sin(a * 2 + now * 0.1), c.z + out.z * r);
                Vec3 vel = V.add(bodyVel, new Vec3(-Math.sin(a), 0, Math.cos(a)), w * 20 * r);
                Opt o = Opt.of("vel", vel, "alpha", 0.92, "fade", 0.0, "phase", (now * 8 / 20.0 + i * 1.3) % JobFx.PANE);
                JobFx.pane(level, at, out, 1.7, 0.11, o, JobFx.outer(j), JobFx.inner(j));
                JobFx.pane(level, at, V.scale(out, -1), 1.7, 0.11, o, JobFx.outer(j), JobFx.inner(j));
            }
            if (now % 10 == 0) JobFx.halo(level, V.up(c, 0.06), r, j, 0.55, Opt.of("vel", bodyVel, "alpha", 0.55));
        }
    }

    // ------------------------------------------------------------ 트릭스터 와일드파이어 피스트 (들불 표식)

    /** 들불 표식을 검 (ticks 틱 — 그동안 이 사람이 그 적에게 준 다른 피해를 쌓음) */
    static void startFire(ServerPlayer sp, LivingEntity e, int ticks) {
        FIRES.put(e.getUUID(), new Fire(sp.getUUID(), Tasks.now() + ticks));
    }

    /** 들불 표식을 거둠: 쌓인 피해 (없으면 0) */
    static double takeFire(ServerPlayer sp, LivingEntity e) {
        Fire f = FIRES.get(e.getUUID());
        if (f == null || !f.owner.equals(sp.getUUID())) return 0;
        FIRES.remove(e.getUUID());
        return f.stored;
    }

    /** 시험: 그 적에 쌓인 들불 피해 (없으면 -1) */
    public static double fireStored(LivingEntity e) {
        Fire f = FIRES.get(e.getUUID());
        return f == null ? -1 : f.stored;
    }

    private static void storeFire(LivingEntity e, DamageSource source, float taken) {
        if (FIRES.isEmpty() || ownFire || !(taken > 0)) return;
        Fire f = FIRES.get(e.getUUID());
        if (f == null) return;
        if (f.until < Tasks.now()) {
            FIRES.remove(e.getUUID());
            return;
        }
        if (source.getEntity() instanceof ServerPlayer p && p.getUUID().equals(f.owner)) f.stored += taken;
    }

    // ------------------------------------------------------------ 피에로 쇼타임

    /** 핸드스프링: ticks 틱 동안 받는 피해 − frac */
    static void guardFor(ServerPlayer sp, int ticks, double frac) {
        State st = of(sp);
        st.guardUntil = Tasks.now() + ticks;
        st.guard = Math.max(0, Math.min(0.9, frac));
    }

    /** 시험: 핸드스프링 받는 피해 감소 (없으면 0) */
    public static double guarding(ServerPlayer sp) {
        return springGuard(sp);
    }

    private static double springGuard(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st != null && st.guardUntil > Tasks.now() && jobIf(sp, PR) != null ? st.guard : 0;
    }

    /** 나눠 받음 (RpgHealth.lossShare): 피에로가 받을 피해 loss 의 share 를 shareRange 칸 안의 가장 가까운 소환물이 받음 */
    private static double showShare(ServerPlayer sp, double loss) {
        Jobs.Job j = jobIf(sp, PR);
        if (j == null || !(loss > 0)) return 0;
        return PierrotSummons.share(sp, loss, trait(j, "share", 0.2), trait(j, "shareRange", 12));
    }

    /** 시험: 소환물 수 (게이지와 같음) */
    public static int summons(ServerPlayer sp) {
        return PierrotSummons.count(sp);
    }

    // ------------------------------------------------------------ 틱 · 태세 · 게이지

    /** 4틱마다 (JobTraits.tick): 피에로 — 8틱마다 몸에서 가장 가까운 소환물 (나눠 받는 것) 로 금빛 실 (3인칭 · 다른 사람) */
    static void tick(ServerPlayer sp, Jobs.Job j, long now) {
        if (!PR.equals(j.id()) || now % 8 != 0 || sp.isSpectator()) return;
        double range = trait(j, "shareRange", 12);
        PierrotSummons.Member best = null;
        double bd = range * range;
        for (PierrotSummons.Member m : PierrotSummons.members(sp)) {
            if (!m.alive()) continue;
            double d = m.x.distanceToSqr(sp);
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        if (best == null) return;
        ServerLevel level = sp.level();
        Vec3 from = V.up(RuneKit.handOf(sp).hand(), 0.1);
        Vec3 to = V.up(best.x.position(), best.x.getBbHeight() * 0.85);
        SkillFx.beam(level, from, to, PierrotSkills.GOLD, JobFx.hide1p(sp).with("half", 0.05, "thick", 0.025, "life", 0.42, "alpha", 0.45, "vel", RuneKit.bodyVel(sp)));
    }

    /** 태세 연출 (8틱마다 — JobTraits.stanceFx): 미러 팰리스 = 손의 청록 섬광 (거울은 palaceTick). 그렸으면 true */
    static boolean stanceFx(ServerPlayer sp, Jobs.Job j, String stance) {
        if (!PALACE.equals(stance)) return false;
        RuneKit.Hand h = RuneKit.handOf(sp);
        JobFx.glint(sp.level(), V.up(h.hand(), 0.12), 0.16, TricksterSkills.ILLUSION, TricksterSkills.ILLUSION_CORE, 0.4, Opt.of("vel", RuneKit.bodyVel(sp)));
        return true;
    }

    /** 게이지 값: 피에로 = 소환물 수 (최대 trait.max 까지 보임) */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        return j != null && PR.equals(j.id()) ? Math.min(Math.max(1, j.trait().max()), PierrotSummons.count(sp)) : 0;
    }

    /** 태세 줄 (태세가 아닌 것 — JobTraits.gauge): 피에로 할루시네이트 = 광대 인형의 남은 시간 {이름, 남은 틱, 모두} (없으면 null) */
    static Object[] stanceLine(ServerPlayer sp, Jobs.Job j) {
        if (j == null || !PR.equals(j.id())) return null;
        long now = Tasks.now();
        for (PierrotSummons.Member m : PierrotSummons.members(sp)) {
            if (m.kind != PierrotSummons.DOLL || !m.alive() || m.until <= now) continue;
            Jobs.Skill k = j.skill(DOLL);
            return new Object[] {k == null ? "할루시네이트" : k.name(), (int) (m.until - now), (int) (m.until - m.born)};
        }
        return null;
    }
}
