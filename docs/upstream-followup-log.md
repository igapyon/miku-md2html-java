# Upstream Follow-up Log

## 2026-11-17 — Node v0.6.0 renderer update

- Inspected the Node `v0.6.0` release at commit
  `4a0918713f8b94fffe8d447e809800a2e096cb84`. The v0.5.0-to-v0.6.0 source
  delta adds leading-H1 TOC placement and structural formatting whitespace
  limiting in HTML output.
- Ported these behaviors to `HtmlDocumentRenderer` and the new
  `HtmlStructuralWhitespace` helper. Jsoup source ranges limit replacements to
  structural whitespace runs, leaving other serialized article content intact.
- `mvn -f pom.xml -DskipTests compile` succeeded with Java 8 release settings
  on OpenJDK 17. No JUnit suite or v0.6.0 oracle comparison was run, and no
  v0.6-specific Java regression fixture has been added.
- The Java artifact remains `0.5.0`; the compatibility source remains Node
  v0.4.0. The pending v0.5.0 image-pipeline work and full parity gates remain
  separate follow-up items.

## 2026-10-23 — Initial source inventory

- Compared upstream `miku-md2html` v0.3.5, commit
  `065c828dc6e987a39bdd7a4a15ea48a97ca2eccd`.
- Java target starts with the CLI/runtime core. Browser-only UI is out of
  scope; viewer CSS and fixed script remain part of the generated HTML artifact.
- The user prefers no third-party runtime libraries. Use Java 8 JDK APIs and
  track parser, sanitizer, and syntax-highlighting parity gaps explicitly.
- No upstream behavior change has been adopted as a Java-only correction.

## 2026-10-23 — First runnable Java path

- Added the JDK-only core conversion entry point, a conservative Markdown/GFM
  subset, local and data-URL image embedding, SVG checks, and a file CLI.
- Kept the Node-to-Java mapping at the responsibility and behavior level;
  several related Node files are consolidated into the Java converter or CLI.
- Added eight focused JUnit tests. The implementation and known gaps are tracked
  in [remaining migration items](remaining-migration-items.md).
- No third-party runtime dependency was introduced. JUnit Jupiter remains
  test-scoped.

## 2026-10-24 — v0.4.0 audit and implementation planning

- Confirmed upstream v0.4.0, commit
  b18a6e390425bd0e15387a2d608980a42a1809e9, against the clean local checkout.
- Audited the v0.3.5-to-v0.4.0 diff and the existing Java migration gaps.
  Compared 19 representative inputs using the existing Node 0.4.0 bundle
  and Java 0.3.5 jar; the sources jar matched all current Java sources.
- Recorded the source and runtime findings in
  [the gap analysis](upstream-v0.4.0-gap-analysis.md), including empty-input,
  SVG CSS validation, raster dimensions, parser, report, and CLI differences.
- Prepared [the Luna execution plan](upstream-v0.4.0-followup-plan.md) with
  task dependencies, focused checks, acceptance criteria, and failure gates.
- The user authorized planning for minimal bundled libraries where justified.
  Candidate versions and adoption gates are proposals; no library was added.
- This was a planning-only turn. Product sources, POM, resources, and product
  tests were not changed. The active compatibility baseline remains v0.3.5.

## 2026-10-24 — GitHub Actions planning

- Confirmed that this repository has no workflow directory.
- Reviewed the shared Java release template, sister Java release workflows,
  and current official GitHub Actions documentation.
- Added the [GitHub Actions plan](github-actions-plan.md): GA01 for CI and
  Java 8/25 packaged-runtime checks, GA02 for runtime/source Release assets.
- Recorded the tag/POM/runtime version check and the immutable-release
  setting as implementation conditions. No workflow YAML, helper script,
  POM change, or GitHub operation was performed.

## 2026-10-24 — miku-soft reference alignment

- Compared the installed skill and selected shared references with the clean
  igapyon-agent-skills source at c61466dc03f7ed4a532fd6a2b37ef5a29be0071c;
  SKILL.md and eight reference/template files matched byte for byte.
- Added the specific reference index and project application notes.
  Clarified that GA02 follows the shared Release asset default, while GA01,
  exact version matching, and Action SHA pinning are project proposals.
- Retained upstream semantics, the Java 8 floor, minimal justified bundled
  libraries, and separate implementation status in the plans.
- This remained planning-only work; no product or workflow implementation
  was added.

## 2026-10-24 — Node v0.4.0 follow-up implementation

