package mmorpg.fabric.test;

import java.util.Map;
import java.util.Set;
import mmorpg.core.Json;
import mmorpg.core.Weapons;
import mmorpg.fabric.Props;
import mmorpg.fabric.RpgNet;
import mmorpg.fabric.cosmetic.CosmeticDefs;
import mmorpg.fabric.cosmetic.Cosmetics;
import mmorpg.fabric.cosmetic.Look;
import mmorpg.fabric.ui.Inv;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 0.13.0 게임 시험 (서버) — 장식: 목록 (칸 · 무기 종류 · 모델 · 아이콘 · 무기 스킨 모델 파일), 사기 (에메랄드 · 이미 가짐 · 모자람 · 바로 입음),
 * 입기 · 벗기 (가진 것만 · 무기 종류마다 하나), 갑옷 숨기기 (무료 · 무기 · 장식 칸은 안 됨 · 모두 숨기기 · 모두 보이기), 기록 (플레이어 속성 JSON · 모르는 키는 뺌),
 * 운영자 (지급 · 회수 — 입은 것 벗김 · 초기화), 스탯 창 단추 (cosmetic), 명령어 (/장식 · /cosmetic).
 */
public final class RpgCosmeticTests {
    static final String ENV = "mmorpg:cosmetic";

    static ServerPlayer player(GameTestHelper h, int emeralds) {
        ServerPlayer sp = RpgGearTests.player(h);
        Cosmetics.reset(sp);
        sp.getInventory().clearContent();
        for (int left = emeralds; left > 0; left -= 64) sp.getInventory().add(new ItemStack(Items.EMERALD, Math.min(64, left)));
        return sp;
    }

    @GameTest(environment = ENV, maxTicks = 20)
    public void cosmeticDefs(GameTestHelper h) {
        Map<String, CosmeticDefs.Def> all = CosmeticDefs.all();
        h.assertTrue(all.size() >= 30, "items " + all.size());
        h.assertTrue(CosmeticDefs.slots().keySet().equals(Set.copyOf(CosmeticDefs.SLOTS)), "slots " + CosmeticDefs.slots().keySet());
        for (String s : CosmeticDefs.SLOTS) {
            h.assertTrue(CosmeticDefs.slot(s).hide() == CosmeticDefs.ARMOR.contains(s), "hide flag of " + s);
            h.assertTrue(!CosmeticDefs.of(s, null).isEmpty(), "samples in slot " + s);
        }
        for (String type : Weapons.allTypes()) h.assertTrue(!CosmeticDefs.of("weapon", type).isEmpty(), "weapon skin sample for " + type);
        for (CosmeticDefs.Def d : all.values()) {
            h.assertTrue(d.price() > 0 && !d.name().isEmpty(), "price/name " + d.id());
            h.assertTrue(res("/assets/rpg/items/cosmetic_" + d.id() + ".json"), "icon item definition " + d.id());
            h.assertTrue(res("/assets/rpg/textures/item/cosmetic/" + d.id() + ".png"), "icon texture " + d.id());
            switch (d.slot()) {
                case "weapon" -> {
                    h.assertTrue(Weapons.allTypes().contains(d.weapon()), "weapon type " + d.id() + " " + d.weapon());
                    h.assertTrue(d.design() != null && d.design().startsWith(designPrefix(d.weapon())), "design name " + d.design());
                    h.assertTrue(res("/assets/rpg/models/item/model/" + d.design() + ".json"), "skin model " + d.design());
                }
                case "aura" -> h.assertTrue(d.aura() != null && Set.of("embers", "orbit", "frost").contains(d.aura().kind()) && !d.aura().colors().isEmpty(),
                        "aura " + d.id());
                case "title" -> h.assertTrue(d.title() != null && !d.title().isEmpty(), "title " + d.id());
                default -> {
                    h.assertTrue(d.model() != null && res("/assets/mmorpg/cosmetic/" + d.model() + ".json"), "model " + d.id() + " " + d.model());
                    Map<String, Object> m = Json.parseObject(text("/assets/mmorpg/cosmetic/" + d.model() + ".json"));
                    String tex = String.valueOf(m.get("texture"));
                    h.assertTrue(tex.startsWith("mmorpg:") && res("/assets/mmorpg/" + tex.substring(7)), "texture " + tex);
                    h.assertTrue(m.get("parts") instanceof Map<?, ?> parts && !parts.isEmpty(), "parts " + d.model());
                    if (CosmeticDefs.ARMOR.contains(d.slot())) h.assertTrue(d.set() != null, "armor skin set " + d.id());
                }
            }
        }
        h.succeed();
    }

