package mmorpg.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 베드락 Molang 식 (애드온 파티클이 쓰는 부분): 숫자 · 변수 (variable.x · v.x, 점이 든 이름 variable.color.r) ·
 * 사칙연산 · 비교 (참 1 / 거짓 0) · && || ! · 삼항 a ? b : c · 괄호 · math.함수.
 * 함수: abs floor ceil round trunc sqrt pow exp ln min max clamp mod lerp sin cos (도) random random_integer.
 * query (q.) · 임시 변수 (t.) · 문장 (;) 은 없음 (애드온 파티클에 없음 — 나오면 parse 가 예외).
 * 변수는 이름 → 칸 번호 (Vars) 로 미리 바꿔 두고, 값은 double[] 칸에서 읽음 (모르는 변수 = 0, 베드락과 같음).
 * 상수만 있는 부분은 미리 계산해 둠.
 */
public final class Molang {
    private Molang() {}

    /** 식 하나 */
    public interface Expr {
        double eval(double[] v);

        default boolean isConstant() {
            return false;
        }
    }

    /** 변수 이름 (variable. 을 뺀 것, 예: "color.r") → 칸 번호 (-1 = 모름 → 늘 0) */
    public interface Vars {
        int slot(String name);
    }

    private record Const(double value) implements Expr {
        @Override
        public double eval(double[] v) {
            return value;
        }

        @Override
        public boolean isConstant() {
            return true;
        }
    }

    public static Expr constant(double value) {
        return new Const(value);
    }

    /** JSON 값 (숫자 또는 식 문자열) → 식 */
    public static Expr of(Object value, Vars vars) {
        if (value == null) return constant(0);
        if (value instanceof Number n) return constant(n.doubleValue());
        if (value instanceof Boolean b) return constant(b ? 1 : 0);
        return parse(value.toString(), vars);
    }

    public static Expr parse(String src, Vars vars) {
        Parser p = new Parser(src, vars);
        Expr e = p.ternary();
        if (p.pos < p.toks.size()) throw new IllegalArgumentException("Molang: 남은 글자 '" + p.toks.get(p.pos) + "' in " + src);
        return e;
    }

    // ------------------------------------------------------------ 글자 나누기

