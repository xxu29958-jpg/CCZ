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

## Core Verification

From the repository root:

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
.\gradlew.bat --no-daemon :game-core:test :native-content:test :game-core:runSelfTest :native-content:runSelfTest :game-core:detektMain :native-content:detektMain :game-core:detektTest :native-content:detektTest
```

## Android SDK Tools

If `adb` or `emulator` are not on PATH, use:

```powershell
& 'C:\Users\Xy172\AppData\Local\Android\Sdk\platform-tools\adb.exe' version
& 'C:\Users\Xy172\AppData\Local\Android\Sdk\emulator\emulator.exe' -list-avds
```

Do not require WSL, Docker, Linux shell, or PowerShell 7.
