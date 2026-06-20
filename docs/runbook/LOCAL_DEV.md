# Local Development Runbook

## Requirements

- JDK 17 available on PATH or discoverable by Gradle.
- Android SDK installed locally for future app work.
- Use the project wrapper under `android/`.

The current machine has Android SDK at:

```text
C:\Users\Xy172\AppData\Local\Android\Sdk
```

The current machine has an AVD:

```text
ticketbox_api36_host
```

## Version Baseline

单一真相源（其它规则文档指向这里，不复列）。「当前 pin」是仓库实测值，「建 :app 时基线」是 2026-06 联网核实的稳定版——**升级前按官方 URL 复核**，且 Kotlin / Gradle / AGP 升级是独立 slice（须跑全套验证），不顺手夹带。

| 项 | 当前 pin | 建 :app 时基线 (2026-06 核实) | 官方来源 |
|---|---|---|---|
| Kotlin | 2.2.21 | 2.3.20（落后一 minor，可在独立 slice 升） | kotlinlang.org/docs/releases.html |
| detekt | 2.0.0-alpha.3 | 升 alpha.5 需独立 slice 验证；2.0 stable 出即升正式（见 ADR-0003） | detekt.dev/changelog |
| Gradle (wrapper) | 9.4.1 | 9.6.0（9.4.1 = AGP 9.2 最低线，偏边） | docs.gradle.org/current/release-notes.html |
| AGP | 无 :app，未引入 | 9.2.0 | developer.android.com/build/releases/about-agp |
| compileSdk / targetSdk | 无 :app | 37 | developer.android.com/tools/releases/platforms |
| Google Play targetSdk 底线 | — | ≥35（随平台滚动，发版前复核） | developer.android.com/google/play/requirements/target-sdk |
| JDK | 17 | 17（AGP 9.x 默认即 min，**不必升 21**） | developer.android.com/build/jdks |

## Core Verification

From `android/`:

```powershell
cd android
.\gradlew.bat --no-daemon :game-core:runSelfTest :native-content:runSelfTest
.\gradlew.bat --no-daemon :game-core:test :native-content:test
```

Expected output includes:

```text
OK deterministic game-core self-test passed
OK native content validator self-test passed
```

## Kotlin Quality Gate

Run type-resolving detekt tasks:

```powershell
cd android
.\gradlew.bat --no-daemon :game-core:detektMain :native-content:detektMain
.\gradlew.bat --no-daemon :game-core:detektTest :native-content:detektTest
```

Current modules are pure JVM/Kotlin, so the Android app variant tasks do not exist yet.

## Full Current Local Gate

```powershell
cd android
.\gradlew.bat --no-daemon :game-core:test :native-content:test :game-core:runSelfTest :native-content:runSelfTest :game-core:detektMain :native-content:detektMain :game-core:detektTest :native-content:detektTest assertTestCountEqualsBaseline
```

## Android SDK Tools

If `adb` or `emulator` are not on PATH, use:

```powershell
& 'C:\Users\Xy172\AppData\Local\Android\Sdk\platform-tools\adb.exe' version
& 'C:\Users\Xy172\AppData\Local\Android\Sdk\emulator\emulator.exe' -list-avds
```

Do not require WSL, Docker, Linux shell, or PowerShell 7.
