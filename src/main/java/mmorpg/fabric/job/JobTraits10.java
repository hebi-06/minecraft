package mmorpg.fabric.job;

import java.util.HashMap;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 2차 직업 — 암살자 반전 료스알프(블레이드 댄서) 특성 · 태세 (서버 — jobs.json advance, 트릭스터의 표식 · 은신은 JobTraits3 의 암살자 줄기):
 * <ul>
 * <li>「춤사위」 (1차 표식과 거꾸로 — 적이 아니라 내 몸에 쌓임): 단검 평타와 직업 스킬을 번갈아 맞히면 +1 (최대 trait.max — 평타 → 스킬 · 스킬 → 평타가 맞을 때마다,
 * 같은 쪽이 이어지면 그대로) · 하나당 주는 피해 + perStack (단검 평타 RpgCombat.meleeMult · 직업 스킬 JobKit.hit) · 이동 속도 + speedPer. keep 틱 동안 아무것도
 * 맞히지 못하면 0 (눈부신 한낮 동안은 그대로). 몸 둘레를 도는 빛 꽃잎 (쌓인 수만큼 — 3인칭 · 다른 사람)</li>
 * <li>요정의 걸음: 스텝을 밟는 동안 받는 피해 − guard (RpgHealth.sourceReduce)</li>
 * <li>눈부신 한낮 (태세): 단검 평타가 두 번 벰 (두 번째 echo — RpgCombat.addScaledBonusHook, echoDelay 틱 뒤 빛 베기 그림) · 춤사위가 줄지 않음 · 등 뒤 빛의 요정 날개</li>
 * </ul>
 */
public final class JobTraits10 {
    private JobTraits10() {}

    static final String BD = "bladedancer";
    static final String NOON = "bladedancer_noon";
    /** 평타에 더한 몫의 구분 (눈부신 한낮의 두 번째 베기) */
    static final String KEY_NOON = "job_noon";
    /** 빛 (흰 금빛 — 춤사위 꽃잎 · 섬광 심지) */
    static final double[] LIGHT = {1.0, 0.97, 0.86};

    private static final Identifier DANCE_SPEED = Mmorpg.id("job_dance_speed");

    static final class State {
        int dance;
        /** 마지막으로 맞힌 쪽: 0 없음 · 1 단검 평타 · 2 직업 스킬 */
        int lastKind;
        long lastHit = -1000;
        long guardUntil;
        double guard;
        double speedSet;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    static void init() {
        RpgCombat.MeleeMult mm = RpgCombat.meleeMult;
        RpgCombat.meleeMult = (p, w, v) -> mm.mult(p, w, v) * basicMult(p, w);
        RpgCombat.addScaledBonusHook(JobTraits10::noonBonus);
        RpgCombat.addWeaponHitHook(JobTraits10::onWeaponHit);
        java.util.function.ToDoubleBiFunction<ServerPlayer, DamageSource> sr = RpgHealth.sourceReduce;
        RpgHealth.sourceReduce = (p, src) -> Math.max(sr.applyAsDouble(p, src), stepGuard(p));
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
        speedMod(sp, 0);
    }

    static void forget(UUID id) {
        STATES.remove(id);
    }

    static void lifeEnded(UUID id) {
        State st = STATES.get(id);
        if (st != null) {
            st.dance = 0;
            st.lastKind = 0;
            st.guardUntil = 0;
        }
    }

    // ------------------------------------------------------------ 춤사위

    /** 지금 춤사위 */
    public static int dance(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.dance;
    }

    /** 시험용: 춤사위를 정함 (마지막으로 맞힌 쪽은 지움 — 다음 맞힘은 쌓이지 않음) */
    public static void setDance(ServerPlayer sp, int n) {
        Jobs.Job j = jobIf(sp, BD);
        State st = of(sp);
        st.dance = Math.max(0, Math.min(j == null || j.trait() == null ? 5 : j.trait().max(), n));
        st.lastKind = 0;
        st.lastHit = Tasks.now();
        applySpeed(sp, j, st);
        JobTraits.sendGauge(sp, false);
    }

    /** 시험: 마지막으로 맞힌 쪽 (0 없음 · 1 평타 · 2 스킬) */
    public static int lastKind(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.lastKind;
    }

