package mmorpg.fabric.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import mmorpg.core.Obj;
import mmorpg.core.Weapon;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 하르페 (영웅 낫 — 12단계, 자바 전용): 초승달 회전베기. 수치는 HERO_SKILLS.scythe, 색은 달빛 보라 · 흰 심지.
 * 낫을 크게 두 바퀴 (hits 번, gap 틱 간격) 휘둘러 둘레 radius 칸의 적을 벰 (mult — 몸 둘레의 초승달 참격 여덟 · 바닥 소용돌이) → 벤 적에게 초승달 낙인
 * (머리 위 초승달) → 두 번째 바퀴 delay 틱 뒤 낙인이 터짐 (afterMult — 적을 따라감). 대상이 없어도 씀
 */
final class Harpe {
    private Harpe() {}

    static void register() {
        Transcend.register("harpe_spin", Harpe::spin);
    }

    static boolean spin(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = HeroKit.colors(k);
        double[] glow = cs[0];
        double[] core = cs[1];
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        double radius = k.num("radius");
        Map<UUID, LivingEntity> branded = new LinkedHashMap<>();
        for (int i = 0; i < hits; i++) {
            final int ii = i;
            HeroKit.at(i * gap, () -> {
                if (!ok.getAsBoolean()) return;
                Vec3 c = sp.position();
                // 몸 둘레의 초승달 여덟 (돌아가는 쪽으로 — 바퀴마다 반대로 기울임)
                for (int j = 0; j < 8; j++) {
                    double a = j / 8.0 * Math.PI * 2 + ii * 0.4;
                    Vec3 dir = new Vec3(Math.cos(a + Math.PI / 2), 0, Math.sin(a + Math.PI / 2));
                    Vec3 at = new Vec3(c.x + Math.cos(a) * radius * 0.6, c.y + 1.0, c.z + Math.sin(a) * radius * 0.6);
                    Opt o = Opt.of("shape", "arc", "flip", ii % 2 == 1, "size", 2.1, "life", 0.26, "speed", 2.5, "spin", ii % 2 == 0 ? 25.0 : -25.0, "color", glow,
                            "aura", j % 2 == 0 ? core : null);
                    int jj = j;
                    HeroKit.at(jj / 3, () -> SkillFx.arc(level, at, dir, o));
                }
                SkillFx.fx(level, "rpg:whirlpool", V.up(c, 0.1), Opt.of("dir", V.ZERO, "size", radius * 0.95, "life", 0.45, "spin", ii * 90.0, "speed", -540.0,
                        "color", glow, "alpha", 0.55));
                RuneKit.crescent(level, V.up(c, 0.08), V.UP, radius * 1.1, glow, core, ii * 120.0, 0.4);
                SkillFx.play(level, "entity.player.attack.sweep", c, 0.7 + ii * 0.15, 0.9);
                SkillFx.play(level, "mob.breeze.whirl", c, 1.3, 0.5);
                long now = Tasks.now();
                for (LivingEntity e : Kit.around(sp, c, radius, 2.5)) {
                    Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now), ok, () -> {
                        if (Kit.skillHit(sp, e, w, k.num("mult")) <= 0) return;
                        branded.putIfAbsent(e.getUUID(), e);
                        Vec3 b = HeroKit.body(e, c);
                        Vec3 v = V.unit(V.sub(b, sp.getEyePosition()));
                        double near = HeroKit.near(sp.getEyePosition(), b, 0.5);
                        SkillFx.arc(level, V.add(b, v, -0.4), v, Opt.of("shape", "thin", "flip", ii % 2 == 0, "size", 1.0 * near, "life", 0.2, "speed", 0.0, "spin",
                                Kit.rand() * 50 - 25, "color", glow));
                        SkillFx.sparks(level, b, core, 4, 4);
                        // 초승달 낙인 (머리 위 — 터질 때까지)
                        if (ii == 0) RuneKit.crescent(level, RuneKit.overHead(e, 0.45), V.unit(V.sub(sp.getEyePosition(), b)), 0.55, glow, core, -30, 0.05
                                + (k.num("delay") + gap) / 20.0);
                    });
                }
            });
        }
        // 낙인이 터짐: 마지막 바퀴 delay 틱 뒤 (적을 따라감)
        HeroKit.at((hits - 1) * gap + (int) k.num("delay"), () -> {
            if (!ok.getAsBoolean()) return;
            long now = Tasks.now();
            List<LivingEntity> list = List.copyOf(branded.values());
            if (!list.isEmpty()) {
                SkillFx.play(level, "random.glass", sp.position(), 1.4, 0.5);
                SkillFx.play(level, "chime.amethyst_block", sp.position(), 0.9, 1.0);
            }
            for (LivingEntity e : list) {
                if (!Kit.canHit(sp, e)) continue;
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now), ok, () -> {
                    if (Kit.skillHit(sp, e, w, k.num("afterMult")) <= 0) return;
                    Vec3 b = HeroKit.body(e, sp.position());
                    Vec3 v = V.unit(V.sub(b, sp.getEyePosition()));
                    double near = HeroKit.near(sp.getEyePosition(), b, 0.45);
                    RuneKit.crescent(level, V.add(b, v, -0.4), v, 1.3 * near, glow, core, 30, 0.3);
                    SkillFx.cutMark(level, V.add(b, v, -0.4), v, 35, 0.05, glow, core, 1.2 * near);
                    SkillFx.cutMark(level, V.add(b, v, -0.4), v, -35, 0.05, glow, core, 1.2 * near);
                    SkillFx.burst(level, b, glow, 1.0 * near, 0.3);
                    SkillFx.sparks(level, b, core, 6, 5);
                });
            }
        });
        return true;
    }
}
