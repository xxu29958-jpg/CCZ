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
- `legacy_stage_8` through `legacy_stage_14` -> generated `PromotedStageRuntimes` entries
- `legacy_stage_16` through `legacy_stage_19`, `legacy_stage_21`, `legacy_stage_23` through `legacy_stage_24`,
  and `legacy_stage_29` through `legacy_stage_30` -> generated `PromotedStageRuntimes` entries
- `legacy_stage_31`, `legacy_stage_34` through `legacy_stage_42` -> generated `PromotedStageRuntimes` entries
- `legacy_stage_44` through `legacy_stage_48`, `legacy_stage_50` through `legacy_stage_59`, and
  `legacy_stage_61` through `legacy_stage_62` -> generated `PromotedStageRuntimes` entries
- `legacy_stage_64`, `legacy_stage_66`, `legacy_stage_69` through `legacy_stage_70`,
  `legacy_stage_72` through `legacy_stage_73`, `legacy_stage_76` through `legacy_stage_78`,
  `legacy_stage_83`, `legacy_stage_86`, and `legacy_stage_89` -> generated `PromotedStageRuntimes` entries
- `legacy_stage_91`, `legacy_stage_93` through `legacy_stage_97`, `legacy_stage_99`,
  `legacy_stage_101` through `legacy_stage_105`, `legacy_stage_107`, `legacy_stage_109`,
  `legacy_stage_111` through `legacy_stage_112`, `legacy_stage_114` through `legacy_stage_116`, and
  `legacy_stage_119` through `legacy_stage_120` -> generated `PromotedStageRuntimes` entries
- `legacy_stage_122`, `legacy_stage_127` through `legacy_stage_128`,
  `legacy_stage_130` through `legacy_stage_131`, `legacy_stage_134` through `legacy_stage_137`,
  `legacy_stage_141`, `legacy_stage_143` through `legacy_stage_148`, `legacy_stage_152`, and
  `legacy_stage_156` through `legacy_stage_158` -> generated `PromotedStageRuntimes` entries
- `legacy_stage_165`, `legacy_stage_172`, `legacy_stage_174` through `legacy_stage_176`,
  `legacy_stage_178` through `legacy_stage_180`, `legacy_stage_182`, `legacy_stage_186`,
  `legacy_stage_189`, `legacy_stage_194`, `legacy_stage_196` through `legacy_stage_197`,
  `legacy_stage_201` through `legacy_stage_202`, and `legacy_stage_204` through `legacy_stage_207`
  -> generated `PromotedStageRuntimes` entries
- `legacy_stage_209` through `legacy_stage_210`, `legacy_stage_214` through `legacy_stage_222`,
  `legacy_stage_224` through `legacy_stage_231`, and `legacy_stage_233`
  -> generated `PromotedStageRuntimes` entries
- `legacy_stage_234` through `legacy_stage_237`, `legacy_stage_239`,
  `legacy_stage_242` through `legacy_stage_245`, `legacy_stage_249` through `legacy_stage_252`,
  `legacy_stage_257` through `legacy_stage_259`, and `legacy_stage_263` through `legacy_stage_266`
  -> generated `PromotedStageRuntimes` entries
- `legacy_stage_268` through `legacy_stage_269`, `legacy_stage_272`, `legacy_stage_276` through
  `legacy_stage_277`, `legacy_stage_279` through `legacy_stage_281`, `legacy_stage_283`,
  `legacy_stage_285`, `legacy_stage_287` through `legacy_stage_288`, `legacy_stage_293`,
  `legacy_stage_295`, and `legacy_stage_297` through `legacy_stage_302`
  -> generated `PromotedStageRuntimes` entries

Promoted stages after `legacy_stage_1` use generated native packs and have no authored intros yet.
`legacy_stage_7` remains deliberately unregistered until its all-referenced same-tile deployment collisions have
proven opening/deferred semantics. `legacy_stage_80` is also unregistered until duplicate legacy actor ids can be
resolved without conflicting with the default player-party ids.

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
