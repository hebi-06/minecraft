package mmorpg.fabric.test;

import java.util.List;
import java.util.function.Supplier;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Props;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.nav.Nav;
import mmorpg.fabric.nav.NavMath;
import mmorpg.fabric.nav.NavNet;
import mmorpg.fabric.npc.Dummy;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.npc.TrainingDummy;
import mmorpg.fabric.quest.MainQuests;
import mmorpg.fabric.quest.Quest;
import mmorpg.fabric.quest.QuestTalk;
import mmorpg.fabric.quest.Quests;
import mmorpg.fabric.ui.FormNet;
import mmorpg.fabric.ui.Forms;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.phys.Vec3;

/**
 * 길 안내 시험 (nav.js — 서버, 클라이언트 없이): 순수 계산 (angleOf · sectorFor 경계 머무름 · distText · 높이 글자 · facing · 그림 이름),
 * 목적지 고르기 (NPC 지금 자리 · 옮기면 기록도 · 불러오지 않은 NPC 는 적어 둔 자리 · 가까이 왔는데 NPC 가 없음 · 적어 둔 적 없음 · 허수아비 ·
 * 5단계 훅: 사냥터 · 보스 제단 · 싸우는 보스 필드 근처는 숨김), 화면 칸 (화살표 → 도착 → 떠남, 처치 퀘스트 "사냥터 없음"),
 * 퀘스트를 받으면 바로 다시 정함 (Quests.onNavChange), /nav · /길안내 끄고 켜기 (끄면 숨김).
 */
public final class RpgNavTests {
    private static ServerPlayer player(GameTestHelper h) {
        return RpgGearTests.player(h);
    }

    private static void done(GameTestHelper h, ServerPlayer sp) {
        RpgGearTests.done(h, sp);
    }

    /** 시험 자리 rel 에 RPG NPC (마네킹) 를 세움 */
    private static Mannequin npc(GameTestHelper h, Vec3 rel, String name) {
        Mannequin m = NpcData.spawnAt(h.getLevel(), h.absoluteVec(rel), null);
        NpcData.setName(m, name);
        NpcData.refresh(m, 0);
        return m;
    }

    private static Quest quest(String id, String title) {
        Quest q = new Quest();
        q.id = id;
        q.title = title;
        return q;
    }

    /** 이 NPC 에게 말 걸기 (일반 하늘빛) */
    private static Quests.NavGoal talkGoal(String npcKey) {
        return new Quests.NavGoal(quest("nav_test", "길 안내 시험"), false, "talk", npcKey, null, List.of(), null, null, 0, 0, 0);
    }

    private static String dim(GameTestHelper h) {
        return NpcData.dimId(h.getLevel());
    }

    /** 다시 정하고 한 번 돌림 (서버 틱 2틱마다와 같은 일) */
    private static NavNet.NavPayload tick(ServerPlayer sp) {
        Nav.refreshNav(sp);
        Nav.tickPlayer(sp, Tasks.now());
        return Nav.hud(sp);
    }

    /** 다시 정하지 않고 한 번 (도착 · 떠남은 목적지를 그대로 두고 자리만 봄) */
    private static NavNet.NavPayload tickOnly(ServerPlayer sp) {
        Nav.tickPlayer(sp, Tasks.now());
        return Nav.hud(sp);
    }

    private static String plain(NavNet.NavPayload p) {
        return p == null ? "(없음)" : BedrockText.plain(p.text());
    }

    private static void moveTo(GameTestHelper h, ServerPlayer sp, Vec3 rel) {
        Vec3 at = h.absoluteVec(rel);
        sp.snapTo(at.x, at.y, at.z, 0F, 0F);
    }

    // ------------------------------------------------------------ 순수 계산

