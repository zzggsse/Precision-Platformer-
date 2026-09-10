package pixelperil.sim;

import pixelperil.Config;
import pixelperil.input.InputState;
import pixelperil.level.LevelData;

/**
 * 玩家（"小孩"）的物理与状态机。
 *
 * <p>实现的是本类型需要的那套精确平台跳跃手感：
 * <ul>
 *   <li>固定步长积分，绝不依赖渲染 delta（{@link pixelperil.core.FixedStepper}）；</li>
 *   <li>可变跳跃高度（早松手 = 跳得矮）；</li>
 *   <li>跳跃缓冲（落地前几帧按跳跃键会自动起跳，让高速重试不发涩）；</li>
 *   <li>1px 边缘修正（跳起时蹭到平台角落会被横向推开，见 {@link Collision#moveY}）；</li>
 *   <li>近乎瞬时的水平加速/减速，没有"冰面感"。</li>
 * </ul>
 *
 * <p>整个类<b>每步零分配</b>：不 new 任何对象、不用装箱集合。这类游戏一关要死几千次，
 * 任何每步分配最终都会变成 GC 抖动，表现为偶发的画面顿挫 —— 手感会"发虚"。
 */
public final class Player {

    public final Body body = new Body(Config.PLAYER_W, Config.PLAYER_H);

    public boolean onGround;
    /** 朝向，只影响贴图翻转。 */
    public boolean facingRight = true;

    // --- 供调试显示 ---
    public boolean hitCeilingThisStep;
    public boolean cornerCorrectedThisStep;
    public float cornerCorrection;
    public boolean jumpedThisStep;

    // --- 内部计时器 ---
    private int coyoteTimer;
    private int jumpBufferTimer;
    private boolean jumpCutUsed;
    private int jumpsUsed;

    /** 复活/重开时调用。 */
    public void reset(float x, float y) {
        body.place(x, y);
        onGround = false;
        facingRight = true;
        hitCeilingThisStep = false;
        cornerCorrectedThisStep = false;
        cornerCorrection = 0f;
        jumpedThisStep = false;
        coyoteTimer = 0;
        jumpBufferTimer = 0;
        jumpCutUsed = false;
        jumpsUsed = 0;
    }

    /**
     * 推进一个逻辑步。
     *
     * @param lv 关卡数据
     * @param in 本步的输入快照（边沿已在 {@link InputState#beginStep()} 里算好）
     * @param dt 固定步长（秒），恒为 {@link Config#STEP_SECONDS}
     */
    public void step(LevelData lv, InputState in, float dt) {
        hitCeilingThisStep = false;
        cornerCorrectedThisStep = false;
        cornerCorrection = 0f;
        jumpedThisStep = false;

        // ---------------------------------------------------- 1. 水平
        float target = 0f;
        if (in.left && !in.right) target = -Config.RUN_SPEED;
        else if (in.right && !in.left) target = Config.RUN_SPEED;

        if (target > 0f) facingRight = true;
        else if (target < 0f) facingRight = false;

        float accel = (target == 0f ? Config.RUN_DECEL : Config.RUN_ACCEL) * dt;
        body.vx = approach(body.vx, target, accel);

        // ---------------------------------------------------- 2. 地面/土狼/跳跃缓冲
        boolean wasOnGround = onGround;
        if (onGround) {
            coyoteTimer = Config.COYOTE_FRAMES;
            jumpsUsed = 0;
        } else {
            if (coyoteTimer > 0) coyoteTimer--;
            // 走下悬崖（而非起跳）离开地面，视为已经用掉了地面那一跳，
            // 这样二段跳的计数才正确（MAX_JUMPS=1 时这条分支无影响）。
            if (wasOnGround && jumpsUsed == 0) jumpsUsed = 1;
        }

        if (in.jumpPressed) {
            jumpBufferTimer = Config.JUMP_BUFFER_FRAMES;
        } else if (jumpBufferTimer > 0) {
            jumpBufferTimer--;
        }

        boolean canGroundJump = onGround || coyoteTimer > 0;
        boolean canAirJump = jumpsUsed > 0 && jumpsUsed < Config.MAX_JUMPS;

        if (jumpBufferTimer > 0 && (canGroundJump || canAirJump)) {
            body.vy = Config.JUMP_VELOCITY;
            jumpsUsed = canGroundJump ? 1 : jumpsUsed + 1;
            jumpBufferTimer = 0;
            coyoteTimer = 0;
            onGround = false;
            jumpCutUsed = false;
            jumpedThisStep = true;
        }

        // ---------------------------------------------------- 3. 可变跳跃高度
        // 上升途中松手 -> 把上升速度砍到 JUMP_CUT_VELOCITY，跳得矮。
        if (!in.jumpHeld && !jumpCutUsed && body.vy > Config.JUMP_CUT_VELOCITY) {
            body.vy = Config.JUMP_CUT_VELOCITY;
            jumpCutUsed = true;
        }

        // ---------------------------------------------------- 4. 重力
        body.vy -= Config.GRAVITY * dt;
        if (body.vy < -Config.MAX_FALL_SPEED) body.vy = -Config.MAX_FALL_SPEED;

        // ---------------------------------------------------- 5. 移动 + 碰撞（先横后纵）
        Collision.moveX(lv, body, body.vx * dt);
        Collision.YResult yr = Collision.moveY(lv, body, body.vy * dt);

        hitCeilingThisStep = yr.hitCeiling;
        cornerCorrectedThisStep = yr.cornerCorrected;
        cornerCorrection = yr.correctionAmount;

        // ---------------------------------------------------- 6. 落地判定
        // 用脚下 1px 探针，比"这一步有没有撞到地面"更稳：站在地上时每步都会
        // 因为重力下沉一点点再被顶回来，探针能一直报告 true，不会出现 onGround 抖动。
        onGround = Collision.overlapsSolid(lv, body.x, body.y - 1f, body.w, body.h);
        if (onGround) jumpsUsed = 0;
    }

    /** 朝目标值靠拢，单步最多变化 maxDelta。 */
    private static float approach(float current, float target, float maxDelta) {
        float d = target - current;
        if (d > maxDelta) return current + maxDelta;
        if (d < -maxDelta) return current - maxDelta;
        return target;
    }
}
