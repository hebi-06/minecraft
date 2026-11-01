package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Combat;
import mmorpg.core.Jobs;
import mmorpg.core.SwingLooks;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.SwingArea;
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
 * 0.51.0 2차 직업 — 전사 각성 뵈드바르(워로드) · 반전 울프헤딘(라이칸) 특성 · 태세 (서버 — jobs.json advance):
 * <ul>
 * <li>워로드 「분노」 (쌓고 줄어드는 것은 전사와 같음 — JobTraitsMore): 다 차면 곰의 화신 bearFor 틱 — 몸을 감싼 곰 (rfx_jbear · 3인칭 · 다른 사람), 도끼 평타가
 * 곰 발톱 세 줄로 넓어짐 (SWING_LOOK warlord_bear — 범위 ×1.5 · 휘두를 때마다 앞에 큰 발톱 세 줄, 피해 + fullBonus — JobTraitsMore.basicMult), 받는 피해
 * −bearGuard (Guards). 그동안 분노가 남은 시간만큼 줄어 끝나면 0 (쌓이지 않음). 곰의 포효 (태세 칸): 분노 가득 · 곰의 화신 바로 (dur) · undying 틱 쓰러지지 않음 ·
 * 그 dur 동안 체력이 low 아래로 떨어지면 heal 회복 (heals 번까지)</li>
 * <li>라이칸 「야성」 (0 ~ max): 사냥감 (같은 적) 을 이어 맞히면 +1 — 도끼 평타는 휘두르기마다 한 번 (그 휘두르기의 첫 맞힘이 다른 적이면 그 적이 사냥감 · 1부터,
 * 범위로 맞은 다른 적은 셈하지 않음), 직업 스킬은 사냥감을 맞힌 틱마다 한 번 (사냥감을 바꾸지 않음). 사냥감이 쓰러지거나 24칸 넘게 멀어지면 다음에 맞힌 적에게
 * 이어짐. decay 틱 동안 못 맞히면 20틱마다 하나씩 (늑대의 밤 동안은 그대로). 하나당 공격 속도 + atkSpeed (RpgCombat.attackSpeed → 평타 사이
 * Motions.attackInterval — 클라이언트는 게이지 값으로 같게 셈) · 이동 속도 + moveSpeed. 야성 clawAt 이상에서 쓴 피해 직업 스킬은 할퀴기 (그 스킬이 끝날 때까지
 * 직업 스킬 피해 × (1 + clawBonus) · 맞은 적마다 붉은 발톱 자국 claws 개 — 나가면 clawUse 씀). 겁먹음 (달밤의 포효): 그 적이 플레이어에게 주는 피해 × (1 − weaken)
 * (RpgHealth.weakenOf — 머리 위 식은땀). 늑대의 밤 (태세): 도끼 평타 두 번째 베기 (echo — 발톱) · 이동 속도 + speed · 야성 유지</li>
 * </ul>
 */
public final class JobTraits6 {
    private JobTraits6() {}

    static final String WL = "warlord";
    static final String LY = "lycan";
    static final String ROAR = "warlord_roar";
    static final String NIGHT = "lycan_night";
    /** 평타에 더한 몫의 구분 (WeaponBonus.parts) */
    static final String KEY_NIGHT = "job_night";
    /** SWING_LOOK 의 곰의 화신 */
    static final String BEAR_LOOK = "warlord_bear";
    private static final Identifier FEROCITY_SPEED = Mmorpg.id("job_ferocity_speed");
    private static final Identifier NIGHT_SPEED = Mmorpg.id("job_night_speed");
    /** 땀방울 (겁먹음 — 식은 은빛) */
    static final double[] SWEAT = {0.78, 0.88, 1.0};

