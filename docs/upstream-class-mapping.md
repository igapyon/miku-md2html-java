# Upstream Class Mapping

This mapping keeps responsibilities traceable while allowing Java-side class
boundaries to fit the current partial port. Parser and sanitizer libraries are
internal adapters; their types do not enter the public core API.

| Upstream file | Java class or package | Responsibility / status |
| --- | --- | --- |
| `src/ts/index.ts` | `core.MikuMd2htmlCore`, `core.MikuMd2htmlMetadata` | Public conversion entry point and runtime metadata. |
| `src/ts/types.ts` | `core.Diagnostic`, `core.SourcePosition`, `core.AssetResolver`, `core.AssetInfo`, `core.ConvertOptions`, `core.ConvertResult`, `core.ConversionStats`, `core.AssetAccessException` | Public request/result/diagnostic and asset contracts. Request is passed as method arguments plus options. |
| `src/ts/core/converter.ts` | `core.MarkdownConverter` | Stage order, metadata, diagnostics, size checks, and result assembly. |
| `src/ts/core/diagnostics.ts` | `core.Diagnostics` | Stable ordering, error detection, and diagnostic creation. |
| `src/ts/core/front-matter.ts` | `core.FrontMatterParser`, `core.FrontMatter` | SnakeYAML Engine CoreSchema parsing and title/description/lang selection; alias/resource limits and full corpus parity remain open. |
| `src/ts/core/slug.ts` | `core.Slug` | Heading normalization and unique IDs. |
| `src/ts/core/document-structure.ts` | `core.DocumentStructure`, `core.MarkdownParser` | Headings, TOC, IDs, and fragment rewriting; footnote ID coverage is partial. |
| `src/ts/core/hast-utils.ts` | `core.InlineMarkdownParser.plainText` | Plain-text extraction used for image alt text and fallback labels. Other source-map helpers remain unported. |
| `src/ts/core/article-sanitizer.ts` | `core.ArticleSanitizer`, `core.markdown.FlexmarkAdapter`, `core.markdown.ImageSourceIndex`, `core.markdown.GeneratedTableWhitespace`, `core.ImageInspector` | jsoup HTML5 sanitation; Markdown image AST spans feed image diagnostics, and registered Markdown tables retain Node v0.6.0 structural whitespace. General source/generated provenance and transformed raw HTML diagnostics remain incomplete. |
| `src/ts/core/html-document.ts` | `core.HtmlDocumentRenderer` | Self-contained HTML shell, CSP, leading-H1 TOC placement, CSS, and fixed viewer script. |
| `src/ts/core/html-whitespace.ts` | `core.HtmlStructuralWhitespace`, `core.markdown.GeneratedTableWhitespace` | v0.6.0 structural formatting whitespace limit plus generated-table formatting whitespace transfer; range-based replacements preserve other serialized article bytes. |
| `src/ts/core/image-embedding.ts` | `core.ImageEmbeddingSession` | Per-conversion image validation, embedding, warnings, and accounting; prepare/estimate/embed parity remains. |
| `src/ts/core/image-references.ts` | `core.ImageReferenceTracker`, `adapters.LocalAssetResolver`, `cli.FileConversion` | Records local image candidates independently from describe/read, marks complete scans, and gates HTML/report collisions and failure reports. Race-injection coverage remains open. |
| `src/ts/core/code-languages.ts` | `core.CodeLanguages` | Accepted fence language aliases; syntax highlighting is not ported. |
| `src/ts/core/syntax-highlight.ts` | — | Not ported; code remains escaped plain text with language classes. Rhino was probe-only. |
| `src/ts/assets/uri.ts`, `image.ts`, `raster-dimensions.ts`, `svg.ts` | `core.ImageEmbeddingSession`, `core.ImageInspector`, `core.RasterDimensions` | URI classification, Base64 decoding, PNG/CgBI/JPEG/WebP dimensions, and SVG XML/CSS checks; full pipeline and boundary parity remain. |
| `src/ts/adapters/node-assets.ts` | `adapters.LocalAssetResolver` | Local file resolution, root containment, bounded reads, and change checks. |
| `src/ts/cli/args.ts` | `cli.CliArguments`, `cli.CliOptions` | Argument parsing and validation. |
| `src/ts/cli/markdown-input.ts` | `cli.FileConversion` | Bounded input reads and strict UTF-8 decoding. |
| `src/ts/cli/path-identity.ts`, `output-files.ts` | `cli.FileConversion` | Target identity, staging, collision checks, and output publication. Responsibilities are consolidated in this first port. |
| `src/ts/cli/convert-file.ts`, `reporting.ts` | `cli.FileConversion` | Conversion orchestration, report output, and diagnostic printing. |
| `src/ts/cli/main.ts` | `cli.MikuMd2htmlCli` | Testable command entry point and executable boundary. |
| `remark` / `rehype` parser pipeline | `core.MarkdownParser`, `core.markdown.FlexmarkAdapter`, `core.InlineMarkdownParser` | Flexmark CommonMark/GFM with selected extensions and jsoup HTML5 sanitation. The Java pipeline does not yet use one provenance-carrying article tree and is not full parser/output parity. |

Update this map when a responsibility is split, consolidated, or ported further.
