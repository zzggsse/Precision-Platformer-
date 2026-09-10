package pixelperil.sim;

import pixelperil.core.Rect;

/**
 * 一个会动的 AABB 实体：玩家、以后的敌人、移动平台都用它。
 *
 * <p>坐标 {@code x,y} 是碰撞盒<b>左下角</b>的世界坐标（y 向上）。
 * 位置保留浮点亚像素精度（移动才顺滑），碰撞解算时再对齐到瓦片边界（贴地时不抖动）。
 */
public final class Body {

    public float x;
    public float y;
    public float vx;
    public float vy;
    public float w;
    public float h;

    /** 复用给 Hazard 查询用的矩形，避免每帧分配。 */
    public final Rect rect = new Rect();

    public Body(float w, float h) {
        this.w = w;
        this.h = h;
    }

    public void place(float x, float y) {
        this.x = x;
        this.y = y;
        this.vx = 0f;
        this.vy = 0f;
    }

    public float left()   { return x; }
    public float right()  { return x + w; }
    public float bottom() { return y; }
    public float top()    { return y + h; }

    public float centerX() { return x + w * 0.5f; }

    public float centerY() { return y + h * 0.5f; }

    /** 把当前状态写进 {@link #rect} 并返回（复用对象，只在本步内有效）。 */
    public Rect rect() {
        return rect.set(x, y, w, h);
    }
}
