# Native Content Pack

## Purpose

The native content pack is the converter output and runtime input. It is the contract between old MOD material and the modern engine.

## Draft Layout

```text
ccz-native-pack/
  manifest.json
  classes.json
  units.json
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
  "entry": "events/r_001.json"
}
```

## Validation

- Required files exist.
- Required fields exist.
- IDs are unique.
- References resolve.
- Events use known ops and trigger conditions.
- R-script branch/choice jumps target a label defined in the same script (labels unique); portraits name a known unit.
- Maps match declared size.
- Assets referenced by content exist.

