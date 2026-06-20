# Legacy Godot Template README

This repository originally contained a Godot tactical RPG template (upstream `ramaureirac/godot-tactical-rpg`). The current CCZ engine direction is Android/Kotlin-first (see `docs/DECISIONS/0002-runtime-engine-choice.md`). The Godot material is kept as reference/prototype content but is **quarantined under `archive/legacy-godot/`** so the repository top level expresses the actual architecture — it is not part of the runtime and is not on the main line.

Original upstream-style summary:

- Godot Engine 4 tactical RPG template.
- Turn-based grid movement, pawn movement and attack, basic enemy AI.
- Camera panning, free look, zoom and rotations; controller support.
- Blender map recognition tutorial.

Where the material now lives (moved from repo root via `git mv`, history preserved):

```text
archive/legacy-godot/project.godot
archive/legacy-godot/assets/
archive/legacy-godot/data/
archive/legacy-godot/docs-tutorials/        (how-to-create-maps tutorial, ~76MB)
archive/legacy-godot/docs-img/
archive/legacy-godot/docs-asset-lib-preview/
```

Original external references from the old README:

- Godot Engine: https://godotengine.org/
- Upstream: https://github.com/ramaureirac/godot-tactical-rpg
