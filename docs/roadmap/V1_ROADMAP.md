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

- R dialogue flow. (pending)
- S pre/mid/post triggers. (pending — P3b `TriggerRunner`)
- Win/lose conditions. **[engine done]** `WinLose.evaluate/settle` in `game-core` decides
  `BattleOutcome` from the S-script win/lose lists and emits `Event.BattleEnded`.

## P4 Converter Sample

- Use one real MOD sample.
- Fill opcode-to-event mapping from evidence.
- Fail closed on unknown opcode.

## P5 Android Playable Slice

- Touch controls.
- Basic HUD.
- Save/replay.
- Internal test build.

