package mmorpg.core.field;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 건물 설계 도구 (2026-11-07 섬 맵): 정해진 방향 (앞 = 남쪽, 왼쪽 위 = 0,0) 으로 상자를 쌓고, 지을 때 자리 · 방향 (남 · 서 · 북 · 동 — 시계 방향
 * 4분의 1 바퀴씩) 으로 돌려 세계 좌표의 상자 목록이 됨 (블록의 방향 상태 — facing · axis · 울타리 이음 · 문 — 도 함께 돎).
 * 블록 글자에 "|" 로 여럿을 적으면 칸마다 고정된 무작위로 하나 (이끼 낀 돌 벽돌 섞기 등).
 */
public final class Blueprint {
    /** 세계 좌표 상자 하나 */
    public record Box(int x1, int y1, int z1, int x2, int y2, int z2, String block) {}

    final List<Box> boxes = new ArrayList<>();
    /** 자리표 (NPC 자리 · 이동 지점 등 — 세계 좌표 · 바라보는 쪽) */
    final List<Mark> marks = new ArrayList<>();

    public record Mark(String id, double x, double y, double z, String facing) {}

    private final int ox;
    private final int oy;
    private final int oz;
    private final int turns;

    /**
     * @param ox 설계의 (0, 0, 0) 이 놓일 세계 x
     * @param facing 앞 (문) 이 볼 쪽 — south · west · north · east
     */
    public Blueprint(int ox, int oy, int oz, String facing) {
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.turns = switch (facing) {
            case "west" -> 1;
            case "north" -> 2;
            case "east" -> 3;
            default -> 0;
        };
    }

    public List<Box> boxes() {
        return boxes;
    }

    public List<Mark> marks() {
        return marks;
    }

    // ------------------------------------------------------------------ 돌리기

    private int wx(int x, int z) {
        return ox + switch (turns) {
            case 1 -> -z;
            case 2 -> -x;
            case 3 -> z;
            default -> x;
        };
    }

    private int wz(int x, int z) {
        return oz + switch (turns) {
            case 1 -> x;
            case 2 -> -z;
            case 3 -> -x;
            default -> z;
        };
    }

    private static final String[] DIRS = {"north", "east", "south", "west"};

    private static String turnDir(String d, int turns) {
        for (int i = 0; i < 4; i++) if (DIRS[i].equals(d)) return DIRS[(i + turns) % 4];
        return d;
    }

    /** 블록 글자의 방향 상태를 돌림 */
    static String rotate(String block, int turns) {
        if (turns == 0 || block.indexOf('[') < 0) return block;
        int i = block.indexOf('[');
        String name = block.substring(0, i);
        String[] kvs = block.substring(i + 1, block.length() - 1).split(",");
        StringBuilder sb = new StringBuilder(name).append('[');
        for (int k = 0; k < kvs.length; k++) {
            String kv = kvs[k];
            int e = kv.indexOf('=');
            String key = kv.substring(0, e);
            String val = kv.substring(e + 1);
            if (key.equals("facing") || key.equals("horizontal_facing")) val = turnDir(val, turns);
            else if (key.equals("axis") && turns % 2 == 1) val = val.equals("x") ? "z" : val.equals("z") ? "x" : val;
            else if (key.equals("north") || key.equals("east") || key.equals("south") || key.equals("west")) key = turnDir(key, turns);
            else if (key.equals("rotation")) val = String.valueOf((Integer.parseInt(val) + turns * 4) % 16);
            if (k > 0) sb.append(',');
            sb.append(key).append('=').append(val);
        }
        return sb.append(']').toString();
    }

    // ------------------------------------------------------------------ 쌓기 (설계 좌표)

    public Blueprint fill(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        int ax = wx(x1, z1);
        int az = wz(x1, z1);
        int bx = wx(x2, z2);
        int bz = wz(x2, z2);
        String b = block;
        if (b.indexOf('|') < 0) b = rotate(b, turns);
        else {
            String[] parts = b.split("\\|");
            for (int i = 0; i < parts.length; i++) parts[i] = rotate(parts[i], turns);
            b = String.join("|", parts);
        }
        boxes.add(new Box(Math.min(ax, bx), oy + Math.min(y1, y2), Math.min(az, bz), Math.max(ax, bx), oy + Math.max(y1, y2), Math.max(az, bz), b));
        return this;
    }

