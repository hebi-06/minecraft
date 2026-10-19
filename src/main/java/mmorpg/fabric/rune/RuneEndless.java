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
 * 위력 P 의 적용 스탯 = 네 스탯의 합 × 1.0, 마법 피해, 피해는 몹 무적 시간을 피해 (RuneLimited.hit). 수치는 RUNES.endless (rules_more.json),
 * 돌파는 rune_break.json (3 · 6돌 + 6돌 하늘 가르기 — finale, 피해 없음).
 * <ol>
 *   <li>전개 (charge 틱): 쓴 자리 (영역은 그 자리에 남음 — 시전자는 움직여도 됨) 에서 고리가 반지름 radius 칸까지 퍼지고 바닥에 먹빛 검진 문양,
 *       경계에 검 (반지름 × 1.5 자루) 이 차례로 하늘에서 떨어져 땅에 꽂힘 (칼끝이 아래 — 한가운데에서 봐도 세로로 다 보이게)</li>
 *   <li>베기 (gap 틱마다 hits 번): 영역 안 모든 적에게 위력 × mult (흰 기울임 숫자), 맞은 적 몸에 엇갈린 베인 자국 · X 참격.
 *       그 사이 매 틱 영역 안 공중에 먹빛 테두리 + 흰 칼날의 참격 (베인 자국 셋 · 2틱마다 초승달 참격) 이 쉴 새 없이 그어지고 경계에는 먹빛 장막이 일렁임
 *       (공중 참격은 시전자 눈 · 3인칭 카메라에서 3칸 안에는 그리지 않음 — 1인칭 화면을 덮지 않게)</li>
 *   <li>마무리 일섬 (마지막 베기 finish 틱 뒤): 꽂힌 검이 뽑혀 한가운데 둘레로 모인 뒤 영역을 가로지르는 흰 일섬 한 줄 (왼쪽 → 오른쪽으로 0.15초에 그어짐)
 *       → 영역 곳곳에 한꺼번에 베인 자국, 영역 안 모든 적 위력 × crush (굵은 흰 기울임 숫자 !) · 밀쳐냄 · 띄움 (6돌 기절 stun 틱), 바닥이 갈라짐</li>
 *   <li>6돌 하늘 가르기: 일섬 뒤 앞쪽 하늘이 X자로 크게 갈라지고 (빛나는 금 · 섬광) 갈라진 곳에서 검 조각이 영역으로 쏟아짐 (피해 없음)</li>
 * </ol>
 * 화면 색 연출 (하늘빛 · 번쩍임) 은 3돌부터 (검붉은 하늘 rpg:ult_bloodvoid · 진홍 번쩍임 — rules_more ULT_RUNES_MORE, 번쩍임은 일섬이 다 그어진 뒤 짧게).
 * 쓰러지면 남은 것은 멈춤. 0.25.2: 색을 보랏빛에서 검붉은 · 진홍빛으로 (사용자 "무간참역 보라색에서 붉고 어두운 빨간색으로")
 */
final class RuneEndless {
    private RuneEndless() {}

    /** 검붉은 먹 (참격 바깥 — 알파로 겹쳐 낮 하늘에서도 또렷) · 흰 칼날 (심 — 붉은 기가 조금) · 진홍 (빛무리 · 고리 · 일섬 — 0.25.2 보랏빛에서) */
    static final double[] INK = {0.12, 0.0, 0.02};
    static final double[] EDGE = {1.0, 0.92, 0.92};
    static final double[] CRIMSON = {0.74, 0.04, 0.1};
    static final double[] WHITE = {1, 1, 1};
    /** 공중 참격 모양 (무기 참격 그림 — 곡선 · 가는 · 굵은) */
    private static final String[] SHAPES = {"arc", "thin", "heavy"};
    /** 경계 검: 크기 (길이 2 × SWORD_SIZE 칸) · 꽂힌 검 가운데 높이 (칼끝 0.3칸이 땅속) · 떨어지기 시작하는 높이 */
    static final double SWORD_SIZE = 1.2;
    static final double SWORD_Y = SWORD_SIZE - 0.3;
    static final double SWORD_TOP = 4.2;
    /** 공중 참격을 그리지 않는 거리 (시전자 눈 · 3인칭 카메라에서) */
    static final double CLEAR = 3.0;

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

