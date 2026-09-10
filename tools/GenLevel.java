import pixelperil.level.Tiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 把 ASCII 关卡草图编译成 Tiled 的 TMX 文件。
 *
 * <p>为什么要有这一步：手写 40x22 的 CSV 数组太容易出错，而用 Tiled 从零画第一版也慢。
 * ASCII 草图可以直接在编辑器里改、能 diff、能一眼看出关卡形状；编译出的 TMX 再丢进
 * Tiled 做精修。
 *
 * <p><b>工作流约定</b>：ASCII 是"种子"。一旦你用 Tiled 精修过 TMX，就以 TMX 为准，
 * 不要再跑这个工具去覆盖它（或者把精修结果另存成 level2.tmx）。
 *
 * <p>ASCII 图例（{@code .ascii} 文件里以 {@code |} 开头的行是注释，会被忽略）：
 * <pre>
 *   .  空
 *   #  地表（实心，顶部带草）
 *   =  泥土（实心，地面内部）
 *   B  石块平台（实心）
 *   ^  朝上尖刺        v  朝下尖刺
 *   &lt;  朝左尖刺        &gt;  朝右尖刺
 *   :  背景砖（纯装饰，不参与碰撞）
 *   P  出生点（该格本身为空）
 *   G  终点（该格本身为空）
 * </pre>
 *
 * <p>运行：{@code scripts/gen-level.ps1}
 */
public final class GenLevel {

    private static final int TILE = 16;

