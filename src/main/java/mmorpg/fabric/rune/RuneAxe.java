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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

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

    /** 회전 도끼의 그림 (도는 도끼 한 장 + 앞 장의 자리에서 이어지는 지나간 자리) */
    private static final class Spin {
        final ServerPlayer sp;
        final ServerLevel level;
        final double[] glow;
        final double[] core;
        Vec3 last;

        Spin(ServerPlayer sp, ServerLevel level, double[] glow, double[] core) {
            this.sp = sp;
            this.level = level;
            this.glow = glow;
            this.core = core;
        }

        void frame(int t, Vec3 at, boolean hovering) {
            boolean near = V.len(V.sub(at, sp.getEyePosition())) < 1.4;
            if (!near) RuneKit.rfx2(level, "prop", at, Opt.of("size", hovering ? 0.78 : 0.66, "life", 0.16, "spin", t * 67.0), glow, core, AXE, null);
            if (last != null && V.len(V.sub(at, last)) > 0.3) RuneKit.streak(level, last, at, glow, 0.14, 0.25, 0.8, 0); // 지나간 자리
            last = at;
            if (t % 4 == 0) SkillFx.sparks(level, at, core, hovering ? 4 : 2, 3);
        }
    }

    /**
     * 회전 도끼: 도끼를 던져 range 칸 (벽 앞까지) 날아가 hover 틱 제자리에서 돌다가 돌아옴. 갈 때 · 올 때 선 가까이 width 칸의 적을 한 번씩
     * (도는 도끼 = rpg:rfx_prop 도끼)
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
        SkillFx.arc(level, V.up(V.add(sp.getEyePosition(), f, 1.2), -0.2), f, Opt.of("shape", "heavy", "flip", true, "size", 1.5, "life", 0.2, "speed", 3.0,
                "spin", 30.0, "color", glow, "aura", core));
        SkillFx.play(level, "item.trident.throw", sp.position(), 0.7, 1);
        SkillFx.play(level, "mob.breeze.whirl", sp.position(), 1.3, 0.6);
        // 도끼: 2틱마다 한 장씩 돌려 가며 (갈 때 → 끝에서 제자리 → 올 때). 손 앞 1칸 안은 그리지 않음 (1인칭 화면)
        Spin spin = new Spin(sp, level, glow, core);
        // 갈 때 → 끝에서 제자리 (돌아오는 모습은 돌아오기 시작할 때 그때 거리로)
        for (int t = 0; t <= back0; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> spin.frame(tt, tt <= out ? V.add(from, f, len * tt / out) : tip, tt > out));
        }
        Map<UUID, Integer> outAt = new HashMap<>();
        for (Kit.LineHit h : RuneKit.lineTargets(sp, from, f, len, width)) {
            LivingEntity e = h.e();
            int when = (int) Math.round(h.t() / speed * 20);
            outAt.put(e.getUUID(), when);
            RuneKit.later(when, () -> {
                if (!sp.isRemoved() && !e.isRemoved() && RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                    RuneKit.hitFlash(level, Kit.bodyPoint(e), f, glow, core);
                }
            });
        }
        RuneKit.later(back0, () -> {
            if (sp.isRemoved()) return;
            Vec3 home = V.up(sp.position(), 1.2);
            Vec3 d = V.sub(home, tip);
            double len2 = V.len(d);
            Vec3 u = V.unit(d);
            int backTicks = Math.max(1, (int) Math.round(len2 / speed * 20));
            for (int t = 2; t <= backTicks; t += 2) {
                final int tt = t;
                RuneKit.later(t, () -> spin.frame(back0 + tt, V.add(tip, d, (double) tt / backTicks), false));
            }
            SkillFx.play(level, "mob.breeze.whirl", tip, 1.0, 0.7);
            for (Kit.LineHit h : RuneKit.lineTargets(sp, tip, u, len2, width)) {
                LivingEntity e = h.e();
                Integer first = outAt.get(e.getUUID());
                int when = (int) Math.round(h.t() / speed * 20);
                // 갈 때 맞은 적은 그때부터 10틱 뒤 (몹 무적 시간)
                int wait = first == null ? when : Math.max(when, first + Kit.IFRAMES - back0);
                RuneKit.later(wait, () -> {
                    if (!sp.isRemoved() && !e.isRemoved() && RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                        RuneKit.hitFlash(level, Kit.bodyPoint(e), u, glow, core);
                    }
                });
            }
            RuneKit.later(backTicks, () -> {
                if (!sp.isRemoved()) SkillFx.play(level, "item.trident.return", sp.position(), 0.9, 1);
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
        SkillFx.arc(level, V.up(V.add(head, f, 1.6), -0.1), f, Opt.of("shape", "heavy", "flip", false, "size", 2.4, "life", 0.26, "speed", 2.0, "spin", -80.0,
                "color", glow, "aura", core));
        RuneKit.later(2, () -> SkillFx.arc(level, V.up(V.add(head, f, 1.9), -0.4), f, Opt.of("shape", "heavy", "flip", true, "size", 2.0, "life", 0.22, "speed",
                2.0, "spin", -100.0, "color", BLOOD, "core", false, "alpha", 0.7)));
        SkillFx.play(level, "mace.smash_air", c, 0.7, 0.9);
        RuneKit.later(2, () -> {
            if (sp.isRemoved()) return;
            Vec3 hitC = V.add(c, f, 1.8);
            RuneKit.zonePulse(level, hitC, glow, 2.0, 0.35);
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

    /** 도약 곡선 위 u (0 ~ 1) 의 자리 (발에서 1칸 위 — 가운데가 peak 칸 솟음) */
    private static Vec3 leapPos(Vec3 start, Vec3 land, double peak, double u) {
        return new Vec3(start.x + (land.x - start.x) * u, start.y + (land.y - start.y) * u + 4 * peak * u * (1 - u) + 1.0, start.z + (land.z - start.z) * u);
    }

    /**
     * 도약 찍기: 조준한 곳 (range 칸, 막히면 벽 앞) 으로 뛰어올라 내려찍음 (반지름 radius, 띄우고 둔화) — 뛰기 전 머리 위로 든 거대한 도끼
     * (rpg:rfx_prop 도끼) → 내려찍으면 앞에 도끼가 박히고 땅이 갈라지며 둘레에 바위 가시가 솟음
     */
    static boolean leapSlam(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 start = sp.position();
        double range = k.num("range");
        Vec3 g = RuneKit.groundAim(sp, range);
        double dist0 = Math.min(range, Math.hypot(g.x - start.x, g.z - start.z));
        Vec3 f = dist0 > 0.2 ? V.flat(V.sub(g, start)) : V.flat(sp.getViewVector(1F));
        double want = Math.max(0, dist0 - 1.0); // 조준한 곳 바로 앞에 착지 (몹과 겹치지 않게)
        double can = RuneKit.dashDistance(sp, f, want, 1.2, 2.2); // 한 칸 턱은 뛰어넘음
        double reach = can >= want - 0.3 ? want : can;
        // 착지는 그 아래의 단단한 땅 (나는 몹을 조준해도 공중에 서지 않게, 물 · 용암 · 허공이면 제자리에서 찍음)
        double top = Math.max(g.y, start.y) + 1.6;
        Double gy = reach > 0.3 ? RuneKit.solidGround(level, new Vec3(start.x + f.x * reach, top, start.z + f.z * reach), top - start.y + 8) : null;
        boolean leap = gy != null;
        Vec3 land = new Vec3(start.x + f.x * reach, leap ? gy.doubleValue() : start.y, start.z + f.z * reach);
        double[][] cs = RuneKit.colorOf("axe");
        double[] glow = cs[0];
        double[] core = cs[1];
        int air = 4; // 공중에 있는 틱
        double peak = 2.0 + want * 0.15;
        if (leap) {
            for (int i = 0; i < 8; i++) {
                RuneKit.streak(level, leapPos(start, land, peak, i / 8.0), leapPos(start, land, peak, (i + 1) / 8.0), glow, 0.32, 0.35, 0.95,
                        (i / 8.0) * air / 20);
            }
        }
        // 머리 위로 든 도끼 (뛰어오르기 전까지 머리 1.3칸 위 · 조금 뒤, 날이 뒤로 젖혀짐 — 몸은 마지막 틱에 옮겨짐)
        for (int t = 0; t < air; t++) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (sp.isRemoved()) return;
                Vec3 at = V.add(V.up(sp.position(), 2.9), f, -0.3);
                RuneKit.rfx2(level, "prop", at, Opt.of("size", 0.8, "life", 0.1, "spin", 20.0 + tt * 8, "alpha", 0.95, "vel", RuneKit.bodyVel(sp)), glow, core,
                        AXE, null);
            });
        }
        RuneKit.zonePulse(level, start, new double[] {0.6, 0.5, 0.4}, 1.6, 0.35);
        SkillFx.sparks(level, V.up(start, 0.2), new double[] {0.7, 0.6, 0.5}, 8, 3);
        SkillFx.play(level, "mob.enderdragon.flap", start, 1.2, 0.7);
        SkillFx.play(level, "mob.breeze.jump", start, 0.8, 1);
        RuneKit.later(air - 1, () -> {
            if (!sp.isRemoved() && leap) RuneKit.moveTo(sp, land, null);
        });
        RuneKit.later(air, () -> {
            if (sp.isRemoved()) return;
            Vec3 c = sp.position();
            double radius = k.num("radius");
            // 내리찍은 도끼: 앞 1.7칸에 날이 아래로 박힘 (0.45초)
            Vec3 axeAt = V.up(V.add(c, f, 1.7), 0.9);
            RuneKit.rfx2(level, "prop", axeAt, Opt.of("size", 1.15 * RuneKit.nearK(sp, axeAt, 3, 0.55), "life", 0.45, "spin", 180.0, "fade", 0.5, "alpha", 0.95),
                    glow, core, AXE, null);
            SkillFx.arc(level, V.up(V.add(c, f, 1.6), 1.3), f, Opt.of("shape", "heavy", "flip", false, "size", 2.6, "life", 0.24, "speed", 1.0, "spin", -90.0,
                    "color", glow, "aura", core));
            SkillFx.burst(level, V.up(c, 0.5), glow, 2.2, 0.35);
            RuneKit.zonePulse(level, c, glow, radius, 0.5);
            RuneKit.zonePulse(level, c, core, radius * 0.5, 0.35);
            SkillFx.fx(level, "rpg:ground_crack", V.up(V.add(c, f, 1.2), 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", radius * 1.0, "life", 1.2,
                    "color", new double[] {0.36, 0.1, 0.06}, "alpha", 0.9));
            for (int i = 0; i < 6; i++) {
                Vec3 d = RuneKit.turn(new Vec3(1, 0, 0), i * 60 + Kit.rand() * 20);
                SkillFx.fx(level, "rpg:light_cut", V.add(V.up(c, 0.08), d, 1.5), Opt.of("dir", d, "size", 1.3, "thick", 0.34, "life", 0.9, "delay", 0.6, "color",
                        glow, "alpha", 0.95));
            }
            RuneKit.rockRing(sp, level, c, radius * 0.75, 6, 0.6, glow, Opt.of("life", 0.9, "minDist", 1.8));
            RuneKit.rockBurst(level, V.up(c, 0.4), 5, 5, 0.24);
            SkillFx.sparks(level, V.up(c, 0.4), core, 8, 5);
            SkillFx.vanilla(level, "minecraft:critical_hit_emitter", V.up(c, 0.6));
            SkillFx.play(level, "mace.heavy_smash_ground", c, 0.8, 1);
            SkillFx.play(level, "random.explode", c, 0.9, 0.5);
            RuneKit.shakeAround(level, c, 10, 0.3, 0.25);
            for (LivingEntity e : Kit.around(sp, c, radius, 3)) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                    RuneKit.knock(e, V.sub(e.position(), c), 0.4, k.num("lift"));
                    RuneKit.effect(e, "slowness", (int) k.num("slowTicks"), 1);
                }
            }
        });
        return true;
    }

    /** 처형: 앞 range 칸의 대상 위로 거대한 도끼 (rpg:rfx_prop 도끼) 가 떠올라 돌다가 (delay) 내려찍음. 체력이 below 이하면 bonus 배 */
    static boolean execute(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        LivingEntity target = Kit.pickTarget(sp, k.num("range"), k.num("cone"));
        if (target == null) {
            Notify.warn(sp, "§c" + k.str("name") + ": §7앞쪽 " + k.text("range") + "칸 안에 대상이 없습니다");
            return false;
        }
        ServerLevel level = sp.level();
        Vec3 f = V.flat(V.sub(target.position(), sp.position()));
        double[][] cs = RuneKit.colorOf("axe");
        double[] glow = cs[0];
        double[] core = cs[1];
        int delay = (int) k.num("delay");
        SkillFx.rune(level, target.position(), Opt.of("size", 1.5, "life", delay / 20.0 + 0.1, "color", glow, "alpha", 0.9, "progress", 1.0, "rate", 160.0));
        for (int t = 0; t < delay; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (target.isRemoved()) return;
                Vec3 at = V.up(target.position(), 3.2 + tt * 0.06);
                // 떠오르며 돌아 마지막에 날이 아래 (180°) — 내려찍는 도끼와 이어짐
                RuneKit.rfx2(level, "prop", at, Opt.of("size", 0.9 + tt * 0.05, "life", 0.16, "spin", 180.0 - (delay - 2 - tt) * 24), glow, core, AXE, null);
            });
        }
        SkillFx.play(level, "mob.ravager.roar", sp.position(), 0.9, 0.8);
        SkillFx.play(level, "mob.evocation_illager.prepare_attack", target.position(), 0.6, 0.8);
        RuneKit.later(delay, () -> {
            if (sp.isRemoved() || target.isRemoved()) return;
            boolean low = target.getHealth() <= target.getMaxHealth() * k.num("below");
            Vec3 at = Kit.bodyPoint(target);
            Vec3 g = target.position();
            // 내려찍는 거대한 도끼 + 땅이 갈라짐
            RuneKit.rfx2(level, "prop", V.up(g, 3.4), Opt.of("dir", V.DOWN, "speed", 26.0, "size", 1.4, "life", 0.1, "spin", 180.0), low ? BLOOD : glow, core, AXE,
                    null); // 도끼날이 아래 (그림은 날이 위)
            SkillFx.arc(level, V.up(V.add(g, f, -0.4), 1.4), f, Opt.of("shape", "heavy", "flip", false, "size", 2.6, "life", 0.26, "speed", 1.0, "spin", -95.0,
                    "color", low ? BLOOD : glow, "aura", core));
            SkillFx.burst(level, at, low ? BLOOD : glow, low ? 2.6 : 1.8, 0.35);
            RuneKit.zonePulse(level, g, glow, 2.4, 0.45);
            for (int i = 0; i < 4; i++) {
                Vec3 d = RuneKit.turn(f, i * 90 + 45);
                SkillFx.fx(level, "rpg:light_cut", V.add(V.up(g, 0.08), d, 1.1), Opt.of("dir", d, "size", 1.0, "thick", 0.3, "life", 0.8, "delay", 0.5,
                        "color", glow, "alpha", 0.95));
            }
            SkillFx.sparks(level, at, core, 12, 6);
            SkillFx.play(level, "mace.heavy_smash_ground", g, 0.7, 1);
            SkillFx.play(level, "game.player.attack.critical", at, 0.7, 1);
            SkillFx.shake(sp, 0.25, 0.2);
            double got = RuneKit.runeHit(sp, target, P, k.num("mult") * (low ? k.num("bonus") : 1), r.stat());
            if (got > 0) RuneKit.knock(target, f, 0.3, 0.15);
            if (got > 0 && low) {
                SkillFx.fx(level, "rpg:slash_x", V.add(at, f, -0.2), Opt.of("dir", f, "size", 2.0, "life", 0.35, "color", BLOOD, "alpha", 1.0));
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
     * 몸에 핏빛 기운 (rpg:rfx_aura), 펄스마다 거대한 핏빛 도끼 둘 (rpg:rfx_prop) 이 몸 둘레를 휩쓸며 돎. 죽으면 끝 (차원 이동으로는 끝나지 않음).
     * 초월 2차 강화: 핏빛 하늘 (죽으면 걷힘) · 검붉은 번쩍임 · 발밑의 칼날 문양 · 앞쪽 하늘이 핏빛으로 찢어짐 · 난타마다 발밑 문양
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
        SkillFx.fx(level0, "rpg:arrow_shot", V.up(c0, 4.4), Opt.of("dir", V.UP, "speed", 0.0, "size", 2.4, "thick", 1.0, "life", 0.6, "color", BLOOD,
                "alpha", 0.85));
        SkillFx.burst(level0, V.up(c0, 1), glow, 2.2, 0.4);
        RuneKit.zonePulse(level0, c0, BLOOD, radius, 0.6);
        RuneKit.selfCue(sp, BLOOD, core, true);
        SkillFx.play(level0, "mob.ravager.roar", c0, 0.7, 1);
        SkillFx.play(level0, "mob.enderdragon.growl", c0, 1.4, 0.4);
        SkillFx.shake(sp, 0.2, 0.3);
        // 2차 강화 — 광폭화: 핏빛 하늘 (광폭화 동안 — 죽으면 걷힘) · 검붉은 번쩍임 · 발밑의 칼날 문양 · 앞쪽 하늘이 핏빛으로 찢어짐 · 난타마다 발밑 문양
        RuneKit.ultSkyFor(sp, level0, c0, "berserker", total + 10, null);
        RuneKit.ultFlashFor(sp, level0, c0, "berserker", "flash", Opt.empty());
        RuneKit.domainSigil(level0, c0, 4.8, "blade", BLOOD, core, Opt.of("life", 1.4, "spinRate", 70.0, "grow", 0.35, "alpha", 0.85));
        RuneKit.skyPlate(sp, level0, "crack", RuneKit.skyPoint(sp, c0, 12, 21, 6, 22), 6.2, BLOOD, core, Opt.of("life", 1.8, "grow", 0.4, "fade", 0.45));
        int alive = RuneKit.deathToken(sp); // 죽으면 끝 (난타 · 흡혈 · 기운 — 차원 이동 · 탈것 부르기로는 끝나지 않음)
        SkillFx.every(hits, gap, i -> {
            if (!RuneKit.aliveSince(sp, alive)) {
                endBloodSky(sp, level0); // 죽으면 핏빛 하늘도 걷힘
                return;
            }
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            // 거대한 도끼 둘이 3틱 동안 몸 둘레를 휩쓸며 돎 (반지름 2.3, 허리 높이)
            for (int j = 0; j < 2; j++) {
                for (int s = 0; s < 3; s++) {
                    final int jj = j;
                    final int ss = s;
                    RuneKit.later(s, () -> {
                        if (sp.isRemoved()) return;
                        Vec3 cc = sp.position();
                        double ang = i * 1.1 + jj * Math.PI + ss * 0.7;
                        Vec3 pos = new Vec3(cc.x + Math.cos(ang) * 2.3, cc.y + 1.0, cc.z + Math.sin(ang) * 2.3);
                        // 앞 (1인칭 화면) 을 지나는 도끼는 흐리게 — 화면을 덮지 않게
                        boolean ahead = V.dot(V.flat(V.sub(pos, cc)), Kit.facingOf(sp)) > 0.5;
                        RuneKit.rfx2(sp.level(), "prop", pos, Opt.of("size", 0.74, "life", 0.14, "spin", (double) ((i * 140 + ss * 90 + jj * 180) % 360), "alpha",
                                ahead ? 0.5 : 0.95), jj == 1 ? BLOOD : glow, core, AXE, null);
                    });
                }
            }
            for (int j = 0; j < 2; j++) {
                double a = (j / 2.0) * Math.PI * 2 + i * 0.8;
                Vec3 dir = new Vec3(Math.cos(a + Math.PI / 2), 0, Math.sin(a + Math.PI / 2));
                Vec3 at = new Vec3(c.x + Math.cos(a) * 2.3, c.y + 0.9, c.z + Math.sin(a) * 2.3);
                SkillFx.arc(level, at, dir, Opt.of("shape", "heavy", "flip", i % 2 == 1, "size", 2.2, "life", 0.26, "speed", 2.5, "spin", 90.0, "color", glow,
                        "aura", j == 1 ? BLOOD : null));
            }
            RuneKit.zonePulse(level, c, glow, radius, 0.4);
            RuneKit.domainSigil(level, c, 3.6, "blade", i % 2 == 1 ? BLOOD : glow, core, Opt.of("life", 0.45, "spinRate", 120.0, "grow", 0.25, "alpha", 0.65,
                    "fade", 0.6));
            SkillFx.fx(level, "rpg:light_cut", V.up(c, 0.08), Opt.of("dir", RuneKit.turn(new Vec3(1, 0, 0), i * 47), "size", 2.2, "thick", 0.32, "life", 0.8,
                    "delay", 0.5, "color", BLOOD, "alpha", 0.9));
            SkillFx.play(level, "mace.smash_air", c, 0.8 + i * 0.05, 0.8);
            SkillFx.play(level, "mace.smash_ground", c, 0.7, 0.6);
            int n = 0;
            for (LivingEntity e : Kit.around(sp, c, radius)) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                    n++;
                    SkillFx.fx(level, "rpg:slash_x", Kit.bodyPoint(e), Opt.of("dir", V.flat(V.sub(e.position(), c)), "size", 0.9, "life", 0.22, "color", glow,
                            "alpha", 1.0));
                }
            }
            if (n > 0) {
                double healed = RpgHealth.healBy(sp, RpgHealth.maxOf(sp) * k.num("heal") * Math.min(n, k.num("healMax"))
                        * Runes.supportScale(RpgStats.effective(sp), r.family()));
                if (healed > 0) DamageNumbers.show(sp, healed, DamageNumbers.Style.HEAL, "", 0);
            }
            if (i == hits - 1) {
                SkillFx.burst(level, V.up(c, 1), BLOOD, 2.6, 0.4);
                SkillFx.play(level, "random.explode", c, 1.0, 0.5);
            }
        });
        // 광폭화 동안: 몸의 핏빛 기운 (양옆 · 뒤, 2틱마다) + 눈앞에서 솟는 핏빛 불씨 (1인칭에서도 보임). 죽으면 멈춤
        for (int t = 2; t < total; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.aliveSince(sp, alive)) return;
                RuneKit.bodyAura(sp, tt % 8 < 4 ? BLOOD : glow, Opt.of("n", 4, "alpha", 0.7));
                if (tt % 8 == 0) RuneKit.auraCue(sp, tt % 16 != 0 ? glow : BLOOD, 2);
            });
        }
        // 끝 소리 (죽었다 다시 태어났어도 — 지금 엔티티를 uuid 로 찾음)
        RuneKit.later(total, () -> {
            ServerPlayer p = RuneKit.player(level0, sp.getUUID());
            if (p != null) SkillFx.play(p.level(), "mob.zombie.unfect", p.position(), 0.8, 0.5);
        });
        return true;
    }
}
