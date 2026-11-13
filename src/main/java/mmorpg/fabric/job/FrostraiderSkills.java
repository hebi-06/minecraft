package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.FxDisplays;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.rune.IceFx;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneProps;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Guards;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.Status;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 2026-11-07 재설계안 3판 — 전사 반전 프로스트 레이더 (글래디에이터 대신 — 사용자 "컨셉이 마음에 안듬" → 고른 것 "서리 도끼를 던져 박고 되불러 얼린 적을 깨부숨 —
 * 중거리 · 단일 · 얼려서 깨뜨림 (분노 ↔ 냉기)"): 1차 전사 (광역 · 묵직한 단타 · 맞으며 버팀 · 분노) 를 뒤집음. 프로스트 바이트 (던져 박음 → 다시 눌러 되부름) ·
 * 아이스 바운드 (박힌 도끼에게로 날아가 내리찍음 · 서리 갑옷) · 샤터 (얼음 결정에 가둠 → 결정째 깨부숨) · 요툰 그립 (등 뒤 서리 거인의 팔 — JobTraits16) ·
 * 핌불윈터 (하늘로 던진 도끼가 빙하 도끼로 떨어짐 → 되불러 빙하째 깨뜨림). 특성 「빙결」 은 JobTraits16. 몸 쓰는 직업 기준 (제 몸 · 제 도끼 — 던지고 되부름).
 */
public final class FrostraiderSkills {
    private FrostraiderSkills() {}

    private static final double[] ICE = JobTraits16.ICE;
    private static final double[] WHITE = JobTraits16.FROST_WHITE;

    static void register() {
        JobSystem.register("frostraider_throw", FrostraiderSkills::throwAxe);
        JobSystem.register("frostraider_leap", FrostraiderSkills::leap);
        JobSystem.register("frostraider_shatter", FrostraiderSkills::shatter);
        JobSystem.register("frostraider_jotunn", FrostraiderSkills::jotunn);
        JobSystem.register("frostraider_ult", FrostraiderSkills::ult);
    }

    /** 박힌 서리 도끼: 박힌 적 (없으면 null — 땅) · 박힌 자리 · 목숨 · 박힌 3D 도끼 (효과 · 소품 — 2026-11-13) · 되부르는 중 */
    private static final class Thrown {
        LivingEntity target;
        Vec3 stuck;
        String life;
        Vfx.Fx fx;
        Vfx.Prop prop;
        boolean back;
    }

    private static final Map<UUID, Thrown> THROWN = new HashMap<>();

    /** 박힌 도끼가 있는 적 (아이스 바운드 · 시험) — 없으면 null */
    public static LivingEntity embedded(ServerPlayer sp) {
        Thrown th = THROWN.get(sp.getUUID());
        if (th == null || th.back || th.target == null || !th.target.isAlive() || th.target.isRemoved() || !RuneKit.sameLife(sp, th.life)) return null;
        return th.target;
    }

    /**
     * 2026-11-13 도는 서리 도끼 (3D 빙하 도끼 — 직업 도감 메모 "얼음과 도끼 연출을 3d로", 환영 도끼 판 대신): from → to 를 ticks 틱 · 도끼머리 둘레로 틱마다
     * 95° 앞으로 구름 (곡선은 거리의 6% — 1칸까지) · 닿은 다음 틱에 지움
     */
    private static void axe(ServerLevel level, Vec3 from, Vec3 to, int ticks, double scale) {
        int n = Math.max(1, ticks);
        Vfx.Fx fx = RuneProps.fx(level, from, n + 3);
        Vfx.Prop p = JobAxe3d.thrown(fx, true, from, to, n, Math.min(1.0, V.len(V.sub(to, from)) * 0.06), -95, scale, ICE, WHITE);
        fx.on(fx.age() + n + 1, p::remove);
    }

    /** 날아가는 길의 서리: 얼음빛 줄기 · 눈발 */
    private static void frostTrail(ServerLevel level, Vec3 from, Vec3 to, Jobs.Job j, Opt view) {
        JobFx.dash(level, from, to, j, 0.3, view.with("alpha", 0.7));
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        if (len < 0.3) return;
        for (double s = 0.8; s < len; s += 1.6) SkillFx.fx(level, "rpg:snowfall", V.add(from, V.unit(d), s), Opt.of("size", 0.45, "life", 0.5, "color", WHITE, "alpha",
                0.7));
    }

    // ------------------------------------------------------------ 프로스트 바이트

    /** 던지는 3D 서리 도끼의 크기 (길이 약 2.4칸 — 예전 환영 도끼 판 1.7 배와 비슷) */
    private static final double AXE = 0.85;

    /**
     * 프로스트 바이트 (롤 드레이븐 Q · 올라프 Q — 도끼 되부르기): throwAt 틱에 서리 도끼를 겨눈 적 (range 칸 — 없으면 보는 쪽 range 칸) 에게 회전시켜 던짐 → hitAt
     * 틱에 박힘 (throw · 서리 +frost — 도끼가 그 적에 박힌 채 남음) → gap 틱 뒤부터 window 틱 안에 다시 누르면 (JobRecast) 박힌 도끼가 얼음을 터뜨리며 비틀리다 pull
     * 틱에 뽑혀 그 적을 다시 벰 (recall) → back 틱에 손으로 돌아옴 (돌아오는 길의 적 서리 +1). 다시 누르지 않으면 창이 닫힐 때 저절로 되부름
     */
    static boolean throwAxe(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (JobRecast.stage(sp, k.id()) >= 1) {
            JobRecast.close(sp, k.id());
            JobRecast.motion(sp, k.id() + ":recall");
            recall(sp, j, k, P);
            return true;
        }
        int throwAt = Math.max(1, (int) k.num("throwAt", 2));
        int hitAt = Math.max(throwAt + 1, (int) k.num("hitAt", 6));
        double range = k.num("range", 10);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        LivingEntity t = Kit.pickTarget(sp, range, 20);
        Vec3 f0 = Kit.facingOf(sp);
        dropThrown(sp); // 앞에 박힌 도끼 (창이 닫히기 전에 직업을 바꾸는 등) 는 지움
        Thrown th = new Thrown();
        th.life = life;
        th.target = t;
        THROWN.put(sp.getUUID(), th);
        JobRecast.open(sp, k, 1, (int) k.num("window", 60), (int) k.num("gap", 10), () -> {
            if (THROWN.get(sp.getUUID()) == th && !th.back) recall(sp, j, k, P);
        });
        JobFx.charge(sp, j, new Vec3(0.55, 1.7, -0.45), throwAt, 0.6);
        SkillFx.play(level0, "item.trident.return", sp.position(), 1.1, 0.5);
        RuneKit.later(throwAt, () -> {
            if (!RuneKit.sameLife(sp, life) || THROWN.get(sp.getUUID()) != th) return;
            ServerLevel lv = sp.level();
            Vec3 from = V.up(RuneKit.handOf(sp).hand(), 0.3);
            Vec3 to = t != null ? Kit.bodyPoint(t) : V.up(V.add(sp.position(), f0, Math.max(2, RuneKit.rayLength(lv, V.up(sp.position(), 1.2), f0, range))), 0.6);
            int fly = hitAt - throwAt;
            axe(lv, from, to, fly, AXE);
            frostTrail(lv, from, to, j, JobFx.hide1p(sp));
            SkillFx.play(lv, "item.trident.throw", from, 0.8, 1.0);
            SkillFx.play(lv, "entity.player.attack.sweep", from, 1.1, 0.6);
            th.stuck = to;
        });
        RuneKit.later(hitAt, () -> {
            if (!RuneKit.sameLife(sp, life) || THROWN.get(sp.getUUID()) != th || th.back) return;
            ServerLevel lv = sp.level();
            int left = (int) Math.max(1, JobRecast.left(sp, k.id()));
            if (t != null && t.isAlive() && !t.isRemoved()) {
                th.stuck = Kit.bodyPoint(t);
                // 2026-11-13 박힌 3D 도끼: 날이 몸에 박히고 (조금 위로) 자루는 던진 쪽 위로 비스듬히 나옴 — 틱마다 그 적을 따라감
                Vec3 u = V.flat(V.sub(th.stuck, sp.position()));
                Vec3 edge = V.unit(V.add(u, V.UP, 0.55));
                Quaternionf q = JobAxe3d.pose(V.unit(V.add(V.UP, u, -0.55)), edge);
                double bite = JobAxe3d.BITE * AXE;
                java.util.function.Supplier<Vec3> head = () -> V.add(V.add(Kit.bodyPoint(t), edge, -bite), u, Kit.bodyPad(t) * 0.3);
                th.fx = RuneProps.fx(lv, th.stuck, left + 8);
                th.prop = JobAxe3d.axe(th.fx, true, head.get(), JobAxe3d.HEAD, q, AXE, ICE, WHITE);
                Vfx.Prop pr = th.prop;
                th.fx.each(th.fx.age() + 1, th.fx.age() + left + 6, 1, age -> {
                    if (!th.back && t.isAlive() && !t.isRemoved()) pr.move(head.get(), 1);
                });
                JobKit.strike(sp, t, j, P, k.num("throw", 2.5), false, false, life, 0, d -> {
                    JobImpact.hit(sp, t, j, 1.4);
                    JobTraits16.addFrost(sp, j, t, Math.max(0, (int) k.num("frost", 2) - 1)); // 맞힌 +1 (JobKit) 에 더해
                    JobTraits16.shardsFx(sp.level(), Kit.bodyPoint(t), 0.6);
                });
                SkillFx.play(lv, "item.trident.hit", t.position(), 0.7, 1.0);
                SkillFx.play(lv, "block.glass.place", t.position(), 0.8, 0.6);
            } else {
                th.target = null;
                Vec3 g = new Vec3(th.stuck.x, RuneKit.groundAt(lv, V.up(th.stuck, 0.5), th.stuck.y - 1), th.stuck.z);
                th.stuck = V.up(g, 0.4);
                // 2026-11-13 땅에 박힌 3D 도끼: 날이 앞 아래로 박히고 자루는 앞으로 40° 들림
                Vec3 u = V.flat(f0);
                Quaternionf q = JobAxe3d.pose(V.add(V.scale(u, 0.766), V.UP, 0.643), V.add(V.scale(u, 0.643), V.UP, -0.766));
                th.fx = RuneProps.fx(lv, th.stuck, left + 8);
                th.prop = JobAxe3d.axe(th.fx, true, V.up(g, 0.15), JobAxe3d.HEAD, q, AXE, ICE, WHITE);
                JobAxe3d.spikeRing(lv, g, 3, 0.5, 0.9, 0.6, 0.9, 8);
                JobFx15.halo(lv, V.up(g, 0.08), 1.2, 0.4, Opt.of("alpha", 0.8), ICE, WHITE);
                JobTraits16.shardsFx(lv, V.up(g, 0.3), 0.5);
                SkillFx.play(lv, "block.glass.place", g, 0.8, 0.6);
            }
            stuckLoop(sp, th);
        });
        JobSystem.note(sp, "§b다시 누르면 도끼를 되부름 §7(" + Math.round(k.num("window", 60) / 20.0 * 10) / 10.0 + "초)");
        return true;
    }

    /** 박혀 있는 동안: 6틱마다 박힌 자리에서 서리 김 · 흰 불티 */
    private static void stuckLoop(ServerPlayer sp, Thrown th) {
        if (!RuneKit.sameLife(sp, th.life) || th.back || THROWN.get(sp.getUUID()) != th) return;
        if (th.target != null && th.target.isAlive() && !th.target.isRemoved()) th.stuck = Kit.bodyPoint(th.target);
        SkillFx.sparks(sp.level(), th.stuck, WHITE, 2, 1.2);
        SkillFx.fx(sp.level(), "rpg:snowfall", V.up(th.stuck, 0.3), Opt.of("size", 0.4, "life", 0.5, "color", WHITE, "alpha", 0.6));
        RuneKit.later(6, () -> stuckLoop(sp, th));
    }

    /**
     * 되부름: 박힌 도끼가 얼음을 터뜨리며 비틀림 (pull 틱 — 얼음 고리 · 금 · 빛) → 뽑혀 그 적을 다시 벰 (recall · 얼음 조각) → back 틱에 손으로 (돌아오는 길의 적 서리 +1)
     */
    private static void recall(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        Thrown th = THROWN.get(sp.getUUID());
        if (th == null || th.back || th.stuck == null || !RuneKit.sameLife(sp, th.life)) return;
        th.back = true;
        String life = th.life;
        int pull = Math.max(1, (int) k.num("pull", 5));
        int back = Math.max(2, (int) k.num("back", 4));
        LivingEntity t = th.target;
        ServerLevel lv0 = sp.level();
        Vec3 p0 = t != null && t.isAlive() && !t.isRemoved() ? Kit.bodyPoint(t) : th.stuck;
        // 비틀림: 박힌 자리에 얼음이 금 가며 퍼짐 · 손 쪽으로 끌리는 빛
        JobFx.glint(lv0, p0, 0.8, j, pull / 20.0, Opt.empty());
        JobFx15.halo(lv0, V.up(t != null ? t.position() : p0, 0.08), 1.0 + (t != null ? Kit.bodyPad(t) : 0), pull / 20.0 + 0.1, Opt.of("alpha", 0.8), ICE, WHITE);
        SkillFx.play(lv0, "item.trident.return", p0, 0.7, 1.0);
        SkillFx.play(lv0, "block.glass.place", p0, 0.6, 0.8);
        RuneKit.later(pull, () -> {
            if (!RuneKit.sameLife(sp, life)) {
                THROWN.remove(sp.getUUID(), th);
                return;
            }
            ServerLevel lv = sp.level();
            // 2026-11-13 박힌 3D 도끼의 도끼머리 자리에서 뽑힘
            Vec3 from = th.prop != null ? th.prop.pos() : t != null && t.isAlive() && !t.isRemoved() ? Kit.bodyPoint(t) : th.stuck;
            Vec3 to = V.up(RuneKit.handOf(sp).hand(), 0.3);
            if (th.fx != null) th.fx.end();
            axe(lv, from, to, back, AXE);
            JobAxe3d.iceShards(lv, from, 6, 1.6, 0.45);
            frostTrail(lv, from, to, j, JobFx.hide1p(sp));
            SkillFx.play(lv, "block.glass.break", from, 1.2, 0.7);
            SkillFx.play(lv, "entity.player.attack.sweep", from, 1.3, 0.6);
            JobTraits16.shardsFx(lv, from, 0.9);
            if (t != null && t.isAlive() && !t.isRemoved()) {
                JobKit.strike(sp, t, j, P, k.num("recall", 4.0), false, true, life, 0, d -> JobImpact.hit(sp, t, j, 1.6));
            }
            Vec3 d = V.sub(to, from);
            double len = V.len(d);
            if (len > 0.5) {
                for (Kit.LineHit h : RuneKit.lineTargets(sp, from, V.scale(d, 1 / len), len, 1.0)) {
                    if (h.e() != t) JobTraits16.addFrost(sp, j, h.e(), 1);
                }
            }
            RuneKit.later(back, () -> {
                THROWN.remove(sp.getUUID(), th);
                if (!RuneKit.sameLife(sp, life)) return;
                JobFx.glint(sp.level(), V.up(RuneKit.handOf(sp).hand(), 0.2), 0.6, j, 0.2, Opt.empty());
                SkillFx.play(sp.level(), "item.armor.equip_iron", sp.position(), 1.2, 0.8);
            });
        });
    }

    /** 남은 박힌 도끼를 지움 (새로 던짐) */
    private static void dropThrown(ServerPlayer sp) {
        Thrown th = THROWN.remove(sp.getUUID());
        if (th != null && th.fx != null) th.fx.end();
    }

    // ------------------------------------------------------------ 아이스 바운드

    /**
     * 아이스 바운드 (롤 판테온 W · 세주아니 Q): 박힌 서리 도끼 (range 칸) 가 있으면 그 적 앞으로, 없으면 겨눈 적 (fallback 칸 — 없으면 보는 쪽 fallback 칸) 으로 서리
     * 바람을 타고 leap 틱에 날아가
     * (포물선 peak 칸 — 머리 위 여유에 맞춤) 내리찍음 → 둘레 radius 칸 slam · 얼음 가시가 솟음 → 서리 갑옷 shieldFor 틱 (보호막 최대 체력 shield)
     */
    static boolean leap(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        if (Status.isRooted(sp) || Kit.ridingAny(sp)) {
            Notify.notify(sp, "§c" + k.name() + " §7— 지금은 움직일 수 없습니다", 30);
            return false;
        }
        int leap = Math.max(4, (int) k.num("leap", 10));
        double R = k.num("radius", 2.5);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        LivingEntity t = embedded(sp);
        if (t != null && Math.hypot(t.getX() - c0.x, t.getZ() - c0.z) > k.num("range", 14) + Kit.bodyPad(t)) t = null;
        if (t == null) t = Kit.pickTarget(sp, k.num("fallback", 6), 25);
        Vec3 dest;
        if (t != null) {
            // (V.flat 은 단위 벡터 — 거리는 뺀 그대로에서: 2026-11-07 CI 42326836876 — 0.9칸만 날아 4칸 과녁에 닿지 않았음)
            Vec3 d = V.sub(c0, t.position());
            Vec3 u = Math.hypot(d.x, d.z) > 0.05 ? V.flat(d) : V.scale(Kit.facingOf(sp), -1);
            Vec3 want = V.add(t.position(), u, 1.1 + Kit.bodyPad(t));
            Vec3 dir = new Vec3(want.x - c0.x, 0, want.z - c0.z);
            double dl = Math.hypot(dir.x, dir.z);
            double free = dl < 0.3 ? 0 : Kit.reachFlat(level0, c0, V.unit(dir), dl);
            Vec3 end = free > 0.3 ? V.add(c0, V.unit(dir), Math.max(0, free - 0.1)) : c0;
            dest = new Vec3(end.x, RuneKit.groundAt(level0, V.up(end, 1.0), c0.y), end.z);
        } else {
            dest = JobMove.landAhead(sp, k.num("fallback", 6), c0.y).apply(c0);
        }
        double peak = JobMove.hopPeak(sp, c0, dest, k.num("peak", 2.6));
        JobMove.run(sp, JobMove.hop(0, leap, JobMove.at(dest), peak));
        RpgCombat.setSteadfast(sp, leap + 4);
        SkillFx.play(level0, "entity.player.attack.sweep", c0, 0.7, 0.8);
        SkillFx.play(level0, "item.elytra.flying", c0, 1.6, 0.4);
        // 서리 바람: 날아가는 동안 몸을 따라 눈발 · 얼음빛 줄기
        for (int i = 1; i < leap; i += 2) {
            int ii = i;
            RuneKit.later(i, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 p0 = RuneKit.leapPoint(c0, dest, peak, (ii - 1) / (double) leap);
                Vec3 p1 = RuneKit.leapPoint(c0, dest, peak, (ii + 1) / (double) leap);
                frostTrail(sp.level(), V.up(p0, 1.0), V.up(p1, 1.0), j, JobFx.hide1p(sp));
            });
        }
        RuneKit.later(leap, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 g = new Vec3(sp.getX(), RuneKit.groundAt(lv, V.up(sp.position(), 1.0), sp.getY()), sp.getZ());
            Vec3 f = Kit.facingOf(sp);
            // 내리찍음: 큰 세로 검기 (땅을 달림) · 얼음 가시 고리 · 얼음 고리 · 충격 · 조각
            JobFx.groundWave(lv, sp, g, f, 1.6, 3.5, 22, j);
            // 2026-11-13 3D 얼음 (직업 도감 메모 "얼음 연출을 3d로" — 2D 가시 그림 대신): 바깥 고리의 얼음 가시 일곱 · 발밑에서 바깥으로 기운 결정 다섯
            JobAxe3d.spikeRing(lv, g, 7, R * 0.75, R * 1.05, 1.2, 1.7, 9);
            JobAxe3d.crystals(lv, g, 5, 0.9, R * 0.6, 0.9, 1.4, 0.4, 28, 46, false, 0, 7, 4);
            JobFx15.halo(lv, V.up(g, 0.08), R, 0.45, Opt.of("alpha", 1.0), ICE, WHITE);
            SkillFx.shock(lv, V.up(g, 0.1), ICE, R * 1.2, 0.4);
            JobTraits16.shardsFx(lv, V.up(g, 0.5), 1.2);
            JobFx.slash(sp, j, 1.8, 0.3, 2.4, 92.0, 0.22);
            SkillFx.play(lv, "item.mace.smash_ground", g, 0.9, 0.9);
            SkillFx.play(lv, "block.glass.break", g, 0.8, 0.8);
            for (LivingEntity e : Kit.around(sp, V.up(g, 1.0), R, 2.5)) {
                JobKit.strike(sp, e, j, P, k.num("slam", 3.9), false, false, life, 0, d -> JobImpact.hit(sp, e, j, 1.4));
            }
            // 서리 갑옷: 보호막 · 몸을 감싸는 서리
            double cap = RpgHealth.maxHpOf(sp) * k.num("shield", 0.1);
            int ticks = (int) k.num("shieldFor", 60);
            RpgHealth.addShield(sp, cap, ticks, cap);
            JobFx.aura(sp, j, 2.4, ticks / 20.0, 0.55);
            SkillFx.play(lv, "block.amethyst_block.resonate", g, 1.5, 0.7);
        });
        return true;
    }

    // ------------------------------------------------------------ 샤터

    /**
     * 샤터 (롤 세주아니 R · 애니비아 E): 도끼를 머리 위로 크게 감았다가 slamAt 틱에 겨눈 적 (reach 칸 — 없으면 앞 fallback 칸의 가장 가까운 적) 을 내려찍어 (slam)
     * 얼음 결정에 가둠 (rfx_jicecage — shatterAt 까지 얼어붙음 · 묶임) → 몸을 크게 비틀어 shatterAt 틱에 결정째 깨부숨 (shatter · 큰 숫자 · 얼음 조각 — 특성의 깨짐 덤 없이 이
     * 숫자: 제 결정을 풀고 침) → 깨진 적 chill 틱 둔화 (강화)
     */
    static boolean shatter(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int slamAt = Math.max(2, (int) k.num("slamAt", 8));
        int shatterAt = Math.max(slamAt + 10, (int) k.num("shatterAt", 28));
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        LivingEntity t0 = Kit.pickTarget(sp, k.num("reach", 5), 25);
        if (t0 == null) {
            List<LivingEntity> c = RuneKit.coneTargets(sp, k.num("fallback", 3.5), 90, 1);
            t0 = c.isEmpty() ? null : c.get(0);
        }
        LivingEntity t = t0;
        RpgCombat.setSteadfast(sp, shatterAt + 2);
        JobFx.charge(sp, j, new Vec3(0.2, 2.2, -0.2), slamAt, 0.8);
        SkillFx.play(level0, "block.respawn_anchor.charge", sp.position(), 1.6, 0.5);
        Vec3[] cage = {null};
        List<List<Display>> cageIce = new ArrayList<>();
        RuneKit.later(slamAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 f = Kit.facingOf(sp);
            Vec3 g = t != null && t.isAlive() && !t.isRemoved() ? t.position() : ground(lv, V.add(sp.position(), f, 2.2));
            cage[0] = g;
            double h = t != null ? t.getBbHeight() + 1.0 : 2.4;
            JobFx.slash(sp, j, 1.8, 0.3, 2.6, 92.0, 0.22);
            JobFx.groundWave(lv, sp, ground(lv, sp.position()), f, 1.4, Math.max(1.0, Math.hypot(g.x - sp.getX(), g.z - sp.getZ())), 30, j);
            // 2026-11-13 3D 얼음 감옥 (직업 도감 메모 "얼음 연출을 3d로" — 2D 결정 그림 대신): 안쪽으로 기운 결정 여섯 (shatterAt 에 부서짐) · 몸을 가둔 얼음 덩어리
            double pad = t != null ? Kit.bodyPad(t) : 0.3;
            JobAxe3d.crystals(lv, g, 6, pad + 0.4, pad + 0.75, h * 0.8, h * 1.05, 0.3, 10, 20, true, 0, shatterAt - slamAt - 4, 3);
            if (t != null && t.isAlive() && !t.isRemoved()) cageIce.add(IceFx.encase(lv, t, t.position(), shatterAt - slamAt + 3));
            IceFx.shards(lv, V.up(g, 0.4), 16, 0.6, 0.2, 0.12);
            SkillFx.play(lv, "item.mace.smash_ground", g, 1.0, 0.9);
            SkillFx.play(lv, "block.glass.place", g, 0.6, 1.0);
            SkillFx.play(lv, "block.amethyst_block.place", g, 0.5, 0.9);
            if (t == null || !t.isAlive() || t.isRemoved()) return;
            // 얼림은 피해가 들어간 뒤 (무적 시간으로 미뤄지면 그때 — 얼린 뒤 들어가 깨짐 덤이 붙지 않게)
            JobKit.strike(sp, t, j, P, k.num("slam", 4.2), false, false, life, 0, d -> {
                JobImpact.hit(sp, t, j, 1.5);
                JobTraits16.freeze(sp, j, t, shatterAt - slamAt, false);
            });
        });
        // 비틀어 감음 (shatterAt − 6) → 깨부숨
        RuneKit.later(shatterAt - 6, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.charge(sp, j, new Vec3(-0.6, 1.2, -0.2), 5, 0.9);
        });
        RuneKit.later(shatterAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 g = t != null && !t.isRemoved() ? t.position() : cage[0] != null ? cage[0] : sp.position();
            double h = t != null ? t.getBbHeight() + 1.0 : 2.4;
            // 2026-11-13 3D 감옥이 깨짐: 얼음 덩어리를 지우고 3D 얼음 조각이 사방으로 튐 (결정은 이 틱에 부서지기 시작)
            for (List<Display> l : cageIce) for (Display d : l) FxDisplays.remove(d);
            JobAxe3d.iceShards(lv, V.up(g, h * 0.45), 10, 2.8, 0.75);
            IceFx.shards(lv, V.up(g, h * 0.5), 46, 0.7, h * 0.3, 0.28);
            JobTraits16.shardsFx(lv, V.up(g, h * 0.45), 2.0);
            JobFx.slash(sp, j, 2.0, 0.2, 3.0, 4.0, 0.25);
            JobFx.slashWave(sp, j, 4.0, 2.2, 5.0, 26);
            SkillFx.play(lv, "block.glass.break", g, 0.6, 1.0);
            SkillFx.play(lv, "block.amethyst_block.break", g, 0.5, 1.0);
            SkillFx.play(lv, "item.mace.smash_ground_heavy", g, 1.1, 0.8);
            if (t == null || !t.isAlive() || t.isRemoved()) return;
            JobTraits16.thaw(sp, t);
            JobKit.strike(sp, t, j, P, k.num("shatter", 7.5), false, true, life, 0, d -> {
                JobImpact.hit(sp, t, j, 2.2);
                // 깨진 적: chill 틱 둔화 (강화 — 깨진 뒤 둔화) · 몸에 남은 서리 김
                if (!JobKit.isBoss(t)) RuneKit.effect(t, "slowness", JobUpgrades.ticks(sp, k, "chill", (int) k.num("chill", 60)), 1);
                SkillFx.fx(sp.level(), "rpg:snowfall", V.up(Kit.bodyPoint(t), 0.4), Opt.of("size", 0.8, "life", 0.8, "color", WHITE, "alpha", 0.8));
            });
        });
        return true;
    }

    // ------------------------------------------------------------ 요툰 그립

    /**
     * 요툰 그립 (롤 세주아니 패시브 · 나서스 R): dur 틱 태세 — 도끼 평타마다 등 뒤 서리 거인의 팔이 같은 쪽으로 거대한 얼음 도끼를 내려침 (평타 × echo · 서리 +1 더 —
     * JobTraits16) · 밀려나지 않음 · 받는 피해 −guard. 그림: 서리 거인의 포효 · 몸을 감싸는 서리 · 발밑 얼음 고리 · 등 뒤 거인의 팔이 솟음
     */
    static boolean jotunn(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = JobUpgrades.ticks(sp, k, "dur", (int) k.num("dur", 160));
        JobTraits.startStance(sp, k, dur);
        Guards.set(sp, dur, k.num("guard", 0.1), null, p -> JobTraits.stanceOn(p, k.id()));
        RpgCombat.setSteadfast(sp, dur);
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        JobFx.aura(sp, j, 3.0, 0.9, 1.0);
        JobTraits16.jotunnOn(sp, j);
        for (int q = 0; q < 3; q++) {
            int qq = q;
            RuneKit.later(q * 4, () -> JobFx15.halo(sp.level(), V.up(sp.position(), 0.08), 1.4 + qq * 1.1, 0.5, Opt.of("alpha", 0.9 - qq * 0.15), ICE, WHITE));
        }
        JobTraits16.shardsFx(level, V.up(c, 1.0), 1.0);
        SkillFx.play(level, "entity.polar_bear.warning", c, 0.6, 0.9);
        SkillFx.play(level, "block.glass.place", c, 0.5, 0.8);
        SkillFx.play(level, "entity.player.hurt_freeze", c, 0.8, 0.8);
        return true;
    }

    // ------------------------------------------------------------ 핌불윈터

    /**
     * 2026-11-13 핌불윈터의 3D 빙하 도끼: 크기 (길이 약 11칸 — 예전 환영 도끼 판 7배와 비슷) · 하늘에서 도는 도끼머리 가운데 높이 (겨눈 곳 위 — 영상 카메라에 들게) ·
     * 박힌 도끼머리 가운데 높이 (도끼머리가 아래 — 창끝 · 날 아래쪽이 땅에 묻힘, 자루는 9칸 가까이 솟음)
     */
    private static final double GIANT = 3.9;
    private static final double SKY = 7.5;
    private static final double REST = 0.6;

    /**
     * 하늘에서 도는 빙하 도끼의 도끼머리 가운데 (소품의 쥔 곳): 도끼 가운데 (자루 위 모델 (8, 6.75, 8)) c 둘레로 axis (보는 쪽) 를 축 삼아 deg 돈 자리 — 처음 (0°)
     * 은 자루가 위 · 날이 side. k = 크기 배
     */
    private static Vec3 skyHead(Vec3 c, Vec3 axis, Vec3 side, double deg, double k) {
        double r = Math.toRadians(deg);
        Vec3 n = V.unit(axis);
        Vec3 haft = V.add(V.scale(V.UP, Math.cos(r)), V.cross(n, V.UP), Math.sin(r));
        Vec3 edge = V.add(V.add(V.scale(side, Math.cos(r)), V.cross(n, side), Math.sin(r)), n, V.dot(n, side) * (1 - Math.cos(r)));
        double px = GIANT * k / 16.0;
        return V.add(V.add(c, haft, (JobAxe3d.HEAD[1] - 6.75) * px), edge, (8 - JobAxe3d.HEAD[2]) * px);
    }

    /**
     * 핌불윈터 (롤 세주아니 R · 애쉬 R): throwAt 틱에 도끼를 하늘 높이 던지면 하늘이 얼어붙고 (rpg:ult_fimbul) 눈보라 → 거대한 빙하 도끼로 자라 겨눈 적 (range 칸 — 없으면
     * 앞 fallback 칸의 땅) 위 하늘에서 내려와 dropAt 틱에 내리꽂힘 (둘레 radius 칸 drop · 빙하가 솟아 가둠 — 얼어붙음) → shatterAt 틱에 도끼를 되불러 빙하가 산산이 깨짐
     * (shatter · 큰 숫자 — 제 빙하라 특성의 깨짐 덤 없이) · 도끼가 손으로 돌아옴. 화면은 흔들지 않음
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int throwAt = Math.max(2, (int) k.num("throwAt", 6));
        int dropAt = Math.max(throwAt + 10, (int) k.num("dropAt", 26));
        int shatterAt = Math.max(dropAt + 10, (int) k.num("shatterAt", 46));
        double R = k.num("radius", 2.5);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 f0 = Kit.facingOf(sp);
        LivingEntity t = Kit.pickTarget(sp, k.num("range", 16), 25);
        Vec3[] spot = {t != null ? t.position() : JobMove.landAhead(sp, k.num("fallback", 8), c0.y).apply(c0)};
        RpgCombat.setSteadfast(sp, shatterAt + 4);
        Ult.sky(level0, c0, "rpg:ult_fimbul", shatterAt + 40);
        Ult.flash(level0, c0, ICE, Opt.of("radius", 8.0, "time", new double[] {0.03, 0.05, 0.3}), sp);
        SkillFx.play(level0, "entity.polar_bear.warning", c0, 0.5, 1.0);
        SkillFx.play(level0, "item.trident.riptide_3", c0, 0.7, 0.8);
        JobFx.charge(sp, j, new Vec3(0.2, 2.3, -0.3), throwAt, 1.0);
        // 하늘로 던짐 (2026-11-13 3D 빙하 도끼가 앞으로 구르며 솟음)
        RuneKit.later(throwAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 from = V.up(RuneKit.handOf(sp).hand(), 0.3);
            axe(lv, from, V.up(V.add(from, f0, 1.5), 12), 7, AXE);
            frostTrail(lv, from, V.up(from, 10), j, Opt.empty());
            SkillFx.play(lv, "item.trident.throw", from, 0.6, 1.0);
        });
        // 눈보라 (던진 뒤 ~ 깨질 때까지): 겨눈 곳 둘레 눈발 · 바람
        for (int q = throwAt; q < shatterAt; q += 3) {
            RuneKit.later(q, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 s = t != null && t.isAlive() && !t.isRemoved() ? t.position() : spot[0];
                for (int i = 0; i < 6; i++) {
                    Vec3 p = V.up(V.add(s, new Vec3(Kit.rand() - 0.5, 0, Kit.rand() - 0.5), 9.0), 3 + Kit.rand() * 6);
                    SkillFx.fx(lv, "rpg:snowfall", p, Opt.of("size", 0.9, "life", 1.2, "color", WHITE, "alpha", 0.85));
                }
            });
        }
        // 2026-11-13 하늘의 3D 빙하 도끼 (직업 도감 메모 "도끼와 얼음 연출을 3d로 수정" — 환영 도끼 판 대신): 겨눈 곳 위 SKY 칸에 자라 넓은 면을 보이며 천천히 돌아
        // (dropAt − 5 에 도끼머리가 아래) 내리꽂힘 → 빙하에 박혀 있다가 (shatterAt − 4) 뽑혀 작아지며 손으로
        int grow = throwAt + 6;
        int spinFor = Math.max(7, dropAt - 5 - grow);
        Vec3 side = V.rightOf(f0);
        Quaternionf up0 = JobAxe3d.pose(V.UP, side);
        Vfx.Prop[] giant = {null};
        RuneKit.later(grow, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 s = t != null && t.isAlive() && !t.isRemoved() ? t.position() : spot[0];
            spot[0] = s;
            Vec3 c = V.up(s, SKY);
            Vfx.Fx fx = RuneProps.fx(lv, c, shatterAt - grow + 4);
            Vfx.Prop p = JobAxe3d.axe(fx, true, skyHead(c, f0, side, 0, 0.25), JobAxe3d.HEAD, up0, GIANT * 0.25, ICE, WHITE);
            giant[0] = p;
            // 자라며 (5틱) 돌기 시작 → 틱마다 이어 돎 (도끼 가운데 둘레 — 내리꽂는 틱 앞까지, 같은 틱에 겹쳐 옮기지 않게)
            double d5 = 180.0 * 5 / spinFor;
            fx.on(1, () -> p.to(skyHead(c, f0, side, d5, 1), JobAxe3d.turn(up0, f0, d5), GIANT, 5));
            fx.each(6, spinFor - 1, 1, a -> {
                Vec3 cur = t != null && t.isAlive() && !t.isRemoved() ? t.position() : spot[0];
                spot[0] = cur;
                double deg = 180.0 * a / spinFor;
                p.to(skyHead(V.up(cur, SKY), f0, side, deg, 1), JobAxe3d.turn(up0, f0, deg), -1, 1);
            });
            JobFx15.halo(lv, V.up(s, 0.08), R * 1.6, (dropAt - grow) / 20.0, Opt.of("alpha", 0.75), ICE, WHITE);
            IceFx.snow(lv, V.up(s, SKY), 30, 2.0, 2.0, 0.05);
            SkillFx.play(lv, "block.beacon.power_select", s, 0.5, 0.9);
        });
        RuneKit.later(dropAt - 5, () -> {
            if (!RuneKit.sameLife(sp, life) || giant[0] == null) return;
            Vec3 s = ground(sp.level(), t != null && t.isAlive() && !t.isRemoved() ? t.position() : spot[0]);
            spot[0] = s;
            giant[0].to(V.up(s, REST), JobAxe3d.turn(up0, f0, 180), GIANT, 5);
            frostTrail(sp.level(), V.up(s, SKY + 3), V.up(s, 3), j, Opt.empty());
            SkillFx.play(sp.level(), "item.trident.riptide_2", s, 0.6, 1.0);
        });
        // 내리꽂힘: 3D 빙하가 솟아 가둠 (가운데 큰 결정 · 둘레 결정 · 바깥 얼음 가시) · 맞은 적을 얼음 덩어리에 가둠 · 충격
        List<List<Display>> encased = new ArrayList<>();
        RuneKit.later(dropAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 s = ground(lv, spot[0]);
            int hold = shatterAt - dropAt;
            JobAxe3d.crystals(lv, s, 6, 1.0, 2.1, 3.4, 5.6, 0.36, 6, 20, false, 0, hold - 4, 4);
            JobAxe3d.crystals(lv, s, 9, R * 1.0, R * 1.4, 1.6, 2.6, 0.42, 20, 38, false, 0, hold - 4, 4);
            JobAxe3d.spikeRing(lv, s, 10, R + 0.8, R + 2.2, 1.1, 1.7, hold - 2);
            JobFx15.halo(lv, V.up(s, 0.08), R * 2.2, 0.6, Opt.of("alpha", 1.0), ICE, WHITE);
            SkillFx.shock(lv, V.up(s, 0.1), ICE, R * 2.6, 0.5);
            JobTraits16.shardsFx(lv, V.up(s, 1.0), 2.4);
            IceFx.shards(lv, V.up(s, 0.6), 60, 1.6, 0.6, 0.3);
            Ult.flash(lv, s, WHITE, Opt.empty(), sp);
            SkillFx.play(lv, "item.mace.smash_ground_heavy", s, 0.7, 1.0);
            SkillFx.play(lv, "block.glass.place", s, 0.4, 1.0);
            SkillFx.play(lv, "entity.generic.explode", s, 1.4, 0.6);
            for (LivingEntity e : Kit.around(sp, V.up(s, 1.0), R, 3.0)) {
                JobKit.strike(sp, e, j, P, k.num("drop", 20), false, true, life, 0, d -> {
                    JobImpact.hit(sp, e, j, 2.0);
                    JobTraits16.freeze(sp, j, e, hold, false);
                    if (!JobKit.isBoss(e) && e.isAlive() && !e.isRemoved()) encased.add(IceFx.encase(sp.level(), e, e.position(), hold + 2));
                });
            }
        });
        // 되부름 (shatterAt − 4): 도끼가 빙하에서 뽑혀 (2틱) 구르며 작아져 손으로 (3틱) → shatterAt 빙하가 산산이 깨짐
        RuneKit.later(shatterAt - 4, () -> {
            if (!RuneKit.sameLife(sp, life) || giant[0] == null) return;
            Vfx.Prop p = giant[0];
            Vec3 s = ground(sp.level(), spot[0]);
            Vec3 out = V.up(s, REST + 2.6);
            p.to(out, null, GIANT * 0.8, 2);
            RuneKit.later(2, () -> {
                if (!RuneKit.sameLife(sp, life)) {
                    p.remove();
                    return;
                }
                Vec3 hand = V.up(RuneKit.handOf(sp).hand(), 0.3);
                Vec3 fly = V.sub(hand, out);
                Vec3 ax = JobFx3d.sideOf(V.len(fly) < 1e-3 ? f0 : V.unit(fly));
                Quaternionf q0 = p.rot();
                for (int i = 1; i <= 3; i++) {
                    int ii = i;
                    RuneKit.later(i - 1, () -> p.to(V.lerp(out, hand, ii / 3.0), JobAxe3d.turn(q0, ax, -95.0 * ii), GIANT * 0.8 + (AXE - GIANT * 0.8) * ii / 3.0, 1));
                }
                RuneKit.later(4, p::remove);
            });
            frostTrail(sp.level(), V.up(s, 3.0), V.up(sp.position(), 1.2), j, JobFx.hide1p(sp));
            SkillFx.play(sp.level(), "item.trident.return", s, 0.8, 1.0);
        });
        RuneKit.later(shatterAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 s = ground(lv, spot[0]);
            // 2026-11-13 3D 빙하가 깨짐 (결정은 이 틱에 부서지기 시작): 갇힌 얼음 덩어리를 지우고 큰 3D 얼음 조각이 사방으로
            for (List<Display> l : encased) for (Display dd : l) FxDisplays.remove(dd);
            JobAxe3d.iceShards(lv, V.up(s, 2.4), 14, 5.0, 1.3);
            JobAxe3d.iceShards(lv, V.up(s, 0.9), 10, 3.6, 0.9);
            IceFx.shards(lv, V.up(s, 2.0), 90, 2.2, 1.6, 0.35);
            JobTraits16.shardsFx(lv, V.up(s, 2.5), 3.2);
            JobTraits16.shardsFx(lv, V.up(s, 0.8), 2.4);
            JobFx15.halo(lv, V.up(s, 0.1), R * 2.6, 0.5, Opt.of("alpha", 1.0), ICE, WHITE);
            SkillFx.shock(lv, V.up(s, 0.2), WHITE, R * 3.0, 0.5);
            Ult.flash(lv, s, ICE, Opt.empty(), sp);
            SkillFx.play(lv, "block.glass.break", s, 0.5, 1.0);
            SkillFx.play(lv, "block.amethyst_block.break", s, 0.4, 1.0);
            SkillFx.play(lv, "entity.generic.explode", s, 0.9, 0.8);
            Set<UUID> seen = new HashSet<>();
            List<LivingEntity> hit = new ArrayList<>(Kit.around(sp, V.up(s, 1.0), R + 0.5, 3.0));
            for (LivingEntity e : hit) {
                if (!seen.add(e.getUUID())) continue;
                JobTraits16.thaw(sp, e);
                JobKit.strike(sp, e, j, P, k.num("shatter", 32), false, true, life, 0, d -> JobImpact.hit(sp, e, j, 2.6));
            }
        });
        return true;
    }

    /** 땅 높이로 내린 자리 */
    private static Vec3 ground(ServerLevel level, Vec3 p) {
        return new Vec3(p.x, RuneKit.groundAt(level, V.up(p, 0.6), p.y), p.z);
    }

    /** 시험 · 직업을 바꿈: 남은 던진 도끼를 지움 */
    static void forget(UUID id) {
        THROWN.remove(id);
    }
}
