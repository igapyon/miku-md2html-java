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
