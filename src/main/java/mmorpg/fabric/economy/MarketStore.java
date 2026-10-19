package mmorpg.fabric.economy;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import mmorpg.core.Economy;
import mmorpg.core.Market;
import mmorpg.core.Wallets;
import mmorpg.fabric.Mmorpg;
import mmorpg.fabric.Notify;
import mmorpg.fabric.StatBookItem;
import mmorpg.fabric.gear.NpcToolItem;
import mmorpg.fabric.quest.QuestRewards;
import mmorpg.fabric.skill.SkillFx;
import mmorpg.fabric.ui.Inv;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.phys.Vec3;

/**
 * 0.21.0 유저 장터 (사용자 요청 — 골드로만 사고팖 · 판매 수수료 5% · 등록 무료 · 3일 · 한 사람 10개): 올라온 물건 · 보관함 (기간이 끝났거나
 * 내렸는데 가방이 가득 찬 물건) · 접속하지 않은 판매자의 대금 · 접속하면 볼 글. 오버월드에 붙여 저장 (서버 데이터 — 바뀔 때마다 새 값으로).
 * 물건은 아이템 코덱의 JSON 글자로 저장 (하나가 망가져도 나머지는 그대로 · 모드가 빠져 모르는 아이템은 글자 그대로 따로 보관했다가 다시 읽어 봄).
 * 규칙 (가격 · 개수 · 기간 · 수수료) 은 core {@link Market}. 창은 {@link MarketWindow}.
 */
public final class MarketStore {
    private MarketStore() {}

    /** 장터의 물건: 아이템 + 저장 글자 (올릴 때 한 번 만듦) */
    public record Ware(ItemStack stack, String json) {}

