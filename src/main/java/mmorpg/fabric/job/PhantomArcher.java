package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.RuneProps;
import mmorpg.fabric.rune.RuneSwords;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-15 팬텀 아처 (궁수 리메이크 — 사용자 "궁수는 정확히는 리메이크임" · "아처 1차는 … 에미야 시로 같은 느낌으로", claude/archer-remake.md): 특성 「환영
 * 무기」 와 직업이 같이 쓰는 그림.
 * <p>
 * 투영: 빛의 선 (푸른 흰빛 2D 선) 이 칼 모양 (칼끝 · 날 어깨 · 코등이 · 자루) 을 그린 뒤 3D 칼 (rune_sword — 반투명 강철빛 날 · 진홍 홈 · 보석) 이 커지며
 * 나타남. 활 평타는 화살 대신 투영한 칼을 쏘고, 맞은 적 몸에 칼이 꽂혀 남음 (적마다 최대 max · 칼마다 life 틱). max 째가 꽂히면 burstDelay 틱 뒤 꽂힌 칼이
 * 모두 터짐 (둘레 radius 칸의 적마다 활 평타 × burst — 칼 파편 · 진홍 폭발). 직업 스킬로 맞혀도 꽂힘 (스킬마다 lodge). 원작의 이름 · 고유 무기 디자인은 쓰지
 * 않음 (느낌만).
 */
public final class PhantomArcher {
    private PhantomArcher() {}

    /** 칼 날 (반투명 강철빛 — rune_sword 물들임 0) · 홈 · 보석 (진홍 — 물들임 1) */
    static final double[] BLADE = {0.9, 0.95, 1.0}; // 2026-11-15 영상: 0.8 · 0.88 은 낮 하늘 앞에서 잿빛으로 가라앉았음
    static final double[] GEM = {0.92, 0.12, 0.14}; // 2026-11-15 미리보기: 더 짙은 진홍
    /** 투영 선 (푸른 흰빛) */
    static final double[] TRACE = {0.42, 0.78, 1.0};
    static final double[] TRACE_CORE = {0.9, 0.97, 1.0};
    /** 폭발 (진홍 불꽃 · 흰 심지) */
    static final double[] FIRE = {1.0, 0.36, 0.12};
    static final double[] FIRE_CORE = {1.0, 0.82, 0.3};

    /**
     * 2026-11-15 직업마다 칼 · 폭발 색 (팬텀 아처 진홍 · 2차 아스널 킹 금빛 보물 · 2차 블레이드 레인저 단조 불빛) · HD 칼 모델 (tools/vfx_hd.py — 구운 색: 강철 · 금 ·
     * 보석 · 가죽) · 땅 마법진 모델
     */
    record Pal(double[] blade, double[] gem, double[] fire, double[] fireCore, String sword, String sigil) {}

    static final String SIGIL_CRIMSON = "rpg:vfx/sigil_crimson";
    static final String SIGIL_GOLD = "rpg:vfx/sigil_gold";
    static final String SIGIL_FORGE = "rpg:vfx/sigil_forge";
    static final String FORGE_SWORD = "rpg:vfx/forge_sword";
    /** 아스널 킹 보물 셋 (금빛 장검 · 창 · 도끼 — 원작에 없는 새 디자인, 쥔 곳 · 칼끝은 투영 장검과 같음) */
    static final String[] TREASURES = {"rpg:vfx/treasure_sword", "rpg:vfx/treasure_spear", "rpg:vfx/treasure_axe"};

    static final Pal CRIMSON_PAL = new Pal(BLADE, GEM, FIRE, FIRE_CORE, RuneProps.PHANTOM_SWORD, SIGIL_CRIMSON);
    static final Pal GOLD_PAL = new Pal(new double[] {1.0, 0.86, 0.42}, new double[] {0.95, 0.18, 0.14}, new double[] {1.0, 0.66, 0.12}, new double[] {1.0, 0.95, 0.62},
            TREASURES[0], SIGIL_GOLD);
    static final Pal FORGE_PAL = new Pal(BLADE, new double[] {1.0, 0.42, 0.08}, new double[] {1.0, 0.42, 0.08}, new double[] {1.0, 0.86, 0.36}, FORGE_SWORD, SIGIL_FORGE);

    /** 2026-11-15 아스널 킹 보물 하나 (돌아가며 — 장검 · 창 · 도끼) */
    static String treasure(int i) {
        return TREASURES[Math.floorMod(i, TREASURES.length)];
    }

    static Pal pal(Jobs.Job j) {
        if (j == null) return CRIMSON_PAL;
        return switch (j.id()) {
            case "arsenalking" -> GOLD_PAL;
            case "bladeranger" -> FORGE_PAL;
            default -> CRIMSON_PAL;
        };
    }

    /** 2026-11-15 맞은 자리 · 평타 잔상 색: 팬텀 아처 진홍 · 블레이드 레인저 단조 주황 · 아스널 킹 금빛 (보석 루비 대신 — 금빛 묶음과 맞춤) */
    static double[] accent(Jobs.Job j) {
        return j != null && j.id().equals("arsenalking") ? GOLD_PAL.fire() : pal(j).gem();
    }

