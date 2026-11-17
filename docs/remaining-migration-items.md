# Remaining Migration Items

## Current Stage

The Java runtime has a partial implementation against the pinned Node v0.4.0
source. This is not a full parity claim. The compatibility baseline is in
[upstream snapshot](upstream-snapshot.md), and tracked Node outputs are under
`src/test/resources/parity/v0.4.0/expected/`.

The Java artifact version is `0.6.0`, aligned to the upstream v0.6.0 version
number by explicit decision. The compatibility baseline remains Node v0.4.0;
the version alignment does not claim full v0.6.0 parity.

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
- [x] Selectively port Node v0.6.0 leading-H1 TOC placement and structural
  blank-line limiting and Markdown-generated table whitespace. Four renderer
  tests and 33 pinned Node v0.6.0 table-whitespace cases pass: 24 exact,
  seven field-scoped regressions, and two documented parser differences. This
  does not advance the compatibility baseline or claim full v0.6.0 parity.

## Remaining Differences and Unverified Work

- Flexmark and jsoup do not yet form one provenance-carrying article tree.
  GFM table handling still differs on a pipe inside an inline code span. The
  generated-table whitespace delta matches the focused Node v0.6.0 oracle,
  including quoted/list contexts and raw inline images. The fixture records
  existing exceptions: jsoup's empty `tbody` for a header-only table, one
  fenced-code warning, raw-HTML diagnostic source/message differences, and a
  one-LF TOC/article boundary difference. Tables following text without a
  blank line inside list items still differ in recognition; that parser issue
  is separate from whitespace output. Details are in the
  [generated-table implementation record](generated-table-whitespace-plan.md).
- Footnote edge cases, repeated back references, general raw HTML provenance,
  and non-image warning source ranges still need full fixture coverage. The
  27 pinned Node v0.5.0 image cases now match warning start/end ranges.
- The jsoup sanitizer derives some raw HTML source positions from rendered
  fragment positions for general HTML diagnostics. Markdown and raw `img`
  diagnostics use a dedicated image source index; broader source/generated
  provenance and id/class spoofing still need tests.
- Syntax highlighting is not ported. Rhino remains isolated in the dependency
  probe; Java 8/25 availability and the runtime isolation gate are unverified.
- Image handling now has pinned v0.5.0 checks for 27 image cases, including
  warning source ranges, and tracks local paths independently of asset reads.
  The pre-read prepare/estimate/embed phases and remaining output-budget
  boundaries are still open; pre-read estimation is deferred for now.
- CLI output/report staging now prepares both files before commit. Injected
  report-stage, report-commit, and internal staging failures, plus missing-input
  failure reports, have focused tests. Completed reference scans now protect
  HTML and report targets, including force writes, hard links, and malformed
  front matter. Filesystem-race cases still need the Node failure-injection
  suite; an interrupted scan suppresses failure-report publication.
- The upstream 10-file test suite is not fully mapped to Java tests. The v0.4.0
  packaged-JAR comparison was rerun with explicit `embed`: three of five outputs
  match exactly. The other two differ at TOC placement after the selective
  v0.6.0 renderer update. The focused Node v0.6.0 table-whitespace oracle now
  passes 24 exact cases and records seven bounded regressions plus two known
  parser differences. A full pinned v0.6.0 core/renderer oracle is still needed;
  GFM inline-code pipe parsing remains a known difference.
- The local host exposes OpenJDK 17 only. The new CI workflows have not run on
  GitHub, so Java 8/25 runtime checks and Release asset attachment remain
  pending.

## Verification

The current implementation compiles with `--release 8` on OpenJDK 17 and runs
63 JUnit tests successfully. The local packaged-JAR smoke checks version, help,
data-image conversion, report fields, and no-force output protection. The
Release helper has passed a local positive staging run for exactly the runtime
and sources JARs, plus a tag/POM mismatch rejection before staging.

The fixed Node v0.4.0 comparison currently reports:

| Fixture | Result |
| --- | --- |
| `empty` | Exact HTML, metadata, diagnostics, and stats match |
| `commonmark-gfm` | HTML differs at the selected TOC placement; GFM table inline-code pipe parsing and generated-table spacing remain known differences |
| `front-matter` | Exact HTML, metadata, diagnostics, and stats match |
| `raw-html` | Exact HTML, metadata, diagnostics, and stats match |
| `unicode-slugs` | HTML differs at the selected TOC placement; metadata and diagnostics match |

The v0.5.0 image oracle separately passes 27 cases for image attributes,
diagnostics including source ranges, and image statistics. It does not compare
full document hashes or `htmlBytes`, which include the selective renderer
delta.

See [the upstream test map](upstream-test-mapping.md),
[dependency decisions](dependency-decisions.md), and
[the execution plan](upstream-v0.4.0-followup-plan.md) for the detailed backlog.
