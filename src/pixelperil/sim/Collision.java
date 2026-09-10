package pixelperil.sim;

import pixelperil.Config;
import pixelperil.level.LevelData;

/**
 * 瓦片 AABB 碰撞。全部静态方法、无状态、零分配。
 *
 * <p>坐标是浮点（保留亚像素精度，移动才顺滑），但采样网格时用 EPS 做半开区间处理，
 * 避免"刚好贴在格子边界上"被算进相邻格子导致重复判定。
 */
public final class Collision {

    private Collision() {}

    /** 边界容差。比任何实际位移都小，但足以吃掉浮点误差。 */
    public static final float EPS = 1e-4f;

    private static final float T = Config.TILE;

    public static int cell(float v) {
        return (int) Math.floor(v / T);
    }

    /**
     * 以 (x, y) 为左下角、尺寸 (w, h) 的矩形是否与任何实心瓦片重叠。
     * 也用于脚下 1px 探针判定"是否站在地面上"。
     */
    public static boolean overlapsSolid(LevelData lv, float x, float y, float w, float h) {
        int c0 = cell(x + EPS);
        int c1 = cell(x + w - EPS);
        int r0 = cell(y + EPS);
        int r1 = cell(y + h - EPS);
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                if (lv.solid(c, r)) return true;
            }
        }
        return false;
    }

    /**
     * 水平移动并解算。
     *
     * <p>只检查"新进入的那一列"。单步最大水平位移（{@link Config#RUN_SPEED}/60 ≈ 2.4px）
     * 远小于瓦片边长，一步最多跨进一列，所以这种写法既精确又不需要扫描。
     */
    public static void moveX(LevelData lv, Body b, float dx) {
        if (dx == 0f) return;
        float before = b.x;
        b.x += dx;

        if (dx > 0f) {
            int colBefore = cell(before + b.w - EPS);
            int colAfter = cell(b.x + b.w - EPS);
            if (colAfter > colBefore) {
                int r0 = cell(b.y + EPS);
                int r1 = cell(b.y + b.h - EPS);
                for (int r = r0; r <= r1; r++) {
                    if (lv.solid(colAfter, r)) {
                        b.x = colAfter * T - b.w;   // 右边缘贴住墙的左侧面
                        b.vx = 0f;
                        return;
                    }
                }
            }
        } else {
            int colBefore = cell(before + EPS);
            int colAfter = cell(b.x + EPS);
            if (colAfter < colBefore) {
                int r0 = cell(b.y + EPS);
                int r1 = cell(b.y + b.h - EPS);
                for (int r = r0; r <= r1; r++) {
                    if (lv.solid(colAfter, r)) {
                        b.x = (colAfter + 1) * T;   // 左边缘贴住墙的右侧面
                        b.vx = 0f;
                        return;
                    }
                }
            }
        }
    }

    /** {@link #moveY} 的结果，复用对象，只在本步内有效。 */
    public static final class YResult {
        public boolean landed;
        public boolean hitCeiling;
        public boolean cornerCorrected;
        public float correctionAmount;
    }

    private static final YResult Y_RESULT = new YResult();

    /**
     * 垂直移动并解算，含<b>1px 边缘修正</b>。
     *
     * <p>边缘修正的作用：向上跳时如果头顶只差几个像素就蹭到平台角落，就把玩家
     * 横向推开让他擦过去。没有这个，玩家会遇到"看起来明明能上去结果卡住"的情况 ——
     * 本类型里这种挫败感是致命的。修正上限见 {@link Config#CORNER_CORRECTION}，
     * 设为 0 即关闭（更硬核）。
     */
    public static YResult moveY(LevelData lv, Body b, float dy) {
        final YResult res = Y_RESULT;
        res.landed = false;
        res.hitCeiling = false;
        res.cornerCorrected = false;
        res.correctionAmount = 0f;
        if (dy == 0f) return res;

        float before = b.y;
        b.y += dy;

        if (dy > 0f) {
            int rowBefore = cell(before + b.h - EPS);
            int rowAfter = cell(b.y + b.h - EPS);
            if (rowAfter > rowBefore) {
                // 先试边缘修正：横向推 1..N 像素，能完全摆脱重叠就放行（继续上升）。
                float max = Config.CORNER_CORRECTION;
                if (max > 0f) {
                    for (float d = 1f; d <= max; d += 1f) {
                        if (!overlapsSolid(lv, b.x - d, b.y, b.w, b.h)) {
                            b.x -= d;
                            res.cornerCorrected = true;
                            res.correctionAmount = -d;
                            return res;
                        }
                        if (!overlapsSolid(lv, b.x + d, b.y, b.w, b.h)) {
                            b.x += d;
                            res.cornerCorrected = true;
                            res.correctionAmount = d;
                            return res;
                        }
                    }
                }
                int c0 = cell(b.x + EPS);
                int c1 = cell(b.x + b.w - EPS);
                for (int c = c0; c <= c1; c++) {
                    if (lv.solid(c, rowAfter)) {
                        b.y = rowAfter * T - b.h;   // 头顶贴住天花板下侧面
                        b.vy = 0f;
                        res.hitCeiling = true;
                        return res;
                    }
                }
            }
        } else {
            int rowBefore = cell(before + EPS);
            int rowAfter = cell(b.y + EPS);
            if (rowAfter < rowBefore) {
                int c0 = cell(b.x + EPS);
                int c1 = cell(b.x + b.w - EPS);
                for (int c = c0; c <= c1; c++) {
                    if (lv.solid(c, rowAfter)) {
                        b.y = (rowAfter + 1) * T;   // 脚底贴住地面上侧面
                        b.vy = 0f;
                        res.landed = true;
                        return res;
                    }
                }
            }
        }
        return res;
    }
}
