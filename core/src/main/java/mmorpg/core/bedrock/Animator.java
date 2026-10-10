package mmorpg.core.bedrock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mmorpg.core.Molang;

/**
 * 엔티티 종류 하나의 동작 계산 (클라이언트 엔티티 설명의 animations · scripts.animate + 스크립트가 튼 동작 playAnimation).
 * 뼈마다 모든 동작의 회전 · 위치를 더하고 (가중치 = animate 의 식, 없으면 1) 크기는 곱함 — 베드락과 같음.
 * animate 의 동작 시간 = 엔티티가 나타난 뒤 지난 초 (q.life_time — 식 가중치가 0 이어도 시간은 흐름), 튼 동작 = 튼 뒤 지난 초.
 * 튼 동작은 멈춤 식 (기본 q.any_animation_finished — 한 번 끝까지 돌면 멈춤, "0" = 다음 동작이 올 때까지) 이 참이면 더하지 않음.
 */
public final class Animator {
    public final Geo geo;
    public final Ctx ctx;
    /** 동작 전체 이름 (animation.rpg.boss.surtr.slam) → 동작 (엔티티 설명에 있는 것 + 튼 동작을 처음 쓸 때 더함) */
    private final Map<String, Anim> anims;
    /** 모든 동작 JSON (엔티티 설명에 없는 동작도 playAnimation 으로 틀 수 있음 — 베드락과 같음) */
    private final Map<String, Object> allAnims;
    /** 짧은 이름 (slam) → 전체 이름 */
    private final Map<String, String> shortNames;
    private final List<Layer> base;
    private final Map<String, Molang.Expr> stops = new HashMap<>();
    /** 동작마다 채널 뼈 번호 (geo.bones 순서, 모델에 없는 뼈 = -1) */
    private final Map<Anim, int[]> trackBones = new HashMap<>();
    private final Map<Anim, Anim.Track[]> tracks = new HashMap<>();

    private record Layer(Anim anim, Molang.Expr weight) {}

    /** 실행 중인 튼 동작: 전체 이름 · 튼 뒤 지난 초 · 멈춤 식 (null = 기본) */
    public record Playing(String anim, double time, String stop) {}

    /** 뼈마다 더한 자세 (뼈 번호 × 3: 회전 도 · 위치 픽셀 (베드락 좌표) · 크기 배수) */
    public static final class Pose {
        public final double[] rot;
        public final double[] pos;
        public final double[] scale;

        public Pose(int bones) {
            rot = new double[bones * 3];
            pos = new double[bones * 3];
            scale = new double[bones * 3];
            reset();
        }

        public void reset() {
            Arrays.fill(rot, 0);
            Arrays.fill(pos, 0);
            Arrays.fill(scale, 1);
        }
    }

