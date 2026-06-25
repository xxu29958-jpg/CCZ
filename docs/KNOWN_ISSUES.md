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
| 事件 placement op 的 `OUT_OF_BOUNDS`/`IMPASSABLE`：部署 + mid-battle 均 live，仅旧装配器 dormant | `BattleOps.spawn`/`move` 的越界/不可通行校验仅在 `ScriptContext.map` 传入时生效（`OCCUPIED` 恒生效）。**`CampaignAssembler` 的单一 `ScriptContext`（reserves+map）现同时驱动部署（`SScript.pre`，PR1/PR2）与 mid-battle 触发器（`TriggerRunner.tick` 经 `BattleReducer` 接进 `:app` 运行时，PR4）→ 两路的越界/墙拒均 live**（`CampaignAssemblerTest` 钉墙格 spawn 拒）。**仅剩 dormant**：旧 `BattleAssembler.scriptContext`（不传 map，仅 `ContentSelfTest` 用）。决策见 ADR-0004，`PlacementReject` KDoc 诚实标注。 | 随旧 `BattleAssembler` 退役转尽 live。 |
| loadout opt-in fail-closed | 配了 loadout 的单位只能用其内技能；未配的回退全技能表（向后兼容默认）。 | 内容驱动层为每个单位填 loadout 后，删 `legalSkills` 的 `?: skills.keys.toList()` 与 `loadoutAllows` 的 `?: return true`，转全严格（内聚触发器 ③，不要提前单做）。 |
| 移动到自身格消耗「移动」 | 行动经济下 move-to-self 是合法的原地待命，且置 `moved`——之后仍可攻、但不可再移。 | 不变（Fire-Emblem 语义）。 |
| 敌方击杀主将 → DEFEAT 现为 live | demo `lose=[ProtectAlive("guan")]` 此前 dormant（敌空转），敌方 AI（#56）落地后转 live。 | 已 live。 |

## P1 — 真实功能缺口（依赖它的工作落地前处理）

| 项 | 说明 | 触发 / 前置 |
|---|---|---|
| 暂无 | 本轮已补 `CampaignReplayDriver`：`:app` 组合根把 bundled content 的 battle tables / R-script 表传给 `SaveLoader` 与 `ScenarioReplayer`，同时覆盖成功重放、未知 script → `CORRUPT_SCENARIO`、choice 不完整 → `INCOMPLETE_REPLAY` 三条路径。此前 S7 caveat（必须传 script 表）已由 app 级测试守住。 | 继续处理下方 P2 / design-contract 项。 |

## P2 — 已知限制 / 体验毛刺（可排期）

