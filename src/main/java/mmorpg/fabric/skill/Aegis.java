package mmorpg.fabric.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Tasks;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 아이기스의 방패 (탱커) — 고르곤 돌진 · 신의 방벽 (skills.js v2.20.0 "고르곤 · 신의 방벽"). 수치는 SKILLS.shield.
 * 세기 "영웅 룬~초월 룬" (Lv.40 체력 125 의 체력 룬 위력 기준) · 빛의 방패 그림은 모델 앞모습 (rpg:aegis_shield)
 */
final class Aegis {
    private Aegis() {}

    static final double[] AE_EYE = {0.55, 1.0, 0.9}; // 메두사의 눈 (청록빛)
    static final double[] AE_WHITE = {1.0, 0.97, 0.84};
    static final double[] AE_STONE = {0.74, 0.73, 0.7}; // 석화 돌 조각 (그림이 회색 — 거의 그대로)
    static final double[] AE_DUST = {0.62, 0.6, 0.56};
    /** 빛의 방패 그림에서 메두사의 두 눈 {x, y} (그림 가운데 기준, 반 크기 = 1 — build.py aegis_eyes: 256점 그림의 (116, 116) · (140, 116)) */
    static final double[][] AE_EYES = {{-12 / 128.0, 12 / 128.0}, {12 / 128.0, 12 / 128.0}};
    /** 고르곤 돌진의 빛의 방패 반 크기 (칸) — 지름 1.6칸 */
    static final double AE_SHIELD = 0.8;
    /** 빛의 방패 가운데 높이 (발에서) — 위끝 1.52칸: 눈 (1.62) 보다 조금 아래 (1인칭에서 앞을 가리지 않게) */
    static final double AE_SHIELD_Y = 0.72;
    /** rpg:shock_ring 이 수명 동안 커지는 배수 (build.py 의 grow 1.6: 크기 × (1 + 1.6 × 진행) — 그림의 고리는 반 크기의 약 0.8) */
    static final double RING_GROW = 2.6;

