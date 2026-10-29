package mmorpg.fabric.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import mmorpg.core.Obj;
import mmorpg.core.Weapon;
import mmorpg.fabric.Fx3d;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 클라우 솔라스 (단검) — 섬광 난무 · 광휘의 검진 (skills.js v2.19.0 "빛 · 섬광": 빛이 되어 적 사이를 순간이동하며 벰 · 머리 위 빛의 검이 내리꽂힘). 수치는 SKILLS.dagger.
 * 사용자가 고른 컨셉 "섬광 난무 / 광휘의 검진" · 세기 "영웅 룬~초월 룬" (Lv.40 민첩 125 의 민첩 룬 위력 기준) · 빛의 검 그림은 모델 옆모습 (rpg:solas_sword)
 */
public final class Solas {
    private Solas() {}

    /** 흰 금빛 기운 */
    static final double[] SOL_LIGHT = {1.0, 0.95, 0.72};
    /** 날밑 · 자루의 보랏빛 (0.12.8 — 무기 디자인 색): 참격 바깥 기운 (예전 SOL_LIGHT) */
    static final double[] SOL_SHADE = {0.55, 0.36, 0.8};
    static final double[] SOL_WHITE = {1, 1, 1};
    /** 섬광 난무가 이어서 고르는 적이 될 수 있는 무리 (적대 몹 · 이 애드온 몬스터 · 보스 · 허수아비). 조준한 적은 무엇이든 */
    static final List<String> RUSH_FAMILIES = List.of("monster", "rpg_monster", "rpg_boss", "rpg_boss_prop", "rpg_dummy");
    /** 이어서 고르지 않는 중립 몹 (때리면 무리가 화를 냄 — 조준하면 됨). 자바 이름으로 (Kit.typeId 와 비교) */
    static final Set<String> RUSH_NEUTRAL = javaTypes("minecraft:zombie_pigman", "minecraft:piglin", "minecraft:enderman", "minecraft:wolf", "minecraft:bee",
            "minecraft:polar_bear", "minecraft:llama", "minecraft:trader_llama", "minecraft:panda", "minecraft:dolphin", "minecraft:goat");

    /** 보스 필드 확인: sp 가 at 에 서서 e (보스 · 소품) 에게 순간이동해도 되는지 */
    @FunctionalInterface
    public interface BossField {
        boolean ok(ServerPlayer sp, Entity e, Vec3 at);
    }

    /**
     * 보스 필드 확인 (보스가 넣음 — skills.js setBossFieldCheck): 그 보스 (소품) 의 싸움 필드 밖 (울타리 너머) 에 선 자리면 false —
     * 필드 밖에서는 보스에게 피해를 줄 수 없는 규칙을 순간이동으로 넘지 않게. 기본은 늘 됨
     */
    public static BossField bossFieldOk = (sp, e, at) -> true;

    static void register() {
        Transcend.register("solas_rush", Solas::rush);
        Transcend.register("solas_halo", Solas::halo);
    }

    private static Set<String> javaTypes(String... bedrockIds) {
        Set<String> out = new HashSet<>();
        for (String id : bedrockIds) out.add(Kit.javaType(id));
        return Set.copyOf(out);
    }

    // ------------------------------------------------------------ 그림 도구

    /**
     * 빛의 검 그림 하나 (rpg:solas_sword — 궁니르 창과 같은 변수): at = 칼 가운데, dir = 칼끝 방향 (단위), half = 칼 반 길이 (칸), life = 초.
     * o: speed · move (움직이는 방향, 기본 dir) · progress (기본 -1, 2 = 수명 동안 흩어짐) · alpha
     */
    static void swordFx(ServerLevel level, Vec3 at, Vec3 dir, double half, double life, Opt o) {
        SkillFx.fx(level, "rpg:solas_sword", at, Opt.of("dir", dir, "size", half, "life", life, "speed", o.num("speed", 0), "progress", o.num("progress", -1),
                "color", SkillFx.WHITE, "alpha", o.num("alpha", 1), "move", o.vec("move", dir), "seg", 0.0));
    }

    static void swordFx(ServerLevel level, Vec3 at, Vec3 dir, double half, double life) {
        swordFx(level, at, dir, half, life, Opt.empty());
    }

    /** 포물선으로 나는 빛의 검 (rpg:solas_sword_arc — 칼끝이 날아가는 방향을 따라 돎): 처음 속도 v (칸/초), 중력 grav (칸/초²) */
    static void swordArcFx(ServerLevel level, Vec3 at, Vec3 v, double half, double life, double grav) {
        double speed = V.len(v);
        Vec3 d = speed > 1e-6 ? V.unit(v) : V.DOWN;
        SkillFx.fx(level, "rpg:solas_sword_arc", at, Opt.of("dir", d, "size", half, "life", life, "speed", speed, "progress", -1.0, "color", SkillFx.WHITE,
                "alpha", 1.0, "move", d, "grav", grav, "seg", 0.0));
    }