    private static final char EMPTY = '.';
    private static final char GROUND = '#';
    private static final char DIRT = '=';
    private static final char BLOCK = 'B';
    private static final char SPIKE_UP = '^';
    private static final char SPIKE_DOWN = 'v';
    private static final char SPIKE_LEFT = '<';
    private static final char SPIKE_RIGHT = '>';
    private static final char BG_BRICK = ':';
    private static final char SPAWN = 'P';
    private static final char GOAL = 'G';
    private static final char COMMENT = '|';

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("usage: GenLevel <input.ascii> [output.tmx] [tilesetImageAbsPath]");
            System.exit(2);
        }
        Path in = Paths.get(args[0]).toAbsolutePath().normalize();
        Path out = args.length > 1
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : in.resolveSibling(stripExt(in.getFileName().toString()) + ".tmx");
        Path tileset = args.length > 2
                ? Paths.get(args[2]).toAbsolutePath().normalize()
                : in.getParent().getParent().resolve("tiles/tiles.png");

        List<String> raw = Files.readAllLines(in, StandardCharsets.UTF_8);
        List<String> rows = new ArrayList<>();
        for (String line : raw) {
            if (line.isBlank()) continue;
            if (line.charAt(0) == COMMENT) continue;
            rows.add(line);
        }
        if (rows.isEmpty()) {
            throw new IOException("ASCII 关卡为空: " + in);
        }

        int height = rows.size();
        int width = rows.get(0).length();
        for (int r = 0; r < height; r++) {
            int len = rows.get(r).length();
            if (len != width) {
                throw new IOException("第 " + r + " 行有 " + len + " 列，但第 0 行有 " + width
                        + " 列。所有行必须等宽: " + in);
            }
        }

        int[] bg = new int[width * height];
        int[] solid = new int[width * height];
        int[] hazard = new int[width * height];
        int spawnCol = -1, spawnRow = -1;
        int goalCol = -1, goalRow = -1;
        List<String> errors = new ArrayList<>();

        for (int r = 0; r < height; r++) {
            String row = rows.get(r);
            for (int c = 0; c < width; c++) {
                char ch = row.charAt(c);
                int i = r * width + c;   // TMX 行序：行 0 在最上
                switch (ch) {
                    case EMPTY -> { }
                    case GROUND -> solid[i] = Tiles.GROUND_TOP;
                    case DIRT -> solid[i] = Tiles.DIRT;
                    case BLOCK -> solid[i] = Tiles.BLOCK;
                    case SPIKE_UP -> hazard[i] = Tiles.SPIKE_UP;
                    case SPIKE_DOWN -> hazard[i] = Tiles.SPIKE_DOWN;
                    case SPIKE_LEFT -> hazard[i] = Tiles.SPIKE_LEFT;
                    case SPIKE_RIGHT -> hazard[i] = Tiles.SPIKE_RIGHT;
                    case BG_BRICK -> bg[i] = Tiles.BG_BRICK;
                    case SPAWN -> {
                        if (spawnCol >= 0) errors.add("有多个出生点 P，只允许一个（第二个在 行" + r + " 列" + c + "）");
                        else { spawnCol = c; spawnRow = r; }
                    }
                    case GOAL -> {
                        if (goalCol >= 0) errors.add("有多个终点 G，只允许一个（第二个在 行" + r + " 列" + c + "）");
                        else { goalCol = c; goalRow = r; }
                    }
                    default -> errors.add("未知字符 '" + ch + "' 在 行" + r + " 列" + c);
                }
            }
        }
        if (spawnCol < 0) errors.add("没有出生点 P");
        if (goalCol < 0) errors.add("没有终点 G");
        if (!errors.isEmpty()) {
            System.err.println("ASCII 关卡有问题: " + in);
            for (String e : errors) System.err.println("  - " + e);
            System.exit(1);
        }

        Files.createDirectories(out.getParent());
        String tilesetRel = out.getParent().relativize(tileset).toString().replace('\\', '/');
        String tmx = buildTmx(width, height, bg, solid, hazard,
                spawnCol, spawnRow, goalCol, goalRow, tilesetRel);
        Files.writeString(out, tmx, StandardCharsets.UTF_8);

        System.out.println("compiled " + in.getFileName() + " -> " + out);
        System.out.println("  size   : " + width + "x" + height + " tiles ("
                + (width * TILE) + "x" + (height * TILE) + " px)");
        System.out.println("  spawn  : col " + spawnCol + ", row " + spawnRow);
        System.out.println("  goal   : col " + goalCol + ", row " + goalRow);
        System.out.println("  tileset: " + tilesetRel);
        System.out.println("  solid=" + count(solid) + " hazard=" + count(hazard) + " bg=" + count(bg));
    }

    private static String buildTmx(int width, int height, int[] bg, int[] solid, int[] hazard,
                                   int spawnCol, int spawnRow, int goalCol, int goalRow,
                                   String tilesetRel) {
        StringBuilder sb = new StringBuilder(64 * 1024);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<map version=\"1.10\" tiledversion=\"1.10.2\" orientation=\"orthogonal\"")
          .append(" renderorder=\"right-down\" width=\"").append(width)
          .append("\" height=\"").append(height)
          .append("\" tilewidth=\"").append(TILE).append("\" tileheight=\"").append(TILE)
          .append("\" infinite=\"0\" nextlayerid=\"5\" nextobjectid=\"3\">\n");
        sb.append(" <tileset firstgid=\"1\" name=\"tiles\" tilewidth=\"").append(TILE)
          .append("\" tileheight=\"").append(TILE)
          .append("\" spacing=\"0\" margin=\"0\" tilecount=\"").append(Tiles.ATLAS_TILES)
          .append("\" columns=\"").append(Tiles.ATLAS_COLUMNS).append("\">\n");
        sb.append("  <image source=\"").append(tilesetRel)
          .append("\" width=\"").append(Tiles.ATLAS_COLUMNS * TILE)
          .append("\" height=\"").append((Tiles.ATLAS_TILES / Tiles.ATLAS_COLUMNS) * TILE)
          .append("\"/>\n");
        sb.append(" </tileset>\n");

        appendLayer(sb, 1, "bg", width, height, bg);
        appendLayer(sb, 2, "solid", width, height, solid);
        appendLayer(sb, 3, "hazard", width, height, hazard);

        // 对象坐标：Tiled 的 y 从顶部往下，所以行 r 的 y = r * TILE。
        sb.append(" <objectgroup id=\"4\" name=\"objects\">\n");
        sb.append("  <object id=\"1\" name=\"spawn\" x=\"").append(spawnCol * TILE)
          .append("\" y=\"").append(spawnRow * TILE)
          .append("\" width=\"").append(TILE).append("\" height=\"").append(TILE).append("\"/>\n");
        sb.append("  <object id=\"2\" name=\"goal\" x=\"").append(goalCol * TILE)
          .append("\" y=\"").append(goalRow * TILE)
          .append("\" width=\"").append(TILE).append("\" height=\"").append(TILE).append("\"/>\n");
        sb.append(" </objectgroup>\n");
        sb.append("</map>\n");
        return sb.toString();
    }

    private static void appendLayer(StringBuilder sb, int id, String name,
                                    int width, int height, int[] data) {
        sb.append(" <layer id=\"").append(id).append("\" name=\"").append(name)
          .append("\" width=\"").append(width).append("\" height=\"").append(height).append("\">\n");
        sb.append("  <data encoding=\"csv\">\n");
        for (int r = 0; r < height; r++) {
            sb.append("   ");
            for (int c = 0; c < width; c++) {
                if (c > 0) sb.append(',');
                sb.append(data[r * width + c]);
            }
            sb.append(r == height - 1 ? "\n" : ",\n");
        }
        sb.append("  </data>\n");
        sb.append(" </layer>\n");
    }

    private static int count(int[] a) {
        int n = 0;
        for (int v : a) if (v != 0) n++;
        return n;
    }

    private static String stripExt(String s) {
        int i = s.lastIndexOf('.');
        return i < 0 ? s : s.substring(0, i);
    }
}
