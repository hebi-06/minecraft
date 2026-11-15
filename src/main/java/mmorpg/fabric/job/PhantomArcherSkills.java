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
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-15 팬텀 아처 직업 스킬 (궁수 리메이크 — 사용자 "아처 1차는 … 에미야 시로 같은 느낌으로", claude/archer-remake.md): 소드 볼트 · 크로스 팽 · 블레이드
 * 레인 · 팬텀 버스트 · 소드 생추어리. 칼은 모두 투영 (빛의 선 → 3D 칼 — {@link PhantomArcher#project}) 이고 맞은 적에게 꽂힘 (특성 환영 무기). 같은 적을 여러 번
 * 치는 것은 10틱 넘게 띄우고, 빠른 연타는 피해 한 번 · 숫자 여러 줄 (JobKit.strikeSplit). 화면 흔들림 없음.
 */
final class PhantomArcherSkills {
    private PhantomArcherSkills() {}

    static void register() {
        JobSystem.register("archer_volley", PhantomArcherSkills::volley);
        JobSystem.register("archer_cross", PhantomArcherSkills::cross);
        JobSystem.register("archer_blades", PhantomArcherSkills::blades);
        JobSystem.register("archer_burst", PhantomArcherSkills::burst);
        JobSystem.register("archer_ult", PhantomArcherSkills::ult);
    }

    private static Vec3 bez(Vec3 a, Vec3 c, Vec3 b, double u) {
        double v = 1 - u;
        return new Vec3(a.x * v * v + c.x * 2 * u * v + b.x * u * u, a.y * v * v + c.y * 2 * u * v + b.y * u * u, a.z * v * v + c.z * 2 * u * v + b.z * u * u);
    }

    private static Vec3 ground(ServerLevel level, Vec3 p) {
        return new Vec3(p.x, RuneKit.groundAt(level, p, p.y), p.z);
    }

    // ------------------------------------------------------------ 소드 볼트

    /**
     * 소드 볼트 (archer_volley): 겨눈 적 (range 칸 — 없으면 쓰지 않음) — 활 위에 칼 swords 자루가 부채꼴로 투영됨 (빛의 선 → 3D 칼, 마지막 칼이 big 배 크게) → lead
     * 틱부터 every 틱마다 한 자루씩 날아가 꽂힘 (꽂힘 +1씩). 피해는 첫 칼이 닿을 때 한 번 (swords 합 — 숫자 자루 수 줄, 줄마다 그 칼의 몫)
     */
    static boolean volley(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        LivingEntity target = Kit.pickTarget(sp, k.num("range", 24), 10);
        if (target == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다", 30);
            return false;
        }
        List<Double> sw = SwordmasterSkills.numbers(k, "swords", List.of(2.0, 2.0, 3.0));
        int n = sw.size();
        int every = Math.max(1, (int) k.num("every", 3));
        int lead = Math.max(2, (int) k.num("lead", 3));
        double speed = k.num("speed", 60);
        double mult = 0;
        double[] wts = new double[n];
        for (int i = 0; i < n; i++) {
            mult += sw.get(i);
            wts[i] = sw.get(i);
        }
        double total = mult;
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        PhantomFx.Lib L = PhantomFx.PHANTOM; // 2026-11-15 직업 전용 이펙트 (사용자 "직업마다 전용으로 그릴래")
        // 동작: 쏘며 뒤로 짧게 뜀
        JobMove.run(sp, JobMove.hop(0, lead + (n - 1) * every + 3, JobMove.ahead(sp, -k.num("back", 1.0), 0), 0.4));
        SkillFx.play(level0, "block.amethyst_block.resonate", sp.position(), 1.7, 0.7);
        RuneSwords.Blade[] blades = new RuneSwords.Blade[n];
        for (int i = 0; i < n; i++) {
            int ii = i;
            double len = k.num("len", 1.3) * (i == n - 1 ? k.num("big", 1.6) : 1.0);
            int fireAt = lead + i * every;
            Vec3 h0 = hover(sp, i, n);
            // 2026-11-15 직업 전용: 투영 선 · 칼 발광 · 나타날 때 작은 섬광 (1인칭 눈앞 — 칼 길이보다 작게)
            blades[i] = PhantomArcher.project(level0, h0, V.sub(Kit.bodyPoint(target), h0), len, 1 + i, fireAt + 40, true, L, 0.6 + len * 0.2);
            // 쏘기 전까지 활 위를 따라다니며 적을 겨눔
            for (int t = 1; t < fireAt; t++) {
                RuneKit.later(t, () -> {
                    if (!RuneKit.sameLife(sp, life) || target.isRemoved()) return;
                    Vec3 h = hover(sp, ii, n);
                    Vec3 d = V.unit(V.sub(Kit.bodyPoint(target), h));
                    blades[ii].pose(h, d, PhantomArcher.faceOf(d), 1);
                });
            }
            RuneKit.later(fireAt, () -> {
                RuneSwords.Blade b = blades[ii];
                if (!RuneKit.sameLife(sp, life) || target.isRemoved() || !target.isAlive()) {
                    b.remove();
                    return;
                }
                ServerLevel level = sp.level();
                Vec3 to = V.up(Kit.bodyPoint(target), (ii - (n - 1) / 2.0) * 0.18);
                Vec3 from = b.hilt();
                Vec3 d = V.unit(V.sub(to, from));
                int t = Math.max(1, ArrowFx.flyTicks(from, to, speed));
                b.pose(V.add(to, d, -b.reach() * 0.75), d, PhantomArcher.faceOf(d), t);
                // 2026-11-15 쏘는 번쩍임 (1인칭 영상: 자루 자리 · 0.9 ~ 1.4 는 눈앞에서 화면을 덮었음 → 0.8칸 앞 · 0.7 ~ 1.0)
                PhantomFx.flash(level, L, V.add(from, d, 0.8), 0.7 + ii * 0.2);
                // 2026-11-15 1인칭 미리보기: 빛 줄이 자루 (눈앞) 에서 시작하면 가까운 끝이 화면을 크게 덮음 → 0.8칸 앞에서
                PhantomArcher.streak2d(level, V.add(from, d, 0.8), to, Math.max(0.2, t / 20.0 + 0.15), glow, core);
                SkillFx.play(level, "item.trident.throw", from, 1.25 + ii * 0.1, 0.8);
                if (ii == 0) JobKit.strikeSplit(sp, target, j, P, total, n, every, wts, false, false, life, t, dd -> {});
                RuneKit.later(t, () -> {
                    b.remove();
                    if (target.isRemoved() || !(target.level() instanceof ServerLevel lv)) return;
                    Vec3 at = Kit.bodyPoint(target);
                    PhantomArcher.lodge(sp, j, target, 1, from);
                    JobFx.hit(target, j, ii == n - 1 ? 1.1 : 0.8);
                    PhantomFx.impact(lv, L, at, ii == n - 1 ? 1.2 : 0.75);
                    if (ii == n - 1) SkillFx.play(lv, "item.trident.hit", at, 0.8, 1.0);
                });
            });
        }
        return true;
    }

    /** 소드 볼트의 i 번째 칼이 떠 있는 자리 (활 위 — 부채꼴) */
    private static Vec3 hover(ServerPlayer sp, int i, int n) {
        Vec3 f = sp.getViewVector(1F);
        Vec3 right = V.rightOf(V.flat(f));
        Vec3 hand = RuneKit.bowHand(sp);
        double u = i - (n - 1) / 2.0;
        // 2026-11-15 1인칭 영상: 활 손 뒤 0.2칸은 눈에 너무 가까워 칼자루가 화면 위를 덮었음 → 활 앞 0.7칸 · 조금 더 위
        return V.add(V.up(V.add(hand, f, 0.7), 0.6 + 0.12 * Math.abs(u) + (i == n - 1 ? 0.15 : 0)), right, u * 0.55);
    }

    // ------------------------------------------------------------ 크로스 팽

    /**
     * 크로스 팽 (archer_cross): 뒤로 leap 칸 공중제비로 물러나며 투영한 쌍검을 양옆으로 던짐 → 바깥으로 휘어 (curve 칸) 날아가 겨눈 곳 (range 칸의 적 — 없으면 앞
     * fallback 칸) 에서 crossAt 틱에 X 자로 교차하며 벰 (cross · 둘레 radius 칸 · 꽂힘 +1) → 돌아오며 backAt 틱에 지나는 길 (폭 width) 을 다시 벰 (back) → 손에 돌아와
     * 사라짐. 묶였거나 타고 있으면 쓰지 않음
     */
    static boolean cross(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        LivingEntity t0 = Kit.pickTarget(sp, k.num("range", 12), 20);
        Vec3[] spot = {t0 != null ? Kit.bodyPoint(t0) : V.up(ground(level0, V.add(c0, f, k.num("fallback", 8))), 1.0)};
        int crossAt = Math.max(4, (int) k.num("crossAt", 8));
        int backAt = Math.max(crossAt + 10, (int) k.num("backAt", 18));
        int home = backAt + 4;
        double curve = k.num("curve", 3.5);
        double len = k.num("len", 1.3);
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        PhantomFx.Lib L = PhantomFx.PHANTOM;
        // 몸: 뒤로 공중제비 (순간 이동 몫)
        JobMove.run(sp, JobMove.hop(0, 8, JobMove.ahead(sp, -k.num("leap", 5), 0), k.num("lift", 1.3)));
        SkillFx.play(level0, "entity.player.attack.sweep", c0, 1.4, 0.8);
        SkillFx.play(level0, "block.amethyst_block.resonate", c0, 1.8, 0.6);
        Vfx.Fx fx = RuneProps.fx(level0, c0, home + 8);
        Vec3 right = V.rightOf(f);
        RuneSwords.Blade[] bl = new RuneSwords.Blade[2];
        Vec3[] start = new Vec3[2];
        Vec3[] prev = new Vec3[2]; // 2026-11-15 컨셉아트: 휘어 나는 쌍검 뒤의 이어진 빛 띠 (틱마다 앞 자리 → 지금 자리)
        for (int s = 0; s < 2; s++) {
            double side = s == 0 ? -1 : 1;
            start[s] = V.add(V.up(c0, 1.15), right, side * 0.45);
            PhantomArcher.trace2d(level0, start[s], f, len, 0.3, L.trace()); // 2026-11-15 투영 선 발광
            bl[s] = RuneSwords.phantom(fx, start[s], f, V.UP, len, 0.05, PhantomArcher.BLADE, PhantomArcher.GEM);
            bl[s].prop.outline(L.glow());
            int ss = s;
            fx.on(1, () -> bl[ss].grow(1, 2));
        }
        // 날아감 (2 → crossAt): 바깥으로 휘는 두 길 · 부메랑처럼 돎
        for (int t = 2; t <= crossAt; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                if (t0 != null && !t0.isRemoved() && t0.isAlive()) spot[0] = Kit.bodyPoint(t0);
                double u = (tt - 2) / (double) Math.max(1, crossAt - 2);
                for (int s = 0; s < 2; s++) {
                    double side = s == 0 ? -1 : 1;
                    Vec3 mid = V.lerp(start[s], spot[0], 0.5);
                    Vec3 ctl = V.add(V.up(mid, 0.6), V.rightOf(V.flat(V.sub(spot[0], start[s]))), side * curve);
                    Vec3 p = bez(start[s], ctl, spot[0], u);
                    Vec3 d = RuneSwords.rot(f, V.UP, side * tt * 62);
                    bl[s].pose(V.add(p, d, -len * 0.45), d, V.UP, 1);
                    if (prev[s] != null) PhantomArcher.streak2d(sp.level(), prev[s], p, 0.3, glow, core);
                    prev[s] = p;
                }
            });
        }
        // 교차 (crossAt): X 자 섬광 · 둘레 적 베기 · 꽂힘
        RuneKit.later(crossAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 c = spot[0];
            Vec3 r = V.rightOf(V.flat(V.sub(c, sp.position())));
            Vec3 a = V.unit(V.add(r, V.UP, 1));
            Vec3 b = V.unit(V.add(r, V.UP, -1));
            // 2026-11-15 직업 전용: 미리 렌더링한 X 베기 (두 줄기가 차례로 그어짐 · 교차점 섬광 — 2 × 2) · 섬광 · 불씨 (예전 2D 참격 판 둘 · 상자 터짐 · 별 대신)
            Vec3 toMe = V.unit(V.sub(sp.getEyePosition(), c));
            PhantomFx.xslash(level, L, V.add(c, toMe, 0.45), 3.8, Kit.rand() * 16 - 8);
            PhantomFx.flash(level, L, c, 2.4);
            PhantomFx.embers(level, L, c, 12, 1.8, 0.42, 16);
            SkillFx.play(level, "entity.player.attack.sweep", c, 0.9, 1.0);
            SkillFx.play(level, "item.trident.hit", c, 1.1, 0.8);
            for (LivingEntity e : Kit.around(sp, c, k.num("radius", 2), 2.0)) {
                JobKit.strike(sp, e, j, P, k.num("cross", 2.8), false, false, life, 0, d -> {
                    PhantomArcher.lodge(sp, j, e, 1, c);
                    JobFx.hit(e, j, 0.9);
                });
            }
        });
        // 돌아옴 (crossAt + 1 → home): 쓴 사람 쪽으로 · backAt 에 지나는 길을 벰
        for (int t = crossAt + 1; t <= home; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double u = (tt - crossAt) / (double) (home - crossAt);
                Vec3 hand = V.up(sp.position(), 1.15);
                for (int s = 0; s < 2; s++) {
                    double side = s == 0 ? -1 : 1;
                    Vec3 mid = V.lerp(spot[0], hand, 0.5);
                    Vec3 ctl = V.add(V.up(mid, 0.4), V.rightOf(V.flat(V.sub(hand, spot[0]))), -side * curve * 0.7);
                    Vec3 p = bez(spot[0], ctl, V.add(hand, V.rightOf(f), side * 0.45), u);
                    Vec3 d = RuneSwords.rot(f, V.UP, side * tt * 62);
                    bl[s].pose(V.add(p, d, -len * 0.45), d, V.UP, 1);
                    if (prev[s] != null) PhantomArcher.streak2d(sp.level(), prev[s], p, 0.25, glow, core);
                    prev[s] = p;
                }
            });
        }
        RuneKit.later(backAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 hand = V.up(sp.position(), 1.0);
            Vec3 dv = V.sub(hand, spot[0]);
            double dl = V.len(dv);
            if (dl < 0.5) return;
            for (Kit.LineHit h : Kit.lineHits(sp, spot[0], V.scale(dv, 1 / dl), dl, k.num("width", 1.5))) {
                LivingEntity e = h.e();
                JobKit.strike(sp, e, j, P, k.num("back", 1.4), false, false, life, 0, d -> JobFx.hit(e, j, 0.7));
            }
        });
        RuneKit.later(home, () -> {
            for (RuneSwords.Blade b : bl) {
                PhantomFx.flash(sp.level(), L, b.hilt(), 0.7);
                b.remove();
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 블레이드 레인

    /**
     * 블레이드 레인 (archer_blades): 조준한 곳 (range 칸) 하늘 (sky 칸 위) 에 칼이 투영됨 → first 틱부터 gap 틱마다 (waves 번) 칼 물결이 쏟아져 땅에 꽂힘 (반지름
     * radius 칸 · 안의 적마다 wave — 숫자 두 줄 · 꽂힘 +1, 처음 맞은 적은 root 틱 묶임 · 보스는 둔화) → burstAt 틱에 꽂힌 칼이 한꺼번에 터짐 (burst)
     */
    static boolean blades(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 center = ground(level0, Kit.aimPoint(sp, k.num("range", 24)));
        double r = k.num("radius", 4.5);
        int waves = (int) k.num("waves", 5);
        int gap = Math.max(10, (int) k.num("gap", 10));
        int first = Math.max(4, (int) k.num("first", 6));
        int burstAt = Math.max(first + (waves - 1) * gap + 10, (int) k.num("burstAt", 56));
        int per = Math.max(2, (int) k.num("per", 6));
        double sky = k.num("sky", 7.0);
        double len = k.num("len", 1.6);
        int fall = 4;
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        PhantomFx.Lib L = PhantomFx.PHANTOM;
        Set<UUID> rooted = new HashSet<>();
        List<RuneSwords.Blade> stuck = new ArrayList<>();
        Vfx.Fx fx = RuneProps.fx(level0, center, burstAt + 20);
        // 동작: 제자리에서 뛰어올라 하늘로 쏨 — 칼 셋이 하늘로 솟음
        JobMove.run(sp, JobMove.hop(0, 10, from -> from, 0.8));
        for (int i = 0; i < 3; i++) {
            Vec3 from = RuneKit.bowHand(sp);
            Vec3 up = V.add(V.up(from, 8 + i), V.flat(sp.getViewVector(1F)), 2 + i * 0.6);
            RuneSwords.Blade b = PhantomArcher.project(level0, from, V.sub(up, from), 1.2, 1, 12, false, L, 0.8);
            b.pose(V.add(up, V.unit(V.sub(up, from)), -1.0), V.sub(up, from), V.UP, 6);
            RuneKit.later(7, () -> {
                PhantomFx.flash(level0, L, up, 1.6);
                b.remove();
            });
            PhantomArcher.streak2d(level0, V.add(from, V.unit(V.sub(up, from)), 0.8), up, 0.35, glow, null); // 2026-11-15 직업 전용: 2D 잔상 줄기
        }
        SkillFx.play(level0, "item.trident.riptide_1", sp.position(), 1.4, 0.8);
        SkillFx.play(level0, "block.amethyst_block.resonate", center, 1.2, 1.0);
        // 땅: 과녁 문양 · 하늘: 투영 고리 (칼이 나오는 곳)
        // 2026-11-15 영상: 오각별 마법진 (SkillFx.rune) 대신 땅에 눕는 칼날 고리 (rfx_jbladering — 칼 컨셉과 맞춤)
        // 2026-11-15 사용자 "남은 2D 고리 블록으로 바꿔": 땅 예고 고리 · 퍼지는 고리 · 하늘 고리 = 단색 블록 (BlockFx)
        // 2026-11-15 직업 전용: 땅 고리 (2 × 2 그림 — 예고 · 테두리) · 퍼지는 충격파
        PhantomFx.ring(level0, L, center, r, 5, burstAt);
        PhantomFx.shock(level0, L, center, r * 2.2, 1);
        // 2026-11-15 HD: 하늘 띠 고리 (장난감처럼 보였음) 대신 땅에 진홍 마법진 (육망성 · 칼 문장 · 금빛 룬 — 위를 보는 판은 마크 면 밝기 1)
        mmorpg.fabric.vfx.BlockFx.sigil(level0, V.up(center, 0.045), r * 1.02, PhantomArcher.SIGIL_CRIMSON, 5, burstAt - 2, 60);
        for (int w = 0; w < waves; w++) {
            int ww = w;
            int hitAt = first + w * gap;
            int spawnAt = Math.max(0, hitAt - fall - 3);
            RuneKit.later(spawnAt, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                List<Vec3> spots = new ArrayList<>();
                for (LivingEntity e : Kit.around(sp, center, r, 4)) if (spots.size() < per / 2) spots.add(e.position());
                while (spots.size() < per) {
                    double a = Kit.rand() * Math.PI * 2;
                    spots.add(V.add(center, new Vec3(Math.cos(a), 0, Math.sin(a)), r * Math.sqrt(Kit.rand())));
                }
                for (Vec3 s0 : spots) {
                    Vec3 g = ground(level, s0);
                    Vec3 tilt = V.unit(V.add(V.DOWN, new Vec3(Kit.rand() - 0.5, 0, Kit.rand() - 0.5), 0.35));
                    Vec3 top = V.add(V.up(g, sky), new Vec3(Kit.rand() - 0.5, 0, Kit.rand() - 0.5), 1.2);
                    RuneSwords.Blade b = RuneSwords.phantom(fx, top, tilt, PhantomArcher.faceOf(tilt), len, 0.05, PhantomArcher.BLADE, PhantomArcher.GEM);
                    b.prop.outline(L.glow());
                    b.grow(1, 2);
                    if (RuneKit.fxQuota("bladeRainTrace", 8)) PhantomFx.flash(level, L, V.add(top, tilt, len * 0.5), 1.0);
                    stuck.add(b);
                    RuneKit.later(3, () -> b.pose(V.add(g, tilt, -len * 0.62), tilt, PhantomArcher.faceOf(tilt), fall));
                    RuneKit.later(3 + fall, () -> {
                        if (RuneKit.fxQuota("bladeRainLand", 8)) {
                            // 2026-11-15 직업 전용: 꽂힌 자리 흙먼지 (밑의 진홍 번쩍) · 작은 섬광
                            PhantomFx.dust(level, L, g, 1.5);
                            PhantomFx.flash(level, L, V.up(g, 0.3), 0.9);
                        }
                    });
                }
                SkillFx.play(level, "item.trident.throw", V.up(center, sky), 0.8 + ww * 0.08, 0.9);
            });
            RuneKit.later(hitAt, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                SkillFx.play(level, "item.trident.hit_ground", center, 1.0 + ww * 0.06, 1.0);
                for (LivingEntity e : Kit.around(sp, center, r, 4)) {
                    boolean pin = rooted.add(e.getUUID());
                    JobKit.strikeSplit(sp, e, j, P, k.num("wave", 2.0), 2, 2, false, false, life, 0, d -> {
                        PhantomArcher.lodge(sp, j, e, 1, V.up(e.position(), sky));
                        if (RuneKit.fxQuota("bladeRainHit", 6)) JobFx.hit(e, j, 0.9);
                        if (pin && k.num("root", 0) > 0) {
                            boolean boss = mmorpg.fabric.boss.Bosses.isBossEntity(e);
                            RuneKit.effect(e, "slowness", (int) k.num("root", 20), boss ? 1 : 10);
                        }
                    });
                }
            });
        }
        // 꽂힌 칼이 한꺼번에 터짐
        RuneKit.later(burstAt, () -> {
            ServerLevel level = sp.level();
            for (RuneSwords.Blade b : stuck) {
                // 2026-11-15 칼마다 작은 불 터짐 (예전 blast 는 칼마다 상자 120개 남짓 — 열넷이면 너무 많음)
                if (RuneKit.fxQuota("bladeRainBurst", 10)) PhantomFx.flash(level, L, b.tip(), 1.5);
                PhantomArcher.shatter(level, b, 1.0, L);
            }
            stuck.clear();
            if (!RuneKit.sameLife(sp, life)) return;
            // 2026-11-15 직업 전용: 큰 폭발 (불덩이 · 땅 충격파 · 그을린 땅 · 불씨 · 칼 조각 — 예전 상자 폭발 · 상자 고리 대신)
            PhantomFx.boom(level, L, V.up(center, 0.1), r * 0.75);
            SkillFx.play(level, "entity.generic.explode", center, 0.9, 1.1);
            for (LivingEntity e : Kit.around(sp, center, r, 4)) JobKit.strike(sp, e, j, P, k.num("burst", 2.6), false, false, life, 0, d -> JobFx.hit(e, j, 1.0));
        });
        return true;
    }

    // ------------------------------------------------------------ 팬텀 버스트

    /**
     * 팬텀 버스트 (archer_burst): 활에 거대한 칼 (len 칸) 이 투영되어 draw 틱 동안 시위에 걸려 당겨짐 (느려짐) → 겨눈 쪽 range 칸을 날아가 처음 맞은 적 (폭 width) 에게
     * 꽂힘 (hit) → blastDelay 틱 뒤 거대하게 터짐 (둘레 radius 칸 blast — 그 적들에게 꽂혀 있던 칼도 함께 터짐: 하나당 + perLodged). 아무도 없으면 벽 · 끝에 꽂혀 터짐
     */
    static boolean burst(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        int draw = Math.max(6, (int) k.num("draw", 14));
        double len = k.num("len", 4.0);
        double speed = k.num("speed", 90);
        int blastDelay = Math.max(10, (int) k.num("blastDelay", 10));
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        RuneKit.effect(sp, "slowness", draw + 4, 2);
        Vec3 f0 = sp.getViewVector(1F);
        Vec3 h0 = V.add(V.add(RuneKit.bowHand(sp), V.rightOf(V.flat(f0)), 0.3), f0, 0.4);
        // 2026-11-15 직업 전용: 투영 선 발광 · 칼 발광 · 나타날 때 섬광 3.4 (project 가 함)
        RuneSwords.Blade b = PhantomArcher.project(level0, h0, f0, len, 6, draw + blastDelay + 60, true, PhantomFx.PHANTOM, 3.4);
        SkillFx.play(level0, "block.amethyst_block.resonate", sp.position(), 0.9, 1.0);
        SkillFx.play(level0, "block.beacon.power_select", sp.position(), 1.6, 0.6);
        // 2026-11-15 시범 HD (사용자 "너가 추천한거 다 넣어서 시범으로 만들어봐 나는 압도적인 퀄을 중요하게 여김" — PhantomFx): 칼 발광 (외곽선 → 리소스 팩 후처리가
        // 부드러운 빛 번짐 — 2판 자체 가우스 흐림) · 칼끝에 모으는 빛 (미리 렌더링한 2D 되풀이) · 나타날 때 섬광. 2판: 칼 둘레 2D 빛 무리 뺌 (밝은 3D 칼에 가려 안 보였음)
        Vfx.Fx chargeFx = Vfx.at(level0, h0, draw + 6);
        Vfx.Sprite[] charge = {null};
        RuneKit.later(6, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            PhantomFx.embers(lv, V.lerp(b.hilt(), b.tip(), 0.5), 8, 1.4, 0.32, 14);
            charge[0] = PhantomFx.charge(chargeFx, PhantomFx.PHANTOM, b.tip(), 1.0);
        });
        // 당김: 활을 따라 겨눔 · 조금씩 뒤로 당겨짐 · 칼끝에 빛이 모임 · 끝무렵 떨림
        for (int t = 1; t < draw; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 f = sp.getViewVector(1F);
                // 2026-11-15 1인칭 영상: 활 손 뒤 (−0.7 ~ −1.05칸) 는 칼자루 · 코등이가 화면 오른쪽을 크게 덮었음 → 활 앞 0.4 → 0.1칸 · 오른쪽 0.3칸
                double pull = 0.4 - 0.3 * tt / (double) draw;
                Vec3 h = V.add(V.add(RuneKit.bowHand(sp), V.rightOf(V.flat(f)), 0.3), f, pull);
                if (tt > draw - 5) h = V.add(h, new Vec3(Kit.rand() - 0.5, Kit.rand() - 0.5, Kit.rand() - 0.5), 0.04);
                b.pose(h, f, PhantomArcher.faceOf(f), 1);
                // 2026-11-15 시범: 모으는 빛이 칼끝을 따라감 (당길수록 커짐)
                double kk = tt / (double) draw;
                if (charge[0] != null) charge[0].to(V.add(b.tip(), f, 0.15), 1.0 + 1.3 * kk, 1);
            });
        }
        RuneKit.later(draw, () -> {
            if (!RuneKit.sameLife(sp, life)) {
                b.remove();
                return;
            }
            ServerLevel level = sp.level();
            Vec3 eye = sp.getEyePosition();
            Vec3 f = sp.getViewVector(1F);
            double range = k.num("range", 32);
            List<Kit.LineHit> hits = Kit.lineHits(sp, eye, f, range, k.num("width", 1.0));
            LivingEntity hitE = hits.isEmpty() ? null : hits.get(0).e();
            double dist = hitE != null ? hits.get(0).t() : Kit.rayDist(level, eye, f, range);
            Vec3 to = hitE != null ? Kit.bodyPoint(hitE) : V.add(eye, f, Math.max(2, dist));
            Vec3 from = b.hilt();
            Vec3 d = V.unit(V.sub(to, from));
            int t = Math.max(1, ArrowFx.flyTicks(from, to, speed));
            double reach = b.reach();
            b.pose(V.add(to, d, -reach * 0.7), d, PhantomArcher.faceOf(d), t);
            // 2026-11-15 시범: 시위를 떠날 때 섬광 (모으는 빛은 사라짐)
            PhantomFx.flash(level, V.add(from, d, reach * 0.9), 3.4);
            if (charge[0] != null) charge[0].remove();
            // 2026-11-15 1인칭 미리보기: 자루 (눈앞) 에서 시작한 굵은 빛 줄이 화면 아래를 덮었음 → 1.5칸 앞에서
            Vec3 s0 = V.add(from, d, 1.5);
            PhantomArcher.streak2d(level, s0, to, Math.max(0.25, t / 20.0 + 0.2), glow, core);
            PhantomArcher.streak2d(level, s0, to, Math.max(0.25, t / 20.0 + 0.3), glow, core);
            SkillFx.play(level, "item.crossbow.shoot", from, 0.6, 1.0);
            SkillFx.play(level, "item.trident.riptide_2", from, 1.2, 0.8);
            Vec3 off = hitE != null ? V.sub(V.add(to, d, -reach * 0.7), Kit.bodyPoint(hitE)) : null;
            RuneKit.later(t, () -> {
                ServerLevel lv = sp.level();
                // 2026-11-15 시범: 꽂힐 때 섬광 · 그을린 땅 (금이 달아올랐다 식음 — 2 × 2 조각) · 불씨
                PhantomFx.flash(lv, to, 4.4);
                PhantomFx.crack(lv, PhantomFx.PHANTOM, PhantomFx.ground(lv, to), 3.4, 0, 5);
                PhantomFx.embers(lv, to, 14, 2.2, 0.42, 18);
                SkillFx.play(lv, "item.trident.hit", to, 0.6, 1.0);
                if (hitE != null && RuneKit.sameLife(sp, life)) JobKit.strike(sp, hitE, j, P, k.num("hit", 5.6), false, false, life, 0, dd -> {
                    PhantomArcher.lodge(sp, j, hitE, 1, from);
                    JobFx.hit(hitE, j, 1.1);
                });
                // 꽂힌 채 빛이 부풂 (적을 따라감) — 2판: 꽂힌 자리에 모으는 빛이 커지며 터지기 직전을 알림 (1판 칼 둘레 빛 무리 대신)
                Vfx.Fx swellFx = Vfx.at(lv, to, blastDelay + 4);
                Vfx.Sprite swell = PhantomFx.charge(swellFx, PhantomFx.PHANTOM, V.lerp(b.hilt(), b.tip(), 0.62), 1.2);
                for (int q = 1; q < blastDelay; q++) {
                    int qq = q;
                    RuneKit.later(q, () -> {
                        if (hitE != null && !hitE.isRemoved()) b.pose(V.add(Kit.bodyPoint(hitE), off), d, PhantomArcher.faceOf(d), 1);
                        swell.to(V.lerp(b.hilt(), b.tip(), 0.62), 1.2 + 2.4 * qq / (double) blastDelay, 1);
                    });
                }
                RuneKit.later(blastDelay, () -> {
                    swell.remove();
                    ServerLevel l2 = sp.level();
                    Vec3 c = hitE != null && !hitE.isRemoved() ? Kit.bodyPoint(hitE) : to;
                    // 2026-11-15 2판: 3D 별 · 3D 조각 (1인칭에서 회색 막대) 대신 2D 칼 조각
                    b.remove();
                    double rad = k.num("radius", 4);
                    // 2026-11-15 시범 HD: 미리 렌더링한 진홍 대폭발 (2 × 2 조각 · 20칸 — 흰 섬광 → 불덩이 → 옅어지는 연기) · 땅 충격파 (속이 빈 고리 — 흰 심 · 푸른 흰 앞 날 ·
                    // 진홍 뒤 빛) · 그을린 땅 (오래 — 금이 식어 감) · 불씨 · 칼 조각 (예전 상자 폭발 · 상자 고리 대신). 2판: 폭발 2.1 → 1.9 (1인칭에서 화면을 덮었음)
                    PhantomFx.boom(l2, PhantomFx.PHANTOM, c, rad);
                    Ult.flash(l2, c, PhantomArcher.FIRE, Opt.of("radius", 6.0, "time", new double[] {0.02, 0.04, 0.25}), sp);
                    SkillFx.play(l2, "entity.generic.explode", c, 0.8, 1.2);
                    if (!RuneKit.sameLife(sp, life)) return;
                    for (LivingEntity e : Kit.around(sp, c, rad, 3)) {
                        int n = PhantomArcher.detonate(sp, e);
                        double m = k.num("blast", 11.2) * (1 + k.num("perLodged", 0.1) * n);
                        JobKit.strike(sp, e, j, P, m, false, true, life, 0, dd -> JobFx.hit(e, j, 1.3));
                    }
                });
            });
        });
        return true;
    }

    // ------------------------------------------------------------ 소드 생추어리

    /** 땅에 꽂힌 칼 하나 (소드 생추어리) */
    private static final class Planted {
        final RuneSwords.Blade b;
        final Vec3 ground;
        boolean used;

        Planted(RuneSwords.Blade b, Vec3 ground) {
            this.b = b;
            this.ground = ground;
        }
    }

    /**
     * 소드 생추어리 (archer_ult): 붉은 노을 (rpg:ult_sunset) — 발밑에서 불꽃 고리가 ringTicks 틱 동안 radius 칸까지 번지고 고리 안 땅에 칼 swords 자루가 솟아 꽂힘 →
     * first 틱부터 every 틱마다 (waves 번) 칼이 뽑혀 고리 안 적들에게 날아가 꽂힘 (적마다 wave — 숫자 두 줄 · 꽂힘 +1, 뽑힌 자리에는 새 칼이 솟음) → finalAt − 14 에
     * 남은 칼이 겨눈 곳 (range 칸) 하늘로 솟았다가 finalAt 에 한꺼번에 내리꽂힘 (둘레 finalRadius 칸 final · 큰 숫자)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        double R = k.num("radius", 12);
        int ringTicks = Math.max(4, (int) k.num("ringTicks", 12));
        int nSw = Math.max(8, (int) k.num("swords", 36));
        int waves = (int) k.num("waves", 6);
        int every = Math.max(10, (int) k.num("every", 10));
        int first = Math.max(ringTicks + 4, (int) k.num("first", 24));
        int finalAt = Math.max(first + (waves - 1) * every + 10, (int) k.num("finalAt", 86));
        String life = RuneKit.lifeToken(sp);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        PhantomFx.Lib L = PhantomFx.PHANTOM; // 2026-11-15 직업 전용 이펙트
        Ult.sky(level0, c0, "rpg:ult_sunset", finalAt + 30);
        // 2026-11-15 영상: 바닐라는 하늘이 바뀌지 않음 → 보는 쪽 지평선에 지는 진홍 해 (rfx_sun) · 둘레 노을 빛
        Vec3 fs = Kit.facingOf(sp);
        // (노을 판 rfx_jpdusk 는 뺌 — 2026-11-15 "도트처럼 보이는게 문제": 큰 글꼴 판은 픽셀이 그대로 보임)
        Ult.flash(level0, c0, glow, Opt.of("radius", 10.0, "time", new double[] {0.03, 0.05, 0.35}), sp);
        SkillFx.play(level0, "block.beacon.activate", c0, 1.2, 0.9);
        SkillFx.play(level0, "item.firecharge.use", c0, 0.6, 1.0);
        // 2026-11-15 직업 전용: 발밑 섬광 · 작은 충격파 (예전 발밑 흰 상자 고리 대신)
        Vec3 g0 = PhantomFx.ground(level0, c0);
        // 2026-11-15 영상 (1인칭): 몸 높이 (1칸) 의 섬광 2.6칸은 눈앞에서 화면을 덮었음 (픽셀 띠 · 누런 막) → 발밑 땅 (눈 아래라 1인칭에서는 안 보이고 3인칭에서는 발밑 섬광)
        PhantomFx.flash(level0, L, V.up(g0, 0.2), 2.4);
        PhantomFx.shock(level0, L, g0, 6.0, 1);
        Vfx.Fx fx = RuneProps.fx(level0, c0, finalAt + 40);
        List<Planted> field = new ArrayList<>();
        // 불꽃 고리가 번짐 — 2026-11-15 직업 전용: 퍼지는 큰 충격파 (ringTicks 동안 R 까지) + 앞 날을 따라 솟는 2D 불꽃 혀 (예전 상자 고리 · 상자 불꽃 대신)
        PhantomFx.shock(level0, L, g0, R * 2.15, Math.max(1, Math.round(ringTicks / 12F)));
        for (int i = 0; i <= ringTicks; i += 2) {
            double ri = Math.max(1.5, R * i / (double) ringTicks);
            RuneKit.later(i, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                if (ri < 2.5) return; // 1인칭: 발 둘레 불꽃은 눈앞을 덮음
                for (int q = 0; q < 10; q++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 p = ground(level, V.add(c0, new Vec3(Math.cos(a), 0, Math.sin(a)), ri));
                    PhantomFx.flame(level, L, p, 1.5 + Kit.rand() * 0.8, 1); // 2026-11-15 영상: 1.2 ~ 1.8칸은 촛불처럼 작았음
                }
            });
        }
        // 2026-11-15 직업 전용: 고리 테두리의 불꽃 혀 서른둘이 끝까지 타오름 (되풀이 — 혀마다 다른 칸에서 시작해 들쭉날쭉 · 세로축 빌보드)
        RuneKit.later(ringTicks, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            int hold = finalAt - ringTicks + 6;
            Vfx.Fx ff = Vfx.at(level, c0, hold + 8);
            int nf = 48; // 2026-11-15 영상 (3인칭): 서른둘 · 2 ~ 2.9칸은 촛불처럼 작고 성겼음 → 마흔여덟 · 2.8 ~ 3.8칸 (불의 벽)
            int frames = Vfx.sheet(L.n("flame")).frames();
            for (int q = 0; q < nf; q++) {
                double a = Math.PI * 2 * (q + Kit.rand() * 0.6) / nf;
                Vec3 p = ground(level, V.add(c0, new Vec3(Math.cos(a), 0, Math.sin(a)), R + (Kit.rand() - 0.5) * 0.5));
                Vfx.Sprite fl = PhantomFx.flame(ff, L, p, 2.8 + Kit.rand() * 1.0, 1 + (q % 2), true, (int) (Kit.rand() * frames));
                fl.fade(hold, 6);
            }
        });
        RuneKit.later(ringTicks, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            // 2026-11-15 직업 전용: 테두리 · 안쪽 고리 = 땅 고리 그림 (2 × 2 — 반짝임) — 예전 상자 고리 대신
            PhantomFx.ring(level, L, g0, R, 4, finalAt - ringTicks + 6);
            // 2026-11-15 마법진 (미리보기에서 맞춤): 육망성 (세모 둘 · 반지름 0.97R — 2틱마다 한 변씩 그어짐) + 그 안쪽 고리 (0.485R)
            PhantomFx.ring(level, L, g0, R * 0.485, 6, finalAt - ringTicks + 2);
            // 2026-11-15 HD: 안쪽 고리 안을 진홍 마법진 (육망성 · 칼 문장 · 금빛 룬) 이 채움
            mmorpg.fabric.vfx.BlockFx.sigil(level, V.up(c0, 0.045), R * 0.5, PhantomArcher.SIGIL_CRIMSON, 8, finalAt - ringTicks + 2, 45);
            double rho = R * 0.97;
            for (int tri = 0; tri < 2; tri++) {
                for (int e = 0; e < 3; e++) {
                    double a0 = Math.PI / 2 + tri * Math.PI / 3 + e * 2 * Math.PI / 3;
                    double a1 = a0 + 2 * Math.PI / 3;
                    int delay = (tri * 3 + e) * 2;
                    Vec3 pa = V.add(V.up(c0, 0.03), new Vec3(Math.cos(a0), 0, Math.sin(a0)), rho);
                    Vec3 pb = V.add(V.up(c0, 0.03), new Vec3(Math.cos(a1), 0, Math.sin(a1)), rho);
                    RuneKit.later(delay, () -> {
                        // 2026-11-15 영상: 굵기 0.2 + 발광은 3인칭에서 굵은 붉은 관 · 1인칭에서 화면을 가로지르는 붉은 막대 (가장 눈에 띄는 것이 됨) → 0.1 · 발광 없음
                        if (RuneKit.sameLife(sp, life)) mmorpg.fabric.vfx.BlockFx.line(sp.level(), pa, pb, 0.1, glow, 4, finalAt - ringTicks - delay);
                    });
                }
            }
            SkillFx.play(level, "block.fire.ambient", c0, 0.8, 1.0);
        });
        // 칼이 솟음 (가까운 데부터 — 번지는 고리를 따라). 2026-11-15 영상 (1인칭): 쓴 사람 1.3칸 안에 솟은 칼이 눈앞을 덮었음 → 둘레 fieldIn 칸 밖 (고리 안 넓이에 고르게)
        for (int i = 0; i < nSw; i++) {
            double rad = fieldRad(R, (i + 0.5) / nSw);
            int at = 3 + (int) (rad / R * ringTicks) + (i % 3);
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                plant(sp.level(), fx, field, c0, rad);
            });
        }
        // 물결: 칼이 뽑혀 적에게 날아가 꽂힘
        int fly = 4;
        for (int w = 0; w < waves; w++) {
            int ww = w;
            int hitAt = first + w * every;
            RuneKit.later(hitAt - fly, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                List<LivingEntity> in = Kit.around(sp, c0, R, 5);
                int launch = in.isEmpty() ? 4 : Math.max(4, Math.min(8, in.size() * 2));
                for (int q = 0; q < launch; q++) {
                    LivingEntity e = in.isEmpty() ? null : in.get(q % in.size());
                    Vec3 to = e != null ? V.up(Kit.bodyPoint(e), (Kit.rand() - 0.5) * 0.4) : V.up(randomIn(level, c0, R), 1.0);
                    Planted p = nearestFree(field, to);
                    if (p == null) break;
                    p.used = true;
                    RuneSwords.Blade b = p.b;
                    Vec3 lift = V.up(b.hilt(), 0.9);
                    b.pose(lift, b.dir(), PhantomArcher.faceOf(b.dir()), 1);
                    Vec3 d = V.unit(V.sub(to, lift));
                    RuneKit.later(1, () -> b.pose(V.add(to, d, -b.reach() * 0.75), d, PhantomArcher.faceOf(d), fly - 1));
                    if (RuneKit.fxQuota("sanctuaryStreak", 10)) PhantomArcher.streak2d(level, lift, to, 0.3, glow, core);
                    RuneKit.later(fly, () -> {
                        b.remove();
                        if (e == null && RuneKit.fxQuota("sanctuaryMiss", 6)) PhantomFx.flash(sp.level(), L, to, 1.1);
                    });
                    RuneKit.later(fly + 2, () -> {
                        if (RuneKit.sameLife(sp, life)) plant(sp.level(), fx, field, c0, fieldRad(R, Kit.rand()));
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
                        PhantomArcher.lodge(sp, j, e, 1, V.up(c0, 1.0));
                        if (RuneKit.fxQuota("sanctuaryHit", 8)) JobFx.hit(e, j, 1.0);
                    });
                }
            });
        }
        // 마지막: 남은 칼이 겨눈 곳 하늘로 솟았다가 한꺼번에 내리꽂힘
        Vec3[] spot = {null};
        List<RuneSwords.Blade> rain = new ArrayList<>();
        JobKit.motionLater(sp, "archer_ult:final", finalAt - 14, life);
        RuneKit.later(finalAt - 14, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            LivingEntity t = Kit.pickTarget(sp, k.num("range", 24), 20);
            spot[0] = t != null ? ground(level, t.position()) : ground(level, Kit.aimPoint(sp, k.num("range", 24)));
            int q = 0;
            for (Planted p : field) {
                if (p.used) continue;
                p.used = true;
                if (q++ >= 24) {
                    p.b.grow(0.1, 6);
                    continue;
                }
                double a = q * 2.399;
                double rr = 1.2 + 2.8 * Math.sqrt(q / 24.0);
                Vec3 top = V.add(V.up(spot[0], 7 + (q % 4) * 0.6), new Vec3(Math.cos(a), 0, Math.sin(a)), rr);
                Vec3 d = V.unit(V.sub(V.add(spot[0], new Vec3(Math.cos(a), 0, Math.sin(a)), rr * 0.25), top));
                p.b.pose(top, d, PhantomArcher.faceOf(d), 6 + (q % 4));
                rain.add(p.b);
            }
            // 2026-11-15 직업 전용: 하늘에 칼이 모이는 자리 — 모으는 빛이 커짐 (예전 세운 띠 상자 고리 대신)
            Vfx.Fx cf = Vfx.at(level, V.up(spot[0], 7.5), 16);
            Vfx.Sprite gather = PhantomFx.charge(cf, L, V.up(spot[0], 7.5), 2.4);
            cf.on(1, () -> gather.to(null, 6.5, 9));
            cf.on(10, () -> gather.fade(10, 3));
            SkillFx.play(level, "block.amethyst_block.resonate", spot[0], 0.7, 1.2);
            SkillFx.play(level, "item.trident.riptide_3", spot[0], 1.2, 0.8);
        });
        RuneKit.later(finalAt - 4, () -> {
            if (spot[0] == null) return;
            for (RuneSwords.Blade b : rain) {
                Vec3 d = b.dir();
                Vec3 hit = V.add(spot[0], new Vec3(Kit.rand() - 0.5, 0, Kit.rand() - 0.5), 2.4);
                b.pose(V.add(V.up(hit, 0.1), d, -b.reach() * 0.6), d, PhantomArcher.faceOf(d), 4);
            }
        });
        RuneKit.later(finalAt, () -> {
            if (spot[0] == null) return;
            ServerLevel level = sp.level();
            double fr = k.num("finalRadius", 4);
            // 2026-11-15 직업 전용: 큰 폭발 (불덩이 · 땅 충격파 · 그을린 땅 · 불씨 · 칼 조각 — 예전 상자 폭발 · 상자 고리 · 상자 조각 대신)
            // 2026-11-15 영상: 궁극기 마지막 폭발이 3인칭 (13칸 카메라) 에서 작았음 — fr × 1.05 → 1.5 (불덩이 지름 약 11칸)
            PhantomFx.boom(level, L, V.up(spot[0], 0.1), fr * 1.5);
            SkillFx.play(level, "entity.generic.explode", spot[0], 0.7, 1.4);
            if (!RuneKit.sameLife(sp, life)) return;
            for (LivingEntity e : Kit.around(sp, spot[0], fr, 4)) JobKit.strike(sp, e, j, P, k.num("final", 14), false, true, life, 0, d -> JobFx.hit(e, j, 1.4));
        });
        RuneKit.later(finalAt + 12, () -> {
            ServerLevel level = sp.level();
            for (RuneSwords.Blade b : rain) PhantomArcher.shatter(level, b, 1.2, L);
            for (Planted p : field) {
                if (!p.used) p.b.grow(0.1, 6);
            }
        });
        RuneKit.later(finalAt + 19, () -> {
            for (Planted p : field) p.b.remove();
        });
        return true;
    }

    /** 들판 칼이 솟는 거리: 쓴 사람 둘레 2.8칸 (1인칭 눈앞) 밖 ~ 0.95R, 넓이에 고르게 (u = 0 ~ 1) */
    private static double fieldRad(double R, double u) {
        double in = Math.min(2.8, R * 0.4);
        double out = R * 0.95;
        return Math.sqrt(in * in + (out * out - in * in) * Math.max(0, Math.min(1, u)));
    }

    /** 고리 안 rad 칸 (각도는 아무렇게나) 땅에 칼 하나가 솟아 꽂힘 */
    private static void plant(ServerLevel level, Vfx.Fx fx, List<Planted> field, Vec3 c0, double rad) {
        double a = Kit.rand() * Math.PI * 2;
        Vec3 g = ground(level, V.add(c0, new Vec3(Math.cos(a), 0, Math.sin(a)), rad));
        Vec3 d = V.unit(V.add(V.DOWN, new Vec3(Kit.rand() - 0.5, 0, Kit.rand() - 0.5), 0.5));
        double len = 2.4 + Kit.rand() * 0.8; // 2026-11-15 미리보기: 들판 칼 더 크게 (1.8 ~ 2.6 은 3인칭에서 작았음)
        Vec3 face = PhantomArcher.faceOf(d);
        RuneSwords.Blade b = RuneSwords.phantom(fx, V.add(g, d, -len * 0.15), d, face, len, 0.7, PhantomArcher.BLADE, PhantomArcher.GEM);
        b.prop.outline(PhantomFx.PHANTOM.glow()); // 2026-11-15 직업 전용: 들판 칼이 진홍으로 번짐
        b.pose(V.add(g, d, -len * 0.62), d, face, 3);
        b.grow(1, 3);
        field.add(new Planted(b, g));
        if (RuneKit.fxQuota("sanctuaryRise", 6)) {
            // 2026-11-15 직업 전용: 솟는 자리 흙먼지 · 작은 섬광 (예전 상자 별 · 불티 대신)
            PhantomFx.dust(level, PhantomFx.PHANTOM, g, 1.6);
            PhantomFx.flash(level, PhantomFx.PHANTOM, V.up(g, 0.4), 0.9);
        }
    }

    private static Planted nearestFree(List<Planted> field, Vec3 to) {
        Planted best = null;
        double bd = Double.MAX_VALUE;
        for (Planted p : field) {
            if (p.used) continue;
            double d = V.len(V.sub(p.ground, to));
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private static Vec3 randomIn(ServerLevel level, Vec3 c0, double R) {
        double a = Kit.rand() * Math.PI * 2;
        return ground(level, V.add(c0, new Vec3(Math.cos(a), 0, Math.sin(a)), R * Math.sqrt(Kit.rand()) * 0.9));
    }
}
