# CI Runbook

CI runs on GitHub Actions: `.github/workflows/ci.yml`.

## JVM Gate (current)

Triggers: push to `main` / `feat|fix|perf|refactor|chore|ci|docs/**` branches, and every `pull_request`.

Runner: `ubuntu-latest` + JDK 17. The three modules it gates (`game-core` / `native-content` / `save-io`) are pure JVM/Kotlin and execute no Android tasks; the root build now includes `:app`, but configuring it only needs the SDK *location* that `ubuntu-latest` preinstalls (see the Android lane note below), so this lane installs no SDK components. Runs from `android/`:

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

## Android App Lane (current)

`android/app` now exists, so the second job `android-gate` runs on every push/PR alongside `jvm-gate`. It provisions the Android SDK (`android-actions/setup-android` + `sdkmanager "platforms;android-36" "build-tools;36.0.0"` — pinned to the locally-verified baseline, not runner-image drift), then:

```bash
./gradlew --no-daemon \
  :app:detektGrayDebug :app:detektGrayDebugUnitTest \
  :app:lintGrayDebug \
  :app:assertAndroidTestCountEqualsBaseline \
  :app:assembleGrayRelease
```

Properties:

- detekt is type-resolving (`detektGrayDebug` / `detektGrayDebugUnitTest`), not plain `detekt`.
- `:app` `@Test` count must equal `android/app/config/android-test-count-baseline.txt` (separate from the root `:game-core`/`:native-content`/`:save-io` baseline).
- `assembleGrayRelease` validates release compilation/packaging; the APK is **unsigned** (R8/minify + apksigner are future gates — see below).
- `:app` detekt + lint reports uploaded as build artifacts.

Note: `jvm-gate` only runs the pure-JVM modules' tasks, but the root project now includes `:app`, so Gradle's configuration phase evaluates `:app` (AGP plugin apply needs the SDK *location*). `ubuntu-latest` ships `ANDROID_SDK_ROOT`, which satisfies configuration; the `android-36` platform is only needed when `:app` tasks **execute** (the `android-gate` lane), so `jvm-gate` does not install it.

Still future (not yet gated — `:app` shell has no DB / signing / instrumented tests): Room schema drift gate, R8 release build, apksigner fingerprint pin, emulator smoke test (AVD `ticketbox_api36_host`).

When CI fails, triage with the `ci-red-triage` skill; put durable commands or new gotchas here, not in `HANDOFF.md`.
