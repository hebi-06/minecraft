package mmorpg.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 0.38.0 동작 규칙 (rules_more.json MOTION — 서버 · 클라이언트가 같이 씀): 무기 종류마다 평타 동작 차례 (연타 단계), 회전 베기 둘레 판정,
 * 쉬프트 두 번 구르기 (거리 · 틱 · 재사용 대기 · 두 번 누름 간격), 스킬 → 동작 (무기 스킬 id · 직업 스킬 id · rune:룬 fn).
 * 동작의 모양 (열쇠 자세) 은 클라이언트 assets/mmorpg/motion/poses.json.
 */
public final class Motions {
    private Motions() {}

    /** 동작 이름 전부 (poses.json 과 같아야 함 — 시험이 확인) */
    public static final Set<String> KNOWN = Set.of("slash_h", "slash_v", "spin", "dagger_x", "chop", "thrust", "bash", "smite", "skill_slash", "skill_flurry",
            "slam", "leap_slam", "dash", "throw", "throw_weapon", "cast", "cast_up", "guard", "shoot", "shoot_up", "spin2", "roll");

    private static Obj cfg() {
        return Rules.get().obj("MOTION");
    }

    /** 무기 종류의 평타 동작 차례 (없으면 빈 목록 — 바닐라 휘두르기) */
    public static List<String> combo(String weaponType) {
        Obj c = cfg().objOrNull("combo");
        if (c == null || weaponType == null || !c.has(weaponType)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object o : c.list(weaponType)) out.add(String.valueOf(o));
        return Collections.unmodifiableList(out);
    }

    /** 다음 평타가 이어지는 틱 수 */
    public static int comboWindow() {
        return (int) cfg().numOr("comboWindow", 24);
    }

    /**
     * 다음 평타의 연타 단계 (1 ~ size): 앞 평타 (단계 lastStep · 틱 lastTick) 에서 comboWindow 틱 안이면 다음 단계 (끝이면 1), 아니면 1.
     * lastStep 0 = 앞 평타 없음.
     */
    public static int nextStep(int lastStep, long lastTick, long now, int size) {
        if (size <= 0) return 0;
        if (lastStep <= 0 || now - lastTick > comboWindow() || now < lastTick) return 1;
        return lastStep % size + 1;
    }

    // ------------------------------------------------------------ 0.39.0 평타 사이 · 참격 범위

    /** 평타 사이 틱 (공격 대기 대신 — 이보다 빨리 누르면 선입력: 앞 동작이 끝나는 순간 나감, 매번 온전한 피해) */
    public static int attackInterval() {
        return Math.max(1, (int) cfg().numOr("interval", 10));
    }

    /** 서버가 받는 평타 사이 틱 (클라이언트 · 서버 틱이 조금 어긋나도 받게 intervalSlack 만큼 여유) */
    public static int serverInterval() {
        return Math.max(1, attackInterval() - (int) cfg().numOr("intervalSlack", 2));
    }

    private static Obj areaCfg() {
        Obj a = cfg().objOrNull("area");
        return a != null ? a : new Obj(new java.util.LinkedHashMap<>(), "MOTION.area");
    }

    /** 참격 범위 피해를 쓰는지 (끄면 예전대로 — 회전 베기 · 낫 넓게 베기만 다른 적에게) */
    public static boolean areaEnabled() {
        return areaCfg().truthy("enabled");
    }

    /** 한 번 휘두를 때 범위로 맞는 적 수 상한 (조준한 적 빼고) */
    public static int areaMax() {
        return (int) areaCfg().numOr("max", 6);
    }

    /**
     * 참격 범위: shape = arc (앞 부채꼴) · band (앞 띠) · line (앞 줄) · ring (둘레), range 칸, angle 도 (arc), width 칸 (band · line), height 칸
     * (arc · band 는 보는 방향 기준 위아래 폭, ring 은 발에서 위로)
     */
    public record Area(String shape, double range, double angle, double width, double height) {}

    /** 이 동작 · 무기 종류의 참격 범위 (무기 종류 배율 scale 를 range · angle · width 에 곱함 — 등급 모름: reach 표는 보통 등급). 없는 동작이면 null */
    public static Area area(String motion, String weaponType) {
        return area(motion, weaponType, 0);
    }

    /**
     * 이 동작 · 무기 종류 · 등급 (0 보통 ~ 4 초월) 의 참격 범위: shapes 의 모양 · 폭 (scale 배율), 거리는 0.40.0 reach 표 (무기 종류마다 등급별 — 참격 궤적이
     * 닿는 거리) 가 있으면 그것, 없으면 shapes 의 range × scale. 없는 동작이면 null
     */
    public static Area area(String motion, String weaponType, int grade) {
        Obj shapes = areaCfg().objOrNull("shapes");
        if (shapes == null || motion == null || !shapes.has(motion)) return null;
        Obj a = shapes.obj(motion);
        Obj sc = areaCfg().objOrNull("scale");
        double k = sc != null && weaponType != null ? sc.numOr(weaponType, 1) : 1;
        double range = a.num("range") * k;
        Double r = reach(weaponType, grade);
        if (r != null) range = r;
        return new Area(a.str("shape"), range, Math.min(360, a.numOr("angle", 0) * k), a.numOr("width", 0) * k, a.numOr("height", 2));
    }

