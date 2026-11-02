package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.Mmorpg;
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
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 2차 직업 — 궁수 각성 에길(스나이퍼) · 반전 미르크비드(섀도 헌터) 특성 · 태세 (서버 — jobs.json advance):
 * <ul>
 * <li>스나이퍼 「호흡」 (쌓임 · 활 평타 +4% · 사거리 +1칸은 궁수와 같음 — JobTraitsMore, 최대 trait.max = 8): 호흡 8 에서 쏜 활 평타는 급소 사격 — 그 화살이 닿는 동안
 * (snipeFor 틱) 치명타 확정 (RpgCombat.sureCrit) · 꿰뚫음 (snipeWidth) · 호흡 snipeUse 씀. 쏠 때 활 앞 흰 섬광 · 화살 길을 따라 흰 금빛 줄. 사과 겨누기 (태세): 호흡 가득 ·
 * 줄지 않음 · 급소 사격이 호흡을 쓰지 않음 (모든 화살이 급소 사격), 8틱마다 앞 markRange 칸 적 (marks 명까지) 의 머리 위에 금빛 약점 과녁</li>
 * <li>섀도 헌터 「추격」 (1차와 거꾸로): 움직이며 (4틱 안에 움직였으면) 활을 쏘면 추격 +1 (최대 trait.max) · 멈춰 서 있으면 decay 틱마다 하나씩 줄어듦, 하나당 이동 속도
 * + speedPer. 활 평타 · 검은 화살이 맞힌 적에 저주 +1 (최대 curseMax · curseFor 틱) — 저주 하나당 그 적이 받는 피해 + cursePer (누구에게서든 — RpgCombat.takenMult).
 * 그림자 구르기: 구르는 동안 받는 피해 없음 (RpgHealth.sourceReduce). 그믐밤 (태세): 활 평타 치명타 + crit · 추격이 줄지 않음 · lose 틱마다 둘레 radius 칸 몹이 나를 놓침</li>
 * </ul>
 */
public final class JobTraits9 {
    private JobTraits9() {}

    static final String SN = "sniper";
    static final String SH = "shadowhunter";
    static final String APPLE = "sniper_apple";
    static final String MOONLESS = "shadowhunter_moonless";
    /** 급소 사격의 흰 섬광 */
    static final double[] SNIPE = {1.0, 0.98, 0.84};
    /** 어둠숲 (짙은 남색 — 그믐밤 · 저주 문양 바깥) */
    static final double[] DUSK = {0.12, 0.1, 0.34};
    /** 은빛 (검은 깃 화살의 심지 · 저주 문양의 테) */
    static final double[] SILVER = {0.86, 0.9, 1.0};

    private static final Identifier PURSUIT_SPEED = Mmorpg.id("job_pursuit_speed");

    // ------------------------------------------------------------ 상태

    static final class State {
        int pursuit;
        long lastMove = -1000;
        Vec3 lastPos;
        int still;
        long rollUntil;
        double speedSet;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    /** 저주 (적마다 하나 — 주인 · 쌓인 수 · 하나당 받는 피해 · 끝 틱) */
    record Curse(UUID owner, int stacks, double per, long until) {}

    private static final Map<UUID, Curse> CURSES = new HashMap<>();
    /** 저주가 걸린 적 (표시 · 받는 피해 — 몸을 찾으려고) */
    private static final Map<UUID, LivingEntity> CURSED = new HashMap<>();

    static void init() {
        java.util.function.BiPredicate<ServerPlayer, Weapon> sc = RpgCombat.sureCrit;
        RpgCombat.sureCrit = (p, w) -> sc.test(p, w) || JobTraitsMore.snipeCrit(p, w);
        java.util.function.ToDoubleFunction<LivingEntity> tm = RpgCombat.takenMult;
        RpgCombat.takenMult = e -> tm.applyAsDouble(e) * curseMult(e);
        java.util.function.ToDoubleBiFunction<ServerPlayer, DamageSource> sr = RpgHealth.sourceReduce;
        RpgHealth.sourceReduce = (p, src) -> Math.max(sr.applyAsDouble(p, src), rollReduce(p));
        RpgCombat.addCritBonusHook(JobTraits9::critAdd);
        RpgCombat.addWeaponHitHook(JobTraits9::onWeaponHit);
        ServerLivingEntityEvents.AFTER_DEATH.register(JobTraits9::onDeath);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATES.clear();
            CURSES.clear();
            CURSED.clear();
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
        speedMod(sp, 0);
        forgetCurses(sp.getUUID());
    }

