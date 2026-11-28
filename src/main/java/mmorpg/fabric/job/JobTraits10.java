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
 * <li>피에로 「쇼타임」 (1차 표식과 거꾸로 — 숨어서 노리던 눈길 대신 모두의 눈길을 끌어모음): 소환물이 둘레 몬스터를 끌어들여 대신 맞음 (PierrotSummons) · 부서지면
 * 색종이 폭죽. 게이지 = 소환물 수, 할루시네이트의 남은 시간은 태세 줄에. 2026-11-28 소환수 딜러 (사용자 "탱커에서 딜러로 변경 … 소환수 딜러" · "받피증을 없애고 스타레일의
 * 에이언즈 아하처럼 맞을려고 할때 폭죽 같은 느낌으로 터지고 투명화"): 받는 피해 나눠 받기 · 핸드스프링 받는 피해 감소는 뺌 → 펑! ({@link #popDodge} — RpgHealth.dodge, hurtServer 앞):
 * 몬스터 · 보스의 공격이 닿으려는 순간 그 공격을 막고 폭죽처럼 터져 popFor 틱 투명 (몹이 놓침 · 그동안 오는 공격도 피함) → 짠 하고 나타남, popCd 틱에 한 번</li>
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
        /** 펑!: 다시 터질 수 있는 틱 · 투명이 끝나는 틱 · 터진 수 (시험) */
        long popReady;
        long popUntil;
        int pops;
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
        // 2026-11-28 피에로 펑!: 몬스터 · 보스의 공격이 닿으려는 순간 (받는 피해 나눠 받기 · 핸드스프링 감소 대신 — LivingEntity.hurtServer 앞, RpgHealth.dodge:
        // 플레이어 쪽 무적 · 난이도 셈 뒤). CI 46131283912 · 46132507660 에서 안 터진 것은 길이 아니라 조건 — 시험용 가짜 플레이어는 isCreative 가 늘 참 (DropGuard
        // 와 같음) → 게임 모드는 isRpgMode (서버의 실제 모드 — 생존 · 모험) 로만 봄
        RpgHealth.Dodge dg = RpgHealth.dodge;
        RpgHealth.dodge = (p, src, amount) -> dg.dodge(p, src, amount) || popDodge(p, src, amount);
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
        if (st != null) st.popUntil = 0;
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
                // 2026-11-16 위르드식: 칠한 환영 분신 (JobFx.shade — 거울 유리 몸, 예전 rfx_shade 와 같은 틀 · 자세 · 좌우 — CI 43887309301 팬텀 왈츠 예전 분신 46)
                JobFx.shade(sp, level, at, face, (now / 4) % 3 == 0 ? 1 : 0, JobKit.core(j), TricksterSkills.ILLUSION, Opt.of("life", 0.14, "fade", 0.0, "alpha",
                        0.72, "vel", vel));
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

    /**
     * 펑! 이 이번 피해에 걸리지 않는 까닭 (걸리면 null — 재사용 대기는 보지 않음 · 시험이 먼저 봄). 게임 모드는 서버의 실제 모드로 (isRpgMode — 생존 · 모험):
     * 시험용 가짜 플레이어는 isCreative 가 늘 참 (CI 46131283912 · 46132507660 — 그래서 한 번도 안 터졌음)
     */
    public static String popBlock(ServerPlayer sp, DamageSource source, float amount) {
        if (sp == null || !(amount > 0)) return "no damage";
        if (jobIf(sp, PR) == null) return "not pierrot";
        if (!RpgHealth.isRpgMode(sp)) return "not survival / adventure";
        if (!hostile(sp, source)) return "not a monster's hit";
        return null;
    }

    /** 펑!: 이번 피해를 막는지 (RpgHealth.dodge — true 면 막음) */
    private static boolean popDodge(ServerPlayer sp, DamageSource source, float amount) {
        if (popBlock(sp, source, amount) != null) return false;
        Jobs.Job j = jobIf(sp, PR);
        State st = of(sp);
        long now = Tasks.now();
        if (st.popUntil > now) return true; // 사라진 동안 오는 공격도 피함
        if (now < st.popReady) return false;
        int popFor = Math.max(1, (int) trait(j, "popFor", 30));
        int popCd = Math.max(popFor + 20, (int) trait(j, "popCd", 240));
        st.popReady = now + popCd;
        st.popUntil = now + popFor;
        st.pops++;
        PierrotSkills.pop(sp, j, popFor, popCd);
        return true;
    }

    /** 몬스터 · 보스가 때린 것 (쏜 것 포함 — 주인이 플레이어가 아닌 살아 있는 것). 무적을 꿰뚫는 피해 (공허 · 명령) 는 아님 */
    private static boolean hostile(ServerPlayer sp, DamageSource source) {
        if (source == null || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        net.minecraft.world.entity.Entity e = source.getEntity();
        return e instanceof LivingEntity le && le != sp && !(le instanceof net.minecraft.world.entity.player.Player) && le.isAlive();
    }

    /** 시험 · 사진첩: 펑! 을 처음부터 (바로 터질 수 있게) */
    public static void popReset(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        if (st == null) return;
        st.popReady = 0;
        st.popUntil = 0;
    }

    /** 시험: 터진 수 · 사라져 있는지 · 다시 터질 때까지 남은 틱 */
    public static int pops(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.pops;
    }

    public static boolean popped(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st != null && st.popUntil > Tasks.now();
    }

    public static long popLeft(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : Math.max(0, st.popReady - Tasks.now());
    }

    /** 시험: 소환물 수 (게이지와 같음) */
    public static int summons(ServerPlayer sp) {
        return PierrotSummons.count(sp);
    }

    // ------------------------------------------------------------ 틱 · 태세 · 게이지

    /** 4틱마다 (JobTraits.tick): 피에로 — 2026-11-28 나눠 받기를 빼며 소환물로 잇던 금빛 실도 뺌 (할 일 없음) */
    static void tick(ServerPlayer sp, Jobs.Job j, long now) {}

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
