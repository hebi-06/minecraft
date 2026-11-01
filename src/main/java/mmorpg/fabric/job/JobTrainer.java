package mmorpg.fabric.job;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import mmorpg.core.Jobs;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.Notify;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.rune.RuneKit;
import mmorpg.fabric.rune.Ult;
import mmorpg.fabric.shop.Shop;
import mmorpg.fabric.skill.Opt;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.ui.ChestForm;
import mmorpg.fabric.ui.DialogForm;
import mmorpg.fabric.ui.Icon;
import mmorpg.fabric.ui.Inv;
import mmorpg.fabric.ui.Slot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * 0.27.0 직업 교관 (NPC 역할 "job" — /npc tag job · NPC 설정 창): 전직 · 직업 바꾸기 창. 10렙 (Jobs.changeLevel) 부터, 첫 전직은 무료 · 바꾸기는 은화
 * 300 (Jobs.changeSilver). 상자 창에 직업 카드 열하나 (jobs.json order — 그림은 그 직업의 40렙 초월 무기, 마검사는 마검) · 지금 직업 · 보유 은화 · 안내,
 * 카드를 누르면 확인 창 (특성 · 스킬 다섯 설명) → 전직 연출 (직업 색 둥근 베기 · 빛기둥 · 솟는 빛 · 번쩍임 · 제목).
 * 스킬이 아직 들어가지 않은 직업 (ready false) 은 흐린 카드 ("준비 중").
 * 0.51.0 2차 전직 (jobs.json advance — claude/job-advance.md): 1차 직업이 있으면 위 줄 2차 전직 단추 → 2차 전직 창 (각성 · 1차 직업 · 반전 카드 —
 * 50렙 (Jobs.advanceLevel) 부터, 처음은 무료 · 각성 ↔ 반전 바꾸기 은화 600 (Jobs.switchSilver)). 2차 직업은 이명(직업명) 으로 보임 (Job.display),
 * 1차 직업을 바꾸면 2차도 풀림 (직업 id 하나만 저장 — 확인 창이 알림), 직업 스킬 강화 단계는 같은 칸으로 이어짐 (Jobs.upId).
 */
public final class JobTrainer {
    private JobTrainer() {}

    /** 창의 칸: 지금 직업 · 보유 은화 · 안내 */
    static final int MINE_SLOT = 0;
    static final int BALANCE_SLOT = 4;
    static final int GUIDE_SLOT = 22;
    /** 0.31.0 직업 스킬 강화 창으로 (직업이 있을 때 — 오른쪽 위) */
    static final int UP_SLOT = 8;
    /** 직업 카드 칸 (jobs.json order 순서): 가운데 줄 일곱 + 아랫줄 넷 (아랫줄 가운데는 안내) */
    static final int[] CARD_SLOTS = {10, 11, 12, 13, 14, 15, 16, 20, 21, 23, 24};
    /** 확인 창: 전직 · 카드 · 취소 */
    static final int OK_SLOT = 11;
    static final int CARD_SLOT = 13;
    static final int NO_SLOT = 15;
    /** 칸 키 이름 (Shift + 룬 칸 1 ~ 5 의 기본 키) */
    static final String KEYS = "RZVBY";
    /** 0.51.0 2차 전직 창으로 (직업이 있을 때 — 위 줄) */
    public static final int ADV_SLOT = 6;
    /** 0.51.0 2차 전직 창: 각성 카드 · 1차 직업 (가운데 — 그림만) · 반전 카드 · 돌아가기 (왼쪽 아래) */
    public static final int AWAKEN_SLOT = 11;
    public static final int ADV_BASE_SLOT = 13;
    public static final int INVERT_SLOT = 15;
    public static final int BACK_SLOT = 18;

    /** 직업 이름 글자색 (창 · 알림 — § 색 하나: 직업 색에 가까운 것) */
    private static final Map<String, String> CODE = Map.ofEntries(Map.entry("swordsman", "§b"), Map.entry("warrior", "§c"), Map.entry("spellblade", "§d"),
            Map.entry("mage", "§9"), Map.entry("archer", "§a"), Map.entry("assassin", "§5"), Map.entry("lancer", "§6"), Map.entry("reaper", "§3"),
            Map.entry("paladin", "§e"), Map.entry("priest", "§f"), Map.entry("guardian", "§7"),
            // 0.51.0 2차 직업 (없는 것은 1차 직업 색)
            Map.entry("swordmaster", "§6"), Map.entry("bloodknight", "§4"), Map.entry("warlord", "§6"), Map.entry("lycan", "§7"));
    /** 카드 그림 (직업 id → 무기 아이템) */
    private static final Map<String, ItemStack> CARD_ITEMS = new ConcurrentHashMap<>();

    /** 직업 이름 글자색 */
    public static String code(Jobs.Job j) {
        if (j == null) return "§f";
        String c = CODE.get(j.id());
        return c != null ? c : j.advanced() ? CODE.getOrDefault(j.base(), "§f") : "§f";
    }

    /** NPC 대화의 선택지 글자 */
    public static String optionText(ServerPlayer sp) {
        Jobs.Job j = JobSystem.job(sp);
        return j == null ? "직업 고르기 (전직)" : "직업 바꾸기 · 2차 전직 §8(지금 " + j.display() + ")";
    }

