# CLI Reference

The Java CLI targets the option names and exit-code intent of upstream
`miku-md2html` v0.4.0. The Java artifact is version `0.6.0`, aligned to the
upstream v0.6.0 version number only; this does not claim full v0.6.0 parity.
Its help text uses Java jar invocation syntax.

```text
java -jar miku-md2html-java-0.6.0.jar <input.md> --out <output.html> [options]
```

Options:

- `--out <file>`: required `.html` destination.
- `--title <text>`: override the document title.
- `--lang <tag>`: set the document language; default is `ja`.
- `--asset-root <directory>`: allow local images under this directory. Paths
  in Markdown remain relative to the Markdown file.
- `--no-toc`: omit the generated table of contents.
- `--image-mode <reference|embed>`: choose how local raster images are handled;
  `reference` is the default and uses a relative URL from the HTML output,
  while `embed` stores image bytes in the HTML.
- `--max-output-mib <n>`: input and HTML size limit, 1 through 1024 MiB;
  default is 100 MiB.
- `--report-json <file>`: write a schema version 2 conversion report.
- `--force`: replace existing regular output and report files.
- `--help`, `--version`: print help or product version.

When the first direct article element is an H1, the generated table of
contents follows that heading; otherwise it appears before the article.
Repeated formatting blank lines between structural elements are limited to
one blank line. Inline text and code whitespace are preserved.

The executable JAR bundles its parser and sanitizer dependencies. Remote images
are rejected. Local raster images are referenced by default or embedded with
`--image-mode embed`; Base64 images, validated SVG, and inline SVG are embedded
in both modes. See [remaining migration items](remaining-migration-items.md)
for parser, sanitizer, and CLI differences from Node.

Successful conversion prints one summary line to stdout. Diagnostics go to
stderr. Exit codes are 0 for success, 1 for conversion or I/O failure, 2 for
usage errors, and 3 for unexpected internal errors.