    static final class State {
        // 워로드
        long bearUntil;
        int bearTotal;
        long undyingUntil;
        long roarUntil;
        int roarHeals;
        long bearSwing = -1;
        // 라이칸
        int stacks;
        UUID prey;
        long lastGain = -1000;
        long lastDecay = -1000;
        long swingCounted = Long.MIN_VALUE;
        long skillCounted = Long.MIN_VALUE;
        long clawUntil;
        boolean clawPending;
        int clawUsed;
        final Set<UUID> clawed = new HashSet<>();
        double speedSet;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    /** 겁먹음: 적 → {적, 끝 틱, 줄이는 몫} */
    private record Fear(LivingEntity e, long until, double cut) {}

    private static final Map<UUID, Fear> FEARS = new HashMap<>();

    static void init() {
        RpgCombat.addScaledBonusHook(JobTraits6::nightBonus);
        RpgCombat.addWeaponHitHook(JobTraits6::onWeaponHit);
        java.util.function.ToDoubleFunction<ServerPlayer> as = RpgCombat.attackSpeed;
        RpgCombat.attackSpeed = p -> as.applyAsDouble(p) * attackSpeed(p);
        java.util.function.ToDoubleFunction<Entity> wk = RpgHealth.weakenOf;
        RpgHealth.weakenOf = e -> 1 - (1 - wk.applyAsDouble(e)) * (1 - fearOf(e));
        java.util.function.Predicate<ServerPlayer> und = RpgHealth.undying;
        java.util.function.Consumer<ServerPlayer> saved = RpgHealth.undyingSaved;
        RpgHealth.undying = p -> und.test(p) || roarUndying(p);
        RpgHealth.undyingSaved = p -> {
            if (und.test(p)) saved.accept(p);
            else roarSaved(p);
        };
        RpgHealth.LossHook lh = RpgHealth.lossHook;
        RpgHealth.lossHook = (sp, src, loss, before, after) -> {
            lh.lost(sp, src, loss, before, after);
            onLoss(sp, src, after);
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
            FEARS.clear();
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

    /** 직업을 바꿈 · 시험: 상태를 지우고 속도 수정치를 뗌 */
    static void reset(ServerPlayer sp) {
        STATES.remove(sp.getUUID());
        speedMod(sp, FEROCITY_SPEED, 0);
        speedMod(sp, NIGHT_SPEED, 0);
    }

    static void forget(UUID id) {
        STATES.remove(id);
    }

    static void lifeEnded(UUID id) {
        State st = STATES.get(id);
        if (st == null) return;
        st.bearUntil = 0;
        st.undyingUntil = 0;
        st.roarUntil = 0;
        st.stacks = 0;
        st.prey = null;
        st.clawUntil = 0;
        st.clawPending = false;
        st.clawed.clear();
        st.speedSet = 0;
    }

    /** 이동 속도 수정치 (amount = 비율 — 0 이면 뗌) */
    private static void speedMod(ServerPlayer sp, Identifier id, double amount) {
        AttributeInstance a = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null) return;
        if (amount > 0) a.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        else a.removeModifier(id);
    }

    /** 시험: 걸린 이동 속도 수정치 (야성 · 늑대의 밤 — 없으면 0) */
    public static double speedBonus(ServerPlayer sp, boolean night) {
        AttributeInstance a = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeModifier m = a == null ? null : a.getModifier(night ? NIGHT_SPEED : FEROCITY_SPEED);
        return m == null ? 0 : m.amount();
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

    /** 곰의 화신 시작 (분노 가득 · 곰의 포효): ticks 틱 — 받는 피해 −bearGuard · 몸을 감싼 곰 · 포효 · 번쩍임 · 알림 */
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
        JobKit.shake(sp, 8, 0.25, 0.3);
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

    /** 곰의 포효: 분노 가득 · 곰의 화신 (ticks) · undying 틱 쓰러지지 않음 · ticks 동안 위기 회복 heals 번 */
    static void roarStart(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, int ticks) {
        State st = of(sp);
        long now = Tasks.now();
        JobTraitsMore.setRage(sp, j.trait() == null ? 100 : Math.max(1, j.trait().max()));
        bearStart(sp, j, ticks);
        st.undyingUntil = now + (long) k.num("undying", 40);
        st.roarUntil = now + ticks;
        st.roarHeals = (int) k.num("heals", 2);
    }

    /** 곰의 포효의 쓰러지지 않음 */
    static boolean roarUndying(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st != null && st.undyingUntil > Tasks.now() && jobIf(sp, WL) != null;
    }

    /** 시험: 곰의 포효로 쓰러지지 않는 중 · 남은 위기 회복 */
    public static boolean undyingOn(ServerPlayer sp) {
        return roarUndying(sp);
    }

    public static int roarHealsLeft(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null || st.roarUntil <= Tasks.now() ? 0 : st.roarHeals;
    }

    /** 쓰러짐을 막음 (다음 틱): 주황 번쩍 · 곰 울음 · 글자 */
    private static void roarSaved(ServerPlayer sp) {
        if (!RuneKit.fxQuota("jobRoarSaved" + sp.getId(), 1)) return;
        Jobs.Job j = jobIf(sp, WL);
        ServerLevel level = sp.level();
        Vec3 c = V.up(sp.position(), 1.0);
        if (j != null) JobFx.burst(level, c, 1.2, j, 0.4, JobFx.hide1p(sp));
        SkillFx.play(level, "entity.polar_bear.warning", c, 0.9, 1.0);
        Notify.notify(sp, "§6§l곰의 포효 §r§7— 쓰러지지 않았습니다", 30);
    }

    /** 맞음: 곰의 포효 동안 체력이 low 아래로 떨어지면 heal 회복 (heals 번까지) */
    private static void onLoss(ServerPlayer sp, DamageSource src, double after) {
        State st = STATES.get(sp.getUUID());
        if (st == null || st.roarUntil <= Tasks.now() || st.roarHeals <= 0) return;
        Jobs.Job j = jobIf(sp, WL);
        Jobs.Skill k = j == null ? null : j.skill(ROAR);
        double max = RpgHealth.maxOf(sp);
        if (k == null || !(max > 0) || after <= 0 || after >= max * k.num("low", 0.3)) return;
        st.roarHeals--;
        double heal = max * JobUpgrades.num(sp, k, "heal", k.num("heal", 0.2));
        String life = RuneKit.lifeToken(sp);
        RuneKit.later(1, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            double got = RpgHealth.healBy(sp, heal);
            if (got > 0) DamageNumbers.show(sp, got, DamageNumbers.Style.HEAL, "", 0);
            ServerLevel level = sp.level();
            JobFx.aura(sp, j, 3.0, 0.9, 1.0);
            JobFx.ring(level, V.up(sp.position(), 0.06), 2.6, j, false, 0.5, Opt.empty());
            JobFx.bear(level, V.up(V.add(sp.position(), Kit.facingOf(sp), -0.35), 1.55), 1.7, 0.7, 3, 4, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp),
                    "alpha", 0.8), bearGlow(j), bearCore(j));
            SkillFx.sparks(level, V.up(sp.position(), 1.0), JobKit.glow(j), 14, 5);
            SkillFx.play(level, "random.totem", sp.position(), 1.3, 0.6);
            SkillFx.play(level, "entity.polar_bear.warning", sp.position(), 0.8, 1.0);
        });
    }

