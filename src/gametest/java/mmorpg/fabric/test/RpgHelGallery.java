package mmorpg.fabric.test;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import mmorpg.core.Obj;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.BossHel;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.boss.Bosses;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 0.35.0 사진첩 (화면 시험, only=hel): 헬헤임 신전을 짓고 진짜로 도전 — 제단 누르기 → 도전 창 (Lv.58 · 5~7명 · 제한 시간 · 30분) → 소환진 →
 * 제단 앞 안개에서 솟아올라 손짓, 모습 (앞 · 옆 · 뒤 · 보스 막대 둘 — 체력 · 헬헤임의 문까지), 움직임 (떠서 다가옴),
 * 죽음의 손톱 (부채꼴 · 검은 손을 듦 → 내리긁음), 망자의 손 (발밑 원 → 솟는 손), 삶과 죽음의 경계 (반쪽이 청록 안개로 — 땅 · 위에서 → 반쪽을 침),
 * 굘 강의 물결 (띠 → 물결이 쓸고 지나감), 헬의 시선 (따라오는 줄 → 멈춤 → 비킨 자리 옆으로 광선), 안개 걸음 (흩어짐 → 등 뒤 안개 → 나타나 손톱),
 * 각성 「망자의 여왕」 (울부짖음 → 검은 금이 번진 텍스처 · 차가운 안개), 걀라르 다리 (금빛 다리 · 빛기둥 · 청록 바닥 → 3 2 1 → 침, 위에서 한 장),
 * 헬헤임의 문 (막대 붉게 → 문), 쓰러뜨림 (딜 순위 · 처치 시간 · 보상 · 가방의 헬의 손톱 · 망자의 안개).
 * 패턴은 모듈 함수를 바로 부르고, 그 사이 보스는 틀 pause 로 멈춤 (몸은 setBody). 플레이어는 저항 V · 밀치기 저항. 사진 이름 hel_* → ci-logs 의 shots/.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgHelGallery extends RpgDriveGallery {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-hel-gallery");
    private static final double SOUTH = Math.PI / 2;
    private static final Obj DEF = Arena.BOSSES.obj("hel");

    @Override
    String key() {
        return "hel";
    }

    @Override
    String only() {
        return "hel";
    }

    @Override
    Logger log() {
        return LOG;
    }

    @Override
    List<String> formWords() {
        return List.of("Lv.58", "5~7명", "근접 보너스", "30분", "제한 시간");
    }

    @Override
    String prefix() {
        return "hel";
    }

    @Override
    void scenes(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        if (!arrive(ctx, server)) return;
        steady(ctx, server);
        looks(ctx, server);
        glide(ctx, server);
        claw(ctx, server);
        hands(ctx, server);
        divide(ctx, server);
        river(ctx, server);
        gaze(ctx, server);
        blink(ctx, server);
        awaken(ctx, server);
        bridge(ctx, server);
        claw2(ctx, server);
        gate(ctx, server);
        settle(server, cx, z0(), SOUTH);
        ctx.waitTicks(10);
        kill(ctx, server, cx, z0() + 4.2, cx, ALTAR_Y + 2.6, z0(), 64);
    }

    // ------------------------------------------------------------ 도우미

    /** 헬을 세워 두는 자리 (가운데 제단 블록 북쪽 — 모듈의 ALTAR_OFF 와 같은 거리) */
    private double z0() {
        return cz - BossHel.ALTAR_OFF;
    }

    /** 스스로 패턴 · 안개 걸음 · 걀라르 다리 · 원거리 견제 · 각성을 고르지 않게 (헬헤임의 문은 시계를 당길 때만) */
    private void freeze(TestServerContext server) {
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            BossHel.M m = BossHel.mem(e);
            if (m == null) return;
            m.next.replaceAll((k, v) -> FAR_FUTURE);
            for (String id : BossHel.PATTERNS) m.next.put(id, FAR_FUTURE);
            m.nextAttack = FAR_FUTURE;
            m.blinkAt = FAR_FUTURE;
            m.bridgeAt = FAR_FUTURE;
            m.awakenPending = false;
            e.farAt = FAR_FUTURE;
        });
    }

    /** 몸을 (x, z) 에 ang 쪽을 보게 두고 멈춤 (틀 pause) */
    private void settle(TestServerContext server, double x, double z, double ang) {
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            if (BossHel.mem(e) == null) return;
            BossHel.setBody(e, x, z, ang);
            Bosses.cast(e, "pause", 1_000_000);
        });
    }

    /** 패턴 시작 (틀을 비우고 바로) — 시작한 서버 틱 */
    private long start(TestServerContext server, Function<Bosses.Enc, Boolean> run, String what) {
        long[] t = {0};
        boolean r = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            e.cast = null;
            t[0] = Tasks.now();
            return run.apply(e);
        });
        check(r, what);
        return t[0];
    }

    /** 플레이어가 밀려나지 않게 (사진 자리) */
    private void steady(ClientGameTestContext ctx, TestServerContext server) {
        server.runOnServer(s -> {
            AttributeInstance a = player(s).getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (a != null) a.setBaseValue(1.0);
        });
        ctx.waitTick();
    }

    /** 선딜레이 (서버의 지금 배율로) */
    private int windup(TestServerContext server, double ticks) {
        return server.computeOnServer(s -> BossHel.windup(enc(), ticks));
    }

    /** 잠깐 관전으로 위에서 한 장 (싸우는 사람이 없는 시간이 짧게 — 바로 서바이벌로) */
    private void top(ClientGameTestContext ctx, TestServerContext server, String name, double tx, double tz, double h) {
        server.runCommand("gamemode spectator @a");
        look(ctx, server, tx + 0.5, ALTAR_Y + h, tz + 7, tx, ALTAR_Y, tz);
        ctx.waitTicks(6);
        shot(ctx, name);
        server.runCommand("gamemode survival @a");
    }

    // ------------------------------------------------------------ 장면

    /** 등장: 제단 앞 안개에서 솟아올라 부른 사람에게 손짓 */
    private boolean arrive(ClientGameTestContext ctx, TestServerContext server) {
        for (int t = 0; t < 60 && !server.computeOnServer(s -> enc() != null && "fight".equals(enc().state)); t++) ctx.waitTick();
        long t0 = now(server);
        int id = bossId(server);
        check(id >= 0, "hel spawned");
        if (id < 0) return false;
        look(ctx, server, cx + 3, ALTAR_Y, cz + 12, cx, ALTAR_Y + 2.8, cz);
        waitUntil(ctx, server, t0 + 8);
        shot(ctx, "arrive");
        checkDrawn(ctx, server);
        waitUntil(ctx, server, t0 + 28);
        shot(ctx, "beckon");
        String r = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            if (!e.immune) return "not immune while arriving";
            return e.boss.getHealth() == 1400000F ? "ok" : "hp " + e.boss.getHealth();
        });
        check("ok".equals(r), "arrives immune with 1400000 hp (" + r + ")");
        waitUntil(ctx, server, t0 + BossHel.ARRIVE + 2);
        freeze(server);
        settle(server, cx, z0(), SOUTH);
        return true;
    }

    /** 모습: 앞 · 옆 · 뒤 · 보스 막대 둘 (체력 · 헬헤임의 문까지) */
    private void looks(ClientGameTestContext ctx, TestServerContext server) {
        camera(ctx, CameraType.FIRST_PERSON);
        double z = z0();
        // 앞 (비스듬히 — 가운데 제단 블록이 헬을 가리지 않게)
        look(ctx, server, cx + 6, ALTAR_Y, z + 9, cx, ALTAR_Y + 3.0, z);
        ctx.waitTicks(10);
        shot(ctx, "hel");
        look(ctx, server, cx + 10, ALTAR_Y, z + 1.5, cx, ALTAR_Y + 3.0, z);
        ctx.waitTicks(6);
        shot(ctx, "hel_side");
        look(ctx, server, cx - 3, ALTAR_Y, z - 10, cx, ALTAR_Y + 3.0, z);
        ctx.waitTicks(6);
        shot(ctx, "hel_back");
        look(ctx, server, cx, ALTAR_Y, z + 14, cx, ALTAR_Y + 3.4, z);
        ctx.waitTicks(12);
        boolean timer = server.computeOnServer(s -> {
            var bar = BossHel.timerOf(enc());
            return bar != null && bar.getPlayers().contains(player(s));
        });
        check(timer, "gate timer bar shown under the hp bar");
        shot(ctx, "bars");
    }

    /** 움직임: 멀리 있는 사람에게 떠서 느리게 다가옴 (치마 · 머리칼이 뒤로 흩날림) */
    private void glide(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz - 9, SOUTH);
        look(ctx, server, cx + 3, ALTAR_Y, cz + 10, cx, ALTAR_Y + 2.6, cz - 9);
        server.runOnServer(s -> enc().cast = null);
        ctx.waitTicks(24);
        double gait = clientProp(ctx, bossId(server), "rpg:gait");
        check(gait > 0.5, String.format(Locale.ROOT, "glides toward the far player (rpg:gait %.2f)", gait));
        shot(ctx, "glide");
        settle(server, cx, z0(), SOUTH);
    }

    /** 죽음의 손톱: 부채꼴 · 검은 오른손을 높이 듦 → 내리긁음 (손톱 자국 · 영혼) */
    private void claw(ClientGameTestContext ctx, TestServerContext server) {
        double z = z0();
        settle(server, cx, z, SOUTH);
        look(ctx, server, cx + 1.8, ALTAR_Y, z + 10, cx, ALTAR_Y + 2.6, z + 1);
        ctx.waitTicks(4);
        int w = windup(server, DEF.obj("claw").num("windup"));
        long t0 = start(server, e -> BossHel.clawAt(e, new Vec3(0, 0, 1)), "claw starts");
        waitUntil(ctx, server, t0 + 14);
        shot(ctx, "claw_windup");
        waitUntil(ctx, server, t0 + w + 1);
        shot(ctx, "claw_strike");
        ctx.waitTicks(16);
    }

    /** 망자의 손: 내 발밑 원 (3인칭 뒤) → 땅에서 손이 솟음 */
    private void hands(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz - 7, SOUTH);
        chase(ctx, server, cx + 1, cz + 4, cx - 1, ALTAR_Y + 0.5, cz - 4, 6);
        ctx.waitTicks(4);
        int w = windup(server, DEF.obj("hands").num("windup"));
        long t0 = start(server, e -> BossHel.hands(e, Tasks.now(), BossKit.fighters(e)), "dead hands start");
        waitUntil(ctx, server, t0 + 18);
        shot(ctx, "hands_mark");
        waitUntil(ctx, server, t0 + w + 3);
        shot(ctx, "hands_rise");
        camera(ctx, CameraType.FIRST_PERSON);
        camDist(ctx, server, 4);
        ctx.waitTicks(14);
        settle(server, cx, z0(), SOUTH);
    }

    /** 삶과 죽음의 경계: 검은 오른손이 남쪽 반쪽을 가리킴 · 반쪽이 청록 안개로 (북쪽 — 무사한 쪽에서 봄 · 위에서 한 장) → 반쪽을 침 */
    private void divide(ClientGameTestContext ctx, TestServerContext server) {
        double z = z0();
        settle(server, cx, z, SOUTH);
        look(ctx, server, cx - 7, ALTAR_Y, cz - 9, cx + 1, ALTAR_Y + 1.6, cz + 5);
        ctx.waitTicks(4);
        int w = windup(server, DEF.obj("divide").num("windup"));
        long t0 = start(server, e -> BossHel.divideAt(e, SOUTH), "divide starts (south half)");
        waitUntil(ctx, server, t0 + 16);
        shot(ctx, "divide");
        top(ctx, server, "divide_top", cx, cz, 34);
        look(ctx, server, cx - 7, ALTAR_Y, cz - 9, cx + 1, ALTAR_Y + 1.6, cz + 5);
        waitUntil(ctx, server, t0 + w + 2);
        shot(ctx, "divide_strike");
        ctx.waitTicks(16);
    }

    /** 굘 강의 물결: 가로지르는 띠 (내 앞 — 띠 밖에서 봄) → 물결이 띠를 따라 쓸고 지나감 */
    private void river(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz - 10, SOUTH);
        look(ctx, server, cx - 3, ALTAR_Y, cz + 13, cx + 2, ALTAR_Y + 0.6, cz + 3);
        ctx.waitTicks(4);
        int w = windup(server, DEF.obj("river").num("windup"));
        long t0 = start(server, e -> BossHel.riverAt(e, 0, 3, 1), "river starts (band z 0 ~ 6, flowing +x)");
        waitUntil(ctx, server, t0 + 20);
        shot(ctx, "river_mark");
        // 물결 (0.7초 동안 흐름 — 화면은 서버보다 몇 틱 늦게 그려짐): 가운데쯤 · 끝 무렵
        waitUntil(ctx, server, t0 + w + 8);
        shot(ctx, "river_wave");
        waitUntil(ctx, server, t0 + w + 13);
        shot(ctx, "river_wave2");
        ctx.waitTicks(16);
        settle(server, cx, z0(), SOUTH);
    }

    /** 헬의 시선: 나를 따라오는 줄 → 멈춤 (창백한 줄) → 옆으로 비킨 자리에서 광선이 지나감 */
    private void gaze(ClientGameTestContext ctx, TestServerContext server) {
        double z = z0();
        settle(server, cx, z, SOUTH);
        look(ctx, server, cx + 0.5, ALTAR_Y, cz + 15, cx, ALTAR_Y + 3.6, z);
        ctx.waitTicks(4);
        Obj G = DEF.obj("gaze");
        int w = windup(server, G.num("windup"));
        int lock = Math.min(w - 4, (int) G.num("lock"));
        long t0 = start(server, e -> BossHel.gaze(e, Tasks.now(), BossKit.fighters(e)), "gaze starts");
        waitUntil(ctx, server, t0 + 12);
        shot(ctx, "gaze_aim");
        waitUntil(ctx, server, t0 + w - lock + 2);
        shot(ctx, "gaze_lock");
        // 멈춘 뒤 옆으로 비킴 → 광선이 곁을 지나감
        look(ctx, server, cx + 4.5, ALTAR_Y, cz + 15, cx - 1, ALTAR_Y + 2.0, z + 4);
        waitUntil(ctx, server, t0 + w + 2);
        shot(ctx, "gaze_beam");
        ctx.waitTicks(16);
    }

    /** 안개 걸음: 안개로 흩어짐 → 내 등 뒤에 안개가 모임 (앞에서 본 3인칭) → 나타남 → 손톱 */
    private void blink(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz - 6, SOUTH);
        Obj B = DEF.obj("blink");
        int vanish = (int) B.num("vanish");
        int hidden = (int) B.num("hidden");
        int w = windup(server, DEF.obj("claw").num("windup"));
        look(ctx, server, cx, ALTAR_Y, cz + 5, cx, ALTAR_Y + 2.6, cz - 6);
        ctx.waitTicks(4);
        long t0 = start(server, e -> BossHel.blink(e, Tasks.now(), BossKit.fighters(e)), "mist step starts");
        waitUntil(ctx, server, t0 + 6);
        shot(ctx, "blink_vanish");
        camera(ctx, CameraType.THIRD_PERSON_FRONT);
        camDist(ctx, server, 7);
        waitUntil(ctx, server, t0 + vanish + 8);
        boolean hiddenOk = server.computeOnServer(s -> enc().boss.isInvisible() && enc().immune);
        check(hiddenOk, "in the mist: invisible + immune");
        shot(ctx, "blink_mist");
        waitUntil(ctx, server, t0 + vanish + hidden + 4);
        shot(ctx, "blink_appear");
        waitUntil(ctx, server, t0 + vanish + hidden + 6 + w + 1);
        shot(ctx, "blink_claw");
        camera(ctx, CameraType.FIRST_PERSON);
        camDist(ctx, server, 4);
        ctx.waitTicks(16);
        settle(server, cx, z0(), SOUTH);
    }

    /** 각성 「망자의 여왕」: 울부짖음 (영혼 문양 · 영혼) → 검은 금이 번진 텍스처 (rpg:phase 1) · 차가운 안개 */
    private void awaken(ClientGameTestContext ctx, TestServerContext server) {
        double z = z0();
        settle(server, cx, z, SOUTH);
        look(ctx, server, cx + 2, ALTAR_Y, z + 13, cx, ALTAR_Y + 3.0, z);
        ctx.waitTicks(4);
        int roar = (int) DEF.obj("phase2").num("roar");
        long t0 = start(server, e -> {
            BossHel.awaken(e, Tasks.now());
            return true;
        }, "awakening");
        waitUntil(ctx, server, t0 + 16);
        shot(ctx, "awaken_roar");
        waitUntil(ctx, server, t0 + roar + 12);
        freeze(server);
        settle(server, cx, z, SOUTH);
        double phase = clientProp(ctx, bossId(server), "rpg:phase");
        check(Math.round(phase) == 1, "queen of the dead texture on the client (rpg:phase " + phase + ")");
        look(ctx, server, cx + 6, ALTAR_Y, z + 9, cx, ALTAR_Y + 3.0, z);
        // 각성 타이틀 (3.5초 + 걷힘 1초) 이 걷힌 뒤
        waitUntil(ctx, server, t0 + 112);
        shot(ctx, "queen");
    }

    /** 걀라르 다리 (각성 뒤): 가운데 곁으로 떠 감 → 금빛 다리 (빛기둥) 만 남고 청록 바닥 (다리 위에서 봄 · 위에서 한 장) → 3 2 1 → 침 */
    private void bridge(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx + 5, cz - 6, SOUTH);
        look(ctx, server, cx + 9, ALTAR_Y, cz + 0.5, cx - 2, ALTAR_Y + 2.8, cz);
        ctx.waitTicks(4);
        Obj B = DEF.obj("bridge");
        int jump = (int) B.num("jump");
        int w = (int) B.num("windup");
        long t0 = start(server, e -> BossHel.bridgeAt(e, 0), "gjallar bridge starts (along x)");
        waitUntil(ctx, server, t0 + jump + 8);
        shot(ctx, "bridge");
        top(ctx, server, "bridge_top", cx, cz, 36);
        look(ctx, server, cx + 9, ALTAR_Y, cz + 0.5, cx - 2, ALTAR_Y + 2.8, cz);
        waitUntil(ctx, server, t0 + jump + w - 18);
        shot(ctx, "bridge_count");
        waitUntil(ctx, server, t0 + jump + w + 2);
        Vec3 b = server.computeOnServer(s -> enc().boss.position());
        check(Math.abs(b.x - (cx - BossHel.ALTAR_OFF)) < 0.4 && Math.abs(b.z - cz) < 0.4, String.format(Locale.ROOT, "floated next to the center (%.2f, %.2f)", b.x - cx, b.z - cz));
        shot(ctx, "bridge_strike");
        ctx.waitTicks(24);
        settle(server, cx, z0(), SOUTH);
    }

    /** 각성 뒤 죽음의 손톱 (선딜레이 ×0.85 · 차가운 안개 속) — 3인칭 뒤에서 */
    private void claw2(ClientGameTestContext ctx, TestServerContext server) {
        double z = z0();
        settle(server, cx, z, SOUTH);
        chase(ctx, server, cx + 1.4, z + 5.5, cx - 0.5, ALTAR_Y + 2.4, z, 6);
        ctx.waitTicks(4);
        int w = windup(server, DEF.obj("claw").num("windup"));
        check(w < (int) DEF.obj("claw").num("windup"), "awakened windup is shorter (" + w + ")");
        long t0 = start(server, e -> BossHel.clawAt(e, new Vec3(0, 0, 1)), "awakened claw");
        waitUntil(ctx, server, t0 + 12);
        shot(ctx, "claw2_windup");
        waitUntil(ctx, server, t0 + w + 1);
        shot(ctx, "claw2_strike");
        camera(ctx, CameraType.FIRST_PERSON);
        camDist(ctx, server, 4);
        ctx.waitTicks(16);
    }

    /** 헬헤임의 문 (제한 시간을 당겨 봄 — 크리에이티브라 즉사하지 않음): 막대 붉게 · 10 → 문이 열리며 필드를 삼킴 → 시계를 되돌림 */
    private void gate(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz, SOUTH);
        server.runCommand("gamemode creative @a");
        // 문의 안개 (3 ~ 22칸) 안에서 — 가운데 위의 구멍 · 문양이 보이게
        look(ctx, server, cx + 3, ALTAR_Y, cz + 10, cx, ALTAR_Y + 4.5, cz);
        ctx.waitTicks(4);
        long[] shift = {0};
        long t0 = server.computeOnServer(s -> {
            shift[0] = BossHel.timeLeft(enc(), Tasks.now()) - 30;
            BossHel.shiftClock(enc(), shift[0]);
            return Tasks.now();
        });
        waitUntil(ctx, server, t0 + 11);
        shot(ctx, "gate_timer");
        int wu = (int) DEF.obj("enrage").num("windup");
        waitUntil(ctx, server, t0 + 30 + wu / 2);
        boolean open = server.computeOnServer(s -> enc().cast != null && "gate".equals(enc().cast.id));
        check(open, "the gate of helheim after the limit");
        shot(ctx, "gate");
        waitUntil(ctx, server, t0 + 30 + wu + 2);
        shot(ctx, "gate_strike");
        // 당긴 만큼 되돌림 (처치 알림의 처치 시간이 실제 걸린 시간이 되게)
        server.runOnServer(s -> BossHel.shiftClock(enc(), -shift[0]));
        waitUntil(ctx, server, t0 + 30 + wu + 24);
        server.runCommand("gamemode survival @a");
        server.runCommand("effect clear @a minecraft:darkness");
        freeze(server);
    }
}
