package pixelperil.level;

import pixelperil.Config;

/**
 * 瓦片表定义。gid 是 TMX 里的全局瓦片编号（Tiled 的 firstgid + 图集内下标）。
 *
 * <p>图集 tiles.png 是 4 列 x 4 行的网格，每格 {@link Config#TILE} 像素，
 * 所以 TMX 里 {@code firstgid="1"} 时：gid = 1 + 图集下标。
 *
 * <p>与 {@code tools/GenAssets.java} 里的绘制顺序必须一一对应 —— 改了一边就要改另一边。
 */
public final class Tiles {

    private Tiles() {}

    public static final int GROUND_TOP  = 1;  // 图集下标 0：地表（顶部有亮边）
    public static final int DIRT        = 2;  // 图集下标 1：泥土内部
    public static final int BLOCK       = 3;  // 图集下标 2：石块平台
    public static final int SPIKE_UP    = 4;  // 图集下标 3：朝上尖刺
    public static final int SPIKE_DOWN  = 5;  // 图集下标 4：朝下尖刺（挂在天花板）
    public static final int SPIKE_LEFT  = 6;  // 图集下标 5：朝左尖刺
    public static final int SPIKE_RIGHT = 7;  // 图集下标 6：朝右尖刺
    public static final int GOAL        = 8;  // 图集下标 7：终点旗
    public static final int BG_BRICK    = 9;  // 图集下标 8：背景装饰砖

    /** 图集列数。 */
    public static final int ATLAS_COLUMNS = 4;
    /** 图集瓷砖总数（4x4）。 */
    public static final int ATLAS_TILES = 16;

    public static boolean isSpike(int gid) {
        return gid >= SPIKE_UP && gid <= SPIKE_RIGHT;
    }

    public static boolean isGoal(int gid) {
        return gid == GOAL;
    }

    /**
     * 把尖刺瓦片换算成"危险判定框"。
     *
     * <p>为什么判定框比贴图小：尖刺画的是三角形，如果按整格 16x16 判定，
     * 玩家擦着刺尖过去就会死，手感极其恶劣。本类型的经典做法是让判定框
     * 明显小于贴图，这里按方向内缩 {@link Config#SPIKE_SIDE_INSET}（两侧）和
     * {@link Config#SPIKE_BASE_INSET}（根部）。
     *
     * @param gid     尖刺瓦片的 gid
     * @param tileX   瓦片左下角世界坐标 x
     * @param tileY   瓦片左下角世界坐标 y
     * @param out     写出的判定框（复用，避免每帧分配）
     * @return 若 gid 不是已知尖刺返回 false，out 不被修改
     */
    public static boolean spikeHitbox(int gid, float tileX, float tileY, pixelperil.core.Rect out) {
        final float t = Config.TILE;
        final float side = Config.SPIKE_SIDE_INSET;
        final float base = Config.SPIKE_BASE_INSET;
        switch (gid) {
            case SPIKE_UP ->
                // 刺尖朝上：左右各缩 side，底部缩 base
                out.set(tileX + side, tileY + base, t - 2 * side, t - base);
            case SPIKE_DOWN ->
                // 刺尖朝下：左右各缩 side，顶部缩 base
                out.set(tileX + side, tileY, t - 2 * side, t - base);
            case SPIKE_LEFT ->
                // 刺尖朝左：上下各缩 side，右侧缩 base
                out.set(tileX + base, tileY + side, t - base, t - 2 * side);
            case SPIKE_RIGHT ->
                // 刺尖朝右：上下各缩 side，左侧缩 base
                out.set(tileX, tileY + side, t - base, t - 2 * side);
            default -> {
                return false;
            }
        }
        return true;
    }
}
