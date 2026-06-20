# Engineering Rules

本文件是规则入口，不承载全部细则。

## Rule Layers

```text
GENERAL_ENGINEERING_RULES.md   跨项目通用工程规则
CCZ_ENGINE_RULES.md            CCZ 现代战棋引擎专属规则
```

## Priority

1. 当前任务先读 `HANDOFF.md`。
2. 任何代码改动必须符合 `GENERAL_ENGINEERING_RULES.md`。
3. 任何引擎、转换器、战斗、内容包改动必须符合 `CCZ_ENGINE_RULES.md`。
4. 规则例外必须进入 `docs/DECISIONS/`，不能只写在聊天里。
5. 机器能查的规则必须逐步进入 test / CI / validator。

## Short Version

```text
目录表达架构
分层限制调用
模型分清边界
数据格式固定
错误结构统一
运行产物不入库
脚本只做运维
规则例外进 ADR
机器能查的交给 CI
```

