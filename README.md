# miku-md2html-java

`miku-md2html-java` is the Java CLI and runtime counterpart to
[`miku-md2html`](https://github.com/igapyon/miku-md2html). It converts local
Markdown and local images into one portable HTML file.

The compatibility source is upstream `miku-md2html` v0.4.0, commit
`b18a6e390425bd0e15387a2d608980a42a1809e9`. The Java artifact version is
`0.5.0`; this identifies the shipped Java version and does not assert full Node
feature parity. A small set of runtime libraries is bundled in the executable
JAR. Current coverage and known differences are listed in
[remaining migration items](docs/remaining-migration-items.md).

## Build and Run

```sh
mvn -f pom.xml test
mvn -f pom.xml package
java -jar target/miku-md2html-java-0.5.0.jar --help
```

The executable JAR and local distribution ZIP are created under `target/`.
The JAR bundles its runtime dependencies and needs Java 8 or newer. The
user-facing command and option contract is in [CLI reference](docs/cli.md).

## Upstream Mapping

The upstream snapshot, implementation status, and pinned runtime hash are in
[upstream snapshot](docs/upstream-snapshot.md). Class, test, CLI, and task
mappings are under `docs/`. Five fixed Node 0.4.0 parity fixtures are tracked
under `src/test/resources/parity/v0.4.0/`; maintainer scripts regenerate the
Node oracle and compare it with a packaged Java JAR.

## Repository Operation

`workplace/` is local scratch space for dependency probes, generated
comparisons, and temporary verification files. Maven outputs under `target/`
are ignored. Shared miku-soft references are linked from
[docs/miku-soft-reference.md](docs/miku-soft-reference.md), not copied into
this repository.

GitHub Actions runs the test/package workflow and verifies one packaged JAR
on Java 8 and 25. A separate workflow attaches runtime and sources JARs to a
Release prepared by a human; it does not create tags or Releases.

Maven plugin support is outside the product scope.

## License

Apache-2.0. See [LICENSE](LICENSE). Bundled third-party notices are in the JAR
at `META-INF/THIRD-PARTY-NOTICES.txt` and in the repository's
[third-party notice index](THIRD-PARTY-NOTICES.md).
