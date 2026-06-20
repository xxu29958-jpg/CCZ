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

