# 0008 战法效果系统:技能效果(Skill Effects)—— 首切确定性治疗(Command.Cast)

Date: 2026-06-25

Status: Proposed

## Context

引擎当前**只建模伤害技能**。核心 `Skill`(`Domain.kt:116-122`)只有 `kind`(`DamageKind{PHYSICAL,STRATEGY}`,`Domain.kt:44`,仅选属性对:PHYSICAL→atk/def、STRATEGY→mat/res)+ `powerCoeff` + `range`。`Resolver.attack`(`Resolver.kt:62-111`)是**单目标伤害管线**:restore RNG → 一次 `Formula.rollHitProfile` → 命中则 1–2 次 `strike()`。`CampaignAssembler.skills()`(`CampaignAssembler.kt:260-263`)构造 core `Skill` 时只取 id/name/kind/powerCoeff/range,**丢弃** `SkillUse.area`/`targeting`(`NativeContent.kt:158-163`)—— 故任何治疗/buff/debuff/范围意图根本到不了引擎。`Combatant.statuses: Set<String>`(`Domain.kt:90`)已序列化往返,但**全程惰性**:由 `BattleOp.setStatus` 写(`BattleOps.kt:111`),**无任何公式读它**。`Event.Healed`/`Event.StatusApplied`(`Battle.kt:28,38`)已存在,但只由 terrain heal(`Resolver.applyTerrainHeal`,`Resolver.kt:39-52`,EndTurn 触发)与脚本 `BattleOps` 发出,从不作为 `Command.Attack` 的技能效果。

**为什么现在做**:真武将目前只能用**授予的普攻 + 仅伤害型战法**(HANDOFF「真实数据进可玩 app」剩余项 ①)。旧作 `dic_skill`(166 条战法)里 **61/166 = 37%** `hurt=0` 纯非伤害;**class-granted 技能里 44%(51/117)非伤害**(治疗/buff/debuff/ailment)。这是当前最大的真实玩法缺口。旧作效果数据已抽出(`C:\Users\Xy172\codex_diag\extracted\json`,见 MEMORY `bgt1-resource-key`):`dic_skill`(战法 + targeting `object` + AoE `atkid` + rider `seid`)、`dic_buff`(19 条 status/stat-mod,带 round 时长 + 概率)、`dic_atk_se`(44 个 AoE 形状模板)、`dic_use_effe`(15 个消耗品效果原语)。当前 `LegacySkillMapper` 只搬 `hurt_num→power_coeff` + `type→PHYSICAL/STRATEGY`,**丢弃全部** object/atkid/seid。

**确定性 / 回放硬约束(压倒性优先,决定本决策的形状)**:

- **RNG 消费序是冻结契约**:仅 `Resolver.attack` 推进 RNG(`Rng` splitmix64 单 `Long`,`Resolver.kt:63` restore / `:81`/`:110` snapshot)。`Formula.rollHitProfile`(`Formula.kt:82-90`)抽序 = **hit → crit → combo → block**;**miss 只抽 1 次**(命中前短路,`Formula.kt:84`),命中抽 4 次。`Formula.damage` 纯整数、零 RNG。combo 第二击复用已 roll 的 profile、**不再抽**。
- **`GoldenReplayTest`** 钉死 seed 12345 + 固定 2-attack 场景的**事件流 + 每单位 `hp=` 行 + 终态 `rng=-1028001813962157855`**,是 `RULES_VERSION` 的 tripwire。任何多抽/重排/漏抽都改这个 `Long`。
- **回放 = `initialState`(含 `rngState`)+ 有序 folded commands**(`SaveLoader.replay`,`SaveLoader.kt:93`);终态**重导出、从不存**。两独立版本轴(`SaveLoader.check`):`saveSchemaVersion`(未来值拒、旧值接受)+ `rulesVersion`(须 `== RULES_VERSION=1` 精确相等,否则 `RULES_VERSION_MISMATCH`)。schema bump 唯一先例 v1→v2(加可选 `scenarios`,默认空保后向兼容)。`SaveCodec` strict:`ignoreUnknownKeys=false`、`encodeDefaults=true`、`classDiscriminator="type"`(未知命令 kind fail-closed)。
- **必须 bump `RULES_VERSION`(`Formula.kt:21`,现 =1)的情形**:公式常量变、RNG 消费序/次数变、或任何让 golden 默认场景输出改变的规则变化。
- **game-core 是唯一战斗权威**;表现层零战斗真相;新效果必须在 game-core 决断、纯整数(走 `Formula.mulDiv` TRUNCATE、无浮点)。

