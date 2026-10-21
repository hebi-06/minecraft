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
 */
public final class JobTrainer {
    private JobTrainer() {}

    /** 창의 칸: 지금 직업 · 보유 은화 · 안내 */
    static final int MINE_SLOT = 0;
    static final int BALANCE_SLOT = 4;
    static final int GUIDE_SLOT = 22;
    /** 직업 카드 칸 (jobs.json order 순서): 가운데 줄 일곱 + 아랫줄 넷 (아랫줄 가운데는 안내) */
    static final int[] CARD_SLOTS = {10, 11, 12, 13, 14, 15, 16, 20, 21, 23, 24};
    /** 확인 창: 전직 · 카드 · 취소 */
    static final int OK_SLOT = 11;
    static final int CARD_SLOT = 13;
    static final int NO_SLOT = 15;
    /** 칸 키 이름 (Shift + 룬 칸 1 ~ 5 의 기본 키) */
    static final String KEYS = "RZVBY";

    /** 직업 이름 글자색 (창 · 알림 — § 색 하나: 직업 색에 가까운 것) */
    private static final Map<String, String> CODE = Map.ofEntries(Map.entry("swordsman", "§b"), Map.entry("warrior", "§c"), Map.entry("spellblade", "§d"),
            Map.entry("mage", "§9"), Map.entry("archer", "§a"), Map.entry("assassin", "§5"), Map.entry("lancer", "§6"), Map.entry("reaper", "§3"),
            Map.entry("paladin", "§e"), Map.entry("priest", "§f"), Map.entry("guardian", "§7"));
    /** 카드 그림 (직업 id → 무기 아이템) */
    private static final Map<String, ItemStack> CARD_ITEMS = new ConcurrentHashMap<>();

    /** 직업 이름 글자색 */
    public static String code(Jobs.Job j) {
        return j == null ? "§f" : CODE.getOrDefault(j.id(), "§f");
    }

    /** NPC 대화의 선택지 글자 */
    public static String optionText(ServerPlayer sp) {
        Jobs.Job j = JobSystem.job(sp);
        return j == null ? "직업 고르기 (전직)" : "직업 바꾸기 §8(지금 " + j.name() + ")";
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
        if (now != null) {
            form.set(MINE_SLOT, Slot.of(cardItem(now), "§7지금 직업 §8· " + code(now) + "§l" + now.name()).lore("§7" + now.role(), "§8무기 §f" + now.weaponText()
                    + " §8· 스탯 §f" + now.statText()).deco());
        }
        List<String> order = Jobs.order();
        for (int i = 0; i < Math.min(order.size(), CARD_SLOTS.length); i++) {
            Jobs.Job j = Jobs.job(order.get(i));
            if (j == null) continue;
            boolean mine = now != null && now.id().equals(j.id());
            Slot s = Slot.of(cardItem(j), code(j) + "§l" + j.name() + (mine ? " §r§a✔" : "")).lore(cardLore(j, mine, cost));
            if (!j.ready()) s.dim();
            form.set(CARD_SLOTS[i], s);
        }
        form.set(GUIDE_SLOT, Slot.of(Icon.of("textures/items/book_enchanted"), "§e직업 안내").lore(guideLore(cost)).deco());
        form.fillEmpty();
        List<String> lines = new ArrayList<>();
        lines.add(now == null ? "§8직업을 고르면 Shift + 룬 칸 키 (⇧R · ⇧Z · ⇧V · ⇧B · ⇧Y) 로 직업 스킬을 씁니다. 첫 전직은 무료입니다.§r"
                : "§8지금 직업: §0" + now.name() + "§8. 다른 직업으로 바꾸면 §2" + cost + "§8 " + Inv.CURRENCY_NAME + " (자원 · 태세 · 재사용 대기는 처음부터).§r");
        lines.add("§8카드를 누르면 특성 · 스킬 설명과 확인 창이 열립니다. 흐린 카드는 준비 중인 직업입니다.§r");
        if (message != null && !message.isEmpty()) lines.add(message);
        form.body(String.join("\n", lines));
        return form;
    }

    /** 카드 설명 줄: 역할 · 무기 · 스탯 · 한 줄 소개 · 특성 · 스킬 다섯 (칸 키 · 이름 · 칸 역할 · 해금 레벨 · 재사용 대기) · 누르면 */
    static List<String> cardLore(Jobs.Job j, boolean mine, int cost) {
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
        else if (mine) out.add("§a✔ 지금 직업");
        else out.add(cost <= 0 ? "§a▶ 누르면 전직 §7(첫 전직 무료)" : "§e▶ 누르면 직업 바꾸기 §7(" + cost + " " + Inv.CURRENCY_NAME + ")");
        return out;
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
        Slot card = Slot.of(cardItem(j), code(j) + "§l" + j.name()).lore(cardLore(j, false, cost));
        if (cost > 0) card.price(cost);
        form.set(CARD_SLOT, card);
        form.set(NO_SLOT, Slot.of(Icon.sprite("icon_cross"), "§c취소"));
        form.fillEmpty();
        form.body(String.join("\n", confirmLines(j, cost, bal)));
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
        if (now != null && now.id().equals(j.id())) return "§8이미 " + j.name() + " 입니다.§r";
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
        Notify.title(sp, code(j) + "§l" + j.name(), "§7" + j.role() + " §8· §f" + j.weaponText());
        SkillFx.play(level, "random.levelup", feet, 1.1, 1);
        SkillFx.play(level, "beacon.activate", feet, 1.3, 0.8);
        SkillFx.play(level, "block.bell.hit", feet, 0.9, 0.8);
        Notify.say(sp, "§7" + (first ? "전직" : "직업 바꾸기") + " 완료: " + code(j) + "§l" + j.name() + "§r§7 — Shift + 룬 칸 키로 직업 스킬 (⇧R 부터 · "
                + Jobs.unlockLevel(0) + "렙). §8/job 으로 칸 보기");
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
