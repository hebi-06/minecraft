package mmorpg.fabric.job;

import java.util.function.Predicate;
import mmorpg.core.Obj;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.V;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-18 축복의 길 — 좌클릭 평타의 스킬급 연출 (사용자 "좌클릭에 스킬과 같은 참격 평타 스킬이 나가게", 컨셉 문서 "지금 평타 연타와 참격 판은 그대로 두고, 타마다
 * 스킬급 연출을 더합니다: 입체 참격 · 빛 · 불티 · 무기 색 잔상, 셋째 타는 더 큰 마무리 참격"): 평타 참격 판 (BasicSlash) 위에 직업 스킬과 같은 위르드식 눈앞 베기
 * (도트 2D + 바닐라 빛 파티클 — CommonSkills.eyeSlash) · 칼끝 불티 · 맞는 자리 반짝임을 그 사람 직업의 그림 묶음으로 (직업이 없으면 강철 묶음).
 * 연타의 마지막 타 (칼 둘째 · 대검 · 낫 셋째 · 창 둘째) 는 크게 + 겹 베기 · 섬광 · 발밑 충격 고리. 연출만 (피해 · 범위는 평타 그대로). 화면 흔들림 없음.
 * <p>2026-11-26 사용자 "마크 파티클은 최소한으로": 바닐라 파티클 겹 (위르드식 — 불티 · 반짝임 · 섬광 · 충격 고리의 바닐라 겹) 은 그리지 않음 (WyrdKit mute —
 * 도트 2D 그림판 그림만). 같은 날 "모든 평타 때릴때마다 왜 세로 베기 파란색 나오냐 없애고": 눈앞 베기 (eyeSlash — 1인칭 초승달 · 남에게 세운 반달 판) 를 뺌 —
 * 남은 것은 칼끝 불티 · 창 찌르기 칼빛 · 방패 밀치기 충격 (2026-11-27 사용자 "연타 마지막에 섬광과 고리 빼버려" — 마무리의 칼끝 섬광 · 발밑
 * 충격 고리도 뺌, 마무리는 불티가 많고 넓은 것만).
 * <p>모양: 판 (rules_more BASIC_SLASH.motions) 의 face — up (가로 베기: mirror 면 반대쪽) · front (세로 · 대각: start 도 = 휘두름 시작 쪽 — 90 위,
 * 225 왼쪽 아래) · thrust (창 찌르기 — 곧은 칼빛) · 방패 밀치기 (bash — 앞 충격).
 */
public final class BasicFx {
    private BasicFx() {}

    /** 켜고 끄기 (시험 · 운영) */
    public static boolean enabled = true;
    /** 시험 · 영상: 연출이 나간 평타 수 · 그 가운데 마무리 수 */
    public static int swings;
    public static int finishers;
    /** 시험: 사람마다 마지막 평타 연출이 마무리였는지 (나란히 도는 시험이 섞이지 않게) */
    private static final java.util.Map<java.util.UUID, Boolean> LAST = new java.util.concurrent.ConcurrentHashMap<>();

    public static Boolean lastFinisher(ServerPlayer sp) {
        return sp == null ? null : LAST.get(sp.getUUID());
    }

    /** 2026-11-26 바닐라 파티클 겹 없이 그림 (평타 — BasicSlash 의 땅 충격처럼 이 밖에서 부르는 평타 그림) */
    public static void quiet(Runnable r) {
        WyrdKit.muteIn();
        try {
            r.run();
        } finally {
            WyrdKit.muteOut();
        }
    }

    /**
     * 평타 한 번 (Slash.swing — 요구 조건을 맞춘 근접 무기의 판이 나간 평타): spec = 판 하나 (BASIC_SLASH.motions 의 첫 판), delaySec = 휘두름 시작까지 (판과
     * 같음), radius = 판 반지름 (칸), finisher = 연타의 마지막 타
     */
    public static void swing(ServerPlayer sp, Obj spec, double delaySec, double radius, boolean finisher) {
        if (!enabled || sp == null || spec == null) return;
        PhantomFx.Lib L = lib(sp);
        String life = RuneKit.lifeToken(sp);
        int delay = Math.max(0, (int) Math.round(delaySec * 20));
        swings++;
        if (finisher) finishers++;
        LAST.put(sp.getUUID(), finisher);
        Tasks.later(delay, () -> {
            if (!RuneKit.sameLife(sp, life) || sp.isRemoved()) return;
            // 그림 주인 (1인칭이면 판 대신 눈앞 그림 — RuneKit.enter) · 칠할 묶음 (예전 rfx 이름으로 부르는 그림도 이 묶음으로) — 안의 later 도 이어 받음
            int prevOwner = RuneKit.enter(sp);
            String prevLib = Tasks.lib(L.p());
            WyrdKit.muteIn();
            try {
                draw(sp, L, spec, radius, finisher);
            } catch (RuntimeException e) {
                mmorpg.fabric.Mmorpg.LOG.warn("[mmorpg] basic fx", e);
            } finally {
                WyrdKit.muteOut();
                Tasks.lib(prevLib);
                RuneKit.leave(prevOwner);
            }
        });
    }

    /** 그릴 묶음: 직업 묶음 (PhantomFx.lib — 직업마다 그린 그림), 직업이 없으면 강철 */
    static PhantomFx.Lib lib(ServerPlayer sp) {
        PhantomFx.Lib L = PhantomFx.ctxOf(sp);
        return L != null ? L : PhantomFx.STEEL;
    }

    /**
     * 2026-11-25 베기 그림 (slash · slashm — 2 × 2 조각) 이 없는 묶음 (사제 성광 · 세인트 꽃잎 · 매지션 비전 · 팬텀 아처 — 그 직업 스킬은 베기를 쓰지 않아 그리지
     * 않았음) 은 빛이 가까운 묶음의 베기로: 성광 · 꽃잎 → 금빛 (아스널 킹 묶음), 그 밖 → 강철. CI 45586370783 게임 시험에서 사제 평타마다
     * "vfx sprite el_holy_slashm_00" 경고가 나고 그 평타의 연출이 통째로 빠졌음
     */
    static PhantomFx.Lib slashLib(PhantomFx.Lib L) {
        if (PhantomFx.hasArt(L, "slash_00") && PhantomFx.hasArt(L, "slashm_00")) return L;
        return L == PhantomFx.HOLY || L == PhantomFx.PETALS ? PhantomFx.GOLD : PhantomFx.STEEL;
    }

    /** 곧은 칼빛 (창 찌르기 — 일섬 cut, 없으면 긴 일섬 cutl, 둘 다 없으면 베기 묶음의 것) */
    private static void cut(ServerLevel level, PhantomFx.Lib L, Vec3 from, Vec3 to, int hold, boolean burst, Predicate<ServerPlayer> who) {
        for (PhantomFx.Lib C : new PhantomFx.Lib[] {L, slashLib(L), PhantomFx.STEEL}) {
            if (PhantomFx.hasArt(C, "cut")) {
                PhantomFx.cut(level, C, from, to, hold, burst, who, "cut");
                return;
            }
            if (PhantomFx.hasArt(C, "cutl")) {
                PhantomFx.cut(level, C, from, to, hold, burst, who, "cutl");
                return;
            }
        }
    }

    private static void draw(ServerPlayer sp, PhantomFx.Lib L, Obj spec, double radius, boolean finisher) {
        ServerLevel level = sp.level();
        Predicate<ServerPlayer> hide = PhantomFx.hide1p(sp);
        Vec3 look = sp.getViewVector(1F);
        Vec3 eye = sp.getEyePosition();
        Vec3 f = Kit.facingOf(sp);
        double k = finisher ? 1.4 : 1.0;
        String face = spec.strOr("face", "up");
        Vec3 tip = V.add(eye, look, 1.5 + radius * 0.4);
        if ("thrust".equals(face)) {
            // 창 찌르기: 손에서 앞으로 곧은 칼빛 (reach 칸) · 끝 섬광 · 불티
            Vec3 hand = RuneKit.handOf(sp).hand();
            double reach = spec.numOr("reach", 4.0) * (finisher ? 1.15 : 1.0);
            Vec3 end = V.add(hand, look, reach);
            cut(level, L, hand, end, finisher ? 5 : 3, finisher, hide);
            PhantomFx.flash(level, L, end, 1.2 * k, hide);
            // 2026-11-18 주인의 1인칭 (몸 앞 그림은 1인칭에서 숨김 — 영상 vanilla_motion_lance 에서 1인칭 찌르기가 밋밋): 오른쪽 아래 손 자리에서
            // 화면 가운데 앞으로 곧은 칼빛 · 끝 섬광
            Vec3 up = JobFx.screenUp(look);
            Vec3 right = V.rightOf(V.flat(look));
            Vec3 from = V.add(V.add(V.add(eye, look, 0.9), right, 0.32), up, -0.28);
            cut(level, L, from, V.add(eye, look, 1.0 + reach), finisher ? 4 : 2, finisher, PhantomFx.only1p(sp));
            PhantomFx.flash(level, L, V.add(eye, look, 2.4), 0.8 * k, PhantomFx.only1p(sp));
            return;
        }
        if (spec.has("model") && spec.str("model").equals("slash_bash")) {
            // 방패 밀치기: 앞 충격 · 반짝임
            Vec3 at = V.add(V.up(sp.position(), 1.0), f, 1.6);
            PhantomFx.impact(level, L, at, 0.9 * k);
            return;
        }
        // 2026-11-26 사용자 "모든 평타 때릴때마다 왜 세로 베기 파란색 나오냐 없애고": 직업 묶음 눈앞 베기 (CommonSkills.eyeSlash — 1인칭 눈앞 초승달 ·
        // 남에게는 몸 앞에 세운 반달 판, 강철 묶음이면 파란색) 를 그리지 않음 — 동작과 상관없이 늘 같은 쪽으로 서 있어 가로 베기에도 세로 베기처럼 보였음
        // (CI 45787275345 · 45790897668 영상). 베기 모양은 무기 색 참격 판 (BasicSlash) 이 동작대로. 마무리의 반대 겹 베기도 뺌
        // 2026-11-27 사용자 "연타 마지막에 섬광과 고리 빼버려": 마무리 (연타 마지막 타) 의 칼끝 섬광 · 발밑 충격 고리 (직업 묶음 색) 를 뺌 —
        // 마무리는 불티가 많고 넓은 것만 (내려찍기의 땅 충격 · 바위는 무기 색 판 BasicSlash.impact 그대로)
        PhantomFx.embers(level, L, tip, finisher ? 10 : 5, 0.9 * k, 0.3, 12);
    }
}
