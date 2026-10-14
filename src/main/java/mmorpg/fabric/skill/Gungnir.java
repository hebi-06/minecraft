package mmorpg.fabric.skill;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.Tasks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 궁니르 (초월 창 — 0.12.3 부터. 애드온 · 0.12.1 까지는 도끼 초월 자리의 창 모양 무기였고, 12단계에 창 종류가 생겨 창 초월 브류나크를 대신함) —
 * 신창 귀환 · 신창 강림 (skills.js v2.18.0 "번개 · 신창" — 동작 · 연출 · 재사용 대기는 그대로). 수치 · 색은 SKILLS.lance (rules_more):
 * 배율 합은 창 세기 (예전 브류나크와 같은 8.5 · 13.6 — 도끼 룬 위력 기준이던 9.9 · 15.9 를 같은 비율로 줄임), 회복은 도끼 규칙 (딜탱) 이라 창에는 없음
 * (heal 이 있으면 씀). 그림은 파티클 그림만 (rpg:gungnir_spear = 모델 옆모습).
 * fallSpot (떨어질 땅) 은 세계수의 도끼 (Yggdrasil) 도 씀
 */
final class Gungnir {
    private Gungnir() {}

    static final double[] GN_BOLT = {0.62, 0.86, 1.0};
    static final double[] GN_WHITE = {0.94, 0.98, 1.0};
    static final double[] GN_DEEP = {0.22, 0.4, 0.95};
    static final double[] GN_DARK = {0.05, 0.07, 0.16};
    static final double[] GN_CLOUD = {0.2, 0.22, 0.34};
    /** 끌지 않는 무거운 몹 (룬 중력의 heavy 목록 — 자바 이름) */
    static final Set<String> HEAVY_MOB = javaTypes(Rules.get().obj("RUNE_GRAVITY").listOrNull("heavy"));
    /** 끌지 않는 바닐라 몹 (몸이 넓거나 무거움 — 몸 크기를 스크립트가 모름) */
    static final Set<String> DRAG_SKIP = javaTypes(List.of("minecraft:ravager", "minecraft:sniffer", "minecraft:camel", "minecraft:hoglin", "minecraft:zoglin",
            "minecraft:polar_bear", "minecraft:horse", "minecraft:donkey", "minecraft:mule", "minecraft:skeleton_horse", "minecraft:zombie_horse",
            "minecraft:llama", "minecraft:trader_llama", "minecraft:panda", "minecraft:strider", "minecraft:breeze"));
    /** build.py 의 SPEAR_PARTS (거대한 창을 나눠 그리는 조각 수) */
    static final int SPEAR_PARTS = 4;
    /** 그림 가로 · 그 안의 창 길이 (점) */
    static final double SPEAR_TEX_W = 512;
    static final double SPEAR_TEX_LEN = 469;

    static void register() {
        Transcend.register("gungnir_return", Gungnir::recall);
        Transcend.register("gungnir_fall", Gungnir::fall);
    }

    /** 애드온 (베드락) 엔티티 이름 목록 → 자바 이름 모음 */
    private static Set<String> javaTypes(List<?> ids) {
        Set<String> out = new HashSet<>();
        if (ids != null) for (Object id : ids) if (id instanceof String s) out.add(Kit.javaType(s));
        return Set.copyOf(out);
    }

    // ------------------------------------------------------------ 그림 도구

    /**
     * 궁니르 그림 하나 (rpg:gungnir_spear): at = 창 가운데, dir = 창끝 방향 (단위), half = 창 반 길이 (칸), life = 초.
     * o.move · o.speed = 움직이는 방향 · 초당 칸 (돌아올 때는 창끝과 반대로 움직임), o.progress = -1 번개 되풀이 (기본) · 0~1 그 장 · 2 수명 동안 흩어짐, o.alpha.
     * id = 같은 변수의 다른 그림 (빛의 검 · 은빛 화살), seg = 나눠 그린 조각 번호
     */
    static void spearFx(ServerLevel level, Vec3 at, Vec3 dir, double half, double life, Opt o, String id, int seg) {
        SkillFx.fx(level, id, at, Opt.of("dir", dir, "size", half, "life", life, "speed", o.num("speed", 0), "progress", o.num("progress", -1),
                "color", SkillFx.WHITE, "alpha", o.num("alpha", 1), "move", o.vec("move", dir), "seg", (double) seg));
    }

    static void spearFx(ServerLevel level, Vec3 at, Vec3 dir, double half, double life, Opt o, String id) {
        spearFx(level, at, dir, half, life, o, id, 0);
    }

    static void spearFx(ServerLevel level, Vec3 at, Vec3 dir, double half, double life, Opt o) {
        spearFx(level, at, dir, half, life, o, "rpg:gungnir_spear", 0);
    }

    /** 거대한 창: SPEAR_PARTS 조각으로 나눠 그림 (rpg:gungnir_spear_part — 한 장이면 가운데가 화면 밖일 때 창 전체가 빠질 수 있어서). center · dir · half 는 창 전체 */
    static void spearParts(ServerLevel level, Vec3 center, Vec3 dir, double half, double life, Opt o) {
        double pw = SPEAR_TEX_W / SPEAR_PARTS;
        for (int s = 0; s < SPEAR_PARTS; s++) {
            double off = ((pw * (s + 0.5) - SPEAR_TEX_W / 2) * 2 * half) / SPEAR_TEX_LEN; // 조각 가운데가 창 가운데에서 dir 쪽으로 몇 칸
            spearFx(level, V.add(center, dir, off), dir, half, life, o, "rpg:gungnir_spear_part", s);
        }
    }

