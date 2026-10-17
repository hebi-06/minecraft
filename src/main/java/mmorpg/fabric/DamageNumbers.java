package mmorpg.fabric;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import mmorpg.core.Js;
import mmorpg.core.Rules;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.mojang.math.Transformation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 피해 숫자 (fx.js showDamageText): 맞은 대상 머리 위에 숫자가 떠올라 0.9초 뒤 사라짐.
 * 자바는 글자 표시 엔티티 (text_display) — 늘 카메라를 보고 (billboard), 밤에도 밝게, 그림자 있는 글자.
 * 18틱 동안 처음 5틱은 빠르게 (0.09칸/틱), 그 뒤 천천히 (0.025) 오름. 한 번에 30개까지 (애드온과 같음).
 * 처음엔 크게 나와 3틱 동안 원래 크기로 줄어듦 (클라이언트 보간). 서버가 꺼질 때 남아 저장된 숫자는 다시 불러오지 않음 (태그 mmorpg_dmg).
 */
public final class DamageNumbers {
    private DamageNumbers() {}

    public static final String TAG = "mmorpg_dmg";
    private static final int MAX_TEXTS = 30;
    /** 내 눈앞 글자 (회복) 는 남의 숫자가 많아도 몇 개 더 */
    private static final int SELF_EXTRA = 6;
    private static final int LIFE = 18;

    /** 숫자 모양 (fx.js TEXT_STYLE) + 글자 크기 (자바: 치명타 · 스킬은 크게) */
    public enum Style {
        NORMAL("§f", "", "", 1.25F),
        CRIT("§6§l", "", "!", 1.6F),
        PLAYER("§c", "-", "", 1.25F),
        SKILL("§b§l", "", "", 1.5F),
        SKILLCRIT("§d§l", "", "!!", 1.75F),
        HEAL("§a§l", "+", "", 1.35F),
        RUNE("§e§l", "", "", 1.5F),
        SPELL("§9§l", "", "", 1.5F),
        SPELLCRIT("§9§l", "", "!!", 1.75F),
        // 0.16.0 한정 초월 룬: 만상귀일의 스탯 구슬 (그 스탯 색) · 귀일 폭발 (무지갯빛 숫자) · 칠성 강림 별 낙하 · 천공의 눈 광선 · 마무리
        STAT_STR("§6§l", "", "", 1.5F),
        STAT_DEX("§a§l", "", "", 1.5F),
        STAT_VIT("§d§l", "", "", 1.5F),
        STAT_INT("§9§l", "", "", 1.5F),
        PRISM("§f§l", "", "", 1.95F) {
            @Override
            public String format(String n) {
                return rainbow(n);
            }
        },
        STARFALL("§e§l", "", "", 1.95F),
        SOLAR("§6§l", "", "", 1.5F),
        JUDGE("§6§l", "", "!", 1.95F),
        // 0.17.0 무간참역: 영역 베기 (흰 기울임 — 작게, 자주) · 마무리 일섬 (굵은 흰 기울임 !)
        CUT("§f§o", "", "", 1.3F),
        ISSEOM("§f§l§o", "", "!", 2.0F);

        final String codes;
        final String before;
        final String after;
        final float scale;

        Style(String codes, String before, String after, float scale) {
            this.codes = codes;
            this.before = before;
            this.after = after;
            this.scale = scale;
        }

        public String format(String n) {
            return codes + before + n + after;
        }
    }

