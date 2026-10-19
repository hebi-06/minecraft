package mmorpg.core;

import java.util.List;
import java.util.Locale;

/**
 * 0.21.0 화폐 (자바 전용 — 사용자 요청 "화폐를 에메랄드에서 골드랑 실버로"): 골드 = 유저 장터 · 치장, 실버 = NPC 와의 교환 (상점 · 갑옷 상점 ·
 * 대장장이 · 룬술사 · 스탯 트리 초기화 · 의뢰 보상 — 예전 에메랄드 값을 그대로 1:1). 둘 다 지갑의 숫자 (화면 왼쪽 아래), 금화 · 은화 아이템은
 * 우클릭하면 지갑으로, "/골드 수" · "/실버 수" 로 꺼냄. 골드는 보스 · 퀘스트 보상으로만 생김 (사용자 선택). 수치는 rules_more.json ECONOMY.
 */
public final class Economy {
    private Economy() {}

    /** 화폐 둘 */
    public enum Kind {
        GOLD("gold"),
        SILVER("silver");

        public final String id;

        Kind(String id) {
            this.id = id;
        }

        private Obj def() {
            return cfg().obj(id);
        }

        /** 이름 ("골드" · "실버") */
        public String label() {
            return def().str("name");
        }

        /** 글자 색 (§6 · §f) */
        public String color() {
            return def().str("color");
        }

        /** 동전 아이템 ID (rpg:gold_coin · rpg:silver_coin) */
        public String item() {
            return def().str("item");
        }

        /** "gold" · "골드" · "silver" · "실버" → 화폐 (모르면 null) */
        public static Kind of(String s) {
            if (s == null) return null;
            for (Kind k : values()) if (k.id.equalsIgnoreCase(s) || k.label().equals(s)) return k;
            return null;
        }
    }

    public static Obj cfg() {
        return Rules.get().obj("ECONOMY");
    }

    /** 지갑 한 화폐의 최대 (넘치지 않게) */
    public static long walletMax() {
        return (long) cfg().num("walletMax");
    }

    /** 1,234 (쉼표) */
    public static String fmt(long v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    /** "§61,234 골드" */
    public static String amount(Kind k, long v) {
        return k.color() + fmt(v) + " " + k.label();
    }

    // ------------------------------------------------------------ 유저 장터

    /** 판매 수수료 비율 (0.05 = 5%) */
    public static double feeRate() {
        return cfg().num("marketFee");
    }

    /** 수수료 = 가격 × 비율 (내림 — 20골드 밑은 수수료 0) */
    public static long fee(long price) {
        return price <= 0 ? 0 : (long) Math.floor(price * feeRate() + 1e-9);
    }

    /** 판매자가 받는 골드 = 가격 − 수수료 */
    public static long proceeds(long price) {
        return price - fee(price);
    }

    /** 등록이 유지되는 시간 (밀리초 — 지나면 판매자에게 돌아감) */
    public static long marketMillis() {
        return Math.round(cfg().num("marketHours") * 3_600_000d);
    }

    /** 한 사람이 동시에 올릴 수 있는 물건 수 */
    public static int marketMax() {
        return (int) cfg().num("marketMax");
    }

    public static long minPrice() {
        return (long) cfg().num("marketMinPrice");
    }

    public static long maxPrice() {
        return (long) cfg().num("marketMaxPrice");
    }

    /** 남은 시간 글자 ("2일 3시간" · "5시간 10분" · "12분" · "곧 끝남") */
    public static String timeLeft(long millis) {
        if (millis <= 60_000) return "곧 끝남";
        long m = millis / 60_000;
        long d = m / (60 * 24);
        long h = (m / 60) % 24;
        long mm = m % 60;
        if (d > 0) return d + "일" + (h > 0 ? " " + h + "시간" : "");
        if (h > 0) return h + "시간" + (mm > 0 ? " " + mm + "분" : "");
        return mm + "분";
    }

    // ------------------------------------------------------------ 골드가 생기는 곳 (보스 · 퀘스트)

    /** 보스 전리품 골드 [min, max] (없으면 [0, 0]) */
    public static int[] bossGold(String boss) {
        List<Double> r = cfg().obj("bossGold").numbersOrNull(boss);
        if (r == null || r.isEmpty()) return new int[] {0, 0};
        return new int[] {(int) Math.round(r.get(0)), (int) Math.round(r.get(r.size() - 1))};
    }

    /** 반복 의뢰 골드 = 실버 보상 ÷ repeatGoldPer (내림 — 큰 의뢰만 1 이상) */
    public static int repeatGold(int silver) {
        double per = cfg().num("repeatGoldPer");
        return per > 0 ? (int) Math.floor(silver / per) : 0;
    }

    /** 메인 퀘스트 단계 골드 = 실버 보상 ÷ mainGoldPer (내림) — 보스 단계는 bossStepGold 를 더함 */
    public static int mainGold(int silver, boolean bossStep) {
        double per = cfg().num("mainGoldPer");
        int g = per > 0 ? (int) Math.floor(silver / per) : 0;
        return g + (bossStep ? (int) cfg().num("bossStepGold") : 0);
    }
}
