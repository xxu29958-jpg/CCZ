# CCZ Engine Rules

> 规则版本 v0.1.0，2026-06-20，自 xiaopiaojia 工程规范 fork 后新建的引擎专属层。
> 本文件只放 CCZ 现代战棋引擎专属规则。
>
> **机器门状态约定**：本文件给硬规则标注落地状态——`[machine-gated]` = 有对应 test/validator 守护（CI 在每次 push/PR 跑，见 `docs/runbook/CI.md`）；`[review-only]` = 无对应机器门、当前只靠 review；`[aspirational]` = 规则描述的对象在 src 中尚未实现。把 review-only / aspirational 逐步变成 machine-gated 是持续目标，不允许给不存在的代码写现在时断言。

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

> **#6 落地状态** `[machine-gated]`：`assertTestCountEqualsBaseline`（root gradle task，权威 baseline 在 `android/config/test-count-baseline.txt`）按 `@Test` 严格等值钉死测试数——加/删测试必须同 diff bump baseline，是经审改动。

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

- Godot：评估后不采用为主线（上游模板素材已移除）。
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

> **落地状态** `[aspirational]`：converter 模块尚未入仓（src 当前只有 game-core + native-content）。上述 converter fail-closed / source-location 规则在 converter 落地时适用；落地时复用 `ContentValidator` 的 path-keyed `ValidationIssue`（如 `units[0].class`）作 source-location 范例。运行时侧的 fail-closed 已有现成范例：`ContentValidator` 拒不支持的 `native_format_version`。

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

必须校验（标注当前 `ContentValidator` 实现状态——绝不给未实现项写现在时断言，见 `docs/architecture/SECURITY.md`）：

- schema version。`[machine-gated]` native_format_version 检查（loader 解码后由 `ContentValidator` 守，二层 fail-closed）。
- required fields。`[machine-gated]` `ContentJsonLoader` 解码边界：缺必填字段 → kotlinx `MissingFieldException` 包成 `ContentDecodeException`（无默认值的 DTO 字段即必填）。
- duplicate ids（含空白 id）。`[machine-gated]` validateUniqueIds。
- missing references（`manifest.entry`→event script、unit→class/skill/item、class→counter/skill、map→terrain）。`[machine-gated]` validateManifestEntry / validateUnits / validateClasses / validateMaps（unknownReferencesFailClosed + manifest entry tests）。
- map bounds（尺寸、行列形状、spawn 越界）。`[machine-gated]` validateMaps。
- selected S-script map bounds（`pre/post`、`mid` reach/action、`win/lose ReachTile` 坐标对已选 `MapDef`）。`[machine-gated]` `CampaignAssembler` 装配前校验（CampaignAssembler map-bound tests）。
- unknown enum。`[machine-gated]` `ContentJsonLoader` 解码边界：faction / damageKind / counterRelation 字符串经 `Decoders` 白名单化，未知值 fail-closed（`ContentJsonLoaderTest`）。未知 JSON 键也拒（`ignoreUnknownKeys=false`）。
- event op / trigger whitelist。`[type-enforced + machine-gated]` op/触发集是 Kotlin sealed interface，内存里造不出未知 op（未知 op 只可能在未来 JSON 解码边界出现，届时 loader 白名单化）；事件**引用完整性**（unit/item/portrait_subject 引用）由 `ContentEventValidator` 校验（eventReferencesFailClosed 测试）。

## Game Core

- `game-core` 是纯 Kotlin/JVM 模块。
- 不依赖 Android framework。
- 不依赖 JSON 库。
- 不依赖 converter。
- 只负责 deterministic battle state、command、event、formula、rng、rules。
- `game-core` 是战斗裁判；Android、converter、content loader 都不能拥有第二套战斗真相。

核心不变量：

