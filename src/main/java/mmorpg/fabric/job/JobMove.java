package mmorpg.fabric.job;

import java.util.function.UnaryOperator;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 0.50.0 직업 스킬 몸 움직임 (사용자 "플레이어 모션들이 역동적인게 내가 좋아하는 느낌인거 같음 검사 궁 같은걸 더 역동적으로 움직이는걸로" — 고른 것: 궁극기 +
 * 제자리 스킬): 쓴 틱부터 구간 (Seg) 마다 몸을 매 틱 옮김 — 솟아오름 · 공중에 떠 있음 · 짧은 도약 · 미끄러짐 · 급강하. 1인칭 화면도 따라감 (RuneKit.leap 과 같은
 * 길 — Kit.moveTo), 움직이는 동안 떨어짐 피해 없음, 다음 자리가 블록과 겹치면 그 자리에서 멈춤 (뒤 구간도 그만), 죽거나 차원이 바뀌면 그만.
 * 스킬의 피해 · 그림 자리는 쓴 때의 발 자리 (땅) 를 기준으로 둠 — 공중에 떠도 둘레 적을 그대로 침.
 */
final class JobMove {
    private JobMove() {}

    static final int LINEAR = 0;
    static final int OUT = 1;
    static final int IN = 2;
    static final int INOUT = 3;

    /**
     * 한 구간: start 틱 → end 틱 동안 그 구간이 시작할 때의 발 자리 from 에서 to = target(from) 으로 (ease). peak > 0 이면 가운데가 peak 칸 솟는 포물선
     * (도약). target 이 null 이면 그 자리에 떠 있음 (공중 — 매 틱 다시 놓아 떨어지지 않게)
     */
    record Seg(int start, int end, UnaryOperator<Vec3> target, int ease, double peak) {}

    static Seg to(int start, int end, UnaryOperator<Vec3> target, int ease) {
        return new Seg(start, end, target, ease, 0);
    }

    static Seg hop(int start, int end, UnaryOperator<Vec3> target, double peak) {
        return new Seg(start, end, target, LINEAR, peak);
    }

    static Seg hold(int start, int end) {
        return new Seg(start, end, null, LINEAR, 0);
    }

    /**
     * 주인마다 지금 움직임의 번호: 새 움직임 (run) · 그만 (cancel) 이 올림 → 앞 움직임의 남은 틱은 아무것도 안 함. 0.50.0 시험: 검은 장미의 빠지는 구간이 이어 쓴
     * 로키의 환영 (순간 이동) 을 원래 자리로 끌어당겼음
     */
    private static final java.util.Map<java.util.UUID, Integer> GEN = new java.util.HashMap<>();

    /** 지금 움직임 번호 (JobSystem: 새 스킬이 제 움직임을 시작했는지 보려고) */
    static int gen(ServerPlayer sp) {
        return GEN.getOrDefault(sp.getUUID(), 0);
    }

    /** 이 사람의 움직임을 그만 (남은 틱 · 공중에 떠 있던 것도 — 떨어짐 피해는 noFall 이 막음) */
    static void cancel(ServerPlayer sp) {
        GEN.merge(sp.getUUID(), 1, Integer::sum);
    }

    /**
     * 구간들을 차례로 (같은 틱에 겹치지 않게 — 앞 구간의 end ≤ 다음 start). 앞 움직임은 그만. 다른 것이 몸을 옮기면 (한 틱에 1칸 넘게 — 순간 이동 · 다른 스킬 ·
     * 끌려감) 그 자리에서 그만
     */
    static void run(ServerPlayer sp, Seg... segs) {
        if (segs.length == 0) return;
        String life = RuneKit.lifeToken(sp);
        int gen = GEN.merge(sp.getUUID(), 1, Integer::sum);
        int last = 0;
        for (Seg s : segs) last = Math.max(last, s.end());
        Kit.noFall(sp, last + 30);
        boolean[] stuck = {false};
        Vec3[] placed = {null};
        for (Seg s : segs) {
            Vec3[] from = new Vec3[1];
            Vec3[] to = new Vec3[1];
            int n = Math.max(1, s.end() - s.start());
            RuneKit.later(s.start(), () -> {
                if (!alive(sp, life, gen, stuck, placed)) return;
                from[0] = sp.position();
                to[0] = s.target() == null ? from[0] : s.target().apply(from[0]);
            });
            for (int t = 1; t <= n; t++) {
                int tt = t;
                RuneKit.later(s.start() + t, () -> {
                    if (from[0] == null || !alive(sp, life, gen, stuck, placed)) return;
                    double u = tt / (double) n;
                    Vec3 p = s.peak() > 0 ? RuneKit.leapPoint(from[0], to[0], s.peak(), u) : V.add(from[0], V.sub(to[0], from[0]), ease(u, s.ease()));
                    if (!Kit.moveTo(sp, p)) {
                        stuck[0] = true;
                        return;
                    }
                    placed[0] = sp.position();
                    Kit.launch(sp, 0, 0, 0); // 떨어지지 않게 (공중에 떠 있음) — 다음 틱에 다시 놓음
                    Kit.noFall(sp, 20);
                });
            }
        }
    }

