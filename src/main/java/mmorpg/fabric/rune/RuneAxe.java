package mmorpg.fabric.rune;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.RpgStats;
import mmorpg.fabric.skill.Guards;
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
import org.joml.Quaternionf;

/** 도끼 룬 (runes.js "도끼" — 핏빛: 힘 1.0 + 체력 0.5, 무겁게 찍고 피를 빨고 버팀): 회전 도끼 · 피의 도끼질 · 도약 찍기 · 처형 · 광전사의 혼 */
final class RuneAxe {
    private RuneAxe() {}

    /** 피 (핏빛 초승달 · 흡혈 · 처형 · 광폭화) */
    private static final double[] BLOOD = {0.85, 0.06, 0.08};
    /** 도끼 그림 칸 {빛, 심지} (rpg:rfx_prop) */
    private static final int[] AXE = RuneKit.RFX.get("axe");

    static void register() {
        RuneCast.register("axeBoomerang", RuneAxe::axeBoomerang);
        RuneCast.register("bloodCleave", RuneAxe::bloodCleave);
        RuneCast.register("leapSlam", RuneAxe::leapSlam);
        RuneCast.register("execute", RuneAxe::execute);
        RuneCast.register("berserker", RuneAxe::berserker);
    }

    // ------------------------------------------------------------ 2026-11-11 Vfx (모드 없는 클라이언트도 보는 입체 도끼 + 2D 연출)

    /** 도끼 모델 (사용자 무기 디자인 — 그 등급의 도끼: rpg:vfx/axe_<등급>) */
    static String axeModel(Runes.Rune r) {
        return "rpg:vfx/axe_" + r.grade();
    }

    /** 도끼 모델 축 (tools/weapon_art): 자루 = 모델 +Y (자루 끝 → 머리), 날 = 모델 −Z, 넓은 면 = ±X. handle · edge (자루와 직교하게 맞춤) 로 가는 회전 */
    static Quaternionf axeRot(Vec3 handle, Vec3 edge) {
        Vec3 y = V.unit(handle);
        Vec3 e = V.unit(V.sub(edge, V.scale(y, V.dot(edge, y))));
        Vec3 z = V.scale(e, -1);
        Vec3 x = V.unit(V.cross(y, z));
        return Vfx.basis(x, y, z);
    }

    /** 등급마다 도끼 모델의 쥔 곳 · 머리 가운데 (모델 픽셀) — tools/weapon_art 의 자루 (x 8 · z) 와 날 */
    static double[] gripOf(String grade) {
        return switch (grade) {
            case "common" -> new double[] {8, 3.7, 10.25};
            case "rare" -> new double[] {8, 3.7, 10.0};
            case "noble" -> new double[] {8, 3.75, 9.55};
            case "hero" -> new double[] {8, 3.4, 8.45};
            default -> new double[] {8, 6.2, 11.0};
        };
    }

    /** 쥔 곳에서 머리 가운데까지 (모델 픽셀 — 자루 방향) */
    static double headReach(String grade) {
        return switch (grade) {
            case "common", "rare" -> 10.8;
            case "noble" -> 9.8;
            case "hero" -> 10.6;
            default -> 14.0;
        };
    }

    /** 1인칭 눈 가까이 (1.3칸 안) 를 지나는 소품은 쓴 사람에게 숨김 — 화면을 덮지 않게 */
    static boolean nearEye(ServerPlayer sp, Vec3 p, double r) {
        return V.len(V.sub(p, sp.getEyePosition())) < r;
    }

