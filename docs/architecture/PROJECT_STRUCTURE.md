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
    legacy-godot/        隔离的上游 Godot 模板（非运行时，非主线）
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

Existing Godot project material has been quarantined under `archive/legacy-godot/` (moved from repo root via `git mv`, history preserved) so the top level expresses the actual Android/Kotlin architecture:

```text
archive/legacy-godot/
  project.godot
  assets/
  data/
  docs-tutorials/            how-to-create-maps tutorial (~76MB)
  docs-img/
  docs-asset-lib-preview/
```

Treat the Godot material as legacy/reference unless a later ADR changes that. See `archive/legacy-godot-template-readme.md`.
