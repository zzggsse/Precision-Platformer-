package pixelperil.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

/**
 * 每逻辑步一份的输入快照。
 *
 * <p>关键设计：<b>边沿（"这一帧刚按下"）必须在逻辑步里算，不能在渲染帧里算。</b>
 * 一帧可能跑 0 个或 2 个逻辑步，如果用 {@code Gdx.input.isKeyJustTouched} 这类
 * 渲染帧级边沿，跳跃就会随机丢帧 —— 这是平台游戏最隐蔽的 bug 来源。
 *
 * <p>这里只保存"持续按住"的原始状态，边沿由 {@link #endStep()} 用上一步的状态比较得出。
 */
public final class InputState {

    // 持续按住状态（每个逻辑步刷新）
    public boolean left;
    public boolean right;
    public boolean jumpHeld;

    // 边沿状态（每个逻辑步开头算好）
    /** 本逻辑步内跳跃键从松开变为按下。 */
    public boolean jumpPressed;
    /** 本逻辑步内跳跃键从按下变为松开。 */
    public boolean jumpReleased;

    // 一次性动作（在渲染帧内被消费掉，不属于手感相关输入）
    public boolean restart;      // R：重开本关
    public boolean reloadLevel;  // F5：从磁盘重载关卡
    public boolean toggleBoxes;  // F1：显示碰撞盒/判定框
    public boolean quit;         // Esc

    private boolean prevJump;

    /** 从当前键盘状态刷新"持续按住"部分。喂进来的原始状态整帧内相同。 */
    public void pollRaw() {
        left = Gdx.input.isKeyPressed(Input.Keys.LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.A);
        right = Gdx.input.isKeyPressed(Input.Keys.RIGHT)
                || Gdx.input.isKeyPressed(Input.Keys.D);
        jumpHeld = Gdx.input.isKeyPressed(Input.Keys.Z)
                || Gdx.input.isKeyPressed(Input.Keys.SPACE)
                || Gdx.input.isKeyPressed(Input.Keys.UP)
                || Gdx.input.isKeyPressed(Input.Keys.W);
    }

    /** 在逻辑步开头调用，计算本步的跳跃边沿。 */
    public void beginStep() {
        jumpPressed = jumpHeld && !prevJump;
        jumpReleased = !jumpHeld && prevJump;
    }

    /** 在逻辑步末尾调用，记录状态供下一步比较。 */
    public void endStep() {
        prevJump = jumpHeld;
    }

    /** 重新读关卡/重开时清掉边沿，避免"读图瞬间的按键"被当成一次跳跃。 */
    public void clearEdges() {
        prevJump = jumpHeld;
        jumpPressed = false;
        jumpReleased = false;
    }
}
