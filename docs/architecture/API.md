# API And Command Contracts

This project does not currently have a network API.

The first stable runtime API is the internal command/event contract between gameplay and core.

## Commands

Initial command set:

```text
Move(unit, to)
Attack(attacker, target, skill)
Wait(unit)
EndTurn(faction)
```

`Wait` stands a unit down for the turn (the Fire-Emblem move-then-no-attack path). It is part of the
action economy: each unit may Move once then take one action — Attack or Wait — per turn (tracked by
`BattleProgress.moved`/`acted`, cleared on `EndTurn`); the validator rejects a second move or action.

Future command set:

```text
CastStrategy
UseItem
TriggerEvent
ForceWin
ForceLose
```

## Events

Initial event set:

```text
Moved
Missed
Damaged
Died
Waited
TurnEnded
```

Future event set:

```text
Dialogue
PortraitChanged
UnitSpawned
UnitRemoved
StatusApplied
ItemGranted
BattleEnded
```

Most of these are already emitted by the trigger runner (P3): `UnitSpawned`, `UnitRemoved`,
`StatusApplied`, `ItemGranted`, plus `HpSet`, `VarSet`, and `Scenario(op)` (presentation ops
forwarded for the view layer). `BattleEnded(outcome)` is emitted by `WinLose.settle` once on
the `ONGOING -> VICTORY/DEFEAT` edge; `BattleOutcome` is sticky on `BattleState`.

There is a second, read-only way to learn the verdict: `Gameplay.outcome(state, sScript)` delegates to
`WinLose.evaluate` and returns the outcome value WITHOUT settling — it neither persists the outcome onto
state nor emits `BattleEnded`. It exists so a presentation layer can poll the verdict after each accepted
command without threading an `SScript` through `submit`/replay. Because it does not persist, the
state-level "sticky" short-circuit does not engage on this path (the value is re-derived each call, stable
while win/lose conditions are monotonic); a caller that needs a one-way latch holds the decided verdict
itself (the `:app` reducer freezes input once decided). `settle` remains the canonical event-emitting,
state-persisting channel.

`TriggerRunner.tick(state, sScript, scriptContext)` fires eligible mid-triggers (conditions in
`TriggerConditions`, `once` tracked) then settles win/lose; `applyPre`/`applyPost` run the
battle's pre/post op lists. All of this is pure and deterministic (no RNG).

`ScriptContext.reserves` (the off-map spawn templates a `SpawnUnit` op draws from) is assembled by
native-content's `BattleAssembler` from validated `UnitDef`s — full-HP `Combatant` templates keyed
by unit id, with a sentinel position the spawn op overwrites when it places the unit on the board.

`CampaignAssembler.assemble(content, battleScriptId, mapId, seed)` (native-content) turns a validated
`NativeContent` pack into a `BattleSetup(context, initialState, script)` — the content-driven path that
replaces hardcoded battle seeds. It maps tables onto engine inputs (terrain+`MapDef` → `BattleMap` with
per-tile `passable`, `ClassDef` → `UnitClass`, `SkillDef` → `Skill`, unit loadouts → the loadout table)
and **deploys** the opening roster by running the script's `pre` SpawnUnit ops through `TriggerRunner.applyPre`
over the `BattleAssembler` reserves, with the `BattleMap` threaded into the `ScriptContext` so deployment
placement honors occupancy *and* bounds/passability. Fail-closed: a missing script/map id, an unknown
terrain id, or a surfaced placement rejection throws `CampaignAssemblyException` rather than yielding a
half-built battle.

