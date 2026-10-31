package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.Notify;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 궁수 룬 (runes.js "궁수" — 연두: 멀리서 꿰뚫고, 묶고, 빠지며 쏨): 관통 화살 · 속박 화살 · 도약 사격 · 저격 · 천궁 */
final class RuneBow {
    private RuneBow() {}

    static void register() {
        RuneCast.register("pierceArrow", RuneBow::pierceArrow);
        RuneCast.register("pinArrow", RuneBow::pinArrow);
        RuneCast.register("backstep", RuneBow::backstep);
        RuneCast.register("snipe", RuneBow::snipe);
        RuneCast.register("skyBow", RuneBow::skyBow);
    }

    /** 대상이 없어 취소 (재사용 대기 없음) */
    private static boolean noTarget(ServerPlayer sp, Obj k) {
        Notify.warn(sp, "§c" + k.str("name") + ": §7" + k.text("range") + "칸 안에 대상이 없습니다");
        return false;
    }

    /** 관통 화살: 시선 방향 range 칸 (벽 앞까지) 을 꿰뚫는 화살, 선 가까이 width 칸의 적 모두 — 빛의 화살 그림 + 화살이 지나간 자리에 바람 고리 */
    static boolean pierceArrow(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 view = sp.getViewVector(1F);
        Vec3 from = RuneKit.bowHand(sp);
        double len = Math.max(1, RuneKit.rayLength(level, from, view, k.num("range")));
        double speed = 60;
        double[][] cs = RuneKit.colorOf("bow");
        double[] glow = cs[0];
        double[] core = cs[1];
        double s0 = Math.min(1.4, len);
        // 촉이 벽 (len) 에서 멈추게: 가운데가 len − 0.76 까지
        RuneKit.rfx2(level, "arrow", V.add(from, view, s0), Opt.of("dir", view, "speed", speed, "size", 0.95, "life", Math.max(0.05, (len - s0 - 0.76) / speed)),
                glow, RuneKit.WHITE);
        SkillFx.fx(level, "rpg:light_cut", V.add(from, view, len / 2), Opt.of("dir", view, "size", len / 2, "thick", 0.2, "life", 0.55, "delay", 0.3, "color", glow,
                "alpha", 0.85));
        SkillFx.ring(level, V.add(from, view, 0.6), view, glow, 0.38, 0.22, 0);
        // 바람 고리: 화살이 지나갈 때 3칸마다 (화살을 감싸고 퍼짐)
        for (int s = 3; s < len; s += 3) {
            final int ss = s;
            RuneKit.later(Math.round(ss / speed * 20), () -> SkillFx.ring(level, V.add(from, view, ss), view, ss % 6 != 0 ? glow : core, 0.42, 0.3, 1.5));
        }
        SkillFx.play(level, "crossbow.shoot", from, 1.2, 0.9);
        SkillFx.play(level, "random.bow", from, 1.5, 0.6);
        for (Kit.LineHit h : RuneKit.lineTargets(sp, from, view, len, k.num("width"))) {
            LivingEntity e = h.e();
            RuneKit.later(Math.round(h.t() / speed * 20), () -> {
                if (sp.isRemoved() || e.isRemoved()) return;
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                    RuneKit.hitFlash(level, Kit.bodyPoint(e), view, glow, core, 0.7);
                    SkillFx.play(level, "random.bowhit", Kit.bodyPoint(e), 1.2, 0.8);
                }
            });
        }
        return true;
    }

    /**
     * 속박 화살: 대상 (range 칸, cone 도 안) 에게 화살 → 맞으면 root 틱 동안 땅에 묶임 (움직이지 못함) — 빛의 화살 셋이 둘레에 비스듬히 꽂히고
     * 화살마다 빛의 사슬 (rpg:rfx_chain) 이 대상의 몸으로 이어짐
     */
    static boolean pinArrow(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        LivingEntity target = Kit.pickTarget(sp, k.num("range"), k.num("cone"));
        if (target == null) return noTarget(sp, k);
        ServerLevel level = sp.level();
        double[][] cs = RuneKit.colorOf("bow");
        double[] glow = cs[0];
        double[] core = cs[1];
        Vec3 from = RuneKit.bowHand(sp);
        int ticks = RuneKit.flyArrow(level, from, Kit.bodyPoint(target), 40, glow, RuneKit.WHITE, 0.8, 1.2);
        SkillFx.play(level, "crossbow.shoot", from, 1.0, 0.9);
        RuneKit.later(ticks, () -> {
            if (sp.isRemoved() || target.isRemoved()) return;
            if (RuneKit.runeHit(sp, target, P, k.num("mult"), r.stat()) <= 0) return;
            int root = (int) k.num("root");
            RuneKit.effect(target, "slowness", root, 10);
            Vec3 g = target.position();
            double hold = root / 20.0;
            double pad = Kit.bodyPad(target);
            Vec3 chest = Kit.bodyPoint(target);
            SkillFx.rune(level, g, Opt.of("size", 1.5 + pad, "life", hold, "color", glow, "alpha", 0.9, "rate", 60.0));
            // 사슬이 모이는 허리의 빛 고리 (묶인 동안 돎)
            SkillFx.rune(level, V.up(g, Math.max(0.7, chest.y - g.y - 0.06)), Opt.of("size", 0.62 + pad, "life", hold, "color", core, "alpha", 0.95, "rate", 220.0));
            for (int i = 0; i < 3; i++) {
                final int ii = i;
                double a = (i / 3.0) * Math.PI * 2 + 0.4;
                double rr = 1.05 + pad;
                Vec3 p = new Vec3(g.x + Math.cos(a) * rr, g.y, g.z + Math.sin(a) * rr);
                // 꽂힌 화살 (말뚝처럼): 촉이 땅속 · 깃이 바깥 위로 기움 — 깃에서 몸으로 사슬
                Vec3 tilt = V.unit(new Vec3(-Math.cos(a) * 0.45, -1, -Math.sin(a) * 0.45));
                double sz = 0.62;
                RuneKit.later(ii, () -> {
                    RuneKit.rfx2(level, "arrow", V.add(p, tilt, -sz * 0.75), Opt.of("dir", tilt, "size", sz, "life", hold - ii / 20.0, "fade", 0.15), glow, core);
                    SkillFx.sparks(level, V.up(p, 0.1), core, 3, 3);
                    // 빛의 사슬: 화살 깃 → 몸
                    Vec3 nock = V.add(p, tilt, -sz * 1.5);
                    Vec3 cd = V.sub(chest, nock);
                    double cl = V.len(cd);
                    if (cl > 0.3) {
                        RuneKit.rfx2(level, "chain", V.add(nock, cd, 0.5), Opt.of("dir", V.unit(cd), "size", cl / 2, "life", hold - ii / 20.0, "fade", 0.15, "grow", 0.0,
                                "alpha", 0.95), glow, core);
                    }
                });
            }
            RuneKit.hitFlash(level, chest, V.unit(V.sub(chest, from)), glow, core, 0.8);
            SkillFx.play(level, "random.bowhit", g, 0.8, 1);
            SkillFx.play(level, "use.chain", g, 0.8, 1);
            RuneKit.later(2, () -> SkillFx.play(level, "use.chain", g, 1.1, 0.8));
        });
        return true;
    }

    /**
     * 도약 사격: 뒤로 back 칸 뛰고 (벽 앞까지 — 0.49.0 air 틱 동안 포물선으로 날아감) 앞쪽 cone 도 · range 칸의 적 arrows 명에게 한 발씩 (대상이 없어도 뜀) —
     * 뛰는 자리에 바람 회오리 · 바람 줄기, 화살은 빛의 화살 그림
     */
    static boolean backstep(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 start = sp.position();
        Vec3 f = V.flat(sp.getViewVector(1F));
        Vec3 back = new Vec3(-f.x, 0, -f.z);
        double dist = RuneKit.dashDistance(sp, back, k.num("back"), 1.1, 1.8); // 한 칸 턱은 넘음
        Vec3 flatEnd = V.add(start, back, dist);
        Vec3 end = new Vec3(flatEnd.x, RuneKit.groundAt(level, flatEnd, start.y), flatEnd.z);
        double[][] cs = RuneKit.colorOf("bow");
        double[] glow = cs[0];
        double[] core = cs[1];
        double[] wind = {0.86, 1.0, 0.8};
        RuneKit.zonePulse(level, start, glow, 1.8, 0.35);
        SkillFx.sparks(level, V.up(start, 0.3), core, 6, 4);
        // 박차고 나간 자리의 바람 회오리 (뛴 뒤라 1인칭 화면 앞 멀리 — 벽에 막혀 2.5칸도 못 뛰면 그리지 않음: 카메라가 회오리 안에 들어감)
        if (dist > 2.5) RuneKit.vortexAt(level, start, 1.0, wind, Opt.of("life", 0.45, "alpha", 0.75, "fade", 0.5, "grow", 0.2));
        for (int i = 0; i < 4; i++) {
            RuneKit.streak(level, V.up(V.add(start, back, dist * i / 4), 1.0), V.up(V.add(start, back, dist * (i + 1) / 4), 1.0), glow, 0.18 - i * 0.03, 0.35,
                    0.85, 0);
        }
        Vec3 side = V.rightOf(f);
        for (int sd : new int[] {-1, 1}) {
            RuneKit.streak(level, V.up(V.add(V.add(start, back, dist * 0.2), side, sd * 0.6), 0.5), V.up(V.add(V.add(start, back, dist * 0.8), side, sd * 0.6), 0.6),
                    wind, 0.08, 0.3, 0.7, 0);
        }
        SkillFx.play(level, "mob.breeze.jump", start, 1.1, 1);
        SkillFx.play(level, "mob.enderdragon.flap", start, 1.6, 0.5);
        // 0.49.0 뒤로 뛰는 몸이 포물선으로 날아감 (다른 도약 룬과 같은 RuneKit.leap) — 쏘는 화살은 나는 동안 (2틱부터)
        RuneKit.leap(sp, dist > 0.2 ? end : start, 1.3, (int) k.numOr("air", 6), glow, core, () -> SkillFx.play(level, "entity.player.big_fall", sp.position(), 1.4, 0.4));
        RuneKit.later(2, () -> {
            if (sp.isRemoved()) return;
            List<LivingEntity> list = RuneKit.coneTargets(sp, k.num("range"), k.num("cone"), (int) k.num("arrows"));
            for (int i = 0; i < list.size(); i++) {
                final int ii = i;
                LivingEntity e = list.get(i);
                RuneKit.later(ii * k.num("gap"), () -> {
                    if (sp.isRemoved() || e.isRemoved()) return;
                    Vec3 from = RuneKit.bowHand(sp);
                    int ticks = RuneKit.flyArrow(level, from, Kit.bodyPoint(e), 45, glow, RuneKit.WHITE, 0.7, 1.2);
                    SkillFx.ring(level, V.add(from, sp.getViewVector(1F), 0.7), V.unit(V.sub(Kit.bodyPoint(e), from)), glow, 0.3, 0.18, 0);
                    SkillFx.play(level, "random.bow", from, 1.3 + ii * 0.1, 0.8);
                    RuneKit.later(ticks, () -> {
                        if (!sp.isRemoved() && !e.isRemoved() && RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                            RuneKit.hitFlash(level, Kit.bodyPoint(e), f, glow, core, 0.8);
                        }
                    });
                });
            }
        });
        return true;
    }

    /**
     * 저격: 대상 (range 칸, cone 도 안) 을 charge 틱 동안 조준 — 손에 빛나는 화살 (rpg:rfx_arrow) 이 모이고 조준선 · 조여드는 고리 → 강한 한 발 (거대한 화살), 밀쳐냄.
     * 그 사이 벽 뒤로 숨으면 벽에 막힘
     */
    static boolean snipe(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        LivingEntity target = Kit.pickTarget(sp, k.num("range"), k.num("cone"));
        if (target == null) return noTarget(sp, k);
        ServerLevel level = sp.level();
        double[][] cs = RuneKit.colorOf("bow");
        double[] glow = cs[0];
        double[] core = cs[1];
        double charge = k.num("charge");
        double glint = RuneKit.RFX.get("glint")[0];
        for (int t = 0; t < charge; t += 2) {
            final int tt = t;
            RuneKit.later(tt, () -> {
                if (sp.isRemoved() || target.isRemoved()) return;
                Vec3 from = RuneKit.bowHand(sp);
                Vec3 to = Kit.bodyPoint(target);
                Vec3 d = V.unit(V.sub(to, from));
                double q = (tt + 2) / charge;
                RuneKit.streak(level, V.add(from, d, 0.4), to, glow, 0.035, 0.12, 0.75, 0);
                SkillFx.ring(level, V.add(to, d, -0.4), d, core, Math.max(0.35, 1.2 - tt * 0.05), 0.12, 0);
                // 손 앞에 모이는 화살 (조준선 위, 1.6칸 앞 — 점점 밝게) + 촉의 반짝임
                RuneKit.rfx2(level, "arrow", V.add(from, d, 1.6), Opt.of("dir", d, "size", 0.75, "life", 0.13, "alpha", 0.4 + 0.6 * q), glow, RuneKit.WHITE);
                RuneKit.rfx(level, "prop", V.add(from, d, 2.3), Opt.of("size", 0.12 + 0.18 * q, "life", 0.13, "cell", glint, "spin", tt * 20.0, "color", core,
                        "alpha", 0.9));
            });
        }
        SkillFx.play(level, "crossbow.loading.start", sp.position(), 0.8, 1);
        RuneKit.later(charge - 4, () -> {
            if (!sp.isRemoved()) SkillFx.play(level, "crossbow.loading.end", sp.position(), 0.8, 1);
        });
        RuneKit.later(charge, () -> {
            if (sp.isRemoved() || target.isRemoved()) return;
            Vec3 from = RuneKit.bowHand(sp);
            Vec3 to = Kit.bodyPoint(target);
            Vec3 d = V.unit(V.sub(to, from));
            double len = V.len(V.sub(to, from));
            double ray = RuneKit.rayLength(level, from, d, len);
            boolean open = ray >= len - 0.6;
            Vec3 end = open ? to : V.add(from, d, ray);
            int ticks = RuneKit.flyStreak(level, from, end, 90, glow, core, 1.6, 0.5);
            double len2 = V.len(V.sub(end, from));
            double life = Math.max(0.05, ticks / 20.0);
            if (len2 > 2) {
                RuneKit.rfx2(level, "arrow", V.add(from, d, 1.6), Opt.of("dir", d, "speed", Math.max(0, len2 - 2.2) / life, "size", 1.2, "life", life), glow,
                        RuneKit.WHITE);
            }
            SkillFx.fx(level, "rpg:light_cut", V.add(from, V.sub(end, from), 0.5), Opt.of("dir", d, "size", len2 / 2, "thick", 0.3, "life", 0.7, "delay", 0.45,
                    "color", glow, "alpha", 0.9));
            SkillFx.ring(level, V.add(from, d, 0.9), d, glow, 0.6, 0.28, 2);
            SkillFx.ring(level, V.add(from, d, 1.5), d, core, 0.4, 0.22, 4);
            SkillFx.play(level, "crossbow.shoot", from, 0.6, 1);
            SkillFx.play(level, "wind_charge.burst", from, 1.4, 0.6);
            SkillFx.shake(sp, 0.12, 0.15);
            RuneKit.later(ticks, () -> {
                if (sp.isRemoved()) return;
                if (!open || target.isRemoved()) {
                    SkillFx.burst(level, end, glow, 0.8, 0.2);
                    return;
                }
                if (RuneKit.runeHit(sp, target, P, k.num("mult"), r.stat()) <= 0) return;
                RuneKit.knock(target, d, k.num("push"), 0.3);
                Vec3 at = Kit.bodyPoint(target);
                SkillFx.burst(level, at, glow, 2.0, 0.35);
                RuneKit.rfx(level, "prop", V.add(at, d, -0.4), Opt.of("size", 1.4, "life", 0.35, "cell", glint, "spin", 45.0, "color", RuneKit.WHITE, "grow", 0.5,
                        "fade", 0.6));
                SkillFx.fx(level, "rpg:slash_x", V.add(at, d, -0.2), Opt.of("dir", d, "size", 1.4, "life", 0.3, "color", core, "alpha", 1.0));
                RuneKit.zonePulse(level, target.position(), glow, 2.0, 0.4);
                SkillFx.sparks(level, at, core, 10, 6);
                SkillFx.play(level, "random.bowhit", at, 0.6, 1);
                SkillFx.play(level, "item.trident.hit", at, 0.8, 1);
            });
        });
        return true;
    }

    /**
     * 천궁: 앞쪽 하늘에 하늘의 활 (rpg:rfx_bow) 이 나타나 시위를 당긴 뒤 (draw 틱) 하늘로 쏘고, 주위 range 칸의 적 targets 명에게 차례로
     * 거대한 화살 (rpg:rfx_arrow) 이 떨어져 꽂힘 (맞은 곳 splash 칸, 적마다 한 번).
     * 초월 2차 강화: 새벽빛 하늘 · 활 뒤 하늘의 성광 문양 · 발밑 문양 → 번쩍임 · 하늘 문양에서 퍼지는 고리 · 꽂힌 자리의 빛기둥
     */
    static boolean skyBow(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        Vec3 c0 = sp.position();
        List<LivingEntity> near = RuneKit.nearestTo(Kit.around(sp, c0, k.num("range"), 6), c0);
        List<LivingEntity> list = new ArrayList<>(near.subList(0, Math.min(near.size(), (int) k.num("targets"))));
        if (list.isEmpty()) return noTarget(sp, k);
        ServerLevel level = sp.level();
        double[][] cs = RuneKit.colorOf("bow");
        double[] glow = cs[0];
        double[] core = cs[1];
        double[] gold = {1.0, 0.95, 0.6};
        Vec3 f0 = V.flat(sp.getViewVector(1F));
        Vec3 center = V.up(V.add(c0, f0, 4.6), 2.9); // 앞쪽 하늘 (1인칭 화면 위쪽 — 활 그림은 손잡이가 위쪽이라 가운데를 조금 낮게)
        double draw = k.num("draw");
        double gap = k.num("gap");
        for (int t = 0; t <= draw; t += 4) {
            final int tt = t;
            // 시위를 당긴 정도 (빛 0~3)
            RuneKit.later(tt, () -> RuneKit.rfx2(level, "bow", center, Opt.of("size", 2.1, "life", tt >= draw ? 0.32 : 0.24, "cell",
                    Math.min(3, Math.floor(tt / draw * 4))), glow, gold, new int[] {0, RuneKit.BOW_CORE}, null));
        }
        SkillFx.rune(level, c0, Opt.of("size", 2.0, "life", draw / 20 + 0.3, "color", glow, "alpha", 0.8, "rate", 90.0, "grow", 0.2));
        SkillFx.play(level, "beacon.power", center, 1.6, 0.8);
        SkillFx.play(level, "crossbow.loading.start", c0, 0.5, 1);
        // 2차 강화 — 예고: 새벽빛 하늘 · 활 뒤 하늘의 성광 문양 (돎) · 발밑 문양
        RuneKit.ultSkyFor(sp, level, c0, "skyBow", (int) Math.round(draw + 4 + list.size() * gap + 30), null);
        Vec3 halo = V.add(V.up(center, 3.2), f0, 9);
        RuneKit.skyPlate(sp, level, "sigil", halo, 5.2, glow, gold, Opt.of("cell", (double) RuneKit.SIGIL.get("holy"), "life", (draw + 10) / 20, "spinRate", 35.0,
                "grow", 0.15, "alpha", 0.75, "fade", 0.3));
        RuneKit.domainSigil(level, c0, 4.2, "holy", glow, gold, Opt.of("life", (draw + 6) / 20, "spinRate", -40.0, "grow", 0.2, "alpha", 0.75));
        Set<UUID> hit = new HashSet<>();
        RuneKit.later(draw, () -> {
            RuneKit.rfx2(level, "arrow", V.up(center, 1.2), Opt.of("dir", V.UP, "speed", 40.0, "size", 1.4, "life", 0.3), glow, gold);
            RuneKit.rfx(level, "prop", V.up(center, 0.4), Opt.of("size", 1.3, "life", 0.3, "cell", (double) RuneKit.RFX.get("glint")[0], "color", RuneKit.WHITE,
                    "grow", 0.6, "fade", 0.6));
            SkillFx.burst(level, center, core, 1.6, 0.3);
            SkillFx.play(level, "random.bow", center, 0.5, 1);
            SkillFx.play(level, "crossbow.shoot", center, 0.5, 1);
            // 2차 강화 발동: 연두 금빛 번쩍임 (짧게 — fadeIn 0.03 · hold 0 · fadeOut 0.32초) · 하늘 문양에서 퍼지는 고리 (시전자 쪽을 봄)
            RuneKit.ultFlashFor(sp, level, c0, "skyBow", "flash", Opt.of("time", new double[] {0.03, 0, 0.32}));
            RuneKit.novaRing(level, halo, 1.6, 3.2, glow, gold, Opt.of("dir", RuneKit.towardEye(sp, halo), "life", 0.5));
            for (int i = 0; i < list.size(); i++) {
                final int ii = i;
                LivingEntity e = list.get(i);
                RuneKit.later(4 + ii * gap, () -> {
                    if (sp.isRemoved() || e.isRemoved()) return;
                    Vec3 g = e.position();
                    int drop = 5;
                    RuneKit.streak(level, V.up(g, 14), V.up(g, 0.1), core, 0.05, drop / 20.0, 0.6, 0); // 떨어질 자리 빛줄기
                    RuneKit.rfx2(level, "arrow", V.up(g, 14), Opt.of("dir", V.DOWN, "speed", 12.6 / (drop / 20.0), "size", 1.9, "life", drop / 20.0), glow, gold);
                    // 꽂힌 화살 (촉이 땅속): 떨어지는 화살이 끝나기 1틱 전 (깜빡임 없게)
                    RuneKit.later(drop - 1, () -> RuneKit.rfx2(level, "arrow", V.up(g, 1.5), Opt.of("dir", V.DOWN, "size", 1.9, "life", 0.95, "fade", 0.45), glow,
                            gold));
                    RuneKit.later(drop, () -> {
                        if (sp.isRemoved()) return;
                        Vec3 at = V.up(g, 0.9);
                        SkillFx.burst(level, at, glow, 2.0, 0.32);
                        SkillFx.burst(level, at, gold, 1.0, 0.24);
                        RuneKit.godRays(sp, level, g, 0, 1, glow, gold, Opt.of("h", 13.0, "life", 0.55, "skipNear", true)); // 화살이 꽂힌 자리의 빛기둥
                        RuneKit.zonePulse(level, g, glow, k.num("splash"), 0.4);
                        for (int j = 0; j < 6; j++) {
                            Vec3 d = RuneKit.turn(new Vec3(1, 0, 0), j * 60 + ii * 17);
                            SkillFx.fx(level, "rpg:light_cut", V.add(V.up(g, 0.08), d, 1.0), Opt.of("dir", d, "size", 0.9, "thick", 0.24, "life", 0.7, "delay", 0.45,
                                    "color", glow, "alpha", 0.95));
                        }
                        SkillFx.sparks(level, at, core, 8, 5);
                        SkillFx.play(level, "random.bowhit", g, 0.6, 1);
                        SkillFx.play(level, "random.explode", g, 1.6, 0.35);
                        for (LivingEntity x : Kit.around(sp, g, k.num("splash"), 3)) {
                            if (hit.contains(x.getUUID())) continue;
                            if (RuneKit.runeHit(sp, x, P, k.num("mult"), r.stat()) > 0) hit.add(x.getUUID());
                        }
                    });
                });
            }
        });
        return true;
    }
}
