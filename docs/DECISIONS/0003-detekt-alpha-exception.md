# 0003 detekt 2.0 alpha 例外

Date: 2026-06-20

Status: Accepted

## Context

`GENERAL_ENGINEERING_RULES.md` 的依赖治理规定「禁止 alpha / beta 依赖进入主线，唯一例外须有 ADR」。当前 Kotlin 质量门 pin 的是 `detekt 2.0.0-alpha.3`（预发布），构成对该规则的例外，必须记录。

选 detekt 2.0 而非 1.23.x stable 的原因：2.0 线与项目 Kotlin（2.2.x，将升 2.3.x）的编译器/类型解析对齐更好，type-resolving 任务（`detektMain`/`detektTest`）行为是 CCZ 质量门的核心——plain detekt 任务会静默跳过需要类型解析的规则。1.x 在新 Kotlin 上 type-resolution 兼容性较差。

## Decision

主线 pin `detekt 2.0.0-alpha.3` 作为唯一的 alpha 依赖例外。版本权威 pin 在 `android/build.gradle.kts` 与各模块 `build.gradle.kts` 的 `detekt { toolVersion }`；阈值权威在 `android/config/detekt/detekt.yml`。

## Consequences

- 享受 2.0 线的 type-resolution 与新 Kotlin 兼容性。
- 承担预发布风险：config schema / 规则行为可能在 alpha 间变化，升级时需复核 `detekt.yml` 键与跑通双任务。
- 这是依赖治理「禁 alpha」的**唯一**挂账例外；新增任何 alpha/beta 依赖须另开 ADR。

## Recycle / Rollback Conditions

- **回收（升正式）**：detekt 2.0 出 **stable** 时，立即升正式版并关闭本例外。
- **中途升 pre-release**：`alpha.5` 等更新预发布可在**独立 slice**采纳，须跑通 `:game-core:detektMain :native-content:detektMain` 双任务 + 复核 `detekt.yml` 键，不顺手夹带进无关改动。
- **放弃 2.0 线**：若 2.0 alpha 出现阻塞性回归且短期无 stable，回退到 detekt 1.23.x stable 并接受其 type-resolution 限制，更新本 ADR。

References:

```text
https://detekt.dev/docs/gettingstarted/type-resolution/
https://detekt.dev/changelog
https://plugins.gradle.org/plugin/dev.detekt
```
