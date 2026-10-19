package mmorpg.core;

import java.util.List;

/**
 * 0.22.0 엔더 창고 (자바 전용 — 사용자 요청 "엔더 상자를 플레이어 전용 창고로 만들 거고 큰 상자 UI 를 쓰게, 최대 5개의 상자, 1개는 기본,
 * 2단계 상자는 실버, 그다음부터 골드로 사게"): 상자마다 큰 상자 54칸 (9 × 6), 상자는 chests 개까지, 처음 free 개는 기본으로 열림,
 * 그 뒤 상자는 앞에서부터 차례로 삼 (prices — [화폐, 값]). 수치는 rules_more.json STORAGE.
 */
public final class Storage {
    private Storage() {}

    /** 상자 하나의 칸 수 (큰 상자 9 × 6) */
    public static final int SIZE = 54;
    /** 상자 하나의 줄 수 */
    public static final int ROWS = 6;

    public static Obj cfg() {
        return Rules.get().obj("STORAGE");
    }

    /** 상자 수 (최대 — 1 ~ 9) */
    public static int chests() {
        return Math.max(1, Math.min(9, (int) cfg().num("chests")));
    }

    /** 처음부터 열린 상자 수 (1 ~ chests) */
    public static int free() {
        return Math.max(1, Math.min(chests(), (int) cfg().num("free")));
    }

    /** 바닐라 엔더 상자 27칸의 아이템을 창고로 옮기는지 */
    public static boolean migrateEnderChest() {
        return cfg().truthy("migrateEnderChest");
    }

    /** 상자를 여는 값 */
    public record Price(Economy.Kind kind, long amount) {
        /** "§f300 실버" · "§630 골드" */
        public String text() {
            return Economy.amount(kind, amount);
        }
    }

    /** 상자 chest (1부터) 를 여는 값 — 기본 상자 · 범위 밖 · 값이 없으면 null */
    public static Price price(int chest) {
        if (chest <= free() || chest > chests()) return null;
        List<Object> list = cfg().list("prices");
        int i = chest - free() - 1;
        if (i >= list.size()) return null;
        if (!(list.get(i) instanceof List<?> pair) || pair.size() < 2 || !(pair.get(1) instanceof Number n)) {
            throw new IllegalStateException("STORAGE.prices[" + i + "] must be [\"silver\" | \"gold\", amount]");
        }
        Economy.Kind kind = Economy.Kind.of(String.valueOf(pair.get(0)));
        if (kind == null) throw new IllegalStateException("STORAGE.prices[" + i + "] unknown currency " + pair.get(0));
        return new Price(kind, Math.max(0, n.longValue()));
    }

    /** 열린 상자 수를 범위 (free ~ chests) 로 */
    public static int clampUnlocked(int unlocked) {
        return Math.max(free(), Math.min(chests(), unlocked));
    }

    /** 다음에 살 상자 (1부터 — 모두 열렸으면 -1) */
    public static int next(int unlocked) {
        int u = clampUnlocked(unlocked);
        return u >= chests() ? -1 : u + 1;
    }

    /** 상자 이름 ("1번 상자") */
    public static String label(int chest) {
        return chest + "번 상자";
    }
}
