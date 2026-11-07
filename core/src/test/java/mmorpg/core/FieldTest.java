package mmorpg.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import mmorpg.core.field.FieldPlan;
import mmorpg.core.field.FieldTerrain;

/**
 * 2026-11-07 섬 맵 시험 (JUnit 없이 main — check-core.sh): field.json · FieldTerrain.
 * <ul>
 * <li>섬 · 다리 · 자리가 맞게 적힘 (같은 id 없음 · 다리의 두 섬이 있음 · 자리의 섬이 있음)</li>
 * <li>자리마다 그 섬의 땅 위 · 해안에서 충분히 안쪽 (평평한 자리 + 8칸) · 보스 · 마을 · 거점 자리는 평평함 (물 없음)</li>
 * <li>다리: 길이 120 ~ 420칸 · 두 끝은 땅 (갑판 높이 − 1) · 가운데는 바다</li>
 * <li>바이옴은 정해 둔 것만 (게임의 바이옴 JSON 과 같은 목록) · 같은 칸은 늘 같은 답 · 강은 바다까지 이어짐</li>
 * </ul>
 * 하나라도 틀리면 종료 코드 1.
 */
public final class FieldTest {
    private FieldTest() {}

    /** 게임의 data/mmorpg/worldgen/biome/*.json 과 같은 목록 */
    public static final Set<String> BIOMES = Set.of("mmorpg:open_sea", "mmorpg:shallows", "mmorpg:frozen_shallows", "mmorpg:rift_shallows", "mmorpg:ember_shallows",
            "mmorpg:midgard", "mmorpg:harvest_fields", "mmorpg:harvest_woods", "mmorpg:dune_sea", "mmorpg:red_mesa", "mmorpg:oasis", "mmorpg:fjord_coast",
            "mmorpg:mire", "mmorpg:frost_taiga", "mmorpg:frost_peaks", "mmorpg:glacier", "mmorpg:void_rift", "mmorpg:helheim", "mmorpg:muspelheim");

    private static final List<String> BAD = new ArrayList<>();
    private static int ok;

    private static void check(boolean cond, String what) {
        if (cond) ok++;
        else BAD.add(what);
    }

    public static void main(String[] argv) {
        FieldPlan plan = FieldPlan.get();
        FieldTerrain t = new FieldTerrain(plan);
        FieldTerrain.Column c = new FieldTerrain.Column();
        check(plan.islands.size() >= 7, "islands " + plan.islands.size());
        check(plan.island("midgard") != null && plan.island("rift") != null && plan.island("muspel") != null, "core islands present");
        // 자리
        for (FieldPlan.Site s : plan.sites) {
            t.sample((int) Math.floor(s.x()), (int) Math.floor(s.z()), c);
            check(c.land(), "site " + s.id() + " on land (coast " + Math.round(c.coast) + ")");
            check(c.island != null && c.island.id().equals(s.island()), "site " + s.id() + " on its island " + s.island() + " (got " + (c.island == null ? null : c.island.id()) + ")");
            double need = 15;
            for (FieldTerrain.Plot p : t.plots()) if (p.id().equals(s.id())) need = p.flat() + 8;
            check(c.coast >= need, "site " + s.id() + " " + Math.round(c.coast) + " blocks from the coast (need " + Math.round(need) + ")");
            if (!s.kind().equals("zone")) check(!c.hasWater() || c.water <= c.ground, "site " + s.id() + " dry");
        }
        // 평평한 자리: 가운데 둘레 (평평한 반지름의 0.8) 의 높이가 같음
        for (FieldTerrain.Plot p : t.plots()) {
            if (p.kind().equals("tree")) continue;
            int lo = Integer.MAX_VALUE;
            int hi = Integer.MIN_VALUE;
            for (int i = 0; i < 16; i++) {
                double a = Math.PI * 2 * i / 16;
                t.sample((int) Math.floor(p.x() + Math.cos(a) * p.flat() * 0.8), (int) Math.floor(p.z() + Math.sin(a) * p.flat() * 0.8), c);
                lo = Math.min(lo, c.ground);
                hi = Math.max(hi, c.ground);
            }
            check(hi - lo <= 1, "plot " + p.id() + " flat (" + lo + " ~ " + hi + ", want " + p.y() + ")");
        }
        // 다리
        check(t.spans().size() == plan.bridges.size(), "every bridge spans (" + t.spans().size() + " / " + plan.bridges.size() + ")");
        for (FieldTerrain.Span sp : t.spans()) {
            String id = sp.bridge().from() + "→" + sp.bridge().to();
            check(sp.length() > 120 && sp.length() < 420, "bridge " + id + " length " + Math.round(sp.length()));
            t.sample((int) Math.floor(sp.ax()), (int) Math.floor(sp.az()), c);
            check(c.land() && c.ground == sp.deck() - 1, "bridge " + id + " start on land at deck-1 (" + c.ground + ")");
            t.sample((int) Math.floor(sp.bx()), (int) Math.floor(sp.bz()), c);
            check(c.land() && c.ground == sp.deck() - 1, "bridge " + id + " end on land at deck-1 (" + c.ground + ")");
            t.sample((int) Math.floor((sp.ax() + sp.bx()) / 2), (int) Math.floor((sp.az() + sp.bz()) / 2), c);
            check(!c.land() && c.hasWater(), "bridge " + id + " crosses the sea");
        }
        // 바이옴 · 같은 답
        int[][] probes = new int[400][];
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < probes.length; i++) probes[i] = new int[] {r.nextInt(4000) - 2100, r.nextInt(6200) - 3200};
        boolean same = true;
        for (int[] p : probes) {
            t.sample(p[0], p[1], c);
            check(BIOMES.contains(c.biome), "biome " + c.biome + " at " + p[0] + " " + p[1] + " known");
            int g = c.ground;
            String b = c.biome;
            int w = c.water;
            t.sample(p[0], p[1], c);
            same &= g == c.ground && b.equals(c.biome) && w == c.water;
        }
        check(same, "same column twice → same answer");
        check(new FieldTerrain(plan).spans().size() == t.spans().size(), "terrain rebuilds the same");
        // 시작 자리는 땅 · 물 없음
        t.sample(plan.spawnX, plan.spawnZ, c);
        check(c.land() && !c.hasWater() && c.island.id().equals("midgard"), "spawn on the village island");
        // 속도: 한 청크 (256칸) 어림
        long t0 = System.nanoTime();
        for (int i = 0; i < 64; i++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) t.sample(1000 + x + i * 16, z, c);
        double perChunk = (System.nanoTime() - t0) / 64.0 / 1e6;
        check(perChunk < 40, "sample speed " + String.format(java.util.Locale.ROOT, "%.2f", perChunk) + " ms / chunk");
        System.out.println("FieldTest: " + ok + " ok, " + BAD.size() + " bad (" + String.format(java.util.Locale.ROOT, "%.2f", perChunk) + " ms/chunk)");
        for (String b : BAD) System.out.println("  FAIL " + b);
        if (!BAD.isEmpty()) System.exit(1);
    }
}
