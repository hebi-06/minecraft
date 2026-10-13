package mmorpg.fabric.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import mmorpg.core.Obj;
import mmorpg.core.Weapon;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 리치의 지팡이 (영웅 완드) — 영혼 수확 (10단계, 자바 전용). 수치는 HERO_SKILLS.wand, 색은 디자인의 영혼빛 초록 보석 · 뼈 빛 자루.
 * 조준한 곳 (몹의 발밑 · 시선이 닿은 곳 — range 칸, 그 아래 땅) 에 망자의 원 (반지름 radius 칸) 이 열리고 둘레에 영혼불이 솟음 →
 * bind 틱에 원 안의 적 (가운데에서 보이는) 을 원 둘레의 땅에서 뻗은 영혼 사슬 셋이 묶음 (mult, slow 틱 구속 slowAmp + 1) →
 * delay 틱 뒤 묶인 적의 영혼을 뜯어냄 (afterMult — 밀려났어도 radius + 2.5칸 안) → 영혼이 나에게 날아와 back 틱 뒤 닿으면 회복
 * (이 스킬로 준 피해 합의 heal, 최대 체력의 healCap 까지 — 초록 + 숫자). 땅을 조준하지 않으면 (허공) 재사용 대기 없이 거절.
 * 쓰러지거나 차원을 옮기면 남은 것은 멈춤
 */
final class LichStaff {
    private LichStaff() {}

    /** 망자의 원 바탕 (짙은 녹흑) */
    static final double[] GRAVE = {0.06, 0.22, 0.14};
    /** 망자의 원 문양 (rpg:rfx_sigil — 비전) */
    static final int ARCANE = RuneKit.SIGIL.get("arcane");

    static void register() {
        Transcend.register("lich_harvest", LichStaff::harvest);
    }

    /** 지팡이 끝 (눈 앞 0.9칸 · 오른쪽 0.35칸 · 위 0.05칸) */
    static Vec3 tipAt(ServerPlayer sp) {
        Vec3 f = Kit.facingOf(sp);
        return V.up(V.add(V.add(sp.getEyePosition(), f, 0.9), V.rightOf(f), 0.35), 0.05);
    }

    /** 원을 열 땅: 조준한 몹의 발밑 → 시선이 닿은 곳 (가로로 0.4칸 앞 — 벽 면에 붙지 않게) 의 아래 땅 (24칸 안). 없으면 null */
    static Vec3 groundSpot(ServerPlayer sp, double range) {
        Vec3 eye = sp.getEyePosition();
        RpgCombat.Aim r = RpgCombat.aimTarget(sp, range, Kit.BOW.numOr("aimAssist", 0));
        Vec3 q;
        if (r.target() != null && Kit.canHit(sp, r.target())) q = r.target().position();
        else {
            Vec3 d = r.dir();
            q = V.add(eye, d, r.dist());
            if (Math.hypot(d.x, d.z) > 0.2) q = V.add(q, V.flat(d), -0.4);
        }
        Kit.Hit hit = Kit.ray(sp.level(), new Vec3(q.x, q.y + 1.5, q.z), V.DOWN, 24, true);
        if (hit == null) return null;
        double fy = hit.point().y - hit.pos().getY();
        return new Vec3(q.x, hit.pos().getY() + (fy > 0.05 && fy <= 1 ? fy : 1), q.z);
    }

