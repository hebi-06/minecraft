package mmorpg.fabric.rune;

import java.util.List;
import java.util.function.Predicate;
import mmorpg.core.Runes;
import mmorpg.core.Rules;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.vfx.PBridge;
import mmorpg.fabric.vfx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 2026-11-27 룬 시전 서명 (사용자 "룬 쓸때 룬 글자 띄울거임" — 룬 재개발안의 "시전 서명"): 룬을 쓰면 그 룬의 글자가 번쩍. 글자 · 색 = 룬 아이템 그림과 같음
 * (엘더 푸타르크 · 묶음 글자 — tools/jobvfx/runesign.py, 그림판 rn_sign · 위르드식 도트). 한정 룬 다섯은 그 룬의 글자를 따로 정함.
 * <ul>
 * <li>남 (쓴 사람이 3인칭이면 그 사람도): 머리 위 (이름표 위) 에 카메라를 보는 글자 · 고리 — 흰 번쩍 → 커졌다 자리 잡음 → 고리가 번갈아 돌며 빛남 → 텍셀이 빠지며
 * 위로 흩어짐. 몸을 따라감 (돌진 · 순간 이동 룬)</li>
 * <li>쓴 사람의 1인칭: 화면 가운데 아래 (조준점 아래 — 가운데의 적 · 피해 숫자를 가리지 않게) 에 작게, 카메라를 따라감</li>
 * <li>때: 룬이 나간 틱 (시전 시간이 있는 룬 · 이어 쓰는 룬은 쓰기 시작한 틱) — 피해 · 재사용 대기 · 효과와 무관 (그림만)</li>
 * </ul>
 * 칸 차례 = rules RUNE_FAMILY_ORDER (룬 30) 다음 RUNE_LIMITED_ORDER (한정 5), 룬마다 {@link #FRAMES} 칸 (0 번쩍 · 1 · 2 빛남 · 3 · 4 흩어짐).
 */
public final class RuneSign {
    private RuneSign() {}

    static final String ART = "rn_sign";
    public static final int FRAMES = 5;
    /** 끄기 (영상 견주기 · 시험) */
    public static volatile boolean enabled = true;
    /** 머리 위: 그림 높이 (칸 — 고리 지름은 그 0.8) · 눈에서 그림 가운데까지 (이름표 위) */
    static final double HEAD_SIZE = 1.0;
    static final double HEAD_LIFT = 1.2;
    /** 1인칭: 그림 높이 · 눈에서 앞 · 조준점 아래 (칸) */
    static final double FP_SIZE = 0.36;
    static final double FP_AHEAD = 2.0;
    static final double FP_DOWN = 0.46;
    /** 틱: 커짐 끝 · 흩어짐 시작 (3) · 흩어짐 둘째 (4) · 끝 */
    static final int POP = 3;
    static final int FADE_A = 11;
    static final int FADE_B = 14;
    static final int LIFE = 17;

    /** 시험 · 영상 기록 */
    public static int shown;
    public static String lastFamily = "";
    public static int lastCell = -1;

    /** 룬 계열 → 그림판의 룬 차례 (rules RUNE_FAMILY_ORDER 다음 RUNE_LIMITED_ORDER — tools/jobvfx/runesign.py 와 같은 차례). 없으면 -1 (숨긴 옛 계열) */
    public static int cellOf(String family) {
        if (family == null) return -1;
        mmorpg.core.Obj root = Rules.get().root();
        List<Object> fam = root.listOrNull("RUNE_FAMILY_ORDER");
        List<Object> lim = root.listOrNull("RUNE_LIMITED_ORDER");
        int n = fam == null ? 0 : fam.size();
        int i = fam == null ? -1 : fam.indexOf(family);
        if (i >= 0) return i;
        int j = lim == null ? -1 : lim.indexOf(family);
        return j >= 0 ? n + j : -1;
    }

    /** 룬이 나감 (RuneCast.castRune): 그 룬의 글자를 띄움 */
    public static void show(ServerPlayer sp, Runes.Rune r) {
        if (!enabled || sp == null || r == null || sp.isRemoved() || !Vfx.hasSheet(ART)) return;
        try {
            int cell = cellOf(r.family());
            if (cell < 0) return;
            int base = cell * FRAMES;
            if (base + FRAMES > Vfx.sheet(ART).frames()) return;
            ServerLevel level = sp.level();
            int id = sp.getId();
            // 남 · 3인칭인 쓴 사람 = 머리 위, 1인칭인 쓴 사람 = 화면 가운데 아래
            Predicate<ServerPlayer> others = p -> !(p.getId() == id && !PBridge.thirdPersonOf(p));
            Predicate<ServerPlayer> mine = (Vfx.FirstPerson) p -> p.getId() == id && !PBridge.thirdPersonOf(p);
            play(level, sp, base, others, false);
            play(level, sp, base, mine, true);
            shown++;
            lastFamily = r.family();
            lastCell = cell;
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[mmorpg] rune sign {}", r.id(), e);
        }
    }

    /** 그림 자리: 머리 위 (rise = 흩어지며 올라감) · 1인칭은 눈 앞 조준점 아래 (화면 위쪽 = 카메라 위) */
    static Vec3 spot(ServerPlayer sp, boolean fp, double rise) {
        if (!fp) return RuneKit.overHead(sp, HEAD_LIFT + rise);
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getViewVector(1F);
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        Vec3 up = right.normalize().cross(look).normalize();
        return eye.add(look.scale(FP_AHEAD)).add(up.scale(-FP_DOWN + rise));
    }

    private static void play(ServerLevel level, ServerPlayer sp, int base, Predicate<ServerPlayer> who, boolean fp) {
        double s = fp ? FP_SIZE : HEAD_SIZE;
        Vec3 at = spot(sp, fp, 0);
        Vfx.Fx fx = Vfx.at(level, at, LIFE + 2, who);
        Vfx.Sprite sign = fx.sprite(ART, at).frame(base).size(s * 0.72);
        String life = RuneKit.lifeToken(sp);
        int[] shownFrame = {base};
        fx.each(1, LIFE, 1, t -> {
            if (sign.gone()) return;
            if (sp.isRemoved() || !RuneKit.sameLife(sp, life)) {
                sign.remove();
                return;
            }
            // 0 번쩍 (1틱) → 1 · 2 번갈아 (고리가 돎) → 3 · 4 흩어짐
            int f = t >= FADE_B ? base + 4 : t >= FADE_A ? base + 3 : base + 1 + ((t / 2) % 2);
            if (f != shownFrame[0]) {
                shownFrame[0] = f;
                sign.frame(f);
            }
            double size = t < POP ? s * 1.14 : s;
            double rise = t >= FADE_A ? (t - FADE_A + 1) * (fp ? 0.012 : 0.06) : 0;
            sign.to(spot(sp, fp, rise), size, 1);
        });
        sign.until(LIFE + 1);
    }
}
