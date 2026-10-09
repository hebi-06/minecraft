package mmorpg.fabric;

/**
 * 베드락 글자 서식 (§) → 자바 에디션. 애드온의 이름 · 설명 글자를 그대로 쓰되, 자바에 없는 베드락 전용 색 코드를 가까운 자바 색으로 바꿈.
 * 자바의 §m (취소선) · §n (밑줄) 은 베드락에서 색 (레드스톤 · 구리) 이므로 색으로 바꿈 (애드온은 취소선 · 밑줄을 쓰지 않음).
 */
public final class BedrockText {
    private BedrockText() {}

    /** 베드락 전용 코드 → 자바 코드 (나머지 0-9 a-f k l o r 은 같음) */
    private static char map(char c) {
        return switch (c) {
            case 'g' -> 'e'; // minecoin gold → 노랑
            case 'h' -> 'f'; // quartz → 흰색
            case 'i' -> '7'; // iron → 회색
            case 'j' -> '8'; // netherite → 짙은 회색
            case 'm' -> '4'; // redstone → 짙은 빨강
            case 'n' -> '6'; // copper → 금색
            case 'p' -> '6'; // gold → 금색
            case 'q' -> '2'; // emerald → 초록
            case 's' -> 'b'; // diamond → 하늘색
            case 't' -> '1'; // lapis → 짙은 파랑
            case 'u' -> 'd'; // amethyst (고귀 등급) → 연보라
            case 'v' -> '6'; // resin (영웅 등급) → 금색
            default -> c;
        };
    }

    public static String toJava(String s) {
        if (s == null || s.indexOf('§') < 0) return s;
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append(c);
            if (c == '§' && i + 1 < s.length()) {
                b.append(map(Character.toLowerCase(s.charAt(i + 1))));
                i++;
            }
        }
        return b.toString();
    }

    /** 서식 코드를 모두 뺀 글자 (번역 파일 · 비교용) */
    public static String plain(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }
}