    static boolean harvest(ServerPlayer sp, Weapon w, Obj k) {
        Vec3 c = groundSpot(sp, k.num("range"));
        if (c == null) {
            Notify.warn(sp, "§c" + k.str("name") + ": §7땅을 조준해야 합니다 (" + k.text("range") + "칸 안)");
            return false;
        }
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = HeroKit.colors(k);
        double[] glow = cs[0];
        double[] core = cs[1];
        double r = k.num("radius");
        int bind = (int) k.num("bind");
        int delay = (int) k.num("delay");
        int back = (int) k.num("back");
        double openS = (bind + delay + 8) / 20.0;
        // 시전: 지팡이 끝 번쩍임 → 원 가운데로 가는 영혼빛
        Vec3 tip = tipAt(sp);
        // 지팡이 끝 번쩍임 (눈 앞 1칸 남짓 — 작게: 크면 1인칭 화면에 흐린 네모로 보임)
        SkillFx.fx(level, "rpg:arrow_spark", tip, Opt.of("dir", V.UP, "size", 0.1, "grow", 1.8, "life", 0.2, "color", glow, "alpha", 1.0));
        SkillFx.sparks(level, tip, core, 4, 1.5);
        // 영혼빛은 지팡이 끝에서 1.5칸 앞부터 (눈 가까이의 굵은 조각이 1인칭 화면을 가리지 않게)
        Vec3 toC = V.sub(V.up(c, 0.3), tip);
        if (V.len(toC) > 2.0) {
            SkillFx.beam(level, V.add(tip, V.unit(toC), 1.5), V.up(c, 0.3), glow, Opt.of("half", 0.45, "thick", 0.06, "life", 0.25, "sweep", 0.12, "gap", 0.8,
                    "alpha", 0.8));
        }
        SkillFx.play(level, "mob.evocation_illager.prepare_summon", c, 0.7, 0.8);
        SkillFx.play(level, "particle.soul_escape", tip, 0.8, 1.0);
        // 망자의 원: 짙은 바탕 + 빛나는 문양 (돎) + 퍼지는 고리 + 둘레 영혼불
        RuneKit.rfx(level, "sigil", V.up(c, 0.05), Opt.of("dir", V.UP, "size", r * 1.08, "life", openS, "spin", Kit.rand() * 360, "spinRate", -20.0, "fade", 0.2,
                "color", GRAVE, "alpha", 0.75, "cell", (double) ARCANE));
        RuneKit.rfx2(level, "sigil", V.up(c, 0.08), Opt.of("dir", V.UP, "size", r, "life", openS, "spin", Kit.rand() * 360, "spinRate", 30.0, "grow", 0.04, "fade",
                0.2, "alpha", 0.9, "cell", (double) ARCANE), glow, core, new int[] {0, 1}, null);
        SkillFx.shock(level, c, glow, r / 2.1, 0.5);
        for (int j = 0; j < 8; j++) {
            Vec3 at = V.add(c, V.turnY(new Vec3(1, 0, 0), j * 45 + 22.5), r * 0.92);
            soulFire(level, at, 0.55, openS, glow, j);
        }
        soulFire(level, c, 0.8, 0.6, glow, 3);
        // 묶음 (bind 틱): 원 안의 적에게 영혼 사슬 · 느리게 (mult)
        Map<UUID, LivingEntity> bound = new LinkedHashMap<>();
        HeroKit.Heal heal = new HeroKit.Heal(sp, k.num("heal"), k.num("healCap"));
        Tasks.later(bind, () -> {
            if (!ok.getAsBoolean()) return;
            List<LivingEntity> in = Kit.visibleFrom(sp, V.up(c, 1.2), Kit.around(sp, c, r));
            if (!in.isEmpty()) {
                SkillFx.play(level, "use.chain", c, 0.7, 1.0);
                SkillFx.play(level, "mob.wither.shoot", c, 1.6, 0.3);
            }
            long now = Tasks.now();
            int slow = (int) k.num("slow");
            int amp = (int) k.num("slowAmp");
            for (LivingEntity e : in) {
                chainFx(level, c, r, e, glow, core, delay);
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now), ok, () -> {
                    double dealt = Kit.skillHit(sp, e, w, k.num("mult"));
                    if (dealt <= 0) return;
                    heal.add(dealt);
                    bound.put(e.getUUID(), e);
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, slow, amp, false, false));
                    Vec3 b = HeroKit.body(e, c);
                    SkillFx.burst(level, b, glow, 0.7, 0.25);
                    soulFire(level, e.position(), 0.5, 0.5, glow, 1);
                });
            }
        });
        // 수확 (bind + delay 틱): 묶인 적의 영혼을 뜯어냄 (afterMult) → 영혼이 나에게 날아옴 → 닿으면 회복
        Tasks.later(bind + delay, () -> {
            if (!ok.getAsBoolean()) return;
            long t0 = Tasks.now();
            List<LivingEntity> list = new ArrayList<>();
            for (LivingEntity e : bound.values()) {
                if (!e.isRemoved() && V.hdist(e.position(), c) <= r + 2.5 + Kit.bodyPad(e) && Math.abs(e.getY() - c.y) <= 5) list.add(e);
            }
            SkillFx.shock(level, c, core, r / 2.1 * 0.8, 0.4);
            RuneKit.rfx(level, "nova", V.up(c, 0.1), Opt.of("dir", V.UP, "size", r * 0.6, "life", 0.35, "grow", 0.9, "fade", 0.6, "color", glow, "alpha", 0.9,
                    "cell", 0.0));
            if (!list.isEmpty()) {
                SkillFx.play(level, "particle.soul_escape", c, 1.0, 1.0);
                SkillFx.play(level, "mob.vex.charge", c, 0.7, 0.6);
            }
            for (LivingEntity e : list) {
                Vec3 from = HeroKit.body(e, c);
                ripFx(sp, e, from, glow, core);
                soulFly(sp, from, glow, core, back);
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, t0), ok, () -> heal.add(Kit.skillHit(sp, e, w, k.num("afterMult"))));
            }
            // 영혼이 닿으면 회복 (묶을 때 쓰러진 적의 피해도 — 날아온 영혼이 없으면 그림 없이)
            boolean souls = !list.isEmpty();
            Tasks.later(back, () -> {
                if (!ok.getAsBoolean()) return;
                double got = heal.open();
                if (!souls && got <= 0) return;
                Vec3 at = V.up(sp.position(), 1.0);
                SkillFx.burst(level, at, glow, 1.0, 0.3);
                SkillFx.shock(level, sp.position(), glow, 0.8, 0.4);
                SkillFx.play(level, "respawn_anchor.charge", at, 1.4, got > 0 ? 0.7 : 0.4);
            });
        });
        return true;
    }

    /** 영혼불 (rpg:rfx_aura — 흰 불꽃 4장을 영혼빛으로, 발 고정): size 반 크기, life 초, phase = 장 어긋남 */
    static void soulFire(ServerLevel level, Vec3 at, double size, double life, double[] glow, int phase) {
        RuneKit.rfx(level, "aura", at, Opt.of("size", size, "life", life, "frames", 4.0, "fps", 12.0, "loop", true, "phase", (double) phase, "fade", 0.4, "color",
                glow, "alpha", 0.85, "cell", 0.0));
    }

    /**
     * 영혼 사슬 셋 (rpg:rfx_chain 빛 + 심지 — 수확 때까지): 원 둘레 (반지름 r) 의 세 곳 — 적 쪽 · 그 양옆 70° — 의 땅에서 적의 몸으로
     * (원 가운데의 적은 둘레 세 곳 120° 마다에서)
     */
    static void chainFx(ServerLevel level, Vec3 c, double r, LivingEntity e, double[] glow, double[] core, int hold) {
        Vec3 b = Kit.bodyPoint(e);
        double dx = e.getX() - c.x;
        double dz = e.getZ() - c.z;
        boolean centered = Math.hypot(dx, dz) < 0.6;
        Vec3 toward = centered ? new Vec3(1, 0, 0) : V.flat(new Vec3(dx, 0, dz));
        double[] angles = centered ? new double[] {0, 120, 240} : new double[] {0, 70, -70};
        for (double a0 : angles) {
            Vec3 a = V.up(V.add(c, V.turnY(toward, a0), r * 0.95), 0.15);
            Vec3 d = V.sub(b, a);
            double len = V.len(d);
            if (len < 0.4) continue;
            RuneKit.rfx2(level, "chain", V.add(a, d, 0.5), Opt.of("dir", V.unit(d), "size", len / 2, "life", (hold + 2) / 20.0, "fade", 0.25, "alpha", 0.9), glow,
                    core, new int[] {0, 1}, null);
            SkillFx.burst(level, a, glow, 0.35, 0.25);
        }
    }

    /** 뜯겨 나오는 영혼: 몸에서 빠져나오는 영혼빛 그림자 (위로 떠오름) + 폭발 */
    static void ripFx(ServerPlayer sp, LivingEntity e, Vec3 from, double[] glow, double[] core) {
        ServerLevel level = sp.level();
        Vec3 feet = e.isRemoved() ? new Vec3(from.x, from.y - 0.9, from.z) : e.position();
        // 몸 겉면 (눈 쪽으로 0.45칸) 에서 떠오름 — 맞아서 붉어진 몸에 가려지지 않게
        Vec3 v = V.flat(V.sub(feet, sp.getEyePosition()));
        RuneKit.shadeClone(sp, level, V.add(feet, v, -0.45), Kit.facingOf(e), 0, glow, core, Opt.of("size", 1.0, "life", 0.55, "fade", 0.7, "alpha", 0.85, "grow",
                0.15, "vel", new Vec3(0, 1.8, 0)));
        SkillFx.burst(level, from, glow, 0.9, 0.3);
        SkillFx.sparks(level, from, core, 5, 3);
    }

    /** 영혼이 나에게 날아옴 (back 틱 — 휘어 날며, 끝은 내 가슴): 영혼빛 구슬 + 꼬리 */
    static void soulFly(ServerPlayer sp, Vec3 from, double[] glow, double[] core, int back) {
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        Vec3 lift = V.up(from, 1.5);
        Vec3[] prev = {from};
        Kit.ticks(t -> {
            if (!Kit.sameLife(sp, life)) return false;
            double u = Math.min(1, (t + 1) / (double) back);
            Vec3 to = V.up(sp.position(), 1.1);
            Vec3 ctrl = V.lerp(lift, V.up(to, 1.2), 0.5);
            Vec3 p = Odin.bezAt(from, ctrl, to, u);
            SkillFx.fx(level, "rpg:arrow_spark", p, Opt.of("dir", V.UP, "size", 0.26, "grow", 0.6, "life", 0.12, "color", glow, "alpha", 1.0));
            Gram.streakLine(level, prev[0], p, glow, 0.14, 0.3, 0.8, 0);
            Gram.streakLine(level, prev[0], p, core, 0.05, 0.22, 1.0, 0);
            prev[0] = p;
            return u < 1;
        });
    }
}
