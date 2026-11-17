# TODO

## Node v0.6.0 Renderer Update — Applied Selectively

- [x] Place the generated table of contents after a leading direct article H1.
- [x] Limit repeated formatting blank lines between structural elements while
  preserving inline text and code content.
- [x] Add focused Java regressions for the Node v0.6.0 TOC placement and
  structural-whitespace changes.
- [x] Align Markdown-generated table whitespace with 24 exact Node v0.6.0
  cases; seven scoped regression cases and two known parser differences remain
  separately classified in the [execution record](docs/generated-table-whitespace-plan.md).
- [ ] Expand the focused Node v0.6.0 oracle to the full renderer/core contract
  before changing the compatibility source.
- [ ] Investigate table recognition inside list items without a blank line
  between the preceding text and table; this is separate from whitespace output.

The Java artifact version is now `0.6.0`, aligned to the Node v0.6.0 version
number by explicit decision. The compatibility source remains Node v0.4.0.
The broader Node v0.5.0 image-pipeline follow-up below is still open; this
version alignment does not claim full Node v0.6.0 parity.

## Node v0.5.0 Follow-up — In Progress

- [x] Compare the pinned Node v0.5.0 image oracle's 27 cases for image
  attributes, diagnostic fields and source ranges, and image accounting.
- [x] Track local image references independently from reads and guard HTML and
  report targets, including malformed-front-matter failure reports.
- [ ] Complete the remaining P00–P10 acceptance work in the
  [Node v0.5.0 Luna execution plan](docs/upstream-v0.5.0-followup-plan.md).
- [ ] Cover default raster references, explicit embedding, warning policies,
  bounded inspection, filesystem race cases, and packaged-runtime checks
  without adding libraries.
- [ ] Compare full HTML against a pinned Node v0.6.0 oracle and retain known
  renderer changes separately from image parity. The targeted generated-table
  whitespace fixture now covers 33 cases; it is not a full v0.6.0 oracle.
- [x] Defer the pre-read output-size estimate for now; the final output cap
  still rejects oversized HTML.
- [ ] Keep the v0.4.0 fixtures and unrelated unfinished migration work visible;
  update the compatibility source only after the v0.5.0 acceptance checks.

This is a plan for the Node v0.5.0 follow-up. The compatibility baseline
remains Node v0.4.0. The Java artifact is now `0.6.0` by separate version
alignment; the number does not declare Node v0.5.0 or v0.6.0 equivalence.

The Java artifact version does not declare Node v0.5.0 equivalence. Keep Node
v0.4.0 as the compatibility baseline until the v0.5.0 acceptance checks and
GitHub Actions workflow validation are complete.

## Node v0.4.0 Follow-up

- [ ] Complete the remaining tasks in the [Luna execution plan](docs/upstream-v0.4.0-followup-plan.md).
- [ ] Expand the fixed Node oracle beyond five cases and resolve the remaining GFM table output difference.
- [ ] Implement the source/provenance article tree, full image reference and budget pipeline, and remaining CLI collision/race cases.
- [ ] Keep syntax highlighting explicitly pending until the Rhino Java 8/25 isolation and budget gate passes.
- [ ] Rerun the new GitHub CI after replacing the smoke helper's unavailable `rg` dependency; confirm the same packaged JAR on Java 8 and 25.
- [ ] Confirm Release asset attachment after a human prepares a matching tag and Release.
- [x] Align the Java artifact version to `0.6.0` by separate explicit decision;
  keep Node v0.4.0 as the compatibility baseline and do not claim full parity.

The current compatibility source is Node v0.4.0 at the pinned commit, while the
Java artifact version is `0.6.0`. See [implementation status](docs/remaining-migration-items.md)
and the [gap analysis](docs/upstream-v0.4.0-gap-analysis.md).
