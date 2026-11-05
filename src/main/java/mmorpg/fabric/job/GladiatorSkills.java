package mmorpg.fabric.job;

import java.util.List;
import mmorpg.core.Jobs;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 전사 반전 글래디에이터 — 재설계안 2판 (라이칸 대신 — 사용자 "반전 컨셉도 대부분이 맘에 안듬" · "매커니즘적으로 애가 단일인지
 * 광역인지랑 연타를 하는 애인지 단타로 때리는지"): 전사 (광역 · 묵직한 단타 · 맞으며 버팀) 를 뒤집음 — 한 적만 (결투 상대) · 빠른 연타 · 버티지 않음. 스킬 다섯 —
 * 도끼 난타 (롤 잭스 패시브 · 신 짜오 — 세 번째마다 크게) · 걸어 당기기 (롤 다리우스 E) · 여덟 팔 (롤 브라이어 W — 연타 열여섯) · 액스 그라인더 (롤 올라프 Q — 2026-11-05 불사의 분노 대신) ·
 * 홀름강 (롤 모데카이저 R — 둘만의 결투장). 특성 「결투」 은 JobTraits6. 빠른 연타는 몹 무적 시간 (10틱) 때문에 피해를 무리마다 한 번에 넣고 숫자를 줄마다
 * (JobKit.strikeSplit — 도끼 난타는 줄마다 다른 몫), 도끼질 그림 · 멈칫은 줄마다. 맞은 자리는 JobImpact (화면은 흔들지 않음). 몸 쓰는 직업 기준 (제 몸 · 제 도끼 —
 * 여덟 팔은 제 팔의 잔상 rfx_jarms).
 */
final class GladiatorSkills {
    private GladiatorSkills() {}

    private static final double[] WHITE = RuneKit.WHITE;
    private static final double[] DUST = JobTraits6.DUST;
    /** 결투장의 돌 (짙은 모래빛 돌) */
    private static final double[] STONE = {0.42, 0.36, 0.3};

    static void register() {
        JobSystem.register("gladiator_flurry", GladiatorSkills::flurry);
        JobSystem.register("gladiator_hook", GladiatorSkills::hook);
        JobSystem.register("gladiator_eight", GladiatorSkills::eight);
        JobSystem.register("gladiator_grinder", GladiatorSkills::grinder);
        JobSystem.register("gladiator_ult", GladiatorSkills::ult);
    }

    /** 결투 상대 (reach 칸 안 — 몸 크기만큼 더) → 없으면 앞 reach 칸 · arc 도의 가장 가까운 보이는 적 (없으면 null) */
    static LivingEntity pick(ServerPlayer sp, double reach, double arc) {
        LivingEntity t = JobTraits6.duelTarget(sp);
        if (t != null && Kit.canHit(sp, t) && Math.hypot(t.getX() - sp.getX(), t.getZ() - sp.getZ()) <= reach + 0.6 + Kit.bodyPad(t)) return t;
        List<LivingEntity> c = RuneKit.coneTargets(sp, reach, arc, 1);
        return c.isEmpty() ? null : c.get(0);
    }

    /** 그 적이 아직 맞을 수 있고 reach 칸 안인지 */
    private static boolean inReach(ServerPlayer sp, LivingEntity e, double reach) {
        return e != null && !e.isRemoved() && e.isAlive() && Kit.canHit(sp, e) && Math.hypot(e.getX() - sp.getX(), e.getZ() - sp.getZ()) <= reach + 1.0 + Kit.bodyPad(e);
    }

    /** 도끼질 한 번의 그림: 1인칭 눈앞 베기 · 3인칭 판 (맞는 적의 몸 — 없으면 앞 1.6칸) · 맞은 자리 (그림 · 소리 · 움찔 — 멈칫은 stop 이면) */
    private static void chopFx(ServerPlayer sp, Jobs.Job j, LivingEntity e, double tilt, double size, double power, boolean stop) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        JobFx.slash(sp, j, 2.1, 0.2, size, tilt, 0.18);
        Vec3 at = e != null ? Kit.bodyPoint(e) : V.up(V.add(sp.position(), f, 1.6), 1.2);
        Vec3 dir = e != null ? V.unit(V.sub(Kit.bodyPoint(e), sp.getEyePosition())) : f;
        JobFx.slashCard(level, at, dir, tilt, size * 1.25, 0.22, e != null ? RuneKit.entVel(e) : V.ZERO, j, JobFx.hide1p(sp));
        SkillFx.play(level, "entity.player.attack.sweep", at, 0.9 + Kit.rand() * 0.3, 0.6);
        if (e == null) return;
        if (stop) JobImpact.hit(sp, e, j, power);
        else JobImpact.fx(sp, e, j, power);
    }

    // ------------------------------------------------------------ 도끼 난타

    /** 도끼 난타의 도끼질 기울기 (무리마다: 비스듬히 내려 · 되돌려 올려 · 머리 위에서 곧게 내려침) */
    private static final double[] FLURRY_TILT = {32, 208, 95, -28, 152, 95};

    /**
     * 도끼 난타 (롤 잭스 패시브 · 신 짜오 Q): 결투 상대 (없으면 앞 reach 칸 · arc 도의 적) 에게 도끼질 chops — first 틱부터 every 틱마다, group 번마다 한 무리
     * (무리 사이 groupGap 틱 — 무리의 피해는 그 무리 첫 도끼질에 한 번에 넣고 숫자는 도끼질마다 그 몫만큼). 무리의 마지막 (세 번째) 은 머리 위에서 곧게 내려침 (크게 ·
     * 멈칫 3틱)
     */
    static boolean flurry(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        List<Double> chops = SwordmasterSkills.numbers(k, "chops", List.of(0.85, 0.85, 1.55, 0.85, 0.85, 1.55));
        int group = Math.max(1, (int) k.num("group", 3));
        int first = Math.max(0, (int) k.num("first", 2));
        int every = Math.max(1, (int) k.num("every", 3));
        int groupGap = Math.max(10, (int) k.num("groupGap", 10));
        double reach = k.num("reach", 4.5);
        double arc = k.num("arc", 100);
        String life = RuneKit.lifeToken(sp);
        LivingEntity[] target = {pick(sp, reach, arc)};
        int n = chops.size();
        int last = first + ((n - 1) / group) * groupGap + ((n - 1) % group) * every;
        RpgCombat.setSteadfast(sp, last + 6);
        SkillFx.play(sp.level(), "item.armor.equip_iron", sp.position(), 1.3, 0.6);
        for (int g = 0; g * group < n; g++) {
            int from = g * group;
            int to = Math.min(n, from + group);
            double[] parts = new double[to - from];
            double sum = 0;
            for (int i = from; i < to; i++) {
                parts[i - from] = chops.get(i);
                sum += chops.get(i);
            }
            double mult = sum;
            int at = first + g * groupGap;
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                if (!inReach(sp, target[0], reach)) target[0] = pick(sp, reach, arc);
                LivingEntity e = target[0];
                if (e != null) JobKit.strikeSplit(sp, e, j, P, mult, parts.length, every, parts, false, false, life, 0, null);
            });
            for (int i = from; i < to; i++) {
                int q = i - from;
                int ii = i;
                boolean heavy = q == group - 1;
                RuneKit.later(at + q * every, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    LivingEntity e = inReach(sp, target[0], reach) ? target[0] : null;
                    double tilt = FLURRY_TILT[ii % FLURRY_TILT.length] + Kit.rand() * 10 - 5;
                    chopFx(sp, j, e, tilt, heavy ? 2.7 : 2.0, heavy ? 1.6 : 1.0, heavy);
                    if (heavy) {
                        ServerLevel lv = sp.level();
                        Vec3 g0 = e != null ? e.position() : V.add(sp.position(), Kit.facingOf(sp), 1.8);
                        JobFx.smash(lv, V.up(new Vec3(g0.x, RuneKit.groundAt(lv, V.up(g0, 0.5), g0.y), g0.z), 0.05), 1.6, j, 0.4, Opt.empty());
                        // 0.51.0 무리의 마지막 내려침: 땅을 타고 앞으로 달리는 세로 도끼 검기 (사용자 "커다란 검기")
                        JobFx.groundWave(lv, sp, V.add(sp.position(), Kit.facingOf(sp), 0.8), Kit.facingOf(sp), 1.7, 5.0, 24, j);
                        SkillFx.play(lv, "entity.player.attack.strong", g0, 0.7, 0.9);
                    }
                });
            }
        }
        return true;
    }

    // ------------------------------------------------------------ 걸어 당기기

    /**
     * 걸어 당기기 (롤 다리우스 E): 도끼를 오른쪽에서 크게 휘둘러 날 끝을 range 칸 · arc 도 안의 적 (결투 상대 먼저) 에게 걺 (1틱 — 걸린 자리 갈고리 판 · 손에서
     * 이어지는 붉은 줄 · 쇠사슬 소리) → 2틱부터 pullTicks 틱에 내 앞 front 칸으로 끌어옴 (밀 수 있는 적만 · 끌리는 땅의 흙먼지) → hitAt 틱에 내려찍음 (total · stun 틱
     * 기절 — 보스 제외). 걸린 적이 결투 상대가 됨 (JobKit — 결투가 없으면). 적이 없으면 허공을 휘두름
     */
    static boolean hook(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        double range = k.num("range", 6);
        LivingEntity t = pick(sp, range, k.num("arc", 70));
        String life = RuneKit.lifeToken(sp);
        int pullTicks = Math.max(2, (int) k.num("pullTicks", 4));
        int hitAt = Math.max(pullTicks + 2, (int) k.num("hitAt", 6));
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        // 크게 휘두름 (1인칭 눈앞 넓은 가로 베기 · 3인칭 앞의 판 · 0.51.0 날 끝을 따라 뻗는 큰 도끼 검기)
        JobFx.slash(sp, j, 2.4, 0.1, 3.0, 8.0, 0.24);
        JobFx.slashCard(level, V.up(V.add(sp.position(), f, 1.5), 1.2), f, 8.0, 4.2, 0.26, V.ZERO, j, JobFx.hide1p(sp));
        JobFx.slashWave(sp, j, 8.0, 2.2, range, 30);
        SkillFx.play(level, "entity.player.attack.sweep", sp.position(), 0.7, 0.9);
        SkillFx.play(level, "item.trident.throw", sp.position(), 0.6, 0.7);
        if (t == null) return true;
        RpgCombat.setSteadfast(sp, hitAt + 4);
        RuneKit.later(1, () -> {
            if (!RuneKit.sameLife(sp, life) || t.isRemoved()) return;
            ServerLevel lv = sp.level();
            Vec3 b = Kit.bodyPoint(t);
            Vec3 dir = V.unit(V.sub(b, sp.getEyePosition()));
            JobFx.slashCard(lv, b, V.scale(dir, -1), -60.0, 2.6, 0.3, RuneKit.entVel(t), j, Opt.empty());
            JobFx.line(lv, V.up(RuneKit.handOf(sp).hand(), 0.1), b, j, 0.3, Opt.empty());
            JobFx.two(lv, "jimpact", JobImpact.IMPACT, b, 0.25, Opt.of("size", 0.9, "spin", Kit.rand() * 360, "fade", 0.3), JobFx.outer(j), WHITE);
            SkillFx.play(lv, "block.chain.hit", b, 0.8, 1.0);
            SkillFx.play(lv, "item.armor.equip_chain", b, 0.7, 0.8);
        });
        if (JobKit.pushable(t)) {
            Vec3 start = t.position();
            for (int i = 1; i <= pullTicks; i++) {
                int ii = i;
                RuneKit.later(1 + i, () -> {
                    if (!RuneKit.sameLife(sp, life) || t.isRemoved() || !t.isAlive()) return;
                    Vec3 ff = Kit.facingOf(sp);
                    Vec3 end = V.add(sp.position(), ff, k.num("front", 1.6) + Kit.bodyPad(t));
                    double u = ii / (double) pullTicks;
                    u = 1 - (1 - u) * (1 - u);
                    Vec3 p = V.lerp(start, end, u);
                    Vec3 stand = Kit.standSpot(t.level(), new Vec3(p.x, Math.max(p.y, start.y) + 0.5, p.z), 1.5);
                    if (stand != null) t.teleportTo(stand.x, stand.y, stand.z);
                    t.setDeltaMovement(Vec3.ZERO);
                    if (t.level() instanceof ServerLevel lv) {
                        SkillFx.sparks(lv, V.up(t.position(), 0.1), DUST, 3, 2.0);
                        JobFx.line(lv, V.up(RuneKit.handOf(sp).hand(), 0.1), Kit.bodyPoint(t), j, 0.12, Opt.empty());
                    }
                });
            }
        }
        // 머리 위로 (hitAt − 2) → 내려찍음
        RuneKit.later(hitAt - 2, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.slash(sp, j, 1.9, -0.7, 1.3, 180.0, 0.14);
        });
        RuneKit.later(hitAt, () -> {
            if (!RuneKit.sameLife(sp, life) || t.isRemoved() || !t.isAlive()) return;
            ServerLevel lv = sp.level();
            Vec3 b = Kit.bodyPoint(t);
            JobFx.slash(sp, j, 2.2, 0.15, 2.4, 95.0, 0.24);
            JobFx.slashCard(lv, b, V.unit(V.sub(b, sp.getEyePosition())), 95.0, 3.4, 0.28, RuneKit.entVel(t), j, JobFx.hide1p(sp));
            JobFx.bigWave(lv, null, b, Kit.facingOf(sp), 0, 1.6 + Kit.bodyPad(t) * 0.5, 90, 0.3, j, Opt.empty()); // 0.51.0 내려찍은 자리의 세로 도끼 검기
            Vec3 g = t.position();
            JobFx.smash(lv, V.up(new Vec3(g.x, RuneKit.groundAt(lv, V.up(g, 0.5), g.y), g.z), 0.05), 1.8, j, 0.45, Opt.empty());
            JobKit.strike(sp, t, j, P, k.total(), false, false, life, 0, d -> {
                JobImpact.hit(sp, t, j, 1.6);
                if (!JobKit.isBoss(t)) RuneKit.stun(t, (int) k.num("stun", 10), JobFx.outer(j), true);
            });
        });
        return true;
    }

    // ------------------------------------------------------------ 여덟 팔

    /**
     * 여덟 팔 (롤 브라이어 W — 스타르카드의 여덟 팔): 2초 동안 결투 상대 (없으면 앞 reach 칸 · arc 도의 적) 를 쉬지 않고 벰 — 몸 둘레에 여덟 팔의 도끼 잔상
     * (rfx_jarms — 3인칭 · 다른 사람, 1인칭은 눈앞 도끼질이 쉬지 않음). first 틱부터 every 틱마다 한 무리 (volleys — 피해는 무리마다 한 번 · 숫자 lines 줄 lineGap
     * 틱마다 · 줄마다 도끼질 그림), 마지막 무리는 엇갈려 크게 (X) · 맞은 적 breakFor 틱 받는 피해 + break (JobTraitsMore 방어 깨짐)
     */
    static boolean eight(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        List<Double> volleys = SwordmasterSkills.numbers(k, "volleys", List.of(2.6, 2.6, 2.6, 3.9));
        int lines = Math.max(1, (int) k.num("lines", 4));
        int lineGap = Math.max(1, (int) k.num("lineGap", 2));
        int first = Math.max(0, (int) k.num("first", 4));
        int every = Math.max(10, (int) k.num("every", 10));
        double reach = k.num("reach", 5);
        double arc = k.num("arc", 120);
        String life = RuneKit.lifeToken(sp);
        LivingEntity[] target = {pick(sp, reach, arc)};
        int n = volleys.size();
        int end = first + (n - 1) * every + lines * lineGap;
        RpgCombat.setSteadfast(sp, end + 4);
        ServerLevel level0 = sp.level();
        SkillFx.play(level0, "mob.ravager.roar", sp.position(), 1.5, 0.5);
        SkillFx.play(level0, "item.armor.equip_iron", sp.position(), 0.9, 0.8);
        JobFx.aura(sp, j, 2.8, 0.5, 0.7);
        // 여덟 팔의 잔상 (몸 가운데 — 10틱마다 다시 놓아 몸을 따라감)
        for (int t = 0; t < end; t += 10) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 c = V.up(V.add(sp.position(), Kit.facingOf(sp), -0.15), 1.25);
                JobFx.arms(sp.level(), c, 1.55, Math.min(0.62, (end - tt) / 20.0 + 0.05), 16, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.92, "phase",
                        (double) (tt / 10 * 3 % JobFx.ARMS)), j);
            });
        }
        for (int v = 0; v < n; v++) {
            int vv = v;
            boolean finale = v == n - 1;
            int at = first + v * every;
            double mult = volleys.get(v);
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                if (!inReach(sp, target[0], reach)) target[0] = pick(sp, reach, arc);
                LivingEntity e = target[0];
                if (e == null) return;
                JobKit.strikeSplit(sp, e, j, P, mult, lines, lineGap, null, false, finale, life, 0, d -> {
                    if (!finale) return;
                    double add = JobUpgrades.num(sp, k, "break", k.num("break", 0.1));
                    JobTraitsMore.applyBreak(e, JobKit.isBoss(e) ? add * 0.5 : add, (int) k.num("breakFor", 80));
                    JobFx.breakMark(e, j, 0.55, 0.7, 1.0);
                });
            });
            for (int l = 0; l < lines; l++) {
                int ll = l;
                RuneKit.later(at + l * lineGap, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    LivingEntity e = inReach(sp, target[0], reach) ? target[0] : null;
                    boolean big = finale && ll == lines - 1;
                    double tilt = (vv * lines + ll) % 2 == 0 ? 25 + Kit.rand() * 40 : 155 + Kit.rand() * 40;
                    chopFx(sp, j, e, big ? 45 : tilt, big ? 2.8 : 1.8, big ? 1.9 : 1.0 + 0.05 * ll, finale ? big : ll == 0); // 마지막 묶음은 끝 줄만 멈춤 (JobImpact 간격 8틱)
                    if (big) chopFx(sp, j, e, 135, 2.8, 1.9, false);
                    SkillFx.play(sp.level(), "item.armor.equip_iron", sp.position(), 1.6 + Kit.rand() * 0.3, 0.25);
                });
            }
        }
        return true;
    }

    // ------------------------------------------------------------ 액스 그라인더

    /** 도는 도끼 (환영 도끼 판 — 제 판 안에서 구름): from → to 를 secs 초 (to 가 null 이면 그 자리에서 life 초 돎) */
    private static void axeSpin(ServerLevel level, Vec3 from, Vec3 to, double secs, double life, double scale, Jobs.Job j) {
        Vec3 d = to == null ? V.ZERO : V.sub(to, from);
        double len = V.len(d);
        Vec3 u = len < 1e-3 ? new Vec3(1, 0, 0) : V.scale(d, 1 / len);
        Opt b = Opt.of("dir", u, "face", JobFx3d.sideOf(u), "spin", -1440.0, "spinAxis", "z", "scale", scale, "life", Math.max(life, secs), "fadeIn", 0.03, "fadeOut",
                0.08, "glowAlpha", 0.55, "shine", 0.35);
        if (to != null && len > 1e-3) b = b.with("to", to, "ctrl", V.up(V.lerp(from, to, 0.5), Math.min(1.2, len * 0.08)), "travel", secs);
        JobFx3d.spawn(level, "phantom_axe", from, j, b);
    }

    /**
     * 액스 그라인더 (롤 올라프 Q — 2026-11-05 사용자 "구지 신체 강화 스킬도 필수로 안넣어도 될거 같다" · "직업 중에 구지 강화 시키면 어색한 애들은 빼자" ·
     * "딜링기도 괜찮고", 불사의 분노 대신): 도끼를 오른 어깨 뒤로 감았다가 throwAt 틱에 결투 상대 (없으면 앞 range 칸 · 40 도 안의 가장 가까운 적) 에게 회전시켜 던짐
     * → hitAt 틱에 박힘 (hits[0]) → every 틱마다 박힌 채 갈아냄 (hits[1] · hits[2] — 숫자 split 줄씩 · 피 튀김 · 느려짐) → 뽑히며 핏빛으로 터짐 (hits[3] 큰 숫자) ·
     * 도끼는 손으로 돌아옴 (받는 동작). 노릴 적이 없으면 앞 10칸으로 날았다 돌아옴 (피해 없음). 결투 상대가 없으면 맞힌 적과 결투 (JobKit.hit → JobTraits6.duelMult).
     * 몸 쓰는 직업 기준 (제 도끼를 던짐 — 던지는 무기 동작)
     */
    static boolean grinder(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int throwAt = Math.max(1, (int) k.num("throwAt", 4));
        int hitAt = Math.max(throwAt + 2, (int) k.num("hitAt", 8));
        int every = Math.max(10, (int) k.num("every", 10));
        List<Double> hl = k.def().numbersOrNull("hits");
        double[] hits = hl == null || hl.size() < 4 ? new double[] {3.0, 3.6, 3.6, 5.4} : new double[] {hl.get(0), hl.get(1), hl.get(2), hl.get(3)};
        int split = Math.max(1, (int) k.num("split", 4));
        double range = k.num("range", 12);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        LivingEntity t = pick(sp, range, 40);
        Vec3 f = Kit.facingOf(sp);
        // 감음: 오른 어깨 뒤로 당긴 도끼날에 빛
        JobFx.charge(sp, j, new Vec3(0.55, 1.7, -0.5), throwAt, 0.7);
        SkillFx.play(level0, "item.trident.return", sp.position(), 0.8, 0.6);
        int fly = hitAt - throwAt;
        int back = 6;
        int ripAt = hitAt + 3 * every;
        if (t == null) {
            RuneKit.later(throwAt, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                RuneKit.Hand h = RuneKit.handOf(sp);
                Vec3 from = V.up(h.hand(), 0.3);
                Vec3 to = V.up(V.add(sp.position(), f, Math.max(2, RuneKit.rayLength(sp.level(), from, f, 10))), 1.2);
                axeSpin(sp.level(), from, to, 0.3, 0.3, 1.9, j);
                SkillFx.play(sp.level(), "item.trident.throw", from, 0.7, 1.0);
                RuneKit.later(6, () -> {
                    if (RuneKit.sameLife(sp, life)) axeSpin(sp.level(), to, V.up(RuneKit.handOf(sp).hand(), 0.3), 0.3, 0.3, 1.9, j);
                });
                JobKit.motionLater(sp, k.id() + ":catch", 10, life);
            });
            return true;
        }
        RuneKit.later(throwAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            RuneKit.Hand h = RuneKit.handOf(sp);
            Vec3 from = V.up(h.hand(), 0.3);
            axeSpin(sp.level(), from, Kit.bodyPoint(t), fly / 20.0, fly / 20.0, 2.1, j); // 영상 1: 1.6 이면 멀리서 작은 붉은 표로만 보였음
            JobFx.dash(sp.level(), V.up(sp.position(), 1.3), Kit.bodyPoint(t), j, 0.25, JobFx.hide1p(sp).with("alpha", 0.6));
            SkillFx.play(sp.level(), "item.trident.throw", from, 0.6, 1.0);
            SkillFx.play(sp.level(), "entity.player.attack.sweep", from, 0.8, 0.7);
        });
        // 박힘 (hitAt): 큰 타격 · 핏빛 튐 · 숫자 한 줄
        JobKit.strike(sp, t, j, P, hits[0], false, false, life, hitAt, d -> {
            JobImpact.hit(sp, t, j, 1.6);
            BloodknightSkills.splash(sp.level(), Kit.bodyPoint(t), 0.8, j, 4);
            SkillFx.play(sp.level(), "item.trident.hit", t.position(), 0.7, 1.0);
        });
        // 박힌 채 갈아냄 (hitAt ~ ripAt): 그 자리에서 도는 도끼 (적을 따라감 — every 틱마다 다시) · 불티 · 느려짐
        for (int q = 0; q < 3; q++) {
            int at = hitAt + q * every;
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life) || t.isRemoved() || !t.isAlive()) return;
                axeSpin(sp.level(), Kit.bodyPoint(t), null, 0, every / 20.0 + 0.03, 2.0, j);
                RuneKit.effect(t, "slowness", every + 2, (int) k.num("slowAmp", 2));
            });
        }
        for (int q = 1; q <= 2; q++) {
            int at = hitAt + q * every;
            double m = hits[q];
            JobKit.strikeSplit(sp, t, j, P, m, split, 2, false, false, life, at, d -> {
                JobImpact.fx(sp, t, j, 1.1);
                Vec3 b = Kit.bodyPoint(t);
                SkillFx.sparks(sp.level(), b, JobFx.inner(j), 10, 5.0);
                BloodknightSkills.splash(sp.level(), b, 0.6, j, 3);
                SkillFx.play(sp.level(), "entity.player.attack.sweep", b, 1.4, 0.6);
                SkillFx.play(sp.level(), "block.grindstone.use", b, 1.2, 0.6);
            });
        }
        // 뽑힘 (ripAt): 핏빛 터짐 · X 베기 판 · 큰 숫자 → 도끼가 손으로 돌아옴
        JobKit.strike(sp, t, j, P, hits[3], false, true, life, ripAt, d -> {
            ServerLevel lv = sp.level();
            Vec3 b = Kit.bodyPoint(t);
            JobImpact.hit(sp, t, j, 2.0);
            JobFx.burst(lv, b, 1.6, j, 0.35, Opt.empty());
            JobFx.cross(lv, b, 1.1 + Kit.bodyPad(t) * 0.5, j, 0.3, Opt.empty());
            BloodknightSkills.splash(lv, b, 1.2, j, 8);
            SkillFx.play(lv, "random.explode", b, 1.3, 0.5);
        });
        RuneKit.later(ripAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 from = t.isRemoved() ? V.up(V.add(sp.position(), f, 3), 1.2) : Kit.bodyPoint(t);
            axeSpin(sp.level(), from, V.up(RuneKit.handOf(sp).hand(), 0.3), back / 20.0, back / 20.0, 1.9, j);
            SkillFx.play(sp.level(), "item.trident.return", from, 0.9, 0.8);
        });
        JobKit.motionLater(sp, k.id() + ":catch", ripAt + back - 2, life);
        return true;
    }

    // ------------------------------------------------------------ 홀름강

    /**
     * 홀름강 (롤 모데카이저 R — 둘만의 결투): range 칸 · arc 도 안의 적 (결투 상대 먼저 — 없으면 쓰지 않음) 과 둘만의 결투장 — 둘 사이 땅을 가운데로 둘레 radius 칸,
     * dur 틱 (어스름한 붉은 하늘 · 결투장 바닥 rfx_jarena · 둘레의 돌 말뚝 · 성벽). 그동안 상대는 나가지 못하고 다른 적은 들어오지 못함 (밀 수 있는 적만 — 나도
     * 나가지 못함) · 상대 받는 피해 + vuln (JobTraits6.arena). 그 적이 결투 상대 (결투 그대로). openAt 틱에 뛰어들어 내려침 (open) → dur 틱에 뛰어올라 크게 내려찍음
     * (slam — 상대 체력이 below 아래면 두 배 · 큰 숫자) · 결투장이 무너짐. 상대가 먼저 쓰러지면 결투장이 바로 무너짐
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        LivingEntity t = pick(sp, k.num("range", 8), k.num("arc", 90));
        if (t == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 결투할 적이 없습니다 (앞 " + Math.round(k.num("range", 8)) + "칸)", 30);
            return false;
        }
        int dur = Math.max(40, (int) k.num("dur", 140));
        int openAt = Math.max(4, (int) k.num("openAt", 10));
        double R = k.num("radius", 7);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level = sp.level();
        Vec3 m = V.lerp(sp.position(), t.position(), 0.5);
        Vec3 cg = new Vec3(m.x, RuneKit.groundAt(level, V.up(m, 0.6), sp.getY()), m.z);
        if (JobTraits6.duelTarget(sp) != t) JobTraits6.startDuel(sp, j, t, JobTraits6.duelStacks(sp), false);
        JobTraits6.arena(t, k.num("vuln", 0.15), dur + 2);
        Kit.noFall(sp, dur + 30);
        Ult.sky(level, cg, "rpg:ult_holmgang", dur + 24);
        Ult.flash(level, cg, JobFx.outer(j), Opt.of("radius", R, "time", new double[] {0.03, 0.06, 0.35}), sp);
        // 결투장: 바닥 (그어지며 펴짐 → 맥동) · 둘레의 성벽 (솟음 → 서 있음) · 여덟 돌 말뚝
        Vec3 floor = V.up(cg, 0.04);
        double spin = Kit.rand() * 360;
        JobFx.arena(level, floor, R, 0, 0.5, Opt.of("spin", spin), j);
        // 바닥 · 성벽 · 말뚝은 마지막 내려찍기까지 짙게 (흐려지는 몫을 짧게 — 영상 1 은 끝 2초가 흐렸음) → 무너질 때 collapse
        RuneKit.later(9, () -> JobFx.arena(level, floor, R, 1, (dur - 9 + 4) / 20.0, Opt.of("spin", spin, "fade", 0.08), j));
        int walls = Math.max(12, (int) Math.round(2 * Math.PI * R / 2.6));
        for (int i = 0; i < walls; i++) {
            double a = i * 2 * Math.PI / walls;
            Vec3 p = new Vec3(cg.x + Math.cos(a) * R, cg.y, cg.z + Math.sin(a) * R);
            Vec3 g = new Vec3(p.x, RuneKit.groundAt(level, V.up(p, 1.0), cg.y), p.z);
            int ii = i;
            RuneKit.later(i % 4, () -> JobFx.wall(level, g, 0.8, j, 0.35, (dur + 2) / 20.0 - ii % 4 / 20.0, Opt.of("fade", 0.08)));
        }
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 + 22.5);
            Vec3 p = new Vec3(cg.x + Math.cos(a) * (R - 0.2), cg.y, cg.z + Math.sin(a) * (R - 0.2));
            Vec3 g = new Vec3(p.x, RuneKit.groundAt(level, V.up(p, 1.0), cg.y), p.z);
            double size = 1.0;
            JobFx.cells(level, "jstake", JobTraits6.STAKE, 0, 1, V.up(g, size), 0.3, 0, false, Opt.of("size", size, "fade", 0.0), JobTraits6.WOOD, JobFx.outer(j));
            RuneKit.later(5, () -> JobFx.cells(level, "jstake", JobTraits6.STAKE, 1, 4, V.up(g, size), (dur - 5 + 2) / 20.0, 6, true, Opt.of("size", size, "fade",
                    0.06), JobTraits6.WOOD, JobFx.outer(j)));
        }
        SkillFx.sparks(level, V.up(cg, 0.3), DUST, 16, 4);
        SkillFx.play(level, "event.raid.horn", cg, 1.0, 0.8);
        SkillFx.play(level, "block.anvil.land", cg, 0.6, 0.8);
        SkillFx.play(level, "mob.ravager.roar", cg, 0.8, 0.9);
        Notify.notify(sp, "§c§l홀름강 §7— " + dur / 20 + "초 둘만의 결투 · 상대 받는 피해 +" + Math.round(k.num("vuln", 0.15) * 100) + "%", 40);
        boolean[] over = {false};
        confine(sp, t, cg, R, dur, life, over, j);
        // 뛰어들어 내려침 (openAt − 5 부터 상대 곁으로)
        RuneKit.later(Math.max(1, openAt - 5), () -> {
            if (over[0] || !RuneKit.sameLife(sp, life) || t.isRemoved()) return;
            double d = Math.hypot(t.getX() - sp.getX(), t.getZ() - sp.getZ());
            if (d > 2.6 + Kit.bodyPad(t)) JobMove.run(sp, JobMove.hop(0, 4, from -> beside(sp, t, from), 1.2));
        });
        RuneKit.later(openAt, () -> {
            if (over[0] || !RuneKit.sameLife(sp, life) || t.isRemoved() || !t.isAlive()) return;
            ServerLevel lv = sp.level();
            Vec3 b = Kit.bodyPoint(t);
            JobFx.slash(sp, j, 2.2, 0.15, 2.6, 95.0, 0.26);
            JobFx.slashCard(lv, b, V.unit(V.sub(b, sp.getEyePosition())), 95.0, 3.6, 0.3, RuneKit.entVel(t), j, JobFx.hide1p(sp));
            JobFx.bigWave(lv, null, b, Kit.facingOf(sp), 0, 1.8 + Kit.bodyPad(t) * 0.5, 90, 0.32, j, Opt.empty()); // 0.51.0 뛰어든 내려침의 세로 도끼 검기
            Vec3 g = t.position();
            JobFx.smash(lv, V.up(new Vec3(g.x, RuneKit.groundAt(lv, V.up(g, 0.5), g.y), g.z), 0.05), 2.4, j, 0.5, Opt.empty());
            SkillFx.play(lv, "block.anvil.land", g, 0.9, 0.8);
            JobKit.strike(sp, t, j, P, k.num("open", 12), false, false, life, 0, d -> JobImpact.hit(sp, t, j, 1.9));
        });
        // 끝: 뛰어올라 (dur − 8) 크게 내려찍음 (dur)
        RuneKit.later(dur - 8, () -> {
            if (over[0] || !RuneKit.sameLife(sp, life) || t.isRemoved() || !t.isAlive()) return;
            JobKit.motion(sp, k.id() + ":slam");
            JobMove.run(sp, JobMove.hop(0, 8, from -> beside(sp, t, from), 3.0));
            SkillFx.play(sp.level(), "mob.breeze.jump", sp.position(), 0.7, 1.0);
            SkillFx.sparks(sp.level(), V.up(sp.position(), 0.2), DUST, 10, 3.5);
        });
        RuneKit.later(dur - 3, () -> {
            if (!over[0] && RuneKit.sameLife(sp, life)) JobFx.slash(sp, j, 1.9, -0.75, 1.4, 180.0, 0.16);
        });
        RuneKit.later(dur, () -> {
            if (over[0] || !RuneKit.sameLife(sp, life)) return;
            over[0] = true;
            finale(sp, j, k, P, t, cg, R, life);
        });
        return true;
    }

    /** 상대 곁 (상대에서 나 쪽으로 1.5칸 — 몸 크기만큼 더) 의 설 자리 (없으면 그대로) */
    private static Vec3 beside(ServerPlayer sp, LivingEntity t, Vec3 from) {
        Vec3 d = V.flat(V.sub(from, t.position()));
        Vec3 dir = V.len(d) < 1e-3 ? V.scale(Kit.facingOf(sp), -1) : V.unit(d);
        Vec3 want = V.add(t.position(), dir, 1.5 + Kit.bodyPad(t));
        Vec3 stand = Kit.standSpot(sp.level(), new Vec3(want.x, Math.max(want.y, from.y) + 0.5, want.z), 2.5);
        return stand != null ? stand : from;
    }

    /**
     * 결투장 (2틱마다 dur 까지): 상대는 안으로 되돌림 · 다른 적은 바깥으로 밀어냄 (밀 수 있는 적만) · 나도 나가지 못함 · 둘레의 불티 (10틱마다). 상대가 쓰러지면 결투장이
     * 바로 무너짐 (over)
     */
    private static void confine(ServerPlayer sp, LivingEntity t, Vec3 cg, double R, int dur, String life, boolean[] over, Jobs.Job j) {
        for (int tick = 2; tick < dur; tick += 2) {
            int tt = tick;
            RuneKit.later(tick, () -> {
                if (over[0] || !RuneKit.sameLife(sp, life)) return;
                ServerLevel level = sp.level();
                if (t.isRemoved() || !t.isAlive()) {
                    over[0] = true;
                    collapse(level, cg, R, j);
                    JobTraits6.arenaEnd(t);
                    return;
                }
                double dt = Math.hypot(t.getX() - cg.x, t.getZ() - cg.z);
                if (dt > R - 0.6 && JobKit.pushable(t)) {
                    double s = (R - 0.8) / dt;
                    t.teleportTo(cg.x + (t.getX() - cg.x) * s, t.getY(), cg.z + (t.getZ() - cg.z) * s);
                    t.setDeltaMovement(Vec3.ZERO);
                    if (RuneKit.fxQuota("holmWall", 4)) SkillFx.sparks(level, Kit.bodyPoint(t), JobFx.inner(j), 5, 2.5);
                }
                for (LivingEntity e : Kit.around(sp, V.up(cg, 1.0), R + 1.2, 3.5)) {
                    if (e == t || !JobKit.pushable(e)) continue;
                    double d = Math.hypot(e.getX() - cg.x, e.getZ() - cg.z);
                    if (d >= R + 1.0) continue;
                    Vec3 out = d < 1e-3 ? Kit.facingOf(sp) : new Vec3((e.getX() - cg.x) / d, 0, (e.getZ() - cg.z) / d);
                    e.teleportTo(cg.x + out.x * (R + 1.1), e.getY(), cg.z + out.z * (R + 1.1));
                    e.setDeltaMovement(out.x * 0.25, 0.15, out.z * 0.25);
                    if (RuneKit.fxQuota("holmWall", 4)) SkillFx.sparks(level, Kit.bodyPoint(e), JobFx.inner(j), 5, 2.5);
                }
                double dp = Math.hypot(sp.getX() - cg.x, sp.getZ() - cg.z);
                if (dp > R - 0.5) {
                    double s = (R - 0.7) / dp;
                    Kit.moveTo(sp, new Vec3(cg.x + (sp.getX() - cg.x) * s, sp.getY(), cg.z + (sp.getZ() - cg.z) * s));
                }
                if (tt % 10 == 0) {
                    for (int i = 0; i < 6; i++) {
                        double a = Kit.rand() * Math.PI * 2;
                        Vec3 p = new Vec3(cg.x + Math.cos(a) * R, cg.y + 0.2 + Kit.rand() * 1.4, cg.z + Math.sin(a) * R);
                        SkillFx.fx(level, "rpg:arrow_spark", p, Opt.of("dir", V.UP, "speed", 1.6, "size", 0.14, "grow", 0.5, "life", 0.6, "color", JobFx.outer(j), "alpha",
                                1.0));
                    }
                }
            });
        }
    }

    /** 결투장이 무너짐 (그림만): 바닥이 흐려지고 · 둘레의 돌이 튀고 · 흙먼지 */
    private static void collapse(ServerLevel level, Vec3 cg, double R, Jobs.Job j) {
        JobFx.arena(level, V.up(cg, 0.05), R, 2, 0.5, Opt.empty(), j);
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5;
            Vec3 p = new Vec3(cg.x + Math.cos(a) * R, cg.y + 0.4, cg.z + Math.sin(a) * R);
            RuneKit.rockBurst(level, p, 2, 3.0, 0.16);
            SkillFx.sparks(level, p, STONE, 4, 2.5);
        }
        SkillFx.play(level, "block.stone.break", cg, 0.6, 1.0);
    }

    /** 마지막 내려찍기 (dur): 상대 체력이 below 아래면 두 배 · 큰 숫자 (결투장 안 — 받는 피해 + vuln) · 결투장이 무너짐 */
    private static void finale(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, LivingEntity t, Vec3 cg, double R, String life) {
        ServerLevel level = sp.level();
        collapse(level, cg, R, j);
        // 결투장 (받는 피해 + vuln) 은 dur + 2 틱에 저절로 걷힘 — 마지막 내려찍기도 결투장 안
        if (t.isRemoved() || !t.isAlive()) return;
        Vec3 b = Kit.bodyPoint(t);
        Vec3 g = new Vec3(t.getX(), RuneKit.groundAt(level, V.up(t.position(), 0.5), t.getY()), t.getZ());
        boolean low = t.getHealth() < t.getMaxHealth() * k.num("below", 0.3);
        JobFx.slash(sp, j, 2.3, 0.1, 3.2, 95.0, 0.3);
        JobFx.slashCard(level, b, V.unit(V.sub(b, sp.getEyePosition())), 95.0, 4.4, 0.34, V.ZERO, j, JobFx.hide1p(sp));
        // 0.51.0 마지막 내려찍기: 상대 위의 거대한 세로 도끼 검기 · 땅을 타고 사방으로 달리는 검기 넷 (사용자 "커다란 검기")
        Vec3 ff = V.flat(V.sub(t.position(), sp.position()));
        if (V.len(ff) < 1e-3) ff = Kit.facingOf(sp);
        ff = V.unit(ff);
        JobFx.bigWave(level, null, b, ff, 0, 2.6 + Kit.bodyPad(t) * 0.6, 90, 0.4, j, Opt.empty());
        for (int i = 0; i < 4; i++) JobFx.groundWave(level, null, g, V.turnY(ff, 45 + i * 90), 1.6, 5.5, 22, j);
        JobFx.smash(level, V.up(g, 0.05), 4.0, j, 0.7, Opt.empty());
        JobFx.burst(level, V.up(g, 0.8), 2.2, j, 0.45, Opt.empty());
        JobFx.ring(level, V.up(g, 0.08), 4.0, j, false, 0.45, Opt.of("alpha", 1.0));
        RuneKit.godRays(sp, level, g, 2.0, 6, JobFx.outer(j), JobFx.inner(j), Opt.of("h", 8.0, "life", 0.7, "alpha", 0.8, "skipNear", true));
        RuneKit.rockBurst(level, V.up(g, 0.3), 12, 5.5, 0.2);
        SkillFx.sparks(level, V.up(g, 0.5), JobFx.inner(j), 20, 7);
        Ult.flash(level, g, JobFx.outer(j), Opt.empty(), sp);
        SkillFx.play(level, "item.mace.smash_ground_heavy", g, 0.6, 1.0);
        SkillFx.play(level, "random.explode", g, 0.8, 0.8);
        SkillFx.play(level, "event.raid.horn", g, 1.3, 0.5);
        double mult = k.num("slam", 40) * (low ? 2 : 1);
        JobKit.strike(sp, t, j, P, mult, false, true, life, 0, d -> {
            JobImpact.hit(sp, t, j, low ? 2.8 : 2.4);
            if (low) Notify.notify(sp, "§c§l홀름강 §7— 쓰러지기 직전의 상대에게 두 배", 24);
        });
    }
}