    /** 2026-11-15 직업 전용 2D 그림판 이름 앞 (pa_ · ak_ · br_ — 영상 몸풀기 Vfx.warm), 그리지 않은 직업은 null */
    public static String vfxPrefix(String jobId) {
        PhantomFx.Lib L = PhantomFx.lib(mmorpg.core.Jobs.job(jobId));
        return L == null ? null : L.p() + "_";
    }

    /** 팬텀 아처 줄기 (1차 · 2차 둘) */
    static boolean family(Jobs.Job j) {
        return j != null && (j.id().equals("archer") || j.id().equals("arsenalking") || j.id().equals("bladeranger"));
    }

    /** 꽂힌 칼 하나 */
    private static final class Stuck {
        final RuneSwords.Blade blade;
        final long until;
        boolean gone;

        Stuck(RuneSwords.Blade blade, long until) {
            this.blade = blade;
            this.until = until;
        }
    }

    /** 한 사람이 한 적에게 꽂은 칼 */
    private static final class Lodge {
        final List<Stuck> swords = new ArrayList<>();
        boolean bursting;
    }

    private static final Map<String, Lodge> LODGES = new HashMap<>();
    private static final Map<UUID, LivingEntity> LAST = new HashMap<>();

    private static String key(ServerPlayer sp, LivingEntity e) {
        return sp.getUUID() + ":" + e.getUUID();
    }

    private static double trait(Jobs.Job j, String key, double fallback) {
        return j == null || j.trait() == null ? fallback : j.trait().def().numOr(key, fallback);
    }

    private static int max(Jobs.Job j) {
        return j == null || j.trait() == null ? 5 : Math.max(1, j.trait().max());
    }

    /** 칼의 넓은 면 (옆 · 조금 위 — 3인칭 옆에서도 1인칭 뒤에서도 날이 보이게) */
    static Vec3 faceOf(Vec3 d) {
        // 2026-11-15 HD: 넓은 면을 왼쪽 위 45° 로 (마크 면 밝기 — 위를 보는 면은 1, 아래를 보는 면은 0.4: 전에는 오른쪽 위 31° 라 왼쪽 뒤 영상 카메라 ·
        // 위에서 보는 눈에는 아래를 보는 어두운 면이 보였음 → 칼이 잿빛으로 가라앉았음)
        Vec3 side = Math.abs(d.y) > 0.95 ? new Vec3(-1, 0, 0) : V.scale(V.rightOf(V.flat(d)), -1);
        return V.unit(V.add(side, V.UP, 1.0));
    }

    // ------------------------------------------------------------ 투영

    /**
     * 투영 선: 칼 모양 (자루 hilt → 칼끝, 길이 len 칸 · 넓은 면 face) 을 빛의 선 다섯으로 그림 (칼끝 ← 날 어깨 둘 · 코등이 · 자루) — life 초 뒤 사라짐
     */
    static void traceLines(ServerLevel level, Vec3 hilt, Vec3 dir, Vec3 face, double len, double life) {
        trace2d(level, hilt, dir, len, Math.max(0.3, life));
    }

    /** 위와 같고 투영 선이 발광 (glow — 직업 전용 그림 묶음의 trace 색) */
    static void traceLines(ServerLevel level, Vec3 hilt, Vec3 dir, Vec3 face, double len, double life, int glow) {
        trace2d(level, hilt, dir, len, Math.max(0.3, life), glow);
    }

    // ------------------------------------------------------------ 2026-11-15 단색 블록 볼륨 (사용자 "그냥 위르드처럼" · "도트처럼 보이는게 문제" — BlockFx)
    // (같은 날 그린 2D rfx_jp* 는 바닐라 글꼴에서 픽셀이 그대로 커져 도트로 보였음 → 본체를 무늬 없는 단색 상자 볼륨으로)

    /**
     * 맞은 자리 (2026-11-15 — JobFx.hit 의 2D 깃털 바람 그림 대신 단색 블록): 진홍 베인 줄 둘 (흰 심) 이 X 로 그어졌다 (둘째는 1틱 늦게) 가늘어짐 · 작은 번쩍 별.
     * 판은 가장 가까운 사람 (쏜 사람 — 32칸) 쪽을 봄
     */
    static void hitMark(ServerLevel level, LivingEntity e, double size) {
        hitMark(level, e, size, GEM);
    }

