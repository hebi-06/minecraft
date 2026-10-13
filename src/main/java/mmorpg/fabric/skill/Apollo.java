package mmorpg.fabric.skill;

import java.util.function.BooleanSupplier;
import mmorpg.core.Obj;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 아폴론의 태양홀 (초월 성스러운 홀 — 12단계, 자바 전용): 작은 태양 · 태양신의 성역. 수치는 SKILLS.scepter, 색은 햇빛 주황 · 흰 심지. 피해는 마법 (홀).
 * 그림: rpg:rfx_sun (해), rfx_rays · rfx_sigil (holy) · rfx_nova · rfx_flare, 불꽃.
 * 쓰러지거나 차원을 옮기면 남은 것은 멈춤
 */
final class Apollo {
    private Apollo() {}

    static void register() {
        Transcend.register("apollo_sun", Apollo::sun);
        Transcend.register("apollo_sanctuary", Apollo::sanctuary);
    }

    /** 해 그림 한 겹 (빛 + 심지, 천천히 돎) */
    static void sunFx(ServerLevel level, Vec3 at, double size, double life, double[] glow, double[] core, double alpha) {
        RuneKit.rfx2(level, "sun", at, Opt.of("size", size, "life", life, "spin", Kit.rand() * 360, "spinRate", 40.0, "fade", 0.25, "alpha", alpha), glow, core,
                new int[] {0, 1}, null);
    }

    // ------------------------------------------------------------ 스킬 1 작은 태양

