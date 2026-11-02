package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.core.Js;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.mob.Monsters;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 암살자 각성 우트가르드(트릭스터) 직업 스킬 (jobs.json advance.trickster — 자홍 · 검보라 + 청록 환영 테두리, 특성 「표식」 최대 7 · 옮겨감 — JobTraits3 의 암살자
 * 줄기): 검은 장미 덤불 · 우트가르드의 환술 · 요르문간드의 똬리 · 끝없는 밤 · 개기일식. 1차 암살자 컨셉 (표식을 쌓아 터뜨림 · 환영 · 독 · 은신 · 일식) 그대로 더
 * 역동적인 동작 — 적의 머리 위로 넘어 등 뒤를 잡음 · 환영 둘 · 갔다가 되돌아오는 S 자 · 쓰러뜨리면 다시 숨음 · 환영 셋과 함께 던지는 비수.
 * 피해 = 위력 P × 배율 (JobKit.strike — 같은 적은 10틱 넘게 띄움, 한꺼번에 여러 줄은 strikeSplit). 연출 = 2D 그림 (꽃 · 꽃잎 · 장미 · 엇갈린 베기 · 비수 · 그림자 분신
 * (환영은 청록 테두리) · 환영이 깨지는 거울 조각 rfx_jmirror · 뱀의 똬리 rfx_jcoil · 검은 해 · 다시 뜨는 해 rfx_jdawn · 망토 소용돌이 rfx_jribbon).
 */
final class TricksterSkills {
    private TricksterSkills() {}

    /** 환영의 테두리 (청록) · 그 심지 */
    static final double[] ILLUSION = {0.3, 0.95, 0.9};
    static final double[] ILLUSION_CORE = {0.8, 1.0, 0.97};
    /** 다시 뜨는 해 (흰 금빛 — 개기일식 끝의 번쩍임) */
    static final double[] DAWN = {1.0, 0.86, 0.55};
    static final double[] DAWN_CORE = {1.0, 0.98, 0.9};
    /** 독 (요르문간드 — 1차 독니와 같은 초록) */
    private static final double[] VENOM = {0.3, 0.78, 0.2};
    private static final double[] VENOM_CORE = {0.78, 1.0, 0.52};

    static void register() {
        JobSystem.register("trickster_rose", TricksterSkills::rose);
        JobSystem.register("trickster_mirage", TricksterSkills::mirage);
        JobSystem.register("trickster_coil", TricksterSkills::coil);
        JobSystem.register("trickster_night", TricksterSkills::night);
        JobSystem.register("trickster_ult", TricksterSkills::ult);
    }

    /** 환영 몸 (짙은 검보라 — 직업 둘째 빛) */
    private static double[] illusionBody(Jobs.Job j) {
        return JobKit.core(j);
    }

    /** 환영 하나 (그림자 분신 — 청록 테두리): pose 0 섬 · 1 돌진 · 2 베기 · 3 찌르기 (던지기) */
    static void illusion(ServerPlayer sp, ServerLevel level, Vec3 feet, Vec3 face, int pose, Jobs.Job j, double life, double alpha) {
        RuneKit.shadeClone(sp, level, feet, face, pose, illusionBody(j), ILLUSION, Opt.of("life", life, "fade", 0.35, "alpha", alpha));
    }

    /** 환영이 깨짐: 거울 조각 (rfx_jmirror) · 검보라 연기 · 청록 반짝임 · 유리 소리 */
    static void shatter(ServerLevel level, Vec3 body, double size, Jobs.Job j) {
        JobFx.mirror(level, body, size, 0.45, Opt.empty(), ILLUSION, JobFx.inner(j));
        JobFx.smoke(level, body, size * 0.9, j, 0.5, Opt.of("alpha", 0.7));
        SkillFx.sparks(level, body, ILLUSION_CORE, 6, 3);
        SkillFx.play(level, "block.glass.break", body, 1.5, 0.45);
    }

    /** 망토를 휘둘러 사라짐 (끝없는 밤 · 다시 은신): 몸 둘레 검보라 망토 소용돌이 (rfx_jribbon) · 그림자 연기 · 흩어지는 환영 · 꽃잎 · 청록 반짝임 */
    static void cloak(ServerPlayer sp, Jobs.Job j, double scale) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        JobFx.ribbon(level, V.up(c, 0.9), 1.1 * scale, j, 0.45, 26, JobFx.hide1p(sp).with("alpha", 0.95));
        JobFx.ribbon(level, V.up(c, 0.35), 1.35 * scale, j, 0.5, 22, JobFx.hide1p(sp).with("alpha", 0.75, "spin", 120.0));
        JobFx.smoke(level, V.up(c, 1.0), 1.1 * scale, j, 0.7, JobFx.hide1p(sp));
        JobFx.smoke(level, V.up(c, 0.3), 1.3 * scale, j, 0.8, JobFx.hide1p(sp).with("alpha", 0.8));
        illusion(sp, level, V.add(c, f, -0.7), f, 0, j, 0.55, 0.85);
        JobFx.petals(level, V.up(c, 1.2), (int) Math.round(8 * scale), 2.2, 0.12, j, 0.8, Opt.empty());
        SkillFx.sparks(level, V.up(c, 1.0), ILLUSION_CORE, 6, 2.5);
        JobFx.eyeRing(sp, j, 2.4, 0.9, 0.45);
        Vec3 look = sp.getViewVector(1F);
        JobFx.smoke(level, V.add(sp.getEyePosition(), look, 1.8), 0.9, j, 0.45, JobFx.only1p(sp).with("alpha", 0.6));
        SkillFx.play(level, "entity.enderman.teleport", c, 0.7, 0.7);
        SkillFx.play(level, "mob.phantom.flap", c, 0.75, 0.7);
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

    // ------------------------------------------------------------ 검은 장미 덤불

