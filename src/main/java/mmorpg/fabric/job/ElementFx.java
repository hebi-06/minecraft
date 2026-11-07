package mmorpg.fabric.job;

import mmorpg.core.Elements;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-07 원소 반응 그림 (ElementSystem): 머리 위 원소 그림 (rfx_jelem — 불꽃 · 물방울 · 번개 · 눈꽃 · 바위, 빛 0 ~ 4 · 심지 5 ~ 9 — 적마다 solo 로 바꿔 끼움),
 * 묻을 때 작은 빛, 반응마다 다른 그림 · 소리 · 이름 (피해 반응은 숫자 앞에 이름 — DamageNumbers REACT_*, 나머지는 이름만 떠오름).
 */
final class ElementFx {
    private ElementFx() {}

    /** 머리 위 원소 그림 (칸 = 원소의 cell · 심지 = cell + 5) */
    static final String ICON = "jelem";
    static final int ICON_CELLS = 5;
    /** 적마다 하나 (owner = 그 적) — 빛 · 심지 */
    private static final double SOLO_GLOW = 7301;
    private static final double SOLO_CORE = 7302;
    private static final double[] STEAM = {0.86, 0.9, 0.96};
    private static final double[] LAVA_GLOW = {1.0, 0.3, 0.04};
    private static final double[] LAVA_CORE = {1.0, 0.82, 0.32};
    private static final double[] MUD_GLOW = {0.36, 0.22, 0.1};
    private static final double[] MUD_CORE = {0.6, 0.44, 0.24};

    static double[] glow(String el) {
        Elements.Element e = Elements.get(el);
        return e == null ? RuneKit.WHITE : e.glow();
    }

    static double[] core(String el) {
        Elements.Element e = Elements.get(el);
        return e == null ? RuneKit.WHITE : e.core();
    }

    /** 머리 위 원소 그림 (blink = 곧 사라짐 — 작게) */
    static void icon(LivingEntity e, String el, boolean blink, double life) {
        if (!(e.level() instanceof ServerLevel level)) return;
        Elements.Element x = Elements.get(el);
        if (x == null) return;
        Vec3 at = RuneKit.overHead(e, 1.0);
        double size = blink && (Tasks.now() / 5) % 2 == 0 ? 0.22 : 0.3;
        Opt o = Opt.of("size", size, "life", life, "fade", 0.12, "vel", RuneKit.entVel(e), "owner", (double) e.getId());
        RuneKit.rfx(level, ICON, at, o.with("color", x.glow(), "cell", (double) x.cell(), "alpha", 0.95, "solo", SOLO_GLOW));
        RuneKit.rfx(level, ICON, at, o.with("color", x.core(), "cell", (double) (ICON_CELLS + x.cell()), "alpha", 1.0, "solo", SOLO_CORE));
    }

    /** 원소가 묻음 (fresh = 처음 묻음 — 테가 퍼짐): 몸에 작은 빛 · 머리 위 그림을 바로 */
    static void applied(LivingEntity e, String el, boolean fresh) {
        if (!(e.level() instanceof ServerLevel level)) return;
        icon(e, el, false, 0.8);
        if (!RuneKit.fxQuota("elemApply", 6)) return;
        Vec3 at = Kit.bodyPoint(e);
        SkillFx.burst(level, at, glow(el), fresh ? 0.75 : 0.5, 0.22);
        if (fresh) SkillFx.sparks(level, at, core(el), 4, 3);
    }

