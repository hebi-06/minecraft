package mmorpg.fabric.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import mmorpg.core.Obj;
import mmorpg.core.Weapon;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 대지의 심판 (영웅 도끼) — 심판의 균열 (10단계, 자바 전용). 수치는 HERO_SKILLS.axe, 색은 디자인의 주황빛 날 · 갈색 자루 (바위).
 * 도끼로 앞 start 칸의 땅을 내리침 → 그곳에서 보는 쪽 (수평) 으로 균열이 한 틱에 한 칸씩 range 칸까지 뻗음 (땅을 따라 한 칸 오르내림 —
 * 벽 · 낭떠러지 · 물 · 용암 앞에서 멈춤): 지나는 자리마다 바위 가시가 솟고 폭 width 칸 안의 적을 띄움 (mult — 적마다 한 번, 위로 lift) →
 * 균열이 다 뻗은 뒤 delay 틱에 갈라진 틈에서 용암이 차례로 분출 (afterMult — 균열 폭 안의 적, 띄운 적은 밀려났어도 2.5칸 안이면).
 * 앞이 바로 막혀 균열이 뻗지 못하면 내리친 자리에서만. 대상이 없어도 씀. 쓰러지거나 차원을 옮기면 남은 것은 멈춤
 */
final class EarthJudgment {
    private EarthJudgment() {}

    /** 그을린 땅 */
    static final double[] ASH = {0.2, 0.12, 0.08};

    static void register() {
        Transcend.register("earth_fissure", EarthJudgment::fissure);
    }