    /** 무지갯빛 글자 (글자마다 색 — 빨강 · 주황 · 노랑 · 초록 · 하늘 · 파랑 · 보라, 굵게) */
    public static String rainbow(String s) {
        String[] cs = {"§c", "§6", "§e", "§a", "§b", "§9", "§d"};
        StringBuilder b = new StringBuilder();
        int k = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isWhitespace(ch)) {
                b.append(ch);
                continue;
            }
            b.append(cs[k++ % cs.length]).append("§l").append(ch);
        }
        return b.toString();
    }

    /** 처음 크기 배율 (톡 튀어나옴) */
    private static final float POP = 1.45F;

    /**
     * 숫자를 띄울 자리 (null 이면 눈 높이 — fx.js showDamageText 의 head). 훈련용 허수아비는 머리 위에 기록이 떠 있어서 몸통 높이 (npc.Dummy 가 채움 —
     * 다른 쪽이 더할 때는 앞의 훅을 이어 부름)
     */
    public static java.util.function.Function<LivingEntity, Vec3> anchor = e -> null;

    private static final class Text {
        final Display.TextDisplay e;
        final double x;
        double y;
        final double z;
        final float scale;
        /** 떠 있는 틱 (기본 LIFE) · 처음 빠르게 오르는 정도 (0 이면 천천히만 — 총 피해) */
        int life = LIFE;
        boolean slow;
        int t;
        /** 총 피해 숫자 (0 보다 크면 0 → total 로 차오름 · 반짝임 — tickTotal) · 색 · 끝에 옅어지며 걷힘 */
        double total;
        Tone tone;
        boolean fade;

        Text(Display.TextDisplay e, Vec3 p, float scale) {
            this.e = e;
            this.x = p.x;
            this.y = p.y;
            this.z = p.z;
            this.scale = scale;
        }
    }

    /**
     * 총 피해 글자 색 (0.17.0 — 무지갯빛 대신 룬 계열 색 하나로 정돈): 숫자는 stops 를 왼쪽 → 오른쪽으로 잇는 그라데이션, 그림자 (글자 아래 1픽셀) 는
     * 계열 색을 어둡게 한 색 (검정 대신 — 은은한 입체감), 위 작은 줄 "── 총 피해 ──" 의 줄 (rule) · 글자 (label) 색. 색은 0xRRGGBB
     */
    public record Tone(int[] stops, int rule, int label, int shadow) {
        private static final double[] W = {1, 1, 1};
        /** 계열 색이 아주 옅을 때 (만상귀일 — 진줏빛): 흰빛 → 옅은 라일락 → 옅은 하늘빛 */
        private static final int[] PEARL = {0xFFFFFF, 0xE6D9FF, 0xCDEBFF};
        /** 기본 (금빛 — W · PEARL 다음에 만듦: 정적 초기화 순서) */
        public static final Tone GOLD = of(new double[] {1, 0.74, 0.25}, new double[] {1, 0.97, 0.85});

        /** 룬 계열 색 (RUNE.color — accent = 첫째 색 · light = 둘째 색) 에서: 숫자 light → accent (흰빛을 30% 섞어 밝게), 그림자 accent × 0.45 */
        public static Tone of(double[] accent, double[] light) {
            double[] l = mix(light, W, 0.3);
            double[] d = mix(accent, W, 0.3);
            double gap = Math.abs(l[0] - d[0]) + Math.abs(l[1] - d[1]) + Math.abs(l[2] - d[2]);
            int[] stops = gap < 0.3 ? PEARL : new int[] {rgb(l), rgb(d)};
            return new Tone(stops, rgb(mix(accent, W, 0.15)), rgb(mix(light, W, 0.15)), rgb(new double[] {accent[0] * 0.45, accent[1] * 0.45, accent[2] * 0.45}));
        }

        /** 그라데이션의 f (0 ~ 1) 자리 색 */
        public int at(double f) {
            if (stops.length == 1) return stops[0];
            double x = Math.max(0, Math.min(1, f)) * (stops.length - 1);
            int i = Math.min(stops.length - 2, (int) Math.floor(x));
            return mixRgb(stops[i], stops[i + 1], x - i);
        }

        private static double[] mix(double[] a, double[] b, double k) {
            return new double[] {a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k};
        }

        private static int rgb(double[] c) {
            int r = (int) Math.round(Math.max(0, Math.min(1, c[0])) * 255);
            int g = (int) Math.round(Math.max(0, Math.min(1, c[1])) * 255);
            int b = (int) Math.round(Math.max(0, Math.min(1, c[2])) * 255);
            return (r << 16) | (g << 8) | b;
        }
    }

    /** 두 색 (0xRRGGBB) 사이 */
    static int mixRgb(int a, int b, double k) {
        int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * k);
        int g = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * k);
        int bl = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * k);
        return (r << 16) | (g << 8) | bl;
    }

    /** 총 피해: 숫자 크기 · 위 줄 크기 · 위 줄 높이 (숫자 위) · 떠 있는 틱 · 차오르는 틱 · 걷히는 틱 · 처음 크기 배율 (작게 나와 커짐) */
    static final float TOTAL_SCALE = 2.6F;
    static final float TOTAL_LABEL_SCALE = 1.15F;
    static final double TOTAL_LABEL_UP = 0.68;
    static final int TOTAL_LIFE = 56;
    static final int TOTAL_COUNT = 14;
    static final int TOTAL_FADE = 8;
    static final float TOTAL_START = 0.6F;

    /** 총 피해 숫자 글자 (굵게 · 그라데이션 · 계열 색 그림자, 쉼표는 가늘게). glint = 반짝이는 글자 자리 (-1 이면 없음) */
    public static Component totalNumber(double value, Tone tone, int glint) {
        String s = String.format(java.util.Locale.ROOT, "%,d", Math.round(Math.max(0, value)));
        MutableComponent c = Component.empty();
        int n = s.length();
        int shadow = 0xFF000000 | tone.shadow();
        for (int i = 0; i < n; i++) {
            char ch = s.charAt(i);
            boolean comma = ch == ',';
            int rgb = tone.at(n <= 1 ? 0 : (double) i / (n - 1));
            if (comma) rgb = tone.at(1);
            else if (glint >= 0 && Math.abs(i - glint) <= 1) rgb = i == glint ? 0xFFFFFF : mixRgb(rgb, 0xFFFFFF, 0.5);
            final int col = rgb;
            c.append(Component.literal(String.valueOf(ch)).withStyle(st -> st.withColor(col).withBold(!comma).withShadowColor(shadow)));
        }
        return c;
    }

    /** 총 피해 위 작은 줄: "── 총 피해 ──" (줄 = 가운데 줄 그은 빈칸 — 이어진 가는 선) */
    public static Component totalLabel(String label, Tone tone) {
        int shadow = 0xFF000000 | tone.shadow();
        MutableComponent c = Component.empty();
        c.append(Component.literal("      ").withStyle(st -> st.withStrikethrough(true).withColor(tone.rule()).withShadowColor(shadow)));
        c.append(Component.literal("  " + label + "  ").withStyle(st -> st.withColor(tone.label()).withShadowColor(shadow)));
        c.append(Component.literal("      ").withStyle(st -> st.withStrikethrough(true).withColor(tone.rule()).withShadowColor(shadow)));
        return c;
    }

    /**
     * 0.16.0 한정 초월 룬: 한 번의 시전이 준 피해의 합을 그 자리에 크게 (2.8초 — 천천히 떠오름). 0 이하면 띄우지 않음.
     * 0.17.0 모양 (사용자 "총 피해 무지개 별로 — 더 세련되게"): 위에 작은 "── 총 피해 ──", 아래 큰 숫자 (룬 계열 색 그라데이션 · 계열 색 그림자) —
     * 작게 나와 커지며 0 에서 합까지 0.7초 동안 차오름 (처음 빠르게 · 끝에 천천히) → 다 차면 톡 커졌다 돌아오고 빛이 왼쪽 → 오른쪽으로 숫자를 훑음
     * → 끝 0.4초 동안 옅어지며 걷힘. 숫자 수 제한 (MAX_TEXTS) 과 따로 더 띄울 수 있음 (연타 숫자가 많아도 마지막 합은 보이게)
     */
    public static void showTotal(ServerLevel level, Vec3 at, double total, String label) {
        showTotal(level, at, total, label, Tone.GOLD);
    }

    public static void showTotal(ServerLevel level, Vec3 at, double total, String label, Tone tone) {
        if (!Rules.get().obj("CONFIG").truthy("DAMAGE_TEXT") || !(total > 0) || ACTIVE.size() >= MAX_TEXTS + SELF_EXTRA) return;
        Tone tn = tone != null ? tone : Tone.GOLD;
        Text num = spawnText(level, at, totalNumber(0, tn, -1), TOTAL_SCALE, TOTAL_START);
        if (num == null) return;
        totalsShown++;
        num.total = total;
        num.tone = tn;
        num.life = TOTAL_LIFE;
        num.slow = true;
        num.fade = true;
        if (label == null || label.isEmpty()) return;
        Text lab = spawnText(level, new Vec3(at.x, at.y + TOTAL_LABEL_UP, at.z), totalLabel(label, tn), TOTAL_LABEL_SCALE, TOTAL_START);
        if (lab == null) return;
        lab.life = TOTAL_LIFE;
        lab.slow = true;
        lab.fade = true;
    }

    /** 총 피해 숫자의 틱 (t = 띄운 뒤 틱): 차오름 → 톡 → 반짝임 (커졌다 돌아온 뒤 — 글자를 바꾸는 동안 크기 보간이 겹치지 않게) */
    private static void tickTotal(Text a) {
        int t = a.t;
        if (t <= TOTAL_COUNT) {
            double x = (double) t / TOTAL_COUNT;
            a.e.setText(totalNumber(t == TOTAL_COUNT ? a.total : a.total * (1 - Math.pow(1 - x, 3)), a.tone, -1));
            if (t == TOTAL_COUNT) interp(a, 2, a.scale * 1.14F);
            return;
        }
        if (t == TOTAL_COUNT + 2) interp(a, 4, a.scale);
        int len = String.format(java.util.Locale.ROOT, "%,d", Math.round(a.total)).length();
        int g = t - (TOTAL_COUNT + 7);
        if (g >= 0 && g <= len + 1) a.e.setText(totalNumber(a.total, a.tone, g <= len ? g : -1));
    }

    /** 크기를 ticks 동안 바꿈 (클라이언트 보간) */
    private static void interp(Text a, int ticks, float scale) {
        a.e.setTransformationInterpolationDelay(0);
        a.e.setTransformationInterpolationDuration(ticks);
        a.e.setTransformation(scaled(scale));
    }

    private static final List<Text> ACTIVE = new ArrayList<>();

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Text a : ACTIVE) if (!a.e.isRemoved()) a.e.discard();
            ACTIVE.clear();
        });
        // 꺼지기 전에 남아 저장된 숫자는 다시 불러오지 않음
        ServerEntityEvents.ALLOW_LOAD.register((entity, level, reason, fromDisk) ->
                !(fromDisk && entity instanceof Display.TextDisplay && entity.entityTags().contains(TAG)));
    }

    /** 시험용: 지금까지 띄운 총 피해 글자 수 (showTotal) */
    public static int totalsShown;

    /** 지금 떠 있는 숫자 수 (시험용) */
    public static int activeCount() {
        return ACTIVE.size();
    }

    /** 100 미만은 소수 첫째 자리까지 (스탯 1포인트 차이도 보이게), 100 이상은 정수 */
    public static String number(double amount) {
        double v = Math.max(0, amount);
        return v >= 100 ? Js.str(Js.round(v)) : Js.str(Js.round(v * 10) / 10);
    }

    /**
     * 몹 · 플레이어가 맞은 뒤 (LivingEntity#actuallyHurt 끝 — 방어구 · 흡수 뒤 실제로 깎인 양): 플레이어가 준 피해만 숫자
     * (combat.js hitFeedback). 치명타로 기억한 공격이면 금색 + 치명타 연출.
     */
    public static void onLivingHurt(LivingEntity victim, DamageSource source, double dealt) {
        if (!(source.getEntity() instanceof ServerPlayer attacker) || attacker == victim || !(dealt > 0)) return;
        feedback(victim, attacker, dealt, RpgCombat.takeMark(victim, attacker), "");
    }

    /**
     * 맞힘 연출 (combat.js hitFeedback): 숫자 (평타 · 치명타 · 스킬 모양) · 치명타 연출 · 무기 맞힘 훅 · 평타에 더한 몫 · 참격.
     * 바닐라 피해 (onLivingHurt) 와 보스 엔진 (바닐라 피해 없이 스크립트가 체력을 줄임 — Bosses) 이 같이 씀.
     * @param m 맞히기 직전에 기억한 공격 (RpgCombat.takeMark — 없으면 null)
     * @param mark 숫자 앞 표시 (보스의 근접 보너스 » · 약점 · 빈틈 …, 없으면 "")
     */
    public static void feedback(LivingEntity victim, ServerPlayer attacker, double dealt, RpgCombat.Mark m, String mark) {
        if (victim == null || attacker == null || attacker == victim || !(dealt > 0)) return;
        if (mark == null) mark = "";
        if (m != null && m.style() != null) {
            // 스킬 · 룬 피해: 그 모양 그대로 (치명타 · 참격 연출은 스킬이 직접 — combat.js hitFeedback)
            show(victim, dealt, m.style(), mark, 0);
            return;
        }
        Style style = Style.NORMAL;
        double shown = dealt;
        double extra = 0;
        if (m != null) {
            RpgCombat.WeaponBonus b = m.bonus();
            // 평타에 더한 피해 (마검 부여 · 평타 강화 룬): 숫자를 평타 몫과 더한 몫으로 나눔 (방어로 줄어든 것은 같은 비율로)
            if (b != null && b.total() > 0) {
                extra = shown * Math.min(1, b.add() / b.total());
                shown -= extra;
            }
            if (b != null) RpgCombat.bonusLanded(victim, attacker, m.tick());
            if (m.w() != null) RpgCombat.weaponHit(attacker, victim, m.w(), b);
            if (m.crit()) {
                style = Style.CRIT;
                Fx.crit(victim);
            }
        }
        if (shown > 0) show(victim, shown, style, mark, 0);
        if (extra > 0) showBonus(victim, extra, m.bonus());
        if (m != null && m.melee() && m.w() != null) {
            Slash.hit(attacker, victim, m.w(), m.crit()); // 참격 맞힘 연출 (slash.js slashHit)
            WeaponTraits.hit(attacker, victim, m.w(), m.crit(), dealt); // 12단계 새 무기: 넓게 베기 · 내려찍기 · 회복
        }
    }

    /** 평타 숫자 위에 더한 몫 (숫자 모양이 같은 것끼리 합쳐 — 마검 부여 = 보라 SPELL, 평타 강화 = 노랑 RUNE) */
    private static void showBonus(LivingEntity victim, double extra, RpgCombat.WeaponBonus b) {
        java.util.Map<Style, double[]> by = new java.util.LinkedHashMap<>();
        java.util.Map<Style, String> marks = new java.util.LinkedHashMap<>();
        for (RpgCombat.Bonus p : b.parts()) {
            Style st = p.style() != null ? p.style() : Style.SPELL;
            by.computeIfAbsent(st, k -> new double[1])[0] += p.add();
            String mk = p.mark() == null ? "" : p.mark();
            String cur = marks.getOrDefault(st, "");
            if (!cur.contains(mk)) marks.put(st, cur + mk);
        }
        double total = 0;
        for (double[] g : by.values()) total += g[0];
        if (by.size() <= 1 || !(total > 0)) {
            Style st = by.isEmpty() ? Style.SPELL : by.keySet().iterator().next();
            show(victim, extra, st, b.mark() == null ? "" : b.mark(), 0.45);
            return;
        }
        double lift = 0.45;
        for (var e : by.entrySet()) {
            show(victim, extra * e.getValue()[0] / total, e.getKey(), marks.getOrDefault(e.getKey(), ""), lift);
            lift += 0.4;
        }
    }

    /**
     * 맞은 대상 머리 위에 숫자 (fx.js showDamageText).
     * @param mark 숫자 앞 표시 (베드락 서식, 보스 근접 보너스 » 등)
     * @param lift 더 높이 띄울 칸 (한 번에 나온 둘째 숫자가 첫 숫자를 가리지 않게)
     */
    public static void show(LivingEntity victim, double amount, Style style, String mark, double lift) {
        if (!Rules.get().obj("CONFIG").truthy("DAMAGE_TEXT")) return;
        boolean self = style == Style.HEAL && victim instanceof Player;
        if (ACTIVE.size() >= MAX_TEXTS + (self ? SELF_EXTRA : 0)) return;
        if (!(victim.level() instanceof ServerLevel level) || victim.isRemoved()) return;
        Vec3 at = anchor.apply(victim);
        Vec3 head = at != null ? at : victim.getEyePosition();
        if (self) {
            // 플레이어가 받은 회복은 얼굴 앞 2칸, 조준점 오른쪽 아래 (머리 위는 1인칭 화면 밖)
            Vec3 v = victim.getViewVector(1F);
            double h = Math.hypot(v.x, v.z);
            if (h < 1e-6) h = 1;
            double fx = v.x / h;
            double fz = v.z / h;
            head = new Vec3(head.x + fx * 2.0 - fz * 0.55, head.y - 0.6, head.z + fz * 2.0 + fx * 0.55);
        }
        RandomSource r = victim.getRandom();
        Vec3 pos = new Vec3(head.x + (r.nextDouble() - 0.5) * 0.7, head.y + 0.3 + lift, head.z + (r.nextDouble() - 0.5) * 0.7);
        spawn(level, pos, BedrockText.toJava(mark + style.format(number(amount))), style.scale);
    }

    /**
     * 플레이어 눈앞에 떠오르는 글자 (fx.js showFaceText: "속박!" · "-3 피의 대가" 등). 보는 방향 수평으로 dist 칸 앞,
     * 오른쪽으로 side 칸, 눈 높이에서 up 칸
     */
    public static void showFace(ServerPlayer sp, String text, double dist, double side, double up) {
        if (!Rules.get().obj("CONFIG").truthy("DAMAGE_TEXT") || ACTIVE.size() >= MAX_TEXTS + SELF_EXTRA || sp.isRemoved()) return;
        Vec3 h = sp.getEyePosition();
        Vec3 v = sp.getViewVector(1F);
        double hz = Math.hypot(v.x, v.z);
        if (hz < 1e-6) hz = 1;
        double fx = v.x / hz;
        double fz = v.z / hz;
        Vec3 pos = new Vec3(h.x + fx * dist - fz * side, h.y + up, h.z + fz * dist + fx * side);
        spawn(sp.level(), pos, BedrockText.toJava(text), 1.25F);
    }

    /** 그 자리에서 떠오르는 글자 (fx.js showTextAt: "+3 경험치" 등) */
    public static void showAt(ServerLevel level, Vec3 at, String text) {
        if (ACTIVE.size() >= MAX_TEXTS) return;
        RandomSource r = level.getRandom();
        spawn(level, new Vec3(at.x + (r.nextDouble() - 0.5) * 0.4, at.y, at.z + (r.nextDouble() - 0.5) * 0.4), BedrockText.toJava(text), 1.25F);
    }

    static Display.TextDisplay spawn(ServerLevel level, Vec3 pos, String text, float scale) {
        Text a = spawnText(level, pos, Component.literal(text), scale, POP);
        return a == null ? null : a.e;
    }

    /** 글자 하나 띄움 (처음 크기 = scale × start — 둘째 틱부터 scale 로 보간) */
    private static Text spawnText(ServerLevel level, Vec3 pos, Component text, float scale, float start) {
        Display.TextDisplay td = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
        if (td == null) return null;
        td.setText(text);
        td.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        td.setBackgroundColor(0);
        // 그림자 + 겹쳐 보임 (베드락 이름표처럼 몸 · 블록에 가리지 않음 — 큰 몬스터 · 보스의 몸통에서 떠오르는 숫자가 처음부터 보이게)
        td.setFlags((byte) (Display.TextDisplay.FLAG_SHADOW | Display.TextDisplay.FLAG_SEE_THROUGH));
        td.setBrightnessOverride(Brightness.FULL_BRIGHT); // 밤 · 동굴에서도 밝게
        td.setPosRotInterpolationDuration(1); // 틱마다 옮기는 것을 부드럽게
        // 처음엔 크게 (총 피해는 작게) → 3틱 동안 원래 크기로 (톡 튀어나오는 느낌)
        td.setTransformation(scaled(scale * start));
        td.snapTo(pos.x, pos.y, pos.z, 0F, 0F);
        td.addTag(TAG);
        Text a = new Text(td, pos, scale);
        ACTIVE.add(a);
        if (!level.addFreshEntity(td)) {
            ACTIVE.remove(a);
            return null;
        }
        return a;
    }

    /** 크기만 바꾼 변환 */
    static Transformation scaled(float s) {
        return new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf());
    }

    private static void tick() {
        for (Iterator<Text> it = ACTIVE.iterator(); it.hasNext(); ) {
            Text a = it.next();
            a.t++;
            if (a.e.isRemoved() || a.t > a.life) {
                if (!a.e.isRemoved()) a.e.discard();
                it.remove();
                continue;
            }
            if (a.t == 2) {
                // 셋째 틱 (클라이언트가 처음 모양을 받은 뒤): 원래 크기로 줄어드는 (총 피해는 커지는) 보간
                interp(a, 3, a.scale);
            }
            if (a.total > 0) tickTotal(a);
            if (a.fade && a.t == a.life - TOTAL_FADE) {
                // 끝: 옅어지며 걷힘 (글자 불투명도도 같은 보간)
                a.e.setTransformationInterpolationDelay(0);
                a.e.setTransformationInterpolationDuration(TOTAL_FADE);
                a.e.setTextOpacity((byte) 24);
            }
            a.y += a.slow ? 0.015 : a.t < 6 ? 0.09 : 0.025;
            a.e.setPos(a.x, a.y, a.z);
        }
    }
}