    private static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (Character.isDigit(c) || (c == '.' && i + 1 < s.length() && Character.isDigit(s.charAt(i + 1)))) {
                int j = i;
                while (j < s.length() && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.')) j++;
                if (j < s.length() && (s.charAt(j) == 'e' || s.charAt(j) == 'E')) {
                    j++;
                    if (j < s.length() && (s.charAt(j) == '+' || s.charAt(j) == '-')) j++;
                    while (j < s.length() && Character.isDigit(s.charAt(j))) j++;
                }
                if (j < s.length() && (s.charAt(j) == 'f' || s.charAt(j) == 'F')) j++;
                out.add(s.substring(i, j));
                i = j;
            } else if (Character.isLetter(c) || c == '_') {
                int j = i;
                while (j < s.length() && (Character.isLetterOrDigit(s.charAt(j)) || s.charAt(j) == '_' || s.charAt(j) == '.')) j++;
                out.add(s.substring(i, j));
                i = j;
            } else {
                String two = i + 1 < s.length() ? s.substring(i, i + 2) : "";
                if (two.equals("&&") || two.equals("||") || two.equals("==") || two.equals("!=") || two.equals("<=") || two.equals(">=")) {
                    out.add(two);
                    i += 2;
                } else if ("+-*/()<>!?:,".indexOf(c) >= 0) {
                    out.add(String.valueOf(c));
                    i++;
                } else {
                    throw new IllegalArgumentException("Molang: 모르는 글자 '" + c + "' in " + s);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------ 읽기 (낮은 우선순위부터)

    private static final class Parser {
        final List<String> toks;
        final Vars vars;
        final String src;
        int pos;

        Parser(String src, Vars vars) {
            this.src = src;
            this.toks = tokenize(src);
            this.vars = vars;
        }

        String peek() {
            return pos < toks.size() ? toks.get(pos) : "";
        }

        boolean eat(String t) {
            if (peek().equals(t)) {
                pos++;
                return true;
            }
            return false;
        }

        void expect(String t) {
            if (!eat(t)) throw new IllegalArgumentException("Molang: '" + t + "' 가 필요 (" + peek() + ") in " + src);
        }

        Expr ternary() {
            Expr c = or();
            if (eat("?")) {
                Expr a = ternary();
                expect(":");
                Expr b = ternary();
                return fold(c, a, b, (x, y, z) -> x != 0 ? y : z, v -> c.eval(v) != 0 ? a.eval(v) : b.eval(v));
            }
            return c;
        }

        Expr or() {
            Expr l = and();
            while (eat("||")) {
                Expr a = l;
                Expr b = and();
                l = fold(a, b, (x, y) -> x != 0 || y != 0 ? 1 : 0, v -> a.eval(v) != 0 || b.eval(v) != 0 ? 1 : 0);
            }
            return l;
        }

        Expr and() {
            Expr l = equality();
            while (eat("&&")) {
                Expr a = l;
                Expr b = equality();
                l = fold(a, b, (x, y) -> x != 0 && y != 0 ? 1 : 0, v -> a.eval(v) != 0 && b.eval(v) != 0 ? 1 : 0);
            }
            return l;
        }

        Expr equality() {
            Expr l = relational();
            while (true) {
                Expr a = l;
                if (eat("==")) {
                    Expr b = relational();
                    l = fold(a, b, (x, y) -> x == y ? 1 : 0, v -> a.eval(v) == b.eval(v) ? 1 : 0);
                } else if (eat("!=")) {
                    Expr b = relational();
                    l = fold(a, b, (x, y) -> x != y ? 1 : 0, v -> a.eval(v) != b.eval(v) ? 1 : 0);
                } else {
                    return l;
                }
            }
        }

        Expr relational() {
            Expr l = additive();
            while (true) {
                Expr a = l;
                if (eat("<")) {
                    Expr b = additive();
                    l = fold(a, b, (x, y) -> x < y ? 1 : 0, v -> a.eval(v) < b.eval(v) ? 1 : 0);
                } else if (eat("<=")) {
                    Expr b = additive();
                    l = fold(a, b, (x, y) -> x <= y ? 1 : 0, v -> a.eval(v) <= b.eval(v) ? 1 : 0);
                } else if (eat(">")) {
                    Expr b = additive();
                    l = fold(a, b, (x, y) -> x > y ? 1 : 0, v -> a.eval(v) > b.eval(v) ? 1 : 0);
                } else if (eat(">=")) {
                    Expr b = additive();
                    l = fold(a, b, (x, y) -> x >= y ? 1 : 0, v -> a.eval(v) >= b.eval(v) ? 1 : 0);
                } else {
                    return l;
                }
            }
        }

        Expr additive() {
            Expr l = multiplicative();
            while (true) {
                Expr a = l;
                if (eat("+")) {
                    Expr b = multiplicative();
                    l = fold(a, b, Double::sum, v -> a.eval(v) + b.eval(v));
                } else if (eat("-")) {
                    Expr b = multiplicative();
                    l = fold(a, b, (x, y) -> x - y, v -> a.eval(v) - b.eval(v));
                } else {
                    return l;
                }
            }
        }

        Expr multiplicative() {
            Expr l = unary();
            while (true) {
                Expr a = l;
                if (eat("*")) {
                    Expr b = unary();
                    l = fold(a, b, (x, y) -> x * y, v -> a.eval(v) * b.eval(v));
                } else if (eat("/")) {
                    Expr b = unary();
                    l = fold(a, b, Molang::div, v -> div(a.eval(v), b.eval(v)));
                } else {
                    return l;
                }
            }
        }

        Expr unary() {
            if (eat("-")) {
                Expr a = unary();
                return fold(a, x -> -x, v -> -a.eval(v));
            }
            if (eat("+")) return unary();
            if (eat("!")) {
                Expr a = unary();
                return fold(a, x -> x == 0 ? 1 : 0, v -> a.eval(v) == 0 ? 1 : 0);
            }
            return primary();
        }

        Expr primary() {
            String t = peek();
            if (t.isEmpty()) throw new IllegalArgumentException("Molang: 식이 끝남 in " + src);
            if (eat("(")) {
                Expr e = ternary();
                expect(")");
                return e;
            }
            char c = t.charAt(0);
            if (Character.isDigit(c) || c == '.') {
                pos++;
                String n = t.endsWith("f") || t.endsWith("F") ? t.substring(0, t.length() - 1) : t;
                return constant(Double.parseDouble(n));
            }
            if (Character.isLetter(c) || c == '_') {
                pos++;
                String name = t.toLowerCase(Locale.ROOT);
                if (name.startsWith("math.")) return function(name.substring(5));
                if (name.startsWith("variable.") || name.startsWith("v.")) {
                    String var = name.substring(name.indexOf('.') + 1);
                    int slot = vars.slot(var);
                    if (slot < 0) return constant(0);
                    return v -> v[slot];
                }
                if (name.equals("true")) return constant(1);
                if (name.equals("false")) return constant(0);
                throw new IllegalArgumentException("Molang: 모르는 이름 '" + t + "' in " + src);
            }
            throw new IllegalArgumentException("Molang: 뜻밖의 '" + t + "' in " + src);
        }

        Expr function(String fn) {
            List<Expr> a = new ArrayList<>();
            if (eat("(")) {
                if (!eat(")")) {
                    do {
                        a.add(ternary());
                    } while (eat(","));
                    expect(")");
                }
            }
            switch (fn) {
                case "pi":
                    return constant(Math.PI);
                case "abs":
                    return f1(a, Math::abs);
                case "floor":
                    return f1(a, Math::floor);
                case "ceil":
                    return f1(a, Math::ceil);
                case "round":
                    return f1(a, x -> (double) Math.round(x));
                case "trunc":
                    return f1(a, x -> x < 0 ? Math.ceil(x) : Math.floor(x));
                case "sqrt":
                    return f1(a, Math::sqrt);
                case "exp":
                    return f1(a, Math::exp);
                case "ln":
                    return f1(a, Math::log);
                case "sin":
                    return f1(a, x -> Math.sin(Math.toRadians(x)));
                case "cos":
                    return f1(a, x -> Math.cos(Math.toRadians(x)));
                case "pow":
                    return f2(a, Math::pow);
                case "min":
                    return f2(a, Math::min);
                case "max":
                    return f2(a, Math::max);
                case "mod":
                    return f2(a, Molang::mod);
                case "clamp": {
                    need(a, 3, fn);
                    Expr x = a.get(0);
                    Expr lo = a.get(1);
                    Expr hi = a.get(2);
                    if (x.isConstant() && lo.isConstant() && hi.isConstant()) return constant(clamp(x.eval(null), lo.eval(null), hi.eval(null)));
                    return v -> clamp(x.eval(v), lo.eval(v), hi.eval(v));
                }
                case "lerp": {
                    need(a, 3, fn);
                    Expr x = a.get(0);
                    Expr y = a.get(1);
                    Expr t = a.get(2);
                    return v -> {
                        double p = x.eval(v);
                        return p + (y.eval(v) - p) * t.eval(v);
                    };
                }
                case "random": {
                    need(a, 2, fn);
                    Expr lo = a.get(0);
                    Expr hi = a.get(1);
                    return v -> {
                        double l = lo.eval(v);
                        return l + (hi.eval(v) - l) * ThreadLocalRandom.current().nextDouble();
                    };
                }
                case "random_integer": {
                    need(a, 2, fn);
                    Expr lo = a.get(0);
                    Expr hi = a.get(1);
                    return v -> {
                        long l = Math.round(lo.eval(v));
                        long h = Math.round(hi.eval(v));
                        return h <= l ? l : l + ThreadLocalRandom.current().nextLong(h - l + 1);
                    };
                }
                default:
                    throw new IllegalArgumentException("Molang: 모르는 함수 math." + fn + " in " + src);
            }
        }

        void need(List<Expr> a, int n, String fn) {
            if (a.size() != n) throw new IllegalArgumentException("Molang: math." + fn + " 는 값 " + n + "개 (" + a.size() + ") in " + src);
        }

        Expr f1(List<Expr> a, java.util.function.DoubleUnaryOperator f) {
            if (a.size() != 1) throw new IllegalArgumentException("Molang: 값 1개가 필요 in " + src);
            Expr x = a.get(0);
            return fold(x, f, v -> f.applyAsDouble(x.eval(v)));
        }

        Expr f2(List<Expr> a, java.util.function.DoubleBinaryOperator f) {
            if (a.size() != 2) throw new IllegalArgumentException("Molang: 값 2개가 필요 in " + src);
            Expr x = a.get(0);
            Expr y = a.get(1);
            return fold(x, y, f, v -> f.applyAsDouble(x.eval(v), y.eval(v)));
        }
    }

    // ------------------------------------------------------------ 상수 접기

    private interface D3 {
        double apply(double a, double b, double c);
    }

    private static Expr fold(Expr a, java.util.function.DoubleUnaryOperator f, Expr live) {
        return a.isConstant() ? constant(f.applyAsDouble(a.eval(null))) : live;
    }

    private static Expr fold(Expr a, Expr b, java.util.function.DoubleBinaryOperator f, Expr live) {
        return a.isConstant() && b.isConstant() ? constant(f.applyAsDouble(a.eval(null), b.eval(null))) : live;
    }

    private static Expr fold(Expr a, Expr b, Expr c, D3 f, Expr live) {
        if (a.isConstant()) return a.eval(null) != 0 ? b : c;
        return b.isConstant() && c.isConstant() && b.eval(null) == c.eval(null) ? b : live;
    }

    // ------------------------------------------------------------ 계산 규칙

    /** 0 으로 나누면 0 (베드락 Molang 처럼 NaN · 무한이 퍼지지 않게) */
    static double div(double a, double b) {
        return b == 0 ? 0 : a / b;
    }

    /** 나머지 (나뉘는 수의 부호 — C fmod), 0 으로 나누면 0 */
    static double mod(double a, double b) {
        return b == 0 ? 0 : a % b;
    }

    static double clamp(double x, double lo, double hi) {
        return x < lo ? lo : x > hi ? hi : x;
    }
}
