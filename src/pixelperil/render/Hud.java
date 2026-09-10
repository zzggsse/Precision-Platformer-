package pixelperil.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import pixelperil.Config;
import pixelperil.sim.Sim;

import java.util.Locale;

/**
 * HUD：死亡数、计时、关卡名、操作提示、通关横幅、错误提示。
 *
 * <p>用的是 libGDX 自带的内置字体（Arial-15），只为省掉一份字体资源。它是位图缩放过的，
 * 在像素画面里略"脏" —— 想要真正的像素字体，见 README 的后续计划。
 * 这里至少把采样设成最近邻并强制整数坐标，避免糊成一团。
 */
public final class Hud implements Disposable {

    private static final Color C_TEXT = new Color(0.92f, 0.94f, 1f, 1f);
    private static final Color C_DIM = new Color(0.62f, 0.66f, 0.78f, 1f);
    private static final Color C_WARN = new Color(1f, 0.75f, 0.3f, 1f);
    private static final Color C_GOOD = new Color(0.45f, 1f, 0.55f, 1f);

    private final BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();
    private final StringBuilder sb = new StringBuilder(64);
    /** 1x1 白色贴图，用来画半透明衬底条（HUD 文字压在泥土上会看不清）。 */
    private final Texture white;

    public Hud() {
        font = new BitmapFont();   // 内置字体，随 gdx.jar 一起提供
        font.getRegion().getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        font.setUseIntegerPositions(true);
        font.getData().setScale(1f);

        Pixmap px = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        px.setColor(1f, 1f, 1f, 1f);
        px.fill();
        white = new Texture(px);
        white.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        px.dispose();
    }

    /** 画一条半透明衬底，让文字在花哨的地形上也读得清。 */
    private void panel(SpriteBatch batch, float x, float y, float w, float h) {
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(white, x, y, w, h);
        batch.setColor(1f, 1f, 1f, 1f);
    }

    /**
     * @param viewLeft 当前可见区域左边界（世界坐标）= 虚拟画布左边缘
     * @param viewTop  当前可见区域上边界
     */
    public void render(SpriteBatch batch, float viewLeft, float viewTop, Sim sim,
                       String levelName, boolean debugBoxes, String levelError) {
        float x = viewLeft + 6f;
        float y = viewTop - 4f;
        float line = 16f;

        // ---- 左上：统计 ----
        font.setColor(C_TEXT);
        sb.setLength(0);
        sb.append("DEATHS  ").append(sim.deaths);
        font.draw(batch, sb, x, y);

        y -= line;
        sb.setLength(0);
        sb.append("TIME    ").append(formatTime(sim.displaySeconds()));
        font.draw(batch, sb, x, y);

        // ---- 右上：关卡名 ----
        font.setColor(C_DIM);
        layout.setText(font, levelName);
        font.draw(batch, levelName, viewLeft + Config.VIRTUAL_W - 6f - layout.width, viewTop - 4f);

        sb.setLength(0);
        sb.append("pos ").append((int) sim.player.body.x).append(",").append((int) sim.player.body.y);
        if (debugBoxes) {
            sb.append("   [F1 boxes ON]");
        }
        layout.setText(font, sb);
        font.draw(batch, sb, viewLeft + Config.VIRTUAL_W - 6f - layout.width, viewTop - 4f - line);

        // ---- 左下：操作提示（带衬底，避免压在泥土上看不清）----
        float hintY = viewTop - Config.VIRTUAL_H + 18f;
        panel(batch, viewLeft, hintY - 3f, Config.VIRTUAL_W, 15f);
        font.setColor(C_DIM);
        font.draw(batch, "Z/SPACE jump   ARROWS move   R restart   F5 reload level   F1 hitboxes   ESC quit",
                x, hintY);

        // ---- 异常提示 ----
        float warnY = viewTop - Config.VIRTUAL_H + 40f;
        if (levelError != null) {
            font.setColor(C_WARN);
            font.draw(batch, "LEVEL ERROR (still running previous level): " + levelError, x, warnY);
            warnY += line;
        }
        if (sim.stuckAtSpawn) {
            font.setColor(C_WARN);
            font.draw(batch, "WARNING: spawn point is inside solid tiles", x, warnY);
        }

        // ---- 通关横幅 ----
        if (sim.cleared) {
            String msg = "CLEAR!   " + formatTime(sim.clearFrames / 60f) + "   deaths " + sim.deaths;
            layout.setText(font, msg);
            font.setColor(C_GOOD);
            font.draw(batch, msg,
                    viewLeft + (Config.VIRTUAL_W - layout.width) * 0.5f,
                    viewTop - Config.VIRTUAL_H * 0.45f);
        }

        font.setColor(Color.WHITE);
    }

    /** 秒 -> mm:ss.mmm */
    private static String formatTime(float seconds) {
        if (seconds < 0f) seconds = 0f;
        int totalMs = (int) (seconds * 1000f);
        int minutes = totalMs / 60000;
        int secs = (totalMs / 1000) % 60;
        int ms = totalMs % 1000;
        // Locale.ROOT：避免在部分地区设置下输出本地化数字
        return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, secs, ms);
    }

    @Override
    public void dispose() {
        font.dispose();
        white.dispose();
    }
}