    /** 0.31.0 NPC 대화의 '직업 스킬 강화' 선택지 글자 (직업이 없으면 null — 선택지 없음) */
    public static String upOptionText(ServerPlayer sp) {
        Jobs.Job j = JobSystem.job(sp);
        if (j == null || !j.ready()) return null;
        return "직업 스킬 강화 §8(" + JobUpgrades.total(sp, j) + "/" + Jobs.upMax() * Jobs.SLOTS + ")";
    }

    /** 직업 카드 칸 번호 (jobs.json order 의 i 번째 — 없으면 -1) */
    public static int slotOf(String jobId) {
        List<String> order = Jobs.order();
        int i = order.indexOf(jobId);
        return i >= 0 && i < CARD_SLOTS.length ? CARD_SLOTS[i] : -1;
    }

    /** 직업 바꾸기 값 (직업이 없으면 첫 전직 — 무료) */
    public static int costFor(ServerPlayer sp) {
        return JobSystem.job(sp) == null ? 0 : (int) Math.round(Jobs.changeSilver());
    }

    /** 0.51.0 2차 전직 값: 1차 직업에서 처음 = 무료, 2차 직업에서 다른 길 (각성 ↔ 반전) = Jobs.switchSilver */
    public static int advCostFor(ServerPlayer sp) {
        Jobs.Job now = JobSystem.job(sp);
        return now != null && now.advanced() ? (int) Math.round(Jobs.switchSilver()) : 0;
    }

    /** 전직 창 (10렙 전이면 대사만). 창을 닫거나 전직하면 done (null 이면 없음) */
    public static void open(ServerPlayer sp, Runnable done) {
        if (RpgCombat.reqLevelOf(sp) < Jobs.changeLevel()) {
            new DialogForm("직업 교관", "아직 이르네. §f" + Jobs.changeLevel() + "렙§r이 되면 다시 오게.\n그때까지 여러 무기를 손에 익혀 두게 — 직업은 손에 맞는 무기를 따라가는 법이지.")
                    .choice("알겠습니다", DialogForm.OK).show(sp, sel -> finish(done));
            return;
        }
        list(sp, "", done);
    }

    private static void finish(Runnable done) {
        if (done != null) done.run();
    }

    // ------------------------------------------------------------ 직업 카드 창

    private static void list(ServerPlayer sp, String message, Runnable done) {
        ChestForm form = build(sp, message);
        form.show(sp, sel -> {
            if (sel < 0) {
                finish(done);
                return;
            }
            if (sel == UP_SLOT && JobSystem.job(sp) != null) {
                JobUpgrades.open(sp, done); // 0.31.0 직업 스킬 강화
                return;
            }
            if (sel == ADV_SLOT && JobSystem.job(sp) != null) {
                Jobs.Job base = Jobs.baseOf(JobSystem.job(sp));
                if (base == null || Jobs.advancedOf(base.id()).isEmpty()) list(sp, "§8" + (base == null ? "" : base.name() + " 의 ") + "2차 전직은 아직 준비 중입니다.§r", done);
                else advanceList(sp, "", done); // 0.51.0 2차 전직
                return;
            }
            Jobs.Job j = jobAt(sel);
            if (j == null) {
                list(sp, message, done);
                return;
            }
            if (!j.ready()) {
                list(sp, "§8" + j.name() + " 은(는) 아직 준비 중입니다 — 다음 판에 들어갑니다.§r", done);
                return;
            }
            Jobs.Job now = JobSystem.job(sp);
            if (now != null && now.id().equals(j.id())) {
                list(sp, "§8이미 " + j.name() + " 입니다.§r", done);
                return;
            }
            if (now != null && now.advanced() && j.id().equals(now.base())) {
                list(sp, "§8이미 " + j.name() + " 의 2차 직업 (" + now.display() + ") 입니다 — 위 줄 2차 전직 칸에서 길을 바꿀 수 있습니다.§r", done);
                return;
            }
            confirm(sp, j, done);
        });
    }

    /** 칸 번호의 직업 (카드가 아니면 null) */
    static Jobs.Job jobAt(int slot) {
        List<String> order = Jobs.order();
        for (int i = 0; i < Math.min(order.size(), CARD_SLOTS.length); i++) if (CARD_SLOTS[i] == slot) return Jobs.job(order.get(i));
        return null;
    }

