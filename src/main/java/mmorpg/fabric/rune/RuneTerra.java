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
 * 0.18.0 다섯째 한정 초월 룬 대지 가르기 (earthSplitter — 사용자 요청 "거대한 대지의 대검이 땅을 가르고 땅이 갈라지는 이펙트" → 고침 "거대한 검을 전방으로
 * 내려찍는 모습 · 전방으로 직선 베기"): 다른 한정 룬처럼 위력 P 의 적용 스탯 = 네 스탯의 합 × 1.0, 마법 피해, 피해는 몹 무적 시간을 피해 (RuneLimited.hit),
 * 끝에 총 피해 글자. 수치는 RUNES.terra (rules_more.json), 돌파는 rune_break.json (3 · 6돌 + 6돌 지맥 폭발 — finale, 피해 없음).
 * <ol>
 *   <li>대검 모임 (charge 틱): 시전자 머리 위 (칼자루 = 발 + HILT_UP · 조금 뒤 · 오른쪽) 에 대지의 대검 (3D 모델 — tools/terra_blade.py, 아이템 표시 엔티티) 이
 *       칼끝을 하늘로 한 채 커지며 생기고 (둘레 땅에서 바위 · 흙이 솟아 모임), 뒤로 더 젖혀 힘을 모음. 앞 땅에는 벨 줄이 빛남 (시전자 앞 start ~ start + length 칸)</li>
 *   <li>직선 베기 (SWING_T 틱 — 점점 빠르게): 대검이 앞으로 크게 휘둘러져 내려옴 (휘두르는 면 = 칼자루와 앞 줄을 지나는 세로면, 칼끝이 지나간 자리에
 *       호박빛 궤적 — 1인칭 · 3인칭에서는 화면 가운데를 세로로 가르는 빛줄기) → 칼끝이 앞 줄 위 (약 9칸) 에 박힘</li>
 *   <li>갈라짐: 박힌 순간 칼 아래 줄 (시전자 앞 ~ 칼끝) 이 한꺼번에 갈라지고 이어 칼끝 너머 끝까지 앞으로 (CRACK_T 틱) — 용암빛 균열 그림 rpg:rfx_fissure ·
 *       양옆 땅이 판처럼 들림 (그 자리 블록의 블록 표시 엔티티). 갈라짐이 지나는 적마다 위력 × crush (굵은 금빛 숫자 !) · 옆으로 띄움 (6돌 기절).
 *       대검은 HOLD 틱 박혀 있다가 바위로 부서져 사라짐 (여진 동안 화면을 가리지 않게)</li>
 *   <li>갈라진 땅 여진 (gap 틱마다 hits 번): 균열을 따라 시전자 쪽에서 앞으로 바위 가시 · 불씨가 솟는 물결 (WAVE_T 틱) — 균열 안 모든 적 위력 × mult</li>
 *   <li>끝: 들린 땅이 내려앉음 → 총 피해. 6돌 지맥 폭발 (마지막 여진 뒤): 균열 양옆으로 바위 기둥 (꼭대기 마그마) 이 줄지어 솟았다 무너짐 (피해 없음)</li>
 * </ol>
 * 줄 = S (시전자 앞 start 칸) 에서 앞으로 d (0 ~ length) · 옆으로 width 칸. 화면 색 연출은 3돌부터 (옅은 모래빛 흙먼지 하늘 SKY_FOG · 메테오의 주황 번쩍임 —
 * RuneKit.ULT_ALIAS). 쓰러지면 남은 것은 멈춤 (표시 엔티티는 정해진 틱 뒤 FxDisplays 가 지움).
 */
final class RuneTerra {
    private RuneTerra() {}

    /** 짙은 틈 · 용암빛 (빛무리) · 심지 · 흙먼지 · 바위 */
    static final double[] RIFT = {0.16, 0.09, 0.05};
    static final double[] AMBER = {1.0, 0.5, 0.12};
    static final double[] MOLTEN = {1.0, 0.86, 0.5};
    static final double[] DUST = {0.6, 0.5, 0.38};
    static final double[] ROCK = {0.5, 0.42, 0.34};

    /** 대검: 표시 크기 (모델 1 = 크기 / 16 칸) · 모델 가운데 (8, 8, 8 — 코등이 바로 아래 손잡이) 에서 칼끝 (y 32) 까지 · 칼날 가운데 (y 22) 까지 (모델 단위) */
    static final double SCALE = 7.0;
    static final double TIP_U = 24.0;
    static final double MID_U = 14.0;
    /**
     * 칼자루 (모델 가운데) 자리: 시전자 발에서 위 · 앞 · 오른쪽 (칸). 사진 검토: 칼자루가 머리 위 (4.5칸 · 0.5칸 뒤) 면 코등이 · 칼날이 1인칭 · 3인칭 눈 2 ~ 4칸
     * 곁을 지나 화면을 덮음 → 앞 3칸 · 높이 6.5칸 (박힌 칼날은 눈에서 5.7칸 넘게 떨어져 지나고, 코등이는 1인칭 화면 위 밖), 가운데 (1인칭 · 3인칭에서 칼과
     * 궤적이 화면 가운데를 세로로 가름)
     */
    static final double HILT_UP = 6.5;
    static final double HILT_FWD = 3.0;
    static final double HILT_SIDE = 0.0;
    /** 칼끝이 땅속으로 박히는 깊이 */
    static final double TIP_BURY = 0.35;
    /** 칼 방향각 (앞 수평 = 0°, 위 = 90°, 90° 넘으면 뒤로 젖힘): 생길 때 · 힘을 모은 끝 */
    static final double RAISE = 100;
    static final double WINDUP = 135;
    /** 칼날 넓은 면: 휘두르는 면 안의 위 · 뒤 (시전자 눈 쪽) 에서 옆으로 기울인 각 — 1인칭 · 3인칭에서 칼날 면이 보이게, 조금 비틀어 입체감 */
    static final double TWIST = 15;
    /** 휘두르는 틱별 진행 (점점 빠르게 — 마지막이 박힘) · 박혀 있는 틱 · 부서지는 틱 */
    static final double[] SWING = {0.10, 0.27, 0.48, 0.72, 1.0};
    static final int HOLD = 12;
    static final int CRUMBLE = 5;
    /** 땅이 갈라지는 틱 (박힌 순간 칼 아래 줄 → 칼끝 너머 끝까지) · 여진 물결이 균열을 지나는 틱 */
    static final int CRACK_T = 6;
    static final int WAVE_T = 4;
    /** 들리는 땅 판: 틈의 반폭 (선에서 판 안쪽 끝까지) · 두께 */
    static final double GAP = 0.36;
    static final double PLATE_T = 0.55;
    static final Identifier BLADE_MODEL = Identifier.fromNamespaceAndPath("rpg", "fx_terra_blade");
    /** 대검 · 기둥의 밝기 (밤에도 맥 빛으로 보이게 — 블록 빛 11) */
    static final Brightness LIGHT = new Brightness(11, 15);
    /** 하늘빛: 옅은 모래빛 흙먼지 (rpg:ult_sword — 12 ~ 84칸: 6돌 짙은 하늘빛에도 대검 · 균열이 보이게. 번쩍임은 메테오의 주황 — ULT_ALIAS) */
    static final String SKY_FOG = "rpg:ult_sword";
    /** 시전자 앞 이 칸 안에는 흙먼지 · 불씨 · 용암 방울을 띄우지 않음 (1인칭 화면에 크게 보이지 않게) */
    static final double NEAR_CLEAR = 4.5;

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
            if (!st.isAir() && st.getFluidState().isEmpty() && !st.getCollisionShape(level, p).isEmpty()) {
                return y + st.getCollisionShape(level, p).max(net.minecraft.core.Direction.Axis.Y);
            }
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

    /** 칼 방향각 e (도) 일 때 칼끝 쪽 단위 벡터 (u = 휘두르는 쪽 수평) */
    static Vec3 bladeDir(Vec3 u, double e) {
        double r = Math.toRadians(e);
        return V.add(V.scale(u, Math.cos(r)), V.UP, Math.sin(r));
    }

    /** 칼 방향각 e 의 대검 회전 (모델 +y = 칼끝, +z = 넓은 면 — 휘두르는 면 안의 위 · 뒤에서 TWIST 만큼 옆으로) */
    static Quaternionf bladeRot(Vec3 u, double e) {
        double r = Math.toRadians(e);
        Vec3 dir = bladeDir(u, e);
        Vec3 flat = V.add(V.scale(u, -Math.sin(r)), V.UP, Math.cos(r));
        Vec3 n1 = V.unit(V.cross(u, V.UP));
        double t = Math.toRadians(TWIST);
        Vec3 ez = V.unit(V.add(V.scale(flat, Math.cos(t)), n1, Math.sin(t)));
        Vec3 ex = V.unit(V.cross(dir, ez));
        return FxDisplays.basis(ex, V.unit(dir), ez);
    }

    /** 대검 변환: 방향각 e · 크기 비율 s (부서질 때 — 칼날 가운데로 오그라듦) */
    static Transformation bladeAt(Vec3 u, double e, double s) {
        Vec3 mid = V.scale(bladeDir(u, e), MID_U * SCALE / 16);
        return FxDisplays.of(V.scale(mid, 1 - s), bladeRot(u, e), SCALE * s);
    }

    /** 줄 위의 땅: S 에서 앞으로 d · 옆으로 lat */
    static Vec3 lineAt(ServerLevel level, Vec3 S, Vec3 f, Vec3 side, double d, double lat, double refY) {
        Vec3 p = V.add(V.add(S, f, d), side, lat);
        return new Vec3(p.x, groundY(level, p.x, p.z, refY), p.z);
    }

    /** 균열 안의 적: S 에서 앞으로 d (-1.2 ~ L + 0.5) · 옆으로 width (+ 몸 폭) 안 · 위아래 3.5칸 */
    static List<LivingEntity> inRift(ServerPlayer sp, Vec3 S, Vec3 f, double L, double W, double refY) {
        Vec3 mid = V.add(S, f, L / 2);
        double r = L / 2 + W + 2;
        Vec3 side = V.rightOf(f);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : sp.level().getEntitiesOfClass(LivingEntity.class, new AABB(mid, mid).inflate(r, 5, r), x -> Kit.canHit(sp, x))) {
            Vec3 d = V.sub(e.position(), S);
            double along = V.dot(d, f);
            double lat = Math.abs(V.dot(d, side));
            if (along >= -1.2 && along <= L + 0.5 && lat <= W + 0.5 + Kit.bodyPad(e) && Math.abs(e.getY() - refY) <= 3.5) out.add(e);
        }
        return out;
    }

    static double alongOf(LivingEntity e, Vec3 S, Vec3 f) {
        return V.dot(V.sub(e.position(), S), f);
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
        Vec3 S = V.add(c, f, start);
        int lastWave = charge + CRACK_T + hits * gap + WAVE_T;
        int settleAt = lastWave + 8;
        int endAt = settleAt + 18;
        RuneLimited.Tally tally = new RuneLimited.Tally();
        double skyK = skyTicksOf(sp);
        RuneKit.ultSkyFor(sp, level, c, "earthSplitter", (int) Math.round((endAt + 20) / skyK), SKY_FOG);

        // ---- 대검 자리: 칼자루 H · 칼끝이 박힐 곳 T (줄 위 — 칼 길이만큼 떨어진 곳) · 휘두르는 쪽 u · 박힌 방향각
        double reach = TIP_U * SCALE / 16;
        Vec3 H = V.add(V.add(V.up(c, HILT_UP), f, HILT_FWD), side, HILT_SIDE);
        double dist = 11.0;
        Vec3 T = c;
        for (int i = 0; i < 2; i++) {
            Vec3 g = V.add(c, f, dist);
            double gy = groundY(level, g.x, g.z, refY) - TIP_BURY;
            double h = Math.max(0.5, H.y - gy);
            double horiz = Math.sqrt(Math.max(1, reach * reach - h * h));
            dist = HILT_FWD + Math.sqrt(Math.max(1, horiz * horiz - HILT_SIDE * HILT_SIDE));
            T = new Vec3(c.x + f.x * dist, gy, c.z + f.z * dist);
        }
        Vec3 u = V.unit(new Vec3(T.x - H.x, 0, T.z - H.z));
        double eHit = -Math.toDegrees(Math.atan2(H.y - T.y, Math.hypot(T.x - H.x, T.z - H.z)));
        double dT = Math.max(1.0, Math.min(L, dist - start)); // 칼끝 자리 (줄에서)
        final Vec3 TT = T;
        final Vec3 tip0 = V.up(T, TIP_BURY);

        // ---- 대검 (아이템 표시 — 칼자루 자리에 두고 돌림): 작게 생겨 커짐 → 뒤로 젖힘 → 휘두름 → 박힘 → 부서짐
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.ITEM_MODEL, BLADE_MODEL);
        int swingAt = charge - SWING.length;
        int crumbleAt = charge + HOLD;
        Display.ItemDisplay blade = FxDisplays.item(level, H, stack, bladeAt(u, RAISE, 0.05), crumbleAt + CRUMBLE + 2, LIGHT);
        RuneKit.later(2, () -> FxDisplays.move(blade, bladeAt(u, RAISE, 1), 6));
        RuneKit.later(9, () -> FxDisplays.move(blade, bladeAt(u, WINDUP, 1), Math.max(1, swingAt - 9)));
        for (int i = 0; i < SWING.length; i++) {
            final double e = WINDUP + (eHit - WINDUP) * SWING[i];
            RuneKit.later(swingAt + i, () -> FxDisplays.move(blade, bladeAt(u, e, 1), 1));
        }
        // 박힌 뒤 조금 튀었다 돌아옴 · 부서짐 (칼날 가운데로 오그라들며 바위가 쏟아짐)
        RuneKit.later(charge + 1, () -> FxDisplays.move(blade, bladeAt(u, eHit + 2.5, 1), 2));
        RuneKit.later(charge + 3, () -> FxDisplays.move(blade, bladeAt(u, eHit, 1), 3));
        RuneKit.later(crumbleAt, () -> {
            if (!RuneKit.sameLife(sp, life)) {
                FxDisplays.remove(blade);
                return;
            }
            FxDisplays.move(blade, bladeAt(u, eHit, 0.02), CRUMBLE);
            Vec3 dir = bladeDir(u, eHit);
            for (int i = 2; i <= 9; i++) {
                Vec3 p = V.add(H, dir, reach * i / 9.0);
                RuneKit.rockBurst(level, p, 3, 3.5, 0.26);
                if (i % 2 == 0 && V.len(V.sub(p, sp.getEyePosition())) > NEAR_CLEAR) {
                    SkillFx.fx(level, "rpg:dust", p, Opt.of("size", 1.4, "life", 0.7, "color", DUST, "alpha", 0.45));
                }
            }
            SkillFx.play(level, "block.deepslate.break", TT, 0.6, 1.5);
            SkillFx.play(level, "block.stone.break", H, 0.7, 1.2);
            SkillFx.play(level, "block.basalt.break", V.add(H, dir, reach / 2), 0.55, 1.4);
        });

        // ---- 모임: 발밑 룬 · 둘레 땅에서 바위 · 흙이 솟아 대검으로 · 앞 땅에 벨 줄 (점점 밝게) · 소리
        SkillFx.rune(level, V.up(c, 0.05), Opt.of("size", 2.4, "life", (charge + 2) / 20.0, "color", AMBER, "alpha", 0.85, "rate", 90.0, "progress", 1.0));
        SkillFx.play(level, "entity.warden.emerge", c, 0.8, 0.7);
        SkillFx.play(level, "block.deepslate.break", c, 0.5, 1.2);
        SkillFx.play(level, "block.rooted_dirt.break", c, 0.6, 1.0);
        Kit.ticks(t -> {
            if (!RuneKit.sameLife(sp, life) || t >= swingAt) return false;
            if (t % 2 == 0) {
                // 둘레 2 ~ 4칸 땅에서 칼자루 쪽으로 솟는 바위 · 흙
                double a = Kit.rand() * Math.PI * 2;
                double rr = 2 + Kit.rand() * 2;
                Vec3 g = new Vec3(c.x + Math.cos(a) * rr, refY + 0.2, c.z + Math.sin(a) * rr);
                Vec3 to = V.sub(V.up(H, 1.5), g);
                SkillFx.fx(level, "rpg:boulder", g, Opt.of("dir", V.unit(to), "speed", V.len(to) / 0.5, "size", 0.24 + Kit.rand() * 0.1, "life", 0.5,
                        "spin", Kit.rand() * 360, "color", ROCK, "alpha", 1.0));
                SkillFx.fx(level, "rpg:dust", g, Opt.of("size", 0.9, "life", 0.5, "color", DUST, "alpha", 0.4));
            }
            if (t % 4 == 0) {
                // 벨 줄: S → 끝, 처음엔 옅게 점점 밝게
                double a = Math.min(1, 0.3 + t / (double) swingAt);
                RuneKit.streak(level, V.up(S, 0.06), V.up(V.add(S, f, L), 0.06), AMBER, 0.12 + 0.1 * a, 0.25, 0.5 * a, 0);
                if (t % 8 == 0) SkillFx.play(level, "block.rooted_dirt.break", V.add(S, f, L * Kit.rand()), 0.6 + t * 0.01, 0.8);
            }
            if (t >= 9 && t % 3 == 0) {
                // 젖히는 동안 칼날의 맥에서 불씨
                Vec3 dir = bladeDir(u, WINDUP);
                SkillFx.sparks(level, V.add(H, dir, reach * (0.4 + Kit.rand() * 0.5)), AMBER, 3, 2);
            }
            return true;
        });
        RuneKit.later(swingAt - 4, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            SkillFx.play(level, "entity.warden.sonic_charge", c, 0.9, 0.9);
            SkillFx.play(level, "item.trident.riptide_3", c, 0.6, 0.8);
        });

        // ---- 휘두름: 칼끝이 지나간 자리의 호박빛 궤적 (부채꼴 — 칼끝 쪽일수록 밝고 굵게)
        double[] radii = {0.5, 0.66, 0.8, 0.92, 1.0};
        for (int i = 0; i < SWING.length; i++) {
            final double e0 = WINDUP + (eHit - WINDUP) * (i == 0 ? 0 : SWING[i - 1]);
            final double e1 = WINDUP + (eHit - WINDUP) * SWING[i];
            final int ii = i;
            RuneKit.later(swingAt + i, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                int sub = 3;
                for (int s = 0; s < sub; s++) {
                    double a0 = e0 + (e1 - e0) * s / sub;
                    double a1 = e0 + (e1 - e0) * (s + 1) / sub;
                    for (int q = 0; q < radii.length; q++) {
                        double rr = reach * radii[q];
                        Vec3 p0 = V.add(H, bladeDir(u, a0), rr);
                        Vec3 p1 = V.add(H, bladeDir(u, a1), rr);
                        boolean outer = q == radii.length - 1;
                        double w = (0.35 + 0.6 * radii[q]) * (ii >= SWING.length - 2 ? 1.4 : 1.0);
                        RuneKit.streak(level, p0, p1, AMBER, w, 0.32, 0.35 + 0.5 * radii[q], 0);
                        if (outer) RuneKit.streak(level, p0, p1, MOLTEN, w * 0.4, 0.3, 1.0, 0);
                    }
                }
                if (ii == 1) SkillFx.play(level, "entity.player.attack.sweep", H, 0.5, 1.6);
                if (ii == 3) SkillFx.play(level, "item.trident.riptide_1", TT, 0.6, 1.0);
            });
        }

        // ---- 들리는 땅 판 (박히기 직전에 땅속에 숨겨 띄움 → 갈라짐이 지날 때 들림)
        List<Plate> plates = new ArrayList<>();
        RuneKit.later(charge - 4, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            for (double d = 0.2; d < L + 1.0; d += 1.0) {
                for (int sgn : new int[] {-1, 1}) {
                    Plate p = Plate.make(level, S, f, side, d + (Kit.rand() - 0.5) * 0.15, sgn, refY, endAt, start);
                    if (p != null) plates.add(p);
                }
            }
        });

        // ---- 박힘: 충격 · 번쩍임 (칼끝 자리)
        RuneKit.later(charge, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            RuneKit.ultFlashFor(sp, level, TT, "earthSplitter", "flash", Opt.empty());
            RuneKit.shakeAround(level, TT, L + 8, 0.6, 0.6);
            SkillFx.play(level, "item.mace.smash_ground_heavy", TT, 0.55, 1.6);
            SkillFx.play(level, "entity.generic.explode", TT, 0.6, 1.2);
            SkillFx.play(level, "block.anvil.land", TT, 0.5, 0.7);
            SkillFx.play(level, "entity.warden.sonic_boom", c, 0.6, 0.35);
            SkillFx.shock(level, TT, AMBER, 3.2, 0.5);
            SkillFx.shock(level, TT, DUST, 5.0, 0.7);
            RuneKit.novaRing(level, TT, 1.2, 4.5, AMBER, MOLTEN, Opt.of("life", 0.45));
            RuneKit.glowCrack(level, TT, 3.6, AMBER, MOLTEN, Opt.of("life", (endAt - charge) / 20.0, "fade", 0.25, "v", 1.0));
            RuneKit.rockBurst(level, V.up(TT, 0.6), 16, 9, 0.34);
            SkillFx.fx(level, "rpg:dust", V.up(TT, 1.2), Opt.of("size", 4.0, "life", 1.1, "color", DUST, "alpha", 0.5));
            SkillFx.burst(level, V.up(tip0, 0.4), AMBER, 3.0, 0.35);
            SkillFx.sparks(level, V.up(tip0, 0.6), MOLTEN, 20, 7);
            // 칼 아래 땅을 앞으로 긋는 빛 (베인 줄)
            RuneKit.streak(level, V.up(S, 0.08), V.up(V.add(S, f, dT), 0.08), MOLTEN, 0.5, 0.4, 1.0, 0);
        });

        // ---- 갈라짐: 박힌 순간 칼 아래 (d ≤ dT) 한꺼번에 → 칼끝 너머 끝까지 앞으로 (CRACK_T - 1 틱)
        Set<UUID> slammed = new HashSet<>();
        List<Seg> segs = Seg.of(L);
        for (int j = 0; j < CRACK_T; j++) {
            final int jj = j;
            final double a0 = j == 0 ? -3 : dT + (L + 1 - dT) * (j - 1) / (CRACK_T - 1);
            final double a1 = j == 0 ? dT : dT + (L + 1 - dT) * j / (CRACK_T - 1);
            RuneKit.later(charge + j, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                for (Seg s : segs) if (s.a0() >= a0 - 1e-9 && s.a0() < a1) s.draw(level, S, f, side, refY, (endAt + 10 - charge - jj) / 20.0);
                for (Plate p : plates) if (p.d >= a0 && p.d < a1) p.raise();
                int nd = 0;
                for (double a = Math.max(0, a0); a < Math.min(a1, L); a += 1.2) {
                    Vec3 g = lineAt(level, S, f, side, a, (Kit.rand() - 0.5) * 0.6, refY);
                    SkillFx.fx(level, "rpg:boulder", V.up(g, 0.3), Opt.of("dir", V.unit(new Vec3(side.x * (Kit.rand() - 0.5), 1.3, side.z * (Kit.rand() - 0.5))),
                            "speed", 5 + Kit.rand() * 3, "size", 0.2 + Kit.rand() * 0.12, "life", 0.6, "spin", Kit.rand() * 360, "color", ROCK, "alpha", 1.0));
                    if (nd++ % 2 == 0 && start + a > NEAR_CLEAR) {
                        SkillFx.fx(level, "rpg:dust", V.up(g, 0.5), Opt.of("size", 1.0, "life", 0.6, "color", DUST, "alpha", 0.35));
                    }
                    SkillFx.fx(level, "rpg:arrow_spark", V.up(g, 0.2), Opt.of("dir", V.UP, "speed", 3.5, "size", 0.22, "grow", 0.8, "life", 0.4, "color", AMBER, "alpha", 1.0));
                }
                if (jj > 0 && jj % 2 == 1) SkillFx.play(level, "block.basalt.break", lineAt(level, S, f, side, (a0 + a1) / 2, 0, refY), 0.5, 1.4);
                // 갈라짐이 지나는 적: 대검 (위력 × crush) · 옆으로 띄움 (6돌 기절)
                boolean last = jj == CRACK_T - 1;
                for (LivingEntity e : inRift(sp, S, f, L, W, refY)) {
                    double along = alongOf(e, S, f);
                    if (!last && (along < a0 || along >= a1)) continue;
                    if (!slammed.add(e.getUUID())) continue;
                    double sgn = Math.signum(V.dot(V.sub(e.position(), S), side));
                    Vec3 out = V.scale(side, sgn == 0 ? 1 : sgn);
                    RuneLimited.hit(sp, life, e, P, k.num("crush"), DamageNumbers.Style.CLEAVE, tally, x -> {
                        RuneKit.knock(x, out, 0.35, lift);
                        if (stun > 0) RuneKit.stun(x, stun, AMBER, slammed.size() <= 10);
                    });
                }
            });
        }

        // ---- 갈라진 땅 여진 (gap 틱마다 hits 번 — 물결이 시전자 쪽에서 앞으로 WAVE_T 틱)
        for (int i = 1; i <= hits; i++) {
            final int at = charge + CRACK_T + i * gap;
            final int idx = i;
            Set<UUID> shook = new HashSet<>();
            RuneKit.later(at, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 mid = V.add(S, f, L / 2);
                SkillFx.play(level, "block.basalt.break", mid, 0.45 + idx * 0.03, 1.5);
                SkillFx.play(level, "entity.generic.explode", mid, 0.8, 0.35);
                SkillFx.play(level, "block.deepslate.break", TT, 0.55, 1.0);
                RuneKit.shakeAround(level, mid, L, 0.22, 0.25);
                for (Plate p : plates) p.bump();
            });
            for (int j = 0; j < WAVE_T; j++) {
                final int jj = j;
                RuneKit.later(at + j, () -> {
                    if (!RuneKit.sameLife(sp, life)) return;
                    double a0 = jj == 0 ? -1.5 : L * jj / WAVE_T;
                    double a1 = jj == WAVE_T - 1 ? L + 1 : L * (jj + 1) / WAVE_T;
                    int n = 0;
                    for (double a = Math.max(0.6, a0 + Kit.rand() * 0.6); a < Math.min(L, a1); a += 1.4) {
                        double lat = ((n++ % 2 == 0) ? 1 : -1) * (0.3 + Kit.rand() * 0.5);
                        Vec3 g = lineAt(level, S, f, side, a, lat, refY);
                        RuneKit.rockSpike(sp, level, g, 0.75 + Kit.rand() * 0.5, AMBER, Opt.of("life", 0.5, "fade", 0.45, "full", 5.0));
                        if (start + a > NEAR_CLEAR) SkillFx.fx(level, "rpg:dust", V.up(g, 0.4), Opt.of("size", 0.8, "life", 0.5, "color", DUST, "alpha", 0.35));
                        if (Kit.rand() < 0.5) {
                            SkillFx.fx(level, "rpg:arrow_spark", V.up(g, 0.2), Opt.of("dir", V.UP, "speed", 4.0, "size", 0.2, "grow", 0.6, "life", 0.45, "color", AMBER,
                                    "alpha", 1.0));
                        }
                    }
                    boolean last = jj == WAVE_T - 1;
                    for (LivingEntity e : inRift(sp, S, f, L, W, refY)) {
                        double along = alongOf(e, S, f);
                        if (!last && (along < a0 || along >= a1)) continue;
                        if (!shook.add(e.getUUID())) continue;
                        double sgn = Math.signum(V.dot(V.sub(e.position(), S), side));
                        Vec3 out = V.scale(side, sgn == 0 ? 1 : sgn);
                        RuneLimited.hit(sp, life, e, P, k.num("mult"), DamageNumbers.Style.QUAKE, tally, x -> RuneKit.knock(x, out, 0.08, 0.28));
                    }
                });
            }
        }

        // ---- 균열에서 오르는 불씨 · 용암 방울 (갈라진 뒤 끝날 때까지 3틱마다 — 시전자 가까이는 빼고)
        RuneKit.later(charge, () -> Kit.ticks(t -> {
            if (!RuneKit.sameLife(sp, life) || t >= settleAt - charge) return false;
            if (t % 3 != 0) return true;
            double near = Math.max(0, NEAR_CLEAR - start);
            for (int i = 0; i < 2; i++) {
                Vec3 g = lineAt(level, S, f, side, near + Kit.rand() * (L - near), (Kit.rand() - 0.5) * 0.5, refY);
                SkillFx.fx(level, "rpg:arrow_spark", V.up(g, 0.1), Opt.of("dir", V.UP, "speed", 1.6 + Kit.rand(), "size", 0.14, "grow", 0.4, "life", 0.7, "color", AMBER,
                        "alpha", 0.95));
            }
            if (t % 6 == 0) {
                Vec3 g = lineAt(level, S, f, side, near + Kit.rand() * (L - near), 0, refY);
                level.sendParticles(ParticleTypes.LAVA, g.x, g.y + 0.1, g.z, 1, 0.1, 0, 0.1, 0);
            }
            return true;
        }));

        // ---- 끝: 들린 땅이 내려앉음 (6돌 — 지맥 폭발, 총 피해는 그 뒤)
        RuneKit.later(settleAt, () -> {
            if (!RuneKit.sameLife(sp, life)) return;
            SkillFx.play(level, "block.grindstone.use", V.add(S, f, L / 2), 0.4, 0.8);
            for (Plate p : plates) p.settle();
        });
        if (fin) RuneKit.later(lastWave + 2, () -> veinBurst(sp, level, S, f, side, L, start, refY, life));
        int totalAt = lastWave + (fin ? 34 : 10);
        RuneLimited.total(sp, "earthSplitter", V.up(V.add(S, f, L * 0.55), 3.2), tally, totalAt);
        return true;
    }

    /** 이 사람이 지금 쓴 대지 가르기의 하늘빛 시간 배율 (3돌 미만이면 1 — 긴 룬이라 6돌 배율 1.5 를 나눠 씀) */
    static double skyTicksOf(ServerPlayer sp) {
        mmorpg.core.RuneBreak.Fx fx = Breakthrough.castFx(sp, "earthSplitter");
        return fx != null && fx.skyTicks() > 0 ? fx.skyTicks() : 1;
    }

    /**
     * 6돌 지맥 폭발: 균열을 따라 (시전자 앞 NEAR_CLEAR + 2칸 밖 — 1인칭 화면을 막지 않게) 시전자 쪽에서 앞으로 바위 기둥 (점적석 · 꼭대기 마그마) 이
     * 균열 양옆으로 번갈아 줄지어 솟구쳤다 (3틱) 무너지고 (14틱 뒤 가라앉음), 기둥 밑에서 용암빛 · 바위가 터짐 (피해 없음)
     */
    static void veinBurst(ServerPlayer sp, ServerLevel level, Vec3 S, Vec3 f, Vec3 side, double L, double start, double refY, String life) {
        if (!RuneKit.sameLife(sp, life)) return;
        List<Double> spots = new ArrayList<>();
        for (double d = NEAR_CLEAR + 2.0 - start; d <= L + 0.5; d += 1.5) spots.add(d);
        int n = spots.size();
        for (int i = 0; i < n; i++) {
            final double d = spots.get(i);
            final int delay = i * 12 / Math.max(1, n);
            final double lat = (i % 2 == 0 ? 1 : -1) * (0.9 + Kit.rand() * 0.7);
            RuneKit.later(delay, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 g = lineAt(level, S, f, side, d, lat, refY);
                double far = Math.max(0, Math.min(1, d / L)); // 멀수록 높게
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
                    SkillFx.fx(level, "rpg:arrow_spark", V.up(g, h - 0.2), Opt.of("dir", V.UP, "speed", 5.0, "size", 0.45, "grow", 1.0, "life", 0.4, "color", MOLTEN,
                            "alpha", 1.0));
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
        Vec3 mid = V.add(S, f, L / 2);
        RuneKit.shakeAround(level, mid, L + 6, 0.45, 0.8);
        SkillFx.play(level, "entity.generic.explode", mid, 0.5, 1.4);
        SkillFx.play(level, "item.mace.smash_ground_heavy", mid, 0.7, 1.2);
    }

    // ------------------------------------------------------------ 균열 그림 조각

    /** 균열 그림 한 조각 (S 에서 앞으로 a0 ~ end — 이웃 조각과 35% 겹침) */
    record Seg(double a0, double end) {
        static List<Seg> of(double L) {
            int m = (int) Math.ceil(L / 6.5);
            List<Seg> out = new ArrayList<>();
            double step = L / m;
            for (int i = 0; i < m; i++) out.add(new Seg(Math.max(-0.6, i * step - step * 0.18), Math.min(L + 0.3, (i + 1) * step + step * 0.18)));
            return out;
        }

        void draw(ServerLevel level, Vec3 S, Vec3 f, Vec3 side, double refY, double life) {
            double mid = (a0 + end) / 2;
            Vec3 g = lineAt(level, S, f, side, mid, 0, refY);
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
        final Display.BlockDisplay e;
        final double d;
        final Vec3 o;
        final double l;
        final double w;
        final double phi;

        Plate(Display.BlockDisplay e, double d, Vec3 o, double l, double w, double phi) {
            this.e = e;
            this.d = d;
            this.o = o;
            this.l = l;
            this.w = w;
            this.phi = phi;
        }

        /** S 에서 앞으로 d 칸, sgn 쪽 (오른쪽 +1 · 왼쪽 -1) 판 — 땅속에 숨긴 채 띄움 (공중이면 null). 시전자 앞 3칸 안은 낮게 들림 (1인칭 화면 아래를 덮지 않게) */
        static Plate make(ServerLevel level, Vec3 S, Vec3 f, Vec3 side, double d, int sgn, double refY, int life, double start) {
            double l = 1.0 + Kit.rand() * 0.22;
            double w = 1.15 + Kit.rand() * 0.35;
            Vec3 o = V.scale(side, sgn);
            Vec3 line = V.add(S, f, d);
            Vec3 hingeXZ = V.add(line, o, GAP + w);
            double gy = groundY(level, hingeXZ.x, hingeXZ.z, refY);
            BlockState st = plateBlock(level, hingeXZ.x, gy, hingeXZ.z);
            if (st == null) return null;
            double phi = 24 + Kit.rand() * 14;
            if (d < 3.2) phi *= 0.55;
            Vec3 hinge = new Vec3(hingeXZ.x, gy, hingeXZ.z);
            Plate p0 = new Plate(null, d, o, l, w, phi);
            Display.BlockDisplay e = FxDisplays.block(level, hinge, st, p0.at(0, -(PLATE_T + 0.45)), life, null);
            return e == null ? null : new Plate(e, d, o, l, w, phi);
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
            FxDisplays.move(e, at(phi, -PLATE_T * 0.55), 3);
        }

        /** 여진: 조금 더 들렸다 (2틱) 돌아옴 (3틱) */
        void bump() {
            FxDisplays.move(e, at(phi + 7, -PLATE_T * 0.45), 2);
            RuneKit.later(2, () -> FxDisplays.move(e, at(phi, -PLATE_T * 0.55), 3));
        }

        void settle() {
            FxDisplays.move(e, at(5, -PLATE_T - 0.2), 12);
        }
    }
}
