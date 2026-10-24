# Upstream Test Mapping

The Java suite is a set of focused probes, not a port of the complete Node
suite. `mvn -f pom.xml package` currently runs 43 JUnit tests.

| Upstream test intent / file | Java test target | Current coverage |
| --- | --- | --- |
| Metadata, heading IDs, TOC, empty input, and conversion result (`tests/core.test.ts`) | `MikuMd2htmlCoreTest`, `MarkdownBlocksTest` | Basic metadata, headings, text blocks, empty Markdown, and selected output behavior. |
| GFM tables, autolinks, strikethrough, task lists, and footnotes (`tests/core.test.ts`) | `GfmTest` | One combined GFM probe; exact table tokenization and footnote edge cases remain. |
| Raw HTML allowlist, active content, unsafe links (`tests/core.test.ts`) | `ArticleSanitizerTest` | Basic active subtree and unsafe link sanitation; source/generated provenance and full diagnostics remain. |
| Front matter and YAML warnings (`tests/core.test.ts`) | `FrontMatterParserTest` | Five cases for YAML CoreSchema selection, block scalars, unknown values, and invalid roots; alias limits and full corpus remain. |
| Raster headers, dimension parsing, and edge forms (`tests/image-embedding.test.ts`) | `RasterDimensionsTest`, `MikuMd2htmlCoreTest` | PNG/CgBI, JPEG including DNL, and WebP reader paths; full upstream byte corpus remains. |
| Image URI validation and image formats (`tests/uri.test.ts`) | `ImageUriTest` | Two focused URI probes; full malformed/base64 boundary corpus remains. |
| Image embedding, accounting, and SVG (`tests/image-embedding.test.ts`) | `InlineSvgTest`, `SvgSafetyTest`, `MikuMd2htmlCoreTest` | Raster/data embedding, SVG CSS and XML checks, and nested raster validation; phase ordering and full output-cap boundaries remain. |
| Local root containment and file behavior (`tests/node-assets.test.ts`) | `LocalAssetResolverTest` | Basic local read and traversal checks; symlink, hard-link, change-race, and attempted-path parity remain. |
| CLI parsing (`tests/cli.test.ts`) | `CliArgumentsTest` | Defaults, shared option parsing, and usage errors. |
| CLI output/report collisions and I/O failures (`tests/cli.test.ts`, `cli-failures.test.ts`, `output-files.test.ts`) | — | Runtime smoke checks basic report generation and no-force protection; failure injection and publication races remain uncovered by JUnit. |
| Language aliases and deterministic highlighted tokens (`tests/syntax-highlight.test.ts`) | — | Not ported; code remains escaped plain text. |
| Full deterministic Node contract (`tests/refactoring-contract.test.ts`) | `scripts/compare-node-java.mjs` | Five tracked oracle cases; four currently match completely and one GFM table case still differs. |

Run the tracked suite with `mvn -f pom.xml test`. The Node capture and packaged
JAR comparison steps are documented in [development](development.md); they are
not part of ordinary JUnit execution and do not claim full parity.