    public Blueprint set(int x, int y, int z, String block) {
        return fill(x, y, z, x, y, z, block);
    }

    public Blueprint air(int x1, int y1, int z1, int x2, int y2, int z2) {
        return fill(x1, y1, z1, x2, y2, z2, "minecraft:air");
    }

    /** 벽 네 면 (속은 그대로) */
    public Blueprint walls(int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        fill(x1, y1, z1, x2, y2, z1, block);
        fill(x1, y1, z2, x2, y2, z2, block);
        fill(x1, y1, z1, x1, y2, z2, block);
        fill(x2, y1, z1, x2, y2, z2, block);
        return this;
    }

    /** 자리표 (설계 좌표 · 바라보는 쪽 — 설계 방향 기준) */
    public Blueprint mark(String id, double x, double y, double z, String facing) {
        double wxd = ox + switch (turns) {
            case 1 -> -z;
            case 2 -> -x;
            case 3 -> z;
            default -> x;
        };
        double wzd = oz + switch (turns) {
            case 1 -> x;
            case 2 -> -z;
            case 3 -> -x;
            default -> z;
        };
        marks.add(new Mark(id, wxd, oy + y, wzd, turnDir(facing, turns)));
        return this;
    }

    // ------------------------------------------------------------------ 조각

    /** 설계를 조각 하나로 (상자를 차례대로 — 뒤의 상자가 앞을 덮음) */
    public FieldBuild.Piece piece(String name) {
        return new Ops(name, List.copyOf(boxes));
    }

    /** 상자 목록 조각 */
    public static final class Ops implements FieldBuild.Piece {
        final String name;
        final List<Box> boxes;
        final int minX;
        final int maxX;
        final int minZ;
        final int maxZ;

        Ops(String name, List<Box> boxes) {
            this.name = name;
            this.boxes = boxes;
            int a = Integer.MAX_VALUE;
            int b = Integer.MIN_VALUE;
            int c = Integer.MAX_VALUE;
            int d = Integer.MIN_VALUE;
            for (Box x : boxes) {
                a = Math.min(a, x.x1());
                b = Math.max(b, x.x2());
                c = Math.min(c, x.z1());
                d = Math.max(d, x.z2());
            }
            minX = a;
            maxX = b;
            minZ = c;
            maxZ = d;
        }

        public String name() {
            return name;
        }

        public List<Box> boxes() {
            return boxes;
        }

        @Override
        public int minX() {
            return minX;
        }

        @Override
        public int maxX() {
            return maxX;
        }

        @Override
        public int minZ() {
            return minZ;
        }

        @Override
        public int maxZ() {
            return maxZ;
        }

        @Override
        public void column(int x, int z, FieldTerrain.Column ground, FieldBuild.Out out) {
            for (Box b : boxes) {
                if (x < b.x1() || x > b.x2() || z < b.z1() || z > b.z2()) continue;
                String block = b.block();
                if (block.indexOf('|') < 0) {
                    for (int y = b.y1(); y <= b.y2(); y++) out.set(y, block);
                } else {
                    String[] parts = block.split("\\|");
                    for (int y = b.y1(); y <= b.y2(); y++) out.set(y, parts[(int) (FieldNoise.hash(x, y * 31L + z, 11) * parts.length)]);
                }
            }
        }
    }

    /** 바닥 높이 맞춤용: 상자들의 블록 이름 세기 (시험) */
    public static Map<String, Integer> count(List<Box> boxes) {
        Map<String, Integer> m = new java.util.TreeMap<>();
        for (Box b : boxes) {
            long v = (long) (b.x2() - b.x1() + 1) * (b.y2() - b.y1() + 1) * (b.z2() - b.z1() + 1);
            m.merge(b.block(), (int) Math.min(Integer.MAX_VALUE, v), Integer::sum);
        }
        return m;
    }
}
