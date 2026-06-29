# Native Content Pack

## Purpose

The native content pack is the converter output and runtime input. It is the contract between old MOD material and the modern engine.

## Draft Layout

```text
ccz-native-pack/
  manifest.json
  classes.json
  units.json
  portrait_subjects.json
  terrain.json
  skills.json
  items.json
  commerce/
    products.json
    rewards.json
    stages.json
  maps/
  events/
  text/
  sprites/
  audio/
```

## Manifest

```json
{
  "native_format_version": "1",
  "content_id": "sample_mod",
  "content_version": "0.1.0",
  "source": {
    "mod": "unknown",
    "engine": "6.6"
  },
  "entry": "s_001"
}
```

## Commerce

Commerce is part of the native content contract, not a legacy runtime compatibility layer. A payment SDK or
store backend is outside this pack; after a caller has verified a purchase, native commerce resolves the product
id to deterministic content rewards.

```json
{
  "commerce": {
    "products": [
      {
        "id": "trssgshz03",
        "name": "Yuanbao x188",
        "price": { "amount_fen": 1200, "currency": "CNY" },
        "reward_id": "reward_trssgshz03"
      }
    ],
    "rewards": [
      {
        "id": "reward_trssgshz03",
        "item_grants": [{ "item_id": "legacy_good_110", "quantity": 188 }],
        "entitlements": []
      }
    ],
    "stages": [
      {
        "id": "legacy_stage_1",
        "name": "Daxingshan",
        "entry": "battle_1",
        "required_items": ["legacy_good_88"]
      }
    ]
  }
}
```

Current entitlement kinds:

- `ALL_STAGES`: unlocks any stage regardless of `required_items`.

The full legacy catalog currently ships as `android/app/src/main/resources/content/trssgshz_catalog/catalog.json`.
Regenerate it from a local decrypted resource tree with:

```powershell
.\gradlew.bat --no-daemon :mod-import:generateLegacyCatalog `
  -PextractedDir=E:\trssgshz_reverse_repo\decrypted_full_apk\assets\GameResources\trssgshz `
  -PoutPath=D:\ccz_tactics_engine\android\app\src\main\resources\content\trssgshz_catalog\catalog.json
```

Commerce unlock does not by itself mean a stage is app-playable. The Android app gates launch through
`PlayableStageCatalog`: a stage must be both unlocked by `CommerceResolver` and registered to a native battle
runtime. Currently `legacy_stage_1` is registered to `CampaignRuntime`, which combines a generated native stage
pack with the authored Daxingshan intro, and `legacy_stage_2` is registered to
`PromotedStageRuntimes.QuyangSiege` from a generated native pack. Other catalog rows may resolve commerce access
but remain non-launchable until their native battle packs are promoted and registered. This keeps old-table catalog
migration separate from playable runtime exposure.

Generate a single promoted stage pack from a local decrypted resource tree with:

```powershell
.\gradlew.bat --no-daemon :mod-import:generateLegacyStage `
  -PextractedDir=E:\trssgshz_reverse_repo\decrypted_full_apk\assets\GameResources\trssgshz `
  -PoutPath=D:\ccz_tactics_engine\android\app\src\main\resources\content\legacy_stage_2\campaign.json `
  -PstageId=2
```

`generateLegacyStage` validates and assembles the pack before writing it. For proposal-ready same-tile deployment
collisions, it preserves script-referenced units as native `events.deferred_deployments[]` metadata instead of
dropping them or using a stage-specific generator path.

## Legacy Maps And Stage Planning

Legacy `terrainMap_*.json` files are converter input, not runtime input. Some maps are not strict rectangular
JSON grids: `map_value` can contain `null` cells, `null` rows, short rows, or extra rows/columns outside the
declared `map_width` / `map_height`. The converter normalizes them into native rectangular maps:

- `null` cell, missing cell, or missing row -> `terrain_void`
- extra rows/columns -> cropped to the declared size
- negative terrain ids -> rejected fail-closed

When a battle pack references `terrain_void`, the builder emits a terrain entry with `passable=false`, so old map
holes remain unwalkable in `game-core` instead of becoming fake plains.

Stage migration is tracked by a generated recon report, not by pretending every old stage is already playable:

```powershell
.\gradlew.bat --no-daemon :mod-import:planLegacyStages `
  -PextractedDir=E:\trssgshz_reverse_repo\decrypted_full_apk\assets\GameResources\trssgshz `
  -PoutPath=D:\ccz_tactics_engine\docs\recon\legacy-stage-migration-report.json
