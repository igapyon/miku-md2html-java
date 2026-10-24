# CLI Reference

The Java CLI targets the option names and exit-code intent of upstream
`miku-md2html` v0.4.0. The Java artifact is version `0.5.0`; its help
text uses Java jar invocation syntax.

```text
java -jar miku-md2html-java-0.5.0.jar <input.md> --out <output.html> [options]
```

Options:

- `--out <file>`: required `.html` destination.
- `--title <text>`: override the document title.
- `--lang <tag>`: set the document language; default is `ja`.
- `--asset-root <directory>`: allow local images under this directory. Paths
  in Markdown remain relative to the Markdown file.
- `--no-toc`: omit the generated table of contents.
- `--max-output-mib <n>`: input and HTML size limit, 1 through 1024 MiB;
  default is 100 MiB.
- `--report-json <file>`: write a schema version 2 conversion report.
- `--force`: replace existing regular output and report files.
- `--help`, `--version`: print help or product version.

The executable JAR bundles its parser and sanitizer dependencies. Remote images
are rejected; local and Base64 PNG, JPEG, GIF, WebP, and validated SVG images
are embedded. See [remaining migration items](remaining-migration-items.md)
for parser, sanitizer, and CLI differences from Node.

Successful conversion prints one summary line to stdout. Diagnostics go to
stderr. Exit codes are 0 for success, 1 for conversion or I/O failure, 2 for
usage errors, and 3 for unexpected internal errors.
