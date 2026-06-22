# Branch Baseline

Lean note to resist stale-branch confusion (mirrors xiaopiaojia's `BRANCH_BASELINE.md`, trimmed).

- **`main` is the only baseline.** It is protected and **squash-merged**, so each shipped slice is
  exactly one commit on `main`. There are no long-lived integration branches.
- **Branch off fresh `main` for every slice.** Before starting: `git checkout main && git pull origin main`,
  then `git checkout -b <type>/<slice>`. Never grow a slice on a branch cut from an older `main` — it
  drags in or conflicts with already-merged work.
- **The merged squash SHA is the slice's baseline.** After merge, `git pull origin main`; the squash
  commit (`<subject> (#NN)`) is the canonical record. The pre-merge feature branch is deleted
  (`gh pr merge --delete-branch`) and must not be reused.
- **Never rewrite `main` history.** Roll back forward with `git revert` (see `BACKUP_RESTORE.md`); never
  force-push `main`.

The current mainline SHA is whatever `git rev-parse --short origin/main` reports — this file does not
pin it (that would itself go stale); `HANDOFF.md` names the top slice in prose.
