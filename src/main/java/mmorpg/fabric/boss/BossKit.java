package mmorpg.fabric.boss;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgNet;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.mob.RpgEntities;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 보스 도구 (bosskit.js): 엔진 ({@link Bosses}) 과 보스별 패턴 (BossScarecrow …) 이 같이 씀. 이름은 애드온 그대로.
 * <ul>
 * <li>보스 피해 (bossHit): 스크립트 공격만 — 보스의 바닐라 근접은 BedrockMob.doHurtTarget 이 막고, 엔진의 ALLOW_DAMAGE 훅이 isOwnHit 로 한 번 더 확인</li>
 * <li>바닥 예고: 모두 둥근 판 (rpg:tele_disc) 과 차오르는 판 (rpg:tele_fill) — 원 (disc) · 선 (lineMarks) · 부채꼴 (fanMarks)</li>
 * <li>보스 안개 (syncBossFog · clearBossFog): 자바에는 /fog 가 없어 초월 룬 하늘빛 패킷 (RpgNet.SkyPayload) 을 오래 (1시간) 씌우고 같은 표로 걷음 —
 * 리소스 팩 fogs/*.json 의 거리 · 색 그대로 ({@link #FOGS})</li>
 * </ul>
 * 자바에서 달라진 점: JS 객체 선택값 ({@code {knock, effect, directional}}) 은 {@link Opt} (Opt.of("knock", knock(x, z, h), "directional", true)),
 * 색 [r, g, b] 은 double[], 차원 (dim) 은 ServerLevel, 소리 이름은 베드락 이름 그대로 (자바 소리로 바꾸는 표 — {@link #sound(String)}).
 * 확률은 {@link #rand} (시험이 정할 수 있게).
 */
public final class BossKit {
    private BossKit() {}

    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-boss");
    public static final Obj BOSS = Arena.BOSS;
    public static final Obj BOSSES = Arena.BOSSES;
    /** 플레이어 (minecraft:player) */
    public static final String PLAYER = "minecraft:player";

    /** 0 ~ 1 무작위 (Math.random — 시험이 바꿀 수 있음) */
    public static DoubleSupplier rand = Math::random;

    public static double random() {
        return rand.getAsDouble();
    }

    /** lo ~ hi 정수 무작위 (양 끝 포함 — 애드온 모듈의 rnd) */
    public static int rnd(int lo, int hi) {
        return lo + (int) Math.floor(random() * (hi - lo + 1));
    }

    /** 섞은 사본 (애드온 모듈의 shuffle — Fisher-Yates) */
    public static <T> List<T> shuffle(List<T> list) {
        List<T> a = new ArrayList<>(list);
        for (int i = a.size() - 1; i > 0; i--) {
            int j = (int) Math.floor(random() * (i + 1));
            T t = a.get(i);
            a.set(i, a.get(j));
            a.set(j, t);
        }
        return a;
    }

    // ------------------------------------------------------------ 이름

    /**
     * 보스 이름 (v2.2.0): "[Lv.레벨] 수식어 이름". 머리 위 이름표와 보스 막대 이름이 같은 글
     * (보스 막대 이름은 행동 팩 minecraft:boss 의 name — MobData.bossName)
     */
    public static String bossLabel(double level, String title, String name) {
        List<String> out = new ArrayList<>();
        out.add("§e[Lv." + Js.str(level) + "]");
        if (title != null && !title.isEmpty()) out.add("§6" + title);
        if (name != null && !name.isEmpty()) out.add("§c" + name);
        return String.join(" ", out);
    }

    // ------------------------------------------------------------ 스크립트 공격 표시

    /** 스크립트가 넣는 보스 공격: "맞는 쪽|보스" → 틱 */
    private static final Map<String, Long> OWN_HITS = new HashMap<>();

    public static void markOwn(Entity target, Entity src) {
        OWN_HITS.put(target.getUUID() + "|" + src.getUUID(), Tasks.now());
    }

    public static boolean isOwnHit(Entity target, Entity src) {
        Long t = OWN_HITS.get(target.getUUID() + "|" + src.getUUID());
        return t != null && Tasks.now() - t <= 1;
    }

    public static void cleanOwn(long now) {
        if (!OWN_HITS.isEmpty()) OWN_HITS.values().removeIf(t -> now - t > 2);
    }

    static void clear() {
        OWN_HITS.clear();
        FOG_TOKENS.clear();
    }

    // ------------------------------------------------------------ 대상

    /** 게임 모드 (시험용 가짜 플레이어도 실제 모드로) */
    static GameType modeOf(ServerPlayer sp) {
        return sp.gameMode.getGameModeForPlayer();
    }

    /** 공격해도 되는 플레이어: 살아 있고, 서바이벌 · 모험, 쓰러지는 중 아님 */
    public static boolean okTarget(Entity p) {
        if (!(p instanceof ServerPlayer sp) || sp.isRemoved()) return false;
        GameType gm = modeOf(sp);
        if (gm == GameType.CREATIVE || gm == GameType.SPECTATOR) return false;
        return !sp.isDeadOrDying();
    }