    /** 직업 카드 창 (시험이 칸을 봄) */
    static ChestForm build(ServerPlayer sp, String message) {
        Jobs.Job now = JobSystem.job(sp);
        int cost = costFor(sp);
        ChestForm form = new ChestForm("직업 교관 §8· §7" + (now == null ? "전직" : "직업 바꾸기"));
        form.set(BALANCE_SLOT, Shop.balanceSlot(sp));
        if (now != null) form.set(MINE_SLOT, mineSlot(now));
        List<String> order = Jobs.order();
        for (int i = 0; i < Math.min(order.size(), CARD_SLOTS.length); i++) {
            Jobs.Job j = Jobs.job(order.get(i));
            if (j == null) continue;
            boolean mine = now != null && (now.id().equals(j.id()) || now.advanced() && j.id().equals(now.base()));
            Slot s = Slot.of(cardItem(j), code(j) + "§l" + j.name() + (mine ? " §r§a✔" : "")).lore(cardLore(j, mine, cost, now));
            if (!j.ready()) s.dim();
            form.set(CARD_SLOTS[i], s);
        }
        form.set(GUIDE_SLOT, Slot.of(Icon.of("textures/items/book_enchanted"), "§e직업 안내").lore(guideLore(cost)).deco());
        if (now != null && now.ready()) form.set(UP_SLOT, upButton(sp, now)); // 0.31.0
        if (now != null) form.set(ADV_SLOT, advButton(sp, now)); // 0.51.0
        form.fillEmpty();
        List<String> lines = new ArrayList<>();
        lines.add(now == null ? "§8직업을 고르면 Shift + 룬 칸 키 (⇧R · ⇧Z · ⇧V · ⇧B · ⇧Y) 로 직업 스킬을 씁니다. 첫 전직은 무료입니다.§r"
                : "§8지금 직업: §0" + now.display() + "§8. 다른 직업으로 바꾸면 §2" + cost + "§8 " + Inv.CURRENCY_NAME + " (자원 · 태세 · 재사용 대기는 처음부터"
                        + (now.advanced() ? " · 2차 직업도 풀림" : "") + ").§r");
        lines.add("§8카드를 누르면 특성 · 스킬 설명과 확인 창이 열립니다. 흐린 카드는 준비 중인 직업입니다.§r");
        if (now != null && now.ready()) lines.add("§8오른쪽 위 칸: §6직업 스킬 강화§8 (스킬마다 +" + Jobs.upMax() + " — " + Jobs.upLevel(1) + "렙부터).§r");
        if (now != null) lines.add("§8위 줄 별 칸: §52차 전직§8 (" + Jobs.advanceLevel() + "렙부터 각성 · 반전 중 하나 — 처음은 무료).§r");
        if (message != null && !message.isEmpty()) lines.add(message);
        form.body(String.join("\n", lines));
        return form;
    }

    /** 지금 직업 칸 (2차 직업은 이명(직업명) · 길) */
    static Slot mineSlot(Jobs.Job now) {
        String path = now.advanced() ? "§8 · §7" + Jobs.pathName(now.path()) : "";
        return Slot.of(cardItem(now), "§7지금 직업 §8· " + code(now) + "§l" + now.display() + path).lore("§7" + now.role(), "§8무기 §f" + now.weaponText() + " §8· 스탯 §f"
                + now.statText()).deco();
    }

    /** 카드 설명 줄: 역할 · 무기 · 스탯 · 한 줄 소개 · 특성 · 스킬 다섯 (칸 키 · 이름 · 칸 역할 · 해금 레벨 · 재사용 대기) · 누르면 */
    static List<String> cardLore(Jobs.Job j, boolean mine, int cost) {
        return cardLore(j, mine, cost, null);
    }

    /** 0.51.0 now = 지금 직업 (2차 직업이면 다른 1차 직업 카드에 '2차 직업도 풀림') */
    static List<String> cardLore(Jobs.Job j, boolean mine, int cost, Jobs.Job now) {
        List<String> out = new ArrayList<>();
        out.add("§7" + j.role());
        out.add("§8무기 §f" + j.weaponText() + " §8· 적용 스탯 §f" + j.statText());
        for (String l : wrap(j.line(), 26)) out.add("§7" + l);
        out.add("");
        if (j.trait() != null) {
            out.add("§e특성 「" + j.trait().name() + "」");
            for (String l : wrap(j.trait().desc(), 28)) out.add("§7" + l);
            out.add("");
        }
        out.add("§e직업 스킬");
        for (Jobs.Skill k : j.skills()) {
            out.add("§8⇧" + KEYS.charAt(Math.max(0, Math.min(KEYS.length() - 1, k.slot()))) + " §f" + k.name() + " §8— " + Jobs.slotRole(k.slot()) + " · "
                    + Jobs.unlockLevel(k.slot()) + "렙 · " + fmt(k.cd()) + "초");
        }
        out.add("");
        if (!j.ready()) out.add("§8준비 중 — 다음 판에 들어갑니다");
        else if (mine) out.add(now != null && now.advanced() ? "§a✔ 지금 직업 §7(2차 " + now.display() + ")" : "§a✔ 지금 직업");
        else {
            out.add(cost <= 0 ? "§a▶ 누르면 전직 §7(첫 전직 무료)" : "§e▶ 누르면 직업 바꾸기 §7(" + cost + " " + Inv.CURRENCY_NAME + ")");
            if (now != null && now.advanced()) out.add("§c2차 직업 " + now.display() + " 은(는) 풀립니다");
        }
        return out;
    }

    /** 0.31.0 직업 스킬 강화 단추 (그림 = 그 직업 궁극기 아이콘 · 단계 합) */
    static Slot upButton(ServerPlayer sp, Jobs.Job j) {
        Jobs.Skill ult = j.skill(Jobs.SLOTS - 1);
        int sum = JobUpgrades.total(sp, j);
        Slot s = Slot.of(ult == null ? Icon.of("textures/items/book_enchanted") : JobUpgrades.skillIcon(ult), "§6§l직업 스킬 강화 §r§e" + sum + "§7/" + Jobs.upMax() * Jobs.SLOTS)
                .lore("§7골드와 전리품 · 보스 재료로", "§7스킬마다 +" + Jobs.upMax() + " 까지 (단계마다 계수 +" + Math.round(Jobs.upPer() * 100) + "%)",
                        "§8+1 은 " + Jobs.upLevel(1) + "렙부터", "", "§e▶ 누르면 강화 창");
        if (sum > 0) s.count(sum);
        return s;
    }

