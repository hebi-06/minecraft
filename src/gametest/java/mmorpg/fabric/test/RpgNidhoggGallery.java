package mmorpg.fabric.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mmorpg.core.Obj;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.ArenaThemes;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.boss.BossNidhogg;
import mmorpg.fabric.boss.Bosses;
import mmorpg.fabric.mob.BedrockMob;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Status;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 5단계 사진첩 (화면 시험, only=nidhogg): 세계수의 뿌리 신전을 짓고 진짜로 도전 — 제단 누르기 → 도전 창 → 소환진 → 하늘에서 내려앉는 니드호그 (rpg:fly),
 * 걷기 (엔진 pose: 클라이언트 자리가 틱마다 고르게 · 몸 방향 = 서버 방향), 물어뜯기 (붉은 부채꼴) · 꼬리 (등 뒤 주황 부채꼴) · 독의 숨결 (쓸어 갈 보라 부채꼴 →
 * 쓸어 가는 불길, 몸이 따라 돎) · 날갯짓 폭풍 (초록 원 + 보라 도넛 → 침묵) · 뿌리의 올가미 (갈색 원 → 뿌리 · 속박) · 높은 곳 (뿌리 위 → 경고 → 날개로 떨어뜨림) ·
 * 비상 (날아오름 → 12칸 위를 돎 → 독의 비 (초록 원 · 방울) → 검붉은 원 → 급강하 → 내려앉음) · 독룡의 낙인 (따라다니는 보라 원 · 3 2 1 → 터짐) · 원거리 견제 (독 가시) ·
 * 플레이어 평타 (번쩍임 · 숫자 ») · 쓰러뜨림 (보상 · 가방). 패턴은 모듈 함수를 바로 부르고, 그 사이 보스는 틀 pause 로 멈춤 (몸은 BossNidhogg.setBody).
 * 사진 이름 nh_* → ci-logs 의 shots/.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgNidhoggGallery extends RpgDriveGallery {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-nidhogg-gallery");
    private static final double SOUTH = Math.PI / 2;
    private static final double NORTH = -Math.PI / 2;
    /** 설정 (BOSSES.nidhogg — 시험 스레드에서 읽음) */
    private static final Obj DEF = Arena.BOSSES.obj("nidhogg");

    @Override
    String key() {
        return "nidhogg";
    }

    @Override
    String only() {
        return "nidhogg";
    }

    @Override
    Logger log() {
        return LOG;
    }

    @Override
    List<String> formWords() {
        return List.of("Lv.38", "근접 보너스", "15분");
    }

    @Override
    String prefix() {
        return "nh";
    }

    @Override
    void scenes(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        if (!arrive(ctx, server)) return;
        walk(ctx, server);
        bite(ctx, server);
        tail(ctx, server);
        breath(ctx, server);
        gust(ctx, server);
        snare(ctx, server);
        high(ctx, server);
        flight(ctx, server);
        brand(ctx, server);
        far(ctx, server);
        playerHit(ctx, server);
        kill(ctx, server, cx, cz + 4.4, cx, ALTAR_Y + 2.2, cz, 28);
    }

    // ------------------------------------------------------------ 도우미

    /** 스스로 패턴 · 원거리 견제를 고르지 않게 (다음 틱을 멀리, 비상은 쓴 것으로) */
    private void freeze(TestServerContext server) {
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            BossNidhogg.M m = BossNidhogg.mem(e);
            if (m == null) return;
            m.next.replaceAll((k, v) -> FAR_FUTURE);
            m.bigUntil = FAR_FUTURE;
            m.flightDone.add(0.75);
            m.flightDone.add(0.4);
            e.farAt = FAR_FUTURE;
        });
    }

    /** 몸을 (x, z) 에 ang (라디안 — +x 에서 +z 쪽) 쪽을 보게 두고 멈춤 (틀 pause: 걷지 않고 패턴도 없음) */
    private void settle(TestServerContext server, double x, double z, double ang) {
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            BossNidhogg.M m = BossNidhogg.mem(e);
            if (m == null) return;
            m.body.want = null;
            BossNidhogg.setBody(e, x, z, ang);
            Bosses.cast(e, "pause", 1_000_000);
        });
    }

    /** 패턴 (서버) → 시작 틱 */
    private long start(TestServerContext server, java.util.function.Function<Bosses.Enc, Boolean> run, String what) {
        long[] t = {0};
        boolean r = server.computeOnServer(s -> {
            t[0] = Tasks.now();
            return run.apply(enc());
        });
        check(r, what);
        return t[0];
    }

    // ------------------------------------------------------------ 장면

    /** 등장: 하늘 14칸에서 깨운 사람 쪽을 보며 2초 동안 내려앉음 (날개 · rpg:fly 1 → 0) */
    private boolean arrive(ClientGameTestContext ctx, TestServerContext server) {
        look(ctx, server, cx, ALTAR_Y, cz + 17, cx, ALTAR_Y + 8, cz);
        for (int t = 0; t < 60 && !server.computeOnServer(s -> enc() != null && "fight".equals(enc().state)); t++) ctx.waitTick();
        long t0 = now(server);
        int id = bossId(server);
        check(id >= 0, "nidhogg spawned");
        if (id < 0) return false;
        ctx.waitTicks(6);
        Vec3 p = clientPos(ctx, id);
        double fly = clientProp(ctx, id, "rpg:fly");
        check(p != null && p.y - ALTAR_Y > 6 && fly == 1.0, String.format(Locale.ROOT, "descends from the sky (client y +%.1f, rpg:fly %.2f)", p == null ? -1 : p.y - ALTAR_Y, fly));
        shot(ctx, "arrive");
        checkDrawn(ctx, server);
        waitUntil(ctx, server, t0 + 41);
        shot(ctx, "land");
        String r = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            BossNidhogg.M m = BossNidhogg.mem(e);
            if (m == null || m.flight != null) return "still " + (m == null ? null : m.flight.stage);
            return e.boss.getHealth() == 25000F ? "ok" : "hp " + e.boss.getHealth();
        });
        check("ok".equals(r), "landed with 25000 hp (" + r + ")");
        freeze(server);
        settle(server, cx, cz, SOUTH);
        server.runCommand("title @a clear");
        look(ctx, server, cx + 2, ALTAR_Y, cz + 15, cx, ALTAR_Y + 3.2, cz);
        ctx.waitTicks(25);
        check(clientProp(ctx, id, "rpg:fly") == 0.0, "wings folded on the ground (client rpg:fly 0)");
        shot(ctx, "appear");
        return true;
    }

    /** 걷기 (엔진 pose — 매 틱 teleport): 클라이언트 자리가 틱마다 고르게 (초당 2.6칸), 걸음 rpg:gait, 몸 방향 = 서버 방향 */
    private void walk(ClientGameTestContext ctx, TestServerContext server) {
        int id = bossId(server);
        settle(server, cx - 5, cz - 5, Math.PI / 4);
        look(ctx, server, cx + 7, ALTAR_Y, cz + 7, cx - 5, ALTAR_Y + 3, cz - 5);
        ctx.waitTicks(4);
        server.runOnServer(s -> enc().cast = null); // 풀어 줌: 가장 가까운 사람 (플레이어) 쪽으로 걸음
        List<Double> steps = new ArrayList<>();
        Vec3 prev = clientPos(ctx, id);
        double maxYaw = 0;
        for (int i = 0; i < 14; i++) {
            ctx.waitTick();
            Vec3 p = clientPos(ctx, id);
            if (i >= 4 && prev != null && p != null) steps.add(Math.hypot(p.x - prev.x, p.z - prev.z));
            prev = p;
            float sy = server.computeOnServer(s -> enc().boss.getYRot());
            maxYaw = Math.max(maxYaw, degDiff(clientBodyYaw(ctx, id), sy));
        }
        double mean = steps.stream().mapToDouble(d -> d).average().orElse(0);
        double max = steps.stream().mapToDouble(d -> d).max().orElse(0);
        double min = steps.stream().mapToDouble(d -> d).min().orElse(0);
        check(mean > 0.1 && mean < 0.16 && max < 0.22 && min > 0.05, String.format(Locale.ROOT, "client walks smoothly (per tick mean %.3f min %.3f max %.3f — server 0.13)", mean, min, max));
        check(clientProp(ctx, id, "rpg:gait") >= 0.9, "stride on the client (rpg:gait " + clientProp(ctx, id, "rpg:gait") + ")");
        check(maxYaw < 6, String.format(Locale.ROOT, "client body faces the server yaw while walking (max %.1f°)", maxYaw));
        chase(ctx, server, cx + 7, cz + 7, cx - 2, ALTAR_Y + 2, cz - 2, 6);
        shot(ctx, "walk");
    }

    /** 물어뜯기: 대상 쪽으로 몸을 돌리며 앞 70° 붉은 부채꼴 (0.8초) → 물기. 몸이 도는 동안 클라이언트 몸 방향이 서버를 따라감 */
    private void bite(ClientGameTestContext ctx, TestServerContext server) {
        int id = bossId(server);
        settle(server, cx, cz - 3, SOUTH);
        chase(ctx, server, cx + 2.5, cz + 2.5, cx, ALTAR_Y + 0.5, cz - 1, 8);
        long t0 = start(server, e -> BossNidhogg.bite(e, Tasks.now(), BossKit.fighters(e)), "bite cast on the player in front");
        waitUntil(ctx, server, t0 + 9);
        float sy = server.computeOnServer(s -> enc().boss.getYRot());
        double d = degDiff(clientBodyYaw(ctx, id), sy);
        check(d < 8, String.format(Locale.ROOT, "body turned toward the target on the client (%.1f° off)", d));
        shot(ctx, "bite_tele");
        waitUntil(ctx, server, t0 + 17);
        shot(ctx, "bite");
        ctx.waitTicks(8);
    }

    /** 꼬리 후려치기: 등 뒤 130° 주황 부채꼴 (0.9초) → 옆으로 날아감 */
    private void tail(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz - 2, NORTH);
        chase(ctx, server, cx + 1.5, cz + 5, cx, ALTAR_Y, cz + 0.5, 9);
        long t0 = start(server, e -> BossNidhogg.tailWhip(e, Tasks.now(), BossKit.fighters(e)), "tail whip on the player behind");
        waitUntil(ctx, server, t0 + 11);
        shot(ctx, "tail_tele");
        waitUntil(ctx, server, t0 + 19);
        shot(ctx, "tail");
        ctx.waitTicks(15);
    }

    /** 독의 숨결: 쓸어 갈 곳 전체 보라 부채꼴 (밝은 쪽에서 시작) 1.2초 → 2초 동안 120° 를 쓸어 가는 독 불길 — 몸 (목) 이 따라 돎 */
    private void breath(ClientGameTestContext ctx, TestServerContext server) {
        int id = bossId(server);
        settle(server, cx, cz - 7, SOUTH);
        chase(ctx, server, cx, cz + 3, cx, ALTAR_Y, cz - 2, 12);
        long[] t = {0};
        boolean r = server.computeOnServer(s -> {
            boolean[] got = {false};
            withRand(new double[] {0.0, 0.3, 0.6}, () -> {
                t[0] = Tasks.now();
                got[0] = BossNidhogg.breath(enc(), Tasks.now(), BossKit.fighters(enc()));
            });
            return got[0];
        });
        check(r, "poison breath cast");
        int windup = (int) DEF.obj("breath").num("windup");
        waitUntil(ctx, server, t[0] + windup - 6);
        shot(ctx, "breath_tele");
        waitUntil(ctx, server, t[0] + windup + 18);
        float sy = server.computeOnServer(s -> enc().boss.getYRot());
        double d = degDiff(clientBodyYaw(ctx, id), sy);
        check(d < 12, String.format(Locale.ROOT, "body sweeps with the breath on the client (%.1f° behind)", d));
        shot(ctx, "breath");
        waitUntil(ctx, server, t[0] + windup + 44);
        server.runCommand("effect clear @a minecraft:wither");
    }

    /** 날갯짓 폭풍: 몸을 세우고 날개를 폄 — 초록 원 (4.5칸) 안 · 16칸 밖은 안전, 그 사이 보라 (1.8초) → 날개를 내려침 (침묵 · 날아감) */
    private void gust(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz - 2, SOUTH);
        look(ctx, server, cx, ALTAR_Y, cz + 6, cx, ALTAR_Y + 2, cz - 2);
        long t0 = start(server, e -> BossNidhogg.gust(e, Tasks.now(), BossKit.fighters(e)), "gust cast (player in the donut)");
        // 높이에서 본 초록 원 + 보라 도넛 (관전으로 잠깐 — 판정 전에 돌아옴)
        server.runCommand("gamemode spectator @a");
        camera(ctx, CameraType.FIRST_PERSON);
        look(ctx, server, cx, ALTAR_Y + 24, cz + 14, cx, ALTAR_Y, cz - 1);
        waitUntil(ctx, server, t0 + 18);
        shot(ctx, "gust_tele");
        server.runCommand("gamemode survival @a");
        chase(ctx, server, cx, cz + 6, cx, ALTAR_Y + 2, cz - 2, 7);
        int windup = (int) DEF.obj("gust").num("windup");
        waitUntil(ctx, server, t0 + windup + 2);
        shot(ctx, "gust");
        check(server.computeOnServer(s -> Status.isSilenced(player(s))), "caught between: silenced");
        ctx.waitTicks(20);
    }

    /** 뿌리의 올가미: 멀리 있는 사람 발밑 갈색 원 (1.4초, 땅을 갉는 동작) → 세계수 뿌리가 솟아 속박 */
    private void snare(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz - 4, SOUTH);
        chase(ctx, server, cx + 1, cz + 7, cx, ALTAR_Y + 1, cz - 4, 6);
        long t0 = start(server, e -> BossNidhogg.snare(e, Tasks.now(), BossKit.fighters(e)), "snare cast on the far player");
        waitUntil(ctx, server, t0 + 16);
        shot(ctx, "snare_tele");
        int windup = (int) DEF.obj("snare").num("windup");
        waitUntil(ctx, server, t0 + windup + 10);
        String r = server.computeOnServer(s -> {
            long n = enc().props.stream().filter(e -> Kit.isType(e, BossNidhogg.ROOTS_ID)).count();
            return n + " roots, rooted " + Status.isRooted(player(s));
        });
        check(r.equals("1 roots, rooted true"), "roots rise and bind (" + r + ")");
        // 발밑의 뿌리를 가까이 (속박 중이라 돌아보기만)
        chase(ctx, server, cx + 1, cz + 7, cx + 1, ALTAR_Y + 0.5, cz + 5, 4);
        shot(ctx, "snare");
        waitUntil(ctx, server, t0 + windup + 50 + 10);
        shot(ctx, "snare_sink");
        ctx.waitTicks(12);
    }

    /** 높은 곳: 세계수 뿌리 위에 올라서면 경고 → 1.5초 넘게 머물면 날개로 쳐서 가운데 쪽으로 떨어뜨림 */
    private void high(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz - 3, SOUTH);
        // 남동쪽 뿌리 (담장 탑 45°, 휨 +14°) 의 s = 0.3 자리 꼭대기
        ArenaThemes.Roots size = ArenaThemes.rootsSize("nidhogg");
        int R = (int) DEF.obj("arena").num("radius");
        ArenaThemes.RootPoint q = ArenaThemes.rootAt(R, size.arch(), size.ground(), 45, 14, 0.3);
        double rx = cx + q.r() * Math.cos(Math.toRadians(q.deg()));
        double rz = cz + q.r() * Math.sin(Math.toRadians(q.deg()));
        int top = server.computeOnServer(s -> {
            ServerLevel level = level(s);
            for (int y = ALTAR_Y + 14; y > ALTAR_Y; y--) {
                if (!level.getBlockState(BlockPos.containing(rx, y, rz)).isAir()) return y + 1;
            }
            return ALTAR_Y;
        });
        check(top - ALTAR_Y >= 3, "root top found " + (top - ALTAR_Y) + " blocks up");
        chase(ctx, server, rx, top, rz, cx, ALTAR_Y + 1, cz, 5);
        long t0 = now(server);
        waitUntil(ctx, server, t0 + 14);
        shot(ctx, "high_warn");
        long until = t0 + 60;
        boolean hit = false;
        while (now(server) < until && !hit) {
            ctx.waitTick();
            hit = server.computeOnServer(s -> BossNidhogg.mem(enc()).high.containsKey(player(s).getUUID()));
        }
        check(hit, "wing knocks the player down from the root");
        ctx.waitTicks(4);
        shot(ctx, "high");
        ctx.waitTicks(20);
    }

    /** 비상: 날아오름 → 12칸 위 원을 돎 (클라이언트도 고르게) → 독의 비 (초록 원 → 방울) 세 번 → 검붉은 원 → 급강하 → 내려앉음 */
    private void flight(ClientGameTestContext ctx, TestServerContext server) {
        int id = bossId(server);
        settle(server, cx, cz, SOUTH);
        double px = cx + 3;
        double pz = cz + 9;
        chase(ctx, server, px, pz, cx, ALTAR_Y + 5, cz, 9);
        long t0 = server.computeOnServer(s -> {
            BossNidhogg.takeoff(enc(), Tasks.now());
            return Tasks.now();
        });
        waitUntil(ctx, server, t0 + 10);
        shot(ctx, "takeoff");
        waitUntil(ctx, server, t0 + 33);
        // 하늘을 도는 니드호그 (땅에서 올려다봄)
        camera(ctx, CameraType.FIRST_PERSON);
        Vec3 b = server.computeOnServer(s -> enc().boss.position());
        look(ctx, server, px, ALTAR_Y, pz, b.x, b.y + 2, b.z);
        Vec3 c0 = clientPos(ctx, id);
        double fly = clientProp(ctx, id, "rpg:fly");
        check(c0 != null && Math.abs(c0.y - ALTAR_Y - 12) < 0.3 && fly == 1.0, String.format(Locale.ROOT, "flying 12 up on the client (y +%.2f, rpg:fly %.2f)",
                c0 == null ? -1 : c0.y - ALTAR_Y, fly));
        List<Double> steps = new ArrayList<>();
        Vec3 prev = c0;
        for (int i = 0; i < 6; i++) {
            ctx.waitTick();
            Vec3 p = clientPos(ctx, id);
            if (prev != null && p != null) steps.add(Math.hypot(p.x - prev.x, p.z - prev.z));
            prev = p;
        }
        double mean = steps.stream().mapToDouble(d -> d).average().orElse(0);
        double max = steps.stream().mapToDouble(d -> d).max().orElse(0);
        check(mean > 0.38 && mean < 0.62 && max < 0.8, String.format(Locale.ROOT, "client circles smoothly (per tick mean %.3f max %.3f — server 0.5)", mean, max));
        shot(ctx, "fly");
        // 독의 비: 발밑 초록 원 + 머리에서 빛줄기 → 방울
        int rain = (int) DEF.obj("flight").obj("rain").num("windup");
        chase(ctx, server, px, pz, px - 1, ALTAR_Y, pz - 3, 7);
        waitUntil(ctx, server, t0 + 40 + 4);
        shot(ctx, "rain_tele");
        waitUntil(ctx, server, t0 + 40 + rain - 6);
        shot(ctx, "rain");
        server.runCommand("effect clear @a minecraft:poison");
        // 급강하: 가장 많이 모인 곳 (플레이어) 에 검붉은 원 (6칸) 1.6초
        long diveAt = t0 + 40 + 2 * 40 + rain;
        waitUntil(ctx, server, diveAt + 2);
        server.runCommand("effect clear @a minecraft:poison");
        chase(ctx, server, px, pz, cx + 1, ALTAR_Y + 3, cz + 2, 12);
        waitUntil(ctx, server, diveAt + 14);
        shot(ctx, "dive_tele");
        waitUntil(ctx, server, diveAt + 33);
        shot(ctx, "dive");
        waitUntil(ctx, server, diveAt + 55);
        Vec3 c1 = clientPos(ctx, id);
        String r = server.computeOnServer(s -> {
            BossNidhogg.Flight f = BossNidhogg.flightOf(enc());
            return f == null ? "landed" : f.stage;
        });
        check("landed".equals(r) && c1 != null && Math.abs(c1.y - ALTAR_Y) < 0.1, "dive → landed (" + r + ", client y " + (c1 == null ? null : c1.y - ALTAR_Y) + ")");
        shot(ctx, "land_dive");
        server.runCommand("effect clear @a minecraft:poison");
        ctx.waitTicks(10);
    }

    /** 독룡의 낙인: 발밑 반지름 5칸 보라 원이 따라다님 · 머리 위 문양 · 3 2 1 → 터짐 */
    private void brand(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz - 9, SOUTH);
        chase(ctx, server, cx, cz + 3, cx, ALTAR_Y, cz + 1, 8);
        long t0 = start(server, e -> BossNidhogg.brand(e, Tasks.now(), BossKit.fighters(e)), "brand on everyone");
        waitUntil(ctx, server, t0 + 30);
        server.runCommand("title @a clear");
        waitUntil(ctx, server, t0 + 46);
        shot(ctx, "brand");
        int time = (int) DEF.obj("brand").num("time");
        waitUntil(ctx, server, t0 + time - 18);
        shot(ctx, "brand_count");
        waitUntil(ctx, server, t0 + time + 1);
        shot(ctx, "brand_boom");
        check(server.computeOnServer(s -> BossNidhogg.brandOf(enc()) == null && player(s).isAlive()), "brand exploded (single = damage only)");
        ctx.waitTicks(10);
    }

    /** 원거리 견제: 10칸 넘게 떨어진 사람 발밑 보라 원 + 입에서 독 가시가 포물선으로 → 시듦 */
    private void far(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz - 6, SOUTH);
        chase(ctx, server, cx, cz + 10, cx, ALTAR_Y + 4, cz - 2, 5);
        long t0 = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            Bosses.farThrow(e, e, Tasks.now(), List.of(player(s)));
            return Tasks.now();
        });
        waitUntil(ctx, server, t0 + 15);
        shot(ctx, "far");
        waitUntil(ctx, server, t0 + 31);
        shot(ctx, "far_hit");
        check(server.computeOnServer(s -> enc().farShots.isEmpty() && player(s).hasEffect(MobEffects.WITHER)), "far shot (poison spike) landed — withered");
        server.runCommand("effect clear @a minecraft:wither");
        ctx.waitTicks(10);
    }

    /** 플레이어 평타 (진짜 클릭): 번쩍임 · 피해 숫자 (가슴 높이, 때린 사람 쪽 몸 겉면) · 근접 보너스 » */
    private void playerHit(ClientGameTestContext ctx, TestServerContext server) {
        int id = bossId(server);
        settle(server, cx, cz, SOUTH);
        chase(ctx, server, cx, cz + 4.4, cx, ALTAR_Y + 2.2, cz, 6);
        float hp0 = server.computeOnServer(s -> enc().boss.getHealth());
        ctx.waitTicks(25);
        attack(ctx);
        boolean flash = false;
        for (int i = 0; i < 6 && !flash; i++) {
            ctx.waitTick();
            flash = ctx.computeOnClient(mc -> mc.level.getEntity(id) instanceof BedrockMob m && m.propBool("rpg:flash"));
        }
        float hp1 = server.computeOnServer(s -> enc().boss.getHealth());
        check(hp1 < hp0, "player melee hits nidhogg (" + hp0 + " → " + hp1 + ")");
        check(flash, "nidhogg flashes red on the client (rpg:flash)");
        List<String> nums = numbersNear(server, 7);
        check(nums.stream().anyMatch(t -> t.contains("»")), "damage number with the close bonus mark » " + nums);
        ctx.waitTicks(1);
        shot(ctx, "hit");
        ctx.waitTicks(15);
    }
}
