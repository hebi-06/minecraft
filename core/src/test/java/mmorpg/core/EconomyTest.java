package mmorpg.core;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 0.21.0 화폐 · 유저 장터 시험 (JUnit 없이 main — check-core.sh): 설정 (골드 · 실버 이름 · 동전 · 수수료 5% · 72시간 · 10개 · 가격 범위),
 * 수수료 (내림 — 19골드 0 · 20골드 1 · 1,000골드 50), 지갑 (더하기 · 꺼내기 · 모자라면 그대로 · 최대 · 음수), 장터 (올리기 · 가격 · 개수 제한 ·
 * 본인 못 삼 · 사면 빠짐 · 두 번 못 삼 · 내리기는 판매자만 · 기간 끝 · 정렬 · 불러오기 뒤 번호), 골드 보상 (보스 · 반복 의뢰 · 메인 퀘스트),
 * 남은 시간 글자. 하나라도 틀리면 종료 코드 1.
 */
public final class EconomyTest {
    private EconomyTest() {}

    private static final List<String> BAD = new ArrayList<>();
    private static int ok;

    private static void check(boolean cond, String what) {
        if (cond) ok++;
        else BAD.add(what);
    }

    public static void main(String[] args) {
        // 설정
        check(Economy.Kind.GOLD.label().equals("골드") && Economy.Kind.SILVER.label().equals("실버"), "names");
        check(Economy.Kind.GOLD.item().equals("rpg:gold_coin") && Economy.Kind.SILVER.item().equals("rpg:silver_coin"), "coin items");
        check(Economy.Kind.of("골드") == Economy.Kind.GOLD && Economy.Kind.of("silver") == Economy.Kind.SILVER && Economy.Kind.of("emerald") == null, "kind of");
        check(Math.abs(Economy.feeRate() - 0.05) < 1e-12, "fee 5%");
        check(Economy.marketMillis() == 72L * 3600_000L, "72 hours");
        check(Economy.marketMax() == 10, "10 listings");
        check(Economy.minPrice() == 1 && Economy.maxPrice() == 1_000_000, "price range");
        check(Economy.cfg().truthy("migrateEmeralds") && Economy.cfg().truthy("emeraldExchange") && Economy.cfg().truthy("dropReturn"), "switches on");
        check(Rules.get().obj("CONFIG").str("CURRENCY_NAME").equals("실버") && Rules.get().obj("CONFIG").str("CURRENCY_ITEM").equals("rpg:silver_coin"),
                "NPC currency = silver");

        // 수수료 (내림)
        check(Economy.fee(1) == 0 && Economy.fee(19) == 0 && Economy.fee(20) == 1 && Economy.fee(39) == 1 && Economy.fee(1000) == 50, "fee floor");
        check(Economy.proceeds(1000) == 950 && Economy.proceeds(19) == 19 && Economy.proceeds(0) == 0, "proceeds");
        check(Economy.fmt(1234567).equals("1,234,567") && Economy.amount(Economy.Kind.GOLD, 1500).equals("§61,500 골드"), "format");

        // 지갑
        Wallets w = new Wallets();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        check(w.get(a, Economy.Kind.GOLD) == 0 && w.get(a, Economy.Kind.SILVER) == 0, "empty wallet");
        check(w.add(a, Economy.Kind.SILVER, 120) == 120 && w.get(a, Economy.Kind.SILVER) == 120 && w.get(a, Economy.Kind.GOLD) == 0, "add silver");
        check(!w.take(a, Economy.Kind.SILVER, 121) && w.get(a, Economy.Kind.SILVER) == 120, "take too much: unchanged");
        check(w.take(a, Economy.Kind.SILVER, 20) && w.get(a, Economy.Kind.SILVER) == 100, "take");
        check(!w.take(b, Economy.Kind.GOLD, 1) && w.take(b, Economy.Kind.GOLD, 0), "take from empty");
        check(w.add(a, Economy.Kind.GOLD, -5) == 0 && w.get(a, Economy.Kind.GOLD) == 0, "negative add ignored");
        w.set(a, Economy.Kind.GOLD, Economy.walletMax() - 3);
        check(w.add(a, Economy.Kind.GOLD, 10) == 3 && w.get(a, Economy.Kind.GOLD) == Economy.walletMax(), "max");
        w.load(b, -4, 7);
        check(w.get(b, Economy.Kind.GOLD) == 0 && w.get(b, Economy.Kind.SILVER) == 7, "load clamps");
        Wallets w2 = new Wallets();
        w2.add(a, Economy.Kind.GOLD, 5);
        w2.clear();
        check(w2.view().isEmpty() && w2.get(a, Economy.Kind.GOLD) == 0, "clear");

        // 장터
        Market<String> m = new Market<>();
        long t0 = 1_000_000L;
        var r1 = m.list(a, "A", "검", 1000, t0);
        check(r1.ok() && r1.value().id() == 1 && r1.value().expiresAt() == t0 + Economy.marketMillis(), "list");
        check(m.list(a, "A", "x", 0, t0).refusal() == Market.Refusal.PRICE && m.list(a, "A", "x", 1_000_001, t0).refusal() == Market.Refusal.PRICE,
                "price range refused");
        for (int i = 1; i < 10; i++) check(m.list(a, "A", "물건" + i, 10 + i, t0 + i).ok(), "list " + i);
        check(m.countOf(a) == 10 && m.list(a, "A", "11번째", 5, t0).refusal() == Market.Refusal.LIMIT, "limit 10");
        check(m.buy(1, a, t0 + 5).refusal() == Market.Refusal.OWN, "own refused");
        var s = m.buy(1, b, t0 + 5);
        check(s.ok() && s.value().fee() == 50 && s.value().proceeds() == 950 && s.value().listing().item().equals("검"), "buy: fee 50 / 950");
        check(m.find(1) == null && m.countOf(a) == 9 && m.buy(1, b, t0 + 6).refusal() == Market.Refusal.GONE, "bought once");
        long id2 = m.all().get(0).id();
        check(m.cancel(id2, b, false).refusal() == Market.Refusal.NOT_OWNER, "cancel: not owner");
        check(m.cancel(id2, a, false).ok() && m.countOf(a) == 8, "cancel by seller");
        check(m.cancel(m.all().get(0).id(), b, true).ok() && m.countOf(a) == 7, "cancel by admin");
        // 정렬: 싼 것 · 비싼 것 · 새 것
        List<Market.Listing<String>> cheap = m.browse(null, Market.Sort.CHEAP);
        List<Market.Listing<String>> dear = m.browse(null, Market.Sort.DEAR);
        List<Market.Listing<String>> fresh = m.browse(null, Market.Sort.NEW);
        check(cheap.get(0).price() <= cheap.get(cheap.size() - 1).price() && dear.get(0).price() >= dear.get(dear.size() - 1).price(), "sort by price");
        check(fresh.get(0).listedAt() >= fresh.get(fresh.size() - 1).listedAt(), "sort newest");
        check(m.browse(l -> l.price() > 15, Market.Sort.CHEAP).stream().allMatch(l -> l.price() > 15), "filter");
        // 기간 끝
        long end = t0 + Economy.marketMillis();
        check(m.expire(end - 1).isEmpty(), "not yet expired");
        var lb = m.list(b, "B", "늦게", 3, end);
        check(lb.ok(), "list later");
        check(m.buy(m.all().get(0).id(), b, end + 100).refusal() == Market.Refusal.EXPIRED, "buy expired refused");
        List<Market.Listing<String>> gone = m.expire(end + 100);
        check(gone.size() == 7 && m.all().size() == 1 && m.all().get(0).seller().equals(b), "expire: 7 back, later one stays");
        // 불러오기: 다음 번호는 저장된 번호보다 큼
        Market<String> m2 = new Market<>();
        m2.load(m.all(), 1);
        check(m2.nextId() > lb.value().id() && m2.list(a, "A", "새", 9, end).value().id() > lb.value().id(), "load keeps ids unique");

        // 골드 보상
        int[] sk = Economy.bossGold("scarecrow_king");
        int[] su = Economy.bossGold("surtr");
        check(sk[0] == 1 && sk[1] == 2 && su[0] == 6 && su[1] == 8 && Economy.bossGold("nope")[1] == 0, "boss gold");
        check(Economy.repeatGold(39) == 0 && Economy.repeatGold(40) == 1 && Economy.repeatGold(70) == 1, "repeat gold");
        check(Economy.mainGold(50, false) == 1 && Economy.mainGold(64, true) == 3 && Economy.mainGold(128, true) == 4 && Economy.mainGold(3, false) == 0,
                "main quest gold");
        // 모든 보스에 골드가 있음
        for (String boss : Rules.get().obj("BOSSES").keys()) check(Economy.bossGold(boss)[1] > 0, "boss gold for " + boss);

        // 남은 시간
        check(Economy.timeLeft(30_000).equals("곧 끝남") && Economy.timeLeft(12 * 60_000).equals("12분"), "time left minutes");
        check(Economy.timeLeft((5 * 60 + 10) * 60_000L).equals("5시간 10분") && Economy.timeLeft((2 * 24 * 60 + 3 * 60) * 60_000L).equals("2일 3시간"),
                "time left hours / days");

        System.out.println("EconomyTest: " + ok + " ok, " + BAD.size() + " bad");
        for (String s2 : BAD) System.out.println("  BAD " + s2);
        if (!BAD.isEmpty()) System.exit(1);
    }
}
