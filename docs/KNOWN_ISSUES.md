# Known Issues — 分级 ledger

> 收口散在 `HANDOFF.md` / 规则 / KDoc 的 dormant caveat、defer 项、已知限制，分级并区分
> **设计契约（按预期，非缺陷）** 与 **缺陷/限制**。镜像 xiaopiaojia 的 `KNOWN_ISSUES.md` 实践
> （小票夹工程金矿移植 backlog #5，`docs/audits/2026-06-22-xiaopiaojia-port-backlog.md`）。
>
> 分级：**P0** = 主线阻断 / 数据损坏风险；**P1** = 真实功能缺口，需在依赖它的工作落地前处理；
> **P2** = 已知限制 / 体验毛刺，可排期；**design-contract** = 故意如此，记录以免被误当 bug。
> 当前 **无 P0**。

## 设计契约（按预期，非缺陷）

| 项 | 说明 | 何时变 |
|---|---|---|
| 事件 placement op 的 `OUT_OF_BOUNDS`/`IMPASSABLE` 生产 dormant | `BattleOps.spawn`/`move` 的越界/不可通行校验仅在 `ScriptContext.map` 传入时生效；装配层暂不传 map（`BattleAssembler` 无战役驱动），故这两类拒在生产环境休眠（`OCCUPIED` 恒生效）。决策见 ADR-0004，`PlacementReject` KDoc 诚实标注。 | 战役驱动层把 `BattleMap` 接进生产 spawn/move 路径时转 live（见 P1「战役驱动层」）。 |
| loadout opt-in fail-closed | 配了 loadout 的单位只能用其内技能；未配的回退全技能表（向后兼容默认）。 | 内容驱动层为每个单位填 loadout 后，删 `legalSkills` 的 `?: skills.keys.toList()` 与 `loadoutAllows` 的 `?: return true`，转全严格（内聚触发器 ③，不要提前单做）。 |
| 移动到自身格消耗「移动」 | 行动经济下 move-to-self 是合法的原地待命，且置 `moved`——之后仍可攻、但不可再移。 | 不变（Fire-Emblem 语义）。 |
| 敌方击杀主将 → DEFEAT 现为 live | demo `lose=[ProtectAlive("guan")]` 此前 dormant（敌空转），敌方 AI（#56）落地后转 live。 | 已 live。 |

## P1 — 真实功能缺口（依赖它的工作落地前处理）

| 项 | 说明 | 触发 / 前置 |
|---|---|---|
| **战役驱动层（content → 运行时）** | `DemoBattle`/`DemoScenario` 仍是硬编码种子。真实战役需：加载 native-content 包 → 装配 `BattleContext` + 初始 `BattleState`（`MapDef`+terrain → `BattleMap`、`UnitDef`+部署 → 带坐标的单位）→ 接 `SaveLoader`（战斗回放）+ `ScenarioReplayer`（过场回放）。**前置架构决策**：`:app` 依赖方向——`:app` 当前只依赖 `:game-core`（`assertModuleDependencyDirection` 守），加载内容需 `:app → :native-content`（app 作组合根）或新组合模块；须同步更新依赖方向门的 `allowed` DAG。亦解上方 spawn/move 的 `BattleMap` dormant 缺口。 | 大架构片，需 DAG 决策，宜专门推进。 |
| **S7 driver caveat：`SaveLoader.load` 须传 script 表** | 建战役驱动层接 `SaveLoader.load` 时**必须**把已加载 content 的 script 表传进 `scripts`，否则带过场的 v2 存档全判 `CORRUPT_SCENARIO`（默认 `emptyMap` = 不可验即拒，fail-closed）。 | 战役驱动层接线时。 |

## P2 — 已知限制 / 体验毛刺（可排期）

| 项 | 说明 | 触发 / 精修方向 |
|---|---|---|
| 敌方回合无逐击动画 | 敌方回合在单次 `endTurn()` 内原子结算；只有最后一击的伤害徽章留存，中间各击仅进日志（不飘字）。 | 内聚触发器 ⑤「effects 逐事件时序」：需 reducer 暴露逐帧 ui 序列供 UI 按时序播放（改 UI 驱动模型）。 |
| 敌方 AI 仅 v1 侵略式 | 远程 kiting 已落地（#58，趋向可攻位）；但**行动优先级**仍按 unit id（源游戏：低血先动、骑兵先于步兵），且无「防守区域 / 仇恨」模式。 | AI 启发式后续片（联网核实的源游戏行为已记于 `EnemyAi` KDoc）。 |
| 中途存档会丢行动经济 | `BattleProgress.moved`/`acted` 刻意不持久化（save 只存全新开局态，replay 重导出）。若未来加「中途存档」捕获非全新态，会静默丢失（已耗尽单位重载后可再动）。 | 「中途存档」功能落地时：持久化 `moved`/`acted`（schema bump）或在 encode 处断言开局态。守卫注已在 `SaveMappers.stateDto`。 |
| `:gameplay` 模块未拆 | `EnemyAi`/`Gameplay`/`WinLose`/`TriggerRunner`（battle loop / AI / trigger runner）暂居 `game-core`；架构计划是 P2/P3 落地后拆独立 `:gameplay` 模块。 | 当 game-core 因这些件膨胀 / 需独立测试边界时（`CCZ_ENGINE_RULES` §Runtime Direction）。 |
| 事件脚本落点上界未校验 | S1 只设了与地图无关的非负地板；`at` 对具体 `MapDef` 边界的上界需 map-aware 层。 | 同战役驱动层 / map 生产接线。 |
| `manifest.entry` 存在性未校验 | 指向已知 script/map id 的存在性校验缺失（低风险，可下游补）。 | 内容校验下游加强时。 |
| scenario `INCOMPLETE_REPLAY` load 路径完整 parity | choices 不再走通漂移脚本的完整 parity 仍归 `ScenarioReplayer`，待驱动层重跑接线。 | 战役驱动层接线时。 |

## `:app` 未来门（`[aspirational]`，见 `CCZ_ENGINE_RULES.md` §Android App Gates）

R8 release 编译（`isMinifyEnabled` 现 false）、apksigner 指纹钉（无 signingConfig/keystore）、
Room schema drift（无 DB）、emulator smoke（AVD `ticketbox_api36_host`，无 instrumented test）。
对应能力入仓后逐一翻 `[machine-gated]`。

## 跳过 / 阻塞（非自主可扫）

- **Converter（P4）+ opcode fail-closed**：等真实 MOD 样本，不猜（`CCZ_ENGINE_RULES` §Evidence Rule）。
- 域专属移植项（网络 / OCR / 多租户 / DB 生命周期）：CCZ 无此面，见 backlog「跳过」。
