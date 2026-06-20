# Ship Slice

把**一个** CCZ 切片走完整个闭环：off 最新 base 开分支 → 写码 → 跑**真**本地门 → 对抗审 → push →（CI 接线后）红黑分诊 → 合并（需用户授权）→ 同步文档。几件事会本地绿但悄悄咬人：跑了门的**子集**、base 不新、删/加测试没 bump count baseline、自合默认分支没授权。

## 流程

1. 读 `HANDOFF.md` + 相关 architecture / rules。
2. **off 最新 base 开分支**：先确认本地 base == 目标（`git log`/`git status`），别在 stale base 上长出意外提交。
3. 定最小可测范围。
4. **在对的层实现**：战斗权威只在 `game-core`；内容校验在 `native-content`；表现层只渲染 event/state、不持第二套真相、不绕核改结果。
5. **跑真本地门全套**（不是子集，见 `docs/runbook/LOCAL_DEV.md` Full Current Local Gate）：

   ```powershell
   cd android
   .\gradlew.bat --no-daemon :game-core:test :native-content:test :game-core:runSelfTest :native-content:runSelfTest :game-core:detektMain :native-content:detektMain :game-core:detektTest :native-content:detektTest
   ```

   detekt 必须是 type-resolving 的 `detektMain`/`detektTest`（见 `android-detekt-discipline`）。
6. **加/删测试时**，若已有 test-count baseline gate，同 diff bump（镜像 xiaopiaojia 的严格等值 pr-delta lane）。
7. **对抗审**（codex 缺位时走 `adversarial-review`，并发 ≤2-3）。
8. push；CI 接线后按 `ci-red-triage` 分诊红。
9. **合并需用户显式授权**——不自合默认分支（见 xiaopiaojia 同款纪律）。
10. 收尾只更新「拥有该知识的那层文档」+ HANDOFF 写当前状态 + 下一步，不写成百科。

## 暗雷

- 只跑 `:*:test` 子集 = 漏掉 detekt + selfTest，门形同虚设。
- 用 plain `detekt` 而非 `detektMain` → 静默跳过类型解析规则。
- 本地 main ≠ remote → PR 带进意外提交。
- detekt daemon 与（未来）CI runner 共享：CI 在飞时别 `gradlew --stop`；本地门命令已带 `--no-daemon`。
