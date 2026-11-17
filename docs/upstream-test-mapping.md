# Upstream Test Mapping

The Java suite is a set of focused probes, not a port of the complete Node
suite. `mvn -f pom.xml test` currently runs 63 JUnit tests.

| Upstream test intent / file | Java test target | Current coverage |
| --- | --- | --- |
| Metadata, heading IDs, TOC, empty input, and conversion result (`tests/core.test.ts`) | `MikuMd2htmlCoreTest`, `MarkdownBlocksTest` | Basic metadata, headings, text blocks, empty Markdown, and selected output behavior. |
| Leading-H1 TOC placement and structural whitespace (`tests/html-output.test.ts`, Node v0.6.0) | `HtmlOutputV060Test`, `GeneratedTableWhitespaceParityTest`, `GeneratedTableWhitespaceTest` | Four focused renderer cases plus 33 pinned table-whitespace cases. Twenty-four cases compare all HTML bytes, metadata, diagnostics, and stats exactly; seven cases carry explicit field-scoped existing differences, and two parser differences are recorded without counting as exact. This is not a full v0.6.0 oracle. |
| GFM tables, autolinks, strikethrough, task lists, and footnotes (`tests/core.test.ts`) | `GfmTest` | One combined GFM probe; exact table tokenization and footnote edge cases remain. |
| Raw HTML allowlist, active content, unsafe links (`tests/core.test.ts`) | `ArticleSanitizerTest` | Basic active subtree and unsafe link sanitation; source/generated provenance and full diagnostics remain. |
| Front matter and YAML warnings (`tests/core.test.ts`) | `FrontMatterParserTest` | Five cases for YAML CoreSchema selection, block scalars, unknown values, and invalid roots; alias limits and full corpus remain. |
| Raster headers, dimension parsing, and edge forms (`tests/image-embedding.test.ts`) | `RasterDimensionsTest`, `MikuMd2htmlCoreTest` | PNG/CgBI, JPEG including DNL, and WebP reader paths; full upstream byte corpus remains. |
| Image URI validation and image formats (`tests/uri.test.ts`) | `ImageUriTest` | Two focused URI probes; full malformed/base64 boundary corpus remains. |
| Image embedding, accounting, warnings, and SVG (`tests/image-embedding.test.ts`) | `UpstreamV050ImageParityTest`, `ImageEmbeddingSessionTest`, `InlineSvgTest`, `SvgSafetyTest` | 27 pinned Node v0.5.0 cases match image attributes, diagnostics including source ranges, and image stats. Full document hashes/byte counts are excluded because the Java renderer also contains the selective v0.6.0 whitespace update; output-budget boundaries remain. |
| Local root containment and file behavior (`tests/node-assets.test.ts`) | `LocalAssetResolverTest` | Basic local read and traversal checks; symlink and change-race coverage remains. |
| CLI parsing (`tests/cli.test.ts`) | `CliArgumentsTest` | Defaults, shared option parsing, and usage errors. |
| CLI output/report collisions and I/O failures (`tests/cli.test.ts`, `cli-failures.test.ts`, `output-files.test.ts`) | `FileConversionTest` | Tests protect image paths from forced HTML/report replacement, including a report hard link and malformed-front-matter failure report; race and report-commit injection coverage remains partial. |
| Language aliases and deterministic highlighted tokens (`tests/syntax-highlight.test.ts`) | — | Not ported; code remains escaped plain text. |
| Full deterministic Node contract (`tests/refactoring-contract.test.ts`) | `scripts/compare-node-java.mjs`, `GeneratedTableWhitespaceParityTest`, `UpstreamV050ImageParityTest` | v0.4.0 packaged comparison with explicit `embed` passes three of five cases; two retain the selected TOC placement difference. The generated-table spacing difference is now covered by the focused v0.6.0 oracle; GFM inline-code pipe parsing and broader renderer differences remain. See the [focused implementation record](generated-table-whitespace-plan.md). Separate v0.5.0 image comparison passes 27 cases for image attributes, diagnostics/source ranges, and image stats, excluding full HTML hashes/byte counts. |

Run the tracked suite with `mvn -f pom.xml test`. The Node capture and packaged
JAR comparison steps are documented in [development](development.md); they are
not part of ordinary JUnit execution and do not claim full parity.
