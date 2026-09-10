# 架构与设计决定

本文解释"为什么这么写"。想知道怎么跑起来看 [README](../README.md)，想改手感看 [PHYSICS.md](PHYSICS.md)。

---

## 一、技术选型

### 为什么是 Java + libGDX

| 需求（精确平台跳跃的硬约束） | libGDX 的对应能力 |
|---|---|
| 输入延迟必须低 | 用 GLFW **轮询**键盘状态，不经过 AWT 事件队列，少一层延迟 |
| 物理必须固定步长 | 主循环完全由你掌控，`ApplicationAdapter.render()` 里自己做累加器 |
| 像素完美渲染 | `Texture.setFilter(Nearest)` + 自己算整数倍视口 |
| 关卡要能用现成编辑器画 | 内置 Tiled（TMX）支持 |
| 每个尖刺判定都要反复微调 | 强类型 + IntelliJ 条件断点 / 变量监视 |
| 一套代码多平台 | 桌面 / Android / HTML5 共用 core |

其中对选型影响最大的一条：**这类游戏一关要死几千次、每个判定框都要来回试**。
**有真正的调试器**（下条件断点、监视变量、重放同一段死亡）价值极高，
这一点 Java + IntelliJ 明显强于 GDScript / GML。

### 为什么不用 Gradle

本机没有 Gradle，而 wrapper 引导要下载约 130MB。这里改成
`tools/MavenFetch.java`（约 120 行）直接读 POM 抓依赖，好处是：

- **依赖集完全可见**：`scripts/fetch-deps.ps1` 里逐行列出了每个坐标和它的来源 POM，
  不是靠构建工具黑盒解析出来的；
- **可复现、可离线重建**：`libs\` 随时删掉重跑；
- **构建只要 1 秒**：`javac` 直接编 20 个文件。

代价是要手工维护依赖列表（升级 libGDX 时要照着 POM 核对一遍，脚本头部注释写了怎么做）。
等工程变大、需要多模块或自动化打包时再迁到 Gradle，`src\` 的目录结构是现成的。

---

## 二、模块划分

```
src/pixelperil/
  Config.java              所有可调参数（手感数值全在这里，改手感只改这个文件）
  Main.java                桌面入口 / 命令行参数解析
  PixelPerilGame.java          主循环、摄像机、热重载、渲染编排
  SelfTest.java            无头自检（31 项断言）

  core/                    与游戏无关的基础设施
    FixedStepper.java      60Hz 累加器
    PixelViewport.java     整数倍缩放 + 居中黑边
    Rect.java              极简浮点矩形

  input/
    InputState.java        每逻辑步一份的输入快照 + 步内跳跃边沿

  level/                   关卡数据
    Tiles.java             瓦片 gid 常量 + 尖刺判定框换算
    LevelData.java         关卡纯数据（扁平 int 数组）
    LevelLoader.java       TMX 解析

  sim/                     纯逻辑，不碰 GL / 贴图 / 输入设备
    Body.java              可移动 AABB 实体
    Collision.java         瓦片碰撞解算（含 1px 边缘修正）
    Player.java            玩家物理与状态机
    Sim.java               世界模拟：关卡 + 玩家 + 死亡统计 + 计时

  render/                  纯绘制，不碰物理
    TileRenderer.java      瓦片绘制（视口裁剪）
    PlayerRenderer.java    玩家与死亡爆散
    DebugRenderer.java     F1 调试视图
    Hud.java               死亡数 / 计时 / 提示 / 通关横幅
    ScreenCapture.java     帧缓冲抓图
