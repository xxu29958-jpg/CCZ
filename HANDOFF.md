# Handoff

> 状态：规则 / 基建阶段已收尾，进入引擎实现阶段。**无在途任务**，fresh session 干净起步。

## 当前真实状态

- `main` = `63243fa`，本地 == 远端，工作树干净。CI（GitHub Actions JVM gate，`.github/workflows/ci.yml`）每次 push/PR 跑全套门，main 实测绿。
- 仓库自 Godot tactical RPG 模板 fork，上游模板素材已**彻底移除**（含 git 历史，`.git` 已瘦到几百 KB）。
- 规则体系已重锚到 CCZ 域并接机器门（PR #1 / #2 已合，详见 `docs/audits/2026-06-20-rules-overhaul-vs-xiaopiaojia.md`）：三份规则 + 裁决梯 + 依赖治理 + 发布门 + skills（process 层移植 + 领域层 add-event-op）；机器门 `ContentEventValidator` / `RngContractTest` / `GoldenReplayTest` / `BattleRules.RULES_VERSION` / `assertTestCountEqualsBaseline`(12) 全绿；所有 `[machine-gated]/[review-only]/[aspirational]` 状态标已诚实化（不给未实现代码写现在时断言）。
- 主线 Android-first：`game-core`（纯 Kotlin/JVM，唯一战斗权威，`BattleRules` 注入、整数公式、RNG 随 state、可回放）+ `native-content`（内容包模型 + validator）。核心种子来自 `files.zip`。
- 本地全量门 = `docs/runbook/LOCAL_DEV.md` 的 Full Current Local Gate（从 `android/` 内跑）。

## 在途任务

无。

## 下一步（V1 Roadmap，起点由用户/下个 session 定，每片走 `skills/ship-slice`）

- 候选：① native content skeleton（手写 sample pack + JSON loader，事件 op 字符串白名单在解码边界）② 最小战斗循环（地图/单位渲染、move/attack/end-turn、事件驱动表现层）③ `android/app` 壳（创建 `:app` + Compose/最小 Activity + gray flavor 补 `:app:detektGrayDebug`/`lintGrayDebug` + AVD `ticketbox_api36_host` smoke + CI 增 Android lane，见 `docs/runbook/CI.md`）。
- 仍 pending 的机器门（落地时补，见各 `[aspirational]`）：Save/Replay 信封 + 拒未来版本测试；converter 模块 + opcode fail-closed（等真实 MOD 样本，不猜）。
- 纪律：改 Kotlin 走 `android-detekt-discipline`；搬/删/改签名走 `safe-code-change`；出码后无外部评审走 `adversarial-review`；CI 红走 `ci-red-triage`。
