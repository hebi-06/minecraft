package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgHealth;
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
 * 0.51.0 2차 직업 — 매지션 각성 아크메이지 스킬 (jobs.json advance.arcmage — 파랑 · 금빛 · 밤하늘 자주, 특성 「주문 연쇄」 최대 5 · 별자리 — JobTraits8).
 * 재설계안 2판 (2026-11-06 사용자 "다음 작업" — 롤 참고): 스타 체인 (브랜드 R — 적 사이를 튀는 별빛 번개) · 헤일로 배리어 (럭스 W — 던졌다 되돌려 받는 별빛 고리 · 보호막) ·
 * 스타 볼텍스 (아우렐리온 솔 E — 끌어당기는 별의 소용돌이 · 무너지며 처형) · 스펠 서지 (라이즈 Q — 다른 스킬마다 스타 체인이 돌아옴) · 셀레스티얼 이글 (아우렐리온 솔 R —
 * 별빛 독수리가 하늘을 돌며 깃털을 쏟고 내리꽂힘). 모두 마법 피해. 연출 = 2D 그림 (JobFx — 별 rfx_jstarnode · 별똥 rfx_jfallstar · 빛 고리 rfx_jhalo · 별의 소용돌이
 * rfx_jgalaxy · 별빛 독수리 rfx_jeagle). 화면 흔들림 없음 (사용자 "화면 흔들림은 넣지마 멀미나")
 */
final class ArcmageSkills {
    private ArcmageSkills() {}

    private static final double[] NIGHT = JobTraits8.NIGHT;
    private static final double[] GOLD = JobTraits8.GOLD;
    /** 별빛 번개 · 고리의 겉빛 (푸른 별빛) · 흰 금빛 심지 */
    private static final double[] STAR_BLUE = {0.45, 0.62, 1.0};
    private static final double[] STAR_CORE = {1.0, 0.95, 0.78};

    static void register() {
        JobSystem.register("arcmage_chain", ArcmageSkills::chain);
        JobSystem.register("arcmage_halo", ArcmageSkills::halo);
        JobSystem.register("arcmage_vortex", ArcmageSkills::vortex);
        JobSystem.register("arcmage_surge", ArcmageSkills::surge);
        JobSystem.register("arcmage_ult", ArcmageSkills::ult);
    }

    static Vec3 wandTip(ServerPlayer sp) {
        return MageSkills.wandTip(sp);
    }

    /** 조준한 곳의 땅 (겨눈 적이 있으면 그 발밑 — range 칸) */
    private static Vec3 groundAim(ServerPlayer sp, double range) {
        LivingEntity t = Kit.pickTarget(sp, range, 10);
        if (t != null) return t.position();
        Vec3 a = Kit.aimPoint(sp, range);
        return new Vec3(a.x, RuneKit.groundAt(sp.level(), V.up(a, 1.0), sp.getY()), a.z);
    }

    // ------------------------------------------------------------ 스타 체인

