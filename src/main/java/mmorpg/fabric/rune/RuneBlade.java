package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Guards;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 검사 룬 (runes.js "검사" — 청백: 발도 · 검기 · 반격 · 연속 베기): 발도 일섬 · 검기 날리기 · 반격 자세 · 삼단베기 · 천지개벽 */
final class RuneBlade {
    private RuneBlade() {}

    /** 반격 자세의 분신 몸 (청백 계열 — 풀밭 위에서도 보이게) */
    private static final double[] SHADE = {0.16, 0.42, 0.5};
    /** 삼단베기의 베인 자국 기울기 (가로 베기 · 세로 베기 · 찌르기) */
    private static final double[] CUT_ANGLE = {0, 90, 20};

    static void register() {
        RuneCast.register("quickDraw", RuneBlade::quickDraw);
        RuneCast.register("swordWave", RuneBlade::swordWave);
        RuneCast.register("counterStance", RuneBlade::counterStance);
        RuneCast.register("tripleCut", RuneBlade::tripleCut);
        RuneCast.register("heavenSplit", RuneBlade::heavenSplit);
    }

    /**
     * 발도 일섬: 칼을 뽑으며 앞을 한 번에 벰 — 칼을 뽑는 번쩍임 (rpg:rfx_prop 반짝임) + 앞으로 퍼지는 가로 초승달 검기 (rpg:rfx_wave),
     * 칼을 거두면 (6틱 뒤) 베인 적에게 늦게 베인 자국이 한 번 더 그어짐
     */
    static boolean quickDraw(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 head = sp.getEyePosition();
        Vec3 f = V.flat(sp.getViewVector(1F));
        double[][] cs = RuneKit.colorOf("blade");
        double[] glow = cs[0];
        double[] core = cs[1];
        // 칼집에서 뽑는 빛
        RuneKit.rfx(level, "prop", V.add(V.up(head, -0.55), V.rightOf(f), 0.45), Opt.of("size", 0.35, "life", 0.18, "cell", (double) RuneKit.RFX.get("glint")[0],
                "spin", 20.0, "color", RuneKit.WHITE, "grow", 0.8, "fade", 0.6));
        // 2026-11-13 3D 룬 검 (3.2칸 — 사용자 "이재 다른 룬들도 모션하고 이펙트 추가하자"): 왼 허리 뒤에서 뽑혀 앞을 지나 (1틱 — 동작 rn_quick_draw 와 같은 때)
        // 오른쪽 끝까지 휩쓸고 머물다 칼을 거둘 때 (5) 작아져 사라짐
        RuneSwing.sword(sp, RuneKit.lifeToken(sp), 1.05, 0.6, 3.2, 4, -130, 105, 0, 2, 3, "out", glow, core);
        SkillFx.fx(level, "rpg:light_cut", V.up(V.add(head, f, 2.0), -0.25), Opt.of("dir", V.rightOf(f), "size", 2.8, "thick", 0.2, "life", 0.32, "delay", 0.04,
                "color", core, "alpha", 1.0));
        RuneKit.arc(level, V.up(V.add(head, f, 1.4), -0.3), f, Opt.of("shape", "arc", "flip", false, "size", 2.7, "life", 0.24, "speed", 3.0, "spin", -6.0,
                "color", glow, "aura", core));
        RuneKit.rfx2(level, "wave", V.up(V.add(head, f, 2.3), -0.35), Opt.of("dir", f, "speed", 6.0, "size", 1.5, "life", 0.22, "grow", 0.35, "fade", 0.6, "spin", 4.0,
                "alpha", 0.9), glow, RuneKit.WHITE);
        SkillFx.play(level, "random.anvil_land", head, 2.0, 0.35);
        SkillFx.play(level, "item.spear.attack_miss", head, 1.3, 0.9);
        // 0.50.0 2D 강화 (아래 등급 터짐): 벤 부채꼴의 가운데 땅 (앞 range 의 반, 벽이 있으면 벽 앞) — 반지름 = range
        Vec3 mid = V.add(sp.position(), f, RuneKit.dashDistance(sp, f, k.num("range") * 0.5));
        RuneBoost.impact(level, new Vec3(mid.x, RuneKit.groundAt(level, mid, mid.y), mid.z), glow, core, k.num("range"), r.grade());
        List<LivingEntity> cut = new ArrayList<>();
        for (LivingEntity e : RuneKit.coneTargets(sp, k.num("range"), k.num("cone"), 10)) {
            if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                Vec3 at = Kit.bodyPoint(e);
                Vec3 v = V.unit(V.sub(at, head));
                SkillFx.cutMark(level, at, v, -8, 0.1, glow, core, 1.0);
                if (cut.size() < 5) SkillFx.sparks(level, at, core, 4, 4); // 불꽃은 5명까지 (파티클 예산)
                cut.add(e);
            }
        }
        // 칼을 거둠: 늦게 그어지는 자국 (6명까지)
        Tasks.later(6, () -> {
            if (sp.isRemoved()) return;
            SkillFx.play(level, "random.click", sp.position(), 1.8, 0.6);
            Vec3 eye = sp.getEyePosition();
            for (LivingEntity e : cut.subList(0, Math.min(6, cut.size()))) {
                if (e.isRemoved()) continue;
                Vec3 at = Kit.bodyPoint(e);
                SkillFx.cutMark(level, at, V.unit(V.sub(at, eye)), 172, 0.06, RuneKit.WHITE, core, 1.2);
            }
        });
        return true;
    }

    /**
     * 검기 날리기: 초승달 검기가 speed 칸/초로 range 칸 (벽 앞까지) 날아가며 선 가까이 width 칸의 적 (적마다 한 번) —
     * 날아가는 초승달 검기 그림 (rpg:rfx_wave, 손 앞 1.6칸부터) + 지나간 자리에 남는 빛줄기
     */
    static boolean swordWave(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 view = sp.getViewVector(1F);
        Vec3 f = V.flat(view);
        Vec3 dir = V.unit(new Vec3(f.x, view.y * 0.3, f.z));
        Vec3 from = V.up(V.add(sp.position(), f, 0.6), 1.1);
        double len = Math.max(1, RuneKit.rayLength(level, from, dir, k.num("range")));
        double[][] cs = RuneKit.colorOf("blade");
        double[] glow = cs[0];
        double[] core = cs[1];
        double speed = k.num("speed");
        double s0 = Math.min(1.6, len);
        double life = Math.max(0.05, (len - s0) / speed);
        RuneKit.rfx2(level, "wave", V.add(from, dir, s0), Opt.of("dir", dir, "speed", speed, "size", k.num("width") * 0.8, "life", life, "fade", 0.15, "spin", -8.0),
                glow, RuneKit.WHITE);
        RuneKit.arc(level, V.add(from, dir, 1.0), dir, Opt.of("shape", "heavy", "flip", false, "size", 1.7, "life", 0.16, "speed", 0.0, "spin", -90.0, "color",
                glow, "aura", core)); // 휘두르는 순간
        // 2026-11-13 3D 룬 검 (3.4칸): 오른쪽 위 뒤에서 대각선으로 왼쪽 아래 앞까지 크게 휩쓸며 (2틱에 앞을 지남 — 동작 rn_sword_wave 와 같은 때) 검기를 날림
        RuneSwing.sword(sp, RuneKit.lifeToken(sp), 1.25, 0.6, 3.4, 48, 140, -75, 0, 3, 2, null, glow, core);
        for (int s = 2; s < len; s += 2) {
            double delay = (s / len) * (len / speed);
            SkillFx.fx(level, "rpg:light_cut", V.add(from, dir, s), Opt.of("dir", dir, "size", 1.1, "thick", 0.16, "life", delay + 0.3, "delay", delay, "color",
                    s % 4 != 0 ? glow : core, "alpha", 0.85));
        }
        SkillFx.play(level, "item.trident.throw", from, 1.3, 0.9);
        SkillFx.play(level, "item.spear.attack_miss", from, 1.6, 0.7);
        Map<Long, Integer> perTick = new HashMap<>(); // 틱 → 그 틱에 벤 적 수 (파티클 예산: 자국 6명 · 불꽃 3명까지)
        boolean[] boomed = {false}; // 0.50.0 2D 강화 (아래 등급 터짐): 터짐은 검기에 처음 베인 적에 한 번
        for (Kit.LineHit h : RuneKit.lineTargets(sp, from, dir, len, k.num("width"))) {
            LivingEntity e = h.e();
            RuneKit.later(Math.max(0, Math.round((h.t() / speed) * 20)), () -> {
                if (sp.isRemoved() || e.isRemoved()) return;
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                    Vec3 at = Kit.bodyPoint(e);
                    long now = Tasks.now();
                    int n = perTick.getOrDefault(now, 0);
                    perTick.put(now, n + 1);
                    if (n < 6) SkillFx.cutMark(level, at, dir, 70, 0.08, glow, core, 1.1);
                    if (n < 3) SkillFx.sparks(level, at, core, 5, 4);
                    if (!boomed[0]) { // 0.50.0 2D 강화 (아래 등급 터짐): 검기에 처음 베인 적의 발밑
                        boomed[0] = true;
                        Vec3 feet = e.position();
                        RuneBoost.impact(level, new Vec3(feet.x, RuneKit.groundAt(level, feet, feet.y), feet.z), glow, core, 2.0, r.grade());
                    }
                    RuneKit.knock(e, dir, 0.4, 0.15);
                }
            });
        }
        return true;
    }

    /**
     * 반격 자세: ticks 동안 받는 피해 -reduce, 때린 적 (멀리서 쏜 적 포함) 을 바로 반격 (적마다 한 번). 끝나면 반격하지 않은 앞쪽 적을 벰 —
     * 오른손 앞에 세운 빛의 칼 (rpg:rfx_sword 작게) · 발밑 검진, 반격은 그림자 분신 (rpg:rfx_shade 청백) 이 때린 적의 옆으로 날아가 벰
     * (한 틱에 여럿이 때리면 분신 · 참격은 둘까지)
     */
    static boolean counterStance(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        double[][] cs = RuneKit.colorOf("blade");
        double[] glow = cs[0];
        double[] core = cs[1];
        int ticks = (int) k.num("ticks");
        Set<UUID> countered = new HashSet<>();
        long until = Tasks.now() + ticks;
        String life = RuneKit.lifeToken(sp);
        Map<Long, Integer> perTick = new HashMap<>(); // 틱 → 그 틱의 반격 수 (여럿이 한꺼번에 때리면 분신 · 참격은 둘까지 — 파티클 예산)
        boolean[] boomed = {false}; // 0.50.0 2D 강화 (아래 등급 터짐): 터짐은 반격이 처음 들어간 적에 한 번
        GuardSword guard = guardSword(sp, life, ticks, glow, core);
        Guards.set(sp, ticks, k.num("reduce"), Guards.Reflect.fn((pl, attacker) -> {
            if (!RuneKit.sameLife(pl, life) || attacker == null || attacker.isRemoved() || countered.contains(attacker.getUUID()) || Tasks.now() > until) return;
            if (!Kit.canHit(pl, attacker)) return;
            countered.add(attacker.getUUID());
            guard.flash();
            ServerLevel d = pl.level();
            Vec3 eye = pl.getEyePosition();
            Vec3 at = Kit.bodyPoint(attacker);
            Vec3 v = V.unit(V.sub(at, eye));
            long now = Tasks.now();
            int nth = perTick.getOrDefault(now, 0);
            perTick.put(now, nth + 1);
            double xs = RuneKit.nearK(pl, at, 3, 0.4); // 바로 앞의 적이면 X 를 작게 (1인칭 화면을 덮지 않게)
            if (nth < 2) {
                // 분신: 내 옆 1칸 앞에서 때린 적의 옆 (내 쪽 선에서 비켜 — 가까운 적이어도 눈앞을 덮지 않게) 으로 미끄러져 가서 벰
                Vec3 fv = V.flat(v);
                int sd = countered.size() % 2 == 1 ? 1 : -1;
                Vec3 fromFeet = V.add(V.add(pl.position(), fv, 1.0), V.rightOf(fv), sd * 0.7);
                Vec3 toFeet = V.add(attacker.position(), V.rightOf(fv), sd * (0.9 + Kit.bodyPad(attacker)));
                double span = V.len(V.sub(toFeet, fromFeet));
                int wait = 0;
                if (span > 1.2) {
                    double tf = Math.max(0.08, span / 30);
                    wait = (int) Math.max(0, Math.round(tf * 20) - 1); // 멀리서 쏜 적: 분신이 닿을 때 벰 (1틱 겹침)
                    RuneKit.shadeClone(pl, d, fromFeet, fv, 1, SHADE, glow, Opt.of("life", tf, "fade", 0.2, "alpha", 0.8, "size",
                            0.95 * RuneKit.nearK(pl, V.up(fromFeet, 1), 3.5, 0.55), "vel", V.scale(V.sub(toFeet, fromFeet), 1 / tf)));
                }
                RuneKit.later(wait, () -> {
                    if (!pl.isRemoved()) {
                        RuneKit.shadeClone(pl, d, toFeet, V.scale(V.rightOf(fv), -sd), 2, SHADE, glow, Opt.of("life", 0.35, "fade", 0.5, "alpha", 0.9, "size",
                                0.95 * RuneKit.nearK(pl, V.up(toFeet, 1), 3.5, 0.55)));
                    }
                });
                RuneKit.arcFor(pl, d, V.add(eye, v, 1.3), v, Opt.of("shape", "thin", "flip", countered.size() % 2 == 0, "size", 2.0, "life", 0.2, "speed", 3.0, "spin", 35.0,
                        "color", glow, "aura", core));
                SkillFx.sparks(d, at, core, 8, 5);
            }
            SkillFx.fx(d, "rpg:slash_x", at, Opt.of("dir", v, "size", 1.4 * xs, "life", 0.3, "color", glow, "alpha", 1.0));
            SkillFx.play(d, "item.shield.block", at, 1.4, 0.8);
            SkillFx.play(d, "game.player.attack.strong", at, 1.2, 1);
            double got = RuneKit.runeHit(pl, attacker, P, k.num("mult"), r.stat());
            if (got > 0 && !boomed[0]) {
                // 0.50.0 2D 강화 (아래 등급 터짐): 반격이 처음 들어간 적의 발밑 — 맞을 때의 되받아침 (룬을 쓰는 중이 아님) 이라 arcFor 처럼 주인을 정해 줌 (1인칭 번쩍임 숨김)
                boomed[0] = true;
                Vec3 feet = attacker.position();
                int prev = RuneKit.enter(pl);
                try {
                    RuneBoost.impact(d, new Vec3(feet.x, RuneKit.groundAt(d, feet, feet.y), feet.z), glow, core, 2.5, r.grade());
                } finally {
                    RuneKit.leave(prev);
                }
            }
        }), null);
        RuneKit.selfCue(sp, glow, core, true);
        SkillFx.play(level, "random.anvil_land", sp.position(), 1.8, 0.4);
        SkillFx.play(level, "armor.equip_iron", sp.position(), 1.2, 0.8);
        // 자세: 2026-11-13 몸 앞에 가로로 뜬 3D 룬 검 (guardSword — 2D 빛의 칼 대신) + 10틱마다 발밑 검진
        for (int t = 0; t < ticks; t += 10) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                SkillFx.rune(sp.level(), sp.position(), Opt.of("size", 1.3, "life", 0.55, "color", glow, "alpha", 0.7, "rate", 0.0, "spin", tt * 3.0));
            });
        }
        // 끝: 모은 기운으로 앞을 벰 (반격한 적은 제외 → 적마다 한 번)
        RuneKit.later(ticks, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Vec3 eye = sp.getEyePosition();
            Vec3 f = V.flat(sp.getViewVector(1F));
            RuneKit.arc(d, V.up(V.add(eye, f, 1.5), -0.3), f, Opt.of("shape", "arc", "flip", true, "size", 2.8, "life", 0.26, "speed", 3.0, "spin", 8.0, "color", glow,
                    "aura", core));
            RuneKit.rfx2(d, "wave", V.up(V.add(eye, f, 2.4), -0.4), Opt.of("dir", f, "speed", 5.0, "size", 1.6, "life", 0.24, "grow", 0.3, "fade", 0.6, "spin", -4.0),
                    glow, RuneKit.WHITE);
            SkillFx.play(d, "item.spear.attack_miss", eye, 1.1, 0.9);
            int n = 0;
            for (LivingEntity e : RuneKit.coneTargets(sp, k.num("range"), k.num("cone"), 10)) {
                if (countered.contains(e.getUUID())) continue;
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                    Vec3 at = Kit.bodyPoint(e);
                    SkillFx.cutMark(d, at, V.unit(V.sub(at, eye)), 10, 0.1, glow, core, 1.0);
                    if (n++ < 4) SkillFx.sparks(d, at, core, 4, 4); // 불꽃은 4명까지 (파티클 예산)
                }
            }
        });
        return true;
    }

    /**
     * 삼단베기: gap 틱마다 가로 베기 → 세로 베기 (앞 cone 도 · range 칸) → 찌르기 (앞 thrust 칸 직선) — 베기마다 가로 · 세로 초승달 검기 (rpg:rfx_wave),
     * 2026-11-13 3D 검 (tripleSword) 이 베기마다 같은 길로 휘두르고 찌르기 끝까지 날아감. 0.49.0 찌르기까지 마치면 검세 (가하는 피해 + buff, buffTicks 틱)
     */
    static boolean tripleCut(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        double[][] cs = RuneKit.colorOf("blade");
        double[] glow = cs[0];
        double[] core = cs[1];
        String life = RuneKit.lifeToken(sp);
        int hits = (int) k.num("hits");
        tripleSword(sp, life, (int) k.num("gap"), k.num("thrust"), glow, core);
        SkillFx.every(hits, (int) k.num("gap"), i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 eye = sp.getEyePosition();
            Vec3 f = V.flat(sp.getViewVector(1F));
            List<LivingEntity> list;
            if (i < hits - 1) {
                boolean vertical = i % 2 == 1;
                RuneKit.arc(level, V.up(V.add(eye, f, 1.5), -0.3), f, Opt.of("shape", vertical ? "thin" : "arc", "flip", vertical, "size", vertical ? 2.4 : 2.6, "life",
                        0.24, "speed", 3.0, "spin", vertical ? 84.0 : -6.0, "color", glow, "aura", core));
                RuneKit.rfx2(level, "wave", V.up(V.add(eye, f, 2.4), vertical ? -0.1 : -0.4), Opt.of("dir", f, "speed", 5.0, "size", vertical ? 1.3 : 1.5, "life", 0.22,
                        "grow", 0.3, "fade", 0.6, "spin", vertical ? 90.0 : -4.0), glow, RuneKit.WHITE);
                SkillFx.play(level, "item.spear.attack_miss", eye, 1.2 + i * 0.15, 0.9);
                list = RuneKit.coneTargets(sp, k.num("range"), k.num("cone"), 10);
            } else {
                Vec3 from = V.up(V.add(sp.position(), f, 0.5), 1.1);
                double len = Math.max(1, RuneKit.rayLength(level, from, f, k.num("thrust")));
                // 2026-11-13 찌르는 칼은 3D 검 (tripleSword — 2D 빛의 칼 thrust 대신)
                SkillFx.fx(level, "rpg:light_cut", V.add(from, f, len / 2), Opt.of("dir", f, "size", len / 2, "thick", 0.18, "life", 0.3, "color", core, "alpha", 1.0));
                SkillFx.ring(level, V.add(from, f, len), f, glow, 0.9, 0.3, 0);
                // 0.50.0 2D 강화 (아래 등급 터짐): 마지막 찌르기 — 칼끝이 닿은 자리의 땅 (벽에 막혔으면 벽면에서 0.4칸 앞 — 벽 위로 올라가지 않게)
                Vec3 tipAt = V.add(from, f, len - 0.4);
                RuneBoost.impact(level, new Vec3(tipAt.x, RuneKit.groundAt(level, tipAt, tipAt.y - 1.1), tipAt.z), glow, core, 2.5, r.grade());
                SkillFx.play(level, "item.trident.throw", eye, 1.1, 1);
                list = new ArrayList<>();
                for (Kit.LineHit h : RuneKit.lineTargets(sp, from, f, len, 1.2)) list.add(h.e());
            }
            int n = 0;
            for (LivingEntity e : list) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                    Vec3 at = Kit.bodyPoint(e);
                    // 맞은 자리 (파티클 예산): 자국 10명 · 불꽃 4명까지
                    if (n < 10) SkillFx.cutMark(level, at, V.unit(V.sub(at, eye)), CUT_ANGLE[i % 3], 0.08, glow, core, 1.0);
                    if (n < 4) SkillFx.sparks(level, at, core, 4, 4);
                    n++;
                }
            }
            // 0.49.0 검세: 찌르기까지 마치면 buffTicks 동안 가하는 피해 + buff (몸에 검기 · 상태 줄)
            if (i == hits - 1 && k.numOr("buff", 0) > 0) RuneMarks.empower(sp, k.num("buff"), (int) k.num("buffTicks"), "검세", glow, core);
        });
        return true;
    }

    /** 쓴 사람 둘레 (가슴 높이 h) 를 기준으로 휘두르는 틀 — 틱마다 지금 자리 · 보는 쪽 */
    /** 2026-11-13 반격 자세의 3D 룬 검 (몸 앞에 가로로 떠서 막음) — 반격마다 번쩍 (flash) */
    static final class GuardSword {
        RuneSwords.Blade b;
        mmorpg.fabric.vfx.Vfx.Fx fx;
        boolean swinging;

        void flash() {
            if (b == null || swinging) return;
            b.grow(1.2, 1);
            int a = fx.age();
            fx.on(a + 2, () -> {
                if (!swinging) b.grow(1, 2);
            });
        }
    }

    /**
     * 반격 자세의 3D 룬 검 (2.6칸): 몸 앞 (0.85칸) 가슴 높이에 가로로 떠서 (칼끝 왼쪽 · 넓은 면이 앞 — 막는 방패처럼) 틱마다 몸을 따라옴 (조금 오르내림) →
     * ticks − 3 에 오른쪽으로 감기고 → ticks − 1 부터 크게 가로로 휩씀 (ticks 에 앞을 지남 — 끝 베기와 같은 때) → 머물다 사라짐
     */
    private static GuardSword guardSword(ServerPlayer sp, String life, int ticks, double[] glow, double[] core) {
        GuardSword g = new GuardSword();
        mmorpg.fabric.vfx.Vfx.Fx fx = RuneProps.fx(sp.level(), sp.position(), ticks + 16);
        g.fx = fx;
        RuneSwords.Frame fr = bodyFrame(sp, 1.15);
        java.util.function.Function<Vec3, Vec3> hilt = f -> V.add(V.add(fr.pivot(), f, 0.85), V.rightOf(f), 1.05);
        Vec3 f0 = fr.fwd();
        g.b = RuneSwords.blade(fx, hilt.apply(f0), V.scale(V.rightOf(f0), -1), f0, 2.6, 0.2, glow, core);
        fx.on(1, () -> g.b.grow(1, 3));
        fx.each(1, ticks - 4, 1, t -> {
            if (g.swinging || !RuneKit.sameLife(sp, life)) return;
            Vec3 f = fr.fwd();
            g.b.pose(V.up(hilt.apply(f), 0.05 * Math.sin(t * 0.3)), V.scale(V.rightOf(f), -1), f, 1);
        });
        fx.on(ticks - 3, () -> {
            g.swinging = true;
            g.b.grow(1, 1);
            Vec3 f = fr.fwd();
            Vec3 d = RuneSwing.planeDir(f, 4, 100);
            g.b.pose(V.add(fr.pivot(), d, 0.6), d, RuneSwing.planeFace(f, 4), 2);
        });
        RuneSwords.swing(g.b, ticks - 1, 3, fr, 0.6, (f, a) -> RuneSwing.planeDir(f, 4, a), f -> RuneSwing.planeFace(f, 4), 100, -125, "out");
        RuneSwing.retire(fx, g.b, sp, life, ticks + 4);
        return g;
    }

    static RuneSwords.Frame bodyFrame(ServerPlayer sp, double h) {
        return new RuneSwords.Frame() {
            @Override
            public Vec3 pivot() {
                return V.up(sp.position(), h);
            }

            @Override
            public Vec3 fwd() {
                return V.flat(sp.getViewVector(1F));
            }
        };
    }

    /**
     * 2026-11-13 삼단베기의 3D 검 (룬 전용 검 2판 · 3.4칸 — 사용자 "이재 다른 룬들도 모션하고 이펙트 추가하자"): 베기마다 내 몸 둘레를 크게 같은 길로 —
     * 가로 (0 — 오른쪽 앞에서 나타나 앞을 지나 왼쪽 뒤까지 휩씀) → 머리 위 뒤로 들림 → 세로 (gap — 하늘에서 앞 땅까지 내려침, 칼끝이 땅에 닿아 갈라짐) →
     * 오른쪽 뒤로 당김 → 찌르기 (2 × gap — 칼끝이 찌르는 끝까지 날아감) → 작아지며 흩어짐. 피해 · 틱은 그대로 (2D 초승달 · 검기도 그대로)
     */
    private static void tripleSword(ServerPlayer sp, String life, int gap, double thrust, double[] glow, double[] core) {
        tripleSword(sp, life, gap, thrust, new double[][] {glow, glow, glow}, new double[][] {core, core, core});
    }

    /**
     * 2026-11-13 마디마다 색이 다른 삼단 검 (삼원소 연격 — 불 가로 · 얼음 세로 · 번개 찌르기, RuneElement.triElement): glows · cores = 가로 · 세로 · 찌르기의
     * {빛, 속빛} — 다음 마디로 넘어가는 틱에 검을 다시 물들임
     */
    static void tripleSword(ServerPlayer sp, String life, int gap, double thrust, double[][] glows, double[][] cores) {
        double[] glow = glows[0];
        double[] core = cores[0];
        ServerLevel level = sp.level();
        mmorpg.fabric.vfx.Vfx.Fx fx = RuneProps.fx(level, sp.position(), 2 * gap + 12);
        double len = 3.4;
        RuneSwords.Frame chest = bodyFrame(sp, 1.15);
        RuneSwords.Frame high = bodyFrame(sp, 1.55);
        Vec3 f0 = chest.fwd();
        Vec3 d0 = RuneSwords.flatDir(f0, 70, 8);
        RuneSwords.Blade b = RuneSwords.blade(fx, V.add(chest.pivot(), d0, 0.7), d0, V.UP, len, 0.55, glow, core);
        fx.on(1, () -> b.grow(1, 2));
        // 가로: 오른쪽 앞 (70°) → 앞 (1틱) → 왼쪽 뒤 (−130°)
        RuneSwords.swing(b, 0, 4, chest, 0.7, (f, a) -> RuneSwords.flatDir(f, a, 8), f -> V.UP, 70, -130, "out");
        // 머리 위로 (왼쪽 위 → 하늘 뒤)
        fx.on(5, () -> {
            if (glows[1] != glows[0]) b.prop.tint(glows[1], cores[1]);
            Vec3 f = chest.fwd();
            Vec3 d = V.unit(V.add(V.scale(V.rightOf(f), -0.55), V.UP, 0.85));
            b.pose(V.add(high.pivot(), d, 0.6), d, V.rightOf(f), 2);
        });
        fx.on(gap - 3, () -> {
            Vec3 f = high.fwd();
            Vec3 d = RuneSwords.upDir(f, 118);
            b.pose(V.add(high.pivot(), d, 0.6), d, V.rightOf(f), 2);
        });
        // 세로: 하늘 뒤 (118°) → 앞 (gap) → 앞 땅 (−26° — 칼끝이 땅에 닿음)
        RuneSwords.swing(b, gap - 1, 3, high, 0.6, RuneSwords::upDir, V::rightOf, 118, -26, null);
        fx.on(gap + 2, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 tip = b.tip();
            Vec3 g = new Vec3(tip.x, RuneKit.groundAt(level, tip, sp.getY()), tip.z);
            RuneKit.glowCrack(level, g, 1.7, glows[1], RuneKit.WHITE, Opt.of("life", 1.0, "v", 1.0));
            SkillFx.burst(level, V.up(g, 0.2), glows[1], 0.9, 0.25);
            SkillFx.sparks(level, V.up(g, 0.3), cores[1], 5, 4);
        });
        // 오른쪽 뒤로 당김 (칼끝은 앞)
        fx.on(gap + 5, () -> {
            if (glows[2] != glows[1]) b.prop.tint(glows[2], cores[2]);
            Vec3 f = chest.fwd();
            Vec3 d = V.unit(V.add(f, V.UP, 0.06));
            b.pose(V.add(V.add(chest.pivot(), V.rightOf(f), 0.55), f, -0.9), d, V.UP, 3);
        });
        fx.on(2 * gap - 3, () -> {
            Vec3 f = chest.fwd();
            Vec3 d = V.unit(V.add(f, V.UP, 0.08));
            b.pose(V.add(V.add(chest.pivot(), V.rightOf(f), 0.62), f, -1.7), d, V.UP, 2);
        });
        // 찌르기: 칼끝이 찌르는 끝까지 (2틱) → 머묾 → 앞으로 밀리며 작아져 흩어짐
        fx.on(2 * gap - 1, () -> {
            Vec3 f = chest.fwd();
            Vec3 from = V.up(V.add(sp.position(), f, 0.5), 1.1);
            double reach = Math.max(1, RuneKit.rayLength(level, from, f, thrust));
            double h2t = len * RuneSwords.HILT_TO_TIP / RuneProps.SWORD_LEN;
            b.pose(V.add(from, f, Math.max(0.3, reach - h2t)), f, V.UP, 2);
        });
        fx.on(2 * gap + 3, () -> {
            b.grow(0.15, 4);
            b.prop.move(V.add(b.hilt(), b.dir(), 1.6), 4);
            SkillFx.sparks(level, b.tip(), cores[2], 6, 5);
        });
        fx.on(2 * gap + 7, b::remove);
    }

    /**
     * 천지개벽: charge 틱 동안 눈앞에 초승달 검기 (rpg:rfx_wave) 가 모여 커진 뒤, 거대한 검기가 speed 칸/초로 range 칸 (벽 앞까지) — 폭 width 칸의 적
     * (적마다 한 번), 밀쳐냄. 초월 2차 강화: 청백 하늘 · 발밑의 칼날 문양 · 앞쪽 하늘의 칼날 문양 → 청백 번쩍임 · 하늘이 둘로 갈라짐 · 하늘 문양의 섬광 ·
     * 검기가 지나간 땅이 갈라져 빛남
     */
    /**
     * 2026-11-13 천지개벽의 3D 거대한 검 (룬 전용 검 2판 · 9.5칸): 오른쪽 땅이 갈라지며 칼끝부터 솟아오름 (0 ~ 6) → 뒤로 눕혀 오른쪽 뒤로 당겨짐 (몸을 감는 동작과
     * 함께 — 떨림) → charge 틱에 몸 앞을 가로로 크게 휩씀 (오른쪽 뒤 → 앞 → 왼쪽 뒤, 앞을 지나는 때 = 거대한 검기가 나가는 틱) → 작아지며 빛으로 흩어짐
     */
    private static void heavenBlade(ServerPlayer sp, String life, int charge, double[] glow, double[] core) {
        ServerLevel level = sp.level();
        mmorpg.fabric.vfx.Vfx.Fx fx = RuneProps.fx(level, sp.position(), charge + 16);
        double len = 9.5;
        RuneSwords.Frame chest = bodyFrame(sp, 1.2);
        Vec3 f0 = chest.fwd();
        Vec3 rt0 = V.rightOf(f0);
        Vec3 base = V.add(V.add(sp.position(), rt0, 2.2), f0, -0.6);
        double gy = RuneKit.groundAt(level, base, sp.getY());
        Vec3 foot = new Vec3(base.x, gy, base.z);
        RuneSwords.Blade b = RuneSwords.blade(fx, V.up(foot, -6.0), V.UP, f0, len, 0.75, glow, core);
        RuneKit.glowCrack(level, foot, 2.2, glow, RuneKit.WHITE, Opt.of("life", 1.2, "v", 0.0));
        SkillFx.burst(level, V.up(foot, 0.3), glow, 1.6, 0.35);
        // 솟아오름 (칼끝부터 — 빨리 솟다가 느려짐)
        double[] ys = {-6.0, -3.4, -1.5, -0.3, 0.35};
        for (int i = 1; i < ys.length; i++) {
            final double y = ys[i];
            final int t = i * 2 - 1;
            fx.on(t, () -> {
                b.grow(1, 2);
                b.pose(V.up(foot, y), V.UP, f0, 2);
                if (t <= 3) SkillFx.sparks(level, V.up(foot, 0.4), core, 6, 5);
            });
        }
        // 뒤로 눕혀 오른쪽 뒤로 당겨짐 (넓은 면이 위 — 휩쓸 자세)
        fx.on(9, () -> {
            Vec3 f = chest.fwd();
            Vec3 d = V.unit(V.add(V.add(V.scale(V.UP, 0.75), V.rightOf(f), 0.4), f, -0.5));
            b.pose(V.add(V.up(sp.position(), 0.9), V.rightOf(f), 1.5), d, V.UP, 3);
        });
        fx.on(12, () -> {
            Vec3 f = chest.fwd();
            Vec3 d = RuneSwords.flatDir(f, 140, -12);
            b.pose(V.add(chest.pivot(), d, 1.0), d, V.UP, 3);
        });
        for (int t = 15; t < charge - 2; t++) {
            final int tt = t;
            fx.on(t, () -> {
                Vec3 f = chest.fwd();
                double j = (tt % 2 == 0 ? 1 : -1);
                Vec3 d = RuneSwords.flatDir(f, 140 + 3 * j, -12 + 2 * j);
                b.pose(V.add(chest.pivot(), d, 1.0), d, V.UP, 1);
            });
        }
        // 휩쓸기: 오른쪽 뒤 (140°) → 앞 (charge) → 왼쪽 뒤 (−150°) → 느려지며 −175°
        RuneSwords.swing(b, charge - 2, 5, chest, 1.0, (f, a) -> RuneSwords.flatDir(f, a, -4), f -> V.UP, 140, -150, null);
        RuneSwords.swing(b, charge + 3, 3, chest, 1.0, (f, a) -> RuneSwords.flatDir(f, a, -4), f -> V.UP, -150, -175, "out");
        // 흩어짐: 작아지며 위로 · 칼날을 따라 불꽃
        fx.on(charge + 7, () -> {
            for (int i = 1; i <= 4; i++) SkillFx.sparks(level, V.add(b.hilt(), b.dir(), len * 0.2 * i), core, 4, 4);
            b.grow(0.08, 6);
            b.prop.move(V.up(b.hilt(), 1.6), 6);
        });
        fx.on(charge + 13, b::remove);
    }

    static boolean heavenSplit(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        double[][] cs = RuneKit.colorOf("blade");
        double[] glow = cs[0];
        double[] core = cs[1];
        double[] white = RuneKit.WHITE;
        ServerLevel level0 = sp.level();
        double charge = k.num("charge");
        SkillFx.play(level0, "mob.evocation_illager.prepare_attack", sp.position(), 1.2, 0.9);
        SkillFx.play(level0, "beacon.power", sp.position(), 1.4, 0.7);
        heavenBlade(sp, RuneKit.lifeToken(sp), (int) charge, glow, core);
        // 2차 강화 — 예고: 청백 하늘 · 발밑의 칼날 문양 (빠르게 돎) · 앞쪽 하늘의 칼날 문양
        Vec3 c0 = sp.position();
        RuneKit.ultSkyFor(sp, level0, c0, "heavenSplit", (int) charge + 34, null);
        RuneKit.domainSigil(level0, c0, 4.6, "blade", glow, white, Opt.of("life", (charge + 2) / 20, "spinRate", 140.0, "grow", 0.3, "alpha", 0.8, "fade", 0.15));
        Vec3 skyAt = RuneKit.skyPoint(sp, c0, 12, 21, 6, 22);
        RuneKit.skyPlate(sp, level0, "sigil", skyAt, 5.4, glow, white, Opt.of("cell", (double) RuneKit.SIGIL.get("blade"), "life", (charge + 6) / 20, "spinRate", 60.0,
                "grow", 0.2, "alpha", 0.75, "fade", 0.3));
        for (int t = 0; t < charge; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (sp.isRemoved()) return;
                ServerLevel level = sp.level();
                Vec3 eye = sp.getEyePosition();
                Vec3 f = V.flat(sp.getViewVector(1F));
                double q = tt / charge;
                // 0.50.0 2D 강화 (초월 나머지): 모이는 검기 후광 — 참격 판 (waveCard) 이라 RuneBoost.aura (파티클) 대신 같은 판을 1.35배 · 옅게 · 빛깔로 한 장 더
                Opt wo = Opt.of("size", 0.7 + q * 1.7, "life", 0.13, "alpha", 0.6 + 0.4 * q);
                RuneKit.waveCard(level, V.up(V.add(eye, f, 2.5), -0.2), f, V.UP, wo.with("size", (0.7 + q * 1.7) * 1.35, "alpha", (0.6 + 0.4 * q) * 0.42), glow,
                        glow);
                RuneKit.waveCard(level, V.up(V.add(eye, f, 2.5), -0.2), f, V.UP, wo, glow, white);
                Vec3 side = V.rightOf(f);
                for (int s : new int[] {-1, 1}) {
                    Vec3 a = V.add(V.up(V.add(eye, f, 2.4), -0.15), side, s * (2.4 - q * 1.6));
                    SkillFx.fx(level, "rpg:arrow_spark", a, Opt.of("dir", V.scale(side, -s), "speed", 3.0, "size", 0.14, "grow", 0.5, "life", 0.25, "color", core,
                            "alpha", 1.0));
                }
                if (tt % 6 == 0) SkillFx.play(level, "block.bell.hit", eye, 1.2 + q, 0.3);
            });
        }
        SkillFx.rune(level0, sp.position(), Opt.of("size", 1.8, "life", charge / 20 + 0.2, "color", glow, "alpha", 0.85, "rate", 160.0, "progress", 1.0, "grow", 0.2));
        String life = RuneKit.lifeToken(sp);
        RuneKit.later(charge, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 f = V.flat(sp.getViewVector(1F));
            Vec3 from = V.up(V.add(sp.position(), f, 0.8), 1.2);
            double speed = k.num("speed");
            double len = Math.max(1, RuneKit.rayLength(level, from, f, k.num("range")));
            long ticks = Math.max(1, Math.round((len / speed) * 20));
            // 거대한 검기: 눈앞 1.6칸에서 나타나 날아감 (한 장이 날아감 — 2틱마다 다시 그리지 않음)
            double s0 = Math.min(1.6, len * 0.5);
            Opt wo = Opt.of("dir", f, "speed", speed, "size", k.num("width") * 1.15, "life", Math.max(0.05, (len - s0) / speed), "fade", 0.15);
            // 0.50.0 2D 강화 (초월 나머지): 거대한 검기 후광 — 날아가는 wave 는 rfx2 가 참격 판 (waveCard) 으로 그리므로 (rfx2a 의 파티클 후광은 옆에서 보면
            // 판과 어긋남) 같은 판을 1.35배 · 옅게 · 빛깔로 한 장 더 (같은 길 · 빠르기)
            RuneKit.waveCard(level, V.add(from, f, s0), f, V.UP, wo.with("size", k.num("width") * 1.15 * 1.35, "alpha", 0.42), glow, glow);
            RuneKit.rfx2(level, "wave", V.add(from, f, s0), wo, glow, white);
            for (int t = 0; t <= ticks; t += 2) {
                double s = Math.min(len, (t / 20.0) * speed);
                RuneKit.later(t, () -> SkillFx.fx(level, "rpg:light_cut", V.up(V.add(from, f, s), -1.05), Opt.of("dir", f, "size", 1.2, "thick", 0.16, "life", 0.9,
                        "delay", 0.3, "color", glow, "alpha", 0.9)));
            }
            RuneKit.flyStreak(level, from, V.add(from, f, len), speed, glow, core, 1.4, 0.5);
            // 0.50.0 2D 강화 (초월 나머지): 검기가 끝에 닿는 터짐 (뇌신의 창처럼 길 끝의 땅 — 반지름은 검기 크기 (폭 × 1.15) 보다 조금 크게)
            RuneKit.later(ticks, () -> {
                Vec3 endp = V.add(from, f, len);
                RuneBoost.impact(level, new Vec3(endp.x, RuneKit.groundAt(level, endp, from.y - 1.2), endp.z), glow, white, k.num("width") * 1.6);
            });
            SkillFx.play(level, "item.trident.thunder", from, 1.5, 0.7);
            SkillFx.play(level, "firework.large_blast", from, 1.2, 0.8);
            RuneKit.shakeAround(level, from, 10, 0.25, 0.3);
            // 2차 강화 발동: 청백 번쩍임 · 하늘이 둘로 갈라짐 (검기 방향으로 하늘을 가로지르는 빛) · 하늘 문양의 섬광 · 검기가 지나간 땅이 갈라져 빛남
            RuneKit.ultFlashFor(sp, level, from, "heavenSplit", "flash", Opt.empty());
            Vec3 sky0 = V.up(V.add(sp.getEyePosition(), f, 15), 7.5);
            SkillFx.fx(level, "rpg:light_cut", sky0, Opt.of("dir", f, "size", 13.0, "thick", 0.9, "life", 1.1, "delay", 0.6, "color", glow, "alpha", 0.95));
            SkillFx.fx(level, "rpg:light_cut", sky0, Opt.of("dir", f, "size", 12.5, "thick", 0.3, "life", 1.0, "delay", 0.55, "color", white, "alpha", 1.0));
            RuneKit.skyFlare(level, skyAt, 3.8, glow, white, Opt.of("life", 0.5));
            for (double s = 4; s < len; s += 5) {
                Vec3 p = V.add(from, f, s);
                double crackV = (s / 5) % 2 > 0.5 ? 1 : 0;
                RuneKit.later(Math.round((s / speed) * 20) + 1, () -> RuneKit.glowCrack(level, new Vec3(p.x, RuneKit.groundAt(level, p, from.y - 1.2), p.z), 2.4,
                        glow, white, Opt.of("life", 1.6, "v", crackV)));
            }
            for (Kit.LineHit h : RuneKit.lineTargets(sp, from, f, len, k.num("width"))) {
                LivingEntity e = h.e();
                RuneKit.later(Math.max(0, Math.round((h.t() / speed) * 20)), () -> {
                    if (sp.isRemoved() || e.isRemoved()) return;
                    if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                        if (k.numOr("stun", 0) > 0) RuneKit.stun(e, (int) k.num("stun"), glow, RuneKit.fxQuota("splitStun", 6)); // 8단계 6돌: 기절
                        if (RuneKit.fxQuota("splitHit", 4)) {
                            // 한 틱에 4명까지 그림 (줄지어 선 적 — 파티클 예산, 피해 · 밀침은 모두)
                            Vec3 at = Kit.bodyPoint(e);
                            Vec3 v = V.unit(V.sub(at, sp.getEyePosition()));
                            SkillFx.fx(level, "rpg:slash_x", V.add(at, v, -0.2), Opt.of("dir", v, "size", 2.0, "life", 0.34, "color", glow, "alpha", 1.0));
                            SkillFx.burst(level, at, core, 1.2, 0.3);
                            SkillFx.sparks(level, at, core, 8, 6);
                        }
                        RuneKit.knock(e, f, k.num("knock"), 0.3);
                    }
                });
            }
        });
        return true;
    }
}
