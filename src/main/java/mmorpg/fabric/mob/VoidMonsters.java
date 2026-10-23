package mmorpg.fabric.mob;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Obj;
import mmorpg.fabric.FxDisplays;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 0.32.0 공허 몬스터 (rules_more.json MONSTERS 의 void · leap · blink · orb · tunnel · spikes — 사용자 요청 "공허 시리즈 40렙 뒤 사냥터, 롤 공허 캐릭터 같은 느낌"):
 * <ul>
 * <li>공통 (void): 균열이 열리며 나타남 (세로 균열 · 공허 알갱이 · 소리) · 죽으면 빨려 들어가듯 사라짐 (검은 구멍이 오므라듦 · 알갱이가 안으로) · 몸 둘레 공허 알갱이</li>
 * <li>공허 도약충 leap: range 안 min 밖이면 웅크림 (prep 틱 — 떨어질 자리에 분홍 경고 원) → 포물선으로 덮침 (air 틱) → 떨어진 자리 둘레 radius 칸 attack × mult · 구속</li>
 * <li>공허 추적자 blink: 균열 속으로 사라짐 (vanish 틱 — 보이지 않고 맞지 않음) → 대상의 등 뒤 behind 칸에 균열 + 경고 원 (open 틱) → 나타나 strike 틱 뒤 십자 베기
 * (그 자리에서 사거리 · 앞 arc 도 안이면 × mult · 어둠 darkness 초 — 비키면 빗나감)</li>
 * <li>공허 해파리 orb: keep 칸 밖 range 안이면 제자리에 떠서 charge 틱 모아 구체를 쏨 (speed 칸/틱 · 조금씩 꺾여 따라옴) → 사람 · 벽에 닿거나 life 틱이 지나면
 * 둘레 radius 칸 터짐 (attack). keep 칸 안 · 사거리 밖이면 그 자리에 머묾 (다가오지 않음)</li>
 * <li>공허 굴착자 tunnel: 파고듦 (dive 틱) → 땅속 (맞지 않음 · 이름표 없음) 으로 대상에게 다가가며 금 · 흙이 튐 (최대 max 틱) → 대상 발밑에 경고 원 (warn 틱) → 솟구쳐
 * 둘레 radius 칸 × mult · 띄움 lift</li>
 * <li>공허 거수 spikes: 크게 일어섬 (prep 틱 — 나를 향한 줄에 경고 원 count 개) → 내려찍으면 수정 가시가 gap 틱마다 하나씩 솟음 (자수정 블록 표시 · 수정 그림 · 조각) —
 * 가시마다 둘레 radius 칸 × mult · 띄움 (같은 사람은 한 번만)</li>
 * </ul>
 * 특성은 달려들기 대신 (Monsters.fightStep 이 tryTrait — 재사용 every 틱, 기절 · 묶임 · 짧은 은신이면 쓰지 않음). 진행 중인 특성은 Fight.voidRun.
 */
public final class VoidMonsters {
    private VoidMonsters() {}

    // ------------------------------------------------------------ 설정

    public record Leap(double range, double min, int every, int prep, int air, double radius, double mult, double slowSec, int slowAmp) {}

    public record Blink(double range, double min, int every, int vanish, int open, int strike, double behind, double mult, double arc, double darkness) {}

    public record Orb(double range, double keep, int every, int charge, double speed, int life, double radius, double attack, double homing) {}

    public record Tunnel(double range, double min, int every, int dive, double speed, int max, int warn, double radius, double mult, double lift) {}

    public record Spikes(double range, double min, int every, int prep, int count, double step, int gap, double radius, double mult, double lift, int linger) {}

    /** 공허 특성 묶음 (void 가 아니면 NONE) */
    public record Traits(boolean isVoid, Leap leap, Blink blink, Orb orb, Tunnel tunnel, Spikes spikes) {}

    public static final Traits NONE = new Traits(false, null, null, null, null, null);

    static Traits parse(Obj o) {
        boolean isVoid = o.has("void") && o.truthy("void");
        Obj l = o.objOrNull("leap");
        Obj b = o.objOrNull("blink");
        Obj r = o.objOrNull("orb");
        Obj t = o.objOrNull("tunnel");
        Obj s = o.objOrNull("spikes");
        if (!isVoid && l == null && b == null && r == null && t == null && s == null) return NONE;
        List<Object> slow = l != null ? l.listOrNull("slow") : null;
        return new Traits(isVoid,
                l == null ? null : new Leap(l.num("range"), l.num("min"), (int) l.num("every"), (int) l.num("prep"), (int) l.num("air"), l.num("radius"), l.num("mult"),
                        slow != null && slow.size() >= 2 ? ((Number) slow.get(0)).doubleValue() : 0, slow != null && slow.size() >= 2 ? ((Number) slow.get(1)).intValue() : 0),
                b == null ? null : new Blink(b.num("range"), b.num("min"), (int) b.num("every"), (int) b.num("vanish"), (int) b.num("open"), (int) b.num("strike"),
                        b.num("behind"), b.num("mult"), b.num("arc"), b.numOr("darkness", 0)),
                r == null ? null : new Orb(r.num("range"), r.num("keep"), (int) r.num("every"), (int) r.num("charge"), r.num("speed"), (int) r.num("life"),
                        r.num("radius"), r.num("attack"), r.numOr("homing", 0)),
                t == null ? null : new Tunnel(t.num("range"), t.num("min"), (int) t.num("every"), (int) t.num("dive"), t.num("speed"), (int) t.num("max"),
                        (int) t.num("warn"), t.num("radius"), t.num("mult"), t.num("lift")),
                s == null ? null : new Spikes(s.num("range"), s.num("min"), (int) s.num("every"), (int) s.num("prep"), (int) s.num("count"), s.num("step"),
                        (int) s.num("gap"), s.num("radius"), s.num("mult"), s.num("lift"), (int) s.num("linger")));
    }

    // ------------------------------------------------------------ 색 · 소리

    static final double[] PINK = {1.0, 0.31, 0.85};
    static final double[] VIOLET = {0.62, 0.36, 1.0};
    static final double[] LIGHT = {1.0, 0.86, 0.98};
    static final double[] DEEP = {0.22, 0.1, 0.36};
    static final double[] DIRT = {0.36, 0.28, 0.42};

    /** 공허 몬스터가 쓰는 소리 (시험이 모두 자바 소리로 풀리는지 봄) */
    public static final List<String> SOUNDS = List.of("entity.enderman.teleport", "block.respawn_anchor.charge", "block.respawn_anchor.deplete", "entity.endermite.death",
            "entity.silverfish.ambient", "entity.breeze.jump", "entity.magma_cube.squish", "entity.evoker.prepare_attack", "entity.warden.attack_impact",
            "entity.illusioner.cast_spell", "entity.shulker.shoot", "entity.shulker_bullet.hit", "entity.generic.explode", "entity.warden.dig", "block.gravel.break",
            "entity.warden.emerge", "block.amethyst_cluster.break", "block.amethyst_block.resonate", "entity.ravager.roar", "item.mace.smash_ground_heavy",
            "entity.player.attack.sweep", "block.end_portal_frame.fill");

