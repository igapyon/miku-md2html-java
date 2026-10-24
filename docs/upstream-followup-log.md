# Upstream Follow-up Log

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
