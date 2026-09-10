import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.GdxNativesLoader;

import java.io.File;

/**
 * 生成占位美术。
 *
 * <p>为什么用代码生成而不是往仓库里塞 PNG：纯文本资源好 diff、好改，
 * 而且换真素材时只要覆盖同名文件即可（路径和尺寸不变，游戏代码一行都不用动）。
 *
 * <p>输出：
 * <ul>
 *   <li>{@code assets/tiles/tiles.png} — 4x4 网格，每格 16x16，共 16 个瓦片</li>
 *   <li>{@code assets/sprites/kid.png} — 16x16 玩家精灵</li>
 * </ul>
 *
 * <p>瓦片顺序必须与 {@code src/pixelperil/level/Tiles.java} 的常量一一对应，改一边就要改另一边。
 *
 * <p>运行：{@code scripts/gen-assets.ps1}
 *
 * <p>注意：这个工具只需要 libGDX 的原生 Pixmap（gdx.dll），<b>不需要 GL 上下文 / 窗口</b>。
 */
public final class GenAssets {

    private static final int TILE = 16;
    private static final int ATLAS_COLS = 4;

    // 调色板（RGB，不含 alpha）
    private static final int GRASS      = 0x8fbf4a;  // 地表亮色
    private static final int DIRT_LIGHT = 0x7a5230;
    private static final int DIRT_DARK  = 0x5c3c22;
    private static final int DIRT_SPECK = 0x6b4527;
    private static final int STONE      = 0x8a8fa3;
    private static final int STONE_EDGE = 0x5f6478;
    private static final int STONE_HI   = 0xc2c8da;
    private static final int SPIKE      = 0xdfe4f0;
    private static final int SPIKE_EDGE = 0x7c8296;
    private static final int BG_BRICK   = 0x2b2438;
    private static final int BG_LINE    = 0x1e1930;
    private static final int FLAG      = 0xff5a5a;
    private static final int POLE      = 0xd8dce8;

    private static final int SKIN      = 0xf0c08a;
    private static final int SKIN_DARK = 0xd49a68;
    private static final int HAIR      = 0x3a2a1e;
    private static final int SHIRT     = 0x4a7ad0;
    private static final int SHIRT_DK  = 0x33569b;
    private static final int PANTS     = 0x2a3550;
    private static final int SHOE      = 0x1a1a22;
    private static final int EYE       = 0x14141c;

    public static void main(String[] args) {
        // 显式加载原生库，报错时能给出更清楚的信息
        try {
            GdxNativesLoader.load();
        } catch (Throwable t) {
            System.err.println("无法加载 libGDX 原生库 gdx: " + t);
            System.err.println("检查 libs/ 里是否有 gdx-platform-*-natives-desktop.jar");
            System.exit(1);
        }

        String root = args.length > 0 ? args[0] : ".";
        File tilesDir = new File(root, "assets/tiles");
        File spritesDir = new File(root, "assets/sprites");
        tilesDir.mkdirs();
        spritesDir.mkdirs();

        writeTileset(new File(tilesDir, "tiles.png"));
        writeKid(new File(spritesDir, "kid.png"));

        System.out.println("generated assets/tiles/tiles.png and assets/sprites/kid.png");
    }

    // ------------------------------------------------------------------ 瓦片图集

    private static void writeTileset(File out) {
        Pixmap pm = new Pixmap(ATLAS_COLS * TILE, ATLAS_COLS * TILE, Pixmap.Format.RGBA8888);
        pm.setColor(0, 0, 0, 0);
        pm.fill();

        tileGroundTop(pm, 0);
        tileDirt(pm, 1);
        tileStone(pm, 2);
        tileSpike(pm, 3, Dir.UP);
        tileSpike(pm, 4, Dir.DOWN);
        tileSpike(pm, 5, Dir.LEFT);
        tileSpike(pm, 6, Dir.RIGHT);
        tileGoal(pm, 7);
        tileBgBrick(pm, 8);
        // 下标 9..15 暂留空（未来加单向平台、传送门等）

        PixmapIO.writePNG(new com.badlogic.gdx.files.FileHandle(out), pm);
        pm.dispose();
    }