`ScenarioRunner.run(rScript, vars, choices)` interprets an R-script (cutscene) deterministically:
control-flow ops (`Label`/`SetVar`/`Branch`) are consumed to evolve variables and the program counter;
presentation ops (Dialogue/Portrait/Wait/SceneTransition/PlayBgm/Fade) are emitted in order into
`Playback.events`. A `Choice` consumes the next index from `choices` — the replayable player-input axis —
applying the option's `setVars` and jumping to its `goto`; when `choices` runs out (or names an
out-of-range option) the run stops at that choice (`Playback.pausedAt`). A step budget halts branch loops
fail-safe (`Playback.haltedOnBudget`). Pure, no RNG; `(rScript, vars, choices)` fully determines the
output; an unset variable reads 0, matching `BattleProgress.vars`.

## Command Legality

Commands are submitted through the gameplay gate, not applied directly:

```text
Gameplay.submit(state, command, context) -> Outcome
  Accepted(resolution)   // legal: Resolver produced new state + events
  Rejected(reason)       // illegal: RejectReason, no state change, no RNG consumed
```

`context` is a `BattleContext` (map + classes + skills + rules). `CommandValidator.check`
is a pure, deterministic function returning the first `RejectReason` or `null`. Rejection
never reaches the `Resolver`, so an illegal command cannot perturb replay determinism.
Replay re-applies the recorded (already-accepted) command sequence through `Resolver`
directly and does not re-validate.

Reject reasons (initial set): `NOT_ACTIVE_FACTION`, `UNIT_NOT_FOUND`, `UNIT_DEAD`,
`UNKNOWN_CLASS`, `DESTINATION_OUT_OF_BOUNDS`, `DESTINATION_IMPASSABLE`,
`DESTINATION_OCCUPIED`, `OUT_OF_MOVE_RANGE`, `UNKNOWN_SKILL`, `TARGET_NOT_FOUND`,
`TARGET_DEAD`, `SELF_TARGET`, `TARGET_FRIENDLY`, `OUT_OF_ATTACK_RANGE`,
`WRONG_END_TURN_FACTION`.

## Save / Replay

A save is a `SaveEnvelope` — the initial battle state (carrying `rngState`) plus two replayable input
axes: the accepted battle `commands` and the cutscene `scenarios` (R-script `scriptId` + player `choices`,
save schema v2+). The two axes replay through independent, decoupled entry points:

```text
SaveLoader.load(envelope, classes, skills, rules, scripts) -> Outcome   // battle axis + scenario ref pre-check
  Loaded(finalState)   // version-OK + commands resolve: folded through Resolver
  Rejected(reason)     // SaveRejection: FUTURE_SCHEMA_VERSION / RULES_VERSION_MISMATCH / CORRUPT_COMMAND / CORRUPT_SCENARIO

ScenarioReplayer.replay(scenarios, scripts) -> Outcome         // cutscene axis
  Replayed(playbacks)  // each scenario run to completion through ScenarioRunner
  Rejected(reason)     // ScenarioRejection: UNKNOWN_SCRIPT / INCOMPLETE_REPLAY
```

`SaveLoader.load` runs three gates: version (`check`), command integrity (`commandIntegrity`, replay-time
references resolve against the initial roster / skill table), then scenario script-reference integrity
(`scenarioIntegrity`, every recorded `scriptId` must resolve against the supplied content script table).
On-disk serialization is a separate concern:
`SaveCodec.encode`/`decode` maps `SaveEnvelope` <-> JSON through a `@Serializable` DTO layer (game-core
domain types carry no serialization annotations). Decoding is fail-closed — unknown keys / missing fields /
unknown command kind / unknown faction·outcome raise `SaveDecodeException`; version-axis gating stays in
`SaveLoader.check`. Atomic on-disk write (temp file + rename) is an IO concern for `:save-io`, not game-core.

`:app` wires both axes through `CampaignReplayDriver`: it supplies the `BattleContext` class/skill/rules
tables assembled from bundled content to `SaveLoader`, supplies the same content R-script table to both
`SaveLoader` and `ScenarioReplayer`, and returns no partial replay if either axis rejects.

## Contract Rule

Presentation consumes events. It must not infer hidden state by reaching around the core.

