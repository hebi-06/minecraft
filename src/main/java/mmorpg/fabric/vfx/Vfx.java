package mmorpg.fabric.vfx;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import mmorpg.core.Json;
import mmorpg.fabric.Mmorpg;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 2026-11-11 3D 효과 엔진 (사용자: 룬 · 직업 스킬 모션 — "골렘 룬과 같이 3d 몹 + 2d 연출", "모든 스킬들은 최대한 화려하게", 모드 없이 접속해 리소스만 받으면 됨):
 * 모드 없는 클라이언트도 보는 효과. 서버가 Polymer 가상 표시 엔티티 (그 청크를 보는 모든 사람 — 모드 클라이언트도 같은 것) 를 띄우고 움직임.
 * <ul>
 * <li>입체 소품 {@link Prop} — 아이템 표시 (아이템 모델 = 사용자 무기 디자인 · 효과 모델, 리소스 팩 assets/rpg): 자리 (엔티티 자리 + 옮김 보간) · 회전 · 크기
 *     (변환 보간). 쥔 곳 (grip — 모델 픽셀) 을 중심으로 돎.</li>
 * <li>2D 그림 {@link Sprite} — 글자 표시 (글꼴 rpg:vfx — tools/vfx_sprites.py 의 그림판, 흰 그림을 글자 색으로 물들임): 카메라를 봄 (billboard) 또는
 *     정한 방향 (땅에 눕힌 고리 · 휘두른 쪽의 초승달), 칸 (그림 차례) · 투명도 (글자 불투명도) · 크기.</li>
 * <li>효과 하나 = {@link Fx} (ElementHolder — 세계의 한 점에 붙음, 틱마다 정해 둔 일을 함 · life 틱 뒤 사라짐). 서버가 꺼지면 모두 지움 (저장하지 않음).</li>
 * </ul>
 * 움직임은 틱마다 (또는 n 틱마다) 다음 모습을 보내고 그 사이는 클라이언트가 보간 — 화면 흔들림은 쓰지 않음 (사용자 "멀미나").
 */
public final class Vfx {
    private Vfx() {}

    /** 글자 표시 엔티티: 글꼴 1 단위 = 0.025칸 */
    static final double TEXT_UNIT = 0.025;
    /** 그림 글꼴 */
    public static final Identifier FONT_ID = Mmorpg.rpg("vfx");
    static final FontDescription FONT = new FontDescription.Resource(FONT_ID);
    /** 글자 불투명도: 마인크래프트가 0.1 아래를 버리고 4 아래는 불투명으로 바꿈 → 26 이 가장 옅은 값 (그 아래는 숨김) */
    static final int MIN_OPACITY = 26;
    /** 아이템 표시는 아이템을 Y 둘레로 180° 돌려 그림 (DisplayRenderer.ItemDisplayRenderer) — 오른쪽 회전으로 되돌려 모델 축 = 우리 축 */
    static final boolean ITEM_Y_FLIP = true;
    /** 표시 엔티티의 자리 보간 (teleport_duration) 최대 틱 */
    static final int MAX_TELEPORT = 59;
    /** 한 번에 떠 있는 효과 수 (넘으면 가장 오래된 것부터 지움 — 여럿이 한꺼번에 써도 버티게) */
    static final int MAX_FX = 400;

    /**
     * 그림판 하나 (vfx_sprites.json) — aspect = 예전 칸의 가로세로 비 · cx · cy = 2026-11-16 빈 가장자리를 자른 칸 (tools/vfx_sprites.py crop_cells — 사용자 "8gb 지원")
     * 을 옮길 자리 (예전 칸 높이 몫 — 오른쪽 · 위): 그림은 예전 칸의 픽셀 자리 그대로
     */
    public record Sheet(String name, String chars, int frames, double aspect, boolean colored, double cx, double cy) {
        /**
         * 칸 frame 의 글자 (2026-11-16 코드 포인트로 — 글자 번호가 U+FFFF 를 넘은 그림판 (글꼴 그림 1만 넘게) 은 글자 하나가 두 char (서로게이트 쌍) 라 char 로 고르면
         * 짝 없는 반쪽이 가서 대체 문자 U+FFFD 의 그림 (다른 그림판) 이 보였음 — CI 43903024474 숨긴 직업 영상의 보랏빛 X 베기)
         */
        public String glyph(int frame) {
            int i = chars.offsetByCodePoints(0, Math.floorMod(frame, frames));
            return new String(Character.toChars(chars.codePointAt(i)));
        }
    }

    static final Map<String, Sheet> SHEETS = new HashMap<>();
    static int glyphHeight = 64;
    private static final List<Fx> ACTIVE = new ArrayList<>();
    /** 시험용: 띄운 효과 · 소품 · 그림 수 */
    public static int fxMade;
    public static int propsMade;
    public static int spritesMade;
    /** 2026-11-16 그림판마다 띄운 그림 수 (영상 셈 jobcensus — 쓰지 않는 그림판을 찾아 글꼴 무게를 줄임, 사용자 "8gb 지원") */
    public static final Map<String, Integer> USED = new java.util.TreeMap<>();
    /** 2026-11-16 그림판마다 가장 큰 그림 크기 (칸 — 조각 그림은 조각 하나): 화면에서 얼마나 크게 보이는지 → 그림판 해상도 예산 */
    public static final Map<String, Double> SIZE_MAX = new java.util.TreeMap<>();

    public static void init() {
        load();
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
    }

    @SuppressWarnings("unchecked")
    static void load() {
        try (InputStream in = Vfx.class.getResourceAsStream("/mmorpg/vfx_sprites.json")) {
            if (in == null) {
                Mmorpg.LOG.warn("[vfx] no sprite index");
                return;
            }
            Map<String, Object> root = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            if (root.get("h") instanceof Number n) glyphHeight = n.intValue();
            Map<String, Object> sprites = (Map<String, Object>) root.get("sprites");
            for (Map.Entry<String, Object> e : sprites.entrySet()) {
                Map<String, Object> m = (Map<String, Object>) e.getValue();
                double cx = 0, cy = 0;
                if (m.get("crop") instanceof List<?> c && c.size() == 2) {
                    cx = ((Number) c.get(0)).doubleValue();
                    cy = ((Number) c.get(1)).doubleValue();
                }
                SHEETS.put(e.getKey(), new Sheet(e.getKey(), (String) m.get("chars"), ((Number) m.get("frames")).intValue(),
                        ((Number) m.get("aspect")).doubleValue(), Boolean.TRUE.equals(m.get("colored")), cx, cy));
            }
            Mmorpg.LOG.info("[vfx] sprites {}", SHEETS.size());
        } catch (IOException | RuntimeException e) {
            Mmorpg.LOG.warn("[vfx] sprite index: {}", e.toString());
        }
    }

    /** 2026-11-15 이름이 prefix 로 시작하는 그림판 이름들 (차례대로) */
    public static List<String> sheetNames(String prefix) {
        List<String> out = new ArrayList<>();
        for (String k : SHEETS.keySet()) if (k.startsWith(prefix)) out.add(k);
        java.util.Collections.sort(out);
        return out;
    }

    /**
     * 2026-11-15 그림 몸풀기: prefix 그림판의 모든 칸을 at 에 아주 작게 (0.02칸) 한 틱씩 차례로 띄움 — 클라이언트가 글꼴 그림을 미리 올림 (처음 보는 큰 그림은
     * 몇 틱 늦게 나타났음 — CI 43668850426 팬텀 버스트 3인칭 폭발 5장). at 은 보는 사람의 화면 안 (눈앞) 이어야 함. 반환 = 걸리는 틱
     */
    public static int warm(ServerLevel level, Vec3 at, String prefix) {
        List<String> names = sheetNames(prefix);
        int longest = 1;
        for (String n : names) longest = Math.max(longest, SHEETS.get(n).frames());
        Fx fx = at(level, at, longest + 6);
        for (String n : names) {
            Sprite sp = fx.sprite(n, at);
            sp.size(0.02);
            sp.play(0, 0, SHEETS.get(n).frames(), 1, false);
        }
        return longest + 2;
    }

