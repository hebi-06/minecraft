package mmorpg.fabric.economy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import mmorpg.core.Economy;
import mmorpg.core.Market;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.RpgNet;
import mmorpg.fabric.rune.RuneItems;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.ui.ChestForm;
import mmorpg.fabric.ui.Icon;
import mmorpg.fabric.ui.Inv;
import mmorpg.fabric.ui.ModalForm;
import mmorpg.fabric.ui.Slot;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/**
 * 0.21.0 유저 장터 창 (서버 상자 창 9 × 3 — 상점과 같은 모양): 둘러보기 (윗 두 줄 18칸씩 · 정렬 · 종류 · 쪽), 사기 확인, 판매 등록 (가방에서 고르기 →
 * 가격 입력창), 내 물건 (누르면 내리기), 보관함 (기간이 끝났거나 내렸는데 가방이 가득 찬 물건). 스탯 창 아랫줄 장터 단추 · /장터 (/market).
 */
public final class MarketWindow {
    private MarketWindow() {}

    public static final int PER_PAGE = 18;
    /** 아랫줄 단추 */
    public static final int PREV = 18;
    public static final int SORT = 19;
    public static final int KIND = 20;
    public static final int HELP = 21;
    public static final int SELL = 22;
    public static final int MINE = 23;
    public static final int BOX = 24;
    public static final int PURSE = 25;
    public static final int NEXT = 26;
    /** 확인 창 */
    public static final int OK = 11;
    public static final int ITEM = 13;
    public static final int NO = 15;
    static final int GUARD = 200;

    /** 종류 거르기 */
    public enum Kind {
        ALL("전체"), WEAPON("무기"), ARMOR("갑옷"), RUNE("룬"), OTHER("재료 · 기타");

        public final String label;

        Kind(String label) {
            this.label = label;
        }

        Kind next() {
            return values()[(ordinal() + 1) % values().length];
        }

        static Kind of(ItemStack s) {
            if (RpgCombat.weaponOf(s) != null) return WEAPON;
            if (RuneItems.runeOf(s) != null) return RUNE;
            if (s.get(DataComponents.EQUIPPABLE) != null) return ARMOR;
            return OTHER;
        }

        boolean test(ItemStack s) {
            return this == ALL || of(s) == this;
        }
    }

    /** 둘러보기 상태: 쪽 · 정렬 · 종류 */
    public record View(int page, Market.Sort sort, Kind kind) {
        public static final View START = new View(0, Market.Sort.NEW, Kind.ALL);

        View page(int p) {
            return new View(p, sort, kind);
        }
    }

    static void init() {
        RpgNet.onStatAction("market", MarketWindow::open);
    }

    /** 장터 창을 엶 */
    public static void open(ServerPlayer sp) {
        if (sp == null || sp.isRemoved()) return;
        SkillFx.playFor(sp, "random.chestopen", 1.3, 0.5);
        browse(sp, View.START, "", 0);
    }

    static String sortLabel(Market.Sort s) {
        return switch (s) {
            case NEW -> "새로 올린 순";
            case CHEAP -> "싼 순";
            case DEAR -> "비싼 순";
        };
    }

    static Market.Sort nextSort(Market.Sort s) {
        return Market.Sort.values()[(s.ordinal() + 1) % Market.Sort.values().length];
    }

    /** 채팅 색 글 → 창 본문 색 (밝은 바탕: 초록 · 빨강 · 흰색 · 회색을 짙게) */
    public static String dark(String chat) {
        return (chat == null ? "" : chat).replace("§a", "§2").replace("§c", "§4").replace("§f", "§0").replace("§7", "§8").replace("§e", "§6") + "§r";
    }

    /** 아이템의 설명 줄 (이름 줄 뺀 앞 max 줄 — 무기 · 갑옷 · 룬의 정보) */
    static List<String> itemLines(ServerPlayer sp, ItemStack s, int max) {
        List<String> out = new ArrayList<>();
        try {
            List<Component> tip = s.getTooltipLines(Item.TooltipContext.of(sp.level()), sp, TooltipFlag.NORMAL);
            for (int i = 1; i < tip.size() && out.size() < max; i++) {
                String t = tip.get(i).getString();
                if (!t.isBlank()) out.add("§r§7" + t);
            }
            if (tip.size() - 1 > max) out.add("§8…");
        } catch (RuntimeException e) {
            // 설명을 만들 수 없는 아이템은 이름만
        }
        return out;
    }