    /** 무기 종류 → 디자인 이름 앞 (완드 = staff — 무기 디자인 이름과 같게: 1인칭 세워 들기 · 아이콘 방향이 이름으로 정해짐) */
    static String designPrefix(String type) {
        return ("wand".equals(type) ? "staff" : type) + "_";
    }

    static boolean res(String path) {
        return RpgCosmeticTests.class.getResource(path) != null;
    }

    static String text(String path) {
        try (var in = RpgCosmeticTests.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException | NullPointerException e) {
            return "{}";
        }
    }

    @GameTest(environment = ENV, maxTicks = 20)
    public void buyAndEquip(GameTestHelper h) {
        ServerPlayer sp = player(h, 500);
        CosmeticDefs.Def crown = CosmeticDefs.get("crown");
        h.assertTrue(Inv.money(sp) == 500, "start money " + Inv.money(sp));
        h.assertTrue(Cosmetics.equip(sp, "crown") != null, "cannot equip before buying");
        h.assertTrue(Cosmetics.buy(sp, "crown") == null, "buy crown");
        h.assertTrue(Inv.money(sp) == 500 - crown.price(), "paid " + Inv.money(sp));
        h.assertTrue(Cosmetics.owns(sp, "crown") && "crown".equals(Cosmetics.look(sp).get("head")), "owned + worn " + Cosmetics.look(sp));
        h.assertTrue(Cosmetics.buy(sp, "crown") != null && Inv.money(sp) == 500 - crown.price(), "buying twice refused, no charge");
        // 모자라면 사지 못하고 그대로
        int before = Inv.money(sp);
        h.assertTrue(CosmeticDefs.get("angel_wings").price() > before, "wings cost more than left");
        h.assertTrue(Cosmetics.buy(sp, "angel_wings") != null && Inv.money(sp) == before && !Cosmetics.owns(sp, "angel_wings"), "not enough money");
        // 무기 스킨: 종류마다 하나 (다른 종류는 그대로)
        Cosmetics.give(sp, "sakura_sword");
        Cosmetics.give(sp, "frost_bow");
        h.assertTrue(Cosmetics.equip(sp, "sakura_sword") == null && Cosmetics.equip(sp, "frost_bow") == null, "equip skins");
        Look l = Cosmetics.look(sp);
        h.assertTrue("sakura_sword".equals(l.weaponSkin("sword")) && "frost_bow".equals(l.weaponSkin("bow")) && l.weaponSkin("axe") == null, "per type " + l);
        h.assertTrue(Cosmetics.clear(sp, "weapon", "sword") == null && Cosmetics.look(sp).weaponSkin("sword") == null
                && "frost_bow".equals(Cosmetics.look(sp).weaponSkin("bow")), "clear one weapon type");
        // 벗기 · 다시 입기
        h.assertTrue(Cosmetics.clear(sp, "head", null) == null && Cosmetics.look(sp).get("head") == null, "clear head");
        h.assertTrue(Cosmetics.equip(sp, "crown") == null && "crown".equals(Cosmetics.look(sp).get("head")), "equip again (free)");
        h.assertTrue(Inv.money(sp) == before, "equip/clear cost nothing");
        RpgGearTests.done(h, sp);
    }

    @GameTest(environment = ENV, maxTicks = 20)
    public void hideArmor(GameTestHelper h) {
        ServerPlayer sp = player(h, 0);
        h.assertTrue(Cosmetics.hide(sp, "helmet") == null && Cosmetics.look(sp).hidden("helmet"), "hide helmet (free, no money)");
        h.assertTrue(Cosmetics.hide(sp, "weapon") != null, "weapon cannot be hidden");
        h.assertTrue(Cosmetics.hide(sp, "head") != null && Cosmetics.hide(sp, "aura") != null, "decorations have no hide");
        h.assertTrue(Cosmetics.look(sp).get("weapon") == null && Cosmetics.look(sp).weapon().isEmpty(), "weapon untouched");
        Cosmetics.action(sp, "hide_all", "", "");
        for (String s : CosmeticDefs.ARMOR) h.assertTrue(Cosmetics.look(sp).hidden(s), "hide all → " + s);
        Cosmetics.action(sp, "show_all", "", "");
        for (String s : CosmeticDefs.ARMOR) h.assertTrue(Cosmetics.look(sp).get(s) == null, "show all → " + s);
        // 스킨은 숨김을 바꿈 · 원래대로 = 입은 갑옷
        Cosmetics.give(sp, "paladin_chest");
        Cosmetics.hide(sp, "chest");
        h.assertTrue(Cosmetics.equip(sp, "paladin_chest") == null && "paladin_chest".equals(Cosmetics.look(sp).get("chest")), "skin replaces hide");
        h.assertTrue(Cosmetics.clear(sp, "chest", null) == null && Cosmetics.look(sp).get("chest") == null, "back to own armor");
        RpgGearTests.done(h, sp);
    }

