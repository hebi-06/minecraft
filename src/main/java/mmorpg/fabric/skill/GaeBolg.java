package mmorpg.fabric.skill;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
 * 게이 볼그 (영웅 창 — 12단계, 자바 전용): 심장 꿰뚫기. 수치는 HERO_SKILLS.lance, 색은 핏빛 · 연분홍 심지.
 * windup 틱 동안 창에 붉은 기운이 모임 → 보는 쪽 (위아래는 15° 넘게 볼 때만, pitch 도까지) 으로 창을 던짐: 틱당 speed 칸씩 range 칸 (벽에서 멈춤)
 * 날며 폭 width 칸의 적을 모두 꿰뚫음 (mult) → 처음 꿰뚫은 적 (없으면 창이 멈춘 자리) 의 몸에서 delay 틱 뒤 가시가 사방으로 터져 나옴: 둘레 radius 칸의 적
 * (afterMult, 구속 I slow 틱). 그림: rpg:rfx_relic 칸 1 (게이 볼그 모델 옆모습). 대상이 없어도 씀
 */
final class GaeBolg {
    private GaeBolg() {}

    static final int RELIC_CELL = 1;

    static void register() {
        Transcend.register("gaebolg_pierce", GaeBolg::pierce);
    }

    static boolean pierce(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = HeroKit.colors(k);
        double[] glow = cs[0];
        double[] core = cs[1];
        Vec3 f0 = Kit.facingOf(sp);
        Vec3 hand = V.add(V.add(V.up(sp.getEyePosition(), -0.35), f0, 0.7), V.rightOf(f0), 0.4);
        RuneKit.rfx2(level, "flare", hand, Opt.of("size", 0.5, "life", 0.25, "grow", 0.8, "fade", 0.6), glow, core, new int[] {0, 1}, null);
        SkillFx.sparks(level, hand, glow, 6, 2);
        RuneKit.bodyAura(sp, glow, Opt.of("life", 0.4, "alpha", 0.7));
        SkillFx.play(level, "item.trident.riptide_1", hand, 0.9, 0.7);
        SkillFx.play(level, "mob.warden.heartbeat", hand, 1.2, 0.9);
        HeroKit.at((int) k.num("windup"), () -> {
            if (ok.getAsBoolean()) fly(sp, w, k, ok, glow, core);
        });
        return true;
    }

    private static void fly(ServerPlayer sp, Weapon w, Obj k, BooleanSupplier ok, double[] glow, double[] core) {
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        Vec3 f = Kit.facingOf(sp);
        Vec3 view = sp.getViewVector(1F);
        double look = Math.asin(Math.max(-1, Math.min(1, view.y)));
        double tilt = Math.signum(look) * Math.max(0, Math.abs(look) - Math.toRadians(15));
        double maxPitch = Math.toRadians(k.num("pitch"));
        double pitch = Math.max(-maxPitch, Math.min(maxPitch, tilt));
        Vec3 dir = V.unit(new Vec3(f.x * Math.cos(pitch), Math.sin(pitch), f.z * Math.cos(pitch)));
        Vec3 from = V.add(V.up(sp.getEyePosition(), -0.3), dir, 0.6);
        double len = Math.max(0.5, Kit.rayDist(level, from, dir, k.num("range")) - 0.2);
        double speed = k.num("speed");
        int steps = Math.max(1, (int) Math.ceil(len / speed));
        // 던짐: 붉은 창 (모델 그림) 이 끝까지 같은 빠르기로 + 핏빛 꼬리
        double start = Math.min(1.8, len);
        boolean wall = len < k.num("range") - 0.6;
        int delay = (int) k.num("delay");
        if (len > 1) {
            double flyS = Math.max(0.05, (len - start) / (speed * 20));
            HeroKit.relicSpear(level, V.add(from, dir, start), dir, 2.6, flyS + 0.05, speed * 20, RELIC_CELL, 0.35);
            // 0.48.0 입체: 내 창 디자인이 붉게 빛나며 돌아 날아감 (창끝이 앞) — 벽에 막히면 꽂힌 채 가시가 터질 때까지
            WeaponFx3d.flyItem(level, sp, w, V.add(from, dir, start), V.add(from, dir, len + 0.3), flyS, wall ? (delay + 4) / 20.0 : 0.03, 1.15, Opt.of("roll", 720.0,
                    "glow", glow, "glowAlpha", 0.6, "shine", 0.25, "fadeOut", wall ? 0.15 : 0.03));
        }
        SkillFx.play(level, "item.trident.throw", from, 0.7, 1.0);
        SkillFx.play(level, "entity.player.attack.sweep", from, 0.6, 0.6);
        SkillFx.shake(sp, 0.1, 0.1);
        Set<UUID> struck = new HashSet<>();
        LivingEntity[] heart = {null};
        Kit.ticks(t -> {
            if (!ok.getAsBoolean()) return false;
            double s0 = t == 0 ? 0 : Math.min(len, speed * t);
            double s1 = Math.min(len, speed * (t + 1));
            Vec3 at = V.add(from, dir, s1);
            if (s1 > start - 0.3) {
                Vec3 tail = V.add(from, dir, Math.max(start - 0.5, s1 - 2.4));
                Gram.streakLine(level, tail, at, glow, 0.3, 0.22, 0.85, 0);
                Gram.streakLine(level, tail, at, core, 0.08, 0.16, 1.0, 0);
                if (t % 2 == 0) SkillFx.sparks(level, at, glow, 2, 2);
            }
            if (s1 - s0 > 0.05) {
                for (Kit.LineHit h : Kit.lineHits(sp, V.add(from, dir, s0), dir, s1 - s0, k.num("width"))) {
                    LivingEntity e = h.e();
                    if (!struck.add(e.getUUID())) continue;
                    if (heart[0] == null) heart[0] = e;
                    Kit.hitAt(sp, e, Kit.reserveHit(sp, e, Tasks.now()), ok, () -> {
                        if (Kit.skillHit(sp, e, w, k.num("mult")) <= 0) return;
                        Vec3 c = HeroKit.body(e, at);
                        Vec3 v = V.unit(V.sub(c, sp.getEyePosition()));
                        double near = HeroKit.near(sp.getEyePosition(), c, 0.5);
                        SkillFx.fx(level, "rpg:slash_x", V.add(c, v, -0.35), Opt.of("dir", dir, "size", 0.9 * near, "life", 0.22, "color", glow, "alpha", 1.0));
                        SkillFx.sparks(level, c, core, 5, 4);
                        SkillFx.play(level, "item.trident.hit", c, 0.8, 0.8);
                    });
                }
            }
            if (t + 1 < steps) return true;
            Vec3 burstAt = heart[0] != null && !heart[0].isRemoved() ? Kit.bodyPoint(heart[0]) : at;
            if (heart[0] == null) SkillFx.play(level, "item.trident.hit_ground", at, 0.7, 0.8);
            LivingEntity h0 = heart[0];
            Kit.motionLater(sp, "gaebolg_pierce:thorns", Math.max(0, delay - 1), life); // 0.48.0 주먹을 움켜쥐어 당김 (1틱째 = 가시)
            Tasks.later(delay, () -> {
                if (ok.getAsBoolean()) thorns(sp, w, k, ok, h0 != null && !h0.isRemoved() ? Kit.bodyPoint(h0) : burstAt, glow, core);
            });
            return false;
        });
    }

