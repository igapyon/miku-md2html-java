# Upstream Snapshot

- Repository: <https://github.com/igapyon/miku-md2html>
- Compatibility source: `v0.4.0`
- Commit: `b18a6e390425bd0e15387a2d608980a42a1809e9`
- Runtime bundle: `bundle/miku-md2html-runtime.mjs`
- Runtime bundle SHA-256: `4c5aaeac024b5c31c003e2df40d66d8e8d2e5ad4a4fbe4c2222619c83290f16c`
- Java product artifact version: `0.5.0`
- Local source/oracle checkout: `../miku-md2html`
- Reference method: clean local checkout; upstream source is not vendored here.

The Node v0.4.0 commit is the fixed compatibility target for this migration.
The Java artifact version is `0.5.0`; this does not claim full Node parity.
Four of the five currently tracked Node oracle cases match the packaged Java
runtime. See [remaining migration items](remaining-migration-items.md) and the
[follow-up execution plan](upstream-v0.4.0-followup-plan.md) for the current
implementation and pending gates.
