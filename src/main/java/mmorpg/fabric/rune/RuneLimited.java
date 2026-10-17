package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import mmorpg.core.Obj;
import mmorpg.core.RuneBreak;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.16.0 한정 초월 룬 셋 (자바 전용 — 사용자 요청 '한정 초월 룬, 총 스탯의 합으로 계수'): 위력 P 의 적용 스탯 = 네 스탯의 합 × 1.0 (Runes — coef 넷),
 * 마법 피해 (방어구 무시). 수치는 RUNES.arcana · dipper · skyeye (rules_more.json), 돌파 3 · 6돌은 rune_break.json, 6돌 전용 연출 (finale — 피해 없음).
 * 피해는 몹 무적 시간을 피해 들어가게 (Kit.reserveHit · hitAt — 같은 적에게 10틱마다), 끝나면 이번 시전이 준 피해의 합을 크게 띄움 (DamageNumbers.showTotal).
 * <ol>
 *   <li>만상귀일 (arcanaUnity): 네 스탯의 구슬 (힘 주황 · 민첩 초록 · 체력 분홍 · 마력 파랑 — 크기 = 그 스탯이 총합에서 차지하는 비율) 이 몸 둘레를 돌다
 *       (charge 틱) 조준한 곳 (range 칸) 위로 날아가 돌며, 스탯 차례로 gap 틱마다 구슬마다 한 번 빛줄기 (zone 칸 안의 적 — 위력 × mult × 그 비율, 숫자는 그 스탯 색).
 *       비율 4% 미만인 스탯의 구슬은 빛 점만 (그 몫은 다른 구슬에 비율대로). 마지막 빛줄기 16틱 뒤 구슬이 가운데로 모여 귀일 폭발 (반지름 radius 칸 ·
 *       위력 × crush · 무지갯빛 숫자 · 띄움 · 6돌 기절 stun 틱). 6돌 근원의 프리즘: 하늘에 네 빛의 무지개 + 빛줄기가 쏟아짐</li>
 *   <li>칠성 강림 (sevenStars): 조준한 곳 (range 칸) 위 하늘 height 칸에 북두칠성 (시전자 쪽을 보는 비스듬한 면) — 별이 3틱마다 켜지며 선으로 이어짐,
 *       draw 틱부터 별마다 gap 틱 간격으로 빛기둥 (별 차례로 적을 돌아가며 · 반지름 beam 칸 · 위력 × mult — 6돌은 북극성까지 별 hits 개), 8틱 뒤 별자리가 한 점으로
 *       접혀 큰 별이 떨어짐 (반지름 radius 칸 · 위력 × crush · 띄움). 6돌 별똥별 비: 떨어진 뒤 하늘에 별똥별이 쏟아지고 북극성이 남아 빛남</li>
 *   <li>천공의 눈 (heavenEye): 조준한 곳 위 height 칸에 눈 모양 마법진이 charge 틱 동안 눈을 뜸 → 광선이 hits × gap 틱 동안 내리꽂히며 시전자가 바라보는 곳을
 *       초당 speed 칸으로 따라감 (그동안 시전자 구속 III — 천천히 걸음), gap 틱마다 광선 굵기 width 칸 안의 적 위력 × mult · 불붙음 fire 초,
 *       끝난 자리에서 마무리 폭발 (반지름 radius 칸 · 위력 × crush). 6돌 심판의 눈: 광선 둘레를 빛이 나선으로 감돌고 끝에 해빛 기둥 여덟 · 해무리</li>
 * </ol>
 * 쓰러지거나 차원을 옮기면 남은 것은 멈춤. 화면 색 연출 (하늘빛 · 번쩍임) 은 3돌부터 (돌파 — RuneKit.ULT_ALIAS 의 하늘).
 */
final class RuneLimited {
    private RuneLimited() {}

