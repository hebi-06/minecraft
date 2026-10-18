package mmorpg.fabric.rune;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import mmorpg.core.Obj;
import mmorpg.core.Runes;
import mmorpg.fabric.DamageNumbers;
import mmorpg.fabric.FxDisplays;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * 0.18.0 다섯째 한정 초월 룬 대지 가르기 (earthSplitter — 사용자 요청 "거대한 대지의 대검이 땅을 가르고 땅이 갈라지는 이펙트"): 다른 한정 룬처럼
 * 위력 P 의 적용 스탯 = 네 스탯의 합 × 1.0, 마법 피해, 피해는 몹 무적 시간을 피해 (RuneLimited.hit), 끝에 총 피해 글자. 수치는 RUNES.terra (rules_more.json),
 * 돌파는 rune_break.json (3 · 6돌 + 6돌 지맥 폭발 — finale, 피해 없음).
 * <ol>
 *   <li>대검 뽑힘 (charge 틱): 시전자 앞 start + length 칸 (균열 끝) 땅이 빛나며 갈라지고, 그 자리에서 대지의 대검 (3D 모델 — tools/terra_blade.py,
 *       아이템 표시 엔티티 · 넓은 면이 시전자 쪽) 이 칼끝을 아래로 한 채 손잡이부터 땅속에서 뽑혀 올라옴 (흙 · 바위가 떨어짐) → 조금 더 오르며 기욺 → 내리꽂힘</li>
 *   <li>대검 내려치기 (charge 틱): 칼끝이 땅속 BURY 칸 → 꽂힌 자리에서 시전자 쪽으로 CRACK_T 틱 동안 땅이 갈라짐 (용암빛 균열 그림 rpg:rfx_fissure ·
 *       양옆 땅이 판처럼 들림 — 그 자리 블록의 블록 표시 엔티티), 갈라짐이 지나는 적마다 위력 × crush (굵은 금빛 숫자 !) · 옆으로 띄움 (6돌 기절)</li>
 *   <li>갈라진 땅 여진 (gap 틱마다 hits 번): 대검의 맥이 빛나고 균열을 따라 바위 가시 · 흙 · 불씨가 솟는 물결 (WAVE_T 틱) — 균열 안 모든 적 위력 × mult</li>
 *   <li>끝: 대검이 다시 땅속으로 가라앉고 들린 땅이 내려앉음. 6돌 지맥 폭발 (마지막 여진 뒤): 균열 양옆으로 바위 기둥 (점적석 · 꼭대기 마그마) 이 줄지어
 *       솟았다 무너지고 용암빛이 터짐 (피해 없음 — 총 피해 글자는 그 뒤)</li>
 * </ol>
 * 균열 = 선 S (시전자 앞 start 칸) → E (S + length 칸) 에서 옆으로 width 칸. 화면 색 연출은 3돌부터 (옅은 모래빛 흙먼지 하늘 SKY_FOG · 메테오의 주황 번쩍임 —
 * RuneKit.ULT_ALIAS).
 * 쓰러지면 남은 것은 멈춤 (표시 엔티티는 정해진 틱 뒤 FxDisplays 가 지움).
 */
final class RuneTerra {
    private RuneTerra() {}

    /** 짙은 틈 · 용암빛 (빛무리) · 심지 · 흙먼지 · 바위 */
    static final double[] RIFT = {0.16, 0.09, 0.05};
    static final double[] AMBER = {1.0, 0.5, 0.12};
    static final double[] MOLTEN = {1.0, 0.86, 0.5};
    static final double[] DUST = {0.6, 0.5, 0.38};
    static final double[] ROCK = {0.5, 0.42, 0.34};
    static final double[] WHITE = {1, 1, 1};