    static void register() {
        Transcend.register("aegis_charge", Aegis::charge);
        Transcend.register("aegis_dome", Aegis::dome);
        // 신의 방벽: 돔 안에서 맞으면 육각 칸이 번쩍 (피해는 Guards 의 감소 버프가 줄임) — 플레이어가 맞을 때만, 방패로 다 막은 것은 아님
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
            if (DOMES.isEmpty() || !(entity instanceof ServerPlayer sp) || !(damageTaken > 0)) return;
            Dome d = DOMES.get(sp.getUUID());
            if (d == null) return;
            if (Tasks.now() >= d.until()) {
                DOMES.remove(sp.getUUID());
                return;
            }
            if (!d.inside().test(sp)) return;
            Entity from = source.getEntity();
            Tasks.later(1, () -> domeBlocked(sp, from));
        });
        // 바닐라 몹 도발: 매 틱 다시 노리게
        ServerTickEvents.END_SERVER_TICK.register(server -> tauntTick());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            DOMES.clear();
            TAUNTED.clear();
        });
    }

    /** 나감: 이 사람의 신의 방벽 · 이 사람이 건 바닐라 몹 도발을 지움 */
    static void forget(UUID id) {
        DOMES.remove(id);
        TAUNTED.values().removeIf(t -> t.sp().getUUID().equals(id));
    }

    // ------------------------------------------------------------ 도발

    /** 도발 (몬스터 · 보스가 넣음): (플레이어, 적, 틱) → 걸었는지 */
    @FunctionalInterface
    public interface TauntHook {
        boolean taunt(ServerPlayer sp, LivingEntity e, int ticks);
    }

    /** 도발 (v2.20.0): 몬스터 (monsters.js) · 보스 (bosses.js) 가 넣음 — 비어 있으면 바닐라 몹만 */
    public static final List<TauntHook> TAUNT_HOOKS = new ArrayList<>();

    public static void addTauntHook(TauntHook fn) {
        if (fn != null) TAUNT_HOOKS.add(fn);
    }

    /** 바닐라 몹 도발: 몹, 도발한 사람, 끝나는 틱 */
    private record Taunted(Mob m, ServerPlayer sp, long until) {}

    /** 몹 → 바닐라 몹 도발 (나중에 건 도발이 이김 — 몬스터의 도발과 같게) */
    private static final Map<UUID, Taunted> TAUNTED = new HashMap<>();

    /**
     * 그 적이 ticks 동안 이 플레이어를 노리게 함. 몬스터 · 보스는 스크립트 (TAUNT_HOOKS) 가, 바닐라 몹 (Mob) 은 대상을 이 사람으로 —
     * ticks 동안 매 틱 다시 (다른 사람이 때려도 바뀌지 않게). 스크립트가 걸었으면 true (바닐라 몹은 false — 머리 위 표시 규칙이 애드온과 같게)
     */
    static boolean taunt(ServerPlayer sp, LivingEntity e, int ticks) {
        for (TauntHook h : TAUNT_HOOKS) {
            try {
                if (h.taunt(sp, e, ticks)) {
                    TAUNTED.remove(e.getUUID());
                    return true;
                }
            } catch (RuntimeException err) {
                Mmorpg.LOG.warn("[mmorpg] taunt", err);
            }
        }
        if (e instanceof Mob m) tauntMob(m, sp, ticks);
        return false;
    }

    /** 몹이 그 사람을 노릴 수 있는지 (둘 다 살아 있고 같은 차원, 크리에이티브 · 관전이 아님) */
    private static boolean canTaunt(Mob m, ServerPlayer sp) {
        return !m.isRemoved() && m.isAlive() && !sp.isRemoved() && sp.isAlive() && m.level() == sp.level() && sp.canBeSeenAsEnemy();
    }

    private static void tauntMob(Mob m, ServerPlayer sp, int ticks) {
        if (!canTaunt(m, sp)) return;
        m.setTarget(sp);
        TAUNTED.put(m.getUUID(), new Taunted(m, sp, Tasks.now() + ticks));
    }

    /** 매 틱: 도발이 끝났거나 · 몹이나 사람이 쓰러졌거나 · 다른 차원이면 놓아 줌, 아니면 대상을 그 사람으로 되돌림 */
    private static void tauntTick() {
        if (TAUNTED.isEmpty()) return;
        long now = Tasks.now();
        for (Iterator<Taunted> it = TAUNTED.values().iterator(); it.hasNext(); ) {
            Taunted t = it.next();
            if (now >= t.until() || !canTaunt(t.m(), t.sp())) {
                it.remove();
                continue;
            }
            if (t.m().getTarget() != t.sp()) t.m().setTarget(t.sp());
        }
    }

    /** 보스 · 보스 소품 (석화 · 둔화 · 밀기가 듣지 않음 — 보스 기믹을 깨지 않게) */
    static boolean bossLike(Entity e) {
        return Kit.hasFamily(e, "rpg_boss") || Kit.hasFamily(e, "rpg_boss_prop");
    }

    /** 도발 표시 (머리 위 "도발") — 몬스터 · 보스 (스크립트가 건 도발) 와 바닐라 적대 몹 */
    static void tauntMark(ServerLevel level, LivingEntity e, boolean scripted) {
        if (!scripted && !Kit.hasFamily(e, "monster")) return;
        if (e.isRemoved()) return;
        DamageNumbers.showAt(level, V.up(e.getEyePosition(), 1.25), "§6§l도발"); // 피해 숫자 (머리 위 0.3칸 근처) 보다 위
    }

    /** 석화: ticks 동안 움직이지 못하고 (구속 XI) 근접 공격이 거의 들어가지 않음 (나약함 V) — 룬 기절과 같음 (이 애드온 몬스터는 공격하지 못함) */
    static void petrify(LivingEntity e, int ticks) {
        e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 10, false, false));
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 4, false, false));
    }

    // ------------------------------------------------------------ 끌기 (skills.js draggable — 신창 귀환과 같음)

    /** 끌지 않는 아주 큰 몹 (RUNE_GRAVITY.heavy — 자바 이름) */
    private static final Set<String> HEAVY_MOB = javaTypes(Rules.get().obj("RUNE_GRAVITY").listOrNull("heavy"));
    /** 끌지 않는 바닐라 몹 (몸이 넓거나 무거움 — 몸 크기를 스크립트가 모름) */
    private static final Set<String> DRAG_SKIP = javaTypes(List.of("minecraft:ravager", "minecraft:sniffer", "minecraft:camel", "minecraft:hoglin",
            "minecraft:zoglin", "minecraft:polar_bear", "minecraft:horse", "minecraft:donkey", "minecraft:mule", "minecraft:skeleton_horse",
            "minecraft:zombie_horse", "minecraft:llama", "minecraft:trader_llama", "minecraft:panda", "minecraft:strider", "minecraft:breeze"));

    private static Set<String> javaTypes(List<?> ids) {
        Set<String> out = new HashSet<>();
        if (ids != null) for (Object id : ids) if (id instanceof String s) out.add(Kit.javaType(s));
        return out;
    }

    /**
     * 끌고 갈 수 있는 몹: 살아 있는 보통 몹 — 플레이어 · 보스 · 보스 소품 · 허수아비 · 아주 큰 몹 (엔더 드래곤 등) ·
     * 넓거나 키 큰 몹 (엔더맨 · 트롤 등) 은 아님 (피해만)
     */
    static boolean draggable(Entity e) {
        if (e == null || e.isRemoved() || Kit.hasNoHealth(e) || e instanceof Player) return false;
        String t = Kit.typeId(e);
        if (HEAVY_MOB.contains(t) || DRAG_SKIP.contains(t) || Kit.bodyPad(e) > 0.6) return false;
        if (e.getEyeY() - e.getY() > 2.3) return false; // 키 큰 몸 (손 높이로 끌면 발이 땅에 박혀 옮겨지지 않음)
        return !(Kit.hasFamily(e, "rpg_boss") || Kit.hasFamily(e, "rpg_boss_prop") || Kit.hasFamily(e, "rpg_dummy"));
    }

    // ------------------------------------------------------------ 그림 도구

    /**
     * 빛의 방패 한 장 (rpg:aegis_shield — 보는 사람을 향해 섬): at = 방패 가운데, size = 반 크기 (칸), life = 초.
     * o.progress = -1 빛이 지나감 되풀이 (기본 — o.phase = 시작 장, 틱마다 다시 그려도 이어짐) · 0 수명 동안 메두사의 눈이 번쩍 · 2 수명 동안 흩어짐
     */
    static void shieldFx(ServerLevel level, Vec3 at, double size, double life, Opt o) {
        SkillFx.fx(level, "rpg:aegis_shield", at, Opt.of("dir", V.UP, "speed", 0, "size", size, "life", life, "spin", o.num("phase", 0), "progress",
                o.num("progress", -1), "color", SkillFx.WHITE, "alpha", o.num("alpha", 1)));
    }

    /** 육각 칸 하나 (rpg:aegis_hex — 보는 사람을 향함): o.fold 초부터 0.4초 동안 o.dir (단위) 쪽으로 o.reach 칸 접혀 들어감, o.frame 0 보통 · 1 번쩍 */
    static void hexFx(ServerLevel level, Vec3 at, Opt o) {
        SkillFx.fx(level, "rpg:aegis_hex", at, Opt.of("dir", o.vec("dir", V.UP), "size", o.num("size", 0), "life", o.num("life", 0), "color",
                o.color("color", AE_WHITE), "alpha", o.num("alpha", 0.55), "fold", o.num("fold", 99), "reach", o.num("reach", 0), "frame", o.num("frame", 0)));
    }

    /** 돌 조각 하나 (rpg:aegis_stone): o.kind 0~3, o.dir · o.speed (칸/초), 아래로 o.grav (칸/초²), o.spin · o.rate (도 · 도/초) */
    static void stoneFx(ServerLevel level, Vec3 at, Opt o) {
        SkillFx.fx(level, "rpg:aegis_stone", at, Opt.of("dir", o.vec("dir", V.UP), "speed", o.num("speed", 0), "size", o.num("size", 0), "life",
                o.num("life", 0), "spin", o.num("spin", 0), "color", o.color("color", AE_STONE), "alpha", o.num("alpha", 1), "grav", o.num("grav", 0),
                "rate", o.num("rate", 0), "kind", o.num("kind", 0)));
    }

    /** 돌 껍질: 몸 둘레에 돌 조각 n 개가 붙음 — part 0 아래 · 1 가운데 · 2 위 (세 틱에 나눠 그려 발부터 굳어 감), life 초 동안 그대로 */
    static void stoneCrust(ServerLevel level, LivingEntity e, int part, double life, int n) {
        if (e.isRemoved()) return;
        Vec3 l = e.position();
        double hgt = Math.max(0.5, e.getEyeY() - l.y + 0.3);
        double r = 0.34 + Kit.bodyPad(e);
        for (int j = 0; j < n; j++) {
            double a = ((j + part * 0.37) / n) * Math.PI * 2 + part;
            double rr = r * (0.8 + (0.25 * ((j * 7 + part * 3) % 5)) / 4);
            double y = l.y + (hgt * (part + (j + 0.5) / n)) / 3;
            stoneFx(level, new Vec3(l.x + Math.cos(a) * rr, y, l.z + Math.sin(a) * rr), Opt.of("size", 0.15 + 0.03 * ((j + part) % 3), "life", life, "kind",
                    (j + part) % 4, "spin", (j * 97 + part * 41) % 360, "alpha", 0.95));
        }
    }

    /** 돌이 깨짐: 몸 가운데 c 에서 돌 조각이 튀어 떨어지고 발밑 feet 에 먼지. 조각은 보는 사람 (eye) 쪽으로는 튀지 않음 (1인칭 화면을 가리지 않게) */
    static void stoneBreak(ServerLevel level, Vec3 c, Vec3 feet, double pad, Vec3 eye) {
        Vec3 toEye = V.flat(V.sub(eye, c));
        for (int j = 0; j < 7; j++) {
            double a = (j / 7.0) * Math.PI * 2 + 0.4;
            double hx = Math.cos(a);
            double hz = Math.sin(a);
            if (hx * toEye.x + hz * toEye.z > 0.2) {
                hx = -hx;
                hz = -hz;
            }
            Vec3 d = V.unit(new Vec3(hx, 0.55 + 0.1 * (j % 3), hz));
            stoneFx(level, V.add(c, d, 0.25), Opt.of("dir", d, "speed", 3.2 + 0.4 * (j % 3), "grav", 16, "size", 0.16 + 0.04 * (j % 3), "life", 0.7,
                    "kind", j % 4, "spin", j * 51, "rate", j % 2 == 1 ? 420 : -420));
        }
        SkillFx.fx(level, "rpg:dust", V.up(feet, 0.5), Opt.of("size", 1.3 + pad, "life", 0.6, "spin", 30, "color", AE_DUST, "alpha", 0.8));
    }

    /** a → b 곧은 빛 한 줄 (rpg:arrow_trail 하나 — skills.js streakLine, Gram.streakLine 과 같음) */
    private static void streakLine(ServerLevel level, Vec3 a, Vec3 b, double[] color, double thick, double life, double alpha) {
        Vec3 d = V.sub(b, a);
        double len = V.len(d);
        if (len < 0.05) return;
        SkillFx.fx(level, "rpg:arrow_trail", V.add(a, d, 0.5), Opt.of("dir", V.unit(d), "size", len / 2, "thick", thick, "life", life, "delay", 0,
                "color", color, "alpha", alpha));
    }

    /** a → b 곧은 빛 한 줄 (조각 하나) 에서 눈 near 칸 안의 부분은 빼고 (1인칭 화면을 덮지 않게 — 한 틱의 파티클 수를 줄임) */
    static void streakAway(ServerLevel level, Vec3 eye, Vec3 a, Vec3 b, double[] color, double thick, double life, double alpha, double near) {
        Vec3 d = V.sub(b, a);
        double len = V.len(d);
        if (len < 0.05) return;
        Vec3 u = V.unit(d);
        double t0 = 0;
        double t1 = len;
        // 선에서 눈까지 near 칸 안인 구간을 잘라냄 (앞쪽이나 뒤쪽 한쪽만 남김)
        Vec3 w = V.sub(eye, a);
        double tc = V.dot(w, u);
        double off2 = V.dot(w, w) - tc * tc;
        if (off2 < near * near) {
            double hw = Math.sqrt(near * near - off2);
            if (tc - hw <= 0) t0 = Math.max(t0, tc + hw);
            else t1 = Math.min(t1, tc - hw);
        }
        if (t1 - t0 > 0.3) streakLine(level, V.add(a, u, t0), V.add(a, u, t1), color, thick, life, alpha);
    }

    /** a → b 번개 (굵은 조각 — 조각 길이 seg 칸, 한 틱의 파티클 수를 줄임). o: jag · thick · life */
    static void boltCoarse(ServerLevel level, Vec3 a, Vec3 b, double[] color, Opt o, double seg) {
        Vec3 d = V.sub(b, a);
        double len = V.len(d);
        if (len < 0.1) return;
        Vec3 u = V.unit(d);
        Vec3 s0 = V.unit(Math.abs(u.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(-u.z, 0, u.x));
        int n = Math.max(3, (int) Math.ceil(len / seg));
        Vec3[] pts = new Vec3[n + 1];
        pts[0] = a;
        for (int i = 1; i < n; i++) {
            double j = (Kit.rand() * 2 - 1) * o.num("jag", 0.45);
            pts[i] = V.add(V.add(a, u, len * i / n), s0, j);
        }
        pts[n] = b;
        for (int i = 0; i < n; i++) {
            Vec3 s = V.sub(pts[i + 1], pts[i]);
            SkillFx.fx(level, "rpg:arrow_trail", V.add(pts[i], s, 0.5), Opt.of("dir", V.unit(s), "size", V.len(s) / 2 + 0.08, "thick", o.num("thick", 0.12),
                    "life", o.num("life", 0.3), "color", color, "alpha", 1));
        }
    }

    static void boltCoarse(ServerLevel level, Vec3 a, Vec3 b, double[] color, Opt o) {
        boltCoarse(level, a, b, color, o, 2.0);
    }

    // ------------------------------------------------------------ 스킬 1 고르곤 돌진

    /** 밀고 가는 적의 옆 자리 */
    private static final double[] SLOTS = {-0.65, 0, 0.65};
    /** 뒷줄은 0.75칸 앞 */
    private static final double ROW = 0.75;

    /** 밀고 가는 적: lat = 옆 자리 (-0.65 · 0 · 0.65), row = 줄 (0 · 1), pad = 몸 반폭 여유, free = 놓침 */
    private static final class Carried {
        final LivingEntity e;
        final double lat;
        final int row;
        final double pad;
        boolean free;

        Carried(LivingEntity e, double lat, int row, double pad) {
            this.e = e;
            this.lat = lat;
            this.row = row;
            this.pad = pad;
        }
    }

    /** 밀고 갈 자리 (옆 · 줄) */
    private record Slot(double lat, int row) {}

    /**
     * 고르곤 돌진: 아이기스 모양 금빛 빛의 방패 (몸 앞 front 칸) 를 앞세워 보는 쪽 (가로) 으로 dash 칸 돌진 — 틱마다 speed 칸, 땅을 따라 (한 칸 오르내림),
     * 벽 · 낭떠러지 · 물 · 용암 앞에서 멈춤. 방패에 닿은 적 (길에서 옆으로 width 칸, 몸 반폭 여유 — 방패에서 보이는 적) 은 부딪힘 (mult):
     * 끌 수 있는 몹은 carry 마리까지 방패 앞에 붙여 밀고 감 (더 많으면 옆으로 밀쳐냄), 끌 수 없는 몹 (보스 · 큰 몹 · 허수아비) 이면 그 몸 앞에서 멈춤.
     * 밀던 적이 길 끝 (벽 · 낭떠러지) 에 닿으면 거기서 멈춤 (몰아붙임). 끝에서 방패의 메두사 눈이 번쩍 — 밀던 적과 방패 앞 gaze 칸 · cone 도 (방패에서 보이는) 의 적이
     * stone 틱 동안 돌이 됨 (석화 — 보스 · 소품은 걸리지 않음, 돌 껍질만 잠깐) → 돌이 깨지며 afterMult + 도발 (taunt 틱).
     * 속박 · 무엇이든 탄 동안은 돌진 없이 그 자리에서 메두사의 시선. 돌진 중 침묵 · 속박 · 다른 것이 나를 옮김 (끌어당김 등) 이면 그 자리에서 멈춤.
     * 쓰러지거나 차원을 옮기면 남은 것은 없음. 적이 없어도 돌진 (재사용 대기)
     */
    static boolean charge(ServerPlayer sp, Weapon w, Obj k) {
        new Charge(sp, w, k).start();
        return true;
    }

    /** 고르곤 돌진 한 번 (skills.js aegisCharge 의 지역 변수 · 함수) */
    private static final class Charge {
        final ServerPlayer sp;
        final Weapon w;
        final Obj k;
        final ServerLevel level;
        final BooleanSupplier ok;
        final Vec3 start;
        final Vec3 f;
        final Vec3 side;
        final double[] glow;
        final double[] core;
        final double front;
        /** 길의 시작 (땅 높이) */
        final Vec3 from;
        final List<Kit.Spot> spots;
        /** 밀고 가는 적이 갈 수 있는 데까지 (벽이면 그 앞 0.6칸) */
        final double lm;
        /** 내가 갈 수 있는 거리 */
        final double len;
        /** 방패에 닿은 적 (한 번씩) */
        final Set<UUID> touched = new HashSet<>();
        /** 밀고 가는 적 */
        final List<Carried> carried = new ArrayList<>();
        /** 지금까지 간 거리 */
        double s;
        /** 이 스킬이 마지막으로 둔 자리 */
        Vec3 last;
        /** 다른 것이 나를 옮김 */
        boolean broken;

        Charge(ServerPlayer sp, Weapon w, Obj k) {
            this.sp = sp;
            this.w = w;
            this.k = k;
            this.level = sp.level();
            String life = Kit.lifeOf(sp);
            this.ok = () -> Kit.sameLife(sp, life);
            this.start = sp.position();
            this.f = Kit.facingOf(sp);
            this.side = V.rightOf(f);
            double[][] cs = Kit.colors(w);
            this.glow = cs[0];
            this.core = cs[1];
            this.front = k.num("front");
            // 길은 땅 높이에서: 뛰는 중에 써도 발밑 땅 (3칸 안) 기준 (화룡 돌진과 같음)
            Double g = Kit.solidBelow(level, V.up(start, 0.3), 3.3);
            this.from = new Vec3(start.x, g == null ? start.y : Math.min(start.y, g), start.z);
            // 길: 돌진 거리 + 밀고 가는 적이 설 자리 (방패 앞 약 1.25칸 + 몸 · 줄) 까지 — 땅이 이어지는 데까지 (벽 · 낭떠러지 · 물 · 용암 앞에서 끊김)
            double dash = k.num("dash");
            double reach = held() ? 0 : Kit.reachFlat(level, from, f, dash + 2.5);
            this.spots = reach > 0.2 ? Kit.dashSpots(level, from, f, reach) : List.of();
            this.lm = spots.isEmpty() ? 0 : spots.get(spots.size() - 1).s();
            // 내가 갈 수 있는 거리: 돌진 거리 · 땅이 이어지는 데까지 — 벽이면 방패 (몸 앞 front 칸) 가 벽에 들어가지 않게 (방패 가운데가 벽 앞 0.1칸)
            boolean wall = reach < dash + 2.5 - 1e-6;
            this.len = Math.max(0, Math.min(Math.min(dash, lm), wall ? reach - (front - 0.5) : Double.POSITIVE_INFINITY));
            this.last = sp.position();
        }

        boolean held() {
            return Status.isRooted(sp) || Status.isSilenced(sp) || Kit.ridingAny(sp);
        }

        /** 길 위 거리 at 의 땅 높이 (그 앞 설 자리의 땅) */
        double yAt(double at) {
            double y = from.y;
            for (Kit.Spot x : spots) {
                if (x.s() > at + 0.25) break;
                y = x.p().y;
            }
            return y;
        }

        Vec3 onPath(double at) {
            Vec3 q = V.add(from, f, at);
            return new Vec3(q.x, yAt(at), q.z);
        }

        /** 밀고 가는 적의 자리: 앞줄부터, 옆으로 가장 가까운 빈 자리 (몸이 겹치지 않게). 없으면 null */
        Slot slotFor(double lat) {
            for (int row = 0; row < 2; row++) {
                Double best = null;
                double bd = Double.POSITIVE_INFINITY;
                for (double x : SLOTS) {
                    if (taken(row, x)) continue;
                    if (Math.abs(x - lat) < bd) {
                        bd = Math.abs(x - lat);
                        best = x;
                    }
                }
                if (best != null) return new Slot(best, row);
            }
            return null;
        }

        private boolean taken(int row, double x) {
            for (Carried c : carried) if (!c.free && c.row == row && c.lat == x) return true;
            return false;
        }

        /** 아직 밀고 가는 적 수 */
        int carrying() {
            int n = 0;
            for (Carried c : carried) if (!c.free) n++;
            return n;
        }

        /** 밀고 가는 적의 몸 가운데까지 (방패 앞 0.35칸 + 몸 반폭 + 줄) */
        double ahead(Carried c) {
            return front + 0.35 + c.pad + ROW * c.row;
        }

        /** 밀고 가는 적 가운데 가장 앞의 몸 끝까지 (방패에서) — 끌 수 없는 몹 앞에서 멈출 때 */
        double depth() {
            double m = 0;
            for (Carried c : carried) if (!c.free) m = Math.max(m, 0.35 + 0.3 + 2 * c.pad + ROW * c.row + 0.05);
            return m;
        }

        /** 다른 것이 나를 옮겼는지 (한 틱 사이 가로 2.5칸 · 세로 3칸 넘게 — 이 스킬이 둔 자리에서) */
        boolean displaced() {
            Vec3 l = sp.position();
            if (Math.hypot(l.x - last.x, l.z - last.z) > 2.5 || Math.abs(l.y - last.y) > 3) broken = true;
            return broken;
        }

        /** 몸 앞으로 방패를 둘 수 있는 거리: front 칸까지 — 막는 블록 (벽 · 턱) 이 있으면 그 앞 0.3칸 (방패 · 메두사의 시선이 블록 안에서 시작하지 않게) */
        double roomAt(Vec3 base, double h) {
            return Math.max(0.3, Math.min(front, Kit.rayDist(level, V.up(base, h), f, front + 0.3) - 0.3));
        }

        Vec3 shieldAt() {
            Vec3 l = sp.position();
            return V.add(V.up(l, AE_SHIELD_Y), f, roomAt(l, AE_SHIELD_Y));
        }

        /** 메두사의 시선이 나가는 곳: 가슴 높이 (1.2) 의 방패 자리 — 블록 안이 아님 */
        Vec3 gazeFrom() {
            Vec3 l = sp.position();
            return V.add(V.up(l, 1.2), f, roomAt(l, 1.2));
        }

        /** 길 위 at 칸의 자리 (옆 lat 칸) 로 옮김 — 막히면 (몸이 턱 · 벽 모서리에 걸침) 0.3칸 뒤 · 앞도 해 봄 (lo ~ hi 안). 옮긴 거리 (못 옮기면 null) */
        Double moveAlong(Entity e, double at, double lo, double hi, double lat) {
            for (double ds : new double[] {0, -0.3, 0.3}) {
                double s2 = at + ds;
                if (s2 < lo - 1e-9 || s2 > hi + 1e-9) continue;
                if (Kit.moveTo(e, V.add(onPath(s2), side, lat))) return s2;
            }
            return null;
        }

        /** 방패에 부딪힘: 금빛 번쩍임 · 불꽃 + 피해 (heavy = 끌 수 없는 몹 — 더 크게, 눈 가까이는 작게) */
        void bash(LivingEntity e, boolean heavy) {
            Vec3 at0 = Kit.bodyPoint(e);
            Kit.hitAt(sp, e, Kit.reserveHit(sp, e, Tasks.now()), ok, () -> {
                if (Kit.skillHit(sp, e, w, k.num("mult")) <= 0) return;
                Vec3 at = Kit.bodyAt0(e, at0);
                Vec3 face = V.add(at, f, -(0.3 + Kit.bodyPad(e)));
                double nearEye = Math.min(1, Math.max(0.45, (V.len(V.sub(face, sp.getEyePosition())) - 0.8) / 2.5));
                SkillFx.burst(level, face, heavy ? AE_WHITE : core, (heavy ? 1.6 : 0.9) * nearEye, 0.25);
                SkillFx.sparks(level, face, glow, heavy ? 8 : 5, heavy ? 6 : 4);
                SkillFx.play(level, "mob.irongolem.hit", at, heavy ? 0.6 : 0.9, 1.0);
                if (heavy) {
                    SkillFx.play(level, "random.anvil_land", at, 0.8, 0.6);
                    SkillFx.shake(sp, 0.25, 0.2);
                }
            });
        }

        /** 끝: 메두사의 눈이 번쩍 → 돌이 됨 → stone 틱 뒤 깨지며 피해 · 도발 */
        void finish() {
            Vec3 eye = sp.getEyePosition();
            Vec3 sa = shieldAt();
            Vec3 ga = gazeFrom();
            // 빛의 방패: 눈이 번쩍 (0.4초) → 그 자리에서 흩어짐 (0.3초). 두 눈의 빛살 + 앞쪽 부채꼴로 퍼지는 청록빛 시선 (눈 1.2칸 안은 빼고)
            shieldFx(level, sa, AE_SHIELD, 0.4, Opt.of("progress", 0));
            Tasks.later(8, () -> {
                if (ok.getAsBoolean()) shieldFx(level, sa, AE_SHIELD, 0.3, Opt.of("progress", 2));
            });
            for (double[] ey : AE_EYES) {
                Vec3 at = V.add(V.add(sa, side, ey[0] * AE_SHIELD), V.UP, ey[1] * AE_SHIELD);
                SkillFx.fx(level, "rpg:arrow_spark", V.add(at, f, 0.05), Opt.of("dir", f, "size", 0.2, "grow", 4, "life", 0.3, "color", AE_EYE, "alpha", 1));
            }
            double cone = k.num("cone");
            double gaze = k.num("gaze");
            for (int j = -2; j <= 2; j++) {
                Vec3 d = V.turnY(f, j * cone / 4);
                streakAway(level, eye, V.add(sa, d, 0.4), V.add(V.up(sa, -0.3), d, gaze), AE_EYE, 0.2, 0.35, 0.8, 1.2);
            }
            SkillFx.play(level, "mob.elderguardian.curse", sa, 1.4, 0.7);
            // 돌이 될 적: 밀던 적 + 시선 앞 gaze 칸 · cone 도 (가슴 높이의 방패 자리에서 보이는 — 위아래 2.5칸)
            double half = Math.toRadians(cone / 2);
            List<LivingEntity> gazed = new ArrayList<>();
            for (LivingEntity e : Kit.visibleFrom(sp, ga, Kit.around(sp, ga, gaze, 2.5))) if (Kit.bodyInCone(e, ga, f, half)) gazed.add(e);
            List<LivingEntity> pushed = new ArrayList<>();
            for (Carried c : carried) if (!c.free && Kit.canHit(sp, c.e)) pushed.add(c.e);
            List<LivingEntity> stoned = Kit.union(pushed, gazed);
            // 돌 조각: 적이 많으면 하나에 덜 (한 틱 파티클 60 안 — 셋까지는 한 틱에 여섯씩)
            int chips = Math.max(2, Math.min(6, 45 / Math.max(1, stoned.size())));
            int stone = (int) k.num("stone");
            int n = 0;
            for (LivingEntity e : stoned) {
                if (bossLike(e)) {
                    stoneCrust(level, e, 1, 0.25, chips); // 보스는 석화되지 않음: 돌 껍질이 잠깐 붙었다 떨어짐
                    continue;
                }
                petrify(e, stone);
                for (int part = 0; part < 3; part++) {
                    int p = part;
                    Runnable crust = () -> {
                        if (ok.getAsBoolean() && !e.isRemoved()) stoneCrust(level, e, p, (stone - p) / 20.0, chips);
                    };
                    if (part == 0) crust.run();
                    else Tasks.later(part, crust);
                }
                if (n++ < 3) SkillFx.play(level, "dig.stone", Kit.bodyAt0(e, sa), 0.6, 1.0);
            }
            // 깨짐: 피해 + 돌 조각 · 먼지 + 도발 (다섯씩 한 틱 — 한 틱 파티클 60 안)
            Tasks.later(stone, () -> {
                if (!ok.getAsBoolean()) return;
                boolean any = false;
                int j = 0;
                for (LivingEntity e : stoned) {
                    if (!Kit.canHit(sp, e)) continue;
                    any = true;
                    Vec3 c0 = Kit.bodyAt0(e, sa);
                    Vec3 feet = e.position();
                    double pad = Kit.bodyPad(e);
                    Kit.hitAt(sp, e, Kit.reserveHit(sp, e, Tasks.now() + j++ / 5), ok, () -> {
                        if (Kit.skillHit(sp, e, w, k.num("afterMult")) <= 0) return;
                        stoneBreak(level, Kit.bodyAt0(e, c0), !e.isRemoved() ? e.position() : feet, pad, sp.getEyePosition());
                        tauntMark(level, e, taunt(sp, e, (int) k.num("taunt")));
                    });
                }
                if (any) {
                    Vec3 at = V.up(sp.position(), 1.2);
                    SkillFx.play(level, "mob.irongolem.crack", at, 1.2, 1.0);
                    SkillFx.play(level, "random.anvil_land", at, 1.7, 0.4);
                    SkillFx.shake(sp, 0.15, 0.2);
                }
            });
        }

        /** 밀고 가는 적이 길 끝 (벽 · 낭떠러지 앞) 을 넘지 않는 데까지 */
        double pinLimit(double want) {
            double lim = want;
            for (Carried c : carried) if (!c.free) lim = Math.min(lim, lm - ahead(c));
            return lim;
        }

        void start() {
            // 출발: 방패를 들어 올리는 소리 + 발밑 고리
            SkillFx.shock(level, from, glow, 1.3, 0.35);
            SkillFx.play(level, "item.shield.block", start, 0.8, 1.0);
            SkillFx.play(level, "mob.breeze.charge", start, 1.2, 0.8);
            int n = (int) Math.max(0, Math.ceil(len / k.num("speed") - 1e-6)); // 움직이는 틱 수
            if (n == 0) {
                // 움직일 수 없음 (벽 바로 앞 · 속박 · 탐): 방패에 닿아 있는 적만 부딪히고 그 자리에서 메두사의 시선
                double room = roomAt(sp.position(), 1.0);
                for (Kit.LineHit h : Kit.lineHits(sp, V.up(from, 1.0), f, room + 0.35, k.num("width") + 0.3)) {
                    LivingEntity e = h.e();
                    double along = (e.getX() - from.x) * f.x + (e.getZ() - from.z) * f.z;
                    if (along < -0.1 || along - 0.3 - Kit.bodyPad(e) > room + 0.05) continue;
                    touched.add(e.getUUID());
                    bash(e, !draggable(e));
                }
                shieldFx(level, shieldAt(), AE_SHIELD, 0.1, Opt.of("phase", 0, "alpha", 0.6));
                finish();
                return;
            }
            Kit.ticks(t -> step(t + 1, n));
        }

        /** 돌진 한 틱 (i = 1, 2, …): 계속하면 true */
        boolean step(int i, int n) {
            if (!ok.getAsBoolean()) return false;
            double prev = s;
            double want = Math.min(len, prev + k.num("speed"));
            boolean stop = false;
            if (held() || displaced()) {
                want = prev; // 묶이거나 다른 것이 옮김: 그 자리에서 멈춤
                stop = true;
            }
            // 밀던 적이 벽 · 낭떠러지에 닿으면 거기서 멈춤 (몰아붙임)
            double lim0 = pinLimit(want);
            if (lim0 < want) {
                want = Math.max(prev, lim0);
                stop = true;
            }
            // 방패 (와 밀고 가는 적의 몸) 에 새로 닿는 적: 지난 자리 (내 몸 가운데) 부터 이번 자리의 방패 앞 (+ 밀고 가는 적의 몸) 까지,
            // 길에서 옆으로 width 칸 (몸 반폭 여유) — 가슴 높이에서 보이는 적
            double dep = depth();
            for (Kit.LineHit h : Kit.lineHits(sp, V.up(onPath(prev), 1.0), f, Math.max(0, want - prev) + front + 0.35 + dep, k.num("width") + 0.3)) {
                LivingEntity e = h.e();
                if (touched.contains(e.getUUID())) continue;
                Vec3 rel = V.sub(e.position(), from);
                double along = rel.x * f.x + rel.z * f.z;
                double pad = Kit.bodyPad(e);
                if (along < prev - 0.1 || along - 0.3 - pad > want + front + 0.05 + dep) continue;
                touched.add(e.getUUID());
                if (!draggable(e)) {
                    // 끌 수 없는 몹 (보스 · 큰 몹 · 허수아비): 그 몸 앞에서 멈춤 (부딪힘 — 밀던 적이 있으면 그 적이 그 몸에 닿는 데서)
                    want = Math.min(want, Math.max(prev, along - 0.3 - pad - front - 0.05 - depth()));
                    stop = true;
                    bash(e, true);
                    continue;
                }
                double lat = rel.x * side.x + rel.z * side.z;
                Slot slot = carrying() < k.num("carry") ? slotFor(lat) : null;
                if (slot != null) {
                    carried.add(new Carried(e, slot.lat(), slot.row(), pad));
                } else {
                    // 더 밀 수 없으면 옆으로 밀쳐냄
                    double sg = lat >= 0 ? 1 : -1;
                    Kit.knockback(e, side.x * sg * 0.7, side.z * sg * 0.7, 0.25);
                }
                bash(e, false);
            }
            // 새로 밀게 된 적도 길 끝을 넘지 않게
            double lim1 = pinLimit(want);
            if (lim1 < want) {
                want = Math.max(prev, lim1);
                stop = true;
            }
            if (want > prev + 1e-3) {
                // 막힌 자리 (몸이 턱 · 모서리에 걸침) 면 0.3칸 뒤 (다음 틱에 다시 앞으로), 그것도 막히면 그 앞에서 멈춤
                Double got = moveAlong(sp, want, prev + 0.05, want, 0);
                if (got != null) {
                    s = got;
                    last = sp.position();
                } else {
                    stop = true;
                }
            }
            // 밀고 가는 적: 방패 앞 자기 자리에 (발은 길의 땅). 옮길 수 없으면 (막힘 — 0.3칸 앞뒤도) 놓침
            for (Carried c : carried) {
                if (c.free) continue;
                if (c.e.isRemoved() || Kit.hasNoHealth(c.e)) {
                    c.free = true;
                    continue;
                }
                if (moveAlong(c.e, Math.min(lm, s + ahead(c)), Double.NEGATIVE_INFINITY, lm, c.lat) == null) c.free = true;
            }
            // 그림: 빛의 방패 (틱마다 다시 — 빛이 지나가는 장은 이어짐) + 양옆 금빛 빛줄기 + 발밑 흙먼지
            Vec3 eye = sp.getEyePosition();
            shieldFx(level, shieldAt(), AE_SHIELD, 0.09, Opt.of("phase", i * 0.6, "alpha", 0.6));
            if (s > prev + 1e-3) {
                for (int sg : new int[] {-1, 1}) {
                    streakAway(level, eye, V.add(V.up(onPath(prev), 0.6), side, sg * 0.95), V.add(V.up(onPath(s), 0.6), side, sg * 0.95), glow, 0.18, 0.25,
                            0.85, 1.0);
                }
                SkillFx.fx(level, "rpg:dust", V.up(onPath(prev), 0.25), Opt.of("size", 0.9, "life", 0.5, "spin", i * 40, "color", AE_DUST, "alpha", 0.6));
            }
            if (stop || s >= len - 1e-3 || i >= n + 3) {
                finish();
                return false;
            }
            return true;
        }
    }

    // ------------------------------------------------------------ 스킬 2 신의 방벽

    /** 신의 방벽 돔의 육각 칸 자리 (가운데 기준 단위 벡터): 아래부터 고리 8개 — 고리마다 둘레 ÷ 1.15칸 개 (한 고리 건너 반 칸씩 엇갈림) + 꼭대기 하나 */
    static List<List<Vec3>> domeLayout(double r) {
        List<List<Vec3>> rings = new ArrayList<>();
        int rows = 8;
        for (int j = 0; j < rows; j++) {
            double th = ((j + 0.35) / rows) * (Math.PI / 2);
            int n = (int) Math.max(3, Math.round((2 * Math.PI * r * Math.cos(th)) / 1.15));
            List<Vec3> ring = new ArrayList<>();
            for (int q = 0; q < n; q++) {
                double ph = ((q + (j % 2) * 0.5) / n) * Math.PI * 2;
                ring.add(new Vec3(Math.cos(th) * Math.cos(ph), Math.sin(th), Math.cos(th) * Math.sin(ph)));
            }
            rings.add(ring);
        }
        rings.get(rows - 1).add(new Vec3(0, 1, 0));
        return rings;
    }

    /** 신의 방벽이 선 사람: 끝나는 틱 · 돔 안인지 (맞을 때 육각 칸이 번쩍 — register 의 피해 알림) */
    private record Dome(long until, Predicate<ServerPlayer> inside) {}

    /** 플레이어 → 신의 방벽 */
    private static final Map<UUID, Dome> DOMES = new HashMap<>();

    /** 신의 방벽이 막음: 때린 쪽 (때린 것이 없으면 보는 쪽) 1.6칸에 육각 칸이 번쩍 + 불꽃 + 막는 소리 */
    static void domeBlocked(ServerPlayer sp, Entity attacker) {
        if (sp.isRemoved()) return;
        ServerLevel level = sp.level();
        Vec3 b = Kit.bodyPoint(sp);
        Vec3 a = attacker != null && !attacker.isRemoved() ? Kit.bodyAt0(attacker, b) : V.add(b, Kit.facingOf(sp), 1);
        Vec3 d = V.len(V.sub(a, b)) > 1e-3 ? V.unit(V.sub(a, b)) : Kit.facingOf(sp);
        Vec3 at = V.add(b, d, 1.6); // 1인칭 화면을 덮지 않게 몸에서 1.6칸
        hexFx(level, at, Opt.of("frame", 1, "size", 0.42, "life", 0.22, "alpha", 0.85, "color", AE_WHITE));
        List<Object> pair = Kit.SKILLS.obj("color").listOrNull("shield");
        SkillFx.sparks(level, at, pair != null && !pair.isEmpty() ? Kit.rgb(pair.get(0)) : AE_WHITE, 4, 3);
        SkillFx.play(level, "item.shield.block", at, 1.3, 0.7);
    }

    /** 돔 안의 적: 가운데 위 1.2칸에서 보이는 (반지름 r + extra, 위아래 3칸) */
    private static List<LivingEntity> enemiesIn(ServerPlayer sp, Vec3 c, double r, double extra) {
        return Kit.visibleFrom(sp, V.up(c, 1.2), Kit.around(sp, c, r + extra, 3));
    }

    /** 둔화 (15틱 구속 slow + 1, 보스는 아님) + 도발 (taunt 틱). mark 면 머리 위 "도발" */
    private static void slowAndTaunt(ServerPlayer sp, ServerLevel level, LivingEntity e, Obj k, boolean mark) {
        if (!bossLike(e)) e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 15, (int) k.num("slow"), false, false));
        boolean scripted = taunt(sp, e, (int) k.num("taunt"));
        if (mark) tauntMark(level, e, scripted);
    }

    /**
     * 신의 방벽: 방패를 땅에 꽂아 (발밑 땅 — 3칸 안에 없으면 그 자리) 반지름 radius 칸 금빛 육각 돔을 ticks 틱 동안 세움.
     * 꽂는 충격: 돔 안의 적 (가운데 위 1.2칸에서 보이는) 에게 mult + 도발 · 둔화. 돔 안에 서 있는 동안 내가 받는 피해 -reduce (막을 때마다 육각 칸이 번쩍 —
     * 돔 밖으로 나가면 없음), 10틱마다 돔 안의 적을 도발 (taunt 틱) · 둔화 (구속 slow + 1, 보스는 아님). ticks 틱에 돔이 안으로 접힘 (fold 틱 — 육각 칸이 가운데로,
     * 돔 안의 적을 가운데로 끌어당기며 foldMult) → burst 틱 뒤 번개와 함께 바깥으로 터짐 (afterMult, 밖으로 밀쳐냄 — 접힐 때 맞은 적은 밀려났어도 radius + 2.5칸 안이면).
     * 쓰러지거나 차원을 옮기면 막기 · 남은 타격 (접힘 · 폭발) 은 없음 (이미 그린 돔은 그대로 접히며 사라짐)
     */
    static boolean dome(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        Vec3 p0 = sp.position();
        Double g = Kit.solidBelow(level, V.up(p0, 0.3), 3.3);
        Vec3 c = new Vec3(p0.x, g == null ? p0.y : Math.min(p0.y, g), p0.z);
        Vec3 f = Kit.facingOf(sp);
        double r = k.num("radius");
        int ticks = (int) k.num("ticks");
        int fold = (int) k.num("fold");
        double[][] cs = Kit.colors(w);
        double[] glow = cs[0];
        double[] core = cs[1];
        // 돔 안에 서 있는지: 몸 가운데 (발 위 0.9칸) 가 반구 (반지름 radius + 0.3) 안, 발이 땅 아래 1.5칸까지
        Predicate<ServerPlayer> inside = pl -> V.len(V.sub(V.up(pl.position(), 0.9), c)) <= r + 0.3 && pl.getY() >= c.y - 1.5;
        Predicate<ServerPlayer> guarded = pl -> ok.getAsBoolean() && inside.test(pl);
        // 막기: 돔 안에 서 있는 동안만 (돔이 접힐 때까지) — 맞을 때마다 육각 칸이 번쩍 (DOMES)
        Guards.set(sp, ticks, k.num("reduce"), null, guarded);
        UUID id = sp.getUUID();
        Dome dome = new Dome(Tasks.now() + ticks, guarded);
        DOMES.put(id, dome);
        Tasks.later(ticks, () -> {
            if (DOMES.get(id) == dome) DOMES.remove(id);
        });
        // 꽂는 충격: 앞 1.5칸 (벽이 가까우면 그 앞) 에 꽂힌 빛의 방패 (밑이 땅, 0.6초 → 흩어짐) + 땅의 룬 (돔이 접힐 때까지) · 충격 고리 + 불꽃.
        // 고리 (rpg:shock_ring) 는 수명 동안 2.6배로 커짐 — R ÷ 2.6 에서 시작해 돔 가장자리 안에서 사라짐 (돔 밖까지 퍼져 범위를 헷갈리게 하지 않게)
        double plantAt = Math.max(0.6, Math.min(1.5, Kit.rayDist(level, V.up(c, 0.85), f, 1.9) - 0.4));
        Vec3 planted = V.add(V.up(c, 0.85), f, plantAt);
        shieldFx(level, planted, 0.85, 0.6, Opt.of("phase", 0));
        Tasks.later(12, () -> {
            if (ok.getAsBoolean()) shieldFx(level, planted, 0.85, 0.35, Opt.of("progress", 2));
        });
        SkillFx.rune(level, c, Opt.of("size", r, "life", (ticks + fold) / 20.0, "color", glow, "alpha", 0.7, "rate", 20));
        SkillFx.shock(level, c, glow, r / RING_GROW, 0.5);
        SkillFx.shock(level, c, core, r * 0.55 / RING_GROW, 0.4);
        // 번쩍임 · 불꽃은 방패가 꽂힌 밑동에서 (내 발밑이 아니라 — 아래를 볼 때 1인칭 화면을 덮지 않게)
        Vec3 foot = V.add(V.up(c, 0.35), f, plantAt);
        SkillFx.burst(level, foot, core, 1.4, 0.3);
        SkillFx.sparks(level, foot, glow, 8, 5);
        SkillFx.play(level, "mace.heavy_smash_ground", c, 0.9, 1.0);
        SkillFx.play(level, "item.shield.block", c, 0.7, 1.0);
        SkillFx.play(level, "beacon.activate", c, 1.2, 0.9);
        Kit.shakeNear(level, c, 10, 0.2, 0.25);
        for (LivingEntity e : enemiesIn(sp, c, r, 0)) {
            Kit.hitAt(sp, e, Kit.reserveHit(sp, e, Tasks.now()), ok, () -> {
                if (Kit.skillHit(sp, e, w, k.num("mult")) > 0) slowAndTaunt(sp, level, e, k, true);
            });
        }
        // 돔: 아래 고리부터 한 틱에 하나씩 솟아오름 (ticks + fold 틱까지) — ticks 틱부터 0.4초 동안 가운데 쪽으로 접혀 들어감
        // (파티클 식 — 가운데 1.8칸에서 멈춤: 가운데 선 사람의 머리를 지나지 않게)
        List<List<Vec3>> rings = domeLayout(r);
        for (int j = 0; j < rings.size(); j++) {
            List<Vec3> ring = rings.get(j);
            int jj = j;
            Runnable draw = () -> {
                if (!ok.getAsBoolean()) return;
                for (Vec3 u : ring) {
                    hexFx(level, V.add(c, u, r), Opt.of("dir", V.scale(u, -1), "size", 0.62, "life", (ticks + fold - jj) / 20.0, "fold", (ticks - jj) / 20.0,
                            "reach", r - 1.8, "alpha", 0.5, "color", glow));
                }
            };
            if (j == 0) draw.run();
            else Tasks.later(j, draw);
        }
        // 10틱마다 돔 안의 적을 도발 · 둔화, 20틱마다 바닥 고리가 가운데에서 돔 가장자리까지 일렁임
        for (int t = 10; t < ticks; t += 10) {
            int tt = t;
            Tasks.later(t, () -> {
                if (!ok.getAsBoolean()) return;
                for (LivingEntity e : enemiesIn(sp, c, r, 0)) slowAndTaunt(sp, level, e, k, false);
                if (tt % 20 == 0) {
                    SkillFx.shock(level, c, glow, r / RING_GROW, 0.4);
                    SkillFx.play(level, "beacon.ambient", c, 1.4, 0.7);
                }
            });
        }
        // 접힘: 땅의 룬 둘이 육각 칸과 함께 0.4초 동안 가운데로 말려 듦 (바깥으로 퍼지는 고리가 아니라 안으로) · 소리 + 돔 안의 적을 가운데로 끌어당기며 벰
        Map<UUID, LivingEntity> folded = new LinkedHashMap<>(); // 접힐 때 맞은 적 (밀려났어도 터질 때 맞음)
        Tasks.later(ticks, () -> {
            if (!ok.getAsBoolean()) return;
            SkillFx.play(level, "beacon.deactivate", c, 1.4, 1.0);
            SkillFx.play(level, "mob.warden.sonic_charge", c, 1.5, 0.7);
            // (땅의 룬보다 조금 위 — 같은 높이의 평면 둘이 깜빡이지 않게)
            SkillFx.rune(level, V.up(c, 0.04), Opt.of("size", r, "life", fold / 20.0, "grow", -0.8, "color", core, "alpha", 0.95, "rate", -360));
            SkillFx.rune(level, V.up(c, 0.08), Opt.of("size", r * 0.62, "life", fold / 20.0, "grow", -0.75, "color", glow, "alpha", 0.9, "rate", 300));
            for (LivingEntity e : enemiesIn(sp, c, r, 0.5)) {
                folded.put(e.getUUID(), e);
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, Tasks.now()), ok, () -> {
                    if (Kit.skillHit(sp, e, w, k.num("foldMult")) <= 0 || bossLike(e) || e.isRemoved()) return;
                    Vec3 d = V.flat(V.sub(c, e.position()));
                    Kit.knockback(e, d.x * 0.55, d.z * 0.55, 0.12);
                });
            }
        });
        // 터짐: 가운데 번쩍임 → 번개와 함께 바깥으로 — 바닥으로 번개 여섯 줄 · 고리 둘, 적마다 하늘에서 번개 (한 틱에 셋씩), 밖으로 밀쳐냄
        Tasks.later(ticks + (int) k.num("burst"), () -> {
            if (!ok.getAsBoolean()) return;
            // 가운데 번쩍임은 돔 가운데 가슴 높이 — 내 눈이 그 3칸 안이면 (가운데에 선 채로) 보는 쪽 앞 (벽 앞까지 2.2칸) 에 작게
            // (화면에 나란한 그림이 눈 바로 밑에 크게 그려지면 아래를 볼 때 1인칭 화면을 다 덮음 — 그람 용익 강하의 앞쪽 번쩍임과 같음)
            Vec3 mid = V.up(c, 1.2);
            if (V.len(V.sub(mid, sp.getEyePosition())) >= 3) {
                SkillFx.burst(level, mid, AE_WHITE, 3.2, 0.35);
                SkillFx.burst(level, mid, glow, 4.6, 0.45);
            } else {
                Vec3 pl = sp.position();
                Vec3 fN = Kit.facingOf(sp);
                Vec3 at = V.up(V.add(pl, fN, Math.max(0.8, Math.min(2.2, Kit.rayDist(level, V.up(pl, 0.6), fN, 2.6) - 0.4))), 0.6);
                SkillFx.burst(level, at, AE_WHITE, 1.8, 0.35);
                SkillFx.burst(level, at, glow, 2.6, 0.45);
            }
            SkillFx.shock(level, c, glow, r + 1.5, 0.55);
            SkillFx.shock(level, c, core, r + 0.5, 0.45);
            for (int q = 0; q < 6; q++) {
                Vec3 d = V.turnY(f, q * 60 + 30);
                boltCoarse(level, V.up(c, 0.5), V.up(V.add(c, d, r + 1.5), 0.3), glow, Opt.of("thick", 0.16, "life", 0.35, "jag", 0.3));
            }
            SkillFx.play(level, "ambient.weather.thunder", c, 1.3, 0.8);
            SkillFx.play(level, "item.trident.thunder", c, 1.0, 1.0);
            SkillFx.play(level, "random.explode", c, 1.2, 0.7);
            Kit.shakeNear(level, c, 14, 0.35, 0.4);
            List<LivingEntity> pulled = new ArrayList<>();
            for (LivingEntity e : folded.values()) {
                if (Kit.canHit(sp, e) && Math.hypot(e.getX() - c.x, e.getZ() - c.z) <= r + 2.5 + Kit.bodyPad(e) && Math.abs(e.getY() - c.y) <= 5) pulled.add(e);
            }
            List<LivingEntity> list = Kit.union(enemiesIn(sp, c, r, 0.5), pulled);
            for (int j = 0; j < list.size(); j++) {
                LivingEntity e = list.get(j);
                Runnable strike = () -> {
                    if (!ok.getAsBoolean()) return;
                    Kit.hitAt(sp, e, Kit.reserveHit(sp, e, Tasks.now()), ok, () -> {
                        Vec3 b = Kit.bodyAt0(e, V.up(c, 1));
                        boltCoarse(level, V.up(b, 8), b, glow, Opt.of("thick", 0.24, "life", 0.3, "jag", 0.5));
                        boltCoarse(level, V.up(b, 8), b, AE_WHITE, Opt.of("thick", 0.09, "life", 0.25, "jag", 0.5));
                        if (Kit.skillHit(sp, e, w, k.num("afterMult")) <= 0) return;
                        SkillFx.fx(level, "rpg:arrow_spark", b, Opt.of("dir", V.UP, "size", 0.5, "grow", 2.2, "life", 0.22, "color", AE_WHITE, "alpha", 1));
                        if (bossLike(e) || e.isRemoved()) return;
                        Vec3 d = V.flat(V.sub(e.position(), c));
                        Kit.knockback(e, d.x * 0.9, d.z * 0.9, 0.35);
                    });
                };
                int wait = j / 3;
                if (wait == 0) strike.run();
                else Tasks.later(wait, strike);
            }
        });
        return true;
    }
}