    // ------------------------------------------------------------ 상태

    /** 보이지 않거나 땅속이라 맞지 않는 몬스터 (추적자가 균열 속 · 굴착자가 땅속) */
    private static final Set<UUID> PHASED = new HashSet<>();
    /** 날아가는 공허 구체 */
    private static final List<OrbShot> ORBS = new ArrayList<>();
    /** 시험 · 사진첩: 마지막으로 솟은 가시 자리들 · 쏜 구체 수 · 터진 구체 수 */
    public static final List<Vec3> lastSpikes = new ArrayList<>();
    public static int orbsFired;
    public static int orbsBurst;

    /** 맞지 않는 상태인지 (균열 속 · 땅속) */
    public static boolean phased(RpgMonster m) {
        return PHASED.contains(m.getUUID());
    }

    /** 날아가는 구체 수 (시험) */
    public static int orbCount() {
        return ORBS.size();
    }

    /** 하던 특성 이름 (leap · blink · orb · tunnel · spikes — 없으면 null, 시험) 과 단계 */
    public static String runKind(Monsters.Fight f) {
        return f != null && f.voidRun instanceof Run r ? r.kind : null;
    }

    public static String runPhase(Monsters.Fight f) {
        return f != null && f.voidRun instanceof Run r ? r.phase : null;
    }

    /** 시험 · 사진첩: 다음 특성을 바로 쓸 수 있게 (재사용 대기를 지움) */
    public static void readyTrait(Monsters.Fight f) {
        if (f != null) f.nextTrait = 0;
    }

    /** 특성이 노린 자리 (덮칠 자리 · 균열 자리 · 솟을 자리 — 없으면 null, 시험 · 사진첩) */
    public static Vec3 runSpot(Monsters.Fight f) {
        return f != null && f.voidRun instanceof Run r ? r.spot : null;
    }

    /** 진행 중인 특성 하나 */
    static final class Run {
        final String kind;
        final ServerPlayer target;
        final long start;
        String phase;
        long phaseUntil;
        Vec3 spot;
        Vec3 from;
        Vec3 dir;
        long flyStart;
        int flyTicks;
        double peak;
        int travelled;

        Run(String kind, ServerPlayer target, long start) {
            this.kind = kind;
            this.target = target;
            this.start = start;
        }
    }

    // ------------------------------------------------------------ 시작 (Monsters.fightStep — 근접 사거리 밖일 때)

    /** 특성을 시작했으면 true (이번 단계는 그것으로 끝) */
    static boolean tryTrait(Monsters.Fight f, Monsters.Gap g, long now) {
        Monsters.Kind k = f.k;
        Traits t = k.traits();
        if (t == null || f.voidRun != null || now < f.nextTrait) return false;
        RpgMonster m = f.m;
        ServerPlayer p = f.player;
        if (Monsters.rooted(m) || Monsters.hidden(p)) return false;
        double gap = g.gap();
        if (t.leap() != null && gap >= t.leap().min() && gap <= t.leap().range() && Math.abs(g.dy()) <= 3 && Monsters.canSee(m, k, p)) {
            startLeap(f, now);
            return true;
        }
        if (t.blink() != null && gap >= t.blink().min() && gap <= t.blink().range() && Monsters.canSee(m, k, p)) {
            startBlink(f, now);
            return true;
        }
        if (t.orb() != null && gap > k.reach() && gap <= t.orb().range() && Monsters.canSee(m, k, p)) {
            startOrb(f, now);
            return true;
        }
        if (t.tunnel() != null && gap >= t.tunnel().min() && gap <= t.tunnel().range() && Math.abs(g.dy()) <= 3 && onSolid(m)) {
            startTunnel(f, now);
            return true;
        }
        if (t.spikes() != null && gap >= t.spikes().min() && gap <= t.spikes().range() && Math.abs(g.dy()) <= 2.5 && Monsters.canSee(m, k, p)) {
            startSpikes(f, now);
            return true;
        }
        return false;
    }

    /** 발밑이 단단한 땅인지 (굴착자가 파고들 수 있는 곳 — 물 · 공중은 아님) */
    static boolean onSolid(RpgMonster m) {
        Double g = RuneKit.solidGround(m.level(), V.up(m.position(), 0.3), 1.0);
        return g != null && !m.isInWater();
    }

    /** 해파리: 구체를 쏠 거리 (사거리 밖 · keep 안) 면 다가오지 않고 그 자리에 머묾 (2틱마다) */
    static void hold(Monsters.Fight f, Monsters.Gap g, long now) {
        Orb o = f.k.traits() != null ? f.k.traits().orb() : null;
        if (o == null || f.voidRun != null) return;
        if (g.gap() > f.k.reach() + 0.4 && g.gap() <= o.keep() && Monsters.canSee(f.m, f.k, f.player)) {
            f.m.holdUntil = Math.max(f.m.holdUntil, now + 6);
            f.m.getNavigation().stop();
            face(f.m, f.player.position());
        }
    }

    // ------------------------------------------------------------ 진행 (2틱마다 — Monsters.fightStep)

    /** 진행 중인 특성의 단계 (끝나면 voidRun = null) */
    static void step(Monsters.Fight f, long now) {
        Run r = (Run) f.voidRun;
        if (r == null) return;
        if (Monsters.targetState(r.target, f.m) != Monsters.State.OK && !r.phase.equals("fly")) {
            cancel(f, now);
            return;
        }
        try {
            switch (r.kind) {
                case "leap" -> leapStep(f, r, now);
                case "blink" -> blinkStep(f, r, now);
                case "orb" -> orbStep(f, r, now);
                case "tunnel" -> tunnelStep(f, r, now);
                case "spikes" -> spikesStep(f, r, now);
                default -> f.voidRun = null;
            }
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[mmorpg] void trait " + r.kind, e);
            cancel(f, now);
        }
    }

    /** 기절 · 빙결로 끊길 수 있는지 (땅속 · 균열 속 · 공중은 끊기지 않음) */
    static boolean interruptible(Monsters.Fight f) {
        return f.voidRun instanceof Run r && !(r.phase.equals("hidden") || r.phase.equals("under") || r.phase.equals("warn") || r.phase.equals("fly")
                || r.phase.equals("spikes"));
    }

    /** 특성을 그만둠 (대상이 사라짐 · 기절): 숨은 몸은 그 자리에 다시 드러남 */
    static void cancel(Monsters.Fight f, long now) {
        Run r = (Run) f.voidRun;
        f.voidRun = null;
        RpgMonster m = f.m;
        m.holdUntil = 0;
        if (r == null) return;
        if (r.kind.equals("blink")) {
            m.stopAnimation("blink");
            if (m.isInvisible()) m.setInvisible(false);
        }
        if (r.kind.equals("tunnel") && m.buried()) surface(m, f.k, false);
        if (r.kind.equals("leap")) m.stopAnimation("leap");
        PHASED.remove(m.getUUID());
        f.nextAttack = Math.max(f.nextAttack, now + 10);
    }

    // ------------------------------------------------------------ 틱마다 (Monsters.serverTick — 덮치기 비행 · 땅속 이동 · 숨은 몸 바로잡기)

