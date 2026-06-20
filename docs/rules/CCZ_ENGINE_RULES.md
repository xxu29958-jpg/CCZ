# CCZ Engine Rules

本文件只放 CCZ 现代战棋引擎专属规则。

## Project Boundary

- 本项目是 Android-first 的现代战棋引擎。
- 曹操传 6.x MOD 是内容来源，不是运行时兼容对象。
- 目标是“挑一部、转一部”，不是“任意 MOD 即插即跑”。
- 引擎长期资产是 Kotlin core、native content pack contract、Android 表现层。
- Converter 是离线工具，可以重写，可以丢弃，不进入 runtime。

## Seven Hard Rules

1. `game-core` 是唯一权威。
2. Android UI 只是渲染和输入。
3. 战斗逻辑必须可回放。
4. 规则必须数据驱动。
5. 状态不可乱改。
6. 每个规则必须有单测。
7. 每个大功能后必须做一次收口。

落地解释：

- `game-core` 产出 state + events，Android app 不能绕过它改战斗结果。
- Android UI 只能把用户输入翻译成 command，并渲染 event/state。
- Replay = initial state + content/rules version + rng state + command sequence。
- 规则常量必须来自显式规则对象或 native content pack，不能藏在 UI、全局 mutable 或临时 if 分支里。
- Battle state 使用不可变模型，状态演进只能走 resolver。
- 新增规则必须同时新增或更新单测；无法单测的规则必须先拆到可测边界。
- 大功能完成后必须更新 HANDOFF、runbook、规则/架构文档和验证命令。

## Runtime Direction

主线：

```text
Android app shell
  -> gameplay
  -> game-core
  -> native-content
  -> presentation
```

不作为主线：

- Godot：只做参考 / 样机。
- Unity / Unreal：只作为未来 ADR 重新评估对象。
- Star 新引擎：不复制、不嵌入。

官方依据：

```text
Android Kotlin: https://developer.android.com/kotlin
Kotlin Android overview: https://kotlinlang.org/docs/android-overview.html
Godot license: https://godotengine.org/license/
Unity runtime fee status: https://unity.com/blog/unity-is-canceling-the-runtime-fee
Unreal EULA: https://www.unrealengine.com/eula/unreal
```

## Legacy MOD Boundary

Converter 可以处理：

- R 剧本：外景、过场、对话、选项、忠奸度分支。
- S 剧本：战前、战中条件检测、战后。
- Data 表：人物、兵种、地形、道具、技能、撤退台词。
- Imsg：列传、物品说明、兵种简介、台词。
- 地图：tile 拼图、坐标。
- 图档：sprite、半身像、特效。
- 音频：音乐、音效。

Runtime 禁止处理：

- 老 R/S opcode VM。
- 老二进制格式兼容层。
- 老图档即时解包。
- 老 MOD 文件直接加载。
- Star 兼容层。

## Evidence Rule

- 结构层可以按现有调研冻结。
- 精确 opcode / 指令参数 / 字节布局必须等真实样本或工具导出，不猜。
- 未知 opcode 必须 fail closed。
- 转换器错误必须定位到源文件、关卡、指令或表格字段。

优先取料：

```text
6.5 / 6.6 MOD
普罗剧本编辑器
通用修改器
R 剧本导出
S 剧本导出
Data 表导出
截图或工具文本
```

## Native Content Pack

Runtime 只认 native content pack。

第一版目录：

```text
ccz-native-pack/
  manifest.json
  classes.json
  units.json
  terrain.json
  skills.json
  items.json
  maps/
  events/
  text/
  sprites/
  audio/
```

必须校验：

- schema version。
- required fields。
- duplicate ids。
- unknown enum。
- missing references。
- map bounds。
- event op whitelist。
- trigger condition whitelist。

## Game Core

- `game-core` 是纯 Kotlin/JVM 模块。
- 不依赖 Android framework。
- 不依赖 JSON 库。
- 不依赖 converter。
- 只负责 deterministic battle state、command、event、formula、rng、rules。
- `game-core` 是战斗裁判；Android、converter、content loader 都不能拥有第二套战斗真相。

核心不变量：

- 战斗公式只用整数。
- RNG state 随 battle state 走。
- RNG 消费顺序是规则契约。
- Resolver 输入 state + command，输出 state + events。
- Presentation 只消费 events。
- Gameplay 负责 command 合法性：移动范围、射程、存活、回合归属。
- 规则配置必须以不可变 value object 显式传入。
- 禁止新增全局可变规则开关。

## Native Content Module

- `native-content` 负责内容包模型和 validator。
- 可以依赖 `game-core` 的领域基础类型。
- 不依赖 Android framework。
- 不解析旧 MOD。
- 未来 JSON loader 放这里或其下游模块，但旧格式 parser 不放这里。

## Battle Formula Rules

当前规则核来自曹操传公开公式，待样本校准。

必须保持：

- 命中 / 闪避、精防 / 格挡、暴击 / 抗暴、连击 / 连抗使用减法抵消。
- 兵种相克是独立系数。
- 不破防走 chip damage。
- 取整策略必须显式。
- 取整、暴击、连击、格挡、相克等规则常量必须进入 `BattleRules` 或 native content pack。
- 公式常量变化必须视为规则版本变化。

禁止：

- 用浮点“更顺手”地重写公式。
- 让 UI 参与公式。
- 为了现代化手感擅改平衡。

## Event Model

R 剧本映射为场景事件流：

```text
dialogue
portrait
choice
set_var
branch
wait
scene_transition
play_bgm
fade
```

S 剧本映射为战斗脚本：

```text
pre
mid triggers
post
win conditions
lose conditions
```

战中触发条件第一批：

```text
turn_start
unit_dead
unit_reach
hp_below
enemy_count_below
var_equals
```

战斗动作第一批：

```text
spawn_unit
remove_unit
move_unit
set_hp
set_status
give_item
force_win
force_lose
script(dialogue / portrait / choice / ...)
```

## Save / Replay

版本独立：

```text
engine_version
native_format_version
content_version
converter_version
save_schema_version
```

规则：

- Save 必须包含 `save_schema_version`。
- Save 必须包含 `rng_state`。
- Replay = initial state + command sequence。
- 运行时遇到未来版本 save 必须拒绝。
- 内容包版本和存档兼容关系必须显式记录。

## Android App Future Gates

当前还没有 app 模块。建立 app 后必须补齐：

```powershell
.\gradlew.bat --no-daemon :app:detektGrayDebug :app:detektGrayDebugUnitTest
.\gradlew.bat --no-daemon :app:lintGrayDebug
.\gradlew.bat --no-daemon :app:assertAndroidTestCountEqualsBaseline
.\gradlew.bat --no-daemon :app:assembleGrayRelease
```

还必须补：

- Room schema drift gate。
- R8 release 编译。
- apksigner 指纹钉。
- emulator smoke test。

## Current Kotlin Gates

在 app 建立前，当前模块必须过：

```powershell
.\gradlew.bat --no-daemon :game-core:test :native-content:test
.\gradlew.bat --no-daemon :game-core:runSelfTest :native-content:runSelfTest
.\gradlew.bat --no-daemon :game-core:detektMain :native-content:detektMain
.\gradlew.bat --no-daemon :game-core:detektTest :native-content:detektTest
```

## Not Doing

- 不做任意 MOD 即插即跑。
- 不把 converter 塞进 app。
- 不让 runtime 读旧格式。
- 不继续扩旧 Godot 工程当主线。
- 不为“以后可能上 3D”提前引 Unity / Unreal。
- 不把 opcode 猜测写成事实。
