package mmorpg.fabric.test;

import java.util.List;
import java.util.Set;
import mmorpg.fabric.npc.GodNpcs;
import mmorpg.fabric.npc.NpcData;
import net.minecraft.core.component.DataComponents;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * 2026-11-28 게임 시험 (서버) — 원소신 NPC 다섯 (GodNpcs · tools/god_npcs.py): 목록 (다섯 · 이름 · 이명), 그림 파일 (스킨 · 망토 · 소품 아이템 정의 · 모델 · 그림판),
 * 세우기 (RPG NPC · 태그 · 이름 · 인사말 · 아래 줄 이명 · 스킨 패치 프로필 (texture · model · cape) · 머리 · 두 손), 둘레 소품 (표시 엔티티 수 = 배치 수),
 * 이름으로 찾기 (라에타 → laeta), 다시 입혀도 태그 하나.
 */
public final class RpgGodNpcTests {
    static final String ENV = "mmorpg:gods";

    @GameTest(environment = ENV, maxTicks = 40)
    public void godNpcs(GameTestHelper h) {
        h.assertTrue(GodNpcs.all().keySet().equals(Set.of("laeta", "nivaria", "marea", "fortia", "gravis")), "gods " + GodNpcs.all().keySet());
        h.assertTrue(GodNpcs.get("라에타") == GodNpcs.get("laeta") && GodNpcs.get("그라비스") != null && GodNpcs.get("헬") == null, "find by name");
        ServerPlayer sp = RpgGearTests.player(h);
        for (GodNpcs.God g : GodNpcs.all().values()) {
            h.assertTrue(!g.name().isEmpty() && !g.title().isEmpty() && !g.greeting().isEmpty(), "texts " + g.id());
            h.assertTrue(res("/assets/rpg/textures/entity/god/" + g.id() + ".png"), "skin png " + g.id());
            if (g.cape() != null) h.assertTrue(res("/assets/" + g.cape().getNamespace() + "/textures/" + g.cape().getPath() + ".png"), "cape png " + g.id());
            h.assertTrue(res("/assets/rpg/textures/item/god/" + g.id() + ".png"), "prop atlas " + g.id());
            for (GodNpcs.Prop p : g.props()) h.assertTrue(item(p.model()), "prop item " + p.model());
            for (var id : java.util.Arrays.asList(g.head(), g.main(), g.off())) if (id != null) h.assertTrue(item(id), "worn item " + id);
            Mannequin m = GodNpcs.spawn(sp, g);
            h.assertTrue(NpcData.isRpgNpc(m) && m.entityTags().contains(GodNpcs.TAG) && GodNpcs.godOf(m) == g, "tags " + g.id() + " " + m.entityTags());
            h.assertTrue(NpcData.name(m).equals(g.name()) && NpcData.greeting(m).equals(g.greeting()) && NpcData.key(m).equals("god_" + g.id()),
                    "name · greeting · key " + g.id());
            PlayerSkin.Patch patch = m.getProfile().skinPatch();
            h.assertTrue(patch.body().isPresent() && patch.body().get().id().equals(g.skin()), "skin patch " + g.id() + " " + patch);
            h.assertTrue(patch.model().orElse(null) == (g.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE), "model " + g.id());
            h.assertTrue(g.cape() == null ? patch.cape().isEmpty() : patch.cape().map(c -> c.id().equals(g.cape())).orElse(false), "cape " + g.id());
            h.assertTrue(g.head().equals(m.getItemBySlot(EquipmentSlot.HEAD).get(DataComponents.ITEM_MODEL)), "head " + g.id());
            h.assertTrue(g.main() == null ? m.getMainHandItem().isEmpty() : g.main().equals(m.getMainHandItem().get(DataComponents.ITEM_MODEL)), "main " + g.id());
            h.assertTrue(g.off() == null ? m.getOffhandItem().isEmpty() : g.off().equals(m.getOffhandItem().get(DataComponents.ITEM_MODEL)), "off " + g.id());
            GodNpcs.attachNow(m);
            h.assertTrue(GodNpcs.propCount(m) == g.props().size(), "props " + g.id() + " " + GodNpcs.propCount(m));
            GodNpcs.dress(m, g);
            h.assertTrue(m.entityTags().stream().filter(t -> t.startsWith(GodNpcs.TAG_PREFIX)).count() == 1, "one god tag after re-dress " + g.id());
            m.discard();
        }
        h.assertTrue(List.of(GodNpcs.get("laeta").slim(), GodNpcs.get("fortia").slim(), GodNpcs.get("gravis").slim()).equals(List.of(true, false, false)), "arm widths");
        h.succeed();
    }

    static boolean res(String path) {
        return RpgGodNpcTests.class.getResource(path) != null;
    }

    static boolean item(net.minecraft.resources.Identifier id) {
        return res("/assets/" + id.getNamespace() + "/items/" + id.getPath() + ".json")
                && res("/assets/" + id.getNamespace() + "/models/item/" + id.getPath() + ".json");
    }
}