    /** 화살표 방향 (내가 보는 쪽에서 시계 방향 — 북쪽을 보면 동쪽이 오른쪽) · 16칸 · 경계 머무름 · 거리 · 높이 글자 · 그림 이름 */
    @GameTest(maxTicks = 20)
    public void navMath(GameTestHelper h) {
        Vec3 o = Vec3.ZERO;
        Vec3 north = new Vec3(0, 0, -1);
        h.assertTrue(Math.abs(NavMath.angleOf(north, o, new Vec3(10, 0, 0)) - 90) < 1e-9, "east is right " + NavMath.angleOf(north, o, new Vec3(10, 0, 0)));
        h.assertTrue(Math.abs(NavMath.angleOf(north, o, new Vec3(-10, 5, 0)) + 90) < 1e-9, "west is left (height ignored)");
        h.assertTrue(Math.abs(NavMath.angleOf(north, o, new Vec3(0, 0, -3))) < 1e-9, "north is ahead");
        h.assertTrue(Math.abs(Math.abs(NavMath.angleOf(north, o, new Vec3(0, 0, 3))) - 180) < 1e-9, "south is behind");
        // 남쪽을 보면 동쪽은 왼쪽 (12), 서쪽은 오른쪽 (4)
        Vec3 south = new Vec3(0, 0, 1);
        h.assertTrue(NavMath.sectorOf(south, o, new Vec3(5, 0, 0)) == 12 && NavMath.sectorOf(south, o, new Vec3(-5, 0, 0)) == 4, "south view sectors");
        h.assertTrue(NavMath.sectorOf(north, o, new Vec3(10, 0, -10)) == 2 && NavMath.sectorOf(north, o, new Vec3(0, 0, 9)) == 8, "north view sectors");
        // 보는 방향의 높낮이는 빼고 (아래를 보아도 같은 칸)
        h.assertTrue(NavMath.sectorOf(new Vec3(0, -0.9, -0.43), o, new Vec3(10, 0, 0)) == 4, "pitched view");
        // 경계 머무름 (11.25 + 4 도)
        h.assertTrue(NavMath.sectorFor(14, 0, 4) == 0 && NavMath.sectorFor(16, 0, 4) == 1, "hold right " + NavMath.sectorFor(16, 0, 4));
        h.assertTrue(NavMath.sectorFor(-15, 0, 4) == 0 && NavMath.sectorFor(-16, 0, 4) == 15, "hold left " + NavMath.sectorFor(-16, 0, 4));
        h.assertTrue(NavMath.sectorFor(179, 8, 4) == 8 && NavMath.sectorFor(-179, 8, 4) == 8 && NavMath.sectorFor(-170, -1, 4) == 8, "wrap behind");
        h.assertTrue(NavMath.sectorFor(12, -1, 4) == 1 && NavMath.sectorFor(11, -1, 4) == 0, "no hold without previous");
        // 거리: 20칸까지 1칸, 100칸까지 5칸, 그 밖 10칸 단위
        String[][] dist = {{"0", "0칸"}, {"7.4", "7칸"}, {"19.4", "19칸"}, {"19.6", "20칸"}, {"22", "20칸"}, {"23", "25칸"}, {"99", "100칸"}, {"104", "100칸"},
            {"105", "110칸"}, {"250", "250칸"}};
        for (String[] d : dist) h.assertTrue(NavMath.distText(Double.parseDouble(d[0])).equals(d[1]), "distText " + d[0] + " → " + NavMath.distText(Double.parseDouble(d[0])));
        h.assertTrue(NavMath.heightText(7, 6).equals(" §7(위 7)") && NavMath.heightText(-6.4, 6).equals(" §7(아래 6)") && NavMath.heightText(5.9, 6).isEmpty(), "height");
        h.assertTrue(NavMath.arrowTail(23, -8, 6).equals(" §7· §f25칸 §7(아래 8)"), "tail " + NavMath.arrowTail(23, -8, 6));
        // 바로 위를 보면 몸 방향 (yaw 90 = 서쪽)
        Vec3 f = NavMath.facing(new Vec3(0, 1, 0), 90F);
        h.assertTrue(Math.abs(f.x + 1) < 1e-9 && Math.abs(f.z) < 1e-9, "facing from yaw " + f);
        h.assertTrue(NavMath.facing(north, 90F) == north, "facing keeps a level view");
        h.assertTrue(NavMath.arrowSprite(1, 3).equals("a_m03") && NavMath.arrowSprite(2, 12).equals("a_c12") && NavMath.hereSprite(2).equals("a_chr"), "sprites");
        h.succeed();
    }

