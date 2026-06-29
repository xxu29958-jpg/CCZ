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
  archive/               历史素材（当前空）
```

Current Android modules:

```text
android/
  app/
    src/main/kotlin/com/ccz/app/
      battle/
      campaign/
      catalog/
      scenario/
    src/main/resources/content/
  game-core/
    src/main/kotlin/com/ccz/core/
      battle/
      event/
      model/
      rng/
      selftest/
  native-content/
    src/main/kotlin/com/ccz/contentpack/
  save-io/
    src/main/kotlin/com/ccz/saveio/
  mod-import/
    src/main/kotlin/com/ccz/modimport/
```

The repository was forked from a Godot tactical RPG template; that material has been removed entirely from both the working tree and git history (`git filter-branch`). See `docs/DECISIONS/0002-runtime-engine-choice.md` and `docs/audits/2026-06-20-rules-overhaul-vs-xiaopiaojia.md`.
