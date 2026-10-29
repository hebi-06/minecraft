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
 * 카두케우스 (영웅 성스러운 홀 — 12단계, 자바 전용): 쌍뱀의 축복. 수치는 HERO_SKILLS.scepter, 색은 금빛 뱀 (color) · 은빛 뱀 (silver).
 * 지팡이에서 날개가 번쩍이며 빛의 뱀 둘이 나와 앞 cone° · range 칸의 적 (조준한 적 먼저 — 둘이면 하나씩) 을 쫓아 hits 번 번갈아 묾 (gap 틱마다 — mult,
 * 마법 피해) → 다 물면 내게 돌아오며 준 피해의 heal 만큼 회복 (한 번에 최대 체력의 healCap 까지). 그림: rpg:rfx_serpent (꿈틀거리는 빛의 뱀).
 * 겨눌 적이 없으면 쓰지 않음 (재사용 대기 없음 — 정령의 연사와 같음)
 */
final class Caduceus {
    private Caduceus() {}

    /** 입체 뱀 크기 (모델 길이 1.4 × — 약 2칸) */
    static final double SNAKE = 1.45;

    static void register() {
        Transcend.register("caduceus_serpents", Caduceus::serpents);
    }

    /** 빛의 뱀 (빛 + 심지, 꿈틀거림): at 에서 dir 쪽으로 speed 칸/초 */
    static void serpent(ServerLevel level, Vec3 at, Vec3 dir, double size, double life, double speed, double[] glow, double[] core) {
        Opt o = Opt.of("dir", dir, "size", size, "life", life, "speed", speed, "frames", 4.0, "fps", 12.0, "loop", true, "phase", Kit.rand() * 4, "fade", 0.25,
                "alpha", 0.4); // 0.48.0: 입체 뱀 밑의 빛 무리로 옅게
        RuneKit.rfx(level, "serpent", at, o.with("color", glow, "cell", 0.0));
        RuneKit.rfx(level, "serpent", V.add(at, dir, -0.02), o.with("color", core, "cell", 4.0));
    }

    /**
     * 뱀이 지나는 길의 반짝임 (from → to, ticks 틱 동안 틱마다 — 옆으로 꿈틀거리는 작은 별빛 + 알갱이): 뱀 그림만으로는 멀리서 · 정면에서 잘 보이지 않음
     */
    static void trail(ServerLevel level, Vec3 from, Vec3 to, int ticks, double[] glow, double[] core) {
        Vec3 d = V.sub(to, from);
        Vec3 u = V.unit(d);
        Vec3 side = V.unit(V.cross(u, Math.abs(u.y) > 0.95 ? new Vec3(1, 0, 0) : V.UP));
        double phase = Kit.rand() * Math.PI * 2;
        Kit.ticks(t -> {
            if (t > ticks) return false;
            double a = Math.min(1, (t + 0.5) / Math.max(1, ticks));
            Vec3 p = V.add(V.add(from, d, a), side, Math.sin(a * Math.PI * 3 + phase) * 0.3);
            RuneKit.rfx2(level, "flare", p, Opt.of("size", 0.5, "life", 0.3, "fade", 0.6, "spin", Kit.rand() * 90), glow, core, new int[] {0, 1}, null);
            SkillFx.sparks(level, p, glow, 2, 1);
            return true;
        });
    }

