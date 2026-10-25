package mmorpg.fabric.smith;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import mmorpg.core.Enhance;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.BedrockText;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.gear.WeaponEnhance;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.ui.ChestForm;
import mmorpg.fabric.ui.Forms;
import mmorpg.fabric.ui.Icon;
import mmorpg.fabric.ui.Inv;
import mmorpg.fabric.ui.Slot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 무기 강화 (enhance.js, 대장장이): +1 ~ +10.
 * <ul>
 *   <li>단계마다 성공 확률 · 재료 (rules.json ENHANCE — 강화석, +6 부터 빛나는 강화석, 등급마다 배수 — 규칙 코어 Enhance.enhanceCost)</li>
 *   <li>재료는 망치질 (연출) 이 끝나 결과를 정하는 순간에 받음 → 도중에 나가거나 무기를 옮기면 아무것도 쓰지 않고 취소</li>
 *   <li>+1 ~ +5 는 실패해도 그대로, +6 부터 실패하면 한 단계 떨어짐. 수호 주문서를 쓰면 그대로 (실패했을 때만 사라짐)</li>
 *   <li>강화 단계는 아이템에 (WeaponEnhance — 이름 앞 +N · 설명 · 무기 피해 × (1 + 보너스) 는 아이템 쪽이 그림)</li>
 *   <li>announceFrom (7) 강 이상 성공: 서버 모두에게 채팅 알림 + 폭죽 소리, 강화한 사람 눈앞 하늘에 폭죽</li>
 * </ul>
 * 창 (상자 9 × 3): 윗줄 [레벨 올리기로] · 강화석 · 빛나는 강화석 · 안내 · 수호 주문서 · [보스 무기 제작으로], 아래 두 줄에 가진 무기 (최대 18개).
 * 자바에서 달라진 점: 애드온은 await 로 망치질을 기다리지만 자바는 창 없이 Forms.busy 로 묶고 Tasks.later 로 끝을 기다린 뒤 결과 문구로 창을 다시 엶.
 * 성공 판정의 난수는 rand (시험이 정할 수 있게).
 */
public final class EnhanceWindow {
    private EnhanceWindow() {}

    private static final Rules R = Rules.get();
    private static final Obj ENHANCE = R.obj("ENHANCE");
    static final Obj STONE = R.obj("RAID_MATERIALS").obj("stone");
    static final Obj BRIGHT = R.obj("RAID_MATERIALS").obj("bright");
    static final Obj SCROLL = R.obj("RAID_MATERIALS").obj("scroll");

    /** 망치질 연출 틱 (ENHANCE.animTicks — 이 동안 창 없이 연출만) */
    public static final int ANIM_TICKS = (int) ENHANCE.num("animTicks");
    /** 최대 강화 (ENHANCE.max) */
    public static final int MAX = (int) Enhance.MAX;
    static final int DOWN_FROM = (int) ENHANCE.num("downFrom");
    static final int ANNOUNCE_FROM = (int) ENHANCE.num("announceFrom");

    /** 강화 성공 판정의 난수 (0 이상 1 미만 — 이 값이 성공 확률보다 작으면 성공. 시험이 정할 수 있게) */
    public static DoubleSupplier rand = Math::random;

    static final int TAB_SLOT = 0;
    /** 0.43.0 장신구 · 아티팩트 강화 탭 (무기 강화 창 ↔ 장신구 · 아티팩트 강화 창) */
    static final int GEAR_TAB_SLOT = 1;
    static final int CRAFT_TAB_SLOT = 8;
    static final int STONE_SLOT = 2;
    static final int BRIGHT_SLOT = 3;
    static final int INFO_SLOT = 4;
    static final int SCROLL_SLOT = 6;
    static final int FIRST = 9;
    static final int MAX_LIST = 18;

