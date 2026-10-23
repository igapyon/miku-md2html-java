# Development

## Project Shape

- Single-module Maven runtime and CLI.
- Java source and binary compatibility: 1.8.
- Test framework and primary command: JUnit Jupiter, `mvn test`.
- Runtime package: executable fat jar; local distribution zip.
- Runtime dependencies: Java standard library only.
- Maven plugin: outside initial scope.

## Upstream and Sister References

- Upstream: `miku-md2html` tag `v0.3.5`, commit
  `065c828dc6e987a39bdd7a4a15ea48a97ca2eccd`.
- Local upstream checkout used: `../miku-md2html`.
- Sister Java references checked: `../miku-md2docx-java` and
  `../miku-md2pptx-java`. They informed the single-module Maven runtime, thin
  testable CLI, Java 8 compatibility, migration documents, and fat-jar shape.
  Product parsing and HTML-safety policy remain mapped to this repository's
  upstream rather than copied from those projects.
- Checked date: 2026-10-23.
- Main workflow: `references/30-java-straight-conversion-workflow.md`.

## Commands

```sh
mvn test
mvn package
java -jar target/miku-md2html-java-0.3.5.jar --version
```

## Runtime Dependency Policy

Do not add third-party runtime dependencies. Preserve upstream responsibilities
in focused Java classes and use JDK APIs. If a specific security or format
requirement makes a third-party runtime dependency necessary, record the
tradeoff and upstream difference before adopting it.

### Pinned Node Dependency Baseline

The pinned Node source (`miku-md2html` v0.3.5) declares 14 direct production
dependencies in its [package.json](https://github.com/igapyon/miku-md2html/blob/065c828dc6e987a39bdd7a4a15ea48a97ca2eccd/package.json):

- Markdown processing: `unified`, `remark-parse`, `remark-gfm`, `remark-rehype`,
  `rehype-raw`, `rehype-sanitize`, `hast-util-to-html`, `unist-util-visit`.
- Front matter: `yaml`.
- Syntax highlighting: `lowlight`, `highlight.js`.
- Image and SVG handling: `image-size`, `@xmldom/xmldom`, `css-tree`.

It also declares 8 development dependencies. Its build script uses esbuild with
bundling enabled for both `bundle/miku-md2html.mjs` and
`bundle/miku-md2html-runtime.mjs`; release users receive a single `.mjs`
artifact, while source-tree development uses `npm install`. See the pinned
[bundle builder](https://github.com/igapyon/miku-md2html/blob/065c828dc6e987a39bdd7a4a15ea48a97ca2eccd/scripts/build-bundles.mjs).

The Java project deliberately targets zero third-party runtime dependencies,
as requested. That is a real implementation divergence from Node's dependency
stack, especially for CommonMark/GFM parsing, YAML, syntax highlighting, and
SVG/CSS validation. JDK-only replacements must be treated as behaviorally
different until parity tests demonstrate otherwise; matching class names or
pipeline stage names alone does not establish one-to-one behavior.