    /** 맞힘 (kind 1 = 단검 평타 · 2 = 직업 스킬): 앞에 맞힌 쪽과 다르면 춤사위 +1 — 몸 둘레 빛 꽃잎 · 맑은 소리 (가득이면 빛 꽃) */
    private static void hitKind(ServerPlayer sp, Jobs.Job j, int kind) {
        State st = of(sp);
        long now = Tasks.now();
        int max = j.trait() == null ? 5 : Math.max(1, j.trait().max());
        boolean gain = st.lastKind != 0 && st.lastKind != kind && st.dance < max;
        st.lastKind = kind;
        st.lastHit = now;
        if (!gain) return;
        st.dance++;
        applySpeed(sp, j, st);
        ServerLevel level = sp.level();
        Vec3 c = V.up(sp.position(), 1.0);
        JobFx.petals(level, c, 3 + st.dance, 2.4, 0.12, j, 0.55, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
        JobFx.glint(level, V.up(RuneKit.handOf(sp).hand(), 0.15), 0.18 + 0.03 * st.dance, j, 0.3, Opt.of("vel", RuneKit.bodyVel(sp)));
        SkillFx.play(level, "block.amethyst_block.chime", sp.position(), 1.2 + 0.12 * st.dance, 0.45);
        if (st.dance == max) {
            JobFx.ring(level, V.up(sp.position(), 0.06), 1.6, j, false, 0.4, Opt.of("vel", RuneKit.bodyVel(sp)));
            JobFx.eyeRing(sp, j, 2.4, 0.7, 0.35);
            SkillFx.play(level, "block.note_block.chime", sp.position(), 1.6, 0.7);
        }
        JobTraits.sendGauge(sp, false);
    }

    private static void applySpeed(ServerPlayer sp, Jobs.Job j, State st) {
        double want = j == null ? 0 : st.dance * trait(j, "speedPer", 0.04);
        if (Math.abs(want - st.speedSet) < 1e-9) return;
        st.speedSet = want;
        speedMod(sp, want);
    }

    private static void speedMod(ServerPlayer sp, double amount) {
        AttributeInstance a = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null) return;
        if (amount > 0) a.addOrUpdateTransientModifier(new AttributeModifier(DANCE_SPEED, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        else a.removeModifier(DANCE_SPEED);
    }

    /** 시험: 걸린 춤사위 이동 속도 (없으면 0) */
    public static double speedBonus(ServerPlayer sp) {
        AttributeInstance a = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeModifier m = a == null ? null : a.getModifier(DANCE_SPEED);
        return m == null ? 0 : m.amount();
    }

    /** 단검 평타 배율 (RpgCombat.meleeMult): 블레이드 댄서 1 + 춤사위 × perStack */
    private static double basicMult(ServerPlayer sp, Weapon w) {
        if (w == null || !"dagger".equals(w.type())) return 1;
        Jobs.Job j = jobIf(sp, BD);
        return j == null ? 1 : 1 + dance(sp) * trait(j, "perStack", 0.06);
    }

    /** 직업 스킬 피해 한 번의 배율 (JobKit.hit · hitSplit): 블레이드 댄서 1 + 춤사위 × perStack */
    static double skillMult(ServerPlayer sp, Jobs.Job j) {
        if (j == null || !BD.equals(j.id())) return 1;
        return 1 + dance(sp) * trait(j, "perStack", 0.06);
    }

    /** 직업 스킬 피해가 들어감 (JobKit): 블레이드 댄서 — 스킬 쪽 맞힘 */
    static void afterJobHit(ServerPlayer sp, LivingEntity e, Jobs.Job j, double got) {
        if (j == null || !BD.equals(j.id()) || !(got > 0)) return;
        hitKind(sp, j, 2);
    }

    /** 무기로 맞힘: 블레이드 댄서 단검 평타 → 평타 쪽 맞힘 · 눈부신 한낮이면 echoDelay 틱 뒤 두 번째 빛 베기 그림 */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        if (victim == null || w == null || !"dagger".equals(w.type())) return;
        Jobs.Job j = jobIf(sp, BD);
        if (j == null) return;
        hitKind(sp, j, 1);
        if (!JobTraits.stanceOn(sp, NOON) || !RuneKit.fxQuota("jobNoonEcho" + sp.getId(), 1)) return;
        Jobs.Skill k = j.skill(NOON);
        int delay = k == null ? 3 : Math.max(1, (int) k.num("echoDelay", 3));
        String life = RuneKit.lifeToken(sp);
        RuneKit.later(delay, () -> {
            if (!RuneKit.sameLife(sp, life) || victim.isRemoved() || !(victim.level() instanceof ServerLevel lv)) return;
            Vec3 b = Kit.bodyPoint(victim);
            JobFx.cross(lv, b, 0.65 + Kit.bodyPad(victim) * 0.35, j, 0.24, Opt.of("vel", RuneKit.entVel(victim), "spin", Kit.rand() * 360));
            JobFx.glint(lv, b, 0.3, j, 0.25, Opt.of("vel", RuneKit.entVel(victim)));
            JobFx.slash(sp, j, 1.7, 0.3, 1.1, Kit.rand() < 0.5 ? 160.0 : 340.0, 0.16);
            SkillFx.play(lv, "entity.player.attack.sweep", b, 1.7, 0.45);
        });
    }

    /** 눈부신 한낮: 단검 평타에 두 번째 베기 (평타 피해 × echo — 강화) */
    private static RpgCombat.Bonus noonBonus(ServerPlayer sp, LivingEntity victim, Weapon w, double dmg) {
        if (w == null || !"dagger".equals(w.type()) || !(dmg > 0) || !JobTraits.stanceOn(sp, NOON)) return null;
        Jobs.Job j = jobIf(sp, BD);
        if (j == null) return null;
        Jobs.Skill k = j.skill(NOON);
        double echo = k == null ? 0.42 : JobUpgrades.num(sp, k, "echo", 0.42);
        return new RpgCombat.Bonus(dmg * echo, "", KEY_NOON, DamageNumbers.Style.JOB, null, JobKit.grad(j));
    }

    // ------------------------------------------------------------ 요정의 걸음: 받는 피해 −

    /** 요정의 걸음: ticks 틱 동안 받는 피해 − frac */
    static void guardFor(ServerPlayer sp, int ticks, double frac) {
        State st = of(sp);
        st.guardUntil = Tasks.now() + ticks;
        st.guard = Math.max(0, Math.min(0.9, frac));
    }

    /** 시험: 요정의 걸음 받는 피해 감소 (없으면 0) */
    public static double guarding(ServerPlayer sp) {
        return stepGuard(sp);
    }

    private static double stepGuard(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st != null && st.guardUntil > Tasks.now() && jobIf(sp, BD) != null ? st.guard : 0;
    }

    // ------------------------------------------------------------ 틱 · 태세 · 게이지

    /**
     * 4틱마다 (JobTraits.tick): 블레이드 댄서 — keep 틱 동안 아무것도 맞히지 못하면 춤사위 0 (눈부신 한낮 동안은 그대로), 8틱마다 몸 둘레를 도는 빛 꽃잎 (쌓인 수 —
     * 3인칭 · 다른 사람)
     */
    static void tick(ServerPlayer sp, Jobs.Job j, long now) {
        if (!BD.equals(j.id())) return;
        State st = STATES.get(sp.getUUID());
        if (st == null) return;
        if (st.dance > 0 && now - st.lastHit >= (long) trait(j, "keep", 60) && !JobTraits.stanceOn(sp, NOON)) {
            st.dance = 0;
            st.lastKind = 0;
            applySpeed(sp, j, st);
            SkillFx.play(sp.level(), "block.amethyst_block.break", sp.position(), 1.4, 0.35);
            JobTraits.sendGauge(sp, false);
        }
        if (now % 8 == 0 && st.dance > 0 && !sp.isSpectator()) {
            ServerLevel level = sp.level();
            double a0 = now * 0.16;
            for (int i = 0; i < st.dance; i++) {
                double a = a0 + i * Math.PI * 2 / st.dance;
                Vec3 p = new Vec3(sp.getX() + Math.cos(a) * 0.8, sp.getY() + 0.8 + 0.3 * Math.sin(a * 2 + i), sp.getZ() + Math.sin(a) * 0.8);
                JobFx.blossom(level, p, 0.1, j, 0.42, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.85, "fade", 0.3));
            }
        }
    }

