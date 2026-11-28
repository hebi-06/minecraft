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

    static void register() {
        RuneCast.register("axeBoomerang", RuneAxe::axeBoomerang);
        RuneCast.register("bloodCleave", RuneAxe::bloodCleave);
        RuneCast.register("leapSlam", RuneAxe::leapSlam);
        RuneCast.register("execute", RuneAxe::execute);
        RuneCast.register("berserker", RuneAxe::berserker);
        RuneCast.register("rageSlam", RuneAxe::rageSlam); // 2026-11-28 새 버서커 (한 방 + 광폭화 · 모든 공격 흡혈)
        DamageNumbers.DealtHook dh = DamageNumbers.dealtHook;
        DamageNumbers.dealtHook = (a, v, d) -> {
            dh.dealt(a, v, d);
            rageLeech(a, v, d);
        };
    }

    // ------------------------------------------------------------ 2026-11-11 Vfx (모드 없는 클라이언트도 보는 입체 도끼 + 2D 연출)

    /**
     * 도끼 모델: 2026-11-12 룬 전용 도끼 하나 (사용자 "룬 전용 도끼 하나" — 룬 등급 · 손에 든 무기와 상관없이, 무기 디자인과 따로) —
     * 흑요석 자루 · 붉은 마력 초승달 날 (룬 글자 맥동) · 흰 빛 날 끝 (tools/rune_props.py → rpg:vfx/rune_axe)
     */
    static final String RUNE_AXE = "rpg:vfx/rune_axe";
    /** 룬 도끼 모델 길이 (픽셀 — tools/rune_props.py) */
    static final double RUNE_AXE_LEN = 34.5;

    static String axeModel(Runes.Rune r) {
        return RUNE_AXE;
    }

    /**
     * 연출 크기 몫: 시범 (2026-11-11 — 등급마다 무기 디자인 도끼) 때의 화면 크기를 그대로 두려고 그때 모델 길이 ÷ 룬 도끼 길이를 곱함
     * (회전 도끼 · 도약 찍기 · 처형은 보통 도끼만큼, 광전사의 혼은 큰 도끼만큼)
     */
    static double fitOf(String grade) {
        double old = switch (grade) {
            case "common", "rare" -> 21.8;
            case "noble" -> 21.4;
            case "hero" -> 23.6;
            default -> 47.5;
        };
        return old / RUNE_AXE_LEN;
    }

    /** 도끼 모델 축 (tools/rune_props.py): 자루 = 모델 +Y (자루 끝 → 머리), 날 = 모델 −Z, 넓은 면 = ±X. handle · edge (자루와 직교하게 맞춤) 로 가는 회전 */
    static Quaternionf axeRot(Vec3 handle, Vec3 edge) {
        Vec3 y = V.unit(handle);
        Vec3 e = V.unit(V.sub(edge, V.scale(y, V.dot(edge, y))));
        Vec3 z = V.scale(e, -1);
        Vec3 x = V.unit(V.cross(y, z));
        return Vfx.basis(x, y, z);
    }

    /** 룬 도끼의 쥔 곳 (모델 픽셀 — 자루 아래 · 자루 끝 보석 위) */
    static double[] gripOf(String grade) {
        return new double[] {8, -4, 8};
    }

    /** 룬 도끼의 머리 가운데 (모델 픽셀 — 날 · 자루 꽂이의 가운데) */
    static final double[] AXE_HEAD = {8, 15.5, 5};

    /** 쥔 곳에서 머리 가운데까지 (모델 픽셀 — 자루 방향) */
    static double headReach(String grade) {
        return AXE_HEAD[1] - gripOf(grade)[1];
    }

    /** 1인칭 눈 가까이 (1.3칸 안) 를 지나는 소품은 쓴 사람에게 숨김 — 화면을 덮지 않게 */
    static boolean nearEye(ServerPlayer sp, Vec3 p, double r) {
        return V.len(V.sub(p, sp.getEyePosition())) < r;
    }

    /**
     * 회전 도끼: 도끼를 던져 range 칸 (벽 앞까지) 날아가 hover 틱 제자리에서 돌다가 돌아옴. 갈 때 · 올 때 선 가까이 width 칸의 적을 한 번씩.
     * 2026-11-11 Vfx: 룬 도끼 (3D) 가 던진 쪽으로 날아가며 톱날처럼 돎 (원반 면이 던진 사람 쪽을 봄 — 1인칭에서 날이 보임) ·
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
        double fit = fitOf(r.grade());
        // 무게 중심 (자루와 날 사이) 둘레로 돎
        Vfx.Prop axe = fx.prop(axeModel(r), V.add(sp.getEyePosition(), f, 0.9)).grip(g[0], g[1] + reach * 16 * 0.62, (g[2] + AXE_HEAD[2]) / 2).bright();
        axe.size(0.7 * fit, 0);
        Vfx.Sprite disc = fx.sprite("spin", from).color(glow).alpha(0.5).fixed(Vfx.basis(a, b, n)).size(0.1);
        Vec3[] last = {from};
        Vec3[] home = {null};
        int[] backTicks = {1};
        String life = RuneKit.lifeToken(sp);
        fx.each(0, back0 + 80, 1, t -> {
            Vec3 pos;
            double sc = Math.min(1.75, 0.7 + t * 0.26) * fit;
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
                if (tt >= backTicks[0] - 2) sc = 0.8 * fit;
            }
            double th = Math.toRadians(t * 74.0);
            Vec3 h = V.add(V.scale(a, Math.cos(th)), b, Math.sin(th));
            Vec3 e = V.add(V.scale(a, -Math.sin(th)), b, Math.cos(th));
            axe.to(pos, axeRot(h, e), sc, 1);
            disc.to(pos, (t > out && t <= back0 ? 2.9 : 2.3) * sc / (1.75 * fit), 1).roll(-t * 47.0, 1);
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
        // 2026-11-13 3D 룬 도끼 (2.4칸 — 2D 도끼 그림 대신, 사용자 "이재 다른 룬들도 모션하고 이펙트 추가하자"): 오른 어깨 위 뒤에서 대각선으로 휘둘러져 (2틱에 앞을
        // 지남 — 동작 rn_blood_cleave 와 같은 때) 왼쪽 아래 앞 땅까지 찍혀 박힌 채 머물다 가라앉음 (날이 휘두르는 쪽)
        RuneSwing.axe(sp, RuneKit.lifeToken(sp), 1.45, 0.25, RuneProps.scaleFor(2.4, RUNE_AXE_LEN), 58, 150, -58, 0, 3, 7, null, glow, core);
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
     * 2026-11-11 Vfx: 뛰어오르는 자리의 흙먼지 고리 → 뛰는 동안 머리 위로 치켜든 룬 도끼 (3D — 몸을 따라감, 머리 뒤 핏빛 빛) →
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
        double fit = fitOf(r.grade());
        Vfx.Prop axe = fx.prop(axeModel(r), V.add(V.up(start, 1.5), f, -0.2)).grip(g[0], g[1], g[2]).bright();
        axe.to(null, axeRot(up0, edge0), 1.7 * fit, 0);
        Vfx.Sprite aura = fx.sprite("glow", V.up(start, 2.6)).color(BLOOD).size(2.2).alpha(0.55);
        double reach = headReach(r.grade()) / 16.0 * 1.7 * fit;
        boolean[] landed = {false}; // 내려섰으면 따라가기를 멈춤 (효과 틱과 일 틱의 차례가 달라도 내리찍은 모습을 덮지 않게)
        fx.each(1, air + 4, 1, t -> {
            if (landed[0] || sp.isRemoved()) return;
            Vec3 grip = V.add(V.up(sp.position(), 1.45), f, -0.15);
            axe.to(grip, null, -1, 1);
            aura.to(V.add(grip, up0, reach), -1, 1);
            if (t % 2 == 0) VfxKit.glow(fx, V.add(grip, up0, reach), 1.2, glow, 0.45, 5);
        });
        SkillFx.play(level, "mob.enderdragon.flap", start, 1.2, 0.7);
        RuneKit.leap(sp, land, 2.0 + V.hdist(land, start) * 0.15, air, glow, core, () -> {
            landed[0] = true;
            Vec3 c = sp.position();
            Vec3 hit = VfxKit.ground(level, V.add(c, f, 1.8));
            // 내리찍음: 2틱에 자루가 머리 위 → 앞 아래 (날이 땅에 박힘)
            Vec3 grip = V.add(V.up(c, 1.15), f, 0.35);
            Vec3 down = V.unit(V.sub(V.up(hit, 0.15), grip));
            Vec3 mid = V.unit(V.add(V.add(up0, down), f, 0.6));
            Vec3 edgeMid = V.unit(V.cross(mid, V.rightOf(f))); // 날 = 자루 × 오른쪽 (휘두르는 쪽)
            Vec3 edgeEnd = V.unit(V.cross(down, V.rightOf(f)));
            double sc = Math.max(1.7 * fit, (V.len(V.sub(V.up(hit, 0.15), grip)) + 0.25) / (headReach(r.grade()) / 16.0));
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
     * 2026-11-11 Vfx: 대상 발밑에 도는 핏빛 칼날 고리 → 대상 위에 룬 도끼 (3D) 가 나타나 커지며 넓은 면이 나를 본 채 한 바퀴 돌아
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
        double fit = fitOf(r.grade());
        Vfx.Prop axe = fx.prop(axeModel(r), V.up(target.position(), top + 1.2)).grip(AXE_HEAD[0], AXE_HEAD[1], AXE_HEAD[2]).bright();
        axe.to(null, axeRot(V.UP, f), 0.5 * fit, 0);
        Vfx.Sprite halo = fx.sprite("glow", V.up(target.position(), top + 1.2)).color(BLOOD).size(1.2).alpha(0.6);
        boolean[] struck = {false}; // 내리꽂았으면 떠오르기를 멈춤 (효과 틱과 일 틱의 차례가 달라도 박힌 도끼를 하늘로 되돌리지 않게)
        fx.each(1, delay + 4, 1, t -> {
            if (struck[0] || target.isRemoved()) return;
            double u = Math.min(1, t / (double) delay);
            Vec3 at = V.up(target.position(), top + 1.2 + u * 1.0);
            double th = Math.toRadians(270 * u * u * (3 - 2 * u)); // 자루: 위 → 왼쪽 → 아래 → 오른쪽 수평 (한 바퀴의 3/4) — 날은 그때 아래
            Vec3 h = V.add(V.scale(V.UP, Math.cos(th)), side, -Math.sin(th));
            Vec3 e = V.cross(h, V.scale(f, -1)); // 날: 자루를 돌린 쪽 (넓은 면은 늘 나를 봄)
            axe.to(at, axeRot(h, e), (0.5 + 1.9 * u) * fit, 1);
            halo.to(at, 1.2 + 2.2 * u, 1);
            sig.to(V.up(VfxKit.ground(level, target.position()), 0.07), -1, 1);
            if (t % 2 == 0) VfxKit.sparks(fx, at, glow, 2, 1.4, 0);
        });
        SkillFx.play(level, "mob.ravager.roar", sp.position(), 0.9, 0.8);
        SkillFx.play(level, "mob.evocation_illager.prepare_attack", target.position(), 0.6, 0.8);
        RuneKit.later(delay, () -> {
            struck[0] = true;
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
            axe.to(V.up(g, 1.0), chop, 2.4 * fit, 2);
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
            fx.on(fx.age() + 18, () -> axe.to(V.up(g, 0.2), null, 1.0 * fit, 8));
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
     * 2026-11-11 Vfx: 광폭화 순간 핏빛 터짐 · 번쩍 · 고리 둘 · 솟는 핏빛 불기둥 · 몸의 핏빛 불꽃 셋.
     * 2026-11-17 도감 메모 (광전사의 혼 → 버서커 "빙빙 도는거 짜쳐서 다른걸로 수정"): 몸 둘레를 돌던 룬 도끼 둘 · 발밑에서 돌던 칼날 고리를 뺌 → 난타마다
     * 핏빛 거대한 룬 도끼 (3D) 가 둘레 한 곳 (번갈아 앞 오른쪽 · 앞 왼쪽 · 옆 뒤) 을 내려찍음 (chop — 치기 7틱 전에 내 머리 위 앞에서 커지며 뒤로 젖혀져 나타나
     * 치는 틱에 2틱 동안 내리찍어 날이 땅에 박힘) → 갈라진 땅 · 핏빛 충격 고리 (반지름 radius — 맞는 범위) · 터짐 · 흙먼지 · 튀는 돌 · 맞은 적마다 X 베기 · 피 →
     * 박힌 도끼는 가라앉음. 첫 난타 (쓴 틱) 는 광폭화 터짐, 마지막은 도끼 둘이 앞 양쪽에 함께 · 큰 핏빛 터짐. 피해 · 틱 · 범위 · 흡혈 그대로. 죽으면 모두 사라짐
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
        VfxKit.crack(fx, c0, radius * 1.8, BLOOD, 0, 40); // 광폭화 순간 발밑이 갈라짐 (돌지 않음)
        // 몸의 핏빛 불꽃 셋 (양옆 · 뒤 — 1인칭 화면을 덮지 않게 앞은 비움)
        Vfx.Sprite[] flames = new Vfx.Sprite[3];
        for (int i = 0; i < 3; i++) {
            flames[i] = fx.sprite("aura", V.up(c0, 1.0)).color(i == 1 ? glow : BLOOD).size(2.0).alpha(0.75);
            flames[i].play(0, i * 3, 8, 2, true);
        }
        fx.each(0, total + 12, 1, t -> {
            if (!RuneKit.aliveSince(sp, alive) || sp.isRemoved()) {
                endBloodSky(sp, level0); // 죽으면 핏빛 하늘도 걷힘
                fx.end();
                return;
            }
            Vec3 c = sp.position();
            Vec3 facing = Kit.facingOf(sp);
            for (int i = 0; i < 3; i++) {
                Vec3 d = V.turnY(facing, 100 + i * 80);
                flames[i].to(V.add(V.up(c, 1.0), d, 0.55), -1, 1);
            }
            if (t == total) for (Vfx.Sprite fl : flames) fl.fade(t, 6);
        });
        // 거대한 도끼 내려찍기 (둘째 난타부터 — 치기 CHOP_LEAD 틱 전에 나타남): 내 쪽 (보는 쪽) 에서 번갈아 앞 오른쪽 · 앞 왼쪽 · 옆 뒤 · 마지막은 앞 양쪽에 함께
        double[] turns = {0, 45, -45, 125, -125, 30};
        for (int i = 1; i < hits; i++) {
            int at = i * gap;
            boolean last = i == hits - 1;
            final double turn = turns[Math.min(i, turns.length - 1)];
            RuneKit.later(Math.max(0, at - CHOP_LEAD), () -> {
                if (!RuneKit.aliveSince(sp, alive) || sp.isRemoved()) return;
                Vec3 face = Kit.facingOf(sp);
                giantChop(fx, sp, r, V.turnY(face, turn), last);
                if (last) giantChop(fx, sp, r, V.turnY(face, -turn), true);
            });
        }
        SkillFx.every(hits, gap, i -> {
            if (!RuneKit.aliveSince(sp, alive)) {
                endBloodSky(sp, level0);
                return;
            }
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            // 난타: 핏빛 충격 고리 (맞는 범위 — 반지름 radius) · 발밑 흙먼지 (도는 칼날 고리 · 휩쓴 초승달은 뺌 — 도끼가 내려찍힌 자리는 giantChop)
            VfxKit.ring(fx, c, radius, i % 2 == 1 ? BLOOD : glow, 0, 8);
            VfxKit.ring(fx, c, radius * 0.55, core, 2, 6);
            VfxKit.dust(fx, c, radius * 0.6, 4, VfxKit.DUST, 1);
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

    // ------------------------------------------------------------ 2026-11-28 새 버서커 (rageSlam)

    /** 광폭화 흡혈 (모든 공격 — DamageNumbers.dealtHook): 플레이어 uuid → 광폭화 */
    private static final Map<UUID, Rage> RAGES = new HashMap<>();

    /**
     * 광폭화 하나: 걸 때의 죽음 표 alive · 끝 틱 until · 준 피해의 rate 만큼 회복 · 1초 (20틱 칸) 상한 = 최대 체력 × capFrac — 지금 칸의 처음 winStart · 그 칸에서
     * 회복하려 한 양 winHealed · 띄울 숫자 pending (모아서 10틱마다) · 마지막으로 맞은 자리 lastHit (피가 흘러드는 줄기)
     */
    private static final class Rage {
        final int alive;
        final long until;
        final double rate;
        final double capFrac;
        long winStart;
        double winHealed;
        double pending;
        Vec3 lastHit;

        Rage(int alive, long until, double rate, double capFrac, long now) {
            this.alive = alive;
            this.until = until;
            this.rate = rate;
            this.capFrac = capFrac;
            this.winStart = now;
        }
    }

    /** 광폭화 동안 준 피해 (평타 · 직업 스킬 · 룬 — 들어간 피해 그대로): × rate 회복, 20틱 칸마다 최대 체력 × capFrac 까지 */
    static void rageLeech(ServerPlayer sp, LivingEntity victim, double dealt) {
        if (RAGES.isEmpty() || sp == null || !(dealt > 0)) return;
        Rage g = RAGES.get(sp.getUUID());
        if (g == null) return;
        long now = mmorpg.fabric.Tasks.now();
        if (now > g.until || !RuneKit.aliveSince(sp, g.alive)) {
            RAGES.remove(sp.getUUID());
            return;
        }
        if (now - g.winStart >= 20) {
            g.winStart = now;
            g.winHealed = 0;
        }
        double want = Math.min(dealt * g.rate, RpgHealth.maxOf(sp) * g.capFrac - g.winHealed);
        if (!(want > 0)) return;
        g.winHealed += want;
        double got = RpgHealth.healBy(sp, want);
        if (got > 0) g.pending += got;
        if (victim != null) g.lastHit = Kit.bodyPoint(victim);
    }

    /** 시험용: 광폭화 흡혈이 걸려 있는지 */
    public static boolean rageOn(ServerPlayer sp) {
        Rage g = RAGES.get(sp.getUUID());
        return g != null && mmorpg.fabric.Tasks.now() <= g.until;
    }

    /**
     * 2026-11-28 새 버서커 (사용자 메모 — 룬 도감 판 15 "도끼를 그냥 처음에 한번 크게 내려치고 그이후에는 버프만 받는걸로 지속딜 삭제 및 한방딜로 변경, 버프는 유지" ·
     * 흡혈은 사용자 고른 것 "광폭화 동안 모든 공격 흡혈"). 옛 광전사의 혼 (숨김 — berserker 난타) 은 그대로.
     * <ol>
     * <li>치켜듦 (windup 틱): 쓴 자리 머리 위 뒤로 거대한 핏빛 룬 도끼가 커지며 나타나 젖혀짐 · 내려찍을 자리 (앞 dist 칸) 에 핏빛 고리가 반지름 radius 까지 퍼짐 (예고)</li>
     * <li>내려찍기 (windup − 2 → windup): 2틱 동안 내리찍어 날이 땅에 박힘 → 반지름 radius 칸 모두 위력 × mult 한 번 (예산 전부) · 큰 핏빛 초승달 둘 · 갈라진 땅 ·
     * 터짐 · 충격 고리 셋 · 솟는 바위 (3D) · 흙먼지 · 튀는 돌 · 피 · 화면 번쩍임 (3돌 이상) · 맞은 적마다 X 베기</li>
     * <li>광폭화 (ticks 틱 — 내려찍은 때부터): 받는 피해 − reduce · 모든 공격이 준 피해의 leech 만큼 회복 (1초에 최대 체력 × heal × 회복 배율까지 — rageLeech) ·
     * 몸의 핏빛 불꽃 셋 · 핏빛 하늘 · 회복은 10틱마다 모아 숫자 · 맞힌 적에게서 피가 흘러듦. 3돌: 밀려나지 않음. 죽으면 끝</li>
     * </ol>
     */
    static boolean rageSlam(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 d = V.flat(sp.getViewVector(1F));
        if (V.len(d) < 1e-6) d = Kit.facingOf(sp);
        final Vec3 f = V.unit(d);
        int windup = Math.max(4, (int) k.num("windup"));
        int rage = (int) k.num("ticks");
        double radius = k.num("radius");
        double[][] cs = RuneKit.colorOf("axe");
        double[] glow = cs[0];
        double[] core = cs[1];
        int alive = RuneKit.deathToken(sp);
        // 오른손으로 내려찍음: 날이 앞 dist 칸 · 오른쪽 0.25칸에 박힘 (1인칭에서 자루가 화면 가운데를 가르지 않게 — 첫 영상에서 박힌 자루가 10틱 넘게 과녁을 가렸음)
        Vec3 right = V.rightOf(f);
        Vec3 hit = VfxKit.ground(level0, V.add(V.add(c0, f, k.num("dist")), right, 0.25));
        Vfx.Fx fx = Vfx.at(level0, c0, windup + rage + 40);
        SkillFx.play(level0, "mob.ravager.roar", c0, 0.8, 1);
        SkillFx.play(level0, "mob.enderdragon.growl", c0, 1.0, 0.5);
        // 치켜든 거대한 도끼 (쓴 자리 — 손잡이 머리 위 앞 0.6칸 · 오른쪽 0.45칸 · 3.4칸 높이, 머리가 위 · 조금 뒤 · 날이 앞): 작게 나타나 windup − 2 틱 동안 커짐.
        // 손잡이가 머리에 가까우면 (2.6칸 — 2차 영상) 도끼 상자가 눈을 품어 1인칭에서 통째로 거둬졌음 (Vfx eyeCull)
        Vec3 grip = V.add(V.add(V.up(c0, 3.4), f, 0.6), right, 0.45);
        Vec3 down = V.unit(V.sub(V.up(hit, 0.2), grip));
        double sc = (V.len(V.sub(V.up(hit, 0.2), grip)) + 0.15) / (headReach(r.grade()) / 16.0);
        Vec3 up0 = V.unit(V.add(V.UP, f, -0.45));
        Vec3 edge0 = V.unit(V.add(f, V.UP, 0.45));
        Vec3 mid = V.unit(V.add(V.add(up0, down), f, 0.7));
        Vec3 edgeMid = V.unit(V.cross(mid, right));
        Vec3 edgeEnd = V.unit(V.cross(down, right));
        double[] g = gripOf(r.grade());
        Vfx.Prop axe = fx.prop(axeModel(r), grip).grip(g[0], g[1], g[2]).bright();
        axe.to(null, axeRot(up0, edge0), sc * 0.3, 0);
        axe.to(null, null, sc, Math.max(1, windup - 3));
        Vec3 head0 = V.add(grip, up0, sc * headReach(r.grade()) / 16.0);
        Vfx.Sprite halo = fx.sprite("glow", head0).color(BLOOD).size(1.2).alpha(0.6);
        halo.to(null, 3.4, Math.max(1, windup - 3));
        fx.sprite("cinder", V.up(c0, 1.6)).color(BLOOD).size(3.6).alpha(0.85).play(0, 0, 8, 2, false);
        VfxKit.ring(fx, hit, radius, BLOOD, 0, windup); // 내려찍을 자리 예고 (맞는 범위까지 퍼짐)
        VfxKit.crack(fx, hit, 1.6, BLOOD, 0, windup + 6);
        for (int t = 2; t < windup - 2; t += 3) {
            final int tt = t;
            fx.on(tt, () -> {
                // 도끼 머리에 모여드는 핏빛 불티
                Vec3 h = V.add(grip, up0, sc * headReach(r.grade()) / 16.0 * Math.min(1, 0.3 + 0.7 * tt / (windup - 3.0)));
                VfxKit.sparks(fx, h, Kit.rand() < 0.5 ? BLOOD : glow, 3, 1.4, 0);
            });
        }
        int swing = windup - 2;
        fx.on(swing, () -> {
            axe.to(grip, axeRot(mid, edgeMid), sc, 1);
            halo.remove();
            SkillFx.play(level0, "item.trident.riptide_1", grip, 0.8, 0.5);
        });
        fx.on(swing + 1, () -> axe.to(grip, axeRot(down, edgeEnd), sc, 1));
        // 피해 · 광폭화는 시간표 (Tasks) 로 — 그림 (fx) 이 먼저 끝나도 그대로
        RuneKit.later(windup, () -> {
            if (!RuneKit.aliveSince(sp, alive) || sp.isRemoved()) {
                fx.end();
                return;
            }
            ServerLevel level = sp.level();
            // 한 방: 반지름 radius 칸 모두 (예산 전부)
            int n = 0;
            for (LivingEntity e : Kit.around(sp, hit, radius)) {
                double got = RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat());
                mmorpg.fabric.Mmorpg.LOG.info("[rune] rageSlam hit {} at {} dealt {}", e.getType().toShortString(), e.position(), got);
                if (got > 0) {
                    n++;
                    if (n <= 8) {
                        VfxKit.xcut(fx, Kit.bodyPoint(e), 1.8, n % 2 == 0 ? BLOOD : glow, 1);
                        VfxKit.blood(fx, Kit.bodyPoint(e), 0.9, 2, BLOOD, 1);
                    }
                }
            }
            mmorpg.fabric.Mmorpg.LOG.info("[rune] rageSlam hits {} at {} from {} facing {}", n, hit, c0, f);
            // 쓴 사람 1인칭: 큰 초승달 · 터짐은 눈앞을 덮어 거둬지므로 (Vfx eyeCull) 1인칭 전용으로 조금 작게 — 내려찍힌 자리에 세로 초승달 · 핏빛 터짐
            int ownerId = sp.getId();
            Vfx.Fx fp = Vfx.at(level, hit, 30, (Vfx.FirstPerson) p -> p.getId() == ownerId && !mmorpg.fabric.vfx.PBridge.thirdPersonOf(p));
            VfxKit.slash(fp, V.up(hit, 1.3), V.DOWN, V.scale(f, -1), 3.0, glow, 0);
            VfxKit.burst(fp, V.up(hit, 0.5), 2.4, BLOOD, 1);
            VfxKit.sparks(fp, V.up(hit, 0.5), core, 8, 1.6, 1);
            // 내려찍힌 자리
            VfxKit.slash(fx, V.up(hit, 2.0), V.DOWN, V.scale(f, -1), 6.0, glow, 0);
            VfxKit.slash(fx, V.up(hit, 1.8), V.DOWN, V.scale(f, -1), 5.0, BLOOD, 1);
            VfxKit.crack(fx, hit, radius * 1.7, BLOOD, 0, 60);
            VfxKit.burst(fx, V.up(hit, 0.6), 4.8, glow, 0);
            VfxKit.burst(fx, V.up(hit, 0.9), 3.4, BLOOD, 2);
            VfxKit.ring(fx, hit, radius * 0.6, core, 0, 8);
            VfxKit.ring(fx, hit, radius, BLOOD, 1, 10);
            VfxKit.ring(fx, hit, radius * 1.3, glow, 3, 12);
            VfxKit.dust(fx, hit, 2.4, 10, VfxKit.DUST, 1);
            VfxKit.sparks(fx, V.up(hit, 0.4), core, 10, 2.4, 1);
            VfxKit.blood(fx, V.up(hit, 0.6), 1.6, 6, BLOOD, 1);
            // 솟아 튀는 3D 바위 — 앞 · 옆으로만 (200°: 첫 영상의 사방 튀는 돌 · 바위가 1인칭 눈앞으로 날아와 화면을 덮었음)
            RuneSwing.erupt(level, hit, f, 6, 200, 0.6, 1.5, 0.5, 0.9, 2.8, glow);
            RuneKit.zonePulse(level, hit, glow, radius, 0.5);
            SkillFx.play(level, "mace.heavy_smash_ground", hit, 1.0, 0.7);
            SkillFx.play(level, "random.explode", hit, 1.0, 0.6);
            // 박힌 도끼: 5틱 머문 뒤 가라앉으며 사라짐
            fx.on(fx.age() + 5, () -> axe.to(V.add(grip, down, 0.9), null, sc * 0.4, 6));
            fx.on(fx.age() + 12, axe::remove);
            // 광폭화 (내려찍은 때부터 rage 틱)
            Vec3 c = sp.position();
            Guards.set(sp, rage, k.num("reduce"), null, null);
            if (k.truthy("steadfast")) mmorpg.fabric.RpgCombat.setSteadfast(sp, rage); // 3돌: 광폭화 동안 밀려나지 않음
            double cap = k.num("heal") * Runes.supportScale(RpgStats.effective(sp), r.family());
            RAGES.put(sp.getUUID(), new Rage(alive, mmorpg.fabric.Tasks.now() + rage, k.num("leech"), cap, mmorpg.fabric.Tasks.now()));
            RuneKit.ultSkyFor(sp, level, c, "rageSlam", rage + 10, null);
            RuneKit.ultFlashFor(sp, level, c, "rageSlam", "flash", Opt.empty());
            SkillFx.play(level, "mob.ravager.roar", c, 0.7, 0.9);
            VfxKit.burst(fx, V.up(c, 1.1), 3.0, BLOOD, 2);
            VfxKit.ring(fx, c, 2.2, BLOOD, 2, 10);
            rageAura(fx, sp, level0, alive, rage, glow);
        });
        return true;
    }

    /** 광폭화 동안 (지금부터 rage 틱): 몸의 핏빛 불꽃 셋 (양옆 · 뒤 — 1인칭 화면을 덮지 않게 앞은 비움) · 회복 숫자 (10틱마다 모아서) · 맞힌 적에게서 피가 흘러듦 */
    private static void rageAura(Vfx.Fx fx, ServerPlayer sp, ServerLevel level0, int alive, int rage, double[] glow) {
        int t0 = fx.age();
        Vfx.Sprite[] flames = new Vfx.Sprite[3];
        for (int i = 0; i < 3; i++) {
            flames[i] = fx.sprite("aura", V.up(sp.position(), 1.0)).color(i == 1 ? glow : BLOOD).size(2.0).alpha(0.75);
            flames[i].play(t0, i * 3, 8, 2, true);
        }
        fx.each(t0, t0 + rage + 6, 1, t -> {
            Rage g = RAGES.get(sp.getUUID());
            if (!RuneKit.aliveSince(sp, alive) || sp.isRemoved()) {
                RAGES.remove(sp.getUUID());
                endRageSky(sp, level0);
                fx.end();
                return;
            }
            Vec3 c = sp.position();
            Vec3 facing = Kit.facingOf(sp);
            for (int i = 0; i < 3; i++) flames[i].to(V.add(V.up(c, 1.0), V.turnY(facing, 100 + i * 80), 0.55), -1, 1);
            if ((t - t0) % 10 == 0 && g != null && g.pending >= 0.05) {
                DamageNumbers.show(sp, g.pending, DamageNumbers.Style.HEAL, "", 0);
                if (g.lastHit != null && g.lastHit.distanceTo(c) < 12) {
                    Vec3 to = V.up(c, 1.0);
                    for (int j = 0; j < 3; j++) {
                        Vec3 p = V.add(g.lastHit, new Vec3((Kit.rand() - 0.5) * 0.6, (Kit.rand() - 0.3) * 0.6, (Kit.rand() - 0.5) * 0.6));
                        Vec3 dd = V.sub(to, p);
                        SkillFx.fx(sp.level(), "rpg:arrow_spark", p, Opt.of("dir", V.unit(dd), "speed", V.len(dd) / 0.4, "size", 0.16, "grow", 0.5, "life", 0.4,
                                "color", BLOOD, "alpha", 1.0));
                    }
                }
                g.pending = 0;
            }
            if (t == t0 + rage) {
                for (Vfx.Sprite fl : flames) fl.fade(t, 6);
                RAGES.remove(sp.getUUID());
                SkillFx.play(sp.level(), "mob.zombie.unfect", c, 0.8, 0.5);
            }
        });
    }

    /** 새 버서커의 광폭화가 죽음으로 끝남: 핏빛 하늘을 걷음 */
    private static void endRageSky(ServerPlayer sp, ServerLevel level) {
        ServerPlayer cur = RuneKit.player(level, sp.getUUID());
        Ult.endSky(List.of(cur != null ? cur : sp), RuneKit.ultOf("rageSlam").strOrNull("fog"));
    }

    /** 거대한 도끼가 나타나 내려찍기까지 (틱) — 치는 틱 (난타) 에 날이 땅에 닿음 */
    static final int CHOP_LEAD = 7;

    /**
     * 2026-11-17 버서커의 거대한 룬 도끼 하나 (giantChop): 쓴 사람 둘레 d 쪽 3.1칸 땅을 내려찍음 — 쥔 곳은 내 머리 위 앞 (d 로 0.9칸 · 2.7칸 높이), 지금부터
     * CHOP_LEAD − 2 틱 동안 커지며 뒤로 젖혀진 채 (머리가 위 뒤 · 날이 앞) → 2틱 동안 내리찍어 머리가 땅에 박힘 (CHOP_LEAD 틱 — 난타와 같은 틱) →
     * 갈라진 땅 · 터짐 · 흙먼지 · 튀는 돌 · 불꽃 · 위에서 아래로 그어지는 핏빛 초승달 → 8틱 머문 뒤 땅속으로 가라앉으며 사라짐. 보이기만 (피해는 난타)
     */
    private static void giantChop(Vfx.Fx fx, ServerPlayer sp, Runes.Rune r, Vec3 d0, boolean big) {
        ServerLevel level = sp.level();
        Vec3 d = V.flat(d0);
        Vec3 c = sp.position();
        Vec3 hit = VfxKit.ground(level, V.add(c, d, 3.1));
        Vec3 grip = V.add(V.up(c, 2.7), d, 0.9);
        Vec3 down = V.unit(V.sub(V.up(hit, 0.2), grip));
        double sc = (V.len(V.sub(V.up(hit, 0.2), grip)) + 0.15) / (headReach(r.grade()) / 16.0);
        Vec3 up0 = V.unit(V.add(V.UP, d, -0.35)); // 젖힘: 머리가 위 · 조금 뒤
        Vec3 edge0 = V.unit(V.add(d, V.UP, 0.4));
        Vec3 mid = V.unit(V.add(V.add(up0, down), d, 0.7));
        Vec3 right = V.rightOf(d);
        Vec3 edgeMid = V.unit(V.cross(mid, right)); // 날 = 자루 × 오른쪽 (휘두르는 쪽)
        Vec3 edgeEnd = V.unit(V.cross(down, right));
        double[] g = gripOf(r.grade());
        Vfx.Prop axe = fx.prop(axeModel(r), grip).grip(g[0], g[1], g[2]).bright();
        axe.to(null, axeRot(up0, edge0), sc * 0.35, 0);
        axe.to(null, null, sc, 3); // 커지며 나타남
        Vfx.Sprite halo = fx.sprite("glow", V.add(grip, up0, sc * headReach(r.grade()) / 16.0)).color(BLOOD).size(2.4).alpha(0.5);
        SkillFx.play(level, "mob.ravager.attack", c, 0.6, 0.7);
        int now = fx.age();
        int swing = now + CHOP_LEAD - 2;
        fx.on(swing, () -> {
            axe.to(grip, axeRot(mid, edgeMid), sc, 1);
            halo.remove();
            SkillFx.play(level, "item.trident.riptide_1", grip, 0.6, 0.6);
        });
        fx.on(swing + 1, () -> axe.to(grip, axeRot(down, edgeEnd), sc, 1));
        fx.on(now + CHOP_LEAD, () -> {
            double[][] cs = RuneKit.colorOf("axe");
            double[] glow = cs[0];
            double[] core = cs[1];
            VfxKit.slash(fx, V.up(hit, 1.6), V.DOWN, V.scale(d, -1), big ? 4.6 : 3.8, glow, 0);
            VfxKit.slash(fx, V.up(hit, 1.5), V.DOWN, V.scale(d, -1), big ? 4.0 : 3.2, BLOOD, 1);
            VfxKit.crack(fx, hit, big ? 5.6 : 4.4, BLOOD, 0, 40);
            VfxKit.burst(fx, V.up(hit, 0.5), big ? 3.6 : 2.8, glow, 0);
            VfxKit.ring(fx, hit, big ? 2.6 : 2.0, core, 1, 8);
            VfxKit.dust(fx, hit, 1.6, 6, VfxKit.DUST, 1);
            VfxKit.rocks(fx, hit, big ? 9 : 6, 6.0, 0.4, new String[] {"minecraft:cobbled_deepslate", "minecraft:deepslate", "minecraft:tuff", "minecraft:cobblestone"}, 1);
            VfxKit.sparks(fx, V.up(hit, 0.4), core, 6, 1.8, 1);
            VfxKit.blood(fx, V.up(hit, 0.6), big ? 1.2 : 0.8, big ? 4 : 2, BLOOD, 1);
            SkillFx.play(level, "mace.heavy_smash_ground", hit, 0.8, big ? 1.0 : 0.8);
        });
        // 박힌 도끼: 8틱 머문 뒤 땅속으로 가라앉으며 사라짐
        fx.on(now + CHOP_LEAD + 8, () -> axe.to(V.add(grip, down, 0.8), null, sc * 0.4, 8));
        fx.on(now + CHOP_LEAD + 17, axe::remove);
    }
}
