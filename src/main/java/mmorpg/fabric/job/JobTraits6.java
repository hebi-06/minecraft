package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 2차 직업 — 전사 각성 워로드 특성 · 태세 (재설계안 2판 — claude/job-advance-redo.md, 서버 — jobs.json advance). 반전 글래디에이터 (결투 · 홀름강) 는
 * 2026-11-07 재설계안 3판에서 프로스트 레이더로 바뀜 (사용자 "컨셉이 마음에 안듬" — JobTraits16 · FrostraiderSkills):
 * <ul>
 * <li>워로드 「분노」 (쌓고 줄어드는 것은 전사와 같음 — JobTraitsMore): 다 차면 곰의 화신 bearFor 틱 — 몸을 감싼 곰 (rfx_jbear · 3인칭 · 다른 사람), 도끼 평타가
 * 곰 발톱 세 줄로 넓어짐 (SWING_LOOK warlord_bear — 범위 ×1.5 · 휘두를 때마다 앞에 큰 발톱 세 줄, 피해 + fullBonus — JobTraitsMore.basicMult), 받는 피해
 * −bearGuard (Guards). 그동안 분노가 남은 시간만큼 줄어 끝나면 0 (쌓이지 않음). 동면 (WarlordSkills.slumber) 동안 받은 피해 (최대 체력 1%) 마다 분노 + rage.
 * 4번 칸은 베어 스톰프 (피해 스킬 — 2026-11-05 거인의 무게 태세 대신, WarlordSkills.stomp)</li>
 * </ul>
 */
public final class JobTraits6 {
    private JobTraits6() {}

    static final String WL = "warlord";
    /** SWING_LOOK 의 곰의 화신 */
    static final String BEAR_LOOK = "warlord_bear";
    /** 흙먼지 (누런 회색) */
    static final double[] DUST = {0.62, 0.52, 0.4};

    static final class State {
        // 워로드
        long bearUntil;
        int bearTotal;
        long bearSwing = -1;
        long slumberUntil;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    static void init() {
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
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> STATES.clear());
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
    }

    static void forget(UUID id) {
        STATES.remove(id);
    }

    static void lifeEnded(UUID id) {
        State st = STATES.get(id);
        if (st == null) return;
        st.bearUntil = 0;
        st.slumberUntil = 0;
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

    // ------------------------------------------------------------ 틱 · 태세 연출 · 게이지

    /** 4틱마다 (JobTraits.tick): 곰의 화신이 끝남 · 곰의 기운 */
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
        }
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

    /** 태세 줄 (태세가 없을 때): 곰의 화신 {이름, 남은 틱, 전체 틱} — 없으면 null */
    static Object[] stanceLine(ServerPlayer sp, Jobs.Job j) {
        if (j == null || !WL.equals(j.id())) return null;
        State st = STATES.get(sp.getUUID());
        long now = Tasks.now();
        if (st == null || st.bearUntil <= now) return null;
        return new Object[] {"곰의 화신", (int) (st.bearUntil - now), st.bearTotal};
    }
}
