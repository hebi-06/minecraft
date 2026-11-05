package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 마검사 각성 아케인 블레이드 — 2026-11-05 룬 스미스 대신 (사용자 "이명을 지우고 다시 직업 이름만 냅두는걸로 하고 컨셉도 이명에 안맞추고 하자 이명에 맞추면서
 * 할려니깐 덜 화려한거 같음" · "단타 애들이 너무 밋밋해 좀 더 화려하게 커다란 검기가 나오면서 때리면"): 마검사와 같은 매커니즘 (근접 마법 딜러 · 광역 · 큰 단타 2단 ·
 * 마력 각인), 칼끝에서 거대한 마력 검기를 쏟아내는 마검사의 끝 — 1차와 짝이 없는 새 스킬 다섯:
 * <ul>
 * <li>크레센트 엣지 (롤 리븐 R2): 크게 감았다가 가로 · 세로로 거대한 초승달 검기 둘</li>
 * <li>팬텀 크로스 (롤 마스터 이 Q): 가장 가까운 적 앞으로 순간 이동해 X 자로 새김 → 칼을 털면 거대한 X 검기로 터짐</li>
 * <li>아케인 템페스트 (롤 다리우스 Q): 머리 위로 칼을 돌려 모았다가 크게 한 바퀴 — 둘레로 퍼지는 검기 · 끌어당김 → 마력 폭풍이 터짐</li>
 * <li>스펠 오버드라이브 (롤 리븐 R1): 칼이 커지고 평타마다 검기가 날아감 (JobTraits7)</li>
 * <li>월드 클리버 (롤 아트록스 Q): 칼이 하늘을 찌르는 마력 대검으로 자라나 앞 14칸을 내려쳐 가름 → 갈라진 땅을 따라 마력이 솟구침</li>
 * </ul>
 * 각인 다섯이 찬 적을 평타로 치면 하늘에서 거대한 마력 대검이 내리꽂힘 (롤 가렌 R — JobTraits7.giantSword). 큰 검기 = JobFx.bigWave (rfx_jwave), 감는 동안 칼끝에 모이는
 * 빛 = JobFx.charge. 마검사는 몸 쓰는 직업이 아님 — 칼은 크게 감고 휘두르되 맞는 것은 검기 · 마력. 맞은 자리는 JobImpact (화면은 흔들지 않음 — 사용자 "화면 흔들림은
 * 넣지마 멀미나"). 스킬이 처음 맞힌 적마다 각인 하나.
 */
final class ArcanebladeSkills {
    private ArcanebladeSkills() {}

    private static final double[] WHITE = RuneKit.WHITE;
    /** 팬텀 크로스 잔상 몸 (짙은 남보라 — 마력 도약과 같음) */
    private static final double[] SHADE = {0.3, 0.16, 0.5};

    static void register() {
        JobSystem.register("arcaneblade_crescent", ArcanebladeSkills::crescent);
        JobSystem.register("arcaneblade_cross", ArcanebladeSkills::cross);
        JobSystem.register("arcaneblade_tempest", ArcanebladeSkills::tempest);
        JobSystem.register(JobTraits7.OVERDRIVE, ArcanebladeSkills::overdrive);
        JobSystem.register("arcaneblade_ult", ArcanebladeSkills::ult);
    }

    /** 이 스킬로 처음 맞힌 적이면 각인 하나 */
    private static void markOnce(ServerPlayer sp, LivingEntity e, Jobs.Job j, Set<UUID> marked) {
        if (marked.add(e.getUUID())) JobTraits.mark(sp, e, j);
    }

    /** 땅 높이로 내린 자리 */
    private static Vec3 ground(ServerLevel level, Vec3 p) {
        return new Vec3(p.x, RuneKit.groundAt(level, V.up(p, 0.6), p.y), p.z);
    }

    /** 쓴 자리 c 에서 f 쪽 직선 (앞 from ~ to 칸 · 반 폭 half) 위의 적 — 몸 폭을 더함 */
    private static List<LivingEntity> inLine(ServerPlayer sp, Vec3 c, Vec3 f, double from, double to, double half) {
        Vec3 right = V.rightOf(f);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, V.add(c, f, (from + to) / 2), (to - from) / 2 + half + 1.0, 2.5)) {
            Vec3 d = V.sub(e.position(), c);
            double along = d.x * f.x + d.z * f.z;
            double side = Math.abs(d.x * right.x + d.z * right.z);
            double pad = Kit.bodyPad(e);
            if (along < from - 0.8 - pad || along > to + 0.4 + pad || side > half + pad) continue;
            out.add(e);
        }
        return out;
    }

    /** c 에서 f 쪽으로 잰 e 의 거리 */
    private static double along(Vec3 c, Vec3 f, LivingEntity e) {
        Vec3 d = V.sub(e.position(), c);
        return d.x * f.x + d.z * f.z;
    }

    // ------------------------------------------------------------ 크레센트 엣지

    /**
     * 크레센트 엣지 (롤 리븐 R2): 칼을 오른쪽 뒤로 크게 감아 charge 틱 동안 칼끝에 마력을 모았다가 (JobFx.charge) 가로로 크게 그어 거대한 초승달 검기 (sweep) → gap 틱 뒤
     * 머리 위에서 세로로 내려 그어 더 큰 검기 (cleave — 지나간 땅이 갈라짐). 검기는 앞 start 칸에서 speed 칸/틱으로 len 칸까지 날아가며 폭 width 의 적을 지나가는 틱에
     * 벰 (마법). 내려 그을 쪽은 그 순간 보는 쪽 (가로로 그은 뒤 몸을 돌려 다른 적을 겨눌 수 있음)
     */
    static boolean crescent(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int charge = Math.max(1, (int) k.num("charge", 6));
        int gap = Math.max(10, (int) k.num("gap", 10));
        String life = RuneKit.lifeToken(sp);
        Set<UUID> marked = new HashSet<>();
        ServerLevel level0 = sp.level();
        // 감음: 오른쪽 뒤로 당긴 칼끝에 빛이 빨려 들며 커짐 · 발밑 빛 고리 · 차오르는 울림 · 칼에 마력이 감기는 소리
        JobFx.charge(sp, j, new Vec3(0.8, 1.3, -0.45), charge, 0.75);
        SkillFx.play(level0, "item.trident.return", sp.position(), 1.4, 0.7);
        SkillFx.play(level0, "mob.evocation_illager.prepare_attack", sp.position(), 1.5, 0.45);
        RuneKit.later(charge, () -> {
            if (RuneKit.sameLife(sp, life)) crescentWave(sp, j, k, P, life, marked, false);
        });
        // 둘째: 되돌려 머리 위로 들어 올림 — 머리 위 칼끝에 다시 빛이 모임
        RuneKit.later(charge + gap - 5, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.charge(sp, j, new Vec3(0.15, 2.5, -0.1), 5, 0.9);
        });
        RuneKit.later(charge + gap, () -> {
            if (RuneKit.sameLife(sp, life)) crescentWave(sp, j, k, P, life, marked, true);
        });
        return true;
    }

    /** 초승달 검기 하나 (vertical = 둘째 — 세로로 내려 그음, 더 크고 셈): 그림 · 소리 · 길 위의 적 (지나가는 틱에) */
    private static void crescentWave(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life, Set<UUID> marked, boolean vertical) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double start = k.num("start", 1.0);
        double len = k.num("len", 8);
        double speed = Math.max(0.5, k.num("speed", 1.5));
        double half = k.num("width", 2.4) / 2;
        double L = Math.max(start + 1, Math.min(len, RuneKit.rayLength(level, V.up(c, 1.1), f, len + 0.5)));
        double secs = (L - start) / speed / 20.0;
        // 가로: 위로 불룩한 초승달 (조금 기움) · 세로: 선 초승달 (더 크게)
        double tilt = vertical ? 90 + (Kit.rand() - 0.5) * 8 : -10;
        double size = vertical ? 2.4 : 1.9;
        JobFx.bigWave(level, sp, V.up(V.add(c, f, start), vertical ? 1.3 : 1.1), f, speed * 20, size, tilt, secs + 0.14, j, Opt.empty());
        // 눈앞 큰 베기 (1인칭 화면 가운데 · 3인칭은 몸 앞 참격 판): 가로 — 오른쪽에서 왼쪽, 세로 — 위에서 아래
        JobFx.slash(sp, j, 2.2, 0.3, vertical ? 2.3 : 2.1, vertical ? 96.0 : 172.0, 0.26);
        RuneKit.Hand h = RuneKit.handOf(sp);
        SkillFx.sparks(level, V.up(h.hand(), 0.3), JobFx.inner(j), 10, 5.0);
        if (vertical) {
            // 세로로 내려 그은 검기가 땅을 스치며 갈라 놓음 (갈라짐이 검기를 따라 달림) · 칼이 땅에 닿은 자리의 터짐
            WarlordSkills.rift(level, ground(level, V.add(c, f, start)), f, L - start, JobFx.outer(j), 1.0, speed * 20);
            Vec3 g = ground(level, V.add(c, f, 1.4));
            JobFx.burst(level, V.up(g, 0.3), 0.9, j, 0.28, JobFx.hide1p(sp));
            RuneKit.rockBurst(level, V.up(g, 0.2), 5, 4.5, 0.14);
            SkillFx.play(level, "item.mace.smash_ground", g, 1.2, 0.6);
        }
        SkillFx.play(level, "entity.player.attack.sweep", c, vertical ? 0.6 : 0.8, 1.0);
        SkillFx.play(level, "item.trident.riptide_1", c, vertical ? 1.05 : 1.35, 0.75);
        SkillFx.play(level, "mob.evocation_illager.cast_spell", c, vertical ? 1.3 : 1.6, 0.45);
        double mult = vertical ? k.num("cleave", 5.1) : k.num("sweep", 3.4);
        for (LivingEntity e : inLine(sp, c, f, start, L, half)) {
            int at = (int) Math.ceil(Math.max(0, along(c, f, e) - start) / speed);
            JobKit.strike(sp, e, j, P, mult, true, false, life, at, x -> {
                markOnce(sp, e, j, marked);
                JobImpact.hit(sp, e, j, vertical ? 1.6 : 1.15);
            });
        }
    }

    // ------------------------------------------------------------ 팬텀 크로스

    /**
     * 팬텀 크로스 (롤 마스터 이 Q): 보는 쪽 dist 칸 안의 가장 가까운 적 바로 앞으로 (없으면 dist 칸 — 벽 앞까지) 순간 이동 — 떠난 자리 · 지나간 길에 잔상 (점점 옅게) ·
     * 마력 번개 → hitAt 틱에 앞의 적들 (앞 reach 칸 · 폭 width) 에 X 자로 두 번 그어 새김 (path — 마법 · 검흔이 남아 빛남) → crossAt 틱에 칼을 털어 거두는 순간 검흔이
     * 거대한 X 검기로 터짐 (cross — 같은 적, 그 사이 움직였으면 지금 자리). 묶였거나 앞이 막혀 못 가면 쓰지 않음 (가장 가까운 적이 바로 앞이면 제자리에서 벰)
     */
    static boolean cross(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double half = k.num("width", 1.6) / 2;
        double max = RuneKit.dashDistance(sp, f, k.num("dist", 7), 1.1, 1.8);
        // 가장 가까운 적 바로 앞 (그 적의 몸 폭 + 0.9칸 앞에 섬)
        List<Kit.LineHit> ahead = RuneKit.lineTargets(sp, V.up(start, 1.0), f, k.num("dist", 7), half + 0.4);
        double want = max;
        if (!ahead.isEmpty()) want = Math.min(max, Math.max(0, ahead.get(0).t() - 0.9 - Kit.bodyPad(ahead.get(0).e())));
        Vec3 dest = start;
        if (want > 0.4) {
            Vec3 p = V.add(start, f, want);
            Vec3 at = new Vec3(p.x, RuneKit.groundAt(level, p, start.y), p.z);
            if (Kit.moveTo(sp, at)) dest = at;
            else {
                // 그 자리가 막혔으면 길의 설 자리 중 가장 먼 곳
                List<Kit.Spot> spots = Kit.dashSpots(level, start, f, want);
                for (int i = spots.size() - 1; i >= 0 && dest == start; i--) if (Kit.moveTo(sp, spots.get(i).p())) dest = spots.get(i).p();
            }
        }
        if (dest == start && ahead.isEmpty()) {
            Notify.notify(sp, "§c" + k.name() + " §7— 앞이 막혔습니다", 30);
            return false;
        }
        double len = V.hdist(start, dest);
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        String life = RuneKit.lifeToken(sp);
        Set<UUID> marked = new HashSet<>();
        int hitAt = Math.max(0, (int) k.num("hitAt", 1));
        int crossAt = Math.max(hitAt + 10, (int) k.num("crossAt", 12));
        Kit.noFall(sp, 40);
        // 떠난 자리 · 지나간 길: 칼을 뻗고 지나가는 잔상 셋 (점점 옅게) · 마력 번개 · 돌진 줄기 (1인칭은 빠른 줄)
        RuneKit.shadeClone(sp, level, start, f, 1, SHADE, glow, Opt.of("life", 0.6, "fade", 0.6, "alpha", 0.85));
        if (len > 1.5) {
            for (int i = 1; i <= 2; i++) {
                Vec3 p = V.add(start, f, len * i / 3.0);
                RuneKit.shadeClone(sp, level, new Vec3(p.x, RuneKit.groundAt(level, p, start.y), p.z), f, 1, SHADE, glow, Opt.of("life", 0.5 - 0.08 * i, "fade",
                        0.6, "alpha", 0.75 - 0.15 * i));
            }
            Vec3 zapEnd = V.add(dest, f, -Math.min(1.8, len * 0.4));
            RuneKit.zap(level, V.up(start, 0.6), V.up(zapEnd, 0.6), glow, core, 0.18, 0.35);
            JobFx.dash(level, V.up(start, 0.9), V.up(zapEnd, 0.9), j, 0.35, JobFx.hide1p(sp));
            JobFx.speed(sp, j, 0.35, 0.9);
        }
        JobFx.burst(level, V.up(start, 1.0), 0.9, j, 0.3, Opt.empty());
        SkillFx.play(level, "mob.endermen.portal", start, 1.6, 0.7);
        SkillFx.play(level, "entity.player.attack.sweep", dest, 1.4, 0.8);
        // 새김 (hitAt): 앞의 적 (닿은 자리 앞 reach 칸 · 폭 width) — 눈앞 X 베기 (1인칭) · 적마다 X 검흔 (crossAt 까지 빛남)
        Vec3 c = dest;
        double reach = k.num("reach", 3.0);
        List<LivingEntity> targets = inLine(sp, c, f, 0, reach, half + 0.3);
        RuneKit.later(hitAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            JobFx.slash(sp, j, 2.0, 0.3, 1.8, 135.0, 0.2);
            RuneKit.later(1, () -> JobFx.slash(sp, j, 2.0, 0.3, 1.8, 45.0, 0.2));
            SkillFx.play(sp.level(), "item.spear.attack_miss", sp.position(), 1.3, 0.8);
        });
        int[] cnt = {0};
        for (LivingEntity e : targets) {
            JobKit.strike(sp, e, j, P, k.num("path", 2.55), true, false, life, hitAt, d -> {
                markOnce(sp, e, j, marked);
                JobImpact.fx(sp, e, j, 1.0);
                if (cnt[0]++ < 6 && e.level() instanceof ServerLevel lv) {
                    Vec3 b = Kit.bodyPoint(e);
                    double s = 0.7 + Kit.bodyPad(e) * 0.4;
                    JobFx.bigWave(lv, null, b, f, 0, s, 45, 0.2, j, Opt.of("alpha", 0.9));
                    JobFx.bigWave(lv, null, b, f, 0, s, -45, 0.2, j, Opt.of("alpha", 0.9));
                    JobFx.cross(lv, b, s * 0.9, j, (crossAt - hitAt) / 20.0 + 0.05, Opt.of("spin", 0.0, "alpha", 0.95, "vel", RuneKit.entVel(e)));
                }
            });
        }
        // 터지기 직전 (crossAt − 3): 검흔이 떨며 빛을 모음 · 소리
        RuneKit.later(crossAt - 3, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            for (LivingEntity e : targets) {
                if (e.isRemoved() || !e.isAlive() || !(e.level() instanceof ServerLevel lv)) continue;
                SkillFx.sparks(lv, Kit.bodyPoint(e), core, 4, 1.5);
            }
            SkillFx.play(sp.level(), "block.respawn_anchor.charge", c, 1.7, 0.55);
        });
        // 터짐 (crossAt — 칼을 털어 거둠): 적마다 거대한 X 검기 · 터짐 · 룬 고리 · 불티 (적이 없으면 앞 2칸에)
        RuneKit.later(crossAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            SkillFx.play(d, "random.explode", c, 1.4, 0.7);
            SkillFx.play(d, "entity.player.attack.sweep", c, 0.7, 1.0);
            SkillFx.play(d, "chime.amethyst_block", c, 1.2, 1.0);
            boolean any = false;
            int m = 0;
            for (LivingEntity e : targets) {
                if (e.isRemoved() || !e.isAlive()) continue;
                any = true;
                if (m++ < 5 && e.level() instanceof ServerLevel lv) {
                    Vec3 b = Kit.bodyPoint(e);
                    double s = 1.5 + Kit.bodyPad(e) * 0.6;
                    JobFx.bigWave(lv, null, b, f, 0, s, 45, 0.36, j, Opt.empty());
                    JobFx.bigWave(lv, null, b, f, 0, s, -45, 0.36, j, Opt.empty());
                    JobFx.burst(lv, b, 1.0 + Kit.bodyPad(e) * 0.4, j, 0.32, Opt.empty());
                    JobFx.runeRing(lv, V.up(e.position(), 0.1), 1.6 + Kit.bodyPad(e), j, 6, Kit.rand() * 60, 0.4, Opt.of("alpha", 0.8));
                    SkillFx.sparks(lv, b, core, 12, 6.0);
                }
                JobKit.strike(sp, e, j, P, k.num("cross", 2.55), true, false, life, 0, x -> {
                    markOnce(sp, e, j, marked);
                    JobImpact.hit(sp, e, j, 1.5);
                });
            }
            if (!any) {
                Vec3 b = V.up(V.add(c, f, 2.0), 1.1);
                JobFx.bigWave(d, null, b, f, 0, 1.5, 45, 0.32, j, Opt.empty());
                JobFx.bigWave(d, null, b, f, 0, 1.5, -45, 0.32, j, Opt.empty());
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 아케인 템페스트

    /**
     * 아케인 템페스트 (롤 다리우스 Q): 칼을 머리 위로 들어 돌리며 spinAt 틱 동안 마력을 모음 (머리 위에서 도는 작은 검기 · 칼끝으로 빨려 드는 빛 · 발밑 둘레 radius 칸
     * 룬 원 — 경고) → spinAt 틱에 크게 한 바퀴 휘두름: 둘레로 퍼지는 원형 검기 (큰 검기 여덟 · 허리 높이 회전 칼날 · 땅 충격파) — 둘레의 적 (spin · 가운데로 끌어당김
     * pull) → 칼끝을 따라 솟은 마력 폭풍 (회오리 · 오르는 검기) 이 burstAt 틱에 터짐 (burst — 둘레 radius 칸 · 띄움 lift)
     */
    static boolean tempest(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int spinAt = Math.max(4, (int) k.num("spinAt", 8));
        int burstAt = Math.max(spinAt + 10, (int) k.num("burstAt", 20));
        double R = k.num("radius", 4.5);
        String life = RuneKit.lifeToken(sp);
        Set<UUID> marked = new HashSet<>();
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        RpgCombat.setSteadfast(sp, burstAt + 4);
        // 모음: 머리 위 칼끝으로 빨려 드는 빛 · 둘레 룬 원 (터질 둘레) · 머리 위에서 도는 작은 검기 (2틱마다 45도씩)
        JobFx.charge(sp, j, new Vec3(0.0, 2.55, 0.0), spinAt, 0.9);
        JobFx.runeRing(level0, V.up(c0, 0.1), R, j, 10, Kit.rand() * 40, burstAt / 20.0 + 0.1, Opt.of("alpha", 0.85));
        SkillFx.play(level0, "item.trident.riptide_2", c0, 1.2, 0.6);
        for (int t = 0; t < spinAt; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 top = V.up(sp.position(), 2.5);
                JobFx.bigWave(sp.level(), null, top, V.UP, 0, 0.75 + tt * 0.03, tt * 45, 0.16, j, JobFx.hide1p(sp).with("alpha", 0.75));
                if (tt % 4 == 0) SkillFx.play(sp.level(), "entity.player.attack.sweep", top, 1.6 + tt * 0.04, 0.35);
            });
        }
        RuneKit.later(spinAt, () -> {
            if (RuneKit.sameLife(sp, life)) tempestSpin(sp, j, k, P, life, marked);
        });
        // 폭풍 (spinAt ~ burstAt): 몸에서 솟는 회오리 · 둘레를 돌며 오르는 검기 (2틱마다 둘) · 모이는 빛 알갱이 · 바람 소리
        RuneKit.later(spinAt + 1, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.twister(sp, j, 5.0, (burstAt - spinAt) / 20.0 + 0.1, 0.85);
        });
        for (int t = spinAt + 2; t < burstAt; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 c = sp.position();
                double q = (tt - spinAt) / (double) (burstAt - spinAt);
                for (int i = 0; i < 2; i++) {
                    double a = Math.toRadians(tt * 37 + i * 180);
                    double r = R * (0.75 - 0.35 * q);
                    Vec3 p = new Vec3(c.x + Math.cos(a) * r, c.y + 0.4 + q * 1.2, c.z + Math.sin(a) * r);
                    JobFx.bigWave(lv, null, p, V.UP, 5.0, 0.9 + 0.3 * q, 60 + i * 60 + tt * 20, 0.3, j, Opt.of("alpha", 0.8));
                }
                for (int i = 0; i < 3; i++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 from = new Vec3(c.x + Math.cos(a) * R, c.y + 0.3 + Kit.rand() * 1.5, c.z + Math.sin(a) * R);
                    SkillFx.fx(lv, "rpg:arrow_spark", from, Opt.of("dir", V.unit(V.sub(V.up(c, 1.5), from)), "speed", R * 3.5, "size", 0.13, "grow", 0.3, "life", 0.28,
                            "color", i % 2 == 0 ? JobFx.inner(j) : JobFx.outer(j), "alpha", 1.0));
                }
                if (tt % 4 == 0) SkillFx.play(lv, "item.elytra.flying", c, 1.4 + q * 0.4, 0.25);
            });
        }
        RuneKit.later(burstAt - 3, () -> {
            if (RuneKit.sameLife(sp, life)) SkillFx.play(sp.level(), "block.respawn_anchor.charge", sp.position(), 1.4, 0.7);
        });
        RuneKit.later(burstAt, () -> {
            if (RuneKit.sameLife(sp, life)) tempestBurst(sp, j, k, P, life, marked);
        });
        return true;
    }

    /** 크게 한 바퀴 (spinAt): 둘레로 퍼지는 큰 검기 여덟 · 허리 높이 회전 칼날 · 땅 충격파 · 눈앞 큰 베기 — 둘레 radius 칸 적 (spin · 끌어당김) */
    private static void tempestSpin(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life, Set<UUID> marked) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double R = k.num("radius", 4.5);
        double secs = 0.22;
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 + 22.5);
            Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
            JobFx.bigWave(level, null, V.up(V.add(c, d, 1.1), 1.0), d, (R - 1.1) / secs, 1.35, 0, secs + 0.1, j, JobFx.hide1p(sp));
        }
        JobFx.whirl(level, V.up(c, 0.95), R * 0.95, j, 0.35, Opt.of("alpha", 0.9));
        JobFx.ring(level, V.up(c, 0.08), R, j, false, 0.4, Opt.empty());
        SkillFx.shock(level, V.up(c, 0.1), JobFx.outer(j), R, 0.4);
        JobFx.slash(sp, j, 2.2, 0.35, 2.4, 180.0, 0.24);
        SkillFx.play(level, "entity.player.attack.sweep", c, 0.55, 1.0);
        SkillFx.play(level, "item.trident.riptide_3", c, 1.0, 0.8);
        SkillFx.play(level, "mob.evocation_illager.cast_spell", c, 1.2, 0.5);
        double pull = k.num("pull", 0.5);
        for (LivingEntity e : Kit.around(sp, c, R, 2.5)) {
            JobKit.strike(sp, e, j, P, k.num("spin", 6.1), true, false, life, 0, x -> {
                markOnce(sp, e, j, marked);
                JobImpact.hit(sp, e, j, 1.2);
                // 가운데로 끌어당김 (1.5칸 넘게 떨어진 · 밀 수 있는 적 — 폭풍 안으로)
                if (JobKit.pushable(e) && V.hdist(e.position(), c) > 1.5) RuneKit.knock(e, V.flat(V.sub(c, e.position())), pull, 0.12);
            });
        }
    }

    /** 폭풍이 터짐 (burstAt): 가운데 큰 터짐 · 솟구치는 큰 검기 넷 · 룬 고리 · 충격파 · 불티 · 돌 — 둘레 radius 칸 적 (burst · 띄움) */
    private static void tempestBurst(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life, Set<UUID> marked) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double R = k.num("radius", 4.5);
        Vec3 mid = V.up(c, 1.4);
        JobFx.burst(level, mid, R * 0.55, j, 0.42, JobFx.hide1p(sp));
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(i * 90 + 45);
            Vec3 p = new Vec3(c.x + Math.cos(a) * R * 0.55, c.y + 0.5, c.z + Math.sin(a) * R * 0.55);
            JobFx.bigWave(level, null, p, V.UP, 14.0, 1.6, 90 + (i % 2 == 0 ? 20 : -20), 0.32, j, Opt.empty());
        }
        JobFx.runeRing(level, V.up(c, 0.1), R, j, 12, Kit.rand() * 30, 0.5, Opt.empty());
        JobFx.ring(level, V.up(c, 0.08), R * 1.05, j, true, 0.45, Opt.empty());
        SkillFx.shock(level, V.up(c, 0.1), JobFx.inner(j), R * 1.1, 0.5);
        RuneKit.rockBurst(level, V.up(c, 0.3), 9, 6.5, 0.17);
        SkillFx.sparks(level, mid, JobFx.inner(j), 22, 8.0);
        // 1인칭: 눈앞 터짐과 고리 (보는 쪽 2.8칸 — 화면 가운데 조금 아래)
        Vec3 look = sp.getViewVector(1F);
        Vec3 ahead = V.add(V.add(sp.getEyePosition(), look, 2.8), JobFx.screenUp(look), -0.3);
        JobFx.burst(level, ahead, 1.6, j, 0.4, JobFx.only1p(sp));
        JobFx.ringFacing(level, ahead, 1.2, j, V.scale(look, -1), 0.4, JobFx.only1p(sp));
        SkillFx.play(level, "random.explode", c, 1.0, 0.9);
        SkillFx.play(level, "chime.amethyst_block", c, 0.8, 1.0);
        SkillFx.play(level, "item.trident.thunder", c, 1.6, 0.4);
        for (LivingEntity e : Kit.around(sp, c, R, 3.0)) {
            JobKit.strike(sp, e, j, P, k.num("burst", 9.2), true, false, life, 0, x -> {
                markOnce(sp, e, j, marked);
                JobImpact.hit(sp, e, j, 1.7);
                if (JobKit.pushable(e)) Kit.launch(e, 0, k.num("lift", 0.7), 0);
            });
        }
    }

    // ------------------------------------------------------------ 스펠 오버드라이브

    /**
     * 스펠 오버드라이브 (롤 리븐 R1 — 태세 dur 틱): 칼을 얼굴 앞에 세우면 칼끝으로 마력이 빨려 들고 (JobFx.charge) 왼손으로 칼날을 쓸어 내리는 순간 칼이 커지며 빛남
     * (SWING_LOOK arcaneblade_overdrive) · 발밑 룬 원 · 눈앞 고리 · 짧은 섬광 · 평타 사거리 + reach. 그동안 칼 평타를 휘두를 때마다 검기 (JobTraits7.overdriveSwing ·
     * waveBonus)
     */
    static boolean overdrive(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) k.num("dur", 240);
        JobTraits.startStance(sp, k, dur);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c = sp.position();
        JobFx.charge(sp, j, new Vec3(0.1, 1.9, 0.55), 8, 0.8);
        JobFx.runeRing(level0, V.up(c, 0.08), 1.8, j, 8, Kit.rand() * 40, 0.75, Opt.of("alpha", 0.9));
        SkillFx.play(level0, "block.respawn_anchor.charge", c, 1.2, 0.5);
        // 칼날을 쓸어 내림 (4 ~ 8): 칼을 따라 흐르는 빛 (칼끝 → 손)
        for (int t = 4; t <= 8; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                RuneKit.Hand h = RuneKit.handOf(sp);
                Vec3 p = V.up(V.add(h.hand(), h.f(), 0.25), 1.15 - (tt - 4) * 0.22);
                JobFx.glint(sp.level(), p, 0.5, j, 0.18, Opt.of("vel", RuneKit.bodyVel(sp)));
                SkillFx.play(sp.level(), "block.amethyst_block.chime", p, 1.4 + tt * 0.05, 0.5);
            });
        }
        // 켜짐 (9): 칼이 커지며 빛남 — 칼 둘레 터짐 · 발밑 고리 · 충격파 · 눈앞 고리 · 짧은 섬광 · 소리
        RuneKit.later(9, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            RuneKit.Hand h = RuneKit.handOf(sp);
            Vec3 blade = V.up(V.add(h.hand(), h.f(), 0.4), 0.7);
            JobFx.burst(lv, blade, 1.0, j, 0.3, JobFx.hide1p(sp));
            JobFx.bigWave(lv, null, blade, h.f(), 0, 1.3, 90, 0.3, j, JobFx.hide1p(sp).with("alpha", 0.85));
            JobFx.ring(lv, V.up(sp.position(), 0.08), 2.2, j, false, 0.45, Opt.of("alpha", 0.9));
            SkillFx.shock(lv, V.up(sp.position(), 0.1), JobFx.outer(j), 2.6, 0.35);
            JobFx.eyeRing(sp, j, 2.2, 1.0, 0.35);
            SkillFx.sparks(lv, blade, JobFx.inner(j), 16, 6.0);
            SkillFx.play(lv, "beacon.activate", sp.position(), 1.4, 0.6);
            SkillFx.play(lv, "item.trident.thunder", sp.position(), 1.8, 0.35);
            Ult.flash(lv, sp.position(), JobFx.outer(j), Opt.of("radius", 4.0, "time", new double[] {0.03, 0.05, 0.3}), sp);
        });
        return true;
    }

    // ------------------------------------------------------------ 월드 클리버

    /**
     * 월드 클리버 (롤 아트록스 Q): 보랏빛 하늘 · 칼을 하늘로 치켜들면 칼이 하늘을 찌르는 거대한 마력 대검으로 자라남 (머리 위 — 칼끝으로 빨려 드는 빛 · 둘레 룬 원 · 내려칠 길의
     * 룬 빛 금) → slamAt 틱에 앞 len 칸을 내려쳐 가름 (거대한 대검이 휘둘러 내려옴 · 앞으로 달리는 거대한 세로 검기 · 땅 갈라짐 — 폭 width 의 적 slam) → riftAt 틱부터
     * 갈라진 땅을 따라 riftSpeed 칸/틱으로 마력이 솟구침 (솟는 검기 · 터짐 — 닿는 적 rift · 띄움 lift). 내려칠 쪽은 쓴 순간 보는 쪽. 그동안 밀려나지 않음
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int slamAt = Math.max(8, (int) k.num("slamAt", 21));
        int riftAt = Math.max(slamAt + 2, (int) k.num("riftAt", 30));
        double len = k.num("len", 14);
        double half = k.num("width", 3.6) / 2;
        double start = k.num("start", 1.0);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 right = V.rightOf(f);
        Set<UUID> marked = new HashSet<>();
        RpgCombat.setSteadfast(sp, riftAt + 12);
        RuneKit.effect(sp, "slowness", slamAt + 8, 4);
        Ult.sky(level0, c0, "rpg:ult_arcane", riftAt + 40);
        Ult.flash(level0, c0, JobFx.outer(j), Opt.of("radius", 10.0, "time", new double[] {0.04, 0.06, 0.4}), sp);
        SkillFx.play(level0, "mob.evocation_illager.prepare_summon", c0, 0.8, 0.9);
        SkillFx.play(level0, "block.respawn_anchor.charge", c0, 0.6, 0.9);
        // 모음: 머리 위 칼끝으로 빨려 드는 빛 · 발밑 룬 원 · 내려칠 길 양쪽 가장자리의 룬 빛 금 (경고) · 칼끝에서 하늘로 빛기둥
        JobFx.charge(sp, j, new Vec3(0.1, 2.6, 0.15), slamAt - 2, 1.5);
        JobFx.runeRing(level0, V.up(c0, 0.1), 2.6, j, 9, 0, slamAt / 20.0 + 0.1, Opt.of("alpha", 0.95));
        for (int side = -1; side <= 1; side += 2) {
            Vec3 edge = ground(level0, V.add(V.add(c0, f, start), right, side * (half + 0.15)));
            WarlordSkills.rift(level0, edge, f, len - start, JobFx.outer(j), 0.55, 40);
        }
        // 거대한 마력 대검이 머리 위에서 자라남 (3 ~ slamAt − 3 — 칼끝이 하늘을 찌름)
        int grow = slamAt - 6;
        RuneKit.later(3, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 top = V.up(sp.position(), 2.3);
            JobFx3d.spawn(sp.level(), "phantom_sword", top, j, Opt.of("dir", V.UP, "scale", 1.4, "grow", 2.6, "life", grow / 20.0 + 0.05, "fadeIn", 0.2, "fadeOut",
                    0.05, "shine", 0.6, "glowAlpha", 0.6, "rune", 1.0));
            JobFx.line(sp.level(), V.up(top, 1.5), V.up(top, 14.0), j, grow / 20.0, Opt.of("alpha", 0.55));
        });
        for (int t = 4; t < slamAt - 2; t += 3) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                double q = (tt - 4) / (double) (slamAt - 6);
                Vec3 tipAt = V.up(sp.position(), 2.6 + 6.0 * q);
                SkillFx.sparks(lv, tipAt, JobFx.inner(j), 5, 3.0);
                JobFx.runeRing(lv, V.up(sp.position(), 2.4 + 5.0 * q), 1.2 + 0.6 * q, j, 6, tt * 25, 0.25, JobFx.hide1p(sp).with("alpha", 0.6));
                SkillFx.play(lv, "block.amethyst_block.resonate", tipAt, 0.6 + 0.8 * q, 0.5);
            });
        }
        // 휘둘러 내려옴 (slamAt − 3 ~ slamAt): 거대한 대검이 쥔 곳을 축으로 하늘에서 앞 땅으로
        double S = 1.4 * 3.6;
        RuneKit.later(slamAt - 3, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 pivot = V.up(V.add(sp.position(), f, 0.5), 1.5);
            JobFx3d.swing(lv, "phantom_sword", pivot, V.UP, f, -8, 98, 0.15, 0.55, S, JobFx3d.SWORD_GRIP, j, Opt.of("arcEase", "in", "shine", 0.7, "rune", 1.0));
            SkillFx.play(lv, "entity.player.attack.sweep", pivot, 0.5, 1.0);
            SkillFx.play(lv, "item.trident.riptide_3", pivot, 0.7, 0.9);
        });
        RuneKit.later(slamAt, () -> {
            if (RuneKit.sameLife(sp, life)) cleave(sp, j, k, P, c0, f, life, marked);
        });
        RuneKit.later(riftAt - 3, () -> {
            if (RuneKit.sameLife(sp, life)) SkillFx.play(sp.level(), "block.respawn_anchor.charge", V.add(c0, f, len * 0.4), 0.8, 0.9);
        });
        RuneKit.later(riftAt, () -> {
            if (RuneKit.sameLife(sp, life)) erupt(sp, j, k, P, c0, f, life, marked);
        });
        return true;
    }

    /**
     * 내려쳐 가름 (slamAt): 대검이 땅에 닿은 자리의 큰 터짐 · 앞으로 달리는 거대한 세로 검기 (둘 — 겹친 잔상) · 길을 따라 갈라지는 땅 · 충격파 · 돌 · 1인칭 눈앞 큰
     * 베기 — 길 (앞 start ~ len 칸 · 폭 width) 의 적 slam (한꺼번에 — 대검의 날이 길 전체를 덮음)
     */
    private static void cleave(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 c0, Vec3 f, String life, Set<UUID> marked) {
        ServerLevel level = sp.level();
        double len = k.num("len", 14);
        double half = k.num("width", 3.6) / 2;
        double start = k.num("start", 1.0);
        JobKit.motion(sp, "arcaneblade_ult:slam");
        Vec3 hit = ground(level, V.add(c0, f, 2.6));
        JobFx.burst(level, V.up(hit, 0.6), 2.2, j, 0.42, Opt.empty());
        JobFx.smash(level, V.up(hit, 0.05), 3.0, j, 0.5, Opt.empty());
        JobFx.bigWave(level, sp, V.up(V.add(c0, f, start), 1.8), f, 45.0, 3.4, 90, (len - start) / 45.0 + 0.15, j, Opt.empty());
        WarlordSkills.rift(level, ground(level, V.add(c0, f, start)), f, len - start, JobFx.outer(j), 1.8, 60);
        SkillFx.shock(level, V.up(hit, 0.1), JobFx.outer(j), 4.5, 0.5);
        RuneKit.rockBurst(level, V.up(hit, 0.3), 12, 7.5, 0.2);
        SkillFx.sparks(level, V.up(hit, 0.8), JobFx.inner(j), 24, 8.0);
        JobFx.slash(sp, j, 2.4, 0.2, 3.2, 96.0, 0.3);
        RuneKit.godRays(sp, level, hit, 4.0, 8, JobFx.outer(j), JobFx.inner(j), Opt.of("h", 8.0, "life", 0.7, "alpha", 0.7, "skipNear", true));
        Ult.flash(level, c0, JobFx.inner(j), Opt.empty(), sp);
        SkillFx.play(level, "random.explode", hit, 0.7, 1.0);
        SkillFx.play(level, "item.mace.smash_ground_heavy", hit, 0.8, 1.0);
        SkillFx.play(level, "item.trident.thunder", hit, 1.0, 0.7);
        for (LivingEntity e : inLine(sp, c0, f, start, len, half)) {
            JobKit.strike(sp, e, j, P, k.num("slam", 24), true, false, life, 0, x -> {
                markOnce(sp, e, j, marked);
                JobImpact.hit(sp, e, j, 2.0);
            });
        }
    }

    /**
     * 갈라진 땅이 터짐 (riftAt ~): 쓴 자리에서 riftSpeed 칸/틱으로 1.5칸마다 마력이 솟구침 (위로 솟는 큰 검기 · 터짐 · 룬 글자 · 불티) — 닿는 적 rift (큰 숫자) · 띄움
     */
    private static void erupt(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 c0, Vec3 f, String life, Set<UUID> marked) {
        ServerLevel level = sp.level();
        double len = k.num("len", 14);
        double half = k.num("width", 3.6) / 2;
        double start = k.num("start", 1.0);
        double speed = Math.max(0.5, k.num("riftSpeed", 2.0));
        Vec3 right = V.rightOf(f);
        for (double s = start; s <= len + 1e-6; s += 1.5) {
            double ss = s;
            int at = (int) Math.ceil((s - start) / speed);
            RuneKit.later(at, () -> {
                ServerLevel lv = sp.level();
                Vec3 g = ground(lv, V.add(V.add(c0, f, ss), right, (Kit.rand() - 0.5) * 0.6));
                double q = 1 - (ss - start) / Math.max(1, len - start) * 0.35;
                JobFx.bigWave(lv, null, V.up(g, 0.4), V.UP, 16.0, 1.7 * q, 90 + (Kit.rand() - 0.5) * 30, 0.34, j, Opt.empty());
                JobFx.burst(lv, V.up(g, 0.9), 1.3 * q, j, 0.35, Opt.empty());
                JobFx.glyph(lv, V.up(g, 1.4), 0.7, (int) (Kit.rand() * 3), 4, 2, 0.3, false, Opt.empty(), j);
                RuneKit.rockBurst(lv, V.up(g, 0.2), 3, 5.0, 0.15);
                SkillFx.sparks(lv, V.up(g, 1.0), JobFx.inner(j), 8, 6.0);
                if (((int) Math.round(ss / 1.5)) % 2 == 0) SkillFx.play(lv, "random.explode", g, 1.1, 0.55);
            });
        }
        SkillFx.play(level, "item.trident.thunder", V.add(c0, f, len * 0.5), 0.8, 0.8);
        for (LivingEntity e : inLine(sp, c0, f, start, len, half)) {
            int at = (int) Math.ceil(Math.max(0, along(c0, f, e) - start) / speed);
            JobKit.strike(sp, e, j, P, k.num("rift", 44), true, true, life, at, x -> {
                markOnce(sp, e, j, marked);
                JobImpact.hit(sp, e, j, 2.3);
                if (JobKit.pushable(e)) Kit.launch(e, 0, k.num("lift", 1.0), 0);
            });
        }
    }
}
