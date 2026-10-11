package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Runes;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 원소 검사 룬 (runes.js "v2.14.0 원소 검사" — 마력 + 힘 · 민첩 절반: 검에 불 · 얼음 · 번개를 실음): 화염 베기 · 서리 일섬 · 뇌광 베기 · 삼원소 연격 · 원소 해방.
 * 검 = 공격 피해 (노란 숫자, RuneKit.swordHit) · 원소 = 마법 피해 (보라 숫자, RuneKit.spellHit). 색은 원소마다 (RUNE_ELEMENTS)
 */
final class RuneElement {
    private RuneElement() {}

    private static final Obj ELEMENTS = Rules.get().obj("RUNE_ELEMENTS");
    /** 삼원소 연격 · 원소 해방의 차례 */
    private static final String[] ORDER = {"fire", "ice", "thunder"};

    static void register() {
        RuneCast.register("flameSlash", RuneElement::flameSlash);
        RuneCast.register("frostThrust", RuneElement::frostThrust);
        RuneCast.register("thunderSlash", RuneElement::thunderSlash);
        RuneCast.register("triElement", RuneElement::triElement);
        RuneCast.register("elementRelease", RuneElement::elementRelease);
    }

    /** 원소 색 {빛, 심지} (RUNE_ELEMENTS[el].color) */
    static double[][] colorOf(String el) {
        List<Object> c = ELEMENTS.obj(el).list("color");
        return new double[][] {Kit.rgb(c.get(0)), Kit.rgb(c.get(1))};
    }

    /** 원소가 맞은 적에게 남기는 것: 불 = 불붙음 (fire 초) · 얼음 = 구속 (slowAmp, slowTicks 틱). 번개는 thunderChain */
    private static void elementEffect(LivingEntity e, String el, Obj k) {
        if (el.equals("fire")) RuneKit.burn(e, k.numOr("fire", 3));
        else if (el.equals("ice")) RuneKit.effect(e, "slowness", (int) k.numOr("slowTicks", 40), (int) k.numOr("slowAmp", 0));
    }

    /**
     * 원소의 맞힌 자리 연출: 불 = 발밑에서 타오르는 불꽃 · 얼음 = 발밑에 얼음 결정 (rpg:rfx_crystal) · 번개 = 몸에 번개 구슬 (rpg:rfx_plasma).
     * fxDelay 틱 뒤에 그림 (적이 많을 때 한 틱의 파티클을 나눔 — 피해는 그대로)
     */
    private static void elementHitFx(ServerLevel level, LivingEntity e, String el, int fxDelay) {
        if (e == null || e.isRemoved()) return;
        if (fxDelay > 0) {
            RuneKit.later(fxDelay, () -> elementHitFx(level, e, el, 0));
            return;
        }
        if (!RuneKit.fxQuota("elementHit", 5)) return; // 한 틱에 5명까지 (적 24명을 한꺼번에 맞혀도 파티클 예산 안 — 피해 · 원소 효과는 모두)
        double[][] cs = colorOf(el);
        double[] glow = cs[0];
        double[] core = cs[1];
        Vec3 at = Kit.bodyPoint(e);
        Vec3 feet = e.position();
        if (el.equals("fire")) {
            SkillFx.fx(level, "rpg:fire_wall", feet, Opt.of("size", 1.6, "life", 0.6, "color", glow, "alpha", 0.95, "spin", Kit.rand() * 360));
            SkillFx.sparks(level, at, core, 4, 4);
        } else if (el.equals("ice")) {
            double pad = Kit.bodyPad(e);
            double s = 0.6 + pad * 0.5;
            int[] cells = {0, RuneKit.CRYSTAL_CORE};
            RuneKit.rfx2(level, "crystal", V.add(feet, new Vec3(0.3, 0, 0.2), 1 + pad), Opt.of("size", s, "life", 0.9, "grow", 0.1, "fade", 0.3, "cell",
                    Math.floor(Kit.rand() * 3), "alpha", 0.95), glow, RuneKit.WHITE, cells, null);
            RuneKit.rfx2(level, "crystal", V.add(feet, new Vec3(-0.3, 0, -0.25), 1 + pad), Opt.of("size", s * 0.8, "life", 0.85, "grow", 0.1, "fade", 0.3, "cell",
                    Math.floor(Kit.rand() * 3), "alpha", 0.95), glow, RuneKit.WHITE, cells, null);
            SkillFx.burst(level, at, core, 0.8, 0.22);
        } else {
            SkillFx.burst(level, at, glow, 0.9, 0.22);
            RuneKit.plasmaOrb(level, at, 0.5 + Kit.bodyPad(e) * 0.4, glow, Opt.of("life", 0.26));
            SkillFx.sparks(level, at, core, 3, 6);
        }
    }

