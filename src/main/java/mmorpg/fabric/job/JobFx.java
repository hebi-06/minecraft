package mmorpg.fabric.job;

import mmorpg.core.Jobs;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0.27.0 직업 스킬 연출 (메이플식 2D 그림 — tools/job_fx_art.py 의 rpg:rfx_j*, 룬 연출의 바닥 문양 · 룬 글자 · 영역 문양 대신):
 * 그림마다 빛 (넓고 흐린 겉 — 직업의 짙은 빛) + 심지 (가늘고 또렷한 속 — 옅은 빛 · 흰색) 두 장을 같은 자리에 겹쳐 frames 장을 수명 동안 차례로 넘김.
 * <ul>
 * <li>slash — 눈앞에서 휘두르는 큰 초승달 베기 (카메라를 봄 — 1인칭 화면 가운데 · 3인칭은 몸 앞)</li>
 * <li>hit — 맞은 적마다 번쩍 · 베인 줄 · 고리 · 조각</li>
 * <li>ring — 땅 (또는 허리 높이) 에 눕힌 한 바퀴 베기</li>
 * <li>dash · line — 돌진 줄기 · 일섬 (방향으로 누움)</li>
 * <li>burst — 터짐</li>
 * <li>aura — 몸에서 솟는 빛 (시전자 1인칭에서는 숨김 — 몸 가운데의 그림은 카메라가 그림 속이라 픽셀이 화면을 덮는 큰 네모가 됨)</li>
 * </ul>
 * 큰 그림은 1인칭 눈 가까이 (반 크기 안 · 앞뒤 1칸 안) 에 두지 않음 — 카메라 바로 옆의 빌보드는 몇 픽셀이 화면 가득 늘어남.
 * <p>
 * 0.47.0 (사용자 "효과 물체를 입체 모델로" · "평타 · 스킬 이펙트를 더 화려하게"): 물체 그림 (비수 · 창 · 마탄 · 화살 · 방패 · 대망치 · 구체) 은 입체 모델
 * (JobFx3d — Fx3d) 이 몸을 맡고 2D 그림은 옅은 빛으로 남음 ("flat" 선택값이면 2D 만 — 태세 동안 8틱마다 다시 그리는 그림). 타격 · 베기 · 터짐 · 고리 · 땅 찍기 ·
 * 일섬은 빛 한 겹 (잔상 · 번짐) 과 불티를 더함.
 */
final class JobFx {
    private JobFx() {}

    static final double[] WHITE = RuneKit.WHITE;
    /**
     * 0.50.0 (사용자 "3d로 되어있는 애들 2d로 다시 바꾸자 대신 엄청 고퀄로 다듬어야 함"): 효과 물체 (마탄 · 화살 · 구체 · 비수 · 창 · 망치 · 방패 …) 는 모두 2D
     * — 파티클 그림 (이 파일의 함수 — v2 그림) 과, 0.47.0 입체 모델 자리에는 같은 크기의 2D 그림 판 (JobFx3d.CARDS — 사신의 니플헤임의 문 · 망자의 손만 입체)
     */
    static final boolean FLAT = true;
    static final int SLASH = 8;
    static final int HIT = 6;
    static final int RING = 8;
    static final int DASH = 6;
    static final int LINE = 6;
    static final int BURST = 8;
    static final int AURA = 8;
    static final int SPEED = 4;
    /** 0.28.0 전사 · 매지션 · 궁수 그림 (tools/job_fx_art_more.py) 의 장 수 */
    static final int SMASH = 8;
    static final int BREAK = 6;
    static final int WHIRL = 8;
    static final int TWISTER = 8;
    static final int COMET = 4;
    static final int BOLT = 4;
    static final int SHATTER = 8;
    static final int ORB = 6;
    static final int BEAM = 6;
    static final int BOOK = 8;
    static final int PAGE = 4;
    static final int ARROW = 4;
    static final int BOW = 6;
    static final int TARGET = 6;
    static final int WIND = 8;
    /** 0.29.0 암살자 · 창술사 · 사신 그림 (tools/job_fx_art_three.py) 의 장 수 */
    static final int PETAL = 8;
    static final int DAGGER = 4;
    static final int SMOKE = 8;
    static final int CROSS = 6;
    static final int STAB = 6;
    static final int SPIN = 8;
    static final int SPEAR = 4;
    static final int SOUL = 6;
    static final int REAPER = 8;
    static final int GATE = 8;
    static final int HAND = 6;
    /** 0.30.0 성기사 · 사제 · 수호기사 그림 (tools/job_fx_art_four.py) 의 장 수 */
    static final int THUNDER = 6;
    static final int VOLT = 8;
    static final int MAUL = 4;
    static final int WING = 4;
    static final int STUN = 8;
    static final int JUDGE = 4;
    static final int HEAL = 6;
    static final int NOTE = 4;
    static final int STIGMA = 4;
    static final int BELL = 8;
    static final int CHAIN = 4;
    static final int BUCKLER = 4;
    static final int AEGIS = 6;
    /**
     * 0.50.0 v2 그림 (tools/job_fx_art_v2.py — 사용자 "3d로 되어있는 애들 2d로 다시 바꾸자 대신 엄청 고퀄로 다듬어야 함": 입체로 바꿨던 물체를 더 곱게 다시 그린
     * 2D) 의 장 수. 크기 (size) 는 v1 과 같은 뜻이 되게 각 함수에서 몸 범위의 비로 맞춤
     */
    static final int BOLT2 = 4;
    static final int ARROW2 = 4;
    static final int SPEAR2 = 4;
    static final int DAGGER2 = 8;
    static final int MAUL2 = 4;
    static final int BELL2 = 8;
    static final int BUCKLER2 = 8;
    static final int AEGIS2 = 6;
    static final int STAR = 8;
    static final int TOME = 12;
    static final int BOW2 = 8;
    static final int HITS = 6;
    /** rfx_jhits 의 직업 줄 차례 (줄 i: 빛 칸 i × 12 + 0 ~ 5 · 심지 i × 12 + 6 ~ 11) */
    private static final java.util.List<String> HIT_ROWS = java.util.List.of("swordsman", "warrior", "spellblade", "mage", "archer", "assassin", "lancer",
            "reaper", "paladin", "priest", "guardian");
    /** 그림 칸의 고리 반지름 = 사각형 반 크기 × 0.88 (tools/job_fx_art.py ring_frame R = 칸의 0.44) */
    private static final double RING_R = 0.88;

    // ------------------------------------------------------------ 색

    /** 겉빛: 직업 두 빛 중 더 짙은 쪽 (검사 하늘색 · 마검사 보라) */
    static double[] outer(Jobs.Job j) {
        double[] a = JobKit.glow(j);
        double[] b = JobKit.core(j);
        return sat(a) >= sat(b) ? a : b;
    }

    /** 속빛: 다른 쪽을 흰색 쪽으로 (검사 흰빛 · 마검사 옅은 청록) */
    static double[] inner(Jobs.Job j) {
        double[] a = JobKit.glow(j);
        double[] b = JobKit.core(j);
        double[] o = sat(a) >= sat(b) ? b : a;
        return new double[] {o[0] + (1 - o[0]) * 0.55, o[1] + (1 - o[1]) * 0.55, o[2] + (1 - o[2]) * 0.55};
    }

    private static double sat(double[] c) {
        double max = Math.max(c[0], Math.max(c[1], c[2]));
        double min = Math.min(c[0], Math.min(c[1], c[2]));
        return max <= 0 ? 0 : (max - min) / max;
    }

    // ------------------------------------------------------------ 보는 사람

    /** 시전자가 1인칭이면 숨김 (몸 둘레 · 발밑 · 머리 위 그림) */
    static Opt hide1p(ServerPlayer sp) {
        return Opt.of("view", 1.0, "owner", (double) sp.getId());
    }

    /** 시전자의 1인칭에서만 (눈앞에 따로 그리는 그림) */
    static Opt only1p(ServerPlayer sp) {
        return Opt.of("view", 2.0, "owner", (double) sp.getId());
    }

    // ------------------------------------------------------------ 그리기

    /** 그림 한 벌 (빛 칸 0 · 심지 칸 frames): life 초 동안 frames 장. o: dir · size · spin · spinRate · speed · grow · fade · alpha · coreAlpha · vel · view · owner */
    static void two(ServerLevel level, String name, int frames, Vec3 at, double life, Opt o, double[] glow, double[] core) {
        two(level, name, frames, 0, at, life, o, glow, core);
    }

