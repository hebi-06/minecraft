package mmorpg.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Molang 대조 시험 (JUnit 없이 main): molang_cases.json (tools/molang_cases.py — 애드온 파티클 식 186개를 따로 짠 파이썬 계산기로 푼 답
 * + 손으로 정한 우선순위 · 삼항 · 비교 사례) 를 자바 Molang 으로 풀어 1e-9 × max(1, |답|) 안인지 봄. 답이 없는 사례 (math.random) 는 읽기만.
 * 하나라도 다르면 종료 코드 1.
 */
public final class MolangTest {
    private MolangTest() {}

    public static void main(String[] argv) throws IOException {
        Map<String, Object> root = Json.parseObject(resource("/molang_cases.json"));
        List<?> cases = (List<?>) root.get("cases");
        int ok = 0;
        int parsedOnly = 0;
        List<String> bad = new ArrayList<>();
        for (Object raw : cases) {
            Map<?, ?> c = (Map<?, ?>) raw;
            String expr = (String) c.get("expr");
            Map<?, ?> vals = (Map<?, ?>) c.get("vars");
            Map<String, Integer> slots = new HashMap<>();
            double[] v = new double[vals.size()];
            int i = 0;
            for (Map.Entry<?, ?> e : vals.entrySet()) {
                slots.put((String) e.getKey(), i);
                v[i++] = ((Number) e.getValue()).doubleValue();
            }
            Molang.Expr compiled;
            try {
                compiled = Molang.parse(expr, name -> slots.getOrDefault(name, -1));
            } catch (RuntimeException ex) {
                bad.add("parse " + expr + "\n      " + ex.getMessage());
                continue;
            }
            Object expect = c.get("expect");
            if (expect == null) {
                double got = compiled.eval(v);
                if (Double.isNaN(got)) bad.add("NaN " + expr);
                else parsedOnly++;
                continue;
            }
            double want = ((Number) expect).doubleValue();
            double got = compiled.eval(v);
            if (Math.abs(got - want) <= 1e-9 * Math.max(1, Math.abs(want))) {
                ok++;
            } else {
                bad.add(expr + " " + vals + "\n      want " + want + " got " + got);
            }
        }
        System.out.println("MolangTest: " + ok + " ok, " + parsedOnly + " parse-only, " + bad.size() + " bad");
        for (int k = 0; k < Math.min(20, bad.size()); k++) System.out.println("  FAIL " + bad.get(k));
        if (!bad.isEmpty()) System.exit(1);
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = MolangTest.class.getResourceAsStream(name)) {
            if (in == null) throw new IOException("resource not found: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
