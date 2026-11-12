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
     * 그림판 하나 (vfx_sprites.json — 글꼴 rpg:vfx · 2026-11-12 px_sprites.json — 글꼴 rpg:px, 이름 앞에 "px/"): 축 (px · py — 칸 안 0 ~ 1, 왼쪽 위에서) 이
     * 엔티티 자리에 옴, hb = 칸 높이 (블록 — 1칸 16픽셀 밀도), b = 같이 띄우는 심지 층 (없으면 null)
     */
    public record Sheet(String name, String chars, int frames, double aspect, boolean colored, FontDescription font, double px, double py, double hb, String b,
            String bchars, int adv) {
        public char glyph(int frame) {
            return chars.charAt(Math.floorMod(frame, frames));
        }

        /** 2026-11-12 이 칸의 흰 심지 글자 (같은 글줄에 겹쳐 그림 — 없으면 0) */
        public char bglyph(int frame) {
            if (bchars == null || bchars.isEmpty()) return 0;
            char c = bchars.charAt(Math.floorMod(frame, Math.min(frames, bchars.length())));
            return c == ' ' ? 0 : c;
        }
    }

    /** 뒤로 옮김 글자 (글꼴 rpg:vfx 의 space — 글자 space + b 가 −2^b) */
    static int spaceFirst = 0xF800;

    static final Map<String, Sheet> SHEETS = new HashMap<>();
    static int glyphHeight = 64;
    private static final List<Fx> ACTIVE = new ArrayList<>();
    /** 시험용: 띄운 효과 · 소품 · 그림 수 */
    public static int fxMade;
    public static int propsMade;
    public static int spritesMade;

    public static void init() {
        load();
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
    }

    /** 2026-11-12 픽셀 효과 글꼴 (tools/vfx_pixel.py — vfx.Px) */
    public static final Identifier PX_FONT_ID = Mmorpg.rpg("px");
    static final FontDescription PX_FONT = new FontDescription.Resource(PX_FONT_ID);

    static void load() {
        load("/mmorpg/vfx_sprites.json", "", FONT);
        load("/mmorpg/px_sprites.json", "px/", PX_FONT);
        Mmorpg.LOG.info("[vfx] sprites {}", SHEETS.size());
    }

    @SuppressWarnings("unchecked")
    private static void load(String file, String prefix, FontDescription font) {
        try (InputStream in = Vfx.class.getResourceAsStream(file)) {
            if (in == null) {
                Mmorpg.LOG.warn("[vfx] no sprite index {}", file);
                return;
            }
            Map<String, Object> root = Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            if (root.get("h") instanceof Number n) glyphHeight = n.intValue();
            if (root.get("space") instanceof Number n) spaceFirst = n.intValue();
            Map<String, Object> sprites = (Map<String, Object>) root.get("sprites");
            for (Map.Entry<String, Object> e : sprites.entrySet()) {
                Map<String, Object> m = (Map<String, Object>) e.getValue();
                double px = 0.5;
                double py = 0.5;
                if (m.get("pivot") instanceof List<?> pv && pv.size() == 2) {
                    px = ((Number) pv.get(0)).doubleValue();
                    py = ((Number) pv.get(1)).doubleValue();
                }
                double hb = m.get("hb") instanceof Number n ? n.doubleValue() : 1.6;
                String b = m.get("b") instanceof String bs ? prefix + bs : null;
                String bchars = m.get("bchars") instanceof String bc ? bc : null;
                int adv = m.get("adv") instanceof Number n ? n.intValue() : 0;
                SHEETS.put(prefix + e.getKey(), new Sheet(prefix + e.getKey(), (String) m.get("chars"), ((Number) m.get("frames")).intValue(),
                        ((Number) m.get("aspect")).doubleValue(), Boolean.TRUE.equals(m.get("colored")), font, px, py, hb, b, bchars, adv));
            }
        } catch (IOException | RuntimeException e) {
            Mmorpg.LOG.warn("[vfx] sprite index {}: {}", file, e.toString());
        }
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
        Fx fx = new Fx(level, at, life);
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
            jobs.add(new Job(tick, tick, 1, t -> fn.run()));
            keep(tick + 1);
            return this;
        }

        /** from ~ to 틱 동안 step 틱마다 fn(나이) */
        public Fx each(int from, int to, int step, IntConsumer fn) {
            jobs.add(new Job(from, to, Math.max(1, step), fn));
            keep(to + 1);
            return this;
        }

        /** 입체 소품 (아이템 모델 — 아이템 정의 id, 예: rpg:vfx/rune_axe) */
        public Prop prop(String itemModel, Vec3 at) {
            return prop(itemModel, at, null);
        }

        /**
         * 2026-11-12 처음 모습 (init — 크기 · 회전 · 쥔 곳 · 빛 · 물들임) 을 정한 뒤 띄움: 띄우는 패킷에 처음 모습이 담겨, 같은 틱에 준 움직임 (to … ticks) 이
         * 처음 모습에서부터 보간됨
         */
        public Prop prop(String itemModel, Vec3 at, java.util.function.Consumer<Prop> init) {
            Prop p = new Prop(this, itemModel, at, init);
            props.add(p);
            propsMade++;
            return p;
        }

        /** 2D 그림 (글꼴 rpg:vfx 의 그림판 이름) — 카메라를 봄 */
        public Sprite sprite(String sheet, Vec3 at) {
            return sprite(sheet, at, null);
        }

        /** 2026-11-12 처음 모습 (init — 색 · 방향 · 크기 · 칸 · 늘림) 을 정한 뒤 띄움 (prop 과 같은 까닭) */
        public Sprite sprite(String sheet, Vec3 at, java.util.function.Consumer<Sprite> init) {
            Sprite s = new Sprite(this, sheet(sheet), at, init);
            sprites.add(s);
            spritesMade++;
            return s;
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

        @Override
        protected void onTick() {
            if (ended) return;
            if (age >= life) {
                end();
                return;
            }
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
        /** 쥔 곳 (모델 픽셀 0 ~ 16 자리) — 이 점이 pos 에 오고 이 점 둘레로 돎. 기본 = 모델 가운데 (8, 8, 8) */
        Vector3f grip = new Vector3f(8, 8, 8);
        boolean removed;

        Prop(Fx fx, String itemModel, Vec3 at, java.util.function.Consumer<Prop> init) {
            this.fx = fx;
            ItemStack stack = new ItemStack(Items.PAPER);
            stack.set(DataComponents.ITEM_MODEL, Identifier.parse(itemModel));
            this.e = new ItemDisplayElement(stack);
            e.setItemDisplayContext(ItemDisplayContext.NONE);
            e.setViewRange(1.5F);
            e.setShadowRadius(0F);
            this.pos = at;
            e.setOffset(at.subtract(fx.origin));
            apply();
            if (init != null) {
                init.accept(this);
                e.setInterpolationDuration(0);
            }
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
            return this;
        }

        public Prop move(Vec3 at, int ticks) {
            return to(at, null, -1, ticks);
        }

        public Prop turn(Quaternionf r, int ticks) {
            return to(null, r, -1, ticks);
        }

        public Prop size(double s, int ticks) {
            return to(null, null, s, ticks);
        }

        private void apply() {
            // 모델 가운데 (8, 8, 8) 가 엔티티 자리 → 쥔 곳을 엔티티 자리로: 안쪽 옮김 c = −(grip − 8) / 16 (아이템 표시의 Y 180° 를 되돌린 축)
            Vector3f c = new Vector3f(-(grip.x - 8F) / 16F, -(grip.y - 8F) / 16F, -(grip.z - 8F) / 16F).mul(scale);
            Vector3f tr = rot.transform(c, new Vector3f());
            e.setTranslation(tr);
            e.setLeftRotation(rot);
            e.setScale(new Vector3f(scale, scale, scale));
            e.setRightRotation(ITEM_Y_FLIP ? new Quaternionf().rotateY((float) Math.PI) : new Quaternionf());
        }

        public void remove() {
            if (removed) return;
            removed = true;
            fx.removeElement(e);
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
        /** 2026-11-12 늘림 (그림 가로 · 세로 — 빛줄기 · 감옥 벽) */
        double sx = 1;
        double sy = 1;
        double roll;
        Quaternionf facing;
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

        Sprite(Fx fx, Sheet sheet, Vec3 at, java.util.function.Consumer<Sprite> init) {
            this.fx = fx;
            this.sheet = sheet;
            this.pos = at;
            this.e = new TextDisplayElement();
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
            if (init != null) {
                init.accept(this);
                e.setInterpolationDuration(0);
            }
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
            return this;
        }

        /** 다음 모습 (ticks 틱 보간): 자리 · 크기 (칸 — 0 이하면 그대로) */
        public Sprite to(Vec3 at, double sizeBlocks, int ticks) {
            if (removed) return this;
            if (at != null) {
                pos = at;
                e.setTeleportDuration(Math.max(0, ticks));
                e.setOffset(at.subtract(fx.origin));
            }
            if (sizeBlocks > 0) size = sizeBlocks;
            e.setInterpolationDuration(Math.max(0, ticks));
            transform();
            e.startInterpolationIfDirty();
            return this;
        }

        public Sprite size(double blocks) {
            size = blocks;
            transform();
            return this;
        }

        /** 2026-11-12 늘림: 그림 가로 sx · 세로 sy 배 (ticks 틱 보간) — 축 (pivot) 은 그대로 */
        public Sprite stretch(double sx, double sy, int ticks) {
            if (removed) return this;
            this.sx = sx;
            this.sy = sy;
            e.setInterpolationDuration(Math.max(0, ticks));
            transform();
            e.startInterpolationIfDirty();
            return this;
        }

        /** 2026-11-12 세로축으로만 카메라를 봄 (번개 · 빛기둥 — 서 있는 그림) */
        public Sprite vertical() {
            facing = null;
            e.setBillboardMode(Display.BillboardConstraints.VERTICAL);
            transform();
            return this;
        }

        public Vec3 pos() {
            return pos;
        }

        public boolean removed() {
            return removed;
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
            String g = String.valueOf(sheet.glyph(frame));
            Style st = Style.EMPTY.withFont(sheet.font);
            net.minecraft.network.chat.MutableComponent t = Component.literal(g).withStyle(st.withColor(rgb));
            // 2026-11-12 픽셀 그림의 흰 심지 (B 글자 — 흰빛 0.7 섞은 색, 뒤로 옮겨 같은 자리에)
            char b = sheet.bglyph(frame);
            if (b != 0 && sheet.adv() > 0) {
                StringBuilder back = new StringBuilder();
                for (int bit = 10; bit >= 0; bit--) if ((sheet.adv() & (1 << bit)) != 0) back.appendCodePoint(spaceFirst + bit);
                int r = rgb >> 16 & 255;
                int gg = rgb >> 8 & 255;
                int bb = rgb & 255;
                int w = (r + (int) Math.round((255 - r) * 0.7)) << 16 | (gg + (int) Math.round((255 - gg) * 0.7)) << 8 | (bb + (int) Math.round((255 - bb) * 0.7));
                t.append(Component.literal(back.toString()).withStyle(st));
                t.append(Component.literal(String.valueOf(b)).withStyle(st.withColor(w)));
            }
            e.setText(t);
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
            // 축 (pivot) 이 엔티티 자리에 오게: 그림 가운데 (글자 가운데 = 엔티티 자리) 를 축의 반대쪽으로 (회전한 면 안에서)
            Vector3f t = new Vector3f();
            if (!hidden && (sheet.px() != 0.5 || sheet.py() != 0.5)) {
                double w = size * sheet.aspect() * sx;
                double h = size * sy;
                t = q.transform(new Vector3f((float) (-(sheet.px() - 0.5) * w), (float) (-(0.5 - sheet.py()) * h), 0F));
            }
            e.setTranslation(t);
            e.setLeftRotation(q);
            e.setScale(new Vector3f((float) (s * sx), (float) (s * sy), s));
            e.setRightRotation(new Quaternionf());
        }

        public void remove() {
            if (removed) return;
            removed = true;
            fx.removeElement(e);
        }
    }
}