    /** 0.40.0 참격 궤적이 닿는 거리 (MOTION.area.reach — 무기 종류마다 [보통 … 초월], 등급이 표 밖이면 가장 가까운 칸). 없으면 null */
    public static Double reach(String weaponType, int grade) {
        Obj r = areaCfg().objOrNull("reach");
        if (r == null || weaponType == null || !r.has(weaponType)) return null;
        java.util.List<Object> row = r.list(weaponType);
        if (row.isEmpty()) return null;
        int i = Math.max(0, Math.min(row.size() - 1, grade));
        return ((Number) row.get(i)).doubleValue();
    }

    // ------------------------------------------------------------ 0.40.0 참격 궤적 (rules_more TRAIL — 그리기는 클라이언트 BladeTrails)

    private static Obj trailCfg() {
        return Rules.get().root().objOrNull("TRAIL");
    }

    /** 참격 궤적을 쓰는지 */
    public static boolean trailEnabled() {
        Obj t = trailCfg();
        return t != null && t.truthy("enabled");
    }

    /** 이 평타 동작은 궤적이 참격을 대신하는지 (서버가 예전 눈앞 초승달 참격을 그리지 않음) */
    public static boolean trailBasic(String motion) {
        Obj t = trailCfg();
        if (t == null || motion == null || !t.truthy("enabled") || !t.has("basic")) return false;
        for (Object o : t.list("basic")) if (motion.equals(String.valueOf(o))) return true;
        return false;
    }

    /**
     * 점이 범위 안인지 (순수 계산 — 시험 가능): arc · band · line 은 눈 기준 보는 방향의 앞 fwd · 오른쪽 lat · 위 up (칸),
     * ring 은 발 기준 가로 (fwd · lat) · 높이 up. pad = 맞는 쪽 몸의 반폭 (그만큼 넓혀 셈)
     */
    public static boolean inArea(Area a, double fwd, double lat, double up, double pad) {
        if (a == null) return false;
        double p = Math.max(0, pad);
        switch (a.shape()) {
            case "arc" -> {
                double d = Math.hypot(fwd, lat);
                if (d - p > a.range() || fwd < -p || Math.abs(up) > a.height() / 2 + p) return false;
                if (d <= p) return true;
                double half = Math.toRadians(a.angle() / 2) + Math.asin(Math.min(1, p / d));
                return Math.abs(Math.atan2(lat, fwd)) <= half;
            }
            case "band" -> {
                return fwd >= -p && fwd <= a.range() + p && Math.abs(lat) <= a.width() / 2 + p && Math.abs(up) <= a.height() / 2 + p;
            }
            case "line" -> {
                return fwd >= -p && fwd <= a.range() + p && Math.hypot(lat, up) <= a.width() / 2 + p;
            }
            case "ring" -> {
                return Math.hypot(fwd, lat) - p <= a.range() && up >= -1 - p && up <= a.height() + p;
            }
            default -> {
                return false;
            }
        }
    }

    /** 회전 베기가 둘레 적에게 주는 몫 (맞힌 피해 ×) */
    public static double spinRatio() {
        return cfg().obj("spin").numOr("ratio", 0.4);
    }

    /** 회전 베기 둘레 (칸) */
    public static double spinRange() {
        return cfg().obj("spin").numOr("range", 3);
    }

    private static Obj roll() {
        return cfg().obj("roll");
    }

    public static int rollTicks() {
        return (int) roll().numOr("ticks", 10);
    }

    public static double rollDistance() {
        return roll().numOr("distance", 4);
    }

    public static int rollCooldown() {
        return (int) roll().numOr("cooldown", 20);
    }

    /** 쉬프트를 두 번 누른 것으로 보는 간격 (틱) */
    public static int doubleTap() {
        return (int) roll().numOr("doubleTap", 6);
    }

    /**
     * 구르기 틱마다 빠르기 (칸/틱 — 합 = rollDistance): 처음이 빠르고 끝으로 갈수록 느려짐 ((1 - i / n)^0.6 꼴).
     */
    public static double[] rollSpeeds() {
        int n = Math.max(1, rollTicks());
        double[] v = new double[n];
        double sum = 0;
        for (int i = 0; i < n; i++) {
            v[i] = Math.pow(1 - (i + 0.5) / n, 0.6);
            sum += v[i];
        }
        double k = rollDistance() / sum;
        for (int i = 0; i < n; i++) v[i] *= k;
        return v;
    }

    /** 스킬 → 동작 (key = 무기 스킬 id · 직업 스킬 id · "rune:" + 룬 fn). 없으면 null */
    public static String skillMotion(String key) {
        Obj s = cfg().objOrNull("skills");
        if (s == null || key == null) return null;
        String m = s.strOrNull(key);
        return m != null && KNOWN.contains(m) ? m : null;
    }

    /** 스킬 표의 열쇠 전부 (시험) */
    public static Set<String> skillKeys() {
        Obj s = cfg().objOrNull("skills");
        return s == null ? Set.of() : s.keys();
    }
}
