# 2026-06-22 小票夹工程金矿移植 backlog

## 背景

开采 `E:\xiaopiaojia`（成熟 FastAPI 记账后端，CCZ 规则的 fork 来源，31.6k LOC / 453 测试 / ~50 docs，被真实 CI + 生产打磨）里**可移植到 CCZ** 的工程实践。方法：5-lens × 务实 triage workflow（28 agent），23 候选分四档。原则（同 `2026-06-20-rules-overhaul-vs-xiaopiaojia.md`）：**可推导半移植，挣来半边跑边长，域专属（网络 / OCR / 金额 / 多租户）跳过**。本文件是后续移植的真相源。

## 已移植（port-now）

| 项 | PR | 落地 |
|---|---|---|
| **高内聚低耦合总纲** | #41 | `GENERAL_ENGINEERING_RULES.md §Module Boundaries` 顶部总纲，`[review-only]`（fork 静默丢失，捡回） |
| **注释纪律** | #41 | `GENERAL §Comment Discipline`（WHY 不复述 WHAT，落地"注释克制"） |
| **baseline 叠债 tripwire** | #41 | `GENERAL §Kotlin Quality Gate`（超阈值冻结单元不得加新功能） |
| **`RejectReason→phrase` 层** | #42 | `:app RejectPhrase.kt`，穷尽 when fail-closed（比 `errors.py` dict+fallback 更强：编译期强制） |
| **detekt `ignoreSingleWhenExpression`** | #42 | `detekt.yml` 门细化（穷尽映射 when 不被圈复杂度误伤） |
| **依赖方向门** | #44 | 根 gradle `assertModuleDependencyDirection`，模块集从 `settings.gradle.kts` 驱动 fail-closed，强制 `native-content/save-io/app → game-core` 单向 DAG；**把高内聚低耦合总纲从 `[review-only]` 升 `[machine-gated]`**。对抗审抓到初版 over-claim fail-closed（只扫硬编码模块）已修。 |
| **文本/编码 lint 门** | #46 | 根 gradle `verifyTextEncoding`，扫 `git ls-files` 跟踪文件，强制 UTF-8 + `.ps1` BOM + 无 U+FFFD；`§Windows/PowerShell Rules` 升 `[machine-gated]`。对抗审抓 3 confirmed（fail-open 空扫描 / 非 ASCII 路径八进制转义 / `.ps1` 大小写）已修。 |
| **依赖版本审计门** | #48 | 根 gradle `assertStableDependencyVersions`，扫所有构建脚本（root + settings + 各 settings 登记模块 `build.gradle.kts`，模块集从 settings 读 fail-closed）+ wrapper `distributionUrl` 的版本字面量（plugin DSL / detekt toolVersion / Maven 坐标 / gradle 发行版），非纯数字点分且非 RELEASE/FINAL/GA 即判预发布 fail，除非精确版本串在 `adrSanctioned`（锁步 ADR-0003 detekt alpha 例外）。`GENERAL §Dependency Governance` 的「禁 alpha/beta/rc/snapshot」条升 `[machine-gated]`，尊重 ADR-0003 豁免。门含自扫描，注释示例刻意避开 regex（镜像依赖方向门 `...` 占位）。 |

## 待移植（adapt-then-port，需改造适配引擎域；按优先级）

1. ~~依赖方向门~~ **已落地（#44，见上）** —— 高内聚低耦合总纲已升 `[machine-gated]`，兑现「规则要么有门要么诚实标注」元规则（"标杆 = 规则有牙"）。
2. ~~文本/编码 lint 门~~ **已落地（#46，见上）** —— `verifyTextEncoding` 把 `§Windows/PowerShell Rules` 升 `[machine-gated]`。
3. ~~依赖版本审计门~~ **已落地（#48，见上）** —— `assertStableDependencyVersions` 禁 alpha/beta/rc/snapshot 进主线，`adrSanctioned` 白名单尊重 detekt alpha ADR-0003 豁免（锁步：升级被豁免预发布须同 diff 改 ADR + 白名单），把 `§Dependency Governance` 禁预发布条升 `[machine-gated]`。
4. ~~per-slice report 习惯~~ **已落地（#62）** —— line 3 run-on 已 terse 化（去掉每轮重述全部 PR，改指向「已合并 PR」列表 + 分片记录 + PR 描述/git log）；逐 PR 明细的单一真相源是 squash-merge 的 PR 描述 + git log（每片一 commit），不再在状态行重述。ship-slice step 8（#61）已立「码/文档/测试三对账」防漂移。
5. ~~`KNOWN_ISSUES.md` 分级 ledger~~ **已落地（#60）** —— `docs/KNOWN_ISSUES.md`：P0/P1/P2 + design-contract-vs-defect 判定，收口散在 handoff / 规则 / KDoc 的 dormant caveat / defer 项。
6. ~~rollback runbook 具体步骤~~ **已落地（#61）** —— `docs/runbook/BACKUP_RESTORE.md`（顺带修其「无存档系统」stale 态）加编号 rollback 步骤（git revert merged slice）+ save-schema 回滚风险边界。
7. ~~ship-slice 加 code-doc-test reconciliation step~~ **已落地（#61）** —— `skills/ship-slice/SKILL.md` step 8「码 / 文档 / 测试三对账」（push 前对账行为↔测试、契约↔规则/KDoc/HANDOFF/API/KNOWN_ISSUES），不建 per-slice 文件。
8. ~~`BRANCH_BASELINE.md`~~ **已落地（#62）** —— `docs/runbook/BRANCH_BASELINE.md`（精简版）：main 唯一 baseline + squash-merge（每片一 commit）+ 每片 off 最新 main 开分支 + 不复用 stale 分支 + 不改写 main 历史。

## 等阶段（defer，带触发器）

- **依赖审计脚本**（机器门实现）—— 待真有联网审计需求；源 420 行网络脚本前提在 CCZ 不成立，是重写非移植。
- **`REFERENCES.md` 集中源登记** —— 待 link-audit 脚本落地，届时把散在规则文件的官方 URL 收口。
- **`RELEASE_PACKAGING.md`（签名）** —— 待首个 release `signingConfig` / keystore-env 进 `:app`（现 `assembleGrayRelease` 未签名）。
- **真机验收 runbook + Pass/Pending/Fail 模板** —— 待 V1 P5「Android Playable Slice」上真机（AVD `ticketbox_api36_host`）。
- **PR-chain 状态 ledger** —— 多片链 / deferred handoff 复杂到需要时。

## 跳过（域专属，无 CCZ 对应）

- **unhandled error catch-all**（网络异常不泄漏内部）、**日志脱敏**（PII / 路径掩码）、**网络边界门**（loopback 鉴权）、**`DATA_RETENTION.md`**（数据层生命周期 + uploads 清理）。CCZ 离线单机、无网络、无 DB、IO 隔离在 `:save-io`，这些面不存在。