    /** a → b 곧은 빛 한 줄 (조각 하나) 에서 눈 near 칸 안의 부분은 빼고 (beamAway 와 같은 규칙, 파티클 하나 — 한 틱의 파티클 수를 줄임) */
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
        double wl = V.len(w);
        double off2 = wl * wl - tc * tc;
        if (off2 < near * near) {
            double hw = Math.sqrt(near * near - off2);
            if (tc - hw <= 0) t0 = Math.max(t0, tc + hw);
            else t1 = Math.min(t1, tc - hw);
        }
        if (t1 - t0 > 0.3) Gram.streakLine(level, V.add(a, u, t0), V.add(a, u, t1), color, thick, life, alpha, 0);
    }

    /** 그 자리 (발 · 머리 칸) 가 서면 다치는 곳인지 (용암 · 불 · 마그마 등 — standSpot 과 같은 Kit.HAZARD) */
    static boolean hazardAt(Level level, Vec3 p) {
        for (double dy : new double[] {0.1, 1.1}) {
            if (Kit.hazard(level.getBlockState(BlockPos.containing(p.x, p.y + dy, p.z)))) return true;
        }
        return false;
    }

    /** 무리 이름이 하나라도 있는지 */
    static boolean hasFamily(Entity e, List<String> list) {
        for (String f : list) if (Kit.hasFamily(e, f)) return true;
        return false;
    }

    private static boolean hostile(Entity e) {
        return hasFamily(e, RUSH_FAMILIES);
    }

    /** 목록에서 아직 벨 수 있는 첫 적 (없으면 null) */
    private static LivingEntity firstHittable(ServerPlayer sp, Iterable<LivingEntity> list) {
        for (LivingEntity x : list) if (Kit.canHit(sp, x)) return x;
        return null;
    }

    /**
     * 자국이 터지는 모습 (v1.2.1 빛의 삼연격에서): 흰 X 번쩍임 → 금빛 폭발 · 고리 · 불꽃 + 발밑에서 솟는 빛기둥.
     * c = 몸 가운데, feet = 발 (맞기 전에 잰 자리 — 쓰러져도 그림), eye = 보는 사람 눈 (가까우면 작게 · 2.5칸 안이면 빛기둥 심지는 뺌 — 1인칭 화면을 덮지 않게)
     */
    static void lightBurst(ServerLevel level, Vec3 c, Vec3 feet, Vec3 eye, double[] glow, double[] core) {
        double d = V.len(V.sub(c, eye));
        Vec3 v = d > 1e-3 ? V.unit(V.sub(c, eye)) : V.UP;
        double s = Math.min(1, Math.max(0.45, (d - 0.8) / 3));
        SkillFx.cutMark(level, c, v, 38, 0.05, SOL_WHITE, SOL_WHITE, 1.2 * s);
        SkillFx.cutMark(level, c, v, -38, 0.05, SOL_WHITE, SOL_WHITE, 1.2 * s);
        SkillFx.burst(level, V.add(c, v, -0.15), glow, 0.95 * s, 0.3);
        SkillFx.sparks(level, c, core, 4, 6); // 한 틱에 넷이 함께 터져도 60개 안 (모바일)
        lightPillar(level, feet, glow, s, d >= 2.5);
    }

    /** 발밑에서 솟는 빛기둥 (금빛 빛 + 흰 심지) + 발밑 고리. size 1 = 높이 약 3칸. withCore = 흰 심지 (가까우면 뺌) */
    static void lightPillar(ServerLevel level, Vec3 feet, double[] glow, double size, boolean withCore) {
        // 0.48.0 입체 빛기둥 (고리가 돎 — 높이 약 3.4칸 × size)
        WeaponFx3d.pillar(level, feet, 0.42 * size, 3.4 * size, 0.5, glow, SOL_WHITE, Opt.empty());
        SkillFx.fx(level, "rpg:arrow_shot", V.up(feet, 1.5 * size), Opt.of("dir", V.UP, "speed", 0.0, "size", 1.6 * size, "thick", 0.5 * size, "life", 0.42,
                "color", glow, "alpha", 0.6));
        if (withCore) {
            SkillFx.fx(level, "rpg:arrow_shot", V.up(feet, 1.5 * size), Opt.of("dir", V.UP, "speed", 0.0, "size", 1.5 * size, "thick", 0.2 * size, "life", 0.34,
                    "color", SOL_WHITE, "alpha", 1.0));
        }
        SkillFx.shock(level, feet, glow, 1.3 * size, 0.4);
    }

    // ------------------------------------------------------------ 스킬 1 섬광 난무

    /**
     * 섬광 난무의 적: 앞쪽 cone 도 · range 칸 (위아래 3칸) 안의 눈에서 보이는 적 가운데 — 조준한 적 (조준 보정) 부터, 바로 앞 적에서 가까운 순으로 이어 최대 targets 명.
     * 이어서 고르는 적은 적대 무리만 (동물 · 중립 몹은 조준해야 — 무리가 화를 내지 않게)
     */
    static List<LivingEntity> rushTargets(ServerPlayer sp, Obj k) {
        Vec3 start = sp.position();
        Vec3 eye = sp.getEyePosition();
        Vec3 f = Kit.facingOf(sp);
        double half = Math.toRadians(k.num("cone") / 2);
        double range = k.num("range");
        List<LivingEntity> inCone = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, start, range)) if (Kit.bodyInCone(e, start, f, half)) inCone.add(e);
        List<LivingEntity> pool = Kit.visibleFrom(sp, eye, inCone);
        LivingEntity cur = null;
        RpgCombat.Aim r = RpgCombat.aimTarget(sp, range, Kit.BOW.numOr("aimAssist", 0));
        // 조준한 적도 앞쪽 · 높이 안이어야 (멀리 위 턱의 적에게 올라가지 않게)
        if (r.target() != null && !r.target().isRemoved() && pool.contains(r.target())) cur = r.target();
        Map<UUID, LivingEntity> left = new LinkedHashMap<>();
        for (LivingEntity e : pool) if (hasFamily(e, RUSH_FAMILIES) && !RUSH_NEUTRAL.contains(Kit.typeId(e))) left.put(e.getUUID(), e);
        if (cur == null) cur = nearestTo(left, start);
        List<LivingEntity> out = new ArrayList<>();
        int targets = (int) k.num("targets");
        while (cur != null && out.size() < targets) {
            out.add(cur);
            left.remove(cur.getUUID());
            cur = nearestTo(left, cur.position());
        }
        return out;
    }

    /** 남은 적 가운데 p 에서 가로로 가장 가까운 것 (없으면 null) */
    private static LivingEntity nearestTo(Map<UUID, LivingEntity> left, Vec3 p) {
        LivingEntity best = null;
        double bd = Double.POSITIVE_INFINITY;
        for (LivingEntity e : left.values()) {
            double d = Math.hypot(e.getX() - p.x, e.getZ() - p.z);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /**
     * 섬광 난무로 옮길 자리: 적의 몸 반폭 + side 칸 떨어진, want 쪽 (적에서 본 단위 방향) 자리 — 안 되면 want 에서 ±35° · ±70° · ±115° · 반대쪽 순.
     * 설 자리 (standSpot — 물 · 용암 · 불 · 허공 · 막힘 아님) 이고, 처음 자리와 높이가 3칸 안 · 가로 range + 3칸 안이고, 거기서 적의 몸이 보여야 (벽 너머 아님).
     * 없으면 null (그 자리에서 빛의 참격)
     */
    static Vec3 rushSpot(Level level, LivingEntity e, Vec3 want, Obj k, Vec3 start) {
        Vec3 t = e.position();
        double reach = 0.4 + Kit.bodyPad(e) + k.num("side");
        Vec3 body = Kit.bodyPoint(e);
        for (double a : new double[] {0, 35, -35, 70, -70, 115, -115, 180}) {
            Vec3 q = V.add(t, V.turnY(want, a), reach);
            Vec3 p = Kit.standSpot(level, new Vec3(q.x, t.y, q.z), 2.0);
            if (p == null) continue;
            if (Math.abs(p.y - start.y) > 3 || Math.hypot(p.x - start.x, p.z - start.z) > k.num("range") + 3) continue;
            if (!Kit.clearPath(level, V.up(p, 1.2), body)) continue;
            return p;
        }
        return null;
    }

    /** 벤 적과 그 몸에 남은 빛의 자국 기울기들 (처음 벨 때만 피해 · 마지막에 자국이 터짐) */
    private static final class Marked {
        final LivingEntity e;
        final Set<Integer> angles = new LinkedHashSet<>();

        Marked(LivingEntity e, int angle) {
            this.e = e;
            angles.add(angle);
        }
    }

    /** 적마다 옮겨 간 횟수 · 처음 선 쪽 (다시 갈 때 90° 씩 돌아감 — 한 명이면 네 방향) */
    private static final class Visit {
        int n = 1;
        final Vec3 dir;

        Visit(Vec3 dir) {
            this.dir = dir;
        }
    }

    /** moved: 한 번이라도 옮겼는지, path: 지나온 자리 (몸 높이 — 검을 거둘 때 다시 빛남), last: 이 스킬이 마지막으로 둔 자리, broken: 다른 것이 나를 옮김 */
    private static final class RushState {
        boolean moved;
        final List<Vec3> path = new ArrayList<>();
        Vec3 last;
        boolean broken;

        RushState(Vec3 start) {
            path.add(V.up(start, 1.1));
            last = start;
        }
    }

    /**
     * 섬광 난무 (v2.19.0): 앞쪽 (cone 도) range 칸 안의 적 — 조준한 적부터 가까운 순으로 이어 최대 targets 명 — 사이를 gap 틱마다 빛이 되어 순간이동하며 벰
     * (모두 blinks 번, 적이 적으면 돌아가며 — 한 명이면 그 둘레를 90° 씩 돌며 네 방향에서). 옮길 때마다 그 적을 바라봄. 적마다 처음 벨 때 mult (치명타는 무기 그대로),
     * 몸에 빛의 자국이 남음 (적을 따라감) → 처음 자리로 돌아와 (보던 방향으로) delay 틱 뒤 검을 거두면 지나온 길이 다시 빛나고 모든 자국이 한꺼번에 터짐
     * (afterMult — 처음 자리에서 range + 6칸 안이면).
     * 옮기지 않음 (그 자리에서 빛의 참격, 피해 같음): 설 자리가 없음 · 속박 · 침묵 (보스가 잡음 · 삼킴) · 탐 · 다른 것이 나를 옮김 (끌어당김 · 엔더 진주 —
     * 그 뒤로는 돌아오지도 않음) · 보스 필드 밖에서 그 보스에게. 처음 자리가 서면 다치는 곳 (용암 · 불) 이 되었으면 돌아오지 않음. 쓰는 동안 · 뒤 0.4초는 낙하 피해 없음.
     * 앞쪽에 적이 없으면 재사용 대기 없이 거절. 쓰러지거나 차원을 옮기면 남은 것은 멈춤
     */
    static boolean rush(ServerPlayer sp, Weapon w, Obj k) {
        List<LivingEntity> list = rushTargets(sp, k);
        if (list.isEmpty()) {
            Notify.warn(sp, "§c" + k.str("name") + ": §7앞쪽 " + k.text("range") + "칸 안에 적이 없습니다");
            return false;
        }
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = Kit.colors(w);
        double[] glow = cs[0];
        double[] core = cs[1];
        Vec3 start = sp.position();
        float[] rot0 = {sp.getYRot(), sp.getXRot()};
        int gap = (int) k.num("gap");
        int blinks = (int) k.num("blinks");
        int backAt = blinks * gap; // 처음 자리로 돌아오는 틱
        int burstAt = backAt + (int) k.num("delay"); // 자국이 터지는 틱
        Kit.noFall(sp, burstAt + 8);
        Map<UUID, Marked> marked = new LinkedHashMap<>();
        Map<UUID, Visit> visits = new HashMap<>();
        RushState st = new RushState(start);
        BooleanSupplier held = () -> Status.isRooted(sp) || Status.isSilenced(sp) || Kit.ridingAny(sp);
        // 다른 것이 나를 옮겼는지 (한 틱 사이 가로 2.5칸 · 세로 3칸 넘게 — 이 스킬이 둔 자리에서)
        BooleanSupplier displaced = () -> {
            Vec3 l = sp.position();
            if (Math.hypot(l.x - st.last.x, l.z - st.last.z) > 2.5 || Math.abs(l.y - st.last.y) > 3) st.broken = true;
            return st.broken;
        };
        // 시작: 손의 금빛 번쩍임 + 발밑 고리
        Vec3 f0 = Kit.facingOf(sp);
        Vec3 eye0 = sp.getEyePosition();
        SkillFx.fx(level, "rpg:arrow_spark", V.up(V.add(V.add(eye0, f0, 0.9), V.rightOf(f0), 0.35), -0.35), Opt.of("dir", f0, "size", 0.3, "grow", 3.0,
                "life", 0.2, "color", core, "alpha", 1.0));
        SkillFx.shock(level, start, glow, 1.4, 0.35);
        SkillFx.play(level, "chime.amethyst_block", eye0, 1.6, 1.0);
        for (int i = 0; i < blinks; i++) {
            final int ii = i;
            Runnable blink = () -> {
                if (!ok.getAsBoolean()) return;
                LivingEntity e0 = list.get(ii % list.size());
                LivingEntity e = Kit.canHit(sp, e0) ? e0 : firstHittable(sp, list); // 쓰러졌으면 남은 적
                if (e == null) return;
                Vec3 from = sp.position();
                Vec3 look = Kit.bodyPoint(e);
                Vec3 t = e.position();
                // 처음 가는 적: 지금 자리에서 그 적을 지나 반대편 (번갈아 ±35° 비낌). 다시 가는 적: 처음 선 쪽에서 90° 씩 돌아감
                Visit been = visits.get(e.getUUID());
                Vec3 through = Math.hypot(t.x - from.x, t.z - from.z) > 0.05 ? V.flat(V.sub(t, from)) : Kit.facingOf(sp);
                Vec3 want = been != null ? V.turnY(been.dir, 90 * been.n) : V.turnY(through, ii % 2 == 1 ? -35 : 35);
                boolean free = !held.getAsBoolean() && !displaced.getAsBoolean() && bossFieldOk.ok(sp, e, start);
                Vec3 spot = free ? rushSpot(level, e, want, k, start) : null;
                boolean moved = false;
                if (spot != null) {
                    float[] rot = Kit.lookRotation(V.up(spot, 1.62), look);
                    moved = Kit.moveTo(sp, spot, rot[0], rot[1]);
                }
                if (been != null) been.n++;
                else visits.put(e.getUUID(), new Visit(moved ? V.flat(V.sub(spot, t)) : want));
                Vec3 eye = sp.getEyePosition();
                if (moved) {
                    st.moved = true;
                    st.last = sp.position();
                    st.path.add(V.up(spot, 1.1));
                    // 빛이 되어 지나간 길 (한 줄, 눈 1.2칸 안은 빼고 — 1인칭 화면을 덮지 않게) + 떠난 자리의 잔상 번쩍임
                    streakAway(level, eye, V.up(from, 1.1), V.up(spot, 1.1), glow, 0.4, 0.3, 0.9, 1.2);
                    streakAway(level, eye, V.up(from, 1.1), V.up(spot, 1.1), SOL_WHITE, 0.13, 0.24, 1, 1.2);
                    SkillFx.burst(level, V.up(from, 1.0), SOL_LIGHT, 0.9, 0.22);
                    SkillFx.sparks(level, V.up(from, 1.0), glow, 5, 3);
                    SkillFx.play(level, "mob.endermen.portal", spot, 1.5 + 0.1 * ii, 0.7);
                } else {
                    // 옮기지 못함: 손에서 그 적으로 빛의 참격이 날아감
                    Vec3 hand = V.add(V.up(eye, -0.25), V.rightOf(Kit.facingOf(sp)), 0.3);
                    streakAway(level, eye, hand, look, glow, 0.32, 0.26, 0.9, 1.2);
                    streakAway(level, eye, hand, look, SOL_WHITE, 0.1, 0.2, 1, 1.2);
                }
                // 벤 자리: 빛의 초승달 (눈 가까이는 작게) + 빛의 자국 (/ \ 번갈아 — 적을 따라 다시 그림, 터질 때까지) + 불꽃
                Vec3 v = V.unit(V.sub(look, eye));
                double near = Math.min(1, Math.max(0.5, V.len(V.sub(look, eye)) / 3));
                SkillFx.arc(level, V.add(look, v, -0.2), v, Opt.of("shape", "arc", "flip", ii % 2 == 1, "size", 1.9 * near, "life", 0.24, "speed", 1.2,
                        "spin", (ii % 2 == 1 ? -1 : 1) * 40.0, "color", glow, "aura", SOL_SHADE));
                int angle = ii % 2 == 1 ? -38 : 38;
                // 0.48.0 내 단검 (빛이 된 칼날 — 금빛 빛 무리) 이 그 자국대로 몸을 가르며 지나감
                WeaponFx3d.cutThrough(level, sp, w, look, v, angle, 0.75, 0.08, 0.06, Opt.of("glow", SOL_LIGHT, "glowAlpha", 0.55, "fadeOut", 0.06));
                SkillFx.cutMark(level, look, v, angle, 0.2, glow, core, near);
                SkillFx.sparks(level, look, core, 5, 4);
                SkillFx.play(level, "item.enchant.lunge" + (ii % 3 + 1), look, 1.1 + 0.08 * ii, 1.0);
                SkillFx.play(level, "item.spear.attack_miss", look, 1.4 + 0.1 * ii, 1.0);
                Marked m = marked.get(e.getUUID());
                if (m != null) {
                    m.angles.add(angle);
                    return;
                }
                marked.put(e.getUUID(), new Marked(e, angle));
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, Tasks.now()), ok, () -> {
                    Vec3 at = Kit.bodyAt0(e, look);
                    if (Kit.skillHit(sp, e, w, k.num("mult")) > 0) SkillFx.play(level, "chime.amethyst_block", at, 1.6 + 0.1 * ii, 1.0);
                });
            };
            if (i == 0) blink.run();
            else Tasks.later(i * gap, blink);
        }
        // 빛의 자국: 4틱마다 적의 지금 몸에 다시 그림 (걸어가도 따라감) — 터질 때까지
        for (int t = 4; t < burstAt; t += 4) {
            final int tt = t;
            Tasks.later(t, () -> {
                if (!ok.getAsBoolean()) return;
                Vec3 eye = sp.getEyePosition();
                for (Marked m : marked.values()) {
                    if (m.e.isRemoved()) continue;
                    Vec3 c = Kit.bodyAt0(m.e, eye);
                    Vec3 v = V.unit(V.sub(c, eye));
                    double near = Math.min(1, Math.max(0.5, V.len(V.sub(c, eye)) / 3));
                    for (int a : m.angles) SkillFx.cutMark(level, c, v, a, Math.min(0.2, (burstAt - tt) / 20.0), glow, core, near);
                }
            });
        }
        // 처음 자리로 돌아옴 (보던 방향으로). 다른 것이 나를 옮겼거나 · 묶였거나 · 그 자리가 용암 · 불이 되었으면 그대로
        Tasks.later(backAt, () -> {
            if (!ok.getAsBoolean() || !st.moved || held.getAsBoolean() || displaced.getAsBoolean() || hazardAt(level, start)) return;
            Vec3 from = sp.position();
            if (!Kit.moveTo(sp, start, rot0[0], rot0[1])) return;
            st.last = start;
            st.path.add(V.up(start, 1.1));
            Vec3 eye = sp.getEyePosition();
            streakAway(level, eye, V.up(from, 1.1), V.up(start, 1.1), glow, 0.4, 0.32, 0.9, 1.2);
            streakAway(level, eye, V.up(from, 1.1), V.up(start, 1.1), SOL_WHITE, 0.13, 0.26, 1, 1.2);
            SkillFx.burst(level, V.up(from, 1.0), SOL_LIGHT, 0.9, 0.22);
            SkillFx.shock(level, start, glow, 1.2, 0.3);
            SkillFx.play(level, "mob.endermen.portal", start, 1.9, 0.8);
        });
        // 검을 거둠: 지나온 길이 다시 빛나고 모든 빛의 자국이 한꺼번에 터짐
        Tasks.later(burstAt, () -> {
            if (!ok.getAsBoolean()) return;
            Vec3 eye = sp.getEyePosition();
            Vec3 f = Kit.facingOf(sp);
            SkillFx.fx(level, "rpg:arrow_spark", V.up(V.add(V.add(eye, f, 0.8), V.rightOf(f), 0.3), -0.45), Opt.of("dir", f, "size", 0.25, "grow", 3.0,
                    "life", 0.18, "color", SOL_LIGHT, "alpha", 1.0));
            SkillFx.play(level, "armor.equip_gold", eye, 1.3, 1.0);
            for (int j = 0; j + 1 < st.path.size(); j++) {
                streakAway(level, eye, st.path.get(j), st.path.get(j + 1), glow, 0.5, 0.36, 0.95, 1.4);
                streakAway(level, eye, st.path.get(j), st.path.get(j + 1), SOL_WHITE, 0.16, 0.3, 1, 1.4);
            }
            boolean any = false;
            for (Marked m : marked.values()) {
                LivingEntity e = m.e;
                if (!Kit.canHit(sp, e) || Math.hypot(e.getX() - start.x, e.getZ() - start.z) > k.num("range") + 6) continue;
                any = true;
                Vec3 c = Kit.bodyAt0(e, eye);
                Vec3 feet = e.position();
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, Tasks.now()), ok, () -> {
                    if (Kit.skillHit(sp, e, w, k.num("afterMult")) > 0) lightBurst(level, c, feet, sp.getEyePosition(), glow, core);
                });
            }
            if (any) {
                SkillFx.play(level, "firework.blast", eye, 1.2, 1.0);
                SkillFx.play(level, "block.bell.hit", eye, 1.6, 0.8);
                SkillFx.shake(sp, 0.18, 0.18);
                Tasks.later(3, () -> {
                    if (!sp.isRemoved()) SkillFx.play(sp.level(), "firework.twinkle", sp.getEyePosition(), 1.1, 1.0);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 스킬 2 광휘의 검진

    /** 광휘의 검진의 조준한 곳: 조준한 몹 (조준 보정) 의 발 → 시선이 닿은 곳 (벽 면이면 가로 0.4칸 앞) → 사거리 끝 */
    static Vec3 haloSpot(ServerPlayer sp, Obj k) {
        Vec3 eye = sp.getEyePosition();
        RpgCombat.Aim r = RpgCombat.aimTarget(sp, k.num("range"), Kit.BOW.numOr("aimAssist", 0));
        if (r.target() != null && !r.target().isRemoved() && Kit.canHit(sp, r.target())) return r.target().position();
        Vec3 d = r.dir();
        Vec3 q = V.add(eye, d, r.dist());
        return Math.hypot(d.x, d.z) > 0.2 ? V.add(q, V.flat(d), -0.4) : q;
    }

    /** 땅 높이 (at 아래 maxDown 칸 안의 블록 윗면 — 물 · 용암도), 없으면 null */
    static Double groundBelow(Level level, Vec3 at, double maxDown) {
        Kit.Hit hit = Kit.ray(level, V.up(at, 0.5), V.DOWN, maxDown + 0.5, true);
        if (hit == null) return null;
        double fy = hit.point().y - hit.pos().getY();
        return hit.pos().getY() + (fy > 0.05 && fy <= 1 ? fy : 1);
    }

    /** 몸의 오른쪽으로 얼마나 (head 에서 가로로 잰 side 쪽 거리) */
    private static double sideOf(Entity e, Vec3 head, Vec3 side) {
        return (e.getX() - head.x) * side.x + (e.getZ() - head.z) * side.z;
    }

    /** 머리 위 반원의 검 자리 · 칼끝 방향 */
    private record Slot(Vec3 pos, Vec3 ray) {}

    /** 지금 머리 위 반원에서 i 번째 (n 자루 중) 검의 자리 · 칼끝 방향 (따라다님) */
    private static Slot haloAt(ServerPlayer sp, int i, int n, double fit, double ring) {
        Vec3 f = Kit.facingOf(sp);
        Vec3 side = V.rightOf(f);
        Vec3 c = V.add(V.up(sp.getEyePosition(), 1.1 * fit), f, -0.3);
        double th = ((double) i / Math.max(1, n - 1)) * Math.PI; // 0 = 오른쪽, π = 왼쪽
        Vec3 ray = V.add(new Vec3(side.x * Math.cos(th), 0, side.z * Math.cos(th)), V.UP, Math.sin(th));
        return new Slot(V.add(c, ray, ring), ray);
    }

    /** p 에서 ts 초 뒤 to 에 닿는 포물선 (중력 g) 의 처음 속도: (끝 − 처음) ÷ 시간 + 위로 ½ g T */
    private static Vec3 launchV(Vec3 p, Vec3 to, double ts, double g) {
        return V.add(new Vec3((to.x - p.x) / ts, (to.y - p.y) / ts, (to.z - p.z) / ts), V.UP, 0.5 * g * ts);
    }

    /** 빛의 검 한 자루: 번호 · 맡은 적 (쓰러지면 바뀜) · 마지막으로 안 몸 자리 · 날아가는 틱 · 날아가는 시간 (틱 — 날아갈 때 정함, 0 = 아직) */
    private static final class Sword {
        final int i;
        LivingEntity e;
        Vec3 last;
        final int launch;
        int flight;
        /** 0.48.0 입체 빛의 검 (머리 위 반원 · 날아가 꽂힌 것 — Fx3d 번호) */
        int halo3d;
        int fly3d;

        Sword(int i, LivingEntity e, Vec3 last, int launch) {
            this.i = i;
            this.e = e;
            this.last = last;
            this.launch = launch;
        }
    }

    /** 꽂힌 검 (적을 따라 다시 그림 · 빛기둥 때 흩어짐): 적 · 몸 가운데에서 칼 가운데까지 · 칼끝 방향 · 지금 자리 · 반 길이 · 꽂힌 틱 */
    private static final class Stuck {
        final LivingEntity e;
        final Vec3 rel;
        final Vec3 dir;
        Vec3 at;
        final double half;
        final long since;
        /** 0.48.0 입체 빛의 검 (Fx3d 번호 — 적을 따라 옮김) */
        final int fx;

        Stuck(LivingEntity e, Vec3 rel, Vec3 dir, Vec3 at, double half, long since, int fx) {
            this.e = e;
            this.rel = rel;
            this.dir = dir;
            this.at = at;
            this.half = half;
            this.since = since;
            this.fx = fx;
        }
    }

    /** 0.48.0 입체 빛의 검 (phantom_sword — 코등이가 원점, 길이 1.67) 의 크기 · 원점: 가운데 center · 칼끝 dir · 반 길이 half 에 맞춤 */
    static double swordScale(double half) {
        return 2 * half / 1.67;
    }

    static Vec3 swordOrigin(Vec3 center, Vec3 dir, double half) {
        return V.add(center, dir, -0.48 * swordScale(half));
    }

    /**
     * 광휘의 검진 (v2.19.0): 조준한 곳 (range 칸) 반지름 radius 칸의 적 (눈에서 보이는 · 적대 무리 먼저 · 가까운 순 targets 명) — 없으면 재사용 대기 없이 거절.
     * 머리 위 반원 (반지름 ring, 오른쪽 → 위 → 왼쪽 — 칼끝이 바깥, 햇살처럼. 따라다님, 머리 위가 낮으면 작게) 에 빛의 검 swords 자루가 한 틱에 하나씩 나타남
     * (적을 몸의 오른쪽 → 왼쪽 순으로 나눠 맡음) → hold 틱 뒤 launchGap 틱마다 오른쪽부터 하나씩 포물선으로 날아올라 맡은 적에게 위에서 내리꽂힘
     * (거리에 따라 0.3~0.6초 — 칼끝이 나는 쪽을 따라 돌아서 옆에서 보임, 맡은 적이 쓰러졌으면 다른 적에게): 적마다 처음 꽂힐 때 mult (꽂힌 검은 적을 따라감) →
     * 마지막 검이 꽂히고 pillar 틱 뒤 꽂힌 검이 빛기둥으로 터짐 (pillarMult — 꽂힌 적) → cross 틱 뒤 조준한 곳 (아래 땅) 에 빛의 X 참격 (afterMult —
     * 반지름 안의 적 + 꽂힌 적은 밀려났어도 radius + 2.5칸 안이면, 1인칭 화면을 다 덮지 않게 거리에 맞춘 크기). 꽂힌 적에게 한 대상 mult + pillarMult + afterMult.
     * 쓰러지거나 차원을 옮기면 남은 것은 멈춤
     */
    static boolean halo(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        Vec3 head0 = sp.getEyePosition();
        Vec3 spot = haloSpot(sp, k);
        List<LivingEntity> near = Kit.visibleFrom(sp, head0, Kit.around(sp, spot, k.num("radius")));
        near.sort((a, b) -> {
            int h = (hostile(b) ? 1 : 0) - (hostile(a) ? 1 : 0);
            return h != 0 ? h : Double.compare(V.len(V.sub(a.position(), spot)), V.len(V.sub(b.position(), spot)));
        });
        List<LivingEntity> list = new ArrayList<>(near.subList(0, Math.min((int) k.num("targets"), near.size())));
        if (list.isEmpty()) {
            Notify.warn(sp, "§c" + k.str("name") + ": §7조준한 곳 " + k.text("radius") + "칸 안에 적이 없습니다");
            return false;
        }
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = Kit.colors(w);
        double[] glow = cs[0];
        double[] core = cs[1];
        // 머리 위가 낮으면 (굴 · 지붕 아래) 반원 · 검을 그만큼 작게, 포물선도 낮게 (천장을 뚫지 않게)
        double room = Kit.rayDist(level, V.up(head0, 0.1), V.UP, 3.5) + 0.1;
        double fit = Math.min(1, Math.max(0.2, (room - 0.2) / (1.1 + k.num("ring") + k.num("half"))));
        double ring = k.num("ring") * fit;
        double half = k.num("half") * fit;
        int n = (int) k.num("swords");
        int form = (int) k.num("form");
        // 몸의 오른쪽에 있는 적부터 (오른쪽 검이 오른쪽 적을 맡음)
        Vec3 side0 = V.rightOf(Kit.facingOf(sp));
        List<LivingEntity> order = new ArrayList<>(list);
        order.sort((a, b) -> Double.compare(sideOf(b, head0, side0), sideOf(a, head0, side0)));
        List<Sword> swords = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            LivingEntity e = order.get(i * order.size() / n);
            swords.add(new Sword(i, e, Kit.bodyPoint(e), form + (int) k.num("hold") + i * (int) k.num("launchGap")));
        }
        // 꽂힌 적 (빛기둥 · X 참격이 따라감)
        Map<UUID, LivingEntity> stabbed = new LinkedHashMap<>();
        // 꽂힌 검 (적을 따라 다시 그림 · 빛기둥 때 흩어짐)
        List<Stuck> stuck = new ArrayList<>();
        // 들어 올림: 손의 금빛 번쩍임 + 발밑 고리 + 머리 위 빛
        Vec3 f0 = Kit.facingOf(sp);
        SkillFx.fx(level, "rpg:arrow_spark", V.up(V.add(V.add(head0, f0, 0.9), side0, 0.35), -0.3), Opt.of("dir", f0, "size", 0.35, "grow", 3.0, "life", 0.25,
                "color", SOL_LIGHT, "alpha", 1.0));
        SkillFx.shock(level, sp.position(), glow, 1.6, 0.45);
        SkillFx.burst(level, V.add(V.up(head0, 1.1 * fit), f0, -0.3), SOL_LIGHT, 1.4 * fit, 0.35);
        SkillFx.play(level, "beacon.activate", head0, 1.6, 1.0);
        SkillFx.play(level, "mob.evocation_illager.prepare_attack", head0, 1.4, 0.7);
        for (Sword s : swords) {
            // 나타남 (한 틱에 하나) → 날아갈 때까지 3틱마다 지금 머리 위에 다시 그림 (따라다님)
            int appear = Math.min(s.i, form - 1);
            for (int t = appear; t < s.launch; t += 3) {
                final int tt = t;
                Runnable draw = () -> {
                    if (!ok.getAsBoolean()) return;
                    Slot h = haloAt(sp, s.i, n, fit, ring);
                    swordFx(level, h.pos(), h.ray(), half, Math.min(3, s.launch - tt) / 20.0 + 0.02, Opt.of("alpha", 0.35));
                    if (tt != appear) return;
                    // 0.48.0 입체 빛의 검: 머리 위 반원의 제자리 (나를 따라감 — 주인 기준 [오른쪽, 위, 앞]), 칼끝이 바깥 (햇살처럼)
                    double th = ((double) s.i / Math.max(1, n - 1)) * Math.PI;
                    Vec3 ray = new Vec3(Math.cos(th), Math.sin(th), 0);
                    Vec3 c = new Vec3(0, sp.getEyeHeight() + 1.1 * fit, -0.3);
                    s.halo3d = Fx3d.spawn(level, "phantom_sword", swordOrigin(V.add(c, ray, ring), ray, half), Opt.of("follow", (double) sp.getId(), "dir", ray,
                            "face", new Vec3(0, 0, 1), "scale", swordScale(half), "life", (s.launch - appear) / 20.0 + 0.1, "color", glow, "glow", SOL_LIGHT,
                            "pop", 0.12, "glowAlpha", 0.5, "shine", 0.5));
                    SkillFx.burst(level, h.pos(), SOL_LIGHT, 0.5 * fit, 0.18);
                    SkillFx.play(level, "chime.amethyst_block", h.pos(), 1.0 + s.i * 0.08, 0.8);
                    // 1인칭: 화면 위쪽으로 떨어지는 금빛 알갱이 (검은 머리 위라 화면 밖)
                    Vec3 fv = Kit.facingOf(sp);
                    Vec3 p = V.up(V.add(V.add(sp.getEyePosition(), fv, 1.7), V.rightOf(fv), ((double) s.i / Math.max(1, n - 1) - 0.5) * -2.2), 1.0);
                    SkillFx.fx(level, "rpg:arrow_spark", p, Opt.of("dir", V.DOWN, "speed", 1.0, "size", 0.16, "grow", 0.8, "life", 0.6, "color", SOL_LIGHT,
                            "alpha", 1.0));
                };
                if (t == 0) draw.run();
                else Tasks.later(t, draw);
            }
            // 날아감: 포물선으로 날아올라 맡은 적 (쓰러졌으면 다른 꽂힌 · 남은 적, 없으면 마지막 자리) 에게 위에서 내리꽂힘
            Tasks.later(s.launch, () -> {
                if (!ok.getAsBoolean()) return;
                if (!Kit.canHit(sp, s.e)) {
                    LivingEntity alt = firstHittable(sp, stabbed.values());
                    if (alt == null) alt = firstHittable(sp, order);
                    if (alt != null) s.e = alt;
                }
                Vec3 p = haloAt(sp, s.i, n, fit, ring).pos();
                Vec3 body = Kit.bodyAt0(s.e, s.last);
                s.last = body;
                // 같은 적에 꽂히는 검은 몸 둘레로 조금씩 비껴 꽂힘 (겹쳐 하나로 보이지 않게 — 황금각으로 돌며 점점 바깥)
                int j = 0;
                for (Sword x : swords) if (x.e == s.e && x.i < s.i) j++;
                Vec3 toward = V.flat(V.sub(body, p));
                Vec3 sideU = V.rightOf(toward);
                double r = Math.min(0.5, 0.16 * j);
                double ga = j * 2.4;
                Vec3 aim = V.add(V.add(body, sideU, Math.cos(ga) * r), V.UP, Math.sin(ga) * r * 1.4);
                double hd = Math.hypot(aim.x - p.x, aim.z - p.z);
                double sec = Math.min(0.6, Math.max(0.3, hd / 22)); // 날아가는 시간 (초)
                int flight = Math.max(1, (int) Math.round(sec * 20));
                double ts = flight / 20.0;
                double arcH = (2.0 + 0.1 * hd) * fit; // 포물선이 곧은 줄보다 솟는 높이
                double g = 8 * arcH / (ts * ts);
                // 칼끝이 몸에 박히게: 끝 자리 = 겨눈 곳에서 내리꽂는 방향으로 칼 반 길이의 0.55 앞 (두 번 잼 — 방향이 끝 자리에 따라 조금 바뀜)
                // 처음 속도 v0 = (끝 − 처음) ÷ 시간 + 위로 ½ g T (그 시간에 끝에 닿는 포물선), 끝 속도 = v0 − g T
                Vec3 end = aim;
                for (int it = 0; it < 2; it++) {
                    Vec3 vf = V.add(launchV(p, end, ts, g), V.DOWN, g * ts);
                    end = V.add(aim, V.unit(vf), -half * 0.55);
                }
                Vec3 v0 = launchV(p, end, ts, g);
                Vec3 u = V.unit(V.add(v0, V.DOWN, g * ts));
                s.flight = flight;
                swordArcFx(level, p, v0, half, ts, g);
                // 0.48.0 입체 빛의 검: 머리 위의 것은 사라지고 같은 포물선 (2차 곡선 — 조절점 = 처음 + 처음 속도 × T/2) 으로 날아가 꽂힌 채 남음 (빛기둥 때 흩어짐)
                WeaponFx3d.kill(level, s.halo3d, p, 0.05);
                Vec3 d0 = V.unit(v0);
                Vec3 o0 = swordOrigin(p, d0, half);
                Vec3 o1 = swordOrigin(end, u, half);
                s.fly3d = Fx3d.spawn(level, "phantom_sword", o0, Opt.of("to", o1, "ctrl", V.add(o0, v0, ts / 2), "travel", ts, "align", 1.0, "face", V.UP, "faceCam",
                        1.0, "scale", swordScale(half), "life", ts + 4.0, "color", glow, "glow", SOL_LIGHT, "glowAlpha", 0.5, "shine", 0.5, "fadeOut", 0.2));
                SkillFx.play(level, "item.trident.throw", p, 1.5 + 0.05 * s.i, 0.6);
                // 꽂힘
                Vec3 tip = end;
                Tasks.later(flight, () -> {
                    if (!ok.getAsBoolean()) return;
                    LivingEntity e = s.e;
                    stuck.add(new Stuck(e, V.sub(tip, Kit.bodyAt0(e, aim)), u, tip, half, Tasks.now(), s.fly3d));
                    swordFx(level, tip, u, half, 0.22, Opt.of("alpha", 0.4));
                    SkillFx.burst(level, aim, SOL_LIGHT, 0.8, 0.22);
                    SkillFx.sparks(level, aim, glow, 5, 5);
                    SkillFx.play(level, "item.trident.hit", aim, 1.4, 0.9);
                    SkillFx.play(level, "hit.amethyst_cluster", aim, 1.2, 1.0);
                    if (stabbed.containsKey(e.getUUID()) || !Kit.canHit(sp, e)) return;
                    stabbed.put(e.getUUID(), e);
                    Kit.hitAt(sp, e, Kit.reserveHit(sp, e, Tasks.now()), ok, () -> Kit.skillHit(sp, e, w, k.num("mult")));
                });
            });
        }
        // 마지막 검이 꽂힌 뒤 pillar 틱: 꽂힌 검이 빛기둥으로 터짐 → cross 틱 뒤 빛의 X (꽂히는 시각은 거리에 따라 달라서 마지막 검이 날아간 뒤에 정함)
        int lastLaunch = swords.get(n - 1).launch;
        Tasks.later(lastLaunch, () -> {
            if (!ok.getAsBoolean()) return;
            int lastHit = 0;
            for (Sword s : swords) lastHit = Math.max(lastHit, s.launch + (s.flight > 0 ? s.flight : 1));
            int pillarIn = lastHit + (int) k.num("pillar") - lastLaunch;
            // 꽂힌 검: 4틱마다 적의 지금 몸에 다시 그림 (걸어가도 따라감)
            for (int t = 4; t < pillarIn; t += 4) {
                final int tt = t;
                Tasks.later(t, () -> {
                    if (!ok.getAsBoolean()) return;
                    for (Stuck x : stuck) {
                        if (x.e.isRemoved() || Tasks.now() - x.since < 3) continue; // 막 꽂힌 검은 그대로 (같은 자리에 두 장 겹치지 않게)
                        x.at = V.add(Kit.bodyAt0(x.e, x.at), x.rel);
                        swordFx(level, x.at, x.dir, x.half, Math.min(0.22, (pillarIn - tt) / 20.0 + 0.02), Opt.of("alpha", 0.4));
                        if (x.fx != 0) Fx3d.move(level, x.fx, swordOrigin(x.at, x.dir, x.half), 0.18); // 0.48.0 입체 검도 적을 따라감
                    }
                });
            }
            Tasks.later(pillarIn, () -> pillars(sp, w, k, ok, spot, glow, core, stabbed, stuck));
            // 0.48.0 단계 동작: 왼손으로 움켜쥐어 빛기둥 (2틱 전) → 단검으로 허공에 X (X 참격 2틱 전)
            Kit.motionLater(sp, "solas_halo:pillar", pillarIn - 2, life);
            Kit.motionLater(sp, "solas_halo:cross", pillarIn + (int) k.num("cross") - 2, life);
        });
        return true;
    }

    /** 광휘의 검진 끝부분: 꽂힌 검이 빛기둥으로 터짐 (pillarMult) → cross 틱 뒤 조준한 곳에 빛의 X 참격 (afterMult) */
    private static void pillars(ServerPlayer sp, Weapon w, Obj k, BooleanSupplier ok, Vec3 spot, double[] glow, double[] core, Map<UUID, LivingEntity> stabbed,
            List<Stuck> stuck) {
        if (!ok.getAsBoolean()) return;
        ServerLevel level = sp.level();
        for (Stuck x : stuck) {
            if (!x.e.isRemoved()) x.at = V.add(Kit.bodyAt0(x.e, x.at), x.rel);
            swordFx(level, x.at, x.dir, x.half, 0.4, Opt.of("progress", 2.0));
            WeaponFx3d.kill(level, x.fx, x.at, 0.25); // 0.48.0 입체 검은 빛기둥 속으로 흩어짐
        }
        long t0 = Tasks.now();
        for (LivingEntity e : stabbed.values()) {
            if (e.isRemoved()) continue;
            Vec3 feet = e.position();
            Vec3 c = Kit.bodyAt0(e, spot);
            lightPillar(level, feet, glow, 1.25, true);
            SkillFx.burst(level, c, SOL_WHITE, 1.1, 0.25);
            Kit.hitAt(sp, e, Kit.reserveHit(sp, e, t0), ok, () -> Kit.skillHit(sp, e, w, k.num("pillarMult")));
        }
        SkillFx.play(level, "beacon.power", spot, 1.4, 1.2);
        SkillFx.play(level, "firework.blast", spot, 1.0, 1.2);
        // 거대한 빛의 X 참격: 조준한 곳 아래 땅 (4칸 안) 위 1.2칸 — 나를 향해 섬, 1인칭에서 화면을 다 덮지 않게 거리에 맞춘 크기
        Tasks.later((int) k.num("cross"), () -> {
            if (!ok.getAsBoolean()) return;
            Vec3 eye = sp.getEyePosition();
            Double gy = groundBelow(level, spot, 4);
            Vec3 ground = new Vec3(spot.x, gy != null ? gy : spot.y, spot.z);
            Vec3 c = V.up(ground, 1.2);
            double dist = Math.hypot(c.x - eye.x, c.z - eye.z);
            Vec3 fv = Kit.facingOf(sp);
            Vec3 dirX = dist > 1e-3 ? V.unit(new Vec3(c.x - eye.x, 0, c.z - eye.z)) : fv;
            double radius = k.num("radius");
            double big = Math.min(radius * 0.95, Math.max(1.0, dist * 0.32));
            Vec3 at = dist > 1e-3 ? c : V.add(eye, fv, 3);
            SkillFx.arc(level, at, dirX, Opt.of("shape", "heavy", "flip", false, "size", big, "life", 0.42, "speed", 1.5, "spin", 45.0, "color", glow,
                    "aura", SOL_SHADE));
            Tasks.later(1, () -> SkillFx.arc(level, V.add(at, dirX, 0.08), dirX, Opt.of("shape", "heavy", "flip", true, "size", big, "life", 0.42, "speed", 1.5,
                    "spin", -45.0, "color", glow, "aura", SOL_SHADE)));
            SkillFx.fx(level, "rpg:slash_x", V.add(at, dirX, -0.1), Opt.of("dir", dirX, "size", big * 0.45, "life", 0.3, "color", SOL_WHITE, "alpha", 1.0));
            SkillFx.burst(level, at, SOL_LIGHT, big * 0.5, 0.35);
            SkillFx.shock(level, ground, glow, radius / 2.1, 0.5);
            SkillFx.shock(level, ground, SOL_WHITE, radius / 4.2, 0.4);
            SkillFx.sparks(level, at, core, 12, 7);
            SkillFx.play(level, "firework.large_blast", spot, 1.0, 1.2);
            SkillFx.play(level, "game.player.attack.critical", spot, 0.9, 1.2);
            SkillFx.play(level, "block.bell.hit", spot, 1.3, 1.0);
            Kit.shakeNear(level, spot, 14, 0.3, 0.3);
            long t1 = Tasks.now();
            // 꽂힌 적은 밀려났어도 radius + 2.5칸 (몸 폭 여유) · 위아래 5칸 안이면
            List<LivingEntity> pushed = new ArrayList<>();
            for (LivingEntity e : stabbed.values()) {
                if (!e.isRemoved() && Math.hypot(e.getX() - spot.x, e.getZ() - spot.z) <= radius + 2.5 + Kit.bodyPad(e) && Math.abs(e.getY() - spot.y) <= 5) {
                    pushed.add(e);
                }
            }
            for (LivingEntity e : Kit.union(Kit.visibleFrom(sp, V.up(spot, 1.2), Kit.around(sp, spot, radius)), pushed)) {
                Vec3 b = Kit.bodyAt0(e, c);
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, t1), ok, () -> {
                    if (Kit.skillHit(sp, e, w, k.num("afterMult")) <= 0) return;
                    Vec3 u = V.unit(V.sub(b, sp.getEyePosition()));
                    SkillFx.cutMark(level, b, u, 45, 0.08, glow, core, 1.1);
                    SkillFx.cutMark(level, b, u, -45, 0.08, glow, core, 1.1);
                });
            }
        });
    }
}
