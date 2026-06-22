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

## 待移植（adapt-then-port，需改造适配引擎域；按优先级）

1. ~~依赖方向门~~ **已落地（#44，见上「已移植」）** —— 高内聚低耦合总纲已升 `[machine-gated]`，兑现「规则要么有门要么诚实标注」元规则（"标杆 = 规则有牙"）。
2. **文本/编码 lint 门**（P1，机器门）—— gradle `verifyTextEncoding`：UTF-8 有效性 + `.ps1` UTF-8-BOM + mojibake 标记扫描（CCZ 是 Windows + 中文文档，已有 `§Windows / PowerShell Rules` 但无机器门）。
3. **依赖版本审计门**（P1，机器门）—— 禁 alpha/beta/rc/snapshot 进主线。**注意**：CCZ 已有 detekt alpha ADR 例外（`0003`），门必须尊重该豁免，否则自相矛盾。
4. **per-slice report 习惯**（P1，doc）—— 缓解 `HANDOFF.md` 顶部 run-on（line 3 ~1800 字每轮重述全部 PR）。可做成 ship-slice 收尾产出或 `docs/reports/`。
5. **`KNOWN_ISSUES.md` 分级 ledger**（P2，doc）—— P0/P1/P2 + design-contract-vs-defect 判定，收口现散在 handoff 的 dormant caveat / defer 项。
6. **rollback runbook 具体步骤**（P2，doc）—— 触发器**已 fire**（`:save-io` 存档已落地）：补 `docs/runbook/` 带编号恢复步骤 + 风险边界，不只是原则。
7. **ship-slice 加 code-doc-test reconciliation step**（P2，process）—— `skills/ship-slice/SKILL.md` 加一步「码 / 文档 / 测试三对账」，不建 per-slice 文件（2.7k sloc 太重）。
8. **`BRANCH_BASELINE.md`**（P3，doc）—— 精简版（非源 176 行）：mainline SHA + squash-merge caveat，抵抗 stale-branch 混乱。

## 等阶段（defer，带触发器）

- **依赖审计脚本**（机器门实现）—— 待真有联网审计需求；源 420 行网络脚本前提在 CCZ 不成立，是重写非移植。
- **`REFERENCES.md` 集中源登记** —— 待 link-audit 脚本落地，届时把散在规则文件的官方 URL 收口。
- **`RELEASE_PACKAGING.md`（签名）** —— 待首个 release `signingConfig` / keystore-env 进 `:app`（现 `assembleGrayRelease` 未签名）。
- **真机验收 runbook + Pass/Pending/Fail 模板** —— 待 V1 P5「Android Playable Slice」上真机（AVD `ticketbox_api36_host`）。
- **PR-chain 状态 ledger** —— 多片链 / deferred handoff 复杂到需要时。

## 跳过（域专属，无 CCZ 对应）

- **unhandled error catch-all**（网络异常不泄漏内部）、**日志脱敏**（PII / 路径掩码）、**网络边界门**（loopback 鉴权）、**`DATA_RETENTION.md`**（数据层生命周期 + uploads 清理）。CCZ 离线单机、无网络、无 DB、IO 隔离在 `:save-io`，这些面不存在。
