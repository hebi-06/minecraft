package mmorpg.fabric.relic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;
import mmorpg.core.Combat;
import mmorpg.core.Relics;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.SwingArea;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.job.JobKit;
import mmorpg.fabric.motion.MotionSystem;
import mmorpg.fabric.rune.RuneAbyss;
import mmorpg.fabric.rune.RuneCast;
import mmorpg.fabric.rune.RuneGrove;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.stattree.StatTrees;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 0.43.0 아티팩트 패시브 열 (단계 = {@link RelicSystem#tier} — 판에서 같은 계열이 3 · 5 · 7칸 이어진 묶음 안이면 1 · 2 · 3단계, 값은 rules_more RELICS.types.*.passive):
 * <ul>
 * <li>메긴교르드 「두 배의 힘」: 평타 every 번째 (근접 = 휘두르기 하나 — 그 휘두르기의 조준 · 범위 적 모두, 활 · 완드 = 맞힘 하나) 피해 × mult — 평타에 더한 몫
 * (금빛 ⚡ 숫자 · 벼락)</li>
 * <li>스키드블라드니르 「순풍」: 이동 속도 (판 합 — Relics adds) · 구른 뒤 afterSec 초 동안 가하는 피해 + dealt (곱)</li>
 * <li>이둔의 사과 「젊음의 사과」: 체력이 below % 아래로 떨어지는 순간 최대 체력 heal % 회복 (cooldownSec 초에 한 번)</li>
 * <li>미미르의 샘 「지혜의 일격」: 룬 피해 한 번마다 chance % 로 × mult (하늘빛 ✦ 숫자)</li>
 * <li>야른그레이프르 「쇠장갑」: 무기 스킬 · 직업 스킬 피해 + skill (Kit.skillMult)</li>
 * <li>에기스햘름 「공포」: range 칸 안의 적 (플레이어 아닌) 이 주는 피해 − cut (RpgHealth.sourceReduce — 다른 쪽 감소와는 센 것 하나)</li>
 * <li>드라우프니르 「아홉째 밤」: 평타 every 번째에 맞힌 적 위로 금고리 rings 개 → 둘레 radius 칸 적마다 위력 P × mult × rings (마법 · 한 번에, 금빛 숫자)</li>
 * <li>걀라르호른 「파수꾼의 나팔」: 적에게 피해를 주면 울림 (cooldownSec 초마다) — radius 칸 안 아군 (나 포함) durationSec 초 동안 가하는 피해 + dealt ·
 * 받는 피해 − cut. 가하는 피해는 세계수의 가호와 센 것 하나, 받는 피해는 룬 · 스킬 감소와 센 것 하나 (RpgHealth.buffReduce)</li>
 * <li>글레이프니르 「묶는 끈」: 평타로 맞힌 적을 durationSec 초 묶음 (보스가 아니면 느림 slow + 1) · 묶인 적이 받는 피해 + taken (모든 플레이어 — 심연의 낙인과
 * 센 것 하나, 숫자 앞 ∞) · 같은 적은 againSec 초에 한 번</li>
 * <li>브리싱가멘 「여신의 축복」: 룬을 쓰면 보호막 = 최대 체력 × shield % (durationSec 초 — 쌓이지 않고 그만큼까지)</li>
 * </ul>
 */
public final class RelicPassives {
    private RelicPassives() {}

    /** 지혜의 일격 확률 난수 (0 이상 1 미만 — 시험이 정할 수 있게) */
    public static DoubleSupplier rand = Math::random;

    /** 플레이어마다 셈 · 재사용 대기 */
    static final class State {
        long meginKey = Long.MIN_VALUE;
        int meginCount;
        long meginHit = Long.MIN_VALUE;
        long draupKey = Long.MIN_VALUE;
        int draupCount;
        long idunnReady;
        long hornReady;
        long rolledAt = -1000;
        long fearFx;
    }

    /** 나팔의 가호 (받은 사람): 끝나는 틱 · 가하는 피해 + · 받는 피해 − (비율) */
    record HornBuff(long until, double dealt, double cut) {}

    /** 묶인 적: 끝나는 틱 · 다시 묶을 수 있는 틱 · 받는 피해 + (비율) */
    record Bind(long until, long again, double taken) {}

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, HornBuff> HORN = new ConcurrentHashMap<>();
    private static final Map<UUID, Bind> BINDS = new ConcurrentHashMap<>();
    private static long rangedSeq;

    private static final double[] GOLD = {1.0, 0.82, 0.3};
    private static final double[] WHITE = {1, 1, 1};
    private static final double[] AZURE = {0.45, 0.8, 1.0};
    private static final double[] WIND = {0.75, 0.95, 0.9};
    private static final double[] APPLE = {1.0, 0.78, 0.2};
    private static final double[] SILVER = {0.8, 0.86, 1.0};
    private static final double[] ROSE = {1.0, 0.55, 0.45};
    private static final double[] FEAR = {0.55, 0.05, 0.08};

    static void init() {
        RpgCombat.addScaledBonusHook(RelicPassives::meginBonus);
        RpgCombat.addWeaponHitHook(RelicPassives::weaponHit);
        java.util.function.Consumer<ServerPlayer> roll = MotionSystem.onRoll;
        MotionSystem.onRoll = sp -> {
            roll.accept(sp);
            rolled(sp);
        };
        java.util.function.ToDoubleFunction<ServerPlayer> dm = RpgCombat.dealtMult;
        RpgCombat.dealtMult = p -> dealtMult(p, dm.applyAsDouble(p));
        java.util.function.ToDoubleFunction<ServerPlayer> br = RpgHealth.buffReduce;
        RpgHealth.buffReduce = p -> Math.max(br.applyAsDouble(p), hornCut(p));
        java.util.function.ToDoubleBiFunction<ServerPlayer, DamageSource> sr = RpgHealth.sourceReduce;
        RpgHealth.sourceReduce = (p, src) -> Math.max(sr.applyAsDouble(p, src), fearCut(p, src));
        RpgHealth.LossHook lh = RpgHealth.lossHook;
        RpgHealth.lossHook = (sp, src, loss, before, after) -> {
            lh.lost(sp, src, loss, before, after);
            apple(sp, before, after);
        };
        java.util.function.BiFunction<ServerPlayer, LivingEntity, RuneKit.HitMod> rm = RuneKit.runeHitMod;
        RuneKit.runeHitMod = (sp, e) -> wisdom(sp, e, rm.apply(sp, e));
        java.util.function.ToDoubleFunction<ServerPlayer> km = Kit.skillMult;
        Kit.skillMult = p -> km.applyAsDouble(p) * (1 + gloves(p));
        DamageNumbers.DealtHook dh = DamageNumbers.dealtHook;
        DamageNumbers.dealtHook = (a, v, d) -> {
            dh.dealt(a, v, d);
            horn(a, v);
        };
        java.util.function.ToDoubleFunction<LivingEntity> tm = RpgCombat.takenMult;
        RpgCombat.takenMult = e -> bindTaken(e, tm.applyAsDouble(e));
        java.util.function.Function<LivingEntity, String> vm = DamageNumbers.victimMark;
        DamageNumbers.victimMark = e -> vm.apply(e) + bindMark(e);
        java.util.function.BiConsumer<ServerPlayer, mmorpg.core.Runes.Rune> oc = RuneCast.onCast;
        RuneCast.onCast = (sp, r) -> {
            oc.accept(sp, r);
            necklace(sp);
        };
        ServerTickEvents.END_SERVER_TICK.register(RelicPassives::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            STATES.remove(handler.player.getUUID());
            HORN.remove(handler.player.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            STATES.clear();
            HORN.clear();
            BINDS.clear();
        });
    }

    private static State state(ServerPlayer sp) {
        return STATES.computeIfAbsent(sp.getUUID(), k -> new State());
    }

    private static Relics.Passive passive(String type) {
        Relics.Type t = Relics.type(type);
        return t == null ? null : t.passive();
    }

    /** 이 사람의 이 패시브 단계 (0 = 꺼짐) */
    private static int tier(ServerPlayer sp, String type) {
        try {
            return sp == null || sp.isRemoved() ? 0 : RelicSystem.tier(sp, type);
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[mmorpg] relic tier", e);
            return 0;
        }
    }

    private static double pct(String type, String key, int tier) {
        Relics.Passive p = passive(type);
        return p == null ? 0 : p.value(key, tier) / 100.0;
    }

    private static int ticks(String type, String key, double fallbackSec) {
        Relics.Passive p = passive(type);
        return (int) Math.round((p == null ? fallbackSec : p.num(key, fallbackSec)) * 20);
    }

    /** 평타 셈 열쇠: 근접 = 이 휘두르기의 틱 (조준 · 범위 적 모두 같음), 활 · 완드 = 맞힘마다 새 열쇠 */
    private static long swingKey(ServerPlayer sp, Weapon w) {
        if (w != null && Combat.isMeleeWeapon(w)) {
            long sw = SwingArea.swingTick(sp);
            if (sw >= 0) return sw;
        }
        return -(++rangedSeq) - 1;
    }

    // ------------------------------------------------------------ 메긴교르드 「두 배의 힘」

    private static RpgCombat.Bonus meginBonus(ServerPlayer sp, LivingEntity victim, Weapon w, double dmg) {
        int t = tier(sp, "megingjord");
        if (t <= 0 || !(dmg > 0)) return null;
        State st = state(sp);
        long key = swingKey(sp, w);
        if (key != st.meginKey) {
            st.meginKey = key;
            st.meginCount++;
            int every = (int) Math.max(1, passive("megingjord").num("every", 5));
            if (st.meginCount >= every) {
                st.meginCount = 0;
                st.meginHit = key;
                meginFx(sp, victim);
            }
        }
        if (st.meginHit != key) return null;
        double k = passive("megingjord").value("mult", t) - 1;
        if (!(k > 0)) return null;
        return new RpgCombat.Bonus(dmg * k, "§6⚡", "relic_megingjord", DamageNumbers.Style.MIGHT, List.of());
    }

    /** 다섯째 평타: 맞은 적 위로 금빛 벼락 · 고리 · 천둥 */
    private static void meginFx(ServerPlayer sp, LivingEntity victim) {
        if (!(victim.level() instanceof ServerLevel level)) return;
        Vec3 at = victim.position();
        Vec3 top = V.up(at, 3.6);
        SkillFx.bolt(level, top, V.up(at, 0.2), GOLD, Opt.of("jag", 0.5, "thick", 0.16, "life", 0.22));
        SkillFx.bolt(level, top, V.up(at, 0.2), WHITE, Opt.of("jag", 0.3, "thick", 0.06, "life", 0.18));
        SkillFx.shock(level, at, GOLD, 1.1, 0.3);
        SkillFx.sparks(level, V.up(at, victim.getBbHeight() * 0.6), GOLD, 8, 5);
        SkillFx.play(level, "item.trident.thunder", at, 1.6, 0.35);
    }

    /** 다음 평타가 다섯째인지 · 남은 수 (시험 · 창) */
    public static int meginCount(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.meginCount;
    }

    // ------------------------------------------------------------ 무기로 맞힘 (드라우프니르 · 글레이프니르)

    private static void weaponHit(ServerPlayer sp, LivingEntity victim, Weapon w, RpgCombat.WeaponBonus bonus) {
        int td = tier(sp, "draupnir");
        if (td > 0) {
            State st = state(sp);
            long key = swingKey(sp, w);
            if (key != st.draupKey) {
                st.draupKey = key;
                st.draupCount++;
                int every = (int) Math.max(1, passive("draupnir").num("every", 9));
                if (st.draupCount >= every) {
                    st.draupCount = 0;
                    rings(sp, victim, td);
                }
            }
        }
        int tg = tier(sp, "gleipnir");
        if (tg > 0) bind(sp, victim, tg);
    }

    /** 드라우프니르 셈 (시험) */
    public static int draupCount(ServerPlayer sp) {
        State st = STATES.get(sp.getUUID());
        return st == null ? 0 : st.draupCount;
    }

    /** 아홉째 평타: 맞은 적 위 하늘에서 금고리 rings 개가 차례로 떨어져 (2틱마다) → 둘레 radius 칸 적마다 위력 × mult × rings */
    static void rings(ServerPlayer sp, LivingEntity victim, int tier) {
        if (!(victim.level() instanceof ServerLevel level)) return;
        Relics.Type ty = Relics.type("draupnir");
        Relics.Passive p = ty.passive();
        int n = (int) Math.max(1, p.num("rings", 8));
        double radius = p.num("radius", 3);
        Vec3 c = victim.position();
        double each = Relics.power(RpgStats.effective(sp), ty) * p.value("mult", tier);
        String life = RuneKit.lifeToken(sp);
        SkillFx.play(level, "block.amethyst_block.resonate", c, 1.3, 0.8);
        for (int i = 0; i < n; i++) {
            final int ii = i;
            double a = Math.PI * 2 * i / n;
            Vec3 spot = new Vec3(c.x + Math.cos(a) * radius * 0.55, c.y, c.z + Math.sin(a) * radius * 0.55);
            for (int k = 0; k < 3; k++) {
                final int kk = k;
                Tasks.later(i * 2 + k, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    Vec3 at = V.up(spot, 2.6 - kk * 1.0);
                    SkillFx.ring(level, at, V.UP, GOLD, 0.42, 0.22, 0);
                    if (kk == 2) {
                        SkillFx.sparks(level, V.up(spot, 0.2), GOLD, 4, 3);
                        SkillFx.play(level, "random.orb", spot, 1.1 + ii * 0.08, 0.5);
                    }
                });
            }
        }
        int land = n * 2 + 2;
        Tasks.later(land, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            SkillFx.shock(level, c, GOLD, radius, 0.45);
            SkillFx.burst(level, V.up(c, 0.6), GOLD, 1.4, 0.35);
            SkillFx.play(level, "block.bell.use", c, 1.6, 0.7);
            double total = each * n * StatTrees.atkMult(sp) * RpgCombat.dealtMult.applyAsDouble(sp);
            for (LivingEntity e : Kit.around(sp, c, radius, 3)) {
                if (!Kit.splashFoe(sp, e)) continue;
                long at = Kit.reserveHit(sp, e, Tasks.now());
                Kit.hitAt(sp, e, at, () -> RuneKit.sameLife(sp, life),
                        () -> Kit.applySkillDamage(sp, e, total, false, Kit.Cause.MAGIC, DamageNumbers.Style.RING));
            }
        });
    }

    // ------------------------------------------------------------ 글레이프니르 「묶는 끈」

    private static void bind(ServerPlayer sp, LivingEntity victim, int tier) {
        if (victim == null || victim.isRemoved() || !victim.isAlive() || victim instanceof Player) return;
        long now = Tasks.now();
        Bind b = BINDS.get(victim.getUUID());
        if (b != null && now < b.again()) return;
        int dur = ticks("gleipnir", "durationSec", 3);
        int again = ticks("gleipnir", "againSec", 6);
        BINDS.put(victim.getUUID(), new Bind(now + dur, now + again, pct("gleipnir", "taken", tier)));
        if (!JobKit.isBoss(victim)) {
            int slow = (int) passive("gleipnir").num("slow", 1);
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, dur, Math.max(0, slow), false, false, true), sp);
        }
        if (victim.level() instanceof ServerLevel level) {
            chains(level, victim, 0.5);
            SkillFx.play(level, "block.chain.place", victim.position(), 1.4, 0.8);
        }
    }

    /** 몸을 감는 끈 (사각 고리 둘 + 비스듬한 둘 — 은빛 사슬) */
    private static void chains(ServerLevel level, LivingEntity e, double life) {
        Vec3 c = e.position();
        double w = e.getBbWidth() * 0.5 + 0.25;
        double h = e.getBbHeight();
        for (double y : new double[] {h * 0.35, h * 0.7}) {
            Vec3[] p = {new Vec3(c.x - w, c.y + y, c.z - w), new Vec3(c.x + w, c.y + y, c.z - w), new Vec3(c.x + w, c.y + y, c.z + w), new Vec3(c.x - w, c.y + y, c.z + w)};
            for (int i = 0; i < 4; i++) chain(level, p[i], p[(i + 1) % 4], life);
        }
        chain(level, new Vec3(c.x - w, c.y + h * 0.15, c.z), new Vec3(c.x + w, c.y + h * 0.85, c.z), life);
        chain(level, new Vec3(c.x, c.y + h * 0.15, c.z + w), new Vec3(c.x, c.y + h * 0.85, c.z - w), life);
    }

    private static void chain(ServerLevel level, Vec3 a, Vec3 b, double life) {
        Vec3 d = V.sub(b, a);
        double len = V.len(d);
        if (len < 0.1) return;
        Vec3 mid = V.add(a, d, 0.5);
        RuneKit.rfx2(level, "chain", mid, Opt.of("dir", V.scale(d, 1 / len), "size", len / 2 * 1.04, "life", life, "fade", 0.3, "alpha", 0.9), SILVER, WHITE);
    }

    /** 묶였는지 (시험) */
    public static boolean bound(LivingEntity e) {
        Bind b = e == null ? null : BINDS.get(e.getUUID());
        return b != null && Tasks.now() < b.until();
    }

    private static double bindTaken(LivingEntity e, double base) {
        Bind b = e == null ? null : BINDS.get(e.getUUID());
        if (b == null || Tasks.now() >= b.until() || !(b.taken() > 0)) return base;
        double abyss = Math.max(1, RuneAbyss.takenMult(e));
        double mine = 1 + b.taken();
        return mine > abyss ? base / abyss * mine : base;
    }

    private static String bindMark(LivingEntity e) {
        return bound(e) ? "§b∞" : "";
    }

    // ------------------------------------------------------------ 스키드블라드니르 「순풍」 · 걀라르호른 「파수꾼의 나팔」 (가하는 피해)

    private static void rolled(ServerPlayer sp) {
        int t = tier(sp, "skidbladnir");
        if (t <= 0) return;
        state(sp).rolledAt = Tasks.now();
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        SkillFx.burst(level, V.up(c, 0.4), WIND, 1.2, 0.3);
        SkillFx.particle(level, ParticleTypes.CLOUD, V.up(c, 0.3), 8, 0.35, 0.05);
        SkillFx.playFor(sp, "random.bow", 1.6, 0.5);
    }

    /** 구른 뒤 더하는 몫 (비율 — 지금 켜져 있지 않으면 0) */
    static double windUp(ServerPlayer sp) {
        int t = tier(sp, "skidbladnir");
        if (t <= 0) return 0;
        State st = STATES.get(sp.getUUID());
        if (st == null || Tasks.now() - st.rolledAt > ticks("skidbladnir", "afterSec", 2)) return 0;
        return pct("skidbladnir", "dealt", t);
    }

    static double hornUp(ServerPlayer sp) {
        HornBuff h = HORN.get(sp.getUUID());
        return h == null || Tasks.now() >= h.until() ? 0 : h.dealt();
    }

    private static double hornCut(ServerPlayer sp) {
        HornBuff h = HORN.get(sp.getUUID());
        return h == null || Tasks.now() >= h.until() ? 0 : h.cut();
    }

    /** 가하는 피해: 앞 배율 (가호 포함) 에서 가호를 나팔과 센 것 하나로 · 순풍은 곱 */
    private static double dealtMult(ServerPlayer p, double base) {
        double out = base;
        double h = hornUp(p);
        if (h > 0) {
            double grace = Math.max(1, RuneGrove.dealtMult(p));
            if (1 + h > grace) out = out / grace * (1 + h);
        }
        double w = windUp(p);
        return w > 0 ? out * (1 + w) : out;
    }

    /** 적에게 피해를 줌: 나팔이 준비되었으면 울림 */
    private static void horn(ServerPlayer sp, LivingEntity victim) {
        if (sp == null || victim == null || victim instanceof Player) return;
        int t = tier(sp, "gjallarhorn");
        if (t <= 0) return;
        State st = state(sp);
        long now = Tasks.now();
        if (now < st.hornReady) return;
        st.hornReady = now + ticks("gjallarhorn", "cooldownSec", 40);
        blow(sp, t);
    }

    /** 나팔: radius 칸 안 아군 (나 포함 · 결투 상대 빼고) 에게 durationSec 초 — 더 센 나팔이 이미 있으면 그대로 */
    static void blow(ServerPlayer sp, int tier) {
        ServerLevel level = sp.level();
        Relics.Passive p = passive("gjallarhorn");
        double radius = p.num("radius", 12);
        long until = Tasks.now() + ticks("gjallarhorn", "durationSec", 10);
        double dealt = pct("gjallarhorn", "dealt", tier);
        double cut = pct("gjallarhorn", "cut", tier);
        List<ServerPlayer> got = new ArrayList<>();
        for (ServerPlayer o : level.players()) {
            if (o.isSpectator() || o.distanceTo(sp) > radius) continue;
            if (o != sp && Kit.duelPartner.test(sp, o)) continue;
            HornBuff cur = HORN.get(o.getUUID());
            if (cur != null && Tasks.now() < cur.until() && cur.dealt() > dealt) continue;
            HORN.put(o.getUUID(), new HornBuff(until, dealt, cut));
            got.add(o);
        }
        Vec3 c = sp.position();
        SkillFx.play(level, "item.goat_horn.sound.5", c, 1.0, 1.4);
        for (int i = 0; i < 3; i++) {
            final int ii = i;
            Tasks.later(i * 4, () -> SkillFx.shock(level, c, GOLD, 2.5 + ii * 3.5, 0.5));
        }
        String text = "§6「" + p.name() + "」 §e가하는 피해 +" + mmorpg.core.Js.str(Math.round(dealt * 1000) / 10.0) + "% §7· §b받는 피해 −"
                + mmorpg.core.Js.str(Math.round(cut * 1000) / 10.0) + "% §7(" + Math.round((until - Tasks.now()) / 20.0) + "초)";
        for (ServerPlayer o : got) {
            Notify.notify(o, text, 50);
            SkillFx.sparks(level, V.up(o.position(), 1.2), GOLD, 6, 2);
        }
    }

    /** 나팔의 가하는 피해 몫 (시험) */
    public static double hornDealt(ServerPlayer sp) {
        return hornUp(sp);
    }

    // ------------------------------------------------------------ 이둔의 사과 · 에기스햘름 · 브리싱가멘 (받는 쪽)

    private static void apple(ServerPlayer sp, double before, double after) {
        if (sp == null || sp.isRemoved() || !(after > 0)) return;
        int t = tier(sp, "idunn");
        if (t <= 0) return;
        double max = RpgHealth.maxOf(sp);
        if (!(max > 0)) return;
        double below = passive("idunn").num("below", 35) / 100.0;
        if (before / max < below || after / max >= below) return;
        State st = state(sp);
        long now = Tasks.now();
        if (now < st.idunnReady) return;
        st.idunnReady = now + ticks("idunn", "cooldownSec", 60);
        double healed = RpgHealth.healBy(sp, max * pct("idunn", "heal", t));
        ServerLevel level = sp.level();
        Vec3 at = V.up(sp.position(), 1.2);
        SkillFx.burst(level, at, APPLE, 1.3, 0.4);
        SkillFx.sparks(level, at, APPLE, 10, 3);
        SkillFx.vanilla(level, "minecraft:totem_particle", at);
        SkillFx.play(level, "random.levelup", sp.position(), 1.6, 0.5);
        if (healed > 0) DamageNumbers.showAt(level, V.up(sp.position(), sp.getBbHeight() + 0.4), "§a§l+" + Math.round(healed) + " §6젊음의 사과");
    }

    private static double fearCut(ServerPlayer p, DamageSource src) {
        if (p == null || src == null) return 0;
        Entity a = src.getEntity();
        if (!(a instanceof LivingEntity le) || a instanceof Player || a == p) return 0;
        int t = tier(p, "aegishjalmr");
        if (t <= 0) return 0;
        double range = passive("aegishjalmr").num("range", 4);
        if (le.distanceTo(p) > range + le.getBbWidth() * 0.5) return 0;
        State st = state(p);
        long now = Tasks.now();
        if (now - st.fearFx >= 10 && le.level() instanceof ServerLevel level) {
            st.fearFx = now;
            SkillFx.particle(level, ParticleTypes.SMOKE, V.up(le.position(), le.getBbHeight() + 0.2), 5, 0.2, 0.01);
            SkillFx.sparks(level, V.up(le.position(), le.getBbHeight() * 0.8), FEAR, 4, 1.5);
        }
        return pct("aegishjalmr", "cut", t);
    }

    private static void necklace(ServerPlayer sp) {
        int t = tier(sp, "brisingamen");
        if (t <= 0) return;
        double max = RpgHealth.maxOf(sp);
        double amount = max * pct("brisingamen", "shield", t);
        if (!(amount > 0)) return;
        double added = RpgHealth.addShield(sp, amount, ticks("brisingamen", "durationSec", 5), amount);
        if (added > 0) {
            ServerLevel level = sp.level();
            SkillFx.ring(level, V.up(sp.position(), 1.0), V.UP, ROSE, 1.1, 0.35, 0);
            SkillFx.sparks(level, V.up(sp.position(), 1.3), ROSE, 5, 2);
            SkillFx.playFor(sp, "block.amethyst_block.chime", 1.6, 0.6);
        }
    }

    // ------------------------------------------------------------ 미미르의 샘 · 야른그레이프르 (스킬 · 룬)

    private static RuneKit.HitMod wisdom(ServerPlayer sp, LivingEntity target, RuneKit.HitMod m) {
        int t = tier(sp, "mimir");
        if (t <= 0) return m;
        if (rand.getAsDouble() >= pct("mimir", "chance", t)) return m;
        double k = passive("mimir").value("mult", t);
        if (target != null && target.level() instanceof ServerLevel level) {
            Vec3 at = V.up(target.position(), target.getBbHeight() * 0.7);
            SkillFx.sparks(level, at, AZURE, 8, 3);
            SkillFx.burst(level, at, AZURE, 0.8, 0.25);
            SkillFx.play(level, "block.amethyst_block.resonate", at, 1.8, 0.6);
        }
        return new RuneKit.HitMod(m.mult() * k, DamageNumbers.Style.WISDOM);
    }

    private static double gloves(ServerPlayer sp) {
        int t = tier(sp, "jarngreipr");
        return t <= 0 ? 0 : pct("jarngreipr", "skill", t);
    }

    // ------------------------------------------------------------ 틱 (묶인 적 끈 · 정리)

    private static void tick(MinecraftServer server) {
        long now = Tasks.now();
        if (now % 10 != 0) return;
        BINDS.entrySet().removeIf(e -> now >= e.getValue().again() + 200);
        HORN.entrySet().removeIf(e -> now >= e.getValue().until() + 200);
        if (BINDS.isEmpty()) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (Map.Entry<UUID, Bind> e : BINDS.entrySet()) {
                if (now >= e.getValue().until()) continue;
                Entity en = level.getEntity(e.getKey());
                if (en instanceof LivingEntity le && le.isAlive()) chains(level, le, 0.55);
            }
        }
    }

    /** 시험: 이 사람의 셈 · 재사용 대기 · 나팔을 지움 */
    public static void testReset(ServerPlayer sp) {
        STATES.remove(sp.getUUID());
        HORN.remove(sp.getUUID());
    }

    /** 시험: 이 사람의 나팔의 가호만 지움 (셈 · 재사용 대기는 그대로) */
    public static void testClearHorn(ServerPlayer sp) {
        if (sp != null) HORN.remove(sp.getUUID());
    }

    /** 시험: 이 적의 묶임을 지움 */
    public static void testUnbind(LivingEntity e) {
        if (e != null) BINDS.remove(e.getUUID());
    }
}