**旧作效果数据形状(矿,非蓝图;只读参考,勿 1:1 复刻)**:active 战法 archetype 比例(166 条)—— 伤害 85、ally stat-buff 22、heal 9、enemy debuff 9、ailment(毒/麻/沉默/混乱)8、召唤 6、吸血/吸蓝 5、回蓝 5、控制 4、净化 3、追加行动 3、天气 3。targeting 经 `object`(0=敌/113、1=友或自/42、2=战场/3、3=召唤/6、4=自身位移/2)。`dic_buff` = **%/flat 双模 + 固定时长 + 概率**(每 stat-mod +15%/5 回合;毒 5 回合、麻 2、沉默/盲/眩/乱 3)。AoE 数据驱动(`dic_atk_se` 44 模板,~15 被引用)。effect **可组合挂在伤害技能上**(skid73 狱焰之舞 = 伤害 100 + seid9 毒)。被动层 `dic_seid`(1728 行/227 type 族,常驻 trait/aura)是**另一关注点**,留独立 ADR。`dic_seid`/`dic_atk_se` 的数百 type-id 是**矿** —— collapse 成本引擎自有的小可组合 op 集(heal / modify-stat / apply-status / cleanse / …),不照搬不透明数值表。

**评审**:并行吃透上述模型 + 旧作数据后,出 3 个设计方案(① minimal-first Cast-Heal、② 完整 framework + per-combatant ActiveEffect 态、③ hybrid-staged sealed 模型 + 后置 effect pipeline),再过对抗式「确定性判官」镜头压测每个方案的 RNG 序 / golden / save 正确性。

## Decision

采纳 **方案 ① 的可证零扰动 Cast-Heal 作首切** + **方案 ③ 的 sealed `SkillEffect` 端态架构作目标** + **方案 ②/③ 对 magnitude gate 的纠正**。

核心理念:技能效果是 **game-core 权威的战斗规则**。非伤害效果走**独立 `Command.Cast` / `Resolver.cast` 分支**,绝不并进 `attack` 的 RNG 热路径。首切只做**确定性、零 RNG、零新持久态**的单目标治疗;端态是一套可组合的 sealed `SkillEffect`(未来可 ride 伤害),但每一片独立可发、golden-safe,凡触 RNG 序 / 持久态 / 公式输出者一律 gated 在显式 `RULES_VERSION` / schema bump 之后。

### 效果模型(引擎自有)

sealed `SkillEffect` 置于 game-core `model/`(镜像 `DamageKind` 跨模块先例,native-content 直接复用、无内容依赖)。首切**单变体**:

```kotlin
sealed interface SkillEffect
enum class EffectTarget { SELF, ALLY }           // 引擎自有目标带;首切略 ENEMY
data class Heal(val target: EffectTarget, val amount: Int) : SkillEffect   // 首切仅 FLAT 整数
```

效果以**新增可选尾字段**挂到 core `Skill`:`val effects: List<SkillEffect> = emptyList()`(additive,默认空 → `DEMO_SKILLS`/`GoldenReplayTest` 现有 `Skill` 构造逐字节不变)。技能两态:**伤害技能**(`effects` 空)走 `Command.Attack`/`Resolver.attack` **完全不变**;**效果技能**(`effects` 非空)走 `Command.Cast`/`Resolver.cast`。

### 为何独立 `Command.Cast`(而非并进 `attack`)

`Resolver.attack` 无条件 restore RNG + roll 4 draws + 假定单 **ENEMY** defender,且 `CommandValidator.checkAttack` 拒 `SELF_TARGET`/`TARGET_FRIENDLY`(`CommandValidator.kt:72,76`)。治疗打**自/友**且必须**零 RNG**。把治疗硬塞进 attack 要么放松 validator(破伤害契约)、要么在 RNG 热路径里特判(扰动抽序)。独立 `Cast`/`cast` 让伤害路径 **100% 不动**(高内聚:伤害真相只在 `attack()`、效果真相只在 `cast()`,各一处);且 `GoldenReplayTest` 记**每单位 `hp=` 行**(`GoldenReplayTest.kt:38-39`),新 `Cast` golden **从不经过**该路径 → 这是拿到**可证零扰动**首切的唯一方式。伤害 + rider 复合技(旧作 skid73 伤害 + 毒)刻意推迟到「attack-path effect-rider + RNG 序」片。

