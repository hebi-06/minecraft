package mmorpg.fabric.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import mmorpg.core.Obj;
import mmorpg.core.Weapon;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 그람 (칼) — 화룡 돌진 · 용익 강하 (skills.js v2.17.0 "불꽃 · 용": 불꽃 날개를 펼쳐 떠올랐다 내려찍기). 수치는 SKILLS.sword.
 */
final class Gram {
    private Gram() {}

    static final double[] G_FIRE = {1.0, 0.45, 0.08};
    static final double[] G_EMBER = {1.0, 0.62, 0.2};
    static final double[] G_DEEP = {0.9, 0.18, 0.04};
    static final double[] G_ASH = {0.16, 0.08, 0.06};

    static void register() {
        Transcend.register("gram_charge", Gram::charge);
        Transcend.register("gram_descent", Gram::descent);
    }

    // ------------------------------------------------------------ 그림 도구

    /** 불꽃 한 장 (rpg:fire_wall — 밑동이 at, 서서 일렁임) */
    static void flame(ServerLevel level, Vec3 at, double size, double life, double[] color, double alpha) {
        SkillFx.fx(level, "rpg:fire_wall", at, Opt.of("size", size, "life", life, "color", color, "alpha", alpha, "spin", Kit.rand() * 360));
    }

    static void flame(ServerLevel level, Vec3 at, double size, double life, double[] color) {
        flame(level, at, size, life, color, 0.95);
    }

    /** 불티 (rpg:ember — 넓이 size 안에서 life 초 동안 위로 날림) */
    static void embers(ServerLevel level, Vec3 at, double size, double life, double speed) {
        SkillFx.fx(level, "rpg:ember", V.up(at, 0.3), Opt.of("size", size, "life", life, "speed", speed, "color", G_EMBER, "alpha", 0.9));
    }

    /** 땅이 갈라져 그을림 (rpg:ground_crack) */
    static void scorch(ServerLevel level, Vec3 at, double size, double life) {
        SkillFx.fx(level, "rpg:ground_crack", V.up(at, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", size, "life", life, "color", G_ASH,
                "alpha", 0.9));
    }

    /** a → b 곧은 빛 한 줄 (rpg:arrow_trail 하나) */
    static void streakLine(ServerLevel level, Vec3 a, Vec3 b, double[] color, double thick, double life, double alpha, double delay) {
        Vec3 d = V.sub(b, a);
        double len = V.len(d);
        if (len < 0.05) return;
        SkillFx.fx(level, "rpg:arrow_trail", V.add(a, d, 0.5), Opt.of("dir", V.unit(d), "size", len / 2, "thick", thick, "life", life + delay, "delay", delay,
                "color", color, "alpha", alpha));
    }

    /** 범위 고리: 안쪽에서 퍼져 반지름 r 에서 사라짐 (shock_ring 은 처음 크기의 약 2.1배까지 퍼짐) */
    static void zonePulse(ServerLevel level, Vec3 feet, double[] color, double r, double life) {
        SkillFx.shock(level, feet, color, r / 2.1, life);
    }

