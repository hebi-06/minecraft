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
 * 스타 볼텍스 (아우렐리온 솔 E — 끌어당기는 별의 소용돌이 · 무너지며 처형) · 2026-11-07 재설계안 3판 (도감 표시 — 스펠 서지 · 셀레스티얼 이글이 별자리 컨셉과 맞지
 * 않음): 셀레스티얼 오빗 (아우렐리온 솔 패시브 — 둘레를 도는 별 다섯이 1초마다 별똥처럼 떨어져 터지며 땅에 별자리를 그림) · 콘스텔레이션 (조이 R · 아우렐리온 솔 R — 하늘에
 * 국자 일곱 별이 차례로 떠 별빛을 내리꽂고 한 점으로 무너져 터짐). 모두 마법 피해. 연출 = 2D 그림 (JobFx — 별 rfx_jstarnode · 별똥 rfx_jfallstar · 빛 고리 rfx_jhalo ·
 * 별의 소용돌이 rfx_jgalaxy) · 환영 별 판 (card_orb). 화면 흔들림 없음 (사용자 "화면 흔들림은 넣지마 멀미나")
 */
final class ArcmageSkills {
    private ArcmageSkills() {}

    private static final double[] NIGHT = JobTraits8.NIGHT;
    private static final double[] GOLD = JobTraits8.GOLD;
    /** 별빛 번개 · 고리의 겉빛 (보랏빛 별빛 — 영상 1: 옅은 파랑은 낮 하늘에 묻혔음) · 흰 금빛 심지 */
    private static final double[] STAR_BLUE = {0.5, 0.38, 1.0};
    private static final double[] STAR_CORE = {1.0, 0.95, 0.78};

    static void register() {
        JobSystem.register("arcmage_chain", ArcmageSkills::chain);
        JobSystem.register("arcmage_halo", ArcmageSkills::halo);
        JobSystem.register("arcmage_vortex", ArcmageSkills::vortex);
        JobSystem.register("arcmage_orbit", ArcmageSkills::orbit);
        JobSystem.register("arcmage_ult", ArcmageSkills::ult);
    }

    static Vec3 wandTip(ServerPlayer sp) {
        return MageSkills.wandTip(sp);
    }

