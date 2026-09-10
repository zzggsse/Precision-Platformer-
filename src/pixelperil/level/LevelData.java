package pixelperil.level;

import pixelperil.core.Rect;

import java.util.ArrayList;
import java.util.List;

/**
 * 关卡的<b>纯数据</b>表示：扁平的 int 数组 + 出生点/终点矩形。
 *
 * <p>为什么不用 libGDX 的 {@code TiledMap} 直接跑模拟：
 * <ul>
 *   <li>{@code TiledMap} 加载贴图需要 GL 上下文，导致物理逻辑无法在无窗口环境测试；</li>
 *   <li>按格子随机访问 {@code TiledMapTileLayer} 会做对象查找，热循环里代价高且可能分配；</li>
 *   <li>扁平数组对确定性友好 —— 同样的输入必然得到同样的结果，方便以后做回放录制。</li>
 * </ul>
 * 所以：<b>模拟用这里的数组，渲染才用贴图</b>。
 *
 * <p>坐标约定：<b>行 0 = 关卡最底下一行</b>（y 轴向上），与 TMX 的"行 0 在最上面"相反，
 * 转换在 {@link LevelLoader} 里一次性做好。
 */
public final class LevelData {

    /** 关卡宽（格）。 */
    public final int width;
    /** 关卡高（格）。 */
    public final int height;

    /** 背景装饰层 gid，0 = 空。 */
    public final int[] bg;
    /** 实体碰撞层 gid，非 0 即实心。 */
    public final int[] solid;
    /** 危险物层 gid，非 0 即尖刺。 */
    public final int[] hazard;

    /** 图集图片路径（相对 assets 根），渲染层用它加载贴图。 */
    public final String tilesetImage;
    /** TMX 里 tileset 的 firstgid。 */
    public final int firstGid;

    /** 出生点：玩家碰撞盒左下角的世界坐标。 */
    public final float spawnX;
    public final float spawnY;

    /** 终点矩形（世界坐标）。hasGoal=false 时无意义。 */
    public final boolean hasGoal;
    public final float goalX;
    public final float goalY;
    public final float goalW;
    public final float goalH;

    /** 解析过程中的非致命问题（未知图层等），用于在控制台提示作者。 */
    public final List<String> warnings = new ArrayList<>();

    /** 复用给尖刺判定用的临时矩形，避免每个尖刺都分配对象。 */
    private final Rect scratchSpike = new Rect();

    public LevelData(int width, int height, String tilesetImage, int firstGid,
                     float spawnX, float spawnY,
                     boolean hasGoal, float goalX, float goalY, float goalW, float goalH) {
        this.width = width;
        this.height = height;
        this.tilesetImage = tilesetImage;
        this.firstGid = firstGid;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.hasGoal = hasGoal;
        this.goalX = goalX;
        this.goalY = goalY;
        this.goalW = goalW;
        this.goalH = goalH;
        this.bg = new int[width * height];
        this.solid = new int[width * height];
        this.hazard = new int[width * height];
    }

    /** 关卡像素宽。 */
    public int pixelWidth() { return width * pixelperil.Config.TILE; }

    /** 关卡像素高。 */
    public int pixelHeight() { return height * pixelperil.Config.TILE; }

    /**
     * 该格是否实心。
     *
     * <p>越界规则：左右两侧和底部视为实心（当墙用，玩家跑不出去也掉不出去），
     * 顶部视为空（天空开放，可以跳出去）。
     */
    public boolean solid(int col, int row) {
        if (col < 0 || col >= width) return true;
        if (row < 0) return true;
        if (row >= height) return false;
        return solid[row * width + col] != 0;
    }

    /** 该格危险物 gid，0 = 无。越界恒为 0。 */
    public int hazardGid(int col, int row) {
        if (col < 0 || col >= width || row < 0 || row >= height) return 0;
        return hazard[row * width + col];
    }

    public int bgGid(int col, int row) {
        if (col < 0 || col >= width || row < 0 || row >= height) return 0;
        return bg[row * width + col];
    }

    public int solidGid(int col, int row) {
        if (col < 0 || col >= width || row < 0 || row >= height) return 0;
        return solid[row * width + col];
    }

    /** 写出该尖刺格的判定框（复用内部临时对象，仅在本步内有效）。 */
    public Rect spikeHitbox(int gid, int col, int row) {
        float t = pixelperil.Config.TILE;
        return Tiles.spikeHitbox(gid, col * t, row * t, scratchSpike) ? scratchSpike : null;
    }

    /** 终点矩形（复用内部临时对象）。 */
    public Rect goalRect() {
        return new Rect(goalX, goalY, goalW, goalH);
    }
}