### 确定性保证

`cast()` **零 RNG**:不 `restore`/`nextX`/`snapshot`,`rngState` 原样返回(同 Move/Wait/EndTurn 守恒 RNG)。`GoldenReplayTest.COMMANDS`(`:87-92`)只有 Move/Attack/EndTurn、其 `Skill` 默认空 `effects` → **事件流 + 每单位 hp 行 + `rng=-1028001813962157855` 逐字节不变,无需 regen、不 bump `RULES_VERSION`**。新增 `CastHealGoldenTest` 钉死零 RNG 输出(其 `rng=` 等于初始 seed 不变,文档化「Cast 不动 RNG」契约)。治疗整数:`gained = (t.hp + amount).coerceAtMost(t.hpMax) - t.hp`(复用 `applyTerrainHeal` 同形 clamp);未来 PERCENT 模式走 `Formula.mulDiv(hpMax, pct, 100, TRUNCATE)`,无浮点。

### save / replay / RULES_VERSION

**schema v2→v3**,唯一原因 = 新持久 **Command 变体** `Cast`(folded commands 须序列化):

- `Battle.kt` 加 `data class Cast(caster, target, skill) : Command`。`SaveDto` 加 `CommandDto.Cast @SerialName("cast")` + `SaveMappers` 对应臂。`classDiscriminator="type"` 下旧 build 见 `"cast"` fail-closed,故 `SUPPORTED_SAVE_SCHEMA_VERSION` 2→3 **同步**(`SaveEnvelope.kt:26`),旧 build 经 FUTURE gate 干净拒新存档,而非死在未知 discriminator。
- `SaveLoader.commandIntegrity`(`:66-78`)穷尽枚举每 Command,**编译期强制**新增 `Cast` 臂:`caster/target ∉ unitIds || skill ∉ skills → CORRUPT_COMMAND`(非穷尽 `when` 不编译 = 想要的 fail-closed)。
- **零新持久 per-unit 态**(只动已序列化的 `hp`)。back-compat:v2 存档无 `Cast`,v3 下照常载(`check` 接受旧 schema);v3 存档被 v2 build 经 discriminator + FUTURE gate 干净拒。回放安全无隐藏态:`Cast→heal` 确定 + 零 RNG,(initialState + folded Cast)完全可重导。`statuses` 首切不碰。

`RULES_VERSION` **不 bump**(保持 1):无公式常量变、零 RNG 抽点、不改任何伤害输出 → 既有存档(rulesVersion=1)仍可载。

### magnitude gate(纠正方案 ① 的「静默发散可接受」)

heal `amount` 是**内容授权技能参数,与既有 `powerCoeff` 同一档**:两者都由内容派生的 `BattleContext.skills` 在 replay fold 时被 Resolver 读取。系统**本就**依赖「replay 对匹配内容(`contentVersion`)折叠」—— `commandIntegrity` 只校引用存在、**不校参数值**,ADR 0007 已把回放与其 `contentVersion` 绑定(「replay 靠快照 + 引用存在性」)。故 heal amount **不引入新回放属性**:伤害技能 `powerCoeff` 的内容漂移**今天就同样**会让重折叠静默发散。

**裁定**:heal magnitude 归 **`contentVersion`**(非 `RULES_VERSION`),与 `powerCoeff` 一致;`RULES_VERSION` 仍只保留给公式常量 / RNG 序 / 抽数。这**既驳回方案 ① 的「静默发散没关系」措辞**(它误判为新的/可接受的缺口),**也避免方案 ②/③ 的过纠**(为 heal 调参 bump `RULES_VERSION` 会与 `powerCoeff` 待遇不一致)。诚实陈述:回放对内容漂移的正确性是**既有、已接受、横切**属性(`powerCoeff`/`range` 同样适用);加固它(把 `contentVersion` 钉进存档 + load gate)是独立未来事、且会一并覆盖 `powerCoeff`,本片范围外,记 Rollback。