    private static List<String> guideLore(int cost) {
        List<String> out = new ArrayList<>();
        out.add("§7" + Jobs.changeLevel() + "렙부터 직업을 고를 수 있습니다.");
        out.add("§7직업 스킬 칸 다섯: Shift + 룬 칸 키");
        StringBuilder b = new StringBuilder("§8");
        for (int i = 0; i < Jobs.SLOTS; i++) {
            if (i > 0) b.append(" · ");
            b.append('⇧').append(KEYS.charAt(i)).append(' ').append(Jobs.unlockLevel(i)).append("렙");
        }
        out.add(b.toString());
        out.add("§7칸은 직업 무기를 들고 요구 스탯을 맞춰야 씁니다.");
        out.add("§7위력 = 레벨 기본값 × (1 + 적용 스탯 × " + fmt(Jobs.perPoint() * 100) + "%)");
        out.add(cost <= 0 ? "§a첫 전직은 무료" : "§e직업 바꾸기: " + cost + " " + Inv.CURRENCY_NAME);
        out.add("§8/job — 내 직업 · 칸 보기");
        return out;
    }

    /** 카드 그림: 그 직업 무기 (마지막 무기 — 마검사는 마검) 의 40렙 초월 아이템 (없으면 더 낮은 등급 · 레벨) */
    static ItemStack cardItem(Jobs.Job j) {
        return CARD_ITEMS.computeIfAbsent(j.id(), id -> findCardItem(j)).copy();
    }

    private static ItemStack findCardItem(Jobs.Job j) {
        List<String> grades = List.of("transcend", "hero", "noble", "rare", "common");
        double[] levels = {40, 35, 30, 20, 10, 0};
        for (int wi = j.weapons().size() - 1; wi >= 0; wi--) {
            String type = j.weapons().get(wi);
            for (String g : grades) {
                for (double lv : levels) {
                    if (!Weapons.isValidWeapon(lv, g, type)) continue;
                    Weapon w = Weapons.weaponFrom(type, lv, g);
                    ItemStack s = w == null ? ItemStack.EMPTY : Shop.weaponItem(w);
                    if (!s.isEmpty()) return s;
                }
            }
        }
        return new ItemStack(Items.IRON_SWORD);
    }

    // ------------------------------------------------------------ 확인 · 값 · 전직

    private static void confirm(ServerPlayer sp, Jobs.Job j, Runnable done) {
        int cost = costFor(sp);
        int bal = Inv.money(sp);
        ChestForm form = new ChestForm(cost <= 0 ? "전직 확인" : "직업 바꾸기 확인");
        form.set(OK_SLOT, Slot.of(Icon.sprite("icon_check"), cost <= 0 ? "§a전직하기" : "§a직업 바꾸기 §7(" + cost + " " + Inv.CURRENCY_NAME + ")"));
        Jobs.Job now = JobSystem.job(sp);
        Slot card = Slot.of(cardItem(j), code(j) + "§l" + j.name()).lore(cardLore(j, false, cost, now));
        if (cost > 0) card.price(cost);
        form.set(CARD_SLOT, card);
        form.set(NO_SLOT, Slot.of(Icon.sprite("icon_cross"), "§c취소"));
        form.fillEmpty();
        List<String> lines = confirmLines(j, cost, bal);
        if (now != null && now.advanced()) lines.add(lines.size() - 1, "§42차 직업 " + now.display() + " 은(는) 풀립니다§8 — " + j.name() + " 으로 " + Jobs.advanceLevel()
                + "렙 2차 전직을 다시 고를 수 있습니다 (처음은 무료).§r");
        form.body(String.join("\n", lines));
        form.show(sp, sel -> {
            if (sel == OK_SLOT) {
                String msg = change(sp, j);
                if (msg.isEmpty()) finish(done);
                else list(sp, msg, done);
            } else list(sp, "", done);
        });
    }

    /** 확인 창 본문: 직업 · 무기 · 스탯 · 특성 · 스킬 다섯 (설명) · 값 */
    static List<String> confirmLines(Jobs.Job j, int cost, int bal) {
        List<String> lines = new ArrayList<>();
        lines.add("§0§l" + j.name() + "§r§8 — " + j.role());
        lines.add("§8무기 §0" + j.weaponText() + "§8 · 적용 스탯 §0" + j.statText() + "§8 · " + j.line() + "§r");
        if (j.trait() != null) lines.add("§8특성 「§0" + j.trait().name() + "§8」 " + j.trait().desc() + "§r");
        for (Jobs.Skill k : j.skills()) {
            lines.add("§8⇧" + KEYS.charAt(Math.max(0, Math.min(KEYS.length() - 1, k.slot()))) + " §0" + k.name() + "§8 (" + Jobs.unlockLevel(k.slot()) + "렙 · "
                    + fmt(k.cd()) + "초) " + k.desc() + "§r");
        }
        lines.add(cost <= 0 ? "§8비용: §2§l무료§r§8 (첫 전직)§r" : "§8비용: §2§l" + cost + "§r§8 " + Inv.CURRENCY_NAME + " · 보유 §0" + bal + "§r");
        lines.add("§8왼쪽 초록 칸: " + (cost <= 0 ? "전직" : "직업 바꾸기") + " · 오른쪽 빨간 칸: 취소§r");
        return lines;
    }