    // ------------------------------------------------------------ 목적지

    /** NPC: 불러와진 NPC 는 지금 자리 (옮기면 기록도 고침) · 이름 · 글자, 화면 칸 화살표 → 다가가면 도착 → 멀어지면 다시 화살표 */
    @GameTest(maxTicks = 40)
    public void navNpcTargetAndArrival(GameTestHelper h) {
        ServerPlayer sp = player(h);
        MinecraftServer server = h.getLevel().getServer();
        Mannequin a = npc(h, new Vec3(1.5, 2, 3.5), "촌장");
        Mannequin b = npc(h, new Vec3(7.5, 2, 1.5), "약초꾼");
        String id = null;
        try {
            String kb = NpcData.key(b);
            Quests.NavGoal g = talkGoal(kb);
            Nav.Target t = Nav.resolve(sp, g);
            h.assertTrue(t != null && t.kind().equals("npc") && t.name().equals("약초꾼") && !t.missing() && dim(h).equals(t.dim()), "npc target " + t);
            h.assertTrue(t.at().distanceTo(b.position()) < 1e-6, "npc at " + t.at() + " vs " + b.position());
            h.assertTrue(Nav.labelOf(g, t).equals("§e약초꾼§f에게"), Nav.labelOf(g, t));
            // 옮김 → 지금 자리, 기록도 고침
            Vec3 moved = h.absoluteVec(new Vec3(7.5, 2, 5.5));
            b.snapTo(moved.x, moved.y, moved.z, 0F, 0F);
            t = Nav.resolve(sp, g);
            h.assertTrue(t.at().distanceTo(moved) < 1e-6, "moved npc " + t.at());
            NpcData.Spot rec = NpcData.pos(server, kb);
            h.assertTrue(rec != null && Math.abs(rec.x() - moved.x) < 0.11 && Math.abs(rec.z() - moved.z) < 0.11, "record moved " + rec);
            b.snapTo(h.absoluteVec(new Vec3(7.5, 2, 1.5)).x, moved.y, h.absoluteVec(new Vec3(7.5, 2, 1.5)).z, 0F, 0F);

            // 만든 말 걸기 퀘스트 (촌장 → 약초꾼) 를 진행 중으로 두고 길 안내로 고름
            Quest q = new Quest();
            q.giver = NpcData.key(a);
            q.title = "약초꾼에게";
            q.type = "talk";
            q.target = kb;
            id = Quests.saveQuest(q).id;
            Props.set(sp, "rpg:qs", "{\"" + id + "\":{\"s\":\"a\",\"n\":0,\"c\":0}}");
            Quests.forgetQuestPlayer(sp.getUUID());
            Quests.pinQuest(sp, id);
            NavNet.NavPayload hud = tick(sp);
            h.assertTrue(hud != null && hud.visible() && hud.frame() == 2 && hud.mode() == NavNet.NavPayload.ARROW, "arrow hud " + hud);
            h.assertTrue(plain(hud).equals("약초꾼에게") && hud.key().contains(id) && hud.key().contains("npc:" + kb) && dim(h).equals(hud.dim()), "hud text " + plain(hud));
            h.assertTrue(Math.abs(hud.x() - b.getX()) < 0.06 && Math.abs(hud.z() - b.getZ()) < 0.06, "hud target " + hud.x() + "," + hud.z());
            // 남쪽을 보는 플레이어: 동쪽 6칸의 약초꾼은 왼쪽 (12) — 클라이언트가 같은 계산 (NavMath)
            int sector = NavMath.sectorOf(sp.getViewVector(1F), sp.position(), new Vec3(hud.x(), hud.y(), hud.z()));
            h.assertTrue(sector == 12, "sector " + sector + " view " + sp.getViewVector(1F));
            // 다가감 (2칸) → 도착
            moveTo(h, sp, new Vec3(5.5, 2, 1.5));
            hud = tickOnly(sp);
            h.assertTrue(hud.mode() == NavNet.NavPayload.HERE && plain(hud).equals("약초꾼에게 · 여기!"), "arrived " + plain(hud) + " mode " + hud.mode());
            // 4칸 + 3 넘게 멀어짐 → 다시 화살표
            moveTo(h, sp, new Vec3(0.5, 2, 7.5));
            hud = tickOnly(sp);
            h.assertTrue(hud.mode() == NavNet.NavPayload.ARROW, "left again " + hud.mode());
        } finally {
            if (id != null) Quests.deleteQuest(id);
            a.discard();
            b.discard();
        }
        done(h, sp);
    }

