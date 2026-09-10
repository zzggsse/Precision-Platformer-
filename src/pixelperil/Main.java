package pixelperil;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/**
 * 桌面端入口。
 *
 * <p>用法：{@code java -cp "out;assets;libs/*" pixelperil.Main [--level=levels/level1.tmx]}
 * 或者直接跑 {@code scripts\run.ps1}。
 */
public final class Main {

    private static final String DEFAULT_LEVEL = "levels/level1.tmx";

    public static void main(String[] args) {
        String level = DEFAULT_LEVEL;
        String screenshot = null;
        int shotFrame = 30;
        boolean debug = false;

        for (String a : args) {
            if (a.startsWith("--level=")) {
                level = a.substring("--level=".length());
            } else if (a.startsWith("--screenshot=")) {
                screenshot = a.substring("--screenshot=".length());
            } else if (a.startsWith("--shot-frame=")) {
                shotFrame = Integer.parseInt(a.substring("--shot-frame=".length()));
            } else if (a.equals("--debug")) {
                debug = true;
            }
        }

        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("PixelPeril - " + level);
        cfg.setWindowedMode(Config.WINDOW_W, Config.WINDOW_H);
        cfg.setResizable(true);
        // 垂直同步 + 限制 60 帧：逻辑本来就是固定 60Hz，渲染也锁 60 最省电、最稳。
        cfg.useVsync(true);
        cfg.setForegroundFPS(60);
        // 关掉 LWJGL 控制台噪声，但保留我们的日志
        cfg.setIdleFPS(30);

        try {
            new Lwjgl3Application(new PixelPerilGame(level, screenshot, shotFrame, debug), cfg);
        } catch (Throwable t) {
            System.err.println();
            System.err.println("启动失败: " + t);
            System.err.println("常见原因：显卡驱动不支持 OpenGL 3.2 / 远程桌面环境缺少 GPU 加速。");
            System.err.println("如果是在无头环境里跑，请改用 scripts\\selftest.ps1（不需要窗口）。");
            t.printStackTrace();
            System.exit(1);
        }
    }
}
