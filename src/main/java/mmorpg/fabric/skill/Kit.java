package mmorpg.fabric.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.Tasks;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * 스킬 · 룬 공통 도구 (skills.js · combat.js 의 대상 · 몸 · 블록 광선 · 옮기기 · 스킬 피해 · 몹 무적 시간 피하기 · 삶 · 낙하 피해 막기).
 * 애드온 코드를 거의 줄마다 옮길 수 있게 이름과 계산을 애드온과 같게 둠.
 */
public final class Kit {
    private Kit() {}

    public static final Obj SKILLS = Rules.get().obj("SKILLS");
    static final double CRIT_CAP = Rules.get().num("CRIT_CAP");
    static final Obj BOW = Rules.get().obj("BOW");
    /** 몹 무적 시간 (틱 — 자바도 맞은 뒤 10틱) */
    public static final int IFRAMES = 10;

    /** 결투 상대인지 (6단계 결투에서 정함 — 그 전에는 플레이어는 스킬에 맞지 않음) */
    public static BiPredicate<ServerPlayer, Player> duelPartner = (a, b) -> false;

    static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer sp) {
                LIVES.merge(sp.getUUID(), 1, Integer::sum);
                Guards.clear(sp.getUUID());
            }
        });
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> LIVES.merge(player.getUUID(), 1, Integer::sum));
        // 용익 강하 등: 떠올랐다 내려찍는 사람의 낙하 피해를 막음
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (NO_FALL.isEmpty() || !(entity instanceof ServerPlayer sp) || !source.is(DamageTypes.FALL)) return true;
            Long until = NO_FALL.get(sp.getUUID());
            return until == null || Tasks.now() > until;
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            NEXT_FREE.clear();
            NO_FALL.clear();
            LIVES.clear();
            HURT_TICKS.clear();
            SKILL_LANDED.clear();
        });
    }

    static void forget(UUID id) {
        String p = id + "|";
        NEXT_FREE.keySet().removeIf(k -> k.startsWith(p));
        NO_FALL.remove(id);
    }

    /** 오래된 기록 지우기 (5초마다) */
    static void prune() {
        long now = Tasks.now();
        NEXT_FREE.values().removeIf(t -> t < now - 40);
        NO_FALL.values().removeIf(t -> t < now);
        HURT_TICKS.values().removeIf(t -> t < now - 200);
        SKILL_LANDED.values().removeIf(t -> t < now - 200);
    }

    // ------------------------------------------------------------ 대상

    /**
     * 스킬이 맞는 대상인지 (skills.js canHit): 살아 있는 생명체 — 갑옷 거치대 · 마네킹 · 주민 · 떠돌이 상인 · 골렘 · 길들인 동물 · 타고 있는 것 ·
     * RPG NPC (rpg_npc) · 사라지는 보스 소품 (rpg_gone) · 다른 플레이어 (결투 상대는 맞음) 는 아님
     */
    public static boolean canHit(ServerPlayer sp, Entity e) {
        if (!(e instanceof LivingEntity le) || e == sp || e.isRemoved() || !le.isAlive() || le.getHealth() <= 0) return false;
        if (e instanceof ArmorStand || e instanceof Mannequin || e instanceof AbstractVillager || e instanceof IronGolem || e instanceof SnowGolem) return false;
        if (e instanceof Player p && (p.isSpectator() || (!SKILLS.truthy("hitPlayers") && !duelPartner.test(sp, p)))) return false;
        if (e.entityTags().contains("rpg_npc") || e.entityTags().contains("rpg_gone")) return false;
        if (sp.getVehicle() == e) return false;
        return !(e instanceof OwnableEntity o && o.getOwnerReference() != null);
    }

    /** 가운데에서 radius 안 (몸 가운데 기준, 몸 폭만큼 여유), 위아래 vertical 칸 안의 맞을 수 있는 대상 (skills.js around) */
    public static List<LivingEntity> around(ServerPlayer sp, Vec3 c, double radius, double vertical) {
        double r = radius + vertical + 4;
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : sp.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), x -> canHit(sp, x))) {
            Vec3 p = bodyAt(e, c.y);
            if (Math.hypot(p.x - c.x, p.z - c.z) <= radius + 0.5 + bodyPad(e) && Math.abs(p.y - c.y) <= vertical) out.add(e);
        }
        return out;
    }

    public static List<LivingEntity> around(ServerPlayer sp, Vec3 c, double radius) {
        return around(sp, c, radius, 3);
    }

    /** 조준한 대상, 없으면 시야 cone 도 안에서 가운데에 가장 가까운 대상 (벽 뒤 제외) — 없으면 null (skills.js pickTarget) */
    public static LivingEntity pickTarget(ServerPlayer sp, double range, double cone) {
        RpgCombat.Aim r = RpgCombat.aimTarget(sp, range, 0);
        if (r.target() != null && canHit(sp, r.target())) return r.target();
        RpgCombat.Aim a = RpgCombat.assist(sp, range, cone, x -> canHit(sp, x));
        return a == null ? null : a.target();
    }

    /** 조준한 곳 (대상 발밑 (시선 가까이의 몹 포함) → 막힌 블록 → 사거리 끝 — skills.js aimPoint) */
    public static Vec3 aimPoint(ServerPlayer sp, double range) {
        RpgCombat.Aim r = RpgCombat.aimTarget(sp, range, BOW.numOr("aimAssist", 0));
        if (r.target() != null) return r.target().position();
        return V.add(sp.getEyePosition(), sp.getViewVector(1F), r.dist());
    }

    /** 몸이 향한 수평 방향 (바로 위 · 아래를 보면 머리의 y 회전으로 — 0도 = 남쪽 +z) */
    public static Vec3 facingOf(Entity e) {
        Vec3 v = e.getViewVector(1F);
        if (Math.hypot(v.x, v.z) > 0.05) return V.flat(v);
        double yaw = Math.toRadians(e.getYRot());
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    /** 아직 그 무기를 들고 살아 있는지 (skills.js holding) */
    public static boolean holding(ServerPlayer sp, Weapon w) {
        if (sp.isRemoved() || !sp.isAlive() || sp.getHealth() <= 0) return false;
        Weapon held = RpgCombat.weaponOf(sp.getMainHandItem());
        return held != null && held.id().equals(w.id());
    }

    /** 체력이 0 이하 (쓰러지는 모습 중) */
    public static boolean hasNoHealth(Entity e) {
        return e instanceof LivingEntity le && le.getHealth() <= 0;
    }

    /** 쓰러졌는지 (체력 0 · 사라짐 · 보스 소품의 쓰러짐 표시) */
    public static boolean fallen(Entity e) {
        return e == null || e.isRemoved() || hasNoHealth(e) || e.entityTags().contains("rpg_gone");
    }

    /** 엔티티 종류 이름 (minecraft:zombie) */
    public static String typeId(Entity e) {
        return EntityType.getKey(e.getType()).toString();
    }

    /** 베드락 이름이 자바와 다른 몹 (애드온 목록의 이름 → 자바 이름) */
    private static final Map<String, String> BEDROCK_TYPES = Map.of(
            "minecraft:zombie_pigman", "minecraft:zombified_piglin",
            "minecraft:evocation_illager", "minecraft:evoker",
            "minecraft:villager_v2", "minecraft:villager",
            "minecraft:zombie_villager_v2", "minecraft:zombie_villager",
            "minecraft:ender_crystal", "minecraft:end_crystal",
            "minecraft:xp_orb", "minecraft:experience_orb");

    /** 애드온의 엔티티 이름 (베드락) 을 자바 이름으로 */
    public static String javaType(String bedrockId) {
        return BEDROCK_TYPES.getOrDefault(bedrockId, bedrockId);
    }

    /** 이 엔티티가 애드온 이름 (베드락) 의 종류인지 */
    public static boolean isType(Entity e, String bedrockId) {
        return typeId(e).equals(javaType(bedrockId));
    }

    /**
     * 베드락 무리 (type_family): monster = 적대 몹 (Enemy), dragon = 엔더 드래곤, undead = 언데드,
     * rpg_monster · rpg_boss · rpg_boss_prop · rpg_dummy 등 RPG 몹은 엔티티 태그 (5단계 몬스터 · 보스가 붙임)
     */
    public static boolean hasFamily(Entity e, String family) {
        return switch (family) {
            case "monster" -> e instanceof Enemy || e.entityTags().contains("monster");
            case "dragon" -> e instanceof EnderDragon;
            case "undead" -> e.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD);
            case "player" -> e instanceof Player;
            default -> e.entityTags().contains(family);
        };
    }

    /** 무엇이든 타고 있는지 (말 · 배 · 탈것) */
    public static boolean ridingAny(ServerPlayer sp) {
        return sp.getVehicle() != null;
    }

    // ------------------------------------------------------------ 몸

    public static Vec3 bodyPoint(Entity e) {
        return RpgCombat.bodyPoint(e);
    }

    public static double bodyPad(Entity e) {
        return RpgCombat.bodyPad(e);
    }

    public static double[] bodySpan(Entity e) {
        return RpgCombat.bodySpan(e);
    }

    /** 높이 y 에 가장 가까운 몸 위의 점 */
    public static Vec3 bodyAt(Entity e, double y) {
        double[] s = bodySpan(e);
        return new Vec3(e.getX(), Math.max(s[0], Math.min(s[1], y)), e.getZ());
    }

    /** 몸 가운데 (사라졌으면 fallback) */
    public static Vec3 bodyAt0(Entity e, Vec3 fallback) {
        return e != null && !e.isRemoved() ? bodyPoint(e) : fallback;
    }

    public static Vec3 bodyToward(Entity e, Vec3 from, Vec3 d) {
        return RpgCombat.bodyToward(e, from, d);
    }

    /** c 에서 수평 단위 방향 f 쪽 반각 half (라디안) 부채꼴에 몸이 걸치는지 (near 칸 안은 방향 무관, 큰 몸은 몸 폭만큼 넓게) */
    public static boolean bodyInCone(Entity e, Vec3 c, Vec3 f, double half, double near) {
        double dx = e.getX() - c.x;
        double dz = e.getZ() - c.z;
        double dist = Math.hypot(dx, dz);
        double pad = bodyPad(e);
        if (dist <= near + pad) return true;
        double cos = (dx * f.x + dz * f.z) / dist;
        return Math.acos(Math.max(-1, Math.min(1, cos))) <= half + Math.asin(Math.min(1, pad / dist));
    }

    public static boolean bodyInCone(Entity e, Vec3 c, Vec3 f, double half) {
        return bodyInCone(e, c, f, half, 1.2);
    }

    /** 눈 (eye) 에서 보이는 적만 */
    public static List<LivingEntity> visibleFrom(ServerPlayer sp, Vec3 eye, List<LivingEntity> list) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : list) if (lineOfSight(sp, eye, bodyAt(e, eye.y))) out.add(e);
        return out;
    }

    /** 적 목록 합치기 (같은 적은 한 번, 처음 나온 순서) */
    @SafeVarargs
    public static List<LivingEntity> union(List<LivingEntity>... lists) {
        Map<UUID, LivingEntity> seen = new LinkedHashMap<>();
        for (List<LivingEntity> l : lists) for (LivingEntity e : l) seen.putIfAbsent(e.getUUID(), e);
        return new ArrayList<>(seen.values());
    }

    /** 선 위의 적: e, 선 위 거리 t */
    public record LineHit(LivingEntity e, double t) {}

    /** from 에서 단위 방향 dir 로 L 칸, 선에서 폭 width 칸 안의 적 (가까운 순). 눈 (from) 에서 그 몸까지 벽이 막으면 빼기 (skills.js lineHits) */
    public static List<LineHit> lineHits(ServerPlayer sp, Vec3 from, Vec3 dir, double len, double width) {
        List<LineHit> out = new ArrayList<>();
        for (LivingEntity e : around(sp, V.add(from, dir, len / 2), len / 2 + width + 0.5, len / 2 + 3)) {
            double pad = bodyPad(e);
            Vec3 q = bodyToward(e, from, dir);
            Vec3 p = V.sub(q, from);
            double t = V.dot(p, dir);
            if (t < -0.3 - pad || t > len + 0.5 + pad) continue;
            if (V.len(V.sub(p, V.scale(dir, t))) > width + pad) continue;
            if (!lineOfSight(sp, from, q)) continue;
            out.add(new LineHit(e, Math.max(0, t)));
        }
        out.sort(Comparator.comparingDouble(LineHit::t));
        return out;
    }

    // ------------------------------------------------------------ 블록 광선 (베드락 getBlockFromRay: 풀 · 꽃처럼 지나갈 수 있는 블록은 통과)

    /** 광선이 맞은 블록: 칸, 맞은 점, 블록 */
    public record Hit(BlockPos pos, Vec3 point, BlockState state) {}

    /** from 에서 단위 방향 dir 로 max 칸까지 처음 맞는 막는 블록 (liquids 면 물 · 용암도 맞음), 없으면 null */
    public static Hit ray(Level level, Vec3 from, Vec3 dir, double max, boolean liquids) {
        if (!(max > 0)) return null;
        Vec3 to = V.add(from, dir, max);
        BlockHitResult r = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, liquids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        if (r.getType() == HitResult.Type.MISS) return null;
        return new Hit(r.getBlockPos(), r.getLocation(), level.getBlockState(r.getBlockPos()));
    }

    /** 블록 이름 (minecraft: 뺀 것 — 애드온의 typeId 정규식 확인용) */
    public static String blockId(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
    }

    /** 물 · 용암 같은 액체 칸 (부딪히는 모양 없이 액체만 — 물에 잠긴 반 블록은 아님) */
    public static boolean liquidAt(Level level, Hit h) {
        return !level.getFluidState(h.pos()).isEmpty() && h.state().getCollisionShape(level, h.pos()).isEmpty();
    }

    /** 서면 다치는 블록 (땅 · 발 · 머리 칸): 용암 · 불 · 모닥불 · 마그마 · 선인장 · 가시덤불 · 위더 장미 · 가루눈 */
    public static final Pattern HAZARD = Pattern.compile("lava|fire|campfire|magma|cactus|sweet_berry|wither_rose|powder_snow");

    public static boolean hazard(BlockState s) {
        return HAZARD.matcher(blockId(s)).find();
    }

    /**
     * 순간이동해서 설 자리 (combat.js standSpot): at 의 수직선에서 천장을 찾고, at 보다 1.6칸 위 (천장이 낮으면 그 바로 아래) 부터 아래로 내려가
     * 처음 닿는 블록이 단단한 땅이어야 하고 (물 · 용암 · 마그마 · 선인장 등 아님, 반 블록도 됨), at 높이와 maxDy 칸 안, 땅 위 1.75칸이 비어 있고
     * 발 · 머리 칸에 불 · 가시덤불 같은 것이 없어야 함. 아니면 null
     */
    public static Vec3 standSpot(Level level, Vec3 at, double maxDy) {
        Hit ceilHit = ray(level, new Vec3(at.x, at.y + 0.1, at.z), V.UP, 1.5, false);
        double ceil = ceilHit != null ? ceilHit.point().y : Double.POSITIVE_INFINITY;
        double top = Math.max(at.y + 0.1, Math.min(at.y + 1.6, ceil - 0.05));
        Hit down = ray(level, new Vec3(at.x, top, at.z), V.DOWN, top - at.y + maxDy + 0.2, true);
        if (down == null) return null;
        if (liquidAt(level, down) || blockId(down.state()).contains("water") || hazard(down.state())) return null;
        double fy = down.point().y - down.pos().getY();
        double y = down.pos().getY() + (fy > 0.05 && fy <= 1 ? fy : 1);
        if (Math.abs(y - at.y) > maxDy) return null;
        Vec3 p = new Vec3(at.x, y, at.z);
        if (ray(level, new Vec3(p.x, p.y + 0.05, p.z), V.UP, 1.75, true) != null) return null;
        for (double dy : new double[] {0.1, 1.1}) {
            if (hazard(level.getBlockState(BlockPos.containing(p.x, p.y + dy, p.z)))) return null;
        }
        return p;
    }

    public static Vec3 standSpot(Level level, Vec3 at) {
        return standSpot(level, at, 1.6);
    }

    /** from 아래로 maxDown 칸 안의 첫 블록이 단단하면 그 윗면 높이 (블록 칸 + 1), 물 · 용암 · 땅 없음 = null */
    public static Double solidBelow(Level level, Vec3 from, double maxDown) {
        Hit h = ray(level, from, V.DOWN, maxDown, true);
        if (h == null || liquidAt(level, h)) return null;
        String id = blockId(h.state());
        if (id.contains("water") || id.contains("lava")) return null;
        return (double) h.pos().getY() + 1;
    }

    /** a → b 사이에 막는 블록이 없는지 (끝 0.3칸 여유) */
    public static boolean clearPath(Level level, Vec3 a, Vec3 b) {
        Vec3 d = V.sub(b, a);
        double len = V.len(d);
        if (len < 0.3) return true;
        Hit h = ray(level, a, V.unit(d), len, false);
        return h == null || V.len(V.sub(h.point(), a)) >= len - 0.3;
    }

    /** from 에서 단위 방향 dir 로 max 칸까지 막힘 없이 가는 거리 (블록 면까지) */
    public static double rayDist(Level level, Vec3 from, Vec3 dir, double max) {
        Hit h = ray(level, from, dir, max, false);
        return h == null ? max : Math.max(0, Math.min(max, V.len(V.sub(h.point(), from))));
    }

    /**
     * 발 s 에서 수평 f 로 max 칸까지 막힘 없이 갈 수 있는 거리 (벽 앞 0.6칸). 1.1 · 1.8 높이 광선 — 한 칸 턱은 넘고, 두 칸 높이 굴에서도 됨
     */
    public static double reachFlat(Level level, Vec3 s, Vec3 f, double max) {
        double dist = max;
        for (double h : new double[] {1.1, 1.8}) {
            Hit hit = ray(level, V.up(s, h), f, max + 0.8, false);
            if (hit != null) dist = Math.min(dist, Math.hypot(hit.point().x - s.x, hit.point().z - s.z) - 0.6);
        }
        return Math.max(0, dist);
    }

    /** 돌진 길의 설 자리: 출발점에서 거리 s, 자리 p */
    public record Spot(double s, Vec3 p) {}

    /**
     * 돌진 길의 설 자리 (0.5칸마다와 끝, 가까운 순): standSpot 이 한 칸 안의 오르내림으로 이어지는 데까지 —
     * 낭떠러지 (1.1칸 넘게 낮음) · 두 칸 턱 · 물 · 용암 · 낮은 천장 앞에서 끊김 (skills.js dashSpots)
     */
    public static List<Spot> dashSpots(Level level, Vec3 from, Vec3 f, double max) {
        List<Double> marks = new ArrayList<>();
        for (double s = 0.5; s < max - 0.05; s += 0.5) marks.add(s);
        if (max > 0.2) marks.add(max);
        List<Spot> out = new ArrayList<>();
        double y = from.y;
        for (double s : marks) {
            Vec3 q = V.add(from, f, s);
            Vec3 p = standSpot(level, new Vec3(q.x, y + 1.1, q.z), 2.2);
            if (p == null || p.y > y + 1.05 || p.y < y - 1.1) break;
            y = p.y;
            out.add(new Spot(s, p));
        }
        return out;
    }

    /** at 위로 트인 높이 (최대 max 칸) — throughLeaves 면 나뭇잎은 지나침 (skills.js skyRoom) */
    public static double skyRoom(Level level, Vec3 at, double max, boolean throughLeaves) {
        double y = at.y;
        for (int i = 0; i < 5; i++) {
            double left = max - (y - at.y);
            if (left <= 0) return max;
            Hit h = ray(level, new Vec3(at.x, y, at.z), V.UP, left, false);
            if (h == null) return max;
            if (!throughLeaves || !blockId(h.state()).contains("leaves")) return Math.max(0, h.point().y - at.y);
            y = h.pos().getY() + 1.01;
        }
        return Math.max(0, y - at.y);
    }

    /** from 에서 to 까지 막는 블록이 없는지 (풀 · 꽃 · 물은 통과) */
    public static boolean lineOfSight(ServerPlayer sp, Vec3 from, Vec3 to) {
        return RpgCombat.lineOfSight(sp, from, to);
    }

    // ------------------------------------------------------------ 옮기기 · 밀기

    /**
     * 옮기기 (skills.js moveTo — tryTeleport, keepVelocity false): 그 자리에서 몸이 블록과 겹치면 옮기지 않고 false.
     * 플레이어는 보는 방향을 그대로 둠 (화면이 튀지 않게)
     */
    public static boolean moveTo(Entity e, Vec3 loc) {
        if (e.isRemoved() || !(e.level() instanceof ServerLevel level)) return false;
        AABB box = e.getBoundingBox().move(loc.x - e.getX(), loc.y - e.getY(), loc.z - e.getZ());
        if (!level.noCollision(e, box)) return false;
        if (e instanceof ServerPlayer sp) {
            return sp.teleportTo(level, loc.x, loc.y, loc.z, Relative.ROTATION, 0F, 0F, false);
        }
        e.teleportTo(loc.x, loc.y, loc.z);
        e.setDeltaMovement(Vec3.ZERO);
        return true;
    }

    /** 옮기면서 보는 방향도 정함 (yRot · xRot 도 — 베드락 teleport 의 rotation / facingLocation). 막혔으면 false */
    public static boolean moveTo(Entity e, Vec3 loc, float yRot, float xRot) {
        if (e.isRemoved() || !(e.level() instanceof ServerLevel level)) return false;
        AABB box = e.getBoundingBox().move(loc.x - e.getX(), loc.y - e.getY(), loc.z - e.getZ());
        if (!level.noCollision(e, box)) return false;
        boolean ok = e.teleportTo(level, loc.x, loc.y, loc.z, java.util.Set.of(), yRot, xRot, false);
        e.setYHeadRot(yRot);
        if (!(e instanceof ServerPlayer)) e.setDeltaMovement(Vec3.ZERO);
        return ok;
    }

    /** from 에서 to 를 보는 회전 {yRot, xRot} (자바: yRot 0 = 남쪽 +z, xRot + = 아래) */
    public static float[] lookRotation(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double h = Math.hypot(dx, dz);
        float yRot = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float xRot = (float) -Math.toDegrees(Math.atan2(dy, h));
        return new float[] {yRot, xRot};
    }

    /**
     * 밀기 (베드락 applyKnockback({x, z}, 세로)): 속도를 (hx, vy, hz) 로 — 밀치기 저항만큼 줄임. 플레이어도 (속도를 그 사람에게 보냄)
     */
    public static void knockback(Entity e, double hx, double hz, double vy) {
        double res = e instanceof LivingEntity le ? le.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) : 0;
        double k = Math.max(0, 1 - res);
        e.setDeltaMovement(hx * k, vy * k, hz * k);
        syncVelocity(e);
    }

    /** 속도를 그대로 정함 (스스로 떠오르기 등 — 밀치기 저항 무시) */
    public static void launch(Entity e, double vx, double vy, double vz) {
        e.setDeltaMovement(vx, vy, vz);
        syncVelocity(e);
    }

    /**
     * 서버에서 바꾼 속도를 보냄. 플레이어는 클라이언트가 몸을 움직이므로 그 사람에게 속도 패킷을 바로 보냄 (바닐라 Player.attack 의 넉백과 같은 길) —
     * 26.3 의 needsSync 만으로는 그 사람의 클라이언트에 가지 않아 보스 · 스킬의 밀치기 · 띄우기 · 끌어당기기가 화면에서 일어나지 않았음
     * (크라켄 소용돌이 · 그람 용익 강하의 솟구침 — 사진첩으로 확인). 다른 엔티티는 서버가 움직이고 위치를 보냄 (needsSync)
     */
    public static void syncVelocity(Entity e) {
        e.needsSync = true;
        if (e instanceof ServerPlayer sp && sp.connection != null && !sp.isRemoved()) sp.connection.send(new ClientboundSetEntityMotionPacket(sp));
    }

    // ------------------------------------------------------------ 스킬 피해 (combat.js applySkillDamage · skills.js skillHit)

    /** 피해 종류: 공격 (방어구 적용), 마법 (방어 무시), 가시 (반사) */
    public enum Cause {
        ATTACK,
        MAGIC,
        THORNS
    }

    static DamageSource source(ServerPlayer sp, Cause c) {
        return switch (c) {
            case MAGIC -> sp.damageSources().indirectMagic(sp, sp);
            case THORNS -> sp.damageSources().thorns(sp);
            default -> sp.damageSources().playerAttack(sp);
        };
    }

    /** "대상|공격자" → 스킬 · 룬 피해가 실제로 들어간 틱 */
    private static final Map<String, Long> SKILL_LANDED = new HashMap<>();
    /** 엔티티 → 마지막으로 피해를 받은 틱 (누가 때렸든) */
    private static final Map<UUID, Long> HURT_TICKS = new HashMap<>();

    private static String key(Entity a, Entity b) {
        return a.getUUID() + "|" + b.getUUID();
    }

    /** 생명체가 피해를 받음 (LivingEntity#actuallyHurt — 몹 무적 시간을 피하려고 기억) */
    public static void noteHurt(LivingEntity e) {
        HURT_TICKS.put(e.getUUID(), Tasks.now());
    }

    /**
     * 스킬 피해 한 번 (combat.js applySkillDamage): 평타 공식을 거치지 않고 그대로, 피해 숫자는 style (null 이면 치명타 여부로 스킬 색).
     * 자바의 기본 밀침은 없앰 (애드온의 applyDamage 처럼 — 밀치는 스킬은 직접 밈). 실제로 들어갔는지 (무적 시간 · 면역이면 false)
     */
    public static boolean applySkillDamage(ServerPlayer sp, LivingEntity target, double amount, boolean crit, Cause cause, DamageNumbers.Style style) {
        if (sp.isRemoved() || target == null || target.isRemoved() || !(amount > 0)) return false;
        if (!(target.level() instanceof ServerLevel tl)) return false;
        RpgCombat.skillMark(target, sp, crit, style != null ? style : crit ? DamageNumbers.Style.SKILLCRIT : DamageNumbers.Style.SKILL);
        Vec3 v = target.getDeltaMovement();
        boolean hurt = target.hurtServer(tl, source(sp, cause), (float) amount);
        if (!hurt) {
            RpgCombat.dropMark(target, sp);
            return false;
        }
        if (!target.isRemoved()) {
            target.setDeltaMovement(v);
            syncVelocity(target);
        }
        SKILL_LANDED.put(key(target, sp), Tasks.now());
        return true;
    }

    /** 이 공격자의 스킬 · 룬 피해가 target 에 마지막으로 들어간 틱 (없으면 -1000) */
    public static long skillLandedAt(Entity target, Entity attacker) {
        return SKILL_LANDED.getOrDefault(key(target, attacker), -1000L);
    }

    /** target 이 마지막으로 피해를 받은 틱 (누가 때렸든, 이 공격자의 스킬 · 룬 피해 포함 — 없으면 -1000) */
    public static long lastHurtTick(Entity target, Entity attacker) {
        long h = HURT_TICKS.getOrDefault(target.getUUID(), -1000L);
        return attacker != null ? Math.max(h, skillLandedAt(target, attacker)) : h;
    }

    /**
     * 스킬 피해 한 번 (skills.js skillHit): 무기 공격력 × mult × 스탯 배율 × 치명타. 실제로 깎인 체력 (0 = 안 들어감).
     * o: critBonus · sure (늘 치명타) · cause (Cause — 없으면 완드는 마법, 나머지는 공격) · spell (마검의 마력: 파란 숫자) · style (숫자 모양)
     */
    public static double skillHit(ServerPlayer sp, LivingEntity target, Weapon w, double mult, Opt o) {
        if (!canHit(sp, target)) return 0;
        boolean crit = o.truthy("sure")
                || (RpgCombat.forceCrit != null ? RpgCombat.forceCrit
                        : ThreadLocalRandom.current().nextDouble() < Math.min(CRIT_CAP, w.critChance() + o.num("critBonus", 0)));
        double dmg = w.damage() * mult * RpgCombat.powerOf(sp, w) * (crit ? w.critMult() : 1);
        Cause cause = o.get("cause") instanceof Cause c ? c : w.type().equals("wand") ? Cause.MAGIC : Cause.ATTACK;
        DamageNumbers.Style style = o.get("style") instanceof DamageNumbers.Style s ? s
                : o.truthy("spell") ? (crit ? DamageNumbers.Style.SPELLCRIT : DamageNumbers.Style.SPELL) : null;
        float before = target.getHealth();
        if (!applySkillDamage(sp, target, dmg, crit, cause, style)) return 0;
        float after = target.isRemoved() ? 0 : target.getHealth();
        return before > after ? before - after : dmg;
    }

    public static double skillHit(ServerPlayer sp, LivingEntity target, Weapon w, double mult) {
        return skillHit(sp, target, w, mult, Opt.empty());
    }

    /** 스킬 한 번의 예상 피해 (치명타 없이 — 설명용) */
    public static double skillDamage(ServerPlayer sp, Weapon w, double mult) {
        return w.damage() * mult * RpgCombat.powerOf(sp, w);
    }

    // ------------------------------------------------------------ 몹 무적 시간 피하기 (skills.js reserveHit · hitAt)

    /** "플레이어|적" → 이 플레이어의 타격이 그 적에게 다음으로 들어갈 수 있는 틱 */
    private static final Map<String, Long> NEXT_FREE = new HashMap<>();

    /**
     * 몹 무적 시간 (10틱) 을 피해 넣을 틱을 잡아 둠: want, 그 적이 마지막으로 피해를 받은 틱 + 10, 이 플레이어가 그 적에게 먼저 잡아 둔 타격 + 10 중 늦은 것
     */
    public static long reserveHit(ServerPlayer sp, Entity e, long want) {
        String k = key(sp, e);
        long at = Math.max(want, Math.max(lastHurtTick(e, sp) + IFRAMES, NEXT_FREE.getOrDefault(k, -1000L)));
        NEXT_FREE.put(k, at + IFRAMES);
        return at;
    }

    /**
     * at 틱에 fn (잡아 둔 틱). 그때 그 적이 다른 피해로 무적 시간이면 끝날 때까지 미룸 (두 번까지).
     * ok 가 거짓이면 (쓰러짐 · 다른 삶 · 무기 바꿈 등) 취소, 맞을 수 없는 적이면 취소
     */
    public static void hitAt(ServerPlayer sp, LivingEntity e, long at, BooleanSupplier ok, Runnable fn) {
        hitAt(sp, e, at, ok, fn, 2);
    }

    private static void hitAt(ServerPlayer sp, LivingEntity e, long at, BooleanSupplier ok, Runnable fn, int retry) {
        Runnable run = () -> {
            if (!ok.getAsBoolean() || !canHit(sp, e)) return;
            long now = Tasks.now();
            // 기억한 피해 틱 + 10 과 지금 남은 무적 시간 (자바: damageCooldownTime 이 10 넘으면 무적) 중 늦은 것
            long wait = Math.max(lastHurtTick(e, sp) + IFRAMES - now, e.damageCooldownTime - 10);
            if (wait > 0 && retry > 0) {
                NEXT_FREE.merge(key(sp, e), now + wait + IFRAMES, Math::max);
                hitAt(sp, e, now + wait, ok, fn, retry - 1);
                return;
            }
            fn.run();
        };
        long d = at - Tasks.now();
        if (d <= 0) run.run();
        else Tasks.later((int) d, run);
    }

    // ------------------------------------------------------------ 매 틱 되풀이 (skills.js 의 step(0) + system.runInterval(() => step(++t), 1))

    /** 매 틱 step(t) (t = 0, 1, 2, …): 첫 번은 바로, step 이 false 를 돌려주면 멈춤 */
    public static void ticks(java.util.function.IntPredicate step) {
        if (step.test(0)) ticksFrom(step, 1);
    }

    private static void ticksFrom(java.util.function.IntPredicate step, int t) {
        Tasks.later(1, () -> {
            if (step.test(t)) ticksFrom(step, t + 1);
        });
    }

    // ------------------------------------------------------------ 삶 (죽거나 차원을 옮기면 바뀜 — 나중에 나가는 타격을 멈춤)

    private static final Map<UUID, Integer> LIVES = new HashMap<>();

    public static String lifeOf(ServerPlayer sp) {
        return sp.level().dimension().identifier() + "|" + LIVES.getOrDefault(sp.getUUID(), 0);
    }

    /** 쓸 때와 같은 삶인지 (살아 있고, 그 사이 죽거나 차원을 옮기지 않음) */
    public static boolean sameLife(ServerPlayer sp, String token) {
        return !sp.isRemoved() && sp.isAlive() && !sp.isDeadOrDying() && lifeOf(sp).equals(token);
    }

    // ------------------------------------------------------------ 낙하 피해 막기 · 화면 흔들림

    private static final Map<UUID, Long> NO_FALL = new HashMap<>();

    /** ticks 틱 동안 낙하 피해 없음 (떨어진 거리도 지움) */
    public static void noFall(ServerPlayer sp, int ticks) {
        NO_FALL.merge(sp.getUUID(), Tasks.now() + ticks, Math::max);
        sp.resetFallDistance();
    }

    /** 가까운 플레이어들 화면 흔들기 */
    public static void shakeNear(ServerLevel level, Vec3 at, double radius, double intensity, double seconds) {
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceTo(at) <= radius) SkillFx.shake(p, intensity, seconds);
        }
    }

    // ------------------------------------------------------------ 색 · 체력

    /** 무기 종류의 스킬 색 {빛, 심지} (config.js SKILLS.color) */
    public static double[][] colors(Weapon w) {
        Obj c = SKILLS.obj("color");
        List<Object> pair = c.listOrNull(w.type());
        if (pair == null) return new double[][] {{1, 1, 1}, {1, 1, 1}};
        return new double[][] {rgb(pair.get(0)), rgb(pair.get(1))};
    }

    /** JSON 의 [r, g, b] → double[] */
    public static double[] rgb(Object o) {
        if (o instanceof List<?> l && l.size() >= 3) {
            return new double[] {((Number) l.get(0)).doubleValue(), ((Number) l.get(1)).doubleValue(), ((Number) l.get(2)).doubleValue()};
        }
        return new double[] {1, 1, 1};
    }

    /** 초월 기운 색 (참격 안쪽 — SLASH.gradeAura[4]) */
    public static double[] aura() {
        List<Object> a = Rules.get().obj("SLASH").listOrNull("gradeAura");
        return a != null && a.size() > 4 && a.get(4) != null ? rgb(a.get(4)) : new double[] {1.0, 0.3, 0.35};
    }

    /** 체력 대가 (망자의 각성): 최대 체력 × cost (소수 첫째 자리) */
    public static double curseCost(ServerPlayer sp, Obj k) {
        return Math.round(RpgHealth.maxOf(sp) * k.numOr("cost", 0) * 10) / 10.0;
    }

    /** 체력 대가를 낼 수 있는지 (RPG 체력이 아니면 늘 됨 — 체력이 1 아래로는 안 내려감) */
    public static boolean canPayCurse(ServerPlayer sp, Obj k) {
        return !RpgHealth.isRpgMode(sp) || sp.getHealth() - curseCost(sp, k) >= 1 - 1e-6;
    }

    /** 0 ~ 1 무작위 */
    public static double rand() {
        return ThreadLocalRandom.current().nextDouble();
    }
}
