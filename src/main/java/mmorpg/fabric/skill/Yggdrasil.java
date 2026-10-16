package mmorpg.fabric.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import mmorpg.core.Obj;
import mmorpg.core.Weapon;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgHealth;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 세계수의 도끼 (도끼 초월 — 0.12.4, 반고의 도끼 자리: 사용자가 고른 새 컨셉 "세계수", 제작 재료 세계수의 가지와 맞춤). 동작은 궁니르와 겹치지 않게 새로
 * (던져서 돌아옴 · 하늘에서 거대한 무기가 아님), 배율 합 · 재사용 대기 · 회복은 예전과 같음 (SKILLS.axe — rules_more, 색은 스킬마다).
 * <ul>
 * <li>세계수의 씨앗 (ygg_seed): 빛나는 씨앗을 조준한 땅에 던짐 → 떨어진 자리에 가시덤불이 솟아 둘레 적을 찌르고 묶음 (mult, 구속) → bind 틱 뒤
 * 빛의 꽃이 피며 터짐 (afterMult)</li>
 * <li>세계수 강림 (ygg_tree): 땅속으로 뿌리가 달려가 조준한 곳에 모여듦 → 거대한 세계수가 솟음 (mult — 뿌리가 둘레 적을 띄우고 묶음) →
 * 연둣빛 잎이 두 번 쏟아짐 (pulseMult — 마법 피해) → 준 피해 일부 회복 (도끼 규칙 딜탱)</li>
 * </ul>
 * 그림: rpg:rfx_yggdrasil (세계수 4장 — 싹 → 다 자람 → 빛남) · rfx_thorn (가시뿌리) · rfx_leaf (잎 · 꽃잎) · rfx_bloom · rfx_bloomflat (빛의 꽃) —
 * tools/java_fx_art_more.py. 색 (0.12.8): 무기 디자인 (0.12.7 사용자 리워크) 의 비취 · 민트 수정 날 · 연둣빛 잎 장식 · 짙은 나무 자루. 쓰러지거나 차원을 옮기면 남은 것은 없음
 */
final class Yggdrasil {
    private Yggdrasil() {}

    /** 나무 빛 (가시뿌리 바탕 — 그림이 회색이라 곱함) */
    static final double[] WOOD = {0.52, 0.37, 0.2};
    static final double[] LEAF = {0.4, 0.8, 0.46};
    /** 쏟아지는 잎 (도끼 자루의 연둣빛 잎 장식 — 예전 금빛) */
    static final double[] SPROUT = {0.72, 0.84, 0.3};
    /** 꽃잎 (도끼 날의 민트 수정빛 — 예전 분홍) */
    static final double[] PETAL = {0.8, 1.0, 0.86};
    static final double[] WHITE = {0.92, 1.0, 0.94};
    static final double[] DEEP = {0.06, 0.26, 0.16};
    /** rfx_thorn 의 빛줄기 칸 (위 줄 나무 0 ~ 2, 아래 줄 빛줄기 3 ~ 5 — rfx_rock 과 같음) */
    static final int THORN_CORE = 3;
    /** rfx_yggdrasil 그림: 밑동이 판 위에서 118/128 → 그림 가운데는 밑동 위 0.84375 × 반 크기 */
    static final double TREE_BASE = 0.84375;
    /** 그림 판 높이 (2 × 반 크기) 가운데 나무가 차지하는 몫 (위 16 ~ 밑동 118 / 128) */
    static final double TREE_FILL = 102 / 128.0;

    static void register() {
        Transcend.register("ygg_seed", Yggdrasil::seed);
        Transcend.register("ygg_tree", Yggdrasil::tree);
    }

    // ------------------------------------------------------------ 그림 도구

    /** 가시뿌리 하나 (rpg:rfx_thorn — 밑동 고정, 모양 3가지): 나무 빛 (o.wood) + 결의 빛 (glow). 카메라에 가까우면 작게. o: v · full · life · grow · fade · alpha */
    static void thorn(ServerPlayer sp, ServerLevel level, Vec3 at, double size, double[] glow, Opt o) {
        double v = o.has("v") ? o.num("v", 0) : Math.floor(Kit.rand() * 3);
        double s = size * RuneKit.nearK(sp, V.up(at, size), o.num("full", 4), 0.35);
        RuneKit.rfx2(level, "thorn", at, Opt.of("size", s, "life", o.num("life", 0.9), "grow", o.num("grow", 0.25), "fade", o.num("fade", 0.35), "cell", v,
                "alpha", o.num("alpha", 1)), o.color("wood", WOOD), glow, new int[] {0, THORN_CORE}, null);
    }