    /**
     * 스타 체인: 겨눈 적 (range 칸 · 없으면 쓰지 않음) 에게 hitAt 틱 별빛 번개 (first) → every 틱마다 jumps 번 튐 (jump — 지금 적 둘레 jumpRange 칸: 덜 맞은 적 먼저 ·
     * 가까운 순, 같은 적은 perFoe 번까지 · 다른 적이 없으면 그 적을 다시 — 위로 솟았다 내리꽂힘). 같은 적을 다시 칠 때는 무적 시간 (10틱) 뒤로 (뒤 튐도 함께 밀림). 길은 쓸 때
     * 자리로 정함. 튄 자리마다 별이 뜨고 금빛 선으로 이어져 별자리처럼 남음
     */
    static boolean chain(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        LivingEntity first = Kit.pickTarget(sp, k.num("range", 20), 12);
        if (first == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다", 30);
            return false;
        }
        int hitAt = Math.max(1, (int) k.num("hitAt", 2));
        int every = Math.max(1, (int) k.num("every", 3));
        int jumps = Math.max(0, (int) k.num("jumps", 5));
        int perFoe = Math.max(1, (int) k.num("perFoe", 2));
        double jumpRange = k.num("jumpRange", 6);
        List<LivingEntity> path = new ArrayList<>();
        List<Integer> when = new ArrayList<>();
        Map<UUID, Integer> count = new HashMap<>();
        Map<UUID, Integer> lastAt = new HashMap<>();
        path.add(first);
        when.add(hitAt);
        count.put(first.getUUID(), 1);
        lastAt.put(first.getUUID(), hitAt);
        LivingEntity cur = first;
        int t = hitAt;
        for (int i = 0; i < jumps; i++) {
            LivingEntity next = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity e : Kit.around(sp, cur.position(), jumpRange, 4)) {
                int c = count.getOrDefault(e.getUUID(), 0);
                if (c >= perFoe) continue;
                double score = V.len(V.sub(e.position(), cur.position())) + c * 100 + (e == cur ? 50 : 0);
                if (score < best) {
                    best = score;
                    next = e;
                }
            }
            if (next == null) break;
            t += every;
            Integer prev = lastAt.get(next.getUUID());
            if (prev != null) t = Math.max(t, prev + 10);
            path.add(next);
            when.add(t);
            count.merge(next.getUUID(), 1, Integer::sum);
            lastAt.put(next.getUUID(), t);
            cur = next;
        }
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        // 완드 끝에 별빛이 모임 (0) · 맑은 소리
        JobFx.starNode(level0, wandTip(sp), 0.32, 0, 2, hitAt / 20.0 + 0.1, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), NIGHT, GOLD);
        SkillFx.play(level0, "block.amethyst_block.chime", sp.position(), 1.5, 1.0);
        int end = when.get(when.size() - 1);
        List<Vec3> stars = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) {
            int ii = i;
            LivingEntity e = path.get(i);
            int at = when.get(i);
            // 번개: 맞기 1틱 전에 그음 (앞 적 → 이 적 · 처음은 완드 끝 → 첫 적)
            RuneKit.later(at - 1, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved() || !e.isAlive()) return;
                ServerLevel d = sp.level();
                Vec3 to = Kit.bodyPoint(e);
                Vec3 from = ii == 0 ? wandTip(sp) : Kit.bodyPoint(path.get(ii - 1));
                if (ii > 0 && path.get(ii - 1) == e) {
                    // 같은 적을 다시: 별빛이 위로 솟았다가 내리꽂힘
                    Vec3 top = V.up(to, 2.6 + Kit.bodyPad(e));
                    RuneKit.zap(d, to, top, STAR_BLUE, STAR_CORE, 0.2, 0.2);
                    RuneKit.zap(d, top, to, STAR_BLUE, STAR_CORE, 0.26, 0.28);
                    JobFx.starNode(d, top, 0.5, 0, 2, 0.25, false, Opt.empty(), NIGHT, GOLD);
                } else {
                    RuneKit.zap(d, from, to, STAR_BLUE, STAR_CORE, ii == 0 ? 0.28 : 0.22, 0.28);
                    RuneKit.streak(d, from, to, GOLD, 0.05, 0.45, 0.85, 0);
                }
                SkillFx.play(d, ii == 0 ? "entity.lightning_bolt.impact" : "block.amethyst_block.chime", to, ii == 0 ? 1.8 : 1.2 + 0.12 * ii, ii == 0 ? 0.5 : 0.9);
            });
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
                ServerLevel d = sp.level();
                Vec3 p = Kit.bodyPoint(e);
                stars.add(p);
                JobFx.starNode(d, p, 0.42 + Kit.bodyPad(e) * 0.25, 6, 2, 0.25, false, Opt.empty(), NIGHT, GOLD);
                JobFx.starNode(d, V.up(p, 0.2), 0.3, 2, 4, Math.max(0.3, (end - at) / 20.0 + 0.5), true, Opt.of("fade", 0.3), NIGHT, GOLD);
                SkillFx.sparks(d, p, GOLD, 6, 4);
                // 앞 별과 잇는 금빛 별자리 선 (끝날 때까지 남음)
                if (stars.size() >= 2) {
                    Vec3 a = stars.get(stars.size() - 2);
                    double keep = Math.max(0.3, (end - at) / 20.0 + 0.5);
                    RuneKit.streak(d, a, p, NIGHT, 0.2, keep, 0.45, 0);
                    RuneKit.streak(d, a, p, GOLD, 0.07, keep, 0.95, 0);
                }
            });
            double mult = i == 0 ? k.num("first", 1.4) : k.num("jump", 1.12);
            JobKit.strike(sp, e, j, P, mult, true, false, life, at, x -> {
                if (RuneKit.fxQuota("jobStarChain", 6)) JobFx.hit(e, j, 1.0);
            });
        }
        // 다 튀면 별자리가 한 번 반짝이고 사라짐
        RuneKit.later(end + 6, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            for (Vec3 p : stars) JobFx.starNode(d, V.up(p, 0.2), 0.36, 6, 2, 0.3, false, Opt.empty(), NIGHT, GOLD);
            if (!stars.isEmpty()) SkillFx.play(d, "block.amethyst_block.resonate", stars.get(stars.size() - 1), 1.4, 0.6);
        });
        return true;
    }

    // ------------------------------------------------------------ 헤일로 배리어

    /**
     * 헤일로 배리어: throwAt 틱에 별빛 고리 (rfx_jhalo — 나를 보도록 기울여 눕힘 · 가운데 별) 를 보는 쪽으로 던짐 — speed 칸/틱으로 dist 칸까지 나갔다가 hold 틱 멈춘 뒤 내게
     * 돌아옴 (내 가슴으로 — 움직여도 따라옴). 지나가는 적 (고리 둘레 radius 칸) 은 갈 때 out · 올 때 back 한 번씩, 고리가 지나간 아군 (둘레 allyRadius 칸) 과 돌려 받은 나에게
     * 보호막 (최대 체력 × shield · shieldFor 틱 — 한 번씩)
     */
    static boolean halo(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 c0 = V.up(sp.position(), 1.2);
        int throwAt = (int) k.num("throwAt", 4);
        double dist = Math.max(2, k.num("dist", 10)); // 벽을 지나감 (별빛 — 막히면 일찍 돌아와 올 때 맞는 틱이 흔들림)
        double speed = Math.max(0.2, k.num("speed", 1.25));
        int hold = (int) k.num("hold", 2);
        double radius = k.num("radius", 1.4);
        double allyR = k.num("allyRadius", 1.8);
        String life = RuneKit.lifeToken(sp);
        // 완드 끝에 고리가 맺힘 (0 ~ throwAt)
        JobFx.ringFacing(level0, wandTip(sp), 0.35, j, V.scale(sp.getViewVector(1F), -1), throwAt / 20.0 + 0.1, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)));
        JobFx.starNode(level0, wandTip(sp), 0.26, 0, 2, throwAt / 20.0 + 0.1, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), NIGHT, GOLD);
        SkillFx.play(level0, "block.amethyst_block.resonate", sp.position(), 1.6, 0.9);
        Set<UUID> outHit = new HashSet<>();
        Set<UUID> backHit = new HashSet<>();
        Set<UUID> shielded = new HashSet<>();
        Vec3[] pos = {V.add(c0, f, 1.0)};
        int[] phase = {0}; // 0 나감 · 1 멈춤 · 2 돌아옴 · 3 끝
        int[] holdLeft = {hold};
        int maxTicks = 70;
        for (int tt = throwAt; tt <= throwAt + maxTicks; tt++) {
            int t = tt;
            RuneKit.later(t, () -> {
                if (phase[0] >= 3 || !RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                Vec3 p0 = pos[0];
                Vec3 p1 = p0;
                if (t == throwAt) {
                    SkillFx.play(d, "entity.allay.item_thrown", sp.position(), 1.3, 1.0);
                } else if (phase[0] == 0) {
                    double s = Math.min(dist, V.dot(V.sub(p0, c0), f) + speed);
                    p1 = V.add(c0, f, s);
                    if (s >= dist - 1e-6) phase[0] = 1;
                } else if (phase[0] == 1) {
                    if (--holdLeft[0] <= 0) {
                        phase[0] = 2;
                        SkillFx.play(d, "block.amethyst_cluster.break", p0, 1.6, 0.6);
                    }
                } else {
                    Vec3 me = V.up(sp.position(), 1.2);
                    Vec3 to = V.sub(me, p0);
                    double l = V.len(to);
                    p1 = l <= speed ? me : V.add(p0, V.scale(to, 1 / l), speed);
                }
                pos[0] = p1;
                // 고리 (2틱마다 — 다음 자리 쪽으로 움직이며 · 나를 보도록 기울임)
                if ((t - throwAt) % 2 == 0) {
                    Vec3 toMe = V.flat(V.sub(sp.position(), p1));
                    Vec3 face = V.unit(V.add(V.scale(V.len(toMe) > 1e-3 ? toMe : V.scale(f, -1), 0.8), V.UP, 0.6));
                    Vec3 vel = phase[0] == 1 ? V.ZERO : V.scale(V.sub(p1, p0), 20);
                    // 빛 고리 (rfx_jhalo 1 ~ 2 — 가장 밝은 두 장을 이어 돌림 · 고리 반지름 = 반 크기 × 0.88)
                    JobFx.cells(d, "jhalo", JobFx.HALO, 1, 2, p1, 0.16, 12, true, Opt.of("dir", face, "size", radius * 0.75 / 0.88, "vel", vel, "spin", (t * 37.0) % 360,
                            "fade", 0.0, "alpha", 0.95), JobFx.outer(j), JobFx.inner(j));
                    JobFx.starNode(d, p1, 0.32, 2, 4, 0.16, true, Opt.of("vel", vel, "fade", 0.0), NIGHT, GOLD);
                    SkillFx.sparks(d, p1, GOLD, 2, 1.5);
                }
                // 적: 갈 때 · 올 때 한 번씩
                for (LivingEntity e : Kit.around(sp, p1, radius + 1.2, 2.5)) {
                    if (V.len(V.sub(Kit.bodyPoint(e), p1)) > radius + Kit.bodyPad(e)) continue;
                    boolean back = phase[0] == 2;
                    if (!(back ? backHit : outHit).add(e.getUUID())) continue;
                    JobKit.strike(sp, e, j, P, back ? k.num("back", 2.1) : k.num("out", 2.1), true, false, life, 0, x -> {
                        if (RuneKit.fxQuota("jobHalo", 6)) JobFx.hit(e, j, 0.9);
                        if (e.level() instanceof ServerLevel lv) JobFx.starNode(lv, Kit.bodyPoint(e), 0.4, 6, 2, 0.22, false, Opt.empty(), NIGHT, GOLD);
                    });
                }
                // 아군: 고리가 지나가면 보호막 (나는 돌려 받을 때)
                for (ServerPlayer o : JobTraits4.allies(sp, p1, allyR)) {
                    if (o == sp) continue;
                    shield(sp, j, k, o, shielded);
                }
                if (phase[0] == 2 && V.len(V.sub(V.up(sp.position(), 1.2), p1)) < 0.9) {
                    phase[0] = 3;
                    shield(sp, j, k, sp, shielded);
                    JobFx.ring(d, V.up(sp.position(), 0.06), 1.6, j, false, 0.45, Opt.empty());
                    SkillFx.play(d, "block.amethyst_block.chime", sp.position(), 1.8, 1.0);
                    JobKit.motion(sp, "arcmage_halo:catch");
                }
            });
        }
        return true;
    }

    /** 보호막 하나 (사람마다 한 번): 최대 체력 × shield · shieldFor 틱 · 몸에 금빛 방패 그림 */
    private static void shield(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, ServerPlayer o, Set<UUID> done) {
        if (!done.add(o.getUUID())) return;
        double max = RpgHealth.maxOf(o);
        double amt = max * k.num("shield", 0.12);
        RpgHealth.addShield(o, amt, (int) k.num("shieldFor", 60), amt);
        ServerLevel d = sp.level();
        JobFx.aegis(d, V.up(o.position(), 1.1), 0.45, j, 0, 0.45, (o == sp ? JobFx.hide1p(sp) : Opt.empty()).with("vel", RuneKit.bodyVel(o)));
        JobFx.starNode(d, V.up(o.position(), 2.2), 0.3, 0, 2, 0.3, false, Opt.of("vel", RuneKit.bodyVel(o)), NIGHT, GOLD);
        SkillFx.play(d, "item.armor.equip_gold", o.position(), 1.4, 0.7);
    }

    // ------------------------------------------------------------ 스타 볼텍스

    /**
     * 스타 볼텍스: 조준한 곳 (range 칸 — 겨눈 적의 발밑) 에 formAt 틱 별의 소용돌이 (rfx_jgalaxy — 땅에 누워 돎) — 둘레 radius 칸의 밀 수 있는 적을 틱마다 pullSpeed 칸씩
     * 가운데로 끌어당김 (선 자리 옮김) · first 틱부터 every 틱마다 pulses 번 별빛 (pulse — 숫자 split 줄) → collapseAt 틱 한 점으로 무너지며 터짐 (collapse) — 그때 체력
     * execute 아래인 보스 아닌 적은 빨려 들어가 쓰러짐
     */
    static boolean vortex(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 c = groundAim(sp, k.num("range", 14));
        double radius = k.num("radius", 4.5);
        int formAt = (int) k.num("formAt", 4);
        int first = (int) k.num("first", 10);
        int every = Math.max(1, (int) k.num("every", 10));
        int pulses = Math.max(1, (int) k.num("pulses", 6));
        int collapseAt = (int) k.num("collapseAt", 70);
        double pull = k.num("pullSpeed", 0.16);
        String life = RuneKit.lifeToken(sp);
        double spin0 = Kit.rand() * 360;
        double rate = 110; // 도/초 (팔이 감기는 쪽)
        // 완드 끝 → 그곳으로 별빛 줄기 (0 ~ formAt)
        RuneKit.streak(level0, wandTip(sp), V.up(c, 0.4), GOLD, 0.06, 0.25, 0.9, 0);
        RuneKit.streak(level0, wandTip(sp), V.up(c, 0.4), NIGHT, 0.2, 0.22, 0.5, 0);
        JobFx.starNode(level0, V.up(c, 0.6), 0.6, 0, 2, formAt / 20.0 + 0.15, false, Opt.empty(), NIGHT, GOLD);
        SkillFx.play(level0, "block.beacon.activate", c, 1.6, 0.8);
        // 소용돌이 그림: 펼쳐짐 (formAt ~ +7) → 돎 (first ~ collapseAt − 6) → 조여 듦 · 번쩍 (collapseAt − 6 ~ +7)
        RuneKit.later(formAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            JobFx.galaxy(d, c, radius, 0, 2, 0.35, false, Opt.of("spin", spin0, "spinRate", rate, "fade", 0.0), NIGHT, GOLD);
            JobFx.ring(d, V.up(c, 0.05), radius, j, true, 0.45, Opt.of("alpha", 0.7));
            SkillFx.play(d, "block.portal.trigger", c, 1.8, 0.35);
        });
        int loopFrom = formAt + 6;
        int loopTo = collapseAt - 6;
        RuneKit.later(loopFrom, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            double spin = spin0 + rate * (loopFrom - formAt) / 20.0;
            JobFx.galaxy(sp.level(), c, radius, 2, 4, (loopTo - loopFrom + 1) / 20.0, true, Opt.of("spin", spin, "spinRate", rate, "fade", 0.0, "phase", 0.0), NIGHT,
                    GOLD);
        });
        RuneKit.later(loopTo, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            double spin = spin0 + rate * (loopTo - formAt) / 20.0;
            JobFx.galaxy(sp.level(), c, radius, 6, 1, 0.33, false, Opt.of("spin", spin, "spinRate", rate * 3, "fade", 0.0), NIGHT, GOLD);
            SkillFx.play(sp.level(), "block.beacon.power_select", c, 1.8, 0.8);
        });
        // 둘레 별빛 · 소리 (8틱마다)
        for (int t = loopFrom; t < loopTo; t += 8) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                double a = Kit.rand() * Math.PI * 2;
                Vec3 p = V.add(V.up(c, 0.3 + Kit.rand() * 0.6), new Vec3(Math.cos(a), 0, Math.sin(a)), radius * (0.4 + Kit.rand() * 0.5));
                JobFx.starNode(d, p, 0.22, 2, 4, 0.5, true, Opt.of("fade", 0.4), NIGHT, GOLD);
                SkillFx.play(d, "block.amethyst_block.chime", c, 0.8 + Kit.rand() * 0.6, 0.4);
            });
        }
        // 끌어당김 (formAt ~ collapseAt − 1): 가운데에서 0.9칸 (+ 몸) 까지
        for (int t = formAt + 1; t < collapseAt; t++) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (LivingEntity e : Kit.around(sp, c, radius + 1.0, 3.0)) {
                    if (!JobKit.pushable(e)) continue;
                    Vec3 to = V.flat(V.sub(c, e.position()));
                    double hd = V.hdist(e.position(), c);
                    double stop = 0.9 + Kit.bodyPad(e);
                    if (hd <= stop) continue;
                    double step = Math.min(pull, hd - stop);
                    Vec3 p = V.add(e.position(), to, step);
                    Vec3 stand = Kit.standSpot(e.level(), new Vec3(p.x, e.getY() + 0.5, p.z), 1.5);
                    if (stand != null) e.teleportTo(stand.x, stand.y, stand.z);
                    e.setDeltaMovement(Vec3.ZERO);
                }
            });
        }
        // 별빛 (first + every × i)
        for (int i = 0; i < pulses; i++) {
            int at = first + every * i;
            RuneKit.later(at - 3, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                // 팔에서 별똥이 적에게 (3틱)
                for (LivingEntity e : Kit.around(sp, c, radius, 3)) {
                    if (!RuneKit.fxQuota("jobVortexStar", 8)) break;
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 from = V.add(V.up(c, 2.2), new Vec3(Math.cos(a), 0, Math.sin(a)), radius * 0.6);
                    Vec3 to = Kit.bodyPoint(e);
                    JobFx.fallStar(d, from, to, V.len(V.sub(to, from)) / 0.15, 0.55, Opt.empty(), NIGHT, GOLD);
                }
            });
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                JobFx.ring(d, V.up(c, 0.08), radius * 0.8, j, false, 0.3, Opt.of("alpha", 0.55));
                for (LivingEntity e : Kit.around(sp, c, radius, 3)) {
                    JobKit.strikeSplit(sp, e, j, P, k.num("pulse", 1.5), Math.max(1, (int) k.num("split", 2)), 2, true, false, life, 0, x -> {
                        if (RuneKit.fxQuota("jobVortex", 6)) JobFx.hit(e, j, 0.9);
                    });
                }
            });
        }
        // 무너짐 (collapseAt): 한 점 번쩍 · 터짐 · 고리 → collapse · 처형
        RuneKit.later(collapseAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Vec3 m = V.up(c, 0.9);
            JobFx.galaxy(d, c, radius, 7, 1, 0.35, false, Opt.of("spin", spin0 + rate * (collapseAt - formAt) / 20.0, "fade", 0.3), NIGHT, GOLD);
            JobFx.starNode(d, m, 1.6, 6, 2, 0.45, false, Opt.empty(), NIGHT, GOLD);
            JobFx.burst(d, m, radius * 0.55, j, 0.4, Opt.empty());
            JobFx.ring(d, V.up(c, 0.08), radius, j, false, 0.45, Opt.empty());
            SkillFx.sparks(d, m, GOLD, 18, 6);
            SkillFx.play(d, "firework.large_blast", c, 1.0, 1.2);
            SkillFx.play(d, "entity.illusioner.mirror_move", c, 0.6, 1.0);
            double exec = k.num("execute", 0.12);
            for (LivingEntity e : Kit.around(sp, c, radius, 3)) {
                JobKit.strike(sp, e, j, P, k.num("collapse", 3.6), true, false, life, 0, x -> {
                    if (RuneKit.fxQuota("jobVortexEnd", 6)) JobFx.hit(e, j, 1.2);
                    if (!e.isAlive() || e.isRemoved() || JobKit.isBoss(e) || e.getHealth() > e.getMaxHealth() * exec) return;
                    // 빨려 들어감: 가운데로 · 별빛 조각 → 남은 체력만큼 (처형 — 숫자 크게)
                    if (e.level() instanceof ServerLevel lv) {
                        RuneKit.streak(lv, Kit.bodyPoint(e), m, GOLD, 0.1, 0.25, 1.0, 0);
                        JobFx.starNode(lv, Kit.bodyPoint(e), 0.6, 7, 1, 0.3, false, Opt.empty(), NIGHT, GOLD);
                        SkillFx.play(lv, "entity.enderman.teleport", e.position(), 1.4, 0.7);
                    }
                    Kit.applyJobDamage(sp, e, e.getHealth() + 1, false, Kit.Cause.MAGIC, DamageNumbers.Style.JOB_BIG, JobKit.grad(j));
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 스펠 서지

    /**
     * 스펠 서지: dur 틱 태세 (강화 dur) — 그동안 다른 직업 스킬을 쓰면 스타 체인의 재사용 대기가 바로 돌아오고 (JobTraits8.commit), 주문 연쇄가 window 틱 이어짐
     * (JobTraits8.chainWindow). 그림: 완드 끝 큰 별 · 몸을 감아 오르는 금빛 별빛 · 발밑 고리 · 1인칭 눈앞 고리
     */
    static boolean surge(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = JobUpgrades.ticks(sp, k, "dur", (int) k.num("dur", 200));
        JobTraits.startStance(sp, k, dur);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        String life = RuneKit.lifeToken(sp);
        JobFx.aura(sp, j, 2.6, 0.9, 0.9);
        JobFx.ring(level, V.up(c, 0.06), 2.2, j, false, 0.5, Opt.empty());
        JobFx.eyeRing(sp, j, 2.4, 0.9, 0.45);
        JobFx.starNode(level, wandTip(sp), 0.55, 0, 2, 0.3, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), NIGHT, GOLD);
        // 몸을 감아 오르는 금빛 별 셋 (14틱)
        for (int t = 0; t < 14; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                for (int i = 0; i < 3; i++) {
                    double a = tt * 0.55 + i * Math.PI * 2 / 3;
                    Vec3 p = new Vec3(sp.getX() + Math.cos(a) * 0.9, sp.getY() + 0.2 + tt * 0.13, sp.getZ() + Math.sin(a) * 0.9);
                    JobFx.starNode(d, p, 0.18, 2, 4, 0.3, true, JobFx.hide1p(sp).with("fade", 0.4, "vel", RuneKit.bodyVel(sp)), NIGHT, GOLD);
                }
            });
        }
        SkillFx.sparks(level, V.up(c, 1.0), GOLD, 14, 4);
        SkillFx.play(level, "beacon.power", c, 1.8, 0.8);
        SkillFx.play(level, "block.amethyst_block.resonate", c, 1.4, 1.0);
        return true;
    }

    // ------------------------------------------------------------ 셀레스티얼 이글

    /** 독수리가 도는 반지름 · 높이 · 내리꽂기 전 꼭대기 (가운데에서 앞 · 위) */
    private static final double ORBIT_R = 6.5;
    private static final double ORBIT_H = 7.5;

    /**
     * 셀레스티얼 이글: 별이 뜬 밤하늘 (rpg:ult_night) · 조준한 곳 (range 칸 — 겨눈 적의 발밑) 위 하늘에 formAt 틱 별빛 독수리 (rfx_jeagle — 날개 끝 사이 약 7칸) 가 나타나
     * 그곳 둘레를 3/4 바퀴 돎 (왼쪽 → 앞 (내 쪽) → 오른쪽 → 너머) — first 틱부터 every 틱마다 beats 번 날개를 내리쳐 별빛 깃털이 쏟아짐 (둘레 radius 칸 — beat · 숫자 split 줄)
     * → 너머에서 솟구쳐 diveAt 틱 그곳에 내리꽂힘 (dive · 큰 숫자 · 띄움 lift). 몸은 땅에 선 채 완드로 부름 (동작 :dive — 내리꽂을 때 완드로 가리킴)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 c = groundAim(sp, k.num("range", 20));
        Vec3 f = Math.hypot(c.x - sp.getX(), c.z - sp.getZ()) > 0.5 ? V.flat(V.sub(c, sp.position())) : Kit.facingOf(sp);
        Vec3 r = V.rightOf(f);
        double radius = k.num("radius", 6);
        int formAt = (int) k.num("formAt", 4);
        int first = (int) k.num("first", 16);
        int every = Math.max(1, (int) k.num("every", 10));
        int beats = Math.max(1, (int) k.num("beats", 4));
        int diveAt = (int) k.num("diveAt", 60);
        int orbitEnd = diveAt - 12;
        int climbEnd = diveAt - 6;
        String life = RuneKit.lifeToken(sp);
        Ult.sky(level0, sp.position(), "rpg:ult_night", diveAt + 30);
        JobFx.aura(sp, j, 3.0, 0.9, 0.9);
        JobFx.eyeRing(sp, j, 2.6, 1.1, 0.5);
        JobFx.ring(level0, V.up(c, 0.06), radius, j, false, diveAt / 20.0, Opt.of("alpha", 0.45, "fade", 0.1));
        SkillFx.play(level0, "block.beacon.activate", sp.position(), 1.3, 1.0);
        SkillFx.play(level0, "entity.phantom.ambient", c, 0.5, 0.8);
        // 하늘 길: 돎 (formAt ~ orbitEnd — 왼쪽에서 시작해 3/4 바퀴) → 솟구침 (~ climbEnd) → 내리꽂힘 (~ diveAt)
        double a0 = -Math.PI / 2;
        double sweep = Math.PI * 1.5;
        Vec3 apex = V.up(V.add(c, f, ORBIT_R + 2.0), ORBIT_H + 5.5);
        Vec3 impact = V.up(c, 0.8);
        java.util.function.IntFunction<Vec3> path = t -> {
            if (t <= orbitEnd) {
                double u = Math.max(0, (t - formAt) / (double) (orbitEnd - formAt));
                double a = a0 + sweep * u;
                Vec3 h = V.add(V.scale(f, -Math.cos(a) * ORBIT_R), r, Math.sin(a) * ORBIT_R);
                return V.add(V.up(c, ORBIT_H + 0.6 * Math.sin(u * Math.PI * 3)), h, 1);
            }
            Vec3 end = path0(c, f, r, a0 + sweep, orbitEnd);
            if (t <= climbEnd) {
                double u = JobMove.ease((t - orbitEnd) / (double) (climbEnd - orbitEnd), JobMove.OUT);
                return V.lerp(end, apex, u);
            }
            double u = Math.min(1, (t - climbEnd) / (double) (diveAt - climbEnd));
            return V.lerp(apex, impact, u * u);
        };
        // 나타남: 하늘에 별 일곱이 독수리 꼴로 번쩍 → 독수리
        Vec3 born = path.apply(formAt);
        JobFx.starNode(level0, born, 2.2, 0, 2, formAt / 20.0 + 0.25, false, Opt.empty(), NIGHT, GOLD);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            Vec3 p = V.add(born, V.add(V.scale(r, Math.cos(a) * 2.6), V.UP, Math.sin(a) * 1.2), 1);
            JobFx.starNode(level0, p, 0.4, 0, 2, formAt / 20.0 + 0.2, false, Opt.empty(), NIGHT, GOLD);
            RuneKit.streak(level0, p, born, GOLD, 0.06, formAt / 20.0 + 0.2, 0.9, 0);
        }
        // 독수리 (2틱마다 한 조각 — 다음 자리로 움직이며 · 1틱 겹침 · 날갯짓 차례를 이어 그림: 날개를 내리친 장 (1) 이 깃털이 쏟아지는 틱에)
        for (int t = formAt; t < diveAt; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                Vec3 p = path.apply(tt);
                Vec3 q = path.apply(Math.min(diveAt, tt + 2));
                Vec3 dir = V.sub(q, p);
                if (V.len(dir) < 1e-3) dir = f;
                boolean dive = tt >= climbEnd;
                double phase = (((tt - first) * 0.6 + 1) % 6 + 6) % 6;
                JobFx.eagle(d, p, dir, 3.8, dive, 0.15, Opt.of("vel", V.scale(V.sub(q, p), 10), "phase", dive ? 0.0 : phase, "alpha", 0.95), NIGHT, GOLD);
                // 지나간 자리의 옅은 별빛 줄 · 반짝이
                Vec3 back = path.apply(Math.max(formAt, tt - 2));
                RuneKit.streak(d, back, p, NIGHT, 0.5, 0.45, 0.35, 0);
                SkillFx.sparks(d, p, GOLD, dive ? 6 : 2, 2.5);
                if ((tt - formAt) % 10 == 0 && !dive) SkillFx.play(d, "entity.ender_dragon.flap", p, 1.5, 0.6);
            });
        }
        // 날갯짓 (first + every × i): 4틱 전에 별빛 깃털이 독수리에서 적에게 날아가 그 틱에 닿음 · 옅은 깃털 비
        for (int i = 0; i < beats; i++) {
            int at = first + every * i;
            RuneKit.later(at - 4, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                Vec3 from = path.apply(at - 4);
                int n = 0;
                for (LivingEntity e : Kit.around(sp, c, radius, 4)) {
                    if (n++ >= 6) break;
                    Vec3 to = Kit.bodyPoint(e);
                    JobFx.fallStar(d, from, to, V.len(V.sub(to, from)) / 0.2, 0.7, Opt.empty(), NIGHT, GOLD);
                }
                for (int m = n; m < 4; m++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 g = V.add(V.up(c, 0.2), new Vec3(Math.cos(a), 0, Math.sin(a)), radius * Math.sqrt(Kit.rand()));
                    JobFx.fallStar(d, from, g, V.len(V.sub(g, from)) / 0.2, 0.6, Opt.empty(), NIGHT, GOLD);
                }
                JobFx.feathers(d, from, 6, 2.0, 0.32, j, 1.2, Opt.empty());
                SkillFx.play(d, "entity.phantom.flap", from, 1.2, 0.9);
            });
            JobKit.motionLater(sp, "arcmage_ult:beat", at - 3, life);
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                SkillFx.play(d, "firework.blast", c, 1.3, 0.7);
                for (LivingEntity e : Kit.around(sp, c, radius, 4)) {
                    JobKit.strikeSplit(sp, e, j, P, k.num("beat", 6), Math.max(1, (int) k.num("split", 6)), 1, true, false, life, 0, x -> {
                        if (e.level() instanceof ServerLevel lv) JobFx.starNode(lv, Kit.bodyPoint(e), 0.45, 6, 2, 0.22, false, Opt.empty(), NIGHT, GOLD);
                        if (RuneKit.fxQuota("jobEagleBeat", 6)) JobFx.hit(e, j, 1.0);
                    });
                }
            });
        }
        // 솟구침 · 내리꽂음
        RuneKit.later(orbitEnd, () -> {
            if (RuneKit.sameLife(sp, life)) SkillFx.play(sp.level(), "entity.ender_dragon.growl", apex, 1.6, 0.5);
        });
        RuneKit.later(climbEnd, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            RuneKit.streak(d, apex, impact, GOLD, 0.12, 0.35, 0.9, 0);
            JobFx.starNode(d, V.up(c, 0.4), 1.2, 0, 2, 0.3, false, Opt.empty(), NIGHT, GOLD);
            SkillFx.play(d, "item.trident.riptide_3", apex, 1.4, 0.8);
        });
        JobKit.motionLater(sp, "arcmage_ult:dive", climbEnd - 2, life);
        RuneKit.later(diveAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Vec3 m = V.up(c, 1.0);
            JobFx.starNode(d, m, 3.0, 6, 2, 0.5, false, Opt.empty(), NIGHT, GOLD);
            for (int i = 0; i < 3; i++) {
                int ii = i;
                RuneKit.later(i * 2, () -> JobFx.burst(d, V.up(c, 0.8 + ii * 0.7), 2.2 + ii * 0.8, j, 0.42, Opt.empty()));
            }
            JobFx.ring(d, V.up(c, 0.06), radius, j, false, 0.5, Opt.empty());
            RuneKit.later(3, () -> JobFx.ring(d, V.up(c, 0.1), radius * 0.6, j, true, 0.45, Opt.empty()));
            JobFx.feathers(d, V.up(c, 1.5), 14, radius * 0.6, 0.38, j, 1.4, Opt.empty());
            RuneKit.godRays(sp, d, c, radius * 0.6, 6, NIGHT, GOLD, Opt.of("h", 14.0, "life", 0.9, "alpha", 0.8, "skipNear", true));
            SkillFx.sparks(d, m, GOLD, 24, 7);
            Ult.flash(d, c, GOLD, Opt.empty(), sp);
            SkillFx.play(d, "random.explode", c, 0.8, 1.0);
            SkillFx.play(d, "firework.large_blast", c, 1.0, 0.9);
            SkillFx.play(d, "entity.phantom.death", c, 1.4, 0.6);
            for (LivingEntity e : Kit.around(sp, c, radius, 4)) {
                JobKit.strike(sp, e, j, P, k.num("dive", 32), true, true, life, 0, x -> {
                    if (JobKit.pushable(e)) Kit.launch(e, 0, k.num("lift", 0.9), 0);
                    if (RuneKit.fxQuota("jobEagleDive", 6)) JobFx.hit(e, j, 1.5);
                });
            }
        });
        return true;
    }

    /** 도는 길의 한 점 (각 a — 가운데 c 에서 내 쪽 −f 가 0, 오른쪽 r 가 π/2) */
    private static Vec3 path0(Vec3 c, Vec3 f, Vec3 r, double a, int t) {
        Vec3 h = V.add(V.scale(f, -Math.cos(a) * ORBIT_R), r, Math.sin(a) * ORBIT_R);
        return V.add(V.up(c, ORBIT_H + 0.6 * Math.sin(Math.PI * 3)), h, 1);
    }
}
