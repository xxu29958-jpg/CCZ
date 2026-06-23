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
| 事件 placement op 的 `OUT_OF_BOUNDS`/`IMPASSABLE`：部署路径已 live，mid-battle 仍 dormant | `BattleOps.spawn`/`move` 的越界/不可通行校验仅在 `ScriptContext.map` 传入时生效（`OCCUPIED` 恒生效）。**`CampaignAssembler`（native-content）的部署路径把 `BattleMap` 接进 `ScriptContext` → 这两类拒在内容驱动部署（`SScript.pre` 的 SpawnUnit）中转 live**（`CampaignAssemblerTest` 钉墙格 spawn 拒），且 PR2 起经 `:app` 的 `DemoBattle` 生效于运行时。**仍 dormant**：① 旧 `BattleAssembler.scriptContext`（不传 map，仅 `ContentSelfTest` 用）；② mid-battle 脚本 spawn/move（触发器 `tick` 尚未接进 `:app` 运行时，生产不跑）。决策见 ADR-0004，`PlacementReject` KDoc 诚实标注。 | 余下两处随 mid-battle `tick` 接线 / 旧 `BattleAssembler` 退役转 live。 |
| loadout opt-in fail-closed | 配了 loadout 的单位只能用其内技能；未配的回退全技能表（向后兼容默认）。 | 内容驱动层为每个单位填 loadout 后，删 `legalSkills` 的 `?: skills.keys.toList()` 与 `loadoutAllows` 的 `?: return true`，转全严格（内聚触发器 ③，不要提前单做）。 |
| 移动到自身格消耗「移动」 | 行动经济下 move-to-self 是合法的原地待命，且置 `moved`——之后仍可攻、但不可再移。 | 不变（Fire-Emblem 语义）。 |
| 敌方击杀主将 → DEFEAT 现为 live | demo `lose=[ProtectAlive("guan")]` 此前 dormant（敌空转），敌方 AI（#56）落地后转 live。 | 已 live。 |

## P1 — 真实功能缺口（依赖它的工作落地前处理）

| 项 | 说明 | 触发 / 前置 |
|---|---|---|
| **战役驱动层（content → 运行时）—— 战斗侧已闭环** | **战斗侧端到端落地**：`CampaignAssembler`（native-content，PR1）`NativeContent`包→`BattleSetup`（`BattleContext` + 经 `TriggerRunner.applyPre` 部署的初始 `BattleState` + 入口 `SScript`），含 `MapDef`+terrain→`BattleMap`（`passable`）、`ClassDef`→`UnitClass`、`SkillDef`→`Skill`、loadout、`SScript.pre` SpawnUnit 部署（map 接线使越界/墙拒 live）。**已接进 `:app`**（PR2，ADR-0005）：`:app → :native-content` 组合根边、`CampaignContent` demo 包、`DemoBattle` 重写为 `CampaignAssembler` 薄访问器（既有 reducer 测试转为对装配产物的端到端覆盖）。**JSON 资源装载已落地**（PR3）：`CampaignContent` 改为从 `app/src/main/resources/content/ccz_demo/campaign.json` 经 `ContentJsonLoader` 解码（真实管线 JSON→loader→validator→assembler→battle 端到端，资源已打进 APK），content 现以**数据**存在而非 Kotlin。**剩余**：① 过场（R 剧本）仍 `DemoScenario` 硬编码——转 content 须先定 `Portrait` 非战斗说话人（如 `cao_cao`）的校验路径；② 接 `SaveLoader`（战斗回放，**S7 caveat** 见下行）/`ScenarioReplayer`（过场回放）；③ mid-battle 触发器 `tick` 接进 reducer（现 `Gameplay.submit` 不跑 `tick`，`SScript.mid` 不触发）。 | 后续片：cutscene→content（先定 Portrait 校验）、replay 接线、mid-trigger tick。 |
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
| `pre` 段 MoveUnit/RemoveUnit 缺单位静默 no-op | `BattleOps.move`/`remove` 对**不在场**单位静默 no-op 且不发拒绝事件（其文档化的 mid-battle fail-safe），故 `CampaignAssembler` 部署时**看不见**这类拒（SpawnUnit 的 spawn 拒可见、已守）。部署请只用 SpawnUnit；`pre` 里 Move/Remove 一个尚未 spawn 的单位 = 作者错误但不报。`CampaignAssembler` KDoc 诚实标注。 | 引擎片：让 `BattleOps` 区分 pre（fail-closed 发拒绝事件）vs mid-battle（静默 no-op），或部署用专门 pre-op runner（改 game-core + `BattleOpTest` 既有 no-op 断言，需独立 ADR/对抗审）。 |
| scenario `INCOMPLETE_REPLAY` load 路径完整 parity | choices 不再走通漂移脚本的完整 parity 仍归 `ScenarioReplayer`，待驱动层重跑接线。 | 战役驱动层接线时。 |

## `:app` 未来门（`[aspirational]`，见 `CCZ_ENGINE_RULES.md` §Android App Gates）

R8 release 编译（`isMinifyEnabled` 现 false）、apksigner 指纹钉（无 signingConfig/keystore）、
Room schema drift（无 DB）、emulator smoke（AVD `ticketbox_api36_host`，无 instrumented test）。
对应能力入仓后逐一翻 `[machine-gated]`。

## 跳过 / 阻塞（非自主可扫）

- **Converter（P4）+ opcode fail-closed**：等真实 MOD 样本，不猜（`CCZ_ENGINE_RULES` §Evidence Rule）。
- 域专属移植项（网络 / OCR / 多租户 / DB 生命周期）：CCZ 无此面，见 backlog「跳过」。
