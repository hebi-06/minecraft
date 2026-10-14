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
import mmorpg.fabric.rune.RuneKit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 반고의 도끼 (도끼 초월 — 하늘과 땅을 가른 도끼). 예전 궁니르 (창 모양 — 12단계에 창 종류가 생겨 종류가 애매해짐) 자리를 실제 도끼로 바꾼 것:
 * 스킬 동작 · 수치 · 재사용 대기는 궁니르와 같고 (SKILLS.axe — rules_more), 연출만 도끼 · 대지 (번개 대신 균열 · 바위 가시 · 흙먼지).
 * <ul>
 * <li>혼돈 가르기 (pangu_return): 도는 도끼를 던져 꿰뚫음 → 끝에서 돌며 흙 · 돌을 모음 → 손으로 돌아오며 다시 벰 · 몹을 끌어옴</li>
 * <li>천지개벽 (pangu_fall): 하늘에서 거대한 도끼가 내리찍음 → 땅이 갈라짐 (폭발 · 띄움) → 여진 두 번 (바위 가시 — 마법 피해) → 준 피해 일부 회복</li>
 * </ul>
 * 그림: rpg:rfx_relicspin 칸 8 (반고의 도끼 모델 비스듬히 — tools/java_fx_art.py), 땅 균열 (rpg:ground_crack · rfx_crack) · 바위 가시 (RuneKit.rockSpike).
 */
final class Pangu {
    private Pangu() {}

    /** rfx_relicspin 의 반고의 도끼 칸 (tools/java_fx_art.py RELIC 순서) */
    static final int RELIC_SPIN_CELL = 8;
    /**
     * 내리찍은 자세: rfx_relicspin 그림은 자루가 오른쪽 위 (45°) 로 비스듬하고 날이 머리 오른쪽 아래 — 시계 방향으로 45° 돌리면 자루가 가로 · 날이 아래.
     * 그 자세에서 그림 가운데 기준 날 끝은 아래 BLADE_DOWN × size, 날 가운데는 오른쪽 BLADE_RIGHT × size (반고의 도끼 모델 기준 — 디자인을 바꾸면 사진첩으로 확인)
     */
    static final double CHOP = -45;
    static final double BLADE_DOWN = 0.35;
    static final double BLADE_RIGHT = 0.4;

    /** 흙먼지 · 그을린 땅 · 하늘 (옥빛) */
    static final double[] DUST = {0.62, 0.52, 0.4};
    static final double[] DEEP = {0.3, 0.17, 0.07};
    static final double[] SKY = {0.62, 0.95, 0.82};
    static final double[] WHITE = {1.0, 0.97, 0.88};

    /** 끌지 않는 무거운 몹 (룬 중력의 heavy 목록 — 자바 이름) */
    static final Set<String> HEAVY_MOB = javaTypes(Rules.get().obj("RUNE_GRAVITY").listOrNull("heavy"));
    /** 끌지 않는 바닐라 몹 (몸이 넓거나 무거움 — 몸 크기를 스크립트가 모름) */
    static final Set<String> DRAG_SKIP = javaTypes(List.of("minecraft:ravager", "minecraft:sniffer", "minecraft:camel", "minecraft:hoglin", "minecraft:zoglin",
            "minecraft:polar_bear", "minecraft:horse", "minecraft:donkey", "minecraft:mule", "minecraft:skeleton_horse", "minecraft:zombie_horse",
            "minecraft:llama", "minecraft:trader_llama", "minecraft:panda", "minecraft:strider", "minecraft:breeze"));

    static void register() {
        Transcend.register("pangu_return", Pangu::split);
        Transcend.register("pangu_fall", Pangu::cleave);
    }

    /** 애드온 (베드락) 엔티티 이름 목록 → 자바 이름 모음 */
    private static Set<String> javaTypes(List<?> ids) {
        Set<String> out = new HashSet<>();
        if (ids != null) for (Object id : ids) if (id instanceof String s) out.add(Kit.javaType(s));
        return Set.copyOf(out);
    }

    // ------------------------------------------------------------ 그림 도구

    /** 도는 도끼 그림 하나: at 에서 vel 칸/초로 움직이며 life 초, 처음 각 spin · 초당 spinRate 도, 끝 fade 몫은 흐려짐 */
    static void axe(ServerLevel level, Vec3 at, Vec3 vel, double size, double life, double spin, double spinRate, double fade) {
        RuneKit.rfx(level, "relicspin", at, Opt.of("size", size, "life", life, "spin", spin, "spinRate", spinRate, "cell", (double) RELIC_SPIN_CELL,
                "alpha", 1.0, "fade", fade, "vel", vel));
    }

