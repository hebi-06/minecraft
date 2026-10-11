package mmorpg.fabric.rune;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mmorpg.core.Obj;
import mmorpg.fabric.RpgNet;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.skill.Opt;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 초월 룬 하늘빛 · 화면 번쩍임 (ultfx.js): 하늘빛 = 둘레 사람의 하늘 · 먼 곳이 룬의 색으로 (사람마다 하나 — 새 하늘빛이 앞 것을 바꿈, 정한 틱 뒤 걷힘),
 * 번쩍임 = 화면이 그 색으로 짧게 (클라이언트가 그림 — RpgNet.SkyPayload · FlashPayload). 모드가 없는 클라이언트는 아무 일 없음.
 * 8단계 돌파: 룬 쪽 (RuneKit.ultSkyFor · ultFlashFor) 이 3돌 미만이면 부르지 않고, 6돌이면 거리 배율 · 테두리 빛 · 긴 번쩍임으로 부름.
 */
public final class Ult {
    private Ult() {}

    private static final Obj U = RuneKit.ULT_FX;

    /** 사람마다 지금 씌운 하늘빛 (하늘빛 이름 · 표 · 끝 틱 · 테두리 빛이 있는지) */
    private record Sky(String id, int token, long until, boolean edge) {}

    private static final Map<UUID, Sky> SKIES = new HashMap<>();
    private static int nextToken = 1;
    /** 하늘빛을 씌우지 않을 사람 (보스 안개가 씌워진 사람 — 5단계 보스가 넣음) */
    public static java.util.function.Predicate<ServerPlayer> skyBlocked = p -> false;

    static void init() {
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SKIES.clear());
    }

    static void forget(UUID id) {
        SKIES.remove(id);
    }

    /** at 둘레 radius 칸의 사람 */
    private static List<ServerPlayer> audience(ServerLevel level, Vec3 at, double radius) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : level.players()) if (p.position().distanceTo(at) <= radius) out.add(p);
        return out;
    }

    /**
     * ULT_FX.fogs[id] = [start, end, "#RRGGBB"] → 하늘빛 패킷. dist = 거리 배율 (작을수록 가까이서부터 짙음 — 8단계 6돌), edge = 화면 테두리 빛 (0 ~ 1)
     */
    private static RpgNet.SkyPayload payload(String id, int token, int ticks, double dist, double edge) {
        List<Object> f = U.obj("fogs").listOrNull(id);
        if (f == null || f.size() < 3) return null;
        int rgb = Integer.parseInt(((String) f.get(2)).substring(1), 16);
        float k = (float) Math.max(0.1, dist);
        return new RpgNet.SkyPayload(token, ticks, ((Number) f.get(0)).floatValue() * k, ((Number) f.get(1)).floatValue() * k, ((rgb >> 16) & 255) / 255F,
                ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F, (float) Math.max(0, Math.min(1, edge)));
    }

    /** 한 사람에게 하늘빛 fogId 를 ticks 틱 동안 (앞 하늘빛은 바꿈). 씌웠으면 true */
    public static boolean setSky(ServerPlayer p, String fogId, int ticks) {
        return setSky(p, fogId, ticks, 1, 0);
    }

    /** setSky + 거리 배율 · 테두리 빛 (8단계 돌파 연출 단계) */
    public static boolean setSky(ServerPlayer p, String fogId, int ticks, double dist, double edge) {
        if (!U.truthy("fog") || fogId == null || ticks <= 0 || skyBlocked.test(p)) return false;
        int token = nextToken++;
        RpgNet.SkyPayload pl = payload(fogId, token, ticks, dist, edge);
        if (pl == null) return false;
        SKIES.put(p.getUUID(), new Sky(fogId, token, Tasks.now() + ticks, edge > 0));
        RpgNet.sky(p, pl);
        return true;
    }

    /** 하늘빛: at 둘레 ULT_FX.audience 칸의 사람 모두에게 ticks 틱. 씌운 사람 수 */
    public static int sky(ServerLevel level, Vec3 at, String fogId, int ticks) {
        return sky(level, at, fogId, ticks, 1, 0);
    }

    /** sky + 거리 배율 · 테두리 빛 (8단계 돌파 연출 단계) */
    public static int sky(ServerLevel level, Vec3 at, String fogId, int ticks, double dist, double edge) {
        if (!U.truthy("fog") || fogId == null) return 0;
        int n = 0;
        for (ServerPlayer p : audience(level, at, U.num("audience"))) if (setSky(p, fogId, ticks, dist, edge)) n++;
        return n;
    }

    /** 하늘빛을 일찍 걷음 (룬을 쓴 사람이 죽음 · 버프가 끝남): 이 사람들 중 하늘빛이 fogId 인 사람만 */
    public static void endSky(List<ServerPlayer> players, String fogId) {
        for (ServerPlayer p : players) {
            Sky s = SKIES.get(p.getUUID());
            if (s == null || !s.id().equals(fogId)) continue;
            SKIES.remove(p.getUUID());
            RpgNet.sky(p, new RpgNet.SkyPayload(s.token(), 0, 0, 0, 0, 0, 0));
        }
    }

    /** 이 사람에게 지금 씌운 하늘빛 (없으면 null — 시험용) */
    public static String skyOf(ServerPlayer p) {
        Sky s = SKIES.get(p.getUUID());
        return s != null && s.until() > Tasks.now() ? s.id() : null;
    }

    /** 지금 씌운 하늘빛에 테두리 빛이 있는지 (시험용 — 8단계 6돌) */
    public static boolean skyEdgeOf(ServerPlayer p) {
        Sky s = SKIES.get(p.getUUID());
        return s != null && s.until() > Tasks.now() && s.edge();
    }

    /** 이 사람의 하늘빛 기록을 지움 (시험용 — 패킷은 보내지 않음) */
    public static void forgetSky(ServerPlayer p) {
        SKIES.remove(p.getUUID());
    }

    /**
     * 화면 번쩍임: at 둘레 o.radius (기본 ULT_FX.flashRadius) 칸의 사람 + caster (멀리 겨눠도) 의 화면이 color 로 짧게.
     * o.time = {fadeIn, hold, fadeOut} 초 (double[] — 없으면 ULT_FX.flashTime). 번쩍인 사람 수
     */
    public static int flash(ServerLevel level, Vec3 at, double[] color, Opt o, ServerPlayer caster) {
        if (!U.truthy("flash") || color == null) return 0;
        Obj t = U.obj("flashTime");
        double[] time = o.get("time") instanceof double[] d && d.length == 3 ? d
                : new double[] {t.num("fadeInTime"), t.num("holdTime"), t.num("fadeOutTime")};
        List<ServerPlayer> list = audience(level, at, o.num("radius", U.num("flashRadius")));
        if (caster != null && !caster.isRemoved() && !list.contains(caster)) list.add(caster);
        RpgNet.FlashPayload pl = new RpgNet.FlashPayload((float) color[0], (float) color[1], (float) color[2], (float) time[0], (float) time[1], (float) time[2]);
        for (ServerPlayer p : list) RpgNet.flash(p, pl);
        return list.size();
    }
}
