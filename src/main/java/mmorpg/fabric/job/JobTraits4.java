package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.WeaponTraits;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 0.30.0 성기사 · 사제 · 수호기사 특성 (JobTraits 가 부름 — 자원 · 태세는 JobTraits 와 같은 틀):
 * <ul>
 * <li>성기사 「심판」: 망치 평타가 맞힌 적에 +1 (최대 max · judgeFor 틱 — 머리 위 작은 번개), 직업 스킬도 더함 (뇌격 강타 +2 · 뇌진 맞을 때마다 +1). max 가 되면
 * 그 적에 벼락 — 일반 적은 stun 틱 기절 (머리 위 도는 별), 보스는 bossFor 틱 받는 피해 + bossTaken (RpgCombat.takenMult) → 0. 수호 오라: 망치를 든 성기사 둘레
 * auraRadius 칸 아군 (나 포함) 받는 피해 − aura (수호의 맹세 동안 − 맹세의 aura · 1초마다 최대 체력 regen 회복) (RpgHealth.sourceReduce)</li>
 * <li>사제 「신앙」: 회복 · 보호막을 주는 직업 스킬 (배율 합 0) 을 쓸 때마다 +1 (최대 max), 공격 직업 스킬 (성가 · 새벽의 종 — 배율 합 &gt; 0) 이 모두 써서
 * 위력 × (1 + per × 신앙) (JobSystem). 홀 평타 흡혈이 넘치면 (WeaponTraits.stealOverflow) 둘레 overflowRadius 칸에서 체력 비율이 가장 낮은 아군에게. 성흔 — 그
 * 적을 때린 아군이 준 피해 × rate 회복 (DamageNumbers.dealtHook). 새벽의 종 — 쓰러지지 않음 (RpgHealth.undying)</li>
 * <li>수호기사 「반격」: 웅크려 막아 준 양 (RpgHealth.blockHook) × rate 가 게이지 (최대 체력 × cap 까지) → 다음 공격 직업 스킬의 첫 타에 더함 (JobKit). 철옹성
 * (태세): 막기 정면 front · 옆 side (RpgCombat.blockMod) · 밀려나지 않음 · 이동 − slow. 수호 돌입: 지키는 아군이 받을 피해의 share 를 내가 × (1 − cut) 로
 * (RpgHealth.lossShare — 체력 1 아래로는 깎이지 않음). 성채: 안의 아군 받는 피해 − guard</li>
 * </ul>
 */
public final class JobTraits4 {
    private JobTraits4() {}

    static final class State {
        // 성기사
        /** 게이지: 마지막으로 심판을 찍은 적 */
        UUID target;
        // 사제
        int faith;
        /** 받은 성흔 회복 (숫자는 모아서 1초에 한 번) */
        double stigmaAcc;
        long stigmaShown;
        // 수호기사
        /** 반격 게이지 (체력 양) */
        double counter;
        /** 지금 쓰는 몫 (안 들어가면 되돌림) */
        double counterUsing;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    /** 심판: "공격자|적" → {수, 끝나는 틱} */
    private static final Map<String, long[]> JUDGE = new HashMap<>();
    /** 심판이 찍힌 적 (머리 위 번개를 다시 그림) */
    private static final Map<UUID, LivingEntity> JUDGED = new HashMap<>();
    /** 보스 심판 (받는 피해 +): 적 UUID → {끝나는 틱, 배율} */
    private static final Map<UUID, double[]> BOSS_JUDGE = new HashMap<>();

    /** 성흔: 사제 · 적 · 끝나는 틱 · 회복 비율 */
    record Stigma(UUID priest, LivingEntity e, long until, double rate) {}

    private static final Map<UUID, Stigma> STIGMA = new HashMap<>();
    /** 새벽의 종: 플레이어 UUID → 쓰러지지 않는 마지막 틱 */
    private static final Map<UUID, Long> DAWN = new HashMap<>();

    /** 수호 돌입: 지켜지는 아군 UUID → 수호기사 · 끝나는 틱 · 나눠 받는 몫 · 수호기사가 덜 받는 비율 */
    record Share(UUID guardian, long until, double share, double cut) {}

    private static final Map<UUID, Share> SHARES = new HashMap<>();

    /** 성채: 쓴 사람 · 차원 · 가운데 · 반지름 · 끝나는 틱 · 받는 피해 감소 */
    record Fort(UUID owner, ServerLevel level, Vec3 center, double radius, long until, double guard) {}

    private static final List<Fort> FORTS = new ArrayList<>();
    /** 철옹성 이동 − (MOVEMENT_SPEED) */
    private static final Identifier BULWARK_SLOW = Mmorpg.id("job_bulwark_slow");

