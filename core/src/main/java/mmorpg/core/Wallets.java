package mmorpg.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 0.21.0 사람마다 골드 · 실버 숫자 (마인크래프트와 무관 — 자바 판의 지갑은 플레이어 데이터에 붙여 저장하고, 이것은 장터의 "접속하지 않은
 * 판매자에게 줄 대금" 처럼 서버 쪽에 모아 두는 데 씀). 값은 0 ~ {@link Economy#walletMax()}.
 */
public final class Wallets {
    /** 사람 → {골드, 실버} */
    private final Map<UUID, long[]> map = new LinkedHashMap<>();

    public long get(UUID id, Economy.Kind k) {
        long[] w = map.get(id);
        return w == null ? 0 : w[k.ordinal()];
    }

    /** n 만큼 더함 (0 이하는 무시 · 최대에서 멈춤) — 실제로 더한 양 */
    public long add(UUID id, Economy.Kind k, long n) {
        if (n <= 0) return 0;
        long[] w = map.computeIfAbsent(id, x -> new long[2]);
        long max = Economy.walletMax();
        long before = w[k.ordinal()];
        w[k.ordinal()] = before > max - n ? max : before + n;
        return w[k.ordinal()] - before;
    }

    /** n 만큼 꺼냄 (모자라면 아무것도 하지 않고 false) */
    public boolean take(UUID id, Economy.Kind k, long n) {
        if (n < 0) return false;
        if (n == 0) return true;
        long[] w = map.get(id);
        if (w == null || w[k.ordinal()] < n) return false;
        w[k.ordinal()] -= n;
        return true;
    }

    /** 바로 정함 (관리자 명령) */
    public void set(UUID id, Economy.Kind k, long n) {
        long[] w = map.computeIfAbsent(id, x -> new long[2]);
        w[k.ordinal()] = Math.max(0, Math.min(Economy.walletMax(), n));
    }

    /** 모두 지움 (서버가 꺼질 때) */
    public void clear() {
        map.clear();
    }

    /** 저장용 (읽기 전용 보기) */
    public Map<UUID, long[]> view() {
        return Collections.unmodifiableMap(map);
    }

    /** 불러오기 (저장된 값 — 음수 · 넘침은 잘라냄) */
    public void load(UUID id, long gold, long silver) {
        long max = Economy.walletMax();
        map.put(id, new long[] {Math.max(0, Math.min(max, gold)), Math.max(0, Math.min(max, silver))});
    }
}
