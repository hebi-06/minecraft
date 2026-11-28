package mmorpg.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import mmorpg.core.field.Blueprint;
import mmorpg.core.field.FieldBuild;
import mmorpg.core.field.FieldPlan;
import mmorpg.core.field.FieldTerrain;

/**
 * 2026-11-07 섬 맵 시험 (JUnit 없이 main — check-core.sh): field.json · FieldTerrain.
 * <ul>
 * <li>섬 · 다리 · 자리가 맞게 적힘 (같은 id 없음 · 다리의 두 섬이 있음 · 자리의 섬이 있음)</li>
 * <li>자리마다 그 섬의 땅 위 · 해안에서 충분히 안쪽 (평평한 자리 + 8칸) · 보스 · 마을 · 거점 자리는 평평함 (물 없음)</li>
 * <li>다리: 길이 120 ~ 420칸 · 두 끝은 땅 (갑판 높이 − 1) · 가운데는 바다 — 2026-11-28 다리 없음 (사용자 "다리 없앨거임 텔포로 이동시킬거여서"):
 * field.json 에 다리가 없음 · 다른 무리의 섬 사이 바다는 안개 되돌림 (fog.back) 보다 멂 (헤엄 · 배로 못 감) · 무리마다 이동 지점 돌 ·
 * 붙은 섬 (곶) 은 북쪽 섬과 땅으로 이어짐</li>
 * <li>바이옴은 정해 둔 것만 (게임의 바이옴 JSON 과 같은 목록) · 같은 칸은 늘 같은 답 · 강은 바다까지 이어짐</li>
 * </ul>
 * 하나라도 틀리면 종료 코드 1.
 */
public final class FieldTest {
    private FieldTest() {}

