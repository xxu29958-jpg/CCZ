# Android Workspace

This directory is the Android-first runtime workspace.

Current modules:

```text
game-core     pure Kotlin deterministic battle core
native-content   native content pack models and validation
```

Future modules:

```text
native-content JSON loader and pack validator wiring
app            Android shell, rendering, input, audio, UI
```

Run the core self-test with Gradle once a wrapper or local Gradle is available:

```powershell
cd android
.\gradlew.bat --no-daemon :game-core:test
.\gradlew.bat --no-daemon :game-core:runSelfTest
.\gradlew.bat --no-daemon :native-content:test
.\gradlew.bat --no-daemon :native-content:runSelfTest
```

On machines without Gradle or kotlinc, use the repository script from the repository root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run_game_core_selftest.ps1
```