    static void forget(UUID id) {
        STATES.remove(id);
        forgetCurses(id);
    }

    static void lifeEnded(UUID id) {
        State st = STATES.get(id);
        if (st != null) {
            st.pursuit = 0;
            st.rollUntil = 0;
        }
    }

    private static void forgetCurses(UUID owner) {
        CURSES.values().removeIf(c -> c.owner().equals(owner));
        CURSED.keySet().removeIf(id -> !CURSES.containsKey(id));
    }

    // ------------------------------------------------------------ 스나이퍼: 급소 사격 · 사과 겨누기

    /** 급소 사격을 쏨 (JobTraitsMore.onFired — 호흡 8): 활 앞 흰 섬광 · 약점 과녁 번쩍 · 화살 길을 따라 흰 금빛 줄 · 맑은 소리 */
    static void snipeFx(ServerPlayer sp, Jobs.Job j, Vec3 from, Vec3 dir, double dist) {
        ServerLevel level = sp.level();
        Vec3 bow = V.add(RuneKit.bowHand(sp), dir, 0.5);
        JobFx.weak(level, bow, 0.42, 4, 2, 0.22, false, Opt.of("vel", RuneKit.bodyVel(sp)), JobKit.glow(j), SNIPE);
        double len = Math.max(2, dist);
        Vec3 end = V.add(from, dir, len);
        RuneKit.streak(level, V.add(from, dir, 1.2), end, SNIPE, 0.06, 0.3, 1.0, 0);
        RuneKit.streak(level, V.add(from, dir, 1.2), end, JobKit.glow(j), 0.16, 0.25, 0.55, 0);
        SkillFx.play(level, "item.trident.hit", sp.position(), 1.8, 0.7);
        SkillFx.play(level, "block.amethyst_block.chime", sp.position(), 2.0, 0.6);
    }

