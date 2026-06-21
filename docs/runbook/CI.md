# CI Runbook

CI runs on GitHub Actions: `.github/workflows/ci.yml`.

## JVM Gate (current)

Triggers: push to `main` / `feat|fix|perf|refactor|chore|ci|docs/**` branches, and every `pull_request`.

Runner: `ubuntu-latest` + JDK 17. Current modules are pure JVM/Kotlin (`game-core` / `native-content` / `save-io`), so no Android SDK is needed. Runs from `android/`:

```bash
./gradlew --no-daemon \
  :game-core:test :native-content:test :save-io:test \
  :game-core:runSelfTest :native-content:runSelfTest :save-io:runSelfTest \
  :game-core:detektMain :native-content:detektMain :save-io:detektMain \
  :game-core:detektTest :native-content:detektTest :save-io:detektTest \
  assertTestCountEqualsBaseline
```

This is the same gate as `docs/runbook/LOCAL_DEV.md` (Full Current Local Gate). Required properties:

- type-resolving detekt (`detektMain`/`detektTest`), not plain `detekt`.
- fail on detekt findings; baseline is frozen debt, not a license for new code.
- `@Test` count must equal `android/config/test-count-baseline.txt`.
- detekt reports uploaded as build artifacts.

## Future Android App Lane

After `android/app` exists, add a lane (will likely need the Android SDK; emulator / instrumented tests may need an SDK-provisioned or Windows runner):

```bash
./gradlew --no-daemon :app:detektGrayDebug :app:detektGrayDebugUnitTest
./gradlew --no-daemon :app:lintGrayDebug
./gradlew --no-daemon :app:assertAndroidTestCountEqualsBaseline
./gradlew --no-daemon :app:assembleGrayRelease
```

Future required gates: Room schema drift gate, R8 release build, apksigner fingerprint pin, emulator smoke test.

When CI fails, triage with the `ci-red-triage` skill; put durable commands or new gotchas here, not in `HANDOFF.md`.
