# 0002 Runtime Engine Choice

Date: 2026-06-20

## Context

Candidates include Android native/Kotlin, Godot, Unity, and Unreal. The repository currently contains Godot project material, while the provided core seed is Kotlin.

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

## Rollback Conditions

Reopen this decision if:

- the product becomes 3D-first;
- multi-platform release becomes immediate and Android-native is too narrow;
- a legally reusable engine appears;
- a team requirement mandates Unity/Godot/Unreal art or level pipelines.
