# Project Structure

Target structure:

```text
ccz_tactics_engine/
  AGENTS.md
  HANDOFF.md
  android/
    settings.gradle.kts
    build.gradle.kts
    gradlew
    gradlew.bat
    gradle/
      wrapper/
    config/
      detekt/
    app/
    game-core/
    native-content/
  tools/
    converter/
    validators/
  docs/
    rules/
      ENGINEERING_RULES.md
    architecture/
      ARCHITECTURE.md
      PROJECT_STRUCTURE.md
      API.md
      SECURITY.md
      VERSION.md
      NATIVE_CONTENT_PACK.md
    DECISIONS/
    runbook/
    roadmap/
    audits/
  skills/
  archive/
```

Current Android modules:

```text
android/
  game-core/
    src/main/kotlin/com/ccz/core/
      battle/
      event/
      model/
      rng/
      selftest/
  native-content/
    src/main/kotlin/com/ccz/contentpack/
```

Existing Godot project material currently lives at repository root:

```text
assets/
data/
docs/tutorials/
project.godot
```

Treat the Godot material as legacy/reference unless a later ADR changes that.
