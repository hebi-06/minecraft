package mmorpg.fabric.smith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import mmorpg.core.Artifacts;
import mmorpg.core.Economy;
import mmorpg.core.Grade;
import mmorpg.core.Js;
import mmorpg.core.Rules;
import mmorpg.core.StatTree;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.artifact.ArtifactSystem;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.gear.GearItems;
import mmorpg.fabric.job.JobUpgrades;
import mmorpg.fabric.shop.Shop;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.ui.ChestForm;
import mmorpg.fabric.ui.Icon;
import mmorpg.fabric.ui.Inv;
import mmorpg.fabric.ui.Slot;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 0.37.0 아티팩트 제작 (사용자가 고른 "대장장이 제작 — 재료 + 골드 · 실버", 규칙 rules_more.json ARTIFACTS.cost): 대장장이 제작 창의 다섯째 쪽
 * (영웅 → 초월 → 고귀 → 공허의 갑옷 → 아티팩트 — {@link Smith#CRAFT_PAGES}) · NPC 대화 "아티팩트 제작".
 * <ul>
 * <li>쪽 (큰 상자 6줄): 윗줄 = 강화로 · 보유 실버 · 다음 쪽 · 보유 골드 · 레벨 올리기로, 가운데 네 줄 = 종류 (반지 · 목걸이 · 귀걸이 · 팔찌 — 줄 첫 칸은
 * 종류 풀이: 등급마다 옵션 값) × 등급 다섯 (일반 ~ 초월 — 칸 설명에 옵션 · 착용 레벨 · 재료 가진 수/필요), 아랫줄 = 등급마다 재료 요약 · 아티팩트 창 열기.</li>
 * <li>아티팩트 칸을 누르면 확인 창 (11 만들기 · 13 아티팩트 · 15 취소) → 실버 · 골드 · 재료를 내고 아티팩트를 가방에.</li>
 * </ul>
 * 비용은 등급마다 같음 (반지 · 목걸이 · 귀걸이 · 팔찌). 다 있는지 확인한 뒤 내고, 가방이 차면 아무것도 쓰지 않음.
 */
public final class ArtifactCraft {
    private ArtifactCraft() {}

    private static final Rules R = Rules.get();
    /** 6줄 쪽 */
    static final int ROWS = 6;
    /** 윗줄: 보유 실버 · 보유 골드 */
    static final int SILVER_SLOT = 2;
    static final int GOLD_SLOT = 6;
    /** 종류 줄 (1 ~ 4) 의 첫 칸 = 종류 풀이, 셋째 칸부터 등급 다섯 */
    static final int TYPE_COL = 0;
    static final int GRADE_COL = 2;
    /** 아랫줄: 아티팩트 창 열기 · 등급마다 재료 요약 (GRADE_COL ~) */
    public static final int OPEN_SLOT = 45;
    static final int MAT_ROW = 5;

    /** 아티팩트를 만들 수 있는지 (규칙에 ARTIFACTS 가 있음) */
    public static boolean enabled() {
        return Artifacts.enabled();
    }

    /** 종류 · 등급의 쪽 칸 번호 (시험용) */
    public static int slotOf(String type, String grade) {
        int row = 1 + Artifacts.types().indexOf(type);
        Grade g = R.grade(grade);
        return row * 9 + GRADE_COL + (g == null ? 0 : g.index());
    }

    /** 쪽 칸 → 아티팩트 (아티팩트 칸이 아니면 null) */
    static Artifacts.Art artAt(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        List<String> types = Artifacts.types();
        if (row < 1 || row > types.size() || col < GRADE_COL || col >= GRADE_COL + R.grades().size()) return null;
        return Artifacts.art(types.get(row - 1), R.grades().get(col - GRADE_COL).id());
    }

    /** 아티팩트의 아이템 (가방에 줄 것 — 하나) */
    public static ItemStack item(Artifacts.Art a) {
        Item it = a == null ? null : GearItems.byId(a.id());
        return it == null ? ItemStack.EMPTY : new ItemStack(it);
    }

    // ------------------------------------------------------------ 비용

    /** 모자란 것 ["실버 12", "게 집게발 3"] (없으면 빈 목록) */
    public static List<String> lack(ServerPlayer sp, Artifacts.Cost c) {
        List<String> out = new ArrayList<>();
        int silver = Inv.money(sp);
        if (silver < c.silver()) out.add(Inv.CURRENCY_NAME + " " + (c.silver() - silver));
        long gold = Wallet.total(sp, Economy.Kind.GOLD);
        if (gold < c.gold()) out.add("골드 " + (c.gold() - gold));
        for (Map.Entry<String, Integer> x : c.items().entrySet()) {
            JobUpgrades.Mat m = JobUpgrades.mat(x.getKey());
            int have = m.item() == null ? 0 : Inv.count(sp, m.item());
            if (have < x.getValue()) out.add(m.name() + " " + (x.getValue() - have));
        }
        return out;
    }

    /** 비용 줄 ["실버 20/20", "멧돼지 엄니 3/6", …] — 채웠으면 초록 (dark: 창 본문용 어두운 글자) */
    static List<String> costParts(ServerPlayer sp, Artifacts.Cost c, boolean dark) {
        List<String> out = new ArrayList<>();
        if (c.silver() > 0) {
            int have = Inv.money(sp);
            String ok = dark ? (have >= c.silver() ? "§2" : "§4") : (have >= c.silver() ? "§a" : "§7");
            out.add(ok + Inv.CURRENCY_NAME + " " + (dark ? Economy.fmt(have) : "§f" + Math.min(have, c.silver())) + "/" + c.silver());
        }
        if (c.gold() > 0) {
            long gold = Wallet.total(sp, Economy.Kind.GOLD);
            String ok = dark ? (gold >= c.gold() ? "§2" : "§4") : (gold >= c.gold() ? "§a" : "§7");
            out.add(ok + "골드 " + (dark ? Economy.fmt(gold) : "§f" + Math.min(gold, c.gold())) + "/" + c.gold());
        }
        for (Map.Entry<String, Integer> x : c.items().entrySet()) out.add(Smith.itemLine(sp, x.getKey(), x.getValue(), dark));
        return out;
    }

    /** 쪽 처음 여는 점수: 등급마다 (가진 재료를 필요한 만큼까지 셈) 의 가장 큰 값 (Smith.firstCraftPage) */
    static int score(ServerPlayer sp) {
        if (!enabled()) return 0;
        int best = 0;
        for (Grade g : R.grades()) {
            Artifacts.Cost c = Artifacts.cost(g.id());
            int s = 0;
            for (Map.Entry<String, Integer> x : c.items().entrySet()) {
                JobUpgrades.Mat m = JobUpgrades.mat(x.getKey());
                s += Math.min(m.item() == null ? 0 : Inv.count(sp, m.item()), x.getValue());
            }
            best = Math.max(best, s);
        }
        return best;
    }

    // ------------------------------------------------------------ 쪽

    /** 종류 풀이 줄: "공격력 1 · 1.5 · 2 · 3 · 4%" (등급 순서) */
    static List<String> typeTable(Artifacts.Type t) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, List<Double>> e : t.bonus().entrySet()) {
            StatTree.Kind k = StatTree.kind(e.getKey());
            List<String> vals = new ArrayList<>();
            for (double v : e.getValue()) vals.add(Js.str(v));
            out.add("§r§7" + (k == null ? e.getKey() : k.name()) + " §f" + String.join(" §8·§f ", vals) + (k == null ? "" : k.unit()));
        }
        return out;
    }

    /** 아티팩트 쪽 (Smith.craftStep 이 보여 줌 — 윗줄은 다른 쪽과 같음: 0 강화로 · 4 다음 쪽 · 8 레벨 올리기로) */
    static ChestForm pageForm(ServerPlayer sp, String message, String title, String nextLabel) {
        ChestForm form = new ChestForm(title, ROWS);
        form.set(Smith.TAB_SLOT, Slot.of("textures/items/nether_star", "§6무기 강화로").lore("§7+1 ~ +10 무기 강화 창"));
        form.set(SILVER_SLOT, Shop.balanceSlot(sp));
        Artifacts.Art sample = Artifacts.art("ring", "hero");
        form.set(Smith.CRAFT_INFO_SLOT, Slot.of(Icon.item(item(sample)), "§d장신구 제작")
                .lore("§e누르면 " + nextLabel + " 보기", "§7반지 · 목걸이 · 귀걸이 · 팔찌를", "§7등급마다 (일반 ~ 초월) 만듭니다", "§7같은 등급이면 재료가 같음",
                        "§7끼기: 장신구 창 (I 키)"));
        form.set(GOLD_SLOT, JobUpgrades.goldSlot(sp));
        form.set(Smith.CRAFT_TAB_SLOT, Slot.of("textures/items/iron_ingot", "§6레벨 올리기로").lore("§7영웅 · 초월 무기 레벨 제한 올리기 창"));
        double lv = RpgCombat.reqLevelOf(sp);
        List<String> types = Artifacts.types();
        for (int r = 0; r < types.size(); r++) {
            Artifacts.Type t = Artifacts.type(types.get(r));
            int row = (r + 1) * 9;
            List<String> head = new ArrayList<>();
            head.add("§r§8" + t.desc());
            head.add("§r§7옵션 (일반 → 초월):");
            head.addAll(typeTable(t));
            int slots = Artifacts.slotsFor(t.key()).size();
            head.add("§r§7장신구 창 칸 §f" + slots + "개");
            // 줄 첫 칸 = 종류 풀이 (책 — 바로 옆 일반 칸과 같은 그림이면 헷갈림)
            form.set(row + TYPE_COL, Slot.of(Icon.item(net.minecraft.world.item.Items.BOOK), "§d§l" + t.name() + " §7풀이").lore(head).deco());
            for (Grade g : R.grades()) {
                Artifacts.Art a = Artifacts.art(t.key(), g.id());
                Artifacts.Cost c = Artifacts.cost(g.id());
                List<String> lore = new ArrayList<>(Artifacts.lore(a, lv));
                for (String s : costParts(sp, c, false)) lore.add("§r" + s);
                lore.add(lack(sp, c).isEmpty() ? "§r§a만들 수 있음 §7(누르면 확인 창)" : "§r§c재료 부족 §7(누르면 확인 창)");
                // 착용 레벨이 모자라도 만들 수 있음 (설명의 착용 줄이 빨강 — 공허의 갑옷처럼)
                form.set(row + GRADE_COL + g.index(), Slot.of(Icon.item(item(a)), a.coloredName()).lore(lore));
            }
        }
        // 아랫줄: 아티팩트 창 · 등급마다 재료 요약 (그림 = 첫 재료, 숫자 = 가진 수)
        form.set(OPEN_SLOT, Slot.of(Icon.item(item(Artifacts.art("necklace", "transcend"))), "§d장신구 창 열기")
                .lore("§7낀 칸 " + ArtifactSystem.worn(sp) + "/" + ArtifactSystem.SIZE, "§7합계: §a" + orNone(StatTree.bonusText(ArtifactSystem.totals(sp))), "§8I 키로도 열림"));
        for (Grade g : R.grades()) {
            Artifacts.Cost c = Artifacts.cost(g.id());
            String first = c.items().keySet().iterator().next();
            JobUpgrades.Mat m = JobUpgrades.mat(first);
            int have = m.item() == null ? 0 : Inv.count(sp, m.item());
            List<String> lore = new ArrayList<>();
            lore.add("§r§7착용 " + Artifacts.level(g.id()) + "렙 · 모든 종류 같음");
            for (String s : costParts(sp, c, false)) lore.add("§r" + s);
            for (String id : c.items().keySet()) lore.add("§r§8" + JobUpgrades.mat(id).name() + ": " + JobUpgrades.mat(id).from());
            form.set(MAT_ROW * 9 + GRADE_COL + g.index(), Slot.of(m.item() == null ? Icon.sprite("pane") : Icon.item(m.item()),
                    g.color() + g.name() + " 재료").count(have).lore(lore).deco(false));
        }
        form.fillEmpty();
        List<String> lines = new ArrayList<>();
        lines.add("§8장신구 칸을 누르면 확인 창이 열립니다. 윗줄 가운데 칸: " + nextLabel + " 쪽.§r");
        lines.add("§8재료: 몬스터 전리품 + 실버 (일반 ~ 고귀) · 골드 + 보스 재료 (영웅 · 초월).§r");
        lines.add("§8아랫줄: 등급마다 재료 (가진 수) · 왼쪽 칸 = 장신구 창.§r");
        if (message != null && !message.isEmpty()) lines.add(message);
        form.body(String.join("\n", lines));
        return form;
    }

    private static String orNone(String s) {
        return s.isEmpty() ? "§8없음" : s;
    }

    // ------------------------------------------------------------ 확인 창 · 만들기

    /** 확인 창 (confirm): 11 만들기 · 13 아티팩트 · 15 취소. 결과 문구를 done 으로 (취소면 "") */
    static void confirm(ServerPlayer sp, Artifacts.Art a, Consumer<String> done) {
        Artifacts.Cost c = Artifacts.cost(a.grade());
        ChestForm form = new ChestForm("장신구 만들기");
        form.set(11, Slot.of(Icon.sprite("icon_check"), "§a만들기"));
        form.set(13, Slot.of(Icon.item(item(a)), a.coloredName()).lore(Artifacts.lore(a, RpgCombat.reqLevelOf(sp))));
        form.set(15, Slot.of(Icon.sprite("icon_cross"), "§c취소"));
        form.fillEmpty();
        double lv = RpgCombat.reqLevelOf(sp);
        List<String> lines = new ArrayList<>();
        lines.add(a.darkName() + " §8· " + a.typeName() + "§r");
        lines.add("§8" + Artifacts.optionText(a) + "§r");
        lines.add((Artifacts.usable(a, lv) ? "§8착용 " : "§4착용 ") + a.level() + "렙" + (Artifacts.usable(a, lv) ? "" : " §8(지금 " + Js.str(Math.floor(lv)) + ")") + "§r");
        // 비용은 "이름 가진 수/필요" 로 한 줄에 둘씩 (휴대폰 창 한 줄 180px)
        List<String> parts = costParts(sp, c, true);
        for (int i = 0; i < parts.size(); i += 2) lines.add("§8" + String.join(" §8· ", parts.subList(i, Math.min(parts.size(), i + 2))) + "§r");
        lines.add("§8왼쪽 초록 칸: 만들기 · 오른쪽 빨간 칸: 취소§r");
        form.body(String.join("\n", lines));
        form.show(sp, sel -> done.accept(sel == 11 ? craft(sp, a) : ""));
    }

    /**
     * 아티팩트 만들기 (craft). 결과 문구 (창 본문용). 비용이 모두 있는지 · 낸 뒤 가방에 들어가는지 확인한 뒤, 재료 → 실버 → 골드 순으로 내고 줌.
     * 모자라거나 가방이 차면 아무것도 쓰지 않음
     */
    public static String craft(ServerPlayer sp, Artifacts.Art a) {
        return craft(sp, a, false);
    }

    /** 같은 것 — quiet 면 연출 · 알림 없이 (0.44.0 대장장이 전용 창) */
    public static String craft(ServerPlayer sp, Artifacts.Art a, boolean quiet) {
        if (!enabled() || a == null) return "§4장신구를 만들 수 없습니다.§r";
        Artifacts.Cost c = Artifacts.cost(a.grade());
        List<String> lack = lack(sp, c);
        if (!lack.isEmpty()) return Smith.lackMessage(sp, lack);
        ItemStack it = item(a);
        if (it.isEmpty()) return "§4장신구를 만들 수 없습니다.§r";
        List<Inv.Take> takes = new ArrayList<>();
        for (Map.Entry<String, Integer> x : c.items().entrySet()) {
            JobUpgrades.Mat m = JobUpgrades.mat(x.getKey());
            if (m.item() != null) takes.add(new Inv.Take(m.item(), x.getValue()));
        }
        takes.addAll(Inv.payTakes(sp, c.silver()));
        takes.addAll(Wallet.takes(sp, Economy.Kind.GOLD, c.gold()));
        if (!Inv.fitsAfter(sp, takes, List.of(it))) return Inv.bagFull(sp);
        for (Map.Entry<String, Integer> x : c.items().entrySet()) {
            JobUpgrades.Mat m = JobUpgrades.mat(x.getKey());
            if (m.item() == null || !Inv.remove(sp, m.item(), x.getValue())) return "§4결제 오류§r";
        }
        if (c.silver() > 0 && !Inv.pay(sp, c.silver())) return "§4결제 오류§r";
        if (c.gold() > 0 && !Wallet.pay(sp, Economy.Kind.GOLD, c.gold())) return "§4결제 오류§r";
        Inv.give(sp, it);
        if (!quiet) {
            craftFx(sp, a);
            double lv = RpgCombat.reqLevelOf(sp);
            Notify.notify(sp, "§d장신구 제작: " + a.coloredName() + " §a" + Artifacts.optionText(a)
                    + (Artifacts.usable(a, lv) ? " §7(I 키 · 들고 우클릭으로 끼기)" : " §c(" + a.level() + "렙부터)"), 70);
        }
        return "§2완료: " + a.darkName() + " §8(" + Artifacts.optionText(a) + ")§r";
    }

    /** 만들기 연출: 모루 · 자수정 소리, 머리 위 등급 색 불꽃 · 마법 가루 (초월은 불꽃 · 용암 방울) */
    private static void craftFx(ServerPlayer sp, Artifacts.Art a) {
        Smith.forgeFx(sp, 1.1, 0.8, 1.3, 0.5, 3);
        ServerLevel level = sp.level();
        Vec3 at = sp.position().add(0, 1.2, 0);
        double[] rgb = ArtifactSystem.gradeRgb(a.gradeIndex());
        SkillFx.play(level, "block.amethyst_block.resonate", at, 1.0 + a.gradeIndex() * 0.1, 0.9);
        SkillFx.sparks(level, V.up(at, 0.4), rgb, 10 + a.gradeIndex() * 4, 2.5);
        SkillFx.particle(level, ParticleTypes.ENCHANT, at, 24, 0.5, 0.6);
        if (a.gradeIndex() >= 4) {
            SkillFx.particle(level, ParticleTypes.FLAME, at, 16, 0.4, 0.05);
            SkillFx.particle(level, ParticleTypes.LAVA, at, 4, 0.3, 0);
        }
    }
}
