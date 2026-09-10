package pixelperil.core;

/**
 * 极简可变浮点矩形，原点在左下角（y 向上）。
 *
 * <p>刻意不用 libGDX 的 {@code Rectangle}：模拟层保持零图形库依赖，
 * 这样物理逻辑可以在没有窗口/GL 的环境下跑自动化测试。
 */
public final class Rect {

    public float x;
    public float y;
    public float w;
    public float h;

    public Rect() {}

    public Rect(float x, float y, float w, float h) {
        set(x, y, w, h);
    }

    public Rect set(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        return this;
    }

    /** 四边各内缩 amount（amount 为负则外扩）。 */
    public Rect inset(float amount) {
        return set(x + amount, y + amount, w - 2 * amount, h - 2 * amount);
    }

    public float right() { return x + w; }
    public float top() { return y + h; }

    /**
     * 与另一矩形是否重叠。
     * 采用半开区间语义（贴边不算重叠），这样相邻瓦片之间不会误判。
     */
    public boolean overlaps(Rect o) {
        return x < o.right() && o.x < right() && y < o.top() && o.y < top();
    }
}
