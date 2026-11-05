package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.skill.Guards;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.51.0 전사 각성 워로드 — 재설계안 2판 (사용자 "각성은 스킬셋을 아예 바꿔도 된다니깐 매커니즘만 유지하고 컨셉만 유지하면" · "스킬 컨셉은 롤같은거
 * 많이 참고하면 좋음" · "이펙트가 전직업보다 안좋았음 타격감이 없었음 이펙트가 작았음"): 전사와 같은 매커니즘 (근접 · 광역 · 묵직한 단타 · 맞으며 버팀 · 분노),
 * 1차와 짝이 없는 새 스킬 다섯 — 대지 가르기 (롤 사이온 Q — 모아 쳐서 다시 누름) · 동면 (롤 알리스타 R) · 곰의 포옹 (롤 세트 E) · 거인의 무게 (롤 말파이트 W) ·
 * 마지막 휘두름 (롤 다리우스 Q — 바깥 날). 맞은 자리는 JobImpact (큰 타격 그림 · 멈칫 — 화면은 흔들지 않음). 몸 쓰는 직업 기준 (제 몸 · 제 도끼 동작만 — 곰은
 * 곰의 혼 그림 rfx_jbear · 앞발 rfx_jpaw). 땅의 그림: 갈라지는 땅 (fissure) · 내려찍기 (rfx_jquake) · 땅에서 솟는 돌창 (rfx_jspike).
 */
final class WarlordSkills {
    private WarlordSkills() {}

    static final String REND = "warlord_rend";
    static final String SLUMBER = "warlord_slumber";
    /** 흙먼지 (누런 회색) */
    private static final double[] DUST = JobTraits6.DUST;
    /** 갈라진 땅의 속 (어두운 틈) */
    private static final double[] RIFT = {0.05, 0.03, 0.02};
    /** 돌창의 몸 (짙은 흙빛 돌) */
    static final double[] ROCK = {0.34, 0.25, 0.18};
    private static final double[] WHITE = RuneKit.WHITE;
    /** 갈라짐이 앞으로 뻗는 빠르기 (칸/초) — 대지 가르기는 가까운 2칸이 바로 */
    private static final double SPLIT_SPEED = 40;

    static void register() {
        JobSystem.register(REND, WarlordSkills::rend);
        JobSystem.register(SLUMBER, WarlordSkills::slumber);
        JobSystem.register("warlord_hug", WarlordSkills::hug);
        JobSystem.register(JobTraits6.TITAN, WarlordSkills::titan);
        JobSystem.register("warlord_ult", WarlordSkills::ult);
    }

    /** 발 앞 ahead 칸의 땅 */
    private static Vec3 groundAhead(Vec3 feet, Vec3 f, double ahead, ServerLevel level) {
        Vec3 a = V.add(feet, f, ahead);
        return new Vec3(a.x, RuneKit.groundAt(level, a, feet.y), a.z);
    }

    /** 땅 높이로 내린 자리 */
    private static Vec3 ground(ServerLevel level, Vec3 p) {
        return new Vec3(p.x, RuneKit.groundAt(level, V.up(p, 0.6), p.y), p.z);
    }

    /** 앞으로 뻗는 균열 한 갈래 (조각마다 그 자리에 닿는 때 — 어두운 틈 · 직업 빛 · 흰 금, 땅에 눕힘) · 튀는 돌 · 흙먼지. 닿는 데 걸리는 틱은 speed 칸/초 */
    static void rift(ServerLevel level, Vec3 g, Vec3 f, double len, double[] glow, double width, double speed) {
        double piece = 1.8;
        double spin = Math.toDegrees(Math.atan2(f.z, -f.x));
        for (double s = 0.3; s < len; s += piece) {
            double a0 = s;
            double a1 = Math.min(len, s + piece * 1.3);
            int at = (int) Math.round(a0 / speed * 20);
            RuneKit.later(at, () -> {
                Vec3 mid = V.add(g, f, (a0 + a1) / 2);
                Vec3 m = new Vec3(mid.x, RuneKit.groundAt(level, mid, g.y), mid.z);
                double size = (a1 - a0) / 2 * 1.3 * width;
                Opt base = Opt.of("dir", V.UP, "size", size, "life", 1.8, "fade", 0.35, "spin", spin + (Kit.rand() < 0.5 ? 180 : 0));
                RuneKit.rfx(level, "fissure", V.up(m, 0.035), base.with("cell", 0.0, "color", RIFT, "alpha", 0.92));
                RuneKit.rfx(level, "fissure", V.up(m, 0.055), base.with("cell", 1.0, "color", glow, "alpha", 0.95));
                RuneKit.rfx(level, "fissure", V.up(m, 0.075), base.with("cell", 2.0, "color", WHITE, "alpha", 1.0));
                RuneKit.rockBurst(level, V.up(m, 0.15), 2, 3.2, 0.15);
                SkillFx.sparks(level, V.up(m, 0.15), DUST, 4, 2.6);
            });
        }
    }

    /** 내려찍은 자리: 갈라지는 땅 (전사의 rfx_jquake) · 터짐 · 튀는 돌 · 불티 · 흙먼지 · 무거운 소리 (화면은 흔들지 않음) */
    private static void slamFx(ServerPlayer sp, Jobs.Job j, Vec3 g, double radius, double scale) {
        ServerLevel level = sp.level();
        JobFx.smash(level, V.up(g, 0.05), radius, j, 0.5 + 0.1 * scale, Opt.empty());
        JobFx.burst(level, V.up(g, 0.5), 1.0 * scale, j, 0.32, Opt.empty());
        JobFx.two(level, "jimpact", JobImpact.IMPACT, V.up(g, 0.6), 0.3, Opt.of("size", 1.2 * scale, "spin", Kit.rand() * 360, "fade", 0.25), JobFx.outer(j), WHITE);
        RuneKit.rockBurst(level, V.up(g, 0.2), (int) Math.round(7 * scale), 5.0, 0.17 * scale);
        SkillFx.sparks(level, V.up(g, 0.3), JobKit.glow(j), (int) Math.round(10 * scale), 5.5);
        SkillFx.sparks(level, V.up(g, 0.2), DUST, (int) Math.round(9 * scale), 3.4);
        SkillFx.play(level, "mace.heavy_smash_ground", g, 0.8, 0.95);
        SkillFx.play(level, "random.anvil_land", g, 0.45, 0.5);
    }

    // ------------------------------------------------------------ 대지 가르기

    /** 모으는 중: 쓴 사람 → {모으기 시작 틱, 목숨, 내려침 (true 면 그만)} */
    private record Charge(long start, String life, boolean[] done) {}

    private static final Map<UUID, Charge> CHARGES = new HashMap<>();

    /**
     * 대지 가르기 (롤 사이온 Q): 처음 누름 — 도끼를 오른쪽 뒤 땅에 끌며 힘을 모음 (charge 틱까지 · 느려짐 · 밀려나지 않음 — 도끼 끝의 불티 · 앞 땅에 모은 만큼
     * 길어지는 빛 금). gap 틱 뒤부터 다시 누르면 (JobRecast) 그 자리에서 내려침, 다시 누르지 않으면 charge 틱에 저절로 (끝까지 모음). 내려친 slamAt 틱 뒤 앞 땅이
     * 갈라짐: 길이 len[0] → len[1] · 피해 total × (minPower → 1) 를 모은 몫만큼 (폭 width — 가까운 2칸은 바로, 그 너머는 갈라짐이 닿는 때), 끝까지 모았으면
     * 갈라진 땅에서 돌창 (rfx_jspike) 이 솟아 맞은 적을 띄움 (lift)
     */
    static boolean rend(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int charge = Math.max(8, (int) k.num("charge", 24));
        if (JobRecast.stage(sp, k.id()) >= 1) {
            Charge c = CHARGES.get(sp.getUUID());
            JobRecast.close(sp, k.id());
            JobRecast.motion(sp, k.id() + ":slam");
            int held = c == null ? charge : (int) Math.min(charge, JobKit.now() - c.start());
            release(sp, j, k, P, held, charge);
            return true;
        }
        String life = RuneKit.lifeToken(sp);
        boolean[] done = {false};
        CHARGES.put(sp.getUUID(), new Charge(JobKit.now(), life, done));
        RuneKit.effect(sp, "slowness", charge + 2, 2);
        RpgCombat.setSteadfast(sp, charge + 8);
        ServerLevel level0 = sp.level();
        SkillFx.play(level0, "item.armor.equip_iron", sp.position(), 0.7, 0.8);
        SkillFx.play(level0, "entity.polar_bear.warning", sp.position(), 0.6, 0.5);
        double len0 = lenAt(k, 0);
        double len1 = lenAt(k, 1);
        JobFx.charge(sp, j, new Vec3(0.75, 0.35, -0.55), charge, 0.75); // 0.51.0 모으는 동안 도끼날에 빛이 빨려 들며 커짐 (사용자 "크게 감는 동작")
        // 모으는 동안 (2틱마다): 도끼 끝이 땅을 긁는 불티 · 흙먼지 · 앞 땅에 모은 만큼 길어지는 빛 금 (4틱마다 — 다 모이면 번쩍) · 차오르는 소리
        for (int t = 1; t <= charge; t++) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (done[0] || !RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 f = Kit.facingOf(sp);
                Vec3 axe = V.up(V.add(V.add(sp.position(), V.rightOf(f), 0.75), f, -0.55), 0.1);
                if (tt % 2 == 0) {
                    SkillFx.fx(lv, "rpg:arrow_spark", axe, Opt.of("dir", V.unit(V.add(V.UP, f, -0.6)), "speed", 2.6, "size", 0.14, "grow", 0.6, "life", 0.35, "color",
                            JobKit.glow(j), "alpha", 1.0));
                    SkillFx.sparks(lv, axe, DUST, 2, 1.6);
                }
                if (tt % 4 == 0 || tt == charge) {
                    double c = Math.min(1, tt / (double) charge);
                    double len = len0 + (len1 - len0) * c;
                    Vec3 g = groundAhead(sp.position(), f, 0.8, lv);
                    double spin = Math.toDegrees(Math.atan2(f.z, -f.x));
                    for (double s = 0.6; s < len; s += 1.6) {
                        Vec3 m = ground(lv, V.add(g, f, s));
                        RuneKit.rfx(lv, "fissure", V.up(m, 0.05), Opt.of("dir", V.UP, "size", 1.05, "life", 0.3, "fade", 0.5, "spin", spin, "cell", 1.0, "color",
                                JobKit.glow(j), "alpha", 0.35 + 0.45 * c));
                    }
                    SkillFx.play(lv, "block.grindstone.use", sp.position(), 0.5 + 0.6 * c, 0.35);
                    if (tt == charge) {
                        JobFx.glint(lv, V.up(axe, 0.3), 0.8, j, 0.35, Opt.empty());
                        JobFx.aura(sp, j, 2.6, 0.45, 0.9);
                        SkillFx.play(lv, "block.anvil.land", sp.position(), 1.5, 0.45);
                    }
                }
            });
        }
        JobRecast.open(sp, k, 1, charge, (int) k.num("gap", 6), () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            JobKit.motion(sp, k.id() + ":slam");
            release(sp, j, k, P, charge, charge);
        });
        JobSystem.note(sp, "§e다시 누르면 내려침 §7(끝까지 모으면 " + charge / 20.0 + "초 · 돌창)");
        return true;
    }

    /** 모은 몫 c (0 ~ 1) 의 길이 (len[0] → len[1]) */
    private static double lenAt(Jobs.Skill k, double c) {
        List<Double> l = SwordmasterSkills.numbers(k, "len", List.of(4.0, 9.0));
        double a = l.get(0);
        double b = l.size() > 1 ? l.get(1) : a;
        return a + (b - a) * Math.max(0, Math.min(1, c));
    }

    /** 내려침 (held 틱 모음): slamAt 틱에 앞 땅이 갈라짐 — 모은 만큼 길고 셈 · 끝까지 모았으면 돌창 · 띄움 */
    private static void release(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, int held, int charge) {
        Charge ch = CHARGES.remove(sp.getUUID());
        if (ch != null) ch.done()[0] = true;
        sp.removeEffect(MobEffects.SLOWNESS);
        String life = RuneKit.lifeToken(sp);
        double c = Math.max(0, Math.min(1, held / (double) charge));
        boolean full = held >= charge;
        double frac = k.num("minPower", 0.5) + (1 - k.num("minPower", 0.5)) * c;
        double len = lenAt(k, c);
        int slamAt = Math.max(2, (int) k.num("slamAt", 4));
        // 도끼를 머리 위로 (2틱): 눈앞 머리 위 반원 · 바람
        RuneKit.later(Math.max(1, slamAt - 2), () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.slash(sp, j, 1.9, -0.75, 1.3, 180.0, 0.16);
            SkillFx.play(sp.level(), "entity.player.attack.sweep", sp.position(), 0.6, 0.7);
        });
        RuneKit.later(slamAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel level = sp.level();
            Vec3 f = Kit.facingOf(sp);
            Vec3 g = groundAhead(sp.position(), f, 1.2, level);
            double width = k.num("width", 2.2);
            JobFx.slash(sp, j, 2.3, 0.2, 2.4, 95.0, 0.26);
            JobFx.slashCard(level, V.up(V.add(sp.position(), f, 1.1), 1.2), f, 95.0, 3.4, 0.3, V.ZERO, j, JobFx.hide1p(sp));
            slamFx(sp, j, g, 2.0 + c, 1.0 + 0.6 * c);
            double free = Math.max(2, RuneKit.rayLength(level, V.up(g, 0.6), f, len));
            JobFx.groundWave(level, sp, g, f, 1.5 + 0.9 * c, free, SPLIT_SPEED, j); // 0.51.0 갈라짐 앞머리를 달리는 세로 도끼 검기 (모은 만큼 크게)
            rift(level, g, f, free, JobKit.glow(j), 0.9 + 0.5 * c, SPLIT_SPEED);
            if (full) {
                // 끝까지 모음: 갈라진 땅을 따라 돌창이 솟음 (닿는 때마다 — 크게 · 1.5칸마다 엇갈려)
                int[] side = {Kit.rand() < 0.5 ? 1 : -1};
                for (double s = 1.4; s < free; s += 1.3) {
                    double ss = s;
                    double off = (side[0] = -side[0]) * (0.35 + Kit.rand() * 0.25);
                    RuneKit.later((int) Math.round(Math.max(0, s - 2) / SPLIT_SPEED * 20), () -> {
                        Vec3 p = ground(level, V.add(V.add(g, f, ss), V.rightOf(f), off));
                        JobFx.spikeLife(level, p, 1.2 + Kit.rand() * 0.3, 0.12, 0.8, Opt.empty(), ROCK, JobKit.glow(j));
                        SkillFx.play(level, "block.pointed_dripstone.land", p, 0.6, 0.6);
                    });
                }
                Ult.flash(level, g, JobKit.glow(j), Opt.of("radius", 5.0, "time", new double[] {0.02, 0.04, 0.25}), sp);
                SkillFx.play(level, "item.mace.smash_ground_heavy", g, 0.7, 0.9);
            }
            int[] cnt = {0};
            for (Kit.LineHit h : RuneKit.lineTargets(sp, V.up(g, 0.6), f, free + 0.4, width / 2)) {
                LivingEntity e = h.e();
                int delay = (int) Math.round(Math.max(0, h.t() - 2) / SPLIT_SPEED * 20);
                JobKit.strike(sp, e, j, P, k.total() * frac, false, false, life, delay, d -> {
                    JobImpact.hit(sp, e, j, full ? 1.7 : 1.2 + 0.3 * c);
                    if (full && JobKit.pushable(e)) Kit.launch(e, 0, k.num("lift", 0.9), 0);
                    if (cnt[0]++ < 6 && e.level() instanceof ServerLevel lv) RuneKit.rockBurst(lv, V.up(e.position(), 0.2), 3, 3.5, 0.15);
                });
            }
        });
    }

    // ------------------------------------------------------------ 동면

    /**
     * 동면 (롤 알리스타 R): dur 틱 웅크려 곰의 잠에 듦 — 받는 피해 −guard (Guards) · 밀려나지 않음 · 느려짐 · 받은 피해 (최대 체력 1%) 마다 분노 + rage (JobTraits6) →
     * dur 틱에 깨어나며 둘레 radius 칸 (total — 밀쳐냄 knock). 그림: 둘레에 솟은 돌창 굴 (rfx_jspike 여섯) · 몸을 감싸 웅크린 곰의 혼 · 숨 쉴 때마다 흙먼지 → 깨어날 때
     * 굴이 무너지고 둘레의 땅이 터져 돌창이 바깥으로 솟음
     */
    static boolean slumber(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = Math.max(10, (int) k.num("dur", 40));
        String life = RuneKit.lifeToken(sp);
        JobTraits6.slumberStart(sp, dur);
        Guards.set(sp, dur, k.num("guard", 0.6), null, JobTraits6::slumbering);
        RpgCombat.setSteadfast(sp, dur + 4);
        RuneKit.effect(sp, "slowness", dur, 2);
        ServerLevel level = sp.level();
        Vec3 c0 = sp.position();
        // 굴: 둘레 1.6칸에 돌창 여섯이 솟아 dur 동안 서 있음 (돌 몸 · 주황 틈)
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(i * 60 + 30);
            Vec3 p = ground(level, new Vec3(c0.x + Math.cos(a) * 1.6, c0.y, c0.z + Math.sin(a) * 1.6));
            JobFx.spikeLife(level, p, 0.7, 0.2, dur / 20.0 - 0.2, Opt.empty(), ROCK, JobKit.glow(j));
        }
        JobFx.ring(level, V.up(c0, 0.06), 2.0, j, false, 0.5, Opt.empty());
        SkillFx.sparks(level, V.up(c0, 0.2), DUST, 12, 3);
        SkillFx.play(level, "entity.polar_bear.ambient_baby", c0, 0.6, 0.8);
        SkillFx.play(level, "block.rooted_dirt.place", c0, 0.6, 1.0);
        // 웅크린 곰의 혼 (3인칭 · 다른 사람 — 등을 덮음) · 숨 (8틱마다 흙먼지 · 낮은 숨소리)
        for (int t = 2; t < dur; t += 8) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 back = V.up(V.add(sp.position(), Kit.facingOf(sp), -0.3), 1.1);
                JobFx.bear(lv, back, 1.45, 0.46, 3, 4, true, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.5, "coreAlpha", 0.85),
                        JobTraits6.bearGlow(j), JobTraits6.bearCore(j));
                SkillFx.sparks(lv, V.up(sp.position(), 0.15), DUST, 4, 1.6);
                if (tt % 16 == 2) SkillFx.play(lv, "entity.polar_bear.ambient", sp.position(), 0.5, 0.5);
            });
        }
        RuneKit.later(dur, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            wake(sp, j, k, P, life);
        });
        return true;
    }

    /** 깨어남: 둘레 radius 칸 터짐 (total · 밀쳐냄) — 돌창이 바깥으로 솟고 · 땅이 갈라지고 · 곰이 포효 */
    private static void wake(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 g = ground(level, c);
        double r = k.num("radius", 4.5);
        slamFx(sp, j, g, r, 1.4);
        JobFx.ring(level, V.up(g, 0.08), r, j, false, 0.45, Opt.of("alpha", 1.0));
        SkillFx.shock(level, V.up(g, 0.1), JobFx.inner(j), r, 0.5);
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 + Kit.rand() * 20);
            Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
            int ii = i;
            RuneKit.later(1 + i % 2, () -> {
                Vec3 p = ground(level, V.add(g, d, 2.2 + (ii % 2) * 1.1));
                JobFx.spikeLife(level, p, 0.95, 0.12, 0.4, Opt.empty(), ROCK, JobKit.glow(j));
            });
            rift(level, g, d, r, JobKit.glow(j), 0.8, 30);
        }
        JobFx.bear(level, V.up(V.add(c, Kit.facingOf(sp), -0.5), 1.8), 2.2, 0.8, 3, 5, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha", 0.9),
                JobTraits6.bearGlow(j), JobTraits6.bearCore(j));
        JobFx.eyeRing(sp, j, 2.3, 1.1, 0.45);
        SkillFx.play(level, "entity.polar_bear.warning", c, 0.6, 1.3);
        SkillFx.play(level, "mob.ravager.roar", c, 0.9, 0.8);
        for (LivingEntity e : Kit.around(sp, g, r, 2.5)) {
            JobKit.strike(sp, e, j, P, k.total(), false, false, life, 0, d -> {
                JobImpact.hit(sp, e, j, 1.4);
                if (JobKit.pushable(e)) RuneKit.knock(e, V.sub(e.position(), c), k.num("knock", 1.0), 0.4);
            });
        }
    }

    // ------------------------------------------------------------ 곰의 포옹

    /**
     * 곰의 포옹 (롤 세트 E): 곰의 두 앞발 (rfx_jpaw — 3인칭 · 다른 사람은 양옆 위에서, 1인칭은 눈앞 양옆에서) 이 둘레 reach 칸의 적을 움켜쥐어 pullAt 틱부터
     * pullTicks 틱에 내 앞 ahead 칸으로 끌어옴 (왼쪽 · 오른쪽 무리가 가운데에서 부딪힘 — 밀 수 있는 적만) → slamAt 틱에 그 자리를 내려찍음 (total — 둘레 radius 칸).
     * 왼쪽 · 오른쪽 모두에서 끌려온 적이 있으면 끌려온 적 stun 틱 기절 (보스 제외)
     */
    static boolean hug(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 r = V.rightOf(f);
        Vec3 gather = groundAhead(c, f, k.num("ahead", 1.8), level);
        int pullAt = Math.max(1, (int) k.num("pullAt", 2));
        int pullTicks = Math.max(2, (int) k.num("pullTicks", 6));
        int slamAt = Math.max(pullAt + pullTicks + 2, (int) k.num("slamAt", 12));
        List<LivingEntity> left = new ArrayList<>();
        List<LivingEntity> right = new ArrayList<>();
        for (LivingEntity e : Kit.around(sp, c, k.num("reach", 6), 2.5)) {
            if (V.dot(V.flat(V.sub(e.position(), c)), r) < 0) left.add(e);
            else right.add(e);
        }
        boolean both = !left.isEmpty() && !right.isEmpty();
        RpgCombat.setSteadfast(sp, slamAt + 6);
        // 앞발 둘 (3인칭 · 다른 사람): 양옆 4칸 위에서 가운데로 휩쓸어 모임 · 1인칭: 눈앞 양옆에서
        double life0 = (pullAt + pullTicks) / 20.0;
        for (int s = -1; s <= 1; s += 2) {
            Vec3 from = V.up(V.add(V.add(c, r, 4.2 * s), f, 1.2), 1.6);
            Vec3 to = V.up(V.add(gather, r, 0.6 * s), 1.1);
            Vec3 dir = V.unit(V.sub(to, from));
            double speed = V.len(V.sub(to, from)) / life0;
            JobFx.paw(level, from, 1.5, life0 + 0.08, JobFx.hide1p(sp).with("dir", dir, "speed", speed, "alpha", 0.95, "spin", s * 70.0), JobTraits6.bearGlow(j),
                    JobTraits6.bearCore(j));
            Vec3 look = sp.getViewVector(1F);
            Vec3 eye = sp.getEyePosition();
            Vec3 sr = V.rightOf(V.flat(look));
            Vec3 a1 = V.add(V.add(eye, look, 2.6), sr, 2.2 * s);
            Vec3 a2 = V.add(V.add(eye, look, 2.6), sr, 0.35 * s);
            JobFx.paw(level, a1, 0.9, life0 + 0.05, JobFx.only1p(sp).with("dir", V.unit(V.sub(a2, a1)), "speed", V.len(V.sub(a2, a1)) / life0, "alpha", 0.95, "spin",
                    s * 70.0, "vel", RuneKit.bodyVel(sp)), JobTraits6.bearGlow(j), JobTraits6.bearCore(j));
        }
        // 곰의 혼은 끌어모으는 자리 위 (3인칭 뒤 카메라와 몸 사이에 두면 화면을 덮음 — 영상 1)
        JobFx.bear(level, V.up(V.add(c, f, 1.8), 3.0), 2.0, (slamAt + 6) / 20.0, 0, JobFx.BEAR - 1, false, JobFx.hide1p(sp).with("vel", RuneKit.bodyVel(sp), "alpha",
                0.8), JobTraits6.bearGlow(j), JobTraits6.bearCore(j));
        SkillFx.play(level, "entity.polar_bear.warning", c, 0.8, 1.0);
        SkillFx.play(level, "item.trident.riptide_1", c, 0.7, 0.8);
        // 끌어옴 (pullAt ~ pullAt + pullTicks): 왼쪽 무리는 가운데 왼쪽으로 · 오른쪽 무리는 오른쪽으로 (부딪히는 자리) — 끌리는 땅의 흙먼지
        List<LivingEntity> pulled = new ArrayList<>();
        for (List<LivingEntity> side : List.of(left, right)) {
            double s = side == left ? -1 : 1;
            side.sort(java.util.Comparator.comparingDouble(x -> x.distanceToSqr(sp)));
            int idx = 0;
            for (LivingEntity e : side) {
                if (!JobKit.pushable(e)) continue;
                pulled.add(e);
                Vec3 start = e.position();
                // 가까운 적부터 가운데 · 뒤의 적은 바깥으로 조금씩 엇갈려 (몸 · 숫자가 한 자리에 겹치지 않게 — 내려찍는 둘레 안)
                int i = idx++;
                Vec3 end = V.add(V.add(gather, r, s * Math.min(2.6, 0.45 + Kit.bodyPad(e) + 0.85 * i)), f, i == 0 ? 0 : (i % 2 == 1 ? 0.8 : -0.8));
                for (int t = 1; t <= pullTicks; t++) {
                    int tt = t;
                    RuneKit.later(pullAt + t, () -> {
                        if (!RuneKit.sameLife(sp, life) || e.isRemoved() || !e.isAlive()) return;
                        double u = tt / (double) pullTicks;
                        u = u * u * (3 - 2 * u);
                        Vec3 p = V.lerp(start, end, u);
                        Vec3 stand = Kit.standSpot(e.level(), new Vec3(p.x, Math.max(p.y, start.y) + 0.5, p.z), 1.5);
                        if (stand != null) e.teleportTo(stand.x, stand.y, stand.z);
                        e.setDeltaMovement(Vec3.ZERO);
                        if (tt % 2 == 0 && e.level() instanceof ServerLevel lv) SkillFx.sparks(lv, V.up(e.position(), 0.1), DUST, 3, 1.8);
                    });
                }
            }
        }
        // 부딪힘 (끌어온 틱): 가운데 흰 섬광 · 쿵 소리
        RuneKit.later(pullAt + pullTicks, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 m = V.up(gather, 1.0);
            JobFx.two(lv, "jimpact", JobImpact.IMPACT, m, 0.28, Opt.of("size", 1.4, "spin", Kit.rand() * 360, "fade", 0.25), JobFx.outer(j), WHITE);
            SkillFx.sparks(lv, m, JobFx.inner(j), 10, 4);
            SkillFx.play(lv, "entity.player.attack.knockback", m, 0.7, 1.0);
            SkillFx.play(lv, "block.anvil.land", m, 0.8, 0.45);
        });
        // 머리 위로 (slamAt − 3): 눈앞 머리 위 반원
        RuneKit.later(slamAt - 3, () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.slash(sp, j, 1.9, -0.75, 1.3, 180.0, 0.16);
        });
        int stun = JobUpgrades.ticks(sp, k, "stun", (int) k.num("stun", 20));
        RuneKit.later(slamAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 ff = Kit.facingOf(sp);
            double rr = k.num("radius", 3.2);
            JobFx.slash(sp, j, 2.3, 0.15, 2.4, 95.0, 0.26);
            JobFx.slashCard(lv, V.up(V.add(sp.position(), ff, 1.1), 1.2), ff, 95.0, 3.4, 0.3, V.ZERO, j, JobFx.hide1p(sp));
            slamFx(sp, j, gather, rr, 1.5);
            // 0.51.0 내려찍은 자리에서 큰 도끼 검기 X (가운데 — 둘 · 맞은 무리 위)
            JobFx.bigWave(lv, null, V.up(gather, 1.2), ff, 0, 2.2, 90 + 30, 0.32, j, Opt.empty());
            JobFx.bigWave(lv, null, V.up(gather, 1.2), ff, 0, 2.2, 90 - 30, 0.32, j, Opt.empty());
            for (int i = 0; i < 6; i++) {
                double a = Math.toRadians(i * 60 + 15);
                JobFx.spikeLife(lv, ground(lv, V.add(gather, new Vec3(Math.cos(a), 0, Math.sin(a)), rr * 0.8)), 0.75, 0.12, 0.35, Opt.empty(), ROCK, JobKit.glow(j));
            }
            for (LivingEntity e : Kit.around(sp, gather, rr, 2.5)) {
                JobKit.strike(sp, e, j, P, k.total(), false, false, life, 0, d -> {
                    JobImpact.hit(sp, e, j, 1.7);
                    if (both && pulled.contains(e) && !JobKit.isBoss(e)) {
                        RuneKit.stun(e, stun, JobFx.outer(j), true);
                    }
                });
            }
            if (both && !pulled.isEmpty()) Notify.notify(sp, "§6곰의 포옹 §7— 양쪽에서 끌려온 적 기절 " + stun / 20.0 + "초", 20);
        });
        return true;
    }

    // ------------------------------------------------------------ 거인의 무게

    /**
     * 거인의 무게 (롤 말파이트 W): dur 틱 태세 — 도끼 평타 + power · 공격 속도 × (1 − slow) · 범위 ×1.6 (SWING_LOOK warlord_titan) · 밀려나지 않음 (JobTraits6).
     * 쓸 때 한 발을 높이 들어 땅을 구름 (6틱 — 둘레가 갈라지고 돌창 넷)
     */
    static boolean titan(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int dur = JobUpgrades.ticks(sp, k, "dur", (int) k.num("dur", 200));
        JobTraits.startStance(sp, k, dur);
        RpgCombat.setSteadfast(sp, dur);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level = sp.level();
        JobFx.aura(sp, j, 3.2, 0.8, 0.9);
        SkillFx.play(level, "block.stone.break", sp.position(), 0.5, 0.9);
        RuneKit.later(6, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            ServerLevel lv = sp.level();
            Vec3 g = ground(lv, sp.position());
            JobFx.smash(lv, V.up(g, 0.05), 3.0, j, 0.55, Opt.empty());
            SkillFx.shock(lv, V.up(g, 0.1), JobFx.inner(j), 3.0, 0.4);
            for (int i = 0; i < 4; i++) {
                double a = Math.toRadians(i * 90 + 45);
                JobFx.spikeLife(lv, ground(lv, V.add(g, new Vec3(Math.cos(a), 0, Math.sin(a)), 1.8)), 0.6, 0.12, 0.45, Opt.empty(), ROCK, JobKit.glow(j));
            }
            RuneKit.rockBurst(lv, V.up(g, 0.2), 8, 4.0, 0.16);
            SkillFx.sparks(lv, V.up(g, 0.2), DUST, 12, 3.2);
            SkillFx.play(lv, "mace.heavy_smash_ground", g, 0.6, 1.0);
            SkillFx.play(lv, "entity.iron_golem.attack", g, 0.6, 0.8);
        });
        return true;
    }

    /** 거인의 무게 동안 휘두를 때마다 (JobTraits6.onSwing): 앞 부채꼴의 땅이 세 갈래로 갈라지고 끝에서 돌창 둘 · 돌이 튐 · 낮은 땅울림 */
    static void titanQuake(ServerPlayer sp, Jobs.Job j) {
        ServerLevel level = sp.level();
        Vec3 f = Kit.facingOf(sp);
        Vec3 g = groundAhead(sp.position(), f, 0.9, level);
        for (int s = -1; s <= 1; s++) {
            Vec3 d = V.turnY(f, s * 24.0);
            rift(level, g, d, 3.6 + (s == 0 ? 0.8 : 0), JobKit.glow(j), 0.85, 50);
        }
        JobFx.two(level, "jquake", JobFx.QUAKE, V.up(V.add(g, f, 1.6), 0.05), 0.5, Opt.of("dir", V.UP, "size", 1.9 / 0.92, "spin", Kit.rand() * 360, "alpha", 0.9),
                JobFx.outer(j), JobFx.inner(j));
        // 갈라짐 끝에서 돌창 둘이 솟음 (가운데 · 엇갈려)
        for (int i = 0; i < 2; i++) {
            Vec3 p = ground(level, V.add(V.add(g, f, 2.6 + i * 1.0), V.rightOf(f), i == 0 ? -0.4 : 0.45));
            int d = 1 + i;
            RuneKit.later(d, () -> JobFx.spikeLife(level, p, 0.85 + Kit.rand() * 0.2, 0.1, 0.35, Opt.empty(), ROCK, JobKit.glow(j)));
        }
        RuneKit.rockBurst(level, V.up(V.add(g, f, 2.2), 0.2), 4, 3.6, 0.15);
        SkillFx.sparks(level, V.up(V.add(g, f, 1.8), 0.2), DUST, 6, 2.8);
        SkillFx.play(level, "mace.smash_ground", g, 0.6, 0.55);
    }

    // ------------------------------------------------------------ 마지막 휘두름

    /**
     * 마지막 휘두름 (롤 다리우스 Q — 바깥 날): windup 틱 도끼를 뒤로 크게 젖혀 힘을 모음 (받는 피해 −guard · 밀려나지 않음 · 느려짐 — 어스름한 하늘 · 둘레 땅의
     * 바깥 고리 (edge ~ radius 칸) 가 달아오르고 · 흙먼지 · 돌이 빨려 들며 · 등 뒤 곰의 혼이 솟음) → 둘레 radius 칸을 한 바퀴 크게 휘두름: 바깥 고리의 적은 도끼날 (outer ·
     * 띄움 lift — 큰 숫자), 안쪽은 자루 (inner). 바깥 고리에서 돌창이 솟고 땅이 터짐
     */
    static boolean ult(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P) {
        int windup = Math.max(10, (int) k.num("windup", 24));
        double R = k.num("radius", 8);
        double edge = k.num("edge", 5);
        String life = RuneKit.lifeToken(sp);
        ServerLevel level0 = sp.level();
        Vec3 c0 = sp.position();
        Guards.set(sp, windup + 2, k.num("guard", 0.5), null, null);
        RpgCombat.setSteadfast(sp, windup + 12);
        RuneKit.effect(sp, "slowness", windup, 3);
        Ult.sky(level0, c0, "rpg:ult_ash", windup + 34);
        JobFx.eyeRing(sp, j, 2.4, 1.1, 0.5);
        SkillFx.play(level0, "entity.polar_bear.warning", c0, 0.5, 1.2);
        SkillFx.play(level0, "mob.enderdragon.growl", c0, 1.4, 0.4);
        // 앞 2.6칸 위에서 곰의 혼이 솟음 (모여 이룸 → 포효를 이어 돌림 — 몸 뒤에 두면 3인칭 뒤 카메라와 몸 사이라 화면을 덮음, 영상 1)
        JobFx.bear(level0, V.up(V.add(c0, Kit.facingOf(sp), 2.6), 3.2), 2.6, windup / 20.0 * 0.4, 0, 3, false, JobFx.hide1p(sp).with("alpha", 0.9),
                JobTraits6.bearGlow(j), JobTraits6.bearCore(j));
        RuneKit.later((int) Math.round(windup * 0.4), () -> {
            if (RuneKit.sameLife(sp, life)) JobFx.bear(sp.level(), V.up(V.add(sp.position(), Kit.facingOf(sp), 2.6), 3.2), 2.6, windup * 0.6 / 20.0 + 0.3, 3, 4, true,
                    JobFx.hide1p(sp).with("alpha", 0.9, "vel", RuneKit.bodyVel(sp)), JobTraits6.bearGlow(j), JobTraits6.bearCore(j));
        });
        // 모음 (3틱마다): 바깥 고리의 달아오르는 금 (돌아가며) · 안쪽 경계의 옅은 금 · 빨려 드는 흙먼지 · 손의 불티 · 차오르는 땅울림
        for (int t = 0; t < windup; t += 3) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                ServerLevel lv = sp.level();
                Vec3 c = sp.position();
                double q = tt / (double) windup;
                int n = 14;
                for (int i = 0; i < n; i++) {
                    double a = Math.toRadians(i * 360.0 / n + tt * 9);
                    Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
                    Vec3 p = ground(lv, V.add(c, dir, (edge + R) / 2));
                    double spin = Math.toDegrees(Math.atan2(-dir.x, -dir.z));
                    RuneKit.rfx(lv, "fissure", V.up(p, 0.05), Opt.of("dir", V.UP, "size", 1.25, "life", 0.32, "fade", 0.5, "spin", spin + 90, "cell", 1.0, "color",
                            JobKit.glow(j), "alpha", 0.35 + 0.6 * q));
                    if (i % 2 == 0) {
                        Vec3 pe = ground(lv, V.add(c, dir, edge));
                        RuneKit.rfx(lv, "fissure", V.up(pe, 0.04), Opt.of("dir", V.UP, "size", 0.7, "life", 0.32, "fade", 0.5, "spin", spin + 90, "cell", 2.0,
                                "color", JobFx.inner(j), "alpha", 0.25 + 0.3 * q));
                    }
                }
                for (int i = 0; i < 3; i++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
                    Vec3 p = V.up(V.add(c, dir, R * (0.6 + Kit.rand() * 0.4)), 0.2 + Kit.rand() * 0.6);
                    SkillFx.fx(lv, "rpg:arrow_spark", p, Opt.of("dir", V.unit(V.add(V.scale(dir, -1), V.UP, 0.15)), "speed", 6.0 + 4 * q, "size", 0.14, "grow", 0.5,
                            "life", 0.4, "color", tt % 2 == 0 ? DUST : JobKit.glow(j), "alpha", 1.0));
                }
                RuneKit.Hand h = RuneKit.handOf(sp);
                SkillFx.fx(lv, "rpg:arrow_spark", V.up(h.hand(), 0.1), Opt.of("dir", V.UP, "speed", 1.8, "size", 0.15, "grow", 0.8, "life", 0.35, "color",
                        JobKit.glow(j), "alpha", 1.0));
                if (tt % 6 == 0) SkillFx.play(lv, "block.grindstone.use", c, 0.4 + 0.5 * q, 0.4);
            });
        }
        JobFx.charge(sp, j, new Vec3(-0.9, 0.55, -0.55), windup, 1.0); // 0.51.0 왼쪽 뒤로 감은 도끼날에 빛이 빨려 들며 커짐
        RuneKit.later(windup - 4, () -> {
            if (RuneKit.sameLife(sp, life)) SkillFx.play(sp.level(), "mob.ravager.roar", sp.position(), 0.7, 1.0);
        });
        RuneKit.later(windup, () -> {
            if (RuneKit.sameLife(sp, life)) sweep(sp, j, k, P, life);
        });
        return true;
    }

    /** 한 바퀴 (windup 틱): 허리 높이 거대한 도끼 고리 둘 · 1인칭 눈앞 큰 가로 베기 둘 · 바깥 고리의 돌창 · 터짐 · 번쩍임 — 바깥 outer · 띄움 / 안쪽 inner */
    private static void sweep(ServerPlayer sp, Jobs.Job j, Jobs.Skill k, double P, String life) {
        ServerLevel level = sp.level();
        Vec3 c = sp.position();
        Vec3 g = ground(level, c);
        double R = k.num("radius", 8);
        double edge = k.num("edge", 5);
        double mid = (edge + R) / 2;
        JobFx.ring(level, V.up(c, 1.0), R, j, false, 0.4, Opt.of("alpha", 1.0));
        JobFx.ring(level, V.up(c, 0.9), mid, j, false, 0.35, Opt.of("alpha", 0.8, "spin", 40.0));
        JobFx.ring(level, V.up(c, 1.1), edge * 0.7, j, false, 0.3, Opt.of("alpha", 0.6, "spin", 80.0));
        JobFx.slash(sp, j, 2.4, 0.15, 3.4, -4.0, 0.3);
        RuneKit.later(2, () -> JobFx.slash(sp, j, 2.4, 0.15, 3.0, 176.0, 0.26));
        // 0.51.0 둘레로 퍼지는 거대한 도끼 검기 열둘 (사용자 "커다란 검기가 나오면서 때리면" — 한 바퀴 휘두른 날이 바깥 고리까지 뻗어 나감)
        JobFx.radialWaves(sp, j, c, 12, 1.4, R, 1.1, 2.0, 0.24, 15);
        SkillFx.shock(level, V.up(g, 0.1), JobFx.inner(j), R, 0.6);
        int n = 12;
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians(i * 360.0 / n + Kit.rand() * 12);
            Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
            int ii = i;
            RuneKit.later(i % 3, () -> {
                Vec3 p = ground(level, V.add(g, d, mid + (ii % 2 == 0 ? -0.6 : 0.6)));
                JobFx.spikeLife(level, p, 1.15 + Kit.rand() * 0.3, 0.12, 0.7, Opt.empty(), ROCK, JobKit.glow(j));
                if (ii % 2 == 0) JobFx.two(level, "jquake", JobFx.QUAKE, V.up(p, 0.05), 0.6, Opt.of("dir", V.UP, "size", 1.4 / 0.92, "spin", Kit.rand() * 360),
                        JobFx.outer(j), JobFx.inner(j));
                RuneKit.rockBurst(level, V.up(p, 0.3), 3, 4.5, 0.18);
            });
        }
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45 + 22.5);
            rift(level, V.add(g, new Vec3(Math.cos(a), 0, Math.sin(a)), edge), new Vec3(Math.cos(a), 0, Math.sin(a)), R - edge + 0.5, JobKit.glow(j), 0.8, 40);
        }
        RuneKit.godRays(sp, level, g, R * 0.7, 8, JobKit.glow(j), JobKit.core(j), Opt.of("h", 7.0, "life", 0.7, "alpha", 0.7, "skipNear", true));
        Ult.flash(level, c, JobKit.glow(j), Opt.empty(), sp);
        SkillFx.sparks(level, V.up(g, 0.6), JobKit.glow(j), 20, 7);
        SkillFx.play(level, "entity.player.attack.sweep", c, 0.5, 1.0);
        SkillFx.play(level, "item.mace.smash_ground_heavy", c, 0.6, 1.0);
        SkillFx.play(level, "random.explode", c, 0.8, 0.7);
        SkillFx.play(level, "entity.polar_bear.warning", c, 0.5, 1.2);
        for (LivingEntity e : Kit.around(sp, g, R, 3.0)) {
            double d = Math.hypot(e.getX() - c.x, e.getZ() - c.z);
            boolean outer = d > edge;
            JobKit.strike(sp, e, j, P, outer ? k.num("outer", 52) : k.num("inner", 26), false, outer, life, 0, x -> {
                JobImpact.hit(sp, e, j, outer ? 2.3 : 1.5);
                if (outer && JobKit.pushable(e)) Kit.launch(e, 0, k.num("lift", 1.0), 0);
            });
        }
    }
}