    /** 요정 날개 자리 (등 뒤 — 어깨 높이) */
    static Vec3 wingAt(ServerPlayer sp) {
        return V.up(V.add(sp.position(), Kit.facingOf(sp), -0.42), 1.4);
    }

    /** 태세 연출 (8틱마다 — JobTraits.stanceFx): 눈부신 한낮 = 등 뒤 빛의 요정 날개 (3인칭 · 주인마다 하나) · 손의 섬광 · 1인칭 화면 아래 빛 꽃잎. 그렸으면 true */
    static boolean stanceFx(ServerPlayer sp, Jobs.Job j, String stance) {
        if (!NOON.equals(stance)) return false;
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        JobFx.fairy(level, wingAt(sp), f, 1.6, j, 0.6, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.85, "solo", 61.0, "phase", (double) ((Tasks.now()
                / 4) % JobFx.FAIRY)));
        RuneKit.Hand h = RuneKit.handOf(sp);
        JobFx.glint(level, V.up(h.hand(), 0.12), 0.16, j, 0.4, Opt.of("vel", RuneKit.bodyVel(sp)));
        Vec3 look = sp.getViewVector(1F);
        JobFx.petal(level, V.add(V.add(sp.getEyePosition(), look, 1.8), JobFx.screenUp(look), -0.7), V.UP, 0.6, 0.08, j, 0.4, JobFx.only1p(sp).with("alpha",
                0.8));
        return true;
    }

    /** 게이지 값: 블레이드 댄서 = 춤사위 */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        return j != null && BD.equals(j.id()) ? dance(sp) : 0;
    }
}
