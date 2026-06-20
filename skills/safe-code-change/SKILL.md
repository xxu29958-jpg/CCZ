# Safe Code Change

搬 / 改名 / 删 / 改签名前，先扫消费面和隐藏耦合——这类伤害不会让普通 `gradlew test` 变红，是悄悄上线的。

## 触发场景

- 把函数 / 字符串 / 常量从一个文件搬到另一个（抽公共、去重、重组模块）。
- 改类 / 函数 / 字段 / 枚举名，批量 rename。
- 批量删测试或删一整套机制（瘦身）。
- 改 data class 签名、加字段、改 `copy()` / claim / CAS 传参。
- 改内部 RNG 消费点或公式常量。

## 动手前必扫

1. **消费面**：grep 所有调用点 / import / 测试引用。搬走一个符号可能留下：
   - 一个还断言旧路径 / 旧字符串的测试（false-red）。
   - 一个 detekt-baseline 里按旧文件路径登记的 ID（纯改 A 文件却让 detekt lane 红）。
   - 一个按路径 keyed 的豁免 / 审计条目指向旧位置。
2. **值表达式**：改字段名时，确认所有**读它的值表达式**也跟着改——一个 `WHERE x == <旧属性>` / `copy(old=...)` 漏改会**静默匹配零行 / no-op**，编译还过。
3. **唯一覆盖**：删一个「migration / contract / roundtrip」测试前，确认它不是某存活代码的唯一不变量覆盖。CCZ 里 `ReplayContractTest`（回放确定性）和 `ContentValidatorTest`（fail-closed）就是这种唯一覆盖，删它们 = 拆掉守门。
4. **确定性契约（CCZ 专属）**：改 RNG roll 顺序 / 计数、或改公式常量，是**规则契约变更**——即便同种子确定性仍成立，也必须反映到 `rulesVersion` + golden replay（见 `CCZ_ENGINE_RULES.md` §Battle Formula / Save/Replay）。不能当成普通重构。

## 验证

改完跑全套本地门（见 `docs/runbook/LOCAL_DEV.md` Full Current Local Gate），不只跑被改模块的单测。删/改测试若碰 test-count baseline，同 diff 声明 bump。
