# Handoff

> 状态：引擎实现阶段，自主扫荡中。P2 引擎半 + P3 事件系统引擎侧（胜负判定 + 触发器）均**已合并** main。当前 P3b 触发器在 `feat/trigger-runner-ops` 上待合。

## 当前真实状态

- `main`=`f610bd2`（P3a 胜负判定，[PR #4](https://github.com/xxu29958-jpg/CCZ/pull/4) 已合）。其前 `940c6ae`=P2 gameplay-legality（[PR #3](https://github.com/xxu29958-jpg/CCZ/pull/3)）。当前分支 `feat/trigger-runner-ops`（off `f610bd2`）。
- **已落 game-core（P2 引擎半）**：`Gameplay.submit` 合法性闸门 + `CommandValidator` + `BattleMap`/`MoveReachability` + `BattleContext` + `Skill.range`。回合归属按侧判定；Move 自身格 = no-op。
- **已落 game-core（P3a 胜负）**：`WinLose.evaluate/settle`（OR、lose 优先、sticky、纯只读）+ `BattleOutcome` + `Event.BattleEnded`。覆盖全 6 `WinLoseCondition`。
- **本片在做（P3b 触发器）**：`TriggerRunner`（`tick`=触发器→win/lose settle、`applyPre`/`applyPost`）+ `TriggerConditions`（全 6 触发条件，整数 HP）+ `BattleOps`（全 9 个 `BattleOp`，纯无 RNG，按序）+ `ScriptContext.reserves`（spawn 模板）。`once` 经 `BattleProgress.firedTriggers` 追踪（仅 once-触发器记）。`BattleState` 把 `outcome`/`vars`/`firedTriggers` 收进 `BattleProgress`（5 参，留余量；`outcome` getter 保兼容）。GiveItem 仅发事件（core 无背包）；非 SetVar scenario op 仅发 `Event.Scenario`。`Resolver`/RNG/公式/`RULES_VERSION`(=1)/golden **未动**。test=76。经 3 镜头对抗审，0 P1（补 `ForceLose`/缺失单位 no-op/`UnitReach` 边界覆盖；非-once 不再误标 fired）。
- 仓库自 Godot tactical RPG 模板 fork，上游模板素材已**彻底移除**（含 git 历史，`.git` 已瘦到几百 KB）。
- 规则体系已重锚到 CCZ 域并接机器门（PR #1 / #2 已合，详见 `docs/audits/2026-06-20-rules-overhaul-vs-xiaopiaojia.md`）：三份规则 + 裁决梯 + 依赖治理 + 发布门 + skills（process 层移植 + 领域层 add-event-op）；机器门 `ContentEventValidator` / `RngContractTest` / `GoldenReplayTest` / `BattleRules.RULES_VERSION` / `assertTestCountEqualsBaseline`(12) 全绿；所有 `[machine-gated]/[review-only]/[aspirational]` 状态标已诚实化（不给未实现代码写现在时断言）。
- 主线 Android-first：`game-core`（纯 Kotlin/JVM，唯一战斗权威，`BattleRules` 注入、整数公式、RNG 随 state、可回放）+ `native-content`（内容包模型 + validator）。核心种子来自 `files.zip`。
- 本地全量门 = `docs/runbook/LOCAL_DEV.md` 的 Full Current Local Gate（从 `android/` 内跑）。

## 在途任务

- `feat/trigger-runner-ops`（自主扫荡）：P3b 触发器已实现并过本地全套门 + 3 镜头对抗审，跑 push → CI → 合并。

## 下一步（V1 Roadmap，每片走 `skills/ship-slice`）

- 引擎侧 P3 已基本闭环（win/lose + 触发器 + ops）。R 剧本 scenario 流（dialogue/choice/branch/goto）尚未执行——现状是 `BattleOps` 把非 SetVar 的 scenario op 透传为 `Event.Scenario`，真正的对话/分支推进需表现层或一个 scenario runner。
- P2 渲染半：建 `:app` 壳 + 渲染 map/units + 输入→command（经 `Gameplay.submit`，**不得**绕核改结果）+ 事件驱动表现层（消费 `Event`，含本批新事件）。`:app` 落地须补 Android future gates（见 `CCZ_ENGINE_RULES.md` §Android App Future Gates + `docs/runbook/CI.md` Android lane + AVD `ticketbox_api36_host` smoke）。
- 候选：① native content skeleton（手写 sample pack + JSON loader，事件 op 字符串白名单在解码边界）；SpawnUnit 的 `ScriptContext.reserves` 模板正好需要 loader 产出。
- 仍 pending 的机器门：Save/Replay 信封 + 拒未来版本测试；converter 模块 + opcode fail-closed（等真实 MOD 样本，不猜）。
- 纪律：改 Kotlin 走 `android-detekt-discipline`；搬/删/改签名走 `safe-code-change`；出码后无外部评审走 `adversarial-review`；CI 红走 `ci-red-triage`。
