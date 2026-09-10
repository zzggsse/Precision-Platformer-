package pixelperil;

import pixelperil.input.InputState;
import pixelperil.level.LevelData;
import pixelperil.level.LevelLoader;
import pixelperil.level.Tiles;
import pixelperil.sim.Collision;
import pixelperil.sim.Sim;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 无头自检：不需要窗口、不需要 GL，直接驱动 {@link Sim} 用脚本化输入跑物理，
 * 断言本类型最要紧的那几条性质。
 *
 * <p>为什么这个文件价值很高：手感相关的 bug（跳跃高度漂移、贴地抖动、擦边误判死亡、
 * 1px 修正失效、非确定性）在窗口里用眼睛看几乎发现不了，而且改一次参数就要手动重测一遍。
 * 有了它，调完 {@link Config} 里的数值直接跑一遍就能确认没有回归。
 *
 * <p>运行：{@code scripts\selftest.ps1}（失败时进程退出码非 0）
 */
public final class SelfTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        System.out.println("PixelPeril self-test");
        System.out.println("=================");
        System.out.printf("step=%.4fs gravity=%.1f jumpVel=%.1f cutVel=%.1f runSpeed=%.1f%n",
                Config.STEP_SECONDS, Config.GRAVITY, Config.JUMP_VELOCITY,
                Config.JUMP_CUT_VELOCITY, Config.RUN_SPEED);
        float analytic = Config.JUMP_VELOCITY * Config.JUMP_VELOCITY / (2f * Config.GRAVITY);
        System.out.printf("analytic full-jump height = %.2f px (%.2f tiles)%n%n",
                analytic, analytic / Config.TILE);

        testRestingIsStable();
        testJumpHeight();
        testVariableJumpHeight();
        testHorizontalReach();
        testCornerCorrectionPasses();
        testCornerCorrectionRejectsTooDeep();
        testWallStopsPlayer();
        testSpikeForgivenessBoundary();
        testDeathAndInstantRespawn();
        testDeterminism();
        testRealLevelLoadsAndPlays();

        System.out.println();
        System.out.println("=================");
        System.out.printf("passed=%d failed=%d%n", passed, failed);
        if (failed > 0) {
            System.out.println("SELF-TEST FAILED");
            System.exit(1);
        }
        System.out.println("SELF-TEST OK");
    }

    // ================================================================== 测试用例

    /**
     * 落地后必须完全静止：y 恒等于地面顶面、vy 恒为 0。
     *
     * <p>这条看着无聊，但它能抓住最恶心的手感问题 —— 贴地时的亚像素抖动。
     * 只要碰撞解算不是"精确对齐到瓦片边界"，玩家站着不动画面就会一直细微上下跳。
     */
    private static void testRestingIsStable() {
        Sim sim = newSim(flatLevel(20, 12, 2, 48f));
        InputState in = new InputState();

        // 先随便跑 60 步让它落稳
        for (int i = 0; i < 60; i++) step(sim, in, false, false, false);

        float expectedY = 2 * Config.TILE;
        boolean stable = true;
        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            step(sim, in, false, false, false);
            if (sim.player.body.y != expectedY || sim.player.body.vy != 0f) {
                stable = false;
                detail.append(String.format(" [step %d y=%.6f vy=%.6f]", i, sim.player.body.y, sim.player.body.vy));
                if (detail.length() > 120) break;
            }
        }
        check("resting is pixel-stable (no jitter)", stable,
                "y must stay exactly " + expectedY + " with vy=0" + detail);
        check("resting reports onGround", sim.player.onGround, "onGround should be true");
    }

    /** 满跳高度必须能上 3 格、上不了 4 格 —— 这是关卡设计的台阶尺寸依据。 */
    private static void testJumpHeight() {
        Sim sim = newSim(flatLevel(20, 16, 2, 48f));
        InputState in = new InputState();
        for (int i = 0; i < 30; i++) step(sim, in, false, false, false);

        float startY = sim.player.body.y;
        float apex = startY;
        for (int i = 0; i < 90; i++) {
            step(sim, in, false, false, true);   // 一直按住跳跃
            apex = Math.max(apex, sim.player.body.y);
        }
        float rise = apex - startY;
        float tiles = rise / Config.TILE;
        System.out.printf("  [measured] full jump rise = %.2f px (%.2f tiles)%n", rise, tiles);

        check("full jump clears a 3-tile step", rise > 3 * Config.TILE,
                String.format("rise %.2f must be > %d", rise, 3 * Config.TILE));
        check("full jump does not clear 4 tiles", rise < 4 * Config.TILE,
                String.format("rise %.2f must be < %d", rise, 4 * Config.TILE));
        check("measured jump height is close to analytic", Math.abs(rise - analyticRise()) < 6f,
                String.format("rise %.2f vs analytic %.2f", rise, analyticRise()));
    }

    private static float analyticRise() {
        return Config.JUMP_VELOCITY * Config.JUMP_VELOCITY / (2f * Config.GRAVITY);
    }

    /** 可变跳跃高度：轻点必须明显比按住矮，但又要够得着 1 格台阶。 */
    private static void testVariableJumpHeight() {
        Sim full = newSim(flatLevel(20, 16, 2, 48f));
        Sim tap = newSim(flatLevel(20, 16, 2, 48f));
        InputState inFull = new InputState();
        InputState inTap = new InputState();
        for (int i = 0; i < 30; i++) {
            step(full, inFull, false, false, false);
            step(tap, inTap, false, false, false);
        }
        float fullApex = full.player.body.y;
        float tapApex = tap.player.body.y;
        for (int i = 0; i < 90; i++) {
            step(full, inFull, false, false, true);
            step(tap, inTap, false, false, i == 0);   // 只按 1 帧
            fullApex = Math.max(fullApex, full.player.body.y);
            tapApex = Math.max(tapApex, tap.player.body.y);
        }
        float fullRise = fullApex - 2 * Config.TILE;
        float tapRise = tapApex - 2 * Config.TILE;
        System.out.printf("  [measured] tap jump rise  = %.2f px (%.2f tiles)%n",
                tapRise, tapRise / Config.TILE);

        check("tap jump is clearly lower than held jump", tapRise < fullRise * 0.65f,
                String.format("tap %.2f vs full %.2f", tapRise, fullRise));
        check("tap jump still clears a 1-tile step", tapRise > Config.TILE,
                String.format("tap rise %.2f must be > %d", tapRise, Config.TILE));
    }

    /**
     * 实测水平跳跃距离 —— 这是<b>关卡设计时最需要的数字</b>：
     * 知道"平跳能跨几个格子、升高 3 格时还能跨多远"，画图时才有依据。
     * 这里只报告数值并做宽松下限断言，避免调参后测试变脆。
     */
    private static void testHorizontalReach() {
        float flat = measureReach(0);
        float rise3 = measureReach(3 * Config.TILE);
        System.out.printf("  [measured] flat jump reach   = %.1f px (%.2f tiles)%n",
                flat, flat / Config.TILE);
        System.out.printf("  [measured] +3-tile rise reach= %.1f px (%.2f tiles)%n",
                rise3, rise3 / Config.TILE);

        check("a 5-tile flat gap is crossable", flat > 5 * Config.TILE,
                String.format("reach %.1f must be > %d", flat, 5 * Config.TILE));
        check("a 2-tile gap is crossable while rising 3 tiles", rise3 > 2 * Config.TILE,
                String.format("reach %.1f must be > %d", rise3, 2 * Config.TILE));
        check("rising reduces horizontal reach", rise3 < flat,
                String.format("rise3 %.1f should be < flat %.1f", rise3, flat));
    }

    /**
     * 先跑满速，然后起跳（按住跳跃键 + 一直向右），返回从起跳点到
     * "最后一次处于 riseHeight 以上"时的水平位移。
     *
     * <p>取"最后一次"是因为玩家会先上升穿过这个高度、再下落穿过它，
     * 真正能落上去的位置是下落那一次 —— 也就是能碰到的最远点。
     */
    private static float measureReach(float riseHeight) {
        Sim sim = newSim(flatLevel(200, 16, 2, 48f));
        InputState in = new InputState();

        // 先跑满速（避免加速过程吃掉水平距离）
        for (int i = 0; i < 40; i++) step(sim, in, false, true, false);

        float x0 = sim.player.body.x;
        float y0 = sim.player.body.y;
        float threshold = y0 + riseHeight;
        float best = 0f;

        step(sim, in, false, true, true);          // 起跳这一帧
        for (int i = 0; i < 200; i++) {
            step(sim, in, false, true, true);
            if (sim.player.body.y >= threshold) {
                best = sim.player.body.x - x0;
            }
            if (i > 2 && sim.player.onGround) break;   // 落回地面了
        }
        return best;
    }

    /**
     * 1px 边缘修正：头顶以很小的重叠蹭到方块角落时，应该被横向推开并继续上升。
     * 关掉这个功能后这个测试会失败，属于预期。
     */
    private static void testCornerCorrectionPasses() {
        // 地面 2 行；方块悬在 col 3 / row 4（y 64..80，x 48..64）
        int overlap = 2;
        float spawnX = 48f - Config.PLAYER_W + overlap;   // 右边缘探进方块 2px
        LevelData lv = flatLevel(20, 16, 2, spawnX);
        lv.solid[4 * 20 + 3] = Tiles.BLOCK;

        Sim sim = newSim(lv);
        InputState in = new InputState();
        boolean corrected = false;
        float apex = sim.player.body.y;
        for (int i = 0; i < 90; i++) {
            step(sim, in, false, false, true);
            if (sim.lastCornerCorrected) corrected = true;
            apex = Math.max(apex, sim.player.body.y);
        }
        float blockBottom = 4 * Config.TILE;   // 64
        check("corner correction triggers on a small ceiling graze", corrected,
                "expected a horizontal nudge while rising");
        check("corner correction lets the player rise past the block",
                apex + Config.PLAYER_H > blockBottom + 4f,
                String.format("apex %.2f + h %.0f must exceed block bottom %.0f",
                        apex, Config.PLAYER_H, blockBottom));
    }

    /** 重叠太深时不应该被"修正"过去，否则等于给了穿墙。 */
    private static void testCornerCorrectionRejectsTooDeep() {
        int overlap = (int) Config.CORNER_CORRECTION + 6;   // 远超修正上限
        float spawnX = 48f - Config.PLAYER_W + overlap;
        LevelData lv = flatLevel(20, 16, 2, spawnX);
        lv.solid[4 * 20 + 3] = Tiles.BLOCK;

        Sim sim = newSim(lv);
        InputState in = new InputState();
        float apex = sim.player.body.y;
        boolean hitCeiling = false;
        for (int i = 0; i < 90; i++) {
            step(sim, in, false, false, true);
            if (sim.player.hitCeilingThisStep) hitCeiling = true;
            apex = Math.max(apex, sim.player.body.y);
        }
        float headMax = apex + Config.PLAYER_H;
        float blockBottom = 4 * Config.TILE;
        check("deep ceiling overlap is not corrected through", hitCeiling,
                "expected the player to actually bump the ceiling");
        check("deep ceiling overlap blocks the player", headMax <= blockBottom + 0.01f,
                String.format("head reached %.2f but block bottom is %.0f", headMax, blockBottom));
    }

    /** 撞墙必须停住，不能穿过去，也不能抖动。 */
    private static void testWallStopsPlayer() {
        LevelData lv = flatLevel(20, 12, 2, 32f);
        int wallCol = 10;
        for (int r = 2; r < 12; r++) lv.solid[r * 20 + wallCol] = Tiles.BLOCK;
        float wallFace = wallCol * Config.TILE;   // 160

        Sim sim = newSim(lv);
        InputState in = new InputState();
        float maxRight = 0f;
        for (int i = 0; i < 300; i++) {
            step(sim, in, false, true, false);   // 一直往右跑
            maxRight = Math.max(maxRight, sim.player.body.right());
        }
        check("running into a wall stops exactly at its face", maxRight == wallFace,
                String.format("right edge reached %.4f, wall face is %.1f", maxRight, wallFace));
        check("player never overlaps the wall", maxRight <= wallFace + 0.0001f,
                String.format("right edge %.4f exceeded %.1f", maxRight, wallFace));
    }

    /**
     * 尖刺宽容度的边界：判定阈值必须精确等于从 {@link Config} 推导出来的位置。
     *
     * <p>推导：尖刺判定框左边 = tileX + SPIKE_SIDE_INSET；
     * 玩家探测框右边缘 = x + PLAYER_W - HAZARD_FORGIVENESS；
     * 发生死亡的条件是探测框右边缘严格大于判定框左边。
     * 所以最小致死 x = floor(tileX + inset - PLAYER_W + FORGIVENESS) + 1。
     */
    private static void testSpikeForgivenessBoundary() {
        int spikeCol = 5;
        int spikeRow = 2;
        float tileX = spikeCol * Config.TILE;
        int expectedMin = (int) Math.floor(
                tileX + Config.SPIKE_SIDE_INSET - Config.PLAYER_W + Config.HAZARD_FORGIVENESS) + 1;

        boolean diedBefore = diesAt(spikeCol, spikeRow, expectedMin - 1);
        boolean diedAt = diesAt(spikeCol, spikeRow, expectedMin);

        System.out.printf("  [measured] spike kill threshold x >= %d (tile x=%.0f, inset=%.1f)%n",
                expectedMin, tileX, Config.SPIKE_SIDE_INSET);
        check("player one pixel short of the spike hitbox survives", !diedBefore,
                "expected survival at x=" + (expectedMin - 1));
        check("player one pixel inside the spike hitbox dies", diedAt,
                "expected death at x=" + expectedMin);
    }

    private static boolean diesAt(int spikeCol, int spikeRow, float playerX) {
        LevelData lv = flatLevel(20, 12, 2, playerX);
        lv.hazard[spikeRow * 20 + spikeCol] = Tiles.SPIKE_UP;
        Sim sim = newSim(lv);
        InputState in = new InputState();
        // 直接摆到指定 x，然后空跑几帧看是否触发死亡
        sim.player.body.place(playerX, 2 * Config.TILE);
        for (int i = 0; i < 5; i++) step(sim, in, false, false, false);
        return sim.deaths > 0;
    }

    /** 死亡必须立刻复活到出生点，并且死亡计数正确。 */
    private static void testDeathAndInstantRespawn() {
        float spawnX = 32f;
        LevelData lv = flatLevel(20, 12, 2, spawnX);
        lv.hazard[2 * 20 + 5] = Tiles.SPIKE_UP;

        Sim sim = newSim(lv);
        InputState in = new InputState();
        for (int i = 0; i < 30; i++) step(sim, in, false, false, false);
        check("spawn is safe (no death while idle)", sim.deaths == 0,
                "deaths=" + sim.deaths);

        // 走进尖刺
        for (int i = 0; i < 120 && sim.deaths == 0; i++) step(sim, in, false, true, false);
        check("walking into a spike kills the player", sim.deaths == 1,
                "deaths=" + sim.deaths);
        check("respawn is instant (player already back at spawn)",
                Math.abs(sim.player.body.x - spawnX) < 0.001f
                        && Math.abs(sim.player.body.y - 2 * Config.TILE) < 0.001f,
                String.format("player at (%.3f, %.3f), spawn is (%.1f, %d)",
                        sim.player.body.x, sim.player.body.y, spawnX, 2 * Config.TILE));
        check("death flash is armed", sim.deathFlashTimer > 0,
                "deathFlashTimer=" + sim.deathFlashTimer);
        check("sim is not in a blocking dead state",
                !sim.dead, "RESPAWN_DELAY_FRAMES=0 should respawn in the same step");
    }

    /**
     * 确定性：同一段脚本化输入跑两遍，结果必须逐位相同。
     *
     * <p>这是以后能加"输入回放 / ghost / 速通验证"的前提，也是"物理不依赖渲染帧率"的证明。
     */
    private static void testDeterminism() {
        float[] a = runScript();
        float[] b = runScript();
        boolean same = true;
        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            if (Float.floatToIntBits(a[i]) != Float.floatToIntBits(b[i])) {
                same = false;
                detail.append(String.format(" [%d: %s vs %s]", i, a[i], b[i]));
            }
        }
        check("identical input produces bit-identical results", same,
                "divergence:" + detail);
    }

    /** 返回 [x, y, vx, vy, deaths, totalFrames] */
    private static float[] runScript() {
        LevelData lv = flatLevel(24, 16, 2, 40f);
        lv.solid[4 * 24 + 8] = Tiles.BLOCK;
        lv.solid[4 * 24 + 9] = Tiles.BLOCK;
        lv.hazard[2 * 24 + 14] = Tiles.SPIKE_UP;
        lv.hazard[2 * 24 + 15] = Tiles.SPIKE_UP;

        Sim sim = newSim(lv);
        InputState in = new InputState();
        int seed = 12345;
        for (int i = 0; i < 900; i++) {
            seed = seed * 1103515245 + 12345;
            int v = (seed >>> 16) & 0x7fff;
            boolean right = (v % 5) == 0;
            boolean left = (v % 13) == 0;
            boolean jump = (v % 7) == 0;
            step(sim, in, left, right, jump);
        }
        return new float[]{
                sim.player.body.x, sim.player.body.y,
                sim.player.body.vx, sim.player.body.vy,
                sim.deaths, sim.totalFrames
        };
    }

    /** 真关卡冒烟测试：确认 TMX 能读进来、出生点安全、能站住、终点存在。 */
    private static void testRealLevelLoadsAndPlays() throws Exception {
        Path tmx = Paths.get("assets", "levels", "level1.tmx");
        if (!Files.exists(tmx)) {
            check("assets/levels/level1.tmx exists", false,
                    "run scripts\\gen-level.ps1 (and gen-assets.ps1) first");
            return;
        }
        LevelData lv = LevelLoader.load(tmx);
        check("level1.tmx parses", true, "");
        check("level1 has a solid layer", lv.solid.length > 0, "");
        check("level1 has a goal", lv.hasGoal, "");
        check("level1 has hazards", countNonZero(lv.hazard) > 0,
                "hazard tiles=" + countNonZero(lv.hazard));

        Sim sim = newSim(lv);
        InputState in = new InputState();
        for (int i = 0; i < 120; i++) step(sim, in, false, false, false);
        check("level1 spawn settles on the ground", sim.player.onGround && sim.deaths == 0,
                String.format("onGround=%s deaths=%d pos=(%.2f, %.2f)",
                        sim.player.onGround, sim.deaths, sim.player.body.x, sim.player.body.y));
        check("level1 spawn is not inside geometry", !sim.stuckAtSpawn && !Collision.overlapsSolid(
                        lv, sim.player.body.x, sim.player.body.y, sim.player.body.w, sim.player.body.h),
                "spawn overlaps solid tiles");

        // 往右跑一段，确认不会被卡住（也顺带覆盖真实关卡的碰撞数据）
        float before = sim.player.body.x;
        for (int i = 0; i < 240; i++) step(sim, in, false, true, false);
        boolean moved = sim.player.body.x > before + Config.TILE;
        check("level1 player can run right from spawn", moved,
                String.format("moved from %.2f to %.2f", before, sim.player.body.x));
    }

    // ================================================================== 基础设施

    private static int countNonZero(int[] a) {
        int n = 0;
        for (int v : a) if (v != 0) n++;
        return n;
    }

    /** 造一个地面平整的测试关卡，行 0..floorRows-1 是实心。 */
    private static LevelData flatLevel(int w, int h, int floorRows, float spawnX) {
        LevelData lv = new LevelData(w, h, "tiles/tiles.png", 1,
                spawnX, floorRows * (float) Config.TILE, false, 0f, 0f, 0f, 0f);
        for (int r = 0; r < floorRows; r++) {
            for (int c = 0; c < w; c++) {
                lv.solid[r * w + c] = Tiles.DIRT;
            }
        }
        return lv;
    }

    private static Sim newSim(LevelData lv) {
        Sim sim = new Sim();
        sim.loadLevel(lv);
        return sim;
    }

    /**
     * 推进一个逻辑步，直接给输入状态赋值。
     *
     * <p>注意这里<b>没有</b>调用 {@link InputState#pollRaw()}（那个要读 Gdx.input），
     * 而是手动设置"按住"状态 —— 跳跃边沿由 {@code beginStep/endStep} 自己算出来，
     * 所以这条路径和真实游戏完全一致。
     */
    private static void step(Sim sim, InputState in, boolean left, boolean right, boolean jump) {
        in.left = left;
        in.right = right;
        in.jumpHeld = jump;
        in.beginStep();
        sim.step(in);
        in.endStep();
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name + (detail.isEmpty() ? "" : "  -- " + detail));
        }
    }
}
