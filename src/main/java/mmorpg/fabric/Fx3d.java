package mmorpg.fabric;

import java.util.concurrent.atomic.AtomicInteger;
import mmorpg.fabric.skill.Opt;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 0.47.0 입체 효과 물체 (사용자 "효과 물체를 입체 모델로"): 서버가 모델 하나를 띄우라고 둘레 사람들에게 보냄 — 클라이언트 FxModels 가 모델
 * (assets/mmorpg/fxmodel/&lt;이름&gt;.json — tools/fx_models.py) 을 세계에 입체로 그림 (빛을 받는 몸 · 빛 무리 · 더한 빛).
 * <p>
 * 선택값 (Opt): 자리 · 움직임 — vel · acc (칸/초 · 칸/초²) 또는 to · ctrl · travel · ease (곡선 길 — travel 초 동안 at → ctrl → to), follow (주인 엔티티 번호 —
 * at · to · ctrl · vel 이 주인 기준 [오른쪽, 위, 앞] 칸, 주인의 보는 쪽을 따라 돎). 방향 — dir (모델 +Y — 칼끝), face (모델 +Z — 넓은 면 쪽), roll (dir 축 도),
 * align (움직이는 쪽을 봄), faceCam (넓은 면이 보는 사람 쪽), spin (도/초) · spinAxis ("y" · "x" · "z" · "up"). 크기 — scale · grow (끝에서 1 + grow 배) ·
 * pop (초 — 튀어나오듯 커짐) · shrink (초 — 끝에서 작아짐). 빛 — alpha · fadeIn · fadeOut (초), color (빛깔) · glow (속빛 · 빛 무리) · glowAlpha · shine (몸에
 * 더하는 빛). 그 밖 — life (초), delay (초 — 그만큼 늦게 나타남), anim · animSpeed · animPhase · loop (모델의 움직임), view (1 = 주인이 1인칭이면 숨김 ·
 * 2 = 주인의 1인칭에만) · owner.
 */
public final class Fx3d {
    private Fx3d() {}

    /** 보내는 거리 (칸) */
    public static final double RANGE = 128;

    // 값 칸 (Payload.v)
    static final int DIR = 0;
    static final int FACE = 3;
    static final int ROLL = 6;
    static final int SPIN = 7;
    static final int SPIN_AXIS = 8;
    static final int VEL = 9;
    static final int ACC = 12;
    static final int TO = 15;
    static final int CTRL = 18;
    static final int TRAVEL = 21;
    static final int EASE = 22;
    static final int LIFE = 23;
    static final int SCALE = 24;
    static final int GROW = 25;
    static final int POP = 26;
    static final int SHRINK = 27;
    static final int ALPHA = 28;
    static final int FADE_IN = 29;
    static final int FADE_OUT = 30;
    static final int COLOR = 31;
    static final int GLOW = 34;
    static final int GLOW_ALPHA = 37;
    static final int SHINE = 38;
    static final int ANIM_SPEED = 39;
    static final int ANIM_PHASE = 40;
    static final int DELAY = 41;
    public static final int VALUES = 42;

    // 표시 (Payload.flags)
    public static final int F_FOLLOW = 1;
    public static final int F_PATH = 2;
    public static final int F_ALIGN = 4;
    public static final int F_FACE_CAM = 8;
    public static final int F_LOOP = 16;
    /** 보는 사람 걸러내기 (2비트): 1 = 주인이 1인칭이면 숨김 · 2 = 주인의 1인칭에만 */
    public static final int VIEW_SHIFT = 5;
    public static final int F_KILL = 128;

    private static final AtomicInteger NEXT = new AtomicInteger(1);
    /** 시험: 보낸 모델 수 · 마지막 이름 */
    public static int sent;
    public static String lastModel;

    /** 모델 하나 (또는 지우기 — F_KILL, v[FADE_OUT] = 사라지는 초) */
    public record Payload(int id, String model, String anim, double x, double y, double z, int owner, int flags, float[] v) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Payload> TYPE = new CustomPacketPayload.Type<>(Mmorpg.id("fx3d"));
        public static final StreamCodec<FriendlyByteBuf, Payload> CODEC = CustomPacketPayload.codec(Payload::write, Payload::read);

        private static Payload read(FriendlyByteBuf buf) {
            int id = buf.readVarInt();
            String model = buf.readUtf(64);
            String anim = buf.readUtf(64);
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            int owner = buf.readVarInt();
            int flags = buf.readVarInt();
            int n = Math.min(VALUES * 2, buf.readVarInt());
            float[] v = new float[Math.max(VALUES, n)];
            for (int i = 0; i < n; i++) v[i] = buf.readFloat();
            return new Payload(id, model, anim, x, y, z, owner, flags, v);
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(id);
            buf.writeUtf(model, 64);
            buf.writeUtf(anim, 64);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeVarInt(owner);
            buf.writeVarInt(flags);
            // 끝의 0 은 보내지 않음
            int n = v.length;
            while (n > 0 && v[n - 1] == 0F) n--;
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) buf.writeFloat(v[i]);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void init() {
        PayloadTypeRegistry.clientboundPlay().register(Payload.TYPE, Payload.CODEC);
    }

