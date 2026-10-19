package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.FxDisplays;
import mmorpg.fabric.skill.Guards;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 0.25.0 투사 룬 (gladiator — 체력 1.0 + 민첩 0.5, 두 스탯 계열): 사슬 · 방패 · 투기장. 사슬은 rpg:rfx_chain (1.6칸 남짓한 마디로 이어 그림 — 늘어나지 않게),
 * 철구 · 울타리 기둥 · 거대한 사슬 기둥 · 철퇴는 블록 표시. 마무리 숫자는 굵은 흰빛 ! (IRON).
 * 사슬 갈고리 (던진 갈고리로 겨눈 적을 내 앞으로 끌어옴) · 방패 돌진 (방패를 앞세워 돌진 → 내려찍기, 받는 피해 감소) · 사슬 회전 (철구 사슬을 휘둘러 둘레를
 * 네 번 → 끌어모음, 받는 피해 감소) · 투기장 (사슬 울타리 안에 적을 가두고 사슬로 내려침, 받는 피해 감소) · 투신의 사슬 (거대한 사슬 기둥이 솟아 묶고 조인 뒤
 * 거대한 철퇴가 내리꽂힘 — 모래빛 투기장 하늘).
 */
final class RuneGladiator {
    private RuneGladiator() {}

    private static final double[][] C = RuneKit.colorOf("gladiator");
    /** 강철빛 · 흰 심지 · 모래 · 짙은 쇠 */
    static final double[] STEEL = C[0];
    static final double[] CORE = C[1];
    static final double[] SAND = {0.78, 0.66, 0.46};
    static final double[] DARK = {0.25, 0.28, 0.34};

    static final BlockState IRON = Blocks.IRON_BLOCK.defaultBlockState();
    static final BlockState CHAIN = Blocks.IRON_CHAIN.defaultBlockState();
    static final BlockState POST = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    static final BlockState LANTERN = Blocks.SEA_LANTERN.defaultBlockState();
    static final BlockState HANDLE = Blocks.DARK_OAK_LOG.defaultBlockState();

    static void register() {
        RuneCast.register("chainHook", RuneGladiator::chainHook);
        RuneCast.register("shieldRush", RuneGladiator::shieldRush);
        RuneCast.register("chainWhirl", RuneGladiator::chainWhirl);
        RuneCast.register("arenaDomain", RuneGladiator::arenaDomain);
        RuneCast.register("warChains", RuneGladiator::warChains);
    }

    // ------------------------------------------------------------ 공용: 사슬 · 쇳소리

    /** 사슬 (rpg:rfx_chain — 빛 + 심지): a → b, 1.6칸 남짓한 마디로 나눠 그림 */
    static void chain(ServerLevel level, Vec3 a, Vec3 b, double life, double alpha) {
        Vec3 d = V.sub(b, a);
        double len = V.len(d);
        if (len < 0.2) return;
        Vec3 u = V.scale(d, 1 / len);
        int n = Math.max(1, (int) Math.round(len / 1.6));
        double seg = len / n;
        for (int i = 0; i < n; i++) {
            Vec3 mid = V.add(a, u, seg * (i + 0.5));
            RuneKit.rfx2(level, "chain", mid, Opt.of("dir", u, "size", seg / 2 * 1.04, "life", life, "fade", 0.25, "alpha", alpha), STEEL, CORE);
        }
    }

    /** 쇠가 부딪힌 자리: 불똥 · 강철빛 X · 치명타 알갱이 */
    static void clang(ServerLevel level, Vec3 at, double size) {
        SkillFx.fx(level, "rpg:slash_x", at, Opt.of("dir", V.UP, "size", size, "life", 0.22, "color", STEEL, "alpha", 1.0));
        SkillFx.sparks(level, at, CORE, 6, 4);
        SkillFx.particle(level, ParticleTypes.CRIT, at, 8, 0.25, 0.35);
    }

    /** 손 자리 (눈앞 오른쪽 아래) */
    static Vec3 hand(ServerPlayer sp) {
        return RuneKit.handOf(sp).hand();
    }

    // ------------------------------------------------------------ 일반: 사슬 갈고리