- 战斗公式只用整数。`[review-only]` 类型层面非强制，靠 review + 公式实现；浮点 = 回放不可复现。
- RNG state 随 battle state 走。`[machine-gated]` ReplayContractTest（同种子同结果）。
- RNG 消费顺序是规则契约（hit→crit→combo→block）。`[machine-gated]` RngContractTest 钉死消费**计数**（命中 4 次 / miss 短路 1 次，靠 splitmix64 state delta）；GoldenReplayTest 钉死固定种子→事件流，重排 roll 顺序会改 golden 即被捕获。
- Resolver 输入 state + command，输出 state + events。
- Presentation 只消费 events。
- Gameplay 负责 command 合法性：移动范围、射程、存活、回合归属。`[machine-gated]` `CommandValidator.check` 是纯确定性闸门，`Gameplay.submit` 在校验通过前不触 `Resolver`（拒绝零 RNG、不改 state）；`MoveLegalityTest` / `AttackLegalityTest` / `TurnOwnershipTest` / `GameplayOutcomeTest` 覆盖全部 18 个 `RejectReason` + 接受路径。
- 行动经济：每单位每回合可移动一次、再行动一次（攻击或待机），不可二动，移动后仍可攻（move-then-attack，Fire-Emblem 式）。`[machine-gated]` `BattleProgress.moved`/`acted`（回合作用域，`Command.EndTurn` 经 `Resolver` 清空）+ `CommandValidator` 的 `UNIT_ALREADY_MOVED`/`UNIT_ALREADY_ACTED` + `Command.Wait` 耗尽该单位；`Gameplay.legal*` 查询加经济守卫（query⟺submit parity）；`ActionEconomyTest` 覆盖一移一动 / move-then-attack / 二动拒 / Wait 耗尽 / EndTurn 重置 / 查询空。`moved`/`acted` 回合作用域不入存档（replay 折命令重导出，见 §Save / Replay 的 `SaveMappers` 注）。
- 规则配置必须以不可变 value object 显式传入。`[machine-gated]` 地图 + 规则/内容表收进 `BattleContext`（map / classes / skills / rules），与 `Resolver` 的入参模式一致；`RuleDataTest` 钉死规则注入而非全局可变。
- 禁止新增全局可变规则开关。

> **合法性层落点（设计决策）**：命令合法性是确定性*规则*，落在 `game-core`（唯一战斗权威），经 `Gameplay.submit` facade 暴露——不放 UI（否则 UI 持有战斗真相），不污染 `Resolver`（保持纯变更，replay 直接重放已接受命令、不重校验）。`ARCHITECTURE.md` 的 "Gameplay 层" 当前即此 facade；独立 `:gameplay` 模块（battle loop / AI / trigger runner）待 P2 渲染半 / P3 落地时再拆。
>
> **空间模型**：`BattleMap`（bounds + 每格 `moveCost`/`passable`）是 `game-core` 自有类型，由上层从 native content `MapDef` + terrain 构建后作输入传入；占位（occupancy）从 `BattleState.units` 派生，不另存一份。移动 4 向、进入格扣 `moveCost`（起点免费）、不可通行格与敌方单位阻断通行、友方单位可穿过但不可停、终点须空且在界内（`MoveReachability`）。射程用曼哈顿距离。
>
> **回合归属按侧判定**：PLAYER + ALLY 同侧（`sameSide`），ALLY 可在 PLAYER 侧回合行动；`BattleState.active` 只取侧代表（PLAYER / ENEMY，见 `Resolver.nextFaction`），故 `EndTurn` 对 active 精确匹配。`Move` 到自身格 = 原地待命 no-op（合法）。

## Native Content Module

- `native-content` 负责内容包模型和 validator。
- 可以依赖 `game-core` 的领域基础类型。
- 不依赖 Android framework。
- 不解析旧 MOD。
- JSON loader 已落此模块（`com.ccz.contentpack.json`，`kotlinx.serialization`）：`@Serializable` DTO 层 + 映射器，把枚举字符串在解码边界白名单化，`game-core` 域类型保持零序列化依赖（DTO 层隔离 JSON）。旧格式 parser 不放这里。

## Battle Formula Rules

当前规则核来自曹操传公开公式，待样本校准。

必须保持：

- 命中 / 闪避、精防 / 格挡、暴击 / 抗暴、连击 / 连抗使用减法抵消。
- 兵种相克是独立系数。
- 不破防走 chip damage。
- 取整策略必须显式。
- 取整、暴击、连击、格挡、相克等规则常量必须进入 `BattleRules` 或 native content pack。
- 公式常量变化必须视为规则版本变化（联动 `ENGINEERING_RULES.md` 裁决根：宁可破坏手感不可破坏回放）。

禁止：

- 用浮点“更顺手”地重写公式（违反整数公式不变量 = 破坏回放）。
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

> **落地状态** `[machine-gated]`：win/lose 求值 = `WinLose.evaluate/settle`（列表 OR、lose 优先、`BattleOutcome` sticky、纯只读，`WinLoseTest` 覆盖全 6 条件）；mid triggers + pre/post battle ops = `TriggerRunner`（`tick` = 触发器→win/lose settle；`once` 经 `BattleProgress.firedTriggers` 追踪；`TriggerConditions` 覆盖全 6 触发条件，整数 HP 比较；`BattleOps` 覆盖全 9 个 `BattleOp`，纯无 RNG、按序应用）。SpawnUnit 经 `ScriptContext.reserves` 模板（缺模板 = fail-safe no-op，引用完整性由 `ContentEventValidator` 上游守）；GiveItem 仅发事件（core 暂无背包）；非 SetVar 的 scenario op 仅发 `Event.Scenario`。`TriggerConditionTest`/`BattleOpTest`/`TriggerRunnerTest` 守护。

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