    static Slot goldSlot(ServerPlayer sp) {
        long g = Wallet.total(sp, Economy.Kind.GOLD);
        return Slot.of(Icon.item(EconomyItems.GOLD_COIN), "§6보유 골드 §f" + Economy.fmt(g)).count(g)
                .lore("§7지갑 " + Economy.fmt(Wallet.get(sp, Economy.Kind.GOLD)) + " · 가방의 금화 " + Wallet.coins(sp, Economy.Kind.GOLD),
                        "§8골드: 보스 · 퀘스트 보상, 장터에서 팔기")
                .deco();
    }

    /** 물건 칸 (둘러보기 · 내 물건 · 확인 창) */
    static Slot listingSlot(ServerPlayer sp, Market.Listing<MarketStore.Ware> l, String action) {
        ItemStack s = l.item().stack();
        List<String> lore = new ArrayList<>();
        lore.add("§6가격: " + Economy.fmt(l.price()) + " 골드");
        lore.add("§7판매자: §f" + l.sellerName());
        lore.add("§7남은 시간: §f" + Economy.timeLeft(l.expiresAt() - MarketStore.now()) + " §8· #" + l.id());
        List<String> tip = itemLines(sp, s, 6);
        if (!tip.isEmpty()) {
            lore.add("");
            lore.addAll(tip);
        }
        if (action != null && !action.isEmpty()) lore.add(action);
        return Slot.of(s, "§f" + MarketStore.label(s)).lore(lore).price(l.price());
    }

    // ------------------------------------------------------------ 둘러보기

