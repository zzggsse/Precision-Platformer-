package pixelperil.render;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.Gdx;
import pixelperil.Config;
import pixelperil.core.Rect;
import pixelperil.level.LevelData;
import pixelperil.level.Tiles;
import pixelperil.sim.Sim;

/**
 * 调试可视化（F1 开关）。做这类关卡时这是最高频用到的工具 ——
 * 你必须能看见"判定框到底在哪"，否则永远调不明白为什么这个缝过不去。
 *
 * <p>画出：玩家碰撞盒（绿）、尖刺判定框（红）、脚下 1px 探测线（黄）、
 * 以及可见范围内的实心格子轮廓（暗蓝）。
 */
public final class DebugRenderer {

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final Rect spike = new Rect();

    /**
     * @param viewportScale 当前整数缩放倍率。线宽按倍率补偿，这样看起来始终是 1 物理像素。
     */
    public void render(OrthographicCamera camera, Sim sim,
                       float viewLeft, float viewBottom, float viewRight, float viewTop,
                       int viewportScale) {
        LevelData lv = sim.level;
        if (lv == null) return;

        shapes.setProjectionMatrix(camera.combined);
        Gdx.gl.glLineWidth(Math.max(1f, viewportScale));
        shapes.begin(ShapeType.Line);

        // 实心格子轮廓
        shapes.setColor(0.25f, 0.35f, 0.6f, 0.45f);
        int c0 = Math.max(0, (int) Math.floor(viewLeft / Config.TILE));
        int c1 = Math.min(lv.width - 1, (int) Math.floor(viewRight / Config.TILE));
        int r0 = Math.max(0, (int) Math.floor(viewBottom / Config.TILE));
        int r1 = Math.min(lv.height - 1, (int) Math.floor(viewTop / Config.TILE));
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                if (lv.solid(c, r)) {
                    shapes.rect(c * Config.TILE, r * Config.TILE, Config.TILE, Config.TILE);
                }
            }
        }

        // 尖刺判定框
        shapes.setColor(1f, 0.25f, 0.25f, 0.95f);
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                int gid = lv.hazardGid(c, r);
                if (gid == 0 || !Tiles.isSpike(gid)) continue;
                Tiles.spikeHitbox(gid, c * Config.TILE, r * Config.TILE, spike);
                shapes.rect(spike.x, spike.y, spike.w, spike.h);
            }
        }

        // 玩家碰撞盒
        shapes.setColor(0.3f, 1f, 0.35f, 1f);
        shapes.rect(sim.player.body.x, sim.player.body.y, sim.player.body.w, sim.player.body.h);

        // 脚下 1px 探测线（落地判定的依据）
        shapes.setColor(1f, 0.9f, 0.2f, 0.9f);
        shapes.line(sim.player.body.x, sim.player.body.y - 1f,
                sim.player.body.x + sim.player.body.w, sim.player.body.y - 1f);

        shapes.end();
        Gdx.gl.glLineWidth(1f);
    }

    public void dispose() {
        shapes.dispose();
    }
}