    static boolean fissure(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = HeroKit.colors(k);
        double[] glow = cs[0];
        double[] core = cs[1];
        double[] rock = HeroKit.rgbOr(k, "rock", RuneKit.ROCK);
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Double g = Kit.solidBelow(level, V.up(start, 0.3), 3.3);
        Vec3 from = new Vec3(start.x, g == null ? start.y : Math.min(start.y, g), start.z);
        // 균열 자리: 한 칸마다 (start, start + 1, …) 의 설 자리 — Kit.dashSpots (0.5칸마다, 끊기는 데까지) 에서 가장 가까운 것 (0.26칸 안 — 없으면 거기서 끝)
        double first = k.num("start");
        double range = k.num("range");
        List<Kit.Spot> spots = Kit.dashSpots(level, from, f, range);
        List<Vec3> path = new ArrayList<>();
        for (double want = first; want <= range + 0.01; want += 1) {
            Vec3 best = null;
            double bd = 0.26;
            for (Kit.Spot x : spots) {
                double d = Math.abs(x.s() - want);
                if (d <= bd) {
                    bd = d;
                    best = x.p();
                }
            }
            if (best == null) break;
            path.add(best);
        }
        boolean blocked = path.isEmpty();
        if (blocked) path.add(V.add(from, f, 0.6)); // 바로 앞이 막힘: 발 앞에서만
        Vec3 p0 = path.get(0);
        // 내리침: 내려치는 초승달 · 땅 금 · 고리 · 튀는 돌 · 쿵
        SkillFx.arc(level, V.add(V.up(sp.getEyePosition(), -0.2), f, 1.5), V.unit(V.add(f, V.DOWN, 0.9)), Opt.of("shape", "heavy", "flip", false, "size", 2.0,
                "life", 0.2, "speed", 1.6, "color", glow, "aura", core, "spin", 90.0));
        SkillFx.burst(level, V.up(p0, 0.3), glow, 1.5, 0.3);
        SkillFx.shock(level, p0, rock, 1.3, 0.45);
        SkillFx.fx(level, "rpg:ground_crack", V.up(p0, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", 2.6, "life", 1.6, "color", ASH, "alpha", 0.9));
        RuneKit.rockBurst(level, V.up(p0, 0.3), 6, 7, 0.32);
        SkillFx.play(level, "mace.heavy_smash_ground", p0, 0.8, 1.0);
        SkillFx.play(level, "random.explode", p0, 1.5, 0.4);
        Kit.shakeNear(level, p0, 10, 0.3, 0.3);
        // 균열이 뻗음 (틱마다 한 칸)
        Map<UUID, LivingEntity> struck = new LinkedHashMap<>();
        double width = k.num("width");
        int n = path.size();
        for (int i = 0; i < n; i++) {
            final int ii = i;
            HeroKit.at(i, () -> {
                if (!ok.getAsBoolean()) return;
                Vec3 p = path.get(ii);
                Vec3 prev = ii == 0 ? V.add(p, f, -Math.min(first, 1.0)) : path.get(ii - 1);
                crackStep(sp, p, f, ii, rock, glow, core);
                // 이번 칸까지 지나간 구간 (내리친 자리는 둘레도) 의 적
                Vec3 d = V.sub(p, prev);
                double len = Math.max(0.3, V.len(d));
                List<LivingEntity> hit = new ArrayList<>();
                for (Kit.LineHit h : Kit.lineHits(sp, V.up(prev, 0.6), V.unit(d), len, width)) hit.add(h.e());
                if (ii == 0) hit = Kit.union(hit, Kit.visibleFrom(sp, V.up(p, 1.0), Kit.around(sp, p, width + 0.4, 2.5)));
                long now = Tasks.now();
                for (LivingEntity e : hit) {
                    if (struck.containsKey(e.getUUID())) continue;
                    struck.put(e.getUUID(), e);
                    Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now), ok, () -> {
                        if (Kit.skillHit(sp, e, w, k.num("mult")) <= 0) return;
                        Kit.knockback(e, f.x * 0.15, f.z * 0.15, k.num("lift"));
                        RuneKit.rockBurst(level, V.up(e.position(), 0.2), 3, 5, 0.26);
                        SkillFx.burst(level, V.up(e.position(), 0.5), glow, 0.8, 0.22);
                    });
                }
            });
        }
        // 용암 분출 (균열이 다 뻗고 delay 틱 뒤 — 앞쪽으로 두 칸에 한 틱씩)
        int eruptAt = (n - 1) + (int) k.num("delay");
        for (int i = 0; i < n; i++) {
            final int ii = i;
            HeroKit.at(eruptAt + i / 2, () -> {
                if (ok.getAsBoolean()) eruptFx(level, path.get(ii), glow, core, ii);
            });
        }
        Tasks.later(eruptAt, () -> {
            if (!ok.getAsBoolean()) return;
            Vec3 a = path.get(0);
            Vec3 b = path.get(n - 1);
            List<LivingEntity> line = new ArrayList<>();
            if (V.len(V.sub(b, a)) > 0.3) for (Kit.LineHit h : Kit.lineHits(sp, V.up(a, 0.6), V.unit(V.sub(b, a)), V.len(V.sub(b, a)), width + 0.3)) line.add(h.e());
            line = Kit.union(line, Kit.visibleFrom(sp, V.up(a, 1.0), Kit.around(sp, a, width + 0.4, 2.5)), near(struck, path, 2.5));
            SkillFx.play(level, "mob.blaze.shoot", a, 0.6, 1.0);
            SkillFx.play(level, "item.bucket.empty_lava", V.lerp(a, b, 0.5), 0.8, 1.0);
            SkillFx.play(level, "random.explode", V.lerp(a, b, 0.5), 0.9, 0.5);
            Kit.shakeNear(level, V.lerp(a, b, 0.5), 12, 0.25, 0.3);
            long t0 = Tasks.now();
            for (LivingEntity e : line) {
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, t0), ok, () -> {
                    if (Kit.skillHit(sp, e, w, k.num("afterMult")) <= 0) return;
                    Vec3 at = e.isRemoved() ? a : e.position();
                    Gram.flame(level, at, 1.5, 0.5, glow);
                    Gram.embers(level, at, 1.0, 0.5, 3);
                });
            }
        });
        return true;
    }

    /** 균열 한 칸: 그을린 땅 + 빛나는 금 + 바위 가시 둘 (좌우 번갈아) + 튀는 돌 · 소리 (두 칸마다) */
    static void crackStep(ServerPlayer sp, Vec3 p, Vec3 f, int i, double[] rock, double[] glow, double[] core) {
        ServerLevel level = sp.level();
        Vec3 side = V.rightOf(f);
        SkillFx.fx(level, "rpg:ground_crack", V.up(p, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", 1.7, "life", 1.6, "color", ASH, "alpha", 0.85));
        RuneKit.rfx2(level, "crack", V.up(p, 0.07), Opt.of("dir", V.UP, "size", 0.9, "life", 1.5, "fade", 0.4, "spin", Kit.rand() * 360, "cell", 0.0), glow, core,
                new int[] {0, 1}, null);
        double s = i % 2 == 0 ? 1 : -1;
        RuneKit.rockSpike(sp, level, V.add(p, side, s * (0.35 + Kit.rand() * 0.2)), 0.75 + Kit.rand() * 0.25, glow, Opt.of("rock", rock, "life", 0.9, "grow", 0.25,
                "full", 3.0));
        RuneKit.rockSpike(sp, level, V.add(p, side, -s * (0.2 + Kit.rand() * 0.25)), 0.5 + Kit.rand() * 0.2, glow, Opt.of("rock", rock, "life", 0.8, "grow", 0.2,
                "full", 3.0));
        if (i % 2 == 0) {
            RuneKit.rockBurst(level, V.up(p, 0.2), 2, 5, 0.22);
            SkillFx.play(level, "dig.stone", p, 0.6 + Kit.rand() * 0.2, 1.0);
        }
        if (i % 3 == 1) SkillFx.play(level, "mace.smash_ground", p, 0.7, 0.5);
    }

    /** 용암 분출 한 칸: 불기둥 (주황) · 불티 · 폭발 · 빛나는 금 (k 번째 칸 — 셋에 하나는 크게) */
    static void eruptFx(ServerLevel level, Vec3 p, double[] glow, double[] core, int i) {
        boolean big = i % 3 == 0;
        Gram.flame(level, p, big ? 2.4 : 1.8, 0.55, glow);
        if (big) Gram.flame(level, V.up(p, 0.1), 1.4, 0.45, core);
        Gram.embers(level, p, 1.2, 0.6, 3.5);
        SkillFx.burst(level, V.up(p, 0.4), glow, big ? 1.1 : 0.8, 0.25);
        if (big) SkillFx.sparks(level, V.up(p, 0.6), core, 5, 4);
        if (i % 2 == 0) SkillFx.play(level, "fire.ignite", p, 0.7, 0.6);
    }

    /** 띄운 적 가운데 아직 있고 균열 (자리들) 에서 가로 r 칸 · 위아래 4칸 안인 것 (밀려났어도 분출이 따라감) */
    private static List<LivingEntity> near(Map<UUID, LivingEntity> struck, List<Vec3> path, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : struck.values()) {
            if (e.isRemoved()) continue;
            for (Vec3 p : path) {
                if (V.hdist(e.position(), p) <= r + Kit.bodyPad(e) && Math.abs(e.getY() - p.y) <= 4) {
                    out.add(e);
                    break;
                }
            }
        }
        return out;
    }
}
