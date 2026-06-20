# Handoff

> 状态：引擎实现阶段。一轮自主扫荡完成 —— P2 引擎半 + P3 事件系统引擎侧 + P1 内容 loader（tables）+ Save/Replay 信封 + 事件脚本 JSON 解码（R/S 多态 op）均**已合并** main，CI 全绿，**无在途任务**。

## 当前真实状态

- `main` 顶部为 Save 信封片（合并后），本地 == 远端、工作树干净。CI（`.github/workflows/ci.yml`）每 push/PR 跑全套门。test=100。已合并 PR：#3 P2 合法性、#4 P3a 胜负、#5 P3b 触发器、#6 P1 loader，及 Save 信封 PR、事件脚本 JSON 解码 PR。
- **game-core（P2 引擎半）**：`Gameplay.submit` 合法性闸门 + `CommandValidator` + `BattleMap`/`MoveReachability` + `BattleContext` + `Skill.range`。回合归属按侧；Move 自身格 = no-op。
- **game-core（P3 事件引擎）**：`WinLose`（胜负，OR/lose 优先/sticky）+ `TriggerRunner`（`tick`=触发器→settle，`applyPre/Post`）+ `TriggerConditions`（6 条，整数 HP）+ `BattleOps`（9 ops，纯无 RNG）+ `ScriptContext.reserves`。`once` 经 `BattleProgress.firedTriggers`。`outcome`/`vars`/`firedTriggers` 收进 `BattleProgress`。
- **game-core（Save/Replay 信封）**：`com.ccz.core.save` —— `SaveEnvelope`/`SaveVersions`（六版本轴）+ `SaveLoader`（fail-closed 拒未来 save schema / 规则漂移，折叠已接受命令确定性回放）。on-disk 序列化 codec 未落地（损坏信封以异常 fail-closed）。
- **native-content（P1 JSON loader，仅 tables）**：`com.ccz.contentpack.json` —— `ContentJsonLoader.load(json)`（strict、snake_case、`ignoreUnknownKeys=false`）+ `@Serializable` DTO + 映射器 + 枚举白名单 `Decoders`（fail-closed）。两层 fail-closed：解码 shape → `ContentValidator` 查引用/版本。`game-core` 零 JSON 依赖；`kotlinx-serialization-json:1.7.3` 仅入 `:native-content`。
- **native-content（事件脚本 JSON 解码，P1 续）**：`EventDto`/`EventMappers` —— R/S 脚本的多态 op 层级（`ScenarioOp` 11 / `TriggerCondition` 6 / `BattleOp` 9 / `WinLoseCondition` 6）经 kotlinx sealed + class-discriminator `type` 解码；discriminator 值（显式 snake_case `@SerialName`）**即 op 字符串白名单**——未知 op 无注册子类 → `SerializationException` → `ContentDecodeException`（fail-closed）。含嵌套多态（`BattleOp.Script` 包 `ScenarioOp`）。embedded faction 走 `decodeFaction` 白名单（fail-closed，直接抛 `ContentDecodeException`）；reference 整性仍在下游 `ContentEventValidator`。`EventTables` 现可从 JSON 装载（`ContentDto.events` 默认空、向后兼容）。每 op/condition/win-lose 变体过 decode→map 结构等值断言（防字段错位）。
- 确定性铁律守住：`Resolver`/RNG 消费顺序/伤害公式/`RULES_VERSION`(=1)/golden 全程未动；每片过 2-3 镜头对抗审 0 P1。
- 主线 Android-first：`game-core`（纯 Kotlin/JVM 唯一战斗权威）+ `native-content`（内容模型 + validator + JSON loader）。仍无 `:app` 模块。
- 本地全量门 = `docs/runbook/LOCAL_DEV.md` 的 Full Current Local Gate（从 `android/` 内跑）。
- 仓库自 Godot tactical RPG 模板 fork，上游模板素材已**彻底移除**（含 git 历史）。
- 规则体系已重锚到 CCZ 域并接机器门（PR #1 / #2，详见 `docs/audits/2026-06-20-rules-overhaul-vs-xiaopiaojia.md`）；`[machine-gated]/[review-only]/[aspirational]` 状态标随每片诚实翻转（不给未实现代码写现在时断言）。本轮把命令合法性、win/lose、触发器+ops、JSON 解码边界、Save 拒未来版本从 review-only/aspirational 翻成 machine-gated。

## 在途任务

- 无（一轮扫荡收尾，工作树干净）。

## 下一步（V1 Roadmap，每片走 `skills/ship-slice`）

- ~~事件脚本 JSON 解码（P1 续）~~ **已落地**（本片：多态 op + class-discriminator 白名单 + fail-closed，`EventTables` 从 JSON 装载）。剩余：从 `UnitDef` 装配 `ScriptContext.reserves`（SpawnUnit 运行期模板），让 spawn op 真正取到 reserve unit。
- R 剧本 scenario 流执行：现状 `BattleOps` 把非 SetVar scenario op 透传为 `Event.Scenario`；对话/选项/branch/goto 推进需表现层或 scenario runner（选项 = 可回放 command）。
- Save on-disk codec + 原子写：序列化 `SaveEnvelope`（复用 `kotlinx.serialization`），落地时补命令完整性校验 + §Write & Convert Safety 的原子写机器门。
- P2 渲染半：建 `:app` 壳 + 渲染 map/units + 输入→command（经 `Gameplay.submit`，**不得**绕核改结果）+ 事件驱动表现层。`:app` 落地须补 Android future gates（见 `CCZ_ENGINE_RULES.md` §Android App Future Gates + `docs/runbook/CI.md` Android lane + AVD `ticketbox_api36_host` smoke）。
- 仍 pending：converter 模块 + opcode fail-closed（等真实 MOD 样本，不猜）。
- 纪律：改 Kotlin 走 `android-detekt-discipline`；搬/删/改签名走 `safe-code-change`；出码后无外部评审走 `adversarial-review`；CI 红走 `ci-red-triage`。
