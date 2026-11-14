package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneProps;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-14 폭풍 궁수 스톰 아이 (archer_focus — 와일드 헌트의 늑대 정령 대신): 태세 동안 머리 위를 따라다니는 3D 먹구름 (구름 덩이 셋 — 밑이 번개빛) · 구름 속 번쩍임.
 * 활 평타가 겨눈 적을 맞히면 ({@link #strike}) 구름에 빛이 모였다가 DELAY 틱 뒤 그 적에게 벼락이 내리꽂힘 — 피해는 평타 × bite (RpgCombat.bonusHit — 치명타 · 더한 피해까지
 * 평타 그대로, 늑대가 물던 때와 같은 틱). 태세가 끝나거나 직업 · 목숨이 바뀌면 구름이 작아지며 흩어짐
 */
final class StormEye {
    private StormEye() {}

    /** 맞힌 틱에서 벼락이 떨어지기까지 (예전 늑대가 뛰어올라 물기까지와 같음) */
    static final int DELAY = 6;
    /** 구름 가운데 높이 (발에서) */
    static final double HIGH = 3.6;
    /** 구름 덩이: 보는 쪽 · 오른쪽 · 위 (칸) · 반 너비 */
    private static final double[][] PUFFS = {{-0.2, 0, 0, 1.55}, {0.35, 1.25, -0.22, 1.1}, {-0.55, -1.2, -0.18, 1.05}};

    private static final class Eye {
        final RuneProps.CloudBank bank;
        final String life;
        final long until;
        final double[] glow;
        final double[] core;
        Vec3 center;
        int age;

        Eye(RuneProps.CloudBank bank, String life, long until, double[] glow, double[] core, Vec3 center) {
            this.bank = bank;
            this.life = life;
            this.until = until;
            this.glow = glow;
            this.core = core;
            this.center = center;
        }
    }

    private static final Map<UUID, Eye> EYES = new HashMap<>();

    /** 구름 가운데 (몸 위 — 조금 뒤: 1인칭 화면 위쪽 끝에 걸쳐 보임) */
    static Vec3 centerOf(ServerPlayer sp) {
        return V.add(V.up(sp.position(), HIGH), Kit.facingOf(sp), -0.35);
    }

    private static Vec3[] puffsAt(Vec3 c, Vec3 f, double bob) {
        Vec3 r = V.rightOf(f);
        Vec3[] out = new Vec3[PUFFS.length];
        for (int i = 0; i < PUFFS.length; i++) {
            double[] p = PUFFS[i];
            out[i] = V.up(V.add(V.add(c, f, p[0]), r, p[1]), p[2] + bob * (i == 0 ? 1 : -0.6));
        }
        return out;
    }

    /** 먹구름을 부름 (ticks 틱 — 앞 구름은 흩어짐) */
    static void summon(ServerPlayer sp, Jobs.Job j, int ticks) {
        dismiss(sp);
        ServerLevel level = sp.level();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        Vec3 c = centerOf(sp);
        Vec3[] at = puffsAt(c, Kit.facingOf(sp), 0);
        double[] halves = new double[PUFFS.length];
        for (int i = 0; i < PUFFS.length; i++) halves[i] = PUFFS[i][3];
        RuneProps.CloudBank bank = RuneProps.clouds(level, at, halves, ArcherSkills.CLOUD, glow, ticks + 20);
        Eye e = new Eye(bank, RuneKit.lifeToken(sp), Tasks.now() + ticks, glow, core, c);
        EYES.put(sp.getUUID(), e);
        loop(sp, e);
    }

    /** 2틱마다 몸을 따라감 (앞질러 옮김 — 2틱 보간) · 구름 속 번쩍임 · 끝나면 흩어짐 */
    private static void loop(ServerPlayer sp, Eye e) {
        RuneKit.later(2, () -> {
            if (EYES.get(sp.getUUID()) != e) return;
            Jobs.Job j = JobSystem.job(sp);
            if (!RuneKit.sameLife(sp, e.life) || sp.isRemoved() || j == null || !"archer".equals(j.id()) || !JobTraits.stanceOn(sp, "archer_focus")
                    || Tasks.now() >= e.until) {
                EYES.remove(sp.getUUID());
                fade(e);
                return;
            }
            e.age += 2;
            Vec3 c = centerOf(sp);
            Vec3 lead = V.sub(c, e.center);
            if (V.len(lead) > 4) lead = Vec3.ZERO;
            e.center = c;
            double bob = 0.12 * Math.sin(e.age * 0.18);
            Vec3[] at = puffsAt(V.add(c, lead, 0.6), Kit.facingOf(sp), bob);
            for (int i = 0; i < at.length; i++) e.bank.place(i, at[i], PUFFS[i][3] * (1 + 0.04 * Math.sin(e.age * 0.21 + i)), 2);
            ServerLevel level = sp.level();
            if (e.age % 8 == 0) {
                // 구름 속 번쩍임 · 덩이 사이 작은 번개 (3인칭 · 다른 사람 — 1인칭에서는 머리 위라 거의 보이지 않음)
                Vec3 p = V.add(at[e.age / 8 % at.length], new Vec3(Kit.rand() - 0.5, -0.2, Kit.rand() - 0.5));
                SkillFx.fx(level, "rpg:arrow_spark", p, Opt.of("dir", V.UP, "size", 1.1, "grow", 1.4, "life", 0.16, "color", e.glow, "alpha", 0.85));
                if (e.age % 16 == 0) RuneKit.zap(level, V.up(at[1], -0.3), V.up(at[2], -0.3), e.glow, e.core, 0.07, 0.15);
            }
            if (e.age % 40 == 0) SkillFx.play(level, "ambient.weather.thunder", c, 1.6, 0.12);
            loop(sp, e);
        });
    }

    private static void fade(Eye e) {
        e.bank.end(8);
    }

    /** 구름을 지움 (흩어짐) */
    static void dismiss(ServerPlayer sp) {
        Eye e = EYES.remove(sp.getUUID());
        if (e != null) fade(e);
    }

    /** 시험 · 화면: 구름이 떠 있는지 */
    static boolean on(ServerPlayer sp) {
        return EYES.containsKey(sp.getUUID());
    }

    /** 구름 밑 (벼락이 나오는 자리 — 구름이 없으면 몸 위) */
    private static Vec3 bottom(ServerPlayer sp) {
        Eye e = EYES.get(sp.getUUID());
        return V.up(e != null ? e.center : centerOf(sp), -0.55);
    }

    /**
     * 활 평타가 겨눈 적을 맞힘 → 구름에 빛이 모임 (DELAY 틱 — 구름 밑 번쩍임 · 빨려 드는 빛) → 그 적에게 벼락 (평타 × ratio — 치명타 · 더한 피해까지 평타 그대로):
     * 구름에서 적까지 굵은 번개 둘 · 몸에 번개 구슬 · 발밑 번쩍임 고리 · 불티 · 번개 소리
     */
    static void strike(ServerPlayer sp, Jobs.Job j, LivingEntity foe, Weapon w, double ratio) {
        if (foe == null || w == null) return;
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        ServerLevel level = sp.level();
        Vec3 b0 = bottom(sp);
        SkillFx.fx(level, "rpg:arrow_spark", b0, Opt.of("dir", V.UP, "size", 1.4, "grow", 1.2, "life", 0.2, "color", core, "alpha", 0.9));
        for (int i = 0; i < 4; i++) {
            Vec3 p = V.add(b0, RuneKit.turn(new Vec3(1, 0, 0), i * 90 + Kit.rand() * 40), 1.3);
            Vec3 d = V.sub(b0, p);
            SkillFx.fx(level, "rpg:arrow_spark", p, Opt.of("dir", V.unit(d), "speed", V.len(d) / 0.25, "size", 0.14, "grow", 0.4, "life", 0.25, "color", glow,
                    "alpha", 1.0));
        }
        String life = RuneKit.lifeToken(sp);
        RuneKit.later(DELAY, () -> {
            if (!RuneKit.sameLife(sp, life) || foe.isRemoved() || !foe.isAlive() || !(foe.level() instanceof ServerLevel lv)) return;
            RpgCombat.bonusHit(sp, foe, w, ratio);
            Vec3 top = lv == sp.level() ? bottom(sp) : V.up(foe.position(), 6);
            Vec3 at = Kit.bodyPoint(foe);
            RuneKit.zap(lv, top, at, glow, core, 0.26, 0.3);
            RuneKit.zap(lv, V.add(top, new Vec3(0.3, 0, -0.2)), V.up(at, 0.2), glow, core, 0.12, 0.22);
            // 2026-11-14 구름에서 적까지 2D 번개 줄 (rfx_jarc — 번쩍 갈라지며 사그라짐) · 발밑 2D 번개 고리
            JobFx.arc(lv, top, at, glow, core, 0.32, Opt.of("flash", true));
            JobFx.volt(lv, V.up(foe.position(), 0.06), 1.1 + Kit.bodyPad(foe), glow, core, 0.4, Opt.empty());
            RuneKit.plasmaOrb(lv, at, 0.7 + Kit.bodyPad(foe) * 0.4, glow, Opt.of("life", 0.3, "fade", 0.4, "vel", RuneKit.entVel(foe)));
            SkillFx.burst(lv, at, core, 1.3, 0.22);
            RuneKit.zonePulse(lv, foe.position(), glow, 1.6 + Kit.bodyPad(foe), 0.3);
            SkillFx.sparks(lv, at, core, 8, 5);
            JobFx.hit(foe, j, 1.1);
            SkillFx.play(lv, "ambient.weather.lightning.impact", at, 1.5, 0.35);
        });
    }
}