    /** 2026-11-16 이 이름의 그림판이 있는지 (자주 묻는 곳 — sheetNames 는 모두 훑음) */
    public static boolean hasSheet(String name) {
        return SHEETS.containsKey(name);
    }

    public static Sheet sheet(String name) {
        Sheet s = SHEETS.get(name);
        if (s == null) throw new IllegalArgumentException("vfx sprite " + name);
        return s;
    }

    /** 지금 떠 있는 효과 수 (시험용) */
    public static int active() {
        return ACTIVE.size();
    }

    static void clear() {
        for (Fx f : new ArrayList<>(ACTIVE)) f.end();
        ACTIVE.clear();
    }

    /** at (세계 자리) 에 붙은 효과 하나 — life 틱 뒤 스스로 사라짐 */
    public static Fx at(ServerLevel level, Vec3 at, int life) {
        return at(level, at, life, null);
    }

    /**
     * 2026-11-15 같은 것 — 볼 사람을 붙이기 전에 정함 (붙는 순간 그 자리를 보는 사람에게 보내지므로 뒤에 정하면 늦음): 1인칭 눈앞 그림 (주인의 1인칭에만) · 몸 앞 판
     * (주인이 1인칭이면 숨김)
     */
    public static Fx at(ServerLevel level, Vec3 at, int life, Predicate<ServerPlayer> viewers) {
        Fx fx = new Fx(level, at, life);
        fx.viewers = viewers;
        if (ACTIVE.size() >= MAX_FX) ACTIVE.get(0).end();
        ACTIVE.add(fx);
        ChunkAttachment.ofTicking(fx, level, at);
        fxMade++;
        return fx;
    }

    /** 0 ~ 1 색 → RGB 정수 */
    public static int rgb(double[] c) {
        int r = (int) Math.round(Math.max(0, Math.min(1, c[0])) * 255);
        int g = (int) Math.round(Math.max(0, Math.min(1, c[1])) * 255);
        int b = (int) Math.round(Math.max(0, Math.min(1, c[2])) * 255);
        return r << 16 | g << 8 | b;
    }

