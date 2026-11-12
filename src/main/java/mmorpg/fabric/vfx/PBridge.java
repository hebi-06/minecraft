package mmorpg.fabric.vfx;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import mmorpg.fabric.AddonParticleOptions;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.vfx.PDefs.Def;
import mmorpg.fabric.vfx.PDefs.Layer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 2026-11-12 모드 없는 클라이언트의 파티클 다리 (사용자 "룬 스킬 다 만들어보자" · 서버에 접속해 리소스만 받으면 바로): 모드 클라이언트가 그리는 애드온 파티클
 * (AddonFx — 참격 · 마법진 · 고리 · 터짐 · 빛줄기 · 룬 그림 rfx_*) 과 2D 판 (Fx3d 의 slash_* 카드 — 휘두른 자국) 을 모드 없는 클라이언트에게는 서버가 같은 계산
 * ({@link PSim} — 모드와 같은 Molang 식) 으로 움직이는 글자 표시 엔티티 (글꼴 rpg:vfxp — 같은 그림) 로 보냄. 모드 클라이언트에는 그대로 파티클 (이 다리는 보이지 않음).
 * <ul>
 * <li>알갱이 하나 = 글자 표시 하나: 자리 (틱마다 · 1틱 보간) · 크기 (가로 · 세로) · 색 (글자 색) · 투명 (글자 불투명도 — 0.1 아래는 숨김) · 칸 (글자) · 방향
 *     (카메라를 봄 = 빌보드 가운데 · 세로 축 = 빌보드 세로 · 정한 방향 = 고정 — 한쪽 면만 그려지므로 보는 사람 쪽으로 앞면).</li>
 * <li>작은 알갱이 (반 크기 {@link #SMALL} 칸 아래 — 불티 · 빛 알갱이) 는 같은 색의 바닐라 가루 파티클 (싸고 비슷함).</li>
 * <li>보는 사람 걸러내기 (view): 모드 없는 클라이언트는 1 · 3인칭을 서버가 모름 → 1인칭으로 봄 (주인 몸 둘레 그림 view 1 은 주인에게 숨김 · view 2 는 주인에게만) —
 *     시험 영상 (3인칭 카메라) 은 {@link #thirdPerson} 로.</li>
 * <li>solo (주인마다 하나): 같은 (주인 · 번호) 의 새 그림은 앞 그림의 엔티티를 그대로 이어 씀 (다시 그리는 큰 그림이 엔티티를 매번 새로 만들지 않게).</li>
 * <li>한도: 떠 있는 글자 표시 {@link #MAX_LIVE} · 한 번에 뿜는 알갱이 {@link #MAX_BATCH} — 넘으면 버림 (모드 클라이언트는 그대로).</li>
 * </ul>
 */
public final class PBridge {
    private PBridge() {}

    /** 모드 없는 클라이언트에게 보내는 거리 (칸) */
    static final double RANGE = 64;
    /** 떠 있는 글자 표시 수 (넘으면 새 알갱이는 버림) */
    static final int MAX_LIVE = 420;
    /** 한 번에 뿜는 알갱이 (instant 수 · 계속 뿜는 발생기의 살아 있는 수) */
    static final int MAX_BATCH = 14;
    /** 이보다 작은 알갱이 (반 크기 칸) 는 바닐라 가루 파티클 */
    static final double SMALL = 0.11;
    static final double TEXT_UNIT = 0.025;
    static final int MIN_OPACITY = 26;

    private static final List<Batch> ACTIVE = new ArrayList<>();
    private static final Map<Long, Part> SOLO = new HashMap<>();
    private static final Set<Integer> THIRD = new HashSet<>();
    static int live;
    /** 시험용 셈: 만든 글자 표시 · 가루 · 버림 · 카드 · 다시 쓴 solo · 그림 없는 파티클 */
    public static int made;
    public static int dusts;
    public static int dropped;
    public static int cards;
    public static int soloReused;
    public static int noGlyph;
    /** 시험용: 파티클 ID → 다리에 들어온 수 */
    public static final Map<String, Integer> SEEN = new java.util.TreeMap<>();
    private static FontDescription font;
    private static final Set<String> WARNED = new HashSet<>();

    public static void init() {
        PDefs.init();
        font = new FontDescription.Resource(Identifier.parse(PDefs.font));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
    }

    static void clear() {
        for (Batch b : new ArrayList<>(ACTIVE)) b.end();
        ACTIVE.clear();
        SOLO.clear();
        live = 0;
    }

    /** 떠 있는 글자 표시 수 (시험용) */
    public static int live() {
        return live;
    }

    /** 이 사람 (모드 없는 클라이언트) 을 3인칭으로 봄 (시험 영상 — 3인칭 카메라) */
    public static void thirdPerson(ServerPlayer sp, boolean on) {
        if (on) THIRD.add(sp.getId());
        else THIRD.remove(sp.getId());
    }

    static boolean isThird(ServerPlayer sp) {
        return THIRD.contains(sp.getId());
    }

    // ------------------------------------------------------------ 들어오는 곳

    /** AddonFx.spawn / spawnFor: 그 자리를 볼 모드 없는 클라이언트 (plain — 이미 걸러짐) 에게 파티클 하나 (발생기) */
    public static void spawn(ServerLevel level, AddonParticleOptions o, Vec3 at, List<ServerPlayer> plain) {
        Def d = PDefs.def(o.particle());
        if (d == null || plain.isEmpty()) return;
        double[] ev = new double[PDefs.SLOTS];
        int[] slots = o.slots();
        float[] values = o.values();
        for (int i = 0; i < slots.length; i++) {
            int s = PDefs.CUSTOM + slots[i];
            if (s >= PDefs.CUSTOM && s < PDefs.SLOTS) ev[s] = values[i];
        }
        SEEN.merge(d.id, 1, Integer::sum);
        // 보는 사람 걸러내기 (view · 주인)
        int view = PDefs.VIEW >= 0 ? (int) Math.round(ev[PDefs.VIEW]) : 0;
        int solo = PDefs.SOLO >= 0 ? (int) Math.round(ev[PDefs.SOLO]) : 0;
        int owner = (view == 0 && solo <= 0) || PDefs.OWNER_HI < 0 ? -1
                : ((int) Math.round(ev[PDefs.OWNER_HI]) << 16) | ((int) Math.round(ev[PDefs.OWNER_LO]) & 0xFFFF);
        List<ServerPlayer> who = new ArrayList<>(plain.size());
        for (ServerPlayer sp : plain) {
            if (sp.level() != level || sp.position().distanceToSqr(at) > RANGE * RANGE) continue;
            if (view != 0 && owner >= 0) {
                boolean ownerFp = sp.getId() == owner && !isThird(sp);
                if (view == 1 && ownerFp || view == 2 && !ownerFp) continue;
            }
            who.add(sp);
        }
        if (who.isEmpty()) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int k = 0; k < 4; k++) ev[PDefs.ER1 + k] = r.nextDouble();
        ev[PDefs.ELIFE] = d.active.eval(ev);
        long soloKey = solo > 0 && owner >= 0 ? ((long) owner << 20) | (solo & 0xFFFFF) : -1;
        if (d.sheet == null) {
            // 그림이 없는 파티클 (바닐라 그림 · 직업 그림 — 아직): 가루로
            if (WARNED.add(d.id)) Mmorpg.LOG.info("[vfx] particle {} has no glyph — dust", d.id);
            noGlyph++;
            if (d.instant) {
                PSim p = new PSim(at, d, ev, 0);
                dust(level, who, p);
            }
            return;
        }
        if (soloKey >= 0) {
            Part prev = SOLO.get(soloKey);
            if (prev != null && !prev.dead && prev.batch.level == level) {
                prev.restart(new PSim(at, d, ev, 0));
                soloReused++;
                return;
            }
        }
        if (d.instant) {
            int n = (int) Math.round(d.rate.eval(ev));
            if (n <= 0) return;
            Batch b = null;
            int take = Math.min(n, MAX_BATCH);
            for (int i = 0; i < take; i++) {
                PSim p = new PSim(at, d, ev, 0);
                if (small(p)) {
                    dust(level, who, p);
                    continue;
                }
                if (live >= MAX_LIVE) {
                    dropped++;
                    continue;
                }
                if (b == null) b = new Batch(level, at, who);
                Part part = b.add(p);
                if (soloKey >= 0 && i == 0) {
                    part.soloKey = soloKey;
                    SOLO.put(soloKey, part);
                }
            }
            if (b != null) b.attach();
            return;
        }
        // 계속 뿜는 발생기
        Batch b = new Batch(level, at, who);
        b.emitter = new Emitter(d, ev, at);
        b.attach();
    }

    /** 작은 알갱이인지 (처음 · 수명 1/3 · 2/3 의 반 크기가 모두 SMALL 아래) */
    private static boolean small(PSim p) {
        double m = 0;
        double[] save = {p.v[PDefs.AGE], p.v[PDefs.EAGE]};
        for (double k : new double[] {0.02, 0.33, 0.66}) {
            p.v[PDefs.AGE] = p.life * k;
            m = Math.max(m, Math.max(p.d.sizeX.eval(p.v), p.d.sizeY.eval(p.v)));
        }
        p.v[PDefs.AGE] = save[0];
        p.v[PDefs.EAGE] = save[1];
        return m < SMALL;
    }

    /** 작은 알갱이 → 같은 색 바닐라 가루 (처음 자리 · 많이 움직이면 끝 자리에도) */
    private static void dust(ServerLevel level, List<ServerPlayer> who, PSim p) {
        PSim.State s = new PSim.State();
        double sx = p.x;
        double sy = p.y;
        double sz = p.z;
        boolean shown = false;
        double half = SMALL;
        int rgb = 0xFFFFFF;
        // 처음 보이는 모습 (늦게 켜지는 알갱이 — delay)
        for (int i = 0; i < 6 && !shown; i++) {
            if (p.state(s)) {
                shown = true;
                half = Math.max(s.w, s.h);
                rgb = rgb(s.r, s.g, s.b);
                sx = p.x;
                sy = p.y;
                sz = p.z;
            } else if (!p.tick()) break;
        }
        if (!shown) return;
        float scale = (float) Math.max(0.3, Math.min(2.5, half / 0.1));
        DustParticleOptions dust = new DustParticleOptions(rgb, scale);
        send(level, who, dust, sx, sy, sz);
        // 끝 자리 (반쯤 산 자리)
        int half2 = Math.max(1, p.lifetime / 2);
        for (int i = 0; i < half2; i++) if (!p.tick()) break;
        if ((p.x - sx) * (p.x - sx) + (p.y - sy) * (p.y - sy) + (p.z - sz) * (p.z - sz) > 0.36) send(level, who, dust, p.x, p.y, p.z);
        dusts++;
    }

    private static void send(ServerLevel level, List<ServerPlayer> who, DustParticleOptions o, double x, double y, double z) {
        for (ServerPlayer sp : who) level.sendParticles(sp, o, true, true, x, y, z, 1, 0, 0, 0, 0);
    }

    static int rgb(double r, double g, double b) {
        return (int) Math.round(r * 255) << 16 | (int) Math.round(g * 255) << 8 | (int) Math.round(b * 255);
    }

    // ------------------------------------------------------------ Fx3d 카드 (2D 판)

    /** Fx3d.spawn: 판 모델 (slash_* — 카드 목록에 있으면) 을 모드 없는 클라이언트에게. false = 이 모델은 다리가 없음 */
    public static boolean card(ServerLevel level, String model, Vec3 at, Opt o, List<ServerPlayer> plain) {
        List<Layer[]> frames = PDefs.card(model);
        if (frames != null && (o.has("follow") || o.has("orbit") || o.has("arc") || o.has("spin"))) frames = null; // 주인 기준 · 도는 길 · 휘두름 · 돌기 — 아직 없음
        if (frames == null || frames.isEmpty()) {
            if (WARNED.add("card:" + model)) Mmorpg.LOG.info("[vfx] fx model {} has no vanilla card", model);
            return false;
        }
        int view = (int) o.num("view", 0);
        int owner = (int) o.num("owner", o.num("follow", 0));
        List<ServerPlayer> who = new ArrayList<>(plain.size());
        for (ServerPlayer sp : plain) {
            if (sp.level() != level || sp.position().distanceToSqr(at) > RANGE * RANGE) continue;
            if (view != 0) {
                boolean ownerFp = sp.getId() == owner && !isThird(sp);
                if (view == 1 && ownerFp || view == 2 && !ownerFp) continue;
            }
            who.add(sp);
        }
        if (who.isEmpty()) return true;
        if (live >= MAX_LIVE) {
            dropped++;
            return true;
        }
        Batch b = new Batch(level, at, who);
        b.card = new Card(b, frames, at, o);
        b.attach();
        cards++;
        return true;
    }

    // ------------------------------------------------------------ 효과 묶음 (한 번 뿜은 것 — 세계의 한 점에 붙은 홀더)

    static final class Batch extends ElementHolder {
        final ServerLevel level;
        final Vec3 origin;
        final Set<Integer> who = new HashSet<>();
        final List<Part> parts = new ArrayList<>();
        Emitter emitter;
        Card card;
        boolean ended;
        boolean attached;
        int age;
        /** 앞면을 돌릴 기준 눈 (가장 가까운 보는 사람) */
        Vec3 eye;
        final List<ServerPlayer> viewers;

        Batch(ServerLevel level, Vec3 origin, List<ServerPlayer> viewers) {
            this.level = level;
            this.origin = origin;
            this.viewers = viewers;
            for (ServerPlayer sp : viewers) who.add(sp.getId());
            refreshEye();
        }

        void refreshEye() {
            double best = Double.MAX_VALUE;
            for (ServerPlayer sp : viewers) {
                if (sp.isRemoved()) continue;
                Vec3 e = sp.getEyePosition();
                double d = e.distanceToSqr(origin);
                if (d < best) {
                    best = d;
                    eye = e;
                }
            }
            if (eye == null) eye = origin.add(0, 1.6, -4);
        }

        Part add(PSim p) {
            Part part = new Part(this, p);
            parts.add(part);
            return part;
        }

        void attach() {
            if (attached) return;
            attached = true;
            ACTIVE.add(this);
            ChunkAttachment.ofTicking(this, level, origin);
        }

        @Override
        public boolean startWatching(net.minecraft.server.network.ServerGamePacketListenerImpl player) {
            if (!who.contains(player.getPlayer().getId())) return false;
            return super.startWatching(player);
        }

        @Override
        protected void onTick() {
            if (ended) return;
            age++;
            if (age % 5 == 0) refreshEye();
            if (emitter != null && !emitter.tick(this)) emitter = null;
            for (Iterator<Part> it = parts.iterator(); it.hasNext(); ) {
                Part p = it.next();
                if (!p.step()) {
                    p.remove();
                    it.remove();
                }
            }
            if (card != null && !card.step()) {
                card.remove();
                card = null;
            }
            if (parts.isEmpty() && emitter == null && card == null) end();
        }

        void end() {
            if (ended) return;
            ended = true;
            for (Part p : parts) p.remove();
            parts.clear();
            if (card != null) card.remove();
            ACTIVE.remove(this);
            destroy();
        }
    }

    /** 계속 뿜는 발생기 (client AddonEmitter 와 같음 — 살아 있는 수는 MAX_BATCH 까지) */
    static final class Emitter {
        final Def d;
        final double[] ev;
        final double active;
        final Vec3 at;
        int age;
        double carry;
        final List<Part> mine = new ArrayList<>();

        Emitter(Def d, double[] ev, Vec3 at) {
            this.d = d;
            this.ev = ev;
            this.at = at;
            this.active = Math.max(0, d.active.eval(ev));
        }

        boolean tick(Batch b) {
            double t = age / 20.0;
            if (age++ >= (int) Math.ceil(active * 20) + 1 || t > active) return false;
            ev[PDefs.EAGE] = t;
            mine.removeIf(p -> p.dead);
            double rate = Math.max(0, d.rate.eval(ev));
            int max = Math.min(MAX_BATCH, (int) Math.max(0, Math.round(d.max.eval(ev))));
            carry += rate / 20.0;
            int n = (int) Math.floor(carry);
            carry -= n;
            for (int i = 0; i < n && mine.size() < max; i++) {
                PSim p = new PSim(at, d, ev, t);
                if (small(p)) {
                    dust(b.level, b.viewers, p);
                    continue;
                }
                if (live >= MAX_LIVE) {
                    dropped++;
                    continue;
                }
                Part part = b.add(p);
                mine.add(part);
            }
            return true;
        }
    }

    // ------------------------------------------------------------ 알갱이 하나 (글자 표시)

    static final class Part {
        final Batch batch;
        PSim sim;
        final TextDisplayElement e;
        final PSim.State s = new PSim.State();
        boolean dead;
        boolean first = true;
        boolean hidden;
        long soloKey = -1;
        int glyph = -2;
        int rgb = -1;
        int opacity = -1;
        Display.BillboardConstraints mode;
        Vec3 pos;

        Part(Batch batch, PSim sim) {
            this.batch = batch;
            this.sim = sim;
            this.e = new TextDisplayElement();
            e.setBackground(0);
            e.setDefaultBackground(false);
            e.setShadow(false);
            e.setSeeThrough(false);
            e.setLineWidth(4096);
            e.setBrightness(Brightness.FULL_BRIGHT);
            e.setViewRange(1.5F);
            live++;
            made++;
            apply();
            batch.addElement(e);
            first = false;
        }

        /** solo: 같은 (주인 · 번호) 의 새 그림 — 이 엔티티로 이어 그림 */
        void restart(PSim next) {
            sim = next;
            apply();
        }

        /** 한 틱 (false = 다 삶) */
        boolean step() {
            if (dead) return false;
            if (!sim.tick()) return false;
            apply();
            return true;
        }

        void apply() {
            int dur = first ? 0 : 1;
            Vec3 at = new Vec3(sim.x, sim.y, sim.z);
            boolean vis = sim.state(s) && s.glyph >= 0;
            int o = vis ? (int) Math.round(s.a * 255) : 0;
            vis = vis && o >= MIN_OPACITY;
            if (pos == null || at.distanceToSqr(pos) > 1e-6) {
                pos = at;
                e.setTeleportDuration(dur);
                e.setOffset(at.subtract(batch.origin));
            }
            if (!vis) {
                if (!hidden) {
                    hidden = true;
                    e.setInterpolationDuration(0);
                    e.setScale(new Vector3f());
                    e.startInterpolationIfDirty();
                }
                return;
            }
            boolean wasHidden = hidden;
            hidden = false;
            int c = rgb(s.r, s.g, s.b);
            if (s.glyph != glyph || c != rgb) {
                glyph = s.glyph;
                rgb = c;
                e.setText(Component.literal(new String(Character.toChars(glyph))).withStyle(Style.EMPTY.withFont(font).withColor(rgb)));
            }
            if (Math.abs(o - opacity) > 2 || (o >= 250) != (opacity >= 250)) {
                opacity = o;
                e.setTextOpacity((byte) Math.min(255, o));
            }
            // 방향
            int f = sim.d.facing;
            Display.BillboardConstraints m;
            Quaternionf q;
            if (f == PDefs.ROTATE_XYZ || f == PDefs.LOOKAT_XYZ) {
                m = Display.BillboardConstraints.CENTER;
                q = new Quaternionf();
                if (s.spin != 0) q.rotateZ((float) Math.toRadians(s.spin));
            } else if (f == PDefs.ROTATE_Y || f == PDefs.LOOKAT_Y) {
                m = Display.BillboardConstraints.VERTICAL;
                q = new Quaternionf();
                if (s.spin != 0) q.rotateZ((float) Math.toRadians(s.spin));
            } else {
                m = Display.BillboardConstraints.FIXED;
                q = fixed(f, at, s.dir, batch.eye);
                if (q == null) {
                    hide();
                    return;
                }
                if (s.spin != 0 && f != PDefs.LOOKAT_DIRECTION) q.rotateZ((float) Math.toRadians(s.spin));
                // 한쪽 면만 그려짐 → 앞면을 보는 사람 쪽으로 (그림 세로 축 둘레 반 바퀴)
                Vector3f n = q.transform(new Vector3f(0, 0, 1));
                Vec3 to = batch.eye.subtract(at);
                if (n.x * to.x + n.y * to.y + n.z * to.z < 0) q.rotateY((float) Math.PI);
            }
            if (m != mode) {
                mode = m;
                e.setBillboardMode(m);
            }
            PDefs.Sheet sh = sim.d.sheet;
            double unit = PDefs.glyphH * TEXT_UNIT;
            float sy = (float) (2 * s.h / unit);
            float sx = (float) (2 * s.w / (unit * sh.aspect()));
            // 글자 가운데 맞춤 (글줄 너비 = advance — 그림 너비보다 1 단위쯤 넓음)
            double gw = PDefs.glyphH * sh.aspect();
            float cx = (float) ((sh.adv() - gw) / 2 * TEXT_UNIT * sx);
            Vector3f tr = q.transform(new Vector3f(cx, 0, 0));
            e.setInterpolationDuration(wasHidden ? 0 : dur);
            e.setTranslation(tr);
            e.setLeftRotation(q);
            e.setScale(new Vector3f(sx, sy, 1));
            e.setRightRotation(new Quaternionf());
            e.startInterpolationIfDirty();
        }

        private void hide() {
            if (hidden) return;
            hidden = true;
            e.setInterpolationDuration(0);
            e.setScale(new Vector3f());
            e.startInterpolationIfDirty();
        }

        void remove() {
            if (dead) return;
            dead = true;
            live--;
            if (soloKey >= 0) SOLO.remove(soloKey, this);
            batch.removeElement(e);
        }
    }

    /** 정한 방향 (고정 빌보드) 의 회전 — client AddonParticle.orientation 과 같음 (카메라 = 기준 눈) */
    static Quaternionf fixed(int facing, Vec3 at, Vector3f dir, Vec3 eye) {
        switch (facing) {
            case PDefs.DIRECTION_X, PDefs.DIRECTION_Y, PDefs.DIRECTION_Z -> {
                if (dir == null || dir.lengthSquared() < 1e-10) return null;
                Vector3f dv = new Vector3f(dir).normalize();
                Vector3f ref = Math.abs(dv.y) < 0.999F ? new Vector3f(0, 1, 0) : new Vector3f(0, 0, 1);
                if (facing == PDefs.DIRECTION_Z) {
                    Vector3f yv = new Vector3f(ref).sub(new Vector3f(dv).mul(ref.dot(dv))).normalize();
                    Vector3f xv = new Vector3f(yv).cross(dv).normalize();
                    return basis(xv, yv, dv);
                }
                if (facing == PDefs.DIRECTION_X) {
                    Vector3f zv = new Vector3f(dv).cross(ref).normalize();
                    Vector3f yv = new Vector3f(zv).cross(dv).normalize();
                    return basis(dv, yv, zv);
                }
                Vector3f zv = new Vector3f(ref).cross(dv);
                if (zv.lengthSquared() < 1e-10) zv = new Vector3f(1, 0, 0).cross(dv);
                zv.normalize();
                Vector3f xv = new Vector3f(dv).cross(zv).normalize();
                return basis(xv, dv, zv);
            }
            case PDefs.LOOKAT_DIRECTION -> {
                if (dir == null || dir.lengthSquared() < 1e-10) return null;
                Vector3f dv = new Vector3f(dir).normalize();
                Vector3f toCam = new Vector3f((float) (eye.x - at.x), (float) (eye.y - at.y), (float) (eye.z - at.z));
                Vector3f n = new Vector3f(toCam).sub(new Vector3f(dv).mul(toCam.dot(dv)));
                if (n.lengthSquared() < 1e-10) {
                    n.set(Math.abs(dv.y) < 0.9F ? 0 : 1, Math.abs(dv.y) < 0.9F ? 1 : 0, 0);
                    n.sub(new Vector3f(dv).mul(n.dot(dv)));
                }
                n.normalize();
                Vector3f yv = new Vector3f(n).cross(dv).normalize();
                return basis(dv, yv, n);
            }
            case PDefs.EMITTER_XY -> {
                return new Quaternionf();
            }
            case PDefs.EMITTER_YZ -> {
                return basis(new Vector3f(0, 0, -1), new Vector3f(0, 1, 0), new Vector3f(1, 0, 0));
            }
            default -> {
                // EMITTER_XZ: 땅에 눕힘 (면이 위)
                return basis(new Vector3f(1, 0, 0), new Vector3f(0, 0, -1), new Vector3f(0, 1, 0));
            }
        }
    }

    static Quaternionf basis(Vector3f xv, Vector3f yv, Vector3f zv) {
        return new Quaternionf().setFromNormalized(new Matrix3f(xv, yv, zv));
    }

    // ------------------------------------------------------------ 카드 (Fx3d 2D 판 — 휘두른 자국)

    /** Fx3d 판 하나 (client FxModels 와 같은 자리 · 방향 · 크기 · 투명 · 칸 — 겹은 한 글줄에 겹쳐 그림: 몸 → 빛) */
    static final class Card {
        final Batch batch;
        final List<Layer[]> frames;
        final TextDisplayElement e;
        final Vec3 at;
        final Vec3 dir0;
        final Vec3 face0;
        final double roll;
        final Vec3 vel;
        final Vec3 acc;
        final Vec3 to;
        final Vec3 ctrl;
        final double travel;
        final int ease;
        final double life;
        final double scale;
        final double grow;
        final double pop;
        final double shrink;
        final double alpha;
        final double fadeIn;
        final double fadeOut;
        final double delay;
        final int[] color;
        final boolean faceCam;
        final boolean anim;
        final double animSpeed;
        final double animPhase;
        final boolean loop;
        int age;
        int frame = -1;
        int opacity = -1;
        boolean hidden = true;
        boolean first = true;
        boolean dead;
        Vec3 pos;

        Card(Batch batch, List<Layer[]> frames, Vec3 at, Opt o) {
            this.batch = batch;
            this.frames = frames;
            this.at = at;
            this.dir0 = o.vec("dir", new Vec3(0, 1, 0));
            this.face0 = o.vec("face", Vec3.ZERO);
            this.roll = o.num("roll", 0);
            this.vel = o.vec("vel", Vec3.ZERO);
            this.acc = o.vec("acc", Vec3.ZERO);
            if (o.has("to")) {
                to = o.vec("to", at);
                ctrl = o.vec("ctrl", at.add(to).scale(0.5));
            } else {
                to = null;
                ctrl = null;
            }
            this.life = Math.max(0.05, o.num("life", 1));
            this.travel = Math.max(0.01, o.num("travel", life));
            this.ease = ease(o.str("ease", null));
            this.scale = o.num("scale", 1);
            this.grow = o.num("grow", 0);
            this.pop = o.num("pop", 0);
            this.shrink = o.num("shrink", 0);
            this.alpha = o.num("alpha", 1);
            this.fadeIn = o.num("fadeIn", 0);
            this.fadeOut = o.num("fadeOut", 0.15);
            this.delay = o.num("delay", 0);
            double[] c = o.color("color", new double[] {1, 1, 1});
            double[] g = o.color("glow", c);
            this.color = new int[] {rgb(c[0], c[1], c[2]), rgb(g[0], g[1], g[2])};
            this.faceCam = o.truthy("faceCam");
            this.anim = !o.str("anim", "").isEmpty();
            this.animSpeed = o.num("animSpeed", 1);
            this.animPhase = o.num("animPhase", 0);
            this.loop = o.truthy("loop");
            this.e = new TextDisplayElement();
            e.setBackground(0);
            e.setDefaultBackground(false);
            e.setShadow(false);
            e.setSeeThrough(false);
            e.setLineWidth(4096);
            e.setBrightness(Brightness.FULL_BRIGHT);
            e.setViewRange(1.5F);
            e.setBillboardMode(Display.BillboardConstraints.FIXED);
            e.setScale(new Vector3f());
            live++;
            made++;
            apply();
            batch.addElement(e);
            first = false;
        }

        boolean step() {
            if (dead) return false;
            age++;
            double t = age / 20.0 - delay;
            if (t > life) return false;
            apply();
            return true;
        }

        void apply() {
            double t = age / 20.0 - delay;
            int dur = first ? 0 : 1;
            if (t < 0) return;
            float tn = (float) Math.max(0, Math.min(1, t / Math.max(0.01, life)));
            // 자리 (곧게 · 곡선 길)
            Vec3 p;
            if (to != null) {
                double k = easeK(ease, t / travel);
                double a = (1 - k) * (1 - k);
                double b = 2 * (1 - k) * k;
                double c = k * k;
                p = new Vec3(a * at.x + b * ctrl.x + c * to.x, a * at.y + b * ctrl.y + c * to.y, a * at.z + b * ctrl.z + c * to.z);
            } else {
                p = new Vec3(at.x + vel.x * t + 0.5 * acc.x * t * t, at.y + vel.y * t + 0.5 * acc.y * t * t, at.z + vel.z * t + 0.5 * acc.z * t * t);
            }
            // 크기 · 투명
            double s = scale;
            if (pop > 0) s *= Math.max(0.01, easeK(5, t / pop));
            if (grow != 0) s *= 1 + grow * tn;
            if (shrink > 0) s *= Math.max(0, Math.min(1, (life - t) / shrink));
            double a = alpha;
            if (fadeIn > 0) a *= Math.max(0, Math.min(1, t / fadeIn));
            if (fadeOut > 0) a *= Math.max(0, Math.min(1, (life - t) / fadeOut));
            int o = (int) Math.round(Math.max(0, Math.min(1, a)) * 255);
            // 칸 (anim play — 칸마다 같은 길이)
            int f = 0;
            if (anim && frames.size() > 1) {
                double u = (t * animSpeed + animPhase);
                u = loop ? u - Math.floor(u) : Math.max(0, Math.min(1, u));
                f = Math.min(frames.size() - 1, (int) Math.floor(u * frames.size()));
            }
            if (pos == null || p.distanceToSqr(pos) > 1e-6) {
                pos = p;
                e.setTeleportDuration(dur);
                e.setOffset(p.subtract(batch.origin));
            }
            if (o < MIN_OPACITY || s <= 0.001) {
                if (!hidden) {
                    hidden = true;
                    e.setInterpolationDuration(0);
                    e.setScale(new Vector3f());
                    e.startInterpolationIfDirty();
                }
                return;
            }
            boolean wasHidden = hidden;
            hidden = false;
            Layer[] ls = frames.get(f);
            if (f != frame) {
                frame = f;
                MutableComponent text = Component.empty();
                for (int i = 0; i < ls.length; i++) {
                    Layer l = ls[i];
                    if (i > 0) text.append(Component.literal(back(ls[i - 1].adv())).withStyle(Style.EMPTY.withFont(font)));
                    text.append(Component.literal(new String(Character.toChars(l.glyph()))).withStyle(Style.EMPTY.withFont(font).withColor(color[Math.min(1, l.mat())])));
                }
                e.setText(text);
            }
            if (Math.abs(o - opacity) > 2) {
                opacity = o;
                e.setTextOpacity((byte) Math.min(255, o));
            }
            // 방향: Y = dir, Z = face (또는 보는 사람), X = Y × Z, dir 둘레로 roll
            Vector3f Y = new Vector3f((float) dir0.x, (float) dir0.y, (float) dir0.z);
            if (Y.lengthSquared() < 1e-8F) Y.set(0, 1, 0);
            Y.normalize();
            Vector3f Z;
            Vec3 toEye = batch.eye.subtract(p);
            if (faceCam) Z = new Vector3f((float) toEye.x, (float) toEye.y, (float) toEye.z);
            else if (face0.lengthSqr() > 1e-6) Z = new Vector3f((float) face0.x, (float) face0.y, (float) face0.z);
            else Z = new Vector3f(0, 1, 0);
            Z.sub(new Vector3f(Y).mul(Z.dot(Y)));
            if (Z.lengthSquared() < 1e-6F) {
                Z.set(Math.abs(Y.y) < 0.9F ? 0 : 1, Math.abs(Y.y) < 0.9F ? 1 : 0, 0);
                Z.sub(new Vector3f(Y).mul(Z.dot(Y)));
            }
            Z.normalize();
            Vector3f X = new Vector3f(Y).cross(Z).normalize();
            Quaternionf q = basis(X, Y, Z);
            if (roll != 0) q.rotateY((float) Math.toRadians(roll));
            Vector3f n = q.transform(new Vector3f(0, 0, 1));
            if (n.x * toEye.x + n.y * toEye.y + n.z * toEye.z < 0) q.rotateY((float) Math.PI);
            Layer l0 = ls[ls.length - 1];
            double unit = PDefs.glyphH * TEXT_UNIT;
            float k = (float) (2 * l0.hh() * s / unit);
            // 글줄 너비 = 마지막 겹의 advance (겹은 같은 너비) — 그림 너비와의 차이만큼 가운데 맞춤 · 모델 자리 (판 가운데)
            double gw = PDefs.glyphH * (l0.hw() / Math.max(1e-6, l0.hh()));
            float cx = (float) ((l0.adv() - gw) / 2 * TEXT_UNIT * k);
            Vector3f tr = q.transform(new Vector3f(cx + (float) (l0.x() * s), (float) (l0.y() * s), (float) (l0.z() * s)));
            e.setInterpolationDuration(wasHidden ? 0 : dur);
            e.setTranslation(tr);
            e.setLeftRotation(q);
            e.setScale(new Vector3f(k, k, 1));
            e.setRightRotation(new Quaternionf());
            e.startInterpolationIfDirty();
        }

        void remove() {
            if (dead) return;
            dead = true;
            live--;
            batch.removeElement(e);
        }
    }

    /** 뒤로 n 단위 옮기는 글자들 (space 글자 −2^b) */
    static String back(int n) {
        StringBuilder b = new StringBuilder();
        for (int bit = 10; bit >= 0; bit--) {
            if ((n & (1 << bit)) != 0) b.appendCodePoint(PDefs.spaceFirst + bit);
        }
        return b.toString();
    }

    static int ease(String e) {
        return switch (e == null ? "" : e) {
            case "in" -> 1;
            case "out" -> 2;
            case "inout" -> 3;
            case "out3" -> 4;
            case "back" -> 5;
            default -> 0;
        };
    }

    static double easeK(int kind, double k) {
        k = Math.max(0, Math.min(1, k));
        return switch (kind) {
            case 1 -> k * k;
            case 2 -> 1 - (1 - k) * (1 - k);
            case 3 -> k * k * (3 - 2 * k);
            case 4 -> 1 - (1 - k) * (1 - k) * (1 - k);
            case 5 -> {
                double c1 = 1.70158;
                double c3 = c1 + 1;
                double x = k - 1;
                yield 1 + c3 * x * x * x + c1 * x * x;
            }
            default -> k;
        };
    }
}
