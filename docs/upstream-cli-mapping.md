# Upstream CLI Mapping

The option names, single-input contract, defaults, stdout/stderr roles, and
exit-code classes target upstream v0.4.0 where practical. The Java executable
uses jar syntax and therefore has Java-specific help text.

| Upstream option / behavior | Java implementation | Status / difference |
| --- | --- | --- |
| `<input.md>` plus required `--out <output.html>` | `CliArguments`, `FileConversion` | Implemented; `.md` and `.markdown` input accepted. |
| `--title`, `--lang`, `--asset-root` | `CliOptions`, `LocalAssetResolver` | Implemented with same option names and intent. |
| `--no-toc`, `--max-output-mib` | `CliOptions`, `MarkdownConverter` | Implemented; cap is checked against input and completed output. |
| `--report-json`, `--force` | `FileConversion` | Implemented for ordinary files; Node's complete publication/race contract remains to be ported. |
| `--help`, `--version` | `MikuMd2htmlCli` | Implemented; help uses Java invocation syntax. |
| Exit codes 0/1/2/3 | `MikuMd2htmlCli`, `FileConversion` | Implemented for success, processing/I/O, usage, and internal failures. |
| Strict UTF-8 and bounded input read | `FileConversion` | Implemented with JDK decoder and configured cap. |
| Atomic output/report staging | `FileConversion` | Stages both beside their targets before commit; injected report-stage/commit tests pass. Reference-scan collision safety and filesystem race cases remain. |

See [CLI reference](cli.md) for user-facing behavior and
[remaining migration items](remaining-migration-items.md) for known gaps.
