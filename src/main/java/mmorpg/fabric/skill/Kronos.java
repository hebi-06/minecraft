package mmorpg.fabric.skill;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import mmorpg.core.Obj;
import mmorpg.core.Weapon;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 크로노스의 낫 (초월 낫 — 12단계, 자바 전용): 시간 가르기 · 종말의 시계. 수치는 SKILLS.scythe, 색은 시간의 푸른빛 · 흰 심지 + 금빛 (gold).
 * 그림: rpg:rfx_clock (시계판 · 시침 · 분침 · 톱니 — 빛 · 심지), rpg:rfx_relicspin 칸 2 (크로노스의 낫 모델 — 하늘에서 내려오는 거대한 낫), 초승달 참격.
 * 시간이 멈춘 적: 움직이지 못함 (구속 X · 나약함 · 매 틱 속도 0), 머리 위에 거꾸로 도는 작은 시계. 보스는 움직임을 막지 않음 (피해만)
 */
final class Kronos {
    private Kronos() {}

    static final int RELIC_SPIN_CELL = 2;
    /** rfx_clock 칸: 시계판 빛 · 심지, 시침, 분침, 톱니 */
    static final int DIAL = 0;
    static final int HOUR = 2;
    static final int MINUTE = 4;
    static final int GEAR = 6;

    static void register() {
        Transcend.register("kronos_rend", Kronos::rend);
        Transcend.register("kronos_clock", Kronos::clock);
    }

    /** 시계 그림 한 겹 (빛 + 심지): 판 (dir 쪽을 봄) 위 at, 크기 size, 처음 각 spin, 초당 rate 도 */
    static void clockPart(ServerLevel level, int cell, Vec3 at, Vec3 dir, double size, double spin, double rate, double life, double[] glow, double[] core,
            double alpha) {
        RuneKit.rfx2(level, "clock", at, Opt.of("dir", dir, "size", size, "life", life, "spin", spin, "spinRate", rate, "fade", 0.3, "alpha", alpha), glow, core,
                new int[] {cell, cell + 1}, null);
    }

    /** 시간이 멈춤: ticks 동안 움직이지 못함 (보스 · 플레이어는 느리게만) + 머리 위 거꾸로 도는 시계 */
    static void stopTime(ServerPlayer sp, LivingEntity e, int ticks, double[] glow, double[] core, double[] gold, BooleanSupplier ok) {
        ServerLevel level = sp.level();
        boolean heavy = Kit.hasFamily(e, "rpg_boss") || Kit.hasFamily(e, "rpg_boss_prop") || e instanceof Player;
        RuneKit.effect(e, "slowness", ticks, heavy ? 1 : 10);
        RuneKit.effect(e, "weakness", ticks, heavy ? 0 : 4);
        Vec3 eye = sp.getEyePosition();
        Vec3 over = RuneKit.overHead(e, 0.6);
        Vec3 toEye = V.unit(V.sub(eye, over));
        double near = HeroKit.near(eye, over, 0.5);
        clockPart(level, DIAL, over, toEye, 0.75 * near, 0, 0, ticks / 20.0, glow, core, 0.9);
        clockPart(level, MINUTE, V.add(over, toEye, 0.02), toEye, 0.75 * near, 0, -720, ticks / 20.0, gold, core, 1.0);
        clockPart(level, HOUR, V.add(over, toEye, 0.03), toEye, 0.75 * near, 90, -120, ticks / 20.0, gold, core, 1.0);
        if (heavy) return;
        Kit.ticks(t -> {
            if (!ok.getAsBoolean() || e.isRemoved() || !e.isAlive() || t >= ticks) return false;
            e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
            Kit.syncVelocity(e);
            return true;
        });
    }

    // ------------------------------------------------------------ 스킬 1 시간 가르기