    /** NPC 가 불러와져 있지 않으면 적어 둔 자리, 적어 둔 자리 가까이 (16칸) 왔는데 없으면 "NPC가 없음", 적어 둔 적이 없으면 위치 모름 */
    @GameTest(maxTicks = 40)
    public void navNpcMissingAndUnloaded(GameTestHelper h) {
        ServerPlayer sp = player(h);
        MinecraftServer server = h.getLevel().getServer();
        Mannequin near = npc(h, new Vec3(6.5, 2, 4.5), "사라진 상인");
        Mannequin far = npc(h, new Vec3(3.5, 2, 6.5), "먼 마을 촌장");
        String kn = NpcData.key(near);
        String kf = NpcData.key(far);
        // 먼 NPC: 200칸 밖 자리를 적어 두고 엔티티는 치움 (불러오지 않은 곳의 NPC 와 같음)
        Vec3 farAt = h.absoluteVec(new Vec3(200.5, 2, 3.5));
        NpcData.notePos(far, kf, farAt, dim(h));
        far.discard();
        near.discard();
        Nav.Target t = Nav.resolve(sp, talkGoal(kn));
        h.assertTrue(t != null && t.missing() && t.at() != null, "missing near npc " + t);
        Nav.Target tf = Nav.resolve(sp, talkGoal(kf));
        h.assertTrue(tf != null && !tf.missing() && tf.at().distanceTo(farAt) < 0.11 && dim(h).equals(tf.dim()), "unloaded npc → noted spot " + tf);
        h.assertTrue(tf.name().equals("먼 마을 촌장"), "name from registry " + tf.name());
        Nav.Target none = Nav.resolve(sp, talkGoal("nav_nobody"));
        h.assertTrue(none != null && none.at() == null && none.dim() == null && none.name().equals("NPC"), "unknown npc " + none);
        // 메인 NPC 는 세우기 전에도 이름 (부르는 이름)
        Nav.Target main = Nav.resolve(sp, talkGoal("main_2"));
        h.assertTrue(main != null && (main.name().equals("훈련 조교 브란") || !NpcData.nameByKey(server, "main_2").isEmpty()), "main npc name " + main);
        NpcData.forget(server, kn);
        NpcData.forget(server, kf);
        done(h, sp);
    }