    /**
     * entity = 클라이언트 엔티티 설명 (identifier · animations · scripts …), allAnims = 동작 전체 이름 → 동작 JSON, props = 엔티티 속성 이름
     */
    @SuppressWarnings("unchecked")
    public Animator(Geo geo, Map<String, Object> entity, Map<String, Object> allAnims, List<String> props) {
        this.geo = geo;
        this.ctx = new Ctx(props);
        Map<String, Anim> a = new LinkedHashMap<>();
        Map<String, String> shorts = new LinkedHashMap<>();
        if (entity.get("animations") instanceof Map<?, ?> am) {
            for (Map.Entry<?, ?> e : am.entrySet()) {
                String full = String.valueOf(e.getValue());
                Object json = allAnims.get(full);
                if (!(json instanceof Map<?, ?>)) continue;
                Anim parsed = Anim.parse(full, (Map<String, Object>) json, ctx);
                a.put(full, parsed);
                shorts.put(String.valueOf(e.getKey()), full);
            }
        }
        this.anims = a;
        this.allAnims = allAnims;
        this.shortNames = Collections.unmodifiableMap(shorts);
        List<Layer> layers = new ArrayList<>();
        if (entity.get("scripts") instanceof Map<?, ?> sm && sm.get("animate") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String s) {
                    Anim an = byShort(s);
                    if (an != null) layers.add(new Layer(an, null));
                } else if (o instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        Anim an = byShort(String.valueOf(e.getKey()));
                        if (an != null) layers.add(new Layer(an, Molang.of(e.getValue(), ctx)));
                    }
                }
            }
        }
        this.base = Collections.unmodifiableList(layers);
        for (Anim an : a.values()) index(an);
    }

    private void index(Anim an) {
        int[] idx = new int[an.bones.size()];
        Anim.Track[] tr = new Anim.Track[an.bones.size()];
        int i = 0;
        for (Map.Entry<String, Anim.Track> e : an.bones.entrySet()) {
            Geo.Bone b = geo.bone(e.getKey());
            idx[i] = b == null ? -1 : b.index;
            tr[i] = e.getValue();
            i++;
        }
        trackBones.put(an, idx);
        tracks.put(an, tr);
    }

    private Anim byShort(String s) {
        String full = shortNames.get(s);
        return full == null ? null : anims.get(full);
    }

    /** 전체 이름 (또는 짧은 이름) → 동작 (없으면 null) */
    @SuppressWarnings("unchecked")
    public Anim anim(String name) {
        if (name == null) return null;
        Anim a = anims.get(name);
        if (a != null) return a;
        a = byShort(name);
        if (a != null) return a;
        if (allAnims != null && allAnims.get(name) instanceof Map<?, ?> json) {
            a = Anim.parse(name, (Map<String, Object>) json, ctx);
            anims.put(name, a);
            index(a);
        }
        return a;
    }

    /** 엔티티 설명에 있는 동작들 */
    public Map<String, Anim> anims() {
        return Collections.unmodifiableMap(anims);
    }

    /** 멈춤 식 (글자 → 식, 한 번만 읽음) */
    public Molang.Expr stopExpr(String src) {
        String s = src == null || src.isBlank() ? "q.any_animation_finished" : src;
        return stops.computeIfAbsent(s, k -> {
            try {
                return Molang.parse(k, ctx);
            } catch (RuntimeException e) {
                return Molang.constant(0);
            }
        });
    }

    /** 튼 동작이 멈췄는지 (v = 값 칸 — ANIM_FINISHED 를 바꿈) */
    public boolean stopped(Playing p, double[] v) {
        Anim a = anim(p.anim());
        if (a == null) return true;
        v[Ctx.ANIM_FINISHED] = a.finished(p.time()) ? 1 : 0;
        boolean s = stopExpr(p.stop()).eval(v) != 0;
        v[Ctx.ANIM_FINISHED] = 0;
        return s;
    }

    /** 자세 계산: v = 값 칸 (LIFE_TIME · 걸음 · 속성 … 을 채운 것), playing = 튼 동작 (멈춘 것은 빼고) → out */
    public void apply(double[] v, List<Playing> playing, Pose out) {
        out.reset();
        double life = v[Ctx.LIFE_TIME];
        double[] tmp = new double[3];
        for (Layer l : base) {
            double w = l.weight() == null ? 1 : l.weight().eval(v);
            if (w == 0) continue;
            add(l.anim(), life, w, v, out, tmp);
        }
        if (playing != null) {
            for (Playing p : playing) {
                Anim a = anim(p.anim());
                if (a == null || stopped(p, v)) continue;
                add(a, p.time(), 1, v, out, tmp);
            }
        }
        v[Ctx.ANIM_TIME] = 0;
    }

    private void add(Anim a, double time, double w, double[] v, Pose out, double[] tmp) {
        double t = a.localTime(time);
        if (t < 0) return;
        v[Ctx.ANIM_TIME] = t;
        int[] idx = trackBones.get(a);
        Anim.Track[] tr = tracks.get(a);
        for (int i = 0; i < idx.length; i++) {
            int b = idx[i];
            if (b < 0) continue;
            Anim.Track k = tr[i];
            if (k.rotation != null) {
                k.rotation.sample(t, v, tmp);
                for (int j = 0; j < 3; j++) out.rot[b * 3 + j] += w * tmp[j];
            }
            if (k.position != null) {
                k.position.sample(t, v, tmp);
                for (int j = 0; j < 3; j++) out.pos[b * 3 + j] += w * tmp[j];
            }
            if (k.scale != null) {
                k.scale.sample(t, v, tmp);
                for (int j = 0; j < 3; j++) out.scale[b * 3 + j] *= 1 + w * (tmp[j] - 1);
            }
        }
    }
}
