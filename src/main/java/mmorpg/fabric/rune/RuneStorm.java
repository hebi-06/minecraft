package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleFunction;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 번개 룬 (runes.js "번개" — 보랏빛: 튀고, 순간이동하고, 기절): 연쇄 번개 · 섬광 이동 · 뇌전 감옥 · 뇌운 · 뇌신의 창 */
final class RuneStorm {
    private RuneStorm() {}

    /** 번개 (rpg:rfx_bolt): 빛 0~3 · 심지 4~7 */
    private static final int[] BOLT = {0, RuneKit.BOLT_CORE};
    /** 번개 창 (rpg:rfx_spear): 빛 0~3 · 심지 4~7 */
    private static final int[] SPEAR = {0, RuneKit.SPEAR_CORE};

    static void register() {
        RuneCast.register("chainLightning", RuneStorm::chainLightning);
        RuneCast.register("blink", RuneStorm::blink);
        RuneCast.register("staticPrison", RuneStorm::staticPrison);
        RuneCast.register("thundercloud", RuneStorm::thundercloud);
        RuneCast.register("thunderSpear", RuneStorm::thunderSpear);
    }

    /**
     * 연쇄 번개: 대상 (range 칸) 에게 번개 → chainRange 칸 안의 새 적에게 chains 번 튐 (gap 틱마다) — 손끝의 번개 구슬 (rpg:rfx_plasma),
     * 맞은 적마다 몸에 번개 구슬이 터지고 연쇄가 끝날 때까지 남음
     */
    static boolean chainLightning(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        LivingEntity first = Kit.pickTarget(sp, k.num("range"), k.num("cone"));
        if (first == null) {
            Notify.warn(sp, "§c" + k.str("name") + ": §7" + k.text("range") + "칸 안에 대상이 없습니다");
            return false;
        }
        ServerLevel level = sp.level();
        double[][] cs = RuneKit.colorOf("storm");
        double[] glow = cs[0];
        double[] core = cs[1];
        List<LivingEntity> chain = new ArrayList<>();
        Set<UUID> chained = new HashSet<>();
        chain.add(first);
        chained.add(first.getUUID());
        for (int i = 0; i < k.num("chains"); i++) {
            LivingEntity cur = chain.get(chain.size() - 1);
            List<LivingEntity> fresh = new ArrayList<>();
            for (LivingEntity e : Kit.around(sp, cur.position(), k.num("chainRange"), 3)) if (!chained.contains(e.getUUID())) fresh.add(e);
            if (fresh.isEmpty()) break;
            LivingEntity next = RuneKit.nearestTo(fresh, cur.position()).get(0);
            chain.add(next);
            chained.add(next.getUUID());
        }
        Vec3 hand = RuneKit.bowHand(sp);
        int n = chain.size();
        Vec3[] spots = new Vec3[n];
        for (int i = 0; i < n; i++) spots[i] = Kit.bodyPoint(chain.get(i));
        SkillFx.fx(level, "rpg:magic_circle_flash", hand, Opt.of("size", 0.35, "life", 0.2, "spin", Kit.rand() * 360, "progress", 1.0, "color", glow, "alpha", 1.0));
        RuneKit.plasmaOrb(level, V.add(hand, sp.getViewVector(1F), 0.7), 0.15, glow, Opt.of("life", 0.2));
        double gap = k.num("gap");
        for (int i = 0; i < n; i++) {
            final int ii = i;
            LivingEntity e = chain.get(i);
            RuneKit.later(i * gap, () -> {
                if (sp.isRemoved()) return;
                Vec3 from = ii == 0 ? RuneKit.bowHand(sp) : spots[ii - 1];
                Vec3 to = !e.isRemoved() ? Kit.bodyPoint(e) : spots[ii];
                spots[ii] = to;
                double hold = (n - ii) * (gap / 20) + 0.25; // 연쇄가 끝날 때까지 이어진 번개 · 구슬이 남음
                RuneKit.zap(level, from, to, glow, core, 0.22, hold);
                RuneKit.plasmaOrb(level, to, 0.55 + Kit.bodyPad(e) * 0.4, glow, Opt.of("life", hold, "fade", 0.3, "vel",
                        !e.isRemoved() ? RuneKit.entVel(e) : null)); // 적을 따라감
                SkillFx.burst(level, to, glow, 1.3, 0.25);
                SkillFx.sparks(level, to, core, 6, 5);
                SkillFx.play(level, "ambient.weather.lightning.impact", to, 1.6 + ii * 0.1, 0.25);
                if (!e.isRemoved()) RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat());
            });
        }
        return true;
    }

    /**
     * 섬광 이동: 앞으로 dist 칸 (벽 앞까지) 순간이동, 지나간 길 가까이 width 칸의 적 감전 — 떠난 자리에 번개 빛 그림자 잔상 (rpg:rfx_shade) ·
     * 번개 구슬, 지나간 길의 적마다 번개 구슬
     */
    static boolean blink(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 start = sp.position();
        Vec3 f = V.flat(sp.getViewVector(1F));
        double dist = RuneKit.dashDistance(sp, f, k.num("dist"), 1.1, 1.8); // 한 칸 턱은 넘음
        Vec3 tip = V.add(start, f, dist);
        Vec3 end = new Vec3(tip.x, RuneKit.groundAt(level, tip, start.y), tip.z);
        double[][] cs = RuneKit.colorOf("storm");
        double[] glow = cs[0];
        double[] core = cs[1];
        List<Kit.LineHit> path = RuneKit.lineTargets(sp, V.up(start, 1.0), f, Math.max(0.5, dist + 0.5), k.num("width"));
        SkillFx.burst(level, V.up(start, 1), glow, 1.2, 0.25);
        SkillFx.sparks(level, V.up(start, 1), core, 6, 5);
        RuneKit.zap(level, V.up(start, 1.0), V.up(end, 1.0), glow, core, 0.18, 0.35);
        // 떠난 자리: 번개 빛 그림자 잔상 + 번개 구슬 (옮긴 뒤 1인칭에서는 등 뒤)
        if (dist > 1.5) {
            // 밝고 반투명한 번개빛 잔상
            RuneKit.shadeClone(sp, level, start, f, 0, new double[] {0.45, 0.35, 0.95}, RuneKit.WHITE, Opt.of("life", 0.5, "fade", 0.7, "alpha", 0.6));
            RuneKit.plasmaOrb(level, V.up(start, 1.0), 0.5, glow, Opt.of("life", 0.3));
        }
        SkillFx.play(level, "mob.endermen.portal", start, 1.4, 0.8);
        SkillFx.play(level, "item.trident.thunder", start, 1.8, 0.3);
        if (dist > 0.2) RuneKit.moveTo(sp, end, null); // 막힌 자리면 그대로 (블록과 겹치면 옮기지 않음)
        // 도착: 번쩍 + 눈앞 고리 (1인칭)
        SkillFx.burst(level, V.up(end, 1), core, 1.0, 0.25);
        RuneKit.zonePulse(level, end, glow, 2.2, 0.35);
        SkillFx.ring(level, V.add(V.up(end, 1.5), f, 0.9), f, glow, 1.0, 0.3, 2.5);
        List<Vec3> shocked = new ArrayList<>();
        for (Kit.LineHit h : path) {
            LivingEntity e = h.e();
            if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                SkillFx.burst(level, Kit.bodyPoint(e), glow, 0.7, 0.2);
                shocked.add(Kit.bodyPoint(e));
            }
        }
        // 감전 줄기 · 번개 구슬: 한 틱 뒤 (순간이동하는 틱의 파티클 예산), 6명까지
        if (!shocked.isEmpty()) {
            Tasks.later(1, () -> {
                for (Vec3 at : shocked.subList(0, Math.min(6, shocked.size()))) {
                    RuneKit.zap(level, V.add(V.up(start, 1.0), f, Math.min(dist, V.dot(V.sub(at, start), f))), at, glow, core, 0.1, 0.22);
                    RuneKit.plasmaOrb(level, at, 0.4, glow, Opt.of("life", 0.3));
                }
            });
        }
        return true;
    }

    /**
     * 뇌전 감옥: 조준한 곳 (range 칸) 테두리에 번개가 둘러친 뒤 (delay 틱) 안쪽 radius 칸의 적 피해 + 기절 (stun) — 테두리 기둥 넷에 번개 구슬이 모이고 →
     * 하늘에서 번개 넷 (rpg:rfx_bolt) · 테두리를 따라 번개 창살 8개가 1.5초 동안 깜빡이며 섬, 바닥 · 위 (3칸) 의 고리 (갇힌 모양)
     */
    static boolean staticPrison(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 g = RuneKit.groundAim(sp, k.num("range"));
        double[][] cs = RuneKit.colorOf("storm");
        double[] glow = cs[0];
        double[] core = cs[1];
        double radius = k.num("radius");
        double delay = k.num("delay");
        int stunTicks = (int) k.num("stun");
        DoubleFunction<Vec3> rim = a -> new Vec3(g.x + Math.cos(a) * radius, g.y + 0.6, g.z + Math.sin(a) * radius);
        Vec3[] posts = new Vec3[4];
        for (int i = 0; i < 4; i++) posts[i] = rim.apply((i / 4.0) * Math.PI * 2 + 0.4);
        SkillFx.rune(level, g, Opt.of("size", radius, "life", delay / 20 + 0.3, "color", glow, "alpha", 0.85, "rate", 120.0, "grow", 0.0));
        for (int t = 0; t < delay; t += 3) {
            final int tt = t;
            RuneKit.later(t, () -> {
                double a = Kit.rand() * Math.PI * 2;
                SkillFx.bolt(level, rim.apply(a), rim.apply(a + 0.8), glow, Opt.of("jag", 0.35, "thick", 0.08, "life", 0.18));
                // 기둥 넷에 모이는 번개 구슬 (커짐)
                for (Vec3 p : posts) RuneKit.plasmaOrb(level, V.up(p, 0.6), 0.3 + (0.3 * (tt + 3)) / delay, glow, Opt.of("life", 0.17, "fade", 0.0));
            });
        }
        SkillFx.play(level, "mob.evocation_illager.prepare_attack", g, 1.2, 0.8);
        SkillFx.play(level, "respawn_anchor.charge", g, 1.2, 0.8);
        RuneKit.later(delay, () -> {
            if (sp.isRemoved()) return;
            for (int i = 0; i < 8; i++) {
                SkillFx.bolt(level, rim.apply((i / 8.0) * Math.PI * 2), rim.apply(((i + 1) / 8.0) * Math.PI * 2), glow, Opt.of("jag", 0.4, "thick", 0.2, "life", 0.45));
            }
            for (Vec3 p : posts) {
                Vec3 gp = V.up(p, -0.6);
                RuneKit.rfx2(level, "bolt", V.up(gp, 3.2), Opt.of("size", 3.2, "life", 0.26, "cell", Math.floor(Kit.rand() * 4)), glow, RuneKit.WHITE, BOLT, null);
            }
            SkillFx.burst(level, V.up(g, 1), glow, 2.2, 0.35);
            RuneKit.zonePulse(level, g, glow, radius, 0.45);
            SkillFx.play(level, "ambient.weather.lightning.impact", g, 1.2, 0.5);
            SkillFx.play(level, "mob.ravager.stun", g, 1.0, 0.8);
            List<LivingEntity> stunned = new ArrayList<>();
            for (LivingEntity e : Kit.around(sp, g, radius, 4)) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                    RuneKit.stun(e, stunTicks, glow, stunned.size() < 16); // 머리 위 기절 표시는 16명까지 (파티클 예산 — 기절은 모두)
                    stunned.add(e);
                }
            }
            // 감옥: 바닥 고리 + 위 (3칸) 의 뚜껑 고리 — 창살과 함께 갇힌 모양 (기절 동안)
            SkillFx.rune(level, g, Opt.of("size", radius, "life", stunTicks / 20.0, "color", glow, "alpha", 0.85, "rate", -40.0));
            SkillFx.rune(level, V.up(g, 3.0), Opt.of("size", radius * 0.95, "life", Math.min(stunTicks, 32) / 20.0, "color", core, "alpha", 0.8, "rate", 60.0));
            // 가운데에서 갇힌 적에게 튀는 번개: 한 틱 뒤, 4명까지 (파티클 예산)
            Tasks.later(1, () -> {
                for (LivingEntity e : stunned.subList(0, Math.min(4, stunned.size()))) {
                    if (!e.isRemoved()) RuneKit.zap(level, V.up(g, 0.6), Kit.bodyPoint(e), glow, core, 0.1, 0.25);
                }
            });
        });
        // 번개 창살: 테두리 8곳에 1.5초 (3틱마다 다른 모양으로 다시 그림 — 깜빡임)
        for (int t = (int) delay + 2; t < delay + 32; t += 3) {
            final int tt = t;
            RuneKit.later(t, () -> {
                for (int i = 0; i < 8; i++) {
                    Vec3 p = rim.apply((i / 8.0) * Math.PI * 2 + 0.2);
                    RuneKit.rfx2(level, "bolt", V.up(p, 0.95), Opt.of("size", 1.55, "life", 0.18, "cell", (double) ((i + tt) % 4), "alpha", 1.0), glow,
                            RuneKit.WHITE, BOLT, 0.95);
                }
            });
        }
        return true;
    }

    /** 먹구름 덩이 하나 (구름 가운데에서 x · z, 크기 s, 그림 칸) */
    private record Puff(double x, double z, double s, int cell) {}

    /**
     * 뇌운: 조준한 곳 (range 칸) 위 height 칸에 먹구름 (rpg:rfx_cloud — 짙은 보랏빛 덩이). gap 틱마다 hits 번, 반지름 radius 안에서 가장 오래 안 맞은 적에게
     * 번개 (rpg:rfx_bolt — 하늘에서 내리침, 떨어진 곳 splash 칸)
     */
    static boolean thundercloud(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 g = RuneKit.groundAim(sp, k.num("range"));
        double[][] cs = RuneKit.colorOf("storm");
        double[] glow = cs[0];
        double[] core = cs[1];
        double[] dark = {0.3, 0.25, 0.42};
        double radius = k.num("radius");
        double height = k.num("height");
        double splash = k.num("splash");
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int total = (hits - 1) * gap + 10;
        Vec3 sky = V.up(g, height);
        SkillFx.rune(level, g, Opt.of("size", radius, "life", total / 20.0 + 0.3, "color", glow, "alpha", 0.5, "rate", 20.0, "grow", 0.0));
        List<Puff> puffs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            double a = (i / 5.0) * Math.PI * 2 + Kit.rand() * 0.5;
            double rr = i == 0 ? 0 : radius * 0.62;
            puffs.add(new Puff(Math.cos(a) * rr, Math.sin(a) * rr, i == 0 ? 2.8 : 2.0, i % 4));
        }
        for (int t = 0; t < total; t += 8) {
            final int tt = t;
            RuneKit.later(t, () -> {
                for (Puff p : puffs) {
                    RuneKit.rfx(level, "cloud", new Vec3(sky.x + p.x(), sky.y + (p.s() > 2.5 ? 0.6 : 0.1), sky.z + p.z()), Opt.of("size", p.s(), "life", 0.48, "cell",
                            (double) p.cell(), "color", dark, "alpha", 0.92, "fade", tt + 8 >= total ? 0.5 : 0.0));
                }
                // 구름 속 번쩍임
                if (tt % 16 == 0) {
                    Vec3 flash = V.add(sky, new Vec3((Kit.rand() - 0.5) * radius, 0.2, (Kit.rand() - 0.5) * radius));
                    SkillFx.fx(level, "rpg:arrow_spark", flash, Opt.of("dir", V.UP, "size", 1.6, "grow", 1.5, "life", 0.18, "color", glow, "alpha", 0.85));
                }
            });
        }
        SkillFx.play(level, "ambient.weather.thunder", g, 1.2, 0.5);
        Map<UUID, Integer> struck = new HashMap<>();
        SkillFx.every(hits, gap, i -> {
            if (sp.isRemoved()) return;
            // 섞은 뒤 가장 오래 안 맞은 적부터 (한 번도 안 맞은 적 = -1, 같으면 섞인 차례 — 안정 정렬)
            List<LivingEntity> list = Kit.around(sp, g, radius, 5);
            for (int a = list.size() - 1; a > 0; a--) {
                int b = (int) Math.floor(Kit.rand() * (a + 1));
                LivingEntity m = list.get(a);
                list.set(a, list.get(b));
                list.set(b, m);
            }
            list.sort(Comparator.comparingInt(q -> struck.getOrDefault(q.getUUID(), -1)));
            LivingEntity tgt = list.isEmpty() ? null : list.get(0);
            double ang = Kit.rand() * Math.PI * 2;
            double rr = Math.sqrt(Kit.rand()) * radius * 0.8;
            Vec3 at = tgt != null ? tgt.position() : new Vec3(g.x + Math.cos(ang) * rr, g.y, g.z + Math.sin(ang) * rr);
            // 하늘 → 땅 번개 (그림 한 장, 키 = 구름 높이) + 가는 지그재그 줄기
            RuneKit.rfx2(level, "bolt", V.up(at, height / 2), Opt.of("size", height / 2, "life", 0.24, "cell", Math.floor(Kit.rand() * 4)), glow, RuneKit.WHITE, BOLT,
                    null);
            SkillFx.bolt(level, new Vec3(at.x + (Kit.rand() - 0.5) * 1.2, sky.y, at.z + (Kit.rand() - 0.5) * 1.2), V.up(at, 0.1), core, Opt.of("jag", 0.5, "thick", 0.1,
                    "life", 0.2));
            SkillFx.burst(level, V.up(at, 0.5), core, 1.4, 0.25);
            RuneKit.zonePulse(level, at, glow, splash, 0.35);
            SkillFx.play(level, "ambient.weather.lightning.impact", at, 1.0 + Kit.rand() * 0.3, 0.45);
            for (LivingEntity e : Kit.around(sp, at, splash, 3)) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) struck.put(e.getUUID(), i);
            }
        });
        return true;
    }

    /** 번개 창이 모이는 자리: 눈 위 0.62칸에서 보는 쪽 (수평) 으로 1.5칸 */
    /** 블록 창의 돌림: 창끝 (+Y) 이 시선 쪽, 넓은 면이 옆 (보는 사람 쪽 — 창날 마름모가 보이게) */
    private static org.joml.Quaternionf spearRot(Vec3 view) {
        return RuneBlocks.orient(view, V.rightOf(V.flat(view)));
    }

    private static Vec3 spearAt(ServerPlayer sp) {
        return V.add(V.up(sp.getEyePosition(), 0.62), V.flat(sp.getViewVector(1F)), 1.5);
    }

    /**
     * 뇌신의 창: charge 틱 동안 눈앞 위에 번개 창 (rpg:rfx_spear) 이 모임 → 던짐. 시선 방향 range 칸 (벽 앞까지) 을 꿰뚫어 폭 width 칸의 적 피해 · 기절,
     * 맞은 적마다 하늘에서 번개 (rpg:rfx_bolt) + chainRange 칸 안의 새 적 하나에게 연쇄 (chainMult).
     * 초월 2차 강화: 폭풍 하늘 · 앞쪽 하늘의 먹구름 둘 · 발밑의 칼날 문양 · 모으는 동안 앞쪽 땅에 번개 셋 → 보랏빛 번쩍임 · 발밑에서 퍼지는 고리 ·
     * 창이 날아가는 길에 번개 셋 · 창이 꽂힌 땅이 갈라져 빛남
     */
    static boolean thunderSpear(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        double[][] cs = RuneKit.colorOf("storm");
        double[] glow = cs[0];
        double[] core = cs[1];
        double[] white = RuneKit.WHITE;
        double charge = k.num("charge");
        int stunTicks = (int) k.num("stun");
        // 0.48.2 초월 룬 손보기 (사용자 "골렘 소환 같은 느낌이면 좋을거 같은데 너무 3d에 매끈매끈 한거보다"): 창 = 블록으로 짠 번개 창 (RuneBlocks.spear —
        // 짙은 프리즈머린 자루 · 다이아몬드 창날 · 바다 랜턴 심, 길이 3.4) 이 눈앞 위에 떠서 2틱마다 몸 · 시선을 따라가며 커짐 → 던지면 시선 방향으로 날아감.
        // 모바일 창 그림 (rfx_spear) 은 빛깔 겹만 옅게 겹쳐 번개 빛으로 (블록 + 2D 빛)
        Vec3 held0 = spearAt(sp);
        int flyMax = (int) Math.ceil(k.num("range") / 50.0 * 20) + 2;
        PairKit.Rig spear = RuneBlocks.spear(level, held0, (int) charge + flyMax + 8, Vec3.ZERO, spearRot(sp.getViewVector(1F)), 0.4, 3.4);
        for (int t = 0; t < charge; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (sp.isRemoved()) return;
                Vec3 view = sp.getViewVector(1F);
                Vec3 at = spearAt(sp);
                double grow = 0.4 + 0.6 * (tt / charge);
                if (tt >= 2) spear.pose(V.sub(at, held0), spearRot(view), grow, 2);
                RuneKit.rfx(level, "spear", at, Opt.of("dir", view, "size", 1.25 * grow, "life", 0.14, "cell", (double) (SPEAR[0] + (tt / 2) % 4), "color", glow,
                        "alpha", 0.5));
                SkillFx.fx(level, "rpg:magic_circle", V.add(at, view, -1.8), Opt.of("size", 0.5 + 0.5 * grow, "life", 0.14, "spin", tt * 18.0, "progress",
                        Math.min(1, (tt + 2) / charge), "color", glow, "alpha", 0.9)); // 창 뒤 · 눈 뒤 (1인칭 화면을 가리지 않음)
                double a = Kit.rand() * Math.PI * 2;
                SkillFx.bolt(level, V.add(at, new Vec3(Math.cos(a) * 1.6, Math.sin(a) * 1.0, Math.sin(a) * 1.6)), at, glow, Opt.of("jag", 0.35, "thick", 0.09,
                        "life", 0.14));
            });
        }
        SkillFx.play(level, "item.trident.riptide_3", sp.position(), 0.6, 0.8);
        SkillFx.play(level, "mob.evocation_illager.cast_spell", sp.position(), 0.8, 0.8);
        // 2차 강화 — 예고: 폭풍 하늘 · 앞쪽 하늘의 먹구름 둘 · 발밑의 칼날 문양 (빠르게 돎) · 모으는 동안 앞쪽 땅에 하늘에서 번개 셋
        Vec3 c0 = sp.position();
        Vec3 f0 = V.flat(sp.getViewVector(1F));
        RuneKit.ultSkyFor(sp, level, c0, "thunderSpear", (int) charge + 44, null);
        Vec3 storm = RuneKit.skyPoint(sp, V.add(c0, f0, 10), 6, 20, 6, 22);
        // 먹구름 둘 = 블록 먹구름 (RuneBlocks.cloud — 마인크래프트 구름처럼 납작한 양털 덩이 · 어두운 밑판): 작게 생겨 부풀고 끝에 흩어짐
        for (int sd : new int[] {-1, 1}) {
            Vec3 cp = V.add(storm, V.rightOf(f0), sd * 4.5);
            org.joml.Quaternionf cq = RuneBlocks.faceTo(cp, c0);
            PairKit.Rig cl = RuneBlocks.cloud(level, cp, (int) charge + 26, Vec3.ZERO, cq, 0.3, 5.2);
            RuneKit.later(2, () -> cl.pose(Vec3.ZERO, cq, 1, 6));
            RuneKit.later((int) charge + 14, () -> cl.pose(new Vec3(0, 0.8, 0), cq, 0.15, 10));
        }
        RuneKit.domainSigil(level, c0, 5, "blade", glow, white, Opt.of("life", (charge + 2) / 20, "spinRate", 120.0, "grow", 0.3, "alpha", 0.8, "fade", 0.15));
        for (int i = 0; i < 3; i++) {
            final int ii = i;
            Tasks.later(3 + i * 6, () -> {
                if (sp.isRemoved()) return;
                Vec3 p = V.add(V.add(sp.position(), f0, 9 + Kit.rand() * 7), V.rightOf(f0), (Kit.rand() - 0.5) * 10);
                double gy = RuneKit.groundAt(level, p, sp.getY());
                RuneKit.rfx2(level, "bolt", new Vec3(p.x, gy + 6, p.z), Opt.of("size", 6.0, "life", 0.24, "cell", (double) ((ii * 3) % 4)), glow, white, BOLT, null);
                SkillFx.play(level, "ambient.weather.thunder", p, 0.6, 0.6);
            });
        }
        RuneKit.later(charge, () -> {
            if (sp.isRemoved()) return;
            Vec3 view = sp.getViewVector(1F);
            // 막힘 · 맞는 대상은 눈에서 (머리 위에서 재면 두 칸 높이 굴에서는 천장에 막힘), 날아가는 창은 눈 조금 위
            Vec3 from = V.add(sp.getEyePosition(), view, 0.5);
            double len = Math.max(1, RuneKit.rayLength(level, from, view, k.num("range")));
            double speed = 50;
            Vec3 shown = V.up(from, 0.25);
            // 창 크기 · 길의 번개 굵기는 폭 width 를 따라 (기본 1.6칸 → 1.35 · 0.12 — 8단계 6돌은 폭이 넓어져 창도 큼)
            double wk = k.num("width") / 1.6;
            Vec3 s0 = V.add(shown, view, 1.2);
            double fly = Math.max(0.05, (len - 1.2) / speed);
            int flyT = Math.max(1, (int) Math.round(fly * 20));
            // 블록 창: 던지는 자리로 (1틱) → 시선 방향 끝까지 날아감 (flyT 틱 — 클라이언트 보간) → 사라짐
            spear.pose(V.sub(s0, held0), spearRot(view), wk, 1);
            RuneKit.later(1, () -> spear.pose(V.sub(V.add(s0, view, len - 1.2), held0), spearRot(view), wk, flyT));
            RuneKit.later(flyT + 3, spear::remove);
            RuneKit.rfx(level, "spear", s0, Opt.of("dir", view, "speed", speed, "size", 1.35 * wk, "life", fly, "cell", (double) (SPEAR[0] + (int) Math.floor(Kit.rand() * 4)),
                    "color", glow, "alpha", 0.5));
            for (double s = 0; s < len; s += 4) {
                final double ss = s;
                RuneKit.later(Math.round((s / speed) * 20), () -> SkillFx.bolt(level, V.add(from, view, ss), V.add(from, view, Math.min(len, ss + 4)), glow,
                        Opt.of("jag", 0.45 * wk, "thick", 0.12 * wk, "life", 0.35)));
            }
            SkillFx.play(level, "item.trident.throw", from, 0.5, 1);
            SkillFx.play(level, "item.trident.thunder", from, 0.8, 1);
            SkillFx.shake(sp, 0.2, 0.2);
            // 2차 강화 발동: 보랏빛 번쩍임 · 발밑에서 퍼지는 고리 · 창이 날아가는 길에 하늘에서 번개 셋 (가까운 곳부터)
            RuneKit.ultFlashFor(sp, level, from, "thunderSpear", "flash", Opt.empty());
            RuneKit.novaRing(level, sp.position(), 1.5, 4, glow, white, Opt.of("life", 0.45));
            for (int i = 0; i < 3; i++) {
                final int ii = i;
                double s = len * (0.3 + i * 0.3);
                RuneKit.later(Math.round((s / speed) * 20) + 1, () -> {
                    Vec3 p = V.add(from, view, s);
                    double gy = RuneKit.groundAt(level, p, from.y - 1.6);
                    RuneKit.rfx2(level, "bolt", new Vec3(p.x, gy + 7, p.z), Opt.of("size", 7.0, "life", 0.28, "cell", (double) ((ii + 1) % 4)), glow, white, BOLT,
                            null);
                });
            }
            List<Kit.LineHit> line = RuneKit.lineTargets(sp, from, view, len, k.num("width"));
            Set<UUID> hit = new HashSet<>();
            for (Kit.LineHit h : line) hit.add(h.e().getUUID());
            for (Kit.LineHit h : line) {
                LivingEntity e = h.e();
                RuneKit.later(Math.round((h.t() / speed) * 20), () -> {
                    if (sp.isRemoved() || e.isRemoved()) return;
                    if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) <= 0) return;
                    boolean show = RuneKit.fxQuota("spearHit", 4); // 한 틱에 4명까지 그림 (줄지어 선 적 24명 — 파티클 예산, 피해 · 기절은 모두)
                    RuneKit.stun(e, stunTicks, glow, show);
                    Vec3 at = Kit.bodyPoint(e);
                    if (show) {
                        RuneKit.rfx2(level, "bolt", V.up(e.position(), 3.6), Opt.of("size", 3.6, "life", 0.28, "cell", Math.floor(Kit.rand() * 4)), glow, white, BOLT,
                                null); // 하늘에서 내리치는 번개
                        SkillFx.burst(level, at, glow, 1.8, 0.3);
                        SkillFx.sparks(level, at, core, 8, 6);
                    }
                    SkillFx.play(level, "ambient.weather.lightning.impact", at, 1.1, 0.4);
                    List<LivingEntity> fresh = new ArrayList<>();
                    for (LivingEntity x : Kit.around(sp, e.position(), k.num("chainRange"), 3)) if (!hit.contains(x.getUUID())) fresh.add(x);
                    if (fresh.isEmpty()) return;
                    List<LivingEntity> order = RuneKit.nearestTo(fresh, e.position());
                    int chains = (int) Math.max(1, k.numOr("chainCount", 1)); // 8단계 3돌: 연쇄 2명
                    for (int ci = 0; ci < Math.min(chains, order.size()); ci++) {
                        LivingEntity next = order.get(ci);
                        hit.add(next.getUUID());
                        Tasks.later(3 + ci * 2, () -> {
                            if (sp.isRemoved() || next.isRemoved()) return;
                            RuneKit.zap(level, at, Kit.bodyPoint(next), glow, core, 0.14, 0.3);
                            if (RuneKit.runeHit(sp, next, P, k.num("chainMult"), r.stat()) > 0) {
                                RuneKit.stun(next, stunTicks, glow, true);
                                SkillFx.burst(level, Kit.bodyPoint(next), glow, 1.0, 0.24);
                            }
                        });
                    }
                });
            }
            RuneKit.later(Math.round((len / speed) * 20), () -> {
                Vec3 endp = V.add(from, view, len);
                SkillFx.burst(level, endp, glow, 3.6, 0.4);
                SkillFx.burst(level, endp, core, 1.8, 0.3);
                // 창이 꽂힌 땅이 갈라져 빛남
                RuneKit.glowCrack(level, new Vec3(endp.x, RuneKit.groundAt(level, endp, endp.y - 1.2), endp.z), 3.4, glow, white, Opt.of("life", 1.8));
                RuneKit.zonePulse(level, V.up(endp, -1), glow, 3, 0.45);
                for (int i = 0; i < 3; i++) {
                    double a = (i / 3.0) * Math.PI * 2 + Kit.rand();
                    Vec3 gg = new Vec3(endp.x + Math.cos(a) * 1.6, endp.y - 1.2, endp.z + Math.sin(a) * 1.6);
                    RuneKit.rfx2(level, "bolt", V.up(gg, 3), Opt.of("size", 3.0, "life", 0.24, "cell", (double) (i % 4)), glow, white, BOLT, null);
                }
                for (int i = 0; i < 4; i++) {
                    double a = (i / 4.0) * Math.PI * 2;
                    RuneKit.zap(level, endp, V.add(endp, new Vec3(Math.cos(a) * 2.2, (Kit.rand() - 0.3) * 1.5, Math.sin(a) * 2.2)), glow, core, 0.14, 0.3);
                }
                SkillFx.play(level, "ambient.weather.lightning.impact", endp, 0.9, 0.7);
            });
        });
        return true;
    }
}