    /**
     * 번개가 튐: from 적의 range 칸 안에서 아직 맞지 않은 (hit · skip 에 없는) 가장 가까운 적 하나에게 위력 × mult (마법). 튄 적 (없으면 null).
     * 튄 적은 hit 에 넣음 (skip = 건너뛰기만 하는 적 — 원소 해방: 검기가 닿을 적). fxDelay = 그림만 늦춤 (피해는 지금)
     */
    private static LivingEntity thunderChain(ServerPlayer sp, LivingEntity from, double P, double mult, double range, Set<UUID> hit, Set<UUID> skip, int fxDelay) {
        if (from == null || from.isRemoved()) return null;
        List<LivingEntity> cand = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, from.position(), range, 3)) {
            UUID id = e.getUUID();
            if (hit.contains(id) || (skip != null && skip.contains(id)) || id.equals(from.getUUID())) continue;
            cand.add(e);
        }
        List<LivingEntity> near = RuneKit.nearestTo(cand, from.position());
        if (near.isEmpty()) return null;
        LivingEntity next = near.get(0);
        hit.add(next.getUUID());
        ServerLevel level = sp.level();
        double[][] cs = colorOf("thunder");
        Vec3 a = Kit.bodyPoint(from);
        Vec3 to = Kit.bodyPoint(next);
        Runnable draw = () -> {
            RuneKit.zap(level, a, to, cs[0], cs[1], 0.18, 0.3);
            SkillFx.play(level, "ambient.weather.lightning.impact", to, 1.8, 0.2);
        };
        if (fxDelay > 0) RuneKit.later(fxDelay, draw);
        else draw.run();
        if (RuneKit.spellHit(sp, next, P, mult) > 0) elementHitFx(level, next, "thunder", fxDelay);
        return next;
    }

    /** 불꽃이 앞 땅을 따라 번짐 (1인칭 화면 아래쪽에 보임): 부채꼴 cone 도, 거리 dist, n 장 */
    private static void flameFan(ServerPlayer sp, double[] glow, double cone, double dist, int n) {
        ServerLevel level = sp.level();
        Vec3 f = V.flat(sp.getViewVector(1F));
        for (int i = 0; i < n; i++) {
            Vec3 d = RuneKit.turn(f, -cone / 2 + cone * i / (n - 1));
            SkillFx.fx(level, "rpg:fire_wall", V.add(sp.position(), d, dist + (i % 2) * 0.6), Opt.of("size", 1.4, "life", 0.55 + i * 0.03, "color", glow, "alpha", 0.9,
                    "spin", Kit.rand() * 360));
        }
    }

    /** 원소 초승달 검기 (rpg:rfx_wave): 눈앞 2.3칸에서 앞으로 퍼짐 (vertical = 세로) */
    private static void elementWave(ServerPlayer sp, String el, boolean vertical, double size) {
        double[][] cs = colorOf(el);
        Vec3 eye = sp.getEyePosition();
        Vec3 f = V.flat(sp.getViewVector(1F));
        RuneKit.rfx2(sp.level(), "wave", V.up(V.add(eye, f, 2.3), vertical ? -0.1 : -0.35), Opt.of("dir", f, "speed", 6.0, "size", size, "life", 0.24, "grow", 0.35,
                "fade", 0.6, "spin", vertical ? 90.0 : 4.0, "alpha", 0.92), cs[0], cs[1]);
    }

    /**
     * 찌르는 빛의 칼 (rpg:rfx_sword, 원소 · 검사 색): 오른손 (몸 높이 from 의 오른쪽 위) 에서 찌르는 끝 (from + f × reach) 까지 칼끝이 뻗음 (3틱) 뒤 잠깐 머묾.
     * 1인칭에서 칼이 보는 쪽과 나란해 점으로 보이지 않게 손에서 비스듬히 뻗음. 머무는 칼은 2틱에 나타남 (뻗는 칼과 1틱 겹침 — 깜빡임 없게).
     * 검사 계열의 삼단베기 (tripleCut) 도 씀
     */
    static void elementThrust(ServerLevel level, Vec3 from, Vec3 f, double reach, double[] glow, double[] core) {
        double half = 0.9;
        Vec3 hand = V.up(V.add(from, V.rightOf(f), 0.3), 0.15);
        Vec3 end = V.add(from, f, reach);
        Vec3 dv = V.unit(V.sub(end, hand));
        double lv = V.len(V.sub(end, hand));
        double s0 = Math.min(0.6 + half, lv);
        RuneKit.rfx2(level, "sword", V.add(hand, dv, s0), Opt.of("dir", dv, "speed", Math.max(0, lv - s0 - half) / 0.15, "size", half, "life", 0.15), glow, core);
        RuneKit.later(2, () -> RuneKit.rfx2(level, "sword", V.add(hand, dv, Math.max(s0, lv - half)), Opt.of("dir", dv, "size", half, "life", 0.25, "fade", 0.6),
                glow, core));
    }

    /** 화염 베기: 앞 cone 도 · range 칸을 불꽃 검으로 벰 (검, 공격 피해) + 불붙음 fire 초. 앞 땅에 불꽃이 번짐 — 불꽃 초승달 검기 */
    static boolean flameSlash(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 head = sp.getEyePosition();
        Vec3 f = V.flat(sp.getViewVector(1F));
        double[][] cs = colorOf("fire");
        double[] glow = cs[0];
        double[] core = cs[1];
        SkillFx.arc(level, V.up(V.add(head, f, 1.4), -0.3), f, Opt.of("shape", "heavy", "flip", false, "size", 2.9, "life", 0.28, "speed", 3.0, "spin", -6.0,
                "color", core, "aura", glow));
        elementWave(sp, "fire", false, 1.65);
        SkillFx.fx(level, "rpg:light_cut", V.up(V.add(head, f, 2.0), -0.25), Opt.of("dir", V.rightOf(f), "size", 2.6, "thick", 0.2, "life", 0.32, "delay", 0.04,
                "color", glow, "alpha", 1.0));
        flameFan(sp, glow, 96, 2.4, 5);
        SkillFx.play(level, "item.firecharge.use", head, 1.1, 0.9);
        SkillFx.play(level, "item.spear.attack_miss", head, 1.0, 0.9);
        int n = 0;
        for (LivingEntity e : RuneKit.coneTargets(sp, k.num("range"), k.num("cone"), 10)) {
            if (RuneKit.swordHit(sp, e, P, k.num("mult")) <= 0) continue;
            elementEffect(e, "fire", k);
            elementHitFx(level, e, "fire", n / 4);
            Vec3 at = Kit.bodyPoint(e);
            SkillFx.cutMark(level, at, V.unit(V.sub(at, head)), -8, 0.1, core, RuneKit.WHITE, 1.0);
            n++;
        }
        return true;
    }

    /**
     * 서리 일섬: 앞 range 칸 (벽 앞까지) 폭 width 를 찌름 (검, 공격 피해) → 땅에 서리 줄 → gap 틱 뒤 그 줄에 얼음 가시가 솟음
     * (얼음, 마법 피해: 그때 줄 위의 적) + 구속 (slowAmp, slowTicks) — 얼음빛 칼 (rpg:rfx_sword) 이 뻗고 고드름 둘이 나란히 날아감,
     * 솟는 가시 = 얼음 결정 (rpg:rfx_crystal, 쓴 사람 1.4칸 안은 비움)
     */
    static boolean frostThrust(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 f = V.flat(sp.getViewVector(1F));
        Vec3 feet = sp.position();
        Vec3 from = V.up(V.add(feet, f, 0.5), 1.1);
        double len = Math.max(1, RuneKit.rayLength(level, from, f, k.num("range")));
        double[][] cs = colorOf("ice");
        double[] glow = cs[0];
        double[] core = cs[1];
        double gap = k.num("gap");
        double width = k.num("width");
        elementThrust(level, from, f, len, glow, RuneKit.WHITE);
        for (int sd : new int[] {-1, 1}) {
            Vec3 o = V.add(from, V.rightOf(f), sd * 0.45);
            double s0 = Math.min(1.4, len);
            RuneKit.rfx2(level, "icicle", V.add(o, f, s0), Opt.of("dir", f, "speed", 60.0, "size", 0.34, "life", Math.max(0.05, (len - s0) / 60)), glow, RuneKit.WHITE);
        }
        SkillFx.fx(level, "rpg:light_cut", V.add(from, f, len / 2), Opt.of("dir", f, "size", len / 2, "thick", 0.2, "life", 0.3, "color", core, "alpha", 1.0));
        // 서리 줄 (가시가 솟을 자리)
        for (double s = 0.9; s <= len + 0.5; s += 1.0) {
            SkillFx.fx(level, "rpg:light_cut", V.up(V.add(feet, f, s), 0.08), Opt.of("dir", f, "size", 0.5, "thick", 0.25, "life", gap / 20 + 0.1, "color", glow,
                    "alpha", 0.9));
        }
        SkillFx.play(level, "item.trident.throw", from, 1.3, 0.9);
        SkillFx.play(level, "random.glass", from, 1.8, 0.4);
        for (Kit.LineHit h : RuneKit.lineTargets(sp, from, f, len, width)) {
            if (RuneKit.swordHit(sp, h.e(), P, k.num("mult")) > 0) SkillFx.burst(level, Kit.bodyPoint(h.e()), core, 0.7, 0.2);
        }
        String life = RuneKit.lifeToken(sp);
        RuneKit.later(gap, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 p0 = sp.position();
            for (double s = 0.9; s <= len + 0.5; s += 0.9) {
                Vec3 q = V.add(feet, f, s);
                Vec3 at = new Vec3(q.x, RuneKit.groundAt(level, q, feet.y), q.z);
                if (Math.hypot(at.x - p0.x, at.z - p0.z) < 1.4) continue;
                RuneKit.iceCrystal(sp, level, at, (0.62 + (Math.round(s * 10) % 3) * 0.1) * (0.9 + Kit.rand() * 0.2), glow, Opt.of("life", 0.95, "fade", 0.3,
                        "alpha", 0.95));
            }
            SkillFx.sparks(level, V.add(from, f, len / 2), core, 8, 4);
            SkillFx.vanilla(level, "minecraft:snowflake_particle", V.add(from, f, len / 2));
            SkillFx.play(level, "mob.player.hurt_freeze", from, 1.0, 0.8);
            SkillFx.play(level, "break.amethyst_cluster", V.add(from, f, len / 2), 0.9, 1);
            int n = 0;
            for (Kit.LineHit h : RuneKit.lineTargets(sp, from, f, len, width)) {
                LivingEntity e = h.e();
                if (RuneKit.spellHit(sp, e, P, k.num("mult")) <= 0) continue;
                elementEffect(e, "ice", k);
                elementHitFx(level, e, "ice", n++ / 4);
            }
        });
        return true;
    }

    /**
     * 뇌광 베기: 앞 cone 도 · range 칸 베기 (검, 공격 피해) → gap 틱 뒤 벤 적마다 하늘에서 번개 (번개, 마법 피해)
     * → 그 적의 chain 칸 안 아직 맞지 않은 적 하나에게 튐 (위력 × chainMult, 마법 — 적마다 한 번) —
     * 번개빛 초승달 검기, 하늘에서 내리치는 번개 그림 (rpg:rfx_bolt) · 몸에 번개 구슬. 적이 많으면 그림을 두 명씩 한 틱 늦게 (피해는 같은 틱)
     */
    static boolean thunderSlash(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        Vec3 head = sp.getEyePosition();
        Vec3 f = V.flat(sp.getViewVector(1F));
        double[][] cs = colorOf("thunder");
        double[] glow = cs[0];
        double[] core = cs[1];
        SkillFx.arc(level, V.up(V.add(head, f, 1.4), -0.3), f, Opt.of("shape", "arc", "flip", true, "size", 2.9, "life", 0.24, "speed", 3.0, "spin", 8.0,
                "color", core, "aura", glow));
        elementWave(sp, "thunder", false, 1.6);
        // 칼날을 따라 튀는 전기
        RuneKit.zap(level, V.up(V.add(head, RuneKit.turn(f, -45), 2.0), -0.45), V.up(V.add(head, RuneKit.turn(f, 45), 2.0), -0.45), glow, core, 0.12, 0.25);
        SkillFx.play(level, "item.spear.attack_miss", head, 1.1, 0.9);
        SkillFx.play(level, "item.trident.thunder", head, 1.8, 0.3);
        List<LivingEntity> cut = new ArrayList<>();
        for (LivingEntity e : RuneKit.coneTargets(sp, k.num("range"), k.num("cone"), 10)) {
            if (RuneKit.swordHit(sp, e, P, k.num("mult")) <= 0) continue;
            cut.add(e);
            SkillFx.sparks(level, Kit.bodyPoint(e), core, 4, 5);
        }
        String life = RuneKit.lifeToken(sp);
        RuneKit.later(k.num("gap"), () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Set<UUID> hit = new HashSet<>();
            for (LivingEntity e : cut) hit.add(e.getUUID());
            for (int idx = 0; idx < cut.size(); idx++) {
                LivingEntity e = cut.get(idx);
                if (e.isRemoved()) continue;
                int fd = idx / 2; // 그림만 두 명씩 한 틱 늦게
                Vec3 feet = e.position();
                Runnable strike = () -> RuneKit.rfx2(level, "bolt", V.up(feet, 3.6), Opt.of("size", 3.6, "life", 0.26, "cell", Math.floor(Kit.rand() * 4)), glow,
                        RuneKit.WHITE, new int[] {0, RuneKit.BOLT_CORE}, null);
                if (fd > 0) RuneKit.later(fd, strike);
                else strike.run();
                if (RuneKit.spellHit(sp, e, P, k.num("mult")) > 0) elementHitFx(level, e, "thunder", fd);
                thunderChain(sp, e, P, k.num("chainMult"), k.num("chain"), hit, null, fd);
            }
            if (!cut.isEmpty()) SkillFx.play(level, "ambient.weather.lightning.impact", sp.position(), 1.4, 0.4);
        });
        return true;
    }

    /**
     * 삼원소 연격: gap 틱마다 — 불 가로 베기 (앞 cone 도 · range 칸, 검 + 불붙음) → 얼음 세로 베기 (검 + 구속)
     * → 번개 찌르기 (thrust 칸 직선 폭 1.4, 마법 + chain 칸 안 아직 맞지 않은 적 하나에게 위력 × chainMult) —
     * 불 · 얼음 초승달 검기 (rpg:rfx_wave 가로 · 세로), 얼음 결정, 번개빛 칼 (rpg:rfx_sword) 찌르기
     */
    static boolean triElement(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        String life = RuneKit.lifeToken(sp);
        SkillFx.every((int) k.num("hits"), (int) k.num("gap"), i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 eye = sp.getEyePosition();
            Vec3 f = V.flat(sp.getViewVector(1F));
            String el = ORDER[i % 3];
            double[][] cs = colorOf(el);
            double[] glow = cs[0];
            double[] core = cs[1];
            if (!el.equals("thunder")) {
                boolean vertical = el.equals("ice");
                SkillFx.arc(level, V.up(V.add(eye, f, 1.5), -0.3), f, Opt.of("shape", vertical ? "thin" : "heavy", "flip", vertical, "size", vertical ? 2.5 : 2.8,
                        "life", 0.26, "speed", 3.0, "spin", vertical ? 84.0 : -6.0, "color", core, "aura", glow));
                elementWave(sp, el, vertical, vertical ? 1.35 : 1.6);
                if (el.equals("fire")) {
                    flameFan(sp, glow, 80, 2.2, 4);
                    SkillFx.play(level, "item.firecharge.use", eye, 1.2, 0.8);
                } else {
                    for (int j = 0; j < 3; j++) {
                        RuneKit.iceCrystal(sp, level, V.add(sp.position(), RuneKit.turn(f, -30 + j * 30), 2.3), 0.6, glow, Opt.of("life", 0.6, "fade", 0.35, "v", (double) j));
                    }
                    SkillFx.play(level, "random.glass", eye, 1.5, 0.5);
                }
                SkillFx.play(level, "item.spear.attack_miss", eye, 1.1 + i * 0.15, 0.9);
                int n = 0;
                for (LivingEntity e : RuneKit.coneTargets(sp, k.num("range"), k.num("cone"), 10)) {
                    if (RuneKit.swordHit(sp, e, P, k.num("mult")) <= 0) continue;
                    elementEffect(e, el, k);
                    elementHitFx(level, e, el, n++ / 4);
                }
            } else {
                Vec3 from = V.up(V.add(sp.position(), f, 0.5), 1.1);
                double len = Math.max(1, RuneKit.rayLength(level, from, f, k.num("thrust")));
                RuneKit.zap(level, from, V.add(from, f, len), glow, core, 0.26, 0.35);
                elementThrust(level, from, f, len, glow, RuneKit.WHITE);
                SkillFx.ring(level, V.add(from, f, len), f, glow, 0.9, 0.3, 0);
                SkillFx.play(level, "item.trident.thunder", eye, 1.4, 0.6);
                List<LivingEntity> list = new ArrayList<>();
                for (Kit.LineHit h : RuneKit.lineTargets(sp, from, f, len, 1.4)) list.add(h.e());
                Set<UUID> hit = new HashSet<>();
                for (LivingEntity e : list) hit.add(e.getUUID());
                for (int idx = 0; idx < list.size(); idx++) {
                    LivingEntity e = list.get(idx);
                    int fd = idx / 2;
                    if (RuneKit.spellHit(sp, e, P, k.num("mult")) > 0) elementHitFx(level, e, "thunder", fd);
                    thunderChain(sp, e, P, k.num("chainMult"), k.num("chain"), hit, null, fd);
                }
            }
        });
        return true;
    }

    /** 원소 해방의 검기가 닿을 적: 원소 · 검기 방향 · 닿는 틱 (쓴 틱부터) */
    private record Planned(LivingEntity e, String el, Vec3 d, long at) {}

    /**
     * 원소 해방: charge 틱 동안 눈앞의 거대한 검 (rpg:rfx_sword) 에 불 → 얼음 → 번개가 차례로 감김 → 거대한 원소 검기 셋 (rpg:rfx_wave) 을 부채꼴로
     * (가운데 불 · 왼쪽 얼음 · 오른쪽 번개, ±spread 도), 각각 range 칸 (벽 앞까지) 폭 width 를 speed 칸/초로 꿰뚫음 — 지나간 자리에 불꽃 · 얼음 가시 · 번개.
     * 적마다 한 번 (먼저 닿은 검기 · 튄 번개): 위력 × mult (마법) + 그 원소 (불붙음 · 구속 · chain 칸 안 한 명에게 위력 × chainMult).
     * 초월 2차 강화: 모으는 동안 하늘빛이 불 → 얼음 → 번개로 바뀜 · 발밑과 앞쪽 하늘의 비전 문양도 그 원소 색으로 → 발동 때 하늘의 섬광
     */
    static boolean elementRelease(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        int charge = (int) k.num("charge");
        SkillFx.play(level0, "mob.evocation_illager.prepare_attack", sp.position(), 1.0, 1);
        SkillFx.play(level0, "beacon.power", sp.position(), 1.3, 0.8);
        // 2차 강화 — 예고: 하늘빛 (원소마다) · 발밑의 비전 문양 · 앞쪽 하늘의 원소 문양
        List<Object> skies = RuneKit.ultOf("elementRelease").listOrNull("fogs");
        int third = (int) Math.max(1, Math.round(charge / 3.0));
        for (int j = 0; j < ORDER.length; j++) {
            final int jj = j;
            String el = ORDER[j];
            RuneKit.later(j * third, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double[][] c = colorOf(el);
                boolean last = jj == ORDER.length - 1;
                String fog = skies != null && jj < skies.size() && skies.get(jj) instanceof String s ? s : null;
                ServerLevel level = sp.level();
                RuneKit.ultSkyFor(sp, level, sp.position(), "elementRelease", last ? charge - jj * third + 34 : third + 2, fog);
                RuneKit.domainSigil(level, sp.position(), 5.2, "arcane", c[0], c[1], Opt.of("life", (last ? charge - jj * third + 2 : third + 1) / 20.0,
                        "spinRate", 70.0, "grow", 0.12, "alpha", 0.82, "fade", 0.1));
                RuneKit.skyPlate(sp, level, "sigil", RuneKit.skyPoint(sp, sp.position(), 12, 21, 6, 22), 5, c[0], c[1], Opt.of("cell",
                        (double) RuneKit.SIGIL.get("arcane"), "life", (last ? charge - jj * third + 4 : third + 1) / 20.0, "spinRate", 50.0, "alpha", 0.75, "fade", 0.15));
            });
        }
        for (int t = 0; t < charge; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                Vec3 f = V.flat(sp.getViewVector(1F));
                double q = (tt + 2.0) / charge;
                String el = ORDER[Math.max(0, Math.min(2, (int) Math.floor(q * 3 - 1e-6)))];
                double[][] cs = colorOf(el);
                Vec3 hilt = V.up(V.add(sp.position(), f, 2.4), 0.5);
                double ls = 1.6 + q * 2.6;
                RuneKit.rfx2(level, "sword", V.up(hilt, ls / 2), Opt.of("dir", V.UP, "size", ls / 2, "life", 0.13), cs[0], cs[1]);
                // 검 둘레를 돌며 감기는 세 원소
                for (int j = 0; j < 3; j++) {
                    double a = tt * 0.5 + j * Math.PI * 2 / 3;
                    Vec3 p = V.add(V.add(V.up(hilt, 0.6 + q * 1.8 * ((j + 1) / 3.0)), V.rightOf(f), Math.cos(a) * 0.5), f, Math.sin(a) * 0.5);
                    SkillFx.fx(level, "rpg:arrow_spark", p, Opt.of("dir", V.UP, "speed", 1.5, "size", 0.16, "grow", 0.8, "life", 0.25, "color", colorOf(ORDER[j])[0],
                            "alpha", 1.0));
                }
                if (tt % 6 == 0) SkillFx.play(level, el.equals("fire") ? "fire.ignite" : el.equals("ice") ? "random.glass" : "item.trident.thunder", hilt, 1.4, 0.4);
            });
        }
        RuneKit.later(charge, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 f = V.flat(sp.getViewVector(1F));
            Vec3 from = V.up(V.add(sp.position(), f, 0.6), 1.1);
            double speed = k.num("speed");
            double spread = k.num("spread");
            double width = k.num("width");
            Set<UUID> hit = new HashSet<>();
            List<Planned> plan = new ArrayList<>();
            // 가운데 불 · 왼쪽 얼음 · 오른쪽 번개
            double[] degs = {0, -spread, spread};
            for (int w = 0; w < ORDER.length; w++) {
                String el = ORDER[w];
                Vec3 d = RuneKit.turn(f, degs[w]);
                double len = Math.max(1, RuneKit.rayLength(level, from, d, k.num("range")));
                double[][] cs = colorOf(el);
                double[] glow = cs[0];
                double[] core = cs[1];
                double s0 = Math.min(1.4, len * 0.5);
                RuneKit.rfx2(level, "wave", V.add(from, d, s0), Opt.of("dir", d, "speed", speed, "size", width * 1.2, "life", Math.max(0.05, (len - s0) / speed),
                        "fade", 0.15), glow, core);
                // 지나간 자리: 불 = 불꽃 · 얼음 = 얼음 가시 · 번개 = 땅을 따라 튀는 번개
                for (double s = 2.5; s < len; s += 2.6) {
                    Vec3 at = V.add(V.add(sp.position(), f, 0.6), d, s);
                    RuneKit.later(Math.round(s / speed * 20), () -> {
                        if (el.equals("fire")) {
                            SkillFx.fx(level, "rpg:fire_wall", at, Opt.of("size", 1.5, "life", 0.7, "color", glow, "alpha", 0.9, "spin", Kit.rand() * 360));
                        } else if (el.equals("ice")) {
                            SkillFx.fx(level, "rpg:ice_spike", at, Opt.of("size", 1.2, "life", 0.8, "spin", Kit.rand() * 30 - 15, "color", glow, "alpha", 1.0));
                        } else {
                            RuneKit.zap(level, V.up(at, 0.2), V.up(V.add(at, d, 2.4), 0.2), glow, core, 0.12, 0.25);
                        }
                    });
                }
                RuneKit.flyStreak(level, from, V.add(from, d, len), speed, glow, core, 1.2, 0.45);
                // 검기가 닿는 끝의 빛기둥
                Vec3 endAt = V.add(from, d, len);
                RuneKit.later(Math.round(len / speed * 20), () -> RuneKit.godRays(sp, level, new Vec3(endAt.x, RuneKit.groundAt(level, endAt, from.y - 1.1), endAt.z),
                        0, 1, glow, core, Opt.of("h", 13.0, "life", 0.6, "skipNear", true)));
                for (Kit.LineHit h : RuneKit.lineTargets(sp, from, d, len, width)) plan.add(new Planned(h.e(), el, d, Math.max(0, Math.round(h.t() / speed * 20))));
            }
            SkillFx.arc(level, V.up(V.add(sp.getEyePosition(), f, 1.6), -0.3), f, Opt.of("shape", "heavy", "flip", false, "size", 3.6, "life", 0.3, "speed", 4.0,
                    "spin", -4.0, "color", RuneKit.WHITE, "aura", colorOf("fire")[0]));
            SkillFx.play(level, "item.trident.thunder", from, 1.3, 0.8);
            SkillFx.play(level, "firework.large_blast", from, 1.0, 0.8);
            RuneKit.shakeAround(level, sp.position(), 10, 0.25, 0.3);
            // 발동: 흰 번쩍임 · 발밑에서 퍼지는 고리 (1틱 뒤 — 검기가 나가는 틱의 파티클 예산)
            RuneKit.ultFlashFor(sp, level, from, "elementRelease", "flash", Opt.empty());
            Vec3 feet0 = sp.position();
            Vec3 skyAt = RuneKit.skyPoint(sp, feet0, 12, 21, 6, 22);
            RuneKit.later(1, () -> {
                RuneKit.novaRing(level, feet0, 1.6, 3.6, RuneKit.WHITE, colorOf("fire")[1], Opt.of("life", 0.45));
                RuneKit.skyFlare(level, skyAt, 3.8, colorOf("thunder")[0], RuneKit.WHITE, Opt.of("life", 0.5));
            });
            plan.sort(Comparator.comparingLong(Planned::at));
            // 번개는 검기가 닿지 않는 적에게만 튐 (검기 피해를 가로채지 않게)
            Set<UUID> planned = new HashSet<>();
            for (Planned p : plan) planned.add(p.e().getUUID());
            for (Planned p : plan) {
                RuneKit.later(p.at(), () -> {
                    if (!RuneKit.sameLife(sp, life) || p.e().isRemoved() || hit.contains(p.e().getUUID())) return;
                    hit.add(p.e().getUUID());
                    if (RuneKit.spellHit(sp, p.e(), P, k.num("mult")) <= 0) return;
                    elementEffect(p.e(), p.el(), k);
                    elementHitFx(level, p.e(), p.el(), 0);
                    RuneKit.knock(p.e(), p.d(), 0.5, 0.25);
                    if (p.el().equals("thunder")) thunderChain(sp, p.e(), P, k.num("chainMult"), k.num("chain"), hit, planned, 0);
                });
            }
        });
        return true;
    }
}
