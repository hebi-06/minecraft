package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.Vfx;
import mmorpg.fabric.vfx.VfxKit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-28 근접 딜 새 룬 다섯 (사용자 "지금 룬 스킬이 전체적으로 마법사한테 좋은거 같음 그래서 근접 및 힐러 등 같은 애들의 룬 추가가 필요할듯" → 고른 것
 * "등급마다 2개 · 10개" · "근접 딜 5개 먼저"): 옛 동작을 빌리지 않는 새 동작 (rules_runes.json — tools/rune_set.py 의 src 없는 룬).
 * <ul>
 * <li>크로스 컷 (일반 · 민첩): 칼날을 X 자로 두 번 그어 앞을 벤 뒤 허공의 X 가 터짐 — 동작 rn_flurry (1 · 3틱에 벰)</li>
 * <li>브루탈 클리브 (희귀 · 힘): 거대한 칼로 앞 반원을 크게 쓸어 벤 뒤 벤 자리를 따라 땅이 터짐 — 동작 rn_cleave (2틱에 앞을 지남)</li>
 * <li>팬텀 스러스트 (고귀 · 민첩): 앞을 꿰뚫어 찌르고 그림자 분신 둘 · 셋이 이어 찌름 — 동작 rn_frost_thrust (1틱에 찌름)</li>
 * <li>모탈 스트라이크 (영웅 · 힘): 한 적 위에 처형의 검이 떠올라 내리꽂히고 깊은 상처가 터짐 — 동작 rn_execute (8틱에 내려찍음)</li>
 * <li>그랜드 크로스 (초월 · 힘): 가로로 휩쓸고 세로로 내려친 자리에 빛의 십자가가 솟아 터짐 — 동작 rn_blade_triple (1 · 10틱에 벰 · 20틱에 찌름 — 세로 피해는 11틱: 몹 무적 시간)</li>
 * </ul>
 * 피해 시각 = 표 (RUNE_SET.spread) 의 w · t (같은 적을 10틱 넘게 띄움 — 몹 무적 시간). 그림은 그림 묶음 계열 (def.look) 의 빛 — 참격 판 (RuneKit.arc) 은 칠한 묶음의
 * 반달 · 쓴 사람 1인칭은 화면 가운데 초승달 (PhantomFx). 화면 흔들림은 없음 (사용자 "화면 흔들림은 넣지마 멀미나").
 */
final class RuneMelee {
    private RuneMelee() {}

    static void register() {
        RuneCast.register("crossCut", RuneMelee::crossCut);
        RuneCast.register("brutalCleave", RuneMelee::brutalCleave);
        RuneCast.register("phantomThrust", RuneMelee::phantomThrust);
        RuneCast.register("mortalStrike", RuneMelee::mortalStrike);
        RuneCast.register("grandCross", RuneMelee::grandCross);
    }

    /** 쓴 사람이 보는 쪽 (수평 · 길이 1) */
    private static Vec3 front(ServerPlayer sp) {
        Vec3 d = V.flat(sp.getViewVector(1F));
        return V.len(d) < 1e-6 ? Kit.facingOf(sp) : V.unit(d);
    }

    /** 몸 높이 h 의 자리에서 f 쪽 dist 칸 · 오른쪽 side 칸 */
    private static Vec3 ahead(Vec3 feet, Vec3 f, double dist, double side, double h) {
        return V.up(V.add(V.add(feet, f, dist), V.rightOf(f), side), h);
    }

    // ------------------------------------------------------------ 크로스 컷 (일반)

