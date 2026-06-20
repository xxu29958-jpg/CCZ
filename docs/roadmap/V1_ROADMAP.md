# V1 Roadmap

## P0 Documentation And Core Seed

- Establish knowledge-layer documentation.
- Land Kotlin deterministic core in the Android/Kotlin module.
- Run deterministic self-test.

## P1 Native Content Skeleton

- Define manifest/classes/units/terrain/skills/items/maps/events models.
- Add validator stubs.
- Load a hand-written sample pack.

## P2 Minimal Battle Loop

- Display map and units. (pending — needs `:app`)
- Move, attack, end turn. **[engine half done]** `BattleMap` + `Gameplay.submit` / `CommandValidator`
  in `game-core` validate move range / path / range / aliveness / turn ownership and gate the
  `Resolver`. Presentation / input still pending the app slice.
- Emit events and drive presentation from events. (events already emitted by `Resolver`; presentation pending)

## P3 Event System

- R dialogue flow. (pending — scenario ops are forwarded as `Event.Scenario` for a view layer)
- S pre/mid/post triggers. **[engine done]** `TriggerRunner` (pre/tick/post) fires mid-triggers
  (`TriggerConditions`, `once` tracked) and runs `BattleOps` (all 9 battle ops); pure, no RNG.
- Win/lose conditions. **[engine done]** `WinLose.evaluate/settle` decides `BattleOutcome` from
  the S-script win/lose lists and emits `Event.BattleEnded`; `tick` settles after firing triggers.

## P4 Converter Sample

- Use one real MOD sample.
- Fill opcode-to-event mapping from evidence.
- Fail closed on unknown opcode.

## P5 Android Playable Slice

- Touch controls.
- Basic HUD.
- Save/replay.
- Internal test build.

