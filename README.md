# miku-md2html-java

`miku-md2html-java` is the Java CLI and runtime counterpart to
[`miku-md2html`](https://github.com/igapyon/miku-md2html). It converts local
Markdown and local images into one portable HTML file.

This repository is being developed as a traceable Java conversion of upstream
`miku-md2html` v0.3.5. The runtime implementation uses the Java standard
library; Maven and JUnit are used for building and tests. Current conversion
coverage and known differences are listed in
[remaining migration items](docs/remaining-migration-items.md).

## Build and Run

```sh
mvn test
mvn package
java -jar target/miku-md2html-java-0.3.5.jar --help
```

The executable jar and local distribution zip are created under `target/`.
The user-facing command and option contract is in [CLI reference](docs/cli.md).

## Upstream Mapping

The compatibility source is pinned to upstream tag `v0.3.5`, commit
`065c828dc6e987a39bdd7a4a15ea48a97ca2eccd`. The class map, test map, CLI map,
and follow-up history are under `docs/`.

## Repository Operation

`workplace/` is local scratch space for upstream and sister checkouts, generated
comparisons, and temporary verification files. Only `workplace/.gitkeep` is
tracked. Maven outputs under `target/` are ignored. Shared miku-soft references
are linked from [docs/miku-soft-reference.md](docs/miku-soft-reference.md),
not copied into this repository.

Maven plugin support is outside the initial Java conversion scope.

## License

Apache-2.0. See [LICENSE](LICENSE).