    /** 맞은 자리 (색 color — 2차는 직업 색) */
    static void hitMark(ServerLevel level, LivingEntity e, double size, double[] color) {
        Vec3 b = Kit.bodyPoint(e);
        net.minecraft.world.entity.player.Player p = level.getNearestPlayer(e, 32);
        Vec3 f = p != null ? V.sub(b, p.getEyePosition()) : new Vec3(Kit.rand() - 0.5, 0, Kit.rand() - 0.5);
        if (V.len(f) < 1e-3) f = new Vec3(1, 0, 0);
        f = V.unit(f);
        Vec3 u = V.rightOf(V.flat(f));
        if (V.len(u) < 1e-3) u = new Vec3(1, 0, 0);
        u = V.unit(u);
        Vec3 v = V.unit(V.cross(u, f));
        double a0 = Math.toRadians(30 + Kit.rand() * 25);
        Vec3 at = V.add(b, f, -0.35);
        // 2026-11-15 2D 베인 자리 (사용자 "참격이나 잔상 남는 연출도 … 2D 연출로"): 작은 참격 판 둘이 X 로 (둘째 0.05초 늦게) · 맞은 자리 섬광
        double[] core = mmorpg.fabric.vfx.BlockFx.hot(color);
        for (int s = 0; s < 2; s++) {
            double a = s == 0 ? a0 : Math.PI - a0 + (Kit.rand() - 0.5) * 0.3;
            Vec3 bulge = V.add(V.scale(u, Math.cos(a + Math.PI / 2)), v, Math.sin(a + Math.PI / 2));
            slash2d(level, V.add(at, bulge, -size * 0.32), V.scale(f, -1), bulge, size * 0.75, 0.38, SLASH_LIFE * 0.8, s * 0.05, color, core, false, s == 1);
        }
        spark2d(level, at, size * 0.9, color, core);
    }

    /** 투영 선: 칼 윤곽 (자루 · 코등이 · 날 양쪽 · 칼끝) 을 가는 푸른 흰빛 줄 다섯으로 — life 초 동안 가늘어지며 사라짐 */
    static void trace2d(ServerLevel level, Vec3 hilt, Vec3 dir, double len, double life) {
        trace2d(level, hilt, dir, len, life, -1);
    }

    /** 투영 선 + 발광 (glow ≥ 0 — 2026-11-15 직업 전용 이펙트: 푸른 흰 빛 번짐) */
    static void trace2d(ServerLevel level, Vec3 hilt, Vec3 dir, double len, double life, int glow) {
        Vec3 d = V.unit(dir);
        Vec3 side = V.rightOf(V.flat(d));
        if (V.len(side) < 1e-3) side = new Vec3(1, 0, 0);
        Vec3 up = V.unit(V.cross(side, d));
        Vec3 guard = V.add(hilt, d, len * 0.22);
        Vec3 tip = V.add(hilt, d, len);
        Vec3 sa = V.add(V.add(guard, d, len * 0.05), up, len * 0.07);
        Vec3 sb = V.add(V.add(guard, d, len * 0.05), up, -len * 0.07);
        int t = Math.max(4, (int) Math.round(life * 20));
        double th = 0.035 + len * 0.012;
        mmorpg.fabric.vfx.BlockFx.beam(level, hilt, guard, th, TRACE, t, glow);
        mmorpg.fabric.vfx.BlockFx.beam(level, V.add(guard, up, len * 0.16), V.add(guard, up, -len * 0.16), th, TRACE, t, glow);
        mmorpg.fabric.vfx.BlockFx.beam(level, sa, tip, th, TRACE, t, glow);
        mmorpg.fabric.vfx.BlockFx.beam(level, sb, tip, th, TRACE, t, glow);
    }

    /**
     * 칼 궤적 2026-11-15 2D 잔상 (사용자 "참격이나 잔상 남는 연출도 넣어서 그런거는 2D 연출로" — 상자 빛 줄 대신 잔상 줄기 판 streak_trail): from → to 를 머리가
     * 나아가고 (life 초 — 칼이 날아가는 동안) 꼬리부터 천천히 거둬짐
     */
    static void streak2d(ServerLevel level, Vec3 from, Vec3 to, double life, double[] glow, double[] core) {
        trail2d(level, from, to, life, 0.42, glow, core == null ? mmorpg.fabric.vfx.BlockFx.hot(glow) : core);
    }

    // ------------------------------------------------------------ 2026-11-15 2D 참격 · 잔상 · 섬광
    // (사용자 "3d만 쓰지 말고 참격이나 잔상 남는 연출도 넣어서 그런거는 2D 연출로 해버려" · "참격 나갈떄 너무 빨리 사라져서 잘 안보이게 하지 말고 잔상 느낌으로
    // 진짜 조금만 천천히 사려져봐" — 256 픽셀 글자 판 fxmodel slash_basic · streak_trail · hit_spark, 모드 없는 클라이언트도 같은 그림)

    /** 참격 판 삶 (초): 머리가 휩쓰는 몫 0.4 · 남은 0.6 동안 잔상이 꼬리부터 천천히 (예전 상자 호는 0.3초 남짓에 꺼졌음) */
    static final double SLASH_LIFE = 0.75;

