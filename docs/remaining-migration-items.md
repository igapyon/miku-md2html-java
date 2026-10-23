# Remaining Migration Items

## Current Stage

The first runnable Java conversion path is implemented. It covers the public
conversion call, a JDK-only Markdown subset, local and Base64 image embedding,
HTML assembly, and a testable file CLI. It is an initial port, not a claim of
full `miku-md2html` v0.3.5 parity.

The pinned Node source declares 14 direct production dependencies. The Java
runtime deliberately has none, following the project's dependency policy;
this makes the hand-written Java substitutes a compatibility risk, not proof
that the implementations behave alike. The dependency inventory and Node's
bundling model are recorded in [development notes](development.md).

## Implemented

- [x] Java request/result/diagnostic models and the public core entry point.
- [x] Front matter for the supported scalar `title`, `description`, and `lang`
  keys; metadata precedence; UTF-8 byte accounting.
- [x] Headings, unique IDs, TOC, fragment links, paragraphs, emphasis, code
  spans/fences, links, blockquotes, flat lists/task lists, rules, and GFM tables.
- [x] Local and Base64 PNG/JPEG/GIF/WebP/SVG image embedding, MIME/header
  checks, output and image warnings, and image statistics.
- [x] JDK XML-based SVG validation, conservative raw-HTML handling, bundled
  viewer resources, CSP hash, and a self-contained HTML document.
- [x] CLI help/version, shared option names/defaults, strict UTF-8 input,
  output/report writing, `--force`, diagnostics, and exit-code classes.
- [x] Initial focused JUnit coverage for core output, metadata, images,
  sanitization, links, argument parsing, local reads, and root traversal.

## Remaining Differences

- The hand-written parser is a deliberate JDK-only subset. It is not a full
  CommonMark/GFM parser: footnotes, reference links, nested/loose lists,
  multiline table details, full delimiter rules, and parser source-map parity
  remain unimplemented.
- Front matter is a small scalar parser, not a complete YAML parser. Complex
  YAML structures and all YAML edge cases are not supported.
- Syntax highlighting is not ported. Fence language aliases are retained as
  CSS classes, and code text is escaped as plain text.
- Raw HTML uses a simpler conservative policy: active block elements and their
  contents are removed, while other raw tags are unwrapped. It does not match
  the upstream sanitizer tree policy.
- SVG validation uses a strict JDK allowlist. It can reject safe upstream SVG
  constructs and does not normalize the SVG source before embedding.
- Local asset identity and staged output checks cover ordinary paths and
  symlink containment, but do not yet reproduce every Node race, hard-link,
  input/report collision, and filesystem-failure test.
- The JSON report and Java-specific `--help` text are compatible in purpose and
  shared options, but are not byte-for-byte copies of the Node CLI contract.
- The upstream's full test map, output snapshots, and format/security corpus
  have not yet been ported.

## Verification

- `mvn -q -DskipTests compile` passed on OpenJDK 17.0.21.
- `mvn -q package` passed on OpenJDK 17.0.21; this also ran all 8 focused
  JUnit tests.
- Executable jar `--help` and `--version` passed. A README conversion with a
  JSON report completed, and the report parsed as valid JSON.
- This host exposes OpenJDK 17 only. Java 8 and a newer LTS runtime were not
  available through `/usr/libexec/java_home -V`, so those runtime checks remain.

See [upstream test mapping](upstream-test-mapping.md) and
[upstream follow-up log](upstream-followup-log.md) for traceability.