    /** 把 (col,row) 的图集坐标换算成 Pixmap 像素原点。注意 Pixmap 的 y=0 在<b>上</b>边。 */
    private static int ox(int index) { return (index % ATLAS_COLS) * TILE; }

    private static int oy(int index) { return (index / ATLAS_COLS) * TILE; }

    /** 地表：上面 4px 草/亮色，下面泥土，中间一条深色分隔。 */
    private static void tileGroundTop(Pixmap pm, int index) {
        int x = ox(index), y = oy(index);
        rect(pm, DIRT_LIGHT, x, y + 4, TILE, TILE - 4);
        rect(pm, GRASS, x, y, TILE, 4);
        // 草下面一条深色阴影，让地面有厚度
        rect(pm, DIRT_DARK, x, y + 4, TILE, 1);
        // 草尖上点缀几粒深色，避免整条死板
        px(pm, DIRT_DARK, x + 2, y + 3);
        px(pm, DIRT_DARK, x + 7, y + 1);
        px(pm, DIRT_DARK, x + 12, y + 2);
        speckle(pm, x, y + 5, TILE, TILE - 5, DIRT_SPECK);
    }

    /** 泥土（地面内部）。 */
    private static void tileDirt(Pixmap pm, int index) {
        int x = ox(index), y = oy(index);
        rect(pm, DIRT_LIGHT, x, y, TILE, TILE);
        speckle(pm, x, y, TILE, TILE, DIRT_SPECK);
    }

    /** 石块平台：有亮顶边和暗底边，和泥土区分开。 */
    private static void tileStone(Pixmap pm, int index) {
        int x = ox(index), y = oy(index);
        rect(pm, STONE, x, y, TILE, TILE);
        rect(pm, STONE_HI, x, y, TILE, 2);          // 顶面高光
        rect(pm, STONE_EDGE, x, y + TILE - 2, TILE, 2); // 底面阴影
        rect(pm, STONE_EDGE, x, y, 1, TILE);
        rect(pm, STONE_EDGE, x + TILE - 1, y, 1, TILE);
    }

    private enum Dir { UP, DOWN, LEFT, RIGHT }

    /** 尖刺：实心三角形 + 描边。朝上时尖端在 Pixmap 的小 y（屏幕上方）。 */
    private static void tileSpike(Pixmap pm, int index, Dir dir) {
        int x = ox(index), y = oy(index);
        int cx = x + TILE / 2;
        int cy = y + TILE / 2;
        // 描边：先用深色画一个稍大的三角形，再用亮色画里面的
        switch (dir) {
            case UP -> {
                pm.setColor(rgba(SPIKE_EDGE));
                pm.fillTriangle(x, y + TILE, x + TILE, y + TILE, cx, y);
                pm.setColor(rgba(SPIKE));
                pm.fillTriangle(x + 1, y + TILE - 1, x + TILE - 1, y + TILE - 1, cx, y + 2);
            }
            case DOWN -> {
                pm.setColor(rgba(SPIKE_EDGE));
                pm.fillTriangle(x, y, x + TILE, y, cx, y + TILE);
                pm.setColor(rgba(SPIKE));
                pm.fillTriangle(x + 1, y + 1, x + TILE - 1, y + 1, cx, y + TILE - 2);
            }
            case LEFT -> {
                pm.setColor(rgba(SPIKE_EDGE));
                pm.fillTriangle(x + TILE, y, x + TILE, y + TILE, x, cy);
                pm.setColor(rgba(SPIKE));
                pm.fillTriangle(x + TILE - 1, y + 1, x + TILE - 1, y + TILE - 1, x + 2, cy);
            }
            case RIGHT -> {
                pm.setColor(rgba(SPIKE_EDGE));
                pm.fillTriangle(x, y, x, y + TILE, x + TILE, cy);
                pm.setColor(rgba(SPIKE));
                pm.fillTriangle(x + 1, y + 1, x + 1, y + TILE - 1, x + TILE - 2, cy);
            }
        }
    }