- Started the authorized implementation from the execution plan without
  changing the Java artifact version (`0.3.5`). Added Flexmark, jsoup,
  SnakeYAML Engine, and ph-css as fixed runtime dependencies in the shaded JAR;
  Rhino remains probe-only.
- Implemented empty-input, URI/slug, raster-dimension, YAML front matter,
  CommonMark/GFM, HTML5 sanitation, and SVG XML/CSS safety changes. Added
  runtime license notices and included them in the dist ZIP.
- Added five fixed Node v0.4.0 oracle fixtures with input/result hashes and a
  packaged-JAR comparator. Latest comparison: empty, front-matter, raw-html,
  and unicode-slugs pass; commonmark-gfm still differs in table parsing and
  reference-definition spacing.
- Added package/runtime smoke CI for Java 8 and 25 and a human-published
  Release asset workflow. Locally verified 33 JUnit tests, JDK 17 packaged
  smoke, positive asset staging and mismatched-tag rejection. Java 8/25 hosted
  runs and actual Release upload remain unverified.
- `docs/upstream-v0.4.0-followup-plan.md` now records partial task status and
  remaining acceptance gates. The implementation has not been committed,
  pushed, or published as a GitHub Release.

## 2026-10-24 — CLI publication and report contract

- Added a package-private output operations seam and stage-both-before-commit
  publication order. Focused tests cover report staging failure, report commit
  failure, internal staging failure, and cleanup of temporary output files.
- Moved output/report target inspection before input reading so a missing input
  can still produce a safe failure report. Reports omit input bytes when the
  file was not read.
- Updated reports to schema version 2 with image mode, description, image
  occurrence counters, details, optional source end positions, and stable
  diagnostic ordering. Full source-range production and byte parity remain open.
- Added per-path asset describe caching, same-id size checks, and unexpected
  resolver contract failure propagation. Local candidate tracking now retains
  missing/outside paths and modification time at filesystem precision.
- Focused CLI/image/filesystem tests pass. Remaining reference-scan completion,
  collision gate, race injection, and output-budget parity are still pending.

## 2026-10-24 — Java artifact version 0.3.6

- Applied the requested SemVer patch increment from `0.3.5` to `0.3.6`.
- Updated the Maven project version, CLI/report metadata version, and current
  README, CLI, development, snapshot, and execution-plan references. Historical
  audit records retain `0.3.5` where that was the artifact actually inspected.
- The miku-scm READONLY validation proposed `0.3.6` from `pom.xml` under the
  SemVer patch policy. `mvn -f pom.xml package` passed all 43 tests; the new
  `0.3.6` JAR passed packaged-runtime smoke on OpenJDK 17 and reports `0.3.6`.
- The Node oracle remains 4/5 exact-output cases. Changes remain unstaged; no
  commit, tag, push, or Release was created.

## 2026-11-17 — Node v0.5.0 image checks and output protection

- Compared 27 pinned Node v0.5.0 image cases against Java. Image attributes,
  diagnostic codes/messages/details/source ranges, and image accounting match.
  Full HTML hashes and byte counts are not compared because the Java renderer
  includes the selective Node v0.6.0 whitespace update.
- Fixed embed-mode local-file failures to emit Node-compatible warnings and
  omit the image. Matched the Node SVG event-handler diagnostic message and
  unsupported-attribute classification.
- Added source spans from Flexmark image nodes and used tracked jsoup source
  ranges for raw `img` tags, so image diagnostics point to their original
  markup and include end positions.
- Added independent image-reference tracking. A malformed-front-matter scan
  records image paths without calling asset describe/read, and incomplete scans
  prevent failure-report publication. Forced HTML output and report hard-link
  collisions preserve the referenced image bytes.
- `mvn -f pom.xml test` passes all 52 JUnit tests; `git diff --check` passes.
  Java artifact version remains `0.5.0`; no commit or push was made.

## 2026-11-17 — Node v0.6.0 renderer regressions

- Added four focused Java regressions for the Node v0.6.0 TOC placement and
  structural-whitespace behavior. They cover leading-H1 TOC placement,
  empty article boundaries, whitespace after removed comments/scripts, nested
  containers, and code text.
- The regression cases exposed an extra trailing newline caused by a synthetic
  parser sentinel. The Java output now retains both template boundary newlines
  for empty articles and omits the synthetic trailing newline after rendered
  blocks.
- The old v0.4.0 packaged comparison now passes three of five cases; the other
  two differ at TOC placement. The 27-case v0.5.0 image comparison passes.