| 项 | 说明 | 触发 / 精修方向 |
|---|---|---|
| ~~敌方回合无逐击动画~~ **已落地** | 敌方回合曾在单次 `endTurn()` 内原子结算、只留最后一击徽章。现 `BattleReducer.endTurnFrames` 暴露逐命令帧序列、`:app` `BattleScreen` 经 `LaunchedEffect` 定时回放(input 锁定),每击各自飘字。`endTurn`=`endTurnFrames().last()` 故权威态/回放/golden 零碰(纯表现层时序);失败模式仅美观(authority 不变)。 | 内聚触发器 ⑤「effects 逐事件时序」的「逐帧 ui 序列」基底已建。 |
| mid-trigger 结构事件未上表现层 | `TriggerRunner.tick`（PR4）已接进 `:app`，但触发器的**结构事件**（`UnitSpawned`/`UnitRemoved` 及 fail-closed 的 `SpawnRejected`/`MoveRejected`/`HpSetRejected`）只改 state、未投影成徽章/专属日志行——故失败的 mid-spawn 在 UI 当前**静默**（state 仍 fail-closed 正确，仅表现层不可见）。demo `mid` 为空故现 dormant。 | per-blow playback 已建逐帧基底;剩把结构事件（尤其落点拒）经 `effectsOf`/日志投影成徽章/专属行(需 `PlacementReject` 措辞层,非复用 `RejectReason` 的 `RejectPhrase`)。`tickAfter` KDoc 诚实标注。 |
| 敌方 AI 侵略式 + 焦点火力 + 支援治疗 + 主动 debuff | 远程 kiting（#58）+ **目标低血优先**（focus-fire）+ **支援治疗**（有 heal 技且半血以下友军在射程内 → 治最残）+ **主动 debuff**（ADR 0008：`debuffCommand` 对**负值定时 enemy StatDelta** 触发 → 对最高 ATK 在射程未被同 stat 影响的敌施放,使 ailment/effect 系统双向化;程远志 `skill_7`「破甲」def-20;slot heal→debuff→attack,「已影响则跳过」防 pacifist;`amount<0 && duration>0` 防 buff 敌/instant 重铸)已落地。剩：**AI 仍不会主动 buff（友军）/ 施加 ailment（沉默/麻痹）**——这些目前只有玩家会施放（敌方持有则 dormant;被沉默敌 healer 降普攻、被麻痹敌单位降 Wait,皆不死循环）；**本方单位行动序**仍按 unit id（需兵种类别/速度模型）；**pure-healer 站位**无「贴友军待命」模型;无「防守区域 / 仇恨」模式。 | 行动序 + pure-healer 站位 + AI auto-buff/ailment 启发式 + 仇恨 后续片（源游戏行为记于 `EnemyAi` KDoc）。 |
| 中途存档会丢行动经济 | `BattleProgress.moved`/`acted` 刻意不持久化（save 只存全新开局态，replay 重导出）。若未来加「中途存档」捕获非全新态，会静默丢失（已耗尽单位重载后可再动）。 | 「中途存档」功能落地时：持久化 `moved`/`acted`（schema bump）或在 encode 处断言开局态。守卫注已在 `SaveMappers.stateDto`。 |
| `:gameplay` 模块未拆 | `EnemyAi`/`Gameplay`/`WinLose`/`TriggerRunner`（battle loop / AI / trigger runner）暂居 `game-core`；架构计划是 P2/P3 落地后拆独立 `:gameplay` 模块。 | 当 game-core 因这些件膨胀 / 需独立测试边界时（`CCZ_ENGINE_RULES` §Runtime Direction）。 |
| 大兴山 `skill_2`（刘备 疗伤）是**手添进生成内容**；真数据 heal importer 因编码歧义**暂缓** | ADR 0008 Phase 1 wiring 给刘备的治疗战法手工加进 `ccz_daxingshan/campaign.json`（生成自 `LegacyPackGenerator`，非门）；`LegacySkillMapper` 仍**只搬伤害**，故手动 regen 会抹掉 skill_2。现 app 实跑 committed 内容,playable（已改用 `PERCENT_MAX` 30% maxHP,与旧作「恢复生命X%」同构）。**同理** `skill_3`(张飞 咆哮 buff)/`skill_4`(关羽 震慑 debuff)/`skill_5`(关羽 沉默 ailment)/`skill_6`(张飞 麻痹 ailment)亦是**引擎自有、手添进生成内容的 demo 效果技**(非 importer 产物),手动 regen 同样会抹掉——为让 buff/debuff/ailment 在真实关卡可玩可见而手补,待 importer 扩到非伤害效果后一并自动化。**真 importer 调查结论**:heal 战法 = `dic_skill{type19, object1, hurt_num0}` → `dic_seid`(`seid∈{60,61,65}`),其量级**无法可靠取**:(a) 结构化 `special_effe`(如 `"999&10"`)不决定 heal % —— seid60/62 同为 `"999&10"`,但 `Info` 的 ASCII % 后缀却 30%/20% 不同;(b) 旧作文本字段(`sename`/`Info` 的中文)在当前抽取里是**双重编码 mojibake、字节已丢(U+FFFD),中文不可读**,只剩 ASCII % 后缀。故 heal 语义系由 `type19`/`object1` 结构推断、量级无可靠来源 —— 照搬即**猜**,违 Evidence Rule。故引擎侧 `PERCENT_MAX` 已就位(本片),但**自动 importer 暂缓**待编码厘清。 | 厘清 `dic_seid` heal 魔法数编码后(或人工标定每 seid 的 %),扩 `LegacySkillMapper` 把 heal `seid` 映成 `SkillEffect.Heal(PERCENT_MAX, %)`,届时 regen 删手补。 |

## `:app` 未来门（`[aspirational]`，见 `CCZ_ENGINE_RULES.md` §Android App Gates）

R8 release 编译（`isMinifyEnabled` 现 false）、apksigner 指纹钉（无 signingConfig/keystore）、
Room schema drift（无 DB）、emulator smoke（AVD `ticketbox_api36_host`，无 instrumented test）。
对应能力入仓后逐一翻 `[machine-gated]`。

## 跳过 / 阻塞（非自主可扫）

- **Converter（P4）+ opcode fail-closed**：等真实 MOD 样本，不猜（`CCZ_ENGINE_RULES` §Evidence Rule）。
- 域专属移植项（网络 / OCR / 多租户 / DB 生命周期）：CCZ 无此面，见 backlog「跳过」。