    static void init() {
        RpgCombat.addWeaponHitHook(JobTraits4::onWeaponHit);
        java.util.function.ToDoubleFunction<LivingEntity> tm = RpgCombat.takenMult;
        RpgCombat.takenMult = e -> tm.applyAsDouble(e) * judgedMult(e);
        java.util.function.ToDoubleBiFunction<ServerPlayer, DamageSource> sr = RpgHealth.sourceReduce;
        RpgHealth.sourceReduce = (p, src) -> Math.max(sr.applyAsDouble(p, src), sourceReduce(p, src));
        // 새벽의 종: 쓰러지지 않음 (불사의 맹약 룬과 같은 길 — 버틴 연출은 그쪽이 아니면 종)
        java.util.function.Predicate<ServerPlayer> und = RpgHealth.undying;
        java.util.function.Consumer<ServerPlayer> saved = RpgHealth.undyingSaved;
        RpgHealth.undying = p -> und.test(p) || dawnOn(p);
        RpgHealth.undyingSaved = p -> {
            if (und.test(p)) saved.accept(p);
            else dawnSaved(p);
        };
        RpgHealth.LossShare ls = RpgHealth.lossShare;
        RpgHealth.lossShare = (p, src, loss) -> {
            double a = ls.share(p, src, loss);
            return a > 0 ? a : share(p, src, loss);
        };
        RpgHealth.BlockHook bh = RpgHealth.blockHook;
        RpgHealth.blockHook = (p, src, amount) -> {
            bh.blocked(p, src, amount);
            onBlocked(p, amount);
        };
        RpgCombat.BlockMod bm = RpgCombat.blockMod;
        RpgCombat.blockMod = (p, w, dot) -> Math.max(bm.block(p, w, dot), bulwarkBlock(p, dot));
        java.util.function.BiConsumer<ServerPlayer, Double> so = WeaponTraits.stealOverflow;
        WeaponTraits.stealOverflow = (p, extra) -> {
            so.accept(p, extra);
            overflow(p, extra);
        };
        DamageNumbers.DealtHook dh = DamageNumbers.dealtHook;
        DamageNumbers.dealtHook = (a, v, d) -> {
            dh.dealt(a, v, d);
            stigmaHeal(a, v, d);
        };
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATES.clear();
            JUDGE.clear();
            JUDGED.clear();
            BOSS_JUDGE.clear();
            STIGMA.clear();
            DAWN.clear();
            SHARES.clear();
            FORTS.clear();
        });
    }

    static State of(ServerPlayer sp) {
        return STATES.computeIfAbsent(sp.getUUID(), k -> new State());
    }

    /** 직업을 바꿈 · 시험: 심판 · 신앙 · 반격 · 성흔 · 수호 돌입 · 성채 · 이동 수정치를 지움 */
    static void reset(ServerPlayer sp) {
        STATES.remove(sp.getUUID());
        speedMod(sp, 0);
        forgetLinks(sp.getUUID());
    }

    static void forget(UUID id) {
        STATES.remove(id);
        forgetLinks(id);
    }

    private static void forgetLinks(UUID id) {
        String p = id + "|";
        JUDGE.keySet().removeIf(k -> k.startsWith(p));
        STIGMA.values().removeIf(s -> s.priest().equals(id));
        SHARES.entrySet().removeIf(en -> en.getKey().equals(id) || en.getValue().guardian().equals(id));
        FORTS.removeIf(f -> f.owner().equals(id));
        DAWN.remove(id);
    }

    /** 죽음 · 차원 이동: 신앙 · 반격 · 심판 · 수호 돌입 · 새벽의 종이 끝남 (성흔 · 성채는 그 자리에 남음) */
    static void lifeEnded(UUID id) {
        State st = STATES.get(id);
        if (st != null) {
            st.faith = 0;
            st.counter = 0;
            st.counterUsing = 0;
            st.target = null;
        }
        String p = id + "|";
        JUDGE.keySet().removeIf(k -> k.startsWith(p));
        SHARES.entrySet().removeIf(en -> en.getKey().equals(id) || en.getValue().guardian().equals(id));
        DAWN.remove(id);
    }

    private static Jobs.Job jobIf(ServerPlayer sp, String id) {
        Jobs.Job j = JobSystem.job(sp);
        return j != null && j.id().equals(id) ? j : null;
    }

    private static double trait(Jobs.Job j, String key, double fallback) {
        return j == null || j.trait() == null ? fallback : j.trait().def().numOr(key, fallback);
    }

    private static Jobs.Skill skill(Jobs.Job j, String id) {
        return j == null ? null : j.skill(id);
    }

    /** 플레이어 a 가 b 를 도울 수 있는지 (나 자신 · 결투 중인 남은 아님) */
    static boolean ally(ServerPlayer a, ServerPlayer b) {
        return a != null && b != null && !b.isSpectator() && b.isAlive() && b.level() == a.level() && (a == b || RuneKit.canAssist.test(a, b));
    }

    /** 겨눈 아군 (range 칸 · 시선에서 12° 안 · 벽 뒤 제외 — 나는 아님), 없으면 null */
    static ServerPlayer aimAlly(ServerPlayer sp, double range) {
        RpgCombat.Aim a = RpgCombat.assist(sp, range, 12, x -> x instanceof ServerPlayer o && o != sp && ally(sp, o));
        return a != null && a.target() instanceof ServerPlayer o ? o : null;
    }

    /** 둘레 r 칸 아군 (나 포함) */
    static List<ServerPlayer> allies(ServerPlayer sp, Vec3 c, double r) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer o : RuneKit.playersAround(sp.level(), c, r, sp)) if (ally(sp, o)) out.add(o);
        return out;
    }

    /** 이동 속도 수정치 (철옹성 — amount = 줄이는 비율, 0 이면 뗌) */
    private static void speedMod(ServerPlayer sp, double amount) {
        AttributeInstance a = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        if (a == null) return;
        if (amount > 0) a.addOrUpdateTransientModifier(new AttributeModifier(BULWARK_SLOW, -amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        else a.removeModifier(BULWARK_SLOW);
    }

    /** 걸린 철옹성 이동 수정치 (없으면 0 — 시험) */
    public static double slowBonus(ServerPlayer sp) {
        AttributeInstance a = sp.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeModifier m = a == null ? null : a.getModifier(BULWARK_SLOW);
        return m == null ? 0 : m.amount();
    }

    /** 회복 + 숫자 (실제로 오른 양) */
    static double heal(ServerPlayer pl, double amount) {
        double got = RpgHealth.healBy(pl, amount);
        if (got >= 0.05) DamageNumbers.show(pl, got, DamageNumbers.Style.HEAL, "", 0);
        return got;
    }

    // ------------------------------------------------------------ 성기사 「심판」

    private static String key(Entity attacker, Entity victim) {
        return attacker.getUUID() + "|" + victim.getUUID();
    }

    /** 이 적의 심판 수 (끝났으면 0) */
    public static int judges(ServerPlayer sp, Entity e) {
        if (e == null) return 0;
        long[] m = JUDGE.get(key(sp, e));
        return m == null || m[1] < Tasks.now() ? 0 : (int) m[0];
    }

    /** 시험용: 심판을 정함 (max 아래) */
    public static void setJudges(ServerPlayer sp, LivingEntity e, int n) {
        JUDGE.put(key(sp, e), new long[] {Math.max(0, n), Tasks.now() + 200});
        JUDGED.put(e.getUUID(), e);
        of(sp).target = e.getUUID();
        JobTraits.sendGauge(sp, false);
    }

    /** 보스 심판으로 받는 피해 배율이 걸려 있는지 (시험) */
    public static double judgedMult(LivingEntity e) {
        double[] b = e == null ? null : BOSS_JUDGE.get(e.getUUID());
        return b == null || b[0] <= Tasks.now() ? 1 : b[1];
    }

    /** 심판 n 더하기 (최대 max · judgeFor 틱) — 머리 위 작은 번개. max 가 되면 벼락 (기절 · 보스는 받는 피해 +) 뒤 0 */
    static void judge(ServerPlayer sp, LivingEntity e, Jobs.Job j, int n) {
        if (e == null || e.isRemoved() || !e.isAlive() || n <= 0 || j == null || !j.id().equals("paladin")) return;
        long now = Tasks.now();
        String k = key(sp, e);
        long[] m = JUDGE.computeIfAbsent(k, x -> new long[2]);
        if (m[1] < now) m[0] = 0;
        int max = j.trait() == null ? 5 : Math.max(1, j.trait().max());
        m[0] = Math.min(max, m[0] + n);
        m[1] = now + (long) trait(j, "judgeFor", 200);
        JUDGED.put(e.getUUID(), e);
        of(sp).target = e.getUUID();
        if (m[0] >= max) {
            JUDGE.remove(k);
            verdict(sp, e, j);
        } else if (e.level() instanceof ServerLevel level && RuneKit.fxQuota("jobJudge", 6)) {
            judgeFx(level, sp, e, j, (int) m[0], true);
            SkillFx.play(level, "block.amethyst_block.hit", Kit.bodyPoint(e), 1.5 + m[0] * 0.08, 0.5);
        }
        JobTraits.sendGauge(sp, false);
    }

    /** 머리 위 작은 번개 n 개 (한 줄 — 새로 찍힌 것은 크게) */
    private static void judgeFx(ServerLevel level, ServerPlayer sp, LivingEntity e, Jobs.Job j, int n, boolean fresh) {
        Vec3 over = RuneKit.overHead(e, 0.5);
        Vec3 right = V.rightOf(V.flat(V.sub(over, sp.getEyePosition())));
        for (int i = 0; i < n; i++) {
            Vec3 at = V.add(over, right, (i - (n - 1) / 2.0) * 0.3);
            boolean last = fresh && i == n - 1;
            JobFx.judgePip(level, at, last ? 0.2 : 0.15, j, 0.85, Opt.of("vel", RuneKit.entVel(e), "grow", last ? -0.3 : 0.0, "fade", 0.3, "alpha", last ? 1.0 : 0.85));
        }
    }

    /** 심판의 벼락: 일반 적은 stun 틱 기절 (머리 위 별) · 보스는 bossFor 틱 받는 피해 + bossTaken */
    private static void verdict(ServerPlayer sp, LivingEntity e, Jobs.Job j) {
        if (!(e.level() instanceof ServerLevel level)) return;
        Vec3 g = e.position();
        JobFx.thunder(level, g, 9 + e.getBbHeight(), j, 0.45, Opt.empty());
        JobFx.volt(level, V.up(g, 0.08), 1.2 + Kit.bodyPad(e), j, 0.45, Opt.empty());
        JobFx.burst(level, Kit.bodyPoint(e), 0.9 + Kit.bodyPad(e) * 0.5, j, 0.3, Opt.empty());
        SkillFx.sparks(level, Kit.bodyPoint(e), JobKit.glow(j), 10, 5);
        SkillFx.play(level, "entity.lightning_bolt.impact", g, 1.3, 0.8);
        SkillFx.play(level, "item.trident.thunder", g, 1.5, 0.3);
        if (JobKit.isBoss(e)) {
            double add = trait(j, "bossTaken", 0.08);
            BOSS_JUDGE.put(e.getUUID(), new double[] {Tasks.now() + trait(j, "bossFor", 80), 1 + add});
            DamageNumbers.showAt(level, V.up(e.getEyePosition(), 1.25), "§6§l심판 §e받는 피해 +" + Math.round(add * 100) + "%");
        } else {
            int t = (int) trait(j, "stun", 20);
            RuneKit.stun(e, t, JobKit.glow(j), false);
            JobFx.stunStars(e, j, t);
            DamageNumbers.showAt(level, V.up(e.getEyePosition(), 1.25), "§6§l심판");
        }
    }

    /** 기절 (스킬 — 보스 제외): ticks 동안 + 머리 위 별 */
    static void stun(LivingEntity e, Jobs.Job j, int ticks) {
        if (e == null || e.isRemoved() || JobKit.isBoss(e) || ticks <= 0) return;
        RuneKit.stun(e, ticks, JobKit.glow(j), false);
        JobFx.stunStars(e, j, ticks);
    }

    /** 망치를 들었는지 (요구 스탯까지 맞음 — 수호 오라) */
    static boolean holdsHammer(ServerPlayer sp) {
        Weapon w = RpgCombat.weaponOf(sp.getMainHandItem());
        return w != null && "hammer".equals(w.type()) && RpgCombat.lackOf(sp, w).isEmpty();
    }

    /** 수호 오라의 감소 (이 아군 p 에게 — 둘레에 망치를 든 성기사가 있으면 aura, 그 성기사가 맹세 중이면 맹세의 aura) */
    public static double auraFor(ServerPlayer p) {
        double best = 0;
        for (ServerPlayer o : p.level().players()) {
            if (o.isSpectator() || !o.isAlive()) continue;
            Jobs.Job j = jobIf(o, "paladin");
            if (j == null || !ally(o, p)) continue;
            double r = trait(j, "auraRadius", 6);
            if (o.distanceToSqr(p) > r * r || !holdsHammer(o)) continue;
            Jobs.Skill oath = skill(j, "paladin_oath");
            double a = JobTraits.stanceOn(o, "paladin_oath") && oath != null ? oath.num("aura", 0.15) : trait(j, "aura", 0.05);
            best = Math.max(best, a);
        }
        return best;
    }

    /** 성채 안의 감소 (이 아군 p 에게) */
    public static double fortFor(ServerPlayer p) {
        if (FORTS.isEmpty()) return 0;
        long now = Tasks.now();
        double best = 0;
        for (Fort f : FORTS) {
            if (f.until() <= now || f.level() != p.level()) continue;
            if (Math.hypot(p.getX() - f.center().x, p.getZ() - f.center().z) > f.radius() + 0.3 || Math.abs(p.getY() - f.center().y) > 6) continue;
            ServerPlayer owner = RuneKit.player(f.level(), f.owner());
            if (owner != null && !ally(owner, p)) continue;
            best = Math.max(best, f.guard());
        }
        return best;
    }

    /** RpgHealth.sourceReduce: 수호 오라 · 성채 (가장 센 것 — Guards 와도 가장 센 것 하나) */
    private static double sourceReduce(ServerPlayer p, DamageSource src) {
        return Math.max(auraFor(p), fortFor(p));
    }

    /** 수호의 맹세: 1초마다 둘레 radius 칸 아군 (나 포함) 최대 체력 regen 회복 (금빛 십자 · 숫자) */
    private static void oathRegen(ServerPlayer sp, Jobs.Job j) {
        Jobs.Skill k = skill(j, "paladin_oath");
        if (k == null) return;
        ServerLevel level = sp.level();
        for (ServerPlayer o : allies(sp, sp.position(), k.num("radius", 6))) {
            if (heal(o, RpgHealth.maxOf(o) * k.num("regen", 0.01)) <= 0) continue;
            JobFx.healCross(level, V.up(o.position(), 2.25), 0.22, JobKit.glow(j), JobKit.core(j), 0.6, (o == sp ? JobFx.hide1p(sp) : Opt.empty()).with("vel",
                    V.add(RuneKit.bodyVel(o), new Vec3(0, 0.6, 0))));
        }
    }

    // ------------------------------------------------------------ 사제 「신앙」

    /** 지금 신앙 */
    public static int faith(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.faith;
    }

    /** 시험용: 신앙을 정함 */
    public static void setFaith(ServerPlayer sp, int n) {
        Jobs.Job j = JobSystem.job(sp);
        of(sp).faith = Math.max(0, Math.min(j == null || j.trait() == null ? 5 : j.trait().max(), n));
        JobTraits.sendGauge(sp, false);
    }

    /** 이번 시전의 위력 배율 (JobSystem): 사제의 공격 직업 스킬 (배율 합 &gt; 0) 이면 1 + per × 신앙 */
    static double castMult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k) {
        if (j == null || !j.id().equals("priest") || k == null || !(k.total() > 0)) return 1;
        State st = STATES.get(sp.getUUID());
        return st == null || st.faith <= 0 ? 1 : 1 + trait(j, "per", 0.1) * st.faith;
    }

    /** 직업 스킬이 나감 (JobSystem): 사제 — 공격 스킬은 신앙을 모두 씀, 회복 · 보호막 스킬은 +1 (손에 작은 십자) */
    static void castCommit(ServerPlayer sp, Jobs.Job j, Jobs.Skill k) {
        if (j == null || !j.id().equals("priest") || k == null) return;
        State st = of(sp);
        if (k.total() > 0) st.faith = 0;
        else {
            int max = j.trait() == null ? 5 : Math.max(1, j.trait().max());
            if (st.faith < max) {
                st.faith++;
                RuneKit.Hand h = RuneKit.handOf(sp);
                JobFx.healCross(sp.level(), V.up(h.hand(), 0.3), 0.14, j, 0.45, Opt.of("vel", V.add(RuneKit.bodyVel(sp), new Vec3(0, 0.8, 0))));
                if (st.faith == max) {
                    JobFx.eyeRing(sp, j, 2.4, 0.7, 0.4);
                    SkillFx.play(sp.level(), "block.amethyst_block.chime", sp.position(), 1.4, 0.8);
                }
            }
        }
        JobTraits.sendGauge(sp, false);
    }

    /** 홀 흡혈이 넘침 (WeaponTraits.stealOverflow): 사제 — 둘레 overflowRadius 칸에서 체력 비율이 가장 낮은 아군 (나 제외) 에게 */
    private static void overflow(ServerPlayer sp, double extra) {
        Jobs.Job j = jobIf(sp, "priest");
        if (j == null || !(extra > 0)) return;
        ServerPlayer best = null;
        double ratio = 1 - 1e-6;
        for (ServerPlayer o : allies(sp, sp.position(), trait(j, "overflowRadius", 4))) {
            if (o == sp || !RpgHealth.isRpgMode(o) || o.isDeadOrDying()) continue;
            double r = o.getHealth() / Math.max(1e-6, o.getMaxHealth());
            if (r < ratio) {
                ratio = r;
                best = o;
            }
        }
        if (best == null) return;
        if (heal(best, extra) <= 0) return;
        ServerLevel level = sp.level();
        if (RuneKit.fxQuota("jobOverflow" + sp.getId(), 1)) {
            JobFx.line(level, V.up(sp.position(), 1.2), V.up(best.position(), 1.1), j, 0.3, Opt.of("alpha", 0.8));
            JobFx.healCross(level, V.up(best.position(), 2.2), 0.2, j, 0.55, Opt.of("vel", V.add(RuneKit.bodyVel(best), new Vec3(0, 0.6, 0))));
        }
    }

    /** 성흔을 찍음 (PriestSkills): 그 적 dur 틱 — 그 적을 때린 아군은 준 피해 × rate 회복 */
    static void stigmaOn(ServerPlayer sp, LivingEntity e, int ticks, double rate) {
        STIGMA.put(e.getUUID(), new Stigma(sp.getUUID(), e, Tasks.now() + ticks, rate));
    }

    /** 이 적에 성흔이 있는지 (시험) */
    public static boolean stigmaOn(LivingEntity e) {
        Stigma s = e == null ? null : STIGMA.get(e.getUUID());
        return s != null && s.until() > Tasks.now();
    }

    /** 성흔 회복 (DamageNumbers.dealtHook): 성흔 찍힌 적을 때린 아군 — 숫자는 모아서 1초에 한 번 */
    private static void stigmaHeal(ServerPlayer attacker, LivingEntity victim, double dealt) {
        if (STIGMA.isEmpty() || victim == null || !(dealt > 0)) return;
        Stigma s = STIGMA.get(victim.getUUID());
        if (s == null) return;
        if (s.until() <= Tasks.now()) {
            STIGMA.remove(victim.getUUID());
            return;
        }
        ServerPlayer priest = RuneKit.player(attacker.level(), s.priest());
        if (priest != null && !ally(priest, attacker)) return;
        if (!attacker.isAlive() || attacker.isDeadOrDying()) return;
        double got = RpgHealth.healBy(attacker, dealt * s.rate());
        if (got > 0) of(attacker).stigmaAcc += got;
    }

    /** 새벽의 종: ticks 동안 쓰러지지 않음 */
    static void dawnFor(ServerPlayer pl, int ticks) {
        DAWN.merge(pl.getUUID(), Tasks.now() + ticks, Math::max);
    }

    /** 새벽의 종으로 쓰러지지 않는 중인지 */
    public static boolean dawnOn(ServerPlayer pl) {
        Long t = DAWN.get(pl.getUUID());
        return t != null && t > Tasks.now();
    }

    /** 새벽의 종이 쓰러짐을 막음 (다음 틱): 금빛 번쩍 · 종소리 · 글자 */
    private static void dawnSaved(ServerPlayer pl) {
        if (!RuneKit.fxQuota("jobDawnSaved" + pl.getId(), 1)) return;
        ServerLevel level = pl.level();
        Vec3 c = V.up(pl.position(), 1.0);
        Jobs.Job pr = Jobs.job("priest");
        if (pr != null) {
            JobFx.burst(level, c, 1.2, pr, 0.4, JobFx.hide1p(pl));
            JobFx.healCross(level, V.up(V.add(pl.getEyePosition(), pl.getViewVector(1F), 2.4), 0.2), 0.55, pr, 0.6, JobFx.only1p(pl));
        }
        SkillFx.play(level, "block.bell.use", c, 1.6, 0.8);
        Notify.notify(pl, "§e§l새벽의 가호 §r§7— 쓰러지지 않았습니다", 30);
    }

    // ------------------------------------------------------------ 수호기사 「반격」

    /** 지금 반격 게이지 (체력 양) */
    public static double counter(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.counter;
    }

    /** 반격 게이지 상한 (최대 체력 × cap) */
    public static double counterCap(ServerPlayer sp, Jobs.Job j) {
        return RpgHealth.maxOf(sp) * trait(j, "cap", 0.5);
    }

    /** 시험용: 반격 게이지를 정함 */
    public static void setCounter(ServerPlayer sp, double v) {
        Jobs.Job j = JobSystem.job(sp);
        of(sp).counter = Math.max(0, Math.min(counterCap(sp, j), v));
        JobTraits.sendGauge(sp, false);
    }

    /** 막음 (RpgHealth.blockHook): 수호기사 — 막아 준 양 × rate 가 반격 게이지로 (최대 체력 × cap 까지) */
    private static void onBlocked(ServerPlayer sp, double amount) {
        Jobs.Job j = jobIf(sp, "guardian");
        if (j == null || !(amount > 0)) return;
        State st = of(sp);
        double cap = counterCap(sp, j);
        double before = st.counter;
        st.counter = Math.min(cap, st.counter + amount * trait(j, "rate", 0.3));
        if (st.counter <= before + 1e-9) return;
        ServerLevel level = sp.level();
        if (RuneKit.fxQuota("jobCounter" + sp.getId(), 1)) {
            // 방패 앞 파란 불티 (1인칭 오른쪽 아래)
            RuneKit.Hand h = RuneKit.handOf(sp);
            SkillFx.fx(level, "rpg:arrow_spark", V.up(V.add(h.hand(), h.f(), 0.3), 0.2), Opt.of("dir", V.UP, "speed", 1.6, "size", 0.14, "grow", 0.8, "life", 0.35, "color",
                    JobKit.glow(j), "alpha", 1.0));
            if (before < cap && st.counter >= cap - 1e-6) {
                JobFx.eyeRing(sp, j, 2.4, 0.8, 0.4);
                JobFx.burst(level, V.up(V.add(h.hand(), h.f(), 0.5), 0.3), 0.35, j, 0.3, Opt.of("vel", RuneKit.bodyVel(sp)));
                SkillFx.play(level, "block.anvil.land", sp.position(), 1.8, 0.35);
            }
        }
        JobTraits.sendGauge(sp, false);
    }

    /** 반격 게이지를 씀 (JobKit — 직업 스킬 피해 한 번의 첫 타): 더할 피해 (0 = 없음, 안 들어가면 counterMissed 가 되돌림) */
    static double counterTake(ServerPlayer sp, Jobs.Job j) {
        if (j == null || !j.id().equals("guardian")) return 0;
        State st = STATES.get(sp.getUUID());
        if (st == null || !(st.counter > 0)) return 0;
        double c = st.counter;
        st.counter = 0;
        st.counterUsing = c;
        return c;
    }

    static void counterMissed(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        if (st == null || !(st.counterUsing > 0)) return;
        st.counter += st.counterUsing;
        st.counterUsing = 0;
    }

    /** 반격이 들어감: 방패 빛 터짐 · 글자 */
    static void counterLanded(ServerPlayer sp, LivingEntity e, Jobs.Job j, double add) {
        State st = STATES.get(sp.getUUID());
        if (st != null) st.counterUsing = 0;
        if (e.level() instanceof ServerLevel level) {
            JobFx.burst(level, Kit.bodyPoint(e), 1.0 + Kit.bodyPad(e) * 0.5, j, 0.35, Opt.empty());
            JobFx.aegis(level, Kit.bodyPoint(e), 0.45 + Kit.bodyPad(e) * 0.3, j, 2, 0.3, Opt.of("alpha", 0.85));
            SkillFx.play(level, "item.shield.block", Kit.bodyPoint(e), 0.7, 1.0);
            SkillFx.play(level, "block.anvil.land", Kit.bodyPoint(e), 1.4, 0.4);
        }
        DamageNumbers.showFace(sp, "§b§l반격 +" + DamageNumbers.number(add), 2.2, 0, 0.1);
        JobTraits.sendGauge(sp, false);
    }

    /** 철옹성 (RpgCombat.blockMod): 웅크려 막기가 정면 front · 옆 side */
    private static double bulwarkBlock(ServerPlayer sp, double dot) {
        if (!JobTraits.stanceOn(sp, "guardian_bulwark")) return 0;
        Jobs.Skill k = skill(jobIf(sp, "guardian"), "guardian_bulwark");
        if (k == null) return 0;
        if (dot > 0.2) return k.num("front", 1.0);
        return dot > -0.5 ? k.num("side", 0.6) : 0;
    }

    /** 철옹성 시작 (GuardianSkills): 태세 · 밀려나지 않음 · 이동 − slow */
    static void bulwarkStart(ServerPlayer sp, Jobs.Skill k, int dur) {
        JobTraits.startStance(sp, k, dur);
        RpgCombat.setSteadfast(sp, dur);
        speedMod(sp, k.num("slow", 0.4));
    }

    /** 수호 돌입: 이 아군 (ally) 이 받을 피해의 몫을 수호기사 (g) 가 ticks 동안 */
    static void shareStart(ServerPlayer g, ServerPlayer ally, Jobs.Skill k) {
        SHARES.put(ally.getUUID(), new Share(g.getUUID(), Tasks.now() + (long) k.num("shareFor", 80), k.num("share", 0.4), k.num("cut", 0.3)));
    }

    /** 이 아군을 지켜 주는 수호기사 (없으면 null — 시험) */
    public static UUID guardOf(ServerPlayer ally) {
        Share s = SHARES.get(ally.getUUID());
        return s == null || s.until() <= Tasks.now() ? null : s.guardian();
    }

    /**
     * RpgHealth.lossShare: 수호 돌입으로 지켜지는 아군이 받을 피해 loss 의 share 를 수호기사가 × (1 − cut) 로 (체력 1 아래로는 깎이지 않음 — 그만큼은 아군에게
     * 남음). 다른 플레이어 (결투) 의 피해 · 즉사 기믹 (최대 체력 5배 넘게) · 24칸 넘게 떨어짐은 나누지 않음. 아군에게서 덜어 낸 양
     */
    private static double share(ServerPlayer p, DamageSource src, double loss) {
        if (SHARES.isEmpty()) return 0;
        Share s = SHARES.get(p.getUUID());
        if (s == null) return 0;
        long now = Tasks.now();
        if (s.until() <= now) {
            SHARES.remove(p.getUUID());
            return 0;
        }
        if (src.getEntity() instanceof Player || loss >= RpgHealth.maxOf(p) * 5) return 0;
        ServerPlayer g = RuneKit.player(p.level(), s.guardian());
        if (g == null || g == p || !g.isAlive() || g.isDeadOrDying() || !RpgHealth.isRpgMode(g) || g.distanceTo(p) > 24) return 0;
        double moved = loss * s.share();
        double keep = 1 - Math.max(0, Math.min(0.95, s.cut()));
        double taken = RpgHealth.takeShared(g, moved * keep);
        if (!(taken > 0)) return 0;
        double relieved = Math.min(moved, taken / keep);
        ServerLevel level = p.level();
        if (RuneKit.fxQuota("jobShare" + p.getId(), 1)) {
            Jobs.Job gj = Jobs.job("guardian");
            if (gj != null) {
                JobFx.chain(level, V.up(g.position(), 1.1), V.up(p.position(), 1.1), gj, 2.0, 0.3, Opt.of("alpha", 1.0));
                JobFx.aegis(level, V.up(p.position(), 1.1), 0.4, gj, 2, 0.3, Opt.of("alpha", 0.8, "vel", RuneKit.bodyVel(p)));
            }
            SkillFx.play(level, "item.shield.block", g.position(), 1.2, 0.5);
        }
        DamageNumbers.show(g, taken, DamageNumbers.Style.PLAYER, "", 0);
        return relieved;
    }

    /** 성채 시작 (GuardianSkills) */
    static void fortStart(ServerPlayer sp, Vec3 center, double radius, int ticks, double guard) {
        FORTS.removeIf(f -> f.owner().equals(sp.getUUID()));
        FORTS.add(new Fort(sp.getUUID(), sp.level(), center, radius, Tasks.now() + ticks, guard));
    }

    static void fortEnd(ServerPlayer sp) {
        FORTS.removeIf(f -> f.owner().equals(sp.getUUID()));
    }

    // ------------------------------------------------------------ 무기로 맞힘 · 틱

    /** 망치 평타가 맞음 → 심판 +1 */
    private static void onWeaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        if (w == null || victim == null || !"hammer".equals(w.type())) return;
        Jobs.Job j = jobIf(sp, "paladin");
        if (j != null) judge(sp, victim, j, 1);
    }

    /** 사람마다 (JobTraits 가 4틱마다): 수호의 맹세 회복 (1초마다) */
    static void tick(ServerPlayer sp, Jobs.Job j, long now) {
        if (j.id().equals("paladin") && now % 20 == 0 && JobTraits.stanceOn(sp, "paladin_oath")) oathRegen(sp, j);
    }

    /** 서버 틱마다 (JobTraits): 머리 위 심판 · 성흔 다시 그림 (16틱마다), 수호 돌입 사슬 (4틱마다), 성흔 회복 숫자 (1초마다), 끝난 것 지움 */
    static void tickGlobal(MinecraftServer server, long now) {
        if (now % 16 == 0) {
            if (!JUDGE.isEmpty()) redrawJudges(now);
            else JUDGED.clear();
            if (!STIGMA.isEmpty()) redrawStigmas(now);
            if (!BOSS_JUDGE.isEmpty()) BOSS_JUDGE.values().removeIf(b -> b[0] <= now);
            FORTS.removeIf(f -> f.until() <= now);
            DAWN.values().removeIf(t -> t <= now);
        }
        if (!SHARES.isEmpty() && now % 4 == 0) drawShares(server, now);
        if (now % 20 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                State st = STATES.get(p.getUUID());
                if (st != null && st.stigmaAcc >= 0.5) {
                    DamageNumbers.show(p, st.stigmaAcc, DamageNumbers.Style.HEAL, "", 0);
                    st.stigmaAcc = 0;
                    st.stigmaShown = now;
                }
            }
        }
    }

    private static void redrawJudges(long now) {
        JUDGE.values().removeIf(m -> m[1] < now);
        JUDGED.values().removeIf(e -> e.isRemoved() || !e.isAlive());
        Jobs.Job pa = Jobs.job("paladin");
        if (pa == null) return;
        for (Map.Entry<String, long[]> en : JUDGE.entrySet()) {
            int bar = en.getKey().indexOf('|');
            if (bar < 0) continue;
            LivingEntity e = JUDGED.get(uuidOr(en.getKey().substring(bar + 1)));
            if (e == null || !(e.level() instanceof ServerLevel level) || !RuneKit.fxQuota("jobJudges", 12)) continue;
            ServerPlayer sp = RuneKit.player(level, uuidOr(en.getKey().substring(0, bar)));
            if (sp == null) continue;
            judgeFx(level, sp, e, pa, (int) en.getValue()[0], false);
        }
    }

    private static void redrawStigmas(long now) {
        Jobs.Job pr = Jobs.job("priest");
        for (Iterator<Stigma> it = STIGMA.values().iterator(); it.hasNext(); ) {
            Stigma s = it.next();
            LivingEntity e = s.e();
            if (s.until() <= now || e.isRemoved() || !e.isAlive()) {
                it.remove();
                continue;
            }
            if (pr == null || !(e.level() instanceof ServerLevel level) || !RuneKit.fxQuota("jobStigmas", 8)) continue;
            JobFx.stigma(level, RuneKit.overHead(e, 0.75), 0.42 + Kit.bodyPad(e) * 0.2, pr, 0.9, Opt.of("vel", RuneKit.entVel(e), "fade", 0.25, "alpha", 0.95));
        }
    }

    private static void drawShares(MinecraftServer server, long now) {
        Jobs.Job gj = Jobs.job("guardian");
        for (Iterator<Map.Entry<UUID, Share>> it = SHARES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Share> en = it.next();
            if (en.getValue().until() <= now) {
                it.remove();
                continue;
            }
            ServerPlayer ally = server.getPlayerList().getPlayer(en.getKey());
            ServerPlayer g = server.getPlayerList().getPlayer(en.getValue().guardian());
            if (ally == null || g == null || ally.level() != g.level() || gj == null || g.distanceTo(ally) > 24) continue;
            // 수호기사 가슴 → 아군 가슴의 빛 사슬 (둘 다 움직임 — 4틱마다 다시)
            JobFx.chain(g.level(), V.up(g.position(), 1.1), V.up(ally.position(), 1.1), gj, 2.0, 0.24, Opt.of("alpha", 0.7, "fade", 0.3));
        }
    }

    private static UUID uuidOr(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return new UUID(0, 0);
        }
    }

    // ------------------------------------------------------------ 태세 연출 · 끝 · 게이지

    /** 성기사 맹세의 날개 자리 (등 뒤 — 어깨 높이) */
    static Vec3 wingAt(ServerPlayer sp) {
        return V.up(V.add(sp.position(), Kit.facingOf(sp), -0.35), 1.35);
    }

    /** 태세 연출 (JobTraits — 8틱마다): 그린 태세면 true */
    static boolean stanceFx(ServerPlayer sp, Jobs.Job j, String stance) {
        ServerLevel level = sp.level();
        if (stance.equals("paladin_oath")) {
            // 등 뒤 빛의 날개 (주인마다 하나 — 다시 그릴 때 앞 그림을 바꿈) · 몸의 금빛 (JobTraits aura) · 3초마다 발밑 오라 범위
            JobFx.wing(level, wingAt(sp), Kit.facingOf(sp), 1.5, j, 0.6, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.7, "coreAlpha", 0.8, "solo",
                    3.0, "phase", (double) ((Tasks.now() / 4) % JobFx.WING)));
            if (Tasks.now() % 48 == 0) {
                Jobs.Skill k = skill(j, "paladin_oath");
                JobFx.ring(level, V.up(sp.position(), 0.06), k == null ? 6 : k.num("radius", 6), j, false, 0.6, Opt.of("alpha", 0.5, "vel", RuneKit.bodyVel(sp)));
            }
            return true;
        }
        if (stance.equals("guardian_bulwark")) {
            // 앞의 큰 빛의 방패 (3인칭 · 다른 사람 — 주인마다 하나, 0.47.0 몸은 입체 방패 · 이 그림은 옅은 빛) · 1인칭 화면 아래 옅은 방패 테
            Vec3 f = Kit.facingOf(sp);
            JobFx.aegis(level, V.up(V.add(sp.position(), f, 0.95), 1.05), 0.62, j, 1, 0.6, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.3, "coreAlpha",
                    0.4, "solo", 3.0));
            Vec3 look = sp.getViewVector(1F);
            JobFx.aegis(level, V.add(V.add(sp.getEyePosition(), look, 2.2), JobFx.screenUp(look), -1.05), 0.42, j, 1, 0.6, JobFx.only1p(sp).with("vel",
                    RuneKit.bodyVel(sp), "alpha", 0.35, "coreAlpha", 0.45, "solo", 4.0));
            return true;
        }
        return false;
    }

    /** 태세가 끝남 (JobTraits): 철옹성 → 이동 수정치를 뗌 · 방패가 깨지며 사라짐, 맹세 → 날개가 흩어짐 */
    static void stanceEnded(ServerPlayer sp, String id) {
        if (id.equals("guardian_bulwark")) {
            speedMod(sp, 0);
            JobFx3d.drop(sp, "guardian_bulwark", 0.15); // 0.47.0 입체 방패도 사라짐
            Jobs.Job j = jobIf(sp, "guardian");
            if (j != null) {
                JobFx.aegis(sp.level(), V.up(V.add(sp.position(), Kit.facingOf(sp), 0.95), 1.05), 0.62, j, 2, 0.35, JobFx.hide1p(sp).with("alpha", 0.7));
                SkillFx.play(sp.level(), "block.glass.break", sp.position(), 1.3, 0.5);
            }
        } else if (id.equals("paladin_oath")) {
            Jobs.Job j = jobIf(sp, "paladin");
            if (j != null) {
                SkillFx.sparks(sp.level(), wingAt(sp), JobKit.glow(j), 10, 4);
                SkillFx.play(sp.level(), "block.amethyst_block.chime", sp.position(), 0.9, 0.6);
            }
        }
    }

    /** 게이지 값: 성기사 심판 (마지막 대상) · 사제 신앙 · 수호기사 반격 (0 ~ 100 — 상한 대비) */
    static int gaugeValue(ServerPlayer sp, Jobs.Job j) {
        State st = STATES.get(sp.getUUID());
        if (st == null) return 0;
        return switch (j.id()) {
            case "paladin" -> {
                if (st.target == null) yield 0;
                Entity e = sp.level().getEntity(st.target);
                yield e == null || e.isRemoved() || !e.isAlive() ? 0 : judges(sp, e);
            }
            case "priest" -> st.faith;
            case "guardian" -> {
                double cap = counterCap(sp, j);
                yield cap <= 0 ? 0 : (int) Math.round(Math.min(1, st.counter / cap) * 100);
            }
            default -> 0;
        };
    }
}