    static boolean serpents(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        List<LivingEntity> targets = HeroKit.targetsIn(sp, k.num("range"), k.num("cone"), 2);
        if (targets.isEmpty()) {
            mmorpg.fabric.Notify.warn(sp, "§c" + k.str("name") + " §7— 겨눌 적이 없습니다");
            return false;
        }
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] gold = HeroKit.colors(k);
        double[][] silver = new double[][] {HeroKit.rgbOr(k, "silverGlow", new double[] {0.8, 0.86, 0.95}), new double[] {0.96, 0.98, 1}};
        Object sv = k.has("silver") ? k.get("silver") : null;
        if (sv instanceof List<?> l && l.size() == 2) silver = new double[][] {Kit.rgb(l.get(0)), Kit.rgb(l.get(1))};
        double[][][] cols = {gold, silver};
        Vec3 f = Kit.facingOf(sp);
        Vec3 staff = V.add(V.add(V.up(sp.getEyePosition(), 0.1), f, 0.9), V.rightOf(f), 0.35);
        // 지팡이의 날개 (rfx_wings) 와 금빛 · 은빛 번쩍임
        RuneKit.rfx2(level, "wings", staff, Opt.of("dir", V.unit(V.sub(sp.getEyePosition(), staff)), "size", 0.9, "life", 0.4, "grow", 0.3, "fade", 0.6, "alpha",
                0.45), gold[0], gold[1], new int[] {0, RuneKit.WINGS_CORE}, null);
        // 0.48.0 입체: 홀 끝에서 금빛 날개가 활짝 펼쳐졌다가 접힘 (작게 — 1인칭 화면 오른쪽 위)
        int wings0 = WeaponFx3d.fx(level, "fire_wings", V.add(staff, f, 0.3), gold[0], gold[1], Opt.of("dir", V.UP, "face", V.scale(f, -1), "scale", 0.42, "anim",
                "spread", "life", 0.55, "glowAlpha", 0.55, "shine", 0.4, "fadeOut", 0.2));
        Tasks.later(6, () -> Fx3d.anim(level, wings0, "fold", false, staff));
        SkillFx.sparks(level, staff, gold[0], 5, 3);
        SkillFx.sparks(level, staff, silver[0], 5, 3);
        SkillFx.play(level, "mob.evocation_illager.cast_spell", staff, 1.4, 0.7);
        SkillFx.play(level, "chime.amethyst_block", staff, 1.6, 0.8);
        HeroKit.Heal heal = new HeroKit.Heal(sp, k.num("heal"), k.num("healCap"));
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        double speed = k.num("speed");
        int end = 2 * 3 + (hits - 1) * gap + 10;
        // 0.48.0 입체 뱀 둘 (금빛 · 은빛 — 비늘 몸 · 흰 눈 · 벌어지는 턱): 홀에서 날아가 묾 → 적 둘레로 물러나 쉭쉭거림 → 다시 덤벼 묾 → 끝에 돌아옴
        int[] snake = new int[2];
        int[] gen = new int[2]; // 뱀마다 지금 하는 일 (새로 덤비면 앞의 물러나기 · 쉭쉭거림은 그만둠)
        Kit.motionLater(sp, "caduceus_serpents:return", end, life); // 두 팔을 벌려 돌아오는 뱀을 맞이함
        for (int i = 0; i < hits; i++) {
            final int ii = i;
            final int s = i % 2; // 뱀 (0 금빛 · 1 은빛)
            final LivingEntity t = targets.get(Math.min(targets.size() - 1, s));
            // 처음 두 번은 지팡이에서 날아감, 그다음은 적 둘레를 돌다 다시 묾
            int fly = (int) Math.ceil(V.len(V.sub(Kit.bodyPoint(t), staff)) / speed);
            int at = ii < 2 ? ii * 3 : 2 * 3 + (ii - 1) * gap;
            Tasks.later(at, () -> {
                if (!ok.getAsBoolean() || !Kit.canHit(sp, t)) return;
                Vec3 from = ii < 2 ? staff : V.add(Kit.bodyPoint(t), V.turnY(f, (ii % 2 == 0 ? 120 : -120)), 1.6);
                Vec3 to = Kit.bodyPoint(t);
                Vec3 d = V.unit(V.sub(to, from));
                int ticks = ii < 2 ? fly : 4;
                serpent(level, from, d, ii < 2 ? 2.1 : 1.7, ticks / 20.0 + 0.1, V.len(V.sub(to, from)) / (ticks / 20.0) * 0.9, cols[s][0], cols[s][1]);
                // 입체 뱀: 머리 끝이 몸 겉에 닿을 자리 (가운데 − 머리 길이)
                Vec3 bite = V.add(to, d, -(0.45 * SNAKE + Math.min(0.35, Kit.bodyPad(t) * 0.5)));
                int g = ++gen[s];
                if (ii < 2) {
                    snake[s] = WeaponFx3d.fx(level, "serpent", from, cols[s][0], cols[s][1], Opt.of("to", bite, "travel", ticks / 20.0, "ease", "in", "dir", d, "align",
                            1.0, "scale", SNAKE, "life", (end - at + 14) / 20.0, "pop", 0.08, "glowAlpha", 0.55, "shine", 0.3, "fadeOut", 0.15));
                    WeaponFx3d.guard(sp, life, new int[] {snake[s]}, end - at + 14);
                } else {
                    Fx3d.move(level, snake[s], bite, ticks / 20.0, "in");
                }
                Tasks.later(ticks, () -> {
                    if (gen[s] == g) Fx3d.anim(level, snake[s], "bite", false, to);
                });
                // 문 뒤: 다음에 덤빌 자리 (적 둘레 ±120°) 로 물러나 그 적을 보며 쉭쉭거림
                int next = ii + 2;
                Tasks.later(ticks + 3, () -> {
                    if (!ok.getAsBoolean() || gen[s] != g) return;
                    Vec3 body = Kit.fallen(t) ? to : Kit.bodyPoint(t);
                    Vec3 rest = V.add(body, V.turnY(f, next % 2 == 0 ? 120 : -120), 1.6);
                    Fx3d.move(level, snake[s], rest, 0.3, "out");
                    Tasks.later(7, () -> {
                        if (gen[s] != g) return;
                        Fx3d.move(level, snake[s], V.add(rest, V.unit(V.sub(body, rest)), 0.08), 0.15, "inout");
                        Fx3d.anim(level, snake[s], "hiss", true, rest);
                    });
                });
                trail(level, from, to, ticks, cols[s][0], cols[s][1]);
                RuneKit.streak(level, from, to, cols[s][0], 0.12, 0.35, 0.7, 0.0);
                Tasks.later(ticks, () -> {
                    if (!ok.getAsBoolean()) return;
                    Kit.hitAt(sp, t, Kit.reserveHit(sp, t, Tasks.now()), ok, () -> {
                        double dealt = Kit.skillHit(sp, t, w, k.num("mult"));
                        if (dealt <= 0) return;
                        heal.add(dealt);
                        Vec3 c = HeroKit.body(t, to);
                        double near = HeroKit.near(sp.getEyePosition(), c, 0.5);
                        RuneKit.rfx2(level, "flare", c, Opt.of("size", 1.3 * near, "life", 0.28, "grow", 0.5, "fade", 0.6, "spin", Kit.rand() * 90), cols[s][0],
                                cols[s][1], new int[] {0, 1}, null);
                        // 무는 이빨 자국 (위아래 두 줄이 맞물림)
                        Vec3 vv = V.unit(V.sub(c, sp.getEyePosition()));
                        SkillFx.cutMark(level, V.add(c, vv, -0.35), vv, 20, 0.03, cols[s][0], cols[s][1], 0.9 * near);
                        SkillFx.cutMark(level, V.add(c, vv, -0.35), vv, -20, 0.03, cols[s][0], cols[s][1], 0.9 * near);
                        SkillFx.sparks(level, c, cols[s][0], 8, 5);
                        SkillFx.play(level, "mob.phantom.bite", c, 1.4, 0.7);
                    });
                });
            });
        }
        // 다 물면 돌아오며 회복 (마지막 문 틱 뒤 — 늦게 들어온 문 것도 HeroKit.Heal 이 이어서 회복)
        Tasks.later(end, () -> {
            if (!ok.getAsBoolean()) return;
            for (int s = 0; s < 2; s++) {
                LivingEntity t = targets.get(Math.min(targets.size() - 1, s));
                Vec3 from = t.isRemoved() ? V.add(sp.position(), f, 4) : Kit.bodyPoint(t);
                Vec3 to = V.up(sp.position(), 1.1);
                Vec3 d = V.unit(V.sub(to, from));
                serpent(level, from, d, 1.4, 0.35, V.len(V.sub(to, from)) / 0.35, cols[s][0], cols[s][1]);
                trail(level, from, to, 7, cols[s][0], cols[s][1]);
                // 입체 뱀이 내 가슴 앞으로 날아와 스며듦 (양옆 — 1인칭 눈 앞을 가리지 않게)
                Vec3 home = V.add(V.add(V.up(sp.position(), 1.0), Kit.facingOf(sp), 0.5), V.rightOf(Kit.facingOf(sp)), s == 0 ? 0.6 : -0.6);
                int id = snake[s];
                gen[s]++;
                Fx3d.move(level, id, home, 0.35, "inout");
                Fx3d.anim(level, id, "hiss", true, home);
                Tasks.later(7, () -> WeaponFx3d.kill(level, id, home, 0.15));
            }
            Tasks.later(7, () -> {
                if (!ok.getAsBoolean()) return;
                double healed = heal.open();
                RuneKit.selfCue(sp, gold[0], gold[1], true);
                Vec3 back = V.up(V.add(sp.position(), Kit.facingOf(sp), -0.35), 1.3);
                RuneKit.rfx2(level, "wings", back, Opt.of("dir", V.scale(Kit.facingOf(sp), -1), "size", 1.6, "life", 0.5, "grow", 0.2, "fade", 0.6, "alpha", 0.3),
                        gold[0], gold[1], new int[] {0, RuneKit.WINGS_CORE}, null);
                // 0.48.0 입체: 등에서 금빛 날개가 펼쳐져 한 번 날갯짓 (축복 — 쓴 사람의 1인칭에서는 숨김)
                int wings = WeaponFx3d.fx(level, "fire_wings", new Vec3(0, 1.25, -0.38), gold[0], gold[1], Opt.of("follow", (double) sp.getId(), "dir", V.UP, "face",
                        new Vec3(0, 0, -1), "scale", 1.25, "anim", "spread", "life", 0.9, "view", 1.0, "glowAlpha", 0.5, "shine", 0.35, "fadeOut", 0.3));
                Tasks.later(9, () -> Fx3d.anim(level, wings, "flap", false, back));
                if (healed > 0) SkillFx.play(level, "beacon.power", sp.position(), 1.7, 0.5);
            });
        });
        return true;
    }
}