    /** anywhere 중 시전자 눈 · 3인칭 카메라에서 CLEAR 칸 밖인 곳 (여섯 번 골라도 없으면 마지막 것) — 가까운 참격이 화면을 덮지 않게 */
    static Vec3 anywhereAway(ServerPlayer sp, Vec3 c, double R, double lo, double hi) {
        Vec3 p = anywhere(c, R, lo, hi);
        for (int i = 0; i < 6 && RuneKit.camDist(sp, p) < CLEAR; i++) p = anywhere(c, R, lo, hi);
        return p;
    }

    /** 시전자 눈에서 그 자리로 (베인 자국이 시전자 화면에 정면으로) */
    static Vec3 viewTo(ServerPlayer sp, Vec3 at) {
        Vec3 v = V.sub(at, sp.getEyePosition());
        return V.len(v) > 0.3 ? V.unit(v) : Kit.facingOf(sp);
    }

    /** 검붉은 베인 자국 하나 (나타났다 hold 초 뒤 벌어지며 사라짐) */
    static void cut(ServerLevel level, ServerPlayer sp, Vec3 at, double scale, double hold) {
        SkillFx.cutMark(level, at, viewTo(sp, at), Kit.rand() * 180, hold, INK, EDGE, scale);
    }

    /** 검붉은 초승달 참격 하나 (흰 심 · 진홍 바깥 빛) */
    static void crescentCut(ServerLevel level, Vec3 at, double size) {
        SkillFx.arc(level, at, V.UP, Opt.of("shape", SHAPES[(int) (Kit.rand() * SHAPES.length)], "flip", Kit.rand() < 0.5, "size", size, "life", 0.18,
                "spin", Kit.rand() * 360, "color", INK, "aura", CRIMSON));
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
        // 하늘빛 (3돌부터): 영역이 끝나고 1.5초 뒤까지 — 6돌의 하늘빛 시간 배율 (skyTicks 1.5) 을 나눠 둠 (긴 영역 룬이라 배율을 곱하면 14초나 남음)
        double skyK = skyTicksOf(sp);
        RuneKit.ultSkyFor(sp, level, c, "endlessSlash", (int) Math.round((cutAt + 30) / skyK), null);

        // ---- 전개: 퍼지는 고리 · 바닥 검진 문양 · 경계의 검이 차례로 솟음
        RuneKit.novaRing(level, c, R * 0.22, 4.4, CRIMSON, EDGE, Opt.of("life", charge / 20.0 + 0.15, "fade", 0.45));
        RuneKit.domainSigil(level, c, R, "blade", INK, EDGE, Opt.of("life", (cutAt + 10) / 20.0, "spinRate", 5.0, "grow", 0.0, "alpha", 0.92, "fade", 0.12));
        RuneKit.domainSigil(level, c, R * 0.55, "blade", CRIMSON, EDGE, Opt.of("life", (cutAt + 6) / 20.0, "spinRate", -12.0, "alpha", 0.55, "fade", 0.15));
        SkillFx.play(level, "block.bell.use", c, 0.5, 1.0);
        SkillFx.play(level, "entity.warden.sonic_charge", c, 1.4, 0.6);
        SkillFx.play(level, "item.trident.riptide_3", c, 0.6, 0.8);
        for (int i = 0; i < swords; i++) {
            final int ii = i;
            final int drop = i * charge / swords;
            RuneKit.later(drop, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 top = swordAt(c, R, ii, swords, SWORD_TOP);
                Vec3 planted = swordAt(c, R, ii, swords, SWORD_Y);
                Vec3 ground = swordAt(c, R, ii, swords, 0.1);
                // 하늘에서 떨어짐 (0.15초) → 땅에 꽂힘 (칼끝이 아래 · 바깥으로 조금 기욺 — 모일 때까지)
                RuneKit.rfx2(level, "sword", top, Opt.of("dir", V.DOWN, "speed", (SWORD_TOP - SWORD_Y) / 0.15, "size", SWORD_SIZE, "life", 0.15), INK, EDGE);
                RuneKit.later(3, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    Vec3 out = V.unit(V.sub(new Vec3(planted.x, c.y, planted.z), c));
                    Vec3 tilt = V.unit(V.add(V.DOWN, out, 0.14));
                    RuneKit.rfx2(level, "sword", planted, Opt.of("dir", tilt, "size", SWORD_SIZE, "life", Math.max(0.2, (converge - drop - 3) / 20.0), "fade", 0.04),
                            INK, EDGE);
                    SkillFx.sparks(level, ground, EDGE, 5, 3);
                    RuneKit.novaRing(level, ground, 0.3, 1.3, CRIMSON, EDGE, Opt.of("life", 0.3));
                    if (ii % 2 == 0) SkillFx.play(level, "item.trident.hit_ground", ground, 1.1 + Kit.rand() * 0.3, 0.7);
                });
            });
        }

        // ---- 영역 안 공중의 참격 (매 틱) · 경계의 먹빛 장막 · 베는 소리
        Kit.ticks(t -> {
            if (!RuneKit.sameLife(sp, life) || t >= converge) return false;
            if (t < charge - 4) return true;
            for (int i = 0; i < 3; i++) cut(level, sp, anywhereAway(sp, c, R, 0.4, 3.0), 1.2 + Kit.rand() * 1.5, 0.05 + Kit.rand() * 0.1);
            if (t % 2 == 0) crescentCut(level, anywhereAway(sp, c, R, 0.6, 2.8), 1.3 + Kit.rand() * 1.3);
            if (t % 3 == 0) {
                for (int i = 0; i < 2; i++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 b = new Vec3(c.x + Math.cos(a) * R, c.y + 0.1, c.z + Math.sin(a) * R);
                    RuneKit.streak(level, b, V.up(b, 2.2 + Kit.rand() * 1.8), INK, 0.4, 0.35, 0.7, 0);
                }
                SkillFx.play(level, "entity.player.attack.sweep", anywhereAway(sp, c, R, 1, 2), 1.2 + Kit.rand() * 0.7, 0.35);
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

        // ---- 마무리 일섬: 꽂힌 검이 뽑혀 한가운데 둘레로 → 영역을 가로지르는 흰 일섬 → 한꺼번에 베인 자국 · 피해
        RuneKit.later(converge, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            for (int i = 0; i < swords; i++) {
                Vec3 p = swordAt(c, R, i, swords, SWORD_Y);
                Vec3 out = V.unit(V.sub(new Vec3(p.x, c.y, p.z), c));
                Vec3 to = V.up(V.add(c, out, GATHER_R), 1.0); // 한가운데 둘레 (시전자 눈 앞을 덮지 않게 GATHER_R 칸 밖에서 멈춤)
                Vec3 d = V.sub(to, p);
                RuneKit.rfx2(level, "sword", p, Opt.of("dir", V.unit(d), "speed", V.len(d) / 0.3, "size", SWORD_SIZE, "life", 0.4, "fade", 0.25), INK, EDGE);
            }
            SkillFx.play(level, "item.trident.riptide_1", c, 0.7, 1.0);
            SkillFx.play(level, "mob.breeze.charge", c, 0.8, 0.8);
        });
        RuneKit.later(cutAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            // 일섬: 시전자 앞쪽에서 영역을 옆으로 가로지름 (1인칭 화면을 가로지르는 한 줄 — 왼쪽 → 오른쪽으로 그어지고, 다 그어진 뒤 짧게 번쩍임)
            Vec3 m = V.up(V.add(c, f, R * 0.45), 1.3);
            Vec3 a = V.add(m, side, -(R + 1.5));
            Vec3 b = V.add(m, side, R + 1.5);
            sweep(level, a, b, 6, 0.15);
            RuneKit.later(3, () -> RuneKit.ultFlashFor(sp, level, c, "endlessSlash", "flash", Opt.of("time", FLASH_TIME.clone())));
            SkillFx.burst(level, m, EDGE, 2.2, 0.3);
            RuneKit.novaRing(level, c, R * 0.3, 3.4, CRIMSON, EDGE, Opt.of("life", 0.5));
            RuneKit.glowCrack(level, c, R * 0.95, INK, CRIMSON, Opt.of("life", 2.2, "fade", 0.6, "v", 1.0));
            RuneKit.shakeAround(level, c, R + 4, 0.5, 0.5);
            SkillFx.play(level, "entity.player.attack.sweep", c, 0.5, 1.4);
            SkillFx.play(level, "entity.warden.sonic_boom", c, 1.7, 0.5);
            SkillFx.play(level, "block.bell.use", c, 0.7, 1.0);
            SkillFx.play(level, "random.explode", c, 1.3, 0.5);
            // 한꺼번에 베인 자국 (영역 곳곳)
            for (int i = 0; i < 14; i++) cut(level, sp, anywhereAway(sp, c, R, 0.4, 2.6), 2.2 + Kit.rand() * 1.4, 0.14);
            int stun = (int) k.numOr("stun", 0);
            int[] marks = {0};
            for (LivingEntity e : Kit.around(sp, c, R, 5)) {
                Vec3 body = e.getBoundingBox().getCenter();
                if (marks[0] < 10) SkillFx.fx(level, "rpg:slash_x", body, Opt.of("dir", viewTo(sp, body), "size", 1.6, "life", 0.3, "color", EDGE, "alpha", 1.0));
                RuneLimited.hit(sp, life, e, P, k.num("crush"), DamageNumbers.Style.ISSEOM, tally, x -> {
                    RuneKit.knock(x, V.sub(x.position(), c), 0.7, k.numOr("lift", 0.5));
                    if (stun > 0) RuneKit.stun(x, stun, CRIMSON, marks[0]++ < 10);
                });
            }
        });
        if (fin) RuneKit.later(cutAt + 4, () -> skySplit(sp, level, c, f, R, life, skyK));
        // 시전 기록 (피해 합 · 타격 수 — 시험): 일섬이 다 들어간 뒤
        RuneLimited.saveTally(sp, "endlessSlash", tally, cutAt + (fin ? 22 : 10));
        return true;
    }

    /** 이 사람이 지금 쓴 무간참역의 하늘빛 시간 배율 (3돌 미만이면 1) */
    static double skyTicksOf(ServerPlayer sp) {
        mmorpg.core.RuneBreak.Fx fx = Breakthrough.castFx(sp, "endlessSlash");
        return fx != null && fx.skyTicks() > 0 ? fx.skyTicks() : 1;
    }

    /** 일섬 한 줄: a → b 를 n 조각으로 나눠 secs 동안 차례로 그음 (조각 양끝 35% 겹침 — 끊겨 보이지 않게). 검붉은 먹 · 진홍 · 흰 칼날 · 흰 심 네 겹 */
    static void sweep(ServerLevel level, Vec3 a, Vec3 b, int n, double secs) {
        Vec3 d = V.sub(b, a);
        for (int k = 0; k < n; k++) {
            Vec3 p0 = V.add(a, d, Math.max(0, (k - 0.35) / n));
            Vec3 p1 = V.add(a, d, Math.min(1, (k + 1.35) / n));
            double delay = secs * k / n;
            RuneKit.streak(level, p0, p1, INK, 1.5, 0.9, 0.9, delay);
            RuneKit.streak(level, p0, p1, CRIMSON, 0.8, 0.8, 0.9, delay);
            RuneKit.streak(level, p0, p1, EDGE, 0.42, 0.7, 1.0, delay);
            RuneKit.streak(level, p0, p1, WHITE, 0.18, 0.6, 1.0, delay);
        }
    }

    /** 일섬 번쩍임 (들어옴 · 머묾 · 걷힘 초 — 3 · 6돌 돌파가 머묾 · 걷힘을 늘림): 기본보다 짧게 (일섬 줄이 번쩍임에 묻히지 않게) */
    static final double[] FLASH_TIME = {0.03, 0.0, 0.25};
    /** 일섬 직전 검이 모여 멈추는 둘레 (한가운데에서) */
    static final double GATHER_R = 2.8;

    /**
     * 6돌 하늘 가르기: 앞쪽 하늘이 X자로 크게 갈라지고 (빛나는 금 · 섬광) 검 조각이 영역으로 쏟아짐 (피해 없음). 갈라지는 순간 하늘빛이 검붉은 하늘 (rpg:ult_bloodvoid
     * — 6돌은 안개가 3 ~ 24칸이라 먼 그림이 어두워짐) 에서 진홍 (rpg:ult_crimson — 안개 8 ~ 56칸) 으로 바뀜 = 갈라진 하늘로 빛이 듦. X 는 영역 안쪽 하늘 (앞으로
     * 반지름 × 0.75 · 높이 4.8칸 — 1인칭 화면 위쪽 안, 3인칭 과녁 뒤)
     */
    static void skySplit(ServerPlayer sp, ServerLevel level, Vec3 c, Vec3 f, double R, String life, double skyK) {
        if (!RuneKit.sameLife(sp, life)) return;
        RuneKit.ultSkyFor(sp, level, c, "endlessSlash", (int) Math.round(70 / skyK), "rpg:ult_crimson");
        Vec3 side = V.rightOf(f);
        Vec3 x0 = V.up(V.add(c, f, R * 0.75), 4.8);
        double s = 3.2;
        Vec3[][] arms = {{V.add(V.add(x0, side, -s), V.UP, -s), V.add(V.add(x0, side, s), V.UP, s)},
            {V.add(V.add(x0, side, -s), V.UP, s), V.add(V.add(x0, side, s), V.UP, -s)}};
        for (int i = 0; i < 2; i++) {
            Vec3 a = arms[i][0];
            Vec3 b = arms[i][1];
            double delay = i * 0.15;
            RuneKit.streak(level, a, b, CRIMSON, 2.6, 2.2, 0.95, delay);
            RuneKit.streak(level, a, b, EDGE, 1.1, 2.0, 1.0, delay);
            RuneKit.streak(level, a, b, WHITE, 0.45, 1.8, 1.0, delay);
        }
        // 갈라진 하늘: 빛나는 금 (시전자 쪽을 보는 면) · 섬광
        Vec3 toward = V.unit(V.sub(sp.getEyePosition(), x0));
        RuneKit.glowCrack(level, x0, s * 1.6, CRIMSON, WHITE, Opt.of("dir", toward, "life", 2.4, "fade", 0.5, "v", 1.0, "grow", 0.15, "spin", 0.0));
        RuneKit.skyFlare(level, x0, 4.0, CRIMSON, WHITE, Opt.of("life", 1.4, "grow", 0.3, "fade", 0.5));
        SkillFx.play(level, "block.glass.break", x0, 0.5, 1.0);
        SkillFx.play(level, "item.trident.thunder", c, 1.3, 0.7);
        // 검 조각이 갈라진 곳에서 영역으로 쏟아짐 (1.2초 동안 스물넷)
        for (int n = 0; n < 24; n++) {
            RuneKit.later(2 + n, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 from = V.add(V.add(x0, side, (Kit.rand() - 0.5) * 2 * s * 0.8), V.UP, (Kit.rand() - 0.5) * 2 * s * 0.6);
                Vec3 to = anywhereAway(sp, c, R, 0.1, 0.3);
                Vec3 d = V.sub(to, from);
                double secs = Math.max(0.2, V.len(d) / 26);
                RuneKit.rfx2(level, "sword", from, Opt.of("dir", V.unit(d), "speed", V.len(d) / secs, "size", 1.0, "life", secs), INK, EDGE);
                RuneKit.later((int) Math.round(secs * 20), () -> {
                    SkillFx.sparks(level, V.up(to, 0.2), EDGE, 4, 3);
                    RuneKit.novaRing(level, to, 0.5, 1.6, CRIMSON, EDGE, Opt.of("life", 0.35));
                });
            });
        }
    }
}
