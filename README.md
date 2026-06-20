# CCZ Tactics Engine

Modern Android-first tactics engine for converted Cao Cao Zhuan style content.

## Project Entry

Read in this order:

1. `AGENTS.md`
2. `HANDOFF.md`
3. `docs/rules/ENGINEERING_RULES.md`
4. `docs/architecture/ARCHITECTURE.md`
5. `docs/DECISIONS/`

## Current Direction

```text
old MOD files
  -> offline converter
  -> native content pack
  -> Android runtime
  -> game-core
```

The runtime does not parse old MOD formats. Legacy scripts, opcodes, Data tables, images, and audio are converter inputs.

## Repository Note

This repository still contains Godot tactical RPG template material at the root (`assets/`, `data/`, `project.godot`). Treat it as reference/prototype material unless a later decision says otherwise.

The Android/Kotlin workspace now contains the first two runtime modules:

```text
android/game-core      deterministic battle core
android/native-content    native content pack models and validator
```

Current local gate:

```powershell
cd android
.\gradlew.bat --no-daemon :game-core:test :native-content:test :game-core:runSelfTest :native-content:runSelfTest :game-core:detektMain :native-content:detektMain :game-core:detektTest :native-content:detektTest
```

## Documentation Map

```text
AGENTS.md                         entry and AI working rules
HANDOFF.md                        current state and next step
docs/rules/ENGINEERING_RULES.md   long-lived engineering rules
docs/architecture/                system truth and contracts
docs/DECISIONS/                   architecture decisions
docs/runbook/                     operating instructions
docs/roadmap/                     milestone plan
docs/audits/                      deferred risk pool
skills/                           repeatable workflows
archive/                          historical context
```