    /**
     * 시간 가르기: windup 틱 동안 등 뒤에 거대한 낫의 그림자 (모델 그림) 가 솟음 → 앞 cone° · range 칸을 크게 가름 (mult — 커다란 초승달 참격 셋, 금빛 기운) →
     * 벤 적의 시간이 stop 틱 동안 멈춤 (시계) → 풀리는 순간 벤 자국이 한꺼번에 터짐 (afterMult). 대상이 없어도 씀
     */
    static boolean rend(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = HeroKit.colors(k);
        double[] glow = cs[0];
        double[] core = cs[1];
        double[] gold = HeroKit.rgbOr(k, "gold", new double[] {1, 0.82, 0.4});
        Vec3 f = Kit.facingOf(sp);
        Vec3 pos = sp.position();
        // 등 뒤에 솟는 거대한 낫 (돌며 앞으로 넘어옴)
        Vec3 back = V.up(V.add(pos, f, -1.2), 2.6);
        RuneKit.rfx(level, "relicspin", back, Opt.of("size", 4.2, "life", 0.45, "spin", 120.0, "spinRate", -520.0, "cell", (double) RELIC_SPIN_CELL, "alpha", 0.9,
                "fade", 0.4));
        clockPart(level, GEAR, V.up(pos, 0.08), V.UP, 2.4, 0, 180, 0.6, glow, core, 0.7);
        SkillFx.play(level, "block.bell.hit", pos, 0.6, 0.9);
        SkillFx.play(level, "item.trident.riptide_3", pos, 0.7, 0.6);
        HeroKit.at((int) k.num("windup"), () -> {
            if (ok.getAsBoolean()) sweep(sp, w, k, ok, glow, core, gold);
        });
        return true;
    }