```

**分层的硬规矩**：`sim\` 和 `level\` 不许 import 任何 `com.badlogic.gdx.graphics.*`。
这不是洁癖 —— 正因为模拟层不碰 GL，`SelfTest` 才能在没有窗口、没有显卡的环境里
把物理跑成自动化测试。这条规矩一旦破，自检就废了。

---

## 三、六个关键设计决定

### 1. 固定步长，物理绝不乘渲染 delta

`FixedStepper` 把时间切成固定的 1/60 秒逻辑步。同一段跳在任何帧率、任何机器上
结果完全一致 —— 自检里有**逐位比对**的确定性测试（`Float.floatToIntBits`）。

一帧可能跑 0 个、1 个或 2 个逻辑步，所以**跳跃边沿必须在逻辑步里算**，
不能用 `Gdx.input.isKeyJustPressed()` 这种渲染帧级事件（会随机丢帧，
是平台游戏最隐蔽的 bug 来源）。`InputState.beginStep()` 就是干这个的。

卡顿时单帧最多补算 5 步，超出部分直接丢弃：宁可让游戏"慢一下"，
也不要欠账越滚越多（death spiral）。

### 2. 关卡是数据，不是场景树

模拟层用 `LevelData` 的扁平 `int[]`，渲染才查贴图。三个好处：

- TMX 解析**不需要 GL 上下文** → 物理能在无窗口环境自动测试；
- 按格子随机访问是数组下标，不是对象查找；
- 对确定性友好 → 以后加输入回放 / ghost 的前提。

这也是为什么 `LevelLoader` 自己用 `XmlReader` 解析 TMX，而不是用 libGDX 的
`TmxMapLoader`（后者会创建 `Texture`，必须有 GL）。

### 3. 死亡即重置状态，绝不重载关卡

`Sim.kill()` 只做三件事：死亡数 +1、记下特效位置、把玩家摆回出生点。
不读文件、不重建对象树、不分配对象。

玩家一关要死几千次，任何"复活 = 重载场景"的设计都会让重试变得发涩。
截图验证时玩家在 59 帧内死了 19 次 —— 复活确实是零延迟的。

### 4. 每步零分配

`Player.step()` 和 `Collision` 全程不 `new` 任何东西，尖刺判定框用复用的 `Rect`。
`Body.rect()` 返回的是内部缓存对象。

Java 有 GC，而这游戏的死亡频率极高，每步分配最终会变成周期性顿挫 ——
表现为"手感发虚"，而且极难定位。

### 5. 两级判定宽容

- **尖刺侧**：贴图 16x16，判定框按方向内缩 4px（两侧）/ 2px（根部）；
- **玩家侧**：判定尖刺时，8x12 的碰撞盒四边再各缩 1px。

合起来的效果是"擦着刺尖过去不会死"。自检里有一项专门测这个边界：
**少 1px 就活、多 1px 就死**，阈值精确等于从 `Config` 推导出来的位置。

### 6. 精灵比碰撞盒大

精灵 16x16，碰撞盒只有 8x12（`Config.PLAYER_W/H`）。看起来贴着的东西判定上还有余量，
玩家会觉得"我擦过去了"，而不是"明明没碰到却死了"。

碰撞盒必须小于 `TILE`(16)，否则过不去恰好 1 格宽的缝。

---

## 四、渲染管线

**整数倍缩放是像素游戏唯一正确的做法。** `FitViewport` 按浮点比例缩放，
2.5 倍时相邻像素的屏幕宽度会不一致（有的 2px 有的 3px），像素画会出现
"粗细不均 / 抖动"。`PixelViewport` 算 `scale = floor(min(winW/640, winH/360))`，
居中并留黑边。

**摄像机位置取整。** 亚像素的摄像机位置会让整个画面的瓦片落在非整数屏幕像素上，
同样是抖动来源。`PixelPerilGame.updateCamera()` 最后一步是 `Math.round()`。

**每帧先清整个窗口再设游戏视口。** `glViewport` 只影响之后的绘制，
所以清屏要在设置视口之前做，否则窗口缩放后黑边里会残留上一帧的内容。

**绘制顺序**：瓦片三图层（bg → solid → hazard）→ 终点旗 → 死亡爆散 → 玩家 → HUD → 调试框。
死亡爆散画在玩家之前，这样复活后玩家立刻盖在上面。

终点旗是**按 goal 对象矩形画的**，不是图层瓦片。所以在 Tiled 里拖动 goal 对象，
画面会自动跟随，"看到的旗子"和"实际触发的判定框"永远是同一个矩形。

---

## 五、已知限制

- `Sim` 目前只支持一个玩家实体。`Body` 抽象已经为敌人 / 移动平台留好了位置
  （`Collision.moveX/moveY` 接受任意 `Body`）。
- 没有空间分区。可见范围裁剪是按视口算的，够用到几千格；真做超大关卡再考虑分块。
- `LevelLoader` 只支持 TMX 的一个子集：正交、内嵌图集、CSV 编码、非无限地图。
  不支持的输入会给出明确报错而不是静默出错。
- 摄像机是瞬时跟随，没有缓动。

---

## 六、后续扩展建议

| 想加的东西 | 建议做法 |
|---|---|
| 敌人 | 新建 `sim/Enemy.java`，内部持一个 `Body`，在 `Sim.step()` 里一起推进 |
| 移动平台 | 平台本身是 `Body`，但**玩家站上去要跟着走** —— 需要在 `moveY` 落地后把平台位移加到玩家上 |
| 二段跳 | 把 `Config.MAX_JUMPS` 改成 2，物理已经支持（`jumpsUsed` 计数已实现） |
| 输入回放 / ghost | 确定性已经有了，只需每步记录 3 个 boolean 的输入快照 |
| 存档点 | 把 `Sim.respawn()` 的目标从 `level.spawn` 改成"当前存档点"即可 |
| 音效 | 依赖（jlayer/jorbis）已在 `libs\`，用 `Sound`/`Music` 即可 |
| 真像素字体 | 自己做 `.fnt` + PNG，或引入 `gdx-freetype` 在运行时渲染 TTF |
