package mmorpg.fabric.shop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mmorpg.core.Armor;
import mmorpg.core.Economy;
import mmorpg.core.Grade;
import mmorpg.core.Js;
import mmorpg.core.Obj;
import mmorpg.core.Rules;
import mmorpg.core.Weapon;
import mmorpg.core.Weapons;
import mmorpg.fabric.RpgCombat;
import mmorpg.fabric.Tasks;
import mmorpg.fabric.economy.EconomyItems;
import mmorpg.fabric.economy.Wallet;
import mmorpg.fabric.skill.Kit;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.skill.V;
import mmorpg.fabric.smith.SmithNet;
import mmorpg.fabric.smith.Workshop;
import mmorpg.fabric.ui.Icon;
import mmorpg.fabric.ui.Inv;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * 0.45.0 상인 전용 창 (서버 — 사용자: "어우 UI저무 좋은데 룬술사랑 상인 같은 애들도 이렇게 바꾸자" → 고른 것: 한 창에 탭 셋 · 대장장이처럼 동작).
 * 대장장이 창과 같은 창 ({@link Workshop} — 위 탭 · 왼쪽 가게 패널의 상인 (계산대 뒤) · 가운데 목록 · 오른쪽 상세 · 큰 단추):
 * <ul>
 *   <li>탭 셋: 무기 (상점 레벨의 일반 · 희귀 — 쪽 둘: 기본 무기 · 새 무기) · 갑옷 (종류 열 — 고를 것 = 부위 넷 · 한 벌) · 전리품 팔기 (모두 팔기 · 전리품마다
 *       1개 · 10개 · 모두). 값 · 판정은 상자 창과 같은 함수 ({@link Shop#purchaseQuiet} · {@link ArmorShop#buyQuiet} · {@link Shop#sellQuiet} — 눈앞
 *       연출 · 알림 없이)</li>
 *   <li>단추를 누르면 상인이 뒤 선반에서 물건을 꺼내 계산대에 올리고 건넴 (shop_hand) · 전리품을 받아 은화를 세어 내줌 (shop_count) → 끝난 뒤 판정
 *       (돈 · 전리품은 그때 오감)</li>
 *   <li>세상: 상인 NPC 가 사는 사람을 보고 같은 동작 — 계산대 높이에 물건 (표시 엔티티) 이 나타나 사는 사람 쪽으로 건너감 · 전리품이 상인에게 · 은화 소리
 *       (일하는 사람은 창에서 듣고 — 둘레 사람만)</li>
 *   <li>모드가 없는 클라이언트 · 시험의 가짜 플레이어는 예전 상자 창 ({@link Store#storeLoop}) — {@link Workshop#FORCE} · {@link #legacy}</li>
 * </ul>
 */
public final class ShopUi {
    private ShopUi() {}

    private static final Rules R = Rules.get();
    private static final Obj CONF = R.root().objOrNull("SHOP_UI");
    private static final Obj DROPS = R.obj("MONSTER_DROPS");

    public static final String WEAPONS = "weapons";
    public static final String ARMOR = "armor";
    public static final String SELL = "sell";

    /** 사진첩: 예전 상자 창으로 (모드 클라이언트도) */
    public static boolean legacy = false;
    /** 상인 가게 (Workshop) */
    public static final Workshop.Kind KIND = new Counter();

    /** 일 하나의 동작 · 틱 · 닿는 틱 (SHOP_UI — poses.json 의 impacts × ticks 와 같아야 함) */
    public record Timing(String motion, int ticks, List<Integer> impacts) {}

    private static Timing timing(String name, String motion, int ticks, List<Integer> impacts) {
        Obj o = CONF != null ? CONF.objOrNull(name) : null;
        if (o == null) return new Timing(motion, ticks, impacts);
        List<Integer> im = new ArrayList<>();
        List<Double> ds = o.numbersOrNull("impacts");
        if (ds != null) for (double d : ds) im.add((int) d);
        return new Timing(o.strOr("motion", motion), (int) o.numOr("ticks", ticks), im.isEmpty() ? impacts : List.copyOf(im));
    }

    /** 사기: 선반에서 꺼내 계산대에 올림 (첫 닿음) → 건넴 (둘째) */
    public static final Timing BUY_WORK = timing("buy", "shop_hand", 20, List.of(9, 14));
    /** 팔기: 은화를 세어 놓음 (세 번) */
    public static final Timing SELL_WORK = timing("sell", "shop_count", 20, List.of(7, 11, 15));

    private static final double[] GOLD = {1.0, 0.82, 0.3};
    private static final double[] SILVER = {0.85, 0.88, 0.95};
    private static final double[] SMOKE = {0.45, 0.45, 0.5};

    /** 새 창을 쓸지 (모드 클라이언트 — 시험은 Workshop.FORCE, 사진첩은 legacy 로 예전 창) */
    public static boolean usable(ServerPlayer sp) {
        return Workshop.usable(sp, KIND);
    }

    /** 상인 창을 엶 (npc = 상인 NPC — 없으면 null, level = 상점 레벨, start: weapons · armor · sell, done = 닫으면) */
    public static void open(ServerPlayer sp, Entity npc, int level, String start, Runnable done) {
        Workshop.open(sp, KIND, npc, start, level, done);
    }

    // ------------------------------------------------------------ 가게

    private static final class Counter implements Workshop.Kind {
        @Override
        public String id() {
            return "shop";
        }

        @Override
        public String role() {
            return "상인";
        }

        @Override
        public boolean legacy() {
            return legacy;
        }

        @Override
        public List<String> tabs(ServerPlayer sp, Workshop.Session s) {
            return List.of(WEAPONS, ARMOR, SELL);
        }

        @Override
        public String tabName(String tab) {
            return switch (tab) {
                case WEAPONS -> "무기";
                case ARMOR -> "갑옷";
                case SELL -> "전리품 팔기";
                default -> tab;
            };
        }

        @Override
        public void begin(ServerPlayer sp, Workshop.Session s, String start) {
            s.tab = ARMOR.equals(start) ? ARMOR : SELL.equals(start) ? SELL : WEAPONS;
            s.page = 0;
        }

        @Override
        public List<String> pages(ServerPlayer sp, Workshop.Session s) {
            return s.tab.equals(WEAPONS) && Shop.pages() > 1 ? List.of("기본 무기", "새 무기") : List.of();
        }

        @Override
        public String listTitle(ServerPlayer sp, Workshop.Session s) {
            return switch (s.tab) {
                case ARMOR -> {
                    int lv = ArmorShop.armorLevelFor(Math.max(0, s.level));
                    Obj tier = ArmorShop.TIERS.objOrNull("common_" + lv);
                    yield lv + "렙 " + (tier != null ? tier.str("name") : "") + " 갑옷";
                }
                case SELL -> "가진 전리품";
                default -> Math.max(0, s.level) + "렙 무기";
            };
        }

        @Override
        public List<SmithNet.Entry> entries(ServerPlayer sp, Workshop.Session s) {
            return switch (s.tab) {
                case ARMOR -> armorEntries(sp, Math.max(0, s.level));
                case SELL -> sellEntries(sp);
                default -> weaponEntries(sp, Math.max(0, s.level), Math.max(0, s.page));
            };
        }

        @Override
        public List<SmithNet.Res> res(ServerPlayer sp, Workshop.Session s) {
            List<SmithNet.Res> out = new ArrayList<>();
            out.add(new SmithNet.Res(stack(Inv.CURRENCY), Inv.CURRENCY_NAME, Inv.money(sp)));
            if (s.tab.equals(SELL)) out.add(new SmithNet.Res(new ItemStack(Items.BUNDLE), "모두 팔면 " + Inv.CURRENCY_NAME, Shop.sellTotal(sp)));
            else out.add(new SmithNet.Res(stack(EconomyItems.GOLD_COIN), "골드", Wallet.total(sp, Economy.Kind.GOLD)));
            return out;
        }

        @Override
        public String emptyText(ServerPlayer sp, Workshop.Session s) {
            return switch (s.tab) {
                case SELL -> "§7팔 전리품이 없습니다\n§8몬스터를 잡으면 떨어집니다";
                default -> "§7이 쪽에 파는 것이 없습니다";
            };
        }

        @Override
        public Workshop.Prepared prepare(ServerPlayer sp, Workshop.Session s, String key, int variant, boolean flag) {
            return ShopUi.prepare(sp, s, key, variant);
        }

        @Override
        public Workshop.Show show(ServerPlayer sp, Workshop.Session s, Entity npc, Workshop.Prepared p) {
            return ShopUi.show(sp, npc, p);
        }
    }

    private static ItemStack stack(Item it) {
        return it == null ? ItemStack.EMPTY : new ItemStack(it);
    }

    private static SmithNet.Cost silver(ServerPlayer sp, long need) {
        return new SmithNet.Cost(stack(Inv.CURRENCY), Inv.CURRENCY_NAME, Inv.money(sp), need);
    }

    private static String lackSilver(ServerPlayer sp, long need) {
        long bal = Inv.money(sp);
        return bal >= need ? "" : Workshop.lackNote(List.of(Inv.CURRENCY_NAME + " " + Economy.fmt(need - bal)));
    }

    // ------------------------------------------------------------ 목록

    /** 무기 탭 (쪽 — 0 기본 6종 · 1 새 무기 4종): 판매 등급 (일반 · 희귀) 마다 묶음 */
    static List<SmithNet.Entry> weaponEntries(ServerPlayer sp, int level, int page) {
        List<SmithNet.Entry> out = new ArrayList<>();
        long bal = Inv.money(sp);
        double lv = RpgCombat.reqLevelOf(sp);
        for (Object go : Shop.SHOP.list("grades")) {
            String gid = String.valueOf(go);
            Grade grade = R.grade(gid);
            boolean first = true;
            for (String type : Shop.pageTypes(page)) {
                if (!Weapons.isValidWeapon(level, gid, type)) continue;
                Weapon w = Weapons.weaponFrom(type, level, gid);
                if (w == null) continue;
                int price = (int) Weapons.shopPrice(w);
                boolean can = bal >= price;
                boolean gap = Weapons.needsLevel(w) && lv < w.level();
                String note = !can ? lackSilver(sp, price) : gap ? "§e" + w.level() + "렙부터 사용 §7(지금 최고 " + Js.str(lv) + ")" : "";
                int state = !can ? 1 : gap ? 3 : 0;
                String group = first && grade != null ? grade.color() + grade.name() + " 등급" : "";
                out.add(new SmithNet.Entry("w:" + w.id(), Shop.weaponItem(w), "§r" + w.name(), Economy.fmt(price), state, group,
                        w.typeLabel() + " §8· §7" + w.level() + "렙", "", "", weaponLines(w), -1, List.of(silver(sp, price)), "§7스탯 · 레벨이 모자라도 살 수 있음", -1, "사기", can,
                        note, List.of()));
                first = false;
            }
        }
        return out;
    }

    /** 무기 설명 줄 (상세 — 피해 · 치명타 · 스탯 비례 · 특성 먼저, 요구는 끝에) */
    static List<String> weaponLines(Weapon w) {
        List<String> all = Weapons.weaponInfo(w);
        List<String> req = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        for (int i = 1; i < all.size(); i++) {
            String l = all.get(i).startsWith("§r") ? all.get(i).substring(2) : all.get(i);
            if (l.contains("요구")) req.add(l);
            else rest.add(l);
        }
        List<String> out = new ArrayList<>(rest.subList(0, Math.min(5, rest.size())));
        out.addAll(req);
        return List.copyOf(out);
    }

    /** 갑옷 탭: 종류 열 (고를 것 = 부위 넷 · 한 벌 — 한 벌은 할인) */
    static List<SmithNet.Entry> armorEntries(ServerPlayer sp, int level) {
        List<SmithNet.Entry> out = new ArrayList<>();
        int lv = ArmorShop.armorLevelFor(level);
        Obj tier = ArmorShop.TIERS.objOrNull("common_" + lv);
        long bal = Inv.money(sp);
        List<String> order = R.strings("ARMOR_TYPE_ORDER");
        for (int i = 0; i < order.size(); i++) {
            String type = order.get(i);
            List<Armor.Piece> pieces = new ArrayList<>();
            for (String p : Armor.PIECE_ORDER) pieces.add(Armor.armorFrom(type, lv, "common", p));
            if (pieces.stream().anyMatch(java.util.Objects::isNull)) continue;
            Armor.Piece chest = pieces.get(Math.min(1, pieces.size() - 1));
            List<SmithNet.Variant> vs = new ArrayList<>();
            boolean any = false;
            for (Armor.Piece a : pieces) {
                int price = (int) Armor.armorPrice(a);
                boolean can = bal >= price;
                any |= can;
                vs.add(new SmithNet.Variant(a.pieceName(), ArmorShop.armorItem(a), List.of(silver(sp, price)), can, lackSilver(sp, price),
                        "§7방어 §f+" + Js.str(a.prot()) + " §8· §7" + Economy.fmt(price) + " " + Inv.CURRENCY_NAME));
            }
            int setPrice = (int) Armor.setPrice(type, lv);
            boolean canSet = bal >= setPrice;
            any |= canSet;
            int off = (int) Math.round((1 - ArmorShop.SET_DISCOUNT) * 100);
            vs.add(new SmithNet.Variant("한 벌", ArmorShop.armorItem(chest), List.of(silver(sp, setPrice)), canSet, lackSilver(sp, setPrice),
                    "§a네 부위 " + off + "% 할인 §8· §7" + Economy.fmt(setPrice) + " " + Inv.CURRENCY_NAME));
            List<String> lines = new ArrayList<>();
            lines.add("§7세트 방어 §f" + Js.str(chest.setProt()) + " §8· " + Armor.bonusText(chest.setBonus(), false));
            lines.add("§7요구 " + Armor.armorReqText(chest));
            String ws = ArmorShop.weaponNames(type);
            if (!ws.isEmpty()) lines.add("§8어울리는 무기: " + ws);
            List<String> lack = ArmorShop.lack(sp, chest);
            String note = lack.isEmpty() ? "" : "§e입으려면 " + String.join(" · ", lack) + " 필요";
            Obj t = ArmorShop.TYPES.objOrNull(type);
            String label = t != null ? t.str("color") + t.str("name") + " 갑옷" : chest.typeName();
            String group = i == 0 ? (tier != null ? tier.str("name") + " " : "") + lv + "렙" : i == 5 ? "두 스탯 무기용" : "";
            out.add(new SmithNet.Entry("a:" + type, ArmorShop.armorItem(chest), "§r" + label, any ? Economy.fmt(setPrice) : "", any ? (lack.isEmpty() ? 0 : 3) : 1, group,
                    "부위마다 또는 한 벌 §8· §7" + lv + "렙", "", "", List.copyOf(lines), -1, List.of(), "", -1, "사기", any, note, List.copyOf(vs)));
        }
        return out;
    }

    /** 전리품 팔기 탭: 모두 팔기 (가진 것이 있으면 맨 위) · 가진 전리품 · 그 밖 (몬스터 차례 — 없으면 흐리게) */
    static List<SmithNet.Entry> sellEntries(ServerPlayer sp) {
        List<SmithNet.Entry> out = new ArrayList<>();
        List<Shop.Loot> list = Shop.sellList(sp);
        int total = 0;
        List<String> parts = new ArrayList<>();
        for (Shop.Loot e : list) {
            if (e.n() <= 0) continue;
            total += e.n() * e.price();
            parts.add(e.d().str("color") + e.d().str("name") + " §7" + e.n());
        }
        if (total > 0) {
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < parts.size() && lines.size() < 5; i += 2) lines.add(String.join(" §8· ", parts.subList(i, Math.min(parts.size(), i + 2))));
            if (parts.size() > 10) lines.add("§8외 " + (parts.size() - 10) + "종");
            out.add(new SmithNet.Entry("all", new ItemStack(Items.BUNDLE), "§a모두 팔기", "+" + Economy.fmt(total), 0, "한 번에", parts.size() + "종 · 가진 전리품 전부",
                    "+" + Economy.fmt(total) + " " + Inv.CURRENCY_NAME, "", List.copyOf(lines), -1, List.of(), "", -1, "모두 팔기", true, "", List.of()));
        }
        boolean firstHave = true;
        boolean firstNone = true;
        for (Shop.Loot e : list) {
            if (e.n() <= 0) continue;
            out.add(lootEntry(sp, e, firstHave ? "가진 전리품" : ""));
            firstHave = false;
        }
        for (Shop.Loot e : list) {
            if (e.n() > 0) continue;
            out.add(lootEntry(sp, e, firstNone ? "그 밖의 전리품" : ""));
            firstNone = false;
        }
        return out;
    }

    /** 팔 개수 고를 것: 1개 · 10개 (10개보다 많을 때) · 모두 (2개 이상일 때) */
    static int[] counts(int n) {
        if (n <= 1) return new int[] {1};
        if (n <= 10) return new int[] {1, n};
        return new int[] {1, 10, n};
    }

    private static SmithNet.Entry lootEntry(ServerPlayer sp, Shop.Loot e, String group) {
        Obj d = e.d();
        Obj m = Shop.MONSTERS.objOrNull(e.key());
        int price = e.price();
        int n = e.n();
        ItemStack icon = stack(e.item());
        List<SmithNet.Variant> vs = new ArrayList<>();
        if (n > 0) {
            for (int k : counts(n)) {
                String label = k == n && n > 1 ? "모두 " + n : k + "개";
                vs.add(new SmithNet.Variant(label, icon.copyWithCount(Math.max(1, Math.min(64, k))),
                        List.of(new SmithNet.Cost(icon, d.str("name"), n, k)), true, "", "§7받음 §a+" + Economy.fmt((long) k * price) + " " + Inv.CURRENCY_NAME));
            }
        }
        List<String> lines = new ArrayList<>();
        lines.add("§71개 §f" + Economy.fmt(price) + " " + Inv.CURRENCY_NAME);
        if (n > 0) lines.add("§7보유 " + n + "개 §8→ §a" + Economy.fmt((long) n * price) + " " + Inv.CURRENCY_NAME);
        if (m != null) lines.add("§8" + m.str("name") + " (Lv." + Js.str(m.num("level")) + ") 전리품");
        lines.add("§8반복 의뢰에 쓰면 더 받음 (여관 주인)");
        return new SmithNet.Entry("l:" + e.key(), icon, "§r" + d.str("color") + d.str("name"), n > 0 ? "×" + n : "", n > 0 ? 0 : 1, group,
                "1개 " + Economy.fmt(price) + " " + Inv.CURRENCY_NAME, "", "", List.copyOf(lines), -1, List.of(), "", -1, "팔기", n > 0, n > 0 ? "" : "§7가진 것이 없습니다",
                List.copyOf(vs));
    }

    // ------------------------------------------------------------ 할 일

    private static Workshop.Prepared work(Workshop.Job job, ItemStack shown, int grade, String fx, Timing t) {
        return new Workshop.Prepared(job, shown, grade, null, fx, t.motion(), t.ticks(), t.impacts());
    }

    private static SmithNet.Result error(int seq, String key, String text) {
        return new SmithNet.Result(seq, "error", key, "§c취소", Workshop.onDark(text), ItemStack.EMPTY);
    }

    /** 열쇠의 할 일 (그 순간의 값 · 개수를 잡아 둠). 열쇠: w:무기 id · a:갑옷 종류 (고를 것 = 부위 · 한 벌) · all · l:몬스터 (고를 것 = 1개 · 10개 · 모두) */
    static Workshop.Prepared prepare(ServerPlayer sp, Workshop.Session s, String key, int variant) {
        String[] k = key == null ? new String[0] : key.split(":", 2);
        int level = Math.max(0, s.level);
        try {
            switch (k.length > 0 ? k[0] : "") {
                case "w" -> {
                    Weapon w = null;
                    for (Object go : Shop.SHOP.list("grades")) {
                        for (String type : Shop.pageTypes(Math.max(0, s.page))) {
                            String gid = String.valueOf(go);
                            if (!Weapons.isValidWeapon(level, gid, type)) continue;
                            Weapon x = Weapons.weaponFrom(type, level, gid);
                            if (x != null && x.id().equals(k[1])) w = x;
                        }
                    }
                    if (w == null) return Workshop.Prepared.fail("§c팔지 않는 무기입니다");
                    Weapon ww = w;
                    int price = (int) Weapons.shopPrice(w);
                    Workshop.Job job = seq -> {
                        String err = Shop.purchaseQuiet(sp, ww);
                        if (err != null) return error(seq, key, err);
                        return new SmithNet.Result(seq, "done", key, "§a§l구매 완료", ww.name() + " §8· §7" + Inv.CURRENCY_NAME + " §f-" + Economy.fmt(price)
                                + Weapons.levelNote(ww, RpgCombat.reqLevelOf(sp)), Shop.weaponItem(ww));
                    };
                    return work(job, Shop.weaponItem(w), w.gradeIndex(), "buy", BUY_WORK);
                }
                case "a" -> {
                    int lv = ArmorShop.armorLevelFor(level);
                    List<Armor.Piece> set = new ArrayList<>();
                    for (String p : Armor.PIECE_ORDER) set.add(Armor.armorFrom(k[1], lv, "common", p));
                    if (set.isEmpty() || set.stream().anyMatch(java.util.Objects::isNull)) return Workshop.Prepared.fail("§c팔지 않는 갑옷입니다");
                    boolean one = variant >= 0 && variant < set.size();
                    List<Armor.Piece> pieces = one ? List.of(set.get(variant)) : set;
                    int price = one ? (int) Armor.armorPrice(set.get(variant)) : (int) Armor.setPrice(k[1], lv);
                    Armor.Piece shown = pieces.get(Math.min(1, pieces.size() - 1));
                    Workshop.Job job = seq -> {
                        String err = ArmorShop.buyQuiet(sp, pieces, price);
                        if (err != null) return error(seq, key, err);
                        String what = one ? shown.name() : shown.setName() + " 세트 §8· §7" + shown.typeName();
                        return new SmithNet.Result(seq, "done", key, "§a§l구매 완료", what + " §8· §7" + Inv.CURRENCY_NAME + " §f-" + Economy.fmt(price),
                                ArmorShop.armorItem(shown));
                    };
                    return work(job, ArmorShop.armorItem(shown), shown.gradeIndex(), "buy", BUY_WORK);
                }
                case "all" -> {
                    Map<String, Integer> what = new LinkedHashMap<>();
                    ItemStack shown = ItemStack.EMPTY;
                    for (Shop.Loot e : Shop.sellList(sp)) {
                        if (e.n() <= 0) continue;
                        what.put(e.key(), e.n());
                        if (shown.isEmpty() && e.item() != null) shown = new ItemStack(e.item(), Math.max(1, Math.min(64, e.n())));
                    }
                    if (what.isEmpty()) return Workshop.Prepared.fail("§c팔 전리품이 없습니다");
                    Workshop.Job job = seq -> sold(seq, key, Shop.sellQuiet(sp, what));
                    return work(job, shown, 0, "sell", SELL_WORK);
                }
                case "l" -> {
                    Obj d = DROPS.objOrNull(k[1]);
                    if (d == null) return Workshop.Prepared.fail("§c팔 수 없습니다");
                    Item item = Icon.itemById(d.str("item"));
                    int n = item == null ? 0 : Inv.count(sp, item);
                    if (n <= 0) return Workshop.Prepared.fail("§c가진 것이 없습니다");
                    // 고를 것: 1개 · (10개) · 모두 — 목록과 같은 차례
                    int[] ks = counts(n);
                    int count = ks[Math.max(0, Math.min(ks.length - 1, variant))];
                    String mk = k[1];
                    Workshop.Job job = seq -> sold(seq, key, Shop.sellQuiet(sp, Map.of(mk, count)));
                    return work(job, new ItemStack(item, Math.max(1, Math.min(64, count))), 0, "sell", SELL_WORK);
                }
                default -> {
                    return Workshop.Prepared.fail("§c알 수 없는 칸");
                }
            }
        } catch (RuntimeException err) {
            return Workshop.Prepared.fail("§c알 수 없는 칸");
        }
    }

    private static SmithNet.Result sold(int seq, String key, Shop.Sold s) {
        if (s == null) return error(seq, key, "§c팔 전리품이 없습니다");
        String list = String.join(" · ", s.lines().subList(0, Math.min(3, s.lines().size()))) + (s.lines().size() > 3 ? " 외 " + (s.lines().size() - 3) : "");
        return new SmithNet.Result(seq, "done", key, "§a§l+" + Economy.fmt(s.got()) + " " + Inv.CURRENCY_NAME, "§7판매 §f" + list,
                new ItemStack(Inv.CURRENCY, Math.max(1, Math.min(64, s.got() / 10 + 1))));
    }

    // ------------------------------------------------------------ 세상 연출 (상인 NPC)

    /** 세상: 상인이 사는 사람을 보고 — 계산대 높이 (몸 앞) 에 물건 */
    static Workshop.Show show(ServerPlayer sp, Entity npc, Workshop.Prepared p) {
        if (!(npc.level() instanceof ServerLevel level)) return null;
        float yaw = Kit.lookRotation(npc.position(), new Vec3(sp.getX(), npc.getY(), sp.getZ()))[0];
        Vec3 f = Vec3.directionFromRotation(0, yaw);
        Vec3 at = npc.position().add(f.x * 0.75, 1.05, f.z * 0.75);
        return p.fx().equals("sell") ? new Selling(sp, level, yaw, at, p) : new Handing(sp, level, yaw, at, p);
    }

    /** 사는 사람 가슴께 (물건 · 은화가 건너가는 곳 — 표시 엔티티 자리 at 에서 옮길 몫) */
    private static Vec3 toBuyer(ServerPlayer sp, Vec3 at) {
        return V.scale(V.sub(V.up(sp.position(), 1.0), at), 0.85);
    }

    /** 사기: 물건이 계산대에 놓였다 (첫 닿음) 사는 사람 쪽으로 건너감 (둘째) */
    private static final class Handing implements Workshop.Show {
        final ServerPlayer sp;
        final ServerLevel level;
        final float yaw;
        final Vec3 at;
        final Workshop.Prepared p;
        Display.ItemDisplay goods;
        Vec3 off = Vec3.ZERO;

        Handing(ServerPlayer sp, ServerLevel level, float yaw, Vec3 at, Workshop.Prepared p) {
            this.sp = sp;
            this.level = level;
            this.yaw = yaw;
            this.at = at;
            this.p = p;
        }

        @Override
        public float yaw() {
            return yaw;
        }

        @Override
        public void impact(int i, boolean last) {
            if (i == 0) {
                goods = Workshop.floatItem(level, at, p.shown(), 0.5, p.ticks() + 30);
                level.sendParticles(ParticleTypes.POOF, at.x, at.y - 0.15, at.z, 3, 0.15, 0.02, 0.15, 0.01);
                Workshop.playExcept(level, sp, "entity.item_frame.add_item", at, 0.9, 0.8);
            }
            if (last) {
                off = toBuyer(sp, at);
                Workshop.moveItem(goods, off, 0.35, 5);
                SkillFx.sparks(level, at, GOLD, 6, 2);
                Workshop.playExcept(level, sp, "random.orb", at, 1.3, 0.6);
                Workshop.playExcept(level, sp, "block.chain.place", at, 1.6, 0.5);
            }
        }

        @Override
        public void end(SmithNet.Result r) {
            Workshop.fade(goods, off, 2);
            if (r != null) resultFx(sp, level, at, r.kind());
        }
    }

    /** 팔기: 전리품이 사는 사람 쪽에서 계산대로 → 은화 소리 세 번 → 은화가 사는 사람 쪽으로 */
    private static final class Selling implements Workshop.Show {
        final ServerPlayer sp;
        final ServerLevel level;
        final float yaw;
        final Vec3 at;
        final Display.ItemDisplay loot;
        Display.ItemDisplay coins;
        Vec3 coinOff = Vec3.ZERO;

        Selling(ServerPlayer sp, ServerLevel level, float yaw, Vec3 at, Workshop.Prepared p) {
            this.sp = sp;
            this.level = level;
            this.yaw = yaw;
            this.at = at;
            // 사는 사람 쪽에서 나타나 계산대로
            Vec3 from = toBuyer(sp, at);
            Vec3 start = V.add(at, from);
            this.loot = Workshop.floatItem(level, start, p.shown(), 0.45, p.ticks() + 30);
            Vec3 back = V.scale(from, -1);
            Tasks.later(3, () -> Workshop.moveItem(loot, back, 0.45, 4));
        }

        @Override
        public float yaw() {
            return yaw;
        }

        @Override
        public void impact(int i, boolean last) {
            SkillFx.sparks(level, at, SILVER, 4, 1.5);
            Workshop.playExcept(level, sp, "block.chain.place", at, 1.4 + i * 0.12, 0.55);
            if (i == 0) {
                Workshop.fade(loot, V.scale(toBuyer(sp, at), -1), 3);
                coins = Workshop.floatItem(level, at, stack(Inv.CURRENCY), 0.4, 60);
            }
            if (last) {
                coinOff = toBuyer(sp, at);
                Workshop.moveItem(coins, coinOff, 0.3, 5);
                Workshop.playExcept(level, sp, "random.orb", at, 1.2, 0.6);
            }
        }

        @Override
        public void end(SmithNet.Result r) {
            Workshop.fade(loot, V.scale(toBuyer(sp, at), -1), 1);
            Workshop.fade(coins, coinOff, 2);
            if (r != null) resultFx(sp, level, at, r.kind());
        }
    }

    /** 결과 (세상): 됨 = 상인이 끄덕 (기쁜 반짝이 · 주민 소리), 취소 = 연기 */
    private static void resultFx(ServerPlayer sp, ServerLevel level, Vec3 at, String kind) {
        if (kind.equals("done")) {
            SkillFx.vanilla(level, "minecraft:villager_happy", V.up(at, 0.4));
            Workshop.playExcept(level, sp, "mob.villager.yes", at, 1.0, 0.6);
        } else {
            SkillFx.burst(level, at, SMOKE, 0.5, 0.3);
            Workshop.playExcept(level, sp, "entity.villager.no", at, 1.0, 0.5);
        }
    }
}