    static void browse(ServerPlayer sp, View v, String message, int guard) {
        if (guard >= GUARD || sp.isRemoved()) return;
        List<Market.Listing<MarketStore.Ware>> list = MarketStore.browse(sp, l -> v.kind().test(l.item().stack()), v.sort());
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        int page = Math.max(0, Math.min(pages - 1, v.page()));
        View cur = v.page(page);
        ChestForm form = new ChestForm("유저 장터 §8· §7" + list.size() + "개");
        Map<Integer, Market.Listing<MarketStore.Ware>> bySlot = new HashMap<>();
        for (int i = 0; i < PER_PAGE; i++) {
            int at = page * PER_PAGE + i;
            if (at >= list.size()) break;
            Market.Listing<MarketStore.Ware> l = list.get(at);
            boolean own = l.seller().equals(sp.getUUID());
            form.set(i, listingSlot(sp, l, own ? "§e내 물건 — 누르면 내리기" : "§a누르면 사기 확인"));
            bySlot.put(i, l);
        }
        int mine = MarketStore.mine(sp).size();
        int boxed = MarketStore.box(sp).size();
        form.set(PREV, page > 0 ? Slot.of(Icon.sprite("icon_prev"), "§e◀ 이전 쪽").lore("§7" + page + " / " + pages + "쪽으로") : Slot.PANE);
        form.set(SORT, Slot.of(Items.HOPPER, "§f정렬: §e" + sortLabel(cur.sort())).lore("§7누르면 " + sortLabel(nextSort(cur.sort())) + "으로"));
        form.set(KIND, Slot.of(Items.COMPASS, "§f종류: §e" + cur.kind().label).lore("§7누르면 " + cur.kind().next().label + "만"));
        form.set(HELP, Slot.of(Items.BOOK, "§e장터 안내").lore("§7골드로만 사고팝니다", "§7등록은 무료 · 팔리면 수수료 " + Math.round(Economy.feeRate() * 100) + "%",
                "§7" + Economy.timeLeft(Economy.marketMillis()) + " 동안 올라감 (지나면 보관함)", "§7한 사람 " + Economy.marketMax() + "개까지",
                "§7가격 " + Economy.fmt(Economy.minPrice()) + " ~ " + Economy.fmt(Economy.maxPrice()) + " 골드", "§8손에 들고 /장터 등록 <가격> 도 됨").deco());
        form.set(SELL, Slot.of(Icon.item(EconomyItems.GOLD_COIN), "§a판매 등록").lore("§7가방에서 아이템을 골라", "§7골드 가격을 정해 올립니다", "§8(내 물건 " + mine + " / "
                + Economy.marketMax() + ")"));
        form.set(MINE, Slot.of(Items.CHEST, "§f내 물건 §7" + mine + " / " + Economy.marketMax()).count(mine).lore("§7올린 물건 보기 · 내리기"));
        form.set(BOX, Slot.of(Items.BARREL, (boxed > 0 ? "§e" : "§f") + "보관함 §7" + boxed + "개").count(boxed)
                .lore("§7기간이 끝났거나 내렸는데", "§7가방이 가득 찬 물건", boxed > 0 ? "§e누르면 꺼내기" : "§8비었음"));
        form.set(PURSE, goldSlot(sp));
        form.set(NEXT, page < pages - 1 ? Slot.of(Icon.sprite("icon_next"), "§e다음 쪽 ▶").lore("§7" + (page + 2) + " / " + pages + "쪽으로") : Slot.PANE);
        form.fillEmpty();
        List<String> lines = new ArrayList<>();
        lines.add(list.isEmpty() ? "§8올라온 물건이 없습니다" + (cur.kind() != Kind.ALL ? " (" + cur.kind().label + ")" : "") + ". 아랫줄 금화 칸: 판매 등록.§r"
                : "§8물건을 누르면 사기 확인 창이 열립니다. 초록 숫자 = 가격 (골드).§r");
        lines.add("§8쪽 " + (page + 1) + "/" + pages + " · 정렬 " + sortLabel(cur.sort()) + " · 종류 " + cur.kind().label + " · 수수료 " + Math.round(Economy.feeRate() * 100)
                + "% (팔릴 때)§r");
        if (message != null && !message.isEmpty()) lines.add(message);
        form.body(String.join("\n", lines));
        form.show(sp, sel -> {
            if (sel < 0) return;
            Consumer<String> back = msg -> browse(sp, cur, msg, guard + 1);
            if (sel == PREV && page > 0) {
                SkillFx.playFor(sp, "item.book.page_turn", 1, 0.8);
                browse(sp, cur.page(page - 1), "", guard + 1);
            } else if (sel == NEXT && page < pages - 1) {
                SkillFx.playFor(sp, "item.book.page_turn", 1, 0.8);
                browse(sp, cur.page(page + 1), "", guard + 1);
            } else if (sel == SORT) browse(sp, new View(0, nextSort(cur.sort()), cur.kind()), "", guard + 1);
            else if (sel == KIND) browse(sp, new View(0, cur.sort(), cur.kind().next()), "", guard + 1);
            else if (sel == SELL) pick(sp, 0, "", back, guard + 1);
            else if (sel == MINE) mine(sp, "", back, guard + 1);
            else if (sel == BOX) box(sp, 0, "", back, guard + 1);
            else if (bySlot.containsKey(sel)) {
                Market.Listing<MarketStore.Ware> l = bySlot.get(sel);
                if (l.seller().equals(sp.getUUID())) confirmCancel(sp, l, back, guard + 1);
                else confirmBuy(sp, l, back, guard + 1);
            } else browse(sp, cur, "", guard + 1);
        });
    }

    // ------------------------------------------------------------ 사기 · 내리기 확인

    static void confirmBuy(ServerPlayer sp, Market.Listing<MarketStore.Ware> l, Consumer<String> back, int guard) {
        if (guard >= GUARD) return;
        long have = Wallet.total(sp, Economy.Kind.GOLD);
        boolean enough = have >= l.price();
        ChestForm form = new ChestForm("사기 확인");
        form.set(4, goldSlot(sp));
        form.set(OK, Slot.of(Icon.sprite("icon_check"), "§a사기 §7(" + Economy.fmt(l.price()) + " 골드)"));
        form.set(ITEM, listingSlot(sp, l, "").deco());
        form.set(NO, Slot.of(Icon.sprite("icon_cross"), "§c취소"));
        form.fillEmpty();
        List<String> lines = new ArrayList<>();
        lines.add("§0" + MarketStore.label(l.item().stack()).replace("§7", "§8") + "§r");
        lines.add("§8가격 §6§l" + Economy.fmt(l.price()) + "§r§8 골드 · 판매자 §0" + l.sellerName() + "§8 · " + Economy.timeLeft(l.expiresAt() - MarketStore.now()) + "§r");
        lines.add(enough ? "§8보유 §0" + Economy.fmt(have) + "§8 → §0" + Economy.fmt(have - l.price()) + "§8 골드§r"
                : "§4골드가 모자랍니다: 보유 " + Economy.fmt(have) + " / " + Economy.fmt(l.price()) + "§r");
        lines.add("§8왼쪽 초록 칸: 사기 · 오른쪽 빨간 칸: 취소§r");
        form.body(String.join("\n", lines));
        form.show(sp, sel -> {
            if (sel < 0) return;
            if (sel != OK) {
                back.accept("");
                return;
            }
            back.accept(dark(MarketStore.buy(sp, l.id()).text()));
        });
    }

