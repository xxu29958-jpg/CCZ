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
| Kotlin (JVM 模块) | 2.2.21 | 2.3.20（落后一 minor，可在独立 slice 升） | kotlinlang.org/docs/releases.html |
| detekt | 2.0.0-alpha.3 | 升 alpha.5 需独立 slice 验证；2.0 stable 出即升正式（见 ADR-0003） | detekt.dev/changelog |
| Gradle (wrapper) | 9.4.1 | 9.6.0（9.4.1 = AGP 9.2 最低线，偏边） | docs.gradle.org/current/release-notes.html |
| AGP | 9.2.0（`:app`） | 9.2.0 | developer.android.com/build/releases/about-agp |
| `:app` Kotlin 编译器 | AGP 内置 2.3.10（AGP 9 移除 kotlin-android 插件，`:app` 走内置编译器；JVM 模块仍 2.2.21——统一升是独立 slice） | 随 AGP 滚动 | developer.android.com/build/releases/about-agp |
| Compose 编译器插件 | `kotlin("plugin.compose")` 2.3.10（须 == `:app` Kotlin 编译器版本） | 随 `:app` Kotlin 滚动 | kotlinlang.org/docs/compose-compiler.html |
| Compose BOM | 2026.05.00（`:app`） | 随 BOM 滚动复核 | developer.android.com/develop/ui/compose/bom |
| activity-compose | 1.13.0（`:app`） | 随 BOM 复核 | developer.android.com/jetpack/androidx/releases/activity |
| compileSdk / targetSdk | 36（`:app`；本机仅装 `android-36` + build-tools 36.0.0） | 37（升前须先 `sdkmanager "platforms;android-37"`） | developer.android.com/tools/releases/platforms |
| minSdk | 26（`:app`） | — | developer.android.com/tools/releases/platforms |
| Google Play targetSdk 底线 | — | ≥35（随平台滚动，发版前复核；36 已满足） | developer.android.com/google/play/requirements/target-sdk |
| JDK | 17 | 17（AGP 9.x 默认即 min，**不必升 21**） | developer.android.com/build/jdks |
| kotlinx-serialization-json | 1.7.3（`:native-content` + `:game-core`） | 随 Kotlin 升级复核兼容 | github.com/Kotlin/kotlinx.serialization/releases |

## Core Verification

From `android/`:

```powershell
cd android
.\gradlew.bat --no-daemon :game-core:runSelfTest :native-content:runSelfTest :save-io:runSelfTest
.\gradlew.bat --no-daemon :game-core:test :native-content:test :save-io:test
```

Expected output includes:

```text
OK deterministic game-core self-test passed
OK native content validator self-test passed
OK save-io atomic write/read self-test passed
```

## Kotlin Quality Gate

Run type-resolving detekt tasks:

```powershell
cd android
.\gradlew.bat --no-daemon :game-core:detektMain :native-content:detektMain :save-io:detektMain
.\gradlew.bat --no-daemon :game-core:detektTest :native-content:detektTest :save-io:detektTest
```

The pure-JVM modules use `detektMain`/`detektTest`. The `:app` Android module uses the per-variant type-resolving tasks (`detektGrayDebug` / `detektGrayDebugUnitTest`):

```powershell
cd android
.\gradlew.bat --no-daemon :app:detektGrayDebug :app:detektGrayDebugUnitTest
```

## Full Current Local Gate

```powershell
cd android
.\gradlew.bat --no-daemon :game-core:test :native-content:test :save-io:test :game-core:runSelfTest :native-content:runSelfTest :save-io:runSelfTest :game-core:detektMain :native-content:detektMain :save-io:detektMain :game-core:detektTest :native-content:detektTest :save-io:detektTest assertTestCountEqualsBaseline :app:detektGrayDebug :app:detektGrayDebugUnitTest :app:testGrayDebugUnitTest :app:lintGrayDebug :app:assertAndroidTestCountEqualsBaseline :app:assembleGrayRelease
```

`:app:testGrayDebugUnitTest` actually runs the `:app` JVM unit tests (e.g. `BattleReducerTest`);
`:app:assertAndroidTestCountEqualsBaseline` only counts `@Test` methods, so both are needed.

## Android SDK Tools

If `adb` or `emulator` are not on PATH, use:

```powershell
& 'C:\Users\Xy172\AppData\Local\Android\Sdk\platform-tools\adb.exe' version
& 'C:\Users\Xy172\AppData\Local\Android\Sdk\emulator\emulator.exe' -list-avds
```

Do not require WSL, Docker, Linux shell, or PowerShell 7.
