package mmorpg.fabric.skill;

import java.util.ArrayList;
import java.util.Comparator;
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
import net.minecraft.world.phys.Vec3;

/**
 * 엘프의 활 (영웅 활) — 정령의 연사 (10단계, 자바 전용). 수치는 HERO_SKILLS.bow, 색은 디자인의 초록빛 시위 · 보석.
 * 조준한 적 (없으면 시선 cone 도 안에서 가장 가까운 적 — range 칸) 과 그 둘레 spread 칸의 적대 몹 (눈에서 보이는) 을 가까운 순으로 targets 명까지 고름 →
 * 내 왼쪽 위에 바람 정령 (실프) 이 나타나 나 (짝수 번째) 와 정령 (홀수 번째) 이 번갈아 화살을 shots 발 쏨 (적마다 돌아가며). 화살은 휘어 날며 적을 따라가
 * flight 틱 뒤 꽂힘 (mult — 한 발마다). 같은 적에게 가는 화살은 몹 무적 시간을 피해 꽂히는 틱을 먼저 잡고 그에 맞춰 쏨 (한 명이면 0.5초마다 한 발 —
 * 꽂히는 모습과 피해 숫자가 같은 틱). 적이 없으면 재사용 대기 없이 거절. 쓰러지거나 차원을 옮기면 남은 것은 멈춤
 */
final class ElfBow {
    private ElfBow() {}

    /** 나뭇잎 */
    static final double[] LEAF = {0.36, 0.78, 0.36};
    /** 바람 정령 그림 (rpg:rfx_spirit — 실프 4장) */
    static final int SYLPH = RuneKit.SPIRIT_CELL.get("sylph");
    /** 깃털 그림 (rpg:rfx_prop) {빛, 심지} */
    static final int[] FEATHER = RuneKit.RFX.get("feather");

    static void register() {
        Transcend.register("elf_volley", ElfBow::volley);
    }

    /**
     * 정령 자리: 선 눈높이 (발 + 1.62 — 웅크려 쓴 처음 몇 틱에도 같은 자리) 에서 앞 1칸 · 왼쪽 0.7칸 · 위 0.1칸 (1인칭 화면 왼쪽 위 — 3인칭에서는 왼쪽 어깨 앞)
     */
    static Vec3 sylphAt(ServerPlayer sp) {
        Vec3 f = Kit.facingOf(sp);
        return V.up(V.add(V.add(sp.position(), f, 1.0), V.rightOf(f), -0.7), 1.72);
    }

    /** 활 자리 (화살이 나가는 곳): 눈 앞 0.8칸 · 오른쪽 0.25칸 · 아래 0.2칸 */
    static Vec3 bowAt(ServerPlayer sp) {
        Vec3 f = Kit.facingOf(sp);
        return V.up(V.add(V.add(sp.getEyePosition(), f, 0.8), V.rightOf(f), 0.25), -0.2);
    }

    static boolean volley(ServerPlayer sp, Weapon w, Obj k) {
        LivingEntity main = Kit.pickTarget(sp, k.num("range"), k.num("cone") / 2);
        if (main == null) {
            Notify.warn(sp, "§c" + k.str("name") + ": §7앞쪽 " + k.text("range") + "칸 안에 겨눌 적이 없습니다");
            return false;
        }
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = HeroKit.colors(k);
        double[] glow = cs[0];
        double[] core = cs[1];
        // 고를 적: 조준한 적 + 그 둘레의 적대 몹 (가까운 순)
        List<LivingEntity> pool = new ArrayList<>();
        pool.add(main);
        Vec3 eye = sp.getEyePosition();
        List<LivingEntity> others = new ArrayList<>();
        for (LivingEntity e : Kit.visibleFrom(sp, eye, Kit.around(sp, main.position(), k.num("spread"), 4))) {
            if (e != main && HeroKit.hostile(e)) others.add(e);
        }
        others.sort(Comparator.comparingDouble(e -> V.hdist(e.position(), main.position())));
        for (LivingEntity e : others) {
            if (pool.size() >= (int) k.num("targets")) break;
            pool.add(e);
        }
        // 화살마다 꽂히는 틱을 잡고 (무적 시간을 피해) 그에 맞춰 쏨
        int shots = (int) k.num("shots");
        int flight = (int) k.num("flight");
        int gap = (int) k.num("gap");
        long now = Tasks.now();
        long next = now;
        long end = now;
        for (int i = 0; i < shots; i++) {
            LivingEntity e = pool.get(i % pool.size());
            long arrive = Kit.reserveHit(sp, e, next + flight);
            long launch = arrive - flight;
            next = launch + gap;
            end = Math.max(end, arrive);
            arrow(sp, w, k, ok, e, i, (int) (launch - now), flight, arrive, glow, core);
        }
        // 정령: 나타나 화살을 다 쏠 때까지 (매 3틱 다시 그림 — 나를 따라감)
        // 나타남: 정령 둘레에 작은 반짝임 · 나뭇잎 (눈 앞 1칸 남짓 — 큰 빛은 1인칭 화면을 가림)
        Vec3 s0 = sylphAt(sp);
        SkillFx.sparks(level, s0, core, 6, 1.0);
        SkillFx.play(level, "mob.allay.idle", s0, 1.3, 0.9);
        SkillFx.play(level, "chime.amethyst_block", s0, 1.6, 0.8);
        int stay = (int) (end - now) + 8;
        for (int t = 0; t < stay; t += 3) {
            final int tt = t;
            HeroKit.at(t, () -> {
                if (!ok.getAsBoolean()) return;
                Vec3 at = sylphAt(sp);
                double fade = tt + 3 >= stay ? 0.8 : 0;
                RuneKit.rfx(level, "spirit", at, Opt.of("size", 0.32, "life", 0.2, "cell", (double) SYLPH, "frames", 4.0, "fps", 12.0, "loop", true, "phase",
                        tt * 0.6, "alpha", 0.95, "fade", fade));
                if (tt % 6 == 0) {
                    RuneKit.rfx2(level, "prop", V.add(at, new Vec3(Kit.rand() - 0.5, -0.1, Kit.rand() - 0.5), 0.4), Opt.of("dir", V.DOWN, "speed", 0.8, "size", 0.12,
                            "life", 0.8, "spin", Kit.rand() * 360, "spinRate", 200.0, "fade", 0.5), LEAF, core, FEATHER, null);
                }
            });
        }
        return true;
    }