    /** 같은 것 — 빛 칸 base · 심지 칸 base + frames (rfx_jring 의 뒤집은 장면은 base = 2 × frames) */
    static void two(ServerLevel level, String name, int frames, int base, Vec3 at, double life, Opt o, double[] glow, double[] core) {
        double fps = frames / Math.max(0.05, life);
        Opt b = o.with("life", life, "frames", (double) frames, "fps", fps, "fade", o.num("fade", 0.2));
        RuneKit.rfx(level, name, at, b.with("color", glow, "cell", (double) base));
        RuneKit.rfx(level, name, at, b.with("color", core, "cell", (double) (base + frames), "alpha", o.num("coreAlpha", o.num("alpha", 1))));
    }

    /** 화면 위쪽 (보는 쪽에 수직 — 고개를 들고 숙여도 화면에서 위) */
    static Vec3 screenUp(Vec3 look) {
        Vec3 up = V.sub(V.UP, V.scale(look, V.dot(V.UP, look)));
        double l = V.len(up);
        return l < 1e-4 ? V.UP : V.scale(up, 1 / l);
    }

    /**
     * 눈앞 큰 베기 (rfx_jslash — 카메라를 봄): 눈에서 보는 쪽 ahead 칸 · 화면 아래 down 칸, size = 가로 반 칸 (세로 = 반), tilt = 화면에서 기운 각
     * (도 — 0 = 왼쪽에서 아래를 지나 오른쪽으로, 180 = 뒤집혀 오른쪽 위에서 왼쪽으로). 1인칭 화면 가운데 · 3인칭은 몸 앞
     */
    static void slash(ServerPlayer sp, Jobs.Job j, double ahead, double down, double size, double tilt, double life) {
        Vec3 look = sp.getViewVector(1F);
        Vec3 at = V.add(V.add(sp.getEyePosition(), look, ahead), screenUp(look), -down);
        Opt own = only1p(sp);
        two(sp.level(), "jslash2", SLASH, at, life, own.with("size", size, "spin", tilt, "vel", RuneKit.bodyVel(sp), "fade", 0.15), outer(j), inner(j));
        // 0.47.0 더 화려하게: 조금 크고 조금 기운 잔상 한 겹 (옅게 — 두 겹의 칼빛)
        two(sp.level(), "jslash2", SLASH, V.add(at, look, 0.15), life * 0.9, own.with("size", size * 1.14, "spin", tilt + 7, "vel", RuneKit.bodyVel(sp), "fade", 0.2,
                "alpha", 0.42, "coreAlpha", 0.3), outer(j), inner(j));
        // 0.50.0 3인칭 · 다른 사람: 카메라를 보는 그림 대신 세상에 놓인 참격 판 (휘두른 면 — 불룩한 날이 보는 쪽 · 가로 휘두름은 누운 판)
        slashCard(sp.level(), at, look, tilt, size * CARD, life, RuneKit.bodyVel(sp), j, hide1p(sp));
    }

    /** 참격 판의 크기 (파티클 size 의 몫 — 파티클은 1인칭 눈앞 화면을 채우게 커서, 세상에 놓으면 몸보다 너무 큼 · 룬 ARC_CARD 와 같은 뜻) */
    static final double CARD = 0.75;

    /**
     * 0.50.0 직업 참격 판 (사용자 "참격 방향 잡혀있는지 확인하고" — fxmodel slash_job: rfx_jslash 여덟 장을 붙인 판, 불룩한 날 = 판의 +Y): 불룩한 날이 dir 쪽,
     * 판의 면 = 화면에서 기운 각 tilt (JobFx.slash 와 같은 뜻 — 0 = 가로 휘두름) 를 dir 둘레로 옮긴 것 → 가로 휘두름은 누운 판 (보는 사람마다 dir 둘레로 돌아
     * 넓은 면을 보임 — RuneKit.faceCamIfFlat), 세로 휘두름은 선 판. vel = 같이 움직임 (칸/초). view = 보는 사람 걸러내기 (hide1p · only1p)
     */
    static void slashCard(ServerLevel level, Vec3 at, Vec3 dir, double tilt, double size, double life, Vec3 vel, Jobs.Job j, Opt view) {
        Vec3 d = V.len(dir) > 1e-6 ? V.unit(dir) : V.UP;
        double lf = Math.max(0.05, life);
        double roll = -tilt;
        Opt card = Opt.of("dir", d, "face", V.UP, "roll", roll, "scale", size, "life", lf, "grow", 0.15, "vel", vel, "anim", "play", "animSpeed", 1.0 / lf,
                "fadeIn", 0.0, "fadeOut", lf * 0.3, "glowAlpha", 0.0, "color", outer(j), "glow", inner(j), "alpha", 0.95, "shine", 0.15, "view", view.num("view", 0),
                "owner", view.num("owner", 0));
        mmorpg.fabric.Fx3d.spawn(level, "slash_job", at, RuneKit.faceCamIfFlat(card, d, V.UP, roll));
    }

    /**
     * 0.50.0 날아가는 검기 (연참 검기 · 발할라 마지막 일섬 · 룬 베기 검기): 시전자 1인칭에는 지금 그림 (카메라를 보는 rfx_jslash — dir · speed 로 날아감),
     * 3인칭 · 다른 사람에게는 날아가는 참격 판 (불룩한 날이 날아가는 쪽 — 룬 0.48.2 와 같음). o = 파티클 값 (size · spin · grow · alpha · fade …)
     */
    static void wave(ServerPlayer sp, Jobs.Job j, Vec3 at, Vec3 dir, double speed, double life, Opt o) {
        ServerLevel level = sp.level();
        Vec3 d = V.len(dir) > 1e-6 ? V.unit(dir) : V.UP;
        two(level, "jslash2", SLASH, at, life, o.with("dir", d, "speed", speed, "view", 2.0, "owner", (double) sp.getId()), outer(j), inner(j));
        slashCard(level, at, d, o.num("spin", 0), o.num("size", 1) * CARD * 1.1, life, V.scale(d, speed), j, hide1p(sp));
    }

    /**
     * 0.50.0 직업 고유 타격음 (사용자 "직업 고유 연출"): 검사 맑은 칼 소리 · 전사 둔탁한 쿵 · 마검사 울림 · 매지션 맑은 팅 · 궁수 꽂힘 · 암살자 짧은 샥 ·
     * 창술사 꿰뚫음 · 사신 낮은 혼 소리 · 성기사 천둥 · 사제 종 · 수호기사 금속 — {소리, 높이, 크기}
     */
    private static final java.util.Map<String, Object[]> HIT_SOUND = java.util.Map.ofEntries(
            java.util.Map.entry("swordsman", new Object[] {"block.amethyst_block.hit", 1.9, 0.5}),
            java.util.Map.entry("warrior", new Object[] {"mace.smash_ground", 0.85, 0.35}),
            java.util.Map.entry("spellblade", new Object[] {"block.amethyst_block.resonate", 1.3, 0.45}),
            java.util.Map.entry("mage", new Object[] {"block.amethyst_block.chime", 1.7, 0.5}),
            java.util.Map.entry("archer", new Object[] {"entity.arrow.hit", 1.25, 0.45}),
            java.util.Map.entry("assassin", new Object[] {"entity.player.attack.sweep", 1.8, 0.35}),
            java.util.Map.entry("lancer", new Object[] {"item.trident.hit", 1.15, 0.45}),
            java.util.Map.entry("reaper", new Object[] {"particle.soul_escape", 0.8, 0.5}),
            java.util.Map.entry("paladin", new Object[] {"item.trident.thunder", 1.8, 0.12}),
            java.util.Map.entry("priest", new Object[] {"block.bell.hit", 1.9, 0.3}),
            java.util.Map.entry("guardian", new Object[] {"item.shield.block", 1.35, 0.4}));

