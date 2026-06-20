# Handoff

> 状态：引擎实现阶段。P2「最小战斗循环」**引擎半已实现**（命令合法性层 + 地图模型），在分支 `feat/gameplay-command-legality` 上本地全套门绿，**等用户授权 push / 合并**。

## 当前真实状态

- 分支 `feat/gameplay-command-legality`（off `main`=`63243fa`），未 push。本地全套门绿（test=44 / detektMain+Test 0 findings / 两 self-test 过）。CI（`.github/workflows/ci.yml`）随 push 跑。
- **本片新增（game-core）**：`Gameplay.submit`（合法性闸门 → `Accepted(resolution)` / `Rejected(RejectReason)`）+ `CommandValidator`（纯确定性，拒绝零 RNG、不触 `Resolver`）+ `BattleMap`/`MoveReachability`（4 向、地形 moveCost、不可通行/敌阻断/友穿过、终点空且在界）+ `BattleContext`(map/classes/skills/rules) + `Skill.range`(曼哈顿)。回合归属按侧判定（PLAYER+ALLY 同侧，ALLY 可在玩家回合行动）；Move 到自身格 = 原地待命 no-op。`Resolver`/RNG/公式/`RULES_VERSION`(=1)/golden **未动**。经 3 镜头对抗审，0 P1。
- 仓库自 Godot tactical RPG 模板 fork，上游模板素材已**彻底移除**（含 git 历史，`.git` 已瘦到几百 KB）。
- 规则体系已重锚到 CCZ 域并接机器门（PR #1 / #2 已合，详见 `docs/audits/2026-06-20-rules-overhaul-vs-xiaopiaojia.md`）：三份规则 + 裁决梯 + 依赖治理 + 发布门 + skills（process 层移植 + 领域层 add-event-op）；机器门 `ContentEventValidator` / `RngContractTest` / `GoldenReplayTest` / `BattleRules.RULES_VERSION` / `assertTestCountEqualsBaseline`(12) 全绿；所有 `[machine-gated]/[review-only]/[aspirational]` 状态标已诚实化（不给未实现代码写现在时断言）。
- 主线 Android-first：`game-core`（纯 Kotlin/JVM，唯一战斗权威，`BattleRules` 注入、整数公式、RNG 随 state、可回放）+ `native-content`（内容包模型 + validator）。核心种子来自 `files.zip`。
- 本地全量门 = `docs/runbook/LOCAL_DEV.md` 的 Full Current Local Gate（从 `android/` 内跑）。

## 在途任务

- `feat/gameplay-command-legality`：已提交，待用户授权 `git push` + 开 PR → CI 跑全套门 → 用户授权合并（不自合 `main`）。

## 下一步（V1 Roadmap，起点由用户/下个 session 定，每片走 `skills/ship-slice`）

- P2 渲染半（接本片）：建 `:app` 壳 + 渲染 map/units + 输入→command（经 `Gameplay.submit`，**不得**绕核改结果）+ 事件驱动表现层。`:app` 落地须补 Android future gates（见 `CCZ_ENGINE_RULES.md` §Android App Future Gates + `docs/runbook/CI.md` Android lane + AVD `ticketbox_api36_host` smoke）。
- 候选：① native content skeleton（手写 sample pack + JSON loader，事件 op 字符串白名单在解码边界）③ `android/app` 壳（同上 P2 渲染半）。
- 引擎侧可继续：P3 trigger runner（驱动已建模的 `SScript`/`BattleTrigger`/`WinLoseCondition`，目前只有数据无执行）。
- 仍 pending 的机器门（落地时补，见各 `[aspirational]`）：Save/Replay 信封 + 拒未来版本测试；converter 模块 + opcode fail-closed（等真实 MOD 样本，不猜）。
- 纪律：改 Kotlin 走 `android-detekt-discipline`；搬/删/改签名走 `safe-code-change`；出码后无外部评审走 `adversarial-review`；CI 红走 `ci-red-triage`。
