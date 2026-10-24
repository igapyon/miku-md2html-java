# Development

## Project Shape

- Single-module Maven runtime and CLI.
- Java source and binary compatibility target: Java 8.
- Tests: JUnit Jupiter; `mvn -f pom.xml test`.
- Product package: executable shaded JAR and local distribution ZIP.
- Third-party runtime libraries are bundled in the executable JAR. The CLI does
  not need Node.js or a separate Java classpath.
- Maven plugin support remains outside the product scope.

## Upstream and Sister References

- Compatibility baseline: Node `miku-md2html` v0.4.0, commit
  `b18a6e390425bd0e15387a2d608980a42a1809e9`.
- Pinned Node runtime bundle SHA-256:
  `4c5aaeac024b5c31c003e2df40d66d8e8d2e5ad4a4fbe4c2222619c83290f16c`.
- Local read-only checkout used for source and oracle work: `../miku-md2html`.
- Product artifact version is `0.4.1`; compatibility baseline and implementation
  status are tracked separately in [upstream snapshot](upstream-snapshot.md).
- Sister Java references checked: `../miku-md2docx-java` and
  `../miku-md2pptx-java`. They inform the single-module runtime, testable CLI,
  Java 8 floor, fat JAR, source artifact, and release workflow shape. Product
  behavior follows this project's Node baseline.
- miku-soft reference materials and their application are listed in
  [miku-soft reference index](miku-soft-reference.md).

## Commands

```sh
mvn -f pom.xml test
mvn -f pom.xml package
java -jar target/miku-md2html-java-0.4.1.jar --version
bash scripts/smoke-packaged-runtime.sh target/miku-md2html-java-0.4.1.jar 0.4.1
node scripts/capture-node-parity.mjs
node scripts/compare-node-java.mjs target/miku-md2html-java-0.4.1.jar
```

The Node capture/comparison scripts are maintainer tools. Regular JUnit tests
use tracked fixtures and do not require Node. The packaged runtime smoke copies
the JAR to a fresh temporary directory and runs it without the source tree or
dependency classpath.

## Runtime Dependencies

Runtime dependencies are intentionally few and each covers an upstream parser
or security boundary that would otherwise require a large custom implementation.
The current POM has ten direct runtime dependencies:

| Component | Version | Purpose |
| --- | --- | --- |
| flexmark core and tables, autolink, GFM strikethrough, task list, and footnote extensions | 0.62.2 | CommonMark/GFM parsing and AST positions |
| jsoup | 1.23.2 | HTML5 fragment parsing, sanitization, and tracked positions |
| SnakeYAML Engine | 2.10 | YAML 1.2 CoreSchema front matter parsing |
| ph-css | 6.5.0 | CSS AST checks for SVG styles, URLs, at-rules, and parse errors |
| SLF4J NOP binding | 1.7.32 | Keeps the CSS parser's optional logger quiet in standalone runs |

The current shaded JAR contains 27 external runtime artifacts after transitive
dependencies. ph-css and ph-commons account for about 1.8 MiB of source JARs;
this is the largest addition and is retained for CSS parsing at an SVG security
boundary. Rhino was tested only in the isolated probe and is not a product
dependency because the syntax-highlighting execution gate is incomplete.

`mvn package` shades the dependency closure into one executable JAR. Third-party
license metadata is in [the bundled notices](../src/main/resources/META-INF/THIRD-PARTY-NOTICES.txt);
the build also places the project Apache-2.0 license at `META-INF/LICENSE`.
The isolated candidate probes are under `workplace/dependency-probes/` and are
not included in the product runtime.

The pinned Node v0.4.0 `package.json` has 11 direct production dependencies:
`unified`, `remark-parse`, `remark-gfm`, `remark-rehype`, `rehype-raw`,
`rehype-sanitize`, `hast-util-to-html`, `yaml`, `highlight.js`, `@xmldom/xmldom`,
and `css-tree`. The Java adapters preserve the upstream responsibility map,
while matching output remains a per-fixture question. Syntax highlighting,
full source provenance, and several CLI/image pipeline contracts are still
pending; the JAR is not yet a full-parity claim.

## Java Runtime Verification

The source targets Java 8. On a modern build JDK, Maven uses `--release 8`.
This does not prove execution on Java 8. The local host currently has OpenJDK
17 only. `.github/workflows/ci.yml` builds once with Temurin 21, then runs the
same packaged JAR on Temurin 8 and 25. Until that hosted workflow completes,
Java 8/25 runtime compatibility is unverified.

## miku-soft and GitHub Actions

The installed `igapyon-miku-soft-developer` skill and selected reference files
matched the clean skills checkout recorded in [miku-soft references](miku-soft-reference.md).
The implementation follows its Java 8 floor, packaged runtime/source roles,
small dependency set, and human-prepared Release workflow. The repo-specific
CI and exact tag-to-POM-version check are recorded in
[GitHub Actions implementation](github-actions-plan.md).