    static void confirmCancel(ServerPlayer sp, Market.Listing<MarketStore.Ware> l, Consumer<String> back, int guard) {
        if (guard >= GUARD) return;
        ChestForm form = new ChestForm("내리기 확인");
        form.set(OK, Slot.of(Icon.sprite("icon_check"), "§a내리기"));
        form.set(ITEM, listingSlot(sp, l, "").deco());
        form.set(NO, Slot.of(Icon.sprite("icon_cross"), "§c그대로 두기"));
        form.fillEmpty();
        form.body("§0" + MarketStore.label(l.item().stack()).replace("§7", "§8") + "§r\n§8가격 §0" + Economy.fmt(l.price()) + "§8 골드 · 남은 시간 "
                + Economy.timeLeft(l.expiresAt() - MarketStore.now()) + "§r\n§8내리면 가방으로 돌아옵니다 (가득이면 보관함) · 다시 올려도 무료§r");
        form.show(sp, sel -> {
            if (sel < 0) return;
            if (sel != OK) {
                back.accept("");
                return;
            }
            back.accept(dark(MarketStore.cancel(sp, l.id(), false).text()));
        });
    }

    // ------------------------------------------------------------ 판매 등록

    /** 가방 칸 하나 (번호 · 아이템) */
    record BagItem(int slot, ItemStack stack) {}

