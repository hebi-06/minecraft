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
     * 0.49.0 도약 발도 (발도 일섬 → 도약): 조준한 곳 (leap 칸) 으로 air 틱 동안 낮게 뛰어들며 (칼집에 손을 얹은 빛) 내려앉는 순간 칼을 뽑아 앞을 한 번에 벰
     * (range 칸 · cone 도) — 칼을 뽑는 번쩍임 (rpg:rfx_prop 반짝임) + 앞으로 퍼지는 가로 초승달 검기 (rpg:rfx_wave), 칼을 거두면 (6틱 뒤) 베인 적에게
     * 늦게 베인 자국이 한 번 더 그어짐
     */
    static boolean quickDraw(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        double[][] cs = RuneKit.colorOf("blade");
        Vec3 start = sp.position();
        Vec3 land = RuneKit.leapLand(sp, k.num("leap"), 1.6);
        int air = (int) k.num("air");
        Vec3 f0 = V.hdist(land, start) > 0.3 ? V.flat(V.sub(land, start)) : V.flat(sp.getViewVector(1F));
        // 칼집에 손을 얹은 빛 (뛰는 동안 허리 오른쪽)
        for (int t = 0; t < air; t += 2) {
            RuneKit.later(t, () -> {
                if (sp.isRemoved()) return;
                Vec3 at = V.up(V.add(sp.position(), V.rightOf(f0), 0.45), 0.95);
                RuneKit.rfx(level, "prop", at, Opt.of("size", 0.22, "life", 0.12, "cell", (double) RuneKit.RFX.get("glint")[0], "spin", 20.0, "color", cs[1],
                        "alpha", 0.9, "vel", RuneKit.bodyVel(sp)));
            });
        }
        SkillFx.play(level, "item.armor.equip_chain", start, 1.4, 0.8);
        RuneKit.leap(sp, land, 1.1 + V.hdist(land, start) * 0.06, air, cs[0], cs[1], () -> drawCut(sp, r, P, k));
        return true;
    }

    /** 발도: 칼을 뽑으며 앞을 한 번에 벰 (도약 발도의 착지) */
    private static void drawCut(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 head = sp.getEyePosition();
        Vec3 f = V.flat(sp.getViewVector(1F));
        double[][] cs = RuneKit.colorOf("blade");
        double[] glow = cs[0];
        double[] core = cs[1];
        // 칼집에서 뽑는 빛
        RuneKit.rfx(level, "prop", V.add(V.up(head, -0.55), V.rightOf(f), 0.45), Opt.of("size", 0.35, "life", 0.18, "cell", (double) RuneKit.RFX.get("glint")[0],
                "spin", 20.0, "color", RuneKit.WHITE, "grow", 0.8, "fade", 0.6));
        SkillFx.fx(level, "rpg:light_cut", V.up(V.add(head, f, 2.0), -0.25), Opt.of("dir", V.rightOf(f), "size", 2.8, "thick", 0.2, "life", 0.32, "delay", 0.04,
                "color", core, "alpha", 1.0));
        RuneKit.arc(level, V.up(V.add(head, f, 1.4), -0.3), f, Opt.of("shape", "arc", "flip", false, "size", 2.7, "life", 0.24, "speed", 3.0, "spin", -6.0,
                "color", glow, "aura", core));
        RuneKit.rfx2(level, "wave", V.up(V.add(head, f, 2.3), -0.35), Opt.of("dir", f, "speed", 6.0, "size", 1.5, "life", 0.22, "grow", 0.35, "fade", 0.6, "spin", 4.0,
                "alpha", 0.9), glow, RuneKit.WHITE);
        SkillFx.play(level, "random.anvil_land", head, 2.0, 0.35);
        SkillFx.play(level, "item.spear.attack_miss", head, 1.3, 0.9);
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
        for (int s = 2; s < len; s += 2) {
            double delay = (s / len) * (len / speed);
            SkillFx.fx(level, "rpg:light_cut", V.add(from, dir, s), Opt.of("dir", dir, "size", 1.1, "thick", 0.16, "life", delay + 0.3, "delay", delay, "color",
                    s % 4 != 0 ? glow : core, "alpha", 0.85));
        }
        SkillFx.play(level, "item.trident.throw", from, 1.3, 0.9);
        SkillFx.play(level, "item.spear.attack_miss", from, 1.6, 0.7);
        Map<Long, Integer> perTick = new HashMap<>(); // 틱 → 그 틱에 벤 적 수 (파티클 예산: 자국 6명 · 불꽃 3명까지)
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
        Guards.set(sp, ticks, k.num("reduce"), Guards.Reflect.fn((pl, attacker) -> {
            if (!RuneKit.sameLife(pl, life) || attacker == null || attacker.isRemoved() || countered.contains(attacker.getUUID()) || Tasks.now() > until) return;
            if (!Kit.canHit(pl, attacker)) return;
            countered.add(attacker.getUUID());
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
            DamageNumbers.showFace(pl, "§b§l반격!", 2.2, -0.8, -0.2);
            SkillFx.play(d, "item.shield.block", at, 1.4, 0.8);
            SkillFx.play(d, "game.player.attack.strong", at, 1.2, 1);
            RuneKit.runeHit(pl, attacker, P, k.num("mult"), r.stat());
        }), null);
        RuneKit.selfCue(sp, glow, core, true);
        SkillFx.play(level, "random.anvil_land", sp.position(), 1.8, 0.4);
        SkillFx.play(level, "armor.equip_iron", sp.position(), 1.2, 0.8);
        // 자세: 오른손 앞에 세운 빛의 칼 (칼끝 위 — 1인칭 조준점을 가리지 않게) + 10틱마다 발밑 검진
        for (int t = 0; t < ticks; t += 5) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 eye = sp.getEyePosition();
                Vec3 f = V.flat(sp.getViewVector(1F));
                Vec3 at = V.up(V.add(V.add(eye, f, 1.5), V.rightOf(f), 0.5), -0.45);
                RuneKit.rfx2(sp.level(), "sword", at, Opt.of("dir", V.UP, "size", 0.42, "life", 0.3, "alpha", 0.6, "vel", RuneKit.bodyVel(sp)), glow, RuneKit.WHITE,
                        RuneKit.RFX.get("sword"), 0.75);
                if (tt % 10 == 0) {
                    SkillFx.rune(sp.level(), sp.position(), Opt.of("size", 1.3, "life", 0.55, "color", glow, "alpha", 0.7, "rate", 0.0, "spin", tt * 3.0));
                }
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
     * 찌르기는 빛의 칼 (rpg:rfx_sword) 이 앞으로 뻗어 나감. 0.49.0 찌르기까지 마치면 검세 (가하는 피해 + buff, buffTicks 틱)
     */
    static boolean tripleCut(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        double[][] cs = RuneKit.colorOf("blade");
        double[] glow = cs[0];
        double[] core = cs[1];
        String life = RuneKit.lifeToken(sp);
        int hits = (int) k.num("hits");
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
                // 빛의 칼: 오른손에서 칼끝이 len 까지 뻗음 (3틱 — 1인칭에서 손에서 비스듬히 뻗어 보임)
                thrust(level, from, f, len, glow, RuneKit.WHITE);
                SkillFx.fx(level, "rpg:light_cut", V.add(from, f, len / 2), Opt.of("dir", f, "size", len / 2, "thick", 0.18, "life", 0.3, "color", core, "alpha", 1.0));
                SkillFx.ring(level, V.add(from, f, len), f, glow, 0.9, 0.3, 0);
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

    /**
     * 찌르는 빛의 칼 (rpg:rfx_sword — runes.js elementThrust, 원소 검사 · 검사 색): 오른손 (몸 높이 from 의 오른쪽 위) 에서 찌르는 끝 (from + f × len) 까지
     * 칼끝이 뻗음 (3틱) 뒤 잠깐 머묾. 1인칭에서 칼이 보는 쪽과 나란해 점으로 보이지 않게 손에서 비스듬히 뻗음.
     * 머무는 칼은 2틱에 나타남 (뻗는 칼과 1틱 겹침 — 깜빡임 없게)
     */
    private static void thrust(ServerLevel level, Vec3 from, Vec3 f, double len, double[] glow, double[] core) {
        double size = 0.9;
        Vec3 hand = V.up(V.add(from, V.rightOf(f), 0.3), 0.15);
        Vec3 end = V.add(from, f, len);
        Vec3 dv = V.unit(V.sub(end, hand));
        double lv = V.len(V.sub(end, hand));
        double s0 = Math.min(0.6 + size, lv);
        RuneKit.rfx2(level, "sword", V.add(hand, dv, s0), Opt.of("dir", dv, "speed", Math.max(0, lv - s0 - size) / 0.15, "size", size, "life", 0.15), glow, core);
        Tasks.later(2, () -> RuneKit.rfx2(level, "sword", V.add(hand, dv, Math.max(s0, lv - size)), Opt.of("dir", dv, "size", size, "life", 0.25, "fade", 0.6), glow,
                core));
    }

    /**
     * 천지개벽: charge 틱 동안 눈앞에 초승달 검기 (rpg:rfx_wave) 가 모여 커진 뒤, 거대한 검기가 speed 칸/초로 range 칸 (벽 앞까지) — 폭 width 칸의 적
     * (적마다 한 번), 밀쳐냄. 초월 2차 강화: 청백 하늘 · 발밑의 칼날 문양 · 앞쪽 하늘의 칼날 문양 → 청백 번쩍임 · 하늘이 둘로 갈라짐 · 하늘 문양의 섬광 ·
     * 검기가 지나간 땅이 갈라져 빛남
     */
    static boolean heavenSplit(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        double[][] cs = RuneKit.colorOf("blade");
        double[] glow = cs[0];
        double[] core = cs[1];
        double[] white = RuneKit.WHITE;
        ServerLevel level0 = sp.level();
        double charge = k.num("charge");
        SkillFx.play(level0, "mob.evocation_illager.prepare_attack", sp.position(), 1.2, 0.9);
        SkillFx.play(level0, "beacon.power", sp.position(), 1.4, 0.7);
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
                RuneKit.waveCard(level, V.up(V.add(eye, f, 2.5), -0.2), f, V.UP, Opt.of("size", 0.7 + q * 1.7, "life", 0.13, "alpha", 0.6 + 0.4 * q), glow, white);
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
            RuneKit.rfx2(level, "wave", V.add(from, f, s0), Opt.of("dir", f, "speed", speed, "size", k.num("width") * 1.15, "life", Math.max(0.05, (len - s0) / speed),
                    "fade", 0.15), glow, white);
            for (int t = 0; t <= ticks; t += 2) {
                double s = Math.min(len, (t / 20.0) * speed);
                RuneKit.later(t, () -> SkillFx.fx(level, "rpg:light_cut", V.up(V.add(from, f, s), -1.05), Opt.of("dir", f, "size", 1.2, "thick", 0.16, "life", 0.9,
                        "delay", 0.3, "color", glow, "alpha", 0.9)));
            }
            RuneKit.flyStreak(level, from, V.add(from, f, len), speed, glow, core, 1.4, 0.5);
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
