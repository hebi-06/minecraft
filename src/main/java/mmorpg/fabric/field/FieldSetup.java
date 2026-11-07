package mmorpg.fabric.field;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.field.Blueprint;
import mmorpg.core.field.FieldBuild;
import mmorpg.core.field.FieldPlan;
import mmorpg.core.field.FieldTerrain;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Props;
import mmorpg.fabric.boss.Arena;
import mmorpg.fabric.mob.HuntZones;
import mmorpg.fabric.npc.Dummy;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.npc.NpcEditor;
import mmorpg.fabric.quest.MainQuests;
import mmorpg.fabric.quest.Quests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.phys.Vec3;

/**
 * 섬 맵을 처음 열 때 한 번 (2026-11-07): 보스 제단 (미리 지은 신전 — 보스 엔진 목록에 "지어짐") · 사냥터 (field.json zone — 몬스터마다 두 곳) ·
 * NPC (마을 · 거점의 자리표 — 메인 퀘스트 NPC 1 ~ 16 · 상인 · 직업 교관) · 훈련장 허수아비. 그 자리 청크를 불러와 세우므로 처음 열 때 몇 초 걸림.
 * 다 하면 세계 속성 rpg:field_ready = 판. 이미 있는 것 (같은 번호의 메인 NPC · 같은 자리의 사냥터 · 제단) 은 그대로 둠.
 */
public final class FieldSetup {
    private FieldSetup() {}

    static final String READY_KEY = "rpg:field_ready";
    static final String READY_VERSION = "1";
    /** 사냥터마다 몬스터 수 */
    static final int ZONE_COUNT = 4;

    static void run(MinecraftServer server, ServerLevel level) {
        if (READY_VERSION.equals(Props.world(server, READY_KEY))) return;
        long t0 = System.nanoTime();
        FieldTerrain t = FieldWorld.terrain();
        String dim = NpcData.dimId(level);
        // 보스 제단 (신전은 지형과 함께 지어짐)
        int altars = 0;
        for (FieldExtras.AltarSpot s : FieldExtras.altars()) {
            Arena.Altar a = Arena.addAltar(s.key(), dim, s.pos());
            if (!a.built) {
                a.built = true;
                altars++;
            }
        }
        Arena.saveAltars();
        // 사냥터 (땅 위 — 몬스터는 사람이 가까이 오면 채워짐)
        int zones = 0;
        for (FieldPlan.Site s : t.plan().sites("zone")) {
            int x = (int) Math.floor(s.x());
            int z = (int) Math.floor(s.z());
            int y = t.surfaceAt(x, z) + 1;
            if (HuntZones.addZoneAt(server, level, s.mob(), ZONE_COUNT, x, y, z) != null) zones++;
        }
        // NPC · 허수아비
        int npcs = 0;
        int dummies = 0;
        List<Blueprint.Mark> marks = FieldBuild.build(t).marks();
        for (Blueprint.Mark m : marks) {
            Vec3 at = new Vec3(m.x(), m.y(), m.z());
            if (m.id().startsWith("waystone")) continue;
            level.getChunk(BlockPos.containing(at));
            Vec3 face = at.add(dir(m.facing()).scale(5)).add(0, 1.6, 0);
            if (m.id().equals("dummy")) {
                float yaw = (float) Math.toDegrees(Math.atan2(-(face.x - at.x), face.z - at.z));
                if (Dummy.spawnDummyAt(level, at, yaw) != null) dummies++;
                continue;
            }
            if (m.id().startsWith("npc_")) {
                int n = Integer.parseInt(m.id().substring(4));
                if (mainNpc(server, level, n, at, face, dim)) npcs++;
                continue;
            }
            if (m.id().startsWith("shop_")) {
                Mannequin npc = NpcData.spawnAt(level, at, face);
                NpcEditor.applyRole(npc, "shop", m.id().substring(5));
                npcs++;
                continue;
            }
            if (m.id().equals("job")) {
                Mannequin npc = NpcData.spawnAt(level, at, face);
                NpcEditor.applyRole(npc, "job", "");
                npcs++;
            }
        }
        Props.setWorld(server, READY_KEY, READY_VERSION);
        Mmorpg.LOG.info("[field] first setup: {} altars built, {} hunt zones, {} NPCs, {} dummies in {} ms", altars, zones, npcs, dummies,
                (System.nanoTime() - t0) / 1_000_000);
    }

    /** 메인 퀘스트 NPC n 을 세움 (MainNpcs.mainNpcCommand 와 같은 기록 — 이미 세운 것이 기록에 있으면 건너뜀) */
    static boolean mainNpc(MinecraftServer server, ServerLevel level, int n, Vec3 at, Vec3 face, String dim) {
        MainQuests.MainNpc def = MainQuests.mainNpcByNumber(n);
        if (def == null) return false;
        if (NpcData.mainRecord(server, def.key()) != null) return false;
        Mannequin npc = NpcData.spawnAt(level, at, face);
        NpcData.setKey(npc, def.key());
        NpcData.setName(npc, def.name());
        if (def.smith()) NpcData.setSmith(npc, true);
        if (def.runesmith()) NpcData.setRunesmith(npc, true);
        NpcData.refresh(npc, Quests.badgeCount(def.key()));
        NpcData.setMainRecord(def.key(), npc, at, dim);
        NpcData.notePos(npc, def.key(), at, dim);
        return true;
    }

    static Vec3 dir(String facing) {
        return switch (facing) {
            case "north" -> new Vec3(0, 0, -1);
            case "east" -> new Vec3(1, 0, 0);
            case "west" -> new Vec3(-1, 0, 0);
            default -> new Vec3(0, 0, 1);
        };
    }

    /** 시험: 세울 자리표 수 (이동 지점 빼고) */
    static List<String> markIds() {
        List<String> out = new ArrayList<>();
        for (Blueprint.Mark m : FieldBuild.build(FieldWorld.terrain()).marks()) if (!m.id().startsWith("waystone")) out.add(m.id());
        return out;
    }
}