    /** 저장: 등록 하나 */
    public record Stored(long id, String seller, String name, String item, long price, long listedAt, long expiresAt) {
        static final Codec<Stored> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.LONG.fieldOf("id").forGetter(Stored::id),
                        Codec.STRING.fieldOf("seller").forGetter(Stored::seller),
                        Codec.STRING.optionalFieldOf("name", "").forGetter(Stored::name),
                        Codec.STRING.fieldOf("item").forGetter(Stored::item),
                        Codec.LONG.fieldOf("price").forGetter(Stored::price),
                        Codec.LONG.fieldOf("listed").forGetter(Stored::listedAt),
                        Codec.LONG.fieldOf("expires").forGetter(Stored::expiresAt))
                .apply(i, Stored::new));
    }

    /** 저장: 사람 하나의 보관함 · 받을 골드 · 글 (items 는 아이템 JSON) */
    public record Box(String owner, long gold, List<String> items, List<String> notes) {
        static final Codec<Box> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.STRING.fieldOf("owner").forGetter(Box::owner),
                        Codec.LONG.optionalFieldOf("gold", 0L).forGetter(Box::gold),
                        Codec.STRING.listOf().optionalFieldOf("items", List.of()).forGetter(Box::items),
                        Codec.STRING.listOf().optionalFieldOf("notes", List.of()).forGetter(Box::notes))
                .apply(i, Box::new));
    }

    /** 저장 전체 (broken = 읽지 못한 등록 — 그대로 보관) */
    public record State(long next, List<Stored> listings, List<Box> boxes, List<Stored> broken) {
        static final State EMPTY = new State(1, List.of(), List.of(), List.of());
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.LONG.optionalFieldOf("next", 1L).forGetter(State::next),
                        Stored.CODEC.listOf().optionalFieldOf("listings", List.of()).forGetter(State::listings),
                        Box.CODEC.listOf().optionalFieldOf("boxes", List.of()).forGetter(State::boxes),
                        Stored.CODEC.listOf().optionalFieldOf("broken", List.of()).forGetter(State::broken))
                .apply(i, State::new));
    }

    public static AttachmentType<State> TYPE;
    static final Market<Ware> MARKET = new Market<>();
    /** 보관함 (사람 → 물건) */
    static final Map<UUID, List<Ware>> BOX = new LinkedHashMap<>();
    /** 보관함의 읽지 못한 물건 (JSON 그대로) */
    static final Map<UUID, List<String>> BOX_BROKEN = new LinkedHashMap<>();
    /** 접속하지 않은 판매자의 대금 */
    static final Wallets PENDING = new Wallets();
    /** 접속하면 보여 줄 글 (사람마다 최근 것만) */
    static final Map<UUID, List<String>> NOTES = new LinkedHashMap<>();
    static final List<Stored> BROKEN = new ArrayList<>();
    static final int MAX_NOTES = 20;
    /** 아이템 JSON 이 이보다 크면 올릴 수 없음 (UTF-8 바이트 — 저장 글자 하나의 한도 65,535 안쪽) */
    static final int MAX_JSON_BYTES = 32_000;
    static final double[] GOLD = {1.0, 0.8, 0.25};
    static MinecraftServer server;
    /** 시험용 시계 (null = 실제 시각, 밀리초) */
    public static LongSupplier clock;
    /** 시험용: 팔린 수 */
    public static int sold;

    static void init() {
        TYPE = AttachmentRegistry.create(Mmorpg.id("market"), b -> b.persistent(State.CODEC).initializer(() -> State.EMPTY));
        ServerLifecycleEvents.SERVER_STARTED.register(MarketStore::load);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            clear();
            server = null;
        });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (s.getTickCount() % 20 == 0) expire(s);
        });
    }

    public static long now() {
        return clock != null ? clock.getAsLong() : System.currentTimeMillis();
    }

    static void ensure(MinecraftServer s) {
        if (s != null && s != server) load(s);
    }

    static void ensure(ServerPlayer sp) {
        ensure(sp.level().getServer());
    }

    private static void clear() {
        MARKET.load(List.of(), 1);
        BOX.clear();
        BOX_BROKEN.clear();
        PENDING.clear();
        NOTES.clear();
        BROKEN.clear();
    }

    // ------------------------------------------------------------ 저장 · 불러오기

    static String encode(HolderLookup.Provider ra, ItemStack s) {
        try {
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, ra);
            return ItemStack.CODEC.encodeStart(ops, s).result().map(JsonElement::toString).orElse(null);
        } catch (RuntimeException e) {
            Mmorpg.LOG.warn("[mmorpg] market encode", e);
            return null;
        }
    }

    static ItemStack decode(HolderLookup.Provider ra, String json) {
        try {
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, ra);
            return ItemStack.CODEC.parse(ops, JsonParser.parseString(json)).result().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    /** 저장 값에서 다시 읽음 (서버가 켜질 때 · 시험) */
    public static void load(MinecraftServer s) {
        clear();
        server = s;
        State st = s.overworld().getAttached(TYPE);
        if (st == null) return;
        HolderLookup.Provider ra = s.registryAccess();
        List<Market.Listing<Ware>> list = new ArrayList<>();
        List<Stored> all = new ArrayList<>(st.listings());
        all.addAll(st.broken());
        for (Stored x : all) {
            UUID seller = uuid(x.seller());
            ItemStack stack = decode(ra, x.item());
            if (seller == null || stack == null || stack.isEmpty()) {
                BROKEN.add(x);
                continue;
            }
            list.add(new Market.Listing<>(x.id(), seller, x.name(), new Ware(stack, x.item()), x.price(), x.listedAt(), x.expiresAt()));
        }
        MARKET.load(list, st.next());
        for (Box b : st.boxes()) {
            UUID id = uuid(b.owner());
            if (id == null) continue;
            if (b.gold() > 0) PENDING.add(id, Economy.Kind.GOLD, b.gold());
            for (String j : b.items()) {
                ItemStack stack = decode(ra, j);
                if (stack == null || stack.isEmpty()) BOX_BROKEN.computeIfAbsent(id, k -> new ArrayList<>()).add(j);
                else BOX.computeIfAbsent(id, k -> new ArrayList<>()).add(new Ware(stack, j));
            }
            if (!b.notes().isEmpty()) NOTES.put(id, new ArrayList<>(b.notes()));
        }
        if (!BROKEN.isEmpty()) Mmorpg.LOG.warn("[mmorpg] market: {} listings could not be read (kept as they are)", BROKEN.size());
    }

    /** 바뀐 것을 저장 값으로 (바뀔 때마다 — 서버가 저장할 때 이 값을 씀) */
    static void save() {
        if (server == null) return;
        List<Stored> ls = new ArrayList<>();
        for (Market.Listing<Ware> l : MARKET.all()) {
            ls.add(new Stored(l.id(), l.seller().toString(), l.sellerName(), l.item().json(), l.price(), l.listedAt(), l.expiresAt()));
        }
        Set<UUID> owners = new LinkedHashSet<>(BOX.keySet());
        owners.addAll(BOX_BROKEN.keySet());
        owners.addAll(PENDING.view().keySet());
        owners.addAll(NOTES.keySet());
        List<Box> boxes = new ArrayList<>();
        for (UUID id : owners) {
            List<String> items = new ArrayList<>();
            for (Ware w : BOX.getOrDefault(id, List.of())) items.add(w.json());
            items.addAll(BOX_BROKEN.getOrDefault(id, List.of()));
            long gold = PENDING.get(id, Economy.Kind.GOLD);
            List<String> notes = NOTES.getOrDefault(id, List.of());
            if (items.isEmpty() && gold <= 0 && notes.isEmpty()) continue;
            boxes.add(new Box(id.toString(), gold, List.copyOf(items), List.copyOf(notes)));
        }
        server.overworld().setAttached(TYPE, new State(MARKET.nextId(), List.copyOf(ls), List.copyOf(boxes), List.copyOf(BROKEN)));
    }

    // ------------------------------------------------------------ 보기

    /** 지금 올라와 있는 것 (거른 것 · 정렬) */
    public static List<Market.Listing<Ware>> browse(ServerPlayer sp, Predicate<Market.Listing<Ware>> filter, Market.Sort sort) {
        ensure(sp);
        return MARKET.browse(filter, sort);
    }

    public static List<Market.Listing<Ware>> mine(ServerPlayer sp) {
        ensure(sp);
        return MARKET.browse(l -> l.seller().equals(sp.getUUID()), Market.Sort.NEW);
    }

    public static Market.Listing<Ware> find(ServerPlayer sp, long id) {
        ensure(sp);
        return MARKET.find(id);
    }

    /** 보관함 (읽기 전용) */
    public static List<Ware> box(ServerPlayer sp) {
        ensure(sp);
        return List.copyOf(BOX.getOrDefault(sp.getUUID(), List.of()));
    }

    /** 접속하지 않은 사람에게 줄 골드 (시험용) */
    public static long pending(UUID id) {
        return PENDING.get(id, Economy.Kind.GOLD);
    }

    /**
     * 판매자 (접속하지 않아도 됨) 이름으로 바로 올림 (운영 · 시험용 — 서버가 여는 상점 물건 등). 가방에서 빼지 않음. 거절이면 null
     */
    public static Market.Listing<Ware> seed(MinecraftServer s, UUID seller, String sellerName, ItemStack stack, long price) {
        ensure(s);
        String json = encode(s.registryAccess(), stack);
        if (json == null || refuse(stack) != null || json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_JSON_BYTES) return null;
        Market.Result<Market.Listing<Ware>> r = MARKET.list(seller, sellerName, new Ware(stack.copy(), json), price, now());
        if (!r.ok()) return null;
        save();
        return r.value();
    }

    /** 모두 (시험 · 운영) */
    public static List<Market.Listing<Ware>> all(MinecraftServer s) {
        ensure(s);
        return MARKET.all();
    }

    // ------------------------------------------------------------ 올릴 수 있는 것

    /** 올릴 수 없는 까닭 (올릴 수 있으면 null) */
    public static String refuse(ItemStack s) {
        if (s == null || s.isEmpty()) return "빈 칸입니다";
        if (s.getItem() instanceof CoinItem) return "금화 · 은화 (화폐) 는 올릴 수 없습니다";
        if (s.getItem() instanceof StatBookItem || s.getItem() instanceof NpcToolItem) return "개인 · 관리 도구는 올릴 수 없습니다";
        if (QuestRewards.isTrial(s)) return "시험용 무기는 올릴 수 없습니다";
        if (hasContents(s)) return "안에 든 것이 있는 상자 · 꾸러미는 올릴 수 없습니다 (비우고 다시)";
        return null;
    }

    /** 셜커 상자 · 꾸러미처럼 안에 아이템이 든 것 */
    static boolean hasContents(ItemStack s) {
        ItemContainerContents c = s.get(DataComponents.CONTAINER);
        if (c != null && c.nonEmptyItemCopyStream().findAny().isPresent()) return true;
        BundleContents b = s.get(DataComponents.BUNDLE_CONTENTS);
        return b != null && !b.isEmpty();
    }

    /** 아이템 이름 (개수가 2 이상이면 "×16") */
    public static String label(ItemStack s) {
        return s.getHoverName().getString() + (s.getCount() > 1 ? " §7×" + s.getCount() : "");
    }

    // ------------------------------------------------------------ 올리기 · 사기 · 내리기 · 보관함

    /** 결과: 성공 여부 + 글 (창 본문 · 채팅) */
    public record Outcome(boolean ok, String text) {
        static Outcome no(String text) {
            return new Outcome(false, "§c" + text);
        }
    }

    /**
     * 올리기: 가방 칸 slot (0 ~ 35) 의 아이템 count 개를 price 골드로. expect = 고를 때의 아이템 (그 사이 바뀌었으면 거절 — null 이면 보지 않음).
     * 수수료는 팔릴 때 (등록은 무료)
     */
    public static Outcome list(ServerPlayer sp, int slot, ItemStack expect, int count, long price) {
        ensure(sp);
        if (slot < 0 || slot >= Inv.SIZE) return Outcome.no("잘못된 칸입니다");
        ItemStack cur = Inv.get(sp, slot);
        if (cur.isEmpty() || (expect != null && !ItemStack.isSameItemSameComponents(cur, expect))) return Outcome.no("아이템이 옮겨졌습니다 — 다시 골라 주세요");
        if (count < 1 || count > cur.getCount()) return Outcome.no("개수가 맞지 않습니다 (가진 것 " + cur.getCount() + "개)");
        String why = refuse(cur);
        if (why != null) return Outcome.no(why);
        ItemStack sell = cur.copyWithCount(count);
        String json = encode(sp.level().registryAccess(), sell);
        if (json == null) return Outcome.no("이 아이템은 저장할 수 없어 올릴 수 없습니다");
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_JSON_BYTES) return Outcome.no("정보가 너무 많은 아이템이라 올릴 수 없습니다");
        Market.Result<Market.Listing<Ware>> r = MARKET.list(sp.getUUID(), sp.getName().getString(), new Ware(sell, json), price, now());
        if (!r.ok()) return Outcome.no(r.refusal().text);
        cur.shrink(count);
        if (cur.isEmpty()) Inv.set(sp, slot, ItemStack.EMPTY);
        sp.getInventory().setChanged();
        save();
        SkillFx.playFor(sp, "random.orb", 0.9, 0.6);
        SkillFx.playFor(sp, "item.book.page_turn", 1.2, 0.9);
        Vec3 h = sp.getEyePosition();
        SkillFx.sparks(sp.level(), h.add(sp.getLookAngle().scale(0.8)).add(0, -0.3, 0), GOLD, 8, 0.9);
        return new Outcome(true, "§a올렸습니다: §f" + label(sell) + " §7· §6" + Economy.fmt(price) + " 골드 §8(번호 #" + r.value().id() + " · "
                + Economy.timeLeft(Economy.marketMillis()) + " · 팔리면 수수료 " + Economy.fmt(Economy.fee(price)) + " 골드)");
    }

    /** 손에 든 아이템 (전부) 을 올림 ("/장터 등록 가격") */
    public static Outcome listHeld(ServerPlayer sp, long price) {
        int slot = sp.getInventory().getSelectedSlot();
        ItemStack held = Inv.get(sp, slot);
        if (held.isEmpty()) return Outcome.no("올릴 아이템을 손에 들어 주세요 (핫바)");
        return list(sp, slot, held.copy(), held.getCount(), price);
    }

    /** 사기: 골드 (지갑 → 모자라면 가방의 금화) 를 내고 물건을 가방에. 판매자는 수수료를 뺀 골드 (접속 중이면 바로 · 아니면 다음 접속에) */
    public static Outcome buy(ServerPlayer sp, long id) {
        ensure(sp);
        long now = now();
        Market.Listing<Ware> l = MARKET.find(id);
        if (l == null) return Outcome.no(Market.Refusal.GONE.text);
        if (l.seller().equals(sp.getUUID())) return Outcome.no(Market.Refusal.OWN.text);
        if (l.expiresAt() <= now) {
            expire(sp.level().getServer());
            return Outcome.no(Market.Refusal.EXPIRED.text);
        }
        long have = Wallet.total(sp, Economy.Kind.GOLD);
        if (have < l.price()) {
            mmorpg.fabric.shop.Shop.angry(sp);
            return Outcome.no("골드가 모자랍니다 (필요 " + Economy.fmt(l.price()) + " · 보유 " + Economy.fmt(have) + ")");
        }
        ItemStack item = l.item().stack().copy();
        if (!Inv.fitsAfter(sp, Wallet.takes(sp, Economy.Kind.GOLD, l.price()), List.of(item))) {
            Inv.bagFull(sp);
            return Outcome.no("가방에 빈 칸이 없습니다 (한 칸 비우고 다시)");
        }
        if (!Wallet.pay(sp, Economy.Kind.GOLD, l.price())) return Outcome.no("결제 오류");
        Market.Result<Market.Sale<Ware>> r = MARKET.buy(id, sp.getUUID(), now);
        if (!r.ok()) {
            Wallet.give(sp, Economy.Kind.GOLD, l.price()); // 돌려줌
            return Outcome.no(r.refusal().text);
        }
        String name = label(item); // 가방에 넣기 전에 (Inv.give 가 묶음을 비우면 이름이 "Air")
        Inv.give(sp, item);
        Market.Sale<Ware> sale = r.value();
        ServerPlayer seller = sp.level().getServer().getPlayerList().getPlayer(l.seller());
        String sellerText = "§6[장터] §f" + name + " §7이(가) 팔렸습니다 §6+" + Economy.fmt(sale.proceeds()) + " 골드 §8(가격 " + Economy.fmt(l.price()) + " · 수수료 "
                + Economy.fmt(sale.fee()) + " · 산 사람 " + sp.getName().getString() + ")";
        if (seller != null) {
            Wallet.give(seller, Economy.Kind.GOLD, sale.proceeds());
            Notify.say(seller, sellerText);
            SkillFx.playFor(seller, "armor.equip_gold", 1.4, 0.9);
            SkillFx.playFor(seller, "random.levelup", 1.8, 0.4);
        } else {
            PENDING.add(l.seller(), Economy.Kind.GOLD, sale.proceeds());
            note(l.seller(), sellerText);
        }
        sold++;
        save();
        buyFx(sp);
        return new Outcome(true, "§a샀습니다: §f" + name + " §7· §6-" + Economy.fmt(l.price()) + " 골드 §8(판매자 " + l.sellerName() + ")");
    }

    /** 산 사람: 금빛 고리 · 반짝임 · 소리 */
    static void buyFx(ServerPlayer sp) {
        Vec3 feet = sp.position();
        SkillFx.rune(sp.level(), feet, mmorpg.fabric.skill.Opt.of("size", 1.0, "life", 0.6, "color", GOLD, "alpha", 0.8, "progress", 1, "rate", 160, "grow", 0.35));
        SkillFx.sparks(sp.level(), feet.add(0, 1.0, 0), GOLD, 12, 1.6);
        SkillFx.playFor(sp, "random.orb", 1.3, 0.9);
        SkillFx.playFor(sp, "mob.villager.yes", 1.0, 0.6);
    }

    /** 내리기 (판매자 — admin 이면 누구 것이든 · 판매자에게 돌려줌). 물건은 가방으로 (자리가 없으면 보관함) */
    public static Outcome cancel(ServerPlayer sp, long id, boolean admin) {
        ensure(sp);
        Market.Result<Market.Listing<Ware>> r = MARKET.cancel(id, sp.getUUID(), admin);
        if (!r.ok()) return Outcome.no(r.refusal().text);
        Market.Listing<Ware> l = r.value();
        String name = label(l.item().stack());
        if (!l.seller().equals(sp.getUUID())) {
            ServerPlayer owner = sp.level().getServer().getPlayerList().getPlayer(l.seller());
            String msg = "§e[장터] §f" + name + " §7을(를) 운영자가 내렸습니다 §8— 보관함으로 (/장터 → 보관함)";
            boxAdd(l.seller(), l.item());
            if (owner != null) Notify.say(owner, msg);
            else note(l.seller(), msg);
            save();
            return new Outcome(true, "§a내렸습니다 (운영자): §f" + name + " §7→ " + l.sellerName() + "의 보관함");
        }
        boolean toBag = Inv.fitsAfter(sp, List.of(), List.of(l.item().stack()));
        if (toBag) Inv.give(sp, l.item().stack().copy());
        else boxAdd(sp.getUUID(), l.item());
        save();
        SkillFx.playFor(sp, "item.book.page_turn", 0.9, 0.8);
        return new Outcome(true, "§a내렸습니다: §f" + name + (toBag ? " §7→ 가방" : " §7→ 보관함 §8(가방이 가득)"));
    }

    /** 보관함에서 꺼내기 (index — -1 = 모두, 가방에 들어가는 것만) */
    public static Outcome claim(ServerPlayer sp, int index) {
        ensure(sp);
        List<Ware> box = BOX.get(sp.getUUID());
        if (box == null || box.isEmpty()) return Outcome.no("보관함이 비었습니다");
        List<Ware> pick = index < 0 ? new ArrayList<>(box) : index < box.size() ? List.of(box.get(index)) : List.of();
        if (pick.isEmpty()) return Outcome.no("그 물건이 없습니다");
        int got = 0;
        for (Ware w : pick) {
            if (!Inv.fitsAfter(sp, List.of(), List.of(w.stack()))) break;
            Inv.give(sp, w.stack().copy());
            box.remove(w);
            got++;
        }
        if (box.isEmpty()) BOX.remove(sp.getUUID());
        if (got == 0) {
            Inv.bagFull(sp);
            return Outcome.no("가방에 빈 칸이 없습니다");
        }
        save();
        SkillFx.playFor(sp, "random.pop", 1.0, 0.8);
        int left = box.size();
        return new Outcome(true, "§a보관함에서 " + got + "개를 꺼냈습니다" + (left > 0 ? " §7(남은 것 " + left + "개 — 가방이 가득)" : ""));
    }

    static void boxAdd(UUID id, Ware w) {
        BOX.computeIfAbsent(id, k -> new ArrayList<>()).add(w);
    }

    static void note(UUID id, String text) {
        List<String> l = NOTES.computeIfAbsent(id, k -> new ArrayList<>());
        l.add(text);
        while (l.size() > MAX_NOTES) l.remove(0);
    }

    // ------------------------------------------------------------ 기간 · 접속

    /** 기간이 끝난 것을 판매자의 보관함으로 (접속 중이면 알림 · 아니면 다음 접속에) */
    public static void expire(MinecraftServer s) {
        ensure(s);
        List<Market.Listing<Ware>> gone = MARKET.expire(now());
        if (gone.isEmpty()) return;
        for (Market.Listing<Ware> l : gone) {
            boxAdd(l.seller(), l.item());
            String msg = "§e[장터] §f" + label(l.item().stack()) + " §7판매 기간이 끝나 보관함으로 옮겼습니다 §8(/장터 → 보관함)";
            ServerPlayer p = s.getPlayerList().getPlayer(l.seller());
            if (p != null) Notify.say(p, msg);
            else note(l.seller(), msg);
        }
        save();
    }

    /** 접속: 그동안 팔린 대금을 지갑에 · 글 보여 주기 · 보관함 알림 */
    static void onJoin(ServerPlayer sp) {
        ensure(sp);
        UUID id = sp.getUUID();
        boolean changed = false;
        List<String> notes = NOTES.remove(id);
        if (notes != null) {
            changed = true;
            for (String n : notes) Notify.say(sp, n);
        }
        long gold = PENDING.get(id, Economy.Kind.GOLD);
        if (gold > 0 && PENDING.take(id, Economy.Kind.GOLD, gold)) {
            changed = true;
            Wallet.give(sp, Economy.Kind.GOLD, gold);
            Notify.say(sp, "§6[장터] §f접속하지 않은 동안의 판매 대금 §6+" + Economy.fmt(gold) + " 골드 §7를 지갑에 넣었습니다");
            Wallet.coinFx(sp, Economy.Kind.GOLD, gold, true);
        }
        int boxed = BOX.getOrDefault(id, List.of()).size();
        if (boxed > 0) Notify.say(sp, "§e[장터] §f보관함에 찾아갈 물건이 " + boxed + "개 있습니다 §7(/장터 → 보관함)");
        if (changed) save();
    }
}