    /** 판매 등록 1: 가방에서 고르기 (가방 36칸의 아이템 — 올릴 수 없는 것은 회색 장식) */
    static void pick(ServerPlayer sp, int page, String message, Consumer<String> back, int guard) {
        if (guard >= GUARD || sp.isRemoved()) return;
        List<BagItem> items = new ArrayList<>();
        for (int i = 0; i < Inv.SIZE; i++) {
            ItemStack s = Inv.get(sp, i);
            if (!s.isEmpty()) items.add(new BagItem(i, s.copy()));
        }
        int pages = Math.max(1, (items.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.max(0, Math.min(pages - 1, page));
        int mine = MarketStore.mine(sp).size();
        boolean full = mine >= Economy.marketMax();
        ChestForm form = new ChestForm("판매할 아이템 고르기 §8· §7" + mine + " / " + Economy.marketMax());
        Map<Integer, BagItem> bySlot = new HashMap<>();
        for (int i = 0; i < PER_PAGE; i++) {
            int at = pg * PER_PAGE + i;
            if (at >= items.size()) break;
            BagItem b = items.get(at);
            String why = MarketStore.refuse(b.stack());
            List<String> lore = new ArrayList<>(itemLines(sp, b.stack(), 4));
            if (why != null) {
                lore.add("§c" + why);
                form.set(i, Slot.of(b.stack(), "§7" + MarketStore.label(b.stack())).lore(lore).deco());
            } else {
                lore.add(full ? "§c올릴 수 있는 수를 다 채웠습니다" : "§a누르면 가격 정하기");
                form.set(i, Slot.of(b.stack(), "§f" + MarketStore.label(b.stack())).lore(lore).count(b.stack().getCount()));
                bySlot.put(i, b);
            }
        }
        form.set(PREV, pg > 0 ? Slot.of(Icon.sprite("icon_prev"), "§e◀ 이전 쪽") : Slot.PANE);
        form.set(SELL, Slot.of(Icon.sprite("icon_cross"), "§c장터로 돌아가기"));
        form.set(PURSE, goldSlot(sp));
        form.set(NEXT, pg < pages - 1 ? Slot.of(Icon.sprite("icon_next"), "§e다음 쪽 ▶") : Slot.PANE);
        form.fillEmpty();
        List<String> lines = new ArrayList<>();
        lines.add(items.isEmpty() ? "§8가방이 비었습니다.§r" : "§8올릴 아이템을 누르면 가격 입력창이 열립니다. 회색 칸은 올릴 수 없음.§r");
        lines.add("§8등록 무료 · 팔리면 수수료 " + Math.round(Economy.feeRate() * 100) + "% · " + Economy.timeLeft(Economy.marketMillis()) + " · 쪽 " + (pg + 1) + "/" + pages + "§r");
        if (message != null && !message.isEmpty()) lines.add(message);
        form.body(String.join("\n", lines));
        form.show(sp, sel -> {
            if (sel < 0) return;
            if (sel == SELL) back.accept("");
            else if (sel == PREV && pg > 0) pick(sp, pg - 1, "", back, guard + 1);
            else if (sel == NEXT && pg < pages - 1) pick(sp, pg + 1, "", back, guard + 1);
            else if (bySlot.containsKey(sel)) {
                if (full) pick(sp, pg, "§4" + Market.Refusal.LIMIT.text + "§r", back, guard + 1);
                else priceForm(sp, bySlot.get(sel), "", msg -> pick(sp, pg, msg, back, guard + 1), back, guard + 1);
            } else pick(sp, pg, "", back, guard + 1);
        });
    }

    /** 가격 글자 → 수 ("1,500" · "1500 골드" 도 됨 — 못 읽으면 -1) */
    public static long parsePrice(String s) {
        if (s == null) return -1;
        String d = s.replaceAll("[,\\s]", "").replace("골드", "").replace("g", "").replace("G", "");
        if (d.isEmpty() || d.length() > 12 || !d.chars().allMatch(Character::isDigit)) return -1;
        return Long.parseLong(d);
    }

    /** 판매 등록 2: 가격 (골드 글자) · 개수 (밀대) → 올리기. 실패면 다시 이 창 (까닭을 위에), 성공이면 장터로 */
    static void priceForm(ServerPlayer sp, BagItem b, String error, Consumer<String> again, Consumer<String> done, int guard) {
        if (guard >= GUARD || sp.isRemoved()) return;
        int count = b.stack().getCount();
        ModalForm form = new ModalForm("판매 등록");
        form.header(MarketStore.label(b.stack()));
        if (error != null && !error.isEmpty()) form.label("§c" + error);
        form.label("§7가격을 골드로 적어 주세요 (" + Economy.fmt(Economy.minPrice()) + " ~ " + Economy.fmt(Economy.maxPrice()) + ")\n§7팔리면 수수료 "
                + Math.round(Economy.feeRate() * 100) + "% 를 뺀 골드가 지갑으로 · " + Economy.timeLeft(Economy.marketMillis()) + " 동안");
        form.text("price", "가격 (골드)", "예: 100", "");
        if (count > 1) form.slider("count", "개수", 1, count, count);
        form.submit("올리기");
        form.show(sp, values -> {
            if (values == null) {
                again.accept("");
                return;
            }
            long price = parsePrice(String.valueOf(values.get("price")));
            int n = count > 1 && values.get("count") instanceof Integer c ? c : count;
            if (price < Economy.minPrice() || price > Economy.maxPrice()) {
                priceForm(sp, b, "가격은 " + Economy.fmt(Economy.minPrice()) + " ~ " + Economy.fmt(Economy.maxPrice()) + " 사이의 수로 적어 주세요", again, done, guard + 1);
                return;
            }
            MarketStore.Outcome o = MarketStore.list(sp, b.slot(), b.stack(), n, price);
            if (o.ok()) done.accept(dark(o.text()));
            else again.accept(dark(o.text()));
        });
    }

    // ------------------------------------------------------------ 내 물건 · 보관함

    static void mine(ServerPlayer sp, String message, Consumer<String> back, int guard) {
        if (guard >= GUARD || sp.isRemoved()) return;
        List<Market.Listing<MarketStore.Ware>> mine = MarketStore.mine(sp);
        ChestForm form = new ChestForm("내 물건 §8· §7" + mine.size() + " / " + Economy.marketMax());
        Map<Integer, Market.Listing<MarketStore.Ware>> bySlot = new HashMap<>();
        for (int i = 0; i < Math.min(PER_PAGE, mine.size()); i++) {
            form.set(i, listingSlot(sp, mine.get(i), "§e누르면 내리기"));
            bySlot.put(i, mine.get(i));
        }
        int boxed = MarketStore.box(sp).size();
        form.set(PREV, Slot.of(Icon.sprite("icon_prev"), "§e◀ 장터로"));
        form.set(SELL, Slot.of(Icon.item(EconomyItems.GOLD_COIN), "§a판매 등록"));
        form.set(BOX, Slot.of(Items.BARREL, (boxed > 0 ? "§e" : "§f") + "보관함 §7" + boxed + "개").count(boxed));
        form.set(PURSE, goldSlot(sp));
        form.fillEmpty();
        List<String> lines = new ArrayList<>();
        lines.add(mine.isEmpty() ? "§8올린 물건이 없습니다.§r" : "§8누르면 내립니다 (가방으로 — 가득이면 보관함). 팔리면 지갑으로 골드 (수수료 " + Math.round(Economy.feeRate() * 100)
                + "%).§r");
        if (message != null && !message.isEmpty()) lines.add(message);
        form.body(String.join("\n", lines));
        Consumer<String> here = msg -> mine(sp, msg, back, guard + 1);
        form.show(sp, sel -> {
            if (sel < 0) return;
            if (sel == PREV) back.accept("");
            else if (sel == SELL) pick(sp, 0, "", here, guard + 1);
            else if (sel == BOX) box(sp, 0, "", here, guard + 1);
            else if (bySlot.containsKey(sel)) confirmCancel(sp, bySlot.get(sel), here, guard + 1);
            else here.accept("");
        });
    }

    static void box(ServerPlayer sp, int page, String message, Consumer<String> back, int guard) {
        if (guard >= GUARD || sp.isRemoved()) return;
        List<MarketStore.Ware> box = MarketStore.box(sp);
        int pages = Math.max(1, (box.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.max(0, Math.min(pages - 1, page));
        ChestForm form = new ChestForm("보관함 §8· §7" + box.size() + "개");
        Map<Integer, Integer> bySlot = new HashMap<>();
        for (int i = 0; i < PER_PAGE; i++) {
            int at = pg * PER_PAGE + i;
            if (at >= box.size()) break;
            ItemStack s = box.get(at).stack();
            List<String> lore = new ArrayList<>(itemLines(sp, s, 4));
            lore.add("§a누르면 가방으로");
            form.set(i, Slot.of(s, "§f" + MarketStore.label(s)).lore(lore).count(s.getCount()));
            bySlot.put(i, at);
        }
        form.set(PREV, pg > 0 ? Slot.of(Icon.sprite("icon_prev"), "§e◀ 이전 쪽") : Slot.of(Icon.sprite("icon_prev"), "§e◀ 돌아가기"));
        form.set(SELL, box.isEmpty() ? Slot.PANE : Slot.of(Icon.sprite("icon_check"), "§a모두 꺼내기 §7(" + box.size() + "개)"));
        form.set(NEXT, pg < pages - 1 ? Slot.of(Icon.sprite("icon_next"), "§e다음 쪽 ▶") : Slot.PANE);
        form.fillEmpty();
        List<String> lines = new ArrayList<>();
        lines.add(box.isEmpty() ? "§8보관함이 비었습니다.§r" : "§8누르면 가방으로 꺼냅니다. 기간이 끝났거나 내렸는데 가방이 가득 찼던 물건.§r");
        if (message != null && !message.isEmpty()) lines.add(message);
        form.body(String.join("\n", lines));
        form.show(sp, sel -> {
            if (sel < 0) return;
            if (sel == PREV) {
                if (pg > 0) box(sp, pg - 1, "", back, guard + 1);
                else back.accept("");
            } else if (sel == NEXT && pg < pages - 1) box(sp, pg + 1, "", back, guard + 1);
            else if (sel == SELL && !box.isEmpty()) box(sp, 0, dark(MarketStore.claim(sp, -1).text()), back, guard + 1);
            else if (bySlot.containsKey(sel)) box(sp, pg, dark(MarketStore.claim(sp, bySlot.get(sel)).text()), back, guard + 1);
            else box(sp, pg, "", back, guard + 1);
        });
    }
}
