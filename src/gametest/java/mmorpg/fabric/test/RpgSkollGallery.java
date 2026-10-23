package mmorpg.fabric.test;

import java.util.List;
import java.util.Locale;
import mmorpg.core.Obj;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.boss.BossKit;
import mmorpg.fabric.boss.BossSkoll;
import mmorpg.fabric.boss.Bosses;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 5단계 사진첩 (화면 시험, only=skoll): 해와 달의 돌 고리 신전을 짓고 진짜로 도전 — 제단 누르기 → 도전 창 → 소환진 → 가장 먼 선돌 위에서 울부짖고 가운데로 뛰어내림,
 * 낮 모습 (스콜이 머리를 듦 · 텍스처 0) · 선돌 눈의 빛 · 약점 (잠든 머리 쪽 옆구리 바닥 원 → 진짜 클릭: 노란 "약점" 숫자) · 물어뜯기 해 (주황) · 도약 (원 → 웅크림 → 공중 → 내려찍기) ·
 * 태양 바퀴 (주황 부채꼴 + 초록 원 → 햇살 줄기) · 원거리 견제 낮 (태양 불씨) → 밤 (울부짖음 → 하티가 머리를 듦 · 텍스처 1) · 물어뜯기 달 (파랑) ·
 * 달빛 사냥 (푸른 원 · 덮침 · 서리 자국 → 지침: 하늘색 "지침" 숫자) · 원거리 견제 밤 (달 서리) · 해와 달의 심판 (북쪽으로 물러남 · 표식 · 붉은/초록 고리 · 원판 테 · 3 2 1 → 판결) ·
 * 황혼 (둘 다 머리를 듦 · 텍스처 2) · 쓰러뜨림 (보상 · 가방). 패턴은 모듈 함수를 바로 부르고, 그 사이 보스는 틀 pause 로 멈춤 (몸은 BossSkoll.setBody).
 * 사진 이름 sk_* → ci-logs 의 shots/.
 */
@SuppressWarnings("UnstableApiUsage")
public final class RpgSkollGallery extends RpgDriveGallery {
    private static final Logger LOG = LoggerFactory.getLogger("mmorpg-skoll-gallery");
    private static final double SOUTH = Math.PI / 2;
    /** 설정 (BOSSES.skoll_hati — 시험 스레드에서 읽음) */
    private static final Obj DEF = Arena.BOSSES.obj("skoll_hati");

    @Override
    String key() {
        return "skoll_hati";
    }

    @Override
    String only() {
        return "skoll";
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
        return "sk";
    }

    @Override
    void scenes(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn) {
        if (!arrive(ctx, server)) return;
        dayLook(ctx, server);
        stones(ctx, server);
        weak(ctx, server);
        biteSun(ctx, server);
        pounce(ctx, server);
        wheel(ctx, server);
        far(ctx, server, "day");
        night(ctx, server);
        biteMoon(ctx, server);
        hunt(ctx, server);
        far(ctx, server, "night");
        judge(ctx, server);
        dusk(ctx, server);
        settle(server, cx, cz, SOUTH);
        kill(ctx, server, cx, cz + 4.2, cx, ALTAR_Y + 1.8, cz, 28);
    }

    // ------------------------------------------------------------ 도우미

