# CI Runbook

CI is not configured yet. This file defines the first lane once CI is added.

## Current JVM/Kotlin Lane

```powershell
cd android
.\gradlew.bat --no-daemon :game-core:test :native-content:test :game-core:runSelfTest :native-content:runSelfTest :game-core:detektMain :native-content:detektMain :game-core:detektTest :native-content:detektTest
```

Required properties:

- Use project Gradle Wrapper.
- Run unit tests for rules and validator behavior.
- Fail on detekt findings.
- Do not use detekt baseline for new code.
- Store reports as CI artifacts when possible:
  - `android/game-core/build/reports/detekt/`
  - `android/native-content/build/reports/detekt/`

## Future Android App Lane

After `android/app` exists:

```powershell
cd android
.\gradlew.bat --no-daemon :app:detektGrayDebug :app:detektGrayDebugUnitTest
.\gradlew.bat --no-daemon :app:lintGrayDebug
.\gradlew.bat --no-daemon :app:assertAndroidTestCountEqualsBaseline
.\gradlew.bat --no-daemon :app:assembleGrayRelease
```

Future required gates:

```text
Room schema drift gate
R8 release build
apksigner fingerprint pin
emulator smoke test
```

When CI fails, put durable commands or new gotchas here. Put only current blocking status in `HANDOFF.md`.
