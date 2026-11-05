package mmorpg.fabric.job;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.core.Js;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 암살자 각성 트릭스터 직업 스킬 — 재설계안 2판 (2026-11-06 사용자 "다음 작업" · 3 · 4 번은 "더 화려한걸로" — 탈론 W 같은 돌아오는 칼 · 룬과 겹치는 연막 ·
 * 피에로와 겹치는 상자 · 분신은 빼고): 섀도 스와프 · 미라주 대시 · 사우전드 팬텀 · 미러 팰리스 (태세 — JobTraits10) · 와일드파이어 피스트 (jobs.json
 * advance.trickster — 자홍 · 검보라 + 청록 환영 테두리, 특성 「표식」 최대 7 · 옮겨감 — JobTraits3 의 암살자 줄기).
 * 1차 암살자 매커니즘 (근접 · 단일 · 표식을 쌓아 한 방) 그대로 — 은신 대신 환영 · 거울: 환영과 자리를 바꿔 등 뒤를 잡음 · 뛰어들었다 돌아옴 · 환영 여섯이 엇갈려 꿰뚫음 ·
 * 둘레를 도는 거울 · 들불 표식이 쌓인 피해를 터뜨림.
 * 피해 = 위력 P × 배율 (JobKit.strike — 같은 적은 10틱 넘게 띄움, 한꺼번에 여러 줄은 strikeSplit). 연출 = 2D 그림 (환영 = 그림자 분신 · 청록 테두리, 거울 조각
 * rfx_jmirror · 거울 rfx_jpane · 육망성 rfx_jhexa · 들불 표식 rfx_jmaw · 서 있는 불꽃 rfx_jflame · 꽃 · 꽃잎 · 엇갈린 베기 · 큰 검기 rfx_jwave) · 들불 하늘
 * (rpg:ult_wildfire).
 */
final class TricksterSkills {
    private TricksterSkills() {}

    /** 환영의 테두리 (청록) · 그 심지 */
    static final double[] ILLUSION = {0.3, 0.95, 0.9};
    static final double[] ILLUSION_CORE = {0.8, 1.0, 0.97};
    /** 들불 (환술의 불 — 자홍 불꽃 · 흰 금빛 심지) */
    static final double[] FIRE = {1.0, 0.3, 0.72};
    static final double[] FIRE_CORE = {1.0, 0.88, 0.62};

    /** 섀도 스와프의 던진 환영 (다시 누르면 자리를 바꿈): 서 있는 자리 · 노린 적 · 쓴 틱 · 환영이 지나간 틱 · 끝났는지 */
    private static final class Swap {
        final Vec3 at;
        final Vec3 face;
        final LivingEntity foe;
        final long cast;
        final int cutAt;
        boolean done;

        Swap(Vec3 at, Vec3 face, LivingEntity foe, long cast, int cutAt) {
            this.at = at;
            this.face = face;
            this.foe = foe;
            this.cast = cast;
            this.cutAt = cutAt;
        }
    }

    /** 미라주 대시의 떠난 자리 (다시 누르면 돌아옴) */
    private static final class Mark {
        final Vec3 at;
        final Vec3 face;
        boolean done;

        Mark(Vec3 at, Vec3 face) {
            this.at = at;
            this.face = face;
        }
    }

    private static final Map<UUID, Swap> SWAPS = new HashMap<>();
    private static final Map<UUID, Mark> MARKS = new HashMap<>();

