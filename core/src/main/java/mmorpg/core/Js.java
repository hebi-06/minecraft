package mmorpg.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * JavaScript semantics that the ported formulas depend on and that Java does differently:
 * number → string ({@code `${x}`}, {@code String(x)}, property keys), {@code Math.round},
 * {@code toFixed}, truthiness, UTF-8 length.
 */
public final class Js {
    private Js() {}

    /**
     * {@code Number::toString(x)} (ECMAScript, radix 10): the shortest decimal that reads back as
     * {@code x}, written as "12.5", "100", "0.000001", "1e+21", "5e-324", "-3", "NaN", "Infinity".
     * Also the property key of a number ({@code LEVEL_DAMAGE[5]} reads key "5").
     */
    public static String str(double x) {
        if (Double.isNaN(x)) return "NaN";
        if (x == 0) return "0"; // +0 and -0
        if (Double.isInfinite(x)) return x > 0 ? "Infinity" : "-Infinity";
        if (x < 0) return "-" + str(-x);
        BigDecimal s = shortest(x);
        String digits = s.unscaledValue().toString();
        int k = digits.length();
        int n = k - s.scale(); // x = digits × 10^(n − k)
        if (k <= n && n <= 21) return digits + "0".repeat(n - k);
        if (0 < n && n <= 21) return digits.substring(0, n) + "." + digits.substring(n);
        if (-6 < n && n <= 0) return "0." + "0".repeat(-n) + digits;
        String exp = (n - 1 >= 0 ? "+" : "-") + Math.abs(n - 1);
        return k == 1 ? digits + "e" + exp : digits.charAt(0) + "." + digits.substring(1) + "e" + exp;
    }

    /**
     * Fewest significant digits k whose decimal reads back as x; among k-digit candidates the one
     * closest to x, ties to an even last digit (ECMAScript Number::toString step 5).
     */
    private static BigDecimal shortest(double x) {
        BigDecimal exact = new BigDecimal(x);
        for (int k = 1; ; k++) {
            BigDecimal lo = exact.round(new MathContext(k, RoundingMode.FLOOR));
            BigDecimal hi = exact.round(new MathContext(k, RoundingMode.CEILING));
            boolean okLo = Double.parseDouble(lo.toString()) == x;
            boolean okHi = Double.parseDouble(hi.toString()) == x;
            if (okLo && okHi) {
                int c = exact.subtract(lo).compareTo(hi.subtract(exact));
                BigDecimal pick = c < 0 ? lo : c > 0 ? hi : significand(lo, k).testBit(0) ? hi : lo;
                return pick.stripTrailingZeros();
            }
            if (okLo) return lo.stripTrailingZeros();
            if (okHi) return hi.stripTrailingZeros();
        }
    }

    /** The k-digit integer s with v = s × 10^(n − k). */
    private static BigInteger significand(BigDecimal v, int k) {
        int n = v.precision() - v.scale();
        return v.movePointRight(k - n).toBigIntegerExact();
    }

    /**
     * Template-literal conversion ({@code `${v}`}) of a JSON value: numbers as {@link #str(double)},
     * strings as is, booleans, arrays joined with ",", objects "[object Object]". A missing value
     * ({@code null} here) prints "undefined" as an absent property does in JS.
     */
    public static String str(Object v) {
        if (v == null) return "undefined";
        if (v instanceof String s) return s;
        if (v instanceof Number n) return str(n.doubleValue());
        if (v instanceof Boolean b) return b.toString();
        if (v instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                if (list.get(i) != null) sb.append(str(list.get(i)));
            }
            return sb.toString();
        }
        if (v instanceof Map) return "[object Object]";
        return String.valueOf(v);
    }

    /**
     * {@code Math.round}: nearest integer, halves toward +∞ ({@code Math.round(-2.5) = -2}),
     * no long overflow ({@code Math.round(1e20) = 1e20}), sign of zero kept.
     */
    public static double round(double x) {
        if (!Double.isFinite(x) || Math.abs(x) >= 0x1p52) return x;
        double r = Math.floor(x);
        if (x - r >= 0.5) r += 1.0;
        return r == 0 ? Math.copySign(0.0, x) : r;
    }

    /** {@code Number.prototype.toFixed(digits)}: exact value of x, halves away from zero. */
    public static String toFixed(double x, int digits) {
        if (Double.isNaN(x)) return "NaN";
        if (Math.abs(x) >= 1e21) return str(x);
        String sign = x < 0 ? "-" : "";
        return sign + new BigDecimal(Math.abs(x)).setScale(digits, RoundingMode.HALF_UP).toPlainString();
    }

    /** UTF-8 byte length (code points; a lone surrogate counts 3 bytes as in the add-on's helper). */
    public static int utf8Length(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); ) {
            int c = s.codePointAt(i);
            n += c < 0x80 ? 1 : c < 0x800 ? 2 : c < 0x10000 ? 3 : 4;
            i += Character.charCount(c);
        }
        return n;
    }

    /** JS truthiness of a JSON value (null/false/0/NaN/"" are falsy). */
    public static boolean truthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) {
            double d = n.doubleValue();
            return d != 0 && !Double.isNaN(d);
        }
        if (v instanceof String s) return !s.isEmpty();
        return true;
    }
}