规则（`SaveEnvelope`/`SaveVersions`/`SaveLoader` + on-disk `SaveCodec` 已入 `game-core` 的 `com.ccz.core.save`；存档原子写已落地于 `:save-io`，见 §Write & Convert Safety）：

- Save 必须包含 `save_schema_version`。`[machine-gated]` `SaveVersions.saveSchemaVersion`。
- Save 必须包含 `rng_state`。`[machine-gated]` 由 `SaveEnvelope.initialState.rngState` 携带（回放从初始 state 折叠命令，rng 随 state 走）。
- Replay = initial state + command sequence。`[machine-gated]` `SaveLoader.load` 折叠已接受命令复现终态（`SaveLoaderTest` 断言回放 == 手动 Resolver 折叠且真消费 RNG）；同进程确定性另由 ReplayContractTest、跨 build/版本由 GoldenReplayTest 覆盖。
- 运行时遇到未来版本 save 必须拒绝。`[machine-gated]` `SaveLoader.check` → `FUTURE_SCHEMA_VERSION`（`rejectsFutureSaveSchemaVersion` 测试，镜像 `ContentValidator` 拒不支持 `native_format_version`）；规则版本漂移另拒 `RULES_VERSION_MISMATCH`（save 在不同战斗公式规则下生成则回放会偏离）。
- 内容包版本和存档兼容关系必须显式记录。`[machine-gated]` `SaveVersions` 显式记 engine / native_format / content / converter / rules / save_schema 各轴。
- Save on-disk 序列化必须 fail-closed。`[machine-gated]` `SaveCodec`（`SaveEnvelope`↔JSON，`@Serializable` DTO 隔离、域类型零注解）round-trip 全字段保真 + 坏 JSON / 未知 key / 缺必填字段 / 未知命令 kind / 未知 faction·outcome → `SaveDecodeException`（`SaveCodecTest`）；版本轴 gating 仍独占 `SaveLoader.check`（两层，镜像 ContentJsonLoader+ContentValidator）。
- 损坏存档命令必须 replay 前优雅拒，不崩。`[machine-gated]` `SaveLoader.commandIntegrity`：命令引用（`Move.unit` / `Attack.attacker·target·skill`）对初始 roster / skill 表不可解析 → `Outcome.Rejected(CORRUPT_COMMAND)`（版本闸优先；`SaveLoaderTest` 钉 Move/Attack 各引用路径 + 版本优先 + 合法放行 + 空命令）。
- Save 须记录 scenario（过场）回放轴。`[machine-gated]` `SaveEnvelope.scenarios`（`ScenarioReplay` = scriptId + 选择索引序列，与战斗 command 并列的第二回放轴）经 `SaveCodec` round-trip 保真；`SUPPORTED_SAVE_SCHEMA_VERSION` 1→2，v1 存档缺 scenarios 字段向后兼容 decode 为空（`SaveCodecTest`）。脚本本体在 content（`contentVersion` 引用），不入存档；回放执行 + 完整性见下条 `ScenarioReplayer`。
- Save 的 scenario 回放须 fail-closed。`[machine-gated]` `ScenarioReplayer.replay(scenarios, scripts)`：对每个 `ScenarioReplay` 跑 `ScenarioRunner`（脚本由 content 提供，game-core 不依赖 native-content）；未知 scriptId → `UNKNOWN_SCRIPT`，choices 截断（残留 Choice）或脚本环 → `INCOMPLETE_REPLAY`，整批拒不漏 partial playback（`ScenarioReplayerTest`）。`SaveLoader`（战斗）+ `ScenarioReplayer`（过场）= 两条独立回放轴。
- 存档写入必须原子，部分产物不得当有效存档。`[machine-gated]` `:save-io` 的 `SaveFileStore.save` 写同目录临时文件 + `ATOMIC_MOVE` rename（读者/崩溃永不见半写），失败清理临时文件不留半文件；`load` 缺文件 → `SaveIoException`、坏内容 → `SaveDecodeException`（`SaveFileStoreTest` + selftest 钉 round-trip / 覆盖原子 / 无残留临时文件 / 缺文件·坏内容 fail-closed）。game-core 纯逻辑不碰文件系统，IO 隔离在此独立模块（CCZ_ENGINE_RULES §Write & Convert Safety）。