    static final double[] WHITE = {1, 1, 1};
    static final double[] PEARL = {0.9, 0.84, 1};
    static final double[] STAR_GLOW = {0.6, 0.76, 1};
    static final double[] STAR_CORE = {1, 0.94, 0.72};
    static final double[] SUN = {1, 0.74, 0.25};
    static final double[] SUN_CORE = {1, 0.97, 0.85};
    static final double[] SCORCH = {0.55, 0.16, 0.02};
    /** 별자리 뒤의 밤빛 (낮에도 별이 보이게 — 어두운 빛무리를 알파로 겹침) */
    static final double[] NIGHT = {0.03, 0.05, 0.16};
    /** 구슬 차례 (스탯 순서) · 숫자 모양 */
    static final String[] STATS = {"str", "dex", "vit", "int"};
    static final DamageNumbers.Style[] STAT_STYLE = {DamageNumbers.Style.STAT_STR, DamageNumbers.Style.STAT_DEX, DamageNumbers.Style.STAT_VIT,
            DamageNumbers.Style.STAT_INT};
    /** 이 비율 밑의 스탯은 구슬이 빛 점만 (몫 없음) */
    static final double MIN_SHARE = 0.04;
    /** 북두칠성 (천구 좌표를 펼친 자리 — 칸, 가운데 0: 큰곰자리 α 두베 → η 알카이드) + 6돌 북극성 */
    static final double[][] DIPPER = {{-4.83, 2.63}, {-4.91, 0.38}, {-1.81, -0.76}, {-0.5, 0.63}, {1.81, 0.19}, {3.57, -0.25}, {5.0, -2.6}, {-4.58, 6.2}};
    /** 별을 잇는 선 (두베-메라크-페크다-메그레즈-두베, 메그레즈-알리오스-미자르-알카이드) */
    static final int[][] LINES = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {3, 4}, {4, 5}, {5, 6}};

    static void register() {
        RuneCast.register("arcanaUnity", RuneLimited::arcana);
        RuneCast.register("sevenStars", RuneLimited::stars);
        RuneCast.register("heavenEye", RuneLimited::eye);
    }

    // ------------------------------------------------------------ 공통

    /** 한 번의 시전이 준 피해의 합 (끝에 띄움) · 맞은 횟수 (시험) */
    static final class Tally {
        double sum;
        int hits;
    }

    /** 시험용: 마지막 시전의 피해 합 (룬 함수 이름 → 합) */
    static final Map<String, double[]> LAST = new java.util.HashMap<>();

    /** 룬 타격 한 번 (마법 — 몹 무적 시간을 피해 넣음), 들어가면 합에 더하고 then */
    static void hit(ServerPlayer sp, String life, LivingEntity e, double P, double mult, DamageNumbers.Style style, Tally t, Consumer<LivingEntity> then) {
        if (!(mult > 0)) return;
        Kit.hitAt(sp, e, Kit.reserveHit(sp, e, Tasks.now()), () -> RuneKit.sameLife(sp, life), () -> {
            double d = RuneKit.runeHit(sp, e, P, mult, "int", Kit.Cause.MAGIC, style);
            if (d > 0) {
                t.sum += d;
                t.hits++;
                if (then != null) then.accept(e);
            }
        });
    }

    /** 이번 시전의 총 피해를 at 에 (delay 틱 뒤 — 0 이면 띄우지 않음) */
    static void total(ServerPlayer sp, String fn, Vec3 at, Tally t, int delay) {
        RuneKit.later(delay, () -> {
            LAST.put(sp.getUUID() + "|" + fn, new double[] {t.sum, t.hits});
            if (t.sum > 0 && !sp.isRemoved()) DamageNumbers.showTotal(sp.level(), at, t.sum, "총 피해");
        });
    }

    /** 이 사람이 지금 쓴 이 룬의 6돌 전용 연출 (finale) 이 켜졌는지 */
    static boolean finale(ServerPlayer sp, Runes.Rune r) {
        RuneBreak.Extra ex = RuneBreak.extraFor(r, Breakthrough.castLevel(sp, r.def().str("fn")));
        return ex != null && "finale".equals(ex.type());
    }

    /** 몸 가운데 (빛줄기가 닿는 곳) */
    static Vec3 body(LivingEntity e) {
        return new Vec3(e.getX(), e.getY() + e.getBbHeight() * 0.55, e.getZ());
    }

    /** 땅의 원 위 한 점 */
    static Vec3 ring(Vec3 c, double r, double ang) {
        return new Vec3(c.x + Math.cos(ang) * r, c.y, c.z + Math.sin(ang) * r);
    }

    // ------------------------------------------------------------ 만상귀일

    /** 구슬 하나: 스탯 · 몫 (빛줄기 위력의 비율 — 0 이면 빛 점만) · 크기 비율 (원래 비율) · 색 · 숫자 모양 */
    record Orb(String stat, double share, double raw, double[] color, DamageNumbers.Style style) {}

    /** 네 스탯 → 구슬 넷 (총합이 0 이면 고르게) */
    static List<Orb> orbs(Map<String, Double> st) {
        double total = 0;
        double[] v = new double[4];
        for (int i = 0; i < 4; i++) {
            v[i] = Math.max(0, st.getOrDefault(STATS[i], 0.0));
            total += v[i];
        }
        double[] raw = new double[4];
        double kept = 0;
        for (int i = 0; i < 4; i++) {
            raw[i] = total > 0 ? v[i] / total : 0.25;
            if (raw[i] >= MIN_SHARE) kept += raw[i];
        }
        List<Orb> out = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            double share = raw[i] >= MIN_SHARE && kept > 0 ? raw[i] / kept : 0;
            out.add(new Orb(STATS[i], share, raw[i], RuneKit.colorOf(STATS[i])[0], STAT_STYLE[i]));
        }
        return out;
    }

    /** 시전 중 구슬이 도는 고리 (발 위 높이 · 반지름) — 머리 위: 1인칭 화면 가운데를 가리지 않게 */
    static final double ORBIT_Y = 2.35;
    static final double ORBIT_R = 1.3;
    /** 칠성 강림 밤빛 무리 (rpg:rfx_orb 8번 칸 — 무늬 없는 부드러운 원) */
    static final double HAZE_CELL = 8;

    /**
     * 하늘 그림 높이: 시전자에서 at 까지 거리 × 0.42 (min ~ max 칸) — 1인칭으로 과녁을 보면 그림이 화면 위쪽 안에 들어오게
     * (13.5칸이면 약 5.7칸 — 예전처럼 늘 max 면 눈 · 별자리 윗부분이 화면 위로 잘림)
     */
    static double skyHeight(ServerPlayer sp, Vec3 at, double min, double max) {
        double d = V.len(V.flat(V.sub(at, sp.position())));
        return Math.min(max, Math.max(min, 0.42 * d));
    }

    /** 구슬 그림 (rpg:rfx_orb — 빛 = 스탯 색 · 심지 흰빛, 소용돌이가 돎) */
    static void orbFx(ServerLevel level, Vec3 at, Orb o, double size, double alpha, double life, int i) {
        RuneKit.rfx2(level, "orb", at, Opt.of("size", size, "life", life, "frames", 4.0, "fps", 10.0, "loop", true, "phase", (double) i, "fade", 0.3, "alpha", alpha),
                o.color(), WHITE, new int[] {0, 4}, alpha);
    }

    /** 구슬 크기 (반지름 칸): 비율이 클수록 큼 (빛 점은 작게) */
    static double orbSize(Orb o) {
        return o.share() > 0 ? 0.3 + 0.6 * Math.sqrt(o.raw()) : 0.16;
    }

    static boolean arcana(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        boolean fin = finale(sp, r);
        Vec3 g = RuneKit.groundAim(sp, k.num("range"));
        List<Orb> orbs = orbs(RpgStats.effective(sp));
        List<Integer> active = new ArrayList<>();
        for (int i = 0; i < 4; i++) if (orbs.get(i).share() > 0) active.add(i);
        int charge = (int) k.num("charge");
        int gap = (int) k.num("gap");
        double zone = k.num("zone");
        double radius = k.num("radius");
        double h = k.num("height");
        int volley0 = charge + 8;
        int fuseAt = volley0 + active.size() * gap;
        int blastAt = fuseAt + 6;
        Tally tally = new Tally();
        // 시전: 발밑 비전 문양 · 구슬 넷이 머리 위를 돎 (1인칭 화면 가운데를 가리지 않게 — 위쪽 끝에 보임, 3인칭은 머리 위 고리)
        Vec3 feet = sp.position();
        RuneKit.domainSigil(level, feet, 2.4, "arcane", PEARL, WHITE, Opt.of("life", (charge + 4) / 20.0, "spinRate", 70.0, "grow", 0.2, "alpha", 0.85));
        SkillFx.rune(level, feet, Opt.of("size", 1.6, "life", (charge + 2) / 20.0, "color", PEARL, "alpha", 0.9, "rate", 160.0, "progress", 1.0));
        SkillFx.play(level, "beacon.power", feet, 1.3, 0.9);
        SkillFx.play(level, "chime.amethyst_block", feet, 0.8, 1.0);
        RuneKit.ultSkyFor(sp, level, g, "arcanaUnity", blastAt + 30, null);
        for (int t = 0; t < charge; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 c = V.up(sp.position(), ORBIT_Y);
                for (int i = 0; i < 4; i++) {
                    double a = tt * 0.28 + i * Math.PI / 2;
                    Vec3 p = new Vec3(c.x + Math.cos(a) * ORBIT_R, c.y + Math.sin(tt * 0.3 + i) * 0.15, c.z + Math.sin(a) * ORBIT_R);
                    Orb o = orbs.get(i);
                    orbFx(level, p, o, orbSize(o) * (0.45 + 0.3 * tt / charge), o.share() > 0 ? 0.95 : 0.5, 0.15, i);
                }
            });
        }
        // 날아감: 구슬 → 조준한 곳 위 (반지름 2.2 · 높이 h 의 고리)
        RuneKit.later(charge, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 c = V.up(sp.position(), ORBIT_Y);
            for (int i = 0; i < 4; i++) {
                Orb o = orbs.get(i);
                double a0 = charge * 0.28 + i * Math.PI / 2;
                Vec3 from = new Vec3(c.x + Math.cos(a0) * ORBIT_R, c.y, c.z + Math.sin(a0) * ORBIT_R);
                Vec3 to = V.up(ring(g, 2.2, i * Math.PI / 2), h);
                if (o.share() > 0) RuneKit.flyStreak(level, from, to, 34, o.color(), WHITE, 0.7, 0.3);
            }
            RuneKit.domainSigil(level, g, zone * 1.1, "arcane", PEARL, WHITE, Opt.of("life", (blastAt - charge + 4) / 20.0, "spinRate", 35.0, "alpha", 0.8,
                    "grow", 0.15));
            SkillFx.play(level, "mob.allay.item_given", g, 1.3, 1.0);
            SkillFx.play(level, "item.trident.riptide_2", sp.position(), 1.4, 0.5);
        });
        // 조준한 곳 위에서 돎 (몫이 없는 구슬은 빛 점)
        for (int t = charge + 4; t < fuseAt + 2; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (int i = 0; i < 4; i++) {
                    Orb o = orbs.get(i);
                    orbFx(level, orbAt(g, h, i, tt - charge), o, orbSize(o), o.share() > 0 ? 0.95 : 0.45, 0.15, i);
                }
            });
        }
        // 빛줄기: 스탯 차례로 gap 틱마다 구슬 하나
        for (int j = 0; j < active.size(); j++) {
            final int i = active.get(j);
            final int at = volley0 + j * gap;
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Orb o = orbs.get(i);
                Vec3 from = orbAt(g, h, i, at - charge);
                orbFx(level, from, o, orbSize(o) * 1.6, 1.0, 0.35, i);
                SkillFx.burst(level, from, o.color(), 1.0, 0.3);
                List<LivingEntity> foes = Kit.around(sp, g, zone, 4);
                List<Vec3> spots = new ArrayList<>();
                for (LivingEntity e : foes) spots.add(body(e));
                for (int s = spots.size(); s < 3; s++) spots.add(V.up(ring(g, zone * (0.3 + 0.5 * Kit.rand()), Kit.rand() * Math.PI * 2), 0.3));
                int drawn = 0;
                for (Vec3 p : spots) {
                    if (drawn++ >= 10) break;
                    RuneKit.streak(level, from, p, o.color(), 0.34, 0.35, 0.95, 0);
                    RuneKit.streak(level, from, p, WHITE, 0.12, 0.3, 1.0, 0);
                    RuneKit.novaRing(level, new Vec3(p.x, g.y, p.z), 0.6, 1.6, o.color(), WHITE, Opt.of("life", 0.35));
                    SkillFx.sparks(level, p, o.color(), 5, 4);
                }
                SkillFx.play(level, "chime.amethyst_block", g, new double[] {0.8, 1.0, 1.2, 1.45}[i], 1.0);
                SkillFx.play(level, "random.orb", g, new double[] {0.6, 0.8, 1.0, 1.2}[i], 0.6);
                for (LivingEntity e : foes) hit(sp, life, e, P, k.num("mult") * o.share(), o.style(), tally, null);
            });
        }
        // 모임 → 귀일 폭발
        RuneKit.later(fuseAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 c = V.up(g, h);
            for (int i = 0; i < 4; i++) {
                Orb o = orbs.get(i);
                if (o.share() > 0) RuneKit.flyStreak(level, orbAt(g, h, i, fuseAt - charge), c, 20, o.color(), WHITE, 0.6, 0.28);
            }
            SkillFx.play(level, "beacon.activate", c, 1.4, 1.0);
        });
        RuneKit.later(fuseAt + 4, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 c = V.up(g, h);
            RuneKit.skyFlare(level, c, 3.0, WHITE, WHITE, Opt.of("life", 0.45, "grow", 0.6));
            orbFx(level, c, new Orb("all", 1, 1, PEARL, DamageNumbers.Style.PRISM), 1.3, 1.0, 0.3, 0);
        });
        RuneKit.later(blastAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            double sc = RuneKit.nearK(sp, V.up(g, 1), 8, 0.4);
            RuneKit.ultFlashFor(sp, level, g, "arcanaUnity", "flash", Opt.empty());
            RuneKit.streak(level, V.up(g, h), V.up(g, 0.2), WHITE, 0.9, 0.3, 1.0, 0);
            for (int i = 0; i < 4; i++) {
                final int ii = i;
                RuneKit.later(i, () -> RuneKit.novaRing(level, g, radius * (0.25 + 0.12 * ii), 2.2, orbs.get(ii).color(), WHITE, Opt.of("life", 0.6)));
            }
            RuneKit.godRays(sp, level, g, radius * 0.55, 8, WHITE, WHITE, Opt.of("h", 16.0, "life", 0.9, "colors", new double[][][] {
                    {orbs.get(0).color(), WHITE}, {orbs.get(1).color(), WHITE}, {orbs.get(2).color(), WHITE}, {orbs.get(3).color(), WHITE}}, "skipNear", true));
            RuneKit.skyFlare(level, V.up(g, 1.2), radius * 0.8 * sc, PEARL, WHITE, Opt.of("life", 0.55, "grow", 0.7));
            RuneKit.glowCrack(level, g, radius * 1.05, PEARL, WHITE, Opt.of("life", 2.0, "fade", 0.6));
            SkillFx.burst(level, V.up(g, 1), WHITE, radius * 0.55 * sc, 0.45);
            SkillFx.sparks(level, V.up(g, 1), PEARL, 16, 8);
            SkillFx.play(level, "random.explode", g, 1.2, 0.8);
            SkillFx.play(level, "firework.large_blast", g, 0.8, 1.0);
            SkillFx.play(level, "block.bell.hit", g, 0.6, 1.0);
            RuneKit.shakeAround(level, g, 20, 0.4, 0.4);
            int stun = (int) k.numOr("stun", 0);
            int[] marks = {0};
            for (LivingEntity e : Kit.around(sp, g, radius, 5)) {
                hit(sp, life, e, P, k.num("crush"), DamageNumbers.Style.PRISM, tally, x -> {
                    RuneKit.knock(x, V.sub(x.position(), g), 0.55, k.num("lift"));
                    if (stun > 0) RuneKit.stun(x, stun, PEARL, marks[0]++ < 12);
                });
            }
            if (fin) prism(sp, level, g, radius, orbs, life);
        });
        total(sp, "arcanaUnity", V.up(g, 3.2), tally, blastAt + 8);
        return true;
    }

    /** 조준한 곳 위 고리에서 구슬 i 의 자리 (dt = 날아간 뒤 틱 — 천천히 돎) */
    static Vec3 orbAt(Vec3 g, double h, int i, int dt) {
        double a = i * Math.PI / 2 + dt * 0.07;
        return new Vec3(g.x + Math.cos(a) * 2.2, g.y + h + Math.sin(dt * 0.2 + i) * 0.18, g.z + Math.sin(a) * 2.2);
    }

    /** 6돌 근원의 프리즘: 시전자 쪽을 보는 하늘에 네 빛 띠의 무지개 (반원 — 0.5초 동안 그려짐) + 무지개에서 빛줄기 열여섯이 쏟아짐 (피해 없음) */
    static void prism(ServerPlayer sp, ServerLevel level, Vec3 g, double radius, List<Orb> orbs, String life) {
        Vec3 f = V.flat(V.sub(g, sp.position()));
        f = V.len(f) > 0.5 ? V.unit(f) : Kit.facingOf(sp);
        Vec3 side = V.rightOf(f);
        // 무지개 띠: 바깥부터 힘 (주황) · 민첩 (초록) · 마력 (파랑) · 체력 (분홍)
        int[] order = {0, 1, 3, 2};
        double R0 = radius + 2.5;
        int segs = 26;
        for (int b = 0; b < 4; b++) {
            double R = R0 - b * 0.7;
            double[] col = orbs.get(order[b]).color();
            for (int s = 0; s < segs; s++) {
                // 조각마다 양끝을 35% 씩 늘려 겹침 (빛줄기 그림은 끝이 가늘어 이어 붙이면 점선처럼 보임)
                double a0 = Math.max(0, Math.PI * (s - 0.35) / segs);
                double a1 = Math.min(Math.PI, Math.PI * (s + 1.35) / segs);
                Vec3 p0 = V.add(V.up(g, Math.sin(a0) * R), side, Math.cos(a0) * R);
                Vec3 p1 = V.add(V.up(g, Math.sin(a1) * R), side, Math.cos(a1) * R);
                double delay = 0.5 * s / segs;
                RuneKit.streak(level, p0, p1, col, 0.42, 2.2, 0.85, delay);
            }
        }
        SkillFx.play(level, "chime.amethyst_block", g, 1.2, 1.0);
        SkillFx.play(level, "random.levelup", g, 1.5, 0.6);
        SkillFx.play(level, "beacon.activate", g, 0.9, 0.8);
        // 빛줄기가 무지개에서 쏟아짐 (1초 동안 16)
        for (int n = 0; n < 16; n++) {
            final int nn = n;
            RuneKit.later(4 + n, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double a = Math.PI * (0.12 + 0.76 * Kit.rand());
                double R = R0 - (nn % 4) * 0.7;
                Vec3 from = V.add(V.up(g, Math.sin(a) * R), side, Math.cos(a) * R);
                Vec3 to = ring(g, radius * Math.sqrt(Kit.rand()) * 0.9, Kit.rand() * Math.PI * 2);
                double[] col = orbs.get(order[nn % 4]).color();
                RuneKit.flyStreak(level, from, V.up(to, 0.1), 26, col, WHITE, 0.8, 0.26);
                RuneKit.later(Math.max(1, (int) Math.round(V.len(V.sub(to, from)) / 26 * 20)), () -> {
                    RuneKit.novaRing(level, to, 0.7, 1.8, col, WHITE, Opt.of("life", 0.4));
                    SkillFx.sparks(level, V.up(to, 0.3), col, 4, 3);
                });
            });
        }
        RuneKit.godRays(sp, level, g, radius * 0.9, 12, WHITE, WHITE, Opt.of("h", 18.0, "life", 1.4, "colors", new double[][][] {
                {orbs.get(0).color(), WHITE}, {orbs.get(1).color(), WHITE}, {orbs.get(3).color(), WHITE}, {orbs.get(2).color(), WHITE}}, "skipNear", true));
    }

    // ------------------------------------------------------------ 칠성 강림

    /** 별 자리 (하늘): 조준한 곳 위 h 칸, 시전자 쪽을 보는 면을 40° 뒤로 눕힘 */
    static Vec3[] starsAt(ServerPlayer sp, Vec3 g, double h, int n) {
        Vec3 f = V.flat(V.sub(g, sp.position()));
        f = V.len(f) > 0.5 ? V.unit(f) : Kit.facingOf(sp);
        Vec3 side = V.rightOf(f);
        double lean = Math.toRadians(40);
        Vec3 up = V.add(V.scale(V.UP, Math.cos(lean)), f, Math.sin(lean));
        Vec3 c = V.up(g, h);
        Vec3[] out = new Vec3[n];
        for (int i = 0; i < n; i++) out[i] = V.add(V.add(c, side, DIPPER[i][0]), up, DIPPER[i][1]);
        return out;
    }

    static boolean stars(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        boolean fin = finale(sp, r);
        Vec3 g = RuneKit.groundAim(sp, k.num("range"));
        int n = Math.min(DIPPER.length, (int) k.num("hits"));
        int draw = (int) k.num("draw");
        int gap = (int) k.num("gap");
        double radius = k.num("radius");
        double beam = k.num("beam");
        double h = skyHeight(sp, g, 5, k.num("height")); // 과녁까지 13.5칸이면 약 5.7칸 (가장 낮은 별도 땅 위 3칸 넘게)
        Vec3[] st = starsAt(sp, g, h, n);
        int collapse = draw + (n - 1) * gap + 8;
        int impact = collapse + 14;
        double hold = (collapse + 2) / 20.0;
        Tally tally = new Tally();
        RuneKit.ultSkyFor(sp, level, g, "sevenStars", impact + (fin ? 50 : 20), null);
        RuneKit.domainSigil(level, g, radius * 1.1, "holy", STAR_GLOW, STAR_CORE, Opt.of("life", (impact + 4) / 20.0, "spinRate", 12.0, "alpha", 0.7, "grow", 0.1));
        SkillFx.rune(level, sp.position(), Opt.of("size", 1.5, "life", 0.9, "color", STAR_GLOW, "alpha", 0.9, "rate", 120.0, "progress", 1.0));
        SkillFx.play(level, "beacon.power", g, 0.8, 0.9);
        SkillFx.play(level, "chime.amethyst_block", sp.position(), 0.9, 1.0);
        // 별이 하나씩 켜짐 (3틱마다 — 음이 올라감): 별 (오래 남는 빛 — 끝 15% 만 흐려짐)
        double[] pitch = {0.7, 0.8, 0.9, 1.0, 1.12, 1.26, 1.41, 1.6};
        for (int i = 0; i < n; i++) {
            final int ii = i;
            RuneKit.later(i * 3, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double left = hold - ii * 3 / 20.0;
                boolean polaris = ii == 7;
                RuneKit.skyFlare(level, st[ii], polaris ? 1.9 : 1.45, STAR_GLOW, STAR_CORE, Opt.of("life", left, "grow", 0.0, "fade", 0.15, "spinRate", 25.0));
                // 별 뒤 밤빛 무리 (부드러운 원 — 낮 하늘에서도 별이 또렷하게, 켜질 때 한 번 · 별과 같이 사라짐)
                Vec3 toEye = RuneKit.towardEye(sp, st[ii]);
                RuneKit.rfx(level, "orb", V.add(st[ii], toEye, -0.7), Opt.of("size", polaris ? 2.6 : 2.2, "life", left, "cell", HAZE_CELL, "color", NIGHT,
                        "alpha", 0.34, "fade", 0.15));
                RuneKit.rfx(level, "orb", V.add(st[ii], toEye, -0.35), Opt.of("size", polaris ? 1.3 : 1.05, "life", left, "cell", HAZE_CELL, "color", NIGHT,
                        "alpha", 0.5, "fade", 0.15));
                SkillFx.burst(level, st[ii], STAR_CORE, 0.9, 0.3);
                SkillFx.play(level, "note.chime", st[ii], pitch[ii], 1.0);
                if (polaris) SkillFx.play(level, "random.levelup", st[ii], 1.8, 0.5);
            });
        }
        // 별자리 선 유지 (6틱마다 다시 그림 — 빛줄기는 수명 동안 옅어지므로): 켜진 두 별을 잇는 선 (어두운 밑줄 + 별빛) · 북극성 길잡이 점선
        for (int t = 0; t < collapse; t += 6) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                int litN = Math.min(n, tt / 3 + 1);
                for (int[] ln : LINES) {
                    if (ln[0] >= litN || ln[1] >= litN) continue;
                    RuneKit.streak(level, st[ln[0]], st[ln[1]], NIGHT, 0.24, 0.5, 0.6, 0);
                    RuneKit.streak(level, st[ln[0]], st[ln[1]], STAR_GLOW, 0.08, 0.5, 0.95, 0);
                }
                if (litN > 7) {
                    for (int s = 0; s < 4; s++) {
                        Vec3 a = V.lerp(st[0], st[7], (s * 2 + 0.5) / 8.0);
                        Vec3 b = V.lerp(st[0], st[7], (s * 2 + 1.5) / 8.0);
                        RuneKit.streak(level, a, b, STAR_CORE, 0.07, 0.5, 0.85, 0);
                    }
                }
            });
        }
        // 별빛 기둥: 별 차례로 gap 틱마다 (적을 돌아가며 — 없으면 땅의 소용돌이 자리)
        for (int i = 0; i < n; i++) {
            final int ii = i;
            RuneKit.later(draw + i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                List<LivingEntity> foes = RuneKit.nearestTo(Kit.around(sp, g, radius, 6), g);
                Vec3 to;
                if (!foes.isEmpty()) {
                    LivingEntity e = foes.get(ii % foes.size());
                    to = e.position();
                } else {
                    to = ring(g, radius * 0.25 * (1 + ii % 3), ii * 2.3);
                }
                RuneKit.skyFlare(level, st[ii], 2.4, STAR_GLOW, WHITE, Opt.of("life", 0.3, "grow", 0.4));
                RuneKit.streak(level, st[ii], V.up(to, 0.2), STAR_GLOW, 0.38, 0.4, 0.95, 0);
                RuneKit.streak(level, st[ii], V.up(to, 0.2), WHITE, 0.14, 0.35, 1.0, 0);
                RuneKit.rfx2(level, "rays", to, Opt.of("size", 3.2, "life", 0.5, "alpha", 0.9, "fade", 0.6), STAR_GLOW, STAR_CORE, new int[] {0, 1}, 0.9);
                RuneKit.novaRing(level, to, beam * 0.45, 1.5, STAR_GLOW, STAR_CORE, Opt.of("life", 0.4));
                SkillFx.sparks(level, V.up(to, 0.6), STAR_CORE, 6, 5);
                SkillFx.play(level, "note.bell", to, 1.6, 0.8);
                SkillFx.play(level, "random.orb", to, 1.4, 0.4);
                for (LivingEntity e : Kit.around(sp, to, beam, 4)) hit(sp, life, e, P, k.num("mult"), DamageNumbers.Style.RUNE, tally, null);
            });
        }
        // 별자리가 한 점으로 접힘 → 큰 별
        Vec3 c = V.up(g, h);
        RuneKit.later(collapse, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            for (int i = 0; i < n; i++) RuneKit.flyStreak(level, st[i], c, 30, STAR_GLOW, STAR_CORE, 0.6, 0.22);
            SkillFx.play(level, "beacon.activate", c, 1.2, 1.0);
        });
        RuneKit.later(collapse + 6, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            RuneKit.skyFlare(level, c, 4.2, STAR_GLOW, STAR_CORE, Opt.of("life", 0.45, "grow", 0.4));
            SkillFx.burst(level, c, STAR_CORE, 2.0, 0.35);
            // 떨어짐 (8틱): 큰 별 + 꼬리
            double len = V.len(V.sub(g, c));
            Vec3 d = V.unit(V.sub(g, c));
            RuneKit.rfx2(level, "flare", c, Opt.of("dir", d, "speed", len / 0.4, "size", 2.8, "life", 0.4, "fade", 0.1, "spinRate", 90.0), STAR_GLOW, STAR_CORE,
                    new int[] {0, 1}, null);
            RuneKit.flyStreak(level, c, V.up(g, 0.3), len / 0.4, STAR_GLOW, STAR_CORE, 2.4, 0.7);
            SkillFx.play(level, "item.trident.thunder", g, 1.5, 0.6);
        });
        RuneKit.later(impact, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            double sc = RuneKit.nearK(sp, V.up(g, 1), 8, 0.4);
            RuneKit.ultFlashFor(sp, level, g, "sevenStars", "flash", Opt.empty());
            RuneKit.novaRing(level, g, radius * 0.4, 2.2, STAR_GLOW, STAR_CORE, Opt.of("life", 0.6));
            RuneKit.later(2, () -> RuneKit.novaRing(level, g, radius * 0.25, 3.0, STAR_CORE, WHITE, Opt.of("life", 0.55)));
            RuneKit.godRays(sp, level, g, radius * 0.6, 10, STAR_GLOW, STAR_CORE, Opt.of("h", 18.0, "life", 1.0, "skipNear", true));
            RuneKit.skyFlare(level, V.up(g, 1.2), radius * 0.85 * sc, STAR_GLOW, STAR_CORE, Opt.of("life", 0.6, "grow", 0.6));
            RuneKit.glowCrack(level, g, radius * 1.1, STAR_GLOW, STAR_CORE, Opt.of("life", 2.4, "fade", 0.6, "v", 1.0));
            SkillFx.burst(level, V.up(g, 1), STAR_CORE, radius * 0.6 * sc, 0.45);
            SkillFx.sparks(level, V.up(g, 1), STAR_CORE, 18, 9);
            SkillFx.play(level, "random.explode", g, 1.1, 0.8);
            SkillFx.play(level, "ambient.weather.thunder", g, 1.5, 0.6);
            SkillFx.play(level, "block.bell.hit", g, 0.8, 1.0);
            RuneKit.shakeAround(level, g, 20, 0.45, 0.45);
            for (LivingEntity e : Kit.around(sp, g, radius, 5)) {
                hit(sp, life, e, P, k.num("crush"), DamageNumbers.Style.STARFALL, tally, x -> RuneKit.knock(x, V.sub(x.position(), g), 0.5, k.num("lift")));
            }
            if (fin) meteorShower(sp, level, g, h, st, life);
        });
        total(sp, "sevenStars", V.up(g, 3.2), tally, impact + 8);
        return true;
    }

    /**
     * 6돌 별똥별 비: 떨어진 뒤 약 2초 동안 하늘을 가로지르는 별똥별 스물넷 (빛 머리 + 긴 꼬리 — 별자리가 있던 높이라 1인칭에서도 화면 위쪽에 보임) ·
     * 북극성이 남아 빛남 · 땅의 별 문양 (피해 없음)
     */
    static void meteorShower(ServerPlayer sp, ServerLevel level, Vec3 g, double h, Vec3[] st, String life) {
        Vec3 f0 = V.flat(V.sub(g, sp.position()));
        Vec3 f = V.len(f0) > 0.5 ? V.unit(f0) : Kit.facingOf(sp);
        Vec3 side = V.rightOf(f);
        if (st.length > 7) {
            RuneKit.skyFlare(level, st[7], 2.6, STAR_GLOW, WHITE, Opt.of("life", 2.6, "grow", 0.0, "fade", 0.3, "spinRate", 30.0));
            RuneKit.godRays(sp, level, g, 0.1, 1, STAR_GLOW, STAR_CORE, Opt.of("h", 22.0, "life", 2.0, "alpha", 0.7));
        }
        RuneKit.domainSigil(level, g, 7.5, "holy", STAR_CORE, WHITE, Opt.of("life", 2.4, "spinRate", 20.0, "alpha", 0.75, "grow", 0.1));
        for (int n = 0; n < 24; n++) {
            final int nn = n;
            RuneKit.later(2 + n * 2 + (n % 3), () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double across = (Kit.rand() - 0.5) * 16;
                double depth = (Kit.rand() - 0.25) * 8;
                Vec3 from = V.add(V.add(V.up(g, h + 0.5 + Kit.rand() * 3.5), side, across - 5), f, depth);
                Vec3 to = V.add(V.add(from, side, 8 + Kit.rand() * 4), V.DOWN, 3 + Kit.rand() * 2.5);
                double[] col = nn % 3 == 0 ? STAR_CORE : STAR_GLOW;
                double len = V.len(V.sub(to, from));
                double secs = Math.max(0.05, len / 20);
                RuneKit.flyStreak(level, from, to, 20, col, WHITE, 3.0, 0.3);
                // 빛 머리 (꼬리와 같은 빠르기로 날아감)
                RuneKit.rfx2(level, "flare", from, Opt.of("size", 0.6, "life", secs, "vel", V.scale(V.unit(V.sub(to, from)), len / secs), "fade", 0.25,
                        "spinRate", 120.0, "grow", 0.0), col, WHITE, new int[] {0, 1}, null);
                if (nn % 2 == 0) SkillFx.play(level, "firework.twinkle", from, 1.2 + Kit.rand() * 0.6, 0.35);
            });
        }
        SkillFx.play(level, "chime.amethyst_block", g, 1.5, 1.0);
        SkillFx.play(level, "random.levelup", g, 1.9, 0.5);
    }

    // ------------------------------------------------------------ 천공의 눈

    /** 눈 그림 (rpg:rfx_skyeye — 미리 칠한 색, 시전자 눈 쪽을 봄): frame 0 감음 · 1 반쯤 · 2 뜸 · 3 빛남 */
    static void eyeFx(ServerPlayer sp, ServerLevel level, Vec3 at, double size, int frame, double life, double alpha) {
        RuneKit.rfx(level, "skyeye", at, Opt.of("dir", RuneKit.towardEye(sp, at), "size", size, "life", life, "cell", (double) frame, "alpha", alpha, "fade", 0.2));
    }

    /** 눈 · 눈 둘레 문양 크기 비율 (낮게 뜨면 작게 — 문양 아래 끝이 과녁 머리 위에 남게): 높이 / 8, 0.7 ~ 1 */
    static double eyeScale(double h) {
        return Math.max(0.7, Math.min(1, h / 8));
    }

    static boolean eye(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        boolean fin = finale(sp, r);
        double range = k.num("range");
        Vec3 a0 = RuneKit.groundAim(sp, range);
        int charge = (int) k.num("charge");
        int gap = (int) k.num("gap");
        int hits = (int) k.num("hits");
        int end = charge + hits * gap;
        double hMax = k.num("height");
        double width = k.num("width");
        double step = k.num("speed") / 20.0;
        double radius = k.num("radius");
        Tally tally = new Tally();
        RuneKit.effect(sp, "slowness", end + 4, 2); // 광선 동안 천천히 걸음 (조준은 그대로)
        RuneKit.ultSkyFor(sp, level, a0, "heavenEye", end + 40, null);
        SkillFx.play(level, "beacon.power", sp.position(), 1.5, 0.9);
        SkillFx.play(level, "mob.elderguardian.curse", a0, 1.6, 0.4);
        SkillFx.rune(level, sp.position(), Opt.of("size", 1.4, "life", charge / 20.0, "color", SUN, "alpha", 0.9, "rate", 140.0, "progress", 1.0));
        Vec3[] p = {a0};
        Kit.ticks(t -> {
            if (!RuneKit.sameLife(sp, life)) return false;
            if (t > end) return false;
            Vec3 cur = p[0];
            double h = skyHeight(sp, cur, 6, hMax); // 과녁까지 13.5칸이면 약 6칸 — 눈 (반지름 3.4) 이 화면 위쪽 안에, 눈 둘레 문양은 과녁 머리 위
            Vec3 eyeAt = V.up(cur, h);
            if (t < charge) {
                // 눈을 뜸 (감음 → 반쯤 → 뜸) · 조준한 곳에 금빛 고리
                if (t % 2 == 0) {
                    int frame = t < charge / 3 ? 0 : t < charge * 2 / 3 ? 1 : 2;
                    eyeFx(sp, level, eyeAt, 3.4 * eyeScale(h), frame, 0.12, 0.95);
                    RuneKit.domainSigil(level, eyeAt, 4.6 * eyeScale(h), "arcane", SUN, SUN_CORE, Opt.of("dir", RuneKit.towardEye(sp, eyeAt), "life", 0.12, "spin", t * 4.0,
                            "alpha", 0.6, "fade", 0.2));
                }
                if (t % 6 == 0) RuneKit.zonePulse(level, cur, SUN, width, 0.35);
                if (t == charge - 4) SkillFx.play(level, "mob.warden.sonic_charge", eyeAt, 1.6, 0.7);
                return true;
            }
            // 광선: 바라보는 곳을 초당 speed 칸으로 따라감 (사거리 안)
            Vec3 want = RuneKit.groundAim(sp, range);
            Vec3 d = V.sub(want, cur);
            Vec3 dh = new Vec3(d.x, 0, d.z);
            double dl = V.len(dh);
            Vec3 next = dl <= step ? new Vec3(want.x, want.y, want.z) : new Vec3(cur.x + dh.x / dl * step, want.y, cur.z + dh.z / dl * step);
            Vec3 off = V.flat(V.sub(next, sp.position()));
            if (V.len(off) > range) next = new Vec3(sp.getX() + off.x / V.len(off) * range, next.y, sp.getZ() + off.z / V.len(off) * range);
            p[0] = next;
            cur = next;
            h = skyHeight(sp, cur, 6, hMax);
            eyeAt = V.up(cur, h);
            int bt = t - charge;
            boolean pulse = bt % gap == 0 && bt / gap < hits;
            if (t % 2 == 0 || pulse) {
                eyeFx(sp, level, eyeAt, 3.4 * eyeScale(h), pulse ? 3 : 2, 0.12, 0.95);
                RuneKit.domainSigil(level, eyeAt, 4.6 * eyeScale(h), "arcane", SUN, SUN_CORE, Opt.of("dir", RuneKit.towardEye(sp, eyeAt), "life", 0.12, "spin", t * 4.0,
                        "alpha", 0.6, "fade", 0.2));
                Vec3 top = V.up(cur, h - 0.8);
                RuneKit.streak(level, top, V.up(cur, 0.1), SUN, width * 0.36, 0.12, 0.9, 0);
                RuneKit.streak(level, top, V.up(cur, 0.1), SUN_CORE, width * 0.14, 0.12, 1.0, 0);
                RuneKit.rfx2(level, "rays", cur, Opt.of("size", h / 2, "life", 0.12, "alpha", 0.85, "fade", 0.3), SUN, SUN_CORE, new int[] {0, 1}, 0.9);
                if (fin) helix(level, cur, h, width, t);
            }
            if (t % 4 == 0) {
                RuneKit.novaRing(level, cur, width * 0.5, 1.2, SUN, SUN_CORE, Opt.of("life", 0.3));
                SkillFx.vanilla(level, "minecraft:basic_flame_particle", V.up(cur, 0.2));
            }
            if (t % 6 == 0) RuneKit.glowCrack(level, cur, width * 0.7, SCORCH, SUN, Opt.of("life", 2.2, "fade", 0.5));
            if (pulse) {
                SkillFx.burst(level, V.up(cur, 0.8), SUN, width * 0.7, 0.3);
                SkillFx.sparks(level, V.up(cur, 0.5), SUN_CORE, 8, 6);
                SkillFx.play(level, "fire.ignite", cur, 0.8, 0.9);
                SkillFx.play(level, "beacon.ambient", cur, 1.8, 0.8);
                if (bt == 0) SkillFx.play(level, "mob.warden.sonic_boom", eyeAt, 1.6, 0.5);
                for (LivingEntity e : Kit.around(sp, cur, width, 4)) {
                    hit(sp, life, e, P, k.num("mult"), DamageNumbers.Style.SOLAR, tally, x -> RuneKit.burn(x, k.num("fire")));
                }
            }
            if (t == end) finish(sp, level, cur, h, radius, P, k, life, tally, fin);
            return true;
        });
        return true;
    }

    /** 6돌 심판의 눈: 광선 둘레를 도는 빛 두 줄기 (나선) */
    static void helix(ServerLevel level, Vec3 cur, double h, double width, int t) {
        double rr = width * 0.55;
        for (int s = 0; s < 2; s++) {
            for (int j = 0; j < 6; j++) {
                double y0 = h * j / 6.0;
                double y1 = h * (j + 1) / 6.0;
                double a0 = t * 0.45 + s * Math.PI + y0 * 0.7;
                double a1 = t * 0.45 + s * Math.PI + y1 * 0.7;
                Vec3 p0 = new Vec3(cur.x + Math.cos(a0) * rr, cur.y + y0, cur.z + Math.sin(a0) * rr);
                Vec3 p1 = new Vec3(cur.x + Math.cos(a1) * rr, cur.y + y1, cur.z + Math.sin(a1) * rr);
                RuneKit.streak(level, p0, p1, s == 0 ? SUN_CORE : WHITE, 0.12, 0.12, 0.95, 0);
            }
        }
    }

    /** 끝: 마무리 폭발 (반지름 radius) · 눈이 감김 · 총 피해 (+ 6돌 해빛 기둥 여덟 · 해무리) */
    static void finish(ServerPlayer sp, ServerLevel level, Vec3 c, double h, double radius, double P, Obj k, String life, Tally tally, boolean fin) {
        Vec3 eyeAt = V.up(c, h);
        double es = eyeScale(h);
        double sc = RuneKit.nearK(sp, V.up(c, 1), 8, 0.4);
        RuneKit.ultFlashFor(sp, level, c, "heavenEye", "flash", Opt.empty());
        RuneKit.streak(level, V.up(c, h - 0.8), V.up(c, 0.1), SUN_CORE, 1.4, 0.3, 1.0, 0);
        RuneKit.novaRing(level, c, radius * 0.4, 2.2, SUN, SUN_CORE, Opt.of("life", 0.6));
        RuneKit.later(2, () -> RuneKit.novaRing(level, c, radius * 0.25, 3.0, SUN_CORE, WHITE, Opt.of("life", 0.5)));
        RuneKit.skyFlare(level, V.up(c, 1.2), radius * 0.9 * sc, SUN, SUN_CORE, Opt.of("life", 0.55, "grow", 0.7));
        RuneKit.skyFlare(level, eyeAt, 4.0 * es, SUN, SUN_CORE, Opt.of("life", 0.5, "grow", 0.5));
        RuneKit.glowCrack(level, c, radius * 1.1, SCORCH, SUN, Opt.of("life", 2.6, "fade", 0.6, "v", 1.0));
        SkillFx.burst(level, V.up(c, 1), SUN, radius * 0.6 * sc, 0.45);
        SkillFx.sparks(level, V.up(c, 1), SUN_CORE, 18, 9);
        SkillFx.play(level, "random.explode", c, 1.0, 0.8);
        SkillFx.play(level, "firework.large_blast", c, 0.7, 1.0);
        SkillFx.play(level, "block.bell.hit", c, 0.7, 1.0);
        RuneKit.shakeAround(level, c, 20, 0.45, 0.45);
        for (LivingEntity e : Kit.around(sp, c, radius, 5)) {
            hit(sp, life, e, P, k.num("crush"), DamageNumbers.Style.JUDGE, tally, x -> {
                RuneKit.knock(x, V.sub(x.position(), c), 0.6, 0.6);
                RuneKit.burn(x, k.num("fire"));
            });
        }
        // 눈이 감김
        for (int i = 0; i < 3; i++) {
            final int frame = 2 - i;
            RuneKit.later(2 + i * 3, () -> eyeFx(sp, level, eyeAt, 3.4 * es, frame, 0.18, 0.9 - frame * 0.1));
        }
        if (fin) {
            // 6돌: 해빛 기둥 여덟이 둘레에서 솟고 눈 자리에 해무리 (rfx_sun)
            RuneKit.godRays(sp, level, c, radius * 0.85, 8, SUN, SUN_CORE, Opt.of("h", 22.0, "life", 1.6, "alpha", 0.9, "skipNear", true));
            RuneKit.rfx2(level, "sun", eyeAt, Opt.of("dir", RuneKit.towardEye(sp, eyeAt), "size", 5.0 * es, "life", 1.4, "grow", 0.5, "fade", 0.5, "spinRate", 40.0),
                    SUN, SUN_CORE, new int[] {0, 1}, null);
            RuneKit.later(4, () -> RuneKit.novaRing(level, c, radius * 0.9, 1.6, SUN_CORE, WHITE, Opt.of("life", 0.9)));
            SkillFx.play(level, "beacon.activate", eyeAt, 0.7, 1.0);
            SkillFx.play(level, "random.totem", c, 1.4, 0.5);
        }
        total(sp, "heavenEye", V.up(c, 3.2), tally, 8);
    }
}
