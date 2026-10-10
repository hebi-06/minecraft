package mmorpg.fabric.quest;

import java.util.ArrayList;
import java.util.List;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.fabric.npc.NpcData;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.SkillFx;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * 메인 퀘스트 NPC 세우기 (npc.js mainNpcCommand · mainNpcListText · findMainNpc — /mainnpc 번호): 미리 짜 둔 NPC 를 내 앞 2칸에 (이름 · 대장장이 ·
 * 룬술사 역할 · 퀘스트가 붙은 채로). 같은 번호의 NPC 가 불러와진 곳에 이미 있으면 새로 만들지 않고 이 자리로 옮김. 몸은 마네킹 (NpcData — 고개 · 연출은 Npcs).
 */
public final class MainNpcs {
    private MainNpcs() {}

    private static final Rules R = Rules.get();

    /** 세운 메인 퀘스트 NPC (불러와진 곳에서만 — 같은 번호가 여럿이면 세운 기록의 것, 없으면 처음 것) */
    public static Mannequin findMainNpc(MinecraftServer server, String key) {
        List<Mannequin> list = NpcData.loaded(server).getOrDefault(key, List.of());
        NpcData.Spot rec = NpcData.mainRecord(server, key);
        Mannequin first = null;
        for (Mannequin m : list) {
            if (m.isRemoved() || !m.isAlive()) continue;
            if (rec != null && rec.id().equals(m.getUUID().toString())) return m;
            if (first == null) first = m;
        }
        return first;
    }

    private static String mobText(String k) {
        Obj m = R.obj("MONSTERS").objOrNull(k);
        return (m != null ? m.str("name") : k) + " Lv." + (m != null ? Js.str(m.num("level")) : "?");
    }

    private static String bossText(String k) {
        Obj b = R.obj("BOSSES").objOrNull(k);
        return (b != null ? b.str("name") : k) + " Lv." + (b != null ? Js.str(b.num("level")) : "?");
    }

    /** /mainnpc (번호 없이): NPC 목록과 필요한 사냥터 · 보스 제단 (지역마다 무엇을 둘지) */
    public static String mainNpcListText() {
        List<String> others = new ArrayList<>();
        for (MainQuests.MainNpc d : MainQuests.MAIN_NPCS) if (!MainQuests.STORY_NPC_NUMBERS.contains(d.n())) others.add(String.valueOf(d.n()));
        List<String> story = new ArrayList<>();
        for (int n : MainQuests.STORY_NPC_NUMBERS) story.add(String.valueOf(n));
        List<String> lines = new ArrayList<>();
        lines.add("§6[메인 퀘스트 NPC] §f/mainnpc 번호§7 → 내 앞에 세움 (이미 세운 NPC는 이 자리로 옮김). " + String.join("·", story) + "번을 모두 세워야 이야기가 이어집니다 ("
                + String.join("·", others) + "번은 대장장이 · 반복 의뢰 · 왕실 퀘스트 · 룬술사)");
        for (MainQuests.MainNpc d : MainQuests.MAIN_NPCS) lines.add("§e" + d.n() + " §f" + d.name() + " §7- " + d.role());
        for (String r : MainQuests.REGION_ORDER) {
            MainQuests.Region reg = MainQuests.REGIONS.get(r);
            MainQuests.MainNpc d = MainQuests.mainNpcByKey(reg.npc());
            List<String> mobs = new ArrayList<>();
            for (String k : reg.mobs()) mobs.add(mobText(k));
            List<String> bosses = new ArrayList<>();
            for (String k : reg.bosses()) bosses.add(bossText(k));
            lines.add("§b" + reg.name() + " §7(" + (d != null ? String.valueOf(d.n()) : "?") + "번 곁): 사냥터 §f" + String.join(" · ", mobs) + "§7, 제단 §f" + String.join(" · ", bosses));
        }
        List<String> zones = new ArrayList<>();
        for (String k : R.strings("MONSTER_ORDER")) zones.add(mobText(k));
        lines.add("§7사냥터 (/mobzone 종류 수 — 잡으면 다시 나옴): §f" + String.join(" · ", zones));
        List<String> altars = new ArrayList<>();
        for (String k : R.strings("BOSS_ORDER")) altars.add(bossText(k));
        lines.add("§7보스 제단 (/boss altar): §f" + String.join(" · ", altars));
        List<String> transcend = R.strings("TRANSCEND_ORDER");
        if (!transcend.isEmpty()) {
            List<String> t = new ArrayList<>();
            for (String k : transcend) t.add(bossText(k));
            lines.add("§c초월 보스 제단 (/boss altar · 왕녀의 전설 퀘스트): §f" + String.join(" · ", t));
        }
        return String.join("\n", lines);
    }