    /**
     * 불꽃 날개 (rpg:fire_wing — 등 뒤, 보는 방향에 수직인 면 한 장, 너비 2 × size 칸): frame 0~3 펼침 · 4~7 날갯짓.
     * tips: 날개 끝 · 팔에 작은 불꽃 (옆에서 보면 그림이 얇아서)
     */
    static void fireWings(ServerPlayer sp, int frame, double life, boolean tips, double size) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 at = V.up(V.add(sp.position(), f, -0.42), 1.3);
        SkillFx.fx(level, "rpg:fire_wing", at, Opt.of("dir", f, "size", size, "life", life, "progress", (frame + 0.5) / 8, "color", SkillFx.WHITE, "alpha", 0.97));
        if (!tips) return;
        Vec3 side = V.rightOf(f);
        double lift = frame == 4 ? 0.9 : frame == 6 ? -0.2 : 0.45;
        for (int s : new int[] {-1, 1}) {
            flame(level, V.up(V.add(at, side, s * size * 0.82), lift - 0.35), 0.9, life + 0.08, G_FIRE, 0.95);
            flame(level, V.up(V.add(at, side, s * size * 0.4), lift * 0.6), 0.75, life + 0.08, G_EMBER, 0.9);
        }
    }

    /** 돌진 길 위 거리 s 의 자리 (높이는 그 앞 설 자리의 땅) */
    private static Vec3 onPath(List<Kit.Spot> spots, Vec3 from, Vec3 f, double s) {
        double y = from.y;
        for (Kit.Spot x : spots) {
            if (x.s() > s + 0.25) break;
            y = x.p().y;
        }
        Vec3 q = V.add(from, f, s);
        return new Vec3(q.x, y, q.z);
    }

    private static List<LivingEntity> lineEnemies(ServerPlayer sp, Vec3 from, Vec3 f, double len, double width) {
        List<LivingEntity> out = new ArrayList<>();
        if (len > 0.3) for (Kit.LineHit h : Kit.lineHits(sp, V.up(from, 1.0), f, len, width)) out.add(h.e());
        return out;
    }

    /** 벤 적 중 아직 있고 중심에서 r + 2.5칸 (몸 폭 여유) · 위아래 dy 칸 안인 것 (밀려났어도 폭발이 따라감) */
    private static List<LivingEntity> pushedNear(Map<UUID, LivingEntity> struck, Vec3 c, double r, double dy) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : struck.values()) {
            if (!e.isRemoved() && Math.hypot(e.getX() - c.x, e.getZ() - c.z) <= r + 2.5 + Kit.bodyPad(e) && Math.abs(e.getY() - c.y) <= dy) out.add(e);
        }
        return out;
    }

    // ------------------------------------------------------------ 스킬 1 화룡 돌진

    /**
     * 화룡 돌진: 보는 쪽으로 dash 칸 돌진 — 땅을 따라 (한 칸 오르내림), 벽 · 낭떠러지 · 물 · 용암 · 불 앞에서 멈춤.
     * 돌진 길에 몸이 걸치는 첫 적은 그 몸 앞 stop 칸에서 멈춤, 옆에 선 적은 지나치며 벰. 지나온 길에 불꽃 →
     * 멈춘 자리 앞 cone 도 · range 칸 (눈에서 보이는 적) 과 지나온 길 (폭 width) 의 적을 벰 (mult, 밀쳐냄) →
     * delay 틱 뒤 지나온 길이 차례로 불기둥이 되어 터지고, 앞쪽 burst 칸이 폭발 (afterMult, 적마다 한 번 — 벤 적은 밀려났어도).
     * 속박 중이거나 무엇이든 탄 동안은 제자리에서 벰
     */
    static boolean charge(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double[][] cs = Kit.colors(w);
        double[] glow = cs[0];
        double[] core = cs[1];
        // 길은 땅 높이에서: 뛰는 중에 써도 발밑 땅 (3칸 안) 기준
        Double g = Kit.solidBelow(level, V.up(start, 0.3), 3.3);
        Vec3 from = new Vec3(start.x, g == null ? start.y : Math.min(start.y, g), start.z);
        double free = Kit.reachFlat(level, from, f, k.num("dash"));
        double dist = Status.isRooted(sp) || Kit.ridingAny(sp) ? 0 : free;
        if (dist > 0.2) {
            for (Kit.LineHit h : Kit.lineHits(sp, V.up(from, 1.0), f, dist, k.num("width"))) {
                // 몸이 돌진 길 (내 몸 반폭 0.3 + 몸 반폭 0.4 + 넓은 만큼) 에 걸치는 앞쪽 적: 그 몸 가장자리 앞에서 멈춤
                LivingEntity e = h.e();
                double pad = Kit.bodyPad(e);
                double dx = e.getX() - from.x;
                double dz = e.getZ() - from.z;
                double along = dx * f.x + dz * f.z;
                double side = Math.abs(dx * f.z - dz * f.x);
                if (along > 0.2 && side <= 0.7 + pad) dist = Math.min(dist, Math.max(0, along - k.num("stop") - pad));
            }
        }
        List<Kit.Spot> spots = dist > 0.2 ? Kit.dashSpots(level, from, f, dist) : List.of();
        Vec3 at0 = start;
        double len0 = 0;
        for (int i = spots.size() - 1; i >= 0; i--) {
            // 가장 먼 설 자리부터 (몸이 블록과 겹쳐 못 옮기면 그 앞 자리)
            if (Kit.moveTo(sp, spots.get(i).p())) {
                at0 = spots.get(i).p();
                len0 = spots.get(i).s();
                break;
            }
        }
        final Vec3 at = at0;
        final double len = len0;
        // 출발: 발밑 불꽃 + 불티
        SkillFx.burst(level, V.up(from, 0.9), G_FIRE, 1.3, 0.3);
        SkillFx.shock(level, from, G_FIRE, 1.3, 0.4);
        embers(level, from, 1.6, 0.5, 2.5);
        // 지나온 길: 불빛 줄기 (빛 + 흰 심지) + 남는 불꽃 + 잔상 참격
        if (len > 0.3) {
            SkillFx.beam(level, V.up(from, 1.0), V.up(at, 1.0), glow, Opt.of("half", 0.55, "thick", 0.26, "life", 0.45, "sweep", 0.08, "gap", 0.8));
            SkillFx.beam(level, V.up(from, 1.0), V.up(at, 1.0), core, Opt.of("half", 0.5, "thick", 0.09, "life", 0.35, "sweep", 0.08, "gap", 0.8));
            for (double s = 0.5; s <= len; s += 0.9) flame(level, onPath(spots, from, f, s), 1.1, 0.8 + (s / len) * 0.3, G_FIRE, 0.9);
            for (double s = 1.2; s < len - 0.6; s += 2.2) {
                SkillFx.arc(level, V.up(onPath(spots, from, f, s), 1.05), f, Opt.of("shape", "heavy", "flip", Math.round(s / 2.2) % 2 == 1, "size", 1.5, "life", 0.22,
                        "color", glow, "aura", Kit.aura(), "spin", -25.0, "alpha", 0.75));
            }
        }
        // 부딪힘: 앞에 거대한 불꽃 초승달 둘 (X) — 1인칭 화면을 다 덮지 않게 2.2칸 앞
        SkillFx.arc(level, V.up(V.add(at, f, 2.2), 1.1), f, Opt.of("shape", "heavy", "flip", false, "size", 2.4, "life", 0.28, "speed", 1.5, "color", glow,
                "aura", Kit.aura(), "spin", -18.0));
        Tasks.later(2, () -> {
            if (ok.getAsBoolean()) {
                SkillFx.arc(level, V.up(V.add(sp.position(), f, 2.3), 1.1), f, Opt.of("shape", "heavy", "flip", true, "size", 2.2, "life", 0.26, "speed", 1.5,
                        "color", G_EMBER, "aura", Kit.aura(), "spin", 22.0));
            }
        });
        SkillFx.play(level, "item.trident.riptide_3", start, 1.1, 0.9);
        SkillFx.play(level, "mob.blaze.shoot", at, 0.9, 0.8);
        SkillFx.play(level, "fire.ignite", at, 1.0, 0.9);
        SkillFx.shake(sp, 0.18, 0.15);
        // 벨 적: 앞쪽 부채꼴 (눈에서 보이는) + 지나온 길
        Vec3 eye = V.up(at, 1.62);
        double half = Math.toRadians(k.num("cone") / 2);
        List<LivingEntity> inCone = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, at, k.num("range"))) if (Kit.bodyInCone(e, at, f, half)) inCone.add(e);
        List<LivingEntity> cone = Kit.visibleFrom(sp, eye, inCone);
        List<LivingEntity> path = lineEnemies(sp, from, f, len, k.num("width"));
        long now = Tasks.now();
        List<LivingEntity> hitList = Kit.union(cone, path);
        // 벤 적 (밀려나 폭발 범위 밖으로 나가도 폭발이 따라감)
        Map<UUID, LivingEntity> struck = new LinkedHashMap<>();
        for (LivingEntity e : hitList) {
            Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now), ok, () -> {
                if (Kit.skillHit(sp, e, w, k.num("mult")) <= 0) return;
                struck.put(e.getUUID(), e);
                Vec3 c = Kit.bodyAt0(e, eye);
                double near = Math.min(1, Math.max(0.5, V.len(V.sub(c, eye)) / 3)); // 눈 가까이의 적은 작게 (1인칭)
                SkillFx.fx(level, "rpg:slash_x", c, Opt.of("dir", f, "size", 1.2 * near, "life", 0.3, "color", glow, "alpha", 1.0));
                SkillFx.fx(level, "rpg:slash_x", V.add(c, f, -0.04), Opt.of("dir", f, "size", 0.85 * near, "life", 0.25, "color", core, "alpha", 1.0));
                SkillFx.burst(level, c, G_FIRE, 0.8, 0.25);
                Kit.knockback(e, f.x * k.num("knock"), f.z * k.num("knock"), 0.25);
            });
        }
        if (!hitList.isEmpty()) SkillFx.play(level, "game.player.attack.strong", at, 0.9, 0.9);
        // delay 틱 뒤: 지나온 길이 앞쪽으로 차례로 터지고 (불기둥) → 앞쪽 폭발 (afterMult). 폭발 자리는 벽을 넘지 않게
        Vec3 center = V.add(len > 0 ? at : from, f, Math.max(0, Math.min(1.6, free + 0.3 - len)));
        int n = Math.max(0, (int) Math.floor(len / 1.1));
        int delay = (int) k.num("delay");
        for (int i = 0; i < n; i++) {
            Vec3 p = onPath(spots, from, f, 0.6 + i * 1.1);
            int t = (int) Math.max(1, delay - 4 + Math.round(((double) i / Math.max(1, n)) * 4));
            Tasks.later(t, () -> {
                if (!ok.getAsBoolean()) return;
                flame(level, p, 2.4, 0.55, G_DEEP);
                SkillFx.burst(level, V.up(p, 0.6), G_FIRE, 0.7, 0.22);
            });
        }
        double burst = k.num("burst");
        Tasks.later(delay, () -> {
            if (!ok.getAsBoolean()) return;
            SkillFx.burst(level, V.up(center, 0.9), G_FIRE, 2.4, 0.4);
            zonePulse(level, center, G_FIRE, burst, 0.5);
            for (int j = 0; j < 7; j++) flame(level, V.add(center, V.turnY(f, j * (360.0 / 7) + 10), 1.9), 2.3, 0.6, j % 2 == 1 ? G_DEEP : G_FIRE);
            flame(level, center, 3.0, 0.55, G_FIRE);
            scorch(level, center, burst * 1.4, 1.2);
            embers(level, center, 3.5, 0.8, 3);
            SkillFx.sparks(level, V.up(center, 0.6), core, 10, 5);
            SkillFx.play(level, "random.explode", center, 0.8, 1.0);
            SkillFx.play(level, "mob.blaze.shoot", center, 1.0, 0.6);
            SkillFx.shake(sp, 0.22, 0.2);
            long t0 = Tasks.now();
            List<LivingEntity> pathNow = lineEnemies(sp, from, f, len, k.num("width"));
            List<LivingEntity> blast = Kit.visibleFrom(sp, V.up(center, 1.2), Kit.around(sp, center, burst));
            for (LivingEntity e : Kit.union(blast, pathNow, pushedNear(struck, center, burst, 4))) {
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, t0), ok, () -> {
                    if (Kit.skillHit(sp, e, w, k.num("afterMult")) > 0) flame(level, !e.isRemoved() ? e.position() : center, 1.6, 0.45, G_FIRE);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 스킬 2 용익 강하

    /**
     * q 의 수직선에서 top 아래 · lowest 위의 첫 단단한 땅에 설 자리. strict 면 standSpot 조건 (물 · 용암 · 불 · 마그마 · 선인장 · 머리 위 막힘이면 안 됨).
     * top - 0.7 보다 높은 땅은 안 됨 (지금보다 높은 곳으로 "내려찍지" 않게)
     */
    static Vec3 landOn(Level level, Vec3 q, double top, double lowest, boolean strict) {
        Double gy = Kit.solidBelow(level, new Vec3(q.x, top, q.z), top - lowest);
        if (gy == null || gy > top - 0.7) return null;
        return strict ? Kit.standSpot(level, new Vec3(q.x, gy, q.z), 0.6) : new Vec3(q.x, gy, q.z);
    }

    /**
     * 내려찍을 곳 (지금 높이에서 잼): 조준한 몹의 바로 앞 → 시선이 닿은 곳에서 반 칸 앞 (가로 reach 칸까지) 의 설 자리.
     * 그곳이 설 수 없거나 가는 길이 막혔으면 바로 아래, 그다음 쓴 자리 (base — 2칸 안 · 막힘 없을 때만),
     * 마지막으로 바로 아래의 단단한 땅 (불 · 마그마여도). 없으면 null
     */
    static Vec3 descentSpot(ServerPlayer sp, Obj k, Vec3 base) {
        Level level = sp.level();
        Vec3 p = sp.position();
        double top = p.y + 1.0;
        double lowest = Math.min(base.y, p.y) - 6;
        Predicate<Vec3> clear = q -> Kit.clearPath(level, V.up(p, 0.4), V.up(q, 0.4)) && Kit.clearPath(level, V.up(p, 1.5), V.up(q, 1.5));
        double reach = k.num("reach");
        Vec3 q;
        RpgCombat.Aim r = RpgCombat.aimTarget(sp, reach + 8, Kit.BOW.numOr("aimAssist", 0));
        if (r.target() != null && !r.target().isRemoved() && Kit.canHit(sp, r.target())) {
            // 조준한 몹: 그 바로 앞 (몸과 겹치지 않게 — 몸 반폭 + 0.9칸 앞)
            Vec3 t = r.target().position();
            double hd = Math.hypot(t.x - p.x, t.z - p.z);
            double back = Math.min(hd, Kit.bodyPad(r.target()) + 0.9);
            q = hd > 1e-6 ? new Vec3(t.x - (t.x - p.x) / hd * back, t.y, t.z - (t.z - p.z) / hd * back) : t;
        } else {
            // 시선이 닿은 곳에서 가로로 반 칸 앞 (벽 · 블록 가장자리에 몸이 겹치지 않게). 바로 아래를 보면 그대로
            Vec3 dir = r.dir();
            Vec3 hit = V.add(sp.getEyePosition(), dir, r.dist());
            q = Math.hypot(dir.x, dir.z) > 0.2 ? V.add(hit, V.flat(dir), -0.45) : hit;
        }
        double dx = q.x - p.x;
        double dz = q.z - p.z;
        double h = Math.hypot(dx, dz);
        if (h > reach) q = new Vec3(p.x + dx / h * reach, q.y, p.z + dz / h * reach);
        Vec3 aim = landOn(level, q, top, lowest, true);
        if (aim != null && clear.test(aim)) return aim;
        Vec3 below = landOn(level, p, top, lowest, true);
        if (below != null) return below;
        if (Math.hypot(base.x - p.x, base.z - p.z) <= 2) {
            Vec3 b = landOn(level, base, top, lowest, true);
            if (b != null && clear.test(b)) return b;
        }
        return landOn(level, p, top, lowest, false);
    }

    /** 떠 있는 동안의 상태: 마지막으로 본 자리 (한순간에 옮겨졌는지), 끊김, 이 스킬이 건 느린 낙하 */
    private static final class Flight {
        Vec3 last;
        boolean broken;
        boolean slow;

        Flight(Vec3 last) {
            this.last = last;
        }
    }

    /**
     * 용익 강하: 발밑에 단단한 땅이 있어야 씀 (물 · 용암 · 허공 · 속박 · 탄 동안이면 재사용 대기 없이 거절).
     * 불꽃 날개를 펴며 떠오름 (세로 rise, 약 4칸) → 꼭대기 (apex 틱) 부터 hover 틱까지 머묾 (느린 낙하) — 내려찍을 곳을 불꽃 룬으로 보임 (조준을 따라감) →
     * dive 틱에 걸쳐 그곳 (가로 reach 칸 안) 으로 내려찍음 → 반지름 radius 칸 폭발 (mult, 띄움) → delay 틱 뒤 불꽃 고리 (afterMult).
     * 떠오른 동안 · 내려찍은 뒤 3초는 낙하 피해 없음. 떠 있는 동안 침묵 · 속박 · 탐 · 한순간에 2.5칸 넘게 옮겨짐이면 내려찍지 않음
     */
    static boolean descent(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        Vec3 c0 = sp.position();
        String name = k.str("name");
        if (Status.isRooted(sp)) {
            Notify.warn(sp, "§c" + name + ": §7속박된 동안에는 떠오를 수 없습니다");
            return false;
        }
        if (Kit.ridingAny(sp)) {
            Notify.warn(sp, "§c" + name + ": §7탄 것에서 내려야 떠오를 수 있습니다");
            return false;
        }
        Double g0 = Kit.solidBelow(level, V.up(c0, 0.5), 4.5);
        if (g0 == null) {
            Notify.warn(sp, "§c" + name + ": §7발밑에 단단한 땅이 있어야 합니다");
            return false;
        }
        Vec3 base = new Vec3(c0.x, g0, c0.z);
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = Kit.colors(w);
        double[] glow = cs[0];
        double[] core = cs[1];
        int diveAt = (int) k.num("hover");
        int dive = (int) k.num("dive");
        int landAt = diveAt + dive - 1;
        int apex = (int) Math.min(k.numOr("apex", 9), diveAt - 1);
        Kit.noFall(sp, landAt + 60);
        Kit.launch(sp, 0, k.num("rise"), 0);
        Flight st = new Flight(c0);
        Runnable dropSlow = () -> {
            if (!st.slow) return;
            st.slow = false;
            sp.removeEffect(MobEffects.SLOW_FALLING);
        };
        Consumer<String> fizzle = why -> {
            st.broken = true;
            dropSlow.run();
            Vec3 l = sp.position();
            SkillFx.burst(level, V.up(l, 0.9), G_ASH, 1.0, 0.3);
            embers(level, l, 1.2, 0.5, 1);
            SkillFx.play(level, "random.fizz", l, 0.9, 0.8);
            Notify.warn(sp, "§c" + name + " §7— " + why);
        };
        BooleanSupplier held = () -> Status.isRooted(sp) || Status.isSilenced(sp) || Kit.ridingAny(sp);
        // 떠 있는 동안 매 틱: 끊겼으면 (침묵 · 속박 · 탐 · 2.5칸 넘게 옮겨짐) true
        BooleanSupplier interrupted = () -> {
            if (st.broken) return true;
            Vec3 l = sp.position();
            boolean jumped = Math.hypot(l.x - st.last.x, l.z - st.last.z) > 2.5 || Math.abs(l.y - st.last.y) > 3;
            st.last = l;
            if (!jumped && !held.getAsBoolean()) return false;
            fizzle.accept("끊김");
            return true;
        };
        // 펼침: 발밑 불꽃 폭발 · 고리 · 불티
        SkillFx.burst(level, V.up(c0, 0.3), G_FIRE, 1.4, 0.3);
        zonePulse(level, base, G_FIRE, 3.2, 0.45);
        for (int j = 0; j < 6; j++) flame(level, V.add(base, V.turnY(new Vec3(1, 0, 0), j * 60), 1.6), 1.5, 0.5, j % 2 == 1 ? G_DEEP : G_FIRE);
        embers(level, base, 3, 0.9, 3.5);
        SkillFx.play(level, "mob.enderdragon.flap", c0, 1.4, 0.8);
        SkillFx.play(level, "mob.blaze.shoot", c0, 1.0, 0.7);
        SkillFx.play(level, "fire.ignite", c0, 1.2, 0.8);
        // 날개: 지금 자리에 (따라 움직임) — 떠오르는 0~9틱은 매 틱, 그 뒤 2틱마다. 0~6틱 펼침, 머무는 동안 날갯짓.
        // 1인칭 (날개는 등 뒤라 화면 밖): 펼칠 때 화면 양 끝에 불꽃 초승달 · 머무는 동안 눈앞으로 떨어지는 불꽃 깃털
        for (int t = 0; t < diveAt; t++) {
            final int tt = t;
            Runnable tick = () -> {
                if (!ok.getAsBoolean() || interrupted.getAsBoolean()) return;
                if (tt == apex) {
                    // 꼭대기: 느린 낙하 (이미 더 오래 걸린 느린 낙하가 있으면 그대로)
                    MobEffectInstance cur = sp.getEffect(MobEffects.SLOW_FALLING);
                    if (cur == null || cur.getDuration() < diveAt - apex + 2) {
                        sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, diveAt - apex + 2, 0, false, false));
                        st.slow = true;
                    }
                }
                if (tt >= 10 && tt % 2 == 1) return;
                int frame = tt < 8 ? Math.min(3, tt / 2) : 4 + ((tt - 8) / 2) % 4;
                fireWings(sp, frame, tt < 10 ? 0.08 : 0.13, tt >= 6 && tt % 4 == 2, 3.6);
                Vec3 c = sp.position();
                if (tt % 2 == 0) streakLine(level, V.up(c, 0.2), V.up(c, -1.3), G_FIRE, 0.22, 0.13, 0.9, 0); // 몸 아래로 흐르는 불꽃
                if (tt % 4 == 0) embers(level, V.up(c, 0.6), 1.2, 0.5, 1.5);
                if (tt == 8 || tt == 14) SkillFx.play(level, "mob.enderdragon.flap", c, 1.1, 0.9);
                Vec3 fv = Kit.facingOf(sp);
                if (tt == 2 || tt == 4) {
                    Vec3 eye = sp.getEyePosition();
                    Vec3 sd = V.rightOf(fv);
                    for (int s : new int[] {-1, 1}) {
                        SkillFx.arc(level, V.up(V.add(V.add(eye, fv, 1.3), sd, s * (tt == 2 ? 1.25 : 1.5)), -0.15), fv, Opt.of("shape", "heavy", "flip", s < 0,
                                "size", 1.3, "life", 0.2, "speed", 1.0, "color", G_FIRE, "aura", Kit.aura(), "spin", s * 70.0, "alpha", 0.85));
                    }
                }
                if (tt >= 6 && tt % 4 == 2) {
                    for (int j = 0; j < 2; j++) {
                        Vec3 p = V.add(V.add(c, V.turnY(fv, (j == 1 ? 1 : -1) * (18 + Kit.rand() * 22)), 1.8 + Kit.rand()), V.UP, 2.6 + Kit.rand() * 0.6);
                        SkillFx.fx(level, "rpg:arrow_spark", p, Opt.of("dir", V.DOWN, "speed", 1.2, "size", 0.14, "grow", 0.8, "life", 0.9, "color",
                                j == 1 ? core : G_FIRE, "alpha", 1.0));
                    }
                }
            };
            if (t == 0) tick.run();
            else Tasks.later(t, tick);
        }
        // 내려찍을 곳 보이기 (조준을 따라감)
        double radius = k.num("radius");
        for (int t = 6; t < diveAt; t += 3) {
            Tasks.later(t, () -> {
                if (!ok.getAsBoolean() || st.broken) return;
                Vec3 spot = descentSpot(sp, k, base);
                if (spot == null) return;
                SkillFx.rune(level, spot, Opt.of("size", radius * 0.95, "life", 0.2, "color", G_FIRE, "alpha", 0.85, "rate", 90.0));
                SkillFx.shock(level, spot, G_EMBER, 0.9, 0.2);
            });
        }
        // 내려찍기
        Tasks.later(diveAt, () -> {
            if (!ok.getAsBoolean() || interrupted.getAsBoolean()) return;
            dropSlow.run();
            Vec3 from = sp.position();
            Vec3 land = descentSpot(sp, k, base);
            if (land == null) {
                fizzle.accept("내려찍을 곳이 없습니다");
                return;
            }
            Vec3 fd = V.sub(land, from);
            // 떨어지는 길: 굵은 불빛 줄기 + 흰 심지. 내려치는 초승달은 내려앉을 곳 앞
            streakLine(level, V.up(from, 1.0), V.up(land, 1.0), G_FIRE, 0.7, 0.3, 0.9, 0);
            streakLine(level, V.up(from, 1.0), V.up(land, 1.0), core, 0.22, 0.25, 1, 0);
            Vec3 fv = Kit.facingOf(sp);
            SkillFx.arc(level, V.up(V.add(land, fv, 1.8), 1.2), V.unit(V.add(fv, V.DOWN, 0.9)), Opt.of("shape", "heavy", "flip", false, "size", 2.4, "life", 0.22,
                    "speed", 2.0, "color", glow, "aura", Kit.aura(), "spin", 90.0));
            SkillFx.play(level, "item.trident.riptide_3", from, 1.2, 0.7);
            for (int i = 1; i <= dive; i++) {
                final int ii = i;
                Runnable step = () -> {
                    if (!ok.getAsBoolean() || st.broken) return;
                    if (ii > 1 && held.getAsBoolean()) {
                        fizzle.accept("끊김");
                        return;
                    }
                    boolean last = ii == dive;
                    boolean moved = Kit.moveTo(sp, last ? land : V.add(from, fd, (double) ii / dive)); // 가운데 자리가 막혔으면 건너뜀
                    if (!last) {
                        fireWings(sp, ii == 1 ? 2 : 1, 0.08, false, 3.6);
                        return;
                    }
                    if (!moved) {
                        // 내려앉을 곳이 막혔으면 (몸이 블록과 겹침) 지금 자리 바로 아래
                        Vec3 pl = sp.position();
                        Vec3 alt = landOn(level, pl, pl.y + 1.0, Math.min(base.y, pl.y) - 6, true);
                        if (alt == null) alt = landOn(level, pl, pl.y + 1.0, Math.min(base.y, pl.y) - 6, false);
                        if (alt == null || !Kit.moveTo(sp, alt)) {
                            fizzle.accept("내려찍을 곳이 막혔습니다");
                            return;
                        }
                    }
                    impact(sp, w, k, ok);
                };
                if (i == 1) step.run();
                else Tasks.later(i - 1, step);
            }
        });
        return true;
    }

    /** 내려찍은 순간: 반지름 radius 칸 폭발 (mult, 띄움) → delay 틱 뒤 불꽃 고리 (afterMult — 띄운 적은 반지름 + 2.5칸 안이면 밀려났어도) */
    static void impact(ServerPlayer sp, Weapon w, Obj k, BooleanSupplier ok) {
        ServerLevel level = sp.level();
        Vec3 pl = sp.position();
        Double sb = Kit.solidBelow(level, V.up(pl, 0.6), 3);
        Vec3 c = new Vec3(pl.x, Math.min(pl.y, sb != null ? sb : pl.y), pl.z); // 반 블록 위에 내려앉아도 그 높이
        double[] core = Kit.colors(w)[1];
        Vec3 f = Kit.facingOf(sp);
        double r = k.num("radius");
        Kit.noFall(sp, 60);
        // 폭발: 앞쪽 번쩍임 · 불꽃 고리 둘 · 땅의 불 룬과 금 · 내려친 날개 · 둘레 불기둥 두 겹 · 불티
        SkillFx.burst(level, V.up(V.add(c, f, 2.2), 0.6), G_FIRE, 2.6, 0.4);
        SkillFx.burst(level, V.up(c, 0.25), core, 1.6, 0.3);
        zonePulse(level, c, G_FIRE, r, 0.55);
        zonePulse(level, c, core, r * 0.5, 0.4);
        SkillFx.rune(level, c, Opt.of("size", r * 0.95, "life", 1.0, "color", G_FIRE, "alpha", 0.9, "grow", 0.15, "rate", -60.0));
        scorch(level, c, r * 1.6, 1.6);
        fireWings(sp, 6, 0.3, false, 4.2); // 내려친 날개 (3인칭)
        // 둘레 불기둥 두 겹 (안쪽 0.6 · 바깥 0.95 × 반지름, 바깥은 2틱 늦게)
        for (int j = 0; j < 8; j++) flame(level, V.add(c, V.turnY(f, j * 45 + 22.5), r * 0.6), 1.8, 0.7, j % 2 == 1 ? G_DEEP : G_FIRE);
        for (int j = 0; j < 8; j++) {
            Vec3 d = V.turnY(f, j * 45);
            Tasks.later(2, () -> flame(level, V.add(c, d, r * 0.95), 1.4, 0.6, G_FIRE));
        }
        embers(level, c, r * 1.2, 1.0, 3.5);
        SkillFx.sparks(level, V.up(c, 0.5), core, 12, 6);
        SkillFx.play(level, "mace.heavy_smash_ground", c, 1.0, 0.8);
        SkillFx.play(level, "random.explode", c, 1.0, 0.7);
        SkillFx.play(level, "mob.enderdragon.growl", c, 0.6, 1.3);
        Kit.shakeNear(level, c, 12, 0.35, 0.35);
        // 피해 (띄움)
        long now = Tasks.now();
        Vec3 eye = V.up(c, 1.4);
        Map<UUID, LivingEntity> struck = new LinkedHashMap<>();
        for (LivingEntity e : Kit.visibleFrom(sp, eye, Kit.around(sp, c, r))) {
            Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now), ok, () -> {
                if (Kit.skillHit(sp, e, w, k.num("mult")) <= 0) return;
                struck.put(e.getUUID(), e);
                SkillFx.burst(level, Kit.bodyAt0(e, eye), G_FIRE, 0.9, 0.25);
                Vec3 d = V.flat(V.sub(e.position(), c));
                Kit.knockback(e, d.x * 0.35, d.z * 0.35, 0.55);
            });
        }
        // delay 틱 뒤: 바깥으로 퍼지는 불꽃 고리 + 적의 발밑에서 솟는 불기둥 (afterMult)
        Tasks.later((int) k.num("delay"), () -> {
            if (!ok.getAsBoolean()) return;
            zonePulse(level, c, G_DEEP, r, 0.45);
            zonePulse(level, c, G_FIRE, r * 0.7, 0.4);
            for (int j = 0; j < 10; j++) {
                Vec3 d = V.turnY(f, j * 36 + 18);
                double ring = r * (0.8 + 0.15 * (j % 2));
                double[] color = j % 2 == 1 ? G_DEEP : G_FIRE;
                Tasks.later(j % 3, () -> flame(level, V.add(c, d, ring), 1.3, 0.5, color));
            }
            SkillFx.play(level, "mob.blaze.shoot", c, 1.0, 0.5);
            SkillFx.play(level, "fire.fire", c, 1.0, 0.8);
            long t0 = Tasks.now();
            for (LivingEntity e : Kit.union(Kit.visibleFrom(sp, eye, Kit.around(sp, c, r)), pushedNear(struck, c, r, 5))) {
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, t0), ok, () -> {
                    if (Kit.skillHit(sp, e, w, k.num("afterMult")) > 0) {
                        Vec3 at = !e.isRemoved() ? e.position() : c;
                        boolean near = Math.hypot(at.x - sp.getX(), at.z - sp.getZ()) < 2.4; // 1인칭: 몸 가까이는 작게
                        flame(level, at, near ? 0.9 : 1.6, 0.5, G_FIRE);
                        embers(level, at, 1.0, 0.5, 3);
                    }
                });
            }
        });
    }
}
