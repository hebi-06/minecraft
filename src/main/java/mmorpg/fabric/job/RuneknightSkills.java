package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 마검사 각성 뵐룬드(룬 스미스 — 룬 나이트에서 이름을 바꿈) — 재설계안 2판 (사용자 "각성은 스킬셋을 아예 바꿔도 된다니깐 매커니즘만 유지하고 컨셉만 유지하면" ·
 * "스킬 컨셉은 롤같은거 많이 참고하면 좋음" · "직업 이름 같은거 1차랑 2차랑 바꾸고 싶으면 바꿔도 됨"): 마검사와 같은 매커니즘 (근접 마법 딜러 · 광역 · 큰 단타 2단 ·
 * 마력 각인), 전설의 대장장이 뵐룬드 — 1차와 짝이 없는 새 스킬 다섯: 룬 모루 (롤 오른 Q — 갈라짐 끝에 솟은 모루가 터짐) · 풀무 숨 (롤 오른 W) · 용광로 (롤 브랜드 W) ·
 * 담금질 (롤 브랜드 패시브) · 쇳물 숫양 (롤 오른 R). 달궈짐 · 식으며 깨짐 · 쇳조각 · 각인 번짐은 JobTraits7. 맞은 자리는 JobImpact (화면은 흔들지 않음). 마검사는 몸
 * 쓰는 직업이 아님 — 룬 · 쇳물 · 환영 망치가 스스로 솟고 터짐. 그림: 쇳물빛 갈라짐 (WarlordSkills.rift) · 룬 모루 (rfx_janvil) · 쇳물 웅덩이 (rfx_jmolten) · 쇳물 숫양
 * (rfx_jram) · 쇳물 기둥 (rfx_fountain) · 폭발 (rfx_boom) · 쇳물 방울 (rpg:glob) · 불꽃 (rfx_jflame · rfx_jfireball). 스킬이 처음 맞힌 적마다 각인 하나.
 */
final class RuneknightSkills {
    private RuneknightSkills() {}

    private static final double[] MOLTEN = JobTraits7.MOLTEN;
    private static final double[] HOT = JobTraits7.HOT;
    private static final double[] WHITE = RuneKit.WHITE;
    /** 룬 모루 몸 (어두운 쇠 — 풀밭 · 과녁 앞에서 묻히지 않게 조금 밝게) */
    private static final double[] IRON = {0.34, 0.28, 0.36};
    /** 불꽃 겉 · 심지 */
    private static final double[] FIRE = {1.0, 0.34, 0.04};
    private static final double[] FIRE_CORE = {1.0, 0.84, 0.42};
    /** 연기 (짙은 회색) */
    private static final double[] SOOT = {0.22, 0.2, 0.2};
    /** rfx_jflame · rfx_fountain 장 수 */
    private static final int FLAME = 6;
    private static final int FOUNTAIN = 4;
    /** 룬 모루 크기 (판 높이 약 3.4칸 — 모루 몸은 땅에서 약 2.2칸 · 너비 약 3칸, 영상 1 뒤 1.3 → 1.7: 과녁 몸에 가려 작은 그림자로만 보였음) */
    private static final double ANVIL_SIZE = 1.7;

    static void register() {
        JobSystem.register("runeknight_anvil", RuneknightSkills::anvil);
        JobSystem.register("runeknight_bellows", RuneknightSkills::bellows);
        JobSystem.register("runeknight_furnace", RuneknightSkills::furnace);
        JobSystem.register(JobTraits7.TEMPER, RuneknightSkills::temper);
        JobSystem.register("runeknight_ult", RuneknightSkills::ult);
    }

    /** 이 스킬로 처음 맞힌 적이면 각인 하나 */
    private static void markOnce(ServerPlayer sp, LivingEntity e, Jobs.Job j, Set<UUID> marked) {
        if (marked.add(e.getUUID())) JobTraits.mark(sp, e, j);
    }

    /** 땅 높이로 내린 자리 */
    private static Vec3 ground(ServerLevel level, Vec3 p) {
        return new Vec3(p.x, RuneKit.groundAt(level, V.up(p, 0.6), p.y), p.z);
    }

    /** 서 있는 불꽃 (rfx_jflame — 이어 돌림): 밑동 = ground · 높이 약 size × 2 칸 */
    static void flame(ServerLevel level, Vec3 ground, double size, double life, Opt o) {
        JobFx.loop(level, "jflame", FLAME, V.up(ground, size * 0.85), life, 14, o.with("size", size, "fade", o.num("fade", 0.3), "grow", o.num("grow", 0.15)), FIRE,
                FIRE_CORE);
    }

    /** 튀어 떨어지는 쇳물 방울 n 개 (rpg:glob — 중력) */
    static void splatter(ServerLevel level, Vec3 at, int n, double speed, double size) {
        for (int i = 0; i < n; i++) {
            double a = Kit.rand() * Math.PI * 2;
            SkillFx.fx(level, "rpg:glob", at, Opt.of("dir", V.unit(new Vec3(Math.cos(a), 1.3 + Kit.rand(), Math.sin(a))), "speed", speed * (0.7 + Kit.rand() * 0.6),
                    "size", size * (0.7 + Kit.rand() * 0.6), "life", 0.8, "spin", Kit.rand() * 360, "color", i % 3 == 0 ? HOT : MOLTEN, "alpha", 1.0));
        }
    }

    /** 쇳물 기둥 (rfx_fountain 두 겹 — 밑동 = ground, 높이 약 size × 2 칸): 쇳물 겉 · 흰 열 심지 · 꼭대기 방울 */
    static void pillar(ServerLevel level, Vec3 ground, double size, double life) {
        JobFx.two(level, "fountain", FOUNTAIN, ground, life, Opt.of("size", size, "fade", 0.45, "grow", 0.1), MOLTEN, HOT);
        JobFx.two(level, "fountain", FOUNTAIN, ground, life * 0.85, Opt.of("size", size * 0.62, "fade", 0.45, "alpha", 0.9), HOT, WHITE);
        splatter(level, V.up(ground, size * 1.6), (int) Math.round(6 + size * 3), 6 + size, 0.16 + size * 0.03);
    }

    // ------------------------------------------------------------ 룬 모루

