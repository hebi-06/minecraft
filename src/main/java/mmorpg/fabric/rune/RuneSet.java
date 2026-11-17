package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Guards;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-17 룬 재개발 (rules_runes.json RUNE_SET — 계열 없이 등급마다 여섯) 에서 옛 동작을 빌려 쓰지 않는 새 동작 (사용자 "재개발을 시작해보자" · 컨셉 문서
 * 「룬 재개발 컨셉」): 램파트 (일반 — 방패 결계) · 블레이드 헤일 (고귀 — 칼날 우박) · 룬 재블린 (영웅 — 빛의 창 던지기). 아이스 랜스는 RuneInt.iceLance.
 * <p>
 * 그림은 그림 묶음 계열 (def.look) 의 빛으로 부름 — 꼬리표 (RuneCast 가 look 으로 붙임) 의 위르드식 묶음이 칠함: 램파트 = 투사 (수호 묶음 — 강철 푸른빛 방패),
 * 블레이드 헤일 = 민첩 (질풍 묶음). 느낌 층 (claude/rune-job-synergy.md): 램파트 = 체력 (버티고 섬 · 막을 때 파문 · 단단한 소리), 블레이드 헤일 = 민첩 (가는 칼 ·
 * 빠른 연타 · 움직이며)
 */
final class RuneSet {
    private RuneSet() {}

    static void register() {
        RuneCast.register("rampart", RuneSet::rampart);
        RuneCast.register("bladeHail", RuneSet::bladeHail);
        RuneCast.register("javelin", RuneSet::javelin);
    }

    // ------------------------------------------------------------ 램파트 (일반)

    /** 결계의 방패 셋 — 보는 쪽에서의 각도 (가운데 · 양옆) */
    private static final double[] WALL = {0, -38, 38};

