package mmorpg.fabric.rune;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** 힘 룬 (runes.js "힘" — 빨강 · 주황: 땅을 부수는 힘): 대지 강타 · 회오리 베기 · 대지 균열 · 거인의 일격 · 천붕 */
final class RuneStr {
    private RuneStr() {}

    private static final double[] CRACK = {0.36, 0.16, 0.06};
    private static final double[] DUST = {0.62, 0.48, 0.34};

    static void register() {
        RuneCast.register("groundSlam", RuneStr::groundSlam);
        RuneCast.register("whirlwind", RuneStr::whirlwind);
        RuneCast.register("fissure", RuneStr::fissure);
        RuneCast.register("titanSmash", RuneStr::titanSmash);
        RuneCast.register("heavenFall", RuneStr::heavenFall);
    }

    /** 대지 강타: 앞쪽 땅을 내려쳐 충격파 — 땅이 갈라지고 둘레에 바위 가시가 솟으며 돌이 튐 */
    static boolean groundSlam(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 f = V.flat(sp.getViewVector(1F));
        Vec3 c = V.add(sp.position(), f, k.num("dist"));
        double[][] cs = RuneKit.colorOf("str");
        double[] glow = cs[0];
        double[] core = cs[1];
        RuneKit.arc(level, V.up(V.add(sp.getEyePosition(), f, 1.6), -0.3), f, Opt.of("shape", "heavy", "flip", false, "size", 2.2, "life", 0.26, "speed", 2.0,
                "spin", -8.0, "color", glow, "aura", new double[] {1.0, 0.72, 0.3}));
        SkillFx.play(level, "mace.smash_air", sp.position(), 0.8, 0.8);
        Tasks.later(2, () -> {
            if (sp.isRemoved()) return;
            double radius = k.num("radius");
            RuneKit.zonePulse(level, c, glow, radius, 0.45);
            RuneKit.zonePulse(level, c, core, radius * 0.55, 0.3);
            SkillFx.burst(level, V.up(c, 0.4), glow, 1.3, 0.3);
            SkillFx.fx(level, "rpg:ground_crack", V.up(c, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", radius * 1.1, "life", 1.2, "color", CRACK,
                    "alpha", 0.9));
            RuneKit.rockRing(sp, level, c, radius * 0.7, 6, 0.55, glow, Opt.of("life", 0.8));
            RuneKit.rockBurst(level, V.up(c, 0.4), 4, 5, 0.24);
            SkillFx.fx(level, "rpg:dust", V.up(c, 0.5), Opt.of("size", 1.2, "life", 0.7, "color", DUST, "alpha", 0.55));
            SkillFx.sparks(level, V.up(c, 0.3), core, 6, 4);
            SkillFx.vanilla(level, "minecraft:critical_hit_emitter", V.up(c, 0.6));
            SkillFx.play(level, "mace.smash_ground", c, 0.9, 1);
            SkillFx.play(level, "dig.stone", c, 0.6, 1);
            SkillFx.shake(sp, 0.15, 0.15);
            for (LivingEntity e : Kit.around(sp, c, radius, 3)) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) RuneKit.knock(e, V.sub(e.position(), c), 0.3, k.num("lift"));
            }
        });
        return true;
    }

    /** 회오리 베기: 제자리에서 gap 틱마다 hits 번 휘몰아치며 주위를 벰 (조금 끌어당김) — 둘레에 흙먼지 회오리 셋 · 땅의 소용돌이 · 휩쓸려 도는 돌 */
    static boolean whirlwind(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        double[][] cs = RuneKit.colorOf("str");
        double[] glow = cs[0];
        double[] core = cs[1];
        double[] dust = {0.86, 0.56, 0.3};
        double radius = k.num("radius");
        SkillFx.every((int) k.num("hits"), (int) k.num("gap"), i -> {
            if (sp.isRemoved()) return;
            ServerLevel level = sp.level();
            Vec3 c = sp.position();
            for (int j = 0; j < 6; j++) {
                double a = (j / 6.0) * Math.PI * 2 + i * 0.5;
                Vec3 dir = new Vec3(Math.cos(a + Math.PI / 2), 0, Math.sin(a + Math.PI / 2));
                Vec3 at = new Vec3(c.x + Math.cos(a) * 2.2, c.y + 1.0, c.z + Math.sin(a) * 2.2);
                Opt o = Opt.of("shape", "heavy", "flip", i % 2 == 1, "size", 2.0 + i * 0.2, "life", 0.28, "speed", 2.5, "spin", 90.0, "color", glow, "aura",
                        j % 2 == 1 ? core : null);
                Runnable draw = () -> RuneKit.arc(level, at, dir, o);
                if (j < 2) draw.run();
                else Tasks.later(j / 2, draw);
            }
            // 흙먼지 회오리 셋이 몸 둘레 (반지름 radius × 0.8) 를 돎 — 몸 한가운데는 비움 (1인칭 카메라가 그 안에 들어가지 않게)
            for (int j = 0; j < 3; j++) {
                double a = (j / 3.0) * Math.PI * 2 + i * 1.3;
                Vec3 at = new Vec3(c.x + Math.cos(a) * radius * 0.8, c.y, c.z + Math.sin(a) * radius * 0.8);
                Vec3 tdir = new Vec3(-Math.sin(a), 0, Math.cos(a));
                RuneKit.vortexAt(level, at, 0.85 + i * 0.1, dust, Opt.of("life", 0.45, "alpha", 0.8, "vel", V.scale(tdir, 3))); // 0.45초에 1.35칸 (범위 안에서 돎)
                SkillFx.fx(level, "rpg:boulder", V.up(at, 0.5), Opt.of("dir", V.unit(new Vec3(tdir.x, 0.4, tdir.z)), "speed", 4.0, "size", 0.16, "life", 0.45,
                        "spin", Kit.rand() * 360, "color", RuneKit.ROCK, "alpha", 1.0));
            }
            SkillFx.fx(level, "rpg:whirlpool", V.up(c, 0.1), Opt.of("dir", V.ZERO, "size", radius * 0.9, "life", 0.5, "spin", i * 60.0, "speed", -420.0, "color", dust,
                    "alpha", 0.5));
            RuneKit.zonePulse(level, c, glow, radius * (0.85 + 0.075 * i), 0.4);
            SkillFx.rune(level, c, Opt.of("size", radius * 0.8, "life", 0.5, "color", glow, "alpha", 0.7, "rate", 360.0, "grow", 0.2));
            SkillFx.play(level, "item.trident.riptide_" + Math.min(3, i + 1), c, 1.0, 0.8);
            SkillFx.play(level, "mace.smash_air", c, 0.9 + 0.1 * i, 0.7);
            SkillFx.play(level, "mob.breeze.whirl", c, 0.8, 0.6);
            for (LivingEntity e : Kit.around(sp, c, radius)) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) RuneKit.knock(e, V.sub(c, e.position()), 0.15, 0.1);
            }
        });
        return true;
    }

    /** 대지 균열: 앞으로 length 칸 땅이 갈라지며 바위가 솟구침 (선 가까이 width 칸 안의 적) — 바위 가시 · 갈라진 땅 · 끝에서 바위가 터짐 */
    static boolean fissure(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 start = sp.position();
        Vec3 f = V.flat(sp.getViewVector(1F));
        double[][] cs = RuneKit.colorOf("str");
        double[] glow = cs[0];
        double[] core = cs[1];
        Set<UUID> hit = new HashSet<>();
        SkillFx.play(level, "mace.heavy_smash_ground", start, 0.8, 1);
        SkillFx.shake(sp, 0.2, 0.25);
        RuneKit.arc(level, V.up(V.add(sp.getEyePosition(), f, 1.5), -0.3), f, Opt.of("shape", "heavy", "flip", true, "size", 2.0, "life", 0.24, "speed", 2.0,
                "spin", 6.0, "color", glow, "aura", core));
        Vec3 side = V.rightOf(f);
        int length = (int) k.num("length");
        double width = k.num("width");
        for (int s = 1; s <= length; s++) {
            final int ss = s;
            Tasks.later(s / 2, () -> {
                if (sp.isRemoved()) return;
                Vec3 p = V.add(start, f, ss);
                SkillFx.fx(level, "rpg:light_cut", V.up(p, 0.08), Opt.of("dir", f, "size", 0.64, "thick", 0.48, "life", 0.95, "delay", 0.75, "color", glow,
                        "alpha", 0.95));
                if (ss % 2 == 0) SkillFx.fx(level, "rpg:ground_crack", V.up(p, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", 1.3, "life", 1.2,
                        "color", CRACK, "alpha", 0.85));
                // 바위 가시 (바로 앞 1칸은 1인칭 화면을 가리므로 2칸부터): 갈라진 틈 양옆에서 솟음, 멀수록 높게
                if (ss >= 2) {
                    for (double o : new double[] {-0.5, 0.5}) {
                        Vec3 q = V.add(p, side, o + (Kit.rand() - 0.5) * 0.3);
                        RuneKit.rockSpike(sp, level, q, (0.45 + ss * 0.04) * (0.8 + Kit.rand() * 0.4), glow, Opt.of("life", 0.95, "fade", 0.3));
                    }
                }
                SkillFx.burst(level, V.up(p, 0.3), glow, 0.8, 0.25);
                if (ss % 3 == 0) SkillFx.play(level, "mace.smash_ground", p, 0.8 + ss * 0.02, 0.8);
                for (LivingEntity e : Kit.around(sp, p, width, 3)) {
                    if (!hit.add(e.getUUID())) continue;
                    if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) RuneKit.knock(e, f, 0.2, k.num("lift"));
                }
                if (ss == length) {
                    RuneKit.zonePulse(level, p, glow, width * 1.8, 0.45);
                    RuneKit.rockRing(sp, level, p, 1.3, 5, 0.75, glow, Opt.of("life", 1.0));
                    RuneKit.rockBurst(level, V.up(p, 0.5), 5, 6, 0.24);
                    SkillFx.sparks(level, V.up(p, 0.4), core, 8, 5);
                    SkillFx.play(level, "random.explode", p, 1.2, 0.5);
                }
            });
        }
        return true;
    }

    /**
     * 거인의 일격: 앞쪽에 붉은 룬이 그려지고 (delay) 하늘에서 거대한 망치 (rpg:rfx_prop 망치 — 머리가 아래) 가 내려찍음 →
     * 망치가 박힌 채 남고 땅이 크게 갈라지며 둘레에 바위 가시가 솟고 돌이 튐
     */
    static boolean titanSmash(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 f = V.flat(sp.getViewVector(1F));
        Vec3 c = V.add(sp.position(), f, k.num("dist"));
        double[][] cs = RuneKit.colorOf("str");
        double[] glow = cs[0];
        double[] core = cs[1];
        int delay = (int) k.num("delay");
        double radius = k.num("radius");
        for (int t = 0; t < delay; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> SkillFx.rune(level, c, Opt.of("size", radius * 0.9, "life", 0.14, "color", glow, "alpha", 0.9, "progress", (tt + 2.0) / delay,
                    "rate", 0.0, "spin", tt * 4.0)));
        }
        SkillFx.play(level, "mob.ravager.roar", sp.position(), 1.2, 0.8);
        double fall = delay / 20.0;
        double h0 = 9;
        double size = 2.2 * RuneKit.nearK(sp, V.up(c, 2), 5, 0.5);
        double rest = size * 0.72; // 망치 가운데 높이 (머리가 땅에 닿음)
        int[] hammer = RuneKit.RFX.get("hammer");
        // 떨어지는 망치는 박힌 망치가 나타나는 틱 (delay − 1) 에 박힐 자리에 닿음 (두 망치가 겹침 — 하나가 위에 떠 보이지 않게)
        RuneKit.rfx2(level, "prop", V.up(c, h0), Opt.of("dir", V.DOWN, "speed", (h0 - rest) / Math.max(0.05, fall - 0.05), "size", size, "life", fall, "spin", 180.0),
                glow, core, hammer, null);
        SkillFx.fx(level, "rpg:arrow_shot", V.up(c, 6), Opt.of("dir", V.UP, "speed", 0.0, "size", 6.0, "thick", 0.1, "life", fall, "color", glow, "alpha", 0.6)); // 떨어질 자리의 가는 빛
        // 박힌 망치 (1틱 먼저 — 깜빡임 없게)
        Tasks.later(delay - 1, () -> RuneKit.rfx2(level, "prop", V.up(c, rest), Opt.of("size", size, "life", 0.75, "spin", 180.0, "fade", 0.5), glow, core, hammer,
                null));
        Tasks.later(delay, () -> {
            if (sp.isRemoved()) return;
            SkillFx.burst(level, V.up(c, 0.6), glow, 3.0, 0.4);
            SkillFx.burst(level, V.up(c, 0.6), core, 1.6, 0.3);
            SkillFx.shock(level, c, glow, 2.0, 0.35);
            RuneKit.zonePulse(level, c, core, radius * 0.7, 0.5);
            RuneKit.zonePulse(level, c, glow, radius, 0.65);
            SkillFx.fx(level, "rpg:ground_crack", V.up(c, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", radius * 1.3, "life", 1.6, "color",
                    new double[] {0.36, 0.14, 0.05}, "alpha", 0.95));
            RuneKit.rockRing(sp, level, c, radius * 0.62, 8, 0.8, glow, Opt.of("life", 1.1, "minDist", 1.8));
            RuneKit.rockBurst(level, V.up(c, 0.5), 8, 7, 0.3);
            SkillFx.fx(level, "rpg:dust", V.up(c, 0.8), Opt.of("size", 2.0, "life", 0.9, "color", DUST, "alpha", 0.6));
            SkillFx.sparks(level, V.up(c, 0.5), core, 10, 7);
            SkillFx.vanilla(level, "minecraft:huge_explosion_emitter", V.up(c, 0.5));
            SkillFx.play(level, "mace.heavy_smash_ground", c, 0.8, 1);
            SkillFx.play(level, "random.explode", c, 0.8, 0.7);
            SkillFx.play(level, "item.trident.thunder", c, 0.6, 0.6);
            RuneKit.shakeAround(level, c, 12, 0.35, 0.3);
            for (LivingEntity e : Kit.around(sp, c, radius, 3)) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) RuneKit.knock(e, V.sub(e.position(), c), 0.6, k.num("lift"));
            }
        });
        return true;
    }

    /**
     * 천붕: 조준한 곳에 거대한 룬 → 하늘에 거대한 빛의 검 (rpg:rfx_sword, 길이 9칸) 이 나타나 칼끝을 아래로 천천히 내려오다가
     * 마지막 8틱에 떨어져 꽂힘 (칼끝이 땅속 1.5칸) → 땅이 갈라지고 바위가 튐 (반지름 radius, 위로 띄우고 둔화). 꽂힌 검은 1.6초 남았다 사라짐.
     * 초월 2차 강화: 금빛 하늘 · 성광 문양 · 갈라지는 하늘 · 빛기둥 넷 · 화면 번쩍임 · 빛나는 금
     */
    static boolean heavenFall(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 g = RuneKit.groundAim(sp, k.num("range"));
        double[][] cs = RuneKit.colorOf("str");
        double[] glow = cs[0];
        double[] core = cs[1];
        double[] gold = {1.0, 0.8, 0.35};
        double[] white = RuneKit.WHITE;
        double len = 10.5; // 검 길이 (칸) — 0.48.2 2D 강화: 9 → 10.5
        int delay = (int) k.num("delay");
        double radius = k.num("radius");
        // 떠 있는 검 가운데 높이 (땅 위): 가까이 겨누면 낮게 (1인칭에서 칼끝이 보이게 — 12칸이면 11.4, 최대 18)
        double sky = Math.min(18, 6 + 0.45 * Math.hypot(g.x - sp.getX(), g.z - sp.getZ()));
        int drop = 8; // 떨어지는 틱
        for (int t = 0; t < delay; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> {
                SkillFx.rune(level, g, Opt.of("size", radius, "life", 0.14, "color", glow, "alpha", 0.9, "progress", Math.min(1, (tt + 2.0) / (delay * 0.7)),
                        "rate", 0.0, "spin", tt * 3.0));
                if (tt % 6 == 0) {
                    double a = Kit.rand() * Math.PI * 2;
                    SkillFx.fx(level, "rpg:arrow_spark", new Vec3(g.x + Math.cos(a) * radius, g.y + 0.2, g.z + Math.sin(a) * radius), Opt.of("dir", V.UP, "speed", 3.0,
                            "size", 0.25, "grow", 1.2, "life", 0.6, "color", gold, "alpha", 1.0));
                }
            });
        }
        // 하늘에서 내려오는 가는 빛 (떨어질 자리) + 하늘의 검: 나타나 천천히 내려옴 (칼끝 아래)
        SkillFx.fx(level, "rpg:arrow_shot", V.up(g, 14), Opt.of("dir", V.UP, "speed", 0.0, "size", 14.0, "thick", 0.12, "life", delay / 20.0, "color", gold,
                "alpha", 0.6));
        double hover = (delay - drop) / 20.0;
        // + 1틱: 떨어지는 검이 나타나는 동안 겹침 (깜빡임 없게)
        RuneBoost.rfx2a(level, "sword", V.up(g, sky + 1.5), Opt.of("dir", V.DOWN, "size", len / 2, "life", hover + 0.05, "speed", 1.5 / hover), glow, white);
        SkillFx.fx(level, "rpg:magic_circle_flash", V.up(g, sky + len / 2 + 1.6), Opt.of("size", 2.2, "life", 0.6, "spin", Kit.rand() * 360, "progress", 1.0,
                "color", gold, "alpha", 0.9));
        SkillFx.play(level, "beacon.power", g, 0.6, 1);
        SkillFx.play(level, "mob.wither.spawn", sp.position(), 1.4, 0.35);
        // 2차 강화 — 예고: 금빛 하늘 (하늘빛) · 겨눈 땅의 성광 문양 (커지며 돎) · 검이 나오는 하늘 뒤쪽이 갈라짐 · 검이 떨어질 때 둘레의 빛기둥 넷
        RuneKit.ultSkyFor(sp, level, g, "heavenFall", delay + 40, null);
        RuneKit.domainSigil(level, g, radius * 1.15, "holy", gold, white, Opt.of("life", (delay + 1) / 20.0, "spinRate", 28.0, "grow", 0.3, "alpha", 0.78,
                "fade", 0.08));
        Vec3 rift = RuneKit.skyPoint(sp, g, 6, 21, 6, 22);
        RuneKit.skyPlate(sp, level, "crack", rift, 6.5, gold, white, Opt.of("life", (delay + 10) / 20.0, "grow", 0.4, "fade", 0.35));
        RuneKit.later(delay - drop, () -> RuneKit.godRays(sp, level, g, radius * 0.85, 4, gold, white, Opt.of("h", 16.0, "life", 0.6, "jitter", 24.0)));
        // 떨어짐: 하늘 자리 → 칼끝이 땅속 1.5칸 (가운데 = 땅 + L/2 - 1.5)
        double rest = len / 2 - 1.5;
        Tasks.later(delay - drop, () -> {
            RuneBoost.rfx2a(level, "sword", V.up(g, sky), Opt.of("dir", V.DOWN, "size", len / 2, "life", drop / 20.0, "speed", (sky - rest) / (drop / 20.0)), glow, white);
            for (double s = 2; s < sky - rest; s += 3) {
                double d = (s / (sky - rest)) * (drop / 20.0);
                SkillFx.fx(level, "rpg:arrow_trail", V.up(g, sky - s + len / 2), Opt.of("dir", V.DOWN, "size", 1.6, "thick", 0.9, "life", d + 0.3, "delay", d,
                        "color", glow, "alpha", 0.7));
            }
            SkillFx.play(level, "item.trident.riptide_3", g, 0.6, 1);
        });
        // 꽂힌 검 (1.65초, 마지막 40% 동안 사라짐): 떨어지는 검이 끝나기 1틱 전에 나타남 (깜빡임 없게)
        Tasks.later(delay - 1, () -> RuneBoost.rfx2a(level, "sword", V.up(g, rest), Opt.of("dir", V.DOWN, "size", len / 2, "life", 1.65, "fade", 0.4), glow, white));
        Tasks.later(delay, () -> {
            if (sp.isRemoved()) return;
            Vec3 mid = V.up(g, 1.0);
            // 발동: 화면 번쩍임 · 갈라진 하늘의 섬광 → 충격 고리 · 문양이 번쩍 (1틱 뒤) → 빛나는 금 (2틱 뒤 — 이 틱의 파티클 예산)
            RuneKit.ultFlashFor(sp, level, g, "heavenFall", "flash", Opt.empty());
            RuneBoost.impact(level, g, glow, gold, radius); // 0.48.2 2D 강화: 터짐 (번쩍임 · 고리 · 충격파 · 빛살 · 불티)
            RuneKit.skyFlare(level, rift, 4.6, gold, white, Opt.of("life", 0.6));
            RuneKit.later(1, () -> {
                RuneKit.novaRing(level, g, 2.2, 3.2, gold, white, Opt.of("life", 0.5));
                RuneKit.domainSigil(level, g, radius * 1.15, "holy", gold, white, Opt.of("life", 0.6, "grow", 0.18, "alpha", 1.0, "fade", 0.85));
            });
            RuneKit.later(2, () -> RuneKit.glowCrack(level, g, radius * 1.2, new double[] {1.0, 0.55, 0.15}, new double[] {1.0, 0.92, 0.7}, Opt.of("life", 2.4,
                    "fade", 0.6)));
            // 칼밑의 빛
            RuneKit.rfx(level, "prop", V.up(g, rest + len / 2 - 0.4), Opt.of("size", 2.2, "life", 0.5, "cell", (double) RuneKit.RFX.get("glint")[0], "color", white,
                    "alpha", 1.0, "grow", 0.6, "fade", 0.6));
            SkillFx.burst(level, mid, gold, 5.5, 0.5);
            SkillFx.burst(level, mid, glow, 3.6, 0.4);
            SkillFx.burst(level, mid, white, 2.4, 0.3);
            SkillFx.fx(level, "rpg:arrow_spark", mid, Opt.of("dir", V.UP, "size", 4.0, "grow", 2.0, "life", 0.22, "color", white, "alpha", 1.0));
            for (int i = 0; i < 5; i++) {
                final int ii = i;
                RuneKit.later(i * 2, () -> SkillFx.shock(level, g, ii % 2 == 1 ? core : glow, 2.5 + ii * 1.5, 0.55 + ii * 0.1));
            }
            for (int i = 0; i < 12; i++) {
                Vec3 d = RuneKit.turn(new Vec3(1, 0, 0), i * 30 + Kit.rand() * 10);
                SkillFx.fx(level, "rpg:light_cut", V.add(V.up(g, 0.08), d, 2.6), Opt.of("dir", d, "size", 2.4, "thick", 0.45, "life", 1.0, "delay", 0.75, "color", glow,
                        "alpha", 0.95));
            }
            // 갈라진 땅 · 튀는 바위 · 먼지
            SkillFx.fx(level, "rpg:ground_crack", V.up(g, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", radius * 1.4, "life", 1.8, "color", CRACK,
                    "alpha", 0.95));
            for (int i = 0; i < 8; i++) {
                double a = (i / 8.0) * Math.PI * 2 + Kit.rand() * 0.4;
                SkillFx.fx(level, "rpg:boulder", V.up(g, 0.6), Opt.of("dir", V.unit(new Vec3(Math.cos(a), 1.1, Math.sin(a))), "speed", 7 + Kit.rand() * 3, "size", 0.3,
                        "life", 0.8, "spin", Kit.rand() * 360, "color", RuneKit.ROCK, "alpha", 1.0));
            }
            SkillFx.fx(level, "rpg:dust", V.up(g, radius * 0.3), Opt.of("size", radius * 0.8, "life", 0.9, "color", DUST, "alpha", 0.7));
            SkillFx.sparks(level, mid, gold, 18, 8);
            SkillFx.vanilla(level, "minecraft:huge_explosion_emitter", mid);
            SkillFx.play(level, "item.trident.thunder", g, 0.7, 1);
            SkillFx.play(level, "random.explode", g, 0.6, 1);
            SkillFx.play(level, "mace.heavy_smash_ground", g, 0.7, 1);
            Tasks.later(6, () -> SkillFx.play(level, "block.bell.hit", g, 0.5, 1));
            RuneKit.shakeAround(level, g, 18, 0.45, 0.4);
            int stun = (int) k.numOr("stun", 0); // 8단계 3돌: 맞은 적 기절
            int marks = 0;
            for (LivingEntity e : Kit.around(sp, g, radius, 4)) {
                if (RuneKit.runeHit(sp, e, P, k.num("mult"), r.stat()) > 0) {
                    RuneKit.knock(e, V.sub(e.position(), g), 0.5, k.num("lift"));
                    RuneKit.effect(e, "slowness", 40, 2);
                    if (stun > 0) RuneKit.stun(e, stun, gold, marks++ < 12);
                }
            }
        });
        return true;
    }
}