    /**
     * 룬 모루 (롤 오른 Q): 대검을 머리 위로 들었다가 strikeAt 틱에 앞 땅을 내려찍음 → 앞 start ~ len 칸이 갈라짐 (speed 칸/틱 — 쇳물빛 틈 · 틈을 따라 솟는 불꽃) ·
     * 갈라짐이 닿는 적 (폭 width — crack · 마법) → 처음 닿은 적 바로 뒤 (없으면 갈라짐 끝) 에 룬 모루가 땅을 뚫고 솟아 달아오름 (터질 둘레의 룬 고리 · 쇳물 웅덩이) →
     * burstAt 틱 뒤 터짐 (burst — 둘레 burstRadius 칸 · 띄움 lift)
     */
    static boolean anvil(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int strikeAt = Math.max(1, (int) k.num("strikeAt", 4));
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        // 들어 올림: 머리 위 칼끝의 룬 글자 (1인칭에서는 숨김) · 칼끝으로 모이는 불티 · 쇠 소리
        JobFx.glyph(level0, V.up(V.add(c0, Kit.facingOf(sp), 0.2), 2.75), 0.5, 1, 0, 2, strikeAt / 20.0 + 0.05, false, JobFx.hide1p(sp).with("alpha", 0.9), j);
        for (int t = 0; t < strikeAt; t += 2) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 tip = V.up(V.add(sp.position(), Kit.facingOf(sp), 0.1), 2.6);
                SkillFx.fx(sp.level(), "rpg:arrow_spark", tip, Opt.of("dir", V.UP, "speed", 1.6, "size", 0.14, "grow", 0.6, "life", 0.3, "color", HOT, "alpha", 1.0));
            });
        }
        SkillFx.play(level0, "item.armor.equip_iron", c0, 0.8, 0.7);
        SkillFx.play(level0, "block.respawn_anchor.charge", c0, 1.5, 0.45);
        RuneKit.later(strikeAt, () -> {
            if (RuneKit.sameLife(sp, life)) anvilStrike(sp, j, k, P, life);
        });
        return true;
    }

    /** 내려찍음: 자리의 쇳물 터짐 · 갈라짐 · 닿는 적 피해 · 모루가 솟을 자리와 때 */
    private static void anvilStrike(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 right = V.rightOf(f);
        double start = k.num("start", 1.0);
        double len = k.num("len", 7);
        double half = k.num("width", 1.6) / 2;
        double speed = Math.max(0.5, k.num("speed", 2));
        Vec3 g0 = ground(level, V.add(c, f, start));
        // 내려찍은 자리: 땅의 쇳물 터짐 · 룬 글자 번쩍 · 튀는 돌 · 흰 열 불티 · 무거운 쇠 소리
        JobFx.smash(level, V.up(g0, 0.05), 1.5, j, 0.5, Opt.empty());
        JobFx.burst(level, V.up(g0, 0.35), 1.1, MOLTEN, HOT, 0.3, Opt.empty());
        JobFx.glyph(level, V.up(g0, 0.95), 0.8, 0, 4, 2, 0.3, false, Opt.empty(), j);
        RuneKit.rockBurst(level, V.up(g0, 0.2), 7, 4.8, 0.16);
        SkillFx.sparks(level, V.up(g0, 0.3), HOT, 14, 6.0);
        splatter(level, V.up(g0, 0.2), 5, 5, 0.13);
        SkillFx.play(level, "random.anvil_land", g0, 0.7, 0.9);
        SkillFx.play(level, "mace.heavy_smash_ground", g0, 0.9, 0.7);
        // 갈라짐: 쇳물빛 틈 (땅에 눕힘 — 닿는 때에) · 틈을 따라 솟는 불꽃 · 연기
        WarlordSkills.rift(level, g0, f, len - start, MOLTEN, 1.3, speed * 20);
        for (double s = 0.6; s < len - start; s += 1.15) {
            double ss = s;
            int at = (int) Math.round(s / speed);
            RuneKit.later(at, () -> {
                Vec3 p = ground(level, V.add(V.add(g0, f, ss), right, (Kit.rand() - 0.5) * 0.5));
                flame(level, p, 0.42 + Kit.rand() * 0.18, 0.6 + Kit.rand() * 0.25, Opt.of("alpha", 0.95));
                SkillFx.fx(level, "rpg:ember", V.up(p, 0.2), Opt.of("size", 0.8, "life", 0.6, "speed", 1.2, "color", MOLTEN, "alpha", 0.85));
            });
        }
        // 맞는 적: 앞 start ~ len 칸 · 폭 half (+ 몸 폭) — 갈라짐이 닿는 틱에. 처음 닿은 적 바로 뒤에 모루
        Set<UUID> marked = new HashSet<>();
        LivingEntity first = null;
        double firstAlong = Double.MAX_VALUE;
        for (LivingEntity e : Kit.around(sp, V.add(c, f, (start + len) / 2), (len - start) / 2 + 1.5, 2.5)) {
            Vec3 d = V.sub(e.position(), c);
            double along = d.x * f.x + d.z * f.z;
            double side = Math.abs(d.x * right.x + d.z * right.z);
            double pad = Kit.bodyPad(e);
            if (along < start - 0.6 - pad || along > len + 0.5 + pad || side > half + pad) continue;
            int at = (int) Math.ceil(Math.max(0, along - start) / speed);
            if (along < firstAlong) {
                firstAlong = along;
                first = e;
            }
            JobKit.strike(sp, e, j, P, k.num("crack", 3.4), true, false, life, at, x -> {
                markOnce(sp, e, j, marked);
                JobImpact.hit(sp, e, j, 1.0);
                if (JobKit.pushable(e)) Kit.launch(e, 0, 0.25, 0);
            });
        }
        int riseAt = first != null ? (int) Math.ceil(Math.max(0, firstAlong - start) / speed) : (int) Math.ceil((len - start) / speed);
        LivingEntity anchor = first;
        Vec3 end = ground(level, V.add(c, f, len));
        RuneKit.later(riseAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            // 처음 닿은 적 바로 뒤 (쓴 사람 반대쪽 0.9칸 + 몸 폭) — 적 몸이 모루를 가리지 않고 모루가 적 뒤에 버티고 솟음 (영상 1 뒤)
            Vec3 at = anchor != null && !anchor.isRemoved() ? ground(sp.level(), V.add(anchor.position(), f, 0.9 + Kit.bodyPad(anchor))) : end;
            anvilRise(sp, j, k, P, at, life, marked);
        });
    }

    /**
     * 룬 모루가 솟음 (at — 땅): 땅이 터지며 모루가 솟아 (2틱) 달아오름 (룬이 하나씩 켜지다 흰 열 금 — rfx_janvil) · 발밑 쇳물 웅덩이 · 터질 둘레의 룬 고리 · 모루를 두드리는
     * 소리 셋 (점점 높게) · 오르는 불티 → burstAt 틱 뒤 터짐
     */
    private static void anvilRise(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 at, String life, Set<UUID> marked) {
        ServerLevel level = sp.level();
        int burstAt = Math.max(10, (int) k.num("burstAt", 12));
        double R = k.num("burstRadius", 2.5);
        JobFx.anvil(level, at, ANVIL_SIZE, 0, 0.12, Opt.empty(), IRON, MOLTEN);
        RuneKit.later(2, () -> JobFx.anvil(level, at, ANVIL_SIZE, 1, (burstAt - 2) / 20.0 + 0.03, Opt.empty(), IRON, MOLTEN));
        JobFx.molten(level, V.up(at, 0.04), 1.5, 0, 2, 0.15, false, Opt.empty());
        RuneKit.later(3, () -> JobFx.molten(level, V.up(at, 0.04), 1.5, 2, 4, (burstAt - 3) / 20.0 + 0.5, true, Opt.of("fade", 0.4)));
        JobFx.runeRing(level, V.up(at, 0.1), R, j, 9, Kit.rand() * 40, burstAt / 20.0 + 0.05, Opt.of("alpha", 0.85));
        RuneKit.rockBurst(level, V.up(at, 0.3), 7, 5.0, 0.16);
        SkillFx.sparks(level, V.up(at, 0.6), MOLTEN, 12, 4.5);
        SkillFx.play(level, "block.basalt.break", at, 0.6, 1.0);
        SkillFx.play(level, "random.anvil_land", at, 0.5, 0.8);
        for (int t = 3; t < burstAt; t += 3) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 top = V.up(at, ANVIL_SIZE * 1.3);
                SkillFx.sparks(level, top, tt % 2 == 0 ? HOT : MOLTEN, 6, 3.0 + tt * 0.15);
                SkillFx.fx(level, "rpg:arrow_spark", V.up(at, 0.4), Opt.of("dir", V.UP, "speed", 2.4, "size", 0.12, "grow", 0.5, "life", 0.5, "color", MOLTEN,
                        "alpha", 1.0));
                SkillFx.play(level, "block.anvil.use", top, 1.0 + tt * 0.05, 0.4);
            });
        }
        RuneKit.later(burstAt - 2, () -> {
            if (RuneKit.sameLife(sp, life)) SkillFx.play(level, "block.respawn_anchor.charge", at, 1.8, 0.6);
        });
        RuneKit.later(burstAt, () -> {
            if (RuneKit.sameLife(sp, life)) anvilBurst(sp, j, k, P, at, life, marked);
        });
    }

    /** 모루가 터짐: 조각이 튐 (rfx_janvil 6 ~ 7) · 쇳물 폭발 (rfx_boom) · 타격 섬광 · 룬 글자 · 충격 고리 · 쇳물 방울 · 돌 — 둘레 burstRadius 칸 적 피해 · 띄움 */
    private static void anvilBurst(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 at, String life, Set<UUID> marked) {
        ServerLevel level = sp.level();
        double R = k.num("burstRadius", 2.5);
        Vec3 mid = V.up(at, ANVIL_SIZE);
        JobFx.anvil(level, at, ANVIL_SIZE, 2, 0.4, Opt.empty(), IRON, MOLTEN);
        RuneKit.rfx(level, "boom", mid, Opt.of("size", R * 0.95, "life", 0.55, "frames", 8.0, "fps", 15.0));
        JobFx.burst(level, mid, 1.7, MOLTEN, HOT, 0.35, Opt.empty());
        JobFx.two(level, "jimpact", JobImpact.IMPACT, mid, 0.32, Opt.of("size", 2.1, "spin", Kit.rand() * 360, "fade", 0.25), MOLTEN, WHITE);
        JobFx.glyph(level, V.up(mid, 0.5), 1.05, (int) (Kit.rand() * 3), 4, 2, 0.35, false, Opt.empty(), j);
        SkillFx.shock(level, V.up(at, 0.1), MOLTEN, R, 0.45);
        JobFx.molten(level, V.up(at, 0.05), R * 0.9, 6, 2, 0.9, false, Opt.empty());
        splatter(level, mid, 12, 7, 0.16);
        RuneKit.rockBurst(level, V.up(at, 0.5), 8, 6.5, 0.18);
        SkillFx.sparks(level, mid, HOT, 20, 7.5);
        SkillFx.particle(level, ParticleTypes.LARGE_SMOKE, V.up(mid, 0.5), 8, 0.6, 0.05);
        SkillFx.play(level, "random.explode", at, 0.9, 0.8);
        SkillFx.play(level, "random.anvil_land", at, 0.6, 0.9);
        SkillFx.play(level, "item.mace.smash_ground_heavy", at, 1.1, 0.6);
        for (LivingEntity e : Kit.around(sp, at, R, 2.5)) {
            JobKit.strike(sp, e, j, P, k.num("burst", 5.1), true, false, life, 0, x -> {
                markOnce(sp, e, j, marked);
                JobImpact.hit(sp, e, j, 1.6);
                if (JobKit.pushable(e)) Kit.launch(e, 0, k.num("lift", 0.6), 0);
            });
        }
    }

    // ------------------------------------------------------------ 풀무 숨

    /**
     * 풀무 숨 (롤 오른 W): dur 틱 동안 대검을 앞으로 겨눠 칼날의 룬을 풀무 삼아 불을 뿜음 — 앞 range 칸 arc 도 부채꼴, first 틱부터 every 틱마다 큰 숨 (breaths — 숨마다
     * 맞은 적 피해 · 마법) · 그 사이 이어지는 불길 (그림) · 그동안 보호막 (최대 체력 × shield) · 밀려나지 않음 → 끝나면 숨에 맞은 적 (살아 있는) 이 달궈짐
     */
    static boolean bellows(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = Math.max(10, (int) k.num("dur", 30));
        int first = Math.max(0, (int) k.num("first", 4));
        int every = Math.max(10, (int) k.num("every", 10));
        double range = k.num("range", 5);
        double arc = k.num("arc", 70);
        List<Double> breaths = SwordmasterSkills.numbers(k, "breaths", List.of(1.7, 1.7, 1.7));
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        double max = RpgHealth.maxOf(sp);
        RpgHealth.addShield(sp, max * k.num("shield", 0.12), dur + 4, max * k.num("shield", 0.12));
        RpgCombat.setSteadfast(sp, dur + 4);
        RuneKit.effect(sp, "slowness", dur, 1);
        // 칼날의 룬이 켜짐 (손 앞 — 1인칭에서는 눈앞에) · 발밑 쇳물빛 고리 (보호막) · 풀무 소리
        JobFx.glyph(level0, V.up(V.add(sp.getEyePosition(), Kit.facingOf(sp), 1.1), -0.35), 0.35, 2, 0, 2, 0.2, false, Opt.of("alpha", 0.9), j);
        JobFx.ring(level0, V.up(sp.position(), 0.08), 1.3, j, false, 0.45, Opt.of("alpha", 0.8));
        SkillFx.play(level0, "block.respawn_anchor.charge", sp.position(), 0.8, 0.6);
        SkillFx.play(level0, "item.firecharge.use", sp.position(), 0.6, 0.7);
        Set<UUID> hit = new HashSet<>();
        List<LivingEntity> burned = new ArrayList<>();
        Set<UUID> marked = new HashSet<>();
        // 이어지는 불길 (2틱마다 — 숨 사이는 작게, 숨 틱은 크게)
        for (int t = first; t < dur - 2; t += 2) {
            int tt = t;
            boolean big = (tt - first) % every == 0;
            RuneKit.later(t, () -> {
                if (RuneKit.sameLife(sp, life)) breathFx(sp, j, range, arc, big, tt);
            });
        }
        for (int i = 0; i < breaths.size(); i++) {
            double mult = breaths.get(i);
            RuneKit.later(first + i * every, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (LivingEntity e : RuneKit.coneTargets(sp, range + 0.5, arc, 12)) {
                    JobKit.strike(sp, e, j, P, mult, true, false, life, 0, x -> {
                        markOnce(sp, e, j, marked);
                        JobImpact.fx(sp, e, j, 0.9);
                        if (hit.add(e.getUUID())) burned.add(e);
                        if (e.level() instanceof ServerLevel lv) {
                            SkillFx.particle(lv, ParticleTypes.FLAME, Kit.bodyPoint(e), 4, 0.3, 0.04);
                            SkillFx.sparks(lv, Kit.bodyPoint(e), MOLTEN, 6, 3.0);
                        }
                    });
                }
            });
        }
        // 끝: 숨에 맞은 적이 달궈짐 · 칼날의 김
        RuneKit.later(dur, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            for (LivingEntity e : burned) if (!e.isRemoved() && e.isAlive()) JobTraits7.heat(sp, e, j);
            RuneKit.Hand h = RuneKit.handOf(sp);
            JobFx.smoke(sp.level(), V.up(h.hand(), 0.4), 0.5, SOOT, MOLTEN, 0.6, Opt.of("vel", new Vec3(0, 0.8, 0), "alpha", 0.6));
            SkillFx.play(sp.level(), "block.fire.extinguish", h.hand(), 1.2, 0.5);
        });
        return true;
    }

    /**
     * 풀무 숨 불길 한 번: 칼끝 (손 앞 1.2칸) 에서 부채꼴로 뻗는 불덩이 (rfx_jfireball — 가는 쪽으로 누움) · 부풀어 오르는 불 연기 (rpg:breath) · 불꽃 · 큰 숨이면 더 많이 ·
     * 부채꼴 끝의 불벽 (rpg:fire_wall) · 숨 소리
     */
    private static void breathFx(ServerPlayer sp, Jobs.Job j, double range, double arc, boolean big, int t) {
        ServerLevel level = sp.level();
        Vec3 look = sp.getViewVector(1F);
        Vec3 f = V.flat(look);
        Vec3 right = V.rightOf(f);
        Vec3 mouth = V.add(V.up(V.add(sp.position(), f, 1.25), 1.15), right, 0.15);
        int n = big ? 7 : 3;
        for (int i = 0; i < n; i++) {
            double a = (Kit.rand() - 0.5) * arc;
            Vec3 d = V.unit(V.add(V.turnY(f, a), V.UP, (Kit.rand() - 0.4) * 0.25 + Math.max(-0.3, Math.min(0.3, look.y)) * 0.6));
            double sp0 = (big ? 13 : 10) + Kit.rand() * 4;
            double lf = range / sp0 * (0.8 + Kit.rand() * 0.3);
            JobFx.loop(level, "jfireball", JobFx.FIREBALL, V.add(mouth, d, 0.2), lf, 14, Opt.of("dir", d, "speed", sp0, "size", (big ? 0.95 : 0.7) + Kit.rand() * 0.2,
                    "grow", 1.1, "fade", 0.35, "alpha", 0.95), FIRE, FIRE_CORE);
        }
        for (int i = 0; i < (big ? 3 : 1); i++) {
            double a = (Kit.rand() - 0.5) * arc * 0.8;
            Vec3 d = V.turnY(f, a);
            SkillFx.fx(level, "rpg:breath", V.add(mouth, d, 0.4), Opt.of("dir", d, "speed", big ? 9.0 : 7.5, "size", big ? 2.1 : 1.5, "life", 0.6, "spin", Kit.rand()
                    * 360, "color", i % 2 == 0 ? FIRE : MOLTEN, "alpha", 0.8));
        }
        SkillFx.particle(level, ParticleTypes.FLAME, V.add(mouth, f, range * 0.4), big ? 6 : 2, range * 0.18, 0.06);
        if (big) {
            for (int i = 0; i < 4; i++) {
                double a = -arc / 2 + arc * (i + 0.5) / 4;
                Vec3 p = ground(level, V.add(sp.position(), V.turnY(f, a), range * (0.75 + Kit.rand() * 0.2)));
                SkillFx.fx(level, "rpg:fire_wall", p, Opt.of("size", 1.1, "life", 0.5, "color", FIRE, "alpha", 0.9, "spin", Kit.rand() * 360));
            }
            JobFx.glyph(level, V.add(mouth, f, -0.15), 0.32, 2, 4, 2, 0.22, false, Opt.of("alpha", 0.95, "vel", RuneKit.bodyVel(sp)), j);
            SkillFx.play(level, "mob.blaze.shoot", mouth, 0.6 + 0.05 * (t % 3), 0.8);
            SkillFx.play(level, "mob.ghast.fireball", mouth, 0.7, 0.5);
        } else if (t % 4 == 0) SkillFx.play(level, "fire.fire", mouth, 1.2, 0.6);
    }

    // ------------------------------------------------------------ 용광로

    /**
     * 용광로 (롤 브랜드 W): 대검을 앞 땅에 꽂으면 칼끝에서 룬 불길이 땅을 타고 조준한 곳 (range 칸 — 땅) 으로 달려가 둘레 radius 칸이 용광로로 달아오름 (쇳물 웅덩이 ·
     * 가장자리 룬 고리 · 둘레를 따라 솟는 불꽃 · 오르는 불티 · 끓는 소리) → eruptAt 틱에 쇳물 기둥이 솟구침 (total — 둘레 적 · 마법 · 띄움 lift) → 남은 적 달궈짐
     */
    static boolean furnace(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        double range = k.num("range", 10);
        double R = k.num("radius", 4);
        int eruptAt = Math.max(8, (int) k.num("eruptAt", 16));
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 aim = Kit.aimPoint(sp, range);
        Vec3 flat = V.sub(new Vec3(aim.x, c0.y, aim.z), c0);
        if (V.len(flat) > range) aim = V.add(c0, V.unit(flat), range);
        Vec3 g = ground(level0, aim);
        String life = RuneKit.lifeToken(sp);
        // 칼을 꽂은 자리 (2틱) → 룬 불길이 땅을 타고 달려감 (2 ~ 6틱 — 한 칸마다 불꽃 · 룬 불티)
        Vec3 f = V.flat(V.sub(g, c0));
        double dist = V.hdist(g, c0);
        RuneKit.later(2, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 plant = ground(lv, V.add(sp.position(), f, 0.9));
            JobFx.burst(lv, V.up(plant, 0.2), 0.7, MOLTEN, HOT, 0.25, Opt.empty());
            JobFx.glyph(lv, V.up(plant, 0.55), 0.5, 2, 4, 2, 0.25, false, Opt.empty(), j);
            SkillFx.play(lv, "random.anvil_land", plant, 1.2, 0.5);
            SkillFx.play(lv, "item.firecharge.use", plant, 0.8, 0.7);
        });
        int steps = Math.max(1, (int) Math.ceil(Math.max(0, dist - 1.2)));
        for (int i = 0; i < steps; i++) {
            double s = 1.2 + i * Math.max(0, dist - 1.2) / steps;
            int at = 2 + (int) Math.round(4.0 * i / steps);
            RuneKit.later(at, () -> {
                Vec3 p = ground(level0, V.add(c0, f, s));
                flame(level0, p, 0.32, 0.5, Opt.of("alpha", 0.9));
                SkillFx.fx(level0, "rpg:arrow_spark", V.up(p, 0.15), Opt.of("dir", V.UP, "speed", 2.2, "size", 0.12, "grow", 0.5, "life", 0.4, "color", JobKit.glow(j),
                        "alpha", 1.0));
            });
        }
        // 용광로 (6틱 ~): 쇳물 웅덩이가 번지며 끓음 · 가장자리 룬 고리 (경고) · 둘레 불꽃 (점점 높게) · 오르는 불티 · 끓는 소리
        int open = 6;
        RuneKit.later(open, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            JobFx.molten(lv, V.up(g, 0.05), R, 0, 2, 0.2, false, Opt.empty());
            RuneKit.later(4, () -> JobFx.molten(lv, V.up(g, 0.05), R, 2, 4, (eruptAt - open - 4) / 20.0 + 0.9, true, Opt.of("fade", 0.35)));
            JobFx.runeRing(lv, V.up(g, 0.1), R, j, 12, Kit.rand() * 30, (eruptAt - open) / 20.0 + 0.05, Opt.of("alpha", 0.95));
            int n = 10;
            for (int i = 0; i < n; i++) {
                double a = Math.toRadians(i * 360.0 / n + Kit.rand() * 10);
                Vec3 p = ground(lv, new Vec3(g.x + Math.cos(a) * R * 0.92, g.y, g.z + Math.sin(a) * R * 0.92));
                flame(lv, p, 0.38 + Kit.rand() * 0.12, (eruptAt - open) / 20.0 + 0.1, Opt.of("grow", 1.1, "alpha", 0.95));
            }
            SkillFx.play(lv, "item.bucket.empty_lava", g, 0.7, 0.8);
        });
        for (int t = open; t < eruptAt; t += 2) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                double q = (tt - open) / (double) (eruptAt - open);
                for (int i = 0; i < 3; i++) {
                    double a = Kit.rand() * Math.PI * 2;
                    double r = Math.sqrt(Kit.rand()) * R * 0.85;
                    Vec3 p = V.up(new Vec3(g.x + Math.cos(a) * r, g.y, g.z + Math.sin(a) * r), 0.15);
                    SkillFx.fx(lv, "rpg:arrow_spark", p, Opt.of("dir", V.UP, "speed", 2.0 + 3.0 * q, "size", 0.13, "grow", 0.6, "life", 0.5, "color", i == 0 ? HOT : MOLTEN,
                            "alpha", 1.0));
                }
                if (tt % 4 == 0) SkillFx.play(lv, "block.lava.pop", g, 0.7 + 0.5 * q, 0.7);
                if (q > 0.6) SkillFx.particle(lv, ParticleTypes.LAVA, V.up(g, 0.2), 2, R * 0.4, 0);
            });
        }
        RuneKit.later(eruptAt - 3, () -> {
            if (RuneKit.sameLife(sp, life)) SkillFx.play(sp.level(), "block.respawn_anchor.charge", g, 0.7, 0.8);
        });
        RuneKit.later(eruptAt, () -> {
            if (RuneKit.sameLife(sp, life)) erupt(sp, j, k, P, g, life);
        });
        return true;
    }

    /** 쇳물 기둥이 솟구침: 가운데 큰 기둥 · 둘레 작은 기둥 넷 · 폭발 · 충격 고리 · 쇳물 방울 · 연기 — 둘레 radius 칸 적 피해 · 띄움 → 남은 적 달궈짐 */
    private static void erupt(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, Vec3 g, String life) {
        ServerLevel level = sp.level();
        double R = k.num("radius", 4);
        pillar(level, g, 2.8, 0.7);
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(45 + i * 90 + Kit.rand() * 20);
            Vec3 p = ground(level, new Vec3(g.x + Math.cos(a) * R * 0.55, g.y, g.z + Math.sin(a) * R * 0.55));
            int ii = i;
            RuneKit.later(1 + ii % 2, () -> pillar(level, p, 1.5, 0.5));
        }
        RuneKit.rfx(level, "boom", V.up(g, 1.4), Opt.of("size", R * 0.85, "life", 0.6, "frames", 8.0, "fps", 14.0));
        JobFx.burst(level, V.up(g, 1.2), 2.2, MOLTEN, HOT, 0.4, Opt.empty());
        SkillFx.shock(level, V.up(g, 0.1), MOLTEN, R, 0.5);
        JobFx.molten(level, V.up(g, 0.06), R, 6, 2, 1.0, false, Opt.empty());
        RuneKit.rockBurst(level, V.up(g, 0.4), 10, 7.0, 0.18);
        SkillFx.sparks(level, V.up(g, 1.5), HOT, 24, 8.0);
        SkillFx.particle(level, ParticleTypes.LARGE_SMOKE, V.up(g, 2.5), 14, R * 0.35, 0.06);
        SkillFx.play(level, "random.explode", g, 0.7, 1.0);
        SkillFx.play(level, "mob.ghast.fireball", g, 0.5, 0.9);
        SkillFx.play(level, "item.bucket.empty_lava", g, 0.5, 1.0);
        Set<UUID> marked = new HashSet<>();
        for (LivingEntity e : Kit.around(sp, g, R, 3.0)) {
            JobKit.strike(sp, e, j, P, k.total(), true, false, life, 0, x -> {
                markOnce(sp, e, j, marked);
                JobImpact.hit(sp, e, j, 1.8);
                if (JobKit.pushable(e)) Kit.launch(e, 0, k.num("lift", 0.8), 0);
                if (!e.isRemoved() && e.isAlive()) JobTraits7.heat(sp, e, j);
            });
        }
    }

    // ------------------------------------------------------------ 담금질

    /**
     * 담금질 (롤 브랜드 패시브 — 태세 dur 틱): 대검을 눈앞에 가로로 들면 룬 망치 (환영 대망치) 가 위에서 세 번 내려쳐 두드림 (불티 · 모루 소리 · 칼이 점점 달아오름) →
     * 김이 솟구치며 칼이 쇳물빛으로 빛남 (SWING_LOOK runeknight_temper) · 발밑 쇳물빛 고리 · 평타 사거리 + reach. 그동안 칼 평타 · 직업 스킬이 맞힌 적에 달군 쇳조각
     * (JobTraits7.embedShard)
     */
    static boolean temper(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = (int) k.num("dur", 240);
        JobTraits.startStance(sp, k, dur);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c = sp.position();
        JobFx.molten(level0, V.up(c, 0.05), 1.6, 0, 2, 0.2, false, Opt.empty());
        RuneKit.later(4, () -> JobFx.molten(level0, V.up(c, 0.05), 1.6, 2, 4, 0.7, true, Opt.of("fade", 0.5)));
        SkillFx.play(level0, "block.respawn_anchor.charge", c, 1.2, 0.5);
        int[] strikes = {3, 7, 11};
        for (int i = 0; i < strikes.length; i++) {
            int ii = i;
            // 망치를 들어 올림 (내려치기 3틱 앞): 칼 오른쪽 위에서 칼날로 내려침
            RuneKit.later(strikes[i] - 3, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 f = Kit.facingOf(sp);
                Vec3 right = V.rightOf(f);
                Vec3 blade = V.up(V.add(sp.position(), f, 0.95), 1.2);
                double r = 1.05;
                Vec3 pivot = V.up(V.add(blade, right, r), 0.05);
                JobFx3d.swing(lv, "maul", pivot, V.UP, V.scale(right, -1), -35, 90, 0.15, 0.08, 0.5, new Vec3(0, -2.0, 0), j, Opt.of("arcEase", "in", "alpha", 0.95));
            });
            RuneKit.later(strikes[i], () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 f = Kit.facingOf(sp);
                Vec3 blade = V.up(V.add(sp.position(), f, 0.95), 1.2);
                double q = (ii + 1) / 3.0;
                JobFx.two(lv, "jimpact", JobImpact.IMPACT, blade, 0.22, Opt.of("size", 0.6 + 0.15 * ii, "spin", Kit.rand() * 360, "fade", 0.3), MOLTEN, WHITE);
                RuneKit.rfx2(lv, "sword", V.up(blade, 0.1), Opt.of("dir", V.unit(new Vec3(f.x, 0.15, f.z)), "size", 0.6 + 0.12 * ii, "life", 0.3, "alpha", 0.6 + 0.4 * q,
                        "vel", RuneKit.bodyVel(sp)), MOLTEN, HOT);
                SkillFx.sparks(lv, blade, HOT, 10 + 4 * ii, 5.0);
                SkillFx.sparks(lv, blade, MOLTEN, 6, 3.5);
                SkillFx.play(lv, "block.anvil.use", blade, 1.0 + 0.2 * ii, 0.7);
                SkillFx.play(lv, "random.anvil_land", blade, 1.6 + 0.1 * ii, 0.25);
            });
        }
        RuneKit.later(14, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            RuneKit.Hand h = RuneKit.handOf(sp);
            for (int i = 0; i < 3; i++) JobFx.smoke(lv, V.up(h.hand(), 0.4 + 0.3 * i), 0.55 + 0.15 * i, JobTraits7.STEAM, WHITE, 0.7 + 0.1 * i, Opt.of("vel", new Vec3(0,
                    1.0 + 0.3 * i, 0), "alpha", 0.6));
            JobFx.glint(lv, V.up(V.add(h.hand(), h.f(), 0.6), 0.6), 0.8, j, 0.4, Opt.of("vel", RuneKit.bodyVel(sp)));
            JobFx.ring(lv, V.up(sp.position(), 0.08), 2.0, j, false, 0.45, Opt.of("alpha", 0.85));
            SkillFx.shock(lv, V.up(sp.position(), 0.1), MOLTEN, 2.4, 0.35);
            SkillFx.play(lv, "block.lava.extinguish", h.hand(), 1.0, 0.8);
            SkillFx.play(lv, "beacon.activate", sp.position(), 1.4, 0.6);
            Ult.flash(lv, sp.position(), MOLTEN, Opt.of("radius", 4.0, "time", new double[] {0.03, 0.05, 0.3}), sp);
        });
        return true;
    }

    // ------------------------------------------------------------ 쇳물 숫양

    /**
     * 쇳물 숫양 (롤 오른 R — 대장장이 신의 부름): 붉은 하늘 · 칼을 땅에 짚고 부름 → 앞 summon 칸 땅이 달아올라 (경고 — 쇳물 웅덩이 · 룬 고리 · 달려올 길의 금) formAt 틱에
     * 터지며 거대한 쇳물 숫양이 솟음 → runAt 틱부터 speed 칸/틱으로 내게 달려옴 (지나간 자리 쇳물 자국 · 발굽 불꽃 — 폭 width 의 지나간 적 charge · 띄움) → clashAt 틱에
     * 내 앞에서 대검과 맞부딪쳐 버팀 (불티 · 쇳소리) → strikeAt 틱에 받아쳐 되돌려 보냄: 숫양이 터져 쇳물 물결이 앞으로 backLen 칸 (backSpeed 칸/틱 — 물결이 닿는 적
     * back · 밀쳐냄 knock · 달궈짐). 그동안 밀려나지 않음
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int formAt = Math.max(2, (int) k.num("formAt", 6));
        int runAt = Math.max(formAt + 2, (int) k.num("runAt", 10));
        int clashAt = (int) k.num("clashAt", 23);
        int strikeAt = Math.max(clashAt + 1, (int) k.num("strikeAt", 30));
        double summon = k.num("summon", 14);
        double speed = Math.max(0.3, k.num("speed", 1.0));
        double half = k.num("width", 3.0) / 2;
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 right = V.rightOf(f);
        RpgCombat.setSteadfast(sp, strikeAt + 14);
        RuneKit.effect(sp, "slowness", strikeAt + 4, 4);
        Ult.sky(level0, c0, "rpg:ult_forge", strikeAt + 40);
        Ult.flash(level0, c0, MOLTEN, Opt.of("radius", 10.0, "time", new double[] {0.04, 0.06, 0.4}), sp);
        SkillFx.play(level0, "mob.enderdragon.growl", c0, 0.6, 0.5);
        SkillFx.play(level0, "block.respawn_anchor.charge", c0, 0.6, 0.9);
        Set<UUID> marked = new HashSet<>();
        // 경고: 부를 자리 (쇳물 웅덩이 · 룬 고리) · 달려올 길 (양쪽 가장자리의 쇳물빛 금 — 숫양 쪽에서 내게로)
        Vec3 g0 = ground(level0, V.add(c0, f, summon));
        JobFx.molten(level0, V.up(g0, 0.05), 2.6, 0, 2, 0.2, false, Opt.empty());
        RuneKit.later(4, () -> JobFx.molten(level0, V.up(g0, 0.05), 2.6, 2, 4, (runAt - 4) / 20.0 + 0.6, true, Opt.of("fade", 0.4)));
        JobFx.runeRing(level0, V.up(g0, 0.1), 2.8, j, 10, 0, runAt / 20.0, Opt.of("alpha", 0.95));
        for (int side = -1; side <= 1; side += 2) {
            Vec3 edge = V.add(g0, right, side * (half + 0.2));
            WarlordSkills.rift(level0, edge, V.scale(f, -1), summon - 2.0, MOLTEN, 0.7, 60);
        }
        for (int t = 1; t < formAt; t += 2) {
            RuneKit.later(t, () -> {
                SkillFx.sparks(level0, V.up(g0, 0.4), MOLTEN, 6, 4.0);
                SkillFx.particle(level0, ParticleTypes.LAVA, V.up(g0, 0.3), 2, 1.2, 0);
            });
        }
        // formAt: 땅이 터지며 숫양이 솟음 (쇳물 기둥 · 폭발 · 숫양 그림 0 ~ 1)
        double ramSize = 2.3;
        RuneKit.later(formAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            pillar(lv, g0, 2.4, 0.5);
            RuneKit.rfx(lv, "boom", V.up(g0, 1.6), Opt.of("size", 3.0, "life", 0.55, "frames", 8.0, "fps", 15.0));
            JobFx.ram(lv, g0, ramSize, 0, 2, (runAt - formAt) / 20.0 + 0.02, false, Opt.of("fade", 0.0, "alpha", 0.98), MOLTEN, HOT);
            RuneKit.rockBurst(lv, V.up(g0, 0.4), 10, 6.5, 0.2);
            SkillFx.play(lv, "random.explode", g0, 0.6, 1.0);
            SkillFx.play(lv, "entity.ravager.roar", g0, 0.6, 1.0);
            SkillFx.play(lv, "entity.goat.screaming.ambient", g0, 0.5, 1.0);
        });
        // runAt ~ clashAt: 달려옴 (한 장이 vel 로 미끄러짐 — 2 ~ 5 이어 돌림) · 발굽 자리의 쇳물 자국 · 불꽃 · 쿵쿵 · 지나간 적 피해
        // 맞댈 자리 (앞 2칸) 까지 speed 칸/틱 — 닿으면 (arrive) 뿔을 숙여 버티는 그림으로 바뀌고 clashAt 에 대검과 부딪침
        double clashDist = 2.0;
        Vec3 clashP = V.add(c0, f, clashDist);
        int runTicks = Math.max(1, (int) Math.ceil((summon - clashDist) / speed));
        int arrive = runAt + runTicks;
        RuneKit.later(runAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            JobFx.ram(sp.level(), new Vec3(g0.x, c0.y, g0.z), ramSize, 2, 4, runTicks / 20.0 + 0.02, true, Opt.of("vel", V.scale(f, -(summon - clashDist) / runTicks
                    * 20), "fade", 0.0, "alpha", 0.98), MOLTEN, HOT);
        });
        RuneKit.later(arrive, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.ram(sp.level(), new Vec3(clashP.x, c0.y, clashP.z), ramSize, 6, 1, (strikeAt - arrive) / 20.0 + 0.02, false, Opt.of(
                    "fade", 0.0, "alpha", 0.98), MOLTEN, HOT);
        });
        Set<UUID> run = new HashSet<>();
        for (int t = runAt; t <= arrive; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                double x = Math.max(clashDist, summon - speed * (tt - runAt));
                Vec3 p = ground(lv, V.add(c0, f, x));
                // 발굽: 쇳물 자국 (웅덩이 작게) · 불꽃 · 튀는 쇳물 · 쿵
                if ((tt - runAt) % 2 == 0) {
                    for (int s = -1; s <= 1; s += 2) {
                        Vec3 hoof = ground(lv, V.add(p, right, s * 0.75));
                        JobFx.molten(lv, V.up(hoof, 0.05), 0.55, 6, 2, 1.3, false, Opt.empty());
                        flame(lv, hoof, 0.32, 0.45, Opt.of("alpha", 0.9));
                    }
                    splatter(lv, V.up(p, 0.3), 3, 5, 0.12);
                    SkillFx.play(lv, "entity.iron_golem.step", p, 0.5, 1.0);
                }
                RuneKit.rockBurst(lv, V.up(p, 0.2), 2, 4.0, 0.14);
                // 지나간 적: 숫양 앞이 닿은 적 (폭 half · 처음 한 번)
                for (LivingEntity e : Kit.around(sp, p, half + 1.5, 3.0)) {
                    Vec3 d = V.sub(e.position(), c0);
                    double along = d.x * f.x + d.z * f.z;
                    double side = Math.abs(d.x * right.x + d.z * right.z);
                    double pad = Kit.bodyPad(e);
                    if (side > half + pad || along < x - 0.5 - pad || along > x + 1.5 + pad || !run.add(e.getUUID())) continue;
                    JobKit.strike(sp, e, j, P, k.num("charge", 24), true, false, life, 0, d0 -> {
                        markOnce(sp, e, j, marked);
                        JobImpact.hit(sp, e, j, 1.7);
                        if (JobKit.pushable(e)) Kit.launch(e, 0, k.num("lift", 1.0), 0);
                    });
                }
            });
        }
        // clashAt: 대검과 맞부딪침 (숫양 그림 6 — 버팀) · 흰 섬광 · 불티 · 쇳소리 → strikeAt 까지 맞댄 자리에서 불티가 튐
        RuneKit.later(clashAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            JobKit.motion(sp, "runeknight_ult:clash");
            Vec3 meet = V.up(V.add(sp.position(), Kit.facingOf(sp), 1.3), 1.2);
            JobFx.two(lv, "jimpact", JobImpact.IMPACT, meet, 0.3, Opt.of("size", 1.6, "spin", Kit.rand() * 360, "fade", 0.25), MOLTEN, WHITE);
            JobFx.burst(lv, meet, 1.2, MOLTEN, HOT, 0.3, Opt.empty());
            SkillFx.sparks(lv, meet, HOT, 20, 7.0);
            JobImpact.stop(sp, 4);
            SkillFx.play(lv, "entity.goat.ram_impact", meet, 0.5, 1.0);
            SkillFx.play(lv, "random.anvil_land", meet, 0.8, 1.0);
            SkillFx.play(lv, "item.shield.block", meet, 0.6, 0.9);
        });
        for (int t = clashAt + 2; t < strikeAt; t += 2) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 meet = V.up(V.add(sp.position(), Kit.facingOf(sp), 1.3), 1.2);
                SkillFx.sparks(lv, meet, Kit.rand() < 0.5 ? HOT : MOLTEN, 8, 5.0);
                SkillFx.play(lv, "block.grindstone.use", meet, 0.8, 0.5);
            });
        }
        RuneKit.later(strikeAt - 3, () -> {
            if (RuneKit.sameLife(sp, life)) SkillFx.play(sp.level(), "entity.ravager.roar", sp.position(), 1.2, 0.6);
        });
        RuneKit.later(strikeAt, () -> {
            if (RuneKit.sameLife(sp, life)) backWave(sp, j, k, P, life, marked);
        });
        return true;
    }

    /**
     * 받아쳐 되돌려 보냄 (strikeAt): 1인칭 큰 올려 베기 · 숫양이 터짐 (그림 7 · 폭발 · 흰 섬광) → 쇳물 물결이 앞으로 backLen 칸 (backSpeed 칸/틱 — 물결 머리마다 쇳물 왕관 ·
     * 쇳물 자국 · 불꽃 · 방울) — 머리가 닿는 적 back · 밀쳐냄 · 달궈짐
     */
    private static void backWave(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life, Set<UUID> marked) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 right = V.rightOf(f);
        double backSpeed = Math.max(0.3, k.num("backSpeed", 1.5));
        double backLen = k.num("backLen", 14);
        double half = k.num("width", 3.0) / 2;
        JobKit.motion(sp, "runeknight_ult:strike");
        JobFx.slash(sp, j, 2.4, 0.2, 3.4, 200.0, 0.3);
        Vec3 burst = V.up(V.add(c, f, 2.0), 1.6);
        JobFx.ram(level, ground(level, V.add(c, f, 2.0)), 2.3, 7, 1, 0.3, false, Opt.of("fade", 0.6), MOLTEN, HOT);
        RuneKit.rfx(level, "boom", burst, Opt.of("size", 3.2, "life", 0.55, "frames", 8.0, "fps", 15.0));
        JobFx.burst(level, burst, 2.4, MOLTEN, HOT, 0.4, Opt.empty());
        JobFx.two(level, "jimpact", JobImpact.IMPACT, burst, 0.4, Opt.of("size", 3.0, "spin", Kit.rand() * 360, "fade", 0.3), MOLTEN, WHITE);
        RuneKit.godRays(sp, level, ground(level, burst), 4.0, 8, MOLTEN, HOT, Opt.of("h", 7.0, "life", 0.7, "alpha", 0.7, "skipNear", true));
        Ult.flash(level, c, HOT, Opt.empty(), sp);
        splatter(level, burst, 16, 8, 0.18);
        SkillFx.play(level, "random.explode", burst, 0.8, 1.0);
        SkillFx.play(level, "entity.player.attack.sweep", c, 0.5, 1.0);
        SkillFx.play(level, "item.bucket.empty_lava", burst, 0.6, 1.0);
        int ticks = (int) Math.ceil(Math.max(0, backLen - 1.0) / backSpeed);
        for (int t = 0; t <= ticks; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                ServerLevel lv = sp.level();
                double x = Math.min(backLen, 1.0 + backSpeed * tt);
                Vec3 head = ground(lv, V.add(c, f, x));
                for (int s = -1; s <= 1; s++) {
                    Vec3 p = ground(lv, V.add(head, right, s * half * 0.7));
                    if (tt % 2 == 0 || s == 0) RuneKit.rfx2(lv, "splash", V.up(p, 1.0 * 0.84), Opt.of("size", s == 0 ? 1.1 : 0.85, "life", 0.45, "grow", 0.3, "fade", 0.55,
                            "alpha", 0.95), MOLTEN, HOT, new int[] {0, 1}, null);
                    if (tt % 2 == 0) JobFx.molten(lv, V.up(p, 0.05), 0.85, 6, 2, 1.6, false, Opt.empty());
                }
                if (tt % 2 == 1) flame(lv, ground(lv, V.add(head, right, (Kit.rand() - 0.5) * half * 1.6)), 0.45, 0.55, Opt.of("alpha", 0.9));
                splatter(lv, V.up(head, 0.4), 3, 6, 0.14);
                if (tt % 3 == 0) SkillFx.play(lv, "block.lava.pop", head, 0.6, 0.9);
            });
        }
        for (LivingEntity e : Kit.around(sp, V.add(c, f, (1.0 + backLen) / 2), backLen / 2 + 1.5, 3.0)) {
            Vec3 d = V.sub(e.position(), c);
            double along = d.x * f.x + d.z * f.z;
            double side = Math.abs(d.x * right.x + d.z * right.z);
            double pad = Kit.bodyPad(e);
            if (along < -0.5 || along > backLen + 0.5 + pad || side > half + pad) continue;
            int at = (int) Math.ceil(Math.max(0, along - 1.0) / backSpeed);
            JobKit.strike(sp, e, j, P, k.num("back", 44), true, true, life, at, x -> {
                markOnce(sp, e, j, marked);
                JobImpact.hit(sp, e, j, 2.3);
                if (JobKit.pushable(e)) RuneKit.knock(e, f, k.num("knock", 1.2), 0.35);
                if (!e.isRemoved() && e.isAlive()) JobTraits7.heat(sp, e, j);
            });
        }
    }
}
