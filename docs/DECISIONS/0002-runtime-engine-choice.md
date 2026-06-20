# 0002 Runtime Engine Choice

Date: 2026-06-20

## Context

Candidates include Android native/Kotlin, Godot, Unity, and Unreal. The repository was forked from a Godot template (since removed), while the provided core seed is Kotlin.

## Decision

Use Android native shell + Kotlin deterministic core + custom 2D tactics presentation as the main line.

Godot remains reference/prototype material. Unity and Unreal are not adopted as the main runtime line.

## Why

- The hard problem is deterministic rules, event flow, content conversion, save/replay, and validation.
- Kotlin core can be tested outside Android and reused inside Android.
- Commercial engines add editor/rendering value, but also add licensing, lifecycle, and integration weight.
- Godot is useful, but the existing repository is not an Android/Kotlin runtime.

## References

- Android Kotlin: https://developer.android.com/kotlin
- Kotlin Android overview: https://kotlinlang.org/docs/android-overview.html
- Godot license: https://godotengine.org/license/
- Unity runtime fee status: https://unity.com/blog/unity-is-canceling-the-runtime-fee
- Unreal EULA: https://www.unrealengine.com/eula/unreal

## External Facts (2026-06 核实)

引用的引擎商业条款在 2026-06 仍准确：Unity Runtime Fee 已于 2024-09 彻底取消、回到订阅制（Personal 免费 + Pro/Enterprise 订阅）；Godot 仍 MIT 永久免费、零版税；Unreal 标准 5% 版税（首 $1M 营收豁免，符合条件可降 3.5%）。**即便如此本决策不变**——选 Kotlin 原生的依据是问题形态（确定性规则 / 事件流 / 内容转换 / 存档回放 / 校验），不是引擎价格；定价变化不触发下方任何 Rollback Condition。建 `:app` 前应再核一次官方页面。

## Rollback Conditions

Reopen this decision if:

- the product becomes 3D-first;
- multi-platform release becomes immediate and Android-native is too narrow;
- a legally reusable engine appears;
- a team requirement mandates Unity/Godot/Unreal art or level pipelines.
