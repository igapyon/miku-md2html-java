# miku-soft Reference

This repository follows the shared miku-soft design references maintained by
the `igapyon-miku-soft-developer` skill. The shared documents are not copied
here; use the installed skill as their source of truth.

Shared reference repository:

<https://github.com/igapyon/igapyon-agent-skills/tree/devel/skills/igapyon-miku-soft-developer>

Project-specific checked revisions and decisions are recorded in
[development.md](development.md).

## References Used for the v0.4.0 Plan

Checked on 2026-10-24. The installed skill was compared with the clean local
igapyon-agent-skills source checkout at commit
c61466dc03f7ed4a532fd6a2b37ef5a29be0071c.
SKILL.md and the eight materials below matched byte for byte.
The installed directory itself has no Git metadata; the revision is that of
the matching source checkout.

| ID | Shared source | Project decision area |
| --- | --- | --- |
| R01 | [Architecture rules](https://github.com/igapyon/igapyon-agent-skills/blob/devel/skills/igapyon-miku-soft-developer/references/architecture-rules.md) | Product boundaries, reference priority, deterministic ordering. |
| R02 | [Java straight-conversion workflow](https://github.com/igapyon/igapyon-agent-skills/blob/devel/skills/igapyon-miku-soft-developer/references/30-java-straight-conversion-workflow.md) | Upstream pin, mapping/tests, Release asset workflow and CI separation. |
| R03 | [Java application design](https://github.com/igapyon/igapyon-agent-skills/blob/devel/skills/igapyon-miku-soft-developer/references/miku-soft-basic/miku-soft-20-javaapp-design.md) | Java 8 floor, runtime verification, fat JAR and source artifact roles. |
| R04 | [Straight-conversion principles](https://github.com/igapyon/igapyon-agent-skills/blob/devel/skills/igapyon-miku-soft-developer/references/miku-soft-basic/miku-soft-30-straight-conversion.md) | Upstream traceability, library decisions, parity and follow-up records. |
| R05 | [CLI interface principles](https://github.com/igapyon/igapyon-agent-skills/blob/devel/skills/igapyon-miku-soft-developer/references/miku-soft-ai-era-cli-interface.md) | Help, diagnostics, machine-readable outputs and overwrite behavior. |
| R06 | [Repository operations](https://github.com/igapyon/igapyon-agent-skills/blob/devel/skills/igapyon-miku-soft-developer/references/repo-operations.md) | Local preparation and human GitHub operations. |
| R07 | [Release starter](https://github.com/igapyon/igapyon-agent-skills/blob/devel/skills/igapyon-miku-soft-developer/assets/java-straight-conversion/.github/workflows/release-cli-runtime.yml) | Adaptable implementation example for Release assets. |
| R08 | [Front matter principles](https://github.com/igapyon/igapyon-agent-skills/blob/devel/skills/igapyon-miku-soft-developer/references/miku-soft-ai-era-markdown-front-matter.md) | YAML parsing and extraction of documented metadata fields. |

Use the current shared principles and the fixed upstream contract when
implementing. Starter files and sister repositories supply implementation
evidence; dated audits and proposals do not become shared rules automatically.
The project plans record which decisions are shared defaults and which are
project-specific proposals:

- [Node v0.4.0 follow-up plan](upstream-v0.4.0-followup-plan.md)
- [GitHub Actions plan](github-actions-plan.md)