    /** 대검: 표시 크기 (모델 1 = 크기 / 16 칸) · 모델 가운데 (8, 8, 8) 에서 칼끝 (y 32) · 머리 끝 (y 0.4) 까지 (모델 단위) · 꽂히는 깊이 (칸) */
    static final double SCALE = 6.5;
    static final double TIP_U = 24.0;
    static final double POMMEL_U = 7.6;
    static final double BURY = 2.5;
    /** 땅이 갈라지는 틱 (꽂힌 자리 → 시전자 쪽 끝) · 여진 물결이 균열을 지나는 틱 */
    static final int CRACK_T = 6;
    static final int WAVE_T = 4;
    /** 들리는 땅 판: 틈의 반폭 (선에서 판 안쪽 끝까지) · 두께 */
    static final double GAP = 0.36;
    static final double PLATE_T = 0.55;
    static final Identifier BLADE_MODEL = Identifier.fromNamespaceAndPath("rpg", "fx_terra_blade");
    /** 하늘빛: 옅은 모래빛 흙먼지 (rpg:ult_sword — 12 ~ 84칸: 균열 끝의 대검이 6돌 짙은 하늘빛에도 보이게. 번쩍임은 메테오의 주황 — ULT_ALIAS) */
    static final String SKY_FOG = "rpg:ult_sword";
    /** 시전자 눈 앞 이 칸 안에는 불씨 · 용암 방울을 띄우지 않음 (1인칭 화면에 크게 보이지 않게) */
    static final double NEAR_CLEAR = 4.5;
    /** 대검 · 판의 밝기 (밤에도 맥 빛으로 보이게 — 블록 빛 11) */
    static final Brightness LIGHT = new Brightness(11, 15);

    static void register() {
        RuneCast.register("earthSplitter", RuneTerra::cast);
    }

    // ------------------------------------------------------------ 도움

    /** (x, z) 의 땅 높이 (위 면): hint 위 3칸부터 아래 6칸까지 처음 막힌 블록 — 없으면 hint */
    static double groundY(ServerLevel level, double x, double z, double hint) {
        int top = (int) Math.floor(hint) + 3;
        for (int y = top; y >= top - 9; y--) {
            BlockPos p = BlockPos.containing(x, y, z);
            BlockState st = level.getBlockState(p);
            if (!st.isAir() && st.getFluidState().isEmpty() && !st.getCollisionShape(level, p).isEmpty()) return y + st.getCollisionShape(level, p).max(net.minecraft.core.Direction.Axis.Y);
        }
        return hint;
    }

    /** 판에 쓸 블록 (그 자리 땅 — 풀 · 흙 · 돌 …, 꽉 찬 블록이 아니면 흙) · 없으면 null (공중) */
    static BlockState plateBlock(ServerLevel level, double x, double gy, double z) {
        BlockPos p = BlockPos.containing(x, gy - 0.5, z);
        BlockState st = level.getBlockState(p);
        if (st.isAir() || !st.getFluidState().isEmpty()) return null;
        return st.isCollisionShapeFullBlock(level, p) ? st : Blocks.DIRT.defaultBlockState();
    }

    /** v 를 axis (단위) 둘레로 deg 도 (오른손) */
    static Vec3 rot(Vec3 v, Vec3 axis, double deg) {
        double a = Math.toRadians(deg);
        double c = Math.cos(a);
        double s = Math.sin(a);
        Vec3 k = axis;
        Vec3 cross = V.cross(k, v);
        double dot = V.dot(k, v);
        return new Vec3(v.x * c + cross.x * s + k.x * dot * (1 - c), v.y * c + cross.y * s + k.y * dot * (1 - c), v.z * c + cross.z * s + k.z * dot * (1 - c));
    }

    /** 대검 회전: 칼끝 아래 · 넓은 면이 f 쪽 (시전자를 봄) · lean 도만큼 칼끝을 앞 (f — 시전자에게서 먼 쪽) 으로 */
    static Quaternionf bladeRot(Vec3 f, Vec3 side, double lean) {
        Vec3 ey = rot(new Vec3(0, -1, 0), side, lean);
        Vec3 ez = rot(f, side, lean);
        Vec3 ex = V.cross(ey, ez);
        return FxDisplays.basis(V.unit(ex), V.unit(ey), V.unit(ez));
    }

