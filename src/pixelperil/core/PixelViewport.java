package pixelperil.core;

import com.badlogic.gdx.Gdx;

/**
 * 像素完美视口：把虚拟分辨率按<b>整数倍</b>缩放后居中放到窗口里，四周留黑边。
 *
 * <p>为什么不能用 FitViewport：它按浮点比例缩放，缩放 2.5 倍时相邻像素的屏幕宽度
 * 会不一致（有的 2 像素、有的 3 像素），像素画会出现"抖动/粗细不均"。
 * 整数倍缩放是像素游戏唯一正确的做法。
 *
 * <p>GL 的视口原点在<b>左下角</b>，这里的 offsetY 也按左下角计算，直接可以喂给
 * {@code glViewport}。
 */
public final class PixelViewport {

    private final int virtualW;
    private final int virtualH;

    private int scale = 1;
    private int viewW;
    private int viewH;
    private int offsetX;
    private int offsetY;

    public PixelViewport(int virtualW, int virtualH) {
        this.virtualW = virtualW;
        this.virtualH = virtualH;
        this.viewW = virtualW;
        this.viewH = virtualH;
    }

    /** 窗口尺寸变化时重算。windowW/H 必须是真实像素（backbuffer 尺寸）。 */
    public void resize(int windowW, int windowH) {
        int s = Math.min(windowW / virtualW, windowH / virtualH);
        // 窗口比虚拟画布还小时退化为 1 倍并居中裁剪，而不是缩成小数倍。
        if (s < 1) s = 1;
        scale = s;
        viewW = virtualW * s;
        viewH = virtualH * s;
        offsetX = (windowW - viewW) / 2;
        offsetY = (windowH - viewH) / 2;
    }

    /** 立即把 GL 视口设成游戏区域。 */
    public void applyGlViewport() {
        Gdx.gl.glViewport(offsetX, offsetY, viewW, viewH);
    }

    public int scale() { return scale; }
    public int viewWidth() { return viewW; }
    public int viewHeight() { return viewH; }
    public int offsetX() { return offsetX; }
    public int offsetY() { return offsetY; }
    public int virtualWidth() { return virtualW; }
    public int virtualHeight() { return virtualH; }
}