    /**
     * 참격 판 하나 (slash_basic — 가늘고 날카로운 머리 · 뒤로 두꺼워지며 짙어지는 잔상): 가운데 c · 판의 면 face (보는 사람 쪽) · 볼록한 쪽 bulge · 반지름 r 칸 ·
     * 볼록한 쪽 납작하게 squash · 삶 life 초 · 늦춤 delay 초 · 빛깔 glow (몸) · core (날), wide = 두꺼운 띠, mirror = 거꾸로 휩쓺
     */
    static void slash2d(ServerLevel level, Vec3 c, Vec3 face, Vec3 bulge, double r, double squash, double life, double delay, double[] glow, double[] core,
            boolean wide, boolean mirror) {
        String model = "slash_basic" + (wide ? "_w" : "") + (mirror ? "_l" : "");
        double lf = Math.max(0.2, life);
        mmorpg.fabric.Fx3d.spawn(level, model, c, Opt.of("dir", V.unit(bulge), "face", V.unit(face), "roll", 0.0, "scale", r, "life", lf, "delay", delay, "grow", 0.06,
                "anim", "play", "animSpeed", 1.0 / lf, "fadeIn", 0.0, "fadeOut", lf * 0.25, "glowAlpha", 0.0, "color", glow, "glow", core, "alpha", 0.95, "shine", 0.12,
                "stretch", new Vec3(1, squash, 1)));
    }

    /** 몸 둘레에 누운 참격 판 (가로 휘두름 · 돌며 베기): 가운데 c · 앞 f (볼록한 쪽) · 반지름 r · 위아래 옅은 겹 (같은 높이 눈에서 옆선이 되지 않게) */
    static void sweep2d(ServerLevel level, Vec3 c, Vec3 f, double r, double life, double delay, double[] glow, double[] core, boolean wide, boolean mirror) {
        Vec3 ff = V.len(V.flat(f)) < 1e-3 ? new Vec3(0, 0, 1) : V.unit(V.flat(f));
        slash2d(level, c, V.UP, ff, r, 1.0, life, delay, glow, core, wide, mirror);
        for (double dy : new double[] {-0.14, 0.14}) {
            String model = "slash_basic" + (wide ? "_w" : "") + (mirror ? "_l" : "");
            double lf = Math.max(0.2, life);
            mmorpg.fabric.Fx3d.spawn(level, model, V.up(c, dy), Opt.of("dir", ff, "face", V.UP, "roll", 0.0, "scale", r * 0.97, "life", lf, "delay", delay, "grow", 0.06,
                    "anim", "play", "animSpeed", 1.0 / lf, "fadeIn", 0.0, "fadeOut", lf * 0.25, "glowAlpha", 0.0, "color", glow, "glow", core, "alpha", 0.45, "shine", 0.1));
        }
    }

    /** 잔상 줄기 판 하나 (streak_trail): from → to · 머리가 travel 초 동안 나아감 (판 삶의 0.4) · 가장 두꺼운 폭 width 칸 · 보는 사람 쪽으로 돎 */
    static void trail2d(ServerLevel level, Vec3 from, Vec3 to, double travel, double width, double[] glow, double[] core) {
        Vec3 d = V.sub(to, from);
        double L = V.len(d);
        if (L < 0.25) return;
        double life = Math.max(0.6, travel / 0.4);
        double sx = Math.max(0.15, width / (2 * 0.085 * L));
        mmorpg.fabric.Fx3d.spawn(level, "streak_trail", from, Opt.of("dir", V.scale(d, 1 / L), "faceCam", 1.0, "roll", 0.0, "scale", L, "life", life, "grow", 0.0,
                "anim", "play", "animSpeed", 1.0 / life, "fadeIn", 0.0, "fadeOut", life * 0.3, "glowAlpha", 0.0, "color", glow, "glow", core, "alpha", 0.92, "shine", 0.1,
                "stretch", new Vec3(sx, 1, 1)));
    }

    /** 맞은 자리 섬광 판 (hit_spark — 빛살 여덟 · 가운데 섬광 · 퍼지는 고리, 보는 사람을 봄): 크기 size 칸 */
    static void spark2d(ServerLevel level, Vec3 at, double size, double[] glow, double[] core) {
        double life = 0.32;
        mmorpg.fabric.Fx3d.spawn(level, "hit_spark", at, Opt.of("scale", size, "life", life, "anim", "play", "animSpeed", 1.0 / life, "faceCam", 1.0, "fadeIn", 0.0,
                "fadeOut", life * 0.15, "glowAlpha", 0.0, "color", glow, "glow", core, "alpha", 1.0, "roll", Kit.rand() * 90));
    }

    /** 착탄 섬광: 흰 심 · 진홍 겉 상자가 터졌다 꺼짐 · 강철 조각 여섯 */
    static void impact2d(ServerLevel level, Vec3 at, double size, double[] glow, double[] core) {
        mmorpg.fabric.vfx.BlockFx.burst(level, at, size * 0.8, glow, mmorpg.fabric.vfx.BlockFx.WHITE, 6);
        mmorpg.fabric.vfx.BlockFx.shards(level, at, 6, size * 1.4, size * 0.4, BLADE, 8);
        spark2d(level, at, size * 1.1, glow, core == null ? mmorpg.fabric.vfx.BlockFx.hot(glow) : core);
    }