    /** 싸움에 들어간 것으로 보는 플레이어: 필드 + BOSS.combatMargin 칸 (울타리 · 성문 자리까지) 안, 싸울 수 있는 상태 */
    public static List<ServerPlayer> fighters(Bosses.Unit enc) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : Arena.playersIn(enc.a, BOSS.num("combatMargin"))) if (okTarget(p)) out.add(p);
        return out;
    }

    /** 필드 둘레의 플레이어 (알림용: 필드 + 8칸) */
    public static List<ServerPlayer> audience(Bosses.Unit enc) {
        return Arena.playersIn(enc.a, 8);
    }

    /** 가로 거리 */
    public static double hdist(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    /**
     * v2.20.0 평타 대상 고르기 (아이기스의 방패 도발): 평타가 닿는 사람들 (pool) 가운데 그 보스 (마리) 를 도발한 사람이 있으면 그 사람 (도발 중일 때만),
     * 없으면 from 에서 가로로 가장 가까운 사람. pool 이 비면 null. 패턴 (보스마다의 기술) 은 도발과 상관없이 고름
     */
    public static ServerPlayer meleeTarget(Bosses.Unit u, List<ServerPlayer> pool, Vec3 from) {
        Bosses.Taunt t = u == null ? null : u.taunt;
        if (t != null && Tasks.now() < t.until()) {
            for (ServerPlayer p : pool) if (p.getUUID().equals(t.id())) return p;
        }
        ServerPlayer best = null;
        double bd = Double.POSITIVE_INFINITY;
        for (ServerPlayer p : pool) {
            double d = hdist(p.position(), from);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    /**
     * 사람이 서 있는 바닥 높이 (v2.3.0): 땅에 서 있으면 발 높이, 뛰거나 떨어지는 중이면 발밑 8칸 안의 막힌 블록 위 (없으면 fallback — null 이면 발 높이).
     * 발밑 원 · 떨어지는 자리를 공중에 그리지 않게
     */
    public static double groundUnder(Entity p, Double fallback) {
        Vec3 l = p.position();
        if (p.onGround()) return l.y;
        int y0 = (int) Math.floor(l.y);
        for (int y = y0; y >= y0 - 8; y--) {
            BlockPos pos = BlockPos.containing(l.x, y - 1, l.z);
            if (!p.level().isLoaded(pos)) break;
            BlockState b = p.level().getBlockState(pos);
            if (!b.isAir() && b.getFluidState().isEmpty()) return y;
        }
        return fallback != null ? fallback : l.y;
    }

    /** 던진 것 (바위 · 덩이) 파티클의 중력 (칸/초² — rpg:boulder · rpg:glob 의 식과 같음) */
    public static final double LOB_GRAVITY = 20;

    /** 포물선의 처음 방향 · 빠르기 */
    public record Arc(Vec3 dir, double speed) {}

    /** from 에서 던져 t 초 뒤 to 에 떨어지는 포물선의 처음 방향 · 빠르기 (중력 g) */
    public static Arc lobArc(Vec3 from, Vec3 to, double t, double g) {
        Vec3 v = new Vec3((to.x - from.x) / t, (to.y - from.y) / t + (g * t) / 2, (to.z - from.z) / t);
        double speed = Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
        if (speed == 0) speed = 1;
        return new Arc(new Vec3(v.x / speed, v.y / speed, v.z / speed), speed);
    }

    public static Arc lobArc(Vec3 from, Vec3 to, double t) {
        return lobArc(from, to, t, LOB_GRAVITY);
    }

    /** 몸 크기 (크기 배율 포함): 가로 반폭, 키 */
    public record Size(double half, double height) {}

    /** 보스 몸 크기 (설정 box × scale) */
    public static Size bossSize(Obj def) {
        List<Double> box = def.numbersOrNull("box");
        double scale = def.numOr("scale", 1);
        if (box == null || box.size() < 2) return new Size(0.6, 3);
        return new Size((box.get(0) * scale) / 2, box.get(1) * scale);
    }

    // ------------------------------------------------------------ 보스 피해

    /** 밀기 (애드온 {x, z, h} — 수평 속도 x · z, 위로 h) */
    public record Knock(double x, double z, double h) {}

    public static Knock knock(double x, double z, double h) {
        return new Knock(x, z, h);
    }

    /** 효과 (애드온 {id, ticks, amp} — id 는 베드락 이름: blindness · slowness · poison · wither …) */
    public record Effect(String id, int ticks, int amp) {}

    public static Effect effect(String id, int ticks) {
        return new Effect(id, ticks, 0);
    }

    public static Effect effect(String id, int ticks, int amp) {
        return new Effect(id, ticks, amp);
    }

    /** 설정의 효과 ({id, ticks, amp} — 없으면 null) */
    public static Effect effect(Obj o) {
        if (o == null || o.strOrNull("id") == null) return null;
        return new Effect(o.str("id"), (int) o.numOr("ticks", 0), (int) o.numOr("amp", 0));
    }

    /** 효과 걸기 (애드온 addEffect(id, ticks, {amplifier, showParticles})) — 없는 효과 이름은 무시 */
    public static void addEffect(LivingEntity e, String id, int ticks, int amp, boolean particles) {
        Holder<MobEffect> h = RuneKit.effectOf(id);
        if (h != null && !e.isRemoved() && ticks > 0) e.addEffect(new MobEffectInstance(h, ticks, amp, false, particles));
    }

    /** 베드락 피해 원인 (EntityDamageCause) → 자바 피해 종류 */
    private static ResourceKey<DamageType> typeOf(String cause, boolean withSource) {
        if (cause == null) return withSource ? DamageTypes.MOB_ATTACK : DamageTypes.FALLING_BLOCK;
        return switch (cause) {
            case "entityAttack" -> DamageTypes.MOB_ATTACK;
            case "magic" -> withSource ? DamageTypes.INDIRECT_MAGIC : DamageTypes.MAGIC;
            case "entityExplosion", "blockExplosion" -> DamageTypes.EXPLOSION;
            case "projectile" -> DamageTypes.MOB_PROJECTILE;
            case "fallingBlock" -> DamageTypes.FALLING_BLOCK;
            case "fire" -> DamageTypes.IN_FIRE;
            case "fireTick" -> DamageTypes.ON_FIRE;
            case "lava" -> DamageTypes.LAVA;
            case "freezing" -> DamageTypes.FREEZE;
            case "drowning" -> DamageTypes.DROWN;
            case "suffocation" -> DamageTypes.IN_WALL;
            case "wither" -> DamageTypes.WITHER;
            case "sonicBoom" -> DamageTypes.SONIC_BOOM;
            case "lightning" -> DamageTypes.LIGHTNING_BOLT;
            case "contact" -> DamageTypes.CACTUS;
            default -> DamageTypes.GENERIC;
        };
    }

    /** 피해 출처: src (때린 엔티티 — null 이면 없이) · 원인 (베드락 이름) */
    public static DamageSource source(ServerLevel level, Entity src, String cause) {
        ResourceKey<DamageType> type = typeOf(cause, src != null);
        return src != null ? level.damageSources().source(type, src, src) : level.damageSources().source(type);
    }

    /**
     * 보스 피해 한 번 (스크립트). 들어갔으면 true.
     * o: directional = 보스 쪽에서 오는 공격 (평타 — 방패를 보스 쪽으로 웅크려 막을 수 있음. 나머지 (발밑 폭발 · 까마귀 · 기믹 벌) 는 방향 막기 없음),
     * cause (베드락 원인 이름 — 기본 entityAttack, noSource 면 fallingBlock), knock ({@link Knock}), from (때린 엔티티 — 기본 그 마리),
     * effect ({@link Effect}), noSource (때린 엔티티 없이: 게임이 보스 쪽에서 밀어내는 넉백을 넣지 않게).
     * 여러 마리 싸움은 때리는 마리 (unit) 를 넘김 (그 마리가 공격한 것으로, 분노 배율 dmgMult 가 곱해짐)
     */
    public static boolean bossHit(Bosses.Unit enc, ServerPlayer target, double amount, Opt o) {
        Entity src = o.get("from") instanceof Entity f ? f : enc.boss;
        if (src == null || src.isRemoved() || !okTarget(target) || !(amount > 0)) return false;
        markOwn(target, src);
        if (!o.truthy("directional")) RpgCombat.markNoBlock(target);
        double dmg = amount * enc.dmgMult;
        boolean ok;
        try {
            ServerLevel level = target.level();
            boolean none = o.truthy("noSource");
            ok = target.hurtServer(level, source(level, none ? null : src, o.str("cause", none ? "fallingBlock" : "entityAttack")), (float) dmg);
        } catch (RuntimeException e) {
            LOG.warn("[mmorpg] boss hit", e);
            ok = false;
        }
        if (o.get("knock") instanceof Knock k && !RpgCombat.isSteadfast(target) && !target.isRemoved()) {
            // v2.24.0 강철 근육 (신체 강화): 밀려나지 않음
            Kit.knockback(target, k.x(), k.z(), k.h());
        }
        if (ok && o.get("effect") instanceof Effect ef) addEffect(target, ef.id(), ef.ticks(), ef.amp(), true);
        return ok;
    }

    public static boolean bossHit(Bosses.Unit enc, ServerPlayer target, double amount) {
        return bossHit(enc, target, amount, Opt.empty());
    }

    // ------------------------------------------------------------ 알림 · 소리

    /** 채팅 (list 가 없으면 필드 둘레 모두) */
    public static void say(Bosses.Unit enc, String text, List<ServerPlayer> list) {
        Component c = Component.literal(BedrockText.toJava(text));
        for (ServerPlayer p : list != null ? list : audience(enc)) {
            if (!p.isRemoved()) p.sendSystemMessage(c);
        }
    }

    public static void say(Bosses.Unit enc, String text) {
        say(enc, text, null);
    }

    /** 큰 글자 (list 가 없으면 필드 둘레 모두 — hp.js showRealTitle) */
    public static void title(Bosses.Unit enc, String big, String small, List<ServerPlayer> list) {
        for (ServerPlayer p : list != null ? list : audience(enc)) {
            if (!p.isRemoved()) Notify.title(p, big, small == null ? "" : small);
        }
    }

    public static void title(Bosses.Unit enc, String big, String small) {
        title(enc, big, small, null);
    }

    /** 플레이어 눈앞 글자 (fx.js showFaceText — o: dist (기본 2.2) · side (0) · up (0.2)) */
    public static void showFaceText(ServerPlayer p, String text, Opt o) {
        if (p == null || p.isRemoved()) return;
        DamageNumbers.showFace(p, text, o.num("dist", 2.2), o.num("side", 0), o.num("up", 0.2));
    }

    /** 모두의 눈앞 글자 (카운트다운 등, list 가 없으면 필드 둘레 모두) */
    public static void faceAll(Bosses.Unit enc, String text, Opt o, List<ServerPlayer> list) {
        for (ServerPlayer p : list != null ? list : audience(enc)) showFaceText(p, text, o);
    }

    /** 화면 하단 알림 (fx.js notify) */
    public static void notify(ServerPlayer p, String text, int hold) {
        if (p != null && !p.isRemoved()) Notify.notify(p, text, hold);
    }

    // ------------------------------------------------------------ 0.34.0 패턴 안내 글자 (설정 BOSS_HINTS)
    // 사용자: "패턴을 알려주는 채팅이나 타이틀 같은 거를 없애고 카운트만 텍스트로 — 여러 번 리트라이를 겪어야 잡을 수 있게",
    // "빈틈, 약점 같은 것도 빼고, 공허 각성 같은 2페이즈는 놔두고".
    // 보스 모듈의 패턴 설명 · 상황 채팅 · 패턴 이름 타이틀 · 눈앞 안내 글자 · 화면 아래 안내는 아래 hint* 로만 보냄 (설정이 false 면 보내지 않음).
    // 카운트 (숫자만) · 보스 이름 · 소환 · 2페이즈 알림 · 처치 · 보상 알림 · 피해 숫자 · 상태 이상 표시는 늘 보임 (say · title · showFaceText · faceAll · notify 를 그대로 씀).
    // 피해 숫자 앞 표시 (빈틈 · 약점 · 지침 · 반격 …) 는 엔진 (Bosses.processHit) 이 BOSS_HINTS.marks 일 때만 붙임 (가까이 » 는 그대로).

    /** 숨긴 안내 수 (시험 — 패턴이 안내를 보내려 했는지) */
    public static int hiddenHints;

    /** 그 종류의 패턴 안내를 보일지: BOSS_HINTS.chat (채팅 · 화면 아래 안내) · title (타이틀) · face (눈앞 글자) · notes (도전 창의 패턴을 깨는 법 · 원거리 견제 줄) · marks (피해 숫자 앞 빈틈 · 약점 …) */
    public static boolean hints(String kind) {
        Obj h = mmorpg.core.Rules.get().root().objOrNull("BOSS_HINTS");
        return h != null && h.truthy(kind);
    }

    private static boolean hint(String kind) {
        if (hints(kind)) return true;
        hiddenHints++;
        return false;
    }

    /** 패턴 안내 채팅 (설정 BOSS_HINTS.chat 일 때만) */
    public static void hintSay(Bosses.Unit enc, String text, List<ServerPlayer> list) {
        if (hint("chat")) say(enc, text, list);
    }

    public static void hintSay(Bosses.Unit enc, String text) {
        hintSay(enc, text, null);
    }

    /** 패턴 · 페이즈 이름 타이틀 (BOSS_HINTS.title 일 때만) */
    public static void hintTitle(Bosses.Unit enc, String big, String small, List<ServerPlayer> list) {
        if (hint("title")) title(enc, big, small, list);
    }

    public static void hintTitle(Bosses.Unit enc, String big, String small) {
        hintTitle(enc, big, small, null);
    }

    /** 눈앞 안내 글자 (피하는 법 · 안전 · 맞았음 — BOSS_HINTS.face 일 때만) */
    public static void hintFace(ServerPlayer p, String text, Opt o) {
        if (hint("face")) showFaceText(p, text, o);
    }

    public static void hintFaceAll(Bosses.Unit enc, String text, Opt o, List<ServerPlayer> list) {
        if (hint("face")) faceAll(enc, text, o, list);
    }

    /** 보스가 건 속박의 눈앞 글자 (Status.root): 패턴 이름 (짚단 감옥 · 석화 · 빙결 …) 은 BOSS_HINTS.face 일 때만, 아니면 상태 이름 "속박" */
    public static String rootLabel(String label) {
        return hints("face") ? label : "속박";
    }

    /** 화면 아래 패턴 안내 (BOSS_HINTS.chat 일 때만) */
    public static void hintNotify(ServerPlayer p, String text, int hold) {
        if (hint("chat")) notify(p, text, hold);
    }

    /** 그 마리 (없으면 가운데) 에서 소리 (at 이 null 이면 보스 자리) */
    public static void sound(Bosses.Unit enc, String id, Vec3 at, double pitch, double volume) {
        if (id == null) return;
        Vec3 where = at != null ? at : enc.boss != null && !enc.boss.isRemoved() ? enc.boss.position() : enc.center;
        play(enc.dim, id, where, pitch, volume);
    }

    /** 설정의 소리 이름 (sounds.키 — 없으면 null) */
    public static String snd(Bosses.Unit u, String key) {
        Obj s = u.def.objOrNull("sounds");
        return s == null ? null : s.strOrNull(key);
    }

    // ------------------------------------------------------------ 소리 이름 (베드락 → 자바)

    /** 보스가 쓰는 베드락 소리 중 SkillFx 의 대응표에 없는 것 (자바 이름 — 앞의 것이 없으면 다음 것) */
    private static final Map<String, String[]> SOUNDS = new LinkedHashMap<>();

    static {
        String[][] map = {
            {"ambient.underwater.enter", "ambient.underwater.enter"}, {"break.amethyst_block", "block.amethyst_block.break"},
            {"bubble.down", "block.bubble_column.whirlpool_inside"}, {"bubble.pop", "block.bubble_column.bubble_pop"},
            {"bucket.empty_lava", "item.bucket.empty_lava"}, {"dig.chain", "block.chain.break", "block.iron_chain.break"},
            {"dig.deepslate", "block.deepslate.break"}, {"dig.grass", "block.grass.break"}, {"dig.sand", "block.sand.break"},
            {"dig.snow", "block.snow.break"}, {"dig.wood", "block.wood.break"}, {"entity.generic.splash", "entity.generic.splash"},
            {"extinguish.candle", "block.candle.extinguish"}, {"hit.chain", "block.chain.hit", "block.iron_chain.hit"},
            {"mob.bee.sting", "entity.bee.sting"}, {"mob.blaze.breathe", "entity.blaze.ambient"}, {"mob.breeze.inhale", "entity.breeze.inhale"},
            {"mob.cat.hiss", "entity.cat.hiss"}, {"mob.dolphin.swim", "entity.dolphin.swim"},
            {"mob.elderguardian.death", "entity.elder_guardian.death"}, {"mob.elderguardian.hit", "entity.elder_guardian.hurt"},
            {"mob.elderguardian.idle", "entity.elder_guardian.ambient"}, {"mob.enderdragon.death", "entity.ender_dragon.death"},
            {"mob.enderdragon.hit", "entity.ender_dragon.hurt"}, {"mob.evocation_illager.prepare_wololo", "entity.evoker.prepare_wololo"},
            {"mob.glow_squid.ink_squirt", "entity.glow_squid.squirt"}, {"mob.guardian.death", "entity.guardian.death"},
            {"mob.guardian.flop", "entity.guardian.flop"}, {"mob.guardian.hit", "entity.guardian.hurt"}, {"mob.hoglin.attack", "entity.hoglin.attack"},
            {"mob.irongolem.death", "entity.iron_golem.death"}, {"mob.irongolem.throw", "entity.iron_golem.attack"}, {"mob.llama.spit", "entity.llama.spit"},
            {"mob.parrot.idle", "entity.parrot.ambient"}, {"mob.phantom.swoop", "entity.phantom.swoop"}, {"mob.ravager.bite", "entity.ravager.attack"},
            {"mob.ravager.step", "entity.ravager.step"}, {"mob.slime.big", "entity.slime.squish", "entity.slime.jump"},
            {"mob.snowgolem.shoot", "entity.snow_golem.shoot"}, {"mob.spider.death", "entity.spider.death"}, {"mob.spider.say", "entity.spider.ambient"},
            {"mob.squid.ambient", "entity.squid.ambient"}, {"mob.squid.ink_squirt", "entity.squid.squirt"},
            {"mob.warden.attack", "entity.warden.attack_impact"}, {"mob.warden.clicking", "entity.warden.tendril_clicks"},
            {"mob.warden.death", "entity.warden.death"}, {"mob.warden.dig", "entity.warden.dig"}, {"mob.warden.listening", "entity.warden.listening"},
            {"mob.warden.listening_angry", "entity.warden.listening_angry"}, {"mob.warden.roar", "entity.warden.roar"},
            {"mob.warden.sniff", "entity.warden.sniff"}, {"mob.warden.step", "entity.warden.step"}, {"mob.witch.celebrate", "entity.witch.celebrate"},
            {"mob.wither.break_block", "entity.wither.break_block"}, {"mob.wither.death", "entity.wither.death"},
            {"mob.wolf.big.bark", "entity.wolf_big.ambient", "entity.wolf.ambient"}, {"mob.wolf.big.death", "entity.wolf_big.death", "entity.wolf.death"},
            {"mob.wolf.big.growl", "entity.wolf_big.growl", "entity.wolf.growl"}, {"mob.wolf.big.panting", "entity.wolf_big.pant", "entity.wolf.pant"},
            {"mob.wolf.big.whine", "entity.wolf_big.whine", "entity.wolf.whine"}, {"mob.wolf.step", "entity.wolf.step"},
            {"mob.zombie.remedy", "entity.zombie_villager.cure"}, {"random.burp", "entity.player.burp"}, {"random.eat", "entity.generic.eat"},
            {"random.fuse", "entity.tnt.primed"}, {"shriek.sculk_shrieker", "block.sculk_shrieker.shriek"},
        };
        for (String[] m : map) {
            String[] ids = new String[m.length - 1];
            System.arraycopy(m, 1, ids, 0, ids.length);
            SOUNDS.put(m[0], ids);
        }
    }

    /** 보스 10종 · 엔진 · 상태가 쓰는 소리 이름 모두 (베드락 이름 · 0.33.0 공허 기사의 자바 이름 — 시험: 모두 자바 소리로 풀리는지) */
    public static final List<String> USED_SOUNDS = List.of("ambient.underwater.enter", "ambient.weather.thunder", "beacon.activate", "block.bell.hit",
            "block.bell.resonate", "block.end_portal_frame.fill", "block.note_block.basedrum", "block.respawn_anchor.charge", "block.respawn_anchor.deplete",
            "entity.breeze.wind_burst", "entity.enderman.teleport", "entity.iron_golem.step", "entity.player.attack.sweep", "entity.warden.attack_impact",
            "item.mace.smash_ground_heavy", "block.grindstone.use", "break.amethyst_block", "bubble.down", "bubble.pop", "bucket.empty_lava", "dig.chain", "dig.deepslate", "dig.grass", "dig.sand",
            "dig.snow", "dig.stone", "dig.wood", "entity.generic.splash", "extinguish.candle", "fire.fire", "fire.ignite", "hit.chain", "item.trident.riptide_1",
            "item.trident.throw", "mace.heavy_smash_ground", "mace.smash_air", "mace.smash_ground", "mob.bee.sting", "mob.blaze.breathe", "mob.blaze.shoot",
            "mob.breeze.charge", "mob.breeze.inhale", "mob.cat.hiss", "mob.dolphin.swim", "mob.elderguardian.curse", "mob.elderguardian.death",
            "mob.elderguardian.hit", "mob.elderguardian.idle", "mob.enderdragon.death", "mob.enderdragon.flap", "mob.enderdragon.growl", "mob.enderdragon.hit",
            "mob.evocation_illager.cast_spell", "mob.evocation_illager.prepare_attack", "mob.evocation_illager.prepare_summon",
            "mob.evocation_illager.prepare_wololo", "mob.ghast.fireball", "mob.glow_squid.ink_squirt", "mob.guardian.death", "mob.guardian.flop",
            "mob.guardian.hit", "mob.hoglin.attack", "mob.irongolem.death", "mob.irongolem.throw", "mob.llama.spit", "mob.parrot.idle", "mob.phantom.bite",
            "mob.phantom.swoop", "mob.ravager.bite", "mob.ravager.roar", "mob.ravager.step", "mob.ravager.stun", "mob.slime.big", "mob.snowgolem.shoot",
            "mob.spider.death", "mob.spider.say", "mob.squid.ambient", "mob.squid.ink_squirt", "mob.warden.attack", "mob.warden.clicking", "mob.warden.death",
            "mob.warden.dig", "mob.warden.emerge", "mob.warden.heartbeat", "mob.warden.listening", "mob.warden.listening_angry", "mob.warden.roar",
            "mob.warden.sniff", "mob.warden.sonic_boom", "mob.warden.sonic_charge", "mob.warden.step", "mob.witch.celebrate", "mob.wither.break_block",
            "mob.wither.death", "mob.wither.spawn", "mob.wolf.big.bark", "mob.wolf.big.death", "mob.wolf.big.growl", "mob.wolf.big.panting",
            "mob.wolf.big.whine", "mob.wolf.step", "mob.zombie.remedy", "mob.zombie.unfect", "note.bass", "place.chain", "random.anvil_break",
            "random.anvil_land", "random.anvil_use", "random.burp", "random.eat", "random.explode", "random.fizz", "random.fuse", "random.glass",
            "random.splash", "random.totem", "respawn_anchor.charge", "shriek.sculk_shrieker", "wind_charge.burst");

    private static final Set<String> SOUND_WARNED = new HashSet<>();

    /** 베드락 소리 이름 → 자바 소리 (보스 표 → 없으면 SkillFx 의 대응표, 그래도 없으면 null — 한 번 로그) */
    public static SoundEvent sound(String bedrock) {
        if (bedrock == null) return null;
        String[] ids = SOUNDS.get(bedrock);
        if (ids != null) {
            for (String id : ids) {
                Identifier rl = Identifier.withDefaultNamespace(id);
                if (BuiltInRegistries.SOUND_EVENT.containsKey(rl)) return BuiltInRegistries.SOUND_EVENT.getValue(rl);
            }
            if (SOUND_WARNED.add(bedrock)) LOG.warn("[mmorpg] boss: no Java sound for {}", bedrock);
            return null;
        }
        return SkillFx.sound(bedrock);
    }

    /** 대응표의 이름 모두 (시험용) */
    public static Set<String> soundNames() {
        Set<String> all = new TreeSet<>(SOUNDS.keySet());
        all.addAll(USED_SOUNDS);
        return all;
    }

    /** 그 자리에서 소리 (fx.js play(dim, 이름, 자리, pitch, volume)) */
    public static void play(ServerLevel level, String bedrock, Vec3 at, double pitch, double volume) {
        SoundEvent s = sound(bedrock);
        if (s == null || level == null || at == null) return;
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.HOSTILE, (float) volume, (float) pitch);
    }

    // ------------------------------------------------------------ 바닥 예고

    public static final double[] RED = {1.0, 0.22, 0.16};

    /**
     * 원 (반지름 radius): 테두리 판 + ticks 동안 가운데서 차오르는 판 (다 차면 터짐). at = 발 높이.
     * o: fill (false 면 차오르는 판 없이) · alpha (테두리, 기본 0.85)
     */
    public static void disc(ServerLevel dim, Vec3 at, double radius, int ticks, double[] color, Opt o) {
        double life = Math.max(0.1, ticks / 20.0);
        double[] c = color != null ? color : RED;
        SkillFx.fx(dim, "rpg:tele_disc", new Vec3(at.x, at.y + 0.06, at.z), Opt.of("dir", Vec3.ZERO, "size", radius, "life", life, "color", c, "alpha",
                o.num("alpha", 0.85)));
        if (!(o.get("fill") instanceof Boolean b && !b)) {
            SkillFx.fx(dim, "rpg:tele_fill", new Vec3(at.x, at.y + 0.08, at.z), Opt.of("dir", Vec3.ZERO, "size", radius, "life", life, "color", c, "alpha", 0.55));
        }
    }

    public static void disc(ServerLevel dim, Vec3 at, double radius, int ticks, double[] color) {
        disc(dim, at, radius, ticks, color, Opt.empty());
    }

    /** 선 (from → to, 폭 width): 선을 따라 작은 판 */
    public static void lineMarks(ServerLevel dim, Vec3 from, Vec3 to, double width, int ticks, double[] color) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double L = Math.hypot(dx, dz);
        if (L < 0.1) return;
        double step = Math.max(0.6, width * 0.9);
        int n = Math.max(2, (int) Math.ceil(L / step));
        double life = Math.max(0.1, ticks / 20.0);
        double[] c = color != null ? color : RED;
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            SkillFx.fx(dim, "rpg:tele_fill", new Vec3(from.x + dx * t, from.y + 0.07, from.z + dz * t), Opt.of("dir", Vec3.ZERO, "size", width, "life", life,
                    "color", c, "alpha", 0.5, "grow", 1.0));
        }
    }

    /**
     * 부채꼴 (꼭짓점 apex, 방향 dir (가로 단위), 반지름 radius, 벌린 각 angle 도): 극좌표 격자에 작은 판 + 테두리.
     * step = 격자 간격 (칸, 큰 부채꼴은 1.5 로 파티클 수를 줄임 — 판도 그만큼 크게)
     */
    public static void fanMarks(ServerLevel dim, Vec3 apex, Vec3 dir, double radius, double angle, int ticks, double[] color, double step) {
        double base = Math.atan2(dir.z, dir.x);
        double half = (angle * Math.PI) / 360;
        double life = Math.max(0.1, ticks / 20.0);
        double[] c = color != null ? color : RED;
        for (double r = step; r <= radius + 0.01; r += step) {
            int n = Math.max(2, (int) Math.ceil((r * 2 * half) / step));
            for (int i = 0; i <= n; i++) {
                double a = base - half + (2 * half * i) / n;
                boolean edge = r + step > radius + 0.01 || i == 0 || i == n;
                SkillFx.fx(dim, edge ? "rpg:tele_disc" : "rpg:tele_fill", new Vec3(apex.x + Math.cos(a) * r, apex.y + 0.07, apex.z + Math.sin(a) * r),
                        Opt.of("dir", Vec3.ZERO, "size", (edge ? 0.55 : 0.75) * step, "life", life, "color", c, "alpha", edge ? 0.9 : 0.45, "grow", edge ? 0.0 : 1.0));
            }
        }
    }

    public static void fanMarks(ServerLevel dim, Vec3 apex, Vec3 dir, double radius, double angle, int ticks, double[] color) {
        fanMarks(dim, apex, dir, radius, angle, ticks, color, 1);
    }

    /** 점 p 가 부채꼴 안인지 (가로, pad 만큼 너그럽게) */
    public static boolean inFan(Vec3 p, Vec3 apex, Vec3 dir, double radius, double angle, double pad) {
        double dx = p.x - apex.x;
        double dz = p.z - apex.z;
        double d = Math.hypot(dx, dz);
        if (d > radius + pad) return false;
        if (d < 0.8) return true;
        double cos = (dx * dir.x + dz * dir.z) / d;
        return cos >= Math.cos(((angle / 2) * Math.PI) / 180) - 1e-9;
    }

    public static boolean inFan(Vec3 p, Vec3 apex, Vec3 dir, double radius, double angle) {
        return inFan(p, apex, dir, radius, angle, 0);
    }

    /** 점 p 가 선분 a-b 에서 width 안인지 (가로) */
    public static boolean nearSegment(Vec3 p, Vec3 a, Vec3 b, double width) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        double L2 = dx * dx + dz * dz;
        double t = L2 > 0 ? Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.z - a.z) * dz) / L2)) : 0;
        return Math.hypot(p.x - (a.x + dx * t), p.z - (a.z + dz * t)) <= width;
    }

    /** 보스 설정 (BOSSES[key]) */
    public static Obj bossDef(String key) {
        return BOSSES.objOrNull(key);
    }

    // ------------------------------------------------------------ 엔티티 (베드락 spawnEntity · teleport)

    /**
     * 애드온 엔티티 소환 (dim.spawnEntity(id, 자리)): yaw = 바라보는 쪽 (자바 각도 — 0 = 남쪽). 못 만들면 null.
     * 소환한 소품 · 보스는 엔진에 넣어야 (addProp · own) 남은 것 정리에서 빠짐
     */
    public static BedrockMob spawn(ServerLevel level, String id, Vec3 at, float yaw) {
        EntityType<? extends BedrockMob> type = RpgEntities.type(id);
        if (type == null) return null;
        BedrockMob e = type.create(level, EntitySpawnReason.COMMAND);
        if (e == null) return null;
        e.snapTo(at.x, at.y, at.z, yaw, 0F);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
        return level.addFreshEntity(e) ? e : null;
    }

    public static BedrockMob spawn(ServerLevel level, String id, Vec3 at) {
        return spawn(level, id, at, 0F);
    }

    /** from → to 를 보는 자바 가로 각도 (도) */
    public static float yawTo(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    /** 애드온 각도 (라디안, +x 에서 +z 쪽으로 — Math.atan2(dz, dx)) → 자바 가로 각도 (도) */
    public static float yawOfAng(double ang) {
        return (float) Math.toDegrees(Math.atan2(-Math.cos(ang), Math.sin(ang)));
    }

    /**
     * 옮기기 (베드락 teleport(자리, {facingLocation})): 블록과 겹쳐도 옮김 (베드락처럼), facing 이 있으면 그쪽을 봄 (가로만 — 고개를 숙이지 않게),
     * 없으면 보던 쪽 그대로. 속도는 0 으로
     */
    public static void teleport(Entity e, Vec3 at, Vec3 facing) {
        if (e == null || e.isRemoved() || !(e.level() instanceof ServerLevel level)) return;
        float yaw = facing != null && hdist(at, facing) > 1e-6 ? yawTo(at, facing) : e.getYRot();
        place(e, level, at, yaw);
    }

    public static void teleport(Entity e, Vec3 at) {
        teleport(e, at, null);
    }

    /** 자리 · 가로 각도를 바로 정함 (몸 · 머리도) */
    public static void place(Entity e, ServerLevel level, Vec3 at, float yaw) {
        e.teleportTo(level, at.x, at.y, at.z, Set.of(), yaw, 0F, false);
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
        if (!(e instanceof ServerPlayer)) e.setDeltaMovement(Vec3.ZERO);
    }

    // ------------------------------------------------------------ 설정 도우미 (JS 객체 → Obj)

    /** 설정의 색 [r, g, b] (없으면 흰색) */
    public static double[] rgb(Obj o, String key) {
        List<Double> c = o == null ? null : o.numbersOrNull(key);
        if (c == null || c.size() < 3) return SkillFx.WHITE;
        return new double[] {c.get(0), c.get(1), c.get(2)};
    }

    /** 설정 [a, b] (every · spawn 등 — 없으면 null) */
    public static double[] pair(Obj o, String key) {
        List<Double> c = o == null ? null : o.numbersOrNull(key);
        if (c == null || c.size() < 2) return null;
        return new double[] {c.get(0), c.get(1)};
    }

    /** 키 · 값으로 만든 Obj (모듈이 덮어쓸 설정 — farStyle 등): obj("name", "달 서리", "look", "frost", "effect", Map.of(…)) */
    public static Obj obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) if (kv[i + 1] != null) m.put((String) kv[i], kv[i + 1]);
        return new Obj(Collections.unmodifiableMap(m), "boss");
    }

    /** { ...a, ...b } (b 가 null 이면 a) */
    public static Obj merge(Obj a, Obj b) {
        if (b == null) return a;
        Map<String, Object> m = new LinkedHashMap<>();
        if (a != null) for (String k : a.keys()) m.put(k, a.get(k));
        for (String k : b.keys()) m.put(k, b.get(k));
        return new Obj(Collections.unmodifiableMap(m), "boss");
    }

    // ------------------------------------------------------------ 보스 안개 (v2.12.0 공통 — v2.5.0 이미르와 같은 규칙)

    /** 베드락 /fog 이름표 (자바는 표 — FOG_TOKENS) */
    public static final String BOSS_FOG_TAG = "rpg_boss_fog";

    /** 보스 안개 (리소스 팩 fogs/*.json): 이름 → {fog_start, fog_end, 색 r, g, b} */
    public static final Map<String, float[]> FOGS = Map.of(
            "rpg:ymir_blizzard", fog(3, 26, 0xDCE7F2),
            "rpg:ymir_winter", fog(10, 48, 0xCFDCEA),
            "rpg:surtr_inferno", fog(18, 80, 0xC2541C),
            "rpg:surtr_ragnarok", fog(6, 48, 0x8A1E0C),
            // 0.33.0 공허 기사: 각성 (보랏빛 먼 안개) · 공허 붕괴 (검게 닫힘)
            "rpg:void_awake", fog(14, 72, 0x2A1846),
            "rpg:void_collapse", fog(2, 16, 0x0C0614));
    /** 씌운 안개가 이만큼 (틱) 뒤 저절로 걷힘 — 싸움이 끝나면 엔진이 일찍 걷음 (1시간) */
    public static final int FOG_TICKS = 20 * 60 * 60;

    private static float[] fog(float start, float end, int rgb) {
        return new float[] {start, end, ((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F};
    }

    /** 사람마다 씌운 보스 안개의 표 (초월 룬 하늘빛과 겹치지 않게 음수) */
    private static final Map<UUID, Integer> FOG_TOKENS = new HashMap<>();
    private static int fogSeq;

    /**
     * 안개 바꾸기: had (이 싸움이 씌운 안개가 있음) 이면 먼저 걷고, id 가 있으면 씌움. 씌웠으면 true.
     * 지우기와 씌우기는 따로 (씌운 적 없는 안개를 지우는 것이 실패해도 씌움)
     */
    public static boolean setBossFog(ServerPlayer p, String id, boolean had) {
        if (had) {
            Integer t = FOG_TOKENS.remove(p.getUUID());
            if (t != null && !p.isRemoved()) RpgNet.sky(p, new RpgNet.SkyPayload(t, 0, 0, 0, 0, 0, 0));
        }
        if (id == null || p.isRemoved()) return false;
        float[] f = FOGS.get(id);
        if (f == null) return false;
        fogSeq = fogSeq <= -1_000_000_000 ? -1 : fogSeq - 1;
        int token = fogSeq;
        FOG_TOKENS.put(p.getUUID(), token);
        RpgNet.sky(p, new RpgNet.SkyPayload(token, FOG_TICKS, f[0], f[1], f[2], f[3], f[4]));
        return true;
    }

    /** 이 사람의 보스 안개를 걷음 (싸움 기록과 상관없이 — 엔진의 clearBossFog(list)) */
    static void dropFog(ServerPlayer p) {
        Integer t = FOG_TOKENS.remove(p.getUUID());
        if (t != null && !p.isRemoved()) RpgNet.sky(p, new RpgNet.SkyPayload(t, 0, 0, 0, 0, 0, 0));
    }

    /** 이 싸움 기억의 안개 (플레이어 → 씌운 것) */
    public record Fog(ServerPlayer p, String id) {}

    /**
     * 매 틱 (바뀔 때) · 10틱마다: 필드 둘레 사람의 안개를 want 로 맞추고, 떠난 사람은 걷음. 기억은 mem.fog (플레이어 → {p, id}) · mem.fogWant
     */
    public static void syncBossFog(Bosses.Unit u, String want, long now, boolean force) {
        Bosses.Mem m = u.mem;
        if (m.fog == null) m.fog = new LinkedHashMap<>();
        if (!force && java.util.Objects.equals(want, m.fogWant) && now % 10 != 0) return;
        m.fogWant = want;
        Set<UUID> here = new HashSet<>();
        for (ServerPlayer p : audience(u)) {
            here.add(p.getUUID());
            Fog cur = m.fog.get(p.getUUID());
            if (java.util.Objects.equals(cur == null ? null : cur.id(), want)) continue;
            // 씌우지 못하면 기억하지 않음 (다음 0.5초에 다시)
            if (setBossFog(p, want, cur != null)) m.fog.put(p.getUUID(), new Fog(p, want));
            else m.fog.remove(p.getUUID());
        }
        for (Map.Entry<UUID, Fog> f : new ArrayList<>(m.fog.entrySet())) {
            if (here.contains(f.getKey())) continue;
            if (f.getValue().p() != null && !f.getValue().p().isRemoved()) setBossFog(f.getValue().p(), null, true);
            m.fog.remove(f.getKey());
        }
    }

    public static void syncBossFog(Bosses.Unit u, String want, long now) {
        syncBossFog(u, want, now, false);
    }

    /** 싸움이 끝나면 이 싸움이 씌운 안개를 모두 걷음 */
    public static void clearBossFog(Bosses.Unit enc) {
        Bosses.Mem m = enc.mem;
        if (m == null || m.fog == null) return;
        for (Fog f : m.fog.values()) if (f.p() != null && !f.p().isRemoved()) setBossFog(f.p(), null, true);
        m.fog.clear();
        m.fogWant = null;
    }
}
