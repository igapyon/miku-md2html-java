# Upstream Snapshot

- Repository: <https://github.com/igapyon/miku-md2html>
- Compatibility source: `v0.4.0`
- Commit: `b18a6e390425bd0e15387a2d608980a42a1809e9`
- Runtime bundle: `bundle/miku-md2html-runtime.mjs`
- Runtime bundle SHA-256: `4c5aaeac024b5c31c003e2df40d66d8e8d2e5ad4a4fbe4c2222619c83290f16c`
- Java product artifact version: `0.6.0` (aligned to the upstream v0.6.0 version number)
- Local source/oracle checkout: `../miku-md2html`
- Reference method: clean local checkout; upstream source is not vendored here.

## Selective Update from Node v0.6.0

- Latest release reviewed: [`v0.6.0`](https://github.com/igapyon/miku-md2html/releases/tag/v0.6.0),
  source commit `4a0918713f8b94fffe8d447e809800a2e096cb84`.
- Adopted renderer behavior: place the generated table of contents after a
  leading direct H1 and limit repeated formatting blank lines between
  structural elements.
- The compatibility source remains Node v0.4.0 until the pending v0.5.0 image
  pipeline and acceptance work is complete. The selective v0.6.0 update is not
  a full parity claim.

The Node v0.4.0 commit remains the compatibility target for this migration.
The Java artifact was aligned to version `0.6.0` by a separate explicit
decision; this does not claim full Node v0.6.0 parity. The last v0.4.0
comparison after the selective v0.6.0 renderer update passes three of five
oracle cases; two differ at TOC placement. See
[remaining migration items](remaining-migration-items.md) and the
[follow-up execution plan](upstream-v0.4.0-followup-plan.md) for the current
implementation and pending gates.
