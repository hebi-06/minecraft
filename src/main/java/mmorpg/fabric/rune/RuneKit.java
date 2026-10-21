package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.AddonFx;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 룬 공통 도구 (runes.js 의 "피해 · 도구" · "v1.4.0 도구" · 룬 연출 그림 (rfx) · 초월 룬 2차 강화 (하늘 · 땅 연출) · 삶 표시).
 * 애드온 코드를 거의 줄마다 옮길 수 있게 이름 · 계산 · 기본값을 애드온과 같게 둠. 스킬 도구 (Kit · SkillFx · V · Opt) 와 함께 씀.
 */
public final class RuneKit {
    private RuneKit() {}

    public static final Obj RUNE = Rules.get().obj("RUNE");
    public static final Obj ULT_FX = Rules.get().obj("ULT_FX");
    static final Obj BOW = Rules.get().obj("BOW");

    public static final Vec3 V_UP = V.UP;
    public static final Vec3 V_DOWN = V.DOWN;
    public static final Vec3 V_ZERO = Vec3.ZERO;
    public static final double[] ROCK = {0.55, 0.4, 0.28};
    public static final double[][] FIRE = {{1.0, 0.46, 0.12}, {1.0, 0.9, 0.55}};
    public static final double[][] ICE = {{0.55, 0.86, 1.0}, {0.93, 0.98, 1.0}};
    public static final double[] STEEL = {0.78, 0.84, 0.95};
    public static final double[] WHITE = {1, 1, 1};
    /** 피의 칼날 베기 속 (밝은 분홍) */
    public static final double[] BLOOD_CORE = {1.0, 0.74, 0.76};
    /** 뽑기 연출 · 아이템 이름 색 (등급) */
    public static final Map<String, double[]> RUNE_GRADE_FX = Map.of(
            "common", new double[] {0.88, 0.88, 0.94},
            "rare", new double[] {0.36, 0.6, 1.0},
            "noble", new double[] {0.72, 0.42, 1.0},
            "hero", new double[] {1.0, 0.62, 0.16},
            "transcend", new double[] {1.0, 0.26, 0.26});
    public static final List<String> GRADE_ORDER = List.of("common", "rare", "noble", "hero", "transcend");