    /** 균열 안의 적: E 에서 시전자 쪽으로 along (-1 ~ L + 0.5) · 옆으로 width (+ 몸 폭) 안 · 위아래 3.5칸 */
    static List<LivingEntity> inRift(ServerPlayer sp, Vec3 E, Vec3 f, double L, double W, double refY) {
        Vec3 mid = V.add(E, f, -L / 2);
        double r = L / 2 + W + 2;
        Vec3 side = V.rightOf(f);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : sp.level().getEntitiesOfClass(LivingEntity.class, new AABB(mid, mid).inflate(r, 5, r), x -> Kit.canHit(sp, x))) {
            Vec3 d = V.sub(e.position(), E);
            double along = -V.dot(d, f);
            double lat = Math.abs(V.dot(d, side));
            if (along >= -1.2 && along <= L + 0.5 && lat <= W + 0.5 + Kit.bodyPad(e) && Math.abs(e.getY() - refY) <= 3.5) out.add(e);
        }
        return out;
    }

    /** E 에서 시전자 쪽으로 along 칸의 균열 위 땅 (옆으로 lat) */
    static Vec3 riftAt(ServerLevel level, Vec3 E, Vec3 f, Vec3 side, double along, double lat, double refY) {
        Vec3 p = V.add(V.add(E, f, -along), side, lat);
        return new Vec3(p.x, groundY(level, p.x, p.z, refY), p.z);
    }

    // ------------------------------------------------------------ 시전

    static boolean cast(ServerPlayer sp, Runes.Rune r, double P, Obj k) {
        ServerLevel level = sp.level();
        String life = RuneKit.lifeToken(sp);
        boolean fin = RuneLimited.finale(sp, r);
        Vec3 c = sp.position();
        Vec3 f = Kit.facingOf(sp);
        Vec3 side = V.rightOf(f);
        double L = k.num("length");
        double W = k.num("width");
        double start = k.numOr("start", 1.5);
        int charge = (int) k.num("charge");
        int gap = (int) k.num("gap");
        int hits = (int) k.num("hits");
        double lift = k.numOr("lift", 0.9);
        int stun = (int) k.numOr("stun", 0);
        double refY = c.y;
        Vec3 E0 = V.add(c, f, start + L);
        Vec3 E = new Vec3(E0.x, groundY(level, E0.x, E0.z, refY), E0.z);
        int lastWave = charge + CRACK_T + hits * gap + WAVE_T;
        int sinkAt = lastWave + 8;
        int endAt = sinkAt + 18;
        RuneLimited.Tally tally = new RuneLimited.Tally();
        double skyK = skyTicksOf(sp);
        RuneKit.ultSkyFor(sp, level, c, "earthSplitter", (int) Math.round((endAt + 20) / skyK), SKY_FOG);

        // ---- 대검 (아이템 표시 — 꽂힌 자리에 두고 위아래로만 옮김)
        double tipOff = TIP_U * SCALE / 16;
        double pommelOff = POMMEL_U * SCALE / 16;
        Vec3 pivot = V.up(E, tipOff - BURY);
        double hidden = -(tipOff - BURY) - pommelOff - 0.6; // 머리 끝까지 땅속
        double hover = BURY + 1.3; // 칼끝이 땅 위 1.3칸
        Quaternionf upright = bladeRot(f, side, 0);
        Quaternionf leaned = bladeRot(f, side, 9);
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL, BLADE_MODEL);
        Display.ItemDisplay blade = FxDisplays.item(level, pivot, stack, FxDisplays.of(new Vec3(0, hidden, 0), upright, SCALE), endAt + 4, LIGHT);
        int riseEnd = charge - 8;
        RuneKit.later(2, () -> FxDisplays.move(blade, FxDisplays.of(new Vec3(0, hover, 0), upright, SCALE), riseEnd - 2));
        RuneKit.later(riseEnd, () -> FxDisplays.move(blade, FxDisplays.of(new Vec3(0, hover + 1.1, 0), leaned, SCALE), 5));
        RuneKit.later(charge - 3, () -> FxDisplays.move(blade, FxDisplays.of(new Vec3(0, 0, 0), upright, SCALE), 3));
        RuneKit.later(sinkAt, () -> FxDisplays.move(blade, FxDisplays.of(new Vec3(0, hidden, 0), upright, SCALE), 16));

        // ---- 뽑힘: 꽂힐 자리의 빛나는 땅 · 흙 · 바위 (매 2틱) · 소리
        SkillFx.rune(level, V.up(E, 0.05), Opt.of("size", 2.6, "life", (charge + 2) / 20.0, "color", AMBER, "alpha", 0.85, "rate", 90.0, "progress", 1.0));
        RuneKit.glowCrack(level, E, 2.4, AMBER, MOLTEN, Opt.of("life", (charge + 6) / 20.0, "fade", 0.3, "grow", 0.4));
        SkillFx.play(level, "entity.warden.dig", E, 0.7, 1.6);
        SkillFx.play(level, "block.deepslate.break", E, 0.5, 1.2);
        SkillFx.play(level, "entity.warden.emerge", E, 0.8, 0.6);
        Kit.ticks(t -> {
            if (!RuneKit.sameLife(sp, life) || t >= charge - 3) return false;
            if (t < riseEnd && t % 2 == 0) {
                RuneKit.rockBurst(level, V.up(E, 0.3), 3, 4.5, 0.22);
                SkillFx.fx(level, "rpg:dust", V.up(E, 0.4), Opt.of("size", 1.4, "life", 0.6, "color", DUST, "alpha", 0.4));
                if (t % 6 == 0) SkillFx.play(level, "block.rooted_dirt.break", E, 0.6 + t * 0.01, 1.0);
            }
            if (t >= riseEnd && t % 2 == 0) SkillFx.sparks(level, V.up(E, hover + tipOff - 2.2), AMBER, 3, 2);
            return true;
        });

        // ---- 들리는 땅 판 (꽂히기 직전에 땅속에 숨겨 띄움 → 갈라짐이 지날 때 들림)
        List<Plate> plates = new ArrayList<>();
        RuneKit.later(charge - 4, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            for (double along = -1.5; along < L - 0.2; along += 1.0) {
                for (int sgn : new int[] {-1, 1}) {
                    Plate p = Plate.make(level, E, f, side, along + (Kit.rand() - 0.5) * 0.15, sgn, refY, endAt);
                    if (p != null) plates.add(p);
                }
            }
        });

        // ---- 내려치기: 꽂힘 · 충격 · 갈라짐 (E → 시전자 쪽, CRACK_T 틱)
        RuneKit.later(charge, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            RuneKit.ultFlashFor(sp, level, E, "earthSplitter", "flash", Opt.empty());
            RuneKit.shakeAround(level, E, L + 8, 0.6, 0.6);
            SkillFx.play(level, "item.mace.smash_ground_heavy", E, 0.55, 1.6);
            SkillFx.play(level, "entity.generic.explode", E, 0.6, 1.2);
            SkillFx.play(level, "block.anvil.land", E, 0.5, 0.7);
            SkillFx.play(level, "entity.warden.sonic_boom", c, 0.6, 0.35);
            Vec3 base = V.up(E, 0.2);
            SkillFx.shock(level, E, AMBER, 3.2, 0.5);
            SkillFx.shock(level, E, DUST, 5.0, 0.7);
            RuneKit.novaRing(level, E, 1.2, 4.5, AMBER, MOLTEN, Opt.of("life", 0.45));
            RuneKit.glowCrack(level, E, 4.2, AMBER, MOLTEN, Opt.of("life", (endAt - charge) / 20.0, "fade", 0.25, "v", 1.0));
            SkillFx.fx(level, "rpg:ground_crack", V.up(E, 0.04), Opt.of("dir", V.ZERO, "spin", Kit.rand() * 360, "size", 4.5, "life", (endAt - charge) / 20.0,
                    "color", RIFT, "alpha", 0.9));
            RuneKit.rockBurst(level, V.up(E, 0.6), 16, 9, 0.34);
            SkillFx.fx(level, "rpg:dust", V.up(E, 1.2), Opt.of("size", 4.0, "life", 1.1, "color", DUST, "alpha", 0.5));
            SkillFx.burst(level, V.up(base, 0.6), AMBER, 3.0, 0.35);
            SkillFx.sparks(level, V.up(base, 0.8), MOLTEN, 20, 7);
            // 대검이 꽂힐 때 칼날을 따라 내려가는 빛 (내리꽂는 속도선)
            for (double o : new double[] {-1.0, 0.0, 1.0}) {
                Vec3 top = V.add(V.up(E, tipOff + 2.0), side, o * 1.1);
                RuneKit.streak(level, top, V.add(V.up(E, 0.3), side, o * 0.5), AMBER, 0.35, 0.25, 0.8, 0);
            }
        });
        Set<UUID> slammed = new HashSet<>();
        for (int j = 0; j < CRACK_T; j++) {
            final int jj = j;
            RuneKit.later(charge + j, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                double a0 = jj == 0 ? -3 : L * jj / CRACK_T;
                double a1 = jj == CRACK_T - 1 ? L + 1 : L * (jj + 1) / CRACK_T;
                // 균열 그림 (조각 — 갈라짐이 그 조각에 들어설 때)
                for (Seg s : Seg.of(L)) if (s.a0() >= a0 - 1e-9 && s.a0() < a1) s.draw(level, E, f, side, refY, (endAt + 10 - charge - jj) / 20.0);
                // 판이 들림
                for (Plate p : plates) if (p.along >= a0 && p.along < a1) p.raise();
                // 갈라지는 앞머리의 흙 · 바위 · 불씨
                int nd = 0;
                for (double a = Math.max(0, a0); a < Math.min(a1, L); a += 1.2) {
                    Vec3 g = riftAt(level, E, f, side, a, (Kit.rand() - 0.5) * 0.6, refY);
                    SkillFx.fx(level, "rpg:boulder", V.up(g, 0.3), Opt.of("dir", V.unit(new Vec3(side.x * (Kit.rand() - 0.5), 1.3, side.z * (Kit.rand() - 0.5))),
                            "speed", 5 + Kit.rand() * 3, "size", 0.2 + Kit.rand() * 0.12, "life", 0.6, "spin", Kit.rand() * 360, "color", ROCK, "alpha", 1.0));
                    if (nd++ % 2 == 0 && a < L - NEAR_CLEAR) SkillFx.fx(level, "rpg:dust", V.up(g, 0.5), Opt.of("size", 1.0, "life", 0.6, "color", DUST, "alpha", 0.35));
                    SkillFx.fx(level, "rpg:arrow_spark", V.up(g, 0.2), Opt.of("dir", V.UP, "speed", 3.5, "size", 0.22, "grow", 0.8, "life", 0.4, "color", AMBER, "alpha", 1.0));
                }
                if (jj % 2 == 0) SkillFx.play(level, "block.basalt.break", riftAt(level, E, f, side, (a0 + a1) / 2, 0, refY), 0.5, 1.4);
                // 갈라짐이 지나는 적: 대검 내려치기 (위력 × crush) · 옆으로 띄움 (6돌 기절)
                boolean last = jj == CRACK_T - 1;
                for (LivingEntity e : inRift(sp, E, f, L, W, refY)) {
                    double along = -V.dot(V.sub(e.position(), E), f);
                    if (!last && (along < a0 || along >= a1)) continue;
                    if (!slammed.add(e.getUUID())) continue;
                    double sgn = Math.signum(V.dot(V.sub(e.position(), E), side));
                    Vec3 out = V.scale(side, sgn == 0 ? 1 : sgn);
                    RuneLimited.hit(sp, life, e, P, k.num("crush"), DamageNumbers.Style.CLEAVE, tally, x -> {
                        RuneKit.knock(x, out, 0.35, lift);
                        if (stun > 0) RuneKit.stun(x, stun, AMBER, slammed.size() <= 10);
                    });
                }
            });
        }

        // ---- 갈라진 땅 여진 (gap 틱마다 hits 번 — 물결이 E 에서 시전자 쪽으로 WAVE_T 틱)
        for (int i = 1; i <= hits; i++) {
            final int at = charge + CRACK_T + i * gap;
            final int idx = i;
            Set<UUID> shook = new HashSet<>();
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                SkillFx.play(level, "block.basalt.break", V.add(E, f, -L / 2), 0.45 + idx * 0.03, 1.5);
                SkillFx.play(level, "entity.generic.explode", V.add(E, f, -L / 2), 0.8, 0.35);
                SkillFx.play(level, "block.deepslate.break", E, 0.55, 1.0);
                // 대검의 맥이 빛남
                SkillFx.burst(level, V.up(E, tipOff - BURY - 1.0), AMBER, 2.0, 0.3);
                SkillFx.sparks(level, V.up(E, tipOff - BURY - 1.0), MOLTEN, 8, 4);
                RuneKit.shakeAround(level, V.add(E, f, -L / 2), L, 0.22, 0.25);
                for (Plate p : plates) p.bump(idx);
            });
            for (int j = 0; j < WAVE_T; j++) {
                final int jj = j;
                RuneKit.later(at + j, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    double a0 = L * jj / WAVE_T - (jj == 0 ? 1.5 : 0);
                    double a1 = jj == WAVE_T - 1 ? L + 1 : L * (jj + 1) / WAVE_T;
                    int n = 0;
                    for (double a = Math.max(0.6, a0 + Kit.rand() * 0.6); a < Math.min(L, a1); a += 1.4) {
                        double lat = ((n++ % 2 == 0) ? 1 : -1) * (0.3 + Kit.rand() * 0.5);
                        Vec3 g = riftAt(level, E, f, side, a, lat, refY);
                        RuneKit.rockSpike(sp, level, g, 0.75 + Kit.rand() * 0.5, AMBER, Opt.of("life", 0.5, "fade", 0.45, "full", 5.0));
                        if (a < L - NEAR_CLEAR) SkillFx.fx(level, "rpg:dust", V.up(g, 0.4), Opt.of("size", 0.8, "life", 0.5, "color", DUST, "alpha", 0.35));
                        if (Kit.rand() < 0.5) {
                            SkillFx.fx(level, "rpg:arrow_spark", V.up(g, 0.2), Opt.of("dir", V.UP, "speed", 4.0, "size", 0.2, "grow", 0.6, "life", 0.45, "color", AMBER,
                                    "alpha", 1.0));
                        }
                    }
                    boolean last = jj == WAVE_T - 1;
                    for (LivingEntity e : inRift(sp, E, f, L, W, refY)) {
                        double along = -V.dot(V.sub(e.position(), E), f);
                        if (!last && (along < a0 || along >= a1)) continue;
                        if (!shook.add(e.getUUID())) continue;
                        double sgn = Math.signum(V.dot(V.sub(e.position(), E), side));
                        Vec3 out = V.scale(side, sgn == 0 ? 1 : sgn);
                        RuneLimited.hit(sp, life, e, P, k.num("mult"), DamageNumbers.Style.QUAKE, tally, x -> RuneKit.knock(x, out, 0.08, 0.28));
                    }
                });
            }
        }

        // ---- 균열에서 오르는 불씨 · 용암 방울 (내려친 뒤 끝날 때까지 3틱마다)
        RuneKit.later(charge, () -> Kit.ticks(t -> {
            if (!RuneKit.sameLife(sp, life) || t >= sinkAt - charge) return false;
            if (t % 3 != 0) return true;
            for (int i = 0; i < 2; i++) {
                Vec3 g = riftAt(level, E, f, side, Kit.rand() * (L - NEAR_CLEAR), (Kit.rand() - 0.5) * 0.5, refY);
                SkillFx.fx(level, "rpg:arrow_spark", V.up(g, 0.1), Opt.of("dir", V.UP, "speed", 1.6 + Kit.rand(), "size", 0.14, "grow", 0.4, "life", 0.7, "color", AMBER,
                        "alpha", 0.95));
            }
            if (t % 6 == 0) {
                Vec3 g = riftAt(level, E, f, side, Kit.rand() * (L - NEAR_CLEAR), 0, refY);
                level.sendParticles(ParticleTypes.LAVA, g.x, g.y + 0.1, g.z, 1, 0.1, 0, 0.1, 0);
            }
            return true;
        }));

        // ---- 끝: 대검이 가라앉고 판이 내려앉음 (6돌 — 지맥 폭발)
        RuneKit.later(sinkAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            SkillFx.play(level, "entity.warden.dig", E, 0.6, 1.4);
            SkillFx.play(level, "block.grindstone.use", E, 0.4, 0.8);
            for (Plate p : plates) p.settle();
        });
        for (int t = 0; t < 14; t += 2) {
            final int tt = t;
            RuneKit.later(sinkAt + t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                RuneKit.rockBurst(level, V.up(E, 0.3), 2, 3.5, 0.2);
                SkillFx.fx(level, "rpg:dust", V.up(E, 0.6 + tt * 0.05), Opt.of("size", 2.0, "life", 0.8, "color", DUST, "alpha", 0.55));
            });
        }
        if (fin) RuneKit.later(lastWave + 2, () -> veinBurst(sp, level, E, f, side, L, refY, life));
        int totalAt = lastWave + (fin ? 34 : 10);
        RuneLimited.total(sp, "earthSplitter", V.up(V.add(E, f, -L * 0.45), 3.2), tally, totalAt);
        return true;
    }

    /** 이 사람이 지금 쓴 대지 가르기의 하늘빛 시간 배율 (3돌 미만이면 1 — 긴 룬이라 6돌 배율 1.5 를 나눠 씀) */
    static double skyTicksOf(ServerPlayer sp) {
        mmorpg.core.RuneBreak.Fx fx = Breakthrough.castFx(sp, "earthSplitter");
        return fx != null && fx.skyTicks() > 0 ? fx.skyTicks() : 1;
    }

    /**
     * 6돌 지맥 폭발: 균열을 따라 (시전자 앞 NEAR_CLEAR + 2칸 밖 — 1인칭 화면을 막지 않게) 시전자 쪽에서 대검 쪽으로 바위 기둥 (점적석 · 꼭대기 마그마) 이
     * 균열 양옆으로 번갈아 줄지어 솟구쳤다 (3틱) 무너지고 (14틱 뒤 가라앉음), 기둥 밑에서 용암빛 · 바위가 터짐 (피해 없음)
     */
    static void veinBurst(ServerPlayer sp, ServerLevel level, Vec3 E, Vec3 f, Vec3 side, double L, double refY, String life) {
        if (!RuneKit.sameLife(sp, life)) return;
        List<Double> spots = new ArrayList<>();
        for (double along = L - NEAR_CLEAR - 2.0; along >= -1.0; along -= 1.5) spots.add(along);
        int n = spots.size();
        for (int i = 0; i < n; i++) {
            final double along = spots.get(i);
            final int delay = i * 12 / Math.max(1, n);
            final double lat = (i % 2 == 0 ? 1 : -1) * (0.9 + Kit.rand() * 0.7);
            RuneKit.later(delay, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 g = riftAt(level, E, f, side, along, lat, refY);
                double far = (L - along) / L; // 멀수록 높게
                double h = 2.2 + far * 2.6 + Kit.rand() * 1.2;
                double wdt = 0.75 + Kit.rand() * 0.35;
                Vec3 tilt = V.unit(V.add(new Vec3(0, 1, 0), side, Math.signum(lat) * (0.1 + Kit.rand() * 0.14)));
                Vec3 ez = V.unit(V.cross(f, tilt));
                Vec3 ex = V.unit(V.cross(tilt, ez));
                Quaternionf q = FxDisplays.basis(ex, tilt, ez);
                // 블록 모서리 → 가운데 아래 (x · z 반) 를 g 에
                Vec3 off = V.add(V.scale(ex, -wdt / 2), V.scale(ez, -wdt / 2));
                double cap = Math.min(0.6, h * 0.18);
                Display.BlockDisplay pillar = FxDisplays.block(level, g, Blocks.DRIPSTONE_BLOCK.defaultBlockState(),
                        FxDisplays.of(V.add(off, V.scale(tilt, -h - 0.4)), q, wdt, h, wdt), 42, LIGHT);
                Display.BlockDisplay top = FxDisplays.block(level, g, Blocks.MAGMA_BLOCK.defaultBlockState(),
                        FxDisplays.of(V.add(V.scale(off, 0.86), V.scale(tilt, -cap - 0.4)), q, wdt * 0.86, cap, wdt * 0.86), 42, new Brightness(15, 15));
                RuneKit.later(2, () -> {
                    FxDisplays.move(pillar, FxDisplays.of(V.add(off, V.scale(tilt, -0.5)), q, wdt, h, wdt), 3);
                    FxDisplays.move(top, FxDisplays.of(V.add(V.scale(off, 0.86), V.scale(tilt, h - 0.5)), q, wdt * 0.86, cap, wdt * 0.86), 3);
                });
                RuneKit.later(5, () -> {
                    SkillFx.burst(level, V.up(g, 0.5), AMBER, 1.6, 0.3);
                    RuneKit.rockBurst(level, V.up(g, 0.4), 4, 6, 0.22);
                    SkillFx.fx(level, "rpg:arrow_spark", V.add(V.up(g, h - 0.2), side, 0), Opt.of("dir", V.UP, "speed", 5.0, "size", 0.45, "grow", 1.0, "life", 0.4,
                            "color", MOLTEN, "alpha", 1.0));
                    level.sendParticles(ParticleTypes.LAVA, g.x, g.y + h - 0.3, g.z, 3, 0.25, 0.1, 0.25, 0);
                    SkillFx.play(level, "block.pointed_dripstone.place", g, 0.6, 1.4);
                    if (Kit.rand() < 0.4) SkillFx.play(level, "entity.generic.explode", g, 0.9, 0.4);
                });
                RuneKit.later(19, () -> {
                    FxDisplays.move(pillar, FxDisplays.of(V.add(off, V.scale(tilt, -h - 0.4)), q, wdt * 0.85, h, wdt * 0.85), 12);
                    FxDisplays.move(top, FxDisplays.of(V.add(V.scale(off, 0.86), V.scale(tilt, -cap - 0.4)), q, wdt * 0.8, cap, wdt * 0.8), 12);
                    RuneKit.rockBurst(level, V.up(g, h * 0.6), 3, 3, 0.25);
                    SkillFx.fx(level, "rpg:dust", V.up(g, 1.0), Opt.of("size", 1.4, "life", 0.8, "color", DUST, "alpha", 0.4));
                });
            });
        }
        RuneKit.shakeAround(level, V.add(E, f, -L / 2), L + 6, 0.45, 0.8);
        SkillFx.play(level, "entity.generic.explode", V.add(E, f, -L / 2), 0.5, 1.4);
        SkillFx.play(level, "item.mace.smash_ground_heavy", V.add(E, f, -L / 2), 0.7, 1.2);
    }

    // ------------------------------------------------------------ 균열 그림 조각

    /** 균열 그림 한 조각 (E 에서 시전자 쪽으로 a0 ~ end — 이웃 조각과 35% 겹침) */
    record Seg(double a0, double end) {
        static List<Seg> of(double L) {
            int m = (int) Math.ceil(L / 6.5);
            List<Seg> out = new ArrayList<>();
            double step = L / m;
            for (int i = 0; i < m; i++) out.add(new Seg(Math.max(-0.6, i * step - step * 0.18), Math.min(L + 0.3, (i + 1) * step + step * 0.18)));
            return out;
        }

        void draw(ServerLevel level, Vec3 E, Vec3 f, Vec3 side, double refY, double life) {
            double mid = (a0 + end) / 2;
            Vec3 g = riftAt(level, E, f, side, mid, 0, refY);
            double size = (end - a0) / 2 * 1.04;
            double spin = Math.toDegrees(Math.atan2(f.z, -f.x)) + (Kit.rand() < 0.5 ? 180 : 0);
            Opt base = Opt.of("dir", V.UP, "size", size, "life", life, "fade", 0.2, "spin", spin);
            RuneKit.rfx(level, "fissure", V.up(g, 0.035), base.with("cell", 0.0, "color", RIFT, "alpha", 0.96));
            RuneKit.rfx(level, "fissure", V.up(g, 0.055), base.with("cell", 1.0, "color", AMBER, "alpha", 0.85));
            RuneKit.rfx(level, "fissure", V.up(g, 0.075), base.with("cell", 2.0, "color", MOLTEN, "alpha", 1.0));
        }
    }

    // ------------------------------------------------------------ 들리는 땅 판

    /** 균열 한쪽의 땅 판 하나 (블록 표시 — 바깥 아래 모서리를 축으로 안쪽이 들림) */
    static final class Plate {
        final Display.BlockDisplay d;
        final double along;
        final Vec3 o;
        final Vec3 f;
        final double l;
        final double w;
        final double phi;

        Plate(Display.BlockDisplay d, double along, Vec3 o, Vec3 f, double l, double w, double phi) {
            this.d = d;
            this.along = along;
            this.o = o;
            this.f = f;
            this.l = l;
            this.w = w;
            this.phi = phi;
        }

        /** E 에서 시전자 쪽으로 along 칸, sgn 쪽 (오른쪽 +1 · 왼쪽 -1) 판 — 땅속에 숨긴 채 띄움 (공중이면 null) */
        static Plate make(ServerLevel level, Vec3 E, Vec3 f, Vec3 side, double along, int sgn, double refY, int life) {
            double l = 1.0 + Kit.rand() * 0.22;
            double w = 1.15 + Kit.rand() * 0.35;
            Vec3 o = V.scale(side, sgn);
            Vec3 line = V.add(E, f, -along);
            Vec3 hingeXZ = V.add(line, o, GAP + w);
            double gy = groundY(level, hingeXZ.x, hingeXZ.z, refY);
            BlockState st = plateBlock(level, hingeXZ.x, gy, hingeXZ.z);
            if (st == null) return null;
            double phi = 24 + Kit.rand() * 14;
            Vec3 hinge = new Vec3(hingeXZ.x, gy, hingeXZ.z);
            Plate p0 = new Plate(null, along, o, f, l, w, phi);
            Display.BlockDisplay d = FxDisplays.block(level, hinge, st, p0.at(0, -(PLATE_T + 0.45)), life, null);
            return d == null ? null : new Plate(d, along, o, f, l, w, phi);
        }

        /** 기운 각도 a (도) · 축 높이 dy 의 변환 (축 = 바깥 아래 모서리 가운데 — 엔티티 자리) */
        Transformation at(double a, double dy) {
            double r = Math.toRadians(a);
            Vec3 up = new Vec3(0, 1, 0);
            Vec3 ey = V.add(V.scale(up, Math.cos(r)), o, Math.sin(r));
            Vec3 ez = V.add(V.scale(o, Math.cos(r)), up, -Math.sin(r));
            Vec3 ex = V.unit(V.cross(ey, ez));
            Quaternionf q = FxDisplays.basis(ex, V.unit(ey), V.unit(ez));
            Vec3 t = V.add(V.add(new Vec3(0, dy, 0), ex, -0.5 * l), ez, -w);
            return FxDisplays.of(t, q, l, PLATE_T, w);
        }

        void raise() {
            FxDisplays.move(d, at(phi, -PLATE_T * 0.55), 3);
        }

        /** 여진: 조금 더 들렸다 (2틱) 돌아옴 (3틱) */
        void bump(int i) {
            FxDisplays.move(d, at(phi + 7, -PLATE_T * 0.45), 2);
            RuneKit.later(2, () -> FxDisplays.move(d, at(phi, -PLATE_T * 0.55), 3));
        }

        void settle() {
            FxDisplays.move(d, at(5, -PLATE_T - 0.2), 12);
        }
    }
}
