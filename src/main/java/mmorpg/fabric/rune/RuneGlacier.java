package mmorpg.fabric.rune;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.FxDisplays;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * 0.24.0 여덟째 한정 초월 룬 만년빙하 (glacier · glacierField — 사용자 요청 "얼음 필드를 소환해서 해당 얼음 필드에 있으면 지속적으로 아래에서 얼음이 자라나
 * 데미지를 주거나 계속 있을 시 적을 얼림" · "얼음이 자라는 것은 거대한 얼음이 자라서 시각적으로 쎄다고 느끼게"). 다른 한정 룬처럼 위력 P 의 적용 스탯 =
 * 네 스탯의 합 × 1.0, 마법 피해, 피해는 몹 무적 시간을 피해 (RuneLimited.hit). 수치는 RUNES.glacier (rules_more.json), 돌파는 rune_break.json
 * (3 · 6돌 + 6돌 빙하의 왕관 — finale, 피해 없음). 얼음은 블록 표시 엔티티 (FxDisplays — 3D, 충돌 · 피해 없음).
 * <ol>
 *   <li>얼어붙음 (charge 틱): 손에서 조준한 곳 (range 칸) 으로 서리 빛이 날아가고, 그 자리에서 얼음 마법진 (rpg:rfx_frost — 눈꽃 문양) 이 반지름 radius 칸까지
 *       퍼지며 땅이 가운데부터 바깥으로 얼음판 (얼음 · 굳은 얼음 블록 조각) 으로 덮임. 경계에 작은 얼음 결정이 빙 둘러 솟음 (필드 경계 표시)</li>
 *   <li>거대한 얼음 (gap 틱마다 hits 번): 필드 안 적마다 발밑이 얼음빛으로 갈라진 뒤 거대한 얼음 결정 (키 4.5 ~ 8칸 · 속은 푸른 얼음, 겉은 투명한 얼음 —
 *       블록을 꼭짓점으로 세워 늘인 뾰족한 결정 + 곁결정 셋 · 타고 오르는 빛) 이 땅에서 솟구침 — 큰 결정은 적 바로 뒤 (시전자 쪽에서 볼 때) 에서 솟아 적과
 *       숫자가 얼음 앞에 보임 → 얼음이 몸에 닿는 틱 (솟기 시작 + STRIKE) 에 위력 × mult (얼음빛 숫자), 잠시 뒤 부서져 가라앉음.
 *       적이 없는 자리에도 결정이 솟아 필드가 살아 있게 (시전자 눈 · 3인칭 카메라 가까이 · 시전자 쪽 앞자리는 빼고)</li>
 *   <li>빙결: 같은 적이 이번 시전의 얼음에 chill 번 꿰뚫리면 얼어붙음 — frost 틱 동안 얼음 덩어리 (투명한 얼음 블록 + 삐죽한 얼음 조각) 에 갇혀 움직이지도
 *       공격하지도 못함 (RuneKit.stun — 구속 XI · 나약함 V). 얼어 있는 동안 피해 숫자 앞에 ❄ · 덩어리는 몸을 따라감 · 얼어 있는 동안은 더 쌓이지 않음 ·
 *       풀리면 얼음이 부서짐. 보스는 얼지 않음 (서리만 — 보스 싸움의 흐름을 지킴)</li>
 *   <li>빙하 붕괴 (마지막 얼음 finish 틱 뒤): 경계에서 가운데로 서리가 모인 뒤 필드 한가운데에서 거대한 빙하 첨탑 (키 9.5칸) 과 둘레의 큰 결정들이 꽃처럼
 *       피어 솟음 → 필드 안 모든 적 위력 × crush (얼음빛 · 흰빛 굵은 숫자 !) · 띄움 (얼어 있던 적은 얼음째 부서짐), 10틱 뒤 첨탑 · 얼음판이 한꺼번에 부서져
 *       얼음 조각이 사방으로 튐</li>
 *   <li>6돌 빙하의 왕관 (finale — 피해 없음): 부서진 뒤 필드 경계에 거대한 얼음 기둥 열이 왕관처럼 솟고 하늘에 오로라 장막 (rpg:rfx_aurora) · 눈이 내림,
 *       하늘빛이 극야 (rpg:ult_polar) 로 바뀜</li>
 * </ol>
 * 화면 색 연출 (하늘빛 · 번쩍임) 은 3돌부터 (짙은 사파이어 하늘 rpg:ult_glacier · 번쩍임은 하늘 가르기의 얼음빛 — RuneKit.ULT_ALIAS). 쓰러지거나 차원을 옮기면
 * 남은 것은 멈춤 (얼음은 정해진 틱 뒤 FxDisplays 가 지움, 얼어 있는 적은 제때 풀림).
 */
final class RuneGlacier {
    private RuneGlacier() {}

    static final String FN = "glacierField";
    private static final double[][] COLOR = RuneKit.colorOf("glacier");
    /** 얼음빛 (계열 첫째 색) · 흰 심지 · 짙은 얼음 (그늘 · 금) · 서리 (안개 · 눈) · 오로라 (초록 · 보라) */
    static final double[] ICE = COLOR[0];
    static final double[] CORE = COLOR[1];
    static final double[] DEEP = {0.1, 0.32, 0.72};
    static final double[] FROST = {0.86, 0.95, 1.0};
    static final double[] AURORA = {0.3, 1.0, 0.7};
    static final double[] AURORA_CORE = {0.84, 1.0, 0.92};
    static final double[] AURORA2 = {0.62, 0.44, 1.0};
    static final double[] AURORA2_CORE = {0.95, 0.86, 1.0};
    /** 얼어 있는 적의 피해 숫자 앞 표시 */
    static final String MARK = "§b❄";
    /** 얼음의 밝기 (밤 · 동굴에서도 얼음빛이 보이게 — 블록 빛 15) */
    static final Brightness GLOW = new Brightness(15, 15);
    /**
     * 하늘빛: 짙은 사파이어빛 (rpg:ult_glacier — 16 ~ 96칸, rules_more ULT_FOGS_MORE) · 6돌 왕관은 극야 (rpg:ult_polar — 오로라가 보이게 짙은 남청).
     * 사진 검토: 옅은 얼음빛 하늘 (rpg:ult_ice) 에서는 투명한 얼음이 하늘빛에 묻힘 — 어두운 하늘에서 빛나는 얼음이 또렷
     */
    static final String SKY_FOG = "rpg:ult_glacier";
    static final String CROWN_FOG = "rpg:ult_polar";
    /** 시전자 눈 · 3인칭 카메라에서 이 칸 안에는 꾸밈 결정 · 경계 결정을 세우지 않음 (1인칭 화면을 덮지 않게) */
    static final double CLEAR = 3.4;
    /** 얼음판 조각 크기 · 고리 간격 (칸) */
    static final double TILE = 2.0;
    static final double TILE_STEP = 1.7;
    /** 거대한 얼음: 솟기 전 땅속에 띄우는 틱 · 솟는 틱 · 서 있는 틱 · 부서지는 틱 · 솟기 시작한 뒤 피해가 들어가는 틱 (얼음이 몸에 닿을 때 숫자) */
    static final int PRE = 2;
    static final int STRIKE = 1;
    static final int RISE = 2;
    static final int HOLD = 6;
    static final int BREAK = 4;
    /** 붕괴: 서리가 모이는 틱 (붕괴 전) · 첨탑이 서 있다 부서지기까지 */
    static final int GATHER = 10;
    static final int SHATTER = 10;
    /** 붕괴 번쩍임 (들어옴 · 머묾 · 걷힘 초 — 3 · 6돌 돌파가 머묾 · 걷힘을 늘림): 기본보다 짧게 (첨탑이 번쩍임에 묻히지 않게 — 무간참역의 일섬과 같음) */
    static final double[] FLASH_TIME = {0.03, 0.0, 0.25};

    private static final BlockState ICE_BLOCK = Blocks.ICE.defaultBlockState();
    private static final BlockState PACKED = Blocks.PACKED_ICE.defaultBlockState();
    private static final BlockState BLUE = Blocks.BLUE_ICE.defaultBlockState();

    /** 얼어 있는 적 하나: 풀리는 틱 · 얼음 덩어리 (몸을 따라감) · 덩어리를 띄운 자리 (발) · 시전자 */
    private static final class Frozen {
        final LivingEntity e;
        final long until;
        final List<Display> ice;
        Vec3 at;

        Frozen(LivingEntity e, long until, List<Display> ice, Vec3 at) {
            this.e = e;
            this.until = until;
            this.ice = ice;
            this.at = at;
        }
    }

    /** 시전 하나: 적마다 쌓인 냉기 · 얼린 수 · 거대한 얼음 수 (시험 · 사진첩) */
    static final class Cast {
        final Map<UUID, Integer> chill = new HashMap<>();
        final long at = Tasks.now();
        int frozen;
        int giants;
        double radius;
    }

    private static final Map<UUID, Frozen> FROZEN = new HashMap<>();
    /** 시험 · 사진첩: 사람마다 마지막 시전 */
    private static final Map<UUID, Cast> LAST = new HashMap<>();

    static void register() {
        RuneCast.register(FN, RuneGlacier::cast);
        java.util.function.Function<LivingEntity, String> vm = DamageNumbers.victimMark;
        DamageNumbers.victimMark = e -> {
            String s = vm.apply(e);
            return frozen(e) ? MARK + (s == null ? "" : s) : s;
        };
        ServerTickEvents.END_SERVER_TICK.register(RuneGlacier::tick);
        RuneSystem.onForget(LAST::remove);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            FROZEN.clear();
            LAST.clear();
        });
    }

    /** 지금 얼어 있는지 (이 룬의 빙결) */
    static boolean frozen(Entity e) {
        if (e == null) return false;
        Frozen f = FROZEN.get(e.getUUID());
        return f != null && f.until > Tasks.now() && !e.isRemoved();
    }

    /** 시험용: 이 사람의 마지막 시전 {얼린 적 수, 거대한 얼음 수, 필드 반지름, 쓴 틱 (Tasks.now)} (없으면 null) */
    static double[] last(UUID id) {
        Cast c = LAST.get(id);
        return c == null ? null : new double[] {c.frozen, c.giants, c.radius, c.at};
    }

    /** 이 사람이 지금 쓴 만년빙하의 하늘빛 시간 배율 (3돌 미만이면 1 — 긴 룬이라 6돌 배율 1.5 를 나눠 씀) */
    static double skyTicksOf(ServerPlayer sp) {
        mmorpg.core.RuneBreak.Fx fx = Breakthrough.castFx(sp, FN);
        return fx != null && fx.skyTicks() > 0 ? fx.skyTicks() : 1;
    }

    // ------------------------------------------------------------ 얼음 결정 (블록 표시)

    /** 블록을 꼭짓점으로 세움 (대각선 (1, 1, 1) → 위): y 로 늘이면 끝이 뾰족한 결정 */
    private static final Quaternionf TIP_UP = new Quaternionf().rotationTo(new Vector3f(1, 1, 1).normalize(), new Vector3f(0, 1, 0));
    private static final double DIAG = Math.sqrt(3);
    /** 꼭짓점으로 세운 블록 1 의 가장 굵은 곳 (가운데 꼭짓점 여섯) 의 반지름 */
    private static final double WIDE = Math.sqrt(2.0 / 3.0);
    /** 땅속에 묻히는 몫 (아래 꼭짓점 쪽 — 위로 갈수록 가늘어지는 꼴만 보이게) */
    private static final double BURY = 0.42;

    /** 결정 하나의 모양: 축 (단위 — 위 쪽) · 땅 위 높이 h · 가장 굵은 곳 지름 w · 축 둘레 돌림 (도) */
    record Crystal(Vec3 axis, double h, double w, double spin) {
        /** rise 0 = 땅속에 다 묻힘 · 1 = 제자리, wk · hk = 굵기 · 높이 배율 (부서질 때 오그라듦), sink = 아래로 더 내림 (칸) */
        Transformation at(double rise, double wk, double hk, double sink) {
            double total = h * hk / (1 - BURY);
            double sy = total / DIAG;
            double sxz = (w * wk / 2) / WIDE;
            Quaternionf right = new Quaternionf().rotationY((float) Math.toRadians(spin)).mul(TIP_UP);
            Quaternionf left = new Quaternionf().rotationTo(0F, 1F, 0F, (float) axis.x, (float) axis.y, (float) axis.z);
            double down = total * BURY + (1 - rise) * (total * (1 - BURY) + 0.3);
            Vec3 t = V.add(V.scale(axis, -down), V.DOWN, sink);
            return new Transformation(new Vector3f((float) t.x, (float) t.y, (float) t.z), left, new Vector3f((float) sxz, (float) sy, (float) sxz), right);
        }

        /** 결정 끝 (base 기준) */
        Vec3 tip(Vec3 base) {
            return V.add(base, axis, h);
        }
    }

    /** 위에서 기울기 tilt 도 · 방위 dir (수평 단위) 쪽으로 기운 축 */
    static Vec3 tilted(Vec3 dir, double tilt) {
        double r = Math.toRadians(tilt);
        return V.unit(V.add(V.scale(V.UP, Math.cos(r)), V.flat(dir), Math.sin(r)));
    }

    /** 땅속에 숨긴 채 결정을 띄움 (life 틱 뒤 지움) */
    static Display.BlockDisplay spawnCrystal(ServerLevel level, Vec3 base, BlockState st, Crystal c, double wk0, int life) {
        return FxDisplays.block(level, base, st, c.at(0, wk0, 1, 0), life, GLOW);
    }

    /**
     * 솟는 결정 하나 (겉 · 속 둘 — core 가 true 면 속에 푸른 얼음): 지금 땅속에 띄우고 delay 틱 뒤 rise 틱 동안 솟음 → hold 틱 → brk 틱 동안 부서져 가라앉음.
     * 돌려준 목록 = 띄운 표시 (없으면 빈 목록)
     */
    static List<Display> erupt(ServerLevel level, Vec3 base, Crystal c, boolean core, BlockState shell, int delay, int rise, int hold, int brk) {
        List<Display> out = new ArrayList<>();
        int life = delay + rise + hold + brk + 2;
        Display.BlockDisplay outer = spawnCrystal(level, base, shell, c, 0.7, life);
        Crystal inner = new Crystal(c.axis(), c.h() * 0.9, c.w() * 0.62, c.spin() + 30);
        Display.BlockDisplay in = core ? spawnCrystal(level, base, BLUE, inner, 0.7, life) : null;
        if (outer != null) out.add(outer);
        if (in != null) out.add(in);
        int d = Math.max(2, delay);
        RuneKit.later(d, () -> {
            FxDisplays.move(outer, c.at(1, 1, 1, 0), rise);
            FxDisplays.move(in, inner.at(1, 1, 1, 0), rise);
        });
        RuneKit.later(d + rise + hold, () -> {
            FxDisplays.move(outer, c.at(1, 0.25, 0.55, 0.5 + c.h() * 0.25), brk);
            FxDisplays.move(in, inner.at(1, 0.2, 0.5, 0.5 + c.h() * 0.25), brk);
        });
        return out;
    }

    /** 얼음 조각 파티클 (그 자리 블록 깨짐 — 얼음) */
    static void shards(ServerLevel level, Vec3 at, int n, double spread, double up, double speed) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ICE_BLOCK), at.x, at.y, at.z, n, spread, up, spread, speed);
    }

    static void snow(ServerLevel level, Vec3 at, int n, double spread, double up, double speed) {
        level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, n, spread, up, spread, speed);
    }

    /** 땅 높이 (x, z) — hint 둘레에서 못 찾으면 hint */
    static double groundY(ServerLevel level, double x, double z, double hint) {
        return RuneTerra.groundY(level, x, z, hint);
    }

    /** 얼음 마법진 (rpg:rfx_frost — 빛 + 심지, 땅에 눕힘). o: life · spin · spinRate · grow · alpha · fade · coreAlpha · dir */
    static void frostSigil(ServerLevel level, Vec3 at, double r, double[] glow, double[] core, Opt o) {
        Vec3 at2 = o.has("dir") ? at : V.up(at, 0.1);
        RuneKit.rfx2(level, "frost", at2, Opt.of("dir", o.vec("dir", V.UP), "size", r, "life", o.num("life", 1), "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 360,
                "spinRate", o.num("spinRate", 0), "grow", o.num("grow", 0), "alpha", o.num("alpha", 0.9), "fade", o.num("fade", 0.3)), glow, core, new int[] {0, 1},
                o.numOrNull("coreAlpha"));
    }

    // ------------------------------------------------------------ 시전

    static boolean cast(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        boolean fin = RuneLimited.finale(sp, r);
        double R = k.num("radius");
        Vec3 c = RuneKit.groundAim(sp, k.numOr("range", 20));
        int charge = (int) k.num("charge");
        int gap = (int) k.num("gap");
        int hits = (int) k.num("hits");
        int chill = Math.max(1, (int) k.numOr("chill", 3));
        int frost = (int) k.numOr("frost", 40);
        int lastHit = charge + (hits - 1) * gap;
        int crushAt = lastHit + (int) k.numOr("finish", 14);
        int breakAt = crushAt + SHATTER;
        double refY = c.y;
        RuneLimited.Tally tally = new RuneLimited.Tally();
        Cast cast = new Cast();
        cast.radius = R;
        LAST.put(sp.getUUID(), cast);
        double skyK = skyTicksOf(sp);
        RuneKit.ultSkyFor(sp, level, c, FN, (int) Math.round((crushAt + 30) / skyK), SKY_FOG);

        // ---- 쓸 때: 시전자 발밑 서리 룬 · 손에서 조준한 곳으로 서리 빛 · 그 자리에서 얼음 마법진이 퍼짐 · 소리
        SkillFx.rune(level, sp.position(), Opt.of("size", 1.5, "life", (charge + 4) / 20.0, "color", ICE, "alpha", 0.9, "rate", 140.0, "progress", 1.0));
        Vec3 hand = V.add(V.up(sp.getEyePosition(), -0.4), V.rightOf(Kit.facingOf(sp)), 0.35);
        RuneKit.flyStreak(level, hand, V.up(c, 0.4), 32, ICE, CORE, 0.9, 0.3);
        RuneKit.selfCue(sp, ICE, CORE, true);
        frostSigil(level, c, R * 0.25, ICE, CORE, Opt.of("life", charge / 20.0, "grow", 3.0, "spinRate", 40.0, "alpha", 0.85, "fade", 0.15));
        RuneKit.novaRing(level, c, R * 0.2, 4.0, ICE, CORE, Opt.of("life", charge / 20.0, "fade", 0.5));
        SkillFx.play(level, "block.amethyst_block.resonate", c, 0.7, 1.2);
        SkillFx.play(level, "entity.player.hurt_freeze", c, 0.6, 1.0);
        SkillFx.play(level, "block.glass.place", c, 0.6, 0.9);
        SkillFx.play(level, "beacon.power", sp.position(), 1.6, 0.6);

        // ---- 얼어붙음: 가운데부터 바깥으로 얼음판 · 퍼지는 서리 · 경계의 작은 결정
        Field field = new Field(level, c, R, refY, breakAt + BREAK + 2);
        field.spread(sp, charge);
        Kit.ticks(t -> {
            if (!RuneKit.sameLife(sp, life) || t >= charge) return false;
            double rr = R * Math.min(1, (t + 2.0) / (charge - 2));
            if (t % 2 == 0) {
                for (int i = 0; i < 3; i++) {
                    double a = Kit.rand() * Math.PI * 2;
                    Vec3 p = new Vec3(c.x + Math.cos(a) * rr, refY, c.z + Math.sin(a) * rr);
                    p = new Vec3(p.x, groundY(level, p.x, p.z, refY) + 0.15, p.z);
                    if (RuneKit.camDist(sp, p) > 2.5) SkillFx.fx(level, "rpg:dust", V.up(p, 0.3), Opt.of("size", 1.1, "life", 0.6, "color", FROST, "alpha", 0.45));
                    snow(level, V.up(p, 0.3), 2, 0.3, 0.2, 0.02);
                }
            }
            if (t % 4 == 0) {
                SkillFx.play(level, "block.glass.place", c, 1.2 + t * 0.03, 0.5);
                RuneKit.glowCrack(level, c, Math.max(1.2, rr), DEEP, ICE, Opt.of("life", 0.5, "fade", 0.5, "v", (double) ((t / 4) % 2)));
            }
            return true;
        });
        RuneKit.later(charge, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            // 필드가 다 얼어붙음: 반지름 R 의 얼음 마법진 (붕괴까지) · 경계 고리 · 결정 울타리 · 소리
            frostSigil(level, c, R, ICE, CORE, Opt.of("life", (crushAt - charge + 6) / 20.0, "spinRate", 6.0, "alpha", 0.8, "fade", 0.1, "coreAlpha", 0.95));
            frostSigil(level, c, R * 0.52, DEEP, CORE, Opt.of("life", (crushAt - charge + 2) / 20.0, "spinRate", -14.0, "alpha", 0.55, "fade", 0.1));
            RuneKit.zonePulse(level, c, ICE, R, 0.5);
            field.fence(sp, crushAt - charge + SHATTER - 5); // 경계 결정은 붕괴 뒤 얼음판과 함께 부서짐 (솟는 데 2 + 3틱)
            SkillFx.play(level, "block.amethyst_cluster.break", c, 0.6, 1.2);
            SkillFx.play(level, "block.glass.break", c, 0.7, 0.8);
            SkillFx.play(level, "entity.warden.sonic_charge", c, 1.8, 0.4);
        });

        // ---- 필드가 살아 있는 동안: 눈송이 · 서리 안개 · 얼음 결정 반짝임 (3틱마다)
        RuneKit.later(charge, () -> Kit.ticks(t -> {
            if (!RuneKit.sameLife(sp, life) || t >= crushAt - charge) return false;
            if (t % 3 != 0) return true;
            for (int i = 0; i < 2; i++) {
                Vec3 p = anywhere(c, R, refY);
                p = new Vec3(p.x, groundY(level, p.x, p.z, refY), p.z);
                snow(level, V.up(p, 1.6 + Kit.rand() * 1.5), 2, 0.6, 0.4, 0.01);
                if (i == 0 && RuneKit.camDist(sp, p) > CLEAR) RuneKit.iceCrystal(sp, level, p, 0.45 + Kit.rand() * 0.3, ICE, Opt.of("life", 0.7, "full", 3.0));
            }
            if (t % 9 == 0) {
                double a = Kit.rand() * Math.PI * 2;
                Vec3 p = new Vec3(c.x + Math.cos(a) * R, refY, c.z + Math.sin(a) * R);
                if (RuneKit.camDist(sp, p) > CLEAR) SkillFx.fx(level, "rpg:dust", V.up(p, 0.4), Opt.of("size", 1.6, "life", 0.9, "color", FROST, "alpha", 0.4));
            }
            return true;
        }));

        // ---- 거대한 얼음: gap 틱마다 필드 안 적마다 발밑에서 솟음 (PRE 틱 전에 땅속에 띄우고 발밑이 갈라짐)
        for (int i = 0; i < hits; i++) {
            final int at = charge + i * gap;
            final int idx = i;
            List<Vec3> spots = new ArrayList<>();
            RuneKit.later(at - PRE, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                int drawn = 0;
                for (LivingEntity e : Kit.around(sp, c, R, 4)) {
                    if (drawn++ >= 10) break; // 적이 많으면 거대한 얼음은 10까지 (피해는 모두)
                    Vec3 base = new Vec3(e.getX(), Math.min(e.getY(), groundY(level, e.getX(), e.getZ(), e.getY())), e.getZ());
                    if (e.getY() - base.y > 6) base = V.up(e.position(), -0.4);
                    spots.add(base);
                    Vec3 away = V.flat(V.sub(base, sp.getEyePosition()));
                    if (V.hdist(base, sp.position()) < 1.0) away = Kit.facingOf(sp);
                    giant(level, base, e.getBbHeight(), e.getBbWidth(), away, PRE);
                    cast.giants++;
                    RuneKit.glowCrack(level, base, 1.3 + e.getBbWidth(), DEEP, ICE, Opt.of("life", 0.6, "fade", 0.5, "v", (double) (idx % 2)));
                    shards(level, V.up(base, 0.15), 6, 0.4, 0.05, 0.05);
                }
                // 적이 없는 자리에도: 꾸밈 결정 (적이 적을수록 많이 — 시전자가 필드 밖이면 시전자 쪽 앞자리는 빼고: 적을 가리지 않게)
                int extra = spots.isEmpty() ? 4 : spots.size() < 3 ? 2 : 1;
                for (int j = 0; j < extra; j++) {
                    Vec3 p = null;
                    for (int tries = 0; tries < 8 && p == null; tries++) {
                        Vec3 q = anywhere(c, R * 0.92, refY);
                        q = new Vec3(q.x, groundY(level, q.x, q.z, refY), q.z);
                        if (RuneKit.camDist(sp, V.up(q, 1.5)) > CLEAR + 1 && Math.abs(q.y - refY) < 3 && !nearSide(sp, c, R, q, 0.15)) p = q;
                    }
                    if (p == null) continue;
                    spots.add(p);
                    giant(level, p, 0.6 + Kit.rand() * 1.0, 0.5 + Kit.rand() * 0.3, null, PRE);
                }
            });
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (int s = 0; s < spots.size(); s++) {
                    Vec3 b = spots.get(s);
                    if (s < 12) {
                        shards(level, V.up(b, 0.4), 22, 0.45, 0.35, 0.18);
                        snow(level, V.up(b, 0.6), 8, 0.5, 0.5, 0.05);
                        RuneKit.novaRing(level, b, 0.8, 1.5, ICE, CORE, Opt.of("life", 0.35));
                        SkillFx.fx(level, "rpg:arrow_spark", V.up(b, 0.4), Opt.of("dir", V.UP, "speed", 7.0, "size", 0.3, "grow", 0.8, "life", 0.35, "color", CORE,
                                "alpha", 1.0));
                    }
                }
                Vec3 mid = spots.isEmpty() ? c : spots.get(0);
                SkillFx.play(level, "block.glass.break", mid, 0.55 + Kit.rand() * 0.15, 1.0);
                SkillFx.play(level, "block.amethyst_cluster.break", mid, 0.6 + Kit.rand() * 0.2, 1.0);
                SkillFx.play(level, "entity.player.hurt_freeze", mid, 0.7, 0.7);
                if (idx % 2 == 0) SkillFx.play(level, "block.basalt.break", mid, 0.5, 0.8);
                RuneKit.shakeAround(level, c, R + 6, 0.12, 0.2);
            });
            RuneKit.later(at + STRIKE, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (LivingEntity e : Kit.around(sp, c, R, 4)) {
                    RuneLimited.hit(sp, life, e, P, k.num("mult"), DamageNumbers.Style.FROST, tally, x -> chillUp(sp, level, x, cast, chill, frost, gap));
                }
            });
            RuneKit.later(at + RISE + HOLD, () -> {
                for (int s = 0; s < spots.size() && s < 12; s++) {
                    Vec3 b = spots.get(s);
                    shards(level, V.up(b, 1.2), 14, 0.5, 0.8, 0.12);
                }
                if (!spots.isEmpty()) SkillFx.play(level, "block.glass.break", spots.get(0), 1.1 + Kit.rand() * 0.2, 0.5);
            });
        }

        // ---- 빙하 붕괴: 경계에서 가운데로 서리가 모임 → 한가운데에서 빙하 첨탑 · 큰 결정이 피어 솟음 → 피해 · 띄움 → 부서짐
        RuneKit.later(crushAt - GATHER, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            for (int i = 0; i < 10; i++) {
                double a = Math.PI * 2 * i / 10 + Kit.rand() * 0.2;
                Vec3 from = new Vec3(c.x + Math.cos(a) * R, refY + 0.4, c.z + Math.sin(a) * R);
                RuneKit.flyStreak(level, from, V.up(c, 0.6), R / (GATHER / 20.0) * 1.1, ICE, CORE, 0.8, 0.28);
            }
            frostSigil(level, c, R * 0.7, ICE, CORE, Opt.of("life", GATHER / 20.0 + 0.1, "spinRate", 90.0, "grow", -0.5, "alpha", 0.9, "fade", 0.2));
            SkillFx.play(level, "block.beacon.power_select", c, 0.8, 1.0);
            SkillFx.play(level, "entity.warden.sonic_charge", c, 1.2, 0.8);
            SkillFx.play(level, "block.amethyst_block.resonate", c, 1.4, 1.0);
        });
        RuneKit.later(crushAt - 4, () -> {
            // 땅속에 띄움 → 붕괴 2틱 전에 솟기 시작 (3틱 — 숫자가 뜰 때 첨탑이 거의 다 솟음) → 얼음판과 함께 부서짐
            if (RuneKit.sameLife(sp, life)) spire(sp, level, c, R, refY, SHATTER - 1);
        });
        RuneKit.later(crushAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            // 번쩍임은 첨탑이 다 솟은 뒤 짧게 (사진 검토: 기본 번쩍임 · 6돌의 긴 머묾이 솟는 첨탑을 덮음)
            RuneKit.later(2, () -> RuneKit.ultFlashFor(sp, level, c, FN, "flash", Opt.of("time", FLASH_TIME.clone())));
            RuneKit.novaRing(level, c, R * 0.3, 3.2, ICE, CORE, Opt.of("life", 0.6));
            RuneKit.later(2, () -> RuneKit.novaRing(level, c, R * 0.2, 4.0, CORE, RuneKit.WHITE, Opt.of("life", 0.55)));
            RuneKit.glowCrack(level, c, R * 1.05, DEEP, ICE, Opt.of("life", (SHATTER + 30) / 20.0, "fade", 0.5, "v", 1.0));
            shards(level, V.up(c, 1.0), 90, R * 0.35, 0.8, 0.3);
            snow(level, V.up(c, 1.5), 40, R * 0.4, 1.0, 0.08);
            SkillFx.burst(level, V.up(c, 1.2), CORE, R * 0.45 * RuneKit.nearK(sp, V.up(c, 1), 8, 0.4), 0.45);
            SkillFx.play(level, "entity.generic.explode", c, 0.8, 0.9);
            SkillFx.play(level, "block.glass.break", c, 0.45, 1.3);
            SkillFx.play(level, "block.amethyst_cluster.break", c, 0.5, 1.3);
            SkillFx.play(level, "item.trident.thunder", c, 1.7, 0.5);
            RuneKit.shakeAround(level, c, R + 12, 0.55, 0.6);
            double lift = k.numOr("lift", 0.6);
            for (LivingEntity e : Kit.around(sp, c, R, 5)) {
                boolean wasFrozen = frozen(e);
                if (wasFrozen) thaw(e, true); // 얼어 있던 적은 얼음째 부서짐
                RuneLimited.hit(sp, life, e, P, k.num("crush"), DamageNumbers.Style.SHATTER, tally, x -> RuneKit.knock(x, V.sub(x.position(), c), 0.35, lift));
            }
        });
        RuneKit.later(breakAt, () -> {
            field.shatter();
            SkillFx.play(level, "block.glass.break", c, 0.7, 1.2);
            SkillFx.play(level, "block.glass.break", V.up(c, 4), 1.0, 1.0);
            SkillFx.play(level, "block.amethyst_block.break", c, 0.6, 1.0);
            shards(level, V.up(c, 3.0), 70, 1.2, 2.5, 0.25);
            shards(level, V.up(c, 0.4), 40, R * 0.45, 0.2, 0.2);
            // 사방으로 튀는 얼음 조각 (고드름 그림)
            for (int i = 0; i < 14; i++) {
                double a = Math.PI * 2 * i / 14 + Kit.rand() * 0.3;
                Vec3 from = V.up(c, 1.5 + Kit.rand() * 4);
                Vec3 d = V.unit(new Vec3(Math.cos(a), 0.25 + Kit.rand() * 0.5, Math.sin(a)));
                RuneKit.rfx2(level, "icicle", from, Opt.of("dir", d, "speed", 9.0 + Kit.rand() * 5, "size", 0.45 + Kit.rand() * 0.25, "life", 0.5, "fade", 0.3), ICE, CORE);
            }
            if (fin) RuneKit.later(2, () -> crown(sp, level, c, R, refY, life, skyK));
        });
        RuneLimited.saveTally(sp, FN, tally, crushAt + 10);
        return true;
    }

    /**
     * 시전자가 필드 밖 (가운데에서 반지름 × 0.8 넘게) 일 때 p 가 시전자 쪽 앞자리인지 (가운데에서 시전자 쪽으로 반지름 × k 넘게 — 그 자리의 결정은 적을 가림)
     */
    static boolean nearSide(ServerPlayer sp, Vec3 c, double R, Vec3 p, double k) {
        double d = V.hdist(sp.position(), c);
        if (d <= R * 0.8) return false;
        Vec3 toward = V.flat(V.sub(sp.position(), c));
        return V.dot(new Vec3(p.x - c.x, 0, p.z - c.z), toward) > R * k;
    }

    /** 필드 안 아무 곳 (가운데가 몰리지 않게 √) */
    static Vec3 anywhere(Vec3 c, double R, double y) {
        double rr = R * Math.sqrt(Kit.rand());
        double a = Kit.rand() * Math.PI * 2;
        return new Vec3(c.x + Math.cos(a) * rr, y, c.z + Math.sin(a) * rr);
    }

    /**
     * 거대한 얼음 결정 무리 (pre 틱 뒤 솟음): 큰 결정 (속 푸른 얼음 · 겉 투명한 얼음 — 키 bh × 1.5 + 1.6, 3.2 ~ 6칸 · 굵기 bw × 1.6 + 0.9) + 곁결정 셋.
     * behind 가 있으면 (적 — 시전자에서 적 쪽 수평 단위) 큰 결정은 몸 바로 뒤에서 조금 뒤로 기울어 솟고 곁결정 둘은 뒤 양옆 · 하나는 앞에 낮게
     * (적을 덮지 않게 — 몸과 피해 숫자가 얼음 앞에 보임), 없으면 (빈 자리) 큰 결정이 가운데 · 곁결정이 둘레
     */
    static void giant(ServerLevel level, Vec3 base, double bh, double bw, Vec3 behind, int pre) {
        double H = Math.max(4.5, Math.min(8.0, bh * 2.2 + 1.8));
        double W = Math.max(1.8, Math.min(3.6, bw * 2.0 + 1.1));
        double spin = Kit.rand() * 120;
        Crystal main;
        Vec3 b0;
        if (behind != null) {
            b0 = V.add(base, behind, bw * 0.5 + W * 0.3);
            main = new Crystal(tilted(behind, 6 + Kit.rand() * 6), H, W, spin);
            erupt(level, b0, main, true, ICE_BLOCK, pre, RISE, HOLD, BREAK);
            for (int j = 0; j < 3; j++) {
                boolean front = j == 2;
                double deg = front ? 180 + (Kit.rand() - 0.5) * 40 : (j == 0 ? -1 : 1) * (62 + Kit.rand() * 18);
                Vec3 out = V.turnY(behind, deg);
                Vec3 b = V.add(base, out, front ? bw * 0.5 + W * 0.45 : bw * 0.5 + W * (0.42 + Kit.rand() * 0.12));
                double h = H * (front ? 0.28 + Kit.rand() * 0.08 : 0.48 + Kit.rand() * 0.18);
                Crystal side = new Crystal(tilted(out, front ? 34 + Kit.rand() * 10 : 22 + Kit.rand() * 12), h, W * (front ? 0.36 : 0.46 + Kit.rand() * 0.1), Kit.rand() * 120);
                erupt(level, b, side, !front && j == 0, j == 1 ? PACKED : ICE_BLOCK, pre + (front ? 1 : 0), RISE, HOLD - 1, BREAK);
            }
        } else {
            b0 = base;
            Vec3 lean = V.flat(new Vec3(Kit.rand() - 0.5, 0, Kit.rand() - 0.5));
            main = new Crystal(tilted(lean, Kit.rand() * 7), H, W, spin);
            erupt(level, base, main, true, ICE_BLOCK, pre, RISE, HOLD, BREAK);
            double a0 = Kit.rand() * Math.PI * 2;
            for (int j = 0; j < 3; j++) {
                double a = a0 + j * Math.PI * 2 / 3 + (Kit.rand() - 0.5) * 0.6;
                Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                Vec3 b = V.add(base, out, W * (0.38 + Kit.rand() * 0.12));
                Crystal side = new Crystal(tilted(out, 24 + Kit.rand() * 14), H * (0.42 + Kit.rand() * 0.2), W * (0.42 + Kit.rand() * 0.12), Kit.rand() * 120);
                erupt(level, b, side, false, j == 1 ? PACKED : ICE_BLOCK, pre + (j == 2 ? 1 : 0), RISE, HOLD - 1, BREAK);
            }
        }
        // 솟을 때 결정을 타고 오르는 빛 · 끝의 반짝임 (빛나는 얼음)
        final Crystal mc = main;
        final Vec3 mb = b0;
        RuneKit.later(Math.max(2, pre) + 1, () -> {
            RuneKit.streak(level, V.up(mb, 0.3), mc.tip(mb), CORE, 0.3 + mc.w() * 0.06, 0.3, 0.9, 0);
            RuneKit.skyFlare(level, mc.tip(mb), 0.9 + mc.w() * 0.15, ICE, CORE, Opt.of("life", 0.35, "grow", 0.4, "fade", 0.6));
        });
    }

    // ------------------------------------------------------------ 빙결

    /** 이 시전의 얼음에 맞음: 냉기 +1 (얼어 있으면 쌓이지 않음), chill 번이면 얼림 */
    static void chillUp(ServerPlayer sp, ServerLevel level, LivingEntity e, Cast cast, int chill, int frost, int gap) {
        if (e.isRemoved() || !e.isAlive() || frozen(e)) return;
        int n = cast.chill.merge(e.getUUID(), 1, Integer::sum);
        if (n >= chill) {
            cast.chill.remove(e.getUUID());
            freeze(level, e, frost, cast);
            return;
        }
        // 쌓이는 냉기: 발밑 얼음 결정 (쌓인 수만큼) · 몸에 서리
        Vec3 feet = e.position();
        for (int i = 0; i < n; i++) {
            double a = Kit.rand() * Math.PI * 2;
            Vec3 p = new Vec3(feet.x + Math.cos(a) * (e.getBbWidth() * 0.6 + 0.2), feet.y, feet.z + Math.sin(a) * (e.getBbWidth() * 0.6 + 0.2));
            RuneKit.iceCrystal(sp, level, p, 0.45 + 0.2 * n, ICE, Opt.of("life", gap / 20.0 + 0.2, "full", 3.0));
        }
        snow(level, RuneLimited.body(e), 5 * n, e.getBbWidth() * 0.4, e.getBbHeight() * 0.3, 0.01);
    }

    /** 얼림: ticks 동안 얼음 덩어리에 갇힘 (기절) — 보스는 얼지 않음 (서리만) */
    static void freeze(ServerLevel level, LivingEntity e, int ticks, Cast cast) {
        Vec3 body = RuneLimited.body(e);
        if (mmorpg.fabric.boss.Bosses.isBossEntity(e)) {
            snow(level, body, 20, e.getBbWidth() * 0.5, e.getBbHeight() * 0.4, 0.04);
            shards(level, body, 16, e.getBbWidth() * 0.4, e.getBbHeight() * 0.3, 0.1);
            SkillFx.play(level, "block.glass.hit", body, 0.8, 0.8);
            return;
        }
        RuneKit.stun(e, ticks, ICE, false);
        e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
        Vec3 feet = e.position();
        List<Display> ice = encase(level, e, feet, ticks + 6);
        Frozen old = FROZEN.put(e.getUUID(), new Frozen(e, Tasks.now() + ticks, ice, feet));
        if (old != null) for (Display d : old.ice) FxDisplays.remove(d);
        cast.frozen++;
        shards(level, body, 30, e.getBbWidth() * 0.5, e.getBbHeight() * 0.4, 0.12);
        snow(level, body, 24, e.getBbWidth() * 0.6, e.getBbHeight() * 0.5, 0.04);
        RuneKit.novaRing(level, feet, e.getBbWidth() + 0.6, 1.6, ICE, CORE, Opt.of("life", 0.45));
        SkillFx.play(level, "entity.player.hurt_freeze", body, 0.6, 1.0);
        SkillFx.play(level, "block.glass.place", body, 0.5, 1.0);
        SkillFx.play(level, "block.amethyst_block.break", body, 0.7, 0.9);
    }

    /**
     * 얼음 덩어리 (feet 에 띄움 — 몸을 따라감): 투명한 얼음 블록 (몸 + 여유, 마구 돌림) + 위 · 옆으로 삐죽한 얼음 조각 넷 (속 굳은 얼음).
     * 0.3배에서 2틱 동안 커짐
     */
    static List<Display> encase(ServerLevel level, LivingEntity e, Vec3 feet, int life) {
        List<Display> out = new ArrayList<>();
        double bx = e.getBbWidth() + 0.5;
        double by = e.getBbHeight() + 0.45;
        double yaw = Kit.rand() * 90;
        Quaternionf q = new Quaternionf().rotationY((float) Math.toRadians(yaw));
        Display.BlockDisplay box = FxDisplays.block(level, feet, ICE_BLOCK, boxAt(q, bx, by, 0.3), life, GLOW);
        if (box != null) {
            box.setPosRotInterpolationDuration(2);
            out.add(box);
            RuneKit.later(2, () -> FxDisplays.move(box, boxAt(q, bx, by, 1), 2));
        }
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(yaw) + i * Math.PI / 2 + Math.PI / 4 + (Kit.rand() - 0.5) * 0.5;
            Vec3 out2 = new Vec3(Math.cos(a), 0, Math.sin(a));
            boolean top = i % 2 == 0;
            Vec3 base = top ? new Vec3(out2.x * bx * 0.28, by - 0.25, out2.z * bx * 0.28) : new Vec3(out2.x * bx * 0.45, by * (0.35 + Kit.rand() * 0.25), out2.z * bx * 0.45);
            Crystal cr = new Crystal(tilted(out2, top ? 20 + Kit.rand() * 20 : 55 + Kit.rand() * 20), 0.7 + Kit.rand() * 0.6, 0.38 + Kit.rand() * 0.14, Kit.rand() * 120);
            Display.BlockDisplay d = FxDisplays.block(level, V.add(feet, base), i == 1 ? ICE_BLOCK : PACKED, cr.at(0.6, 0.5, 1, 0), life, GLOW);
            if (d != null) {
                d.setPosRotInterpolationDuration(2);
                out.add(d);
                RuneKit.later(2, () -> FxDisplays.move(d, cr.at(1, 1, 1, 0), 2));
            }
        }
        return out;
    }

    /** 얼음 블록 변환 (가운데가 발 위 by/2 - 0.1 · 크기 비율 s) */
    static Transformation boxAt(Quaternionf q, double bx, double by, double s) {
        double sx = bx * s;
        double sy = by * s;
        Vector3f half = q.transform(new Vector3f((float) (sx / 2), (float) (sy / 2), (float) (sx / 2)));
        Vector3f t = new Vector3f(-half.x, (float) (by / 2 - 0.1) - half.y, -half.z);
        return new Transformation(t, new Quaternionf(q), new Vector3f((float) sx, (float) sy, (float) sx), new Quaternionf());
    }

    /** 풀림: 얼음 덩어리가 부서짐 (조각 · 소리). broken = 붕괴에 부서짐 (더 크게) */
    static void thaw(LivingEntity e, boolean broken) {
        Frozen f = FROZEN.remove(e.getUUID());
        if (f == null) return;
        shatterFx(e.level() instanceof ServerLevel l ? l : null, f, e.getBbWidth(), e.getBbHeight(), broken);
        if (broken) {
            // 기절도 함께 풀림 (붕괴에 날아감)
            e.removeEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
            e.removeEffect(net.minecraft.world.effect.MobEffects.WEAKNESS);
        }
    }

    private static void shatterFx(ServerLevel level, Frozen f, double bw, double bh, boolean big) {
        for (Display d : f.ice) FxDisplays.remove(d);
        if (level == null) return;
        Vec3 mid = V.up(f.at, bh * 0.5);
        shards(level, mid, big ? 50 : 34, bw * 0.5 + 0.2, bh * 0.4, big ? 0.25 : 0.15);
        snow(level, mid, 12, bw * 0.5, bh * 0.4, 0.05);
        SkillFx.fx(level, "rpg:splash", V.up(f.at, 0.3), Opt.of("size", bw + 1.0, "life", 0.5, "color", FROST, "alpha", 0.9));
        SkillFx.play(level, "block.glass.break", mid, big ? 0.8 : 1.0, big ? 1.0 : 0.8);
    }

    /** 매 틱: 풀릴 때가 된 얼음 · 쓰러진 적의 얼음 (부서짐), 덩어리가 몸을 따라감 */
    private static void tick(MinecraftServer server) {
        if (FROZEN.isEmpty()) return;
        long now = Tasks.now();
        for (Iterator<Map.Entry<UUID, Frozen>> it = FROZEN.entrySet().iterator(); it.hasNext(); ) {
            Frozen f = it.next().getValue();
            LivingEntity e = f.e;
            boolean gone = e.isRemoved() || !e.isAlive();
            if (gone || now >= f.until) {
                it.remove();
                shatterFx(e.level() instanceof ServerLevel l ? l : null, f, e.getBbWidth(), e.getBbHeight(), gone);
                continue;
            }
            Vec3 p = e.position();
            if (p.distanceToSqr(f.at) > 0.0025) {
                Vec3 d = V.sub(p, f.at);
                for (Display x : f.ice) if (x != null && !x.isRemoved()) x.setPos(x.getX() + d.x, x.getY() + d.y, x.getZ() + d.z);
                f.at = p;
            }
        }
    }

    // ------------------------------------------------------------ 얼음판 · 경계 결정

    /** 필드의 얼음판 조각 · 경계 결정 (블록 표시 — 붕괴 뒤 부서짐) */
    static final class Field {
        final ServerLevel level;
        final Vec3 c;
        final double R;
        final double refY;
        final int life;
        final List<Display.BlockDisplay> tiles = new ArrayList<>();
        final List<Transformation> tileShapes = new ArrayList<>();
        final List<Display> fence = new ArrayList<>();
        final List<Crystal> fenceShapes = new ArrayList<>();

        Field(ServerLevel level, Vec3 c, double R, double refY, int life) {
            this.level = level;
            this.c = c;
            this.R = R;
            this.refY = refY;
            this.life = life;
        }

        /** 얼음판: 가운데부터 바깥 고리로 (charge - 4 틱에 걸쳐) — 조각마다 작게 생겨 3틱 동안 커짐 (얼어붙는 느낌) */
        void spread(ServerPlayer sp, int charge) {
            int n = 0;
            for (double rr = 0; rr <= R - 0.3; rr += TILE_STEP) {
                int ring = rr < 0.1 ? 1 : (int) Math.round(Math.PI * 2 * rr / TILE_STEP);
                double a0 = Kit.rand() * Math.PI * 2;
                for (int i = 0; i < ring; i++) {
                    double a = a0 + Math.PI * 2 * i / ring + (Kit.rand() - 0.5) * 0.3;
                    double r2 = Math.min(R - 0.6, rr + (Kit.rand() - 0.5) * 0.4);
                    double x = c.x + Math.cos(a) * Math.max(0, r2);
                    double z = c.z + Math.sin(a) * Math.max(0, r2);
                    double gy = groundY(level, x, z, refY);
                    if (Math.abs(gy - refY) > 3) continue;
                    double s = TILE * (0.85 + Kit.rand() * 0.3) * (rr > R - TILE_STEP ? 0.8 : 1);
                    double yaw = Kit.rand() * 90;
                    double lift = 0.012 + (n % 7) * 0.004;
                    BlockState st = Kit.rand() < 0.28 ? PACKED : ICE_BLOCK;
                    Quaternionf q = new Quaternionf().rotationY((float) Math.toRadians(yaw));
                    Transformation full = tile(q, s, lift, 1);
                    Transformation small = tile(q, s, lift, 0.08);
                    int delay = (int) Math.round(rr / Math.max(0.1, R) * (charge - 4));
                    final Vec3 at = new Vec3(x, gy, z);
                    n++;
                    RuneKit.later(delay, () -> {
                        Display.BlockDisplay d = FxDisplays.block(level, at, st, small, life - delay, GLOW);
                        if (d == null) return;
                        tiles.add(d);
                        tileShapes.add(full);
                        RuneKit.later(2, () -> FxDisplays.move(d, full, 3));
                    });
                }
            }
        }

        /** 얼음판 조각 변환 (가운데가 자리 위 · 크기 비율 k) */
        static Transformation tile(Quaternionf q, double s, double lift, double k) {
            double w = s * k;
            Vector3f half = q.transform(new Vector3f((float) (w / 2), 0F, (float) (w / 2)));
            return new Transformation(new Vector3f(-half.x, (float) lift, -half.z), new Quaternionf(q), new Vector3f((float) w, 0.07F, (float) w), new Quaternionf());
        }

        /** 경계의 작은 결정 (바깥으로 기울어 빙 두름 — 시전자 가까이는 빼고) */
        void fence(ServerPlayer sp, int hold) {
            int n = Math.max(10, (int) Math.round(Math.PI * 2 * R / 1.7));
            double a0 = Kit.rand() * Math.PI * 2;
            for (int i = 0; i < n; i++) {
                double a = a0 + Math.PI * 2 * i / n;
                Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
                double x = c.x + out.x * (R - 0.2);
                double z = c.z + out.z * (R - 0.2);
                double gy = groundY(level, x, z, refY);
                Vec3 base = new Vec3(x, gy, z);
                if (Math.abs(gy - refY) > 3 || RuneKit.camDist(sp, V.up(base, 0.8)) < CLEAR || nearSide(sp, c, R, base, 0.55)) continue;
                Crystal cr = new Crystal(tilted(out, 18 + Kit.rand() * 16), 0.9 + Kit.rand() * 0.8 + (i % 3 == 0 ? 0.6 : 0), 0.55 + Kit.rand() * 0.25, Kit.rand() * 120);
                final int delay = i % 4;
                List<Display> ds = erupt(level, base, cr, false, i % 3 == 0 ? PACKED : ICE_BLOCK, 2 + delay, 3, hold - delay, BREAK);
                fence.addAll(ds);
                for (int j = 0; j < ds.size(); j++) fenceShapes.add(cr);
            }
        }

        /** 붕괴 뒤: 얼음판이 오그라들며 가라앉음 (4틱) */
        void shatter() {
            for (int i = 0; i < tiles.size(); i++) {
                Display.BlockDisplay d = tiles.get(i);
                Transformation t = tileShapes.get(i);
                Vector3fc tr = t.translation();
                Vector3fc sc = t.scale();
                FxDisplays.move(d, new Transformation(new Vector3f(tr.x() * 0.3F, tr.y() - 0.2F, tr.z() * 0.3F), t.leftRotation(),
                        new Vector3f(sc.x() * 0.3F, sc.y(), sc.z() * 0.3F), new Quaternionf()), BREAK);
                if (i % 3 == 0 && d != null && !d.isRemoved()) shards(level, new Vec3(d.getX(), d.getY() + 0.2, d.getZ()), 5, 0.6, 0.1, 0.1);
            }
        }
    }

    // ------------------------------------------------------------ 빙하 첨탑 (붕괴) · 6돌 왕관

    /**
     * 빙하 첨탑: 한가운데 거대한 결정 (키 R + 2.5 — 9칸까지 · 굵기 3.2, 속 푸른 얼음) · 둘레 큰 결정 여섯 (바깥으로 25 ~ 40° — 2틱 뒤) · 반지름 R × 0.55 의
     * 결정 여덟 (4틱 뒤 — 꽃처럼 피어남). hold 틱 뒤 부서짐. 시전자가 한가운데 가까우면 (카메라 6칸 안) 첨탑은 작게
     */
    static void spire(ServerPlayer sp, ServerLevel level, Vec3 c, double R, double refY, int hold) {
        double near = RuneKit.nearK(sp, V.up(c, 2), 9, 0.35);
        double H = Math.min(9.5, R + 3.5) * near;
        Crystal main = new Crystal(V.UP, H, 3.6 * near, Kit.rand() * 120);
        erupt(level, c, main, true, ICE_BLOCK, 0, 3, hold, BREAK);
        double a0 = Kit.rand() * Math.PI * 2;
        for (int i = 0; i < 6; i++) {
            double a = a0 + Math.PI * 2 * i / 6;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 b = V.add(c, out, (1.4 + Kit.rand() * 0.6) * near);
            b = new Vec3(b.x, groundY(level, b.x, b.z, refY), b.z);
            Crystal cr = new Crystal(tilted(out, 25 + Kit.rand() * 15), (4.2 + Kit.rand() * 2.0) * near, (1.7 + Kit.rand() * 0.5) * near, Kit.rand() * 120);
            erupt(level, b, cr, i % 2 == 0, i % 2 == 0 ? ICE_BLOCK : PACKED, 2, 3, hold - 2, BREAK);
        }
        RuneKit.later(5, () -> { // 다 솟았을 때 (붕괴 + 1틱)
            RuneKit.streak(level, V.up(c, 0.3), main.tip(c), CORE, 0.9, 0.45, 0.95, 0);
            RuneKit.skyFlare(level, main.tip(c), 2.4, ICE, CORE, Opt.of("life", 0.6, "grow", 0.5, "fade", 0.6));
        });
        for (int i = 0; i < 8; i++) {
            double a = a0 + Math.PI / 8 + Math.PI * 2 * i / 8;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 b = V.add(c, out, R * (0.5 + Kit.rand() * 0.12));
            b = new Vec3(b.x, groundY(level, b.x, b.z, refY), b.z);
            if (RuneKit.camDist(sp, V.up(b, 1.5)) < CLEAR) continue;
            Crystal cr = new Crystal(tilted(out, 30 + Kit.rand() * 15), (nearSide(sp, c, R, b, 0.3) ? 1.6 : 2.6) + Kit.rand() * 1.4, 1.1 + Kit.rand() * 0.4, Kit.rand() * 120);
            erupt(level, b, cr, false, i % 3 == 0 ? PACKED : ICE_BLOCK, 4, 3, hold - 4, BREAK);
        }
    }

    /**
     * 6돌 빙하의 왕관 (피해 없음): 경계 (반지름 R × 0.92) 에 거대한 얼음 기둥 열 (높낮이가 번갈아 — 왕관, 바깥으로 10 ~ 16°, 속 푸른 얼음) 이 차례로 솟아
     * 2초 서 있다 가라앉음 · 필드 너머 하늘에 오로라 장막 셋 (rpg:rfx_aurora — 시전자 쪽을 봄) · 눈이 내림 · 하늘빛이 오로라빛으로
     */
    static void crown(ServerPlayer sp, ServerLevel level, Vec3 c, double R, double refY, String life, double skyK) {
        if (!RuneKit.sameLife(sp, life)) return;
        RuneKit.ultSkyFor(sp, level, c, FN, (int) Math.round(80 / skyK), CROWN_FOG);
        int n = 10;
        double a0 = Math.atan2(sp.getZ() - c.z, sp.getX() - c.x) + Math.PI / n; // 시전자 쪽 정면은 기둥 사이 (앞이 트임)
        for (int i = 0; i < n; i++) {
            double a = a0 + Math.PI * 2 * i / n;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 b = V.add(c, out, R * 0.92);
            b = new Vec3(b.x, groundY(level, b.x, b.z, refY), b.z);
            if (RuneKit.camDist(sp, V.up(b, 2)) < CLEAR + 0.6) continue;
            boolean tall = i % 2 == 0;
            Crystal cr = new Crystal(tilted(out, 10 + Kit.rand() * 6), tall ? 6.2 + Kit.rand() * 1.2 : 4.2 + Kit.rand() * 0.8, tall ? 1.9 : 1.5, Kit.rand() * 120);
            erupt(level, b, cr, tall, ICE_BLOCK, 2 + i, 3, 40 - i, 12);
            final Vec3 bb = b;
            final int ii = i;
            RuneKit.later(4 + i, () -> {
                shards(level, V.up(bb, 0.5), 16, 0.6, 0.4, 0.15);
                if (ii % 2 == 0) SkillFx.play(level, "block.amethyst_block.chime", bb, 0.6 + ii * 0.08, 1.0);
            });
        }
        // 오로라: 왕관 위 하늘 (시전자에서 과녁 쪽 — 가운데에서 반지름 × 0.5 ~ 뒤, 높이 9 ~ 11.5칸 — 6돌 짙은 하늘빛 (안개 6 ~ 43칸) 에 묻히지 않게 가깝게) 에
        // 장막 셋 (초록 · 보라 · 초록 — 가운데가 조금 더 뒤)
        Vec3 f = V.flat(V.sub(c, sp.position()));
        if (V.len(V.sub(c, sp.position())) < 1.5) f = Kit.facingOf(sp);
        Vec3 side = V.rightOf(f);
        double[][][] cols = {{AURORA, AURORA_CORE}, {AURORA2, AURORA2_CORE}, {AURORA, AURORA_CORE}};
        for (int i = 0; i < 3; i++) {
            Vec3 at = V.up(V.add(V.add(c, f, R * 0.5 + (i == 1 ? 3 : 0)), side, (i - 1) * 8), 9 + i * 1.2);
            final int ii = i;
            RuneKit.later(i * 3, () -> RuneKit.rfx2(level, "aurora", at, Opt.of("dir", RuneKit.towardEye(sp, at), "size", 11.0 - (ii == 1 ? 0 : 1.5), "life", 3.4,
                    "fade", 0.35, "frames", 2.0, "fps", 2.5, "loop", true, "phase", (double) ii, "alpha", 0.9, "spin", 0.0), cols[ii][0], cols[ii][1], new int[] {0, 2},
                    0.95));
        }
        frostSigil(level, c, R * 1.1, ICE, CORE, Opt.of("life", 2.6, "spinRate", 10.0, "alpha", 0.75, "fade", 0.4));
        // 눈 (2틱마다 3초)
        Kit.ticks(t -> {
            if (t >= 60) return false;
            if (t % 2 == 0) {
                Vec3 p = anywhere(c, R + 2, refY);
                snow(level, V.up(p, 6 + Kit.rand() * 3), 6, 2.5, 0.5, 0.0);
            }
            return true;
        });
        SkillFx.play(level, "block.amethyst_block.resonate", c, 0.8, 1.2);
        SkillFx.play(level, "random.levelup", c, 1.6, 0.5);
        SkillFx.play(level, "block.beacon.activate", c, 1.3, 0.8);
    }
}
