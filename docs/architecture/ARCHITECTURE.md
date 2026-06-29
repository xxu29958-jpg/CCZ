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
  manifest / units / classes / terrain / skills / items / commerce / stages / maps / events / text / assets
```

## App Entry Flow

The Android app starts from a playable-stage registry:

```text
LegacyCatalogContent (native commerce catalog)
  -> CommerceResolver purchase/access result
  -> PlayableStageCatalog registration check
  -> optional R-script scenario
  -> BattleReducer / BattleScreen
  -> game-core
```

Catalog access and playability are deliberately separate. A legacy catalog row can be unlocked by products or
entitlements, but it can launch only after the app registers a native battle runtime for that stage. The
registered production runtimes are currently:

- `legacy_stage_1` -> `CampaignRuntime` (generated native stage pack plus authored Daxingshan intro)
- `legacy_stage_2` -> `PromotedStageRuntimes.QuyangSiege`
- `legacy_stage_3` -> `PromotedStageRuntimes.ShimenAttack`
- `legacy_stage_4` -> `PromotedStageRuntimes.SishuiPassOne`
- `legacy_stage_5` -> `PromotedStageRuntimes.SishuiPassTwo`
- `legacy_stage_6` -> `PromotedStageRuntimes.HulaoPassBattle`

Promoted stages after `legacy_stage_1` use generated native packs and have no authored intros yet.

Command validation is realized today inside `game-core` (the `Gameplay.submit` facade over
`CommandValidator`), since legality is a deterministic rule and `game-core` is the sole combat
authority. A standalone `:gameplay` module (battle loop / AI / trigger runner) is deferred until
P2 presentation / P3 events need it. See `docs/rules/CCZ_ENGINE_RULES.md` §Game Core.

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

- R/S scripts, opcode mapping, and package-specific opcode profiles.
- Data tables.
- Imsg text.
- legacy maps (including null/void normalization and stage migration planning), sprites, portraits, effects, audio.
- existing Windows tools and exported intermediate artifacts.

The runtime must know only:

- native schema version.
- validated content records.
- deterministic commands and events.
- save/replay schema version.

## Current Engine Choice

Main line: Android native shell + Kotlin deterministic core + custom 2D tactics presentation.

The repository was forked from a Godot template, since removed (working tree and history). Unity and Unreal are not the main line.
