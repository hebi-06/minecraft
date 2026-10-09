package mmorpg.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import mmorpg.core.Rules;
import mmorpg.core.Stats;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

/**
 * 플레이어에 저장하는 RPG 데이터 (애드온의 스코어보드 rpg_str · rpg_dex · rpg_int · rpg_vit · rpg_point · rpg_lvmax +
 * 체력 비율 rpg:hp_ratio). 죽어도 그대로 (copyOnDeath).
 *
 * @param str      힘 (찍은 값)
 * @param dex      민첩
 * @param intel    마력 (스탯 키 "int")
 * @param vit      체력
 * @param point    남은 스탯 포인트
 * @param lvmax    경험치 레벨 최고 기록 (죽어서 레벨이 내려가도 그대로 — 레벨 포인트 · 무기 렙제 · 최대 체력)
 * @param hpRatio  남은 RPG 체력 비율 (0.001 ~ 1, 나갔다 들어와도 이어짐)
 * @param started  처음 접속 처리를 했는지 (시작 포인트 · 스탯 책)
 */
public record RpgData(int str, int dex, int intel, int vit, int point, int lvmax, double hpRatio, boolean started) {
    public static final Codec<RpgData> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.INT.optionalFieldOf("str", 0).forGetter(RpgData::str),
                    Codec.INT.optionalFieldOf("dex", 0).forGetter(RpgData::dex),
                    Codec.INT.optionalFieldOf("int", 0).forGetter(RpgData::intel),
                    Codec.INT.optionalFieldOf("vit", 0).forGetter(RpgData::vit),
                    Codec.INT.optionalFieldOf("point", 0).forGetter(RpgData::point),
                    Codec.INT.optionalFieldOf("lvmax", 0).forGetter(RpgData::lvmax),
                    Codec.DOUBLE.optionalFieldOf("hp_ratio", 1.0).forGetter(RpgData::hpRatio),
                    Codec.BOOL.optionalFieldOf("started", false).forGetter(RpgData::started))
            .apply(i, RpgData::new));

    public static AttachmentType<RpgData> TYPE;

    static void init() {
        TYPE = AttachmentRegistry.create(Mmorpg.id("player"), builder -> builder
                .persistent(CODEC)
                .copyOnDeath()
                .initializer(() -> new RpgData(0, 0, 0, 0, 0, 0, 1.0, false)));
    }

    public static RpgData of(ServerPlayer p) {
        return p.getAttachedOrCreate(TYPE);
    }

    public static void set(ServerPlayer p, RpgData d) {
        p.setAttached(TYPE, d);
    }

    /** 처음 접속 (애드온 setupNewPlayer): 스탯 0 · 시작 포인트 · 최고 레벨 = 지금 레벨 */
    public static RpgData first(int level) {
        int start = (int) Rules.get().obj("CONFIG").num("START_POINTS");
        return new RpgData(0, 0, 0, 0, start, level, 1.0, true);
    }

    /** 스탯 키 (str · dex · int · vit) 의 찍은 값 */
    public int stat(String key) {
        return switch (key) {
            case "str" -> str;
            case "dex" -> dex;
            case "int" -> intel;
            case "vit" -> vit;
            default -> throw new IllegalArgumentException("unknown stat " + key);
        };
    }

    public RpgData withStat(String key, int value) {
        return switch (key) {
            case "str" -> new RpgData(value, dex, intel, vit, point, lvmax, hpRatio, started);
            case "dex" -> new RpgData(str, value, intel, vit, point, lvmax, hpRatio, started);
            case "int" -> new RpgData(str, dex, value, vit, point, lvmax, hpRatio, started);
            case "vit" -> new RpgData(str, dex, intel, value, point, lvmax, hpRatio, started);
            default -> throw new IllegalArgumentException("unknown stat " + key);
        };
    }

    public RpgData withPoint(int value) {
        return new RpgData(str, dex, intel, vit, value, lvmax, hpRatio, started);
    }

    public RpgData withLvmax(int value) {
        return new RpgData(str, dex, intel, vit, point, value, hpRatio, started);
    }

    public RpgData withHpRatio(double value) {
        return new RpgData(str, dex, intel, vit, point, lvmax, value, started);
    }

    /** 규칙 코어에 넘기는 찍은 스탯 {str, dex, int, vit, point, lvmax} (stats.js getBaseStats) */
    public Map<String, Double> base() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("str", (double) str);
        m.put("dex", (double) dex);
        m.put("int", (double) intel);
        m.put("vit", (double) vit);
        m.put("point", (double) point);
        m.put("lvmax", (double) lvmax);
        return m;
    }

    /** 실제 스탯 (장비 보너스 · 룬 버프 — 1단계에는 없음, stats.js getStatsCached) */
    public Map<String, Double> effective() {
        return Stats.effectiveStats(base(), Map.of(), 0);
    }
}