    /**
     * 검은 장미 덤불: 겨눈 적 (range 칸) 에게 파고들어 (cutAt 틱 — 앞 1.3칸까지) 벰 (cut) → vaultAt 틱부터 적의 머리 위로 넘어 등 뒤에 내려앉음 (포물선 · 둘째 반에
     * 적 쪽으로 돌아섬) → burstAt 틱에 표식을 모두 터뜨림 (base + per × 표식 — 한 번에 넣고 숫자 1 + 표식 줄, gap 틱마다) · 반대쪽 (처음 온 쪽) 에 내 환영이 나타나
     * 같이 벰 (청록 테두리 — 6틱 뒤 거울 조각으로 깨짐) · 적의 몸에 검은 장미 셋이 덤불처럼 피었다 흩어짐. 표식은 쓸 때 떼어 냄 (머리 위 꽃은 터질 때까지 보임).
     * 묶였거나 타고 있으면 제자리에서 (피해는 그대로). 등 뒤에 설 자리가 없으면 넘지 않음. 겨눈 적이 없으면 쓰지 않음
     */
    static boolean rose(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        double range = k.num("range", 5);
        LivingEntity e = Kit.pickTarget(sp, range, 25);
        if (e == null) {
            Notify.notify(sp, "§c" + k.name() + " §7— 겨눈 적이 없습니다 (" + Js.str(range) + "칸)", 30);
            return false;
        }
        ServerLevel level = sp.level();
        int max = j.trait() == null ? 7 : Math.max(1, j.trait().max());
        int cutAt = Math.max(1, (int) k.num("cutAt", 2));
        int vaultAt = Math.max(cutAt + 1, (int) k.num("vaultAt", 4));
        int burstAt = Math.max(vaultAt + 4, (int) k.num("burstAt", 12));
        int gap = Math.max(1, (int) k.num("gap", 2));
        String life = RuneKit.lifeToken(sp);
        Vec3 start = sp.position();
        double dist0 = Math.hypot(e.getX() - start.x, e.getZ() - start.z);
        Vec3 toward = dist0 < 0.1 ? Kit.facingOf(sp) : V.flat(V.sub(e.position(), start));
        // 표식: 쓸 때 떼어 냄 — 머리 위 꽃은 터질 때까지 그대로 보임
        int n = JobTraits3.takeMarks(sp, e);
        boolean full = n >= max;
        double mult = k.num("base", 0.2) + k.num("per", 0.6) * n;
        int lines = 1 + n;
        heldFlowers(level, sp, e, n, j, burstAt / 20.0 + 0.05);
        // 몸 움직임: 파고듦 → 잠깐 → 머리 위로 넘어 등 뒤
        boolean free = !Status.isRooted(sp) && !Kit.ridingAny(sp);
        double in = dist0 - 1.3 - Kit.bodyPad(e);
        Vec3 near = free && in > 0.3 ? V.add(start, toward, Math.min(in, RuneKit.dashDistance(sp, toward, in, 0.3, 1.5))) : start;
        Vec3 behind = free ? behindSpot(sp, e, toward) : null;
        if (behind != null) {
            double peak = Math.max(1.5, e.getBbHeight() + 0.8);
            JobMove.run(sp, JobMove.to(0, cutAt, JobMove.at(near), JobMove.OUT), JobMove.hold(cutAt, vaultAt), JobMove.hop(vaultAt, burstAt - 1, JobMove.at(behind),
                    peak));
            faceDuring(sp, e, (vaultAt + burstAt) / 2, burstAt, life);
        } else if (near != start) {
            JobMove.run(sp, JobMove.to(0, cutAt, JobMove.at(near), JobMove.OUT), JobMove.hold(cutAt, burstAt));
        }
        RpgCombat.setSteadfast(sp, burstAt + 4);
        // 1) 파고들어 벰
        JobKit.strike(sp, e, j, P, k.num("cut", 0.35), false, false, life, cutAt, d -> {
            if (!(e.level() instanceof ServerLevel lv)) return;
            JobFx.cross(lv, Kit.bodyPoint(e), 0.7 + Kit.bodyPad(e) * 0.4, j, 0.24, Opt.of("vel", RuneKit.entVel(e)));
            JobFx.hit(e, j, 1.0);
        });
        RuneKit.later(cutAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            JobFx.slash(sp, j, 1.8, 0.3, 1.15, 25.0, 0.18);
            JobFx.petals(sp.level(), Kit.bodyPoint(e), 4, 2.6, 0.12, j, 0.6, Opt.empty());
            SkillFx.play(sp.level(), "entity.player.attack.sweep", e.position(), 1.5, 0.7);
        });
        // 2) 넘어감: 떠난 자리 잔상 · 발밑 연기 · 위로 솟는 줄 · 단계 동작
        if (behind != null) {
            RuneKit.later(vaultAt, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 p = sp.position();
                RuneKit.shadeClone(sp, lv, p, toward, 1, JobKit.core(j), JobKit.glow(j), Opt.of("life", 0.4, "fade", 0.6, "alpha", 0.8));
                JobFx.smoke(lv, V.up(p, 0.3), 0.8, j, 0.5, JobFx.hide1p(sp).with("alpha", 0.8));
                JobFx.petals(lv, V.up(p, 1.0), 6, 2.4, 0.12, j, 0.6, Opt.empty());
                SkillFx.play(lv, "mob.breeze.jump", p, 1.4, 0.6);
                SkillFx.play(lv, "mob.phantom.flap", p, 1.3, 0.6);
                JobKit.motion(sp, "trickster_rose:vault");
            });
            for (int t = vaultAt + 1; t < burstAt; t++) {
                RuneKit.later(t, () -> {
                    if (RuneKit.sameLife(sp, life)) JobFx.speed(sp, j, 0.1, 0.7);
                });
            }
        }
        // 3) 등 뒤에서 표식을 터뜨림 · 반대쪽 환영이 같이 벰 · 검은 장미 덤불
        JobKit.motionLater(sp, "trickster_rose:burst", burstAt - 1, life);
        RuneKit.later(burstAt, () -> {
            if (!RuneKit.sameLife(sp, life) || e.isRemoved() || !e.isAlive()) return;
            ServerLevel lv = sp.level();
            if (behind != null) {
                float[] rot = Kit.lookRotation(sp.getEyePosition(), Kit.bodyPoint(e));
                Kit.moveTo(sp, sp.position(), rot[0], Mth.clamp(rot[1], -25F, 40F));
            }
            JobKit.strikeSplit(sp, e, j, P, mult, lines, gap, false, full, life, 0, null);
            flowersBurst(lv, sp, e, n, j);
            // 반대쪽 (처음 온 쪽) 환영: 적을 보고 벰 → 6틱 뒤 깨짐
            Vec3 fromSide = V.flat(V.sub(start, e.position()));
            if (Math.hypot(start.x - e.getX(), start.z - e.getZ()) < 0.2) fromSide = V.scale(toward, -1);
            Vec3 opp = V.add(e.position(), fromSide, 1.2 + Kit.bodyPad(e));
            Vec3 oppFace = V.scale(fromSide, -1);
            illusion(sp, lv, opp, oppFace, 2, j, 0.4, 0.92);
            Vec3 oppBody = V.up(opp, 1.0);
            JobFx.line(lv, V.up(opp, 1.1), Kit.bodyPoint(e), j, 0.2, Opt.of("alpha", 0.8));
            RuneKit.later(6, () -> shatter(lv, oppBody, 0.55, j));
            // 검은 장미 덤불: 몸 둘레에 셋이 피었다 흩어짐 (표식이 많을수록 크게)
            double hold = Math.max(0.45, (lines - 1) * gap / 20.0 + 0.35);
            Vec3 b = Kit.bodyPoint(e);
            Vec3 side = V.rightOf(V.flat(V.sub(b, sp.getEyePosition())));
            double rs = 0.5 + Kit.bodyPad(e) * 0.4 + 0.06 * n;
            JobFx.rose(lv, b, rs, j, hold, Opt.of("vel", RuneKit.entVel(e)));
            JobFx.rose(lv, V.add(V.up(b, -0.35), side, 0.45 + Kit.bodyPad(e) * 0.3), rs * 0.7, j, hold * 0.9, Opt.of("vel", RuneKit.entVel(e), "spin", -25.0));
            JobFx.rose(lv, V.add(V.up(b, 0.3), side, -0.45 - Kit.bodyPad(e) * 0.3), rs * 0.6, j, hold * 0.85, Opt.of("vel", RuneKit.entVel(e), "spin", 25.0));
            JobFx.slash(sp, j, 1.8, 0.3, 1.2, 200.0, 0.18);
            RuneKit.later(gap, () -> {
                if (RuneKit.sameLife(sp, life)) JobFx.slash(sp, j, 1.8, 0.3, 1.2, 20.0, 0.18);
            });
            int last = lines - 1;
            for (int i = 0; i < lines; i++) {
                int ii = i;
                RuneKit.later(i * gap, () -> {
                    if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
                    ServerLevel d = sp.level();
                    Vec3 at = Kit.bodyPoint(e);
                    Vec3 jit = new Vec3((Kit.rand() - 0.5) * 0.4, (Kit.rand() - 0.5) * 0.5, (Kit.rand() - 0.5) * 0.4);
                    JobFx.cross(d, V.add(at, jit), 0.7 + Kit.bodyPad(e) * 0.4 + (ii == last ? 0.3 : 0), j, 0.26, Opt.of("vel", RuneKit.entVel(e)));
                    JobFx.petals(d, at, ii == last ? 9 : 3, 3.0, 0.13, j, 0.65, Opt.empty());
                    SkillFx.play(d, "entity.player.attack.sweep", at, 1.3 + ii * 0.1, 0.7);
                    if (ii == last) {
                        SkillFx.play(d, "block.cherry_leaves.break", at, 0.9, 1.0);
                        if (full) {
                            JobFx.burst(d, at, 1.4 + Kit.bodyPad(e) * 0.5, j, 0.35, Opt.empty());
                            JobFx.blossom(d, at, 1.0, j, 0.45, Opt.of("grow", 0.6, "fade", 0.5));
                            SkillFx.play(d, "entity.player.attack.crit", at, 0.8, 1.0);
                        }
                    }
                });
            }
        });
        JobSystem.note(sp, "§d표식 " + n + " §7→ " + lines + "연격" + (full ? " §d(가득)" : ""));
        return true;
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

    /** from 틱부터 to 틱까지 매 틱 적 쪽으로 조금씩 돌아섬 (남은 틱으로 나눠 — 마지막 틱에 적을 봄) */
    private static void faceDuring(ServerPlayer sp, LivingEntity e, int from, int to, String life) {
        for (int t = from; t <= to; t++) {
            int left = to - t + 1;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
                float[] rot = Kit.lookRotation(sp.getEyePosition(), Kit.bodyPoint(e));
                float dy = Mth.wrapDegrees(rot[0] - sp.getYRot());
                float yaw = sp.getYRot() + dy / left;
                float pitch = sp.getXRot() + (Mth.clamp(rot[1], -25F, 40F) - sp.getXRot()) / left;
                Kit.moveTo(sp, sp.position(), yaw, pitch);
            });
        }
    }

    // ------------------------------------------------------------ 우트가르드의 환술

    /**
     * 우트가르드의 환술: 보는 쪽 dist 칸 (벽 · 낭떠러지 앞까지) 을 steps 틱에 순간 이동 (적은 지나침) — 원래 자리 양옆 (side 칸) 에 환영 둘 life 틱 (청록 테두리 · 나와
     * 싸우던 둘레 taunt 칸 몹이 원래 자리로 감 — Monsters.decoy). 적이 어느 환영에든 touch 칸 안에 닿거나 life 틱이 지나면 둘 다 터짐 (환영마다 둘레 radius 칸 blast —
     * 둘 다 맞은 적은 한 번에 넣고 숫자 두 줄) · 맞은 적 표식 + marks
     */
    static boolean mirage(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double free = Kit.reachFlat(level, start, f, k.num("dist", 6));
        List<Kit.Spot> spots = free > 0.4 ? Kit.dashSpots(level, start, f, free) : List.of();
        if (spots.isEmpty()) {
            Notify.notify(sp, "§c" + k.name() + " §7— 앞이 막혔습니다", 30);
            return false;
        }
        double len = spots.get(spots.size() - 1).s();
        int steps = Math.max(1, (int) k.num("steps", 3));
        List<Vec3> marks = new ArrayList<>();
        for (int s = 1; s <= steps; s++) {
            double want = len * s / steps;
            Kit.Spot best = spots.get(0);
            for (Kit.Spot x : spots) if (Math.abs(x.s() - want) < Math.abs(best.s() - want)) best = x;
            marks.add(best.p());
        }
        // 환영 둘: 원래 자리 양옆 (설 자리가 없으면 원래 자리 쪽으로 당김)
        Vec3 r = V.rightOf(f);
        double side = k.num("side", 1.2);
        Vec3[] twins = new Vec3[2];
        for (int i = 0; i < 2; i++) {
            Vec3 want = V.add(start, r, (i == 0 ? -1 : 1) * side);
            Vec3 s = Kit.standSpot(level, new Vec3(want.x, start.y + 0.6, want.z), 2.0);
            twins[i] = s != null && Math.abs(s.y - start.y) < 1.2 && Kit.clearPath(level, V.up(start, 0.8), V.up(s, 0.8)) ? s : start;
        }
        String life = RuneKit.lifeToken(sp);
        int clone = Math.max(4, (int) k.num("life", 40));
        Kit.noFall(sp, steps + 20);
        Monsters.decoy(sp, start, k.num("taunt", 6), clone);
        boolean[] blown = {false};
        // 출발: 망토를 휘둘러 사라짐 · 1인칭 눈앞 연기 · 소리
        JobFx.smoke(level, V.up(start, 1.0), 1.0, j, 0.55, JobFx.hide1p(sp));
        JobFx.ribbon(level, V.up(start, 0.8), 1.0, j, 0.4, 26, JobFx.hide1p(sp).with("alpha", 0.9));
        SkillFx.play(level, "entity.enderman.teleport", start, 1.25, 0.7);
        SkillFx.play(level, "mob.phantom.flap", start, 1.5, 0.5);
        // 환영 그림 (8틱마다 다시 — 청록 테두리 분신 · 발밑 연기 · 머리 위 꽃 하나), 나타날 때 청록 반짝임
        for (Vec3 tw : twins) SkillFx.sparks(level, V.up(tw, 1.0), ILLUSION_CORE, 6, 2.5);
        for (int t = 0; t < clone; t += 8) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (blown[0]) return;
                for (int i = 0; i < 2; i++) {
                    illusion(sp, level, twins[i], f, 0, j, 0.5, 0.9);
                    JobFx.blossom(level, V.up(twins[i], 2.3), 0.15, j, 0.45, Opt.of("alpha", 0.85));
                    if (tt % 16 == 0) JobFx.smoke(level, V.up(twins[i], 0.25), 0.65, j, 0.6, Opt.of("alpha", 0.5));
                }
            });
        }
        // 닿으면 터짐 (2틱마다 확인) · life 틱이 지나면 터짐
        for (int t = 2; t < clone; t += 2) {
            RuneKit.later(t, () -> {
                if (blown[0] || !RuneKit.sameLife(sp, life)) return;
                double touch = k.num("touch", 1.4);
                if (!Kit.around(sp, twins[0], touch, 2.0).isEmpty() || !Kit.around(sp, twins[1], touch, 2.0).isEmpty()) {
                    blown[0] = true;
                    mirageBlast(sp, j, k, P, twins, life);
                }
            });
        }
        RuneKit.later(clone, () -> {
            if (blown[0]) return;
            blown[0] = true;
            if (RuneKit.sameLife(sp, life)) mirageBlast(sp, j, k, P, twins, life);
            else Monsters.endDecoy(sp);
        });
        // 순간 이동: 한 틱에 한 마디 — 지나온 줄기 (3인칭) · 1인칭 빠른 줄 · 도착 연기
        for (int s = 0; s < marks.size(); s++) {
            Vec3 to = marks.get(s);
            int ss = s;
            RuneKit.later(s, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                Vec3 prev = sp.position();
                if (!Kit.moveTo(sp, to)) return;
                JobFx.line(d, V.up(prev, 0.9), V.up(to, 0.9), j, 0.25, JobFx.hide1p(sp));
                JobFx.speed(sp, j, ss == marks.size() - 1 ? 0.25 : 0.1, 0.7);
                if (ss == marks.size() - 1) {
                    JobFx.smoke(d, V.up(to, 0.8), 0.8, j, 0.5, JobFx.hide1p(sp).with("alpha", 0.8));
                    JobFx.petals(d, V.up(to, 1.2), 5, 2.0, 0.12, j, 0.6, Opt.empty());
                }
            });
        }
        return true;
    }

    /** 환영 둘이 터짐: 미끼가 끝남 · 환영마다 거울 조각 · 연기 · 땅의 고리 · 꽃잎 — 적마다 닿은 환영 수만큼 blast (한 번에 · 숫자 여러 줄) · 표식 + marks */
    private static void mirageBlast(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3[] twins, String life) {
        Monsters.endDecoy(sp);
        ServerLevel level = sp.level();
        double r = k.num("radius", 3);
        int marks = Math.max(0, (int) k.num("marks", 2));
        Map<LivingEntity, Integer> hits = new LinkedHashMap<>();
        for (Vec3 tw : twins) {
            Vec3 c = V.up(tw, 1.0);
            shatter(level, c, 1.1, j);
            JobFx.burst(level, c, 1.3, j, 0.35, Opt.empty());
            JobFx.ring(level, V.up(tw, 0.08), r, j, false, 0.4, Opt.empty());
            JobFx.petals(level, c, 8, 4.0, 0.14, j, 0.7, Opt.empty());
            for (LivingEntity e : Kit.around(sp, tw, r, 2.5)) hits.merge(e, 1, Integer::sum);
        }
        SkillFx.play(level, "random.explode", twins[0], 1.5, 0.5);
        SkillFx.play(level, "entity.enderman.teleport", twins[1], 0.6, 0.8);
        double blast = k.num("blast", 1.425);
        for (Map.Entry<LivingEntity, Integer> en : hits.entrySet()) {
            LivingEntity e = en.getKey();
            int c = en.getValue();
            JobKit.strikeSplit(sp, e, j, P, blast * c, c, 2, false, false, life, 0, d -> {
                for (int m = 0; m < marks; m++) JobTraits3.mark(sp, e, j);
                if (RuneKit.fxQuota("jobMirageBlast", 6)) JobFx.hit(e, j, 1.1);
            });
        }
    }

    // ------------------------------------------------------------ 요르문간드의 똬리

    /**
     * 요르문간드의 똬리: 독 단검을 쥐고 보는 쪽 dist 칸을 S 자로 (좌우 sway 칸 — 트인 쪽부터, steps 틱에 한 마디씩) 갔다가 거꾸로 된 S 로 되돌아옴 (두 길이 겹쳐 뱀의
     * 똬리 꼴 — 옆이 막히면 덜 휘고 턱 · 낭떠러지 · 벽 앞에서 멈춤). 가는 길에 지난 마디 가까이 (반 폭 width/2) 의 적을 한 번 벰 (cut · 표식 + marks) · 오는 길에 지난
     * 적도 기억 → coilAt 틱에 뱀이 똬리를 조임 (땅에 뱀의 똬리 rfx_jcoil) — 두 길에 닿은 적마다 coil (표식 + marks) → every 틱마다 ticks 번 독 (venom).
     * 묶였거나 앞이 막혀 한 칸도 못 가면 쓰지 않음
     */
    static boolean coil(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 r = V.rightOf(f);
        double dist = k.num("dist", 7);
        double sway = k.num("sway", 1.5);
        int steps = Math.max(2, (int) k.num("steps", 7));
        double roomR = Kit.reachFlat(level, start, r, sway + 0.6);
        double roomL = Kit.reachFlat(level, start, V.scale(r, -1), sway + 0.6);
        Vec3 side = roomL > roomR + 0.25 ? V.scale(r, -1) : r;
        Double gy0 = RuneKit.solidGround(level, V.up(start, 0.1), 3);
        Vec3 base = gy0 != null && start.y - gy0 > 0.05 ? new Vec3(start.x, gy0, start.z) : start;
        List<Vec3> out = new ArrayList<>();
        Vec3 prev = base;
        for (int s = 1; s <= steps; s++) {
            double u = (double) s / steps;
            Vec3 p = sPoint(sp, start, f, side, dist * u, sway * Math.sin(2 * Math.PI * u), out.isEmpty() ? start : prev, prev);
            if (p == null) break;
            out.add(p);
            prev = p;
        }
        if (out.isEmpty()) {
            Notify.notify(sp, "§c" + k.name() + " §7— 앞이 막혔습니다", 30);
            return false;
        }
        // 되돌아오는 길: 간 마디를 거꾸로 (옆은 반대쪽 — 거꾸로 된 S) → 처음 자리
        int n = out.size();
        List<Vec3> back = new ArrayList<>();
        for (int i = n - 2; i >= 0; i--) {
            double u = (i + 1.0) / steps;
            Vec3 p = sPoint(sp, start, f, side, dist * u, -sway * Math.sin(2 * Math.PI * u), prev, prev);
            if (p == null) break;
            back.add(p);
            prev = p;
        }
        if (back.size() == n - 1 && Kit.clearPath(level, V.up(prev, 0.6), V.up(base, 0.6)) && Math.abs(base.y - prev.y) <= 1.05) back.add(base);
        List<Vec3> path = new ArrayList<>(out);
        path.addAll(back);
        String life = RuneKit.lifeToken(sp);
        double half = k.num("width", 2.6) / 2;
        double cut = k.num("cut", 2.4);
        int marks = Math.max(0, (int) k.num("marks", 1));
        double[] dark = JobKit.core(j);
        double[] glow = JobKit.glow(j);
        int total = path.size();
        int coilAt = Math.max(total + 2, (int) k.num("coilAt", 18));
        Kit.noFall(sp, coilAt + 20);
        // 출발: 단검에 독이 번쩍 · 발밑 그림자 연기 · 쉿 소리
        RuneKit.Hand h0 = RuneKit.handOf(sp);
        venomDrops(level, V.up(V.add(h0.hand(), h0.f(), 0.5), 0.2), 5, 1.6, JobFx.hide1p(sp));
        JobFx.smoke(level, V.up(start, 0.4), 0.9, j, 0.5, JobFx.hide1p(sp));
        JobFx.slash(sp, j, 1.8, 0.35, 1.2, 20.0, 0.18);
        SkillFx.play(level, "entity.cat.hiss", start, 0.55, 0.75);
        SkillFx.play(level, "entity.player.attack.sweep", start, 1.6, 0.6);
        Set<UUID> outDone = new HashSet<>();
        Map<UUID, LivingEntity> coiled = new LinkedHashMap<>();
        boolean[] stopped = {false};
        for (int s = 0; s < total; s++) {
            Vec3 to = path.get(s);
            int ss = s;
            boolean going = s < n;
            RuneKit.later(s + 1, () -> {
                if (stopped[0] || !RuneKit.sameLife(sp, life)) return;
                ServerLevel d = sp.level();
                Vec3 from = sp.position();
                if (!Kit.moveTo(sp, to)) {
                    stopped[0] = true;
                    return;
                }
                Kit.launch(sp, 0, 0, 0);
                // 지나온 마디: 자홍 줄기 (1인칭 숨김) · 땅에 남는 뱀 자국 (가는 길 자홍 · 오는 길 독 초록) · 그림자 잔상 (두 틱마다) · 1인칭 빠른 줄
                JobFx.dash(d, V.up(from, 0.85), V.up(to, 0.85), j, 0.32, JobFx.hide1p(sp));
                RuneKit.streak(d, V.up(from, 0.12), V.up(to, 0.12), going ? glow : VENOM, 0.16, 0.9, 0.85, 0);
                if (ss % 2 == 0) RuneKit.shadeClone(sp, d, from, V.flat(V.sub(to, from)), 1, dark, glow, Opt.of("life", 0.35, "fade", 0.6, "alpha", 0.8));
                JobFx.speed(sp, j, 0.1, 0.75);
                if (ss % 2 == 1) SkillFx.play(d, "entity.player.attack.sweep", to, 1.45 + ss * 0.03, 0.55);
                if (ss == n - 1) {
                    // 끝에서 몸을 뒤집어 되돌아옴: 눈앞 되돌려 긋는 베기 · 연기 · 쉿 소리
                    JobFx.slash(sp, j, 1.8, 0.35, 1.25, 200.0, 0.2);
                    JobFx.smoke(d, V.up(to, 0.5), 0.8, j, 0.5, JobFx.hide1p(sp).with("alpha", 0.8));
                    SkillFx.play(d, "entity.cat.hiss", to, 0.75, 0.6);
                    JobKit.motion(sp, "trickster_coil:back");
                }
                Vec3 seg = V.sub(to, from);
                double len = Math.hypot(seg.x, seg.z);
                if (len < 0.05) return;
                Vec3 dir = V.flat(seg);
                for (Kit.LineHit lh : RuneKit.lineTargets(sp, V.up(from, 0.9), dir, len, half)) {
                    LivingEntity e = lh.e();
                    coiled.putIfAbsent(e.getUUID(), e);
                    if (!going || !outDone.add(e.getUUID())) continue;
                    JobKit.strike(sp, e, j, P, cut, false, false, life, 0, dd -> {
                        if (!(e.level() instanceof ServerLevel lv)) return;
                        for (int m = 0; m < marks; m++) JobTraits3.mark(sp, e, j);
                        if (!RuneKit.fxQuota("jobTrCoilCut", 6)) return;
                        Vec3 b = Kit.bodyPoint(e);
                        JobFx.cross(lv, b, 0.75 + Kit.bodyPad(e) * 0.4, j, 0.26, Opt.of("vel", RuneKit.entVel(e)));
                        JobFx.hit(e, j, 1.1);
                        venomDrops(lv, b, 6, 2.4, Opt.empty());
                        SkillFx.play(lv, "entity.cat.hiss", b, 1.1, 0.45);
                    });
                }
                if (ss == total - 1) {
                    JobFx.slash(sp, j, 1.8, 0.35, 1.25, 20.0, 0.2);
                    JobFx.petals(d, V.up(to, 1.0), 6, 2.2, 0.12, j, 0.6, Opt.empty());
                }
            });
        }
        // 똬리를 조임: 땅에 뱀의 똬리 (가운데 = 간 길의 가운데 · 길을 따라 누움) → 두 길에 닿은 적마다 coil · 표식 → 독
        Vec3 mid = V.add(start, f, Math.min(dist, Math.hypot(out.get(n - 1).x - start.x, out.get(n - 1).z - start.z)) / 2);
        double coilSize = Math.max(1.6, Math.hypot(out.get(n - 1).x - start.x, out.get(n - 1).z - start.z) / 2 + 0.4);
        RuneKit.later(coilAt - 6, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.coil(sp.level(), mid, f, coilSize, 0, 5, 0.32, false, Opt.empty(), j);
        });
        JobKit.motionLater(sp, "trickster_coil:squeeze", coilAt - 2, life);
        RuneKit.later(coilAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            JobFx.coil(d, mid, f, coilSize, 5, 3, 0.4, false, Opt.empty(), j);
            JobFx.smoke(d, V.up(mid, 0.3), coilSize * 0.8, VENOM, VENOM_CORE, 0.6, Opt.of("alpha", 0.6));
            JobKit.shake(sp, 10, 0.25, 0.2);
            SkillFx.play(d, "entity.cat.hiss", mid, 0.5, 0.9);
            SkillFx.play(d, "block.brewing_stand.brew", mid, 0.7, 0.7);
            SkillFx.play(d, "entity.player.attack.knockback", mid, 0.6, 0.8);
            double coil = k.num("coil", 3.15);
            double venom = k.num("venom", 0.75);
            int ticks = Math.max(0, (int) k.num("ticks", 4));
            int every = Math.max(1, (int) k.num("every", 10));
            for (LivingEntity e : coiled.values()) {
                if (e.isRemoved() || !e.isAlive()) continue;
                JobKit.strike(sp, e, j, P, coil, false, false, life, 0, dd -> {
                    if (!(e.level() instanceof ServerLevel lv)) return;
                    for (int m = 0; m < marks; m++) JobTraits3.mark(sp, e, j);
                    if (!RuneKit.fxQuota("jobTrCoil", 6)) return;
                    Vec3 b = Kit.bodyPoint(e);
                    JobFx.ring(lv, V.up(e.position(), 0.1), 0.8 + Kit.bodyPad(e), j, true, 0.3, Opt.of("vel", RuneKit.entVel(e)));
                    JobFx.cross(lv, b, 0.85 + Kit.bodyPad(e) * 0.4, j, 0.28, Opt.of("vel", RuneKit.entVel(e), "spin", 90.0));
                    JobFx.smoke(lv, b, 0.8 + Kit.bodyPad(e) * 0.4, VENOM, VENOM_CORE, 0.55, Opt.of("alpha", 0.85, "vel", RuneKit.entVel(e)));
                    JobFx.hit(e, j, 1.2);
                });
                for (int t = 1; t <= ticks; t++) {
                    boolean lastTick = t == ticks;
                    JobKit.strike(sp, e, j, P, venom, false, false, life, t * every, dd -> {
                        if (!(e.level() instanceof ServerLevel lv) || !RuneKit.fxQuota("jobTrVenom", 8)) return;
                        Vec3 b = Kit.bodyPoint(e);
                        JobFx.smoke(lv, b, 0.7 + Kit.bodyPad(e) * 0.35 + (lastTick ? 0.2 : 0), VENOM, VENOM_CORE, 0.5, Opt.of("alpha", 0.8, "vel", RuneKit.entVel(e)));
                        SkillFx.sparks(lv, b, VENOM_CORE, lastTick ? 6 : 4, 1.8);
                        venomDrops(lv, V.up(b, 0.2), lastTick ? 6 : 4, 1.6, Opt.empty());
                        SkillFx.play(lv, "block.brewing_stand.brew", b, 1.6, 0.35);
                    });
                }
            }
        });
        return true;
    }

    /**
     * S 자 길의 한 마디: 처음 자리에서 앞 x 칸 · 옆 lat 칸 (옆이 막히면 반 · 0 으로 덜 휨) 에 설 자리 — 앞 마디 (prev) 에서 한 칸 안의 오르내림 · 발목 · 머리 높이가
     * 막히지 않는 데까지 (몸 상자도 들어가야 함). 없으면 null
     */
    private static Vec3 sPoint(ServerPlayer sp, Vec3 start, Vec3 f, Vec3 side, double x, double lat, Vec3 from, Vec3 prev) {
        ServerLevel level = sp.level();
        for (double bend : new double[] {1, 0.5, 0}) {
            Vec3 q = V.add(V.add(start, f, x), side, lat * bend);
            Vec3 p = Kit.standSpot(level, new Vec3(q.x, prev.y + 1.1, q.z), 2.2);
            if (p != null && p.y <= prev.y + 1.05 && p.y >= prev.y - 1.1 && Kit.clearPath(level, V.up(from, 0.6), V.up(p, 0.6))
                    && Kit.clearPath(level, V.up(from, 1.5), V.up(p, 1.5)) && level.noCollision(sp, sp.getBoundingBox().move(p.x - sp.getX(), p.y - sp.getY(), p.z
                            - sp.getZ()))) return p;
        }
        return null;
    }

    /** 독 방울 n 개 (rpg:drop — 초록). view = 보는 사람 걸러내기 */
    private static void venomDrops(ServerLevel level, Vec3 at, int n, double speed, Opt view) {
        for (int i = 0; i < n; i++) {
            double a = Kit.rand() * Math.PI * 2;
            SkillFx.fx(level, "rpg:drop", at, view.with("dir", V.unit(new Vec3(Math.cos(a), 0.6 + Kit.rand() * 0.8, Math.sin(a))), "speed", speed * (0.6 + Kit.rand()
                    * 0.6), "size", 0.18 + Kit.rand() * 0.1, "life", 0.5 + Kit.rand() * 0.2, "color", Kit.rand() < 0.5 ? VENOM : VENOM_CORE, "alpha", 0.95));
        }
    }

    // ------------------------------------------------------------ 끝없는 밤

    /**
     * 끝없는 밤: 망토를 휘둘러 사라짐 — dur 틱 은신 (1차 밤의 장막과 같음 · JobTraits3) · 태세 window 틱 동안 적을 쓰러뜨리면 다시 은신 (reveil 틱 · again 번까지 —
     * JobTraits3.reveil). 그림: 몸 둘레 검보라 망토 소용돌이 · 연기 · 흩어지는 환영 · 꽃잎 · 청록 반짝임 · 1인칭 눈앞 연기와 고리
     */
    static boolean night(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        JobTraits3.veilStart(sp, j, k);
        cloak(sp, j, 1.0);
        return true;
    }

    // ------------------------------------------------------------ 개기일식

    /**
     * 개기일식: 하늘이 검어지고 (rpg:ult_eclipse · 앞 위 하늘의 검은 해) first 틱부터 every 틱마다 rounds 번 — 적 (표식 찍힌 적 · 없으면 가까운 targets 명 — 보이는 적)
     * 곁으로 순간 이동해 비수를 던짐 · 그 적 둘레 세 자리에 내 환영 (청록 테두리) 이 나타나 같은 동작 (던지는 넷 — 환영마다 다른 적을 차례로, 적이 하나면 넷이 둘러쌈) —
     * 비수는 flight 틱 뒤 닿음 (적마다 맞은 비수 수 × dagger 를 한 번에 · 숫자 여러 줄 · 비수마다 표식 +1) → 마지막 던짐 뒤 처음 자리로 → finalAt 틱에 해가 다시 뜸
     * (rfx_jdawn · 흰 금빛 번쩍임 · 새벽빛 하늘) — 모든 적의 표식을 터뜨림 (base + per × 표식 — 큰 숫자). 둘레에 적이 없으면 쓰지 않음
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level0 = sp.level();
        double radius = k.num("radius", 12);
        int maxN = Math.max(1, (int) k.num("targets", 6));
        List<LivingEntity> around = Kit.visibleFrom(sp, sp.getEyePosition(), JobKit.near(sp, radius, 24));
        List<LivingEntity> marked = new ArrayList<>();
        for (LivingEntity e : around) if (JobTraits3.marks(sp, e) > 0) marked.add(e);
        List<LivingEntity> pool = marked.isEmpty() ? around : marked;
        List<LivingEntity> targets = new ArrayList<>(pool.subList(0, Math.min(maxN, pool.size())));
        if (targets.isEmpty()) {
            Notify.notify(sp, "§c" + k.name() + " §7— 둘레에 적이 없습니다", 30);
            return false;
        }
        int rounds = Math.max(1, (int) k.num("rounds", 4));
        int first = Math.max(2, (int) k.num("first", 6));
        int every = Math.max(10, (int) k.num("every", 10));
        int throwers = Math.max(1, (int) k.num("throwers", 4));
        int flight = Math.max(1, (int) k.num("flight", 2));
        int lastThrow = first + (rounds - 1) * every;
        int finalAt = Math.max(lastThrow + flight + 10, (int) k.num("finalAt", 52));
        double dagger = k.num("dagger", 1.25);
        String life = RuneKit.lifeToken(sp);
        Vec3 home = sp.position();
        Vec3 f0 = Kit.facingOf(sp);
        Ult.sky(level0, home, "rpg:ult_eclipse", finalAt + 2);
        Kit.noFall(sp, finalAt + 30);
        RpgCombat.setSteadfast(sp, finalAt + 10);
        // 앞 위 하늘의 검은 해 (커지며 나타남 → 코로나 맥동) — 해가 다시 뜰 때까지
        Vec3 sunAt = V.up(V.add(home, f0, 8), 7.5);
        JobFx.eclipse(level0, sunAt, 3.4, j, finalAt / 20.0 + 0.05, Opt.of("alpha", 0.95));
        // 시작: 망토 소용돌이 · 꽃잎 · 어두운 번쩍임 · 1인칭 눈앞 고리
        JobFx.ribbon(level0, V.up(home, 0.9), 1.2, j, 0.5, 26, JobFx.hide1p(sp).with("alpha", 0.95));
        JobFx.smoke(level0, V.up(home, 1.0), 1.6, j, 0.8, JobFx.hide1p(sp));
        JobFx.petals(level0, V.up(home, 1.2), 14, 3.0, 0.14, j, 0.9, Opt.empty());
        JobFx.eyeRing(sp, j, 2.4, 1.1, 0.5);
        Ult.flash(level0, home, JobKit.core(j), Opt.of("radius", 6.0, "time", new double[] {0.03, 0.08, 0.4}), sp);
        SkillFx.play(level0, "entity.enderman.teleport", home, 0.5, 1.0);
        SkillFx.play(level0, "mob.wither.shoot", home, 1.5, 0.4);
        int n = targets.size();
        for (int r = 0; r < rounds; r++) {
            int rr = r;
            RuneKit.later(first + r * every, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                throwRound(sp, j, P, targets, rr, throwers, flight, dagger, life);
                if (rr > 0) JobKit.motion(sp, "trickster_ult:throw" + (rr % 2));
            });
        }
        // 처음 자리로 돌아옴 (적들을 바라봄)
        RuneKit.later(lastThrow + 6, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 sum = Vec3.ZERO;
            int m = 0;
            for (LivingEntity e : targets) {
                if (e.isRemoved()) continue;
                sum = V.add(sum, Kit.bodyPoint(e));
                m++;
            }
            Vec3 prev = sp.position();
            Vec3 focus = m > 0 ? V.scale(sum, 1.0 / m) : V.add(home, f0, 4);
            float[] rot = Kit.lookRotation(V.up(home, sp.getEyeHeight()), focus);
            if (!Kit.moveTo(sp, home, rot[0], Mth.clamp(rot[1], -20F, 40F))) Kit.moveTo(sp, home);
            ServerLevel d = sp.level();
            RuneKit.shadeClone(sp, d, prev, Kit.facingOf(sp), 1, JobKit.core(j), JobKit.glow(j), Opt.of("life", 0.45, "fade", 0.6, "alpha", 0.8));
            JobFx.smoke(d, V.up(home, 0.9), 0.9, j, 0.5, JobFx.hide1p(sp));
            SkillFx.play(d, "entity.enderman.teleport", home, 1.2, 0.6);
            JobKit.motion(sp, "trickster_ult:home");
        });
        JobKit.motionLater(sp, "trickster_ult:final", finalAt - 3, life);
        // 해가 다시 뜸: 모든 적의 표식을 터뜨림
        RuneKit.later(finalAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel d = sp.level();
            Ult.sky(d, home, "rpg:ult_sunrise", 26);
            JobFx.dawn(d, sunAt, 4.2, 0.9, Opt.of("alpha", 1.0), DAWN, DAWN_CORE);
            Ult.flash(d, sp.position(), DAWN, Opt.empty(), sp);
            RuneKit.shakeAround(d, sp.position(), 16, 0.35, 0.35);
            SkillFx.play(d, "entity.player.attack.crit", sp.position(), 0.6, 1.0);
            SkillFx.play(d, "random.explode", sp.position(), 1.3, 0.6);
            SkillFx.play(d, "block.beacon.activate", sp.position(), 1.6, 0.8);
            SkillFx.play(d, "block.cherry_leaves.break", sp.position(), 0.8, 1.0);
            Vec3 look = sp.getViewVector(1F);
            JobFx.burst(d, V.add(V.add(sp.getEyePosition(), look, 2.8), JobFx.screenUp(look), -0.3), 1.4, j, 0.4, JobFx.only1p(sp));
            double base = k.num("base", 4);
            double per = k.num("per", 2);
            int max = j.trait() == null ? 7 : Math.max(1, j.trait().max());
            for (LivingEntity e : targets) {
                if (e.isRemoved() || !e.isAlive()) continue;
                int m = JobTraits3.takeMarks(sp, e);
                Vec3 at = Kit.bodyPoint(e);
                double s = 1.2 + Kit.bodyPad(e) * 0.6;
                flowersBurst(d, sp, e, m, j);
                JobFx.blossom(d, at, 0.8 + 0.08 * m, j, 0.5, Opt.of("grow", 0.7, "fade", 0.5));
                JobFx.rose(d, at, 0.6 + Kit.bodyPad(e) * 0.4 + 0.06 * m, j, 0.6, Opt.empty());
                JobFx.cross(d, at, s, j, 0.35, Opt.empty());
                RuneKit.later(2, () -> JobFx.cross(d, at, s * 0.8, j, 0.3, Opt.of("spin", 45.0)));
                JobFx.burst(d, at, s, j, 0.4, Opt.empty());
                JobFx.petals(d, at, 8 + m, 4.5, 0.15, j, 0.8, Opt.empty());
                JobKit.strike(sp, e, j, P, base + per * m, false, m >= max, life, 0, null);
            }
        });
        return true;
    }

    /**
     * 개기일식 한 번: 던지는 이 넷 (0 = 나 · 1 ~ 3 = 환영) 이 각자 적 targets[(round + i) % n] 둘레 (나와 90° 씩 돌아간 자리 — 3칸 + 몸) 에서 비수를 던짐 —
     * 나는 그 자리로 순간 이동 (적을 봄), 환영은 그 자리에 나타남 (던지는 자세 · 청록 테두리). 적마다 맞은 비수 수 × dagger 를 flight 틱 뒤 한 번에 (숫자 여러 줄) ·
     * 표식 + 비수 수
     */
    private static void throwRound(ServerPlayer sp, Jobs.Job j, double P, List<LivingEntity> targets, int round, int throwers, int flight, double dagger,
            String life) {
        ServerLevel level = sp.level();
        List<LivingEntity> alive = new ArrayList<>();
        for (LivingEntity e : targets) if (!e.isRemoved() && e.isAlive()) alive.add(e);
        if (alive.isEmpty()) return;
        int n = alive.size();
        Map<LivingEntity, Integer> count = new LinkedHashMap<>();
        Vec3 now = sp.position();
        LivingEntity main = alive.get(round % n);
        Vec3 away0 = Math.hypot(now.x - main.getX(), now.z - main.getZ()) < 0.2 ? V.scale(Kit.facingOf(sp), -1) : V.flat(V.sub(now, main.position()));
        double turn0 = round % 2 == 0 ? 50 : -50;
        for (int i = 0; i < throwers; i++) {
            LivingEntity t = alive.get((round + i) % n);
            Vec3 away = t == main ? away0 : (Math.hypot(now.x - t.getX(), now.z - t.getZ()) < 0.2 ? away0 : V.flat(V.sub(now, t.position())));
            Vec3 spot = ringSpot(level, t, V.turnY(away, turn0 + i * 90), 3.0 + Kit.bodyPad(t));
            Vec3 focus = V.up(t.position(), t.getBbHeight() * 0.55);
            Vec3 from;
            if (i == 0) {
                // 나: 그 자리로 순간 이동 (떠난 자리 잔상 · 지나간 줄) · 적을 봄 · 손에서 비수
                Vec3 prev = sp.position();
                boolean moved = false;
                if (spot != null) {
                    float[] rot = Kit.lookRotation(V.up(spot, sp.getEyeHeight()), focus);
                    moved = Kit.moveTo(sp, spot, rot[0], Mth.clamp(rot[1], -25F, 45F));
                }
                RuneKit.shadeClone(sp, level, prev, V.flat(V.sub(t.position(), prev)), 1 + round % 3, JobKit.core(j), JobKit.glow(j), Opt.of("life", 0.4, "fade",
                        0.6, "alpha", 0.85));
                if (moved) JobFx.line(level, V.up(prev, 1.0), V.up(sp.position(), 1.0), j, 0.22, JobFx.hide1p(sp));
                RuneKit.Hand h = RuneKit.handOf(sp);
                from = V.add(h.hand(), h.f(), 0.3);
                JobFx.slash(sp, j, 1.9, 0.25, 1.2, round % 2 == 0 ? 30.0 : 210.0, 0.18);
                JobFx.speed(sp, j, 0.12, 0.7);
                SkillFx.play(level, "entity.enderman.teleport", sp.position(), 1.8, 0.35);
            } else {
                if (spot == null) continue;
                Vec3 face = V.flat(V.sub(t.position(), spot));
                illusion(sp, level, spot, face, 3, j, 0.45, 0.9);
                SkillFx.sparks(level, V.up(spot, 1.0), ILLUSION_CORE, 4, 2);
                Vec3 shatterAt = V.up(spot, 1.0);
                RuneKit.later(7, () -> shatter(level, shatterAt, 0.5, j));
                from = V.add(V.up(spot, 1.3), face, 0.4);
            }
            JobFx.dagger(level, from, Kit.bodyPoint(t), flight, 0.42, j, Opt.empty());
            count.merge(t, 1, Integer::sum);
        }
        SkillFx.play(level, "entity.player.attack.sweep", main.position(), 1.6, 0.7);
        SkillFx.play(level, "item.trident.throw", main.position(), 1.5, 0.6);
        for (Map.Entry<LivingEntity, Integer> en : count.entrySet()) {
            LivingEntity e = en.getKey();
            int c = en.getValue();
            JobKit.strikeSplit(sp, e, j, P, dagger * c, c, 1, false, false, life, flight, d -> {
                for (int m = 0; m < c; m++) JobTraits3.mark(sp, e, j);
                if (!(e.level() instanceof ServerLevel lv) || !RuneKit.fxQuota("jobTrUlt", 8)) return;
                Vec3 at = Kit.bodyPoint(e);
                JobFx.cross(lv, at, 0.8 + Kit.bodyPad(e) * 0.5, j, 0.26, Opt.of("vel", RuneKit.entVel(e)));
                JobFx.petals(lv, at, 3 + c, 3.0, 0.13, j, 0.6, Opt.empty());
                JobFx.hit(e, j, 1.0);
            });
        }
    }

    /** 적 둘레 dir 쪽 dist 칸에 설 자리 (막혔으면 ±40° · 가까이 — 없으면 null) */
    private static Vec3 ringSpot(ServerLevel level, LivingEntity e, Vec3 dir, double dist) {
        for (double turn : new double[] {0, 40, -40}) {
            for (double k : new double[] {1.0, 0.7}) {
                Vec3 d = V.turnY(dir, turn);
                Vec3 want = V.add(e.position(), d, dist * k);
                Vec3 s = Kit.standSpot(level, new Vec3(want.x, Math.max(want.y, e.getY()) + 0.5, want.z), 2.5);
                if (s != null && Math.abs(s.y - e.getY()) < 2.5) return s;
            }
        }
        return null;
    }
}