    /** 전직 · 직업 바꾸기 처리 (값 · 직업 · 연출). 실패 사유 ("" = 됨) */
    public static String change(ServerPlayer sp, Jobs.Job j) {
        if (j == null) return "§4없는 직업입니다.§r";
        if (!j.ready()) return "§8" + j.name() + " 은(는) 아직 준비 중입니다.§r";
        if (RpgCombat.reqLevelOf(sp) < Jobs.changeLevel()) return "§4" + Jobs.changeLevel() + "렙부터 직업을 고를 수 있습니다.§r";
        Jobs.Job now = JobSystem.job(sp);
        if (j.advanced()) return advance(sp, j); // 0.51.0 2차 직업은 2차 전직으로
        if (now != null && now.id().equals(j.id())) return "§8이미 " + j.name() + " 입니다.§r";
        if (now != null && now.advanced() && j.id().equals(now.base())) return "§8이미 " + j.name() + " 의 2차 직업 (" + now.display() + ") 입니다.§r";
        int cost = costFor(sp);
        if (cost > 0) {
            int bal = Inv.money(sp);
            if (bal < cost) {
                Shop.angry(sp);
                Notify.notify(sp, "§c" + Inv.CURRENCY_NAME + " 부족 §7(필요 " + cost + ", 보유 " + bal + ")", 40);
                return "§4" + Inv.CURRENCY_NAME + " 부족: 필요 " + cost + ", 보유 " + bal + "§r";
            }
            if (!Inv.pay(sp, cost)) return "§4결제 오류§r";
        }
        JobSystem.setJob(sp, j.id());
        celebrate(sp, j, now == null);
        return "";
    }

    /**
     * 전직 연출 (직업 색 두 빛 — 메이플식 2D 그림 JobFx): 발밑에서 도는 큰 둥근 베기 둘 (거꾸로), 둘레 빛기둥 여섯, 몸에서 솟는 빛 (3인칭 · 다른 사람),
     * 몸 둘레 터짐 세 번 (3인칭) · 1인칭 눈앞 고리 · 손에서 튀는 불꽃, 화면 번쩍임, 제목 (직업 이름 · 역할), 소리 (레벨 업 · 신호기 · 종)
     */
    static void celebrate(ServerPlayer sp, Jobs.Job j, boolean first) {
        celebrate(sp, j, first ? "전직" : "직업 바꾸기");
    }