### 内容授权(fail-closed 两层,保持现有形状)

- **解码层(throws `ContentDecodeException`)**:`SkillDto` 加 `effects: List<SkillEffectDto> = emptyList()`(默认空 → v1 包在 `ignoreUnknownKeys=false` 下仍解码,沿用 `TerrainDto.passable` 同款 additive 先例)。`SkillEffectDto` 为多态 sealed,用 `classDiscriminator="type"`(`EventDto.kt` 既有 op 白名单机制),首切注册 `@SerialName("heal") HealDto(target, amount)`;未知 effect `type` 无注册子类 → fail-closed。`Decoders` 加 `decodeEffectTarget`(self|ally,大小写不敏感,miss 抛),并把当前 dead string `targeting` 正式化为白名单(关掉 area/targeting 的 fail-open 缺口)。
- **校验层(返 `List<ValidationIssue>`,不抛)**:`ContentValidator` 加 effects pass —— `amount ≥ 1`、coherence(heal 须 `targeting ∈ {ally, self}`)。镜像 `EventScriptOpCoverageTest` 的穷尽 decode + invalid-fails-closed 测试。
- **装配**:`CampaignAssembler.skills()` 把 `effects`(+ `targeting`)线进 core `Skill`(`area` 仍弃,AoE 推迟)。`mod-import`(`LegacySkillMapper`)**本片不动** —— 把旧作 heal `seid`(object1/type19)接进授权 JSON 是后续 importer 片,且不得伸进引擎 DTO。

### 分阶段实施计划

**Phase 1(本 ADR,先发 —— 确定性单目标 HEAL,回放安全最小切片)**:① game-core model(`SkillEffect`/`Heal`/`EffectTarget` + `Skill.effects` 尾字段);② `Command.Cast`;③ `Resolver.cast`(零 RNG,复用 heal clamp,发既有 `Event.Healed`)+ `Resolver.apply` 臂;④ `CommandValidator.checkCast`(`checkAttack` faction 逻辑**反向**:接受同侧/自身,仍校 actor 资格/存活/range/目标存活,零 RNG);⑤ save v2→v3(`CommandDto.Cast` + mappers + `commandIntegrity` 臂);⑥ content(`effects` DTO + `HealDto` + `Decoders` 白名单 + validator coherence + assembler thread-through);⑦ 测试(`CastHealGoldenTest` 零 RNG / `SaveCodec` Cast round-trip / decode-fails-closed 未知 effect type / validator coherence)+ 同 diff bump `test-count-baseline.txt`。**不 bump `RULES_VERSION`;`GoldenReplayTest` 逐字节不变**。

**Phase 2(确定性瞬时 stat-delta + cleanse,仍无 duration/RNG)**:`StatDelta(Flat)` 折进 `CombatStats` 面板 + `Cleanse` over 既有 `statuses` Set;formalize `area` enum。仍零 RNG / 零新持久态 → 无 bump。

**Phase 3(durations / stacks —— 首个持久态片,刻意延后)**:新增**并列**字段 `Combatant.effectsState: List<StatusInstance{id,remaining,stacks,source}>`(**不** retype `statuses`,避免破 v2/v3 存档与 `SaveCodecTest`),`CombatantDto` 默认字段 + `SUPPORTED_SAVE_SCHEMA_VERSION` 3→4(沿用 v1→v2 默认字段后向兼容法)。时长递减在 `Resolver.endTurn` 确定性、id-sorted、零 RNG(同 terrain heal 相位)。buff 读入公式 / 毒 DoT 改变同场输出 → **bump `RULES_VERSION` 1→2** + 一次性重生成受影响 golden。**buff 读点须穷举**:`+def`/`+evade` 不是单一 `generalDmgPct` 旋钮,须在 defender 读点(`effectiveDef`,`Resolver.kt:88`)与 `rollHitProfile` 内 rates(`Formula.kt:82-88`)折叠 —— 每个被 buff 触及的读点都变成 golden-load-bearing,落地前须列全。

