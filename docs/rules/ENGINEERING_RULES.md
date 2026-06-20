# Engineering Rules

> 规则版本 v0.1.0，2026-06-20，自 xiaopiaojia 工程规范 v1.7.0 fork。
> 本文件是规则入口，不承载全部细则。

## Rule Layers

```text
GENERAL_ENGINEERING_RULES.md   跨项目通用工程规则
CCZ_ENGINE_RULES.md            CCZ 现代战棋引擎专属规则
```

## Reading & Process Order

1. 当前任务先读 `HANDOFF.md`。
2. 任何代码改动必须符合 `GENERAL_ENGINEERING_RULES.md`。
3. 任何引擎 / 转换器 / 战斗 / 内容包改动必须符合 `CCZ_ENGINE_RULES.md`。
4. 规则例外必须进入 `docs/DECISIONS/`，不能只写在聊天里。
5. 机器能查的规则必须逐步进入 test / CI / validator。

## Conflict Resolution Order

目标冲突时按以下优先级裁决（高者压低者）：

1. **确定性与回放正确性**——同输入同输出；RNG 消费顺序契约不破。
2. **规则 / 公式正确**——整数公式、显式取整、规则常量来自显式规则对象或内容包。
3. **状态边界清晰**——battle state 只走 resolver 演进；表现层不持第二套真相。
4. **内容包 / 存档兼容可控**——版本轴显式，未来版本拒绝（fail closed）。
5. **可维护、可测试**。
6. **表现层体验**。
7. **UI 手感 / 表现**。

裁决根：**公式 / 规则常量改 = 规则版本变化；宁可破坏手感，不可破坏回放**。架构可以简单，但边界必须清楚；实现可以先小，但必须可替换、可回滚、可验证。

## Short Version

```text
目录表达架构
分层限制调用
模型分清边界
数据格式固定
契约结构统一
运行产物不入库
脚本只做运维
规则例外进 ADR
机器能查的交给 CI
```

## 变更管理

遵循语义化版本 `MAJOR.MINOR.PATCH`：

- `MAJOR`：改变工程边界（如「不做」→「做」）。
- `MINOR`：增加规则。
- `PATCH`：修正措辞 / 格式。

改规则 = ADR（若是放宽 / 边界变动）+ `git log` + 在对应规则文件头部 bump 版本号，**不另起 CHANGELOG_RULES 文件**。规则放宽必须进 `docs/DECISIONS/` 并写明回收条件。