    /**
     * X 베기 2026-11-15 2D (사용자 "참격이나 잔상 남는 연출도 … 2D 연출로"): 보는 쪽 (facing — 쓴 사람 → 과녁) 을 보는 참격 판 둘이 엇갈려 (둘째 0.06초 늦게 ·
     * 거꾸로 휩쓺) · 잔상이 천천히 거둬짐 · 맞은 자리 섬광 + 작은 3D 터짐
     */
    static void cross2d(ServerLevel level, Vec3 at, Vec3 facing, double size, double[] glow, double[] core) {
        Vec3 f = V.len(facing) < 1e-6 ? new Vec3(0, 0, 1) : V.unit(facing);
        Vec3 u = V.rightOf(V.flat(f));
        if (V.len(u) < 1e-3) u = new Vec3(1, 0, 0);
        u = V.unit(u);
        Vec3 v = V.unit(V.cross(u, f));
        double[] cc = core == null ? mmorpg.fabric.vfx.BlockFx.hot(glow) : core;
        Vec3 c = V.add(at, f, -0.25);
        for (int s = 0; s < 2; s++) {
            Vec3 bulge = V.unit(s == 0 ? V.add(u, v) : V.sub(v, u));
            slash2d(level, V.add(c, bulge, -size * 0.42), V.scale(f, -1), bulge, size * 1.05, 0.42, SLASH_LIFE, s * 0.06, glow, cc, size > 1.6, s == 1);
        }
        spark2d(level, c, size * 0.8, glow, cc);
        mmorpg.fabric.vfx.BlockFx.burst(level, at, size * 0.35, glow, mmorpg.fabric.vfx.BlockFx.WHITE, 6);
    }

    /** 폭발: 진홍 · 주황 상자 볼륨 · 흰 심 · 땅 충격파 고리 · 강철 조각 */
    static void blast2d(ServerLevel level, Vec3 at, double size) {
        blast2d(level, at, size, CRIMSON_PAL);
    }

    /** 폭발 (색 묶음 p — 2차는 직업 색) */
    static void blast2d(ServerLevel level, Vec3 at, double size, Pal p) {
        mmorpg.fabric.vfx.BlockFx.burst(level, at, size * 0.75, p.fire(), p.fireCore(), 12);
        mmorpg.fabric.vfx.BlockFx.burst(level, at, size * 0.5, p.gem(), mmorpg.fabric.vfx.BlockFx.WHITE, 9);
        Vec3 g = new Vec3(at.x, RuneKit.groundAt(level, at, at.y - 1) + 0.05, at.z);
        mmorpg.fabric.vfx.BlockFx.ring(level, g, size * 0.3, size * 1.25, 22, 0.32, p.fire(), 11);
        mmorpg.fabric.vfx.BlockFx.shards(level, at, 10, size * 1.6, size * 0.35, p.blade(), 12);
    }

    /** 불꽃: 주황 · 노란 상자가 솟으며 돌고 작아짐 */
    static void flame2d(ServerLevel level, Vec3 foot, double size, double life) {
        mmorpg.fabric.vfx.BlockFx.flame(level, foot, size * 2.0, FIRE, FIRE_CORE, Math.max(6, (int) Math.round(life * 20)));
    }

    /**
     * 칼 하나를 투영: 선이 먼저 (lines) → appear 틱에 3D 칼이 2틱 동안 커지며 나타남. 칼 (fx 수명 life 틱 — 다 쓰면 remove)
     */
    static RuneSwords.Blade project(ServerLevel level, Vec3 hilt, Vec3 dir, double len, int appear, int life, boolean lines) {
        return project(level, hilt, dir, len, appear, life, lines, null);
    }

    /**
     * 2026-11-15 직업 전용 이펙트 (L — 그림 묶음, null 이면 예전): 투영 선이 푸른 흰빛으로 번지고 (발광) · 칼은 진홍 발광 · 나타날 때 섬광 (칼 길이만큼)
     */
    static RuneSwords.Blade project(ServerLevel level, Vec3 hilt, Vec3 dir, double len, int appear, int life, boolean lines, PhantomFx.Lib L) {
        return project(level, hilt, dir, len, appear, life, lines, L, Math.min(2.6, 0.7 + len * 0.55));
    }

    /** 위와 같고 나타날 때 섬광 크기 flash (0 이하면 섬광 없음) */
    static RuneSwords.Blade project(ServerLevel level, Vec3 hilt, Vec3 dir, double len, int appear, int life, boolean lines, PhantomFx.Lib L, double flash) {
        Vec3 d = V.unit(dir);
        Vec3 face = faceOf(d);
        if (lines) trace2d(level, hilt, d, len, Math.max(0.3, Math.max(0.15, (appear + 3) / 20.0)), L == null ? -1 : L.trace());
        Vfx.Fx fx = RuneProps.fx(level, hilt, Math.max(appear + 4, life));
        RuneSwords.Blade b = RuneSwords.phantom(fx, RuneProps.PHANTOM_SWORD, hilt, d, face, len, 0.05, BLADE, GEM);
        if (L != null) b.prop.outline(L.glow());
        fx.on(Math.max(1, appear), () -> {
            b.grow(1, 2);
            if (L != null && flash > 0) PhantomFx.flash(level, L, V.lerp(b.hilt(), b.tip(), 0.5), flash);
        });
        return b;
    }

    /** 칼이 부서짐 (빛 조각 · 작은 섬광 — 칼은 지움) */
    static void shatter(ServerLevel level, RuneSwords.Blade b, double size) {
        shatter(level, b, size, null);
    }

