package mmorpg.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * 이펙트 그림 파티클 하나 (애드온의 rpg:arrow_shot · arrow_trail · arrow_spark · magic_circle · magic_circle_flash 와 같은 변수).
 * 서버가 sendParticles 로 보내면 클라이언트 (mmorpg.fabric.client.FxParticle) 가 애드온 그림으로 그림.
 * 시간 (life · delay) 은 초, 속도는 초당 블록, 크기는 반 크기 (블록) — 애드온 값 그대로.
 *
 * @param kind     그림 종류 (아래 상수)
 * @param sprite   kind = SPRITE 일 때 FxSprite 순번 (참격 · 충격파 …)
 * @param dir      방향 (빛줄기의 길이 방향 · 움직이는 방향)
 * @param speed    dir 쪽으로 초당 블록
 * @param size     반 크기 (빛줄기는 반 길이)
 * @param thick    빛줄기의 반 두께
 * @param life     수명 (초)
 * @param delay    처음에 보이지 않는 시간 (초, 지나간 자리 선)
 * @param grow     불꽃: 끝날 때 크기 배율 (0 이면 줄어들며 사라짐)
 * @param spin     처음 돌린 각 (도)
 * @param progress 마법진 그려진 정도 (0~1 → 10단계)
 * @param color    ARGB (그림은 흰색, 이 색을 곱함)
 * @param accel    위로 가속 (초당² 블록, 빛 알갱이)
 * @param drag     끌림 (초당 감속 계수, 빛 알갱이 — 애드온 linear_drag_coefficient)
 */
public record FxOptions(int kind, int sprite, Vec3 dir, float speed, float size, float thick, float life, float delay, float grow, float spin,
        float progress, int color, float accel, float drag) implements ParticleOptions {
    public static final int ARROW_SHOT = 0;
    public static final int ARROW_TRAIL = 1;
    public static final int ARROW_SPARK = 2;
    public static final int MAGIC_CIRCLE = 3;
    public static final int MAGIC_FLASH = 4;
    /** 일반 그림 (FxSprite: 참격 · X · 충격파 · 방패 고리 · 폭발) — 크기 · 움직임 · 사라짐은 FxSprite 에 */
    public static final int SPRITE = 5;
    /**
     * 무기 빛 알갱이 (애드온 영웅 · 초월 무기의 fx — 클라이언트가 직접 뿌림): 처음 속도 dir × speed, 위로 accel 가속 · drag 끌림,
     * 반 크기 size 에서 수명 동안 줄어듦, 카메라를 향함. sprite = 그림 (GLINT_TEXTURES 순번 — 바닐라 파티클 그림)
     */
    public static final int GLINT = 6;
    public static final String[] GLINT_TEXTURES = {"generic_0", "flame"};

    /** 끌림 · 가속 없는 그림 (빛줄기 · 불꽃 · 마법진 · 일반 그림) */
    public FxOptions(int kind, int sprite, Vec3 dir, float speed, float size, float thick, float life, float delay, float grow, float spin,
            float progress, int color) {
        this(kind, sprite, dir, speed, size, thick, life, delay, grow, spin, progress, color, 0F, 0F);
    }

    public static final MapCodec<FxOptions> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("kind").forGetter(FxOptions::kind),
            Codec.INT.optionalFieldOf("sprite", -1).forGetter(FxOptions::sprite),
            Vec3.CODEC.optionalFieldOf("dir", Vec3.ZERO).forGetter(FxOptions::dir),
            Codec.FLOAT.optionalFieldOf("speed", 0F).forGetter(FxOptions::speed),
            Codec.FLOAT.optionalFieldOf("size", 0.5F).forGetter(FxOptions::size),
            Codec.FLOAT.optionalFieldOf("thick", 0F).forGetter(FxOptions::thick),
            Codec.FLOAT.optionalFieldOf("life", 0.5F).forGetter(FxOptions::life),
            Codec.FLOAT.optionalFieldOf("delay", 0F).forGetter(FxOptions::delay),
            Codec.FLOAT.optionalFieldOf("grow", 0F).forGetter(FxOptions::grow),
            Codec.FLOAT.optionalFieldOf("spin", 0F).forGetter(FxOptions::spin),
            Codec.FLOAT.optionalFieldOf("progress", 1F).forGetter(FxOptions::progress),
            Codec.INT.optionalFieldOf("color", -1).forGetter(FxOptions::color),
            Codec.FLOAT.optionalFieldOf("accel", 0F).forGetter(FxOptions::accel),
            Codec.FLOAT.optionalFieldOf("drag", 0F).forGetter(FxOptions::drag)).apply(i, FxOptions::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FxOptions> STREAM_CODEC = StreamCodec.ofMember(FxOptions::write, FxOptions::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeByte(kind);
        buf.writeByte(sprite);
        buf.writeFloat((float) dir.x);
        buf.writeFloat((float) dir.y);
        buf.writeFloat((float) dir.z);
        buf.writeFloat(speed);
        buf.writeFloat(size);
        buf.writeFloat(thick);
        buf.writeFloat(life);
        buf.writeFloat(delay);
        buf.writeFloat(grow);
        buf.writeFloat(spin);
        buf.writeFloat(progress);
        buf.writeInt(color);
        buf.writeFloat(accel);
        buf.writeFloat(drag);
    }

    private static FxOptions read(RegistryFriendlyByteBuf buf) {
        int kind = buf.readByte();
        int sprite = buf.readByte();
        Vec3 dir = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        return new FxOptions(kind, sprite, dir, buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readFloat(), buf.readFloat());
    }

    /** 일반 그림 하나: 자리에서 dir 쪽으로 speed (초당 블록, 끌림으로 느려짐), 반 크기 size, 수명 life, 돌린 각 spin */
    public static FxOptions ofSprite(FxSprite s, Vec3 dir, double speed, double size, double life, double spin, int color) {
        return new FxOptions(SPRITE, s.ordinal(), dir, (float) speed, (float) size, 0F, (float) life, 0F, 0F, (float) spin, 1F, color);
    }

    @Override
    public ParticleType<FxOptions> getType() {
        return RpgParticles.FX;
    }

    /** 빛 알갱이 하나: 처음 속도 dir × speed (초당 블록), 위로 accel · 끌림 drag, 반 크기 size → 0, 수명 life, 그림 GLINT_TEXTURES[texture] */
    public static FxOptions glint(int texture, Vec3 dir, double speed, double accel, double drag, double size, double life, int color) {
        return new FxOptions(GLINT, texture, dir, (float) speed, (float) size, 0F, (float) life, 0F, 0F, 0F, 1F, color, (float) accel, (float) drag);
    }

    /** 0~1 색 + 알파 → ARGB */
    public static int argb(double r, double g, double b, double a) {
        return (to255(a) << 24) | (to255(r) << 16) | (to255(g) << 8) | to255(b);
    }

    private static int to255(double v) {
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }
}