    /**
     * 회전 도끼: 도끼를 던져 range 칸 (벽 앞까지) 날아가 hover 틱 제자리에서 돌다가 돌아옴. 갈 때 · 올 때 선 가까이 width 칸의 적을 한 번씩.
     * 2026-11-11 Vfx: 일반 등급 도끼 (사용자 디자인 3D) 가 던진 쪽으로 날아가며 톱날처럼 돎 (원반 면이 던진 사람 쪽을 봄 — 1인칭에서 날이 보임) ·
     * 원반의 흐린 빛 · 지나간 자리의 빛 → 끝에서 머물며 둘레를 휩쓰는 초승달 · 땅의 고리 → 돌아와 손에 잡힘 (번쩍). 맞은 적마다 X 베기 · 피 · 불꽃,
     * 처음 맞은 적 발밑에 터짐 · 고리 · 갈라진 땅
     */
    static boolean axeBoomerang(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 f = V.flat(sp.getViewVector(1F));
        Vec3 from = V.up(V.add(sp.position(), f, 0.6), 1.2);
        double len = Math.max(1.5, RuneKit.rayLength(level, from, f, k.num("range")));
        Vec3 tip = V.add(from, f, len);
        double[][] cs = RuneKit.colorOf("axe");
        double[] glow = cs[0];
        double[] core = cs[1];
        double speed = k.num("speed");
        double width = k.num("width");
        int out = Math.max(1, (int) Math.round(len / speed * 20));
        int back0 = out + (int) k.num("hover"); // 돌아오기 시작하는 틱
        SkillFx.play(level, "item.trident.throw", sp.position(), 0.7, 1);
        SkillFx.play(level, "mob.breeze.whirl", sp.position(), 1.3, 0.6);
        Vec3 side = V.rightOf(f);
        // 도는 면: 앞으로 날며 던진 사람 쪽 (뒤 · 위) 을 봄 → 1인칭 · 3인칭에서 톱날처럼 보임
        Vec3 n = V.unit(V.add(V.scale(f, -0.55), V.UP, 0.83));
        Vec3 a = side;
        Vec3 b = V.unit(V.cross(n, side));
        double[] g = gripOf(r.grade());
        double reach = headReach(r.grade()) / 16.0;
        Vfx.Fx fx = Vfx.at(level, from, back0 + 60);
        // 던지는 손의 초승달 (오른쪽 → 왼쪽, 눈 앞 1.3칸 · 조금 아래 — 1인칭 화면 아래쪽)
        VfxKit.slash(fx, V.up(V.add(sp.getEyePosition(), f, 1.4), -0.45), V.scale(side, -1), V.unit(V.add(V.scale(f, -0.7), V.UP, 0.7)), 2.4, glow, 0);
        // 무게 중심 (자루와 머리 사이) 둘레로 돎
        Vfx.Prop axe = fx.prop(axeModel(r), V.add(sp.getEyePosition(), f, 0.9)).grip(g[0], g[1] + reach * 16 * 0.62, (g[2] + 7.5) / 2).bright();
        axe.size(0.7, 0);
        Vfx.Sprite disc = fx.sprite("spin", from).color(glow).alpha(0.5).fixed(Vfx.basis(a, b, n)).size(0.1);
        Vec3[] last = {from};
        Vec3[] home = {null};
        int[] backTicks = {1};
        String life = RuneKit.lifeToken(sp);
        fx.each(0, back0 + 80, 1, t -> {
            Vec3 pos;
            double sc = Math.min(1.75, 0.7 + t * 0.26);
            if (t <= out) pos = V.add(from, f, len * t / out);
            else if (t <= back0) pos = V.up(tip, Math.sin((t - out) * 0.5) * 0.12);
            else {
                if (home[0] == null) {
                    if (sp.isRemoved() || !RuneKit.sameLife(sp, life)) {
                        fx.end();
                        return;
                    }
                    // 돌아오는 길: 지금 몸 (가슴 · 오른쪽 아래 — 1인칭 눈 앞을 지나지 않게)
                    home[0] = V.add(V.up(sp.position(), 1.0), V.rightOf(Kit.facingOf(sp)), 0.45);
                    backTicks[0] = Math.max(1, (int) Math.round(V.len(V.sub(home[0], tip)) / speed * 20));
                }
                int tt = t - back0;
                if (tt > backTicks[0]) {
                    axe.remove();
                    disc.remove();
                    return;
                }
                pos = V.add(tip, V.sub(home[0], tip), (double) tt / backTicks[0]);
                if (tt >= backTicks[0] - 2) sc = 0.8;
            }
            double th = Math.toRadians(t * 74.0);
            Vec3 h = V.add(V.scale(a, Math.cos(th)), b, Math.sin(th));
            Vec3 e = V.add(V.scale(a, -Math.sin(th)), b, Math.cos(th));
            axe.to(pos, axeRot(h, e), sc, 1);
            disc.to(pos, (t > out && t <= back0 ? 2.9 : 2.3) * sc / 1.75, 1).roll(-t * 47.0, 1);
            // 지나간 자리의 빛 (2틱마다 — 옅어짐)
            if (t % 2 == 0 && V.len(V.sub(pos, last[0])) > 0.4) {
                VfxKit.glow(fx, pos, 1.1, glow, 0.55, 6);
                last[0] = pos;
            }
            if (t % 4 == 0) VfxKit.sparks(fx, pos, core, 2, 0.8, 0);
        });
        // 끝에서 머묾: 둘레를 휩쓰는 초승달 둘 · 아래 땅의 고리
        fx.on(out, () -> {
            for (int i = 0; i < 2; i++) {
                Vec3 sw = V.turnY(f, 90 + i * 180);
                VfxKit.slash(fx, V.add(tip, sw, 0.3), V.turnY(sw, 90), V.unit(V.add(V.UP, f, -0.6)), 3.2, i == 0 ? glow : BLOOD, i * 3);
            }
            Vec3 gnd = VfxKit.ground(level, tip);
            if (tip.y - gnd.y < 3.5) {
                VfxKit.ring(fx, gnd, 2.6, glow, 0, 10);
                VfxKit.dust(fx, gnd, 1.2, 3, VfxKit.DUST, 0);
            }
        });
        // 처음 맞은 적의 발밑 터짐 (갈 때 · 올 때 통틀어 한 번)
        boolean[] boomed = {false};
        java.util.function.Consumer<LivingEntity> boom = hitE -> {
            if (boomed[0]) return;
            boomed[0] = true;
            Vec3 feet = VfxKit.ground(level, hitE.position());
            VfxKit.ring(fx, feet, 2.2, glow, 0, 8);
            VfxKit.crack(fx, feet, 2.6, BLOOD, 0, 30);
        };
        java.util.function.BiConsumer<LivingEntity, Vec3> hitFx = (e, dir) -> {
            Vec3 body = Kit.bodyPoint(e);
            VfxKit.xcut(fx, body, 1.5, glow, 0);
            VfxKit.blood(fx, body, 0.7, 2, BLOOD, 0);
            VfxKit.sparks(fx, body, core, 5, 1.2, 0);
            boom.accept(e);
        };
        Map<UUID, Integer> outAt = new HashMap<>();
        for (Kit.LineHit h : RuneKit.lineTargets(sp, from, f, len, width)) {
            LivingEntity e = h.e();
            int when = (int) Math.round(h.t() / speed * 20);
            outAt.put(e.getUUID(), when);
            RuneKit.later(when, () -> {
                if (!sp.isRemoved() && !e.isRemoved() && RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) hitFx.accept(e, f);
            });
        }
        RuneKit.later(back0, () -> {
            if (sp.isRemoved()) return;
            Vec3 homeNow = V.up(sp.position(), 1.2);
            Vec3 d = V.sub(homeNow, tip);
            double len2 = V.len(d);
            Vec3 u = V.unit(d);
            int backT = Math.max(1, (int) Math.round(len2 / speed * 20));
            SkillFx.play(level, "mob.breeze.whirl", tip, 1.0, 0.7);
            for (Kit.LineHit h : RuneKit.lineTargets(sp, tip, u, len2, width)) {
                LivingEntity e = h.e();
                Integer first = outAt.get(e.getUUID());
                int when = (int) Math.round(h.t() / speed * 20);
                // 갈 때 맞은 적은 그때부터 10틱 뒤 (몹 무적 시간)
                int wait = first == null ? when : Math.max(when, first + Kit.IFRAMES - back0);
                RuneKit.later(wait, () -> {
                    if (!sp.isRemoved() && !e.isRemoved() && RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) hitFx.accept(e, u);
                });
            }
            RuneKit.later(backT, () -> {
                if (sp.isRemoved()) return;
                SkillFx.play(level, "item.trident.return", sp.position(), 0.9, 1);
                // 잡음: 손 앞 번쩍 (1인칭 화면 오른쪽 아래)
                Vec3 hand = V.add(V.up(sp.position(), 1.0), V.rightOf(Kit.facingOf(sp)), 0.45);
                VfxKit.burst(fx, V.add(hand, Kit.facingOf(sp), 0.5), 1.0, core, 0);
                // 0.49.0 분노: 돌아온 도끼를 받으면 buffTicks 동안 가하는 피해 + buff (몸에 핏빛 기운 · 상태 줄)
                if (k.numOr("buff", 0) > 0) {
                    RuneMarks.empower(sp, k.num("buff"), (int) k.num("buffTicks"), "분노", BLOOD, glow);
                    SkillFx.play(level, "mob.ravager.roar", sp.position(), 1.4, 0.45);
                    for (int i = 0; i < 3; i++) {
                        Vec3 p = V.add(sp.position(), V.turnY(Kit.facingOf(sp), 120 * i + 60), 0.7);
                        fx.sprite("aura", V.up(p, 0.9)).color(i == 1 ? glow : BLOOD).size(1.8).alpha(0.8).play(fx.age(), 0, 8, 2, false).fade(fx.age() + 12, 5);
                    }
                }
            });
        });
        return true;
    }

