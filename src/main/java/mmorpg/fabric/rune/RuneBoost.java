package mmorpg.fabric.rune;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.48.2 초월 룬 2D 강화 (사용자 "초월 31종 먼저" → 입체 · 블록 · 2D 세 판을 견준 뒤 "하나로 통일하고 싶은데 걍 2d로 하고 2d 이펙트를 더 강화하는 느낌으로" →
 * "2D 만 강화"): 초월 룬의 2D 연출은 그대로 두고 그 위에 2D 그림만 더함 (입체 모델 · 블록 표시 없음).
 * <ul>
 *   <li>후광 (aura · rfx2a): 핵심 그림 (검 · 달 · 창 · 눈 · 블랙홀 · 날개) 마다 같은 그림의 빛깔 겹을 1.35배 크기 · 옅게 한 장 더 — 같은 길로 움직임</li>
 *   <li>터짐 (impact): 큰 번쩍임 · 고리 둘 (빠르게 크게 → 3틱 뒤 흰 고리) · 충격파 셋 · 땅에 퍼지는 빛살 열여섯 · 불티 · 위로 튀는 빛 알갱이 · 더 센 흔들림</li>
 *   <li>부드러운 터짐 (bloom): 버프 · 회복 룬 — 흔들림 · 충격파 없이 번쩍임 · 고리 · 빛 알갱이만</li>
 * </ul>
 */
final class RuneBoost {
    private RuneBoost() {}

    /** 후광: rfx 그림 name 의 빛깔 겹 (첫 칸 + o 의 cell) 을 k 배 크기 · alpha 로 (o 의 자리 · 방향 · 움직임 · 삶 그대로) */
    static void aura(ServerLevel level, String name, Vec3 at, Opt o, double[] glow, double k, double alpha) {
        int base = RuneKit.RFX.getOrDefault(name, new int[] {0, 1})[0];
        rfx(level, name, at, o, glow, base, k, alpha);
    }

    /** 후광 (빛깔 칸을 직접 — 칸 묶음이 따로인 그림: 창 · 번개) */
    static void auraCell(ServerLevel level, String name, Vec3 at, Opt o, double[] glow, int glowCell, double k, double alpha) {
        rfx(level, name, at, o, glow, glowCell, k, alpha);
    }

    private static void rfx(ServerLevel level, String name, Vec3 at, Opt o, double[] glow, int cell0, double k, double alpha) {
        RuneKit.rfx(level, name, at, o.with("size", o.num("size", 1) * k, "color", glow, "cell", (double) (cell0 + (int) o.num("cell", 0)), "alpha",
                alpha * o.num("alpha", 1), "solo", null));
    }

    /** rfx2 (빛깔 + 심) 에 후광을 더함 */
    static void rfx2a(ServerLevel level, String name, Vec3 at, Opt o, double[] glow, double[] core) {
        aura(level, name, at, o, glow, 1.35, 0.42);
        RuneKit.rfx2(level, name, at, o, glow, core);
    }

    /** 터짐 (반지름 r — 룬의 영역 크기): 번쩍임 · 고리 둘 · 충격파 셋 · 땅 빛살 열여섯 · 불티 · 빛 알갱이 · 흔들림 */
    static void impact(ServerLevel level, Vec3 at, double[] glow, double[] core, double r) {
        Vec3 mid = V.up(at, 1.0);
        RuneKit.skyFlare(level, V.up(at, 1.3), r * 1.05, glow, core, Opt.of("life", 0.5, "grow", 0.8));
        RuneKit.novaRing(level, at, r * 0.3, r * 0.95, glow, core, Opt.of("life", 0.45));
        RuneKit.later(3, () -> RuneKit.novaRing(level, at, r * 0.2, r * 0.6, core, RuneKit.WHITE, Opt.of("life", 0.7)));
        for (int i = 0; i < 3; i++) {
            final int ii = i;
            RuneKit.later(i * 2, () -> SkillFx.shock(level, at, ii % 2 == 0 ? glow : core, r * (0.6 + 0.35 * ii), 0.5 + 0.1 * ii));
        }
        for (int i = 0; i < 16; i++) {
            Vec3 d = RuneKit.turn(new Vec3(1, 0, 0), i * 22.5 + Kit.rand() * 8);
            SkillFx.fx(level, "rpg:light_cut", V.add(V.up(at, 0.1), d, r * 0.55), Opt.of("dir", d, "size", r * 0.5, "thick", 0.35, "life", 0.6, "color",
                    i % 2 == 0 ? glow : core, "alpha", 0.95));
        }
        SkillFx.sparks(level, mid, core, 24, 10);
        motes(level, at, glow, r, 10);
        RuneKit.shakeAround(level, at, 24, 0.6, 0.5);
    }