    /** 맞은 자리 (rfx_jhit): 몸 가운데 · 몸 크기에 맞춤 · 아무렇게나 기욺 */
    static void hit(LivingEntity e, Jobs.Job j, double scale) {
        if (!(e.level() instanceof ServerLevel level)) return;
        Object[] snd = j == null ? null : HIT_SOUND.get(j.id());
        if (snd != null && RuneKit.fxQuota("jobHitSound", 3)) mmorpg.fabric.skill.SkillFx.play(level, (String) snd[0], e.position(), (double) snd[1], (double) snd[2]);
        double size = (0.75 + Kit.bodyPad(e) * 0.6) * scale;
        Vec3 b = Kit.bodyPoint(e);
        // 0.50.0 직업마다 다른 맞은 자리 (rfx_jhits — 사용자 "직업 고유 연출": 검사 십자 섬광 · 전사 금 간 충격 · 마검사 룬 고리 · 매지션 별빛 · 궁수 꽂힌 깃 ·
        // 암살자 꽃잎 · 창술사 꿰뚫은 빛 · 사신 혼 · 성기사 번개 · 사제 빛 십자 · 수호기사 방패 문장 — 처음 두 장 번쩍 → 흩어짐)
        int row = j == null ? -1 : HIT_ROWS.indexOf(j.id());
        // 사신 (혼이 위로 솟음) 은 돌리지 않음, 나머지는 조금씩 기울임
        double tilt = "reaper".equals(j.id()) ? 0 : Kit.rand() * 40 - 20;
        if (row >= 0) two(level, "jhits", HITS, row * 12, b, 0.32, Opt.of("size", size * 0.9, "spin", tilt, "fade", 0.25), outer(j), inner(j));
        else two(level, "jhit", HIT, b, 0.3, Opt.of("size", size, "spin", Kit.rand() * 360, "fade", 0.2), outer(j), inner(j));
        // 0.47.0 더 화려하게: 맞은 자리의 짧은 번쩍 (터짐 작은 장) · 튀는 불티 (직업 빛)
        two(level, "jburst2", BURST, b, 0.2, Opt.of("size", size * 0.55, "spin", Kit.rand() * 360, "alpha", 0.75, "coreAlpha", 0.9, "fade", 0.3), outer(j), inner(j));
        mmorpg.fabric.skill.SkillFx.sparks(level, b, inner(j), 5, 3.5 + scale);
    }

    /**
     * 땅 (또는 그 높이) 에 눕힌 한 바퀴 베기 (rfx_jring — 면은 늘 위를 봄): 반지름 radius 칸, reverse = 반시계 (좌우를 뒤집은 장면 — 면을 아래로 눕히면 위에서
     * 보이지 않음). o: alpha · coreAlpha · fade · view · owner · grow · vel
     */
    static void ring(ServerLevel level, Vec3 center, double radius, Jobs.Job j, boolean reverse, double life, Opt o) {
        two(level, "jring2", RING, reverse ? 2 * RING : 0, center, life, o.with("dir", V.UP, "size", radius / RING_R, "spin", o.num("spin", 0)), outer(j), inner(j));
        // 0.47.0 더 화려하게: 조금 바깥의 옅은 잔상 고리 (거꾸로 — 겹친 두 바퀴)
        double a = o.num("alpha", 1);
        two(level, "jring2", RING, reverse ? 0 : 2 * RING, V.up(center, 0.02), life * 0.9, o.with("dir", V.UP, "size", radius * 1.07 / RING_R, "spin", o.num("spin", 0) + 40,
                "alpha", a * 0.38, "coreAlpha", a * 0.28), outer(j), inner(j));
    }

    /** 세운 둥근 베기 (rfx_jring 을 normal 쪽을 보게 — 보는 사람 쪽을 보게 두면 눈앞 고리 · 적 둘레 고리) */
    static void ringFacing(ServerLevel level, Vec3 center, double radius, Jobs.Job j, Vec3 normal, double life, Opt o) {
        two(level, "jring2", RING, center, life, o.with("dir", normal, "size", radius / RING_R, "spin", o.num("spin", 0)), outer(j), inner(j));
    }

    /** 1인칭 눈앞 고리 (시전자의 1인칭에만 — 태세 · 각성 신호): 눈앞 ahead 칸에 반지름 radius 칸, 화면을 보고 시계 방향으로 한 바퀴 */
    static void eyeRing(ServerPlayer sp, Jobs.Job j, double ahead, double radius, double life) {
        eyeRing(sp, j, ahead, radius, life, RuneKit.bodyVel(sp));
    }

    /** 같은 것 — vel (칸/초) 로 같이 움직임 (0.29.0 순간 이동 걸음으로 나아가는 망령 걸음: 걸음마다 그리면 다음 틱에 카메라 뒤로 지나감) */
    static void eyeRing(ServerPlayer sp, Jobs.Job j, double ahead, double radius, double life, Vec3 vel) {
        Vec3 look = sp.getViewVector(1F);
        ringFacing(sp.level(), V.add(sp.getEyePosition(), look, ahead), radius, j, V.scale(look, -1), life, only1p(sp).with("vel", vel));
    }

    /** 빠른 줄 (rfx_jspeed — 시전자의 1인칭에만, 화면 가장자리): 눈앞 1.4칸, life 초 동안 깜빡이며 흐름 (순간 이동 뒤에 부름 — 자리를 따라가지 않음) */
    static void speed(ServerPlayer sp, Jobs.Job j, double life, double alpha) {
        speed(sp, j, life, alpha, V.ZERO);
    }

    /** 같은 것 — vel (칸/초) 로 같이 움직임 (0.29.0 망령 걸음 — 걸음 동안 눈앞에 머무름) */
    static void speed(ServerPlayer sp, Jobs.Job j, double life, double alpha, Vec3 vel) {
        Vec3 look = sp.getViewVector(1F);
        // 반 크기 1.35 (1.4칸 앞): 줄 안쪽 끝이 화면 가운데에서 약 30° — 위아래 시야 (반 35°) 안에도 줄이 보임
        Opt o = only1p(sp).with("size", 1.35, "loop", 1.0, "alpha", alpha, "fade", 0.4, "spin", Kit.rand() * 360, "vel", vel);
        Opt base = o.with("life", life, "frames", (double) SPEED, "fps", 24.0);
        Vec3 at = V.add(sp.getEyePosition(), look, 1.4);
        RuneKit.rfx(sp.level(), "jspeed", at, base.with("color", outer(j), "cell", 0.0));
        RuneKit.rfx(sp.level(), "jspeed", at, base.with("color", inner(j), "cell", (double) SPEED, "alpha", alpha * 0.9));
    }

    /** 0.29.0 1인칭 화면 네 귀퉁이의 옅은 연기 (시전자의 1인칭에만 — 암영 은신 · 망령 걸음): 눈앞 1.3칸, vel (칸/초) 로 같이 움직임 */
    static void cornerSmoke(ServerPlayer sp, Jobs.Job j, double size, double life, double alpha, Vec3 vel) {
        Vec3 look = sp.getViewVector(1F);
        Vec3 up = screenUp(look);
        Vec3 right = V.unit(V.cross(look, up));
        Vec3 c = V.add(sp.getEyePosition(), look, 1.3);
        for (int i = 0; i < 4; i++) {
            double sx = (i % 2 == 0 ? -1 : 1) * 0.95;
            double sy = (i < 2 ? -1 : 1) * 0.55;
            smoke(sp.level(), V.add(V.add(c, right, sx), up, sy), size, j, life, only1p(sp).with("alpha", alpha, "vel", vel));
        }
    }

