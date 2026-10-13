package mmorpg.fabric.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import mmorpg.core.Obj;
import mmorpg.core.Weapon;
import mmorpg.fabric.Notify;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 그림자 단검 (영웅 단검) — 그림자 습격 (10단계, 자바 전용). 수치는 HERO_SKILLS.dagger, 색은 디자인의 보랏빛 칼날 · 검은 그림자.
 * 앞쪽 (cone 도 · range 칸) 에서 조준한 적 (없으면 시선에 가장 가까운 적) 의 등 뒤 (그 적이 보는 쪽의 반대 — 몸 반폭 + side 칸) 로 그림자가 되어 옮겨 가
 * 찌름 (mult) → gap 틱 뒤 그 적의 양옆에 그림자 분신 둘이 나타나 엇갈려 벰 (mult — 두 번째) → 다시 gap 틱 뒤 분신이 그 적에게 빨려 들어가 그림자가 터짐
 * (afterMult — 그 적과 둘레 radius 칸의 적, 그 적이 쓰러졌어도 둘레는). 설 자리가 없거나 속박 · 탄 동안 · 보스 필드 밖이면 옮기지 않고 그 자리에서 그림자 칼날
 * (피해 같음). 앞쪽에 적이 없으면 재사용 대기 없이 거절. 쓰러지거나 차원을 옮기면 남은 것은 멈춤
 */
final class ShadowDagger {
    private ShadowDagger() {}

    /** 그림자 (분신 몸 · 연기) */
    static final double[] DARK = {0.16, 0.08, 0.26};

    static void register() {
        Transcend.register("shadow_ambush", ShadowDagger::ambush);
    }