    /** 부드러운 터짐 (버프 · 회복): 번쩍임 · 고리 둘 · 빛 알갱이 — 흔들림 · 충격파 없음 */
    static void bloom(ServerLevel level, Vec3 at, double[] glow, double[] core, double r) {
        RuneKit.skyFlare(level, V.up(at, 1.3), r * 0.8, glow, core, Opt.of("life", 0.45, "grow", 0.6));
        RuneKit.novaRing(level, at, r * 0.25, r * 0.8, glow, core, Opt.of("life", 0.5));
        RuneKit.later(3, () -> RuneKit.novaRing(level, at, r * 0.15, r * 0.5, core, RuneKit.WHITE, Opt.of("life", 0.7)));
        motes(level, at, glow, r, 14);
    }

    /** 위로 튀는 빛 알갱이 n 개 (반지름 r 안 땅에서) */
    static void motes(ServerLevel level, Vec3 at, double[] color, double r, int n) {
        for (int i = 0; i < n; i++) {
            double a = Kit.rand() * Math.PI * 2;
            double rr = Math.sqrt(Kit.rand()) * r * 0.7;
            Vec3 p = new Vec3(at.x + Math.cos(a) * rr, at.y + 0.2, at.z + Math.sin(a) * rr);
            SkillFx.fx(level, "rpg:arrow_spark", p, Opt.of("dir", V.UP, "speed", 3.5 + Kit.rand() * 4, "size", 0.28, "grow", 1.0, "life", 0.9, "color", color,
                    "alpha", 1.0));
        }
    }

    // ------------------------------------------------------------ 0.50.0 룬 맞은 자리 · 아래 등급 그림 후광 (사용자 "다른 룬이나 스킬들도 이펙트나 모션 추가로 해야 하는거 있으면 해둬")

    /**
     * 계열 → 맞은 자리 그림 (rfx_jhits 의 줄 — 0.50.0 직업 맞은 자리 그림을 계열 색으로: 0 베인 줄 · 1 발톱 세 줄 · 2 룬 균열 · 3 별 터짐 · 4 소용돌이 · 5 X ·
     * 6 원뿔 충격 · 7 빠져나가는 혼 · 8 번개 갈래 · 9 빛 꽃 · 10 육각 판). 피해가 없는 계열 (신체 강화 · 세계수 · 심연) 은 없음
     */
    private static final Map<String, Integer> MARK_ROW = Map.ofEntries(Map.entry("str", 1), Map.entry("dex", 0), Map.entry("vit", 10), Map.entry("int", 3),
            Map.entry("axe", 1), Map.entry("bow", 6), Map.entry("holy", 9), Map.entry("storm", 8), Map.entry("assassin", 5), Map.entry("blade", 0),
            Map.entry("spellblade", 2), Map.entry("element", 0), Map.entry("spirit", 4), Map.entry("gravity", 3), Map.entry("strike", 0), Map.entry("reaper", 7),
            Map.entry("tide", 4), Map.entry("golem", 10), Map.entry("monk", 6), Map.entry("hunter", 1), Map.entry("volcano", 3), Map.entry("gladiator", 10),
            Map.entry("arcana", 3), Map.entry("dipper", 3), Map.entry("skyeye", 8), Map.entry("endless", 0), Map.entry("terra", 10), Map.entry("glacier", 10));
    /** rfx_jhits 한 줄의 장 수 (JobFx.HITS) · rfx_jburst2 장 수 (JobFx.BURST) */
    private static final int MARK_FRAMES = 6;
    private static final int BURST_FRAMES = 8;
    /** 한 적에 맞은 자리를 다시 그리기까지 틱 (장판 · 여러 번 치는 룬이 맞은 자리로 덮이지 않게) */
    static final int MARK_GAP = 10;
    private static final Map<Integer, Long> MARKED = new HashMap<>();