    /** 칼이 부서짐 (L 이 있으면 직업 전용 — 작은 섬광 · 2D 칼 조각) */
    static void shatter(ServerLevel level, RuneSwords.Blade b, double size, PhantomFx.Lib L) {
        if (L != null) {
            Vec3 m = V.lerp(b.hilt(), b.tip(), 0.5);
            PhantomFx.flash(level, L, m, 0.9 * size);
            if (RuneKit.fxQuota("phantomShard", 10)) PhantomFx.shards(level, L, m, 3, 1.2 * size, 0.32, 12);
            b.remove();
            return;
        }
        Vec3 mid = V.lerp(b.hilt(), b.tip(), 0.5);
        mmorpg.fabric.vfx.BlockFx.star(level, mid, 0.6 * size, TRACE); // 2026-11-15 2D 반짝임 대신 단색 블록 별
        if (RuneKit.fxQuota("phantomShard", 10)) shards(level, mid, 3, 5.0, 0.3 * size);
        b.remove();
    }

    /** 칼 파편 (2026-11-15 2D rfx_icicle 대신 단색 블록 조각 — 강철빛 납작한 상자가 돌며 사방으로): speed = 예전 빠르기 (칸/초 → 날아가는 거리 × 0.25) */
    static void shards(ServerLevel level, Vec3 at, int n, double speed, double size) {
        mmorpg.fabric.vfx.BlockFx.shards(level, at, n, speed * 0.25, size * 1.6, BLADE, 8);
    }

    /** 진홍 폭발 (칼이 터짐 — 크기 size 칸) */
    static void blast(ServerLevel level, Jobs.Job j, Vec3 at, double size) {
        PhantomFx.Lib L = PhantomFx.lib(j);
        if (L != null) {
            // 2026-11-15 직업 전용: 작은 폭발 (불덩이 · 충격파 · 그을린 땅 한 장 그림) · 불씨 · 칼 조각
            PhantomFx.boomSmall(level, L, at, size * 1.7);
            SkillFx.play(level, "entity.generic.explode", at, 1.5 - Math.min(0.6, size * 0.12), 0.35 + size * 0.1);
            SkillFx.play(level, "block.glass.break", at, 1.3, 0.5);
            return;
        }
        blast2d(level, at, size * 1.1, pal(j));
        impact2d(level, at, size * 0.8, JobKit.glow(j), JobKit.core(j));
        SkillFx.sparks(level, at, pal(j).fireCore(), 6 + (int) (size * 3), 5);
        shards(level, at, 4 + (int) (size * 2), 6.0, 0.25 + size * 0.06);
        SkillFx.play(level, "entity.generic.explode", at, 1.5 - Math.min(0.6, size * 0.12), 0.35 + size * 0.1);
        SkillFx.play(level, "block.glass.break", at, 1.3, 0.5);
    }

    // ------------------------------------------------------------ 꽂힌 칼

    /** 이 사람이 이 적에게 꽂은 칼 수 (살아 있는 것만) */
    public static int count(ServerPlayer sp, LivingEntity e) {
        if (sp == null || e == null) return 0;
        Lodge l = LODGES.get(key(sp, e));
        if (l == null) return 0;
        long now = Tasks.now();
        int n = 0;
        for (Stuck s : l.swords) if (!s.gone && s.until > now) n++;
        return n;
    }

    /** 게이지: 마지막으로 맞힌 적에게 꽂힌 칼 수 */
    static int gauge(ServerPlayer sp) {
        LivingEntity e = LAST.get(sp.getUUID());
        if (e == null || e.isRemoved() || !e.isAlive()) return 0;
        return count(sp, e);
    }

    /**
     * 적 e 에게 칼 n 자루를 꽂음 (from 쪽에서 날아옴 — 칼이 몸에 박힌 채 따라다님, 칼마다 life 틱 뒤 빛으로 흩어짐). 가득 (max) 이면 burstDelay 틱 뒤 모두 터짐.
     * 직업 스킬 (skill) 은 max − 1 까지만 꽂음 — 터짐 (다섯째) 은 활 평타로 (CI 43619048420: 블레이드 레인 · 소드 생추어리의 물결이 다섯째를 채우면 터짐이 스킬의 다음
     * 타와 같은 틱에 겹쳐 무적 시간에 10틱 밀림 — 스킬의 마지막 피해가 늦어짐). 꽂은 수
     */
    public static int lodge(ServerPlayer sp, Jobs.Job j, LivingEntity e, int n, Vec3 from) {
        return lodge(sp, j, e, n, from, true);
    }

    /** 활 평타의 칼 (skill false — 다섯째까지 · 터짐) */
    static int lodgeShot(ServerPlayer sp, Jobs.Job j, LivingEntity e, Vec3 from) {
        return lodge(sp, j, e, 1, from, false);
    }