    /**
     * 크로스 컷: 1틱 오른 어깨 위 → 왼쪽 아래 · 3틱 왼 어깨 위 → 오른쪽 아래로 3D 룬 칼 (2.2칸) 과 칠한 반달이 X 자로 지나감 (동작 rn_flurry) → 3틱에 앞 range 칸 ·
     * cone 도 안 적에게 위력 × mult (X 베기 — 적마다 X 자국) · 허공 (앞 2.1칸 · 가슴 높이) 에 빛나는 X 가 걸림 → hold 틱 뒤 (13틱) X 가 모여 터지며 벤 적
     * (둘레 range + 2칸 안) 에게 위력 × crush (X 폭발). 1인칭: 반달은 화면 가운데 초승달 · 허공의 X 는 눈앞 2칸 넘게 떨어져 가리지 않음
     */
    static boolean crossCut(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        double[][] cs = RuneKit.lookColors(k, "blade");
        double[] glow = cs[0];
        double[] core = cs[1];
        String life = RuneKit.lifeToken(sp);
        Vec3 f = front(sp);
        Vec3 right = V.rightOf(f);
        double range = k.num("range");
        int hold = (int) k.num("hold");
        Vec3 c0 = sp.position();
        Vec3 x = ahead(c0, f, 2.1, 0, 1.15); // 허공의 X 가운데
        Vfx.Fx fx = Vfx.at(level, x, 3 + hold + 18);
        SkillFx.play(level, "item.trident.riptide_1", c0, 1.6, 0.6);
        // 두 번 벰 (3D 칼: tilt 45 = 오른쪽 위 ↔ 왼쪽 아래 · −45 = 왼쪽 위 ↔ 오른쪽 아래, 앞 (0) 을 지나는 틱 = 1 · 3)
        RuneSwing.sword(sp, life, 1.25, 0.5, 2.2, 45, 75, -75, 0, 2, 1, "out", glow, core);
        RuneSwing.sword(sp, life, 1.25, 0.5, 2.2, -45, -75, 75, 2, 2, 1, "out", glow, core);
        RuneKit.later(1, () -> crossArc(sp, life, false, glow, core, 1.4));
        RuneKit.later(3, () -> crossArc(sp, life, true, glow, core, 1.25));
        List<LivingEntity> cut = new ArrayList<>();
        RuneKit.later(3, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            int n = 0;
            for (LivingEntity e : RuneKit.coneTargets(sp, range, k.num("cone"), 10)) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) <= 0) continue;
                cut.add(e);
                if (n++ < 6) {
                    VfxKit.xcut(fx, Kit.bodyPoint(e), 1.5, glow, 0);
                    VfxKit.sparks(fx, Kit.bodyPoint(e), core, 4, 1.0, 0);
                }
            }
            // 허공의 X: 두 빛줄기 (테 + 흰 심지) 가 hold 틱 걸려 있음
            double s = 1.15;
            Vec3 a1 = V.add(V.add(x, right, s), V.UP, s);
            Vec3 b1 = V.add(V.add(x, right, -s), V.UP, -s);
            Vec3 a2 = V.add(V.add(x, right, -s), V.UP, s);
            Vec3 b2 = V.add(V.add(x, right, s), V.UP, -s);
            double sec = (hold + 2) / 20.0;
            RuneKit.streak(d, a1, b1, glow, 0.22, sec);
            RuneKit.streak(d, a2, b2, glow, 0.22, sec);
            RuneKit.streak(d, a1, b1, core, 0.08, sec);
            RuneKit.streak(d, a2, b2, core, 0.08, sec);
            VfxKit.glow(fx, x, 1.4, glow, 0.55, hold);
            SkillFx.play(d, "entity.player.attack.sweep", c0, 1.3, 0.9);
        });
        // X 에 빛이 모임 (불티가 가운데로)
        for (int t = 5; t < 3 + hold; t += 2) {
            final int tt = t;
            fx.on(tt, () -> {
                double a = Kit.rand() * Math.PI * 2;
                Vec3 p = V.add(V.add(x, right, Math.cos(a) * 1.3), V.UP, Math.sin(a) * 1.3);
                Vfx.Sprite sp2 = fx.sprite("burst2", p).color(Vfx.mix(glow, VfxKit.WHITE, 0.4)).size(0.32).frame(8 + (tt & 1));
                sp2.to(x, 0.12, 3);
                sp2.fade(fx.age() + 2, 2);
            });
        }
        // X 폭발: 모여든 X 가 터지며 벤 적에게 한 번 더 (밀려난 적도 둘레 range + 2칸 안이면)
        RuneKit.later(3 + hold, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            // 크기: 눈 (앞 2.1칸) 에서 반지름 × 1.5 안이면 1인칭에서 거둬지므로 (Vfx eyeCull) 지름 2.6 · 2.4 까지
            VfxKit.xcut(fx, x, 2.6, core, 0);
            VfxKit.burst(fx, x, 2.4, glow, 0);
            VfxKit.sparks(fx, x, core, 12, 2.2, 0);
            RuneKit.hitFlash(d, x, f, glow, core, 1.6);
            RuneKit.zonePulse(d, V.add(c0, f, 2.0), glow, 2.6, 0.35);
            SkillFx.play(d, "block.glass.break", x, 1.4, 0.9);
            SkillFx.play(d, "entity.player.attack.strong", x, 1.2, 0.9);
            boolean boomed = false;
            for (LivingEntity e : cut) {
                if (e.isRemoved() || !e.isAlive() || e.position().distanceTo(sp.position()) > range + 2) continue;
                if (RuneKit.runeHit(sp, e, P, k.num("crush"), r.stat()) <= 0) continue;
                VfxKit.xcut(fx, Kit.bodyPoint(e), 2.0, core, 0);
                if (!boomed) {
                    boomed = true;
                    RuneBoost.impact(d, VfxKit.ground(d, e.position()), glow, core, 1.8, r.grade());
                }
            }
        });
        return true;
    }

    /** 크로스 컷의 칠한 반달 하나 (가는 반달 — flip = 두 번째 · 반대쪽 대각선), 눈앞 dist 칸 · 가슴 높이 */
    private static void crossArc(ServerPlayer sp, String life, boolean second, double[] glow, double[] core, double dist) {
        if (!RuneKit.sameLife(sp, life)) return;
        Vec3 f = front(sp);
        RuneKit.arc(sp.level(), ahead(sp.position(), f, dist, 0, 1.15), f, Opt.of("shape", "thin", "flip", second, "size", 2.5, "life", 0.22, "speed", 3.0,
                "spin", 0.0, "color", glow, "aura", core));
        SkillFx.play(sp.level(), "entity.player.attack.sweep", sp.position(), second ? 1.5 : 1.7, 0.7);
    }

    // ------------------------------------------------------------ 브루탈 클리브 (희귀)

    /**
     * 브루탈 클리브: 거대한 3D 룬 칼 (4칸) 이 오른쪽 뒤 낮게에서 왼쪽 끝까지 허리 높이로 크게 휩쓸고 (앞을 지나는 틱 2 — 동작 rn_cleave) 칠한 큰 반달 ·
     * 땅을 긁은 금빛 반원 → 2틱에 앞 radius 칸 · cone 도 안 적에게 위력 × mult · 밀쳐냄 (knock) → quake 틱 뒤 (12틱) 벤 반원을 따라 바위가 솟아 터지며
     * (반지름 radius × 0.7 의 반원 위 다섯 자리) 둘레 radius + 1칸 반원 안 적에게 위력 × crush
     */
    static boolean brutalCleave(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        double[][] cs = RuneKit.lookColors(k, "str");
        double[] glow = cs[0];
        double[] core = cs[1];
        String life = RuneKit.lifeToken(sp);
        Vec3 f = front(sp);
        Vec3 c0 = sp.position();
        double radius = k.num("radius");
        double cone = k.num("cone");
        int quake = (int) k.num("quake");
        Vfx.Fx fx = Vfx.at(level, c0, 2 + quake + 30);
        SkillFx.play(level, "mob.ravager.attack", c0, 0.7, 0.9);
        SkillFx.play(level, "item.trident.riptide_1", c0, 0.8, 0.8);
        VfxKit.dust(fx, c0, 1.2, 4, VfxKit.DUST, 0);
        // 거대한 칼: tilt −8 (조금 아래로 기운 가로 — 허리 높이에서 앞 땅 쪽으로), 오른쪽 뒤 (110) → 왼쪽 (−100), 앞을 지나는 틱 2
        RuneSwing.sword(sp, life, 1.05, 0.7, 4.0, -8, 110, -100, 0, 4, 3, "out", glow, core);
        RuneKit.later(1, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 ff = front(sp);
            RuneKit.arc(sp.level(), ahead(sp.position(), ff, 1.8, 0, 0.95), ff, Opt.of("shape", "arc", "flip", false, "size", 4.6, "life", 0.28, "speed", 2.0,
                    "spin", 6.0, "color", glow, "aura", core));
        });
        RuneKit.later(2, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Vec3 c = sp.position();
            Vec3 ff = front(sp);
            // 긁고 지나간 땅: 금빛 반원 · 흙먼지 · 불티
            RuneKit.groundCrescent(d, V.add(c, ff, radius * 0.45), radius * 1.15, glow, core, Math.toDegrees(Math.atan2(ff.z, ff.x)), 0.9);
            for (int i = 0; i < 5; i++) {
                Vec3 p = VfxKit.ground(d, V.add(c, RuneKit.turn(ff, -80 + i * 40), radius * 0.7));
                VfxKit.crack(fx, p, 1.6, glow, i, 2 + quake + 8);
                VfxKit.dust(fx, p, 0.8, 2, VfxKit.DUST, i);
            }
            SkillFx.play(d, "mace.heavy_smash_ground", c, 0.9, 0.9);
            SkillFx.play(d, "entity.player.attack.sweep", c, 0.7, 1.0);
            int n = 0;
            for (LivingEntity e : RuneKit.coneAround(sp, c, ff, radius, cone)) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) <= 0) continue;
                RuneKit.knock(e, V.sub(e.position(), c), k.numOr("knock", 0.8), 0.25);
                if (n++ < 6) {
                    VfxKit.slash(fx, Kit.bodyPoint(e), V.rightOf(ff), V.scale(ff, -1), 1.8, glow, 0);
                    VfxKit.sparks(fx, Kit.bodyPoint(e), core, 5, 1.4, 0);
                }
            }
        });
        // 벤 자리가 끓어오름 (금 사이 빛 · 불티)
        for (int t = 4; t < 2 + quake; t += 2) {
            final int tt = t;
            RuneKit.later(tt, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 p = VfxKit.ground(sp.level(), V.add(c0, RuneKit.turn(f, -80 + Kit.rand() * 160), radius * (0.45 + Kit.rand() * 0.45)));
                VfxKit.sparks(fx, V.up(p, 0.2), Kit.rand() < 0.5 ? glow : core, 3, 0.8, 0);
                if (tt % 4 == 0) SkillFx.play(sp.level(), "block.basalt.break", p, 0.7, 0.7);
            });
        }
        // 땅 터짐: 반원 위 다섯 자리에서 바위가 솟아 튐 · 터짐 · 고리
        RuneKit.later(2 + quake, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            for (int i = 0; i < 5; i++) {
                Vec3 p = VfxKit.ground(d, V.add(c0, RuneKit.turn(f, -80 + i * 40), radius * 0.7));
                RuneKit.rockSpike(sp, d, p, 1.1 + (i == 2 ? 0.4 : 0), glow, Opt.of("life", 0.7));
                VfxKit.burst(fx, V.up(p, 0.7), 2.2, glow, 0);
                VfxKit.dust(fx, p, 1.0, 3, VfxKit.DUST, 1);
            }
            RuneSwing.erupt(d, c0, f, 6, 170, radius * 0.4, radius * 0.95, 0.5, 0.9, 1.8, glow);
            VfxKit.ring(fx, c0, radius * 1.1, glow, 0, 10);
            RuneKit.zonePulse(d, c0, glow, radius, 0.45);
            SkillFx.play(d, "random.explode", V.add(c0, f, radius * 0.7), 0.8, 0.8);
            SkillFx.play(d, "block.deepslate.break", V.add(c0, f, radius * 0.7), 0.6, 1.0);
            boolean boomed = false;
            for (LivingEntity e : RuneKit.coneAround(sp, c0, f, radius + 1, cone)) {
                if (RuneKit.runeHit(sp, e, P, k.num("crush"), r.stat()) <= 0) continue;
                if (!boomed) {
                    boomed = true;
                    RuneBoost.impact(d, VfxKit.ground(d, e.position()), glow, core, 2.4, r.grade());
                }
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 팬텀 스러스트 (고귀)

    /**
     * 팬텀 스러스트: 1틱 앞을 꿰뚫어 찌름 (3D 환영 칼이 손에서 앞으로 · 칠한 돌진 줄기) — 앞 length 칸 · 폭 width 칸 줄의 적 위력 × mult →
     * 그림자 분신 둘 (앞 0.6칸 · 양옆 1.5칸) 이 나타나 11틱에 함께 찌름 (위력 × mult) → 셋째 분신 (머리 위 뒤) 이 더해 셋이 21틱에 환영 창으로 꿰뚫음
     * (거대한 3D 환영 칼 · 줄 끝 터짐 — 위력 × crush). hits = 앞의 두 번
     */
    static boolean phantomThrust(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        double[][] cs = RuneKit.lookColors(k, "assassin");
        double[] glow = cs[0];
        double[] core = cs[1];
        String life = RuneKit.lifeToken(sp);
        Vec3 f = front(sp);
        Vec3 c0 = sp.position();
        double len = k.num("length");
        double width = k.num("width");
        int gap = (int) k.num("gap");
        Vfx.Fx fx = Vfx.at(level, c0, 2 * gap + 30);
        SkillFx.play(level, "item.trident.throw", c0, 1.4, 0.8);
        // 1: 내 찌르기
        thrust(sp, fx, life, V.up(c0, 1.2), f, len, 1, 2.2, glow, core);
        RuneKit.later(1, () -> pierce(sp, r, P, k.num("mult"), V.up(c0, 1.2), f, len, width, glow, core, fx));
        // 2: 분신 둘 (양옆 — 1인칭 화면 가장자리)
        List<RuneMob> shades = new ArrayList<>();
        for (int s : new int[] {-1, 1}) {
            Vec3 at = V.add(V.add(c0, f, 0.6), V.rightOf(f), 1.5 * s);
            RuneMob m = RuneMob.spawn(RuneMob.SHADE, level, at, f, 2 * gap + 16, 1.0, true);
            if (m == null) continue;
            m.anim("appear");
            shades.add(m);
        }
        RuneKit.later(1 + gap - RuneMob.SHADE_STRIKE_AT, () -> {
            for (RuneMob m : shades) if (m.alive()) m.anim("stab");
        });
        RuneKit.later(1 + gap, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            SkillFx.play(sp.level(), "item.trident.throw", c0, 1.2, 0.8);
            for (int s : new int[] {-1, 1}) {
                Vec3 from = V.up(V.add(V.add(c0, f, 0.9), V.rightOf(f), 1.5 * s), 1.2);
                // 분신의 창은 가운데 줄 쪽으로 모임 (앞 length 칸 끝에서 엇갈림)
                Vec3 to = V.up(V.add(c0, f, len + 0.5), 1.2);
                Vec3 dd = V.unit(V.sub(to, from));
                thrust(sp, fx, life, from, dd, V.len(V.sub(to, from)), 0, 1.8, glow, core);
            }
            pierce(sp, r, P, k.num("mult"), V.up(c0, 1.2), f, len, width + 0.4, glow, core, fx);
        });
        // 3: 셋째 분신 (머리 위 뒤 — 3인칭 카메라 · 1인칭 화면 밖) · 셋이 환영 창으로
        RuneKit.later(1 + gap, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            RuneMob m = RuneMob.spawn(RuneMob.SHADE, sp.level(), V.up(V.add(c0, f, -0.4), 1.4), f, gap + 16, 1.15, true);
            if (m == null) return;
            m.anim("appear");
            shades.add(m);
        });
        RuneKit.later(1 + 2 * gap - RuneMob.SHADE_STRIKE_AT, () -> {
            for (RuneMob m : shades) if (m.alive()) m.anim("stab");
            if (RuneKit.sameLife(sp, life)) SkillFx.play(sp.level(), "mob.phantom.flap", c0, 0.8, 0.9);
        });
        RuneKit.later(1 + 2 * gap, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            // 거대한 환영 칼 (4.5칸) 이 가운데 줄을 꿰뚫음 + 칠한 돌진 줄기 셋
            thrust(sp, fx, life, V.up(c0, 1.25), f, len + 0.5, 0, 4.5, glow, core);
            for (int s : new int[] {-1, 0, 1}) {
                Vec3 from = V.up(V.add(c0, V.rightOf(f), 0.35 * s), 1.1 + 0.15 * Math.abs(s));
                RuneKit.flyStreak(d, from, V.add(from, f, len + 1), 40, glow, core, 0.9, 0.3);
            }
            Vec3 end = V.up(V.add(c0, f, len), 1.2);
            VfxKit.burst(fx, end, 2.8, glow, 0);
            VfxKit.sparks(fx, end, core, 10, 2.0, 0);
            RuneKit.hitFlash(d, end, f, glow, core, 1.8);
            SkillFx.play(d, "item.trident.hit", end, 0.8, 1.0);
            SkillFx.play(d, "firework.large_blast", end, 1.2, 0.6);
            List<Kit.LineHit> hits = RuneKit.lineTargets(sp, V.up(c0, 1.2), f, len, width + 0.4);
            boolean boomed = false;
            for (Kit.LineHit h : hits) {
                if (RuneKit.runeHit(sp, h.e(), P, k.num("crush"), r.stat()) <= 0) continue;
                VfxKit.xcut(fx, Kit.bodyPoint(h.e()), 1.6, core, 0);
                if (!boomed) {
                    boomed = true;
                    RuneBoost.impact(d, VfxKit.ground(d, h.e().position()), glow, core, 2.0, r.grade());
                }
            }
            for (RuneMob m : shades) if (m.alive()) m.leave("vanish", 8);
        });
        return true;
    }

    /**
     * 찌르기 그림 하나: 3D 환영 칼 (blade 칸) 이 from 에서 dir 쪽으로 2틱에 len 칸 앞까지 밀려 나갔다가 사라짐 + 칠한 돌진 줄기 · 끝의 섬광.
     * delay 틱 뒤 (0 = 바로)
     */
    private static void thrust(ServerPlayer sp, Vfx.Fx fx, String life, Vec3 from, Vec3 dir, double len, int delay, double blade, double[] glow,
            double[] core) {
        RuneKit.later(delay, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Vec3 u = V.unit(dir);
            Vec3 side = V.len(V.cross(u, V.UP)) > 1e-3 ? V.unit(V.cross(u, V.UP)) : new Vec3(1, 0, 0);
            double s = RuneProps.scaleFor(blade, RuneProps.SWORD_LEN);
            Vec3 a = V.add(from, u, 0.4 + blade / 2);
            Vec3 b = V.add(from, u, Math.max(0.6, len - blade / 2));
            Vfx.Prop p = RuneProps.prop(fx, RuneProps.PHANTOM_SWORD, a, RuneProps.SWORD_MID, RuneProps.along(u, side), s * 0.6, glow, core);
            int t0 = fx.age();
            fx.on(t0 + 1, () -> p.to(b, null, s, 2));
            fx.on(t0 + 5, () -> p.to(V.add(b, u, 0.6), null, s * 0.2, 4));
            fx.on(t0 + 10, p::remove);
            RuneKit.flyStreak(d, from, V.add(from, u, len), 45, glow, core, 0.6, 0.18);
            VfxKit.glow(fx, V.add(from, u, len), 1.0, glow, 0.7, 5);
        });
    }

    /** 찌르기 피해 한 번: from 에서 dir 쪽 len 칸 · 폭 w 줄의 적 (적마다 찔린 자국 · 불티) */
    private static void pierce(ServerPlayer sp, Runes.Rune r, double P, double mult, Vec3 from, Vec3 dir, double len, double w, double[] glow, double[] core,
            Vfx.Fx fx) {
        int n = 0;
        for (Kit.LineHit h : RuneKit.lineTargets(sp, from, dir, len, w)) {
            if (RuneKit.runeHit(sp, h.e(), P, mult, r.stat()) <= 0) continue;
            if (n++ < 6) {
                VfxKit.burst(fx, Kit.bodyPoint(h.e()), 1.1, glow, 0);
                VfxKit.sparks(fx, Kit.bodyPoint(h.e()), core, 4, 1.2, 0);
            }
        }
    }

    // ------------------------------------------------------------ 모탈 스트라이크 (영웅)

    /** 처형의 검 (3D 룬 검) 의 길이 · 떠 있는 높이 (대상 발 위) */
    private static final double MORTAL_LEN = 4.2;
    private static final double MORTAL_SKY = 6.5;

    /**
     * 모탈 스트라이크: 앞 range 칸 · cone 도 안의 적 하나 (없으면 쓰지 않음 — 재사용 대기 없음) — 그 적 위 하늘 (발 위 6.5칸) 에 칼끝을 아래로 한 처형의 검
     * (3D 룬 검 4.2칸) 이 작게 나타나 커지며 핏빛 빛을 모음 · 발밑 핏빛 고리 (동작 rn_execute — 무기를 머리 위 뒤로 젖힘) → 7틱부터 2틱에 내리꽂혀 8틱에 위력
     * × mult (처형 일격 — 단일) · 세로 참격 · 갈라진 땅 → 검이 꽂힌 채 상처가 고동침 → wound 틱 뒤 (18틱) 상처가 터지며 위력 × crush (그 적만 — 그 사이 쓰러지면
     * 터짐만)
     */
    static boolean mortalStrike(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        LivingEntity target = Kit.pickTarget(sp, k.num("range"), k.num("cone"));
        if (target == null) {
            mmorpg.fabric.Notify.warn(sp, "§c" + k.str("name") + ": §7앞 " + k.text("range") + "칸 안에 대상이 없습니다");
            return false;
        }
        ServerLevel level = sp.level();
        double[][] cs = RuneKit.lookColors(k, "axe");
        double[] glow = cs[0];
        double[] core = cs[1];
        String life = RuneKit.lifeToken(sp);
        int wound = (int) k.num("wound");
        int strike = 8;
        Vec3 g0 = VfxKit.ground(level, target.position());
        Vec3 toMe = V.flat(V.sub(sp.position(), g0));
        Vec3 side = RuneKit.turn(V.len(toMe) > 1e-3 ? V.unit(toMe) : new Vec3(0, 0, 1), 90);
        double s = RuneProps.scaleFor(MORTAL_LEN, RuneProps.SWORD_LEN);
        double rest = MORTAL_LEN / 2 - 0.7; // 꽂혔을 때 가운데 높이 (칼끝 땅속 0.7칸)
        Vfx.Fx fx = Vfx.at(level, g0, strike + wound + 24);
        Vec3 sky0 = V.up(target.position(), MORTAL_SKY);
        Vfx.Prop blade = RuneProps.prop(fx, RuneProps.SWORD, sky0, RuneProps.SWORD_MID, RuneProps.along(V.DOWN, side), s * 0.2, glow, core);
        fx.on(1, () -> blade.size(s, strike - 3));
        Vfx.Sprite halo = fx.sprite("glow", V.up(sky0, -MORTAL_LEN / 2)).color(glow).size(1.0).alpha(0.7);
        halo.to(null, 3.0, strike - 2);
        VfxKit.ring(fx, g0, 2.2, glow, 0, strike);
        SkillFx.play(level, "mob.evocation_illager.prepare_attack", g0, 0.6, 1.0);
        SkillFx.play(level, "block.bell.hit", g0, 0.5, 0.8);
        // 하늘의 검을 따라감 (대상이 움직이면) · 빛이 모임
        for (int t = 1; t < strike - 2; t++) {
            final int tt = t;
            fx.on(tt, () -> {
                if (target.isRemoved()) return;
                Vec3 p = V.up(target.position(), MORTAL_SKY);
                blade.move(p, 1);
                halo.to(V.up(p, -MORTAL_LEN / 2), -1, 1);
                if (tt % 2 == 0) VfxKit.sparks(fx, V.up(p, -MORTAL_LEN / 2), Kit.rand() < 0.5 ? glow : core, 2, 1.2, 0);
            });
        }
        // 내리꽂힘 (strike − 2 → strike)
        fx.on(strike - 2, () -> {
            halo.remove();
            Vec3 g = target.isRemoved() ? g0 : VfxKit.ground(level, target.position());
            blade.to(V.up(g, rest + 1.6), null, s, 1);
            SkillFx.play(level, "item.trident.riptide_1", g, 0.6, 1.0);
        });
        RuneKit.later(strike - 1, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 g = target.isRemoved() ? g0 : VfxKit.ground(level, target.position());
            blade.to(V.up(g, rest), null, s, 1);
        });
        RuneKit.later(strike, () -> {
            if (!RuneKit.sameLife(sp, life)) {
                fx.end();
                return;
            }
            ServerLevel d = sp.level();
            Vec3 g = target.isRemoved() ? g0 : VfxKit.ground(d, target.position());
            Vec3 f = V.flat(V.sub(g, sp.position()));
            Vec3 ff = V.len(f) > 1e-3 ? V.unit(f) : front(sp);
            boolean hit = !target.isRemoved() && target.isAlive() && RuneKit.runeHit(sp, target, P, k.num("mult"), r.stat()) > 0;
            // 세로 참격 (칠한 무거운 반달 — 1인칭은 화면 가운데를 곧게) · 핏빛 터짐 · 갈라진 땅
            RuneKit.arc(d, V.up(g, 1.4), ff, Opt.of("shape", "heavy", "flip", false, "size", 3.4, "life", 0.26, "speed", 1.0, "spin", -6.0, "color", glow,
                    "aura", core));
            VfxKit.burst(fx, V.up(g, 1.0), 3.6, glow, 0);
            VfxKit.burst(fx, V.up(g, 1.2), 2.4, core, 1);
            VfxKit.crack(fx, g, 4.2, glow, 0, wound + 16);
            VfxKit.ring(fx, g, 3.0, glow, 0, 8);
            VfxKit.blood(fx, V.up(g, 1.0), 1.2, 5, glow, 1);
            VfxKit.sparks(fx, V.up(g, 0.8), core, 10, 2.2, 0);
            RuneKit.zonePulse(d, g, glow, 2.6, 0.4);
            SkillFx.play(d, "mace.heavy_smash_ground", g, 0.8, 1.0);
            SkillFx.play(d, "random.anvil_land", g, 0.6, 0.6);
            if (hit) RuneBoost.impact(d, g, glow, core, 2.6, r.grade());
        });
        // 상처가 고동침 (4틱마다 핏빛 불티 · 심장 소리)
        for (int t = strike + 3; t < strike + wound; t += 4) {
            final int tt = t;
            RuneKit.later(tt, () -> {
                if (!RuneKit.sameLife(sp, life) || target.isRemoved()) return;
                Vec3 b = Kit.bodyPoint(target);
                VfxKit.glow(fx, b, 1.3 + (tt - strike) * 0.05, glow, 0.6, 3);
                VfxKit.blood(fx, b, 0.7, 2, glow, 0);
                SkillFx.play(sp.level(), "entity.warden.heartbeat", b, 0.9, 1.0);
            });
        }
        // 상처 터짐 · 검이 부서지며 가라앉음
        RuneKit.later(strike + wound, () -> {
            if (!RuneKit.sameLife(sp, life)) {
                fx.end();
                return;
            }
            ServerLevel d = sp.level();
            Vec3 g = target.isRemoved() ? g0 : VfxKit.ground(d, target.position());
            Vec3 b = target.isRemoved() ? V.up(g, 1.0) : Kit.bodyPoint(target);
            if (!target.isRemoved() && target.isAlive()) RuneKit.runeHit(sp, target, P, k.num("crush"), r.stat());
            VfxKit.burst(fx, b, 3.0, glow, 0);
            VfxKit.blood(fx, b, 1.6, 8, glow, 0);
            VfxKit.xcut(fx, b, 2.4, core, 0);
            VfxKit.sparks(fx, b, core, 12, 2.4, 0);
            RuneKit.hitFlash(d, b, V.UP, glow, core, 1.8);
            SkillFx.play(d, "random.explode", b, 1.1, 0.7);
            SkillFx.play(d, "mob.phantom.bite", b, 0.7, 1.0);
            blade.to(V.up(g, rest - 1.6), null, s * 0.5, 6);
            fx.on(fx.age() + 7, blade::remove);
        });
        return true;
    }

    // ------------------------------------------------------------ 그랜드 크로스 (초월)

    /**
     * 그랜드 크로스: 1틱 금빛 3D 룬 칼 (3.6칸) 로 오른쪽에서 왼쪽 끝까지 가로로 휩쓸어 앞 radius 칸 반원의 적 위력 × mult · 땅에 가로 빛줄 (앞 at 칸 —
     * 십자가의 가로) → 11틱 머리 위에서 앞 땅까지 세로로 내려쳐 앞 length 칸 · 폭 width 칸 줄의 적 위력 × mult · 땅에 세로 빛줄 (십자가의 세로) → 빛줄이
     * 밝아지며 둘레에서 빛이 모임 (20틱 찌르는 동작) → 24틱 십자가가 빛기둥으로 솟아 터지며 십자가 (세로 줄 · 가로 줄 · 가운데 둘레) 안 적에게 위력 × crush.
     * 하늘빛 · 번쩍임은 대천사의 하늘 (rpg:ult_heaven — 3돌부터, ULT_ALIAS)
     */
    static boolean grandCross(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        double[][] cs = RuneKit.lookColors(k, "holy");
        double[] glow = cs[0];
        double[] core = cs[1];
        String life = RuneKit.lifeToken(sp);
        Vec3 f = front(sp);
        Vec3 right = V.rightOf(f);
        Vec3 c0 = sp.position();
        double radius = k.num("radius");
        double len = k.num("length");
        double width = k.num("width");
        double at = k.num("at");
        double arm = Math.max(2.0, radius * 0.55); // 가로 줄의 반 길이
        Vec3 cross = VfxKit.ground(level, V.add(c0, f, at));
        int horiz = 1;
        int vert = 11; // 가로 베기 (1틱) 와 10틱 띄움 — 몹 무적 시간
        int boom = 24;
        Vfx.Fx fx = Vfx.at(level, cross, boom + 30);
        RuneKit.selfCue(sp, glow, core, true);
        SkillFx.play(level, "beacon.activate", c0, 1.4, 0.8);
        SkillFx.play(level, "item.trident.riptide_1", c0, 1.0, 0.8);
        // 1: 가로 휩쓸기 (오른쪽 → 왼쪽 · 앞을 지나는 틱 1)
        RuneSwing.sword(sp, life, 1.2, 0.6, 3.6, 0, 100, -100, 0, 2, 3, "out", glow, core);
        RuneKit.later(horiz, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Vec3 c = sp.position();
            Vec3 ff = front(sp);
            RuneKit.arc(d, ahead(c, ff, 1.8, 0, 1.15), ff, Opt.of("shape", "arc", "flip", false, "size", 4.8, "life", 0.28, "speed", 2.5, "spin", 0.0,
                    "color", glow, "aura", core));
            SkillFx.play(d, "entity.player.attack.sweep", c, 1.0, 1.0);
            SkillFx.play(d, "block.bell.hit", c, 1.4, 0.5);
            for (LivingEntity e : RuneKit.coneAround(sp, c, ff, radius, 180)) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) VfxKit.slash(fx, Kit.bodyPoint(e), V.rightOf(ff), V.scale(ff, -1), 1.8, core, 0);
            }
            // 십자가의 가로 (땅 — 앞 at 칸에서 양옆 arm 칸)
            groundBar(d, V.add(cross, right, arm), V.add(cross, right, -arm), glow, core, (boom - horiz) / 20.0 + 0.2);
        });
        // 2: 세로 내려치기 (머리 위 → 앞 아래 · 앞을 지나는 틱 약 10.5)
        RuneSwing.sword(sp, life, 1.2, 0.6, 3.6, 90, 95, -70, vert - 2, 3, 3, "in", glow, core);
        RuneKit.later(vert, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Vec3 c = sp.position();
            Vec3 ff = front(sp);
            RuneKit.arc(d, ahead(c, ff, 2.2, 0, 1.3), ff, Opt.of("shape", "heavy", "flip", false, "size", 4.4, "life", 0.26, "speed", 1.5, "spin", -6.0,
                    "color", glow, "aura", core));
            for (int i = 1; i <= 4; i++) VfxKit.crack(fx, VfxKit.ground(d, V.add(c0, f, len * i / 4.5)), 1.8, glow, i - 1, boom - vert + 12);
            SkillFx.play(d, "mace.heavy_smash_ground", V.add(c, ff, 2), 1.0, 0.9);
            SkillFx.play(d, "block.bell.hit", c, 1.0, 0.6);
            for (Kit.LineHit h : RuneKit.lineTargets(sp, V.up(c0, 0.9), f, len, width)) {
                if (RuneKit.runeHit(sp, h.e(), P, k.num("mult"), r.stat()) > 0) VfxKit.slash(fx, Kit.bodyPoint(h.e()), V.DOWN, V.scale(f, -1), 1.8, core, 0);
            }
            // 십자가의 세로 (땅 — 앞 0.8칸에서 length 칸까지)
            groundBar(d, VfxKit.ground(d, V.add(c0, f, 0.8)), VfxKit.ground(d, V.add(c0, f, len)), glow, core, (boom - vert) / 20.0 + 0.2);
        });
        // 3: 십자가에 빛이 모임 (가운데 문양 · 둘레 빛 알갱이 · 땅의 고리)
        RuneKit.later(vert + 2, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            RuneKit.rfx(sp.level(), "sigil", V.up(cross, 0.06), Opt.of("dir", V.UP, "size", arm * 1.1, "life", (boom - vert) / 20.0, "color", glow, "alpha", 0.85,
                    "fade", 0.3, "spin", 0.0));
            SkillFx.play(sp.level(), "beacon.power", cross, 1.2, 0.9);
        });
        for (int t = vert + 3; t < boom; t += 2) {
            final int tt = t;
            fx.on(tt, () -> {
                double a = Kit.rand() * Math.PI * 2;
                Vec3 p = V.up(V.add(cross, new Vec3(Math.cos(a), 0, Math.sin(a)), arm + 0.8), 0.3 + Kit.rand() * 1.6);
                Vfx.Sprite s = fx.sprite("burst2", p).color(Vfx.mix(glow, VfxKit.WHITE, 0.5)).size(0.36).frame(8 + (tt & 1));
                s.to(V.up(cross, 0.8), 0.14, 4);
                s.fade(fx.age() + 3, 2);
                if (tt % 6 == 0) VfxKit.ring(fx, cross, arm * (0.6 + 0.4 * (tt - vert) / (double) (boom - vert)), glow, 0, 6);
            });
        }
        // 4: 그랜드 크로스 — 빛기둥 · 터짐 · 하늘빛
        RuneKit.later(boom, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            List<Vec3> spots = new ArrayList<>();
            spots.add(cross);
            spots.add(VfxKit.ground(d, V.add(cross, right, arm)));
            spots.add(VfxKit.ground(d, V.add(cross, right, -arm)));
            spots.add(VfxKit.ground(d, V.add(c0, f, len)));
            spots.add(VfxKit.ground(d, V.add(c0, f, (at + len) / 2 + 0.5)));
            for (int i = 0; i < spots.size(); i++) {
                Vec3 p = spots.get(i);
                boolean mid = i == 0;
                RuneKit.rfx2(d, "rays", p, Opt.of("size", mid ? 8.0 : 5.5, "life", 0.9, "alpha", 0.9, "fade", 0.55, "grow", 0.0), glow, core, new int[] {0, 1}, 0.95);
                VfxKit.burst(fx, V.up(p, 1.0), mid ? 4.6 : 2.8, glow, i);
                VfxKit.ring(fx, p, mid ? arm + 1.5 : 1.8, core, i, 10);
            }
            VfxKit.burst(fx, V.up(cross, 1.4), 3.2, core, 2);
            VfxKit.sparks(fx, V.up(cross, 1.0), core, 16, 3.2, 0);
            RuneKit.zonePulse(d, cross, glow, arm + 1.5, 0.55);
            RuneBoost.impact(d, cross, glow, core, arm + 1.0, r.grade());
            SkillFx.play(d, "random.explode", cross, 1.0, 0.9);
            SkillFx.play(d, "beacon.deactivate", cross, 1.6, 0.9);
            SkillFx.play(d, "item.trident.thunder", cross, 1.5, 0.5);
            RuneKit.ultSkyFor(sp, d, cross, "grandCross", 50, null);
            RuneKit.ultFlashFor(sp, d, cross, "grandCross", "flash", Opt.empty());
            // 십자가 안: 세로 줄 (앞 0.3 ~ length 칸 · 폭 width) · 가로 줄 (앞 at 칸 · 양옆 arm 칸 · 폭 width) · 가운데 둘레 2.5칸
            List<LivingEntity> in = new ArrayList<>();
            for (Kit.LineHit h : RuneKit.lineTargets(sp, V.up(c0, 0.9), f, len, width)) if (!in.contains(h.e())) in.add(h.e());
            Vec3 left = V.up(V.add(cross, right, -arm - 0.5), 0.9);
            for (Kit.LineHit h : RuneKit.lineTargets(sp, left, right, arm * 2 + 1, width)) if (!in.contains(h.e())) in.add(h.e());
            for (LivingEntity e : Kit.around(sp, cross, 2.5)) if (!in.contains(e)) in.add(e);
            for (LivingEntity e : in) {
                if (RuneKit.runeHit(sp, e, P, k.num("crush"), r.stat()) > 0) {
                    VfxKit.burst(fx, Kit.bodyPoint(e), 1.6, core, 0);
                    RuneKit.knock(e, V.UP, 0, 0.25); // 타격 반응 (띄움 0.35 아래 — 유틸 아님)
                }
            }
        });
        return true;
    }

    /** 십자가의 빛줄 (땅 위 0.1칸): 금빛 테 + 흰 심지 — sec 초 */
    private static void groundBar(ServerLevel level, Vec3 a, Vec3 b, double[] glow, double[] core, double sec) {
        Vec3 aa = V.up(a, 0.1);
        Vec3 bb = V.up(b, 0.1);
        RuneKit.streak(level, aa, bb, glow, 0.55, sec);
        RuneKit.streak(level, aa, bb, core, 0.18, sec);
    }
}
