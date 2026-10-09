package mmorpg.fabric;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import mmorpg.core.Js;
import mmorpg.core.Rules;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 피해 숫자 (fx.js showDamageText): 맞은 대상 머리 위에 숫자가 떠올라 0.9초 뒤 사라짐.
 * 자바는 글자 표시 엔티티 (text_display) — 늘 카메라를 보고 (billboard), 밤에도 밝게, 그림자 있는 글자.
 * 18틱 동안 처음 5틱은 빠르게 (0.09칸/틱), 그 뒤 천천히 (0.025) 오름. 한 번에 30개까지 (애드온과 같음).
 * 서버가 꺼질 때 남은 숫자는 다음에 그 청크가 불러와질 때 지움 (태그 mmorpg_dmg).
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
        SPELLCRIT("§9§l", "", "!!", 1.75F);

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

    private static final class Text {
        final Display.TextDisplay e;
        final double x;
        double y;
        final double z;
        int t;

        Text(Display.TextDisplay e, Vec3 p) {
            this.e = e;
            this.x = p.x;
            this.y = p.y;
            this.z = p.z;
        }
    }

    private static final List<Text> ACTIVE = new ArrayList<>();

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Text a : ACTIVE) if (!a.e.isRemoved()) a.e.discard();
            ACTIVE.clear();
        });
        // 꺼지기 전에 남은 숫자 (저장된 것) 는 불러와질 때 지움
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Display.TextDisplay && entity.getTags().contains(TAG) && !isActive(entity)) Tasks.later(1, entity::discard);
        });
    }

    /** 지금 떠 있는 숫자 수 (시험용) */
    public static int activeCount() {
        return ACTIVE.size();
    }

    static boolean isActive(Entity e) {
        for (Text a : ACTIVE) if (a.e == e) return true;
        return false;
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
        RpgCombat.Mark m = RpgCombat.takeMark(victim, attacker);
        Style style = Style.NORMAL;
        if (m != null && m.crit()) {
            style = Style.CRIT;
            Fx.crit(victim);
        }
        show(victim, dealt, style, "", 0);
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
        Vec3 head = victim.getEyePosition();
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

    /** 그 자리에서 떠오르는 글자 (fx.js showTextAt: "+3 경험치" 등) */
    public static void showAt(ServerLevel level, Vec3 at, String text) {
        if (ACTIVE.size() >= MAX_TEXTS) return;
        RandomSource r = level.getRandom();
        spawn(level, new Vec3(at.x + (r.nextDouble() - 0.5) * 0.4, at.y, at.z + (r.nextDouble() - 0.5) * 0.4), BedrockText.toJava(text), 1.25F);
    }

    static Display.TextDisplay spawn(ServerLevel level, Vec3 pos, String text, float scale) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:text_display");
        tag.putString("text", Component.Serializer.toJson(Component.literal(text), level.registryAccess()));
        tag.putString("billboard", "center");
        tag.putString("alignment", "center"); // 없으면 바닐라가 "Display entityNot a string" 오류를 남김
        tag.putInt("background", 0);
        tag.putBoolean("shadow", true);
        tag.putInt("teleport_duration", 1);
        CompoundTag light = new CompoundTag();
        light.putInt("sky", 15);
        light.putInt("block", 15);
        tag.put("brightness", light);
        CompoundTag tf = new CompoundTag();
        tf.put("translation", floats(0, 0, 0));
        tf.put("left_rotation", floats(0, 0, 0, 1));
        tf.put("scale", floats(scale, scale, scale));
        tf.put("right_rotation", floats(0, 0, 0, 1));
        tag.put("transformation", tf);
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(TAG));
        tag.put("Tags", tags);
        Entity e = EntityType.loadEntityRecursive(tag, level, ent -> {
            ent.moveTo(pos.x, pos.y, pos.z, 0F, 0F);
            return ent;
        });
        if (!(e instanceof Display.TextDisplay td)) return null;
        Text a = new Text(td, pos);
        ACTIVE.add(a); // 불러오기 정리 (ENTITY_LOAD) 가 지우지 않게 먼저
        if (!level.addFreshEntity(td)) {
            ACTIVE.remove(a);
            return null;
        }
        return td;
    }

    private static ListTag floats(float... v) {
        ListTag l = new ListTag();
        for (float x : v) l.add(FloatTag.valueOf(x));
        return l;
    }

    private static void tick() {
        for (Iterator<Text> it = ACTIVE.iterator(); it.hasNext(); ) {
            Text a = it.next();
            a.t++;
            if (a.e.isRemoved() || a.t > LIFE) {
                if (!a.e.isRemoved()) a.e.discard();
                it.remove();
                continue;
            }
            a.y += a.t < 6 ? 0.09 : 0.025;
            a.e.setPos(a.x, a.y, a.z);
        }
    }
}