> 注：on-disk `SaveCodec`（shape + enum fail-closed）、命令完整性优雅拒绝（`SaveLoader.commandIntegrity` 在 replay 前校验命令引用 vs 初始 state / skill 表，缺失 → `CORRUPT_COMMAND`）、存档原子写（`:save-io` 的 `SaveFileStore`，临时文件 + ATOMIC_MOVE）均已落地。§Write & Convert Safety 的 converter 幂等批处理（IO 层）仍待。

## Android App Gates

`:app` 模块已建立（Compose 壳，P2 渲染半起步）。以下四门 `[machine-gated]`——CI 的 `android-gate` lane（见 `docs/runbook/CI.md`）每 push/PR 跑，本地在 `docs/runbook/LOCAL_DEV.md` Full Current Local Gate 跑：

```powershell
.\gradlew.bat --no-daemon :app:detektGrayDebug :app:detektGrayDebugUnitTest
.\gradlew.bat --no-daemon :app:testGrayDebugUnitTest
.\gradlew.bat --no-daemon :app:lintGrayDebug
.\gradlew.bat --no-daemon :app:assertAndroidTestCountEqualsBaseline
.\gradlew.bat --no-daemon :app:assembleGrayRelease
```

`testGrayDebugUnitTest` **执行** `:app` 的 JVM 单测（如 `BattleReducerTest`，在无设备的 JVM 上跑表现层 reducer 逻辑）；`assertAndroidTestCountEqualsBaseline` 只静态点 `@Test` 数，故两者都需要——计数门钉住测试不被偷删，执行门钉住断言真过。`assembleGrayRelease` 当前产**未签名** APK（不 minify）——R8/签名是下面的未来门。

仍 `[aspirational]`（待对应能力入仓后翻 machine-gated）：

- Room schema drift gate（`:app` 尚无 DB）。
- R8 release 编译（`isMinifyEnabled` 现 false）。
- apksigner 指纹钉（尚无 signingConfig/keystore）。
- emulator smoke test（AVD `ticketbox_api36_host`；尚无 instrumented test）。

## Current Kotlin Gates

> **CI 状态**：CI（`.github/workflows/ci.yml`）在每次 push/PR 跑以下 gate（见 `docs/runbook/CI.md`）；push 前本地也应先跑（见 `docs/runbook/LOCAL_DEV.md`）。

JVM 模块（`game-core` / `native-content` / `save-io`）的门（CI `jvm-gate` + 本地）；`:app` 的门见上 §Android App Gates：

```powershell
.\gradlew.bat --no-daemon :game-core:runSelfTest :native-content:runSelfTest :save-io:runSelfTest
.\gradlew.bat --no-daemon :game-core:test :native-content:test :save-io:test
.\gradlew.bat --no-daemon :game-core:detektMain :native-content:detektMain :save-io:detektMain
.\gradlew.bat --no-daemon :game-core:detektTest :native-content:detektTest :save-io:detektTest
.\gradlew.bat --no-daemon assertTestCountEqualsBaseline
```

## Write & Convert Safety

写存档与离线转换的安全约束（与模板 §幂等/§事务的 CCZ 映射）：

- 存档写入必须原子：写临时文件再 rename，不就地半写。`[machine-gated]` `:save-io` 的 `SaveFileStore.save`（同目录临时文件 + `ATOMIC_MOVE`）。
- Converter 重跑同输入必须同输出（幂等批处理），不产生损坏或重复产物。`[aspirational]`（待 converter 模块入仓）。
- 任何失败 fail-closed 并定位到源（文件 / 关卡 / 指令 / 字段），复用 `ValidationIssue` 的 path-keyed 范式。
- 部分产物不得被当作有效内容包 / 存档。`[machine-gated]`（存档侧：`SaveFileStore` 失败清理临时文件不留半文件、`load` 缺文件/坏内容 fail-closed）；converter 侧 `[aspirational]`。

> 落地状态：**存档原子写** `[machine-gated]`（`:save-io` 的 `SaveFileStore`，临时文件 + `ATOMIC_MOVE`，`SaveFileStoreTest` + selftest）；**converter** 幂等批处理仍 `[aspirational]`（待 converter 模块入仓时机器化）。

## Not Doing

- 不做任意 MOD 即插即跑。
- 不把 converter 塞进 app。
- 不让 runtime 读旧格式。
- 不采用 Godot 作主线运行时。
- 不为“以后可能上 3D”提前引 Unity / Unreal。
- 不把 opcode 猜测写成事实。
