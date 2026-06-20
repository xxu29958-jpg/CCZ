# AGENTS

本文件是 CCZ 现代战棋引擎的入口文档。AI 和人接手项目时，先读这里。

## 必读顺序

1. `HANDOFF.md`：只看当前状态、在途任务、下一步。
2. `docs/rules/ENGINEERING_RULES.md`：规则入口。
3. `docs/rules/GENERAL_ENGINEERING_RULES.md`：跨项目通用硬规。
4. `docs/rules/CCZ_ENGINE_RULES.md`：CCZ 专属硬规。
5. `docs/architecture/ARCHITECTURE.md`：系统真相源。
6. `docs/architecture/PROJECT_STRUCTURE.md`：目录和模块位置。
7. `docs/architecture/NATIVE_CONTENT_PACK.md`：运行时内容契约。
8. `docs/DECISIONS/`：关键路线为什么这么选。
9. `docs/runbook/`：启动、CI、发布、备份恢复等操作说明。

## 项目边界

- 本项目是 Android-first 的现代战棋引擎。
- 曹操传 6.x MOD 是内容来源，不是运行时兼容对象。
- 运行时只加载 native content pack。
- 老 R/S 剧本 opcode、Data 表、Imsg、老图档和 Star 扩展都属于离线转换器。
- `game-core` 是唯一战斗权威，必须能脱离 Android 单独测试和回放。
- Android UI 只负责渲染和输入，不能计算伤害、消费 RNG 或直接改战斗状态。
- 现有 Godot 工程只做参考和样机，不是主线运行时。

## 知识归位

```text
AGENTS.md                 项目入口、AI 工作规则、必读顺序、硬边界
HANDOFF.md                当前真实状态、在途任务、下一步
docs/rules/               长期工程宪法
docs/architecture/        系统真相源：架构、目录、契约、安全、版本
docs/DECISIONS/           ADR：为什么这么选，何时回滚
docs/runbook/             操作手册：本地开发、CI、发布、备份恢复
docs/roadmap/             阶段路线和验收标准
docs/audits/              已知但暂不修的问题和风险
skills/                   可重复施工流程
archive/                  历史交接、旧上下文、已废弃资料
tests / CI                机器裁判，能自动检查的不要只写文档
```

## 工作规则

- 不把 `HANDOFF.md` 写成百科或记忆仓库。
- 不把长期原则塞进当前交接。
- 不把重复流程写成长段聊天记录，沉淀到 `skills/`。
- 不把历史争论留在规则里，沉淀到 `docs/DECISIONS/` 或 `archive/`。
- 新增长期规则时，先确认它属于 rules、architecture、runbook、skill、audit 还是 ADR。
- 能用测试、validator、CI gate 检查的约束，优先加机器检查。
- 不确定现代外部事实时，查官方来源，不靠印象。

## 当前施工方向

下一阶段优先建立：

```text
android/
  game-core/       纯 Kotlin 确定性核心
  native-content/     原生内容包模型和校验
  app/                Android 壳与表现层
```

压缩包里的 Kotlin 核心应落到 Android/Kotlin 主线结构中，不再散放在仓库根目录。