    /** 쓴 사람 발밑 가로 CLEAR 칸 안 (둘레에 흩뿌리는 가시를 여기에는 돋우지 않음 — 1인칭 화면을 가시가 덮지 않게) */
    static final double CLEAR = 2.2;

    static boolean nearCaster(ServerPlayer sp, Vec3 p) {
        return Math.hypot(p.x - sp.getX(), p.z - sp.getZ()) < CLEAR && Math.abs(p.y - sp.getY()) < 3;
    }

    /** 잎 (꽃잎) n 장이 at 에서 흩날림: 옆으로 spread 칸/초 · 위로 up 칸/초 (아래로면 음수), life 초 */
    static void leaves(ServerLevel level, Vec3 at, int n, double[] color, double spread, double up, double life) {
        for (int i = 0; i < n; i++) {
            double a = Kit.rand() * Math.PI * 2;
            double s = spread * (0.4 + Kit.rand() * 0.6);
            Vec3 vel = new Vec3(Math.cos(a) * s, up * (0.5 + Kit.rand()), Math.sin(a) * s);
            Vec3 p = new Vec3(at.x + Math.cos(a) * 0.3, at.y + Kit.rand() * 0.4, at.z + Math.sin(a) * 0.3);
            RuneKit.rfx(level, "leaf", p, Opt.of("size", 0.12 + Kit.rand() * 0.08, "life", life * (0.7 + Kit.rand() * 0.5), "spin", Kit.rand() * 360,
                    "spinRate", (Kit.rand() - 0.5) * 720, "cell", Math.floor(Kit.rand() * 4), "color", color, "fade", 0.4, "vel", vel));
        }
    }

    /** 땅속으로 달리는 뿌리 한 칸: 땅의 금 (rfx_crack — 나무 빛 + 금빛 결) + 작은 가시 */
    private static void rootStep(ServerPlayer sp, ServerLevel level, Vec3 p, double[] glow, double[] core, double size) {
        RuneKit.rfx2(level, "crack", V.up(p, 0.07), Opt.of("dir", V.UP, "size", size, "life", 1.0, "fade", 0.5, "spin", Kit.rand() * 360, "cell", 0.0),
                DEEP, glow, new int[] {0, 1}, null);
        thorn(sp, level, p, 0.35 + Kit.rand() * 0.2, core, Opt.of("life", 0.6, "grow", 0.3, "full", 3.0));
    }

    /** 회복 모으기 (천지개벽과 같음): 모인 피해 pool, 다음 틱 회복이 잡혔는지 pending, 이번에 더 회복할 수 있는 양 left */
    private static final class Heal {
        double pool;
        boolean pending;
        double left;

        Heal(double left) {
            this.left = left;
        }
    }

