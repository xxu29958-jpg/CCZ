# Handoff

> 状态：引擎实现阶段，自主扫荡中。P2 引擎半（命令合法性 + 地图）**已合并** main；P3「事件系统」拆片推进，P3a 胜负判定在 `feat/trigger-runner` 上。

## 当前真实状态

- `main`=`940c6ae`（P2 gameplay-legality，[PR #3](https://github.com/xxu29958-jpg/CCZ/pull/3) 已合，CI 绿）。当前分支 `feat/trigger-runner`（off `940c6ae`）。
- **已落 game-core（P2 引擎半）**：`Gameplay.submit`（合法性闸门 → `Accepted` / `Rejected(RejectReason)`，纯确定性、拒绝零 RNG、不触 `Resolver`）+ `CommandValidator` + `BattleMap`/`MoveReachability`（4 向、地形 moveCost、不可通行/敌阻断/友穿过、终点空且在界）+ `BattleContext` + `Skill.range`(曼哈顿)。回合归属按侧判定；Move 到自身格 = 原地待命 no-op。
- **本片在做（P3a 胜负判定）**：`WinLose.evaluate/settle`（win/lose 列表 OR、lose 优先、outcome sticky、纯只读）+ `BattleOutcome{ONGOING/VICTORY/DEFEAT}` + `BattleState.outcome`（默认 ONGOING，golden/replay 不受影响）+ `Event.BattleEnded`。`ProtectAlive` 语义=保护目标阵亡时触发（放 lose）。覆盖全 6 个 `WinLoseCondition`。`Resolver`/RNG/公式/`RULES_VERSION`(=1)/golden **未动**。test=54。经 2 镜头对抗审，0 P1（补了 `DefeatUnit` 覆盖缺口）。
- 仓库自 Godot tactical RPG 模板 fork，上游模板素材已**彻底移除**（含 git 历史，`.git` 已瘦到几百 KB）。
- 规则体系已重锚到 CCZ 域并接机器门（PR #1 / #2 已合，详见 `docs/audits/2026-06-20-rules-overhaul-vs-xiaopiaojia.md`）：三份规则 + 裁决梯 + 依赖治理 + 发布门 + skills（process 层移植 + 领域层 add-event-op）；机器门 `ContentEventValidator` / `RngContractTest` / `GoldenReplayTest` / `BattleRules.RULES_VERSION` / `assertTestCountEqualsBaseline`(12) 全绿；所有 `[machine-gated]/[review-only]/[aspirational]` 状态标已诚实化（不给未实现代码写现在时断言）。
- 主线 Android-first：`game-core`（纯 Kotlin/JVM，唯一战斗权威，`BattleRules` 注入、整数公式、RNG 随 state、可回放）+ `native-content`（内容包模型 + validator）。核心种子来自 `files.zip`。
- 本地全量门 = `docs/runbook/LOCAL_DEV.md` 的 Full Current Local Gate（从 `android/` 内跑）。

## 在途任务

- `feat/trigger-runner`（自主扫荡）：P3a 胜负判定已实现，跑门 → 对抗审 → push → CI → 合并；接着同分支/新分支做 **P3b 触发器**（`vars` + `firedTriggers` + `TriggerConditions` + `BattleOps` + `TriggerRunner`，驱动 `BattleTrigger` 的 6 个条件 + 战斗 ops）。

## 下一步（V1 Roadmap，每片走 `skills/ship-slice`）

- P3b 触发器（接本片）：`BattleState` 加 `vars`/`firedTriggers`；`TriggerCondition` 6 条求值 + once 追踪 + `BattleOp` 执行（spawn 经 reserves 模板 / remove / move / setHp / setStatus / giveItem→event / forceWin/Lose / Script(SetVar)）。
- P2 渲染半：建 `:app` 壳 + 渲染 map/units + 输入→command（经 `Gameplay.submit`，**不得**绕核改结果）+ 事件驱动表现层。`:app` 落地须补 Android future gates（见 `CCZ_ENGINE_RULES.md` §Android App Future Gates + `docs/runbook/CI.md` Android lane + AVD `ticketbox_api36_host` smoke）。
- 候选：① native content skeleton（手写 sample pack + JSON loader，事件 op 字符串白名单在解码边界）。
- 仍 pending 的机器门（落地时补，见各 `[aspirational]`）：Save/Replay 信封 + 拒未来版本测试；converter 模块 + opcode fail-closed（等真实 MOD 样本，不猜）。
- 纪律：改 Kotlin 走 `android-detekt-discipline`；搬/删/改签名走 `safe-code-change`；出码后无外部评审走 `adversarial-review`；CI 红走 `ci-red-triage`。