    private static int lodge(ServerPlayer sp, Jobs.Job j, LivingEntity e, int n, Vec3 from, boolean skill) {
        if (sp == null || e == null || e.isRemoved() || !e.isAlive() || n <= 0 || !(e.level() instanceof ServerLevel level)) return 0;
        prune();
        Lodge l = LODGES.computeIfAbsent(key(sp, e), k -> new Lodge());
        if (l.bursting) return 0;
        int max = max(j);
        int life = (int) trait(j, "life", 160);
        long now = Tasks.now();
        l.swords.removeIf(s -> s.gone || s.until <= now);
        int add = Math.min(n, (skill ? max - 1 : max) - l.swords.size());
        Vec3 body = Kit.bodyPoint(e);
        Vec3 in = V.sub(body, from);
        Vec3 base = V.len(in) > 0.1 ? V.unit(in) : Kit.facingOf(sp);
        double pad = Kit.bodyPad(e);
        double len = 1.15 + pad * 0.3; // 2026-11-15 컨셉아트: 꽂힌 칼이 몸에서 잘 보이게
        for (int i = 0; i < add; i++) {
            Vec3 d = V.unit(V.add(base, new Vec3(Kit.rand() - 0.5, Kit.rand() * 0.5 - 0.35, Kit.rand() - 0.5), 0.55));
            Vec3 tip = V.add(body, new Vec3(Kit.rand() - 0.5, Kit.rand() * 0.8 - 0.3, Kit.rand() - 0.5), 0.3 + pad * 0.4);
            double reach = len * 0.9;
            Vec3 hilt = V.add(tip, d, -reach * 0.72);
            Vec3 off = V.sub(hilt, body);
            Vec3 face = faceOf(d);
            Vfx.Fx fx = RuneProps.fx(level, hilt, life + 12);
            RuneSwords.Blade b = RuneSwords.phantom(fx, pal(j).sword(), V.add(hilt, d, -0.35), d, face, len, 0.6, pal(j).blade(), pal(j).gem());
            if (PhantomFx.lib(j) != null) b.prop.outline(PhantomFx.lib(j).glow()); // 2026-11-15 꽂힌 칼이 진홍으로 번짐 (몇 자루 꽂혔는지 보임)
            Stuck s = new Stuck(b, now + life);
            fx.on(1, () -> {
                b.grow(1, 1);
                b.pose(V.add(Kit.bodyPoint(e), off), d, face, 1);
            });
            fx.each(3, life - 1, 2, t -> {
                if (s.gone || e.isRemoved()) return;
                b.pose(V.add(Kit.bodyPoint(e), off), d, face, 2);
            });
            fx.on(life, () -> {
                if (s.gone) return;
                s.gone = true;
                b.grow(0.1, 5);
            });
            fx.on(life + 6, b::remove);
            l.swords.add(s);
        }
        LAST.put(sp.getUUID(), e);
        if (add > 0) {
            if (PhantomFx.lib(j) != null) PhantomFx.flash(level, PhantomFx.lib(j), body, 1.0 + pad * 0.5); // 2026-11-15 직업 전용 섬광
            else mmorpg.fabric.vfx.BlockFx.star(level, body, 0.8 + pad * 0.4, JobKit.glow(j)); // 2026-11-15 꽂힐 때 가벼운 번쩍 (평타마다라 폭발 대신)
            SkillFx.play(level, "item.trident.hit", body, 1.3 + Kit.rand() * 0.3, 0.45);
        }
        if (l.swords.size() >= max) {
            l.bursting = true;
            int delay = (int) trait(j, "burstDelay", 10);
            Weapon w0 = RpgCombat.weaponOf(sp.getMainHandItem());
            String life0 = RuneKit.lifeToken(sp);
            RuneKit.later(delay, () -> burst(sp, j, e, l, w0, life0));
        }
        JobTraits.sendGauge(sp, false);
        return add;
    }

    /** 가득 찬 칼이 터짐: 둘레 radius 칸의 적마다 활 평타 × burst (평타 그대로 — 치명타 · 더한 피해) · 칼 파편 · 진홍 폭발 */
    private static void burst(ServerPlayer sp, Jobs.Job j, LivingEntity e, Lodge l, Weapon w0, String life) {
        l.bursting = false;
        if (!(e.level() instanceof ServerLevel level)) {
            clear(l, null);
            return;
        }
        Vec3 at = Kit.bodyPoint(e);
        clear(l, level, PhantomFx.lib(j));
        if (e.isRemoved() || !RuneKit.sameLife(sp, life)) return;
        blast(level, j, at, 1.4 + Kit.bodyPad(e) * 0.6);
        if (PhantomFx.lib(j) == null) mmorpg.fabric.vfx.BlockFx.ring(level, V.up(e.position(), 0.05), 0.4, trait(j, "radius", 2), 16, 0.25, JobKit.glow(j), 7); // 2026-11-15 2D 고리 대신 단색 블록
        Weapon held = RpgCombat.weaponOf(sp.getMainHandItem());
        Weapon w = held != null && "bow".equals(held.type()) ? held : w0;
        if (w == null || !"bow".equals(w.type())) return;
        double ratio = trait(j, "burst", 1.0);
        for (LivingEntity x : Kit.around(sp, e.position(), trait(j, "radius", 2), 2.5)) RpgCombat.bonusHit(sp, x, w, ratio);
        JobTraits.sendGauge(sp, false);
    }

