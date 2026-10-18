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
        ISSEOM("§f§l§o", "", "!", 2.0F),
        // 0.18.0 대지 가르기: 갈라진 땅 여진 (금빛 — 작게, 자주) · 대검 내려치기 (굵은 금빛 !)
        QUAKE("§6", "", "", 1.4F),
        CLEAVE("§6§l", "", "!", 2.15F);

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

    /** 0.19.0 플레이어가 준 피해가 들어감 (공격자 · 맞은 것 · 실제로 깎인 체력 — 바닐라 피해 · 보스 엔진 모두, 숫자를 띄우기 전) */
    public interface DealtHook {
        void dealt(ServerPlayer attacker, LivingEntity victim, double dealt);
    }

    public static DealtHook dealtHook = (a, v, d) -> {};

    /** 0.20.0 맞은 것의 숫자 앞 표시 (심연의 낙인 — 낙인 찍힌 적은 "◆", 없으면 "") */
    public static java.util.function.Function<LivingEntity, String> victimMark = e -> "";

    private static final class Text {
        final Display.TextDisplay e;
        final double x;
        double y;
        final double z;
        final float scale;
        /** 떠 있는 틱 (LIFE) */
        int life = LIFE;
        int t;

        Text(Display.TextDisplay e, Vec3 p, float scale) {
            this.e = e;
            this.x = p.x;
            this.y = p.y;
            this.z = p.z;
            this.scale = scale;
        }
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
        try {
            dealtHook.dealt(attacker, victim, dealt); // 0.19.0 세계수의 가호: 가호를 받은 사람이 맞히면 맞은 적에게 금빛 잎 (RuneGrove)
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[mmorpg] dealt hook", e);
        }
        if (mark == null) mark = "";
        String vm = victimMark.apply(victim);
        if (vm != null && !vm.isEmpty()) mark = vm + mark;
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
        // 처음엔 크게 → 3틱 동안 원래 크기로 (톡 튀어나오는 느낌)
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
                // 셋째 틱 (클라이언트가 처음 모양을 받은 뒤): 원래 크기로 줄어드는 보간
                interp(a, 3, a.scale);
            }
            a.y += a.t < 6 ? 0.09 : 0.025;
            a.e.setPos(a.x, a.y, a.z);
        }
    }
}
