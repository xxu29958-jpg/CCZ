# Handoff

## 当前真实状态

- 仓库原本是 Godot tactical RPG 模板，仍保留 `assets/`、`data/`、`project.godot`。
- 项目主线已定为 Android-first：Android 壳 + Kotlin 确定性核心 + native content pack。
- Godot 只作为参考 / 样机材料，不作为最终运行时架构。
- 用户提供的 `files.zip` 是核心种子：`Domain.kt`、`Formula.kt`、`Resolver.kt`、`Battle.kt`、`Rng.kt`、`SelfTest.kt`。
- 文档已按知识分层落位：入口、交接、通用规则、CCZ 专属规则、架构、ADR、runbook、roadmap、audits、skills。
- 当前 Android workspace 已有 `:game-core` 和 `:native-content` 两个 JVM/Kotlin 模块。
- `game-core` 是唯一战斗权威；Android UI 未来只负责渲染和输入。
- `BattleRules` 已替代全局可变战斗规则，规则以不可变 value object 显式传入。
- `:game-core:test`、`:native-content:test`、`:game-core:runSelfTest`、`:native-content:runSelfTest` 已跑通。
- `:game-core:detektMain`、`:native-content:detektMain`、`:game-core:detektTest`、`:native-content:detektTest` 已按 detekt 2.0.0-alpha.3 跑通。
- 本项目自己的 Gradle Wrapper 已生成在 `android/`。

## 在途任务

- 准备下一刀 Android `app` 壳。
- 不再继续扩旧 Godot 工程。
- 不在运行时引入老 MOD 格式解析。

## 下一步

1. 创建 `android/app`。
2. 接入 Android SDK / Compose / 最小 Activity。
3. 加 gray/internal flavor 后补 `:app:detektGrayDebug`、`:app:detektGrayDebugUnitTest`、`:app:lintGrayDebug`。
4. 用本机 AVD `ticketbox_api36_host` 做启动 smoke test。
