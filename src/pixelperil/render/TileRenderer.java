package pixelperil.render;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import pixelperil.Config;
import pixelperil.level.LevelData;
import pixelperil.level.Tiles;

/**
 * 瓦片渲染。
 *
 * <p>只画摄像机可见范围内的格子（视口裁剪）。关卡可能很大，全量遍历在 60Hz 下是浪费。
 *
 * <p>刻意<b>不用</b> libGDX 的 {@code TiledMapRenderer}：模拟层用的是
 * {@link LevelData} 的扁平数组，直接照着数组画是最简单也最可控的做法，
 * 也避免了两套关卡表示（TiledMap 和 LevelData）之间出现不同步。
 */
public final class TileRenderer implements Disposable {

    private static final int TILE = Config.TILE;

    private final Texture tileset;
    private final int firstGid;
    /** 下标 = gid - firstGid，即图集内的瓦片下标。 */
    private final TextureRegion[] regions;

    public TileRenderer(FileHandle tilesetFile, int firstGid, int atlasColumns, int atlasTiles) {
        this.tileset = new Texture(tilesetFile);
        this.firstGid = firstGid;
        // 最近邻采样：像素画必须这样，否则缩放时会糊成一团。
        tileset.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);

        TextureRegion[][] grid = TextureRegion.split(tileset, TILE, TILE);
        this.regions = new TextureRegion[atlasTiles];
        for (int i = 0; i < atlasTiles; i++) {
            int row = i / atlasColumns;
            int col = i % atlasColumns;
            if (row < grid.length && col < grid[row].length) {
                regions[i] = grid[row][col];
            }
        }
    }

    /**
     * 画出可见范围内的三个图层。
     *
     * @param viewLeft  可见世界区域左边界（含）
     * @param viewBottom 可见世界区域下边界（含）
     * @param viewRight 可见世界区域右边界（不含）
     * @param viewTop   可见世界区域上边界（不含）
     */
    public void render(SpriteBatch batch, LevelData lv,
                       float viewLeft, float viewBottom, float viewRight, float viewTop) {
        int c0 = Math.max(0, (int) Math.floor(viewLeft / TILE));
        int c1 = Math.min(lv.width - 1, (int) Math.floor(viewRight / TILE));
        int r0 = Math.max(0, (int) Math.floor(viewBottom / TILE));
        int r1 = Math.min(lv.height - 1, (int) Math.floor(viewTop / TILE));
        if (c0 > c1 || r0 > r1) return;

        drawLayer(batch, lv.bg, lv.width, c0, c1, r0, r1);
        drawLayer(batch, lv.solid, lv.width, c0, c1, r0, r1);
        drawLayer(batch, lv.hazard, lv.width, c0, c1, r0, r1);
    }

    private void drawLayer(SpriteBatch batch, int[] data, int width,
                           int c0, int c1, int r0, int r1) {
        for (int r = r0; r <= r1; r++) {
            int rowBase = r * width;
            for (int c = c0; c <= c1; c++) {
                int gid = data[rowBase + c];
                if (gid == 0) continue;
                TextureRegion region = regionFor(gid);
                if (region == null) continue;   // 未知 gid：跳过而不是崩溃，方便边改边试
                batch.draw(region, c * TILE, r * TILE, TILE, TILE);
            }
        }
    }

    /**
     * 画终点旗。
     *
     * <p>为什么不由图层数据来画：终点在 TMX 里是一个<b>对象矩形</b>，不是瓦片。
     * 直接按矩形位置画旗子有两个好处 —— 在 Tiled 里拖动 goal 对象，画面会自动跟上；
     * 而且"看到的旗子"和"实际触发的判定框"永远是同一个矩形，不会对不上。
     */
    public void renderGoal(SpriteBatch batch, LevelData lv) {
        if (!lv.hasGoal || lv.goalW <= 0f || lv.goalH <= 0f) return;
        TextureRegion region = regionFor(Tiles.GOAL);
        if (region == null) return;
        batch.draw(region, lv.goalX, lv.goalY, lv.goalW, lv.goalH);
    }

    private TextureRegion regionFor(int gid) {
        int index = gid - firstGid;
        if (index < 0 || index >= regions.length) return null;
        // 图集里没定义的格子是 null（split 越界），一并跳过而不是崩溃。
        return regions[index];
    }

    public Texture texture() {
        return tileset;
    }

    @Override
    public void dispose() {
        tileset.dispose();
    }
}