    /**
     * 피의 도끼질: 앞을 찍고 맞힌 적마다 체력 회복 — 거대한 도끼 (rpg:rfx_prop 도끼) 가 오른쪽 위에서 왼쪽 아래로 휘둘러지고,
     * 맞은 적에게 핏빛 초승달 (rpg:rfx_wave) · 피가 나에게로 흘러듦
     */
    static boolean bloodCleave(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 head = sp.getEyePosition();
        Vec3 f = V.flat(sp.getViewVector(1F));
        double[][] cs = RuneKit.colorOf("axe");
        double[] glow = cs[0];
        double[] core = cs[1];
        Vec3 side = V.rightOf(f);
        // 휘두르는 도끼 (3틱): 오른쪽 위 → 가운데 → 왼쪽 아래, 날이 도는 쪽으로 기움 (그림은 날이 위)
        for (int t = 0; t < 3; t++) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (sp.isRemoved()) return;
                double q = tt / 2.0;
                Vec3 at = V.up(V.add(V.add(sp.position(), f, 2.1), side, 1.3 - 2.6 * q), 2.5 - 1.9 * q);
                double sz = 0.95 * RuneKit.nearK(sp, at, 3, 0.55);
                RuneKit.rfx2(level, "prop", at, Opt.of("size", sz, "life", tt == 2 ? 0.2 : 0.1, "spin", -40.0 - 60 * tt, "fade", tt == 2 ? 0.6 : 0.0,
                        "alpha", 0.95), glow, core, AXE, null);
            });
        }
        RuneKit.arc(level, V.up(V.add(head, f, 1.6), -0.1), f, Opt.of("shape", "heavy", "flip", false, "size", 2.4, "life", 0.26, "speed", 2.0, "spin", -80.0,
                "color", glow, "aura", core));
        RuneKit.later(2, () -> RuneKit.arc(level, V.up(V.add(head, f, 1.9), -0.4), f, Opt.of("shape", "heavy", "flip", true, "size", 2.0, "life", 0.22, "speed",
                2.0, "spin", -100.0, "color", BLOOD, "core", false, "alpha", 0.7)));
        SkillFx.play(level, "mace.smash_air", c, 0.7, 0.9);
        RuneKit.later(2, () -> {
            if (sp.isRemoved()) return;
            Vec3 hitC = V.add(c, f, 1.8);
            RuneKit.zonePulse(level, hitC, glow, 2.0, 0.35);
            RuneBoost.impact(level, hitC, glow, core, k.num("radius"), r.grade()); // 0.50.0 2D 강화 (아래 등급 터짐): 도끼가 찍힌 앞자리 (부채꼴 가운데)
            SkillFx.fx(level, "rpg:light_cut", V.up(V.add(c, f, 2.0), 0.08), Opt.of("dir", f, "size", 1.6, "thick", 0.42, "life", 0.9, "delay", 0.6, "color", glow,
                    "alpha", 0.95));
            SkillFx.play(level, "mace.smash_ground", hitC, 0.8, 1);
            SkillFx.shake(sp, 0.15, 0.15);
            double healMax = k.num("healMax");
            int n = 0;
            // 가까운 순 (베드락의 거리순 목록처럼 — 아래 "가까운 6명")
            for (LivingEntity e : RuneKit.nearestTo(RuneKit.coneAround(sp, c, f, k.num("radius"), k.num("cone")), c)) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) <= 0) continue;
                n++;
                Vec3 at = Kit.bodyPoint(e);
                if (n <= 12) SkillFx.burst(level, at, BLOOD, 0.8, 0.22); // 번쩍임은 12명까지 (파티클 예산)
                if (n <= 6) {
                    // 가까운 6명: 베인 자국 + 핏빛 초승달 (파티클 예산)
                    Vec3 v = V.unit(V.sub(at, head));
                    SkillFx.cutMark(level, at, v, 70, 0.12, glow, core, 1.0);
                    Vec3 wv = V.add(at, v, -0.3);
                    RuneKit.rfx2(level, "wave", wv, Opt.of("size", 0.65 * RuneKit.nearK(sp, wv, 3, 0.4), "life", 0.24, "spin", -60 + Kit.rand() * 30, "grow", 0.3,
                            "fade", 0.5, "alpha", 0.9), BLOOD, RuneKit.BLOOD_CORE);
                }
                if (n > healMax) continue;
                // 피가 나에게로
                Vec3 to = V.up(sp.position(), 1.0);
                for (int i = 0; i < 3; i++) {
                    Vec3 from = V.add(at, new Vec3((Kit.rand() - 0.5) * 0.6, (Kit.rand() - 0.3) * 0.6, (Kit.rand() - 0.5) * 0.6));
                    Vec3 d = V.sub(to, from);
                    SkillFx.fx(level, "rpg:arrow_spark", from, Opt.of("dir", V.unit(d), "speed", V.len(d) / 0.35, "size", 0.16, "grow", 0.5, "life", 0.35, "color",
                            BLOOD, "alpha", 1.0));
                }
            }
            if (n > 0) {
                double healed = RpgHealth.healBy(sp, RpgHealth.maxOf(sp) * k.num("heal") * Math.min(n, healMax)
                        * Runes.supportScale(RpgStats.effective(sp), r.family()));
                if (healed > 0) {
                    DamageNumbers.show(sp, healed, DamageNumbers.Style.HEAL, "", 0);
                    RuneKit.later(6, () -> {
                        if (!sp.isRemoved()) RuneKit.selfCue(sp, BLOOD, core, false);
                    });
                }
                SkillFx.play(level, "mob.zombie.unfect", c, 0.6, 0.5);
            }
        });
        return true;
    }

    /**
     * 도약 찍기 (0.49.0 — 몸이 포물선으로 날아가는 도약 RuneKit.leap · 둔화 → 방어 깨짐): 조준한 곳 (range 칸, 막히면 벽 앞) 으로 air 틱 동안 뛰어올라
     * 내려찍음 (반지름 radius, 조금 띄움 · 맞은 적 vulnTicks 동안 받는 피해 + vuln).
     * 2026-11-11 Vfx: 뛰어오르는 자리의 흙먼지 고리 → 뛰는 동안 머리 위로 치켜든 고귀 등급 도끼 (사용자 디자인 3D — 몸을 따라감, 머리 뒤 핏빛 빛) →
     * 내려서며 앞으로 내리찍어 날이 땅에 박힘 (2틱) · 위에서 아래로 그어지는 초승달 → 갈라진 땅 · 충격파 고리 둘 · 터짐 · 흙먼지 · 튀는 돌 → 도끼가 땅속으로
     * 가라앉으며 사라짐. 맞은 적마다 피 · 불꽃
     */
    static boolean leapSlam(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 start = sp.position();
        Vec3 land = RuneKit.leapLand(sp, k.num("range"), 1.0);
        Vec3 f = V.hdist(land, start) > 0.3 ? V.flat(V.sub(land, start)) : V.flat(sp.getViewVector(1F));
        double[][] cs = RuneKit.colorOf("axe");
        double[] glow = cs[0];
        double[] core = cs[1];
        int air = (int) k.num("air");
        double radius = k.num("radius");
        double[] g = gripOf(r.grade());
        Vfx.Fx fx = Vfx.at(level, start, air + 70);
        // 뛰어오르는 자리: 흙먼지 고리 · 먼지
        VfxKit.ring(fx, start, 1.8, VfxKit.DUST, 0, 8);
        VfxKit.dust(fx, start, 0.9, 4, VfxKit.DUST, 0);
        // 머리 위로 치켜든 도끼 (자루가 뒤로 젖혀짐 · 날이 앞) — 몸을 따라감 (틱마다)
        Vec3 up0 = V.unit(V.add(V.UP, f, -0.55));
        Vec3 edge0 = V.unit(V.add(f, V.UP, 0.55));
        Vfx.Prop axe = fx.prop(axeModel(r), V.add(V.up(start, 1.5), f, -0.2)).grip(g[0], g[1], g[2]).bright();
        axe.to(null, axeRot(up0, edge0), 1.7, 0);
        Vfx.Sprite aura = fx.sprite("glow", V.up(start, 2.6)).color(BLOOD).size(2.2).alpha(0.55);
        double reach = headReach(r.grade()) / 16.0 * 1.7;
        fx.each(1, air, 1, t -> {
            if (sp.isRemoved()) return;
            Vec3 grip = V.add(V.up(sp.position(), 1.45), f, -0.15);
            axe.to(grip, null, -1, 1);
            aura.to(V.add(grip, up0, reach), -1, 1);
            if (t % 2 == 0) VfxKit.glow(fx, V.add(grip, up0, reach), 1.2, glow, 0.45, 5);
        });
        SkillFx.play(level, "mob.enderdragon.flap", start, 1.2, 0.7);
        RuneKit.leap(sp, land, 2.0 + V.hdist(land, start) * 0.15, air, glow, core, () -> {
            Vec3 c = sp.position();
            Vec3 hit = VfxKit.ground(level, V.add(c, f, 1.8));
            // 내리찍음: 2틱에 자루가 머리 위 → 앞 아래 (날이 땅에 박힘)
            Vec3 grip = V.add(V.up(c, 1.15), f, 0.35);
            Vec3 down = V.unit(V.sub(V.up(hit, 0.15), grip));
            Vec3 mid = V.unit(V.add(V.add(up0, down), f, 0.6));
            Vec3 edgeMid = V.unit(V.cross(mid, V.rightOf(f))); // 날 = 자루 × 오른쪽 (휘두르는 쪽)
            Vec3 edgeEnd = V.unit(V.cross(down, V.rightOf(f)));
            double sc = Math.max(1.7, (V.len(V.sub(V.up(hit, 0.15), grip)) + 0.25) / (headReach(r.grade()) / 16.0));
            axe.to(grip, axeRot(mid, edgeMid), sc * 0.95, 1);
            fx.on(fx.age() + 1, () -> axe.to(grip, axeRot(down, edgeEnd), sc, 1));
            aura.remove();
            // 위에서 아래로 그어지는 초승달 (보는 사람 쪽 — 뒤를 봄)
            VfxKit.slash(fx, V.add(V.up(c, 1.4), f, 1.3), V.DOWN, V.scale(f, -1), 3.8, glow, 0);
            VfxKit.slash(fx, V.add(V.up(c, 1.3), f, 1.4), V.DOWN, V.scale(f, -1), 3.2, BLOOD, 1);
            // 박힘 (2틱 뒤): 갈라진 땅 · 고리 둘 · 터짐 · 번쩍 · 흙먼지 · 튀는 돌 · 불꽃
            VfxKit.crack(fx, hit, radius * 2.2, BLOOD, 2, 40);
            VfxKit.ring(fx, hit, radius, glow, 2, 10);
            VfxKit.ring(fx, hit, radius * 0.6, core, 4, 8);
            VfxKit.burst(fx, V.up(hit, 0.5), 3.2, glow, 2);
            VfxKit.dust(fx, hit, radius * 0.8, 7, VfxKit.DUST, 3);
            VfxKit.rocks(fx, hit, 8, 6.5, 0.42, new String[] {"minecraft:cobbled_deepslate", "minecraft:deepslate", "minecraft:tuff", "minecraft:cobblestone"}, 2);
            VfxKit.sparks(fx, V.up(hit, 0.4), core, 8, 2.0, 2);
            // 박힌 도끼: 14틱 뒤 땅속으로 가라앉으며 사라짐
            fx.on(fx.age() + 16, () -> axe.to(V.up(grip, -0.7), null, sc * 0.4, 8));
            fx.on(fx.age() + 25, axe::remove);
            SkillFx.play(level, "mace.heavy_smash_ground", c, 0.8, 1);
            SkillFx.play(level, "random.explode", c, 0.9, 0.5);
            int marks = 0;
            for (LivingEntity e : Kit.around(sp, c, radius, 3)) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                    RuneKit.knock(e, V.sub(e.position(), c), 0.4, k.num("lift"));
                    RuneMarks.vuln(e, k.numOr("vuln", 0), (int) k.numOr("vulnTicks", 0), BLOOD, core, marks++ < 8);
                    if (marks <= 8) {
                        VfxKit.blood(fx, Kit.bodyPoint(e), 0.7, 2, BLOOD, 2);
                        VfxKit.sparks(fx, Kit.bodyPoint(e), core, 3, 1.0, 2);
                    }
                }
            }
        }, false);
        return true;
    }

    /**
     * 처형: 앞 range 칸의 대상 위로 거대한 도끼가 떠올라 돌다가 (delay) 내려찍음. 체력이 below 이하면 bonus 배.
     * 2026-11-11 Vfx: 대상 발밑에 도는 핏빛 칼날 고리 → 대상 위에 영웅 등급 도끼 (사용자 디자인 3D) 가 나타나 커지며 넓은 면이 나를 본 채 한 바퀴 돌아
     * 날이 아래로 → 단두대처럼 내리꽂힘 (2틱) → X 베기 · 터짐 · 번쩍 · 고리 · 갈라진 땅 · 불꽃 · 피 (처형이면 핏빛 X 둘 · 큰 피 터짐) → 박힌 도끼가 가라앉음
     */
    static boolean execute(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        LivingEntity target = Kit.pickTarget(sp, k.num("range"), k.num("cone"));
        if (target == null) {
            Notify.warn(sp, "§c" + k.str("name") + ": §7앞쪽 " + k.text("range") + "칸 안에 대상이 없습니다");
            return false;
        }
        ServerLevel level = sp.level();
        Vec3 f = V.flat(V.sub(target.position(), sp.position()));
        Vec3 side = V.rightOf(f);
        double[][] cs = RuneKit.colorOf("axe");
        double[] glow = cs[0];
        double[] core = cs[1];
        int delay = (int) k.num("delay");
        double top = target.getBbHeight();
        Vfx.Fx fx = Vfx.at(level, target.position(), delay + 60);
        // 발밑 칼날 고리 (돎 — 대상을 따라감)
        Vfx.Sprite sig = fx.sprite("bladering", V.up(VfxKit.ground(level, target.position()), 0.07)).color(BLOOD).fixed(Vfx.flat(0)).size(2.2).alpha(0.9);
        sig.play(0, 0, 8, 2, true);
        sig.to(null, 3.6, 4);
        // 떠오르는 도끼: 머리 가운데를 축으로 넓은 면 (모델 ±X) 이 나 (−f) 를 봄 — 자루가 위에서 한 바퀴 돌아 오른쪽 수평 · 날이 아래로
        Vfx.Prop axe = fx.prop(axeModel(r), V.up(target.position(), top + 2.4)).grip(8, 14, 8).bright();
        axe.to(null, axeRot(V.UP, f), 0.5, 0);
        Vfx.Sprite halo = fx.sprite("glow", V.up(target.position(), top + 2.4)).color(BLOOD).size(1.2).alpha(0.6);
        fx.each(1, delay, 1, t -> {
            if (target.isRemoved()) return;
            double u = t / (double) delay;
            Vec3 at = V.up(target.position(), top + 2.4 + u * 1.6);
            double th = Math.toRadians(270 * u * u * (3 - 2 * u)); // 자루: 위 → 왼쪽 → 아래 → 오른쪽 수평 (한 바퀴의 3/4) — 날은 그때 아래
            Vec3 h = V.add(V.scale(V.UP, Math.cos(th)), side, -Math.sin(th));
            Vec3 e = V.cross(h, V.scale(f, -1)); // 날: 자루를 돌린 쪽 (넓은 면은 늘 나를 봄)
            axe.to(at, axeRot(h, e), 0.5 + 1.9 * u, 1);
            halo.to(at, 1.2 + 2.2 * u, 1);
            sig.to(V.up(VfxKit.ground(level, target.position()), 0.07), -1, 1);
            if (t % 2 == 0) VfxKit.sparks(fx, at, glow, 2, 1.4, 0);
        });
        SkillFx.play(level, "mob.ravager.roar", sp.position(), 0.9, 0.8);
        SkillFx.play(level, "mob.evocation_illager.prepare_attack", target.position(), 0.6, 0.8);
        RuneKit.later(delay, () -> {
            if (sp.isRemoved() || target.isRemoved()) {
                fx.end();
                return;
            }
            boolean low = target.getHealth() <= target.getMaxHealth() * k.num("below");
            Vec3 at = Kit.bodyPoint(target);
            Vec3 g = VfxKit.ground(level, target.position());
            double[] hitColor = low ? BLOOD : glow;
            // 내리꽂힘 (2틱): 날이 아래 · 자루가 오른쪽 수평
            Quaternionf chop = axeRot(side, V.DOWN);
            axe.to(V.up(g, 1.0), chop, 2.4, 2);
            halo.remove();
            sig.fade(fx.age() + 2, 6);
            VfxKit.slash(fx, V.up(at, 0.6), V.DOWN, V.scale(f, -1), 3.6, hitColor, 0);
            VfxKit.xcut(fx, at, low ? 3.0 : 2.4, hitColor, 2);
            VfxKit.burst(fx, at, low ? 3.6 : 2.8, hitColor, 2);
            VfxKit.ring(fx, g, 2.6, glow, 2, 10);
            VfxKit.crack(fx, g, 4.2, BLOOD, 2, 40);
            VfxKit.sparks(fx, at, core, 12, 2.2, 2);
            VfxKit.dust(fx, g, 1.4, 5, VfxKit.DUST, 3);
            VfxKit.blood(fx, at, low ? 1.4 : 0.9, low ? 5 : 2, BLOOD, 2);
            if (low) {
                VfxKit.xcut(fx, V.add(at, f, -0.2), 3.4, BLOOD, 4);
                fx.on(fx.age() + 3, () -> fx.sprite("burst2", at).color(BLOOD).size(2.0).play(fx.age(), 0, 8, 1, false).to(null, 4.4, 7));
            }
            // 박힌 도끼: 땅속으로 가라앉으며 사라짐
            fx.on(fx.age() + 18, () -> axe.to(V.up(g, 0.2), null, 1.0, 8));
            fx.on(fx.age() + 27, axe::remove);
            SkillFx.play(level, "mace.heavy_smash_ground", g, 0.7, 1);
            SkillFx.play(level, "game.player.attack.critical", at, 0.7, 1);
            double got = RuneKit.runeHit(sp, target, P, k.num("mult") * (low ? k.num("bonus") : 1), r.stat());
            if (got > 0) RuneKit.knock(target, f, 0.3, 0.15);
            if (got > 0 && low) {
                SkillFx.play(level, "item.trident.thunder", at, 0.5, 0.5);
                Notify.notify(sp, "§4§l처형! §r§7체력 " + Js.str(Js.round(k.num("below") * 100)) + "% 이하 ×" + k.text("bonus"), 24);
            }
        });
        return true;
    }

    /** 광전사의 혼이 죽음으로 끝남: 핏빛 하늘을 걷음 (다시 태어난 사람은 새 엔티티라 uuid 로 지금 엔티티를 찾음) */
    private static void endBloodSky(ServerPlayer sp, ServerLevel level) {
        ServerPlayer cur = RuneKit.player(level, sp.getUUID());
        Ult.endSky(List.of(cur != null ? cur : sp), RuneKit.ultOf("berserker").strOrNull("fog"));
    }

    /**
     * 광전사의 혼: hits × gap 틱 동안 광폭화 (받는 피해 -reduce). gap 틱마다 주위 radius 칸을 난타, 맞힌 적마다 최대 체력 × heal 회복 (healMax 명까지).
     * 죽으면 끝 (차원 이동으로는 끝나지 않음). 초월: 핏빛 하늘 · 번쩍임 (모드 클라이언트 — 화면 색).
     * 2026-11-11 Vfx: 광폭화 순간 핏빛 터짐 · 번쩍 · 고리 둘 · 솟는 핏빛 불기둥 · 발밑의 거대한 칼날 고리 (광폭화 동안 몸을 따라 돎) · 몸의 핏빛 불꽃 셋 →
     * 초월 도끼 둘 (사용자 디자인 3D — 머리가 바깥, 날이 도는 쪽) 이 허리 높이에서 몸 둘레를 천천히 돌다가 난타마다 반 바퀴를 휩씀 · 휩쓴 자리의 초승달 ·
     * 발밑 고리 · 맞은 적마다 X 베기 · 피 → 마지막 난타는 큰 핏빛 터짐. 죽으면 모두 사라짐
     */
    static boolean berserker(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int total = hits * gap;
        double radius = k.num("radius");
        double[][] cs = RuneKit.colorOf("axe");
        double[] glow = cs[0];
        double[] core = cs[1];
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Guards.set(sp, total, k.num("reduce"), null, null);
        if (k.truthy("steadfast")) mmorpg.fabric.RpgCombat.setSteadfast(sp, total); // 8단계 3돌: 광폭화 동안 밀려나지 않음
        SkillFx.play(level0, "mob.ravager.roar", c0, 0.7, 1);
        SkillFx.play(level0, "mob.enderdragon.growl", c0, 1.4, 0.4);
        // 초월: 핏빛 하늘 (광폭화 동안 — 죽으면 걷힘) · 검붉은 번쩍임 (모드 클라이언트의 화면 색)
        RuneKit.ultSkyFor(sp, level0, c0, "berserker", total + 10, null);
        RuneKit.ultFlashFor(sp, level0, c0, "berserker", "flash", Opt.empty());
        int alive = RuneKit.deathToken(sp); // 죽으면 끝 (난타 · 흡혈 · 기운 — 차원 이동 · 탈것 부르기로는 끝나지 않음)
        Vfx.Fx fx = Vfx.at(level0, c0, total + 30);
        // 광폭화 순간
        VfxKit.burst(fx, V.up(c0, 1.1), 3.4, BLOOD, 0);
        VfxKit.ring(fx, c0, radius, BLOOD, 0, 12);
        VfxKit.ring(fx, c0, radius * 1.35, glow, 3, 12);
        fx.sprite("cinder", V.up(c0, 1.9)).color(BLOOD).size(4.6).alpha(0.9).play(0, 0, 8, 2, false);
        Vfx.Sprite sigil = fx.sprite("bladering", V.up(c0, 0.07)).color(BLOOD).fixed(Vfx.flat(0)).size(4.0).alpha(0.85);
        sigil.play(0, 0, 8, 2, true);
        sigil.to(null, radius * 2.3, 6);
        // 몸의 핏빛 불꽃 셋 (양옆 · 뒤 — 1인칭 화면을 덮지 않게 앞은 비움)
        Vfx.Sprite[] flames = new Vfx.Sprite[3];
        for (int i = 0; i < 3; i++) {
            flames[i] = fx.sprite("aura", V.up(c0, 1.0)).color(i == 1 ? glow : BLOOD).size(2.0).alpha(0.75);
            flames[i].play(0, i * 3, 8, 2, true);
        }
        // 초월 도끼 둘: 쥔 곳이 몸에서 1칸 · 머리가 바깥 (허리 높이)
        double[] g = gripOf(r.grade());
        Vfx.Prop[] axes = new Vfx.Prop[2];
        for (int j = 0; j < 2; j++) axes[j] = fx.prop(axeModel(r), V.up(c0, 0.75)).grip(g[0], g[1], g[2]).bright();
        double[] ang = {Kit.rand() * 360};
        int[] sweepUntil = {-1};
        fx.each(0, total + 12, 1, t -> {
            if (!RuneKit.aliveSince(sp, alive) || sp.isRemoved()) {
                endBloodSky(sp, level0); // 죽으면 핏빛 하늘도 걷힘
                fx.end();
                return;
            }
            Vec3 c = sp.position();
            boolean ending = t >= total;
            ang[0] += t < sweepUntil[0] ? 30 : 6;
            Vec3 facing = Kit.facingOf(sp);
            for (int j = 0; j < 2; j++) {
                Vec3 radial = V.turnY(new Vec3(1, 0, 0), ang[0] + j * 180);
                Vec3 tangent = V.turnY(radial, 90); // 도는 쪽 (ang 이 커지는 쪽 — 날이 앞)
                Vec3 grip = V.add(V.up(c, 0.75), radial, 0.95);
                axes[j].to(grip, axeRot(radial, tangent), ending ? 0.2 : (t < 4 ? 0.2 + t * 0.2 : 0.82), 1);
            }
            sigil.to(V.up(c, 0.07), -1, 1);
            for (int i = 0; i < 3; i++) {
                Vec3 d = V.turnY(facing, 100 + i * 80);
                flames[i].to(V.add(V.up(c, 1.0), d, 0.55), -1, 1);
            }
            if (ending && t == total) {
                sigil.fade(t, 8);
                for (Vfx.Sprite fl : flames) fl.fade(t, 6);
            }
        });
        SkillFx.every(hits, gap, i -> {
            if (!RuneKit.aliveSince(sp, alive)) {
                endBloodSky(sp, level0);
                return;
            }
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            sweepUntil[0] = fx.age() + 6; // 도끼 둘이 6틱 동안 반 바퀴를 휩씀
            // 휩쓴 자리의 초승달 (도끼 둘 — 바닥에 가깝게 눕혀 · 도는 쪽)
            for (int j = 0; j < 2; j++) {
                Vec3 radial = V.turnY(new Vec3(1, 0, 0), ang[0] + j * 180 + 60);
                VfxKit.slash(fx, V.add(V.up(c, 0.8), radial, 2.2), V.turnY(radial, 90), V.unit(V.add(V.UP, radial, 0.35)), 4.0, j == 1 ? BLOOD : glow, 2);
            }
            VfxKit.ring(fx, c, radius, i % 2 == 1 ? BLOOD : glow, 0, 8);
            fx.sprite("bladering", V.up(c, 0.09)).color(i % 2 == 1 ? glow : BLOOD).fixed(Vfx.flat(i * 23)).size(radius * 1.6).alpha(0.7)
                    .play(fx.age(), 8, 8, 1, false);
            SkillFx.play(level, "mace.smash_air", c, 0.8 + i * 0.05, 0.8);
            SkillFx.play(level, "mace.smash_ground", c, 0.7, 0.6);
            int n = 0;
            for (LivingEntity e : Kit.around(sp, c, radius)) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                    n++;
                    if (n <= 8) {
                        VfxKit.xcut(fx, Kit.bodyPoint(e), 1.4, n % 2 == 0 ? BLOOD : glow, 2);
                        VfxKit.blood(fx, Kit.bodyPoint(e), 0.7, 1, BLOOD, 2);
                    }
                }
            }
            if (n > 0) {
                double healed = RpgHealth.healBy(sp, RpgHealth.maxOf(sp) * k.num("heal") * Math.min(n, k.num("healMax"))
                        * Runes.supportScale(RpgStats.effective(sp), r.family()));
                if (healed > 0) DamageNumbers.show(sp, healed, DamageNumbers.Style.HEAL, "", 0);
            }
            if (i == hits - 1) {
                VfxKit.burst(fx, V.up(c, 1), 4.0, BLOOD, 2);
                VfxKit.blood(fx, V.up(c, 1.2), 1.4, 4, BLOOD, 2);
                SkillFx.play(level, "random.explode", c, 1.0, 0.5);
            }
        });
        // 끝 소리 (죽었다 다시 태어났어도 — 지금 엔티티를 uuid 로 찾음)
        RuneKit.later(total, () -> {
            ServerPlayer p = RuneKit.player(level0, sp.getUUID());
            if (p != null) SkillFx.play(p.level(), "mob.zombie.unfect", p.position(), 0.8, 0.5);
        });
        return true;
    }
}