    /** 스스로 패턴 · 낮밤 바뀜 · 원거리 견제를 고르지 않게 */
    private void freeze(TestServerContext server) {
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            BossSkoll.M m = BossSkoll.mem(e);
            if (m == null) return;
            m.next.replaceAll((k, v) -> FAR_FUTURE);
            m.bigUntil = FAR_FUTURE;
            m.stance.next = FAR_FUTURE;
            m.judgeDone.add(0.7);
            m.judgeDone.add(0.25);
            e.farAt = FAR_FUTURE;
        });
    }

    /** 몸을 (x, z) 땅 위에 ang 쪽을 보게 두고 멈춤 (틀 pause) */
    private void settle(TestServerContext server, double x, double z, double ang) {
        server.runOnServer(s -> {
            Bosses.Enc e = enc();
            BossSkoll.M m = BossSkoll.mem(e);
            if (m == null) return;
            m.leap = null;
            m.body.want = null;
            BossSkoll.setBody(e, x, z, ang);
            Bosses.cast(e, "pause", 1_000_000);
        });
    }

    private long start(TestServerContext server, java.util.function.Function<Bosses.Enc, Boolean> run, String what) {
        long[] t = {0};
        boolean r = server.computeOnServer(s -> {
            t[0] = Tasks.now();
            return run.apply(enc());
        });
        check(r, what);
        return t[0];
    }

    /** 클라이언트 모습 (rpg:day · rpg:dusk · rpg:phase) */
    private String lookOf(ClientGameTestContext ctx, int id) {
        return String.format(Locale.ROOT, "day %.2f dusk %.2f phase %d", clientProp(ctx, id, "rpg:day"), clientProp(ctx, id, "rpg:dusk"),
                (int) Math.round(clientProp(ctx, id, "rpg:phase")));
    }

    // ------------------------------------------------------------ 장면

    /** 등장: 깨운 사람에게서 가장 먼 선돌 위 (5칸) 에서 울부짖음 1초 → 포물선으로 가운데 원판에 내려앉음 (금빛 충격파 · 눈보라) */
    private boolean arrive(ClientGameTestContext ctx, TestServerContext server) {
        for (int t = 0; t < 60 && !server.computeOnServer(s -> enc() != null && "fight".equals(enc().state)); t++) ctx.waitTick();
        long t0 = now(server);
        int id = bossId(server);
        check(id >= 0, "skoll and hati spawned");
        if (id < 0) return false;
        Vec3 b = server.computeOnServer(s -> enc().boss.position());
        check(b.y - ALTAR_Y > 4.9, String.format(Locale.ROOT, "on top of a standing stone (y +%.1f)", b.y - ALTAR_Y));
        // 선돌 위의 늑대 (가운데 쪽에서 올려다봄)
        Vec3 toC = new Vec3(cx - b.x, 0, cz - b.z).normalize();
        look(ctx, server, b.x + toC.x * 10, ALTAR_Y, b.z + toC.z * 10, b.x, b.y + 1.5, b.z);
        waitUntil(ctx, server, t0 + 10);
        shot(ctx, "arrive");
        checkDrawn(ctx, server);
        look(ctx, server, cx + 8, ALTAR_Y, cz + 10, (cx + b.x) / 2, ALTAR_Y + 4, (cz + b.z) / 2);
        waitUntil(ctx, server, t0 + 29);
        shot(ctx, "leap");
        waitUntil(ctx, server, t0 + 41);
        shot(ctx, "land");
        String r = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            if (BossSkoll.leapOf(e) != null) return "still leaping";
            if (BossKit.hdist(e.boss.position(), new Vec3(cx, ALTAR_Y, cz)) > 0.01 || Math.abs(e.boss.getY() - ALTAR_Y) > 1e-6) return "not at the center " + e.boss.position();
            return e.boss.getHealth() == 27000F ? "ok" : "hp " + e.boss.getHealth();
        });
        check("ok".equals(r), "landed on the center disc with 27000 hp (" + r + ")");
        ctx.waitTicks(16);
        freeze(server);
        return true;
    }

    /** 낮 모습: 스콜 (몸 왼쪽, 검은 머리) 이 머리를 들고 하티 (오른쪽, 흰 머리) 는 잠듦 — 텍스처 0 (하티의 빛이 꺼짐), 잠든 쪽 옆구리 바닥 원 */
    private void dayLook(ClientGameTestContext ctx, TestServerContext server) {
        int id = bossId(server);
        settle(server, cx, cz, SOUTH);
        server.runCommand("title @a clear");
        look(ctx, server, cx + 1.5, ALTAR_Y, cz + 9, cx, ALTAR_Y + 3, cz);
        ctx.waitTicks(12);
        String l = lookOf(ctx, id);
        check(l.equals("day 1.00 dusk 0.00 phase 0"), "day look on the client (" + l + ")");
        shot(ctx, "day");
    }

    /** 선돌 눈의 빛: 깨어난 머리 쪽 (낮 = 동쪽 해 선돌 넷) 선돌의 눈에서 빛이 오름 (2초마다) */
    private void stones(ClientGameTestContext ctx, TestServerContext server) {
        server.runCommand("gamemode spectator @a");
        camera(ctx, CameraType.FIRST_PERSON);
        look(ctx, server, cx - 6, ALTAR_Y + 5, cz, cx + 19, ALTAR_Y + 2.5, cz);
        long n = now(server);
        waitUntil(ctx, server, n + (40 - n % 40) + 6);
        shot(ctx, "stones");
        server.runCommand("gamemode survival @a");
    }

    /** 약점: 낮에는 하티 (오른쪽 — 남쪽을 보면 서쪽) 쪽 옆구리 → 진짜 클릭: 노란 "약점" 숫자 (+30%) */
    private void weak(ClientGameTestContext ctx, TestServerContext server) {
        int id = bossId(server);
        settle(server, cx, cz, SOUTH);
        // 3인칭 뒤 5칸 거의 수평 — 조준점은 보스 몸 (사거리 안), 옆구리 바닥 원과 숫자가 함께 보임
        chase(ctx, server, cx - 4.2, cz, cx, ALTAR_Y + 1.8, cz, 5);
        check(server.computeOnServer(s -> BossSkoll.onFlank(enc(), player(s).position())), "standing on the weak (Hati) flank");
        float hp0 = server.computeOnServer(s -> enc().boss.getHealth());
        ctx.waitTicks(25);
        attack(ctx);
        ctx.waitTicks(2);
        float hp1 = server.computeOnServer(s -> enc().boss.getHealth());
        check(hp1 < hp0, "flank hit lands (" + hp0 + " → " + hp1 + ")");
        List<String> nums = numbersNear(server, 7);
        // 0.34.0: 숫자 앞 "약점" 표시는 숨김 (BOSS_HINTS.marks) — 피해 ×1.3 은 그대로
        check(!nums.isEmpty() && nums.stream().noneMatch(t -> t.contains("약점")), "damage number without the weak mark (hidden) " + nums);
        shot(ctx, "weak");
        ctx.waitTicks(12);
    }

    /** 물어뜯기 (낮 — 스콜): 앞 80° 주황 부채꼴 0.7초 → 물기 (불) */
    private void biteSun(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz, SOUTH);
        chase(ctx, server, cx + 1, cz + 4.5, cx, ALTAR_Y, cz + 1.5, 8);
        long t0 = start(server, e -> BossSkoll.bite(e, Tasks.now(), BossKit.fighters(e)), "sun bite cast");
        waitUntil(ctx, server, t0 + 8);
        shot(ctx, "bite_sun_tele");
        waitUntil(ctx, server, t0 + 15);
        shot(ctx, "bite_sun");
        ctx.waitTicks(10);
    }

    /** 도약: 가장 먼 사람 발밑 원 (3.4칸) · 웅크림 1.1초 → 0.6초 동안 높이 4칸 포물선 → 앞발로 내려찍음 */
    private void pounce(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz - 4, SOUTH);
        chase(ctx, server, cx + 1, cz + 8, cx, ALTAR_Y + 1, cz + 1, 9);
        long t0 = start(server, e -> BossSkoll.pounce(e, Tasks.now(), BossKit.fighters(e)), "pounce on the far player");
        int windup = (int) DEF.obj("pounce").num("windup");
        int time = (int) DEF.obj("pounce").num("time");
        waitUntil(ctx, server, t0 + windup - 6);
        shot(ctx, "pounce_tele");
        waitUntil(ctx, server, t0 + windup + time / 2);
        double h = server.computeOnServer(s -> enc().boss.getY() - ALTAR_Y);
        check(h > 2, String.format(Locale.ROOT, "in the air mid-pounce (+%.1f)", h));
        shot(ctx, "pounce");
        waitUntil(ctx, server, t0 + windup + time + 1);
        shot(ctx, "pounce_land");
        ctx.waitTicks(12);
    }

    /** 태양 바퀴: 몸 위에 해 · 줄기 넷이 쓸고 갈 주황 부채꼴 + 몸 곁 초록 원 1.6초 → 햇살 줄기가 45° 돎 */
    private void wheel(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz, SOUTH);
        // 줄기 72° · 162° · 252° · 342° 가 +45° 돎 → 49.5° 쪽 7.6칸 = 부채꼴 사이 (안전)
        double px = cx + 4.94;
        double pz = cz + 5.78;
        look(ctx, server, px, ALTAR_Y, pz, cx, ALTAR_Y + 2, cz);
        long[] t = {0};
        boolean r = server.computeOnServer(s -> {
            boolean[] got = {false};
            withRand(new double[] {0.2, 0.0}, () -> {
                t[0] = Tasks.now();
                got[0] = BossSkoll.wheel(enc(), Tasks.now(), BossKit.fighters(enc()));
            });
            return got[0];
        });
        check(r, "sun wheel cast");
        int windup = (int) DEF.obj("wheel").num("windup");
        // 높이에서 본 부채꼴 넷 + 초록 원 (관전으로 잠깐)
        server.runCommand("gamemode spectator @a");
        camera(ctx, CameraType.FIRST_PERSON);
        look(ctx, server, cx, ALTAR_Y + 26, cz + 13, cx, ALTAR_Y, cz);
        waitUntil(ctx, server, t[0] + 18);
        shot(ctx, "wheel_tele");
        server.runCommand("gamemode survival @a");
        chase(ctx, server, px, pz, cx, ALTAR_Y + 1, cz, 10);
        waitUntil(ctx, server, t[0] + windup + 22);
        shot(ctx, "wheel");
        waitUntil(ctx, server, t[0] + windup + 50);
    }

    /** 원거리 견제: 낮 = 스콜의 입에서 태양 불씨 (주황 원, 불) · 밤 = 하티의 입에서 달 서리 (짙은 파랑 원, 느림 II) */
    private void far(ClientGameTestContext ctx, TestServerContext server, String when) {
        settle(server, cx, cz - 5, SOUTH);
        chase(ctx, server, cx, cz + 9, cx, ALTAR_Y + 3, cz - 2, 5);
        long t0 = server.computeOnServer(s -> {
            Bosses.Enc e = enc();
            Bosses.farThrow(e, e, Tasks.now(), List.of(player(s)));
            return Tasks.now();
        });
        waitUntil(ctx, server, t0 + 15);
        shot(ctx, "far_" + when);
        waitUntil(ctx, server, t0 + 31);
        shot(ctx, "far_" + when + "_hit");
        if (when.equals("day")) {
            check(server.computeOnServer(s -> enc().farShots.isEmpty() && player(s).getRemainingFireTicks() > 0), "day far shot (sun ember) landed — burning");
        } else {
            boolean slow = server.computeOnServer(s -> {
                MobEffectInstance m = player(s).getEffect(MobEffects.SLOWNESS);
                return enc().farShots.isEmpty() && m != null && m.getAmplifier() == 1;
            });
            check(slow, "night far shot (moon frost) landed — slowness II");
            server.runCommand("effect clear @a minecraft:slowness");
        }
        ctx.waitTicks(10);
    }

    /** 낮 → 밤: 울부짖음 1.2초 (하티 쪽 푸른 충격파 · 큰 글자) → 하티가 머리를 듦, 텍스처 1 (스콜의 불씨가 식음) */
    private void night(ClientGameTestContext ctx, TestServerContext server) {
        int id = bossId(server);
        settle(server, cx, cz, SOUTH);
        look(ctx, server, cx - 1.5, ALTAR_Y, cz + 10, cx, ALTAR_Y + 3, cz);
        long t0 = server.computeOnServer(s -> {
            BossSkoll.swapStance(enc(), Tasks.now());
            return Tasks.now();
        });
        waitUntil(ctx, server, t0 + 8);
        shot(ctx, "night_howl");
        waitUntil(ctx, server, t0 + 30);
        server.runCommand("title @a clear");
        settle(server, cx, cz, SOUTH);
        ctx.waitTicks(4);
        String l = lookOf(ctx, id);
        check(l.equals("day 0.00 dusk 0.00 phase 1"), "night look on the client (" + l + ")");
        shot(ctx, "night");
    }

    /** 물어뜯기 (밤 — 하티): 파란 부채꼴 → 물기 (느림) */
    private void biteMoon(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz, SOUTH);
        chase(ctx, server, cx - 1, cz + 4.5, cx, ALTAR_Y, cz + 1.5, 8);
        long t0 = start(server, e -> BossSkoll.bite(e, Tasks.now(), BossKit.fighters(e)), "moon bite cast");
        waitUntil(ctx, server, t0 + 8);
        shot(ctx, "bite_moon_tele");
        waitUntil(ctx, server, t0 + 15);
        shot(ctx, "bite_moon");
        server.runCommand("effect clear @a minecraft:slowness");
        ctx.waitTicks(10);
    }

    /** 달빛 사냥: 사냥감 발밑 푸른 원 · 머리 위 달 문양 → 덮침 세 번 (서리 자국) → 지침 2초 (진짜 클릭: 하늘색 "지침" 숫자) */
    private void hunt(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz - 2, SOUTH);
        Obj H = DEF.obj("hunt");
        int windup = (int) H.num("windup");
        int time = (int) H.num("time");
        int cycle = windup + time + 6;
        double px = cx + 2;
        double pz = cz + 8;
        chase(ctx, server, px, pz, cx, ALTAR_Y + 1, cz, 9);
        long t0 = start(server, e -> BossSkoll.hunt(e, Tasks.now(), BossKit.fighters(e)), "moonlight hunt on the player");
        waitUntil(ctx, server, t0 + 16 + windup - 6);
        shot(ctx, "hunt_tele");
        waitUntil(ctx, server, t0 + 16 + windup + time / 2);
        shot(ctx, "hunt");
        // 덮친 뒤 비켜섬: 서리 자국이 남고 다음 원은 새 자리에
        waitUntil(ctx, server, t0 + 16 + windup + time + 2);
        chase(ctx, server, px - 6, pz + 2, px - 3, ALTAR_Y, pz - 1, 8);
        waitUntil(ctx, server, t0 + 16 + cycle + windup - 4);
        shot(ctx, "frost");
        long winded = t0 + 16 + 3 * cycle;
        waitUntil(ctx, server, winded + 1);
        check(server.computeOnServer(s -> BossSkoll.huntOf(enc()) == null && BossSkoll.mem(enc()).windedUntil > Tasks.now()), "hunt over → winded");
        // 지친 동안: 가운데에 세우고 앞에서 진짜 클릭 (옆구리가 아님 — 지침만)
        settle(server, cx, cz, SOUTH);
        server.runOnServer(s -> BossSkoll.mem(enc()).windedUntil = Tasks.now() + 60);
        chase(ctx, server, cx, cz + 4.2, cx, ALTAR_Y + 1.8, cz, 5);
        ctx.waitTicks(20);
        attack(ctx);
        ctx.waitTicks(2);
        List<String> nums = numbersNear(server, 7);
        // 0.34.0: 숫자 앞 "지침" 표시는 숨김 (BOSS_HINTS.marks) — 피해 ×1.25 는 그대로
        check(!nums.isEmpty() && nums.stream().noneMatch(t -> t.contains("지침")), "damage number without the winded mark (hidden) " + nums);
        shot(ctx, "winded");
        server.runCommand("effect clear @a minecraft:slowness");
        server.runOnServer(s -> BossSkoll.mem(enc()).windedUntil = 0);
        ctx.waitTicks(10);
    }

    /** 해와 달의 심판: 북쪽 가장자리로 물러나 무적 · 하늘로 울부짖음 → 표식 (해 = 동쪽 금빛 반원) · 발밑 고리 (붉게 → 제 반원 위 초록) · 원판 테 · 3 2 1 → 판결 */
    private void judge(ClientGameTestContext ctx, TestServerContext server) {
        settle(server, cx, cz, SOUTH);
        chase(ctx, server, cx, cz + 6, cx, ALTAR_Y + 1, cz - 4, 9);
        long t0 = server.computeOnServer(s -> {
            BossSkoll.judge(enc(), Tasks.now(), BossKit.fighters(enc()), false);
            return Tasks.now();
        });
        Obj J = DEF.obj("judge");
        int markAt = (int) J.num("leap") + 6;
        int windup = (int) J.num("windup");
        waitUntil(ctx, server, t0 + 10);
        shot(ctx, "judge_retreat");
        waitUntil(ctx, server, t0 + markAt + 2);
        String side = server.computeOnServer(s -> {
            BossSkoll.Judge G = BossSkoll.judgeOf(enc());
            return G == null || G.marks.isEmpty() ? null : G.marks.get(0).side;
        });
        check(side != null, "marked (" + side + ")");
        if (side == null) return;
        boolean immune = server.computeOnServer(s -> enc().immune);
        check(immune, "immune during the judgement");
        // 먼저 반대 반원 (붉은 고리) → 제 반원 (초록 고리)
        double wrong = side.equals("sun") ? -3 : 3;
        server.runCommand("title @a clear");
        chase(ctx, server, cx + wrong, cz + 1, cx, ALTAR_Y + 1, cz - 6, 8);
        waitUntil(ctx, server, t0 + markAt + 24);
        shot(ctx, "judge_wrong");
        chase(ctx, server, cx - wrong, cz + 1, cx, ALTAR_Y + 1, cz - 6, 8);
        waitUntil(ctx, server, t0 + markAt + 44);
        shot(ctx, "judge");
        // 위에서 본 원판 (해 = 동쪽 금빛, 달 = 서쪽 푸른) 과 테 · 표식
        waitUntil(ctx, server, t0 + markAt + windup - 18);
        shot(ctx, "judge_count");
        waitUntil(ctx, server, t0 + markAt + windup + 1);
        shot(ctx, "judge_verdict");
        String r = server.computeOnServer(s -> BossSkoll.judgeOf(enc()) == null && !enc().immune && player(s).isAlive() && !player(s).isDeadOrDying() ? "ok" : "bad");
        check("ok".equals(r), "verdict: own half survives, not immune any more");
        ctx.waitTicks(40);
    }

    /** 황혼: 두 머리가 함께 울부짖음 (보라 충격파) → 둘 다 머리를 듦, 텍스처 2 (둘 다 빛남) */
    private void dusk(ClientGameTestContext ctx, TestServerContext server) {
        int id = bossId(server);
        settle(server, cx, cz, SOUTH);
        look(ctx, server, cx, ALTAR_Y, cz + 10, cx, ALTAR_Y + 3, cz);
        long t0 = server.computeOnServer(s -> {
            BossSkoll.startDusk(enc(), Tasks.now());
            return Tasks.now();
        });
        waitUntil(ctx, server, t0 + 10);
        shot(ctx, "dusk_howl");
        waitUntil(ctx, server, t0 + 46);
        server.runCommand("title @a clear");
        settle(server, cx, cz, SOUTH);
        ctx.waitTicks(4);
        String l = lookOf(ctx, id);
        check(l.equals("day 0.00 dusk 1.00 phase 2"), "dusk look on the client (" + l + ")");
        shot(ctx, "dusk");
    }
}