    /** 사과 겨누기 동안 (8틱마다): 앞 markRange 칸 (보는 쪽 70°) 적 marks 명까지 머리 위에 금빛 약점 과녁 (조여 들었다 맥동) */
    private static void weakMarks(ServerPlayer sp, Jobs.Job j) {
        Jobs.Skill k = j.skill(APPLE);
        double range = k == null ? 24 : k.num("markRange", 24);
        int marks = k == null ? 6 : (int) k.num("marks", 6);
        ServerLevel level = sp.level();
        Vec3 eye = sp.getEyePosition();
        Vec3 look = Kit.facingOf(sp);
        List<LivingEntity> list = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, sp.position(), range, 6)) {
            Vec3 d = V.flat(V.sub(e.position(), sp.position()));
            if (V.dot(d, look) < Math.cos(Math.toRadians(70)) || !Kit.splashFoe(sp, e)) continue;
            list.add(e);
        }
        list.sort(java.util.Comparator.comparingDouble((LivingEntity e) -> e.position().distanceToSqr(eye)));
        for (int i = 0; i < Math.min(marks, list.size()); i++) {
            LivingEntity e = list.get(i);
            Vec3 head = RuneKit.overHead(e, 0.15);
            JobFx.weak(level, head, 0.34 + Kit.bodyPad(e) * 0.3, 1, 3, 0.45, true, Opt.of("vel", RuneKit.entVel(e), "fade", 0.25), JobKit.glow(j), SNIPE);
        }
    }

    // ------------------------------------------------------------ 섀도 헌터: 추격 · 저주

    /** 지금 추격 */
    public static int pursuit(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.pursuit;
    }

    /** 시험용: 추격을 정함 */
    public static void setPursuit(ServerPlayer sp, int n) {
        Jobs.Job j = jobIf(sp, SH);
        State st = of(sp);
        st.pursuit = Math.max(0, Math.min(j == null || j.trait() == null ? 5 : j.trait().max(), n));
        st.still = 0;
        applySpeed(sp, j, st);
        JobTraits.sendGauge(sp, false);
    }

    /** 추격 + n (달리며 쏘는 스킬 · 활 평타) */
    static void addPursuit(ServerPlayer sp, Jobs.Job j, int n) {
        State st = of(sp);
        int max = j.trait() == null ? 5 : Math.max(1, j.trait().max());
        if (st.pursuit >= max) {
            st.still = 0;
            return;
        }
        st.pursuit = Math.min(max, st.pursuit + n);
        st.still = 0;
        applySpeed(sp, j, st);
        // 발밑에서 은빛 바람이 뒤로 흩어짐 · 다 차면 맑은 소리
        ServerLevel level = sp.level();
        Vec3 back = V.scale(Kit.facingOf(sp), -1);
        SkillFx.fx(level, "rpg:arrow_spark", V.up(sp.position(), 0.3), Opt.of("dir", V.unit(V.add(back, V.UP, 0.4)), "speed", 2.2, "size", 0.12, "grow", 0.6, "life", 0.35,
                "color", SILVER, "alpha", 1.0));
        if (st.pursuit == max) SkillFx.play(level, "block.amethyst_block.chime", sp.position(), 1.5, 0.6);
        JobTraits.sendGauge(sp, false);
    }

    private static void applySpeed(ServerPlayer sp, Jobs.Job j, State st) {
        double want = j == null ? 0 : st.pursuit * trait(j, "speedPer", 0.03);
        if (Math.abs(want - st.speedSet) < 1e-9) return;
        st.speedSet = want;
        speedMod(sp, want);
    }

    private static void speedMod(ServerPlayer sp, double amount) {
        AttributeInstance a = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null) return;
        if (amount > 0) a.addOrUpdateTransientModifier(new AttributeModifier(PURSUIT_SPEED, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        else a.removeModifier(PURSUIT_SPEED);
    }

    /** 시험: 걸린 추격 이동 속도 (없으면 0) */
    public static double speedBonus(ServerPlayer sp) {
        AttributeInstance a = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeModifier m = a == null ? null : a.getModifier(PURSUIT_SPEED);
        return m == null ? 0 : m.amount();
    }

    /** 활 평타를 쏨 (JobTraitsMore.onFired — 섀도 헌터): 4틱 안에 움직였으면 추격 +1 · 화살 끝 은빛 */
    static void onFired(ServerPlayer sp, Jobs.Job j, Weapon w, LivingEntity target, Vec3 from, Vec3 dir, double dist) {
        State st = of(sp);
        if (Tasks.now() - st.lastMove <= 4 || moving(sp)) addPursuit(sp, j, 1);
        RuneKit.rfx(sp.level(), "prop", V.add(RuneKit.bowHand(sp), dir, 0.4), Opt.of("size", 0.14, "life", 0.25, "cell", (double) RuneKit.RFX.get("glint")[0], "spin",
                20.0, "spinRate", 200.0, "color", SILVER, "grow", 0.6, "fade", 0.5, "vel", RuneKit.bodyVel(sp)));
    }

    /** 이 틱에 움직이는 중인지 (걷기 · 달리기 — 아는 움직임이 0.04칸 넘게) */
    private static boolean moving(ServerPlayer sp) {
        Vec3 m = sp.getKnownMovement();
        return Math.hypot(m.x, m.z) > 0.04;
    }

    /** 무기로 맞힘: 섀도 헌터 활 평타 → 그 적에 저주 +1 */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        if (victim == null || w == null || !"bow".equals(w.type())) return;
        Jobs.Job j = jobIf(sp, SH);
        if (j != null) addCurse(sp, victim, 1, j);
    }

    /** 저주 + n (최대 curseMax · curseFor 틱 — 다시 걸면 새로): 머리 위 검은 저주 문양이 피어남 */
    static void addCurse(ServerPlayer sp, LivingEntity e, int n, Jobs.Job j) {
        if (e == null || e.isRemoved() || !e.isAlive() || n <= 0 || e instanceof net.minecraft.world.entity.player.Player) return;
        long now = Tasks.now();
        Curse c = CURSES.get(e.getUUID());
        int old = c == null || c.until() < now ? 0 : c.stacks();
        int max = (int) trait(j, "curseMax", 5);
        int stacks = Math.min(max, old + n);
        CURSES.put(e.getUUID(), new Curse(sp.getUUID(), stacks, trait(j, "cursePer", 0.02), now + (long) trait(j, "curseFor", 160)));
        CURSED.put(e.getUUID(), e);
        if (e.level() instanceof ServerLevel level && RuneKit.fxQuota("jobCurse", 8)) {
            JobFx.curse(level, RuneKit.overHead(e, 0.35), 0.24 + 0.04 * stacks, 0, 2, 0.25, false, Opt.of("vel", RuneKit.entVel(e)), DUSK, SILVER);
            SkillFx.play(level, "entity.wither.ambient", e.position(), 1.9, 0.25);
        }
    }

    /** 이 적의 저주 (없으면 0) */
    public static int curseOf(Entity e) {
        Curse c = e == null ? null : CURSES.get(e.getUUID());
        return c == null || c.until() < Tasks.now() ? 0 : c.stacks();
    }

    /** 시험용: 저주를 정함 (주인 sp) */
    public static void setCurse(ServerPlayer sp, LivingEntity e, int n) {
        Jobs.Job j = jobIf(sp, SH);
        if (j == null || e == null) return;
        CURSES.remove(e.getUUID());
        if (n > 0) addCurse(sp, e, n, j);
    }

    /** 받는 피해 배율: 저주 하나당 + per */
    static double curseMult(LivingEntity e) {
        if (CURSES.isEmpty() || e == null) return 1;
        Curse c = CURSES.get(e.getUUID());
        if (c == null) return 1;
        if (c.until() < Tasks.now()) {
            CURSES.remove(e.getUUID());
            CURSED.remove(e.getUUID());
            return 1;
        }
        return 1 + c.stacks() * c.per();
    }

    /** 저주 걸린 적 (주인 sp — 둘레 radius 칸 · 살아 있는 것) */
    static List<LivingEntity> cursedNear(ServerPlayer sp, Vec3 at, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        long now = Tasks.now();
        for (Map.Entry<UUID, Curse> en : CURSES.entrySet()) {
            if (en.getValue().until() < now) continue;
            LivingEntity e = CURSED.get(en.getKey());
            if (e == null || e.isRemoved() || !e.isAlive() || e.level() != sp.level() || V.hdist(e.position(), at) > radius) continue;
            out.add(e);
        }
        return out;
    }

    // ------------------------------------------------------------ 그림자 구르기 · 그믐밤

    /** 그림자 구르기: ticks 틱 동안 받는 피해 없음 */
    static void rollFor(ServerPlayer sp, int ticks) {
        of(sp).rollUntil = Tasks.now() + ticks;
    }

    /** 시험: 구르는 중인지 */
    public static boolean rolling(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st != null && st.rollUntil > Tasks.now();
    }

    private static double rollReduce(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st != null && st.rollUntil > Tasks.now() && jobIf(sp, SH) != null ? 1 : 0;
    }

    /** 치명타 + : 그믐밤 동안 섀도 헌터 활 평타 */
    private static double critAdd(ServerPlayer sp, Weapon w, LivingEntity victim) {
        if (w == null || !"bow".equals(w.type()) || !JobTraits.stanceOn(sp, MOONLESS)) return 0;
        Jobs.Job j = jobIf(sp, SH);
        Jobs.Skill k = j == null ? null : j.skill(MOONLESS);
        return k == null ? 0 : JobUpgrades.num(sp, k, "crit", 0.2);
    }

    /** 그믐밤: 둘레 radius 칸 몹이 나를 놓침 (노리던 대상이 나면 잃음) — 놓친 수 */
    private static int loseMe(ServerPlayer sp, double radius) {
        int n = 0;
        AABB box = sp.getBoundingBox().inflate(radius, 3, radius);
        for (Mob m : sp.level().getEntitiesOfClass(Mob.class, box, x -> x.getTarget() == sp)) {
            m.setTarget(null);
            m.getNavigation().stop();
            n++;
            if (RuneKit.fxQuota("jobMoonlessLose", 4)) SkillFx.fx(sp.level(), "rpg:arrow_spark", RuneKit.overHead(m, 0.4), Opt.of("dir", V.UP, "speed", 0.8, "size",
                    0.2, "grow", 0.5, "life", 0.5, "color", SILVER, "alpha", 0.9));
        }
        return n;
    }

    // ------------------------------------------------------------ 죽음 (저주 번짐)

    private static void onDeath(LivingEntity e, DamageSource src) {
        if (e == null) return;
        ShadowhunterSkills.onDeath(e); // 저주 번짐 문양이 터지기 전에 쓰러짐 → 그 자리에서 바로 번짐
        CURSES.remove(e.getUUID());
        CURSED.remove(e.getUUID());
    }

    // ------------------------------------------------------------ 틱 · 태세 · 게이지

    /**
     * 4틱마다 (JobTraits.tick — 사람마다): 섀도 헌터 — 움직였으면 추격이 줄지 않음 (멈춰 서 있으면 decay 틱마다 하나씩 — 그믐밤 동안은 그대로), 8틱마다 내 저주가 걸린
     * 적 (8명까지) 머리 위 저주 문양 (쌓인 수만큼 크게)
     */
    static void tick(ServerPlayer sp, Jobs.Job j, long now) {
        if (!SH.equals(j.id())) return;
        State st = of(sp);
        Vec3 p = sp.position();
        boolean moved = (st.lastPos != null && Math.hypot(p.x - st.lastPos.x, p.z - st.lastPos.z) > 0.2) || moving(sp);
        st.lastPos = p;
        if (moved) {
            st.lastMove = now;
            st.still = 0;
        } else if (st.pursuit > 0 && !JobTraits.stanceOn(sp, MOONLESS)) {
            st.still += 4;
            int decay = (int) trait(j, "decay", 20);
            if (st.still >= decay) {
                st.still -= decay;
                st.pursuit--;
                applySpeed(sp, j, st);
                JobTraits.sendGauge(sp, false);
            }
        }
        if (now % 8 == 0 && !CURSES.isEmpty()) {
            ServerLevel level = sp.level();
            int n = 0;
            for (Map.Entry<UUID, Curse> en : CURSES.entrySet()) {
                Curse c = en.getValue();
                if (!c.owner().equals(sp.getUUID()) || c.until() < now) continue;
                LivingEntity e = CURSED.get(en.getKey());
                if (e == null || e.isRemoved() || !e.isAlive() || e.level() != level || e.distanceToSqr(sp) > 40 * 40 || n++ >= 8) continue;
                JobFx.curse(level, RuneKit.overHead(e, 0.35), 0.2 + 0.035 * c.stacks(), 1, 3, 0.45, true, Opt.of("vel", RuneKit.entVel(e), "fade", 0.25), DUSK, SILVER);
            }
        }
    }

    /**
     * 태세 연출 (8틱마다 — JobTraits.stanceFx): 사과 겨누기 = 앞 적들의 금빛 약점 과녁 · 활 앞 흰 반짝임 / 그믐밤 = 몸 둘레 짙은 남색 어둠 · 발밑 그림자 고리 ·
     * 둘레 몹이 나를 놓침 (lose 틱마다). 그렸으면 true
     */
    static boolean stanceFx(ServerPlayer sp, Jobs.Job j, String stance) {
        ServerLevel level = sp.level();
        if (APPLE.equals(stance)) {
            weakMarks(sp, j);
            RuneKit.rfx(level, "prop", V.add(RuneKit.bowHand(sp), sp.getViewVector(1F), 0.35), JobFx.hide1p(sp).with("size", 0.14, "life", 0.4, "cell",
                    (double) RuneKit.RFX.get("glint")[0], "spin", 0.0, "spinRate", 180.0, "color", SNIPE, "fade", 0.4, "vel", RuneKit.bodyVel(sp)));
            return true;
        }
        if (MOONLESS.equals(stance)) {
            Jobs.Skill k = j.skill(MOONLESS);
            double r = k == null ? 6 : k.num("radius", 6);
            JobFx.smoke(level, V.up(sp.position(), 0.6), r * 0.55, DUSK, SILVER, 0.6, JobFx.hide1p(sp).with("alpha", 0.5, "vel", RuneKit.bodyVel(sp)));
            JobFx.ring(level, V.up(sp.position(), 0.06), r, j, false, 0.5, Opt.of("alpha", 0.55, "vel", RuneKit.bodyVel(sp)));
            int lose = k == null ? 10 : Math.max(4, (int) k.num("lose", 10));
            if (Tasks.now() % lose < 8) loseMe(sp, r);
            return true;
        }
        return false;
    }

    /** 게이지 값: 섀도 헌터 = 추격 (최대 trait.max) */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        return j != null && SH.equals(j.id()) ? pursuit(sp) : 0;
    }
}
