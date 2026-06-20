# Architecture

## Positioning

CCZ is a modern Android tactics engine. Cao Cao Zhuan MODs are content sources, not runtime compatibility targets.

The runtime loads a converted native content pack. The offline converter handles legacy files and can be rewritten without changing the engine.

## Main Flow

```text
old MOD files
  -> offline converter
  -> native content pack
  -> Android runtime
  -> game-core
  -> presentation events
```

## Runtime Layers

```text
Android app
  lifecycle / input / render / audio / UI
  no combat authority

Gameplay
  command validation / battle loop / AI / trigger runner

game-core
  immutable state / resolver / formula / rules / RNG / replay primitives
  sole combat authority

Native content
  manifest / units / classes / terrain / skills / items / maps / events / text / assets
```

## Authority Boundary

`game-core` is the only authority for battle state transitions. Android UI renders state/events and collects input. It may request commands, but it must not compute damage, mutate battle state, consume RNG, or decide battle outcomes.

Replay must be derivable from:

```text
native content version
rules version
initial battle state
rng state
ordered command sequence
```

## Converter Boundary

The converter may know about:

- R/S scripts and opcode mapping.
- Data tables.
- Imsg text.
- legacy maps, sprites, portraits, effects, audio.
- existing Windows tools and exported intermediate artifacts.

The runtime must know only:

- native schema version.
- validated content records.
- deterministic commands and events.
- save/replay schema version.

## Current Engine Choice

Main line: Android native shell + Kotlin deterministic core + custom 2D tactics presentation.

The repository was forked from a Godot template, since removed (working tree and history). Unity and Unreal are not the main line.
