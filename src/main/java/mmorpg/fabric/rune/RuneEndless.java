package mmorpg.fabric.rune;

import java.util.List;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.17.0 넷째 한정 초월 룬 무간참역 (endlessSlash — 사용자 요청 "한정 룬으로 무수한 참격 공격" 영역 룬, 이름 · 모습은 오리지널): 다른 한정 룬처럼
 * 위력 P 의 적용 스탯 = 네 스탯의 합 × 1.0, 마법 피해, 피해는 몹 무적 시간을 피해 (RuneLimited.hit), 끝에 총 피해 글자. 수치는 RUNES.endless (rules_more.json),
 * 돌파는 rune_break.json (3 · 6돌 + 6돌 하늘 가르기 — finale, 피해 없음).
 * <ol>
 *   <li>전개 (charge 틱): 쓴 자리 (영역은 그 자리에 남음 — 시전자는 움직여도 됨) 에서 고리가 반지름 radius 칸까지 퍼지고 바닥에 먹빛 검진 문양,
 *       경계에 검 (반지름 × 1.5 자루) 이 차례로 땅에서 솟아 한가운데를 겨눈 채 떠 있음</li>
 *   <li>베기 (gap 틱마다 hits 번): 영역 안 모든 적에게 위력 × mult (흰 기울임 숫자), 맞은 적 몸에 엇갈린 베인 자국 · X 참격.
 *       그 사이 매 틱 영역 안 공중에 먹빛 테두리 + 흰 칼날의 참격 (베인 자국 셋 · 2틱마다 초승달 참격) 이 쉴 새 없이 그어지고 경계에는 먹빛 장막이 일렁임</li>
 *   <li>마무리 일섬 (마지막 베기 finish 틱 뒤): 경계의 검이 한가운데로 모인 뒤 영역을 가로지르는 흰 일섬 한 줄 → 영역 곳곳에 한꺼번에 베인 자국,
 *       영역 안 모든 적 위력 × crush (굵은 흰 기울임 숫자 !) · 밀쳐냄 · 띄움 (6돌 기절 stun 틱), 바닥이 갈라짐</li>
 *   <li>6돌 하늘 가르기: 일섬 뒤 앞쪽 하늘이 X자로 갈라지고 갈라진 곳에서 빛 · 검 조각이 영역으로 쏟아짐 (피해 없음)</li>
 * </ol>
 * 화면 색 연출 (하늘빛 · 번쩍임) 은 3돌부터 (중력 블랙홀의 공허 하늘 — RuneKit.ULT_ALIAS). 쓰러지면 남은 것은 멈춤.
 */
final class RuneEndless {
    private RuneEndless() {}

    /** 먹빛 (참격 바깥 — 알파로 겹쳐 낮 하늘에서도 또렷) · 흰 칼날 (심) · 보랏빛 (빛무리 · 고리) */
    static final double[] INK = {0.06, 0.02, 0.1};
    static final double[] EDGE = {0.95, 0.93, 1.0};
    static final double[] VIOLET = {0.58, 0.38, 0.98};
    static final double[] WHITE = {1, 1, 1};
    /** 공중 참격 모양 (무기 참격 그림 — 곡선 · 가는 · 굵은) */
    private static final String[] SHAPES = {"arc", "thin", "heavy"};
    /** 경계 검이 떠 있는 높이 */
    static final double SWORD_Y = 2.3;

    static void register() {
        RuneCast.register("endlessSlash", RuneEndless::cast);
    }

    /** i 번째 경계 검 자리 (n 자루 · 반지름 R) */
    static Vec3 swordAt(Vec3 c, double R, int i, int n, double y) {
        double a = Math.PI * 2 * i / n;
        return new Vec3(c.x + Math.cos(a) * (R - 0.3), c.y + y, c.z + Math.sin(a) * (R - 0.3));
    }

    /** 영역 안 아무 곳 (가운데 쪽이 너무 몰리지 않게 √ — 높이 lo ~ hi) */
    static Vec3 anywhere(Vec3 c, double R, double lo, double hi) {
        double r = R * Math.sqrt(Kit.rand()) * 0.95;
        double a = Kit.rand() * Math.PI * 2;
        return new Vec3(c.x + Math.cos(a) * r, c.y + lo + Kit.rand() * (hi - lo), c.z + Math.sin(a) * r);
    }

