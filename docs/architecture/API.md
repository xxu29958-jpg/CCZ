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

## Contract Rule

Presentation consumes events. It must not infer hidden state by reaching around the core.