    static void register() {
        JobSystem.register("trickster_swap", TricksterSkills::swap);
        JobSystem.register("trickster_dash", TricksterSkills::dash);
        JobSystem.register("trickster_phantom", TricksterSkills::phantom);
        JobSystem.register("trickster_palace", TricksterSkills::palace);
        JobSystem.register("trickster_ult", TricksterSkills::ult);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SWAPS.clear();
            MARKS.clear();
        });
    }

    /** 환영 몸 (짙은 검보라 — 직업 둘째 빛) */
    private static double[] illusionBody(Jobs.Job j) {
        return JobKit.core(j);
    }

    /** 환영 하나 (그림자 분신 — 청록 테두리): pose 0 섬 · 1 돌진 · 2 베기 · 3 찌르기 (던지기) */
    static void illusion(ServerPlayer sp, ServerLevel level, Vec3 feet, Vec3 face, int pose, Jobs.Job j, double life, double alpha) {
        RuneKit.shadeClone(sp, level, feet, face, pose, illusionBody(j), ILLUSION, Opt.of("life", life, "fade", 0.35, "alpha", alpha));
    }

    /** 들불을 두른 환영 (자홍 불꽃 테두리) */
    private static void fireClone(ServerPlayer sp, ServerLevel level, Vec3 feet, Vec3 face, int pose, Jobs.Job j, double life, double alpha) {
        RuneKit.shadeClone(sp, level, feet, face, pose, illusionBody(j), FIRE, Opt.of("life", life, "fade", 0.35, "alpha", alpha));
    }

    /** 환영이 깨짐: 거울 조각 (rfx_jmirror) · 검보라 연기 · 청록 반짝임 · 유리 소리 */
    static void shatter(ServerLevel level, Vec3 body, double size, Jobs.Job j) {
        JobFx.mirror(level, body, size, 0.45, Opt.empty(), ILLUSION, JobFx.inner(j));
        JobFx.smoke(level, body, size * 0.9, j, 0.5, Opt.of("alpha", 0.7));
        SkillFx.sparks(level, body, ILLUSION_CORE, 6, 3);
        SkillFx.play(level, "block.glass.break", body, 1.5, 0.45);
    }

    /** 머리 위 꽃 n 송이 (표식 — 터지기 전까지 그대로 보이게 life 초) */
    private static void heldFlowers(ServerLevel level, ServerPlayer sp, LivingEntity e, int n, Jobs.Job j, double life) {
        Vec3 over = RuneKit.overHead(e, 0.5);
        Vec3 right = V.rightOf(V.flat(V.sub(over, sp.getEyePosition())));
        for (int i = 0; i < n; i++) JobFx.blossom(level, V.add(over, right, (i - (n - 1) / 2.0) * 0.32), 0.15, j, life, Opt.of("vel", RuneKit.entVel(e), "fade", 0.2,
                "alpha", 0.9));
    }

    /** 꽃이 터짐: 머리 위 꽃 n 송이 자리에서 꽃잎이 흩어짐 */
    private static void flowersBurst(ServerLevel level, ServerPlayer sp, LivingEntity e, int n, Jobs.Job j) {
        Vec3 over = RuneKit.overHead(e, 0.5);
        Vec3 right = V.rightOf(V.flat(V.sub(over, sp.getEyePosition())));
        for (int i = 0; i < n; i++) JobFx.petals(level, V.add(over, right, (i - (n - 1) / 2.0) * 0.32), 4, 2.8, 0.13, j, 0.7, Opt.empty());
    }

    /** 적의 등 뒤 (온 쪽의 반대 — 1.3칸 + 몸) 에 설 자리: 막혔으면 ±35° · ±70° (없으면 null) */
    private static Vec3 behindSpot(ServerPlayer sp, LivingEntity e, Vec3 toward) {
        ServerLevel level = sp.level();
        double back = 1.3 + Kit.bodyPad(e);
        for (double turn : new double[] {0, 35, -35, 70, -70}) {
            Vec3 dir = V.turnY(toward, turn);
            Vec3 want = V.add(e.position(), dir, back);
            Vec3 s = Kit.standSpot(level, new Vec3(want.x, Math.max(want.y, e.getY()) + 0.6, want.z), 2.5);
            if (s == null || Math.abs(s.y - e.getY()) > 1.6) continue;
            if (!level.noCollision(sp, sp.getBoundingBox().move(s.x - sp.getX(), s.y - sp.getY(), s.z - sp.getZ()))) continue;
            return s;
        }
        return null;
    }

    /** 보는 쪽 dist 칸 (벽 · 낭떠러지 앞까지) 의 설 자리 (없으면 null) */
    private static Vec3 aheadSpot(ServerPlayer sp, Vec3 start, Vec3 f, double dist) {
        ServerLevel level = sp.level();
        double free = Kit.reachFlat(level, start, f, dist);
        java.util.List<Kit.Spot> spots = free > 0.4 ? Kit.dashSpots(level, start, f, free) : java.util.List.of();
        return spots.isEmpty() ? null : spots.get(spots.size() - 1).p();
    }

    /** 몸 · 머리를 적 쪽으로 (고개는 −25 ~ 40°) */
    private static void faceTo(ServerPlayer sp, LivingEntity e) {
        float[] rot = Kit.lookRotation(sp.getEyePosition(), Kit.bodyPoint(e));
        Kit.moveTo(sp, sp.position(), rot[0], Mth.clamp(rot[1], -25F, 40F));
    }

    // ------------------------------------------------------------ 섀도 스와프

    /**
     * 섀도 스와프: 보는 쪽 dist 칸 (겨눈 적이 range 칸 안이면 그 적의 등 뒤) 으로 내 환영을 던짐 — throwAt 틱부터 날아가 cutAt 틱에 적을 꿰뚫고 지나감 (cut · 표식 +1)
     * → 그 자리에 서서 이쪽을 봄 → gap 틱 뒤부터 window 틱 안에 다시 누르면 환영과 자리를 바꿔 (떠난 자리엔 깨지는 환영) 등 뒤에서 찌르며 표식을 모두 터뜨림 (base +
     * per × 표식 — 한 번에 넣고 숫자 1 + 표식 줄 · 거대한 X 검기, 다시 누른 뒤 stabAt 틱 — 환영이 벤 뒤 10틱 안이면 그만큼 미룸). 다시 누르지 않으면 환영이 깨짐.
     * 묶였거나 타고 있으면 자리를 바꾸지 못함 (찌르기만 — 환영 자리에서 벤 것으로)
     */
    static boolean swap(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (JobRecast.stage(sp, k.id()) >= 1) return swapStab(sp, j, k, P);
        ServerLevel level0 = sp.level();
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        LivingEntity e = Kit.pickTarget(sp, k.num("range", 8), 25);
        Vec3 toward = f;
        Vec3 dest = null;
        if (e != null) {
            Vec3 d = V.flat(V.sub(e.position(), start));
            if (V.len(d) > 0.1) toward = V.unit(d);
            dest = behindSpot(sp, e, toward);
        }
        if (dest == null) dest = aheadSpot(sp, start, f, k.num("dist", 6));
        if (dest == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 앞이 막혔습니다", 30);
            return false;
        }
        int throwAt = Math.max(0, (int) k.num("throwAt", 1));
        int cutAt = Math.max(throwAt + 1, (int) k.num("cutAt", 4));
        String life = RuneKit.lifeToken(sp);
        Vec3 face = e != null ? V.scale(toward, -1) : f;
        Swap s = new Swap(dest, face, e, JobKit.now(), cutAt);
        SWAPS.put(sp.getUUID(), s);
        Vec3 from = V.add(start, toward, 0.4);
        Vec3 to = dest;
        // 던짐: 손끝 번쩍 · 망토 휘두름 소리
        JobFx.glint(level0, V.up(RuneKit.handOf(sp).hand(), 0.1), 0.35, ILLUSION, ILLUSION_CORE, 0.25, Opt.of("vel", RuneKit.bodyVel(sp)));
        SkillFx.play(level0, "mob.phantom.flap", start, 1.4, 0.7);
        SkillFx.play(level0, "entity.enderman.teleport", start, 1.6, 0.5);
        // 환영이 날아감 (한 틱에 한 장 — 돌진 꼴 · 지나간 줄 · 꽃잎)
        int n = Math.max(1, cutAt - throwAt);
        for (int t = 0; t <= n; t++) {
            int tt = t;
            RuneKit.later(throwAt + t, () -> {
                if (s.done || !RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                double u = tt / (double) n;
                Vec3 p = V.lerp(from, to, u);
                illusion(sp, lv, p, V.flat(V.sub(to, from)), 1, j, 0.22, 0.75 + 0.2 * u);
                if (tt > 0) JobFx.line(lv, V.up(V.lerp(from, to, (tt - 1) / (double) n), 1.0), V.up(p, 1.0), j, 0.25, Opt.of("alpha", 0.7));
                JobFx.petals(lv, V.up(p, 1.0), 2, 1.6, 0.11, j, 0.5, Opt.empty());
            });
        }
        // 지나가며 벰 (cutAt — 표식 +1)
        if (e != null) {
            JobKit.strike(sp, e, j, P, k.num("cut", 0.35), false, false, life, cutAt, d -> {
                JobTraits3.mark(sp, e, j);
                if (!(e.level() instanceof ServerLevel lv)) return;
                JobFx.cross(lv, Kit.bodyPoint(e), 0.75 + Kit.bodyPad(e) * 0.4, j, 0.24, Opt.of("vel", RuneKit.entVel(e)));
                SkillFx.play(lv, "entity.player.attack.sweep", e.position(), 1.6, 0.6);
            });
        }
        // 서 있는 환영 (8틱마다 다시 — 이쪽을 봄 · 발밑 연기) — 다시 누르거나 창이 닫힐 때까지
        int window = Math.max(10, (int) k.num("window", 60));
        for (int t = cutAt; t < window + 2; t += 8) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (s.done || !RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 look = s.foe != null && s.foe.isAlive() ? V.flat(V.sub(s.foe.position(), s.at)) : s.face;
                illusion(sp, lv, s.at, V.len(look) < 1e-3 ? s.face : look, tt == cutAt ? 2 : 0, j, 0.48, 0.92);
                if (tt % 16 == cutAt % 16) JobFx.smoke(lv, V.up(s.at, 0.25), 0.6, j, 0.6, Opt.of("alpha", 0.45));
            });
        }
        JobRecast.open(sp, k, 1, window, (int) k.num("gap", 8), () -> {
            Swap left = SWAPS.remove(sp.getUUID());
            if (left == null || left.done) return;
            left.done = true;
            shatter(sp.level(), V.up(left.at, 1.0), 0.7, j);
        });
        JobSystem.note(sp, "§d다시 누르면 환영과 자리를 바꿈 §7(" + window / 20.0 + "초)");
        return true;
    }

    /** 섀도 스와프 2단계: 환영과 자리를 바꿔 등 뒤에서 찌름 (표식을 모두 터뜨림) */
    private static boolean swapStab(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        Swap s = SWAPS.remove(sp.getUUID());
        JobRecast.close(sp, k.id());
        JobRecast.motion(sp, k.id() + ":stab");
        if (s == null || s.done) return true;
        s.done = true;
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 old = sp.position();
        boolean free = !Status.isRooted(sp) && !Kit.ridingAny(sp);
        // 자리를 바꿈: 떠난 자리엔 깨지는 환영 · 두 자리의 연기 · 리본 · 유리 소리
        illusion(sp, level, old, Kit.facingOf(sp), 0, j, 0.3, 0.9);
        RuneKit.later(4, () -> shatter(level, V.up(old, 1.0), 0.7, j));
        JobFx.smoke(level, V.up(old, 0.9), 0.9, j, 0.5, JobFx.hide1p(sp));
        if (free) {
            Kit.noFall(sp, 20);
            Kit.moveTo(sp, s.at, sp.getYRot(), sp.getXRot());
        }
        Vec3 now = sp.position();
        JobFx.ribbon(level, V.up(now, 0.8), 1.0, j, 0.35, 26, JobFx.hide1p(sp).with("alpha", 0.9));
        JobFx.mirror(level, V.up(now, 1.0), 0.9, 0.4, Opt.empty(), ILLUSION, JobFx.inner(j));
        SkillFx.play(level, "entity.enderman.teleport", now, 1.2, 0.8);
        SkillFx.play(level, "block.amethyst_block.break", now, 1.3, 0.6);
        LivingEntity e = s.foe != null && s.foe.isAlive() && !s.foe.isRemoved() && s.foe.distanceTo(sp) <= 4.5 ? s.foe : nearestFoe(sp, now, 3.5);
        if (e == null) return true;
        faceTo(sp, e);
        // 찌르기: 다시 누른 뒤 stabAt 틱 (환영이 벤 뒤 10틱 안이면 그만큼 미룸) — 표식을 모두 터뜨림
        int max = j.trait() == null ? 7 : Math.max(1, j.trait().max());
        long due = Math.max(JobKit.now() + (long) k.num("stabAt", 2), s.cast + s.cutAt + 10);
        int delay = (int) Math.max(1, due - JobKit.now());
        RpgCombat.setSteadfast(sp, delay + 4);
        JobFx.charge(sp, j, new Vec3(0.35, 1.2, 0.5), delay, 0.5);
        RuneKit.later(delay, () -> {
            if (!RuneKit.sameLife(sp, life) || e.isRemoved() || !e.isAlive()) return;
            ServerLevel lv = sp.level();
            faceTo(sp, e);
            int nMarks = JobTraits3.takeMarks(sp, e);
            boolean full = nMarks >= max;
            double mult = k.num("base", 0.2) + k.num("per", 0.6) * nMarks;
            int lines = 1 + nMarks;
            heldFlowers(lv, sp, e, nMarks, j, 0.15 + lines * 0.1);
            JobKit.strikeSplit(sp, e, j, P, mult, lines, 2, false, full, life, 0, null);
            Vec3 b = Kit.bodyPoint(e);
            // 거대한 X 검기 (두 획) · 큰 베기 · 찌르기 번쩍 · 꽃이 터짐
            JobFx.bigWave(lv, sp, V.add(b, Kit.facingOf(sp), -0.3), Kit.facingOf(sp), 0, 1.5 + 0.08 * nMarks, 45, 0.45, j, Opt.empty());
            JobFx.bigWave(lv, sp, V.add(b, Kit.facingOf(sp), -0.25), Kit.facingOf(sp), 0, 1.5 + 0.08 * nMarks, -45, 0.45, j, Opt.empty());
            JobFx.slash(sp, j, 1.8, 0.3, 1.25, 30.0, 0.2);
            JobFx.stab(lv, b, 0.9 + Kit.bodyPad(e) * 0.4, j, 0.3, Opt.of("vel", RuneKit.entVel(e)));
            flowersBurst(lv, sp, e, nMarks, j);
            JobFx.rose(lv, b, 0.55 + Kit.bodyPad(e) * 0.4 + 0.05 * nMarks, j, 0.45 + lines * 0.05, Opt.of("vel", RuneKit.entVel(e)));
            SkillFx.play(lv, "entity.player.attack.crit", b, 1.1, 1.0);
            SkillFx.play(lv, "block.cherry_leaves.break", b, 0.9, 1.0);
            if (full) {
                JobFx.burst(lv, b, 1.5 + Kit.bodyPad(e) * 0.5, j, 0.35, Opt.empty());
                SkillFx.play(lv, "block.amethyst_block.chime", b, 0.8, 1.0);
            }
            JobSystem.note(sp, "§d표식 " + nMarks + " §7→ " + lines + "연격" + (full ? " §d(가득)" : ""));
        });
        return true;
    }

    /** at 둘레 r 칸의 가장 가까운 적 (없으면 null) */
    private static LivingEntity nearestFoe(ServerPlayer sp, Vec3 at, double r) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : Kit.around(sp, at, r, 2.5)) {
            double d = e.position().distanceToSqr(at);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    // ------------------------------------------------------------ 미라주 대시

    /**
     * 미라주 대시: 보는 쪽 dist 칸 (벽 · 낭떠러지 앞까지) 을 steps 틱에 뛰어듦 (지나간 길 폭 pass 의 적 표식 +1) → 내려선 자리 둘레 radius 칸을 환영 거울이 깨지듯 벰
     * (total · 표식 + marks) · 떠난 자리에 내 환영이 남음 → gap 틱 뒤부터 window 틱 안에 다시 누르면 처음 자리로 돌아옴 (환영이 깨짐). 묶였거나 앞이 막혀 한 칸도
     * 못 가면 쓰지 않음
     */
    static boolean dash(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (JobRecast.stage(sp, k.id()) >= 1) return dashBack(sp, j, k);
        ServerLevel level0 = sp.level();
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double free = Kit.reachFlat(level0, start, f, k.num("dist", 6));
        java.util.List<Kit.Spot> spots = free > 0.4 ? Kit.dashSpots(level0, start, f, free) : java.util.List.of();
        if (spots.isEmpty()) {
            Notify.notify(sp, "§c" + k.name() + " §7— 앞이 막혔습니다", 30);
            return false;
        }
        int steps = Math.max(1, (int) k.num("steps", 4));
        double len = spots.get(spots.size() - 1).s();
        java.util.List<Vec3> path = new java.util.ArrayList<>();
        for (int i = 1; i <= steps; i++) {
            double want = len * i / steps;
            Kit.Spot best = spots.get(0);
            for (Kit.Spot x : spots) if (Math.abs(x.s() - want) < Math.abs(best.s() - want)) best = x;
            path.add(best.p());
        }
        String life = RuneKit.lifeToken(sp);
        Mark mk = new Mark(start, f);
        MARKS.put(sp.getUUID(), mk);
        Kit.noFall(sp, steps + 20);
        RpgCombat.setSteadfast(sp, steps + 4);
        // 출발: 거울에 비친 듯 갈라지는 연기 · 소리
        JobFx.smoke(level0, V.up(start, 1.0), 0.9, j, 0.5, JobFx.hide1p(sp));
        JobFx.pane(level0, V.up(start, 1.05), f, 1.9, 0.3, Opt.of("alpha", 0.8), ILLUSION, ILLUSION_CORE);
        SkillFx.play(level0, "entity.enderman.teleport", start, 1.3, 0.7);
        SkillFx.play(level0, "mob.breeze.jump", start, 1.5, 0.6);
        // 지나간 적 표식 +1 (한 번씩)
        java.util.Set<LivingEntity> passed = new java.util.HashSet<>();
        for (int s = 0; s < path.size(); s++) {
            Vec3 to = path.get(s);
            int ss = s;
            RuneKit.later(s + 1, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 prev = sp.position();
                if (!Kit.moveTo(sp, to)) return;
                JobFx.line(lv, V.up(prev, 0.9), V.up(to, 0.9), j, 0.25, JobFx.hide1p(sp));
                JobFx.speed(sp, j, 0.12, 0.7);
                illusion(sp, lv, prev, f, 1, j, 0.25, 0.6);
                for (LivingEntity e : Kit.around(sp, V.lerp(prev, to, 0.5), k.num("pass", 1.2) + 0.5, 2)) if (passed.add(e)) JobTraits3.mark(sp, e, j);
                if (ss == path.size() - 1) landBurst(sp, j, k, P, life);
            });
        }
        // 떠난 자리의 환영 (8틱마다 — 돌아올 때까지)
        int window = Math.max(10, (int) k.num("window", 50));
        for (int t = 0; t < window + 2; t += 8) {
            RuneKit.later(t, () -> {
                if (mk.done || !RuneKit.sameLife(sp, life)) return;
                illusion(sp, sp.level(), mk.at, mk.face, 0, j, 0.48, 0.85);
            });
        }
        JobRecast.open(sp, k, 1, window, (int) k.num("gap", 6), () -> {
            Mark left = MARKS.remove(sp.getUUID());
            if (left == null || left.done) return;
            left.done = true;
            shatter(sp.level(), V.up(left.at, 1.0), 0.6, j);
        });
        JobSystem.note(sp, "§d다시 누르면 처음 자리로 돌아옴 §7(" + window / 20.0 + "초)");
        return true;
    }

    /** 미라주 대시가 내려섬: 둘레 radius 칸 — 바닥의 거울이 깨지듯 (거울 조각 · 청록 고리 · 엇갈린 베기) · total · 표식 + marks */
    private static void landBurst(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life) {
        ServerLevel lv = sp.level();
        Vec3 c = sp.position();
        double r = k.num("radius", 2.5);
        int marks = Math.max(0, (int) k.num("marks", 1));
        JobFx.mirror(lv, V.up(c, 0.8), r * 0.75, 0.45, Opt.empty(), ILLUSION, JobFx.inner(j));
        JobFx.ring(lv, V.up(c, 0.08), r, j, false, 0.4, Opt.empty());
        SkillFx.shock(lv, V.up(c, 0.06), ILLUSION, r / 2.1, 0.35);
        JobFx.petals(lv, V.up(c, 1.0), 8, 3.5, 0.13, j, 0.6, Opt.empty());
        JobFx.slash(sp, j, 1.7, 0.3, 1.2, 160.0, 0.2);
        SkillFx.play(lv, "block.glass.break", c, 1.2, 0.8);
        SkillFx.play(lv, "entity.player.attack.sweep", c, 1.3, 0.8);
        for (LivingEntity e : Kit.around(sp, c, r, 2.5)) {
            JobKit.strike(sp, e, j, P, k.total(), false, false, life, 0, d -> {
                for (int m = 0; m < marks; m++) JobTraits3.mark(sp, e, j);
                if (e.level() instanceof ServerLevel elv) JobFx.cross(elv, Kit.bodyPoint(e), 0.6 + Kit.bodyPad(e) * 0.35, j, 0.22, Opt.of("vel", RuneKit.entVel(e)));
            });
        }
    }

    /** 미라주 대시 2단계: 처음 자리로 돌아옴 (떠난 자리의 환영이 깨짐 · 지금 자리엔 잠깐 환영) */
    private static boolean dashBack(ServerPlayer sp, Jobs.Job j, Jobs.Skill k) {
        Mark mk = MARKS.remove(sp.getUUID());
        JobRecast.close(sp, k.id());
        JobRecast.motion(sp, k.id() + ":back");
        if (mk == null || mk.done) return true;
        mk.done = true;
        ServerLevel level = sp.level();
        Vec3 here = sp.position();
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            shatter(level, V.up(mk.at, 1.0), 0.6, j);
            return true;
        }
        Kit.noFall(sp, 20);
        illusion(sp, level, here, Kit.facingOf(sp), 0, j, 0.3, 0.8);
        RuneKit.later(3, () -> shatter(level, V.up(here, 1.0), 0.55, j));
        Kit.moveTo(sp, mk.at, sp.getYRot(), sp.getXRot());
        JobFx.line(level, V.up(here, 0.9), V.up(mk.at, 0.9), j, 0.3, Opt.of("alpha", 0.8));
        JobFx.mirror(level, V.up(mk.at, 1.0), 0.7, 0.4, Opt.empty(), ILLUSION, JobFx.inner(j));
        JobFx.ribbon(level, V.up(mk.at, 0.8), 0.9, j, 0.3, 26, JobFx.hide1p(sp).with("alpha", 0.85));
        SkillFx.play(level, "entity.enderman.teleport", mk.at, 1.6, 0.7);
        SkillFx.play(level, "block.amethyst_block.chime", mk.at, 1.5, 0.5);
        return true;
    }

    // ------------------------------------------------------------ 사우전드 팬텀

    /**
     * 사우전드 팬텀: 겨눈 적 (range 칸 — 없으면 쓰지 않음) 둘레 ring 칸에 내 환영 여섯이 둥글게 나타남 (손끝에서 튀어 나감) → first 틱부터 every 틱마다 packets 번 마주 선
     * 둘이 엇갈려 적을 꿰뚫고 반대편에 섬 (packet — 한 번에 넣고 숫자 두 줄 · 표식 + marks) — 꿰뚫은 자국이 발밑에 육망성으로 이어짐 → blinkAt 틱 나는 적의 등 뒤로
     * 순간 이동 (잔상) → xAt 틱 거대한 X 로 벰 (x — 육망성이 번쩍이며 깨지고 환영 여섯이 거울 조각으로)
     */
    static boolean phantom(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        double range = k.num("range", 8);
        LivingEntity e = Kit.pickTarget(sp, range, 25);
        if (e == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다 (" + Js.str(range) + "칸)", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        int first = Math.max(2, (int) k.num("first", 4));
        int every = Math.max(10, (int) k.num("every", 10));
        int packets = Math.max(1, (int) k.num("packets", 3));
        int blinkAt = Math.max(first + every * (packets - 1) + 2, (int) k.num("blinkAt", 28));
        int xAt = Math.max(blinkAt + 2, Math.max(first + every * (packets - 1) + 10, (int) k.num("xAt", 34)));
        double ring = k.num("ring", 3) + Kit.bodyPad(e);
        int marks = Math.max(0, (int) k.num("marks", 2));
        String life = RuneKit.lifeToken(sp);
        Vec3 c0 = e.position();
        Vec3 toward = V.flat(V.sub(c0, sp.position()));
        if (V.len(toward) < 0.1) toward = Kit.facingOf(sp);
        double a0 = Math.atan2(toward.z, toward.x) + Math.PI / 2;
        // 여섯 자리 (마주 선 둘씩 — 0 · 3, 1 · 4, 2 · 5 · 60° 씩)
        Vec3[] spots = new Vec3[6];
        for (int i = 0; i < 6; i++) {
            double a = a0 + i * Math.PI / 3;
            spots[i] = new Vec3(Math.cos(a), 0, Math.sin(a));
        }
        // 손끝에서 튀어 나가는 환영 여섯 · 땅의 고리 · 소리
        Vec3 hand = RuneKit.handOf(sp).hand();
        for (int i = 0; i < 6; i++) {
            Vec3 at = V.add(e.position(), spots[i], ring);
            JobFx.line(level0, hand, V.up(at, 1.0), j, 0.2, Opt.of("alpha", 0.6));
            SkillFx.sparks(level0, V.up(at, 1.0), ILLUSION_CORE, 3, 2.5);
        }
        JobFx.hexagram(level0, e.position(), ring, 0, 1, Math.max(0.2, first / 20.0), false, Opt.of("vel", RuneKit.entVel(e)), JobFx.outer(j), JobFx.inner(j));
        SkillFx.play(level0, "entity.illusioner.mirror_move", e.position(), 1.2, 0.9);
        SkillFx.play(level0, "entity.enderman.teleport", e.position(), 1.5, 0.6);
        // 서 있는 환영 (4틱마다 — 적을 봄, 꿰뚫은 둘은 반대편에 섬)
        int[] side = {1, 1, 1, 1, 1, 1};
        for (int t = 0; t < xAt; t += 4) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
                ServerLevel lv = sp.level();
                Vec3 c = e.position();
                for (int i = 0; i < 6; i++) {
                    Vec3 at = V.add(c, spots[i], ring * side[i]);
                    illusion(sp, lv, at, V.flat(V.sub(c, at)), 0, j, 0.24, 0.85);
                }
            });
        }
        // 엇갈려 꿰뚫음: 마주 선 둘 (2틱 — 잔상 줄 · 돌진 꼴) → 반대편에 섬
        for (int p = 0; p < packets; p++) {
            int pair = p;
            int at = first + p * every;
            RuneKit.later(at - 2, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
                ServerLevel lv = sp.level();
                Vec3 c = e.position();
                for (int q : new int[] {pair, pair + 3}) {
                    Vec3 from = V.add(c, spots[q], ring * side[q]);
                    Vec3 to = V.add(c, spots[q], -ring * side[q]);
                    for (int t = 0; t <= 2; t++) {
                        int tt = t;
                        RuneKit.later(t, () -> illusion(sp, lv, V.lerp(from, to, tt / 2.0), V.flat(V.sub(to, from)), 1, j, 0.16, 0.9));
                    }
                    JobFx.line(lv, V.up(from, 1.0), V.up(to, 1.0), j, 0.35, Opt.of("alpha", 0.9));
                    side[q] = -side[q];
                }
                SkillFx.play(lv, "entity.player.attack.sweep", c, 1.3 + pair * 0.15, 0.7);
            });
            JobKit.strikeSplit(sp, e, j, P, k.num("packet", 2.0), 2, 3, false, false, life, at, d -> {
                for (int m = 0; m < marks; m++) JobTraits3.mark(sp, e, j);
                if (!(e.level() instanceof ServerLevel lv)) return;
                Vec3 b = Kit.bodyPoint(e);
                JobFx.cross(lv, b, 0.8 + Kit.bodyPad(e) * 0.4, j, 0.24, Opt.of("vel", RuneKit.entVel(e), "spin", pair * 60.0));
                JobFx.petals(lv, b, 4, 2.6, 0.12, j, 0.6, Opt.empty());
                SkillFx.play(lv, "block.amethyst_block.hit", b, 1.4 + pair * 0.1, 0.6);
            });
            // 육망성이 한 획씩 이어짐 (꿰뚫은 자국)
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
                ServerLevel lv = sp.level();
                JobFx.hexagram(lv, e.position(), ring, pair == packets - 1 ? 1 : 0, 1, Math.max(0.2, (pair == packets - 1 ? xAt - at : every) / 20.0 + 0.05),
                        false, Opt.of("vel", RuneKit.entVel(e), "alpha", 0.85), JobFx.outer(j), JobFx.inner(j));
            });
        }
        // 나는 등 뒤로 순간 이동 (잔상) → 거대한 X
        RuneKit.later(blinkAt, () -> {
            if (!RuneKit.sameLife(sp, life) || e.isRemoved() || !e.isAlive()) return;
            ServerLevel lv = sp.level();
            Vec3 from = sp.position();
            Vec3 tw = V.flat(V.sub(e.position(), from));
            Vec3 behind = Status.isRooted(sp) || Kit.ridingAny(sp) ? null : behindSpot(sp, e, V.len(tw) < 0.1 ? Kit.facingOf(sp) : V.unit(tw));
            if (behind != null) {
                Kit.noFall(sp, 20);
                illusion(sp, lv, from, Kit.facingOf(sp), 1, j, 0.3, 0.7);
                Kit.moveTo(sp, behind, sp.getYRot(), sp.getXRot());
                JobFx.line(lv, V.up(from, 1.0), V.up(behind, 1.0), j, 0.3, Opt.empty());
                SkillFx.play(lv, "entity.enderman.teleport", behind, 1.4, 0.7);
            }
            faceTo(sp, e);
            JobKit.motion(sp, "trickster_phantom:x");
            JobFx.charge(sp, j, new Vec3(0.3, 1.3, 0.4), xAt - blinkAt, 0.55);
        });
        JobKit.strike(sp, e, j, P, k.num("x", 2.55), false, false, life, xAt, d -> {
            if (!(e.level() instanceof ServerLevel lv)) return;
            faceTo(sp, e);
            Vec3 b = Kit.bodyPoint(e);
            Vec3 f = Kit.facingOf(sp);
            JobFx.bigWave(lv, sp, V.add(b, f, -0.3), f, 0, 1.7, 45, 0.45, j, Opt.empty());
            JobFx.bigWave(lv, sp, V.add(b, f, -0.25), f, 0, 1.7, -45, 0.45, j, Opt.empty());
            JobFx.slash(sp, j, 1.8, 0.3, 1.3, 330.0, 0.2);
            JobFx.hexagram(lv, e.position(), ring, 4, 2, 0.5, false, Opt.of("vel", RuneKit.entVel(e)), JobFx.outer(j), JobFx.inner(j));
            for (int i = 0; i < 6; i++) shatter(lv, V.up(V.add(e.position(), spots[i], ring * side[i]), 1.0), 0.5, j);
            JobFx.burst(lv, b, 1.3 + Kit.bodyPad(e) * 0.5, j, 0.35, Opt.empty());
            JobFx.petals(lv, b, 10, 3.5, 0.14, j, 0.8, Opt.empty());
            SkillFx.play(lv, "entity.player.attack.crit", b, 0.9, 1.0);
            SkillFx.play(lv, "block.glass.break", b, 0.9, 1.0);
        });
        JobSystem.note(sp, "§d환영 여섯 §7· 표식 +" + marks * packets);
        return true;
    }

    // ------------------------------------------------------------ 미러 팰리스

    /** 미러 팰리스: dur 틱 태세 (도는 거울 · 어느 쪽에서든 등 뒤 · 거울 베기 — JobTraits10) — 땅에서 거울 여섯이 솟음 · 유리 울림 */
    static boolean palace(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = Math.max(20, (int) JobUpgrades.num(sp, k, "dur", k.num("dur", 160)));
        JobTraits.startStance(sp, k, dur);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        double r = k.num("radius", 2.6);
        int n = Math.max(3, (int) k.num("mirrors", 6));
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 at = V.add(c, out, r);
            JobFx.glint(level, V.up(at, 1.2), 0.5, ILLUSION, ILLUSION_CORE, 0.35, Opt.empty());
            SkillFx.sparks(level, V.up(at, 0.4), ILLUSION_CORE, 3, 2.0);
        }
        JobFx.halo(level, V.up(c, 0.06), r, j, 0.6, Opt.of("vel", RuneKit.bodyVel(sp)));
        JobFx.ribbon(level, V.up(c, 0.9), 1.1, j, 0.5, 22, JobFx.hide1p(sp).with("alpha", 0.8));
        SkillFx.play(level, "block.amethyst_block.resonate", c, 1.0, 1.0);
        SkillFx.play(level, "entity.illusioner.prepare_mirror", c, 1.1, 0.9);
        return true;
    }

    // ------------------------------------------------------------ 와일드파이어 피스트

    /** 와일드파이어 피스트 자신의 타격 (들불 표식에 쌓지 않음 — JobTraits10.ownFire): delay 틱 뒤 (그 적이 무적 시간이면 끝난 뒤) */
    private static void ownHit(ServerPlayer sp, LivingEntity e, Jobs.Job j, double P, double mult, String life, int delay, java.util.function.DoubleConsumer then) {
        JobKit.hitLater(sp, e, life, JobKit.now() + Math.max(0, delay), () -> {
            double d;
            JobTraits10.ownFire = true;
            try {
                d = JobKit.hit(sp, e, j, P, mult, false, false);
            } finally {
                JobTraits10.ownFire = false;
            }
            if (d > 0 && then != null) then.accept(d);
        });
    }

    /**
     * 와일드파이어 피스트: 들불 하늘 (rpg:ult_wildfire) · 겨눈 적 (range 칸 — 없으면 쓰지 않음) 에게 순간 이동해 (등 뒤가 아니라 앞 1.2칸) slashAt 틱 베며 (slash) 들불
     * 표식을 새김 (finalAt 틱까지 — 그동안 다른 공격으로 그 적에게 준 피해를 쌓음 · JobTraits10) → first 틱부터 every 틱마다 flames 번 들불을 두른 내 환영이 사방에서
     * 꿰뚫음 (flame · 표식 +1) → finalAt 틱 들불이 터지며 표식을 모두 터뜨림 (base + per × 표식) + 쌓인 피해 × store (큰 숫자 · 불기둥)
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        double range = k.num("range", 10);
        LivingEntity e = Kit.pickTarget(sp, range, 30);
        if (e == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다 (" + Js.str(range) + "칸)", 30);
            return false;
        }
        ServerLevel level0 = sp.level();
        int slashAt = Math.max(1, (int) k.num("slashAt", 2));
        int first = Math.max(slashAt + 10, (int) k.num("first", 12));
        int every = Math.max(10, (int) k.num("every", 10));
        int flames = Math.max(1, (int) k.num("flames", 4));
        int finalAt = Math.max(first + every * (flames - 1) + 10, (int) k.num("finalAt", 62));
        String life = RuneKit.lifeToken(sp);
        Ult.sky(level0, sp.position(), "rpg:ult_wildfire", finalAt + 30);
        // 순간 이동: 적 앞 1.2칸 (적과 나 사이) — 떠난 자리엔 불꽃 잔상
        Vec3 start = sp.position();
        double dist = V.hdist(e.position(), start); // V.flat 은 단위 방향 — 거리는 hdist
        Vec3 u = dist < 0.1 ? Kit.facingOf(sp) : V.flat(V.sub(e.position(), start));
        if (!Status.isRooted(sp) && !Kit.ridingAny(sp)) {
            double in = Math.max(0, dist - 1.2 - Kit.bodyPad(e));
            if (in > 0.3) {
                Vec3 to = V.add(start, u, Math.min(in, RuneKit.dashDistance(sp, u, in, 0.3, 1.5)));
                fireClone(sp, level0, start, u, 1, j, 0.35, 0.8);
                Kit.noFall(sp, 30);
                Kit.moveTo(sp, to, sp.getYRot(), sp.getXRot());
                JobFx.line(level0, V.up(start, 1.0), V.up(to, 1.0), FIRE, FIRE_CORE, 0.3, Opt.empty());
            }
        }
        faceTo(sp, e);
        RpgCombat.setSteadfast(sp, slashAt + 6);
        SkillFx.play(level0, "entity.blaze.shoot", sp.position(), 0.8, 1.0);
        SkillFx.play(level0, "entity.enderman.teleport", sp.position(), 0.9, 0.8);
        JobTraits10.startFire(sp, e, finalAt + 2);
        // 들불 표식: 머리 위 · 발밑 불꽃 고리 (6틱마다 다시 — 터질 때까지)
        for (int t = slashAt; t < finalAt; t += 6) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved() || !(e.level() instanceof ServerLevel lv)) return;
                Vec3 over = RuneKit.overHead(e, 0.9);
                JobFx.maw(lv, over, 0.75 + Kit.bodyPad(e) * 0.3, tt == slashAt ? 0 : 2, tt == slashAt ? 2 : 4, tt == slashAt ? 0.3 : 0.36, tt != slashAt, Opt.of("vel",
                        RuneKit.entVel(e), "fade", 0.0), FIRE, FIRE_CORE);
                double rr = 1.0 + Kit.bodyPad(e);
                for (int i = 0; i < 5; i++) {
                    double a = tt * 0.15 + i * Math.PI * 2 / 5;
                    Vec3 p = V.add(e.position(), new Vec3(Math.cos(a), 0, Math.sin(a)), rr);
                    JobFx.flame(lv, p, 0.28, 0.36, Opt.of("vel", RuneKit.entVel(e)), FIRE, FIRE_CORE);
                }
                if (tt % 18 == slashAt % 18) SkillFx.play(lv, "block.fire.ambient", e.position(), 1.2, 0.7);
            });
        }
        // 베며 새김
        ownHit(sp, e, j, P, k.num("slash", 6), life, slashAt, d -> {
            if (!(e.level() instanceof ServerLevel lv)) return;
            Vec3 b = Kit.bodyPoint(e);
            JobFx.bigWave(lv, sp, V.add(b, Kit.facingOf(sp), -0.3), Kit.facingOf(sp), 0, 1.6, 20, 0.4, j, FIRE, FIRE_CORE, Opt.empty());
            JobFx.slash(sp, j, 1.8, 0.3, 1.25, 200.0, 0.2);
            JobFx.burst(lv, b, 1.1, FIRE, FIRE_CORE, 0.3, Opt.empty());
            SkillFx.play(lv, "entity.player.attack.crit", b, 1.0, 1.0);
            SkillFx.play(lv, "item.firecharge.use", b, 1.2, 0.8);
        });
        // 들불 환영 넷: 사방에서 꿰뚫음 (0.1초 — 불꽃 꼬리)
        Vec3 r = V.rightOf(u);
        Vec3[] dirs = {r, V.scale(u, -1), V.scale(r, -1), u};
        for (int i = 0; i < flames; i++) {
            int at = first + i * every;
            Vec3 d0 = dirs[i % dirs.length];
            RuneKit.later(at - 2, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
                ServerLevel lv = sp.level();
                double rr = 2.6 + Kit.bodyPad(e);
                Vec3 from = V.add(e.position(), d0, rr);
                Vec3 to = V.add(e.position(), d0, -rr);
                for (int t = 0; t <= 2; t++) {
                    int tt = t;
                    RuneKit.later(t, () -> fireClone(sp, lv, V.lerp(from, to, tt / 2.0), V.scale(d0, -1), 1, j, 0.2, 0.9));
                }
                JobFx.comet(lv, V.up(to, 1.0), V.scale(d0, -1), 0.9, 40, 0.15, FIRE, FIRE_CORE, Opt.empty());
                JobFx.line(lv, V.up(from, 1.0), V.up(to, 1.0), FIRE, FIRE_CORE, 0.3, Opt.empty());
                SkillFx.play(lv, "entity.blaze.shoot", e.position(), 1.3, 0.6);
            });
            ownHit(sp, e, j, P, k.num("flame", 1.5), life, at, d -> {
                JobTraits3.mark(sp, e, j);
                if (!(e.level() instanceof ServerLevel lv)) return;
                Vec3 b = Kit.bodyPoint(e);
                JobFx.cross(lv, b, 0.8 + Kit.bodyPad(e) * 0.4, j, 0.24, Opt.of("vel", RuneKit.entVel(e)));
                JobFx.flame(lv, e.position(), 0.5 + Kit.bodyPad(e) * 0.3, 0.4, Opt.of("vel", RuneKit.entVel(e)), FIRE, FIRE_CORE);
            });
        }
        // 터짐: 표식을 모두 + 쌓인 피해의 store
        JobKit.motionLater(sp, "trickster_ult:final", finalAt - 4, life);
        RuneKit.later(finalAt - 4, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.charge(sp, j, new Vec3(0.3, 1.4, 0.4), 4, 0.6);
        });
        RuneKit.later(finalAt, () -> {
            if (!RuneKit.sameLife(sp, life) || e.isRemoved() || !e.isAlive()) {
                if (!e.isAlive()) JobTraits10.takeFire(sp, e);
                return;
            }
            ServerLevel lv = sp.level();
            int nMarks = JobTraits3.takeMarks(sp, e);
            double stored = JobTraits10.takeFire(sp, e);
            double mult = k.num("base", 12) + k.num("per", 2) * nMarks;
            double extra = stored * k.num("store", 0.15);
            JobKit.strikeSplit(sp, e, j, P, mult, 1 + nMarks, 2, false, true, life, 0, null);
            if (extra > 0) {
                // 쌓인 피해의 몫: 위력과 상관없는 고정 피해 (숫자 따로 — 직업 색)
                JobKit.strike(sp, e, j, extra, 1.0, true, true, life, 0, null);
            }
            Vec3 b = Kit.bodyPoint(e);
            Vec3 g = e.position();
            JobFx.maw(lv, V.up(b, 0.4), 1.4 + Kit.bodyPad(e) * 0.5, 6, 2, 0.45, false, Opt.empty(), FIRE, FIRE_CORE);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                JobFx.flame(lv, V.add(g, new Vec3(Math.cos(a), 0, Math.sin(a)), 0.8 + Kit.bodyPad(e)), 0.7, 0.6, Opt.empty(), FIRE, FIRE_CORE);
            }
            JobFx.flame(lv, g, 1.3 + Kit.bodyPad(e) * 0.5, 0.7, Opt.empty(), FIRE, FIRE_CORE);
            JobFx.burst(lv, b, 2.0 + Kit.bodyPad(e) * 0.6, FIRE, FIRE_CORE, 0.45, Opt.empty());
            SkillFx.shock(lv, V.up(g, 0.06), FIRE, 1.6, 0.5);
            flowersBurst(lv, sp, e, nMarks, j);
            JobFx.petals(lv, b, 14, 4.5, 0.15, j, 0.9, Opt.empty());
            SkillFx.play(lv, "entity.generic.explode", b, 1.2, 0.9);
            SkillFx.play(lv, "item.firecharge.use", b, 0.7, 1.0);
            SkillFx.play(lv, "block.amethyst_block.chime", b, 0.7, 1.0);
            JobSystem.note(sp, "§d표식 " + nMarks + " §7· 들불 " + Math.round(stored) + " → +" + Math.round(extra));
        });
        return true;
    }
}