    static boolean ambush(ServerPlayer sp, Weapon w, Obj k) {
        LivingEntity target = Kit.pickTarget(sp, k.num("range"), k.num("cone") / 2);
        if (target == null) {
            Notify.warn(sp, "§c" + k.str("name") + ": §7앞쪽 " + k.text("range") + "칸 안에 적이 없습니다");
            return false;
        }
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = HeroKit.colors(k);
        double[] glow = cs[0];
        double[] core = cs[1];
        int gap = (int) k.num("gap");
        Vec3 start = sp.position();
        Vec3 from = V.up(start, 1.1);
        boolean free = !Status.isRooted(sp) && !Kit.ridingAny(sp) && Solas.bossFieldOk.ok(sp, target, start);
        Vec3 spot = free ? behind(level, target, k, start) : null;
        Vec3 look = Kit.bodyPoint(target);
        boolean moved = false;
        if (spot != null) {
            float[] rot = Kit.lookRotation(V.up(spot, 1.62), look);
            moved = Kit.moveTo(sp, spot, rot[0], rot[1]);
        }
        Kit.noFall(sp, 12);
        Vec3 eye = sp.getEyePosition();
        // 떠난 자리: 그림자가 흩어짐 (분신 그림이 사라짐 + 어둠 + 보랏빛)
        RuneKit.shadeClone(sp, level, start, Kit.facingOf(sp), 0, DARK, glow, Opt.of("life", 0.35, "fade", 0.9, "alpha", 0.75, "vel", new Vec3(0, 0.8, 0)));
        SkillFx.burst(level, from, DARK, 1.2, 0.35);
        SkillFx.burst(level, from, glow, 0.7, 0.25);
        if (moved) {
            // 지나간 길: 어두운 줄기 + 보랏빛 심지 (눈 1.2칸 안은 빼고)
            Solas.streakAway(level, eye, from, V.up(spot, 1.1), DARK, 0.55, 0.32, 0.8, 1.2);
            Solas.streakAway(level, eye, from, V.up(spot, 1.1), glow, 0.14, 0.26, 1.0, 1.2);
            SkillFx.play(level, "mob.endermen.portal", spot, 0.6, 0.7);
        } else {
            // 옮기지 못함: 손에서 그 적으로 그림자 칼날
            Vec3 hand = V.add(V.up(eye, -0.25), V.rightOf(Kit.facingOf(sp)), 0.3);
            Solas.streakAway(level, eye, hand, look, DARK, 0.45, 0.28, 0.85, 1.2);
            Solas.streakAway(level, eye, hand, look, glow, 0.12, 0.22, 1.0, 1.2);
        }
        SkillFx.play(level, "mob.endermen.portal", start, 0.5, 0.6);
        SkillFx.play(level, "item.enchant.lunge1", look, 0.8, 1.0);
        long now = Tasks.now();
        Vec3[] last = {look};
        // 1타: 등을 찌름
        Kit.hitAt(sp, target, Kit.reserveHit(sp, target, now), ok, () -> {
            if (Kit.skillHit(sp, target, w, k.num("mult")) <= 0) return;
            stabFx(sp, HeroKit.body(target, last[0]), glow, core);
        });
        // 2타: 양옆 분신이 엇갈려 벰
        Tasks.later(gap, () -> {
            if (!ok.getAsBoolean()) return;
            if (!Kit.fallen(target)) last[0] = Kit.bodyPoint(target);
            clonesFx(sp, target, last[0], glow, core);
        });
        Kit.hitAt(sp, target, Kit.reserveHit(sp, target, now + gap), ok, () -> Kit.skillHit(sp, target, w, k.num("mult")));
        // 그림자 폭발: 그 적 + 둘레
        Tasks.later(gap * 2, () -> {
            if (!ok.getAsBoolean()) return;
            if (!Kit.fallen(target)) last[0] = Kit.bodyPoint(target);
            Vec3 c = last[0];
            burstFx(sp, c, k.num("radius"), glow, core);
            long t0 = Tasks.now();
            List<LivingEntity> list = new ArrayList<>();
            if (Kit.canHit(sp, target)) list.add(target);
            Vec3 feet = new Vec3(c.x, c.y - 0.9, c.z);
            list = Kit.union(list, Kit.visibleFrom(sp, c, Kit.around(sp, feet, k.num("radius"))));
            for (LivingEntity e : list) {
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, t0), ok, () -> {
                    if (Kit.skillHit(sp, e, w, k.num("afterMult")) > 0 && e != target) SkillFx.burst(level, HeroKit.body(e, c), glow, 0.6, 0.22);
                });
            }
        });
        return true;
    }

    /**
     * 등 뒤 자리: 그 적이 보는 쪽의 반대로 몸 반폭 + side 칸 — 안 되면 ±40° · ±80° · ±120° · 앞쪽 순. 설 자리 (standSpot) 이고, 처음 자리와 높이 3칸 ·
     * 가로 range + 3칸 안, 거기서 그 적의 몸이 보여야 (벽 너머 아님). 없으면 null
     */
    static Vec3 behind(Level level, LivingEntity e, Obj k, Vec3 start) {
        Vec3 t = e.position();
        Vec3 back = V.scale(Kit.facingOf(e), -1);
        double reach = 0.4 + Kit.bodyPad(e) + k.num("side");
        Vec3 body = Kit.bodyPoint(e);
        for (double a : new double[] {0, 40, -40, 80, -80, 120, -120, 180}) {
            Vec3 q = V.add(t, V.turnY(back, a), reach);
            Vec3 p = Kit.standSpot(level, new Vec3(q.x, t.y, q.z), 2.0);
            if (p == null) continue;
            if (Math.abs(p.y - start.y) > 3 || V.hdist(p, start) > k.num("range") + 3) continue;
            if (!Kit.clearPath(level, V.up(p, 1.2), body)) continue;
            return p;
        }
        return null;
    }

    /** 찌름: 보랏빛 가는 초승달 · 빛의 자국 · 불꽃 (눈 가까이는 작게) */
    static void stabFx(ServerPlayer sp, Vec3 c, double[] glow, double[] core) {
        ServerLevel level = sp.level();
        Vec3 eye = sp.getEyePosition();
        double near = HeroKit.near(eye, c, 0.45);
        Vec3 v = V.unit(V.sub(c, eye));
        SkillFx.arc(level, V.add(c, v, -0.25), v, Opt.of("shape", "thin", "flip", false, "size", 1.4 * near, "life", 0.22, "speed", 1.0, "spin", 70.0, "color", glow,
                "aura", DARK));
        SkillFx.cutMark(level, c, v, 70, 0.12, glow, core, near);
        SkillFx.burst(level, V.add(c, v, -0.2), DARK, 0.8 * near, 0.25);
        SkillFx.sparks(level, c, core, 5, 4);
        SkillFx.play(level, "game.player.attack.critical", c, 1.3, 0.8);
        SkillFx.play(level, "item.spear.attack_miss", c, 1.5, 0.8);
    }

    /** 분신 둘: 그 적의 양옆 (내가 보는 쪽에서 왼쪽 · 오른쪽) 에 나타나 그 적에게 덤벼들며 X 로 벰 */
    static void clonesFx(ServerPlayer sp, LivingEntity target, Vec3 c, double[] glow, double[] core) {
        ServerLevel level = sp.level();
        Vec3 eye = sp.getEyePosition();
        Vec3 toT = V.flat(V.sub(c, eye));
        Vec3 side = V.rightOf(toT);
        double pad = Kit.fallen(target) ? 0.4 : Kit.bodyPad(target);
        Vec3 feet = new Vec3(c.x, c.y - 0.9, c.z);
        if (!Kit.fallen(target)) feet = target.position();
        for (int s : new int[] {-1, 1}) {
            Vec3 at = V.add(feet, side, s * (pad + 1.1));
            Vec3 face = V.scale(side, -s); // 그 적 쪽
            RuneKit.shadeClone(sp, level, at, face, 2, DARK, glow, Opt.of("size", 0.95, "life", 0.42, "fade", 0.45, "alpha", 0.9, "vel", V.scale(face, 2.2)));
            SkillFx.burst(level, V.up(at, 1.0), DARK, 0.7, 0.25);
        }
        double near = HeroKit.near(eye, c, 0.45);
        Vec3 v = V.unit(V.sub(c, eye));
        for (int s : new int[] {-1, 1}) {
            SkillFx.arc(level, V.add(c, v, -0.2), v, Opt.of("shape", "thin", "flip", s < 0, "size", 1.7 * near, "life", 0.24, "speed", 1.2, "spin", s * 45.0,
                    "color", glow, "aura", DARK));
        }
        SkillFx.cutMark(level, c, v, 45, 0.1, glow, core, near);
        SkillFx.cutMark(level, c, v, -45, 0.1, glow, core, near);
        SkillFx.sparks(level, c, core, 6, 4);
        SkillFx.play(level, "item.enchant.lunge2", c, 1.1, 1.0);
        SkillFx.play(level, "item.spear.attack_miss", c, 1.2, 0.9);
    }

    /** 그림자 폭발: 분신이 빨려 들어가는 검은 구멍 → 보랏빛 폭발 · 고리 (반지름 r) · 그림자 조각 */
    static void burstFx(ServerPlayer sp, Vec3 c, double r, double[] glow, double[] core) {
        ServerLevel level = sp.level();
        Vec3 eye = sp.getEyePosition();
        double near = HeroKit.near(eye, c, 0.5);
        Vec3 v = V.unit(V.sub(c, eye));
        RuneKit.rfx(level, "hole", V.add(c, v, -0.1), Opt.of("size", 0.9 * near, "life", 0.3, "grow", -0.6, "fade", 0.4, "color", glow, "alpha", 0.95, "cell", 0.0,
                "spinRate", 360.0));
        SkillFx.burst(level, V.add(c, v, -0.2), glow, 1.5 * near, 0.35);
        SkillFx.burst(level, V.add(c, v, -0.15), DARK, 1.1 * near, 0.4);
        Vec3 feet = new Vec3(c.x, c.y - 0.9, c.z);
        SkillFx.shock(level, feet, glow, r / 2.1, 0.45);
        SkillFx.sparks(level, c, core, 10, 5);
        for (int i = 0; i < 6; i++) {
            Vec3 d = V.unit(new Vec3(Math.cos(i * Math.PI / 3), 0.4, Math.sin(i * Math.PI / 3)));
            SkillFx.fx(level, "rpg:arrow_spark", c, Opt.of("dir", d, "speed", 5.0, "size", 0.22, "grow", 0.3, "life", 0.35, "color", DARK, "alpha", 0.9));
        }
        SkillFx.play(level, "firework.large_blast", c, 0.6, 0.6);
        SkillFx.play(level, "mob.endermen.portal", c, 0.5, 0.9);
        Kit.shakeNear(level, c, 8, 0.2, 0.2);
    }
}
