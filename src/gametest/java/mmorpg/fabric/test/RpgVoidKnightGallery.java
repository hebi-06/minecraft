package mmorpg.fabric.test;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import mmorpg.core.Obj;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.boss.BossVoidKnight;
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
 * 0.33.0 사진첩 (화면 시험, only=voidknight): 공허의 결투장을 짓고 진짜로 도전 — 제단 누르기 → 도전 창 (제한 시간 · 빈틈 · 공허 붕괴) → 소환진 →
 * 제단 앞 균열에서 비집고 나와 칼을 세움, 모습 (앞 · 옆 · 뒤 · 보스 막대 둘 — 체력 · 공허 붕괴까지), 움직임 (달려듦 · 둘레 돌기),
 * 내려베기 (부채꼴 · 칼을 듦 → 내려침 · 균열 → 빈틈: 진짜 클릭 분홍 "빈틈" 숫자), 돌진 찌르기 (줄 → 찔러 나감), 도약 내려찍기 (원 → 공중 → 내려찍음),
 * 회전 베기 (원 → 회전), 균열 걸음 (사라짐 → 등 뒤 균열 → 나옴 → 베기), 3연격, 각성 (포효 · 문양 · 균열 → 빛이 센 텍스처 · 보랏빛 안개),
 * 공허 검기 (줄 → 날아감 · 회전 베기의 여섯 줄), 공허 참 (금빛 안전 원 · 빛기둥 · 보랏빛 바닥 → 3 2 1 → 십자 참격), 공허 붕괴 (막대 붉게 → 구멍),
 * 쓰러뜨림 (딜 순위 · 처치 시간 · 보상 · 가방의 공허의 심장 · 균열 대검 조각). 패턴은 모듈 함수를 바로 부르고, 그 사이 보스는 틀 pause 로 멈춤 (몸은 setBody).
 * 플레이어는 저항 V · 밀치기 저항 (사진 자리가 흔들리지 않게). 사진 이름 vk_* → ci-logs 의 shots/.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgVoidKnightGallery extends RpgDriveGallery {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-voidknight-gallery");
    private static final double SOUTH = Math.PI / 2;
    private static final double NORTH = -Math.PI / 2;
    private static final Obj DEF = Arena.BOSSES.obj("void_knight");

    @Override
    String key() {
        return "void_knight";
    }

    @Override
    String only() {
        return "voidknight";
    }

    @Override
    Logger log() {
        return LOG;
    }

    @Override
    List<String> formWords() {
        return List.of("Lv.55", "근접 보너스", "20분", "제한 시간");
    }

    @Override
    String prefix() {
        return "vk";
    }

    @Override
    void scenes(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        if (!arrive(ctx, server)) return;
        steady(ctx, server);
        looks(ctx, server);
        move(ctx, server);
        cleave(ctx, server);
        thrust(ctx, server);
        leap(ctx, server);
        spin(ctx, server, "spin");
        blink(ctx, server);
        combo(ctx, server);
        awaken(ctx, server);
        waves(ctx, server);
        spin(ctx, server, "spin2");
        cross(ctx, server);
        collapse(ctx, server);
        settle(server, cx, z0(), SOUTH);
        ctx.waitTicks(10);
        kill(ctx, server, cx, z0() + 3.4, cx, ALTAR_Y + 2.4, z0(), 56);
    }

    // ------------------------------------------------------------ 도우미

    /** 보스를 세워 두는 자리 (가운데 제단 블록 북쪽 — 다리가 제단 블록에 묻히지 않게, 모듈의 ALTAR_OFF 와 같은 거리) */
    private double z0() {
        return cz - BossVoidKnight.ALTAR_OFF;
    }

    /** 스스로 패턴 · 원거리 견제 · 공허 참 · 각성을 고르지 않게 (공허 붕괴는 시계를 당길 때만) */
    private void freeze(TestServerContext server) {
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            BossVoidKnight.M m = BossVoidKnight.mem(e);
            if (m == null) return;
            m.next.replaceAll((k, v) -> FAR_FUTURE);
            m.nextAttack = FAR_FUTURE;
            m.crossAt = FAR_FUTURE;
            m.awakenPending = false;
            e.farAt = FAR_FUTURE;
        });
    }

    /** 몸을 (x, z) 땅 위에 ang 쪽을 보게 두고 멈춤 (틀 pause) */
    private void settle(TestServerContext server, double x, double z, double ang) {
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            if (BossVoidKnight.mem(e) == null) return;
            BossVoidKnight.setBody(e, x, z, ang);
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
        return server.computeOnServer(s -> BossVoidKnight.windup(enc(), ticks));
    }

    // ------------------------------------------------------------ 장면

    /** 등장: 가운데 균열에서 가늘게 비집고 나옴 → 무릎 꿇은 자세로 부풂 → 일어나 칼을 세움 */
    private boolean arrive(ClientGameTestContext ctx, TestServerContext server) {
        for (int t = 0; t < 60 && !server.computeOnServer(s -> enc() != null && "fight".equals(enc().state)); t++) ctx.waitTick();
        long t0 = now(server);
        int id = bossId(server);
        check(id >= 0, "void knight spawned");
        if (id < 0) return false;
        look(ctx, server, cx + 3, ALTAR_Y, cz + 10, cx, ALTAR_Y + 2.6, cz);
        waitUntil(ctx, server, t0 + 5);
        shot(ctx, "arrive");
        checkDrawn(ctx, server);
        waitUntil(ctx, server, t0 + 26);
        shot(ctx, "salute");
        String r = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            if (!e.immune) return "not immune while arriving";
            return e.boss.getHealth() == 450000F ? "ok" : "hp " + e.boss.getHealth();
        });
        check("ok".equals(r), "arrives immune with 450000 hp (" + r + ")");
        waitUntil(ctx, server, t0 + BossVoidKnight.ARRIVE + 2);
        freeze(server);
        settle(server, cx, z0(), SOUTH);
        return true;
    }

    /** 모습: 앞 · 옆 · 뒤 (망토) · 보스 막대 둘 (체력 · 공허 붕괴까지) */
    private void looks(ClientGameTestContext ctx, TestServerContext server) {
        camera(ctx, CameraType.FIRST_PERSON);
        double z = z0();
        look(ctx, server, cx + 2.4, ALTAR_Y, z + 8.5, cx, ALTAR_Y + 2.7, z);
        ctx.waitTicks(10);
        shot(ctx, "knight");
        look(ctx, server, cx + 8.5, ALTAR_Y, z + 1.2, cx, ALTAR_Y + 2.7, z);
        ctx.waitTicks(6);
        shot(ctx, "knight_side");
        look(ctx, server, cx - 3, ALTAR_Y, z - 8.5, cx, ALTAR_Y + 2.7, z);
        ctx.waitTicks(6);
        shot(ctx, "knight_back");
        look(ctx, server, cx, ALTAR_Y, z + 13, cx, ALTAR_Y + 3.2, z);
        ctx.waitTicks(12);
        boolean timer = server.computeOnServer(s -> {
            var bar = BossVoidKnight.timerOf(enc());
            return bar != null && bar.getPlayers().contains(player(s));
        });
        check(timer, "collapse timer bar shown under the hp bar");
        shot(ctx, "bars");
    }

    /** 움직임: 멀리서 달려듦 (몸을 숙임 · 망토) → 둘레를 돎 (옆걸음) */
    private void move(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz - 8, SOUTH);
        look(ctx, server, cx + 2, ALTAR_Y, cz + 10, cx, ALTAR_Y + 2.4, cz - 8);
        server.runOnServer(s -> enc().cast = null);
        ctx.waitTicks(9);
        boolean dashing = server.computeOnServer(s -> BossVoidKnight.mem(enc()).dashing);
        check(dashing, "dashes at the far player");
        shot(ctx, "dash");
        ctx.waitTicks(30);
        double strafe = clientProp(ctx, bossId(server), "rpg:strafe");
        camera(ctx, CameraType.THIRD_PERSON_BACK);
        camDist(ctx, server, 7);
        Vec3 b = server.computeOnServer(s -> enc().boss.position());
        ctx.getInput().lookAt((float) Math.toDegrees(Math.atan2(-(b.x - (cx + 2)), b.z - (cz + 10))), 8F);
        ctx.waitTicks(4);
        check(Math.abs(strafe) > 0.2, String.format(Locale.ROOT, "circles with a side step (rpg:strafe %.2f)", strafe));
        shot(ctx, "strafe");
        camera(ctx, CameraType.FIRST_PERSON);
        camDist(ctx, server, 4);
        settle(server, cx, z0(), SOUTH);
    }

    /** 내려베기: 부채꼴 · 칼을 듦 → 내려침 (참격 · 금 · 균열) → 빈틈: 가까이 가서 진짜 클릭 → 분홍 "빈틈" 숫자 */
    private void cleave(ClientGameTestContext ctx, TestServerContext server) {
        double z = z0();
        settle(server, cx, z, SOUTH);
        look(ctx, server, cx + 1.2, ALTAR_Y, z + 8.4, cx, ALTAR_Y + 2.6, z + 1);
        ctx.waitTicks(4);
        int w = windup(server, DEF.obj("cleave").num("windup"));
        long t0 = start(server, e -> BossVoidKnight.cleaveAt(e, new Vec3(0, 0, 1)), "cleave starts");
        waitUntil(ctx, server, t0 + 10);
        shot(ctx, "cleave_windup");
        waitUntil(ctx, server, t0 + w + 1);
        shot(ctx, "cleave_strike");
        look(ctx, server, cx + 0.6, ALTAR_Y, z + 3.2, cx, ALTAR_Y + 2.0, z);
        waitUntil(ctx, server, t0 + w + 8);
        float hp0 = server.computeOnServer(s -> enc().boss.getHealth());
        attack(ctx);
        ctx.waitTicks(2);
        float hp1 = server.computeOnServer(s -> enc().boss.getHealth());
        check(hp1 < hp0, "hit during the opening lands (" + hp0 + " → " + hp1 + ")");
        List<String> nums = numbersNear(server, 8);
        // 0.34.0: 숫자 앞 "빈틈" 표시는 숨김 (BOSS_HINTS.marks) — 받는 피해 +30% 는 그대로
        check(!nums.isEmpty() && nums.stream().noneMatch(t -> t.contains("빈틈")), "damage number without the opening mark (hidden) " + nums);
        shot(ctx, "opening");
        waitUntil(ctx, server, t0 + w + (int) DEF.obj("cleave").num("open") + 14);
        settle(server, cx, z, SOUTH);
    }

    /** 돌진 찌르기: 보라 긴 줄 · 칼을 뒤로 → 줄을 따라 찔러 나감 (옆에서 봄) */
    private void thrust(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz - 6, SOUTH);
        // 서쪽 멀리서 줄 전체 (왼쪽 → 오른쪽으로 찔러 나감 — 오른쪽의 든 칼에 가리지 않게)
        look(ctx, server, cx - 14, ALTAR_Y, cz + 0.5, cx, ALTAR_Y + 2.0, cz + 3);
        ctx.waitTicks(4);
        int w = windup(server, DEF.obj("thrust").num("windup"));
        long t0 = start(server, e -> BossVoidKnight.thrustAt(e, new Vec3(0, 0, 1), 13), "thrust starts");
        waitUntil(ctx, server, t0 + 9);
        shot(ctx, "thrust_windup");
        waitUntil(ctx, server, t0 + w + 2);
        shot(ctx, "thrust_lunge");
        waitUntil(ctx, server, t0 + w + (int) DEF.obj("thrust").num("time") + 1);
        Vec3 b = server.computeOnServer(s -> enc().boss.position());
        check(Math.abs(b.z - (cz + 7)) < 0.6, String.format(Locale.ROOT, "lunged to the end of the line (z %.2f)", b.z - cz));
        shot(ctx, "thrust_end");
        ctx.waitTicks(16);
    }

    /** 도약 내려찍기: 떨어질 원 · 웅크림 → 공중 → 내려찍음 (충격파 · 균열) */
    private void leap(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz - 9, SOUTH);
        Vec3 spot = new Vec3(cx, ALTAR_Y, cz + 2);
        look(ctx, server, cx - 7, ALTAR_Y, cz + 7, cx, ALTAR_Y + 3, cz - 2);
        ctx.waitTicks(4);
        int w = windup(server, DEF.obj("leap").num("windup"));
        int jump = Math.min(w - 2, (int) DEF.obj("leap").num("jump"));
        long t0 = start(server, e -> BossVoidKnight.leapTo(e, spot), "leap starts");
        waitUntil(ctx, server, t0 + 5);
        shot(ctx, "leap_windup");
        waitUntil(ctx, server, t0 + w - jump + jump / 2);
        double h = server.computeOnServer(s -> BossVoidKnight.heightOf(enc()));
        check(h > 2, "airborne (" + String.format(Locale.ROOT, "%.2f", h) + ")");
        shot(ctx, "leap_air");
        waitUntil(ctx, server, t0 + w + 1);
        shot(ctx, "leap_slam");
        ctx.waitTicks((int) DEF.obj("leap").num("open") + 14);
        settle(server, cx, z0(), SOUTH);
    }

    /** 회전 베기: 몸 둘레 원 · 몸을 비틂 → 한 바퀴 (각성 뒤에는 공허 검기 여섯 줄 — 위에서 봄) */
    private void spin(ClientGameTestContext ctx, TestServerContext server, String name) {
        double z = z0();
        settle(server, cx, z, SOUTH);
        boolean top = name.equals("spin2");
        // 각성 뒤 회전 베기는 위에서 (잠깐 관전 — 떨어지지 않게, 싸우는 사람이 없는 시간이 짧게)
        if (top) server.runCommand("gamemode spectator @a");
        if (top) look(ctx, server, cx + 9, ALTAR_Y + 12, z + 9, cx, ALTAR_Y, z);
        else look(ctx, server, cx + 1.5, ALTAR_Y, z + 9.5, cx, ALTAR_Y + 2.4, z);
        ctx.waitTicks(4);
        int w = windup(server, DEF.obj("spin").num("windup"));
        long t0 = start(server, BossVoidKnight::spinAt, name + " starts");
        waitUntil(ctx, server, t0 + 12);
        shot(ctx, name + "_windup");
        waitUntil(ctx, server, t0 + w + (top ? 6 : 2));
        if (top) check(server.computeOnServer(s -> BossVoidKnight.waveCount(enc())) >= 6, "six void waves fly out");
        shot(ctx, name);
        if (top) {
            server.runCommand("gamemode survival @a");
            look(ctx, server, cx + 1.5, ALTAR_Y, z + 9.5, cx, ALTAR_Y + 2.4, z);
        }
        ctx.waitTicks((int) DEF.obj("spin").num("open") + 14);
    }

    /** 균열 걸음: 사라짐 (균열) → 내 등 뒤 균열 (앞에서 본 3인칭) → 나와 겨눔 → 가로 베기 */
    private void blink(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz - 5, SOUTH);
        Obj B = DEF.obj("blink");
        int vanish = (int) B.num("vanish");
        int hidden = (int) B.num("hidden");
        int w = windup(server, B.num("windup"));
        look(ctx, server, cx, ALTAR_Y, cz + 5, cx, ALTAR_Y + 2.4, cz - 5);
        ctx.waitTicks(4);
        long t0 = start(server, e -> BossVoidKnight.blink(e, Tasks.now(), BossKit.fighters(e)), "blink starts");
        waitUntil(ctx, server, t0 + 5);
        shot(ctx, "blink_vanish");
        camera(ctx, CameraType.THIRD_PERSON_FRONT);
        camDist(ctx, server, 6);
        waitUntil(ctx, server, t0 + vanish + 8);
        boolean hiddenOk = server.computeOnServer(s -> enc().boss.isInvisible() && enc().immune);
        check(hiddenOk, "in the rift: invisible + immune");
        shot(ctx, "blink_rift");
        waitUntil(ctx, server, t0 + vanish + hidden + 5);
        shot(ctx, "blink_appear");
        waitUntil(ctx, server, t0 + vanish + hidden + w + 1);
        shot(ctx, "blink_slash");
        camera(ctx, CameraType.FIRST_PERSON);
        camDist(ctx, server, 4);
        ctx.waitTicks(16);
        settle(server, cx, z0(), SOUTH);
    }

    /** 3연격: 한 걸음씩 세 번 (부채꼴이 차례로) — 3인칭 뒤에서 */
    private void combo(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz - 3, SOUTH);
        // 3인칭 뒤 — 플레이어를 오른쪽 아래로 (기사의 몸이 플레이어에게 가리지 않게): 기사 왼쪽 발밑을 보며 조금 높이서
        chase(ctx, server, cx + 1.6, cz + 3.2, cx - 2.5, ALTAR_Y + 0.4, cz - 3, 7);
        ctx.waitTicks(4);
        Obj C = DEF.obj("combo");
        List<Double> ws = C.numbersOrNull("windup");
        int w0 = windup(server, ws.get(0));
        int w1 = windup(server, ws.get(1));
        int w2 = windup(server, ws.get(2));
        long t0 = start(server, e -> BossVoidKnight.combo(e, Tasks.now(), BossKit.fighters(e)), "combo starts");
        waitUntil(ctx, server, t0 + 8);
        shot(ctx, "combo1");
        waitUntil(ctx, server, t0 + w0 + 4);
        shot(ctx, "combo2");
        waitUntil(ctx, server, t0 + w0 + w1 + 8);
        shot(ctx, "combo3_ready");
        // 셋째는 기사가 바로 앞 (1.8칸) 이라 기사 가슴을 올려다봄 (시선만 — 플레이어는 그 자리, 셋째의 방향은 이미 정해짐)
        Vec3 kb = server.computeOnServer(s -> enc().boss.position());
        Vec3 eye = server.computeOnServer(s -> player(s).getEyePosition());
        double ddx = kb.x - eye.x;
        double ddz = kb.z - eye.z;
        ctx.getInput().lookAt((float) Math.toDegrees(Math.atan2(-ddx, ddz)), (float) Math.toDegrees(Math.atan2(-(ALTAR_Y + 2.6 - eye.y), Math.hypot(ddx, ddz))));
        waitUntil(ctx, server, t0 + w0 + w1 + w2 + 1);
        shot(ctx, "combo3");
        camera(ctx, CameraType.FIRST_PERSON);
        camDist(ctx, server, 4);
        ctx.waitTicks((int) C.num("open") + 14);
        settle(server, cx, z0(), SOUTH);
    }

    /** 각성: 포효 (칼날 문양 · 균열 여섯 · 충격파) → 빛이 센 텍스처 (rpg:phase 1) · 보랏빛 안개 */
    private void awaken(ClientGameTestContext ctx, TestServerContext server) {
        double z = z0();
        settle(server, cx, z, SOUTH);
        look(ctx, server, cx + 2, ALTAR_Y, z + 12, cx, ALTAR_Y + 2.8, z);
        ctx.waitTicks(4);
        int roar = (int) DEF.obj("phase2").num("roar");
        long t0 = start(server, e -> {
            BossVoidKnight.awaken(e, Tasks.now());
            return true;
        }, "awakening");
        waitUntil(ctx, server, t0 + 14);
        shot(ctx, "awaken_roar");
        waitUntil(ctx, server, t0 + roar + 12);
        freeze(server);
        // 포효가 끝나면 다시 움직이므로 (달려듦) 세워 둠
        settle(server, cx, z, SOUTH);
        double phase = clientProp(ctx, bossId(server), "rpg:phase");
        check(Math.round(phase) == 1, "awakened texture on the client (rpg:phase " + phase + ")");
        look(ctx, server, cx + 2.4, ALTAR_Y, z + 8.5, cx, ALTAR_Y + 2.7, z);
        ctx.waitTicks(10);
        shot(ctx, "awakened");
    }

    /** 공허 검기 (각성 뒤 내려베기): 분홍 줄 예고 → 부채꼴 끝에서 날아감 */
    private void waves(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz - 8, SOUTH);
        look(ctx, server, cx + 5, ALTAR_Y, cz + 9, cx, ALTAR_Y + 1.5, cz);
        ctx.waitTicks(4);
        int w = windup(server, DEF.obj("cleave").num("windup"));
        long t0 = start(server, e -> BossVoidKnight.cleaveAt(e, new Vec3(0, 0, 1)), "awakened cleave");
        waitUntil(ctx, server, t0 + 8);
        shot(ctx, "wave_mark");
        waitUntil(ctx, server, t0 + w + 4);
        check(server.computeOnServer(s -> BossVoidKnight.waveCount(enc())) >= 1, "void wave flies");
        shot(ctx, "wave_fly");
        ctx.waitTicks((int) DEF.obj("cleave").num("open") + 14);
    }

    /** 공허 참: 가운데로 뛰어 칼을 세움 · 금빛 안전 원 넷 (빛기둥) · 보랏빛 바닥 → 3 2 1 → 십자 참격 (안전 원 안에서 봄 · 위에서 한 장) */
    private void cross(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz - 4, SOUTH);
        Obj C = DEF.obj("cross");
        double k = C.num("dist") / Math.sqrt(2);
        // 무작위 0 → 첫 안전 원은 45° (남동쪽) — 그 원 안에서 가운데를 봄
        look(ctx, server, cx + k + 0.4, ALTAR_Y, cz + k + 0.4, cx, ALTAR_Y + 3.2, cz);
        ctx.waitTicks(4);
        int jump = (int) C.num("jump");
        int w = (int) C.num("windup");
        long[] t = {0};
        boolean r = server.computeOnServer(s -> {
            boolean[] ok = {false};
            withRand(new double[] {0.0}, () -> {
                Bosses.Enc e = enc();
                e.cast = null;
                t[0] = Tasks.now();
                ok[0] = BossVoidKnight.cross(e, t[0], BossKit.fighters(e));
            });
            return ok[0];
        });
        check(r, "void cross starts");
        long t0 = t[0];
        waitUntil(ctx, server, t0 + jump + 8);
        shot(ctx, "cross_safe");
        // 위에서 한 장 (잠깐 관전 — 싸우는 사람이 없는 시간이 짧게)
        server.runCommand("gamemode spectator @a");
        look(ctx, server, cx + 0.5, ALTAR_Y + 30, cz + 6, cx, ALTAR_Y, cz);
        ctx.waitTicks(8);
        shot(ctx, "cross_top");
        server.runCommand("gamemode survival @a");
        look(ctx, server, cx + k + 0.4, ALTAR_Y, cz + k + 0.4, cx, ALTAR_Y + 3.2, cz);
        waitUntil(ctx, server, t0 + jump + w - 18);
        shot(ctx, "cross_count");
        waitUntil(ctx, server, t0 + jump + w + 2);
        shot(ctx, "cross_strike");
        ctx.waitTicks((int) C.num("open") + 18);
        settle(server, cx, z0(), SOUTH);
    }

    /** 공허 붕괴 (제한 시간을 당겨 봄 — 크리에이티브라 즉사하지 않음): 막대 붉게 · 10 → 구멍이 커지며 붕괴 → 시계를 되돌림 */
    private void collapse(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz, SOUTH);
        server.runCommand("gamemode creative @a");
        look(ctx, server, cx + 3, ALTAR_Y, cz + 14, cx, ALTAR_Y + 4, cz);
        ctx.waitTicks(4);
        long[] shift = {0};
        long t0 = server.computeOnServer(s -> {
            shift[0] = BossVoidKnight.timeLeft(enc(), Tasks.now()) - 30;
            BossVoidKnight.shiftClock(enc(), shift[0]);
            return Tasks.now();
        });
        waitUntil(ctx, server, t0 + 11);
        shot(ctx, "collapse_timer");
        int wu = (int) DEF.obj("enrage").num("windup");
        waitUntil(ctx, server, t0 + 30 + wu / 2);
        boolean collapsing = server.computeOnServer(s -> enc().cast != null && "collapse".equals(enc().cast.id));
        check(collapsing, "void collapse after the limit");
        shot(ctx, "collapse");
        waitUntil(ctx, server, t0 + 30 + wu + 2);
        shot(ctx, "collapse_strike");
        // 당긴 만큼 되돌림 (처치 알림의 처치 시간이 실제 걸린 시간이 되게)
        server.runOnServer(s -> BossVoidKnight.shiftClock(enc(), -shift[0]));
        waitUntil(ctx, server, t0 + 30 + wu + 24);
        server.runCommand("gamemode survival @a");
        server.runCommand("effect clear @a minecraft:darkness");
        freeze(server);
    }
}
