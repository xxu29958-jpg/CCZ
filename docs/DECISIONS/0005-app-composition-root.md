# 0005 :app 作组合根，依赖 :native-content（content → 运行时装配）

Date: 2026-06-23

Status: Accepted

## Context

战役驱动层要把 native-content 包变成可玩战斗：加载 → 校验 → 装配 `BattleContext` + 部署初始 `BattleState` + 入口 `SScript`，交给 `:app` 的 reducer 经 `Gameplay` 驱动。装配逻辑（`CampaignAssembler`）已落 `:native-content`（PR #63），但 `:app` 此前**只依赖 `:game-core`**（`assertModuleDependencyDirection` 机器门钉死 `native-content/save-io/app → game-core` 单向 DAG）。要让 `:app` 加载并装配内容，须让它能引用 `:native-content`。

`KNOWN_ISSUES` 把这记为前置架构决策，两个候选：**(a)** `:app` 直接依赖 `:native-content`（app 作组合根）；**(b)** 新建组合模块（如 `:campaign`）夹在中间。高内聚低耦合是 `:game-core` 的一等硬要求（`GENERAL_ENGINEERING_RULES §Module Boundaries`），故须谨慎选向并机器守。

## Decision

**选 (a)：`:app` 作组合根，新增 `:app → :native-content` 依赖边。**

- DAG 变为 `app → native-content → game-core`（`game-core` 仍零依赖），**无环、单向**——组合根在 DAG 顶端 wire 多个域模块是标准组合根模式，不破坏内聚（`:app` 只用 `:native-content` 的公开 API：`ContentJsonLoader`/`ContentValidator`/`CampaignAssembler`，不伸进其内部）。
- 装配逻辑留 `:native-content`（`CampaignAssembler`，content→engine 映射的天然归属，已与 `BattleAssembler` 同处）；`:app` 只持 `CampaignContent`（其内置 demo 内容包）+ 薄 Compose wiring。
- 不建新 `:campaign` 模块（YAGNI）：当前无第二个组合根消费者；过早抽象反增耦合面。
- `assertModuleDependencyDirection` 的 `allowed` DAG 同步加 `"app" to setOf("game-core", "native-content")`——机器门继续 fail-closed 守边界（任何越界 / 反向边仍报错）。

## Consequences

- `:app` 现可加载 + 装配真实内容；`DemoBattle` 由硬编码 core-type 种子重写为 `CampaignAssembler.assemble(CampaignContent.pack(), …)` 的薄访问器（content→运行时端到端，既有 `:app` reducer 测试转为对装配产物的端到端覆盖）。
- `:app`（Android 模块）现传递依赖 `kotlinx-serialization-json`（经 `:native-content`）——纯 JVM，无 Android 冲突（CI android-gate 验证）。
- 后续状态（非本决策）：mid-battle 触发器 `tick` 已接进 reducer；intro 过场已转 content（`events.portrait_subjects` 声明非战斗说话人，`DemoScenario` 为薄访问器）；replay 接线已由 `CampaignReplayDriver` 承接，`:app` 组合根把 content-derived battle tables / R-script 表传给 `SaveLoader` 与 `ScenarioReplayer`，S7 caveat 已有 app 单测覆盖。

## Rollback Conditions

- 若出现第二个组合根消费者（如独立工具 / 服务端装配），或 `:app` 因装配编排膨胀需独立可测边界：再抽 `:campaign` 组合模块，把 `CampaignContent` + wiring 移入，`:app → :campaign → native-content → game-core`，更新本 ADR + 依赖门。
- 若依赖方向门迁移到 type-safe `projects.*` / version-catalog 边：须同步更新门的文本解析（门 KDoc 已诚实标注此覆盖边界）。
