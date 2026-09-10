package pixelperil;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import pixelperil.core.FixedStepper;
import pixelperil.core.PixelViewport;
import pixelperil.input.InputState;
import pixelperil.level.LevelData;
import pixelperil.level.LevelLoader;
import pixelperil.level.Tiles;
import pixelperil.render.DebugRenderer;
import pixelperil.render.Hud;
import pixelperil.render.PlayerRenderer;
import pixelperil.render.ScreenCapture;
import pixelperil.render.TileRenderer;
import pixelperil.sim.Sim;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 游戏主体：固定步长主循环 + 像素完美渲染 + 热重载。
 *
 * <p>帧结构（顺序很重要）：
 * <ol>
 *   <li>处理一次性按键（R / F5 / F1 / ESC）—— 这些与手感无关，用渲染帧级事件没问题；</li>
 *   <li>读取键盘原始状态；</li>
 *   <li>{@link FixedStepper} 决定本帧要跑几个逻辑步，逐步推进模拟（跳跃边沿在步内计算）；</li>
 *   <li>清屏 -> 设置整数缩放的 GL 视口 -> 画瓦片、玩家、HUD -> 画调试框。</li>
 * </ol>
 */
public final class PixelPerilGame extends ApplicationAdapter {

    private final String levelPath;
    /** 截图模式：非空时在第 shotFrame 帧抓图后退出（用于自动化视觉验收）。 */
    private final String shotPath;
    private final int shotFrame;
    private int frameCounter;

    private SpriteBatch batch;
    private OrthographicCamera camera;
    private PixelViewport viewport;
    private FixedStepper stepper;
    private InputState input;
    private Sim sim;
    private Hud hud;
    private DebugRenderer debug;

    private TileRenderer tileRenderer;
    private PlayerRenderer playerRenderer;

    private boolean debugBoxes;
    private final boolean debugBoxesAtStart;
    /** 最近一次关卡加载失败的信息；显示在 HUD 上而不是让游戏崩掉，方便边改关卡边试。 */
    private String levelError;

    private float camX;
    private float camY;

    public PixelPerilGame(String levelPath) {
        this(levelPath, null, 0, false);
    }

    public PixelPerilGame(String levelPath, String shotPath, int shotFrame, boolean debugBoxesAtStart) {
        this.levelPath = levelPath;
        this.shotPath = shotPath;
        this.shotFrame = shotFrame;
        this.debugBoxesAtStart = debugBoxesAtStart;
        this.debugBoxes = debugBoxesAtStart;
    }

