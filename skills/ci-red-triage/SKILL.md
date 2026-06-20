# CI Red Triage

Use this workflow when CI exists and fails.

1. Identify the first failing gate.
2. Reproduce locally with the same command.
3. Classify as compile, test, lint, packaging, or environment.
4. Fix the narrowest cause.
5. Record durable commands or gotchas in `docs/runbook/CI.md`.
6. Do not put CI failure history into `HANDOFF.md` unless it is currently blocking work.

