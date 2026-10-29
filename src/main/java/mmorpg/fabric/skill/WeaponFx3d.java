package mmorpg.fabric.skill;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import mmorpg.core.Weapon;
import mmorpg.fabric.Fx3d;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Tasks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 0.48.0 무기 스킬의 입체 효과 물체 (사용자 "효과 물체를 입체 모델로" — 무기 스킬 차례): Fx3d 로
 * <ul>
 *   <li>효과 모델 (tools/fx_models.py — 까마귀 · 오딘의 눈 · 작은 태양 · 세계수 · 불꽃 날개 …) 을 무기 색 (빛 · 심지) 으로</li>
 *   <li>무기 모델 (item:&lt;무기 ID&gt; — 사용자 무기 디자인의 아이템 3D 모델 그대로, 주인의 무기 스킨): 던진 묠니르 · 궁니르 · 게 불그, 떨어지는 거대한
 *       궁니르 · 크로노스의 낫 … 크기 · 끝 자리는 mmorpg/weapon_bounds.json (tools/weapon_bounds.py)</li>
 * </ul>
 * 무기 모델 좌표 (클라이언트가 효과 모델의 축에 맞춰 돌림): +Y = 무기 끝 (칼끝 · 창끝 · 망치 머리), ±Z = 넓은 면 (face · faceCam 이 보이는 면),
 * +X = 치는 쪽 (날 · 도끼날 · 낫날 · 망치 면 — 휘두를 때 앞서 감), 가운데 (0, 0, 0) = 모델 상자 가운데. 손에 든 것과 같은 크기 = hand 배.
 * 도끼 · 낫처럼 한쪽만 날이면 faceCam 대신 face 를 정해 줌 (faceCam 은 보는 쪽에 따라 +X 가 뒤집힘).
 */
final class WeaponFx3d {
    private WeaponFx3d() {}

    /** 무기 모델의 크기 (아이템을 자세 없이 그릴 때의 칸): 상자 · 손에 든 크기 배율 */
    record Bounds(Vec3 min, Vec3 max, double hand) {
        /** 무기 끝 (+Y 쪽 끝 — 가운데 줄) */
        Vec3 tip() {
            return new Vec3(0, max.y, 0);
        }

        /** 자루 끝 (−Y 쪽 끝) */
        Vec3 butt() {
            return new Vec3(0, min.y, 0);
        }

        double length() {
            return max.y - min.y;
        }
    }

    private static final Bounds DEFAULT = new Bounds(new Vec3(-0.15, -1.4, -0.4), new Vec3(0.15, 1.4, 0.4), 1.0);
    private static Map<String, Bounds> table;

    /** 무기 모델의 크기 (표에 없으면 보통 크기) */
    static Bounds bounds(Weapon w) {
        if (table == null) table = load();
        Bounds b = w == null ? null : table.get(w.id());
        return b != null ? b : DEFAULT;
    }