    /** 무기 시험 (메인) → 48칸 안의 훈련용 허수아비 (금빛), 화살표 → 다가가면 도착 */
    @GameTest(maxTicks = 40)
    public void navTryTargetsDummy(GameTestHelper h) {
        ServerPlayer sp = player(h);
        TrainingDummy d = Dummy.spawnDummy(sp);
        try {
            h.assertTrue(d != null, "dummy spawned");
            Props.set(sp, "rpg:qs", "{\"" + MainQuests.MAIN_START + "\":{\"s\":\"d\",\"n\":0,\"c\":1},\"" + MainQuests.MAIN_TRIAL + "\":{\"s\":\"a\",\"n\":0,\"c\":0,\"t\":[]}}");
            Quests.forgetQuestPlayer(sp.getUUID());
            Quests.pinQuest(sp, MainQuests.MAIN_TRIAL);
            Quests.NavGoal g = Quests.navGoal(sp);
            h.assertTrue(g != null && g.why().equals("try") && g.main() && g.count() == 6, "try goal " + g);
            Nav.Target t = Nav.resolve(sp, g);
            h.assertTrue(t != null && t.kind().equals("dummy") && t.id().equals(d.getUUID().toString()) && t.at().distanceTo(d.position()) < 1e-6, "dummy target " + t);
            // 허수아비는 3칸 앞 (남쪽) — 4칸 넘게 물러나서 화살표
            Vec3 at = d.position();
            sp.snapTo(at.x, at.y, at.z - 4.3, 0F, 0F);
            NavNet.NavPayload hud = tick(sp);
            h.assertTrue(hud.visible() && hud.frame() == 1 && hud.mode() == NavNet.NavPayload.ARROW && plain(hud).equals("무기 시험 0/6"), "try hud " + plain(hud) + " " + hud);
            h.assertTrue(NavMath.sectorOf(sp.getViewVector(1F), sp.position(), new Vec3(hud.x(), hud.y(), hud.z())) == 0, "dummy straight ahead");
            // 1칸 앞으로 가면 도착
            sp.snapTo(at.x, at.y, at.z - 1, 0F, 0F);
            hud = tickOnly(sp);
            h.assertTrue(hud.mode() == NavNet.NavPayload.HERE && plain(hud).equals("무기 시험 0/6 · 훈련용 허수아비"), "dummy arrived " + plain(hud));
        } finally {
            if (d != null) Dummy.removeDummy(d);
        }
        done(h, sp);
    }