- `mvn -f pom.xml package` passes all 56 JUnit tests; the packaged runtime
  smoke check and `git diff --check` pass. Full v0.6.0 HTML oracle comparison
  remains pending. Java artifact version remains `0.5.0`; no commit or push was
  made.

## 2026-11-17 — Generated-table whitespace investigation and plan

- Compared 18 minimal inputs against the clean Node v0.6.0 checkout and the
  current packaged Java JAR. Every conversion succeeded with no diagnostics.
  The blank-line difference occurs before Markdown-generated tables with or
  without reference definitions, including quoted and loose-list contexts.
  Ordinary paragraph spacing and a compact raw HTML table match.
- Traced Node's intermediate trees: `remark-rehype` generates formatting LF
  nodes within tables, `rehype-raw` moves them before the table, and the final
  renderer limits block-boundary runs to two LF. Java currently deletes the
  internal table whitespace. The earlier reference-definition cause was an
  unconfirmed inference and is corrected by this experiment.
- Recorded the bounded implementation and fixture plan in
  [generated-table-whitespace-plan.md](generated-table-whitespace-plan.md).
  A list item with no blank line before the table has a separate table
  recognition difference and remains a parser follow-up.
- This change records the plan and corrects documentation; runtime code and
  tracked test fixtures have not been changed. General maintenance uplift was
  skipped because the request is limited to this whitespace plan.

## 2026-11-17 — Luna implementation handoff plan

- Expanded [the generated-table whitespace plan](generated-table-whitespace-plan.md)
  into L00–L06 with concrete file ownership, capture provenance, fixture IDs,
  comparison scopes, and acceptance checks.
- Specified removal of table-only internal attributes before the diagnostic
  HTML parse, followed by generated-table spacing after sanitization. This
  prevents the new markers and LF from shifting tracked diagnostic coordinates.
  The required jsoup attribute-range APIs exist in the current packaged JAR;
  actual integration remains an implementation task.
- The user requested planning only in this turn. No product code, tests,
  fixtures, scripts, dependencies, or version files were changed or executed
  as implementation. The existing uncommitted investigation documents are
  preserved for the later implementation turn.

## 2026-11-18 — Generated-table whitespace implementation

- Completed L00–L06 from the [generated-table whitespace plan](generated-table-whitespace-plan.md).
  L00 used branch `devel-tiga1024vfh`, HEAD `d1bdb7bdea8d6bba3fa45e673dd4a59a6ff6a338`,
  a freshly packaged `0.5.0` JAR (`f24ebdc...0824cd`), 56 passing tests,
  passing packaged smoke, and the existing Node v0.4.0 comparator result of 3/5.
- Added per-conversion `TableBlock` markers and
  `GeneratedTableWhitespace`. It removes only registered marker bytes before
  the diagnostic jsoup parse, translates table offsets after removal, then
  adjusts whitespace after sanitation. Table structure determines transferred
  LF counts when an inline element precedes a table; block boundaries continue
  through the existing two-LF limiter.
- Captured 33 fixed Node v0.6.0 cases with the pinned runtime. All 24 `exact`
  cases match full HTML bytes, metadata, diagnostics, stats, and `htmlBytes`.
  Seven `regression` cases document narrow pre-existing differences: empty
  `tbody`, fenced-code warning, raw image/link diagnostic coordinates, raw
  unsupported-attribute wording/source end, and a one-LF TOC/article boundary.
  Two `known-difference` cases retain no-blank-line list recognition and the
  old `commonmark-gfm` parser/TOC fixture.
- The 63-test Maven package and packaged-runtime smoke pass. The old Node v0.4.0
  comparator remains 3/5; its two TOC cases are still outside this task. The
  GFM inline-code pipe difference and broader v0.6.0 parity remain open.
- At completion of the generated-table whitespace implementation, the Java
  artifact was still `0.5.0`; compatibility baseline remained Node v0.4.0.
  That implementation did not change dependencies, public API, version, tag,
  commit, push, or Release state.

## 2026-11-18 — Java artifact version aligned to upstream v0.6.0

- By explicit decision, changed the Java artifact and CLI-reported version from
  `0.5.0` to `0.6.0`, matching the upstream release number.
- This is version-number alignment only. The compatibility baseline remains
  Node v0.4.0; the focused v0.6.0 renderer coverage does not establish full
  Node v0.6.0 parity, and the Node v0.5.0 image-pipeline follow-up remains open.
- No compatibility baseline, API, dependency, tag, or published Release was
  changed by this version update.