    @Override
    public void create() {
        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        camera.setToOrtho(false, Config.VIRTUAL_W, Config.VIRTUAL_H);
        viewport = new PixelViewport(Config.VIRTUAL_W, Config.VIRTUAL_H);
        viewport.resize(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        stepper = new FixedStepper(Config.STEP_SECONDS, Config.MAX_STEPS_PER_FRAME);
        input = new InputState();
        hud = new Hud();
        debug = new DebugRenderer();
        sim = new Sim();

        boolean ok = loadLevel();
        if (!ok && sim.level == null) {
            // 第一次就读不进来：继续跑也没意义，直接退出并保留日志。
            Gdx.app.error("pixelperil", "无法加载关卡 " + levelPath + "，退出。");
            Gdx.app.exit();
        }
    }

    /**
     * 读取关卡（也从磁盘重载时复用）。
     *
     * <p>失败时保留上一个可用的关卡，只把错误显示出来 —— 这样你在 Tiled 里改到一半、
     * TMX 语法暂时是坏的，游戏不会直接挂掉。
     */
    private boolean loadLevel() {
        try {
            LevelData lv = LevelLoader.load(Gdx.files.internal(levelPath).read(), levelPath);

            FileHandle tilesetFile = Gdx.files.internal(resolveSibling(levelPath, lv.tilesetImage));
            TileRenderer newTiles = new TileRenderer(tilesetFile, lv.firstGid,
                    Tiles.ATLAS_COLUMNS, Tiles.ATLAS_TILES);
            PlayerRenderer newPlayer = new PlayerRenderer(Gdx.files.internal("sprites/kid.png"));

            if (tileRenderer != null) tileRenderer.dispose();
            if (playerRenderer != null) playerRenderer.dispose();
            tileRenderer = newTiles;
            playerRenderer = newPlayer;

            sim.loadLevel(lv);
            levelError = null;

            for (String w : lv.warnings) Gdx.app.log("pixelperil", "level warning: " + w);
            Gdx.app.log("pixelperil", "loaded " + levelPath + " ("
                    + lv.width + "x" + lv.height + " tiles, " + lv.pixelWidth() + "x"
                    + lv.pixelHeight() + " px), spawn=(" + lv.spawnX + "," + lv.spawnY + ")"
                    + (lv.hasGoal ? "" : ", NO GOAL"));

            centerCameraOnLevel();
            return true;
        } catch (Exception e) {
            levelError = e.getMessage();
            Gdx.app.error("pixelperil", "加载关卡失败: " + levelPath, e);
            return false;
        }
    }

    /**
     * 把 TMX 里写的图集相对路径（相对 level 文件所在目录）解析成 assets 根下的路径。
     * 例如 {@code levels/level1.tmx} + {@code ../tiles/tiles.png} -> {@code tiles/tiles.png}。
     */
    private static String resolveSibling(String levelPath, String relative) {
        String dir = "";
        int slash = levelPath.lastIndexOf('/');
        if (slash >= 0) dir = levelPath.substring(0, slash + 1);

        Deque<String> parts = new ArrayDeque<>();
        for (String p : (dir + relative).split("/")) {
            if (p.isEmpty() || ".".equals(p)) continue;
            if ("..".equals(p)) {
                if (!parts.isEmpty()) parts.removeLast();
            } else {
                parts.addLast(p);
            }
        }
        return String.join("/", parts);
    }

    @Override
    public void render() {
        handleOneShotKeys();

        input.pollRaw();
        stepper.beginFrame(Gdx.graphics.getDeltaTime());
        int steps = stepper.steps();
        for (int i = 0; i < steps; i++) {
            input.beginStep();
            sim.step(input);
            input.endStep();
        }

        updateCamera();

        // 先把整个窗口清成底色：glViewport 只覆盖游戏区域，黑边需要单独清，
        // 否则窗口缩放后黑边里会残留上一帧的内容。
        Gdx.gl.glViewport(0, 0,
                Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        ScreenUtils.clear(Config.BG_R, Config.BG_G, Config.BG_B, 1f);

        viewport.applyGlViewport();
        camera.update();

        float halfW = Config.VIRTUAL_W * 0.5f;
        float halfH = Config.VIRTUAL_H * 0.5f;
        float viewLeft = camX - halfW;
        float viewBottom = camY - halfH;
        float viewRight = camX + halfW;
        float viewTop = camY + halfH;

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        if (tileRenderer != null && sim.level != null) {
            tileRenderer.render(batch, sim.level, viewLeft, viewBottom, viewRight, viewTop);
            tileRenderer.renderGoal(batch, sim.level);
        }
        if (playerRenderer != null) {
            playerRenderer.renderDeathBurst(batch, sim);
            playerRenderer.renderPlayer(batch, sim.player);
        }
        hud.render(batch, viewLeft, viewTop, sim, levelPath, debugBoxes, levelError);
        batch.end();

        if (debugBoxes) {
            debug.render(camera, sim, viewLeft, viewBottom, viewRight, viewTop, viewport.scale());
        }

        frameCounter++;
        if (shotPath != null && frameCounter >= shotFrame) {
            // 同步抓图：此时本帧的绘制命令还没提交/交换，glReadPixels 读到的就是刚画完的内容。
            // 用 postRunnable 推到下一帧反而不安全 —— 交换后后台缓冲内容未定义。
            ScreenCapture.capture(viewport.offsetX(), viewport.offsetY(),
                    viewport.viewWidth(), viewport.viewHeight(), Gdx.files.local(shotPath));
            Gdx.app.log("pixelperil", "screenshot written: " + shotPath);
            Gdx.app.exit();
        }
    }

    private void handleOneShotKeys() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) {
            debugBoxes = !debugBoxes;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            sim.restart();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) {
            // 读图后清掉输入边沿，避免按 F5 时按住的键被当成一次跳跃
            if (loadLevel()) input.clearEdges();
        }
    }

    /**
     * 摄像机跟随。位置取整以保持像素完美 —— 亚像素的摄像机位置会让整个画面的
     * 瓦片在屏幕上落到非整数像素上，出现"抖动/粗细不均"。
     */
    private void updateCamera() {
        LevelData lv = sim.level;
        float halfW = Config.VIRTUAL_W * 0.5f;
        float halfH = Config.VIRTUAL_H * 0.5f;

        float targetX = sim.player.body.centerX()
                + (sim.player.facingRight ? Config.CAM_LOOK_AHEAD : -Config.CAM_LOOK_AHEAD);
        float targetY = sim.player.body.centerY();

        if (lv != null) {
            targetX = lv.pixelWidth() <= Config.VIRTUAL_W
                    ? lv.pixelWidth() * 0.5f
                    : clamp(targetX, halfW, lv.pixelWidth() - halfW);
            targetY = lv.pixelHeight() <= Config.VIRTUAL_H
                    ? lv.pixelHeight() * 0.5f
                    : clamp(targetY, halfH, lv.pixelHeight() - halfH);
        }

        camX = Math.round(targetX);
        camY = Math.round(targetY);
        camera.position.set(camX, camY, 0f);
    }

    /** 关卡换成尺寸不同的另一张时，把摄像机先摆正，避免第一帧位置错乱。 */
    private void centerCameraOnLevel() {
        updateCamera();
    }

    private static float clamp(float v, float lo, float hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    @Override
    public void resize(int width, int height) {
        // 用 backbuffer 尺寸（真实像素）而不是逻辑尺寸，整数缩放才算得对。
        viewport.resize(Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
    }

    @Override
    public void pause() {
        // 窗口失焦回来时 deltaTime 会很大，丢掉积压避免一次性补算一大段逻辑。
        stepper.reset();
    }

    @Override
    public void resume() {
        stepper.reset();
    }

    @Override
    public void dispose() {
        if (tileRenderer != null) tileRenderer.dispose();
        if (playerRenderer != null) playerRenderer.dispose();
        if (hud != null) hud.dispose();
        if (debug != null) debug.dispose();
        if (batch != null) batch.dispose();
    }
}
