# Detekt Discipline

改 Kotlin（game-core / native-content / 未来 :app）时不踩 detekt 六条复杂度阈值、不误算函数/行数、不手写坏 baseline 条目。detekt 是 **type-resolving** 的，肉眼或对抗审估行数/参数数都不可靠——一个阈值破或一个 baseline ID typo 本地绿、CI 红。

## 触发场景

- 加函数 / 方法 / composable，或给现有的加参数 / 回调。
- 给 interface / fake / test 类加方法。
- 编辑 `android/**/*.kt` 或 `android/**/test/**/*.kt`。
- 手写 / 编辑 detekt baseline。
- 本地跑 detekt。

## 规则

1. **必须跑 type-resolving 任务**：当前 JVM 模块 `:game-core:detektMain :native-content:detektMain` + `:*:detektTest`；未来 `:app:detektGrayDebug :app:detektGrayDebugUnitTest`。普通 `detekt` task 会**静默跳过**需要类型解析的规则（如 LongParameterList）——绿了不代表过。
2. **阈值唯一真相源 = `android/config/detekt/detekt.yml`**（六条：LongMethod / LargeClass / LongParameterList(function+constructor) / CyclomaticComplexMethod / NestedBlockDepth / TooManyFunctions）。不在规则文档复列、不靠记忆，改阈值改 yml。
3. **baseline 是冻结旧债，不是新代码许可**。新代码 / 被改代码必须真达标；默认真简化（抽 helper / 拆函数 / 提取 policy），而不是往 baseline 加一条把它压下去。
4. **不手算行数 / 参数数 / 函数数**——以 type-resolving detekt 的判定为准；改完本地跑一遍再 push。

## 常见触发与解法

- 加方法触 `TooManyFunctions`（类函数数上限）→ 把纯函数提到顶层 / 拆类，而非加 baseline。
- 加参数触 `LongParameterList` → 收进 value object（CCZ 规则要求规则配置以不可变 value object 传入，正好对齐）。
- composable / resolver 变长触 `LongMethod` → 抽子函数。
- 手写 baseline 条目：ID 串、签名、文件路径必须逐字对——typo 不会本地报错，会在慢 CI lane 一轮一轮磁吸式失败。

## 版本

detekt 当前 pin `2.0.0-alpha.3`（alpha 例外见 `docs/DECISIONS/0003-detekt-alpha-exception.md`）；版本权威在 `android/build.gradle.kts` + 各模块 `build.gradle.kts`。