    /** 돌진 줄기 (rfx_jdash — 오른쪽이 앞): from → to. 굵기 = 길이의 1/4 (줄 여럿이 그 안에) */
    static void dash(ServerLevel level, Vec3 from, Vec3 to, Jobs.Job j, double life, Opt o) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        if (len < 0.2) return;
        two(level, "jdash", DASH, V.lerp(from, to, 0.5), life, o.with("dir", V.scale(d, 1 / len), "size", len / 2), outer(j), inner(j));
        streakSparks(level, from, to, j, o);
    }

    /** 0.47.0 더 화려하게: 줄기 · 일섬을 따라 흩어지는 불티 (몇 개 — 1인칭 숨김 그림이면 같이 숨김은 못 함: 줄 위 1칸 넘게 떨어진 곳만) */
    private static void streakSparks(ServerLevel level, Vec3 from, Vec3 to, Jobs.Job j, Opt o) {
        if (o.num("view", 0) != 0) return;
        for (int i = 0; i < 3; i++) {
            Vec3 p = V.lerp(from, to, 0.2 + 0.3 * i + Kit.rand() * 0.1);
            mmorpg.fabric.skill.SkillFx.fx(level, "rpg:arrow_spark", p, Opt.of("dir", V.unit(new Vec3(Kit.rand() - 0.5, 0.7, Kit.rand() - 0.5)), "speed", 2.2, "size", 0.12,
                    "grow", 0.6, "life", 0.32, "color", inner(j), "alpha", 1.0));
        }
    }

    /** 일섬 (rfx_jline): from → to — 가는 선이 그어지고 가운데가 부풀었다 사라짐 */
    static void line(ServerLevel level, Vec3 from, Vec3 to, Jobs.Job j, double life, Opt o) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        if (len < 0.2) return;
        two(level, "jline", LINE, V.lerp(from, to, 0.5), life, o.with("dir", V.scale(d, 1 / len), "size", len / 2), outer(j), inner(j));
        streakSparks(level, from, to, j, o);
    }

    /** 터짐 (rfx_jburst — 카메라를 봄) */
    static void burst(ServerLevel level, Vec3 at, double size, Jobs.Job j, double life, Opt o) {
        two(level, "jburst2", BURST, at, life, o.with("size", size, "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 360), outer(j), inner(j));
        // 0.47.0 더 화려하게: 크게 번지는 옅은 빛 한 겹 · (큰 터짐은) 불티
        double a = o.num("alpha", 1);
        two(level, "jburst2", BURST, at, life * 1.15, o.with("size", size * 1.4, "spin", Kit.rand() * 360, "alpha", a * 0.32, "coreAlpha", a * 0.22, "fade", 0.4), outer(j),
                inner(j));
        if (size >= 0.6 && o.num("view", 0) == 0) mmorpg.fabric.skill.SkillFx.sparks(level, at, inner(j), (int) Math.min(10, 3 + size * 3), 3 + size * 1.5);
    }

    /** 몸에서 솟는 빛 (rfx_jaura — 세워 카메라 쪽으로 돎): 높이 약 height 칸, life 초 동안 이어 돌림. 시전자 1인칭에서는 숨김 */
    static void aura(ServerPlayer sp, Jobs.Job j, double height, double life, double alpha) {
        // 그림 칸 128 × 256: 반 너비 = 0.3 × 높이 (몸 뒤에 다 가려지지 않게 넓게), 반 높이 = 0.6 × 높이 — 아래 끝이 발밑 조금 아래
        Vec3 c = V.up(sp.position(), height * 0.6 - 0.15);
        Opt o = hide1p(sp).with("size", height * 0.3, "loop", 1.0, "alpha", alpha, "coreAlpha", alpha * 0.8, "vel", RuneKit.bodyVel(sp), "fade", 0.3);
        double fps = AURA / 0.45; // 한 바퀴 0.45초
        Opt base = o.with("life", life, "frames", (double) AURA, "fps", fps);
        RuneKit.rfx(sp.level(), "jaura", c, base.with("color", outer(j), "cell", 0.0));
        RuneKit.rfx(sp.level(), "jaura", c, base.with("color", inner(j), "cell", (double) AURA, "alpha", alpha * 0.8));
    }

    // ------------------------------------------------------------ 0.28.0 전사 · 매지션 · 궁수 그림

    /** 칸 범위 하나 (빛 칸 start ~ · 심지 칸 total + start ~, count 장 — 책 · 활 · 과녁처럼 장면 일부만): fps (0 = 수명에 맞춤) · loop */
    static void cells(ServerLevel level, String name, int total, int start, int count, Vec3 at, double life, double fps, boolean loop, Opt o, double[] glow,
            double[] core) {
        double f = fps > 0 ? fps : count / Math.max(0.05, life);
        Opt b = o.with("life", life, "frames", (double) count, "fps", f, "fade", o.num("fade", 0.2), "loop", loop ? 1.0 : null, "phase",
                loop ? (o.has("phase") ? o.num("phase", 0) : Kit.rand() * count) : null);
        // 0.29.0 solo (주인마다 하나만 — owner 가 있어야 함): 빛 · 심지가 서로를 지우지 않게 번호를 나눔
        double solo = o.num("solo", 0);
        RuneKit.rfx(level, name, at, b.with("color", glow, "cell", (double) start, "solo", solo > 0 ? solo * 2 : null));
        RuneKit.rfx(level, name, at, b.with("color", core, "cell", (double) (total + start), "alpha", o.num("coreAlpha", o.num("alpha", 1)), "solo",
                solo > 0 ? solo * 2 + 1 : null));
    }

    /** 이어 돌리는 그림 (회전 칼날 · 회오리 · 구체 · 책장 …): 장 frames 개를 fps 로 되풀이 */
    static void loop(ServerLevel level, String name, int frames, Vec3 at, double life, double fps, Opt o, double[] glow, double[] core) {
        cells(level, name, frames, 0, frames, at, life, fps, true, o, glow, core);
    }

    /** 땅에 눕는 내려찍기 (rfx_jsmash): 충격 고리 반지름 radius 칸 (그림 고리 끝 = 반 크기 × 0.92) · 금 · 튀는 돌조각 */
    static void smash(ServerLevel level, Vec3 at, double radius, Jobs.Job j, double life, Opt o) {
        two(level, "jsmash2", SMASH, at, life, o.with("dir", V.UP, "size", radius / 0.92, "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 360), outer(j), inner(j));
        // 0.47.0 더 화려하게: 땅을 따라 퍼지는 충격파 · 튀는 불티
        mmorpg.fabric.skill.SkillFx.shock(level, at, inner(j), radius * 0.9, life * 0.8);
        mmorpg.fabric.skill.SkillFx.sparks(level, V.up(at, 0.2), outer(j), (int) Math.min(12, 4 + radius * 1.5), 3.5 + radius * 0.5);
    }

    /** 방어 깨짐 (rfx_jbreak — 카메라를 봄): 머리 위에 방패가 갈라짐 */
    static void breakMark(LivingEntity e, Jobs.Job j, double size, double life, double alpha) {
        if (!(e.level() instanceof ServerLevel level)) return;
        two(level, "jbreak", BREAK, RuneKit.overHead(e, 0.55 + size * 0.4), life, Opt.of("size", size, "alpha", alpha, "vel", RuneKit.entVel(e), "fade", 0.3), outer(j),
                inner(j));
    }

    /** 땅 (또는 허리 높이) 에 눕는 회전 칼날 (rfx_jwhirl — 이어 돌림): 칼날 반지름 radius 칸 (= 반 크기 × 0.8) */
    static void whirl(ServerLevel level, Vec3 center, double radius, Jobs.Job j, double life, Opt o) {
        loop(level, "jwhirl2", WHIRL, center, life, o.num("fps", 30), o.with("dir", V.UP, "size", radius / 0.8), outer(j), inner(j));
    }

    /** 몸에서 솟는 회오리 (rfx_jtwister — 세워 카메라 쪽으로 돎, 이어 돌림): 높이 약 height 칸 — 시전자 1인칭에서는 숨김 */
    static void twister(ServerPlayer sp, Jobs.Job j, double height, double life, double alpha) {
        Vec3 c = V.up(sp.position(), height * 0.6 - 0.2);
        loop(sp.level(), "jtwister", TWISTER, c, life, 22, hide1p(sp).with("size", height * 0.34, "alpha", alpha, "coreAlpha", alpha * 0.85, "vel", RuneKit.bodyVel(sp),
                "fade", 0.3), outer(j), inner(j));
    }

    /** 불꽃 꼬리 (rfx_jcomet — 머리가 앞): 머리 head 가 dir 쪽 (단위) 으로 speed 칸/초. 길이 = size × 2 */
    static void comet(ServerLevel level, Vec3 head, Vec3 dir, double size, double speed, double life, Jobs.Job j, Opt o) {
        Vec3 c = V.add(head, dir, -size * 0.64);
        loop(level, "jcomet", COMET, c, life, 16, o.with("dir", dir, "size", size, "speed", speed), outer(j), inner(j));
    }

    /** 같은 것 — 색을 정함 (금서의 불 · 얼음 주문) */
    static void comet(ServerLevel level, Vec3 head, Vec3 dir, double size, double speed, double life, double[] glow, double[] core, Opt o) {
        Vec3 c = V.add(head, dir, -size * 0.64);
        loop(level, "jcomet", COMET, c, life, 16, o.with("dir", dir, "size", size, "speed", speed), glow, core);
    }

    /** 날아가는 그림 (머리가 앞 — 마탄 · 빛의 화살): 머리가 from 근처에서 나와 to 에 닿음. headAt = 머리 자리 (그림 길이의 비율 — 가운데 기준 −1 ~ 1). 닿는 틱 */
    private static int fly(ServerLevel level, String name, int frames, Vec3 from, Vec3 to, double speed, double size, double headAt, Opt o, double[] glow,
            double[] core) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        if (len < 0.1) return 0;
        Vec3 u = V.scale(d, 1 / len);
        double life = Math.max(0.05, len / speed);
        // 처음: 꼬리 끝이 from (머리는 from + 길이) — 1인칭 화면 뒤에서 튀어나오지 않게, 길이보다 짧은 거리면 그만큼만
        double lead = Math.min(size * (1 + headAt), len * 0.6);
        Vec3 c0 = V.add(from, u, lead - size * headAt);
        double travel = Math.max(0, len - lead);
        loop(level, name, frames, c0, life, 14, o.with("dir", u, "size", size, "speed", travel / life, "fade", o.num("fade", 0.1)), glow, core);
        return Math.max(1, (int) Math.round(life * 20));
    }

    /** 마탄 (rfx_jbolt) 이 from → to: 닿는 틱 */
    static int bolt(ServerLevel level, Vec3 from, Vec3 to, Jobs.Job j, double speed, double size, Opt o) {
        return bolt(level, from, to, outer(j), inner(j), speed, size, o);
    }

    /**
     * 0.31.0 색을 정한 마탄 (스킬 강화 — 재료마다 그 색의 빛이 모임): from → to, 닿는 틱. 0.50.0 v2 별 마탄 (rfx_jbolt2 — 도는 네 뿔 별 머리 · 가늘어지는
     * 꼬리 · 반짝임, 머리 = 길이의 0.58): v1 과 같은 길이 (× 1.09)
     */
    static int bolt(ServerLevel level, Vec3 from, Vec3 to, double[] glow, double[] core, double speed, double size, Opt o) {
        return fly(level, "jbolt2", BOLT2, from, to, speed, size * 1.09, 0.58, o, glow, core);
    }

    /** 빛의 화살 (0.50.0 v2 바람 화살 rfx_jarrow2 — 잎 촉 · 살대 · 깃 셋 · 살대를 감아 도는 바람 띠 둘) 이 from → to: 닿는 틱 */
    static int arrow(ServerLevel level, Vec3 from, Vec3 to, Jobs.Job j, double speed, double size, Opt o) {
        return fly(level, "jarrow2", ARROW2, from, to, speed, size, 0.97, o, outer(j), inner(j));
    }

    /** 멈춰 있는 빛의 화살 (잔상 — 촉이 tip, 방향 dir) */
    static void arrowAt(ServerLevel level, Vec3 tip, Vec3 dir, double size, double life, Jobs.Job j, Opt o) {
        loop(level, "jarrow2", ARROW2, V.add(tip, dir, -size * 0.97), life, 10, o.with("dir", dir, "size", size), outer(j), inner(j));
    }

    /** 깨지는 거울 (rfx_jshatter — 카메라를 봄): 금이 퍼지고 조각이 흩어짐 (반지름 약 size 칸) */
    static void shatter(ServerLevel level, Vec3 at, double size, Jobs.Job j, double life, Opt o) {
        two(level, "jshatter", SHATTER, at, life, o.with("size", size, "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 360), outer(j), inner(j));
    }

    /**
     * 마력 구체 → 0.50.0 v2 떠도는 별 (rfx_jstar — 구 안의 빛나는 별 · 서로 다른 빠르기로 도는 기운 고리 둘 · 도는 반짝임, 카메라를 봄, 이어 돌림): 구 반지름
     * radius 칸 (v1 rfx_jorb 의 반 크기 × 0.6 과 같은 전체 크기 — × 1.17)
     */
    static void orb(ServerLevel level, Vec3 at, double radius, Jobs.Job j, double life, Opt o) {
        loop(level, "jstar", STAR, at, life, 12, o.with("size", radius / 0.6 * 1.17), outer(j), inner(j));
    }

    /**
     * 굵은 광선 (rfx_jbeam — 방향으로 누움): from → to 를 길이 seg 칸 조각으로 (그림은 길이에 따라 굵기가 늘어서 — 조각마다 굵기 = 조각 길이 × 0.25),
     * 조각끼리 조금 겹침
     */
    static void beam(ServerLevel level, Vec3 from, Vec3 to, Jobs.Job j, double seg, double life, Opt o) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        if (len < 0.3) return;
        Vec3 u = V.scale(d, 1 / len);
        int n = Math.max(1, (int) Math.ceil(len / (seg * 0.82)));
        double step = len / n;
        for (int i = 0; i < n; i++) {
            Vec3 c = V.add(from, u, step * (i + 0.5));
            two(level, "jbeam", BEAM, c, life, o.with("dir", u, "size", Math.max(step * 0.62, seg / 2)), outer(j), inner(j));
        }
    }

    /**
     * 펼쳐진 책 (0.50.0 v2 마도서 rfx_jtome — 카메라를 봄): 책장이 넘어가며 별이 떠오름 (3 ~ 8 이어 돌림) · closing 이면 닫히며 번쩍 (9 ~ 11). 책 너비 약
     * size × 1.44 칸 (v1 과 같게 × 0.95)
     */
    static void book(ServerLevel level, Vec3 at, double size, Jobs.Job j, double life, boolean closing, Opt o) {
        Opt b = o.with("size", size * 0.95);
        if (closing) cells(level, "jtome", TOME, 9, 3, at, life, 0, false, b, outer(j), inner(j));
        else cells(level, "jtome", TOME, 3, 6, at, life, 9, true, b, outer(j), inner(j));
    }

    /** 날리는 책장 (rfx_jpage — 이어 돌림) */
    static void page(ServerLevel level, Vec3 at, double size, double[] glow, double[] core, double life, Opt o) {
        loop(level, "jpage", PAGE, at, life, 10, o.with("size", size), glow, core);
    }

    /**
     * 빛의 활 (0.50.0 v2 리커브 활 rfx_jbow2 — 시위를 당기며 빛이 모임 0 ~ 5 · 놓음 6 ~ 7, 옆에서 본 활을 눕혀 (spin −90 — 화살이 화면 위 = 앞, 궁극기의 활 판과 같은
     * 모습)): cell · count 는 v1 (0 ~ 3 = 당긴 정도, 4 ~ 5 = 놓음) 의 칸 — v2 칸으로 옮김. 활 길이 약 size × 1.72 칸
     */
    static void bow(ServerLevel level, Vec3 at, double size, Jobs.Job j, int cell, int count, double life, Opt o) {
        int c0 = Math.max(0, Math.min(BOW - 1, cell));
        int c1 = Math.max(c0, Math.min(BOW - 1, cell + Math.max(1, count) - 1));
        int a = bow2(c0);
        int b = bow2(c1);
        cells(level, "jbow2", BOW2, a, b - a + 1, at, life, 0, false, o.with("size", size, "spin", o.num("spin", -90)), outer(j), inner(j));
    }

    /** v1 활 칸 → v2 칸 (당김 0 ~ 3 → 0 ~ 5 · 놓음 4 ~ 5 → 6 ~ 7) */
    private static int bow2(int c) {
        return c <= 3 ? (int) Math.round(c * 5 / 3.0) : 6 + Math.min(1, c - 4);
    }

    /** 과녁 (rfx_jtarget — 면이 normal 쪽): 바깥 고리 반지름 radius 칸 (= 반 크기 × 0.84). stage 0 = 조여 들어옴 (0 ~ 3) · 1 = 맞춰진 채 깜빡임 (3 ~ 5 이어 돌림) */
    static void target(ServerLevel level, Vec3 at, double radius, Jobs.Job j, Vec3 normal, int stage, double life, Opt o) {
        Opt b = o.with("dir", normal, "size", radius / 0.84);
        if (stage == 0) cells(level, "jtarget", TARGET, 0, 4, at, life, 0, false, b, outer(j), inner(j));
        else cells(level, "jtarget", TARGET, 3, 3, at, life, 8, true, b, outer(j), inner(j));
    }

    /** 땅에 눕는 바람 고리 (rfx_jwind): 바깥 반지름 약 radius 칸 */
    static void wind(ServerLevel level, Vec3 at, double radius, Jobs.Job j, double life, Opt o) {
        two(level, "jwind", WIND, at, life, o.with("dir", V.UP, "size", radius, "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 360), outer(j), inner(j));
    }

    // ------------------------------------------------------------ 0.29.0 암살자 · 창술사 · 사신 그림

    /** 꽃 (rfx_jpetal 0 ~ 3 — 카메라를 봄, 이어 돌림): 다섯 꽃잎 (표식 · 낙화). 꽃 지름 약 size × 1.6 칸 */
    static void blossom(ServerLevel level, Vec3 at, double size, Jobs.Job j, double life, Opt o) {
        cells(level, "jpetal", PETAL, 0, 4, at, life, 8, true, o.with("size", size, "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 360), outer(j), inner(j));
    }

    /** 꽃잎 하나 (rfx_jpetal 4 ~ 7 — 카메라를 봄, 팔랑임): dir (단위) 쪽으로 speed 칸/초 */
    static void petal(ServerLevel level, Vec3 at, Vec3 dir, double speed, double size, Jobs.Job j, double life, Opt o) {
        cells(level, "jpetal", PETAL, 4, 4, at, life, 10, true, o.with("dir", dir, "speed", speed, "size", size, "spin", Kit.rand() * 360, "spinRate",
                (Kit.rand() - 0.5) * 500), outer(j), inner(j));
    }

    /** 꽃잎 흩날림: at 에서 n 장이 사방 (조금 위쪽) 으로 */
    static void petals(ServerLevel level, Vec3 at, int n, double speed, double size, Jobs.Job j, double life, Opt o) {
        for (int i = 0; i < n; i++) {
            double a = Kit.rand() * Math.PI * 2;
            Vec3 d = V.unit(new Vec3(Math.cos(a), 0.15 + Kit.rand() * 0.75, Math.sin(a)));
            petal(level, at, d, speed * (0.5 + Kit.rand() * 0.7), size * (0.8 + Kit.rand() * 0.4), j, life * (0.8 + Kit.rand() * 0.4), o);
        }
    }

    /** 도는 비수 (rfx_jdagger2 — 카메라를 봄, 화면에서 팽이처럼 돎): from → to 를 ticks 틱에. 비수 길이 약 size × 1.6 칸 */
    static void dagger(ServerLevel level, Vec3 from, Vec3 to, int ticks, double size, Jobs.Job j, Opt o) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        double life = Math.max(0.05, ticks / 20.0);
        // 0.50.0 v2 휜 단검 (rfx_jdagger2 — 여덟 장이 한 바퀴 · 가는 원 꼬리): 장이 돌림 (24장/초 = 초당 세 바퀴 — 그림을 더 돌리지 않음)
        loop(level, "jdagger2", DAGGER2, from, life, o.num("fps", 24), o.with("dir", len > 1e-3 ? V.scale(d, 1 / len) : V.UP, "speed", len / life, "size", size * 1.1,
                "spin", Kit.rand() * 360, "spinRate", o.num("spinRate", 0), "fade", o.num("fade", 0.1)), outer(j), inner(j));
    }

    /** 그림자 연기 (rfx_jsmoke — 카메라를 봄): 뭉게뭉게 퍼지며 사라짐 (반지름 약 size 칸). 색 = 짙은 빛 · 그보다 옅은 빛 (어두운 연기는 섞는 그림이라 어둡게 보임) */
    static void smoke(ServerLevel level, Vec3 at, double size, double[] glow, double[] core, double life, Opt o) {
        two(level, "jsmoke", SMOKE, at, life, o.with("size", size, "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 360, "fade", o.num("fade", 0.45)), glow, core);
    }

    /** 직업의 짙은 연기 (어두운 둘째 빛 · 그 반쯤) */
    static void smoke(ServerLevel level, Vec3 at, double size, Jobs.Job j, double life, Opt o) {
        double[] c = JobKit.core(j);
        smoke(level, at, size, new double[] {c[0] * 0.45, c[1] * 0.45, c[2] * 0.45}, c, life, o);
    }

    /** 엇갈린 두 베기 (rfx_jcross — 카메라를 봄): X 자로 그어졌다 사라짐 (반 크기 size) */
    static void cross(ServerLevel level, Vec3 at, double size, Jobs.Job j, double life, Opt o) {
        two(level, "jcross", CROSS, at, life, o.with("size", size, "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 360), outer(j), inner(j));
    }

    /** 찌르기 번쩍 (rfx_jstab — 카메라를 봄): 길쭉한 창끝 빛 · 가로 충격 줄 · 퍼지는 고리 (반 크기 size) */
    static void stab(ServerLevel level, Vec3 at, double size, Jobs.Job j, double life, Opt o) {
        two(level, "jstab", STAB, at, life, o.with("size", size, "spin", o.has("spin") ? o.num("spin", 0) : (Kit.rand() - 0.5) * 50), outer(j), inner(j));
    }

    /** 도는 창 (rfx_jspin — 면이 normal 쪽, 이어 돌림): 양끝 창날이 도는 원판 — 날 끝 반지름 radius 칸 (= 반 크기 × 0.86) */
    static void spin(ServerLevel level, Vec3 center, double radius, Jobs.Job j, Vec3 normal, double life, Opt o) {
        loop(level, "jspin2", SPIN, center, life, o.num("fps", 28), o.with("dir", normal, "size", radius / 0.86), outer(j), inner(j));
    }

    /** 빛의 창 (rfx_jspear — 방향으로 누움, 오른쪽이 창끝): 창끝 tip 이 dir (단위) 쪽으로 speed 칸/초 (0 = 멈춤). 길이 = size × 2 */
    static void spear(ServerLevel level, Vec3 tip, Vec3 dir, double size, double speed, double life, Jobs.Job j, Opt o) {
        // 0.50.0 v2 긴 창 (rfx_jspear2 — 잎 창날 · 작은 가름쇠 · 감은 손잡이 · 흐르는 빛, 창끝 = 길이의 0.98)
        loop(level, "jspear2", SPEAR2, V.add(tip, dir, -size * 0.98), life, 14, o.with("dir", dir, "size", size, "speed", speed), outer(j), inner(j));
    }

    /** 날아가는 빛의 창 from → to (창끝이 to 에 닿음): 닿는 틱 */
    static int spearFly(ServerLevel level, Vec3 from, Vec3 to, Jobs.Job j, double speed, double size, Opt o) {
        return fly(level, "jspear2", SPEAR2, from, to, speed, size, 0.98, o, outer(j), inner(j));
    }

    /** 혼불 (rfx_jsoul — 카메라를 봄, 이어 돌림): 흔들리는 혼의 불꽃 (높이 약 size × 2 칸) */
    static void soul(ServerLevel level, Vec3 at, double size, Jobs.Job j, double life, Opt o) {
        loop(level, "jsoul", SOUL, at, life, 12, o.with("size", size), outer(j), inner(j));
    }

    /** 날아가는 혼불 from → to (speed 칸/초): 닿는 틱 */
    static int soulFly(ServerLevel level, Vec3 from, Vec3 to, double speed, double size, Jobs.Job j, Opt o) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        if (len < 0.1) return 0;
        double life = Math.max(0.05, len / speed);
        soul(level, from, size, j, life, o.with("dir", V.scale(d, 1 / len), "speed", len / life, "fade", 0.15));
        return Math.max(1, (int) Math.round(life * 20));
    }

    /** 사신 그림자 (rfx_jreaper — 세워 카메라 쪽으로 돎): stage 0 = 서서 흔들림 (0 ~ 3 이어 돌림) · 1 = 낫을 휘두름 (4 ~ 7). 높이 약 size × 2.67 칸 (at = 가운데) */
    static void reaper(ServerLevel level, Vec3 at, double size, Jobs.Job j, int stage, double life, Opt o) {
        if (stage == 0) cells(level, "jreaper", REAPER, 0, 4, at, life, 6, true, o.with("size", size), outer(j), inner(j));
        else cells(level, "jreaper", REAPER, 4, 4, at, life, 0, false, o.with("size", size), outer(j), inner(j));
    }

    /** 명계의 문 (rfx_jgate — 세워 카메라 쪽으로 돎): stage 0 = 솟음 (0 ~ 2) · 1 = 열린 채 소용돌이 (3 ~ 5 이어 돌림) · 2 = 닫힘 (6 ~ 7). 높이 약 size × 2 칸 (at = 가운데) */
    static void gate(ServerLevel level, Vec3 at, double size, Jobs.Job j, int stage, double life, Opt o) {
        Opt b = o.with("size", size);
        switch (stage) {
            case 0 -> cells(level, "jgate", GATE, 0, 3, at, life, 0, false, b, outer(j), inner(j));
            case 1 -> cells(level, "jgate", GATE, 3, 3, at, life, 8, true, b, outer(j), inner(j));
            default -> cells(level, "jgate", GATE, 6, 2, at, life, 0, false, b, outer(j), inner(j));
        }
    }

    /** 망자의 손 (rfx_jhand — 세워 카메라 쪽으로 돎): 땅 ground 에서 솟아 움켜쥠 (높이 약 size × 4 칸 — 그림 아래 끝이 땅) */
    static void hand(ServerLevel level, Vec3 ground, double size, Jobs.Job j, double life, Opt o) {
        two(level, "jhand", HAND, V.up(ground, size * 2), life, o.with("size", size), outer(j), inner(j));
    }

    // ------------------------------------------------------------ 0.30.0 성기사 · 사제 · 수호기사 그림

    /**
     * 내리치는 벼락 (rfx_jthunder — 세워 카메라 쪽으로 돎, 그림 128 × 512): 아래 끝이 ground · 높이 height 칸 (반 너비 = 높이 / 8). 위에서 내려와 번쩍 →
     * 가지가 갈라져 깜빡이며 사라짐
     */
    static void thunder(ServerLevel level, Vec3 ground, double height, Jobs.Job j, double life, Opt o) {
        two(level, "jthunder", THUNDER, V.up(ground, height / 2), life, o.with("size", height / 8), outer(j), inner(j));
    }

    /** 같은 것 — 색을 정함 */
    static void thunder(ServerLevel level, Vec3 ground, double height, double[] glow, double[] core, double life, Opt o) {
        two(level, "jthunder", THUNDER, V.up(ground, height / 2), life, o.with("size", height / 8), glow, core);
    }

    /** 땅에 눕는 번개 고리 (rfx_jvolt — 면이 위): 고리 반지름 radius 칸 (= 반 크기 × 0.8) — 나타나 지직거리다 사라짐 (안쪽 무늬 없음) */
    static void volt(ServerLevel level, Vec3 center, double radius, Jobs.Job j, double life, Opt o) {
        two(level, "jvolt", VOLT, center, life, o.with("dir", V.UP, "size", radius / 0.8, "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 360), outer(j),
                inner(j));
    }

    /** 번개 대망치 (rfx_jmaul2 — 세워 카메라 쪽으로 돎, 이어 돌림 · 머리가 아래): 가운데 at · 높이 약 size × 2.67 칸 */
    static void maul(ServerLevel level, Vec3 at, double size, Jobs.Job j, double life, Opt o) {
        // 0.50.0 v2 대망치 (rfx_jmaul2 — 머리를 기어 다니는 번개 · 빛나는 룬 띠, 그림 256 × 384): v1 과 같은 높이 (× 0.88)
        loop(level, "jmaul2", MAUL2, at, life, 12, o.with("size", size * 0.88), outer(j), inner(j));
    }

    /**
     * 빛의 날개 (rfx_jwing — 세워 면이 앞뒤로, 이어 돌림 · 천천히 날갯짓, 그림 256 × 192): 가운데 at (등 뒤) · 너비 약 size × 2 칸. 사각형은 앞면만 그려지므로
     * (뒷면은 걸러짐 — 0.30.0 사진첩: 면이 보는 쪽 facing 이면 뒤 3인칭에서 날개가 안 보였음) 뒤를 보는 장 (뒤 3인칭 · 뒤의 사람) 과 앞을 보는 장 (앞의 사람) 둘.
     * solo 가 있으면 앞 장은 solo + 16
     */
    static void wing(ServerLevel level, Vec3 at, Vec3 facing, double size, Jobs.Job j, double life, Opt o) {
        loop(level, "jwing", WING, at, life, 5, o.with("dir", V.scale(facing, -1), "size", size), outer(j), inner(j));
        double solo = o.num("solo", 0);
        loop(level, "jwing", WING, at, life, 5, o.with("dir", facing, "size", size, "solo", solo > 0 ? solo + 16 : null), outer(j), inner(j));
    }

    /** 기절 별 (rfx_jstun — 카메라를 봄, 이어 돌림): 머리 위를 도는 별 다섯 (너비 약 몸 + 0.8칸) — ticks 동안 */
    static void stunStars(LivingEntity e, Jobs.Job j, int ticks) {
        if (!(e.level() instanceof ServerLevel level) || ticks <= 0) return;
        double size = 0.45 + Kit.bodyPad(e) * 0.4;
        loop(level, "jstun", STUN, RuneKit.overHead(e, 0.32), ticks / 20.0, 14, Opt.of("size", size, "vel", RuneKit.entVel(e), "fade", 0.12, "alpha", 0.95),
                new double[] {1, 0.86, 0.3}, new double[] {1, 1, 0.82});
    }

    /** 심판 표시 (rfx_jjudge — 카메라를 봄, 깜빡임): 작은 번개 문장 (반 크기 size) */
    static void judgePip(ServerLevel level, Vec3 at, double size, Jobs.Job j, double life, Opt o) {
        loop(level, "jjudge", JUDGE, at, life, 10, o.with("size", size), outer(j), inner(j));
    }

    /** 회복 십자 (rfx_jheal — 카메라를 봄): 십자 빛이 피어나 조금 떠오르며 사라짐 (반 크기 size) */
    static void healCross(ServerLevel level, Vec3 at, double size, Jobs.Job j, double life, Opt o) {
        two(level, "jheal", HEAL, at, life, o.with("size", size), outer(j), inner(j));
    }

    /** 같은 것 — 색을 정함 (성기사의 성역 · 맹세 회복은 금빛) */
    static void healCross(ServerLevel level, Vec3 at, double size, double[] glow, double[] core, double life, Opt o) {
        two(level, "jheal", HEAL, at, life, o.with("size", size), glow, core);
    }

    /** 음표 (rfx_jnote — 카메라를 봄, kind 0 ~ 3 중 한 장): o 의 dir · speed 로 떠감 (높이 약 size × 2) */
    static void note(ServerLevel level, Vec3 at, int kind, double size, Jobs.Job j, double life, Opt o) {
        cells(level, "jnote", NOTE, Math.floorMod(kind, NOTE), 1, at, life, 1, false, o.with("size", size), outer(j), inner(j));
    }

    /** 성흔 (rfx_jstigma — 카메라를 봄, 이어 돌림): 빛나는 십자가와 빛살 (반 크기 size) */
    static void stigma(ServerLevel level, Vec3 at, double size, Jobs.Job j, double life, Opt o) {
        loop(level, "jstigma", STIGMA, at, life, 8, o.with("size", size), outer(j), inner(j));
    }

    /** 큰 종 (0.50.0 v2 장식 종 rfx_jbell2 — 세워 카메라 쪽으로 돎): 왼쪽 → 가운데 → 오른쪽 → 가운데로 흔들리며 울림 (아래로 퍼지는 소리 고리) · 가운데 at · 높이 약 size × 2 칸 */
    static void bell(ServerLevel level, Vec3 at, double size, double[] glow, double[] core, double life, Opt o) {
        two(level, "jbell2", BELL2, at, life, o.with("size", size * 0.91), glow, core);
    }

    /** 가만히 매달린 종 (rfx_jbell2 의 가운데 장 — 내려오는 동안) */
    static void bellStill(ServerLevel level, Vec3 at, double size, double[] glow, double[] core, double life, Opt o) {
        cells(level, "jbell2", BELL2, BELL_STILL, 1, at, life, 1, false, o.with("size", size * 0.91), glow, core);
    }

    /** rfx_jbell2 에서 종이 똑바로 선 장 */
    static final int BELL_STILL = 2;

    /** 빛의 사슬 (rfx_jchain — 방향으로 누움, 이어 돌림 · 그림 256 × 32): from → to 를 seg 칸 조각으로 (굵기 = 조각 길이 / 8) */
    static void chain(ServerLevel level, Vec3 from, Vec3 to, Jobs.Job j, double seg, double life, Opt o) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        if (len < 0.3) return;
        Vec3 u = V.scale(d, 1 / len);
        int n = Math.max(1, (int) Math.ceil(len / seg));
        double step = len / n;
        for (int i = 0; i < n; i++) {
            Vec3 c = V.add(from, u, step * (i + 0.5));
            loop(level, "jchain", CHAIN, c, life, 10, o.with("dir", u, "size", step / 2 * 1.04), outer(j), inner(j));
        }
    }

    /** 둥근 방패 (0.50.0 v2 rfx_jbuckler2 — 카메라를 봄, 앞면이 타원으로 눌렸다 펴지며 도는 여덟 장 + 화면에서 팽이처럼 돎): 반지름 약 size × 0.9 칸 */
    static void buckler(ServerLevel level, Vec3 at, double size, Jobs.Job j, double life, Opt o) {
        loop(level, "jbuckler2", BUCKLER2, at, life, 20, o.with("size", size, "spin", Kit.rand() * 360, "spinRate", o.num("spinRate", 720)), outer(j), inner(j));
    }

    /** 날아가는 방패 from → to (speed 칸/초): 닿는 틱 */
    static int bucklerFly(ServerLevel level, Vec3 from, Vec3 to, double speed, double size, Jobs.Job j, Opt o) {
        return fly(level, "jbuckler2", BUCKLER2, from, to, speed, size, 0, o.with("spin", Kit.rand() * 360, "spinRate", o.num("spinRate", 720)), outer(j), inner(j));
    }

    /**
     * 빛의 방패 (rfx_jaegis2 — 세워 카메라 쪽으로 돎, 긴 연 모양): stage 0 = 나타남 · 1 = 버팀 (이어 돌림) · 2 = 금이 가며 사라짐. 가운데 at · 높이 약 size × 2.67 칸
     */
    static void aegis(ServerLevel level, Vec3 at, double size, Jobs.Job j, int stage, double life, Opt o) {
        // 0.50.0 v2 연 모양 문장 방패 (rfx_jaegis2 — 날개 달린 별 문장, 0 ~ 2 빛 줄에서 나타남 · 3 ~ 5 버팀 (빛이 지나감)): v1 과 같은 높이 (× 1.18).
        // 사라짐 (stage 2) 은 v2 에 깨지는 장이 없어 버팀 마지막 장이 흐려지며 깨지는 거울 (rfx_jshatter) 이 겹침
        Opt b = o.with("size", size * 1.18);
        switch (stage) {
            case 0 -> cells(level, "jaegis2", AEGIS2, 0, 3, at, life, 0, false, b, outer(j), inner(j));
            case 1 -> cells(level, "jaegis2", AEGIS2, 3, 3, at, life, 6, true, b, outer(j), inner(j));
            default -> {
                cells(level, "jaegis2", AEGIS2, 5, 1, at, life, 0, false, b.with("fade", 0.9), outer(j), inner(j));
                shatter(level, at, size * 1.3, j, life, o.with("alpha", o.num("alpha", 1) * 0.85));
            }
        }
    }

    // ------------------------------------------------------------ 0.50.0 직업 고유 그림 (tools/job_fx_art_v2.py — 사용자 "직업 고유 연출")

    static final int VALWING = 6;
    static final int ECLIPSE = 8;
    static final int ROSE = 8;
    static final int FROST = 6;
    static final int RUNES = 4;
    static final int FEATHER = 8;
    static final int WALL = 4;

    /**
     * 발키리의 날개 (rfx_jvalwing — 세워 카메라 쪽으로 돎, 이어 돌림 · 천천히 날갯짓): 등 뒤 (몸에서 뒤로 0.35칸 · 위로 1.3칸) · 너비 약 size × 2 칸, ticks 동안
     * 4틱마다 다시 그림 (주인마다 하나 — solo, 몸을 따라감). 1인칭에서는 숨김 (등 뒤라 눈앞을 가리지 않게)
     */
    static void valWings(ServerPlayer sp, Jobs.Job j, double size, int ticks, double alpha) {
        String life = RuneKit.lifeToken(sp);
        double phase = Kit.rand() * VALWING;
        for (int t = 0; t < Math.max(1, ticks); t += 4) {
            int tt = t;
            RuneKit.later(t, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                Vec3 f = Kit.facingOf(sp);
                Vec3 at = V.up(V.add(sp.position(), f, -0.35), 1.3);
                double fade = tt + 4 >= ticks ? 0.6 : 0.0;
                cells(sp.level(), "jvalwing", VALWING, 0, VALWING, at, 0.3, 8, true, hide1p(sp).with("size", size * (tt == 0 ? 0.85 : 1.0), "vel", RuneKit.bodyVel(sp),
                        "solo", 37.0, "phase", phase + tt * 8 / 20.0, "alpha", alpha, "fade", fade), outer(j), inner(j));
            });
        }
    }

    /** 검은 해 (rfx_jeclipse — 카메라를 봄): 커지며 나타남 (0 ~ 3) 뒤 일렁이는 코로나가 맥동 (4 ~ 7 이어 돌림). 지름 약 size × 1.6 칸 */
    static void eclipse(ServerLevel level, Vec3 at, double size, Jobs.Job j, double life, Opt o) {
        double grow = Math.min(0.5, life * 0.3);
        cells(level, "jeclipse", ECLIPSE, 0, 4, at, grow, 0, false, o.with("size", size, "fade", 0.0), outer(j), inner(j));
        RuneKit.later(Math.max(1, (int) Math.round(grow * 20) - 1), () -> cells(level, "jeclipse", ECLIPSE, 4, 4, at, Math.max(0.1, life - grow), 8, true,
                o.with("size", size, "fade", o.num("fade", 0.25)), outer(j), inner(j)));
    }

    /** 검은 장미 (rfx_jrose — 카메라를 봄): 피어나 (0 ~ 4) 꽃잎이 흩어짐 (5 ~ 7). 지름 약 size × 1.5 칸 */
    static void rose(ServerLevel level, Vec3 at, double size, Jobs.Job j, double life, Opt o) {
        two(level, "jrose", ROSE, at, life, o.with("size", size, "spin", o.has("spin") ? o.num("spin", 0) : Kit.rand() * 30 - 15, "fade", o.num("fade", 0.2)),
                outer(j), inner(j));
    }

    /** 얼음 결정 표시 (rfx_jfrost — 카메라를 봄): 자라나 (빙결) 금이 가며 깨짐 — 몸 가운데 · 몸 크기에 맞춤, ticks 동안. 색 = 얼음 빛 (glow · core) */
    static void frost(LivingEntity e, double[] glow, double[] core, int ticks) {
        if (!(e.level() instanceof ServerLevel level)) return;
        double size = 0.75 + Kit.bodyPad(e) * 0.8 + e.getBbHeight() * 0.15;
        two(level, "jfrost", FROST, Kit.bodyPoint(e), Math.max(0.2, ticks / 20.0), Opt.of("size", size, "spin", Kit.rand() * 30 - 15, "fade", 0.2, "vel",
                RuneKit.entVel(e)), glow, core);
    }

    /**
     * 룬 고리 (rfx_jrunes — 방향으로 눕는 각진 글자 띠, 이어 돌림 · 차례로 깜빡임): center 둘레 반지름 radius 칸의 수평 고리를 n 조각의 띠로 (조각마다 고리를 따라
     * 누움 — 보는 사람 쪽으로 돎). turn = 고리를 돌리는 각 (도 — 조각 자리)
     */
    static void runeRing(ServerLevel level, Vec3 center, double radius, Jobs.Job j, int n, double turn, double life, Opt o) {
        int m = Math.max(3, n);
        double half = Math.PI * radius / m * 1.02;
        for (int i = 0; i < m; i++) {
            double a = Math.toRadians(turn) + i * 2 * Math.PI / m;
            Vec3 at = new Vec3(center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius);
            Vec3 tan = new Vec3(-Math.sin(a), 0, Math.cos(a));
            cells(level, "jrunes", RUNES, 0, RUNES, at, life, 8, true, o.with("dir", tan, "size", half, "phase", (double) (i % RUNES)), outer(j), inner(j));
        }
    }

    /** 떠다니는 깃털 n 개 (rfx_jfeather — 카메라를 봄, 이어 돌림 · 뒤집힘): at 둘레 spread 칸에서 천천히 내려앉음 (크기 약 size 칸) */
    static void feathers(ServerLevel level, Vec3 at, int n, double spread, double size, Jobs.Job j, double life, Opt o) {
        for (int i = 0; i < n; i++) {
            Vec3 p = V.add(at, new Vec3((Kit.rand() - 0.5) * 2 * spread, (Kit.rand() - 0.3) * spread, (Kit.rand() - 0.5) * 2 * spread));
            Vec3 vel = new Vec3((Kit.rand() - 0.5) * 0.6, -0.35 - Kit.rand() * 0.35, (Kit.rand() - 0.5) * 0.6);
            cells(level, "jfeather", FEATHER, 0, FEATHER, p, life * (0.8 + Kit.rand() * 0.4), 10, true, o.with("size", size * (0.8 + Kit.rand() * 0.4), "vel", vel,
                    "spin", Kit.rand() * 360, "fade", o.num("fade", 0.4)), outer(j), inner(j));
        }
    }

    /** 땅에서 솟는 돌 성벽 (rfx_jwall — 세워 카메라 쪽으로 돎): 낮음 → 다 솟음 (0 ~ 3, rise 초) 뒤 그대로 (금은 빛 줄). 땅 ground 에 아래 끝 · 높이 약 size × 2 칸 */
    static void wall(ServerLevel level, Vec3 ground, double size, Jobs.Job j, double rise, double life, Opt o) {
        Vec3 at = V.up(ground, size);
        cells(level, "jwall", WALL, 0, WALL, at, Math.max(0.05, rise), 0, false, o.with("size", size, "fade", 0.0), outer(j), inner(j));
        RuneKit.later(Math.max(1, (int) Math.round(rise * 20) - 1), () -> cells(level, "jwall", WALL, WALL - 1, 1, at, Math.max(0.1, life - rise), 1, false,
                o.with("size", size, "fade", o.num("fade", 0.3)), outer(j), inner(j)));
    }
}