    private static Map<String, Bounds> load() {
        Map<String, Bounds> out = new HashMap<>();
        try (InputStream in = WeaponFx3d.class.getResourceAsStream("/mmorpg/weapon_bounds.json")) {
            if (in == null) throw new IllegalStateException("missing mmorpg/weapon_bounds.json");
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("weapons");
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                JsonObject b = e.getValue().getAsJsonObject();
                out.put(e.getKey(), new Bounds(vec(b, "min"), vec(b, "max"), b.has("hand") ? b.get("hand").getAsDouble() : 1.0));
            }
        } catch (Exception e) {
            Mmorpg.LOG.warn("[mmorpg] weapon bounds: {}", e.toString());
        }
        return out;
    }

    private static Vec3 vec(JsonObject o, String k) {
        var a = o.getAsJsonArray(k);
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }

    /** 무기 모델 이름 (item:rpg:hammer_l40_transcend …) */
    static String item(Weapon w) {
        return "item:" + w.id();
    }

    /** 무기 색 (빛 = 몸 · 심지 = 속빛 · 빛 무리) */
    static Opt look(Weapon w) {
        double[][] cs = Kit.colors(w);
        return Opt.of("color", cs[0], "glow", cs[1]);
    }

    /** a 에 b 를 덮어씀 */
    static Opt merge(Opt a, Opt b) {
        Opt r = a;
        for (String k : b.keys()) r = r.with(k, b.get(k));
        return r;
    }

    /** 효과 모델 하나 (무기 색 — o 가 덮음): 번호 */
    static int fx(ServerLevel level, String model, Vec3 at, Weapon w, Opt o) {
        return Fx3d.spawn(level, model, at, merge(look(w), o));
    }

    /** 효과 모델 하나 (색을 정함): 번호 */
    static int fx(ServerLevel level, String model, Vec3 at, double[] color, double[] glow, Opt o) {
        return Fx3d.spawn(level, model, at, merge(Opt.of("color", color, "glow", glow), o));
    }

    /**
     * 무기 모델 하나 (주인 sp 의 무기 스킨): size = 손에 든 크기의 배 (o 의 "size" — 없으면 1), 가운데 빛 무리 (glowR — 길이의 0.32, glowAlpha 0.4).
     * o 의 grip 은 모델 칸 그대로 (Bounds.tip · butt 를 쓰면 끝 · 자루 끝이 at 에). 번호
     */
    static int weapon(ServerLevel level, ServerPlayer sp, Weapon w, Vec3 at, Opt o) {
        Bounds b = bounds(w);
        double scale = b.hand() * o.num("size", 1);
        Opt base = merge(look(w), Opt.of("owner", (double) sp.getId(), "scale", scale, "glowR", b.length() * 0.32, "glowAlpha", 0.4, "fadeOut", 0.1));
        return Fx3d.spawn(level, item(w), at, merge(base, o.with("size", null)));
    }

    /** 무기 모델의 크기 배 (손에 든 크기 × size) */
    static double weaponScale(Weapon w, double size) {
        return bounds(w).hand() * size;
    }

    /**
     * 곧게 날아감: 모델 끝 (tip 모델 칸 × scale) 이 secs 초에 to 에 닿고 hold 초 머무름 (가는 쪽을 봄). 번호
     */
    static int fly(ServerLevel level, String model, Vec3 from, Vec3 to, double tip, double scale, double secs, double hold, Opt o) {
        Vec3 d = V.sub(to, from);
        double len = V.len(d);
        Vec3 u = len < 1e-3 ? V.UP : V.scale(d, 1 / len);
        Vec3 end = V.add(to, u, -tip * scale);
        Vec3 start = V.add(from, u, -Math.min(tip * scale, len * 0.5));
        double travel = Math.max(0.05, secs);
        Opt b = Opt.of("to", end, "ctrl", o.vec("ctrl", V.lerp(start, end, 0.5)), "travel", travel, "life", travel + Math.max(0, hold), "align", 1.0, "dir", u,
                "scale", scale, "fadeIn", 0.04, "fadeOut", 0.1, "faceCam", 1.0, "glowAlpha", 0.45, "shine", 0.15);
        return Fx3d.spawn(level, model, start, merge(b, o.with("ctrl", null)));
    }

    /**
     * 휘두름: 쥔 곳 (grip — 모델 칸) 을 pivot 에 두고 모델 +Y 를 up → sweep 평면에서 a0 → a1 도 (0 = up, 90 = sweep) 로 secs 초에 돌리고 끝 각에서 hold 초
     * 머무름 (넓은 면이 보는 사람 쪽 — faceCam). 번호
     */
    static int swing(ServerLevel level, String model, Vec3 pivot, Vec3 up, Vec3 sweep, double a0, double a1, double secs, double hold, double scale, Vec3 grip,
            Opt o) {
        Vec3 axis = V.unit(V.cross(up, sweep));
        double r0 = Math.toRadians(a0);
        Vec3 d0 = V.add(V.scale(up, Math.cos(r0)), sweep, Math.sin(r0));
        double t = Math.max(0.05, secs);
        double h = Math.max(0, hold);
        Opt b = Opt.of("dir", d0, "face", V.unit(V.cross(sweep, up)), "arcAxis", axis, "arc", a1 - a0, "arcTime", t, "arcEase", "in", "grip", grip, "faceCam", 1.0,
                "scale", scale, "life", t + h, "fadeIn", 0.03, "fadeOut", Math.max(0.05, h * 0.8), "glowAlpha", 0.5, "shine", 0.25);
        return Fx3d.spawn(level, model, pivot, merge(b, o));
    }

    /**
     * 무기 모델 휘두름 (사용자 무기 디자인): 자루 끝 (쥔 곳) 을 pivot 에 두고 무기 끝을 d0 → d1 방향으로 (큰 원 — 짧은 쪽) secs 초에 돌리고 hold 초 머무름.
     * 치는 쪽 (+X — 날 · 도끼날) 이 앞서 감. faceCam 이면 넓은 면이 보는 사람 쪽 (칼 · 망치처럼 양쪽이 같은 무기), 아니면 휘두르는 면에 수직 (도끼 · 낫). size = 손에 든 크기의 배
     */
    static int swingItem(ServerLevel level, ServerPlayer sp, Weapon w, Vec3 pivot, Vec3 d0, Vec3 d1, double secs, double hold, double size, boolean faceCam,
            Opt o) {
        Bounds b = bounds(w);
        Vec3 u = V.unit(d0);
        Vec3 e = V.unit(d1);
        double c = Math.max(-1, Math.min(1, V.dot(u, e)));
        Vec3 s = V.sub(e, V.scale(u, c));
        if (V.len(s) < 1e-4) s = V.rightOf(V.flat(u).lengthSqr() > 1e-6 ? V.unit(V.flat(u)) : new Vec3(0, 0, 1));
        s = V.unit(s);
        double ang = Math.toDegrees(Math.acos(c));
        Opt base = merge(look(w), Opt.of("owner", (double) sp.getId(), "glowR", b.length() * 0.3, "glowAlpha", 0.45, "faceCam", faceCam ? 1.0 : 0.0));
        return swing(level, item(w), pivot, u, s, 0, ang, secs, hold, b.hand() * size, b.butt(), merge(base, o));
    }

    /**
     * 무기 모델이 곧게 날아감 (던진 창 · 떨어지는 거대한 검): 무기 끝이 secs 초에 to 에 닿고 hold 초 머무름 (끝이 가는 쪽 — align). size = 손에 든 크기의 배,
     * spin (도/초 — 망치처럼 앞으로 구르며) 은 o 로
     */
    static int flyItem(ServerLevel level, ServerPlayer sp, Weapon w, Vec3 from, Vec3 to, double secs, double hold, double size, Opt o) {
        Bounds b = bounds(w);
        double scale = b.hand() * size;
        Opt base = merge(look(w), Opt.of("owner", (double) sp.getId(), "glowR", b.length() * 0.3, "glowAlpha", 0.45, "shine", 0.0));
        return fly(level, item(w), from, to, b.max().y, scale, secs, hold, merge(base, o));
    }

    /**
     * 무기 모델이 몸을 가르며 지나감 (빛이 된 칼날 — 섬광 난무의 한 번 벰): 보는 쪽 view 에 수직인 면에서, 화면 기울기 angle 도 (0 = 위에서 아래로 곧게,
     * + = 오른쪽 위 → 왼쪽 아래) 로 center 를 지나도록 쥔 곳을 앞쪽 (view 반대쪽) 에 두고 휘두름. size = 손에 든 크기의 배
     */
    static int cutThrough(ServerLevel level, ServerPlayer sp, Weapon w, Vec3 center, Vec3 view, double angle, double size, double secs, double hold, Opt o) {
        Bounds b = bounds(w);
        double len = b.length() * b.hand() * size;
        Vec3 v = V.len(view) > 1e-4 ? V.unit(view) : new Vec3(0, 0, 1);
        Vec3 right = V.cross(v, V.UP);
        right = V.len(right) < 1e-4 ? new Vec3(1, 0, 0) : V.unit(right);
        Vec3 up = V.unit(V.cross(right, v));
        double a = Math.toRadians(angle);
        Vec3 line = V.add(V.scale(up, Math.cos(a)), right, Math.sin(a)); // 베는 줄 (위쪽 끝 방향)
        double reach = len * 0.62;
        Vec3 pivot = V.add(center, v, -reach);
        Vec3 d0 = V.unit(V.add(V.scale(v, reach), line, reach * 0.85));
        Vec3 d1 = V.unit(V.add(V.scale(v, reach), line, -reach * 0.85));
        return swingItem(level, sp, w, pivot, d0, d1, secs, hold, size, true, o);
    }

    /**
     * 입체 불기둥 (flame — 밑동이 at, 일렁임): 폭 width · 높이 height 칸, life 초 (끝 0.2초에 사그라듦). 빛깔 color · 속빛 core
     */
    static int flame(ServerLevel level, Vec3 at, double width, double height, double life, double[] color, double[] core, Opt o) {
        double scale = Math.max(0.05, width / 0.62);
        Opt b = Opt.of("dir", V.UP, "faceCam", 1.0, "scale", scale, "stretch", new Vec3(1, Math.max(0.3, height / scale), 1), "life", life, "color", color,
                "glow", core, "anim", "flicker", "loop", 1.0, "animSpeed", 0.8 + Kit.rand() * 0.5, "animPhase", Kit.rand(), "pop", 0.1, "fadeOut",
                Math.min(0.25, life * 0.4), "glowAlpha", 0.55, "shine", 0.45);
        return Fx3d.spawn(level, "flame", at, merge(b, o));
    }

    /** 입체 빛기둥 (pillar — 밑이 at): 반지름 radius · 높이 height 칸, life 초. 빛깔 color · 속빛 core (고리가 돎) */
    static int pillar(ServerLevel level, Vec3 at, double radius, double height, double life, double[] color, double[] core, Opt o) {
        double scale = Math.max(0.05, radius / 0.5);
        Opt b = Opt.of("dir", V.UP, "face", new Vec3(0, 0, 1), "scale", scale, "stretch", new Vec3(1, Math.max(0.3, height / scale), 1), "life", life, "color",
                color, "glow", core, "anim", "spin", "loop", 1.0, "pop", 0.08, "fadeIn", 0.03, "fadeOut", Math.min(0.3, life * 0.5), "glowAlpha", 0.6, "shine",
                0.55);
        return Fx3d.spawn(level, "pillar", at, merge(b, o));
    }

    static void kill(ServerLevel level, int id, Vec3 near, double fade) {
        if (id != 0) Fx3d.kill(level, id, near, fade);
    }

    static void killAll(ServerLevel level, int[] ids, Vec3 near, double fade) {
        if (ids == null) return;
        for (int id : ids) kill(level, id, near, fade);
    }

    /** 수명 동안 주인이 죽거나 나가면 지움 (목숨 표가 바뀜) — 10틱마다 살핌 */
    static void guard(ServerPlayer sp, String life, int[] ids, int ticks) {
        for (int t = 10; t < ticks; t += 10) {
            Tasks.later(t, () -> {
                if (!Kit.sameLife(sp, life)) killAll(sp.level(), ids, sp.position(), 0.1);
            });
        }
    }
}
