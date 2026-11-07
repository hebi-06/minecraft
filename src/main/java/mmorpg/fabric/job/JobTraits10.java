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
 * <li>트릭스터 팬텀 왈츠 (태세 — 2026-11-07 3판, 미러 팰리스 대신): 춤 상대 환영이 내 오른쪽 뒤에서 맴돎 (2틱마다 — 1인칭 화면 밖) · 단검 평타는 어느 쪽에서
 * 쳐도 등 뒤 ({@link #anySide} — JobTraits3 치명타 · 환영이 적의 눈길을 끎) · 맞힐 때마다 춤 상대가 그 적 둘레를 반 바퀴 휘돌아 (좌우 번갈아) 등 뒤에서 벰 (평타 × reflect
 * — RpgCombat.addScaledBonusHook, spinAt 틱 뒤 그림) · 표식 +1 더. 화면은 움직이지 않음 (사용자 "화면 흔들림은 넣지마 멀미나" — 내가 매번 등 뒤로 옮겨 가면 화면이 180° 씩
 * 돎)</li>
 * <li>트릭스터 미러 월드: 거울 세계에 가둔 동안 그 적에게 준 다른 피해 (평타 · 다른 스킬 · 룬 — 궁극기 자신의 타격은 빼고) 를 쌓음 (AFTER_DAMAGE)</li>
 * <li>피에로 「쇼타임」 (1차 표식과 거꾸로 — 숨어서 노리던 눈길 대신 모두의 눈길을 끌어모음): 소환물이 둘레 몬스터를 끌어들여 대신 맞음 (PierrotSummons) · 받는 피해의
 * share 를 가장 가까운 소환물이 나눠 받음 (RpgHealth.lossShare) · 부서지면 색종이 폭죽. 게이지 = 소환물 수, 할루시네이트의 남은 시간은 태세 줄에. 핸드스프링: guardFor
 * 틱 받는 피해 − guard (RpgHealth.sourceReduce). 몸에서 가장 가까운 소환물로 금빛 실 (8틱마다 — 나눠 받는 줄)</li>
 * </ul>
 */
public final class JobTraits10 {
    private JobTraits10() {}

    static final String TR = "trickster";
    static final String WALTZ = "trickster_waltz";
    /** 평타에 더한 몫의 구분 (팬텀 왈츠의 환영 베기) */
    static final String KEY_WALTZ = "job_waltz";
    static final String PR = "pierrot";
    static final String DOLL = "pierrot_doll";

    static final class State {
        /** 핸드스프링: 받는 피해 − guard · 끝나는 틱 */
        long guardUntil;
        double guard;
    }

    /** 거울 세계 하나: 가둔 사람 · 끝나는 틱 · 쌓인 피해 */
    static final class Mirror {
        final UUID owner;
        final long until;
        double stored;

        Mirror(UUID owner, long until) {
            this.owner = owner;
            this.until = until;
        }
    }

    /** 팬텀 왈츠의 춤 상대: 적에게 가 있는 틱 (그때까지 곁이 빔) · 다음에 휘도는 쪽 (±1) */
    static final class Waltz {
        long busyUntil;
        int side = 1;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** 적 UUID → 거울 세계 */
    private static final Map<UUID, Mirror> MIRRORS = new HashMap<>();
    private static final Map<UUID, Waltz> WALTZES = new HashMap<>();
    /** 궁극기 자신의 타격 중 (쌓지 않음) */
    static boolean ownMirror;

    static void init() {
        RpgCombat.addScaledBonusHook(JobTraits10::waltzBonus);
        RpgCombat.addWeaponHitHook(JobTraits10::onWeaponHit);
        RpgHealth.LossShare ls = RpgHealth.lossShare;
        RpgHealth.lossShare = (p, src, loss) -> {
            double a = ls.share(p, src, loss);
            return a + showShare(p, loss - a);
        };
        java.util.function.ToDoubleBiFunction<ServerPlayer, DamageSource> sr = RpgHealth.sourceReduce;
        RpgHealth.sourceReduce = (p, src) -> Math.max(sr.applyAsDouble(p, src), springGuard(p));
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> storeMirror(entity, source, taken));
        ServerTickEvents.END_SERVER_TICK.register(JobTraits10::waltzTick);
        PierrotSummons.init();
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATES.clear();
            MIRRORS.clear();
            WALTZES.clear();
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
        WALTZES.remove(sp.getUUID());
        PierrotSummons.dismissAll(sp.getUUID(), true);
        MIRRORS.values().removeIf(f -> f.owner.equals(sp.getUUID()));
    }

    static void forget(UUID id) {
        STATES.remove(id);
        WALTZES.remove(id);
        PierrotSummons.dismissAll(id, false);
        MIRRORS.values().removeIf(f -> f.owner.equals(id));
    }

    static void lifeEnded(UUID id) {
        State st = STATES.get(id);
        if (st != null) st.guardUntil = 0;
        WALTZES.remove(id);
        PierrotSummons.dismissAll(id, true);
        MIRRORS.values().removeIf(f -> f.owner.equals(id));
    }

    // ------------------------------------------------------------ 트릭스터 팬텀 왈츠 (2026-11-07 3판 — 미러 팰리스 대신)

    /** 팬텀 왈츠 태세 중 (단검 평타가 어느 쪽에서 쳐도 등 뒤 — JobTraits3 의 등 뒤 치명타 · 엇갈린 베기) */
    static boolean anySide(ServerPlayer sp) {
        return JobTraits.stanceOn(sp, WALTZ) && jobIf(sp, TR) != null;
    }

    /** 팬텀 왈츠를 씀: 춤 상대가 곁에 섬 (적에게 가 있지 않음) */
    static void waltzStart(ServerPlayer sp) {
        WALTZES.put(sp.getUUID(), new Waltz());
    }

    /** 팬텀 왈츠: 단검 평타에 환영 베기 (평타 피해 × reflect — 강화) */
    private static RpgCombat.Bonus waltzBonus(ServerPlayer sp, LivingEntity victim, Weapon w, double dmg) {
        if (w == null || !"dagger".equals(w.type()) || !(dmg > 0) || !anySide(sp)) return null;
        Jobs.Job j = jobIf(sp, TR);
        Jobs.Skill k = j == null ? null : j.skill(WALTZ);
        double r = k == null ? 0.5 : JobUpgrades.num(sp, k, "reflect", 0.5);
        return new RpgCombat.Bonus(dmg * r, "", KEY_WALTZ, DamageNumbers.Style.JOB, null, JobKit.grad(j));
    }

    /** 춤 상대가 맴도는 자리: 내 오른쪽 1.1칸 · 뒤 0.5칸 (1인칭 화면 밖 · 뒤 3인칭에서 보임) 둘레 반지름 0.3 으로 돎 */
    private static Vec3 partnerSpot(ServerPlayer sp, long now) {
        Vec3 f = Kit.facingOf(sp);
        Vec3 home = V.add(V.add(sp.position(), V.rightOf(f), 1.1), f, -0.5);
        double a = now * Math.PI * 2 / 30;
        return V.add(home, new Vec3(Math.cos(a), 0, Math.sin(a)), 0.3);
    }

    /**
     * 무기로 맞힘 (팬텀 왈츠): 표식 +1 더 · 춤 상대가 곁에서 그 적 옆으로 번쩍 옮겨 가 적 둘레를 반 바퀴 휘돌아 (spinAt 틱 — 좌우 번갈아) 등 뒤에서 벰 (청록 X 자국 ·
     * 꽃잎) → 5틱 뒤 곁으로 돌아옴
     */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        if (victim == null || w == null || !"dagger".equals(w.type()) || !anySide(sp)) return;
        Jobs.Job j = jobIf(sp, TR);
        if (j == null) return;
        JobTraits3.mark(sp, victim, j);
        Jobs.Skill k = j.skill(WALTZ);
        int spin = k == null ? 4 : Math.max(2, (int) k.num("spinAt", 4));
        Waltz wz = WALTZES.computeIfAbsent(sp.getUUID(), x -> new Waltz());
        long now = Tasks.now();
        if (wz.busyUntil > now) return; // 춤 상대가 아직 앞 적에게 가 있음 (그림만 — 피해 · 표식은 그대로)
        wz.busyUntil = now + spin + 6;
        int side = wz.side;
        wz.side = -side;
        String life = RuneKit.lifeToken(sp);
        if (!(victim.level() instanceof ServerLevel lv0)) return;
        Vec3 home = V.up(partnerSpot(sp, now), 0.0);
        Vec3 away0 = V.sub(victim.position(), sp.position());
        Vec3 away = Math.hypot(away0.x, away0.z) > 0.05 ? V.flat(away0) : Kit.facingOf(sp); // V.flat 은 단위 벡터 (0 이면 남쪽)
        double r = 1.0 + Kit.bodyPad(victim);
        double aB = Math.atan2(away.z, away.x);
        double aS = aB + side * Math.toRadians(150);
        // 곁에서 적 옆으로 번쩍 (청록 줄)
        Vec3 s0 = V.add(victim.position(), new Vec3(Math.cos(aS), 0, Math.sin(aS)), r);
        JobFx.line(lv0, V.up(home, 1.0), V.up(s0, 1.0), TricksterSkills.ILLUSION, TricksterSkills.ILLUSION_CORE, 0.25, Opt.of("alpha", 0.8));
        SkillFx.play(lv0, "entity.illusioner.mirror_move", s0, 1.6, 0.5);
        // 적 둘레를 반 바퀴 휘돎 (틱마다 — 돌진 꼴 잔상)
        for (int t = 0; t < spin; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life) || victim.isRemoved() || !(victim.level() instanceof ServerLevel lv)) return;
                double a = aS + (aB - aS) * tt / (double) spin;
                Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                Vec3 at = V.add(victim.position(), out, r);
                Vec3 tangent = V.scale(new Vec3(-Math.sin(a), 0, Math.cos(a)), -side);
                TricksterSkills.illusion(sp, lv, at, tangent, 1, j, 0.16, 0.6 + 0.08 * tt);
                if (tt % 2 == 1) JobFx.petals(lv, V.up(at, 1.0), 2, 1.6, 0.1, j, 0.5, Opt.empty());
            });
        }
        // 등 뒤에서 벰
        RuneKit.later(spin, () -> {
            if (!RuneKit.sameLife(sp, life) || victim.isRemoved() || !(victim.level() instanceof ServerLevel lv)) return;
            Vec3 back = V.add(victim.position(), away, r);
            Vec3 face = V.scale(away, -1);
            Vec3 vel = RuneKit.entVel(victim);
            TricksterSkills.illusion(sp, lv, back, face, 2, j, 0.3, 0.95);
            Vec3 b = Kit.bodyPoint(victim);
            JobFx.cross(lv, V.add(b, away, 0.25), 0.7 + Kit.bodyPad(victim) * 0.35, TricksterSkills.ILLUSION, TricksterSkills.ILLUSION_CORE, 0.24, Opt.of("vel", vel, "spin",
                    Kit.rand() * 360));
            JobFx.petals(lv, b, 5, 2.6, 0.12, j, 0.6, Opt.empty());
            SkillFx.sparks(lv, b, TricksterSkills.ILLUSION_CORE, 5, 3.0);
            SkillFx.play(lv, "block.amethyst_block.hit", b, 1.6, 0.6);
            SkillFx.play(lv, "entity.player.attack.sweep", b, 1.8, 0.4);
            RuneKit.later(5, () -> JobFx.glint(lv, V.up(back, 1.1), 0.4, TricksterSkills.ILLUSION, TricksterSkills.ILLUSION_CORE, 0.2, Opt.empty()));
        });
    }

    /** 2틱마다: 팬텀 왈츠 태세인 트릭스터의 춤 상대 (적에게 가 있지 않으면 — 오른쪽 뒤에서 맴돌며 춤추듯 좌우로 돎) · 10틱마다 몸 둘레 옅은 빛 리본 */
    private static void waltzTick(MinecraftServer server) {
        long now = Tasks.now();
        if (now % 2 != 0) return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (!anySide(sp) || sp.isSpectator()) continue;
            Jobs.Job j = jobIf(sp, TR);
            ServerLevel level = sp.level();
            Waltz wz = WALTZES.get(sp.getUUID());
            if (wz == null || wz.busyUntil <= now) {
                Vec3 at = partnerSpot(sp, now);
                Vec3 next = partnerSpot(sp, now + 2);
                Vec3 vel = V.add(RuneKit.bodyVel(sp), V.sub(next, at), 10.0);
                Vec3 f = Kit.facingOf(sp);
                Vec3 face = (now / 8) % 2 == 0 ? V.rightOf(f) : V.scale(V.rightOf(f), -1); // 춤추듯 좌우로 돎
                RuneKit.shadeClone(sp, level, at, face, (now / 4) % 3 == 0 ? 1 : 0, JobKit.core(j), TricksterSkills.ILLUSION, Opt.of("life", 0.14, "fade", 0.0,
                        "alpha", 0.72, "vel", vel));
            }
            if (now % 10 == 0) JobFx.ribbon(level, V.up(sp.position(), 0.9), 1.0, j, 0.55, 22, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.5));
        }
    }

    // ------------------------------------------------------------ 트릭스터 미러 월드 (거울 세계 — 2026-11-07 3판, 와일드파이어 피스트의 들불 표식과 같은 매커니즘)

    /** 그 적을 거울 세계에 가둠 (ticks 틱 — 그동안 이 사람이 그 적에게 준 다른 피해를 쌓음) */
    static void startMirror(ServerPlayer sp, LivingEntity e, int ticks) {
        MIRRORS.put(e.getUUID(), new Mirror(sp.getUUID(), Tasks.now() + ticks));
    }

    /** 거울 세계를 거둠: 쌓인 피해 (없으면 0) */
    static double takeMirror(ServerPlayer sp, LivingEntity e) {
        Mirror f = MIRRORS.get(e.getUUID());
        if (f == null || !f.owner.equals(sp.getUUID())) return 0;
        MIRRORS.remove(e.getUUID());
        return f.stored;
    }

    /** 시험: 그 적의 거울 세계에 쌓인 피해 (없으면 -1) */
    public static double mirrorStored(LivingEntity e) {
        Mirror f = MIRRORS.get(e.getUUID());
        return f == null ? -1 : f.stored;
    }

    private static void storeMirror(LivingEntity e, DamageSource source, float taken) {
        if (MIRRORS.isEmpty() || ownMirror || !(taken > 0)) return;
        Mirror f = MIRRORS.get(e.getUUID());
        if (f == null) return;
        if (f.until < Tasks.now()) {
            MIRRORS.remove(e.getUUID());
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

    /** 태세 연출 (8틱마다 — JobTraits.stanceFx): 팬텀 왈츠 = 손의 청록 섬광 (춤 상대는 waltzTick). 그렸으면 true */
    static boolean stanceFx(ServerPlayer sp, Jobs.Job j, String stance) {
        if (!WALTZ.equals(stance)) return false;
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