    /** 게임의 data/mmorpg/worldgen/biome/*.json 과 같은 목록 */
    public static final Set<String> BIOMES = Set.of("mmorpg:open_sea", "mmorpg:shallows", "mmorpg:frozen_shallows", "mmorpg:rift_shallows", "mmorpg:ember_shallows",
            "mmorpg:midgard", "mmorpg:midgard_town", "mmorpg:midgard_grove", "mmorpg:harvest_fields", "mmorpg:harvest_woods", "mmorpg:dune_sea", "mmorpg:red_mesa", "mmorpg:oasis", "mmorpg:fjord_coast",
            "mmorpg:mire", "mmorpg:frost_taiga", "mmorpg:frost_peaks", "mmorpg:glacier", "mmorpg:void_rift", "mmorpg:helheim", "mmorpg:muspelheim",
            "mmorpg:harvest_clearing", "mmorpg:dune_clearing", "mmorpg:oasis_clearing", "mmorpg:fjord_clearing", "mmorpg:mire_clearing", "mmorpg:frost_clearing",
            "mmorpg:glacier_clearing", "mmorpg:muspel_clearing", "mmorpg:snowcrown", "mmorpg:snowcrown_clearing", "mmorpg:rift_cape", "mmorpg:rift_cape_clearing",
            "mmorpg:shieldwall", "mmorpg:shieldwall_clearing", "mmorpg:black_fissures", "mmorpg:inae_fields", "mmorpg:inae_grove", "mmorpg:inae_peak",
            "mmorpg:inae_clearing", "mmorpg:sage_plateau", "mmorpg:weightless", "mmorpg:sage_clearing");

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
        // 섬마다 둘레 (반지름 1.6 배 안 — 땅 · 물가 · 바다) 를 고루
        for (int i = 0; i < probes.length; i++) {
            FieldPlan.Island is = plan.islands.get(i % plan.islands.size());
            double a = r.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(r.nextDouble()) * is.radius() * 1.6;
            probes[i] = new int[] {(int) Math.round(is.x() + Math.cos(a) * d), (int) Math.round(is.z() + Math.sin(a) * d)};
        }
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
        // 2026-11-28 다리 없음 · 섬 사이는 이동 지점 돌로만
        check(plan.bridges.isEmpty() && t.spans().isEmpty(), "no bridges (" + plan.bridges.size() + ")");
        for (int i = 0; i < plan.islands.size(); i++) {
            for (int j = i + 1; j < plan.islands.size(); j++) {
                FieldPlan.Island a = plan.islands.get(i);
                FieldPlan.Island b = plan.islands.get(j);
                if (a.group().equals(b.group())) continue;
                double len = Math.hypot(b.x() - a.x(), b.z() - a.z());
                double far = 0;
                for (double s = 0; s <= len; s += 8) far = Math.max(far, t.offshore(a.x() + (b.x() - a.x()) * s / len, a.z() + (b.z() - a.z()) * s / len));
                check(far >= plan.fogBack + 60, "sea between " + a.id() + " and " + b.id() + " reaches " + Math.round(far) + " from the coast (fog back " + Math.round(plan.fogBack) + ")");
            }
        }
        java.util.Set<String> withStone = new java.util.HashSet<>();
        for (Blueprint.Mark m : FieldBuild.build(t).marks()) {
            if (!m.id().startsWith("waystone")) continue;
            t.sample((int) Math.floor(m.x()), (int) Math.floor(m.z()), c);
            check(c.land() && c.island != null, "waystone " + m.id() + " on land");
            if (c.island != null) withStone.add(c.island.group());
        }
        for (FieldPlan.Island is : plan.islands) if (is.with() == null) check(withStone.contains(is.id()), "island group " + is.id() + " has a waystone");
        // 붙은 섬 (가운데 사이 < 두 반지름 합) 은 땅으로 이어짐: 두 섬의 거점 · 사냥터 가운데 첫 자리 사이 곧은 길이 모두 땅
        for (FieldPlan.Island is : plan.islands) {
            FieldPlan.Island to = is.with() == null ? null : plan.island(is.with());
            if (to == null || Math.hypot(is.x() - to.x(), is.z() - to.z()) >= is.radius() + to.radius()) continue;
            FieldPlan.Site sa = null;
            FieldPlan.Site sb = null;
            for (FieldPlan.Site s : plan.sites) {
                if (s.kind().equals("camp") && s.island().equals(is.id()) && sa == null) sa = s;
                if (s.kind().equals("camp") && s.island().equals(to.id()) && sb == null) sb = s;
            }
            if (sa == null || sb == null) continue;
            double len = Math.hypot(sb.x() - sa.x(), sb.z() - sa.z());
            int sea = 0;
            for (double s = 0; s <= len; s += 4) {
                t.sample((int) Math.floor(sa.x() + (sb.x() - sa.x()) * s / len), (int) Math.floor(sa.z() + (sb.z() - sa.z()) * s / len), c);
                if (!c.land()) sea++;
            }
            check(sea == 0, "joined " + is.id() + " → " + to.id() + ": land from " + sa.id() + " to " + sb.id() + " (" + sea + " sea columns)");
        }
        // 시작 자리는 땅 · 물 없음
        t.sample(plan.spawnX, plan.spawnZ, c);
        check(c.land() && !c.hasWater() && c.island.id().equals("midgard"), "spawn on the village island");
        // 속도: 한 청크 (256칸) 어림
        long t0 = System.nanoTime();
        int sx = (int) plan.island("harvest").x() - 500;
        for (int i = 0; i < 64; i++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) t.sample(sx + x + i * 16, z, c);
        double perChunk = (System.nanoTime() - t0) / 64.0 / 1e6;
        check(perChunk < 40, "sample speed " + String.format(java.util.Locale.ROOT, "%.2f", perChunk) + " ms / chunk");
        System.out.println("FieldTest: " + ok + " ok, " + BAD.size() + " bad (" + String.format(java.util.Locale.ROOT, "%.2f", perChunk) + " ms/chunk)");
        for (String b : BAD) System.out.println("  FAIL " + b);
        if (!BAD.isEmpty()) System.exit(1);
    }
}
