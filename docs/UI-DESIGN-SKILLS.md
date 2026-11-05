# UI design skills — included in the repository

Taste Skill and Impeccable are vendored as actual files under `.agents/skills/`.
They do not require npm installation or Git submodule commands. Claude Code
uses links under `.claude/skills/`; Codex uses `.agents/skills/`.
Restart the coding agent after pulling the latest main branch.

- Taste Skill: Leonxlnx/taste-skill at 12af471f627f866411b4bd785b19646d3672784c (MIT).
- Impeccable: pbakaus/impeccable at 9fadc41729c38fd47d7ae622b501558653f9d2e9 (Apache-2.0).
- License and NOTICE files are retained in `docs/third-party-notices/`.

## Scope
These are developer-only AI agent instructions and resources, not
application runtime dependencies. Do not add hooks or run privileged live
commands. Keep existing routes, data connections, real user data, auth,
deployment and geometry solvers unchanged unless the separate task
explicitly authorizes an implementation.

## Verify
`test -f .agents/skills/design-taste-frontend/SKILL.md`
`test -f .agents/skills/impeccable/SKILL.md`
`test -f .claude/skills/design-taste-frontend/SKILL.md`
`test -f .claude/skills/impeccable/SKILL.md`

Vendor updates require review and an explicit PR; do not fetch the upstream
main branch automatically.

## Repo hygiene (2026-11-05)

The two reviewed skills remain available to Codex and Claude: their entrypoints,
working libraries, scripts and license notices are **not** app runtime files.
The upstream skill-development-only `design-taste-frontend/evals/evals.json`
benchmark fixture is intentionally omitted from this product repo; it is
recoverable from the pinned upstream revision or Git history. Do not delete
runtime library/data/script files merely because they are large. Generated
`.taste-skill/` and `.impeccable/` run outputs are ignored by Git.