    /** 곰의 화신 동안 도끼를 휘두를 때마다 (허공 포함): 앞에 큰 곰 발톱 세 줄 (1인칭 눈앞 · 3인칭 몸 앞) · 둔탁한 소리 */
    private static void onSwing(ServerPlayer sp, Weapon w) {
        if (w == null || !"axe".equals(w.type()) || !bearOn(sp)) return;
        Jobs.Job j = jobIf(sp, WL);
        if (j == null) return;
        State st = of(sp);
        long now = Tasks.now();
        if (st.bearSwing == now) return;
        st.bearSwing = now;
        ServerLevel level = sp.level();
        Vec3 look = sp.getViewVector(1F);
        Vec3 at = V.add(V.add(sp.getEyePosition(), look, 2.3), JobFx.screenUp(look), -0.2);
        double tilt = (now % 2 == 0 ? 20 : -20) + Kit.rand() * 10;
        JobFx.bearClaws(level, at, 1.5, tilt, 0.3, JobFx.only1p(sp).with("vel", RuneKit.bodyVel(sp)), j);
        JobFx.bearClaws(level, V.up(V.add(sp.position(), Kit.facingOf(sp), 1.6), 1.2), 1.25, tilt, 0.3, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), j);
        SkillFx.play(level, "entity.polar_bear.hurt", sp.position(), 0.7, 0.35);
    }

    // ------------------------------------------------------------ 라이칸 「야성」

    /** 지금 야성 */
    public static int ferocity(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.stacks;
    }

    /** 시험용: 야성을 정함 (사냥감은 그대로) */
    public static void setFerocity(ServerPlayer sp, int n) {
        State st = of(sp);
        Jobs.Job j = JobSystem.job(sp);
        int max = j == null || j.trait() == null ? 10 : Math.max(1, j.trait().max());
        st.stacks = Math.max(0, Math.min(max, n));
        st.lastGain = st.lastDecay = Tasks.now();
        applySpeed(sp, j, st);
        JobTraits.sendGauge(sp, false);
    }

    /** 시험용: 지금 사냥감 (없으면 null) */
    public static UUID preyOf(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? null : st.prey;
    }

    /** 야성을 n 개까지 씀 — 쓴 수 */
    static int useFerocity(ServerPlayer sp, int n) {
        State st = of(sp);
        int k = Math.max(0, Math.min(n, st.stacks));
        st.stacks -= k;
        st.lastDecay = Tasks.now();
        applySpeed(sp, JobSystem.job(sp), st);
        JobTraits.sendGauge(sp, false);
        return k;
    }

    /** 사냥감을 정함 (사냥감 덮치기 — 야성 그대로) */
    static void setPrey(ServerPlayer sp, LivingEntity e) {
        if (e == null) return;
        of(sp).prey = e.getUUID();
    }

    /** 사냥감이 아직 있는지 (살아 있고 같은 세계 24칸 안) */
    private static boolean preyValid(ServerPlayer sp, State st) {
        if (st.prey == null) return false;
        Entity e = sp.level().getEntity(st.prey);
        return e instanceof LivingEntity le && le.isAlive() && !le.isRemoved() && le.distanceToSqr(sp) <= 24 * 24;
    }

    /** 야성 +1 (최대 trait.max) — 쌓일 때 손의 붉은 반짝임, 다 차면 눈빛 · 늑대 울음 */
    private static void gain(ServerPlayer sp, Jobs.Job j, State st) {
        int max = j.trait() == null ? 10 : Math.max(1, j.trait().max());
        long now = Tasks.now();
        st.lastGain = now;
        st.lastDecay = now;
        if (st.stacks >= max) return;
        st.stacks++;
        applySpeed(sp, j, st);
        ServerLevel level = sp.level();
        RuneKit.Hand h = RuneKit.handOf(sp);
        SkillFx.fx(level, "rpg:arrow_spark", V.up(h.hand(), 0.25), Opt.of("dir", V.UP, "speed", 1.4, "size", 0.13, "grow", 0.8, "life", 0.32, "color", JobKit.core(j),
                "alpha", 1.0));
        if (st.stacks == max) {
            eyeGlint(sp, j, 0.5);
            JobFx.burst(level, V.up(V.add(h.hand(), h.f(), 0.6), 0.3), 0.32, j, 0.3, Opt.of("vel", RuneKit.bodyVel(sp)));
            SkillFx.play(level, "entity.wolf.growl", sp.position(), 1.1, 0.6);
            Notify.notify(sp, "§c§l야성 " + max + " §7— 다음 직업 스킬에 할퀴기", 30);
        }
        JobTraits.sendGauge(sp, false);
    }

    /** 이동 속도 수정치를 야성에 맞춤 (공격 속도는 RpgCombat.attackSpeed 가 그때그때 읽음) */
    private static void applySpeed(ServerPlayer sp, Jobs.Job j, State st) {
        double want = j != null && LY.equals(j.id()) ? st.stacks * trait(j, "moveSpeed", 0.02) : 0;
        if (Math.abs(want - st.speedSet) < 1e-9) return;
        st.speedSet = want;
        speedMod(sp, FEROCITY_SPEED, want);
    }

    /** 공격 속도 배율 (라이칸: 1 + 야성 × atkSpeed — 클라이언트 ClientJobBar.attackSpeed 와 같은 셈) */
    static double attackSpeed(ServerPlayer sp) {
        Jobs.Job j = jobIf(sp, LY);
        if (j == null) return 1;
        State st = STATES.get(sp.getUUID());
        return st == null ? 1 : 1 + st.stacks * trait(j, "atkSpeed", 0.02);
    }

    /** 눈의 붉은 빛 (두 점 — 3인칭 · 다른 사람): 늑대 눈빛 */
    static void eyeGlint(ServerPlayer sp, Jobs.Job j, double life) {
        ServerLevel level = sp.level();
        Vec3 f = V.flat(sp.getViewVector(1F));
        Vec3 r = V.rightOf(f);
        Vec3 eye = V.add(sp.getEyePosition(), f, 0.32);
        for (int s = -1; s <= 1; s += 2) {
            RuneKit.rfx(level, "prop", V.add(eye, r, 0.11 * s), JobFx.hide1p(sp).with("size", 0.09, "life", life, "cell", (double) RuneKit.RFX.get("glint")[0], "spin", 0.0,
                    "spinRate", 90.0, "color", JobKit.core(j), "grow", 0.4, "fade", 0.5, "vel", RuneKit.bodyVel(sp)));
        }
    }

    /** 도끼 평타가 맞음: 휘두르기마다 한 번 — 사냥감이면 +1, 첫 맞힘이 다른 적이면 그 적이 사냥감 (1부터 — 사냥감이 없으면 이어짐) · 늑대의 밤 발톱 */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        if (w == null || victim == null || !"axe".equals(w.type()) || !Combat.isMeleeWeapon(w)) return;
        Jobs.Job j = jobIf(sp, LY);
        if (j == null) return;
        State st = of(sp);
        long swing = SwingArea.swingTick(sp);
        long key = swing >= 0 ? swing : Tasks.now();
        if (bonus != null) {
            for (RpgCombat.Bonus p : bonus.parts()) {
                if (!KEY_NIGHT.equals(p.key())) continue;
                // 늑대의 밤 두 번째 베기: 맞은 자리에 붉은 발톱 (조금 늦게)
                Jobs.Job jj = j;
                RuneKit.later(3, () -> {
                    if (victim.isRemoved() || !(victim.level() instanceof ServerLevel lv)) return;
                    JobFx.claw4(lv, Kit.bodyPoint(victim), 0.8 + Kit.bodyPad(victim) * 0.5, Kit.rand() * 50 - 25, 0.32, Opt.empty(), JobKit.core(jj), JobFx.inner(jj));
                    SkillFx.play(lv, "entity.player.attack.sweep", Kit.bodyPoint(victim), 1.6, 0.4);
                });
                break;
            }
        }
        if (st.swingCounted == key) {
            // 같은 휘두르기의 범위 맞힘: 사냥감이 아직 셈하지 않았으면 (조준이 다른 적이었는데 범위로 사냥감도 맞음) 셈하지 않음 — 한 휘두르기에 한 번
            return;
        }
        st.swingCounted = key;
        boolean valid = preyValid(sp, st);
        if (valid && victim.getUUID().equals(st.prey)) {
            gain(sp, j, st);
        } else if (!valid) {
            st.prey = victim.getUUID();
            gain(sp, j, st);
        } else {
            // 다른 적을 먼저 침: 그 적이 사냥감 · 1부터
            st.prey = victim.getUUID();
            st.stacks = 0;
            gain(sp, j, st);
        }
    }

    /** 늑대의 밤: 도끼 평타 두 번째 베기 (평타 × echo) */
    private static RpgCombat.Bonus nightBonus(ServerPlayer sp, LivingEntity victim, Weapon w, double dmg) {
        if (w == null || !"axe".equals(w.type()) || !(dmg > 0) || !JobTraits.stanceOn(sp, NIGHT)) return null;
        Jobs.Job j = jobIf(sp, LY);
        if (j == null) return null;
        Jobs.Skill k = j.skill(NIGHT);
        double echo = k == null ? 0.42 : JobUpgrades.num(sp, k, "echo", 0.42);
        return new RpgCombat.Bonus(dmg * echo, "", KEY_NIGHT, DamageNumbers.Style.JOB, null, JobKit.grad(j));
    }

    /** 늑대의 밤 시작: 이동 속도 + speed (끝나면 stanceEnded 가 뗌) */
    static void nightStart(ServerPlayer sp, Jobs.Skill k) {
        speedMod(sp, NIGHT_SPEED, k.num("speed", 0.2));
    }

    /** 태세가 끝남 (JobTraits): 늑대의 밤 → 이동 속도 수정치를 뗌 */
    static void stanceEnded(ServerPlayer sp, String id) {
        if (NIGHT.equals(id)) speedMod(sp, NIGHT_SPEED, 0);
    }

    // ------------------------------------------------------------ 시전 (JobSystem.Slot.cast) — 라이칸 할퀴기

    /**
     * 야성이 clawAt 이상이면 이번 피해 스킬에 할퀴기 (지금부터 그 스킬이 끝날 때까지) — 야성 clawUse 개를 먼저 씀 (그 스킬의 첫 타가 바로 들어가 다시 +1 이
     * 되게). 나가지 않으면 afterCast 가 거두고 돌려줌
     */
    static void beforeCast(ServerPlayer sp, Jobs.Job j, Jobs.Skill k) {
        if (j == null || !LY.equals(j.id()) || k == null || k.total() <= 0) return;
        State st = of(sp);
        if (st.stacks < trait(j, "clawAt", 10)) return;
        st.clawPending = true;
        st.clawUsed = useFerocity(sp, (int) trait(j, "clawUse", 5));
        st.clawed.clear();
        st.clawUntil = Tasks.now() + Math.max(20, (long) k.num("t", 20) + 12);
    }

    /** 나간 뒤: 할퀴기 → 눈빛 · 울음 (나가지 않았으면 할퀴기를 거두고 쓴 야성을 돌려줌). 알림 줄에 덧붙일 말 (없으면 null) */
    static String afterCast(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, boolean ok) {
        if (j == null || !LY.equals(j.id())) return null;
        State st = STATES.get(sp.getUUID());
        if (st == null || !st.clawPending) return null;
        st.clawPending = false;
        if (!ok) {
            st.clawUntil = 0;
            st.stacks = Math.min(j.trait() == null ? 10 : j.trait().max(), st.stacks + st.clawUsed);
            applySpeed(sp, j, st);
            JobTraits.sendGauge(sp, false);
            return null;
        }
        eyeGlint(sp, j, 0.6);
        SkillFx.play(sp.level(), "entity.wolf.growl", sp.position(), 1.3, 0.7);
        return "§c할퀴기 ×" + (int) trait(j, "claws", 3) + " +" + Math.round(trait(j, "clawBonus", 0.3) * 100) + "%";
    }

    /** 이번 직업 스킬 피해의 할퀴기 배율 (라이칸 할퀴기 동안 1 + clawBonus — 그 밖 1) */
    static double clawMult(ServerPlayer sp, Jobs.Job j) {
        if (j == null || !LY.equals(j.id())) return 1;
        State st = STATES.get(sp.getUUID());
        return st != null && st.clawUntil >= Tasks.now() ? 1 + trait(j, "clawBonus", 0.3) : 1;
    }

    /** 시험용: 할퀴기 창이 열려 있는지 */
    public static boolean clawOn(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st != null && st.clawUntil >= Tasks.now();
    }

    // ------------------------------------------------------------ 직업 스킬 피해가 들어간 뒤 (JobKit.hit)

    /** 라이칸: 사냥감을 맞혔으면 야성 +1 (틱마다 한 번 — 사냥감이 없으면 그 적이 사냥감) · 할퀴기 동안 맞은 적마다 붉은 발톱 자국 claws 개 (적마다 한 번) */
    static void afterJobHit(ServerPlayer sp, LivingEntity e, Jobs.Job j, double dealt) {
        if (j == null || !LY.equals(j.id()) || !(dealt > 0) || e == null) return;
        State st = of(sp);
        long now = Tasks.now();
        boolean valid = preyValid(sp, st);
        if (!valid) st.prey = e.getUUID();
        if (e.getUUID().equals(st.prey) && st.skillCounted != now) {
            st.skillCounted = now;
            gain(sp, j, st);
        }
        if (st.clawUntil >= now && st.clawed.add(e.getUUID()) && e.level() instanceof ServerLevel level) clawMarks(level, e, j, (int) trait(j, "claws", 3));
    }

    /** 할퀴기: 맞은 적에 붉은 발톱 자국 n 개 (2틱마다 — 엇갈려 기울어짐) · 할퀴는 소리 */
    static void clawMarks(ServerLevel level, LivingEntity e, Jobs.Job j, int n) {
        for (int i = 0; i < n; i++) {
            int ii = i;
            RuneKit.later(i * 2, () -> {
                if (e.isRemoved()) return;
                double s = 0.85 + Kit.bodyPad(e) * 0.5;
                Vec3 b = V.up(Kit.bodyPoint(e), (ii - (n - 1) / 2.0) * 0.18);
                JobFx.claw4(level, b, s, (ii % 2 == 0 ? -1 : 1) * (18 + ii * 9), 0.34, Opt.empty(), JobKit.core(j), JobFx.inner(j));
                SkillFx.play(level, "entity.player.attack.sweep", b, 1.4 + ii * 0.12, 0.45);
            });
        }
        SkillFx.sparks(level, Kit.bodyPoint(e), JobKit.core(j), 6, 3.5);
    }

    // ------------------------------------------------------------ 겁먹음 (달밤의 포효)

    /** 겁먹음: ticks 동안 이 적이 플레이어에게 주는 피해 × (1 − cut) (센 것 · 오래 가는 것) */
    static void fear(LivingEntity e, double cut, int ticks) {
        if (e == null || e.isRemoved()) return;
        long until = Tasks.now() + ticks;
        Fear f = FEARS.get(e.getUUID());
        if (f != null && f.until() > Tasks.now() && f.cut() > cut + 1e-9) until = Math.max(until, f.until());
        FEARS.put(e.getUUID(), new Fear(e, until, f != null && f.until() > Tasks.now() ? Math.max(cut, f.cut()) : cut));
    }

    /** 이 적의 겁먹음 몫 (없으면 0) */
    public static double fearOf(Entity e) {
        if (e == null || FEARS.isEmpty()) return 0;
        Fear f = FEARS.get(e.getUUID());
        return f == null || f.until() <= Tasks.now() ? 0 : f.cut();
    }

    /** 겁먹음 표시 (서버 틱마다 — JobTraits): 끝난 것을 지우고, 12틱마다 머리 위 양옆에 떨어지는 식은땀 둘 · 떨림 */
    static void tickFears(long now) {
        if (FEARS.isEmpty()) return;
        FEARS.values().removeIf(f -> f.until() <= now || f.e().isRemoved() || !f.e().isAlive());
        if (now % 12 != 0) return;
        for (Fear f : FEARS.values()) {
            if (f.until() - now < 6 || !RuneKit.fxQuota("jobFear", 8) || !(f.e().level() instanceof ServerLevel level)) continue;
            Vec3 over = RuneKit.overHead(f.e(), 0.25);
            Vec3 r = V.rightOf(Kit.facingOf(f.e()));
            double w = f.e().getBbWidth() * 0.5 + 0.18;
            for (int s = -1; s <= 1; s += 2) {
                JobTraits5.drop(level, V.add(over, r, s * w), V.DOWN, 0.1, SWEAT, 0.5, Opt.of("speed", 1.6, "fade", 0.4, "vel", RuneKit.entVel(f.e())));
            }
        }
    }

    // ------------------------------------------------------------ 틱 · 태세 연출 · 게이지

    /** 4틱마다 (JobTraits.tick): 곰의 화신이 끝남 · 곰 · 야성이 줄어듦 · 사냥감 표시 */
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
        if (!LY.equals(j.id())) return;
        if (st.stacks > 0 && !JobTraits.stanceOn(sp, NIGHT) && now - st.lastGain >= trait(j, "decay", 60) && now - st.lastDecay >= 20) {
            st.stacks--;
            st.lastDecay = now;
            applySpeed(sp, j, st);
            JobTraits.sendGauge(sp, false);
        }
        // 사냥감 표시 (16틱마다): 사냥감 머리 위에 옅은 붉은 발톱 자국 (야성이 있을 때만 — 쓴 사람에게만 보이게는 못 함: 옅게)
        if (now % 16 == 0 && st.stacks > 0 && st.prey != null && sp.level().getEntity(st.prey) instanceof LivingEntity le && le.isAlive() && RuneKit.fxQuota("jobPrey", 4)) {
            Vec3 over = RuneKit.overHead(le, 0.55);
            JobFx.claw4(sp.level(), over, 0.22, 0, 0.6, Opt.of("vel", RuneKit.entVel(le), "alpha", 0.55 + 0.04 * st.stacks), JobKit.core(j), JobFx.inner(j));
        }
    }

    /** 곰의 화신 동안 8틱마다: 몸을 감싼 곰 (3인칭 · 다른 사람 — 포효 장면을 이어 돌림) · 몸의 주황 기운 · 1인칭 손의 불티 */
    private static void bearAura(ServerPlayer sp, Jobs.Job j) {
        ServerLevel level = sp.level();
        Vec3 c = V.up(V.add(sp.position(), Kit.facingOf(sp), -0.35), 1.55);
        JobFx.bear(level, c, 1.75, 0.46, 3, 4, true, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.55, "coreAlpha", 0.7), bearGlow(j), bearCore(j));
        JobFx.aura(sp, j, 2.6, 0.5, 0.5);
        RuneKit.Hand h = RuneKit.handOf(sp);
        SkillFx.fx(level, "rpg:arrow_spark", V.up(h.hand(), 0.1), Opt.of("dir", V.UP, "speed", 1.6, "size", 0.13, "grow", 0.8, "life", 0.35, "color", JobKit.glow(j),
                "alpha", 1.0));
    }

    /** 태세 연출 (8틱마다 — JobTraits.stanceFx): 늑대의 밤 = 머리 위로 울부짖는 은빛 늑대 · 붉은 눈빛 · 손의 발톱 빛. 그렸으면 true */
    static boolean stanceFx(ServerPlayer sp, Jobs.Job j, String stance) {
        if (!NIGHT.equals(stance)) return false;
        ServerLevel level = sp.level();
        double t = Tasks.now() * 0.05;
        Vec3 back = V.add(sp.position(), Kit.facingOf(sp), -0.45);
        JobFx.wolf(level, V.up(back, 2.55 + 0.05 * Math.sin(t)), V.UP, 1.05, 0.46, j, JobFx.hide1p(sp).with("solo", 43.0, "vel", RuneKit.bodyVel(sp), "alpha", 0.6,
                "coreAlpha", 0.85));
        eyeGlint(sp, j, 0.45);
        RuneKit.Hand h = RuneKit.handOf(sp);
        SkillFx.fx(level, "rpg:arrow_spark", V.up(h.hand(), 0.15), Opt.of("dir", V.UP, "speed", 1.3, "size", 0.12, "grow", 0.6, "life", 0.4, "color", JobKit.core(j),
                "alpha", 1.0));
        return true;
    }

    /** 게이지 값: 라이칸 = 야성 (워로드는 JobTraitsMore 의 분노) */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        return j != null && LY.equals(j.id()) ? ferocity(sp) : 0;
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