    /** 보는 사람을 향한 창이 화면에서 돎 (rpg:gungnir_spin): 처음 각 deg, 초당 rate 도 — 틱마다 다시 그리지 않아 매끄러움 */
    static void spinFx(ServerLevel level, Vec3 at, double half, double life, double deg, double rate) {
        SkillFx.fx(level, "rpg:gungnir_spin", at, Opt.of("size", half, "life", life, "spin", deg, "speed", rate, "progress", -1.0, "color", SkillFx.WHITE,
                "alpha", 1.0, "move", V.UP));
    }

    /**
     * 신창 귀환이 꿰어 끌어올 수 있는 몹: 살아 있는 보통 몹 — 플레이어 · 보스 · 보스 소품 · 허수아비 · 아주 큰 몹 (엔더 드래곤 등) ·
     * 넓거나 키 큰 몹 (엔더맨 · 트롤 등) 은 아님 (피해만)
     */
    static boolean draggable(Entity e) {
        if (e == null || e.isRemoved() || Kit.hasNoHealth(e) || e instanceof Player) return false;
        String type = Kit.typeId(e);
        if (HEAVY_MOB.contains(type) || DRAG_SKIP.contains(type) || Kit.bodyPad(e) > 0.6) return false;
        if (e.getEyeY() - e.getY() > 2.3) return false; // 키 큰 몸 (손 높이로 끌면 발이 땅에 박혀 옮겨지지 않음)
        return !(Kit.hasFamily(e, "rpg_boss") || Kit.hasFamily(e, "rpg_boss_prop") || Kit.hasFamily(e, "rpg_dummy"));
    }

    /** 창이 지나가며 벤 자리: 하늘빛 X 번쩍임 + 불꽃 (눈 가까이는 작게) */
    static void spearHitFx(ServerLevel level, Entity e, Vec3 eye, Vec3 dir, double[] glow, double[] core) {
        Vec3 c = Kit.bodyAt0(e, eye);
        double near = Math.min(1, Math.max(0.45, V.len(V.sub(c, eye)) / 3));
        SkillFx.fx(level, "rpg:slash_x", c, Opt.of("dir", dir, "size", 1.0 * near, "life", 0.25, "color", glow, "alpha", 1.0));
        SkillFx.fx(level, "rpg:slash_x", V.add(c, dir, -0.04), Opt.of("dir", dir, "size", 0.7 * near, "life", 0.2, "color", core, "alpha", 1.0));
        SkillFx.sparks(level, c, GN_WHITE, 5, 5);
    }

    // ------------------------------------------------------------ 스킬 1 신창 귀환

    /** 돌아오는 창: 창 가운데 c, 움직이는 단위 방향 u, 틱당 칸 per, 이 곧은 구간의 남은 틱 left, 이 구간 끝이 손인지 last */
    private static final class Back {
        Vec3 c;
        Vec3 u;
        double per;
        int left;
        boolean last;

        Back(Vec3 c, Vec3 u, double per) {
            this.c = c;
            this.u = u;
            this.per = per;
        }
    }

    /** 창끝에 꿰인 몹: 몸 가운데가 발에서 lift 칸 위, free = 빠짐 (쓰러짐 · 벽 너머 · 막힘) */
    private static final class Hook {
        final LivingEntity e;
        final double lift;
        boolean free;

        Hook(LivingEntity e, double lift) {
            this.e = e;
            this.lift = lift;
        }
    }

