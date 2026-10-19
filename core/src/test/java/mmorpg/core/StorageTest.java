package mmorpg.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 0.22.0 엔더 창고 설정 시험 (JUnit 없이 main — check-core.sh): 상자 5개 · 기본 1개 · 54칸 (9 × 6), 2번 상자 = 실버 · 3번부터 골드
 * (사용자 요청 그대로), 값 (300 실버 · 30 · 60 · 100 골드 — 뒤 상자일수록 비쌈), 기본 상자 · 범위 밖은 값 없음, 다음에 살 상자 (차례로),
 * 열린 수 범위, 모든 살 상자에 값이 있음. 하나라도 틀리면 종료 코드 1.
 */
public final class StorageTest {
    private StorageTest() {}

    private static final List<String> BAD = new ArrayList<>();
    private static int ok;

    private static void check(boolean cond, String what) {
        if (cond) ok++;
        else BAD.add(what);
    }

    public static void main(String[] args) {
        // 모양
        check(Storage.chests() == 5, "5 chests");
        check(Storage.free() == 1, "1 free chest");
        check(Storage.SIZE == 54 && Storage.ROWS == 6 && Storage.ROWS * 9 == Storage.SIZE, "large chest 9 x 6");
        check(Storage.migrateEnderChest(), "vanilla ender chest moves into chest 1");

        // 값: 2번은 실버, 3번부터 골드 (사용자 요청)
        check(Storage.price(1) == null && Storage.price(0) == null && Storage.price(6) == null && Storage.price(-1) == null, "no price for free / out of range");
        Storage.Price p2 = Storage.price(2);
        check(p2 != null && p2.kind() == Economy.Kind.SILVER && p2.amount() == 300, "chest 2 = 300 silver " + p2);
        for (int c = 3; c <= Storage.chests(); c++) {
            Storage.Price p = Storage.price(c);
            check(p != null && p.kind() == Economy.Kind.GOLD, "chest " + c + " = gold " + p);
        }
        check(Storage.price(3).amount() == 30 && Storage.price(4).amount() == 60 && Storage.price(5).amount() == 100, "gold 30 / 60 / 100");
        for (int c = 4; c <= Storage.chests(); c++) check(Storage.price(c).amount() > Storage.price(c - 1).amount(), "chest " + c + " costs more than " + (c - 1));
        check(p2.text().equals("§f300 실버") && Storage.price(5).text().equals("§6100 골드"), "price text " + p2.text());
        for (int c = Storage.free() + 1; c <= Storage.chests(); c++) check(Storage.price(c) != null, "every chest to buy has a price: " + c);

        // 차례로 사기
        check(Storage.next(1) == 2 && Storage.next(2) == 3 && Storage.next(4) == 5 && Storage.next(5) == -1, "next chest in order");
        check(Storage.next(0) == 2 && Storage.next(99) == -1, "next clamps");
        check(Storage.clampUnlocked(0) == 1 && Storage.clampUnlocked(3) == 3 && Storage.clampUnlocked(9) == 5, "unlocked range");
        check(Storage.label(2).equals("2번 상자"), "label");

        // 합계 (문서 · 밸런스용): 실버 300 + 골드 190
        long gold = 0;
        long silver = 0;
        for (int c = 2; c <= Storage.chests(); c++) {
            Storage.Price p = Storage.price(c);
            if (p.kind() == Economy.Kind.GOLD) gold += p.amount();
            else silver += p.amount();
        }
        check(silver == 300 && gold == 190, "totals silver " + silver + " gold " + gold);

        System.out.println("StorageTest: " + ok + " ok, " + BAD.size() + " failed");
        for (String b : BAD) System.out.println("  FAIL " + b);
        if (!BAD.isEmpty()) System.exit(1);
    }
}
