package mmorpg.core;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import mmorpg.core.field.FieldBuild;
import mmorpg.core.field.FieldPlan;
import mmorpg.core.field.FieldTerrain;

/**
 * 섬 맵 짓는 것 위에서 보기 (2026-11-07 — 시험 도구): 네모 (x0, z0) ~ (x1, z1) 의 칸마다 지형 + 조각의 가장 높은 블록을 색으로.
 * 쓰는 법: java … mmorpg.core.TownPreview 출력.png x0 z0 x1 z1 [배율]
 */
public final class TownPreview {
    private TownPreview() {}

    static final Map<String, Integer> COLORS = new HashMap<>();

    static int color(String block) {
        String b = block.replaceAll("\\[.*", "").replace("minecraft:", "");
        Integer c = COLORS.get(b);
        if (c != null) return c;
        int h = b.hashCode();
        int v;
        if (b.contains("water")) v = 0x3d7fd0;
        else if (b.contains("lava") || b.contains("magma")) v = 0xe0601a;
        else if (b.contains("dark_oak") || b.contains("deepslate")) v = 0x4a3a2a;
        else if (b.contains("spruce")) v = 0x7a5a36;
        else if (b.contains("birch")) v = 0xd8cc90;
        else if (b.contains("oak")) v = 0xa8884e;
        else if (b.contains("stone_brick") || b.contains("cobble") || b.contains("andesite") || b.contains("stone")) v = 0x8c8c8c;
        else if (b.contains("wool")) v = b.startsWith("red") ? 0xb02020 : b.startsWith("blue") ? 0x3040b0 : b.startsWith("green") ? 0x3a8a2a : b.startsWith("yellow") ? 0xe0c020 : 0xeeeeee;
        else if (b.contains("path") || b.contains("dirt") || b.contains("gravel")) v = 0xa08a5a;
        else if (b.contains("lantern") || b.contains("torch") || b.contains("end_rod") || b.contains("sea_lantern")) v = 0xffe080;
        else if (b.contains("grass")) v = 0x6aa040;
        else if (b.contains("sand")) v = 0xe0d090;
        else v = 0x808080 + (h & 0x3f3f3f);
        COLORS.put(b, v);
        return v;
    }

    public static void main(String[] argv) throws IOException {
        String out = argv[0];
        int x0 = Integer.parseInt(argv[1]);
        int z0 = Integer.parseInt(argv[2]);
        int x1 = Integer.parseInt(argv[3]);
        int z1 = Integer.parseInt(argv[4]);
        int k = argv.length > 5 ? Integer.parseInt(argv[5]) : 3;
        FieldTerrain t = new FieldTerrain(FieldPlan.get());
        FieldBuild.Built built = FieldBuild.build(t);
        int w = x1 - x0 + 1;
        int h = z1 - z0 + 1;
        BufferedImage img = new BufferedImage(w * k, h * k, BufferedImage.TYPE_INT_RGB);
        FieldTerrain.Column c = new FieldTerrain.Column();
        int[] topY = new int[w * h];
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                t.sample(x, z, c);
                int[] best = {c.hasWater() ? Math.max(c.ground, c.water) : c.ground};
                String[] blk = {c.hasWater() && c.water > c.ground ? "minecraft:water" : "minecraft:" + c.top.name().toLowerCase()};
                Map<Integer, String> col = new HashMap<>();
                for (FieldBuild.Piece p : built.pieces()) {
                    if (x < p.minX() || x > p.maxX() || z < p.minZ() || z > p.maxZ()) continue;
                    p.column(x, z, c, (y, b) -> col.put(y, b));
                }
                for (Map.Entry<Integer, String> e : col.entrySet()) {
                    if (e.getValue().equals("minecraft:air")) {
                        if (e.getKey() <= best[0]) {
                            best[0] = Math.min(best[0], e.getKey() - 1);
                        }
                        continue;
                    }
                    if (e.getKey() >= best[0]) {
                        best[0] = e.getKey();
                        blk[0] = e.getValue();
                    }
                }
                int i = (z - z0) * w + (x - x0);
                topY[i] = best[0];
                int rgb = color(blk[0]);
                for (int a = 0; a < k; a++) for (int b2 = 0; b2 < k; b2++) img.setRGB((x - x0) * k + a, (z - z0) * k + b2, rgb);
            }
        }
        // 높이 그늘
        for (int z = 1; z < h; z++) {
            for (int x = 1; x < w; x++) {
                int i = z * w + x;
                int d = topY[i] - topY[i - 1] + topY[i] - topY[i - w];
                if (d == 0) continue;
                double f = Math.max(0.6, Math.min(1.4, 1 + d * 0.08));
                for (int a = 0; a < k; a++) {
                    for (int b2 = 0; b2 < k; b2++) {
                        int rgb = img.getRGB(x * k + a, z * k + b2);
                        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f));
                        int g = Math.min(255, (int) (((rgb >> 8) & 255) * f));
                        int bl = Math.min(255, (int) ((rgb & 255) * f));
                        img.setRGB(x * k + a, z * k + b2, (r << 16) | (g << 8) | bl);
                    }
                }
            }
        }
        javax.imageio.ImageIO.write(img, "png", new File(out));
        System.out.println("town preview " + w + "x" + h + " → " + out + " · marks " + built.marks().size());
        for (var m : built.marks()) System.out.println("  " + m);
    }
}
