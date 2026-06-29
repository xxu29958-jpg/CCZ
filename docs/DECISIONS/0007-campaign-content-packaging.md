# 0007 战役内容打包:两包(生成战斗 + 手写过场)+ 单一 CampaignRuntime/版本

Date: 2026-06-25

Status: Accepted

Update 2026-06-30: the generated battle pack for stage 1 is now
`content/legacy_stage_1/campaign.json`, produced by `LegacyStagePackGenerator` through
`:mod-import:generateLegacyStage -PstageId=1`. The older Daxingshan-specific `LegacyPackGenerator` /
`ccz_daxingshan_full` path has been removed; the two-pack decision still applies to generated battle data plus
the authored `ccz_daxingshan/intro.json` scenario pack.

## Context

真实战役(大兴山之战)由两部分内容组成,来源不同:

- **战斗包** `ccz_daxingshan/campaign.json`:由 `LegacyPackGenerator`(离线转换器)从真实解密表**生成** —— 真实兵种/武将/地形/地图 + s-script 部署。`LegacyBattleBuilder.PackEvents` 只发 `s_scripts`,不发 `r_scripts`。
- **过场包** `ccz_daxingshan/intro.json`:**手写**叙事内容 —— 桃园三兄弟战前 r-script(旧作表无本引擎的场景脚本,只能作者手写)。

此前 `RealBattle`(读战斗包)与 `RealScenario`(读过场包)是两个各自为政的访问器;`CampaignReplayDriver` 却接的是 demo 包(`DemoBattle`/`CampaignContent`),回放的不是真实战役。需要单一真相源让战斗宿主、过场宿主、回放驱动都读同一处。

回放安全的事实(决定本决策形状):回放不依赖 `contentVersion` —— 战斗轴 = `SaveEnvelope.initialState` 快照(面板已固化)+ `commandIntegrity`(命令引用对初始 roster/skill 可解析);过场轴 = `ScenarioReplayer` 要求 r-script id 存在(`UNKNOWN_SCRIPT`)+ choices 完整(`INCOMPLETE_REPLAY`)。`SaveLoader` **不校验** `contentVersion`(只记录,见 ADR 0006:内容漂移属 `contentVersion` 轴、不破回放)。

## Decision

**保留两包,由单一 `CampaignRuntime` 统一;两包作为一个战役发布、共享一个 `content_version`;不动存档 schema。**

- `CampaignRuntime`(`:app` 组合层)= 真实战役的单一真相源:装载两包、装配战斗(`CampaignAssembler`)、暴露 `context/initialState/script/scriptContext` + `introScript`/`rScripts` + `contentVersion`。`RealBattle`/`RealScenario` 并入它;`MainActivity` 战斗/过场宿主与 `CampaignReplayDriver` 都改读它。回放驱动因此接真实战斗包 + 真实 r-script 表 + 真实 `contentVersion`。
- 两包**共享同一 `content_version`**,作为一个战役发布版本一起演进,由测试钉死(`theTwoPacksShareOneCampaignContentVersion`)。存档只记这一个 `contentVersion`,足够。

### 为何不合并成一个包

战斗包是**生成**产物(`LegacyPackGenerator` 重跑即覆盖);把手写过场塞进它,要么让生成器去发/保留叙事 r-script(生成与手写混在一文件、重生成会清掉手写),要么每次重生成手工补回 —— 都脏。把**生成(开矿)**与**手写(叙事)**内容分文件,生成工作流干净、矿/手写来源诚实可分。

### 为何不在存档里记两个版本

回放与 `contentVersion` 无关(快照 + 引用存在性,见 Context),为纯诊断性的"两包各自版本"去 bump `save_schema_version` 是过度工程。单一共享版本 + `CampaignRuntime` 作单一源已够;真要独立版本另见 Rollback。

## Consequences

- 单一真相源:战役的包、id、版本只在 `CampaignRuntime` 一处;回放驱动回放真实战役(非 demo)。`RealBattle`/`RealScenario` 消失(其测试并入 `CampaignRuntimeTest`)。
- 共享版本不变式由测试守护:两包 `content_version` 漂移即测试红(tripwire)。
- 回放安全不变:纯 `:app` 组合层 + 内容轴;`game-core`/RNG/`Formula`/golden/`RULES_VERSION` 零碰;demo 包 + `DemoBattle` + `BattleReducerTest` 无回归。
- `DemoBattle`/`CampaignContent` 保留作 `BattleReducer` 测试夹具(非战役运行时路径)。

## Rollback Conditions

- 若过场包需与战斗包**独立发布/独立版本**(如过场 DLC 单独更新),则 `SaveVersions` 增 `scenarioContentVersion` 轴(`save_schema_version` bump)+ `SaveLoader` 按需校验,另开 ADR。
- 若生成器将来能发 `r_scripts`(`PackEvents` 扩 r-script)且产品要单包,则合并两包、`CampaignRuntime` 简化为单包,更新本 ADR。
