# Remaining Migration Items

## Current Stage

The Java runtime has a partial implementation against the pinned Node v0.4.0
source. This is not a full parity claim. The compatibility baseline is in
[upstream snapshot](upstream-snapshot.md), and tracked Node outputs are under
`src/test/resources/parity/v0.4.0/expected/`.

## Implemented in the Current Work

- [x] Empty Markdown handling, remote-image classification, encoded local image
  paths, and Node-style repeated-hyphen slug normalization.
- [x] Raster dimensions for PNG/CgBI, JPEG including DNL, and WebP chunk forms;
  large-image warning deduplication.
- [x] YAML 1.2 CoreSchema front matter through SnakeYAML Engine.
- [x] Flexmark parsing for image and non-image inputs, with tables, autolink,
  GFM strikethrough, task lists, and footnotes enabled.
- [x] jsoup HTML5 fragment sanitation with an allowlist, unsafe-link removal,
  raw image routing, and inline SVG-to-data-image conversion.
- [x] SVG XML hardening, strict UTF-8, namespace and element checks, nested
  raster validation, and ph-css AST checks for SVG style content and attributes.
- [x] Five pinned Node v0.4.0 oracle fixtures, provenance hashes, and a
  packaged-JAR comparison script.
- [x] CI workflow for package/tests and packaged-runtime checks on Java 8/25;
  Release asset workflow for human-published Releases.
- [x] Runtime and distribution ZIP license notices plus dependency inventory.

## Remaining Differences and Unverified Work

- Flexmark and jsoup do not yet form one provenance-carrying article tree.
  GFM table handling differs on a pipe inside an inline code span, and the
  current serializer does not preserve Node's blank-line spacing after a
  reference definition.
- Footnote edge cases, repeated back references, raw HTML provenance, and exact
  warning source ranges still need full fixture coverage.
- The jsoup sanitizer derives some raw HTML source positions from rendered
  fragment positions. It does not carry source/generated provenance through an
  article tree, so id/class spoofing and source ranges need broader tests.
- Syntax highlighting is not ported. Rhino remains isolated in the dependency
  probe; Java 8/25 availability and the runtime isolation gate are unverified.
- Image handling does not yet match Node's prepare/estimate/embed phases,
  resolver call counts, complete attempted-path tracking, or all output-budget
  boundaries.
- CLI output/report staging now prepares both files before commit. Injected
  report-stage, report-commit, and internal staging failures, plus missing-input
  failure reports, have focused tests. Reference-scan collision safety and
  filesystem-race cases still need the Node failure-injection suite.
- The upstream 10-file test suite is not fully mapped to Java tests. The current
  Node/JAR comparison reports four of five tracked exact-output cases passing.
- The local host exposes OpenJDK 17 only. The new CI workflows have not run on
  GitHub, so Java 8/25 runtime checks and Release asset attachment remain
  pending.

## Verification

The current implementation compiles with `--release 8` on OpenJDK 17 and runs
43 JUnit tests successfully. The local packaged-JAR smoke checks version, help,
data-image conversion, report fields, and no-force output protection. The
Release helper has passed a local positive staging run for exactly the runtime
and sources JARs, plus a tag/POM mismatch rejection before staging.

The fixed Node comparison currently reports:

| Fixture | Result |
| --- | --- |
| `empty` | HTML, metadata, diagnostics, and stats match |
| `commonmark-gfm` | GFM table inline-code pipe parsing and reference-definition spacing differ; diagnostics match; report schema v2 fields are compared by focused CLI tests |
| `front-matter` | HTML, metadata, diagnostics, and stats match |
| `raw-html` | HTML, metadata, diagnostics, and stats match |
| `unicode-slugs` | HTML, metadata, diagnostics, and stats match |

See [the upstream test map](upstream-test-mapping.md),
[dependency decisions](dependency-decisions.md), and
[the execution plan](upstream-v0.4.0-followup-plan.md) for the detailed backlog.