    @GameTest(environment = ENV, maxTicks = 20)
    public void recordAndSanitize(GameTestHelper h) {
        ServerPlayer sp = player(h, 0);
        Cosmetics.give(sp, "halo");
        Cosmetics.equip(sp, "halo");
        Cosmetics.hide(sp, "boots");
        Map<String, Object> m = Json.parseObject(Props.get(sp, Cosmetics.DATA));
        h.assertTrue(m.get("owned") instanceof java.util.List<?> o && o.contains("halo"), "record owned " + m);
        h.assertTrue(m.get("look") instanceof Map<?, ?> lm && "halo".equals(lm.get("head")) && Look.HIDE.equals(lm.get("boots")), "record look " + m);
        // 잘못된 기록: 모르는 키 · 다른 칸의 키 · 가지지 않은 것 · 장식 칸의 hide · 무기 종류가 다른 스킨 → 뺌
        Props.set(sp, Cosmetics.DATA, "{\"owned\":[\"halo\",\"nope\",\"crown\",\"sakura_sword\"],\"look\":{\"head\":\"crown\",\"back\":\"halo\","
                + "\"aura\":\"hide\",\"title\":\"title_hero\",\"legs\":\"hide\",\"weapon\":{\"bow\":\"sakura_sword\",\"sword\":\"sakura_sword\"}}}");
        Cosmetics.State s = Cosmetics.state(sp);
        h.assertTrue(s.owned().equals(Set.of("halo", "crown", "sakura_sword")), "unknown owned dropped " + s.owned());
        Look l = s.look();
        h.assertTrue("crown".equals(l.get("head")) && l.get("back") == null && l.get("aura") == null && l.get("title") == null && l.hidden("legs"),
                "sanitized slots " + l);
        h.assertTrue("sakura_sword".equals(l.weaponSkin("sword")) && l.weaponSkin("bow") == null, "sanitized weapon " + l);
        Props.set(sp, Cosmetics.DATA, "not json");
        h.assertTrue(Cosmetics.state(sp).owned().isEmpty() && Cosmetics.look(sp).isEmpty(), "broken record = empty");
        // Look JSON 왕복
        Look round = Look.parse(new Look(Map.of("axe", "lava_axe"), Map.of("head", "crown", "helmet", Look.HIDE)).json());
        h.assertTrue("lava_axe".equals(round.weaponSkin("axe")) && "crown".equals(round.get("head")) && round.hidden("helmet"), "json round trip " + round);
        RpgGearTests.done(h, sp);
    }

    @GameTest(environment = ENV, maxTicks = 20)
    public void adminAndHooks(GameTestHelper h) {
        ServerPlayer sp = player(h, 0);
        h.assertTrue(Cosmetics.give(sp, "angel_wings") && !Cosmetics.give(sp, "angel_wings"), "give once");
        h.assertTrue(!Cosmetics.give(sp, "nope"), "give unknown");
        Cosmetics.equip(sp, "angel_wings");
        h.assertTrue(Cosmetics.take(sp, "angel_wings") && !Cosmetics.owns(sp, "angel_wings") && Cosmetics.look(sp).get("back") == null, "take strips it");
        Cosmetics.give(sp, "title_hero");
        Cosmetics.equip(sp, "title_hero");
        Cosmetics.reset(sp);
        h.assertTrue(Cosmetics.state(sp).owned().isEmpty() && Cosmetics.look(sp).isEmpty(), "reset");
        h.assertTrue(RpgNet.statAction(sp, "cosmetic"), "stat window button");
        var root = h.getLevel().getServer().getCommands().getDispatcher().getRoot();
        h.assertTrue(root.getChild("장식") != null && root.getChild("cosmetic") != null, "commands");
        for (String sub : new String[] {"입기", "벗기", "숨기기", "사기", "목록", "지급", "회수", "초기화"}) {
            h.assertTrue(root.getChild("장식").getChild(sub) != null, "/장식 " + sub);
        }
        RpgGearTests.done(h, sp);
    }
}