    /** 두 색 사이 (t = 0 → a, 1 → b) */
    public static double[] mix(double[] a, double[] b, double t) {
        return new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    // ------------------------------------------------------------ 방향 도움

    /** 축 셋 (ex = ey × ez, 서로 직교하는 단위 벡터) 으로 가는 회전 — 모델 x · y · z 가 세계의 ex · ey · ez 로 */
    public static Quaternionf basis(Vec3 ex, Vec3 ey, Vec3 ez) {
        org.joml.Matrix3f m = new org.joml.Matrix3f((float) ex.x, (float) ex.y, (float) ex.z, (float) ey.x, (float) ey.y, (float) ey.z, (float) ez.x,
                (float) ez.y, (float) ez.z);
        return new Quaternionf().setFromNormalized(m);
    }

    /** 앞이 dir (모델 +z) · 위가 up 쪽인 회전 (up 이 dir 과 나란하면 다른 위) */
    public static Quaternionf look(Vec3 dir, Vec3 up) {
        Vec3 z = dir.normalize();
        Vec3 u = Math.abs(z.dot(up.normalize())) > 0.98 ? (Math.abs(z.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)) : up;
        Vec3 x = u.cross(z).normalize();
        Vec3 y = z.cross(x).normalize();
        return basis(x, y, z);
    }

    /** 글자 그림을 땅에 눕힘 (그림 앞면이 위 — 그림 위쪽이 north 쪽, yaw 도만큼 돎) */
    public static Quaternionf flat(double yawDeg) {
        return new Quaternionf().rotateY((float) Math.toRadians(-yawDeg)).rotateX((float) (-Math.PI / 2));
    }

    // ------------------------------------------------------------ 효과 하나

    /** 세계의 한 점에 붙은 효과 (소품 · 그림 · 틱마다 할 일) */
    public static final class Fx extends ElementHolder {
        final ServerLevel level;
        final Vec3 origin;
        int life;
        int age;
        boolean ended;
        private final List<Job> jobs = new ArrayList<>();
        final List<Prop> props = new ArrayList<>();
        final List<Sprite> sprites = new ArrayList<>();
        /** 이 사람에게는 보이지 않음 (예: 쓴 사람의 눈앞을 덮는 그림 — null 이면 모두) */
        Predicate<ServerPlayer> viewers;

        private record Job(int from, int to, int step, IntConsumer fn) {}

        Fx(ServerLevel level, Vec3 origin, int life) {
            this.level = level;
            this.origin = origin;
            this.life = Math.max(1, life);
        }

        public ServerLevel level() {
            return level;
        }

        public Vec3 origin() {
            return origin;
        }

        public int age() {
            return age;
        }

        /** 2026-11-16 수명 (틱 — 이 나이에 끝남) */
        public int life() {
            return life;
        }

        /** 더 오래 (지금 남은 것보다 길면) */
        public Fx keep(int ticks) {
            life = Math.max(life, age + ticks);
            return this;
        }

        /** tick (이 효과의 나이) 에 fn — 이미 지났거나 지금이면 바로 (틱 안의 일에서 부르면 같은 틱) */
        public Fx on(int tick, Runnable fn) {
            if (tick <= age) {
                if (!ended) fn.run();
                return this;
            }
            Runnable g = mmorpg.fabric.Tasks.bind(fn); // 2026-11-17 부른 때의 그림 맥락 (꼬리표 · 칠할 묶음 · 주인) 으로 — 시간표 안의 예전 그림도 칠함
            jobs.add(new Job(tick, tick, 1, t -> g.run()));
            keep(tick + 1);
            return this;
        }

        /** from ~ to 틱 동안 step 틱마다 fn(나이) */
        public Fx each(int from, int to, int step, IntConsumer fn) {
            jobs.add(new Job(from, to, Math.max(1, step), mmorpg.fabric.Tasks.bind(fn)));
            keep(to + 1);
            return this;
        }

        /** 입체 소품 (아이템 모델 — 아이템 정의 id, 예: rpg:vfx/rune_axe) */
        public Prop prop(String itemModel, Vec3 at) {
            Prop p = new Prop(this, itemModel, at);
            p.show();
            props.add(p);
            propsMade++;
            eyeCheck();
            return p;
        }

        /**
         * 2026-11-13 처음 모습을 정한 뒤 띄움: init (쥔 곳 · 빛 · 물들임 · 자리 · 회전 · 크기) 을 마친 다음에 홀더에 넣음 — 띄우는 패킷에 처음 모습이 담김
         * (예전: 띄운 뒤에 준 회전 · 물들임이 다음 틱에 가서 첫 1 ~ 2틱 동안 물들지 않은 채 곧게 선 모델이 보였음 — CI 43386949877 삼단베기)
         */
        public Prop prop(String itemModel, Vec3 at, java.util.function.Consumer<Prop> init) {
            Prop p = new Prop(this, itemModel, at);
            if (init != null) init.accept(p);
            p.show();
            props.add(p);
            propsMade++;
            eyeCheck();
            return p;
        }

        /** 조각 물체 (mmorpg/vfx_shapes.json 의 이름 — tools/vfx_shapes.py: 파도 · 물기둥 · 기의 손바닥) */
        public Group group(String shape, Vec3 at) {
            return group(shape, at, null);
        }

        /**
         * 2026-11-13 조각 물체를 init (물들임 · 밝기 · 첫 모습) 을 마친 뒤 띄움 — 띄운 뒤 바꾸면 모드 없는 클라이언트가 한두 틱 물들지 않은 · 처음 회전의 조각을 봄
         * (Prop 과 같음)
         */
        public Group group(String shape, Vec3 at, java.util.function.Consumer<Group> init) {
            Group g = new Group(this, shape(shape), at);
            if (init != null) init.accept(g);
            g.show();
            propsMade++;
            return g;
        }

        /** 2D 그림 (글꼴 rpg:vfx 의 그림판 이름) — 카메라를 봄 */
        public Sprite sprite(String sheet, Vec3 at) {
            Sprite s = new Sprite(this, sheet(sheet), at);
            sprites.add(s);
            spritesMade++;
            USED.merge(sheet, 1, Integer::sum);
            return s;
        }

        /** 2026-11-15 이 효과의 그림을 모두 delta 만큼 ticks 틱 (보간 — 59틱까지) 에 옮김 (날아가는 검기 — 조각 그림이 함께) */
        public Fx shift(Vec3 delta, int ticks) {
            for (Sprite s : sprites) s.to(s.pos.add(delta), -1, Math.min(59, Math.max(0, ticks)));
            return this;
        }

        /** 2026-11-15 이 효과의 그림을 모두 ticks 틱에 옅어져 사라지게 (지금부터) */
        public Fx fadeAll(int ticks) {
            for (Sprite s : sprites) s.fade(age, ticks);
            return this;
        }

        /** 이 효과를 볼 사람 (null = 모두) — 띄우기 전에 */
        public Fx viewers(Predicate<ServerPlayer> who) {
            this.viewers = who;
            return this;
        }

        public void end() {
            if (ended) return;
            ended = true;
            ACTIVE.remove(this);
            destroy();
        }

        @Override
        public boolean startWatching(net.minecraft.server.network.ServerGamePacketListenerImpl player) {
            if (viewers != null && !viewers.test(player.getPlayer())) return false;
            return super.startWatching(player);
        }

        /**
         * 2026-11-17 1인칭 눈이 입체 소품 속에 들거나 소품 · 큰 그림이 눈앞을 덮으면 그 사람에게서 그 소품 · 그림 하나만 거둠 · 벗어나면 다시 보냄 (룬 1인칭 영상
         * CI 44002166301 — 힘 영웅 거대한 망치 · 검 초월 거대한 검 · 도끼 초월이 쓴 사람의 눈을 지나며 화면이 잿빛 · 흙빛 · 핏빛 한 면으로, 암살 초월 · 일반의 대상 바로 앞
         * X 베기 · 연기가 보랏빛 도트 면으로 덮였음). 소품 = 모델 조각 상자 (Prop.contains — 여유 EYE_MARGIN 칸) · 눈앞 2.5칸 안에서 화면을 덮는 몫 (Prop.coverage 0.7 —
         * 다시 보냄 0.55), 그림 = Sprite.covers (1.5 — 다시 보냄 1.75, 경계에서 깜빡이지 않게). 3인칭 (영상 카메라) · 1인칭 눈앞에 일부러 둔 그림 (FirstPerson) 은 그대로.
         * 1판 (cf7434bf ~ 06608825) 은 효과 전체 (홀더) 를 거둬서, 몸 둘레 불티가 덮는 도끼 초월은 같은 홀더의 땅 문양 · 도끼까지 1인칭 내내 사라졌음 → 덮는 것만
         */
        private void eyeCull() {
            if (viewers instanceof FirstPerson) return;
            for (ServerPlayer p : level.players()) {
                Vec3 eye = p.getEyePosition();
                boolean fp = eye.distanceToSqr(origin) < 48 * 48 && !PBridge.thirdPersonOf(p);
                Vec3 look = fp ? p.getViewVector(1F) : null;
                for (Prop pr : props) {
                    if (pr.removed) continue;
                    boolean was = pr.eyeOff.contains(p.getId());
                    boolean in = fp && (pr.contains(eye, EYE_MARGIN) || pr.coverage(eye, look) >= (was ? 0.55 : 0.7));
                    if (in != was) eyeShow(pr.e, pr.eyeOff, p, !in);
                }
                for (Sprite sp : sprites) {
                    if (sp.removed) continue;
                    boolean was = sp.eyeOff.contains(p.getId());
                    boolean in = fp && sp.covers(eye, was ? 1.75 : 1.5);
                    if (in != was) eyeShow(sp.e, sp.eyeOff, p, !in);
                }
            }
        }

        /** 그림 · 소품 하나 (e) 를 한 사람에게서 거둠 (거둔 사람 off — 그 요소의 보이기 걸러내기가 봄: 다시 들어와도 띄우지 않음) · 다시 보냄 (지금 보는 사람이면) */
        private void eyeShow(eu.pb4.polymer.virtualentity.api.elements.GenericEntityElement e, java.util.Set<Integer> off, ServerPlayer p, boolean show) {
            boolean watching = getWatchingPlayers().contains(p.connection);
            if (!show) {
                if (!off.add(p.getId())) return;
                if (watching) p.connection.send(new net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket(e.getEntityIds()));
                return;
            }
            if (!off.remove(p.getId()) || !watching) return;
            List<net.minecraft.network.protocol.Packet<? super net.minecraft.network.protocol.game.ClientGamePacketListener>> ps = new ArrayList<>();
            e.startWatching(p, ps::add);
            if (!ps.isEmpty()) p.connection.send(new net.minecraft.network.protocol.game.ClientboundBundlePacket(ps));
        }

        /**
         * 2026-11-17 만든 틱 안의 거름 (그림 · 소품의 크기 · 자리 · 방향을 정할 때마다): 붙자마자 보낸 그림이 다음 틱의 eyeCull 전에 한 틱 화면을 덮지 않게 —
         * 같은 틱에 거두면 띄움 · 거둠 패킷이 함께 가서 그려지지 않음
         */
        void eyeCheck() {
            if (age == 0 && !ended) eyeCull();
        }

        /**
         * 2026-11-28 그림 하나의 거름 (나이 0 이 아니어도 — 시간표 안에서 만든 그림): 만든 틱에 1인칭 눈앞을 덮으면 바로 거둠 (띄움 · 거둠 패킷이 같은 틱에 가서 그려지지
         * 않음). 예전에는 나이 0 만 같은 틱에 걸러 시간표 안에서 몸 가까이 만든 큰 그림이 다음 틱 eyeCull 전 한 틱 화면을 덮었음 (새 버서커 광폭화 터짐 — 1인칭
         * 영상 한 장이 붉게, CI 46090588538). 거두기만 함 (다시 보이기는 틱마다의 eyeCull)
         */
        void eyeCheck(Sprite s) {
            if (ended) return;
            if (age == 0) {
                eyeCull();
                return;
            }
            if (viewers instanceof FirstPerson || s.removed) return;
            for (ServerPlayer p : level.players()) {
                Vec3 eye = p.getEyePosition();
                if (eye.distanceToSqr(origin) >= 48 * 48 || PBridge.thirdPersonOf(p) || s.eyeOff.contains(p.getId())) continue;
                if (s.covers(eye, 1.5)) eyeShow(s.e, s.eyeOff, p, false);
            }
        }

        @Override
        protected void onTick() {
            if (ended) return;
            if (age >= life) {
                end();
                return;
            }
            if (!props.isEmpty() || !sprites.isEmpty()) eyeCull();
            int t = age;
            for (Iterator<Job> it = new ArrayList<>(jobs).iterator(); it.hasNext(); ) {
                Job j = it.next();
                if (t < j.from || t > j.to || (t - j.from) % j.step != 0) continue;
                try {
                    j.fn.accept(t);
                } catch (RuntimeException e) {
                    Mmorpg.LOG.warn("[vfx] job", e);
                    jobs.remove(j);
                }
            }
            jobs.removeIf(j -> j.to < t);
            for (Sprite s : sprites) s.tick(t);
            age++;
        }
    }

    // ------------------------------------------------------------ 입체 소품

    /** 아이템 모델 하나 (아이템 표시). 자리 · 회전 · 크기는 보간 (ticks) */
    public static final class Prop {
        final Fx fx;
        final ItemDisplayElement e;
        Vec3 pos;
        Quaternionf rot = new Quaternionf();
        float scale = 1F;
        /** 2026-11-15 축마다 다른 크기 (null 이면 scale 하나 — BlockFx 의 단색 상자 · 빛 줄 · 판) */
        Vector3f scale3;
        /** 쥔 곳 (모델 픽셀 0 ~ 16 자리) — 이 점이 pos 에 오고 이 점 둘레로 돎. 기본 = 모델 가운데 (8, 8, 8) */
        Vector3f grip = new Vector3f(8, 8, 8);
        boolean removed;

        /** 2026-11-17 아이템 정의 id (1인칭 눈 거름의 모델 상자 — modelBoxes) */
        final String model;
        /** 2026-11-17 1인칭 눈 거름으로 이 소품을 거둔 사람 (id — Fx.eyeCull) */
        final java.util.Set<Integer> eyeOff = new java.util.HashSet<>();

        Prop(Fx fx, String itemModel, Vec3 at) {
            this.fx = fx;
            this.model = itemModel;
            ItemStack stack = new ItemStack(Items.PAPER);
            stack.set(DataComponents.ITEM_MODEL, Identifier.parse(itemModel));
            this.e = new ItemDisplayElement(stack);
            e.setVisibilityPredicate(pl -> !eyeOff.contains(pl.getId()));
            e.setItemDisplayContext(ItemDisplayContext.NONE);
            e.setViewRange(1.5F);
            e.setShadowRadius(0F);
            this.pos = at;
            e.setOffset(at.subtract(fx.origin));
            apply();
        }

        private boolean shown;

        /** 홀더에 넣음 (보는 사람에게 띄움 — 지금의 모습으로) */
        void show() {
            if (shown) return;
            shown = true;
            fx.addElement(e);
        }

        /**
         * 2026-11-12 물들임 (아이템 정의 tints — custom_model_data 색: 0 = 빛, 1 = 속빛 · tools/vfx_props.py 의 tintindex 재질) — 계열 색 (룬 전용 검 · 망치 ·
         * 구름 · 손 · 주먹 · 그물 · 바위 가시의 빛)
         */
        public Prop tint(double[]... colors) {
            List<Integer> c = new ArrayList<>();
            for (double[] col : colors) c.add(rgb(col));
            ItemStack stack = e.getItem().copy();
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(List.of(), List.of(), List.of(), c));
            e.setItem(stack);
            return this;
        }

        /** 쥔 곳 (모델 픽셀) */
        public Prop grip(double x, double y, double z) {
            grip = new Vector3f((float) x, (float) y, (float) z);
            apply();
            return this;
        }

        /** 늘 밝게 (밤 · 그늘에서도 — 빛나는 날) */
        public Prop bright() {
            e.setBrightness(Brightness.FULL_BRIGHT);
            return this;
        }

        public Prop light(int block, int sky) {
            e.setBrightness(new Brightness(block, sky));
            return this;
        }

        /** 테두리 빛 (발광 — 벽 너머로도 보이는 외곽선, 색) */
        public Prop outline(int rgb) {
            e.setGlowColorOverride(rgb);
            e.setGlowing(true);
            return this;
        }

        public Vec3 pos() {
            return pos;
        }

        public Quaternionf rot() {
            return new Quaternionf(rot);
        }

        /** 다음 모습: ticks 틱 동안 at · r · s 로 (0 이면 바로) */
        public Prop to(Vec3 at, Quaternionf r, double s, int ticks) {
            if (removed) return this;
            if (at != null && ticks > MAX_TELEPORT) {
                // 2026-11-12 자리 보간은 59틱까지 (표시 엔티티 teleport_duration) — 긴 옮김은 곧은 길을 40틱씩 나눔 (회전 · 크기는 한 번에)
                Vec3 from = pos;
                int n = (int) Math.ceil(ticks / 40.0);
                for (int i = 1; i < n; i++) {
                    Vec3 mid = from.add(at.subtract(from).scale(i / (double) n));
                    int step = i * ticks / n - (i - 1) * ticks / n;
                    fx.on(fx.age + (i - 1) * ticks / n, () -> move(mid, step));
                }
                int last = ticks - (n - 1) * ticks / n;
                fx.on(fx.age + (n - 1) * ticks / n, () -> move(at, last));
                if (r != null || s > 0) {
                    if (r != null) rot = new Quaternionf(r);
                    if (s > 0) scale = (float) s;
                    e.setInterpolationDuration(ticks);
                    apply();
                    e.startInterpolationIfDirty();
                }
                return this;
            }
            if (at != null) {
                pos = at;
                e.setTeleportDuration(Math.max(0, ticks));
                e.setOffset(at.subtract(fx.origin));
            }
            if (r != null) rot = new Quaternionf(r);
            if (s > 0) scale = (float) s;
            e.setInterpolationDuration(Math.max(0, ticks));
            apply();
            e.startInterpolationIfDirty();
            fx.eyeCheck();
            return this;
        }

        public Prop move(Vec3 at, int ticks) {
            return to(at, null, -1, ticks);
        }

        public Prop turn(Quaternionf r, int ticks) {
            return to(null, r, -1, ticks);
        }

        public Prop size(double s, int ticks) {
            scale3 = null;
            return to(null, null, s, ticks);
        }

        /** 2026-11-15 축마다 다른 크기 (모델 x · y · z — 칸 = 모델 16 픽셀 × 크기) 로 ticks 틱 동안 (BlockFx — 단색 상자를 늘여 빛 줄 · 판 · 조각) */
        public Prop stretch(double sx, double sy, double sz, int ticks) {
            if (removed) return this;
            scale3 = new Vector3f((float) sx, (float) sy, (float) sz);
            e.setInterpolationDuration(Math.max(0, ticks));
            apply();
            e.startInterpolationIfDirty();
            return this;
        }

        private void apply() {
            // 모델 가운데 (8, 8, 8) 가 엔티티 자리 → 쥔 곳을 엔티티 자리로: 안쪽 옮김 c = −(grip − 8) / 16 (아이템 표시의 Y 180° 를 되돌린 축)
            Vector3f sc = scale3 != null ? new Vector3f(scale3) : new Vector3f(scale, scale, scale);
            Vector3f c = new Vector3f(-(grip.x - 8F) / 16F * sc.x, -(grip.y - 8F) / 16F * sc.y, -(grip.z - 8F) / 16F * sc.z);
            Vector3f tr = rot.transform(c, new Vector3f());
            e.setTranslation(tr);
            e.setLeftRotation(rot);
            e.setScale(sc);
            e.setRightRotation(ITEM_Y_FLIP ? new Quaternionf().rotateY((float) Math.PI) : new Quaternionf());
        }

        public void remove() {
            if (removed) return;
            removed = true;
            fx.removeElement(e);
        }

        /**
         * 2026-11-17 세상의 점 p 가 이 소품의 모델 조각 상자 (여유 margin 칸) 안인지: 모델 픽셀 m = 쥔 곳 + 16 × rot⁻¹ (p − pos) / 크기 (apply 의 거꿀셈 — 서버가 아는 끝 모습,
         * 보간 중이면 여유가 메움)
         */
        boolean contains(Vec3 p, double margin) {
            if (removed) return false;
            float[][] boxes = modelBoxes(model);
            if (boxes.length == 0) return false;
            Vector3f sc = scale3 != null ? scale3 : new Vector3f(scale, scale, scale);
            if (Math.abs(sc.x) < 1e-4F || Math.abs(sc.y) < 1e-4F || Math.abs(sc.z) < 1e-4F) return false;
            Vector3f d = new Vector3f((float) (p.x - pos.x), (float) (p.y - pos.y), (float) (p.z - pos.z));
            new Quaternionf(rot).normalize().conjugate().transform(d);
            float mx = grip.x + 16F * d.x / sc.x;
            float my = grip.y + 16F * d.y / sc.y;
            float mz = grip.z + 16F * d.z / sc.z;
            float gx = (float) (16 * margin / Math.abs(sc.x));
            float gy = (float) (16 * margin / Math.abs(sc.y));
            float gz = (float) (16 * margin / Math.abs(sc.z));
            Vector3f m = new Vector3f(mx, my, mz);
            float gm = Math.max(gx, Math.max(gy, gz));
            for (float[] b : boxes) {
                Vector3f q = toElement(b, m, true);
                float hx = b[6] < 0 ? gx : gm;
                float hy = b[6] < 0 ? gy : gm;
                float hz = b[6] < 0 ? gz : gm;
                if (q.x >= b[0] - hx && q.x <= b[3] + hx && q.y >= b[1] - hy && q.y <= b[4] + hy && q.z >= b[2] - hz && q.z <= b[5] + hz) return true;
            }
            return false;
        }

        /**
         * 2026-11-17 1인칭 화면을 이 소품이 가까이서 덮는 몫: 눈 eye 에서 보는 쪽 f 둘레 (가로 ±40° · 세로 ±25°) 5 × 5 빛줄 중 EYE_NEAR 칸 안에서 모델 조각 상자에 닿는 몫
         * (힘 영웅 망치 머리가 눈앞 1칸에서 화면 2/3 를 잿빛 면으로 덮었음 — 눈이 속에 들지 않아 contains 로는 못 거름, CI 44007116244). 거둠 0.7 · 다시 보냄 0.55 —
         * 검 고귀 반격 자세의 가로 검 (가슴 앞 0.85칸 · 화면 아래 절반) 은 0.48 이라 그대로
         */
        double coverage(Vec3 eye, Vec3 f) {
            if (removed) return 0;
            float[][] boxes = modelBoxes(model);
            if (boxes.length == 0) return 0;
            Vector3f sc = scale3 != null ? scale3 : new Vector3f(scale, scale, scale);
            if (Math.abs(sc.x) < 1e-4F || Math.abs(sc.y) < 1e-4F || Math.abs(sc.z) < 1e-4F) return 0;
            float big = Math.max(Math.abs(sc.x), Math.max(Math.abs(sc.y), Math.abs(sc.z)));
            if (eye.distanceTo(pos) > EYE_NEAR + 2.5 * big) return 0; // 모델은 크기 1 에 3칸 안 (쥔 곳에서)
            Quaternionf inv = new Quaternionf(rot).normalize().conjugate();
            Vector3f o = inv.transform(new Vector3f((float) (eye.x - pos.x), (float) (eye.y - pos.y), (float) (eye.z - pos.z)));
            Vector3f m0 = new Vector3f(grip.x + 16F * o.x / sc.x, grip.y + 16F * o.y / sc.y, grip.z + 16F * o.z / sc.z);
            Vec3 fw = f.normalize();
            Vec3 r = fw.cross(new Vec3(0, 1, 0));
            r = r.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : r.normalize();
            Vec3 u = r.cross(fw);
            int hit = 0;
            int n = 0;
            for (double ay : COVER_V) {
                for (double ax : COVER_H) {
                    Vec3 d = fw.add(r.scale(Math.tan(Math.toRadians(ax)))).add(u.scale(Math.tan(Math.toRadians(ay)))).normalize();
                    Vector3f dm = inv.transform(new Vector3f((float) d.x, (float) d.y, (float) d.z));
                    dm.set(16F * dm.x / sc.x, 16F * dm.y / sc.y, 16F * dm.z / sc.z);
                    if (rayHits(m0, dm, boxes, EYE_NEAR)) hit++;
                    n++;
                }
            }
            return hit / (double) n;
        }
    }

