package mmorpg.fabric.smith;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import mmorpg.core.Crafting;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.gear.WeaponEnhance;
import mmorpg.fabric.shop.Shop;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.ui.ChestForm;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.Icon;
import mmorpg.fabric.ui.Inv;
import mmorpg.fabric.ui.Slot;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 대장장이 (smith.js): 영웅 무기 레벨 올리기 ↔ 무기 강화 (EnhanceWindow — enhance.js) ↔ 보스 무기 제작, 윗줄 양 끝 칸으로 오감.
 * <ul>
 *   <li>레벨 올리기: 영웅 무기의 레벨 제한을 SMITH.step 씩 (30 → 35 → 40). 그 칸의 무기를 다음 레벨 무기 아이템으로 바꾸므로 기본 성능 · 요구 레벨 · 요구 스탯이
 *       그 레벨 기준이 됨 (렙제: 아직 그 레벨이 아니어도 올릴 수 있고 창에 "N렙부터 사용" 을 알림). 비용: 에메랄드 + 별철 광석. 강화 단계 · 모루 이름은 그대로 옮김.</li>
 *   <li>보스 무기 제작: 재료 (여러 보스의 재료 + 일반 몬스터 전리품 — Crafting.craftMaterials) + 에메랄드 + 별철 광석 → 무기 (BOSS_CRAFT). 등급마다 한 쪽
 *       (영웅 30렙 · 초월 40렙 · 12단계 고귀 10 · 20렙 — 새 무기 넷, 전리품 + 에메랄드만), 윗줄 가운데 칸을 누르면 다음 쪽 (영웅 → 초월 → 고귀 → 영웅).
 *       가운데 줄 = 그 쪽의 무기, 아랫줄 = 그 쪽 무기들의 재료 (가진 수). 12단계 새 무기의 영웅 · 초월은 같은 쪽에 더해짐 (CRAFT_MORE).</li>
 * </ul>
 * 명령어 (commands.js, 관리자): /smith (레벨 올리기 창부터) · /enhance (강화 창부터).
 * 자바에서 달라진 점: 애드온의 async 창 고리는 받을 일 (콜백) 되부름으로 (shop 과 같음). 모루 이름은 바닐라 CUSTOM_NAME (애드온은 동적 속성 rpg:name).
 */
public final class Smith {
    private Smith() {}

    private static final Rules R = Rules.get();
    private static final Obj CONFIG = R.obj("CONFIG");
    private static final Obj SMITH = R.obj("SMITH");
    private static final Obj BOSSES = R.obj("BOSSES");
    private static final Obj BOSS_MATERIALS = R.obj("BOSS_MATERIALS");
    private static final Obj MONSTER_DROPS = R.obj("MONSTER_DROPS");
    private static final Obj MONSTERS = R.obj("MONSTERS");
    private static final String ORE_NAME = SMITH.str("oreName");

    static final int TAB_SLOT = 0;
    static final int CRAFT_TAB_SLOT = 8;
    static final int EMERALD_SLOT = 2;
    static final int INFO_SLOT = 4;
    static final int ORE_SLOT = 6;
    static final int FIRST = 9;
    static final int MAX_LIST = 18;
    static final int CRAFT_INFO_SLOT = 4;
    /** 가운데 줄: 만들 무기 (최대 9개) */
    static final int CRAFT_FIRST = 9;
    /** 아랫줄: 그 쪽 무기들의 재료 (최대 9종) */
    static final int MAT_FIRST = 18;

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    // ------------------------------------------------------------ 명령어 · 흐름

