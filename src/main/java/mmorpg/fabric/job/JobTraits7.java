package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Fx3d;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.SwingArea;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 2차 직업 — 마검사 각성 아케인 블레이드 (2026-11-05 룬 스미스 대신 — 이명에 맞춘 대장장이 컨셉을 버림) · 반전 소드 콜러 특성 · 태세 (재설계안 2판 —
 * claude/job-advance-redo.md):
 * <ul>
 * <li>아케인 블레이드 「마력 각인」 (쌓고 터지는 것은 마검사와 같음 — JobTraits.mark · markBonus, 최대 5): 각인이 터지면 둘레 spreadRadius 칸의 다른 적에게 마력
 * 폭발이 번짐 (위력 × markBurst × spread × 터진 수 · 마법). 다섯이 찬 적을 칼 평타로 치면 다섯이 한꺼번에 터짐 — 하늘에서 거대한 마력 대검이 내리꽂힘
 * ({@link #giantSword}). 스펠 오버드라이브 (태세): 칼 평타를 휘두를 때마다 마력 검기가 앞으로 날아가 길 위의 적을 벰 (위력 × line · 마법 — 평타로 맞힌 적은 평타에
 * 더한 몫 위력 × wave · 따로 숫자)</li>
 * <li>소드 콜러 「스스로 싸우는 칼」 (1차와 거꾸로 — 내가 베는 광역 → 칼이 스스로 베는 한 적): 칼 평타를 휘두르면 마력 칼이 어깨에서 날아가 겨눈 적 (range 칸 · 둘로
 * 나뉘어라 동안 + range) 을 대신 벰 — 평타 한 번 그대로 (RpgCombat.areaHit), 둘레 범위 피해는 없음 (SwingArea.single). 가까이 조준해 바닐라 평타로 맞힌 적은 칼이
 * 그 적을 베는 그림만. 같은 적을 이어 벨 때마다 쌓임 (최대 max — lapse 틱 못 베거나 다른 적을 베면 1부터) · 하나당 공격 속도 + per (RpgCombat.attackSpeed).
 * 둘로 나뉘어라 (태세): 칼 평타마다 둘째 칼이 같은 적을 X 자로 함께 벰 (평타에 더한 몫 — 위력 × echo · 직업 색 숫자)</li>
 * </ul>
 */
public final class JobTraits7 {
    private JobTraits7() {}

    static final String AB = "arcaneblade";
    static final String SC = "swordcaller";
    static final String OVERDRIVE = "arcaneblade_overdrive";
    static final String TWIN = "swordcaller_twin";
    /** 평타에 더한 몫의 구분 (WeaponBonus.parts) — 둘째 칼 · 오버드라이브 검기 */
    static final String KEY_ECHO = "job_echo";
    static final String KEY_WAVE = "job_wave";
    /** 각인 다섯 — 거대한 마력 대검이 떨어지는 틱 */
    static final int FULL_FALL = 2;
    private static final double[] WHITE = RuneKit.WHITE;

    static final class State {
        /** 소드 콜러: 이어 베는 적 · 쌓임 · 마지막으로 벤 틱 · 칼이 나간 쪽 (번갈아 — 오른쪽 · 왼쪽 어깨) */
        UUID chain;
        int stacks;
        long lastCut;
        boolean left;
        /** 소드 콜러 베어라: 꽂혀 기다리는 칼 (자리 · 끝나는 틱 · 입체 번호 · 그때의 삶) — 데려가라가 날아감 */
        Vec3 stuckAt;
        long stuckUntil;
        int stuckId;
        String stuckLife;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    static void init() {
        RpgCombat.addScaledBonusHook(JobTraits7::echoBonus);
        RpgCombat.addScaledBonusHook(JobTraits7::waveBonus);
        RpgCombat.addWeaponHitHook(JobTraits7::onWeaponHit);
        java.util.function.BiConsumer<ServerPlayer, Weapon> sh = RpgCombat.swingHook;
        RpgCombat.swingHook = (sp, w) -> {
            sh.accept(sp, w);
            onSwing(sp, w);
        };
        java.util.function.ToDoubleFunction<ServerPlayer> as = RpgCombat.attackSpeed;
        RpgCombat.attackSpeed = p -> as.applyAsDouble(p) * attackSpeed(p);
        java.util.function.Predicate<ServerPlayer> single = SwingArea.single;
        SwingArea.single = p -> single.test(p) || flyingSword(p);
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

    static void reset(ServerPlayer sp) {
        dropStuck(sp, 0.2);
        forget(sp.getUUID());
        JobFx3d.drop(sp, TWIN, 0.2);
    }

    static void forget(UUID id) {
        STATES.remove(id);
    }

    static void lifeEnded(UUID id) {
        State st = STATES.get(id);
        if (st != null) {
            st.chain = null;
            st.stacks = 0;
            st.stuckAt = null;
            st.stuckUntil = 0;
        }
    }

    // ------------------------------------------------------------ 아케인 블레이드: 각인이 터지면 번짐 · 다섯이면 거대한 마력 대검

    /**
     * 각인 n 개가 터짐 (victim 에서): delay 틱 뒤 둘레 spreadRadius 칸의 다른 적에게 마력 폭발이 번짐 (위력 × markBurst × spread × n — 마법). 그림: 둘레로 퍼지는 룬
     * 고리 · 땅 충격파 · 번진 적마다 X 검흔
     */
    static void spread(ServerPlayer sp, LivingEntity victim, Jobs.Job j, int n, int delay) {
        if (j == null || !AB.equals(j.id()) || victim == null || n <= 0) return;
        if (delay > 0) {
            String life = RuneKit.lifeToken(sp);
            RuneKit.later(delay, () -> {
                if (RuneKit.sameLife(sp, life)) spread(sp, victim, j, n, 0);
            });
            return;
        }
        if (!(victim.level() instanceof ServerLevel level)) return;
        double r = trait(j, "spreadRadius", 3.0);
        double mult = trait(j, "markBurst", 1.0) * trait(j, "spread", 0.5) * n;
        Vec3 at = Kit.bodyPoint(victim);
        if (RuneKit.fxQuota("jobSpread", 6)) {
            JobFx.runeRing(level, V.up(victim.position(), 0.12), r * 0.9, j, 8, Kit.rand() * 45, 0.35, Opt.of("alpha", 0.85));
            SkillFx.shock(level, V.up(victim.position(), 0.1), JobFx.outer(j), r, 0.35);
            SkillFx.sparks(level, at, JobFx.inner(j), 8, 4.0);
            SkillFx.play(level, "block.amethyst_block.resonate", at, 1.7, 0.6);
        }
        String life = RuneKit.lifeToken(sp);
        double P = JobSystem.power(sp, j);
        int[] cnt = {0};
        for (LivingEntity e : Kit.around(sp, victim.position(), r, 2.5)) {
            if (e == victim) continue;
            JobKit.hitLater(sp, e, life, Tasks.now(), () -> {
                if (JobKit.hit(sp, e, j, P, mult, true, false) > 0 && cnt[0]++ < 6 && e.level() instanceof ServerLevel lv) {
                    JobFx.cross(lv, Kit.bodyPoint(e), 0.6 + Kit.bodyPad(e) * 0.4, j, 0.25, Opt.empty());
                    SkillFx.sparks(lv, Kit.bodyPoint(e), JobFx.inner(j), 5, 3.0);
                }
            });
        }
    }

    /**
     * 각인 다섯이 한꺼번에 터짐 (평타 — JobTraits.onWeaponHit, 롤 가렌 R): 적 머리 위 7칸에서 거대한 마력 대검이 칼끝부터 내리꽂힘 (FULL_FALL 틱) → 꽂히는 순간 X 큰
     * 검기 · 터짐 · 룬 고리 · 충격 고리 · 돌 · 맞은 자리 큰 타격 (멈칫). 피해는 평타에 더한 몫 (JobTraits.markBonus — 위력 × markBurst × 다섯 · 큰 숫자), 둘레로 번지는
     * 것은 꽂히는 틱에 (spread)
     */
    static void giantSword(ServerPlayer sp, LivingEntity victim, Jobs.Job j) {
        if (!(victim.level() instanceof ServerLevel level)) return;
        Vec3 b = Kit.bodyPoint(victim);
        Vec3 top = V.up(b, 7.0);
        JobFx3d.fly(level, "phantom_sword", top, V.up(victim.position(), 0.2), JobFx3d.SWORD_TIP, 2.6, 0.1, 0.4, j, Opt.of("shine", 0.6, "fadeOut", 0.25));
        JobFx.line(level, top, b, j, 0.2, Opt.of("alpha", 0.6));
        SkillFx.play(level, "item.trident.thunder", b, 1.6, 0.6);
        String life = RuneKit.lifeToken(sp);
        RuneKit.later(FULL_FALL, () -> {
            if (!RuneKit.sameLife(sp, life) || !(victim.level() instanceof ServerLevel lv)) return;
            Vec3 at = victim.isRemoved() ? b : Kit.bodyPoint(victim);
            double s = 1.5 + Kit.bodyPad(victim) * 0.6;
            Vec3 view = V.unit(V.sub(at, sp.getEyePosition()));
            JobFx.bigWave(lv, null, at, view, 0, s * 1.4, 45, 0.38, j, Opt.empty());
            JobFx.bigWave(lv, null, at, view, 0, s * 1.4, -45, 0.38, j, Opt.empty());
            JobFx.burst(lv, at, s, j, 0.35, Opt.empty());
            JobFx.runeRing(lv, V.up(victim.position(), 0.1), 3.0, j, 9, Kit.rand() * 40, 0.6, Opt.of("alpha", 0.9));
            SkillFx.shock(lv, V.up(victim.position(), 0.1), JobFx.outer(j), 3.4, 0.45);
            RuneKit.rockBurst(lv, V.up(victim.position(), 0.3), 6, 5.0, 0.15);
            SkillFx.sparks(lv, at, JobFx.inner(j), 18, 7.0);
            if (!victim.isRemoved()) JobImpact.hit(sp, victim, j, 1.8);
            SkillFx.play(lv, "random.explode", at, 1.2, 0.6);
            SkillFx.play(lv, "block.amethyst_block.resonate", at, 0.7, 1.0);
        });
    }

    // ------------------------------------------------------------ 아케인 블레이드: 스펠 오버드라이브 (태세 — 칼 평타마다 검기)

    /** 오버드라이브: 평타로 맞힌 적에 검기 몫 (위력 × wave — 직업 색 숫자) */
    private static RpgCombat.Bonus waveBonus(ServerPlayer sp, LivingEntity victim, Weapon w, double dmg) {
        if (w == null || victim == null || !JobTraits.stanceOn(sp, OVERDRIVE)) return null;
        Jobs.Job j = jobIf(sp, AB);
        if (j == null || !j.usesWeapon(w.type())) return null;
        Jobs.Skill k = j.skill(OVERDRIVE);
        double wave = k == null ? 0.9 : JobUpgrades.num(sp, k, "wave", 0.9);
        double add = JobSystem.power(sp, j) * wave * mmorpg.fabric.stattree.StatTrees.atkMult(sp);
        return new RpgCombat.Bonus(add, "", KEY_WAVE, DamageNumbers.Style.JOB, null, JobKit.grad(j));
    }

    /**
     * 오버드라이브 동안 칼 평타를 휘두름 (Slash.once — 허공 포함): 1틱 뒤 마력 검기가 칼 높이에서 앞으로 날아감 (waveLen 칸 · waveSpeed 칸/틱 — 휘두를 때마다 기울기가 번갈아)
     * → 길 위 (폭 waveWidth) 의 다른 적 (이번 휘두르기로 맞은 적 — 조준 · 범위 — 은 빼고: 그 몫은 waveBonus) 을 지나가는 틱에 벰 (위력 × line · 마법)
     */
    private static void overdriveSwing(ServerPlayer sp, Jobs.Job j) {
        Jobs.Skill k = j.skill(OVERDRIVE);
        if (k == null) return;
        String life = RuneKit.lifeToken(sp);
        State st = of(sp);
        st.left = !st.left;
        boolean left = st.left;
        RuneKit.later(1, () -> {
            if (!RuneKit.sameLife(sp, life) || !JobTraits.stanceOn(sp, OVERDRIVE)) return;
            ServerLevel level = sp.level();
            java.util.Set<UUID> swung = SwingArea.swingVictims(sp);
            Vec3 c = sp.position();
            Vec3 f = Kit.facingOf(sp);
            double len = k.num("waveLen", 6.0);
            double speed = Math.max(0.5, k.num("waveSpeed", 1.5));
            double half = k.num("waveWidth", 2.0) / 2;
            double L = Math.min(len, RuneKit.rayLength(level, V.up(c, 1.1), f, len + 0.5));
            JobFx.bigWave(level, sp, V.up(V.add(c, f, 0.9), 1.15), f, speed * 20, 1.35, left ? 12 : -12, Math.max(0.12, L / speed / 20.0 + 0.08), j, Opt.empty());
            SkillFx.play(level, "entity.player.attack.sweep", c, 1.3, 0.5);
            double P = JobSystem.power(sp, j);
            double mult = JobUpgrades.num(sp, k, "line", 0.45);
            Vec3 right = V.rightOf(f);
            for (LivingEntity e : Kit.around(sp, V.add(c, f, L / 2), L / 2 + 1.5, 2.5)) {
                if (swung.contains(e.getUUID())) continue;
                Vec3 d = V.sub(e.position(), c);
                double along = d.x * f.x + d.z * f.z;
                double side = Math.abs(d.x * right.x + d.z * right.z);
                double pad = Kit.bodyPad(e);
                if (along < 0.3 - pad || along > L + 0.4 + pad || side > half + pad) continue;
                int at = (int) Math.ceil(Math.max(0, along - 0.9) / speed);
                JobKit.hitLater(sp, e, life, Tasks.now() + at, () -> {
                    if (JobKit.hit(sp, e, j, P, mult, true, false) > 0 && e.level() instanceof ServerLevel lv && RuneKit.fxQuota("jobOverdrive", 8)) {
                        JobFx.cross(lv, Kit.bodyPoint(e), 0.7 + Kit.bodyPad(e) * 0.5, j, 0.22, Opt.empty());
                        JobImpact.fx(sp, e, j, 0.8);
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------ 소드 콜러: 스스로 싸우는 칼

    /** 칼을 날리는 소드 콜러인지 (그 직업의 칼을 듦 — 둘레 범위 피해 없음 · SwingArea.single) */
    static boolean flyingSword(ServerPlayer sp) {
        Jobs.Job j = jobIf(sp, SC);
        if (j == null) return false;
        Weapon w = RpgCombat.weaponOf(sp.getMainHandItem());
        return w != null && j.usesWeapon(w.type());
    }

    /** 칼이 날아가는 거리 (trait.range + 둘로 나뉘어라 동안 range) */
    static double flyRange(ServerPlayer sp, Jobs.Job j) {
        double r = trait(j, "range", 10);
        if (JobTraits.stanceOn(sp, TWIN)) {
            Jobs.Skill k = j.skill(TWIN);
            r += k == null ? 4 : k.num("range", 4);
        }
        return r;
    }

    /** 칼이 노릴 적: 조준한 적 (맞을 수 있으면), 없으면 시야 cone 도 안의 적대 무리 (곁의 동물은 아님) */
    static LivingEntity flyTarget(ServerPlayer sp, Jobs.Job j, double range) {
        RpgCombat.Aim r = RpgCombat.aimTarget(sp, range, 0);
        if (r.target() != null && Kit.canHit(sp, r.target())) return r.target();
        RpgCombat.Aim a = RpgCombat.assist(sp, range, trait(j, "cone", 12), x -> Kit.splashFoe(sp, x));
        return a == null ? null : a.target();
    }

    /** 공격 속도 배율 (쌓임 하나당 + per — lapse 틱 못 베면 1) */
    static double attackSpeed(ServerPlayer sp) {
        Jobs.Job j = jobIf(sp, SC);
        if (j == null) return 1;
        State st = STATES.get(sp.getUUID());
        if (st == null || st.stacks <= 0 || Tasks.now() - st.lastCut > trait(j, "lapse", 40)) return 1;
        return 1 + trait(j, "per", 0.2) * st.stacks;
    }

    /** 게이지 값: 소드 콜러 = 쌓임 */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        if (j == null || !SC.equals(j.id())) return 0;
        State st = STATES.get(sp.getUUID());
        return st == null || Tasks.now() - st.lastCut > trait(j, "lapse", 40) ? 0 : st.stacks;
    }

    /** 시험용: 칼이 날아가는 거리 */
    public static double flyRangeOf(ServerPlayer sp) {
        Jobs.Job j = jobIf(sp, SC);
        return j == null ? 0 : flyRange(sp, j);
    }

    /** 시험용: 꽂혀 기다리는 칼이 있는지 */
    public static boolean stuckOf(ServerPlayer sp) {
        return stuckAt(sp) != null;
    }

    /** 시험용: 쌓임 */
    public static int stacksOf(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.stacks;
    }

    /**
     * 칼 평타를 휘두름 (Slash.once — 허공 포함): 1틱 뒤 (같은 누름의 공격 패킷까지 받은 뒤) — 가까이 조준해 바닐라 평타로 맞힌 적이 있으면 칼이 그 적을 베는 그림만,
     * 없으면 칼이 날아가 노린 적을 벰 (닿는 틱에 평타 한 번), 노릴 적이 없으면 칼이 앞으로 날았다 돌아옴
     */
    private static void onSwing(ServerPlayer sp, Weapon w) {
        Jobs.Job ab = jobIf(sp, AB);
        if (ab != null && w != null && ab.usesWeapon(w.type()) && JobTraits.stanceOn(sp, OVERDRIVE)) {
            overdriveSwing(sp, ab);
            return;
        }
        Jobs.Job j = jobIf(sp, SC);
        if (j == null || w == null || !j.usesWeapon(w.type())) return;
        String life = RuneKit.lifeToken(sp);
        State st = of(sp);
        st.left = !st.left;
        boolean left = st.left;
        RuneKit.later(1, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            UUID prim = SwingArea.primaryOf(sp);
            Entity pe = prim == null ? null : sp.level().getEntity(prim);
            if (pe instanceof LivingEntity le && le.isAlive()) {
                flyCut(sp, j, le, w, false, left);
                return;
            }
            LivingEntity t = flyTarget(sp, j, flyRange(sp, j));
            if (t == null) emptyCut(sp, j, left);
            else flyCut(sp, j, t, w, true, left);
        });
    }

    /** 어깨 위 칼이 떠나는 자리 (left 면 왼쪽 어깨) */
    static Vec3 shoulder(ServerPlayer sp, boolean left) {
        Vec3 f = Kit.facingOf(sp);
        return V.up(V.add(V.add(sp.position(), V.rightOf(f), left ? -0.62 : 0.62), f, -0.15), 1.72);
    }

    /**
     * 칼이 날아가 벰: 어깨 위에서 마력 칼이 휘어 날아가 (flySpeed 칸/틱) 닿는 틱에 적을 가로질러 벰 (칼이 몸을 꿰고 지나감 · 참격 판 · 맞은 자리 — 쌓일수록 밝게) →
     * 뒤로 물러나며 어깨로 돌아옴. deal 이면 닿는 틱에 평타 한 번 그대로 (그 무기를 아직 들고 쓸 수 있을 때). 둘로 나뉘어라 동안 둘째 칼이 다른 어깨에서 함께 날아 X 자로 벰
     */
    static void flyCut(ServerPlayer sp, Jobs.Job j, LivingEntity t, Weapon w, boolean deal, boolean left) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        boolean twin = JobTraits.stanceOn(sp, TWIN);
        Vec3 to = Kit.bodyPoint(t);
        Vec3 from = shoulder(sp, left);
        double speed = Math.max(0.5, trait(j, "flySpeed", 3.0));
        int ticks = Math.max(1, (int) Math.ceil(V.len(V.sub(to, from)) / speed));
        State st = of(sp);
        int stacks = Tasks.now() - st.lastCut <= trait(j, "lapse", 40) ? st.stacks : 0;
        int id1 = cutSword(sp, j, from, to, left, ticks, stacks);
        int id2 = twin ? cutSword(sp, j, shoulder(sp, !left), to, !left, ticks, stacks) : 0;
        SkillFx.play(level, "item.trident.throw", from, 1.5 + 0.06 * stacks, 0.45);
        RuneKit.later(ticks, () -> {
            if (!RuneKit.sameLife(sp, life)) {
                JobFx3d.kill(sp.level(), id1, to, 0.1);
                JobFx3d.kill(sp.level(), id2, to, 0.1);
                return;
            }
            ServerLevel lv = sp.level();
            if (deal && !t.isRemoved() && t.isAlive()) {
                Weapon held = RpgCombat.weaponOf(sp.getMainHandItem());
                if (held != null && held.id().equals(w.id()) && RpgCombat.lackOf(sp, held).isEmpty() && Kit.canHit(sp, t)) RpgCombat.areaHit(sp, t, held);
            }
            Vec3 at = t.isRemoved() ? to : Kit.bodyPoint(t);
            cutFx(lv, sp, j, at, Kit.bodyPad(t), left, twin, stacks);
            passBack(sp, id1, from, at, left);
            passBack(sp, id2, shoulder(sp, !left), at, !left);
        });
    }

    /** 날아가는 칼 하나 (어깨 → 적 — 바깥 위로 휘어 감, 칼끝이 가는 쪽): 번호 */
    private static int cutSword(ServerPlayer sp, Jobs.Job j, Vec3 from, Vec3 to, boolean left, int ticks, int stacks) {
        Vec3 f = Kit.facingOf(sp);
        Vec3 out = V.scale(V.rightOf(f), left ? -1 : 1);
        double len = V.len(V.sub(to, from));
        Vec3 ctrl = V.up(V.add(V.lerp(from, to, 0.45), out, Math.min(1.6, 0.35 + len * 0.12)), Math.min(1.0, 0.2 + len * 0.06));
        return JobFx3d.fly(sp.level(), "phantom_sword", from, to, JobFx3d.SWORD_TIP, 1.0, ticks / 20.0, 0.5, j, Opt.of("ctrl", ctrl, "shine", 0.3 + 0.08 * stacks,
                "glowAlpha", 0.5 + 0.06 * stacks, "fadeOut", 0.12));
    }

    /** 벤 뒤: 칼이 적을 꿰고 1.1칸 지나갔다가 (2틱) 적을 본 채 물러나 어깨로 돌아옴 (4틱) → 사라짐 */
    private static void passBack(ServerPlayer sp, int id, Vec3 from, Vec3 at, boolean left) {
        if (id == 0) return;
        ServerLevel level = sp.level();
        Vec3 d = V.unit(V.sub(at, from));
        Fx3d.move(level, id, V.add(at, d, 1.1), 0.1, "out");
        RuneKit.later(2, () -> Fx3d.moveBack(level, id, shoulder(sp, left), 0.2, "inout"));
        RuneKit.later(6, () -> JobFx3d.kill(level, id, sp.position(), 0.12));
    }

    /**
     * 칼이 벤 자리: 참격 판 (칼이 지나간 결 — 왼쪽 어깨에서 오면 반대로 기욺 · 둘이면 X 자) · 직업의 맞은 자리 · 청록 불티 · 쌓임 3 부터 잔상 한 겹 · 다섯이면 흰 고리 ·
     * 휙 · 칼 소리 (쌓일수록 높게)
     */
    static void cutFx(ServerLevel level, ServerPlayer sp, Jobs.Job j, Vec3 at, double pad, boolean left, boolean twin, int stacks) {
        if (!RuneKit.fxQuota("jobFlyCut", 8)) return;
        Vec3 d = V.unit(V.sub(at, sp.getEyePosition()));
        double tilt = (left ? 200 : -20) + (Kit.rand() - 0.5) * 24;
        double size = 1.0 + pad * 0.9 + 0.05 * stacks;
        JobFx.slashCard(level, at, d, tilt, size, 0.24, Vec3.ZERO, j, Opt.empty());
        if (twin) JobFx.slashCard(level, at, d, tilt + 90 + (Kit.rand() - 0.5) * 20, size, 0.24, Vec3.ZERO, j, Opt.empty());
        if (stacks >= 3) JobFx.slashCard(level, V.add(at, d, -0.1), d, tilt + 12, size * 1.18, 0.3, Vec3.ZERO, j, Opt.of("alpha", 0.5));
        JobFx.burst(level, at, 0.45 + pad * 0.4, j, 0.22, Opt.empty());
        SkillFx.sparks(level, at, JobFx.inner(j), 5 + stacks, 3.5 + 0.3 * stacks);
        if (stacks >= 5) JobFx.ringFacing(level, at, 0.7 + pad, j, d, 0.25, Opt.of("alpha", 0.85));
        SkillFx.play(level, "entity.player.attack.sweep", at, 1.45 + 0.08 * stacks, 0.45);
        SkillFx.play(level, "item.trident.hit", at, 1.3 + 0.08 * stacks, 0.4);
    }

    /** 노릴 적이 없음: 칼이 보는 쪽 앞 4칸까지 날았다가 돌아옴 (휙) */
    private static void emptyCut(ServerPlayer sp, Jobs.Job j, boolean left) {
        if (!RuneKit.fxQuota("jobFlyEmpty", 6)) return;
        ServerLevel level = sp.level();
        Vec3 look = sp.getViewVector(1F);
        Vec3 from = shoulder(sp, left);
        double reach = Math.min(4.0, Kit.rayDist(level, sp.getEyePosition(), look, 4.0));
        Vec3 to = V.add(sp.getEyePosition(), look, Math.max(1.5, reach));
        int id = cutSword(sp, j, from, to, left, 2, 0);
        RuneKit.later(2, () -> {
            Fx3d.moveBack(level, id, shoulder(sp, left), 0.2, "inout");
            JobFx.slashCard(level, to, look, left ? 200 : -20, 0.9, 0.18, Vec3.ZERO, j, Opt.of("alpha", 0.6));
        });
        RuneKit.later(6, () -> JobFx3d.kill(level, id, sp.position(), 0.12));
        SkillFx.play(level, "entity.player.attack.nodamage", from, 1.6, 0.35);
    }

    /** 둘로 나뉘어라: 칼 평타에 둘째 칼 (위력 × echo — 직업 색 숫자) */
    private static RpgCombat.Bonus echoBonus(ServerPlayer sp, LivingEntity victim, Weapon w, double dmg) {
        if (w == null || victim == null || !JobTraits.stanceOn(sp, TWIN)) return null;
        Jobs.Job j = jobIf(sp, SC);
        if (j == null || !j.usesWeapon(w.type())) return null;
        Jobs.Skill k = j.skill(TWIN);
        double echo = k == null ? 0.9 : JobUpgrades.num(sp, k, "echo", 0.9);
        double add = JobSystem.power(sp, j) * echo * mmorpg.fabric.stattree.StatTrees.atkMult(sp);
        return new RpgCombat.Bonus(add, "", KEY_ECHO, DamageNumbers.Style.JOB, null, JobKit.grad(j));
    }

    /** 무기로 맞힘 (피해가 들어간 뒤): 소드 콜러 — 같은 적을 이어 벰 (쌓임 +1 · 최대 max, 다른 적이거나 lapse 틱이 지났으면 1) */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        if (victim == null || w == null) return;
        Jobs.Job j = JobSystem.job(sp);
        if (j == null || !j.usesWeapon(w.type()) || !SC.equals(j.id())) return;
        State st = of(sp);
        long now = Tasks.now();
        int max = j.trait() == null ? 5 : Math.max(1, j.trait().max());
        boolean same = victim.getUUID().equals(st.chain) && now - st.lastCut <= trait(j, "lapse", 40);
        int before = st.stacks;
        st.stacks = same ? Math.min(max, st.stacks + 1) : 1;
        st.chain = victim.getUUID();
        st.lastCut = now;
        if (st.stacks == max && before < max && RuneKit.fxQuota("jobChainFull", 4)) {
            // 가득 참: 몸 둘레 흰 바람 고리 · 높은 칼 소리
            JobFx.ring(sp.level(), V.up(sp.position(), 1.0), 1.3, j, false, 0.3, Opt.of("alpha", 0.9));
            SkillFx.play(sp.level(), "item.trident.return", sp.position(), 1.8, 0.6);
        }
        JobTraits.sendGauge(sp, false);
    }

    // ------------------------------------------------------------ 소드 콜러: 꽂혀 기다리는 칼 (베어라 → 데려가라)

    /** 꽂혀 기다리는 칼을 둠 (자리 · ticks 틱 · 입체 번호) — 앞의 것은 지움 */
    static void setStuck(ServerPlayer sp, Vec3 at, int ticks, int id) {
        dropStuck(sp, 0.15);
        State st = of(sp);
        st.stuckAt = at;
        st.stuckUntil = Tasks.now() + ticks;
        st.stuckId = id;
        st.stuckLife = RuneKit.lifeToken(sp);
    }

    /** 시험 · 영상용: 꽂혀 기다리는 칼을 at (땅) 에 ticks 틱 둠 (베어라를 쓰지 않고 — 데려가라가 그리로 날아감) */
    public static void testStuck(ServerPlayer sp, Vec3 at, int ticks) {
        Jobs.Job j = jobIf(sp, SC);
        if (j == null) return;
        setStuck(sp, at, ticks, stuckSword(sp.level(), at, ticks, j));
    }

    /**
     * 땅에 꽂혀 기다리는 칼 (g = 땅): 비스듬히 꽂힌 큰 칼 · 발밑 룬 고리 · 위로 옅어지는 청록 빛기둥 (멀리서도 데려가라가 날아갈 자리가 보이게 — 영상 1 뒤: 과녁 무리 뒤에
     * 묻혔음 · 빛기둥은 꽂혀 있는 동안 8틱마다 다시 그려 칼을 뽑으면 곧 사라짐 — tick). 칼 번호
     */
    static int stuckSword(ServerLevel level, Vec3 g, int ticks, Jobs.Job j) {
        double secs = ticks / 20.0;
        int id = JobFx3d.stuck(level, "phantom_sword", V.up(g, -0.3), V.unit(new Vec3(0.12, -1, 0.08)), JobFx3d.SWORD_TIP, 1.25, secs, j, Opt.of("fade", 0.3));
        JobFx.runeRing(level, V.up(g, 0.08), 1.1, j, 6, 0, secs, Opt.of("alpha", 0.8));
        stuckBeam(level, g, j);
        return id;
    }

    /** 꽂힌 칼 위의 빛기둥 한 번 (0.5초 — 위로 옅어지는 청록 줄 넷) */
    static void stuckBeam(ServerLevel level, Vec3 g, Jobs.Job j) {
        double[] alpha = {0.5, 0.36, 0.24, 0.13};
        for (int i = 0; i < alpha.length; i++) {
            JobFx.beam(level, V.up(g, 0.2 + i * 1.1), V.up(g, 0.2 + (i + 1) * 1.1), j, 1.1, 0.5, Opt.of("alpha", alpha[i], "fade", 0.2));
        }
    }

    /** 꽂혀 기다리는 칼 자리 (없거나 끝났거나 삶이 바뀌었으면 null) */
    static Vec3 stuckAt(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        if (st == null || st.stuckAt == null || st.stuckUntil <= Tasks.now() || !RuneKit.sameLife(sp, st.stuckLife)) return null;
        return st.stuckAt;
    }

    /** 꽂혀 기다리는 칼을 지움 (fade 초) */
    static void dropStuck(ServerPlayer sp, double fade) {
        State st = STATES.get(sp.getUUID());
        if (st == null || st.stuckAt == null) return;
        JobFx3d.kill(sp.level(), st.stuckId, st.stuckAt, fade);
        st.stuckAt = null;
        st.stuckUntil = 0;
        st.stuckId = 0;
    }

    // ------------------------------------------------------------ 틱 · 태세

    /** 4틱마다 (JobTraits.tick): 소드 콜러 — 쌓임이 끊김 (lapse) · 꽂힌 칼이 끝남 (흐려짐) · 꽂혀 있는 동안 8틱마다 빛기둥 */
    static void tick(ServerPlayer sp, Jobs.Job j, long now) {
        if (!SC.equals(j.id())) return;
        State st = STATES.get(sp.getUUID());
        if (st == null) return;
        if (st.stacks > 0 && now - st.lastCut > trait(j, "lapse", 40)) {
            st.stacks = 0;
            st.chain = null;
            JobTraits.sendGauge(sp, false);
        }
        if (st.stuckAt != null && st.stuckUntil <= now) dropStuck(sp, 0.3);
        else if (st.stuckAt != null && now % 8 == 0 && RuneKit.sameLife(sp, st.stuckLife)) stuckBeam(sp.level(), st.stuckAt, j);
    }

    /**
     * 태세 연출 (8틱마다 — JobTraits.stanceFx): 스펠 오버드라이브 = 손의 칼에서 흘러넘치는 마력 (보라 빛 알갱이가 칼날을 따라 오름 · 칼끝 반짝임 · 발밑에 이따금 룬
     * 고리) / 제미니 소드 = 손의 칼에서 흩날리는 청록 빛살. 그렸으면 true
     */
    static boolean stanceFx(ServerPlayer sp, Jobs.Job j, String stance) {
        ServerLevel level = sp.level();
        RuneKit.Hand h = RuneKit.handOf(sp);
        if (OVERDRIVE.equals(stance)) {
            Vec3 blade = V.up(V.add(h.hand(), h.f(), 0.3), 0.5);
            for (int i = 0; i < 2; i++) {
                SkillFx.fx(level, "rpg:arrow_spark", V.add(blade, new Vec3(Kit.rand() - 0.5, Kit.rand() - 0.5, Kit.rand() - 0.5), 0.35), Opt.of("dir", V.UP, "speed", 1.6,
                        "size", 0.12, "grow", 0.5, "life", 0.4, "color", i == 0 ? JobFx.inner(j) : JobFx.outer(j), "alpha", 1.0));
            }
            JobFx.glint(level, V.up(blade, 0.4), 0.35, j, 0.3, Opt.of("vel", RuneKit.bodyVel(sp)));
            if (Kit.rand() < 0.3) JobFx.runeRing(level, V.up(sp.position(), 0.08), 1.1, j, 6, Kit.rand() * 60, 0.4, JobFx.hide1p(sp).with("alpha", 0.55));
            return true;
        }
        if (TWIN.equals(stance)) {
            Vec3 blade = V.up(V.add(h.hand(), h.f(), 0.3), 0.4);
            SkillFx.fx(level, "rpg:arrow_spark", blade, Opt.of("dir", V.unit(new Vec3(Kit.rand() - 0.5, 1, Kit.rand() - 0.5)), "speed", 1.2, "size", 0.1, "grow", 0.6,
                    "life", 0.4, "color", JobFx.inner(j), "alpha", 1.0));
            return true;
        }
        return false;
    }
}