    /** 꽂힌 칼을 모두 부숨 (level 이 있으면 조각 · 섬광) */
    private static void clear(Lodge l, ServerLevel level) {
        clear(l, level, null);
    }

    private static void clear(Lodge l, ServerLevel level, PhantomFx.Lib L) {
        for (Stuck s : l.swords) {
            if (s.gone) continue;
            s.gone = true;
            if (level != null) shatter(level, s.blade, 1, L);
            else s.blade.remove();
        }
        l.swords.clear();
    }

    /** 꽂힌 칼을 모두 터뜨려 없앰 (팬텀 버스트 — 피해는 그 스킬이 넣음): 터뜨린 수 */
    public static int detonate(ServerPlayer sp, LivingEntity e) {
        int n = count(sp, e);
        Lodge l = LODGES.get(key(sp, e));
        if (l == null) return 0;
        clear(l, e.level() instanceof ServerLevel lv ? lv : null, PhantomFx.lib(JobSystem.job(sp)));
        l.bursting = false;
        return n;
    }

    /** 다 끝난 기록을 지움 (128 개가 넘을 때) */
    private static void prune() {
        if (LODGES.size() < 128) return;
        long now = Tasks.now();
        Iterator<Map.Entry<String, Lodge>> it = LODGES.entrySet().iterator();
        while (it.hasNext()) {
            Lodge l = it.next().getValue();
            l.swords.removeIf(s -> s.gone || s.until <= now);
            if (l.swords.isEmpty() && !l.bursting) it.remove();
        }
    }

    /** 시험: 이 사람의 기록을 지움 */
    public static void testReset(ServerPlayer sp) {
        String p = sp.getUUID() + ":";
        LODGES.entrySet().removeIf(en -> {
            if (!en.getKey().startsWith(p)) return false;
            clear(en.getValue(), null);
            return true;
        });
        LAST.remove(sp.getUUID());
    }

    // ------------------------------------------------------------ 활 평타 (JobTraitsMore 훅)

    /** 쏨: 활 위에 칼이 투영되어 (섬광) 화살 대신 날아감 — 평타 빛줄과 같은 틱에 닿음. 맞으면 onHit 이 꽂음, 빗나가면 끝에서 사라짐 */
    static void onFired(ServerPlayer sp, Jobs.Job j, Weapon w, LivingEntity target, Vec3 from, Vec3 dir, double dist) {
        ServerLevel level = sp.level();
        Vec3 d = V.unit(dir);
        double len = 1.45; // 2026-11-15 컨셉아트: 평타 칼 크게
        int ticks = Math.max(1, (int) Math.round(RpgCombat.arrowShot(dist).life() * 20));
        Vec3 start = V.add(from, d, 0.2);
        Vfx.Fx fx = RuneProps.fx(level, start, ticks + 14);
        Vec3 face = faceOf(d);
        RuneSwords.Blade b = RuneSwords.phantom(fx, pal(j).sword(), start, d, face, len, 0.35, pal(j).blade(), pal(j).gem());
        PhantomFx.Lib L = PhantomFx.lib(j);
        if (L != null) b.prop.outline(L.glow());
        double reach = b.reach() / 0.35;
        Vec3 end = V.add(from, d, Math.max(0.6, dist - reach * (target != null ? 0.75 : 1.0)));
        fx.on(1, () -> {
            b.grow(1, 1);
            b.pose(end, d, face, Math.max(1, ticks - 1));
        });
        if (target != null) fx.on(ticks, b::remove);
        else {
            fx.on(ticks + 8, () -> b.grow(0.1, 4));
            fx.on(ticks + 13, b::remove);
        }
        // 2026-11-15 1인칭: 눈앞 0.5칸 별은 평타마다 화면을 덮음 → 1.6칸 앞 · 작게
        if (RuneKit.fxQuota("phantomShotGlint", 6)) {
            if (L != null) PhantomFx.flash(level, L, V.add(from, d, 1.6), 0.6);
            else mmorpg.fabric.vfx.BlockFx.star(level, V.add(from, d, 1.6), 0.45, TRACE);
        }
        // 2026-11-15 날아가는 칼 뒤 2D 잔상 줄기 (평타가 잦아 가늘게 · 몫 8)
        if (RuneKit.fxQuota("phantomShotTrail", 8)) trail2d(level, V.add(from, d, 0.6), V.add(end, d, reach * 0.5), ticks / 20.0, 0.24, accent(j),
                mmorpg.fabric.vfx.BlockFx.hot(accent(j)));
        SkillFx.play(level, "item.trident.throw", from, 1.5 + Kit.rand() * 0.2, 0.3);
    }

    /** 활 평타가 맞음: 칼이 꽂힘 (+1) */
    static void onHit(ServerPlayer sp, Jobs.Job j, LivingEntity victim) {
        // 2026-11-15 아스널 킹: 평타 칼 + 등 뒤 문의 보물 — 한 발에 perShot (2) 자루
        lodge(sp, j, victim, Math.max(1, (int) trait(j, "perShot", 1)), sp.getEyePosition(), false);
    }
}