    /** 도끼가 벤 자리: 금빛 X 번쩍임 + 불꽃 + 튀는 돌 (눈 가까이는 작게) */
    static void hitFx(ServerLevel level, Entity e, Vec3 eye, Vec3 dir, double[] glow, double[] core) {
        Vec3 c = Kit.bodyAt0(e, eye);
        double near = Math.min(1, Math.max(0.45, V.len(V.sub(c, eye)) / 3));
        SkillFx.fx(level, "rpg:slash_x", c, Opt.of("dir", dir, "size", 1.0 * near, "life", 0.25, "color", glow, "alpha", 1.0));
        SkillFx.fx(level, "rpg:slash_x", V.add(c, dir, -0.04), Opt.of("dir", dir, "size", 0.7 * near, "life", 0.2, "color", core, "alpha", 1.0));
        SkillFx.sparks(level, c, core, 5, 5);
        RuneKit.rockBurst(level, c, 2, 4, 0.16);
    }

    /**
     * 혼돈 가르기가 꿰어 끌어올 수 있는 몹: 살아 있는 보통 몹 — 플레이어 · 보스 · 보스 소품 · 허수아비 · 아주 큰 몹 (엔더 드래곤 등) ·
     * 넓거나 키 큰 몹 (엔더맨 · 트롤 등) 은 아님 (피해만)
     */
    static boolean draggable(Entity e) {
        if (e == null || e.isRemoved() || Kit.hasNoHealth(e) || e instanceof Player) return false;
        String type = Kit.typeId(e);
        if (HEAVY_MOB.contains(type) || DRAG_SKIP.contains(type) || Kit.bodyPad(e) > 0.6) return false;
        if (e.getEyeY() - e.getY() > 2.3) return false; // 키 큰 몸 (손 높이로 끌면 발이 땅에 박혀 옮겨지지 않음)
        return !(Kit.hasFamily(e, "rpg_boss") || Kit.hasFamily(e, "rpg_boss_prop") || Kit.hasFamily(e, "rpg_dummy"));
    }

    // ------------------------------------------------------------ 스킬 1 혼돈 가르기

    /** 돌아오는 도끼: 가운데 c, 움직이는 단위 방향 u, 틱당 칸 per, 이 곧은 구간의 남은 틱 left, 이 구간 끝이 손인지 last */
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