    /** 묶음: 구속 (amp 단계) ticks 틱 — 보스 · 보스 소품은 묶이지 않음 */
    private static void bind(LivingEntity e, int ticks, int amp) {
        if (!Aegis.bossLike(e) && !e.isRemoved()) e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, amp, false, false));
    }

    // ------------------------------------------------------------ 스킬 1 세계수의 씨앗

    /**
     * 세계수의 씨앗: 조준한 땅 (range 칸, 가로 min 칸보다 가까우면 min 칸 앞 — 궁니르와 같은 규칙) 으로 빛나는 씨앗을 던짐 (fly 틱 동안 포물선 —
     * 뒤로 잎이 날림) → 떨어진 자리에 가시덤불이 솟아 반지름 radius 칸의 적 (가운데 위 1.2칸에서 보이는) 을 찌름 (mult) · 묶음 (bind 틱 동안 구속 slow —
     * 보스는 아님, 발밑에 가시) → bind 틱 뒤 빛의 꽃이 피며 터짐 (afterMult — 반지름 radius + 0.5칸, 찔린 적은 밀려났어도 radius + 2.5칸 안이면).
     * 땅을 조준하지 않으면 (허공) 재사용 대기 없이 거절
     */
    static boolean seed(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        Vec3 spot = Gungnir.fallSpot(sp, k);
        if (spot == null) {
            Notify.warn(sp, "§c" + k.str("name") + ": §7땅을 조준해야 합니다 (" + k.text("range") + "칸 안)");
            return false;
        }
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = HeroKit.colors(k);
        double[] glow = cs[0];
        double[] core = cs[1];
        double radius = k.num("radius");
        int fly = Math.max(2, (int) k.num("fly"));
        int bind = (int) k.num("bind");
        int slow = (int) k.num("slow");
        Vec3 f = Kit.facingOf(sp);
        Vec3 from = V.add(V.add(V.up(sp.getEyePosition(), -0.35), V.rightOf(f), 0.35), f, 1.0);
        Vec3 to = V.up(spot, 0.3);
        double arc = Math.max(1.2, Math.min(4.5, V.len(V.sub(to, from)) * 0.3));
        SkillFx.play(level, "entity.snowball.throw", from, 0.6, 1.0);
        SkillFx.play(level, "block.azalea_leaves.break", from, 1.2, 0.8);
        // 날아감: 틱마다 그 자리에 씨앗 빛 (빛 + 심지) + 잎 한 장
        Kit.ticks(t -> {
            if (!ok.getAsBoolean() || t >= fly) return false;
            double s = (t + 1.0) / fly;
            Vec3 p = V.up(V.add(from, V.sub(to, from), s), arc * 4 * s * (1 - s));
            double near = HeroKit.near(sp.getEyePosition(), p, 0.4);
            RuneKit.rfx2(level, "flare", p, Opt.of("size", 0.45 * near, "life", 0.12, "fade", 0.5, "spin", Kit.rand() * 90), glow, core, new int[] {0, 1}, null);
            if (t % 2 == 0) leaves(level, p, 1, LEAF, 0.6, 0.4, 0.6);
            return true;
        });
        // 떨어짐: 가시덤불 · 땅의 문양 · 고리 · 잎 → 둘레 적을 찌르고 묶음
        Map<UUID, LivingEntity> bound = new LinkedHashMap<>();
        double hold = bind / 20.0;
        Tasks.later(fly, () -> {
            if (!ok.getAsBoolean()) return;
            thorn(sp, level, spot, 1.35, glow, Opt.of("v", 2.0, "life", hold + 0.25, "grow", 0.12, "full", 5.0));
            double a0 = Kit.rand() * Math.PI * 2;
            for (int i = 0; i < 8; i++) {
                double a = a0 + i / 8.0 * Math.PI * 2 + Kit.rand() * 0.3;
                double rr = radius * (0.4 + Kit.rand() * 0.55);
                Vec3 q = new Vec3(spot.x + Math.cos(a) * rr, spot.y, spot.z + Math.sin(a) * rr);
                if (nearCaster(sp, q)) continue;
                thorn(sp, level, q, 0.65 + Kit.rand() * 0.45, glow, Opt.of("life", hold + 0.1, "grow", 0.15));
            }
            SkillFx.rune(level, V.up(spot, 0.05), Opt.of("size", radius, "life", hold, "color", glow, "alpha", 0.75, "rate", 40.0));
            SkillFx.shock(level, spot, glow, radius / 2.6, 0.4);
            SkillFx.burst(level, V.up(spot, 0.6), core, 1.3, 0.25);
            leaves(level, V.up(spot, 0.4), 10, LEAF, 3.0, 4.0, 0.9);
            SkillFx.play(level, "block.roots.break", spot, 0.7, 1.0);
            SkillFx.play(level, "block.azalea_leaves.break", spot, 0.8, 1.0);
            SkillFx.play(level, "mace.smash_ground", spot, 1.2, 0.6);
            Kit.shakeNear(level, spot, 8, 0.15, 0.2);
            long t0 = Tasks.now();
            for (LivingEntity e : Kit.visibleFrom(sp, V.up(spot, 1.2), Kit.around(sp, spot, radius))) {
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, t0), ok, () -> {
                    if (Kit.skillHit(sp, e, w, k.num("mult")) <= 0) return;
                    bound.put(e.getUUID(), e);
                    bind(e, bind, slow);
                    Vec3 feet = e.position();
                    thorn(sp, level, feet, 0.55 + Kit.bodyPad(e), glow, Opt.of("v", 1.0, "life", hold, "grow", 0.08, "full", 3.0));
                    SkillFx.ring(level, V.up(feet, 0.35), V.UP, glow, 0.55 + Kit.bodyPad(e), 0.35, 0);
                });
            }
        });
        // 꽃이 핌: 땅에 활짝 핀 빛의 꽃 + 가운데 위의 꽃 (보는 사람 쪽) · 꽃잎 · 번쩍임 → 둘레 적이 터짐
        Tasks.later(fly + bind, () -> {
            if (!ok.getAsBoolean()) return;
            RuneKit.rfx(level, "bloomflat", V.up(spot, 0.09), Opt.of("dir", V.UP, "size", radius * 0.9, "life", 0.9, "frames", 4.0, "fps", 12.0, "fade", 0.45,
                    "spin", Kit.rand() * 360));
            Vec3 mid = V.up(spot, 1.3);
            double near = HeroKit.near(sp.getEyePosition(), mid, 0.45);
            RuneKit.rfx(level, "bloom", mid, Opt.of("size", 1.7 * near, "life", 0.6, "frames", 4.0, "fps", 14.0, "fade", 0.5, "grow", 0.3));
            leaves(level, V.up(spot, 0.8), 14, PETAL, 4.5, 3.0, 1.0);
            SkillFx.burst(level, mid, WHITE, 2.4 * near, 0.3);
            SkillFx.shock(level, spot, core, radius / 2.6, 0.45);
            SkillFx.play(level, "chime.amethyst_block", spot, 1.2, 1.4);
            SkillFx.play(level, "firework.twinkle", spot, 1.4, 0.8);
            SkillFx.play(level, "item.bone_meal.use", spot, 0.9, 1.0);
            long t0 = Tasks.now();
            List<LivingEntity> held = new ArrayList<>();
            for (LivingEntity e : bound.values()) {
                if (Kit.canHit(sp, e) && Math.hypot(e.getX() - spot.x, e.getZ() - spot.z) <= radius + 2.5 + Kit.bodyPad(e) && Math.abs(e.getY() - spot.y) <= 4) {
                    held.add(e);
                }
            }
            for (LivingEntity e : Kit.union(Kit.visibleFrom(sp, V.up(spot, 1.2), Kit.around(sp, spot, radius + 0.5)), held)) {
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, t0), ok, () -> {
                    if (Kit.skillHit(sp, e, w, k.num("afterMult")) <= 0) return;
                    Vec3 b = Kit.bodyAt0(e, mid);
                    RuneKit.rfx(level, "bloom", b, Opt.of("size", 0.6, "life", 0.35, "frames", 4.0, "fps", 16.0, "fade", 0.5, "cell", 0.0));
                    if (!Aegis.bossLike(e) && !e.isRemoved()) Kit.knockback(e, 0, 0, 0.35);
                });
            }
        });
        return true;
    }

    // ------------------------------------------------------------ 스킬 2 세계수 강림

    /**
     * 세계수 강림: 도끼로 땅을 내리치면 뿌리가 땅속으로 달려가 조준한 곳 (range 칸, 가로 min 칸보다 가까우면 min 칸 앞) 에 모여듦 — delay 틱 동안 땅의 문양 ·
     * 모여드는 뿌리 · 솟는 잎 → 거대한 세계수가 솟음 (height 칸 — 굴 · 지붕 아래면 트인 높이에 맞게, 가까우면 화면을 덮지 않게 조금 작게, grow 틱 동안 자람):
     * 반지름 radius 칸의 적을 뿌리가 띄우고 묶음 (mult, 구속 slow — 잎이 다 쏟아질 때까지) → pulseGap 틱마다 pulses 번 금빛 잎이 쏟아짐 (pulseMult · 마법 피해
     * — 반지름 radius + 1 칸, 묶인 적은 밀려났어도 radius + 2.5 칸 안이면) → stay 틱 뒤 잎이 흩날리며 사라짐 (fade 틱).
     * 준 피해의 heal 만큼 회복 (도끼 규칙 딜탱 — 한 번 쓸 때 실제로 회복한 양이 최대 체력의 healCap 까지). 땅을 조준하지 않으면 (허공) 재사용 대기 없이 거절
     */
    static boolean tree(ServerPlayer sp, Weapon w, Obj k) {
        ServerLevel level = sp.level();
        Vec3 spot = Gungnir.fallSpot(sp, k);
        if (spot == null) {
            Notify.warn(sp, "§c" + k.str("name") + ": §7땅을 조준해야 합니다 (" + k.text("range") + "칸 안)");
            return false;
        }
        String life = Kit.lifeOf(sp);
        BooleanSupplier ok = () -> Kit.sameLife(sp, life);
        double[][] cs = HeroKit.colors(k);
        double[] glow = cs[0];
        double[] core = cs[1];
        double radius = k.num("radius");
        int delay = (int) k.num("delay");
        int grow = Math.max(1, (int) k.num("grow"));
        int stay = (int) k.num("stay");
        int fade = (int) k.num("fade");
        int pulses = (int) k.num("pulses");
        int pulseGap = (int) k.num("pulseGap");
        int slow = (int) k.num("slow");
        // 나무 크기: 가까우면 조금 작게, 트인 높이가 모자라면 그만큼
        double want = k.num("height") * RuneKit.nearK(sp, V.up(spot, 2), 14, 0.6);
        double room = Kit.skyRoom(level, V.up(spot, 0.2), want + 2, true) + 0.2;
        double height = Math.max(2.5, Math.min(want, room));
        double half = height / (2 * TREE_FILL); // 그림 반 크기
        Vec3 center = V.up(spot, TREE_BASE * half);
        Vec3 canopy = V.up(spot, height * 0.82);
        // 내리침: 발밑 충격 + 뿌리가 땅속으로 조준한 곳까지 달려감 (delay 의 앞 절반 동안)
        Vec3 p0 = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 start = V.add(p0, f, 1.0);
        SkillFx.shock(level, V.up(start, 0.05), glow, 0.6, 0.3);
        SkillFx.play(level, "mace.smash_ground", start, 0.9, 1.0);
        SkillFx.play(level, "block.roots.break", start, 0.6, 1.0);
        int run = Math.max(2, delay / 2);
        for (int j = 0; j < run; j++) {
            int jj = j;
            Tasks.later(j, () -> {
                if (!ok.getAsBoolean()) return;
                Vec3 p = V.add(start, V.sub(spot, start), (jj + 1.0) / run);
                rootStep(sp, level, p, glow, core, 0.7);
                if (jj % 3 == 0) SkillFx.play(level, "block.roots.break", p, 0.8 + 0.05 * jj, 0.6);
            });
        }
        // 예고: 땅의 문양 (그려짐) · 모여드는 뿌리 금 · 솟는 잎 · 돋아나는 싹
        for (int t = 0; t < delay; t += 2) {
            int tt = t;
            Tasks.later(t, () -> {
                if (!ok.getAsBoolean()) return;
                double g = (tt + 2.0) / delay;
                SkillFx.rune(level, spot, Opt.of("size", radius * 0.95, "life", 0.13, "progress", Math.min(1, g * 1.2), "spin", tt * 9.0, "color", glow,
                        "alpha", 0.9));
                SkillFx.fx(level, "rpg:tele_disc", V.up(spot, 0.05), Opt.of("size", radius * (0.25 + 0.6 * g), "life", 0.13, "color", DEEP,
                        "alpha", 0.5 + 0.3 * g));
                if (tt % 4 == 0) {
                    double a = Kit.rand() * Math.PI * 2;
                    double rr = radius * (0.45 + Kit.rand() * 0.5);
                    Vec3 q = new Vec3(spot.x + Math.cos(a) * rr, spot.y, spot.z + Math.sin(a) * rr);
                    if (nearCaster(sp, q)) return;
                    thorn(sp, level, q, 0.35 + 0.3 * g, core, Opt.of("v", 0.0, "life", 0.5, "grow", 0.4, "full", 3.0));
                    leaves(level, V.up(q, 0.2), 2, LEAF, 0.4, 2.5, 0.8);
                }
            });
        }
        // 회복 (천지개벽 · 신창 강림과 같은 규칙)
        Map<UUID, LivingEntity> struck = new LinkedHashMap<>();
        Heal heal = new Heal(RpgHealth.maxOf(sp) * k.numOr("healCap", 1));
        double healRate = k.numOr("heal", 0);
        DoubleConsumer addHeal = got -> {
            if (healRate <= 0) return;
            heal.pool += got;
            if (heal.pending) return;
            heal.pending = true;
            Tasks.later(1, () -> {
                heal.pending = false;
                if (heal.pool <= 0 || heal.left <= 0 || !ok.getAsBoolean()) return;
                double amount = Math.min(heal.left, heal.pool * healRate);
                heal.pool = 0;
                double healed = RpgHealth.healBy(sp, amount);
                heal.left -= Math.max(0, healed);
                if (healed > 0) {
                    DamageNumbers.show(sp, healed, DamageNumbers.Style.HEAL, "", 0);
                    // 생명의 빛이 나무에서 나에게로
                    SkillFx.beam(level, canopy, V.up(sp.position(), 1.0), LEAF, Opt.of("half", 0.4, "thick", 0.1, "life", 0.4, "sweep", 0.3, "gap", 0.9,
                            "alpha", 0.8));
                }
            });
        };
        boolean[] grown = {false};
        int boundFor = pulseGap * pulses + 10;
        // 솟음: 세계수 (자라는 4장 → 다 자란 장을 stay 동안, 끝 fade 동안 흐려짐) + 사방으로 뿌리 · 금 · 고리 · 잎 → 둘레 적을 띄우고 묶음
        Tasks.later(delay, () -> {
            if (!ok.getAsBoolean()) return;
            grown[0] = true;
            double life0 = (stay + fade) / 20.0;
            RuneKit.rfx(level, "yggdrasil", center, Opt.of("size", half, "life", life0, "frames", 4.0, "fps", 80.0 / grow, "fade", fade / (double) (stay + fade)));
            SkillFx.burst(level, V.up(spot, 0.8), core, 4.0, 0.4);
            SkillFx.burst(level, V.up(spot, height * 0.5), glow, 3.0, 0.35);
            SkillFx.shock(level, spot, glow, radius / 2.1, 0.55);
            SkillFx.shock(level, spot, core, radius / 4.2, 0.4);
            SkillFx.rune(level, spot, Opt.of("size", radius * 0.95, "life", life0, "color", glow, "alpha", 0.7, "rate", 15.0));
            SkillFx.fx(level, "rpg:ground_crack", V.up(spot, 0.05), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", radius * 1.4, "life", 1.8,
                    "color", DEEP, "alpha", 0.85));
            leaves(level, V.up(spot, 1.0), 14, LEAF, 4.0, 5.0, 1.2);
            SkillFx.play(level, "mace.heavy_smash_ground", spot, 0.7, 1.0);
            SkillFx.play(level, "block.roots.break", spot, 0.5, 1.0);
            SkillFx.play(level, "random.explode", spot, 0.6, 0.7);
            SkillFx.play(level, "beacon.activate", spot, 0.8, 1.0);
            Kit.shakeNear(level, spot, 14, 0.4, 0.4);
            long t0 = Tasks.now();
            Vec3 eye = V.up(spot, 1.2);
            for (LivingEntity e : Kit.visibleFrom(sp, eye, Kit.around(sp, spot, radius))) {
                Kit.hitAt(sp, e, Kit.reserveHit(sp, e, t0), ok, () -> {
                    double got = Kit.skillHit(sp, e, w, k.num("mult"));
                    if (got <= 0) return;
                    struck.put(e.getUUID(), e);
                    addHeal.accept(got);
                    SkillFx.burst(level, Kit.bodyAt0(e, eye), core, 0.9, 0.25);
                    thorn(sp, level, e.position(), 0.7 + Kit.bodyPad(e), glow, Opt.of("v", 2.0, "life", boundFor / 20.0, "grow", 0.1, "full", 3.0));
                    if (!Aegis.bossLike(e) && !e.isRemoved()) Kit.knockback(e, 0, 0, 0.45);
                    bind(e, boundFor, slow);
                });
            }
        });
        // 뿌리가 사방으로 (세 틱에 나눠 — 안쪽 고리부터)
        double a0 = Kit.rand() * Math.PI * 2;
        for (int ring = 0; ring < 3; ring++) {
            int rr = ring;
            Tasks.later(delay + ring, () -> {
                if (!ok.getAsBoolean()) return;
                for (int j = 0; j < 8; j++) {
                    double a = a0 + j / 8.0 * Math.PI * 2 + rr * 0.2;
                    double dist = radius * (0.3 + 0.3 * rr) + Kit.rand() * 0.4;
                    Vec3 p = new Vec3(spot.x + Math.cos(a) * dist, spot.y, spot.z + Math.sin(a) * dist);
                    if (nearCaster(sp, p)) continue;
                    thorn(sp, level, p, 0.9 - 0.15 * rr + Kit.rand() * 0.3, glow, Opt.of("life", 1.2, "grow", 0.25, "full", 3.0));
                }
            });
        }
        // 잎이 쏟아짐: 잎 위가 번쩍 · 금빛 잎이 떨어짐 · 잎의 빛이 적마다 꽂힘 (마법 피해)
        for (int i = 1; i <= pulses; i++) {
            int ii = i;
            Tasks.later(delay + pulseGap * i, () -> {
                if (!ok.getAsBoolean()) return;
                long t0 = Tasks.now();
                List<LivingEntity> near = Kit.visibleFrom(sp, V.up(spot, 1.2), Kit.around(sp, spot, radius + 1));
                List<LivingEntity> held = new ArrayList<>();
                for (LivingEntity e : struck.values()) {
                    if (e.isRemoved()) continue;
                    if (Math.hypot(e.getX() - spot.x, e.getZ() - spot.z) <= radius + 2.5 + Kit.bodyPad(e) && Math.abs(e.getY() - spot.y) <= 5) held.add(e);
                }
                List<LivingEntity> list = Kit.union(near, held);
                RuneKit.rfx2(level, "flare", canopy, Opt.of("size", 2.6, "life", 0.35, "fade", 0.6, "spin", Kit.rand() * 90), glow, WHITE, new int[] {0, 1}, null);
                leaves(level, canopy, 12, SPROUT, 2.5, -2.0, 1.4);
                SkillFx.shock(level, spot, glow, 1.4 + 0.4 * ii, 0.35);
                SkillFx.play(level, "chime.amethyst_block", spot, 0.9 + 0.15 * ii, 1.2);
                SkillFx.play(level, "block.azalea_leaves.break", spot, 0.7, 1.0);
                for (int j = 0; j < list.size(); j++) {
                    LivingEntity e = list.get(j);
                    if (j < 8) {
                        Vec3 b = Kit.bodyAt0(e, canopy);
                        SkillFx.beamAway(level, sp.getEyePosition(), canopy, b, glow, Opt.of("half", 0.45, "thick", 0.14, "life", 0.3, "gap", 0.9, "alpha", 0.9));
                    }
                    Kit.hitAt(sp, e, Kit.reserveHit(sp, e, t0), ok, () -> {
                        double got = Kit.skillHit(sp, e, w, k.num("pulseMult"), Opt.of("cause", Kit.Cause.MAGIC));
                        if (got <= 0) return;
                        addHeal.accept(got);
                        Vec3 b = Kit.bodyAt0(e, canopy);
                        RuneKit.rfx(level, "bloom", b, Opt.of("size", 0.5, "life", 0.3, "frames", 4.0, "fps", 16.0, "fade", 0.5));
                    });
                }
            });
        }
        // 흩날림: 다 자란 나무의 잎이 흩날리며 사라짐 (솟기 전에 끊겼으면 없음 — 솟은 뒤에는 쓴 사람이 쓰러져도 나무는 그림대로 흐려짐)
        Tasks.later(delay + stay, () -> {
            if (!grown[0]) return;
            leaves(level, canopy, 16, SPROUT, 3.0, -1.0, 1.6);
            leaves(level, V.up(spot, height * 0.6), 10, LEAF, 2.5, -0.5, 1.4);
            SkillFx.play(level, "block.azalea_leaves.break", spot, 0.6, 1.0);
            SkillFx.play(level, "beacon.deactivate", spot, 1.2, 0.6);
        });
        return true;
    }
}