    /** 시전자 눈에서 그 자리로 (베인 자국이 시전자 화면에 정면으로) */
    static Vec3 viewTo(ServerPlayer sp, Vec3 at) {
        Vec3 v = V.sub(at, sp.getEyePosition());
        return V.len(v) > 0.3 ? V.unit(v) : Kit.facingOf(sp);
    }

    /** 먹빛 베인 자국 하나 (나타났다 hold 초 뒤 벌어지며 사라짐) */
    static void cut(ServerLevel level, ServerPlayer sp, Vec3 at, double scale, double hold) {
        SkillFx.cutMark(level, at, viewTo(sp, at), Kit.rand() * 180, hold, INK, EDGE, scale);
    }

    /** 먹빛 초승달 참격 하나 (흰 심 · 보랏빛 바깥 빛) */
    static void crescentCut(ServerLevel level, Vec3 at, double size) {
        SkillFx.arc(level, at, V.UP, Opt.of("shape", SHAPES[(int) (Kit.rand() * SHAPES.length)], "flip", Kit.rand() < 0.5, "size", size, "life", 0.18,
                "spin", Kit.rand() * 360, "color", INK, "aura", VIOLET));
    }

    static boolean cast(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        boolean fin = RuneLimited.finale(sp, r);
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 side = V.rightOf(f);
        double R = k.num("radius");
        int charge = (int) k.num("charge");
        int gap = (int) k.num("gap");
        int hits = (int) k.num("hits");
        int last = charge + (hits - 1) * gap;
        int cutAt = last + (int) k.num("finish");
        int converge = cutAt - 8;
        int swords = (int) Math.round(R * 1.5);
        RuneLimited.Tally tally = new RuneLimited.Tally();
        RuneKit.ultSkyFor(sp, level, c, "endlessSlash", cutAt + 30, null);

        // ---- 전개: 퍼지는 고리 · 바닥 검진 문양 · 경계의 검이 차례로 솟음
        RuneKit.novaRing(level, c, R * 0.22, 4.4, VIOLET, EDGE, Opt.of("life", charge / 20.0 + 0.15, "fade", 0.45));
        RuneKit.domainSigil(level, c, R, "blade", INK, EDGE, Opt.of("life", (cutAt + 10) / 20.0, "spinRate", 5.0, "grow", 0.0, "alpha", 0.92, "fade", 0.12));
        RuneKit.domainSigil(level, c, R * 0.55, "blade", VIOLET, EDGE, Opt.of("life", (cutAt + 6) / 20.0, "spinRate", -12.0, "alpha", 0.55, "fade", 0.15));
        SkillFx.play(level, "block.bell.use", c, 0.5, 1.0);
        SkillFx.play(level, "entity.warden.sonic_charge", c, 1.4, 0.6);
        SkillFx.play(level, "item.trident.riptide_3", c, 0.6, 0.8);
        for (int i = 0; i < swords; i++) {
            final int ii = i;
            final int rise = i * charge / swords;
            RuneKit.later(rise, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 ground = swordAt(c, R, ii, swords, 0.15);
                Vec3 hover = swordAt(c, R, ii, swords, SWORD_Y);
                // 땅에서 솟음 (0.25초) → 한가운데를 겨누고 떠 있음 (모일 때까지)
                RuneKit.rfx2(level, "sword", ground, Opt.of("dir", V.UP, "speed", (SWORD_Y - 0.15) / 0.25, "size", 0.9, "life", 0.25), INK, EDGE);
                SkillFx.sparks(level, ground, EDGE, 3, 3);
                RuneKit.later(5, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    Vec3 aim = V.unit(V.sub(V.up(c, 0.9), hover));
                    RuneKit.rfx2(level, "sword", hover, Opt.of("dir", aim, "size", 0.95, "life", Math.max(0.2, (converge - rise - 5) / 20.0), "fade", 0.06), INK,
                            EDGE);
                });
                if (ii % 3 == 0) SkillFx.play(level, "item.trident.return", hover, 1.3 + ii * 0.02, 0.5);
            });
        }

        // ---- 영역 안 공중의 참격 (매 틱) · 경계의 먹빛 장막 · 베는 소리
        Kit.ticks(t -> {
            if (!RuneKit.sameLife(sp, life) || t >= converge) return false;
            if (t < charge - 4) return true;
            for (int i = 0; i < 3; i++) cut(level, sp, anywhere(c, R, 0.4, 3.0), 1.2 + Kit.rand() * 1.5, 0.05 + Kit.rand() * 0.1);
            if (t % 2 == 0) crescentCut(level, anywhere(c, R, 0.6, 2.8), 1.3 + Kit.rand() * 1.3);
            if (t % 3 == 0) {
                for (int i = 0; i < 2; i++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 b = new Vec3(c.x + Math.cos(a) * R, c.y + 0.1, c.z + Math.sin(a) * R);
                    RuneKit.streak(level, b, V.up(b, 2.2 + Kit.rand() * 1.8), INK, 0.4, 0.35, 0.7, 0);
                }
                SkillFx.play(level, "entity.player.attack.sweep", anywhere(c, R, 1, 2), 1.2 + Kit.rand() * 0.7, 0.35);
            }
            return true;
        });

        // ---- 베기: gap 틱마다 영역 안 모든 적 (hits 번)
        for (int i = 0; i < hits; i++) {
            final int at = charge + i * gap;
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                List<LivingEntity> foes = Kit.around(sp, c, R, 4);
                int drawn = 0;
                for (LivingEntity e : foes) {
                    RuneLimited.hit(sp, life, e, P, k.num("mult"), DamageNumbers.Style.CUT, tally, null);
                    if (drawn++ >= 10) continue; // 적이 많으면 연출은 10명까지 (피해는 모두)
                    Vec3 body = e.getBoundingBox().getCenter();
                    double a0 = Kit.rand() * 180;
                    Vec3 view = viewTo(sp, body);
                    SkillFx.cutMark(level, body, view, a0, 0.08, INK, EDGE, 1.5);
                    SkillFx.cutMark(level, body, view, a0 + 70 + Kit.rand() * 40, 0.12, INK, EDGE, 1.3);
                    SkillFx.sparks(level, body, EDGE, 4, 4);
                }
                if (!foes.isEmpty()) SkillFx.play(level, "entity.player.attack.sweep", c, 0.7 + Kit.rand() * 0.5, 1.0);
                SkillFx.play(level, "entity.player.attack.strong", c, 1.6 + Kit.rand() * 0.3, 0.4);
            });
        }

        // ---- 마무리 일섬: 경계의 검이 한가운데로 → 영역을 가로지르는 흰 일섬 → 한꺼번에 베인 자국 · 피해
        RuneKit.later(converge, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 mid = V.up(c, 0.9);
            for (int i = 0; i < swords; i++) {
                Vec3 p = swordAt(c, R, i, swords, SWORD_Y);
                Vec3 d = V.sub(mid, p);
                RuneKit.rfx2(level, "sword", p, Opt.of("dir", V.unit(d), "speed", V.len(d) / 0.35, "size", 0.95, "life", 0.35), INK, EDGE);
            }
            SkillFx.play(level, "item.trident.riptide_1", c, 0.7, 1.0);
            SkillFx.play(level, "mob.breeze.charge", c, 0.8, 0.8);
        });
        RuneKit.later(cutAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            RuneKit.ultFlashFor(sp, level, c, "endlessSlash", "flash", Opt.empty());
            // 일섬: 시전자 앞쪽에서 영역을 옆으로 가로지름 (1인칭 화면을 가로지르는 한 줄)
            Vec3 m = V.up(V.add(c, f, R * 0.45), 1.3);
            Vec3 a = V.add(m, side, -(R + 1.5));
            Vec3 b = V.add(m, side, R + 1.5);
            RuneKit.streak(level, a, b, INK, 1.2, 0.55, 0.9, 0);
            RuneKit.streak(level, a, b, VIOLET, 0.6, 0.45, 0.85, 0);
            RuneKit.streak(level, a, b, EDGE, 0.3, 0.4, 1.0, 0);
            RuneKit.streak(level, a, b, WHITE, 0.12, 0.3, 1.0, 0);
            SkillFx.burst(level, m, EDGE, 2.2, 0.3);
            RuneKit.novaRing(level, c, R * 0.3, 3.4, VIOLET, EDGE, Opt.of("life", 0.5));
            RuneKit.glowCrack(level, c, R * 0.95, INK, VIOLET, Opt.of("life", 2.2, "fade", 0.6, "v", 1.0));
            RuneKit.shakeAround(level, c, R + 4, 0.5, 0.5);
            SkillFx.play(level, "entity.player.attack.sweep", c, 0.5, 1.4);
            SkillFx.play(level, "entity.warden.sonic_boom", c, 1.7, 0.5);
            SkillFx.play(level, "block.bell.use", c, 0.7, 1.0);
            SkillFx.play(level, "random.explode", c, 1.3, 0.5);
            // 한꺼번에 베인 자국 (영역 곳곳)
            for (int i = 0; i < 14; i++) cut(level, sp, anywhere(c, R, 0.4, 2.6), 2.2 + Kit.rand() * 1.4, 0.14);
            int stun = (int) k.numOr("stun", 0);
            int[] marks = {0};
            for (LivingEntity e : Kit.around(sp, c, R, 5)) {
                Vec3 body = e.getBoundingBox().getCenter();
                if (marks[0] < 10) SkillFx.fx(level, "rpg:slash_x", body, Opt.of("dir", viewTo(sp, body), "size", 1.6, "life", 0.3, "color", EDGE, "alpha", 1.0));
                RuneLimited.hit(sp, life, e, P, k.num("crush"), DamageNumbers.Style.ISSEOM, tally, x -> {
                    RuneKit.knock(x, V.sub(x.position(), c), 0.7, k.numOr("lift", 0.5));
                    if (stun > 0) RuneKit.stun(x, stun, VIOLET, marks[0]++ < 10);
                });
            }
        });
        if (fin) RuneKit.later(cutAt + 4, () -> skySplit(sp, level, c, f, R, life));
        RuneLimited.total(sp, "endlessSlash", V.up(V.add(c, f, R * 0.45), 3.0), tally, cutAt + 10);
        return true;
    }

    /** 6돌 하늘 가르기: 앞쪽 하늘이 X자로 갈라지고 빛 · 검 조각이 영역으로 쏟아짐 (피해 없음) */
    static void skySplit(ServerPlayer sp, ServerLevel level, Vec3 c, Vec3 f, double R, String life) {
        if (!RuneKit.sameLife(sp, life)) return;
        Vec3 side = V.rightOf(f);
        Vec3 x0 = V.up(V.add(c, f, R + 3), 6.5);
        double s = 4.6;
        Vec3[][] arms = {{V.add(V.add(x0, side, -s), V.UP, -s), V.add(V.add(x0, side, s), V.UP, s)},
            {V.add(V.add(x0, side, -s), V.UP, s), V.add(V.add(x0, side, s), V.UP, -s)}};
        for (int i = 0; i < 2; i++) {
            Vec3 a = arms[i][0];
            Vec3 b = arms[i][1];
            double delay = i * 0.15;
            RuneKit.streak(level, a, b, INK, 1.6, 1.6, 0.9, delay);
            RuneKit.streak(level, a, b, VIOLET, 0.8, 1.5, 0.85, delay);
            RuneKit.streak(level, a, b, EDGE, 0.36, 1.4, 1.0, delay);
            RuneKit.streak(level, a, b, WHITE, 0.14, 1.2, 1.0, delay);
        }
        RuneKit.skyFlare(level, x0, 3.6, VIOLET, WHITE, Opt.of("life", 1.2, "grow", 0.3, "fade", 0.5));
        RuneKit.godRays(sp, level, c, R * 0.8, 8, EDGE, WHITE, Opt.of("h", 22.0, "life", 1.6, "alpha", 0.8, "skipNear", true));
        SkillFx.play(level, "block.glass.break", x0, 0.5, 1.0);
        SkillFx.play(level, "item.trident.thunder", c, 1.3, 0.7);
        // 검 조각이 갈라진 곳에서 영역으로 쏟아짐 (1.5초 동안 스물)
        for (int n = 0; n < 20; n++) {
            RuneKit.later(2 + n * 3 / 2, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 from = V.add(V.add(x0, side, (Kit.rand() - 0.5) * 2 * s * 0.8), V.UP, (Kit.rand() - 0.5) * 2 * s * 0.6);
                Vec3 to = anywhere(c, R, 0.1, 0.3);
                Vec3 d = V.sub(to, from);
                double secs = Math.max(0.2, V.len(d) / 24);
                RuneKit.rfx2(level, "sword", from, Opt.of("dir", V.unit(d), "speed", V.len(d) / secs, "size", 0.6, "life", secs), INK, EDGE);
                RuneKit.later((int) Math.round(secs * 20), () -> {
                    SkillFx.sparks(level, V.up(to, 0.2), EDGE, 4, 3);
                    RuneKit.novaRing(level, to, 0.5, 1.6, VIOLET, EDGE, Opt.of("life", 0.35));
                });
            });
        }
    }
}
