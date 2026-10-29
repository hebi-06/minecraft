package mmorpg.fabric.skill;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import mmorpg.core.Obj;
import mmorpg.core.Weapon;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 발뭉 (영웅 칼) — 용살 검기 (10단계, 자바 전용). 수치는 HERO_SKILLS.sword, 색은 디자인의 빛나는 칼날 (하늘빛) · 손잡이 (금빛).
 * windup 틱 동안 칼에 푸른 빛이 모임 → 휘두르면 거대한 푸른 초승달 검기가 보는 쪽 (위아래는 15° 넘게 볼 때만, pitch 도까지) 으로 틱마다 speed 칸씩
 * range 칸 날아감 (벽에서 멈춤): 지나가며 검기 반폭 width 칸 안의 적을 한 번씩 벰 (mult, 조금 밀침 — 몸에 푸른 검흔이 남음) → 벤 적마다 delay 틱 뒤
 * 검흔이 X 로 터짐 (afterMult — 적을 따라감).
 * 대상이 없어도 씀 (재사용 대기). 쓰러지거나 차원을 옮기면 남은 것은 멈춤
 */
final class Balmung {
    private Balmung() {}

    static final double[] GOLD = {1.0, 0.8, 0.35};

    static void register() {
        Transcend.register("balmung_wave", Balmung::wave);
    }

    static boolean wave(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = HeroKit.colors(k);
        double[] glow = cs[0];
        double[] core = cs[1];
        // 모음: 칼끝 쪽 (손 앞) 에 빛이 모이고 발밑 고리
        Vec3 f0 = Kit.facingOf(sp);
        Vec3 eye0 = sp.getEyePosition();
        Vec3 hand = V.add(V.add(V.up(eye0, -0.4), f0, 0.8), V.rightOf(f0), 0.4);
        SkillFx.fx(level, "rpg:arrow_spark", hand, Opt.of("dir", V.UP, "size", 0.1, "grow", 2.0, "life", 0.18, "color", core, "alpha", 1.0));
        SkillFx.sparks(level, hand, glow, 5, 1.5);
        SkillFx.shock(level, sp.position(), glow, 1.1, 0.3);
        SkillFx.play(level, "item.trident.riptide_1", eye0, 1.5, 0.6);
        SkillFx.play(level, "chime.amethyst_block", eye0, 1.4, 1.0);
        HeroKit.at((int) k.num("windup"), () -> {
            if (ok.getAsBoolean()) release(sp, w, k, ok, glow, core);
        });
        return true;
    }

