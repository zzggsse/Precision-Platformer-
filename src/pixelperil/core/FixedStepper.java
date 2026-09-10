package pixelperil.core;

/**
 * 固定步长累加器。
 *
 * <p>为什么必须有它：如果把物理直接乘渲染 delta，同一段跳在不同帧率/不同机器上
 * 高度就不一样，这类要求像素精确的游戏会彻底失控。这里把时间切成固定的
 * 1/60 秒逻辑步，渲染帧率与逻辑完全解耦。
 *
 * <p>用法：
 * <pre>
 *   stepper.beginFrame(deltaTime);
 *   for (int i = 0; i &lt; stepper.steps(); i++) {
 *       simulation.step(input);
 *   }
 * </pre>
 */
public final class FixedStepper {

    /** 一次卡顿最多累积多少秒。断点调试后恢复不会瞬间补算几千步。 */
    private static final float MAX_FRAME_DELTA = 0.25f;

    private final float step;
    private final int maxSteps;
    private float accumulator;
    private int steps;

    public FixedStepper(float stepSeconds, int maxStepsPerFrame) {
        this.step = stepSeconds;
        this.maxSteps = maxStepsPerFrame;
    }

    /**
     * 把这一帧的真实耗时喂进来，计算出本帧应该跑几个逻辑步。
     * 结果通过 {@link #steps()} 读取。
     */
    public void beginFrame(float deltaSeconds) {
        float delta = deltaSeconds;
        if (delta < 0f) delta = 0f;
        if (delta > MAX_FRAME_DELTA) delta = MAX_FRAME_DELTA;

        accumulator += delta;
        steps = 0;
        while (accumulator >= step && steps < maxSteps) {
            accumulator -= step;
            steps++;
        }
        // 补算已达上限：丢掉积压，宁可让游戏慢一帧，也不要越欠越多（death spiral）。
        if (steps == maxSteps) accumulator = 0f;
    }

    /** 本帧要执行的逻辑步数。 */
    public int steps() {
        return steps;
    }

    /** 丢掉所有积压（读取关卡、窗口失焦恢复后调用）。 */
    public void reset() {
        accumulator = 0f;
        steps = 0;
    }

    /** 当前步长（秒）。 */
    public float stepSeconds() {
        return step;
    }
}