    /** 이 움직임이 아직 이어지는지: 같은 삶 · 같은 번호 · 막히지 않음 · 마지막에 놓은 자리에서 1칸 안 (넘으면 다른 것이 옮긴 것 — 그만) */
    private static boolean alive(ServerPlayer sp, String life, int gen, boolean[] stuck, Vec3[] placed) {
        if (stuck[0] || !RuneKit.sameLife(sp, life) || gen(sp) != gen) return false;
        if (placed[0] != null && sp.position().distanceToSqr(placed[0]) > 1.0) {
            stuck[0] = true;
            return false;
        }
        return true;
    }

    /**
     * 떠오름 (움직일 수 있게 — 노래하며 걷는 스킬): 공중 부양 rise 틱 (amp 세기 — 약 1칸) 뒤 천천히 떨어짐 floatTicks 틱. 매 틱 옮기지 않으므로 앞뒤로 움직일 수 있음
     */
    static void floatUp(ServerPlayer sp, int rise, int floatTicks, int amp) {
        sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.LEVITATION, Math.max(1, rise), amp, false, false));
        sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, Math.max(1, rise + floatTicks), 0, false, false));
        Kit.noFall(sp, rise + floatTicks + 30);
    }

    static double ease(double u, int e) {
        u = Math.max(0, Math.min(1, u));
        return switch (e) {
            case OUT -> 1 - (1 - u) * (1 - u);
            case IN -> u * u;
            case INOUT -> u < 0.5 ? 2 * u * u : 1 - 2 * (1 - u) * (1 - u);
            default -> u;
        };
    }

    // ------------------------------------------------------------ 자리 (구간의 target)

    /** 위로 h 칸 (머리 위가 막히면 닿는 데까지 — 1칸 여유) */
    static UnaryOperator<Vec3> up(ServerPlayer sp, double h) {
        return from -> {
            double room = RuneKit.rayLength(sp.level(), V.up(from, sp.getBbHeight()), V.UP, h + 1) - 1;
            return V.up(from, Math.max(0, Math.min(h, room)));
        };
    }

    /** 보는 쪽 (수평) 으로 dist 칸 · 위로 h 칸 (벽 앞까지) */
    static UnaryOperator<Vec3> ahead(ServerPlayer sp, double dist, double h) {
        return from -> {
            Vec3 f = Kit.facingOf(sp);
            double can = dist > 0 ? RuneKit.dashDistance(sp, f, dist, 0.3, 1.5) : -RuneKit.dashDistance(sp, V.scale(f, -1), -dist, 0.3, 1.5);
            return V.up(V.add(from, f, can), h);
        };
    }

    /** 오른쪽 (수평 — 음수면 왼쪽) 으로 dist 칸 (벽 앞까지) */
    static UnaryOperator<Vec3> side(ServerPlayer sp, double dist) {
        return from -> {
            Vec3 r = V.rightOf(Kit.facingOf(sp));
            Vec3 d = dist >= 0 ? r : V.scale(r, -1);
            double can = RuneKit.dashDistance(sp, d, Math.abs(dist), 0.3, 1.5);
            return V.add(from, d, can);
        };
    }

    /** 보는 쪽 dist 칸 앞의 땅 (그 아래 단단한 땅 — 없으면 쓴 때의 땅 높이 ground) */
    static UnaryOperator<Vec3> landAhead(ServerPlayer sp, double dist, double ground) {
        return from -> {
            Vec3 f = Kit.facingOf(sp);
            double can = dist > 0 ? RuneKit.dashDistance(sp, f, dist, 0.3, 1.5) : 0;
            Vec3 p = V.add(from, f, can);
            Double gy = RuneKit.solidGround(sp.level(), new Vec3(p.x, Math.max(from.y, ground) + 0.5, p.z), from.y - ground + 8);
            return new Vec3(p.x, gy != null ? gy : ground, p.z);
        };
    }

    /** 그 자리 아래 땅 (내려앉기 — 없으면 쓴 때의 땅 높이 ground) */
    static UnaryOperator<Vec3> down(ServerPlayer sp, double ground) {
        return from -> {
            Double gy = RuneKit.solidGround(sp.level(), V.up(from, 0.5), from.y - ground + 8);
            return new Vec3(from.x, gy != null ? gy : ground, from.z);
        };
    }

    /** 정한 자리 (그대로) */
    static UnaryOperator<Vec3> at(Vec3 p) {
        return from -> p;
    }

    /** 땅 위 몇 칸에 떠 있는지 (땅이 없으면 0) */
    static double airborne(ServerPlayer sp) {
        ServerLevel level = sp.level();
        Double gy = RuneKit.solidGround(level, V.up(sp.position(), 0.1), 12);
        return gy == null ? 0 : Math.max(0, sp.getY() - gy);
    }
}
