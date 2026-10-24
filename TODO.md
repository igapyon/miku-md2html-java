# TODO

## Node v0.5.0 Follow-up — Planned

- [ ] After implementation is requested, execute P00–P10 in the
  [Node v0.5.0 Luna execution plan](docs/upstream-v0.5.0-followup-plan.md).
- [ ] Cover default raster references, explicit embedding, warning policies,
  bounded inspection, complete reference tracking, output protection, and
  packaged-runtime checks without adding libraries.
- [ ] Keep the v0.4.0 fixtures and unrelated unfinished migration work visible;
  update the compatibility source only after the v0.5.0 acceptance checks.

This is a plan for the Node v0.5.0 follow-up. The compatibility baseline
remains Node v0.4.0 and the Java artifact is now `0.4.1`.

Version policy: keep `0.4.1` during the current implementation work. Use later
`0.4.x` patch versions for verifiable intermediate compatibility and GitHub
Actions checkpoints. Set the completed release to `0.5.0` after Node v0.5.0
equivalence and workflow validation are complete.

## Node v0.4.0 Follow-up

- [ ] Complete the remaining tasks in the [Luna execution plan](docs/upstream-v0.4.0-followup-plan.md).
- [ ] Expand the fixed Node oracle beyond five cases and resolve the remaining GFM table output difference.
- [ ] Implement the source/provenance article tree, full image reference and budget pipeline, and remaining CLI collision/race cases.
- [ ] Keep syntax highlighting explicitly pending until the Rhino Java 8/25 isolation and budget gate passes.
- [ ] Run the new GitHub CI on a published branch; confirm the same packaged JAR on Java 8 and 25.
- [ ] Confirm Release asset attachment after a human prepares a matching tag and Release.
- [ ] Do not bump the Java artifact version or claim full parity as part of this follow-up without a separate explicit release decision.

The current compatibility source is Node v0.4.0 at the pinned commit, while the
Java artifact is `0.4.1`. See [implementation status](docs/remaining-migration-items.md)
and the [gap analysis](docs/upstream-v0.4.0-gap-analysis.md).
