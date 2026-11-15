package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.Notify;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneProps;
import mmorpg.fabric.rune.RuneSwords;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.BlockFx;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-15 아스널 킹 (궁수 2차 각성 — 사용자 "2차부터 길가매쉬 같은 느낌으로", claude/archer-remake.md): 금빛 문 ({@link BlockFx#gate}) 이 허공에 열리고 보물
 * (금빛 투영 칼 — 붉은 홈) 이 칼끝부터 나와 날아감. 골든 볼리 · 킹스 체인 · 트레저 레인 · 킹스 저지먼트 · 아스널 게이트. 원작의 이름 · 주문 · 고유 무기 디자인은 쓰지 않음.
 * 같은 적을 여러 번 치는 것은 10틱 넘게 띄우고, 빠른 연타는 피해 한 번 · 숫자 여러 줄. 화면 흔들림 없음.
 * <p>
 * 2026-11-15 직업 전용 2D 그림 (사용자 "직업마다 전용으로 그릴래" — tools/jobvfx/arsenal.py · 그림판 ak_*): 문 = 허공의 금빛 물결 (예전 3D 금 원판 대신) · 빛 폭발 ·
 * 금빛 충격파 · 달아오른 금 · 보석 반짝임 · 금가루, 보물 · 사슬은 3D 그대로 + 금빛 발광 (빛 번짐 후처리).
 */
final class ArsenalKingSkills {
    private ArsenalKingSkills() {}

    static void register() {
        JobSystem.register("arsenalking_volley", ArsenalKingSkills::volley);
        JobSystem.register("arsenalking_chain", ArsenalKingSkills::chain);
        JobSystem.register("arsenalking_rain", ArsenalKingSkills::rain);
        JobSystem.register("arsenalking_judge", ArsenalKingSkills::judge);
        JobSystem.register("arsenalking_ult", ArsenalKingSkills::ult);
    }

    private static Vec3 ground(ServerLevel level, Vec3 p) {
        return new Vec3(p.x, RuneKit.groundAt(level, p, p.y), p.z);
    }

    /** 등 뒤 문 자리 (i 번째 / n — 어깨 뒤 부채꼴: 가운데가 arch 만큼 높음) */
    static Vec3 backGate(ServerPlayer sp, int i, int n, double spread, double back, double h0, double arch) {
        Vec3 f = Kit.facingOf(sp);
        Vec3 right = V.rightOf(f);
        double u = n <= 1 ? 0 : (i / (double) (n - 1)) * 2 - 1;
        return V.add(V.up(V.add(sp.position(), f, -back), h0 + arch * (1 - u * u)), right, u * spread);
    }

    /** 2026-11-15 보물 차례 (장검 · 창 · 도끼를 돌아가며) */
    private static int treasureN;

    /**
     * 보물 하나: 문 g 에서 dir 쪽으로 칼끝부터 나옴 (appear 틱에 3틱 동안 커지며 앞으로) — 2026-11-15 HD 보물 셋 (금빛 장검 · 창 · 도끼 — 사파이어 · 에메랄드 · 루비 ·
     * 자수정, 돌아가며) (fx 수명 life 틱)
     */
    static RuneSwords.Blade treasure(ServerLevel level, Vec3 g, Vec3 dir, double len, int appear, int life) {
        Vec3 d = V.unit(dir);
        Vec3 face = PhantomArcher.faceOf(d);
        PhantomArcher.Pal p = PhantomArcher.GOLD_PAL;
        Vfx.Fx fx = RuneProps.fx(level, g, Math.max(appear + 8, life));
        RuneSwords.Blade b = RuneSwords.phantom(fx, PhantomArcher.treasure(treasureN++), V.add(g, d, -len * 0.6), d, face, len, 0.05, p.blade(), p.gem());
        b.prop.outline(PhantomFx.GOLD.glow()); // 2026-11-15 직업 전용: 보물이 금빛으로 번짐
        fx.on(Math.max(1, appear), () -> b.grow(1, 3).pose(V.add(g, d, -len * 0.2), d, face, 3));
        return b;
    }

    /** 보물을 쏨 (지금 자루 → to · speed 칸/초): 금빛 빛 줄 · 문 앞 번쩍 별. 날아가는 틱 */
    static int fire(ServerLevel level, RuneSwords.Blade b, Vec3 to, double speed, double[] glow) {
        Vec3 from = b.hilt();
        Vec3 d = V.unit(V.sub(to, from));
        int t = Math.max(1, ArrowFx.flyTicks(from, to, speed));
        b.pose(V.add(to, d, -b.reach() * 0.75), d, PhantomArcher.faceOf(d), t);
        PhantomArcher.streak2d(level, V.add(from, d, 0.6), to, Math.max(0.2, t / 20.0 + 0.15), glow, null);
        return t;
    }

    // ------------------------------------------------------------ 골든 볼리

    /**
     * 골든 볼리 (arsenalking_volley): 겨눈 적 (range 칸 — 없으면 쓰지 않음) — 어깨 뒤에 금빛 문 swords 개가 부채꼴로 열리고 보물이 칼끝부터 나옴 → lead 틱부터 every 틱마다
     * 하나씩 날아가 꽂힘 (꽂힘 +1씩, 마지막 보물이 big 배). 피해는 첫 보물이 닿을 때 한 번 (swords 합 — 숫자 줄 여럿)
     */
    static boolean volley(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        LivingEntity target = Kit.pickTarget(sp, k.num("range", 24), 10);
        if (target == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다", 30);
            return false;
        }
        List<Double> sw = SwordmasterSkills.numbers(k, "swords", List.of(1.75, 1.75, 1.75, 1.75));
        int n = sw.size();
        int every = Math.max(1, (int) k.num("every", 3));
        int lead = Math.max(3, (int) k.num("lead", 6));
        double speed = k.num("speed", 70);
        double[] wts = new double[n];
        double total = 0;
        for (int i = 0; i < n; i++) {
            wts[i] = sw.get(i);
            total += sw.get(i);
        }
        double mult = total;
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        PhantomFx.Lib L = PhantomFx.GOLD;
        SkillFx.play(level0, "block.amethyst_block.resonate", sp.position(), 1.4, 0.8);
        SkillFx.play(level0, "block.beacon.activate", sp.position(), 1.8, 0.5);
        for (int i = 0; i < n; i++) {
            int ii = i;
            double len = k.num("len", 1.9) * (i == n - 1 ? k.num("big", 1.4) : 1.0);
            int fireAt = lead + i * every;
            Vec3 g = backGate(sp, i, n, 1.8, 0.9, 1.9, 0.7);
            Vec3 aim = V.sub(Kit.bodyPoint(target), g);
            PhantomFx.gate(level0, L, g, aim, 0.7 + (i == n - 1 ? 0.2 : 0), 3, fireAt + 2); // 2026-11-15 직업 전용: 금빛 물결 문 (2D)
            RuneSwords.Blade b = treasure(level0, g, aim, len, 2 + i, fireAt + 30);
            // 쏘기 전까지 문 안에서 적을 겨눔
            for (int t = 5 + i; t < fireAt; t += 2) {
                RuneKit.later(t, () -> {
                    if (!RuneKit.sameLife(sp, life) || target.isRemoved()) return;
                    Vec3 d = V.unit(V.sub(Kit.bodyPoint(target), g));
                    b.pose(V.add(g, d, -len * 0.2), d, PhantomArcher.faceOf(d), 2);
                });
            }
            RuneKit.later(fireAt, () -> {
                if (!RuneKit.sameLife(sp, life) || target.isRemoved() || !target.isAlive()) {
                    b.remove();
                    return;
                }
                ServerLevel level = sp.level();
                Vec3 to = V.up(Kit.bodyPoint(target), (ii - (n - 1) / 2.0) * 0.15);
                int t = fire(level, b, to, speed, glow);
                PhantomFx.flash(level, L, g, 1.1 + (ii == n - 1 ? 0.5 : 0));
                SkillFx.play(level, "item.trident.throw", g, 1.1 + ii * 0.1, 0.8);
                if (ii == 0) JobKit.strikeSplit(sp, target, j, P, mult, n, every, wts, false, false, life, t, dd -> {});
                RuneKit.later(t, () -> {
                    b.remove();
                    if (target.isRemoved() || !(target.level() instanceof ServerLevel lv)) return;
                    Vec3 at = Kit.bodyPoint(target);
                    PhantomArcher.lodge(sp, j, target, 1, g);
                    JobFx.hit(target, j, ii == n - 1 ? 1.1 : 0.8);
                    PhantomFx.impact(lv, L, at, ii == n - 1 ? 1.2 : 0.75); // 2026-11-15 직업 전용: 빛살 별 · 금가루 · 금 조각
                    if (ii == n - 1) SkillFx.play(lv, "item.trident.hit", at, 0.8, 1.0);
                });
            });
        }
        return true;
    }

    // ------------------------------------------------------------ 킹스 체인

    /**
     * 킹스 체인 (arsenalking_chain): 발밑에 금빛 문이 열려 몸을 뒤로 leap 칸 날려 보냄 (순간 이동 몫) · 겨눈 곳 (range 칸의 적 — 없으면 앞 fallback 칸) 둘레에 문 셋이
     * 열려 금빛 사슬이 뻗음 → bindAt 틱에 둘레 radius 칸의 적을 휘감음 (bind · root 틱 묶음 — 보스는 둔화 · 꽂힘 +1) → pullAt 틱에 사슬이 조이며 다시 (pull). 묶였거나 타고
     * 있으면 쓰지 않음
     */
    static boolean chain(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        LivingEntity t0 = Kit.pickTarget(sp, k.num("range", 12), 20);
        Vec3[] spot = {t0 != null ? Kit.bodyPoint(t0) : V.up(ground(level0, V.add(c0, f, k.num("fallback", 8))), 1.0)};
        int bindAt = Math.max(4, (int) k.num("bindAt", 6));
        int pullAt = Math.max(bindAt + 10, (int) k.num("pullAt", 18));
        double radius = k.num("radius", 2);
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        PhantomFx.Lib L = PhantomFx.GOLD;
        // 발밑 문 → 뒤로 날아감 (2026-11-15 직업 전용: 금빛 물결 문 2D)
        PhantomFx.gate(level0, L, V.up(c0, 0.08), V.UP, 1.2, 2, 3);
        JobMove.run(sp, JobMove.hop(0, 8, JobMove.ahead(sp, -k.num("leap", 5), 0), k.num("lift", 1.0)));
        SkillFx.play(level0, "block.chain.place", c0, 0.8, 1.0);
        SkillFx.play(level0, "block.amethyst_block.resonate", c0, 1.5, 0.7);
        // 겨눈 곳 둘레 문 셋 · 사슬
        Vec3 sp0 = spot[0];
        double yaw = Math.atan2(f.z, f.x);
        for (int s = 0; s < 3; s++) {
            double a = yaw + Math.PI + (s - 1) * 2.0; // 쓴 사람 쪽 · 양옆 (≈115° 씩)
            Vec3 g = V.add(V.up(sp0, 0.5 + (s == 1 ? 0.8 : 0)), new Vec3(Math.cos(a), 0, Math.sin(a)), 2.8);
            Vec3 d = V.sub(sp0, g);
            int ss = s;
            RuneKit.later(1 + s, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                PhantomFx.gate(level, L, g, d, 0.6, 2, pullAt - 1);
                BlockFx.chain(level, V.add(g, V.unit(d), 0.15), sp0, 0.34, glow, Math.max(2, bindAt - 2 - ss), pullAt - bindAt + 3, L.glow());
                SkillFx.play(level, "block.chain.hit", g, 0.9 + ss * 0.1, 0.9);
            });
        }
        Set<UUID> bound = new HashSet<>();
        RuneKit.later(bindAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            if (t0 != null && !t0.isRemoved() && t0.isAlive()) spot[0] = Kit.bodyPoint(t0);
            Vec3 c = spot[0];
            PhantomFx.flash(level, L, c, 2.2);
            PhantomFx.shock(level, L, PhantomFx.ground(level, c), radius * 2.8, 1);
            SkillFx.play(level, "block.chain.break", c, 0.7, 1.0);
            for (LivingEntity e : Kit.around(sp, c, radius, 2.0)) {
                bound.add(e.getUUID());
                boolean boss = mmorpg.fabric.boss.Bosses.isBossEntity(e);
                RuneKit.effect(e, "slowness", (int) k.num("root", 20), boss ? 1 : 10);
                JobKit.strike(sp, e, j, P, k.num("bind", 2.8), false, false, life, 0, d -> {
                    PhantomArcher.lodge(sp, j, e, 1, c);
                    JobFx.hit(e, j, 0.9);
                });
            }
        });
        RuneKit.later(pullAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 c = spot[0];
            // 2026-11-15 직업 전용: 사슬이 조이며 작은 빛 폭발 (예전 X 베기 · 상자 터짐 대신)
            PhantomFx.boomSmall(level, L, c, 2.6);
            PhantomFx.flash(level, L, c, 2.0);
            SkillFx.play(level, "block.chain.break", c, 1.2, 1.0);
            SkillFx.play(level, "entity.player.attack.sweep", c, 0.8, 1.0);
            for (LivingEntity e : Kit.around(sp, c, radius + 0.5, 2.0)) {
                if (!bound.contains(e.getUUID())) continue;
                JobKit.strike(sp, e, j, P, k.num("pull", 1.4), false, false, life, 0, d -> JobFx.hit(e, j, 0.8));
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 트레저 레인

    /**
     * 트레저 레인 (arsenalking_rain): 조준한 곳 (range 칸) 위 하늘에 금빛 문 gates 개가 둥글게 열려 가운데를 내려다봄 → first 틱부터 gap 틱마다 (waves 번) 문마다 보물이
     * 쏟아져 땅에 꽂힘 (반지름 radius 칸 · 안의 적마다 wave — 숫자 두 줄 · 꽂힘 +1, 처음 맞은 적은 root 틱 묶임 · 보스는 둔화) → finalAt 틱에 가운데 큰 문에서 거대한
     * 보물이 내리꽂혀 터짐 (final · 꽂힌 보물도 부서짐)
     */
    static boolean rain(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 center = ground(level0, Kit.aimPoint(sp, k.num("range", 24)));
        double r = k.num("radius", 4.5);
        int waves = (int) k.num("waves", 5);
        int gap = Math.max(10, (int) k.num("gap", 10));
        int first = Math.max(6, (int) k.num("first", 6));
        int finalAt = Math.max(first + (waves - 1) * gap + 10, (int) k.num("finalAt", 56));
        int gates = Math.max(4, (int) k.num("gates", 10));
        double sky = k.num("sky", 6.5);
        double len = k.num("len", 1.7);
        double speed = k.num("speed", 60);
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        Set<UUID> rooted = new HashSet<>();
        List<RuneSwords.Blade> stuck = new ArrayList<>();
        PhantomFx.Lib L = PhantomFx.GOLD;
        // 하늘 문 2026-11-15 HD: 과녁 자리 앞쪽 (쓴 사람 쪽) 하늘에 활처럼 늘어서 과녁 쪽 (앞 아래) 을 봄 — 쓴 사람 뒤 카메라에는 위를 보는 문의 뒷면이 보임 (마크 면
        // 밝기: 아래를 보는 판은 0.4 라 검은 흙빛이었음)
        Vec3 fw = V.flat(V.sub(center, sp.position()));
        fw = V.len(fw) < 1e-3 ? Kit.facingOf(sp) : V.unit(fw);
        Vec3 rt = V.rightOf(fw);
        Vec3[] gp = new Vec3[gates];
        for (int g = 0; g < gates; g++) {
            double u = gates <= 1 ? 0 : g / (double) (gates - 1) * 2 - 1;
            double back = r * (0.55 + 0.35 * u * u) + Kit.rand() * 0.6;
            gp[g] = V.add(V.add(V.up(center, sky + (1 - u * u) * 1.4 + Kit.rand() * 0.8), fw, -back), rt, u * r * 1.15);
            PhantomFx.gate(level0, L, gp[g], V.sub(V.up(center, 0.5), gp[g]), 0.75, 3 + g / 3, finalAt - 3 - g / 3 + 2);
        }
        // 2026-11-15 직업 전용: 땅 금빛 고리 (금 징 · 보석) · 금 마법진 — 거대한 보물이 내리꽂히기 전에 닫힘 (큰 폭발 그림과 겹치면 그리는 차례에 따라 마법진이 네모난
        // 자리만큼 가려졌음 — 팬텀 아처 CI 43675143452)
        PhantomFx.ring(level0, L, center, r, 5, finalAt - 9);
        BlockFx.sigil(level0, V.up(center, 0.04), r * 1.05, PhantomArcher.SIGIL_GOLD, 6, finalAt - 12, 70);
        SkillFx.play(level0, "block.beacon.activate", center, 1.6, 1.0);
        SkillFx.play(level0, "block.amethyst_block.resonate", center, 1.2, 1.0);
        // 물결: 문마다 보물이 나와 (hit − 6) 날아가 (hit − t) 땅에 꽂힘 · hit 틱에 피해
        for (int w = 0; w < waves; w++) {
            int ww = w;
            int hitAt = first + w * gap;
            for (int g = 0; g < gates; g++) {
                if ((g + w) % 2 == 1 && gates > 6) continue; // 물결마다 문 절반씩 번갈아
                int gg = g;
                Vec3 land = V.add(center, new Vec3(Kit.rand() - 0.5, 0, Kit.rand() - 0.5), r * 1.4);
                Vec3 to = ground(level0, land);
                Vec3 d0 = V.sub(to, gp[gg]);
                int t = Math.max(1, ArrowFx.flyTicks(gp[gg], to, speed));
                int appear = Math.max(1, hitAt - t - 4);
                RuneKit.later(appear, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    ServerLevel level = sp.level();
                    RuneSwords.Blade b = treasure(level, gp[gg], d0, len, 1, finalAt - appear + 16);
                    RuneKit.later(hitAt - t - appear, () -> {
                        if (!RuneKit.sameLife(sp, life)) {
                            b.remove();
                            return;
                        }
                        Vec3 d = V.unit(V.sub(to, b.hilt()));
                        b.pose(V.add(V.up(to, 0.15), d, -b.reach() * 0.62), d, PhantomArcher.faceOf(d), t);
                        if (RuneKit.fxQuota("treasureRainStreak", 8)) PhantomArcher.streak2d(level, V.add(b.hilt(), d, 0.5), to, Math.max(0.2, t / 20.0 + 0.1), glow, null);
                        RuneKit.later(t, () -> {
                            stuck.add(b);
                            if (RuneKit.fxQuota("treasureRainLand", 8)) {
                                // 2026-11-15 직업 전용: 꽂힌 자리 금가루 · 빛살 별
                                PhantomFx.dust(sp.level(), L, PhantomFx.ground(sp.level(), to), 1.4);
                                PhantomFx.flash(sp.level(), L, V.up(to, 0.3), 0.9);
                            }
                        });
                    });
                });
            }
            RuneKit.later(hitAt, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                SkillFx.play(level, "item.trident.hit", center, 0.9 + ww * 0.08, 1.0);
                for (LivingEntity e : Kit.around(sp, center, r, 4)) {
                    boolean pin = rooted.add(e.getUUID());
                    JobKit.strikeSplit(sp, e, j, P, k.num("wave", 2.0), 2, 2, false, false, life, 0, d -> {
                        PhantomArcher.lodge(sp, j, e, 1, V.up(center, sky));
                        if (RuneKit.fxQuota("treasureRainHit", 8)) JobFx.hit(e, j, 0.8);
                        if (pin && k.num("root", 0) > 0) {
                            boolean boss = mmorpg.fabric.boss.Bosses.isBossEntity(e);
                            RuneKit.effect(e, "slowness", (int) k.num("root", 20), boss ? 1 : 10);
                        }
                    });
                }
            });
        }
        // 가운데 큰 문 · 거대한 보물 (2026-11-15 HD: 과녁 자리 앞쪽 위에서 비스듬히 내려다봄)
        int bigAt = finalAt - 16;
        Vec3 bigGate = V.add(V.up(center, sky + 2.6), fw, -r * 0.7);
        Vec3 bigDir = V.unit(V.sub(center, bigGate));
        RuneSwords.Blade[] giant = {null};
        RuneKit.later(bigAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            PhantomFx.gate(level, L, bigGate, bigDir, 2.2, 5, 18);
            giant[0] = treasure(level, bigGate, bigDir, k.num("bigLen", 4.6), 4, 40);
            SkillFx.play(level, "block.beacon.power_select", center, 0.7, 1.4);
        });
        RuneKit.later(finalAt - 3, () -> {
            if (giant[0] == null) return;
            RuneSwords.Blade b = giant[0];
            b.pose(V.add(V.up(center, 0.3), bigDir, -b.reach() * 0.7), bigDir, PhantomArcher.faceOf(bigDir), 3);
            PhantomArcher.streak2d(sp.level(), bigGate, center, 0.3, glow, null);
        });
        RuneKit.later(finalAt, () -> {
            ServerLevel level = sp.level();
            // 2026-11-15 직업 전용: 금빛 빛 폭발 (빛살 · 불덩이 · 금가루 · 충격파 · 달아오른 금 — 예전 상자 폭발 · 상자 고리 대신)
            PhantomFx.boom(level, L, V.up(center, 0.1), r * 0.95);
            SkillFx.play(level, "entity.generic.explode", center, 0.9, 1.2);
            for (RuneSwords.Blade b : stuck) {
                if (RuneKit.fxQuota("treasureRainBurst", 10)) PhantomFx.flash(level, L, V.lerp(b.hilt(), b.tip(), 0.7), 1.4);
                PhantomArcher.shatter(level, b, 1.0, L);
            }
            stuck.clear();
            if (giant[0] != null) RuneKit.later(10, () -> PhantomArcher.shatter(sp.level(), giant[0], 2.0, L));
            if (!RuneKit.sameLife(sp, life)) return;
            for (LivingEntity e : Kit.around(sp, center, r, 4)) JobKit.strike(sp, e, j, P, k.num("final", 2.6), false, true, life, 0, d -> JobFx.hit(e, j, 1.2));
        });
        RuneKit.later(finalAt + 14, () -> {
            for (RuneSwords.Blade b : stuck) b.remove();
        });
        return true;
    }

    // ------------------------------------------------------------ 킹스 저지먼트

    /**
     * 킹스 저지먼트 (arsenalking_judge): 겨눈 적 (range 칸 · 시선 — 없으면 조준한 땅) 위 하늘 (sky 칸) 에 큰 금빛 문이 열리고 거대한 보물 (len 칸) 이 천천히 내려옴 →
     * hitAt 틱에 내리꽂혀 그 적에게 hit (꽂힘 +1) → blastAt 틱에 거대하게 터짐 (둘레 radius 칸 blast — 그 적들에게 꽂혀 있던 보물도 함께 터짐: 하나당 + perLodged)
     */
    static boolean judge(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        double range = k.num("range", 32);
        Vec3 eye = sp.getEyePosition();
        Vec3 f = sp.getViewVector(1F);
        List<Kit.LineHit> hits = Kit.lineHits(sp, eye, f, range, k.num("width", 1.2));
        LivingEntity hitE = hits.isEmpty() ? null : hits.get(0).e();
        Vec3[] spot = {hitE != null ? Kit.bodyPoint(hitE) : V.up(ground(level0, Kit.aimPoint(sp, range)), 1.0)};
        int hitAt = Math.max(10, (int) k.num("hitAt", 17));
        int blastAt = Math.max(hitAt + 10, (int) k.num("blastAt", 27));
        double sky = k.num("sky", 7.5);
        double len = k.num("len", 5.5);
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        PhantomFx.Lib L = PhantomFx.GOLD;
        // 2026-11-15 HD: 큰 문은 과녁 앞쪽 (쓴 사람 쪽) 위에서 비스듬히 과녁을 내려다봄 (아래만 보는 판은 마크 면 밝기 0.4 로 어두웠음) · 땅에 금빛 마법진
        Vec3 fw = V.flat(V.sub(spot[0], sp.position()));
        fw = V.len(fw) < 1e-3 ? Kit.facingOf(sp) : V.unit(fw);
        Vec3 top = V.add(V.up(spot[0], sky), fw, -sky * 0.45);
        Vec3 gdir = V.unit(V.sub(spot[0], top));
        PhantomFx.gate(level0, L, top, gdir, 2.6, 6, hitAt);
        // 2026-11-15 직업 전용: 땅 금빛 고리 · 금 마법진은 터지기 전에 닫힘 (큰 폭발 그림과 겹치지 않게)
        PhantomFx.ring(level0, L, PhantomFx.ground(level0, spot[0]), k.num("radius", 4) * 0.9, 6, blastAt - 9);
        BlockFx.sigil(level0, V.up(ground(level0, spot[0]), 0.04), k.num("radius", 4) * 0.9, PhantomArcher.SIGIL_GOLD, 6, blastAt - 13, 80);
        SkillFx.play(level0, "block.beacon.activate", sp.position(), 1.2, 1.0);
        SkillFx.play(level0, "block.amethyst_block.resonate", spot[0], 0.7, 1.4);
        RuneSwords.Blade b = treasure(level0, top, gdir, len, 5, blastAt + 30);
        // 천천히 내려옴 (나옴 → hitAt − 3)
        RuneKit.later(9, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            if (hitE != null && !hitE.isRemoved() && hitE.isAlive()) spot[0] = Kit.bodyPoint(hitE);
            b.pose(V.add(top, gdir, len * 0.45), gdir, PhantomArcher.faceOf(gdir), hitAt - 12);
        });
        for (int t = 9; t < hitAt - 3; t += 3) {
            int tt = t;
            RuneKit.later(t, () -> PhantomFx.flash(sp.level(), L, b.tip(), 0.7 + tt * 0.05));
        }
        RuneKit.later(hitAt - 3, () -> {
            if (!RuneKit.sameLife(sp, life)) {
                b.remove();
                return;
            }
            if (hitE != null && !hitE.isRemoved() && hitE.isAlive()) spot[0] = Kit.bodyPoint(hitE);
            Vec3 to = spot[0];
            Vec3 d = V.unit(V.sub(to, b.hilt()));
            b.pose(V.add(to, d, -b.reach() * 0.7), d, PhantomArcher.faceOf(d), 3);
            PhantomArcher.streak2d(sp.level(), b.hilt(), to, 0.35, glow, null);
            SkillFx.play(sp.level(), "item.trident.riptide_3", to, 1.0, 1.0);
        });
        RuneKit.later(hitAt, () -> {
            ServerLevel lv = sp.level();
            Vec3 to = spot[0];
            // 2026-11-15 직업 전용: 빛살 별 · 금빛 충격파 · 꽂힌 자리 금가루 · 반짝임 (1인칭 영상: 별 2.6 은 화면을 거의 덮었음 → 2.2)
            PhantomFx.flash(lv, L, to, 2.2);
            PhantomFx.shock(lv, L, PhantomFx.ground(lv, to), 6.0, 1);
            PhantomFx.dust(lv, L, PhantomFx.ground(lv, to), 2.0);
            PhantomFx.embers(lv, L, to, 12, 1.8, 0.42, 16);
            SkillFx.play(lv, "item.trident.hit", to, 0.6, 1.0);
            if (!RuneKit.sameLife(sp, life)) return;
            if (hitE != null) JobKit.strike(sp, hitE, j, P, k.num("hit", 5.6), false, false, life, 0, dd -> {
                PhantomArcher.lodge(sp, j, hitE, 1, top);
                JobFx.hit(hitE, j, 1.1);
            });
            else for (LivingEntity e : Kit.around(sp, to, 1.5, 2.0)) JobKit.strike(sp, e, j, P, k.num("hit", 5.6), false, false, life, 0, dd -> JobFx.hit(e, j, 1.1));
        });
        // 2026-11-15 직업 전용: 꽂힌 거대한 보물에 빛이 모이며 부풂 (모으는 빛 1.2 → 3.6 — 터질 때 지움)
        Vfx.Sprite[] swell = {null};
        RuneKit.later(hitAt + 1, () -> {
            Vec3 m = V.lerp(b.hilt(), b.tip(), 0.62);
            Vfx.Fx sf = Vfx.at(sp.level(), m, blastAt - hitAt + 4);
            swell[0] = PhantomFx.charge(sf, L, m, 1.2);
            swell[0].to(null, 3.6, Math.max(1, blastAt - hitAt - 2));
        });
        RuneKit.later(blastAt, () -> {
            ServerLevel l2 = sp.level();
            Vec3 c = hitE != null && !hitE.isRemoved() ? Kit.bodyPoint(hitE) : spot[0];
            if (swell[0] != null) swell[0].remove();
            PhantomArcher.shatter(l2, b, 2.5, L);
            double rad = k.num("radius", 4);
            // 2026-11-15 직업 전용: 금빛 빛 폭발 (예전 상자 폭발 · 상자 고리 대신)
            PhantomFx.boom(l2, L, V.up(c, 0.1), rad);
            Ult.flash(l2, c, PhantomArcher.GOLD_PAL.fire(), Opt.of("radius", 6.0, "time", new double[] {0.02, 0.04, 0.25}), sp);
            SkillFx.play(l2, "entity.generic.explode", c, 0.8, 1.2);
            if (!RuneKit.sameLife(sp, life)) return;
            for (LivingEntity e : Kit.around(sp, c, rad, 3)) {
                int n = PhantomArcher.detonate(sp, e);
                double m = k.num("blast", 11.2) * (1 + k.num("perLodged", 0.1) * n);
                JobKit.strike(sp, e, j, P, m, false, true, life, 0, dd -> JobFx.hit(e, j, 1.3));
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 아스널 게이트

    /**
     * 아스널 게이트 (arsenalking_ult): 등 뒤 하늘에 금빛 문 gates 개가 두 줄로 차례로 열림 (발밑 금빛 물결) → first 틱부터 every 틱마다 (waves 번) 문마다 보물이 둘레
     * radius 칸의 적들에게 쏟아짐 (적마다 wave — 숫자 두 줄 · 꽂힘 +1) → finalAt − 16 에 겨눈 곳 (range 칸) 위에 가장 큰 문이 열려 거대한 보물이 나옴 → finalAt 에
     * 꿰뚫어 터짐 (둘레 finalRadius 칸 final · 큰 숫자)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 f0 = Kit.facingOf(sp);
        double R = k.num("radius", 12);
        int waves = (int) k.num("waves", 6);
        int every = Math.max(10, (int) k.num("every", 10));
        int first = Math.max(10, (int) k.num("first", 24));
        int finalAt = Math.max(first + (waves - 1) * every + 12, (int) k.num("finalAt", 86));
        int gates = Math.max(6, (int) k.num("gates", 20));
        double speed = k.num("speed", 70);
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        PhantomFx.Lib L = PhantomFx.GOLD;
        Ult.sky(level0, c0, "rpg:ult_sunset", finalAt + 30);
        Ult.flash(level0, c0, glow, Opt.of("radius", 10.0, "time", new double[] {0.03, 0.05, 0.35}), sp);
        SkillFx.play(level0, "block.beacon.activate", c0, 0.8, 1.4);
        SkillFx.play(level0, "block.end_portal.spawn", c0, 1.6, 0.5);
        // 2026-11-15 직업 전용: 발밑 금빛 물결 문 · 퍼지는 금빛 충격파 (예전 3D 원판 · 상자 고리 대신)
        PhantomFx.gate(level0, L, V.up(c0, 0.08), V.UP, 2.4, 4, 10);
        PhantomFx.shock(level0, L, PhantomFx.ground(level0, c0), R * 2.15, 1);
        // 등 뒤 문 두 줄 (아래 줄 넓게 · 위 줄 높게) — 쓴 자리 기준으로 고정
        Vec3 right = V.rightOf(f0);
        Vec3[] gp = new Vec3[gates];
        int lower = gates * 3 / 5;
        for (int g = 0; g < gates; g++) {
            boolean low = g < lower;
            int idx = low ? g : g - lower;
            int cnt = low ? lower : gates - lower;
            double u = cnt <= 1 ? 0 : idx / (double) (cnt - 1) * 2 - 1;
            double ang = u * (low ? 1.25 : 1.0);
            Vec3 out = V.add(V.scale(f0, -Math.cos(ang)), right, Math.sin(ang));
            // 2026-11-15 게임 영상: 문이 작고 몸에 붙어 보였음 → 더 크게 · 더 멀리
            gp[g] = V.add(V.up(c0, (low ? 3.2 : 6.0) + Kit.rand() * 0.6 + (1 - u * u) * 0.9), out, low ? 4.4 : 3.6);
            Vec3 face = V.add(V.scale(f0, 1), V.UP, -0.4); // 2026-11-15 HD: 앞 아래를 봄 — 뒤 카메라에는 위를 보는 밝은 뒷면
            Vec3 pos = gp[g];
            int open = 2 + g;
            RuneKit.later(open, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                PhantomFx.gate(sp.level(), L, pos, face, low ? 1.1 : 0.9, 3, finalAt - open);
            });
        }
        // 물결: 문마다 보물이 나와 적에게 날아감 (hitAt 에 닿음)
        for (int w = 0; w < waves; w++) {
            int ww = w;
            int hitAt = first + w * every;
            RuneKit.later(hitAt - 6, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                List<LivingEntity> in = Kit.around(sp, c0, R, 5);
                int launch = in.isEmpty() ? 4 : Math.max(5, Math.min(10, in.size() * 3));
                for (int q = 0; q < launch; q++) {
                    LivingEntity e = in.isEmpty() ? null : in.get(q % in.size());
                    Vec3 g = gp[(ww * 7 + q * 3) % gates];
                    Vec3 to = e != null ? V.up(Kit.bodyPoint(e), (Kit.rand() - 0.5) * 0.5) : V.up(ground(level, V.add(c0, V.add(f0, new Vec3(Kit.rand() - 0.5, 0, Kit.rand() - 0.5), 0.8), 4 + Kit.rand() * (R - 5))), 0.6);
                    RuneSwords.Blade b = treasure(level, g, V.sub(to, g), 1.6 + Kit.rand() * 0.6, 1, 24);
                    RuneKit.later(4, () -> {
                        Vec3 dst = e != null && !e.isRemoved() ? V.up(Kit.bodyPoint(e), (Kit.rand() - 0.5) * 0.5) : to;
                        Vec3 d = V.unit(V.sub(dst, b.hilt()));
                        b.pose(V.add(dst, d, -b.reach() * 0.75), d, PhantomArcher.faceOf(d), 2);
                        if (RuneKit.fxQuota("arsenalStreak", 12)) PhantomArcher.streak2d(sp.level(), V.add(b.hilt(), d, 0.5), dst, 0.25, glow, null);
                        RuneKit.later(2, () -> {
                            b.remove();
                            if (e == null && RuneKit.fxQuota("arsenalMiss", 6)) PhantomFx.flash(sp.level(), L, dst, 1.0);
                        });
                    });
                }
                SkillFx.play(level, "item.trident.throw", c0, 0.75 + ww * 0.06, 1.0);
            });
            RuneKit.later(hitAt, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                SkillFx.play(level, "item.trident.hit", c0, 0.9 + ww * 0.06, 0.9);
                for (LivingEntity e : Kit.around(sp, c0, R, 5)) {
                    JobKit.strikeSplit(sp, e, j, P, k.num("wave", 7), 2, 2, false, false, life, 0, d -> {
                        PhantomArcher.lodge(sp, j, e, 1, V.up(c0, 3.0));
                        if (RuneKit.fxQuota("arsenalHit", 8)) JobFx.hit(e, j, 1.0);
                    });
                }
            });
        }
        // 마지막: 가장 큰 문 · 거대한 보물
        Vec3[] spot = {null};
        Vec3[] top0 = {null};
        Vec3[] gdir0 = {V.DOWN};
        RuneSwords.Blade[] giant = {null};
        RuneKit.later(finalAt - 16, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            LivingEntity t = Kit.pickTarget(sp, k.num("range", 24), 20);
            spot[0] = t != null ? ground(level, t.position()) : ground(level, Kit.aimPoint(sp, k.num("range", 24)));
            // 2026-11-15 HD: 가장 큰 문은 겨눈 곳 앞쪽 위에서 비스듬히 (아래만 보는 판은 어두웠음) · 하늘 띠 고리 대신 땅에 금빛 마법진
            Vec3 fw = V.flat(V.sub(spot[0], sp.position()));
            fw = V.len(fw) < 1e-3 ? f0 : V.unit(fw);
            top0[0] = V.add(V.up(spot[0], 9.0), fw, -4.2);
            gdir0[0] = V.unit(V.sub(spot[0], top0[0]));
            PhantomFx.gate(level, L, top0[0], gdir0[0], 3.4, 6, 16);
            // 2026-11-15 직업 전용: 땅 금빛 고리 · 금 마법진은 거대한 보물이 꽂히기 전에 닫힘 (큰 폭발 그림과 겹치지 않게)
            PhantomFx.ring(level, L, spot[0], k.num("finalRadius", 4) * 1.1, 5, 8);
            BlockFx.sigil(level, V.up(spot[0], 0.04), k.num("finalRadius", 4) * 1.1, PhantomArcher.SIGIL_GOLD, 6, 4, 90);
            giant[0] = treasure(level, top0[0], gdir0[0], k.num("giantLen", 7.0), 5, 40);
            SkillFx.play(level, "block.beacon.power_select", spot[0], 0.6, 1.4);
            SkillFx.play(level, "block.amethyst_block.resonate", spot[0], 0.6, 1.2);
        });
        RuneKit.later(finalAt - 3, () -> {
            if (spot[0] == null || giant[0] == null) return;
            RuneSwords.Blade b = giant[0];
            Vec3 gd = gdir0[0];
            b.pose(V.add(V.up(spot[0], 0.2), gd, -b.reach() * 0.75), gd, PhantomArcher.faceOf(gd), 3);
            PhantomArcher.streak2d(sp.level(), top0[0] != null ? top0[0] : V.up(spot[0], 9.0), spot[0], 0.35, glow, null);
            SkillFx.play(sp.level(), "item.trident.riptide_3", spot[0], 0.9, 1.2);
        });
        RuneKit.later(finalAt, () -> {
            if (spot[0] == null) return;
            ServerLevel level = sp.level();
            double fr = k.num("finalRadius", 4);
            // 2026-11-15 직업 전용: 금빛 대폭발 (예전 상자 폭발 · 상자 고리 대신)
            PhantomFx.boom(level, L, V.up(spot[0], 0.1), fr * 1.5);
            Ult.flash(level, spot[0], PhantomArcher.GOLD_PAL.fire(), Opt.of("radius", 8.0, "time", new double[] {0.02, 0.05, 0.3}), sp);
            SkillFx.play(level, "entity.generic.explode", spot[0], 0.7, 1.4);
            if (giant[0] != null) RuneKit.later(12, () -> PhantomArcher.shatter(sp.level(), giant[0], 2.5, L));
            if (!RuneKit.sameLife(sp, life)) return;
            for (LivingEntity e : Kit.around(sp, spot[0], fr, 4)) JobKit.strike(sp, e, j, P, k.num("final", 14), false, true, life, 0, d -> JobFx.hit(e, j, 1.4));
        });
        return true;
    }
}
