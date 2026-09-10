package pixelperil.level;

import com.badlogic.gdx.utils.XmlReader;
import pixelperil.Config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 把 Tiled 的 TMX 文件解析成 {@link LevelData}。
 *
 * <p>刻意<b>不使用</b> libGDX 的 {@code TmxMapLoader}：那个类会去创建 {@code Texture}，
 * 必须有 GL 上下文，于是物理逻辑就没法在无窗口环境里做自动化测试。
 * 这里只用 {@link XmlReader}（纯 Java，不碰 GL）读出瓦片数组和对象矩形。
 *
 * <p>支持的 TMX 子集（Tiled 默认导出即可满足）：
 * <ul>
 *   <li>orientation="orthogonal"、无限地图关闭；</li>
 *   <li>内嵌 tileset（必须带 {@code <image>}）；</li>
 *   <li><b>CSV</b> 编码的图层数据（base64 / zlib 不支持，会给出明确报错）；</li>
 *   <li>图层名：{@code bg}（装饰）/ {@code solid}（实心）/ {@code hazard}（尖刺），
 *       其他名字会被忽略并记录成 warning；</li>
 *   <li>对象层里名为 {@code spawn} 和 {@code goal} 的矩形。</li>
 * </ul>
 */
public final class LevelLoader {

    private LevelLoader() {}

    private static final String LAYER_BG = "bg";
    private static final String LAYER_SOLID = "solid";
    private static final String LAYER_HAZARD = "hazard";