```

Current real-resource report uses opcode profile `trssgshz_current_apk` (current E-drive package remaps legacy
script opcodes while keeping the deployment payload layout stable): 397 stage rows, 262 ready, 135 blocked,
0 map errors, 0 unknown deployment hids, 10 missing scripts, 69 empty deployments, and 56 stages with deployment
collisions. This means catalog, map normalization, stage binding, current opcode profile selection, and most
opening enemy/friend deployments are now recoverable. Remaining blocked stages need explicit handling for
legacy same-tile/hidden deployment semantics or missing opening deployment, not looser byte scanning.

For stages blocked by deployment collisions, the report emits `diagnostics.collision_groups[]` with each
colliding cell's side, hid, level, source slot, record offset, and raw 16-bit slot words. Treat those fields as
reverse-engineering evidence for hidden/replacement deployment rules; they are not runtime content.
When a colliding hid is referenced by a decoded actor-state record, the same group also emits `script_refs`
entries such as `set_actor_visible` or `army_change` with record offsets and raw numeric values. In the current
E-drive report, the deployment totals summarize 163 collision groups / 881 grouped units; 125 groups have these
script references (285 rows total). Each referenced group also carries `script_ref_coverage` with a bucket and,
for single-candidate groups, the unreferenced side/hid/slot. Coverage totals are mutually exclusive: 38 groups
without script refs, 55 groups where every grouped unit is referenced, 68 groups with exactly one unreferenced
unit, and 2 mixed-ref groups with multiple unreferenced units; the broader actionable set with any unreferenced
unit is 70 groups. The planner now also emits `resolution_proposal` for those 68 single-candidate groups:
`opening_unit_with_deferred_actor_state_refs` means "keep the single unreferenced unit as the opening occupant,
defer the script-referenced units" as an offline migration proposal. Current totals are 68 proposed opening
units / 93 deferred units across 16 stages; 13 collision stages contain only proposed collision groups. This is
exposed as `collision_resolution_preview`: those 13 stages would become ready if the proposed hidden/deferred
deployment contract is applied, while their original report status remains blocked. This is evidence for future
hidden/replacement handling, not an automatic runtime resolver yet.
After the native deferred-deployment metadata contract landed, `planLegacyStages` now runs a stricter
`trial_assembly` for exactly those preview-ready stages: it applies the proposal, emits top-level
`events.deferred_deployments`, decodes through `ContentJsonLoader`, validates through `ContentValidator`, and
assembles through `CampaignAssembler`. In the current E-drive report, all 13 trial rows pass
(`trial_assembly.status=ready`) with 57 deferred units total. Their original `diagnostics.status` remains `blocked`
until the converter
chooses to apply the proposal for production output; the trial field is machine evidence, not a silent status
rewrite.

## Deferred Deployments

Native packs can preserve legacy hidden/replacement deployment evidence with top-level event metadata:

```json
{
  "events": {
    "s_scripts": [
      {
        "id": "battle_1",
        "win": [{ "type": "annihilate_enemies" }],
        "lose": [],
        "pre": [{ "type": "spawn_unit", "unit": "hero_226", "at": { "x": 4, "y": 4 } }]
      }
    ],
    "deferred_deployments": [
      {
        "script": "battle_1",
        "unit": "hero_603",
        "at": { "x": 4, "y": 4 },
        "faction": "ENEMY",
        "source": "legacy_actor_state_refs"
      }
    ]
  }
}
```

`events.deferred_deployments` is metadata, not an implicit core op. The unit starts as an off-map reserve; a
future script translation must still emit an explicit `SpawnUnit` to put it on the board. This keeps
`game-core` as the battle authority and keeps Android/UI out of deployment decisions.

Validation is fail-closed: the referenced S-script must exist, the unit id must resolve, the coordinate must be
non-negative, `source` must be non-blank, and the same unit cannot also appear in that script's opening `pre`
spawns. Campaign assembly also checks the deferred coordinate against the selected map bounds and returns the
selected script's matching deferred metadata in `BattleSetup.deferredDeployments`.

The legacy importer writes this metadata at the top-level `events.deferred_deployments` table when a stage
planner proposal chooses one opening occupant and defers actor-state-referenced colliding units. The normal
opening deployment remains in `s_scripts[].pre`; deferred units are still included in reserves so later translated
script ops can spawn them deterministically. `planLegacyStages` uses the same contract for `trial_assembly`, so
RE proposals are checked against the real native runtime path before they are accepted for a production converter.

## Validation

- Required files exist.
- Required fields exist.
- IDs are unique.
- References resolve.
- `manifest.entry` resolves to a known event script id (`events.s_scripts` or `events.r_scripts`).
- Events use known ops and trigger conditions.
- R-script branch/choice jumps target a label defined in the same script (labels unique); portraits name a known unit or `events.portrait_subjects` entry.
- `events.deferred_deployments` references known S-scripts and units, has a non-blank source, uses non-negative coordinates, and does not duplicate an opening `pre` spawn.
- Native maps match declared size after legacy-map normalization.
- Campaign assembly checks every coordinate in the selected S-script against the selected `MapDef` bounds before battle setup is returned.
- Campaign assembly also checks selected `events.deferred_deployments` coordinates against the selected `MapDef` bounds.
- Campaign assembly rejects selected S-script `pre` MoveUnit/RemoveUnit ops that target a unit not currently deployed by earlier `pre` ops.
- Commerce product ids, reward ids, and stage ids are unique.
- Commerce products reference known rewards.
- Commerce rewards grant at least one item or entitlement; item grants reference known items and have positive quantity.
- Commerce stage `required_items` reference known items; non-empty stage `entry` references a known event script.
- Assets referenced by content exist.