    /** what = 알림 줄의 일 ("전직" · "직업 바꾸기" · 0.51.0 "2차 전직" · "각성 ↔ 반전 바꾸기") */
    static void celebrate(ServerPlayer sp, Jobs.Job j, String what) {
        ServerLevel level = sp.level();
        Vec3 feet = sp.position();
        double[] glow = JobKit.glow(j);
        double[] core = JobKit.core(j);
        JobFx.ring(level, V.up(feet, 0.06), 3.2, j, false, 0.7, Opt.empty());
        RuneKit.later(4, () -> JobFx.ring(level, V.up(feet, 0.08), 2.0, j, true, 0.6, Opt.of("alpha", 0.85)));
        RuneKit.godRays(sp, level, feet, 2.6, 6, glow, core, Opt.of("h", 12.0, "life", 1.3, "alpha", 0.8));
        JobFx.aura(sp, j, 2.8, 1.4, 0.95);
        JobFx.eyeRing(sp, j, 2.4, 1.0, 0.6);
        String life = RuneKit.lifeToken(sp);
        for (int i = 0; i < 3; i++) {
            RuneKit.later(i * 5, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                RuneKit.Hand h = RuneKit.handOf(sp);
                SkillFx.sparks(level, h.hand(), core, 8, 4);
                JobFx.burst(level, V.up(sp.position(), 1.0), 1.0, j, 0.32, JobFx.hide1p(sp));
            });
        }
        Ult.flash(level, feet, glow, Opt.of("radius", 8.0, "time", new double[] {0.05, 0.12, 0.6}), sp);
        String path = j.advanced() ? Jobs.pathName(j.path()) + " · " : "";
        Notify.title(sp, code(j) + "§l" + j.display(), "§7" + path + j.role() + " §8· §f" + j.weaponText());
        SkillFx.play(level, "random.levelup", feet, 1.1, 1);
        SkillFx.play(level, "beacon.activate", feet, 1.3, 0.8);
        SkillFx.play(level, "block.bell.hit", feet, 0.9, 0.8);
        if (j.advanced()) {
            // 0.51.0 2차 전직: 빛기둥이 한 번 더 · 높이 솟는 깃 (발키리의 깃털 — 하늘에서 내림) · 용의 울음
            RuneKit.later(10, () -> {
                if (!RuneKit.sameLife(sp, life)) return;
                RuneKit.godRays(sp, level, sp.position(), 3.4, 8, glow, core, Opt.of("h", 16.0, "life", 1.2, "alpha", 0.75));
                JobFx.ring(level, V.up(sp.position(), 0.07), 4.2, j, true, 0.8, Opt.of("alpha", 0.8));
                JobFx.feathers(level, V.up(sp.position(), 3.0), 10, 1.6, 0.2, j, 2.0, Opt.empty());
                SkillFx.play(level, "entity.ender_dragon.growl", sp.position(), 1.5, 0.35);
            });
            Notify.say(sp, "§7" + what + " 완료: " + code(j) + "§l" + j.display() + "§r§7 (" + Jobs.pathName(j.path()) + ") — 직업 스킬 다섯이 바뀌었습니다 (강화 단계는 같은 칸으로 이어짐). §8/job 으로 칸 보기");
            return;
        }
        Notify.say(sp, "§7" + what + " 완료: " + code(j) + "§l" + j.name() + "§r§7 — Shift + 룬 칸 키로 직업 스킬 (⇧R 부터 · " + Jobs.unlockLevel(0) + "렙). §8/job 으로 칸 보기");
    }

    // ------------------------------------------------------------ 0.51.0 2차 전직 (각성 · 반전)

    /** 2차 전직 단추 (위 줄): 그 1차 직업의 각성 · 반전 이름 · 레벨 · 값 — 2차 직업이 없거나 50렙 전이면 흐림 (누르면 미리 보기) */
    static Slot advButton(ServerPlayer sp, Jobs.Job now) {
        Jobs.Job base = Jobs.baseOf(now);
        List<Jobs.Job> advs = base == null ? List.of() : Jobs.advancedOf(base.id());
        int lv = Jobs.advanceLevel();
        boolean levelOk = RpgCombat.reqLevelOf(sp) >= lv;
        List<String> lore = new ArrayList<>();
        if (advs.isEmpty()) {
            lore.add("§8" + (base == null ? "" : base.name() + " 의 ") + "2차 전직은 준비 중입니다");
            Slot s = Slot.of(Icon.of("textures/items/nether_star"), "§5§l2차 전직").lore(lore);
            s.dim();
            return s;
        }
        lore.add("§7" + lv + "렙부터 둘 중 하나:");
        for (Jobs.Job a : advs) lore.add("§8" + Jobs.pathName(a.path()) + " §r" + code(a) + a.display() + (a.id().equals(now.id()) ? " §a✔" : ""));
        lore.add("§8각성 = 1차 컨셉 그대로 더 센 스킬 · 반전 = 새 스킬 · 뒤집힌 특성");
        lore.add("§8처음 2차 전직 무료 · 각성 ↔ 반전 " + Math.round(Jobs.switchSilver()) + " " + Inv.CURRENCY_NAME);
        lore.add("");
        lore.add(levelOk ? "§e▶ 누르면 2차 전직 창" : "§c" + lv + "렙부터 §7(지금 " + (int) RpgCombat.reqLevelOf(sp) + "렙) §8— 누르면 미리 보기");
        String title = now.advanced() ? "§5§l2차 전직 §r§7(지금 " + code(now) + now.display() + "§7)" : "§5§l2차 전직";
        // 50렙 전에도 누를 수 있음 (2차 전직 창에서 카드를 미리 봄 — 카드만 흐림)
        return Slot.of(now.advanced() ? JobUpgrades.skillIcon(now.skill(Jobs.SLOTS - 1)) : Icon.of("textures/items/nether_star"), title).lore(lore);
    }

    /** 2차 전직 창 (각성 · 1차 직업 · 반전 카드) — 50렙 전에도 열어 미리 봄 (누르면 이유) */
    private static void advanceList(ServerPlayer sp, String message, Runnable done) {
        if (JobSystem.job(sp) == null) {
            list(sp, "§8먼저 1차 직업을 고르세요.§r", done);
            return;
        }
        ChestForm form = buildAdvance(sp, message);
        form.show(sp, sel -> {
            if (sel < 0) {
                finish(done);
                return;
            }
            if (sel == BACK_SLOT) {
                list(sp, "", done);
                return;
            }
            Jobs.Job adv = advanceAt(sp, sel);
            if (adv == null) {
                advanceList(sp, message, done);
                return;
            }
            String why = advanceBlock(sp, adv);
            if (why != null) advanceList(sp, why, done);
            else confirmAdvance(sp, adv, done);
        });
    }

    /** 2차 전직 창의 칸 번호 → 2차 직업 (각성 · 반전 카드가 아니면 null) */
    static Jobs.Job advanceAt(ServerPlayer sp, int slot) {
        Jobs.Job base = Jobs.baseOf(JobSystem.job(sp));
        if (base == null) return null;
        if (slot == AWAKEN_SLOT) return Jobs.advance(base.id(), "awaken");
        if (slot == INVERT_SLOT) return Jobs.advance(base.id(), "invert");
        return null;
    }

    /** 2차 전직 창 (시험이 칸을 봄) */
    static ChestForm buildAdvance(ServerPlayer sp, String message) {
        Jobs.Job now = JobSystem.job(sp);
        Jobs.Job base = Jobs.baseOf(now);
        int cost = advCostFor(sp);
        ChestForm form = new ChestForm("직업 교관 §8· §52차 전직");
        form.set(BALANCE_SLOT, Shop.balanceSlot(sp));
        if (now != null) form.set(MINE_SLOT, mineSlot(now));
        if (base != null) {
            form.set(ADV_BASE_SLOT, Slot.of(cardItem(base), "§71차 직업 §8· " + code(base) + "§l" + base.name()).lore("§7" + base.role(), "§8특성 「" + (base.trait() == null
                    ? "" : base.trait().name()) + "」", "", "§8← 각성 §7(같은 컨셉 · 더 센 스킬)", "§8→ 반전 §7(새 스킬 · 뒤집힌 특성)").deco());
            for (String path : Jobs.paths()) {
                Jobs.Job a = Jobs.advance(base.id(), path);
                int slot = "awaken".equals(path) ? AWAKEN_SLOT : "invert".equals(path) ? INVERT_SLOT : -1;
                if (a == null || slot < 0) continue;
                boolean mine = now.id().equals(a.id());
                Jobs.Skill ult = a.skill(Jobs.SLOTS - 1);
                Slot s = Slot.of(ult == null ? Icon.item(cardItem(a)) : JobUpgrades.skillIcon(ult), code(a) + "§l" + a.display() + (mine ? " §r§a✔" : "")).lore(advCardLore(sp,
                        a, mine, cost));
                if (!a.ready() || RpgCombat.reqLevelOf(sp) < Jobs.advanceLevel()) s.dim();
                if (!mine && cost > 0) s.price(cost);
                form.set(slot, s);
            }
        }
        form.set(BACK_SLOT, Slot.of(Icon.sprite("icon_cross"), "§7← 직업 교관"));
        form.set(GUIDE_SLOT, Slot.of(Icon.of("textures/items/book_enchanted"), "§e2차 전직 안내").lore(advGuideLore()).deco());
        form.fillEmpty();
        List<String> lines = new ArrayList<>();
        lines.add("§8" + Jobs.advanceLevel() + "렙부터 1차 직업 하나에 §6각성§8 (컨셉 그대로 더 센 스킬 다섯) 또는 §4반전§8 (같은 무기 · 같은 칸 역할의 새 스킬 다섯 · 뒤집힌 특성) 중 하나."
                + " 둘 다 1차보다 초당 피해 +" + Math.round((Jobs.advanceDps() - 1) * 100) + "%.§r");
        lines.add(cost <= 0 ? "§8처음 2차 전직은 §2무료§8. 각성 ↔ 반전 바꾸기는 §2" + Math.round(Jobs.switchSilver()) + "§8 " + Inv.CURRENCY_NAME + ".§r"
                : "§8지금 2차 직업: §0" + now.display() + "§8. 다른 길로 바꾸면 §2" + cost + "§8 " + Inv.CURRENCY_NAME + ".§r");
        if (message != null && !message.isEmpty()) lines.add(message);
        form.body(String.join("\n", lines));
        return form;
    }

    /** 2차 직업 카드 설명: 길 · 역할 · 무기 · 소개 · 특성 · 스킬 다섯 (각성은 ← 1차 스킬 이름) · 누르면 */
    static List<String> advCardLore(ServerPlayer sp, Jobs.Job a, boolean mine, int cost) {
        Jobs.Job base = Jobs.baseOf(a);
        List<String> out = new ArrayList<>();
        out.add("§7" + Jobs.pathName(a.path()) + " §8· §7" + a.role());
        out.add("§8무기 §f" + a.weaponText() + " §8· 적용 스탯 §f" + a.statText());
        for (String l : wrap(a.line(), 26)) out.add("§7" + l);
        out.add("");
        if (a.trait() != null) {
            out.add("§e특성 「" + a.trait().name() + "」");
            for (String l : wrap(a.trait().desc(), 28)) out.add("§7" + l);
            out.add("");
        }
        out.add("§e직업 스킬");
        for (Jobs.Skill k : a.skills()) {
            Jobs.Skill was = base == null ? null : base.skill(k.slot());
            String from = "awaken".equals(a.path()) && was != null ? " §8← " + was.name() : "";
            out.add("§8⇧" + KEYS.charAt(Math.max(0, Math.min(KEYS.length() - 1, k.slot()))) + " §f" + k.name() + from + " §8— " + Jobs.slotRole(k.slot()) + " · " + fmt(k.cd())
                    + "초");
        }
        out.add("");
        int lv = Jobs.advanceLevel();
        if (!a.ready()) out.add("§8준비 중 — 다음 판에 들어갑니다");
        else if (mine) out.add("§a✔ 지금 직업");
        else if (sp != null && RpgCombat.reqLevelOf(sp) < lv) out.add("§c" + lv + "렙부터 §7(지금 " + (int) RpgCombat.reqLevelOf(sp) + "렙)");
        else out.add(cost <= 0 ? "§a▶ 누르면 2차 전직 §7(처음 무료)" : "§e▶ 누르면 각성 ↔ 반전 바꾸기 §7(" + cost + " " + Inv.CURRENCY_NAME + ")");
        return out;
    }

    private static List<String> advGuideLore() {
        List<String> out = new ArrayList<>();
        out.add("§7" + Jobs.advanceLevel() + "렙부터 2차 전직 (직업 교관)");
        out.add("§6각성 §7— 1차 컨셉 그대로, 더 크고 센 스킬");
        out.add("§4반전 §7— 새 스킬 다섯 · 뒤집힌 특성 · 새 색");
        out.add("§7둘 다 1차보다 초당 피해 +" + Math.round((Jobs.advanceDps() - 1) * 100) + "%");
        out.add("§7처음 2차 전직 무료 · 각성 ↔ 반전 " + Math.round(Jobs.switchSilver()) + " " + Inv.CURRENCY_NAME);
        out.add("§71차 직업을 바꾸면 2차 직업도 풀림");
        out.add("§7직업 스킬 강화 단계는 같은 칸으로 이어짐");
        out.add("§8표기: 이명(직업명)");
        return out;
    }

    /** 2차 전직을 막는 이유 (없으면 null) */
    static String advanceBlock(ServerPlayer sp, Jobs.Job a) {
        if (a == null || !a.advanced()) return "§4없는 2차 직업입니다.§r";
        if (!a.ready()) return "§8" + a.display() + " 은(는) 아직 준비 중입니다.§r";
        Jobs.Job now = JobSystem.job(sp);
        if (now == null) return "§8먼저 1차 직업을 고르세요.§r";
        Jobs.Job base = Jobs.baseOf(now);
        if (base == null || !base.id().equals(a.base())) return "§4" + a.display() + " 은(는) " + (base == null ? "" : base.name() + " 의 ") + "2차 직업이 아닙니다.§r";
        if (RpgCombat.reqLevelOf(sp) < Jobs.advanceLevel()) return "§4" + Jobs.advanceLevel() + "렙부터 2차 전직을 할 수 있습니다 §8(지금 " + (int) RpgCombat.reqLevelOf(sp)
                + "렙).§r";
        if (now.id().equals(a.id())) return "§8이미 " + a.display() + " 입니다.§r";
        return null;
    }

    private static void confirmAdvance(ServerPlayer sp, Jobs.Job a, Runnable done) {
        int cost = advCostFor(sp);
        int bal = Inv.money(sp);
        ChestForm form = new ChestForm(cost <= 0 ? "2차 전직 확인" : "각성 ↔ 반전 바꾸기 확인");
        form.set(OK_SLOT, Slot.of(Icon.sprite("icon_check"), cost <= 0 ? "§a2차 전직하기" : "§a길 바꾸기 §7(" + cost + " " + Inv.CURRENCY_NAME + ")"));
        Jobs.Skill u = a.skill(Jobs.SLOTS - 1);
        Slot card = Slot.of(u == null ? Icon.item(cardItem(a)) : JobUpgrades.skillIcon(u), code(a) + "§l" + a.display()).lore(advCardLore(sp, a, false, cost));
        if (cost > 0) card.price(cost);
        form.set(CARD_SLOT, card);
        form.set(NO_SLOT, Slot.of(Icon.sprite("icon_cross"), "§c취소"));
        form.fillEmpty();
        form.body(String.join("\n", advConfirmLines(a, cost, bal)));
        form.show(sp, sel -> {
            if (sel == OK_SLOT) {
                String msg = advance(sp, a);
                if (msg.isEmpty()) finish(done);
                else advanceList(sp, msg, done);
            } else advanceList(sp, "", done);
        });
    }

    /** 2차 전직 확인 창 본문: 이명(직업명) · 길 · 특성 · 스킬 다섯 (설명) · 값 */
    static List<String> advConfirmLines(Jobs.Job a, int cost, int bal) {
        Jobs.Job base = Jobs.baseOf(a);
        List<String> lines = new ArrayList<>();
        lines.add("§0§l" + a.display() + "§r§8 — " + Jobs.pathName(a.path()) + " · " + a.role());
        lines.add("§8" + (base == null ? "" : base.name() + " 의 2차 직업 · ") + "무기 §0" + a.weaponText() + "§8 · " + a.line() + "§r");
        if (a.trait() != null) lines.add("§8특성 「§0" + a.trait().name() + "§8」 " + a.trait().desc() + "§r");
        for (Jobs.Skill k : a.skills()) {
            lines.add("§8⇧" + KEYS.charAt(Math.max(0, Math.min(KEYS.length() - 1, k.slot()))) + " §0" + k.name() + "§8 (" + fmt(k.cd()) + "초) " + k.desc() + "§r");
        }
        lines.add(cost <= 0 ? "§8비용: §2§l무료§r§8 (처음 2차 전직)§r" : "§8비용: §2§l" + cost + "§r§8 " + Inv.CURRENCY_NAME + " · 보유 §0" + bal + "§r");
        lines.add("§8직업 스킬 강화 단계는 같은 칸으로 이어집니다. 왼쪽 초록 칸: " + (cost <= 0 ? "2차 전직" : "길 바꾸기") + " · 오른쪽 빨간 칸: 취소§r");
        return lines;
    }

    /** 2차 전직 · 각성 ↔ 반전 바꾸기 처리 (값 · 직업 · 연출). 실패 사유 ("" = 됨) */
    public static String advance(ServerPlayer sp, Jobs.Job a) {
        String why = advanceBlock(sp, a);
        if (why != null) return why;
        Jobs.Job now = JobSystem.job(sp);
        int cost = advCostFor(sp);
        if (cost > 0) {
            int bal = Inv.money(sp);
            if (bal < cost) {
                Shop.angry(sp);
                Notify.notify(sp, "§c" + Inv.CURRENCY_NAME + " 부족 §7(필요 " + cost + ", 보유 " + bal + ")", 40);
                return "§4" + Inv.CURRENCY_NAME + " 부족: 필요 " + cost + ", 보유 " + bal + "§r";
            }
            if (!Inv.pay(sp, cost)) return "§4결제 오류§r";
        }
        JobSystem.setJob(sp, a.id());
        celebrate(sp, a, now != null && now.advanced() ? "각성 ↔ 반전 바꾸기" : "2차 전직");
        return "";
    }

    // ------------------------------------------------------------ 글자

    /** 띄어쓰기에서 끊어 한 줄 max 자 안팎으로 */
    static List<String> wrap(String text, int max) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > max) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
