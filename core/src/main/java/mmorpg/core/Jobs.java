package mmorpg.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 직업 (0.27.0 — 자바 전용, 사용자 "직업 11종 넣을 거임"): 직업마다 무기 · 적용 스탯 · 특성 · 직업 스킬 다섯 (칸 ⇧R · ⇧Z · ⇧V · ⇧B · ⇧Y).
 * 데이터는 {@code /mmorpg/jobs.json}. 순수 계산 (서버 · 클라이언트 · 코어 시험이 같이 씀): 직업 위력 P, 칸 해금 레벨, 지속 보정, 마검사 「마력 검술」
 * 의 요구 · 피해 스탯.
 *
 * <ul>
 * <li>직업 스킬 피해 = P × 배율 × 지속 보정. P = 레벨 기본값 (룬과 같은 {@link Runes#runeLevelBase} — 최고 레벨) × (1 + 적용 스탯 × perPoint),
 * 적용 스탯 = 직업 stats 의 합 (계수 1 — 40렙 의도한 빌드면 모든 직업이 125 → P 42)</li>
 * <li>지속 보정은 룬 (0.26.0 RUNE_SPREAD) 과 같은 단계 · 보너스 — 스킬마다 t (쓴 때부터 마지막 피해까지의 틱) · zone</li>
 * <li>0.31.0 직업 스킬 강화 (upgrade): 스킬마다 0 ~ 5단계, 계수 = 1 + 0.1 × 단계 — 위력 P 와 스킬의 up 키 (회복 · 보호막 · 평타 강화 값 · 지속) 에
 * 곱함. 단계마다 필요한 최고 레벨 · 골드 · 아이템 ({@link #upStep})</li>
 * </ul>
 */
public final class Jobs {
    private Jobs() {}

    public static final String RESOURCE = "/mmorpg/jobs.json";
    /** 칸 수 (⇧R · ⇧Z · ⇧V · ⇧B · ⇧Y) */
    public static final int SLOTS = 5;

    /**
     * 직업 스킬 하나
     *
     * @param slot  칸 (0 ~ 4)
     * @param id    스킬 id (구현이 있는 직업만 — 없으면 "")
     * @param cd    재사용 대기 (초)
     * @param total 과녁 하나의 배율 합 (없으면 0 — 태세 · 회복)
     * @param def   스킬의 정의 그대로 (스킬마다 쓰는 수치)
     */
    public record Skill(int slot, String id, String name, double cd, double total, String desc, Obj def) {
        /** 재사용 대기 (틱) */
        public int cooldownTicks() {
            return (int) Math.round(cd * 20);
        }

        /** 수치 (없으면 fallback) */
        public double num(String key, double fallback) {
            return def.numOr(key, fallback);
        }
    }

    /** 특성 (이름 · 최대 · 풀이 · 정의 그대로) */
    public record Trait(String name, int max, String desc, Obj def) {}

    /**
     * 직업 하나
     *
     * @param weapons 직업 스킬을 쓸 수 있는 무기 종류 (Weapons 의 type)
     * @param stats   적용 스탯 (합)
     * @param rune    전용 룬 계열 (없으면 null)
     * @param ready   스킬이 들어간 직업 (전직 창에서 고를 수 있음)
     * @param scale   검사 대비 직업 스킬 세기
     * @param color   피해 숫자 · 연출 색 두 빛 [[r, g, b], [r, g, b]] (0 ~ 1)
     */
    public record Job(String id, String name, List<String> weapons, List<String> stats, String rune, boolean ready, double scale,
            String role, String line, double[][] color, Trait trait, List<Skill> skills) {
        /** 칸 i 의 스킬 (없으면 null) */
        public Skill skill(int slot) {
            return slot >= 0 && slot < skills.size() ? skills.get(slot) : null;
        }

        /** 스킬 id 로 (없으면 null) */
        public Skill skill(String id) {
            for (Skill s : skills) if (s.id().equals(id)) return s;
            return null;
        }

        /** 이 무기 종류로 직업 스킬을 쓸 수 있는지 */
        public boolean usesWeapon(String type) {
            return type != null && weapons.contains(type);
        }

        /** 무기 이름 ("칼 · 마검") */
        public String weaponText() {
            List<String> names = new ArrayList<>();
            for (String w : weapons) {
                Obj t = Rules.get().obj("TYPES").objOrNull(w);
                names.add(t == null ? w : t.str("name"));
            }
            return String.join(" · ", names);
        }

        /** 적용 스탯 이름 ("힘" · "마력 + 힘") */
        public String statText() {
            List<String> names = new ArrayList<>();
            for (String s : stats) names.add(Stats.info(s).str("name"));
            return String.join(" + ", names);
        }
    }

    /** 0.31.0 강화 재료 하나 (아이템 id — "rpg:loot_troll" · 수) */
    public record UpItem(String id, int count) {}

    /**
     * 0.31.0 강화 한 단계 (그 단계로 올리는 값)
     *
     * @param to    올라가는 단계 (1 ~ max)
     * @param level 필요한 최고 레벨
     * @param gold  골드
     * @param items 아이템 (가방에서)
     */
    public record UpStep(int to, int level, int gold, List<UpItem> items) {}

    /**
     * 0.31.0 강화가 곱하는 스킬 값 하나 (jobs[].skills[].up)
     *
     * @param key   스킬 정의의 값 이름 (heal · shield · echo · dur …)
     * @param name  창 · 툴팁에 쓰는 이름
     * @param fmt   글자 모양: pct (0.35 → 35%) · pctp (0.2 → +20%p) · x (0.93 → ×0.93) · sec (틱 160 → 8초)
     * @param bonus 1 을 넘는 몫만 곱함 (배율 값 — 암영 첫 공격 ×2.5 → 1 + 1.5 × 계수)
     */
    public record UpKey(String key, String name, String fmt, boolean bonus) {}

    private record Upgrade(int max, double per, List<UpStep> steps) {}

    private record Data(int changeLevel, double changeSilver, double perPoint, int[] unlock, List<String> slotRoles, Map<String, Job> jobs, Upgrade upgrade,
            Map<String, List<UpKey>> upKeys) {}

    private static final Data DATA = load();

    // ------------------------------------------------------------------ 데이터

    /** 직업 순서 (전직 창) */
    public static List<String> order() {
        return List.copyOf(DATA.jobs().keySet());
    }

    /** 모든 직업 (순서대로) */
    public static List<Job> all() {
        return List.copyOf(DATA.jobs().values());
    }

    /** 직업 (없으면 null — 빈 id 도 null) */
    public static Job job(String id) {
        return id == null || id.isEmpty() ? null : DATA.jobs().get(id);
    }

    /** 이름 · id 로 직업 찾기 (명령어 — "검사" · "swordsman") */
    public static Job find(String nameOrId) {
        if (nameOrId == null) return null;
        Job j = job(nameOrId.trim());
        if (j != null) return j;
        for (Job x : DATA.jobs().values()) if (x.name().equals(nameOrId.trim())) return x;
        return null;
    }

    /** 전직 레벨 */
    public static int changeLevel() {
        return DATA.changeLevel();
    }

    /** 직업 바꾸기 값 (실버 — 처음 고를 때는 무료) */
    public static double changeSilver() {
        return DATA.changeSilver();
    }

    /** 적용 스탯 1포인트당 위력 */
    public static double perPoint() {
        return DATA.perPoint();
    }

    /** 칸 i 가 열리는 레벨 */
    public static int unlockLevel(int slot) {
        return slot >= 0 && slot < DATA.unlock().length ? DATA.unlock()[slot] : Integer.MAX_VALUE;
    }

    /** 칸 i 가 이 레벨 (최고 레벨) 에서 열렸는지 */
    public static boolean unlocked(int slot, double level) {
        return level >= unlockLevel(slot);
    }

    /** 칸의 역할 이름 (연계기 · 이동 · 방어 · 광역 · 태세 · 강화 · 궁극기) */
    public static String slotRole(int slot) {
        return slot >= 0 && slot < DATA.slotRoles().size() ? DATA.slotRoles().get(slot) : "";
    }

    // ------------------------------------------------------------------ 위력

    /** 직업 적용 스탯 = stats 의 합 (유효 스탯 — 장비 · 룬 버프 포함, 룬과 같음) */
    public static double applied(Job j, Map<String, Double> s) {
        if (j == null) return 0;
        double sum = 0;
        for (String k : j.stats()) sum += Stats.get(s, k);
        return sum;
    }

    /** 직업 위력 P = 레벨 기본값 (최고 레벨 lvmax — 룬과 같음) × (1 + 적용 스탯 × perPoint) */
    public static double power(Job j, Map<String, Double> s) {
        return Runes.runeLevelBase(Stats.get(s, "lvmax")) * (1 + applied(j, s) * perPoint());
    }

    // ------------------------------------------------------------------ 0.26.0 지속 보정 (룬과 같은 규칙)

    private static final Obj SPREAD = Rules.get().root().has("RUNE_SPREAD") ? Rules.get().obj("RUNE_SPREAD") : null;

    /** 지속 보정 단계 (null = 한방 · 표에 t 없음): short · long · zone · zoneLong — {@link Runes#spreadKind} 와 같은 단계 */
    public static String spreadKind(Skill k) {
        if (SPREAD == null || k == null || !k.def().has("t")) return null;
        double t = k.def().num("t");
        if (k.def().truthy("zone")) return t > SPREAD.num("zoneLong") ? "zoneLong" : "zone";
        if (t <= SPREAD.num("instant")) return null;
        return t > SPREAD.num("short") ? "long" : "short";
    }

    /** 지속 보정 배율 (1 + 단계의 bonus — 한방이면 1) */
    public static double spreadMult(Skill k) {
        String kind = spreadKind(k);
        return kind == null ? 1 : 1 + SPREAD.obj("bonus").num(kind);
    }

    /** 한 번 쓸 때 과녁 하나의 피해 (P × 배율 합 × 지속 보정 — 조건 보너스 빼고) */
    public static double totalDamage(Skill k, double power) {
        return k == null ? 0 : power * k.total() * spreadMult(k);
    }

    // ------------------------------------------------------------------ 0.31.0 직업 스킬 강화 (upgrade)

    /** 가장 높은 강화 단계 */
    public static int upMax() {
        return DATA.upgrade().max();
    }

    /** 단계마다 더하는 계수 (0.1 — 룬 돌파 1돌과 같음) */
    public static double upPer() {
        return DATA.upgrade().per();
    }

    /** 단계를 0 ~ max 로 자름 */
    public static int upClamp(int level) {
        return Math.max(0, Math.min(upMax(), level));
    }

    /** 강화 계수 = 1 + per × 단계 (0단계 1 · 5단계 1.5) */
    public static double upMult(int level) {
        return 1 + upPer() * upClamp(level);
    }

    /** 단계 to (1 ~ max) 로 올리는 값 (없으면 null) */
    public static UpStep upStep(int to) {
        List<UpStep> steps = DATA.upgrade().steps();
        return to >= 1 && to <= steps.size() ? steps.get(to - 1) : null;
    }

    /** 단계 to 에 필요한 최고 레벨 (없는 단계는 Integer.MAX_VALUE) */
    public static int upLevel(int to) {
        UpStep s = upStep(to);
        return s == null ? Integer.MAX_VALUE : s.level();
    }

    /** 강화가 곱하는 스킬 값들 (없으면 빈 목록 — 그 스킬은 위력 P 로만 세짐) */
    public static List<UpKey> upKeys(Skill k) {
        if (k == null) return List.of();
        List<UpKey> l = DATA.upKeys().get(k.id());
        return l == null ? List.of() : l;
    }

    /** 이 값이 강화로 곱해지는지 (아니면 null) */
    public static UpKey upKey(Skill k, String key) {
        for (UpKey u : upKeys(k)) if (u.key().equals(key)) return u;
        return null;
    }

    /** 강화 단계 level 의 값: 그 스킬의 up 키면 base × 계수 (bonus 면 1 + (base − 1) × 계수), 아니면 base 그대로 */
    public static double upValue(Skill k, String key, double base, int level) {
        UpKey u = upKey(k, key);
        if (u == null || level <= 0) return base;
        double m = upMult(level);
        return u.bonus() ? 1 + (base - 1) * m : base * m;
    }

    /** 위력 (P) 줄을 보이는지: 피해가 있는 스킬 (total > 0) 이거나 up 키가 없는 스킬 (창벽처럼 위력으로만 세짐) */
    public static boolean upShowsPower(Skill k) {
        return k != null && (k.total() > 0 || upKeys(k).isEmpty());
    }

    /** 값 글자 (UpKey.fmt): pct 35% · pctp +20%p · x ×0.93 · sec 8초 */
    public static String upFormat(UpKey u, double v) {
        return switch (u.fmt()) {
            case "pct" -> Js.str(round(v * 100, Math.abs(v) < 0.1 ? 2 : 1)) + "%"; // 10% 아래는 소수 둘째 (성가 1.65%)
            case "pctp" -> "+" + Js.str(round(v * 100, 1)) + "%p";
            case "sec" -> Js.str(round(Math.round(v) / 20.0, 2)) + "초";
            default -> "×" + Js.str(round(v, 2));
        };
    }

    /** 단계 from → to 에서 바뀌는 것 ("위력 ×1.2 → ×1.3" · "두 번째 베기 42% → 45.5%") — 피해 스킬은 위력 줄이 먼저 */
    public static List<String> upLines(Skill k, int from, int to) {
        List<String> out = new ArrayList<>();
        if (k == null) return out;
        if (upShowsPower(k)) out.add("위력 ×" + Js.str(round(upMult(from), 2)) + " → ×" + Js.str(round(upMult(to), 2)));
        for (UpKey u : upKeys(k)) {
            double base = k.num(u.key(), 0);
            out.add(u.name() + " " + upFormat(u, upValue(k, u.key(), base, from)) + " → " + upFormat(u, upValue(k, u.key(), base, to)));
        }
        return out;
    }

    /** 지금 단계의 것 ("위력 ×1.3" · "두 번째 베기 45.5%") */
    public static List<String> upNow(Skill k, int level) {
        List<String> out = new ArrayList<>();
        if (k == null) return out;
        if (upShowsPower(k)) out.add("위력 ×" + Js.str(round(upMult(level), 2)));
        for (UpKey u : upKeys(k)) out.add(u.name() + " " + upFormat(u, upValue(k, u.key(), k.num(u.key(), 0), level)));
        return out;
    }

    /** 별 (단계만큼 ★, 나머지 ☆ — 다섯 칸) */
    public static String upStars(int level) {
        int n = upClamp(level);
        return "★".repeat(n) + "☆".repeat(Math.max(0, upMax() - n));
    }

    private static double round(double v, int places) {
        double p = Math.pow(10, places);
        return Math.round(v * p + (v >= 0 ? 1e-9 : -1e-9)) / p;
    }

    // ------------------------------------------------------------------ 마검사 「마력 검술」 (특성 magicSword)

    /** 마력 검술이 이 무기 종류에 걸리는지 */
    public static boolean magicSword(Job j, String weaponType) {
        Obj ms = j == null || j.trait() == null ? null : j.trait().def().objOrNull("magicSword");
        if (ms == null || weaponType == null) return false;
        for (Object w : ms.list("weapons")) if (weaponType.equals(w)) return true;
        return false;
    }

    /**
     * 요구 스탯을 따질 때의 스탯 (마력 검술: 칼의 '힘' 을 힘 + 마력으로) — 걸리지 않으면 s 그대로. s 는 바꾸지 않음
     */
    public static Map<String, Double> reqStats(Job j, String weaponType, Map<String, Double> s) {
        return magicSword(j, weaponType) ? shifted(s, j.trait().def().obj("magicSword").obj("req")) : s;
    }

    /** 피해 배율을 셀 때의 스탯 (마력 검술: 칼의 '힘' 을 힘 + 마력 × 0.5 로) — 걸리지 않으면 s 그대로 */
    public static Map<String, Double> damageStats(Job j, String weaponType, Map<String, Double> s) {
        return magicSword(j, weaponType) ? shifted(s, j.trait().def().obj("magicSword").obj("damage")) : s;
    }

    /** {stat: {다른 스탯: 계수}} — stat += Σ 다른 스탯 × 계수 */
    private static Map<String, Double> shifted(Map<String, Double> s, Obj add) {
        Map<String, Double> out = new LinkedHashMap<>(s);
        for (String stat : add.keys()) {
            Obj from = add.obj(stat);
            double v = Stats.get(s, stat);
            for (String k : from.keys()) v += Stats.get(s, k) * from.num(k);
            out.put(stat, v);
        }
        return out;
    }

    // ------------------------------------------------------------------ 글자

    /** 색 (0 ~ 1) → "#rrggbb" */
    public static String hex(double[] c) {
        return String.format(java.util.Locale.ROOT, "#%02x%02x%02x", ch(c[0]), ch(c[1]), ch(c[2]));
    }

    /** 색 (0 ~ 1) → 0xRRGGBB */
    public static int rgb(double[] c) {
        return (ch(c[0]) << 16) | (ch(c[1]) << 8) | ch(c[2]);
    }

    private static int ch(double v) {
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }

    /** 스킬 한 줄 ("연참 · 6초 · 배율 5") */
    public static String skillLine(Skill k) {
        StringBuilder b = new StringBuilder(k.name()).append(" · ").append(Js.str(k.cd())).append("초");
        if (k.total() > 0) b.append(" · 배율 ").append(Js.str(k.total()));
        return b.toString();
    }

    // ------------------------------------------------------------------ 읽기

    private static Data load() {
        Map<String, Object> json;
        try (InputStream in = Jobs.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException(RESOURCE + " is not on the classpath");
            json = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + RESOURCE, e);
        }
        Obj root = new Obj(json, "jobs");
        List<Double> ul = root.numbersOrNull("unlock");
        if (ul == null || ul.size() != SLOTS) throw new IllegalStateException("jobs.unlock must have " + SLOTS + " levels");
        int[] unlock = new int[SLOTS];
        for (int i = 0; i < SLOTS; i++) unlock[i] = (int) Math.round(ul.get(i));
        List<String> roles = new ArrayList<>();
        for (Object o : root.list("slotRoles")) roles.add(String.valueOf(o));
        Map<String, Job> jobs = new LinkedHashMap<>();
        Map<String, List<UpKey>> upKeys = new LinkedHashMap<>();
        Obj js = root.obj("jobs");
        for (Object idObj : root.list("order")) {
            String id = String.valueOf(idObj);
            Obj o = js.obj(id);
            List<String> weapons = new ArrayList<>();
            for (Object w : o.list("weapons")) weapons.add(String.valueOf(w));
            List<String> stats = new ArrayList<>();
            for (Object w : o.list("stats")) stats.add(String.valueOf(w));
            List<Object> colors = o.list("color");
            double[][] color = new double[colors.size()][];
            for (int i = 0; i < colors.size(); i++) {
                List<?> c = (List<?>) colors.get(i);
                color[i] = new double[] {((Number) c.get(0)).doubleValue(), ((Number) c.get(1)).doubleValue(), ((Number) c.get(2)).doubleValue()};
            }
            Obj t = o.objOrNull("trait");
            Trait trait = t == null ? null : new Trait(t.str("name"), (int) t.numOr("max", 0), t.strOr("desc", ""), t);
            List<Skill> skills = new ArrayList<>();
            List<Obj> ss = o.objsOrNull("skills");
            if (ss != null) {
                for (int i = 0; i < ss.size(); i++) {
                    Obj k = ss.get(i);
                    Skill sk = new Skill(i, k.strOr("id", ""), k.str("name"), k.num("cd"), k.numOr("total", 0), k.strOr("desc", ""), k);
                    skills.add(sk);
                    List<Obj> up = k.objsOrNull("up");
                    if (up != null && !sk.id().isEmpty()) {
                        List<UpKey> keys = new ArrayList<>();
                        for (Obj u : up) keys.add(new UpKey(u.str("key"), u.str("name"), u.strOr("fmt", "x"), u.truthy("bonus")));
                        upKeys.put(sk.id(), List.copyOf(keys));
                    }
                }
            }
            jobs.put(id, new Job(id, o.str("name"), List.copyOf(weapons), List.copyOf(stats), o.strOrNull("rune"), o.truthy("ready"), o.numOr("scale", 1),
                    o.strOr("role", ""), o.strOr("line", ""), color, trait, List.copyOf(skills)));
        }
        return new Data((int) root.num("changeLevel"), root.num("changeSilver"), root.num("perPoint"), unlock, List.copyOf(roles), jobs, upgrade(root),
                Map.copyOf(upKeys));
    }

    /** 0.31.0 강화 규칙 (jobs.json upgrade — 없으면 강화 없음: max 0) */
    private static Upgrade upgrade(Obj root) {
        Obj u = root.objOrNull("upgrade");
        if (u == null) return new Upgrade(0, 0, List.of());
        List<Double> levels = u.numbersOrNull("levels");
        List<Obj> ss = u.objsOrNull("steps");
        int max = (int) u.num("max");
        if (levels == null || ss == null || levels.size() != max || ss.size() != max) throw new IllegalStateException("jobs.upgrade needs " + max + " levels · steps");
        List<UpStep> steps = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            Obj s = ss.get(i);
            List<UpItem> items = new ArrayList<>();
            for (Object o : s.list("items")) {
                List<?> pair = (List<?>) o;
                items.add(new UpItem(String.valueOf(pair.get(0)), ((Number) pair.get(1)).intValue()));
            }
            steps.add(new UpStep(i + 1, (int) Math.round(levels.get(i)), (int) s.num("gold"), List.copyOf(items)));
        }
        return new Upgrade(max, u.num("per"), List.copyOf(steps));
    }
}
