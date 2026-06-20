# CI Red Triage

CI（接线后）红了，先分清真红 vs flake / 幻红，再正确处置。误判两个方向都会上线：把真失败当 flake 放过（回归溜过门），或把良性 flake 当真红、于是 thrash PR（改 base / 空提交 / force-push）去「修」一个幻红。

> CI lane = `.github/workflows/ci.yml`（JVM gate，每次 push/PR）。push 前也先本地跑全套门（见 `ship-slice`）。

## 流程

1. **定位第一个失败 gate**（按 job 级看，不信 run 级聚合）。
2. **本地同命令复现**：用 `docs/runbook/CI.md` / `LOCAL_DEV.md` 里那条原命令跑。
3. **分类**：compile / test / detekt / packaging / environment。
4. **真红 vs flake**：
   - 真红：编译错、断言失败、detekt 规则破、baseline ID 失配——本地能复现。
   - flake：插件 / 依赖下载超时（detekt alpha 从仓库拉）、网络 / gateway 超时、并发取消、ratchet 时钟偏移——本地复现不出、重跑即绿。
5. **修最窄的根因**；**别 thrash**：不靠改 base / 空提交 / force-push 去「修」幻红——那只会掩盖或制造问题。
6. **沉淀**：耐久命令 / 新坑写进 `docs/runbook/CI.md`，不写进 `HANDOFF.md`（除非当前正阻塞）。

## CCZ 暗雷

- detekt 是 type-resolving 任务，CI 必须跑 `:*:detektMain`/`:*:detektTest`（或 `:app:detektGrayDebug*`），plain task 会假绿——见 `android-detekt-discipline`。
- selfTest（`runSelfTest`）失败通常是真的确定性破，不是环境问题，优先当真红查。