**Phase 4(概率 / RNG 效果)**:概率 proc(`chancePct<100`)的抽点**严格附在 hit→crit→combo→block 之后**,per-effect 按 list 序、AoE per-victim 按 target-id 排序 —— 定义新 per-victim 抽序契约;与 Phase 3 的 `RULES_VERSION=2` **批在一次 bump**(避免连续 bump 反复 invalidate 存档)。同片加 `Command.Cast` 的 effect-only 分支供「伤害 + rider」复合。

**Phase 5+(各自独立 ADR,刻意不在本决策内)**:AoE 多目标伤害(per-victim 抽序契约)、召唤(往 state spawn 单位)、控制/魅惑(临时阵营改派 + 回放含义)、追加行动(行动经济重入)、天气 / 元素 tag、以及整个 1728 行被动 `dic_seid` trait/aura 层。

## Consequences

- **缺口分片关闭**:Phase 1 即让真武将能用治疗战法,首切**可证零** golden/RNG/`RULES_VERSION` 扰动(GoldenReplayTest COMMANDS 无 Cast、Skill 默认空 effects);治疗是唯一能**零新持久态**建模的 archetype(`hp` 已序列化)。
- **新 `Command.Cast` 在 3 处 `when` 触非穷尽**(`Resolver.apply` / `CommandValidator.check` / `SaveLoader.commandIntegrity`,现均无 `else`)—— **编译期强制** fail-closed 处理,是安全特性也是本片主要机械面,须同 diff 在三处补齐。
- **schema v2→v3 单向**:新 build 读旧存档正常,旧 build 经 FUTURE gate 干净拒新存档(须 lockstep bump,否则旧 build 死在未知 discriminator 而非干净拒)。
- **magnitude = `contentVersion`**(与 `powerCoeff` 一致):heal 调参不 bump `RULES_VERSION`、不 invalidate 存档;回放须对匹配 `contentVersion` 重折叠 —— 这是 ADR 0007 已立的既有契约,非本片新增风险。
- **dormant 诚实**:Phase 1 仅发 `SELF/ALLY` + `FLAT`;ENEMY 目标效果、PERCENT、duration、status、AoE、伤害+rider 复合、概率效果**全部推迟** + `SkillEffect`/`Heal` KDoc 诚实标注(不给未实现能力写现在时断言)。`statuses: Set<String>` 保持惰性不碰。
- **fail-open 缺口收窄**:`targeting` 从 dead free string 升为 fail-closed 白名单(`area` 待 AoE 片)。
- 测试策略:`CastHealGoldenTest`(零 RNG 恒等)+ `SaveCodec` Cast 往返 + 未知 effect type fail-closed + validator coherence;`assertTestCountEqualsBaseline` 须同 diff bump(CI 硬门)。
- 利:首切**可证**零扰动、复用既有 `Event.Healed`/clamp/多态白名单/默认字段范式、高内聚(伤害 vs 效果各一处)。弊:「技能使用」首切分裂为 `Attack`/`Cast` 两命令(伤害+rider 复合待后),且为单一新命令付一次 schema bump。

## Rollback Conditions

- 若产品要**伤害 + rider 复合技**(伤害技附带毒/晕):须把 effect-rider 接进 `attack` post-damage,若 proc 则定义 RNG 序 → `RULES_VERSION=2` + golden regen;更新本 ADR。
- 若要 **timed buff/debuff/ailment**:走 Phase 3 持久态 + schema v3→v4 +(触公式/输出)`RULES_VERSION` 1→2,并先列全 buff 读点;按需 retype `statuses` 须复核既有存档兼容。
- 若要**概率效果**:走 Phase 4 RNG 序片,与 Phase 3 批进**单次** `RULES_VERSION` bump。
- 若产品要把**回放对内容漂移做硬 gate**(heal amount / `powerCoeff` 漂移即拒载而非静默重折叠):须把 `contentVersion` 钉进 `SaveEnvelope` + `SaveLoader.check` 加轴,**横切覆盖所有内容授权参数**(不止 heal),另开 ADR。
- 若 magnitude gate 的「heal = content 参数」裁定被产品推翻(要 heal 调参即 invalidate 存档):须把 heal 常量移到 rules 面并 bump `RULES_VERSION`,同时复核 `powerCoeff` 是否同样处理(否则待遇不一致)。
