# Add Event Op

新增一个 native event op（场景事件 / 战中触发 / 战斗动作）时走这条流程。这是 CCZ「挣来那半」的领域 skill——对应 xiaopiaojia 的 add-mutating-route：一个 op 加进来若漏了白名单校验或 fail-closed，运行时会静默吃下未知 op，而 `SECURITY.md` 还写着「已拒绝」。

## 触发场景

- 给 `Event Model`（见 `CCZ_ENGINE_RULES.md`）的场景事件流 / S 剧本触发 / 战斗动作加一个 op。
- 扩 `EventTables` / `RScript` / `SScript` 的指令集。

## 流程

1. 在 native event 模型加该 op（`com.ccz.core.event` / `EventTables`）。
2. **加白名单校验**：在 `ContentValidator.validateEvents` 把它纳入已知 op 白名单 + 必填字段 / 引用校验。**未知 op 必须 fail-closed**（拒绝 + path-keyed `ValidationIssue` 定位到事件 / 字段）。
3. 加 gameplay 执行行为（command → resolver → event）。
4. 需要的话加 presentation event 输出（表现层只消费 event）。
5. 加一个 sample content 片段。
6. **加两类测试**：合法内容通过 + 非法 / 未知 op 被拒（仿 `unknownReferencesFailClosed`）。
7. 若加了测试且有 test-count baseline，同 diff bump。

## 暗雷（Evidence Rule）

- **不把 opcode 猜测写成事实**：精确 opcode / 指令参数 / 字节布局必须等真实样本或工具导出再实现；结构层可冻结，参数层不猜。
- 没有真实样本支撑的 legacy opcode 假设不进 converter / validator。
- op 白名单是安全边界：加 op 必须同步更新 `CCZ_ENGINE_RULES.md` §Native Content Pack 的校验状态标注和 `SECURITY.md`，三处不漂移。
