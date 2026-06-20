# Release Runbook

> 完整发布流程尚未配置（CCZ 还在 P0/P1 阶段）。本文件定义发布门硬清单与回滚预案，随模块成熟逐步机器化。
> 总纲：没有可执行检查的规范只算文档；下面任一不过，不发。

## Release Gate（硬清单）

发布前必须全部通过（当前从 `android/` 内本地跑，CI 接线后变自动门——见 `docs/runbook/CI.md`）：

```text
detekt type-resolving 双任务 (:*:detektMain :*:detektTest，未来 :app:detektGrayDebug*)
单元测试 (:game-core:test :native-content:test)
确定性自检 (:game-core:runSelfTest :native-content:runSelfTest)
native content pack validator 通过
golden / replay 回归测试通过 (GoldenReplayTest)
test-count 基线一致 (assertTestCountEqualsBaseline)
release 构建无 debug 入口 / 调试菜单 / 隐藏后门
版本字段有意更新（engine/native_format/content/converter/save_schema）
```

未来 Android app 增补：`lintGrayDebug`、`assertAndroidTestCountEqualsBaseline`、`assembleGrayRelease`（R8）、apksigner 指纹钉、emulator smoke。

## Rollback

引擎有多条独立版本轴（见 `docs/architecture/VERSION.md`），回滚必须分轴评估：

- **代码回滚**：引擎版本回退后，旧版本写出的 save 是否仍可读？运行时遇到**更新**的 `save_schema_version` 必须 fail-closed 拒绝，不得半读。
- **内容包不兼容**：运行时遇到不支持的 `native_format_version` 必须拒绝并提示重新转换，不静默降级。
- **converter 版本回退**：converter 版本变化不牵连 engine 版本；回退 converter 不应使已生成的合法 native pack 失效（除非 `native_format_version` 同时变）。
- **存档兼容关系**：内容包版本与存档的兼容关系必须显式记录，回滚前查表确认。

## Backup Scope

见 `docs/runbook/BACKUP_RESTORE.md`：save 文件、replay 命令日志、内容包版本、`rng_state`、save schema version。Restore 必须拒绝比运行时更新的 save。