    /** 처치 (RPG 몬스터) → 사냥터, 보스 → 제단: 5단계 훅이 비어 있으면 "사냥터 없음" · "제단 없음", 채우면 가장 가까운 곳 · 싸우는 보스 필드 근처는 숨김 */
    @GameTest(maxTicks = 40)
    public void navZoneAltarHooks(GameTestHelper h) {
        ServerPlayer sp = player(h);
        String id = null;
        Supplier<List<Nav.Zone>> zones0 = Nav.zones;
        Supplier<List<Nav.Altar>> altars0 = Nav.altars;
        Supplier<List<Nav.BossFight>> fights0 = Nav.bossFights;
        try {
            Quest q = new Quest();
            q.title = "굴론 사냥";
            q.type = "kill";
            q.target = "rpg:gulon";
            q.count = 3;
            q.targetName = "굴론";
            id = Quests.saveQuest(q).id;
            Props.set(sp, "rpg:qs", "{\"" + id + "\":{\"s\":\"a\",\"n\":1,\"c\":0}}");
            Quests.forgetQuestPlayer(sp.getUUID());
            Quests.pinQuest(sp, id);
            Nav.zones = List::of; // 사냥터 없음 (다른 시험이 세계에 남긴 사냥터와 상관없이)
            Quests.NavGoal g = Quests.navGoal(sp);
            h.assertTrue(g != null && g.why().equals("hunt") && "gulon".equals(g.mob()), "hunt goal " + g);
            NavNet.NavPayload hud = tick(sp);
            h.assertTrue(hud.visible() && hud.mode() == NavNet.NavPayload.NONE && plain(hud).equals("굴론 처치 1/3 · 사냥터 없음"), "no zone " + plain(hud));
            // 5단계 사냥터: 다른 차원 · 먼 곳 · 가까운 곳 → 같은 차원의 가장 가까운 곳 (가운데 = 블록 + 0.5)
            Vec3 c = h.absoluteVec(new Vec3(1.5, 2, 1.5));
            int bx = (int) Math.floor(c.x);
            int by = (int) Math.floor(c.y);
            int bz = (int) Math.floor(c.z);
            Nav.zones = () -> List.of(new Nav.Zone("z_nether", "gulon", "minecraft:the_nether", 0, 64, 0), new Nav.Zone("z_far", "gulon", dim(h), bx + 60, by, bz),
                    new Nav.Zone("z_near", "gulon", dim(h), bx + 20, by, bz), new Nav.Zone("z_troll", "troll", dim(h), bx + 2, by, bz));
            Nav.Target t = Nav.resolve(sp, g);
            h.assertTrue(t != null && t.kind().equals("zone") && t.id().equals("z_near") && t.radius() == 8, "nearest zone " + t);
            h.assertTrue(Math.abs(t.at().x - (bx + 20.5)) < 1e-9 && Math.abs(t.at().z - (bz + 0.5)) < 1e-9, "zone center " + t.at());
            hud = tick(sp);
            h.assertTrue(hud.mode() == NavNet.NavPayload.ARROW && plain(hud).equals("굴론 처치 1/3"), "zone arrow " + plain(hud));
            // 보스 (허수아비 킹) → 제단
            Quests.NavGoal bg = new Quests.NavGoal(quest("nav_boss", "수확의 왕"), true, "boss", null, null, List.of(), "scarecrow_king", "허수아비 킹", 0, 1, 0);
            h.assertTrue(Nav.resolve(sp, bg) == null, "no altar yet");
            Nav.Altar altar = new Nav.Altar("scarecrow_king", dim(h), bx + 30, by, bz + 4);
            Nav.altars = () -> List.of(new Nav.Altar("kraken", dim(h), bx + 3, by, bz), altar);
            Nav.Target ta = Nav.resolve(sp, bg);
            h.assertTrue(ta != null && ta.kind().equals("altar") && ta.altar().equals(altar) && ta.radius() == 16 && Math.abs(ta.at().x - (bx + 30.5)) < 1e-9, "altar " + ta);
            h.assertTrue(Nav.labelOf(bg, ta).equals("§c허수아비 킹 §f제단"), Nav.labelOf(bg, ta));
            h.assertTrue(Nav.inArena(altar, dim(h), new Vec3(bx + 30.5, by + 1, bz + 10), 0) && !Nav.inArena(altar, dim(h), new Vec3(bx + 30.5, by + 1, bz + 25), 0),
                    "in arena");
            // 싸우는 보스 필드 근처 (반지름 16 + 46 안) → 화면 칸 숨김
            Nav.bossFights = () -> List.of(new Nav.BossFight(altar, null));
            hud = tick(sp);
            h.assertTrue(!hud.visible(), "hidden near a boss fight " + hud);
            Nav.bossFights = () -> List.of(new Nav.BossFight(new Nav.Altar("scarecrow_king", dim(h), bx + 200, by, bz), null));
            hud = tick(sp);
            h.assertTrue(hud.visible(), "shown far from the fight");
        } finally {
            Nav.zones = zones0;
            Nav.altars = altars0;
            Nav.bossFights = fights0;
            if (id != null) Quests.deleteQuest(id);
        }
        done(h, sp);
    }

    // ------------------------------------------------------------ 퀘스트 · 명령어

    /** 퀘스트를 받으면 (대화창 — Forms.HEADLESS) 다음 틱을 기다리지 않고 바로 다시 정함 (Quests.onNavChange → Nav.refreshNav) */
    @GameTest(maxTicks = 40)
    public void navFollowsAcceptedQuest(GameTestHelper h) {
        ServerPlayer sp = player(h);
        Mannequin giver = npc(h, new Vec3(3.5, 2, 3.5), "경비대장");
        Mannequin target = npc(h, new Vec3(1.5, 2, 6.5), "대장장이 견습생");
        Forms.HEADLESS.add(sp.getUUID());
        String id = null;
        try {
            Quest q = new Quest();
            q.giver = NpcData.key(giver);
            q.title = "견습생에게 전하기";
            q.type = "talk";
            q.target = NpcData.key(target);
            id = Quests.saveQuest(q).id;
            NavNet.NavPayload hud = tick(sp);
            // (다른 시험이 메인 NPC 를 세웠으면 메인 이야기 "새 이야기" 를 안내할 수 있음 — 이 퀘스트는 아직 아님)
            h.assertTrue(hud != null && !hud.key().contains(id + "|"), "not this quest yet " + hud);
            QuestTalk.talkLoop(sp, giver, null);
            pick(h, sp, "견습생에게 전하기");
            pick(h, sp, "수락하기");
            h.assertTrue(id.equals(Quests.navOf(sp).q()), "pinned on accept");
            Nav.tickPlayer(sp, Tasks.now()); // 같은 틱 — 목적지를 다시 정하는 주기 (10틱) 를 기다리지 않음
            hud = Nav.hud(sp);
            h.assertTrue(hud.visible() && hud.frame() == 2 && plain(hud).startsWith("대장장이 견습생에게") && hud.key().contains(id), "guides at once " + plain(hud));
        } finally {
            Forms.HEADLESS.remove(sp.getUUID());
            if (id != null) Quests.deleteQuest(id);
            giver.discard();
            target.discard();
        }
        done(h, sp);
    }

