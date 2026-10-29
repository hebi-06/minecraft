package mmorpg.fabric.skill;

import java.util.List;
import java.util.function.BooleanSupplier;
import mmorpg.core.Obj;
import mmorpg.core.Weapon;
import mmorpg.fabric.Fx3d;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 다그다의 곤봉 (영웅 전투 망치 — 12단계, 자바 전용): 생과 사의 일격. 수치는 HERO_SKILLS.hammer, 색은 생명의 초록 · 흰 심지 + 죽음의 보라 (dark).
 * windup 틱 동안 곤봉을 들어 올림 (초록 · 보라 기운이 감김) → 앞 ahead 칸을 내려침: 둘레 radius 칸 충격파 (mult, 띄움 lift) → delay 틱 뒤 같은 자리 여진
 * (afterMult) → 준 피해의 heal 만큼 회복 (한 번에 최대 체력의 healCap 까지 — 초록 숫자). 대상이 없어도 씀
 */
final class Dagda {
    private Dagda() {}

    static void register() {
        Transcend.register("dagda_smash", Dagda::smash);
    }

    static boolean smash(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = HeroKit.colors(k);
        double[] glow = cs[0];
        double[] core = cs[1];
        double[] dark = HeroKit.rgbOr(k, "dark", new double[] {0.8, 0.52, 0.24});
        RuneKit.bodyAura(sp, glow, Opt.of("life", 0.45, "alpha", 0.75));
        RuneKit.bodyAura(sp, dark, Opt.of("life", 0.45, "alpha", 0.6, "force", true));
        Vec3 f0 = Kit.facingOf(sp);
        Vec3 over = V.add(V.up(sp.getEyePosition(), 0.7), f0, 0.4);
        SkillFx.sparks(level, over, glow, 6, 2);
        SkillFx.sparks(level, over, dark, 6, 2);
        SkillFx.play(level, "mob.ravager.roar", sp.position(), 1.5, 0.4);
        SkillFx.play(level, "mace.smash_air", sp.position(), 0.6, 0.8);
        HeroKit.Heal heal = new HeroKit.Heal(sp, k.num("heal"), k.num("healCap"));
        // 0.48.0 입체: 내 곤봉 디자인이 커져 머리 뒤 높이에서 천천히 → 빠르게 앞 ahead 칸의 땅으로 내리찍힘 (windup 틱째 닿음 — 쓴 사람의 1인칭에서는 숨김:
        // 눈 바로 위를 지나는 자루가 화면을 가르지 않게, 1인칭은 내 손의 곤봉 동작이 보임)
        int windup = (int) k.num("windup");
        Vec3 pivot = V.up(V.add(sp.position(), f0, -0.4), 2.4);
        Vec3 hitAt = V.add(sp.position(), f0, k.num("ahead"));
        WeaponFx3d.Bounds bd = WeaponFx3d.bounds(w);
        double size = V.len(V.sub(hitAt, pivot)) / Math.max(0.5, bd.length() * bd.hand());
        WeaponFx3d.swingItem(level, sp, w, pivot, V.unit(V.add(V.UP, f0, -0.45)), V.unit(V.sub(hitAt, pivot)), windup / 20.0, 0.3, size, true, Opt.of("view", 1.0,
                "glow", glow, "glowAlpha", 0.6, "shine", 0.3, "fadeOut", 0.2));
        HeroKit.at(windup, () -> {
            if (!ok.getAsBoolean()) return;
            Vec3 f = Kit.facingOf(sp);
            Vec3 c = V.add(sp.position(), f, k.num("ahead"));
            double r = k.num("radius");
            impact(sp, c, r, glow, core, dark, true);
            long now = Tasks.now();
            List<LivingEntity> hit = Kit.around(sp, c, r, 2.5);
            for (LivingEntity e : hit) {
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now), ok, () -> {
                    double dealt = Kit.skillHit(sp, e, w, k.num("mult"));
                    if (dealt <= 0) return;
                    heal.add(dealt);
                    Vec3 d = V.flat(V.sub(e.position(), c));
                    Kit.knockback(e, d.x * 0.25, d.z * 0.25, k.num("lift"));
                    SkillFx.sparks(level, HeroKit.body(e, c), glow, 4, 3);
                });
            }
            HeroKit.at((int) k.num("delay"), () -> {
                if (!ok.getAsBoolean()) return;
                impact(sp, c, r, glow, core, dark, false);
                long t1 = Tasks.now();
                for (LivingEntity e : Kit.around(sp, c, r, 2.5)) {
                    Kit.hitAt(sp, e, Kit.reserveHit(sp, e, t1), ok, () -> {
                        double dealt = Kit.skillHit(sp, e, w, k.num("afterMult"));
                        if (dealt > 0) heal.add(dealt);
                    });
                }
                // 생명의 기운이 돌아옴: 맞은 자리에서 나에게 초록 빛줄기 → 회복
                HeroKit.at(8, () -> {
                    if (!ok.getAsBoolean()) return;
                    double healed = heal.open();
                    if (healed > 0) {
                        Vec3 chest = V.up(sp.position(), 1.0);
                        RuneKit.streak(level, V.up(c, 0.8), chest, glow, 0.14, 0.3, 0.9, 0);
                        RuneKit.selfCue(sp, glow, core, true);
                        SkillFx.play(level, "beacon.power", chest, 1.6, 0.5);
                    }
                });
            });
        });
        return true;
    }

    /** 내려친 자리: 갈라진 땅 (생명 = 보석의 초록 · 여진 = 곤봉 나무의 호박빛 — 0.12.8 무기 디자인 색, 예전 보라) · 충격파 고리 둘 · 흙먼지 · 튀는 돌 · 크게 흔들림 (first = 첫 일격) */
    private static void impact(ServerPlayer sp, Vec3 c, double r, double[] glow, double[] core, double[] dark, boolean first) {
        ServerLevel level = sp.level();
        if (first) {
            RuneKit.glowCrack(level, c, r * 0.9, glow, core, Opt.of("life", 1.4));
            RuneKit.glowCrack(level, c, r * 0.7, dark, core, Opt.of("life", 1.2, "v", 1.0));
            RuneKit.rockBurst(level, V.up(c, 0.3), 6, 5, 0.3);
            SkillFx.play(level, "mace.heavy_smash_ground", c, 0.7, 1.0);
            SkillFx.play(level, "random.anvil_land", c, 0.5, 0.6);
            SkillFx.shake(sp, 0.35, 0.3);
        } else {
            SkillFx.play(level, "mace.smash_ground", c, 0.6, 0.9);
            SkillFx.shake(sp, 0.2, 0.2);
        }
        SkillFx.shock(level, c, first ? glow : dark, r * (first ? 0.9 : 1.1), 0.45);
        if (first) {
            // 0.48.0 입체: 내려친 자리 둘레로 바위 가시 여덟이 솟았다가 꺼짐 (틈에 생명의 초록빛)
            for (int j = 0; j < 8; j++) {
                Vec3 at = V.add(c, V.turnY(new Vec3(1, 0, 0), j * 45 + Kit.rand() * 20), r * (0.45 + Kit.rand() * 0.15));
                int id = WeaponFx3d.fx(level, "rock_spike", at, dark, glow, Opt.of("dir", V.UP, "face", V.turnY(new Vec3(0, 0, 1), Kit.rand() * 360), "scale", 0.7
                        + Kit.rand() * 0.25, "anim", "rise", "life", 1.0, "glowAlpha", 0.45, "shine", 0.1, "fadeOut", 0.2));
                Tasks.later(12, () -> Fx3d.anim(level, id, "sink", false, at));
            }
        } else {
            // 0.48.0 입체 여진: 가운데에서 생명의 빛기둥 + 활짝 피는 꽃 (생과 사 — 거둔 생명이 돌아옴)
            WeaponFx3d.pillar(level, c, r * 0.22, 3.2, 0.55, glow, core, Opt.of("grow", 0.4));
            WeaponFx3d.fx(level, "flower", V.up(c, 0.05), glow, core, Opt.of("dir", V.UP, "face", new Vec3(0, 0, 1), "scale", r * 0.55, "anim", "bloom", "life", 1.1,
                    "glowAlpha", 0.55, "shine", 0.35, "fadeOut", 0.4));
        }
        RuneKit.novaRing(level, c, r * 0.3, first ? 2.4 : 3.0, first ? glow : dark, core, Opt.of("life", 0.45));
        SkillFx.fx(level, "rpg:dust", V.up(c, 0.5), Opt.of("size", r * 0.8, "life", 0.8, "color", new double[] {0.55, 0.45, 0.32}, "alpha", 0.75));
        RuneKit.zonePulse(level, c, first ? glow : dark, r, 0.4);
    }
}