    /** 가시가 터짐: 몸 가운데에서 사방으로 핏빛 가시 (빛줄기) 와 붉은 결정 조각 · 고리 → 둘레 radius 칸의 적 (afterMult, 구속 I) */
    private static void thorns(ServerPlayer sp, Weapon w, Obj k, BooleanSupplier ok, Vec3 c, double[] glow, double[] core) {
        ServerLevel level = sp.level();
        double r = k.num("radius");
        double near = HeroKit.near(sp.getEyePosition(), c, 0.5);
        for (int i = 0; i < 14; i++) {
            double a = i / 14.0 * Math.PI * 2 + Kit.rand() * 0.3;
            double up = (Kit.rand() * 2 - 1) * 0.7;
            Vec3 d = V.unit(new Vec3(Math.cos(a), up, Math.sin(a)));
            double l = r * (0.6 + Kit.rand() * 0.5) * near;
            RuneKit.streak(level, V.add(c, d, 0.2), V.add(c, d, l), glow, 0.16, 0.3, 0.95, 0);
            RuneKit.streak(level, V.add(c, d, 0.2), V.add(c, d, l * 0.95), core, 0.05, 0.24, 1.0, 0);
        }
        for (int i = 0; i < 6; i++) {
            double a = i / 6.0 * Math.PI * 2;
            Vec3 at = V.add(c, new Vec3(Math.cos(a) * 0.9 * near, -0.4 + Kit.rand() * 0.8, Math.sin(a) * 0.9 * near));
            RuneKit.iceCrystal(sp, level, at, 0.55 * near, glow, Opt.of("life", 0.6, "alpha", 0.95, "spin", Kit.rand() * 360));
        }
        RuneKit.rfx2(level, "flare", c, Opt.of("size", 1.6 * near, "life", 0.35, "grow", 0.4, "fade", 0.6, "spin", Kit.rand() * 90), glow, core, new int[] {0, 1}, null);
        // 0.48.0 입체: 몸 가운데에서 핏빛 가시덤불이 위아래로 터져 나왔다가 꺼짐
        for (int j = 0; j < 2; j++) {
            Vec3 d = j == 0 ? V.UP : V.DOWN;
            int id = WeaponFx3d.fx(level, "thorns", c, glow, core, Opt.of("dir", d, "face", V.turnY(new Vec3(0, 0, 1), Kit.rand() * 360), "scale", (j == 0 ? 1.5
                    : 1.1) * near, "anim", "burst", "life", 0.85, "glowAlpha", 0.6, "shine", 0.3, "fadeOut", 0.2));
            Tasks.later(9, () -> Fx3d.anim(level, id, "sink", false, c));
        }
        SkillFx.burst(level, c, glow, 1.3 * near, 0.35);
        RuneKit.zonePulse(level, V.up(c, -0.9), glow, r, 0.35);
        SkillFx.play(level, "break.amethyst_cluster", c, 0.6, 1.0);
        SkillFx.play(level, "mob.phantom.bite", c, 0.7, 0.8);
        long now = Tasks.now();
        List<LivingEntity> list = Kit.around(sp, c, r, 2.5);
        for (LivingEntity e : list) {
            Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now), ok, () -> {
                if (Kit.skillHit(sp, e, w, k.num("afterMult")) <= 0) return;
                RuneKit.effect(e, "slowness", (int) k.num("slow"), 0);
                Vec3 b = HeroKit.body(e, c);
                SkillFx.sparks(level, b, glow, 4, 4);
                // 0.48.0 입체: 둘레 적의 발밑에서도 가시가 솟아 묶음
                int id = WeaponFx3d.fx(level, "thorns", e.position(), glow, core, Opt.of("dir", V.UP, "face", V.turnY(new Vec3(0, 0, 1), Kit.rand() * 360), "scale",
                        0.75 + Kit.bodyPad(e), "anim", "burst", "life", 1.0, "glowAlpha", 0.5, "shine", 0.2, "fadeOut", 0.2));
                Vec3 at = e.position();
                Tasks.later(12, () -> Fx3d.anim(level, id, "sink", false, at));
            });
        }
    }
}
