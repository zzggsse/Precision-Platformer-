package pixelperil.render;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import pixelperil.Config;
import pixelperil.sim.Player;
import pixelperil.sim.Sim;

/**
 * 玩家与死亡特效的绘制。
 *
 * <p>死亡特效的设计要点：它<b>不阻塞复活</b>。玩家在死亡的同一帧就已经回到出生点，
 * 爆散效果只是留在原地做视觉反馈。这样"死 -> 重试"的循环始终是零延迟的。
 */
public final class PlayerRenderer implements Disposable {

    private static final float SPRITE = Config.TILE;

    private final Texture kid;
    private final TextureRegion region;

    public PlayerRenderer(FileHandle kidFile) {
        this.kid = new Texture(kidFile);
        kid.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        this.region = new TextureRegion(kid);
    }

    /** 画玩家本体。 */
    public void renderPlayer(SpriteBatch batch, Player p) {
        float x = p.body.x + Config.SPRITE_OFFSET_X;
        float y = p.body.y + Config.SPRITE_OFFSET_Y;
        batch.setColor(1f, 1f, 1f, 1f);
        if (p.facingRight) {
            batch.draw(region, x, y, SPRITE, SPRITE);
        } else {
            // 负宽度实现水平翻转（同时要把 x 右移一个精灵宽，否则会画到左边去）
            batch.draw(region, x + SPRITE, y, -SPRITE, SPRITE);
        }
    }

    /**
     * 画死亡爆散：精灵原地放大并淡出。
     * 放在玩家之前画，这样复活后玩家立刻盖在上面，不会互相干扰。
     */
    public void renderDeathBurst(SpriteBatch batch, Sim sim) {
        if (sim.deathFlashTimer <= 0) return;
        float t = sim.deathFlashTimer / (float) Config.DEATH_FLASH_FRAMES;   // 1 -> 0
        float scale = 1f + (1f - t) * 1.6f;
        float w = SPRITE * scale;
        float h = SPRITE * scale;
        float cx = sim.deathX;
        float cy = sim.deathY + Config.PLAYER_H * 0.5f;
        batch.setColor(1f, 1f, 1f, t);
        batch.draw(region, cx - w * 0.5f, cy - h * 0.5f, w, h);
        batch.setColor(1f, 1f, 1f, 1f);
    }

    @Override
    public void dispose() {
        kid.dispose();
    }
}
