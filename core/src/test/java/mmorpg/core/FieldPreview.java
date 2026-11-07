package mmorpg.core;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mmorpg.core.field.FieldPlan;
import mmorpg.core.field.FieldTerrain;

/**
 * 섬 맵 배치도 (2026-11-07 — 시험 도구, JUnit 없이 main): field.json + FieldTerrain 으로 위에서 본 그림 (PNG) 과 이름표 자리 (JSON).
 * 쓰는 법: java -cp … mmorpg.core.FieldPreview 출력.png [블록/픽셀 = 4]
 * 이름표 (한글) 는 그림 도구가 JSON 을 읽어 덧그림.
 */
public final class FieldPreview {
    private FieldPreview() {}

    private static int rgb(int r, int g, int b) {
        return (Math.max(0, Math.min(255, r)) << 16) | (Math.max(0, Math.min(255, g)) << 8) | Math.max(0, Math.min(255, b));
    }

    private static int shade(int c, double k) {
        return rgb((int) (((c >> 16) & 255) * k), (int) (((c >> 8) & 255) * k), (int) ((c & 255) * k));
    }

    private static int mix(int a, int b, double t) {
        return rgb((int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t),
                (int) ((a & 255) * (1 - t) + (b & 255) * t));
    }

    private static int surfaceColor(FieldTerrain.Surface s, int y) {
        return switch (s) {
            case GRASS -> mix(0x5f9b3a, 0x7fae4c, Math.max(0, Math.min(1, (y - 64) / 30.0)));
            case SAND -> 0xe3d59c;
            case SANDSTONE -> 0xd8c690;
            case RED_SAND -> 0xc56b34;
            case TERRACOTTA -> {
                int[] bands = {0xa3593a, 0xc98a45, 0xb5734f, 0xd4b48c, 0x8c4a33, 0xc2683c};
                yield bands[Math.floorMod(y / 3, bands.length)];
            }
            case GRAVEL -> 0x8f8a86;
            case STONE -> 0x878787;
            case SNOW -> 0xf3f7fb;
            case PACKED_ICE -> 0x9fc4ee;
            case BLUE_ICE -> 0x74a8f2;
            case PODZOL -> 0x6b5a2c;
            case MUD -> 0x4b4038;
            case COARSE -> 0x7a5a3e;
            case CLAY -> 0x9aa0ad;
            case VOID -> 0x5a5266;
            case VOID_DARK -> 0x3e3848;
            case SCULK -> 0x0f3b45;
            case OBSIDIAN -> 0x1d1428;
            case SOUL -> 0x5a4636;
            case BONE -> 0xd8d2b8;
            case BASALT -> 0x4a4a50;
            case BLACKSTONE -> 0x2e2a30;
            case MAGMA -> 0xd2561b;
            case PATH -> 0xb39463;
            case DIRT -> 0x86603e;
            case DEEPSLATE -> 0x48484f;
        };
    }

    /** 풀 색을 바이옴마다 (게임에서 덮일 눈 · 나무 숲을 어림) */
    private static int biomeGrass(String biome, int col, int x, int z) {
        double r = mmorpg.core.field.FieldNoise.hash(x >> 2, z >> 2, 5);
        return switch (biome) {
            case "mmorpg:midgard" -> mix(col, 0x86c24f, 0.4);
            case "mmorpg:harvest_fields" -> r < 0.03 ? 0x3e6e2a : mix(col, 0x9cc455, 0.35);
            case "mmorpg:harvest_woods" -> r < 0.45 ? 0x2f5f22 : 0x4f8a33;
            case "mmorpg:oasis" -> r < 0.12 ? 0x2f6d2a : 0x6aa843;
            case "mmorpg:fjord_coast" -> r < 0.22 ? 0x2c5530 : 0x5e9a45;
            case "mmorpg:mire" -> r < 0.25 ? 0x3c4f26 : 0x55703a;
            case "mmorpg:frost_taiga" -> r < 0.3 ? 0x24452e : 0xe8eef2;
            case "mmorpg:frost_peaks", "mmorpg:glacier" -> 0xeef3f6;
            default -> col;
        };
    }