    private static void sweep(ServerPlayer sp, Weapon w, Obj k, BooleanSupplier ok, double[] glow, double[] core, double[] gold) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 pos = sp.position();
        Vec3 eye = sp.getEyePosition();
        double range = k.num("range");
        // 앞을 가르는 거대한 초승달 셋 (가운데 · 양옆), 바닥을 쓸고 가는 금빛 물결
        for (int i = -1; i <= 1; i++) {
            Vec3 d = V.turnY(f, i * 45);
            Vec3 at = V.add(V.up(eye, -0.5), d, 2.6 + Math.abs(i) * 0.3);
            SkillFx.arc(level, at, d, Opt.of("shape", "heavy", "flip", i > 0, "size", 3.4 - Math.abs(i) * 0.6, "life", 0.3, "speed", 4.0, "spin", -10.0 * i,
                    "color", glow, "aura", gold));
        }
        RuneKit.rfx2(level, "wave", V.add(V.up(pos, 1.0), f, 2.2), Opt.of("dir", f, "size", range * 1.1, "life", 0.35, "speed", 10.0, "fade", 0.6, "alpha", 0.8),
                glow, core, new int[] {0, 1}, null);
        SkillFx.play(level, "entity.player.attack.sweep", pos, 0.5, 1.0);
        SkillFx.play(level, "mob.warden.sonic_charge", pos, 1.8, 0.4);
        SkillFx.shake(sp, 0.15, 0.15);
        double half = Math.toRadians(k.num("cone") / 2);
        List<LivingEntity> in = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, pos, range, 3)) if (Kit.bodyInCone(e, pos, f, half)) in.add(e);
        List<LivingEntity> targets = Kit.visibleFrom(sp, eye, in);
        int stop = (int) k.num("stop");
        long now = Tasks.now();
        for (LivingEntity e : targets) {
            Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now), ok, () -> {
                if (Kit.skillHit(sp, e, w, k.num("mult")) <= 0) return;
                Vec3 c = HeroKit.body(e, eye);
                Vec3 v = V.unit(V.sub(c, sp.getEyePosition()));
                double near = HeroKit.near(sp.getEyePosition(), c, 0.5);
                Vec3 front = V.add(c, v, -0.4);
                SkillFx.cutMark(level, front, v, 20, stop / 20.0, glow, core, 1.3 * near);
                SkillFx.sparks(level, c, core, 5, 4);
                stopTime(sp, e, stop, glow, core, gold, ok);
                // 시간이 풀리며 벤 자국이 터짐
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, Tasks.now() + stop), ok, () -> {
                    if (Kit.skillHit(sp, e, w, k.num("afterMult")) <= 0) return;
                    Vec3 c2 = HeroKit.body(e, c);
                    Vec3 v2 = V.unit(V.sub(c2, sp.getEyePosition()));
                    double n2 = HeroKit.near(sp.getEyePosition(), c2, 0.45);
                    Vec3 f2 = V.add(c2, v2, -0.4);
                    SkillFx.cutMark(level, f2, v2, 50, 0.06, glow, core, 1.5 * n2);
                    SkillFx.cutMark(level, f2, v2, -40, 0.06, glow, core, 1.5 * n2);
                    clockPart(level, DIAL, f2, V.scale(v2, -1), 1.3 * n2, 0, 0, 0.3, gold, core, 0.9);
                    RuneKit.rfx2(level, "flare", f2, Opt.of("size", 1.4 * n2, "life", 0.3, "grow", 0.6, "fade", 0.6, "spin", Kit.rand() * 90), glow, core,
                            new int[] {0, 1}, null);
                    SkillFx.burst(level, f2, glow, 1.1 * n2, 0.3);
                    SkillFx.play(level, "random.glass", c2, 0.9, 0.6);
                    SkillFx.play(level, "block.bell.hit", c2, 1.6, 0.5);
                });
            });
        }
    }

    // ------------------------------------------------------------ 스킬 2 종말의 시계

    /**
     * 종말의 시계: 쓴 자리 발밑에 반지름 radius 칸 시계판 (금빛 톱니 · 시곗바늘) → tick 틱마다 바늘이 한 칸 (30°) 씩 12번 — gap 틱마다 판 안의 적을 벰
     * (hits 번 mult, 구속 slowAmp+1) → 자정: 하늘에서 거대한 낫 (모델 그림) 이 내려와 판 전체를 가름 (afterMult). 대상이 없어도 씀
     */
    static boolean clock(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = HeroKit.colors(k);
        double[] glow = cs[0];
        double[] core = cs[1];
        double[] gold = HeroKit.rgbOr(k, "gold", new double[] {1, 0.82, 0.4});
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double r = k.num("radius");
        int tick = (int) k.num("tick");
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int total = Math.max(12 * tick, hits * gap);
        double secs = total / 20.0 + 0.4;
        Vec3 ground = V.up(c, 0.07);
        // 시계판 (빛 + 심지) · 바깥 금빛 톱니 (천천히 거꾸로) · 시침 (천천히) — 바늘 (분침) 은 틱마다 새로 그림
        clockPart(level, DIAL, ground, V.UP, r * 1.05, 0, 0, secs + 0.6, glow, core, 0.9);
        clockPart(level, GEAR, V.up(ground, -0.01), V.UP, r * 1.2, 0, -25, secs + 0.6, gold, core, 0.55);
        clockPart(level, HOUR, V.up(ground, 0.02), V.UP, r * 1.05, 0, 30.0 / (total / 20.0), secs, gold, core, 1.0);
        RuneKit.zonePulse(level, c, glow, r, 0.6);
        SkillFx.play(level, "block.bell.hit", c, 0.5, 1.0);
        SkillFx.play(level, "beacon.activate", c, 0.7, 0.8);
        SkillFx.shake(sp, 0.1, 0.2);
        // 바늘: tick 틱마다 30° (째깍 소리 · 판 끝의 빛)
        for (int i = 0; i < 12; i++) {
            final int ii = i;
            Tasks.later(i * tick, () -> {
                if (!ok.getAsBoolean()) return;
                clockPart(level, MINUTE, V.up(ground, 0.03), V.UP, r * 1.05, ii * 30.0, 0, tick / 20.0 + 0.05, gold, core, 1.0);
                double a = Math.toRadians(ii * 30.0);
                Vec3 tip = V.add(ground, new Vec3(Math.sin(a), 0, -Math.cos(a)), r * 0.85);
                SkillFx.burst(level, V.up(tip, 0.2), glow, 0.6, 0.25);
                SkillFx.play(level, "random.click", c, 0.6 + ii * 0.05, 0.9);
                if (ii % 3 == 0) SkillFx.play(level, "note.chime", c, 0.5 + ii * 0.08, 0.6);
            });
        }
        // 판 안의 적을 벰 (hits 번) · 그동안 느려짐
        Map<UUID, LivingEntity> inside = new HashMap<>();
        for (int h = 0; h < hits; h++) {
            Tasks.later(h * gap, () -> {
                if (!ok.getAsBoolean()) return;
                long now = Tasks.now();
                for (LivingEntity e : Kit.around(sp, c, r, 3)) {
                    inside.put(e.getUUID(), e);
                    RuneKit.effect(e, "slowness", gap + 6, (int) k.num("slowAmp"));
                    Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now), ok, () -> {
                        if (Kit.skillHit(sp, e, w, k.num("mult")) <= 0) return;
                        Vec3 b = HeroKit.body(e, c);
                        Vec3 v = V.unit(V.sub(b, sp.getEyePosition()));
                        double near = HeroKit.near(sp.getEyePosition(), b, 0.5);
                        SkillFx.arc(level, V.add(b, v, -0.4), v, Opt.of("shape", "thin", "flip", Kit.rand() < 0.5, "size", 1.0 * near, "life", 0.18, "speed", 0.0,
                                "spin", Kit.rand() * 60 - 30, "color", glow));
                        SkillFx.sparks(level, b, gold, 3, 3);
                    });
                }
            });
        }
        // 자정: 거대한 낫이 하늘에서 내려와 판을 가름 — 쓴 사람 앞 (판 반지름의 절반) 으로 내려옴 (1인칭에서 보이고 3인칭 화면을 덮지 않게)
        int fall = (int) k.num("fall");
        Vec3 drop = V.add(c, f, r * 0.5);
        Tasks.later(total, () -> {
            if (!ok.getAsBoolean()) return;
            double room = Math.max(4, Math.min(12, Kit.skyRoom(level, V.up(drop, 0.5), 12, true)));
            Vec3 top = V.up(drop, room);
            RuneKit.rfx(level, "relicspin", top, Opt.of("size", r * 1.1, "life", fall / 20.0 + 0.15, "spin", 0.0, "spinRate", 720.0, "cell", (double) RELIC_SPIN_CELL,
                    "alpha", 0.95, "fade", 0.2, "vel", new Vec3(0, -(room - 2.2) / (fall / 20.0), 0)));
            SkillFx.beam(level, top, V.up(drop, 1), gold, Opt.of("half", 0.5, "thick", 0.4, "life", fall / 20.0 + 0.2, "gap", 1.0));
            SkillFx.play(level, "block.bell.hit", c, 0.4, 1.0);
            SkillFx.play(level, "mob.warden.sonic_charge", c, 0.8, 0.7);
        });
        Tasks.later(total + fall, () -> {
            if (!ok.getAsBoolean()) return;
            // 판을 가르는 초승달 넷 (앞쪽 반원으로 퍼짐 — 뒤로 퍼지면 3인칭 카메라를 금빛 기운이 덮음) + 시계판이 깨짐 (금 · 빛 고리)
            for (int j = 0; j < 4; j++) {
                Vec3 d = V.turnY(f, -66 + j * 44);
                SkillFx.arc(level, V.add(V.up(c, 1.0), d, r * 0.45), d, Opt.of("shape", "heavy", "flip", j % 2 == 1, "size", r * 0.9, "life", 0.32, "speed", 3.0,
                        "spin", 0.0, "color", glow, "aura", gold));
            }
            RuneKit.glowCrack(level, c, r * 1.1, glow, core, Opt.of("life", 1.2, "v", 1.0));
            RuneKit.rfx2(level, "flare", V.up(drop, 1.2), Opt.of("size", 2.6, "life", 0.35, "grow", 0.5, "fade", 0.6, "spin", 45.0), glow, core, new int[] {0, 1},
                    null);
            RuneKit.novaRing(level, c, r * 0.5, 1.4, glow, core, Opt.of("life", 0.5));
            clockPart(level, DIAL, V.up(c, 0.1), V.UP, r * 1.05, 0, 0, 0.35, gold, core, 1.0);
            SkillFx.play(level, "mob.warden.sonic_boom", c, 1.2, 0.8);
            SkillFx.play(level, "random.glass", c, 0.6, 1.0);
            SkillFx.shake(sp, 0.3, 0.3);
            long now = Tasks.now();
            for (LivingEntity e : Kit.around(sp, c, r, 3)) {
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now), ok, () -> {
                    if (Kit.skillHit(sp, e, w, k.num("afterMult")) <= 0) return;
                    Vec3 b = HeroKit.body(e, c);
                    Vec3 v = V.unit(V.sub(b, sp.getEyePosition()));
                    double near = HeroKit.near(sp.getEyePosition(), b, 0.45);
                    SkillFx.cutMark(level, V.add(b, v, -0.4), v, 70, 0.05, glow, core, 1.6 * near);
                    SkillFx.burst(level, b, glow, 1.2 * near, 0.3);
                });
            }
        });
        return true;
    }
}
