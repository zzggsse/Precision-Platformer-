package pixelperil.sim;

import pixelperil.Config;
import pixelperil.core.Rect;
import pixelperil.input.InputState;
import pixelperil.level.LevelData;

/**
 * 整个世界的模拟：关卡 + 玩家 + 死亡统计 + 计时。
 *
 * <p>这个类是<b>纯逻辑</b>：不碰 GL、不碰贴图、不做输入读取，所有输入通过参数传进来。
 * 好处是它可以在没有窗口的环境下被 {@link pixelperil.SelfTest} 反复驱动，验证物理和判定的正确性。
 *
 * <p>核心架构约束（本类型的头号要求）：<b>瞬死瞬复活</b>。
 * 死亡时只是重置玩家状态，绝不重新加载关卡、不读文件、不重建对象树。
 * 玩家一关会死几千次，任何"复活 = 重载场景"的设计都会让重试变得发涩。
 */
public final class Sim {

    public LevelData level;
    public final Player player = new Player();

    /** 死亡次数（本次通关尝试累计）。 */
    public int deaths;
    /** 本关累计逻辑帧数（用于计时）。 */
    public int totalFrames;
    /** 当前这次尝试已经过去的帧数。 */
    public int attemptFrames;

    public boolean cleared;
    /** 通关时定格的时间帧数。 */
    public int clearFrames;

    /** 处于"已死亡、等待复活"的过渡状态（{@link Config#RESPAWN_DELAY_FRAMES} > 0 时才会出现）。 */
    public boolean dead;

    /** 死亡特效：残留位置与剩余帧数。不阻塞复活，纯粹是视觉反馈。 */
    public float deathX;
    public float deathY;
    public int deathFlashTimer;

    /** 出生点被埋进实心方块（作者把关卡做出问题了），加载后给出提示。 */
    public boolean stuckAtSpawn;

    /** 最近一次 1px 边缘修正是否本步发生了（用于调试显示/自检）。 */
    public boolean lastCornerCorrected;

    private int respawnTimer;
    private final Rect hazardProbe = new Rect();
    private final Rect goalRect = new Rect();

    /** 载入关卡并开始全新的一次通关尝试。 */
    public void loadLevel(LevelData lv) {
        this.level = lv;
        restart();
    }

    /** R 键：重开本关（清零死亡数和计时）。 */
    public void restart() {
        deaths = 0;
        totalFrames = 0;
        attemptFrames = 0;
        cleared = false;
        clearFrames = 0;
        dead = false;
        respawnTimer = 0;
        deathFlashTimer = 0;
        deathX = 0f;
        deathY = 0f;
        stuckAtSpawn = false;
        respawn();
    }

    /** 推进一个逻辑步。 */
    public void step(InputState in) {
        if (deathFlashTimer > 0) deathFlashTimer--;

        totalFrames++;
        attemptFrames++;

        if (cleared) return;   // 通关后冻结（HUD 显示的是 clearFrames）

        if (dead) {
            if (--respawnTimer <= 0) respawn();
            return;
        }

        player.step(level, in, Config.STEP_SECONDS);
        lastCornerCorrected = player.cornerCorrectedThisStep;

        if (hazardTouchesPlayer()) {
            kill();
            return;
        }

        if (level.hasGoal) {
            goalRect.set(level.goalX, level.goalY, level.goalW, level.goalH);
            if (goalRect.overlaps(player.body.rect())) {
                cleared = true;
                clearFrames = totalFrames;
            }
        }
    }

    // ------------------------------------------------------------------ 死亡

    /** 触发一次死亡。默认立即复活（{@link Config#RESPAWN_DELAY_FRAMES} = 0）。 */
    public void kill() {
        deaths++;
        deathX = player.body.centerX();
        deathY = player.body.y;
        deathFlashTimer = Config.DEATH_FLASH_FRAMES;
        attemptFrames = 0;

        if (Config.RESPAWN_DELAY_FRAMES > 0) {
            dead = true;
            respawnTimer = Config.RESPAWN_DELAY_FRAMES;
        } else {
            respawn();
        }
    }

    /** 把玩家放回出生点。不分配任何对象。 */
    public void respawn() {
        player.reset(level.spawnX, level.spawnY);
        dead = false;
        respawnTimer = 0;
        stuckAtSpawn = Collision.overlapsSolid(level,
                player.body.x, player.body.y, player.body.w, player.body.h);
    }

    // ------------------------------------------------------------------ 判定

    /**
     * 玩家是否碰到任何尖刺。
     *
     * <p>两步宽容，都是本类型手感的关键：
     * <ol>
     *   <li>玩家碰撞盒四边各内缩 {@link Config#HAZARD_FORGIVENESS} 再做判定；</li>
     *   <li>尖刺自身的判定框比贴图明显小（见 {@link pixelperil.level.Tiles#spikeHitbox}）。</li>
     * </ol>
     */
    private boolean hazardTouchesPlayer() {
        Body b = player.body;
        float f = Config.HAZARD_FORGIVENESS;
        float px = b.x + f;
        float py = b.y + f;
        float pw = b.w - 2f * f;
        float ph = b.h - 2f * f;
        if (pw <= 0f || ph <= 0f) {
            px = b.x; py = b.y; pw = b.w; ph = b.h;
        }
        hazardProbe.set(px, py, pw, ph);

        int c0 = Collision.cell(px + Collision.EPS);
        int c1 = Collision.cell(px + pw - Collision.EPS);
        int r0 = Collision.cell(py + Collision.EPS);
        int r1 = Collision.cell(py + ph - Collision.EPS);

        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) {
                int gid = level.hazardGid(c, r);
                if (gid == 0) continue;
                Rect hb = level.spikeHitbox(gid, c, r);
                if (hb != null && hb.overlaps(hazardProbe)) return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 查询

    /** 用于 HUD 的计时（秒）。 */
    public float displaySeconds() {
        int f = cleared ? clearFrames : totalFrames;
        return f / 60f;
    }
}