    private static final double[] GOLD = {1.0, 0.82, 0.3};
    private static final double[] WHITE = {1, 1, 1};
    private static final double[] SMOKE = {0.45, 0.45, 0.5};
    private static final double[] RED = {0.9, 0.2, 0.15};
    /** 망치질 마법진 색 (무기 등급 순서) */
    private static final double[][] HAMMER = {GOLD, GOLD, {0.8, 0.55, 1.0}, {1.0, 0.6, 0.2}, {1.0, 0.3, 0.3}};
    /** 하늘 폭죽 색 */
    private static final double[][] SKY = {GOLD, {1.0, 0.4, 0.4}, {0.5, 0.8, 1.0}, {0.7, 1.0, 0.5}, {1.0, 0.55, 1.0}};

    /** 가방의 RPG 무기 하나 (enhanceable — 칸 · 아이템 (그 때의 복사본) · 무기 · 강화 단계) */
    public record Entry(int slot, ItemStack item, Weapon w, int n) {}

    static Item itemOf(Obj m) {
        return Icon.itemById(m.str("item"));
    }

    static String pct(double x) {
        return Js.str(Js.round(x * 100)) + "%";
    }

    /** 가방 (핫바부터) 의 RPG 무기 (최대 18개 — enhanceable) */
    public static List<Entry> enhanceable(ServerPlayer sp) {
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < Inv.SIZE && out.size() < MAX_LIST; i++) {
            ItemStack item = Inv.get(sp, i);
            Weapon w = RpgCombat.weaponOf(item);
            if (w != null) out.add(new Entry(i, item.copy(), w, WeaponEnhance.of(item)));
        }
        return out;
    }

    static String costText(Enhance.Cost c, boolean dark) {
        String v = dark ? "§0" : "§f";
        String l = dark ? "§8" : "§7";
        List<String> parts = new ArrayList<>();
        parts.add(STONE.str("name") + " " + v + Js.str(c.stone()) + l);
        if (c.bright() > 0) parts.add(BRIGHT.str("name") + " " + v + Js.str(c.bright()) + l);
        return String.join(" · ", parts);
    }

    static String failText(Enhance.Cost c) {
        return c.down() ? "실패하면 한 단계 떨어짐 (수호 주문서로 방지)" : "실패해도 그대로";
    }

    /** 무기 칸 이름 (강화했으면 "+N 이름") */
    private static String slotName(Entry e) {
        return e.n() > 0 ? "§r" + e.w().grade().color() + "+" + e.n() + " " + e.w().title() : e.w().name();
    }

    /** 무기 칸 설명 */
    private static List<String> slotLore(Entry e) {
        if (e.n() >= MAX) return List.of("§r§6최대 강화 +" + MAX, "§r§7피해 +" + pct(Enhance.enhanceBonus(e.n())));
        Enhance.Cost c = Enhance.enhanceCost(e.n(), e.w().grade().id());
        if (c == null) return List.of();
        return List.of("§r§7강화 §f+" + e.n() + " → +" + (e.n() + 1) + "§7 · 성공 §a" + pct(c.rate()),
                "§r§7피해 +" + pct(Enhance.enhanceBonus(e.n())) + " → §f+" + pct(Enhance.enhanceBonus(e.n() + 1)), "§r§7재료: " + costText(c, false),
                "§r" + (c.down() ? "§c" : "§8") + failText(c));
    }

    private static ChestForm buildForm(ServerPlayer sp, List<Entry> list, String message) {
        int st = Inv.count(sp, itemOf(STONE));
        int br = Inv.count(sp, itemOf(BRIGHT));
        int sc = Inv.count(sp, itemOf(SCROLL));
        ChestForm form = new ChestForm("대장장이 §8· §7무기 강화");
        form.set(TAB_SLOT, Slot.of("textures/items/iron_ingot", "§6레벨 올리기로").lore("§7영웅 · 초월 무기 레벨 제한 올리기 창"));
        if (Enhance.gearEnabled()) {
            form.set(GEAR_TAB_SLOT, Slot.of(Icon.item(GearEnhance.icon()), "§d장신구 · 아티팩트 강화로").lore("§7낀 장신구 · 판의 아티팩트 · 가방의 것", "§7옵션 · 스탯 줄 +4 ~ +50%"));
        }
        form.set(STONE_SLOT, Slot.of(STONE.str("icon"), STONE.str("color") + "보유 " + STONE.str("name") + " §f" + st).count(st));
        form.set(BRIGHT_SLOT, Slot.of(BRIGHT.str("icon"), BRIGHT.str("color") + "보유 " + BRIGHT.str("name") + " §f" + br).count(br));
        form.set(INFO_SLOT, Slot.of("textures/items/nether_star", "§6무기 강화 §f(+1 ~ +" + MAX + ")")
                .lore("§7성공하면 무기 피해가 오릅니다", "§7+" + DOWN_FROM + "부터 실패하면 한 단계 하락", "§7" + SCROLL.str("name") + "가 있으면 하락 방지",
                        "§7+" + ANNOUNCE_FROM + " 이상 성공은 서버 전체 알림")
                .deco());
        form.set(SCROLL_SLOT, Slot.of(SCROLL.str("icon"), SCROLL.str("color") + "보유 " + SCROLL.str("name") + " §f" + sc).count(sc));
        form.set(CRAFT_TAB_SLOT, Slot.of("textures/items/iron_sword", "§6보스 무기 제작으로").lore("§7보스 재료로 영웅·초월 무기 만들기", "§7공허 전리품으로 공허의 갑옷 만들기",
                "§7전리품 · 보스 재료로 장신구 만들기"));
        for (int i = 0; i < list.size(); i++) {
            Entry e = list.get(i);
            form.set(FIRST + i, Slot.of(e.item(), slotName(e)).lore(slotLore(e)));
        }
        form.fillEmpty();
        List<String> lines = new ArrayList<>();
        if (list.isEmpty()) lines.add("§8강화할 RPG 무기가 없습니다. §0(무기를 가지고 오세요)§r");
        else lines.add("§8무기 칸을 누르면 확인 창이 열립니다.§r");
        lines.add("§8성공: +1 §095%§8 · +5 §065%§8 · +7 §045%§8 · +10 §015%§r");
        lines.add("§8+1~+5 실패: 그대로 · +6~+10 실패: §4한 단계 하락§r");
        if (message != null && !message.isEmpty()) lines.add(message);
        form.body(String.join("\n", lines));
        return form;
    }

    /** 확인 창 (confirm). 누른 것을 pick 으로: "go" · "guard" (수호 주문서 쓰고) · "" (취소) */
    private static void confirm(ServerPlayer sp, Entry e, Consumer<String> pick) {
        Enhance.Cost c = Enhance.enhanceCost(e.n(), e.w().grade().id());
        if (c == null) {
            pick.accept("");
            return;
        }
        int st = Inv.count(sp, itemOf(STONE));
        int br = Inv.count(sp, itemOf(BRIGHT));
        int sc = Inv.count(sp, itemOf(SCROLL));
        boolean scroll = c.down() && sc > 0;
        ChestForm form = new ChestForm("무기 강화 §8· §7+" + e.n() + " → +" + (e.n() + 1));
        form.set(11, Slot.of(Icon.sprite("icon_check"), "§a강화하기 §7(성공 " + pct(c.rate()) + ")").lore("§r§7" + failText(c)));
        if (scroll) {
            form.set(12, Slot.of(SCROLL.str("icon"), "§6" + SCROLL.str("name") + " 쓰고 강화").count(sc).lore("§r§7실패해도 단계가 떨어지지 않음", "§r§7(실패했을 때만 사라짐)"));
        }
        form.set(13, Slot.of(e.item(), slotName(e)).lore(Weapons.weaponInfo(e.w(), false, e.n())));
        form.set(15, Slot.of(Icon.sprite("icon_cross"), "§c취소"));
        form.fillEmpty();
        List<String> lines = List.of(
                (e.n() > 0 ? "§0+" + e.n() + " " : "") + e.w().darkName() + " §8→ §0§l+" + (e.n() + 1) + "§r",
                "§8성공 확률 §2§l" + pct(c.rate()) + "§r§8 · 피해 +" + pct(Enhance.enhanceBonus(e.n())) + " → §0+" + pct(Enhance.enhanceBonus(e.n() + 1)) + "§r",
                "§8재료 " + costText(c, true) + " §8(보유 §0" + st + "§8" + (c.bright() > 0 ? " / §0" + br : "") + "§8)§r",
                (c.down() ? "§4" : "§8") + failText(c) + "§r",
                "§8초록 칸: 강화" + (scroll ? " · 주문서 칸: 하락 방지" : "") + " · 빨간 칸: 취소§r");
        form.body(String.join("\n", lines));
        form.show(sp, sel -> pick.accept(sel == 11 ? "go" : sel == 12 && scroll ? "guard" : ""));
    }

    // ------------------------------------------------------------ 연출

    /** 눈앞 연출 자리 (뽑기 마법진과 같은 자리: 눈앞 2.2칸, 조준점 조금 아래 — at), 그 아래 땅 (ground), 보는 수평 방향 (f), 화면 가로 (side) */
    private record Altar(Vec3 at, Vec3 ground, Vec3 f, Vec3 side) {}

    private static Altar altarOf(ServerPlayer sp) {
        Vec3 f = V.flat(sp.getViewVector(1F));
        Vec3 ground = V.add(sp.position(), f, 2.2);
        return new Altar(V.up(ground, 1.35), ground, f, V.rightOf(f));
    }

    /** 망치질 연출 (animTicks 동안): 눈앞 마법진이 한 칸씩 그려지고 불꽃이 모이며 모루 소리 3번 */
    private static void hammerFx(ServerPlayer sp, Entry e) {
        hammerFx(sp, e.w().gradeIndex());
    }

    /** 망치질 연출 (등급 순번 gi) — 0.43.0 장신구 · 아티팩트 강화도 */
    static void hammerFx(ServerPlayer sp, int gi) {
        double[] color = gi >= 0 && gi < HAMMER.length ? HAMMER[gi] : GOLD;
        int T = ANIM_TICKS;
        for (int t = 0; t < T; t += 2) {
            final int tt = t;
            Tasks.later(t, () -> {
                if (sp.isRemoved()) return;
                ServerLevel level = sp.level();
                Altar al = altarOf(sp);
                SkillFx.fx(level, "rpg:magic_circle", al.at(), Opt.of("size", 0.5 + tt * 0.01, "life", 0.14, "spin", tt * 16.0, "progress", Math.min(1, (tt + 2.0) / T),
                        "color", color, "alpha", 0.95));
                double a = tt * 0.8;
                Vec3 from = V.add(V.add(al.at(), al.side(), Math.cos(a) * 1.1), V.UP, Math.sin(a) * 0.8);
                SkillFx.fx(level, "rpg:arrow_spark", from, Opt.of("dir", V.sub(al.at(), from), "speed", 2.4, "size", 0.12, "grow", 0.5, "life", 0.4, "color", GOLD,
                        "alpha", 1.0));
            });
        }
        int[] hits = {0, 8, 16};
        for (int i = 0; i < hits.length; i++) {
            final int ii = i;
            Tasks.later(hits[i], () -> {
                if (sp.isRemoved()) return;
                Altar al = altarOf(sp);
                SkillFx.play(sp.level(), "random.anvil_use", al.at(), 0.9 + ii * 0.15, 0.7);
                SkillFx.sparks(sp.level(), al.at(), GOLD, 6, 4);
            });
        }
    }

    /** 성공: 마법진 섬광 · 금빛 폭발 · 고리 · 불꽃 · 바닥 룬 · 토템 반짝이 · 레벨 업 소리 */
    static void successFx(ServerPlayer sp, int n) {
        ServerLevel level = sp.level();
        Altar al = altarOf(sp);
        SkillFx.fx(level, "rpg:magic_circle_flash", al.at(), Opt.of("size", 0.9 + n * 0.04, "life", 0.55, "spin", Kit.rand() * 360, "progress", 1.0, "color", GOLD,
                "alpha", 1.0));
        SkillFx.burst(level, al.at(), GOLD, 1.3 + n * 0.08, 0.4);
        SkillFx.burst(level, al.at(), WHITE, 0.8, 0.3);
        SkillFx.ring(level, al.at(), al.f(), GOLD, 0.8, 0.4, 2);
        SkillFx.sparks(level, al.at(), GOLD, 12, 6);
        SkillFx.rune(level, al.ground(), Opt.of("size", 1.4, "life", 0.8, "color", GOLD, "alpha", 0.9, "rate", 90.0, "grow", 0.3));
        SkillFx.vanilla(level, "minecraft:totem_particle", V.up(sp.position(), 1.2));
        SkillFx.play(level, "random.levelup", sp.position(), 1.2 + n * 0.03, 0.9);
        SkillFx.play(level, "random.anvil_use", sp.position(), 1.6, 0.5);
    }

    /** 높은 단계 성공: 서버 모두에게 채팅 알림 + 폭죽 소리, 강화한 사람 눈앞 하늘에 폭죽 (최고 강화면 7발, 아니면 4발) */
    private static void announce(ServerPlayer sp, Weapon w, int n) {
        announce(sp, w.grade().color(), w.grade().name(), w.title(), n);
    }

    /** 높은 단계 성공 알림 (등급 색 · 등급 이름 · 이름 — 0.43.0 장신구 · 아티팩트도) */
    static void announce(ServerPlayer sp, String gradeColor, String gradeName, String title, int n) {
        String top = n >= MAX ? " §d§l(최고 강화!)" : "";
        MinecraftServer server = sp.level().getServer();
        server.getPlayerList().broadcastSystemMessage(Component.literal(BedrockText.toJava("§6§l[강화] §r§f" + sp.getName().getString() + "님이 " + gradeColor
                + gradeName + " 등급§r §f[" + title + "] §e§l" + n + "강§r§f을 성공하였습니다!" + top)), false);
        for (ServerPlayer pl : server.getPlayerList().getPlayers()) {
            SkillFx.playFor(pl, n >= MAX ? "firework.large_blast" : "firework.blast", 1.0, 0.8);
            if (pl != sp) SkillFx.playFor(pl, "firework.twinkle", 1.2, 0.5);
        }
        int shots = n >= MAX ? 7 : 4;
        // 강화한 사람의 눈앞 하늘 (1인칭 시야 안: 4.5 ~ 5.3칸 앞, 눈높이 +1.5 ~ 2.4칸), 왼쪽 · 오른쪽을 번갈아
        int[] order = shots == 7 ? new int[] {3, 1, 5, 0, 6, 2, 4} : new int[] {1, 2, 0, 3};
        double width = shots == 7 ? 6 : 4.8;
        for (int i = 0; i < shots; i++) {
            final int ii = i;
            Tasks.later(4 + i * 5, () -> {
                if (sp.isRemoved()) return;
                ServerLevel level = sp.level();
                Vec3 f = V.flat(sp.getViewVector(1F));
                Vec3 eye = sp.getEyePosition();
                Vec3 p = V.add(V.add(eye, f, 4.5 + (ii % 2) * 0.8), V.rightOf(f), ((double) order[ii] / (shots - 1) - 0.5) * width);
                p = new Vec3(p.x, eye.y + 1.5 + (ii % 3) * 0.45, p.z);
                double[] col = SKY[ii % SKY.length];
                SkillFx.fx(level, "rpg:arrow_shot", V.up(p, -2.2), Opt.of("dir", V.UP, "speed", 0.0, "size", 2.0, "thick", 0.08, "life", 0.2, "color", col,
                        "alpha", 0.8)); // 올라가는 꼬리
                SkillFx.burst(level, p, col, 1.8, 0.45);
                SkillFx.sparks(level, p, col, 12, 6);
                SkillFx.play(level, "firework.twinkle", p, 1.0 + ii * 0.05, 0.6);
            });
        }
    }

    /** 실패: 연기 (떨어지면 붉은 폭발 · 모루 깨지는 소리), 수호 주문서가 지켰으면 금빛 고리 · 방패 소리 */
    static void failFx(ServerPlayer sp, boolean down, boolean guarded) {
        ServerLevel level = sp.level();
        Altar al = altarOf(sp);
        SkillFx.burst(level, al.at(), down ? RED : SMOKE, 1.0, 0.35);
        for (int i = 0; i < 4; i++) {
            SkillFx.vanilla(level, "minecraft:basic_smoke_particle", V.add(al.at(), new Vec3((Kit.rand() - 0.5) * 0.8, Kit.rand() * 0.4, (Kit.rand() - 0.5) * 0.8)));
        }
        if (guarded) {
            SkillFx.ring(level, al.at(), al.f(), GOLD, 0.9, 0.45, 1.5);
            SkillFx.play(level, "item.shield.block", al.at(), 1.1, 0.8);
        }
        SkillFx.play(level, down ? "random.anvil_break" : "random.anvil_land", al.at(), down ? 0.8 : 0.6, 0.7);
        SkillFx.play(level, "random.fizz", al.at(), 0.8, 0.6);
    }

    // ------------------------------------------------------------ 강화

    /** 재료가 모자란 것 ["강화석 2", …] (없으면 빈 목록 — lacking). useScroll 이면 수호 주문서 1장도 */
    static List<String> lacking(ServerPlayer sp, Enhance.Cost c, boolean useScroll) {
        List<String> out = new ArrayList<>();
        int st = Inv.count(sp, itemOf(STONE));
        int br = Inv.count(sp, itemOf(BRIGHT));
        if (st < c.stone()) out.add(STONE.str("name") + " " + Js.str(c.stone() - st));
        if (br < c.bright()) out.add(BRIGHT.str("name") + " " + Js.str(c.bright() - br));
        if (useScroll && Inv.count(sp, itemOf(SCROLL)) < 1) out.add(SCROLL.str("name") + " 1");
        return out;
    }

    /** 재료가 모자라면 낮은 소리 + 알림, 창 본문 문구 (충분하면 null — shortage) */
    static String shortage(ServerPlayer sp, Enhance.Cost c, boolean guard) {
        List<String> lack = lacking(sp, c, guard);
        if (lack.isEmpty()) return null;
        SkillFx.playFor(sp, "note.bass", 0.6, 1);
        Notify.notify(sp, "§c재료 부족: " + String.join(", ", lack) + " 더 필요", 40);
        return "§4재료 부족: " + String.join(", ", lack) + " 더 필요§r";
    }

    /** 그 칸의 아이템이 고른 무기 그대로인지 (같은 무기 · 같은 강화 단계) */
    private static boolean same(ItemStack now, Entry e) {
        Weapon w = RpgCombat.weaponOf(now);
        return w != null && w.id().equals(e.w().id()) && WeaponEnhance.of(now) == e.n();
    }

    /**
     * 강화 한 번 (확인 창에서 누른 뒤 — enhanceOnce). 결과 문구 (창 본문용) 를 done 으로.
     * 재료 확인 → 망치질 연출 (ANIM_TICKS 동안 창 없이 — Forms.busy 로 묶음) → 무기 · 재료가 그대로면 그 틱에 재료를 받고 판정
     * (옮겼으면 아무것도 쓰지 않고 취소). 수호 주문서 (useScroll — 실패하면 떨어지는 단계에서만) 는 실패했을 때만 씀.
     * 망치질 도중 나가거나 죽으면 done 을 부르지 않음 (흐름 끝 — 재료는 쓰지 않고, 죽음 화면 위에 창을 열지 않음).
     */
    public static void enhanceOnce(ServerPlayer sp, Entry e, boolean useScroll, Consumer<String> done) {
        if (!same(Inv.get(sp, e.slot()), e)) {
            done.accept("§4무기가 옮겨져서 취소했습니다. 다시 골라 주세요.§r");
            return;
        }
        Enhance.Cost c = Enhance.enhanceCost(e.n(), e.w().grade().id());
        if (c == null) {
            done.accept("§8이미 최대 강화 +" + MAX + "입니다.§r");
            return;
        }
        boolean guard = useScroll && c.down();
        String short0 = shortage(sp, c, guard);
        if (short0 != null) {
            done.accept(short0);
            return;
        }
        hammerFx(sp, e);
        Forms.busy(sp, ANIM_TICKS + 2);
        Tasks.later(ANIM_TICKS, () -> {
            if (sp.isRemoved() || sp.isDeadOrDying()) return;
            done.accept(judge(sp, e, c, guard));
        });
    }

    /** 망치질이 끝난 순간 (enhanceOnce 뒷부분): 무기 · 재료를 다시 보고 재료를 받고 판정. 결과 문구 */
    private static String judge(ServerPlayer sp, Entry e, Enhance.Cost c, boolean guard) {
        ItemStack now = Inv.get(sp, e.slot());
        if (!same(now, e)) return "§4강화 중에 무기를 옮겨서 취소했습니다. 재료는 쓰지 않았습니다.§r";
        String short1 = shortage(sp, c, guard);
        if (short1 != null) return short1;
        if (!Inv.remove(sp, itemOf(STONE), (int) c.stone()) || (c.bright() > 0 && !Inv.remove(sp, itemOf(BRIGHT), (int) c.bright()))) return "§4재료를 쓰지 못했습니다§r";
        boolean ok = rand.getAsDouble() < c.rate();
        int n = e.n();
        if (ok) n = e.n() + 1;
        else if (c.down() && !guard) n = e.n() - 1;
        if (!ok && guard && !Inv.remove(sp, itemOf(SCROLL), 1)) n = e.n() - 1; // (위에서 확인했으므로 없는 일)
        WeaponEnhance.write(now, n);
        Inv.set(sp, e.slot(), now);
        String name = e.w().grade().color() + e.w().title();
        if (ok) {
            successFx(sp, n);
            Notify.title(sp, "§6§l+" + n + " 강화 성공!", "§r" + name + "§r §7피해 +" + pct(Enhance.enhanceBonus(n)));
            if (n >= ANNOUNCE_FROM) announce(sp, e.w(), n);
            return "§2§l성공! §r§0+" + n + " " + e.w().darkName() + "§8 (피해 +" + pct(Enhance.enhanceBonus(n)) + ")" + (guard ? " · 주문서는 쓰지 않았습니다" : "") + "§r";
        }
        failFx(sp, n < e.n(), guard);
        if (n < e.n()) {
            Notify.title(sp, "§c강화 실패", "§c+" + e.n() + " → +" + n);
            return "§4실패: 한 단계 떨어졌습니다 (+" + e.n() + " → +" + n + ")§r";
        }
        if (guard) {
            Notify.title(sp, "§e강화 실패", "§e" + SCROLL.str("name") + "가 지켜 주었습니다 (+" + n + ")");
            return "§6실패: " + SCROLL.str("name") + "가 지켜 주어 +" + n + " 그대로입니다§r";
        }
        Notify.title(sp, "§7강화 실패", "§7+" + n + " 그대로");
        return "§8실패: +" + n + " 그대로입니다§r";
    }

    /**
     * 강화 창 반복 (enhanceLoop). next: "smith" = 레벨 올리기 창으로, "craft" = 보스 무기 제작 창으로, null = 닫음
     */
    public static void enhanceLoop(ServerPlayer sp, Consumer<String> next) {
        step(sp, "", 0, next);
    }

    static void step(ServerPlayer sp, String message, int guard, Consumer<String> next) {
        if (guard >= 200) {
            next.accept(null);
            return;
        }
        List<Entry> list = enhanceable(sp);
        buildForm(sp, list, message).show(sp, sel -> {
            if (sel < 0) next.accept(null);
            else if (sel == TAB_SLOT) next.accept("smith");
            else if (sel == CRAFT_TAB_SLOT) next.accept("craft");
            else if (sel == GEAR_TAB_SLOT && Enhance.gearEnabled()) GearEnhance.step(sp, "", guard + 1, next);
            else {
                int i = sel - FIRST;
                Entry e = i >= 0 && i < list.size() ? list.get(i) : null;
                if (e == null) step(sp, "", guard + 1, next);
                else if (e.n() >= MAX) step(sp, "§8이미 최대 강화 +" + MAX + "입니다.§r", guard + 1, next);
                else confirm(sp, e, pick -> {
                    if (pick.isEmpty()) step(sp, "", guard + 1, next);
                    else enhanceOnce(sp, e, pick.equals("guard"), msg -> step(sp, msg, guard + 1, next));
                });
            }
        });
    }
}
