# Backup And Restore Runbook

No runtime save system exists yet.

Future backup scope:

- save files;
- replay command logs;
- content pack version used by the save;
- `rng_state`;
- save schema version.

Restore must reject saves newer than the runtime supports.

