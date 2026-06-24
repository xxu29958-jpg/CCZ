# 0006 成长/升级系统:导入期成长预算(Budgeted Growth)

Date: 2026-06-24

Status: Proposed

## Context

引擎当前没有成长/升级系统。`level` 字段全链路搬运、零消费,是确证的死字段:`ContentDto.level`(`ContentDto.kt:76`)→ `TableMappers.toUnit`(`TableMappers.kt:42-60`)→ `UnitProfile.level`(`NativeContent.kt:82-87`),到 `BattleAssembler.toReserveCombatant`(`BattleAssembler.kt:32-43`)处被显式丢弃(类注释 `:18` 明言 level "not carried here"),`stats = profile.stats` / `vitals = CombatVitals(profile.hpMax, …)` 是直接复制、不缩放。`LegacyClassMapper.kt:12` 同样丢弃旧作 `dic_job` 的成长字段。game-core 完全不知道"等级"概念——`CombatStats{atk,def,mat,res}`(`Domain.kt:7-12`)的注释明言"五维属性、装备、状态、内容修正都已解析完",公式只吃已算好的整数最终面板,`Combatant` 无 level 字段,`BattleState` 无 XP / 持久成长状态。

为什么现在做:`level` 已经搬到 `UnitProfile` 但激活不了;旧作 `dic_job`(189 兵种成长权重)/ `dic_grade`(6 档评级倍率)/ `dic_hero`(2729 条 1 级基准)数据已抽出(见 MEMORY `bgt1-resource-key`),具备把成长接进引擎的全部输入。把死字段激活、把旧作成长数据真正喂进面板,是 mod-import 真实数据接进引擎这条主线(#73–#77 terrain/move/cost)的自然延伸。

回放/golden 的硬约束(压倒性优先,决定本决策的形状):

- 回放 = `initialState`(含 `rngState`)+ 有序 `commands` 经 `Resolver.apply` 折叠(`SaveLoader.replay`)。字节级一致由三件事决定:**RNG 抽取序**(`Rng` splitmix64,状态是单个 `Long` 存在 `BattleState.rngState`,只有 `attack` 路径 `restore→roll→snapshot`,抽取次数与顺序是契约)、**公式常量**(`DamageRuleSet`/`CounterRuleSet`/`Rounding`)、**`initialState` 内联的属性快照**(`Combatant.stats`/`vitals`/`rates`,replay 不重算)。
- `GoldenReplayTest` pin 的正是这三者的合成,是 `RULES_VERSION` 的 tripwire;其 `player()`/`enemy()` 用硬编码内联 stats(`:57`/`:66`),走默认 `ResolveContext(CLASSES)` 无 map(`:35`),**不经 `BattleAssembler`**。
- 最危险红线:成长若用 `rng.nextInt(...)` 做随机长进并复用 `BattleState.rngState`,会插入新 RNG 抽取点,把后续每次 `attack` 的 hit/crit/combo roll 全部错位 → 所有既有存档/golden 的 `rng=` 终态与事件流发散。
- 必须 bump `RULES_VERSION` 的情形:公式常量变化、RNG 消费顺序/次数变化、任何让 golden 默认场景输出改变的成长/属性公式变化。
- 地形 PR(#74–#81)证明的"不 bump"四件套:新字段经内容/`ResolveContext` 穿入且不写 `SaveEnvelope`、默认惰性中性、零新增 RNG 抽取点、golden 走默认路径不被触发。
- `SaveVersions`(`SaveEnvelope.kt:12-54`)把 `contentVersion` 与 `rulesVersion` 分成独立轴——新单位面板的内容数据漂移属 `contentVersion`,不属 `RULES_VERSION`。

旧作数据形状(只读参考,勿入库):`dic_job` 主属性成长 1–6 离散权重 / `hp_up` 3–15 / `mp_up` 1–5;`dic_grade` 6 档 `effect`(1–6 线性倍率)+ up/down/skip 概率阈值;`dic_hero` 是 1 级基准模板(`level` 恒 1、主属性 ≤500、HP ≤2000);`const.json` 的 9M/40M 是运行时上限钳值(三路叠乘:逐级成长 × 转职 × 装备百分比),是数值通胀产物而非基准,不照搬。

## Decision

采纳评审胜出方案 **「导入期成长预算(Budgeted Growth)」** 为骨干,并嫁接其它方案优点。

核心理念:成长是 **创建期/导入期的纯确定性函数,不是重放期的规则**。单位按目标等级一次性把面板预算成整数,写进 `Combatant.stats`/`vitals` → `SaveEnvelope.initialState` 快照;game-core 永远只看已固化的最终数值,不知道成长表存在。成长表是创建期输入,绝不当重放期输入——绝不走"存档只存 level、加载按成长表重算"那条危险路(成长曲线一变,旧档重算出不同 stats 会无版本拦截地发散)。这与地形 PR 已验证的安全模式同构,只是更保守:连 `ResolveContext` 都不碰,直接在装配处把数值算死。

### 属性成长模型(公式)

逐兵种成长向量 × 等级,truncate 取整(与 `Formula.mulDiv` 的 TRUNCATE 口径一致),可配置 cap 压平膨胀,零 RNG:

```
final = clamp( base + growth * (level - 1) * gradeMulPct / 100 , 0 , cap )
```

- `base` = `UnitProfile.stats` / `hpMax`(旧作 `dic_hero` 的 1 级基准,主属性 ≤500、HP ≤2000,天然低基准锚点);
- `growth` = `ClassGrowth` 对应分量(旧作 `dic_job` 离散权重 atk/def/ints→mat/hp_up);
- `gradeMulPct` = 评级倍率(嫁接 C 的评级维度),来自 `dic_grade.effect` 经 `GrowthConfig.gradeMulPctByGrade` 映射的百分比档(如 100/120/…/200),**默认 100 = 中性**;
- `cap` = `GrowthConfig` 软上限,锚在 base 量级的可控倍数(默认 stat 999 / hp 9999),**不照搬 9M**。

数据结构(`data class ClassGrowth(val atk:Int=0, val def:Int=0, val mat:Int=0, val res:Int=0, val hp:Int=0)`,默认全 0 = 惰性中性)。五维→四维折算:`dic_job` 的 `atk→atk`、`def→def`、`ints→mat`,`res` 旧作无直接来源,首切 **权重 = 0(惰性,不臆造)**;`burst`/`fortune`/`spe` 本切片不进 `CombatStats`(暂忽略,与 `LegacyClassMapper.kt:12` 现状一致)。

### 升级触发

本系统 **不做运行时升级、不做战中 exp、不做战中升级**——这是回放最安全的根本前提。"升级"发生在内容装配/导入期:目标 `level` 是单位的静态输入(`UnitProfile.level`,旧作 `dic_hero.level`,或战役元数据指定的出场等级)。单位以其声明 level 的预算面板出场,整场战斗 level 不变,不存在"升级"事件。养成层(若做)只负责"决定某单位下一场以什么 level 出场",仍走同一预算函数产出面板快照。

### 如何压平 9M

- **期望化、不叠乘**:9M 来自旧作"90 级 × 每级 grade-roll 概率波动"的尾部叠加 + 转职 + 装备百分比三路叠乘;本式取期望值(`growth × gradeMulPct`)线性增长,无方差爆炸,不做百分比叠乘(转职只换 `base + growth` 向量)。
- **可配置 cap**:9M/40M 当旧作数值通胀产物丢弃,默认 cap 锚在 base 量级(level 90 主属约 `base + 89 × 5 × 1 ≈ base + 445`,落在百级量级)。
- **DIVISOR 离线标定法(嫁接 A)**:cap / 曲线默认值不拍脑袋,用真实 `dic_job × 90 级`离线跑表标定,目标终值落在主属性数千、HP 数万的可控量级。

### 确定性保证

纯整数算术 + truncate,无浮点、无 RNG——绝不调用 `Rng`、不复用 `BattleState.rngState`、不新增任何抽取点;对同一 `(base, growth, level, grade)` 输入恒等输出(纯函数,平台无关)。预算必须在写 `initialState` 之前完成。

### 具体接入点(game-core 零改动)

改动面全在 `:native-content`(后续在 `:mod-import`),game-core(`Combatant`/`CombatStats`/`Formula`/`Resolver`/`ResolveContext`/`SaveLoader`)一行不改:

- **唯一数值汇聚点**:`BattleAssembler.toReserveCombatant()`(`BattleAssembler.kt:32-43`)——把 `:40-41` 的 `vitals`/`stats` 直接复制替换为预算输出。
- **签名穿透**:`toReserveCombatant` 是 `UnitDef` 扩展函数拿不到 `ClassDef`,故 `BattleAssembler.reserves(units)`(`:26`)与 `scriptContext`(`:30`)增参 `reserves(units, growthByClass, cfg)`,用默认值兜底保持源码兼容。
- **调用方**:`CampaignAssembler.assemble`(`:113`)已持有 `content.tables.classes`,派生 `growthByClass` 一并传入。
- **数据声明**:`ClassCombat`(`NativeContent.kt:59-63`)加 `growth: ClassGrowth = ClassGrowth()`;`UnitProfile` 可选加 `grade: Int = 0`;`CombatDto`(`ContentDto.kt:56-61`)加可选 `growth`(snake_case);`TableMappers.toClass`(`:26-40`)解码。
- **数据来源(后续)**:`LegacyClassMapper.kt:12` 改为把 `dic_job` 的 atk/def/ints/hp_up 映进 `ClassGrowth`;`dic_grade` 可选产出 grade 倍率表。

### 分阶段实施计划

**第一阶段(回放安全的最小切片,可独立合并):「激活 level,零数据、game-core 零改、golden 不变」**

1. 新建 `GrowthBudget.kt`(纯函数 `budgetStats`/`budgetHp`/`gradeMul`)+ `ClassGrowth`(默认全 0)+ `GrowthConfig`(默认 cap 999/9999、`gradeMulPctByGrade = listOf(100)`)。
2. `BattleAssembler.reserves`/`scriptContext` 增可选参 `growthByClass`(默认 `emptyMap`)+ `cfg`(默认);`toReserveCombatant` 走预算函数。`CampaignAssembler.assemble` 传 `content.tables.classes` 派生的 growth 表(此时全为默认空,gradeMul=100)。
3. `ClassCombat.growth` 默认字段 + `CombatDto` 可选字段 + `TableMappers.toClass` 解码(缺省即空)。
4. 测试(验收红线):空 growth / level=N → `stats == base`(中性恒等,**"空 curve = 恒等"必须用测试钉死**);`growth=5`/`level=10`/`grade=0` → `stats == base+45`(确定性);cap 钳制;同输入跨次调用恒等;assembler 测试证明"无 growth 字段 → 输出与现状逐字节相同"。

回放安全证明随 PR 入测试:所有内容包当前 growth 为空 → `budget` 恒返回 base → reserve 面板逐字节不变 → `GoldenReplayTest` 不变、`RULES_VERSION` 不 bump、`SaveEnvelope`/`SaveLoader` 不动、game-core 不动。

**第二阶段:「旧作真实成长数据导入 + DIVISOR 标定」(内容数据变更,不触发 game-core / golden)**

`LegacyClassMapper` 读 `dic_job` growth 真正喂进 `ClassGrowth`;`dic_grade` → `gradeMulPctByGrade`;用 A 的离线跑表法标定 cap / 曲线默认值;`ContentValidator` 对 growth 字段范围 fail-closed 校验防脏数据。即使接了,内容默认仍惰性,golden 仍走默认路径不触发。新单位面板漂移属 `contentVersion`,内容侧快照守护防无意漂移。

**第三阶段(若产品需要,各自独立 ADR,刻意不在本决策内):**

- 评级转生制(嫁接 C):"等级是进度刻度、品质 grade 决定成长速度、转生 = 重置 level + 升 grade 而非叠乘属性",接到 `GrowthConfig.gradeMulPctByGrade` 上补深度,延续"线性不叠乘"反膨胀纪律。
  - **已落地(品质档乘子维度,`feat/growth-grade-tiers`)**:`GrowthConfig.gradeMulPctByGrade` 默认从惰性 `[100]` 改为**本引擎自有**的 6 档线性梯子 `100/120/…/200`(每档 +20%,档 0 = 中性基准,顶档仅把成长**速度**翻倍),`UnitProfile.grade`(`>=0`,`ProfileDto.grade` 入 JSON 线、`ContentValidator` fail-closed 拒负值)穿进 `BattleAssembler` 预算。**这是自定设计而非复刻旧作评级**:旧作 `dic_grade`(C/B/A/S/X/X+ 六档 + up/down/skip 概率波动)只作"该有品质杠杆"的矿源启发,其档名与概率波动**刻意不复刻**(概率波动违回放确定性,见 Rollback)。旧作 `dic_hero` 无静态 grade;品质档改由 mod-import 在导入期从真实武将战力(`atk+def+ints→mat+burst→res`)锻出 0..5(本引擎自有规则,阈值标定 2729 武将战力分布,非复刻 `dic_grade`)。grade 只放大逐级成长,而旧作单位 level 恒 1 → `growth×(level-1)=0`,故对当前导入面板暂无影响(杠杆真实但惰性),待出场 level>1 或转生层才显化。零新增 RNG、纯整数预算、golden 走默认路径不触发、`RULES_VERSION` 不动。完整转生(重置 level + 升 grade 作进度)仍留独立 ADR。
- 战中 exp / 升级 spine:跨 `Combatant`/`BattleProgress`/`Event`/save schema 的 god-object 风险区,需持久化 + `Command.LevelUp` + `SaveLoader.commandIntegrity` fail-closed 校验;若 golden 默认场景会触发且改变同场后续伤害/事件流,**必须重生成 golden 同步 bump `RULES_VERSION`**。
- 真随机成长(理念保留自 B):若产品要 FE 风随机长进,**只能**用与战斗 RNG 完全隔离的独立 `Rng` 实例 + 独立 seed,纳入有版本号的养成层轴守护,**绝不复用 `BattleState.rngState`**;本决策不引入,留作未来独立 ADR。

## Consequences

- `level` 从死字段激活;旧作成长数据具备落地路径;game-core 零改动,所有变更收敛在内容层装配处,沿用已验证的地形安全范式。
- 对回放 / `RULES_VERSION` 影响 = 无:成长在 `initialState` 写入前折算成整数面板快照,replay 不重算;零新增 RNG 抽取点(纯算术),`attack` 路径 hit→crit→combo→block 抽取序与次数不变;`SaveEnvelope`/`SaveLoader`/`Formula` 常量不动;golden 走默认路径(内联 stats、不经 `BattleAssembler`)输出逐字节不变 → 不重生成 golden → `RULES_VERSION` 保持 1。
- 新单位面板的数据变更属 `contentVersion` 轴而非 `RULES_VERSION`(经 `SaveVersions.kt` 证实两轴独立);既有存档已固化 stats 不受成长曲线变更影响(值快照,不重算),曲线变更只影响新装配单位,天然带版本隔离。
- 先做成 dormant 的部分:第一阶段 `ClassGrowth` 默认全 0、`gradeMulPctByGrade = [100]` → 全链路惰性中性,预算恒等于 base(等同当前行为);评级倍率维度结构就位但默认 100 不生效;`res` 维度权重 0 不臆造;`burst`/`fortune`/`spe`、战中升级、转生、真随机成长全部划在范围外。这些 dormant 状态在 `ClassGrowth` / `GrowthConfig` KDoc 诚实标注,不写现在时断言。
- 利:最保守、表面积最小(不新增 `Grade` 值类、不动 `ResolveContext`)、压平 9M 的旋钮工程化最干净、确定性从结构上保证(纯闭式不可能污染战斗 RNG 序)。弊:首切深度偏薄(只有 level × 权重线性 + 评级倍率,无养成轴);还原度中等(取 `dic_job` 权重但丢弃 `dic_grade` 概率波动轴以保确定性);cap / 曲线是"设计旋钮"非旧作"事实",需产品确认新量级。
- `toReserveCombatant` 签名扩散可能波及 `scriptContext` 便捷函数与测试夹具,用默认参数兜底保持源码兼容(编译期可全暴露)。
- 测试策略:`GrowthBudget` 纯函数单测(中性恒等 / 确定性 / 单调 / cap 钳制 / 跨次恒等);assembler 测试证明"无 growth → 逐字节同现状";第二阶段加内容侧面板快照守护防漂移 + `ContentValidator` 范围 fail-closed。

## Rollback Conditions

- 若产品要求"存档只存 level/exp、加载重算面板":本决策的"成长表是创建期输入"前提被推翻——必须新增有版本号的 `growthVersion` 轴,在 `SaveLoader.check` fail-closed 拦截跨成长曲线版本档,否则违反"宁可拒绝也不破坏回放";须另开 ADR 复核。
- 若产品要求 FE 风真随机成长:不得在本系统内加 RNG;须按第三阶段 B 路线用完全独立、有版本守护的养成层流另开 ADR,绝不复用 `BattleState.rngState`。
- 若引入战中 exp / 升级(第三阶段 spine):一旦其改变同场后续伤害/事件流且 golden 默认场景能触发,必须重生成 golden 并 bump `RULES_VERSION`,同时按需补 `Command.LevelUp` 的 `commandIntegrity` fail-closed 校验;届时本决策的"创建期固化、零回放影响"边界须随之复核。
- 若 `dic_grade` 概率波动 / 转生机制被产品列为必需深度轴:以本决策为骨干嫁接 C 的转生升档(线性不叠乘),更新本 ADR;若深度需求超出嫁接范围,另立 ADR。