    /**
     * 사슬 갈고리: 앞 range 칸 · cone 도의 가장 가까운 적에게 사슬 갈고리를 던짐 (3틱 — 사슬이 뻗어 나감) → 꽂히며 위력 × mult → 그 적을 내 앞 1.8칸으로
     * 끌어옴 (pull = 끌어오는 몫, 보스 제외 — 사슬이 감기며 돌아옴). 대상이 없으면 쓰지 않음
     */
    static boolean chainHook(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        List<LivingEntity> ts = RuneKit.coneTargets(sp, k.num("range"), k.num("cone"), 1);
        if (ts.isEmpty()) return PairKit.noTarget(sp, k);
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        LivingEntity e = ts.get(0);
        int fly = 3;
        for (int t = 0; t <= fly; t++) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
                Vec3 h = hand(sp);
                Vec3 b = Kit.bodyPoint(e);
                Vec3 tip = V.lerp(h, b, (tt + 1) / (double) (fly + 1));
                chain(level, h, tip, 0.08, 0.95);
                RuneKit.rfx2(level, "arrow", tip, Opt.of("dir", V.unit(V.sub(b, h)), "size", 0.35, "life", 0.08), STEEL, CORE);
            });
        }
        SkillFx.play(level, "entity.fishing_bobber.throw", sp.position(), 0.7, 1.0);
        SkillFx.play(level, "block.chain.place", sp.position(), 1.2, 0.8);
        RuneKit.later(fly, () -> {
            if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
            PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> clang(level, Kit.bodyPoint(e), 0.9));
            SkillFx.play(level, "block.chain.hit", e.position(), 0.8, 1.0);
            SkillFx.play(level, "item.trident.hit", e.position(), 1.2, 0.8);
            if (PairKit.boss(e)) return;
            // 끌어옴 (3틱 — 사슬이 감기며)
            Vec3 from = e.position();
            Vec3 f = Kit.facingOf(sp);
            Vec3 front = V.add(sp.position(), f, 1.8);
            Vec3 to = V.lerp(from, front, Math.max(0, Math.min(1, k.num("pull"))));
            for (int i = 1; i <= 3; i++) {
                final double u = i / 3.0;
                RuneKit.later(i, () -> {
                    if (!RuneKit.sameLife(sp, life) || e.isRemoved()) return;
                    Vec3 p = V.lerp(from, to, u);
                    RuneKit.moveTo(e, new Vec3(p.x, RuneKit.groundAt(level, p, p.y), p.z), null);
                    chain(level, hand(sp), Kit.bodyPoint(e), 0.1, 0.95);
                });
            }
            RuneKit.later(4, () -> {
                if (e.isRemoved()) return;
                SkillFx.particle(level, ParticleTypes.CLOUD, V.up(e.position(), 0.2), 6, 0.3, 0.02);
                SkillFx.play(level, "block.chain.break", e.position(), 1.0, 0.7);
            });
        });
        return true;
    }

    // ------------------------------------------------------------ 희귀: 방패 돌진

    /**
     * 방패 돌진: 받는 피해 감소 (reduce, ticks 틱) · 방패 (rpg:rfx_shield) 를 앞세워 앞으로 range 칸 (벽 앞까지) 돌진 — 처음 부딪힌 적 앞에서 멈추며 부딪힌 적 ·
     * 앞의 적 (위력 × mult, 밀쳐냄 knock) → 방패로 땅을 내려찍음: 부딪힌 적 · 둘레 radius 칸의 적 (위력 × crush)
     */
    static boolean shieldRush(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 start = sp.position();
        Vec3 f = Kit.facingOf(sp);
        double can = RuneKit.dashDistance(sp, f, k.num("range"), 0.4, 1.4);
        LivingEntity first = null;
        double stop = can;
        for (Kit.LineHit h : RuneKit.lineTargets(sp, V.up(start, 1.0), f, can + 1.0, k.num("width"))) {
            first = h.e();
            stop = Math.max(0, Math.min(can, h.t() - 1.7 - Kit.bodyPad(h.e())));
            break;
        }
        final LivingEntity rammed = first;
        final double reach = stop;
        int dash = Math.max(1, (int) Math.ceil(reach / 2.5));
        Guards.set(sp, (int) k.num("ticks"), k.num("reduce"), null, null);
        Kit.noFall(sp, 30);
        SkillFx.play(level, "item.shield.block", start, 0.8, 1.0);
        SkillFx.play(level, "mob.ravager.stun", start, 1.4, 0.5);
        for (int t = 0; t <= dash; t++) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                if (tt > 0) {
                    Vec3 p = V.add(start, f, reach * tt / dash);
                    RuneKit.moveTo(sp, new Vec3(p.x, RuneKit.groundAt(level, p, start.y), p.z), null);
                }
                Vec3 c = sp.position();
                RuneKit.shieldEmblem(sp, level, V.add(V.up(c, 1.1), f, 1.3), 0.8, STEEL, Opt.of("full", 2.6, "min", 0.45, "life", 0.12, "core", CORE));
                SkillFx.particle(level, ParticleTypes.CLOUD, V.up(c, 0.1), 3, 0.3, 0.02);
                RuneKit.streak(level, V.up(V.add(c, f, -1.2), 0.9), V.up(c, 0.9), STEEL, 0.3, 0.25);
            });
        }
        // 부딪힘
        RuneKit.later(dash + 1, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 c = sp.position();
            Vec3 front = V.add(V.up(c, 1.0), f, 1.3);
            RuneKit.shieldEmblem(sp, level, front, 1.1, STEEL, Opt.of("full", 2.6, "min", 0.45, "life", 0.3, "grow", 0.4, "core", CORE));
            SkillFx.ring(level, front, f, CORE, 0.9, 0.3, 3.0);
            clang(level, V.add(front, f, 0.3), 1.0);
            SkillFx.play(level, "entity.goat.ram_impact", c, 0.8, 1.0);
            SkillFx.play(level, "block.anvil.land", c, 1.4, 0.4);
            List<LivingEntity> got = new ArrayList<>();
            if (rammed != null && !rammed.isRemoved()) got.add(rammed);
            for (LivingEntity e : RuneKit.coneAround(sp, c, f, 2.8, 100)) if (!got.contains(e)) got.add(e);
            for (LivingEntity e : got) {
                PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                    clang(level, Kit.bodyPoint(e), 0.8);
                    if (!PairKit.boss(e)) RuneKit.knock(e, f, k.num("knock"), 0.25);
                });
            }
        });
        // 내려찍기
        RuneKit.later(dash + 11, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 c = sp.position();
            Vec3 at = PairKit.onGround(level, V.add(c, f, 1.2));
            double radius = k.num("radius");
            RuneKit.shieldEmblem(sp, level, V.up(at, 0.6), 0.9, STEEL, Opt.of("full", 2.6, "min", 0.45, "life", 0.25, "core", CORE));
            RuneKit.zonePulse(level, at, STEEL, radius, 0.4);
            RuneKit.glowCrack(level, at, radius * 0.8, STEEL, CORE, Opt.of("life", 1.0));
            RuneKit.rockBurst(level, V.up(at, 0.3), 5, 4, 0.2);
            RuneKit.shakeAround(level, at, 8, 0.25, 0.25);
            SkillFx.play(level, "mace.smash_ground", at, 0.9, 1.0);
            SkillFx.play(level, "item.shield.block", at, 0.6, 1.0);
            Map<UUID, LivingEntity> got = new LinkedHashMap<>();
            if (rammed != null && !rammed.isRemoved()) got.put(rammed.getUUID(), rammed);
            for (LivingEntity e : Kit.around(sp, at, radius, 3)) got.putIfAbsent(e.getUUID(), e);
            for (LivingEntity e : got.values()) {
                PairKit.hit(sp, r, e, P, k.num("crush"), DamageNumbers.Style.IRON, life, dmg -> {
                    clang(level, Kit.bodyPoint(e), 0.9);
                    if (!PairKit.boss(e)) Kit.knockback(e, 0, 0, 0.3);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 고귀: 사슬 회전

    /**
     * 사슬 회전: 받는 피해 감소 (reduce, ticks 틱) · 철구 둘 (블록 표시 — 가시 철구) 을 사슬에 매달아 몸 둘레로 휘두름 — gap 틱마다 hits 번 둘레 radius 칸의 적
     * (위력 × mult) → 사슬로 끌어모음 (pull — 보스 제외, 위력 × crush)
     */
    static boolean chainWhirl(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        double radius = k.num("radius");
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int whirl = hits * gap;
        Guards.set(sp, (int) k.num("ticks"), k.num("reduce"), null, null);
        Vec3 base = sp.position();
        // 철구 둘 (정육면체 + 45° 돌린 정육면체 = 가시 철구)
        List<Display.BlockDisplay> balls = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Display.BlockDisplay d = FxDisplays.block(level, base, IRON, PairKit.box(new Vec3(0, 1.1, 0), new Quaternionf(), 0.1, 0.1, 0.1), whirl + 6, PairKit.LIT);
            balls.add(d);
        }
        double a0 = Math.atan2(Kit.facingOf(sp).z, Kit.facingOf(sp).x);
        double rr = Math.min(radius - 0.8, 3.2);
        Quaternionf spiky = new Quaternionf().rotationXYZ(0.62F, 0.79F, 0.0F);
        SkillFx.play(level, "item.armor.equip_chain", base, 0.8, 1.0);
        SkillFx.play(level, "mob.breeze.whirl", base, 0.7, 0.9);
        for (int t = 2; t <= whirl; t += 2) {
            final int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 c = sp.position();
                Vec3 rel = V.sub(c, base);
                for (int j = 0; j < 2; j++) {
                    double a = a0 + Math.PI / 2 + tt * 0.42 + j * Math.PI;
                    Vec3 off = new Vec3(Math.cos(a) * rr, 1.1 + 0.15 * Math.sin(tt * 0.3 + j), Math.sin(a) * rr);
                    Vec3 at = V.add(rel, off);
                    FxDisplays.move(balls.get(j * 2), PairKit.box(at, new Quaternionf().rotationY((float) (tt * 0.5)), 0.6, 0.6, 0.6), 2);
                    FxDisplays.move(balls.get(j * 2 + 1), PairKit.box(at, new Quaternionf(spiky).rotateY((float) (tt * 0.5)), 0.5, 0.5, 0.5), 2);
                    Vec3 ball = V.add(c, off);
                    chain(level, V.up(c, 1.1), ball, 0.12, 0.95);
                    SkillFx.fx(level, "rpg:arrow_spark", ball, Opt.of("dir", new Vec3(-Math.sin(a), 0, Math.cos(a)), "speed", 2.0, "size", 0.14, "life", 0.25, "color",
                            CORE, "alpha", 1.0));
                }
                if (tt % 4 == 0) SkillFx.fx(level, "rpg:whirlpool", V.up(c, 0.1), Opt.of("dir", V.ZERO, "size", radius * 0.85, "life", 0.3, "spin", tt * 30.0, "speed",
                        -700.0, "color", DARK, "alpha", 0.45));
            });
        }
        SkillFx.every(hits, gap, i -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 c = sp.position();
            // 몸 둘레의 사슬 참격 여섯 (도는 쪽으로 — 앞 (1인칭 화면) 의 것은 흐리게, 몸 가운데에는 그리지 않음)
            for (int j = 0; j < 6; j++) {
                double a = j / 6.0 * Math.PI * 2 + i * 0.7;
                Vec3 dir = new Vec3(Math.cos(a + Math.PI / 2), 0, Math.sin(a + Math.PI / 2));
                Vec3 at = new Vec3(c.x + Math.cos(a) * radius * 0.7, c.y + 1.0, c.z + Math.sin(a) * radius * 0.7);
                boolean ahead = V.dot(V.flat(V.sub(at, c)), Kit.facingOf(sp)) > 0.6;
                SkillFx.arc(level, at, dir, Opt.of("shape", "heavy", "flip", i % 2 == 1, "size", 1.6, "life", 0.22, "speed", 2.0, "spin", i % 2 == 0 ? 30.0 : -30.0,
                        "color", STEEL, "aura", j % 2 == 0 ? DARK : null, "alpha", ahead ? 0.4 : 0.9));
            }
            RuneKit.zonePulse(level, c, STEEL, radius, 0.35);
            SkillFx.play(level, "block.chain.hit", c, 0.7 + i * 0.1, 1.0);
            SkillFx.play(level, "entity.player.attack.sweep", c, 0.8, 0.8);
            for (LivingEntity e : Kit.around(sp, c, radius, 2.5)) {
                PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                    if (RuneKit.fxQuota("whirl", 5)) clang(level, Kit.bodyPoint(e), 0.7);
                });
            }
        });
        // 끌어모음
        RuneKit.later(whirl, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            Vec3 c = sp.position();
            for (Display.BlockDisplay d : balls) FxDisplays.move(d, PairKit.box(V.up(V.sub(c, base), 1.0), new Quaternionf(), 0.3, 0.3, 0.3), 3);
            RuneKit.zonePulse(level, c, CORE, radius, 0.4);
            SkillFx.play(level, "block.chain.break", c, 0.6, 1.0);
            SkillFx.play(level, "item.armor.equip_chain", c, 0.6, 1.0);
            for (LivingEntity e : Kit.around(sp, c, radius, 3)) {
                chain(level, V.up(c, 1.0), Kit.bodyPoint(e), 0.3, 0.95);
                PairKit.hit(sp, r, e, P, k.num("crush"), DamageNumbers.Style.IRON, life, dmg -> clang(level, Kit.bodyPoint(e), 0.9));
                if (!PairKit.boss(e)) {
                    Vec3 in = V.flat(V.sub(c, e.position()));
                    double dist = V.hdist(c, e.position());
                    double pull = k.num("pull") * Math.min(2.5, dist);
                    Kit.knockback(e, in.x * pull, in.z * pull, 0.15);
                }
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 영웅: 투기장

    /**
     * 투기장: 받는 피해 감소 (reduce, ticks 틱) · 내 자리를 가운데로 반지름 radius 칸의 투기장 (사슬 울타리 — 기둥 (블록 표시) 사이의 사슬 두 줄 · 모래빛 칼날
     * 문양) 이 ticks 틱 — 안에 들어온 적은 울타리 밖으로 나가지 못함 (보스 제외 — 밀어 넣음). gap 틱마다 hits 번 안의 적에게 하늘에서 사슬이 내리침
     * (위력 × mult) → 끝에 사슬이 모두 모여 내리침 (위력 × crush)
     */
    static boolean arenaDomain(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 c = sp.position();
        double radius = k.num("radius");
        int ticks = (int) k.num("ticks");
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int first = 10;
        int crushAt = Math.max(first + (hits - 1) * gap + 12, ticks - 6);
        int end = crushAt + 6;
        Guards.set(sp, ticks, k.num("reduce"), null, null);
        RuneKit.domainSigil(level, c, radius, "blade", SAND, CORE, Opt.of("life", end / 20.0, "spinRate", 6.0, "alpha", 0.6, "fade", 0.1, "coreAlpha", 0.5));
        SkillFx.play(level, "block.chain.place", c, 0.6, 1.0);
        SkillFx.play(level, "block.bell.hit", c, 0.7, 1.0);
        SkillFx.play(level, "mob.ravager.roar", c, 1.2, 0.5);
        // 울타리 기둥 (솟아오름)
        int posts = 12;
        List<Vec3> tops = new ArrayList<>();
        for (int j = 0; j < posts; j++) {
            double a = j / (double) posts * Math.PI * 2;
            Vec3 p = PairKit.onGround(level, PairKit.ring(c, radius, a));
            tops.add(V.up(p, 2.2));
            if (PairKit.nearCam(sp, V.up(p, 1.2), 2.4)) continue;
            PairKit.Rig post = new PairKit.Rig(level, p, end + 4);
            post.offset = new Vec3(0, -2.6, 0);
            post.add(POST, new Vec3(0, 1.2, 0), 0.42, 2.4, 0.42, PairKit.LIT);
            post.add(IRON, new Vec3(0, 2.45, 0), 0.6, 0.28, 0.6, PairKit.LIT);
            post.add(LANTERN, new Vec3(0, 2.72, 0), 0.3, 0.3, 0.3, PairKit.GLOW);
            final int jj = j;
            RuneKit.later(2 + jj % 3, () -> post.pose(Vec3.ZERO, new Quaternionf(), 1.0, 3));
            RuneKit.later(end - 2, () -> post.crumble(5, 0.1, 1.6, 0.4));
        }
        // 사슬 두 줄 (기둥 사이 — 10틱마다 다시 그림)
        for (int t = 6; t < end; t += 10) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (int j = 0; j < posts; j++) {
                    Vec3 a = tops.get(j);
                    Vec3 b = tops.get((j + 1) % posts);
                    if (PairKit.nearCam(sp, V.lerp(a, b, 0.5), 2.0)) continue;
                    chain(level, V.up(a, -0.4), V.up(b, -0.4), 0.55, 0.95);
                    chain(level, V.up(a, -1.3), V.up(b, -1.3), 0.55, 0.85);
                }
            });
        }
        // 가두기 (2틱마다): 안에 있던 적이 울타리에 닿으면 안으로 밀어 넣음
        Map<UUID, LivingEntity> inside = new LinkedHashMap<>();
        for (int t = 0; t < end; t += 2) {
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (LivingEntity e : Kit.around(sp, c, radius + 2.5, 3)) {
                    double dist = V.hdist(c, e.position());
                    if (dist <= radius - 0.3) inside.putIfAbsent(e.getUUID(), e);
                    if (!inside.containsKey(e.getUUID()) || PairKit.boss(e) || dist < radius - 0.8) continue;
                    Vec3 in = V.flat(V.sub(c, e.position()));
                    Kit.knockback(e, in.x * 0.55, in.z * 0.55, 0.12);
                    if (RuneKit.fxQuota("arenaWall", 3)) clang(level, V.up(e.position(), 1.0), 0.6);
                }
            });
        }
        // 사슬이 내리침
        for (int i = 0; i < hits; i++) {
            final int ii = i;
            RuneKit.later(first + i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                List<LivingEntity> ts = Kit.around(sp, c, radius, 3);
                for (int j = 0; j < Math.min(6, ts.size()); j++) {
                    Vec3 b = Kit.bodyPoint(ts.get(j));
                    Vec3 top = V.add(V.up(b, 6.0), V.flat(V.sub(b, c)), 1.5);
                    chain(level, top, b, 0.3, 0.95);
                }
                RuneKit.zonePulse(level, c, ii % 2 == 0 ? STEEL : SAND, radius, 0.4);
                SkillFx.play(level, "block.chain.hit", c, 0.6 + ii * 0.1, 1.0);
                SkillFx.play(level, "mace.smash_ground", c, 1.2, 0.6);
                for (LivingEntity e : ts) {
                    PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                        if (RuneKit.fxQuota("arenaHit", 6)) {
                            clang(level, Kit.bodyPoint(e), 0.8);
                            SkillFx.fx(level, "rpg:shock_ring", V.up(e.position(), 0.08), Opt.of("dir", V.ZERO, "size", 0.8, "life", 0.3, "color", STEEL, "alpha",
                                    0.85));
                        }
                    });
                }
            });
        }
        // 마무리: 기둥마다 사슬이 가운데로 모여 내리침
        RuneKit.later(crushAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            for (int j = 0; j < posts; j += 2) chain(level, tops.get(j), V.up(c, 0.8), 0.35, 0.95);
            RuneKit.novaRing(level, c, 1.0, radius * 0.9, STEEL, CORE, Opt.of("life", 0.5));
            RuneKit.glowCrack(level, c, radius * 0.7, STEEL, CORE, Opt.of("life", 1.2));
            RuneKit.shakeAround(level, c, 14, 0.35, 0.35);
            SkillFx.play(level, "block.anvil.land", c, 0.6, 0.8);
            SkillFx.play(level, "block.chain.break", c, 0.5, 1.0);
            SkillFx.play(level, "random.explode", c, 1.1, 0.5);
            Map<UUID, LivingEntity> got = new LinkedHashMap<>();
            for (LivingEntity e : Kit.around(sp, c, radius, 3)) got.put(e.getUUID(), e);
            for (LivingEntity e : inside.values()) if (!e.isRemoved() && V.hdist(c, e.position()) <= radius + 2) got.putIfAbsent(e.getUUID(), e);
            for (LivingEntity e : got.values()) {
                PairKit.hit(sp, r, e, P, k.num("crush"), DamageNumbers.Style.IRON, life, dmg -> {
                    clang(level, Kit.bodyPoint(e), 1.0);
                    if (!PairKit.boss(e)) Kit.knockback(e, 0, 0, 0.4);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 초월: 투신의 사슬

    /** 거대한 철퇴 (모형: 머리 아래 끝 = 0, 자루는 위로 — 쇠 머리 · 가시 · 빛나는 띠 · 나무 자루) */
    private static PairKit.Rig mace(ServerLevel level, Vec3 base, int life, Vec3 offset, Quaternionf rot, double scale) {
        PairKit.Rig m = new PairKit.Rig(level, base, life);
        m.offset = offset;
        m.rot = rot;
        m.scale = scale;
        m.add(HANDLE, new Vec3(0, 4.1, 0), 0.5, 4.4, 0.5, PairKit.LIT);
        m.add(IRON, new Vec3(0, 6.2, 0), 0.7, 0.4, 0.7, PairKit.LIT);
        m.add(IRON, new Vec3(0, 1.05, 0), 2.0, 2.0, 2.0, PairKit.LIT);
        m.add(LANTERN, new Vec3(0, 1.05, 0), 2.08, 0.3, 2.08, PairKit.GLOW);
        Quaternionf diamond = new Quaternionf().rotationXYZ(0.62F, 0.79F, 0.0F);
        Vec3[] spikes = {new Vec3(1.15, 1.05, 0), new Vec3(-1.15, 1.05, 0), new Vec3(0, 1.05, 1.15), new Vec3(0, 1.05, -1.15), new Vec3(0, -0.1, 0),
            new Vec3(0.8, 1.85, 0.8), new Vec3(-0.8, 1.85, -0.8), new Vec3(0.8, 0.25, -0.8), new Vec3(-0.8, 0.25, 0.8)};
        for (Vec3 s : spikes) m.add(0, IRON, s, new Vec3(0.55, 0.55, 0.55), diamond, PairKit.LIT);
        return m;
    }

    /**
     * 투신의 사슬: 조준한 곳 (range 칸) 에 모래빛 투기장 하늘 (3돌부터) · 칼날 문양 → 둘레에서 거대한 사슬 기둥 여섯 (블록 표시 — 쇠사슬 블록을 쌓음) 이 땅을 뚫고
     * 솟아 가운데로 기울고 (delay 틱), 반지름 radius 칸의 적을 사슬로 묶음 (root 틱 — 보스는 느려짐만) → gap 틱마다 hits 번 사슬이 조임 (위력 × mult) →
     * 하늘에서 거대한 철퇴 (블록 표시) 가 내리꽂힘 (위력 × crush — 충격 고리 · 갈라짐, 띄움). 대상이 없어도 씀
     */
    static boolean warChains(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        Vec3 aim = RuneKit.groundAim(sp, k.num("range"));
        Vec3 f = V.flat(V.sub(aim, sp.position()));
        double radius = k.num("radius");
        int delay = (int) k.num("delay");
        int hits = (int) k.num("hits");
        int gap = (int) k.num("gap");
        int root = (int) k.num("root");
        int lastHit = delay + (hits - 1) * gap;
        int slamAt = lastHit + 12;
        int crushAt = slamAt + 4;
        int end = crushAt + 14;
        RuneKit.ultSkyFor(sp, level, aim, "warChains", end + 30, null);
        RuneKit.ultFlashFor(sp, level, aim, "warChains", "flash", Opt.empty());
        RuneKit.domainSigil(level, aim, radius, "blade", STEEL, CORE, Opt.of("life", end / 20.0, "spinRate", -20.0, "grow", 0.15, "alpha", 0.8, "fade", 0.15));
        RuneKit.selfCue(sp, STEEL, CORE, true);
        SkillFx.play(level, "block.chain.place", aim, 0.5, 1.0);
        SkillFx.play(level, "mob.wither.spawn", sp.position(), 1.7, 0.4);
        SkillFx.play(level, "block.bell.hit", aim, 0.5, 1.0);
        // 거대한 사슬 기둥 여섯 (쇠사슬 블록 셋을 쌓음 — 가운데로 기욺)
        List<Vec3> anchors = new ArrayList<>();
        double ringR = radius + 0.8;
        for (int j = 0; j < 6; j++) {
            double a = j / 6.0 * Math.PI * 2 + 0.3;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 p = PairKit.onGround(level, V.add(aim, out, ringR));
            Quaternionf tilt = PairKit.about(V.cross(V.UP, out), -24);
            Vec3 axis = new Vec3(0, 1, 0);
            org.joml.Vector3f ax = new org.joml.Vector3f(0, 1, 0).rotate(new Quaternionf(tilt));
            axis = new Vec3(ax.x, ax.y, ax.z);
            anchors.add(V.add(p, axis, 6.2));
            if (PairKit.nearCam(sp, V.up(p, 2.0), 3.0)) continue;
            PairKit.Rig col = new PairKit.Rig(level, p, end + 4);
            col.offset = new Vec3(0, -7.0, 0);
            col.rot = tilt;
            for (int s = 0; s < 3; s++) col.add(0, CHAIN, new Vec3(0, 1.1 + s * 2.2, 0), new Vec3(2.2, 2.2, 2.2), PairKit.about(V.UP, s * 90 + 45), PairKit.LIT);
            col.add(LANTERN, new Vec3(0, 0.15, 0), 1.2, 0.3, 1.2, PairKit.GLOW);
            final int jj = j;
            RuneKit.later(2 + jj % 3, () -> col.pose(Vec3.ZERO, tilt, 1.0, Math.max(3, delay - 6)));
            RuneKit.later(crushAt + 4, () -> col.crumble(8, 0.1, 5.0, 0.5));
            RuneKit.later(jj % 3, () -> {
                RuneKit.rockBurst(level, V.up(p, 0.3), 4, 5, 0.26);
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.DUST_PILLAR, Blocks.STONE.defaultBlockState()), p.x, p.y + 0.1,
                        p.z, 12, 0.6, 0.1, 0.6, 0.3);
            });
        }
        for (int t = 0; t < delay; t += 3) {
            RuneKit.later(t, () -> {
                if (RuneKit.sameLife(sp, life)) RuneKit.shakeAround(level, aim, 18, 0.15, 0.2);
            });
        }
        // 묶음: 사슬 기둥 꼭대기에서 적마다 사슬
        Map<UUID, LivingEntity> bound = new LinkedHashMap<>();
        RuneKit.later(delay, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            SkillFx.play(level, "block.chain.hit", aim, 0.5, 1.0);
            SkillFx.play(level, "item.armor.equip_chain", aim, 0.5, 1.0);
            for (LivingEntity e : Kit.around(sp, aim, radius, 5)) {
                bound.put(e.getUUID(), e);
                PairKit.root(e, root);
            }
        });
        // 조이기: 사슬이 당겨지며 번쩍임
        for (int i = 0; i < hits; i++) {
            final int ii = i;
            RuneKit.later(delay + i * gap, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (LivingEntity e : Kit.around(sp, aim, radius, 5)) bound.putIfAbsent(e.getUUID(), e);
                int n = 0;
                for (LivingEntity e : bound.values()) {
                    if (e.isRemoved() || V.hdist(aim, e.position()) > radius + 2.5) continue;
                    if (n++ < 6) {
                        Vec3 b = Kit.bodyPoint(e);
                        chain(level, anchors.get((ii + n) % anchors.size()), b, 0.45, 0.95);
                        chain(level, anchors.get((ii + n + 3) % anchors.size()), b, 0.45, 0.8);
                    }
                    PairKit.hit(sp, r, e, P, k.num("mult"), life, dmg -> {
                        if (RuneKit.fxQuota("warChainHit", 6)) clang(level, Kit.bodyPoint(e), 0.8);
                    });
                }
                if (bound.isEmpty()) for (int j = 0; j < 3; j++) chain(level, anchors.get((ii + j * 2) % anchors.size()), V.up(aim, 0.8), 0.45, 0.9);
                RuneKit.zonePulse(level, aim, ii % 2 == 0 ? STEEL : CORE, radius, 0.4);
                SkillFx.play(level, "block.chain.hit", aim, 0.7 + ii * 0.08, 1.0);
                SkillFx.play(level, "item.armor.equip_chain", aim, 0.9, 0.7);
            });
        }
        // 거대한 철퇴
        // 철퇴는 조준한 곳 너머 위에 떠올라 (1인칭에서도 보이게) 내리꽂힘
        Vec3 hover = V.add(new Vec3(0, 6.0, 0), f, 3.0);
        Quaternionf q = PairKit.facing(f);
        PairKit.Rig m = mace(level, aim, end + 4, V.add(hover, V.UP, 6.0), q, 0.4);
        RuneKit.later(Math.max(2, slamAt - 18), () -> m.pose(hover, new Quaternionf(q).mul(PairKit.about(new Vec3(1, 0, 0), -24)), 1.6, 12));
        RuneKit.later(slamAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            m.pose(new Vec3(0, -0.4, 0), q, 1.6, 4);
            RuneKit.streak(level, V.add(aim, hover), V.up(aim, 1.0), STEEL, 1.4, 0.3);
            SkillFx.play(level, "mob.enderdragon.flap", V.add(aim, hover), 0.6, 1.0);
        });
        RuneKit.later(crushAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            RuneKit.ultFlashFor(sp, level, aim, "warChains", "flash", Opt.empty());
            RuneKit.novaRing(level, aim, 1.8, radius * 0.9, STEEL, CORE, Opt.of("life", 0.6));
            RuneKit.glowCrack(level, aim, radius * 1.05, STEEL, CORE, Opt.of("life", 1.8, "v", 1.0));
            SkillFx.fx(level, "rpg:ground_crack", V.up(aim, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", radius * 1.1, "life", 1.6, "color", DARK,
                    "alpha", 0.9));
            RuneKit.rockRing(sp, level, aim, radius * 0.6, 8, 0.7, STEEL, Opt.of("life", 1.0, "minDist", 2.2));
            RuneKit.rockBurst(level, V.up(aim, 0.4), 10, 6, 0.3);
            SkillFx.particle(level, ParticleTypes.CRIT, V.up(aim, 1.0), 50, radius * 0.4, 0.8);
            RuneKit.shakeAround(level, aim, 24, 0.6, 0.55);
            SkillFx.play(level, "block.anvil.land", aim, 0.5, 1.0);
            SkillFx.play(level, "mace.heavy_smash_ground", aim, 0.5, 1.0);
            SkillFx.play(level, "random.explode", aim, 0.6, 0.8);
            for (LivingEntity e : Kit.around(sp, aim, radius, 5)) {
                PairKit.hit(sp, r, e, P, k.num("crush"), DamageNumbers.Style.IRON, life, dmg -> {
                    if (!PairKit.boss(e)) RuneKit.knock(e, V.sub(e.position(), aim), 0.35, 0.6);
                    if (RuneKit.fxQuota("warChainCrush", 8)) clang(level, Kit.bodyPoint(e), 1.1);
                });
            }
        });
        RuneKit.later(crushAt + 8, () -> m.crumble(8, 0.1, 1.0, 0.5));
        return true;
    }
}
