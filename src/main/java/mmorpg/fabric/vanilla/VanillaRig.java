package mmorpg.fabric.vanilla;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import mmorpg.core.Json;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.motion.MotionPoses;
import mmorpg.fabric.motion.Pose;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 2026-11-13 모드 없는 클라이언트의 플레이어 모션 (사용자 "모든 모션을 추천대로 만들고 있어봐" — 1번: 뼈 몸 + 픽셀마다 색 (셰이더 없음) · 내 화면은 기본
 * 숨김 (메뉴에서 켬) · 싸우는 동안): 평타 · 스킬 · 룬 동작 (MotionSystem.broadcast — 모드 클라이언트에 보내는 것과 같은 동작) 을 하면 {@link #COMBAT} 틱 동안
 * 진짜 몸 대신 뼈 몸 (뼈 10 — 머리 · 몸통 · 팔 · 다리 위 아래, 아이템 표시 · 스킨은 tools/player_rig.py 의 픽셀 면에 custom_model_data 색) 이 그 사람을
 * 타고 (탄 것은 클라이언트가 탈것의 자리에 그대로 놓음 — 몸 자리 보간의 늦음이 없음) 모드와 같은 자세 (MotionPoses — 공통 코드) 를 함.
 * 모드 없는 다른 사람에게만: 진짜 몸은 투명 (그 사람에게 가는 공유 상태의 투명 칸 — VanillaPackets) · 손 · 머리 장식을 비움 (VanillaCosmetics.equipment).
 * 모드 클라이언트는 그대로 (모드 모션). 내 화면 ({@link #SELF}) 은 기본 끔 — 1인칭 화면을 가리지 않게.
 *
 * <p>자세 (모드 MotionRender 와 같은 차례): 바닐라 HumanoidModel 자세 (걸음 · 고개 · 든 팔 · 숨 · 웅크림) → 동작 자세를 무게 w 만큼 섞음 (몸통은 엉덩이 축 ·
 * 팔은 사원수 · 팔꿈치 · 무릎 굽힘 · 다리) → 뿌리 (오르내림 · 앞 · 돌기 · 앞구르기). 표시 변환 = 몸 (Ry(180 − 몸 방향) · S(−1, −1, 1) · 0.9375 ·
 * T(0, −1.501, 0)) · 부위 · Rx(180°) (tools/player_rig.py 의 F 와 아이템 표시의 Y 180°).
 */
public final class VanillaRig {
    private VanillaRig() {}

    /** 동작 뒤 뼈 몸으로 남는 틱 (그 사이 다음 평타 · 스킬이면 이어짐) */
    static final int COMBAT = 80;
    /** 뼈 순서 (tools/player_rig.py — 머리 · 몸통 · 오른팔 위 · 아래 · 왼팔 위 · 아래 · 오른다리 위 · 아래 · 왼다리 위 · 아래) */
    static final int HEAD = 0, BODY = 1, RA_U = 2, RA_L = 3, LA_U = 4, LA_L = 5, RL_U = 6, RL_L = 7, LL_U = 8, LL_L = 9, BONES = 10;
    private static final String[] NAMES = {"head", "body", "rarm_u", "rarm_l", "larm_u", "larm_l", "rleg_u", "rleg_l", "lleg_u", "lleg_l"};
    private static final float PI = (float) Math.PI;
    /** 모드 MotionRender 의 엉덩이 축 · 앞구르기 축 */
    private static final Vector3f HIP = new Vector3f(0, 12, 0);
    private static final float FLIP_PIVOT = 0.55F;

    /**
     * 뼈 하나 (아이템 · custom_model_data colors 순서의 색 자리 — 몸 층 [u, v] · 평균 칸, 겉옷 층 [u, v] + 밑 몸 칸 [bu, bv]). tint 색은 알파를 쓰지 않음 (바닐라
     * CustomModelDataSource 가 ARGB.opaque — CI 43351661053 에서 겉옷 층의 투명 칸이 검게 칠해져 몸 전체가 검은 껍데기) → 겉옷 층의 투명 칸은 밑 몸 칸의 색,
     * 겉옷이 하나도 없으면 flag 0 거짓 (겉옷 모델을 그리지 않음 — tools/player_rig.py)
     */
    record BoneDef(String item, int[] u, int[] v, boolean[] outer, int[] bu, int[] bv, int[][] avg) {}

    private static final Map<String, BoneDef[]> VARIANTS = new HashMap<>();
    /** 바닐라 기본 스킨 목록조차 없을 때만 (우리 그림 — 모험가) */
    private static Skin DEFAULT;
    /**
     * 2026-11-13 바닐라 기본 스킨 (사용자 "플레이어가 스킬을 쓰면 플레이어 스킨이 바뀌잖아 … 스킨을 그대로 유지시키고 싶은데"): 스킨 그림을 받지 못한 사람
     * (오프라인 서버 · 프로필에 textures 없음 · 받기 실패) 은 클라이언트가 진짜 몸에 그리는 기본 스킨 (DefaultPlayerSkin — UUID.hashCode() 를 목록 수로 나눈
     * 나머지) 과 같은 것을 뼈 몸에도 — 바닐라 그림을 그대로 쓰는 뼈 모델 (rpg:vrig/def/&lt;형&gt;/&lt;뼈&gt; — tools/player_rig.py, 그림은 클라이언트의 것) ·
     * 목록 차례는 vanilla_rig.json defaults ("slim/alex" …)
     */
    private static final List<String> DEFAULTS = new ArrayList<>();
    private static final Map<String, Skin> DEFAULT_SKINS = new HashMap<>();

    /** 스킨 (64 × 64 ARGB) · 가는 팔 · vanilla = 바닐라 기본 스킨 이름 (그림을 쓰는 뼈 모델 — argb 없음) */
    record Skin(int[] argb, boolean slim, String vanilla) {}

    /** 그 UUID 의 사람에게 클라이언트가 그리는 기본 스킨 (목록이 없으면 우리 그림) */
    static Skin defaultFor(UUID id) {
        if (DEFAULTS.isEmpty()) return DEFAULT;
        String e = DEFAULTS.get(Math.floorMod(id.hashCode(), DEFAULTS.size()));
        return DEFAULT_SKINS.computeIfAbsent(e, k -> {
            int slash = k.indexOf('/');
            return new Skin(null, "slim".equals(k.substring(0, Math.max(0, slash))), k.substring(slash + 1));
        });
    }

    private static final Map<UUID, Skin> SKINS = new ConcurrentHashMap<>();
    private static final Map<String, Skin> BY_URL = new ConcurrentHashMap<>();
    private static final Set<UUID> LOADING = ConcurrentHashMap.newKeySet();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private static final Map<UUID, Rig> RIGS = new HashMap<>();
    /** 내 모션 보기 (내 화면에도 뼈 몸 — 3인칭용, 기본 끔 · 꾸미기 창 단추 — 플레이어 속성 {@link #SELF_KEY} 에 저장) */
    private static final Set<UUID> SELF = new HashSet<>();
    static final String SELF_KEY = "rpg:rig_self";
    private static MinecraftServer server;
    /** 시험: 뼈 몸을 띄운 횟수 · 마지막 동작 */
    public static int shown;
    public static String lastMotion = "";
    /** 시험: 마지막 동작의 빠르기 (2026-11-14 공속) */
    public static float lastSpeed = 1F;

    public static void init() {
        load();
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            server = s;
            tick();
        });
        ServerPlayConnectionEvents.JOIN.register((h, sender, s) -> {
            skinOf(h.player);
            if ("1".equals(mmorpg.fabric.Props.get(h.player, SELF_KEY))) SELF.add(h.player.getUUID());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> {
            Rig r = RIGS.remove(h.player.getUUID());
            if (r != null) r.destroy();
            SKINS.remove(h.player.getUUID());
            SELF.remove(h.player.getUUID());
        });
    }

    // ------------------------------------------------------------ 바깥에서

    /** 동작 시작 (MotionSystem.broadcast — 평타 · 스킬 · 룬 · 구르기): 싸우는 동안 뼈 몸으로 */
    public static void play(ServerPlayer sp, String motion, float yaw) {
        play(sp, motion, yaw, 1F);
    }

    /** 같은 것 — 2026-11-14 빠르기 speed (평타 공속: 동작 틱 ÷ speed) */
    public static void play(ServerPlayer sp, String motion, float yaw, float speed) {
        if (sp == null || sp.isRemoved() || VARIANTS.isEmpty()) return;
        MotionPoses.Motion m = MotionPoses.get(motion);
        if (m == null) return;
        Rig r = RIGS.get(sp.getUUID());
        if (r == null) {
            if (!usable(sp)) return;
            r = new Rig(sp);
            RIGS.put(sp.getUUID(), r);
            r.attach();
        }
        r.start(motion, m, yaw, speed);
        lastMotion = motion;
        lastSpeed = speed;
    }

    /** 멈칫 (MotionSystem.hitStop — 맞는 순간): 하는 동작을 ticks 틱 그 자세로 */
    public static void stop(ServerPlayer sp, int ticks) {
        Rig r = sp == null ? null : RIGS.get(sp.getUUID());
        if (r != null) r.hitStop(ticks);
    }

    /** 뼈 몸이 하나라도 있는지 (VanillaPackets — 패킷마다 엔티티를 찾지 않게) */
    static boolean any() {
        return !RIGS.isEmpty();
    }

    /** 뼈 몸을 보이는 중인지 (모드 없는 다른 사람에게 진짜 몸 · 손을 숨김) */
    public static boolean active(Entity e) {
        return e instanceof ServerPlayer && RIGS.containsKey(e.getUUID());
    }

    /**
     * 뼈 몸의 지금 몸통 · 머리 틀 (j 공간 — 발 기준, VanillaCosmetics.CosmeticHolder 의 등 장식 · 머리 뼈 장식이 따라감: 회전 베기 · 몸 기울기 · 뿌리 움직임) —
     * 뼈 몸이 없으면 null
     */
    static Matrix4f[] frames(Entity e) {
        Rig r = e == null ? null : RIGS.get(e.getUUID());
        if (r == null || r.bodyJ == null) return null;
        return new Matrix4f[] {new Matrix4f(r.bodyJ), new Matrix4f(r.headJ)};
    }

    /** 내 모션 보기 (내 화면에도 뼈 몸 — 저장됨). 보이던 뼈 몸은 거둠 (다음 동작부터 새로) */
    public static void selfView(ServerPlayer sp, boolean on) {
        if (on) SELF.add(sp.getUUID());
        else SELF.remove(sp.getUUID());
        mmorpg.fabric.Props.set(sp, SELF_KEY, on ? "1" : null);
        end(sp);
    }

    /** 뼈 몸을 바로 거두고 진짜 몸을 다시 보임 (드라군 변신 — 사람이 용이 됨 · 내 모션 보기 바꿈) */
    public static void end(ServerPlayer sp) {
        Rig r = sp == null ? null : RIGS.remove(sp.getUUID());
        if (r == null) return;
        r.destroy();
        if (!sp.isRemoved()) reveal(sp);
    }

    public static boolean selfView(ServerPlayer sp) {
        return SELF.contains(sp.getUUID());
    }

    /** 뼈 몸을 쓸 수 있는 몸 상태 (누움 · 헤엄 · 겉날개 · 탄 것 · 관전 · 변신 아님) */
    private static boolean usable(ServerPlayer sp) {
        return sp.isAlive() && !sp.isSpectator() && !sp.isSleeping() && !sp.isSwimming() && !sp.isFallFlying() && !sp.isPassenger()
                && !sp.isInvisible() && !VanillaCosmetics.handsHidden(sp);
    }

    // ------------------------------------------------------------ 진짜 몸 숨김 (VanillaPackets)

    /** 모드 없는 다른 사람 (내 모션 보기면 나도) 에게 진짜 몸을 숨기는지 (공유 상태 0번의 투명 칸 · 손 · 머리 장식) */
    static boolean hidesBodyFor(Entity e, ServerPlayer receiver) {
        return active(e) && VanillaClients.isVanilla(receiver) && (e != receiver || SELF.contains(e.getUUID()));
    }

    /** 공유 상태 0번을 다시 보냄 (투명 칸은 VanillaPackets 가 받는 사람마다 넣음) · 장비 다시 */
    private static void pushBody(ServerPlayer sp) {
        byte flags = 0;
        List<SynchedEntityData.DataValue<?>> all = sp.getEntityData().getNonDefaultValues();
        if (all != null) for (SynchedEntityData.DataValue<?> v : all) if (v.id() == 0 && v.value() instanceof Byte b) flags = b;
        ClientboundSetEntityDataPacket p = new ClientboundSetEntityDataPacket(sp.getId(), List.of(new SynchedEntityData.DataValue<>(0, EntityDataSerializers.BYTE, flags)));
        for (ServerPlayer o : sp.level().players()) {
            if (VanillaClients.isVanilla(o) && o.distanceToSqr(sp) < 160 * 160) o.connection.send(p);
        }
        VanillaCosmetics.refreshGear(sp);
        // 내 모션 보기: 내 화면의 진짜 손에 든 것도 숨김 (3인칭에서 뼈 몸의 무기와 둘이 되지 않게)
        VanillaCosmetics.hideOwnHands(sp, active(sp) && SELF.contains(sp.getUUID()));
    }

    private static void reveal(ServerPlayer sp) {
        pushBody(sp);
    }

    // ------------------------------------------------------------ 틱

    private static void tick() {
        if (RIGS.isEmpty()) return;
        long now = Tasks.now();
        for (Map.Entry<UUID, Rig> e : new ArrayList<>(RIGS.entrySet())) {
            Rig r = e.getValue();
            ServerPlayer sp = r.sp;
            boolean over = sp.isRemoved() || !usable(sp) || sp.level() != r.level || now > r.until && r.motion == null;
            if (over) {
                RIGS.remove(e.getKey());
                r.destroy();
                if (!sp.isRemoved()) reveal(sp);
            }
        }
    }

    // ------------------------------------------------------------ 뼈 몸

    static final class Rig extends ElementHolder {
        final ServerPlayer sp;
        final net.minecraft.server.level.ServerLevel level;
        final ItemDisplayElement[] bones = new ItemDisplayElement[BONES];
        final ItemDisplayElement main;
        final ItemDisplayElement off;
        /** 머리 장식 (모자 — 진짜 몸의 투구 칸은 비움: VanillaCosmetics.equipment) */
        final ItemDisplayElement hat;
        /** 이름 (따로 — 내 화면에는 그리지 않음) */
        final NameTag tag;
        String hatKey = "";
        String variant = "";
        Skin skin;
        ItemStack lastMain = ItemStack.EMPTY;
        ItemStack lastOff = ItemStack.EMPTY;
        String lastMainKey = "";
        String lastOffKey = "";
        // 동작 (ClientMotions 와 같은 셈 — 시작 틱 · 멈칫)
        String motion;
        MotionPoses.Motion m;
        double start;
        float yaw;
        int ticks;
        double holdFrom = Double.NaN;
        double holdUntil;
        long until;
        boolean attached;
        private final Pose pose = new Pose();
        // 걸음 (WalkAnimationState 와 같은 셈 — 서버 사람의 값에 기대지 않음) · 몸 방향 (머리를 따라감 — 75° 넘게 벌어지지 않게)
        private double lastX = Double.NaN;
        private double lastZ;
        private float walkPos;
        private float walkSpeed;
        private float bodyYaw = Float.NaN;
        private long lastPose = -1;
        /** 꾸미기가 따라갈 몸통 · 머리 틀 (j 공간 — {@link #frames}) */
        Matrix4f bodyJ;
        Matrix4f headJ;

        Rig(ServerPlayer sp) {
            this.sp = sp;
            this.level = sp.level();
            for (int i = 0; i < BONES; i++) bones[i] = element(ItemDisplayContext.NONE);
            main = element(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
            off = element(ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
            hat = element(ItemDisplayContext.HEAD);
            tag = new NameTag(sp);
            skin(skinOf(sp));
            pose(true);
        }

        private ItemDisplayElement element(ItemDisplayContext ctx) {
            ItemDisplayElement e = new ItemDisplayElement(ItemStack.EMPTY);
            e.setItemDisplayContext(ctx);
            e.setInterpolationDuration(1);
            // 크기 0 = 가려짐 판정 없음 (표시 엔티티의 판정 상자는 제자리 (머리 위) 에서 위로만 — 다리만 보이는 화면에서 사라지지 않게)
            e.setDisplaySize(0F, 0F);
            // 2026-11-14 처음 2틱은 보이지 않게 (보는 거리 0): 붙는 패킷 (태우기) 보다 먼저 그려진 한 틱 동안 뼈가 발 높이에 무너진 채 보였음 (영상 CI 43605774503
            // 칼 첫 평타) — attach 뒤 reveal
            e.setViewRange(0F);
            e.ignorePositionUpdates();
            addPassengerElement(e);
            return e;
        }

        /** 붙은 뒤 보이게 (보는 거리 1 — 바닐라 기본) */
        void reveal() {
            for (ItemDisplayElement b : bones) b.setViewRange(1F);
            main.setViewRange(1F);
            off.setViewRange(1F);
            hat.setViewRange(1F);
        }

        void attach() {
            EntityAttachment.ofTicking(this, sp);
            EntityAttachment.ofTicking(tag, sp);
            attached = true;
            tag.attached = true;
            shown++;
            // 내 모션 보기: 내 화면에도 (Polymer 는 엔티티를 보는 사람 (추적) 에게만 붙임 — 자기 자신은 그 목록에 없음)
            if (SELF.contains(sp.getUUID()) && VanillaClients.isVanilla(sp)) startWatching(sp.connection);
            // 태우기: 홀더가 엔티티에 붙은 뒤에야 Polymer 가 태우는 패킷에 뼈 번호를 실음 (VanillaModels 와 같음)
            Set<ServerGamePacketListenerImpl> to = new HashSet<>(getWatchingPlayers());
            to.addAll(tag.getWatchingPlayers());
            if (!to.isEmpty()) {
                ClientboundSetPassengersPacket mount = new ClientboundSetPassengersPacket(sp);
                for (ServerGamePacketListenerImpl c : to) c.send(mount);
            }
            pushBody(sp);
            mmorpg.fabric.Tasks.later(2, this::reveal);
        }

        @Override
        public void destroy() {
            super.destroy();
            tag.destroy();
        }

        @Override
        public boolean startWatching(ServerGamePacketListenerImpl c) {
            ServerPlayer p = c.getPlayer();
            if (VanillaClients.isModded(p)) return false;
            if (p == sp && !SELF.contains(sp.getUUID())) return false;
            if (!super.startWatching(c)) return false;
            if (attached) c.send(new ClientboundSetPassengersPacket(sp));
            return true;
        }

        /** 스킨 색을 뼈마다 (가는 팔이면 그 모델 · 바닐라 기본 스킨이면 그 그림을 쓰는 뼈 모델) */
        void skin(Skin s) {
            if (s == null) s = defaultFor(sp.getUUID());
            if (s == null || s == skin) return;
            skin = s;
            variant = s.slim() ? "slim" : "wide";
            BoneDef[] defs = VARIANTS.get(variant);
            if (defs == null) return;
            for (int i = 0; i < BONES; i++) bones[i].setItem(s.vanilla() != null ? vanillaStack(variant, i, s.vanilla()) : stack(defs[i], s));
            Mmorpg.LOG.info("[vanilla] rig skin {}: {}", sp.getGameProfile().name(), s.vanilla() != null ? "default " + variant + "/" + s.vanilla()
                    : s == DEFAULT ? "ours" : "profile " + variant);
        }

        void start(String name, MotionPoses.Motion mo, float y, float speed) {
            motion = name;
            m = mo;
            start = Tasks.now() + 1;
            yaw = y;
            ticks = mmorpg.core.Motions.playTicks(mo.ticks, speed); // 2026-11-14 공속 (평타만 — 나머지는 1)
            holdFrom = Double.NaN;
            until = Tasks.now() + ticks + COMBAT;
        }

        /** 멈칫 (ClientMotions.hitStop — 맞는 때 (impacts) 에 맞춰 · 끝나면 그 두 배 동안 따라잡음) */
        void hitStop(int n) {
            if (motion == null || n <= 0) return;
            double base = Tasks.now() + 1;
            double el = elapsed(base);
            double snap = 0;
            if (m.impacts != null) {
                double best = Double.NaN;
                for (float f : m.impacts) {
                    double t = f * ticks;
                    if (t >= el - 0.25 && t <= el + 2.0 && (Double.isNaN(best) || t < best)) best = t;
                }
                if (!Double.isNaN(best)) snap = Math.max(0, best - el);
            }
            double from = base + snap;
            if (!Double.isNaN(holdFrom) && from <= holdUntil) {
                holdUntil = Math.max(holdUntil, from + n);
                return;
            }
            double rem = Double.isNaN(holdFrom) ? 0 : heldBy(holdFrom, holdUntil, from);
            holdFrom = from - rem;
            holdUntil = from + n;
        }

        double elapsed(double now) {
            double e = now - start;
            return Double.isNaN(holdFrom) ? e : e - heldBy(holdFrom, holdUntil, now);
        }

        static double heldBy(double from, double until, double now) {
            if (now <= from) return 0;
            if (now <= until) return now - from;
            double len = until - from;
            return len <= 0 ? 0 : len * Math.max(0, 1 - (now - until) / (len * 2.0));
        }

        @Override
        protected void onTick() {
            if (!attached || sp.isRemoved()) return;
            // 보는 모드 없는 사람이 없으면 자세 셈은 쉼 (동작 시각은 틱으로 셈 — 다시 보이면 그 자리부터, 끝난 동작은 지워 뼈 몸이 4초 뒤 거둬지게)
            if (getWatchingPlayers().isEmpty()) {
                if (motion != null && elapsed(Tasks.now()) >= ticks) {
                    motion = null;
                    m = null;
                }
                return;
            }
            skin(skinOf(sp));
            // 보내는 빠르기: 동작 중 틱마다 · 동작 뒤 (걷기 · 서 있기) 는 2틱마다 (보간도 2틱 — 보내는 양을 반으로)
            int want = motion != null ? 1 : 2;
            if (want != rate) {
                rate = want;
                for (ItemDisplayElement b : bones) b.setInterpolationDuration(rate);
                main.setInterpolationDuration(rate);
                off.setInterpolationDuration(rate);
                hat.setInterpolationDuration(rate);
            }
            if (rate > 1 && ++age % rate != 0) return;
            pose(false);
        }

        int rate = 1;
        int age;

        // -------------------------------------------------------- 자세 → 표시 변환

        /** 부위 (자바 ModelPart — 자리 픽셀 · 회전 라디안) */
        static final class Part {
            float x, y, z, xRot, yRot, zRot;

            void set(float px, float py, float pz) {
                x = px;
                y = py;
                z = pz;
                xRot = yRot = zRot = 0;
            }

            Matrix4f matrix(Matrix4f root) {
                return matrix(root, 0);
            }

            /** dx = 자리를 x 로 더 옮김 (PlayerModel.translateToHand — 가는 팔의 손 ±0.5 픽셀) */
            Matrix4f matrix(Matrix4f root, float dx) {
                Matrix4f m = new Matrix4f(root).translate((x + dx) / 16F, y / 16F, z / 16F);
                if (xRot != 0 || yRot != 0 || zRot != 0) m.rotate(new Quaternionf().rotationZYX(zRot, yRot, xRot));
                return m;
            }
        }

        final Part head = new Part();
        final Part body = new Part();
        final Part rarm = new Part();
        final Part larm = new Part();
        final Part rleg = new Part();
        final Part lleg = new Part();

        void pose(boolean first) {
            boolean slim = "slim".equals(variant);
            boolean left = sp.getMainArm() == HumanoidArm.LEFT;
            double now = Tasks.now();
            // 지금 동작 (ClientMotions.pose)
            Pose p = null;
            if (motion != null) {
                double t = elapsed(now) / ticks;
                if (t >= 1) {
                    motion = null;
                    m = null;
                } else {
                    p = MotionPoses.evaluate(m, (float) Math.max(0, t), left, pose);
                    p.yaw = "roll".equals(motion) || m.faceYaw ? yaw : Float.NaN;
                    if (p.w <= 0) p = null;
                }
            }
            // 걸음 · 몸 방향 (LivingEntity.calculateEntityAnimation · tickHeadTurn 처럼 — 지난번부터 dt 틱)
            long tickNow = Tasks.now();
            int dt = lastPose < 0 ? 1 : (int) Math.max(1, Math.min(4, tickNow - lastPose));
            lastPose = tickNow;
            double dx = Double.isNaN(lastX) ? 0 : sp.getX() - lastX;
            double dz = Double.isNaN(lastX) ? 0 : sp.getZ() - lastZ;
            lastX = sp.getX();
            lastZ = sp.getZ();
            float moved = (float) Math.sqrt(dx * dx + dz * dz) / dt;
            walkSpeed += (Math.min(1F, moved * 4F) - walkSpeed) * (1F - (float) Math.pow(0.6, dt));
            walkPos += walkSpeed * dt;
            float look = sp.getYRot();
            if (Float.isNaN(this.bodyYaw)) this.bodyYaw = look;
            if (moved > 0.02F) {
                float dir = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float off = Mth.wrapDegrees(dir - look);
                // 뒤로 걸으면 몸은 앞 (바닐라처럼 — 걷는 쪽이 등 뒤면 그 반대)
                if (Math.abs(off) > 95F) dir += 180F;
                this.bodyYaw += Mth.wrapDegrees(dir - this.bodyYaw) * (1F - (float) Math.pow(0.7, dt));
            }
            float gap = Mth.wrapDegrees(look - this.bodyYaw);
            if (gap > 50F) this.bodyYaw += gap - 50F;
            else if (gap < -50F) this.bodyYaw += gap + 50F;
            // 몸 방향 (모드 MotionRender.extract: 구르기 · 모루 쪽)
            float bodyYaw = this.bodyYaw;
            float headYaw = Mth.wrapDegrees(look - bodyYaw);
            float headPitch = sp.getXRot();
            if (p != null && !Float.isNaN(p.yaw)) {
                if (p.faceYaw) {
                    float w = Math.max(0F, Math.min(1F, p.w));
                    bodyYaw += Mth.wrapDegrees(p.yaw - bodyYaw) * w;
                    headYaw *= 1F - w;
                    headPitch *= 1F - w;
                } else {
                    bodyYaw = p.yaw;
                    headYaw = 0;
                    headPitch = 0;
                }
            }
            boolean crouch = sp.isCrouching();
            float age = sp.tickCount;
            ItemStack held = sp.getMainHandItem();
            ItemStack heldOff = sp.getOffhandItem();
            // 바닐라 HumanoidModel.setupAnim (걸음 · 고개 · 든 팔 · 숨 · 웅크림)
            head.set(0, 0, 0);
            head.xRot = headPitch * Mth.DEG_TO_RAD;
            head.yRot = headYaw * Mth.DEG_TO_RAD;
            body.set(0, 0, 0);
            rarm.set(-5, slim ? 2.5F : 2, 0);
            larm.set(5, slim ? 2.5F : 2, 0);
            rleg.set(-1.9F, 12, 0);
            lleg.set(1.9F, 12, 0);
            rarm.xRot = Mth.cos(walkPos * 0.6662F + PI) * 2F * walkSpeed * 0.5F;
            larm.xRot = Mth.cos(walkPos * 0.6662F) * 2F * walkSpeed * 0.5F;
            rleg.xRot = Mth.cos(walkPos * 0.6662F) * 1.4F * walkSpeed;
            lleg.xRot = Mth.cos(walkPos * 0.6662F + PI) * 1.4F * walkSpeed;
            Part mainArm = left ? larm : rarm;
            Part offArm = left ? rarm : larm;
            if (!held.isEmpty()) mainArm.xRot = mainArm.xRot * 0.5F - PI / 10F;
            if (!heldOff.isEmpty()) offArm.xRot = offArm.xRot * 0.5F - PI / 10F;
            if (crouch) {
                body.xRot = 0.5F;
                rarm.xRot += 0.4F;
                larm.xRot += 0.4F;
                rleg.z += 4F;
                lleg.z += 4F;
                rleg.y += 0.2F;
                lleg.y += 0.2F;
                head.y += 4.2F;
                body.y += 3.2F;
                larm.y += 3.2F;
                rarm.y += 3.2F;
            }
            rarm.zRot += Mth.cos(age * 0.09F) * 0.05F + 0.05F;
            larm.zRot -= Mth.cos(age * 0.09F) * 0.05F + 0.05F;
            rarm.xRot += Mth.sin(age * 0.067F) * 0.05F;
            larm.xRot -= Mth.sin(age * 0.067F) * 0.05F;
            // 동작 (모드 MotionRender.applyModel)
            float raBend = 0, laBend = 0, rk = 0, lk = 0;
            if (p != null) {
                float w = p.w;
                if (p.has(Pose.BODY) && !crouch) {
                    float lean = lerp(body.xRot, p.lean, w);
                    float twist = lerp(body.yRot, p.twist, w);
                    float side = lerp(body.zRot, p.side, w);
                    Matrix3f rb = new Matrix3f().rotationZYX(side, twist, lean);
                    Vector3f neck = new Vector3f(0, 0, 0).sub(HIP).mul(rb).add(HIP);
                    body.xRot = lean;
                    body.yRot = twist;
                    body.zRot = side;
                    body.x = neck.x;
                    body.y = neck.y;
                    body.z = neck.z;
                    head.x = neck.x;
                    head.y = neck.y;
                    head.z = neck.z;
                    Vector3f rs = new Vector3f(-5, 2, 0).sub(HIP).mul(rb).add(HIP);
                    Vector3f ls = new Vector3f(5, 2, 0).sub(HIP).mul(rb).add(HIP);
                    rarm.x = lerp(rarm.x, rs.x, w);
                    rarm.y = lerp(rarm.y, rs.y, w);
                    rarm.z = lerp(rarm.z, rs.z, w);
                    larm.x = lerp(larm.x, ls.x, w);
                    larm.y = lerp(larm.y, ls.y, w);
                    larm.z = lerp(larm.z, ls.z, w);
                }
                if (p.has(Pose.HEAD)) {
                    head.xRot += p.headX * w;
                    head.yRot += p.headY * w;
                }
                if (p.has(Pose.RA)) {
                    armTo(rarm, p.ra, w);
                    raBend = p.raBend * w;
                }
                if (p.has(Pose.LA)) {
                    armTo(larm, p.la, w);
                    laBend = p.laBend * w;
                }
                float wl = w;
                if (p.softLegs) wl *= 1 - Math.min(1, walkSpeed * 2.5F);
                if (p.has(Pose.LEGS) && wl > 0) {
                    rleg.xRot = lerp(rleg.xRot, p.rlX, wl);
                    rleg.yRot = lerp(rleg.yRot, p.rlY, wl);
                    rleg.zRot = lerp(rleg.zRot, p.rlZ, wl);
                    lleg.xRot = lerp(lleg.xRot, p.llX, wl);
                    lleg.yRot = lerp(lleg.yRot, p.llY, wl);
                    lleg.zRot = lerp(lleg.zRot, p.llZ, wl);
                    rk = p.rk * wl;
                    lk = p.lk * wl;
                }
            }
            // 몸 (LivingEntityRenderer: Ry(180 − 몸) · 뿌리 (MotionRender.rotate) · S(−1, −1, 1) · 0.9375 · T(0, −1.501, 0)) — 탄 자리 (사람 키) 를 뺌
            Matrix4f root = new Matrix4f().translate(0, (float) -sp.getBbHeight() - (crouch ? 0.125F : 0F), 0).rotateY((180F - bodyYaw) * Mth.DEG_TO_RAD);
            // 꾸미기 틀 (j 공간 = 모델 공간을 F = diag(−1, −1, 1) 로 — 발 기준, 탄 자리를 빼지 않음)
            Matrix4f rootJ = new Matrix4f().translate(0, crouch ? -0.125F : 0F, 0).rotateY((180F - bodyYaw) * Mth.DEG_TO_RAD);
            if (p != null && p.has(Pose.ROOT)) {
                float w = p.w;
                float wu = p.softLegs && p.up < 0 ? w * (1 - Math.min(1, walkSpeed * 2.5F)) : w;
                if (p.up != 0 || p.fwd != 0) {
                    root.translate(0, p.up * wu, -p.fwd * w);
                    rootJ.translate(0, p.up * wu, -p.fwd * w);
                }
                if (p.spin != 0) {
                    root.rotateY(p.spin);
                    rootJ.rotateY(p.spin);
                }
                if (p.flip != 0) {
                    root.translate(0, FLIP_PIVOT, 0).rotateX(-p.flip).translate(0, -FLIP_PIVOT, 0);
                    rootJ.translate(0, FLIP_PIVOT, 0).rotateX(-p.flip).translate(0, -FLIP_PIVOT, 0);
                }
            }
            root.scale(-1F, -1F, 1F).scale(0.9375F).translate(0, -1.501F, 0);
            rootJ.scale(0.9375F).translate(0, 1.501F, 0);
            // 부위 T(p) · Rz Ry Rx (모델) → j: T(F p) · Rz(z) · Ry(−y) · Rx(−x)
            bodyJ = new Matrix4f(rootJ).translate(-body.x / 16F, -body.y / 16F, body.z / 16F).rotateZ(body.zRot).rotateY(-body.yRot).rotateX(-body.xRot);
            headJ = new Matrix4f(rootJ).translate(-head.x / 16F, -head.y / 16F, head.z / 16F).rotateZ(head.zRot).rotateY(-head.yRot).rotateX(-head.xRot);
            Matrix4f mh = head.matrix(root);
            Matrix4f mb = body.matrix(root);
            Matrix4f mra = rarm.matrix(root);
            Matrix4f mla = larm.matrix(root);
            Matrix4f mrl = rleg.matrix(root);
            Matrix4f mll = lleg.matrix(root);
            float rjx = slim ? -0.5F : -1F;
            float ljx = slim ? 0.5F : 1F;
            set(HEAD, mh);
            set(BODY, mb);
            set(RA_U, mra);
            set(RA_L, bend(mra, rjx, 4, 0, raBend));
            set(LA_U, mla);
            set(LA_L, bend(mla, ljx, 4, 0, laBend));
            set(RL_U, mrl);
            set(RL_L, bend(mrl, 0, 6, 0, rk));
            set(LL_U, mll);
            set(LL_L, bend(mll, 0, 6, 0, lk));
            // 머리 장식 (CustomHeadLayer: 머리 · T(0, −0.25, 0) · Ry(180°) · S(0.625, −0.625, −0.625) — 아이템 표시의 Y 180° 와 S 는 바꿔 놓아도 같음)
            ItemStack hatStack = VanillaCosmetics.hatFor(sp);
            String hk = hatStack.isEmpty() ? "" : String.valueOf(hatStack.getComponents().hashCode());
            if (!hk.equals(hatKey)) {
                hatKey = hk;
                hat.setItem(hatStack);
            }
            if (!hatStack.isEmpty()) {
                hat.setTransformation(new Matrix4f(mh).translate(0, -0.25F, 0).scale(0.625F, -0.625F, -0.625F));
                hat.startInterpolationIfDirty();
            }
            // 든 것 (ItemInHandLayer — 손 자리 (가는 팔은 바깥으로 0.5 픽셀) · 굽힘 · 비틀기 · 손목, 동작의 맨손 · 소품)
            Matrix4f rh = slim ? rarm.matrix(root, 0.5F) : mra;
            Matrix4f lh = slim ? larm.matrix(root, -0.5F) : mla;
            hand(true, left ? lh : rh, left ? laBend : raBend, left ? ljx : rjx, p, held, left);
            hand(false, left ? rh : lh, left ? raBend : laBend, left ? rjx : ljx, p, heldOff, left);
        }

        private static float lerp(float a, float b, float u) {
            return a + (b - a) * u;
        }

        private static void armTo(Part arm, Quaternionf target, float w) {
            Quaternionf vanilla = new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot);
            Quaternionf q = vanilla.slerp(target, w, new Quaternionf());
            Vector3f e = new Matrix3f().set(q).getEulerAnglesZYX(new Vector3f());
            arm.xRot = e.x;
            arm.yRot = e.y;
            arm.zRot = e.z;
        }

        /** 팔꿈치 · 무릎 아래 (관절 j — 픽셀, x 축으로 a 라디안) */
        private static Matrix4f bend(Matrix4f m, float jx, float jy, float jz, float a) {
            if (a == 0) return m;
            return new Matrix4f(m).translate(jx / 16F, jy / 16F, jz / 16F).rotateX(a).translate(-jx / 16F, -jy / 16F, -jz / 16F);
        }

        private void set(int i, Matrix4f part) {
            Matrix4f d = new Matrix4f(part).rotateX(PI);
            bones[i].setTransformation(d);
            bones[i].startInterpolationIfDirty();
        }

        /**
         * 손에 든 것 (바닐라 ItemInHandLayer — PlayerModel.translateToHand (가는 팔 ±0.5) · 모드 MotionRender.hand (굽힘 · 비틀기 · 손목) · Rx(−90°) · Ry(180°) ·
         * T(±1/16, 0.125, −0.625)) · 아이템 표시의 Y 180° 를 되돌림
         */
        private void hand(boolean mainHand, Matrix4f arm, float bendA, float jx, Pose p, ItemStack st, boolean leftHanded) {
            ItemDisplayElement e = mainHand ? main : off;
            boolean rightSide = mainHand != leftHanded;
            e.setItemDisplayContext(rightSide ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
            // 동작의 손 (맨손 · 숨김 · 소품)
            String prop = null;
            boolean bare = false;
            if (p != null) {
                if (mainHand) {
                    bare = p.hide || p.mainBare();
                    if (p.hold != null && p.w > 0 && (p.mainMode == MotionPoses.WEAPON || p.propNow())) prop = p.hold;
                } else {
                    bare = p.offBare();
                    if (p.holdOff != null && p.w > 0 && p.offMode == MotionPoses.PROP && p.holdOffNow) prop = p.holdOff;
                }
            }
            ItemStack want = prop != null ? propStack(prop) : bare ? ItemStack.EMPTY : st;
            String key = prop != null ? "prop:" + prop : bare || want.isEmpty() ? "" : String.valueOf(System.identityHashCode(want.getItem())) + ":" + want.getComponents().hashCode();
            if (!key.equals(mainHand ? lastMainKey : lastOffKey)) {
                ItemStack shownStack = want.isEmpty() ? ItemStack.EMPTY : prop != null ? want : VanillaCosmetics.handStack(sp, want);
                e.setItem(shownStack);
                if (mainHand) lastMainKey = key;
                else lastOffKey = key;
            }
            if (want.isEmpty()) return;
            Matrix4f m = new Matrix4f(arm);
            if (bendA != 0) m.translate(jx / 16F, 4 / 16F, 0).rotateX(bendA).translate(-jx / 16F, -4 / 16F, 0);
            if (p != null && p.has(rightSide ? Pose.RA : Pose.LA)) {
                boolean r = rightSide;
                float tw = (r ? p.raTwist : p.laTwist) * p.w;
                float wr = (r ? p.raWrist : p.laWrist) * p.w;
                float dv = (r ? p.raDev : p.laDev) * p.w;
                float cx = jx / 16F;
                if (tw != 0) m.translate(cx, 0, 0).rotateY(tw).translate(-cx, 0, 0);
                if (wr != 0 || dv != 0) {
                    float wy = 9.5F / 16F;
                    m.translate(cx, wy, 0);
                    if (wr != 0) m.rotateX(wr);
                    if (dv != 0) m.rotateZ(dv);
                    m.translate(-cx, -wy, 0);
                }
            }
            m.rotateX(-PI / 2F).rotateY(PI).translate((rightSide ? 1 : -1) / 16F, 0.125F, -0.625F).rotateY(PI);
            e.setTransformation(m);
            e.startInterpolationIfDirty();
        }
    }

    /**
     * 뼈 몸의 이름 (진짜 몸의 이름표는 투명하고 탄 것이 있으면 바닐라가 그리지 않음): 머리 위 0.5칸 (바닐라 이름표 자리). 따로 붙인 홀더 — 내 화면에는 보내지 않음
     * (바닐라도 내 이름은 그리지 않음). 모드 클라이언트도 아님 (진짜 몸 · 이름이 그대로)
     */
    static final class NameTag extends ElementHolder {
        final ServerPlayer sp;
        final TextDisplayElement text;
        boolean attached;

        NameTag(ServerPlayer sp) {
            this.sp = sp;
            text = new TextDisplayElement(sp.getDisplayName() != null ? sp.getDisplayName() : Component.literal(sp.getGameProfile().name()));
            text.setBillboardMode(Display.BillboardConstraints.CENTER);
            text.setBackground(0x40000000);
            text.setShadow(false);
            text.setSeeThrough(false);
            text.setTranslation(new Vector3f(0, 0.5F, 0));
            text.ignorePositionUpdates();
            addPassengerElement(text);
        }

        @Override
        public boolean startWatching(ServerGamePacketListenerImpl c) {
            ServerPlayer p = c.getPlayer();
            if (p == sp || VanillaClients.isModded(p)) return false;
            if (!super.startWatching(c)) return false;
            if (attached) c.send(new ClientboundSetPassengersPacket(sp));
            return true;
        }
    }

    // ------------------------------------------------------------ 아이템

    private static final Map<String, ItemStack> PROPS = new HashMap<>();

    private static ItemStack propStack(String model) {
        return PROPS.computeIfAbsent(model, k -> {
            Identifier id = Identifier.tryParse(k);
            if (id == null) return ItemStack.EMPTY;
            ItemStack s = new ItemStack(Items.STICK);
            s.set(DataComponents.ITEM_MODEL, id);
            return s;
        });
    }

    /** 뼈 하나의 아이템: rpg:vrig/… 모델 · custom_model_data 색 (tint 순서) */
    private static ItemStack stack(BoneDef d, Skin s) {
        List<Integer> colors = new ArrayList<>(d.u().length);
        boolean anyOuter = false;
        for (int i = 0; i < d.u().length; i++) {
            if (d.avg()[i] != null) {
                long r = 0, g = 0, b = 0;
                int[] a = d.avg()[i];
                int n = a.length / 2;
                for (int k = 0; k < n; k++) {
                    int c = s.argb()[a[2 * k + 1] * 64 + a[2 * k]];
                    r += (c >> 16) & 0xFF;
                    g += (c >> 8) & 0xFF;
                    b += c & 0xFF;
                }
                colors.add(n == 0 ? -1 : 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n));
                continue;
            }
            int c = s.argb()[d.v()[i] * 64 + d.u()[i]];
            if (d.outer()[i]) {
                // 겉옷 층: 반 넘게 보이는 칸은 그 색, 투명한 칸은 밑 몸 칸의 색 (겉옷 껍데기가 몸처럼 보이게)
                if ((c >>> 24) >= 128) {
                    anyOuter = true;
                    colors.add(0xFF000000 | c);
                } else {
                    colors.add(0xFF000000 | s.argb()[d.bv()[i] * 64 + d.bu()[i]]);
                }
                continue;
            }
            colors.add(0xFF000000 | c);
        }
        ItemStack st = new ItemStack(Items.PAPER);
        st.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(Mmorpg.NS, d.item()));
        st.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(anyOuter), List.of(), colors));
        return st;
    }

    /** 바닐라 기본 스킨의 뼈 하나: rpg:vrig/def/&lt;형&gt;/&lt;뼈&gt; · custom_model_data 문자열 0 = 이름 (그림은 클라이언트의 minecraft:entity/player/…) */
    private static ItemStack vanillaStack(String variant, int bone, String name) {
        ItemStack st = new ItemStack(Items.PAPER);
        st.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(Mmorpg.NS, "vrig/def/" + variant + "/" + NAMES[bone]));
        st.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(name), List.of()));
        return st;
    }

    // ------------------------------------------------------------ 스킨

    /** 그 사람의 스킨 (받는 중이면 클라이언트가 그리는 기본 스킨 — 받으면 바뀜) */
    static Skin skinOf(ServerPlayer sp) {
        Skin s = SKINS.get(sp.getUUID());
        if (s != null) return s;
        if (LOADING.add(sp.getUUID())) fetch(sp);
        return defaultFor(sp.getUUID());
    }

    /** 바닐라 기본 스킨 목록 (VanillaPack — 그 그림을 아이템 아틀라스에 넣음): "slim/alex" … */
    public static List<String> defaultSkins() {
        if (!DEFAULTS.isEmpty()) return List.copyOf(DEFAULTS);
        List<String> out = new ArrayList<>();
        if (read("/mmorpg/vanilla_rig.json").get("defaults") instanceof List<?> dl) for (Object o : dl) out.add(String.valueOf(o));
        return out;
    }

    /** 프로필의 textures (base64 JSON — SKIN url · 가는 팔) → 텍스처 서버에서 PNG (비동기) */
    private static void fetch(ServerPlayer sp) {
        UUID id = sp.getUUID();
        String url = null;
        boolean slim = false;
        try {
            for (Object po : textures(sp.getGameProfile().properties())) {
                if (!(po instanceof com.mojang.authlib.properties.Property p)) continue;
                String json = new String(Base64.getDecoder().decode(p.value()), StandardCharsets.UTF_8);
                Map<String, Object> o = Json.parseObject(json);
                Object tex = o.get("textures");
                if (tex instanceof Map<?, ?> t && t.get("SKIN") instanceof Map<?, ?> sk) {
                    url = String.valueOf(sk.get("url"));
                    if (sk.get("metadata") instanceof Map<?, ?> md && "slim".equals(String.valueOf(md.get("model")))) slim = true;
                }
            }
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[vanilla] rig skin profile {}: {}", sp.getGameProfile().name(), e.toString());
        }
        if (url == null || url.isEmpty() || "null".equals(url)) {
            SKINS.put(id, defaultFor(id)); // 오프라인 서버 등: 클라이언트도 기본 스킨 (같은 것)
            return;
        }
        String u = url.startsWith("http://") ? "https://" + url.substring(7) : url;
        boolean sl = slim;
        Skin cached = BY_URL.get(u);
        if (cached != null) {
            SKINS.put(id, cached);
            return;
        }
        HTTP.sendAsync(HttpRequest.newBuilder(URI.create(u)).timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofByteArray())
                .whenComplete((resp, err) -> {
                    Skin s = null;
                    if (err == null && resp != null && resp.statusCode() == 200) {
                        try {
                            s = decode(resp.body(), sl);
                        } catch (IOException | RuntimeException e) {
                            Mmorpg.LOG.warn("[vanilla] rig skin decode {}: {}", u, e.toString());
                        }
                    } else {
                        Mmorpg.LOG.warn("[vanilla] rig skin fetch {}: {}", u, err != null ? err.toString() : resp == null ? "no response" : "HTTP " + resp.statusCode());
                    }
                    Skin got = s != null ? s : defaultFor(id);
                    if (s != null) BY_URL.put(u, s);
                    SKINS.put(id, got);
                });
    }

    /** 프로필 속성의 textures 값들 (authlib 판마다 PropertyMap 모양이 달라 Multimap 으로 봄 — 아니면 그 안의 properties()) */
    private static java.util.Collection<?> textures(Object props) {
        Object m = props;
        if (!(m instanceof com.google.common.collect.Multimap<?, ?>)) {
            try {
                m = props.getClass().getMethod("properties").invoke(props);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return List.of();
            }
        }
        if (m instanceof com.google.common.collect.Multimap<?, ?> mm) {
            java.util.Collection<?> c = mm.asMap().get("textures");
            return c == null ? List.of() : c;
        }
        return List.of();
    }

    /** PNG → 64 × 64 ARGB (64 × 32 옛 스킨은 바닐라처럼 왼팔 · 왼다리를 오른쪽에서 거울로) */
    static Skin decode(byte[] png, boolean slim) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        if (img == null || img.getWidth() != 64 || (img.getHeight() != 64 && img.getHeight() != 32)) throw new IOException("skin size");
        int[] px = new int[64 * 64];
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < 64; x++) px[y * 64 + x] = img.getRGB(x, y);
        if (img.getHeight() == 32) {
            int[][] copies = {{4, 16, 16, 32, 4, 4}, {8, 16, 16, 32, 4, 4}, {0, 20, 24, 32, 4, 12}, {4, 20, 16, 32, 4, 12}, {8, 20, 8, 32, 4, 12},
                    {12, 20, 16, 32, 4, 12}, {44, 16, -8, 32, 4, 4}, {48, 16, -8, 32, 4, 4}, {40, 20, 0, 32, 4, 12}, {44, 20, -8, 32, 4, 12},
                    {48, 20, -16, 32, 4, 12}, {52, 20, -8, 32, 4, 12}};
            for (int[] c : copies) {
                for (int j = 0; j < c[5]; j++) {
                    for (int i = 0; i < c[4]; i++) {
                        int sx = c[0] + i;
                        int sy = c[1] + j;
                        int dx = c[0] + c[2] + (c[4] - 1 - i);
                        int dy = c[1] + c[3] + j;
                        px[dy * 64 + dx] = px[sy * 64 + sx];
                    }
                }
            }
        }
        return new Skin(px, slim, null);
    }

    // ------------------------------------------------------------ 읽기

    private static void load() {
        Map<String, Object> mf = read("/mmorpg/vanilla_rig.json");
        Object vs = mf.get("variants");
        if (vs instanceof Map<?, ?> variants) {
            for (Map.Entry<?, ?> v : variants.entrySet()) {
                if (!(v.getValue() instanceof Map<?, ?> vm) || !(vm.get("bones") instanceof List<?> bl)) continue;
                BoneDef[] defs = new BoneDef[BONES];
                for (Object bo : bl) {
                    if (!(bo instanceof Map<?, ?> b)) continue;
                    String nm = String.valueOf(b.get("name"));
                    int idx = java.util.Arrays.asList(NAMES).indexOf(nm);
                    if (idx < 0 || !(b.get("src") instanceof List<?> src)) continue;
                    int n = src.size();
                    int[] u = new int[n];
                    int[] vv = new int[n];
                    boolean[] outer = new boolean[n];
                    int[] bu = new int[n];
                    int[] bv = new int[n];
                    int[][] avg = new int[n][];
                    for (int i = 0; i < n; i++) {
                        Object s = src.get(i);
                        if (s instanceof List<?> l && l.size() >= 3) {
                            u[i] = ((Number) l.get(0)).intValue();
                            vv[i] = ((Number) l.get(1)).intValue();
                            outer[i] = ((Number) l.get(2)).intValue() != 0 && l.size() >= 5;
                            if (outer[i]) {
                                bu[i] = ((Number) l.get(3)).intValue();
                                bv[i] = ((Number) l.get(4)).intValue();
                            }
                        } else if (s instanceof Map<?, ?> am && am.get("avg") instanceof List<?> al) {
                            int[] a = new int[al.size() * 2];
                            for (int k = 0; k < al.size(); k++) {
                                List<?> pt = (List<?>) al.get(k);
                                a[2 * k] = ((Number) pt.get(0)).intValue();
                                a[2 * k + 1] = ((Number) pt.get(1)).intValue();
                            }
                            avg[i] = a;
                        }
                    }
                    defs[idx] = new BoneDef(String.valueOf(b.get("item")), u, vv, outer, bu, bv, avg);
                }
                boolean all = true;
                for (BoneDef d : defs) all &= d != null;
                if (all) VARIANTS.put(String.valueOf(v.getKey()), defs);
            }
        }
        if (mf.get("defaults") instanceof List<?> dl) for (Object o : dl) DEFAULTS.add(String.valueOf(o));
        try (InputStream in = VanillaRig.class.getResourceAsStream("/assets/mmorpg/rig/default_skin.png")) {
            if (in != null) DEFAULT = decode(in.readAllBytes(), false);
        } catch (IOException e) {
            Mmorpg.LOG.warn("[vanilla] rig default skin: {}", e.toString());
        }
        Mmorpg.LOG.info("[vanilla] rig variants {} (vanilla default skins {}, ours {})", VARIANTS.keySet(), DEFAULTS.size(), DEFAULT != null);
    }

    private static Map<String, Object> read(String resource) {
        try (InputStream in = VanillaRig.class.getResourceAsStream(resource)) {
            if (in == null) return Map.of();
            return Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            Mmorpg.LOG.warn("[vanilla] {}: {}", resource, e.toString());
            return Map.of();
        }
    }
}