    /**
     * 작은 태양: 조준한 곳 (range 칸 — 적 몸 · 막힌 블록 · 사거리 끝) 위 1.5칸으로 작은 해가 틱당 speed 칸 날아가 떠오름 → gap 틱마다 pulses 번 둘레 radius 칸의
     * 적을 태움 (pulseMult, 불붙음 fire 초) → 마지막에 해가 터짐: 둘레 burst 칸 (afterMult). 대상이 없어도 씀
     */
    static boolean sun(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = HeroKit.colors(k);
        double[] glow = cs[0];
        double[] core = cs[1];
        Vec3 f = Kit.facingOf(sp);
        Vec3 staff = V.add(V.add(V.up(sp.getEyePosition(), 0.1), f, 1.0), V.rightOf(f), 0.35);
        Vec3 aim = Kit.aimPoint(sp, k.num("range"));
        Vec3 at = V.up(aim, 1.5);
        double speed = k.num("speed");
        int fly = Math.max(2, (int) Math.ceil(V.len(V.sub(at, staff)) / speed));
        Vec3 u = V.unit(V.sub(at, staff));
        sunFx(level, staff, 0.4, 0.3, glow, core, 1.0);
        RuneKit.rfx(level, "sun", staff, Opt.of("size", 0.6, "life", fly / 20.0 + 0.05, "speed", V.len(V.sub(at, staff)) / (fly / 20.0), "dir", u, "color", glow,
                "alpha", 0.95, "cell", 0.0, "spinRate", 90.0));
        RuneKit.streak(level, staff, at, glow, 0.12, fly / 20.0 + 0.2, 0.8, 0);
        SkillFx.play(level, "mob.blaze.shoot", staff, 0.8, 0.8);
        SkillFx.play(level, "beacon.power", staff, 1.3, 0.6);
        int pulses = (int) k.num("pulses");
        int gap = (int) k.num("gap");
        double r = k.num("radius");
        int burn = (int) Math.max(1, Math.round(pulses * gap / 20.0 * 20));
        Tasks.later(fly, () -> {
            if (!ok.getAsBoolean()) return;
            // 떠 있는 해 (빛 · 심지 · 빛살) + 땅에 비친 햇빛
            sunFx(level, at, 1.8, burn / 20.0 + 0.2, glow, core, 0.95);
            RuneKit.rfx(level, "rays", V.up(at, -1.5), Opt.of("size", 1.8, "life", burn / 20.0 + 0.2, "fade", 0.3, "color", glow, "alpha", 0.6, "cell", 0.0));
            RuneKit.domainSigil(level, aim, r, "holy", glow, core, Opt.of("life", burn / 20.0 + 0.3, "spinRate", 45.0, "alpha", 0.7));
            SkillFx.play(level, "fire.ignite", at, 0.7, 1.0);
        });
        for (int p = 0; p < pulses; p++) {
            final int pp = p;
            Tasks.later(fly + p * gap, () -> {
                if (!ok.getAsBoolean()) return;
                // 햇빛이 쏟아짐: 해에서 둘레로 빛줄기 · 불꽃 고리
                SkillFx.shock(level, aim, glow, r * 0.5, 0.35);
                for (int j = 0; j < 4; j++) {
                    double a = j / 4.0 * Math.PI * 2 + pp * 0.7;
                    Vec3 g = new Vec3(aim.x + Math.cos(a) * r * 0.6, aim.y, aim.z + Math.sin(a) * r * 0.6);
                    RuneKit.streak(level, at, V.up(g, 0.2), glow, 0.1, 0.25, 0.8, 0);
                    SkillFx.fx(level, "rpg:fire_wall", g, Opt.of("size", 0.8, "life", 0.4, "color", glow, "alpha", 0.9, "spin", Kit.rand() * 360));
                }
                SkillFx.play(level, "fire.fire", at, 0.9, 1.0);
                long now = Tasks.now();
                for (LivingEntity e : Kit.around(sp, aim, r, 3)) {
                    Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now), ok, () -> {
                        if (Kit.skillHit(sp, e, w, k.num("pulseMult")) <= 0) return;
                        RuneKit.burn(e, k.num("fire"));
                        SkillFx.sparks(level, HeroKit.body(e, aim), glow, 3, 3);
                    });
                }
            });
        }
        Tasks.later(fly + pulses * gap, () -> {
            if (!ok.getAsBoolean()) return;
            double br = k.num("burst");
            sunFx(level, at, 2.6, 0.3, core, core, 1.0);
            RuneKit.novaRing(level, aim, br * 0.4, 1.6, glow, core, Opt.of("life", 0.45));
            RuneKit.skyFlare(level, at, 3.0, glow, core, Opt.of("life", 0.45));
            SkillFx.burst(level, at, glow, 2.0, 0.35);
            SkillFx.fx(level, "rpg:fire_wall", aim, Opt.of("size", br * 1.3, "life", 0.6, "color", glow, "alpha", 0.95, "spin", Kit.rand() * 360));
            SkillFx.play(level, "random.explode", at, 1.1, 0.9);
            SkillFx.play(level, "firework.large_blast", at, 0.8, 0.8);
            long now = Tasks.now();
            for (LivingEntity e : Kit.around(sp, aim, br, 3)) {
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now), ok, () -> {
                    if (Kit.skillHit(sp, e, w, k.num("afterMult")) <= 0) return;
                    RuneKit.burn(e, k.num("fire"));
                    Vec3 d = V.flat(V.sub(e.position(), aim));
                    Kit.knockback(e, d.x * 0.5, d.z * 0.5, 0.3);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 스킬 2 태양신의 성역

    /**
     * 태양신의 성역: 쓴 자리 반지름 radius 칸에 태양 문양이 pulses × gap 틱 동안 펼쳐짐 — gap 틱마다 안의 적을 태움 (pulseMult) · 안의 플레이어 (나 · 결투 상대가
     * 아닌 사람) 는 최대 체력의 heal 만큼 회복 (초록 숫자) · 안에 있는 동안 받는 피해 -reduce (방벽 버프 — 성역을 나가면 그 순간 풀림) → 끝에 태양 폭발
     * (afterMult, 바깥으로 밀쳐냄 knock). 대상이 없어도 씀
     */
    static boolean sanctuary(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = HeroKit.colors(k);
        double[] glow = cs[0];
        double[] core = cs[1];
        Vec3 c = sp.position();
        double r = k.num("radius");
        int pulses = (int) k.num("pulses");
        int gap = (int) k.num("gap");
        int total = pulses * gap;
        double secs = total / 20.0;
        // 땅의 태양 문양 (holy 문양 + 해 + 빛살) · 하늘의 해 · 둘레 빛기둥
        RuneKit.domainSigil(level, c, r, "holy", glow, core, Opt.of("life", secs + 0.4, "spinRate", 18.0, "alpha", 0.85, "grow", 0.05));
        RuneKit.domainSigil(level, V.up(c, 0.02), r * 0.55, "arcane", glow, core, Opt.of("life", secs + 0.4, "spinRate", -30.0, "alpha", 0.6));
        double sky = Math.max(4, Math.min(9, Kit.skyRoom(level, V.up(c, 1), 9, true)));
        sunFx(level, V.up(c, sky), 3.2, secs + 0.3, glow, core, 0.9);
        RuneKit.godRays(sp, level, c, r, 8, glow, core, Opt.of("h", 12.0, "life", 1.0, "alpha", 0.75));
        SkillFx.play(level, "beacon.activate", c, 1.0, 1.0);
        SkillFx.play(level, "block.bell.hit", c, 0.8, 0.8);
        RuneKit.selfCue(sp, glow, core, true);
        double reduce = k.num("reduce");
        for (int p = 0; p < pulses; p++) {
            final int pp = p;
            Tasks.later(p * gap, () -> {
                if (!ok.getAsBoolean()) return;
                RuneKit.zonePulse(level, c, glow, r, 0.4);
                if (pp % 2 == 0) {
                    for (int j = 0; j < 3; j++) {
                        double a = Kit.rand() * Math.PI * 2;
                        double rr = r * Math.sqrt(Kit.rand());
                        RuneKit.rfx(level, "rays", new Vec3(c.x + Math.cos(a) * rr, c.y, c.z + Math.sin(a) * rr), Opt.of("size", 2.2, "life", 0.5, "fade", 0.6, "color",
                                glow, "alpha", 0.7, "cell", 0.0));
                    }
                }
                // 안의 플레이어: 회복 · 피해 감소 (성역 안에서만)
                for (ServerPlayer p2 : RuneKit.playersAround(level, c, r, sp)) {
                    if (p2 != sp && Kit.duelPartner.test(sp, p2)) continue;
                    double healed = RpgHealth.healBy(p2, RpgHealth.maxOf(p2) * k.num("heal"));
                    if (healed >= 0.05) DamageNumbers.show(p2, healed, DamageNumbers.Style.HEAL, "", 0);
                    Guards.set(p2, gap + 2, reduce, null, q -> V.hdist(q.position(), c) <= r + 0.5 && Kit.sameLife(sp, life));
                    if (pp % 3 == 0) SkillFx.sparks(level, V.up(p2.position(), 1.0), core, 3, 2);
                }
                long now = Tasks.now();
                for (LivingEntity e : Kit.around(sp, c, r, 3)) {
                    Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now), ok, () -> {
                        if (Kit.skillHit(sp, e, w, k.num("pulseMult")) <= 0) return;
                        Vec3 b = HeroKit.body(e, c);
                        RuneKit.rfx(level, "rays", e.position(), Opt.of("size", 1.4, "life", 0.3, "fade", 0.6, "color", glow, "alpha", 0.8, "cell", 0.0));
                        SkillFx.sparks(level, b, glow, 3, 3);
                    });
                }
                if (pp % 2 == 0) SkillFx.play(level, "beacon.ambient", c, 1.4, 0.8);
            });
        }
        Tasks.later(total, () -> {
            if (!ok.getAsBoolean()) return;
            Vec3 top = V.up(c, sky);
            Gungnir.beamAway(level, sp.getEyePosition(), top, V.up(c, 0.5), glow, Opt.of("half", 0.7, "thick", 0.7, "life", 0.4, "gap", 1.0));
            sunFx(level, V.up(c, 1.2), 4.0, 0.35, core, core, 1.0);
            RuneKit.novaRing(level, c, r * 0.3, 2.4, glow, core, Opt.of("life", 0.55));
            RuneKit.novaRing(level, c, r * 0.2, 3.2, core, core, Opt.of("life", 0.45));
            SkillFx.fx(level, "rpg:fire_wall", c, Opt.of("size", r * 1.4, "life", 0.6, "color", glow, "alpha", 0.95, "spin", Kit.rand() * 360));
            SkillFx.play(level, "random.explode", c, 0.9, 1.0);
            SkillFx.play(level, "firework.large_blast", c, 0.7, 0.9);
            SkillFx.shake(sp, 0.3, 0.3);
            long now = Tasks.now();
            double knock = k.num("knock");
            for (LivingEntity e : Kit.around(sp, c, r, 3)) {
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now), ok, () -> {
                    if (Kit.skillHit(sp, e, w, k.num("afterMult")) <= 0) return;
                    Vec3 d = V.flat(V.sub(e.position(), c));
                    Kit.knockback(e, d.x * knock, d.z * knock, 0.3);
                    RuneKit.burn(e, 3);
                });
            }
        });
        return true;
    }
}