    static void tick(RpgMonster m, Monsters.Kind k, long now) {
        Monsters.Fight f = Monsters.fightOf(m);
        Run r = f != null && f.voidRun instanceof Run x ? x : null;
        if (r != null && r.kind.equals("leap") && r.phase.equals("fly")) flyTick(f, r, now);
        else if (r != null && r.kind.equals("tunnel") && r.phase.equals("under")) underTick(f, r, now);
        if (r == null) {
            // 다시 불러온 몸 · 끊긴 특성: 숨은 채 남지 않게
            if (m.isInvisible() && k.traits().blink() != null) m.setInvisible(false);
            if (PHASED.contains(m.getUUID())) PHASED.remove(m.getUUID());
            if (k.traits().tunnel() != null && m.buried()) surface(m, k, false);
        }
        if (k.traits().isVoid() && (now + m.getId()) % 30 == 0 && !m.isInvisible() && !m.buried() && m.level() instanceof ServerLevel level) ambient(level, m, k);
    }

    /** 몸 둘레 공허 알갱이 (1.5초마다 — 해파리는 아래로 떨어지는 보랏빛 방울도) */
    static void ambient(ServerLevel level, RpgMonster m, Monsters.Kind k) {
        double h = k.height();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, m.getX(), m.getY() + h * 0.55, m.getZ(), 3, k.half() * 0.8, h * 0.3, k.half() * 0.8, 0.01);
        if (k.traits().orb() != null) {
            level.sendParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR, m.getX(), m.getY() + h * 0.42, m.getZ(), 2, k.half() * 0.5, 0.05, k.half() * 0.5, 0);
        }
    }

    // ------------------------------------------------------------ 공통 연출

    /** 경고 원: 테두리 + 차오르는 원 (sec 초 — 다 차면 맞음) */
    static void warn(ServerLevel level, Vec3 at, double r, double sec, double[] col) {
        SkillFx.fx(level, "rpg:tele_disc", new Vec3(at.x, at.y + 0.06, at.z), Opt.of("dir", Vec3.ZERO, "size", r, "life", sec, "color", col, "alpha", 0.9));
        SkillFx.fx(level, "rpg:tele_fill", new Vec3(at.x, at.y + 0.07, at.z), Opt.of("dir", Vec3.ZERO, "size", r, "life", sec, "color", col, "alpha", 0.42, "grow", 1.0));
    }

    /** 세로 균열 (열림 · life 초): 가운데가 at, 키 size × 2 */
    static void rift(ServerLevel level, Vec3 at, double size, double life, double grow) {
        RuneKit.rfx2(level, "rift", at, Opt.of("dir", V.UP, "size", size, "life", life, "grow", grow, "alpha", 0.95, "fade", 0.35), PINK, LIGHT);
    }

    /** 땅 높이 (못 찾으면 fallback) */
    static double ground(ServerLevel level, Vec3 p, double fallback) {
        return RuneKit.groundAt(level, p, fallback);
    }

    static void face(RpgMonster m, Vec3 to) {
        float yaw = Kit.lookRotation(m.position(), new Vec3(to.x, m.getY(), to.z))[0];
        m.setYRot(yaw);
        m.setYBodyRot(yaw);
        m.setYHeadRot(yaw);
    }

    /** 몸이 균열에서 비집고 나오는 틱 (동작 spawn — 처음 0.22초는 몸이 보이지 않음) */
    public static final int EMERGE_TICKS = 5;

    /**
     * 나타남 (Monsters.spawnAt): 몸 뒤 (바라보는 쪽 반대 0.5 × 크기 칸) 에 세로 균열이 열리고 (알갱이가 빨려 듦), EMERGE_TICKS 뒤 몸이 균열에서 비집고 나옴
     * (동작 spawn — 좁고 작게 → 부풀며 앞으로) · 보라 폭발 · 소리. 상태는 바꾸지 않음 (그림만 — 바로 맞고 싸울 수 있음)
     */
    static void spawnFx(ServerLevel level, RpgMonster m, Monsters.Kind k) {
        double size = Math.max(1.0, k.height() * 0.62);
        Vec3 back = V.scale(Kit.facingOf(m), -0.5 * m.getScale());
        Vec3 c = new Vec3(m.getX() + back.x, m.getY() + size * 0.95, m.getZ() + back.z);
        rift(level, c, size, 1.1, 0.5); // 몸이 다 나온 (0.8초) 뒤 곧 닫힘
        level.sendParticles(ParticleTypes.PORTAL, c.x, c.y, c.z, 36, size * 0.5, size * 0.6, size * 0.5, 0.6);
        Monsters.play(level, "block.respawn_anchor.charge", c, 1.3, 0.5);
        Monsters.play(level, "block.end_portal_frame.fill", c, 0.6, 0.7);
        m.playAnimation(Monsters.anim(k, "spawn"), "spawn", null);
        Tasks.later(EMERGE_TICKS, () -> {
            if (m.isRemoved() || !(m.level() instanceof ServerLevel lv)) return;
            Vec3 b = V.up(m.position(), k.height() * 0.5);
            SkillFx.burst(lv, c, VIOLET, 0.8 + k.half(), 0.4);
            lv.sendParticles(ParticleTypes.REVERSE_PORTAL, b.x, b.y, b.z, 30, k.half() * 0.6, k.height() * 0.35, k.half() * 0.6, 0.06);
            Monsters.play(lv, "entity.enderman.teleport", b, 0.55, 0.9);
        });
    }

    /** 죽음: 검은 구멍이 오므라들며 몸이 빨려 듦 (동작 die — 비틀리며 오그라들어 구멍 가운데로 · 알갱이가 안으로 · 사라질 때 흰 연기 대신 보랏빛 — RpgMonster) */
    static void deathFx(ServerLevel level, net.minecraft.world.entity.LivingEntity e, Vec3 at, Monsters.Kind k) {
        double s = 0.8 + k.half() * 0.9;
        RuneKit.rfx(level, "hole", at, Opt.of("size", s * 1.3, "life", 0.8, "cell", 0.0, "spinRate", 360.0, "grow", -0.85, "alpha", 0.95));
        RuneKit.rfx(level, "hole", at, Opt.of("size", s * 1.3, "life", 0.8, "cell", (double) RuneKit.HOLE_CORE, "grow", -0.85));
        SkillFx.sparks(level, at, PINK, 10, 4);
        level.sendParticles(ParticleTypes.PORTAL, at.x, at.y, at.z, 40, k.half() + 0.4, k.height() * 0.4, k.half() + 0.4, 0.9);
        if (e instanceof RpgMonster m) m.playAnimation(Monsters.anim(k, "die"), "die", "0");
        Monsters.play(level, "block.respawn_anchor.deplete", at, 1.1, 0.9);
        Monsters.play(level, "entity.endermite.death", at, 0.6, 1);
    }

    /** 공허 충격파 (거수 앞발 — shock fx "void"): 분홍 고리 · 빛나는 금 · 수정 조각 */
    static void shockFx(ServerLevel level, Vec3 at, double r) {
        RuneKit.novaRing(level, at, 0.5, r, PINK, LIGHT, Opt.of("life", 0.5));
        RuneKit.glowCrack(level, at, r * 0.9, VIOLET, PINK, Opt.of("life", 1.4, "fade", 0.5));
        for (int i = 0; i < 5; i++) {
            double a = i * Math.PI * 2 / 5 + Kit.rand() * 0.6;
            Vec3 p = new Vec3(at.x + Math.cos(a) * r * 0.55, at.y, at.z + Math.sin(a) * r * 0.55);
            RuneKit.rfx2(level, "crystal", p, Opt.of("size", 0.55 + Kit.rand() * 0.3, "life", 0.7, "grow", 0.2, "fade", 0.4, "cell", Math.floor(Kit.rand() * 3),
                    "alpha", 0.95), VIOLET, LIGHT, new int[] {0, RuneKit.CRYSTAL_CORE}, null);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 0.3, at.z, 24, r * 0.4, 0.2, r * 0.4, 0.05);
        Monsters.play(level, "block.amethyst_cluster.break", at, 0.6, 1);
    }

    // ------------------------------------------------------------ 공허 도약충: 덮치기

    static void startLeap(Monsters.Fight f, long now) {
        Leap l = f.k.traits().leap();
        RpgMonster m = f.m;
        ServerLevel level = (ServerLevel) m.level();
        Run r = new Run("leap", f.player, now);
        r.phase = "prep";
        r.phaseUntil = now + l.prep();
        Vec3 tp = f.player.position();
        r.spot = new Vec3(tp.x, ground(level, tp, tp.y), tp.z);
        f.voidRun = r;
        f.windup = null;
        f.nextTrait = now + l.every();
        f.nextAttack = Math.max(f.nextAttack, now + l.prep() + l.air() + 10);
        f.nextLunge = Math.max(f.nextLunge, now + l.prep() + l.air() + 20);
        m.holdUntil = now + l.prep() + l.air() + 8;
        m.getNavigation().stop();
        face(m, r.spot);
        m.playAnimation(Monsters.anim(f.k, "leap_prep"), "leap", "0");
        warn(level, r.spot, l.radius(), (l.prep() + l.air() * 0.6) / 20.0, PINK);
        Monsters.play(level, f.k.windupSound(), m.position(), 0.7, 1);
        SkillFx.burst(level, V.up(m.position(), 0.2), DIRT, 0.6 + f.k.half(), 0.3);
    }

    static void leapStep(Monsters.Fight f, Run r, long now) {
        if (!r.phase.equals("prep") || now < r.phaseUntil) return;
        Leap l = f.k.traits().leap();
        RpgMonster m = f.m;
        ServerLevel level = (ServerLevel) m.level();
        // 떠오름: 포물선 (틱마다 자리를 정함 — flyTick)
        r.phase = "fly";
        r.from = m.position();
        r.flyStart = now;
        r.flyTicks = Math.max(6, l.air());
        double d = Math.hypot(r.spot.x - r.from.x, r.spot.z - r.from.z);
        r.peak = 1.2 + d * 0.16;
        m.playAnimation(Monsters.anim(f.k, "leap"), "leap", "0");
        Monsters.play(level, "entity.breeze.jump", m.position(), 1.2, 0.9);
        SkillFx.burst(level, V.up(m.position(), 0.2), VIOLET, 0.8 + f.k.half(), 0.3);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, m.getX(), m.getY() + 0.3, m.getZ(), 12, 0.3, 0.1, 0.3, 0.05);
    }

    /** 덮치는 비행 (틱마다): 포물선 위 자리로 옮김 · 꼬리 빛 → 다 오면 떨어짐 */
    static void flyTick(Monsters.Fight f, Run r, long now) {
        RpgMonster m = f.m;
        ServerLevel level = (ServerLevel) m.level();
        double t = Math.min(1, (now - r.flyStart + 1) / (double) r.flyTicks);
        double x = r.from.x + (r.spot.x - r.from.x) * t;
        double z = r.from.z + (r.spot.z - r.from.z) * t;
        double y = r.from.y + (r.spot.y - r.from.y) * t + r.peak * 4 * t * (1 - t);
        Vec3 before = m.position();
        m.setPos(x, y, z);
        m.setDeltaMovement(Vec3.ZERO);
        m.needsSync = true;
        if ((now & 1) == 0) {
            SkillFx.beam(level, V.up(before, 0.4), V.up(m.position(), 0.4), PINK, Opt.of("half", 0.25, "thick", 0.12, "life", 0.18, "alpha", 0.75));
        }
        if (t >= 1) land(f, r, now);
    }

    /** 떨어짐: 둘레 radius 칸 피해 × mult · 구속 · 분홍 고리 · 짓눌린 소리 */
    static void land(Monsters.Fight f, Run r, long now) {
        Leap l = f.k.traits().leap();
        RpgMonster m = f.m;
        Monsters.Kind k = f.k;
        ServerLevel level = (ServerLevel) m.level();
        f.voidRun = null;
        m.stopAnimation("leap");
        m.holdUntil = now + 6;
        Vec3 at = m.position();
        SkillFx.shock(level, at, PINK, l.radius() / 2.1, 0.45);
        RuneKit.novaRing(level, at, 0.4, l.radius(), PINK, LIGHT, Opt.of("life", 0.4));
        RuneKit.rfx2(level, "jburst", V.up(at, 0.4), Opt.of("size", 0.9 + l.radius() * 0.3, "life", 0.3, "frames", 8.0, "fps", 8 / 0.3, "spin", Kit.rand() * 360,
                "alpha", 0.95), PINK, LIGHT, new int[] {0, 8}, null);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 0.3, at.z, 18, l.radius() * 0.4, 0.15, l.radius() * 0.4, 0.05);
        Monsters.play(level, "entity.magma_cube.squish", at, 0.7, 1);
        Monsters.play(level, k.hitSound(), at, 0.8, 1);
        f.lastKind = "leap";
        f.lastResolved = now;
        boolean any = false;
        for (ServerPlayer p : new ArrayList<>(level.players())) {
            if (Monsters.targetState(p, m) != Monsters.State.OK) continue;
            if (Math.hypot(p.getX() - at.x, p.getZ() - at.z) > l.radius() + 0.3 || Math.abs(p.getY() - at.y) > 1.8) continue;
            any = true;
            if (Monsters.monsterHit(m, k, p, k.attack() * l.mult() * Monsters.weakMult(m)) && l.slowSec() > 0) {
                p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, (int) Math.round(l.slowSec() * 20), l.slowAmp(), false, true));
            }
            Monsters.impactFx(level, m, k, p);
            SkillFx.shake(p, 0.3, 0.3);
        }
        if (any) f.hits++;
        else f.misses++;
        f.nextAttack = Math.max(f.nextAttack, now + 12);
    }

    // ------------------------------------------------------------ 공허 추적자: 균열 이동 + 십자 베기

    static void startBlink(Monsters.Fight f, long now) {
        Blink b = f.k.traits().blink();
        RpgMonster m = f.m;
        ServerLevel level = (ServerLevel) m.level();
        Run r = new Run("blink", f.player, now);
        r.phase = "vanish";
        r.phaseUntil = now + b.vanish();
        f.voidRun = r;
        f.windup = null;
        f.nextTrait = now + b.every();
        int all = b.vanish() + b.open() + b.strike();
        f.nextAttack = Math.max(f.nextAttack, now + all + 12);
        f.nextLunge = Math.max(f.nextLunge, now + all + 20);
        m.holdUntil = now + all + 6;
        m.getNavigation().stop();
        m.playAnimation(Monsters.anim(f.k, "vanish"), "blink", "0");
        Vec3 c = V.up(m.position(), f.k.height() * 0.5);
        rift(level, c, f.k.height() * 0.55, (b.vanish() + 6) / 20.0, 0.4);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 20, 0.3, f.k.height() * 0.3, 0.3, 0.05);
        Monsters.play(level, f.k.windupSound(), c, 0.8, 0.9);
    }

    /** 대상 등 뒤 자리 (서서 들어갈 수 있는 곳 — 막히면 옆 · 앞 · 대상 자리) */
    static Vec3 behind(ServerLevel level, ServerPlayer p, double dist) {
        Vec3 look = Kit.facingOf(p);
        Vec3 side = V.rightOf(look);
        Vec3[] tries = {V.add(p.position(), look, -dist), V.add(p.position(), side, dist), V.add(p.position(), side, -dist), V.add(p.position(), look, dist)};
        for (Vec3 t : tries) {
            Double g = RuneKit.solidGround(level, V.up(t, 1.5), 3.5);
            if (g == null) continue;
            BlockPos feet = BlockPos.containing(t.x, g, t.z);
            if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()) return new Vec3(t.x, g, t.z);
        }
        return p.position();
    }

    static void blinkStep(Monsters.Fight f, Run r, long now) {
        Blink b = f.k.traits().blink();
        RpgMonster m = f.m;
        Monsters.Kind k = f.k;
        ServerLevel level = (ServerLevel) m.level();
        switch (r.phase) {
            case "vanish" -> {
                if (now < r.phaseUntil) return;
                // 균열 속으로: 보이지 않고 맞지 않음 · 대상 등 뒤에 균열이 열림 (경고 원 — 다 차면 나와서 벰)
                m.setInvisible(true);
                PHASED.add(m.getUUID());
                Monsters.setLabel(m, null);
                Vec3 c = V.up(m.position(), k.height() * 0.5);
                SkillFx.burst(level, c, PINK, 0.8 + k.half(), 0.3);
                Monsters.play(level, "entity.enderman.teleport", c, 0.7, 1);
                r.spot = behind(level, r.target, b.behind());
                r.phase = "hidden";
                r.phaseUntil = now + b.open();
                Vec3 rc = V.up(r.spot, k.height() * 0.5);
                rift(level, rc, k.height() * 0.55, (b.open() + b.strike() + 4) / 20.0, 0.5);
                warn(level, r.spot, k.reach() + 0.4, (b.open() + b.strike()) / 20.0, PINK);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, rc.x, rc.y, rc.z, 16, 0.25, k.height() * 0.3, 0.25, 0.04);
                Monsters.play(level, "block.respawn_anchor.charge", r.spot, 1.5, 0.8);
            }
            case "hidden" -> {
                if (now < r.phaseUntil) return;
                // 나옴: 균열 자리로 옮겨 대상을 보고 · 십자 베기 동작 (strike 틱 뒤 판정)
                Vec3 face = new Vec3(r.target.getX(), r.spot.y + k.height() * 0.6, r.target.getZ());
                RuneKit.moveTo(m, r.spot, face);
                face(m, r.target.position());
                m.setInvisible(false);
                PHASED.remove(m.getUUID());
                m.playAnimation(Monsters.anim(k, "appear"), "blink", null);
                r.phase = "strike";
                r.phaseUntil = now + b.strike();
                r.dir = V.flat(V.sub(r.target.position(), m.position()));
                Vec3 c = V.up(m.position(), k.height() * 0.5);
                SkillFx.burst(level, c, VIOLET, 0.9 + k.half(), 0.3);
                Monsters.play(level, "entity.warden.attack_impact", c, 1.4, 0.7);
            }
            case "strike" -> {
                if (now < r.phaseUntil) return;
                f.voidRun = null;
                m.holdUntil = now + 4;
                f.lastKind = "blink";
                f.lastResolved = now;
                ServerPlayer p = r.target;
                Monsters.Gap g = Monsters.gapTo(m, k, p);
                Vec3 aim = r.dir;
                Vec3 at = V.up(V.add(m.position(), aim, k.half() + k.reach() * 0.5), k.height() * 0.5);
                // 십자 베기 그림 (두 줄이 엇갈림)
                SkillFx.arc(level, at, aim, Opt.of("shape", "heavy", "flip", false, "size", 1.6, "life", 0.22, "speed", 1.0, "spin", 45.0, "color", PINK));
                SkillFx.arc(level, at, aim, Opt.of("shape", "heavy", "flip", true, "size", 1.6, "life", 0.22, "speed", 1.0, "spin", -45.0, "color", VIOLET));
                boolean inArc = g.gap() <= k.half() + 0.1 || dotArc(m, p, aim, b.arc());
                if (g.gap() <= k.reach() + Monsters.TOLERANCE + 0.3 && Monsters.heightOk(k, g.dy(), 0.5) && inArc) {
                    f.hits++;
                    if (Monsters.monsterHit(m, k, p, k.attack() * b.mult() * Monsters.weakMult(m)) && b.darkness() > 0) {
                        p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, (int) Math.round(b.darkness() * 20), 0, false, true));
                    }
                    Monsters.impactFx(level, m, k, p);
                    SkillFx.shake(p, 0.35, 0.3);
                    Monsters.play(level, k.hitSound(), p.position(), 0.8, 1);
                } else {
                    f.misses++;
                    Monsters.play(level, "item.spear.attack_miss", at, 0.8, 0.6);
                }
                f.nextAttack = Math.max(f.nextAttack, now + 10);
            }
            default -> {
            }
        }
    }

    static boolean dotArc(RpgMonster m, ServerPlayer p, Vec3 aim, double arcDeg) {
        double dx = p.getX() - m.getX();
        double dz = p.getZ() - m.getZ();
        double d = Math.hypot(dx, dz);
        if (d < 1e-6) return true;
        return (dx * aim.x + dz * aim.z) / d >= Math.cos(Math.toRadians(arcDeg / 2));
    }

    // ------------------------------------------------------------ 공허 해파리: 공허 구체

    static void startOrb(Monsters.Fight f, long now) {
        Orb o = f.k.traits().orb();
        RpgMonster m = f.m;
        ServerLevel level = (ServerLevel) m.level();
        Run r = new Run("orb", f.player, now);
        r.phase = "charge";
        r.phaseUntil = now + o.charge();
        f.voidRun = r;
        f.windup = null;
        f.nextTrait = now + o.every();
        f.nextAttack = Math.max(f.nextAttack, now + o.charge() + 8);
        m.holdUntil = now + o.charge() + 4;
        m.getNavigation().stop();
        face(m, f.player.position());
        m.playAnimation(Monsters.anim(f.k, "cast"));
        Vec3 c = core(m, f.k);
        RuneKit.rfx2(level, "jorb", c, Opt.of("size", 0.5, "life", o.charge() / 20.0, "grow", 0.8, "frames", 6.0, "fps", 12.0, "loop", true, "alpha", 0.9), PINK,
                LIGHT, new int[] {0, 6}, null);
        SkillFx.shock(level, m.position(), PINK, (f.k.half() + 1.2) / 2.1, o.charge() / 20.0);
        Monsters.play(level, "entity.illusioner.cast_spell", c, 1.2, 0.9);
    }

    /** 해파리 몸속 핵 자리 (종 아래 — 크기 배율 포함) */
    static Vec3 core(RpgMonster m, Monsters.Kind k) {
        return V.up(m.position(), k.height() * 0.5);
    }

    static void orbStep(Monsters.Fight f, Run r, long now) {
        if (now < r.phaseUntil) return;
        Orb o = f.k.traits().orb();
        RpgMonster m = f.m;
        ServerLevel level = (ServerLevel) m.level();
        f.voidRun = null;
        Vec3 from = core(m, f.k);
        Vec3 to = V.up(r.target.position(), 1.0);
        Vec3 v = V.scale(V.unit(V.sub(to, from)), o.speed());
        ORBS.add(new OrbShot(level, m, f.k, from, v, r.target, now));
        orbsFired++;
        f.lastKind = "orb";
        f.lastResolved = now;
        Monsters.play(level, "entity.shulker.shoot", from, 0.7, 1);
        SkillFx.burst(level, from, PINK, 0.9, 0.25);
    }

    /** 날아가는 구체 하나 */
    static final class OrbShot {
        final ServerLevel level;
        final RpgMonster owner;
        final Monsters.Kind k;
        Vec3 pos;
        Vec3 vel;
        final ServerPlayer target;
        final long born;

        OrbShot(ServerLevel level, RpgMonster owner, Monsters.Kind k, Vec3 pos, Vec3 vel, ServerPlayer target, long born) {
            this.level = level;
            this.owner = owner;
            this.k = k;
            this.pos = pos;
            this.vel = vel;
            this.target = target;
            this.born = born;
        }
    }

    /** 구체 (틱마다): 조금씩 꺾여 대상을 따라감 · 사람 · 벽에 닿거나 수명이 다하면 터짐 */
    static void orbTick(long now) {
        if (ORBS.isEmpty()) return;
        for (Iterator<OrbShot> it = ORBS.iterator(); it.hasNext(); ) {
            OrbShot s = it.next();
            Orb o = s.k.traits().orb();
            boolean alive = !s.target.isRemoved() && s.target.level() == s.level && !s.target.isDeadOrDying();
            if (alive && o.homing() > 0) {
                Vec3 want = V.scale(V.unit(V.sub(V.up(s.target.position(), 1.0), s.pos)), o.speed());
                s.vel = V.scale(V.unit(V.add(s.vel, V.sub(want, s.vel), o.homing())), o.speed());
            }
            Vec3 next = V.add(s.pos, s.vel, 1);
            boolean burst = now - s.born >= o.life();
            BlockPos bp = BlockPos.containing(next);
            if (!s.level.getBlockState(bp).getCollisionShape(s.level, bp).isEmpty()) burst = true;
            for (ServerPlayer p : s.level.players()) {
                if (Monsters.targetState(p, s.owner) != Monsters.State.OK) continue;
                if (V.len(V.sub(V.up(p.position(), 0.9), next)) <= 0.95) burst = true;
            }
            s.pos = next;
            if ((now & 1) == 0) {
                Vec3 vel = V.scale(s.vel, 20);
                RuneKit.rfx(s.level, "hole", s.pos, Opt.of("size", 0.42, "life", 0.14, "cell", 0.0, "spinRate", 480.0, "vel", vel, "alpha", 0.95));
                RuneKit.rfx2(s.level, "jorb", s.pos, Opt.of("size", 0.62, "life", 0.14, "frames", 6.0, "fps", 12.0, "loop", true, "vel", vel, "alpha", 0.85), PINK,
                        LIGHT, new int[] {0, 6}, null);
            }
            if ((now % 4) == 0) s.level.sendParticles(ParticleTypes.REVERSE_PORTAL, s.pos.x, s.pos.y, s.pos.z, 2, 0.08, 0.08, 0.08, 0.01);
            if (burst) {
                it.remove();
                orbBurst(s, o);
            }
        }
    }

    /** 구체가 터짐: 둘레 radius 칸 피해 (주인이 살아 있을 때) · 분홍 고리 · 검은 구멍 · 소리 */
    static void orbBurst(OrbShot s, Orb o) {
        orbsBurst++;
        ServerLevel level = s.level;
        Vec3 at = s.pos;
        RuneKit.novaRing(level, at, 0.4, o.radius(), PINK, LIGHT, Opt.of("dir", V.UP, "life", 0.4));
        RuneKit.rfx(level, "hole", at, Opt.of("size", o.radius() * 0.7, "life", 0.35, "cell", 0.0, "grow", 0.5, "fade", 0.7, "alpha", 0.9));
        RuneKit.rfx2(level, "jburst", at, Opt.of("size", o.radius() * 0.8, "life", 0.3, "frames", 8.0, "fps", 8 / 0.3, "spin", Kit.rand() * 360, "alpha", 0.95), PINK,
                LIGHT, new int[] {0, 8}, null);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 26, o.radius() * 0.3, o.radius() * 0.3, o.radius() * 0.3, 0.08);
        Monsters.play(level, "entity.shulker_bullet.hit", at, 0.7, 1);
        Monsters.play(level, "entity.generic.explode", at, 1.6, 0.35);
        if (s.owner.isRemoved() || Kit.hasNoHealth(s.owner)) return;
        for (ServerPlayer p : new ArrayList<>(level.players())) {
            if (Monsters.targetState(p, s.owner) != Monsters.State.OK) continue;
            if (V.len(V.sub(V.up(p.position(), 0.9), at)) > o.radius() + 0.4) continue;
            Monsters.monsterHit(s.owner, s.k, p, o.attack() * Monsters.weakMult(s.owner));
            SkillFx.shake(p, 0.25, 0.25);
        }
    }

    // ------------------------------------------------------------ 공허 굴착자: 땅굴 습격

    static void startTunnel(Monsters.Fight f, long now) {
        Tunnel t = f.k.traits().tunnel();
        RpgMonster m = f.m;
        ServerLevel level = (ServerLevel) m.level();
        Run r = new Run("tunnel", f.player, now);
        r.phase = "dive";
        r.phaseUntil = now + t.dive();
        f.voidRun = r;
        f.windup = null;
        f.nextTrait = now + t.every();
        f.nextAttack = Math.max(f.nextAttack, now + t.dive() + t.max() + t.warn() + 14);
        f.nextLunge = Math.max(f.nextLunge, f.nextAttack + 10);
        m.holdUntil = now + t.dive() + t.max() + t.warn() + 10;
        m.getNavigation().stop();
        face(m, f.player.position());
        m.playAnimation(Monsters.anim(f.k, "burrow"), "tunnel", "0");
        debris(level, m.position(), 1.4 + f.k.half());
        Monsters.play(level, "entity.warden.dig", m.position(), 0.9, 1);
        Monsters.play(level, f.k.windupSound(), m.position(), 0.7, 0.8);
    }

    /** 흙 · 돌 조각이 튐 (발밑 블록) + 보랏빛 */
    static void debris(ServerLevel level, Vec3 at, double size) {
        BlockState below = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
        if (below.isAir()) below = Blocks.STONE.defaultBlockState();
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), at.x, at.y + 0.2, at.z, (int) (14 * size), 0.4 * size, 0.2, 0.4 * size, 0.15);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 0.2, at.z, (int) (8 * size), 0.3 * size, 0.1, 0.3 * size, 0.03);
    }

    static void tunnelStep(Monsters.Fight f, Run r, long now) {
        Tunnel t = f.k.traits().tunnel();
        RpgMonster m = f.m;
        Monsters.Kind k = f.k;
        ServerLevel level = (ServerLevel) m.level();
        switch (r.phase) {
            case "dive" -> {
                if (now < r.phaseUntil) return;
                // 땅속: 낮은 몸 · 맞지 않음 · 이름표 없음 → underTick 이 다가감
                m.setProp(RpgMonster.BURIED, true);
                PHASED.add(m.getUUID());
                Monsters.setLabel(m, null);
                debris(level, m.position(), 1.2 + k.half());
                r.phase = "under";
                r.travelled = 0;
                r.phaseUntil = now + t.max();
            }
            case "warn" -> {
                if (now < r.phaseUntil) return;
                erupt(f, r, now);
            }
            default -> {
            }
        }
    }

    /** 땅속 (틱마다): 대상 쪽으로 speed 칸 (땅을 따라) · 금 간 자국 · 흙 → 가까우면 (또는 max 틱) 발밑 경고 */
    static void underTick(Monsters.Fight f, Run r, long now) {
        Tunnel t = f.k.traits().tunnel();
        RpgMonster m = f.m;
        Monsters.Kind k = f.k;
        ServerLevel level = (ServerLevel) m.level();
        Vec3 tp = r.target.position();
        double dx = tp.x - m.getX();
        double dz = tp.z - m.getZ();
        double d = Math.hypot(dx, dz);
        r.travelled++;
        if (d <= 0.9 || now >= r.phaseUntil || r.target.isRemoved()) {
            // 경고: 대상 발밑 (지금 자리) 에 원 · 빛나는 금 · 울림
            r.phase = "warn";
            r.phaseUntil = now + t.warn();
            Vec3 spot = new Vec3(tp.x, ground(level, tp, tp.y), tp.z);
            r.spot = spot;
            m.setPos(spot.x, spot.y, spot.z);
            m.needsSync = true;
            warn(level, spot, t.radius(), t.warn() / 20.0, PINK);
            RuneKit.glowCrack(level, spot, t.radius() * 0.9, VIOLET, PINK, Opt.of("life", t.warn() / 20.0 + 0.6, "grow", 0.3, "fade", 0.4));
            Monsters.play(level, "entity.warden.dig", spot, 0.6, 1.2);
            RuneKit.shakeAround(level, spot, 7, 0.2, t.warn() / 20.0);
            return;
        }
        double s = Math.min(t.speed(), d);
        double nx = m.getX() + dx / d * s;
        double nz = m.getZ() + dz / d * s;
        double ny = ground(level, new Vec3(nx, m.getY(), nz), m.getY());
        face(m, tp);
        m.setPos(nx, ny, nz);
        m.setDeltaMovement(Vec3.ZERO);
        m.needsSync = true;
        if (r.travelled % 2 == 0) {
            Vec3 at = m.position();
            RuneKit.glowCrack(level, at, 0.7 + k.half() * 0.3, DEEP, VIOLET, Opt.of("life", 1.6, "fade", 0.6, "alpha", 0.9, "v", (double) (r.travelled / 2 % 2)));
            BlockState below = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
            if (!below.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), at.x, at.y + 0.1, at.z, 6, 0.35, 0.05, 0.35, 0.1);
        }
        if (r.travelled % 8 == 0) Monsters.play(level, "block.gravel.break", m.position(), 0.5, 0.8);
    }

    /** 솟구침: 둘레 radius 칸 × mult · 띄움 · 흙 · 수정 조각 · 고리 */
    static void erupt(Monsters.Fight f, Run r, long now) {
        Tunnel t = f.k.traits().tunnel();
        RpgMonster m = f.m;
        Monsters.Kind k = f.k;
        ServerLevel level = (ServerLevel) m.level();
        f.voidRun = null;
        surface(m, k, true);
        m.holdUntil = now + 14;
        Vec3 at = m.position();
        f.lastKind = "tunnel";
        f.lastResolved = now;
        boolean any = false;
        for (ServerPlayer p : new ArrayList<>(level.players())) {
            if (Monsters.targetState(p, m) != Monsters.State.OK) continue;
            if (Math.hypot(p.getX() - at.x, p.getZ() - at.z) > t.radius() + 0.3 || Math.abs(p.getY() - at.y) > 2) continue;
            any = true;
            Monsters.monsterHit(m, k, p, k.attack() * t.mult() * Monsters.weakMult(m));
            Kit.launch(p, (p.getX() - at.x) * 0.12, t.lift(), (p.getZ() - at.z) * 0.12);
            SkillFx.shake(p, 0.45, 0.4);
        }
        if (any) f.hits++;
        else f.misses++;
        f.nextAttack = Math.max(f.nextAttack, now + 16);
    }

    /** 땅 위로 나옴 (솟구침 연출 · 이름표 · 맞음) — big = 솟구치는 연출 (아니면 조용히) */
    static void surface(RpgMonster m, Monsters.Kind k, boolean big) {
        m.setProp(RpgMonster.BURIED, false);
        PHASED.remove(m.getUUID());
        m.playAnimation(Monsters.anim(k, "emerge"), "tunnel", null);
        if (!(m.level() instanceof ServerLevel level)) return;
        Vec3 at = m.position();
        debris(level, at, big ? 2.4 : 1.2);
        if (!big) return;
        SkillFx.shock(level, at, PINK, (k.traits().tunnel().radius() + 0.4) / 2.1, 0.5);
        RuneKit.novaRing(level, at, 0.5, k.traits().tunnel().radius() + 0.6, PINK, LIGHT, Opt.of("life", 0.45));
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + Kit.rand() * 0.5;
            Vec3 p = new Vec3(at.x + Math.cos(a) * 1.3, at.y, at.z + Math.sin(a) * 1.3);
            RuneKit.rfx2(level, "crystal", p, Opt.of("size", 0.6 + Kit.rand() * 0.35, "life", 0.8, "grow", 0.25, "fade", 0.4, "cell", Math.floor(Kit.rand() * 3),
                    "alpha", 0.95), VIOLET, LIGHT, new int[] {0, RuneKit.CRYSTAL_CORE}, null);
        }
        Monsters.play(level, "entity.warden.emerge", at, 1.3, 0.8);
        Monsters.play(level, "block.amethyst_cluster.break", at, 0.6, 1);
        Monsters.play(level, k.hitSound(), at, 0.7, 1);
    }

    // ------------------------------------------------------------ 공허 거수: 수정 가시 줄

    static void startSpikes(Monsters.Fight f, long now) {
        Spikes s = f.k.traits().spikes();
        RpgMonster m = f.m;
        Monsters.Kind k = f.k;
        ServerLevel level = (ServerLevel) m.level();
        Run r = new Run("spikes", f.player, now);
        r.phase = "prep";
        r.phaseUntil = now + s.prep();
        r.dir = V.flat(V.sub(f.player.position(), m.position()));
        r.from = m.position();
        f.voidRun = r;
        f.windup = null;
        f.nextTrait = now + s.every();
        int all = s.prep() + s.count() * s.gap();
        f.nextAttack = Math.max(f.nextAttack, now + all + 10);
        f.nextLunge = Math.max(f.nextLunge, now + all + 20);
        m.holdUntil = now + all + 6;
        m.getNavigation().stop();
        face(m, f.player.position());
        m.playAnimation(Monsters.anim(k, "slam"));
        Monsters.play(level, k.windupSound(), m.position(), 0.7, 1.2);
        // 가시 자리마다 경고 원 (솟을 때까지)
        for (int i = 0; i < s.count(); i++) {
            Vec3 c = spikeAt(level, r, k, s, i);
            warn(level, c, s.radius(), (s.prep() + i * s.gap()) / 20.0, VIOLET);
        }
    }

    /** i 번째 가시 자리 (몸 앞에서 step 칸 간격 — 땅 높이) */
    static Vec3 spikeAt(ServerLevel level, Run r, Monsters.Kind k, Spikes s, int i) {
        Vec3 p = V.add(r.from, r.dir, k.half() + 1.0 + i * s.step());
        return new Vec3(p.x, ground(level, p, r.from.y), p.z);
    }

    static void spikesStep(Monsters.Fight f, Run r, long now) {
        if (!r.phase.equals("prep") || now < r.phaseUntil) return;
        Spikes s = f.k.traits().spikes();
        RpgMonster m = f.m;
        Monsters.Kind k = f.k;
        ServerLevel level = (ServerLevel) m.level();
        r.phase = "spikes";
        // 내려찍음: 앞에 충격 · 흔들림
        Vec3 front = V.add(m.position(), r.dir, k.half() + 0.4);
        SkillFx.shock(level, front, VIOLET, 1.4, 0.45);
        RuneKit.shakeAround(level, front, 10, 0.35, 0.4);
        Monsters.play(level, k.hitSound(), front, 0.8, 1);
        lastSpikes.clear();
        Set<UUID> hit = new HashSet<>();
        for (int i = 0; i < s.count(); i++) {
            final int idx = i;
            Tasks.later(1 + i * s.gap(), () -> {
                spike(level, f, r, s, idx, hit);
                if (idx == s.count() - 1 && f.voidRun == r) {
                    f.voidRun = null;
                    m.holdUntil = Tasks.now() + 4;
                }
            });
        }
        f.lastKind = "spikes";
        f.lastResolved = now;
    }

    /** 가시 하나: 자수정 블록 표시가 땅에서 솟았다가 가라앉음 · 수정 그림 · 조각 · 소리 · 둘레 피해 (같은 사람 한 번) */
    static void spike(ServerLevel level, Monsters.Fight f, Run r, Spikes s, int i, Set<UUID> hit) {
        RpgMonster m = f.m;
        Monsters.Kind k = f.k;
        Vec3 c = spikeAt(level, r, k, s, i);
        lastSpikes.add(c);
        double sx = 1.15 + (i % 2) * 0.25;
        double sy = 2.2 + (i % 3) * 0.35;
        Quaternionf rot = new Quaternionf().rotateY((float) (Kit.rand() * Math.PI));
        // 블록의 아래 가운데 (0.5, 0, 0.5) 가 c 에 오게: 옮김 = −회전 · (크기 × (0.5, 0, 0.5))
        org.joml.Vector3f off = new org.joml.Vector3f((float) (sx * 0.5), 0F, (float) (sx * 0.5));
        rot.transform(off);
        Vec3 down = new Vec3(-off.x, -sy - 0.1, -off.z);
        Vec3 up = new Vec3(-off.x, -0.25, -off.z);
        Display.BlockDisplay d = FxDisplays.block(level, c, Blocks.AMETHYST_CLUSTER.defaultBlockState(), FxDisplays.of(down, rot, sx, sy, sx), s.linger() + 20,
                new Brightness(15, 15));
        if (d != null) {
            Tasks.later(2, () -> FxDisplays.move(d, FxDisplays.of(up, rot, sx, sy, sx), 3));
            Tasks.later(2 + s.linger(), () -> FxDisplays.move(d, FxDisplays.of(down, rot, sx, sy, sx), 8));
        }
        RuneKit.rfx2(level, "crystal", c, Opt.of("size", 1.1 + (i % 2) * 0.2, "life", 0.8, "grow", 0.2, "fade", 0.45, "cell", (double) (i % 3), "alpha", 0.95), VIOLET,
                LIGHT, new int[] {0, RuneKit.CRYSTAL_CORE}, null);
        SkillFx.shock(level, c, PINK, (s.radius() + 0.2) / 2.1, 0.35);
        SkillFx.sparks(level, V.up(c, 0.6), LIGHT, 6, 3.5);
        BlockState below = level.getBlockState(BlockPos.containing(c.x, c.y - 0.5, c.z));
        if (!below.isAir()) level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), c.x, c.y + 0.2, c.z, 10, 0.4, 0.1, 0.4, 0.12);
        Monsters.play(level, "block.amethyst_cluster.break", c, 0.6 + Kit.rand() * 0.3, 1);
        if (i % 2 == 0) Monsters.play(level, "block.amethyst_block.resonate", c, 0.7, 0.8);
        if (m.isRemoved() || Kit.hasNoHealth(m)) return;
        for (ServerPlayer p : new ArrayList<>(level.players())) {
            if (hit.contains(p.getUUID()) || Monsters.targetState(p, m) != Monsters.State.OK) continue;
            if (Math.hypot(p.getX() - c.x, p.getZ() - c.z) > s.radius() + 0.3 || Math.abs(p.getY() - c.y) > 2) continue;
            hit.add(p.getUUID());
            f.hits++;
            Monsters.monsterHit(m, k, p, k.attack() * s.mult() * Monsters.weakMult(m));
            Kit.launch(p, (p.getX() - c.x) * 0.15, s.lift(), (p.getZ() - c.z) * 0.15);
            SkillFx.shake(p, 0.4, 0.35);
        }
    }

    // ------------------------------------------------------------ 등록

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            try {
                orbTick(Tasks.now());
            } catch (RuntimeException e) {
                Mmorpg.LOG.warn("[mmorpg] void orbs", e);
                ORBS.clear();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> clearState());
    }

    /** 시험용: 구체 · 숨은 상태 · 기록을 지움 */
    public static void clearState() {
        ORBS.clear();
        PHASED.clear();
        lastSpikes.clear();
        orbsFired = 0;
        orbsBurst = 0;
    }
}
