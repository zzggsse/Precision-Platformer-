package pixelperil;

/**
 * 全局可调参数。所有手感相关的数值都集中在这里，调手感只改这个文件。
 *
 * <p>本作是一款<b>精确平台跳跃游戏</b>（precision platformer），玩法参照
 * I Wanna 系一类高难度跳跃游戏：一击即死、瞬时重试、判定精确到像素。
 * 本文档下文提到的"本类型"即指这一类游戏。
 *
 * <p>单位约定：长度 = 像素（1 格 = {@link #TILE} px），速度 = px/秒，加速度 = px/秒²，
 * 重力 = px/秒²。定点步长固定 {@link #STEP_SECONDS}（60Hz），物理绝不依赖渲染 delta。
 *
 * <p>命名约定：世界坐标 y 轴向上，原点在关卡左下角。<b>向上 = y 增大</b>，
 * 因此跳跃初速度是正数、重力是负数。
 */
public final class Config {

    private Config() {}

    // ------------------------------------------------------------ 显示

    /** 虚拟分辨率（像素画布）。窗口按整数倍缩放到这个画布上。 */
    public static final int VIRTUAL_W = 640;
    public static final int VIRTUAL_H = 360;

    public static final int WINDOW_W = 1280;
    public static final int WINDOW_H = 720;

    /** 关卡范围之外（天空）的底色。 */
    public static final float BG_R = 0.07f;
    public static final float BG_G = 0.06f;
    public static final float BG_B = 0.10f;

    // ------------------------------------------------------------ 时间

    /** 定点步长：60Hz。改这个会同时改变所有物理手感。 */
    public static final float STEP_SECONDS = 1f / 60f;

    /**
     * 单帧最多补算几个逻辑步。防止卡顿（或断点调试）后一次性补算上千步造成雪崩，
     * 超出部分直接丢弃（游戏会"慢一下"而不是卡死）。
     */
    public static final int MAX_STEPS_PER_FRAME = 5;

    // ------------------------------------------------------------ 关卡

    /** 瓦片边长（必须和 tiles.png 里的瓦片尺寸一致）。 */
    public static final int TILE = 16;

    // ------------------------------------------------------------ 玩家碰撞盒

    /**
     * 玩家碰撞盒尺寸。刻意小于 1 格（16px）：
     * 这样才能穿过恰好 1 格宽的缝隙，也让贴边操作不会因为太大而失败。
     * 精灵图是 16x16，碰撞盒只覆盖中间部分。
     */
    public static final float PLAYER_W = 8f;
    public static final float PLAYER_H = 12f;

    /**
     * 精灵相对碰撞盒左下角的偏移。精灵是 16x16，碰撞盒只有 8x12，
     * 所以精灵要往外偏一点才能视觉居中。
     *
     * <p>精灵比碰撞盒大是有意的：看起来贴着的东西判定上还有余量，
     * 玩家会觉得"我擦过去了"，而不是"明明没碰到却死了"。
     */
    public static final float SPRITE_OFFSET_X = (PLAYER_W - TILE) / 2f;   // -4
    public static final float SPRITE_OFFSET_Y = -2f;

    // ------------------------------------------------------------ 移动

    /** 最大水平速度。2.4 px/帧 = 144 px/s。 */
    public static final float RUN_SPEED = 144f;

    /**
     * 水平加速度。144/3600 = 0.04s ≈ 2.4 帧到满速，手感接近"立即"但没有瞬移感。
     * 想要绝对瞬时（更硬核）就调到 10000 以上。
     */
    public static final float RUN_ACCEL = 3600f;

    /** 左右方向键都不按时，水平速度归零的减速度。 */
    public static final float RUN_DECEL = 3600f;

    // ------------------------------------------------------------ 跳跃

    /**
     * 重力加速度。1008 px/s² = 0.28 px/帧²。
     * 与 {@link #JUMP_VELOCITY} 配合得到：跳跃高度 3.5 格、上升到顶点 20 帧、滞空 40 帧。
     */
    public static final float GRAVITY = 1008f;

    /** 起跳初速度（向上为正）。336 px/s = 5.6 px/帧。 */
    public static final float JUMP_VELOCITY = 336f;

    /**
     * 可变跳跃高度：松开跳跃键时，若还在上升且速度大于此值，就把速度砍到这个值。
     * 越小 = 轻点跳得越矮。
     *
     * <p>160 的选择依据：满跳约 3.3 格，轻点约 1.2 格 —— 大小跳差异明显，
     * 但轻点仍然够得上 1 格台阶（本类型里经常需要小跳微调）。
     * 用 {@code scripts\selftest.ps1} 可以实测这两个高度。
     */
    public static final float JUMP_CUT_VELOCITY = 160f;

    /** 下落最大速度（终端速度）。540 px/s = 9 px/帧，必须小于 {@link #TILE} 以免穿墙。 */
    public static final float MAX_FALL_SPEED = 540f;

    /**
     * 跳跃缓冲帧数：落地前这几帧内按过跳跃键，落地瞬间会自动起跳。
     * 本类型里这是"重试手感"的关键，删掉它会让高速连跳变得很烦。
     */
    public static final int JUMP_BUFFER_FRAMES = 5;

    /**
     * 土狼时间帧数：离开地面后还能跳的宽限帧。
     * 本类型传统是 0（严格），这里默认保持 0 以保留精确性。
     */
    public static final int COYOTE_FRAMES = 0;

    /**
     * 允许的跳跃次数。1 = 纯单跳（正统做法）。
     * 改成 2 就变成二段跳关卡（很多同类的 gimmick 关就是这么做的）。
     */
    public static final int MAX_JUMPS = 1;

    // ------------------------------------------------------------ 1px 边缘修正

    /**
     * 顶头时的横向修正上限（像素）。跳起时若头顶只差这么点就撞到天花板角落，
     * 会自动把玩家横向推一下让他擦过去，避免"差 1px 上不去"的挫败感。
     * 设为 0 即关闭（更硬核）。
     */
    public static final float CORNER_CORRECTION = 3f;

    // ------------------------------------------------------------ 死亡与复活

    /** 死亡到复活的延迟帧数。0 = 瞬死瞬复活。 */
    public static final int RESPAWN_DELAY_FRAMES = 0;

    /** 死亡特效（在原死亡位置残留的爆散）持续帧数。不阻塞复活。 */
    public static final int DEATH_FLASH_FRAMES = 10;

    /**
     * 尖刺判定的侧向内缩（像素）。尖刺贴图是 16x16，但判定框比它窄，
     * 这样"擦着尖刺边过去"不会死 —— 本类型手感的关键之一。
     */
    public static final float SPIKE_SIDE_INSET = 4f;

    /** 尖刺判定的底部内缩（像素）。 */
    public static final float SPIKE_BASE_INSET = 2f;

    /** 玩家碰撞盒对尖刺的额外宽容（像素，四边各缩这么多再做判定）。 */
    public static final float HAZARD_FORGIVENESS = 1f;

    // ------------------------------------------------------------ 摄像机

    /** 摄像机跟随玩家的水平偏移（负数 = 玩家偏左，能看到更多前方）。 */
    public static final float CAM_LOOK_AHEAD = 24f;
}