    /** 등급의 크기 몫 (일반 0.8 ~ 초월 1.15) */
    static double tierK(String grade) {
        return switch (grade) {
            case "common" -> 0.8;
            case "rare" -> 0.88;
            case "noble" -> 0.96;
            case "hero" -> 1.05;
            case "transcend" -> 1.15;
            default -> 0.9;
        };
    }

    /**
     * 룬 피해가 들어간 적 (RuneKit.runeHit): 그 계열의 맞은 자리 그림 + 짧은 번쩍 + 불티 — 꼬리표 (Tasks.tag "계열:등급") 가 있을 때만, 한 적에 MARK_GAP 틱마다 ·
     * 한 틱에 8개까지. 크기는 몸 크기 × 등급 몫
     */
    static void mark(LivingEntity e) {
        String tag = Tasks.tag();
        if (tag == null || e == null || e.isRemoved() || !(e.level() instanceof ServerLevel level)) return;
        int c = tag.indexOf(':');
        String fam = c < 0 ? tag : tag.substring(0, c);
        Integer row = MARK_ROW.get(fam);
        if (row == null) return;
        long now = Tasks.now();
        Long last = MARKED.get(e.getId());
        if (last != null && now - last < MARK_GAP) return;
        if (!RuneKit.fxQuota("runeMark", 8)) return;
        if (MARKED.size() > 256) MARKED.values().removeIf(t -> now - t > MARK_GAP);
        MARKED.put(e.getId(), now);
        double k = tierK(c < 0 ? "" : tag.substring(c + 1));
        double[][] col = RuneKit.colorOf(fam);
        double size = (0.75 + Kit.bodyPad(e) * 0.6) * k * 0.9;
        Vec3 b = Kit.bodyPoint(e);
        double tilt = row == 7 ? 0 : Kit.rand() * 40 - 20; // 혼은 위로 솟으므로 돌리지 않음
        two(level, "jhits", MARK_FRAMES, row * 12, b, 0.32, Opt.of("size", size, "spin", tilt, "fade", 0.25), col[0], col[1]);
        double a = Math.min(1, 0.55 + k * 0.3);
        two(level, "jburst2", BURST_FRAMES, 0, b, 0.2, Opt.of("size", size * 0.55, "spin", Kit.rand() * 360, "alpha", 0.75 * a, "coreAlpha", 0.9 * a, "fade", 0.3),
                col[0], col[1]);
        SkillFx.sparks(level, b, col[1], (int) Math.round(3 + k * 2), 3.5 + k);
    }

    /** 빛 칸 base · 심지 칸 base + frames 를 같은 자리에 차례로 넘김 (JobFx.two 와 같음) */
    private static void two(ServerLevel level, String name, int frames, int base, Vec3 at, double life, Opt o, double[] glow, double[] core) {
        double fps = frames / Math.max(0.05, life);
        Opt b = o.with("life", life, "frames", (double) frames, "fps", fps, "fade", o.num("fade", 0.2));
        RuneKit.rfx(level, name, at, b.with("color", glow, "cell", (double) base));
        RuneKit.rfx(level, name, at, b.with("color", core, "cell", (double) (base + frames), "alpha", o.num("coreAlpha", o.num("alpha", 1))));
    }

    /** 후광을 얹는 물체 그림 (검 · 화살 · 고드름 · 달 · 도끼 · 망치 · 표창 · 깃털 · 창 · 번개 구슬 · 얼음 결정) — 불티 · 문양 · 연기는 빼고 */
    private static final Set<String> AURA_OBJ = Set.of("sword", "arrow", "icicle", "moon", "axe", "hammer", "shuriken", "feather", "spear", "plasma", "crystal");

    /** 지금 꼬리표의 아래 등급 후광 진하기 (일반 0.26 ~ 영웅 0.38, 초월 · 꼬리표 없음 = 0 — 초월은 룬마다 rfx2a 로) */
    static double lowAura() {
        String tag = Tasks.tag();
        if (tag == null) return 0;
        return switch (tag.substring(tag.indexOf(':') + 1)) {
            case "common" -> 0.26;
            case "rare" -> 0.3;
            case "noble" -> 0.34;
            case "hero" -> 0.38;
            default -> 0;
        };
    }

    /** 이 그림 (rfx 이름) 에 아래 등급 후광을 얹을지 — 진하기 (0 = 아님) */
    static double lowAura(String name) {
        return AURA_OBJ.contains(name) ? lowAura() : 0;
    }
}