    /** 휘둘러 검기를 날림 */
    private static void release(ServerPlayer sp, Weapon w, Obj k, BooleanSupplier ok, double[] glow, double[] core) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 view = sp.getViewVector(1F);
        // 위아래: 15° 까지는 수평 (가까운 적을 내려다보며 써도 땅에 박히지 않게), 그 너머는 pitch 도까지 기울임
        double look = Math.asin(Math.max(-1, Math.min(1, view.y)));
        double tilt = Math.signum(look) * Math.max(0, Math.abs(look) - Math.toRadians(15));
        double maxPitch = Math.toRadians(k.num("pitch"));
        double pitch = Math.max(-maxPitch, Math.min(maxPitch, tilt));
        Vec3 dir = V.unit(new Vec3(f.x * Math.cos(pitch), Math.sin(pitch), f.z * Math.cos(pitch)));
        Vec3 from = V.up(sp.position(), 1.1);
        double len = Math.max(0, Kit.rayDist(level, from, dir, k.num("range")) - 0.3);
        double speed = k.num("speed");
        double width = k.num("width");
        double size = k.num("size");
        // 휘두름: 눈앞 2칸의 초승달 (금빛 기운 — 1인칭 화면을 다 덮지 않게 작게) + 바람 소리
        SkillFx.arc(level, V.add(V.up(sp.getEyePosition(), -0.3), f, 2.0), f, Opt.of("shape", "heavy", "flip", false, "size", 1.35, "life", 0.18, "speed", 1.5,
                "color", glow, "aura", GOLD, "spin", -15.0));
        SkillFx.play(level, "item.trident.riptide_2", sp.position(), 1.3, 0.9);
        SkillFx.play(level, "entity.player.attack.sweep", sp.position(), 0.75, 1.0);
        SkillFx.shake(sp, 0.12, 0.12);
        // 검기 그림 (rpg:rfx_wave 빛 + 흰 심지 — 보이는 초승달 폭 ≈ 1.7 × size): 앞 start 칸에서 나타나 끝까지 같은 빠르기로 (1인칭 화면을 덮지 않게 2.6칸 앞에서)
        double start = Math.min(2.6, len);
        double vel = speed * 20;
        double flightS = Math.max(0.05, (len - start) / vel);
        if (len > 0.6) {
            Vec3 p0 = V.add(from, dir, start);
            RuneKit.rfx(level, "wave", p0, Opt.of("dir", dir, "size", size, "life", flightS + 0.06, "speed", vel, "color", glow, "alpha", 0.45, "cell", 0.0,
                    "fade", 0.2));
            RuneKit.rfx(level, "wave", V.add(p0, dir, 0.05), Opt.of("dir", dir, "size", size * 0.95, "life", flightS + 0.06, "speed", vel, "color", core, "alpha",
                    0.5, "cell", 1.0, "fade", 0.2));
            // 0.48.0 입체 검기: 비스듬히 누운 푸른 초승달 (볼록한 날이 앞 — 오른쪽이 30° 들림: 사진첩 — 땅과 나란하면 뒤에서 보는 1 · 3인칭에서 가는 줄로만 보였음)
            // + 뒤따르는 잔상 하나
            Vec3 up = V.sub(V.UP, V.scale(dir, dir.y));
            up = V.len(up) > 1e-3 ? V.unit(up) : V.UP;
            Vec3 end = V.add(from, dir, len);
            double ws = size * 0.85;
            WeaponFx3d.fx(level, "crescent_wave", p0, glow, core, Opt.of("to", end, "travel", flightS, "dir", dir, "face", up, "roll", -30.0, "scale", ws, "life",
                    flightS + 0.08, "pop", 0.06, "fadeOut", 0.1, "glowAlpha", 0.65, "shine", 0.45));
            WeaponFx3d.fx(level, "crescent_wave", V.add(p0, dir, -0.6), glow, glow, Opt.of("to", V.add(end, dir, -0.6), "travel", flightS, "dir", dir, "face", up,
                    "roll", -30.0, "scale", ws * 0.82, "life", flightS + 0.06, "delay", 0.05, "alpha", 0.45, "fadeOut", 0.1, "glowAlpha", 0.35, "shine", 0.2));
        }
        Vec3 side = V.rightOf(f);
        int steps = Math.max(1, (int) Math.ceil(len / speed));
        Set<UUID> struck = new HashSet<>();
        Kit.ticks(t -> {
            if (!ok.getAsBoolean()) return false;
            double s0 = t == 0 ? 0 : Math.min(len, speed * t);
            double s1 = Math.min(len, speed * (t + 1));
            Vec3 at = V.add(from, dir, s1);
            // 검기 뒤로 빛줄기 셋 (가운데 · 양옆 — 넓은 검기)
            if (s1 > start - 0.2) {
                Vec3 tail = V.add(from, dir, Math.max(start - 0.6, s1 - 1.8));
                Gram.streakLine(level, tail, at, glow, 0.42, 0.22, 0.8, 0);
                Gram.streakLine(level, tail, at, core, 0.12, 0.18, 1.0, 0);
                for (int s : new int[] {-1, 1}) {
                    Vec3 o = V.up(V.scale(side, s * size * 0.36), -0.12);
                    Gram.streakLine(level, V.add(tail, o), V.add(at, o), glow, 0.2, 0.2, 0.7, 0);
                }
                if (t % 2 == 0) SkillFx.sparks(level, at, core, 3, 2.5);
            }
            // 이번 틱에 지나간 구간의 적
            if (s1 - s0 > 0.05) {
                for (Kit.LineHit h : Kit.lineHits(sp, V.add(from, dir, s0), dir, s1 - s0, width)) {
                    LivingEntity e = h.e();
                    if (struck.add(e.getUUID())) cut(sp, w, k, ok, e, dir, glow, core);
                }
            }
            if (t + 1 < steps) return true;
            // 끝: 검기가 흩어짐 (벽에 막혔으면 부딪힌 소리)
            SkillFx.burst(level, at, glow, 1.5, 0.3);
            SkillFx.sparks(level, at, core, 8, 4);
            if (len < k.num("range") - 0.6) SkillFx.play(level, "random.glass", at, 1.6, 0.4);
            SkillFx.play(level, "chime.amethyst_block", at, 0.9, 0.7);
            return false;
        });
    }

    /** 검기가 적을 벰 (mult) → delay 틱 뒤 검흔이 터짐 (afterMult) */
    private static void cut(ServerPlayer sp, Weapon w, Obj k, BooleanSupplier ok, LivingEntity e, Vec3 dir, double[] glow, double[] core) {
        ServerLevel level = sp.level();
        long now = Tasks.now();
        Kit.hitAt(sp, e, Kit.reserveHit(sp, e, now), ok, () -> {
            if (Kit.skillHit(sp, e, w, k.num("mult")) <= 0) return;
            Vec3 eye = sp.getEyePosition();
            Vec3 c = HeroKit.body(e, eye);
            double near = HeroKit.near(eye, c, 0.5);
            Vec3 v = V.unit(V.sub(c, eye));
            // 벤 자리 (몸 겉면 — 눈 쪽으로 0.4칸, 몸에 가려지지 않게): X 번쩍임 + 검흔 (터질 때까지 남음)
            Vec3 front = V.add(c, v, -0.4);
            double hold = Math.max(0.05, k.num("delay") / 20.0 - 0.05);
            SkillFx.fx(level, "rpg:slash_x", front, Opt.of("dir", dir, "size", 1.1 * near, "life", 0.24, "color", glow, "alpha", 1.0));
            SkillFx.cutMark(level, front, v, 8, hold, glow, core, 1.1 * near);
            SkillFx.sparks(level, front, core, 5, 4);
            SkillFx.play(level, "entity.player.attack.sweep", c, 1.3, 0.6);
            double knock = k.num("knock");
            Kit.knockback(e, dir.x * knock, dir.z * knock, 0.12);
            // 검흔이 터짐: 몸을 따라감
            Kit.hitAt(sp, e, Kit.reserveHit(sp, e, Tasks.now() + (long) k.num("delay")), ok, () -> {
                if (Kit.skillHit(sp, e, w, k.num("afterMult")) <= 0) return;
                Vec3 eye2 = sp.getEyePosition();
                Vec3 c2 = HeroKit.body(e, c);
                double n2 = HeroKit.near(eye2, c2, 0.45);
                Vec3 v2 = V.unit(V.sub(c2, eye2));
                Vec3 f2 = V.add(c2, v2, -0.4);
                // 검흔이 X 로 갈라지며 번쩍 (푸른 별빛 · 고리 · 사방으로 튀는 빛 조각)
                // 0.48.0 입체: 작은 초승달 둘이 X 로 엇갈리며 퍼짐 (보는 사람 쪽 면)
                Vec3 r2 = V.cross(v2, V.UP);
                r2 = V.len(r2) > 1e-3 ? V.unit(r2) : new Vec3(1, 0, 0);
                Vec3 u2 = V.unit(V.cross(r2, v2));
                for (int s2 : new int[] {-1, 1}) {
                    Vec3 d2 = V.unit(V.add(u2, r2, s2));
                    WeaponFx3d.fx(level, "crescent_wave", V.add(f2, d2, -0.25 * n2), glow, core, Opt.of("dir", d2, "face", V.scale(v2, -1), "scale", 0.6 * n2,
                            "grow", 0.7, "vel", V.scale(d2, 2.5 * n2), "life", 0.26, "pop", 0.04, "fadeOut", 0.14, "glowAlpha", 0.7, "shine", 0.5));
                }
                SkillFx.cutMark(level, f2, v2, 42, 0.06, glow, core, 1.45 * n2);
                SkillFx.cutMark(level, f2, v2, -42, 0.06, glow, core, 1.45 * n2);
                RuneKit.rfx2(level, "flare", V.add(f2, v2, -0.05), Opt.of("size", 1.5 * n2, "life", 0.32, "grow", 0.6, "fade", 0.6, "spin", Kit.rand() * 90), glow,
                        core, new int[] {0, 1}, null);
                SkillFx.burst(level, f2, glow, 1.0 * n2, 0.28);
                SkillFx.ring(level, f2, v2, glow, 0.9 * n2, 0.3, 0);
                SkillFx.sparks(level, f2, core, 8, 6);
                SkillFx.play(level, "random.glass", c2, 1.7, 0.35);
                SkillFx.play(level, "chime.amethyst_block", c2, 1.2, 0.9);
            });
        });
    }
}
