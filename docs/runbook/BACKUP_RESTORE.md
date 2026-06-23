# Backup, Restore, And Rollback Runbook

The save system now exists in code (`:save-io` `SaveFileStore` for atomic on-disk write +
`com.ccz.core.save` `SaveEnvelope` / `SaveCodec` / `SaveLoader`); there is no deployed build or real
save data yet, so the procedures below are the **intended** ones to follow once builds ship — not
operations against live data today.

## What a save contains

A `SaveEnvelope` (round-tripped as JSON by `SaveCodec`) holds the full replay axes:

- `versions` — the version four-tuple + `save_schema_version` + `rules_version` (`SaveVersions`);
- `initialState` — the battle's turn-1-fresh `BattleState` (units, `rng_state`, …);
- `commands` — the accepted battle command stream (replayed through `Resolver`);
- `scenarios` — the cutscene replay axis (`scriptId` + player `choices`).

The on-disk file is written atomically (`SaveFileStore`: same-dir temp file + `ATOMIC_MOVE`), so a
reader/crash never sees a half-written save and no temp residue is left on failure.

## Restore

`SaveLoader.load(envelope, classes, skills, rules, scripts)` is fail-closed and runs three gates
before replaying:

1. **version** — reject a `save_schema_version` newer than the runtime supports (`FUTURE_SCHEMA_VERSION`)
   or a `rules_version` mismatch (`RULES_VERSION_MISMATCH`), since the deterministic fold would diverge;
2. **command integrity** — reject commands referencing units/skills absent from the initial roster/skill
   table (`CORRUPT_COMMAND`);
3. **scenario reference integrity** — reject scenario `scriptId` values absent from the supplied content
   script table (`CORRUPT_SCENARIO`).

After `SaveLoader` accepts the save axis, replay the cutscene axis with
`ScenarioReplayer.replay(envelope.scenarios, scripts)`: choices that no longer complete the current
content script reject as `INCOMPLETE_REPLAY` rather than leaking partial playback. In `:app`, this
two-axis restore wiring lives in `CampaignReplayDriver`, which passes the bundled content's battle
tables and R-script table to both game-core entry points.

## Rollback (intended procedure)

### Reverting a merged slice (code)

`main` is protected and squash-merged, so each slice is one commit.

1. Identify the slice's squash commit on `main` (`git log --oneline`).
2. Branch off latest `main`: `git checkout -b revert/<slice> main`.
3. `git revert <sha>` (a forward revert — never force-push `main`).
4. Run the Full Current Local Gate (`docs/runbook/LOCAL_DEV.md`) — a revert must pass the same gates,
   including the test-count baselines (a reverted slice restores the prior counts).
5. Open a PR, let CI go green, merge. Never rewrite `main` history.

### Rolling back a save-schema change — risk boundary

- A save's `save_schema_version` is the contract. **Lowering** `SUPPORTED_SAVE_SCHEMA_VERSION` (or
  reverting a schema-raising slice) makes the runtime reject any save written at the higher version
  (`FUTURE_SCHEMA_VERSION`) — that is the fail-closed design, not a bug, but it means **saves created
  after a schema bump are unreadable by a rolled-back build**. Communicate this before shipping a bump.
- Adding an optional defaulted field (the v1→v2 `scenarios` pattern) is forward-compatible: an old save
  decodes with the field empty, so that kind of change is safely revertible without orphaning saves.
- **Turn-scoped state is not in the save** (`BattleProgress.moved`/`acted`) — replay re-derives it, so
  a rollback never has to reconcile it. A future "save mid-battle" feature would change this; see
  `docs/KNOWN_ISSUES.md` (P2) and the guard note at `SaveMappers.stateDto`.

## Backup scope (when builds ship)

Persist together: the save file, the content pack version it references (`content_version`), and the
build's `engine_version` / `rules_version`. Restore must reject a save the running build cannot honor
(the version gate above already enforces this).
