# Handoff

> 状态：引擎实现阶段，自主扫荡中。P2 引擎半 + P3 事件系统引擎侧（胜负 + 触发器）+ P1 内容 JSON loader（tables）均**已合并** main。当前 loader 在 `feat/content-json-loader` 上待合。

## 当前真实状态

- `main`=`35201ab`（P3b 触发器，[PR #5](https://github.com/xxu29958-jpg/CCZ/pull/5) 已合）。前序：`f610bd2`=P3a 胜负（[#4](https://github.com/xxu29958-jpg/CCZ/pull/4)）、`940c6ae`=P2 合法性（[#3](https://github.com/xxu29958-jpg/CCZ/pull/3)）。当前分支 `feat/content-json-loader`（off `35201ab`）。
- **已落 game-core（P2 引擎半）**：`Gameplay.submit` 合法性闸门 + `CommandValidator` + `BattleMap`/`MoveReachability` + `BattleContext` + `Skill.range`。回合归属按侧；Move 自身格 = no-op。
- **已落 game-core（P3 事件引擎）**：`WinLose`（胜负，OR/lose 优先/sticky）+ `TriggerRunner`（`tick`=触发器→settle，`applyPre/Post`）+ `TriggerConditions`（6 条，整数 HP）+ `BattleOps`（9 ops，纯无 RNG）+ `ScriptContext.reserves`。`once` 经 `BattleProgress.firedTriggers`。`BattleState` 的 `outcome`/`vars`/`firedTriggers` 收进 `BattleProgress`。
- **本片在做（P1 JSON loader，仅 tables）**：`com.ccz.contentpack.json` —— `ContentJsonLoader.load(json)`（strict、snake_case、`ignoreUnknownKeys=false`）+ `@Serializable` DTO（`ContentDto`）+ 映射器（`ContentMapper`/`TableMappers`）+ 枚举白名单 `Decoders`（faction/damageKind/counterRelation 未知值 fail-closed → `ContentDecodeException`）。缺必填字段/未知键也拒。两层 fail-closed：解码 shape → `ContentValidator` 查交叉引用/版本。**事件脚本（R/S）JSON 暂不解码**（多态 op 层级延后）。`game-core` 仍零 JSON 依赖（DTO 层隔离）；`kotlinx-serialization-json:1.7.3` 仅入 `:native-content`。`Resolver`/RNG/公式/golden **未动**。test=83。经 3 镜头对抗审，0 P1（补 `counterRelation` 覆盖 + 嵌套值断言 + replace 守卫；位置构造器改具名参数）。
- 仓库自 Godot tactical RPG 模板 fork，上游模板素材已**彻底移除**（含 git 历史，`.git` 已瘦到几百 KB）。
- 规则体系已重锚到 CCZ 域并接机器门（PR #1 / #2 已合，详见 `docs/audits/2026-06-20-rules-overhaul-vs-xiaopiaojia.md`）：三份规则 + 裁决梯 + 依赖治理 + 发布门 + skills（process 层移植 + 领域层 add-event-op）；机器门 `ContentEventValidator` / `RngContractTest` / `GoldenReplayTest` / `BattleRules.RULES_VERSION` / `assertTestCountEqualsBaseline`(12) 全绿；所有 `[machine-gated]/[review-only]/[aspirational]` 状态标已诚实化（不给未实现代码写现在时断言）。
- 主线 Android-first：`game-core`（纯 Kotlin/JVM，唯一战斗权威，`BattleRules` 注入、整数公式、RNG 随 state、可回放）+ `native-content`（内容包模型 + validator）。核心种子来自 `files.zip`。
- 本地全量门 = `docs/runbook/LOCAL_DEV.md` 的 Full Current Local Gate（从 `android/` 内跑）。

## 在途任务

- `feat/content-json-loader`（自主扫荡）：P1 JSON loader（tables）已实现并过本地全套门 + 3 镜头对抗审，跑 push → CI → 合并。

## 下一步（V1 Roadmap，每片走 `skills/ship-slice`）

- 事件脚本 JSON 解码（P1 续）：R/S 脚本的多态 op 层级（`ScenarioOp`/`BattleOp`/`TriggerCondition`/`WinLoseCondition`）走 kotlinx 多态序列化（class discriminator）+ op 字符串白名单；落地后 `EventTables` 也能从 JSON 加载，并补 `reserves` 模板产出。
- R 剧本 scenario 流执行：现状 `BattleOps` 把非 SetVar scenario op 透传为 `Event.Scenario`；对话/选项/branch/goto 推进需表现层或 scenario runner（选项=可回放 command）。
- P2 渲染半：建 `:app` 壳 + 渲染 map/units + 输入→command（经 `Gameplay.submit`，**不得**绕核改结果）+ 事件驱动表现层。`:app` 落地须补 Android future gates（见 `CCZ_ENGINE_RULES.md` §Android App Future Gates + `docs/runbook/CI.md` Android lane + AVD `ticketbox_api36_host` smoke）。
- 仍 pending 的机器门：Save/Replay 信封 + 拒未来版本测试；converter 模块 + opcode fail-closed（等真实 MOD 样本，不猜）。
- 纪律：改 Kotlin 走 `android-detekt-discipline`；搬/删/改签名走 `safe-code-change`；出码后无外部评审走 `adversarial-review`；CI 红走 `ci-red-triage`。