    /** 반응 그림 · 소리 · 이름 (first = 묻어 있던 원소 · then = 친 원소) */
    static void reaction(ServerPlayer sp, LivingEntity e, Elements.Reaction r, String first, String then) {
        if (!(e.level() instanceof ServerLevel level)) return;
        Vec3 at = Kit.bodyPoint(e);
        Vec3 feet = e.position();
        double pad = Kit.bodyPad(e);
        int[] grad = {mmorpg.core.Jobs.rgb(glow(first)), mmorpg.core.Jobs.rgb(glow(then))};
        boolean full = RuneKit.fxQuota("elemReact", 6);
        double rad = r.num("radius", 3);
        switch (r.id()) {
            case "vaporize" -> { // 증발: 하얀 증기가 터져 솟음 · 불빛 · 물빛 고리
                JobFx.burst(level, at, 1.5 + pad, glow("fire"), core("water"), 0.32, Opt.empty());
                if (full) {
                    JobFx.smoke(level, V.up(at, 0.2), 1.5 + pad, STEAM, RuneKit.WHITE, 0.9, Opt.of("alpha", 0.75));
                    JobFx.smoke(level, V.up(at, 1.0), 1.1 + pad, STEAM, RuneKit.WHITE, 1.1, Opt.of("alpha", 0.55, "vel", new Vec3(0, 1.2, 0)));
                    RuneKit.novaRing(level, feet, 0.5, 2.6, glow("fire"), glow("water"), Opt.of("life", 0.4));
                    SkillFx.sparks(level, at, core("water"), 10, 5);
                }
                SkillFx.play(level, "block.fire.extinguish", at, 0.9, 1.0);
                SkillFx.play(level, "entity.generic.extinguish_fire", at, 1.3, 0.8);
            }
            case "shatter" -> { // 쇄빙: 얼음이 바위에 부서져 사방으로 튐
                JobFx.burst(level, at, 1.6 + pad, glow("ice"), core("rock"), 0.3, Opt.empty());
                if (full) {
                    shards(level, at, 10, 6.5, 0.5);
                    RuneKit.rockBurst(level, at, 6, 4.0, 0.32);
                    JobFx.two(level, "jshatter2", JobFx.SHATTER, at, 0.45, Opt.of("size", 1.4 + pad, "spin", Kit.rand() * 360), glow("ice"), RuneKit.WHITE);
                }
                SkillFx.play(level, "block.glass.break", at, 0.7, 1.0);
                SkillFx.play(level, "block.stone.break", at, 0.8, 0.9);
            }
            case "overload" -> { // 과부하: 불과 번개가 엉켜 크게 폭발
                JobFx.burst(level, at, 2.2 + pad, glow("fire"), core("thunder"), 0.38, Opt.empty());
                if (full) {
                    SkillFx.fx(level, "rpg:fire_wall", feet, Opt.of("size", rad * 1.1, "life", 0.6, "color", glow("fire"), "alpha", 0.95, "spin", Kit.rand() * 360));
                    RuneKit.novaRing(level, feet, 0.8, rad * 1.1, glow("fire"), glow("thunder"), Opt.of("life", 0.45));
                    SkillFx.shock(level, feet, glow("thunder"), rad * 0.9, 0.45);
                    for (int i = 0; i < 5; i++) {
                        double a = Kit.rand() * Math.PI * 2;
                        Vec3 to = V.add(at, new Vec3(Math.cos(a), (Kit.rand() - 0.3) * 0.8, Math.sin(a)), rad * (0.6 + Kit.rand() * 0.4));
                        RuneKit.zap(level, at, to, glow("thunder"), core("thunder"), 0.16, 0.28);
                    }
                }
                SkillFx.play(level, "random.explode", at, 1.2, 0.8);
                SkillFx.play(level, "item.trident.thunder", at, 1.6, 0.5);
            }
            case "thermal" -> { // 열충격: 얼음이 불에 터지며 증기와 얼음 조각
                JobFx.burst(level, at, 2.0 + pad, glow("fire"), core("ice"), 0.36, Opt.empty());
                if (full) {
                    JobFx.smoke(level, V.up(at, 0.3), 1.6 + pad, STEAM, RuneKit.WHITE, 0.85, Opt.of("alpha", 0.7));
                    shards(level, at, 8, 6.0, 0.42);
                    RuneKit.novaRing(level, feet, 0.6, rad, glow("ice"), glow("fire"), Opt.of("life", 0.45));
                }
                SkillFx.play(level, "block.glass.break", at, 1.3, 0.8);
                SkillFx.play(level, "block.fire.extinguish", at, 1.2, 0.7);
            }
            case "shock" -> { // 감전: 젖은 몸에 전기가 휘감겨 멈춤
                RuneKit.plasmaOrb(level, at, 0.85 + pad, glow("thunder"), Opt.of("life", 1.0, "vel", RuneKit.entVel(e)));
                if (full) SkillFx.every(5, 4, i -> {
                    if (e.isRemoved()) return;
                    Vec3 c = Kit.bodyPoint(e);
                    double a = Kit.rand() * Math.PI * 2;
                    double h = 0.6 + pad;
                    RuneKit.zap(level, V.add(c, new Vec3(Math.cos(a), 0.5, Math.sin(a)), h), V.add(c, new Vec3(-Math.cos(a), -0.4, -Math.sin(a)), h), glow("thunder"),
                            core("thunder"), 0.12, 0.2);
                });
                label(e, r.name() + "!", grad, 1.7F);
                SkillFx.play(level, "item.trident.thunder", at, 1.8, 0.45);
                SkillFx.play(level, "entity.generic.splash", at, 1.5, 0.4);
            }
            case "freeze" -> { // 빙결: 젖은 몸이 얼음으로 굳음
                JobFx.frost(e, glow("ice"), core("ice"), (int) r.num("ticks", 20) + 8);
                if (full) {
                    for (int i = 0; i < 3; i++) {
                        double a = i * 2.1 + Kit.rand();
                        RuneKit.iceCrystal(sp, level, V.add(feet, new Vec3(Math.cos(a), 0, Math.sin(a)), 0.5 + pad), 0.55 + pad * 0.4, glow("ice"), Opt.of("life", 1.1,
                                "v", (double) i));
                    }
                    SkillFx.burst(level, at, core("water"), 1.0 + pad, 0.25);
                }
                label(e, r.name() + "!", grad, 1.7F);
                SkillFx.play(level, "block.glass.place", at, 1.2, 0.9);
                SkillFx.play(level, "block.powder_snow.break", at, 0.8, 1.0);
            }
            case "mud" -> { // 진흙: 발밑이 질척한 진흙이 되어 묶음
                JobFx14.pool(level, V.up(feet, 0.06), 1.3 + pad, 0, 6, r.num("ticks", 40) / 20.0, false, Opt.empty(), MUD_GLOW, MUD_CORE);
                if (full) SkillFx.fx(level, "rpg:splash", V.up(feet, 0.2), Opt.of("size", 1.4 + pad, "life", 0.5, "color", MUD_CORE, "alpha", 0.95));
                label(e, r.name() + "!", grad, 1.7F);
                SkillFx.play(level, "block.mud.place", at, 0.8, 1.0);
                SkillFx.play(level, "entity.generic.splash", at, 0.7, 0.6);
            }
            case "superconduct" -> { // 초전도: 서리 번개 고리가 퍼지며 둘레를 얼려 금 가게 함
                JobFx.two(level, "jvolt2", JobFx.VOLT, V.up(feet, 0.1), 0.6, Opt.of("dir", V.UP, "size", rad / 0.8, "spin", Kit.rand() * 360), glow("thunder"),
                        core("ice"));
                if (full) {
                    RuneKit.novaRing(level, feet, 0.5, rad, glow("ice"), glow("thunder"), Opt.of("life", 0.5));
                    RuneKit.plasmaOrb(level, at, 0.7 + pad, glow("ice"), Opt.of("life", 0.4));
                }
                label(e, r.name() + "!", grad, 1.7F);
                SkillFx.play(level, "block.beacon.deactivate", at, 1.7, 0.7);
                SkillFx.play(level, "block.glass.break", at, 1.6, 0.5);
            }
            case "lava" -> { // 용암: 발밑이 끓는 용암이 됨 (불타는 땅 — 그림은 lavaPulse)
                label(e, r.name() + "!", grad, 1.7F);
                SkillFx.play(level, "item.bucket.empty_lava", at, 0.9, 1.0);
            }
            case "magnet" -> { // 자력: 바위 조각과 번개가 돌며 둘레 적을 끌어모음
                RuneKit.vortexAt(level, feet, 1.6 + pad, glow("rock"), Opt.of("life", 0.6, "alpha", 0.9));
                if (full) {
                    RuneKit.rockBurst(level, V.up(feet, 0.4), 5, 2.5, 0.28);
                    RuneKit.plasmaOrb(level, at, 0.7 + pad, glow("thunder"), Opt.of("life", 0.5));
                    RuneKit.novaRing(level, feet, rad, -rad * 0.9, glow("rock"), glow("thunder"), Opt.of("life", 0.4));
                }
                label(e, r.name() + "!", grad, 1.7F);
                SkillFx.play(level, "block.respawn_anchor.charge", at, 1.3, 0.8);
                SkillFx.play(level, "block.anvil.land", at, 0.6, 0.3);
            }
            default -> label(e, r.name() + "!", grad, 1.7F);
        }
    }

