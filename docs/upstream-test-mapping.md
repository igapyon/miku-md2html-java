# Upstream Test Mapping

The first Java tests are focused parity probes, not a port of the complete
upstream suite.

| Upstream test intent / file | Java test target | Current coverage |
| --- | --- | --- |
| Deterministic output, metadata, heading IDs, and TOC (`tests/core.test.ts`) | `MikuMd2htmlCoreTest` | Basic headings/title/TOC and front matter/override behavior. |
| GFM, footnotes, and safe raw HTML (`tests/core.test.ts`) | `MikuMd2htmlCoreTest` | Basic emphasis, active HTML removal, and unsafe link removal. Footnotes/full GFM remain open. |
| Image accounting, URI validation, formats, and size limits (`tests/image-embedding.test.ts`, `tests/uri.test.ts`) | `MikuMd2htmlCoreTest` | Base64 PNG embedding and occurrence/unique-asset accounting only. |
| Local root containment and symlinks (`tests/node-assets.test.ts`) | `LocalAssetResolverTest` | Basic local read and traversal rejection; symlink/change tests remain. |
| CLI output, reports, collisions, invalid UTF-8, and caps (`tests/cli.test.ts`, `tests/cli-failures.test.ts`, `tests/output-files.test.ts`) | `CliArgumentsTest` | Shared option parsing, defaults, and usage errors only. File publication tests remain. |
| Language aliases, bounds, source text, and deterministic tokens (`tests/syntax-highlight.test.ts`) | Not yet covered | Highlighting is not ported. |
| Refactor contract goldens and output stability (`tests/refactoring-contract.test.ts`) | Not yet covered | No Node output snapshots have been copied. |

Run the current focused suite with `mvn test`. Add Java tests alongside each
new ported unit and update its row and migration status here.
