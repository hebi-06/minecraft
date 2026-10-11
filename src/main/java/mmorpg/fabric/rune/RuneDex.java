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

/** 민첩 룬 (runes.js "민첩" — 초록: 빠른 칼날): 질풍 베기 · 칼날 부채 · 그림자 습격 · 칼바람 폭풍 · 무영검 */
final class RuneDex {
    private RuneDex() {}

    /** 그림자 분신 몸 (그림자보다 밝게 — 풀밭 위에서도 사람 모양이 보이게) */
    private static final double[] CLONE = {0.12, 0.45, 0.24};
    /** 무영검의 멈춘 시간 빛 (ULT_FX.runes.shadowless.still — 돌파로 늘어난 멈춤 표시) */
    private static final double[] STILL = {0.86, 0.9, 0.96};

    static void register() {
        RuneCast.register("galeSlash", RuneDex::galeSlash);
        RuneCast.register("bladeFan", RuneDex::bladeFan);
        RuneCast.register("shadowRaid", RuneDex::shadowRaid);
        RuneCast.register("bladeStorm", RuneDex::bladeStorm);
        RuneCast.register("shadowless", RuneDex::shadowless);
    }

    /** 질풍 베기: 앞으로 dash 칸 돌진 (벽 앞에서 멈춤), 지나간 길 양옆 width 칸의 적 — 지나간 길에 그림자 분신 잔상 셋 · 바람 · 끝에서 초승달 검기 */
    static boolean galeSlash(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 start = sp.position();
        Vec3 f = V.flat(sp.getViewVector(1F));
        double dist = RuneKit.dashDistance(sp, f, k.num("dash"));
        Vec3 end = V.add(start, f, dist);
        double[][] cs = RuneKit.colorOf("dex");
        double[] glow = cs[0];
        double[] core = cs[1];
        double width = k.num("width");
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, V.add(start, f, dist / 2), dist / 2 + width + 0.5)) {
            Vec3 p = V.sub(e.position(), start);
            double pad = Kit.bodyPad(e);
            double along = p.x * f.x + p.z * f.z;
            double across = Math.abs(p.x * f.z - p.z * f.x);
            if (along >= -0.5 - pad && along <= dist + 1.0 + pad && across <= width + pad) targets.add(e);
        }
        for (double s = 0.3; s <= dist; s += 0.8) {
            SkillFx.fx(level, "rpg:arrow_trail", V.up(V.add(start, f, s), 1.0), Opt.of("dir", f, "size", 0.4, "thick", 0.2, "life", 0.45, "color", glow,
                    "alpha", 0.85));
        }
        // 잔상: 지나간 길에 돌진하는 그림자 분신 셋 (뒤로 갈수록 흐림 — 1인칭에서는 등 뒤라 안 보임)
        double[] qs = {0.2, 0.45, 0.7};
        for (int j = 0; j < qs.length; j++) {
            if (dist * qs[j] < 0.4) continue;
            RuneKit.shadeClone(sp, level, V.add(start, f, dist * qs[j]), f, 1, CLONE, glow, Opt.of("life", 0.3 + j * 0.08, "alpha", 0.4 + j * 0.2, "fade", 0.7));
        }
        SkillFx.arc(level, V.up(V.add(start, f, 1.2), 1.1), f, Opt.of("shape", "thin", "flip", false, "size", 1.6, "life", 0.24, "speed", 4.0, "spin", -20.0,
                "color", glow, "aura", core));
        SkillFx.sparks(level, V.up(start, 0.5), glow, 6, 4);
        if (dist > 0.2) Kit.moveTo(sp, end); // 못 옮기면 (막힌 자리) 제자리에서 벰
        RuneKit.rfx2(level, "wave", V.up(V.add(end, f, 1.9), 1.0), Opt.of("size", 1.2, "life", 0.22, "grow", 0.3, "fade", 0.6, "spin", -15.0), glow, core);
        SkillFx.play(level, "item.trident.riptide_1", start, 1.4, 0.8);
        SkillFx.play(level, "mob.enderdragon.flap", start, 1.8, 0.5);
        int n = 0;
        for (LivingEntity e : targets) {
            if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                // 맞은 자리 (파티클 예산): X 12명 · 불꽃 4명까지
                if (n < 12) SkillFx.fx(level, "rpg:slash_x", Kit.bodyPoint(e), Opt.of("dir", f, "size", 0.9, "life", 0.25, "color", glow, "alpha", 1.0));
                if (n < 4) SkillFx.sparks(level, Kit.bodyPoint(e), core, 4, 4);
                n++;
            }
        }
        return true;
    }

    /** 칼날 부채: 칼날 blades 개를 부채꼴 (spread 도) 로 던짐. 칼날마다 처음 닿는 적, 적마다 한 번 — 날아가는 빛의 칼 (rpg:rfx_sword 작게) */
    static boolean bladeFan(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 head = sp.getEyePosition();
        Vec3 view = sp.getViewVector(1F);
        Vec3 f = V.flat(view);
        double[][] cs = RuneKit.colorOf("dex");
        double[] glow = cs[0];
        double[] core = cs[1];
        double range = k.num("range");
        double spread = k.num("spread");
        int blades = (int) k.num("blades");
        Set<UUID> hit = new HashSet<>();
        List<LivingEntity> cands = Kit.around(sp, sp.position(), range + 1, 4);
        for (int i = 0; i < blades; i++) {
            double deg = -spread / 2 + (spread * i) / Math.max(1, blades - 1);
            Vec3 h = RuneKit.turn(f, deg);
            Vec3 d = V.unit(new Vec3(h.x, view.y * 0.6, h.z));
            LivingEntity best = null;
            double bestT = range;
            for (LivingEntity e : cands) {
                Vec3 q = Kit.bodyToward(e, head, d);
                Vec3 p = V.sub(q, head);
                double t = V.dot(p, d);
                if (t < 0 || t > bestT) continue;
                if (V.len(V.sub(p, V.scale(d, t))) > 0.9 + Kit.bodyPad(e)) continue;
                if (!Kit.lineOfSight(sp, head, q)) continue;
                best = e;
                bestT = t;
            }
            double len = best != null ? bestT : range;
            double life = Math.max(0.05, len / 28);
            Vec3 from = V.add(V.up(head, -0.25), d, 1.0);
            double speed = Math.max(0, (len - 1.2) / life);
            // 칼끝이 날아가는 쪽 (손잡이 뒤) — 눈에서 1칸 앞부터
            RuneKit.rfx2(level, "sword", from, Opt.of("dir", d, "speed", speed, "size", 0.42, "life", life), glow, core);
            for (double s2 = 1.0; s2 < len; s2 += 1.6) {
                double delay = (s2 / len) * life;
                SkillFx.fx(level, "rpg:arrow_trail", V.add(from, d, s2), Opt.of("dir", d, "size", 0.5, "thick", 0.1, "life", delay + 0.22, "delay", delay,
                        "color", glow, "alpha", 0.7));
            }
            if (best != null && hit.add(best.getUUID())) {
                LivingEntity target = best;
                Tasks.later(Math.max(1, (int) Math.round(life * 20)), () -> {
                    if (sp.isRemoved() || target.isRemoved()) return;
                    if (RuneKit.runeHit(sp, target, P, k.num("mult"), r.stat()) > 0) {
                        SkillFx.fx(level, "rpg:slash_x", Kit.bodyPoint(target), Opt.of("dir", d, "size", 0.8, "life", 0.22, "color", glow, "alpha", 1.0));
                        SkillFx.sparks(level, Kit.bodyPoint(target), core, 3, 4);
                        SkillFx.play(level, "item.trident.hit", Kit.bodyPoint(target), 1.6, 0.5);
                    }
                });
            }
        }
        SkillFx.arc(level, V.up(V.add(head, f, 1.2), -0.2), f, Opt.of("shape", "thin", "flip", false, "size", 1.6, "life", 0.2, "speed", 4.0, "spin", 90.0,
                "color", glow));
        SkillFx.play(level, "random.bow", head, 1.8, 0.8);
        SkillFx.play(level, "item.trident.throw", head, 1.5, 0.7);
        return true;
    }

    /**
     * 그림자 습격: 앞쪽 cone 도 · range 칸의 적 targets 명에게 그림자 분신이 차례로 날아가 벰 — 분신 그림 (rpg:rfx_shade):
     * 내 옆에서 돌진하는 분신이 적의 옆으로 미끄러져 가서 → 그 자리에서 베는 분신 + X (가까운 적의 X 는 작게)
     */
    static boolean shadowRaid(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        List<LivingEntity> list = RuneKit.coneTargets(sp, k.num("range"), k.num("cone"), (int) k.num("targets"));
        if (list.isEmpty()) {
            Notify.warn(sp, "§c" + k.str("name") + ": §7앞쪽 " + k.text("range") + "칸 안에 대상이 없습니다");
            return false;
        }
        ServerLevel level = sp.level();
        double[][] cs = RuneKit.colorOf("dex");
        double[] glow = cs[0];
        double[] core = cs[1];
        double[] shade = {0.08, 0.3, 0.16};
        Vec3 c0 = sp.position();
        SkillFx.shock(level, c0, shade, 1.6, 0.4);
        SkillFx.sparks(level, V.up(c0, 1), shade, 8, 3);
        SkillFx.play(level, "mob.vex.charge", c0, 1.3, 0.9);
        int gap = (int) k.num("gap");
        for (int i = 0; i < list.size(); i++) {
            LivingEntity e = list.get(i);
            int side = i % 2 == 1 ? 1 : -1;
            Tasks.later(i * gap, () -> {
                if (sp.isRemoved() || e.isRemoved()) return;
                Vec3 fv = V.flat(sp.getViewVector(1F));
                // 분신이 튀어나가는 곳: 옆 1.1칸 · 앞 1.2칸 (1인칭 화면 아래 구석을 덮지 않게 — 가까우면 작게)
                Vec3 fromFeet = V.add(V.add(sp.position(), V.rightOf(fv), side * 1.1), fv, 1.2);
                Vec3 toward = V.flat(V.sub(e.position(), fromFeet));
                // 베는 자리: 적의 옆 (내 쪽 선에서 비켜 — 가까운 적이어도 분신이 1인칭 눈앞을 덮지 않게)
                Vec3 flank = V.scale(V.rightOf(toward), side);
                Vec3 toFeet = V.add(e.position(), flank, 0.9 + Kit.bodyPad(e));
                // 날아가는 시간 (= 베는 틱): 내 옆 0.8칸 · 몸 높이에서 적의 몸까지 30칸/초
                double l0 = V.len(V.sub(Kit.bodyPoint(e), V.up(V.add(sp.position(), V.rightOf(fv), side * 0.8), 1.0)));
                double life = Math.max(0.05, l0 / 30);
                int hitT = Math.max(1, (int) Math.round(life * 20));
                RuneKit.shadeClone(sp, level, fromFeet, toward, 1, CLONE, glow, Opt.of("life", life, "fade", 0.15, "alpha", 0.85, "size",
                        1.1 * RuneKit.nearK(sp, V.up(fromFeet, 1), 3.5, 0.55), "vel", V.scale(V.sub(toFeet, fromFeet), 1 / life)));
                SkillFx.beam(level, V.up(fromFeet, 1.0), Kit.bodyPoint(e), shade, Opt.of("half", 0.32, "thick", 0.25, "life", 0.35, "sweep", life,
                        "alpha", 0.6));
                SkillFx.play(level, "mob.endermen.portal", V.up(fromFeet, 1), 1.6, 0.6);
                // 도착: 적 옆에서 베는 분신 (돌진 분신이 끝나기 1틱 전에 나타남 — 이어지게)
                Tasks.later(Math.max(1, hitT - 1), () -> {
                    if (sp.isRemoved() || e.isRemoved()) return;
                    Vec3 at2 = V.add(e.position(), flank, 0.9 + Kit.bodyPad(e));
                    RuneKit.shadeClone(sp, level, at2, V.scale(flank, -1), 2, CLONE, glow, Opt.of("life", 0.42, "fade", 0.5, "size",
                            1.1 * RuneKit.nearK(sp, V.up(at2, 1), 3.5, 0.55)));
                });
                Tasks.later(hitT, () -> {
                    if (sp.isRemoved() || e.isRemoved()) return;
                    Vec3 at = Kit.bodyPoint(e);
                    Vec3 v = V.unit(V.sub(at, sp.getEyePosition()));
                    double xs = RuneKit.nearK(sp, V.add(at, v, -0.2), 3, 0.35); // 가까운 적의 X 는 작게 (1인칭 화면을 덮지 않게)
                    SkillFx.burst(level, V.add(at, v, -0.05), shade, 1.0 * xs, 0.28);
                    SkillFx.fx(level, "rpg:slash_x", V.add(at, v, -0.2), Opt.of("dir", v, "size", 1.0 * xs, "life", 0.26, "color", glow, "alpha", 1.0));
                    SkillFx.cutMark(level, at, v, 40, 0.18, glow, core, 1.1 * xs);
                    SkillFx.cutMark(level, at, v, -40, 0.18, glow, core, 1.1 * xs);
                    SkillFx.sparks(level, at, core, 5, 5);
                    SkillFx.play(level, "game.player.attack.critical", at, 1.3, 0.8);
                    RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat());
                });
            });
        }
        return true;
    }

    /**
     * 칼바람 폭풍: 빨라지고, gap 틱마다 hits 번 주위 radius 칸을 벰 — 빛의 칼 셋이 몸 둘레 (반지름 2.1) 를 돌고 (앞을 지날 때는 흐리게),
     * 벨 때마다 둘레에 작은 회오리 둘
     */
    static boolean bladeStorm(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        double[][] cs = RuneKit.colorOf("dex");
        double[] glow = cs[0];
        double[] core = cs[1];
        RuneKit.effect(sp, "speed", (int) k.num("speedTicks"), 1);
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        double radius = k.num("radius");
        int total = (hits - 1) * gap + 6;
        for (int t = 0; t < total; t += 3) {
            final int tt = t;
            Tasks.later(t, () -> {
                if (sp.isRemoved()) return;
                ServerLevel level = sp.level();
                Vec3 c = sp.position();
                Vec3 fc = Kit.facingOf(sp);
                Vec3 vel = RuneKit.bodyVel(sp); // 빨라진 몸 (이동 속도 II) 을 따라옴
                for (int j = 0; j < 3; j++) {
                    // 한 틱에 12° (다시 그릴 때마다 36° — 120° 간격의 칼 셋이 멈춰 보이거나 뒤로 도는 것처럼 보이지 않게)
                    double a = (tt * 12 + j * 120) * (Math.PI / 180);
                    Vec3 tang = new Vec3(-Math.sin(a), 0, Math.cos(a));
                    Vec3 at = new Vec3(c.x + Math.cos(a) * 2.1, c.y + 0.7 + j * 0.4, c.z + Math.sin(a) * 2.1);
                    boolean ahead = V.dot(V.flat(V.sub(at, c)), fc) > 0.4; // 1인칭 화면 앞을 지나는 칼은 흐리게
                    RuneKit.rfx2(level, "sword", at, Opt.of("dir", tang, "size", 0.5, "life", 0.17, "alpha", ahead ? 0.45 : 0.95, "vel", vel), glow, core);
                    if (j == 0) {
                        SkillFx.arc(level, at, tang, Opt.of("shape", "thin", "flip", false, "size", 1.8, "life", 0.2, "speed", 3.0, "spin", 90.0, "color", glow,
                                "core", true));
                    }
                }
            });
        }
        SkillFx.every(hits, gap, i -> {
            if (sp.isRemoved()) return;
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            RuneKit.zonePulse(level, c, glow, radius, 0.35);
            for (int j = 0; j < 2; j++) {
                double a = i * 1.7 + j * Math.PI;
                RuneKit.vortexAt(level, new Vec3(c.x + Math.cos(a) * radius * 0.85, c.y, c.z + Math.sin(a) * radius * 0.85), 0.7, glow, Opt.of("life", 0.45,
                        "alpha", 0.7));
            }
            SkillFx.sparks(level, V.up(c, 1), core, 4, 5);
            SkillFx.play(level, "mob.breeze.whirl", c, 1.2 + i * 0.05, 0.8);
            int n = 0;
            for (LivingEntity e : Kit.around(sp, c, radius)) {
                // X 는 12명까지 (파티클 예산)
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0 && n++ < 12) {
                    SkillFx.fx(level, "rpg:slash_x", Kit.bodyPoint(e), Opt.of("dir", V.flat(V.sub(e.position(), c)), "size", 0.7, "life", 0.2, "color", glow,
                            "alpha", 1.0));
                }
            }
            if (i == hits - 1) SkillFx.play(level, "wind_charge.burst", c, 1.2, 0.8);
        });
        return true;
    }

    /**
     * 무영검: 앞쪽 적을 멈추고 (freeze) 적마다 화면에서 그 적을 둘러싼 빛의 칼 (rpg:rfx_sword 작게, 칼끝이 적 쪽) 이 하나씩 나타남 →
     * delay 틱에 칼이 한꺼번에 꽂히며 터짐 (적마다 큰 X · 초승달 검기). 적이 많으면 칼 수를 줄이고 터지는 틱을 셋으로 나눔 (모바일 파티클).
     * 초월 2차 강화: 시간이 멈춤 (잿빛 하늘 · 짧은 흰 번쩍임) · 발밑의 칼날 문양 · 대상들 뒤 하늘에 멈춘 비전 문양 → 끝에 깨짐 · 섬광 · 충격 고리
     */
    static boolean shadowless(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        List<LivingEntity> list = RuneKit.coneTargets(sp, k.num("range"), k.num("cone"), (int) k.num("maxTargets"));
        if (list.isEmpty()) {
            Notify.warn(sp, "§c" + k.str("name") + ": §7앞쪽 " + k.text("range") + "칸 안에 대상이 없습니다");
            return false;
        }
        ServerLevel level = sp.level();
        Vec3 head = sp.getEyePosition();
        Vec3 view = sp.getViewVector(1F);
        Vec3 f = V.flat(view);
        double[][] cs = RuneKit.colorOf("dex");
        double[] glow = cs[0];
        double[] core = cs[1];
        double[] white = RuneKit.WHITE;
        int delay = (int) k.num("delay");
        int freeze = (int) k.num("freeze");
        // 칼을 뽑는 한 줄기 빛 (화면을 가로지름) + 시간이 멈춘 고리
        SkillFx.fx(level, "rpg:light_cut", V.up(V.add(head, f, 2.2), -0.2), Opt.of("dir", V.rightOf(f), "size", 3.2, "thick", 0.3, "life", 0.5, "delay", 0.3,
                "color", core, "alpha", 1.0));
        SkillFx.ring(level, V.add(head, f, 2.0), view, glow, 2.2, 0.8, 0);
        SkillFx.play(level, "chime.amethyst_block", head, 0.8, 1);
        SkillFx.play(level, "mob.evocation_illager.cast_spell", head, 1.6, 0.6);
        // 2차 강화 — 예고: 시간이 멈춤 (잿빛 하늘 · 짧은 흰 번쩍임) · 발밑의 칼날 문양 (돌지 않음) · 대상들 뒤 하늘에 멈춘 비전 문양 → 끝에 깨짐
        Vec3 c0 = sp.position();
        double mx = 0;
        double my = 0;
        double mz = 0;
        for (LivingEntity e : list) {
            mx += e.getX() / list.size();
            my += e.getY() / list.size();
            mz += e.getZ() / list.size();
        }
        Vec3 mid0 = new Vec3(mx, my, mz);
        RuneKit.ultSkyFor(sp, level, c0, "shadowless", delay + 24, null);
        RuneKit.ultFlashFor(sp, level, c0, "shadowless", "still", Opt.of("time", new double[] {0.02, 0, 0.3}));
        RuneKit.domainSigil(level, c0, 6.5, "blade", glow, white, Opt.of("life", (delay + 2) / 20.0, "spinRate", 0.0, "alpha", 0.7, "fade", 0.15));
        Vec3 still = RuneKit.skyPoint(sp, mid0, 6, 20, 6, 22);
        RuneKit.skyPlate(sp, level, "sigil", still, 5.2, new double[] {0.78, 0.84, 0.9}, white, Opt.of("cell", (double) RuneKit.SIGIL.get("arcane"), "life",
                (delay - 1) / 20.0, "alpha", 0.75, "fade", 0.1));
        // 멈춘 시간이 깨짐
        RuneKit.later(delay - 2, () -> RuneKit.skyPlate(sp, level, "crack", still, 5.8, glow, white, Opt.of("life", 0.5, "grow", 0.5, "fade", 0.6)));
        for (LivingEntity e : list) {
            RuneKit.effect(e, "slowness", freeze, 10);
            RuneKit.effect(e, "weakness", freeze, 2);
        }
        int blades = Math.max(2, Math.min(6, 24 / list.size()));
        double ringR = 1.45;
        Map<UUID, List<Vec3>> spots = new HashMap<>(); // 적 → 마지막에 꽂힐 칼의 방향 (적 둘레의 자리)
        for (int c = 0; c < blades; c++) {
            final int cc = c;
            int at0 = 2 + (int) Math.round((double) (c * (delay - 6)) / blades);
            RuneKit.later(at0, () -> {
                if (sp.isRemoved()) return;
                Vec3 eye = sp.getEyePosition();
                for (int ei = 0; ei < list.size(); ei++) {
                    LivingEntity e = list.get(ei);
                    if (e.isRemoved()) continue;
                    Vec3 at = Kit.bodyPoint(e);
                    Vec3 v = V.unit(V.sub(at, eye));
                    double ang = (360.0 / blades) * cc + ei * 23 + 90;
                    Vec3 out = RuneKit.screenAngle(v, ang);
                    Vec3 pos = V.add(V.add(at, out, ringR), v, -0.3);
                    double hold = (delay - at0) / 20.0 + 0.05 + (ei % 3) / 20.0; // 그 적을 찌르는 틱까지 (i % 3 틱 늦음)
                    RuneKit.rfx2(level, "sword", pos, Opt.of("dir", V.scale(out, -1), "size", 0.62, "life", hold), glow, white);
                    spots.computeIfAbsent(e.getUUID(), x -> new ArrayList<>()).add(out);
                }
                SkillFx.play(level, "chime.amethyst_block", eye, 1.2 + cc * 0.12, 0.45);
            });
        }
        // 칼자국 몇 개 (멈춘 몸 위)
        for (int c = 0; c < 3; c++) {
            final int cc = c;
            RuneKit.later(4 + c * 6, () -> {
                if (sp.isRemoved()) return;
                Vec3 eye = sp.getEyePosition();
                for (LivingEntity e : list) {
                    if (e.isRemoved()) continue;
                    Vec3 at = Kit.bodyPoint(e);
                    Vec3 v = V.unit(V.sub(at, eye));
                    double hold = (delay - 4 - cc * 6) / 20.0;
                    SkillFx.fx(level, "rpg:light_cut", V.add(at, RuneKit.screenOffset(v, cc * 3), 1), Opt.of("dir", RuneKit.screenAngle(v, cc * 60 + 20), "size",
                            0.9, "thick", 0.24, "life", hold + 0.16, "delay", hold, "color", glow, "alpha", 1.0));
                }
            });
        }
        int swordCell = RuneKit.RFX.get("sword")[0];
        for (int i = 0; i < list.size(); i++) {
            LivingEntity e = list.get(i);
            final int ii = i;
            RuneKit.later(delay + (i % 3), () -> {
                if (sp.isRemoved() || e.isRemoved()) return;
                Vec3 eye = sp.getEyePosition();
                Vec3 at = Kit.bodyPoint(e);
                Vec3 v = V.unit(V.sub(at, eye));
                for (Vec3 out : spots.getOrDefault(e.getUUID(), List.of())) {
                    RuneKit.rfx(level, "sword", V.add(at, out, ringR), Opt.of("dir", V.scale(out, -1), "size", 0.62, "life", 0.1, "speed", ringR / 0.1,
                            "color", glow, "cell", (double) swordCell));
                }
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) <= 0) return;
                // 8단계 돌파 (3 · 6돌 — 멈춤이 꽂힌 뒤에도 남음): 머리 위에 멈춘 시간 문양 (돌지 않음) — 풀릴 때까지
                int still = freeze - delay - (ii % 3);
                if (still > 2) SkillFx.rune(level, V.up(e.getEyePosition(), 0.5), Opt.of("size", 0.5, "life", still / 20.0, "color", STILL, "alpha", 0.95,
                        "rate", 0.0, "spin", ii * 37.0));
                SkillFx.fx(level, "rpg:slash_x", V.add(at, v, -0.2), Opt.of("dir", v, "size", 1.6, "life", 0.3, "color", white, "alpha", 1.0));
                RuneKit.rfx2(level, "wave", V.add(at, v, -0.3), Opt.of("size", 1.25, "life", 0.3, "spin", (double) ((ii * 47) % 180), "grow", 0.3, "fade", 0.5),
                        glow, white);
                SkillFx.burst(level, V.add(at, v, -0.15), glow, 1.1, 0.3);
                SkillFx.sparks(level, at, core, 6, 6);
                SkillFx.fx(level, "rpg:arrow_shot", V.up(e.position(), 1.5), Opt.of("dir", V.UP, "speed", 0.0, "size", 1.6, "thick", 0.4, "life", 0.36,
                        "color", glow, "alpha", 0.85));
            });
        }
        RuneKit.later(delay, () -> {
            if (sp.isRemoved()) return;
            Vec3 eye = sp.getEyePosition();
            SkillFx.play(level, "firework.large_blast", eye, 1.2, 1);
            SkillFx.play(level, "item.trident.thunder", eye, 1.4, 0.5);
            // 발동: 화면 번쩍임 · 깨진 하늘의 섬광 → 발밑에서 퍼지는 충격 고리 (1틱 뒤)
            RuneKit.ultFlashFor(sp, level, eye, "shadowless", "flash", Opt.empty());
            RuneKit.skyFlare(level, still, 4, glow, white, Opt.of("life", 0.5));
            RuneKit.later(1, () -> RuneKit.novaRing(level, c0, 1.5, 5, glow, white, Opt.of("life", 0.5)));
            RuneKit.shakeAround(level, eye, 10, 0.25, 0.25);
        });
        return true;
    }
}
