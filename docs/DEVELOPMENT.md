# 开发指南

面向改代码的人。跑起来看 [README](../README.md)，改手感看 [PHYSICS.md](PHYSICS.md)。

---

## 一、无头自检

`scripts\selftest.ps1` 不开窗口、不需要 GL、不需要显卡，直接驱动 `Sim` 用脚本化输入
跑物理并断言 **31 项**性质。失败时退出码非 0，可以直接挂到 CI 上。

```powershell
scripts\selftest.ps1
```

输出示例（节选）：

```
analytic full-jump height = 56.00 px (3.50 tiles)
  PASS  resting is pixel-stable (no jitter)
  [measured] full jump rise = 53.20 px (3.33 tiles)
  PASS  full jump clears a 3-tile step
  PASS  full jump does not clear 4 tiles
  [measured] tap jump rise  = 16.72 px (1.04 tiles)
  [measured] flat jump reach   = 89.6 px (5.60 tiles)
  [measured] +3-tile rise reach= 56.0 px (3.50 tiles)
  [measured] spike kill threshold x >= 78 (tile x=80, inset=4.0)
  PASS  identical input produces bit-identical results
  ...
passed=31 failed=0
SELF-TEST OK
```

### 为什么这个文件价值很高

手感相关的 bug —— **跳跃高度漂移、贴地亚像素抖动、擦边误判死亡、1px 修正失效、
非确定性** —— 在窗口里用眼睛几乎发现不了，而且改一次参数就要手动重测一遍。

自检能抓住它们，尤其是这几项设计上刻意做的：

| 断言 | 抓住什么问题 |
|---|---|
| 落地后 `y`/`vy` **完全恒定**（不是"差不多"） | 贴地亚像素抖动 —— 站着不动画面一直细微上下跳 |
| 尖刺致死阈值 = 从 `Config` **推导**出来的位置 | 判定宽容度被意外改动的回归 |
| 同一输入跑两遍 `Float.floatToIntBits` **逐位相同** | 物理意外依赖了渲染帧率或系统状态 |
| 1px 修正：小重叠能过、大重叠**绝不**能过 | 修正幅度被调大后变成"穿墙外挂" |

阈值是**从 `Config` 算出来的**而不是硬编码常数，所以调参后测试不会假失败，
但会抓住非预期的行为变化。

### 加新测试

在 `SelfTest.java` 的 `main()` 里加一行调用，用现成的工具方法：

```java
private static void testSomething() {
    LevelData lv = flatLevel(20, 12, 2, 48f);   // 宽/高/地面行数/出生点 x
    lv.solid[4 * 20 + 3] = Tiles.BLOCK;         // 放个方块（注意行 0 在最下面）
    lv.hazard[2 * 20 + 5] = Tiles.SPIKE_UP;     // 放个尖刺

    Sim sim = newSim(lv);
    InputState in = new InputState();
    for (int i = 0; i < 60; i++) step(sim, in, false, true, false);   // 左/右/跳

    check("描述", 实际 == 期望, "失败时打印什么");
}
```

注意 `step()` 里**没有**调用 `InputState.pollRaw()`（那个要读 `Gdx.input`），
而是手动设 `jumpHeld`，跳跃边沿由 `beginStep/endStep` 自己算 —— 所以这条路径和真实游戏完全一致。

---

## 二、构建

> **关于顶层目录名**：现在的目录是 `D:\iwanna`，那是立项时的历史遗留，
> 和项目名 PixelPeril 无关。所有脚本都用 `$PSScriptRoot` 定位路径，
> **把整个目录改名或挪到别处都不影响运行**。唯一的外部路径依赖是
> `scripts\env.ps1` 里的 JDK 兜底路径 `D:\java\JDK21`（可用 `PIXELPERIL_JDK` 覆盖）。

```powershell
scripts\build.ps1         # javac 编译到 out\
scripts\build.ps1 之外没有别的构建系统
```

`build.ps1` 用 `javac @argfile`（文件列表写到 `out\sources.txt`）避免命令行长度限制。

**`-encoding UTF-8` 是必须的**：源码里有中文注释，不给这个参数 javac 会用平台代码页
（本机 GBK）去读，直接报错。

### 用 IDE 打开