    /**
     * 램파트: ticks 틱 동안 몸 앞 1.7칸에 빛의 방패 셋 (방패 문장 — 수호 묶음의 빛의 방패) 이 둥글게 서서 쓴 사람의 보는 쪽을 따라감 · 받는 피해 × (1 − reduce)
     * (Guards). 맞으면 때린 쪽 방패에 파문 (세운 충격 고리 · 불티 · '쨍'). 끝 (ticks) 에 방패가 앞으로 밀려 나가며 앞 range 칸 · cone 도 안 적에게 위력 × mult ·
     * 밀쳐냄 (knock) — 땅의 충격 고리 · 터짐
     */
    static boolean rampart(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        double[][] cs = RuneKit.lookColors(k, "gladiator");
        double[] glow = cs[0];
        double[] core = cs[1];
        int ticks = (int) k.num("ticks");
        String life = RuneKit.lifeToken(sp);
        long until = Tasks.now() + ticks;
        long[] lastRipple = {-100};
        Guards.set(sp, ticks, k.num("reduce"), Guards.Reflect.fn((pl, attacker) -> {
            // 막을 때 파문: 때린 쪽 방패 자리 (몸 앞 1.6칸) 에 세운 고리 · 불티 — 6틱에 한 번
            if (!RuneKit.sameLife(pl, life) || attacker == null || Tasks.now() > until || Tasks.now() - lastRipple[0] < 6) return;
            lastRipple[0] = Tasks.now();
            ServerLevel d = pl.level();
            Vec3 to = V.flat(V.sub(attacker.position(), pl.position()));
            Vec3 dir = V.len(to) > 1e-3 ? V.unit(to) : V.flat(pl.getViewVector(1F));
            Vec3 at = V.up(V.add(pl.position(), dir, 1.6), 1.1);
            RuneKit.rfx2(d, "nova", at, Opt.of("dir", dir, "size", 0.55, "grow", 0.8, "life", 0.3, "alpha", 0.9), glow, core);
            RuneKit.shieldEmblem(pl, d, at, 0.95, glow, Opt.of("life", 0.25, "grow", 0.15, "fade", 0.5, "full", 2.5, "core", core));
            SkillFx.sparks(d, at, core, 6, 4);
            SkillFx.play(d, "item.shield.block", at, 1.3, 0.9);
        }), null);
        RuneKit.selfCue(sp, glow, core, false);
        SkillFx.play(level, "item.shield.block", sp.position(), 1.0, 0.7);
        SkillFx.play(level, "block.anvil.place", sp.position(), 0.6, 0.6);
        SkillFx.play(level, "item.armor.equip_iron", sp.position(), 1.2, 0.9);
        // 결계: 8틱마다 방패 셋을 다시 (보는 쪽을 따라감 — 처음은 커지며) · 발밑 문양
        for (int t = 0; t < ticks; t += 8) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                Vec3 f = Kit.facingOf(sp);
                for (double deg : WALL) {
                    Vec3 dir = RuneKit.turn(f, deg);
                    Vec3 at = V.up(V.add(sp.position(), dir, 1.7), 1.05);
                    RuneKit.shieldEmblem(sp, d, at, deg == 0 ? 1.0 : 0.85, glow, Opt.of("life", 0.5, "grow", tt == 0 ? 0.3 : 0.0, "fade", 0.25, "alpha", 0.85,
                            "full", 2.5, "core", core, "vel", RuneKit.bodyVel(sp)));
                }
                SkillFx.rune(d, sp.position(), Opt.of("size", 1.5, "life", 0.5, "color", glow, "alpha", 0.65, "rate", 0.0, "spin", tt * 4.0));
            });
        }
        // 끝: 결계를 앞으로 밀어 냄
        RuneKit.later(ticks, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Vec3 f = Kit.facingOf(sp);
            Vec3 c = sp.position();
            for (double deg : WALL) {
                Vec3 dir = RuneKit.turn(f, deg);
                RuneKit.shieldEmblem(sp, d, V.up(V.add(c, dir, 1.7), 1.05), 1.0, glow, Opt.of("life", 0.3, "fade", 0.5, "full", 2.5, "core", core, "vel",
                        V.scale(dir, 9.0)));
            }
            Vec3 front = V.add(c, f, 2.2);
            RuneKit.rfx2(d, "nova", V.up(front, 1.0), Opt.of("dir", f, "size", 1.1, "grow", 1.2, "life", 0.35, "alpha", 0.95), glow, core);
            RuneKit.zonePulse(d, front, glow, 2.2, 0.4);
            SkillFx.sparks(d, V.up(front, 1.0), core, 10, 6);
            SkillFx.play(d, "entity.iron_golem.attack", c, 1.0, 0.8);
            SkillFx.play(d, "item.shield.block", c, 0.8, 1.0);
            boolean boomed = false;
            for (LivingEntity e : RuneKit.coneTargets(sp, k.num("range"), k.num("cone"), 12)) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) <= 0) continue;
                RuneKit.knock(e, V.sub(e.position(), c), k.numOr("knock", 0.9), 0.3);
                if (!boomed) {
                    boomed = true;
                    Vec3 feet = e.position();
                    RuneBoost.impact(d, new Vec3(feet.x, RuneKit.groundAt(d, feet, feet.y), feet.z), glow, core, 2.2, r.grade()); // 아래 등급 터짐
                }
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 블레이드 헤일 (고귀)

    /** 칼날이 하늘에서 떨어지는 틱 (떨어지기 시작 → 꽂힘 = 피해) */
    static final int FALL = 4;
    /** 칼날 길이 (칸) · 꽂혀 있는 틱 · 가라앉는 틱 */
    private static final double HAIL_LEN = 1.9;
    private static final int HAIL_STUCK = 8;
    private static final int HAIL_SINK = 6;

    /**
     * 블레이드 헤일: hits 번 gap 틱마다 (그때 쓴 사람의 둘레 — 움직이며 쓸 수 있음) 둘레 radius 칸에 3D 룬 칼날이 하늘에서 우박처럼 떨어져 꽂힘 — 적마다 하나 (여섯까지)
     * + 빈 자리에 채워 넷 이상. 떨어지는 데 FALL 틱 → 꽂히는 틱에 둘레의 적 (떨어지기 시작한 자리 둘레) 에 위력 × mult · 꽂힌 자리 금 · 불티. 마지막에 바람 고리 · 터짐
     */
    static boolean bladeHail(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        double[][] cs = RuneKit.lookColors(k, "dex");
        double[] glow = cs[0];
        double[] core = cs[1];
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        double radius = k.num("radius");
        String life = RuneKit.lifeToken(sp);
        SkillFx.play(sp.level(), "item.trident.riptide_1", sp.position(), 1.5, 0.7);
        RuneKit.selfCue(sp, glow, core, true);
        for (int i = 0; i < hits; i++) {
            final int ii = i;
            RuneKit.later(i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 c = sp.position();
                // 떨어질 자리: 적마다 하나 (쓴 사람 쪽으로 반 칸 — 몸에 가려지지 않게) · 빈 자리를 채워 넷
                List<Vec3> spots = new ArrayList<>();
                for (LivingEntity e : Kit.around(sp, c, radius)) {
                    if (spots.size() >= 6) break;
                    Vec3 to = V.flat(V.sub(c, e.position()));
                    Vec3 p = V.len(to) > 1e-3 ? V.add(e.position(), V.unit(to), Math.min(0.5, e.getBbWidth() / 2 + 0.15)) : e.position();
                    spots.add(p);
                }
                double a0 = Kit.rand() * Math.PI * 2;
                for (int j = 0; spots.size() < 4; j++) {
                    double a = a0 + j * 2.4;
                    double rr = radius * (0.45 + 0.5 * Kit.rand());
                    spots.add(new Vec3(c.x + Math.cos(a) * rr, c.y, c.z + Math.sin(a) * rr));
                }
                Vfx.Fx fx = RuneProps.fx(level, c, FALL + HAIL_STUCK + HAIL_SINK + 4);
                for (Vec3 s : spots) hailBlade(sp, level, fx, s, glow, core);
                // 떨어지는 칼날의 바람 소리 (첫 번은 크게)
                SkillFx.play(level, "item.trident.throw", c, ii == 0 ? 1.4 : 1.1, 1.2);
                RuneKit.later(FALL, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    ServerLevel l2 = sp.level();
                    for (Vec3 s : spots) {
                        Vec3 g = new Vec3(s.x, RuneKit.groundAt(l2, s, s.y), s.z);
                        RuneKit.rfx(l2, "crack", V.up(g, 0.04), Opt.of("dir", V.UP, "size", 0.55, "life", 0.5, "color", glow, "alpha", 0.9, "fade", 0.5));
                        SkillFx.sparks(l2, V.up(g, 0.2), core, 3, 3);
                    }
                    RuneKit.zonePulse(l2, c, glow, radius, 0.3);
                    SkillFx.play(l2, "item.trident.hit_ground", c, 1.1, 1.0);
                    for (LivingEntity e : Kit.around(sp, c, radius)) RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat());
                    if (ii == hits - 1) {
                        SkillFx.play(l2, "wind_charge.burst", c, 1.2, 0.8);
                        RuneBoost.impact(l2, c, glow, core, radius, r.grade()); // 아래 등급 터짐: 마지막 우박 — 몸 둘레
                    }
                });
            });
        }
        return true;
    }

    /** 칼날 하나: 자리 at 위 5칸에서 칼끝을 아래로 나타나 FALL 틱 동안 빨라지며 떨어져 칼끝이 땅속 0.4칸 → HAIL_STUCK 틱 꽂혀 있다가 가라앉으며 사라짐 */
    private static void hailBlade(ServerPlayer sp, ServerLevel level, Vfx.Fx fx, Vec3 at, double[] glow, double[] core) {
        Vec3 g = new Vec3(at.x, RuneKit.groundAt(level, at, at.y), at.z);
        double rest = HAIL_LEN / 2 - 0.4; // 가운데 높이 (칼끝 땅속 0.4칸)
        double sky = 5.0 + Kit.rand() * 1.2;
        double s = RuneProps.scaleFor(HAIL_LEN, RuneProps.SWORD_LEN);
        Vec3 toMe = V.flat(V.sub(sp.position(), g));
        Vec3 side = RuneKit.turn(V.len(toMe) > 1e-3 ? V.unit(toMe) : new Vec3(0, 0, 1), 90);
        // 조금 비스듬히 (우박처럼 — 바깥에서 안쪽으로 6 ~ 14°)
        Vec3 tilt = V.unit(V.add(V.DOWN, V.scale(V.unit(V.len(toMe) > 1e-3 ? toMe : new Vec3(0, 0, 1)), -0.12 - Kit.rand() * 0.12)));
        Vec3 start = V.add(V.up(g, rest), tilt, -sky);
        Vfx.Prop blade = RuneProps.prop(fx, RuneProps.SWORD, start, RuneProps.SWORD_MID, RuneProps.along(tilt, side), s * 0.3, glow, core);
        fx.on(1, () -> blade.size(s, 2));
        for (int i = 1; i <= FALL; i++) {
            double u = (double) i / FALL;
            final Vec3 p = V.add(V.up(g, rest), tilt, -sky * (1 - u * u));
            fx.on(i, () -> blade.move(p, 1));
        }
        fx.on(FALL + HAIL_STUCK, () -> blade.to(V.up(g, rest - 1.2), null, s * 0.6, HAIL_SINK));
        fx.on(FALL + HAIL_STUCK + HAIL_SINK, blade::remove);
        // 떨어지는 길의 가는 빛 (칼끝 뒤 — 2D 날아가는 빛줄기: 질풍 묶음의 돌진 줄기)
        RuneKit.flyStreak(level, start, V.up(g, rest), sky / (FALL / 20.0), glow, core, 0.6, 0.2);
    }

    // ------------------------------------------------------------ 룬 재블린 (영웅)

    /**
     * 2026-11-17 룬 재블린 (영웅 — "빛의 창을 만들어 던짐 · 처음 맞은 적에 꽂혀 터짐"): 옛 저격 (snipe — 활 시위를 당기는 동작 · 석궁 소리 · 빛 화살) 을 빌려 썼는데
     * 무기와 상관없는 룬이 활을 당기는 모습이라 설명과 어긋났음 (CI 44046252216 영상) → charge 틱 동안 오른 어깨 위에 3D 빛의 창 (RuneStorm.Spear3d — 창 모델 ·
     * 계열 빛) 이 빛을 모으며 커짐 (촉 반짝임 · 둘레 빛 알갱이) → 던짐 (창 던지기 동작 rn_thunder_spear): 겨눈 적 (range 칸 · cone 도) 에 꽂히는 틱에 터짐 — 위력 × mult
     * (단일) · 꽂힌 자리 섬광 · 고리 · 발밑 충격파. 창은 꽂힌 채 떨리다 흩어짐 (Spear3d)
     */
    static boolean javelin(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        LivingEntity target = Kit.pickTarget(sp, k.num("range"), k.num("cone"));
        if (target == null) {
            mmorpg.fabric.Notify.warn(sp, "§c" + k.str("name") + ": §7" + k.text("range") + "칸 안에 대상이 없습니다");
            return false;
        }
        ServerLevel level = sp.level();
        double[][] cs = RuneKit.lookColors(k, "bow");
        double[] glow = cs[0];
        double[] core = cs[1];
        int charge = (int) k.num("charge");
        String life = RuneKit.lifeToken(sp);
        RuneStorm.Spear3d spear = new RuneStorm.Spear3d(sp, charge, k.numOr("len", 3.0), glow, core);
        double glint = RuneKit.RFX.get("glint")[0];
        SkillFx.play(level, "item.trident.return", sp.position(), 1.3, 0.9);
        for (int t = 2; t < charge; t += 3) {
            final int tt = t;
            RuneKit.later(tt, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 tip = spear.tipNow();
                double q = (double) tt / charge;
                RuneKit.rfx(level, "prop", tip, Opt.of("size", 0.12 + 0.22 * q, "life", 0.18, "cell", glint, "spin", tt * 20.0, "color", core, "alpha", 0.9));
                SkillFx.sparks(level, V.lerp(spear.gripNow(), tip, 0.55), glow, 3, 1.5);
            });
        }
        RuneKit.later(Math.max(0, charge - 5), () -> {
            if (RuneKit.sameLife(sp, life)) SkillFx.play(level, "item.trident.riptide_1", sp.position(), 1.5, 0.6);
        });
        RuneKit.later(charge, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 shown = V.up(V.add(sp.getEyePosition(), Kit.facingOf(sp), 0.5), 0.25);
            boolean alive = !target.isRemoved() && target.isAlive();
            Vec3 to = alive ? Kit.bodyPoint(target) : V.add(shown, sp.getViewVector(1F), 8);
            Vec3 d = V.unit(V.sub(to, shown));
            double len = V.len(V.sub(to, shown));
            double ray = RuneKit.rayLength(level, shown, d, len);
            boolean open = alive && ray >= len - 0.6;
            int fly = spear.thrown(shown, d, Math.max(1, open ? len + 0.3 : ray), 60);
            SkillFx.play(level, "item.trident.throw", shown, 0.9, 1.0);
            SkillFx.ring(level, V.add(shown, d, 1.2), d, glow, 0.6, 0.25, 3);
            RuneKit.later(fly, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 at = open && !target.isRemoved() ? Kit.bodyPoint(target) : V.add(shown, d, Math.max(1, ray));
                if (!open || target.isRemoved()) {
                    SkillFx.burst(level, at, glow, 1.0, 0.25);
                    SkillFx.play(level, "item.trident.hit", at, 1.2, 0.6);
                    return;
                }
                if (RuneKit.runeHit(sp, target, P, k.num("mult"), r.stat()) <= 0) return;
                RuneKit.knock(target, d, k.numOr("push", 0), 0.3);
                // 꽂혀 터짐: 섬광 · 세운 고리 · 발밑 충격파 · 불티
                SkillFx.burst(level, at, core, 2.2, 0.35);
                SkillFx.burst(level, at, glow, 1.6, 0.45);
                SkillFx.ring(level, at, d, glow, 1.6, 0.35, 3.5);
                RuneKit.rfx(level, "prop", V.add(at, d, -0.4), Opt.of("size", 1.4, "life", 0.35, "cell", glint, "spin", 45.0, "color", RuneKit.WHITE, "grow", 0.5,
                        "fade", 0.6));
                RuneKit.zonePulse(level, target.position(), glow, 2.4, 0.45);
                RuneBoost.impact(level, target.position(), glow, core, 2.6, r.grade());
                SkillFx.sparks(level, at, core, 12, 6);
                SkillFx.play(level, "item.trident.hit", at, 0.8, 1.0);
                SkillFx.play(level, "firework.blast", at, 0.7, 0.9);
            });
        });
        return true;
    }
}