    private static void put(float[] v, int i, Vec3 p) {
        v[i] = (float) p.x;
        v[i + 1] = (float) p.y;
        v[i + 2] = (float) p.z;
    }

    private static void put(float[] v, int i, double[] c) {
        v[i] = (float) c[0];
        v[i + 1] = (float) c[1];
        v[i + 2] = (float) c[2];
    }

    private static int ease(String e) {
        return switch (e == null ? "" : e) {
            case "in" -> 1;
            case "out" -> 2;
            case "inout" -> 3;
            case "out3" -> 4;
            case "back" -> 5;
            default -> 0;
        };
    }

    private static int axis(String a) {
        return switch (a == null ? "y" : a) {
            case "x" -> 1;
            case "z" -> 2;
            case "up" -> 3;
            default -> 0;
        };
    }

    /** 모델 하나를 띄움: 번호 (지울 때 — kill) */
    public static int spawn(ServerLevel level, String model, Vec3 at, Opt o) {
        if (level == null || model == null) return 0;
        float[] v = new float[VALUES];
        put(v, DIR, o.vec("dir", new Vec3(0, 1, 0)));
        put(v, FACE, o.vec("face", Vec3.ZERO));
        v[ROLL] = (float) o.num("roll", 0);
        v[SPIN] = (float) o.num("spin", 0);
        v[SPIN_AXIS] = axis(o.str("spinAxis", "y"));
        put(v, VEL, o.vec("vel", Vec3.ZERO));
        put(v, ACC, o.vec("acc", Vec3.ZERO));
        boolean path = o.has("to");
        if (path) {
            Vec3 to = o.vec("to", at);
            put(v, TO, to);
            put(v, CTRL, o.vec("ctrl", at.add(to).scale(0.5)));
        }
        double life = Math.max(0.05, o.num("life", 1));
        v[TRAVEL] = (float) o.num("travel", life);
        v[EASE] = ease(o.str("ease", null));
        v[LIFE] = (float) life;
        v[SCALE] = (float) o.num("scale", 1);
        v[GROW] = (float) o.num("grow", 0);
        v[POP] = (float) o.num("pop", 0);
        v[SHRINK] = (float) o.num("shrink", 0);
        v[ALPHA] = (float) o.num("alpha", 1);
        v[FADE_IN] = (float) o.num("fadeIn", 0);
        v[FADE_OUT] = (float) o.num("fadeOut", 0.15);
        put(v, COLOR, o.color("color", new double[] {1, 1, 1}));
        put(v, GLOW, o.color("glow", o.color("color", new double[] {1, 1, 1})));
        v[GLOW_ALPHA] = (float) o.num("glowAlpha", 0.55);
        v[SHINE] = (float) o.num("shine", 0.25);
        v[ANIM_SPEED] = (float) o.num("animSpeed", 1);
        v[ANIM_PHASE] = (float) o.num("animPhase", 0);
        v[DELAY] = (float) o.num("delay", 0);
        int flags = 0;
        int owner = (int) o.num("owner", o.num("follow", 0));
        if (o.has("follow")) flags |= F_FOLLOW;
        if (path) flags |= F_PATH;
        if (o.truthy("align")) flags |= F_ALIGN;
        if (o.truthy("faceCam")) flags |= F_FACE_CAM;
        if (o.truthy("loop")) flags |= F_LOOP;
        flags |= ((int) o.num("view", 0) & 3) << VIEW_SHIFT;
        int id = NEXT.getAndIncrement() & 0x3FFFFFFF;
        if (id == 0) id = NEXT.getAndIncrement();
        Payload p = new Payload(id, model, o.str("anim", ""), at.x, at.y, at.z, owner, flags, v);
        Vec3 near = at;
        if ((flags & F_FOLLOW) != 0) {
            var e = level.getEntity(owner);
            if (e != null) near = e.position();
        }
        send(level, near, p);
        sent++;
        lastModel = model;
        return id;
    }

    /** 띄운 모델을 fade 초 동안 흐리게 하며 지움 */
    public static void kill(ServerLevel level, int id, Vec3 near, double fade) {
        if (level == null || id == 0) return;
        float[] v = new float[VALUES];
        v[FADE_OUT] = (float) Math.max(0, fade);
        send(level, near, new Payload(id, "", "", near.x, near.y, near.z, 0, F_KILL, v));
    }

    private static void send(ServerLevel level, Vec3 near, Payload p) {
        double r2 = RANGE * RANGE;
        for (ServerPlayer sp : level.players()) {
            if (sp.position().distanceToSqr(near) > r2) continue;
            if (ServerPlayNetworking.canSend(sp, Payload.TYPE)) ServerPlayNetworking.send(sp, p);
        }
    }
}