    /** 2026-11-17 소품이 화면을 덮는지 볼 거리 (칸 — 이보다 먼 면은 덮어도 그대로: 하늘에서 내려오는 거대한 검 같은 큰 그림은 멀리서 화면을 채우는 것이 연출) */
    static final double EYE_NEAR = 2.5;
    static final double[] COVER_H = {-40, -20, 0, 20, 40};
    static final double[] COVER_V = {-25, -12.5, 0, 12.5, 25};

    /** 모델 픽셀 공간의 빛줄 (o + t d, t = 0 ~ tmax 칸) 이 조각 상자 하나에라도 닿는지 (slab) */
    static boolean rayHits(Vector3f o0, Vector3f d0, float[][] boxes, double tmax) {
        for (float[] b : boxes) {
            Vector3f o = toElement(b, o0, true);
            Vector3f d = toElement(b, d0, false);
            double t0 = 0;
            double t1 = tmax;
            boolean ok = true;
            for (int i = 0; i < 3 && ok; i++) {
                double oi = o.get(i);
                double di = d.get(i);
                if (Math.abs(di) < 1e-9) {
                    if (oi < b[i] || oi > b[i + 3]) ok = false;
                } else {
                    double ta = (b[i] - oi) / di;
                    double tb = (b[i + 3] - oi) / di;
                    if (ta > tb) {
                        double tmp = ta;
                        ta = tb;
                        tb = tmp;
                    }
                    t0 = Math.max(t0, ta);
                    t1 = Math.min(t1, tb);
                    if (t0 > t1) ok = false;
                }
            }
            if (ok) return true;
        }
        return false;
    }