IntelliJ IDEA：`File → Open` 选工程根目录 → 它会当成普通 Java 工程。
需要手动加一个依赖：`File → Project Structure → Libraries → +` → 选整个 `libs\` 目录。
运行配置的 VM options 填 `-Dfile.encoding=UTF-8`，working directory 设为工程根目录。

（工程里没有 `.idea`，也没有 `build.gradle`，所以不会自动识别 —— 这是刻意的，
免得把 IDE 的本地配置提交进仓库。）

---

## 三、调试手段

| 手段 | 用法 |
|---|---|
| 判定框可视化 | 游戏里按 `F1`，或 `scripts\run.ps1 -DebugBoxes` |
| 自动化截图 | `scripts\run.ps1 -Screenshot out.png -ShotFrame 40` |
| 无头物理测试 | `scripts\selftest.ps1` |
| 关卡热重载 | 游戏里按 `F5` |
| 逐帧调试 | 在 `Sim.step()` 下断点；固定步长意味着**每帧状态完全可复现** |

`--screenshot` 抓的是游戏区域（不含黑边），抓完自动退出，退出码 0。
适合做"改完渲染拍一张确认没画歪"的自动化视觉验收。

---

## 四、代码规范

- **`sim\` 和 `level\` 不许 import `com.badlogic.gdx.graphics.*`。**
  这是让自检能跑起来的根本前提，破坏它自检就废了（见 [ARCHITECTURE.md](ARCHITECTURE.md)）。
- **热循环里不分配。** `Player.step()` / `Collision` / `Sim.step()` 全程不 `new`。
  需要临时对象就用可复用的成员变量（`Rect`、`Collision.YResult` 都是这个套路）。
- **可调数值一律进 `Config.java`**，不要在逻辑里写魔数。
- **注释写"为什么"，不写"是什么"。** `x += 1; // x 加 1` 没有价值；
  "必须在逻辑步里算边沿，否则会丢帧" 才有。
- **`.ps1` 文件必须纯 ASCII**（原因见下面的环境坑）。
- 中文注释放在 `.java` 里（`javac -encoding UTF-8`），**运行时的控制台输出保持 ASCII**
  （Windows 控制台是 CP936，中文会乱码）。

---

## 五、环境坑（改脚本前必看）

这些都是踩过的，写在这是为了避免重复踩：

### 1. `.ps1` 文件必须纯 ASCII

Windows PowerShell 会按 **ANSI 代码页**解析脚本文件。中文注释的 UTF-8 字节被
当 GBK 读之后，可能产生出引号、反引号之类的字符，**破坏语法解析**，
报出来的是让人完全看不懂的"意外的标记"错误。

→ 脚本里写英文注释，中文注释放 `.java`（`javac -encoding UTF-8` 可控）和文档里。

### 2. 别用 PowerShell 保留自动变量做参数名

```powershell
# ✗ 静默绑定失败，$inPath 会变成工程根目录
param([string]$Input = 'assets\levels\level1.ascii')

# ✓
param([string]$AsciiFile = 'assets\levels\level1.ascii')
```

`$Input`、`$Args`、`$Error` 之类都是保留的。绑定失败的报错是
`AccessDeniedException: <工程根目录>`（因为对目录做了 `readAllLines`），
和真实原因完全对不上，极难定位。

### 3. `"$var:suffix"` 会被当成作用域限定符

```powershell
"com.badlogicgames.gdx:gdx-platform:$GdxVersion:natives-desktop"    # ✗
"com.badlogicgames.gdx:gdx-platform:${GdxVersion}:natives-desktop"  # ✓
```

PowerShell 把 `$GdxVersion:natives` 解析成"作用域 `natives` 里的变量 `GdxVersion`"。
报出来的是 `all repos failed for .../gdx-platform/-desktop/...`（版本号不见了），
一看就知道是变量没展开。

### 4. `XmlReader.getAttribute(name)` 单参数版会抛异常

libGDX 的 `com.badlogic.gdx.utils.XmlReader.Element`：

```java
element.getAttribute("source")          // 属性不存在 -> 抛 GdxRuntimeException
element.getAttribute("source", null)    // 属性不存在 -> 返回 null
```

解析 TMX 时属性经常是可选的，**一律用带默认值的重载**。

### 5. libGDX 1.14 的 `SharedLibraryLoader` 不在 core 里