    /** 적 앞 (나를 향한 쪽 · 가슴 높이) 의 별 자리 — 몸 가운데에 그리면 몸에 가려 보이지 않음 (영상 1) */
    private static Vec3 starAt(ServerPlayer sp, LivingEntity e) {
        Vec3 b = V.up(Kit.bodyPoint(e), 0.35);
        Vec3 d = V.sub(sp.position(), e.position());
        Vec3 toMe = Math.hypot(d.x, d.z) > 1e-3 ? V.flat(d) : V.scale(Kit.facingOf(sp), -1);
        return V.add(b, toMe, Kit.bodyPad(e) + 0.45);
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
                Vec3 to = starAt(sp, e);
                Vec3 from = ii == 0 ? wandTip(sp) : starAt(sp, path.get(ii - 1));
                if (ii > 0 && path.get(ii - 1) == e) {
                    // 같은 적을 다시: 별빛이 위로 솟았다가 내리꽂힘
                    Vec3 top = V.up(to, 2.6 + Kit.bodyPad(e));
                    RuneKit.zap(d, to, top, STAR_BLUE, STAR_CORE, 0.36, 0.25);
                    RuneKit.zap(d, top, to, STAR_BLUE, STAR_CORE, 0.42, 0.32);
                    JobFx.starNode(d, top, 0.7, 0, 2, 0.3, false, Opt.empty(), NIGHT, GOLD);
                } else {
                    RuneKit.zap(d, from, to, STAR_BLUE, STAR_CORE, ii == 0 ? 0.48 : 0.4, 0.34);
                    RuneKit.streak(d, from, to, GOLD, 0.09, 0.5, 0.9, 0);
                }
                SkillFx.play(d, ii == 0 ? "entity.lightning_bolt.impact" : "block.amethyst_block.chime", to, ii == 0 ? 1.8 : 1.2 + 0.12 * ii, ii == 0 ? 0.5 : 0.9);
            });
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
                ServerLevel d = sp.level();
                Vec3 p = starAt(sp, e);
                stars.add(p);
                JobFx.starNode(d, p, 0.75 + Kit.bodyPad(e) * 0.25, 6, 2, 0.3, false, Opt.empty(), NIGHT, GOLD);
                JobFx.starNode(d, p, 0.5, 2, 4, Math.max(0.3, (end - at) / 20.0 + 0.5), true, Opt.of("fade", 0.3), NIGHT, GOLD);
                SkillFx.sparks(d, p, GOLD, 8, 4);
                // 앞 별과 잇는 금빛 별자리 선 (끝날 때까지 남음)
                if (stars.size() >= 2) {
                    Vec3 a = stars.get(stars.size() - 2);
                    double keep = Math.max(0.3, (end - at) / 20.0 + 0.5);
                    RuneKit.streak(d, a, p, NIGHT, 0.32, keep, 0.5, 0);
                    RuneKit.streak(d, a, p, GOLD, 0.11, keep, 1.0, 0);
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
            for (Vec3 p : stars) JobFx.starNode(d, p, 0.6, 6, 2, 0.35, false, Opt.empty(), NIGHT, GOLD);
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
                    JobFx.cells(d, "jhalo", JobFx.HALO, 1, 2, p1, 0.16, 12, true, Opt.of("dir", face, "size", radius * 1.0 / 0.88, "vel", vel, "spin", (t * 37.0) % 360,
                            "fade", 0.0, "alpha", 1.0), STAR_BLUE, GOLD);
                    JobFx.starNode(d, p1, 0.45, 2, 4, 0.16, true, Opt.of("vel", vel, "fade", 0.0), NIGHT, GOLD);
                    SkillFx.sparks(d, p1, GOLD, 3, 1.5);
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
                    JobFx.halo(d, V.up(sp.position(), 0.06), 1.6, j, 0.45, Opt.empty());
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
            JobFx.halo(d, V.up(c, 0.05), radius, j, 0.45, Opt.of("alpha", 0.8));
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
        // 소용돌이를 따라 감겨 드는 별 (4틱마다 셋 — 가슴 높이에서 안쪽으로 돌며 내려앉음: 땅의 그림만으로는 낮은 카메라에서 납작했음 — 영상 1) · 소리 (8틱마다)
        for (int t = loopFrom; t < loopTo; t += 4) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                for (int i = 0; i < 3; i++) {
                    double a = Kit.rand() * Math.PI * 2;
                    double r = radius * (0.55 + Kit.rand() * 0.4);
                    Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                    Vec3 tan = new Vec3(-Math.sin(a), 0, Math.cos(a));
                    Vec3 p = V.add(V.up(c, 0.8 + Kit.rand() * 1.4), out, r);
                    Vec3 vel = V.add(V.add(V.scale(tan, r * 0.9), out, -r * 0.55), V.UP, -0.8);
                    JobFx.starNode(d, p, 0.24 + Kit.rand() * 0.12, 2, 4, 0.55, true, Opt.of("fade", 0.4, "vel", vel), NIGHT, GOLD);
                }
                if (tt % 8 == 0) SkillFx.play(d, "block.amethyst_block.chime", c, 0.8 + Kit.rand() * 0.6, 0.4);
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
                JobFx.halo(d, V.up(c, 0.08), radius * 0.8, j, 0.35, Opt.of("alpha", 0.6));
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
            JobFx.halo(d, V.up(c, 0.08), radius, j, 0.5, Opt.empty());
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

    // ------------------------------------------------------------ 셀레스티얼 오빗 (2026-11-07 재설계안 3판 — 스펠 서지 대신)

    /**
     * 셀레스티얼 오빗 (태세 칸 몫 딜 — 롤 아우렐리온 솔 패시브): 금빛 별 다섯 (rfx_jstarnode — 2틱마다 다시 그림 · 주인을 따라 돎 · orbitTicks 틱에 한 바퀴) 이 둘레 orbit 칸을 돎 → first 틱부터 every 틱마다 shots 번,
     * fly 틱 전에 가장 가까운 적 (range 칸) 쪽 별 하나가 별똥처럼 솟았다 떨어져 그 틱에 터짐 (그 적 둘레 radius 칸 — shot · 마법 피해). 터진 자리마다 작은 별이 남아 앞 자리와
     * 금빛 선으로 이어져 땅에 별자리를 그림 (끝날 때까지 — 그림만, 특성의 별자리와는 따로). 칸 HUD 에 남은 시간 (태세 줄)
     */
    static boolean orbit(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int shots = Math.max(1, (int) k.num("shots", 10));
        int first = Math.max(2, (int) k.num("first", 10));
        int every = Math.max(1, (int) k.num("every", 20));
        int fly = Math.max(2, Math.min(first, (int) k.num("fly", 4)));
        double range = k.num("range", 14);
        double R = k.num("radius", 2);
        double orbitR = k.num("orbit", 1.7);
        int end = first + every * (shots - 1);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        JobTraits.startStance(sp, k, end + 6);
        // 별 다섯이 둘레를 돎 — 금빛 네 갈래 별 (rfx_jstarnode — 반짝임 이어 돌림) 을 2틱마다 다시 그림 (주인을 따라감 · 도는 쪽으로 움직임 · 위아래로 조금씩 출렁임).
        // 영상 42332661771 뒤: 환영 별 판 (card_orb) 은 파란 구슬로 보여 별 같지 않았음. 날아간 별은 gone 틱까지 비었다가 다시 뜸
        double w = Math.PI * 2 / Math.max(20, k.num("orbitTicks", 60));
        int[] gone = new int[5];
        for (int t = 0; t < end + 8; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life) || tt > 2 && !JobTraits.stanceOn(sp, k.id())) return;
                ServerLevel lv = sp.level();
                Vec3 c = V.up(sp.position(), 1.25);
                Vec3 bodyVel = RuneKit.bodyVel(sp);
                for (int i = 0; i < 5; i++) {
                    if (gone[i] > tt) continue;
                    double a = tt * w + i * Math.PI * 2 / 5;
                    Vec3 p = new Vec3(c.x + Math.cos(a) * orbitR, c.y + 0.14 * Math.sin(a * 2 + tt * 0.15), c.z + Math.sin(a) * orbitR);
                    Vec3 vel = V.add(bodyVel, new Vec3(-Math.sin(a), 0, Math.cos(a)), w * 20 * orbitR);
                    boolean back = gone[i] > 0 && tt - gone[i] < 4; // 다시 뜨는 별은 번쩍
                    JobFx.starNode(lv, p, back ? 0.75 : 0.58, back ? 0 : 2, back ? 2 : 4, 0.13, !back, Opt.of("vel", vel, "fade", 0.0), NIGHT, GOLD);
                }
            });
        }
        // 부름: 완드 끝 큰 별 · 발밑 별빛 고리 · 둘레로 퍼지는 금빛 별 다섯
        JobFx.starNode(level0, wandTip(sp), 0.6, 0, 2, 0.3, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp)), NIGHT, GOLD);
        JobFx.halo(level0, V.up(c0, 0.06), orbitR + 0.5, j, 0.6, Opt.empty());
        for (int i = 0; i < 5; i++) {
            double ang = i * Math.PI * 2 / 5;
            Vec3 p = V.up(new Vec3(c0.x + Math.cos(ang) * orbitR, c0.y, c0.z + Math.sin(ang) * orbitR), 1.25);
            JobFx.starNode(level0, p, 0.85, 0, 2, 0.3, false, Opt.empty(), NIGHT, GOLD);
            RuneKit.streak(level0, V.up(c0, 1.25), p, GOLD, 0.1, 0.3, 0.8, 0);
        }
        SkillFx.sparks(level0, V.up(c0, 1.2), GOLD, 14, 4);
        SkillFx.play(level0, "block.beacon.activate", c0, 1.7, 0.7);
        SkillFx.play(level0, "block.amethyst_block.resonate", c0, 1.4, 1.0);
        List<Vec3> marks = new ArrayList<>();
        for (int i = 0; i < shots; i++) {
            int ii = i;
            int at = first + every * i;
            LivingEntity[] tgt = {null};
            // 날아감 (at − fly): 그 적 쪽 별이 번쩍 떨어져 나와 솟았다가 (반) 떨어짐 (반)
            RuneKit.later(at - fly, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                List<LivingEntity> near = JobKit.near(sp, range, 1);
                Vec3 c = V.up(sp.position(), 1.25);
                if (near.isEmpty()) {
                    // 적이 없으면 별 하나가 반짝이고 그대로 돎
                    JobFx.starNode(lv, V.add(c, Kit.facingOf(sp), orbitR), 0.7, 6, 2, 0.2, false, Opt.empty(), NIGHT, GOLD);
                    return;
                }
                LivingEntity e = near.get(0);
                tgt[0] = e;
                Vec3 to = Kit.bodyPoint(e);
                Vec3 raw = V.sub(to, c);
                Vec3 dir = Math.hypot(raw.x, raw.z) > 0.1 ? V.flat(raw) : Kit.facingOf(sp); // V.flat 은 단위 벡터 (0 이면 남쪽)
                // 그 적 쪽에 가장 가까운 도는 별이 떨어져 나감 (14틱 비었다가 다시 뜸)
                int now = at - fly;
                int best = 0;
                double bd = -2;
                for (int s = 0; s < 5; s++) {
                    double a = now * w + s * Math.PI * 2 / 5;
                    double dd = Math.cos(a) * dir.x + Math.sin(a) * dir.z;
                    if (dd > bd) {
                        bd = dd;
                        best = s;
                    }
                }
                double ab = now * w + best * Math.PI * 2 / 5;
                gone[best] = now + 14;
                Vec3 from = new Vec3(c.x + Math.cos(ab) * orbitR, c.y, c.z + Math.sin(ab) * orbitR);
                double dist = V.len(V.sub(to, from));
                Vec3 apex = V.up(V.lerp(from, to, 0.5), 2.2 + dist * 0.2);
                int half = Math.max(1, fly / 2);
                JobFx.starNode(lv, from, 0.85, 6, 2, 0.2, false, Opt.empty(), NIGHT, GOLD);
                JobFx.fallStar(lv, from, apex, V.len(V.sub(apex, from)) / (half / 20.0), 1.0, Opt.empty(), NIGHT, GOLD);
                RuneKit.later(half, () -> {
                    if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
                    Vec3 hit = Kit.bodyPoint(e);
                    JobFx.fallStar(sp.level(), apex, hit, V.len(V.sub(hit, apex)) / ((fly - half) / 20.0), 1.1, Opt.empty(), NIGHT, GOLD);
                });
                SkillFx.play(lv, "block.amethyst_block.chime", from, 1.3 + 0.05 * ii, 0.7);
            });
            // 터짐 (at): 별 번쩍 · 둘레 별빛 고리 · 불티 → 그 자리에 작은 별 (끝까지) · 앞 자리와 금빛 선
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                LivingEntity e = tgt[0];
                if (e == null || e.isRemoved()) return;
                ServerLevel lv = sp.level();
                Vec3 p = Kit.bodyPoint(e);
                JobFx.starNode(lv, p, 1.5 + Kit.bodyPad(e) * 0.5, 6, 2, 0.3, false, Opt.empty(), NIGHT, GOLD);
                JobFx.burst(lv, p, 1.8, j, 0.3, Opt.empty());
                JobFx.halo(lv, V.up(e.position(), 0.06), R, j, 0.35, Opt.of("alpha", 0.85));
                SkillFx.sparks(lv, p, GOLD, 14, 4.5);
                SkillFx.play(lv, "firework.blast", p, 1.5, 0.55);
                Vec3 mark = V.up(e.position(), 0.25);
                double keep = Math.max(0.3, (end - at) / 20.0 + 0.6);
                JobFx.starNode(lv, mark, 0.55, 2, 4, keep, true, Opt.of("fade", 0.3), NIGHT, GOLD);
                if (!marks.isEmpty() && V.len(V.sub(marks.get(marks.size() - 1), mark)) > 0.6) {
                    RuneKit.streak(lv, marks.get(marks.size() - 1), mark, NIGHT, 0.34, keep, 0.5, 0);
                    RuneKit.streak(lv, marks.get(marks.size() - 1), mark, GOLD, 0.11, keep, 0.95, 0);
                }
                marks.add(mark);
                for (LivingEntity o : Kit.around(sp, p, R, 2.5)) {
                    JobKit.strike(sp, o, j, P, k.num("shot", 1.68), true, false, life, 0, x -> {
                        if (RuneKit.fxQuota("jobStarOrbit", 6)) JobFx.hit(o, j, 0.9);
                    });
                }
            });
        }
        // 끝: 땅에 그려진 별자리가 한 번 크게 반짝이고 사라짐
        RuneKit.later(end + 6, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            for (Vec3 m : marks) JobFx.starNode(lv, m, 0.95, 6, 2, 0.35, false, Opt.empty(), NIGHT, GOLD);
            if (!marks.isEmpty()) SkillFx.play(lv, "block.amethyst_block.resonate", marks.get(marks.size() - 1), 1.5, 0.7);
        });
        return true;
    }

    // ------------------------------------------------------------ 콘스텔레이션 (2026-11-07 재설계안 3판 — 셀레스티얼 이글 대신)

    /**
     * 큰곰자리 국자 일곱 별 (오른쪽 x · 위 y — 손잡이 끝 알카이드부터 차례로: 알카이드 · 미자르 · 알리오스 · 메그레즈 · 페크다 · 메라크 · 두베). 하늘의 별 자리 = 조준한
     * 곳 위 DIPPER_H 칸 + 오른쪽 x × DIPPER_W · 위 y × DIPPER_V · 조금 너머로
     */
    private static final double[][] DIPPER = {{-0.95, 0.5}, {-0.45, 0.68}, {0.0, 0.55}, {0.45, 0.42}, {0.5, -0.12}, {1.0, -0.05}, {1.0, 0.55}};
    private static final double DIPPER_H = 8.5;
    private static final double DIPPER_W = 4.6;
    private static final double DIPPER_V = 3.0;

    /**
     * 콘스텔레이션 (롤 조이 R · 아우렐리온 솔 R): 별이 뜬 밤하늘 (rpg:ult_night) — 조준한 곳 (range 칸 — 겨눈 적의 발밑) 위 하늘에 formAt 틱부터 every 틱마다 일곱 별이 차례로
     * 떠 금빛 선으로 이어지며 국자 꼴 별자리를 그림 (마지막에 국자 그릇을 닫음). 별이 뜰 때마다 별빛이 둘레 radius 칸의 적 (가까운 순 일곱까지 · 돌아가며 — 적이 하나면 일곱 다
     * 그 적) 에게 fall 틱 만에 내리꽂힘 — 피해는 그 적의 첫 별빛에 한 번 (star × 맞을 별 수 · 숫자는 별빛마다 한 줄) → burstAt − 6 에 별자리가 한 점으로 무너져 내려
     * burstAt 틱에 터짐 (둘레 radius 칸 burst · 큰 숫자 · 띄움 lift). 화면은 흔들지 않음
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        Vec3 c = groundAim(sp, k.num("range", 20));
        Vec3 f = Math.hypot(c.x - sp.getX(), c.z - sp.getZ()) > 0.5 ? V.unit(V.flat(V.sub(c, sp.position()))) : Kit.facingOf(sp);
        Vec3 r = V.rightOf(f);
        double radius = k.num("radius", 9);
        int stars = Math.max(1, Math.min(DIPPER.length, (int) k.num("stars", 7)));
        int formAt = Math.max(2, (int) k.num("formAt", 8));
        int every = Math.max(1, (int) k.num("every", 6));
        int fall = Math.max(1, (int) k.num("fall", 2));
        int burstAt = Math.max(formAt + every * (stars - 1) + fall + 6, (int) k.num("burstAt", 56));
        String life = RuneKit.lifeToken(sp);
        Ult.sky(level0, sp.position(), "rpg:ult_night", burstAt + 30);
        JobFx.aura(sp, j, 3.0, 0.9, 0.9);
        JobFx.eyeRing(sp, j, 2.6, 1.1, 0.5);
        JobFx.halo(level0, V.up(c, 0.06), radius, j, burstAt / 20.0, Opt.of("alpha", 0.45));
        SkillFx.play(level0, "block.beacon.activate", sp.position(), 1.3, 1.0);
        SkillFx.play(level0, "block.amethyst_block.resonate", c, 0.8, 1.0);
        // 하늘의 별 자리 (쓸 때 정함)
        Vec3[] sky = new Vec3[stars];
        for (int i = 0; i < stars; i++) {
            sky[i] = V.add(V.add(V.up(c, DIPPER_H + DIPPER[i][1] * DIPPER_V), r, DIPPER[i][0] * DIPPER_W), f, 1.5);
        }
        // 별빛이 노릴 적 (쓸 때 둘레 radius 칸 — 가까운 순 일곱까지, 별 i 는 적 i mod n)
        List<LivingEntity> foes = new ArrayList<>(Kit.around(sp, c, radius, 4));
        foes.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(c.x, c.y, c.z)));
        if (foes.size() > stars) foes = new ArrayList<>(foes.subList(0, stars));
        int n = foes.size();
        List<LivingEntity> fs = foes;
        for (int i = 0; i < stars; i++) {
            int ii = i;
            int at = formAt + every * i;
            LivingEntity e = n == 0 ? null : fs.get(i % n);
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                Vec3 p = sky[ii];
                // 별이 뜸 (번쩍 → 반짝임 — 무너질 때까지) · 앞 별과 금빛 선 · 맑은 소리 (차례마다 한 음씩 높게)
                double keep = (burstAt - 6 - at) / 20.0 + 0.1;
                JobFx.starNode(d, p, 2.4, 0, 2, 0.25, false, Opt.empty(), NIGHT, GOLD);
                JobFx.starNode(d, p, 1.7, 2, 4, keep, true, Opt.of("fade", 0.2), NIGHT, GOLD);
                if (ii > 0) {
                    RuneKit.streak(d, sky[ii - 1], p, NIGHT, 0.9, keep, 0.55, 0);
                    RuneKit.streak(d, sky[ii - 1], p, GOLD, 0.26, keep, 1.0, 0);
                }
                if (ii == stars - 1 && stars >= 4) {
                    RuneKit.streak(d, p, sky[3], NIGHT, 0.9, keep, 0.55, 0);
                    RuneKit.streak(d, p, sky[3], GOLD, 0.26, keep, 1.0, 0);
                }
                SkillFx.sparks(d, p, GOLD, 10, 3.5);
                SkillFx.play(d, "block.amethyst_block.chime", p, 0.8 + 0.12 * ii, 1.0);
                // 별빛이 내리꽂힘: 별 → 그 적 (없으면 별 아래 땅)
                Vec3 to = e != null && e.isAlive() && !e.isRemoved() ? Kit.bodyPoint(e) : new Vec3(p.x, RuneKit.groundAt(d, p, c.y), p.z);
                JobFx.fallStar(d, p, to, V.len(V.sub(to, p)) / (fall / 20.0), 1.6, Opt.empty(), NIGHT, GOLD);
                RuneKit.later(fall, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    ServerLevel d2 = sp.level();
                    Vec3 hit = e != null && e.isAlive() && !e.isRemoved() ? Kit.bodyPoint(e) : to;
                    JobFx.beam(d2, p, hit, NIGHT, GOLD, 1.4, 0.3, Opt.of("alpha", 0.9));
                    JobFx.starNode(d2, hit, 1.7, 6, 2, 0.3, false, Opt.empty(), NIGHT, GOLD);
                    JobFx.burst(d2, hit, 2.0, j, 0.3, Opt.empty());
                    SkillFx.sparks(d2, hit, GOLD, 12, 5);
                    SkillFx.play(d2, "firework.blast", hit, 1.2, 0.8);
                });
            });
        }
        // 피해: 적마다 첫 별빛에 한 번 (star × 그 적에게 내리꽂힐 별 수 — 숫자는 별빛마다 한 줄, 줄 사이 = 같은 적의 다음 별빛까지)
        for (int q = 0; q < n; q++) {
            LivingEntity e = fs.get(q);
            int count = 0;
            for (int i = q; i < stars; i += n) count++;
            int at = formAt + every * q + fall;
            JobKit.strikeSplit(sp, e, j, P, k.num("star", 4) * count, count, every * n, true, false, life, at, x -> {
                if (RuneKit.fxQuota("jobConstellation", 6)) JobFx.hit(e, j, 1.2);
            });
        }
        // 무너짐 (burstAt − 6): 일곱 별이 한 점 (조준한 곳) 으로 떨어져 내림 → burstAt 터짐
        JobKit.motionLater(sp, "arcmage_ult:burst", burstAt - 8, life);
        RuneKit.later(burstAt - 6, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Vec3 m = V.up(c, 1.0);
            for (Vec3 p : sky) {
                JobFx.fallStar(d, p, m, V.len(V.sub(m, p)) / 0.3, 1.8, Opt.empty(), NIGHT, GOLD);
                RuneKit.streak(d, p, m, GOLD, 0.2, 0.3, 0.9, 0);
            }
            SkillFx.play(d, "item.trident.riptide_3", c, 1.4, 0.8);
        });
        RuneKit.later(burstAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Vec3 m = V.up(c, 1.0);
            JobFx.starNode(d, m, 4.6, 6, 2, 0.5, false, Opt.empty(), NIGHT, GOLD);
            for (int i = 0; i < 3; i++) {
                int ii = i;
                RuneKit.later(i * 2, () -> JobFx.burst(d, V.up(c, 0.8 + ii * 0.8), 2.6 + ii * 1.0, j, 0.45, Opt.empty()));
            }
            JobFx.halo(d, V.up(c, 0.06), radius, j, 0.6, Opt.empty());
            RuneKit.later(3, () -> JobFx.halo(d, V.up(c, 0.1), radius * 0.6, j, 0.5, Opt.empty()));
            RuneKit.godRays(sp, d, c, radius * 0.6, 7, NIGHT, GOLD, Opt.of("h", 14.0, "life", 0.9, "alpha", 0.8, "skipNear", true));
            for (Vec3 p : sky) JobFx.starNode(d, new Vec3(p.x, c.y + 1.2, p.z), 1.2, 6, 2, 0.35, false, Opt.empty(), NIGHT, GOLD);
            SkillFx.sparks(d, m, GOLD, 28, 7);
            Ult.flash(d, c, GOLD, Opt.empty(), sp);
            SkillFx.play(d, "random.explode", c, 0.8, 1.0);
            SkillFx.play(d, "firework.large_blast", c, 1.0, 0.9);
            SkillFx.play(d, "block.amethyst_block.break", c, 0.6, 1.0);
            for (LivingEntity e : Kit.around(sp, c, radius, 4)) {
                JobKit.strike(sp, e, j, P, k.num("burst", 28), true, true, life, 0, x -> {
                    if (JobKit.pushable(e)) Kit.launch(e, 0, k.num("lift", 0.9), 0);
                    if (RuneKit.fxQuota("jobConstellationBurst", 6)) JobFx.hit(e, j, 1.5);
                });
            }
        });
        return true;
    }
}