    /** 2026-11-17 1인칭 눈 거름의 여유 (칸 — 모델 겉에서 이만큼 안이면 속으로 셈: 눈앞 0.05칸의 가까운 면도 화면을 덮음) */
    static final double EYE_MARGIN = 0.25;

    /** 2026-11-17 소품 모델의 조각 상자 (아이템 정의 id → {x0, y0, z0, x1, y1, z1} 모델 픽셀 — 돌린 조각은 돌린 꼭짓점을 감싸는 상자. 못 읽으면 빈 목록) */
    private static final Map<String, float[][]> MODEL_BOXES = new java.util.concurrent.ConcurrentHashMap<>();

    static float[][] modelBoxes(String itemModel) {
        return MODEL_BOXES.computeIfAbsent(itemModel, Vfx::loadBoxes);
    }

    private static float[][] loadBoxes(String itemModel) {
        try {
            Identifier id = Identifier.parse(itemModel);
            Map<String, Object> def = readJson("/assets/" + id.getNamespace() + "/items/" + id.getPath() + ".json");
            String model = def == null ? null : firstModel(def.get("model"));
            List<float[]> out = new ArrayList<>();
            Identifier mid = model == null ? null : Identifier.parse(model);
            for (int depth = 0; depth < 4 && mid != null; depth++) {
                Map<String, Object> m = readJson("/assets/" + mid.getNamespace() + "/models/" + mid.getPath() + ".json");
                if (m == null) break;
                if (m.get("elements") instanceof List<?> els) {
                    for (Object o : els) {
                        if (o instanceof Map<?, ?> e) {
                            float[] b = elementBox(e);
                            if (b != null) out.add(b);
                        }
                    }
                    break;
                }
                mid = m.get("parent") instanceof String par ? Identifier.parse(par) : null;
            }
            return out.toArray(new float[0][]);
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[vfx] model boxes " + itemModel + ": " + e);
            return new float[0][];
        }
    }

