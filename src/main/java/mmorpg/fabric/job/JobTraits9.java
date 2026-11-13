package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
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
 * 0.51.0 2차 직업 — 궁수 각성 스나이퍼 · 반전 배틀 아처 특성 · 태세 (서버 — jobs.json advance, 재설계안 2판 2026-11-06 — 섀도 헌터 대신 배틀 아처):
 * <ul>
 * <li>스나이퍼 「호흡」 (쌓임 · 활 평타 +4% · 사거리 +1칸은 궁수와 같음 — JobTraitsMore, 최대 trait.max = 8): 호흡 8 에서 쏜 활 평타는 급소 사격 (치명타 확정 · 꿰뚫음 ·
 * 호흡 snipeUse 씀 — JobTraitsMore, 쏠 때의 흰 섬광은 {@link #snipeFx}). 헌터스 마크 (태세): 활 평타가 같은 적을 every 번째 맞힐 때마다 그 평타에 은빛 화살을 더함
 * (그 적 최대 체력 × pct — 위력 × min ~ cap, RpgCombat.addScaledBonusHook) · 맞힌 수만큼 그 적 머리 위 은빛 표식</li>
 * <li>배틀 아처 「난사」 (1차와 거꾸로): 움직이며 (4틱 안에 움직였으면) 쏜 활 평타는 세 갈래 — 둘레 sideRange 칸의 다른 적 둘에게도 한 발씩 (RpgCombat.extraArrow — 평타 ×
 * side, 스톰 스트라이드 동안은 서 있어도 · 스킬의 side). 내 둘레 near 칸 안의 적에게 주는 피해 + nearBonus · far 칸 밖 − farCut (활 평타 RpgCombat.rangedMult · 직업 스킬
 * JobKit.hit). 게이지 = 다음 활 평타의 갈래 (1 · 3). 스톰 스트라이드 (태세): 이동 속도 + speed. 팰컨 다이브: 내려앉으며 쏜 적이 reset 틱 안에 쓰러지면 재사용 대기가 바로 돌아옴</li>
 * </ul>
 */
public final class JobTraits9 {
    private JobTraits9() {}

    static final String SN = "sniper";
    static final String BA = "battlearcher";
    static final String MARK = "sniper_mark";
    static final String STRIDE = "battlearcher_stride";
    static final String DIVE = "battlearcher_dive";
    /** 급소 사격의 흰 섬광 */
    static final double[] SNIPE = {1.0, 0.98, 0.84};
    /** 은빛 (헌터스 마크의 은빛 화살 · 표식) */
    static final double[] SILVER = {0.82, 0.9, 1.0};
    /** 은빛 화살의 짙은 테 (밝은 하늘 · 풀 위에서도 보이게) */
    static final double[] STEEL = {0.36, 0.46, 0.66};
    /** 헌터스 마크 평타 몫의 열쇠 (숫자 줄) */
    private static final String KEY_MARK = "job_sniper_mark";

    private static final Identifier STRIDE_SPEED = Mmorpg.id("job_stride_speed");

    // ------------------------------------------------------------ 상태

    static final class State {
        long lastMove = -1000;
        Vec3 lastPos;
        /** 배틀 아처 게이지 (다음 활 평타의 갈래 — 1 · 3) */
        int gauge = 1;
        boolean speedOn;
        /** 헌터스 마크: 지금 맞히는 적 · 이어 맞힌 수 */
        UUID markFoe;
        int markHits;
        LivingEntity markEnt;
        /** 팰컨 다이브: 내려앉으며 쏜 적 · 재사용 대기가 돌아오는 끝 틱 */
        final Set<UUID> diveFoes = new HashSet<>();
        long diveUntil;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    static void init() {
        java.util.function.BiPredicate<ServerPlayer, Weapon> sc = RpgCombat.sureCrit;
        RpgCombat.sureCrit = (p, w) -> sc.test(p, w) || JobTraitsMore.snipeCrit(p, w);
        RpgCombat.MeleeMult rm = RpgCombat.rangedMult;
        RpgCombat.rangedMult = (p, w, v) -> rm.mult(p, w, v) * basicRange(p, w, v);
        RpgCombat.addScaledBonusHook(JobTraits9::markBonus);
        ServerLivingEntityEvents.AFTER_DEATH.register(JobTraits9::onDeath);
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
            st.markFoe = null;
            st.markHits = 0;
            st.markEnt = null;
            st.diveFoes.clear();
        }
    }

    // ------------------------------------------------------------ 스나이퍼: 급소 사격 · 헌터스 마크

    /**
     * 천벌의 화살을 쏨 (JobTraitsMore.onFired — 심판 8, 2026-11-14): 활 앞 흰 반짝임 · 화살 길을 따라 흰 금빛 줄 · 날아가는 빛 · 화살이 닿는 자리에 하늘의 빛기둥 · 맑은 소리
     */
    static void snipeFx(ServerPlayer sp, Jobs.Job j, Vec3 from, Vec3 dir, double dist) {
        ServerLevel level = sp.level();
        Vec3 bow = V.add(RuneKit.bowHand(sp), dir, 0.5);
        ArrowFx.glint(level, bow, 0.5, 0.25, SNIPE, Opt.of("vel", RuneKit.bodyVel(sp)));
        double len = Math.max(2, dist);
        Vec3 end = V.add(from, dir, len);
        RuneKit.streak(level, V.add(from, dir, 1.2), end, SNIPE, 0.06, 0.3, 1.0, 0);
        RuneKit.streak(level, V.add(from, dir, 1.2), end, JobKit.glow(j), 0.16, 0.25, 0.55, 0);
        RpgCombat.Shot shot = RpgCombat.arrowShot(len);
        RuneKit.flyStreak(level, V.add(from, dir, 1.0), end, len / Math.max(0.05, shot.life()), JobKit.glow(j), SNIPE, 0.9, 0.24);
        int t = Math.max(1, (int) Math.round(shot.life() * 20));
        String life = RuneKit.lifeToken(sp);
        RuneKit.later(t, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 g = new Vec3(end.x, RuneKit.groundAt(level, end, end.y - 1.5), end.z);
            RuneKit.godRays(sp, level, g, 0, 1, JobKit.glow(j), JobKit.core(j), Opt.of("h", 9.0, "life", 0.4, "skipNear", true));
            ArrowFx.glint(level, end, 0.7, 0.25, RuneKit.WHITE, Opt.empty());
        });
        SkillFx.play(level, "item.trident.hit", sp.position(), 1.8, 0.7);
        SkillFx.play(level, "block.amethyst_block.chime", sp.position(), 2.0, 0.6);
    }

    /** 헌터스 마크를 씀: 이어 맞힌 수를 처음부터 */
    static void markStart(ServerPlayer sp) {
        State st = of(sp);
        st.markFoe = null;
        st.markHits = 0;
        st.markEnt = null;
    }

    /** 시험: 헌터스 마크로 지금 적에게 이어 맞힌 수 */
    public static int markHits(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.markHits;
    }

    /** 시험 (사진첩): 헌터스 마크로 그 적에게 이어 맞힌 수를 정함 (다음 활 평타가 n + 1 번째) */
    public static void testMarkHits(ServerPlayer sp, LivingEntity e, int n) {
        State st = of(sp);
        st.markFoe = e.getUUID();
        st.markEnt = e;
        st.markHits = Math.max(0, n);
    }

    /**
     * 평타에 더할 것 (RpgCombat.addScaledBonusHook): 헌터스 마크 동안 활 평타가 같은 적을 every 번째 맞히면 은빛 화살 — 그 적 최대 체력 × pct (위력 × min ~ cap) 를 더함
     * (그 평타의 숫자 옆에 큰 숫자). 다른 적을 맞히면 처음부터
     */
    private static RpgCombat.Bonus markBonus(ServerPlayer sp, LivingEntity victim, Weapon w, double dmg) {
        if (w == null || victim == null || !"bow".equals(w.type()) || !JobTraits.stanceOn(sp, MARK)) return null;
        Jobs.Job j = jobIf(sp, SN);
        if (j == null) return null;
        Jobs.Skill k = j.skill(MARK);
        State st = of(sp);
        if (!victim.getUUID().equals(st.markFoe)) {
            st.markFoe = victim.getUUID();
            st.markHits = 0;
        }
        st.markEnt = victim;
        st.markHits++;
        int every = k == null ? 3 : Math.max(2, (int) k.num("every", 3));
        if (st.markHits < every) {
            if (victim.level() instanceof ServerLevel level) pips(level, victim, st.markHits, every, j);
            return null;
        }
        st.markHits = 0;
        double power = JobSystem.power(sp, j);
        double pct = k == null ? 0.05 : k.num("pct", 0.05);
        double lo = power * (k == null ? 1 : k.num("min", 1));
        double hi = power * (k == null ? 3 : k.num("cap", 3));
        double add = Math.max(lo, Math.min(hi, victim.getMaxHealth() * pct));
        if (victim.level() instanceof ServerLevel level) silverBolt(sp, level, victim, j);
        return new RpgCombat.Bonus(add, "", KEY_MARK, DamageNumbers.Style.JOB_BIG, null, JobKit.grad(j));
    }

    /** 맞힌 수 (n / every) 만큼 적 머리 위 빛의 표식 (2026-11-14 — 반짝임 · 나란히, 켜진 것은 크고 진하게) */
    private static void pips(ServerLevel level, LivingEntity e, int n, int every, Jobs.Job j) {
        Vec3 head = RuneKit.overHead(e, 0.5);
        double pad = Kit.bodyPad(e);
        for (int i = 0; i < every - 1; i++) {
            boolean lit = i < n;
            Vec3 at = V.add(head, new Vec3(1, 0, 0), (i - (every - 2) / 2.0) * (0.62 + pad * 0.2));
            ArrowFx.glint(level, at, (lit ? 0.4 : 0.22) + pad * 0.08, 0.6, lit ? JobKit.core(j) : JobKit.glow(j), Opt.of("vel", RuneKit.entVel(e), "fade", 0.3, "alpha",
                    lit ? 1.0 : 0.55, "spin", 45.0, "spinRate", 60.0, "grow", 0.0));
        }
        SkillFx.play(level, "block.amethyst_block.hit", e.position(), 1.4 + n * 0.2, 0.6);
    }

    /**
     * 셋째 화살 (2026-11-14 심판의 화살 — 은빛 화살 대신): 그 적 위 하늘에서 3D 금빛 화살이 내리꽂힘 (2틱) · 하늘의 빛기둥 · 몸 가운데 큰 반짝임 · 발밑 고리 · 세라핌의
     * 날개가 크게 내려침 · 맑은 쇳소리
     */
    private static void silverBolt(ServerPlayer sp, ServerLevel level, LivingEntity e, Jobs.Job j) {
        Vec3 c = Kit.bodyPoint(e);
        double pad = Kit.bodyPad(e);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        ArrowFx.fallArrow(level, e.position(), 2.6, e.getBbHeight() + 5, 2, 12, glow, SNIPE);
        RuneKit.godRays(sp, level, e.position(), 0, 1, glow, core, Opt.of("h", 11.0, "life", 0.45, "skipNear", true));
        ArrowFx.glint(level, c, 1.0 + pad * 0.3, 0.35, RuneKit.WHITE, Opt.empty());
        RuneKit.zonePulse(level, e.position(), glow, 1.6 + pad, 0.35);
        SkillFx.burst(level, c, core, 1.1 + pad * 0.3, 0.3);
        SkillFx.sparks(level, c, SNIPE, 14, 5);
        SniperSkills.seraphBeat(sp);
        SkillFx.play(level, "item.trident.hit", c, 1.6, 0.9);
        SkillFx.play(level, "block.amethyst_block.break", c, 1.9, 0.5);
    }

    // ------------------------------------------------------------ 배틀 아처: 난사

    /** 이 사람이 4틱 안에 움직였는지 (걷기 · 달리기 — 아는 움직임이 0.04칸 넘게) */
    private static boolean moved(ServerPlayer sp, State st) {
        Vec3 m = sp.getKnownMovement();
        return Math.hypot(m.x, m.z) > 0.04 || Tasks.now() - st.lastMove <= 4;
    }

    /** 시험: 방금 움직인 것으로 (4틱 안에 쏜 활 평타가 세 갈래 — 시험 몸은 걷지 않음) */
    public static void testMoved(ServerPlayer sp) {
        of(sp).lastMove = Tasks.now();
    }

    /** 다음 활 평타가 세 갈래인지 (움직였거나 스톰 스트라이드) */
    static boolean splitting(ServerPlayer sp) {
        if (jobIf(sp, BA) == null) return false;
        return JobTraits.stanceOn(sp, STRIDE) || moved(sp, of(sp));
    }

    /**
     * 활 평타를 쏨 (JobTraitsMore.onFired — 배틀 아처): 세 갈래면 둘레 sideRange 칸의 다른 적 둘 (가까운 차례) 에게도 한 발씩 (평타 × side — 스톰 스트라이드면 스킬의 side) ·
     * 활 앞 하늘빛 갈래 불티
     */
    static void onFired(ServerPlayer sp, Jobs.Job j, Weapon w, LivingEntity target, Vec3 from, Vec3 dir, double dist) {
        State st = of(sp);
        boolean stride = JobTraits.stanceOn(sp, STRIDE);
        if (!stride && !moved(sp, st)) return;
        double ratio = trait(j, "side", 0.5);
        if (stride) {
            Jobs.Skill k = j.skill(STRIDE);
            ratio = k == null ? 1.0 : k.num("side", 1.0);
        }
        double range = trait(j, "sideRange", 5);
        List<LivingEntity> others = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, sp.position(), range, 4)) if (e != target && Kit.splashFoe(sp, e)) others.add(e);
        others.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(sp)));
        ServerLevel level = sp.level();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        Vec3 bow = V.add(RuneKit.bowHand(sp), dir, 0.45);
        // 2026-11-14 분신: 섀도 댄스면 곁의 3D 분신 둘이 함께 쏨 (재빨리 당겨 놓음), 아니면 양옆에 그림자 분신이 번쩍 솟아 쏨 (2D — 평타마다 몹을 세우지 않게)
        List<mmorpg.fabric.rune.RuneMob> dancers = ShadeArchers.dancers(sp);
        Vec3 f = Kit.facingOf(sp);
        Vec3[] side = new Vec3[2];
        for (int s = 0; s < 2; s++) {
            if (s < dancers.size()) {
                mmorpg.fabric.rune.RuneMob m = dancers.get(s);
                m.anim("burst");
                side[s] = ShadeArchers.bowOf(m);
            } else {
                Vec3 feet = V.add(V.add(sp.position(), V.rightOf(f), s == 0 ? 1.2 : -1.2), f, -0.3);
                RuneKit.shadeClone(sp, level, feet, f, 1, ShadeArchers.DARK, glow, Opt.of("life", 0.35, "fade", 0.6, "alpha", 0.8));
                side[s] = V.add(V.up(feet, 1.35), f, 0.6);
            }
        }
        int n = 0;
        for (LivingEntity e : others) {
            if (n >= 2) break;
            int t = RpgCombat.extraArrow(sp, e, w, ratio);
            if (t < 0) continue;
            Vec3 src = side[n];
            n++;
            Vec3 to = Kit.bodyPoint(e);
            RuneKit.streak(level, src, to, glow, 0.07, 0.2, 0.75, 0);
            RuneKit.flyArrow(level, src, to, V.len(V.sub(to, src)) / Math.max(0.05, t / 20.0), glow, core, 0.6, 0.0);
        }
        // 갈래 불티 (쏘는 쪽으로 세 갈래 — 다른 적이 없어도 보임)
        for (int s = -1; s <= 1; s++) {
            Vec3 d = V.unit(V.add(dir, V.rightOf(V.flat(dir)), s * 0.32));
            SkillFx.fx(level, "rpg:arrow_spark", bow, Opt.of("dir", d, "speed", 5.0, "size", 0.12, "grow", 0.5, "life", 0.22, "color", s == 0 ? core : glow,
                    "alpha", 1.0));
        }
    }

    /** 활 평타 배율 (RpgCombat.rangedMult): 배틀 아처 활 — 가까운 적 + · 먼 적 − */
    private static double basicRange(ServerPlayer sp, Weapon w, LivingEntity victim) {
        if (w == null || victim == null || !"bow".equals(w.type())) return 1;
        Jobs.Job j = jobIf(sp, BA);
        return j == null ? 1 : distMult(sp, victim, j);
    }

    /** 직업 스킬 배율 (JobKit.hit): 배틀 아처 — 가까운 적 + · 먼 적 − (다른 직업은 1) */
    static double rangeMult(ServerPlayer sp, LivingEntity e, Jobs.Job j) {
        if (j == null || e == null || !BA.equals(j.id())) return 1;
        return distMult(sp, e, j);
    }

    /** 거리 배율: 내 몸에서 적 몸까지 (가로 — 몸 여유를 뺌) near 칸 안 1 + nearBonus · far 칸 밖 1 − farCut · 그 사이 1 */
    static double distMult(ServerPlayer sp, LivingEntity e, Jobs.Job j) {
        double d = Math.max(0, V.hdist(sp.position(), e.position()) - Kit.bodyPad(e));
        if (d <= trait(j, "near", 3)) return 1 + trait(j, "nearBonus", 0.2);
        if (d > trait(j, "far", 10)) return 1 - trait(j, "farCut", 0.3);
        return 1;
    }

    /** 스톰 스트라이드를 씀: 이동 속도 + speed · 게이지 3 */
    static void strideStart(ServerPlayer sp, Jobs.Job j, Jobs.Skill k) {
        State st = of(sp);
        speedMod(sp, k == null ? 0.25 : k.num("speed", 0.25));
        st.speedOn = true;
        st.gauge = 3;
        JobTraits.sendGauge(sp, false);
    }

    private static void speedMod(ServerPlayer sp, double amount) {
        AttributeInstance a = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null) return;
        if (amount > 0) a.addOrUpdateTransientModifier(new AttributeModifier(STRIDE_SPEED, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        else a.removeModifier(STRIDE_SPEED);
    }

    /** 시험: 걸린 스톰 스트라이드 이동 속도 (없으면 0) */
    public static double speedBonus(ServerPlayer sp) {
        AttributeInstance a = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeModifier m = a == null ? null : a.getModifier(STRIDE_SPEED);
        return m == null ? 0 : m.amount();
    }

    /** 팰컨 다이브가 내려앉으며 쏜 적: ticks 틱 안에 하나라도 쓰러지면 재사용 대기가 바로 돌아옴 */
    static void diveFoes(ServerPlayer sp, List<LivingEntity> foes, int ticks) {
        State st = of(sp);
        st.diveFoes.clear();
        for (LivingEntity e : foes) st.diveFoes.add(e.getUUID());
        st.diveUntil = Tasks.now() + ticks;
    }

    /** 쓰러짐: 팰컨 다이브로 쏜 적이면 그 사람의 팰컨 다이브 재사용 대기를 끝냄 (하늘빛 깃털 · 맑은 소리) */
    private static void onDeath(LivingEntity e, DamageSource src) {
        if (e == null || STATES.isEmpty() || !(e.level() instanceof ServerLevel level)) return;
        long now = Tasks.now();
        for (Map.Entry<UUID, State> en : STATES.entrySet()) {
            State st = en.getValue();
            if (st.diveUntil < now || !st.diveFoes.remove(e.getUUID())) continue;
            ServerPlayer sp = level.getServer().getPlayerList().getPlayer(en.getKey());
            Jobs.Job j = sp == null ? null : jobIf(sp, BA);
            if (j == null) continue;
            st.diveFoes.clear();
            st.diveUntil = 0;
            if (JobSkills.cooldownLeft(sp, DIVE) <= 0) continue;
            JobSkills.clearCooldown(sp, DIVE);
            ServerLevel lv = sp.level();
            ShadeArchers.puff(lv, sp.position(), JobKit.glow(j), 1.0);
            RuneKit.zonePulse(lv, sp.position(), JobKit.glow(j), 1.6, 0.35);
            SkillFx.sparks(lv, V.up(sp.position(), 1.0), JobKit.core(j), 10, 4);
            SkillFx.play(lv, "block.amethyst_block.chime", sp.position(), 1.7, 0.8);
            SkillFx.play(lv, "mob.endermen.portal", sp.position(), 1.6, 0.6);
            JobSystem.note(sp, "§5섀도 스위치 §7— 다시 쓸 수 있음");
        }
    }

    /** 시험: 팰컨 다이브 재사용 대기가 돌아오는 적 수 */
    public static int diveWatch(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null || st.diveUntil < Tasks.now() ? 0 : st.diveFoes.size();
    }

    // ------------------------------------------------------------ 틱 · 태세 · 게이지

    /** 4틱마다 (JobTraits.tick — 사람마다): 배틀 아처 — 움직임 · 게이지 (갈래 1 · 3) · 스톰 스트라이드가 끝나면 이동 속도를 거둠 */
    static void tick(ServerPlayer sp, Jobs.Job j, long now) {
        if (!BA.equals(j.id())) {
            if (ShadeArchers.dancerCount(sp) > 0) ShadeArchers.danceEnd(sp, JobKit.glow(j)); // 직업이 바뀜
            return;
        }
        State st = of(sp);
        Vec3 p = sp.position();
        if ((st.lastPos != null && Math.hypot(p.x - st.lastPos.x, p.z - st.lastPos.z) > 0.2) || Math.hypot(sp.getKnownMovement().x, sp.getKnownMovement().z) > 0.04) st.lastMove = now;
        st.lastPos = p;
        boolean stride = JobTraits.stanceOn(sp, STRIDE);
        if (st.speedOn && !stride) {
            speedMod(sp, 0);
            st.speedOn = false;
        }
        if (!stride && ShadeArchers.dancerCount(sp) > 0) ShadeArchers.danceEnd(sp, JobKit.glow(j)); // 섀도 댄스가 끝남 — 분신이 연기로 꺼짐
        int g = stride || now - st.lastMove <= 4 ? 3 : 1;
        if (g != st.gauge) {
            st.gauge = g;
            JobTraits.sendGauge(sp, false);
        }
    }

    /**
     * 태세 연출 (8틱마다 — JobTraits.stanceFx): 헌터스 마크 = 활 앞 은빛 반짝임 · 화살통에서 솟는 은빛 / 스톰 스트라이드 = 발밑 폭풍 바람 고리 · 몸 뒤로 흐르는 바람 줄 ·
     * 하늘빛 불티. 그렸으면 true
     */
    static boolean stanceFx(ServerPlayer sp, Jobs.Job j, String stance) {
        ServerLevel level = sp.level();
        if (MARK.equals(stance)) {
            // 2026-11-14 세라핌: 활 앞 반짝임 · 등의 날개에서 솟는 빛 (날개 여섯 · 빛 고리는 SniperSkills — LightWings)
            RuneKit.rfx(level, "prop", V.add(RuneKit.bowHand(sp), sp.getViewVector(1F), 0.35), JobFx.hide1p(sp).with("size", 0.16, "life", 0.4, "cell",
                    (double) RuneKit.RFX.get("glint")[0], "spin", 0.0, "spinRate", 180.0, "color", JobKit.core(j), "fade", 0.4, "vel", RuneKit.bodyVel(sp)));
            Vec3 back = V.up(V.add(sp.position(), Kit.facingOf(sp), -0.35), 1.5);
            SkillFx.fx(level, "rpg:arrow_spark", back, Opt.of("dir", V.UP, "speed", 1.6, "size", 0.12, "grow", 0.6, "life", 0.4, "color", JobKit.glow(j), "alpha", 0.9));
            State st = STATES.get(sp.getUUID());
            if (st != null && st.markEnt != null && st.markHits > 0 && !st.markEnt.isRemoved() && st.markEnt.isAlive() && st.markEnt.level() == level) {
                Jobs.Skill k = j.skill(MARK);
                pips(level, st.markEnt, st.markHits, k == null ? 3 : Math.max(2, (int) k.num("every", 3)), j);
            }
            return true;
        }
        if (STRIDE.equals(stance)) {
            // 2026-11-14 섀도 댄스: 발밑 그림자 연기 · 보랏빛 불티 (곁의 분신 둘은 ShadeArchers.dance)
            JobFx.smoke(level, V.up(sp.position(), 0.3), 0.8, ShadeArchers.DARK, JobKit.glow(j), 0.4, JobFx.hide1p(sp).with("alpha", 0.75, "vel", RuneKit.bodyVel(sp)));
            JobFx.speed(sp, j, 0.4, 0.75, RuneKit.bodyVel(sp));
            Vec3 c = V.up(sp.position(), 0.4);
            for (int i = 0; i < 2; i++) {
                double a = Kit.rand() * Math.PI * 2;
                Vec3 p = V.add(c, new Vec3(Math.cos(a), 0, Math.sin(a)), 0.6);
                SkillFx.fx(level, "rpg:arrow_spark", p, Opt.of("dir", V.unit(new Vec3(Math.cos(a), 0.6, Math.sin(a))), "speed", 2.4, "size", 0.12, "grow", 0.6, "life", 0.3,
                        "color", JobKit.core(j), "alpha", 1.0));
            }
            return true;
        }
        return false;
    }

    /** 게이지 값: 배틀 아처 = 다음 활 평타의 갈래 (1 · 3) */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        if (j == null || !BA.equals(j.id())) return 0;
        State st = STATES.get(sp.getUUID());
        return st == null ? 1 : st.gauge;
    }
}
