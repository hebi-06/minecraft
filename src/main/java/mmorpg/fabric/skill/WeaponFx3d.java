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
 * 무기 모델 좌표: +Y = 무기 끝 (칼끝 · 창끝 · 망치 머리), 가운데 (0, 0, 0) = 모델 상자 가운데. 손에 든 것과 같은 크기 = hand 배.
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