1.14 把它拆到了独立构件 `com.badlogicgames.gdx:gdx-jnigen-loader`，
而它是 **`gdx` 的传递依赖**，不在 `gdx-backend-lwjgl3` 的 POM 里。

→ 只照抄 backend 的 POM 就会漏掉它，加载原生库时报
`NoClassDefFoundError: com/badlogic/gdx/utils/SharedLibraryLoader`。
`scripts\fetch-deps.ps1` 头部注释里写明了这一点。

### 6. `JAVA_HOME` 与 PATH 上的 JDK 版本不一致

本机 `JAVA_HOME` 指向 `D:\JSP\jdk-19_windows-x64_bin\jdk-19.0.2`（**已 EOL**），
而 PATH 上是 JDK 21。Gradle/Maven 这类工具优先读 `JAVA_HOME`，会用到 19。

→ 本工程的 `scripts\env.ps1` 显式优先 `D:\java\JDK21`，不受影响。
但建议把系统 `JAVA_HOME` 也统一到 21。想换 JDK 就设环境变量 `PIXELPERIL_JDK`。

---

## 六、升级 libGDX

```powershell
# 1) 看新版 backend 和 core 的 POM 分别依赖什么
java tools\MavenFetch.java pom com.badlogicgames.gdx:gdx-backend-lwjgl3:<新版本>
java tools\MavenFetch.java pom com.badlogicgames.gdx:gdx:<新版本>

# 2) 按输出核对 scripts\fetch-deps.ps1 里的坐标列表
#    （重点：LWJGL 版本、gdx-jnigen-loader 版本有没有变）

# 3) 清掉 libs 重新抓
Remove-Item libs -Recurse -Force
scripts\fetch-deps.ps1

# 4) 重新编译 + 自检 + 开窗口确认
scripts\selftest.ps1
scripts\run.ps1
```

`fetch-deps.ps1` 的参数可以临时覆盖版本号：

```powershell
scripts\fetch-deps.ps1 -GdxVersion 1.15.0 -LwjglVersion 3.3.4
```

---

## 七、提交规范建议

工程目前没有 git 仓库（本机也没装 git）。建议的约定：

**分支**：`main` 保持可运行；功能分支 `feat/xxx`、修 bug `fix/xxx`。

**提交信息**（Conventional Commits 风格）：

```
feat(sim): 支持二段跳
fix(collision): 修正顶头时向右修正的方向判断
tune(config): 满跳高度从 3.5 格调到 3.33 格
docs(levels): 补充尖刺贴图与判定框的对齐要求
test(selftest): 增加升高 3 格时的水平距离测量
```

**提交前必须**：

```powershell
scripts\selftest.ps1      # 必须 31 项全过
```

改了渲染相关代码的话，再拍一张截图确认：

```powershell
# 截图会自动退出，退出码 0
scripts\run.ps1 -Screenshot docs\check.png -ShotFrame 40

# 顺带看一眼判定框
scripts\run.ps1 -DebugBoxes
```

---

## 八、待办 / 后续可做

按价值从高到低：

| 项 | 说明 |
|---|---|
| **像素字体** | 现在 HUD 用 libGDX 内置 Arial 位图字体，在像素画面里略脏。自己做 `.fnt` + PNG，或引入 `gdx-freetype` 运行时渲染 TTF |
| **玩家动画** | 现在只有单帧精灵。跑 / 跳 / 落地 / 死亡各做几帧，按状态切换 |
| **音效与音乐** | 依赖（jlayer/jorbis）已在 `libs\`，用 `Sound`/`Music` 即可 |
| **jpackage 打包** | 需要先定：资源怎么进 jar、要不要 `--add-modules` 裁剪 JRE。见 [README 的打包章节](../README.md#八打包发布可选) |
| **敌人 / 移动平台** | `Body` 抽象已留好位置，见 [ARCHITECTURE.md](ARCHITECTURE.md) 的扩展建议表 |
| **输入回放 / ghost** | 确定性已经有了，只需每步记录 3 个 boolean |
| **存档点 / 多关卡 / 关卡选择** | `Sim.respawn()` 的目标从 `level.spawn` 改成"当前存档点"即可 |
| **`LICENSE` 文件** | 目前未指定许可证，需要你决定（建议 MIT，依赖的 libGDX 是 Apache-2.0） |