    /**
     * 신창 귀환: 조준한 쪽 (조준 보정 — 시선에 몹이 겹치지 않으면 가까운 몹 쪽, 창이 닿는 거리 안) 으로 궁니르를 던짐. 창끝이 손에서 range 칸 (벽 앞까지) 에
     * 닿을 때까지 틱당 speed 칸으로 날며 폭 width 칸의 적을 꿰뚫음 (mult) → 끝에서 hover 틱 동안 돌며 (보는 사람을 향해 한 바퀴) 하늘의 번개를 모음 →
     * 손으로 돌아옴 (3틱마다 지금 손을 다시 겨눔, 창끝은 바깥): 지나는 적을 다시 벰 (backMult) — 끌 수 있는 몹은 hook 마리까지 창끝에 꿰어 내 앞까지 끌어옴
     * (번개 사슬). 몹은 나에게서 멀어지는 쪽으로는 옮기지 않고 (창이 따라잡을 때까지 기다림), 벽 너머 · 막힌 자리로 옮기게 되면 · 쓰러지면 빠짐.
     * 몹 무적 시간을 피해 적마다 틱을 잡아 넣음 (reserveHit · hitAt). 쓰러지거나 차원을 옮기면 멈춤, 돌아오는 동안 손이 멀리 순간이동하면 창은 바로 손에 돌아옴.
     * 벽이 가까우면 (창이 날 거리가 없으면) 창 그림 없이 번개로만 (1인칭 화면을 덮지 않게)
     */
    static boolean recall(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = HeroKit.colors(k);
        double[] glow = cs[0];
        double[] core = cs[1];
        double range = k.num("range");
        double half = k.num("half");
        double speed = k.num("speed");
        double width = k.num("width");
        Supplier<Vec3> handOf = () -> V.add(V.up(sp.getEyePosition(), -0.25), V.rightOf(Kit.facingOf(sp)), 0.3);
        Vec3 hand = handOf.get();
        Vec3 eye0 = sp.getEyePosition();
        RpgCombat.Aim r = RpgCombat.aimTarget(sp, range - 1, Kit.BOW.numOr("aimAssist", 0));
        Vec3 dir = r.target() != null && !r.target().isRemoved() && Kit.canHit(sp, r.target())
                ? V.unit(V.sub(Kit.bodyPoint(r.target()), hand))
                : V.unit(r.dir());
        double reach = Math.max(1.5, Kit.rayDist(level, hand, dir, range) - 0.3); // 창끝이 닿는 거리 (벽 앞)
        Vec3 c0 = V.add(hand, dir, 0.9); // 판정의 창 가운데 (창끝 = 가운데 + half)
        double travel = Math.max(0, reach - 0.9 - half);
        boolean shown = travel >= 1.5; // 창 그림을 그릴 만큼 날아가는지
        int hover = shown ? (int) k.num("hover") : 0;
        int outT = (int) Math.max(1, Math.ceil(travel / speed));
        Vec3 cEnd = V.add(c0, dir, travel);
        long now = Tasks.now();
        // 던짐: 손 앞의 작은 번쩍임 (1.6칸 앞) + 날아가는 창 (그림은 손 앞 2.4칸부터 — 창 뒤끝이 눈을 스치지 않게) + 창끝의 빛 (멀어지면 창이 끝만 보여서)
        // + 지나간 자리의 빛 · 번개 줄기
        SkillFx.burst(level, V.add(hand, dir, 1.6), glow, 0.5, 0.18);
        if (shown) {
            Vec3 vc0 = V.add(hand, dir, 2.4);
            double vTravel = Math.max(0, V.len(V.sub(cEnd, vc0)));
            double vOut = vTravel / (outT / 20.0);
            spearFx(level, vc0, dir, half, outT / 20.0, Opt.of("move", dir, "speed", vOut));
            SkillFx.fx(level, "rpg:arrow_spark", V.add(vc0, dir, half * 0.8), Opt.of("dir", dir, "speed", vOut, "size", 0.75, "grow", 1.0,
                    "life", outT / 20.0 + 0.05, "color", GN_BOLT, "alpha", 0.75));
            SkillFx.fx(level, "rpg:arrow_spark", V.add(vc0, dir, half * 0.8), Opt.of("dir", dir, "speed", vOut, "size", 0.35, "grow", 1.0,
                    "life", outT / 20.0 + 0.05, "color", GN_WHITE, "alpha", 1.0));
            Vec3 a = V.add(hand, dir, 1.6);
            SkillFx.beam(level, a, cEnd, glow, Opt.of("half", 0.5, "thick", 0.28, "life", 0.4, "sweep", outT / 20.0, "gap", 0.8, "alpha", 0.85));
            SkillFx.beam(level, a, cEnd, GN_WHITE, Opt.of("half", 0.45, "thick", 0.08, "life", 0.3, "sweep", outT / 20.0, "gap", 1.6, "alpha", 1.0));
            int n = (int) Math.max(1, Math.round(travel / 5));
            double span = V.len(V.sub(cEnd, a));
            for (int i = 0; i < n; i++) {
                Vec3 s0 = V.add(a, dir, (span * i) / n);
                Vec3 s1 = V.add(a, dir, (span * (i + 1)) / n);
                SkillFx.bolt(level, s0, s1, GN_BOLT, Opt.of("thick", 0.1, "life", 0.25, "jag", 0.35, "delay", ((i + 1.0) / n) * (outT / 20.0)));
            }
        } else {
            // 벽이 가까움: 창끝이 닿는 자리에 번개만
            Vec3 tip = V.add(hand, dir, reach);
            SkillFx.burst(level, tip, GN_WHITE, 0.9, 0.22);
            SkillFx.sparks(level, tip, GN_BOLT, 8, 5);
            SkillFx.bolt(level, V.up(tip, 6), tip, GN_BOLT, Opt.of("thick", 0.2, "life", 0.25, "jag", 0.5));
        }
        SkillFx.play(level, "item.trident.throw", hand, 0.8, 1.0);
        SkillFx.play(level, "item.trident.riptide_1", hand, 1.2, 0.6);
        // 갈 때: 창끝이 지나가는 순간 꿰뚫음
        for (Kit.LineHit h : Kit.lineHits(sp, hand, dir, reach, width)) {
            LivingEntity e = h.e();
            long at = now + (long) Math.max(0, Math.ceil((h.t() - 0.9 - half) / speed));
            Kit.hitAt(sp, e, Kit.reserveHit(sp, e, at), ok, () -> {
                if (Kit.skillHit(sp, e, w, k.num("mult")) > 0) {
                    spearHitFx(level, e, eye0, dir, glow, core);
                    SkillFx.play(level, "item.trident.hit", Kit.bodyAt0(e, eye0), 1.1, 0.7);
                }
            });
        }
        // 끝: hover 틱 동안 돎 (보는 사람을 향한 창이 화면에서 한 바퀴 — 가까우면 작게) + 하늘에서 번개가 창에 떨어져 모임
        if (hover > 0) {
            Tasks.later(outT, () -> {
                if (!ok.getAsBoolean()) return;
                double dEye = V.len(V.sub(cEnd, sp.getEyePosition()));
                spinFx(level, cEnd, half * Math.min(1, Math.max(0.35, (dEye - 1.5) / 4.5)), hover / 20.0 + 0.02, Kit.rand() * 360, 360 / (hover / 20.0));
                SkillFx.shock(level, V.up(cEnd, -0.3), GN_BOLT, 1.2, 0.35);
                SkillFx.play(level, "item.trident.thunder", cEnd, 1.6, 0.5);
            });
            for (int h = 0; h < hover; h++) {
                final int hh = h;
                Tasks.later(outT + h, () -> {
                    if (!ok.getAsBoolean()) return;
                    Vec3 side = V.rightOf(V.flat(dir));
                    double a = Kit.rand() * Math.PI * 2;
                    double rr = 1.4 + Kit.rand() * 1.0;
                    SkillFx.bolt(level, cEnd, V.add(V.up(cEnd, Math.sin(a) * rr), side, Math.cos(a) * rr), GN_BOLT,
                            Opt.of("thick", 0.09, "life", 0.12, "jag", 0.3));
                    if (hh == 1) {
                        SkillFx.bolt(level, V.up(cEnd, 12), cEnd, GN_BOLT, Opt.of("thick", 0.32, "life", 0.3, "jag", 0.7));
                        SkillFx.bolt(level, V.up(cEnd, 12), cEnd, GN_WHITE, Opt.of("thick", 0.11, "life", 0.24, "jag", 0.7));
                        SkillFx.burst(level, cEnd, GN_WHITE, 1.5, 0.25);
                        SkillFx.sparks(level, cEnd, GN_BOLT, 8, 6);
                        SkillFx.play(level, "item.trident.thunder", cEnd, 1.2, 0.9);
                    }
                });
            }
        }
        // 돌아옴: 3틱마다 지금 손을 다시 겨눠 그 3틱은 곧게 (그림과 판정이 같은 길). 지나는 적을 벰 (적마다 한 번), 끌 수 있는 몹은 창끝에 꿰어 끌어옴
        double backMult = k.num("backMult");
        double hook = k.num("hook");
        Set<UUID> backHit = new HashSet<>();
        List<Hook> hooked = new ArrayList<>();
        Back st = new Back(cEnd, dir, speed);
        Consumer<Vec3> finish = handNow -> {
            // 손에 돌아옴: 손 앞의 작은 번쩍임 · 불꽃 (1인칭 화면 가운데를 덮지 않게 — 오른손 쪽 아래, 1.1칸 앞)
            Vec3 f = Kit.facingOf(sp);
            SkillFx.burst(level, V.add(V.up(handNow, -0.1), f, 1.1), GN_WHITE, 0.35, 0.16);
            SkillFx.sparks(level, V.add(handNow, f, 1.1), GN_BOLT, 5, 3);
            SkillFx.play(level, "item.trident.return", handNow, 1.3, 1.0);
            if (!hooked.isEmpty()) SkillFx.play(level, "item.trident.thunder", handNow, 1.8, 0.5);
        };
        IntPredicate step = i -> {
            if (!ok.getAsBoolean()) return false;
            if (st.left <= 0) {
                Vec3 handNow = handOf.get();
                Vec3 v = V.sub(handNow, st.c);
                double dist = V.len(v);
                if (dist > range + 6) {
                    // 손이 멀리 순간이동함: 창은 바로 손에 돌아옴 (꿰인 몹은 그 자리에 남음)
                    finish.accept(handNow);
                    return false;
                }
                st.u = dist > 1e-3 ? V.unit(v) : dir;
                int segT = (int) Math.max(1, Math.min(3, Math.ceil(dist / speed)));
                st.per = dist / Math.max(segT, Math.ceil(dist / speed));
                st.left = segT;
                st.last = segT * st.per >= dist - 1e-3;
                // 그림: 이 3틱 동안 곧게 (창끝은 바깥 = 움직이는 쪽과 반대) + 가운데의 빛. 손에 거의 왔으면 (2.6칸 안) 그리지 않음 — 창이 눈을 지나가지 않게
                if (shown && dist > 2.6) {
                    double segLen = Math.min(st.per * segT, dist - 2.6); // 손 2.6칸 앞에서 사라짐
                    spearFx(level, st.c, V.scale(st.u, -1), half, segLen / (st.per * 20) + 0.001, Opt.of("move", st.u, "speed", st.per * 20));
                    SkillFx.fx(level, "rpg:arrow_spark", st.c, Opt.of("dir", st.u, "speed", st.per * 20, "size", 0.7, "grow", 1.0,
                            "life", segLen / (st.per * 20) + 0.03, "color", GN_BOLT, "alpha", 0.7));
                }
                if (i == 0) SkillFx.play(level, "item.trident.return", st.c, 0.9, 0.9);
            }
            Vec3 u = st.u;
            Vec3 next = V.add(st.c, u, st.per);
            Vec3 eyeNow = sp.getEyePosition();
            if (shown) SkillFx.beamAway(level, eyeNow, st.c, next, glow, Opt.of("half", 0.55, "thick", 0.14, "life", 0.22, "gap", 0.8, "alpha", 0.75));
            // 지나는 적 (첫 틱은 창끝부터 — 돌아선 자리에서 가운데와 창끝 사이에 있던 적도. 창끝은 벽 앞 (reach) 을 넘지 않음:
            // 벽이 가까워 창이 날지 않았을 때 벽 면에서 재지 않게)
            double tipBack = i == 0 ? Math.min(half, Math.max(0, reach - 0.9)) : 0;
            Vec3 from = V.add(st.c, u, -tipBack);
            for (Kit.LineHit h : Kit.lineHits(sp, from, u, st.per + tipBack + 0.4, width)) {
                LivingEntity e = h.e();
                if (!backHit.add(e.getUUID())) continue;
                if (hooked.stream().filter(x -> !x.free).count() < hook && draggable(e)) hooked.add(new Hook(e, Kit.bodyPoint(e).y - e.getY()));
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, Tasks.now()), ok, () -> {
                    if (Kit.skillHit(sp, e, w, backMult) > 0) {
                        spearHitFx(level, e, sp.getEyePosition(), u, glow, core);
                        SkillFx.play(level, "item.trident.hit", Kit.bodyAt0(e, next), 0.9, 0.8);
                    }
                });
            }
            st.c = next;
            st.left--;
            // 꿰인 몹: 창끝 쪽에 줄지어 끌려옴 + 번개 사슬. 나에게서 멀어지는 쪽으로는 옮기지 않음 (창이 따라잡을 때까지),
            // 끌려갈 자리가 벽 너머 · 막힘이면 · 쓰러지면 빠짐. 발은 그 아래 땅 위 (땅이 가까우면)
            Vec3 tipDir = V.scale(u, -1);
            Vec3 handNow = handOf.get();
            int slot = 0; // 줄 자리: 빠진 몹의 자리는 비우고 앞으로 당김
            for (Hook hk : hooked) {
                if (hk.free) continue;
                if (hk.e.isRemoved() || Kit.hasNoHealth(hk.e)) {
                    hk.free = true;
                    continue;
                }
                Vec3 body = V.add(next, tipDir, half * 0.55 + 1.0 + 0.75 * slot); // 손에 돌아왔을 때 첫 몹이 약 2.2칸 앞
                Vec3 now0 = Kit.bodyAt0(hk.e, body);
                if (i % 2 == 0) SkillFx.bolt(level, V.add(next, tipDir, half * 0.4), now0, GN_BOLT, Opt.of("thick", 0.08, "life", 0.1, "jag", 0.25));
                if (V.len(V.sub(body, handNow)) >= V.len(V.sub(now0, handNow)) - 0.05) {
                    slot++; // 아직 창이 따라잡지 못함 (자리는 그대로)
                    continue;
                }
                if (!Kit.clearPath(level, now0, body)) {
                    hk.free = true;
                    continue;
                }
                double feet = body.y - hk.lift;
                Double gy = Kit.solidBelow(level, V.up(body, 0.3), hk.lift + 2.5);
                if (gy != null && gy > feet) feet = gy;
                if (!Kit.moveTo(hk.e, new Vec3(body.x, feet, body.z))) {
                    hk.free = true; // 옮길 수 없으면 (몸이 블록과 겹침) 거기서 빠짐 — 자리를 비움
                    continue;
                }
                slot++;
            }
            if (st.left <= 0 && st.last) {
                finish.accept(handNow);
                return false;
            }
            return i < 40;
        };
        Tasks.later(outT + hover, () -> Kit.ticks(step));
        return true;
    }

    // ------------------------------------------------------------ 스킬 2 신창 강림

    /**
     * 신창 강림이 떨어질 땅: 조준한 몹의 발밑 → 시선이 닿은 곳 (사거리 끝) 에서 가로로 0.4칸 앞 (벽 면에 붙지 않게) 의 아래 땅 (물 · 용암 위도 됨).
     * 가로 min 칸보다 가까우면 min 칸 앞 — 그 자리가 눈에서 보이지 않으면 (벽 너머) 밀지 않음. 없으면 null (k: range · min)
     */
    static Vec3 fallSpot(ServerPlayer sp, Obj k) {
        ServerLevel level = sp.level();
        Vec3 p = sp.position();
        Vec3 eye = sp.getEyePosition();
        double range = k.num("range");
        double min = k.num("min");
        Vec3 q;
        RpgCombat.Aim r = RpgCombat.aimTarget(sp, range, Kit.BOW.numOr("aimAssist", 0));
        if (r.target() != null && !r.target().isRemoved() && Kit.canHit(sp, r.target())) {
            q = r.target().position();
        } else {
            Vec3 d = r.dir();
            q = V.add(eye, d, r.dist());
            if (Math.hypot(d.x, d.z) > 0.2) q = V.add(q, V.flat(d), -0.4);
        }
        Vec3 f = Kit.facingOf(sp);
        double dx = q.x - p.x;
        double dz = q.z - p.z;
        double h = Math.hypot(dx, dz);
        if (h < min) {
            Vec3 pushed = new Vec3(p.x + (h > 0.5 ? dx / h : f.x) * min, q.y, p.z + (h > 0.5 ? dz / h : f.z) * min);
            if (Kit.lineOfSight(sp, eye, new Vec3(pushed.x, Math.max(q.y, p.y) + 1.0, pushed.z))) q = pushed;
        }
        Kit.Hit hit = Kit.ray(level, new Vec3(q.x, q.y + 1.5, q.z), V.DOWN, 24, true);
        if (hit == null) return null;
        double fy = hit.point().y - hit.pos().getY(); // 맞은 면의 블록 안 높이 (반 블록 = 0.5)
        return new Vec3(q.x, hit.pos().getY() + (fy > 0.05 && fy <= 1 ? fy : 1), q.z);
    }

    /** 회복 모으기: 모인 피해 pool, 다음 틱 회복이 잡혔는지 pending, 이번에 더 회복할 수 있는 양 left */
    private static final class Heal {
        double pool;
        boolean pending;
        double left;

        Heal(double left) {
            this.left = left;
        }
    }

    /**
     * 신창 강림: 창을 하늘로 던짐 → 조준한 곳 (range 칸, 가로 min 칸보다 가까우면 min 칸 앞 · 그 아래 땅) 에 delay 틱 동안 번개 룬 · 떨어지는 그림자 · 먹구름 →
     * 거대한 궁니르 (모델 giant 배 — 굴 · 지붕 아래면 트인 높이에 맞게 줄임) 가 하늘에서 내리꽂힘: 반지름 radius 칸 번개 폭발 (mult, 띄움) →
     * 꽂힌 창에서 pulseGap 틱마다 pulses 번 번개가 튐 (pulseMult — 반지름 radius + 1 칸, 띄운 적은 밀려났어도 radius + 2.5 칸 안이면) → stay 틱 뒤 빛으로 흩어짐.
     * 규칙에 heal 이 있으면 준 피해의 heal 만큼 회복 (한 번 쓸 때 실제로 회복한 양이 최대 체력의 healCap 까지 — 예전 도끼 궁니르의 딜탱 규칙. 창 궁니르는 없음).
     * 땅을 조준하지 않으면 (허공) 재사용 대기 없이 거절.
     * 거대한 창은 조각 넷으로 그림 (가운데가 화면 밖이어도 창날이 보이게)
     */
    static boolean fall(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        Vec3 spot = fallSpot(sp, k);
        if (spot == null) {
            Notify.warn(sp, "§c" + k.str("name") + ": §7땅을 조준해야 합니다 (" + k.text("range") + "칸 안)");
            return false;
        }
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[] glow = HeroKit.colors(k)[0];
        double radius = k.num("radius");
        int delay = (int) k.num("delay");
        double sink = 0.1; // 창끝이 땅에 박힌 몫
        double giant = k.num("length") * k.num("giant");
        // 하늘이 트인 높이 (굴 · 지붕 아래면 거기에 맞게 창을 줄이고 덜 떨어뜨림 — 그림이 천장을 뚫지 않게)
        double room = Kit.skyRoom(level, V.up(spot, 0.2), giant + k.num("drop") + 2, true) + 0.2;
        double spearLen = Math.min(giant, Math.max(1.5, room / (1 - sink))); // 거대한 창 길이
        double half = spearLen / 2;
        double drop = Math.max(0, Math.min(k.num("drop"), room - spearLen * (1 - sink)));
        double cloud = Math.max(1.2, Math.min(k.num("cloud"), room - 0.8));
        Vec3 center = V.up(spot, half - spearLen * sink);
        Vec3 head = V.up(spot, Math.min(spearLen * 0.18, 2.2)); // 땅 위로 드러난 창날쯤 (번개가 튀는 곳)
        Vec3 f = Kit.facingOf(sp);
        Vec3 hand = V.add(V.up(sp.getEyePosition(), -0.2), V.rightOf(f), 0.3);
        // 던짐: 눈앞 1.2칸에서 하늘로 솟는 창 (눈을 스치지 않게) + 천둥
        Vec3 rise = V.add(hand, f, 1.2);
        spearFx(level, V.up(rise, 2.6), V.UP, k.num("half"), 0.5, Opt.of("move", V.UP, "speed", 64.0));
        SkillFx.beam(level, V.up(rise, 1.8), V.up(rise, 14), glow, Opt.of("half", 0.5, "thick", 0.2, "life", 0.35, "sweep", 0.2, "gap", 0.8));
        SkillFx.play(level, "item.trident.throw", hand, 0.6, 1.0);
        SkillFx.play(level, "item.trident.riptide_3", sp.position(), 0.7, 0.6);
        SkillFx.play(level, "ambient.weather.thunder", spot, 1.5, 0.35);
        // 예고: 번개 룬 (그려짐) · 커지는 그림자 · 먹구름 · 솟는 불꽃
        int fallT = (int) Math.min(k.num("fall"), delay - 1);
        for (int t = 0; t < delay; t += 2) {
            final int tt = t;
            Tasks.later(t, () -> {
                if (!ok.getAsBoolean()) return;
                double g = (tt + 2.0) / delay;
                SkillFx.rune(level, spot, Opt.of("size", radius * 0.95, "life", 0.13, "progress", Math.min(1, g * 1.2), "spin", tt * 9.0, "color", GN_BOLT,
                        "alpha", 0.9));
                SkillFx.fx(level, "rpg:tele_disc", V.up(spot, 0.05), Opt.of("size", radius * (0.25 + 0.6 * g), "life", 0.13, "color", GN_DARK,
                        "alpha", 0.55 + 0.3 * g));
                if (tt % 4 == 0) {
                    SkillFx.rune(level, V.up(spot, cloud), Opt.of("size", radius * 1.15, "life", 0.25, "spin", -tt * 6.0, "color", GN_CLOUD, "alpha", 0.85,
                            "rate", 30.0));
                    double a = Kit.rand() * Math.PI * 2;
                    double rr = radius * (0.5 + Kit.rand() * 0.45);
                    SkillFx.fx(level, "rpg:arrow_spark", new Vec3(spot.x + Math.cos(a) * rr, spot.y + 0.2, spot.z + Math.sin(a) * rr),
                            Opt.of("dir", V.UP, "speed", 3.0, "size", 0.12, "life", 0.4, "color", GN_BOLT, "alpha", 1.0));
                }
                if (tt == 8 && cloud > 3) {
                    SkillFx.bolt(level, V.up(spot, cloud), V.up(spot, Math.max(1, cloud - 6)), GN_BOLT, Opt.of("thick", 0.16, "life", 0.2, "jag", 0.8));
                }
            });
        }
        // 낙하: 하늘에서 거대한 창이 fallT 틱 동안 곧게 떨어짐 (뒤에 빛줄기)
        Tasks.later(delay - fallT, () -> {
            if (!ok.getAsBoolean()) return;
            spearParts(level, V.up(center, drop), V.DOWN, half, fallT / 20.0, Opt.of("move", V.DOWN, "speed", drop / (fallT / 20.0)));
            if (drop > 2) {
                SkillFx.beam(level, V.up(spot, drop + spearLen * 0.9), V.up(spot, spearLen * 0.8), GN_BOLT, Opt.of("half", 1.2, "thick", 1.4, "life", 0.3,
                        "sweep", fallT / 20.0, "gap", 2.0, "alpha", 0.55));
            }
            SkillFx.play(level, "item.trident.riptide_3", spot, 0.5, 1.0);
        });
        // 띄운 적 (밀려나도 번개가 따라감)
        Map<UUID, LivingEntity> struck = new LinkedHashMap<>();
        // 회복 (규칙에 heal 이 있을 때만): 준 피해의 heal 만큼 — 모아서 다음 틱에 한 번 (숫자가 적마다 뜨지 않게), 한 번 쓸 때 실제로 회복한 양이 최대 체력의 healCap 까지
        Heal heal = new Heal(RpgHealth.maxOf(sp) * k.numOr("healCap", 1));
        double healRate = k.numOr("heal", 0);
        DoubleConsumer addHeal = got -> {
            if (healRate <= 0) return;
            heal.pool += got;
            if (heal.pending) return;
            heal.pending = true;
            Tasks.later(1, () -> {
                heal.pending = false;
                if (heal.pool <= 0 || heal.left <= 0 || !ok.getAsBoolean()) return;
                double want = Math.min(heal.left, heal.pool * healRate);
                heal.pool = 0;
                double healed = RpgHealth.healBy(sp, want);
                heal.left -= Math.max(0, healed);
                if (healed > 0) DamageNumbers.show(sp, healed, DamageNumbers.Style.HEAL, "", 0);
            });
        };
        boolean[] landed = {false};
        // 꽂힘
        Tasks.later(delay, () -> {
            if (!ok.getAsBoolean()) return;
            landed[0] = true;
            spearParts(level, center, V.DOWN, half, k.num("stay") / 20, Opt.empty());
            // 먹구름에서 창으로 번개 (창대 가운데쯤 — 먹구름보다 창이 길면 창대에)
            double strike = Math.min(cloud - 0.5, spearLen * (1 - sink) * 0.55);
            if (cloud > 2.5) {
                double a = Kit.rand() * Math.PI * 2;
                Vec3 top = new Vec3(spot.x + Math.cos(a) * 1.5, spot.y + cloud, spot.z + Math.sin(a) * 1.5);
                SkillFx.bolt(level, top, V.up(spot, strike), GN_BOLT, Opt.of("thick", 0.5, "life", 0.4, "jag", 1.0));
                SkillFx.bolt(level, top, V.up(spot, strike), GN_WHITE, Opt.of("thick", 0.18, "life", 0.32, "jag", 1.0));
            }
            SkillFx.burst(level, V.up(spot, 0.8), GN_WHITE, 4.5, 0.4);
            SkillFx.burst(level, V.up(spot, 2.2), glow, 3.2, 0.35);
            SkillFx.shock(level, spot, GN_BOLT, radius / 2.1, 0.55);
            SkillFx.shock(level, spot, GN_WHITE, radius / 4.2, 0.4);
            SkillFx.rune(level, spot, Opt.of("size", radius * 0.95, "life", 0.8, "color", GN_BOLT, "alpha", 0.9, "grow", 0.12, "rate", -80.0));
            SkillFx.fx(level, "rpg:ground_crack", V.up(spot, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", radius * 1.5, "life", 1.6,
                    "color", GN_DEEP, "alpha", 0.85));
            for (int j = 0; j < 8; j++) {
                double a = (j / 8.0) * Math.PI * 2 + Kit.rand() * 0.4;
                Vec3 end = new Vec3(spot.x + Math.cos(a) * radius, spot.y + 0.15, spot.z + Math.sin(a) * radius);
                SkillFx.bolt(level, V.up(spot, 0.3), end, GN_BOLT, Opt.of("thick", 0.16, "life", 0.3, "jag", 0.6));
            }
            SkillFx.sparks(level, V.up(spot, 1.0), GN_WHITE, 14, 8);
            SkillFx.play(level, "item.trident.thunder", spot, 0.7, 1.3);
            SkillFx.play(level, "ambient.weather.thunder", spot, 1.0, 1.0);
            SkillFx.play(level, "random.explode", spot, 0.8, 0.8);
            SkillFx.play(level, "mace.heavy_smash_ground", spot, 0.9, 0.9);
            Kit.shakeNear(level, spot, 14, 0.4, 0.4);
            long t0 = Tasks.now();
            Vec3 eye = V.up(spot, 1.2);
            for (LivingEntity e : Kit.visibleFrom(sp, eye, Kit.around(sp, spot, radius))) {
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, t0), ok, () -> {
                    double got = Kit.skillHit(sp, e, w, k.num("mult"));
                    if (got <= 0) return;
                    struck.put(e.getUUID(), e);
                    addHeal.accept(got);
                    SkillFx.burst(level, Kit.bodyAt0(e, eye), GN_WHITE, 0.9, 0.25);
                    Vec3 d = V.flat(V.sub(e.position(), spot));
                    Kit.knockback(e, d.x * 0.3, d.z * 0.3, 0.6);
                });
            }
        });
        // 꽂힌 창에서 번개가 튐
        int pulses = (int) k.num("pulses");
        int pulseGap = (int) k.num("pulseGap");
        for (int i = 1; i <= pulses; i++) {
            final int ii = i;
            Tasks.later(delay + pulseGap * i, () -> {
                if (!ok.getAsBoolean()) return;
                long t0 = Tasks.now();
                List<LivingEntity> near = Kit.visibleFrom(sp, head, Kit.around(sp, spot, radius + 1));
                List<LivingEntity> pushed = new ArrayList<>();
                for (LivingEntity e : struck.values()) {
                    if (e.isRemoved()) continue;
                    if (Math.hypot(e.getX() - spot.x, e.getZ() - spot.z) <= radius + 2.5 + Kit.bodyPad(e) && Math.abs(e.getY() - spot.y) <= 5) pushed.add(e);
                }
                List<LivingEntity> list = Kit.union(near, pushed);
                for (int j = 0; j < Math.min(8, list.size()); j++) {
                    SkillFx.bolt(level, head, Kit.bodyAt0(list.get(j), head), GN_BOLT, Opt.of("thick", 0.14, "life", 0.22, "jag", 0.5));
                }
                if (list.isEmpty()) {
                    for (int j = 0; j < 3; j++) {
                        SkillFx.bolt(level, head, V.up(V.add(spot, V.turnY(new Vec3(1, 0, 0), Kit.rand() * 360), 2 + Kit.rand() * 3), 0.1), GN_BOLT,
                                Opt.of("thick", 0.12, "life", 0.2, "jag", 0.5));
                    }
                }
                SkillFx.burst(level, head, GN_WHITE, 1.6, 0.25);
                SkillFx.shock(level, spot, GN_BOLT, 1.4, 0.35);
                SkillFx.play(level, "ambient.weather.lightning.impact", spot, 1.2 + 0.2 * ii, 0.7);
                for (LivingEntity e : list) {
                    Kit.hitAt(sp, e, Kit.reserveHit(sp, e, t0), ok, () -> {
                        // 번개 = 마법 피해 (방어구 무시 — 예전 궁니르 번개와 같은 규칙)
                        double got = Kit.skillHit(sp, e, w, k.num("pulseMult"), Opt.of("cause", Kit.Cause.MAGIC));
                        if (got <= 0) return;
                        addHeal.accept(got);
                        SkillFx.burst(level, Kit.bodyAt0(e, head), GN_BOLT, 0.7, 0.2);
                    });
                }
            });
        }
        // 빛으로 흩어짐 (꽂힌 창만 — 떨어지기 전에 끊겼으면 없음. 꽂힌 뒤에는 쓴 사람이 쓰러져도 창은 흩어짐)
        Tasks.later(delay + (int) k.num("stay"), () -> {
            if (!landed[0]) return;
            spearParts(level, center, V.DOWN, half, k.num("fade") / 20, Opt.of("progress", 2.0));
            for (int j = 0; j < 6; j++) {
                SkillFx.fx(level, "rpg:arrow_spark", V.up(spot, 1 + Kit.rand() * spearLen * 0.6), Opt.of("dir", V.UP, "speed", 2 + Kit.rand() * 2, "size", 0.18,
                        "life", 0.6, "color", GN_WHITE, "alpha", 1.0));
            }
            SkillFx.play(level, "beacon.deactivate", spot, 1.6, 0.6);
        });
        return true;
    }
}