    /** 终点旗：旗杆 + 三角旗。 */
    private static void tileGoal(Pixmap pm, int index) {
        int x = ox(index), y = oy(index);
        rect(pm, POLE, x + 4, y + 1, 2, TILE - 2);
        pm.setColor(rgba(FLAG));
        pm.fillTriangle(x + 6, y + 2, x + 6, y + 10, x + 14, y + 6);
        rect(pm, STONE_EDGE, x + 3, y + TILE - 2, 4, 2);
    }

    /** 背景装饰砖。 */
    private static void tileBgBrick(Pixmap pm, int index) {
        int x = ox(index), y = oy(index);
        rect(pm, BG_BRICK, x, y, TILE, TILE);
        // 错缝砖线
        rect(pm, BG_LINE, x, y + 7, TILE, 1);
        rect(pm, BG_LINE, x, y + 15, TILE, 1);
        rect(pm, BG_LINE, x + 7, y, 1, 8);
        rect(pm, BG_LINE, x + 3, y + 8, 1, 7);
        rect(pm, BG_LINE, x + 11, y + 8, 1, 7);
    }

    // ------------------------------------------------------------------ 玩家精灵

    /**
     * 16x16 的"小孩"。
     *
     * <p>碰撞盒只有 8x12，精灵比它大 —— 这是有意为之：视觉上看起来贴着的东西，
     * 判定上还留有余量，玩家会觉得"擦过去了"，而不是"明明没碰到却死了"。
     * 精灵相对碰撞盒的偏移见 {@code Config.SPRITE_OFFSET_X/Y} 的说明。
     */
    private static void writeKid(File out) {
        Pixmap pm = new Pixmap(TILE, TILE, Pixmap.Format.RGBA8888);
        pm.setColor(0, 0, 0, 0);
        pm.fill();

        // 头发
        rect(pm, HAIR, 4, 1, 8, 3);
        rect(pm, HAIR, 3, 2, 1, 3);
        rect(pm, HAIR, 12, 2, 1, 3);
        // 脸
        rect(pm, SKIN, 4, 4, 8, 4);
        rect(pm, SKIN_DARK, 4, 7, 8, 1);   // 下巴阴影
        // 眼睛
        px(pm, EYE, 6, 5);
        px(pm, EYE, 10, 5);
        // 脖子
        rect(pm, SKIN_DARK, 7, 8, 2, 1);
        // 上衣
        rect(pm, SHIRT, 4, 9, 8, 4);
        rect(pm, SHIRT_DK, 4, 12, 8, 1);
        // 手臂
        rect(pm, SKIN, 2, 9, 2, 3);
        rect(pm, SKIN, 12, 9, 2, 3);
        // 裤子
        rect(pm, PANTS, 5, 13, 2, 2);
        rect(pm, PANTS, 9, 13, 2, 2);
        // 鞋
        rect(pm, SHOE, 4, 15, 3, 1);
        rect(pm, SHOE, 9, 15, 3, 1);

        PixmapIO.writePNG(new com.badlogic.gdx.files.FileHandle(out), pm);
        pm.dispose();
    }

    // ------------------------------------------------------------------ 小工具

    private static int rgba(int rgb) {
        return (rgb << 8) | 0xff;
    }

    private static void rect(Pixmap pm, int rgb, int x, int y, int w, int h) {
        pm.setColor(rgba(rgb));
        pm.fillRectangle(x, y, w, h);
    }

    private static void px(Pixmap pm, int rgb, int x, int y) {
        pm.setColor(rgba(rgb));
        pm.drawPixel(x, y);
    }

    /** 用确定性的伪随机撒一些暗色颗粒，让大片色块不至于死板。 */
    private static void speckle(Pixmap pm, int x, int y, int w, int h, int rgb) {
        pm.setColor(rgba(rgb));
        int seed = 0x2545f491;
        for (int i = 0; i < w * h / 6; i++) {
            seed = seed * 1103515245 + 12345;
            int sx = x + Math.floorMod(seed >> 16, w);
            seed = seed * 1103515245 + 12345;
            int sy = y + Math.floorMod(seed >> 16, h);
            pm.drawPixel(sx, sy);
        }
    }
}