    private static void pick(GameTestHelper h, ServerPlayer sp, String part) {
        if (Forms.last(sp) instanceof FormNet.DialogPayload d) {
            for (int i = 0; i < d.choices().size(); i++) {
                if (BedrockText.plain(d.choices().get(i).text()).contains(part)) {
                    Forms.answer(sp, i, null);
                    return;
                }
            }
        }
        h.fail("선택지 없음: " + part + " — " + Forms.last(sp));
    }

    /** /nav off → 숨김, /길안내 on → 다시 (갈 곳 알려 줌), /nav (바꿈) · 명령어 등록 */
    @GameTest(maxTicks = 40)
    public void navCommandOnOff(GameTestHelper h) {
        ServerPlayer sp = player(h);
        MinecraftServer server = h.getLevel().getServer();
        Mannequin a = npc(h, new Vec3(5.5, 2, 5.5), "파수꾼");
        String id = null;
        try {
            h.assertTrue(server.getCommands().getDispatcher().getRoot().getChild("nav") != null && server.getCommands().getDispatcher().getRoot().getChild("길안내") != null,
                    "commands registered");
            Quest q = new Quest();
            q.giver = NpcData.key(a);
            q.title = "파수꾼에게 보고";
            q.type = "talk";
            q.target = NpcData.key(a);
            id = Quests.saveQuest(q).id;
            Props.set(sp, "rpg:qs", "{\"" + id + "\":{\"s\":\"a\",\"n\":0,\"c\":0}}");
            Quests.forgetQuestPlayer(sp.getUUID());
            Quests.pinQuest(sp, id);
            h.assertTrue(tick(sp).visible(), "visible before /nav off");
            server.getCommands().performPrefixedCommand(sp.createCommandSourceStack(), "nav off");
            h.assertTrue(Quests.navOf(sp).off() && Quests.navGoal(sp) == null, "off");
            Nav.tickPlayer(sp, Tasks.now());
            h.assertTrue(NavNet.NavPayload.HIDDEN.equals(Nav.hud(sp)), "hidden after /nav off " + Nav.hud(sp));
            server.getCommands().performPrefixedCommand(sp.createCommandSourceStack(), "길안내 on");
            h.assertTrue(!Quests.navOf(sp).off(), "on again");
            Nav.tickPlayer(sp, Tasks.now());
            h.assertTrue(Nav.hud(sp).visible() && plain(Nav.hud(sp)).startsWith("파수꾼에게"), "shown after /길안내 on " + plain(Nav.hud(sp)));
            server.getCommands().performPrefixedCommand(sp.createCommandSourceStack(), "nav");
            h.assertTrue(Quests.navOf(sp).off(), "/nav toggles off");
            String msg = Nav.navCommand(sp, null);
            h.assertTrue(!Quests.navOf(sp).off() && msg.contains("길 안내를 켰습니다") && msg.contains("파수꾼에게 보고 → 파수꾼에게") && msg.contains("오버월드"), msg);
            h.assertTrue(Nav.navCommand(sp, "off").contains("길 안내를 껐습니다") && Quests.navOf(sp).off(), "off message");
            Quests.setNavOff(sp, false);
        } finally {
            if (id != null) Quests.deleteQuest(id);
            a.discard();
        }
        done(h, sp);
    }
}