    static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayer sp)) return;
            lifeEnded(sp.getUUID());
            DEATHS.merge(sp.getUUID(), 1, Integer::sum);
        });
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> lifeEnded(player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            LIVES.clear();
            DEATHS.clear();
            AURA_DRAWN.clear();
            QUOTA.clear();
        });
    }

    static void forget(UUID id) {
        LIVES.remove(id);
        DEATHS.remove(id);
        AURA_DRAWN.remove(id);
    }

    /** 계열 색 {빛, 심지} (RUNE.color[계열]) */
    public static double[][] colorOf(String family) {
        List<Object> pair = RUNE.obj("color").listOrNull(family);
        if (pair == null) return new double[][] {WHITE, WHITE};
        return new double[][] {Kit.rgb(pair.get(0)), Kit.rgb(pair.get(1))};
    }

    // ------------------------------------------------------------ 피해 · 도구

    /**
     * 룬 피해 한 번 (P × mult). 실제로 깎인 체력 (0 = 안 들어감). 피해 종류: 마력 (stat int) = 마법, 나머지 = 공격 (cause 로 바꿈).
     * style = 숫자 모양 (기본 노랑 RUNE, 마검사의 마력은 보라 SPELL)
     */
    public static double runeHit(ServerPlayer sp, LivingEntity target, double P, double mult, String stat, Kit.Cause cause, DamageNumbers.Style style) {
        if (!Kit.canHit(sp, target)) return 0;
        if (target instanceof Player p && !RUNE.truthy("hitPlayers") && !(p instanceof ServerPlayer o && Kit.duelPartner.test(sp, o))) return 0;
        double dmg = P * mult * mmorpg.fabric.stattree.StatTrees.runeMult(sp) // 스탯 트리 공격력 + 룬 피해 (7단계)
                * RpgCombat.dealtMult.applyAsDouble(sp); // 0.19.0 세계수의 가호
        Kit.Cause c = cause != null ? cause : "int".equals(stat) ? Kit.Cause.MAGIC : Kit.Cause.ATTACK;
        float before = target.getHealth();
        if (!Kit.applySkillDamage(sp, target, dmg, false, c, style != null ? style : DamageNumbers.Style.RUNE)) return 0;
        float after = target.isRemoved() ? 0 : target.getHealth();
        return before > after ? before - after : dmg;
    }

    public static double runeHit(ServerPlayer sp, LivingEntity target, double P, double mult, String stat) {
        return runeHit(sp, target, P, mult, stat, null, null);
    }

    /** 밀치기 (방향 d 의 수평으로 k, 위로 h) */
    public static void knock(Entity e, Vec3 d, double k, double h) {
        Vec3 f = V.flat(d);
        Kit.knockback(e, f.x * k, f.z * k, h);
    }

    /** 베드락 효과 이름 → 자바 (fatal_poison 은 자바에 없어 독) */
    private static final Map<String, String> EFFECT_ALIAS = Map.of("fatal_poison", "poison");

    /** 효과 이름 (slowness · weakness · poison …) 의 자바 효과 (없으면 null) */
    public static Holder<MobEffect> effectOf(String id) {
        Optional<Holder.Reference<MobEffect>> h = BuiltInRegistries.MOB_EFFECT.get(Identifier.withDefaultNamespace(EFFECT_ALIAS.getOrDefault(id, id)));
        return h.isPresent() ? h.get() : null;
    }

    /** 효과 걸기 (파티클 없이 — 애드온 addEffect(id, ticks, {amplifier, showParticles: false})) */
    public static void effect(Entity e, String id, int ticks, int amp) {
        Holder<MobEffect> h = effectOf(id);
        if (h != null && e instanceof LivingEntity le && !le.isRemoved()) le.addEffect(new MobEffectInstance(h, ticks, amp, false, false));
    }

    /** 불붙이기 (초) */
    public static void burn(Entity e, double seconds) {
        if (!e.isRemoved()) e.igniteForSeconds((float) seconds);
    }

    /** 수평 방향 f 를 y 축으로 deg 도 돌림 */
    public static Vec3 turn(Vec3 f, double deg) {
        return V.turnY(f, deg);
    }

    /** 조준한 곳의 땅 (조준한 몹의 발밑 → 시선이 닿은 블록 → 사거리 끝, 거기서 아래로 땅을 찾음) */
    public static Vec3 groundAim(ServerPlayer sp, double range) {
        RpgCombat.Aim r = RpgCombat.aimTarget(sp, range, BOW.numOr("aimAssist", 0));
        if (r.target() != null && !r.target().isRemoved()) return r.target().position();
        Vec3 head = sp.getEyePosition();
        Vec3 p = V.add(head, r.dir(), Math.max(0.5, r.dist() - 0.3));
        Kit.Hit hit = Kit.ray(sp.level(), V.up(p, 0.3), V_DOWN, 32, false);
        if (hit != null) return new Vec3(p.x, hit.pos().getY() + 1, p.z);
        return new Vec3(p.x, Math.min(p.y, sp.getY()), p.z);
    }

    /** 범위 고리: 안쪽에서 퍼져 반지름 R (피해 범위) 에서 사라짐 (shock_ring 은 처음 크기의 약 2.1배까지) */
    public static void zonePulse(ServerLevel level, Vec3 feet, double[] color, double r, double life) {
        SkillFx.shock(level, feet, color, r / 2.1, life);
    }

    public static void zonePulse(ServerLevel level, Vec3 feet, double[] color, double r) {
        zonePulse(level, feet, color, r, 0.45);
    }

    /** 1인칭에서도 보이는 자기 강화 표시: 앞쪽 반원에서 솟는 빛 알갱이 + (withRing) 눈앞에서 앞으로 퍼지는 고리 */
    public static void selfCue(ServerPlayer sp, double[] color, double[] core, boolean withRing) {
        ServerLevel level = sp.level();
        Vec3 view = sp.getViewVector(1F);
        Vec3 f = V.flat(view);
        if (withRing) SkillFx.ring(level, V.add(sp.getEyePosition(), view, 0.9), view, color, 1.1, 0.45, 2.5);
        for (int i = 0; i < 7; i++) {
            Vec3 d = turn(f, (i / 6.0 - 0.5) * 150);
            Vec3 p = V.add(sp.position(), d, 1.5 + (i % 2) * 0.5);
            SkillFx.fx(level, "rpg:arrow_spark", V.up(p, 0.4 + (i % 3) * 0.3), Opt.of("dir", V_UP, "speed", 2.4, "size", 0.16, "grow", 1.2, "life", 0.7,
                    "color", i % 2 == 1 ? core : color, "alpha", 1.0));
        }
    }

    /** 가까운 플레이어들 화면 흔들기 */
    public static void shakeAround(ServerLevel level, Vec3 at, double radius, double intensity, double seconds) {
        Kit.shakeNear(level, at, radius, intensity, seconds);
    }

    /** 앞쪽 원뿔 안의 적 (가까운 순, 벽 뒤 제외, max 명까지) */
    public static List<LivingEntity> coneTargets(ServerPlayer sp, double range, double deg, int max) {
        Vec3 head = sp.getEyePosition();
        Vec3 c = sp.position();
        Vec3 f = V.flat(sp.getViewVector(1F));
        double half = Math.toRadians(deg / 2);
        List<LivingEntity> list = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, c, range, 4)) {
            if (Kit.bodyInCone(e, c, f, half) && Kit.lineOfSight(sp, head, Kit.bodyAt(e, head.y))) list.add(e);
        }
        list.sort(Comparator.comparingDouble(e -> Math.hypot(e.getX() - c.x, e.getZ() - c.z)));
        return list.size() > max ? new ArrayList<>(list.subList(0, max)) : list;
    }

    /** t 틱 뒤 (0 이하면 바로 — 다음 틱으로 미루면 여러 번 때리는 간격이 한 틱 줄어듦) */
    public static void later(double t, Runnable fn) {
        if (t <= 0) fn.run();
        else Tasks.later((int) Math.round(t), fn);
    }

    /** 언데드인지 (좀비 · 스켈레톤 · 팬텀 등) — 성기사 신성 피해 RUNE.undeadBonus 배 */
    public static boolean isUndead(Entity e) {
        return Kit.hasFamily(e, "undead");
    }

    /** 성기사 신성 피해: 마법 피해 (방어구 무시), 언데드는 undeadBonus 배 */
    public static double holyHit(ServerPlayer sp, LivingEntity e, double P, double mult) {
        return runeHit(sp, e, P, mult * (isUndead(e) ? RUNE.num("undeadBonus") : 1), "vit", Kit.Cause.MAGIC, null);
    }

    /** 기절: ticks 동안 움직이지 못하고 (구속 XI) 근접 공격이 거의 들어가지 않음 (나약함 V). 머리 위에 작은 룬이 돎 */
    public static void stun(LivingEntity e, int ticks, double[] color, boolean mark) {
        effect(e, "slowness", ticks, 10);
        effect(e, "weakness", ticks, 4);
        if (!mark || !(e.level() instanceof ServerLevel level)) return;
        SkillFx.rune(level, V.up(e.getEyePosition(), 0.45), Opt.of("size", 0.42, "life", ticks / 20.0, "color", color, "alpha", 0.95, "rate", 300.0));
    }

    /** from 에서 dir 로 막는 블록까지 거리 (없으면 max) */
    public static double rayLength(Level level, Vec3 from, Vec3 dir, double max) {
        return Kit.rayDist(level, from, dir, max);
    }

    /** 수평 방향 f 로 max 칸까지 막힘 없이 갈 수 있는 거리 (heights 높이의 광선, 벽 앞 0.6칸) */
    public static double dashDistance(ServerPlayer sp, Vec3 f, double max, double... heights) {
        double[] hs = heights.length > 0 ? heights : new double[] {0.3, 1.5};
        Vec3 start = sp.position();
        double dist = max;
        for (double h : hs) {
            Kit.Hit hit = Kit.ray(sp.level(), V.up(start, h), f, max + 0.8, false);
            if (hit != null) dist = Math.min(dist, Math.hypot(hit.point().x - start.x, hit.point().z - start.z) - 0.6);
        }
        return Math.max(0, dist);
    }

    /** p 자리의 땅 높이 (p 보다 1.6 위에서 아래로 3.5칸 안, 없으면 fallback) — 한 칸 턱을 넘어 옮길 때 */
    public static double groundAt(Level level, Vec3 p, double fallback) {
        Kit.Hit hit = Kit.ray(level, V.up(p, 1.6), V_DOWN, 3.5, false);
        return hit != null ? hit.pos().getY() + 1 : fallback;
    }

    /** 착지할 땅: from 에서 아래로 maxDown 칸 안의 첫 블록이 단단하면 그 위 높이 (물 · 용암 · 땅 없음 = null) */
    public static Double solidGround(Level level, Vec3 from, double maxDown) {
        return Kit.solidBelow(level, from, maxDown);
    }

    /** 옮기기 (막힌 자리면 그대로 — false). facing 을 주면 옮긴 뒤 그곳을 봄 */
    public static boolean moveTo(Entity e, Vec3 at, Vec3 facing) {
        if (facing == null) return Kit.moveTo(e, at);
        Vec3 eye = new Vec3(at.x, at.y + e.getEyeHeight(), at.z);
        float[] rot = Kit.lookRotation(eye, facing);
        return Kit.moveTo(e, at, rot[0], rot[1]);
    }

    /** 결투 중인 사람을 도울 수 있는지 (6단계 결투에서 정함 — 그 전에는 늘 됨) */
    public static java.util.function.BiPredicate<ServerPlayer, ServerPlayer> canAssist = (a, b) -> true;

    /** 주위 플레이어 (나 포함 — caster 를 주면 결투 중인 남은 빼고) */
    public static List<ServerPlayer> playersAround(ServerLevel level, Vec3 c, double radius, ServerPlayer caster) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.position().distanceTo(c) > radius) continue;
            if (caster != null && !canAssist.test(caster, p)) continue;
            out.add(p);
        }
        return out;
    }

    /** 정화로 없애는 나쁜 효과 */
    public static final List<String> BAD_EFFECTS = List.of("poison", "wither", "slowness", "weakness", "blindness", "nausea", "hunger", "mining_fatigue",
            "darkness", "levitation");

    /** 나쁜 효과 없애기. 없앤 개수 */
    public static int cleanse(LivingEntity pl) {
        int n = 0;
        for (String id : BAD_EFFECTS) {
            Holder<MobEffect> h = effectOf(id);
            if (h != null && pl.hasEffect(h) && pl.removeEffect(h)) n++;
        }
        return n;
    }

    /** 직선 (from → dir, 길이 L, 폭 w) 가까이의 적, 앞에서부터 (벽은 보지 않음 — runes.js lineTargets) */
    public static List<Kit.LineHit> lineTargets(ServerPlayer sp, Vec3 from, Vec3 dir, double len, double w) {
        List<Kit.LineHit> out = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, V.add(from, dir, len / 2), len / 2 + w + 0.5, len / 2 + 3)) {
            double pad = Kit.bodyPad(e);
            Vec3 p = V.sub(Kit.bodyToward(e, from, dir), from);
            double t = V.dot(p, dir);
            if (t < -0.3 - pad || t > len + 0.5 + pad) continue;
            if (V.len(V.sub(p, V.scale(dir, t))) > w + pad) continue;
            out.add(new Kit.LineHit(e, Math.max(0, t)));
        }
        out.sort(Comparator.comparingDouble(Kit.LineHit::t));
        return out;
    }

    /** c 에서 수평 방향 f 쪽 cone 도 · radius 칸 안의 적 (1.2칸 안은 방향 무관) */
    public static List<LivingEntity> coneAround(ServerPlayer sp, Vec3 c, Vec3 f, double radius, double cone) {
        double half = Math.toRadians(cone / 2);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, c, radius)) if (Kit.bodyInCone(e, c, f, half)) out.add(e);
        return out;
    }

    /** 가까운 순 */
    public static <T extends Entity> List<T> nearestTo(List<T> list, Vec3 p) {
        List<T> out = new ArrayList<>(list);
        out.sort(Comparator.comparingDouble(e -> e.position().distanceTo(p)));
        return out;
    }

    /** a → b 곧은 빛 한 줄 (파티클 하나) */
    public static void streak(ServerLevel level, Vec3 a, Vec3 b, double[] color, double thick, double life, double alpha, double delay) {
        Vec3 d = V.sub(b, a);
        double len = V.len(d);
        if (len < 0.05) return;
        SkillFx.fx(level, "rpg:arrow_trail", V.add(a, d, 0.5), Opt.of("dir", V.unit(d), "size", len / 2, "thick", thick, "life", life + delay, "delay", delay,
                "color", color, "alpha", alpha));
    }

    public static void streak(ServerLevel level, Vec3 a, Vec3 b, double[] color, double thick, double life) {
        streak(level, a, b, color, thick, life, 1, 0);
    }

    /** 날아가는 빛 (빛 + 심지). 도착까지 틱 */
    public static int flyStreak(ServerLevel level, Vec3 from, Vec3 to, double speed, double[] glow, double[] core, double size, double thick) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        double life = Math.max(0.05, len / speed);
        Vec3 u = V.unit(d);
        SkillFx.fx(level, "rpg:arrow_shot", from, Opt.of("dir", u, "speed", len / life, "size", size, "thick", thick, "life", life, "color", glow, "alpha", 1.0));
        SkillFx.fx(level, "rpg:arrow_shot", from, Opt.of("dir", u, "speed", len / life, "size", size * 0.75, "thick", thick * 0.35, "life", life, "color", core,
                "alpha", 1.0));
        return Math.max(1, (int) Math.round(life * 20));
    }

    public static int flyStreak(ServerLevel level, Vec3 from, Vec3 to, double speed, double[] glow, double[] core) {
        return flyStreak(level, from, to, speed, glow, core, 0.7, 0.26);
    }

    /** 맞힌 자리 번쩍임 (X 참격 + 폭발 + 불꽃) */
    public static void hitFlash(ServerLevel level, Vec3 at, Vec3 dir, double[] glow, double[] core, double size) {
        SkillFx.fx(level, "rpg:slash_x", at, Opt.of("dir", dir, "size", size, "life", 0.24, "color", glow, "alpha", 1.0));
        SkillFx.burst(level, at, glow, size, 0.24);
        SkillFx.sparks(level, at, core, 5, 4);
    }

    public static void hitFlash(ServerLevel level, Vec3 at, Vec3 dir, double[] glow, double[] core) {
        hitFlash(level, at, dir, glow, core, 0.9);
    }

    // ------------------------------------------------------------ 룬 연출 그림 (rpg:rfx_* — runes.js RFX · rfx · rfx2)

    /** 그림 칸: 색을 입히는 그림은 {빛, 심지} */
    public static final Map<String, int[]> RFX = Map.ofEntries(
            Map.entry("sword", new int[] {0, 1}), Map.entry("wave", new int[] {0, 1}), Map.entry("arrow", new int[] {0, 1}), Map.entry("icicle", new int[] {0, 1}),
            Map.entry("moon", new int[] {0, 1}), Map.entry("axe", new int[] {0, 5}), Map.entry("hammer", new int[] {1, 6}), Map.entry("shuriken", new int[] {2, 7}),
            Map.entry("feather", new int[] {3, 8}), Map.entry("glint", new int[] {4, 9}));
    /** 하늘의 활: 빛 0~3 (시위를 당긴 정도) · 심지 4~7 */
    public static final int BOW_CORE = 4;
    /** 날개: 빛 0 (위) · 1 (아래) · 심지 2~3 */
    public static final int WINGS_CORE = 2;
    /** 번개 창: 빛 0~3 · 심지 4~7 */
    public static final int SPEAR_CORE = 4;
    /** 번개: 빛 0~3 · 심지 4~7 */
    public static final int BOLT_CORE = 4;
    /** 블랙홀: 원반 0 · 핵 1 */
    public static final int HOLE_CORE = 1;
    /** 정령 4장씩 */
    public static final Map<String, Integer> SPIRIT_CELL = Map.of("salamander", 0, "undine", 4, "sylph", 8, "gnome", 12);
    /** 바위 가시: 모양 0~2 · 틈의 빛 3~5 */
    public static final int ROCK_CORE = 3;
    /** 그림자 분신: 자세 0~3 (왼쪽을 보면 +4) · 테두리 +8 */
    public static final int SHADE_CORE = 8;
    /** 얼음 결정: 모양 0~2 · 모서리 3~5 */
    public static final int CRYSTAL_CORE = 3;
    /** 번개 구슬: 빛 0~3 · 심지 4~7 */
    public static final int PLASMA_CORE = 4;
    /** 영역 문양 4가지 (빛 = 칸, 심지 = 칸 + 1) */
    public static final Map<String, Integer> SIGIL = Map.of("arcane", 0, "holy", 2, "blade", 4, "spirit", 6);
    /** 갈라진 금: 모양 v 의 첫 칸 = v × 2 */
    public static final int CRACK_V = 2;

    /**
     * 룬 연출 그림 하나 (rpg:rfx_&lt;name&gt;). o: dir (긴 그림은 그 방향으로 눕고 그 방향으로 움직임) · size · life · color · alpha · spin · spinRate ·
     * speed (dir 쪽 칸/초) · grow · cell · frames · fps · loop · phase · fade · vel (몸을 따라오는 속도 칸/초) · view · owner (0.27.0 보는 사람 걸러내기)
     */
    public static void rfx(ServerLevel level, String name, Vec3 at, Opt o) {
        AddonFx.Rfx r = new AddonFx.Rfx();
        r.dir = o.vec("dir", V_UP);
        r.size = o.num("size", 0);
        r.life = o.num("life", 0);
        r.color = o.color("color", WHITE);
        r.alpha = o.num("alpha", 1);
        r.spin = o.num("spin", 0);
        r.spinRate = o.num("spinRate", 0);
        r.speed = o.num("speed", 0);
        r.grow = o.num("grow", 0);
        r.cell = o.num("cell", 0);
        r.frames = o.num("frames", 1);
        r.fps = o.num("fps", 0);
        r.loop = o.truthy("loop");
        r.phase = o.num("phase", 0);
        r.fade = o.num("fade", 0);
        r.vel = o.vec("vel", V_ZERO);
        r.view = (int) o.num("view", 0); // 0.27.0 보는 사람 걸러내기 (1 = 주인이 1인칭이면 숨김 · 2 = 주인의 1인칭에만)
        r.owner = (int) o.num("owner", 0);
        r.solo = (int) o.num("solo", 0); // 0.29.0 주인마다 하나만 (AddonParticle — 다시 그리는 큰 그림이 겹쳐 줄무늬가 지지 않게)
        AddonFx.rfx(level, name, at, r);
    }

    /** 색을 입히는 그림의 빛 + 심지 두 장 (cells = {빛 칸, 심지 칸} — o.cell 은 둘에 더함). coreAlpha = 심지만 다른 불투명도 (null = 같게) */
    public static void rfx2(ServerLevel level, String name, Vec3 at, Opt o, double[] glow, double[] core, int[] cells, Double coreAlpha) {
        int[] c = cells != null ? cells : RFX.getOrDefault(name, new int[] {0, 1});
        double cell = o.num("cell", 0);
        double solo = o.num("solo", 0); // 0.29.0 주인마다 하나만 — 빛 · 심지가 서로를 지우지 않게 번호를 나눔
        rfx(level, name, at, o.with("color", glow, "cell", c[0] + cell, "solo", solo > 0 ? solo * 2 : null));
        rfx(level, name, at, o.with("color", core, "cell", c[1] + cell, "alpha", coreAlpha != null ? coreAlpha : o.num("alpha", 1), "solo", solo > 0 ? solo * 2 + 1 : null));
    }

    public static void rfx2(ServerLevel level, String name, Vec3 at, Opt o, double[] glow, double[] core) {
        rfx2(level, name, at, o, glow, core, null, null);
    }

    /** 그림까지 거리: 1인칭 눈과 3인칭 카메라 (보는 쪽 뒤 4.5칸 · 위 1칸 근사) 중 가까운 쪽 */
    public static double camDist(ServerPlayer sp, Vec3 at) {
        if (sp.isRemoved()) return 99;
        Vec3 eye = sp.getEyePosition();
        Vec3 f = Kit.facingOf(sp);
        Vec3 cam = new Vec3(eye.x - f.x * 4.5, eye.y + 1, eye.z - f.z * 4.5);
        return Math.min(V.len(V.sub(at, eye)), V.len(V.sub(at, cam)));
    }

    /** 큰 그림이 보는 곳에 가까우면 작게 (full 칸에서 1, 1칸에서 min) — 화면을 덮지 않게 */
    public static double nearK(ServerPlayer sp, Vec3 at, double full, double min) {
        return Math.max(min, Math.min(1, (camDist(sp, at) - 1) / Math.max(0.1, full - 1)));
    }

    // ------------------------------------------------------------ 초월 룬 2차 강화: 하늘빛 · 번쩍임 · 하늘 · 땅 연출

    /** 룬 함수의 하늘빛 · 번쩍임 설정 (ULT_FX.runes[fn] — 없으면 빈 것) */
    public static Obj ultOf(String fn) {
        // 0.25.0 자바 전용 초월 룬 (두 스탯 계열 여섯): rules_more ULT_RUNES_MORE 를 먼저
        Obj more = ULT_MORE != null ? ULT_MORE.objOrNull(fn) : null;
        if (more != null) return more;
        Obj runes = ULT_FX.objOrNull("runes");
        Obj o = runes != null ? runes.objOrNull(ULT_ALIAS.getOrDefault(fn, fn)) : null;
        return o != null ? o : new Obj(Map.of(), "ULT_FX.runes." + fn);
    }

    /** 0.25.0 자바 전용 초월 룬의 하늘빛 · 번쩍임 (rules_more ULT_RUNES_MORE — 하늘빛 이름은 ULT_FOGS_MORE) */
    private static final Obj ULT_MORE = mmorpg.core.Rules.get().root().objOrNull("ULT_RUNES_MORE");

    /**
     * 자바 전용 초월 룬 함수 → 같은 하늘빛 · 번쩍임을 쓰는 애드온 룬 함수 (12단계 정령왕 강림 = 정령왕의 부름의 오로라, 0.12.6 적월 강림 = 광전사의 혼의 핏빛 하늘,
     * 0.16.0 한정: 만상귀일 = 마검 해방의 비전 하늘 · 칠성 강림 = 월광 암살의 밤하늘 · 천공의 눈 = 대천사 강림의 금빛 하늘 ·
     * 0.18.0 대지 가르기 = 메테오의 노을 하늘 · 0.19.0 세계수의 가호 = 하늘의 활의 새벽빛 · 0.20.0 심연의 낙인 = 블랙홀의 공허 하늘 · 0.24.0 만년빙하 = 하늘 가르기의
     * 얼음빛 번쩍임 (하늘빛은 룬이 rpg:ult_glacier 를 직접 줌)). 0.17.0 무간참역은 0.25.2 부터 블랙홀의 공허 하늘 대신 자기 검붉은 하늘 (ULT_RUNES_MORE)
     */
    private static final Map<String, String> ULT_ALIAS = Map.ofEntries(Map.entry("spiritKing", "summonSpirits"), Map.entry("crimsonMoon", "berserker"),
            Map.entry("arcanaUnity", "spellbladeRelease"), Map.entry("sevenStars", "moonAssassin"), Map.entry("heavenEye", "archangel"),
            Map.entry("earthSplitter", "meteor"), Map.entry("worldTreeGrace", "skyBow"), Map.entry("abyssBrand", "blackHole"),
            Map.entry("glacierField", "heavenSplit"));

    /**
     * 하늘빛: 룬 함수 fn 의 하늘빛 (fogId 를 주면 그것) 을 at 둘레 사람에게 ticks 틱. 씌운 사람 수.
     * 8단계 돌파: sp 가 쓴 그 룬이 3돌 미만이면 하늘빛 없음 (화면 색 연출은 돌파 효과), 6돌이면 더 길고 짙게 + 화면 테두리 빛 (RuneBreak.Fx)
     */
    public static int ultSkyFor(ServerPlayer sp, ServerLevel level, Vec3 at, String fn, int ticks, String fogId) {
        mmorpg.core.RuneBreak.Fx fx = Breakthrough.castFx(sp, fn);
        if (fx == null) return 0;
        return Ult.sky(level, at, fogId != null ? fogId : ultOf(fn).strOrNull("fog"), (int) Math.round(ticks * fx.skyTicks()), fx.skyDist(), fx.edge());
    }

    /**
     * 번쩍임: 룬 함수 fn 의 색 (key = flash 발동 · still 멈춘 시간 · dark 어둠) 으로 at 둘레 사람 + 룬을 쓴 사람의 화면. o: radius · time (fadeIn · hold · fadeOut 초).
     * 8단계 돌파: 3돌 미만이면 없음, 6돌이면 더 오래 머물고 천천히 걷힘
     */
    public static int ultFlashFor(ServerPlayer sp, ServerLevel level, Vec3 at, String fn, String key, Opt o) {
        mmorpg.core.RuneBreak.Fx fx = Breakthrough.castFx(sp, fn);
        if (fx == null) return 0;
        List<Object> c = ultOf(fn).listOrNull(key == null ? "flash" : key);
        if (c == null) return 0;
        Opt oo = o == null ? Opt.empty() : o;
        if (fx.flashHold() > 0 || fx.flashOut() != 1) {
            Obj t = ULT_FX.obj("flashTime");
            double[] time = oo.get("time") instanceof double[] d && d.length == 3 ? d.clone()
                    : new double[] {t.num("fadeInTime"), t.num("holdTime"), t.num("fadeOutTime")};
            time[1] += fx.flashHold();
            time[2] *= fx.flashOut();
            oo = oo.with("time", time);
        }
        return Ult.flash(level, at, Kit.rgb(c), oo, sp);
    }

    /**
     * 하늘 자리: 시전자 눈에서 anchor 쪽 (수평 — anchor 가 몸 가까이면 보는 쪽) 으로 anchor 보다 back 칸 더 먼 곳.
     * 높이는 눈에서 elev 도 위, anchor 땅에서 minH ~ maxH 칸
     */
    public static Vec3 skyPoint(ServerPlayer sp, Vec3 anchor, double back, double elev, double minH, double maxH) {
        if (sp.isRemoved()) return V.up(anchor, minH + 4);
        Vec3 eye = sp.getEyePosition();
        double dx = anchor.x - eye.x;
        double dz = anchor.z - eye.z;
        double dd = Math.hypot(dx, dz);
        Vec3 d = dd > 1.5 ? new Vec3(dx / dd, 0, dz / dd) : Kit.facingOf(sp);
        double h = (dd > 1.5 ? dd : 0) + back;
        double y = Math.max(anchor.y + minH, Math.min(anchor.y + maxH, eye.y + Math.tan(Math.toRadians(elev)) * h));
        return new Vec3(eye.x + d.x * h, y, eye.z + d.z * h);
    }

    public static Vec3 skyPoint(ServerPlayer sp, Vec3 anchor) {
        return skyPoint(sp, anchor, 8, 20, 6, 22);
    }

    /** 시전자 눈을 보는 방향 (하늘의 그림이 시전자에게 정면으로) */
    public static Vec3 towardEye(ServerPlayer sp, Vec3 at) {
        if (sp.isRemoved()) return V_UP;
        Vec3 v = V.sub(sp.getEyePosition(), at);
        return V.len(v) > 1e-3 ? V.unit(v) : V_UP;
    }

    /**
     * 거대한 영역 문양 (rpg:rfx_sigil, 빛 + 심지): 땅에 눕힘 (o.dir 을 주면 그 쪽을 보는 면). style: arcane · holy · blade · spirit.
     * o: life · spin · spinRate · grow · alpha · fade · coreAlpha
     */
    public static void domainSigil(ServerLevel level, Vec3 at, double r, String style, double[] glow, double[] core, Opt o) {
        Vec3 at2 = o.has("dir") ? at : V.up(at, 0.08);
        rfx2(level, "sigil", at2, Opt.of("dir", o.vec("dir", V_UP), "size", r, "life", o.num("life", 1), "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 360,
                "spinRate", o.num("spinRate", 0), "grow", o.num("grow", 0), "alpha", o.num("alpha", 0.9), "fade", o.num("fade", 0.35), "cell",
                (double) SIGIL.getOrDefault(style, 0)), glow, core, new int[] {0, 1}, o.numOrNull("coreAlpha"));
    }

    /** 하늘의 그림 (시전자 쪽을 봄): name = sigil · crack · nova. o 는 rfx 의 것 */
    public static void skyPlate(ServerPlayer sp, ServerLevel level, String name, Vec3 at, double size, double[] glow, double[] core, Opt o) {
        rfx2(level, name, at, o.with("dir", towardEye(sp, at), "size", size), glow, core, new int[] {0, 1}, null);
    }

    /** at 에서 r 칸 안에 플레이어가 있는지 */
    public static boolean playerNear(ServerLevel level, Vec3 at, double r) {
        for (ServerPlayer p : level.players()) if (!p.isSpectator() && p.position().distanceTo(at) <= r) return true;
        return false;
    }

    /**
     * 빛기둥 (rpg:rfx_rays — 밑동 = 놓은 자리): c 둘레 반지름 R 에 n 개. o: arc ({시작, 끝} 도 — o.f 기준, 기본 보는 쪽 · 둘레 전체), f, h (높이),
     * life · alpha · fade · grow · coreAlpha · jitter · skipNear · colors (double[][][] — 기둥마다 {빛, 심지}). 시전자 눈에서 5칸 안이면 밀어냄
     */
    public static void godRays(ServerPlayer sp, ServerLevel level, Vec3 c, double r, int n, double[] glow, double[] core, Opt o) {
        if (sp.isRemoved()) return;
        Vec3 f = o.vec("f", Kit.facingOf(sp));
        double[] arc = o.get("arc") instanceof double[] a ? a : new double[] {0, 360};
        boolean full = arc[1] - arc[0] >= 359;
        Vec3 eye = sp.getEyePosition();
        Vec3 vf = Kit.facingOf(sp);
        double cx = eye.x - vf.x * 4.5;
        double cz = eye.z - vf.z * 4.5;
        double[][][] colors = o.get("colors") instanceof double[][][] cs ? cs : null;
        for (int i = 0; i < n; i++) {
            double deg = full ? arc[0] + 360.0 * i / n : n == 1 ? (arc[0] + arc[1]) / 2 : arc[0] + (arc[1] - arc[0]) * i / (n - 1);
            Vec3 p = V.add(c, turn(f, deg + o.num("jitter", 0) * (Kit.rand() - 0.5)), r);
            double hx = p.x - eye.x;
            double hz = p.z - eye.z;
            double hd = Math.hypot(hx, hz);
            if (hd < 5) {
                if (o.truthy("skipNear")) continue;
                p = hd > 1e-3 ? new Vec3(eye.x + hx / hd * 5, p.y, eye.z + hz / hd * 5) : V.add(eye, f, 5);
            }
            if (Math.hypot(p.x - cx, p.z - cz) < 5) continue; // 3인칭 카메라 바로 앞
            if (playerNear(level, new Vec3(p.x, c.y + 1, p.z), 3)) continue; // 다른 사람 바로 옆
            double[] g = colors != null ? colors[i % colors.length][0] : glow;
            double[] k = colors != null ? colors[i % colors.length][1] : core;
            rfx2(level, "rays", new Vec3(p.x, c.y, p.z), Opt.of("size", o.num("h", 14) / 2, "life", o.num("life", 0.8), "alpha", o.num("alpha", 0.85), "fade",
                    o.num("fade", 0.55), "grow", o.num("grow", 0)), g, k, new int[] {0, 1}, o.num("coreAlpha", 0.9));
        }
    }

    /** 충격 고리 (rpg:rfx_nova): 땅에 눕혀 반지름 R0 에서 R0 × (1 + grow) 까지 퍼지며 사라짐 (o.dir 을 주면 그 쪽을 보는 면) */
    public static void novaRing(ServerLevel level, Vec3 at, double r0, double grow, double[] glow, double[] core, Opt o) {
        rfx2(level, "nova", o.has("dir") ? at : V.up(at, 0.15), Opt.of("dir", o.vec("dir", V_UP), "size", r0, "grow", grow, "life", o.num("life", 0.45), "alpha",
                o.num("alpha", 0.95), "fade", o.num("fade", 0.65), "spin", Kit.rand() * 360), glow, core, new int[] {0, 1}, null);
    }

    /** 빛나는 금 (rpg:rfx_crack): 땅에 눕힘 (o.dir 을 주면 그 쪽을 보는 면). o: v (모양 0 · 1) · life · grow · alpha · fade · spin · spinRate */
    public static void glowCrack(ServerLevel level, Vec3 at, double r, double[] glow, double[] core, Opt o) {
        rfx2(level, "crack", o.has("dir") ? at : V.up(at, 0.06), Opt.of("dir", o.vec("dir", V_UP), "size", r, "life", o.num("life", 2), "alpha", o.num("alpha", 0.95),
                "fade", o.num("fade", 0.55), "grow", o.num("grow", 0), "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 360, "spinRate", o.num("spinRate", 0),
                "cell", (double) CRACK_V * o.num("v", 0)), glow, core, new int[] {0, 1}, null);
    }

    /** 하늘의 섬광 (rpg:rfx_flare — 화면을 봄): 긴 빛살 · 무리 */
    public static void skyFlare(ServerLevel level, Vec3 at, double size, double[] glow, double[] core, Opt o) {
        rfx2(level, "flare", at, Opt.of("size", size, "life", o.num("life", 0.5), "grow", o.num("grow", 0.45), "fade", o.num("fade", 0.65), "alpha",
                o.num("alpha", 1), "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 45, "spinRate", o.num("spinRate", 20)), glow, core, new int[] {0, 1}, null);
    }

    /** 한 틱에 그린 같은 연출 수 (fxQuota) */
    private static final Map<String, Integer> QUOTA = new HashMap<>();
    private static long quotaTick = -1;

    /** 적을 한꺼번에 많이 맞힐 때: 같은 틱에 같은 연출 (key) 은 max 번까지만 그림 (피해는 모두 그대로). 그려도 되면 true */
    public static boolean fxQuota(String key, int max) {
        long now = Tasks.now();
        if (now != quotaTick) {
            QUOTA.clear();
            quotaTick = now;
        }
        int n = QUOTA.getOrDefault(key, 0);
        QUOTA.put(key, n + 1);
        return n < max;
    }

    /** 몸을 따라오는 속도 (칸/초, 수평만) — 몸에 붙은 그림이 걷기 · 달리기에 뒤처지지 않게 */
    public static Vec3 bodyVel(Entity e) {
        Vec3 v = e.getDeltaMovement();
        return new Vec3(v.x * 20, 0, v.z * 20);
    }

    /** 몸에 붙은 그림의 수명 (초): 2틱마다 다시 그림 */
    public static final double BODY_LIFE = 0.15;
    /** 몸의 기운을 그린 틱 (여러 룬이 같은 틱에 그리면 하나만) */
    private static final Map<UUID, Long> AURA_DRAWN = new HashMap<>();

    /**
     * 몸의 기운 (신체 강화 · 광전사의 혼 · 불사의 맹약): 불꽃 n 장을 양옆 · 뒤쪽 옆에 (앞과 바로 뒤는 비움). 2틱마다 부름.
     * 같은 틱에 이미 그렸으면 그리지 않음 (o.force = 쓸 때의 큰 기운). o: n · size · radius · life · alpha · force
     */
    public static void bodyAura(ServerPlayer sp, double[] color, Opt o) {
        long now = Tasks.now();
        Long drawn = AURA_DRAWN.get(sp.getUUID());
        if (!o.truthy("force") && drawn != null && drawn == now) return;
        AURA_DRAWN.put(sp.getUUID(), now);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        int n = (int) o.num("n", 4);
        double size = o.num("size", 1.05);
        Vec3 vel = bodyVel(sp);
        for (int i = 0; i < n; i++) {
            int sd = i % 2 == 1 ? 1 : -1;
            int k = i / 2;
            Vec3 d = turn(f, sd * (118 + (k % 3) * 22 + Kit.rand() * 8));
            rfx(level, "aura", V.add(c, d, o.num("radius", 0.7)), Opt.of("size", size * (0.85 + Kit.rand() * 0.3), "life", o.num("life", BODY_LIFE), "color", color,
                    "alpha", o.num("alpha", 0.72), "frames", 8.0, "fps", 16.0, "loop", true, "phase", Kit.rand() * 8, "fade", 0.3, "vel", vel));
        }
    }

    /** 1인칭에서 보이는 기운: 화면 아래 양옆에서 솟는 작은 불티 */
    public static void auraCue(ServerPlayer sp, double[] color, int n) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 c = sp.position();
        for (int i = 0; i < n; i++) {
            Vec3 d = turn(f, (i % 2 == 1 ? 1 : -1) * (24 + Kit.rand() * 26));
            Vec3 p = V.add(c, d, 1.5 + Kit.rand() * 0.4);
            SkillFx.fx(level, "rpg:arrow_spark", V.up(p, 0.2 + Kit.rand() * 0.5), Opt.of("dir", V_UP, "speed", 1.6, "size", 0.12, "grow", 1.2, "life", 0.6,
                    "color", color, "alpha", 1.0));
        }
    }

    /** 바위 가시 (rpg:rfx_rock — 밑동 고정, 모양 3가지): 바위 빛 (o.rock) + 갈라진 틈의 빛 (glow). 카메라에 가까우면 작게. o: v · full · life · grow · fade · alpha · rock */
    public static void rockSpike(ServerPlayer sp, ServerLevel level, Vec3 at, double size, double[] glow, Opt o) {
        double v = o.has("v") ? o.num("v", 0) : Math.floor(Kit.rand() * 3);
        double s = size * nearK(sp, V.up(at, size), o.num("full", 4), 0.35);
        rfx2(level, "rock", at, Opt.of("size", s, "life", o.num("life", 0.9), "grow", o.num("grow", 0.2), "fade", o.num("fade", 0.35), "cell", v, "alpha",
                o.num("alpha", 1)), o.color("rock", ROCK), glow, new int[] {0, ROCK_CORE}, null);
    }

    /** 바위 가시 고리: c 둘레 반지름 R 에 n 개 (쓴 사람 몸에서 o.minDist (1.6) 칸 안은 빼고) */
    public static void rockRing(ServerPlayer sp, ServerLevel level, Vec3 c, double r, int n, double size, double[] glow, Opt o) {
        Vec3 p0 = sp.position();
        double a0 = Kit.rand() * Math.PI * 2;
        for (int i = 0; i < n; i++) {
            double a = a0 + (double) i / n * Math.PI * 2;
            double rr = r * (0.85 + Kit.rand() * 0.3);
            Vec3 at = new Vec3(c.x + Math.cos(a) * rr, c.y, c.z + Math.sin(a) * rr);
            if (Math.hypot(at.x - p0.x, at.z - p0.z) < o.num("minDist", 1.6)) continue;
            rockSpike(sp, level, at, size * (0.8 + Kit.rand() * 0.4), glow, o);
        }
    }

    /** 튀는 돌 n 개 (c 에서 둘레로) */
    public static void rockBurst(ServerLevel level, Vec3 c, int n, double speed, double size) {
        for (int i = 0; i < n; i++) {
            double a = (double) i / n * Math.PI * 2 + Kit.rand() * 0.5;
            SkillFx.fx(level, "rpg:boulder", c, Opt.of("dir", V.unit(new Vec3(Math.cos(a), 1.1 + Kit.rand() * 0.4, Math.sin(a))), "speed",
                    speed * (0.8 + Kit.rand() * 0.4), "size", size, "life", 0.7, "spin", Kit.rand() * 360, "color", ROCK, "alpha", 1.0));
        }
    }

    /**
     * 그림자 분신 (rpg:rfx_shade — 발 고정): pose 0 섬 · 1 돌진 · 2 베기 · 3 찌르기. face = 분신이 보는 쪽 (쓴 사람 화면에서 왼쪽을 보면 뒤집은 장).
     * o: size · life · fade · grow · alpha · vel
     */
    public static void shadeClone(ServerPlayer sp, ServerLevel level, Vec3 feet, Vec3 face, int pose, double[] glow, double[] core, Opt o) {
        boolean left = !sp.isRemoved() && V.dot(V.flat(face), V.rightOf(V.flat(sp.getViewVector(1F)))) < 0;
        rfx2(level, "shade", feet, Opt.of("size", o.num("size", 0.95), "life", o.num("life", 0.4), "fade", o.num("fade", 0.5), "grow", o.num("grow", 0), "alpha",
                o.num("alpha", 0.92), "cell", (double) (pose + (left ? 4 : 0)), "vel", o.vec("vel", V_ZERO)), glow, core, new int[] {0, SHADE_CORE}, null);
    }

    /** 번개 구슬 (rpg:rfx_plasma — 4장이 돎). o: life · fps · fade · grow · alpha · vel */
    public static void plasmaOrb(ServerLevel level, Vec3 at, double size, double[] glow, Opt o) {
        rfx2(level, "plasma", at, Opt.of("size", size, "life", o.num("life", 0.3), "frames", 4.0, "fps", o.num("fps", 18), "loop", true, "phase", Kit.rand() * 4,
                "spin", Kit.rand() * 360, "fade", o.num("fade", 0.4), "grow", o.num("grow", 0), "alpha", o.num("alpha", 1), "vel", o.vec("vel", V_ZERO)), glow, WHITE,
                new int[] {0, PLASMA_CORE}, null);
    }

    /** 얼음 결정 (rpg:rfx_crystal — 밑동 고정, 모양 3가지). 카메라에 가까우면 작게. o: full · life · grow · fade · v · alpha */
    public static void iceCrystal(ServerPlayer sp, ServerLevel level, Vec3 at, double size, double[] glow, Opt o) {
        double s = size * nearK(sp, V.up(at, size), o.num("full", 4), 0.35);
        rfx2(level, "crystal", at, Opt.of("size", s, "life", o.num("life", 1.0), "grow", o.num("grow", 0.15), "fade", o.num("fade", 0.3), "cell",
                o.has("v") ? o.num("v", 0) : Math.floor(Kit.rand() * 3), "alpha", o.num("alpha", 0.95)), glow, WHITE, new int[] {0, CRYSTAL_CORE}, null);
    }

    /** 회오리 (rpg:rfx_vortex — 밑동 고정, 8장이 돎). o: life · alpha · fps · phase · fade · grow · vel */
    public static void vortexAt(ServerLevel level, Vec3 at, double size, double[] color, Opt o) {
        rfx(level, "vortex", at, Opt.of("size", size, "life", o.num("life", 0.4), "color", color, "alpha", o.num("alpha", 0.9), "frames", 8.0, "fps", o.num("fps", 20),
                "loop", true, "phase", o.has("phase") ? o.num("phase", 0) : Kit.rand() * 8, "fade", o.num("fade", 0.3), "grow", o.num("grow", 0), "vel",
                o.vec("vel", V_ZERO)));
    }

    // ------------------------------------------------------------ 여러 계열이 함께 쓰는 그림 · 피해

    /** 칼자국 자리를 조금씩 흩뜨림 (화면 기준 — 무영검) */
    public static Vec3 screenOffset(Vec3 v, int c) {
        Vec3 side = V.unit(Math.abs(v.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(-v.z, 0, v.x));
        double s = ((c % 3) - 1) * 0.18;
        double h = (((c / 3) % 3) - 1) * 0.25;
        return new Vec3(side.x * s, h, side.z * s);
    }

    /** 보는 방향 v 에 수직인 면에서 ang 도 방향 */
    public static Vec3 screenAngle(Vec3 v, double ang) {
        Vec3 side = V.unit(Math.abs(v.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(-v.z, 0, v.x));
        Vec3 upv = V.unit(new Vec3(side.y * v.z - side.z * v.y, side.z * v.x - side.x * v.z, side.x * v.y - side.y * v.x));
        double a = Math.toRadians(ang);
        return V.unit(V.add(V.scale(side, Math.cos(a)), upv, Math.sin(a)));
    }

    /**
     * 방패 문장 (rpg:rfx_shield — 화면을 봄): 빛 + 흰 테두리. o.full 이면 1인칭 · 3인칭 카메라에 가까울 때 작게.
     * o: full · min · life · grow · fade · alpha · spin · vel · dir · speed · core
     */
    public static void shieldEmblem(ServerPlayer sp, ServerLevel level, Vec3 at, double size, double[] glow, Opt o) {
        double s = size * (o.truthy("full") ? nearK(sp, at, o.num("full", 0), o.num("min", 0.4)) : 1);
        rfx2(level, "shield", at, Opt.of("size", s, "life", o.num("life", 0.6), "grow", o.num("grow", 0), "fade", o.num("fade", 0.4), "alpha", o.num("alpha", 0.95),
                "spin", o.num("spin", 0), "vel", o.vec("vel", null), "dir", o.vec("dir", null), "speed", o.numOrNull("speed")), glow, o.color("core", WHITE));
    }

    /** 육각 판 (rpg:rfx_hex — dir 에 수직인 면, dir = 판이 보는 쪽). o.speed &lt; 0 = 안쪽으로 움직임, o.coreAlpha = 테두리만 다른 불투명도 */
    public static void hexPlate(ServerLevel level, Vec3 at, Vec3 dir, double size, double[] glow, Opt o) {
        rfx2(level, "hex", at, Opt.of("dir", dir, "size", size, "life", o.num("life", 0.4), "speed", o.num("speed", 0), "grow", o.num("grow", 0), "fade",
                o.num("fade", 0.4), "alpha", o.num("alpha", 0.9), "spin", o.num("spin", 0), "vel", o.vec("vel", null)), glow, o.color("core", WHITE), null,
                o.numOrNull("coreAlpha"));
    }

    /** 머리 위 h 칸 (표식 자리) */
    public static Vec3 overHead(Entity e, double h) {
        return new Vec3(e.getX(), e.getEyeY() + h, e.getZ());
    }

    public static Vec3 overHead(Entity e) {
        return overHead(e, 0.55);
    }

    /** 몹이 움직이는 속도 (칸/초, 수평만) — 적에게 붙인 그림 (도발 표식 · 번개 구슬) 이 적을 따라가게 */
    public static Vec3 entVel(Entity e) {
        return bodyVel(e);
    }

    /** 활 쏘는 손 자리 (눈에서 조금 오른쪽 아래 앞) */
    public static Vec3 bowHand(ServerPlayer sp) {
        Vec3 view = sp.getViewVector(1F);
        return V.add(V.add(V.up(sp.getEyePosition(), -0.2), V.rightOf(V.flat(view)), 0.25), view, 0.6);
    }

    /**
     * 빛의 화살 (rpg:rfx_arrow) 이 from → to 로 날아감 (speed 칸/초, 걸리는 틱 — flyStreak 와 같음).
     * 그림은 손 앞 skip 칸에서 나타나고 (1인칭 화면을 가리지 않게) 촉이 to 에 닿으며 끝남
     */
    public static int flyArrow(ServerLevel level, Vec3 from, Vec3 to, double speed, double[] glow, double[] core, double size, double skip) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        double life = Math.max(0.05, len / speed);
        Vec3 u = V.unit(d);
        double s0 = Math.min(skip, Math.max(0, len - size));
        double dist = Math.max(0, len - s0 - size * 0.8);
        rfx2(level, "arrow", V.add(from, u, s0), Opt.of("dir", u, "speed", dist / life, "size", size, "life", life), glow, core);
        return Math.max(1, (int) Math.round(life * 20));
    }

    public static int flyArrow(ServerLevel level, Vec3 from, Vec3 to, double speed, double[] glow, double[] core) {
        return flyArrow(level, from, to, speed, glow, core, 0.75, 1.2);
    }

    /** 번개 한 줄기 (빛 + 흰 심지 — 번개 · 원소 검사) */
    public static void zap(ServerLevel level, Vec3 a, Vec3 b, double[] glow, double[] core, double thick, double life) {
        SkillFx.bolt(level, a, b, glow, Opt.of("jag", 0.5, "thick", thick, "life", life));
        SkillFx.bolt(level, a, b, core, Opt.of("jag", 0.3, "thick", thick * 0.35, "life", life * 0.8));
    }

    public static void zap(ServerLevel level, Vec3 a, Vec3 b, double[] glow, double[] core) {
        zap(level, a, b, glow, core, 0.22, 0.3);
    }

    /** 초승달 검기 한 장 (빛 + 심지 — 검사 · 마검사): 카메라를 보는 참격이라 멀어질수록 작아 보임 */
    public static void crescent(ServerLevel level, Vec3 at, Vec3 dir, double size, double[] glow, double[] core, double spin, double life) {
        SkillFx.arc(level, at, dir, Opt.of("shape", "heavy", "flip", false, "size", size, "life", life, "speed", 0.0, "spin", spin, "color", glow, "aura", core));
    }

    public static void crescent(ServerLevel level, Vec3 at, Vec3 dir, double size, double[] glow, double[] core) {
        crescent(level, at, dir, size, glow, core, -90, 0.16);
    }

    /** 무기를 든 손 자리 (hand — 눈앞 오른쪽 아래, 1인칭에서도 보임) · 수평 보는 쪽 (f) */
    public record Hand(Vec3 hand, Vec3 f) {}

    /** 무기를 든 손 자리 (handOf — 마검사 · 중력 · 평타 강화) */
    public static Hand handOf(ServerPlayer sp) {
        Vec3 eye = sp.getEyePosition();
        Vec3 f = V.flat(sp.getViewVector(1F));
        return new Hand(V.up(V.add(V.add(eye, f, 0.9), V.rightOf(f), 0.42), -0.5), f);
    }

    /** 검: 공격 피해 (노란 숫자 — 마검사 · 원소 검사) */
    public static double swordHit(ServerPlayer sp, LivingEntity e, double P, double mult) {
        return runeHit(sp, e, P, mult, "str", Kit.Cause.ATTACK, DamageNumbers.Style.RUNE);
    }

    /** 마력: 마법 피해 (방어구 무시, 보라 숫자 — 마검사 · 원소 검사) */
    public static double spellHit(ServerPlayer sp, LivingEntity e, double P, double mult) {
        return runeHit(sp, e, P, mult, "int", Kit.Cause.MAGIC, DamageNumbers.Style.SPELL);
    }

    // ------------------------------------------------------------ 삶 표시 (runes.js lives · deaths)

    /** 진짜로 죽거나 차원을 옮긴 횟수 (불사의 토템으로 산 것은 세지 않음) */
    private static final Map<UUID, Integer> LIVES = new HashMap<>();
    /** 죽은 횟수 (죽음으로만 끝나는 버프 — 광전사의 혼 · 대천사 강림 · 불사의 맹약) */
    private static final Map<UUID, Integer> DEATHS = new HashMap<>();
    /** 죽음 · 차원 이동 때 할 일 (콤보 · 표식 · 마검 부여 · 정령이 끝남 — 계열마다 넣음) */
    private static final List<Consumer<UUID>> LIFE_ENDED = new ArrayList<>();

    public static void onLifeEnded(Consumer<UUID> fn) {
        LIFE_ENDED.add(fn);
    }

    /** 죽음 · 차원 이동 · 탈것 부르기 (runes.js onLifeEnded · interruptRunes) */
    public static void lifeEnded(UUID id) {
        LIVES.merge(id, 1, Integer::sum);
        for (Consumer<UUID> fn : LIFE_ENDED) {
            try {
                fn.accept(id);
            } catch (RuntimeException e) {
                mmorpg.fabric.Mmorpg.LOG.warn("[mmorpg] rune life ended", e);
            }
        }
    }

    /** 누른 때의 삶 표시 (차원 + 죽음 · 차원 이동 횟수): 나중에 나가는 룬 · 마무리 · 순간이동은 이것이 같을 때만 */
    public static String lifeToken(ServerPlayer sp) {
        return sp.level().dimension().identifier() + "|" + LIVES.getOrDefault(sp.getUUID(), 0);
    }

    public static boolean sameLife(ServerPlayer sp, String token) {
        return !sp.isRemoved() && sp.isAlive() && !sp.isDeadOrDying() && lifeToken(sp).equals(token);
    }

    public static int deathToken(ServerPlayer sp) {
        return DEATHS.getOrDefault(sp.getUUID(), 0);
    }

    /** 그 버프를 건 뒤 죽지 않았는지 (쓰러지는 중도 아님) */
    public static boolean aliveSince(ServerPlayer sp, int token) {
        return !sp.isRemoved() && !sp.isDeadOrDying() && deathToken(sp) == token;
    }

    /** 플레이어 엔티티 찾기 (uuid — 나갔으면 null) */
    public static ServerPlayer player(ServerLevel level, UUID id) {
        return level.getServer().getPlayerList().getPlayer(id);
    }

    /** box 안의 살아 있는 엔티티 (조건) */
    public static <T extends Entity> List<T> entities(ServerLevel level, Class<T> cls, Vec3 c, double r, java.util.function.Predicate<T> ok) {
        return level.getEntitiesOfClass(cls, new AABB(c, c).inflate(r), ok);
    }
}