    private static Map<String, Object> readJson(String path) {
        try (InputStream in = Vfx.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    /** 아이템 정의의 첫 모델 id (select · composite 안쪽까지) */
    private static String firstModel(Object o) {
        if (o instanceof Map<?, ?> m) {
            if (m.get("model") instanceof String s) return s;
            for (Object v : m.values()) {
                String s = firstModel(v);
                if (s != null) return s;
            }
        } else if (o instanceof List<?> l) {
            for (Object v : l) {
                String s = firstModel(v);
                if (s != null) return s;
            }
        }
        return null;
    }

    /**
     * 조각 하나 → {x0, y0, z0, x1, y1, z1, 축 (0 · 1 · 2, 안 돌렸으면 −1), 각 (라디안), origin x · y · z} — 돌린 조각은 그 조각의 틀 (origin 둘레로 −각 만큼 되돌림)
     * 에서 상자로 봄 (2026-11-17 1판은 돌린 꼭짓점을 감싸는 큰 상자라 비스듬한 칼날 조각이 넓게 부풀어 검 고귀 반격 자세의 가로 검이 1인칭 내내 거둬졌음 — CI 44008618300)
     */
    private static float[] elementBox(Map<?, ?> e) {
        if (!(e.get("from") instanceof List<?> f) || !(e.get("to") instanceof List<?> t) || f.size() < 3 || t.size() < 3) return null;
        float[] a = new float[3];
        float[] b = new float[3];
        for (int i = 0; i < 3; i++) {
            a[i] = ((Number) f.get(i)).floatValue();
            b[i] = ((Number) t.get(i)).floatValue();
        }
        float[] box = {Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2]), Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2]), -1, 0,
                8, 8, 8};
        if (!(e.get("rotation") instanceof Map<?, ?> r) || !(r.get("angle") instanceof Number ang) || Math.abs(ang.doubleValue()) < 1e-3) return box;
        box[6] = "x".equals(r.get("axis")) ? 0 : "y".equals(r.get("axis")) ? 1 : 2;
        box[7] = (float) Math.toRadians(ang.doubleValue());
        if (r.get("origin") instanceof List<?> ol && ol.size() >= 3) {
            for (int i = 0; i < 3; i++) box[8 + i] = ((Number) ol.get(i)).floatValue();
        }
        return box;
    }

    /** 모델 픽셀의 점 · 방향 v 를 돌린 조각 b 의 틀로 (그 축 둘레로 −각 — 오른손 돌림, 마크 모델 조각 돌림과 같은 방향). point 면 origin 둘레로 */
    static Vector3f toElement(float[] b, Vector3f v, boolean point) {
        if (b[6] < 0) return v;
        int axis = (int) b[6];
        int i = (axis + 1) % 3;
        int j = (axis + 2) % 3;
        double c = Math.cos(-b[7]);
        double sn = Math.sin(-b[7]);
        double vi = v.get(i) - (point ? b[8 + i] : 0);
        double vj = v.get(j) - (point ? b[8 + j] : 0);
        Vector3f out = new Vector3f(v);
        out.setComponent(i, (float) ((point ? b[8 + i] : 0) + vi * c - vj * sn));
        out.setComponent(j, (float) ((point ? b[8 + j] : 0) + vi * sn + vj * c));
        return out;
    }

    // ------------------------------------------------------------ 조각 물체

    /** 조각 하나: 모델 (rpg:vfx/shard_<m>) · 가운데 c · 회전 q · 크기 s (모델 픽셀 — tip 이면 지름 · 끝에서 끝 · 지름) · 꼭짓점으로 세움 */
    record ShapePart(String model, Vector3f c, Quaternionf q, Vector3f s, boolean tip) {}

    record ShapeDef(String name, Vector3f grip, List<ShapePart> parts) {}

    static final Map<String, ShapeDef> SHAPES = new HashMap<>();
    /** 정육면체를 꼭짓점으로 세움 (대각선 (1, 1, 1) → +Y — 만년빙하 결정과 같음) */
    static final Quaternionf TIP_UP = new Quaternionf().rotationTo(new Vector3f(1, 1, 1).normalize(), new Vector3f(0, 1, 0));
    static final float DIAG = (float) Math.sqrt(3);
    static final float WIDE = (float) Math.sqrt(2.0 / 3.0);

    @SuppressWarnings("unchecked")
    static synchronized ShapeDef shape(String name) {
        if (SHAPES.isEmpty()) {
            try (InputStream in = Vfx.class.getResourceAsStream("/mmorpg/vfx_shapes.json")) {
                if (in == null) throw new IllegalStateException("no vfx_shapes.json");
                Map<String, Object> root = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                for (Map.Entry<String, Object> e : root.entrySet()) {
                    Map<String, Object> m = (Map<String, Object>) e.getValue();
                    List<Object> g = (List<Object>) m.get("grip");
                    List<ShapePart> parts = new ArrayList<>();
                    for (Object o : (List<Object>) m.get("parts")) {
                        Map<String, Object> pm = (Map<String, Object>) o;
                        List<Object> c = (List<Object>) pm.get("c");
                        List<Object> q = (List<Object>) pm.get("q");
                        List<Object> sz = (List<Object>) pm.get("s");
                        parts.add(new ShapePart("rpg:vfx/shard_" + pm.get("m"), vec(c), new Quaternionf(num(q, 0), num(q, 1), num(q, 2), num(q, 3)), vec(sz),
                                Boolean.TRUE.equals(pm.get("tip"))));
                    }
                    SHAPES.put(e.getKey(), new ShapeDef(e.getKey(), vec(g), parts));
                }
                Mmorpg.LOG.info("[vfx] shapes {}", SHAPES.size());
            } catch (IOException | RuntimeException ex) {
                Mmorpg.LOG.warn("[vfx] shapes: {}", ex.toString());
            }
        }
        ShapeDef d = SHAPES.get(name);
        if (d == null) throw new IllegalArgumentException("vfx shape " + name);
        return d;
    }

    private static float num(List<Object> l, int i) {
        return ((Number) l.get(i)).floatValue();
    }

    private static Vector3f vec(List<Object> l) {
        return new Vector3f(num(l, 0), num(l, 1), num(l, 2));
    }

    /**
     * 2026-11-12 조각 물체 (사용자 "만년빙하처럼 파도 같은거 만들어도 됨" — tools/vfx_shapes.py): 조각마다 아이템 표시 하나 (반투명 정육면체 모델 rpg:vfx/shard_*)
     * 를 비스듬히 · 아무 비율로 (변환 = 옮김 · 왼쪽 회전 · 크기 · 오른쪽 회전 — 자바 모델의 22.5° 계단 없이) 겹침. 물체 전체는 {@link Prop} 처럼 옮김 · 돌림 · 키움
     * (쥔 곳 · 크기 단위 = 모델 픽셀, 16 = 1칸). 조각의 모델 점 u (정육면체 −½ ~ ½) → 세계: 자리 + R · 크기 · ((c − 쥔 곳) / 16 + q · s · (tip ? 꼭짓점 세움 : 1) · u)
     */
    public static final class Group {
        final Fx fx;
        final ShapeDef def;
        final List<ItemDisplayElement> els = new ArrayList<>();
        Vec3 pos;
        Quaternionf rot = new Quaternionf();
        float scale = 1F;
        boolean removed;
        boolean shown;

        Group(Fx fx, ShapeDef def, Vec3 at) {
            this.fx = fx;
            this.def = def;
            this.pos = at;
            for (ShapePart p : def.parts()) {
                ItemStack stack = new ItemStack(Items.PAPER);
                stack.set(DataComponents.ITEM_MODEL, Identifier.parse(p.model()));
                ItemDisplayElement e = new ItemDisplayElement(stack);
                e.setItemDisplayContext(ItemDisplayContext.NONE);
                e.setViewRange(1.5F);
                e.setShadowRadius(0F);
                e.setOffset(at.subtract(fx.origin));
                els.add(e);
            }
            apply();
        }

        /** 띄움 (한 번만 — Fx.group 이 init 뒤에 부름) */
        void show() {
            if (shown || removed) return;
            shown = true;
            for (ItemDisplayElement e : els) fx.addElement(e);
        }

        /** 물들임 (기운 조각 — 0 = 빛, 1 = 속빛) */
        public Group tint(double[]... colors) {
            List<Integer> c = new ArrayList<>();
            for (double[] col : colors) c.add(rgb(col));
            for (ItemDisplayElement e : els) {
                ItemStack stack = e.getItem().copy();
                stack.set(DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(List.of(), List.of(), List.of(), c));
                e.setItem(stack);
            }
            return this;
        }

        public Group bright() {
            for (ItemDisplayElement e : els) e.setBrightness(Brightness.FULL_BRIGHT);
            return this;
        }

        public Vec3 pos() {
            return pos;
        }

        public Quaternionf rot() {
            return new Quaternionf(rot);
        }

        /** 다음 모습: ticks 틱 동안 at · r · s 로 (자리 보간은 59틱까지 — 긴 옮김은 곧은 길을 40틱씩 나눔, {@link Prop#to} 와 같음) */
        public Group to(Vec3 at, Quaternionf r, double s, int ticks) {
            if (removed) return this;
            int t = Math.max(0, ticks);
            if (at != null && t > MAX_TELEPORT) {
                Vec3 from = pos;
                int n = (int) Math.ceil(t / 40.0);
                for (int i = 1; i <= n; i++) {
                    Vec3 mid = from.add(at.subtract(from).scale(i / (double) n));
                    int step = i * t / n - (i - 1) * t / n;
                    fx.on(fx.age + (i - 1) * t / n, () -> move(mid, step));
                }
                if (r != null || s > 0) to(null, r, s, t);
                return this;
            }
            if (at != null) {
                pos = at;
                for (ItemDisplayElement e : els) {
                    e.setTeleportDuration(Math.min(MAX_TELEPORT, t));
                    e.setOffset(at.subtract(fx.origin));
                }
            }
            if (r != null) rot = new Quaternionf(r);
            if (s > 0) scale = (float) s;
            for (ItemDisplayElement e : els) e.setInterpolationDuration(t);
            apply();
            for (ItemDisplayElement e : els) e.startInterpolationIfDirty();
            return this;
        }

        public Group move(Vec3 at, int ticks) {
            return to(at, null, -1, ticks);
        }

        public Group turn(Quaternionf r, int ticks) {
            return to(null, r, -1, ticks);
        }

        public Group size(double s, int ticks) {
            return to(null, null, s, ticks);
        }

        private void apply() {
            Quaternionf flip = ITEM_Y_FLIP ? new Quaternionf().rotateY((float) Math.PI) : new Quaternionf();
            for (int i = 0; i < els.size(); i++) {
                ShapePart p = def.parts().get(i);
                ItemDisplayElement e = els.get(i);
                Vector3f o = new Vector3f(p.c()).sub(def.grip()).div(16F).mul(scale);
                e.setTranslation(rot.transform(o, new Vector3f()));
                e.setLeftRotation(new Quaternionf(rot).mul(p.q()));
                Vector3f sz = p.tip() ? new Vector3f(p.s().x / 2F / WIDE, p.s().y / DIAG, p.s().z / 2F / WIDE) : new Vector3f(p.s());
                e.setScale(sz.div(16F).mul(scale));
                e.setRightRotation(p.tip() ? new Quaternionf(TIP_UP).mul(flip) : new Quaternionf(flip));
            }
        }

        public void remove() {
            if (removed) return;
            removed = true;
            if (shown) for (ItemDisplayElement e : els) fx.removeElement(e);
        }
    }

    // ------------------------------------------------------------ 2D 그림

    /** 글자 하나로 그린 그림 (글자 표시). 크기 = 그림 높이 (칸) */
    public static final class Sprite {
        final Fx fx;
        final TextDisplayElement e;
        final Sheet sheet;
        Vec3 pos;
        int rgb = 0xFFFFFF;
        double alpha = 1;
        int frame;
        double size = 1;
        double roll;
        Quaternionf facing;
        /** 2026-11-15 조각 자리 (그림 면 안의 x · y — 칸): 큰 그림을 2 × 2 조각 글자로 나눠 붙일 때 (돌림 · 고정 방향을 따라 함께 돎) */
        Vector3f offset = new Vector3f();
        boolean removed;
        boolean hidden;
        // 칸 넘기기 (play) · 옅어짐 (fade)
        int playFrom = -1;
        int playFirst;
        int playCount;
        int playEvery = 1;
        boolean playLoop;
        int fadeFrom = -1;
        int fadeTicks;
        double fadeStart = 1;
        /** 2026-11-15 좌우 거울 (크기 x 를 뒤집음 — 꼭짓점이 거울 자리로 가 삼각형 감김이 뒤집혀 뒷면에서 보임) */
        boolean mirrored;
        /** 2026-11-28 그림 면 안의 납작함 (돌림 전 가로 sx · 세로 sy 배 — 조각 자리도 같이): 1인칭 세로 베기의 반달을 일자에 가깝게 (PhantomFx.waveStraight) */
        double sx = 1;
        double sy = 1;
        /** 2026-11-17 1인칭 눈 거름으로 이 그림을 거둔 사람 (id — Fx.eyeCull) */
        final java.util.Set<Integer> eyeOff = new java.util.HashSet<>();

        Sprite(Fx fx, Sheet sheet, Vec3 at) {
            this.fx = fx;
            this.sheet = sheet;
            this.pos = at;
            this.e = new TextDisplayElement();
            e.setVisibilityPredicate(pl -> !eyeOff.contains(pl.getId()));
            e.setBillboardMode(Display.BillboardConstraints.CENTER);
            e.setBackground(0);
            e.setDefaultBackground(false);
            e.setShadow(false);
            e.setSeeThrough(false);
            e.setLineWidth(4096);
            e.setBrightness(Brightness.FULL_BRIGHT);
            e.setViewRange(1.5F);
            e.setOffset(at.subtract(fx.origin));
            text();
            transform();
            fx.addElement(e);
        }

        /** 색 (0 ~ 1 RGB) — 미리 칠한 그림판은 흰색 그대로 */
        public Sprite color(double[] c) {
            return color(rgb(c));
        }

        public Sprite color(int rgb) {
            this.rgb = sheet.colored ? 0xFFFFFF : rgb;
            text();
            return this;
        }

        public Sprite frame(int f) {
            frame = f;
            text();
            return this;
        }

        public Sprite alpha(double a) {
            alpha = a;
            opacity();
            return this;
        }

        /** 땅 · 벽에 붙임 (그림 앞면 방향 q — 고정, 카메라를 보지 않음) */
        public Sprite fixed(Quaternionf q) {
            facing = new Quaternionf(q);
            e.setBillboardMode(Display.BillboardConstraints.FIXED);
            transform();
            fx.eyeCheck(this);
            return this;
        }

        /** 2026-11-15 세로축만 카메라를 봄 (불꽃 혀 · 기둥처럼 늘 서 있는 그림 — 위에서 봐도 눕지 않음) */
        public Sprite vertical() {
            facing = null;
            e.setBillboardMode(Display.BillboardConstraints.VERTICAL);
            transform();
            fx.eyeCheck(this);
            return this;
        }

        /** 2026-11-16 지금 자리 · 크기 (칸) · 지웠는지 — 바닐라 파티클 겹이 그림을 따라감 (PhantomFx.charge) */
        public Vec3 pos() {
            return pos;
        }

        public double sizeNow() {
            return size;
        }

        public boolean gone() {
            return removed;
        }

        /** 다음 모습 (ticks 틱 보간): 자리 · 크기 (칸 — 0 이하면 그대로) */
        public Sprite to(Vec3 at, double sizeBlocks, int ticks) {
            if (removed) return this;
            if (at != null) {
                pos = at;
                e.setTeleportDuration(Math.max(0, ticks));
                e.setOffset(at.subtract(fx.origin));
            }
            if (sizeBlocks > 0) {
                size = sizeBlocks;
                SIZE_MAX.merge(sheet.name(), sizeBlocks, Math::max);
            }
            e.setInterpolationDuration(Math.max(0, ticks));
            transform();
            e.startInterpolationIfDirty();
            fx.eyeCheck(this);
            return this;
        }

        public Sprite size(double blocks) {
            size = blocks;
            SIZE_MAX.merge(sheet.name(), blocks, Math::max);
            transform();
            fx.eyeCheck(this);
            return this;
        }

        /** 카메라를 보는 그림의 기울임 (화면에서 도는 각 — 도) · 고정 그림이면 그 면 안에서 돎 */
        public Sprite roll(double deg, int ticks) {
            roll = deg;
            e.setInterpolationDuration(Math.max(0, ticks));
            transform();
            e.startInterpolationIfDirty();
            return this;
        }

        /** 고정 그림의 방향을 ticks 틱 동안 q 로 */
        public Sprite face(Quaternionf q, int ticks) {
            facing = new Quaternionf(q);
            e.setInterpolationDuration(Math.max(0, ticks));
            transform();
            e.startInterpolationIfDirty();
            fx.eyeCheck(this);
            return this;
        }

        /** 나이 from 부터 every 틱마다 칸 first ~ first + count − 1 (loop 면 되풀이) */
        public Sprite play(int from, int first, int count, int every, boolean loop) {
            playFrom = from;
            playFirst = first;
            playCount = Math.max(1, count);
            playEvery = Math.max(1, every);
            playLoop = loop;
            if (from <= fx.age) tick(fx.age);
            fx.keep(from + (loop ? 1 : playCount * playEvery));
            return this;
        }

        /** 나이 from 부터 ticks 틱 동안 옅어져 사라짐 */
        public Sprite fade(int from, int ticks) {
            fadeFrom = from;
            fadeTicks = Math.max(1, ticks);
            fadeStart = alpha;
            fx.keep(from + fadeTicks + 1);
            return this;
        }

        /**
         * 2026-11-15 좌우 거울 (고정 그림의 뒷면): 크기 x 를 뒤집어 글자 판의 꼭짓점이 거울 자리로 감 → 삼각형 감김이 뒤집혀 반대쪽에서 보임 (글자는 한쪽 면만 그려짐).
         * 그림은 세계에서 좌우가 바뀜 — 같은 세계 그림이 필요하면 미리 뒤집은 그림판을 씀 (PhantomFx.tiled 두 면). 처음 판 (크기 z 를 뒤집음) 은 판의 두께가 0 이라
         * 꼭짓점이 그대로여서 뒷면이 생기지 않았음 (CI 43681025801 — 아스널 게이트 등 뒤 문 · 블레이드 스톰 베기 절반이 안 보였음)
         */
        public Sprite mirror() {
            mirrored = true;
            transform();
            return this;
        }

        /**
         * 2026-11-17 가려지지 않는 그림 (글자 표시의 see_through — 깊이 시험 없이 블록 · 몸 위에 그려짐): 1인칭 눈앞을 지나가는 큰 그림이 과녁 몸 · 땅 모서리에
         * 가려 반만 보이던 것 (오록스 차지 1인칭 들소 — CI 44054369134 · 44057837261)
         */
        public Sprite seeThrough() {
            e.setSeeThrough(true);
            return this;
        }

        /** 2026-11-28 그림 면 안에서 납작하게 (돌림 전 가로 x · 세로 y 배 — 조각 자리 offset 도 같은 배로: 조각 그림 전체가 한 장처럼 눌림) */
        public Sprite squash(double x, double y) {
            sx = x;
            sy = y;
            transform();
            fx.eyeCheck(this);
            return this;
        }

        /** 2026-11-15 그림 면 안의 자리 옮김 (칸 — 조각 그림: 오른쪽 x · 위 y) */
        public Sprite offset(double x, double y) {
            offset = new Vector3f((float) x, (float) y, 0F);
            transform();
            return this;
        }

        /** 나이 at 에 지움 */
        public Sprite until(int at) {
            fx.on(at, this::remove);
            return this;
        }

        void tick(int t) {
            if (removed) return;
            if (playFrom >= 0 && t >= playFrom) {
                int k = (t - playFrom) / playEvery;
                int f = playLoop ? playFirst + k % playCount : playFirst + Math.min(playCount - 1, k);
                if (f != frame) {
                    frame = f;
                    text();
                }
                if (!playLoop && k >= playCount + 1 && fadeFrom < 0) {
                    // 마지막 칸 다음 틱에 지움 (옅어짐이 없으면)
                    remove();
                    return;
                }
            }
            if (fadeFrom >= 0 && t >= fadeFrom) {
                double k = (t - fadeFrom) / (double) fadeTicks;
                if (k >= 1) {
                    remove();
                    return;
                }
                alpha = fadeStart * (1 - k);
                opacity();
            }
        }

        private void text() {
            if (removed) return;
            String g = sheet.glyph(frame);
            e.setText(Component.literal(g).withStyle(Style.EMPTY.withFont(FONT).withColor(rgb)));
        }

        private void opacity() {
            int o = (int) Math.round(Math.max(0, Math.min(1, alpha)) * 255);
            boolean hide = o < MIN_OPACITY;
            if (hide != hidden) {
                hidden = hide;
                transform();
            }
            e.setTextOpacity((byte) (hide ? MIN_OPACITY : Math.min(255, o)));
        }

        private void transform() {
            float s = hidden ? 0F : (float) (size / (glyphHeight * TEXT_UNIT));
            Quaternionf q = facing != null ? new Quaternionf(facing) : new Quaternionf();
            if (roll != 0) q.rotateZ((float) Math.toRadians(roll));
            // 자른 칸 자리 (그림판 cx · cy — 크기 몫): 거울이면 글자가 엔티티 자리를 축으로 뒤집히므로 가로를 반대로
            Vector3f at = new Vector3f(offset).add((float) ((mirrored ? -sheet.cx() : sheet.cx()) * size), (float) (sheet.cy() * size), 0F);
            // 납작함은 돌림 전 그림 면 안에서 (크기 · 조각 자리 모두 — 변환 = 옮김 · 돌림 · 크기 차례라 크기가 먼저 걸림)
            at.mul((float) sx, (float) sy, 1F);
            e.setTranslation(hidden ? new Vector3f() : q.transform(at));
            e.setLeftRotation(q);
            e.setScale(new Vector3f((float) ((mirrored ? -s : s) * sx), (float) (s * sy), s));
            e.setRightRotation(new Quaternionf());
        }

        public void remove() {
            if (removed) return;
            removed = true;
            fx.removeElement(e);
        }

        /**
         * 2026-11-17 이 그림이 1인칭 눈앞을 다 덮는지 (눈에서 가운데까지 &lt; k × 반 너비 — k 1.5 = 반 너비 / 거리 0.67 넘게: 화면에서 67° 넘게 — 세로 시야 70° 를 거의 다): 카메라를 보는 그림 · 세운 판만 (땅 · 하늘에
         * 눕힌 판은 아래 · 위를 덮을 뿐 — 발밑 룬 원은 그대로), 반 너비 0.6칸 아래 작은 그림 · 거의 투명한 그림은 뺌
         */
        boolean covers(Vec3 eye, double k) {
            if (removed || hidden || alpha < 0.12) return false;
            double hw = 0.5 * size * sheet.aspect() * sx;
            double hh = 0.5 * size * sy;
            if (facing != null) {
                Quaternionf q = new Quaternionf(facing);
                if (roll != 0) q.rotateZ((float) Math.toRadians(roll));
                Vector3f n = q.transform(new Vector3f(0, 0, 1));
                if (Math.abs(n.y) > 0.8F) return false;
                // 2026-11-17 가는 쪽으로 누운 긴 그림 (가로세로 비 2 넘게 — 불덩이 · 줄기 · 광선) 의 긴 축은 눈에서 본 겉보기 길이로 (긴 축이 눈 → 판 쪽과 이루는 각만큼 줄어듦):
                // 뒤에서 보면 길이가 거의 없음 — 판 길이로 셈하면 날아가는 내내 1인칭 주인에게서 거둬졌음 (마력 일반 불덩이, CI 44012061831). 정사각에 가까운 판은 그대로
                // (가까이 있는 큰 판은 비스듬해도 화면을 채움 — 모든 판에 걸었던 094438e7 에서 도끼 초월의 핏빛 판이 다시 한 장 화면을 덮었음, CI 44013192154)
                double dl = eye.distanceTo(pos);
                if (dl > 1e-6 && sheet.aspect() >= 2) {
                    Vector3f v = new Vector3f((float) ((pos.x - eye.x) / dl), (float) ((pos.y - eye.y) / dl), (float) ((pos.z - eye.z) / dl));
                    float cx = q.transform(new Vector3f(1, 0, 0)).dot(v);
                    hw = Math.max(hh, hw * Math.sqrt(Math.max(0, 1 - cx * cx)));
                }
            }
            double half = Math.max(hw, hh) + new Vector3f(offset).mul((float) sx, (float) sy, 1F).length();
            if (half < 0.6) return false;
            return eye.distanceTo(pos) < k * half;
        }
    }

    /**
     * 2026-11-17 1인칭 눈앞에 일부러 둔 그림의 보는 사람 (주인의 1인칭에만 — 1인칭 대검 · 눈앞 칼빛): 이 걸러내기를 쓴 효과는 눈앞을 덮어도 거두지 않음 (Fx.eyeCull)
     */
    public interface FirstPerson extends Predicate<ServerPlayer> {}
}
