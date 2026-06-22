# V1 Roadmap

## P0 Documentation And Core Seed

- Establish knowledge-layer documentation.
- Land Kotlin deterministic core in the Android/Kotlin module.
- Run deterministic self-test.

## P1 Native Content Skeleton

- Define manifest/classes/units/terrain/skills/items/maps/events models. **[done]**
- Add validator stubs. **[done]** `ContentValidator` + `ContentEventValidator` (cross-ref + bounds + version).
- Load a hand-written sample pack. **[done — tables]** `ContentJsonLoader` (snake_case JSON → domain via
  `@Serializable` DTOs + fail-closed enum/required-field/unknown-key decoding). Event scripts (R/S) in
  JSON are not decoded yet (polymorphic op hierarchy deferred).

## P2 Minimal Battle Loop

- Display map and units. **[done]** `:app` Compose `BattleBoard` / `BattleScreen` render the grid,
  terrain, units, reachable-tile and target highlights, and an event log (#31/#32).
- Move, attack, end turn. **[engine + presentation done]** `BattleMap` + `Gameplay.submit` /
  `CommandValidator` in `game-core` validate move range / path / range / aliveness / turn ownership and
  gate the `Resolver`; `:app` `BattleReducer` routes taps to `Move` / `Attack` / `EndTurn` through
  `Gameplay` (read-only `legalDestinations` / `legalTargets` / `legalSkills` for highlighting), holding
  no combat authority (#32/#33/#37).
- Emit events and drive presentation from events. **[done]** `effectsOf` projects the authority's
  `Event` stream into floating Damaged / Missed / Defeated badges; the view layer never recomputes them (#35).

## P3 Event System

- R dialogue flow. **[engine done + presentation done]** `ScenarioRunner` deterministically runs an
  `RScript` (control flow → var/branch/goto/choice; presentation ops → `Playback.events`); `:app`
  `scenario.ScenarioReducer` / `ScenarioScreen` render that playback as a Compose cutscene (dialogue /
  portrait / scene / bgm / fade + choice branches), with `choices` as the replayable input axis and zero
  scenario authority in the view layer. `MainActivity` runs the intro cutscene then hands off to battle.
- S pre/mid/post triggers. **[engine done]** `TriggerRunner` (pre/tick/post) fires mid-triggers
  (`TriggerConditions`, `once` tracked) and runs `BattleOps` (all 9 battle ops); pure, no RNG.
- Win/lose conditions. **[engine done + presentation done]** `WinLose.evaluate/settle` decides
  `BattleOutcome` from the S-script win/lose lists and emits `Event.BattleEnded`; `tick` settles after
  firing triggers. `:app` surfaces the verdict via the read-only `Gameplay.outcome(state, script)` query
  (polling `evaluate`), shows a victory/defeat banner, and freezes input once decided — so a player
  command can now reach VICTORY. The demo's DEFEAT path (`ProtectAlive` the protagonist) is wired but
  dormant until enemy AI exists to threaten it.

## P4 Converter Sample

- Use one real MOD sample.
- Fill opcode-to-event mapping from evidence.
- Fail closed on unknown opcode.

## P5 Android Playable Slice

- Touch controls.
- Basic HUD.
- Save/replay. **[engine core done]** `SaveEnvelope` + `SaveLoader` in `game-core`: version axes,
  fail-closed reject of future save schema / rules drift, deterministic replay fold. On-disk
  serialization codec + atomic write still pending.
- Internal test build.

