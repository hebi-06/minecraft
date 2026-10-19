package mmorpg.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 0.21.0 유저 장터 (자바 전용 — 사용자 요청 "유저 장터", 골드로만 사고팖, 판매 수수료 5% · 등록 무료 · 3일 · 한 사람 10개):
 * 물건 (자바 판: ItemStack) 의 등록 · 사기 · 내리기 · 기간 끝남. 골드의 이동 (사는 사람 → 판매자) 은 부르는 쪽이 지갑으로 —
 * 여기는 규칙만 (판매자 본인은 못 삼 · 가격 범위 · 개수 제한 · 기간). 마인크래프트와 무관 (core 시험).
 *
 * @param <T> 물건
 */
public final class Market<T> {
    /** 등록 하나: 번호 · 판매자 · 판매자 이름 (그때의) · 물건 · 가격 (골드) · 올린 때 · 끝나는 때 (밀리초) */
    public record Listing<T>(long id, UUID seller, String sellerName, T item, long price, long listedAt, long expiresAt) {}

    /** 팔림: 그 등록 · 수수료 · 판매자가 받는 골드 */
    public record Sale<T>(Listing<T> listing, long fee, long proceeds) {}

    /** 거절 이유 */
    public enum Refusal {
        PRICE("가격은 " + Economy.fmt(Economy.minPrice()) + " ~ " + Economy.fmt(Economy.maxPrice()) + " 골드"),
        LIMIT("한 사람이 올릴 수 있는 물건은 " + Economy.marketMax() + "개까지"),
        GONE("이미 팔렸거나 내려간 물건"),
        OWN("내가 올린 물건은 살 수 없음 (내 물건에서 내리기)"),
        NOT_OWNER("내가 올린 물건이 아님"),
        EXPIRED("기간이 끝난 물건");

        public final String text;

        Refusal(String text) {
            this.text = text;
        }
    }

    /** 결과: 성공 (값) 또는 거절 (이유) */
    public record Result<V>(V value, Refusal refusal) {
        public boolean ok() {
            return refusal == null;
        }

        static <V> Result<V> of(V v) {
            return new Result<>(v, null);
        }

        static <V> Result<V> no(Refusal r) {
            return new Result<>(null, r);
        }
    }

    /** 정렬: 새로 올린 것 · 싼 것 · 비싼 것 */
    public enum Sort {
        NEW, CHEAP, DEAR
    }

    private final List<Listing<T>> listings = new ArrayList<>();
    private long nextId = 1;

    /** 지금 올라와 있는 것 (올린 순서) */
    public List<Listing<T>> all() {
        return List.copyOf(listings);
    }

    public long nextId() {
        return nextId;
    }

    /** 불러오기 (저장된 등록 · 다음 번호) */
    public void load(List<Listing<T>> saved, long next) {
        listings.clear();
        listings.addAll(saved);
        long max = 0;
        for (Listing<T> l : saved) max = Math.max(max, l.id());
        nextId = Math.max(next, max + 1);
    }

    /** 이 사람이 올린 수 */
    public int countOf(UUID seller) {
        int n = 0;
        for (Listing<T> l : listings) if (l.seller().equals(seller)) n++;
        return n;
    }

    public Listing<T> find(long id) {
        for (Listing<T> l : listings) if (l.id() == id) return l;
        return null;
    }

    /** 올리기 (가격 범위 · 개수 제한). 기간 = now + {@link Economy#marketMillis()} */
    public Result<Listing<T>> list(UUID seller, String name, T item, long price, long now) {
        if (price < Economy.minPrice() || price > Economy.maxPrice()) return Result.no(Refusal.PRICE);
        if (countOf(seller) >= Economy.marketMax()) return Result.no(Refusal.LIMIT);
        Listing<T> l = new Listing<>(nextId++, seller, name, item, price, now, now + Economy.marketMillis());
        listings.add(l);
        return Result.of(l);
    }

    /** 사기 (있는지 · 기간 · 본인 아님) — 성공하면 장터에서 빠지고 수수료 · 판매 대금이 정해짐. 골드는 부르는 쪽이 먼저 확인 */
    public Result<Sale<T>> buy(long id, UUID buyer, long now) {
        Listing<T> l = find(id);
        if (l == null) return Result.no(Refusal.GONE);
        if (l.expiresAt() <= now) return Result.no(Refusal.EXPIRED);
        if (l.seller().equals(buyer)) return Result.no(Refusal.OWN);
        listings.remove(l);
        long fee = Economy.fee(l.price());
        return Result.of(new Sale<>(l, fee, l.price() - fee));
    }

    /** 내리기 (판매자만 — admin 이면 누구 것이든) */
    public Result<Listing<T>> cancel(long id, UUID who, boolean admin) {
        Listing<T> l = find(id);
        if (l == null) return Result.no(Refusal.GONE);
        if (!admin && !l.seller().equals(who)) return Result.no(Refusal.NOT_OWNER);
        listings.remove(l);
        return Result.of(l);
    }

    /** 기간이 끝난 것을 빼서 돌려줌 (판매자에게 돌려줄 물건) */
    public List<Listing<T>> expire(long now) {
        List<Listing<T>> out = new ArrayList<>();
        for (Iterator<Listing<T>> it = listings.iterator(); it.hasNext(); ) {
            Listing<T> l = it.next();
            if (l.expiresAt() <= now) {
                out.add(l);
                it.remove();
            }
        }
        return out;
    }

    /** 둘러보기: 거른 것 · 정렬 (같은 값이면 새로 올린 것부터) */
    public List<Listing<T>> browse(Predicate<Listing<T>> filter, Sort sort) {
        List<Listing<T>> out = new ArrayList<>();
        for (Listing<T> l : listings) if (filter == null || filter.test(l)) out.add(l);
        Comparator<Listing<T>> newest = Comparator.comparingLong((Listing<T> l) -> l.listedAt()).thenComparingLong(Listing::id).reversed();
        Comparator<Listing<T>> c = switch (sort) {
            case NEW -> newest;
            case CHEAP -> Comparator.comparingLong((Listing<T> l) -> l.price()).thenComparing(newest);
            case DEAR -> Comparator.comparingLong((Listing<T> l) -> l.price()).reversed().thenComparing(newest);
        };
        out.sort(c);
        return out;
    }
}
