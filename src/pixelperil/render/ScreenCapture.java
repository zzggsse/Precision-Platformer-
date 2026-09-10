package pixelperil.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;

import java.nio.ByteBuffer;

/**
 * 把帧缓冲抓成 PNG。用 {@code --screenshot=out.png --shot-frame=30} 启动即可。
 *
 * <p>两个必须自己处理的细节：
 * <ul>
 *   <li>GL 的像素原点在<b>左下角</b>，而 Pixmap 的 y=0 在<b>上边</b>，读回来的图是上下颠倒的，要手动翻；</li>
 *   <li>部分驱动读回来的 alpha 是 0，直接存 PNG 会得到一张全透明的图，所以要强制 alpha=255。</li>
 * </ul>
 *
 * <p>用途：自动化视觉验收（不用人盯着看就知道画面有没有画歪）、做进度截图。
 */
public final class ScreenCapture {

    private ScreenCapture() {}

    public static void capture(int x, int y, int w, int h, FileHandle out) {
        if (w <= 0 || h <= 0) throw new IllegalArgumentException("bad capture rect " + w + "x" + h);

        // 先把命令都执行完，再读像素，否则可能读到半张画面
        Gdx.gl.glFinish();
        Gdx.gl.glPixelStorei(GL20.GL_PACK_ALIGNMENT, 1);

        Pixmap src = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        Gdx.gl.glReadPixels(x, y, w, h, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, src.getPixels());

        Pixmap dst = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        ByteBuffer s = src.getPixels();
        ByteBuffer d = dst.getPixels();
        int stride = w * 4;
        for (int row = 0; row < h; row++) {
            int srcRow = (h - 1 - row) * stride;   // 上下翻转
            int dstRow = row * stride;
            for (int col = 0; col < w; col++) {
                int si = srcRow + col * 4;
                int di = dstRow + col * 4;
                d.put(di, s.get(si));
                d.put(di + 1, s.get(si + 1));
                d.put(di + 2, s.get(si + 2));
                d.put(di + 3, (byte) 0xff);        // 强制不透明
            }
        }

        out.parent().mkdirs();
        PixmapIO.writePNG(out, dst);
        src.dispose();
        dst.dispose();
    }
}