    private static LiteralArgumentBuilder<CommandSourceStack> admin(String name) {
        return Commands.literal(name).requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(admin("smith").executes(c -> {
            openSmith(c.getSource().getPlayerOrException(), "smith");
            return 1;
        }));
        d.register(admin("enhance").executes(c -> {
            openSmith(c.getSource().getPlayerOrException(), "enhance");
            return 1;
        }));
        // 손에 든 무기의 강화 단계 (시험용 — 애드온 /enhanceset)
        d.register(admin("enhanceset").then(Commands.argument("level", com.mojang.brigadier.arguments.IntegerArgumentType.integer()).executes(c -> {
            ServerPlayer sp = c.getSource().getPlayerOrException();
            ItemStack held = sp.getMainHandItem();
            Weapon w = RpgCombat.weaponOf(held);
            if (w == null) {
                Notify.say(sp, "§cRPG 무기를 손에 들고 쓰세요.");
                return 0;
            }
            int v = Math.max(0, Math.min((int) Rules.get().obj("ENHANCE").num("max"), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "level")));
            WeaponEnhance.write(held, v);
            Notify.say(sp, w.title() + " 강화 +" + v);
            return 1;
        })));
    }

    /** 대장장이 창 (흐름 안에서 — 예: NPC 대화에서). start: "smith" · "enhance" · "craft", 끝나면 done (null 이면 없음) */
    public static void smithLoop(ServerPlayer sp, String start, Runnable done) {
        go(sp, start == null ? "smith" : start, 0, done);
    }

    private static void go(ServerPlayer sp, String next, int i, Runnable done) {
        if (next == null || i >= 20) {
            if (done != null) done.run();
            return;
        }
        switch (next) {
            case "enhance" -> EnhanceWindow.enhanceLoop(sp, n -> go(sp, n, i + 1, done));
            case "craft" -> craftLoop(sp, n -> go(sp, n, i + 1, done));
            default -> raiseLoop(sp, n -> go(sp, n, i + 1, done));
        }
    }

    /** 대장장이 창 열기 (명령어용 — 창이 열려 있거나 강화 망치질 중이면 무시) */
    public static void openSmith(ServerPlayer sp, String start) {
        if (Forms.isOpen(sp)) return;
        smithLoop(sp, start, null);
    }

    // ------------------------------------------------------------ 공통

    static Item ore() {
        return Icon.itemById(SMITH.str("oreItem"));
    }

    /** 칸 설명 · 본문의 성능 한 줄: "공격력 30 · 치명타 12.5%" (방패는 감소) */
    static String statLine(Weapon w) {
        if (w.type().equals("shield")) return "공격력 " + Js.str(w.damage()) + " · 감소 " + Js.str(Js.round(w.passive() * 1000) / 10) + "%";
        return "공격력 " + Js.str(w.damage()) + " · 치명타 " + Js.str(Js.round(w.critChance() * 1000) / 10) + "%";
    }

    /** 렙제: 이 무기를 아직 쓸 수 없으면 {무기 레벨, 지금 최고 레벨}, 아니면 null (levelGap) */
    static double[] levelGap(ServerPlayer sp, Weapon w) {
        double lv = RpgCombat.reqLevelOf(sp);
        return Weapons.needsLevel(w) && lv < w.level() ? new double[] {w.level(), lv} : null;
    }

    private static String costText(double emerald, double ore) {
        return Inv.CURRENCY_NAME + " " + Js.str(emerald) + " · " + ORE_NAME + " " + Js.str(ore);
    }

    private static Slot oreSlot(ServerPlayer sp) {
        int ore = Inv.count(sp, ore());
        return Slot.of(SMITH.str("oreIcon"), "§e보유 " + ORE_NAME + " §f" + ore).count(ore);
    }

    /** 모자란 것을 알림 (낮은 소리 · 머리 위 화난 얼굴 · 화면 하단) + 창 본문 문구 */
    private static String lackMessage(ServerPlayer sp, List<String> lack) {
        Shop.angry(sp);
        Notify.notify(sp, "§c재료 부족: " + String.join(", ", lack) + " 더 필요", 40);
        return "§4재료 부족: " + String.join(", ", lack) + " 더 필요§r";
    }

    /** 만들기 · 올리기 성공 연출: 모루 · 레벨 업 소리, 머리 아래 용암 불똥 · 토템 반짝이 */
    private static void forgeFx(ServerPlayer sp, double anvilPitch, double anvilVol, double upPitch, double upVol, int lava) {
        ServerLevel level = sp.level();
        Vec3 head = sp.getEyePosition();
        SkillFx.play(level, "random.anvil_use", head, anvilPitch, anvilVol);
        SkillFx.play(level, "random.levelup", head, upPitch, upVol);
        for (int i = 0; i < lava; i++) SkillFx.vanilla(level, "minecraft:lava_particle", V.up(head, -0.5));
        SkillFx.vanilla(level, "minecraft:totem_particle", V.up(head, -0.3));
    }

    // ------------------------------------------------------------ 레벨 올리기

    /** 레벨을 올릴 수 있는 영웅 무기 하나 (raisable — 칸 · 아이템 (복사본) · 무기 · 다음 레벨 무기 · 비용) */
    public record Raise(int slot, ItemStack item, Weapon w, Weapon to, int emerald, int ore) {}

    /** 가방 (핫바부터) 의 영웅 무기 중 레벨을 올릴 수 있는 것 (최대 18개) */
    public static List<Raise> raisable(ServerPlayer sp) {
        List<Raise> out = new ArrayList<>();
        for (int i = 0; i < Inv.SIZE && out.size() < MAX_LIST; i++) {
            ItemStack item = Inv.get(sp, i);
            Weapon w = RpgCombat.weaponOf(item);
            Weapons.SmithStep n = Weapons.nextLevelOf(w);
            if (w != null && n != null) out.add(new Raise(i, item.copy(), w, n.to(), (int) n.emerald(), (int) n.ore()));
        }
        return out;
    }

    private static ChestForm raiseForm(ServerPlayer sp, List<Raise> list, String message) {
        ChestForm form = new ChestForm("대장장이 §8· §7영웅 무기 레벨 올리기");
        form.set(TAB_SLOT, Slot.of("textures/items/nether_star", "§6무기 강화로").lore("§7+1 ~ +10 무기 강화 창"));
        // 보유 개수는 그림 위 숫자로 (장식 칸은 숫자가 안 보여서 일반 칸으로, 누르면 새로 그림)
        form.set(EMERALD_SLOT, Shop.balanceSlot(sp));
        form.set(INFO_SLOT, Slot.of("textures/items/iron_ingot", "§6레벨 제한 올리기").lore("§7영웅 무기: 30 → 35 → 40렙", "§7기본 성능·요구 레벨·스탯이", "§7올린 레벨 기준이 됩니다")
                .deco());
        form.set(ORE_SLOT, oreSlot(sp));
        form.set(CRAFT_TAB_SLOT, Slot.of("textures/items/iron_sword", "§6보스 무기 제작으로").lore("§7보스 재료로 영웅·초월 무기 만들기"));
        for (int i = 0; i < list.size(); i++) {
            Raise e = list.get(i);
            double[] gap = levelGap(sp, e.to());
            List<String> lore = new ArrayList<>(List.of("§r§7레벨 제한 §f" + e.w().level() + " → " + e.to().level(), "§r§7" + statLine(e.w()), "§r§f→ " + statLine(e.to()),
                    "§r§a" + costText(e.emerald(), e.ore())));
            if (gap != null) lore.add("§r§c" + Js.str(gap[0]) + "렙부터 사용 · 최고 " + Js.str(gap[1]) + "렙");
            form.set(FIRST + i, Slot.of(e.item(), e.w().name()).lore(lore));
        }
        form.fillEmpty();
        List<String> lines = new ArrayList<>();
        if (list.isEmpty()) lines.add("§8올릴 수 있는 영웅 무기가 없습니다. §0(30·35렙 영웅 무기를 가져오세요)§r");
        else lines.add("§8무기 칸을 누르면 확인 창이 열립니다.§r");
        Obj cost = SMITH.obj("cost");
        lines.add("§8비용: 30→35렙 §0" + costText(cost.obj("30").num("emerald"), cost.obj("30").num("ore")) + "§r");
        lines.add("§8      35→40렙 §0" + costText(cost.obj("35").num("emerald"), cost.obj("35").num("ore")) + "§r");
        if (message != null && !message.isEmpty()) lines.add(message);
        form.body(String.join("\n", lines));
        return form;
    }

    /** 레벨 올리기 확인 창 (confirm). 결과 문구를 done 으로 (취소면 "") */
    private static void confirm(ServerPlayer sp, Raise e, Consumer<String> done) {
        int em = Inv.money(sp);
        int ore = Inv.count(sp, ore());
        ChestForm form = new ChestForm("레벨 제한 올리기");
        form.set(11, Slot.of(Icon.sprite("icon_check"), "§a올리기"));
        form.set(13, Slot.of(Shop.weaponItem(e.to()), e.to().name()).lore(Weapons.weaponInfo(e.to())));
        form.set(15, Slot.of(Icon.sprite("icon_cross"), "§c취소"));
        form.fillEmpty();
        double[] gap = levelGap(sp, e.to());
        List<String> lines = new ArrayList<>();
        lines.add(e.w().darkName() + " §8→ §0" + e.to().level() + "렙§r");
        lines.add("§8" + statLine(e.w()) + " §8→ §0" + statLine(e.to()) + "§r");
        lines.add("§8요구 스탯: " + Weapons.reqsText(e.to().reqs(), true) + "§r");
        if (gap != null) lines.add("§4" + Js.str(gap[0]) + "렙부터 사용 가능 §8(지금 최고 " + Js.str(gap[1]) + ")§r"); // 휴대폰 창 한 줄 (180px)
        lines.add("§8비용: §0" + costText(e.emerald(), e.ore()) + "§8 · 보유 §0" + em + "§8 / §0" + ore + "§r");
        lines.add("§8왼쪽 초록 칸: 올리기 · 오른쪽 빨간 칸: 취소§r");
        form.body(String.join("\n", lines));
        form.show(sp, sel -> done.accept(sel == 11 ? raise(sp, e) : ""));
    }

    /**
     * 레벨 올리기 처리 (raise). 결과 문구 (창 본문용). 무기가 그 칸에 그대로 있는지 · 비용이 있는지 다시 본 뒤 비용을 내고 무기를 바꿈
     * (강화 단계 · 모루 이름은 새 무기로 옮김)
     */
    public static String raise(ServerPlayer sp, Raise e) {
        ItemStack cur = Inv.get(sp, e.slot());
        Weapon cw = RpgCombat.weaponOf(cur);
        if (cw == null || !cw.id().equals(e.w().id())) return "§4무기가 옮겨져서 취소했습니다. 다시 골라 주세요.§r";
        int em = Inv.money(sp);
        int ore = Inv.count(sp, ore());
        if (em < e.emerald() || ore < e.ore()) {
            List<String> lack = new ArrayList<>();
            if (em < e.emerald()) lack.add(Inv.CURRENCY_NAME + " " + (e.emerald() - em));
            if (ore < e.ore()) lack.add(ORE_NAME + " " + (e.ore() - ore));
            return lackMessage(sp, lack);
        }
        // 새 무기를 먼저 만듦 (못 만들면 비용을 받지 않음)
        ItemStack next = Shop.weaponItem(e.to());
        if (next.isEmpty()) return "§4무기를 만들 수 없습니다.§r";
        Component custom = cur.get(DataComponents.CUSTOM_NAME);
        if (custom != null) next.set(DataComponents.CUSTOM_NAME, custom);
        WeaponEnhance.write(next, WeaponEnhance.of(cur));
        if (!Inv.remove(sp, Inv.CURRENCY, e.emerald()) || !Inv.remove(sp, ore(), e.ore())) return "§4결제 오류§r";
        Inv.set(sp, e.slot(), next);
        forgeFx(sp, 1.0, 0.8, 1.4, 0.5, 6);
        Notify.notify(sp, "§6레벨 제한 " + e.w().level() + " → " + e.to().level() + ": " + e.to().name() + Weapons.levelNote(e.to(), RpgCombat.reqLevelOf(sp)), 60);
        return "§2완료: " + e.to().darkName() + " §8(" + statLine(e.to()) + ")§r";
    }

    /** 레벨 올리기 창 반복 (raiseLoop). next: "enhance" = 강화 창으로, "craft" = 보스 무기 제작 창으로, null = 닫음 */
    public static void raiseLoop(ServerPlayer sp, Consumer<String> next) {
        raiseStep(sp, "", 0, next);
    }

    private static void raiseStep(ServerPlayer sp, String message, int guard, Consumer<String> next) {
        if (guard >= 100) {
            next.accept(null);
            return;
        }
        List<Raise> list = raisable(sp);
        raiseForm(sp, list, message).show(sp, sel -> {
            if (sel < 0) next.accept(null);
            else if (sel == TAB_SLOT) next.accept("enhance");
            else if (sel == CRAFT_TAB_SLOT) next.accept("craft");
            else {
                int i = sel - FIRST;
                Raise e = i >= 0 && i < list.size() ? list.get(i) : null;
                if (e == null) raiseStep(sp, "", guard + 1, next);
                else confirm(sp, e, msg -> raiseStep(sp, msg, guard + 1, next));
            }
        });
    }

    // ------------------------------------------------------------ 보스 무기 제작

    /**
     * 재료 하나의 보이는 정보 (matInfo): 몬스터 · 보스 재료 키, 종류 ("boss" · "drop"), 필요 수, 아이템 ID · 이름 · 색 · 그림, 나오는 곳 (보스 이름 또는 "몬스터 Lv.N")
     */
    public record Mat(String key, String kind, int count, String itemId, String name, String color, String icon, String from) {
        public Item item() {
            return Icon.itemById(itemId);
        }
    }

    /** 제작 하나 (craftRecipes): 설정 (BOSS_CRAFT 의 한 줄) · 무기 · 주재료 · 재료 [주재료, 다른 보스 재료…, 전리품…] */
    public record Recipe(Obj r, Weapon w, Mat mat, List<Mat> mats) {
        public String grade() {
            return r.str("grade");
        }

        public int emerald() {
            return (int) r.num("emerald");
        }

        public int ore() {
            return (int) r.num("ore");
        }
    }

    /** 제작 창의 쪽: 등급마다 한 쪽 */
    public record CraftPage(String grade, String label) {}

    /** 영웅 30렙 · 초월 40렙 · 고귀 10·20렙 (CRAFT_PAGES — 고귀는 12단계 새 무기만, 보스 재료 없이) */
    public static final List<CraftPage> CRAFT_PAGES = List.of(new CraftPage("hero", "영웅 30렙"), new CraftPage("transcend", "초월 40렙"),
            new CraftPage("noble", "고귀 10·20렙"));

    /** 윗줄 가운데 칸을 누르면 가는 쪽 (영웅 → 초월 → 고귀 → 영웅 — 제작할 무기가 없는 쪽은 건너뜀) */
    public static int nextCraftPage(List<Recipe> list, int page) {
        for (int i = 1; i <= CRAFT_PAGES.size(); i++) {
            int p = (Math.max(0, page) + i) % CRAFT_PAGES.size();
            if (!craftPage(list, p).isEmpty()) return p;
        }
        return Math.max(0, page);
    }

    /** 이 쪽에 보스 재료가 드는지 (고귀 쪽은 몬스터 전리품 + 에메랄드만) */
    static boolean bossPage(int page) {
        return page < 0 || page >= CRAFT_PAGES.size() || !CRAFT_PAGES.get(page).grade().equals("noble");
    }

    private static Mat matInfo(Crafting.Material m) {
        int count = (int) m.count();
        if (m.kind().equals("boss")) {
            Obj b = BOSS_MATERIALS.objOrNull(m.key());
            if (b == null) return null;
            Obj boss = BOSSES.objOrNull(b.str("boss"));
            String from = boss != null ? boss.strOr("name", b.str("boss")) : b.str("boss");
            return new Mat(m.key(), m.kind(), count, b.str("item"), b.str("name"), b.str("color"), b.str("icon"), from);
        }
        Obj d = MONSTER_DROPS.objOrNull(m.key());
        if (d == null) return null;
        Obj mob = MONSTERS.objOrNull(m.key());
        String from = mob != null ? mob.str("name") + " Lv." + Js.str(mob.num("level")) : m.key();
        return new Mat(m.key(), m.kind(), count, d.str("item"), d.str("name"), d.str("color"), d.str("icon"), from);
    }

    private static List<Recipe> recipes;

    /** 제작 목록 (BOSS_CRAFT 중 무기와 재료가 모두 있는 것, 적힌 순서 — craftRecipes) */
    public static List<Recipe> craftRecipes() {
        if (recipes != null) return recipes;
        List<Recipe> out = new ArrayList<>();
        for (Obj r : Crafting.RECIPES) {
            Weapon w = Weapons.weaponFrom(r.str("type"), r.num("level"), r.str("grade"));
            List<Mat> mats = new ArrayList<>();
            boolean all = true;
            for (Crafting.Material m : Crafting.craftMaterials(r)) {
                Mat x = matInfo(m);
                if (x == null) all = false;
                else mats.add(x);
            }
            if (w != null && !w.legacy() && !mats.isEmpty() && all) out.add(new Recipe(r, w, mats.get(0), List.copyOf(mats)));
        }
        recipes = List.copyOf(out);
        return recipes;
    }

    /** 한 쪽의 제작 목록 (가운데 줄 9칸까지 — craftPage) */
    public static List<Recipe> craftPage(List<Recipe> list, int page) {
        String g = page >= 0 && page < CRAFT_PAGES.size() ? CRAFT_PAGES.get(page).grade() : null;
        List<Recipe> out = new ArrayList<>();
        for (Recipe e : list) if (e.grade().equals(g) && out.size() < 9) out.add(e);
        return out;
    }

    /** 아랫줄 재료: 보이는 것 (shown) · 아홉째 칸에 모은 나머지 (rest — "그 밖의 재료") */
    public record PageMats(List<Mat> shown, List<Mat> rest) {}

    /**
     * 한 쪽 무기들의 재료 (아랫줄 9칸 — pageMaterials): 주재료 → 전리품 → 다른 보스 재료 (무기마다 둘째 재료, 그다음 셋째 재료 순서), 같은 재료는 한 번.
     * 9종이 넘으면 앞의 8종 + 아홉째 칸에 나머지
     */
    public static PageMats pageMaterials(List<Recipe> list) {
        Map<String, Mat> seen = new LinkedHashMap<>();
        for (Recipe e : list) seen.putIfAbsent(e.mats().get(0).itemId(), e.mats().get(0));
        for (Recipe e : list) for (Mat m : e.mats()) if (m.kind().equals("drop")) seen.putIfAbsent(m.itemId(), m);
        int depth = 0;
        for (Recipe e : list) depth = Math.max(depth, e.mats().size());
        for (int c = 1; c < depth; c++) {
            for (Recipe e : list) {
                if (c < e.mats().size() && e.mats().get(c).kind().equals("boss")) seen.putIfAbsent(e.mats().get(c).itemId(), e.mats().get(c));
            }
        }
        List<Mat> all = new ArrayList<>(seen.values());
        return all.size() <= 9 ? new PageMats(List.copyOf(all), List.of()) : new PageMats(List.copyOf(all.subList(0, 8)), List.copyOf(all.subList(8, all.size())));
    }

    /** 처음 여는 쪽 (firstCraftPage): 그 쪽 무기들의 재료를 가장 많이 모은 쪽 (무기마다 필요한 만큼까지 셈, 같으면 앞쪽 — 영웅) */
    public static int firstCraftPage(ServerPlayer sp, List<Recipe> list) {
        Map<String, Integer> have = new HashMap<>();
        int[] score = new int[CRAFT_PAGES.size()];
        for (int p = 0; p < CRAFT_PAGES.size(); p++) {
            for (Recipe e : craftPage(list, p)) {
                for (Mat m : e.mats()) score[p] += Math.min(have.computeIfAbsent(m.itemId(), id -> Inv.count(sp, Icon.itemById(id))), m.count());
            }
        }
        int best = 0;
        for (int p = 1; p < score.length; p++) if (score[p] > score[best]) best = p;
        return best;
    }

    /** 필요한 것 하나 (같은 아이템이 여러 줄이면 합침) */
    private static final class Need {
        final String name;
        int need;

        Need(String name, int need) {
            this.name = name;
            this.need = need;
        }
    }

    private static void want(Map<String, Need> need, String itemId, String name, int n) {
        Need x = need.get(itemId);
        if (x != null) x.need += n;
        else need.put(itemId, new Need(name, n));
    }

    /**
     * 모자란 것 ["칠흑 먹물 3", …] (없으면 빈 목록 — craftLack). 같은 아이템이 여러 줄이면 합쳐서 셈 (재료 · 에메랄드 · 별철 광석 —
     * 모두 있는 것을 확인한 뒤 craft 가 차례로 내므로, 내는 도중에 모자라 앞의 것만 사라지는 일이 없게)
     */
    public static List<String> craftLack(ServerPlayer sp, Recipe e) {
        Map<String, Need> need = new LinkedHashMap<>();
        for (Mat m : e.mats()) want(need, m.itemId(), m.name(), m.count());
        want(need, CONFIG.str("CURRENCY_ITEM"), Inv.CURRENCY_NAME, e.emerald());
        want(need, SMITH.str("oreItem"), ORE_NAME, e.ore());
        List<String> lack = new ArrayList<>();
        for (Map.Entry<String, Need> x : need.entrySet()) {
            int have = Inv.count(sp, Icon.itemById(x.getKey()));
            if (have < x.getValue().need) lack.add(x.getValue().name + " " + (x.getValue().need - have));
        }
        return lack;
    }

    /** 무기 칸 설명의 재료 한 줄: "칠흑 먹물 3/8 (크라켄)" — 채웠으면 초록 */
    private static String matLine(ServerPlayer sp, Mat m) {
        int have = Inv.count(sp, m.item());
        return "§r" + (have >= m.count() ? "§a" : "§7") + m.name() + " §f" + Math.min(have, m.count()) + "/" + m.count() + " §8(" + m.from() + ")";
    }

    /** 제작 창 (craftForm). list = 이 쪽의 제작 목록 (craftPage), page 0 = 영웅 · 1 = 초월 · 2 = 고귀 (12단계) */
    private static ChestForm craftForm(ServerPlayer sp, List<Recipe> list, String message, int page, int nextPage) {
        CraftPage p = page >= 0 && page < CRAFT_PAGES.size() ? CRAFT_PAGES.get(page) : CRAFT_PAGES.get(0);
        CraftPage other = CRAFT_PAGES.get(nextPage >= 0 && nextPage < CRAFT_PAGES.size() ? nextPage : 0);
        boolean boss = bossPage(page);
        String what = boss ? "보스 무기 제작" : "무기 제작";
        ChestForm form = new ChestForm("대장장이 §8· §7" + what + " §8· §7" + p.label());
        form.set(TAB_SLOT, Slot.of("textures/items/nether_star", "§6무기 강화로").lore("§7+1 ~ +10 무기 강화 창"));
        form.set(EMERALD_SLOT, Shop.balanceSlot(sp));
        String icon = !boss ? "textures/items/golden_sword" : page == 0 ? "textures/items/diamond_sword" : "textures/items/netherite_sword";
        form.set(CRAFT_INFO_SLOT, Slot.of(icon, "§6" + what + " §7· §f" + p.label())
                .lore(boss ? new String[] {"§e누르면 " + other.label() + " 무기 보기", "§7여러 보스의 재료와 일반 몬스터", "§7전리품을 섞어 무기를 만듭니다",
                        "§7(강화 +0) 영웅 무기는 30렙으로 만들어", "§7레벨 올리기 창에서 40렙까지", "§7초월 무기는 40렙으로 만듦"}
                        : new String[] {"§e누르면 " + other.label() + " 무기 보기", "§7일반 몬스터 전리품과 에메랄드로", "§7새 무기 (창 · 낫 · 전투 망치 ·", "§7성스러운 홀) 의 고귀 무기를",
                                "§7만듭니다 (강화 +0)"}));
        form.set(ORE_SLOT, oreSlot(sp));
        form.set(CRAFT_TAB_SLOT, Slot.of("textures/items/iron_ingot", "§6레벨 올리기로").lore("§7영웅 무기 레벨 제한 올리기 창"));
        for (int i = 0; i < list.size(); i++) {
            Recipe e = list.get(i);
            boolean ok = craftLack(sp, e).isEmpty();
            double[] gap = levelGap(sp, e.w());
            List<String> lore = new ArrayList<>();
            lore.add("§r§7" + e.w().typeLabel() + " · " + statLine(e.w()));
            for (Mat m : e.mats()) lore.add(matLine(sp, m));
            lore.add("§r§7" + Inv.CURRENCY_NAME + " §f" + e.emerald() + (e.ore() > 0 ? " §7· " + ORE_NAME + " §f" + e.ore() : ""));
            lore.add(ok ? "§r§a만들 수 있음" : "§r§c재료 부족");
            if (gap != null) lore.add("§r§c" + Js.str(gap[0]) + "렙부터 사용 · 최고 " + Js.str(gap[1]) + "렙");
            form.set(CRAFT_FIRST + i, Slot.of(Shop.weaponItem(e.w()), e.w().name()).lore(lore));
        }
        // 아랫줄: 이 쪽 무기들의 재료 (가진 수). 9종이 넘으면 마지막 칸 = 그 밖의 재료 (설명에 하나씩)
        PageMats pm = pageMaterials(list);
        for (int i = 0; i < pm.shown().size(); i++) {
            Mat m = pm.shown().get(i);
            int n = Inv.count(sp, m.item());
            form.set(MAT_FIRST + i, Slot.of(Icon.item(m.item()), m.color() + "보유 " + m.name() + " §f" + n).count(n)
                    .lore("§7" + (m.kind().equals("boss") ? m.from() + "에게서 얻음" : m.from() + " 전리품")));
        }
        if (!pm.rest().isEmpty()) {
            int n = 0;
            List<String> lore = new ArrayList<>();
            for (Mat m : pm.rest()) {
                int k = Inv.count(sp, m.item());
                n += k;
                lore.add("§r§7" + m.name() + " §f" + k + " §8(" + m.from() + ")");
            }
            form.set(MAT_FIRST + pm.shown().size(), Slot.of(Icon.item(pm.rest().get(0).item()), "§7그 밖의 재료 §f" + pm.rest().size() + "종").count(n).lore(lore));
        }
        form.fillEmpty();
        List<String> lines = new ArrayList<>();
        lines.add(list.isEmpty() ? "§8만들 수 있는 " + (boss ? "보스 " : "") + "무기가 없습니다.§r"
                : "§8무기 칸을 누르면 확인 창이 열립니다. 윗줄 가운데 칸: " + other.label() + " 쪽.§r");
        lines.add(boss ? "§8재료: 여러 보스의 재료 + 일반 몬스터 전리품 (무기 칸 설명).§r" : "§8재료: 일반 몬스터 전리품 + 에메랄드 (무기 칸 설명).§r");
        lines.add("§8아랫줄: 이 쪽 무기들의 재료를 가진 수.§r");
        if (message != null && !message.isEmpty()) lines.add(message);
        form.body(String.join("\n", lines));
        return form;
    }

    /** 보스 무기 제작 확인 창 (confirmCraft). 결과 문구를 done 으로 (취소면 "") */
    private static void confirmCraft(ServerPlayer sp, Recipe e, Consumer<String> done) {
        ChestForm form = new ChestForm(e.grade().equals("noble") ? "무기 제작" : "보스 무기 제작");
        form.set(11, Slot.of(Icon.sprite("icon_check"), "§a만들기"));
        form.set(13, Slot.of(Shop.weaponItem(e.w()), e.w().name()).lore(Weapons.weaponInfo(e.w())));
        form.set(15, Slot.of(Icon.sprite("icon_cross"), "§c취소"));
        form.fillEmpty();
        double[] gap = levelGap(sp, e.w());
        // 재료는 "이름 가진 수/필요" 로 한 줄에 둘씩 (휴대폰 창 한 줄 180px)
        List<String> mats = new ArrayList<>();
        for (Mat m : e.mats()) {
            int have = Inv.count(sp, m.item());
            mats.add((have >= m.count() ? "§2" : "§4") + m.name() + " " + have + "/" + m.count());
        }
        List<String> lines = new ArrayList<>();
        lines.add(e.w().darkName() + "§r");
        lines.add("§8" + statLine(e.w()) + " · 요구 스탯: " + Weapons.reqsText(e.w().reqs(), true) + "§r");
        if (gap != null) lines.add("§4" + Js.str(gap[0]) + "렙부터 사용 가능 §8(지금 최고 " + Js.str(gap[1]) + ")§r");
        for (int i = 0; i < mats.size(); i += 2) lines.add("§8" + String.join(" §8· ", mats.subList(i, Math.min(mats.size(), i + 2))) + "§r");
        lines.add("§8" + Inv.CURRENCY_NAME + " §0" + Inv.money(sp) + "/" + e.emerald()
                + (e.ore() > 0 ? " §8· " + ORE_NAME + " §0" + Inv.count(sp, ore()) + "/" + e.ore() : "") + "§r");
        lines.add("§8왼쪽 초록 칸: 만들기 · 오른쪽 빨간 칸: 취소§r");
        form.body(String.join("\n", lines));
        form.show(sp, sel -> done.accept(sel == 11 ? craft(sp, e) : ""));
    }

    /**
     * 보스 무기 만들기 (craft). 결과 문구 (창 본문용). 비용을 다시 확인한 뒤 무기를 먼저 만들고, 비용을 내고 줌 (가방이 차면 발밑에).
     * 모자라면 아무것도 쓰지 않음
     */
    public static String craft(ServerPlayer sp, Recipe e) {
        List<String> lack = craftLack(sp, e);
        if (!lack.isEmpty()) return lackMessage(sp, lack);
        ItemStack item = Shop.weaponItem(e.w());
        if (item.isEmpty()) return "§4무기를 만들 수 없습니다.§r";
        // 모두 있는 것을 위에서 확인했으므로 차례로 냄 (하나라도 실패하면 그 뒤는 내지 않음)
        for (Mat m : e.mats()) if (!Inv.remove(sp, m.item(), m.count())) return "§4결제 오류§r";
        if (!Inv.remove(sp, Inv.CURRENCY, e.emerald()) || !Inv.remove(sp, ore(), e.ore())) return "§4결제 오류§r";
        Inv.give(sp, item);
        forgeFx(sp, 0.9, 0.9, 1.2, 0.6, 8);
        Notify.notify(sp, "§6" + (e.grade().equals("noble") ? "무기 제작: " : "보스 무기 제작: ") + e.w().name() + Weapons.levelNote(e.w(), RpgCombat.reqLevelOf(sp)), 60);
        return "§2완료: " + e.w().darkName() + " §8(" + statLine(e.w()) + ")§r";
    }

    /** 보스 무기 제작 창 반복 (craftLoop). next: "enhance" = 강화 창으로, "smith" = 레벨 올리기 창으로, null = 닫음 */
    public static void craftLoop(ServerPlayer sp, Consumer<String> next) {
        craftStep(sp, "", firstCraftPage(sp, craftRecipes()), 0, next);
    }

    /** 제작 창을 그 쪽 (0 영웅 · 1 초월 · 2 고귀) 부터 엶 (시험 · 사진첩 — 창이 열려 있으면 무시) */
    public static void openCraftPage(ServerPlayer sp, int page) {
        if (Forms.isOpen(sp)) return;
        craftStep(sp, "", Math.max(0, Math.min(CRAFT_PAGES.size() - 1, page)), 0, n -> {});
    }

    private static void craftStep(ServerPlayer sp, String message, int page, int guard, Consumer<String> next) {
        if (guard >= 100) {
            next.accept(null);
            return;
        }
        List<Recipe> list = craftPage(craftRecipes(), page);
        int nextPage = nextCraftPage(craftRecipes(), page);
        craftForm(sp, list, message, page, nextPage).show(sp, sel -> {
            if (sel < 0) next.accept(null);
            else if (sel == TAB_SLOT) next.accept("enhance");
            else if (sel == CRAFT_TAB_SLOT) next.accept("smith");
            else if (sel == CRAFT_INFO_SLOT) craftStep(sp, "", nextPage, guard + 1, next); // 윗줄 가운데 칸 = 다음 쪽 (영웅 → 초월 → 고귀)
            else {
                int i = sel - CRAFT_FIRST;
                Recipe e = sel >= CRAFT_FIRST && sel < MAT_FIRST && i < list.size() ? list.get(i) : null;
                if (e == null) craftStep(sp, "", page, guard + 1, next);
                else confirmCraft(sp, e, msg -> craftStep(sp, msg, page, guard + 1, next));
            }
        });
    }
}
