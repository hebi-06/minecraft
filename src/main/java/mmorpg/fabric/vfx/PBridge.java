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

    /** 떠 있는 입체 부분 (아이템 표시) 수 (시험용) */
    public static int solidLive() {
        return solidLive;
    }

    /** 이 사람 (모드 없는 클라이언트) 을 3인칭으로 봄 (시험 영상 — 3인칭 카메라) */
    public static void thirdPerson(ServerPlayer sp, boolean on) {
        if (on) THIRD.add(sp.getId());
        else THIRD.remove(sp.getId());
    }

    static boolean isThird(ServerPlayer sp) {
        return THIRD.contains(sp.getId());
    }

    /** 이 사람 (모드 없는 클라이언트) 을 3인칭으로 보는지 (시험 영상 카메라 — 보통 놀이는 1인칭으로 봄) */
    public static boolean thirdPersonOf(ServerPlayer sp) {
        return THIRD.contains(sp.getId());
    }

    /** 시험 영상 카메라 (사진기 cam film 과 같은 값 — Probe.filmCamera): {각(라디안), 거리, 높이, 보는 높이, 앞, 따라감, 처음 x, y, z, 앞 x, 앞 z} */
    private static final Map<Integer, double[]> FILM = new HashMap<>();

    /**
     * 2026-11-14 시험 영상 카메라를 서버도 앎 (VanillaProbe 가 사진기에 cam film 을 보낼 때 같은 값): 가는 쪽으로 눕는 그림 (화살 · 번개 줄 · 광선 · 참격 판) 의 앞면을
     * 몸의 눈 대신 영상 카메라 쪽으로 돌림 (3인칭 영상에서 그림이 옆으로 눕거나 얇게 보이지 않게). angle 도 = 보는 쪽에서 왼쪽으로
     */
    public static void filmCam(ServerPlayer sp, double angle, double dist, double height, double lookY, double ahead, double follow) {
        double yaw = Math.toRadians(sp.getYRot());
        FILM.put(sp.getId(), new double[] {Math.toRadians(angle), dist, height, lookY, ahead, follow, sp.getX(), sp.getY(), sp.getZ(), -Math.sin(yaw), Math.cos(yaw)});
    }

    /** 시험 영상 카메라를 지움 */
    public static void filmCamOff(ServerPlayer sp) {
        FILM.remove(sp.getId());
    }

    /** 그림의 앞면을 돌릴 이 사람의 눈: 시험 영상이면 영상 카메라 자리 (Probe.filmCamera 와 같은 셈), 아니면 몸의 눈 */
    static Vec3 eyeOf(ServerPlayer sp) {
        double[] c = FILM.get(sp.getId());
        if (c == null || !isThird(sp)) return sp.getEyePosition();
        double fx = c[9];
        double fz = c[10];
        double by = c[7] + (sp.getY() - c[7]) * 0.6;
        double ox = Math.cos(c[0]) * fx + Math.sin(c[0]) * fz;
        double oz = Math.cos(c[0]) * fz + Math.sin(c[0]) * -fx;
        return new Vec3(c[6] + (sp.getX() - c[6]) * c[5] + ox * c[1], by + c[2], c[8] + (sp.getZ() - c[8]) * c[5] + oz * c[1]);
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

    /** Fx3d 번호 → 띄운 카드 (옮기기 · 움직임 바꾸기 · 지우기 — Fx3d.move · anim · kill 도 모드 없는 클라이언트에게) */
    private static final Map<Integer, Mover> BY_ID = new HashMap<>();

    /**
     * Fx3d.spawn: 판 모델 (카드 목록에 있으면 — 휘두른 자국 slash_* · 2026-11-14 직업 스킬의 2D 물체 card_* · 입체 모델을 구운 판) 을 모드 없는 클라이언트에게.
     * 2026-11-14 주인 기준 (follow — 주인 둘레를 도는 검 · 앞의 방패) · 도는 길 (orbit) · 휘두름 (arc · 쥔 곳 grip) · 돌기 (spin) · 가는 쪽 (align) · 움직임 이름도 —
     * 전에는 이런 판을 통째로 버렸음 (헬름 브레이커의 머리 위 도끼 · 포비든 그리모어의 금서 · 책장 …). id = Fx3d 번호 (옮기기 · 움직임 · 지우기). false = 다리가 없음
     */
    public static boolean card(ServerLevel level, String model, Vec3 at, Opt o, List<ServerPlayer> plain, int id) {
        List<Layer[]> frames = PDefs.card(model);
        boolean flat = frames != null && !frames.isEmpty();
        // 2026-11-14 입체 효과 모델 (tools/vfx_solids.py — 부분마다 아이템 모델) · 무기 모델 (item:<무기> — 그 무기의 아이템 모델 · 주인의 무기 스킨)
        PDefs.SolidDef solid = flat ? null : PDefs.solid(model);
        boolean weapon = !flat && solid == null && model.startsWith("item:");
        if (!flat && solid == null && !weapon) {
            if (WARNED.add("card:" + model)) Mmorpg.LOG.info("[vfx] fx model {} has no vanilla card", model);
            return false;
        }
        int view = (int) o.num("view", 0);
        int owner = (int) o.num("owner", o.num("follow", 0));
        // 주인 기준: at 은 주인 기준 [오른쪽, 위, 앞] — 홀더 · 보는 사람은 주인 자리로
        net.minecraft.world.entity.Entity follow = null;
        Vec3 near = at;
        if (o.has("follow")) {
            follow = level.getEntity((int) o.num("follow", 0));
            if (follow == null) return true;
            near = follow.position();
        }
        List<ServerPlayer> who = new ArrayList<>(plain.size());
        for (ServerPlayer sp : plain) {
            if (sp.level() != level || sp.position().distanceToSqr(near) > RANGE * RANGE) continue;
            if (view != 0) {
                boolean ownerFp = sp.getId() == owner && !isThird(sp);
                if (view == 1 && ownerFp || view == 2 && !ownerFp) continue;
            }
            who.add(sp);
        }
        if (who.isEmpty()) return true;
        Batch b;
        if (flat) {
            if (live >= MAX_LIVE) {
                dropped++;
                return true;
            }
            b = new Batch(level, near, who);
            b.card = new Card(b, model, frames, at, o, follow);
            cards++;
        } else {
            int need = weapon ? 1 : solid.parts().size();
            if (solidLive + need > MAX_SOLID) {
                solidsDropped++;
                return true;
            }
            net.minecraft.world.item.ItemStack ws = null;
            if (weapon) {
                Identifier item = Identifier.tryParse(model.substring(5));
                ws = item == null ? null : mmorpg.fabric.vanilla.VanillaCosmetics.effectStack(item, owner == 0 ? null : level.getEntity(owner));
                if (ws == null) {
                    if (WARNED.add("card:" + model)) Mmorpg.LOG.info("[vfx] fx weapon model {} unknown", model);
                    return false;
                }
            }
            b = new Batch(level, near, who);
            b.card = new Solid(b, model, solid, ws, at, o, follow);
        }
        if (id != 0) {
            b.card.id = id;
            BY_ID.put(id, b.card);
        }
        b.attach();
        return true;
    }

    /** Fx3d.move · moveBack: 띄운 카드 · 입체 모델을 지금 그려지는 자리에서 to (세계 · 주인 기준이면 그 틀) 로 seconds 초에 */
    public static void cardMove(int id, Vec3 to, double seconds, String ease, boolean back) {
        Mover c = BY_ID.get(id);
        if (c == null || c.dead) return;
        c.moveTo(to, seconds, ease(ease), back);
    }

    /** Fx3d.anim: 움직임을 바꿈 (처음부터 · loop 면 되풀이) */
    public static void cardAnim(int id, String anim, boolean loop) {
        Mover c = BY_ID.get(id);
        if (c == null || c.dead) return;
        c.setAnim(anim, loop, Math.max(0, c.age / 20.0 - c.delay));
    }

    /** Fx3d.kill: fade 초 동안 흐려지며 지움 */
    public static void cardKill(int id, double fade) {
        Mover c = BY_ID.get(id);
        if (c == null || c.dead) return;
        c.kill(fade);
    }

    // ------------------------------------------------------------ 효과 묶음 (한 번 뿜은 것 — 세계의 한 점에 붙은 홀더)

    static final class Batch extends ElementHolder {
        final ServerLevel level;
        final Vec3 origin;
        final Set<Integer> who = new HashSet<>();
        final List<Part> parts = new ArrayList<>();
        Emitter emitter;
        /** 판 (Card) · 입체 모델 (Solid) */
        Mover card;
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
                Vec3 e = eyeOf(sp);
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

    // ------------------------------------------------------------ Fx3d 모델의 움직임 (판 Card · 입체 Solid 가 같이 씀)

    /** 지금 모습: 자리 · 방향 (오른손 틀 — mirror 면 모델 X 를 뒤집어 그림) · 크기 · 투명 (alpha · 사라짐 몫 fade) · 시간 · 보는 눈 쪽. lost = 주인을 잃음 */
    record Pose(boolean lost, Vec3 p, Quaternionf q, boolean mirror, double s, double a, double fade, double t, Vec3 toEye) {}

    private static final Pose LOST = new Pose(true, null, null, false, 0, 0, 0, 0, null);

    /**
     * Fx3d 모델 하나의 움직임 (client FxModels 와 같은 셈 — 2026-11-14 판 Card 에서 떼어 입체 Solid 도 씀): 곧게 · 곡선 길 · 도는 길 (orbit — 반지름이 늚 · 흔들림) ·
     * 주인 기준 (follow — 주인의 자리 · 보는 쪽 틀) · 옮기기 (Fx3d.move — 지금 자리에서) · 휘두름 (arc — 축 둘레로 dir · face) · 쥔 곳 (grip — 이 점을 중심으로 돎) ·
     * 돌기 (spin — 모델 안 z · 세계 위 up · x · y) · 가는 쪽 (align) · 축마다 늘임 (stretch) · 크기 (pop · grow · shrink) · 투명 (fadeIn · fadeOut) · 지우기 (Fx3d.kill)
     */
    abstract static class Mover {
        final Batch batch;
        final String model;
        final Vec3 at;
        final Vec3 dir0;
        final Vec3 face0;
        final double roll;
        final double spin;
        final int spinAxis;
        final Vec3 vel;
        final Vec3 acc;
        final Vec3 to;
        final Vec3 ctrl;
        final double travel;
        final int ease;
        final boolean orbit;
        final double orbitR;
        final double orbitW;
        final double orbitA;
        final double orbitGrow;
        final double bob;
        final double bobHz;
        final Vec3 arcAxis;
        final double arcDeg;
        final double arcTime;
        final int arcEase;
        final Vec3 grip;
        final Vec3 stretch;
        final net.minecraft.world.entity.Entity follow;
        Vec3 lastBase;
        double lastYaw;
        final boolean align;
        final double life;
        final double scale;
        final double grow;
        final double pop;
        final double shrink;
        final double alpha;
        final double fadeIn;
        final double fadeOut;
        final double delay;
        final boolean faceCam;
        /** 옆모습 판 (모델 JSON "upright" — client: +X 가 세계 아래쪽이면 좌우를 뒤집어 그림, 등이 늘 위) */
        final boolean upright;
        String animName = "";
        final double animSpeed;
        final double animPhase;
        boolean loop;
        double animT0;
        Vec3 moveFrom;
        Vec3 moveTo;
        int moveStart = -1;
        double moveDur;
        int moveEase;
        boolean moveBack;
        int killAt = -1;
        double killFade;
        int id;
        int age;
        boolean first = true;
        boolean dead;
        Vec3 pos;

        Mover(Batch batch, String model, Vec3 at, Opt o, net.minecraft.world.entity.Entity follow, boolean faceCam, boolean upright) {
            this.batch = batch;
            this.model = model;
            this.at = at;
            this.follow = follow;
            this.faceCam = faceCam;
            this.upright = upright;
            this.dir0 = o.vec("dir", new Vec3(0, 1, 0));
            this.face0 = o.vec("face", Vec3.ZERO);
            this.roll = o.num("roll", 0);
            this.spin = o.num("spin", 0);
            this.spinAxis = switch (o.str("spinAxis", "y")) {
                case "x" -> 1;
                case "z" -> 2;
                case "up" -> 3;
                default -> 0;
            };
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
            this.orbit = o.has("orbit");
            this.orbitR = o.num("orbit", 1);
            this.orbitW = o.num("orbitSpeed", 180);
            this.orbitA = o.num("orbitAngle", 0);
            this.orbitGrow = o.num("orbitGrow", 0);
            this.bob = o.num("bob", 0);
            this.bobHz = o.num("bobHz", 1);
            if (o.has("arc")) {
                arcAxis = o.vec("arcAxis", new Vec3(0, 1, 0));
                arcDeg = o.num("arc", 0);
                arcTime = Math.max(0.01, o.num("arcTime", life));
                arcEase = ease(o.str("arcEase", null));
            } else {
                arcAxis = null;
                arcDeg = 0;
                arcTime = 0;
                arcEase = 0;
            }
            this.grip = o.vec("grip", Vec3.ZERO);
            this.stretch = o.vec("stretch", new Vec3(1, 1, 1));
            this.align = o.truthy("align");
            this.scale = o.num("scale", 1);
            this.grow = o.num("grow", 0);
            this.pop = o.num("pop", 0);
            this.shrink = o.num("shrink", 0);
            this.alpha = o.num("alpha", 1);
            this.fadeIn = o.num("fadeIn", 0);
            this.fadeOut = o.num("fadeOut", 0.15);
            this.delay = o.num("delay", 0);
            this.animSpeed = o.num("animSpeed", 1);
            this.animPhase = o.num("animPhase", 0);
        }

        /** 움직임 이름을 바꿈 (t0 = 바꾼 때 — 태어난 뒤 초) */
        abstract void setAnim(String name, boolean loop, double t0);

        /** Fx3d.move · moveBack: 지금 그려지는 자리에서 to (세계 · 주인 기준이면 그 틀) 로 seconds 초에 */
        void moveTo(Vec3 to, double seconds, int ease, boolean back) {
            double[] p = posAt(age);
            moveFrom = new Vec3(p[0], p[1], p[2]);
            moveTo = to;
            moveStart = age;
            moveDur = Math.max(0.01, seconds);
            moveEase = ease;
            moveBack = back;
        }

        /** Fx3d.kill: fade 초 동안 흐려지며 지움 */
        void kill(double fade) {
            if (killAt >= 0) return;
            killAt = age;
            killFade = Math.max(0, fade);
        }

        /** 지금 모습을 엔티티에 */
        abstract void apply();

        abstract void remove();

        boolean step() {
            if (dead) return false;
            age++;
            double t = age / 20.0 - delay;
            if (t > life) return false;
            if (killAt >= 0 && (age - killAt) / 20.0 >= killFade) return false;
            apply();
            return true;
        }

        private double bobAt(double tf) {
            return bob != 0 ? bob * Math.sin(Math.PI * 2 * bobHz * tf) : 0;
        }

        /** t 초의 자리 · 가는 쪽 (주인 기준이면 그 틀 — client FxModels.localPos): {x, y, z, 가는 쪽 x, y, z} */
        double[] localPos(double t) {
            double tf = Math.max(0, t);
            if (orbit) {
                double r = orbitR + orbitGrow * tf;
                double a = Math.toRadians(orbitA + orbitW * tf);
                double b = bob != 0 ? bob * Math.sin(Math.PI * 2 * bobHz * tf + a) : 0;
                double sg = orbitW >= 0 ? 1 : -1;
                return new double[] {at.x + Math.cos(a) * r, at.y + b, at.z + Math.sin(a) * r, -Math.sin(a) * sg, 0, Math.cos(a) * sg};
            }
            if (to != null) {
                double k = easeK(ease, tf / travel);
                double a = (1 - k) * (1 - k);
                double b = 2 * (1 - k) * k;
                double c = k * k;
                return new double[] {a * at.x + b * ctrl.x + c * to.x, a * at.y + b * ctrl.y + c * to.y + bobAt(tf), a * at.z + b * ctrl.z + c * to.z,
                    2 * (1 - k) * (ctrl.x - at.x) + 2 * k * (to.x - ctrl.x), 2 * (1 - k) * (ctrl.y - at.y) + 2 * k * (to.y - ctrl.y),
                    2 * (1 - k) * (ctrl.z - at.z) + 2 * k * (to.z - ctrl.z)};
            }
            return new double[] {at.x + vel.x * tf + 0.5 * acc.x * tf * tf, at.y + vel.y * tf + 0.5 * acc.y * tf * tf + bobAt(tf),
                at.z + vel.z * tf + 0.5 * acc.z * tf * tf, vel.x + acc.x * tf, vel.y + acc.y * tf, vel.z + acc.z * tf};
        }

        /** 틱 ticks (태어난 뒤) 의 자리 — 옮기는 중이면 그 길 위 (client FxModels.posAt) */
        double[] posAt(int ticks) {
            double[] p = localPos(ticks / 20.0 - delay);
            if (moveStart < 0) return p;
            double k = Math.max(0, Math.min(1, (ticks - moveStart) / 20.0 / moveDur));
            double m = easeK(moveEase, k);
            double sg = moveBack ? -1 : 1;
            return new double[] {moveFrom.x + (moveTo.x - moveFrom.x) * m, moveFrom.y + (moveTo.y - moveFrom.y) * m, moveFrom.z + (moveTo.z - moveFrom.z) * m,
                sg * (moveTo.x - moveFrom.x), sg * (moveTo.y - moveFrom.y), sg * (moveTo.z - moveFrom.z)};
        }

        /**
         * 지금 (age) 의 모습 — client FxModels 와 같은 차례: 세계 위 둘레로 도는 카메라 판 (spin up + faceCam) → 휘두름 (arc) → 주인 틀 → Y = dir (또는 가는 쪽) ·
         * Z = face (또는 보는 사람) · X = Y × Z (옆모습 판이면 등이 위) → roll → 돌기 (spin). null = 아직 (delay)
         */
        Pose pose() {
            double t = age / 20.0 - delay;
            if (t < 0) return null;
            float tf = (float) t;
            double tn = Math.max(0, Math.min(1, t / Math.max(0.01, life)));
            double[] lp = posAt(age);
            // 주인 기준 [오른쪽, 위, 앞] → 세계 (주인이 사라지면 마지막 자리)
            double sy = 0;
            double cy = 1;
            Vec3 p;
            if (follow != null) {
                if (!follow.isRemoved()) {
                    lastBase = follow.position();
                    lastYaw = follow.getYRot();
                }
                if (lastBase == null) return LOST;
                double yr = Math.toRadians(lastYaw);
                sy = Math.sin(yr);
                cy = Math.cos(yr);
                p = new Vec3(lastBase.x - cy * lp[0] - sy * lp[2], lastBase.y + lp[1], lastBase.z - sy * lp[0] + cy * lp[2]);
            } else {
                p = new Vec3(lp[0], lp[1], lp[2]);
            }
            // 크기 · 투명
            double s = scale;
            if (pop > 0) s *= Math.max(0.01, easeK(5, t / pop));
            if (grow != 0) s *= 1 + grow * tn;
            if (shrink > 0) s *= Math.max(0, Math.min(1, (life - t) / shrink));
            double fade = 1;
            if (fadeIn > 0) fade *= Math.max(0, Math.min(1, t / fadeIn));
            if (fadeOut > 0) fade *= Math.max(0, Math.min(1, (life - t) / fadeOut));
            if (killAt >= 0) fade *= killFade <= 0 ? 0 : Math.max(0, 1 - (age - killAt) / 20.0 / killFade);
            // 방향
            Vector3f dir = new Vector3f((float) dir0.x, (float) dir0.y, (float) dir0.z);
            Vector3f face = new Vector3f((float) face0.x, (float) face0.y, (float) face0.z);
            Vector3f tan = new Vector3f((float) lp[3], (float) lp[4], (float) lp[5]);
            boolean camSpinUp = faceCam && spinAxis == 3 && spin != 0;
            if (camSpinUp) {
                float su = (float) Math.toRadians(spin * tf);
                Vector3f up = new Vector3f(0, 1, 0);
                rotateAbout(dir, up, su);
                rotateAbout(face, up, su);
            }
            if (arcTime > 0) {
                Vector3f ax = new Vector3f((float) arcAxis.x, (float) arcAxis.y, (float) arcAxis.z);
                if (ax.lengthSquared() > 1e-8F) {
                    ax.normalize();
                    float th = (float) Math.toRadians(arcDeg * easeK(arcEase, tf / arcTime));
                    rotateAbout(dir, ax, th);
                    rotateAbout(face, ax, th);
                }
            }
            if (follow != null) {
                dir = local(dir, sy, cy);
                face = local(face, sy, cy);
                tan = local(tan, sy, cy);
            }
            Vector3f Y = align && tan.lengthSquared() > 1e-6F ? new Vector3f(tan) : new Vector3f(dir);
            if (Y.lengthSquared() < 1e-8F) Y.set(0, 1, 0);
            Y.normalize();
            Vector3f Z;
            Vec3 toEye = batch.eye.subtract(p);
            if (faceCam) Z = new Vector3f((float) toEye.x, (float) toEye.y, (float) toEye.z);
            else if (face.lengthSquared() > 1e-6F) Z = new Vector3f(face);
            else Z = new Vector3f(0, 1, 0);
            Z.sub(new Vector3f(Y).mul(Z.dot(Y)));
            if (Z.lengthSquared() < 1e-6F) {
                Z.set(Math.abs(Y.y) < 0.9F ? 0 : 1, Math.abs(Y.y) < 0.9F ? 1 : 0, 0);
                Z.sub(new Vector3f(Y).mul(Z.dot(Y)));
            }
            Z.normalize();
            Vector3f X = new Vector3f(Y).cross(Z).normalize();
            Matrix3f R = new Matrix3f(X, Y, Z);
            if (upright && X.y < 0) R.m00(-R.m00()).m01(-R.m01()).m02(-R.m02()); // 첫 열 (X) 을 뒤집음 — 등 (+X) 이 늘 위
            if (roll != 0) R.rotateY((float) Math.toRadians(roll));
            float sp = camSpinUp ? 0F : (float) Math.toRadians(spin * tf);
            if (sp != 0) {
                switch (spinAxis) {
                    case 1 -> R.rotateX(sp);
                    case 2 -> R.rotateZ(sp);
                    case 3 -> R = new Matrix3f().rotationY(sp).mul(R);
                    default -> R.rotateY(sp);
                }
            }
            boolean mirror = R.determinant() < 0;
            if (mirror) R.m00(-R.m00()).m01(-R.m01()).m02(-R.m02()); // 오른손 틀 + 모델 X 뒤집기 (크기의 x 를 −)
            Quaternionf q = new Quaternionf().setFromNormalized(R);
            return new Pose(false, p, q, mirror, s, alpha * fade, fade, t, toEye);
        }

        /** 움직임의 자리 u (0 ~ 1 — 되풀이면 나머지) — client FxModels: ((t − 바꾼 때) × animSpeed + animPhase) / len */
        double animU(double t, double len) {
            double u = ((t - animT0) * animSpeed + animPhase) / Math.max(0.01, len);
            return loop ? u - Math.floor(u) : Math.max(0, Math.min(1, u));
        }
    }

    // ------------------------------------------------------------ 카드 (Fx3d 2D 판 — 휘두른 자국)

    /**
     * Fx3d 판 하나 (client FxModels 와 같은 자리 · 방향 · 크기 · 투명 · 칸 — 겹은 한 글줄에 겹쳐 그림: 몸 → 빛). 움직임은 {@link Mover} (모드의 셈 모두), 칸은 움직임 이름
     * (모델 JSON 의 anims — idle · open · grasp …)
     */
    static final class Card extends Mover {
        final List<Layer[]> frames;
        final TextDisplayElement e;
        final int[] color;
        PDefs.CardAnim animDef;
        int frame = -1;
        int opacity = -1;
        boolean hidden = true;

        Card(Batch batch, String model, List<Layer[]> frames, Vec3 at, Opt o, net.minecraft.world.entity.Entity follow) {
            // 구운 판 (입체 모델의 앞모습) 은 늘 보는 사람 쪽 — 옆에서 얇게 보이지 않게
            super(batch, model, at, o, follow, o.truthy("faceCam") || PDefs.bill(model), false);
            this.frames = frames;
            double[] c = o.color("color", new double[] {1, 1, 1});
            double[] g = o.color("glow", c);
            this.color = new int[] {rgb(c[0], c[1], c[2]), rgb(g[0], g[1], g[2]), 0xFFFFFF};
            setAnim(o.str("anim", ""), o.truthy("loop"), 0);
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

        @Override
        void setAnim(String name, boolean loop, double t0) {
            this.animName = name == null ? "" : name;
            this.animDef = PDefs.cardAnim(model, animName);
            this.loop = loop;
            this.animT0 = t0;
        }

        private void hide() {
            if (hidden) return;
            hidden = true;
            e.setInterpolationDuration(0);
            e.setScale(new Vector3f());
            e.startInterpolationIfDirty();
        }

        @Override
        void apply() {
            int dur = first ? 0 : 1;
            Pose ps = pose();
            if (ps == null) return;
            if (ps.lost()) {
                hide();
                return;
            }
            double t = ps.t();
            Vec3 p = ps.p();
            double s = ps.s();
            int o = (int) Math.round(Math.max(0, Math.min(1, ps.a())) * 255);
            // 칸: 모델의 움직임 (이름 — 구간마다 보이는 칸) · 이름만 있고 정의가 없으면 칸마다 같은 길이 (예전 play)
            int f = 0;
            if (animDef != null) {
                f = animDef.frameAt(animU(t, animDef.len()));
            } else if (!animName.isEmpty() && frames.size() > 1) {
                double u = (t - animT0) * animSpeed + animPhase;
                u = loop ? u - Math.floor(u) : Math.max(0, Math.min(1, u));
                f = Math.min(frames.size() - 1, (int) Math.floor(u * frames.size()));
            }
            if (pos == null || p.distanceToSqr(pos) > 1e-6) {
                pos = p;
                e.setTeleportDuration(dur);
                e.setOffset(p.subtract(batch.origin));
            }
            if (o < MIN_OPACITY || s <= 0.001 || f < 0 || f >= frames.size()) {
                hide();
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
                    text.append(Component.literal(new String(Character.toChars(l.glyph()))).withStyle(Style.EMPTY.withFont(font).withColor(color[Math.max(0,
                            Math.min(2, l.mat()))])));
                }
                e.setText(text);
            }
            if (Math.abs(o - opacity) > 2) {
                opacity = o;
                e.setTextOpacity((byte) Math.min(255, o));
            }
            Quaternionf q = new Quaternionf(ps.q());
            Vec3 toEye = ps.toEye();
            Vector3f n = q.transform(new Vector3f(0, 0, 1));
            if (n.x * toEye.x + n.y * toEye.y + n.z * toEye.z < 0) q.rotateY((float) Math.PI); // 글자 표시는 한쪽 면 — 앞면을 보는 사람 쪽으로
            Layer l0 = ls[ls.length - 1];
            double unit = PDefs.glyphH * TEXT_UNIT;
            float k = (float) (2 * l0.hh() * s / unit);
            float kx = (float) (k * stretch.x);
            float ky = (float) (k * stretch.y);
            // 글줄 너비 = 마지막 겹의 advance (겹은 같은 너비) — 그림 너비와의 차이만큼 가운데 맞춤 · 모델 자리 (판 가운데) − 쥔 곳
            double gw = PDefs.glyphH * (l0.hw() / Math.max(1e-6, l0.hh()));
            float cx = (float) ((l0.adv() - gw) / 2 * TEXT_UNIT * kx);
            Vector3f tr = q.transform(new Vector3f(cx + (float) ((l0.x() - grip.x) * s * stretch.x), (float) ((l0.y() - grip.y) * s * stretch.y),
                    (float) ((l0.z() - grip.z) * s * stretch.z)));
            e.setInterpolationDuration(wasHidden ? 0 : dur);
            e.setTranslation(tr);
            e.setLeftRotation(q);
            e.setScale(new Vector3f(kx, ky, 1));
            e.setRightRotation(new Quaternionf());
            e.startInterpolationIfDirty();
        }

        @Override
        void remove() {
            if (dead) return;
            dead = true;
            live--;
            if (id != 0 && BY_ID.get(id) == this) BY_ID.remove(id);
            batch.removeElement(e);
        }
    }

    // ------------------------------------------------------------ 2026-11-14 입체 효과 모델 (Fx3d 의 면 모델 · 무기 모델 — 아이템 표시)

    /** 떠 있는 입체 부분 (아이템 표시) 수 · 한도 */
    static int solidLive;
    static final int MAX_SOLID = 260;
    /** 시험용 셈: 띄운 입체 모델 · 무기 모델 · 버린 것 */
    public static int solidsMade;
    public static int weaponsMade;
    public static int solidsDropped;
    /** 아이템 표시가 모델을 Y 둘레로 180° 돌려 그리는 것을 되돌림 (Vfx.ITEM_Y_FLIP) */
    private static final Quaternionf Y180 = new Quaternionf().rotationY((float) Math.PI);
    /** 무기 모델 (item:) 을 효과 모델의 축에 맞춤 (client FxModels.ITEM_TURN — Y 축 −90°: 넓은 면 ±Z · 치는 쪽 +X) */
    private static final Quaternionf ITEM_TURN = new Quaternionf().rotationY((float) (-Math.PI / 2));

    /**
     * 입체 효과 모델 하나 (모드 없는 클라이언트 — tools/vfx_solids.py 가 부분마다 옮긴 아이템 모델 rpg:fx3d/*, 무기 모델 item:&lt;무기&gt; 는 그 무기의 아이템 모델 ·
     * 주인의 무기 스킨): 부분마다 아이템 표시 하나를 모드 FxModels 와 같은 셈 (움직임 {@link Mover} · 부분 움직임 T(pivot + pos) · Rz · Ry · Rx · S · T(−pivot)) 으로
     * 틱마다 옮김. 색 = 아이템 정의의 물들임 (custom_model_data 색 0 빛깔 · 1 속빛). 투명은 없음 — 사라질 때는 60% 까지 작아졌다 감춤 (모드 무기 모델과 같게).
     * 빛 무리 (glow — 모드는 더하는 빛) 는 밝은 속빛일 때 옅은 빛 판 (rfx_bloom — 늘 보는 사람 쪽) 으로
     */
    static final class Solid extends Mover {
        final PDefs.SolidDef def;
        final boolean weapon;
        final eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement[] els;
        final PDefs.SolidPart[] parts;
        final List<Card> halos = new ArrayList<>();
        PDefs.SolidAnim animDef;
        final boolean[] shown;

        Solid(Batch batch, String model, PDefs.SolidDef def, net.minecraft.world.item.ItemStack weaponStack, Vec3 at, Opt o,
                net.minecraft.world.entity.Entity follow) {
            super(batch, model, at, o, follow, o.truthy("faceCam"), def != null && def.upright());
            this.def = def;
            this.weapon = weaponStack != null;
            double[] c = o.color("color", new double[] {1, 1, 1});
            double[] g = o.color("glow", c);
            List<Integer> tint = List.of(rgb(c[0], c[1], c[2]), rgb(g[0], g[1], g[2]));
            List<PDefs.SolidPart> ps = new ArrayList<>();
            if (def != null) for (PDefs.SolidPart sp : def.parts()) if (sp.item() != null) ps.add(sp);
            this.parts = ps.toArray(new PDefs.SolidPart[0]);
            int n = weapon ? 1 : parts.length;
            this.els = new eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement[n];
            this.shown = new boolean[n];
            for (int i = 0; i < n; i++) {
                net.minecraft.world.item.ItemStack stack;
                if (weapon) {
                    stack = weaponStack;
                } else {
                    stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER);
                    stack.set(net.minecraft.core.component.DataComponents.ITEM_MODEL, Identifier.parse(parts[i].item()));
                    stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA, new net.minecraft.world.item.component.CustomModelData(List.of(), List.of(),
                            List.of(), tint));
                }
                var el = new eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement(stack);
                el.setItemDisplayContext(net.minecraft.world.item.ItemDisplayContext.NONE);
                el.setBrightness(Brightness.FULL_BRIGHT);
                el.setViewRange(1.5F);
                el.setShadowRadius(0F);
                el.setScale(new Vector3f());
                els[i] = el;
            }
            setAnim(o.str("anim", ""), o.truthy("loop"), 0);
            solidLive += n;
            if (weapon) weaponsMade++;
            else solidsMade++;
            apply();
            for (var el : els) batch.addElement(el);
            // 빛 무리: 밝은 속빛일 때만 (섞는 빛 — 어두운 색은 회색 원판으로 보임). 2026-11-14 모델 뒤 (보는 사람에게서 반지름만큼 — 판의 z 는 눈 쪽) · 더 옅게:
            // 영상 (CI 43577665879) 에서 앞에 그린 빛 판이 태양 · 달 원판을 하얗게 덮었음 — 뒤에 두면 모델 둘레로만 번짐
            double ga = (weapon ? o.num("glowAlpha", 0.4) : o.num("glowAlpha", 0.55)) * Math.min(1, alpha);
            double bright = Math.max(g[0], Math.max(g[1], g[2]));
            if (PDefs.haloGlyph >= 0 && ga > 0.05 && bright >= 0.55) {
                float[][] glow = weapon ? new float[][] {{0, 0, 0, (float) o.num("glowR", 0)}} : def.glow();
                for (float[] gp : glow) {
                    if (gp.length < 4 || gp[3] <= 0.01F) continue;
                    float back = Math.max(gp[3], 0.5F);
                    List<Layer[]> fr = List.<Layer[]>of(new Layer[] {new Layer(PDefs.haloGlyph, 1, gp[0], gp[1], gp[2] - back, gp[3], gp[3], PDefs.haloAdv)});
                    Card h = new Card(batch, "__halo", fr, at, o.with("faceCam", 1.0).with("alpha", ga * 0.45).with("anim", null), follow);
                    halos.add(h);
                }
            }
            first = false;
        }

        @Override
        void setAnim(String name, boolean loop, double t0) {
            this.animName = name == null ? "" : name;
            this.animDef = def == null || animName.isEmpty() ? null : def.anims().get(animName);
            this.loop = loop;
            this.animT0 = t0;
        }

        @Override
        void moveTo(Vec3 to, double seconds, int ease, boolean back) {
            super.moveTo(to, seconds, ease, back);
            for (Card h : halos) h.moveTo(to, seconds, ease, back);
        }

        @Override
        void kill(double fade) {
            super.kill(fade);
            for (Card h : halos) h.kill(fade);
        }

        @Override
        boolean step() {
            boolean alive = super.step();
            for (Iterator<Card> it = halos.iterator(); it.hasNext(); ) {
                Card h = it.next();
                if (!alive || !h.step()) {
                    h.remove();
                    it.remove();
                }
            }
            return alive;
        }

        private void hide(int i) {
            if (!shown[i] && !first) return;
            shown[i] = false;
            els[i].setInterpolationDuration(0);
            els[i].setScale(new Vector3f());
            els[i].startInterpolationIfDirty();
        }

        @Override
        void apply() {
            int dur = first ? 0 : 1;
            Pose ps = pose();
            if (ps == null) return;
            if (ps.lost()) {
                for (int i = 0; i < els.length; i++) hide(i);
                return;
            }
            Vec3 p = ps.p();
            if (pos == null || p.distanceToSqr(pos) > 1e-6) {
                pos = p;
                for (var el : els) {
                    el.setTeleportDuration(dur);
                    el.setOffset(p.subtract(batch.origin));
                }
            }
            // 사라짐: 투명 대신 60% 까지 작아졌다가 감춤 (client 무기 모델) — 모델의 alpha (옅게 그린 것) 는 모드 없는 클라이언트에서 그대로 (불투명)
            double f = ps.fade();
            if (f < 0.3 || ps.s() <= 0.001) {
                for (int i = 0; i < els.length; i++) hide(i);
                return;
            }
            double ks = f >= 0.65 ? 1 : 0.6 + 0.4 * Math.max(0, Math.min(1, (f - 0.3) / 0.35));
            double s = ps.s() * ks;
            Quaternionf q = ps.q();
            float mx = ps.mirror() ? -1F : 1F;
            float sx = (float) (s * stretch.x) * mx;
            float sy = (float) (s * stretch.y);
            float sz = (float) (s * stretch.z);
            if (weapon) {
                // client: T(d) · R · S(s · ks) · 늘임 · T(−grip) · ITEM_TURN → 아이템 (가운데 = 원점)
                Vector3f tr = q.transform(new Vector3f((float) -grip.x * sx, (float) -grip.y * sy, (float) -grip.z * sz));
                show(0, dur, tr, q, new Vector3f(sx, sy, sz), new Quaternionf(ITEM_TURN).mul(Y180));
                return;
            }
            double t = ps.t();
            double u = animDef == null ? 0 : animU(t, animDef.len());
            for (int i = 0; i < parts.length; i++) {
                PDefs.SolidPart pt = parts[i];
                PDefs.Track tk = animDef == null ? null : animDef.tracks().get(pt.name());
                float[] rot = tk == null ? null : sample(tk.rot(), (float) u, 3);
                float[] pp = tk == null ? null : sample(tk.pos(), (float) u, 3);
                float[] sc = tk == null ? null : sample(tk.scale(), (float) u, 1);
                if (sc != null && sc[0] <= 0.002F) {
                    hide(i);
                    continue;
                }
                float scl = sc == null ? 1F : Math.max(0.001F, sc[0]);
                Quaternionf rp = new Quaternionf();
                if (rot != null) rp.rotateZ((float) Math.toRadians(rot[2])).rotateY((float) Math.toRadians(rot[1])).rotateX((float) Math.toRadians(rot[0]));
                // 부분 가운데 (아이템 모델의 (8, 8, 8)) 의 모델 자리: pivot + pos + Rp · sc · (c − pivot)
                Vector3f cc = rp.transform(new Vector3f(pt.cx() - pt.px(), pt.cy() - pt.py(), pt.cz() - pt.pz())).mul(scl);
                cc.add(pt.px() + (pp == null ? 0 : pp[0]), pt.py() + (pp == null ? 0 : pp[1]), pt.pz() + (pp == null ? 0 : pp[2]));
                // 늘임 · 크기 · 쥔 곳 → 세계 방향 (모델 X 뒤집기는 크기의 x)
                Vector3f tr = q.transform(new Vector3f((float) ((cc.x - grip.x) * sx), (float) ((cc.y - grip.y) * sy), (float) ((cc.z - grip.z) * sz)));
                // 아이템 표시는 모델 칸 m 을 (m − 8) / 16 블록에 그림 → 모델 자리 c + (m − 8) / k 가 되게 16 / k 배 (× s · 늘임 · sc)
                float kk = scl * 16F / Math.max(0.01F, pt.k());
                show(i, dur, tr, q, new Vector3f(sx * kk, sy * kk, sz * kk), new Quaternionf(rp).mul(Y180));
            }
        }

        private void show(int i, int dur, Vector3f tr, Quaternionf left, Vector3f scale, Quaternionf right) {
            boolean wasHidden = !shown[i];
            shown[i] = true;
            var el = els[i];
            el.setInterpolationDuration(wasHidden ? 0 : dur);
            el.setTranslation(tr);
            el.setLeftRotation(left);
            el.setScale(scale);
            el.setRightRotation(right);
            el.startInterpolationIfDirty();
        }

        @Override
        void remove() {
            if (dead) return;
            dead = true;
            solidLive -= els.length;
            if (id != 0 && BY_ID.get(id) == this) BY_ID.remove(id);
            for (var el : els) batch.removeElement(el);
            for (Card h : halos) h.remove();
            halos.clear();
        }
    }

    /** 열쇠들 사이 (t 오름 — 첫 값은 t, 나머지는 값) — 부드럽게 (smoothstep · client FxModels.sample) */
    static float[] sample(float[][] keys, float u, int n) {
        if (keys == null || keys.length == 0) return null;
        float[] out = new float[n];
        if (u <= keys[0][0]) {
            System.arraycopy(keys[0], 1, out, 0, n);
            return out;
        }
        for (int i = 0; i + 1 < keys.length; i++) {
            float[] a = keys[i];
            float[] b = keys[i + 1];
            if (u <= b[0]) {
                float k = (u - a[0]) / Math.max(1e-5F, b[0] - a[0]);
                k = k * k * (3 - 2 * k);
                for (int j = 0; j < n; j++) out[j] = a[j + 1] + (b[j + 1] - a[j + 1]) * k;
                return out;
            }
        }
        System.arraycopy(keys[keys.length - 1], 1, out, 0, n);
        return out;
    }

    /** 주인 기준 [오른쪽, 위, 앞] → 세계 (보는 쪽 sin · cos — client FxModels.local) */
    static Vector3f local(Vector3f l, double sy, double cy) {
        return new Vector3f((float) (-cy * l.x - sy * l.z), l.y, (float) (-sy * l.x + cy * l.z));
    }

    /** v 를 축 ax (단위) 둘레로 th 라디안 (로드리게스 — client FxModels.rotateAbout) */
    static void rotateAbout(Vector3f v, Vector3f ax, float th) {
        if (v.lengthSquared() < 1e-10F) return;
        float c = (float) Math.cos(th);
        float s = (float) Math.sin(th);
        float d = v.dot(ax);
        float cx = ax.y * v.z - ax.z * v.y;
        float cy = ax.z * v.x - ax.x * v.z;
        float cz = ax.x * v.y - ax.y * v.x;
        v.set(v.x * c + cx * s + ax.x * d * (1 - c), v.y * c + cy * s + ax.y * d * (1 - c), v.z * c + cz * s + ax.z * d * (1 - c));
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