    /** 마네킹을 at 으로 옮겨 face 쪽을 보게 (다른 차원이면 새 엔티티 — 옮긴 엔티티, 실패하면 null) */
    static Entity moveNpc(Entity npc, ServerLevel level, Vec3 at, Vec3 face) {
        float yaw = Kit.lookRotation(at, face)[0];
        Entity moved = npc.teleport(new TeleportTransition(level, at, Vec3.ZERO, yaw, 0F, TeleportTransition.DO_NOTHING));
        if (moved != null) {
            moved.setYHeadRot(yaw);
            moved.setYBodyRot(yaw);
        }
        return moved;
    }

    /**
     * /mainnpc 번호: 메인 퀘스트 NPC 를 내 앞 2칸에 세움 (같은 번호의 NPC 가 불러와진 곳에 있으면 이 자리로 옮김). n 이 없거나 모르는 번호면 목록. 알림 글자를 돌려줌
     */
    public static String mainNpcCommand(ServerPlayer sp, Integer n) {
        MainQuests.MainNpc def = n == null ? null : MainQuests.mainNpcByNumber(n);
        if (def == null) return mainNpcListText();
        MinecraftServer server = sp.level().getServer();
        ServerLevel level = sp.level();
        Vec3 loc = NpcData.spotNear(sp);
        Vec3 face = sp.getEyePosition();
        String dim = NpcData.dimId(level);
        Mannequin found = findMainNpc(server, def.key());
        if (found != null) {
            Entity moved = moveNpc(found, level, loc, face);
            Entity npc = moved != null ? moved : found;
            NpcData.refresh(npc, Quests.badgeCount(def.key()));
            // 옮긴 직후에는 엔티티 위치가 아직 예전일 수 있어서 옮긴 자리를 직접 적음 (길 안내)
            NpcData.setMainRecord(def.key(), npc, loc, dim);
            NpcData.notePos(npc, def.key(), loc, dim);
            return "§6[메인 퀘스트] §f" + def.n() + "번 §e" + NpcData.name(npc) + "§f을(를) 이 자리로 옮겼습니다.";
        }
        // 전에 세운 NPC 가 불러와지지 않은 곳에 있으면: 새로 세우고, 예전 NPC 는 가까이 가는 사람이 생기면 치움 (Npcs.effectsTick)
        NpcData.Spot old = NpcData.mainRecord(server, def.key());
        Mannequin npc = NpcData.spawnAt(level, loc, face);
        NpcData.setKey(npc, def.key());
        NpcData.setName(npc, def.name());
        if (def.smith()) NpcData.setSmith(npc, true);
        if (def.runesmith()) NpcData.setRunesmith(npc, true);
        NpcData.refresh(npc, Quests.badgeCount(def.key()));
        NpcData.setMainRecord(def.key(), npc, loc, dim);
        NpcData.notePos(npc, def.key(), loc, dim); // 길 안내: 새로 세운 자리 (기록을 바꾼 뒤에)
        Vec3 head = npc.getEyePosition();
        SkillFx.vanilla(level, "minecraft:totem_particle", new Vec3(head.x, head.y + 0.4, head.z));
        SkillFx.playFor(sp, "random.orb", 1.2, 1);
        String note = old != null ? "\n§7전에 세운 " + def.n() + "번 NPC(" + Js.str(old.x()) + " " + Js.str(old.y()) + " " + Js.str(old.z())
                + ")는 불러와지지 않은 곳에 있어 찾지 못했습니다. 그 NPC는 근처에 사람이 가면 사라집니다." : "";
        return "§6[메인 퀘스트] §f" + def.n() + "번 §e" + def.name() + "§f을(를) 세웠습니다 §7(" + def.role() + ")" + note;
    }
}
