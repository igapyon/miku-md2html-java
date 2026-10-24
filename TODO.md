# TODO

## Node v0.4.0 Follow-up

- [ ] Complete the remaining tasks in the [Luna execution plan](docs/upstream-v0.4.0-followup-plan.md).
- [ ] Expand the fixed Node oracle beyond five cases and resolve the remaining GFM table output difference.
- [ ] Implement the source/provenance article tree, full image reference and budget pipeline, and remaining CLI collision/race cases.
- [ ] Keep syntax highlighting explicitly pending until the Rhino Java 8/25 isolation and budget gate passes.
- [ ] Run the new GitHub CI on a published branch; confirm the same packaged JAR on Java 8 and 25.
- [ ] Confirm Release asset attachment after a human prepares a matching tag and Release.
- [ ] Do not bump the Java artifact version or claim full parity as part of this follow-up without a separate explicit release decision.

The current compatibility source is Node v0.4.0 at the pinned commit, while the
Java artifact is `0.3.6`. See [implementation status](docs/remaining-migration-items.md)
and the [gap analysis](docs/upstream-v0.4.0-gap-analysis.md).
