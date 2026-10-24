# Runtime Dependency Decisions

Reviewed against Node v0.4.0 and miku-soft's straight-conversion guidance on
2026-10-24. The Java 8 runtime floor remains. Dependencies are bundled in the
shaded JAR and listed at `META-INF/THIRD-PARTY-NOTICES.txt`.

## Adopted Dependencies

| Direct dependency | Version | Adopted for | Probe evidence and remaining gate |
| --- | --- | --- | --- |
| flexmark core | 0.62.2 | CommonMark AST/parser | Focused block and GFM tests pass; source provenance and byte parity remain partial. |
| flexmark tables | 0.62.2 | GFM tables | Table rendering probe and Java test pass. |
| flexmark autolink | 0.62.2 | Bare URL/email links | Required URL rendering exercised; full upstream email cases remain. |
| flexmark GFM strikethrough | 0.62.2 | `~~text~~` | Java fixture passes. |
| flexmark GFM task list | 0.62.2 | Task-list checkboxes | Java fixture passes. |
| flexmark footnotes | 0.62.2 | Footnote syntax | One basic footnote fixture passes; repeated/multi-paragraph parity remains. |
| jsoup | 1.23.2 | HTML5 fragment parsing, allowlist sanitation, source ranges | Safe details/table, unsafe elements/links, and raw image behavior are covered; generated/source provenance is incomplete. |
| SnakeYAML Engine | 2.10 | YAML 1.2 CoreSchema front matter | Empty/unknown nested values and block scalar tests pass; alias-budget equivalence and full Node corpus remain unverified. |
| ph-css | 6.5.0 | SVG stylesheet and declaration AST validation | Isolated JDK 17 probe exposed URL AST values and malformed CSS parse errors. Escaped external URLs fail closed because the returned URI is not a literal `#fragment`. CSS handling tests pass. |
| slf4j-nop | 1.7.32 | Quiet binding for ph-css's optional SLF4J API | 4 KiB JAR; avoids a provider warning in a standalone executable. |

The current shaded build resolves 27 external runtime artifacts including
transitives. flexmark's own modules and utility artifacts are listed in the
bundled notices. The largest dependency closure is ph-css plus ph-commons at
about 1.8 MiB. It is retained because CSS parsing is part of SVG resource
validation; a regular-expression-only check missed CSS escapes and style
elements without attributes.

No raster image library was added. `RasterDimensions` follows the upstream
PNG/CgBI, JPEG, and WebP dimension readers using byte parsing and Java `long`.
The converter continues to embed the original image bytes.

## Not Adopted

| Candidate | Decision | Reason |
| --- | --- | --- |
| Rhino 1.7.15.1 | Probe only; not in product POM | The initial probe proved class loading only. Java 8/25 execution, all 13 language families, host-access isolation, execution budget interruption, and deterministic token parity have not passed the plan's gate. Syntax highlighting remains an explicit gap. |
| JavaScript transpiler | Not added | No supported highlight runtime has passed the Rhino gate, so a transpiler would add another dependency without enabling a verified feature. |
| Additional YAML or HTML library | Not added | SnakeYAML Engine and jsoup cover the required parsers. |
| Separate SVG/XML library | Not added | JDK XML parsing remains suitable for hardened SVG structure checks. |
| General-purpose image library | Not added | The required dimension readers are small and remain dependency-free. |

## Java Compatibility Evidence

The selected parser classes inspected from local artifacts have class-file
major version 52 (Java 8). Maven compiles product sources with `--release 8`
on the available OpenJDK 17 host. No local Java 8 or 25 runtime is available;
actual packaged-JAR execution on both versions is delegated to
`.github/workflows/ci.yml` and has not yet run on GitHub.

The isolated probe project is `workplace/dependency-probes/pom.xml`. Its tests
ran successfully on OpenJDK 17 after adding the CSS AST cases. Passing the
probe does not count as Java 8/25 runtime verification.
