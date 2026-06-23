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

## Validation

- Required files exist.
- Required fields exist.
- IDs are unique.
- References resolve.
- `manifest.entry` resolves to a known event script id (`events.s_scripts` or `events.r_scripts`).
- Events use known ops and trigger conditions.
- R-script branch/choice jumps target a label defined in the same script (labels unique); portraits name a known unit or `events.portrait_subjects` entry.
- Maps match declared size.
- Campaign assembly checks every coordinate in the selected S-script against the selected `MapDef` bounds before battle setup is returned.
- Campaign assembly rejects selected S-script `pre` MoveUnit/RemoveUnit ops that target a unit not currently deployed by earlier `pre` ops.
- Assets referenced by content exist.

