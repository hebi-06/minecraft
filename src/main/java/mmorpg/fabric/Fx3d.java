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
 * 2 = 주인의 1인칭에만) · owner. 0.47.0 도는 길 — orbit (반지름 — at · follow 기준이 가운데) · orbitSpeed (도/초) · orbitAngle · bob · bobHz · orbitGrow,
 * 옮기기 move (쫓아가는 구체 — 지금 자리에서 새 자리로), 움직임 바꾸기 anim (문이 열림 → 소용돌이 → 닫힘), 휘두름 arc · arcAxis · arcTime · arcEase (dir · face 를
 * 세계 축 둘레로 돌림 — 내리치는 도끼 · 휘두르는 거대한 검, faceCam 과 같이 쓰면 넓은 면이 늘 보임), 쥔 곳 grip (모델 칸 — 이 점을 중심으로 돎).
 * 0.48.0 무기 모델 — 이름이 item:&lt;아이템 ID&gt; 이면 사용자 무기 디자인의 아이템 3D 모델 (던진 묠니르 · 떨어지는 궁니르 …, 주인의 무기 스킨) — glowR (가운데 빛 무리).
 * stretch (모델 축마다 늘임 [x, y, z] — 빛기둥 · 불기둥을 높이만 길게).
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
    /** 0.47.0 도는 길 (orbit — at 이 가운데): 반지름 · 도/초 · 처음 각 · 위아래 흔들림 칸 · 흔들림 Hz · 반지름이 느는 칸/초 */
    static final int ORBIT_R = 42;
    static final int ORBIT_W = 43;
    static final int ORBIT_A = 44;
    static final int BOB = 45;
    static final int BOB_HZ = 46;
    static final int ORBIT_GROW = 47;
    /**
     * 0.47.0 휘두름 (arc — 사진첩: 앞으로 내리치는 칼 · 도끼가 1 · 3인칭 모두 칼날의 옆선으로만 보였음): dir · face 를 arcAxis 둘레로 arcTime 초에 arc 도 (arcEase)
     * 돌림 — 세계 (follow 면 주인 기준) 축이라 보는 사람마다 같은 자리를 지남, faceCam 과 같이 쓰면 넓은 면이 늘 보는 사람 쪽
     */
    static final int ARC_AXIS = 48;
    static final int ARC_DEG = 51;
    static final int ARC_TIME = 52;
    static final int ARC_EASE = 53;
    /** 0.47.0 쥔 곳 (grip — 모델 칸): 이 점이 at 에 놓이고 이 점을 중심으로 돎 (칼 · 도끼를 자루 끝을 쥐고 휘두름 — 0 이면 모델의 가운데) */
    static final int GRIP = 54;
    /** 0.48.0 무기 모델 (item:) 의 가운데 빛 무리 반지름 (모델 칸 — glowR, 0 = 없음) */
    static final int GLOW_R = 57;
    /** 0.48.0 모델 축마다 늘임 − 1 (stretch — 빛기둥 · 불기둥: 0 이면 그대로) */
    static final int STRETCH = 58;
    public static final int VALUES = 61;

    // 표시 (Payload.flags)
    public static final int F_FOLLOW = 1;
    public static final int F_PATH = 2;
    public static final int F_ALIGN = 4;
    public static final int F_FACE_CAM = 8;
    public static final int F_LOOP = 16;
    /** 보는 사람 걸러내기 (2비트): 1 = 주인이 1인칭이면 숨김 · 2 = 주인의 1인칭에만 */
    public static final int VIEW_SHIFT = 5;
    public static final int F_KILL = 128;
    /** 0.47.0 도는 길 (at 둘레를 orbit 반지름으로 — follow 면 주인 기준) */
    public static final int F_ORBIT = 256;
    /** 0.47.0 옮기기 (id 의 모델을 지금 자리에서 at 으로 travel 초에 — 쫓아가는 구체) */
    public static final int F_MOVE = 512;
    /** 0.47.0 움직임 바꾸기 (id 의 모델 — anim 을 처음부터, F_LOOP 면 되풀이): 문이 열림 → 소용돌이 → 닫힘 */
    public static final int F_ANIM = 1024;

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
        v[SHINE] = (float) o.num("shine", 0);
        v[ANIM_SPEED] = (float) o.num("animSpeed", 1);
        v[ANIM_PHASE] = (float) o.num("animPhase", 0);
        v[DELAY] = (float) o.num("delay", 0);
        boolean orbit = o.has("orbit");
        if (orbit) {
            v[ORBIT_R] = (float) o.num("orbit", 1);
            v[ORBIT_W] = (float) o.num("orbitSpeed", 180);
            v[ORBIT_A] = (float) o.num("orbitAngle", 0);
            v[ORBIT_GROW] = (float) o.num("orbitGrow", 0);
        }
        // 위아래 흔들림 (떠 있는 책 · 구체 · 종 — 도는 길이 아니어도)
        v[BOB] = (float) o.num("bob", 0);
        v[BOB_HZ] = (float) o.num("bobHz", 1);
        // 휘두름 (arcAxis 둘레로 arc 도 — arcTime 초) · 쥔 곳
        if (o.has("arc")) {
            put(v, ARC_AXIS, o.vec("arcAxis", new Vec3(0, 1, 0)));
            v[ARC_DEG] = (float) o.num("arc", 0);
            v[ARC_TIME] = (float) Math.max(0.01, o.num("arcTime", life));
            v[ARC_EASE] = ease(o.str("arcEase", null));
        }
        put(v, GRIP, o.vec("grip", Vec3.ZERO));
        v[GLOW_R] = (float) o.num("glowR", 0);
        Vec3 st = o.vec("stretch", null);
        if (st != null) put(v, STRETCH, st.subtract(1, 1, 1));
        int flags = 0;
        if (orbit) flags |= F_ORBIT;
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

    /** 띄운 모델을 지금 자리에서 to 로 seconds 초에 옮김 (곧게 · 처음 자리 움직임 대신 — 부드럽게 시작해 부드럽게 멈춤) */
    public static void move(ServerLevel level, int id, Vec3 to, double seconds) {
        move(level, id, to, seconds, "inout");
    }

    /**
     * 옮기기 (ease — null 이면 고른 빠르기: 쫓아가는 구체 · 튕기는 방패처럼 잇따라 옮길 때). 0.47.0 사진첩: 잇따라 옮기면 처음 자리에서 다시 떠나던 것을 고침 — 지금 그려지는
     * 자리 (옮기는 중이면 그 길 위) 에서 떠남
     */
    public static void move(ServerLevel level, int id, Vec3 to, double seconds, String ease) {
        if (level == null || id == 0) return;
        float[] v = new float[VALUES];
        v[TRAVEL] = (float) Math.max(0.01, seconds);
        v[EASE] = ease(ease);
        send(level, to, new Payload(id, "", "", to.x, to.y, to.z, 0, F_MOVE, v));
    }

    /** 띄운 모델의 움직임 (모델 안의 anim) 을 바꿈 — 처음부터, loop 면 되풀이 */
    public static void anim(ServerLevel level, int id, String anim, boolean loop, Vec3 near) {
        if (level == null || id == 0) return;
        send(level, near, new Payload(id, "", anim == null ? "" : anim, near.x, near.y, near.z, 0, F_ANIM | (loop ? F_LOOP : 0), new float[VALUES]));
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