    public static void main(String[] argv) throws IOException {
        String out = argv.length > 0 ? argv[0] : "field_preview.png";
        int k = argv.length > 1 ? Integer.parseInt(argv[1]) : 4;
        FieldPlan plan = FieldPlan.get();
        FieldTerrain terrain = new FieldTerrain(plan);
        double minX = 1e9;
        double maxX = -1e9;
        double minZ = 1e9;
        double maxZ = -1e9;
        for (FieldPlan.Island is : plan.islands) {
            minX = Math.min(minX, is.x() - is.radius() * 1.35 - 260);
            maxX = Math.max(maxX, is.x() + is.radius() * 1.35 + 260);
            minZ = Math.min(minZ, is.z() - is.radius() * 1.35);
            maxZ = Math.max(maxZ, is.z() + is.radius() * 1.35);
        }
        int x0 = (int) Math.floor(minX / k) * k;
        int z0 = (int) Math.floor(minZ / k) * k;
        int w = (int) Math.ceil((maxX - x0) / k);
        int h = (int) Math.ceil((maxZ - z0) / k);
        int[] height = new int[w * h];
        int[] color = new int[w * h];
        boolean[] wet = new boolean[w * h];
        FieldTerrain.Column c = new FieldTerrain.Column();
        long t0 = System.nanoTime();
        for (int pz = 0; pz < h; pz++) {
            for (int px = 0; px < w; px++) {
                int bx = x0 + px * k + k / 2;
                int bz = z0 + pz * k + k / 2;
                terrain.sample(bx, bz, c);
                int top = c.ground;
                int col = surfaceColor(c.top, c.ground);
                if (c.top == FieldTerrain.Surface.GRASS || c.top == FieldTerrain.Surface.PODZOL) col = biomeGrass(c.biome, col, bx, bz);
                if (c.floatTop > c.floatBottom) {
                    top = c.floatTop;
                    col = mix(surfaceColor(FieldTerrain.Surface.VOID, top), 0x9a7fd0, 0.35);
                }
                if (c.hasWater() && c.water > c.ground && top <= c.water) {
                    int depth = c.water - c.ground;
                    if (c.lava) col = mix(0xff8a1e, 0xd2401a, Math.min(1, depth / 6.0));
                    else {
                        boolean cold = c.biome.contains("frozen") || c.biome.contains("glacier") || c.biome.contains("frost");
                        int shallow = cold ? 0x7fb2d8 : c.biome.contains("rift") ? 0x3c3a78 : c.biome.contains("ember") ? 0x5b6a8a : 0x3d8fd0;
                        int deep = c.biome.contains("rift") ? 0x161433 : 0x15386e;
                        col = mix(shallow, deep, Math.min(1, depth / 26.0));
                    }
                    wet[pz * w + px] = true;
                    top = c.water;
                }
                height[pz * w + px] = top;
                color[pz * w + px] = col;
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int pz = 0; pz < h; pz++) {
            for (int px = 0; px < w; px++) {
                int i = pz * w + px;
                int col = color[i];
                if (!wet[i]) {
                    int hl = height[pz * w + Math.max(0, px - 1)];
                    int hu = height[Math.max(0, pz - 1) * w + px];
                    double slope = ((height[i] - hl) + (height[i] - hu)) / (double) k;
                    double light = Math.max(-0.45, Math.min(0.45, slope * 0.35));
                    col = shade(col, 1 + light);
                    int add = (int) (light * 70);
                    col = rgb(((col >> 16) & 255) + add, ((col >> 8) & 255) + add, (col & 255) + add);
                }
                img.setRGB(px, pz, col);
            }
        }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // 길
        g.setStroke(new BasicStroke(Math.max(1f, 5f / k), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0xd9c08a));
        for (FieldTerrain.Segment s : terrain.roads()) {
            g.drawLine((int) ((s.ax() - x0) / k), (int) ((s.az() - z0) / k), (int) ((s.bx() - x0) / k), (int) ((s.bz() - z0) / k));
        }
        // 다리
        g.setStroke(new BasicStroke(Math.max(2f, 7f / k), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        for (FieldTerrain.Span sp : terrain.spans()) {
            g.setColor(new Color(0x3b2a1c));
            g.drawLine((int) ((sp.ax() - x0) / k), (int) ((sp.az() - z0) / k), (int) ((sp.bx() - x0) / k), (int) ((sp.bz() - z0) / k));
        }
        g.setStroke(new BasicStroke(Math.max(1f, 3f / k)));
        for (FieldTerrain.Span sp : terrain.spans()) {
            g.setColor(new Color(0xc8a06a));
            g.drawLine((int) ((sp.ax() - x0) / k), (int) ((sp.az() - z0) / k), (int) ((sp.bx() - x0) / k), (int) ((sp.bz() - z0) / k));
        }
        g.dispose();
        writePng(img, out);
        // 이름표 자리
        List<String> labels = new ArrayList<>();
        for (FieldPlan.Island is : plan.islands) {
            labels.add(String.format(Locale.ROOT, "{\"kind\":\"island\",\"text\":\"%s\",\"sub\":\"%s\",\"x\":%d,\"y\":%d}", is.name(),
                    is.region() == null && is.terrain().equals("village") ? "Lv1~" : "Lv" + is.levelMin() + (is.levelMax() != is.levelMin() ? "~" + is.levelMax() : ""),
                    (int) ((is.x() + (is.x() < -1 ? -1 : 1) * is.radius() * (is.radius() < 200 ? 0 : 1.08) - x0) / k),
                    (int) ((is.z() - is.radius() * (is.radius() < 200 ? 1.25 : 0.55) - z0) / k)));
        }
        Obj mons = Rules.get().obj("MONSTERS");
        Obj bosses = Rules.get().obj("BOSSES");
        for (FieldPlan.Site s : plan.sites) {
            String text = s.name();
            String sub = "";
            if (s.kind().equals("zone")) {
                Obj m = mons.objOrNull(s.mob());
                text = m == null ? s.mob() : m.str("name");
                sub = m == null ? "" : "Lv" + Js.str(m.num("level"));
            } else if (s.kind().equals("boss")) {
                Obj b = bosses.objOrNull(s.boss());
                text = b == null ? s.boss() : b.str("name");
                sub = b == null ? "" : "Lv" + Js.str(b.num("level"));
            }
            labels.add(String.format(Locale.ROOT, "{\"kind\":\"%s\",\"text\":\"%s\",\"sub\":\"%s\",\"x\":%d,\"y\":%d}", s.kind(), text == null ? s.id() : text, sub,
                    (int) ((s.x() - x0) / k), (int) ((s.z() - z0) / k)));
        }
        String json = "{\"blocksPerPixel\":" + k + ",\"x0\":" + x0 + ",\"z0\":" + z0 + ",\"w\":" + w + ",\"h\":" + h + ",\"labels\":[\n" + String.join(",\n", labels) + "\n]}";
        Files.writeString(new File(out.replaceAll("\\.png$", "") + ".json").toPath(), json, StandardCharsets.UTF_8);
        System.out.println("preview " + w + "x" + h + " px (" + k + " blocks/px) from x " + x0 + " z " + z0 + " in " + ms + " ms → " + out);
    }

    private static void writePng(BufferedImage img, String out) throws IOException {
        javax.imageio.ImageIO.write(img, "png", new File(out));
    }
}
