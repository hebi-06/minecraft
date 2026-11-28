package mmorpg.core;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import mmorpg.core.field.FieldBuild;
import mmorpg.core.field.FieldNoise;
import mmorpg.core.field.FieldTerrain;
import mmorpg.core.field.FieldPlan;

/**
 * 2026-11-28 짓는 것 미리 보기 (개발용 — 게임 없이): 지형 + 조각 (FieldBuild) 을 칸으로 쌓아 비스듬한 입체 그림 (남동 · 남서 · 북동 · 북서에서 내려다봄).
 * 사용: java … BuildPreview 출력.png x0 z0 x1 z1 y0 [칸 크기 px] [쪽 se|sw|ne|nw]
 */
public final class BuildPreview {
    private BuildPreview() {}

    static final Map<String, Color> CACHE = new HashMap<>();

    static Color color(String b) {
        return CACHE.computeIfAbsent(b, BuildPreview::pick);
    }

    private static Color pick(String b) {
        String n = b.replace("minecraft:", "");
        int i = n.indexOf('[');
        if (i >= 0) n = n.substring(0, i);
        if (n.equals("air")) return null;
        String[][] map = {
            {"red_concrete", "182,36,32"}, {"black_concrete", "20,20,24"}, {"polished_blackstone", "48,42,50"}, {"blackstone", "48,42,50"},
            {"oxidized", "80,160,140"}, {"weathered", "105,150,110"}, {"copper", "190,110,80"}, {"lightning_rod", "200,120,80"},
            {"gravel", "135,128,124"}, {"mossy", "100,120,90"}, {"stone_brick", "122,122,122"}, {"chiseled_stone", "118,118,118"}, {"andesite", "140,140,138"},
            {"white_terracotta", "214,184,168"}, {"deepslate_tile", "58,58,64"}, {"deepslate", "70,70,76"}, {"stripped_dark_oak", "92,68,44"},
            {"dark_oak", "66,44,24"}, {"spruce", "112,82,52"}, {"mangrove", "120,50,44"}, {"oak", "160,130,80"}, {"hay", "205,170,50"},
            {"cherry_leaves", "240,172,204"}, {"cherry_log", "66,36,44"}, {"pink_petals", "240,160,200"}, {"lantern", "255,205,90"},
            {"gold", "250,214,64"}, {"lapis", "36,66,170"}, {"glazed_terracotta", "64,176,206"}, {"cut_sandstone", "222,206,152"},
            {"chiseled_sandstone", "214,196,138"}, {"sandstone", "228,214,166"}, {"iron_chain", "60,62,72"}, {"banner", "245,245,245"},
            {"white_wool", "240,240,240"}, {"red_wool", "170,40,40"}, {"blue_wool", "50,70,170"}, {"bell", "230,190,60"}, {"end_rod", "250,250,240"},
            {"pressure_plate", "240,210,80"}, {"water", "60,100,200"}, {"mud", "84,66,58"}, {"packed_mud", "140,104,76"}, {"dirt_path", "150,124,80"},
            {"coarse_dirt", "120,86,60"}, {"dirt", "128,92,62"}, {"grass", "92,150,62"}, {"cauldron", "60,60,64"}, {"lodestone", "120,120,130"},
            {"barrel", "120,88,52"}, {"ladder", "130,100,60"}, {"door", "110,80,50"}, {"trapdoor", "120,90,55"}, {"glass", "200,220,230"},
            {"campfire", "200,90,40"}, {"bookshelf", "120,80,50"}, {"bed", "170,40,40"}, {"crafting", "140,100,60"}, {"torch", "255,220,120"},
            {"wall", "150,150,150"}, {"calcite", "226,224,218"}, {"stone", "125,125,125"}, {"podzol", "100,70,40"}, {"sand", "220,206,160"}
        };
        for (String[] m : map) {
            if (n.contains(m[0])) {
                String[] c = m[1].split(",");
                return new Color(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]));
            }
        }
        return new Color(255, 0, 255);
    }

    static String surface(FieldTerrain.Surface s) {
        return switch (s) {
            case GRASS -> "grass";
            case SAND -> "sand";
            case SANDSTONE -> "sandstone";
            case GRAVEL -> "gravel";
            case STONE -> "stone";
            case PODZOL -> "podzol";
            case MUD -> "mud";
            case COARSE -> "coarse_dirt";
            case PATH -> "dirt_path";
            case DIRT -> "dirt";
            case CALCITE -> "calcite";
            default -> "stone";
        };
    }

    public static void main(String[] a) throws IOException {
        String out = a[0];
        int x0 = Integer.parseInt(a[1]);
        int z0 = Integer.parseInt(a[2]);
        int x1 = Integer.parseInt(a[3]);
        int z1 = Integer.parseInt(a[4]);
        int y0 = Integer.parseInt(a[5]);
        int px = a.length > 6 ? Integer.parseInt(a[6]) : 4;
        String side = a.length > 7 ? a[7] : "se";
        int H = 140;
        int W = x1 - x0 + 1;
        int D = z1 - z0 + 1;
        String[] vox = new String[W * D * H];
        FieldPlan plan = FieldPlan.get();
        FieldTerrain t = new FieldTerrain(plan);
        FieldBuild.Built built = FieldBuild.build(t);
        FieldTerrain.Column c = new FieldTerrain.Column();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                t.sample(x, z, c);
                int base = (x - x0) * D + (z - z0);
                for (int y = Math.max(y0, c.ground - 6); y <= c.ground && y < y0 + H; y++) vox[base * H + (y - y0)] = y == c.ground ? surface(c.top) : "stone";
                if (c.hasWater()) for (int y = c.ground + 1; y <= c.water && y < y0 + H; y++) if (y >= y0) vox[base * H + (y - y0)] = "water";
                if (c.floatTop > c.floatBottom) for (int y = Math.max(y0, c.floatBottom); y <= c.floatTop && y < y0 + H; y++) vox[base * H + (y - y0)] = "sandstone";
                final int fb = base;
                for (FieldBuild.Piece p : built.pieces()) {
                    if (x < p.minX() || x > p.maxX() || z < p.minZ() || z > p.maxZ()) continue;
                    p.column(x, z, c, (y, b) -> {
                        if (y < y0 || y >= y0 + H) return;
                        String bb = b;
                        if (bb.indexOf('|') >= 0) {
                            String[] parts = bb.split("\\|");
                            bb = parts[(int) (FieldNoise.hash(fb, y, 3) * parts.length)];
                        }
                        vox[fb * H + (y - y0)] = bb.equals("minecraft:air") ? null : bb;
                    });
                }
            }
        }
        // 비스듬한 그림: 보는 쪽 (sx, sz) — se 이면 +x · +z 쪽에서
        int sx = side.contains("e") ? 1 : -1;
        int sz = side.startsWith("s") ? 1 : -1;
        double cs = Math.cos(Math.toRadians(30));
        double sn = 0.5;
        int imgW = (int) ((W + D) * cs * px) + 40;
        int imgH = (int) ((W + D) * sn * px + H * px) + 40;
        BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(new Color(150, 190, 230));
        g.fillRect(0, 0, imgW, imgH);
        // 화면 자리: u = (a − b) · cs, v = (a + b) · sn − y — a · b = 보는 쪽으로 커지는 좌표
        int maxS = W + D;
        for (int s = 0; s <= maxS; s++) {
            for (int ia = 0; ia < W; ia++) {
                int ib = s - ia;
                if (ib < 0 || ib >= D) continue;
                int xi = sx > 0 ? ia : W - 1 - ia;
                int zi = sz > 0 ? ib : D - 1 - ib;
                int base = xi * D + zi;
                for (int yi = 0; yi < H; yi++) {
                    String b = vox[base * H + yi];
                    if (b == null) continue;
                    boolean topOpen = yi + 1 >= H || vox[base * H + yi + 1] == null;
                    int nxi = xi + sx;
                    int nzi = zi + sz;
                    boolean aOpen = nxi < 0 || nxi >= W || vox[(nxi * D + zi) * H + yi] == null;
                    boolean bOpen = nzi < 0 || nzi >= D || vox[(xi * D + nzi) * H + yi] == null;
                    if (!topOpen && !aOpen && !bOpen) continue;
                    Color col = color(b);
                    if (col == null) continue;
                    double u = (ia - ib) * cs * px + D * cs * px + 20;
                    double v = (ia + ib) * sn * px + (H - yi) * px + 20;
                    // 꼭짓점: 위 면 마름모 · 두 옆 면
                    int[] tx = {(int) u, (int) (u + cs * px), (int) u, (int) (u - cs * px)};
                    int[] ty = {(int) (v - 2 * sn * px), (int) (v - sn * px), (int) v, (int) (v - sn * px)};
                    // 위 면: (a, b) 칸의 위쪽 (a+1, b+1 쪽이 앞)
                    double ux = u;
                    double vy = v + sn * px;
                    int[] topX = {(int) ux, (int) (ux + cs * px), (int) ux, (int) (ux - cs * px)};
                    int[] topY = {(int) (vy - 2 * sn * px), (int) (vy - sn * px), (int) vy, (int) (vy - sn * px)};
                    g.setColor(shade(col, 1.0));
                    g.fillPolygon(new Polygon(topX, topY, 4));
                    // a 쪽 면 (오른쪽 아래)
                    int[] rX = {(int) (ux + cs * px), (int) (ux + cs * px), (int) ux, (int) ux};
                    int[] rY = {(int) (vy - sn * px), (int) (vy - sn * px + px), (int) (vy + px), (int) vy};
                    g.setColor(shade(col, 0.78));
                    g.fillPolygon(new Polygon(rX, rY, 4));
                    // b 쪽 면 (왼쪽 아래)
                    int[] lX = {(int) (ux - cs * px), (int) (ux - cs * px), (int) ux, (int) ux};
                    int[] lY = {(int) (vy - sn * px), (int) (vy - sn * px + px), (int) (vy + px), (int) vy};
                    g.setColor(shade(col, 0.62));
                    g.fillPolygon(new Polygon(lX, lY, 4));
                    if (tx.length == 0 || ty.length == 0) continue;
                }
            }
        }
        g.dispose();
        // 하늘만 있는 테두리를 잘라 냄
        int sky = new Color(150, 190, 230).getRGB();
        int minX = imgW;
        int minY = imgH;
        int maxX = 0;
        int maxY = 0;
        for (int yy = 0; yy < imgH; yy++) {
            for (int xx = 0; xx < imgW; xx++) {
                if (img.getRGB(xx, yy) == sky) continue;
                minX = Math.min(minX, xx);
                minY = Math.min(minY, yy);
                maxX = Math.max(maxX, xx);
                maxY = Math.max(maxY, yy);
            }
        }
        if (maxX > minX) img = img.getSubimage(Math.max(0, minX - 8), Math.max(0, minY - 8), Math.min(imgW, maxX + 8) - Math.max(0, minX - 8), Math.min(imgH, maxY + 8) - Math.max(0, minY - 8));
        ImageIO.write(img, "png", new File(out));
        System.out.println("build preview " + imgW + "x" + imgH + " → " + out);
    }

    static Color shade(Color c, double k) {
        return new Color((int) Math.min(255, c.getRed() * k), (int) Math.min(255, c.getGreen() * k), (int) Math.min(255, c.getBlue() * k));
    }
}
