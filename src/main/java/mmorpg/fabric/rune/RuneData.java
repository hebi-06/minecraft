package mmorpg.fabric.rune;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Runes;
import mmorpg.fabric.Mmorpg;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

/**
 * 플레이어에 저장하는 룬 데이터 (애드온의 동적 속성 rpg:rune_quick · rpg:rune_pending). 죽어도 그대로.
 *
 * @param quick   룬 칸을 고르면 바로 쓰기: "auto" (자바에는 터치가 없어 꺼짐) · "on" · "off"
 * @param pending 뽑았지만 아직 받지 않은 룬 ID (뽑기 연출 중에 나가면 다시 들어올 때 받음)
 */
public record RuneData(String quick, List<String> pending) {
    public static final Codec<RuneData> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Codec.STRING.optionalFieldOf("quick", "auto").forGetter(RuneData::quick),
                    Codec.STRING.listOf().optionalFieldOf("pending", List.of()).forGetter(RuneData::pending))
            .apply(i, RuneData::new));

    public static AttachmentType<RuneData> TYPE;

    static void init() {
        TYPE = AttachmentRegistry.create(Mmorpg.id("runes"), builder -> builder
                .persistent(CODEC)
                .copyOnDeath()
                .initializer(() -> new RuneData("auto", List.of())));
    }

    public static RuneData of(ServerPlayer p) {
        return p.getAttachedOrCreate(TYPE);
    }

    /** 바로 쓰기 설정 (auto · on · off — 그 밖은 auto) */
    public static void setQuick(ServerPlayer p, String mode) {
        RuneData d = of(p);
        String m = "on".equals(mode) || "off".equals(mode) ? mode : "auto";
        p.setAttached(TYPE, new RuneData(m, d.pending()));
    }

    /** 받지 않은 룬 (있는 룬 ID 만) */
    public static List<String> pending(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        for (String id : of(p).pending()) if (Runes.parseRune(id) != null) out.add(id);
        return out;
    }

    public static void setPending(ServerPlayer p, List<String> list) {
        p.setAttached(TYPE, new RuneData(of(p).quick(), List.copyOf(list)));
    }
}
