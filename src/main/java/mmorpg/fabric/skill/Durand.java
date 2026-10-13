package mmorpg.fabric.skill;

import java.util.ArrayList;
import java.util.List;
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
 * 듀란드의 방패 (영웅 방패) — 성광 강타 (10단계, 자바 전용). 수치는 HERO_SKILLS.shield, 색은 디자인의 금빛 테 · 흰 방패 · 빛나는 보석.
 * 눈앞에 빛의 방패가 나타나 내리침: 앞쪽 cone 도 · range 칸 (눈에서 보이는) 의 적을 침 (mult, 밀쳐냄 knock, stun 틱 기절) →
 * 나에게 guard 틱 동안 받는 피해 -reduce (발밑에 성스러운 문양이 따라다님) → delay 틱 뒤 친 적에게 하늘에서 빛기둥이 내리꽂힘
 * (afterMult — 밀려났어도 쓴 자리에서 range + 6칸 안). 대상이 없어도 씀 (보호막만 — 재사용 대기). 쓰러지거나 차원을 옮기면 남은 것은 멈춤
 */
final class Durand {
    private Durand() {}

    static void register() {
        Transcend.register("durand_smite", Durand::smite);
    }

    static boolean smite(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = HeroKit.colors(k);
        double[] glow = cs[0];
        double[] core = cs[1];
        Vec3 pos = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 eye = sp.getEyePosition();
        // 빛의 방패: 눈앞 2칸에서 앞으로 밀려나며 커짐 + 내리치는 금빛 초승달 + 앞으로 퍼지는 빛의 물결
        RuneKit.rfx2(level, "shield", V.add(V.up(eye, -0.35), f, 2.0), Opt.of("dir", f, "size", 0.9, "life", 0.4, "speed", 3.5, "grow", 0.45, "fade", 0.55,
                "alpha", 0.9), glow, core, new int[] {0, 1}, null);
        SkillFx.arc(level, V.add(V.up(eye, -0.25), f, 1.6), V.unit(V.add(f, V.DOWN, 0.5)), Opt.of("shape", "heavy", "flip", false, "size", 2.0, "life", 0.22,
                "speed", 1.2, "color", glow, "aura", core, "spin", 80.0));
        RuneKit.rfx2(level, "wave", V.add(V.up(pos, 0.9), f, 2.3), Opt.of("dir", f, "size", 3.4, "life", 0.3, "speed", 9.0, "fade", 0.6, "alpha", 0.85), glow, core,
                new int[] {0, 1}, null);
        SkillFx.shock(level, V.add(pos, f, 2.0), glow, 1.5, 0.35);
        SkillFx.play(level, "item.shield.block", pos, 0.8, 1.0);
        SkillFx.play(level, "block.bell.hit", pos, 1.3, 0.9);
        SkillFx.play(level, "mace.smash_air", pos, 1.0, 0.7);
        SkillFx.shake(sp, 0.18, 0.15);
        // 친 적: 앞쪽 부채꼴 (눈에서 보이는)
        double half = Math.toRadians(k.num("cone") / 2);
        List<LivingEntity> inCone = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, pos, k.num("range"))) if (Kit.bodyInCone(e, pos, f, half)) inCone.add(e);
        List<LivingEntity> targets = Kit.visibleFrom(sp, eye, inCone);
        long now = Tasks.now();
        double knock = k.num("knock");
        int stun = (int) k.num("stun");
        for (LivingEntity e : targets) {
            Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now), ok, () -> {
                if (Kit.skillHit(sp, e, w, k.num("mult")) <= 0) return;
                Vec3 c = HeroKit.body(e, eye);
                double near = HeroKit.near(sp.getEyePosition(), c, 0.5);
                RuneKit.hitFlash(level, c, f, glow, core, 0.9 * near);
                SkillFx.play(level, "random.anvil_land", c, 1.8, 0.35);
                Vec3 d = V.flat(V.sub(e.position(), pos));
                Kit.knockback(e, d.x * knock, d.z * knock, 0.35);
                RuneKit.stun(e, stun, glow, true);
            });
        }
        // 보호막: guard 틱 동안 받는 피해 -reduce, 발밑 성스러운 문양 (따라다님)
        int guard = (int) k.num("guard");
        Guards.set(sp, guard, k.num("reduce"), null, null);
        int holy = RuneKit.SIGIL.get("holy");
        SkillFx.burst(level, V.up(pos, 0.4), glow, 1.2, 0.3);
        SkillFx.play(level, "beacon.power", pos, 1.6, 0.6);
        int[] glint = RuneKit.RFX.get("glint");
        for (int t = 0; t < guard; t += 5) {
            final int tt = t;
            HeroKit.at(t, () -> {
                if (!ok.getAsBoolean() || Guards.of(sp) == null) return;
                Vec3 feet = sp.position();
                // 몸을 도는 금빛 반짝임 셋 (1인칭 화면 아래 · 3인칭 몸 둘레 — 보호막이 걸린 동안)
                for (int j = 0; j < 3; j++) {
                    Vec3 at = V.up(V.add(feet, V.turnY(new Vec3(1, 0, 0), tt * 24 + j * 120), 0.85), 0.9 + 0.25 * j);
                    RuneKit.rfx2(level, "prop", at, Opt.of("size", 0.12, "life", 0.3, "fade", 0.6, "spin", Kit.rand() * 90, "alpha", 0.95), glow, core, glint, null);
                }
                if (tt % 10 != 0) return;
                RuneKit.rfx2(level, "sigil", V.up(feet, 0.06), Opt.of("dir", V.UP, "size", 1.3, "life", 0.6, "spin", tt * 4.0, "spinRate", 40.0, "fade", 0.3, "alpha",
                        0.8, "cell", (double) holy), glow, core, new int[] {0, 1}, null);
                if (tt % 20 == 0) {
                    for (int j = 0; j < 3; j++) {
                        Vec3 at = V.add(feet, V.turnY(f, tt * 7 + j * 120), 0.75);
                        RuneKit.rfx(level, "rays", at, Opt.of("size", 0.7, "life", 0.6, "fade", 0.6, "color", glow, "alpha", 0.7, "cell", 0.0, "vel", new Vec3(0, 0.6, 0)));
                    }
                }
            });
        }
        // 빛기둥: delay 틱 뒤 친 적에게 하늘에서
        Tasks.later((int) k.num("delay"), () -> {
            if (!ok.getAsBoolean()) return;
            long t0 = Tasks.now();
            double reach = k.num("range") + 6;
            List<LivingEntity> list = new ArrayList<>();
            // 친 적 (몹 무적 시간으로 강타가 밀렸어도 — 빛기둥은 그 뒤로 밀림)
            for (LivingEntity e : targets) if (Kit.canHit(sp, e) && V.hdist(e.position(), pos) <= reach) list.add(e);
            if (!list.isEmpty()) {
                SkillFx.play(level, "block.bell.hit", pos, 1.7, 0.8);
                SkillFx.play(level, "ambient.weather.lightning.impact", pos, 1.9, 0.25);
            }
            for (LivingEntity e : list) {
                Vec3 feet = e.position();
                pillarFx(sp, feet, glow, core);
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, t0), ok, () -> Kit.skillHit(sp, e, w, k.num("afterMult")));
            }
        });
        return true;
    }

    /** 하늘에서 내리꽂히는 빛기둥: 위에서 아래로 그어지는 빛줄기 → 발밑 빛기둥 (rfx_rays) · 고리 · 불꽃 (눈 가까이면 심지는 빼고) */
    static void pillarFx(ServerPlayer sp, Vec3 feet, double[] glow, double[] core) {
        ServerLevel level = sp.level();
        double room = Math.max(2.5, Math.min(9, Kit.skyRoom(level, V.up(feet, 0.2), 9, true)));
        Vec3 top = V.up(feet, room);
        SkillFx.beam(level, top, V.up(feet, 0.2), glow, Opt.of("half", 0.6, "thick", 0.5, "life", 0.3, "sweep", 0.08, "gap", 1.0));
        SkillFx.beam(level, top, V.up(feet, 0.2), core, Opt.of("half", 0.55, "thick", 0.16, "life", 0.25, "sweep", 0.08, "gap", 1.0));
        boolean far = V.len(V.sub(V.up(feet, 1.0), sp.getEyePosition())) >= 2.5;
        RuneKit.rfx(level, "rays", feet, Opt.of("size", Math.min(room / 2, 3.0), "life", 0.5, "fade", 0.6, "color", glow, "alpha", 0.85, "cell", 0.0));
        if (far) RuneKit.rfx(level, "rays", feet, Opt.of("size", Math.min(room / 2, 3.0) * 0.95, "life", 0.4, "fade", 0.6, "color", core, "alpha", 1.0, "cell", 1.0));
        SkillFx.shock(level, feet, glow, 0.9, 0.4);
        SkillFx.burst(level, V.up(feet, 0.8), glow, 1.0, 0.3);
        SkillFx.sparks(level, V.up(feet, 0.8), core, 6, 4);
    }
}