    /** 도끼에 꿰인 몹: 몸 가운데가 발에서 lift 칸 위, free = 빠짐 (쓰러짐 · 벽 너머 · 막힘) */
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
     * 혼돈 가르기: 조준한 쪽 (조준 보정 — 시선에 몹이 겹치지 않으면 가까운 몹 쪽, 도끼가 닿는 거리 안) 으로 도는 도끼를 던짐. 도끼가 손에서 range 칸
     * (벽 앞까지) 에 닿을 때까지 틱당 speed 칸으로 날며 폭 width 칸의 적을 꿰뚫음 (mult) → 끝에서 hover 틱 동안 제자리에서 돌며 흙 · 돌을 모음 →
     * 손으로 돌아옴 (3틱마다 지금 손을 다시 겨눔): 지나는 적을 다시 벰 (backMult) — 끌 수 있는 몹은 hook 마리까지 도끼에 걸어 내 앞까지 끌어옴.
     * 몹은 나에게서 멀어지는 쪽으로는 옮기지 않고 (도끼가 따라잡을 때까지 기다림), 벽 너머 · 막힌 자리로 옮기게 되면 · 쓰러지면 빠짐.
     * 몹 무적 시간을 피해 적마다 틱을 잡아 넣음 (reserveHit · hitAt). 쓰러지거나 차원을 옮기면 멈춤, 돌아오는 동안 손이 멀리 순간이동하면 도끼는 바로 손에 돌아옴.
     * 벽이 가까우면 (도끼가 날 거리가 없으면) 도끼 그림 없이 벽에 부딪힌 자리의 돌 · 먼지로만 (1인칭 화면을 덮지 않게).
     * 수치 · 판정은 예전 궁니르 신창 귀환과 같음 (도끼 그림 크기 size)
     */
    static boolean split(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = HeroKit.colors(k);
        double[] glow = cs[0];
        double[] core = cs[1];
        double range = k.num("range");
        double half = k.num("half"); // 판정: 도끼 가운데에서 앞 끝까지 (예전 창 반 길이와 같음)
        double size = k.numOr("size", 1.35);
        double speed = k.num("speed");
        double width = k.num("width");
        double spinRate = -1080;
        Supplier<Vec3> handOf = () -> V.add(V.up(sp.getEyePosition(), -0.25), V.rightOf(Kit.facingOf(sp)), 0.3);
        Vec3 hand = handOf.get();
        Vec3 eye0 = sp.getEyePosition();
        RpgCombat.Aim r = RpgCombat.aimTarget(sp, range - 1, Kit.BOW.numOr("aimAssist", 0));
        Vec3 dir = r.target() != null && !r.target().isRemoved() && Kit.canHit(sp, r.target())
                ? V.unit(V.sub(Kit.bodyPoint(r.target()), hand))
                : V.unit(r.dir());
        double reach = Math.max(1.5, Kit.rayDist(level, hand, dir, range) - 0.3); // 도끼 앞 끝이 닿는 거리 (벽 앞)
        Vec3 c0 = V.add(hand, dir, 0.9); // 판정의 도끼 가운데 (앞 끝 = 가운데 + half)
        double travel = Math.max(0, reach - 0.9 - half);
        boolean shown = travel >= 1.5; // 도끼 그림을 그릴 만큼 날아가는지
        int hover = shown ? (int) k.num("hover") : 0;
        int outT = (int) Math.max(1, Math.ceil(travel / speed));
        Vec3 cEnd = V.add(c0, dir, travel);
        long now = Tasks.now();
        double spin0 = Kit.rand() * 360;
        // 지금 도끼가 돈 각 (돌아올 때 이어서)
        java.util.function.LongToDoubleFunction spinAt = tick -> spin0 + spinRate * ((tick - now) / 20.0);
        // 던짐: 손 앞의 작은 번쩍임 (1.6칸 앞) + 돌며 날아가는 도끼 (그림은 손 앞 2.4칸부터 — 눈을 스치지 않게) + 지나간 자리의 금빛 줄 · 흙먼지
        SkillFx.burst(level, V.add(hand, dir, 1.6), glow, 0.5, 0.18);
        if (shown) {
            Vec3 vc0 = V.add(hand, dir, 2.4);
            double vTravel = Math.max(0, V.len(V.sub(cEnd, vc0)));
            double vOut = vTravel / (outT / 20.0);
            axe(level, vc0, V.scale(dir, vOut), size, outT / 20.0, spin0, spinRate, 0);
            SkillFx.fx(level, "rpg:arrow_spark", vc0, Opt.of("dir", dir, "speed", vOut, "size", 0.6, "grow", 1.0, "life", outT / 20.0 + 0.05, "color", glow,
                    "alpha", 0.7));
            Vec3 a = V.add(hand, dir, 1.6);
            SkillFx.beam(level, a, cEnd, glow, Opt.of("half", 0.5, "thick", 0.28, "life", 0.4, "sweep", outT / 20.0, "gap", 0.8, "alpha", 0.8));
            SkillFx.beam(level, a, cEnd, core, Opt.of("half", 0.45, "thick", 0.08, "life", 0.3, "sweep", outT / 20.0, "gap", 1.6, "alpha", 1.0));
            int n = (int) Math.max(1, Math.round(travel / 4));
            double span = V.len(V.sub(cEnd, a));
            for (int i = 0; i < n; i++) {
                Vec3 s = V.add(a, dir, (span * (i + 1)) / n);
                Tasks.later((int) Math.round(((i + 1.0) / n) * outT), () -> {
                    if (!ok.getAsBoolean()) return;
                    SkillFx.sparks(level, s, DUST, 4, 3);
                    SkillFx.sparks(level, s, glow, 2, 3);
                });
            }
        } else {
            // 벽이 가까움: 도끼가 닿는 자리에 튀는 돌 · 먼지만
            Vec3 tip = V.add(hand, dir, reach);
            SkillFx.burst(level, tip, core, 0.9, 0.22);
            SkillFx.sparks(level, tip, glow, 8, 5);
            RuneKit.rockBurst(level, tip, 4, 5, 0.2);
            SkillFx.play(level, "dig.stone", tip, 0.7, 1.0);
        }
        SkillFx.play(level, "item.trident.throw", hand, 0.7, 1.0);
        SkillFx.play(level, "mob.breeze.whirl", hand, 1.2, 0.7);
        // 갈 때: 도끼 앞 끝이 지나가는 순간 꿰뚫음
        for (Kit.LineHit h : Kit.lineHits(sp, hand, dir, reach, width)) {
            LivingEntity e = h.e();
            long at = now + (long) Math.max(0, Math.ceil((h.t() - 0.9 - half) / speed));
            Kit.hitAt(sp, e, Kit.reserveHit(sp, e, at), ok, () -> {
                if (Kit.skillHit(sp, e, w, k.num("mult")) > 0) {
                    hitFx(level, e, eye0, dir, glow, core);
                    SkillFx.play(level, "entity.player.attack.sweep", Kit.bodyAt0(e, eye0), 0.8, 0.9);
                }
            });
        }
        // 끝: hover 틱 동안 제자리에서 돎 (가까우면 작게) + 둘레에서 흙 · 돌이 모여듦
        if (hover > 0) {
            Tasks.later(outT, () -> {
                if (!ok.getAsBoolean()) return;
                double dEye = V.len(V.sub(cEnd, sp.getEyePosition()));
                axe(level, cEnd, V.ZERO, size * Math.min(1, Math.max(0.4, (dEye - 1.5) / 4.5)), hover / 20.0 + 0.02, spinAt.applyAsDouble(Tasks.now()), spinRate * 1.3,
                        0);
                SkillFx.shock(level, V.up(cEnd, -0.3), glow, 1.2, 0.35);
                SkillFx.play(level, "mob.breeze.whirl", cEnd, 0.8, 0.8);
            });
            for (int h = 0; h < hover; h++) {
                final int hh = h;
                Tasks.later(outT + h, () -> {
                    if (!ok.getAsBoolean()) return;
                    Vec3 side = V.rightOf(V.flat(dir));
                    double a = Kit.rand() * Math.PI * 2;
                    double rr = 1.3 + Kit.rand() * 0.9;
                    Vec3 from = V.add(V.up(cEnd, Math.sin(a) * rr), side, Math.cos(a) * rr);
                    SkillFx.fx(level, "rpg:arrow_spark", from, Opt.of("dir", V.unit(V.sub(cEnd, from)), "speed", rr * 6, "size", 0.14, "life", 0.16, "color", DUST,
                            "alpha", 1.0));
                    if (hh == 1) {
                        SkillFx.burst(level, cEnd, core, 1.4, 0.25);
                        SkillFx.sparks(level, cEnd, glow, 8, 6);
                        RuneKit.rockBurst(level, cEnd, 4, 4, 0.2);
                        SkillFx.play(level, "dig.stone", cEnd, 0.6, 1.0);
                    }
                });
            }
        }
        // 돌아옴: 3틱마다 지금 손을 다시 겨눠 그 3틱은 곧게 (그림과 판정이 같은 길). 지나는 적을 벰 (적마다 한 번), 끌 수 있는 몹은 도끼에 걸어 끌어옴
        double backMult = k.num("backMult");
        double hook = k.num("hook");
        Set<UUID> backHit = new HashSet<>();
        List<Hook> hooked = new ArrayList<>();
        Back st = new Back(cEnd, dir, speed);
        Consumer<Vec3> finish = handNow -> {
            // 손에 돌아옴: 손 앞의 작은 번쩍임 · 불꽃 (1인칭 화면 가운데를 덮지 않게 — 오른손 쪽 아래, 1.1칸 앞)
            Vec3 f = Kit.facingOf(sp);
            SkillFx.burst(level, V.add(V.up(handNow, -0.1), f, 1.1), core, 0.35, 0.16);
            SkillFx.sparks(level, V.add(handNow, f, 1.1), glow, 5, 3);
            SkillFx.play(level, "item.trident.return", handNow, 1.1, 1.0);
            if (!hooked.isEmpty()) SkillFx.play(level, "mace.smash_ground", handNow, 1.2, 0.6);
        };
        IntPredicate step = i -> {
            if (!ok.getAsBoolean()) return false;
            if (st.left <= 0) {
                Vec3 handNow = handOf.get();
                Vec3 v = V.sub(handNow, st.c);
                double dist = V.len(v);
                if (dist > range + 6) {
                    // 손이 멀리 순간이동함: 도끼는 바로 손에 돌아옴 (꿰인 몹은 그 자리에 남음)
                    finish.accept(handNow);
                    return false;
                }
                st.u = dist > 1e-3 ? V.unit(v) : dir;
                int segT = (int) Math.max(1, Math.min(3, Math.ceil(dist / speed)));
                st.per = dist / Math.max(segT, Math.ceil(dist / speed));
                st.left = segT;
                st.last = segT * st.per >= dist - 1e-3;
                // 그림: 이 3틱 동안 곧게 날며 돎 (손에 거의 왔으면 (2.6칸 안) 그리지 않음 — 도끼가 눈을 지나가지 않게)
                if (shown && dist > 2.6) {
                    double segLen = Math.min(st.per * segT, dist - 2.6); // 손 2.6칸 앞에서 사라짐
                    axe(level, st.c, V.scale(st.u, st.per * 20), size, segLen / (st.per * 20) + 0.001, spinAt.applyAsDouble(Tasks.now()), spinRate, 0);
                    SkillFx.fx(level, "rpg:arrow_spark", st.c, Opt.of("dir", st.u, "speed", st.per * 20, "size", 0.55, "grow", 1.0,
                            "life", segLen / (st.per * 20) + 0.03, "color", glow, "alpha", 0.6));
                }
                if (i == 0) SkillFx.play(level, "item.trident.return", st.c, 0.8, 0.9);
            }
            Vec3 u = st.u;
            Vec3 next = V.add(st.c, u, st.per);
            Vec3 eyeNow = sp.getEyePosition();
            if (shown) SkillFx.beamAway(level, eyeNow, st.c, next, glow, Opt.of("half", 0.55, "thick", 0.14, "life", 0.22, "gap", 0.8, "alpha", 0.7));
            // 지나는 적 (첫 틱은 도끼 앞 끝부터 — 돌아선 자리에서 가운데와 앞 끝 사이에 있던 적도. 앞 끝은 벽 앞 (reach) 을 넘지 않음:
            // 벽이 가까워 도끼가 날지 않았을 때 벽 면에서 재지 않게)
            double tipBack = i == 0 ? Math.min(half, Math.max(0, reach - 0.9)) : 0;
            Vec3 from = V.add(st.c, u, -tipBack);
            for (Kit.LineHit h : Kit.lineHits(sp, from, u, st.per + tipBack + 0.4, width)) {
                LivingEntity e = h.e();
                if (!backHit.add(e.getUUID())) continue;
                if (hooked.stream().filter(x -> !x.free).count() < hook && draggable(e)) hooked.add(new Hook(e, Kit.bodyPoint(e).y - e.getY()));
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, Tasks.now()), ok, () -> {
                    if (Kit.skillHit(sp, e, w, backMult) > 0) {
                        hitFx(level, e, sp.getEyePosition(), u, glow, core);
                        SkillFx.play(level, "entity.player.attack.sweep", Kit.bodyAt0(e, next), 0.7, 0.9);
                    }
                });
            }
            st.c = next;
            st.left--;
            // 걸린 몹: 도끼 뒤쪽에 줄지어 끌려옴 + 금빛 끈. 나에게서 멀어지는 쪽으로는 옮기지 않음 (도끼가 따라잡을 때까지),
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
                if (i % 2 == 0) SkillFx.beam(level, V.add(next, tipDir, half * 0.3), now0, glow, Opt.of("half", 0.12, "thick", 0.07, "life", 0.12, "gap", 0.6,
                        "alpha", 0.8));
                if (V.len(V.sub(body, handNow)) >= V.len(V.sub(now0, handNow)) - 0.05) {
                    slot++; // 아직 도끼가 따라잡지 못함 (자리는 그대로)
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

    // ------------------------------------------------------------ 스킬 2 천지개벽

    /**
     * 천지개벽이 떨어질 땅: 조준한 몹의 발밑 → 시선이 닿은 곳 (사거리 끝) 에서 가로로 0.4칸 앞 (벽 면에 붙지 않게) 의 아래 땅 (물 · 용암 위도 됨).
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

    /** 갈라진 땅 한 줄기: spot 에서 각 a 쪽으로 radius 칸까지 금 (rfx_crack) 셋 · 끝에 바위 가시 */
    private static void crackLine(ServerPlayer sp, Vec3 spot, double a, double radius, double[] glow, double[] core) {
        ServerLevel level = sp.level();
        Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
        for (int j = 1; j <= 3; j++) {
            Vec3 p = V.up(V.add(spot, d, radius * j / 3.4), 0.07);
            RuneKit.rfx2(level, "crack", p, Opt.of("dir", V.UP, "size", 0.9 + 0.2 * j, "life", 1.8, "fade", 0.4, "spin", Kit.rand() * 360, "cell", 0.0),
                    glow, core, new int[] {0, 1}, null);
        }
        RuneKit.rockSpike(sp, level, V.add(spot, d, radius * (0.75 + Kit.rand() * 0.15)), 0.8 + Kit.rand() * 0.3, glow,
                Opt.of("rock", RuneKit.ROCK, "life", 1.4, "grow", 0.25, "full", 3.0));
    }

    /**
     * 천지개벽: 도끼를 하늘로 던짐 → 조준한 곳 (range 칸, 가로 min 칸보다 가까우면 min 칸 앞 · 그 아래 땅) 에 delay 틱 동안 땅의 문양 · 커지는 그림자 ·
     * 하늘의 문양 (옥빛) · 하늘이 갈라지는 금빛 줄 → 거대한 반고의 도끼가 하늘에서 내리찍음 (giantSize — 굴 · 지붕 아래면 트인 높이에, 가까우면 화면을
     * 덮지 않게 줄임): 반지름 radius 칸 땅이 갈라짐 (mult, 띄움) — 균열 · 바위 가시 · 흙먼지 → 박힌 도끼에서 pulseGap 틱마다 pulses 번 여진
     * (pulseMult · 마법 피해 — 반지름 radius + 1 칸, 띄운 적은 밀려났어도 radius + 2.5 칸 안이면) → stay 틱 뒤 빛으로 흩어짐.
     * 준 피해의 heal 만큼 회복 (도끼 규칙 딜탱 — 한 번 쓸 때 실제로 회복한 양이 최대 체력의 healCap 까지). 땅을 조준하지 않으면 (허공) 재사용 대기 없이 거절.
     * 수치 · 판정은 예전 궁니르 신창 강림과 같음
     */
    static boolean cleave(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        Vec3 spot = fallSpot(sp, k);
        if (spot == null) {
            Notify.warn(sp, "§c" + k.str("name") + ": §7땅을 조준해야 합니다 (" + k.text("range") + "칸 안)");
            return false;
        }
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = HeroKit.colors(k);
        double[] glow = cs[0];
        double[] core = cs[1];
        double radius = k.num("radius");
        int delay = (int) k.num("delay");
        Vec3 f = Kit.facingOf(sp);
        Vec3 right = V.rightOf(f);
        // 거대한 도끼 크기: 가까우면 (3인칭 카메라 · 눈) 작게, 트인 높이가 모자라면 그만큼 (내리찍은 자세의 높이 ≈ 0.7 × size)
        double want = k.num("giantSize") * RuneKit.nearK(sp, V.up(spot, 2), 14, 0.5);
        double room = Kit.skyRoom(level, V.up(spot, 0.2), want * 0.75 + k.num("drop") + 2, true) + 0.2;
        double size = Math.max(1.5, Math.min(want, room / 0.75));
        double drop = Math.max(0, Math.min(k.num("drop"), room - size * 0.75));
        double sky = Math.max(1.2, Math.min(k.num("cloud"), room - 0.8));
        // 박힌 자리: 날 가운데가 spot (쓴 사람이 보기에 — 그림 가운데는 왼쪽으로), 날 끝이 날 깊이의 1/5 쯤 땅에 박힘
        Vec3 center = V.up(V.add(spot, right, -BLADE_RIGHT * size), (BLADE_DOWN - 0.1) * size);
        Vec3 head = V.up(spot, 1.2); // 여진이 튀는 곳 (박힌 날 둘레)
        Vec3 hand = V.add(V.up(sp.getEyePosition(), -0.2), right, 0.3);
        // 던짐: 눈앞 1.2칸에서 돌며 하늘로 솟는 도끼 (눈을 스치지 않게) + 땅울림
        Vec3 rise = V.add(hand, f, 1.2);
        axe(level, V.up(rise, 2.4), new Vec3(0, 40, 0), 1.2, 0.45, Kit.rand() * 360, 1440, 0.3);
        SkillFx.beam(level, V.up(rise, 1.8), V.up(rise, 14), glow, Opt.of("half", 0.5, "thick", 0.2, "life", 0.35, "sweep", 0.2, "gap", 0.8));
        SkillFx.play(level, "item.trident.throw", hand, 0.6, 1.0);
        SkillFx.play(level, "mob.breeze.whirl", sp.position(), 0.7, 0.8);
        SkillFx.play(level, "ambient.weather.thunder", spot, 0.5, 0.4);
        // 예고: 땅의 문양 (그려짐) · 커지는 그림자 · 하늘의 문양 (옥빛) · 솟는 흙먼지 → 하늘이 갈라지는 금빛 줄
        int fallT = (int) Math.min(k.num("fall"), delay - 1);
        for (int t = 0; t < delay; t += 2) {
            final int tt = t;
            Tasks.later(t, () -> {
                if (!ok.getAsBoolean()) return;
                double g = (tt + 2.0) / delay;
                SkillFx.rune(level, spot, Opt.of("size", radius * 0.95, "life", 0.13, "progress", Math.min(1, g * 1.2), "spin", tt * 9.0, "color", glow,
                        "alpha", 0.9));
                SkillFx.fx(level, "rpg:tele_disc", V.up(spot, 0.05), Opt.of("size", radius * (0.25 + 0.6 * g), "life", 0.13, "color", DEEP,
                        "alpha", 0.55 + 0.3 * g));
                if (tt % 4 == 0) {
                    SkillFx.rune(level, V.up(spot, sky), Opt.of("size", radius * 1.1, "life", 0.25, "spin", -tt * 6.0, "color", SKY, "alpha", 0.75,
                            "rate", 30.0));
                    double a = Kit.rand() * Math.PI * 2;
                    double rr = radius * (0.5 + Kit.rand() * 0.45);
                    SkillFx.fx(level, "rpg:arrow_spark", new Vec3(spot.x + Math.cos(a) * rr, spot.y + 0.2, spot.z + Math.sin(a) * rr),
                            Opt.of("dir", V.UP, "speed", 3.0, "size", 0.14, "life", 0.4, "color", DUST, "alpha", 1.0));
                }
                if (tt == 8 && sky > 3) {
                    Vec3 sc = V.up(spot, sky);
                    SkillFx.beam(level, V.add(sc, right, -radius * 1.3), V.add(sc, right, radius * 1.3), glow,
                            Opt.of("half", radius * 1.3, "thick", 0.35, "life", 0.6, "sweep", 0.25, "gap", 1.0, "alpha", 0.9));
                    SkillFx.beam(level, V.add(sc, right, -radius * 1.2), V.add(sc, right, radius * 1.2), core,
                            Opt.of("half", radius * 1.2, "thick", 0.1, "life", 0.5, "sweep", 0.25, "gap", 1.0, "alpha", 1.0));
                    SkillFx.play(level, "block.bell.hit", spot, 0.5, 0.6);
                }
            });
        }
        // 낙하: 하늘에서 거대한 도끼가 fallT 틱 동안 내려오며 내리찍는 자세로 돎 (뒤에 금빛 줄기)
        Tasks.later(delay - fallT, () -> {
            if (!ok.getAsBoolean()) return;
            double secs = fallT / 20.0;
            axe(level, V.up(center, drop), new Vec3(0, -drop / secs, 0), size, secs, CHOP + 40, -40 / secs, 0);
            if (drop > 2) {
                SkillFx.beam(level, V.up(spot, drop + size * 0.6), V.up(spot, size * 0.5), glow, Opt.of("half", 1.2, "thick", 1.4, "life", 0.3,
                        "sweep", secs, "gap", 2.0, "alpha", 0.5));
            }
            SkillFx.play(level, "item.trident.riptide_3", spot, 0.5, 1.0);
        });
        // 띄운 적 (밀려나도 여진이 따라감)
        Map<UUID, LivingEntity> struck = new LinkedHashMap<>();
        // 회복: 준 피해의 heal 만큼 — 모아서 다음 틱에 한 번 (숫자가 적마다 뜨지 않게), 한 번 쓸 때 실제로 회복한 양이 최대 체력의 healCap 까지
        Heal heal = new Heal(RpgHealth.maxOf(sp) * k.numOr("healCap", 1));
        double healRate = k.num("heal");
        DoubleConsumer addHeal = got -> {
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
        int stay = (int) k.num("stay");
        int fade = (int) k.num("fade");
        // 내리찍음: 땅이 갈라짐
        Tasks.later(delay, () -> {
            if (!ok.getAsBoolean()) return;
            landed[0] = true;
            axe(level, center, V.ZERO, size, (stay + fade) / 20.0, CHOP, 0, fade / (double) (stay + fade));
            SkillFx.burst(level, V.up(spot, 0.8), core, 4.5, 0.4);
            SkillFx.burst(level, V.up(spot, 1.6), glow, 3.2, 0.35);
            SkillFx.shock(level, spot, glow, radius / 2.1, 0.55);
            SkillFx.shock(level, spot, core, radius / 4.2, 0.4);
            SkillFx.rune(level, spot, Opt.of("size", radius * 0.95, "life", 0.8, "color", glow, "alpha", 0.9, "grow", 0.12, "rate", -80.0));
            SkillFx.fx(level, "rpg:ground_crack", V.up(spot, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", radius * 1.5, "life", 1.8,
                    "color", DEEP, "alpha", 0.9));
            double a0 = Kit.rand() * Math.PI * 2;
            for (int j = 0; j < 8; j++) crackLine(sp, spot, a0 + (j / 8.0) * Math.PI * 2 + Kit.rand() * 0.3, radius, glow, core);
            RuneKit.rockBurst(level, V.up(spot, 0.3), 10, 8, 0.3);
            SkillFx.sparks(level, V.up(spot, 1.0), DUST, 14, 8);
            SkillFx.sparks(level, V.up(spot, 0.6), glow, 8, 6);
            SkillFx.play(level, "mace.heavy_smash_ground", spot, 0.7, 1.0);
            SkillFx.play(level, "random.explode", spot, 0.7, 0.8);
            SkillFx.play(level, "dig.stone", spot, 0.5, 1.0);
            SkillFx.play(level, "ambient.weather.thunder", spot, 0.6, 0.7);
            Kit.shakeNear(level, spot, 14, 0.45, 0.4);
            long t0 = Tasks.now();
            Vec3 eye = V.up(spot, 1.2);
            for (LivingEntity e : Kit.visibleFrom(sp, eye, Kit.around(sp, spot, radius))) {
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, t0), ok, () -> {
                    double got = Kit.skillHit(sp, e, w, k.num("mult"));
                    if (got <= 0) return;
                    struck.put(e.getUUID(), e);
                    addHeal.accept(got);
                    SkillFx.burst(level, Kit.bodyAt0(e, eye), core, 0.9, 0.25);
                    Vec3 d = V.flat(V.sub(e.position(), spot));
                    Kit.knockback(e, d.x * 0.3, d.z * 0.3, 0.6);
                });
            }
        });
        // 여진: 박힌 도끼 둘레에서 땅이 다시 울리고 적의 발밑에서 바위 가시가 솟음 (마법 피해 — 예전 궁니르 번개와 같은 규칙)
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
                    LivingEntity e = list.get(j);
                    RuneKit.rockSpike(sp, level, e.position(), 0.9 + Kit.rand() * 0.3, glow, Opt.of("rock", RuneKit.ROCK, "life", 0.8, "grow", 0.25, "full", 3.0));
                }
                if (list.isEmpty()) {
                    for (int j = 0; j < 3; j++) {
                        Vec3 p = V.add(spot, V.turnY(new Vec3(1, 0, 0), Kit.rand() * 360), 2 + Kit.rand() * 3);
                        RuneKit.rockSpike(sp, level, p, 0.7 + Kit.rand() * 0.3, glow, Opt.of("rock", RuneKit.ROCK, "life", 0.8, "grow", 0.25, "full", 3.0));
                    }
                }
                SkillFx.burst(level, head, core, 1.6, 0.25);
                SkillFx.shock(level, spot, glow, 1.4 + 0.4 * ii, 0.35);
                RuneKit.rockBurst(level, V.up(spot, 0.3), 5, 5, 0.22);
                SkillFx.play(level, "mace.smash_ground", spot, 0.8 + 0.1 * ii, 0.8);
                SkillFx.play(level, "dig.stone", spot, 0.6, 0.8);
                for (LivingEntity e : list) {
                    Kit.hitAt(sp, e, Kit.reserveHit(sp, e, t0), ok, () -> {
                        double got = Kit.skillHit(sp, e, w, k.num("pulseMult"), Opt.of("cause", Kit.Cause.MAGIC));
                        if (got <= 0) return;
                        addHeal.accept(got);
                        SkillFx.burst(level, Kit.bodyAt0(e, head), glow, 0.7, 0.2);
                    });
                }
            });
        }
        // 빛으로 흩어짐 (박힌 도끼만 — 내리찍기 전에 끊겼으면 없음. 박힌 뒤에는 쓴 사람이 쓰러져도 도끼는 흩어짐 — 그림이 stay 뒤 fade 동안 흐려짐)
        Tasks.later(delay + stay, () -> {
            if (!landed[0]) return;
            for (int j = 0; j < 8; j++) {
                SkillFx.fx(level, "rpg:arrow_spark", V.add(V.up(center, (Kit.rand() - 0.5) * size * 0.5), right, (Kit.rand() - 0.5) * size),
                        Opt.of("dir", V.UP, "speed", 2 + Kit.rand() * 2, "size", 0.18, "life", 0.6, "color", core, "alpha", 1.0));
            }
            SkillFx.play(level, "beacon.deactivate", spot, 1.4, 0.6);
        });
        return true;
    }
}
