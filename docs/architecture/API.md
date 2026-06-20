# API And Command Contracts

This project does not currently have a network API.

The first stable runtime API is the internal command/event contract between gameplay and core.

## Commands

Initial command set:

```text
Move(unit, to)
Attack(attacker, target, skill)
EndTurn(faction)
```

Future command set:

```text
CastStrategy
UseItem
Wait
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

`TriggerRunner.tick(state, sScript, scriptContext)` fires eligible mid-triggers (conditions in
`TriggerConditions`, `once` tracked) then settles win/lose; `applyPre`/`applyPost` run the
battle's pre/post op lists. All of this is pure and deterministic (no RNG).

`ScriptContext.reserves` (the off-map spawn templates a `SpawnUnit` op draws from) is assembled by
native-content's `BattleAssembler` from validated `UnitDef`s — full-HP `Combatant` templates keyed
by unit id, with a sentinel position the spawn op overwrites when it places the unit on the board.

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

## Contract Rule

Presentation consumes events. It must not infer hidden state by reaching around the core.