    /** 반응 이름이 떠오름 (피해 없는 반응 — 두 원소 색 그라데이션) */
    private static void label(LivingEntity e, String text, int[] grad, float scale) {
        DamageNumbers.showLabel(e, text, grad, scale, 0.25);
    }

    /** 얼음 조각이 사방으로 (rfx_icicle — 나아가는 쪽으로 누움) */
    private static void shards(ServerLevel level, Vec3 at, int n, double speed, double size) {
        for (int i = 0; i < n; i++) {
            double a = (double) i / n * Math.PI * 2 + Kit.rand() * 0.4;
            Vec3 d = V.unit(new Vec3(Math.cos(a), 0.3 + Kit.rand() * 0.6, Math.sin(a)));
            RuneKit.rfx2(level, "icicle", V.add(at, d, 0.3), Opt.of("dir", d, "speed", speed * (0.8 + Kit.rand() * 0.4), "size", size, "life", 0.32, "fade", 0.3),
                    glow("ice"), RuneKit.WHITE);
        }
    }

    /** 불타는 땅 한 번 (k 번째 · 모두 n 번): 처음 = 용암이 튀며 퍼짐 · 가운데 = 끓음 (불꽃 · 불티) · 끝 = 식어 사라짐 */
    static void lavaPulse(ServerLevel level, Vec3 c, double radius, int k, int n) {
        double every = 0.5;
        Vec3 g = V.up(c, 0.06);
        if (k == 0) JobFx14.pool(level, g, radius, 0, 2, 0.3, false, Opt.empty(), LAVA_GLOW, LAVA_CORE);
        int f = k == n - 1 ? 6 : 2 + (k % 4);
        JobFx14.pool(level, g, radius, f, k == n - 1 ? 2 : 1, k == n - 1 ? 0.6 : every + 0.1, false, Opt.of("spin", 0.0), LAVA_GLOW, LAVA_CORE);
        if (!RuneKit.fxQuota("elemLava", 4)) return;
        for (int i = 0; i < 3; i++) {
            double a = Kit.rand() * Math.PI * 2;
            Vec3 p = V.add(c, new Vec3(Math.cos(a), 0, Math.sin(a)), radius * Math.sqrt(Kit.rand()) * 0.85);
            SkillFx.fx(level, "rpg:fire_wall", p, Opt.of("size", 0.9, "life", 0.55, "color", LAVA_GLOW, "alpha", 0.9, "spin", Kit.rand() * 360));
        }
        SkillFx.sparks(level, V.up(c, 0.3), LAVA_CORE, 4, 3);
        if (k % 2 == 0) SkillFx.play(level, "block.lava.pop", c, 0.9 + Kit.rand() * 0.3, 0.7);
    }

    /** 자력: 끌려오는 적에서 가운데로 바위빛 · 번개 줄 */
    static void magnetLine(ServerLevel level, LivingEntity from, LivingEntity to, String first, String then) {
        if (!RuneKit.fxQuota("elemMagnet", 6)) return;
        RuneKit.zap(level, Kit.bodyPoint(from), Kit.bodyPoint(to), glow("rock"), core("thunder"), 0.14, 0.3);
    }
}