    public static LevelData load(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return load(in, file.toString());
        }
    }

    public static LevelData load(InputStream in, String sourceName) throws IOException {
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return load(reader, sourceName);
        }
    }

    public static LevelData load(Reader reader, String sourceName) throws IOException {
        XmlReader xml = new XmlReader();
        XmlReader.Element map;
        try {
            map = xml.parse(reader);
        } catch (RuntimeException e) {
            throw new IOException("无法解析 TMX: " + sourceName + " (" + e.getMessage() + ")", e);
        }
        if (!"map".equals(map.getName())) {
            throw new IOException("不是 TMX 文件（根节点是 <" + map.getName() + ">）: " + sourceName);
        }

        String orientation = map.getAttribute("orientation", "orthogonal");
        if (!"orthogonal".equals(orientation)) {
            throw new IOException("只支持 orientation=\"orthogonal\"，当前是 \"" + orientation + "\": " + sourceName);
        }
        if ("1".equals(map.getAttribute("infinite", "0"))) {
            throw new IOException("不支持无限地图（infinite=\"1\"），请在 Tiled 里关掉: " + sourceName);
        }

        int width = map.getInt("width", -1);
        int height = map.getInt("height", -1);
        int tileW = map.getInt("tilewidth", -1);
        int tileH = map.getInt("tileheight", -1);
        if (width <= 0 || height <= 0) {
            throw new IOException("TMX 缺少合法的 width/height: " + sourceName);
        }
        if (tileW != Config.TILE || tileH != Config.TILE) {
            throw new IOException("瓦片尺寸必须是 " + Config.TILE + "x" + Config.TILE
                    + "（Config.TILE），当前是 " + tileW + "x" + tileH + ": " + sourceName);
        }

        // ---- tileset：只取图片路径和 firstgid ----
        XmlReader.Element tileset = map.getChildByName("tileset");
        if (tileset == null) {
            throw new IOException("TMX 里没有 <tileset>（请在 Tiled 里给地图挂上图集）: " + sourceName);
        }
        if (tileset.getAttribute("source", null) != null) {
            throw new IOException("不支持外部 TSX 图集（<tileset source=\"...\">）。"
                    + "请在 Tiled 里改用内嵌图集: " + sourceName);
        }
        XmlReader.Element image = tileset.getChildByName("image");
        if (image == null) {
            throw new IOException("内嵌 tileset 必须带 <image>（基于单张图集的瓦片）: " + sourceName);
        }
        String imageSource = image.getAttribute("source", null);
        if (imageSource == null) {
            throw new IOException("tileset 的 <image> 缺少 source 属性: " + sourceName);
        }
        int firstGid = tileset.getInt("firstgid", 1);

        // ---- 图层数据 ----
        int[] bg = new int[width * height];
        int[] solid = new int[width * height];
        int[] hazard = new int[width * height];

        boolean sawSolidLayer = false;
        for (int i = 0; i < map.getChildCount(); i++) {
            XmlReader.Element child = map.getChild(i);
            if (!"layer".equals(child.getName())) continue;

            String name = child.getAttribute("name", "").trim().toLowerCase();
            boolean known = name.equals(LAYER_BG) || name.equals(LAYER_SOLID) || name.equals(LAYER_HAZARD);
            if (!known) continue;

            int lw = child.getInt("width", width);
            int lh = child.getInt("height", height);
            if (lw != width || lh != height) {
                throw new IOException("图层 \"" + name + "\" 尺寸 " + lw + "x" + lh
                        + " 与地图 " + width + "x" + height + " 不一致: " + sourceName);
            }

            int[] dst = switch (name) {
                case LAYER_BG -> bg;
                case LAYER_SOLID -> solid;
                default -> hazard;
            };
            readCsvLayer(child, dst, width, height, name, sourceName);
            if (name.equals(LAYER_SOLID)) sawSolidLayer = true;
        }

        // ---- 对象层：spawn / goal ----
        // Tiled 对象坐标 y 从顶部往下，这里统一换算成"y 向上、原点左下"的世界坐标。
        float mapPixelH = height * (float) Config.TILE;
        boolean hasSpawn = false;
        float spawnX = 0f, spawnY = 0f;
        boolean hasGoal = false;
        float goalX = 0f, goalY = 0f, goalW = 0f, goalH = 0f;

        for (int i = 0; i < map.getChildCount(); i++) {
            XmlReader.Element group = map.getChild(i);
            if (!"objectgroup".equals(group.getName())) continue;
            for (int j = 0; j < group.getChildCount(); j++) {
                XmlReader.Element obj = group.getChild(j);
                if (!"object".equals(obj.getName())) continue;

                // 注意：XmlReader 的单参数 getAttribute 在属性缺失时会抛异常，
                // 所以这里一律用带默认值的重载。
                String kind = firstNonNull(
                        obj.getAttribute("name", null),
                        obj.getAttribute("type", null),
                        obj.getAttribute("class", null));
                if (kind == null) continue;
                kind = kind.trim().toLowerCase();

                float ox = obj.getFloat("x", 0f);
                float oy = obj.getFloat("y", 0f);
                float ow = obj.getFloat("width", 0f);
                float oh = obj.getFloat("height", 0f);
                // 顶朝下的 TMX 坐标 -> y 向上的世界坐标
                float worldBottom = mapPixelH - oy - oh;

                switch (kind) {
                    case "spawn" -> {
                        hasSpawn = true;
                        spawnX = ox;
                        spawnY = worldBottom;
                    }
                    case "goal" -> {
                        hasGoal = true;
                        goalX = ox;
                        goalY = worldBottom;
                        goalW = ow;
                        goalH = oh;
                    }
                    default -> { /* 其它对象暂时忽略 */ }
                }
            }
        }

        if (!hasSpawn) {
            throw new IOException("关卡里没有出生点。请在 Tiled 的对象层里加一个矩形，"
                    + "把它的 Name 设成 \"spawn\": " + sourceName);
        }

        LevelData level = new LevelData(width, height, imageSource, firstGid,
                spawnX, spawnY, hasGoal, goalX, goalY, goalW, goalH);
        System.arraycopy(bg, 0, level.bg, 0, bg.length);
        System.arraycopy(solid, 0, level.solid, 0, solid.length);
        System.arraycopy(hazard, 0, level.hazard, 0, hazard.length);

        if (!sawSolidLayer) {
            level.warnings.add("没有名为 \"" + LAYER_SOLID + "\" 的图层，关卡将没有实心地形");
        }
        return level;
    }

    /**
     * 读出图层数据并做 TMX 行序转换（TMX 行 0 在最上，LevelData 行 0 在最下）。
     */
    private static void readCsvLayer(XmlReader.Element layer, int[] dst, int width, int height,
                                     String layerName, String sourceName) throws IOException {
        XmlReader.Element data = layer.getChildByName("data");
        if (data == null) {
            throw new IOException("图层 \"" + layerName + "\" 没有 <data> 节点: " + sourceName);
        }
        String encoding = data.getAttribute("encoding", "");
        if (!"csv".equals(encoding)) {
            throw new IOException("图层 \"" + layerName + "\" 使用了不支持的编码 \"" + encoding + "\"。"
                    + "请在 Tiled 的 地图属性 -> 图层格式 里选 CSV: " + sourceName);
        }

        String text = data.getText();
        if (text == null) {
            throw new IOException("图层 \"" + layerName + "\" 的数据为空: " + sourceName);
        }
        // CSV 里可能有换行和缩进，先把所有空白字符去掉再切分。
        StringBuilder cleaned = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isWhitespace(c)) cleaned.append(c);
        }

        String[] parts = cleaned.toString().split(",");
        int expected = width * height;
        if (parts.length != expected) {
            throw new IOException("图层 \"" + layerName + "\" 有 " + parts.length
                    + " 个格子，期望 " + expected + " (" + width + "x" + height + "): " + sourceName);
        }
        for (int i = 0; i < expected; i++) {
            int gid;
            try {
                gid = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                throw new IOException("图层 \"" + layerName + "\" 第 " + i + " 个格子不是整数: \""
                        + parts[i] + "\": " + sourceName, e);
            }
            int tmxRow = i / width;       // 0 = 最上面一行
            int col = i % width;
            int dataRow = height - 1 - tmxRow;  // 转成 0 = 最下面一行
            dst[dataRow * width + col] = gid;
        }
    }

    private static String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }
}