    /**
     * 화살 하나: delay 틱 뒤 활 (짝수) 또는 정령 (홀수) 에서 쏨 → flight 틱 동안 휘어 날며 (2차 베지에 — 끝은 그 적의 지금 몸) 빛 꼬리 →
     * arrive 틱에 꽂힘 (mult). 그 적이 쓰러졌으면 마지막으로 본 자리까지 날아가 흩어짐 (피해 없음)
     */
    private static void arrow(ServerPlayer sp, Weapon w, Obj k, BooleanSupplier ok, LivingEntity e, int i, int delay, int flight, long arrive, double[] glow,
            double[] core) {
        ServerLevel level = sp.level();
        boolean bySylph = i % 2 == 1;
        int sgn = (i / 2) % 2 == 0 ? 1 : -1;
        double curve = k.num("curve");
        HeroKit.at(delay, () -> {
            if (!ok.getAsBoolean()) return;
            Vec3 from = bySylph ? sylphAt(sp) : bowAt(sp);
            Vec3 f = Kit.facingOf(sp);
            Vec3 side = V.scale(V.rightOf(f), (bySylph ? -1 : 1) * sgn * curve);
            Vec3[] last = {HeroKit.body(e, from)};
            Vec3[] prev = {from};
            SkillFx.play(level, "random.bow", from, bySylph ? 1.7 : 1.25, bySylph ? 0.6 : 0.9);
            SkillFx.fx(level, "rpg:arrow_spark", from, Opt.of("dir", f, "size", 0.2, "grow", 2.5, "life", 0.15, "color", glow, "alpha", 1.0));
            Kit.ticks(t -> {
                if (!ok.getAsBoolean()) return false;
                if (!Kit.fallen(e)) last[0] = Kit.bodyPoint(e);
                double u = Math.min(1, (t + 1) / (double) flight);
                Vec3 to = last[0];
                Vec3 ctrl = V.add(V.up(V.lerp(from, to, 0.5), curve * 0.5), side);
                Vec3 p = Odin.bezAt(from, ctrl, to, u);
                Vec3 tan = V.unit(Odin.bezTan(from, ctrl, to, u));
                // 화살 (빛 + 심지 — 날아가는 쪽으로 누움) + 휘는 빛 꼬리 + 나뭇잎 (두 틱마다)
                RuneKit.rfx2(level, "arrow", p, Opt.of("dir", tan, "size", 0.72, "life", 0.1, "fade", 0.3), glow, core, new int[] {0, 1}, null);
                Gram.streakLine(level, prev[0], p, glow, 0.24, 0.36, 0.85, 0);
                Gram.streakLine(level, prev[0], p, core, 0.08, 0.28, 1.0, 0);
                if (t % 2 == 1) {
                    RuneKit.rfx2(level, "prop", p, Opt.of("dir", V.DOWN, "speed", 0.6, "size", 0.1, "life", 0.6, "spin", Kit.rand() * 360, "spinRate", 260.0,
                            "fade", 0.5), LEAF, core, FEATHER, null);
                }
                prev[0] = p;
                return u < 1;
            });
        });
        Kit.hitAt(sp, e, arrive, ok, () -> {
            if (Kit.skillHit(sp, e, w, k.num("mult")) <= 0) return;
            Vec3 eye = sp.getEyePosition();
            Vec3 c = HeroKit.body(e, eye);
            double near = HeroKit.near(eye, c, 0.5);
            SkillFx.burst(level, c, glow, 0.7 * near, 0.22);
            SkillFx.sparks(level, c, core, 5, 3.5);
            for (int j = 0; j < 4; j++) {
                double a = j * Math.PI / 2 + Kit.rand();
                RuneKit.rfx2(level, "prop", c, Opt.of("dir", V.unit(new Vec3(Math.cos(a), 0.6, Math.sin(a))), "speed", 2.2, "size", 0.13, "life", 0.5, "spin",
                        Kit.rand() * 360, "spinRate", 300.0, "fade", 0.6), LEAF, core, FEATHER, null);
            }
            SkillFx.play(level, "random.bowhit", c, 1.3, 0.8);
        });
    }
}
